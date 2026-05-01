package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Assume;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow for SaleADS "Mi Negocio".
 *
 * <p>
 * The test is intentionally disabled by default. To run:
 * </p>
 *
 * <pre>
 * mvn -Dtest=SaleAdsMiNegocioFullWorkflowTest -Dsaleads.e2e.enabled=true \
 *   -Dsaleads.loginUrl=https://<your-login-page> test
 * </pre>
 */
public class SaleAdsMiNegocioFullWorkflowTest {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";
	private static final String ENABLED_PROPERTY = "saleads.e2e.enabled";
	private static final String LOGIN_URL_PROPERTY = "saleads.loginUrl";
	private static final String HEADLESS_PROPERTY = "saleads.e2e.headless";

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_POLITICA = "Política de Privacidad";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue(
				"Enable this workflow with -D" + ENABLED_PROPERTY + "=true",
				Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false")));

		final Path artifactsDir = createArtifactsDirectory();
		final Path screenshotsDir = artifactsDir.resolve("screenshots");
		Files.createDirectories(screenshotsDir);

		final LinkedHashMap<String, StepResult> report = new LinkedHashMap<>();
		initializeReport(report);

		WebDriver driver = null;
		WebDriverWait wait = null;
		String originalAppWindow = null;

		try {
			driver = createDriver();
			wait = new WebDriverWait(driver, WAIT_TIMEOUT);

			openLoginPageIfConfigured(driver, wait);

			final StepResult loginResult = runStep(() -> {
				final Set<String> handlesBeforeLogin = driver.getWindowHandles();
				clickFirstVisibleText(driver, wait, Arrays.asList("Sign in with Google", "Login with Google",
						"Ingresar con Google", "Continuar con Google", "Google"), true);
				chooseGoogleAccountIfPrompted(driver, wait, handlesBeforeLogin, GOOGLE_ACCOUNT_EMAIL);
				waitForMainInterface(driver, wait);
				ensureSidebarVisible(driver, wait);
				captureScreenshot(driver, screenshotsDir, "01-dashboard-loaded.png");
				return null;
			});
			report.put(REPORT_LOGIN, loginResult);
			if (!loginResult.passed) {
				markRemainingAsSkipped(report, REPORT_LOGIN);
				return;
			}

			originalAppWindow = driver.getWindowHandle();

			final StepResult menuResult = runStep(() -> {
				openMiNegocioMenu(driver, wait);
				waitForVisibleText(driver, wait, "Agregar Negocio");
				waitForVisibleText(driver, wait, "Administrar Negocios");
				captureScreenshot(driver, screenshotsDir, "02-mi-negocio-expanded.png");
				return null;
			});
			report.put(REPORT_MI_NEGOCIO_MENU, menuResult);
			if (!menuResult.passed) {
				markRemainingAsSkipped(report, REPORT_MI_NEGOCIO_MENU);
				return;
			}

			final StepResult modalResult = runStep(() -> {
				clickFirstVisibleText(driver, wait, Arrays.asList("Agregar Negocio"), true);
				waitForVisibleText(driver, wait, "Crear Nuevo Negocio");
				findBusinessNameInput(driver, wait);
				waitForVisibleText(driver, wait, "Tienes 2 de 3 negocios");
				waitForVisibleText(driver, wait, "Cancelar");
				waitForVisibleText(driver, wait, "Crear Negocio");
				captureScreenshot(driver, screenshotsDir, "03-agregar-negocio-modal.png");

				final WebElement input = findBusinessNameInput(driver, wait);
				input.click();
				input.sendKeys(Keys.chord(Keys.CONTROL, "a"), TEST_BUSINESS_NAME);
				clickFirstVisibleText(driver, wait, Arrays.asList("Cancelar"), true);
				waitForElementInvisibility(driver, wait, By.xpath("//*[contains(normalize-space(), 'Crear Nuevo Negocio')]"));
				return null;
			});
			report.put(REPORT_AGREGAR_MODAL, modalResult);
			if (!modalResult.passed) {
				markRemainingAsSkipped(report, REPORT_AGREGAR_MODAL);
				return;
			}

			final StepResult administrarResult = runStep(() -> {
				ensureAdministrarNegociosVisible(driver, wait);
				clickFirstVisibleText(driver, wait, Arrays.asList("Administrar Negocios"), true);
				waitForVisibleText(driver, wait, "Informacion General", "Información General");
				waitForVisibleText(driver, wait, "Detalles de la Cuenta");
				waitForVisibleText(driver, wait, "Tus Negocios");
				waitForVisibleText(driver, wait, "Seccion Legal", "Sección Legal");
				captureScreenshot(driver, screenshotsDir, "04-administrar-negocios.png");
				return null;
			});
			report.put(REPORT_ADMINISTRAR_VIEW, administrarResult);
			if (!administrarResult.passed) {
				markRemainingAsSkipped(report, REPORT_ADMINISTRAR_VIEW);
				return;
			}

			report.put(REPORT_INFO_GENERAL, runStep(() -> {
				validateInformacionGeneral(driver, wait);
				return null;
			}));
			report.put(REPORT_DETALLES_CUENTA, runStep(() -> {
				validateDetallesCuenta(driver, wait);
				return null;
			}));
			report.put(REPORT_TUS_NEGOCIOS, runStep(() -> {
				validateTusNegocios(driver, wait);
				return null;
			}));

			report.put(REPORT_TERMINOS,
					runStepWithDetails(() -> validateLegalPage(driver, wait, screenshotsDir, originalAppWindow,
							Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
							"08-terminos-y-condiciones.png")));

			report.put(REPORT_POLITICA,
					runStepWithDetails(() -> validateLegalPage(driver, wait, screenshotsDir, originalAppWindow,
							Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
							"09-politica-de-privacidad.png")));
		} finally {
			writeReport(artifactsDir.resolve("final-report.txt"), report);
			if (driver != null) {
				driver.quit();
			}
		}

		final boolean allPassed = report.values().stream().allMatch(step -> step.passed);
		assertTrue("At least one workflow validation failed. See target/saleads-e2e/final-report.txt", allPassed);
	}

