package io.proleap.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";
	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = getBooleanSetting("RUN_SALEADS_E2E", false);
		Assume.assumeTrue(
				"Skipping SaleADS E2E test. Set RUN_SALEADS_E2E=true to execute browser automation.",
				enabled);

		final ChromeOptions options = new ChromeOptions();
		if (getBooleanSetting("SALEADS_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--lang=es-ES");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));

		final String timestamp = TIMESTAMP_FORMAT.format(LocalDateTime.now());
		artifactsDir = Paths.get("target", "saleads-e2e-artifacts", TEST_NAME + "-" + timestamp);
		Files.createDirectories(artifactsDir);
	}

	@After
	public void tearDown() {
		writeReportSafely();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		openLoginPageIfProvided();
		appWindowHandle = driver.getWindowHandle();

		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Informaci\u00f3n General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("T\u00e9rminos y Condiciones", () -> stepValidateLegalLink("T\u00e9rminos y Condiciones", "T\u00e9rminos y Condiciones",
				"08-terminos-y-condiciones"));
		runStep("Pol\u00edtica de Privacidad", () -> stepValidateLegalLink("Pol\u00edtica de Privacidad", "Pol\u00edtica de Privacidad",
				"09-politica-de-privacidad"));

		writeReportSafely();
		assertAllStepsPassed();
	}

	private void stepLoginWithGoogle() throws IOException {
		clickByAnyVisibleText(
				"Sign in with Google",
				"Iniciar sesi\u00f3n con Google",
				"Iniciar sesion con Google",
				"Login with Google",
				"Google");

		selectGoogleAccountIfVisible(ACCOUNT_EMAIL);

		waitForUiToLoad();
		assertTrue("Main application interface did not appear after login.", hasMainApplicationShell());
		assertTrue("Left sidebar navigation is not visible.", hasSidebar());
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		waitForUiToLoad();
		assertTrue("Sidebar must be visible before opening Mi Negocio menu.", hasSidebar());

		if (!isTextVisible("Mi Negocio")) {
			clickByVisibleText("Negocio");
		}
		clickByVisibleText("Mi Negocio");

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		assertElementVisible(By.xpath("//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio' or @name='businessName']"),
				"Input field 'Nombre del Negocio' is not present.");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final WebElement input = findOptional(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio' or @name='businessName']"));
		if (input != null) {
			input.clear();
			input.sendKeys(TEST_BUSINESS_NAME);
			waitForUiToLoad();
		}

		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}
		clickByVisibleText("Administrar Negocios");

		assertTextVisible("Informaci\u00f3n General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Secci\u00f3n Legal");
		takeScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("Informaci\u00f3n General");
		final String bodyText = getBodyText();
		assertTrue("Expected user email is not visible in Informacion General section.",
				EMAIL_PATTERN.matcher(bodyText).find());
		assertTrue("Expected BUSINESS PLAN text not found.", containsTextIgnoreAccents(bodyText, "BUSINESS PLAN"));
		assertTextVisible("Cambiar Plan");

		final String expectedNameToken = getSetting("SALEADS_EXPECTED_NAME_TOKEN", "juan");
		assertTrue("Expected user name token '" + expectedNameToken + "' is not visible.",
				containsTextIgnoreAccents(bodyText, expectedNameToken));
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");

		final List<WebElement> businessRows = driver.findElements(By.xpath(
				"//*[contains(normalize-space(),'Tus Negocios')]/following::*[self::tr or self::li or @role='row' or contains(@class,'card')][normalize-space()]"));
		assertTrue("Business list is not visible in Tus Negocios section.", !businessRows.isEmpty());
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotPrefix)
			throws IOException {
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String initialHandle = driver.getWindowHandle();
		final String initialUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		final String targetHandle = waitForNewWindowOrStay(handlesBefore, initialHandle);
		driver.switchTo().window(targetHandle);
		waitForUiToLoad();

		assertTextVisible(expectedHeading);
		assertTrue("Legal page content text is not visible for link: " + linkText, hasEnoughLegalText());
		takeScreenshot(screenshotPrefix);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (!targetHandle.equals(appWindowHandle)) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
			return;
		}

		if (!driver.getCurrentUrl().equals(initialUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void openLoginPageIfProvided() {
		final String loginUrl = getSetting("SALEADS_LOGIN_URL", "");
		if (!loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	private boolean hasMainApplicationShell() {
		final List<WebElement> candidates = driver.findElements(By.xpath("//aside | //nav | //main"));
		return candidates.stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean hasSidebar() {
		final List<WebElement> sidebars = driver.findElements(By.xpath("//aside | //nav"));
		return sidebars.stream().anyMatch(WebElement::isDisplayed);
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final Set<String> handles = new LinkedHashSet<>(driver.getWindowHandles());
		for (final String handle : handles) {
			driver.switchTo().window(handle);
			waitForUiToLoad();
			final WebElement accountTile = findOptional(By.xpath("//*[contains(normalize-space(), " + xpathLiteral(email) + ")]"));
			if (accountTile != null && accountTile.isDisplayed()) {
				accountTile.click();
				waitForUiToLoad();
				break;
			}
		}

		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private String waitForNewWindowOrStay(final Set<String> handlesBefore, final String fallbackHandle) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15))
					.until(driverRef -> driverRef.getWindowHandles().size() > handlesBefore.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					return handle;
				}
			}
		} catch (final TimeoutException ignored) {
			// Link opened in the same tab/window.
		}
		return fallbackHandle;
	}

	private void runStep(final String stepName, final StepAction action) {
		final StepResult stepResult = new StepResult();
		try {
			action.run();
			stepResult.status = "PASS";
			stepResult.details = "Validation completed successfully.";
		} catch (final Exception ex) {
			stepResult.status = "FAIL";
			stepResult.details = ex.getMessage();
			takeScreenshotSafely("fail-" + normalizeForFileName(stepName));
		}
		report.put(stepName, stepResult);
	}

	private void assertAllStepsPassed() {
		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!"PASS".equals(entry.getValue().status)) {
				failed.add(entry.getKey() + ": " + entry.getValue().details);
			}
		}

		if (!failed.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failed.\n" + String.join("\n", failed));
		}
	}

	private void clickByAnyVisibleText(final String... texts) {
		Exception lastError = null;
		for (final String text : texts) {
			try {
				clickByVisibleText(text);
				return;
			} catch (final Exception ex) {
				lastError = ex;
			}
		}
		throw new IllegalStateException("Could not click any element by visible text candidates.", lastError);
	}

	private void clickByVisibleText(final String text) {
		final String literal = xpathLiteral(text);
		final By locator = By.xpath(
				"//button[normalize-space()=" + literal + "] | " +
						"//a[normalize-space()=" + literal + "] | " +
						"//*[@role='button' and normalize-space()=" + literal + "] | " +
						"//*[normalize-space()=" + literal + "]");
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
		element.click();
		waitForUiToLoad();
	}

	private void assertTextVisible(final String text) {
		assertElementVisible(By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]"),
				"Expected text is not visible: " + text);
	}

	private void assertElementVisible(final By locator, final String failureMessage) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final TimeoutException ex) {
			throw new IllegalStateException(failureMessage, ex);
		}
	}

	private boolean isTextVisible(final String text) {
		final List<WebElement> matches = driver
				.findElements(By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]"));
		return matches.stream().anyMatch(WebElement::isDisplayed);
	}

	private WebElement findOptional(final By locator) {
		final List<WebElement> matches = driver.findElements(locator);
		for (final WebElement match : matches) {
			if (match.isDisplayed()) {
				return match;
			}
		}
		return null;
	}

	private void waitForUiToLoad() {
		wait.until(driverRef -> "complete".equals(((JavascriptExecutor) driverRef).executeScript("return document.readyState")));
		waitForInvisibility(By.cssSelector("[aria-busy='true']"));
		waitForInvisibility(By.cssSelector(".loading, .loader, .spinner, .ant-spin-spinning"));
	}

	private void waitForInvisibility(final By locator) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.invisibilityOfElementLocated(locator));
		} catch (final TimeoutException ignored) {
			// Some pages have no loading indicators, or they are not relevant for this flow.
		}
	}

	private boolean hasEnoughLegalText() {
		final String pageText = getBodyText().trim();
		return pageText.length() > 120;
	}

	private String getBodyText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private void takeScreenshot(final String name) throws IOException {
		final Path output = artifactsDir.resolve(name + ".png");
		final byte[] pngData = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(output, pngData);
	}

	private void takeScreenshotSafely(final String name) {
		try {
			if (driver != null) {
				takeScreenshot(name);
			}
		} catch (final Exception ignored) {
			// Ignore screenshot errors to keep report generation resilient.
		}
	}

	private void writeReportSafely() {
		if (artifactsDir == null) {
			return;
		}

		final StringBuilder sb = new StringBuilder();
		sb.append("test: ").append(TEST_NAME).append('\n');
		sb.append("generated_at: ").append(LocalDateTime.now()).append('\n');
		sb.append("artifacts_dir: ").append(artifactsDir.toAbsolutePath()).append('\n');
		sb.append('\n');
		sb.append("results:\n");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().status);
			if (entry.getValue().details != null) {
				sb.append(" (").append(entry.getValue().details).append(')');
			}
			sb.append('\n');
		}

		if (!legalUrls.isEmpty()) {
			sb.append('\n');
			sb.append("legal_urls:\n");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		try {
			Files.createDirectories(artifactsDir);
			Files.writeString(artifactsDir.resolve("final-report.txt"), sb.toString());
		} catch (final IOException ignored) {
			// Ignore report write failures during cleanup to avoid masking test result.
		}
	}

	private boolean getBooleanSetting(final String key, final boolean defaultValue) {
		final String value = getSetting(key, String.valueOf(defaultValue));
		return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
	}

	private String getSetting(final String key, final String defaultValue) {
		final String env = System.getenv(key);
		if (env != null && !env.isBlank()) {
			return env.trim();
		}
		final String property = System.getProperty(key);
		if (property != null && !property.isBlank()) {
			return property.trim();
		}
		return defaultValue;
	}

	private boolean containsTextIgnoreAccents(final String source, final String expected) {
		final String normalizedSource = normalizeText(source);
		final String normalizedExpected = normalizeText(expected);
		return normalizedSource.contains(normalizedExpected);
	}

	private String normalizeText(final String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT);
	}

	private String normalizeForFileName(final String text) {
		return normalizeText(text).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			if (chars[i] == '\'') {
				sb.append("\"'\"");
			} else if (chars[i] == '"') {
				sb.append("'\"'");
			} else {
				sb.append('\'').append(chars[i]).append('\'');
			}
		}
		sb.append(')');
		return sb.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepResult {
		private String status;
		private String details;
	}
}
