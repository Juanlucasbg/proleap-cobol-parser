package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter EVIDENCE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private String appWindowHandle;

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();

	private interface StepAction {
		String run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}
	}

	@Before
	public void setUp() throws IOException {
		final String loginUrl = environment("SALEADS_LOGIN_URL", "");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the login page URL of the active environment.",
				loginUrl != null && !loginUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		if (Boolean.parseBoolean(environment("SALEADS_HEADLESS", "false"))) {
			options.addArguments("--headless=new");
		}

		final String userDataDir = environment("SALEADS_CHROME_USER_DATA_DIR", "");
		if (!userDataDir.isBlank()) {
			options.addArguments("--user-data-dir=" + userDataDir);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(Integer.parseInt(environment("SALEADS_WAIT_SECONDS", "40"))));
		driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));

		evidenceDirectory = Paths.get("target", "saleads-evidence", EVIDENCE_TS.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDirectory);

		driver.get(loginUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		try {
			printFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		final String googleAccount = environment("SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);

		runStep("Login", () -> loginWithGoogle(googleAccount));
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegocios);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", () -> validateLegalDocument("Términos y Condiciones", "08-terminos.png"));
		runStep("Política de Privacidad", () -> validateLegalDocument("Política de Privacidad", "09-politica-privacidad.png"));

		assertNoFailures();
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			final String detail = action.run();
			stepResults.put(stepName, new StepResult(true, detail == null || detail.isBlank() ? "PASS" : detail));
		} catch (final Throwable error) {
			final String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
			stepResults.put(stepName, new StepResult(false, detail));
		}
	}

	private String loginWithGoogle(final String accountEmail) throws IOException {
		clickAnyText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiToLoad();

		handleGoogleAccountSelection(accountEmail);

		waitForSidebar();
		saveScreenshot("01-dashboard-loaded.png");
		appWindowHandle = driver.getWindowHandle();
		return "Dashboard y sidebar visibles";
	}

	private void handleGoogleAccountSelection(final String accountEmail) {
		final String initialHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeSelection = driver.getWindowHandles();

		try {
			wait.until(drv -> drv.getWindowHandles().size() > handlesBeforeSelection.size() || isTextVisible(accountEmail));
		} catch (final TimeoutException timeoutException) {
			// Account selector is optional if the Google session is already active.
		}

		final Set<String> handlesAfterSelection = driver.getWindowHandles();
		if (handlesAfterSelection.size() > handlesBeforeSelection.size()) {
			for (final String handle : handlesAfterSelection) {
				if (!handlesBeforeSelection.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		if (isTextVisible(accountEmail)) {
			clickByText(accountEmail, false);
			waitForUiToLoad();
		}

		try {
			wait.until(drv -> {
				for (final String handle : drv.getWindowHandles()) {
					drv.switchTo().window(handle);
					if (isTextVisible("Negocio")) {
						appWindowHandle = handle;
						return true;
					}
				}
				return false;
			});
		} catch (final TimeoutException timeoutException) {
			driver.switchTo().window(initialHandle);
		}

		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private String openMiNegocioMenu() throws IOException {
		waitForSidebar();

		clickIfPresent("Negocio");
		clickAnyText("Mi Negocio");

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		saveScreenshot("02-mi-negocio-menu-expandido.png");
		return "Submenú expandido con opciones requeridas";
	}

	private String validateAgregarNegocioModal() throws IOException {
		clickAnyText("Agregar Negocio");
		waitForVisibleText("Crear Nuevo Negocio");
		waitForVisibleText("Nombre del Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");

		final List<WebElement> modalInputs = driver.findElements(By.xpath(
				"//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]/ancestor::*[self::div or self::section][1]//input"));
		Assert.assertTrue("El modal debe contener un input para el nombre del negocio.", !modalInputs.isEmpty());

		saveScreenshot("03-agregar-negocio-modal.png");

		WebElement nombreNegocioInput = null;
		final List<WebElement> namedInputs = driver.findElements(By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"));
		if (!namedInputs.isEmpty()) {
			nombreNegocioInput = namedInputs.get(0);
		} else if (!modalInputs.isEmpty()) {
			nombreNegocioInput = modalInputs.get(0);
		}

		if (nombreNegocioInput != null) {
			nombreNegocioInput.click();
			nombreNegocioInput.clear();
			nombreNegocioInput.sendKeys("Negocio Prueba Automatización");
		}

		clickAnyText("Cancelar");
		waitUntilTextGone("Crear Nuevo Negocio");

		return "Modal validado y cerrado con Cancelar";
	}

	private String openAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickIfPresent("Mi Negocio");
		}

		clickAnyText("Administrar Negocios");
		waitForVisibleText("Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal");

		saveScreenshot("04-administrar-negocios.png");
		return "Vista de cuenta cargada con todas las secciones";
	}

	private String validateInformacionGeneral() {
		assertAnyElementVisible(By.xpath(
				"//*[contains(@class,'user') or contains(@class,'profile') or contains(normalize-space(.), '@')]"),
				"No se encontró bloque de usuario/email visible.");
		waitForVisibleText("BUSINESS PLAN");
		waitForVisibleText("Cambiar Plan");
		return "Nombre/email, plan y botón Cambiar Plan visibles";
	}

	private String validateDetallesCuenta() {
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
		return "Sección Detalles de la Cuenta visible";
	}

	private String validateTusNegocios() {
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
		assertAnyElementVisible(By.xpath("//*[contains(@class,'business') or contains(@class,'negocio')]"),
				"No se encontró lista visible de negocios.");
		return "Listado y capacidad de negocios visibles";
	}

	private String validateLegalDocument(final String linkText, final String screenshotFile) throws IOException {
		waitForVisibleText("Sección Legal");
		final String originalHandle = appWindowHandle != null ? appWindowHandle : driver.getWindowHandle();
		driver.switchTo().window(originalHandle);
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String currentUrlBeforeClick = driver.getCurrentUrl();

		clickByText(linkText, false);

		String activeHandle = originalHandle;
		try {
			wait.until(drv -> drv.getWindowHandles().size() > handlesBefore.size()
					|| !drv.getCurrentUrl().equals(currentUrlBeforeClick));
		} catch (final TimeoutException timeoutException) {
			throw new AssertionError("No hubo navegación ni apertura de pestaña para: " + linkText);
		}

		final Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > handlesBefore.size()) {
			for (final String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					activeHandle = handle;
					break;
				}
			}
			driver.switchTo().window(activeHandle);
		}

		waitForUiToLoad();
		waitForVisibleText(linkText);
		saveScreenshot(screenshotFile);
		final String finalUrl = driver.getCurrentUrl();

		if (!originalHandle.equals(activeHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return "URL final: " + finalUrl;
	}

	private void clickAnyText(final String... candidateTexts) {
		for (final String text : candidateTexts) {
			if (isTextVisible(text)) {
				clickByText(text, true);
				return;
			}
		}

		final StringJoiner joiner = new StringJoiner(", ");
		for (final String candidate : candidateTexts) {
			joiner.add(candidate);
		}
		throw new AssertionError("No se encontró ningún elemento visible con texto: " + joiner);
	}

	private void clickIfPresent(final String text) {
		if (isTextVisible(text)) {
			clickByText(text, true);
		}
	}

	private void clickByText(final String text, final boolean waitForLoad) {
		final WebElement element = waitForVisibleText(text);
		try {
			new Actions(driver).moveToElement(element).pause(Duration.ofMillis(120)).click(element).perform();
		} catch (final Exception nativeClickFailed) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		if (waitForLoad) {
			waitForUiToLoad();
		}
	}

	private WebElement waitForVisibleText(final String text) {
		final By textLocator = By.xpath("//*[normalize-space(.)=" + xpathLiteral(text) + "]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(textLocator));
	}

	private void waitUntilTextGone(final String text) {
		final By textLocator = By.xpath("//*[normalize-space(.)=" + xpathLiteral(text) + "]");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(textLocator));
	}

	private void waitForSidebar() {
		final List<By> sidebarCandidates = new ArrayList<>();
		sidebarCandidates.add(By.tagName("aside"));
		sidebarCandidates.add(By.xpath("//*[contains(@class,'sidebar')]"));
		sidebarCandidates.add(By.xpath("//*[contains(@class,'menu')]"));

		boolean found = false;
		for (final By locator : sidebarCandidates) {
			if (isElementVisible(locator)) {
				found = true;
				break;
			}
		}

		if (!found) {
			throw new AssertionError("No se detectó la barra lateral de navegación.");
		}

		waitForVisibleText("Negocio");
	}

	private boolean isTextVisible(final String text) {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[normalize-space(.)=" + xpathLiteral(text) + "]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean isElementVisible(final By locator) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void assertAnyElementVisible(final By locator, final String message) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return;
			}
		}
		throw new AssertionError(message);
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));

		final By[] loadingSelectors = new By[] { By.xpath("//*[contains(@class,'loading')]"),
				By.xpath("//*[contains(@class,'spinner')]"), By.xpath("//*[contains(@class,'progress')]") };

		for (final By selector : loadingSelectors) {
			try {
				wait.until(ExpectedConditions.invisibilityOfElementLocated(selector));
			} catch (final TimeoutException timeoutException) {
				// Ignore generic loading selectors that are not used by this environment.
			}
		}
	}

	private void saveScreenshot(final String filename) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDirectory.resolve(filename);
		Files.copy(screenshot.toPath(), target);
	}

	private String environment(final String name, final String fallback) {
		final String value = System.getenv(name);
		return value == null ? fallback : value.trim();
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(",\"'\",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private void assertNoFailures() {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			if (!entry.getValue().passed) {
				failedSteps.add(entry.getKey() + " -> " + entry.getValue().details);
			}
		}

		if (!failedSteps.isEmpty()) {
			final StringJoiner joiner = new StringJoiner(" | ");
			for (final String failedStep : failedSteps) {
				joiner.add(failedStep);
			}
			Assert.fail("Workflow con fallos: " + joiner);
		}
	}

	private void printFinalReport() {
		if (stepResults.isEmpty()) {
			return;
		}

		System.out.println("===== SaleADS Mi Negocio Full Test Report =====");
		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			final String status = entry.getValue().passed ? "PASS" : "FAIL";
			System.out.println(entry.getKey() + ": " + status + " | " + entry.getValue().details);
		}
		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
		System.out.println("================================================");
	}
}
