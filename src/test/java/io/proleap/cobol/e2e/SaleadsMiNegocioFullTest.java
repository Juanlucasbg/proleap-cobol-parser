package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

import org.junit.After;
import org.junit.Assume;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Environment-agnostic Selenium E2E that validates SaleADS "Mi Negocio" flow.
 *
 * <p>
 * Runtime contract:
 * <ul>
 * <li>SALEADS_LOGIN_URL: required; login page URL for the current environment.</li>
 * <li>SALEADS_HEADLESS: optional; defaults to true.</li>
 * <li>SALEADS_WAIT_SECONDS: optional; defaults to 30.</li>
 * <li>SALEADS_CHROME_BINARY: optional; custom Chrome binary path.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final String TARGET_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepNotes = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Duration timeout;
	private Path evidenceDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to run SaleADS browser automation.", loginUrl != null && !loginUrl.isBlank());

		timeout = Duration.ofSeconds(parseLongOrDefault(getenv("SALEADS_WAIT_SECONDS"), 30L));
		wait = null;

		evidenceDir = Path.of("target", "saleads-evidence", TIMESTAMP.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(getenvOrDefault("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-gpu");

		final String chromeBinary = getenv("SALEADS_CHROME_BINARY");
		if (chromeBinary != null && !chromeBinary.isBlank()) {
			options.setBinary(chromeBinary.trim());
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, timeout);

		driver.get(loginUrl.trim());
		waitForUiLoad();
	}

	@After
	public void tearDown() {
		printFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final boolean allPassed = stepResults.values().stream().allMatch(Boolean.TRUE::equals);
		assertTrue("Some SaleADS workflow validations failed. Review report and screenshots under " + evidenceDir, allPassed);
	}

	private void stepLoginWithGoogle() throws Exception {
		final Set<String> handlesBeforeLoginClick = new LinkedHashSet<>(driver.getWindowHandles());
		clickFirstVisibleTextOrThrow(Arrays.asList(
				"Sign in with Google",
				"Login with Google",
				"Iniciar sesión con Google",
				"Continuar con Google"));

		switchToNewestWindowIfAny(handlesBeforeLoginClick);
		selectGoogleAccountIfPrompted();
		switchBackToAppWindowWhenPopupCloses();

		waitForAnyVisibleTextOrThrow(Arrays.asList("Negocio", "Mi Negocio"));
		waitForSidebarOrThrow();
		appWindowHandle = driver.getWindowHandle();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		ensureTextVisible("Negocio");
		clickFirstVisibleTextOrThrow(Arrays.asList("Mi Negocio", "Negocio"));
		ensureTextVisible("Agregar Negocio");
		ensureTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickFirstVisibleTextOrThrow(Arrays.asList("Agregar Negocio"));
		ensureTextVisible("Crear Nuevo Negocio");
		ensureTextVisible("Nombre del Negocio");
		ensureTextVisible("Tienes 2 de 3 negocios");
		ensureTextVisible("Cancelar");
		ensureTextVisible("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		typeIntoFieldByNearbyLabel("Nombre del Negocio", "Negocio Prueba Automatización");
		clickFirstVisibleTextOrThrow(Arrays.asList("Cancelar"));
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios", 3)) {
			clickFirstVisibleTextOrThrow(Arrays.asList("Mi Negocio"));
		}
		clickFirstVisibleTextOrThrow(Arrays.asList("Administrar Negocios"));
		waitForUiLoad();

		ensureTextVisible("Información General");
		ensureTextVisible("Detalles de la Cuenta");
		ensureTextVisible("Tus Negocios");
		ensureTextVisible("Sección Legal");
		takeFullPageScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		ensureTextVisible("Información General");
		ensureTextVisible("BUSINESS PLAN");
		ensureTextVisible("Cambiar Plan");
		ensureEmailVisibleOrThrow();
		ensureUserNameLikeValueVisibleOrThrow();
	}

	private void stepValidateDetallesCuenta() throws Exception {
		ensureTextVisible("Cuenta creada");
		ensureTextVisible("Estado activo");
		ensureTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() throws Exception {
		ensureTextVisible("Tus Negocios");
		ensureTextVisible("Agregar Negocio");
		ensureTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosCondiciones() throws Exception {
		validateLegalLink(
				"Términos y Condiciones",
				"Términos y Condiciones",
				"05-terminos-condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		validateLegalLink(
				"Política de Privacidad",
				"Política de Privacidad",
				"06-politica-privacidad");
	}

	private void validateLegalLink(final String linkText, final String expectedHeading, final String screenshotName) throws Exception {
		ensureTextVisible("Sección Legal");

		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String sourceHandle = driver.getWindowHandle();

		clickFirstVisibleTextOrThrow(Arrays.asList(linkText));
		switchToNewestWindowIfAny(handlesBefore);
		waitForUiLoad();

		ensureTextVisible(expectedHeading);
		ensureLegalBodyVisibleOrThrow(expectedHeading);
		capturedUrls.put(expectedHeading, driver.getCurrentUrl());
		takeScreenshot(screenshotName);

		returnToApplication(sourceHandle, handlesBefore);
	}

	private void returnToApplication(final String sourceHandle, final Set<String> handlesBeforeClick) {
		final Set<String> handlesAfter = driver.getWindowHandles();
		final boolean openedNewTab = handlesAfter.size() > handlesBeforeClick.size();

		if (openedNewTab) {
			final String current = driver.getWindowHandle();
			if (!current.equals(sourceHandle)) {
				driver.close();
			}
			driver.switchTo().window(sourceHandle);
			waitForUiLoad();
			return;
		}

		driver.switchTo().window(sourceHandle);
		driver.navigate().back();
		waitForUiLoad();
	}

	private void runStep(final String reportField, final ThrowingStep step) {
		try {
			step.run();
			stepResults.put(reportField, Boolean.TRUE);
			stepNotes.put(reportField, "PASS");
		} catch (final Exception ex) {
			stepResults.put(reportField, Boolean.FALSE);
			stepNotes.put(reportField, "FAIL - " + ex.getClass().getSimpleName() + ": " + safeMessage(ex));
			saveFailureScreenshot(reportField);
		}
	}

	private void saveFailureScreenshot(final String reportField) {
		if (driver == null) {
			return;
		}
		try {
			takeScreenshot("failure-" + reportField);
		} catch (final Exception ignored) {
			// Avoid masking the original assertion failure.
		}
	}

	private void waitForSidebarOrThrow() {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside | //nav")));
		} catch (final TimeoutException e) {
			throw new IllegalStateException("Left sidebar navigation is not visible.", e);
		}
	}

	private void ensureTextVisible(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]")));
	}

	private void waitForAnyVisibleTextOrThrow(final List<String> candidates) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		shortWait.until(d -> {
			for (final String candidate : candidates) {
				if (isTextVisible(candidate, 1)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isTextVisible(final String text, final int seconds) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(seconds));
			shortWait.until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]")));
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private void clickFirstVisibleTextOrThrow(final List<String> textOptions) {
		for (final String text : textOptions) {
			final List<WebElement> matches = driver.findElements(By.xpath(
					"//button[contains(normalize-space(.), " + toXPathLiteral(text) + ")]"
							+ " | //a[contains(normalize-space(.), " + toXPathLiteral(text) + ")]"
							+ " | //*[@role='button' and contains(normalize-space(.), " + toXPathLiteral(text) + ")]"
							+ " | //span[contains(normalize-space(.), " + toXPathLiteral(text) + ")]"
							+ " | //div[contains(normalize-space(.), " + toXPathLiteral(text) + ")]"));
			for (final WebElement match : matches) {
				if (match.isDisplayed()) {
					wait.until(ExpectedConditions.elementToBeClickable(match)).click();
					waitForUiLoad();
					return;
				}
			}
		}

		throw new NoSuchElementException("Could not find clickable element for any of: " + textOptions);
	}

	private void typeIntoFieldByNearbyLabel(final String labelText, final String value) {
		final List<By> candidates = new ArrayList<>();
		candidates.add(By.xpath("//label[contains(normalize-space(.), " + toXPathLiteral(labelText)
				+ ")]/following::input[1]"));
		candidates.add(By.xpath("//input[@placeholder and contains(@placeholder, " + toXPathLiteral(labelText) + ")]"));
		candidates.add(By.xpath("//input[@aria-label and contains(@aria-label, " + toXPathLiteral(labelText) + ")]"));

		for (final By locator : candidates) {
			final List<WebElement> fields = driver.findElements(locator);
			for (final WebElement field : fields) {
				if (field.isDisplayed()) {
					field.click();
					field.clear();
					field.sendKeys(value);
					waitForUiLoad();
					return;
				}
			}
		}

		throw new NoSuchElementException("Input field for label not found: " + labelText);
	}

	private void selectGoogleAccountIfPrompted() {
		if (!isTextVisible(TARGET_GOOGLE_ACCOUNT, 8)) {
			return;
		}
		clickFirstVisibleTextOrThrow(Arrays.asList(TARGET_GOOGLE_ACCOUNT));
		waitForUiLoad();
	}

	private void switchToNewestWindowIfAny(final Set<String> handlesBefore) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
		try {
			shortWait.until(d -> d.getWindowHandles().size() >= handlesBefore.size());
		} catch (final TimeoutException ignored) {
			return;
		}

		final Set<String> handlesAfter = driver.getWindowHandles();
		for (final String handle : handlesAfter) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				return;
			}
		}
	}

	private void switchBackToAppWindowWhenPopupCloses() {
		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForUiLoad();
		}
	}

	private void ensureEmailVisibleOrThrow() {
		final List<WebElement> emails = driver.findElements(By.xpath("//*[contains(normalize-space(.), '@')]"));
		for (final WebElement email : emails) {
			if (email.isDisplayed()) {
				return;
			}
		}
		throw new IllegalStateException("Expected user email to be visible.");
	}

	private void ensureUserNameLikeValueVisibleOrThrow() {
		final List<WebElement> candidates = driver.findElements(By.xpath(
				"//*[string-length(normalize-space(.)) > 2 and not(contains(normalize-space(.), '@'))"
						+ " and not(contains(normalize-space(.), 'BUSINESS PLAN'))"
						+ " and not(contains(normalize-space(.), 'Cambiar Plan'))"
						+ " and not(contains(normalize-space(.), 'Información General'))]"));
		for (final WebElement candidate : candidates) {
			final String value = candidate.getText().trim();
			if (candidate.isDisplayed() && value.matches(".*[A-Za-z].*")) {
				return;
			}
		}
		throw new IllegalStateException("Expected user name to be visible.");
	}

	private void ensureLegalBodyVisibleOrThrow(final String heading) {
		final List<WebElement> bodyCandidates = driver.findElements(By.xpath(
				"//p[string-length(normalize-space(.)) > 40]"
						+ " | //div[string-length(normalize-space(.)) > 80]"
						+ " | //li[string-length(normalize-space(.)) > 20]"));
		for (final WebElement body : bodyCandidates) {
			if (!body.isDisplayed()) {
				continue;
			}
			final String text = body.getText().trim();
			if (!text.isBlank() && !text.equalsIgnoreCase(heading)) {
				return;
			}
		}
		throw new IllegalStateException("Expected legal body content to be visible for: " + heading);
	}

	private void waitForUiLoad() {
		wait.until(d -> "complete".equals(String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"))));
		try {
			Thread.sleep(500L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for UI load.", interruptedException);
		}
	}

	private void takeFullPageScreenshot(final String checkpoint) throws IOException {
		if (driver instanceof JavascriptExecutor) {
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			final int width = ((Number) js.executeScript(
					"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, 1920);")).intValue();
			final int height = ((Number) js.executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, 1080);")).intValue();
			driver.manage().window().setSize(new Dimension(Math.min(width, 1920), Math.min(height, 4000)));
			waitForUiLoad();
		}
		takeScreenshot(checkpoint);
	}

	private Path takeScreenshot(final String checkpoint) throws IOException {
		final String sanitized = checkpoint.replaceAll("[^a-zA-Z0-9\\-_]+", "_").toLowerCase(Locale.ROOT);
		final Path output = evidenceDir.resolve(sanitized + ".png");
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), output, StandardCopyOption.REPLACE_EXISTING);
		return output;
	}

	private void printFinalReport() {
		if (stepResults.isEmpty()) {
			return;
		}

		System.out.println();
		System.out.println("==== SaleADS Mi Negocio Workflow Report ====");
		for (final String field : Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad")) {
			final Boolean pass = stepResults.get(field);
			final String status = Boolean.TRUE.equals(pass) ? "PASS" : "FAIL";
			final String detail = stepNotes.getOrDefault(field, "NOT EXECUTED");
			System.out.println(field + ": " + status + " (" + detail + ")");
		}

		if (!capturedUrls.isEmpty()) {
			System.out.println("-- Captured Legal URLs --");
			for (final Map.Entry<String, String> entry : capturedUrls.entrySet()) {
				System.out.println(entry.getKey() + ": " + entry.getValue());
			}
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("============================================");
		System.out.println();
	}

	private String safeMessage(final Exception ex) {
		final String message = ex.getMessage();
		if (message == null || message.isBlank()) {
			return "no message";
		}
		return message.replace("\n", " ").trim();
	}

	private String getenv(final String key) {
		return System.getenv(key);
	}

	private String getenvOrDefault(final String key, final String defaultValue) {
		return System.getenv().getOrDefault(key, defaultValue);
	}

	private long parseLongOrDefault(final String value, final long defaultValue) {
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (final NumberFormatException e) {
			return defaultValue;
		}
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String fragment;
			if (chars[i] == '\'') {
				fragment = "\"'\"";
			} else if (chars[i] == '"') {
				fragment = "'\"'";
			} else {
				fragment = "'" + chars[i] + "'";
			}
			result.append(fragment);
			if (i < chars.length - 1) {
				result.append(",");
			}
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface ThrowingStep {
		void run() throws Exception;
	}
}
