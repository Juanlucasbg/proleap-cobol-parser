package io.proleap.cobol.e2e;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(25);
	private static final String REPORT_FILENAME = "final-report.txt";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final List<String> reportOrder = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String accountPageUrl;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this external UI workflow test.",
				Boolean.parseBoolean(getEnv("SALEADS_E2E_ENABLED", "false")));

		for (final String step : reportOrder) {
			report.put(step, StepResult.pending());
		}

		evidenceDir = createEvidenceDirectory();
		driver = buildDriver();
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		final String debuggerAddress = System.getenv("SALEADS_CHROME_DEBUG_ADDRESS");
		if (isBlank(debuggerAddress)) {
			final String loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getenv("SALEADS_BASE_URL"));
			Assume.assumeTrue(
					"Provide SALEADS_LOGIN_URL or SALEADS_BASE_URL when not attaching to an existing Chrome session.",
					!isBlank(loginUrl));
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() throws Exception {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStep("Login", this::performGoogleLoginAndValidateDashboard);
		runStep("Mi Negocio menu", this::openMiNegocioMenuAndValidate);
		runStep("Agregar Negocio modal", this::openAgregarNegocioAndValidateModal);
		runStep("Administrar Negocios view", this::openAdministrarNegociosAndValidateSections);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", () -> termsUrl = validateLegalPage("Términos y Condiciones"));
		runStep("Política de Privacidad", () -> privacyUrl = validateLegalPage("Política de Privacidad"));

		assertAllStepsPassed();
	}

	private void performGoogleLoginAndValidateDashboard() throws Exception {
		clickVisibleTextAny("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Login with Google", "Google");

		handleGoogleAccountSelector("juanlucasbarbiergarzon@gmail.com");

		waitForVisibleTextAny("Negocio", "Mi Negocio");
		final WebElement sidebar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside | //nav")));
		assertNotNull("Left sidebar navigation must be visible after login.", sidebar);
		captureScreenshot("01-dashboard-loaded.png");
	}

	private void openMiNegocioMenuAndValidate() throws Exception {
		clickIfVisibleText("Negocio");
		clickVisibleTextAny("Mi Negocio");
		waitForVisibleTextAny("Agregar Negocio");
		waitForVisibleTextAny("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void openAgregarNegocioAndValidateModal() throws Exception {
		clickVisibleTextAny("Agregar Negocio");
		waitForVisibleTextAny("Crear Nuevo Negocio");
		waitForVisibleTextAny("Nombre del Negocio");
		waitForVisibleTextAny("Tienes 2 de 3 negocios");
		waitForVisibleTextAny("Cancelar");
		waitForVisibleTextAny("Crear Negocio");

		captureScreenshot("03-agregar-negocio-modal.png");

		final WebElement nombreInput = findVisibleElement(By.xpath(
				"//label[normalize-space()='Nombre del Negocio']/following::input[1] | //input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreDelNegocio' or @name='businessName']"));
		assertNotNull("Nombre del Negocio input field should exist.", nombreInput);
		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatizacion");

		clickVisibleTextAny("Cancelar");
		waitForUiToLoad();
	}

	private void openAdministrarNegociosAndValidateSections() throws Exception {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(3))) {
			clickVisibleTextAny("Mi Negocio");
		}

		clickVisibleTextAny("Administrar Negocios");
		waitForVisibleTextAny("Información General");
		waitForVisibleTextAny("Detalles de la Cuenta");
		waitForVisibleTextAny("Tus Negocios");
		waitForVisibleTextAny("Sección Legal");
		captureScreenshot("04-administrar-negocios-view.png");
		accountPageUrl = driver.getCurrentUrl();
	}

	private void validateInformacionGeneral() {
		waitForVisibleTextAny("Información General");
		waitForVisibleTextAny("BUSINESS PLAN");
		waitForVisibleTextAny("Cambiar Plan");

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(bodyText);
		assertTrue("A user email must be visible in Información General.", emailMatcher.find());
		assertTrue("A user name-like value must be visible in Información General.", hasNameLikeText(bodyText));
	}

	private void validateDetallesDeLaCuenta() {
		waitForVisibleTextAny("Detalles de la Cuenta");
		waitForVisibleTextAny("Cuenta creada");
		waitForVisibleTextAny("Estado activo");
		waitForVisibleTextAny("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		waitForVisibleTextAny("Tus Negocios");
		waitForVisibleTextAny("Agregar Negocio");
		waitForVisibleTextAny("Tienes 2 de 3 negocios");

		final WebElement negociosSection = findFirstDisplayed(By.xpath(
				"//*[normalize-space()='Tus Negocios']/ancestor::*[self::section or self::div][1]"));
		assertNotNull("Tus Negocios section should be visible.", negociosSection);
		assertTrue("Business list should have visible content.", negociosSection.getText().trim().length() > 40);
	}

	private String validateLegalPage(final String linkText) throws Exception {
		waitForVisibleTextAny("Sección Legal");
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBeforeClick = driver.getWindowHandles();
		final String fallbackAppUrl = accountPageUrl;

		clickVisibleTextAny(linkText);

		String targetWindow = appWindow;
		try {
			targetWindow = wait.until(anyWindowOpenedOrCurrentHasText(windowsBeforeClick, linkText));
		} catch (final TimeoutException timeoutException) {
			waitForVisibleTextAny(linkText);
		}

		if (!targetWindow.equals(appWindow)) {
			driver.switchTo().window(targetWindow);
			waitForUiToLoad();
		}

		waitForVisibleTextAny(linkText);
		final String legalBodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal content text should be visible for " + linkText + ".", legalBodyText.trim().length() > 120);

		final String screenshotName = "Términos y Condiciones".equals(linkText) ? "05-terminos-condiciones.png"
				: "06-politica-privacidad.png";
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (!targetWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			returnToApplicationPage(fallbackAppUrl);
		}

		return finalUrl;
	}

	private void returnToApplicationPage(final String fallbackAppUrl) {
		try {
			driver.navigate().back();
			waitForVisibleTextAny("Información General");
		} catch (final Exception ignored) {
			if (!isBlank(fallbackAppUrl)) {
				driver.navigate().to(fallbackAppUrl);
				waitForVisibleTextAny("Información General");
			}
		}
	}

	private ExpectedCondition<String> anyWindowOpenedOrCurrentHasText(final Set<String> windowsBeforeClick,
			final String requiredText) {
		return drv -> {
			final Set<String> currentHandles = drv.getWindowHandles();
			if (currentHandles.size() > windowsBeforeClick.size()) {
				for (final String handle : currentHandles) {
					if (!windowsBeforeClick.contains(handle)) {
						return handle;
					}
				}
			}

			if (drv.findElement(By.tagName("body")).getText().contains(requiredText)) {
				return drv.getWindowHandle();
			}

			return null;
		};
	}

	private void handleGoogleAccountSelector(final String accountEmail) {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = driver.getWindowHandles();

		try {
			wait.until(drv -> drv.getWindowHandles().size() > windowsBefore.size()
					|| drv.getCurrentUrl().contains("accounts.google.com"));
		} catch (final TimeoutException ignored) {
			// Google may complete automatically with a saved session.
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!handle.equals(appWindow)) {
				driver.switchTo().window(handle);
				break;
			}
		}

		try {
			if (driver.getCurrentUrl().contains("accounts.google.com")) {
				clickIfVisibleText(accountEmail);
			}
		} catch (final Exception ignored) {
			// If account selection is skipped, continue with dashboard validation.
		}

		if (!driver.getWindowHandle().equals(appWindow)) {
			if (driver.getWindowHandles().contains(appWindow)) {
				driver.switchTo().window(appWindow);
			}
		}

		waitForUiToLoad();
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, StepResult.pass("PASS"));
		} catch (final Throwable throwable) {
			report.put(stepName, StepResult.fail(shortMessage(throwable)));
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				failedSteps.add(entry.getKey() + " -> " + entry.getValue().details);
			}
		}

		assertTrue("Workflow validation failures: " + String.join(" | ", failedSteps), failedSteps.isEmpty());
	}

	private void writeFinalReport() throws Exception {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio Workflow Report").append(System.lineSeparator());
		reportBuilder.append("Generated: ").append(LocalDateTime.now()).append(System.lineSeparator());
		reportBuilder.append(System.lineSeparator());

		for (final String field : reportOrder) {
			final StepResult result = report.getOrDefault(field, StepResult.pending());
			reportBuilder.append(field).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (!isBlank(result.details) && !"PASS".equals(result.details)) {
				reportBuilder.append(" (").append(result.details).append(")");
			}
			reportBuilder.append(System.lineSeparator());
		}

		reportBuilder.append(System.lineSeparator());
		reportBuilder.append("Términos y Condiciones URL: ").append(termsUrl).append(System.lineSeparator());
		reportBuilder.append("Política de Privacidad URL: ").append(privacyUrl).append(System.lineSeparator());

		Files.writeString(evidenceDir.resolve(REPORT_FILENAME), reportBuilder.toString());
		System.out.println(reportBuilder);
	}

	private void clickVisibleTextAny(final String... texts) {
		for (final String text : texts) {
			final WebElement element = findFirstDisplayed(By.xpath("//*[normalize-space()=" + toXpathLiteral(text) + "]"));
			if (element != null) {
				clickAndWait(element);
				return;
			}
		}

		throw new IllegalStateException("Could not find clickable element with visible text: " + Arrays.toString(texts));
	}

	private void clickIfVisibleText(final String text) {
		final WebElement element = findFirstDisplayed(By.xpath("//*[normalize-space()=" + toXpathLiteral(text) + "]"));
		if (element != null) {
			clickAndWait(element);
		}
	}

	private WebElement findVisibleElement(final By locator) {
		try {
			return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final TimeoutException exception) {
			return null;
		}
	}

	private WebElement findFirstDisplayed(final By locator) {
		try {
			waitForUiToLoad();
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		} catch (final Exception exception) {
			return null;
		}
	}

	private void clickAndWait(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForVisibleTextAny(final String... texts) {
		wait.until(drv -> {
			for (final String text : texts) {
				if (hasVisibleText(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			shortWait.until(drv -> hasVisibleText(text));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private boolean hasVisibleText(final String text) {
		final List<WebElement> elements = driver
				.findElements(By.xpath("//*[normalize-space()=" + toXpathLiteral(text) + " or contains(normalize-space(),"
						+ toXpathLiteral(text) + ")]"));

		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}

		return false;
	}

	private void waitForUiToLoad() {
		wait.until(driverReadyStateComplete());
		try {
			Thread.sleep(400L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private ExpectedCondition<Boolean> driverReadyStateComplete() {
		return drv -> {
			final Object readyState = ((JavascriptExecutor) drv).executeScript("return document.readyState");
			return "complete".equals(readyState);
		};
	}

	private Path createEvidenceDirectory() throws Exception {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private void captureScreenshot(final String fileName) throws Exception {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), evidenceDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private WebDriver buildDriver() throws Exception {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (Boolean.parseBoolean(getEnv("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		final String debuggerAddress = System.getenv("SALEADS_CHROME_DEBUG_ADDRESS");
		if (!isBlank(debuggerAddress)) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		}

		final String remoteUrl = System.getenv("SELENIUM_REMOTE_URL");
		if (!isBlank(remoteUrl)) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}

		return new ChromeDriver(options);
	}

	private static boolean hasNameLikeText(final String pageText) {
		final String[] lines = pageText.split("\\R");
		for (final String line : lines) {
			final String candidate = line.trim();
			if (candidate.length() < 4 || candidate.length() > 60) {
				continue;
			}
			if (candidate.contains("@")) {
				continue;
			}
			if (candidate.matches(".*\\d.*")) {
				continue;
			}
			if ("INFORMACIÓN GENERAL".equalsIgnoreCase(candidate) || "BUSINESS PLAN".equalsIgnoreCase(candidate)
					|| "CAMBIAR PLAN".equalsIgnoreCase(candidate)) {
				continue;
			}
			if (candidate.matches("[\\p{L} .'-]+")) {
				return true;
			}
		}
		return false;
	}

	private static String shortMessage(final Throwable throwable) {
		if (throwable.getMessage() == null || throwable.getMessage().isBlank()) {
			return throwable.getClass().getSimpleName();
		}

		final String normalized = throwable.getMessage().replace('\n', ' ').replace('\r', ' ').trim();
		return normalized.length() > 180 ? normalized.substring(0, 180) + "..." : normalized;
	}

	private static String getEnv(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return isBlank(value) ? defaultValue : value;
	}

	private static boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (!isBlank(value)) {
				return value;
			}
		}

		return null;
	}

	private static String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int index = 0; index < parts.length; index++) {
			builder.append("'").append(parts[index]).append("'");
			if (index < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

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

		private static StepResult pending() {
			return new StepResult(false, "NOT_EXECUTED");
		}
	}
}
