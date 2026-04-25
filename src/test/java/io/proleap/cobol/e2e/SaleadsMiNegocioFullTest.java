package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * This test is intentionally opt-in and only runs when:
 * -Dsaleads.e2e.enabled=true
 *
 * Required runtime property:
 * -Dsaleads.login.url=<current-environment-login-url>
 */
public class SaleadsMiNegocioFullTest {

	private static final String EXPECTED_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String FIELD_LOGIN = "Login";
	private static final String FIELD_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String FIELD_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String FIELD_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String FIELD_INFORMACION_GENERAL = "Información General";
	private static final String FIELD_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String FIELD_TUS_NEGOCIOS = "Tus Negocios";
	private static final String FIELD_TERMINOS = "Términos y Condiciones";
	private static final String FIELD_POLITICA = "Política de Privacidad";

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter EVIDENCE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, ValidationStatus> report = new LinkedHashMap<>();
	private String terminosFinalUrl = "N/A";
	private String politicaFinalUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue("Skipped. Enable with -Dsaleads.e2e.enabled=true", enabled);

		final String loginUrl = getLoginUrl();
		Assume.assumeTrue("Skipped. Provide -Dsaleads.login.url or SALEADS_LOGIN_URL", loginUrl != null);

		final String runId = LocalDateTime.now().format(EVIDENCE_FORMAT);
		evidenceDir = Path.of("target", "saleads-mi-negocio-evidence", runId);
		Files.createDirectories(evidenceDir);

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		driver.manage().window().maximize();
		driver.get(loginUrl);
		waitForUiToSettle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		initializeReport();

