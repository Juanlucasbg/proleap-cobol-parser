package io.proleap.cobol.e2e;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

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
import java.util.regex.Pattern;

/**
 * End-to-end Selenium test for the SaleADS "Mi Negocio" module workflow.
 *
 * <p>Run explicitly with:
 * <pre>
 * mvn -Dtest=SaleAdsMiNegocioWorkflowTest -Drun.saleads.e2e=true -Dsaleads.login.url=https://... test
 * </pre>
 *
 * <p>Configuration options:
 * <ul>
 *   <li>-Drun.saleads.e2e=true (required to execute this test)</li>
 *   <li>-Dsaleads.login.url=https://... (optional, environment specific login URL)</li>
 *   <li>-Dsaleads.headless=true|false (optional, defaults to false unless CI=true)</li>
 *   <li>-Dsaleads.timeout.seconds=30 (optional explicit timeout)</li>
 * </ul>
 */
public class SaleAdsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad"
	);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Duration SHORT_WAIT = Duration.ofSeconds(8);
	private static final Duration POST_CLICK_SETTLE = Duration.ofMillis(700);

	private final LinkedHashMap<String, StepResult> report = new LinkedHashMap<>();
	private final DateTimeFormatter timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path runOutputDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		final boolean shouldRun = Boolean.parseBoolean(readConfig("run.saleads.e2e", "false"));
		Assume.assumeTrue("Skipping SaleADS E2E test (set -Drun.saleads.e2e=true to run)", shouldRun);

		final String runId = LocalDateTime.now().format(timestampFormat);
		runOutputDir = Path.of("target", "saleads-mi-negocio-e2e", runId);
		Files.createDirectories(runOutputDir);

		final boolean defaultHeadless = "true".equalsIgnoreCase(System.getenv("CI"));
		final boolean headless = Boolean.parseBoolean(readConfig("saleads.headless", Boolean.toString(defaultHeadless)));
		final int timeoutSeconds = Integer.parseInt(readConfig("saleads.timeout.seconds", "30"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		final String loginUrl = readConfig("saleads.login.url", "").trim();
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", step -> stepValidateLegalLink(
				step,
				new String[]{"Términos y Condiciones", "Terminos y Condiciones"},
				new String[]{"Términos y Condiciones", "Terminos y Condiciones"}
		));
		runStep("Política de Privacidad", step -> stepValidateLegalLink(
				step,
				new String[]{"Política de Privacidad", "Politica de Privacidad"},
				new String[]{"Política de Privacidad", "Politica de Privacidad"}
		));

		final Path finalReportPath = writeFinalReport();
		final String summary = buildSummary(finalReportPath);

		final boolean hasFailures = report.values().stream().anyMatch(step -> !step.passed);
		Assert.assertFalse(summary, hasFailures);
	}

	private void stepLoginWithGoogle(final StepResult step) throws Exception {
		final String currentUrl = driver.getCurrentUrl();
		step.info("Initial URL: " + currentUrl);
		if (currentUrl == null || currentUrl.startsWith("about:blank")) {
			step.fail("Browser is on about:blank. Pass -Dsaleads.login.url=<environment_login_url>.");
			return;
		}

		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final WebElement loginButton = clickVisibleText(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Iniciar sesion con Google",
				"Entrar con Google",
				"Continuar con Google",
				"Google"
		);
		step.pass("Clicked login button: " + normalizeText(loginButton.getText()));

		final String newAuthWindowHandle = waitForNewWindow(handlesBeforeClick, SHORT_WAIT);
		if (newAuthWindowHandle != null) {
			driver.switchTo().window(newAuthWindowHandle);
			waitForUiLoad();
			step.info("Google authentication opened in a new window.");
		}

		if (isTextVisible(SHORT_WAIT, GOOGLE_ACCOUNT_EMAIL)) {
			clickVisibleText(GOOGLE_ACCOUNT_EMAIL);
			step.pass("Selected Google account: " + GOOGLE_ACCOUNT_EMAIL);
		} else {
			step.info("Google account selector did not appear. Continuing with current auth flow.");
		}

		waitForUiLoad();
		switchToMostRelevantApplicationWindow();
		waitForUiLoad();

		final boolean mainInterfaceVisible = isTextVisible(SHORT_WAIT, "Negocio")
				|| isTextVisible(SHORT_WAIT, "Mi Negocio")
				|| isTextVisible(SHORT_WAIT, "Dashboard")
				|| isTextVisible(SHORT_WAIT, "Inicio");
		step.check(mainInterfaceVisible, "Main application interface appears");

		final boolean sidebarVisible = isElementVisible(By.xpath("//aside | //nav"));
		step.check(sidebarVisible, "Left sidebar navigation is visible");

		appWindowHandle = driver.getWindowHandle();
		recordCheckpoint(step, "01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu(final StepResult step) throws Exception {
		step.check(isTextVisible(SHORT_WAIT, "Negocio"), "Section 'Negocio' is visible");

		clickVisibleText("Mi Negocio");
		step.pass("Clicked 'Mi Negocio'.");

		step.check(isTextVisible(SHORT_WAIT, "Agregar Negocio"), "'Agregar Negocio' is visible");
		step.check(isTextVisible(SHORT_WAIT, "Administrar Negocios"), "'Administrar Negocios' is visible");
		recordCheckpoint(step, "02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal(final StepResult step) throws Exception {
		clickVisibleText("Agregar Negocio");
		step.pass("Clicked 'Agregar Negocio'.");

		step.check(isTextVisible(SHORT_WAIT, "Crear Nuevo Negocio"), "Modal title 'Crear Nuevo Negocio' is visible");

		final WebElement businessNameInput = findBusinessNameInput();
		step.check(businessNameInput != null, "Input field 'Nombre del Negocio' exists");

		step.check(isTextVisible(SHORT_WAIT, "Tienes 2 de 3 negocios"), "Text 'Tienes 2 de 3 negocios' is visible");
		step.check(isTextVisible(SHORT_WAIT, "Cancelar"), "Button 'Cancelar' is present");
		step.check(isTextVisible(SHORT_WAIT, "Crear Negocio"), "Button 'Crear Negocio' is present");

		if (businessNameInput != null) {
			businessNameInput.click();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatización");
			waitForUiLoad();
			step.pass("Optional modal interaction completed (typed sample business name).");
		}

		recordCheckpoint(step, "03-agregar-negocio-modal");
		clickVisibleText("Cancelar");
		step.pass("Modal closed using 'Cancelar'.");
	}

	private void stepOpenAdministrarNegocios(final StepResult step) throws Exception {
		if (!isTextVisible(SHORT_WAIT, "Administrar Negocios")) {
			clickVisibleText("Mi Negocio");
			step.info("'Mi Negocio' menu was collapsed; expanded again.");
		}

		clickVisibleText("Administrar Negocios");
		step.pass("Clicked 'Administrar Negocios'.");

		step.check(isTextVisible(SHORT_WAIT, "Información General"), "Section 'Información General' exists");
		step.check(isTextVisible(SHORT_WAIT, "Detalles de la Cuenta"), "Section 'Detalles de la Cuenta' exists");
		step.check(isTextVisible(SHORT_WAIT, "Tus Negocios"), "Section 'Tus Negocios' exists");
		step.check(isTextVisible(SHORT_WAIT, "Sección Legal", "Seccion Legal"), "Section 'Sección Legal' exists");

		recordCheckpoint(step, "04-administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral(final StepResult step) throws Exception {
		final WebElement section = findSectionByHeading("Información General");
		if (section == null) {
			step.fail("Unable to locate section 'Información General'.");
			return;
		}

		final String sectionText = normalizeText(section.getText());
		final boolean hasEmail = EMAIL_PATTERN.matcher(sectionText).find();
		step.check(hasEmail, "User email is visible");

		final boolean hasUserName = hasLikelyUserName(section);
		step.check(hasUserName, "User name is visible");

		step.check(sectionText.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN"), "Text 'BUSINESS PLAN' is visible");
		step.check(isTextVisibleInside(section, "Cambiar Plan"), "Button 'Cambiar Plan' is visible");
	}

	private void stepValidateDetallesCuenta(final StepResult step) throws Exception {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		if (section == null) {
			step.fail("Unable to locate section 'Detalles de la Cuenta'.");
			return;
		}

		step.check(isTextVisibleInside(section, "Cuenta creada"), "'Cuenta creada' is visible");
		step.check(isTextVisibleInside(section, "Estado activo"), "'Estado activo' is visible");
		step.check(isTextVisibleInside(section, "Idioma seleccionado"), "'Idioma seleccionado' is visible");
	}

	private void stepValidateTusNegocios(final StepResult step) throws Exception {
		final WebElement section = findSectionByHeading("Tus Negocios");
		if (section == null) {
			step.fail("Unable to locate section 'Tus Negocios'.");
			return;
		}

		final String sectionText = normalizeText(section.getText());
		step.check(sectionText.length() > 20, "Business list is visible");
		step.check(isTextVisibleInside(section, "Agregar Negocio"), "Button 'Agregar Negocio' exists");
		step.check(isTextVisibleInside(section, "Tienes 2 de 3 negocios"), "Text 'Tienes 2 de 3 negocios' is visible");
	}

	private void stepValidateLegalLink(
			final StepResult step,
			final String[] linkTexts,
			final String[] expectedHeadingTexts
	) throws Exception {
		final String startUrl = driver.getCurrentUrl();
		final Set<String> windowsBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickVisibleText(linkTexts);
		step.pass("Clicked legal link: " + Arrays.toString(linkTexts));

		final String newWindowHandle = waitForNewWindow(windowsBeforeClick, SHORT_WAIT);
		final boolean openedNewWindow = newWindowHandle != null;
		if (openedNewWindow) {
			driver.switchTo().window(newWindowHandle);
			waitForUiLoad();
			step.info("Legal page opened in a new tab/window.");
		}

		final boolean headingVisible = isTextVisible(SHORT_WAIT, expectedHeadingTexts);
		step.check(headingVisible, "Legal page heading is visible");

		final String pageText = normalizeText(driver.findElement(By.tagName("body")).getText());
		final boolean hasLegalContent = pageText.length() >= 250;
		step.check(hasLegalContent, "Legal content text is visible");

		final String finalUrl = driver.getCurrentUrl();
		step.finalUrl = finalUrl;
		step.info("Final URL: " + finalUrl);
		recordCheckpoint(step, "legal-" + slugify(expectedHeadingTexts[0]));

		if (openedNewWindow) {
			driver.close();
			driver.switchTo().window(resolveAppWindowHandle());
			waitForUiLoad();
		} else if (!normalizeText(finalUrl).equals(normalizeText(startUrl))) {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String stepName, final StepExecutor executor) {
		final StepResult step = new StepResult(stepName);
		report.put(stepName, step);

		try {
			executor.execute(step);
		} catch (Exception ex) {
			step.fail("Unhandled exception: " + ex.getClass().getSimpleName() + " - " + safeMessage(ex));
			recordFailureCheckpoint(step);
		}
	}

	private void recordCheckpoint(final StepResult step, final String checkpointName) throws IOException {
		final String fileName = checkpointName + ".png";
		final Path screenshotPath = runOutputDir.resolve(fileName);
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
		step.evidencePaths.add(screenshotPath.toString());
		step.info("Screenshot: " + screenshotPath);
	}

	private void recordFailureCheckpoint(final StepResult step) {
		try {
			recordCheckpoint(step, "failure-" + slugify(step.name));
		} catch (Exception ignored) {
			step.info("Failure screenshot was not captured.");
		}
	}

	private Path writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Full Workflow Report");
		lines.add("Generated at: " + LocalDateTime.now());
		lines.add("Output folder: " + runOutputDir.toAbsolutePath());
		lines.add("");

		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.getOrDefault(field, StepResult.notExecuted(field));
			lines.add(field + ": " + (result.passed ? "PASS" : "FAIL"));
			for (final String detail : result.details) {
				lines.add("  - " + detail);
			}
			if (result.finalUrl != null) {
				lines.add("  - Final URL: " + result.finalUrl);
			}
			if (!result.evidencePaths.isEmpty()) {
				lines.add("  - Evidence:");
				for (final String evidencePath : result.evidencePaths) {
					lines.add("    * " + evidencePath);
				}
			}
			lines.add("");
		}

		final Path reportPath = runOutputDir.resolve("final-report.txt");
		Files.write(reportPath, lines);
		return reportPath;
	}

	private String buildSummary(final Path reportPath) {
		final StringBuilder sb = new StringBuilder("SaleADS workflow validation failed. ");
		sb.append("Report: ").append(reportPath.toAbsolutePath()).append(System.lineSeparator());
		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.getOrDefault(field, StepResult.notExecuted(field));
			sb.append("- ").append(field).append(": ").append(result.passed ? "PASS" : "FAIL").append(System.lineSeparator());
		}
		return sb.toString();
	}

	private void waitForUiLoad() {
		wait.until((ExpectedCondition<Boolean>) d -> {
			if (d == null) {
				return false;
			}
			final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(state) || "interactive".equals(state);
		});

		try {
			Thread.sleep(POST_CLICK_SETTLE.toMillis());
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private WebElement clickVisibleText(final String... texts) {
		final By locator = By.xpath("//*[(self::a or self::button or self::span or self::div or self::li or self::p) and ("
				+ textContainsAnyClause(texts) + ")]");
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));

		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		} catch (Exception ignored) {
			// scrolling fallback is non-critical.
		}

		try {
			element.click();
		} catch (Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiLoad();
		return element;
	}

	private boolean isTextVisible(final Duration timeout, final String... texts) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[" + textContainsAnyClause(texts) + "]")
			));
			return true;
		} catch (TimeoutException ignored) {
			return false;
		}
	}

	private boolean isElementVisible(final By locator) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (TimeoutException ignored) {
			return false;
		}
	}

	private WebElement findBusinessNameInput() {
		try {
			final List<WebElement> labels = driver.findElements(By.xpath(
					"//label[contains(normalize-space(.), " + toXpathLiteral("Nombre del Negocio") + ")]"
			));
			if (!labels.isEmpty()) {
				final WebElement label = labels.get(0);
				final String forAttr = label.getAttribute("for");
				if (forAttr != null && !forAttr.isBlank()) {
					return driver.findElement(By.id(forAttr));
				}
				final List<WebElement> nearbyInputs = label.findElements(By.xpath(
						"./ancestor::*[self::form or self::div][1]//input"
				));
				if (!nearbyInputs.isEmpty()) {
					return nearbyInputs.get(0);
				}
			}

			final List<WebElement> inputByPlaceholder = driver.findElements(By.xpath(
					"//input[contains(@placeholder, " + toXpathLiteral("Nombre del Negocio")
							+ ") or contains(@aria-label, " + toXpathLiteral("Nombre del Negocio") + ")]"
			));
			return inputByPlaceholder.isEmpty() ? null : inputByPlaceholder.get(0);
		} catch (NoSuchElementException ignored) {
			return null;
		}
	}

	private WebElement findSectionByHeading(final String headingText) {
		try {
			final WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[(self::h1 or self::h2 or self::h3 or self::h4 or self::p or self::span) and contains(normalize-space(.), "
							+ toXpathLiteral(headingText) + ")]")
			));
			return heading.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		} catch (Exception ignored) {
			return null;
		}
	}

	private boolean isTextVisibleInside(final WebElement scope, final String text) {
		try {
			final List<WebElement> matches = scope.findElements(By.xpath(".//*[contains(normalize-space(.), " + toXpathLiteral(text) + ")]"));
			return !matches.isEmpty();
		} catch (Exception ignored) {
			return false;
		}
	}

	private boolean hasLikelyUserName(final WebElement section) {
		try {
			final List<WebElement> candidates = section.findElements(By.xpath(
					".//*[(self::h1 or self::h2 or self::h3 or self::h4 or self::strong or self::p)"
							+ " and string-length(normalize-space(.)) > 2 and not(contains(normalize-space(.), '@'))]"
			));

			for (final WebElement candidate : candidates) {
				final String text = normalizeText(candidate.getText());
				if (text.isEmpty()) {
					continue;
				}
				final String upper = text.toUpperCase(Locale.ROOT);
				if (upper.contains("INFORMACIÓN GENERAL")
						|| upper.contains("INFORMACION GENERAL")
						|| upper.contains("BUSINESS PLAN")
						|| upper.contains("CAMBIAR PLAN")) {
					continue;
				}
				return true;
			}
			return false;
		} catch (Exception ignored) {
			return false;
		}
	}

	private String waitForNewWindow(final Set<String> handlesBeforeClick, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(d -> d != null && d.getWindowHandles().size() > handlesBeforeClick.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBeforeClick.contains(handle)) {
					return handle;
				}
			}
			return null;
		} catch (TimeoutException ignored) {
			return null;
		}
	}

	private void switchToMostRelevantApplicationWindow() {
		final List<String> handles = new ArrayList<>(driver.getWindowHandles());
		for (final String handle : handles) {
			driver.switchTo().window(handle);
			final String url = normalizeText(driver.getCurrentUrl()).toLowerCase(Locale.ROOT);
			if (!url.contains("accounts.google.com")) {
				appWindowHandle = handle;
				return;
			}
		}
		if (!handles.isEmpty()) {
			driver.switchTo().window(handles.get(0));
			appWindowHandle = handles.get(0);
		}
	}

	private String resolveAppWindowHandle() {
		final Set<String> handles = driver.getWindowHandles();
		if (appWindowHandle != null && handles.contains(appWindowHandle)) {
			return appWindowHandle;
		}
		if (!handles.isEmpty()) {
			appWindowHandle = handles.iterator().next();
			return appWindowHandle;
		}
		throw new IllegalStateException("No browser window handle is available.");
	}

	private String textContainsAnyClause(final String... texts) {
		final StringBuilder clause = new StringBuilder();
		for (int i = 0; i < texts.length; i++) {
			if (i > 0) {
				clause.append(" or ");
			}
			clause.append("contains(normalize-space(.), ").append(toXpathLiteral(texts[i])).append(")");
		}
		return clause.toString();
	}

	private String toXpathLiteral(final String value) {
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
				sb.append(", \"'\", ");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	private String normalizeText(final String text) {
		return text == null ? "" : text.trim();
	}

	private String slugify(final String value) {
		final String lower = normalizeText(value).toLowerCase(Locale.ROOT);
		return lower.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String safeMessage(final Exception ex) {
		return ex.getMessage() == null ? "(no message)" : ex.getMessage();
	}

	private String readConfig(final String key, final String defaultValue) {
		final String fromProperty = System.getProperty(key);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}

		final String envName = key.toUpperCase(Locale.ROOT).replace('.', '_');
		final String fromEnv = System.getenv(envName);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		return defaultValue;
	}

	@FunctionalInterface
	private interface StepExecutor {
		void execute(StepResult step) throws Exception;
	}

	private static final class StepResult {
		private final String name;
		private boolean passed = true;
		private final List<String> details = new ArrayList<>();
		private final List<String> evidencePaths = new ArrayList<>();
		private String finalUrl;

		private StepResult(final String name) {
			this.name = name;
		}

		private static StepResult notExecuted(final String name) {
			final StepResult result = new StepResult(name);
			result.passed = false;
			result.details.add("FAIL: Step was not executed.");
			return result;
		}

		private void check(final boolean condition, final String validationLabel) {
			if (condition) {
				details.add("PASS: " + validationLabel);
			} else {
				passed = false;
				details.add("FAIL: " + validationLabel);
			}
		}

		private void pass(final String message) {
			details.add("PASS: " + message);
		}

		private void fail(final String message) {
			passed = false;
			details.add("FAIL: " + message);
		}

		private void info(final String message) {
			details.add("INFO: " + message);
		}
	}
}
