package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * E2E UI workflow test for SaleADS "Mi Negocio" module.
 *
 * Required runtime inputs:
 * - saleads.baseUrl: login page URL in current environment.
 *
 * Optional runtime inputs:
 * - saleads.google.email (default: juanlucasbarbiergarzon@gmail.com)
 * - saleads.browser (chrome|firefox|edge, default: chrome)
 * - saleads.headless (true|false, default: false)
 * - saleads.screenshots.dir (default: target/saleads-screenshots)
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_EMAIL_DEFAULT = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final List<String> REQUIRED_REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu",
			"Agregar Negocio modal", "Administrar Negocios view", "Información General", "Detalles de la Cuenta",
			"Tus Negocios", "Términos y Condiciones", "Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDir;
	private final Map<String, String> stepResults = new LinkedHashMap<>();
	private final List<String> legalUrls = new ArrayList<>();

	@Before
	public void setUp() throws IOException {
		driver = buildDriver();
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
		driver.manage().window().maximize();
		screenshotsDir = prepareScreenshotsDirectory();
		for (final String field : REQUIRED_REPORT_FIELDS) {
			stepResults.put(field, "FAIL - NOT_EXECUTED");
		}
	}

	@After
	public void tearDown() {
		try {
			printFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String baseUrl = requiredProperty("saleads.baseUrl");
		driver.get(baseUrl);

		loginWithGoogleAndValidateAppLoaded();
		openMiNegocioMenu();
		validateAgregarNegocioModal();
		openAdministrarNegociosAndValidateSections();
		validateInformacionGeneralSection();
		validateDetallesCuentaSection();
		validateTusNegociosSection();
		validateLegalLink("Términos y Condiciones", "Términos y Condiciones");
		validateLegalLink("Política de Privacidad", "Política de Privacidad");
	}

	private void loginWithGoogleAndValidateAppLoaded() {
		final String stepName = "Login";
		try {
			waitForUiAfterClickIfNeeded();

			final String appWindow = driver.getWindowHandle();
			final int initialWindows = driver.getWindowHandles().size();
			clickFirstVisibleByTextAnyTag("Sign in with Google", "Ingresar con Google", "Iniciar sesión con Google",
					"Continuar con Google", "Login with Google");
			handlePossibleGoogleWindowSelection(appWindow, initialWindows);

			assertVisibleByTexts("main application interface",
					List.of("Negocio", "Mi Negocio", "Dashboard", "Inicio", "Panel"));
			assertVisibleByTexts("left sidebar navigation",
					List.of("Negocio", "Mi Negocio", "Administrar Negocios", "Agregar Negocio"));

			captureScreenshot("01-dashboard-loaded");
			stepResults.put(stepName, pass("Main interface and left sidebar visible"));
		} catch (Exception ex) {
			stepResults.put(stepName, failStatus(ex));
			throw ex;
		}
	}

	private void openMiNegocioMenu() {
		final String stepName = "Mi Negocio menu";
		try {
			waitForUiAfterClickIfNeeded();

			expandIfNeededByText("Negocio");
			clickFirstVisibleByTextAnyTag("Mi Negocio");
			waitForUiAfterClickIfNeeded();

			assertAnyVisible("submenu expands", List.of("Agregar Negocio", "Administrar Negocios"));
			assertAnyVisible("Agregar Negocio visible", List.of("Agregar Negocio"));
			assertAnyVisible("Administrar Negocios visible", List.of("Administrar Negocios"));

			captureScreenshot("02-mi-negocio-menu-expanded");
			stepResults.put(stepName, pass("Mi Negocio submenu expanded with expected options"));
		} catch (Exception ex) {
			stepResults.put(stepName, failStatus(ex));
			throw ex;
		}
	}

	private void validateAgregarNegocioModal() {
		final String stepName = "Agregar Negocio modal";
		try {
			clickFirstVisibleByTextAnyTag("Agregar Negocio");
			waitForUiAfterClickIfNeeded();

			assertAnyVisible("modal title", List.of("Crear Nuevo Negocio"));
			assertInputVisibleByLabelOrPlaceholder("Nombre del Negocio");
			assertAnyVisible("business limit text", List.of("Tienes 2 de 3 negocios"));
			assertAnyVisible("cancel button", List.of("Cancelar"));
			assertAnyVisible("create business button", List.of("Crear Negocio"));

			optionalModalInteraction();
			captureScreenshot("03-agregar-negocio-modal");
			stepResults.put(stepName, pass("Modal content validated successfully"));
		} catch (Exception ex) {
			stepResults.put(stepName, failStatus(ex));
			throw ex;
		}
	}

	private void openAdministrarNegociosAndValidateSections() {
		final String stepName = "Administrar Negocios view";
		try {
			expandIfNeededByText("Mi Negocio");
			clickFirstVisibleByTextAnyTag("Administrar Negocios");
			waitForUiAfterClickIfNeeded();

			assertAnyVisible("Información General section", List.of("Información General"));
			assertAnyVisible("Detalles de la Cuenta section", List.of("Detalles de la Cuenta"));
			assertAnyVisible("Tus Negocios section", List.of("Tus Negocios"));
			assertAnyVisible("Sección Legal section", List.of("Sección Legal"));

			captureFullPageScreenshot("04-administrar-negocios-page");
			stepResults.put(stepName, pass("Account page sections are visible"));
		} catch (Exception ex) {
			stepResults.put(stepName, failStatus(ex));
			throw ex;
		}
	}

	private void validateInformacionGeneralSection() {
		final String stepName = "Información General";
		try {
			assertAnyVisible("user name", List.of("Nombre", "Usuario", "@"));
			assertAnyVisible("user email", List.of("@"));
			assertAnyVisible("plan text", List.of("BUSINESS PLAN"));
			assertAnyVisible("Cambiar Plan button", List.of("Cambiar Plan"));

			stepResults.put(stepName, pass("Información General data visible"));
		} catch (Exception ex) {
			stepResults.put(stepName, failStatus(ex));
			throw ex;
		}
	}

	private void validateDetallesCuentaSection() {
		final String stepName = "Detalles de la Cuenta";
		try {
			assertAnyVisible("Cuenta creada", List.of("Cuenta creada"));
			assertAnyVisible("Estado activo", List.of("Estado activo"));
			assertAnyVisible("Idioma seleccionado", List.of("Idioma seleccionado"));

			stepResults.put(stepName, pass("Detalles de la Cuenta content visible"));
		} catch (Exception ex) {
			stepResults.put(stepName, failStatus(ex));
			throw ex;
		}
	}

	private void validateTusNegociosSection() {
		final String stepName = "Tus Negocios";
		try {
			assertAnyVisible("business list", List.of("Tus Negocios"));
			assertAnyVisible("Agregar Negocio button", List.of("Agregar Negocio"));
			assertAnyVisible("business capacity", List.of("Tienes 2 de 3 negocios"));

			stepResults.put(stepName, pass("Tus Negocios content visible"));
		} catch (Exception ex) {
			stepResults.put(stepName, failStatus(ex));
			throw ex;
		}
	}

	private void validateLegalLink(final String linkText, final String expectedHeading) {
		final String stepName = linkText;
		try {
			final String appWindow = driver.getWindowHandle();
			final Set<String> beforeHandles = driver.getWindowHandles();
			final String previousUrl = driver.getCurrentUrl();

			clickFirstVisibleByTextAnyTag(linkText);

			switchToLegalTargetWindow(appWindow, beforeHandles, previousUrl);
			final String finalUrl = driver.getCurrentUrl();
			legalUrls.add(stepName + ": " + finalUrl);

			assertAnyVisible("legal heading " + expectedHeading, List.of(expectedHeading));
			assertLegalContentVisible(expectedHeading);
			captureScreenshot("legal-" + sanitizeForFileName(linkText));

			if (!driver.getWindowHandle().equals(appWindow)) {
				driver.close();
				driver.switchTo().window(appWindow);
			} else if (!sameUrl(driver.getCurrentUrl(), previousUrl)) {
				driver.navigate().back();
			}

			waitForUiAfterClickIfNeeded();
			stepResults.put(stepName, pass("Heading/content visible. URL=" + finalUrl));
		} catch (Exception ex) {
			stepResults.put(stepName, failStatus(ex));
			throw ex;
		}
	}

	private void switchToLegalTargetWindow(final String appWindow, final Set<String> beforeHandles,
			final String previousUrl) {
		wait.until(driver -> {
			if (driver.getWindowHandles().size() > beforeHandles.size()) {
				return true;
			}
			final String current = driver.getCurrentUrl();
			return current != null && !sameUrl(current, previousUrl);
		});

		for (final String handle : driver.getWindowHandles()) {
			if (!beforeHandles.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiAfterClickIfNeeded();
				return;
			}
		}

		driver.switchTo().window(appWindow);
		waitForUiAfterClickIfNeeded();
	}

	private void handlePossibleGoogleWindowSelection(final String appWindow, final int initialWindows) {
		wait.until(driver -> driver.getWindowHandles().size() >= initialWindows);
		if (driver.getWindowHandles().size() > initialWindows) {
			for (final String handle : driver.getWindowHandles()) {
				if (!handle.equals(appWindow)) {
					driver.switchTo().window(handle);
					break;
				}
			}

			selectGoogleAccountIfPresent();

			wait.until(driver -> {
				try {
					driver.switchTo().window(appWindow);
					return true;
				} catch (Exception ignored) {
					return false;
				}
			});
		} else {
			selectGoogleAccountIfPresent();
		}
	}

	private void selectGoogleAccountIfPresent() {
		final String googleEmail = property("saleads.google.email", GOOGLE_EMAIL_DEFAULT);
		final List<By> accountLocators = List.of(
				By.xpath("//div[contains(normalize-space(.), '" + escapeForXpath(googleEmail) + "')]"),
				By.xpath("//span[contains(normalize-space(.), '" + escapeForXpath(googleEmail) + "')]"));

		for (final By locator : accountLocators) {
			try {
				final WebElement account = new WebDriverWait(driver, Duration.ofSeconds(7))
						.until(ExpectedConditions.visibilityOfElementLocated(locator));
				safeClick(account);
				waitForUiAfterClickIfNeeded();
				return;
			} catch (TimeoutException ignored) {
				// If no account chooser is present, user may already be authenticated.
			}
		}
	}

	private void optionalModalInteraction() {
		try {
			final WebElement input = findVisibleInputByLabelOrPlaceholder("Nombre del Negocio");
			input.click();
			input.sendKeys("Negocio Prueba Automatización");
			clickFirstVisibleByTextAnyTag("Cancelar");
			waitForUiAfterClickIfNeeded();
		} catch (Exception ignored) {
			// Optional interaction should not fail the test.
		}
	}

	private WebDriver buildDriver() {
		final String browser = property("saleads.browser", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(property("saleads.headless", "false"));

		switch (browser) {
		case "firefox":
			WebDriverManager.firefoxdriver().setup();
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("--headless");
			}
			return new FirefoxDriver(firefoxOptions);
		case "edge":
			WebDriverManager.edgedriver().setup();
			final EdgeOptions edgeOptions = new EdgeOptions();
			if (headless) {
				edgeOptions.addArguments("--headless=new");
			}
			return new EdgeDriver(edgeOptions);
		case "chrome":
		default:
			WebDriverManager.chromedriver().setup();
			final ChromeOptions chromeOptions = new ChromeOptions();
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			chromeOptions.addArguments("--disable-gpu");
			chromeOptions.addArguments("--no-sandbox");
			chromeOptions.addArguments("--disable-dev-shm-usage");
			return new ChromeDriver(chromeOptions);
		}
	}

	private Path prepareScreenshotsDirectory() throws IOException {
		final String configuredDir = property("saleads.screenshots.dir", "target/saleads-screenshots");
		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Paths.get(configuredDir, runId);
		Files.createDirectories(dir);
		return dir;
	}

	private void captureScreenshot(final String checkpoint) {
		try {
			final Path screenshotPath = screenshotsDir.resolve(checkpoint + ".png");
			final byte[] data = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(screenshotPath, data);
			System.out.println("[EVIDENCE] Screenshot saved: " + screenshotPath.toAbsolutePath());
		} catch (Exception ex) {
			System.out.println("[EVIDENCE] Failed to capture screenshot for " + checkpoint + ": " + ex.getMessage());
		}
	}

	private void captureFullPageScreenshot(final String checkpoint) {
		try {
			final Dimension original = driver.manage().window().getSize();
			final Long scrollWidth = Long.parseLong(String
					.valueOf(((JavascriptExecutor) driver).executeScript("return Math.max(document.body.scrollWidth,"
							+ "document.documentElement.scrollWidth,document.documentElement.clientWidth);")));
			final Long scrollHeight = Long.parseLong(String
					.valueOf(((JavascriptExecutor) driver).executeScript("return Math.max(document.body.scrollHeight,"
							+ "document.documentElement.scrollHeight,document.documentElement.clientHeight);")));

			final int width = Math.max(original.width, Math.min(scrollWidth.intValue(), 2200));
			final int height = Math.max(original.height, Math.min(scrollHeight.intValue(), 5000));
			driver.manage().window().setSize(new Dimension(width, height));
			waitForUiAfterClickIfNeeded();
			captureScreenshot(checkpoint);
			driver.manage().window().setSize(original);
			waitForUiAfterClickIfNeeded();
		} catch (Exception ex) {
			captureScreenshot(checkpoint);
		}
	}

	private WebElement clickFirstVisibleByTextAnyTag(final String... texts) {
		final By locator = byAnyVisibleText(texts);
		final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		safeClick(element);
		waitForUiAfterClickIfNeeded();
		return element;
	}

	private void expandIfNeededByText(final String text) {
		try {
			final List<WebElement> targets = driver.findElements(byAnyVisibleText(text));
			for (final WebElement element : targets) {
				if (element.isDisplayed()) {
					final String expanded = element.getAttribute("aria-expanded");
					if (expanded == null || "false".equalsIgnoreCase(expanded)) {
						safeClick(element);
						waitForUiAfterClickIfNeeded();
					}
					return;
				}
			}
		} catch (Exception ignored) {
			// Clicking based on visible text is best effort in dynamic sidebars.
		}
	}

	private void waitForUiAfterClickIfNeeded() {
		try {
			wait.until((ExpectedCondition<Boolean>) webDriver -> {
				if (!(webDriver instanceof JavascriptExecutor)) {
					return true;
				}
				final Object readyState = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
				return "complete".equals(String.valueOf(readyState)) || "interactive".equals(String.valueOf(readyState));
			});
		} catch (TimeoutException ignored) {
			// Continue even if SPA keeps loading in background.
		}
	}

	private void assertVisibleByTexts(final String description, final List<String> texts) {
		for (final String text : texts) {
			if (isAnyElementVisible(By.xpath("//*[contains(normalize-space(.), " + toXpathLiteral(text) + ")]"))) {
				return;
			}
		}
		fail("Expected to find " + description + " with any of texts: " + texts);
	}

	private void assertAnyVisible(final String description, final List<String> texts) {
		assertVisibleByTexts(description, texts);
	}

	private void assertInputVisibleByLabelOrPlaceholder(final String labelText) {
		final WebElement input = findVisibleInputByLabelOrPlaceholder(labelText);
		assertTrue("Expected input for label or placeholder: " + labelText, input.isDisplayed());
	}

	private WebElement findVisibleInputByLabelOrPlaceholder(final String labelText) {
		final List<By> selectors = List.of(
				By.xpath("//label[contains(normalize-space(.), " + toXpathLiteral(labelText) + ")]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, " + toXpathLiteral(labelText) + ")]"),
				By.xpath("//input[contains(@aria-label, " + toXpathLiteral(labelText) + ")]"));

		for (final By selector : selectors) {
			try {
				final WebElement element = new WebDriverWait(driver, Duration.ofSeconds(10))
						.until(ExpectedConditions.visibilityOfElementLocated(selector));
				return element;
			} catch (TimeoutException ignored) {
				// Try the next selector.
			}
		}
		throw new NoSuchElementException("Could not find visible input for: " + labelText);
	}

	private boolean isAnyElementVisible(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private By byAnyVisibleText(final String... texts) {
		final StringBuilder expression = new StringBuilder("//*[");
		for (int i = 0; i < texts.length; i++) {
			if (i > 0) {
				expression.append(" or ");
			}
			expression.append("contains(normalize-space(.), ").append(toXpathLiteral(texts[i])).append(")");
		}
		expression.append("]");
		return By.xpath(expression.toString());
	}

	private String requiredProperty(final String key) {
		final String value = System.getProperty(key);
		if (value == null || value.trim().isEmpty()) {
			fail("Missing required system property: " + key);
		}
		return value;
	}

	private String property(final String key, final String defaultValue) {
		final String value = System.getProperty(key);
		return value == null || value.trim().isEmpty() ? defaultValue : value;
	}

	private void safeClick(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void assertLegalContentVisible(final String heading) {
		final List<By> legalContentLocators = List.of(
				By.xpath("//h1[contains(normalize-space(.), " + toXpathLiteral(heading) + ")]"),
				By.xpath("//h2[contains(normalize-space(.), " + toXpathLiteral(heading) + ")]"),
				By.xpath("//main//*[string-length(normalize-space(.)) > 80]"),
				By.xpath("//article//*[string-length(normalize-space(.)) > 80]"),
				By.xpath("//*[contains(normalize-space(.), 'última actualización') or contains(normalize-space(.), 'Last updated')]"));

		for (final By locator : legalContentLocators) {
			if (isAnyElementVisible(locator)) {
				return;
			}
		}
		fail("Expected legal content to be visible for: " + heading);
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(", \"'\", ");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	private String sanitizeForFileName(final String raw) {
		return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private boolean sameUrl(final String sourceUrl, final String targetUrl) {
		try {
			final URI source = new URI(sourceUrl);
			final URI target = new URI(targetUrl);
			if (source.getScheme() == null || source.getHost() == null || target.getScheme() == null
					|| target.getHost() == null) {
				return true;
			}
			final String sourcePath = source.getPath() == null ? "" : source.getPath();
			final String targetPath = target.getPath() == null ? "" : target.getPath();
			return source.getScheme().equalsIgnoreCase(target.getScheme())
					&& source.getHost().equalsIgnoreCase(target.getHost()) && sourcePath.equals(targetPath)
					&& sameNullable(source.getQuery(), target.getQuery());
		} catch (URISyntaxException ex) {
			return sourceUrl.equals(targetUrl);
		}
	}

	private boolean sameNullable(final String a, final String b) {
		if (a == null && b == null) {
			return true;
		}
		if (a == null || b == null) {
			return false;
		}
		return a.equals(b);
	}

	private String pass(final String detail) {
		return "PASS - " + detail;
	}

	private String failStatus(final Exception ex) {
		return "FAIL - " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
	}

	private void printFinalReport() {
		System.out.println("========== SaleADS Mi Negocio Full Workflow Report ==========");
		System.out.println("Login: " + stepResults.getOrDefault("Login", "NOT_EXECUTED"));
		System.out.println("Mi Negocio menu: " + stepResults.getOrDefault("Mi Negocio menu", "NOT_EXECUTED"));
		System.out.println("Agregar Negocio modal: " + stepResults.getOrDefault("Agregar Negocio modal", "NOT_EXECUTED"));
		System.out.println(
				"Administrar Negocios view: " + stepResults.getOrDefault("Administrar Negocios view", "NOT_EXECUTED"));
		System.out.println("Información General: " + stepResults.getOrDefault("Información General", "NOT_EXECUTED"));
		System.out.println(
				"Detalles de la Cuenta: " + stepResults.getOrDefault("Detalles de la Cuenta", "NOT_EXECUTED"));
		System.out.println("Tus Negocios: " + stepResults.getOrDefault("Tus Negocios", "NOT_EXECUTED"));
		System.out.println(
				"Términos y Condiciones: " + stepResults.getOrDefault("Términos y Condiciones", "NOT_EXECUTED"));
		System.out.println(
				"Política de Privacidad: " + stepResults.getOrDefault("Política de Privacidad", "NOT_EXECUTED"));
		if (legalUrls.isEmpty()) {
			System.out.println("Legal URLs: none captured");
		} else {
			System.out.println("Legal URLs:");
			for (final String url : legalUrls) {
				System.out.println(" - " + url);
			}
		}
		System.out.println("Screenshots directory: " + screenshotsDir.toAbsolutePath());
		System.out.println("==============================================================");
	}
}
