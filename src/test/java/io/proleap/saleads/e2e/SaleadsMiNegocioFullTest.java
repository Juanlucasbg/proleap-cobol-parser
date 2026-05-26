package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String DEFAULT_TYPED_BUSINESS_NAME = "Negocio Prueba Automatizaci\u00F3n";
	private static final Duration DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final List<String> REPORT_FIELDS = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Informaci\u00F3n General", "Detalles de la Cuenta", "Tus Negocios",
			"T\u00E9rminos y Condiciones", "Pol\u00EDtica de Privacidad");
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss",
			Locale.ROOT).withZone(ZoneOffset.UTC);

	private final Map<String, StepResult> results = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String googleAccountEmail;
	private String expectedUserEmail;

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to execute UI automation.",
				Boolean.parseBoolean(env("SALEADS_E2E_ENABLED", "false")));

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_WAIT_TIMEOUT);

		googleAccountEmail = env("SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);
		expectedUserEmail = env("SALEADS_USER_EMAIL", googleAccountEmail);

		final String timestamp = TIMESTAMP_FORMATTER.format(Instant.now());
		evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);

		driver.manage().window().setSize(new Dimension(1920, 1080));

		final String startUrl = env("SALEADS_START_URL", "").trim();
		if (!startUrl.isEmpty()) {
			driver.get(startUrl);
			waitForUiToLoad();
		} else {
			Assume.assumeTrue("SALEADS_START_URL must be configured for this test run.",
					!driver.getCurrentUrl().startsWith("about:blank"));
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void runMiNegocioWorkflow() throws Exception {
		initializeReportEntries();
		try {
			executeStep("Login", this::stepLoginWithGoogle);
			executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			executeStep("Informaci\u00F3n General", this::stepValidateInformacionGeneral);
			executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			executeStep("Tus Negocios", this::stepValidateTusNegocios);
			executeStep("T\u00E9rminos y Condiciones", this::stepValidateTerminosYCondiciones);
			executeStep("Pol\u00EDtica de Privacidad", this::stepValidatePoliticaPrivacidad);
		} finally {
			writeFinalReport();
		}

		final List<String> failures = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			if (!results.get(field).passed) {
				failures.add(field + ": " + results.get(field).details);
			}
		}

		assertTrue("Workflow validation failed. Evidence: " + evidenceDir.toAbsolutePath() + "\n"
				+ String.join("\n", failures), failures.isEmpty());
	}

	private WebDriver createDriver() {
		final String browser = env("SALEADS_BROWSER", "chrome").trim().toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			return new FirefoxDriver(firefoxOptions);
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.addArguments("--window-size=1920,1080");
			chromeOptions.addArguments("--disable-dev-shm-usage");
			chromeOptions.addArguments("--no-sandbox");
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			return new ChromeDriver(chromeOptions);
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		if (!isTextVisible("Mi Negocio", Duration.ofSeconds(8))
				&& !isTextVisible("Administrar Negocios", Duration.ofSeconds(8))) {
			clickFirstAvailableText(List.of("Sign in with Google", "Iniciar sesi\u00F3n con Google",
					"Continuar con Google", "Login with Google"));

			if (isTextVisible(googleAccountEmail, Duration.ofSeconds(12))) {
				clickByText(googleAccountEmail);
			}
		}

		assertTrue("Main interface did not load after login attempt.",
				waitForAnyText(List.of("Negocio", "Mi Negocio", "Administrar Negocios"), Duration.ofSeconds(60)));
		assertTrue("Left sidebar navigation is not visible.", isSidebarVisible());
		captureScreenshot("step-1-dashboard.png");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickIfVisible("Negocio");
		clickByText("Mi Negocio");

		assertTrue("Mi Negocio submenu did not expand for Agregar Negocio.",
				isTextVisible("Agregar Negocio", Duration.ofSeconds(20)));
		assertTrue("Mi Negocio submenu did not expand for Administrar Negocios.",
				isTextVisible("Administrar Negocios", Duration.ofSeconds(20)));
		captureScreenshot("step-2-mi-negocio-menu-expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByText("Agregar Negocio");

		assertTrue("Modal title Crear Nuevo Negocio was not visible.",
				isTextVisible("Crear Nuevo Negocio", Duration.ofSeconds(20)));
		assertTrue("Nombre del Negocio field was not visible.",
				isElementVisible(By.xpath(
						"//label[contains(normalize-space(), \"Nombre del Negocio\")]/following::input[1] | //input[contains(@placeholder, \"Nombre del Negocio\")] | //input[contains(@aria-label, \"Nombre del Negocio\")]"),
						Duration.ofSeconds(20)));
		assertTrue("Expected business limit text was not visible.", isTextVisible("Tienes 2 de 3 negocios",
				Duration.ofSeconds(20)));
		assertTrue("Cancelar button was not visible.", isTextVisible("Cancelar", Duration.ofSeconds(20)));
		assertTrue("Crear Negocio button was not visible.", isTextVisible("Crear Negocio", Duration.ofSeconds(20)));

		captureScreenshot("step-3-agregar-negocio-modal.png");

		final WebElement businessNameInput = waitForVisible(By.xpath(
				"//label[contains(normalize-space(), \"Nombre del Negocio\")]/following::input[1] | //input[contains(@placeholder, \"Nombre del Negocio\")] | //input[contains(@aria-label, \"Nombre del Negocio\")]"),
				Duration.ofSeconds(20));
		clickElement(businessNameInput);
		businessNameInput.clear();
		businessNameInput.sendKeys(DEFAULT_TYPED_BUSINESS_NAME);
		clickByText("Cancelar");

		assertTrue("Agregar Negocio modal did not close after Cancelar.",
				!isTextVisible("Crear Nuevo Negocio", Duration.ofSeconds(10)));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(3))) {
			clickIfVisible("Negocio");
			clickIfVisible("Mi Negocio");
		}
		clickByText("Administrar Negocios");

		assertTrue("Informaci\u00F3n General section was not visible.",
				isTextVisible("Informaci\u00F3n General", Duration.ofSeconds(30)));
		assertTrue("Detalles de la Cuenta section was not visible.",
				isTextVisible("Detalles de la Cuenta", Duration.ofSeconds(30)));
		assertTrue("Tus Negocios section was not visible.", isTextVisible("Tus Negocios", Duration.ofSeconds(30)));
		assertTrue("Secci\u00F3n Legal section was not visible.", isTextVisible("Secci\u00F3n Legal", Duration.ofSeconds(30)));

		captureFullPageScreenshot("step-4-administrar-negocios-full.png");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Informaci\u00F3n General");
		final String sectionText = normalize(section.getText());

		assertTrue("Expected user email was not visible in Informaci\u00F3n General.", sectionText.contains(expectedUserEmail));
		assertTrue("BUSINESS PLAN text was not visible in Informaci\u00F3n General.", sectionText.contains("BUSINESS PLAN"));
		assertTrue("Cambiar Plan button was not visible in Informaci\u00F3n General.", sectionText.contains("Cambiar Plan"));
		assertTrue("A user name was not detected in Informaci\u00F3n General.",
				isLikelyUserNameVisible(sectionText, expectedUserEmail));
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		final String sectionText = normalize(section.getText());

		assertTrue("Cuenta creada label missing in Detalles de la Cuenta.", sectionText.contains("Cuenta creada"));
		assertTrue("Estado activo label missing in Detalles de la Cuenta.", sectionText.contains("Estado activo"));
		assertTrue("Idioma seleccionado label missing in Detalles de la Cuenta.", sectionText.contains("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final String sectionText = normalize(section.getText());

		assertTrue("Business list section text was empty.", !sectionText.isBlank());
		assertTrue("Agregar Negocio button missing in Tus Negocios.",
				hasClickableTextInSection(section, "Agregar Negocio"));
		assertTrue("Expected business limit text missing in Tus Negocios.", sectionText.contains("Tienes 2 de 3 negocios"));
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		final String finalUrl = validateLegalLink("T\u00E9rminos y Condiciones", "T\u00E9rminos y Condiciones",
				"step-8-terminos-y-condiciones.png");
		capturedUrls.put("T\u00E9rminos y Condiciones URL", finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		final String finalUrl = validateLegalLink("Pol\u00EDtica de Privacidad", "Pol\u00EDtica de Privacidad",
				"step-9-politica-de-privacidad.png");
		capturedUrls.put("Pol\u00EDtica de Privacidad URL", finalUrl);
	}

	private String validateLegalLink(final String linkText, final String headingText, final String screenshotName)
			throws Exception {
		final String applicationWindowHandle = driver.getWindowHandle();
		final String previousUrl = driver.getCurrentUrl();
		final Set<String> previousHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickByText(linkText);

		new WebDriverWait(driver, Duration.ofSeconds(30)).until(webDriver -> {
			final boolean openedNewTab = webDriver.getWindowHandles().size() > previousHandles.size();
			final boolean navigatedInSameTab = !previousUrl.equals(webDriver.getCurrentUrl());
			return openedNewTab || navigatedInSameTab || isTextVisibleNow(headingText);
		});

		final Set<String> currentHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final boolean openedNewTab = currentHandles.size() > previousHandles.size();
		if (openedNewTab) {
			currentHandles.removeAll(previousHandles);
			driver.switchTo().window(currentHandles.iterator().next());
		}

		waitForUiToLoad();
		assertTrue("Expected legal heading was not visible for " + linkText + ".",
				isTextVisible(headingText, Duration.ofSeconds(30)));
		assertTrue("Legal content text was not visible for " + linkText + ".", isLegalContentVisible());
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(applicationWindowHandle);
			waitForUiToLoad();
		} else if (!driver.getCurrentUrl().equals(previousUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		assertTrue("Could not return to the application after validating " + linkText + ".",
				isTextVisible("Informaci\u00F3n General", Duration.ofSeconds(30))
						|| isTextVisible("Administrar Negocios", Duration.ofSeconds(30)));
		return finalUrl;
	}

	private void initializeReportEntries() {
		for (final String field : REPORT_FIELDS) {
			results.put(field, StepResult.fail("Step was not executed."));
		}
	}

	private void executeStep(final String reportField, final StepAction action) {
		try {
			action.run();
			results.put(reportField, StepResult.pass("All validations passed."));
		} catch (final Throwable throwable) {
			final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getMessage().replace('\n', ' ');
			results.put(reportField, StepResult.fail(message));
			safeCaptureScreenshot("failure-" + toSlug(reportField) + ".png");
		}
	}

	private void clickIfVisible(final String text) {
		if (isTextVisible(text, Duration.ofSeconds(5))) {
			clickByText(text);
		}
	}

	private void clickFirstAvailableText(final List<String> texts) {
		for (final String text : texts) {
			if (isTextVisible(text, Duration.ofSeconds(6))) {
				clickByText(text);
				return;
			}
		}
		throw new IllegalStateException("None of these options were clickable: " + texts);
	}

	private void clickByText(final String text) {
		final By locator = By.xpath(
				"//button[normalize-space()=" + quoteForXPath(text) + "] | //a[normalize-space()=" + quoteForXPath(text)
						+ "] | //*[@role='button' and normalize-space()=" + quoteForXPath(text)
						+ "] | //*[normalize-space()=" + quoteForXPath(text)
						+ "]/ancestor::*[self::button or self::a or @role='button'][1] | //*[normalize-space()="
						+ quoteForXPath(text) + "]");
		final WebElement element = waitForVisible(locator, Duration.ofSeconds(20));
		clickElement(element);
	}

	private void clickElement(final WebElement element) {
		wait.until(ExpectedConditions.visibilityOf(element));
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final Exception ignored) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private WebElement waitForVisible(final By locator, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(webDriver -> {
			for (final WebElement element : webDriver.findElements(locator)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private boolean isElementVisible(final By locator, final Duration timeout) {
		try {
			return waitForVisible(locator, timeout) != null;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private boolean waitForAnyText(final List<String> texts, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout)
					.until(webDriver -> texts.stream().anyMatch(this::isTextVisibleNow));
		} catch (final Exception ignored) {
			return false;
		}
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(webDriver -> isTextVisibleNow(text));
		} catch (final Exception ignored) {
			return false;
		}
	}

	private boolean isTextVisibleNow(final String text) {
		final By locator = By.xpath("//*[contains(normalize-space(), " + quoteForXPath(text) + ")]");
		for (final WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private WebElement findSectionByHeading(final String headingText) {
		final WebElement heading = waitForVisible(
				By.xpath("//*[normalize-space()=" + quoteForXPath(headingText) + "]"), Duration.ofSeconds(30));
		return heading.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
	}

	private boolean hasClickableTextInSection(final WebElement section, final String text) {
		final By locator = By.xpath(".//button[normalize-space()=" + quoteForXPath(text) + "] | .//a[normalize-space()="
				+ quoteForXPath(text) + "] | .//*[@role='button' and normalize-space()=" + quoteForXPath(text) + "]");
		for (final WebElement element : section.findElements(locator)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean isLikelyUserNameVisible(final String sectionText, final String email) {
		final String configuredUserName = env("SALEADS_USER_NAME", "").trim();
		if (!configuredUserName.isEmpty()) {
			return sectionText.contains(configuredUserName);
		}

		final String[] lines = sectionText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.contains("@") || line.contains(email) || line.contains("Informaci\u00F3n General")
					|| line.contains("BUSINESS PLAN") || line.contains("Cambiar Plan")) {
				continue;
			}
			if (line.matches(".*[A-Za-z].*") && line.length() > 2) {
				return true;
			}
		}
		return false;
	}

	private boolean isSidebarVisible() {
		final By sidebarLocator = By.xpath(
				"//aside | //nav[.//*[contains(normalize-space(), \"Negocio\") or contains(normalize-space(), \"Mi Negocio\")]]");
		for (final WebElement element : driver.findElements(sidebarLocator)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean isLegalContentVisible() {
		try {
			final String bodyText = normalize(driver.findElement(By.tagName("body")).getText());
			return bodyText.length() > 120;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		try {
			wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
			Thread.sleep(500L);
		} catch (final Exception ignored) {
			// Continue even if readyState probing is not available.
		}
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final Path screenshotPath = evidenceDir.resolve(fileName);
		final Path sourcePath = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(sourcePath, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureFullPageScreenshot(final String fileName) throws IOException, InterruptedException {
		final Dimension originalSize = driver.manage().window().getSize();
		final Number contentHeight = (Number) ((JavascriptExecutor) driver)
				.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
		final int targetHeight = Math.min(Math.max(originalSize.getHeight(), contentHeight.intValue() + 120), 9000);
		driver.manage().window().setSize(new Dimension(Math.max(originalSize.getWidth(), 1920), targetHeight));
		Thread.sleep(500L);
		captureScreenshot(fileName);
		driver.manage().window().setSize(originalSize);
	}

	private void safeCaptureScreenshot(final String fileName) {
		try {
			captureScreenshot(fileName);
		} catch (final Exception ignored) {
			// Keep original failure details even if screenshot capture fails.
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("Test: ").append(TEST_NAME).append('\n');
		report.append("Generated: ").append(Instant.now()).append('\n');
		report.append("Evidence Directory: ").append(evidenceDir.toAbsolutePath()).append("\n\n");

		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.get(field);
			report.append(field).append(": ").append(result.passed ? "PASS" : "FAIL").append('\n');
			report.append("  Details: ").append(result.details).append('\n');
		}

		report.append('\n');
		report.append("Captured URLs:\n");
		for (final Map.Entry<String, String> urlEntry : capturedUrls.entrySet()) {
			report.append("- ").append(urlEntry.getKey()).append(": ").append(urlEntry.getValue()).append('\n');
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, report.toString());
		System.out.println(report);
	}

	private static String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value;
	}

	private static String normalize(final String value) {
		return value == null ? "" : value;
	}

	private static String toSlug(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
	}

	private static String quoteForXPath(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder concat = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part = chars[i] == '\'' ? "\"'\"" : "'" + chars[i] + "'";
			concat.append(part);
			if (i < chars.length - 1) {
				concat.append(", ");
			}
		}
		concat.append(')');
		return concat.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
