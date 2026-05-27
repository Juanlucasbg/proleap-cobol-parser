package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

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

/**
 * End-to-end workflow for SaleADS "Mi Negocio" module.
 *
 * Environment variables:
 * - RUN_SALEADS_E2E=true (required to run)
 * - SALEADS_START_URL=https://... (required, environment-agnostic)
 * - SALEADS_HEADLESS=true|false (optional, default true)
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Path reportPath;
	private final Map<String, StepResult> results = new LinkedHashMap<>();

	@Before
	public void setup() throws IOException {
		Assume.assumeTrue("Set RUN_SALEADS_E2E=true to execute this E2E workflow.",
				"true".equalsIgnoreCase(System.getenv("RUN_SALEADS_E2E")));

		final String startUrl = System.getenv("SALEADS_START_URL");
		Assume.assumeTrue("Set SALEADS_START_URL to the SaleADS login page URL of your current environment.",
				startUrl != null && !startUrl.isBlank());

		final boolean headless = !"false".equalsIgnoreCase(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(25));

		final String runStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Path.of("target", "saleads-evidence", runStamp);
		Files.createDirectories(evidenceDir);
		reportPath = evidenceDir.resolve("final-report.md");

		driver.get(startUrl);
		waitForUiLoad();
	}

	@After
	public void cleanup() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepAdministrarNegocios);
		runStep("Informacion General", this::stepInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepDetallesCuenta);
		runStep("Tus Negocios", this::stepTusNegocios);
		runStep("Terminos y Condiciones", this::stepTerminosYCondiciones);
		runStep("Politica de Privacidad", this::stepPoliticaPrivacidad);

		writeFinalReport();
		assertTrue("One or more SaleADS validations failed. Review " + reportPath.toAbsolutePath(), allStepsPassed());
	}

	private void stepLoginWithGoogle(final StepResult step) throws Exception {
		clickFirstVisibleText(List.of("Sign in with Google", "Iniciar sesion con Google", "Continuar con Google",
				"Ingresar con Google", "Login with Google"), "Google sign-in button");
		waitForUiLoad();

		if (isVisibleText(GOOGLE_EMAIL, 8)) {
			clickText(GOOGLE_EMAIL);
			waitForUiLoad();
		}

		waitForAnyVisibleText(60, List.of("Negocio", "Mi Negocio", "Dashboard"));
		requireAnyVisible(step, "Main application interface appears", List.of("Negocio", "Mi Negocio", "Dashboard"));
		requireAnyVisible(step, "Left sidebar navigation is visible",
				List.of("Negocio", "Mi Negocio", "Administrar Negocios"));
		step.addEvidence("dashboard", screenshot("01-dashboard-loaded"));
	}

	private void stepMiNegocioMenu(final StepResult step) throws Exception {
		clickFirstVisibleText(List.of("Negocio", "Mi Negocio"), "Negocio/Mi Negocio menu");
		waitForUiLoad();

		requireVisible(step, "Mi Negocio submenu expands", "Mi Negocio");
		requireVisible(step, "Agregar Negocio visible", "Agregar Negocio");
		requireVisible(step, "Administrar Negocios visible", "Administrar Negocios");
		step.addEvidence("expanded_menu", screenshot("02-mi-negocio-expanded"));
	}

	private void stepAgregarNegocioModal(final StepResult step) throws Exception {
		clickText("Agregar Negocio");
		waitForUiLoad();

		requireVisible(step, "Modal title visible", "Crear Nuevo Negocio");
		requireVisible(step, "Nombre del Negocio input exists", "Nombre del Negocio");
		requireVisible(step, "Tienes 2 de 3 negocios visible", "Tienes 2 de 3 negocios");
		requireVisible(step, "Cancelar button present", "Cancelar");
		requireVisible(step, "Crear Negocio button present", "Crear Negocio");
		step.addEvidence("modal", screenshot("03-agregar-negocio-modal"));

		findBusinessNameInput().ifPresent(input -> {
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatizacion");
		});

		if (isVisibleText("Cancelar", 3)) {
			clickText("Cancelar");
			waitForUiLoad();
		}
	}

	private void stepAdministrarNegocios(final StepResult step) throws Exception {
		if (!isVisibleText("Administrar Negocios", 4) && isVisibleText("Mi Negocio", 4)) {
			clickText("Mi Negocio");
			waitForUiLoad();
		}

		clickText("Administrar Negocios");
		waitForUiLoad();

		requireVisible(step, "Informacion General section exists", "Informacion General");
		requireVisible(step, "Detalles de la Cuenta section exists", "Detalles de la Cuenta");
		requireVisible(step, "Tus Negocios section exists", "Tus Negocios");
		requireVisible(step, "Seccion Legal section exists", "Seccion Legal");
		step.addEvidence("account_page", screenshot("04-administrar-negocios"));
	}

	private void stepInformacionGeneral(final StepResult step) {
		requireAnyVisible(step, "User name visible", List.of("Nombre", "Usuario", "Perfil"));
		requireAnyVisible(step, "User email visible", List.of("@gmail.com", "@"));
		requireVisible(step, "BUSINESS PLAN visible", "BUSINESS PLAN");
		requireVisible(step, "Cambiar Plan button visible", "Cambiar Plan");
	}

	private void stepDetallesCuenta(final StepResult step) {
		requireVisible(step, "Cuenta creada visible", "Cuenta creada");
		requireAnyVisible(step, "Estado activo visible", List.of("Estado activo", "Activo"));
		requireVisible(step, "Idioma seleccionado visible", "Idioma seleccionado");
	}

	private void stepTusNegocios(final StepResult step) {
		requireVisible(step, "Business list visible", "Tus Negocios");
		requireVisible(step, "Agregar Negocio button exists", "Agregar Negocio");
		requireVisible(step, "Tienes 2 de 3 negocios visible", "Tienes 2 de 3 negocios");
	}

	private void stepTerminosYCondiciones(final StepResult step) throws Exception {
		validateLegalLink(step, "Terminos y Condiciones", "Terminos y Condiciones", "08-terminos");
	}

	private void stepPoliticaPrivacidad(final StepResult step) throws Exception {
		validateLegalLink(step, "Politica de Privacidad", "Politica de Privacidad", "09-politica-privacidad");
	}

	private void validateLegalLink(final StepResult step, final String linkText, final String headingText,
			final String screenshotName) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final String previousUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickText(linkText);
		waitForUiLoad();

		String targetHandle = appHandle;
		if (waitForCondition(12, () -> driver.getWindowHandles().size() > handlesBefore.size())) {
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					targetHandle = handle;
					break;
				}
			}
			driver.switchTo().window(targetHandle);
			waitForUiLoad();
		} else if (previousUrl.equals(driver.getCurrentUrl())) {
			waitForUiLoad();
		}

		requireVisible(step, "Heading visible", headingText);
		final boolean legalContentVisible = driver.findElements(By.xpath("//main//*[normalize-space()] | //body//*[normalize-space()]"))
				.size() > 10;
		if (legalContentVisible) {
			step.addPass("Legal content text visible");
		} else {
			step.addFailure("Legal content text visible");
		}

		final String finalUrl = driver.getCurrentUrl();
		step.addPass("Final URL: " + finalUrl);
		step.addEvidence("legal_page", screenshot(screenshotName));

		if (!targetHandle.equals(appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else if (!previousUrl.equals(finalUrl)) {
			driver.navigate().back();
		}
		waitForUiLoad();
	}

	private void runStep(final String name, final StepAction action) {
		final StepResult step = new StepResult(name);
		results.put(name, step);

		try {
			action.execute(step);
		} catch (final Exception ex) {
			step.addFailure("Unhandled exception: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
		}
	}

	private boolean allStepsPassed() {
		return results.values().stream().allMatch(StepResult::isPassed);
	}

	private void requireVisible(final StepResult step, final String description, final String visibleText) {
		if (isVisibleText(visibleText, 12)) {
			step.addPass(description);
		} else {
			step.addFailure(description + " (text not found: " + visibleText + ")");
		}
	}

	private void requireAnyVisible(final StepResult step, final String description, final List<String> options) {
		for (final String option : options) {
			if (isVisibleText(option, 6)) {
				step.addPass(description + " [" + option + "]");
				return;
			}
		}
		step.addFailure(description + " (none found: " + String.join(", ", options) + ")");
	}

	private void clickText(final String text) throws Exception {
		clickFirstVisibleText(List.of(text), text);
	}

	private void clickFirstVisibleText(final List<String> texts, final String targetDescription) throws Exception {
		for (final String text : texts) {
			final Optional<WebElement> candidate = findVisibleElement(text, 5);
			if (candidate.isPresent()) {
				clickElement(candidate.get());
				waitForUiLoad();
				return;
			}
		}
		throw new TimeoutException("Unable to click " + targetDescription + ". Tried: " + String.join(", ", texts));
	}

	private Optional<WebElement> findVisibleElement(final String text, final int timeoutSeconds) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
			return Optional.of(shortWait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text))));
		} catch (final TimeoutException ex) {
			return Optional.empty();
		}
	}

	private Optional<WebElement> findBusinessNameInput() {
		final List<By> candidates = Arrays.asList(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[@aria-label='Nombre del Negocio']"),
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"));

		for (final By by : candidates) {
			final List<WebElement> matches = driver.findElements(by);
			for (final WebElement match : matches) {
				if (match.isDisplayed()) {
					return Optional.of(match);
				}
			}
		}
		return Optional.empty();
	}

	private boolean isVisibleText(final String text, final int timeoutSeconds) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
			shortWait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void waitForAnyVisibleText(final int timeoutSeconds, final List<String> texts) {
		waitForCondition(timeoutSeconds, () -> texts.stream().anyMatch(text -> isVisibleText(text, 1)));
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private By byVisibleText(final String text) {
		final String escaped = escapeXpathLiteral(text);
		final String exact = "//*[normalize-space()=" + escaped + "]";
		final String contains = "//*[contains(normalize-space(), " + escaped + ")]";
		return By.xpath(exact + " | " + contains);
	}

	private String escapeXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	private void waitForUiLoad() {
		try {
			wait.until(driver -> "complete"
					.equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// SPA transitions may not update readyState.
		}

		try {
			Thread.sleep(700);
		} catch (final InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean waitForCondition(final int timeoutSeconds, final Supplier<Boolean> condition) {
		final long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
		while (System.currentTimeMillis() < deadline) {
			try {
				if (condition.get()) {
					return true;
				}
			} catch (final Exception ignored) {
				// Keep polling until timeout.
			}

			try {
				Thread.sleep(500);
			} catch (final InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	private String screenshot(final String baseName) throws IOException {
		final String fileName = baseName + ".png";
		final Path output = evidenceDir.resolve(fileName);
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
		return output.toString();
	}

	private void writeFinalReport() throws IOException {
		if (reportPath == null) {
			return;
		}

		final List<String> lines = new ArrayList<>();
		lines.add("# SaleADS Mi Negocio Full Test Report");
		lines.add("");
		lines.add("- Generated at: " + LocalDateTime.now());
		lines.add("- Overall result: " + (allStepsPassed() ? "PASS" : "FAIL"));
		lines.add("");
		lines.add("## Step Results");
		lines.add("");

		for (final StepResult step : results.values()) {
			lines.add("### " + step.name + ": " + (step.isPassed() ? "PASS" : "FAIL"));
			for (final String pass : step.passes) {
				lines.add("- PASS: " + pass);
			}
			for (final String failure : step.failures) {
				lines.add("- FAIL: " + failure);
			}
			for (final Map.Entry<String, String> evidence : step.evidence.entrySet()) {
				lines.add("- Evidence (" + evidence.getKey() + "): " + evidence.getValue());
			}
			lines.add("");
		}

		Files.write(reportPath, lines);
	}

	@FunctionalInterface
	private interface StepAction {
		void execute(StepResult step) throws Exception;
	}

	private static class StepResult {
		private final String name;
		private final List<String> passes = new ArrayList<>();
		private final List<String> failures = new ArrayList<>();
		private final Map<String, String> evidence = new LinkedHashMap<>();

		private StepResult(final String name) {
			this.name = name;
		}

		private void addPass(final String message) {
			passes.add(message);
		}

		private void addFailure(final String message) {
			failures.add(message);
		}

		private void addEvidence(final String label, final String path) {
			evidence.put(label, path);
		}

		private boolean isPassed() {
			return failures.isEmpty();
		}
	}
}
