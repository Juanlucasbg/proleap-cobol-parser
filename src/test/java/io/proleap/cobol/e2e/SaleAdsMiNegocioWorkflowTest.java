package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio".
 *
 * This test is environment-agnostic: provide any environment login URL through
 * SALEADS_LOGIN_URL or -Dsaleads.login.url.
 */
public class SaleAdsMiNegocioWorkflowTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(getLong("SALEADS_TIMEOUT_SECONDS", "saleads.timeout.seconds", 25L));
	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN = "Administrar Negocios view";
	private static final String STEP_INFO = "Información General";
	private static final String STEP_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Términos y Condiciones";
	private static final String STEP_PRIVACY = "Política de Privacidad";

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Path reportPath;

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		if (getBoolean("SALEADS_HEADLESS", "saleads.headless", true)) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence");
		reportPath = Paths.get("target", "saleads-mi-negocio-report.json");
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() {
		writeReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep(STEP_LOGIN, this::loginWithGoogleAndValidateMainInterface);
		runStep(STEP_MENU, this::openMiNegocioMenuAndValidateOptions);
		runStep(STEP_MODAL, this::validateAgregarNegocioModal);
		runStep(STEP_ADMIN, this::openAdministrarNegociosAndValidateSections);
		runStep(STEP_INFO, this::validateInformacionGeneral);
		runStep(STEP_DETAILS, this::validateDetallesDeLaCuenta);
		runStep(STEP_BUSINESSES, this::validateTusNegocios);
		runStep(STEP_TERMS, () -> {
			final String url = validateLegalPage("Términos y Condiciones", "Términos y Condiciones", "05-terminos-y-condiciones");
			legalUrls.put(STEP_TERMS, url);
		});
		runStep(STEP_PRIVACY, () -> {
			final String url = validateLegalPage("Política de Privacidad", "Política de Privacidad", "06-politica-privacidad");
			legalUrls.put(STEP_PRIVACY, url);
		});

		final List<String> failedSteps = stepResults.entrySet().stream().filter(e -> !e.getValue().passed)
				.map(e -> e.getKey() + " (" + e.getValue().details + ")").collect(Collectors.toList());
		Assert.assertTrue("One or more workflow validations failed: " + failedSteps + ". See " + reportPath, failedSteps.isEmpty());
	}

	private void loginWithGoogleAndValidateMainInterface() {
		final String loginUrl = getString("SALEADS_LOGIN_URL", "saleads.login.url", "");
		if (!loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiLoad();
		}

		final Set<String> handlesBeforeGoogleClick = driver.getWindowHandles();
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Inicia sesión con Google", "Continuar con Google", "Google");
		switchToNewestWindowIfNeeded(handlesBeforeGoogleClick);
		selectGoogleAccountIfVisible();
		switchBackToAppWindow(handlesBeforeGoogleClick);

		assertAnyVisible("Main application interface was not visible after login.", By.cssSelector("aside"), By.cssSelector("nav"),
				By.xpath("//*[contains(normalize-space(.), 'Negocio')]"));
		assertAnyVisible("Left sidebar navigation was not visible after login.", By.cssSelector("aside"),
				By.cssSelector("[role='navigation']"), By.xpath("//nav"));
		captureScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenuAndValidateOptions() {
		assertAnyVisible("Left sidebar navigation was not visible before opening Mi Negocio.", By.cssSelector("aside"),
				By.cssSelector("[role='navigation']"), By.xpath("//nav"));

		clickByVisibleText("Mi Negocio");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final WebElement businessNameInput = waitForFirstVisible("Nombre del Negocio input was not found in modal.",
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"));
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		assertNotVisibleText("Crear Nuevo Negocio");
	}

	private void openAdministrarNegociosAndValidateSections() {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(3))) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertAnyVisible("Sección Legal was not visible in account page.", By.xpath("//*[contains(normalize-space(.), 'Sección Legal')]"),
				By.xpath("//*[contains(normalize-space(.), 'Legal')]"));
		captureScreenshot("04-administrar-negocios-page");
	}

	private void validateInformacionGeneral() {
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
		assertVisibleEmail();
		assertProbableUserNameVisible();
	}

	private void validateDetallesDeLaCuenta() {
		assertVisibleText("Cuenta creada");
		assertAnyVisible("'Estado activo' was not visible.", By.xpath("//*[contains(normalize-space(.), 'Estado activo')]"),
				By.xpath("//*[contains(normalize-space(.), 'Estado') and contains(normalize-space(.), 'activo')]"));
		assertVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private String validateLegalPage(final String linkText, final String expectedHeading, final String screenshotName) {
		final String appWindow = driver.getWindowHandle();
		final String previousUrl = driver.getCurrentUrl();
		final Set<String> beforeClickHandles = driver.getWindowHandles();

		clickByVisibleText(linkText);

		String legalWindow = null;
		try {
			legalWindow = new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(d -> d.getWindowHandles().size() > beforeClickHandles.size() ? newestWindowHandle(beforeClickHandles) : null);
		} catch (final TimeoutException ignored) {
			// No new tab/window was opened; continue validating in the current tab.
		}

		if (legalWindow != null) {
			driver.switchTo().window(legalWindow);
			waitForUiLoad();
		}

		assertVisibleText(expectedHeading);
		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (legalWindow != null) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else if (!Objects.equals(previousUrl, finalUrl)) {
			driver.navigate().back();
			waitForUiLoad();
		}

		return finalUrl;
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, StepResult.pass());
		} catch (final Throwable throwable) {
			stepResults.put(stepName, StepResult.fail(throwable));
			captureScreenshot("error-" + slug(stepName));
		}
	}

	private void selectGoogleAccountIfVisible() {
		final String accountEmail = getString("SALEADS_GOOGLE_ACCOUNT_EMAIL", "saleads.google.account.email", DEFAULT_GOOGLE_ACCOUNT);
		if (isTextVisible(accountEmail, Duration.ofSeconds(10))) {
			clickByVisibleText(accountEmail);
			waitForUiLoad();
		}
	}

	private void switchToNewestWindowIfNeeded(final Set<String> previousHandles) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(d -> d.getWindowHandles().size() > previousHandles.size());
			final String newHandle = newestWindowHandle(previousHandles);
			if (newHandle != null) {
				driver.switchTo().window(newHandle);
				waitForUiLoad();
			}
		} catch (final TimeoutException ignored) {
			// Login flow remained in the same window.
		}
	}

	private void switchBackToAppWindow(final Set<String> originalHandles) {
		for (final String handle : originalHandles) {
			if (driver.getWindowHandles().contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				return;
			}
		}
	}

	private String newestWindowHandle(final Set<String> previousHandles) {
		for (final String handle : driver.getWindowHandles()) {
			if (!previousHandles.contains(handle)) {
				return handle;
			}
		}
		return null;
	}

	private void clickByVisibleText(final String... texts) {
		final List<String> candidates = Arrays.asList(texts);
		Throwable lastError = null;
		for (final String text : candidates) {
			final String literal = xpathLiteral(text);
			final List<By> locators = Arrays.asList(
					By.xpath("//*[self::button or self::a or @role='button'][normalize-space()=" + literal + "]"),
					By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(), " + literal + ")]"),
					By.xpath("//*[normalize-space()=" + literal + "]"),
					By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
			for (final By locator : locators) {
				try {
					final WebElement element = new WebDriverWait(driver, Duration.ofSeconds(8))
							.until(ExpectedConditions.visibilityOfElementLocated(locator));
					clickElement(element);
					waitForUiLoad();
					return;
				} catch (final Throwable ex) {
					lastError = ex;
				}
			}
		}
		throw new AssertionError("Could not click any element by visible text: " + candidates, lastError);
	}

	private void clickElement(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
			new Actions(driver).moveToElement(element).pause(Duration.ofMillis(150)).perform();
			element.click();
		} catch (final Throwable clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void assertVisibleText(final String text) {
		final String literal = xpathLiteral(text);
		waitForFirstVisible("Expected text was not visible: " + text, By.xpath("//*[normalize-space()=" + literal + "]"),
				By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
	}

	private void assertNotVisibleText(final String text) {
		final String literal = xpathLiteral(text);
		wait.until(d -> d.findElements(By.xpath("//*[contains(normalize-space(), " + literal + ")]")).stream()
				.noneMatch(WebElement::isDisplayed));
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		final String literal = xpathLiteral(text);
		try {
			new WebDriverWait(driver, timeout).until(d -> d.findElements(By.xpath("//*[contains(normalize-space(), " + literal + ")]")).stream()
					.anyMatch(WebElement::isDisplayed));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private WebElement waitForFirstVisible(final String errorMessage, final By... locators) {
		Throwable lastError = null;
		for (final By locator : locators) {
			try {
				return new WebDriverWait(driver, Duration.ofSeconds(10))
						.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final Throwable ex) {
				lastError = ex;
			}
		}
		throw new AssertionError(errorMessage, lastError);
	}

	private void assertAnyVisible(final String errorMessage, final By... locators) {
		for (final By locator : locators) {
			try {
				final List<WebElement> elements = driver.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return;
					}
				}
			} catch (final NoSuchElementException ignored) {
				// Try next locator.
			}
		}
		throw new AssertionError(errorMessage);
	}

	private void assertVisibleEmail() {
		final List<WebElement> visibleEmailCandidates = driver.findElements(By.xpath(
				"//*[contains(normalize-space(.), '@') and contains(normalize-space(.), '.') and not(self::script)]"));
		for (final WebElement candidate : visibleEmailCandidates) {
			if (candidate.isDisplayed()) {
				return;
			}
		}
		throw new AssertionError("User email was not visible.");
	}

	private void assertProbableUserNameVisible() {
		final List<String> ignoredStaticTexts = Arrays.asList("Información General", "BUSINESS PLAN", "Cambiar Plan", "Cuenta creada",
				"Estado activo", "Idioma seleccionado", "Tus Negocios", "Sección Legal", "Términos y Condiciones",
				"Política de Privacidad", "Agregar Negocio", "Administrar Negocios");
		final List<WebElement> candidates = driver.findElements(By.xpath("//*[self::h1 or self::h2 or self::h3 or self::p or self::span or self::div]"));
		for (final WebElement candidate : candidates) {
			if (!candidate.isDisplayed()) {
				continue;
			}
			final String text = candidate.getText().trim();
			if (text.length() < 4 || text.contains("@") || ignoredStaticTexts.contains(text)) {
				continue;
			}
			if (text.matches(".*[A-Za-zÀ-ÿ].*")) {
				return;
			}
		}
		throw new AssertionError("User name was not visible.");
	}

	private void waitForUiLoad() {
		try {
			wait.until(d -> "complete".equals(String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"))));
		} catch (final TimeoutException ignored) {
			// Some transitions are SPA-based and may not alter readyState.
		}
	}

	private void captureScreenshot(final String name) {
		if (driver == null) {
			return;
		}
		try {
			final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			final Path screenshotPath = evidenceDir.resolve(Instant.now().toEpochMilli() + "-" + slug(name) + ".png");
			Files.write(screenshotPath, screenshot);
		} catch (final Throwable ignored) {
			// Best effort evidence capture.
		}
	}

	private void writeReport() {
		if (reportPath == null) {
			return;
		}

		try {
			Files.createDirectories(reportPath.getParent());
			Files.writeString(reportPath, toReportJson(), StandardCharsets.UTF_8);
		} catch (final IOException ignored) {
			// Report generation should not break the test lifecycle.
		}
	}

	private String toReportJson() {
		final StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"generatedAt\": \"").append(escapeJson(Instant.now().toString())).append("\",\n");
		builder.append("  \"results\": {\n");

		final List<String> rows = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			rows.add("    \"" + escapeJson(entry.getKey()) + "\": { \"status\": \""
					+ (entry.getValue().passed ? "PASS" : "FAIL") + "\", \"details\": \""
					+ escapeJson(entry.getValue().details) + "\" }");
		}
		builder.append(String.join(",\n", rows));
		builder.append("\n  },\n");
		builder.append("  \"legalUrls\": {\n");

		final List<String> urlRows = new ArrayList<>();
		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			urlRows.add("    \"" + escapeJson(entry.getKey()) + "\": \"" + escapeJson(entry.getValue()) + "\"");
		}
		builder.append(String.join(",\n", urlRows));
		builder.append("\n  }\n");
		builder.append("}\n");
		return builder.toString();
	}

	private static String slug(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static String escapeJson(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}
		final String[] parts = text.split("'");
		final String joined = Arrays.stream(parts).map(part -> "'" + part + "'").collect(Collectors.joining(", \"'\", "));
		return "concat(" + joined + ")";
	}

	private static String getString(final String envName, final String propertyName, final String defaultValue) {
		final String fromProperty = System.getProperty(propertyName);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}
		final String fromEnv = System.getenv(envName);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}
		return defaultValue;
	}

	private static boolean getBoolean(final String envName, final String propertyName, final boolean defaultValue) {
		final String value = getString(envName, propertyName, String.valueOf(defaultValue));
		return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
	}

	private static long getLong(final String envName, final String propertyName, final long defaultValue) {
		final String value = getString(envName, propertyName, "");
		if (value.isBlank()) {
			return defaultValue;
		}
		try {
			return Long.parseLong(value);
		} catch (final NumberFormatException ex) {
			return defaultValue;
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run();
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final Throwable throwable) {
			final String message = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
			return new StepResult(false, message);
		}
	}
}
