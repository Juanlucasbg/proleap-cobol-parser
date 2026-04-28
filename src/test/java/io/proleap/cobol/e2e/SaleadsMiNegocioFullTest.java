package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
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

public class SaleadsMiNegocioFullTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TERMS_TEXT = "T\u00e9rminos y Condiciones";
	private static final String PRIVACY_TEXT = "Pol\u00edtica de Privacidad";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final DateTimeFormatter EVIDENCE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> failures = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private String appWindowHandle;
	private String termsUrl = "";
	private String privacyUrl = "";

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue("Set RUN_SALEADS_E2E=true to execute this workflow.",
				Boolean.parseBoolean(env("RUN_SALEADS_E2E", "false")));

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDirectory = Path.of("target", "saleads-evidence", LocalDateTime.now().format(EVIDENCE_TS));
		Files.createDirectories(evidenceDirectory);
		openEntryPageIfConfigured();
		appWindowHandle = driver.getWindowHandle();
		waitForUiToLoad();
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegociosView);
		runStep("Informaci\u00f3n General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("T\u00e9rminos y Condiciones", this::stepValidateTerminosCondiciones);
		runStep("Pol\u00edtica de Privacidad", this::stepValidatePoliticaPrivacidad);

		printFinalReport();
		assertEverythingPassed();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		if (isSidebarVisible(Duration.ofSeconds(6))) {
			captureScreenshot("01-dashboard-loaded");
			return;
		}

		clickByVisibleText("Sign in with Google", "Iniciar con Google", "Continuar con Google", "Google");

		if (isVisible(By.xpath("//*[normalize-space(.)=" + xpathLiteral(ACCOUNT_EMAIL) + "]"), Duration.ofSeconds(10))) {
			clickAndWait(By.xpath("//*[normalize-space(.)=" + xpathLiteral(ACCOUNT_EMAIL) + "]"));
		}

		assertSidebarVisible();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		assertVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");
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
		assertVisible(businessNameInputLocator(), "Input 'Nombre del Negocio' is not present.");
		captureScreenshot("03-agregar-negocio-modal");

		WebElement businessNameInput = wait.until(ExpectedConditions.visibilityOfElementLocated(businessNameInputLocator()));
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegociosView() throws Exception {
		if (!isVisibleText("Administrar Negocios", Duration.ofSeconds(5))) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		assertVisibleText("Informaci\u00f3n General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Secci\u00f3n Legal");
		captureScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertVisible(By.xpath(
				"(//*[contains(normalize-space(.), '@')]/ancestor::*[self::section or self::article or self::div][1]"
						+ "//*[self::h1 or self::h2 or self::h3 or self::p or self::span]"
						+ "[string-length(normalize-space(.)) > 2"
						+ " and not(contains(normalize-space(.), '@'))"
						+ " and not(contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'business plan'))]"
						+ ")[1]"),
				"User name is not visible.");
		assertVisible(By.xpath("//*[contains(normalize-space(.), '@')]"), "User email is not visible.");
		assertVisible(By.xpath("//h1|//h2|//h3|//p|//span[contains(normalize-space(.), 'BUSINESS PLAN')]"),
				"'BUSINESS PLAN' is not visible.");
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
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisible(
				By.xpath("(//ul[.//*[contains(normalize-space(.), 'Negocio')]] | //table | //div[.//*[contains(normalize-space(.), 'Negocio')]])[1]"),
				"Business list is not visible.");
	}

	private void stepValidateTerminosCondiciones() throws Exception {
		termsUrl = validateLegalPage(TERMS_TEXT, TERMS_TEXT, "05-terminos-y-condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		privacyUrl = validateLegalPage(PRIVACY_TEXT, PRIVACY_TEXT, "06-politica-de-privacidad");
	}

	private String validateLegalPage(final String linkText, final String headingText, final String screenshotPrefix)
			throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String urlBeforeClick = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		final String newHandle = waitForNewWindowHandle(handlesBeforeClick, Duration.ofSeconds(8));
		final boolean openedNewTab = newHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(newHandle);
			waitForUiToLoad();
		}

		if (!openedNewTab && urlBeforeClick.equals(driver.getCurrentUrl())) {
			wait.until(ExpectedConditions.or(
					ExpectedConditions.visibilityOfElementLocated(textLocator(headingText)),
					ExpectedConditions.not(ExpectedConditions.urlToBe(urlBeforeClick))));
		}

		assertVisibleText(headingText);
		assertVisible(By.xpath("//p[string-length(normalize-space(.)) > 40] | //article//*[string-length(normalize-space(.)) > 40]"),
				"Legal content text is not visible.");
		captureScreenshot(screenshotPrefix);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		if (!driver.getWindowHandle().equals(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void runStep(final String reportKey, final StepAction action) {
		try {
			action.run();
			report.put(reportKey, true);
		} catch (Throwable t) {
			report.put(reportKey, false);
			failures.put(reportKey, t.getMessage());
			try {
				captureScreenshot("failed-" + sanitize(reportKey));
			} catch (Exception ignored) {
				// Ignore evidence failures after step failures.
			}
		}
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		System.out.println("T\u00e9rminos y Condiciones URL: " + (termsUrl.isBlank() ? "N/A" : termsUrl));
		System.out.println("Pol\u00edtica de Privacidad URL: " + (privacyUrl.isBlank() ? "N/A" : privacyUrl));
		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
	}

	private void assertEverythingPassed() {
		final List<String> failedSteps = report.entrySet().stream()
				.filter(entry -> !entry.getValue())
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

		if (!failedSteps.isEmpty()) {
			final String detail = failedSteps.stream()
					.map(step -> step + ": " + failures.getOrDefault(step, "no detail"))
					.collect(Collectors.joining(" | "));
			Assert.fail("One or more validations failed -> " + detail);
		}
	}

	private void openEntryPageIfConfigured() {
		final String entryUrl = env("SALEADS_ENTRY_URL", "").trim();
		if (!entryUrl.isEmpty()) {
			driver.get(entryUrl);
			waitForUiToLoad();
			return;
		}

		if ("about:blank".equalsIgnoreCase(driver.getCurrentUrl())) {
			throw new IllegalStateException(
					"WebDriver is on about:blank. Provide SALEADS_ENTRY_URL for the active environment login page.");
		}
	}

	private WebDriver createDriver() throws MalformedURLException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		final String remoteWebDriverUrl = env("SELENIUM_REMOTE_URL", "").trim();
		if (!remoteWebDriverUrl.isEmpty()) {
			return new RemoteWebDriver(new URL(remoteWebDriverUrl), options);
		}

		return new ChromeDriver(options);
	}

	private void clickByVisibleText(final String... textCandidates) {
		for (String text : textCandidates) {
			final List<By> locators = Arrays.asList(
					clickableTextLocator(text, false),
					clickableTextLocator(text, true));
			for (By locator : locators) {
				try {
					clickAndWait(locator);
					return;
				} catch (TimeoutException | NoSuchElementException ignored) {
					// try next locator
				}
			}
		}

		throw new NoSuchElementException("Could not click element with visible text in candidates: "
				+ Arrays.toString(textCandidates));
	}

	private void clickAndWait(final By by) {
		WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
		element.click();
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		try {
			wait.until(driver -> {
				Object readyState = ((JavascriptExecutor) driver).executeScript("return document.readyState");
				return "complete".equals(String.valueOf(readyState));
			});
		} catch (Exception ignored) {
			// Continue even when a page does not expose readystatechange.
		}
	}

	private void assertSidebarVisible() {
		assertVisible(By.xpath("//aside | //nav"), "Sidebar navigation is not visible.");
		assertVisibleText("Negocio");
	}

	private boolean isSidebarVisible(final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside | //nav")));
			return true;
		} catch (TimeoutException ex) {
			return false;
		}
	}

	private void assertVisibleText(final String text) {
		assertVisible(textLocator(text), "Text '" + text + "' is not visible.");
	}

	private void assertVisible(final By locator, final String failureMessage) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (TimeoutException ex) {
			throw new AssertionError(failureMessage, ex);
		}
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (TimeoutException ex) {
			return false;
		}
	}

	private boolean isVisibleText(final String text, final Duration timeout) {
		return isVisible(textLocator(text), timeout);
	}

	private void captureScreenshot(final String filePrefix) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDirectory.resolve(sanitize(filePrefix) + ".png");
		Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private String waitForNewWindowHandle(final Set<String> handlesBeforeClick, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(d -> {
				Set<String> handlesAfter = d.getWindowHandles();
				if (handlesAfter.size() <= handlesBeforeClick.size()) {
					return null;
				}
				for (String handle : handlesAfter) {
					if (!handlesBeforeClick.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (TimeoutException ex) {
			return null;
		}
	}

	private By textLocator(final String text) {
		final String xpathText = xpathLiteral(text);
		return By.xpath("//*[normalize-space(.)=" + xpathText + " or contains(normalize-space(.), " + xpathText + ")]");
	}

	private By businessNameInputLocator() {
		return By.xpath("("
				+ "//label[contains(normalize-space(.), " + xpathLiteral("Nombre del Negocio") + ")]/following::input[1]"
				+ " | //input[contains(@placeholder, " + xpathLiteral("Nombre del Negocio") + ")]"
				+ " | //input[@name='businessName'])"
				+ "[1]");
	}

	private By clickableTextLocator(final String text, final boolean partialMatch) {
		final String expected = partialMatch
				? "contains(normalize-space(.), " + xpathLiteral(text) + ")"
				: "normalize-space(.)=" + xpathLiteral(text);

		return By.xpath("("
				+ "//button[" + expected + "]"
				+ " | //a[" + expected + "]"
				+ " | //*[@role='button' and " + expected + "]"
				+ " | //*[(" + expected + ") and (self::span or self::div)]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"
				+ ")[1]");
	}

	private static String env(final String key, final String defaultValue) {
		String value = System.getenv(key);
		return value == null ? defaultValue : value;
	}

	private static String sanitize(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
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

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
