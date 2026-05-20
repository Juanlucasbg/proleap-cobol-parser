package io.proleap.saleads.e2e;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaleadsMiNegocioFullTest {

	private static final Logger LOG = LoggerFactory.getLogger(SaleadsMiNegocioFullTest.class);

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final DateTimeFormatter DIRECTORY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss",
			Locale.US);

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private Path artifactsDirectory;
	private Path finalReportPath;
	private WebDriver driver;
	private WebDriverWait wait;
	private String appWindowHandle;
	private String termsFinalUrl = "N/A";
	private String privacyFinalUrl = "N/A";

	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();

	@Before
	public void setUp() throws Exception {
		initializeStepReport();
		artifactsDirectory = Files.createDirectories(Path.of("target", "saleads-mi-negocio-artifacts",
				LocalDateTime.now().format(DIRECTORY_TIMESTAMP)));
		driver = createDriver();
		wait = new WebDriverWait(driver, getTimeout());
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);
		openEnvironmentLoginPage();
	}

	@After
	public void tearDown() throws IOException {
		if (driver != null) {
			try {
				driver.quit();
			} catch (final RuntimeException e) {
				LOG.warn("Ignoring browser shutdown error", e);
			}
		}

		if (finalReportPath == null) {
			finalReportPath = writeFinalReport();
		}
		LOG.info("SaleADS final report written to {}", finalReportPath.toAbsolutePath());
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaDePrivacidad);

		finalReportPath = writeFinalReport();

		final List<String> failedFields = stepResults.entrySet().stream().filter(entry -> !entry.getValue())
				.map(Map.Entry::getKey).collect(Collectors.toList());
		if (!failedFields.isEmpty()) {
			fail("Workflow validation failed for: " + failedFields + ". See report: " + finalReportPath.toAbsolutePath());
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		final WebElement loginButton = waitForAnyVisible(By.xpath(
				"//button[contains(normalize-space(.), 'Google')] | //a[contains(normalize-space(.), 'Google')]"));
		clickAndWait(loginButton);
		trySelectGoogleAccount(ACCOUNT_EMAIL);
		waitForMainApplicationInterface();
		captureScreenshot("01-dashboard-loaded");
		appWindowHandle = driver.getWindowHandle();
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		expandMiNegocioMenu();
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final WebElement businessNameInput = waitForAnyVisible(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		captureScreenshot("03-agregar-negocio-modal");

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]")));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		expandMiNegocioMenu();
		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();
		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		captureScreenshot("04-administrar-negocios-page");
		appWindowHandle = driver.getWindowHandle();
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("Información General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
		assertEmailVisible();
		assertUserNameVisible();
	}

	private void stepValidateDetallesDeLaCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		final WebElement section = findSectionByHeading("Tus Negocios");
		final List<WebElement> businessElements = section.findElements(By.xpath(
				".//*[self::li or self::tr or self::article or self::div][string-length(normalize-space(.)) > 3]"));
		final boolean hasVisibleBusinessContent = businessElements.stream().anyMatch(WebElement::isDisplayed);
		if (!hasVisibleBusinessContent) {
			throw new AssertionError("Business list is not visible in 'Tus Negocios' section.");
		}
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		termsFinalUrl = validateLegalPage("Términos y Condiciones", "Términos y Condiciones", "08-terminos-page");
	}

	private void stepValidatePoliticaDePrivacidad() throws Exception {
		privacyFinalUrl = validateLegalPage("Política de Privacidad", "Política de Privacidad", "09-privacidad-page");
	}

	private String validateLegalPage(final String linkText, final String headingText, final String screenshotName)
			throws Exception {
		if (appWindowHandle == null) {
			appWindowHandle = driver.getWindowHandle();
		}

		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String currentUrl = driver.getCurrentUrl();
		clickByVisibleText(linkText);

		wait.until(driverRef -> {
			final boolean openedNewTab = driverRef.getWindowHandles().size() > handlesBeforeClick.size();
			final boolean sameTabNavigated = !driverRef.getCurrentUrl().equals(currentUrl);
			return openedNewTab || sameTabNavigated || hasVisibleText(headingText);
		});

		final Set<String> handlesAfterClick = new LinkedHashSet<>(driver.getWindowHandles());
		boolean openedNewTab = handlesAfterClick.size() > handlesBeforeClick.size();

		if (openedNewTab) {
			handlesAfterClick.removeAll(handlesBeforeClick);
			final String legalPageHandle = handlesAfterClick.iterator().next();
			driver.switchTo().window(legalPageHandle);
			waitForUiToLoad();
		}

		assertVisibleText(headingText);
		wait.until(driverRef -> !driverRef
				.findElements(By.xpath("//p[string-length(normalize-space(.)) > 50] | //div[string-length(normalize-space(.)) > 120]"))
				.isEmpty());
		captureScreenshot(screenshotName);
		final String legalPageUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return legalPageUrl;
	}

	private void expandMiNegocioMenu() throws Exception {
		if (hasVisibleText("Agregar Negocio") && hasVisibleText("Administrar Negocios")) {
			return;
		}

		if (hasVisibleText("Mi Negocio")) {
			clickByVisibleText("Mi Negocio");
		} else if (hasVisibleText("Negocio")) {
			clickByVisibleText("Negocio");
			clickByVisibleText("Mi Negocio");
		} else {
			throw new AssertionError("Neither 'Negocio' nor 'Mi Negocio' menu items are visible in sidebar.");
		}

		wait.until(driverRef -> hasVisibleText("Agregar Negocio") && hasVisibleText("Administrar Negocios"));
	}

	private void waitForMainApplicationInterface() {
		wait.until(driverRef -> hasVisibleText("Negocio") || hasVisibleText("Mi Negocio") || !driverRef
				.findElements(By.xpath("//aside | //nav")).isEmpty());
		if (!hasSidebarVisible()) {
			throw new AssertionError("Main interface did not load or sidebar navigation is not visible.");
		}
	}

	private boolean hasSidebarVisible() {
		final List<WebElement> sidebarCandidates = driver.findElements(By.xpath("//aside | //nav"));
		return sidebarCandidates.stream().anyMatch(WebElement::isDisplayed);
	}

	private void trySelectGoogleAccount(final String accountEmail) {
		final long timeoutMs = Duration.ofSeconds(25).toMillis();
		final long startedAt = System.currentTimeMillis();

		while (System.currentTimeMillis() - startedAt < timeoutMs) {
			if (hasSidebarVisible() || hasVisibleText("Mi Negocio") || hasVisibleText("Negocio")) {
				return;
			}

			if (clickVisibleTextAcrossWindows(accountEmail)) {
				waitForUiToLoad();
				return;
			}

			sleepQuietly(Duration.ofMillis(600));
		}
	}

	private boolean clickVisibleTextAcrossWindows(final String text) {
		final String originalHandle = driver.getWindowHandle();
		for (final String handle : driver.getWindowHandles()) {
			try {
				driver.switchTo().window(handle);
				final List<WebElement> candidates = driver.findElements(By.xpath(
						"//*[contains(normalize-space(.), \"" + escapeXpathText(text) + "\")]"));
				for (final WebElement candidate : candidates) {
					if (candidate.isDisplayed()) {
						candidate.click();
						driver.switchTo().window(originalHandle);
						return true;
					}
				}
			} catch (final RuntimeException ignored) {
				// Ignore transient window/DOM updates while polling the account selector.
			}
		}

		driver.switchTo().window(originalHandle);
		return false;
	}

	private void assertEmailVisible() {
		final List<WebElement> emailElements = driver
				.findElements(By.xpath("//*[contains(normalize-space(.), \"" + escapeXpathText(ACCOUNT_EMAIL) + "\")]"));
		final boolean hasVisibleEmail = emailElements.stream().anyMatch(WebElement::isDisplayed);
		if (!hasVisibleEmail) {
			throw new AssertionError("Expected user email not visible: " + ACCOUNT_EMAIL);
		}
	}

	private void assertUserNameVisible() {
		final WebElement informationSection = findSectionByHeading("Información General");
		final List<WebElement> textNodes = informationSection.findElements(By.xpath(
				".//*[self::h1 or self::h2 or self::h3 or self::h4 or self::p or self::span or self::div][string-length(normalize-space(.)) > 2]"));

		final boolean hasVisibleName = textNodes.stream().filter(WebElement::isDisplayed).map(WebElement::getText)
				.map(String::trim).filter(value -> !value.isEmpty())
				.anyMatch(value -> !value.contains("@") && !value.contains("Información General")
						&& !value.contains("BUSINESS PLAN") && !value.contains("Cambiar Plan"));

		if (!hasVisibleName) {
			throw new AssertionError("No user name-like text found in 'Información General' section.");
		}
	}

	private WebElement findSectionByHeading(final String heading) {
		final String headingXPath = "//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::p][contains(normalize-space(.), \""
				+ escapeXpathText(heading) + "\")]";
		final WebElement headingElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(headingXPath)));
		final WebElement section = headingElement.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		if (!section.isDisplayed()) {
			throw new AssertionError("Section containing heading '" + heading + "' is not visible.");
		}
		return section;
	}

	private void assertVisibleText(final String text) {
		wait.until(driverRef -> hasVisibleText(text));
	}

	private boolean hasVisibleText(final String text) {
		final String escapedText = escapeXpathText(text);
		final List<WebElement> candidates = driver
				.findElements(By.xpath("//*[contains(normalize-space(.), \"" + escapedText + "\")]"));
		return candidates.stream().anyMatch(WebElement::isDisplayed);
	}

	private void clickByVisibleText(final String text) throws Exception {
		final String escapedText = escapeXpathText(text);
		final WebElement element = waitForAnyVisible(
				By.xpath("//button[contains(normalize-space(.), \"" + escapedText + "\")]"),
				By.xpath("//a[contains(normalize-space(.), \"" + escapedText + "\")]"),
				By.xpath("//*[self::span or self::div or self::li][contains(normalize-space(.), \"" + escapedText
						+ "\")]"));
		clickAndWait(element);
	}

	private WebElement waitForAnyVisible(final By... locators) {
		return wait.until(driverRef -> {
			for (final By locator : locators) {
				final List<WebElement> elements = driverRef.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(driverRef -> {
			try {
				return "complete".equals(((JavascriptExecutor) driverRef).executeScript("return document.readyState"));
			} catch (final RuntimeException e) {
				return false;
			}
		});
		sleepQuietly(Duration.ofMillis(700));
	}

	private Path captureScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String filename = name + ".png";
		final Path target = artifactsDirectory.resolve(filename);
		Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		return target;
	}

	private void runStep(final String stepField, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepField, Boolean.TRUE);
			stepDetails.put(stepField, "PASS");
		} catch (final Throwable throwable) {
			stepResults.put(stepField, Boolean.FALSE);
			stepDetails.put(stepField, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
			try {
				captureScreenshot(stepField.replace(" ", "-").replace("ó", "o").replace("í", "i").toLowerCase(Locale.US)
						+ "-failure");
			} catch (final IOException screenshotError) {
				LOG.warn("Could not capture failure screenshot for {}", stepField, screenshotError);
			}
			LOG.error("Step '{}' failed", stepField, throwable);
		}
	}

	private void initializeStepReport() {
		for (final String reportField : REPORT_FIELDS) {
			stepResults.put(reportField, Boolean.FALSE);
			stepDetails.put(reportField, "Not executed");
		}
	}

	private Path writeFinalReport() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("# saleads_mi_negocio_full_test").append(System.lineSeparator()).append(System.lineSeparator());
		builder.append("- Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		builder.append("- Artifacts directory: ").append(artifactsDirectory.toAbsolutePath()).append(System.lineSeparator());
		builder.append("- Terminos y Condiciones URL: ").append(termsFinalUrl).append(System.lineSeparator());
		builder.append("- Politica de Privacidad URL: ").append(privacyFinalUrl).append(System.lineSeparator())
				.append(System.lineSeparator());
		builder.append("| Step | Result | Details |").append(System.lineSeparator());
		builder.append("| --- | --- | --- |").append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			final String result = stepResults.get(field) ? "PASS" : "FAIL";
			builder.append("| ").append(field).append(" | ").append(result).append(" | ")
					.append(stepDetails.get(field).replace("|", "/")).append(" |").append(System.lineSeparator());
		}

		final Path reportPath = artifactsDirectory.resolve("final-report.md");
		Files.writeString(reportPath, builder.toString());
		return reportPath;
	}

	private WebDriver createDriver() throws MalformedURLException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-gpu");

		if (Boolean.parseBoolean(readConfig("saleads.headless", "false"))) {
			options.addArguments("--headless=new");
		}

		final String remoteWebDriverUrl = readConfig("saleads.remoteWebDriverUrl", null);
		if (remoteWebDriverUrl != null && !remoteWebDriverUrl.isBlank()) {
			return new RemoteWebDriver(URI.create(remoteWebDriverUrl).toURL(), options);
		}
		return new ChromeDriver(options);
	}

	private void openEnvironmentLoginPage() {
		final String loginUrl = readConfig("saleads.loginUrl", null);
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiToLoad();
			return;
		}

		final String currentUrl = driver.getCurrentUrl();
		if (currentUrl == null || currentUrl.isBlank() || currentUrl.startsWith("about:blank")
				|| currentUrl.startsWith("data:")) {
			throw new IllegalStateException(
					"No active login page detected. Provide -Dsaleads.loginUrl (or SALEADS_LOGIN_URL) for the target environment.");
		}
	}

	private Duration getTimeout() {
		final String timeoutConfig = readConfig("saleads.timeoutSeconds", String.valueOf(DEFAULT_TIMEOUT.toSeconds()));
		return Duration.ofSeconds(Long.parseLong(timeoutConfig));
	}

	private String readConfig(final String key, final String defaultValue) {
		final String systemValue = System.getProperty(key);
		if (systemValue != null && !systemValue.isBlank()) {
			return systemValue;
		}

		final String envKey = key.toUpperCase(Locale.US).replace('.', '_');
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		return defaultValue;
	}

	private void sleepQuietly(final Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String escapeXpathText(final String text) {
		return text.replace("\"", "\\\"");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
