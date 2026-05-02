package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end UI automation for SaleADS Mi Negocio workflow.
 *
 * Required runtime config:
 * -Dsaleads.url=https://<environment-login-page>
 *
 * Optional runtime config:
 * -Dsaleads.headless=true|false (default: true)
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final Duration SHORT_WAIT = Duration.ofSeconds(8);
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN = "Administrar Negocios view";
	private static final String STEP_INFO = "Informaci\u00f3n General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "T\u00e9rminos y Condiciones";
	private static final String STEP_PRIVACY = "Pol\u00edtica de Privacidad";

	private final Map<String, StepResult> results = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor js;
	private Path evidenceDir;
	private Path reportFile;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		evidenceDir = Path.of("target", "saleads-mi-negocio-evidence");
		Files.createDirectories(evidenceDir);
		reportFile = Path.of("target", "saleads-mi-negocio-report.json");

		final ChromeOptions chromeOptions = new ChromeOptions();
		if (isHeadlessEnabled()) {
			chromeOptions.addArguments("--headless=new");
		}
		chromeOptions.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(chromeOptions);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		js = (JavascriptExecutor) driver;

		final String loginUrl = getConfig("saleads.url", "SALEADS_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.navigate().to(loginUrl);
			waitForUiToLoad();
		}

		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() throws IOException {
		runStep(STEP_LOGIN, this::stepLoginWithGoogle);
		runStep(STEP_MENU, this::stepOpenMiNegocioMenu);
		runStep(STEP_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(STEP_ADMIN, this::stepOpenAdministrarNegocios);
		runStep(STEP_INFO, this::stepValidateInformacionGeneral);
		runStep(STEP_ACCOUNT_DETAILS, this::stepValidateDetallesDeLaCuenta);
		runStep(STEP_BUSINESSES, this::stepValidateTusNegocios);
		runStep(STEP_TERMS, this::stepValidateTerminosYCondiciones);
		runStep(STEP_PRIVACY, this::stepValidatePoliticaDePrivacidad);

		writeFinalReport();
		assertTrue(buildFailureSummary(), allStepsPassed());
	}

	private Map<String, String> stepLoginWithGoogle() throws IOException {
		final Map<String, String> evidence = new LinkedHashMap<>();
		if (isMainAppInterfaceVisible(SHORT_WAIT)) {
			evidence.put("loginState", "Already authenticated");
			evidence.put("dashboardScreenshot", takeViewportScreenshot("01-dashboard-loaded"));
			return evidence;
		}

		clickByVisibleText("Sign in with Google", "Iniciar sesion con Google", "Continuar con Google", "Google");
		chooseGoogleAccountIfPrompted("juanlucasbarbiergarzon@gmail.com");
		waitForMainAppInterface();

		evidence.put("dashboardScreenshot", takeViewportScreenshot("01-dashboard-loaded"));
		return evidence;
	}

	private Map<String, String> stepOpenMiNegocioMenu() throws IOException {
		final Map<String, String> evidence = new LinkedHashMap<>();
		ensureTextVisible("Negocio");
		clickByVisibleText("Mi Negocio");
		ensureTextVisible("Agregar Negocio");
		ensureTextVisible("Administrar Negocios");

		evidence.put("expandedMenuScreenshot", takeViewportScreenshot("02-mi-negocio-menu-expanded"));
		return evidence;
	}

	private Map<String, String> stepValidateAgregarNegocioModal() throws IOException {
		final Map<String, String> evidence = new LinkedHashMap<>();
		clickByVisibleText("Agregar Negocio");

		ensureTextVisible("Crear Nuevo Negocio");
		ensureTextVisible("Nombre del Negocio");
		ensureTextVisible("Tienes 2 de 3 negocios");
		ensureTextVisible("Cancelar");
		ensureTextVisible("Crear Negocio");

		typeIntoBusinessName("Negocio Prueba Automatizacion");
		evidence.put("modalScreenshot", takeViewportScreenshot("03-agregar-negocio-modal"));

		clickByVisibleText("Cancelar");
		waitForTextToDisappear("Crear Nuevo Negocio");
		return evidence;
	}

	private Map<String, String> stepOpenAdministrarNegocios() throws IOException {
		final Map<String, String> evidence = new LinkedHashMap<>();
		if (!isTextVisible("Administrar Negocios", SHORT_WAIT)) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		ensureTextVisible("Informacion General", "Informaci\u00f3n General");
		ensureTextVisible("Detalles de la Cuenta");
		ensureTextVisible("Tus Negocios");
		ensureTextVisible("Seccion Legal", "Secci\u00f3n Legal");

		evidence.put("administrarNegociosScreenshot", takeFullPageScreenshot("04-administrar-negocios"));
		return evidence;
	}

	private Map<String, String> stepValidateInformacionGeneral() {
		final Map<String, String> evidence = new LinkedHashMap<>();
		ensureTextVisible("BUSINESS PLAN");
		ensureTextVisible("Cambiar Plan");
		ensureEmailVisible();
		ensureUserNameVisible();

		evidence.put("status", "Validated Informacion General");
		return evidence;
	}

	private Map<String, String> stepValidateDetallesDeLaCuenta() {
		final Map<String, String> evidence = new LinkedHashMap<>();
		ensureTextVisible("Cuenta creada");
		ensureTextVisible("Estado activo");
		ensureTextVisible("Idioma seleccionado");

		evidence.put("status", "Validated Detalles de la Cuenta");
		return evidence;
	}

	private Map<String, String> stepValidateTusNegocios() {
		final Map<String, String> evidence = new LinkedHashMap<>();
		ensureTextVisible("Tus Negocios");
		ensureTextVisible("Agregar Negocio");
		ensureTextVisible("Tienes 2 de 3 negocios");

		evidence.put("status", "Validated Tus Negocios");
		return evidence;
	}

	private Map<String, String> stepValidateTerminosYCondiciones() throws IOException {
		final Map<String, String> evidence = new LinkedHashMap<>();
		evidence.putAll(openLegalLinkAndValidate("Terminos y Condiciones", "T\u00e9rminos y Condiciones",
				"05-terminos-y-condiciones"));
		return evidence;
	}

	private Map<String, String> stepValidatePoliticaDePrivacidad() throws IOException {
		final Map<String, String> evidence = new LinkedHashMap<>();
		evidence.putAll(
				openLegalLinkAndValidate("Politica de Privacidad", "Pol\u00edtica de Privacidad", "06-politica-privacidad"));
		return evidence;
	}

	private Map<String, String> openLegalLinkAndValidate(final String primaryText, final String alternateText,
			final String screenshotPrefix) throws IOException {
		final String appHandleBefore = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(primaryText, alternateText);
		final String legalHandle = switchToLegalTabOrCurrent(handlesBefore, appHandleBefore);
		waitForUiToLoad();

		ensureTextVisible(primaryText, alternateText);
		ensureLegalContentVisible();

		final Map<String, String> evidence = new LinkedHashMap<>();
		evidence.put("finalUrl", driver.getCurrentUrl());
		evidence.put("screenshot", takeViewportScreenshot(screenshotPrefix));

		returnToApplicationTab(appHandleBefore, legalHandle);
		return evidence;
	}

	private void returnToApplicationTab(final String appHandle, final String legalHandle) {
		if (!appHandle.equals(legalHandle) && driver.getWindowHandles().contains(legalHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
			return;
		}

		driver.navigate().back();
		waitForUiToLoad();
	}

	private String switchToLegalTabOrCurrent(final Set<String> handlesBefore, final String currentHandle) {
		try {
			new WebDriverWait(driver, SHORT_WAIT).until(d -> d.getWindowHandles().size() > handlesBefore.size());
		} catch (final TimeoutException ignored) {
			// Same-tab navigation path.
		}

		final Set<String> handlesAfter = driver.getWindowHandles();
		for (final String handle : handlesAfter) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				return handle;
			}
		}

		driver.switchTo().window(currentHandle);
		return currentHandle;
	}

	private void runStep(final String name, final StepAction action) {
		final StepResult result = new StepResult();
		result.name = name;

		try {
			result.evidence.putAll(action.run());
			result.status = "PASS";
		} catch (final Exception ex) {
			result.status = "FAIL";
			result.error = safeMessage(ex);
			try {
				result.evidence.put("failureScreenshot", takeViewportScreenshot("fail-" + sanitizeFileName(name)));
			} catch (final IOException ioException) {
				result.evidence.put("failureScreenshotError", ioException.getMessage());
			}
		}

		results.put(name, result);
	}

	private void clickByVisibleText(final String... textCandidates) {
		WebElement clickable = null;
		for (final String candidate : textCandidates) {
			clickable = findVisibleClickable(candidate, SHORT_WAIT);
			if (clickable != null) {
				break;
			}
		}

		if (clickable == null) {
			throw new IllegalStateException("Unable to find clickable element by text: " + String.join(", ", textCandidates));
		}

		scrollIntoView(clickable);
		try {
			clickable.click();
		} catch (final RuntimeException clickFailure) {
			js.executeScript("arguments[0].click();", clickable);
		}

		waitForUiToLoad();
	}

	private WebElement findVisibleClickable(final String text, final Duration timeout) {
		final By locator = By.xpath(
				"(//*[self::button or self::a or @role='button' or @role='menuitem' or self::div or self::span][normalize-space(.)="
						+ xpathLiteral(text) + " or contains(normalize-space(.), " + xpathLiteral(text) + ")])[1]");

		try {
			return new WebDriverWait(driver, timeout).until(ExpectedConditions.elementToBeClickable(locator));
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private void waitForMainAppInterface() {
		wait.until(ExpectedConditions.or(ExpectedConditions.visibilityOfElementLocated(By.tagName("aside")),
				ExpectedConditions.visibilityOfElementLocated(textLocator("Negocio")),
				ExpectedConditions.visibilityOfElementLocated(textLocator("Mi Negocio"))));
	}

	private boolean isMainAppInterfaceVisible(final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.or(
					ExpectedConditions.visibilityOfElementLocated(By.tagName("aside")),
					ExpectedConditions.visibilityOfElementLocated(textLocator("Negocio")),
					ExpectedConditions.visibilityOfElementLocated(textLocator("Mi Negocio"))));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void chooseGoogleAccountIfPrompted(final String email) {
		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() > 1) {
			for (final String handle : handles) {
				if (!handle.equals(appWindowHandle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		final WebElement accountElement = findVisibleClickable(email, SHORT_WAIT);
		if (accountElement != null) {
			scrollIntoView(accountElement);
			accountElement.click();
			waitForUiToLoad();
		}

		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private void typeIntoBusinessName(final String value) {
		final List<By> candidates = List.of(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));

		for (final By candidate : candidates) {
			try {
				final WebElement input = new WebDriverWait(driver, SHORT_WAIT)
						.until(ExpectedConditions.visibilityOfElementLocated(candidate));
				input.clear();
				input.sendKeys(value);
				return;
			} catch (final TimeoutException ignored) {
				// try next locator
			}
		}

		throw new IllegalStateException("Unable to type in 'Nombre del Negocio' input.");
	}

	private void ensureEmailVisible() {
		wait.until(driverRef -> {
			final String text = driverRef.findElement(By.tagName("body")).getText();
			return EMAIL_PATTERN.matcher(text).find();
		});
	}

	private void ensureUserNameVisible() {
		wait.until(driverRef -> {
			final String text = driverRef.findElement(By.tagName("body")).getText();
			final String collapsed = collapseWhitespace(text);
			return collapsed.contains("@") && collapsed.matches(".*\\b[A-Za-z]{3,}\\s+[A-Za-z]{3,}.*");
		});
	}

	private void ensureLegalContentVisible() {
		wait.until(driverRef -> {
			final String text = collapseWhitespace(driverRef.findElement(By.tagName("body")).getText());
			return text.length() > 120;
		});
	}

	private WebElement ensureTextVisible(final String... textCandidates) {
		for (final String text : textCandidates) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(textLocator(text)));
			} catch (final TimeoutException timeoutException) {
				// try next candidate
			}
		}

		throw new IllegalStateException("Expected visible text not found: " + String.join(", ", textCandidates));
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(textLocator(text)));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void waitForTextToDisappear(final String text) {
		new WebDriverWait(driver, SHORT_WAIT)
				.until(ExpectedConditions.invisibilityOfElementLocated(textLocator(text)));
	}

	private By textLocator(final String text) {
		return By.xpath("//*[not(self::html) and not(self::body) and (normalize-space(.)=" + xpathLiteral(text)
				+ " or contains(normalize-space(.), " + xpathLiteral(text) + "))]");
	}

	private void waitForUiToLoad() {
		wait.until(pageIsReady());
		waitForBusyIndicators();
	}

	private ExpectedCondition<Boolean> pageIsReady() {
		return driverRef -> "complete".equals(js.executeScript("return document.readyState"));
	}

	private void waitForBusyIndicators() {
		final List<By> indicators = List.of(By.cssSelector("[aria-busy='true']"), By.cssSelector(".loading"),
				By.cssSelector(".loader"), By.cssSelector(".spinner"), By.cssSelector("[data-testid*='loading']"));

		for (final By indicator : indicators) {
			try {
				new WebDriverWait(driver, SHORT_WAIT)
						.until(ExpectedConditions.or(ExpectedConditions.invisibilityOfElementLocated(indicator),
								drv -> drv.findElements(indicator).isEmpty()));
			} catch (final TimeoutException ignored) {
				// Keep moving if this app does not use the checked indicator.
			}
		}
	}

	private void scrollIntoView(final WebElement element) {
		js.executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
	}

	private String takeViewportScreenshot(final String prefix) throws IOException {
		final Path destination = evidenceDir.resolve(System.currentTimeMillis() + "-" + sanitizeFileName(prefix) + ".png");
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
		return destination.toString();
	}

	private String takeFullPageScreenshot(final String prefix) throws IOException {
		final Dimension original = driver.manage().window().getSize();
		try {
			final Number width = (Number) js.executeScript(
					"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, document.documentElement.clientWidth);");
			final Number height = (Number) js.executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, document.documentElement.clientHeight);");
			driver.manage().window().setSize(new Dimension(width.intValue(), height.intValue()));
			waitForUiToLoad();
			return takeViewportScreenshot(prefix + "-full");
		} finally {
			driver.manage().window().setSize(original);
			waitForUiToLoad();
		}
	}

	private boolean allStepsPassed() {
		for (final StepResult result : results.values()) {
			if (!"PASS".equals(result.status)) {
				return false;
			}
		}
		return true;
	}

	private String buildFailureSummary() {
		final List<String> failures = new ArrayList<>();
		for (final StepResult result : results.values()) {
			if (!"PASS".equals(result.status)) {
				failures.add(result.name + ": " + result.error);
			}
		}
		return failures.isEmpty() ? "All validations passed." : "Validation failures -> " + String.join(" | ", failures);
	}

	private void writeFinalReport() throws IOException {
		Files.createDirectories(reportFile.getParent());
		Files.writeString(reportFile, toJsonReport());
	}

	private String toJsonReport() {
		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n");
		json.append("  \"results\": [\n");

		int index = 0;
		for (final StepResult result : results.values()) {
			if (index++ > 0) {
				json.append(",\n");
			}
			json.append("    {\n");
			json.append("      \"step\": \"").append(escapeJson(result.name)).append("\",\n");
			json.append("      \"status\": \"").append(escapeJson(result.status)).append("\",\n");
			json.append("      \"error\": ");
			if (result.error == null) {
				json.append("null,\n");
			} else {
				json.append("\"").append(escapeJson(result.error)).append("\",\n");
			}
			json.append("      \"evidence\": {\n");
			int evidenceIndex = 0;
			for (final Map.Entry<String, String> entry : result.evidence.entrySet()) {
				if (evidenceIndex++ > 0) {
					json.append(",\n");
				}
				json.append("        \"").append(escapeJson(entry.getKey())).append("\": \"")
						.append(escapeJson(entry.getValue())).append("\"");
			}
			json.append("\n      }\n");
			json.append("    }");
		}

		json.append("\n  ],\n");
		json.append("  \"summary\": {\n");
		json.append("    \"Login\": \"").append(getStatus(STEP_LOGIN)).append("\",\n");
		json.append("    \"Mi Negocio menu\": \"").append(getStatus(STEP_MENU)).append("\",\n");
		json.append("    \"Agregar Negocio modal\": \"").append(getStatus(STEP_MODAL)).append("\",\n");
		json.append("    \"Administrar Negocios view\": \"").append(getStatus(STEP_ADMIN)).append("\",\n");
		json.append("    \"Informaci\\u00f3n General\": \"").append(getStatus(STEP_INFO)).append("\",\n");
		json.append("    \"Detalles de la Cuenta\": \"").append(getStatus(STEP_ACCOUNT_DETAILS)).append("\",\n");
		json.append("    \"Tus Negocios\": \"").append(getStatus(STEP_BUSINESSES)).append("\",\n");
		json.append("    \"T\\u00e9rminos y Condiciones\": \"").append(getStatus(STEP_TERMS)).append("\",\n");
		json.append("    \"Pol\\u00edtica de Privacidad\": \"").append(getStatus(STEP_PRIVACY)).append("\"\n");
		json.append("  }\n");
		json.append("}\n");
		return json.toString();
	}

	private String getStatus(final String step) {
		final StepResult result = results.get(step);
		return result == null ? "NOT_RUN" : result.status;
	}

	private String safeMessage(final Exception exception) {
		final String message = exception.getMessage();
		return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
	}

	private String sanitizeFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String collapseWhitespace(final String value) {
		return value.replaceAll("\\s+", " ").trim();
	}

	private String getConfig(final String property, final String env) {
		final String fromProperty = System.getProperty(property);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}

		final String fromEnv = System.getenv(env);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		return null;
	}

	private boolean isHeadlessEnabled() {
		final String config = getConfig("saleads.headless", "SALEADS_HEADLESS");
		if (config == null) {
			return true;
		}

		return Boolean.parseBoolean(config);
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder literal = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				literal.append(", \"'\", ");
			}
			literal.append("'").append(parts[i]).append("'");
		}
		literal.append(")");
		return literal.toString();
	}

	private String escapeJson(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private interface StepAction {
		Map<String, String> run() throws Exception;
	}

	private static final class StepResult {
		private String name;
		private String status = "NOT_RUN";
		private String error;
		private final Map<String, String> evidence = new LinkedHashMap<>();
	}
}