	private static void initializeReport(final Map<String, StepResult> report) {
		report.put(REPORT_LOGIN, StepResult.pending());
		report.put(REPORT_MI_NEGOCIO_MENU, StepResult.pending());
		report.put(REPORT_AGREGAR_MODAL, StepResult.pending());
		report.put(REPORT_ADMINISTRAR_VIEW, StepResult.pending());
		report.put(REPORT_INFO_GENERAL, StepResult.pending());
		report.put(REPORT_DETALLES_CUENTA, StepResult.pending());
		report.put(REPORT_TUS_NEGOCIOS, StepResult.pending());
		report.put(REPORT_TERMINOS, StepResult.pending());
		report.put(REPORT_POLITICA, StepResult.pending());
	}

	private static WebDriver createDriver() {
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getProperty(HEADLESS_PROPERTY, "false"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		return new ChromeDriver(options);
	}

	private static Path createArtifactsDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Path.of("target", "saleads-e2e", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private static void openLoginPageIfConfigured(final WebDriver driver, final WebDriverWait wait) {
		final String loginUrl = firstNonBlank(System.getProperty(LOGIN_URL_PROPERTY), System.getenv("SALEADS_LOGIN_URL"));
		if (loginUrl != null) {
			driver.get(loginUrl);
			waitForDocumentReady(driver, wait);
			return;
		}

		final String current = driver.getCurrentUrl();
		if (current == null || current.isBlank() || "about:blank".equals(current) || "data:,".equals(current)) {
			throw new IllegalStateException(
					"Provide the environment login URL through -D" + LOGIN_URL_PROPERTY + " or SALEADS_LOGIN_URL.");
		}
	}

	private static void chooseGoogleAccountIfPrompted(final WebDriver driver, final WebDriverWait wait,
			final Set<String> handlesBeforeLogin, final String accountEmail) {
		waitForDocumentReady(driver, wait);
		final String accountSelectorXpath = "//*[@data-identifier=" + xpathLiteral(accountEmail)
				+ " or normalize-space()=" + xpathLiteral(accountEmail) + "]";

		final Set<String> handlesAfterLogin = waitForWindowChange(driver, handlesBeforeLogin);
		if (handlesAfterLogin != null && handlesAfterLogin.size() > handlesBeforeLogin.size()) {
			final String newWindow = findNewWindow(handlesBeforeLogin, handlesAfterLogin);
			driver.switchTo().window(newWindow);
			waitForDocumentReady(driver, wait);
			clickIfPresent(driver, wait, By.xpath(accountSelectorXpath));
			waitForDocumentReady(driver, wait);
			for (String handle : driver.getWindowHandles()) {
				if (handlesBeforeLogin.contains(handle)) {
					driver.switchTo().window(handle);
					return;
				}
			}
			return;
		}

		clickIfPresent(driver, wait, By.xpath(accountSelectorXpath));
		waitForDocumentReady(driver, wait);
	}

	private static void waitForMainInterface(final WebDriver driver, final WebDriverWait wait) {
		waitForAnyVisibleText(driver, wait, Arrays.asList("Mi Negocio", "Negocio", "Dashboard", "Inicio"));
	}

	private static void ensureSidebarVisible(final WebDriver driver, final WebDriverWait wait) {
		final List<By> candidates = Arrays.asList(
				By.xpath("//aside//*[contains(normalize-space(), 'Negocio')]"),
				By.xpath("//*[contains(@class,'sidebar')]//*[contains(normalize-space(), 'Negocio')]"),
				By.xpath("//nav//*[contains(normalize-space(), 'Negocio')]"));

		for (By candidate : candidates) {
			if (isVisible(driver, candidate)) {
				return;
			}
		}

		wait.until(driverRef -> candidates.stream().anyMatch(by -> isVisible(driverRef, by)));
	}

	private static void openMiNegocioMenu(final WebDriver driver, final WebDriverWait wait) {
		clickIfPresent(driver, wait, buildTextLocator("Negocio"));
		clickFirstVisibleText(driver, wait, Arrays.asList("Mi Negocio"), true);
	}

	private static void ensureAdministrarNegociosVisible(final WebDriver driver, final WebDriverWait wait) {
		if (isVisible(driver, buildTextLocator("Administrar Negocios"))) {
			return;
		}
		openMiNegocioMenu(driver, wait);
		waitForVisibleText(driver, wait, "Administrar Negocios");
	}

	private static void validateInformacionGeneral(final WebDriver driver, final WebDriverWait wait) {
		waitForVisibleText(driver, wait, "Informacion General", "Información General");
		waitForVisibleText(driver, wait, "BUSINESS PLAN");
		waitForVisibleText(driver, wait, "Cambiar Plan");

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		if (!EMAIL_PATTERN.matcher(bodyText).find()) {
			throw new AssertionError("No user email visible in Informacion General section.");
		}

		final String userNameXpath = "//*[contains(normalize-space(), 'Informacion General') or contains(normalize-space(), 'Información General')]"
				+ "/ancestor::*[self::section or self::div][1]//*[self::h2 or self::h3 or self::p or self::span]"
				+ "[normalize-space() != '' and not(contains(normalize-space(), '@'))"
				+ " and not(contains(normalize-space(),'BUSINESS PLAN'))"
				+ " and not(contains(normalize-space(),'Cambiar Plan'))]";

		final List<WebElement> candidates = driver.findElements(By.xpath(userNameXpath));
		if (candidates.isEmpty()) {
			throw new AssertionError("No user name candidate found in Informacion General section.");
		}
	}

	private static void validateDetallesCuenta(final WebDriver driver, final WebDriverWait wait) {
		waitForVisibleText(driver, wait, "Detalles de la Cuenta");
		waitForVisibleText(driver, wait, "Cuenta creada");
		waitForVisibleText(driver, wait, "Estado activo");
		waitForVisibleText(driver, wait, "Idioma seleccionado");
	}

	private static void validateTusNegocios(final WebDriver driver, final WebDriverWait wait) {
		waitForVisibleText(driver, wait, "Tus Negocios");
		waitForVisibleText(driver, wait, "Agregar Negocio");
		waitForVisibleText(driver, wait, "Tienes 2 de 3 negocios");

		final By businessItems = By.xpath(
				"//*[contains(normalize-space(), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]"
						+ "//*[self::li or self::tr or contains(@class,'card') or contains(@class,'business')]");
		final List<WebElement> rows = driver.findElements(businessItems);
		if (rows.isEmpty()) {
			throw new AssertionError("No business list items detected in Tus Negocios section.");
		}
	}

	private static String validateLegalPage(final WebDriver driver, final WebDriverWait wait,
			final Path screenshotsDir, final String originalAppWindow, final List<String> visibleTexts,
			final String screenshotFileName) throws IOException {
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		clickFirstVisibleText(driver, wait, visibleTexts, true);
		waitForDocumentReady(driver, wait);

		final Set<String> handlesAfterClick = waitForWindowChange(driver, handlesBeforeClick);
		boolean openedNewTab = handlesAfterClick != null && handlesAfterClick.size() > handlesBeforeClick.size();
		String activeHandle = driver.getWindowHandle();

		if (openedNewTab) {
			final String newHandle = findNewWindow(handlesBeforeClick, handlesAfterClick);
			driver.switchTo().window(newHandle);
			activeHandle = newHandle;
			waitForDocumentReady(driver, wait);
		}

		waitForVisibleText(driver, wait, visibleTexts.toArray(new String[0]));
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		if (bodyText.length() < 150) {
			throw new AssertionError("Legal page content seems too short.");
		}

		captureScreenshot(driver, screenshotsDir, screenshotFileName);
		final String url = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalAppWindow);
			waitForDocumentReady(driver, wait);
		} else if (!activeHandle.equals(originalAppWindow)) {
			driver.switchTo().window(originalAppWindow);
			waitForDocumentReady(driver, wait);
		}

		return "Final URL: " + url;
	}

