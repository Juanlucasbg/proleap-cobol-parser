package io.proleap.e2e.saleads;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.openqa.selenium.Dimension;
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
 * Full E2E validation for SaleADS "Mi Negocio" workflow.
 *
 * <p>This test is intentionally disabled by default and only runs when
 * SALEADS_E2E_ENABLED=true is provided in the environment.
 */
public class SaleadsMiNegocioFullTest {

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private int screenshotCounter;

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue("Skipping SaleADS E2E: set SALEADS_E2E_ENABLED=true to run.",
				envBoolean("SALEADS_E2E_ENABLED", false));

		evidenceDir = Paths
				.get(env("SALEADS_EVIDENCE_DIR", "target/saleads-e2e-evidence"), LocalDateTime.now().format(FILE_TS_FORMAT));
		Files.createDirectories(evidenceDir);

		driver = createDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(envInt("SALEADS_TIMEOUT_SECONDS", 30)));
		driver.manage().window().setSize(new Dimension(1440, 1000));
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);
		driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(envInt("SALEADS_PAGELOAD_TIMEOUT_SECONDS", 60)));

		final String loginUrl = env("SALEADS_LOGIN_URL", "");
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
			waitForUiToLoad();
		} else {
			final String currentUrl = safeCurrentUrl();
			final boolean hasUsableStartPage = currentUrl != null && !currentUrl.isEmpty() && !"about:blank".equals(currentUrl)
					&& !"data:,".equals(currentUrl);

			if (!hasUsableStartPage) {
				throw new IllegalStateException("No login URL available. Provide SALEADS_LOGIN_URL for environment-agnostic execution.");
			}
		}
	}

	@After
	public void tearDown() throws Exception {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		boolean previousStepPassed = true;

		previousStepPassed = runStep("Login", previousStepPassed, this::loginWithGoogle);
		previousStepPassed = runStep("Mi Negocio menu", previousStepPassed, this::openMiNegocioMenu);
		previousStepPassed = runStep("Agregar Negocio modal", previousStepPassed, this::validateAgregarNegocioModal);
		previousStepPassed = runStep("Administrar Negocios view", previousStepPassed, this::openAdministrarNegocios);
		previousStepPassed = runStep("Información General", previousStepPassed, this::validateInformacionGeneral);
		previousStepPassed = runStep("Detalles de la Cuenta", previousStepPassed, this::validateDetallesCuenta);
		previousStepPassed = runStep("Tus Negocios", previousStepPassed, this::validateTusNegocios);
		previousStepPassed = runStep("Términos y Condiciones", previousStepPassed,
				() -> validateLegalDocument("Términos y Condiciones"));
		previousStepPassed = runStep("Política de Privacidad", previousStepPassed,
				() -> validateLegalDocument("Política de Privacidad"));

		if (!allStepsPassed()) {
			Assert.fail("SaleADS Mi Negocio workflow has one or more failed validations. See final report in " + evidenceDir);
		}
	}

	private String loginWithGoogle() throws Exception {
		if (!isTextVisible("Mi Negocio", 3) && !isTextVisible("Negocio", 3)) {
			clickByVisibleTextVariants(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
					"Ingresar con Google", "Google", "Iniciar sesión"));
			selectGoogleAccountIfPrompted(env("SALEADS_GOOGLE_ACCOUNT", "juanlucasbarbiergarzon@gmail.com"));
		}

		waitForAnyVisibleText(Arrays.asList("Mi Negocio", "Negocio", "Dashboard", "Inicio"), 45);
		assertLeftSidebarVisible();
		takeScreenshot("dashboard_loaded");
		return "Dashboard loaded and left sidebar visible.";
	}

	private String openMiNegocioMenu() throws Exception {
		if (isTextVisible("Negocio", 5)) {
			clickByVisibleTextIfPresent("Negocio");
		}
		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		waitForVisibleText("Agregar Negocio", 20);
		waitForVisibleText("Administrar Negocios", 20);
		takeScreenshot("mi_negocio_expanded");
		return "Mi Negocio submenu expanded with expected options.";
	}

	private String validateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		waitForVisibleText("Crear Nuevo Negocio", 20);
		waitForVisibleText("Nombre del Negocio", 20);
		waitForVisibleText("Tienes 2 de 3 negocios", 20);
		waitForVisibleText("Cancelar", 20);
		waitForVisibleText("Crear Negocio", 20);
		findBusinessNameInput();

		takeScreenshot("agregar_negocio_modal");

		final WebElement input = findBusinessNameInput();
		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatización");
		waitForUiToLoad();

		clickByVisibleText("Cancelar");
		waitForTextToDisappear("Crear Nuevo Negocio", 15);

		return "Crear Nuevo Negocio modal validated and dismissed using Cancelar.";
	}

	private String openAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios", 3)) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		waitForVisibleText("Información General", 30);
		waitForVisibleText("Detalles de la Cuenta", 30);
		waitForVisibleText("Tus Negocios", 30);
		waitForVisibleText("Sección Legal", 30);
		takeFullPageScreenshot("administrar_negocios_page_full");

		return "Administrar Negocios loaded with all required sections.";
	}

	private String validateInformacionGeneral() throws Exception {
		final WebElement section = findSectionByHeading("Información General");
		final String sectionText = normalizeSpaces(section.getText());

		Assert.assertTrue("User email is not visible in Información General.", EMAIL_PATTERN.matcher(sectionText).find());
		Assert.assertTrue("User name is not visible in Información General.", hasProbableUserName(sectionText));
		waitForVisibleText("BUSINESS PLAN", 15);
		waitForVisibleText("Cambiar Plan", 15);

		return "Información General includes name, email, plan and Cambiar Plan.";
	}

	private String validateDetallesCuenta() throws Exception {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		final String sectionText = normalizeSpaces(section.getText());

		Assert.assertTrue("'Cuenta creada' is not visible.", sectionText.contains("Cuenta creada"));
		Assert.assertTrue("'Estado activo' is not visible.", sectionText.contains("Estado activo"));
		Assert.assertTrue("'Idioma seleccionado' is not visible.", sectionText.contains("Idioma seleccionado"));

		return "Detalles de la Cuenta includes expected account metadata.";
	}

	private String validateTusNegocios() throws Exception {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final String sectionText = normalizeSpaces(section.getText());

		waitForVisibleText("Agregar Negocio", 15);
		Assert.assertTrue("'Tienes 2 de 3 negocios' is not visible in Tus Negocios.",
				sectionText.contains("Tienes 2 de 3 negocios"));
		Assert.assertTrue("Business list is not visible in Tus Negocios.", sectionText.length() > "Tus Negocios".length() + 20);

		return "Tus Negocios list and usage counter validated.";
	}

	private String validateLegalDocument(final String legalLinkText) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String appUrlBefore = safeCurrentUrl();

		clickByVisibleText(legalLinkText);
		waitForUiToLoad();

		final String switchedHandle = trySwitchToNewWindow(beforeHandles, 10);
		final boolean openedNewTab = switchedHandle != null;

		waitForVisibleText(legalLinkText, 30);
		final String legalPageText = normalizeSpaces(driver.findElement(By.tagName("body")).getText());
		Assert.assertTrue("Legal content text is not visible for " + legalLinkText + ".", legalPageText.length() > 120);
		takeScreenshot("legal_" + legalLinkText.toLowerCase(Locale.ROOT).replace(" ", "_"));

		final String finalUrl = safeCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else if (appUrlBefore != null && !appUrlBefore.equals(finalUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return "Validated at URL: " + finalUrl;
	}

	private WebDriver createDriver() throws Exception {
		final String browser = env("SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		final String remoteUrl = env("SALEADS_WEBDRIVER_URL", "");
		final boolean headless = envBoolean("SALEADS_HEADLESS", true);

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			return remoteUrl.isEmpty() ? new FirefoxDriver(firefoxOptions)
					: new RemoteWebDriver(new URL(remoteUrl), firefoxOptions);
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.addArguments("--disable-dev-shm-usage", "--no-sandbox", "--window-size=1440,1000");
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			return remoteUrl.isEmpty() ? new ChromeDriver(chromeOptions)
					: new RemoteWebDriver(new URL(remoteUrl), chromeOptions);
		}
	}

	private boolean runStep(final String field, final boolean precondition, final StepAction action) {
		if (!precondition) {
			stepResults.put(field, StepResult.fail("Not executed because a previous step failed."));
			return false;
		}

		try {
			final String detail = action.run();
			stepResults.put(field, StepResult.pass(detail));
			return true;
		} catch (final Throwable ex) {
			try {
				takeScreenshot("failure_" + field.toLowerCase(Locale.ROOT).replace(" ", "_"));
			} catch (final Exception screenshotEx) {
				// best effort only
			}
			stepResults.put(field, StepResult.fail(ex.getClass().getSimpleName() + ": " + ex.getMessage()));
			return false;
		}
	}

	private boolean allStepsPassed() {
		for (final String field : REPORT_FIELDS) {
			final StepResult result = stepResults.get(field);
			if (result == null || !result.passed) {
				return false;
			}
		}
		return true;
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio Full Test - Final Report").append(System.lineSeparator());
		report.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		report.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		report.append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			final StepResult result = stepResults.getOrDefault(field, StepResult.fail("Step did not execute."));
			report.append(field).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (result.detail != null && !result.detail.isEmpty()) {
				report.append(" - ").append(result.detail);
			}
			report.append(System.lineSeparator());
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, report.toString());
		System.out.println(report);
	}

	private void assertLeftSidebarVisible() {
		final List<By> sidebarLocators = Arrays.asList(By.xpath("//aside"), By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar')]"));

		for (final By locator : sidebarLocators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}

		Assert.fail("Left sidebar navigation is not visible.");
	}

	private void selectGoogleAccountIfPrompted(final String email) throws Exception {
		final Set<String> handles = driver.getWindowHandles();
		final String currentHandle = driver.getWindowHandle();

		if (handles.size() > 1) {
			for (final String handle : handles) {
				if (!handle.equals(currentHandle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		if (isTextVisible(email, 12)) {
			clickByVisibleText(email);
		}

		if (handles.size() > 1) {
			for (int i = 0; i < 20; i++) {
				Thread.sleep(500L);
				if (driver.getWindowHandles().size() == 1) {
					break;
				}
			}
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
			}
		}

		waitForUiToLoad();
	}

	private String trySwitchToNewWindow(final Set<String> oldHandles, final int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(driverRef -> driverRef.getWindowHandles().size() > oldHandles.size());

			for (final String handle : driver.getWindowHandles()) {
				if (!oldHandles.contains(handle)) {
					driver.switchTo().window(handle);
					waitForUiToLoad();
					return handle;
				}
			}
		} catch (final TimeoutException ex) {
			return null;
		}
		return null;
	}

	private void clickByVisibleText(final String text) throws Exception {
		if (clickByVisibleTextIfPresent(text)) {
			return;
		}
		throw new IllegalStateException("Could not click visible text: " + text);
	}

	private void clickByVisibleTextVariants(final List<String> variants) throws Exception {
		for (final String variant : variants) {
			if (clickByVisibleTextIfPresent(variant)) {
				return;
			}
		}
		throw new IllegalStateException("Could not click any visible text variant: " + variants);
	}

	private boolean clickByVisibleTextIfPresent(final String text) throws Exception {
		final List<By> locators = Arrays.asList(
				By.xpath("//*[self::button or self::a or @role='button'][normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath(
						"//*[self::button or self::a or @role='button'][contains(normalize-space(), " + xpathLiteral(text) + ")]"),
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]"));

		for (final By locator : locators) {
			final List<WebElement> matches = driver.findElements(locator);
			for (final WebElement match : matches) {
				if (!match.isDisplayed()) {
					continue;
				}
				final WebElement clickable = toClickable(match);
				scrollIntoView(clickable);
				wait.until(ExpectedConditions.elementToBeClickable(clickable));
				clickWithJsFallback(clickable);
				waitForUiToLoad();
				return true;
			}
		}

		return false;
	}

	private WebElement toClickable(final WebElement element) {
		final List<WebElement> candidates = element
				.findElements(By.xpath("./ancestor-or-self::*[self::button or self::a or @role='button'][1]"));
		return candidates.isEmpty() ? element : candidates.get(0);
	}

	private void clickWithJsFallback(final WebElement element) {
		try {
			element.click();
		} catch (final Exception ex) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private WebElement findBusinessNameInput() {
		final List<By> locators = Arrays.asList(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"));

		for (final By locator : locators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		throw new IllegalStateException("Input 'Nombre del Negocio' was not found.");
	}

	private WebElement findSectionByHeading(final String headingText) {
		final WebElement heading = waitForVisibleElement(
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::div or self::span][normalize-space()="
						+ xpathLiteral(headingText) + "]"),
				30);

		final List<WebElement> containers = heading.findElements(By.xpath("./ancestor::*[self::section or self::article or self::div]"));
		for (final WebElement container : containers) {
			if (container.isDisplayed()) {
				final String text = normalizeSpaces(container.getText());
				if (text.contains(headingText)) {
					return container;
				}
			}
		}

		return heading;
	}

	private void waitForVisibleText(final String text, final int timeoutSeconds) {
		new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(driverRef -> isTextVisibleNow(text));
	}

	private void waitForAnyVisibleText(final List<String> texts, final int timeoutSeconds) {
		new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(driverRef -> {
			for (final String text : texts) {
				if (isTextVisibleNow(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isTextVisible(final String text, final int timeoutSeconds) {
		try {
			waitForVisibleText(text, timeoutSeconds);
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private boolean isTextVisibleNow(final String text) {
		final By exact = By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]");
		final By contains = By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]");

		return hasDisplayedElement(exact) || hasDisplayedElement(contains);
	}

	private boolean hasDisplayedElement(final By locator) {
		for (final WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void waitForTextToDisappear(final String text, final int timeoutSeconds) {
		final By locator = By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]");
		new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
				.until(ExpectedConditions.invisibilityOfElementLocated(locator));
	}

	private void waitForUiToLoad() {
		wait.until(driverRef -> "complete".equals(((JavascriptExecutor) driverRef).executeScript("return document.readyState")));
		try {
			Thread.sleep(envInt("SALEADS_UI_SETTLE_MS", 700));
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private WebElement waitForVisibleElement(final By locator, final int timeoutSeconds) {
		return new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
				.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String normalizedName = checkpointName.replaceAll("[^A-Za-z0-9_-]", "_");
		final String fileName = String.format("%02d_%s.png", ++screenshotCounter, normalizedName);
		Files.copy(source.toPath(), evidenceDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private void takeFullPageScreenshot(final String checkpointName) throws IOException {
		final Dimension original = driver.manage().window().getSize();
		try {
			final Long pageHeight = (Long) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			if (pageHeight != null && pageHeight > original.getHeight()) {
				final int boundedHeight = Math.min(pageHeight.intValue() + 120, 8000);
				driver.manage().window().setSize(new Dimension(original.getWidth(), boundedHeight));
				waitForUiToLoad();
			}
			takeScreenshot(checkpointName);
		} finally {
			driver.manage().window().setSize(original);
			waitForUiToLoad();
		}
	}

	private boolean hasProbableUserName(final String sectionText) {
		final List<String> excludedTokens = Arrays.asList("INFORMACIÓN GENERAL", "BUSINESS PLAN", "CAMBIAR PLAN",
				"CUENTA CREADA", "ESTADO ACTIVO", "IDIOMA SELECCIONADO");

		final String[] lines = sectionText.split("\\R");
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.contains("@")) {
				continue;
			}

			final String upper = trimmed.toUpperCase(Locale.ROOT);
			if (excludedTokens.contains(upper)) {
				continue;
			}

			if (trimmed.matches(".*\\p{L}.*") && trimmed.length() >= 3) {
				return true;
			}
		}
		return false;
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (final Exception ex) {
			return null;
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value.trim();
	}

	private boolean envBoolean(final String key, final boolean defaultValue) {
		final String value = env(key, String.valueOf(defaultValue));
		return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
	}

	private int envInt(final String key, final int defaultValue) {
		final String value = env(key, String.valueOf(defaultValue));
		try {
			return Integer.parseInt(value);
		} catch (final NumberFormatException ex) {
			return defaultValue;
		}
	}

	private String normalizeSpaces(final String value) {
		return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final List<String> parts = new ArrayList<>();
		for (final String part : value.split("'")) {
			parts.add("'" + part + "'");
		}
		return "concat(" + String.join(", \"'\", ", parts) + ")";
	}

	@FunctionalInterface
	private interface StepAction {
		String run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail == null ? "" : detail;
		}

		private static StepResult pass(final String detail) {
			return new StepResult(true, detail == null ? "Validation passed." : detail);
		}

		private static StepResult fail(final String detail) {
			return new StepResult(false, detail == null ? "Validation failed." : detail);
		}
	}
}
