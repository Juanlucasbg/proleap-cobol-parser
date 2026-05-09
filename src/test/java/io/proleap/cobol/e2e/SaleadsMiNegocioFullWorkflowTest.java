package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * SaleADS "Mi Negocio" full workflow test.
 *
 * <p>Environment agnostic by design:
 * no domain is hardcoded. Configure the target login URL with either
 * -Dsaleads.url=https://... or SALEADS_URL=https://...
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String SECCION_LEGAL = "Secci\u00f3n Legal";
	private static final String TERMINOS_Y_CONDICIONES = "T\u00e9rminos y Condiciones";
	private static final String POLITICA_PRIVACIDAD = "Pol\u00edtica de Privacidad";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final List<String> reportOrder = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			INFORMACION_GENERAL,
			DETALLES_CUENTA,
			"Tus Negocios",
			TERMINOS_Y_CONDICIONES,
			POLITICA_PRIVACIDAD);

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver,
				Duration.ofSeconds(Long.parseLong(readConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", "30"))));
		evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() throws IOException {
		try {
			if (driver != null) {
				driver.quit();
			}
		} finally {
			writeReport();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		Assume.assumeTrue(
				"Enable this E2E flow with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true.",
				Boolean.parseBoolean(readConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false")));
		openConfiguredEnvironment();

		runStep("Login", this::validateLoginWithGoogle);
		runStep("Mi Negocio menu", this::validateMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::validateAdministrarNegociosView);
		runStep(INFORMACION_GENERAL, this::validateInformacionGeneral);
		runStep(DETALLES_CUENTA, this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep(TERMINOS_Y_CONDICIONES, () -> validateLegalLink(TERMINOS_Y_CONDICIONES, "08-terminos.png"));
		runStep(POLITICA_PRIVACIDAD, () -> validateLegalLink(POLITICA_PRIVACIDAD, "09-politica-privacidad.png"));

		final List<String> failedSteps = reportOrder.stream()
				.filter(step -> !report.getOrDefault(step, StepResult.failed("Not executed")).passed)
				.collect(Collectors.toList());

		assertTrue("Workflow validation failed for steps: " + failedSteps + ". Report: " + evidenceDir.resolve("final-report.md"),
				failedSteps.isEmpty());
	}

	private void openConfiguredEnvironment() {
		final String url = readConfig("saleads.url", "SALEADS_URL", "");
		if (url == null || url.isBlank()) {
			throw new IllegalStateException(
					"Missing environment URL. Provide -Dsaleads.url=<login_url> or SALEADS_URL=<login_url>.");
		}
		driver.get(url);
		waitForUiToLoad();
	}

	private StepResult validateLoginWithGoogle() throws IOException {
		boolean clickedLogin = false;
		if (!isSidebarVisible()) {
			clickedLogin = clickAnyVisibleText("Sign in with Google", "Login with Google", "Continuar con Google", "Google");
			if (!clickedLogin) {
				return StepResult.failed("Unable to find a Google login button.");
			}
			waitForUiToLoad();
			handleGoogleAccountChooserIfPresent();
		}

		final boolean interfaceVisible = waitForAnyVisibleText("Dashboard", "Inicio", "Mi Negocio", "Negocio")
				&& isSidebarVisible();
		takeScreenshot("01-dashboard.png");

		if (interfaceVisible) {
			if (clickedLogin) {
				return StepResult.passed("Dashboard loaded and sidebar visible after Google login.");
			}
			return StepResult.passed("Session already authenticated; dashboard and sidebar are visible.");
		}
		return StepResult.failed("Main interface and sidebar were not visible after login.");
	}

	private void handleGoogleAccountChooserIfPresent() {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		final String popupHandle = waitForNewWindow(handlesBefore, Duration.ofSeconds(8));
		if (popupHandle != null) {
			driver.switchTo().window(popupHandle);
			waitForUiToLoad();
			clickAnyVisibleText(GOOGLE_ACCOUNT_EMAIL);
			waitForUiToLoad();
			try {
				wait.until(d -> d.getWindowHandles().size() < handlesBefore.size() + 1);
			} catch (final TimeoutException ignored) {
				// Some flows keep the tab open; this test still proceeds.
			}
			if (driver.getWindowHandles().contains(originalWindow)) {
				driver.switchTo().window(originalWindow);
			}
			waitForUiToLoad();
			return;
		}

		// Same-tab Google selector fallback.
		clickAnyVisibleText(GOOGLE_ACCOUNT_EMAIL);
		waitForUiToLoad();
	}

	private StepResult validateMiNegocioMenu() throws IOException {
		expandMiNegocioMenuIfNeeded();
		final boolean agregarVisible = isTextVisible("Agregar Negocio");
		final boolean administrarVisible = isTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded.png");

		if (agregarVisible && administrarVisible) {
			return StepResult.passed("Mi Negocio expanded with expected submenu options.");
		}
		return StepResult.failed("Expected submenu options are not visible.");
	}

	private StepResult validateAgregarNegocioModal() throws IOException {
		if (!clickAnyVisibleText("Agregar Negocio")) {
			return StepResult.failed("Could not click 'Agregar Negocio'.");
		}
		waitForUiToLoad();

		final boolean modalTitle = isTextVisible("Crear Nuevo Negocio");
		final boolean nombreField = isElementVisible(By.xpath("//*[contains(normalize-space(.), 'Nombre del Negocio')]"
				+ " | //input[contains(@placeholder, 'Nombre del Negocio')]"));
		final boolean negociosCounter = isTextVisible("Tienes 2 de 3 negocios");
		final boolean cancelar = isTextVisible("Cancelar");
		final boolean crearNegocio = isTextVisible("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal.png");

		if (nombreField) {
			typeIntoFirstVisibleInput("Nombre del Negocio", "Negocio Prueba Automatizacion");
		}
		clickAnyVisibleText("Cancelar");
		waitForUiToLoad();

		if (modalTitle && nombreField && negociosCounter && cancelar && crearNegocio) {
			return StepResult.passed("Crear Nuevo Negocio modal validated.");
		}
		return StepResult.failed("Modal validation failed for one or more expected fields/buttons.");
	}

	private StepResult validateAdministrarNegociosView() throws IOException {
		expandMiNegocioMenuIfNeeded();
		if (!clickAnyVisibleText("Administrar Negocios")) {
			return StepResult.failed("Could not open 'Administrar Negocios'.");
		}
		waitForUiToLoad();

		final boolean info = isTextVisible(INFORMACION_GENERAL);
		final boolean detalles = isTextVisible(DETALLES_CUENTA);
		final boolean negocios = isTextVisible("Tus Negocios");
		final boolean legal = isTextVisible(SECCION_LEGAL);
		takeFullPageScreenshot("04-administrar-negocios-full.png");

		if (info && detalles && negocios && legal) {
			return StepResult.passed("Account page sections are visible.");
		}
		return StepResult.failed("One or more expected account sections are missing.");
	}

	private StepResult validateInformacionGeneral() {
		final boolean hasEmail = pageContainsPattern(EMAIL_PATTERN);
		final boolean businessPlan = isTextVisible("BUSINESS PLAN");
		final boolean cambiarPlan = isTextVisible("Cambiar Plan");
		final boolean hasNameLikeText = sectionHasNonEmptyValue(INFORMACION_GENERAL);

		if (hasEmail && businessPlan && cambiarPlan && hasNameLikeText) {
			return StepResult.passed("Informacion General shows user identity and plan details.");
		}
		return StepResult.failed("Informacion General is missing user or plan information.");
	}

	private StepResult validateDetallesCuenta() {
		final boolean created = isTextVisible("Cuenta creada");
		final boolean active = isTextVisible("Estado activo");
		final boolean language = isTextVisible("Idioma seleccionado");

		if (created && active && language) {
			return StepResult.passed("Detalles de la Cuenta fields are visible.");
		}
		return StepResult.failed("Expected fields in Detalles de la Cuenta were not all visible.");
	}

	private StepResult validateTusNegocios() {
		final boolean listVisible = isTextVisible("Tus Negocios");
		final boolean addButton = isTextVisible("Agregar Negocio");
		final boolean counter = isTextVisible("Tienes 2 de 3 negocios");

		if (listVisible && addButton && counter) {
			return StepResult.passed("Tus Negocios list, button, and counter are visible.");
		}
		return StepResult.failed("Tus Negocios validation failed.");
	}

	private StepResult validateLegalLink(final String linkText, final String screenshotName) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final String originUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = driver.getWindowHandles();

		if (!clickAnyVisibleText(linkText)) {
			return StepResult.failed("Could not click legal link: " + linkText);
		}

		String targetWindow = waitForNewWindow(handlesBefore, Duration.ofSeconds(8));
		boolean openedNewTab = false;
		if (targetWindow != null) {
			openedNewTab = true;
			driver.switchTo().window(targetWindow);
		}
		waitForUiToLoad();

		wait.until(d -> !Objects.equals(d.getCurrentUrl(), originUrl) || isTextVisible(linkText));
		final String finalUrl = driver.getCurrentUrl();
		final boolean headingVisible = isTextVisible(linkText);
		final boolean legalContentVisible = bodyTextLength() > 120;
		takeScreenshot(screenshotName);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();

		if (headingVisible && legalContentVisible) {
			return StepResult.passed("Validated legal page. URL: " + finalUrl);
		}
		return StepResult.failed("Legal page validation failed for '" + linkText + "'. URL: " + finalUrl);
	}

	private void expandMiNegocioMenuIfNeeded() {
		if (isTextVisible("Agregar Negocio") && isTextVisible("Administrar Negocios")) {
			return;
		}
		clickAnyVisibleText("Negocio");
		waitForUiToLoad();
		clickAnyVisibleText("Mi Negocio");
		waitForUiToLoad();
	}

	private void runStep(final String stepName, final StepEvaluator evaluator) {
		try {
			report.put(stepName, evaluator.evaluate());
		} catch (final Exception ex) {
			report.put(stepName, StepResult.failed("Error: " + ex.getMessage()));
		}
	}

	private void waitForUiToLoad() {
		wait.until((ExpectedCondition<Boolean>) d -> {
			if (!(d instanceof JavascriptExecutor)) {
				return true;
			}
			final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(readyState) || "interactive".equals(readyState);
		});
	}

	private boolean waitForAnyVisibleText(final String... texts) {
		try {
			wait.until(d -> Arrays.stream(texts).anyMatch(this::isTextVisible));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private boolean clickAnyVisibleText(final String... texts) {
		for (final String text : texts) {
			final By locator = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text)
					+ ") and not(self::script) and not(self::style)]");
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				for (final WebElement element : driver.findElements(locator)) {
					if (element.isDisplayed()) {
						try {
							wait.until(ExpectedConditions.elementToBeClickable(element));
							element.click();
							waitForUiToLoad();
							return true;
						} catch (final Exception clickException) {
							((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
							waitForUiToLoad();
							return true;
						}
					}
				}
			} catch (final TimeoutException ignored) {
				// Try next candidate text.
			}
		}
		return false;
	}

	private boolean isTextVisible(final String text) {
		final By locator = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
		return isElementVisible(locator);
	}

	private boolean isElementVisible(final By locator) {
		try {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final NoSuchElementException ex) {
			return false;
		}
	}

	private boolean isSidebarVisible() {
		final List<By> sidebarLocators = Arrays.asList(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class, 'sidebar')]"),
				By.xpath("//*[contains(@class, 'menu')]"));

		for (final By locator : sidebarLocators) {
			if (isElementVisible(locator)) {
				return true;
			}
		}
		return isTextVisible("Mi Negocio") || isTextVisible("Negocio");
	}

	private boolean sectionHasNonEmptyValue(final String sectionTitle) {
		final By sectionLocator = By.xpath(
				"//*[contains(normalize-space(.), " + xpathLiteral(sectionTitle) + ")]/ancestor::*[self::section or self::div][1]");
		try {
			final WebElement section = driver.findElement(sectionLocator);
			final String text = section.getText();
			return text != null && text.trim().replace(sectionTitle, "").trim().length() > 3;
		} catch (final Exception ex) {
			return false;
		}
	}

	private void typeIntoFirstVisibleInput(final String relatedLabelText, final String value) {
		final List<By> inputCandidates = Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), " + xpathLiteral(relatedLabelText)
						+ ")]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, " + xpathLiteral(relatedLabelText) + ")]"),
				By.xpath("//input[1]"));

		for (final By candidate : inputCandidates) {
			try {
				for (final WebElement input : driver.findElements(candidate)) {
					if (input.isDisplayed()) {
						input.clear();
						input.sendKeys(value);
						return;
					}
				}
			} catch (final Exception ignored) {
				// Try next candidate.
			}
		}
	}

	private boolean pageContainsPattern(final Pattern pattern) {
		final String body = driver.findElement(By.tagName("body")).getText();
		return body != null && pattern.matcher(body).find();
	}

	private int bodyTextLength() {
		try {
			final String body = driver.findElement(By.tagName("body")).getText();
			return body == null ? 0 : body.trim().length();
		} catch (final Exception ex) {
			return 0;
		}
	}

	private String waitForNewWindow(final Set<String> existingHandles, final Duration timeout) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			return shortWait.until(d -> {
				final Set<String> currentHandles = d.getWindowHandles();
				for (final String handle : currentHandles) {
					if (!existingHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException ex) {
			return null;
		}
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final byte[] data = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(evidenceDir.resolve(fileName), data);
	}

	private void takeFullPageScreenshot(final String fileName) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final Object scrollHeight = ((JavascriptExecutor) driver).executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			final int targetHeight = Math.max(originalSize.getHeight(), Math.min(Integer.parseInt(scrollHeight.toString()), 8000));
			driver.manage().window().setSize(new Dimension(originalSize.getWidth(), targetHeight));
			waitForUiToLoad();
			takeScreenshot(fileName);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}
	}

	private void writeReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("# SaleADS Mi Negocio Full Workflow Report\n\n");
		sb.append("| Step | Result | Details |\n");
		sb.append("|---|---|---|\n");
		for (final String key : reportOrder) {
			final StepResult stepResult = report.getOrDefault(key, StepResult.failed("Not executed"));
			sb.append("| ")
					.append(key)
					.append(" | ")
					.append(stepResult.passed ? "PASS" : "FAIL")
					.append(" | ")
					.append(stepResult.details.replace("|", "\\|"))
					.append(" |\n");
		}
		sb.append("\n");
		sb.append("Evidence directory: `").append(evidenceDir.toAbsolutePath()).append("`\n");

		Files.write(evidenceDir.resolve("final-report.md"), sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String readConfig(final String sysProp, final String envVar, final String defaultValue) {
		final String sysValue = System.getProperty(sysProp);
		if (sysValue != null && !sysValue.isBlank()) {
			return sysValue.trim();
		}
		final String envValue = System.getenv(envVar);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		return defaultValue;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final String joined = Arrays.stream(parts)
				.map(part -> "'" + part + "'")
				.collect(Collectors.joining(", \"'\", "));
		return "concat(" + joined + ")";
	}

	@FunctionalInterface
	private interface StepEvaluator {
		StepResult evaluate() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult passed(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult failed(final String details) {
			return new StepResult(false, details);
		}
	}
}
