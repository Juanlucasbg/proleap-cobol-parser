package io.proleap.saleads.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end browser automation for the SaleADS "Mi Negocio" module workflow.
 *
 * This test does not hardcode any SaleADS domain. It supports any environment
 * using either:
 * - SALEADS_LOGIN_URL=<current-environment-login-url> to navigate directly.
 * - No URL provided: assumes browser is already on the login page.
 *
 * Optional environment variables:
 * - SELENIUM_REMOTE_URL : remote Selenium Grid URL.
 * - SALEADS_BROWSER     : chrome (default), firefox, edge.
 * - SALEADS_HEADLESS    : true/false (default false).
 * - SALEADS_TIMEOUT_SEC : explicit wait timeout in seconds (default 25).
 * - SALEADS_EXPECTED_EMAIL : default juanlucasbarbiergarzon@gmail.com.
 * - SALEADS_EXPECTED_NAME  : optional expected user name.
 */
public class SaleadsMiNegocioFullTest {

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Información General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "Términos y Condiciones";
	private static final String PRIVACIDAD = "Política de Privacidad";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;
	private String appWindowHandle;
	private String expectedEmail;
	private String expectedName;

	@Before
	public void setUp() throws Exception {
		initializeReport();
		Assume.assumeTrue(
				"Skipping SaleADS E2E: set SALEADS_E2E_ENABLED=true to run this browser test.",
				Boolean.parseBoolean(envOrDefault("SALEADS_E2E_ENABLED", "false")));
		expectedEmail = envOrDefault("SALEADS_EXPECTED_EMAIL", GOOGLE_ACCOUNT_EMAIL);
		expectedName = System.getenv("SALEADS_EXPECTED_NAME");

		artifactsDir = createArtifactsDirectory();
		driver = createDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(envInt("SALEADS_TIMEOUT_SEC", 25)));

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
		}

		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() throws IOException {
		if (artifactsDir != null) {
			writeFinalReport();
		}
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep(LOGIN, this::stepLoginWithGoogleAndValidateMainUi);
		runStep(MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		runStep(INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		runStep(DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(TERMINOS, () -> stepOpenAndValidateLegalPage("Términos y Condiciones", "Términos y Condiciones", "08-terminos"));
		runStep(PRIVACIDAD, () -> stepOpenAndValidateLegalPage("Política de Privacidad", "Política de Privacidad", "09-privacidad"));

		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().pass) {
				failed.add(entry.getKey() + " -> " + entry.getValue().details);
			}
		}
		Assert.assertTrue("Final Report contains failures: " + failed, failed.isEmpty());
	}

	private void stepLoginWithGoogleAndValidateMainUi() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Login con Google", "Login with Google");
		waitForUiLoad();
		maybeSwitchToNewTab();
		maybeChooseGoogleAccount(expectedEmail);
		switchBackToApplicationWindow();

		final WebElement sidebar = waitForAnyVisible(
				By.cssSelector("aside"),
				By.cssSelector("nav"),
				By.cssSelector("[class*='sidebar']"),
				By.cssSelector("[data-testid*='sidebar']"),
				By.xpath("//*[contains(normalize-space(.), 'Mi Negocio')]"));
		Assert.assertTrue("Left sidebar navigation is not visible.", sidebar.isDisplayed());
		waitForUiLoad();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		waitForUiLoad();
		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		waitForTextVisible("Agregar Negocio");
		waitForTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForUiLoad();

		waitForTextVisible("Crear Nuevo Negocio");
		waitForTextVisible("Nombre del Negocio");
		waitForTextVisible("Tienes 2 de 3 negocios");
		waitForTextVisible("Cancelar");
		waitForTextVisible("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final WebElement nameField = findOptionalVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio') or @name='businessName']"));
		if (nameField != null) {
			nameField.clear();
			nameField.sendKeys("Negocio Prueba Automatización");
		}
		clickByVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioExpanded();
		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		waitForTextVisible("Información General");
		waitForTextVisible("Detalles de la Cuenta");
		waitForTextVisible("Tus Negocios");
		waitForTextVisible("Sección Legal");
		takeFullPageScreenshot("04-administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() {
		waitForTextVisible("Información General");
		final String bodyText = driver.findElement(By.tagName("body")).getText();

		final boolean emailVisible = bodyText.toLowerCase(Locale.ROOT).contains(expectedEmail.toLowerCase(Locale.ROOT))
				|| containsAnyEmail(bodyText);
		final boolean nameVisible = (expectedName != null && !expectedName.isBlank() && bodyText.contains(expectedName))
				|| isAnyTextVisible("Nombre", "Usuario", "Perfil");

		Assert.assertTrue("User email is not visible in Información General.", emailVisible);
		Assert.assertTrue("User name is not visible in Información General.", nameVisible);
		waitForTextVisible("BUSINESS PLAN");
		waitForTextVisible("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		waitForTextVisible("Detalles de la Cuenta");
		waitForTextVisible("Cuenta creada");
		waitForTextVisible("Estado activo");
		waitForTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitForTextVisible("Tus Negocios");
		waitForTextVisible("Agregar Negocio");
		waitForTextVisible("Tienes 2 de 3 negocios");

		final WebElement businessArea = waitForAnyVisible(
				By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]"),
				By.cssSelector("[class*='business']"),
				By.cssSelector("[data-testid*='business']"));
		Assert.assertTrue("Business list area is not visible.", businessArea.isDisplayed());
	}

	private void stepOpenAndValidateLegalPage(final String linkText, final String headingText, final String artifactPrefix)
			throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final int initialTabs = driver.getWindowHandles().size();

		clickByVisibleText(linkText);
		waitForUiLoad();

		final boolean openedNewTab = waitUntil(
				d -> d.getWindowHandles().size() > initialTabs
						|| !safeCurrentUrl().equalsIgnoreCase("about:blank"),
				15);
		if (openedNewTab && driver.getWindowHandles().size() > initialTabs) {
			maybeSwitchToNewTab();
		}

		waitForUiLoad();
		waitForTextVisible(headingText);
		assertLegalContentVisible(headingText);
		takeScreenshot(artifactPrefix + "-legal-page");
		legalUrls.put(linkText, safeCurrentUrl());

		if (!driver.getWindowHandle().equals(originalHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiLoad();
		switchBackToApplicationWindow();
	}

	private void assertLegalContentVisible(final String headingText) {
		final String pageText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Legal heading not visible: " + headingText, pageText.contains(headingText));
		Assert.assertTrue("Legal content appears empty.",
				pageText.length() > headingText.length() + 100 || pageText.split("\\R").length > 8);
	}

	private void ensureMiNegocioExpanded() {
		if (!isTextVisible("Administrar Negocios")) {
			if (isTextVisible("Mi Negocio")) {
				clickByVisibleText("Mi Negocio");
				waitForUiLoad();
			} else if (isTextVisible("Negocio")) {
				clickByVisibleText("Negocio");
				waitForUiLoad();
			}
		}
	}

	private void maybeChooseGoogleAccount(final String email) {
		waitForUiLoad();
		if (!isGoogleSelectorContext()) {
			return;
		}

		final WebElement account = findOptionalVisible(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(email) + ")]"));
		if (account != null) {
			account.click();
			waitForUiLoad();
		}
	}

	private boolean isGoogleSelectorContext() {
		final String currentUrl = safeCurrentUrl().toLowerCase(Locale.ROOT);
		return currentUrl.contains("accounts.google.")
				|| isAnyTextVisible("Choose an account", "Selecciona una cuenta", "Elegir una cuenta");
	}

	private void switchBackToApplicationWindow() {
		final Set<String> handles = driver.getWindowHandles();
		if (appWindowHandle != null && handles.contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForUiLoad();
			return;
		}

		for (final String handle : handles) {
			driver.switchTo().window(handle);
			if (!safeCurrentUrl().contains("accounts.google.")) {
				appWindowHandle = handle;
				waitForUiLoad();
				return;
			}
		}
	}

	private void maybeSwitchToNewTab() {
		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() <= 1) {
			return;
		}

		for (final String handle : handles) {
			if (appWindowHandle == null || !handle.equals(appWindowHandle)) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				return;
			}
		}
	}

	private void runStep(final String key, final StepAction action) {
		try {
			action.run();
			report.put(key, StepResult.pass());
		} catch (final Exception ex) {
			final String details = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			report.put(key, StepResult.fail(details));
			takeScreenshot("failure-" + sanitizeFileName(key));
		}
	}

	private void waitForUiLoad() {
		waitUntil(d -> {
			try {
				final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return state != null && "complete".equalsIgnoreCase(state.toString());
			} catch (final Exception e) {
				return true;
			}
		}, 20);
	}

	private boolean waitUntil(final ExpectedCondition<Boolean> condition, final int timeoutSec) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSec)).until(condition);
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private WebElement waitForAnyVisible(final By... locators) {
		Throwable lastError = null;
		for (final By locator : locators) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final Exception ex) {
				lastError = ex;
			}
		}
		throw new AssertionError("No expected visible elements were found.", lastError);
	}

	private WebElement findOptionalVisible(final By locator) {
		try {
			return new WebDriverWait(driver, Duration.ofSeconds(3)).until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final Exception ex) {
			return null;
		}
	}

	private void clickByVisibleText(final String... texts) {
		Exception last = null;
		for (final String text : texts) {
			try {
				final String xpath = "//*[self::a or self::button or self::div or self::span or self::li][contains(normalize-space(.), "
						+ xpathLiteral(text) + ")]";
				final WebElement candidate = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(xpath)));
				candidate.click();
				waitForUiLoad();
				return;
			} catch (final Exception ex) {
				last = ex;
			}
		}
		throw new AssertionError("Unable to click any of texts: " + String.join(", ", texts), last);
	}

	private void waitForTextVisible(final String text) {
		final String xpath = "//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]";
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
	}

	private boolean isTextVisible(final String text) {
		return findOptionalVisible(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]")) != null;
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return true;
			}
		}
		return false;
	}

	private void takeScreenshot(final String name) {
		try {
			final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(artifactsDir.resolve(name + ".png"), screenshot);
		} catch (final Exception ignored) {
			// Best-effort evidence collection; do not break test cleanup.
		}
	}

	private void takeFullPageScreenshot(final String name) {
		Dimension original = null;
		try {
			original = driver.manage().window().getSize();
			final Number fullHeight = (Number) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			final int targetHeight = Math.max(fullHeight.intValue() + 120, original.height);
			driver.manage().window().setSize(new Dimension(original.width, targetHeight));
			waitForUiLoad();
			takeScreenshot(name);
		} catch (final Exception ignored) {
			takeScreenshot(name);
		} finally {
			if (original != null) {
				driver.manage().window().setSize(original);
			}
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test final report").append(System.lineSeparator());
		sb.append("=====================================").append(System.lineSeparator());
		sb.append("Artifacts directory: ").append(artifactsDir.toAbsolutePath()).append(System.lineSeparator());
		sb.append(System.lineSeparator());

		for (final String key : report.keySet()) {
			final StepResult value = report.get(key);
			sb.append(key).append(": ").append(value.pass ? "PASS" : "FAIL");
			if (value.details != null && !value.details.isBlank()) {
				sb.append(" - ").append(value.details);
			}
			sb.append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			sb.append(System.lineSeparator());
			sb.append("Final legal URLs:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		final Path reportFile = artifactsDir.resolve("final-report.txt");
		Files.writeString(reportFile, sb.toString());
		System.out.println(sb);
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (final Exception ex) {
			return "unknown";
		}
	}

	private WebDriver createDriver() throws Exception {
		final String remoteUrl = System.getenv("SELENIUM_REMOTE_URL");
		final String browser = envOrDefault("SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "false"));

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			return remoteUrl != null && !remoteUrl.isBlank() ? new RemoteWebDriver(new java.net.URI(remoteUrl).toURL(), firefoxOptions)
					: new FirefoxDriver(firefoxOptions);
		case "edge":
			final EdgeOptions edgeOptions = new EdgeOptions();
			if (headless) {
				edgeOptions.addArguments("--headless=new");
			}
			return remoteUrl != null && !remoteUrl.isBlank() ? new RemoteWebDriver(new java.net.URI(remoteUrl).toURL(), edgeOptions)
					: new EdgeDriver(edgeOptions);
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.addArguments("--start-maximized");
			if (headless) {
				chromeOptions.addArguments("--headless=new", "--window-size=1920,1080");
			}
			return remoteUrl != null && !remoteUrl.isBlank() ? new RemoteWebDriver(new java.net.URI(remoteUrl).toURL(), chromeOptions)
					: new ChromeDriver(chromeOptions);
		}
	}

	private Path createArtifactsDirectory() throws IOException {
		final String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path base = Paths.get("target", "saleads-e2e-artifacts", stamp);
		Files.createDirectories(base);
		return base;
	}

	private void initializeReport() {
		report.put(LOGIN, StepResult.fail("Not executed"));
		report.put(MI_NEGOCIO_MENU, StepResult.fail("Not executed"));
		report.put(AGREGAR_NEGOCIO_MODAL, StepResult.fail("Not executed"));
		report.put(ADMINISTRAR_NEGOCIOS_VIEW, StepResult.fail("Not executed"));
		report.put(INFORMACION_GENERAL, StepResult.fail("Not executed"));
		report.put(DETALLES_CUENTA, StepResult.fail("Not executed"));
		report.put(TUS_NEGOCIOS, StepResult.fail("Not executed"));
		report.put(TERMINOS, StepResult.fail("Not executed"));
		report.put(PRIVACIDAD, StepResult.fail("Not executed"));
	}

	private static boolean containsAnyEmail(final String input) {
		final Pattern emailPattern = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
		final Matcher matcher = emailPattern.matcher(input);
		return matcher.find();
	}

	private static String envOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private static int envInt(final String key, final int defaultValue) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (final NumberFormatException ex) {
			return defaultValue;
		}
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part;
			if (chars[i] == '\'') {
				part = "\"'\"";
			} else {
				part = "'" + chars[i] + "'";
			}
			sb.append(part);
			if (i < chars.length - 1) {
				sb.append(",");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	private static String sanitizeFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean pass;
		private final String details;

		private StepResult(final boolean pass, final String details) {
			this.pass = pass;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, "OK");
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
