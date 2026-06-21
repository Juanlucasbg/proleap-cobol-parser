package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String ENV_START_URL = "SALEADS_START_URL";
	private static final String ENV_BROWSER = "SALEADS_BROWSER";
	private static final String ENV_HEADLESS = "SALEADS_HEADLESS";
	private static final String ENV_WAIT_SECONDS = "SALEADS_WAIT_SECONDS";
	private static final String ENV_EVIDENCE_DIR = "SALEADS_EVIDENCE_DIR";

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(25);

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private String termsUrl;
	private String privacyUrl;

	@Before
	public void setUp() throws IOException {
		final String startUrl = getRequiredEnv(ENV_START_URL);
		Assume.assumeTrue("SALEADS_START_URL must point to the current environment login page.", startUrl != null);

		final Duration timeout = Duration.ofSeconds(parseLongOrDefault(System.getenv(ENV_WAIT_SECONDS), DEFAULT_WAIT.getSeconds()));
		wait = new WebDriverWait(createDriver(), timeout);
		driver.manage().window().maximize();
		driver.get(startUrl);
		waitForUiLoad();

		final String defaultEvidenceDir = "target/saleads-evidence/" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
				.format(LocalDateTime.now());
		evidenceDirectory = Path.of(readEnvOrDefault(ENV_EVIDENCE_DIR, defaultEvidenceDir));
		Files.createDirectories(evidenceDirectory);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final Map<String, StepResult> results = new LinkedHashMap<>();

		runStep(results, "Login", this::loginWithGoogleAndValidateDashboard);
		runStep(results, "Mi Negocio menu", this::openMiNegocioMenu);
		runStep(results, "Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep(results, "Administrar Negocios view", this::openAdministrarNegocios);
		runStep(results, "Informacion General", this::validateInformacionGeneral);
		runStep(results, "Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		runStep(results, "Tus Negocios", this::validateTusNegocios);
		runStep(results, "Terminos y Condiciones", this::validateTerminosYCondiciones);
		runStep(results, "Politica de Privacidad", this::validatePoliticaDePrivacidad);

		printSummary(results);
		final List<String> failedSteps = results.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(Map.Entry::getKey).collect(Collectors.toList());
		assertTrue("Failed workflow checks: " + failedSteps, failedSteps.isEmpty());
	}

	private void loginWithGoogleAndValidateDashboard() {
		clickFirstVisibleText("Sign in with Google", "Iniciar sesion con Google", "Continuar con Google", "Google");
		waitForUiLoad();

		clickVisibleTextIfPresent(Duration.ofSeconds(8), GOOGLE_ACCOUNT_EMAIL);
		waitForUiLoad();

		assertAnyTextVisible("Negocio", "Mi Negocio", "Dashboard", "Panel");
		assertSidebarVisible();
		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() {
		if (!isTextVisible("Mi Negocio")) {
			clickVisibleTextIfPresent(Duration.ofSeconds(10), "Negocio");
			waitForUiLoad();
		}

		clickVisibleTextIfPresent(Duration.ofSeconds(10), "Mi Negocio");
		waitForUiLoad();

		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() {
		clickFirstVisibleText("Agregar Negocio");
		waitForUiLoad();

		assertAnyTextVisible("Crear Nuevo Negocio");
		assertAnyTextVisible("Nombre del Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertAnyTextVisible("Cancelar");
		assertAnyTextVisible("Crear Negocio");
		assertNombreNegocioInputPresent();
		takeScreenshot("03-agregar-negocio-modal");

		typeInNombreDelNegocioIfPresent("Negocio Prueba Automatizacion");
		clickVisibleTextIfPresent(Duration.ofSeconds(5), "Cancelar");
		waitForUiLoad();
	}

	private void openAdministrarNegocios() {
		if (!isTextVisible("Administrar Negocios")) {
			clickVisibleTextIfPresent(Duration.ofSeconds(10), "Mi Negocio");
			waitForUiLoad();
		}

		clickFirstVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertAnyTextVisible("Informacion General", "Información General");
		assertAnyTextVisible("Detalles de la Cuenta");
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Seccion Legal", "Sección Legal");
		takeScreenshot("04-administrar-negocios-view");
	}

	private void validateInformacionGeneral() {
		assertAnyTextVisible("Informacion General", "Información General");
		assertAnyTextVisible("BUSINESS PLAN");
		assertAnyTextVisible("Cambiar Plan");

		final String bodyText = getVisiblePageText();
		assertTrue("Expected visible user email in Informacion General section.", EMAIL_PATTERN.matcher(bodyText).find());
		assertTrue("Expected visible user name in Informacion General section.", hasLikelyUserName(bodyText));
	}

	private void validateDetallesDeLaCuenta() {
		assertAnyTextVisible("Detalles de la Cuenta");
		assertAnyTextVisible("Cuenta creada");
		assertAnyTextVisible("Estado activo");
		assertAnyTextVisible("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertBusinessListVisible();
	}

	private void validateTerminosYCondiciones() {
		termsUrl = validateLegalDocument("Términos y Condiciones", "Términos y Condiciones", "05-terminos-y-condiciones");
		assertTrue("Expected Terms and Conditions URL.", termsUrl != null && !termsUrl.isBlank());
	}

	private void validatePoliticaDePrivacidad() {
		privacyUrl = validateLegalDocument("Política de Privacidad", "Política de Privacidad", "06-politica-de-privacidad");
		assertTrue("Expected Privacy Policy URL.", privacyUrl != null && !privacyUrl.isBlank());
	}

	private String validateLegalDocument(final String linkText, final String headingText, final String screenshotName) {
		final String currentWindow = driver.getWindowHandle();
		final Set<String> initialWindows = driver.getWindowHandles();

		clickFirstVisibleText(linkText);
		waitForUiLoad();

		boolean openedNewTab = false;
		try {
			wait.until(ignored -> driver.getWindowHandles().size() > initialWindows.size() || isTextVisible(headingText));
		} catch (final TimeoutException ignored) {
			// Some environments keep navigation in the same tab.
		}

		final Set<String> currentWindows = driver.getWindowHandles();
		if (currentWindows.size() > initialWindows.size()) {
			openedNewTab = true;
			for (final String handle : currentWindows) {
				if (!initialWindows.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		assertAnyTextVisible(headingText);
		assertLegalContentVisible();
		takeScreenshot(screenshotName);

		final String currentUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(currentWindow);
		} else {
			driver.navigate().back();
		}
		waitForUiLoad();

		return currentUrl;
	}

	private void runStep(final Map<String, StepResult> results, final String stepName, final ThrowingRunnable stepAction) {
		try {
			stepAction.run();
			results.put(stepName, StepResult.passed("PASS"));
		} catch (final Throwable throwable) {
			takeScreenshot("failed-" + sanitizeForFileName(stepName));
			results.put(stepName, StepResult.failed(throwable.getMessage()));
		}
	}

	private WebDriver createDriver() {
		final String browser = readEnvOrDefault(ENV_BROWSER, "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(readEnvOrDefault(ENV_HEADLESS, "true"));

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("--headless");
			}
			driver = new FirefoxDriver(firefoxOptions);
			break;
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			chromeOptions.addArguments("--no-sandbox");
			chromeOptions.addArguments("--disable-dev-shm-usage");
			chromeOptions.addArguments("--window-size=1920,1080");
			driver = new ChromeDriver(chromeOptions);
			break;
		}

		return driver;
	}

	private void waitForUiLoad() {
		wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		sleep(800);
	}

	private void clickFirstVisibleText(final String... texts) {
		for (final String text : texts) {
			if (clickVisibleTextIfPresent(Duration.ofSeconds(5), text)) {
				waitForUiLoad();
				return;
			}
		}
		throw new IllegalStateException("Could not click any visible element with text: " + Arrays.toString(texts));
	}

	private boolean clickVisibleTextIfPresent(final Duration timeout, final String text) {
		final long timeoutAt = System.currentTimeMillis() + timeout.toMillis();

		while (System.currentTimeMillis() < timeoutAt) {
			final List<WebElement> candidates = driver.findElements(By.xpath("//*[normalize-space()="
					+ toXPathLiteral(text) + " or contains(normalize-space(), " + toXPathLiteral(text) + ")]"));
			for (final WebElement element : candidates) {
				if (!element.isDisplayed()) {
					continue;
				}

				try {
					element.click();
					return true;
				} catch (final Exception clickException) {
					try {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
						return true;
					} catch (final Exception ignored) {
						// try next candidate
					}
				}
			}
			sleep(250);
		}

		return false;
	}

	private void assertAnyTextVisible(final String... texts) {
		final boolean found = Arrays.stream(texts).anyMatch(this::isTextVisible);
		assertTrue("Expected one of visible texts: " + Arrays.toString(texts), found);
	}

	private boolean isTextVisible(final String text) {
		final List<WebElement> elements = driver.findElements(By.xpath(
				"//*[normalize-space()=" + toXPathLiteral(text) + " or contains(normalize-space(), " + toXPathLiteral(text) + ")]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void assertSidebarVisible() {
		final List<WebElement> sidebarElements = new ArrayList<>();
		sidebarElements.addAll(driver.findElements(By.cssSelector("aside")));
		sidebarElements.addAll(driver.findElements(By.cssSelector("nav")));
		final boolean hasSidebar = sidebarElements.stream().anyMatch(WebElement::isDisplayed);
		assertTrue("Expected left sidebar navigation.", hasSidebar);
	}

	private void assertNombreNegocioInputPresent() {
		final List<WebElement> inputs = driver.findElements(By.xpath(
				"//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1] | "
						+ "//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"));
		final boolean present = inputs.stream().anyMatch(WebElement::isDisplayed);
		assertTrue("Expected 'Nombre del Negocio' input field.", present);
	}

	private void typeInNombreDelNegocioIfPresent(final String value) {
		final List<WebElement> inputs = driver.findElements(By.xpath(
				"//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1] | "
						+ "//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"));
		for (final WebElement input : inputs) {
			if (input.isDisplayed()) {
				input.clear();
				input.sendKeys(value);
				return;
			}
		}
	}

	private void assertBusinessListVisible() {
		final List<WebElement> businessCards = driver.findElements(By.xpath(
				"//*[contains(normalize-space(), 'Tus Negocios')]/following::*[self::li or self::article or self::div]"));
		final boolean hasVisibleBusinessList = businessCards.stream().anyMatch(WebElement::isDisplayed);
		assertTrue("Expected visible business list in 'Tus Negocios' section.", hasVisibleBusinessList);
	}

	private void assertLegalContentVisible() {
		final String bodyText = getVisiblePageText();
		assertTrue("Expected legal content text on the page.", bodyText != null && bodyText.trim().length() > 200);
	}

	private String getVisiblePageText() {
		final WebElement body = driver.findElement(By.tagName("body"));
		return body.getText();
	}

	private boolean hasLikelyUserName(final String bodyText) {
		return Arrays.stream(bodyText.split("\\R")).map(String::trim).filter(line -> !line.isBlank())
				.filter(line -> !line.contains("@")).filter(line -> !line.equalsIgnoreCase("Informacion General"))
				.filter(line -> !line.equalsIgnoreCase("Información General")).filter(line -> !line.equalsIgnoreCase("BUSINESS PLAN"))
				.filter(line -> !line.equalsIgnoreCase("Cambiar Plan")).anyMatch(line -> line.length() >= 4 && line.length() <= 60);
	}

	private void takeScreenshot(final String name) {
		if (driver == null) {
			return;
		}

		try {
			final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path destination = evidenceDirectory.resolve(sanitizeForFileName(name) + ".png");
			Files.copy(screenshot.toPath(), destination);
		} catch (final Exception ignored) {
			// If screenshot capture fails, preserve test flow.
		}
	}

	private String getRequiredEnv(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String readEnvOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value.trim();
	}

	private long parseLongOrDefault(final String value, final long defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (final NumberFormatException ex) {
			return defaultValue;
		}
	}

	private String sanitizeForFileName(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-").replaceAll("(^-|-$)", "");
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder xpathBuilder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String literal = chars[i] == '\'' ? "\"'\"" : "'" + chars[i] + "'";
			if (i > 0) {
				xpathBuilder.append(", ");
			}
			xpathBuilder.append(literal);
		}
		xpathBuilder.append(")");
		return xpathBuilder.toString();
	}

	private void printSummary(final Map<String, StepResult> results) {
		System.out.println("=== SaleADS Mi Negocio workflow summary ===");
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue().passed ? "PASS" : "FAIL"));
			if (!entry.getValue().passed && entry.getValue().details != null) {
				System.out.println("  reason: " + entry.getValue().details);
			}
		}
		if (termsUrl != null) {
			System.out.println("Terminos y Condiciones URL: " + termsUrl);
		}
		if (privacyUrl != null) {
			System.out.println("Politica de Privacidad URL: " + privacyUrl);
		}
		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult passed(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult failed(final String details) {
			return new StepResult(false, details);
		}
	}
}
