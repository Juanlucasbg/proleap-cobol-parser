package io.proleap.cobol.e2e.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end UI test for the SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * This test intentionally avoids hardcoding any domain and reads the login URL
 * from either:
 * </p>
 *
 * <ul>
 * <li>system property: saleads.baseUrl</li>
 * <li>environment variable: SALEADS_BASE_URL</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration FAST_TIMEOUT = Duration.ofSeconds(8);
	private static final String REPORT_FILE_NAME = "saleads_mi_negocio_full_test_report.json";

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_DETALLES = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Política de Privacidad";

	private final Map<String, StepStatus> statusByStep = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	@Before
	public void setUp() throws IOException {
		final String baseUrl = firstNonBlank(System.getProperty("saleads.baseUrl"), System.getenv("SALEADS_BASE_URL"));
		Assume.assumeTrue(
				"Set saleads.baseUrl or SALEADS_BASE_URL with the SaleADS login page URL of the current environment.",
				baseUrl != null);

		final String evidenceFolder = "target/saleads-evidence/" + TS_FORMATTER.format(Instant.now());
		evidenceDir = Path.of(evidenceFolder);
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		driver.get(baseUrl);
		waitForUiToSettle();
	}

	@After
	public void tearDown() throws IOException {
		writeReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		initializeReport();

		runStep(STEP_LOGIN, this::stepLoginWithGoogle);
		runStep(STEP_MENU, this::stepOpenMiNegocioMenu);
		runStep(STEP_AGREGAR_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(STEP_ADMINISTRAR, this::stepOpenAdministrarNegocios);
		runStep(STEP_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(STEP_DETALLES, this::stepValidateDetallesCuenta);
		runStep(STEP_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(STEP_TERMINOS, () -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos"));
		runStep(STEP_PRIVACIDAD, () -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09-politica-privacidad"));

		if (!failures.isEmpty()) {
			Assert.fail("One or more validations failed:\n - " + String.join("\n - ", failures));
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		clickByAnyVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiToSettle();
		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");

		assertAnyVisibleText("left sidebar", "Negocio", "Mi Negocio", "Dashboard");
		assertVisible(By.xpath("//aside | //nav"), "left sidebar navigation");
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByAnyVisibleText("Mi Negocio", "Negocio");
		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final WebElement nameField = wait.until(ExpectedConditions
				.visibilityOfElementLocated(By.xpath("//input[@placeholder='Nombre del Negocio' or @name='nombreNegocio' or @type='text']")));
		nameField.click();
		nameField.sendKeys("Negocio Prueba Automatización");

		takeScreenshot("03-agregar-negocio-modal");
		clickByVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioMenuIfNeeded();
		clickByVisibleText("Administrar Negocios");
		waitForUiToSettle();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertAnyVisibleText("legal section", "Sección Legal", "Términos y Condiciones");

		takeScreenshot("04-administrar-negocios-full");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisibleText("user name", "Juan", "Lucas");
		assertVisible(By.xpath("//*[contains(normalize-space(.), '@')]"), "user email");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertAnyVisibleText("Estado activo", "Estado activo", "Activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String urlBefore = driver.getCurrentUrl();

		clickByVisibleText(linkText);

		final String targetHandle = waitForPossiblyNewWindow(handlesBefore, originalHandle, urlBefore);
		final boolean newTabOpened = !Objects.equals(targetHandle, originalHandle);

		if (newTabOpened) {
			driver.switchTo().window(targetHandle);
		}

		waitForUiToSettle();
		assertVisibleText(expectedHeading);
		assertVisible(By.xpath("//main//*[normalize-space(text())!=''] | //article//*[normalize-space(text())!=''] | //body//*[normalize-space(text())!='']"),
				"legal content text");

		final String finalUrl = driver.getCurrentUrl();
		addStepInfo(linkText, "Final URL: " + finalUrl);
		takeScreenshot(screenshotName);

		if (newTabOpened) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToSettle();
		} else if (!Objects.equals(urlBefore, finalUrl)) {
			driver.navigate().back();
			waitForUiToSettle();
		}
	}

	private String waitForPossiblyNewWindow(final Set<String> handlesBefore, final String originalHandle, final String urlBefore) {
		final WebDriverWait shortWait = new WebDriverWait(driver, FAST_TIMEOUT);
		return shortWait.until((ExpectedCondition<String>) d -> {
			final Set<String> handlesNow = d.getWindowHandles();
			if (handlesNow.size() > handlesBefore.size()) {
				for (final String handle : handlesNow) {
					if (!handlesBefore.contains(handle)) {
						return handle;
					}
				}
			}
			if (!Objects.equals(urlBefore, d.getCurrentUrl())) {
				return originalHandle;
			}
			return null;
		});
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		final List<By> accountSelectors = List.of(
				By.xpath("//*[normalize-space(text())='" + accountEmail + "']"),
				By.xpath("//div[@data-email='" + accountEmail + "']"));

		for (final By selector : accountSelectors) {
			final List<WebElement> matches = driver.findElements(selector);
			if (!matches.isEmpty() && matches.get(0).isDisplayed()) {
				matches.get(0).click();
				waitForUiToSettle();
				return;
			}
		}
	}

	private void expandMiNegocioMenuIfNeeded() {
		if (!isTextVisible("Administrar Negocios") || !isTextVisible("Agregar Negocio")) {
			clickByVisibleText("Mi Negocio");
		}
	}

	private void clickByAnyVisibleText(final String... labels) {
		RuntimeException lastError = null;
		for (final String label : labels) {
			try {
				clickByVisibleText(label);
				return;
			} catch (final RuntimeException ex) {
				lastError = ex;
			}
		}
		throw new AssertionError("Unable to click any of these labels: " + String.join(", ", labels), lastError);
	}

	private void clickByVisibleText(final String label) {
		final By locator = textLocator(label);
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
		element.click();
		waitForUiToSettle();
	}

	private void waitForUiToSettle() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));

		// jQuery is optional; this check becomes a no-op when jQuery does not exist.
		wait.until(d -> {
			final Object result = ((JavascriptExecutor) d)
					.executeScript("return (window.jQuery ? jQuery.active === 0 : true);");
			return Boolean.TRUE.equals(result);
		});
	}

	private void assertVisibleText(final String text) {
		assertVisible(textLocator(text), "text: " + text);
	}

	private void assertAnyVisibleText(final String elementName, final String... candidateTexts) {
		for (final String text : candidateTexts) {
			if (isTextVisible(text)) {
				return;
			}
		}
		throw new AssertionError("Expected " + elementName + " to contain one of: " + String.join(", ", candidateTexts));
	}

	private boolean isTextVisible(final String text) {
		for (final WebElement element : driver.findElements(textLocator(text))) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void assertVisible(final By locator, final String elementName) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final Exception ex) {
			throw new AssertionError("Expected visible element not found: " + elementName, ex);
		}
	}

	private By textLocator(final String text) {
		return By.xpath("//*[normalize-space()='" + text + "']");
	}

	private void takeScreenshot(final String name) throws IOException {
		final Path target = evidenceDir.resolve(name + ".png");
		final Path src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
	}

	private void runStep(final String stepName, final CheckedRunnable step) {
		try {
			step.run();
			final StepStatus current = statusByStep.get(stepName);
			final String details = current == null ? "" : current.details;
			statusByStep.put(stepName, new StepStatus(true, details));
		} catch (final Throwable ex) {
			final String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			statusByStep.put(stepName, StepStatus.fail(message));
			failures.add(stepName + ": " + message);
			try {
				takeScreenshot("failure-" + sanitize(stepName));
			} catch (final IOException ignored) {
				// best effort screenshot on failure
			}
		}
	}

	private void initializeReport() {
		statusByStep.put(STEP_LOGIN, StepStatus.fail("Not executed"));
		statusByStep.put(STEP_MENU, StepStatus.fail("Not executed"));
		statusByStep.put(STEP_AGREGAR_MODAL, StepStatus.fail("Not executed"));
		statusByStep.put(STEP_ADMINISTRAR, StepStatus.fail("Not executed"));
		statusByStep.put(STEP_INFO_GENERAL, StepStatus.fail("Not executed"));
		statusByStep.put(STEP_DETALLES, StepStatus.fail("Not executed"));
		statusByStep.put(STEP_TUS_NEGOCIOS, StepStatus.fail("Not executed"));
		statusByStep.put(STEP_TERMINOS, StepStatus.fail("Not executed"));
		statusByStep.put(STEP_PRIVACIDAD, StepStatus.fail("Not executed"));
	}

	private void addStepInfo(final String stepName, final String info) {
		final StepStatus current = statusByStep.getOrDefault(stepName, StepStatus.pass());
		statusByStep.put(stepName, current.withDetails(info));
	}

	private void writeReport() throws IOException {
		if (statusByStep.isEmpty()) {
			return;
		}
		final Path reportFile = evidenceDir.resolve(REPORT_FILE_NAME);
		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"generatedAtUtc\": \"").append(Instant.now().toString()).append("\",\n");
		json.append("  \"evidenceDir\": \"").append(escapeJson(evidenceDir.toString())).append("\",\n");
		json.append("  \"results\": {\n");

		int index = 0;
		for (final Map.Entry<String, StepStatus> entry : statusByStep.entrySet()) {
			final StepStatus value = entry.getValue();
			json.append("    \"").append(escapeJson(entry.getKey())).append("\": {\n");
			json.append("      \"status\": \"").append(value.passed ? "PASS" : "FAIL").append("\",\n");
			json.append("      \"details\": \"").append(escapeJson(value.details)).append("\"\n");
			json.append("    }");
			if (index < statusByStep.size() - 1) {
				json.append(",");
			}
			json.append("\n");
			index++;
		}
		json.append("  }\n");
		json.append("}\n");

		Files.writeString(reportFile, json.toString());
	}

	private String sanitize(final String value) {
		return value.replaceAll("[^a-zA-Z0-9\\-]+", "-").toLowerCase();
	}

	private String escapeJson(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	private String firstNonBlank(final String first, final String second) {
		if (first != null && !first.isBlank()) {
			return first.trim();
		}
		if (second != null && !second.isBlank()) {
			return second.trim();
		}
		return null;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static class StepStatus {
		private final boolean passed;
		private final String details;

		private StepStatus(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepStatus pass() {
			return new StepStatus(true, "");
		}

		private static StepStatus fail(final String details) {
			return new StepStatus(false, details == null ? "" : details);
		}

		private StepStatus withDetails(final String details) {
			return new StepStatus(this.passed, details == null ? this.details : details);
		}
	}
}
