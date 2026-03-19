package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation of the SaleADS.ai Mi Negocio module workflow.
 *
 * <p>
 * Configuration:
 * <ul>
 * <li>Set login URL using -Dsaleads.login.url or SALEADS_LOGIN_URL</li>
 * <li>Set Google account using -Dsaleads.google.account or SALEADS_GOOGLE_ACCOUNT</li>
 * <li>Set headless mode using -Dsaleads.headless or SALEADS_HEADLESS (default: true)</li>
 * </ul>
 *
 * <p>
 * Run only this test:
 *
 * <pre>
 * mvn -Dtest=SaleadsMiNegocioFullTest \
 *     -Dsaleads.login.url="https://your-env.example/login" \
 *     test
 * </pre>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final List<String> LEGAL_HEADING_HINTS = Arrays.asList("Términos y Condiciones", "Politica de Privacidad",
			"Política de Privacidad", "Terms and Conditions", "Privacy Policy");

	private final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> details = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;
	private String termsUrl;
	private String privacyUrl;

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--lang=es-419");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);
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
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaDePrivacidad);

		assertTrue("One or more workflow validations failed. Check target/saleads-evidence final-report.txt.", allStepsPassed());
	}

	private void stepLoginWithGoogle() throws Exception {
		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", null);
		if (loginUrl == null || loginUrl.isBlank()) {
			throw new IllegalStateException("Missing login URL. Set -Dsaleads.login.url or SALEADS_LOGIN_URL.");
		}

		driver.get(loginUrl);
		waitForUiLoad();

		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickUsingVisibleTextCandidates("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google", "Google");
		waitForUiLoad();
		switchToNewestWindowIfOpened(handlesBeforeClick);

		final String googleAccount = readConfig("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);
		selectGoogleAccountIfVisible(googleAccount);
		waitForApplicationShell();

		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		waitForAnyTextVisible("Negocio", "Mi Negocio");
		clickUsingVisibleTextCandidates("Negocio");
		waitForUiLoad();
		clickUsingVisibleTextCandidates("Mi Negocio");
		waitForUiLoad();

		assertTextVisible("Agregar Negocio", Duration.ofSeconds(20));
		assertTextVisible("Administrar Negocios", Duration.ofSeconds(20));

		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickUsingVisibleTextCandidates("Agregar Negocio");
		waitForUiLoad();

		assertTextVisible("Crear Nuevo Negocio", Duration.ofSeconds(20));
		final WebElement nombreInput = waitForVisibleElement(By.xpath(
				"//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder,'Nombre del Negocio')]"),
				Duration.ofSeconds(15));
		assertTrue("Input 'Nombre del Negocio' is not visible.", nombreInput.isDisplayed());
		assertTextVisible("Tienes 2 de 3 negocios", Duration.ofSeconds(10));
		assertTextVisible("Cancelar", Duration.ofSeconds(10));
		assertTextVisible("Crear Negocio", Duration.ofSeconds(10));

		captureScreenshot("03-agregar-negocio-modal");

		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatizacion");
		waitForUiLoad();
		clickUsingVisibleTextCandidates("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioExpanded();

		clickUsingVisibleTextCandidates("Administrar Negocios");
		waitForUiLoad();

		assertTextVisible("Información General", Duration.ofSeconds(30));
		assertTextVisible("Detalles de la Cuenta", Duration.ofSeconds(30));
		assertTextVisible("Tus Negocios", Duration.ofSeconds(30));
		assertTextVisible("Sección Legal", Duration.ofSeconds(30));

		driver.manage().window().setSize(new Dimension(1920, 2200));
		waitForUiLoad();
		captureScreenshot("04-administrar-negocios-view");
		driver.manage().window().setSize(new Dimension(1920, 1080));
	}

	private void stepValidateInformacionGeneral() {
		final String sectionText = extractSectionText("Información General");
		final boolean hasEmail = EMAIL_PATTERN.matcher(sectionText).find();
		final boolean hasBusinessPlan = containsIgnoreAccents(sectionText, "BUSINESS PLAN");
		final boolean hasCambiarPlan = containsIgnoreAccents(sectionText, "Cambiar Plan");
		final boolean hasUserNameLikeValue = hasNameLikeContent(sectionText);

		assertTrue("User name is not visible in 'Información General'.", hasUserNameLikeValue);
		assertTrue("User email is not visible in 'Información General'.", hasEmail);
		assertTrue("Text 'BUSINESS PLAN' is not visible.", hasBusinessPlan);
		assertTrue("Button 'Cambiar Plan' is not visible.", hasCambiarPlan);
	}

	private void stepValidateDetallesCuenta() {
		final String sectionText = extractSectionText("Detalles de la Cuenta");

		assertTrue("'Cuenta creada' is not visible.", containsIgnoreAccents(sectionText, "Cuenta creada"));
		assertTrue("'Estado activo' is not visible.", containsIgnoreAccents(sectionText, "Estado activo"));
		assertTrue("'Idioma seleccionado' is not visible.", containsIgnoreAccents(sectionText, "Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final String sectionText = extractSectionText("Tus Negocios");

		final boolean hasBusinessList = sectionText.lines().map(String::trim).anyMatch(line -> !line.isEmpty() && !containsIgnoreAccents(line, "Tus Negocios"));
		assertTrue("Business list is not visible in 'Tus Negocios'.", hasBusinessList);
		assertTrue("Button 'Agregar Negocio' is missing in 'Tus Negocios'.", containsIgnoreAccents(sectionText, "Agregar Negocio"));
		assertTrue("Text 'Tienes 2 de 3 negocios' is missing in 'Tus Negocios'.", containsIgnoreAccents(sectionText, "Tienes 2 de 3 negocios"));
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		waitForAnyTextVisible("Términos y Condiciones", "Terminos y Condiciones");

		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickUsingVisibleTextCandidates("Términos y Condiciones", "Terminos y Condiciones");
		waitForUiLoad();

		final boolean switchedToNewTab = switchToNewestWindowIfOpened(handlesBeforeClick);
		validateLegalPage("Términos y Condiciones", "Terminos y Condiciones");
		termsUrl = driver.getCurrentUrl();
		captureScreenshot("05-terminos-y-condiciones");

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void stepValidatePoliticaDePrivacidad() throws Exception {
		waitForAnyTextVisible("Política de Privacidad", "Politica de Privacidad", "Privacy Policy");

		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickUsingVisibleTextCandidates("Política de Privacidad", "Politica de Privacidad", "Privacy Policy");
		waitForUiLoad();

		final boolean switchedToNewTab = switchToNewestWindowIfOpened(handlesBeforeClick);
		validateLegalPage("Política de Privacidad", "Politica de Privacidad", "Privacy Policy");
		privacyUrl = driver.getCurrentUrl();
		captureScreenshot("06-politica-de-privacidad");

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String reportField, final CheckedRunnable stepLogic) {
		try {
			stepLogic.run();
			report.put(reportField, Boolean.TRUE);
			details.put(reportField, "PASS");
		} catch (final Throwable ex) {
			report.put(reportField, Boolean.FALSE);
			details.put(reportField, "FAIL - " + sanitize(ex.getMessage()));

			try {
				captureScreenshot("error-" + toFileSafeName(reportField));
			} catch (final Exception ignored) {
				// Ignore screenshot failure in error path.
			}
		}
	}

	private void waitForApplicationShell() {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(90).toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (isAnyTextVisibleQuick("Negocio", "Mi Negocio")) {
					appWindowHandle = handle;
					return;
				}
				selectGoogleAccountIfVisible(readConfig("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT));
			}
			sleepMillis(750);
		}
		throw new TimeoutException("Could not detect application shell after Google login.");
	}

	private void ensureMiNegocioExpanded() {
		if (isAnyTextVisibleQuick("Administrar Negocios")) {
			return;
		}
		if (isAnyTextVisibleQuick("Mi Negocio")) {
			clickUsingVisibleTextCandidates("Mi Negocio");
			waitForUiLoad();
		}
		if (!isAnyTextVisibleQuick("Administrar Negocios") && isAnyTextVisibleQuick("Negocio")) {
			clickUsingVisibleTextCandidates("Negocio");
			waitForUiLoad();
			clickUsingVisibleTextCandidates("Mi Negocio");
			waitForUiLoad();
		}
		assertTextVisible("Administrar Negocios", Duration.ofSeconds(15));
	}

	private void validateLegalPage(final String... headingCandidates) {
		waitForUiLoad();
		waitForVisibleElement(textLocator(headingCandidates), Duration.ofSeconds(30));

		final String pageText = visiblePageText();
		assertTrue("Expected legal heading not found.", containsOne(pageText, headingCandidates));
		assertTrue("Legal content text is not visible.", pageText != null && pageText.trim().length() > 200);
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final List<By> locators = new ArrayList<>();
		locators.add(By.xpath("//*[contains(normalize-space()," + xpathLiteral(email) + ")]"));
		locators.add(By.xpath("//*[contains(normalize-space(),'Choose an account')]"));
		locators.add(By.xpath("//*[contains(normalize-space(),'Elige una cuenta')]"));

		for (final By locator : locators) {
			final List<WebElement> candidates = driver.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					try {
						if (candidate.getText().contains(email)) {
							candidate.click();
							waitForUiLoad();
							return;
						}
					} catch (final Exception ignored) {
						// Continue trying alternative elements.
					}
				}
			}
		}
	}

	private void clickUsingVisibleTextCandidates(final String... textCandidates) {
		Exception lastException = null;
		for (final String text : textCandidates) {
			try {
				clickUsingVisibleText(text);
				return;
			} catch (final Exception ex) {
				lastException = ex;
			}
		}
		throw new NoSuchElementException("Could not click any visible text candidate: " + Arrays.toString(textCandidates)
				+ ". Last error: " + sanitize(lastException == null ? null : lastException.getMessage()));
	}

	private void clickUsingVisibleText(final String text) {
		final WebElement element = waitForVisibleElement(By.xpath(
				"//*[self::button or self::a or self::span or self::div or @role='button' or @role='menuitem'][contains(normalize-space(),"
						+ xpathLiteral(text) + ")]"),
				Duration.ofSeconds(20));
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void assertTextVisible(final String text, final Duration timeout) {
		waitForVisibleElement(textLocator(text), timeout);
	}

	private void waitForAnyTextVisible(final String... texts) {
		waitForVisibleElement(textLocator(texts), Duration.ofSeconds(30));
	}

	private boolean isAnyTextVisibleQuick(final String... texts) {
		try {
			final List<WebElement> elements = driver.findElements(textLocator(texts));
			return elements.stream().anyMatch(WebElement::isDisplayed);
		} catch (final Exception ex) {
			return false;
		}
	}

	private String extractSectionText(final String headingText) {
		final WebElement heading = waitForVisibleElement(By.xpath("//*[contains(normalize-space()," + xpathLiteral(headingText) + ")]"),
				Duration.ofSeconds(30));
		WebElement container = heading;

		for (int i = 0; i < 6; i++) {
			try {
				container = container.findElement(By.xpath("./.."));
			} catch (final Exception ex) {
				break;
			}
			final String containerText = container.getText();
			if (containerText != null && containsIgnoreAccents(containerText, headingText) && containerText.length() > 30) {
				return containerText;
			}
		}
		return heading.getText();
	}

	private void waitForUiLoad() {
		wait.until((ExpectedCondition<Boolean>) drv -> {
			final Object state = ((JavascriptExecutor) drv).executeScript("return document.readyState");
			return Objects.equals("complete", state);
		});
	}

	private boolean switchToNewestWindowIfOpened(final Set<String> handlesBefore) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
		while (System.currentTimeMillis() < deadline) {
			final Set<String> handlesNow = driver.getWindowHandles();
			if (handlesNow.size() > handlesBefore.size()) {
				for (final String handle : handlesNow) {
					if (!handlesBefore.contains(handle)) {
						driver.switchTo().window(handle);
						waitForUiLoad();
						return true;
					}
				}
			}
			sleepMillis(250);
		}
		return false;
	}

	private WebElement waitForVisibleElement(final By locator, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		return localWait.until(drv -> {
			final List<WebElement> elements = drv.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private By textLocator(final String... texts) {
		final StringBuilder expression = new StringBuilder("//*[");
		for (int i = 0; i < texts.length; i++) {
			if (i > 0) {
				expression.append(" or ");
			}
			expression.append("contains(normalize-space(),").append(xpathLiteral(texts[i])).append(")");
		}
		expression.append("]");
		return By.xpath(expression.toString());
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final Path screenshotPath = evidenceDir.resolve(checkpointName + ".png");
		Files.write(screenshotPath, screenshot);
	}

	private boolean allStepsPassed() {
		return report.values().stream().allMatch(Boolean.TRUE::equals);
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test\n");
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append("\n\n");
		builder.append("Final Report:\n");

		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL");
			final String stepDetail = details.get(entry.getKey());
			if (stepDetail != null) {
				builder.append(" (").append(stepDetail).append(")");
			}
			builder.append("\n");
		}

		builder.append("\nCaptured URLs:\n");
		builder.append("- Términos y Condiciones URL: ").append(termsUrl == null ? "N/A" : termsUrl).append("\n");
		builder.append("- Política de Privacidad URL: ").append(privacyUrl == null ? "N/A" : privacyUrl).append("\n");

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, builder.toString());
	}

	private String readConfig(final String systemPropertyKey, final String envKey, final String defaultValue) {
		final String value = System.getProperty(systemPropertyKey);
		if (value != null && !value.isBlank()) {
			return value.trim();
		}
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		return defaultValue;
	}

	private String visiblePageText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception ex) {
			return "";
		}
	}

	private boolean containsOne(final String source, final String... values) {
		for (final String value : values) {
			if (containsIgnoreAccents(source, value)) {
				return true;
			}
		}
		for (final String legalHeadingHint : LEGAL_HEADING_HINTS) {
			if (containsIgnoreAccents(source, legalHeadingHint)) {
				return true;
			}
		}
		return false;
	}

	private boolean containsIgnoreAccents(final String source, final String expected) {
		if (source == null || expected == null) {
			return false;
		}
		final String normalizedSource = normalizeForMatch(source);
		final String normalizedExpected = normalizeForMatch(expected);
		return normalizedSource.contains(normalizedExpected);
	}

	private String normalizeForMatch(final String value) {
		return value.toLowerCase().replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
				.replace("ü", "u").replaceAll("\\s+", " ").trim();
	}

	private boolean hasNameLikeContent(final String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		final String[] lines = text.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine == null ? "" : rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			final String normalized = normalizeForMatch(line);
			if (normalized.contains("informacion general") || normalized.contains("business plan")
					|| normalized.contains("cambiar plan") || normalized.contains("plan")) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (line.length() >= 4) {
				return true;
			}
		}
		return false;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(",\"'\",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String toFileSafeName(final String input) {
		return input == null ? "unknown" : input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String sanitize(final String message) {
		if (message == null || message.isBlank()) {
			return "No details available";
		}
		return message.replaceAll("\\s+", " ").trim();
	}

	private void sleepMillis(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
