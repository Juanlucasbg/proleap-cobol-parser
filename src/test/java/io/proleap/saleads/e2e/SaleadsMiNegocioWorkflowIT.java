package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowIT {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(6);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, StepResult> results = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (Boolean.parseBoolean(resolveValue("saleads.headless", "SALEADS_HEADLESS").orElse("false"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		evidenceDirectory = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(TIMESTAMP_FORMAT));
		Files.createDirectories(evidenceDirectory);

		final Optional<String> loginUrl = resolveValue("saleads.loginUrl", "SALEADS_LOGIN_URL");
		if (loginUrl.isPresent() && !loginUrl.get().isBlank()) {
			driver.get(loginUrl.get());
		}

		waitForUiToLoad();

		if ("about:blank".equalsIgnoreCase(driver.getCurrentUrl())) {
			throw new IllegalStateException(
					"Browser started on about:blank. Provide -Dsaleads.loginUrl or SALEADS_LOGIN_URL with the current environment login page.");
		}
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegociosView);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones", "05-terminos-y-condiciones.png"));
		runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad", "06-politica-de-privacidad.png"));

		final List<String> failures = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			if (!entry.getValue().passed) {
				failures.add(entry.getKey() + " => " + entry.getValue().detail);
			}
		}

		assertTrue("Workflow finished with failing validations: " + failures, failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		waitForAnyVisibleElement(By.xpath("//body"));

		final WebElement loginButton = waitForAnyVisibleElement(By.xpath("//button[normalize-space()='Sign in with Google']"),
				By.xpath("//button[normalize-space()='Iniciar sesión con Google']"),
				By.xpath("//button[normalize-space()='Continuar con Google']"),
				By.xpath("//button[contains(normalize-space(),'Google')]"),
				By.xpath("//a[contains(normalize-space(),'Google')]"));

		clickAndWait(loginButton);
		trySelectGoogleAccount();

		waitForAnyVisibleElement(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[normalize-space()='Negocio']"),
				By.xpath("//*[contains(normalize-space(),'Mi Negocio')]"));
		assertVisible(By.xpath("//*[normalize-space()='Negocio' or normalize-space()='Mi Negocio' or normalize-space()='Agregar Negocio']"),
				"Left sidebar navigation was not visible after login.");

		captureScreenshot("01-dashboard-loaded.png", false);
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		waitForAnyVisibleElement(By.xpath("//*[normalize-space()='Negocio']"));
		clickByVisibleText("Mi Negocio");

		assertVisible(By.xpath("//*[normalize-space()='Agregar Negocio']"), "Submenu option 'Agregar Negocio' was not visible.");
		assertVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"),
				"Submenu option 'Administrar Negocios' was not visible.");

		captureScreenshot("02-mi-negocio-menu-expandido.png", false);
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		assertVisible(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']"), "Modal title 'Crear Nuevo Negocio' was not visible.");
		assertVisible(By.xpath(
				"//label[contains(normalize-space(),'Nombre del Negocio')] | //input[@placeholder='Nombre del Negocio' or @name='nombreNegocio']"),
				"Input field 'Nombre del Negocio' was not visible.");
		assertVisible(By.xpath("//*[contains(normalize-space(),'Tienes 2 de 3 negocios')]"),
				"Usage text 'Tienes 2 de 3 negocios' was not visible.");
		assertVisible(By.xpath("//button[normalize-space()='Cancelar']"), "Button 'Cancelar' was not visible.");
		assertVisible(By.xpath("//button[normalize-space()='Crear Negocio']"), "Button 'Crear Negocio' was not visible.");

		captureScreenshot("03-modal-agregar-negocio.png", false);

		final WebElement businessNameInput = waitForAnyVisibleElement(By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"));
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegociosView() throws Exception {
		expandMiNegocioMenuIfCollapsed();
		clickByVisibleText("Administrar Negocios");

		assertVisible(By.xpath("//*[normalize-space()='Información General']"), "Section 'Información General' was not visible.");
		assertVisible(By.xpath("//*[normalize-space()='Detalles de la Cuenta']"),
				"Section 'Detalles de la Cuenta' was not visible.");
		assertVisible(By.xpath("//*[normalize-space()='Tus Negocios']"), "Section 'Tus Negocios' was not visible.");
		assertVisible(By.xpath("//*[normalize-space()='Sección Legal']"), "Section 'Sección Legal' was not visible.");

		captureScreenshot("04-administrar-negocios-vista-completa.png", true);
	}

	private void stepValidateInformacionGeneral() throws Exception {
		final WebElement infoSection = getSectionByHeading("Información General");
		final String sectionText = infoSection.getText();

		assertContainsByRegex(sectionText, Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
				"User email was not visible in 'Información General'.");

		final boolean likelyUserNameVisible = sectionText.lines().map(String::trim)
				.anyMatch(line -> !line.isEmpty() && !line.contains("@") && !line.equalsIgnoreCase("Información General")
						&& !line.equalsIgnoreCase("BUSINESS PLAN") && !line.equalsIgnoreCase("Cambiar Plan"));
		assertTrue("User name was not clearly visible in 'Información General'.", likelyUserNameVisible);

		assertVisible(By.xpath("//*[normalize-space()='BUSINESS PLAN']"), "Text 'BUSINESS PLAN' was not visible.");
		assertVisible(By.xpath("//button[normalize-space()='Cambiar Plan']"), "Button 'Cambiar Plan' was not visible.");
	}

	private void stepValidateDetallesCuenta() {
		assertVisible(By.xpath("//*[contains(normalize-space(),'Cuenta creada')]"), "'Cuenta creada' was not visible.");
		assertVisible(By.xpath("//*[contains(normalize-space(),'Estado activo')]"), "'Estado activo' was not visible.");
		assertVisible(By.xpath("//*[contains(normalize-space(),'Idioma seleccionado')]"),
				"'Idioma seleccionado' was not visible.");
	}

	private void stepValidateTusNegocios() {
		final WebElement businessSection = getSectionByHeading("Tus Negocios");
		assertVisible(By.xpath("//*[normalize-space()='Agregar Negocio']"), "Button 'Agregar Negocio' was not visible.");
		assertVisible(By.xpath("//*[contains(normalize-space(),'Tienes 2 de 3 negocios')]"),
				"Text 'Tienes 2 de 3 negocios' was not visible.");

		final boolean hasVisibleBusinessList = businessSection
				.findElements(By.xpath(".//*[self::li or self::tr or self::article or @role='listitem' or contains(@class,'business') or contains(@class,'negocio')]"))
				.stream().anyMatch(WebElement::isDisplayed);
		assertTrue("Business list was not visible in 'Tus Negocios'.", hasVisibleBusinessList);
	}

	private void stepValidateLegalLink(final String linkText, final String screenshotName) throws Exception {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> existingHandles = driver.getWindowHandles();

		clickByVisibleText(linkText);

		String navigatedWindow = originalWindow;
		try {
			wait.until((ExpectedCondition<Boolean>) drv -> drv != null && drv.getWindowHandles().size() > existingHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!existingHandles.contains(handle)) {
					navigatedWindow = handle;
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			// link opened in the same tab, no action needed
		}

		if (!originalWindow.equals(navigatedWindow)) {
			driver.switchTo().window(navigatedWindow);
		}

		waitForUiToLoad();
		assertVisible(By.xpath("//*[contains(normalize-space(),'" + linkText + "')]"),
				"Heading '" + linkText + "' was not visible on legal page.");

		final String bodyText = waitForAnyVisibleElement(By.tagName("body")).getText().trim();
		assertTrue("Legal content text was not visible on '" + linkText + "'.", bodyText.length() > 200);

		captureScreenshot(screenshotName, false);
		results.put(linkText, StepResult.success("PASS (URL: " + driver.getCurrentUrl() + ")"));

		if (!originalWindow.equals(navigatedWindow)) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void expandMiNegocioMenuIfCollapsed() {
		final boolean administrarVisible = isVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"));
		if (!administrarVisible) {
			clickByVisibleText("Mi Negocio");
		}
	}

	private void runStep(final String stepName, final ThrowingRunnable stepLogic) {
		try {
			stepLogic.run();
			results.putIfAbsent(stepName, StepResult.success("PASS"));
		} catch (final Throwable ex) {
			results.put(stepName, StepResult.failure(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
		}
	}

	private void clickByVisibleText(final String visibleText) {
		final WebElement element = waitForAnyVisibleElement(By.xpath("//button[normalize-space()='" + visibleText + "']"),
				By.xpath("//a[normalize-space()='" + visibleText + "']"),
				By.xpath("//*[self::span or self::div or self::p][normalize-space()='" + visibleText + "']"));
		clickAndWait(element);
	}

	private WebElement waitForAnyVisibleElement(final By... locators) {
		Throwable lastException = null;
		for (final By locator : locators) {
			try {
				return new WebDriverWait(driver, SHORT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final TimeoutException ex) {
				lastException = ex;
			}
		}

		throw new NoSuchElementException("Could not locate visible element for any locator. Last error: " + lastException);
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(drv -> "complete".equals(((JavascriptExecutor) drv).executeScript("return document.readyState")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
	}

	private void trySelectGoogleAccount() {
		final By byDataEmail = By.xpath("//*[@data-email='" + GOOGLE_ACCOUNT_EMAIL + "']");
		final By byVisibleEmail = By.xpath("//*[contains(normalize-space(),'" + GOOGLE_ACCOUNT_EMAIL + "')]");

		if (isVisible(byDataEmail) || isVisible(byVisibleEmail)) {
			final WebElement account = waitForAnyVisibleElement(byDataEmail, byVisibleEmail);
			clickAndWait(account);
		}
	}

	private boolean isVisible(final By locator) {
		try {
			return new WebDriverWait(driver, Duration.ofSeconds(2)).until(ExpectedConditions.visibilityOfElementLocated(locator)) != null;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void assertVisible(final By locator, final String messageIfMissing) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final TimeoutException ex) {
			throw new AssertionError(messageIfMissing, ex);
		}
	}

	private WebElement getSectionByHeading(final String headingText) {
		final By sectionLocator = By.xpath(
				"//*[normalize-space()='" + headingText + "']/ancestor::*[self::section or self::div][1]");
		try {
			return wait.until(ExpectedConditions.visibilityOfElementLocated(sectionLocator));
		} catch (final TimeoutException ex) {
			throw new AssertionError("Could not locate section with heading: " + headingText, ex);
		}
	}

	private void assertContainsByRegex(final String value, final Pattern pattern, final String errorMessage) {
		assertTrue(errorMessage, pattern.matcher(value).find());
	}

	private void captureScreenshot(final String fileName, final boolean fullPage) throws IOException {
		final Path screenshotPath = evidenceDirectory.resolve(fileName);

		if (fullPage && driver instanceof ChromiumDriver) {
			final Map<String, Object> screenshot = ((ChromiumDriver) driver).executeCdpCommand("Page.captureScreenshot",
					Map.of("format", "png", "captureBeyondViewport", true, "fromSurface", true));
			final String base64Data = String.valueOf(screenshot.get("data"));
			Files.write(screenshotPath, Base64.getDecoder().decode(base64Data));
			return;
		}

		final File sourceScreenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(sourceScreenshot.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() throws IOException {
		if (results.isEmpty()) {
			return;
		}

		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Workflow - Final Report");
		lines.add("Generated at: " + LocalDateTime.now());
		lines.add("");
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			lines.add(entry.getKey() + ": " + (entry.getValue().passed ? "PASS" : "FAIL") + " - " + entry.getValue().detail);
		}

		final Path reportPath = evidenceDirectory.resolve("final-report.txt");
		Files.write(reportPath, lines);
		System.out.println(String.join(System.lineSeparator(), lines));
	}

	private Optional<String> resolveValue(final String systemProperty, final String envVar) {
		final String fromProperty = System.getProperty(systemProperty);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return Optional.of(fromProperty);
		}

		final String fromEnv = System.getenv(envVar);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return Optional.of(fromEnv);
		}

		return Optional.empty();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}

		private static StepResult success(final String detail) {
			return new StepResult(true, detail);
		}

		private static StepResult failure(final String detail) {
			return new StepResult(false, detail);
		}
	}
}
