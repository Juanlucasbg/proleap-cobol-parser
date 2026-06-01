package io.proleap.cobol.e2e;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for SaleADS Mi Negocio workflow.
 *
 * <p>
 * Runtime inputs:
 * </p>
 * <ul>
 * <li>SALEADS_LOGIN_URL: login page URL for the target environment (required)</li>
 * <li>SALEADS_HEADLESS: true/false (optional, default true)</li>
 * <li>SALEADS_WAIT_SECONDS: explicit wait timeout in seconds (optional, default 30)</li>
 * <li>SALEADS_PAGE_LOAD_TIMEOUT_SECONDS: page-load timeout in seconds (optional, default
 * 60)</li>
 * <li>SELENIUM_REMOTE_URL: Selenium Grid URL (optional; local ChromeDriver is used when empty)</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Informaci\u00f3n General", "Detalles de la Cuenta", "Tus Negocios",
			"T\u00e9rminos y Condiciones", "Pol\u00edtica de Privacidad");

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private String termsUrl = "";
	private String privacyUrl = "";

	@Before
	public void setUp() throws IOException, MalformedURLException {
		evidenceDir = createEvidenceDir();
		driver = createDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(readIntEnv("SALEADS_WAIT_SECONDS", 30)));
		driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(readIntEnv("SALEADS_PAGE_LOAD_TIMEOUT_SECONDS", 60)));

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the login page of your current SaleADS environment.",
				loginUrl != null && !loginUrl.trim().isEmpty());

		driver.get(loginUrl.trim());
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		prepareReportDefaults();

		runStep("Login", this::stepLoginWithGoogle);
		runStepIfPreviousPassed("Mi Negocio menu", "Login", this::stepOpenMiNegocioMenu);
		runStepIfPreviousPassed("Agregar Negocio modal", "Mi Negocio menu", this::stepValidateAgregarNegocioModal);
		runStepIfPreviousPassed("Administrar Negocios view", "Mi Negocio menu", this::stepOpenAdministrarNegocios);
		runStepIfPreviousPassed("Informaci\u00f3n General", "Administrar Negocios view", this::stepValidateInformacionGeneral);
		runStepIfPreviousPassed("Detalles de la Cuenta", "Administrar Negocios view",
				this::stepValidateDetallesCuenta);
		runStepIfPreviousPassed("Tus Negocios", "Administrar Negocios view", this::stepValidateTusNegocios);
		runStepIfPreviousPassed("T\u00e9rminos y Condiciones", "Administrar Negocios view", this::stepValidateTerminos);
		runStepIfPreviousPassed("Pol\u00edtica de Privacidad", "Administrar Negocios view",
				this::stepValidatePoliticaPrivacidad);

		writeFinalReport();
		Assert.assertTrue("One or more SaleADS validations failed:\n" + buildSummary(), allStepsPassed());
	}

	private void stepLoginWithGoogle() throws IOException {
		clickAnyVisibleText(true, "Sign in with Google", "Iniciar sesion con Google", "Ingresar con Google",
				"Continuar con Google", "Google");
		clickAnyVisibleText(false, ACCOUNT_EMAIL);

		waitForAnyVisibleText(Duration.ofSeconds(90), "Mi Negocio", "Negocio", "Dashboard", "Panel");

		final boolean sidebarVisible = isVisible(By.xpath("//aside"))
				|| isVisible(By.xpath("//nav[contains(@class, 'sidebar')]"))
				|| isVisible(By.xpath("//nav"));
		Assert.assertTrue("Main application interface/sidebar did not appear after login.", sidebarVisible);
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickAnyVisibleText(false, "Negocio");
		clickAnyVisibleText(true, "Mi Negocio");

		waitForAnyVisibleText(Duration.ofSeconds(30), "Agregar Negocio");
		waitForAnyVisibleText(Duration.ofSeconds(30), "Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickAnyVisibleText(true, "Agregar Negocio");
		waitForAnyVisibleText(Duration.ofSeconds(20), "Crear Nuevo Negocio");
		waitForAnyVisibleText(Duration.ofSeconds(20), "Nombre del Negocio");
		waitForAnyVisibleText(Duration.ofSeconds(20), "Tienes 2 de 3 negocios");
		waitForAnyVisibleText(Duration.ofSeconds(20), "Cancelar");
		waitForAnyVisibleText(Duration.ofSeconds(20), "Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final WebElement input = waitForVisible(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')]"),
				Duration.ofSeconds(10));
		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatizacion");

		clickAnyVisibleText(true, "Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickAnyVisibleText(true, "Mi Negocio");
		}

		clickAnyVisibleText(true, "Administrar Negocios");
		waitForAnyVisibleText(Duration.ofSeconds(30), "Informacion General", "Informaci\u00f3n General");
		waitForAnyVisibleText(Duration.ofSeconds(30), "Detalles de la Cuenta");
		waitForAnyVisibleText(Duration.ofSeconds(30), "Tus Negocios");
		waitForAnyVisibleText(Duration.ofSeconds(30), "Seccion Legal", "Secci\u00f3n Legal");
		captureScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		waitForAnyVisibleText(Duration.ofSeconds(20), "Informacion General", "Informaci\u00f3n General");
		waitForAnyVisibleText(Duration.ofSeconds(20), "BUSINESS PLAN");
		waitForAnyVisibleText(Duration.ofSeconds(20), "Cambiar Plan");

		final String bodyText = normalizedPageText();
		Assert.assertTrue("User email is not visible in Informaci\u00f3n General.",
				EMAIL_PATTERN.matcher(bodyText).find());
		Assert.assertTrue("User name was not detected near account data.", findLikelyUserName(bodyText).isPresent());
	}

	private void stepValidateDetallesCuenta() {
		waitForAnyVisibleText(Duration.ofSeconds(20), "Cuenta creada");
		waitForAnyVisibleText(Duration.ofSeconds(20), "Estado activo");
		waitForAnyVisibleText(Duration.ofSeconds(20), "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitForAnyVisibleText(Duration.ofSeconds(20), "Tus Negocios");
		waitForAnyVisibleText(Duration.ofSeconds(20), "Agregar Negocio");
		waitForAnyVisibleText(Duration.ofSeconds(20), "Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminos() throws IOException {
		termsUrl = openLegalDocument(Arrays.asList("Terminos y Condiciones", "T\u00e9rminos y Condiciones"),
				Arrays.asList("Terminos y Condiciones", "T\u00e9rminos y Condiciones"), "05-terminos-condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		privacyUrl = openLegalDocument(Arrays.asList("Politica de Privacidad", "Pol\u00edtica de Privacidad"),
				Arrays.asList("Politica de Privacidad", "Pol\u00edtica de Privacidad"), "06-politica-privacidad");
	}

	private String openLegalDocument(final List<String> linkTexts, final List<String> headingTexts, final String screenshotName)
			throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickAnyVisibleText(true, linkTexts.toArray(new String[0]));
		final boolean openedNewTab = switchToNewTab(beforeHandles, Duration.ofSeconds(12));

		waitForAnyVisibleText(Duration.ofSeconds(20), headingTexts.toArray(new String[0]));
		final String legalBody = normalizedPageText();
		Assert.assertTrue("Legal content is not visible.", legalBody.length() > 120);
		captureScreenshot(screenshotName);

		final String currentUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return currentUrl;
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, Boolean.TRUE);
		} catch (final Throwable t) {
			stepResults.put(stepName, Boolean.FALSE);
			stepDetails.put(stepName, summarizeError(t));
			try {
				captureScreenshot("failed-" + slug(stepName));
			} catch (final IOException ignored) {
				// ignore secondary screenshot errors on failure path
			}
		}
	}

	private void runStepIfPreviousPassed(final String stepName, final String prerequisiteStep, final StepAction action) {
		if (Boolean.TRUE.equals(stepResults.get(prerequisiteStep))) {
			runStep(stepName, action);
		} else {
			stepResults.put(stepName, Boolean.FALSE);
			stepDetails.put(stepName, "Blocked: prerequisite failed (" + prerequisiteStep + ").");
		}
	}

	private void clickAnyVisibleText(final boolean required, final String... texts) {
		for (final String text : texts) {
			final Optional<WebElement> element = findVisibleTextElement(text, Duration.ofSeconds(4));
			if (element.isPresent()) {
				clickElement(element.get());
				waitForUiToLoad();
				return;
			}
		}

		if (required) {
			throw new AssertionError("Could not find clickable element with visible text: " + Arrays.toString(texts));
		}
	}

	private Optional<WebElement> findVisibleTextElement(final String text, final Duration timeout) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			final List<WebElement> candidates = new ArrayList<>();
			candidates.addAll(driver.findElements(By.xpath("//*[normalize-space(.)=" + asXpathLiteral(text) + "]")));
			candidates.addAll(driver.findElements(
					By.xpath("//*[contains(normalize-space(.), " + asXpathLiteral(text) + ")]")));

			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					return Optional.of(candidate);
				}
			}

			sleep(250);
		}

		return Optional.empty();
	}

	private void clickElement(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		wait.until(ExpectedConditions.elementToBeClickable(element));

		try {
			element.click();
		} catch (final Exception e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void waitForAnyVisibleText(final Duration timeout, final String... texts) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String text : texts) {
				if (isTextVisible(text)) {
					return;
				}
			}
			sleep(250);
		}

		throw new AssertionError("Did not find expected visible text within timeout: " + Arrays.toString(texts));
	}

	private boolean isTextVisible(final String text) {
		final List<WebElement> exact = driver
				.findElements(By.xpath("//*[normalize-space(.)=" + asXpathLiteral(text) + "]"));
		for (final WebElement element : exact) {
			if (element.isDisplayed()) {
				return true;
			}
		}

		final List<WebElement> contains = driver
				.findElements(By.xpath("//*[contains(normalize-space(.), " + asXpathLiteral(text) + ")]"));
		for (final WebElement element : contains) {
			if (element.isDisplayed()) {
				return true;
			}
		}

		return false;
	}

	private boolean isVisible(final By by) {
		try {
			return waitForVisible(by, Duration.ofSeconds(3)).isDisplayed();
		} catch (final RuntimeException e) {
			return false;
		}
	}

	private WebElement waitForVisible(final By by, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private void waitForUiToLoad() {
		wait.until((ExpectedCondition<Boolean>) webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		sleep(350);
	}

	private boolean switchToNewTab(final Set<String> previousHandles, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout)
					.until(webDriver -> webDriver.getWindowHandles().size() > previousHandles.size());
		} catch (final TimeoutException e) {
			return false;
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!previousHandles.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return true;
			}
		}

		return false;
	}

	private void captureScreenshot(final String name) throws IOException {
		final Path screenshotPath = evidenceDir.resolve(name + ".png");
		Files.copy(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath(), screenshotPath);
	}

	private Path createEvidenceDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private WebDriver createDriver() throws MalformedURLException {
		final boolean headless = Boolean.parseBoolean(readEnv("SALEADS_HEADLESS", "true"));
		final String remoteUrl = System.getenv("SELENIUM_REMOTE_URL");

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		if (remoteUrl != null && !remoteUrl.trim().isEmpty()) {
			final MutableCapabilities capabilities = options;
			return new RemoteWebDriver(new URL(remoteUrl.trim()), capabilities);
		}

		return new ChromeDriver(options);
	}

	private int readIntEnv(final String key, final int defaultValue) {
		final String raw = System.getenv(key);
		if (raw == null || raw.trim().isEmpty()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (final NumberFormatException e) {
			return defaultValue;
		}
	}

	private String readEnv(final String key, final String defaultValue) {
		final String raw = System.getenv(key);
		return raw == null || raw.trim().isEmpty() ? defaultValue : raw.trim();
	}

	private Optional<String> findLikelyUserName(final String pageText) {
		final String normalized = normalizeAccents(pageText);
		final String[] lines = normalized.split("\\R");

		for (int i = 0; i < lines.length; i++) {
			if (!EMAIL_PATTERN.matcher(lines[i]).find()) {
				continue;
			}

			for (int j = Math.max(0, i - 4); j < i; j++) {
				final String candidate = lines[j].trim();
				if (isLikelyName(candidate)) {
					return Optional.of(candidate);
				}
			}
		}

		for (final String line : lines) {
			final String candidate = line.trim();
			if (isLikelyName(candidate)) {
				return Optional.of(candidate);
			}
		}

		return Optional.empty();
	}

	private boolean isLikelyName(final String candidate) {
		if (candidate.isEmpty()) {
			return false;
		}

		final String lowered = normalizeAccents(candidate).toLowerCase(Locale.ROOT);
		if (lowered.contains("@")) {
			return false;
		}
		if (lowered.contains("business plan") || lowered.contains("cambiar plan")
				|| lowered.contains("informacion general") || lowered.contains("detalles de la cuenta")
				|| lowered.contains("tus negocios") || lowered.contains("seccion legal")
				|| lowered.contains("terminos y condiciones") || lowered.contains("politica de privacidad")
				|| lowered.contains("agregar negocio") || lowered.contains("administrar negocios")
				|| lowered.contains("mi negocio")) {
			return false;
		}

		return lowered.matches(".*[a-z].*") && candidate.length() >= 3;
	}

	private String normalizedPageText() {
		final String rawText = driver.findElement(By.tagName("body")).getText();
		return normalizeAccents(rawText);
	}

	private String normalizeAccents(final String value) {
		final String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
		return normalized.replaceAll("\\p{M}", "");
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				builder.append(", ");
			}
			builder.append(chars[i] == '\'' ? "\"'\"" : "'" + chars[i] + "'");
		}
		builder.append(")");
		return builder.toString();
	}

	private String summarizeError(final Throwable t) {
		final String message = t.getMessage() == null ? "No error message provided." : t.getMessage();
		return t.getClass().getSimpleName() + ": " + message;
	}

	private void prepareReportDefaults() {
		for (final String field : REPORT_FIELDS) {
			stepResults.put(field, Boolean.FALSE);
		}
	}

	private boolean allStepsPassed() {
		for (final String step : REPORT_FIELDS) {
			if (!Boolean.TRUE.equals(stepResults.get(step))) {
				return false;
			}
		}
		return true;
	}

	private String buildSummary() {
		final StringBuilder summary = new StringBuilder();
		for (final String step : REPORT_FIELDS) {
			summary.append("- ").append(step).append(": ")
					.append(Boolean.TRUE.equals(stepResults.get(step)) ? "PASS" : "FAIL");
			if (stepDetails.containsKey(step)) {
				summary.append(" (").append(stepDetails.get(step)).append(")");
			}
			summary.append(System.lineSeparator());
		}
		if (!termsUrl.isEmpty()) {
			summary.append("- T\u00e9rminos y Condiciones URL: ").append(termsUrl).append(System.lineSeparator());
		}
		if (!privacyUrl.isEmpty()) {
			summary.append("- Pol\u00edtica de Privacidad URL: ").append(privacyUrl).append(System.lineSeparator());
		}
		return summary.toString();
	}

	private void writeFinalReport() throws IOException {
		final Path reportPath = evidenceDir.resolve("final-report.txt");
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		report.append(buildSummary());
		Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Sleep interrupted.", e);
		}
	}

	private String slug(final String value) {
		return normalizeAccents(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-|-$)", "");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