	private static WebElement findBusinessNameInput(final WebDriver driver, final WebDriverWait wait) {
		final List<By> candidates = Arrays.asList(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@name, 'business')]"));
		for (By candidate : candidates) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(candidate));
			} catch (TimeoutException ignored) {
				// try next selector
			}
		}
		throw new AssertionError("Input 'Nombre del Negocio' not found.");
	}

	private static void waitForElementInvisibility(final WebDriver driver, final WebDriverWait wait, final By locator) {
		wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
		waitForDocumentReady(driver, wait);
	}

	private static void waitForVisibleText(final WebDriver driver, final WebDriverWait wait, final String... text) {
		for (String value : text) {
			final By locator = buildTextLocator(value);
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return;
			} catch (TimeoutException ignored) {
				// try next visible text fallback
			}
		}
		throw new AssertionError("Expected visible text not found: " + Arrays.toString(text));
	}

	private static void waitForAnyVisibleText(final WebDriver driver, final WebDriverWait wait,
			final List<String> options) {
		wait.until(d -> options.stream().anyMatch(value -> isVisible(d, buildTextLocator(value))));
	}

	private static void clickFirstVisibleText(final WebDriver driver, final WebDriverWait wait,
			final List<String> textOptions, final boolean waitAfterClick) {
		for (String text : textOptions) {
			final By locator = buildClickableTextLocator(text);
			try {
				WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
				element.click();
				if (waitAfterClick) {
					waitForDocumentReady(driver, wait);
				}
				return;
			} catch (TimeoutException ignored) {
				// try next text option
			}
		}
		throw new AssertionError("Could not click any expected element by visible text: " + textOptions);
	}

	private static void clickIfPresent(final WebDriver driver, final WebDriverWait wait, final By locator) {
		try {
			WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
			element.click();
			waitForDocumentReady(driver, wait);
		} catch (TimeoutException ignored) {
			// optional element
		}
	}

	private static By buildTextLocator(final String text) {
		final String escaped = xpathLiteral(text);
		return By.xpath("//*[normalize-space()=" + escaped + " or contains(normalize-space(), " + escaped + ")]");
	}

	private static By buildClickableTextLocator(final String text) {
		final String escaped = xpathLiteral(text);
		return By.xpath(
				"//button[normalize-space()=" + escaped + " or contains(normalize-space(), " + escaped + ")]"
						+ " | //a[normalize-space()=" + escaped + " or contains(normalize-space(), " + escaped + ")]"
						+ " | //*[@role='button' and (normalize-space()=" + escaped
						+ " or contains(normalize-space(), " + escaped + "))]"
						+ " | //*[(self::div or self::span) and (normalize-space()=" + escaped
						+ " or contains(normalize-space(), " + escaped + "))]");
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		String[] split = value.split("'");
		StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < split.length; i++) {
			if (i > 0) {
				sb.append(", \"'\", ");
			}
			sb.append("'").append(split[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	private static Set<String> waitForWindowChange(final WebDriver driver, final Set<String> previousHandles) {
		try {
			final WebDriverWait quickWait = new WebDriverWait(driver, Duration.ofSeconds(8));
			return quickWait.until(d -> {
				Set<String> current = d.getWindowHandles();
				if (current.size() != previousHandles.size()) {
					return current;
				}
				return null;
			});
		} catch (TimeoutException ignored) {
			return null;
		}
	}

	private static String findNewWindow(final Set<String> oldHandles, final Set<String> newHandles) {
		for (String handle : newHandles) {
			if (!oldHandles.contains(handle)) {
				return handle;
			}
		}
		return new ArrayList<>(newHandles).get(0);
	}

	private static boolean isVisible(final WebDriver driver, final By locator) {
		try {
			return driver.findElement(locator).isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	private static void waitForDocumentReady(final WebDriver driver, final WebDriverWait wait) {
		wait.until(d -> {
			Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(readyState));
		});
	}

	private static void captureScreenshot(final WebDriver driver, final Path screenshotsDir, final String fileName)
			throws IOException {
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshotsDir.resolve(fileName), screenshot);
	}

	private static StepResult runStep(final StepAction action) {
		try {
			action.run();
			return StepResult.pass();
		} catch (Throwable t) {
			return StepResult.fail(t.getClass().getSimpleName() + ": " + t.getMessage());
		}
	}

	private static StepResult runStepWithDetails(final StepDetailAction action) {
		try {
			final String details = action.run();
			if (details == null || details.isBlank()) {
				return StepResult.pass();
			}
			return StepResult.pass(details);
		} catch (Throwable t) {
			return StepResult.fail(t.getClass().getSimpleName() + ": " + t.getMessage());
		}
	}

	private static void markRemainingAsSkipped(final LinkedHashMap<String, StepResult> report,
			final String completedUntilKey) {
		boolean afterCurrent = false;
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (entry.getKey().equals(completedUntilKey)) {
				afterCurrent = true;
				continue;
			}
			if (afterCurrent && entry.getValue().isPending()) {
				entry.setValue(StepResult.fail("Skipped due to previous failure."));
			}
		}
	}

	private static void writeReport(final Path reportFile, final Map<String, StepResult> report) throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Workflow Report").append(System.lineSeparator());
		sb.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		sb.append(System.lineSeparator());

		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ")
					.append(entry.getValue().passed ? "PASS" : "FAIL");
			if (entry.getValue().details != null && !entry.getValue().details.isBlank()) {
				sb.append(" (").append(entry.getValue().details).append(")");
			}
			sb.append(System.lineSeparator());
		}

		Files.createDirectories(reportFile.getParent());
		Files.writeString(reportFile, sb.toString(), StandardCharsets.UTF_8);
	}

	private static String firstNonBlank(final String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	@FunctionalInterface
	private interface StepDetailAction {
		String run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;
		private final boolean pending;

		private StepResult(final boolean passed, final String details, final boolean pending) {
			this.passed = passed;
			this.details = details;
			this.pending = pending;
		}

		private static StepResult pass() {
			return new StepResult(true, null, false);
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details, false);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details, false);
		}

		private static StepResult pending() {
			return new StepResult(false, "Pending", true);
		}

		private StepResult withDetails(final String newDetails) {
			return new StepResult(this.passed, newDetails, this.pending);
		}

		private boolean isPending() {
			return pending;
		}
	}
}
