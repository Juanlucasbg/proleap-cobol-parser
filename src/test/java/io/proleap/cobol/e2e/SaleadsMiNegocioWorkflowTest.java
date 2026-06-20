package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow validation for SaleADS "Mi Negocio".
 *
 * <p>
 * Execution is intentionally opt-in. Enable with:
 * -Dsaleads.e2e.enabled=true
 *
 * <p>
 * Runtime configuration:
 * -Dsaleads.base.url=https://<current-environment-host> (optional; if omitted,
 * the opened session is expected to already be on the login page)
 * -Dsaleads.remote.url=http://grid:4444/wd/hub (optional)
 * -Dsaleads.browser=chrome|firefox|edge (default: chrome)
 * -Dsaleads.headless=true|false (default: true)
 * -Dsaleads.timeout.seconds=30 (default: 30)
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String STEP_INFORMACION_GENERAL = "Información General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_POLITICA = "Política de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private final Map<String, String> legalFinalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Duration timeout;
	private Path reportDirectory;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set saleads.e2e.enabled=true to execute UI workflow test.", isE2eEnabled());
		timeout = Duration.ofSeconds(readIntConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", 30));
		wait = null;
		reportDirectory = createReportDirectory();
		driver = createDriver();
		wait = new WebDriverWait(driver, timeout);

		final String baseUrl = readConfig("saleads.base.url", "SALEADS_BASE_URL");
		if (!baseUrl.isBlank()) {
			driver.get(baseUrl);
			waitForUiLoad();
		}
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
	public void saleadsMiNegocioFullWorkflow() {
		runStep(STEP_LOGIN, () -> {
			clickLoginWithGoogle();
			selectGoogleAccountIfPresent("juanlucasbarbiergarzon@gmail.com");
			waitForVisible(byNavigationWithText("Negocio"), "left sidebar with Negocio");
			captureScreenshot("01-dashboard-loaded.png");
		});

		runStep(STEP_MI_NEGOCIO_MENU, () -> {
			clickByText("Negocio");
			clickByText("Mi Negocio");
			waitForVisible(byText("Agregar Negocio"), "Agregar Negocio option");
			waitForVisible(byText("Administrar Negocios"), "Administrar Negocios option");
			captureScreenshot("02-mi-negocio-expanded-menu.png");
		});

		runStep(STEP_AGREGAR_NEGOCIO_MODAL, () -> {
			clickByText("Agregar Negocio");
			waitForVisible(byText("Crear Nuevo Negocio"), "Crear Nuevo Negocio modal title");
			waitForVisible(byBusinessNameInput(), "Nombre del Negocio input");
			waitForVisible(byText("Tienes 2 de 3 negocios"), "quota text");
			waitForVisible(byText("Cancelar"), "Cancelar button");
			waitForVisible(byText("Crear Negocio"), "Crear Negocio button");
			captureScreenshot("03-agregar-negocio-modal.png");

			WebElement businessNameInput = waitForVisible(byBusinessNameInput(), "Nombre del Negocio input");
			businessNameInput.click();
			waitForUiLoad();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatización");
			clickByText("Cancelar");
		});

		runStep(STEP_ADMINISTRAR_NEGOCIOS, () -> {
			if (!isVisible(byText("Administrar Negocios"))) {
				clickByText("Mi Negocio");
			}
			clickByText("Administrar Negocios");
			waitForVisible(byText("Información General"), "Información General section");
			waitForVisible(byText("Detalles de la Cuenta"), "Detalles de la Cuenta section");
			waitForVisible(byText("Tus Negocios"), "Tus Negocios section");
			waitForVisible(byText("Sección Legal"), "Sección Legal section");
			captureScreenshot("04-administrar-negocios-page-full.png");
		});

		runStep(STEP_INFORMACION_GENERAL, () -> {
			waitForVisible(byText("BUSINESS PLAN"), "BUSINESS PLAN text");
			waitForVisible(byText("Cambiar Plan"), "Cambiar Plan button");
			assertTrue("A visible email should exist in Información General area.", hasVisibleEmailOnPage());
			assertTrue("A visible user-like name/value should exist in Información General area.", hasVisibleUserNameLikeValue());
		});

		runStep(STEP_DETALLES_CUENTA, () -> {
			waitForVisible(byText("Cuenta creada"), "Cuenta creada");
			waitForVisible(byText("Estado activo"), "Estado activo");
			waitForVisible(byText("Idioma seleccionado"), "Idioma seleccionado");
		});

		runStep(STEP_TUS_NEGOCIOS, () -> {
			waitForVisible(byText("Tus Negocios"), "Tus Negocios section");
			waitForVisible(byText("Agregar Negocio"), "Agregar Negocio button");
			waitForVisible(byText("Tienes 2 de 3 negocios"), "business quota text");
		});

		runStep(STEP_TERMINOS, () -> validateLegalPage("Términos y Condiciones", "Términos y Condiciones",
				"05-terminos-y-condiciones.png", STEP_TERMINOS));

		runStep(STEP_POLITICA, () -> validateLegalPage("Política de Privacidad", "Política de Privacidad",
				"06-politica-de-privacidad.png", STEP_POLITICA));

		assertWorkflowPassed();
	}

	private void clickLoginWithGoogle() {
		By googleLoginButton = By.xpath(
				"//*[self::button or self::a or @role='button'][contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'google')]");
		waitForClickable(googleLoginButton, "Google login button").click();
		waitForUiLoad();
	}

	private void selectGoogleAccountIfPresent(final String accountEmail) {
		By accountOption = byText(accountEmail);
		if (isVisible(accountOption, 10)) {
			waitForClickable(accountOption, "Google account option").click();
			waitForUiLoad();
		}
	}

	private void validateLegalPage(final String linkText, final String headingText, final String screenshotName,
			final String reportField) throws IOException {
		String originalHandle = driver.getWindowHandle();
		Set<String> handlesBeforeClick = driver.getWindowHandles();
		String sourceUrl = driver.getCurrentUrl();

		clickByText(linkText);

		String newHandle = waitForNewWindowHandle(handlesBeforeClick, 12);
		boolean openedNewTab = newHandle != null;
		if (openedNewTab) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		}

		waitForVisible(byText(headingText), headingText + " heading");
		WebElement body = waitForVisible(By.tagName("body"), "legal page body");
		assertTrue("Legal content should be visible and not empty.",
				body.getText() != null && body.getText().replaceAll("\\s+", " ").length() > 120);

		captureScreenshot(screenshotName);
		legalFinalUrls.put(reportField, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiLoad();
			return;
		}

		if (!sourceUrl.equals(driver.getCurrentUrl())) {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void clickByText(final String text) {
		waitForClickable(byText(text), "clickable text [" + text + "]").click();
		waitForUiLoad();
	}

	private WebElement waitForVisible(final By locator, final String description) {
		return wait.until(driver -> {
			List<WebElement> elements = driver.findElements(locator);
			for (WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private WebElement waitForClickable(final By locator, final String description) {
		return wait.until(ExpectedConditions.elementToBeClickable(locator));
	}

	private void waitForUiLoad() {
		if (driver instanceof JavascriptExecutor) {
			ExpectedCondition<Boolean> documentReady = wd -> "complete"
					.equals(((JavascriptExecutor) wd).executeScript("return document.readyState"));
			wait.until(documentReady);
		}
		pause(400);
	}

	private void captureScreenshot(final String name) throws IOException {
		if (!(driver instanceof TakesScreenshot) || reportDirectory == null) {
			return;
		}
		byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(reportDirectory.resolve(name), bytes);
	}

	private void runStep(final String fieldName, final ThrowingRunnable stepLogic) {
		try {
			stepLogic.run();
			stepStatus.put(fieldName, Boolean.TRUE);
			stepDetails.put(fieldName, "PASS");
		} catch (Throwable throwable) {
			stepStatus.put(fieldName, Boolean.FALSE);
			stepDetails.put(fieldName, throwable.getClass().getSimpleName() + ": " + safeMessage(throwable));
			try {
				captureScreenshot("failure-" + slug(fieldName) + ".png");
			} catch (IOException ignored) {
				// Best effort artifact capture.
			}
		}
	}

	private void assertWorkflowPassed() {
		List<String> failedSteps = new ArrayList<>();
		for (Map.Entry<String, Boolean> entry : stepStatus.entrySet()) {
			if (!Boolean.TRUE.equals(entry.getValue())) {
				failedSteps.add(entry.getKey() + " => " + stepDetails.get(entry.getKey()));
			}
		}
		assertTrue("Final report contains failed validations: " + failedSteps, failedSteps.isEmpty());
	}

	private boolean isVisible(final By locator) {
		return isVisible(locator, timeout == null ? 5 : (int) timeout.getSeconds());
	}

	private boolean isVisible(final By locator, final int seconds) {
		try {
			WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(seconds));
			shortWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	private boolean hasVisibleEmailOnPage() {
		for (WebElement element : driver.findElements(By.xpath("//*[contains(normalize-space(.), '@')]"))) {
			if (element.isDisplayed() && EMAIL_PATTERN.matcher(element.getText()).find()) {
				return true;
			}
		}
		return false;
	}

	private boolean hasVisibleUserNameLikeValue() {
		List<String> disallowedSnippets = Arrays.asList("INFORMACIÓN GENERAL", "BUSINESS PLAN", "CAMBIAR PLAN", "CUENTA",
				"ESTADO", "IDIOMA", "TUS NEGOCIOS", "SECCIÓN LEGAL", "AGREGAR NEGOCIO", "ADMINISTRAR NEGOCIOS");
		for (WebElement element : driver.findElements(By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::p or self::div]"))) {
			if (!element.isDisplayed()) {
				continue;
			}
			String text = element.getText() == null ? "" : element.getText().trim();
			if (text.length() < 3 || text.length() > 80 || EMAIL_PATTERN.matcher(text).find()) {
				continue;
			}
			String upper = text.toUpperCase(Locale.ROOT);
			boolean disallowed = disallowedSnippets.stream().anyMatch(upper::contains);
			if (!disallowed && text.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private By byText(final String text) {
		String literal = xpathLiteral(text);
		return By.xpath(
				"//*[self::button or self::a or self::span or self::div or self::p or self::li or self::h1 or self::h2 or self::h3 or self::h4]"
						+ "[normalize-space(.)=" + literal + " or contains(normalize-space(.), " + literal + ")]");
	}

	private By byNavigationWithText(final String text) {
		String literal = xpathLiteral(text);
		return By.xpath(
				"//*[self::aside or self::nav or @role='navigation']//*[normalize-space(.)=" + literal
						+ " or contains(normalize-space(.), " + literal + ")]");
	}

	private By byBusinessNameInput() {
		return By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | "
				+ "//input[contains(@placeholder, 'Nombre del Negocio')] | "
				+ "//input[contains(@aria-label, 'Nombre del Negocio')]");
	}

	private String waitForNewWindowHandle(final Set<String> handlesBeforeClick, final int seconds) {
		try {
			WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(seconds));
			return shortWait.until(wd -> {
				Set<String> currentHandles = wd.getWindowHandles();
				for (String handle : currentHandles) {
					if (!handlesBeforeClick.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (Exception ignored) {
			return null;
		}
	}

	private WebDriver createDriver() {
		final String browser = readConfig("saleads.browser", "SALEADS_BROWSER", "chrome").trim().toLowerCase(Locale.ROOT);
		final boolean headless = readBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true);
		final String remoteUrl = readConfig("saleads.remote.url", "SALEADS_REMOTE_URL");

		try {
			if (!remoteUrl.isBlank()) {
				switch (browser) {
				case "firefox":
					return new RemoteWebDriver(URI.create(remoteUrl).toURL(), configureFirefox(headless));
				case "edge":
					return new RemoteWebDriver(URI.create(remoteUrl).toURL(), configureEdge(headless));
				case "chrome":
				default:
					return new RemoteWebDriver(URI.create(remoteUrl).toURL(), configureChrome(headless));
				}
			}

			switch (browser) {
			case "firefox":
				return new FirefoxDriver(configureFirefox(headless));
			case "edge":
				return new EdgeDriver(configureEdge(headless));
			case "chrome":
			default:
				return new ChromeDriver(configureChrome(headless));
			}
		} catch (MalformedURLException malformedURLException) {
			throw new IllegalArgumentException("Invalid saleads.remote.url value.", malformedURLException);
		}
	}

	private ChromeOptions configureChrome(final boolean headless) {
		ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-notifications");
		options.addArguments("--lang=es-ES");
		return options;
	}

	private FirefoxOptions configureFirefox(final boolean headless) {
		FirefoxOptions options = new FirefoxOptions();
		if (headless) {
			options.addArguments("-headless");
		}
		options.addArguments("--width=1920");
		options.addArguments("--height=1080");
		options.addPreference("intl.accept_languages", "es-ES");
		return options;
	}

	private EdgeOptions configureEdge(final boolean headless) {
		EdgeOptions options = new EdgeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-notifications");
		options.addArguments("--lang=es-ES");
		return options;
	}

	private Path createReportDirectory() throws IOException {
		Path dir = Paths.get("target", "saleads-e2e", DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-"));
		return Files.createDirectories(dir);
	}

	private void writeFinalReport() throws IOException {
		if (reportDirectory == null) {
			return;
		}

		List<String> orderedFields = Arrays.asList(STEP_LOGIN, STEP_MI_NEGOCIO_MENU, STEP_AGREGAR_NEGOCIO_MODAL,
				STEP_ADMINISTRAR_NEGOCIOS, STEP_INFORMACION_GENERAL, STEP_DETALLES_CUENTA, STEP_TUS_NEGOCIOS, STEP_TERMINOS,
				STEP_POLITICA);

		StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio Workflow Final Report").append(System.lineSeparator());
		report.append("Generated at: ").append(Instant.now()).append(System.lineSeparator());
		report.append(System.lineSeparator());

		for (String field : orderedFields) {
			Boolean passed = stepStatus.get(field);
			String status = passed == null ? "NOT_EXECUTED" : (passed ? "PASS" : "FAIL");
			report.append("- ").append(field).append(": ").append(status);
			String detail = stepDetails.get(field);
			if (detail != null && !detail.isBlank() && !"PASS".equals(detail)) {
				report.append(" (").append(detail).append(")");
			}
			report.append(System.lineSeparator());
		}

		if (!legalFinalUrls.isEmpty()) {
			report.append(System.lineSeparator()).append("Captured legal URLs").append(System.lineSeparator());
			for (Map.Entry<String, String> entry : legalFinalUrls.entrySet()) {
				report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		report.append(System.lineSeparator());
		report.append("Evidence directory: ").append(reportDirectory.toAbsolutePath()).append(System.lineSeparator());
		Files.writeString(reportDirectory.resolve("final-report.txt"), report.toString(), StandardCharsets.UTF_8);
	}

	private static String safeMessage(final Throwable throwable) {
		return throwable.getMessage() == null ? "No details available." : throwable.getMessage();
	}

	private static String slug(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		StringBuilder builder = new StringBuilder("concat(");
		char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			String character = String.valueOf(chars[i]);
			if ("'".equals(character)) {
				builder.append("\"'\"");
			} else if ("\"".equals(character)) {
				builder.append("'\"'");
			} else {
				builder.append("'").append(character).append("'");
			}
			if (i < chars.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private static void pause(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private static boolean isE2eEnabled() {
		return readBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false);
	}

	private static int readIntConfig(final String systemKey, final String envKey, final int defaultValue) {
		String value = readConfig(systemKey, envKey, String.valueOf(defaultValue));
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
			return defaultValue;
		}
	}

	private static boolean readBooleanConfig(final String systemKey, final String envKey, final boolean defaultValue) {
		return Boolean.parseBoolean(readConfig(systemKey, envKey, String.valueOf(defaultValue)));
	}

	private static String readConfig(final String systemKey, final String envKey) {
		return readConfig(systemKey, envKey, "");
	}

	private static String readConfig(final String systemKey, final String envKey, final String defaultValue) {
		String fromSystem = System.getProperty(systemKey);
		if (fromSystem != null && !fromSystem.isBlank()) {
			return fromSystem.trim();
		}
		String fromEnv = System.getenv(envKey);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv.trim();
		}
		return defaultValue;
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