		runStep(FIELD_LOGIN, this::stepLoginWithGoogle);
		runStep(FIELD_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(FIELD_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(FIELD_ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		runStep(FIELD_INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		runStep(FIELD_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(FIELD_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(FIELD_TERMINOS, this::stepValidateTerminos);
		runStep(FIELD_POLITICA, this::stepValidatePoliticaPrivacidad);

		writeFinalReport();
		assertTrue(buildAssertionMessage(), report.values().stream().allMatch(ValidationStatus::isPassed));
	}

	private void initializeReport() {
		report.put(FIELD_LOGIN, ValidationStatus.pending());
		report.put(FIELD_MI_NEGOCIO_MENU, ValidationStatus.pending());
		report.put(FIELD_AGREGAR_NEGOCIO_MODAL, ValidationStatus.pending());
		report.put(FIELD_ADMINISTRAR_NEGOCIOS_VIEW, ValidationStatus.pending());
		report.put(FIELD_INFORMACION_GENERAL, ValidationStatus.pending());
		report.put(FIELD_DETALLES_CUENTA, ValidationStatus.pending());
		report.put(FIELD_TUS_NEGOCIOS, ValidationStatus.pending());
		report.put(FIELD_TERMINOS, ValidationStatus.pending());
		report.put(FIELD_POLITICA, ValidationStatus.pending());
	}

	private void runStep(final String field, final Step step) {
		try {
			step.execute();
			report.put(field, ValidationStatus.passed("PASS"));
		} catch (final Exception ex) {
			report.put(field, ValidationStatus.failed(ex.getMessage()));
			captureScreenshot("failure_" + sanitize(field));
		}
	}

	private void stepLoginWithGoogle() {
		if (!isSidebarVisible()) {
			final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
			clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
					"Continuar con Google", "Login with Google");
			waitForUiToSettle();
			chooseGoogleAccountIfShown(handlesBefore, EXPECTED_ACCOUNT_EMAIL);
		}

		waitUntil(this::isSidebarVisible, "Main application sidebar did not appear after login.");
		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() {
		ensureMiNegocioExpanded();

		final boolean agregarVisible = isTextPresent("Agregar Negocio");
		final boolean administrarVisible = isTextPresent("Administrar Negocios");
		assertTrue("Expected submenu option 'Agregar Negocio' to be visible.", agregarVisible);
		assertTrue("Expected submenu option 'Administrar Negocios' to be visible.", administrarVisible);
		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");
		waitForUiToSettle();

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		captureScreenshot("03_agregar_negocio_modal");

		final WebElement input = firstVisibleElement(
				By.xpath("//input[@name='businessName']"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//label[contains(.,'Nombre del Negocio')]/following::input[1]"));
		if (input != null) {
			input.click();
			input.sendKeys(Keys.chord(Keys.CONTROL, "a"), "Negocio Prueba Automatización");
			waitForUiToSettle();
		}

		clickByVisibleText("Cancelar");
		waitForUiToSettle();
		waitUntil(() -> !isTextPresent("Crear Nuevo Negocio"), "Modal did not close after clicking Cancelar.");
	}

	private void stepOpenAdministrarNegocios() {
		ensureMiNegocioExpanded();
		clickByVisibleText("Administrar Negocios");
		waitForUiToSettle();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		captureScreenshot("04_administrar_negocios_page");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("Información General");
		assertTrue("Expected user name information to be visible.", isUserNameVisible());
		assertTrue("Expected user email to be visible.", isUserEmailVisible());
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertTrue("Expected business list to be visible.", hasBusinessListVisible());
		assertTrue("Expected 'Agregar Negocio' button to be visible inside 'Tus Negocios'.",
				isAgregarNegocioInTusNegociosVisible());
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminos() {
		terminosFinalUrl = validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "05_terminos");
	}

	private void stepValidatePoliticaPrivacidad() {
		politicaFinalUrl = validateLegalLink("Política de Privacidad", "Política de Privacidad", "06_politica_privacidad");
	}

	private String validateLegalLink(final String linkText, final String headingText, final String screenshotPrefix) {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);
		waitForUiToSettle();

		final String targetHandle = awaitNewWindowOrCurrent(handlesBefore);
		driver.switchTo().window(targetHandle);
		waitForUiToSettle();

		assertVisibleText(headingText);
		final String bodyText = safeBodyText();
		assertTrue("Expected legal content text for " + headingText, bodyText != null && bodyText.length() > 150);
		captureScreenshot(screenshotPrefix + "_page");

		final String finalUrl = driver.getCurrentUrl();
		if (!targetHandle.equals(appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToSettle();
		} else {
			driver.navigate().back();
			waitForUiToSettle();
		}

		return finalUrl;
	}

	private void chooseGoogleAccountIfShown(final Set<String> handlesBefore, final String accountEmail) {
		try {
			final String maybeGoogleHandle = awaitNewWindowOrCurrent(handlesBefore);
			driver.switchTo().window(maybeGoogleHandle);
			waitForUiToSettle();

			if (isGoogleAuthScreen()) {
				final WebElement account = firstVisibleElement(
						By.xpath("//*[normalize-space()=" + toXpathLiteral(accountEmail) + "]"),
						By.xpath("//*[@data-email=" + toXpathLiteral(accountEmail) + "]"),
						By.xpath("//*[contains(@data-identifier," + toXpathLiteral(accountEmail) + ")]"));

				if (account != null) {
					scrollIntoView(account);
					tryClick(account);
					waitForUiToSettle();
				}
			}
		} catch (final TimeoutException ignored) {
			// In some sessions Google step is skipped because the user is already authenticated.
		}

		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (isSidebarVisible()) {
				return;
			}
		}
	}

	private void ensureMiNegocioExpanded() {
		final boolean hasAgregar = isTextPresent("Agregar Negocio");
		final boolean hasAdministrar = isTextPresent("Administrar Negocios");

		if (!hasAgregar || !hasAdministrar) {
			if (isTextPresent("Negocio")) {
				clickByVisibleText("Negocio");
				waitForUiToSettle();
			}
			if (isTextPresent("Mi Negocio")) {
				clickByVisibleText("Mi Negocio");
				waitForUiToSettle();
			}
		}
	}

	private boolean isSidebarVisible() {
		final WebElement sidebar = firstVisibleElement(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar')]"));
		if (sidebar == null) {
			return false;
		}
		return isTextPresent("Negocio") || isTextPresent("Mi Negocio");
	}

	private boolean isUserNameVisible() {
		final String configuredName = System.getProperty("saleads.user.name", System.getenv("SALEADS_USER_NAME"));
		if (configuredName != null && isTextPresent(configuredName)) {
			return true;
		}

		return hasLabeledValue("Nombre") || hasLabeledValue("Usuario");
	}

	private boolean isUserEmailVisible() {
		if (isTextPresent(EXPECTED_ACCOUNT_EMAIL)) {
			return true;
		}

		final String bodyText = safeBodyText();
		return bodyText != null && EMAIL_PATTERN.matcher(bodyText).find();
	}

	private boolean hasLabeledValue(final String labelText) {
		final WebElement value = firstVisibleElement(
				By.xpath("//*[contains(normalize-space(), " + toXpathLiteral(labelText) + ")]/following-sibling::*[1]"),
				By.xpath("//*[contains(normalize-space(), " + toXpathLiteral(labelText) + ")]/ancestor::*[1]//*[self::span or self::div][2]"));
		return value != null && value.getText() != null && !value.getText().trim().isEmpty();
	}

	private boolean hasBusinessListVisible() {
		final WebElement list = firstVisibleElement(
				By.xpath("//*[contains(normalize-space(),'Tus Negocios')]/following::*[self::ul or self::table][1]"),
				By.xpath("//*[contains(@class,'business') and (self::ul or self::div)]"));
		return list != null;
	}

	private boolean isAgregarNegocioInTusNegociosVisible() {
		final WebElement button = firstVisibleElement(
				By.xpath("//*[contains(normalize-space(),'Tus Negocios')]/following::*[self::button or self::a][contains(.,'Agregar Negocio')][1]"),
				By.xpath("//button[contains(normalize-space(),'Agregar Negocio')]"));
		return button != null;
	}

	private void assertVisibleText(final String text) {
		waitUntil(() -> isTextPresent(text), "Expected text to be visible: " + text);
	}

	private boolean isTextPresent(final String text) {
		final List<WebElement> exactMatches = driver.findElements(By.xpath("//*[normalize-space()=" + toXpathLiteral(text) + "]"));
		for (final WebElement element : exactMatches) {
			if (element.isDisplayed()) {
				return true;
			}
		}

		final List<WebElement> partialMatches = driver
				.findElements(By.xpath("//*[contains(normalize-space(), " + toXpathLiteral(text) + ")]"));
		for (final WebElement element : partialMatches) {
			if (element.isDisplayed()) {
				return true;
			}
		}

		return false;
	}

	private void clickByVisibleText(final String... texts) {
		Exception lastError = null;
		for (final String text : texts) {
			final List<By> candidates = candidateLocatorsByText(text);
			for (final By locator : candidates) {
				try {
					final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
					scrollIntoView(element);
					tryClick(element);
					waitForUiToSettle();
					return;
				} catch (final Exception ex) {
					lastError = ex;
				}
			}
		}
		final String joined = String.join(", ", texts);
		throw new IllegalStateException("Could not click any element with visible text: " + joined, lastError);
	}

	private List<By> candidateLocatorsByText(final String text) {
		final String literal = toXpathLiteral(text);
		final List<By> candidates = new ArrayList<>();
		candidates.add(By.xpath("//button[normalize-space()=" + literal + "]"));
		candidates.add(By.xpath("//a[normalize-space()=" + literal + "]"));
		candidates.add(By.xpath("//*[@role='button' and normalize-space()=" + literal + "]"));
		candidates.add(By.xpath("//*[normalize-space()=" + literal + "]"));
		candidates.add(By.xpath("//button[contains(normalize-space(), " + literal + ")]"));
		candidates.add(By.xpath("//a[contains(normalize-space(), " + literal + ")]"));
		candidates.add(By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
		return candidates;
	}

	private WebElement firstVisibleElement(final By... locators) {
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		return null;
	}

	private String awaitNewWindowOrCurrent(final Set<String> handlesBefore) {
		wait.until((ExpectedCondition<Boolean>) d -> d != null && d.getWindowHandles().size() >= handlesBefore.size());
		final Set<String> handlesAfter = driver.getWindowHandles();
		for (final String handle : handlesAfter) {
			if (!handlesBefore.contains(handle)) {
				return handle;
			}
		}
		return driver.getWindowHandle();
	}

	private boolean isGoogleAuthScreen() {
		final String currentUrl = driver.getCurrentUrl();
		if (currentUrl != null && currentUrl.contains("accounts.google.com")) {
			return true;
		}
		final String bodyText = safeBodyText();
		if (bodyText == null) {
			return false;
		}
		final String lowered = bodyText.toLowerCase(Locale.ROOT);
		return lowered.contains("choose an account") || lowered.contains("elige una cuenta")
				|| lowered.contains("continuar como");
	}

	private void waitUntil(final Condition condition, final String timeoutMessage) {
		try {
			wait.until(driver -> condition.evaluate());
		} catch (final TimeoutException ex) {
			throw new IllegalStateException(timeoutMessage, ex);
		}
	}

	private void waitForUiToSettle() {
		wait.until(d -> {
			if (!(d instanceof JavascriptExecutor)) {
				return true;
			}
			final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(readyState));
		});
		try {
			Thread.sleep(500);
		} catch (final InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private void tryClick(final WebElement element) {
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void scrollIntoView(final WebElement element) {
		if (driver instanceof JavascriptExecutor) {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		}
	}

	private void captureScreenshot(final String name) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		try {
			final Path destination = evidenceDir.resolve(name + ".png");
			final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception ignored) {
			// Screenshot capture is best effort.
		}
	}

	private String safeBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception ex) {
			return null;
		}
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if (c == '\'') {
				builder.append("\"'\"");
			} else if (c == '"') {
				builder.append("'\"'");
			} else {
				builder.append("'").append(c).append("'");
			}
			if (i < value.length() - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String sanitize(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
	}

	private WebDriver createDriver() {
		final String remoteUrl = System.getProperty("saleads.selenium.remote.url", System.getenv("SALEADS_SELENIUM_REMOTE_URL"));
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "false"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		if (remoteUrl != null && !remoteUrl.isBlank()) {
			return RemoteWebDriver.builder().oneOf(options).address(remoteUrl).build();
		}

		return new ChromeDriver(options);
	}

	private String getLoginUrl() {
		final String fromProperty = System.getProperty("saleads.login.url");
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}

		final String fromEnv = System.getenv("SALEADS_LOGIN_URL");
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		return null;
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Full Test Report").append(System.lineSeparator());
		sb.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		sb.append(System.lineSeparator());

		for (final Map.Entry<String, ValidationStatus> entry : report.entrySet()) {
			sb.append(entry.getKey()).append(": ").append(entry.getValue().status).append(System.lineSeparator());
			if (entry.getValue().detail != null && !entry.getValue().detail.isBlank()) {
				sb.append("  detail: ").append(entry.getValue().detail).append(System.lineSeparator());
			}
		}

		sb.append(System.lineSeparator());
		sb.append("Términos y Condiciones final URL: ").append(terminosFinalUrl).append(System.lineSeparator());
		sb.append("Política de Privacidad final URL: ").append(politicaFinalUrl).append(System.lineSeparator());

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, sb.toString());
	}

	private String buildAssertionMessage() {
		final StringBuilder sb = new StringBuilder("One or more SaleADS workflow validations failed:");
		for (final Map.Entry<String, ValidationStatus> entry : report.entrySet()) {
			if (!entry.getValue().isPassed()) {
				sb.append(System.lineSeparator()).append("- ").append(entry.getKey()).append(": ")
						.append(entry.getValue().detail);
			}
		}
		sb.append(System.lineSeparator()).append("Evidence directory: ")
				.append(evidenceDir != null ? evidenceDir.toAbsolutePath() : "N/A");
		return sb.toString();
	}

	@FunctionalInterface
	private interface Step {
		void execute() throws Exception;
	}

	@FunctionalInterface
	private interface Condition {
		boolean evaluate();
	}

	private static class ValidationStatus {
		private final String status;
		private final String detail;

		private ValidationStatus(final String status, final String detail) {
			this.status = status;
			this.detail = detail;
		}

		private static ValidationStatus pending() {
			return new ValidationStatus("PENDING", "Not executed yet");
		}

		private static ValidationStatus passed(final String detail) {
			return new ValidationStatus("PASS", detail);
		}

		private static ValidationStatus failed(final String detail) {
			return new ValidationStatus("FAIL", detail == null ? "Unknown error" : detail);
		}

		private boolean isPassed() {
			return "PASS".equals(status);
		}
	}
}
