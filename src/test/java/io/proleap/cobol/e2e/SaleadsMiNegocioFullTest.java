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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
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
 * End-to-end workflow validation for the SaleADS "Mi Negocio" module.
 *
 * <p>
 * This test is disabled by default so CI remains stable:
 * </p>
 *
 * <pre>
 * mvn -Dtest=SaleadsMiNegocioFullTest \
 *   -Dsaleads.e2e.enabled=true \
 *   -Dsaleads.loginUrl=https://<current-saleads-environment>/login \
 *   test
 * </pre>
 *
 * <p>
 * Optional properties:
 * </p>
 * <ul>
 * <li><code>saleads.headless</code> (default: true)</li>
 * <li><code>saleads.timeout.seconds</code> (default: 30)</li>
 * <li><code>saleads.google.account.email</code> (default:
 * juanlucasbarbiergarzon@gmail.com)</li>
 * <li><code>saleads.chrome.userDataDir</code> (default: empty)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Informacion General", "Detalles de la Cuenta", "Tus Negocios",
			"Terminos y Condiciones", "Politica de Privacidad");

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private final Map<String, String> evidence = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDir;
	private Path reportDir;
	private int screenshotCounter = 0;

	@Before
	public void setUp() throws IOException {
		initializeStepMap();

		final boolean enabled = booleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false);
		Assume.assumeTrue("Enable with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true", enabled);

		final String loginUrl = config("saleads.loginUrl", "SALEADS_LOGIN_URL", "");
		Assume.assumeTrue("Provide the current environment login page using -Dsaleads.loginUrl", !loginUrl.isBlank());

		WebDriverManager.chromedriver().setup();

		final ChromeOptions chromeOptions = new ChromeOptions();
		if (booleanConfig("saleads.headless", "SALEADS_HEADLESS", true)) {
			chromeOptions.addArguments("--headless=new");
		}
		chromeOptions.addArguments("--window-size=1920,1080");
		chromeOptions.addArguments("--disable-dev-shm-usage");
		chromeOptions.addArguments("--no-sandbox");

		final String userDataDir = config("saleads.chrome.userDataDir", "SALEADS_CHROME_USER_DATA_DIR", "");
		if (!userDataDir.isBlank()) {
			chromeOptions.addArguments("--user-data-dir=" + userDataDir);
		}

		driver = new ChromeDriver(chromeOptions);
		wait = new WebDriverWait(driver, Duration.ofSeconds(longConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", 30)));

		screenshotsDir = Path.of("target", "screenshots", "saleads_mi_negocio_full_test");
		reportDir = Path.of("target", "reports");
		Files.createDirectories(screenshotsDir);
		Files.createDirectories(reportDir);

		driver.get(loginUrl);
		waitForUiToSettle();
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep("Login", this::stepLoginWithGoogle, Arrays.asList());
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu, Arrays.asList("Login"));
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal, Arrays.asList("Mi Negocio menu"));
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios, Arrays.asList("Mi Negocio menu"));
		runStep("Informacion General", this::stepValidateInformacionGeneral, Arrays.asList("Administrar Negocios view"));
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta, Arrays.asList("Administrar Negocios view"));
		runStep("Tus Negocios", this::stepValidateTusNegocios, Arrays.asList("Administrar Negocios view"));
		runStep("Terminos y Condiciones", this::stepValidateTerminos, Arrays.asList("Administrar Negocios view"));
		runStep("Politica de Privacidad", this::stepValidatePoliticaPrivacidad, Arrays.asList("Administrar Negocios view"));

		writeFinalReport();
		assertAllStepsPassed();
	}

	@After
	public void tearDown() throws IOException {
		if (driver != null) {
			driver.quit();
		}

		if (Files.exists(reportDir.resolve("saleads_mi_negocio_full_test_report.txt"))) {
			return;
		}

		writeFinalReport();
	}

	private void stepLoginWithGoogle() {
		clickAndWait("Sign in with Google",
				By.xpath("//button[contains(normalize-space(.), 'Google')]"),
				By.xpath("//a[contains(normalize-space(.), 'Google')]"),
				By.xpath("//*[@role='button' and contains(normalize-space(.), 'Google')]"),
				By.xpath("//*[contains(normalize-space(.), 'Sign in with Google')]"),
				By.xpath("//*[contains(normalize-space(.), 'Iniciar con Google')]"));

		handleGoogleWindowAndAccountSelection();

		requireVisible("Main application interface",
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(normalize-space(.), 'Negocio')]"));
		requireVisible("Left sidebar navigation", By.xpath("//aside"), By.xpath("//nav"));

		captureScreenshot("dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() {
		if (!isVisible(By.xpath("//*[contains(normalize-space(.), 'Mi Negocio')]"), Duration.ofSeconds(4))) {
			clickAndWait("Negocio menu", By.xpath("//*[contains(normalize-space(.), 'Negocio')]"));
		}

		clickAndWait("Mi Negocio", By.xpath("//*[contains(normalize-space(.), 'Mi Negocio')]"));

		requireVisible("'Agregar Negocio' visible", By.xpath("//*[contains(normalize-space(.), 'Agregar Negocio')]"));
		requireVisible("'Administrar Negocios' visible", By.xpath("//*[contains(normalize-space(.), 'Administrar Negocios')]"));

		captureScreenshot("mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickAndWait("Agregar Negocio", By.xpath("//*[contains(normalize-space(.), 'Agregar Negocio')]"));

		requireVisible("Modal title 'Crear Nuevo Negocio'",
				By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]"));
		requireVisible("Input field 'Nombre del Negocio'",
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[@name='nombreNegocio' or @name='businessName']"));
		requireVisible("Text 'Tienes 2 de 3 negocios'",
				By.xpath("//*[contains(normalize-space(.), 'Tienes 2 de 3 negocios')]"));
		requireVisible("Button 'Cancelar'",
				By.xpath("//button[contains(normalize-space(.), 'Cancelar')]"));
		requireVisible("Button 'Crear Negocio'",
				By.xpath("//button[contains(normalize-space(.), 'Crear Negocio')]"));

		captureScreenshot("agregar_negocio_modal");

		final WebElement businessNameInput = findAnyVisible("Nombre del Negocio input", DEFAULT_TIMEOUT,
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[@name='nombreNegocio' or @name='businessName']"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		waitForUiToSettle();

		clickAndWait("Cancelar modal", By.xpath("//button[contains(normalize-space(.), 'Cancelar')]"));
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]")));
	}

	private void stepOpenAdministrarNegocios() {
		if (!isVisible(By.xpath("//*[contains(normalize-space(.), 'Administrar Negocios')]"), Duration.ofSeconds(4))) {
			clickAndWait("Expand Mi Negocio again", By.xpath("//*[contains(normalize-space(.), 'Mi Negocio')]"));
		}

		clickAndWait("Administrar Negocios",
				By.xpath("//*[contains(normalize-space(.), 'Administrar Negocios')]"));

		requireVisible("Section 'Informacion General'",
				By.xpath("//*[contains(normalize-space(.), 'Informacion General') or contains(normalize-space(.), 'Información General')]"));
		requireVisible("Section 'Detalles de la Cuenta'",
				By.xpath("//*[contains(normalize-space(.), 'Detalles de la Cuenta')]"));
		requireVisible("Section 'Tus Negocios'",
				By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]"));
		requireVisible("Section 'Seccion Legal'",
				By.xpath("//*[contains(normalize-space(.), 'Seccion Legal') or contains(normalize-space(.), 'Sección Legal')]"));

		captureScreenshot("administrar_negocios_account_page");
	}

	private void stepValidateInformacionGeneral() {
		requireVisible("Informacion General section visible",
				By.xpath("//*[contains(normalize-space(.), 'Informacion General') or contains(normalize-space(.), 'Información General')]"));

		final String fullPageText = fullPageText();
		if (!EMAIL_PATTERN.matcher(fullPageText).find()) {
			throw new AssertionError("User email is not visible.");
		}

		requireVisible("User name marker visible",
				By.xpath("//*[contains(normalize-space(.), 'Nombre')]"),
				By.xpath("//*[contains(normalize-space(.), 'Usuario')]"));
		requireVisible("Text 'BUSINESS PLAN' visible",
				By.xpath("//*[contains(normalize-space(.), 'BUSINESS PLAN')]"));
		requireVisible("Button 'Cambiar Plan' visible",
				By.xpath("//button[contains(normalize-space(.), 'Cambiar Plan')]"));
	}

	private void stepValidateDetallesCuenta() {
		requireVisible("'Cuenta creada' visible",
				By.xpath("//*[contains(normalize-space(.), 'Cuenta creada')]"));
		requireVisible("'Estado activo' visible",
				By.xpath("//*[contains(normalize-space(.), 'Estado activo')]"));
		requireVisible("'Idioma seleccionado' visible",
				By.xpath("//*[contains(normalize-space(.), 'Idioma seleccionado')]"));
	}

	private void stepValidateTusNegocios() {
		final WebElement heading = findAnyVisible("Tus Negocios heading", DEFAULT_TIMEOUT,
				By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]"));

		final WebElement businessesContainer = heading.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		final int listLikeElements = businessesContainer.findElements(By.xpath(".//li | .//tr | .//tbody/tr")).size();
		if (listLikeElements == 0 && businessesContainer.getText().trim().split("\\R").length < 4) {
			throw new AssertionError("Business list is not visible.");
		}

		requireVisible("'Agregar Negocio' button exists",
				By.xpath("//button[contains(normalize-space(.), 'Agregar Negocio')]"),
				By.xpath("//*[contains(normalize-space(.), 'Agregar Negocio')]"));
		requireVisible("'Tienes 2 de 3 negocios' text visible",
				By.xpath("//*[contains(normalize-space(.), 'Tienes 2 de 3 negocios')]"));
	}

	private void stepValidateTerminos() {
		final String finalUrl = validateLegalLink("Terminos y Condiciones",
				By.xpath("//*[contains(normalize-space(.), 'Terminos y Condiciones') or contains(normalize-space(.), 'Términos y Condiciones')]"),
				"terminos_y_condiciones");
		evidence.put("Terminos y Condiciones final URL", finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() {
		final String finalUrl = validateLegalLink("Politica de Privacidad",
				By.xpath("//*[contains(normalize-space(.), 'Politica de Privacidad') or contains(normalize-space(.), 'Política de Privacidad')]"),
				"politica_de_privacidad");
		evidence.put("Politica de Privacidad final URL", finalUrl);
	}

	private String validateLegalLink(final String linkTextForLogs, final By linkLocator, final String screenshotName) {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> initialHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickAndWait(linkTextForLogs, linkLocator);

		final String newHandle = waitForPotentialNewWindow(initialHandles, Duration.ofSeconds(10));
		final boolean openedNewTab = newHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(newHandle);
			waitForUiToSettle();
		}

		requireVisible(linkTextForLogs + " heading", linkLocator);
		final String bodyText = fullPageText();
		if (bodyText.trim().length() < 120) {
			throw new AssertionError("Legal content text is not visible for " + linkTextForLogs + ".");
		}

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToSettle();
		} else {
			driver.navigate().back();
			waitForUiToSettle();
		}

		requireVisible("Returned to SaleADS app",
				By.xpath("//*[contains(normalize-space(.), 'Negocio')]"),
				By.xpath("//aside"),
				By.xpath("//nav"));

		return finalUrl;
	}

	private void handleGoogleWindowAndAccountSelection() {
		final String googleAccountEmail = config("saleads.google.account.email", "SALEADS_GOOGLE_ACCOUNT_EMAIL",
				"juanlucasbarbiergarzon@gmail.com");

		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String newHandle = waitForPotentialNewWindow(handlesBefore, Duration.ofSeconds(6));
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiToSettle();
		}

		if (driver.getCurrentUrl().contains("accounts.google.com") || newHandle != null) {
			if (isVisible(By.xpath("//*[contains(normalize-space(.), " + xPathLiteral(googleAccountEmail) + ")]"),
					Duration.ofSeconds(8))) {
				clickAndWait("Google account selector for " + googleAccountEmail,
						By.xpath("//*[contains(normalize-space(.), " + xPathLiteral(googleAccountEmail) + ")]"));
			}

			waitForPotentialWindowCloseOrRedirect(originalHandle, Duration.ofSeconds(20));
		}

		if (!driver.getWindowHandle().equals(originalHandle) && driver.getWindowHandles().contains(originalHandle)) {
			driver.switchTo().window(originalHandle);
		}

		waitForUiToSettle();
	}

	private void runStep(final String stepName, final StepAction action, final List<String> dependencies) {
		if (!dependencies.isEmpty()) {
			for (final String dependency : dependencies) {
				final String dependencyStatus = stepStatus.get(dependency);
				if (!"PASS".equals(dependencyStatus)) {
					stepStatus.put(stepName, "SKIPPED");
					stepDetails.put(stepName, "Skipped because dependency '" + dependency + "' status was " + dependencyStatus);
					return;
				}
			}
		}

		try {
			action.run();
			stepStatus.put(stepName, "PASS");
		} catch (final Throwable throwable) {
			stepStatus.put(stepName, "FAIL");
			stepDetails.put(stepName, throwable.getMessage() == null ? throwable.toString() : throwable.getMessage());
			try {
				captureScreenshot("failure_" + sanitize(stepName));
			} catch (final RuntimeException ignored) {
				// Keep original failure; screenshot attempt is best-effort.
			}
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failedOrSkipped = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			final String status = stepStatus.getOrDefault(field, "NOT_RUN");
			if (!"PASS".equals(status)) {
				failedOrSkipped.add(field + "=" + status);
			}
		}

		assertTrue("One or more SaleADS Mi Negocio validations failed: " + String.join(", ", failedOrSkipped),
				failedOrSkipped.isEmpty());
	}

	private void writeFinalReport() throws IOException {
		if (reportDir == null) {
			return;
		}

		final Path reportPath = reportDir.resolve("saleads_mi_negocio_full_test_report.txt");
		final StringBuilder report = new StringBuilder();
		report.append("Test: saleads_mi_negocio_full_test").append(System.lineSeparator());
		report.append("Generated at: ").append(Instant.now()).append(System.lineSeparator());
		report.append("Screenshots directory: ")
				.append(screenshotsDir == null ? "n/a" : screenshotsDir.toAbsolutePath())
				.append(System.lineSeparator()).append(System.lineSeparator());

		for (final String reportField : REPORT_FIELDS) {
			final String status = stepStatus.getOrDefault(reportField, "NOT_RUN");
			report.append(reportField).append(": ").append(status).append(System.lineSeparator());

			final String detail = stepDetails.get(reportField);
			if (detail != null && !detail.isBlank()) {
				report.append("  detail: ").append(detail).append(System.lineSeparator());
			}
		}

		report.append(System.lineSeparator()).append("Evidence").append(System.lineSeparator());
		for (final Map.Entry<String, String> entry : evidence.entrySet()) {
			report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}

		Files.writeString(reportPath, report.toString());
	}

	private void initializeStepMap() {
		for (final String field : REPORT_FIELDS) {
			stepStatus.put(field, "NOT_RUN");
		}
	}

	private void captureScreenshot(final String checkpointName) {
		if (driver == null || screenshotsDir == null) {
			return;
		}

		try {
			final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final String filename = String.format("%02d_%s.png", ++screenshotCounter, sanitize(checkpointName));
			final Path destination = screenshotsDir.resolve(filename);
			Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
			evidence.put("screenshot." + checkpointName, destination.toAbsolutePath().toString());
		} catch (final IOException ioException) {
			throw new RuntimeException("Could not save screenshot for checkpoint '" + checkpointName + "'", ioException);
		}
	}

	private void clickAndWait(final String description, final By... locators) {
		final WebElement element = findAnyVisible(description, DEFAULT_TIMEOUT, locators);
		clickElement(element);
		waitForUiToSettle();
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void requireVisible(final String description, final By... locators) {
		findAnyVisible(description, DEFAULT_TIMEOUT, locators);
	}

	private WebElement findAnyVisible(final String description, final Duration timeout, final By... locators) {
		final long timeoutAt = System.nanoTime() + timeout.toNanos();
		Throwable lastThrowable = null;

		while (System.nanoTime() < timeoutAt) {
			for (final By locator : locators) {
				try {
					final List<WebElement> elements = driver.findElements(locator);
					for (final WebElement element : elements) {
						if (element.isDisplayed()) {
							return element;
						}
					}
				} catch (final StaleElementReferenceException staleElementReferenceException) {
					lastThrowable = staleElementReferenceException;
				} catch (final Exception exception) {
					lastThrowable = exception;
				}
			}
			sleep(250);
		}

		throw new AssertionError("Could not find visible element: " + description
				+ (lastThrowable == null ? "" : " (" + lastThrowable.getClass().getSimpleName() + ")"));
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private String waitForPotentialNewWindow(final Set<String> handlesBefore, final Duration timeout) {
		final long timeoutAt = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < timeoutAt) {
			final Set<String> currentHandles = new LinkedHashSet<>(driver.getWindowHandles());
			for (final String handle : currentHandles) {
				if (!handlesBefore.contains(handle)) {
					return handle;
				}
			}
			sleep(200);
		}
		return null;
	}

	private void waitForPotentialWindowCloseOrRedirect(final String originalHandle, final Duration timeout) {
		final long timeoutAt = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < timeoutAt) {
			final Set<String> handles = driver.getWindowHandles();
			if (handles.contains(originalHandle) && handles.size() == 1) {
				driver.switchTo().window(originalHandle);
				return;
			}

			try {
				if (!driver.getCurrentUrl().contains("accounts.google.com")) {
					return;
				}
			} catch (final Exception ignored) {
				// Continue waiting.
			}
			sleep(250);
		}
	}

	private void waitForUiToSettle() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10)).until(webDriver -> {
				final Object readyState = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
				return "interactive".equals(readyState) || "complete".equals(readyState);
			});
		} catch (final Exception ignored) {
			// Some transitions are SPA-only and do not expose reliable readyState changes.
		}
		sleep(500);
	}

	private String fullPageText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private String config(final String propertyName, final String envName, final String defaultValue) {
		final String fromProperty = System.getProperty(propertyName);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}

		final String fromEnv = System.getenv(envName);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv.trim();
		}

		return defaultValue;
	}

	private boolean booleanConfig(final String propertyName, final String envName, final boolean defaultValue) {
		return Boolean.parseBoolean(config(propertyName, envName, String.valueOf(defaultValue)));
	}

	private long longConfig(final String propertyName, final String envName, final long defaultValue) {
		final String value = config(propertyName, envName, String.valueOf(defaultValue));
		try {
			return Long.parseLong(value);
		} catch (final NumberFormatException numberFormatException) {
			return defaultValue;
		}
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting", interruptedException);
		}
	}

	private String sanitize(final String value) {
		return value.toLowerCase()
				.replace('á', 'a')
				.replace('é', 'e')
				.replace('í', 'i')
				.replace('ó', 'o')
				.replace('ú', 'u')
				.replaceAll("[^a-z0-9]+", "_")
				.replaceAll("^_+|_+$", "");
	}

	private String xPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String text = String.valueOf(chars[i]);
			if (i > 0) {
				result.append(",");
			}
			if ("'".equals(text)) {
				result.append("\"").append(text).append("\"");
			} else {
				result.append("'").append(text).append("'");
			}
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
