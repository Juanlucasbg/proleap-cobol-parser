package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * End-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * Run only when explicitly enabled:
 *
 * <pre>
 * SALEADS_E2E_ENABLED=true
 * SALEADS_LOGIN_URL=https://{current-environment}/login
 * SALEADS_HEADLESS=true
 * </pre>
 */
public class SaleAdsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TERMS_LABEL = "T\u00e9rminos y Condiciones";
	private static final String PRIVACY_LABEL = "Pol\u00edtica de Privacidad";
	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(20);
	private static final Duration SHORT_WAIT = Duration.ofSeconds(5);

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private String appWindowHandle;
	private String termsAndConditionsUrl;
	private String privacyPolicyUrl;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Enable with SALEADS_E2E_ENABLED=true.",
				Boolean.parseBoolean(readConfig("SALEADS_E2E_ENABLED", "false")));

		final String loginUrl = readConfig("SALEADS_LOGIN_URL", "").trim();
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the current environment login page.", !loginUrl.isEmpty());

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		screenshotDirectory = Path.of("target", "saleads-e2e-screenshots", Instant.now().toString().replace(":", "-"));
		Files.createDirectories(screenshotDirectory);

		initReport();

		driver.get(loginUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		printFinalReport();

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
		runStep("Informaci\u00f3n General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("T\u00e9rminos y Condiciones", this::stepValidateTerminosCondiciones);
		runStep("Pol\u00edtica de Privacidad", this::stepValidatePoliticaPrivacidad);

		assertTrue(buildFailureSummary(), failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Iniciar sesion con Google",
				"Continuar con Google", "Acceder con Google", "Ingresar con Google");
		handleGoogleAccountSelectionIfPresent();
		waitForSidebarToBeVisible();
		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02_mi_negocio_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		captureScreenshot("03_agregar_negocio_modal");

		final WebElement nombreNegocioInput = findFirstVisibleElement(
				By.xpath("//label[normalize-space()='Nombre del Negocio']/following::input[1]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));
		nombreNegocioInput.click();
		nombreNegocioInput.sendKeys("Negocio Prueba Automatizacion");
		waitForUiToLoad();

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']")));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios", SHORT_WAIT)) {
			clickByVisibleText("Mi Negocio");
		}
		clickByVisibleText("Administrar Negocios");
		assertAnyTextVisible("Informaci\u00f3n General", "Informacion General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertAnyTextVisible("Secci\u00f3n Legal", "Seccion Legal");
		captureScreenshot("04_administrar_negocios_view_full");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		assertUserNameAndEmailVisible();
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosCondiciones() throws Exception {
		termsAndConditionsUrl = openLegalLinkValidateAndReturn(TERMS_LABEL, "05_terminos_condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		privacyPolicyUrl = openLegalLinkValidateAndReturn(PRIVACY_LABEL, "06_politica_privacidad");
	}

	private void runStep(final String reportField, final CheckedStep step) {
		try {
			step.run();
			report.put(reportField, Boolean.TRUE);
		} catch (final Throwable throwable) {
			report.put(reportField, Boolean.FALSE);
			failures.add(reportField + " => " + throwable.getMessage());
			try {
				captureScreenshot("FAILED_" + sanitizeFileName(reportField));
			} catch (final Exception ignored) {
				// Keep original failure; screenshot errors are secondary.
			}
		}
	}

	private void handleGoogleAccountSelectionIfPresent() {
		final Set<String> existingHandles = driver.getWindowHandles();
		waitForUiToLoad();

		final String googleHandle = waitForNewOrGoogleWindow(existingHandles);
		if (googleHandle != null) {
			driver.switchTo().window(googleHandle);
		}

		final boolean accountFound = clickByVisibleTextIfPresent(SHORT_WAIT, GOOGLE_ACCOUNT_EMAIL);
		if (accountFound) {
			waitForUiToLoad();
		}

		switchBackToAppWindow();
	}

	private void waitForSidebarToBeVisible() {
		wait.until(driver -> isAnyLocatorVisible(
				By.xpath("//aside"),
				By.xpath("//*[contains(@class, 'sidebar')]"),
				By.xpath("//*[normalize-space()='Negocio']"),
				By.xpath("//*[normalize-space()='Mi Negocio']")));
	}

	private String openLegalLinkValidateAndReturn(final String linkText, final String screenshotName) throws Exception {
		final String previousWindow = driver.getWindowHandle();
		final Set<String> beforeClickHandles = driver.getWindowHandles();

		if (TERMS_LABEL.equals(linkText)) {
			clickByVisibleText(TERMS_LABEL, "Terminos y Condiciones");
		} else if (PRIVACY_LABEL.equals(linkText)) {
			clickByVisibleText(PRIVACY_LABEL, "Politica de Privacidad");
		} else {
			clickByVisibleText(linkText);
		}

		String activeLegalWindow = previousWindow;
		final String newWindow = waitForNewWindowHandle(beforeClickHandles);
		if (newWindow != null) {
			activeLegalWindow = newWindow;
			driver.switchTo().window(activeLegalWindow);
			waitForUiToLoad();
		}

		assertAnyTextVisible(linkText, linkText.replace("\u00e9", "e").replace("\u00ed", "i"));
		final String legalPageText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
		assertTrue("Legal content text should be visible for " + linkText + ".", legalPageText != null
				&& legalPageText.trim().length() > 120);
		captureScreenshot(screenshotName);

		final String url = driver.getCurrentUrl();

		if (!activeLegalWindow.equals(previousWindow)) {
			driver.close();
			driver.switchTo().window(previousWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		switchBackToAppWindow();
		return url;
	}

	private void clickByVisibleText(final String... candidates) {
		Throwable lastError = null;

		for (final String candidate : candidates) {
			try {
				final By locator = By.xpath("//*[normalize-space()=" + xpathLiteral(candidate) + "]");
				wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
				waitForUiToLoad();
				return;
			} catch (final Throwable throwable) {
				lastError = throwable;
			}
		}

		throw new AssertionError("Unable to click any visible text candidate: " + String.join(", ", candidates), lastError);
	}

	private boolean clickByVisibleTextIfPresent(final Duration timeout, final String text) {
		try {
			final By locator = By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]");
			new WebDriverWait(driver, timeout).until(ExpectedConditions.elementToBeClickable(locator)).click();
			waitForUiToLoad();
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void assertTextVisible(final String text) {
		final By exact = By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]");
		final By contains = By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]");

		wait.until(driver -> isAnyLocatorVisible(exact, contains));
	}

	private void assertAnyTextVisible(final String... texts) {
		Throwable lastError = null;
		for (final String text : texts) {
			try {
				assertTextVisible(text);
				return;
			} catch (final Throwable throwable) {
				lastError = throwable;
			}
		}
		throw new AssertionError("None of the expected texts are visible: " + String.join(", ", texts), lastError);
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			final By exact = By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]");
			final By contains = By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]");
			new WebDriverWait(driver, timeout).until(driver -> isAnyLocatorVisible(exact, contains));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void assertUserNameAndEmailVisible() {
		final By emailPattern = By.xpath("//*[contains(text(), '@')]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(emailPattern));

		final By userNameElement = By.xpath(
				"//*[contains(@class, 'user') or contains(@class, 'name') or contains(@class, 'profile')][normalize-space()]");
		assertTrue("User name should be visible.",
				isAnyLocatorVisible(userNameElement, By.xpath("//*[contains(normalize-space(), 'Hola')]")));
	}

	private WebElement findFirstVisibleElement(final By... locators) {
		for (final By locator : locators) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final TimeoutException timeoutException) {
				// Try next locator.
			}
		}

		throw new NoSuchElementException("None of the expected elements were visible.");
	}

	private String waitForNewOrGoogleWindow(final Set<String> previousHandles) {
		final long timeoutMillis = SHORT_WAIT.toMillis();
		final long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < timeoutMillis) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				final String url = driver.getCurrentUrl();
				if (!previousHandles.contains(handle) || (url != null && url.contains("accounts.google.com"))) {
					return handle;
				}
			}
			sleep(200);
		}
		return null;
	}

	private String waitForNewWindowHandle(final Set<String> previousHandles) {
		final long timeoutMillis = SHORT_WAIT.toMillis();
		final long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < timeoutMillis) {
			for (final String handle : driver.getWindowHandles()) {
				if (!previousHandles.contains(handle)) {
					return handle;
				}
			}
			sleep(200);
		}
		return null;
	}

	private void switchBackToAppWindow() {
		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			return;
		}

		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			final String currentUrl = driver.getCurrentUrl();
			if (currentUrl != null && !currentUrl.contains("accounts.google.com")) {
				appWindowHandle = handle;
				return;
			}
		}
	}

	private boolean isAnyLocatorVisible(final By... locators) {
		for (final By locator : locators) {
			try {
				if (!driver.findElements(locator).isEmpty() && driver.findElements(locator).get(0).isDisplayed()) {
					return true;
				}
			} catch (final Exception ignored) {
				// Keep trying remaining locators.
			}
		}
		return false;
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String fileName = sanitizeFileName(checkpointName) + ".png";
		Files.copy(screenshot.toPath(), screenshotDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete"
				.equals(((org.openqa.selenium.JavascriptExecutor) driver).executeScript("return document.readyState")));
		sleep(400);
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				builder.append(", ");
			}
			if (chars[i] == '\'') {
				builder.append("\"'\"");
			} else {
				builder.append('\'').append(chars[i]).append('\'');
			}
		}
		builder.append(')');
		return builder.toString();
	}

	private String sanitizeFileName(final String input) {
		return input.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private String readConfig(final String key, final String defaultValue) {
		final String property = System.getProperty(key);
		if (property != null) {
			return property;
		}
		return System.getenv().getOrDefault(key, defaultValue);
	}

	private void initReport() {
		report.put("Login", Boolean.FALSE);
		report.put("Mi Negocio menu", Boolean.FALSE);
		report.put("Agregar Negocio modal", Boolean.FALSE);
		report.put("Administrar Negocios view", Boolean.FALSE);
		report.put("Informaci\u00f3n General", Boolean.FALSE);
		report.put("Detalles de la Cuenta", Boolean.FALSE);
		report.put("Tus Negocios", Boolean.FALSE);
		report.put("T\u00e9rminos y Condiciones", Boolean.FALSE);
		report.put("Pol\u00edtica de Privacidad", Boolean.FALSE);
	}

	private String buildFailureSummary() {
		if (failures.isEmpty()) {
			return "All workflow validations passed.";
		}
		return "Workflow validation failures:\n - " + String.join("\n - ", failures);
	}

	private void printFinalReport() {
		System.out.println("\n=== saleads_mi_negocio_full_test final report ===");
		report.forEach((key, value) -> System.out.println(key + ": " + (Boolean.TRUE.equals(value) ? "PASS" : "FAIL")));
		if (termsAndConditionsUrl != null) {
			System.out.println("T\u00e9rminos y Condiciones URL: " + termsAndConditionsUrl);
		}
		if (privacyPolicyUrl != null) {
			System.out.println("Pol\u00edtica de Privacidad URL: " + privacyPolicyUrl);
		}
		if (screenshotDirectory != null) {
			System.out.println("Screenshots: " + screenshotDirectory.toAbsolutePath());
		}
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}
}
