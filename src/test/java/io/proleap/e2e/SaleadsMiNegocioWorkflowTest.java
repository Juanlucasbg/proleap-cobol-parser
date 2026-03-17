package io.proleap.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SaleadsMiNegocioWorkflowTest {

	private static final int DEFAULT_TIMEOUT_SECONDS = 25;

	private WebDriver driver;
	private WebDriverWait wait;
	private String baseUrl;
	private String googleAccountEmail;
	private Path screenshotDir;

	@Before
	public void setUp() throws Exception {
		baseUrl = readConfig("saleads.baseUrl", "SALEADS_BASE_URL");
		googleAccountEmail = readConfig("saleads.googleEmail", "SALEADS_GOOGLE_EMAIL");
		final String headlessValue = readConfig("saleads.headless", "SALEADS_HEADLESS");
		final boolean headless = headlessValue == null || Boolean.parseBoolean(headlessValue);

		Assume.assumeTrue(
				"Set -Dsaleads.baseUrl or SALEADS_BASE_URL to the current SaleADS login URL.",
				baseUrl != null && !baseUrl.isBlank()
		);

		if (googleAccountEmail == null || googleAccountEmail.isBlank()) {
			googleAccountEmail = "juanlucasbarbiergarzon@gmail.com";
		}

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));

		final String runFolder = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		screenshotDir = Paths.get("target", "saleads-e2e-screenshots", runFolder);
		Files.createDirectories(screenshotDir);

		driver.get(baseUrl);
		waitForPageSettled();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();

		runStep(report, "Login", this::stepLoginWithGoogle);
		runStep(report, "Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep(report, "Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep(report, "Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep(report, "Informaci\u00f3n General", this::stepValidateInformacionGeneral);
		runStep(report, "Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep(report, "Tus Negocios", this::stepValidateTusNegocios);
		runStep(report, "T\u00e9rminos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep(report, "Pol\u00edtica de Privacidad", this::stepValidatePoliticaPrivacidad);

		final String formattedReport = formatReport(report);
		System.out.println(formattedReport);
		Assert.assertTrue("One or more validations failed:\n" + formattedReport, report.values().stream().allMatch(Boolean::booleanValue));
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleText("Google login button",
				"Sign in with Google",
				"Iniciar sesion con Google",
				"Iniciar sesi\u00f3n con Google",
				"Continuar con Google",
				"Google");

		selectGoogleAccountIfShown();

		// Dashboard and sidebar are key post-login checkpoints.
		Assert.assertTrue("Main application interface did not appear after login.", waitForAnyVisible(
				By.cssSelector("aside"),
				By.cssSelector("nav"),
				visibleTextLocator("Negocio", "Mi Negocio")
		));
		Assert.assertTrue("Left sidebar navigation is not visible.", waitForAnyVisible(
				visibleTextLocator("Negocio", "Mi Negocio")
		));

		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		expandMiNegocioMenuIfNeeded();

		assertVisibleText("Agregar Negocio submenu item", "Agregar Negocio");
		assertVisibleText("Administrar Negocios submenu item", "Administrar Negocios");

		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio", "Agregar Negocio");
		assertVisibleText("Modal title", "Crear Nuevo Negocio");
		assertVisibleText("Plan usage text", "Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar button", "Cancelar");
		assertVisibleText("Crear Negocio button", "Crear Negocio");

		final By businessNameInput = By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"
						+ " | //input[contains(@placeholder, 'Nombre del Negocio')]"
		);
		wait.until(ExpectedConditions.visibilityOfElementLocated(businessNameInput));

		captureScreenshot("03_agregar_negocio_modal");

		final WebElement input = driver.findElement(businessNameInput);
		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatizacion");
		waitForPageSettled();

		clickByVisibleText("Cancelar modal", "Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(visibleTextLocator("Crear Nuevo Negocio")));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		expandMiNegocioMenuIfNeeded();
		clickByVisibleText("Administrar Negocios", "Administrar Negocios");

		assertVisibleText("Informacion General section", "Informacion General", "Informaci\u00f3n General");
		assertVisibleText("Detalles de la Cuenta section", "Detalles de la Cuenta");
		assertVisibleText("Tus Negocios section", "Tus Negocios");
		assertVisibleText("Seccion Legal section", "Seccion Legal", "Secci\u00f3n Legal");

		captureScreenshot("04_administrar_negocios_full_page");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("User name", "Nombre", "Usuario", "@");
		assertVisibleText("User email", "@");
		assertVisibleText("Business plan", "BUSINESS PLAN");
		assertVisibleText("Cambiar Plan button", "Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Cuenta creada", "Cuenta creada");
		assertVisibleText("Estado activo", "Estado activo");
		assertVisibleText("Idioma seleccionado", "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Business list section", "Tus Negocios");
		assertVisibleText("Agregar Negocio button", "Agregar Negocio");
		assertVisibleText("Plan usage text", "Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		validateLegalLink(
				"T\u00e9rminos y Condiciones",
				"Terminos y Condiciones",
				"05_terminos_y_condiciones"
		);
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		validateLegalLink(
				"Pol\u00edtica de Privacidad",
				"Politica de Privacidad",
				"06_politica_privacidad"
		);
	}

	private void validateLegalLink(final String primaryText, final String fallbackText, final String screenshotName) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText("Legal link", primaryText, fallbackText);

		final String newWindow = waitForNewWindow(handlesBeforeClick);
		final boolean openedNewTab = newWindow != null;

		if (openedNewTab) {
			driver.switchTo().window(newWindow);
			waitForPageSettled();
		}

		try {
			assertVisibleText("Legal heading", primaryText, fallbackText);
			final String bodyText = driver.findElement(By.tagName("body")).getText();
			Assert.assertTrue("Legal content appears to be empty.", bodyText != null && bodyText.trim().length() > 120);

			captureScreenshot(screenshotName);
			System.out.println(screenshotName + " final URL: " + driver.getCurrentUrl());
		} finally {
			if (openedNewTab) {
				driver.close();
				driver.switchTo().window(appWindow);
			} else {
				driver.navigate().back();
			}
			waitForPageSettled();
		}
	}

	private void expandMiNegocioMenuIfNeeded() {
		if (isVisible(visibleTextLocator("Agregar Negocio")) && isVisible(visibleTextLocator("Administrar Negocios"))) {
			return;
		}

		clickByVisibleText("Negocio", "Negocio", "Mi Negocio");
		waitForPageSettled();
	}

	private void selectGoogleAccountIfShown() {
		final String appWindow = driver.getWindowHandle();
		String googleWindow = null;
		for (final String handle : driver.getWindowHandles()) {
			if (!handle.equals(appWindow)) {
				googleWindow = handle;
				break;
			}
		}

		if (googleWindow != null) {
			driver.switchTo().window(googleWindow);
			waitForPageSettled();
		}

		final By accountLocator = By.xpath("//*[contains(normalize-space(.), '" + googleAccountEmail + "')]");
		if (isVisible(accountLocator)) {
			click(accountLocator);
		}

		if (!driver.getWindowHandle().equals(appWindow) && driver.getWindowHandles().contains(appWindow)) {
			driver.switchTo().window(appWindow);
		}

		waitForPageSettled();
	}

	private void runStep(final Map<String, Boolean> report, final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, Boolean.TRUE);
		} catch (final Throwable throwable) {
			report.put(stepName, Boolean.FALSE);
			try {
				captureScreenshot("failed_" + toFileName(stepName));
			} catch (final Exception ignored) {
				// ignore screenshot failures in failure handler
			}
			System.err.println("Step failed [" + stepName + "]: " + throwable.getMessage());
		}
	}

	private String formatReport(final Map<String, Boolean> report) {
		final StringBuilder builder = new StringBuilder();
		builder.append("Final Report:\n");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			builder
					.append("- ")
					.append(entry.getKey())
					.append(": ")
					.append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL")
					.append('\n');
		}
		return builder.toString();
	}

	private String readConfig(final String propertyName, final String envName) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return null;
	}

	private void assertVisibleText(final String label, final String... textOptions) {
		final By locator = visibleTextLocator(textOptions);
		Assert.assertTrue(label + " is not visible.", waitForAnyVisible(locator));
	}

	private By visibleTextLocator(final String... textOptions) {
		final String conditions = Arrays.stream(textOptions)
				.map(text -> "contains(normalize-space(.), '" + text + "')")
				.collect(Collectors.joining(" or "));
		return By.xpath("//*["
				+ "self::a or self::button or self::span or self::div or self::h1 or self::h2 or self::h3 or self::p or self::label"
				+ "][" + conditions + "]");
	}

	private void clickByVisibleText(final String label, final String... textOptions) {
		final By locator = visibleTextLocator(textOptions);
		Assert.assertTrue("Could not locate element for click: " + label, waitForAnyVisible(locator));
		click(locator);
	}

	private void click(final By locator) {
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
		scrollIntoView(element);
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForPageSettled();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private boolean waitForAnyVisible(final By... locators) {
		try {
			wait.until(d -> Arrays.stream(locators).anyMatch(this::isVisible));
			return true;
		} catch (final Exception timeout) {
			return false;
		}
	}

	private boolean isVisible(final By locator) {
		try {
			return !driver.findElements(locator).isEmpty() && driver.findElement(locator).isDisplayed();
		} catch (final Exception ignored) {
			return false;
		}
	}

	private String waitForNewWindow(final Set<String> oldHandles) {
		try {
			wait.until(d -> d.getWindowHandles().size() > oldHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!oldHandles.contains(handle)) {
					return handle;
				}
			}
		} catch (final Exception ignored) {
			// no new window opened
		}
		return null;
	}

	private void waitForPageSettled() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
			Thread.sleep(350L);
		} catch (final Exception ignored) {
			// keep test flow moving even if readyState probe is not supported
		}
	}

	private void captureScreenshot(final String name) throws Exception {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = screenshotDir.resolve(toFileName(name) + ".png");
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Screenshot captured: " + target);
	}

	private String toFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
