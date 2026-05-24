package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT = System.getProperty("google.account.email",
			readConfig("GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com"));
	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private String appWindowHandle;
	private String termsAndConditionsUrl = "N/A";
	private String privacyPolicyUrl = "N/A";

	@BeforeClass
	public static void shouldRunOnlyWhenEnabled() {
		final boolean enabled = Boolean.parseBoolean(
				System.getProperty("runSaleadsE2E", readConfig("RUN_SALEADS_E2E", "false")));
		Assume.assumeTrue(
				"This test is opt-in. Set -DrunSaleadsE2E=true (and SALEADS_URL) to run SaleADS workflow validation.",
				enabled);
	}

	@Before
	public void setUp() throws IOException {
		final String saleadsUrl = System.getProperty("saleads.url", readConfig("SALEADS_URL", ""));
		Assume.assumeTrue(
				"SALEADS_URL must point to the SaleADS login page for the current environment.",
				saleadsUrl != null && !saleadsUrl.isBlank());

		evidenceDirectory = Path.of("target", "saleads-evidence", RUN_ID_FORMATTER.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDirectory);

		driver = createWebDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(readInt("SALEADS_WAIT_SECONDS", 30)));

		driver.manage().timeouts().implicitlyWait(Duration.ZERO);
		driver.get(saleadsUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() throws IOException {
		try {
			if (evidenceDirectory != null) {
				writeReport();
			}
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaDePrivacidad);

		assertTrue("Workflow finished with failures:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		final Set<String> handlesBeforeLoginClick = driver.getWindowHandles();
		clickFirstVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Iniciar con Google", "Login with Google");

		selectGoogleAccountIfSelectorAppears(handlesBeforeLoginClick);

		wait.until(ExpectedConditions.or(
				ExpectedConditions.visibilityOfElementLocated(containsTextLocator("Negocio")),
				ExpectedConditions.visibilityOfElementLocated(containsTextLocator("Mi Negocio")),
				ExpectedConditions.visibilityOfElementLocated(containsTextLocator("Dashboard"))));

		assertVisible("Negocio", "Sidebar navigation should contain 'Negocio'.");
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		if (!isVisible("Mi Negocio")) {
			clickFirstVisibleText("Negocio");
		}
		clickFirstVisibleText("Mi Negocio");

		assertVisible("Agregar Negocio", "'Agregar Negocio' should be visible when menu expands.");
		assertVisible("Administrar Negocios", "'Administrar Negocios' should be visible when menu expands.");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickFirstVisibleText("Agregar Negocio");

		assertVisible("Crear Nuevo Negocio", "Modal title 'Crear Nuevo Negocio' must be visible.");
		assertVisible("Nombre del Negocio", "Input label 'Nombre del Negocio' must exist.");
		assertVisible("Tienes 2 de 3 negocios", "Business usage text must be visible.");
		assertVisible("Cancelar", "Button 'Cancelar' must be present.");
		assertVisible("Crear Negocio", "Button 'Crear Negocio' must be present.");

		takeScreenshot("03-agregar-negocio-modal");

		WebElement businessNameInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio')] | //label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]")));
		businessNameInput.click();
		businessNameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		clickFirstVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isVisible("Administrar Negocios")) {
			clickFirstVisibleText("Mi Negocio");
		}
		clickFirstVisibleText("Administrar Negocios");

		assertVisible("Información General", "Section 'Información General' must exist.");
		assertVisible("Detalles de la Cuenta", "Section 'Detalles de la Cuenta' must exist.");
		assertVisible("Tus Negocios", "Section 'Tus Negocios' must exist.");
		assertVisible("Sección Legal", "Section 'Sección Legal' must exist.");
		takeScreenshot("04-administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() {
		assertVisible("BUSINESS PLAN", "Text 'BUSINESS PLAN' must be visible.");
		assertVisible("Cambiar Plan", "Button 'Cambiar Plan' must be visible.");

		String infoSectionText = textNearHeading("Información General");
		Matcher emailMatcher = EMAIL_PATTERN.matcher(infoSectionText);
		assertTrue("A user email should be visible in 'Información General'.", emailMatcher.find());

		String sanitized = infoSectionText
				.replaceAll("Información General|BUSINESS PLAN|Cambiar Plan|\\s+", " ")
				.replaceAll(EMAIL_PATTERN.pattern(), " ").trim();
		assertTrue("A user name should be visible in 'Información General'.", sanitized.length() >= 3);
	}

	private void stepValidateDetallesCuenta() {
		assertVisible("Cuenta creada", "'Cuenta creada' should be visible.");
		assertVisible("Estado activo", "'Estado activo' should be visible.");
		assertVisible("Idioma seleccionado", "'Idioma seleccionado' should be visible.");
	}

	private void stepValidateTusNegocios() {
		assertVisible("Tus Negocios", "Business list section should be visible.");
		assertVisible("Agregar Negocio", "Button 'Agregar Negocio' should exist in 'Tus Negocios'.");
		assertVisible("Tienes 2 de 3 negocios", "Business usage text should be visible in 'Tus Negocios'.");
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		termsAndConditionsUrl = openAndValidateLegalLink("Términos y Condiciones", "Términos y Condiciones",
				"08-terminos-y-condiciones");
	}

	private void stepValidatePoliticaDePrivacidad() throws IOException {
		privacyPolicyUrl = openAndValidateLegalLink("Política de Privacidad", "Política de Privacidad",
				"09-politica-de-privacidad");
	}

	private String openAndValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String previousHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickFirstVisibleText(linkText);

		String openedHandle = waitForNewWindowHandle(handlesBeforeClick, Duration.ofSeconds(10));
		final boolean openedInNewTab = openedHandle != null;

		if (openedInNewTab) {
			driver.switchTo().window(openedHandle);
			waitForUiToLoad();
		}

		assertVisible(expectedHeading, "Legal page heading '" + expectedHeading + "' must be visible.");
		assertLegalBodyVisible(expectedHeading);
		takeScreenshot(screenshotName);

		String destinationUrl = driver.getCurrentUrl();

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(previousHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		assertVisible("Sección Legal", "Application tab should be restored after validating legal links.");
		return destinationUrl;
	}

	private void selectGoogleAccountIfSelectorAppears(final Set<String> handlesBeforeSelection) {
		final String candidateGoogleHandle = waitForNewWindowHandle(handlesBeforeSelection, Duration.ofSeconds(8));

		if (candidateGoogleHandle != null) {
			driver.switchTo().window(candidateGoogleHandle);
			waitForUiToLoad();
		}

		if (isVisible(GOOGLE_ACCOUNT, Duration.ofSeconds(5))) {
			clickFirstVisibleText(GOOGLE_ACCOUNT);
		}

		if (candidateGoogleHandle != null) {
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		}
	}

	private void assertLegalBodyVisible(final String headingText) {
		WebElement body = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//*[contains(normalize-space(.), " + xpathLiteral(headingText)
						+ ")]/ancestor::*[self::main or self::section or self::article or self::div][1]")));
		String bodyText = body.getText().replaceAll("\\s+", " ").trim();
		assertTrue("Legal page should contain readable content.", bodyText.length() > headingText.length() + 100);
	}

	private void runStep(final String reportField, final StepAction action) {
		StepResult stepResult = new StepResult();
		try {
			action.run();
			stepResult.status = "PASS";
			stepResult.details = "Validated successfully.";
		} catch (Exception | AssertionError ex) {
			stepResult.status = "FAIL";
			stepResult.details = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			failures.add(reportField + ": " + stepResult.details);
			try {
				takeScreenshot("error-" + slugify(reportField));
			} catch (IOException ignored) {
				// screenshot capture should not hide the root failure
			}
		}
		report.put(reportField, stepResult);
	}

	private void writeReport() throws IOException {
		Path reportPath = evidenceDirectory.resolve("final-report.txt");
		StringBuilder content = new StringBuilder();
		content.append("SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
		content.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator()).append(System.lineSeparator());
		content.append("Final URL - Términos y Condiciones: ").append(termsAndConditionsUrl).append(System.lineSeparator());
		content.append("Final URL - Política de Privacidad: ").append(privacyPolicyUrl).append(System.lineSeparator())
				.append(System.lineSeparator());
		content.append("Step results:").append(System.lineSeparator());

		for (String step : List.of("Login", "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
				"Información General", "Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones",
				"Política de Privacidad")) {
			StepResult result = report.getOrDefault(step, StepResult.notExecuted());
			content.append("- ").append(step).append(": ").append(result.status).append(" | ").append(result.details)
					.append(System.lineSeparator());
		}

		Files.writeString(reportPath, content.toString());
	}

	private void clickFirstVisibleText(final String... candidates) {
		for (String text : candidates) {
			if (isVisible(text, Duration.ofSeconds(4))) {
				clickVisibleText(text);
				return;
			}
		}
		throw new AssertionError("Unable to find a visible/clickable element with any of texts: " + String.join(", ", candidates));
	}

	private void clickVisibleText(final String text) {
		By locator = clickableContainsTextLocator(text);
		WebElement element = wait.until(driver -> {
			List<WebElement> candidates = driver.findElements(locator);
			for (WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					return candidate;
				}
			}
			return null;
		});

		scrollIntoView(element);
		try {
			element.click();
		} catch (RuntimeException clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void assertVisible(final String text, final String errorMessage) {
		assertTrue(errorMessage, isVisible(text, Duration.ofSeconds(20)));
	}

	private boolean isVisible(final String text) {
		return isVisible(text, Duration.ofSeconds(20));
	}

	private boolean isVisible(final String text, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout)
					.until(ExpectedConditions.visibilityOfElementLocated(containsTextLocator(text)));
			return true;
		} catch (TimeoutException timeoutException) {
			return false;
		}
	}

	private String textNearHeading(final String headingText) {
		WebElement container = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//*[contains(normalize-space(.), " + xpathLiteral(headingText)
						+ ")]/ancestor::*[self::section or self::div or self::main][1]")));
		return container.getText();
	}

	private String waitForNewWindowHandle(final Set<String> handlesBeforeClick, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(currentDriver -> {
				Set<String> currentHandles = currentDriver.getWindowHandles();
				if (currentHandles.size() <= handlesBeforeClick.size()) {
					return null;
				}
				for (String handle : currentHandles) {
					if (!handlesBeforeClick.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (TimeoutException timeoutException) {
			return null;
		}
	}

	private void waitForUiToLoad() {
		if (driver == null || wait == null) {
			return;
		}
		try {
			wait.until(webDriver -> "complete"
					.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		} catch (TimeoutException ignored) {
			// SPAs may keep loading resources; continue with explicit element waits.
		}
		try {
			Thread.sleep(700);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String name) throws IOException {
		Path screenshotPath = evidenceDirectory.resolve(slugify(name) + ".png");
		Files.copy(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath(), screenshotPath,
				StandardCopyOption.REPLACE_EXISTING);
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private WebDriver createWebDriver() throws MalformedURLException {
		ChromeOptions options = new ChromeOptions();
		boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless",
				readConfig("SALEADS_HEADLESS", "false")));
		options.addArguments("--disable-dev-shm-usage", "--no-sandbox", "--lang=es-ES");
		if (headless) {
			options.addArguments("--headless=new", "--window-size=1920,1080");
		} else {
			options.addArguments("--start-maximized");
		}

		String userDataDir = readConfig("CHROME_USER_DATA_DIR", "");
		if (!userDataDir.isBlank()) {
			options.addArguments("--user-data-dir=" + userDataDir);
		}

		String profileDirectory = readConfig("CHROME_PROFILE_DIRECTORY", "");
		if (!profileDirectory.isBlank()) {
			options.addArguments("--profile-directory=" + profileDirectory);
		}

		String remoteSeleniumUrl = System.getProperty("selenium.remote.url", readConfig("SELENIUM_REMOTE_URL", ""));
		if (remoteSeleniumUrl != null && !remoteSeleniumUrl.isBlank()) {
			return new RemoteWebDriver(new URL(remoteSeleniumUrl), options);
		}

		return new ChromeDriver(options);
	}

	private static String readConfig(final String key, final String defaultValue) {
		String value = System.getenv(key);
		return value == null ? defaultValue : value;
	}

	private static int readInt(final String envVar, final int defaultValue) {
		String value = readConfig(envVar, String.valueOf(defaultValue));
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException invalidNumber) {
			return defaultValue;
		}
	}

	private By containsTextLocator(final String text) {
		return By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
	}

	private By clickableContainsTextLocator(final String text) {
		return By.xpath("//*[self::a or self::button or @role='button' or @onclick or contains(@class, 'btn') or contains(@class, 'menu')][contains(normalize-space(.), "
				+ xpathLiteral(text) + ")]");
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		String[] parts = text.split("'");
		StringBuilder literal = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				literal.append(", \"'\", ");
			}
			literal.append("'").append(parts[i]).append("'");
		}
		literal.append(")");
		return literal.toString();
	}

	private String slugify(final String value) {
		return value.toLowerCase()
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-|-$", "");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepResult {
		private String status;
		private String details;

		private static StepResult notExecuted() {
			StepResult stepResult = new StepResult();
			stepResult.status = "FAIL";
			stepResult.details = "Not executed.";
			return stepResult;
		}
	}
}
