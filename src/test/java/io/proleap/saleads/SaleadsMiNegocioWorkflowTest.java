package io.proleap.saleads;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaleadsMiNegocioWorkflowTest {

	private static final Logger LOG = LoggerFactory.getLogger(SaleadsMiNegocioWorkflowTest.class);
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final By SIDEBAR_LOCATOR = By.xpath(
			"//aside | //nav[contains(@class,'sidebar')] | //nav[.//*[contains(normalize-space(.),'Negocio')]]");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Enable this test with -Dsaleads.e2e.enabled=true.",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

		final String baseUrl = System.getProperty("saleads.baseUrl", "").trim();
		Assert.assertFalse(
				"Set -Dsaleads.baseUrl to the login page of the current SaleADS environment (dev/staging/prod).",
				baseUrl.isEmpty());

		screenshotDirectory = Path.of("target", "saleads-e2e-screenshots", TIMESTAMP_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(screenshotDirectory);

		driver = createDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(Long.parseLong(System.getProperty("saleads.timeoutSeconds", "25"))));
		driver.get(baseUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::loginWithGoogleAndValidateDashboard);
		runStep("Mi Negocio menu", this::openMiNegocioMenuAndValidateOptions);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegociosAndValidateSections);
		runStep("Información General", this::validateInformacionGeneralSection);
		runStep("Detalles de la Cuenta", this::validateDetallesDeLaCuentaSection);
		runStep("Tus Negocios", this::validateTusNegociosSection);
		runStep("Términos y Condiciones", this::validateTerminosYCondiciones);
		runStep("Política de Privacidad", this::validatePoliticaDePrivacidad);

		printFinalReport();
		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue().passed())
				.map(entry -> "- " + entry.getKey() + ": " + entry.getValue().details()).collect(Collectors.toList());
		Assert.assertTrue("One or more workflow validations failed:\n" + String.join("\n", failedSteps), failedSteps.isEmpty());
	}

	private void loginWithGoogleAndValidateDashboard() {
		clickVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Login with Google");
		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");
		requireVisible(SIDEBAR_LOCATOR, "Left sidebar navigation is not visible.");
		requireVisible(byContainsVisibleText("Negocio"), "Main application interface did not load.");
		captureScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenuAndValidateOptions() {
		clickVisibleText("Negocio");
		clickVisibleText("Mi Negocio");
		requireVisible(byContainsVisibleText("Agregar Negocio"), "'Agregar Negocio' option is not visible.");
		requireVisible(byContainsVisibleText("Administrar Negocios"), "'Administrar Negocios' option is not visible.");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() {
		clickVisibleText("Agregar Negocio");
		requireVisible(byContainsVisibleText("Crear Nuevo Negocio"), "Modal title 'Crear Nuevo Negocio' is not visible.");
		requireVisible(nombreDelNegocioInputLocator(), "Input field 'Nombre del Negocio' was not found.");
		requireVisible(byContainsVisibleText("Tienes 2 de 3 negocios"), "Business usage text was not visible.");
		requireVisible(byContainsVisibleText("Cancelar"), "Button 'Cancelar' is missing.");
		requireVisible(byContainsVisibleText("Crear Negocio"), "Button 'Crear Negocio' is missing.");
		captureScreenshot("03-agregar-negocio-modal");

		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(nombreDelNegocioInputLocator()));
		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatización");
		clickVisibleText("Cancelar");
	}

	private void openAdministrarNegociosAndValidateSections() {
		if (!isVisible(byContainsVisibleText("Administrar Negocios"), Duration.ofSeconds(3))) {
			clickVisibleText("Mi Negocio");
		}
		clickVisibleText("Administrar Negocios");
		requireVisible(byContainsVisibleText("Información General"), "Section 'Información General' is missing.");
		requireVisible(byContainsVisibleText("Detalles de la Cuenta"), "Section 'Detalles de la Cuenta' is missing.");
		requireVisible(byContainsVisibleText("Tus Negocios"), "Section 'Tus Negocios' is missing.");
		requireVisible(byContainsVisibleText("Sección Legal"), "Section 'Sección Legal' is missing.");
		captureScreenshot("04-administrar-negocios-page");
	}

	private void validateInformacionGeneralSection() {
		requireVisible(By.xpath("//*[contains(normalize-space(.), '@')]"), "User email is not visible in Información General.");
		requireVisible(By.xpath("//*[contains(@class,'name') or contains(@class,'user') or contains(@class,'profile')]"),
				"User name block is not visible in Información General.");
		requireVisible(byContainsVisibleText("BUSINESS PLAN"), "Text 'BUSINESS PLAN' is not visible.");
		requireVisible(byContainsVisibleText("Cambiar Plan"), "Button 'Cambiar Plan' is not visible.");
	}

	private void validateDetallesDeLaCuentaSection() {
		requireVisible(byContainsVisibleText("Cuenta creada"), "Text 'Cuenta creada' is not visible.");
		requireVisible(byContainsVisibleText("Estado activo"), "Text 'Estado activo' is not visible.");
		requireVisible(byContainsVisibleText("Idioma seleccionado"), "Text 'Idioma seleccionado' is not visible.");
	}

	private void validateTusNegociosSection() {
		requireVisible(By.xpath(
				"//*[contains(normalize-space(.),'Tus Negocios')]/following::*[self::ul or self::table or self::div][1]"),
				"Business list is not visible.");
		requireVisible(byContainsVisibleText("Agregar Negocio"), "Button 'Agregar Negocio' is missing in Tus Negocios.");
		requireVisible(byContainsVisibleText("Tienes 2 de 3 negocios"), "Text 'Tienes 2 de 3 negocios' is not visible.");
	}

	private void validateTerminosYCondiciones() {
		final String finalUrl = openLegalLinkAndValidate("Términos y Condiciones", "Términos y Condiciones",
				"05-terminos-y-condiciones");
		LOG.info("Términos y Condiciones final URL: {}", finalUrl);
	}

	private void validatePoliticaDePrivacidad() {
		final String finalUrl = openLegalLinkAndValidate("Política de Privacidad", "Política de Privacidad",
				"06-politica-de-privacidad");
		LOG.info("Política de Privacidad final URL: {}", finalUrl);
	}

	private String openLegalLinkAndValidate(final String linkText, final String headingText, final String screenshotName) {
		final String applicationWindow = driver.getWindowHandle();
		final Set<String> existingWindows = driver.getWindowHandles();

		clickVisibleText(linkText);
		waitForUiToLoad();

		final Set<String> currentWindows = driver.getWindowHandles();
		if (currentWindows.size() > existingWindows.size()) {
			for (final String window : currentWindows) {
				if (!existingWindows.contains(window)) {
					driver.switchTo().window(window);
					break;
				}
			}
		}

		requireVisible(byContainsVisibleText(headingText),
				"Expected legal heading '" + headingText + "' was not visible after clicking '" + linkText + "'.");
		requireVisible(By.xpath("//p[string-length(normalize-space(.)) > 40] | //li[string-length(normalize-space(.)) > 40]"),
				"Legal content text is not visible.");

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (!Objects.equals(driver.getWindowHandle(), applicationWindow)) {
			driver.close();
			driver.switchTo().window(applicationWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, StepResult.pass("All validations passed."));
		} catch (final Throwable throwable) {
			final String details = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
			report.put(stepName, StepResult.fail(details));
			captureScreenshot("failure-" + sanitizeFileName(stepName));
			LOG.error("Step '{}' failed: {}", stepName, details, throwable);
		}
	}

	private void printFinalReport() {
		LOG.info("=== SaleADS Mi Negocio Workflow Final Report ===");
		for (final String field : Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
				"Información General", "Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones",
				"Política de Privacidad")) {
			final StepResult result = report.getOrDefault(field, StepResult.fail("Step was not executed."));
			LOG.info("{}: {} - {}", field, result.passed() ? "PASS" : "FAIL", result.details());
		}
		LOG.info("Screenshot directory: {}", screenshotDirectory.toAbsolutePath());
	}

	private WebDriver createDriver() {
		final String browser = System.getProperty("saleads.browser", "chrome").trim().toLowerCase();
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			return new FirefoxDriver(firefoxOptions);
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.addArguments("--window-size=1920,1080");
			chromeOptions.addArguments("--disable-gpu");
			chromeOptions.addArguments("--no-sandbox");
			chromeOptions.addArguments("--disable-dev-shm-usage");
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			return new ChromeDriver(chromeOptions);
		}
	}

	private void waitForUiToLoad() {
		wait.until(driver1 -> "complete".equals(((JavascriptExecutor) driver1).executeScript("return document.readyState")));
		sleep(Duration.ofMillis(Long.parseLong(System.getProperty("saleads.postClickPauseMs", "800"))));
	}

	private void clickVisibleText(final String... visibleTexts) {
		final WebElement element = findFirstClickableElement(visibleTexts, Duration.ofSeconds(15));
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiToLoad();
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final By accountLocator = byContainsVisibleText(email);
		if (isVisible(accountLocator, Duration.ofSeconds(8))) {
			wait.until(ExpectedConditions.elementToBeClickable(accountLocator)).click();
			waitForUiToLoad();
			return;
		}

		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (isVisible(accountLocator, Duration.ofSeconds(3))) {
				wait.until(ExpectedConditions.elementToBeClickable(accountLocator)).click();
				waitForUiToLoad();
				break;
			}
		}
	}

	private By nombreDelNegocioInputLocator() {
		return By.xpath("//input[@placeholder='Nombre del Negocio' or contains(@aria-label,'Nombre del Negocio')]"
				+ " | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"
				+ " | //span[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]");
	}

	private By byAnyVisibleText(final String... visibleTexts) {
		final String expression = Arrays.stream(visibleTexts)
				.map(text -> "normalize-space(.)=" + xpathLiteral(text))
				.collect(Collectors.joining(" or "));
		return By.xpath("//*[(" + expression + ") and not(self::script) and not(self::style)]");
	}

	private By byContainsVisibleText(final String... visibleTexts) {
		final String expression = Arrays.stream(visibleTexts)
				.map(text -> "contains(normalize-space(.)," + xpathLiteral(text) + ")")
				.collect(Collectors.joining(" or "));
		return By.xpath("//*[(" + expression + ") and not(self::script) and not(self::style)]");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	private void requireVisible(final By locator, final String failureMessage) {
		if (!isVisible(locator, Duration.ofSeconds(15))) {
			throw new AssertionError(failureMessage);
		}
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private WebElement findFirstClickableElement(final String[] visibleTexts, final Duration timeout) {
		final long timeoutAt = System.nanoTime() + timeout.toNanos();
		final List<By> locators = Arrays.asList(byAnyVisibleText(visibleTexts), byContainsVisibleText(visibleTexts));

		while (System.nanoTime() < timeoutAt) {
			for (final By locator : locators) {
				final List<WebElement> elements = driver.findElements(locator);
				for (final WebElement element : elements) {
					try {
						if (element.isDisplayed() && element.isEnabled()) {
							return element;
						}
					} catch (final Exception ignored) {
						// Stale element can happen while the UI rerenders.
					}
				}
			}
			sleep(Duration.ofMillis(250));
		}

		throw new TimeoutException("Could not find a clickable element using text: " + String.join(" / ", visibleTexts));
	}

	private void captureScreenshot(final String logicalName) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		try {
			final Path screenshotPath = screenshotDirectory.resolve(sanitizeFileName(logicalName) + ".png");
			Files.copy(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath(), screenshotPath, REPLACE_EXISTING);
		} catch (final IOException exception) {
			LOG.warn("Could not capture screenshot '{}': {}", logicalName, exception.getMessage());
		}
	}

	private String sanitizeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9\\-]+", "-").replaceAll("-{2,}", "-");
	}

	private void sleep(final Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run();
	}

	private record StepResult(boolean passed, String details) {

		static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
