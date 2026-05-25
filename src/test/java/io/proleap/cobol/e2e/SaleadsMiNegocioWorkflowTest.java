package io.proleap.cobol.e2e;

import static org.junit.Assert.fail;

import java.io.IOException;
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
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu",
			"Agregar Negocio modal", "Administrar Negocios view", "Información General",
			"Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones", "Política de Privacidad");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, List<String>> failures = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		initReportMaps();
		screenshotDir = Files.createDirectories(Paths.get("target", "saleads-screenshots", FILE_TS.format(LocalDateTime.now())));
		driver = buildDriver();

		final long timeoutSeconds = Long.parseLong(getConfig("SALEADS_TIMEOUT_SECONDS", "saleads.timeout.seconds", "40"));
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);

		final String loginUrl = getConfig("SALEADS_LOGIN_URL", "saleads.login.url", "").trim();
		if (loginUrl.isEmpty()) {
			fail("Missing login URL. Configure SALEADS_LOGIN_URL or -Dsaleads.login.url=<login_page>.");
		}

		driver.get(loginUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		printReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::loginWithGoogleAndValidateSidebar);
		runStep("Mi Negocio menu", this::openMiNegocioMenuAndValidate);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegociosAndValidateView);
		runStep("Información General", this::validateInformacionGeneralSection);
		runStep("Detalles de la Cuenta", this::validateDetallesDeLaCuentaSection);
		runStep("Tus Negocios", this::validateTusNegociosSection);
		runStep("Términos y Condiciones", () -> validateLegalPageAndReturn("Términos y Condiciones", "terms_and_conditions"));
		runStep("Política de Privacidad", () -> validateLegalPageAndReturn("Política de Privacidad", "privacy_policy"));

		final List<String> failedFields = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.toList();
		if (!failedFields.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failed for: " + failedFields + ". Details: " + failures);
		}
	}

	private void loginWithGoogleAndValidateSidebar() {
		clickFirstVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Login with Google", "Ingresar con Google", "Google"));
		waitForUiToLoad();
		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");

		waitForVisible(By.xpath("//aside | //nav"));
		verifyVisibleText("Login", "Negocio");
		takeScreenshot("01_dashboard_loaded");
	}

	private void openMiNegocioMenuAndValidate() {
		verifyVisibleText("Mi Negocio menu", "Negocio");
		clickFirstVisibleText(Arrays.asList("Mi Negocio"));
		waitForUiToLoad();

		verifyVisibleText("Mi Negocio menu", "Agregar Negocio");
		verifyVisibleText("Mi Negocio menu", "Administrar Negocios");
		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void validateAgregarNegocioModal() {
		clickFirstVisibleText(Arrays.asList("Agregar Negocio"));
		waitForUiToLoad();

		verifyVisibleText("Agregar Negocio modal", "Crear Nuevo Negocio");
		verifyPresent("Agregar Negocio modal", By.xpath(
				"//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder,'Nombre del Negocio')]"),
				"Input 'Nombre del Negocio' exists");
		verifyVisibleText("Agregar Negocio modal", "Tienes 2 de 3 negocios");
		verifyVisibleText("Agregar Negocio modal", "Cancelar");
		verifyVisibleText("Agregar Negocio modal", "Crear Negocio");
		takeScreenshot("03_agregar_negocio_modal");

		typeIfVisible(By.xpath(
				"//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder,'Nombre del Negocio')]"),
				"Negocio Prueba Automatización");
		clickIfVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void openAdministrarNegociosAndValidateView() {
		ensureMiNegocioExpanded();
		clickFirstVisibleText(Arrays.asList("Administrar Negocios"));
		waitForUiToLoad();

		verifyVisibleText("Administrar Negocios view", "Información General");
		verifyVisibleText("Administrar Negocios view", "Detalles de la Cuenta");
		verifyVisibleText("Administrar Negocios view", "Tus Negocios");
		verifyVisibleText("Administrar Negocios view", "Sección Legal");
		takeScreenshot("04_administrar_negocios_page");
	}

	private void validateInformacionGeneralSection() {
		verifyTextByRegex("Información General", EMAIL_PATTERN, "User email is visible");
		verifyAnyVisibleText("Información General", Arrays.asList("BUSINESS PLAN", "Business Plan", "PLAN DE NEGOCIO"),
				"Plan text is visible");
		verifyVisibleText("Información General", "Cambiar Plan");
		verifyAnyVisibleText("Información General",
				Arrays.asList("Nombre", "Name", "Usuario", "Perfil", "Cuenta", "Información General"),
				"User name context is visible");
	}

	private void validateDetallesDeLaCuentaSection() {
		verifyAnyVisibleText("Detalles de la Cuenta", Arrays.asList("Cuenta creada", "Creada"), "'Cuenta creada' is visible");
		verifyAnyVisibleText("Detalles de la Cuenta", Arrays.asList("Estado activo", "Activo"), "'Estado activo' is visible");
		verifyAnyVisibleText("Detalles de la Cuenta", Arrays.asList("Idioma seleccionado", "Idioma"),
				"'Idioma seleccionado' is visible");
	}

	private void validateTusNegociosSection() {
		verifyVisibleText("Tus Negocios", "Tus Negocios");
		verifyVisibleText("Tus Negocios", "Agregar Negocio");
		verifyVisibleText("Tus Negocios", "Tienes 2 de 3 negocios");
		verifyPresent("Tus Negocios", By.xpath("//ul/li | //table/tbody/tr | //div[contains(@class,'card')]"),
				"Business list is visible");
	}

	private void validateLegalPageAndReturn(final String linkText, final String screenshotNamePrefix) {
		ensureOnApplicationTab();
		final String currentHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickFirstVisibleText(Arrays.asList(linkText));
		waitForUiToLoad();

		final String resolvedHandle = wait.until(browser -> resolveWindowAfterClick(browser, handlesBeforeClick, currentHandle));
		driver.switchTo().window(resolvedHandle);
		waitForUiToLoad();

		verifyVisibleText(linkText, linkText);
		verifyPresent(linkText, By.xpath("//p[string-length(normalize-space()) > 40]"), "Legal content text is visible");

		takeScreenshot("05_" + screenshotNamePrefix);
		legalUrls.put(linkText, driver.getCurrentUrl());
		System.out.println("Final URL [" + linkText + "]: " + driver.getCurrentUrl());

		if (!resolvedHandle.equals(currentHandle)) {
			driver.close();
			driver.switchTo().window(currentHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();
	}

	private String resolveWindowAfterClick(final WebDriver browser, final Set<String> handlesBeforeClick,
			final String currentHandle) {
		final Set<String> handlesNow = browser.getWindowHandles();
		if (handlesNow.size() > handlesBeforeClick.size()) {
			for (final String handle : handlesNow) {
				if (!handlesBeforeClick.contains(handle)) {
					return handle;
				}
			}
		}

		if (containsVisibleText("Términos y Condiciones") || containsVisibleText("Política de Privacidad")) {
			return currentHandle;
		}
		return null;
	}

	private void ensureMiNegocioExpanded() {
		if (!containsVisibleText("Administrar Negocios")) {
			clickIfVisibleText("Mi Negocio");
			waitForUiToLoad();
		}
	}

	private void ensureOnApplicationTab() {
		if (!driver.getWindowHandle().equals(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private void selectGoogleAccountIfVisible(final String email) {
		try {
			final WebElement account = new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(browser -> firstDisplayed(By.xpath(
							"//*[contains(normalize-space(), " + asXpathLiteral(email) + ")] | //div[contains(@data-email, "
									+ asXpathLiteral(email) + ")]")));
			if (account != null) {
				account.click();
				waitForUiToLoad();
			}
		} catch (TimeoutException ignored) {
			// Account selector does not always appear (existing Google session can redirect directly).
		}
	}

	private void clickIfVisibleText(final String text) {
		final WebElement element = firstDisplayed(byVisibleText(text));
		if (element != null) {
			element.click();
		}
	}

	private void clickFirstVisibleText(final List<String> candidateTexts) {
		for (final String text : candidateTexts) {
			final WebElement element = waitForFirstDisplayed(byVisibleText(text));
			if (element != null) {
				element.click();
				waitForUiToLoad();
				return;
			}
		}
		throw new NoSuchElementException("None of the expected texts were clickable: " + candidateTexts);
	}

	private void typeIfVisible(final By locator, final String text) {
		final WebElement input = firstDisplayed(locator);
		if (input != null) {
			input.clear();
			input.sendKeys(text);
		}
	}

	private void verifyVisibleText(final String reportField, final String text) {
		if (!containsVisibleText(text)) {
			markFailure(reportField, "Text not visible: '" + text + "'");
		}
	}

	private void verifyAnyVisibleText(final String reportField, final List<String> texts, final String context) {
		for (final String text : texts) {
			if (containsVisibleText(text)) {
				return;
			}
		}
		markFailure(reportField, "Expected one of " + texts + " (" + context + ")");
	}

	private void verifyPresent(final String reportField, final By locator, final String context) {
		if (firstDisplayed(locator) == null) {
			markFailure(reportField, "Element not visible for: " + context);
		}
	}

	private void verifyTextByRegex(final String reportField, final Pattern pattern, final String context) {
		final String text = driver.findElement(By.tagName("body")).getText();
		if (!pattern.matcher(text).find()) {
			markFailure(reportField, context);
		}
	}

	private boolean containsVisibleText(final String text) {
		return firstDisplayed(byVisibleText(text)) != null;
	}

	private By byVisibleText(final String text) {
		final String value = asXpathLiteral(text);
		return By.xpath(
				"//*[normalize-space(text())=" + value + "] | //*[contains(normalize-space(.), " + value + ")]");
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(browser -> firstDisplayed(locator));
	}

	private WebElement waitForFirstDisplayed(final By locator) {
		try {
			return waitForVisible(locator);
		} catch (TimeoutException exception) {
			return null;
		}
	}

	private WebElement firstDisplayed(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		return null;
	}

	private void waitForUiToLoad() {
		final ExpectedCondition<Boolean> documentReady = browser -> {
			if (!(browser instanceof JavascriptExecutor)) {
				return true;
			}
			final Object state = ((JavascriptExecutor) browser).executeScript("return document.readyState");
			return "complete".equals(state);
		};
		wait.until(documentReady);
	}

	private void takeScreenshot(final String checkpointName) {
		try {
			final Path destination = screenshotDir.resolve(FILE_TS.format(LocalDateTime.now()) + "_" + sanitize(checkpointName) + ".png");
			final byte[] image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(destination, image);
			System.out.println("Screenshot saved: " + destination.toAbsolutePath());
		} catch (IOException ioException) {
			System.err.println("Could not write screenshot: " + ioException.getMessage());
		}
	}

	private void markFailure(final String reportField, final String message) {
		report.put(reportField, false);
		failures.computeIfAbsent(reportField, key -> new ArrayList<>()).add(message);
		System.err.println("Validation failure [" + reportField + "]: " + message);
	}

	private void runStep(final String reportField, final Runnable step) {
		try {
			step.run();
		} catch (Exception exception) {
			markFailure(reportField, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
		}
	}

	private void initReportMaps() {
		for (final String field : REPORT_FIELDS) {
			report.put(field, true);
			failures.put(field, new ArrayList<>());
		}
	}

	private void printReport() {
		System.out.println("=== SaleADS Mi Negocio Workflow Report ===");
		for (final String field : REPORT_FIELDS) {
			final boolean passed = report.getOrDefault(field, false);
			System.out.println("- " + field + ": " + (passed ? "PASS" : "FAIL"));
			final List<String> fieldErrors = failures.get(field);
			if (fieldErrors != null && !fieldErrors.isEmpty()) {
				for (final String error : fieldErrors) {
					System.out.println("  * " + error);
				}
			}
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("=== Final Legal URLs ===");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println("- " + entry.getKey() + ": " + entry.getValue());
			}
		}
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
	}

	private WebDriver buildDriver() {
		final String browser = getConfig("SALEADS_BROWSER", "saleads.browser", "chrome").trim().toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(getConfig("SALEADS_HEADLESS", "saleads.headless", "true"));

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			firefoxOptions.addArguments("--width=1920");
			firefoxOptions.addArguments("--height=1080");
			return new FirefoxDriver(firefoxOptions);
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			chromeOptions.addArguments("--window-size=1920,1080");
			chromeOptions.addArguments("--disable-dev-shm-usage");
			chromeOptions.addArguments("--no-sandbox");
			return new ChromeDriver(chromeOptions);
		}
	}

	private String getConfig(final String envKey, final String propertyKey, final String defaultValue) {
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return System.getProperty(propertyKey, defaultValue);
	}

	private String sanitize(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("_+", "_")
				.replaceAll("^_|_$", "");
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
			final String fragment;
			if (chars[i] == '\'') {
				fragment = "\"'\"";
			} else {
				fragment = "'" + chars[i] + "'";
			}
			builder.append(fragment);
			if (i < chars.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}
}
