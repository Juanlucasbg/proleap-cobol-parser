package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio" module.
 *
 * This test intentionally avoids any hardcoded domain. Set SALEADS_LOGIN_URL for
 * the target environment (dev/staging/prod). When SALEADS_LOGIN_URL is omitted,
 * the test expects a non-empty URL already loaded in the browser session.
 *
 * Required/optional environment variables:
 * - SALEADS_E2E_ENABLED=true           -> enables this test (default false)
 * - SALEADS_LOGIN_URL=https://...      -> optional environment-specific login URL
 * - SALEADS_HEADLESS=true|false        -> browser mode (default true)
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private final List<String> reportOrder = Arrays.asList(REPORT_LOGIN, REPORT_MI_NEGOCIO_MENU, REPORT_AGREGAR_MODAL,
			REPORT_ADMIN_VIEW, REPORT_INFO_GENERAL, REPORT_DETALLES_CUENTA, REPORT_TUS_NEGOCIOS, REPORT_TERMINOS,
			REPORT_PRIVACIDAD);

	private final Map<String, StepStatus> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;

	@Before
	public void setUp() throws IOException {
		for (final String step : reportOrder) {
			report.put(step, StepStatus.notRun());
		}

		screenshotDir = Paths.get("target", "saleads-mi-negocio-e2e-screenshots");
		Files.createDirectories(screenshotDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
		printFinalReport();
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue("Enable this test with SALEADS_E2E_ENABLED=true",
				Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_E2E_ENABLED", "false")));

		initializeDriver();
		openLoginPageIfConfigured();

		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_AGREGAR_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(REPORT_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(REPORT_TERMINOS, () -> stepValidateLegalLink("T\u00e9rminos y Condiciones", "T\u00e9rminos y Condiciones",
				"terminos-y-condiciones"));
		runStep(REPORT_PRIVACIDAD, () -> stepValidateLegalLink("Pol\u00edtica de Privacidad", "Pol\u00edtica de Privacidad",
				"politica-de-privacidad"));

		assertAllStepsPassed();
	}

	private void initializeDriver() {
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));
	}

	private void openLoginPageIfConfigured() {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiLoad();
			return;
		}

		final String currentUrl = driver.getCurrentUrl();
		if (currentUrl == null || "data:,".equals(currentUrl) || "about:blank".equals(currentUrl)) {
			throw new IllegalStateException(
					"Set SALEADS_LOGIN_URL for the current environment when starting from a fresh browser session.");
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google", "Google");
		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");

		assertVisibleAnyText("Negocio", "Mi Negocio");
		assertVisibleAny(By.cssSelector("aside"), By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar')]"));

		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio", "Mi Negocio");
		waitForUiLoad();

		assertVisibleAnyText("Agregar Negocio");
		assertVisibleAnyText("Administrar Negocios");

		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		assertVisibleAnyText("Crear Nuevo Negocio");
		assertVisibleAnyText("Nombre del Negocio");
		assertVisibleAnyText("Tienes 2 de 3 negocios");
		assertVisibleAnyText("Cancelar");
		assertVisibleAnyText("Crear Negocio");

		final WebElement nombreInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//input[@name='nombre' or @name='name' or @placeholder='Nombre del Negocio' or @type='text']")));
		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatizacion");

		captureScreenshot("03-agregar-negocio-modal");

		clickByVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		expandMiNegocioIfCollapsed();
		clickByVisibleText("Administrar Negocios");

		assertVisibleAnyText("Informaci\u00f3n General");
		assertVisibleAnyText("Detalles de la Cuenta");
		assertVisibleAnyText("Tus Negocios");
		assertVisibleAnyText("Secci\u00f3n Legal");

		captureScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleAnyText("BUSINESS PLAN");
		assertVisibleAnyText("Cambiar Plan");

		final String bodyText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
		final Pattern emailPattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
		Assert.assertTrue("Expected an email in Informaci\u00f3n General section.", emailPattern.matcher(bodyText).find());

		// User name can vary by environment; enforce at least one non-email profile label.
		Assert.assertTrue("Expected user name label/value visible.", bodyText.contains("Nombre") || bodyText.contains("Usuario")
				|| bodyText.contains("Perfil"));
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleAnyText("Cuenta creada");
		assertVisibleAnyText("Estado activo");
		assertVisibleAnyText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleAnyText("Tus Negocios");
		assertVisibleAnyText("Agregar Negocio");
		assertVisibleAnyText("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(final String linkText, final String headingText, final String screenshotName)
			throws Exception {
		final String applicationWindow = driver.getWindowHandle();
		final Set<String> windowsBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);

		final String legalWindowHandle = waitForLegalWindow(windowsBeforeClick);
		if (legalWindowHandle != null) {
			driver.switchTo().window(legalWindowHandle);
			waitForUiLoad();
		}

		assertVisibleAnyText(headingText);
		final String legalBodyText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
		Assert.assertTrue("Expected visible legal content for " + headingText, legalBodyText.length() > 120);

		legalUrls.put(headingText, driver.getCurrentUrl());
		captureScreenshot(screenshotName);

		if (legalWindowHandle != null) {
			driver.close();
			driver.switchTo().window(applicationWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private String waitForLegalWindow(final Set<String> windowsBeforeClick) {
		try {
			wait.until(driver -> driver.getWindowHandles().size() > windowsBeforeClick.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!windowsBeforeClick.contains(handle)) {
					return handle;
				}
			}
		} catch (final TimeoutException ignored) {
			// Link opened in same tab, which is also valid.
		}
		return null;
	}

	private void expandMiNegocioIfCollapsed() throws Exception {
		if (isTextVisible("Administrar Negocios")) {
			return;
		}
		clickByVisibleText("Negocio", "Mi Negocio");
		waitForUiLoad();
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final Duration shortTimeout = Duration.ofSeconds(12);
		final WebDriverWait shortWait = new WebDriverWait(driver, shortTimeout);
		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() > 1) {
			final String latestHandle = new ArrayList<>(handles).get(handles.size() - 1);
			driver.switchTo().window(latestHandle);
		}

		try {
			final WebElement accountOption = shortWait.until(ExpectedConditions.elementToBeClickable(By
					.xpath("//*[contains(normalize-space(.)," + quoteXpath(email) + ")]")));
			accountOption.click();
			waitForUiLoad();
		} catch (final TimeoutException ignored) {
			// Account chooser may not appear when session is already authenticated.
		}

		// Ensure focus is returned to the primary app window if available.
		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (!driver.getCurrentUrl().contains("accounts.google.com")) {
				return;
			}
		}
	}

	private void clickByVisibleText(final String... texts) throws Exception {
		Exception lastError = null;
		for (final String text : texts) {
			final By clickableByText = By.xpath(
					"//button[contains(normalize-space(.)," + quoteXpath(text) + ")]"
							+ " | //a[contains(normalize-space(.)," + quoteXpath(text) + ")]"
							+ " | //*[@role='button' and contains(normalize-space(.)," + quoteXpath(text) + ")]"
							+ " | //*[contains(normalize-space(.)," + quoteXpath(text) + ") and (self::span or self::div)]");

			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(clickableByText));
				element.click();
				waitForUiLoad();
				return;
			} catch (final Exception e) {
				lastError = e;
			}
		}

		throw new AssertionError("Could not click any element using visible texts: " + Arrays.toString(texts), lastError);
	}

	private void assertVisibleAnyText(final String... texts) {
		AssertionError lastError = null;
		for (final String text : texts) {
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(
						By.xpath("//*[contains(normalize-space(.)," + quoteXpath(text) + ")]")));
				return;
			} catch (final TimeoutException e) {
				lastError = new AssertionError("Text not visible: " + text, e);
			}
		}
		throw new AssertionError("None of these texts were visible: " + Arrays.toString(texts), lastError);
	}

	private void assertVisibleAny(final By... locators) {
		AssertionError lastError = null;
		for (final By locator : locators) {
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return;
			} catch (final TimeoutException e) {
				lastError = new AssertionError("Locator not visible: " + locator, e);
			}
		}
		throw new AssertionError("None of the locators became visible.", lastError);
	}

	private boolean isTextVisible(final String text) {
		return !driver
				.findElements(By.xpath("//*[contains(normalize-space(.)," + quoteXpath(text) + ")]")).isEmpty();
	}

	private void waitForUiLoad() {
		final ExpectedCondition<Boolean> documentReady = driver -> {
			if (!(driver instanceof JavascriptExecutor)) {
				return true;
			}
			final Object readyState = ((JavascriptExecutor) driver).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(readyState));
		};

		wait.until(documentReady);

		try {
			Thread.sleep(500);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String name) throws IOException {
		final String timestamp = TS_FORMATTER.format(Instant.now());
		final Path screenshotPath = screenshotDir.resolve(timestamp + "-" + name + ".png");
		final byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshotPath, png);
		System.out.println("SCREENSHOT: " + screenshotPath.toAbsolutePath());
	}

	private void runStep(final String reportField, final StepAction step) {
		try {
			step.run();
			report.put(reportField, StepStatus.pass());
		} catch (final Throwable t) {
			report.put(reportField, StepStatus.fail(t.getClass().getSimpleName() + ": " + safeMessage(t.getMessage())));
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failedSteps = new ArrayList<>();
		for (final String step : reportOrder) {
			if (!report.get(step).passed) {
				failedSteps.add(step + " -> " + report.get(step).detail);
			}
		}
		Assert.assertTrue("Workflow had failed validations: " + failedSteps, failedSteps.isEmpty());
	}

	private void printFinalReport() {
		System.out.println("==== SaleADS Mi Negocio Final Report ====");
		for (final String step : reportOrder) {
			final StepStatus status = report.get(step);
			final String state = status.passed ? "PASS" : status.notRun ? "NOT RUN" : "FAIL";
			final String detail = (status.detail == null || status.detail.isBlank()) ? "" : " | " + status.detail;
			System.out.println(step + ": " + state + detail);
		}

		if (!legalUrls.isEmpty()) {
			System.out.println("---- Legal URLs ----");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(entry.getKey() + ": " + entry.getValue());
			}
		}
	}

	private String quoteXpath(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder concat = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String chunk = chars[i] == '\'' ? "\"'\"" : "'" + chars[i] + "'";
			concat.append(chunk);
			if (i < chars.length - 1) {
				concat.append(",");
			}
		}
		concat.append(")");
		return concat.toString();
	}

	private String safeMessage(final String message) {
		if (message == null) {
			return "(no message)";
		}
		return message.replace('\n', ' ').replace('\r', ' ');
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepStatus {
		private final boolean passed;
		private final boolean notRun;
		private final String detail;

		private StepStatus(final boolean passed, final boolean notRun, final String detail) {
			this.passed = passed;
			this.notRun = notRun;
			this.detail = detail;
		}

		private static StepStatus pass() {
			return new StepStatus(true, false, null);
		}

		private static StepStatus fail(final String detail) {
			return new StepStatus(false, false, detail);
		}

		private static StepStatus notRun() {
			return new StepStatus(false, true, null);
		}
	}
}
