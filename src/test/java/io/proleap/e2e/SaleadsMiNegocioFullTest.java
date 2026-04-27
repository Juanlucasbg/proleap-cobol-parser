package io.proleap.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio" module.
 *
 * <p>
 * Runtime properties:
 * <ul>
 * <li>-Dsaleads.e2e.enabled=true</li>
 * <li>-Dsaleads.login.url=https://{current-environment-login-page} (recommended)</li>
 * <li>-Dsaleads.browser=chrome|firefox (default chrome)</li>
 * <li>-Dsaleads.headless=true|false (default false)</li>
 * <li>-Dsaleads.expected.user.name={optional exact name}</li>
 * </ul>
 * </p>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}][\\p{L}\\s'.-]{2,}$");

	private final Map<String, StepResult> finalReport = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;
	private String termsFinalUrl = "";
	private String privacyFinalUrl = "";

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Skipping E2E test. Enable with -Dsaleads.e2e.enabled=true.",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().window().setSize(new Dimension(1600, 1200));
		evidenceDir = createEvidenceDirectory();

		final String loginUrl = System.getProperty("saleads.login.url", "").trim();
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
		}

		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaDePrivacidad);

		printFinalReport();
		final List<String> failedSteps = finalReport.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(entry -> entry.getKey() + " -> " + entry.getValue().message).collect(Collectors.toList());
		assertTrue("One or more SaleADS workflow validations failed:\n" + String.join("\n", failedSteps),
				failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		final String initialUrl = safeCurrentUrl();
		if (initialUrl.isEmpty() || initialUrl.startsWith("about:") || initialUrl.equals("data:,")) {
			throw new IllegalStateException(
					"No login URL loaded. Provide -Dsaleads.login.url with the current environment login page.");
		}

		clickByFirstVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google");
		waitForUiLoad();

		selectGoogleAccountIfSelectorIsPresent("juanlucasbarbiergarzon@gmail.com");
		waitForUiLoad();

		assertVisibleText("Negocio");
		assertElementVisible(By.xpath("//aside | //nav"));
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		ensureSidebarVisible();
		clickByFirstVisibleText("Negocio");
		waitForUiLoad();
		clickByFirstVisibleText("Mi Negocio");
		waitForUiLoad();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByFirstVisibleText("Agregar Negocio");
		waitForUiLoad();

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final WebElement nameInput = waitForVisibleElement(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='nombre' or @name='businessName' or @type='text']"));
		nameInput.click();
		nameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "Negocio Prueba Automatización");
		captureScreenshot("03-agregar-negocio-modal");

		clickByFirstVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		tryExpandMiNegocioMenu();
		clickByFirstVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		captureScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		assertVisibleText("Información General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		assertEmailVisible();
		assertUserNameVisible();
	}

	private void stepValidateDetallesDeLaCuenta() throws Exception {
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() throws Exception {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		final List<WebElement> businessItems = driver
				.findElements(By.xpath("//*[contains(@class,'business') or contains(@class,'negocio') or self::li]"));
		final long visibleItems = businessItems.stream().filter(WebElement::isDisplayed).count();
		assertTrue("Expected visible business list items in 'Tus Negocios'.", visibleItems > 0);
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		tryExpandMiNegocioMenu();
		String originalWindow = driver.getWindowHandle();
		Set<String> windowsBefore = driver.getWindowHandles();

		clickByFirstVisibleText("Términos y Condiciones");
		waitForUiLoad();

		switchToNewWindowIfOpened(windowsBefore);
		assertVisibleText("Términos y Condiciones");
		assertLegalContentVisible();
		termsFinalUrl = safeCurrentUrl();
		captureScreenshot("08-terminos-y-condiciones");

		returnToApplicationWindow(originalWindow, windowsBefore);
	}

	private void stepValidatePoliticaDePrivacidad() throws Exception {
		tryExpandMiNegocioMenu();
		String originalWindow = driver.getWindowHandle();
		Set<String> windowsBefore = driver.getWindowHandles();

		clickByFirstVisibleText("Política de Privacidad");
		waitForUiLoad();

		switchToNewWindowIfOpened(windowsBefore);
		assertVisibleText("Política de Privacidad");
		assertLegalContentVisible();
		privacyFinalUrl = safeCurrentUrl();
		captureScreenshot("09-politica-de-privacidad");

		returnToApplicationWindow(originalWindow, windowsBefore);
	}

	private void runStep(String stepName, CheckedRunnable action) {
		try {
			action.run();
			finalReport.put(stepName, StepResult.pass());
		} catch (Throwable throwable) {
			final String screenshotPath = captureScreenshotSilently("fail-" + sanitize(stepName));
			final String details = throwable.getClass().getSimpleName() + ": " + safeMessage(throwable)
					+ (screenshotPath.isEmpty() ? "" : " (screenshot: " + screenshotPath + ")");
			finalReport.put(stepName, StepResult.fail(details));
		}
	}

	private WebDriver createDriver() {
		final String browser = System.getProperty("saleads.browser", "chrome").trim().toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "false"));

		if ("firefox".equals(browser)) {
			final FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			return new FirefoxDriver(options);
		}

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--disable-gpu", "--no-sandbox", "--window-size=1600,1200");
		return new ChromeDriver(options);
	}

	private Path createEvidenceDirectory() throws IOException {
		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path dir = Path.of("target", "evidence", "saleads_mi_negocio_full_test", runId);
		Files.createDirectories(dir);
		return dir;
	}

	private void waitForUiLoad() {
		wait.until((ExpectedCondition<Boolean>) wd -> {
			if (!(wd instanceof JavascriptExecutor)) {
				return true;
			}
			Object state = ((JavascriptExecutor) wd).executeScript("return document.readyState");
			return "complete".equals(state);
		});
		try {
			Thread.sleep(500L);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void ensureSidebarVisible() {
		assertElementVisible(By.xpath("//aside | //nav | //*[contains(@class,'sidebar')]"));
	}

	private void tryExpandMiNegocioMenu() {
		if (!isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			clickByFirstVisibleText("Negocio");
			waitForUiLoad();
			clickByFirstVisibleText("Mi Negocio");
			waitForUiLoad();
		}
	}

	private void selectGoogleAccountIfSelectorIsPresent(String accountEmail) {
		final Duration selectorTimeout = Duration.ofSeconds(15);
		final WebDriverWait shortWait = new WebDriverWait(driver, selectorTimeout);
		try {
			shortWait.until(ExpectedConditions.or(ExpectedConditions.urlContains("accounts.google.com"),
					ExpectedConditions.numberOfWindowsToBe(2)));
		} catch (TimeoutException timeoutException) {
			return;
		}

		final String currentUrl = safeCurrentUrl();
		if (!currentUrl.contains("accounts.google.com")) {
			switchToGoogleWindowIfPresent();
		}

		if (safeCurrentUrl().contains("accounts.google.com")) {
			clickByFirstVisibleText(accountEmail);
		}

		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
		waitForUiLoad();
	}

	private void switchToGoogleWindowIfPresent() {
		for (String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (safeCurrentUrl().contains("accounts.google.com")) {
				return;
			}
		}
	}

	private void switchToNewWindowIfOpened(Set<String> windowsBefore) {
		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);
		try {
			shortWait.until(wd -> wd.getWindowHandles().size() > windowsBefore.size());
		} catch (TimeoutException timeoutException) {
			return;
		}

		for (String handle : driver.getWindowHandles()) {
			if (!windowsBefore.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				return;
			}
		}
	}

	private void returnToApplicationWindow(String originalWindow, Set<String> windowsBefore) {
		String activeWindow = driver.getWindowHandle();
		boolean newWindowWasOpened = !windowsBefore.contains(activeWindow);

		if (newWindowWasOpened) {
			driver.close();
			driver.switchTo().window(originalWindow);
		} else {
			driver.navigate().back();
		}
		waitForUiLoad();
	}

	private void assertLegalContentVisible() {
		final WebElement body = waitForVisibleElement(By.tagName("body"));
		final String text = body.getText() == null ? "" : body.getText().trim();
		assertTrue("Expected legal content text to be visible.", text.length() > 120);
	}

	private void assertUserNameVisible() {
		final String expectedUserName = System.getProperty("saleads.expected.user.name", "").trim();
		if (!expectedUserName.isEmpty()) {
			assertVisibleText(expectedUserName);
			return;
		}

		final List<String> visibleTexts = driver.findElements(By.xpath("//*[self::h1 or self::h2 or self::h3 or self::p or self::span or self::div]"))
				.stream().filter(WebElement::isDisplayed).map(WebElement::getText).map(this::normalize)
				.filter(text -> !text.isEmpty()).collect(Collectors.toList());

		boolean hasName = visibleTexts.stream().anyMatch(text -> NAME_PATTERN.matcher(text).matches() && !looksLikeLabel(text));
		assertTrue(
				"Expected user name to be visible. Set -Dsaleads.expected.user.name for strict validation if needed.",
				hasName);
	}

	private void assertEmailVisible() {
		final List<String> visibleTexts = driver
				.findElements(By.xpath("//*[self::h1 or self::h2 or self::h3 or self::p or self::span or self::div]"))
				.stream().filter(WebElement::isDisplayed).map(WebElement::getText).map(this::normalize)
				.filter(text -> !text.isEmpty()).collect(Collectors.toList());
		boolean hasEmail = visibleTexts.stream().anyMatch(text -> EMAIL_PATTERN.matcher(text).matches());
		assertTrue("Expected user email to be visible.", hasEmail);
	}

	private boolean looksLikeLabel(String text) {
		final String lower = text.toLowerCase(Locale.ROOT);
		return lower.contains("información general") || lower.contains("detalles de la cuenta")
				|| lower.contains("business plan") || lower.contains("cambiar plan") || lower.contains("estado activo")
				|| lower.contains("idioma seleccionado") || lower.contains("cuenta creada")
				|| lower.contains("tus negocios");
	}

	private void clickByFirstVisibleText(String... candidates) {
		Objects.requireNonNull(candidates, "candidates");
		Exception lastException = null;

		for (String text : candidates) {
			try {
				WebElement element = waitForClickableByText(text);
				scrollIntoView(element);
				try {
					element.click();
				} catch (Exception clickException) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				}
				waitForUiLoad();
				return;
			} catch (Exception exception) {
				lastException = exception;
			}
		}

		throw new IllegalStateException(
				"Unable to click any candidate text: " + String.join(", ", candidates), lastException);
	}

	private WebElement waitForClickableByText(String text) {
		String escaped = toXpathLiteral(text);
		String xpath = "//button[contains(normalize-space(.), " + escaped + ")]"
				+ " | //a[contains(normalize-space(.), " + escaped + ")]"
				+ " | //*[@role='button' and contains(normalize-space(.), " + escaped + ")]"
				+ " | //*[contains(@class,'menu') and contains(normalize-space(.), " + escaped + ")]"
				+ " | //*[contains(normalize-space(.), " + escaped + ")]";

		return wait.until(driver -> {
			List<WebElement> elements = driver.findElements(By.xpath(xpath));
			for (WebElement element : elements) {
				if (element.isDisplayed() && element.isEnabled()) {
					return element;
				}
			}
			return null;
		});
	}

	private void assertVisibleText(String text) {
		WebElement element = waitForVisibleText(text);
		assertTrue("Expected text to be visible: " + text, element.isDisplayed());
	}

	private boolean isTextVisible(String text, Duration timeout) {
		WebDriverWait customWait = new WebDriverWait(driver, timeout);
		try {
			customWait.until(driver -> {
				List<WebElement> matches = driver.findElements(By.xpath("//*[contains(normalize-space(.), "
						+ toXpathLiteral(text) + ")]"));
				return matches.stream().anyMatch(WebElement::isDisplayed);
			});
			return true;
		} catch (TimeoutException timeoutException) {
			return false;
		}
	}

	private WebElement waitForVisibleText(String text) {
		String xpath = "//*[contains(normalize-space(.), " + toXpathLiteral(text) + ")]";
		return waitForVisibleElement(By.xpath(xpath));
	}

	private WebElement waitForVisibleElement(By by) {
		return wait.until(driver -> {
			List<WebElement> elements = driver.findElements(by);
			for (WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private void assertElementVisible(By by) {
		WebElement element = waitForVisibleElement(by);
		assertTrue("Expected element to be visible for selector: " + by, element.isDisplayed());
	}

	private void scrollIntoView(WebElement element) {
		if (driver instanceof JavascriptExecutor) {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		}
	}

	private String captureScreenshot(String fileNamePrefix) {
		if (!(driver instanceof TakesScreenshot)) {
			return "";
		}

		String fileName = sanitize(fileNamePrefix) + ".png";
		Path destination = evidenceDir.resolve(fileName);
		try {
			File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
			return destination.toString();
		} catch (IOException ioException) {
			throw new IllegalStateException("Could not save screenshot to " + destination, ioException);
		}
	}

	private String captureScreenshotSilently(String fileNamePrefix) {
		try {
			return captureScreenshot(fileNamePrefix);
		} catch (Exception exception) {
			return "";
		}
	}

	private String safeCurrentUrl() {
		try {
			String currentUrl = driver.getCurrentUrl();
			return currentUrl == null ? "" : currentUrl;
		} catch (Exception exception) {
			return "";
		}
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ");
	}

	private String sanitize(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String toXpathLiteral(String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final List<String> parts = new ArrayList<>();
		for (String part : value.split("'")) {
			parts.add("'" + part + "'");
		}
		return "concat(" + String.join(", \"'\", ", parts) + ")";
	}

	private void printFinalReport() {
		System.out.println("===== SaleADS Mi Negocio Workflow Report =====");
		for (Map.Entry<String, StepResult> entry : finalReport.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue().passed ? "PASS" : "FAIL")
					+ (entry.getValue().message.isEmpty() ? "" : " - " + entry.getValue().message));
		}
		System.out.println("Términos y Condiciones final URL: " + termsFinalUrl);
		System.out.println("Política de Privacidad final URL: " + privacyFinalUrl);
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private String safeMessage(Throwable throwable) {
		return throwable.getMessage() == null ? "(no details)" : throwable.getMessage();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String message;

		private StepResult(boolean passed, String message) {
			this.passed = passed;
			this.message = message;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(String message) {
			return new StepResult(false, message);
		}
	}
}
