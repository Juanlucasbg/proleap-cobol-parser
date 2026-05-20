package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws Exception {
		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(getConfig("SALEADS_HEADLESS", "saleads.headless", "true"))) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,2200", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
		driver = new ChromeDriver(options);

		final int timeoutSeconds = Integer.parseInt(getConfig("SALEADS_TIMEOUT_SECONDS", "saleads.timeout.seconds", "30"));
		wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(timeoutSeconds));

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDirectory = Files.createDirectories(Paths.get("target", "saleads-evidence", timestamp));

		final String loginUrl = getConfig("SALEADS_LOGIN_URL", "saleads.login.url", "");
		if (loginUrl.isBlank()) {
			throw new IllegalStateException(
					"SALEADS_LOGIN_URL is required. Use an environment-specific login URL (dev/staging/prod) without hardcoding domains in this test.");
		}

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStep("Login", this::loginWithGoogle);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegocios);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", () -> {
			termsUrl = validateLegalLink("Términos y Condiciones", "Terminos y Condiciones", "Términos y Condiciones",
					"08-terminos");
		});
		runStep("Política de Privacidad", () -> {
			privacyUrl = validateLegalLink("Política de Privacidad", "Politica de Privacidad", "Política de Privacidad",
					"09-politica-privacidad");
		});

		writeFinalReport();
		assertAllStepsPassed();
	}

	private void loginWithGoogle() throws Exception {
		clickFirstVisible(
				By.xpath("//button[contains(normalize-space(),'Google')]"),
				By.xpath("//a[contains(normalize-space(),'Google')]"),
				By.xpath("//*[contains(normalize-space(),'Sign in with Google')]"),
				By.xpath("//*[contains(normalize-space(),'Iniciar sesión con Google')]"),
				By.xpath("//*[contains(normalize-space(),'Iniciar sesion con Google')]"),
				By.xpath("//*[contains(normalize-space(),'Continuar con Google')]"));

		clickIfVisible(By.xpath("//*[contains(normalize-space(),'juanlucasbarbiergarzon@gmail.com')]"));

		waitForVisible(
				By.xpath("//*[contains(@class,'sidebar') and not(contains(@style,'display: none'))]"),
				By.xpath("//nav"),
				By.xpath("//*[contains(normalize-space(),'Negocio')]"));

		captureScreenshot("01-dashboard-cargado");
	}

	private void openMiNegocioMenu() throws Exception {
		clickIfVisible(By.xpath("//*[normalize-space()='Negocio']"));
		clickFirstVisible(
				By.xpath("//a[normalize-space()='Mi Negocio']"),
				By.xpath("//button[normalize-space()='Mi Negocio']"),
				By.xpath("//*[normalize-space()='Mi Negocio']"));

		waitForVisible(
				By.xpath("//*[normalize-space()='Agregar Negocio']"),
				By.xpath("//*[normalize-space()='Administrar Negocios']"));

		captureScreenshot("02-mi-negocio-menu-expandido");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickFirstVisible(
				By.xpath("//a[normalize-space()='Agregar Negocio']"),
				By.xpath("//button[normalize-space()='Agregar Negocio']"),
				By.xpath("//*[normalize-space()='Agregar Negocio']"));

		waitForVisible(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']"));
		waitForVisible(By.xpath("//input[contains(@placeholder,'Nombre del Negocio') or @name='businessName' or @id='businessName']"),
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"));
		waitForVisible(By.xpath("//*[contains(normalize-space(),'Tienes 2 de 3 negocios')]"));
		waitForVisible(By.xpath("//button[normalize-space()='Cancelar']"));
		waitForVisible(By.xpath("//button[contains(normalize-space(),'Crear Negocio')]"));

		captureScreenshot("03-agregar-negocio-modal");

		final WebElement nameField = firstVisibleElement(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio') or @name='businessName' or @id='businessName']"),
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"));
		nameField.click();
		nameField.clear();
		nameField.sendKeys("Negocio Prueba Automatizacion");
		waitForUiToLoad();

		clickFirstVisible(By.xpath("//button[normalize-space()='Cancelar']"));
		waitForUiToLoad();
	}

	private void openAdministrarNegocios() throws Exception {
		ensureMiNegocioMenuExpanded();

		clickFirstVisible(
				By.xpath("//a[normalize-space()='Administrar Negocios']"),
				By.xpath("//button[normalize-space()='Administrar Negocios']"),
				By.xpath("//*[normalize-space()='Administrar Negocios']"));

		waitForAnyVisible(
				By.xpath("//*[normalize-space()='Información General']"),
				By.xpath("//*[normalize-space()='Informacion General']"));
		waitForVisible(By.xpath("//*[normalize-space()='Detalles de la Cuenta']"));
		waitForVisible(By.xpath("//*[normalize-space()='Tus Negocios']"));
		waitForAnyVisible(
				By.xpath("//*[normalize-space()='Sección Legal']"),
				By.xpath("//*[normalize-space()='Seccion Legal']"));

		captureScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() throws Exception {
		final WebElement section = firstVisibleElement(
				By.xpath("//*[normalize-space()='Información General']/ancestor::*[self::section or self::div][1]"),
				By.xpath("//*[normalize-space()='Informacion General']/ancestor::*[self::section or self::div][1]"),
				By.xpath("//*[contains(normalize-space(),'Información General')]/ancestor::*[self::section or self::div][1]"),
				By.xpath("//*[contains(normalize-space(),'Informacion General')]/ancestor::*[self::section or self::div][1]"));

		waitForVisible(By.xpath("//*[contains(normalize-space(),'BUSINESS PLAN')]"));
		waitForVisible(By.xpath("//*[normalize-space()='Cambiar Plan']"));

		final String sectionText = section.getText();
		final Matcher matcher = EMAIL_PATTERN.matcher(sectionText);
		assertTrue("User email should be visible in Informacion General.", matcher.find());

		final String sanitizedText = sectionText.replace("Información General", "").replace("Informacion General", "")
				.replace("BUSINESS PLAN", "")
				.replace("Cambiar Plan", "").trim();
		assertTrue("User name should be visible in Informacion General.", sanitizedText.length() > 5);
	}

	private void validateDetallesCuenta() throws Exception {
		waitForVisible(By.xpath("//*[contains(normalize-space(),'Cuenta creada')]"));
		waitForVisible(By.xpath("//*[contains(normalize-space(),'Estado activo')]"));
		waitForVisible(By.xpath("//*[contains(normalize-space(),'Idioma seleccionado')]"));
	}

	private void validateTusNegocios() throws Exception {
		final WebElement section = firstVisibleElement(By.xpath("//*[normalize-space()='Tus Negocios']/ancestor::*[self::section or self::div][1]"),
				By.xpath("//*[contains(normalize-space(),'Tus Negocios')]/ancestor::*[self::section or self::div][1]"));

		waitForVisible(By.xpath("//*[normalize-space()='Agregar Negocio']"));
		waitForVisible(By.xpath("//*[contains(normalize-space(),'Tienes 2 de 3 negocios')]"));
		assertTrue("Business list should be visible in Tus Negocios.", section.getText().trim().length() > 20);
	}

	private String validateLegalLink(final String primaryLinkText, final String secondaryLinkText, final String expectedHeading,
			final String screenshotName) throws Exception {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> beforeWindows = driver.getWindowHandles();

		clickFirstVisible(
				By.xpath("//a[normalize-space()='" + primaryLinkText + "']"),
				By.xpath("//button[normalize-space()='" + primaryLinkText + "']"),
				By.xpath("//*[normalize-space()='" + primaryLinkText + "']"),
				By.xpath("//a[normalize-space()='" + secondaryLinkText + "']"),
				By.xpath("//button[normalize-space()='" + secondaryLinkText + "']"),
				By.xpath("//*[normalize-space()='" + secondaryLinkText + "']"));

		boolean openedNewTab = false;
		try {
			wait.until(drv -> drv.getWindowHandles().size() > beforeWindows.size());
			openedNewTab = true;
		} catch (final TimeoutException ignored) {
			openedNewTab = false;
		}

		if (openedNewTab) {
			final Set<String> afterWindows = driver.getWindowHandles();
			afterWindows.removeAll(beforeWindows);
			if (!afterWindows.isEmpty()) {
				driver.switchTo().window(afterWindows.iterator().next());
				waitForUiToLoad();
			}
		}

		final String expectedHeadingWithoutAccents = removeAccents(expectedHeading);
		waitForAnyVisible(
				By.xpath("//*[contains(normalize-space(),'" + expectedHeading + "')]"),
				By.xpath("//*[contains(normalize-space(),'" + expectedHeadingWithoutAccents + "')]"));
		waitForVisible(By.xpath("//body//*[string-length(normalize-space()) > 40]"));
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void ensureMiNegocioMenuExpanded() throws Exception {
		if (!isVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"), 2)) {
			clickIfVisible(By.xpath("//*[normalize-space()='Negocio']"));
			clickIfVisible(By.xpath("//*[normalize-space()='Mi Negocio']"));
			waitForVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"));
		}
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, StepResult.pass("OK"));
		} catch (final Throwable throwable) {
			report.put(stepName, StepResult.fail(throwable.getMessage() == null ? throwable.toString() : throwable.getMessage()));
			captureScreenshot("error-" + slugify(stepName));
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				failed.add(entry.getKey() + " => " + entry.getValue().details);
			}
		}

		assertTrue("Some SaleADS workflow steps failed: " + String.join(" | ", failed), failed.isEmpty());
	}

	private void writeFinalReport() throws Exception {
		final List<String> fields = Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad");

		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Workflow Final Report");
		lines.add("Evidence directory: " + evidenceDirectory.toAbsolutePath());
		lines.add("");

		for (final String field : fields) {
			final StepResult result = report.getOrDefault(field, StepResult.fail("Not executed"));
			lines.add(field + ": " + (result.passed ? "PASS" : "FAIL") + " - " + result.details);
		}

		lines.add("");
		lines.add("Términos y Condiciones final URL: " + termsUrl);
		lines.add("Política de Privacidad final URL: " + privacyUrl);

		Files.write(evidenceDirectory.resolve("final-report.txt"), lines);
	}

	private WebElement firstVisibleElement(final By... locators) {
		for (final By locator : locators) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final TimeoutException ignored) {
				// try next locator
			}
		}

		throw new NoSuchElementException("Could not find any visible element for locators: " + Arrays.toString(locators));
	}

	private void clickFirstVisible(final By... locators) throws Exception {
		final WebElement element = firstVisibleElement(locators);
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiToLoad();
	}

	private void clickIfVisible(final By locator) throws Exception {
		if (isVisible(locator, 3)) {
			clickFirstVisible(locator);
		}
	}

	private boolean isVisible(final By locator, final int timeoutSeconds) {
		try {
			new WebDriverWait(driver, java.time.Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void waitForVisible(final By... locators) {
		for (final By locator : locators) {
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		}
	}

	private void waitForAnyVisible(final By... locators) {
		firstVisibleElement(locators);
	}

	private void captureScreenshot(final String name) {
		if (driver == null || evidenceDirectory == null) {
			return;
		}

		try {
			final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			Files.copy(screenshotFile.toPath(), evidenceDirectory.resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception ignored) {
			// Ignore screenshot write failures to avoid interrupting workflow reporting.
		}
	}

	private void waitForUiToLoad() throws Exception {
		wait.until(drv -> "complete".equals(((JavascriptExecutor) drv).executeScript("return document.readyState")));
		Thread.sleep(500);
	}

	private String slugify(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String removeAccents(final String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	private String getConfig(final String envKey, final String propertyKey, final String defaultValue) {
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		return defaultValue;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
