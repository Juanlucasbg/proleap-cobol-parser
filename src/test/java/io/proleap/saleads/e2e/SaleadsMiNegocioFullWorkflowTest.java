package io.proleap.saleads.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MENU = "Mi Negocio menu";
	private static final String REPORT_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN = "Administrar Negocios view";
	private static final String REPORT_INFO = "Informaci\u00f3n General";
	private static final String REPORT_DETAILS = "Detalles de la Cuenta";
	private static final String REPORT_BUSINESS = "Tus Negocios";
	private static final String REPORT_TERMS = "T\u00e9rminos y Condiciones";
	private static final String REPORT_PRIVACY = "Pol\u00edtica de Privacidad";

	private static final List<String> TEXT_INFORMACION_GENERAL = Arrays.asList("Informaci\u00f3n General",
			"Informacion General");
	private static final List<String> TEXT_SECCION_LEGAL = Arrays.asList("Secci\u00f3n Legal", "Seccion Legal");
	private static final List<String> TEXT_TERMINOS = Arrays.asList("T\u00e9rminos y Condiciones",
			"Terminos y Condiciones");
	private static final List<String> TEXT_PRIVACIDAD = Arrays.asList("Pol\u00edtica de Privacidad",
			"Politica de Privacidad");

	private final Map<String, StepResult> reportResults = new LinkedHashMap<>();
	private final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withLocale(Locale.ROOT).withZone(ZoneOffset.UTC);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private Path reportFile;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		initReport();
		setUpDriver();

		try {
			final boolean loginOk = runStep(REPORT_LOGIN, this::validateLoginWithGoogle);
			final boolean menuOk = loginOk ? runStep(REPORT_MENU, this::openMiNegocioMenu) : markBlocked(REPORT_MENU, REPORT_LOGIN);
			final boolean modalOk = menuOk ? runStep(REPORT_MODAL, this::validateAgregarNegocioModal)
					: markBlocked(REPORT_MODAL, REPORT_MENU);
			final boolean adminOk = modalOk ? runStep(REPORT_ADMIN, this::openAdministrarNegocios)
					: markBlocked(REPORT_ADMIN, REPORT_MODAL);
			final boolean infoOk = adminOk ? runStep(REPORT_INFO, this::validateInformacionGeneral)
					: markBlocked(REPORT_INFO, REPORT_ADMIN);
			final boolean detailsOk = infoOk ? runStep(REPORT_DETAILS, this::validateDetallesCuenta)
					: markBlocked(REPORT_DETAILS, REPORT_INFO);
			final boolean businessOk = detailsOk ? runStep(REPORT_BUSINESS, this::validateTusNegocios)
					: markBlocked(REPORT_BUSINESS, REPORT_DETAILS);
			final boolean termsOk = businessOk ? runStep(REPORT_TERMS, this::validateTerminosYCondiciones)
					: markBlocked(REPORT_TERMS, REPORT_BUSINESS);
			if (termsOk) {
				runStep(REPORT_PRIVACY, this::validatePoliticaPrivacidad);
			} else {
				markBlocked(REPORT_PRIVACY, REPORT_TERMS);
			}
		} finally {
			writeFinalReport();
			tearDownDriver();
		}

		assertAllPassed();
	}

	@After
	public void afterEach() {
		tearDownDriver();
	}

	private void setUpDriver() throws Exception {
		final boolean enabled = readBoolean("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false);
		Assume.assumeTrue(
				"Set -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true to run the SaleADS E2E workflow.",
				enabled);

		final String timestamp = timestampFormatter.format(OffsetDateTime.now(ZoneOffset.UTC));
		screenshotDirectory = Files.createDirectories(Paths.get("target", "saleads-e2e", "screenshots", timestamp));
		final Path reportDirectory = Files.createDirectories(Paths.get("target", "saleads-e2e", "reports"));
		reportFile = reportDirectory.resolve("saleads-mi-negocio-full-report-" + timestamp + ".md");

		final ChromeOptions options = new ChromeOptions();
		if (readBoolean("saleads.headless", "SALEADS_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		final String remoteUrl = firstNonBlank(System.getProperty("selenium.remote.url"), System.getenv("SELENIUM_REMOTE_URL"));
		if (remoteUrl != null) {
			driver = new RemoteWebDriver(parseUrl(remoteUrl), options);
		} else {
			driver = new ChromeDriver(options);
		}
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"Provide -Dsaleads.login.url or SALEADS_LOGIN_URL pointing to the SaleADS login page in the current environment.",
				loginUrl != null && !loginUrl.isBlank());
		driver.get(loginUrl);
		waitForUiLoad();
	}

	private void tearDownDriver() {
		if (driver != null) {
			driver.quit();
			driver = null;
		}
	}

	private void validateLoginWithGoogle() throws Exception {
		clickFirstVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesion con Google", "Continuar con Google",
				"Login with Google", "Google"));
		waitForUiLoad();
		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");

		final List<By> sidebarLocators = Arrays.asList(By.xpath("//aside"), By.xpath("//nav"),
				By.xpath("//*[self::div or self::section][.//*[contains(normalize-space(.), 'Negocio')]]"));
		waitForAnyVisible(sidebarLocators, DEFAULT_TIMEOUT);
		waitForVisibleText("Negocio", DEFAULT_TIMEOUT);
		captureScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws Exception {
		clickTextIfVisible("Negocio", SHORT_TIMEOUT);
		clickVisibleText("Mi Negocio", DEFAULT_TIMEOUT);

		waitForVisibleText("Agregar Negocio", DEFAULT_TIMEOUT);
		waitForVisibleText("Administrar Negocios", DEFAULT_TIMEOUT);
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickVisibleText("Agregar Negocio", DEFAULT_TIMEOUT);
		waitForVisibleText("Crear Nuevo Negocio", DEFAULT_TIMEOUT);
		waitForAnyVisible(Arrays.asList(
				By.xpath(
						"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//input")), DEFAULT_TIMEOUT);
		waitForVisibleText("Tienes 2 de 3 negocios", DEFAULT_TIMEOUT);
		waitForVisibleText("Cancelar", DEFAULT_TIMEOUT);
		waitForVisibleText("Crear Negocio", DEFAULT_TIMEOUT);
		captureScreenshot("03-crear-nuevo-negocio-modal");

		final WebElement nombreNegocio = waitForAnyVisible(Arrays.asList(
				By.xpath(
						"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//input")), SHORT_TIMEOUT);
		nombreNegocio.click();
		nombreNegocio.clear();
		nombreNegocio.sendKeys("Negocio Prueba Automatizacion");
		clickVisibleText("Cancelar", DEFAULT_TIMEOUT);

		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space(.)='Crear Nuevo Negocio']")));
		waitForUiLoad();
	}

	private void openAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios")) {
			clickVisibleText("Mi Negocio", DEFAULT_TIMEOUT);
		}
		clickVisibleText("Administrar Negocios", DEFAULT_TIMEOUT);
		waitForUiLoad();

		waitForVisibleTextAny(TEXT_INFORMACION_GENERAL, DEFAULT_TIMEOUT);
		waitForVisibleText("Detalles de la Cuenta", DEFAULT_TIMEOUT);
		waitForVisibleText("Tus Negocios", DEFAULT_TIMEOUT);
		waitForVisibleTextAny(TEXT_SECCION_LEGAL, DEFAULT_TIMEOUT);
		captureScreenshot("04-administrar-negocios-page");
	}

	private void validateInformacionGeneral() {
		waitForVisibleTextAny(TEXT_INFORMACION_GENERAL, DEFAULT_TIMEOUT);
		final String sectionText = extractSectionText(TEXT_INFORMACION_GENERAL);
		final String expectedEmail = firstNonBlank(System.getProperty("saleads.expected.user.email"),
				System.getenv("SALEADS_EXPECTED_USER_EMAIL"), "juanlucasbarbiergarzon@gmail.com");

		assertTrue("Expected user email was not visible in Informacion General.", sectionText.contains(expectedEmail));
		assertTrue("Expected BUSINESS PLAN text was not visible.", sectionText.contains("BUSINESS PLAN"));
		assertTrue("Expected Cambiar Plan button/text was not visible.", sectionText.contains("Cambiar Plan"));

		final boolean hasNameCandidate = sectionText.lines().map(String::trim)
				.anyMatch(line -> line.length() >= 3 && !line.contains("@")
						&& !line.equalsIgnoreCase("Informaci\u00f3n General")
						&& !line.equalsIgnoreCase("Informacion General")
						&& !line.equalsIgnoreCase("BUSINESS PLAN") && !line.equalsIgnoreCase("Cambiar Plan"));
		assertTrue("A user name-like value was not found in Informacion General.", hasNameCandidate);
	}

	private void validateDetallesCuenta() {
		final String detailsText = extractSectionText("Detalles de la Cuenta");
		assertTrue("Expected 'Cuenta creada' was not visible.", detailsText.contains("Cuenta creada"));
		assertTrue("Expected 'Estado activo' was not visible.", detailsText.contains("Estado activo"));
		assertTrue("Expected 'Idioma seleccionado' was not visible.", detailsText.contains("Idioma seleccionado"));
	}

	private void validateTusNegocios() {
		final String businessText = extractSectionText("Tus Negocios");
		assertTrue("Expected 'Agregar Negocio' was not visible in Tus Negocios.", businessText.contains("Agregar Negocio"));
		assertTrue("Expected 'Tienes 2 de 3 negocios' was not visible in Tus Negocios.",
				businessText.contains("Tienes 2 de 3 negocios"));

		final List<WebElement> businessRows = driver.findElements(By.xpath(
				"//*[normalize-space(.)='Tus Negocios']/ancestor::*[self::section or self::div][1]//*[self::li or self::tr or self::article][string-length(normalize-space(.)) > 1]"));
		assertFalse("Business list was not detected in Tus Negocios section.", businessRows.isEmpty());
	}

	private void validateTerminosYCondiciones() throws Exception {
		termsUrl = openLegalLinkAndValidate(TEXT_TERMINOS, "05-terminos-y-condiciones");
	}

	private void validatePoliticaPrivacidad() throws Exception {
		privacyUrl = openLegalLinkAndValidate(TEXT_PRIVACIDAD, "06-politica-de-privacidad");
	}

	private String openLegalLinkAndValidate(final List<String> linkTexts, final String screenshotName) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		clickFirstVisibleText(linkTexts);

		String activeWindow = appWindow;
		try {
			wait.until(ExpectedConditions.numberOfWindowsToBe(handlesBefore.size() + 1));
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					activeWindow = handle;
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			activeWindow = appWindow;
		}

		waitForUiLoad();
		waitForVisibleTextAny(linkTexts, DEFAULT_TIMEOUT);

		final String pageText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal content text was not visible for " + linkTexts.get(0) + ".", pageText.trim().length() > 200);

		captureScreenshot(screenshotName);
		final String url = driver.getCurrentUrl();

		if (!activeWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}
		waitForUiLoad();
		return url;
	}

	private void initReport() {
		reportResults.put(REPORT_LOGIN, StepResult.pending());
		reportResults.put(REPORT_MENU, StepResult.pending());
		reportResults.put(REPORT_MODAL, StepResult.pending());
		reportResults.put(REPORT_ADMIN, StepResult.pending());
		reportResults.put(REPORT_INFO, StepResult.pending());
		reportResults.put(REPORT_DETAILS, StepResult.pending());
		reportResults.put(REPORT_BUSINESS, StepResult.pending());
		reportResults.put(REPORT_TERMS, StepResult.pending());
		reportResults.put(REPORT_PRIVACY, StepResult.pending());
	}

	private boolean runStep(final String reportField, final CheckedRunnable action) {
		try {
			action.run();
			reportResults.put(reportField, StepResult.pass("All validations passed."));
			return true;
		} catch (final Throwable e) {
			final String screenshot = safeFailureScreenshot(reportField);
			reportResults.put(reportField, StepResult.fail(e.getMessage() + screenshot));
			return false;
		}
	}

	private boolean markBlocked(final String reportField, final String blockedBy) {
		reportResults.put(reportField, StepResult.fail("Blocked because '" + blockedBy + "' did not pass."));
		return false;
	}

	private void writeFinalReport() throws IOException {
		if (reportFile == null) {
			return;
		}

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("# SaleADS Mi Negocio Full Workflow Report\n\n");
		reportBuilder.append("- Executed at (UTC): ").append(OffsetDateTime.now(ZoneOffset.UTC)).append('\n');
		reportBuilder.append("- Screenshot directory: ").append(screenshotDirectory).append('\n');
		reportBuilder.append("- Terms URL: ").append(termsUrl).append('\n');
		reportBuilder.append("- Privacy URL: ").append(privacyUrl).append("\n\n");
		reportBuilder.append("## PASS / FAIL by step\n\n");

		for (final Map.Entry<String, StepResult> entry : reportResults.entrySet()) {
			final StepResult result = entry.getValue();
			reportBuilder.append("- ").append(entry.getKey()).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (result.details != null && !result.details.isBlank()) {
				reportBuilder.append(" - ").append(result.details.replace('\n', ' '));
			}
			reportBuilder.append('\n');
		}

		Files.writeString(reportFile, reportBuilder.toString(), StandardCharsets.UTF_8);
	}

	private void assertAllPassed() {
		final List<String> failedFields = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : reportResults.entrySet()) {
			if (!entry.getValue().passed) {
				failedFields.add(entry.getKey());
			}
		}
		assertTrue("Failed fields: " + failedFields + ". See report file: " + reportFile, failedFields.isEmpty());
	}

	private void clickFirstVisibleText(final List<String> textCandidates) {
		Throwable lastError = null;
		for (final String text : textCandidates) {
			try {
				clickVisibleText(text, SHORT_TIMEOUT);
				return;
			} catch (final Throwable e) {
				lastError = e;
			}
		}
		throw new IllegalStateException("Unable to click any candidate text: " + textCandidates, lastError);
	}

	private void clickTextIfVisible(final String text, final Duration timeout) {
		try {
			clickVisibleText(text, timeout);
		} catch (final Exception ignored) {
		}
	}

	private void clickVisibleText(final String text, final Duration timeout) {
		WebElement element = null;
		final List<By> locators = textLocators(text);
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);

		for (final By locator : locators) {
			try {
				element = localWait.until(ExpectedConditions.elementToBeClickable(locator));
				break;
			} catch (final TimeoutException ignored) {
			}
		}

		if (element == null) {
			throw new IllegalStateException("Could not find clickable element with text: " + text);
		}

		scrollIntoView(element);
		element.click();
		waitForUiLoad();
	}

	private void selectGoogleAccountIfVisible(final String email) {
		try {
			clickVisibleText(email, SHORT_TIMEOUT);
		} catch (final Exception ignored) {
		}
	}

	private void waitForUiLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
	}

	private WebElement waitForAnyVisible(final List<By> locators, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		for (final By locator : locators) {
			try {
				return localWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final TimeoutException ignored) {
			}
		}
		throw new IllegalStateException("None of the expected locators were visible: " + locators);
	}

	private void waitForVisibleText(final String text, final Duration timeout) {
		waitForAnyVisible(textLocators(text), timeout);
	}

	private void waitForVisibleTextAny(final List<String> textCandidates, final Duration timeout) {
		Throwable lastError = null;
		for (final String candidate : textCandidates) {
			try {
				waitForVisibleText(candidate, timeout);
				return;
			} catch (final Throwable e) {
				lastError = e;
			}
		}
		throw new IllegalStateException("None of the expected texts became visible: " + textCandidates, lastError);
	}

	private String extractSectionText(final List<String> headingCandidates) {
		for (final String headingText : headingCandidates) {
			try {
				return extractSectionText(headingText);
			} catch (final Throwable ignored) {
			}
		}
		throw new IllegalStateException("Could not resolve section for headings: " + headingCandidates);
	}

	private String extractSectionText(final String headingText) {
		final String escapedHeading = escapeXPathText(headingText);
		final By sectionBy = By.xpath("//*[normalize-space(.)=" + escapedHeading
				+ "]/ancestor::*[self::section or self::article or self::div][1]");
		final WebElement section = waitForAnyVisible(Arrays.asList(sectionBy), DEFAULT_TIMEOUT);
		return section.getText();
	}

	private boolean isTextVisible(final String text) {
		for (final By locator : textLocators(text)) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private String captureScreenshot(final String baseName) throws IOException {
		final Path screenshotPath = screenshotDirectory.resolve(baseName + ".png");
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
		return screenshotPath.toString();
	}

	private String safeFailureScreenshot(final String reportField) {
		try {
			final String screenshotPath = captureScreenshot("failure-" + sanitize(reportField));
			return " | failure screenshot: " + screenshotPath;
		} catch (final Exception ignored) {
			return "";
		}
	}

	private List<By> textLocators(final String text) {
		final String escapedText = escapeXPathText(text);
		final List<By> locators = new ArrayList<>();
		locators.add(By.xpath("//*[normalize-space(.)=" + escapedText + "]"));
		locators.add(By.xpath(
				"//*[self::button or self::a or self::span or self::div or self::p or self::h1 or self::h2 or self::h3][contains(normalize-space(.), "
						+ escapedText + ")]"));
		return locators;
	}

	private URL parseUrl(final String urlText) throws MalformedURLException {
		return new URL(urlText);
	}

	private String escapeXPathText(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		final String[] parts = text.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private boolean readBoolean(final String propertyName, final String envName, final boolean defaultValue) {
		final String rawValue = firstNonBlank(System.getProperty(propertyName), System.getenv(envName));
		if (rawValue == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(rawValue);
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private String sanitize(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pending() {
			return new StepResult(false, "Not executed.");
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
