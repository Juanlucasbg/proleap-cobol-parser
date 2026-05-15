package io.proleap.cobol.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, StepOutcome> stepOutcomes = new LinkedHashMap<>();
	private final String runTimestamp = TIMESTAMP_FORMAT.format(LocalDateTime.now());

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private Path reportFile;
	private String baseUrl;

	@Before
	public void setUp() throws IOException {
		final boolean isEnabled = Boolean
				.parseBoolean(readConfig("SALEADS_E2E_ENABLED", "saleads.e2e.enabled", "false"));
		Assume.assumeTrue(
				"Skipping SaleADS E2E workflow test. Enable with SALEADS_E2E_ENABLED=true or -Dsaleads.e2e.enabled=true.",
				isEnabled);

		baseUrl = readConfig("SALEADS_BASE_URL", "saleads.baseUrl", "");
		if (baseUrl.isBlank()) {
			Assert.fail("SALEADS_BASE_URL (or -Dsaleads.baseUrl) must be provided for this environment.");
		}

		final boolean isHeadless = Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "saleads.headless", "true"));

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (isHeadless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		screenshotDirectory = Paths.get("target", "saleads-evidence", TEST_NAME, runTimestamp);
		Files.createDirectories(screenshotDirectory);
		reportFile = Paths.get("target", "saleads-reports", TEST_NAME + "-" + runTimestamp + ".txt");
		Files.createDirectories(reportFile.getParent());

		driver.get(baseUrl);
		waitForUiToSettle();
	}

	@Test
	public void executeMiNegocioWorkflow() {
		runStep("Login", outcome -> {
			clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Inicia sesión con Google",
					"Continuar con Google", "Google");
			waitForUiToSettle();

			clickByVisibleTextIfPresent(Duration.ofSeconds(8), "juanlucasbarbiergarzon@gmail.com");
			waitForUiToSettle();

			assertVisible(By.xpath("//aside | //nav[.//*[contains(normalize-space(), 'Negocio')]]"),
					"Main application sidebar is visible");
			assertVisible(textLocator("Negocio"), "Sidebar option 'Negocio' is visible");
			outcome.screenshots.add(captureScreenshot("01-dashboard-loaded"));
		});

		runStep("Mi Negocio menu", outcome -> {
			clickByVisibleText("Negocio");
			waitForUiToSettle();
			clickByVisibleText("Mi Negocio");
			waitForUiToSettle();

			assertVisible(textLocator("Agregar Negocio"), "'Agregar Negocio' is visible");
			assertVisible(textLocator("Administrar Negocios"), "'Administrar Negocios' is visible");
			outcome.screenshots.add(captureScreenshot("02-mi-negocio-menu-expanded"));
		});

		runStep("Agregar Negocio modal", outcome -> {
			clickByVisibleText("Agregar Negocio");
			waitForUiToSettle();

			assertVisible(textLocator("Crear Nuevo Negocio"), "Modal title 'Crear Nuevo Negocio' is visible");
			assertVisible(textLocator("Nombre del Negocio"), "Input label 'Nombre del Negocio' is visible");
			assertVisible(textLocator("Tienes 2 de 3 negocios"), "Business quota text is visible");
			assertVisible(textLocator("Cancelar"), "Button 'Cancelar' is visible");
			assertVisible(textLocator("Crear Negocio"), "Button 'Crear Negocio' is visible");
			outcome.screenshots.add(captureScreenshot("03-agregar-negocio-modal"));

			typeBusinessNameIfPresent("Negocio Prueba Automatización");
			clickByVisibleTextIfPresent(Duration.ofSeconds(5), "Cancelar");
			waitForUiToSettle();
		});

		runStep("Administrar Negocios view", outcome -> {
			if (!isVisible(textLocator("Administrar Negocios"), Duration.ofSeconds(4))) {
				clickByVisibleTextIfPresent(Duration.ofSeconds(4), "Negocio");
				clickByVisibleTextIfPresent(Duration.ofSeconds(4), "Mi Negocio");
				waitForUiToSettle();
			}

			clickByVisibleText("Administrar Negocios");
			waitForUiToSettle();

			assertVisible(textLocator("Información General"), "Section 'Información General' exists");
			assertVisible(textLocator("Detalles de la Cuenta"), "Section 'Detalles de la Cuenta' exists");
			assertVisible(textLocator("Tus Negocios"), "Section 'Tus Negocios' exists");
			assertVisible(textLocator("Sección Legal"), "Section 'Sección Legal' exists");
			outcome.screenshots.add(captureScreenshot("04-administrar-negocios"));
		});

		runStep("Información General", outcome -> {
			final WebElement infoSection = findSection("Información General");
			assertCondition(hasVisibleText(infoSection, "@"), "User email is visible");
			assertCondition(hasVisibleText(infoSection, "BUSINESS PLAN"), "Text 'BUSINESS PLAN' is visible");
			assertCondition(hasVisibleText(infoSection, "Cambiar Plan"), "Button 'Cambiar Plan' is visible");

			// Accept common account labels to avoid hard-coding names.
			assertCondition(hasAnyVisibleText(infoSection, Arrays.asList("Nombre", "Usuario", "Perfil", "Cuenta")),
					"User name or account label is visible");
		});

		runStep("Detalles de la Cuenta", outcome -> {
			assertVisible(textLocator("Cuenta creada"), "'Cuenta creada' is visible");
			assertVisible(textLocator("Estado activo"), "'Estado activo' is visible");
			assertVisible(textLocator("Idioma seleccionado"), "'Idioma seleccionado' is visible");
		});

		runStep("Tus Negocios", outcome -> {
			final WebElement businessSection = findSection("Tus Negocios");
			assertCondition(hasAnyElement(businessSection, By.xpath(".//ul | .//table | .//div[contains(@class,'list')]")),
					"Business list is visible");
			assertCondition(hasVisibleText(businessSection, "Agregar Negocio"), "Button 'Agregar Negocio' exists");
			assertCondition(hasVisibleText(businessSection, "Tienes 2 de 3 negocios"),
					"Text 'Tienes 2 de 3 negocios' is visible");
		});

		runStep("Términos y Condiciones", outcome -> {
			validateLegalPage("Términos y Condiciones", "08-terminos-y-condiciones", outcome);
		});

		runStep("Política de Privacidad", outcome -> {
			validateLegalPage("Política de Privacidad", "09-politica-de-privacidad", outcome);
		});

		final List<String> failedSteps = stepOutcomes.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(Map.Entry::getKey).collect(Collectors.toList());
		Assert.assertTrue("Validation failed for steps: " + failedSteps + ". Review report at " + reportFile,
				failedSteps.isEmpty());
	}

	@After
	public void tearDown() throws IOException {
		if (!stepOutcomes.isEmpty() && reportFile != null) {
			writeReport();
		}

		if (driver != null) {
			driver.quit();
		}
	}

	private void validateLegalPage(final String legalLinkText, final String screenshotPrefix, final StepOutcome outcome)
			throws IOException {
		final String appHandle = driver.getWindowHandle();
		final Set<String> originalHandles = driver.getWindowHandles();

		clickByVisibleText(legalLinkText);
		waitForUiToSettle();

		final String newHandle = waitForNewWindowHandle(originalHandles, Duration.ofSeconds(10));
		final boolean openedNewTab = newHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(newHandle);
			waitForUiToSettle();
		}

		assertVisible(textLocator(legalLinkText), "Legal heading '" + legalLinkText + "' is visible");
		assertCondition(hasLegalBodyText(), "Legal content text is visible");
		outcome.finalUrl = driver.getCurrentUrl();
		outcome.screenshots.add(captureScreenshot(screenshotPrefix));

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToSettle();
	}

	private void runStep(final String stepName, final StepAction action) {
		final StepOutcome outcome = new StepOutcome();

		try {
			action.run(outcome);
			outcome.passed = true;
			outcome.details = "PASS";
		} catch (final Throwable throwable) {
			outcome.passed = false;
			outcome.details = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getMessage();
		}

		stepOutcomes.put(stepName, outcome);
	}

	private void waitForUiToSettle() {
		if (driver == null) {
			return;
		}

		wait.until(webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		wait.until(ExpectedConditions.or(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading")),
				ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".spinner")),
				ExpectedConditions.invisibilityOfElementLocated(By.cssSelector("[aria-busy='true']"))));

		try {
			Thread.sleep(400);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void clickByVisibleText(final String... textOptions) {
		if (!clickByVisibleTextIfPresent(Duration.ofSeconds(12), textOptions)) {
			throw new TimeoutException("Unable to click an element by visible text: " + Arrays.toString(textOptions));
		}
	}

	private boolean clickByVisibleTextIfPresent(final Duration timeout, final String... textOptions) {
		for (final String text : textOptions) {
			final By locator = clickableTextLocator(text);
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			try {
				final WebElement element = shortWait.until(ExpectedConditions.presenceOfElementLocated(locator));
				shortWait.until(ExpectedConditions.visibilityOf(element));
				try {
					shortWait.until(ExpectedConditions.elementToBeClickable(element));
					element.click();
				} catch (final Exception clickException) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				}
				waitForUiToSettle();
				return true;
			} catch (final TimeoutException timeoutException) {
				// Try next visible-text option.
			}
		}
		return false;
	}

	private void typeBusinessNameIfPresent(final String businessName) {
		final String labelLiteral = xpathLiteral("Nombre del Negocio");
		final By inputLocator = By.xpath(
				"//label[contains(normalize-space(), " + labelLiteral + ")]/following::input[1]"
						+ " | //input[@placeholder=" + labelLiteral + " or contains(@placeholder, " + labelLiteral
						+ ")]"
						+ " | //input[contains(@aria-label, " + labelLiteral + ")]");

		try {
			final WebElement input = new WebDriverWait(driver, Duration.ofSeconds(6))
					.until(ExpectedConditions.visibilityOfElementLocated(inputLocator));
			input.clear();
			input.sendKeys(businessName);
		} catch (final TimeoutException timeoutException) {
			// Optional step. Continue if this field is not interactable.
		}
	}

	private WebElement findSection(final String headingText) {
		final String literal = xpathLiteral(headingText);
		final By locator = By.xpath(
				"(//*[self::section or self::div][.//*[normalize-space()=" + literal + " or contains(normalize-space(), "
						+ literal + ")]])[1]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private boolean hasAnyElement(final WebElement context, final By locator) {
		return !context.findElements(locator).isEmpty();
	}

	private boolean hasVisibleText(final WebElement context, final String text) {
		final String literal = xpathLiteral(text);
		final List<WebElement> matches = context.findElements(
				By.xpath(".//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"));
		return matches.stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean hasAnyVisibleText(final WebElement context, final List<String> options) {
		for (final String option : options) {
			if (hasVisibleText(context, option)) {
				return true;
			}
		}
		return false;
	}

	private void assertVisible(final By locator, final String validationMessage) {
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

	private void assertCondition(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private String waitForNewWindowHandle(final Set<String> currentHandles, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(driverInstance -> driverInstance.getWindowHandles().size() > currentHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!currentHandles.contains(handle)) {
					return handle;
				}
			}
			return null;
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private boolean hasLegalBodyText() {
		final List<WebElement> legalBodyElements = driver.findElements(
				By.xpath("//p[string-length(normalize-space()) > 40] | //li[string-length(normalize-space()) > 40]"));
		return legalBodyElements.stream().anyMatch(WebElement::isDisplayed);
	}

	private By clickableTextLocator(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath(
				"((//*[self::button or self::a or @role='button'][normalize-space()=" + literal
						+ " or contains(normalize-space(), " + literal + ")])[1]"
						+ " | ((//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal
						+ ")])[1]/ancestor-or-self::*[self::button or self::a or @role='button'][1]))[1]");
	}

	private By textLocator(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]");
	}

	private String captureScreenshot(final String checkpointName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = screenshotDirectory.resolve(sanitizeFileName(checkpointName) + ".png");
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		return destination.toString();
	}

	private void writeReport() throws IOException {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append(TEST_NAME).append(System.lineSeparator());
		reportBuilder.append("baseUrl=").append(baseUrl).append(System.lineSeparator());
		reportBuilder.append("generatedAt=").append(runTimestamp).append(System.lineSeparator()).append(System.lineSeparator());

		for (final Map.Entry<String, StepOutcome> entry : stepOutcomes.entrySet()) {
			final StepOutcome outcome = entry.getValue();
			reportBuilder.append(entry.getKey()).append(": ").append(outcome.passed ? "PASS" : "FAIL")
					.append(System.lineSeparator());
			reportBuilder.append("  details: ").append(outcome.details).append(System.lineSeparator());
			if (outcome.finalUrl != null) {
				reportBuilder.append("  final_url: ").append(outcome.finalUrl).append(System.lineSeparator());
			}
			if (!outcome.screenshots.isEmpty()) {
				reportBuilder.append("  screenshots: ").append(String.join(", ", outcome.screenshots))
						.append(System.lineSeparator());
			}
			reportBuilder.append(System.lineSeparator());
		}

		Files.writeString(reportFile, reportBuilder.toString(), StandardCharsets.UTF_8);
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder literalBuilder = new StringBuilder("concat(");
		for (int i = 0; i < value.length(); i++) {
			final String character = value.substring(i, i + 1);
			if (i > 0) {
				literalBuilder.append(", ");
			}
			if ("'".equals(character)) {
				literalBuilder.append("\"'\"");
			} else {
				literalBuilder.append("'").append(character).append("'");
			}
		}
		literalBuilder.append(")");
		return literalBuilder.toString();
	}

	private String sanitizeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9-]+", "-").replaceAll("-+", "-").replaceAll("(^-|-$)", "");
	}

	private String readConfig(final String envKey, final String propertyKey, final String defaultValue) {
		final String fromProperty = System.getProperty(propertyKey);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}

		final String fromEnv = System.getenv(envKey);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv.trim();
		}

		return defaultValue;
	}

	@FunctionalInterface
	private interface StepAction {
		void run(StepOutcome outcome) throws Exception;
	}

	private static final class StepOutcome {
		private boolean passed;
		private String details;
		private String finalUrl;
		private final List<String> screenshots = new ArrayList<>();
	}
}
