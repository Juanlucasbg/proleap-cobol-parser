package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * SaleADS.ai cross-environment E2E flow for Mi Negocio module.
 *
 * <p>
 * Runtime configuration:
 * </p>
 * <ul>
 * <li>SALEADS_LOGIN_URL (required to execute test in CI)</li>
 * <li>SALEADS_GOOGLE_EMAIL (defaults to juanlucasbarbiergarzon@gmail.com)</li>
 * <li>SALEADS_BROWSER (chrome|firefox, defaults to chrome)</li>
 * <li>SALEADS_HEADLESS (true|false, defaults to false)</li>
 * <li>SALEADS_WAIT_SECONDS (defaults to 30)</li>
 * <li>SALEADS_SCREENSHOT_DIR (defaults to target/saleads-screenshots)</li>
 * <li>SALEADS_REPORT_PATH (defaults to target/saleads_mi_negocio_full_test_report.json)</li>
 * <li>SELENIUM_REMOTE_URL (optional, for grid/remote execution)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Informaci\u00f3n General", "Detalles de la Cuenta", "Tus Negocios",
			"T\u00e9rminos y Condiciones", "Pol\u00edtica de Privacidad");

	private static final String LOGIN_FIELD = "Login";
	private static final String MENU_FIELD = "Mi Negocio menu";
	private static final String MODAL_FIELD = "Agregar Negocio modal";
	private static final String ADMIN_FIELD = "Administrar Negocios view";
	private static final String INFO_FIELD = "Informaci\u00f3n General";
	private static final String ACCOUNT_DETAILS_FIELD = "Detalles de la Cuenta";
	private static final String BUSINESSES_FIELD = "Tus Negocios";
	private static final String TERMS_FIELD = "T\u00e9rminos y Condiciones";
	private static final String PRIVACY_FIELD = "Pol\u00edtica de Privacidad";

	private final Map<String, StepResult> results = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private String googleEmail;
	private String loginUrl;
	private Path screenshotDir;
	private Path reportPath;
	private int waitSeconds;

	@Before
	public void setUp() throws IOException {
		initResults();
		googleEmail = envOrDefault("SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL);
		loginUrl = envOrDefault("SALEADS_LOGIN_URL", "").trim();
		waitSeconds = Integer.parseInt(envOrDefault("SALEADS_WAIT_SECONDS", "30"));
		screenshotDir = Path.of(envOrDefault("SALEADS_SCREENSHOT_DIR", "target/saleads-screenshots"));
		reportPath = Path.of(envOrDefault("SALEADS_REPORT_PATH", "target/saleads_mi_negocio_full_test_report.json"));

		Files.createDirectories(screenshotDir);
		if (reportPath.getParent() != null) {
			Files.createDirectories(reportPath.getParent());
		}

		if (!loginUrl.isEmpty()) {
			driver = buildDriver();
			wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(waitSeconds));
		}
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() throws IOException {
		if (loginUrl.isEmpty()) {
			markAllStepsFailed("Missing SALEADS_LOGIN_URL; workflow was not executed.");
		}
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to execute this workflow.", !loginUrl.isEmpty());
		driver.get(loginUrl);
		waitForUiToLoad();

		stepLoginWithGoogle();
		stepOpenMiNegocioMenu();
		stepValidateAgregarNegocioModal();
		stepOpenAdministrarNegocios();
		stepValidateInformacionGeneral();
		stepValidateDetallesCuenta();
		stepValidateTusNegocios();
		stepValidateTermsAndConditions();
		stepValidatePrivacyPolicy();

		assertTrue("One or more validations failed. Check report: " + reportPath.toAbsolutePath(), allStepsPassed());
	}

	private void stepLoginWithGoogle() throws IOException {
		final StepResult step = results.get(LOGIN_FIELD);
		try {
			clickFirstVisibleText(step, "Sign in with Google", "Iniciar sesi\u00f3n con Google", "Inicia sesi\u00f3n con Google",
					"Continuar con Google", "Ingresar con Google", "Google");
			waitForUiToLoad();
			selectGoogleAccountIfVisible(step);

			checkVisibleText(step, "Negocio");
			checkVisibleText(step, "Mi Negocio");
			capture(step, "01-dashboard-loaded");
		} catch (Exception e) {
			step.fail("Login flow failed: " + e.getMessage());
		}
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		final StepResult step = results.get(MENU_FIELD);
		try {
			// Keep clicks text-driven so this works across environments.
			safeClickByText(step, "Negocio");
			waitForUiToLoad();
			safeClickByText(step, "Mi Negocio");
			waitForUiToLoad();

			checkVisibleText(step, "Agregar Negocio");
			checkVisibleText(step, "Administrar Negocios");
			capture(step, "02-mi-negocio-expanded");
		} catch (Exception e) {
			step.fail("Mi Negocio menu validation failed: " + e.getMessage());
		}
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		final StepResult step = results.get(MODAL_FIELD);
		try {
			safeClickByText(step, "Agregar Negocio");
			waitForUiToLoad();

			checkVisibleText(step, "Crear Nuevo Negocio");
			checkVisibleText(step, "Nombre del Negocio");
			checkVisibleText(step, "Tienes 2 de 3 negocios");
			checkVisibleText(step, "Cancelar");
			checkVisibleText(step, "Crear Negocio");
			capture(step, "03-agregar-negocio-modal");

			typeIfVisible(step, "Nombre del Negocio", "Negocio Prueba Automatizacion");
			safeClickByText(step, "Cancelar");
			waitForUiToLoad();
		} catch (Exception e) {
			step.fail("Agregar Negocio modal validation failed: " + e.getMessage());
		}
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		final StepResult step = results.get(ADMIN_FIELD);
		try {
			if (!isTextVisible("Administrar Negocios")) {
				safeClickByText(step, "Mi Negocio");
				waitForUiToLoad();
			}

			safeClickByText(step, "Administrar Negocios");
			waitForUiToLoad();

			checkVisibleText(step, "Informaci\u00f3n General");
			checkVisibleText(step, "Detalles de la Cuenta");
			checkVisibleText(step, "Tus Negocios");
			checkVisibleText(step, "Secci\u00f3n Legal");
			capture(step, "04-administrar-negocios-view");
		} catch (Exception e) {
			step.fail("Administrar Negocios view validation failed: " + e.getMessage());
		}
	}

	private void stepValidateInformacionGeneral() {
		final StepResult step = results.get(INFO_FIELD);
		try {
			final String sectionText = extractSectionText("Informaci\u00f3n General");
			final boolean hasEmail = sectionText.matches("(?s).*\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b.*");
			final boolean hasBusinessPlan = sectionText.contains("BUSINESS PLAN");
			final boolean hasCambiarPlan = sectionText.contains("Cambiar Plan");
			final boolean hasAnyNameLikeText = sectionText.matches("(?s).*\\b[A-Za-z\\u00c0-\\u017f]{2,}\\s+[A-Za-z\\u00c0-\\u017f]{2,}\\b.*");

			if (!hasAnyNameLikeText) {
				step.fail("User name is not visible in Informacion General.");
			}
			if (!hasEmail) {
				step.fail("User email is not visible in Informacion General.");
			}
			if (!hasBusinessPlan) {
				step.fail("Text 'BUSINESS PLAN' is missing in Informacion General.");
			}
			if (!hasCambiarPlan) {
				step.fail("Button 'Cambiar Plan' is missing in Informacion General.");
			}
		} catch (Exception e) {
			step.fail("Informacion General validation failed: " + e.getMessage());
		}
	}

	private void stepValidateDetallesCuenta() {
		final StepResult step = results.get(ACCOUNT_DETAILS_FIELD);
		try {
			final String sectionText = extractSectionText("Detalles de la Cuenta");
			if (!sectionText.contains("Cuenta creada")) {
				step.fail("'Cuenta creada' is missing.");
			}
			if (!sectionText.contains("Estado activo")) {
				step.fail("'Estado activo' is missing.");
			}
			if (!sectionText.contains("Idioma seleccionado")) {
				step.fail("'Idioma seleccionado' is missing.");
			}
		} catch (Exception e) {
			step.fail("Detalles de la Cuenta validation failed: " + e.getMessage());
		}
	}

	private void stepValidateTusNegocios() {
		final StepResult step = results.get(BUSINESSES_FIELD);
		try {
			final String sectionText = extractSectionText("Tus Negocios");
			if (sectionText.isBlank()) {
				step.fail("Business list section appears empty.");
			}
			if (!sectionText.contains("Agregar Negocio")) {
				step.fail("Button 'Agregar Negocio' is missing in Tus Negocios.");
			}
			if (!sectionText.contains("Tienes 2 de 3 negocios")) {
				step.fail("Text 'Tienes 2 de 3 negocios' is missing in Tus Negocios.");
			}
		} catch (Exception e) {
			step.fail("Tus Negocios validation failed: " + e.getMessage());
		}
	}

	private void stepValidateTermsAndConditions() throws IOException {
		validateLegalLink(TERMS_FIELD, "T\u00e9rminos y Condiciones", "T\u00e9rminos y Condiciones", "08-terminos-y-condiciones");
	}

	private void stepValidatePrivacyPolicy() throws IOException {
		validateLegalLink(PRIVACY_FIELD, "Pol\u00edtica de Privacidad", "Pol\u00edtica de Privacidad", "09-politica-de-privacidad");
	}

	private void validateLegalLink(final String reportField, final String linkText, final String headingText,
			final String screenshotName) throws IOException {
		final StepResult step = results.get(reportField);
		try {
			final String appTab = driver.getWindowHandle();
			final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
			final String urlBefore = driver.getCurrentUrl();

			safeClickByText(step, linkText);
			waitForUiToLoad();

			wait.until(d -> d.getWindowHandles().size() > beforeHandles.size() || !safeCurrentUrl().equals(urlBefore));

			final Set<String> afterHandles = driver.getWindowHandles();
			final Set<String> newHandles = new LinkedHashSet<>(afterHandles);
			newHandles.removeAll(beforeHandles);

			boolean openedNewTab = !newHandles.isEmpty();
			if (openedNewTab) {
				final String newHandle = newHandles.iterator().next();
				driver.switchTo().window(newHandle);
				waitForUiToLoad();
			}

			checkVisibleText(step, headingText);
			final String bodyText = safeBodyText();
			if (bodyText.length() < 100) {
				step.fail("Legal content text is not sufficiently visible for " + linkText + ".");
			}

			capture(step, screenshotName);
			step.note("Final URL: " + safeCurrentUrl());

			if (openedNewTab) {
				driver.close();
				driver.switchTo().window(appTab);
				waitForUiToLoad();
			} else {
				driver.navigate().back();
				waitForUiToLoad();
			}
		} catch (Exception e) {
			step.fail(reportField + " validation failed: " + e.getMessage());
		}
	}

	private WebDriver buildDriver() throws MalformedURLException {
		final String browser = envOrDefault("SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "false"));
		final String remoteUrl = envOrDefault("SELENIUM_REMOTE_URL", "").trim();

		if ("firefox".equals(browser)) {
			final FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			if (!remoteUrl.isEmpty()) {
				return new RemoteWebDriver(URI.create(remoteUrl).toURL(), options);
			}
			return new FirefoxDriver(options);
		}

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-popup-blocking");
		options.addArguments("--start-maximized");
		if (headless) {
			options.addArguments("--headless=new");
			options.addArguments("--window-size=1920,1080");
		}
		if (!remoteUrl.isEmpty()) {
			return new RemoteWebDriver(URI.create(remoteUrl).toURL(), options);
		}
		return new ChromeDriver(options);
	}

	private void initResults() {
		results.clear();
		for (final String field : REPORT_FIELDS) {
			results.put(field, new StepResult(field));
		}
	}

	private void clickFirstVisibleText(final StepResult step, final String... texts) {
		Exception lastError = null;
		for (final String text : texts) {
			try {
				safeClickByText(step, text);
				return;
			} catch (Exception e) {
				lastError = e;
			}
		}
		throw new RuntimeException("Could not click any expected text: " + Arrays.toString(texts), lastError);
	}

	private void selectGoogleAccountIfVisible(final StepResult step) {
		try {
			final By accountSelector = byVisibleText(googleEmail);
			final WebElement account = new WebDriverWait(driver, java.time.Duration.ofSeconds(8))
					.until(ExpectedConditions.visibilityOfElementLocated(accountSelector));
			clickElement(step, account, "Selected Google account " + googleEmail);
			waitForUiToLoad();
		} catch (TimeoutException ignored) {
			step.note("Google account selector did not appear; continuing.");
		}
	}

	private void safeClickByText(final StepResult step, final String text) {
		final By locator = byClickableText(text);
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
		clickElement(step, element, "Clicked '" + text + "'");
	}

	private void clickElement(final StepResult step, final WebElement element, final String detailMessage) {
		try {
			element.click();
			step.note(detailMessage);
		} catch (Exception clickException) {
			try {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				step.note(detailMessage + " (via JS click)");
			} catch (Exception jsException) {
				throw new RuntimeException("Click failed: " + detailMessage + " => " + jsException.getMessage(), clickException);
			}
		}
	}

	private void checkVisibleText(final StepResult step, final String text) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
		} catch (TimeoutException e) {
			step.fail("Expected visible text not found: '" + text + "'");
		}
	}

	private void typeIfVisible(final StepResult step, final String fieldLabelText, final String value) {
		final List<By> candidates = Arrays.asList(
				By.xpath("//label[normalize-space()='" + escapeXPath(fieldLabelText) + "']/following::input[1]"),
				By.xpath("//input[@placeholder='" + escapeXPath(fieldLabelText) + "']"),
				By.xpath("//input[contains(@aria-label,'" + escapeXPath(fieldLabelText) + "')]"),
				By.xpath("//input[contains(@name,'negocio') or contains(@id,'negocio')]"));

		for (final By candidate : candidates) {
			try {
				final WebElement input = new WebDriverWait(driver, java.time.Duration.ofSeconds(4))
						.until(ExpectedConditions.visibilityOfElementLocated(candidate));
				input.clear();
				input.sendKeys(value);
				step.note("Typed in field '" + fieldLabelText + "'.");
				return;
			} catch (Exception ignored) {
				// Try next candidate.
			}
		}

		step.note("Optional typing step skipped; input field not found for '" + fieldLabelText + "'.");
	}

	private String extractSectionText(final String headingText) {
		final By headingLocator = byVisibleText(headingText);
		final WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(headingLocator));

		try {
			final WebElement section = heading.findElement(By.xpath("ancestor::*[self::section or self::article or self::div][1]"));
			return section.getText();
		} catch (NoSuchElementException ignored) {
			return heading.getText();
		}
	}

	private boolean isTextVisible(final String text) {
		try {
			return driver.findElement(byVisibleText(text)).isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(600L);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void capture(final StepResult step, final String name) throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(java.time.ZoneOffset.UTC)
				.format(Instant.now());
		final Path target = screenshotDir.resolve(name + "_" + timestamp + ".png");
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		step.note("Screenshot: " + target.toAbsolutePath());
	}

	private By byVisibleText(final String text) {
		final String escaped = escapeXPath(text);
		return By.xpath("//*[normalize-space()='" + escaped + "' or contains(normalize-space(),'" + escaped + "')]");
	}

	private By byClickableText(final String text) {
		final String escaped = escapeXPath(text);
		return By.xpath("//*[self::button or self::a or @role='button' or self::span or self::div]"
				+ "[normalize-space()='" + escaped + "' or contains(normalize-space(),'" + escaped + "')]");
	}

	private String escapeXPath(final String value) {
		return value.replace("'", "\\'");
	}

	private boolean allStepsPassed() {
		for (final String field : REPORT_FIELDS) {
			if (!results.get(field).passed) {
				return false;
			}
		}
		return true;
	}

	private void markAllStepsFailed(final String reason) {
		for (final String field : REPORT_FIELDS) {
			results.get(field).fail(reason);
		}
	}

	private void writeReport() throws IOException {
		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"name\": \"").append(TEST_NAME).append("\",\n");
		json.append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n");
		json.append("  \"overallStatus\": \"").append(allStepsPassed() ? "PASS" : "FAIL").append("\",\n");
		json.append("  \"results\": [\n");

		for (int i = 0; i < REPORT_FIELDS.size(); i++) {
			final String field = REPORT_FIELDS.get(i);
			final StepResult step = results.get(field);
			json.append("    {\n");
			json.append("      \"field\": \"").append(jsonEscape(field)).append("\",\n");
			json.append("      \"status\": \"").append(step.passed ? "PASS" : "FAIL").append("\",\n");
			json.append("      \"details\": [");
			for (int j = 0; j < step.details.size(); j++) {
				if (j > 0) {
					json.append(", ");
				}
				json.append("\"").append(jsonEscape(step.details.get(j))).append("\"");
			}
			json.append("]\n");
			json.append("    }");
			if (i < REPORT_FIELDS.size() - 1) {
				json.append(",");
			}
			json.append("\n");
		}
		json.append("  ]\n");
		json.append("}\n");

		Files.writeString(reportPath, json.toString(), StandardCharsets.UTF_8);
	}

	private String jsonEscape(final String input) {
		return input.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String safeBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (Exception e) {
			return "";
		}
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (Exception e) {
			return "";
		}
	}

	private String envOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value;
	}

	private static final class StepResult {
		private boolean passed = true;
		private final List<String> details = new ArrayList<>();

		private StepResult(final String field) {
		}

		private void note(final String message) {
			details.add(message);
		}

		private void fail(final String message) {
			passed = false;
			details.add(message);
		}
	}
}
