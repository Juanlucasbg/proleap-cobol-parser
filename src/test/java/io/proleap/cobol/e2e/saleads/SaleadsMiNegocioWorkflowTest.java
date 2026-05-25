package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final String LOGIN_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;

	@Before
	public void setUp() throws IOException {
		registerReportFields();

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "false"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String runStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		screenshotDirectory = Path.of("target", "saleads-mi-negocio-evidence", runStamp);
		Files.createDirectories(screenshotDirectory);
	}

	@After
	public void tearDown() {
		printFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep("Login", this::validateLoginWithGoogle);
		runStep("Mi Negocio menu", this::validateMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::validateAdministrarNegociosView);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", () -> validateLegalDocument("Términos y Condiciones",
				"terminos-y-condiciones.png"));
		runStep("Política de Privacidad", () -> validateLegalDocument("Política de Privacidad", "politica-privacidad.png"));

		if (!failures.isEmpty()) {
			fail("One or more workflow validations failed:\n - " + String.join("\n - ", failures));
		}
	}

	private void validateLoginWithGoogle() throws Exception {
		final String baseUrl = readConfig("saleads.baseUrl", "SALEADS_BASE_URL", "");
		assertTrue(
				"Provide saleads.baseUrl (system property) or SALEADS_BASE_URL (env var) for the current environment.",
				!baseUrl.isBlank());

		driver.get(baseUrl);
		waitForUiToLoad();

		clickAndWait(By.xpath("//button[normalize-space()='Sign in with Google' or normalize-space()='Iniciar sesión con Google'"
				+ " or normalize-space()='Login con Google' or normalize-space()='Continuar con Google']"
				+ " | //a[normalize-space()='Sign in with Google' or normalize-space()='Iniciar sesión con Google'"
				+ " or normalize-space()='Login con Google' or normalize-space()='Continuar con Google']"));

		chooseGoogleAccountIfVisible(LOGIN_EMAIL);
		switchToWindowContaining(byTextContains("Negocio"), Duration.ofSeconds(90));

		assertVisible(By.xpath("//aside | //nav"));
		assertVisible(byTextContains("Negocio"));
		saveScreenshot("dashboard-loaded.png");
	}

	private void validateMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertVisible(byText("Agregar Negocio"));
		assertVisible(byText("Administrar Negocios"));
		saveScreenshot("mi-negocio-menu-expanded.png");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		assertVisible(byText("Crear Nuevo Negocio"));
		assertVisible(By.xpath("//label[normalize-space()='Nombre del Negocio']/following::input[1]"
				+ " | //input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio']"));
		assertVisible(byTextContains("Tienes 2 de 3 negocios"));
		assertVisible(buttonByText("Cancelar"));
		assertVisible(buttonByText("Crear Negocio"));
		saveScreenshot("agregar-negocio-modal.png");

		final WebElement businessNameInput = findVisibleElement(By.xpath(
				"//label[normalize-space()='Nombre del Negocio']/following::input[1] | //input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio']"));
		businessNameInput.click();
		businessNameInput.sendKeys("Negocio Prueba Automatización");

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byText("Crear Nuevo Negocio")));
	}

	private void validateAdministrarNegociosView() throws Exception {
		if (!isVisible(byText("Administrar Negocios"), Duration.ofSeconds(4))) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");

		assertVisible(byText("Información General"));
		assertVisible(byText("Detalles de la Cuenta"));
		assertVisible(byText("Tus Negocios"));
		assertVisible(byText("Sección Legal"));
		saveScreenshot("administrar-negocios-view.png");
	}

	private void validateInformacionGeneral() {
		assertVisible(byText("Información General"));

		final String pageText = normalizedBodyText();
		assertTrue("User email is not visible in Información General.", EMAIL_PATTERN.matcher(pageText).find());
		assertTrue("User name is not visible in Información General.",
				pageText.contains("Nombre") || Pattern.compile("\\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\\b")
						.matcher(pageText).find());
		assertVisible(byTextContains("BUSINESS PLAN"));
		assertVisible(byText("Cambiar Plan"));
	}

	private void validateDetallesDeLaCuenta() {
		assertVisible(byText("Detalles de la Cuenta"));
		assertVisible(byTextContains("Cuenta creada"));
		assertVisible(byTextContains("Estado activo"));
		assertVisible(byTextContains("Idioma seleccionado"));
	}

	private void validateTusNegocios() {
		assertVisible(byText("Tus Negocios"));
		assertVisible(byText("Agregar Negocio"));
		assertVisible(byTextContains("Tienes 2 de 3 negocios"));

		final WebElement section = findVisibleElement(byText("Tus Negocios"));
		assertTrue("Business list is not visible.", section.findElement(By.xpath("./ancestor::*[1]")).getText().length() > 0);
	}

	private void validateLegalDocument(final String legalText, final String screenshotName) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final String appUrl = safeCurrentUrl();
		final Set<String> previousHandles = driver.getWindowHandles();

		clickByVisibleText(legalText);
		waitForTabOrNavigation(previousHandles, appUrl);

		final String documentHandle = switchToLegalDocumentWindow(previousHandles);
		assertVisible(
				By.xpath("//h1[normalize-space()=" + xpathLiteral(legalText) + "] | //h2[normalize-space()="
						+ xpathLiteral(legalText) + "] | //*[@role='heading' and normalize-space()="
						+ xpathLiteral(legalText) + "]"));

		assertTrue("Legal content text is not visible for " + legalText + ".",
				normalizedBodyText().replace(legalText, "").trim().length() > 100);

		saveScreenshot(screenshotName);
		legalUrls.put(legalText, safeCurrentUrl());

		if (!documentHandle.equals(appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else if (!safeCurrentUrl().equals(appUrl)) {
			driver.navigate().back();
		}

		waitForUiToLoad();
	}

	private String switchToLegalDocumentWindow(final Set<String> previousHandles) {
		final Set<String> currentHandles = driver.getWindowHandles();
		for (final String handle : currentHandles) {
			if (!previousHandles.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return handle;
			}
		}

		waitForUiToLoad();
		return driver.getWindowHandle();
	}

	private void waitForTabOrNavigation(final Set<String> previousHandles, final String previousUrl) {
		new WebDriverWait(driver, Duration.ofSeconds(20)).until(webDriver -> {
			final boolean tabOpened = webDriver.getWindowHandles().size() > previousHandles.size();
			final boolean urlChanged = !safeCurrentUrl().equals(previousUrl);
			return tabOpened || urlChanged;
		});
	}

	private void chooseGoogleAccountIfVisible(final String email) {
		final By accountLocator = By.xpath("//*[normalize-space()=" + xpathLiteral(email) + "]");

		try {
			new WebDriverWait(driver, Duration.ofSeconds(20)).until(webDriver -> {
				for (final String handle : webDriver.getWindowHandles()) {
					webDriver.switchTo().window(handle);
					if (isVisible(accountLocator, Duration.ofSeconds(1))) {
						findVisibleElement(accountLocator).click();
						waitForUiToLoad();
						return true;
					}
				}
				return false;
			});
		} catch (final TimeoutException ignored) {
			// If account selection does not appear, continue the workflow.
		}
	}

	private void switchToWindowContaining(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(webDriver -> {
				for (final String handle : webDriver.getWindowHandles()) {
					webDriver.switchTo().window(handle);
					if (isVisible(locator, Duration.ofSeconds(1))) {
						return true;
					}
				}
				return false;
			});
		} catch (final TimeoutException ignored) {
			// Keep current context and let subsequent assertions report the failure.
		}
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			report.put(reportField, new StepResult("PASS", ""));
		} catch (final Throwable throwable) {
			report.put(reportField, new StepResult("FAIL", throwable.getMessage()));
			failures.add(reportField + ": " + throwable.getMessage());
			try {
				saveScreenshot("failure-" + reportField.toLowerCase().replace(' ', '-').replace('ó', 'o').replace('í', 'i')
						.replace('á', 'a').replace('é', 'e').replace('ú', 'u') + ".png");
			} catch (final Exception ignored) {
				// Ignore screenshot errors after a functional failure.
			}
		}
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		sleep(700);
	}

	private void clickAndWait(final By locator) {
		wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
		waitForUiToLoad();
	}

	private void clickByVisibleText(final String text) {
		final String literal = xpathLiteral(text);
		final List<By> preferredLocators = List.of(
				By.xpath("//button[normalize-space()=" + literal + "]"),
				By.xpath("//a[normalize-space()=" + literal + "]"),
				By.xpath("//*[@role='button' and normalize-space()=" + literal + "]"),
				By.xpath("//*[@role='menuitem' and normalize-space()=" + literal + "]"),
				By.xpath("//*[normalize-space()=" + literal + "]"));

		for (final By locator : preferredLocators) {
			try {
				new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.elementToBeClickable(locator)).click();
				waitForUiToLoad();
				return;
			} catch (final TimeoutException ignored) {
				// Try next locator.
			}
		}

		throw new TimeoutException("Could not click visible text: " + text);
	}

	private void assertVisible(final By locator) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private WebElement findVisibleElement(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void saveScreenshot(final String fileName) throws IOException {
		final Path screenshotPath = screenshotDirectory.resolve(fileName);
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private String normalizedBodyText() {
		return driver.findElement(By.tagName("body")).getText().replaceAll("\\s+", " ").trim();
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(interruptedException);
		}
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (final Exception exception) {
			return "";
		}
	}

	private By byText(final String text) {
		return By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]");
	}

	private By byTextContains(final String text) {
		return By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]");
	}

	private By buttonByText(final String text) {
		return By.xpath("//button[normalize-space()=" + xpathLiteral(text) + "] | //*[@role='button' and normalize-space()="
				+ xpathLiteral(text) + "]");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private void registerReportFields() {
		report.clear();
		report.put("Login", new StepResult("PENDING", ""));
		report.put("Mi Negocio menu", new StepResult("PENDING", ""));
		report.put("Agregar Negocio modal", new StepResult("PENDING", ""));
		report.put("Administrar Negocios view", new StepResult("PENDING", ""));
		report.put("Información General", new StepResult("PENDING", ""));
		report.put("Detalles de la Cuenta", new StepResult("PENDING", ""));
		report.put("Tus Negocios", new StepResult("PENDING", ""));
		report.put("Términos y Condiciones", new StepResult("PENDING", ""));
		report.put("Política de Privacidad", new StepResult("PENDING", ""));
	}

	private void printFinalReport() {
		final StringBuilder builder = new StringBuilder("\n=== SaleADS Mi Negocio Final Report ===\n");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			builder.append(entry.getKey()).append(": ").append(entry.getValue().status);
			if (!entry.getValue().details.isBlank()) {
				builder.append(" (").append(entry.getValue().details).append(")");
			}
			builder.append("\n");
		}

		for (final Map.Entry<String, String> legalEntry : legalUrls.entrySet()) {
			builder.append(legalEntry.getKey()).append(" URL: ").append(legalEntry.getValue()).append("\n");
		}

		builder.append("Evidence directory: ").append(screenshotDirectory).append("\n");
		System.out.println(builder);
	}

	private String readConfig(final String propertyName, final String envVarName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envVarName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepResult {
		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details == null ? "" : details;
		}
	}
}
