package io.proleap.e2e;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration LONG_TIMEOUT = Duration.ofSeconds(60);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private String appWindowHandle;
	private String terminosUrl = "N/A";
	private String privacidadUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		driver = createWebDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		screenshotDirectory = Path.of("target", "saleads-screenshots",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
		Files.createDirectories(screenshotDirectory);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		Assert.assertNotNull("Set saleads.login.url or SALEADS_LOGIN_URL before running this test.", loginUrl);

		driver.get(loginUrl);
		waitForDocumentReady(LONG_TIMEOUT);

		runStep("Login", this::loginWithGoogleAndValidateDashboard);
		runStep("Mi Negocio menu", this::openMiNegocioMenuAndValidateOptions);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegociosAndValidateSections);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", this::validateTerminosYCondiciones);
		runStep("Política de Privacidad", this::validatePoliticaPrivacidad);

		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		for (Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue().status + " - " + entry.getValue().details);
		}
		System.out.println("Términos y Condiciones final URL: " + terminosUrl);
		System.out.println("Política de Privacidad final URL: " + privacidadUrl);
		System.out.println("Screenshots directory: " + screenshotDirectory.toAbsolutePath());

		boolean hasFailures = stepResults.values().stream().anyMatch(result -> "FAIL".equals(result.status));
		if (hasFailures) {
			Assert.fail("One or more SaleADS Mi Negocio workflow validations failed. Check report output and screenshots.");
		}
	}

	private void loginWithGoogleAndValidateDashboard() throws IOException {
		appWindowHandle = driver.getWindowHandle();
		Set<String> windowsBefore = driver.getWindowHandles();

		clickVisibleText("Sign in with Google", "Iniciar con Google", "Continuar con Google", "Google");
		handleGoogleAccountSelection(windowsBefore);

		assertVisibleText("Main application interface", "Negocio", "Dashboard", "Inicio");
		assertAnyElementVisible("Left sidebar navigation should be visible", by("//*[self::aside or self::nav]"),
				by("//*[contains(@class,'sidebar')]"));

		captureScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenuAndValidateOptions() throws IOException {
		clickVisibleText("Negocio");
		clickVisibleText("Mi Negocio");

		assertVisibleText("Mi Negocio submenu should expand", "Agregar Negocio");
		assertVisibleText("Mi Negocio submenu should expand", "Administrar Negocios");

		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickVisibleText("Agregar Negocio");

		assertVisibleText("Modal title should be visible", "Crear Nuevo Negocio");
		assertAnyElementVisible("Nombre del Negocio input field should exist",
				by("//label[contains(normalize-space(.), 'Nombre del Negocio')]"),
				by("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				by("//input[contains(@aria-label, 'Nombre del Negocio')]"));
		assertVisibleText("Business count text should be visible", "Tienes 2 de 3 negocios");
		assertVisibleText("Cancel button should be visible", "Cancelar");
		assertVisibleText("Create button should be visible", "Crear Negocio");

		captureScreenshot("03-crear-nuevo-negocio-modal");

		WebElement businessNameInput = findVisibleElement(
				by("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				by("//input[contains(@aria-label, 'Nombre del Negocio')]"),
				by("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		clickVisibleText("Cancelar");
		waitForUiToSettle();
	}

	private void openAdministrarNegociosAndValidateSections() throws IOException {
		ensureMiNegocioExpanded();
		clickVisibleText("Administrar Negocios");

		assertVisibleText("Información General section should exist", "Información General");
		assertVisibleText("Detalles de la Cuenta section should exist", "Detalles de la Cuenta");
		assertVisibleText("Tus Negocios section should exist", "Tus Negocios");
		assertVisibleText("Sección Legal section should exist", "Sección Legal");

		captureFullPageScreenshot("04-administrar-negocios-account-page");
	}

	private void validateInformacionGeneral() {
		assertVisibleText("User name should be visible", "@", "Nombre", "Perfil");
		assertAnyElementVisible("User email should be visible", by("//*[contains(normalize-space(.), '@')]"));
		assertVisibleText("BUSINESS PLAN text should be visible", "BUSINESS PLAN");
		assertVisibleText("Cambiar Plan button should be visible", "Cambiar Plan");
	}

	private void validateDetallesCuenta() {
		assertVisibleText("'Cuenta creada' should be visible", "Cuenta creada");
		assertVisibleText("'Estado activo' should be visible", "Estado activo");
		assertVisibleText("'Idioma seleccionado' should be visible", "Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertVisibleText("Business list should be visible", "Tus Negocios");
		assertVisibleText("'Agregar Negocio' button should exist", "Agregar Negocio");
		assertVisibleText("'Tienes 2 de 3 negocios' should be visible", "Tienes 2 de 3 negocios");
	}

	private void validateTerminosYCondiciones() throws IOException {
		terminosUrl = clickLegalLinkValidateAndReturn("Términos y Condiciones", "Términos y Condiciones",
				"05-terminos-y-condiciones");
	}

	private void validatePoliticaPrivacidad() throws IOException {
		privacidadUrl = clickLegalLinkValidateAndReturn("Política de Privacidad", "Política de Privacidad",
				"06-politica-de-privacidad");
	}

	private String clickLegalLinkValidateAndReturn(final String linkText, final String expectedHeading,
			final String screenshotName) throws IOException {
		ensureLegalSectionVisible();

		final String originalWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = driver.getWindowHandles();
		final String previousUrl = driver.getCurrentUrl();

		clickVisibleText(linkText);

		Optional<String> newWindow = waitForNewWindow(windowsBefore, Duration.ofSeconds(10));
		boolean openedInNewTab = newWindow.isPresent();
		if (openedInNewTab) {
			driver.switchTo().window(newWindow.get());
		}

		waitForHeadingOrUrlChange(expectedHeading, previousUrl);
		assertVisibleText("Legal heading should be visible", expectedHeading);
		assertAnyElementVisible("Legal content text should be visible", by("//p[string-length(normalize-space(.)) > 40]"),
				by("//*[self::article or self::main]//*[string-length(normalize-space(.)) > 40]"));

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiToSettle();
		} else {
			driver.navigate().back();
			waitForUiToSettle();
		}

		assertVisibleText("Application tab should be restored", "Sección Legal", "Tus Negocios", "Administrar Negocios");
		return finalUrl;
	}

	private void runStep(final String stepName, final StepRunnable stepRunnable) {
		try {
			stepRunnable.run();
			stepResults.put(stepName, StepResult.pass("Validation completed."));
		} catch (Throwable throwable) {
			String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
			stepResults.put(stepName, StepResult.fail(message));
			try {
				captureScreenshot("FAILED-" + sanitizeFileName(stepName));
			} catch (IOException ignored) {
				// Best effort screenshot in failure flow.
			}
		}
	}

	private void handleGoogleAccountSelection(final Set<String> windowsBeforeLoginClick) {
		Optional<String> newWindow = waitForNewWindow(windowsBeforeLoginClick, Duration.ofSeconds(12));
		if (newWindow.isPresent()) {
			driver.switchTo().window(newWindow.get());
		}

		tryClickVisibleText(Duration.ofSeconds(12), GOOGLE_ACCOUNT_EMAIL);

		if (newWindow.isPresent() && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}

		waitForUiToSettle();
	}

	private void ensureMiNegocioExpanded() {
		if (!isVisible(Duration.ofSeconds(2), containsVisibleText("Administrar Negocios"))) {
			clickVisibleText("Mi Negocio");
		}
	}

	private void ensureLegalSectionVisible() {
		if (!isVisible(Duration.ofSeconds(3), containsVisibleText("Sección Legal"))) {
			ensureMiNegocioExpanded();
			clickVisibleText("Administrar Negocios");
		}
	}

	private void clickVisibleText(final String... texts) {
		tryClickVisibleText(DEFAULT_TIMEOUT, texts);
		waitForUiToSettle();
	}

	private void tryClickVisibleText(final Duration timeout, final String... texts) {
		WebDriverWait customWait = new WebDriverWait(driver, timeout);
		WebElement element = customWait.until(d -> {
			for (String text : texts) {
				for (By locator : clickLocatorsForText(text)) {
					for (WebElement candidate : d.findElements(locator)) {
						if (candidate.isDisplayed() && candidate.isEnabled()) {
							return candidate;
						}
					}
				}
			}
			return null;
		});

		scrollIntoView(element);
		element.click();
	}

	private void assertVisibleText(final String assertionMessage, final String... texts) {
		for (String text : texts) {
			if (isVisible(DEFAULT_TIMEOUT, containsVisibleText(text))) {
				return;
			}
		}
		Assert.fail(assertionMessage + " Expected one of: " + String.join(", ", texts));
	}

	private void assertAnyElementVisible(final String assertionMessage, final By... locators) {
		for (By locator : locators) {
			if (isVisible(DEFAULT_TIMEOUT, locator)) {
				return;
			}
		}
		Assert.fail(assertionMessage);
	}

	private WebElement findVisibleElement(final By... locators) {
		for (By locator : locators) {
			try {
				return new WebDriverWait(driver, DEFAULT_TIMEOUT).until(d -> {
					for (WebElement candidate : d.findElements(locator)) {
						if (candidate.isDisplayed()) {
							return candidate;
						}
					}
					return null;
				});
			} catch (TimeoutException ignored) {
				// Try next locator.
			}
		}
		throw new AssertionError("Expected visible element was not found.");
	}

	private Optional<String> waitForNewWindow(final Set<String> windowsBefore, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(d -> d.getWindowHandles().size() > windowsBefore.size());
			for (String handle : driver.getWindowHandles()) {
				if (!windowsBefore.contains(handle)) {
					return Optional.of(handle);
				}
			}
		} catch (TimeoutException ignored) {
			// No new window opened.
		}
		return Optional.empty();
	}

	private void waitForHeadingOrUrlChange(final String heading, final String previousUrl) {
		new WebDriverWait(driver, LONG_TIMEOUT).until((ExpectedCondition<Boolean>) d -> {
			boolean urlChanged = !previousUrl.equals(d.getCurrentUrl());
			boolean headingVisible = isVisible(Duration.ofSeconds(2), containsVisibleText(heading));
			return urlChanged || headingVisible;
		});
		waitForUiToSettle();
	}

	private void waitForUiToSettle() {
		waitForDocumentReady(DEFAULT_TIMEOUT);
	}

	private void waitForDocumentReady(final Duration timeout) {
		new WebDriverWait(driver, timeout).until(d -> {
			if (!(d instanceof JavascriptExecutor)) {
				return true;
			}
			Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(state);
		});
	}

	private boolean isVisible(final Duration timeout, final By locator) {
		try {
			return new WebDriverWait(driver, timeout).until(d -> {
				for (WebElement candidate : d.findElements(locator)) {
					if (candidate.isDisplayed()) {
						return true;
					}
				}
				return false;
			});
		} catch (TimeoutException exception) {
			return false;
		}
	}

	private void scrollIntoView(final WebElement element) {
		if (driver instanceof JavascriptExecutor) {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		}
	}

	private void captureScreenshot(final String screenshotName) throws IOException {
		String fileName = sanitizeFileName(screenshotName) + ".png";
		Path screenshotPath = screenshotDirectory.resolve(fileName);
		File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureFullPageScreenshot(final String screenshotName) throws IOException {
		if (driver instanceof ChromeDriver) {
			Map<String, Object> params = new LinkedHashMap<>();
			params.put("captureBeyondViewport", true);
			params.put("fromSurface", true);
			@SuppressWarnings("unchecked")
			Map<String, Object> result = ((ChromeDriver) driver).executeCdpCommand("Page.captureScreenshot", params);
			String base64Data = (String) result.get("data");
			Path screenshotPath = screenshotDirectory.resolve(sanitizeFileName(screenshotName) + ".png");
			Files.write(screenshotPath, Base64.getDecoder().decode(base64Data));
			return;
		}
		captureScreenshot(screenshotName);
	}

	private WebDriver createWebDriver() {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		boolean headless = Boolean.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"),
				System.getenv("SALEADS_HEADLESS"), "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		String remoteUrl = firstNonBlank(System.getProperty("selenium.remote.url"), System.getenv("SELENIUM_REMOTE_URL"));
		if (remoteUrl != null) {
			try {
				return new RemoteWebDriver(new URL(remoteUrl), options);
			} catch (MalformedURLException exception) {
				throw new IllegalArgumentException("Invalid selenium remote URL: " + remoteUrl, exception);
			}
		}

		return new ChromeDriver(options);
	}

	private By containsVisibleText(final String text) {
		String loweredText = text.toLowerCase(Locale.ROOT);
		return by("//*[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), '"
				+ loweredText + "')]");
	}

	private By[] clickLocatorsForText(final String text) {
		return new By[] {
				by("//button[contains(normalize-space(.), '" + text + "')]"),
				by("//a[contains(normalize-space(.), '" + text + "')]"),
				by("//*[@role='button' and contains(normalize-space(.), '" + text + "')]"),
				by("//*[contains(normalize-space(.), '" + text + "')]") };
	}

	private By by(final String xpath) {
		return By.xpath(xpath);
	}

	private String firstNonBlank(final String... values) {
		for (String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private String sanitizeFileName(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]+", "-").replaceAll("-{2,}", "-")
				.replaceAll("^-|-$", "");
	}

	private interface StepRunnable {
		void run() throws Exception;
	}

	private static class StepResult {
		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult("PASS", details);
		}

		private static StepResult fail(final String details) {
			return new StepResult("FAIL", details);
		}
	}
}
