package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.junit.Assert;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end UI test for the SaleADS "Mi Negocio" workflow.
 *
 * This test intentionally avoids hardcoded domains. Configure runtime values with:
 * - SALEADS_START_URL or -Dsaleads.start.url
 * - SALEADS_BROWSER (auto, chrome, firefox) or -Dsaleads.browser
 * - SALEADS_HEADLESS (true/false) or -Dsaleads.headless
 * - SALEADS_SELENIUM_REMOTE_URL or -Dsaleads.selenium.remote.url
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informaci\u00F3n General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "T\u00E9rminos y Condiciones";
	private static final String STEP_POLITICA = "Pol\u00EDtica de Privacidad";

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern NAME_PATTERN = Pattern.compile("\\b[A-Za-z]{2,}\\s+[A-Za-z]{2,}\\b");

	private WebDriver driver;
	private WebDriverWait wait;

	private Path runOutputDirectory;
	private Path screenshotDirectory;
	private Path reportFile;

	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepNotes = new LinkedHashMap<>();

	@Before
	public void setUp() throws Exception {
		final boolean e2eEnabled = Boolean
				.parseBoolean(config("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"SaleADS E2E disabled. Enable with SALEADS_E2E_ENABLED=true or -Dsaleads.e2e.enabled=true.",
				e2eEnabled);

		initializeResultSlots();

		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
		runOutputDirectory = Paths.get("target", "surefire-reports", "saleads-mi-negocio", runId);
		screenshotDirectory = runOutputDirectory.resolve("screenshots");
		reportFile = runOutputDirectory.resolve("final-report.txt");
		Files.createDirectories(screenshotDirectory);

		driver = createWebDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(resolveLongWaitSeconds()));
		waitForUiToLoad();

		final String configuredStartUrl = config("saleads.start.url", "SALEADS_START_URL", "");
		if (!configuredStartUrl.isBlank()) {
			driver.navigate().to(configuredStartUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		try {
			runStep(STEP_LOGIN, this::stepLoginWithGoogle);
			runStep(STEP_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
			runStep(STEP_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
			runStep(STEP_ADMINISTRAR_NEGOCIOS, this::stepOpenAdministrarNegocios);
			runStep(STEP_INFO_GENERAL, this::stepValidateInformacionGeneral);
			runStep(STEP_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
			runStep(STEP_TUS_NEGOCIOS, this::stepValidateTusNegocios);
			runStep(STEP_TERMINOS, () -> {
				ensureOnAdministrarNegociosPage();
				termsUrl = openLegalLinkAndValidate(
						new String[] { "T\u00E9rminos y Condiciones", "Terminos y Condiciones" },
						new String[] { "T\u00E9rminos y Condiciones", "Terminos y Condiciones" },
						"05_terminos_y_condiciones");
			});
			runStep(STEP_POLITICA, () -> {
				ensureOnAdministrarNegociosPage();
				privacyUrl = openLegalLinkAndValidate(
						new String[] { "Pol\u00EDtica de Privacidad", "Politica de Privacidad" },
						new String[] { "Pol\u00EDtica de Privacidad", "Politica de Privacidad" },
						"06_politica_de_privacidad");
			});
		} finally {
			writeFinalReport();
		}

		assertAllStepsPassed();
	}

	private void stepLoginWithGoogle() throws Exception {
		if ("about:blank".equalsIgnoreCase(driver.getCurrentUrl())) {
			throw new IllegalStateException(
					"Browser is on about:blank. Set SALEADS_START_URL (or -Dsaleads.start.url) "
							+ "or provide a remote session already on SaleADS login.");
		}

		clickByVisibleText("Sign in with Google", "Iniciar sesi\u00F3n con Google", "Continuar con Google", "Google");

		optionalClickByVisibleText(Duration.ofSeconds(10), GOOGLE_ACCOUNT_EMAIL);

		waitForAnyVisibleText(Duration.ofSeconds(45), "Negocio", "Mi Negocio");
		assertVisibleText("Negocio", "Mi Negocio");
		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		captureScreenshot("03_agregar_negocio_modal");

		final WebElement nameInput = findFirstVisible(Duration.ofSeconds(8),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//*[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
		if (nameInput != null) {
			nameInput.click();
			waitForUiToLoad();
			nameInput.clear();
			nameInput.sendKeys("Negocio Prueba Automatizacion");
		}

		clickByVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		expandMiNegocioIfNeeded();
		clickByVisibleText("Administrar Negocios");

		assertVisibleText("Informaci\u00F3n General", "Informacion General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Secci\u00F3n Legal", "Seccion Legal");
		captureScreenshot("04_administrar_negocios_page");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		assertVisibleText("Informaci\u00F3n General", "Informacion General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String pageText = visiblePageText();
		final boolean hasEmail = EMAIL_PATTERN.matcher(pageText).find();
		final boolean hasName = hasLikelyUserName(pageText) || isAnyTextVisible("Nombre", "Usuario");

		if (!hasEmail) {
			throw new AssertionError("No user email found in visible content.");
		}
		if (!hasName) {
			throw new AssertionError("No likely user name found in visible content.");
		}
	}

	private void stepValidateDetallesCuenta() throws Exception {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() throws Exception {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		final WebElement section = findSectionByHeading("Tus Negocios");
		final String sectionText = section != null ? section.getText() : visiblePageText();
		final boolean hasListLikeContent = sectionText != null && sectionText.replace("Tus Negocios", "").trim().length() > 20;
		if (!hasListLikeContent) {
			throw new AssertionError("Business list content is not visible under 'Tus Negocios'.");
		}
	}

	private String openLegalLinkAndValidate(final String[] linkTexts, final String[] headingTexts, final String screenshotName)
			throws Exception {
		final String appTab = driver.getWindowHandle();
		final Set<String> oldHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkTexts);
		waitForUiToLoad();

		final String possiblyNewHandle = waitForNewWindowHandle(oldHandles, Duration.ofSeconds(12));
		final boolean openedNewTab = possiblyNewHandle != null;
		if (openedNewTab) {
			driver.switchTo().window(possiblyNewHandle);
			waitForUiToLoad();
		}

		waitForAnyVisibleText(Duration.ofSeconds(30), headingTexts);
		final String legalText = visiblePageText();
		if (legalText.trim().length() < 120) {
			throw new AssertionError("Legal page content is too short.");
		}

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appTab);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void ensureOnAdministrarNegociosPage() throws Exception {
		if (isAnyTextVisible("Secci\u00F3n Legal", "Seccion Legal")) {
			return;
		}
		expandMiNegocioIfNeeded();
		clickByVisibleText("Administrar Negocios");
		assertVisibleText("Secci\u00F3n Legal", "Seccion Legal");
	}

	private void expandMiNegocioIfNeeded() throws Exception {
		if (isAnyTextVisible("Administrar Negocios")) {
			return;
		}
		if (isAnyTextVisible("Mi Negocio")) {
			clickByVisibleText("Mi Negocio");
		}
		if (!isAnyTextVisible("Administrar Negocios") && isAnyTextVisible("Negocio")) {
			clickByVisibleText("Negocio");
		}
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}
	}

	private void runStep(final String name, final StepAction action) {
		try {
			action.run();
			stepResults.put(name, Boolean.TRUE);
			stepNotes.put(name, "PASS");
		} catch (final Exception ex) {
			stepResults.put(name, Boolean.FALSE);
			stepNotes.put(name, ex.getClass().getSimpleName() + ": " + ex.getMessage());
			try {
				captureScreenshot("error_" + sanitizeFileName(name));
			} catch (final Exception ignored) {
				// Keep going to preserve a full PASS/FAIL matrix.
			}
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : stepResults.entrySet()) {
			if (!Boolean.TRUE.equals(entry.getValue())) {
				failedSteps.add(entry.getKey());
			}
		}

		Assert.assertTrue("Failed workflow validations: " + failedSteps + ". See report: " + reportFile, failedSteps.isEmpty());
	}

	private void initializeResultSlots() {
		stepResults.put(STEP_LOGIN, Boolean.FALSE);
		stepResults.put(STEP_MI_NEGOCIO_MENU, Boolean.FALSE);
		stepResults.put(STEP_AGREGAR_NEGOCIO_MODAL, Boolean.FALSE);
		stepResults.put(STEP_ADMINISTRAR_NEGOCIOS, Boolean.FALSE);
		stepResults.put(STEP_INFO_GENERAL, Boolean.FALSE);
		stepResults.put(STEP_DETALLES_CUENTA, Boolean.FALSE);
		stepResults.put(STEP_TUS_NEGOCIOS, Boolean.FALSE);
		stepResults.put(STEP_TERMINOS, Boolean.FALSE);
		stepResults.put(STEP_POLITICA, Boolean.FALSE);
	}

	private void writeFinalReport() throws IOException {
		Files.createDirectories(runOutputDirectory);

		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Workflow - Final Report\n");
		sb.append("=========================================\n\n");

		appendStep(sb, STEP_LOGIN);
		appendStep(sb, STEP_MI_NEGOCIO_MENU);
		appendStep(sb, STEP_AGREGAR_NEGOCIO_MODAL);
		appendStep(sb, STEP_ADMINISTRAR_NEGOCIOS);
		appendStep(sb, STEP_INFO_GENERAL);
		appendStep(sb, STEP_DETALLES_CUENTA);
		appendStep(sb, STEP_TUS_NEGOCIOS);
		appendStep(sb, STEP_TERMINOS);
		appendStep(sb, STEP_POLITICA);

		sb.append("\nEvidence\n");
		sb.append("--------\n");
		sb.append("Screenshots directory: ").append(screenshotDirectory.toAbsolutePath()).append('\n');
		sb.append("T\u00E9rminos y Condiciones URL: ").append(termsUrl).append('\n');
		sb.append("Pol\u00EDtica de Privacidad URL: ").append(privacyUrl).append('\n');

		Files.writeString(reportFile, sb.toString(), StandardCharsets.UTF_8);
	}

	private void appendStep(final StringBuilder sb, final String stepName) {
		final boolean pass = Boolean.TRUE.equals(stepResults.get(stepName));
		final String note = stepNotes.getOrDefault(stepName, pass ? "PASS" : "FAIL");
		sb.append("- ").append(stepName).append(": ").append(pass ? "PASS" : "FAIL");
		if (!pass) {
			sb.append(" | ").append(note);
		}
		sb.append('\n');
	}

	private WebDriver createWebDriver() throws Exception {
		final String remoteUrl = config("saleads.selenium.remote.url", "SALEADS_SELENIUM_REMOTE_URL", "");
		final String browser = config("saleads.browser", "SALEADS_BROWSER", "auto").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(config("saleads.headless", "SALEADS_HEADLESS", "true"));

		if (!remoteUrl.isBlank()) {
			if ("firefox".equals(browser)) {
				final FirefoxOptions firefox = new FirefoxOptions();
				if (headless) {
					firefox.addArguments("-headless");
				}
				return new RemoteWebDriver(new java.net.URL(remoteUrl), firefox);
			}
			final ChromeOptions chrome = new ChromeOptions();
			if (headless) {
				chrome.addArguments("--headless=new");
			}
			chrome.addArguments("--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage");
			return new RemoteWebDriver(new java.net.URL(remoteUrl), chrome);
		}

		if ("chrome".equals(browser)) {
			return createLocalChrome(headless);
		}
		if ("firefox".equals(browser)) {
			return createLocalFirefox(headless);
		}

		try {
			return createLocalChrome(headless);
		} catch (final Exception chromeError) {
			return createLocalFirefox(headless);
		}
	}

	private WebDriver createLocalChrome(final boolean headless) {
		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage");
		return new ChromeDriver(options);
	}

	private WebDriver createLocalFirefox(final boolean headless) {
		final FirefoxOptions options = new FirefoxOptions();
		if (headless) {
			options.addArguments("-headless");
		}
		return new FirefoxDriver(options);
	}

	private int resolveLongWaitSeconds() {
		try {
			return Integer.parseInt(config("saleads.wait.seconds", "SALEADS_WAIT_SECONDS", "30"));
		} catch (final NumberFormatException ignored) {
			return 30;
		}
	}

	private String config(final String property, final String environment, final String fallback) {
		final String propertyValue = System.getProperty(property);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		final String envValue = System.getenv(environment);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		return fallback;
	}

	private void clickByVisibleText(final String... textOptions) throws Exception {
		final WebElement candidate = waitForAnyVisibleElement(Duration.ofSeconds(resolveLongWaitSeconds()), textOptions);
		clickAndWait(candidate);
	}

	private void optionalClickByVisibleText(final Duration timeout, final String... textOptions) throws Exception {
		final WebElement candidate = waitForAnyVisibleElement(timeout, textOptions);
		if (candidate != null) {
			clickAndWait(candidate);
		}
	}

	private void clickAndWait(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception primaryClickFailure) {
			final WebElement clickableFallback = findClickableAncestorOrSelf(element);
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickableFallback);
		}
		waitForUiToLoad();
	}

	private WebElement waitForAnyVisibleElement(final Duration timeout, final String... textOptions) throws Exception {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			for (final String text : textOptions) {
				final String literal = toXPathLiteral(text);
				final String containsExpr = "contains(normalize-space(.), " + literal + ")";
				final By locator = By.xpath(
						"//*[self::button or self::a or @role='button' or @onclick][" + containsExpr + "] | //*[" + containsExpr
								+ "]");

				final List<WebElement> elements = driver.findElements(locator);
				for (final WebElement element : elements) {
					try {
						if (element.isDisplayed()) {
							return findClickableAncestorOrSelf(element);
						}
					} catch (final Exception ignored) {
						// Element may become stale while iterating.
					}
				}
			}
			Thread.sleep(250L);
		}
		if (timeout.toSeconds() <= 10) {
			return null;
		}
		throw new TimeoutException("Could not locate visible element by text options: " + String.join(", ", textOptions));
	}

	private WebElement findClickableAncestorOrSelf(final WebElement element) {
		try {
			final List<WebElement> clickable = element.findElements(
					By.xpath("./ancestor-or-self::*[self::button or self::a or @role='button' or @onclick][1]"));
			if (!clickable.isEmpty()) {
				return clickable.get(0);
			}
		} catch (final Exception ignored) {
			// If lookup fails, use the original node.
		}
		return element;
	}

	private void assertVisibleText(final String... textOptions) {
		if (!isAnyTextVisible(textOptions)) {
			throw new AssertionError("Expected visible text not found. Options: " + String.join(", ", textOptions));
		}
	}

	private boolean isAnyTextVisible(final String... textOptions) {
		for (final String text : textOptions) {
			if (isTextVisible(text)) {
				return true;
			}
		}
		return false;
	}

	private boolean isTextVisible(final String text) {
		final String literal = toXPathLiteral(text);
		final By locator = By.xpath("//*[contains(normalize-space(.), " + literal + ")]");
		for (final WebElement element : driver.findElements(locator)) {
			try {
				if (element.isDisplayed()) {
					return true;
				}
			} catch (final Exception ignored) {
				// Stale element during iteration.
			}
		}
		return false;
	}

	private void waitForAnyVisibleText(final Duration timeout, final String... textOptions) throws InterruptedException {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			if (isAnyTextVisible(textOptions)) {
				return;
			}
			Thread.sleep(250L);
		}
		throw new TimeoutException("Could not find visible text options: " + String.join(", ", textOptions));
	}

	private WebElement findFirstVisible(final Duration timeout, final By... locators) throws InterruptedException {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			for (final By locator : locators) {
				final List<WebElement> candidates = driver.findElements(locator);
				for (final WebElement candidate : candidates) {
					try {
						if (candidate.isDisplayed()) {
							return candidate;
						}
					} catch (final Exception ignored) {
						// Ignore stale element and continue.
					}
				}
			}
			Thread.sleep(200L);
		}
		return null;
	}

	private WebElement findSectionByHeading(final String heading) {
		final String literal = toXPathLiteral(heading);
		final By sectionLocator = By.xpath(
				"//*[contains(normalize-space(.), " + literal + ")]/ancestor::*[self::section or self::div][1]");
		for (final WebElement section : driver.findElements(sectionLocator)) {
			try {
				if (section.isDisplayed()) {
					return section;
				}
			} catch (final Exception ignored) {
				// Ignore stale element.
			}
		}
		return null;
	}

	private String waitForNewWindowHandle(final Set<String> oldHandles, final Duration timeout) throws InterruptedException {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			final Set<String> currentHandles = driver.getWindowHandles();
			if (currentHandles.size() > oldHandles.size()) {
				for (final String handle : currentHandles) {
					if (!oldHandles.contains(handle)) {
						return handle;
					}
				}
			}
			Thread.sleep(200L);
		}
		return null;
	}

	private String visiblePageText() {
		try {
			final WebElement body = driver.findElement(By.tagName("body"));
			return body.getText();
		} catch (final Exception ignored) {
			return "";
		}
	}

	private boolean hasLikelyUserName(final String pageText) {
		if (pageText == null || pageText.isBlank()) {
			return false;
		}
		final String normalized = pageText.replaceAll("\\s+", " ");
		return NAME_PATTERN.matcher(normalized).find();
	}

	private void captureScreenshot(final String name) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = screenshotDirectory.resolve(sanitizeFileName(name) + ".png");
		Files.copy(source.toPath(), destination);
	}

	private void waitForUiToLoad() {
		if (wait == null) {
			return;
		}
		try {
			wait.until(driver -> {
				try {
					return "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState"));
				} catch (final Exception ignored) {
					return true;
				}
			});
		} catch (final Exception ignored) {
			// Do not hard fail on transient document.readyState issues.
		}

		try {
			Thread.sleep(450L);
		} catch (final InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private String sanitizeFileName(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	private String toXPathLiteral(final String value) {
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
				sb.append(",\"'\",");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
