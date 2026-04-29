package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
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

/**
 * End-to-end workflow test for SaleADS "Mi Negocio" module.
 *
 * <p>
 * Execution is opt-in to avoid impacting default repository tests:
 * </p>
 *
 * <pre>
 * mvn -Dtest=SaleadsMiNegocioFullTest -Dsaleads.e2e.enabled=true test
 * </pre>
 *
 * <p>
 * Optional properties:
 * </p>
 * <ul>
 * <li>saleads.baseUrl (or SALEADS_BASE_URL env var)</li>
 * <li>saleads.remoteUrl (or SELENIUM_REMOTE_URL env var)</li>
 * <li>saleads.headless=true|false (default false)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(25);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Informaci\u00f3n General", "Detalles de la Cuenta", "Tus Negocios",
			"T\u00e9rminos y Condiciones", "Pol\u00edtica de Privacidad");

	private final Map<String, String> report = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String appWindowHandle;
	private int screenshotCounter = 1;
	private String termsFinalUrl = "";
	private String privacyFinalUrl = "";

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue("Set -Dsaleads.e2e.enabled=true to run SaleADS E2E workflow.", enabled);

		driver = newWebDriver();
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		screenshotDir = createScreenshotDir();
		initializeReport();

		driver.manage().window().setSize(new Dimension(1440, 1080));

		final String baseUrl = firstNonBlank(System.getProperty("saleads.baseUrl"), System.getenv("SALEADS_BASE_URL"));
		if (isNonBlank(baseUrl)) {
			driver.get(baseUrl);
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final boolean loginOk = runLoginStep();
		final boolean menuOk = loginOk ? runMiNegocioMenuStep() : markDependencyFailure("Mi Negocio menu", "Login");
		final boolean modalOk = menuOk ? runAgregarNegocioModalStep()
				: markDependencyFailure("Agregar Negocio modal", "Mi Negocio menu");
		final boolean administrarOk = menuOk ? runAdministrarNegociosStep()
				: markDependencyFailure("Administrar Negocios view", "Mi Negocio menu");

		if (administrarOk) {
			runInformacionGeneralStep();
			runDetallesCuentaStep();
			runTusNegociosStep();
			runTerminosStep();
			runPoliticaPrivacidadStep();
		} else {
			markDependencyFailure("Informaci\u00f3n General", "Administrar Negocios view");
			markDependencyFailure("Detalles de la Cuenta", "Administrar Negocios view");
			markDependencyFailure("Tus Negocios", "Administrar Negocios view");
			markDependencyFailure("T\u00e9rminos y Condiciones", "Administrar Negocios view");
			markDependencyFailure("Pol\u00edtica de Privacidad", "Administrar Negocios view");
		}

		writeFinalReport();
		assertAllStepsPass();
	}

	private boolean runLoginStep() {
		try {
			final WebElement loginButton = findClickableByAnyText(12, "Sign in with Google", "Iniciar sesi\u00f3n con Google",
					"Continuar con Google", "Login with Google", "Google");
			if (loginButton != null) {
				clickAndWait(loginButton);
			}

			selectGoogleAccountIfPresent("juanlucasbarbiergarzon@gmail.com");
			waitForUiLoad();

			ensureAnyTextVisible(15, "Negocio", "Mi Negocio", "Dashboard", "Panel");
			final boolean sidebarVisible = isAnyVisible(8,
					By.xpath("//aside//*[contains(normalize-space(.),'Negocio') or contains(normalize-space(.),'Mi Negocio')]"),
					By.xpath("//nav//*[contains(normalize-space(.),'Negocio') or contains(normalize-space(.),'Mi Negocio')]"));
			if (!sidebarVisible) {
				throw new AssertionError("No se detect\u00f3 la navegaci\u00f3n lateral con 'Negocio' o 'Mi Negocio'.");
			}

			appWindowHandle = driver.getWindowHandle();
			takeScreenshot("01-dashboard-loaded");
			markPass("Login");
			return true;
		} catch (Exception ex) {
			markFail("Login", ex.getMessage());
			return false;
		}
	}

	private boolean runMiNegocioMenuStep() {
		try {
			clickByAnyText("Negocio", "Mi Negocio");
			waitForUiLoad();
			clickByAnyText("Mi Negocio");
			waitForUiLoad();

			ensureTextVisible(10, "Agregar Negocio");
			ensureTextVisible(10, "Administrar Negocios");
			takeScreenshot("02-mi-negocio-menu-expanded");
			markPass("Mi Negocio menu");
			return true;
		} catch (Exception ex) {
			markFail("Mi Negocio menu", ex.getMessage());
			return false;
		}
	}

	private boolean runAgregarNegocioModalStep() {
		try {
			clickByAnyText("Agregar Negocio");
			waitForUiLoad();

			ensureTextVisible(12, "Crear Nuevo Negocio");
			final WebElement nombreInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
					"//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder,'Nombre del Negocio')]")));
			ensureTextVisible(8, "Tienes 2 de 3 negocios");
			ensureTextVisible(8, "Cancelar");
			ensureTextVisible(8, "Crear Negocio");

			takeScreenshot("03-agregar-negocio-modal");

			nombreInput.clear();
			nombreInput.sendKeys("Negocio Prueba Automatizacion");
			clickByAnyText("Cancelar");
			waitForUiLoad();

			markPass("Agregar Negocio modal");
			return true;
		} catch (Exception ex) {
			markFail("Agregar Negocio modal", ex.getMessage());
			return false;
		}
	}

	private boolean runAdministrarNegociosStep() {
		try {
			if (!isTextCurrentlyVisible("Administrar Negocios")) {
				clickByAnyText("Mi Negocio");
				waitForUiLoad();
			}

			clickByAnyText("Administrar Negocios");
			waitForUiLoad();

			ensureTextVisible(15, "Informaci\u00f3n General");
			ensureTextVisible(15, "Detalles de la Cuenta");
			ensureTextVisible(15, "Tus Negocios");
			ensureTextVisible(15, "Secci\u00f3n Legal");
			takeFullPageScreenshot("04-administrar-negocios-view");

			appWindowHandle = driver.getWindowHandle();
			markPass("Administrar Negocios view");
			return true;
		} catch (Exception ex) {
			markFail("Administrar Negocios view", ex.getMessage());
			return false;
		}
	}

	private boolean runInformacionGeneralStep() {
		try {
			final String sectionText = extractSectionText("Informaci\u00f3n General");
			final boolean hasEmail = EMAIL_PATTERN.matcher(sectionText).find();
			final boolean hasBusinessPlan = sectionText.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN");
			final boolean hasChangePlanButton = isTextCurrentlyVisible("Cambiar Plan");
			final boolean hasUserName = sectionText.lines().map(String::trim).filter(s -> !s.isEmpty())
					.anyMatch(s -> !s.equalsIgnoreCase("Informaci\u00f3n General") && !EMAIL_PATTERN.matcher(s).find()
							&& !s.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN")
							&& !s.equalsIgnoreCase("Cambiar Plan"));

			if (!hasUserName) {
				throw new AssertionError("No se detect\u00f3 nombre de usuario visible en Informaci\u00f3n General.");
			}
			if (!hasEmail) {
				throw new AssertionError("No se detect\u00f3 correo visible en Informaci\u00f3n General.");
			}
			if (!hasBusinessPlan) {
				throw new AssertionError("No se detect\u00f3 texto 'BUSINESS PLAN'.");
			}
			if (!hasChangePlanButton) {
				throw new AssertionError("No se detect\u00f3 bot\u00f3n 'Cambiar Plan'.");
			}

			markPass("Informaci\u00f3n General");
			return true;
		} catch (Exception ex) {
			markFail("Informaci\u00f3n General", ex.getMessage());
			return false;
		}
	}

	private boolean runDetallesCuentaStep() {
		try {
			ensureTextVisible(8, "Cuenta creada");
			ensureTextVisible(8, "Estado activo");
			ensureTextVisible(8, "Idioma seleccionado");
			markPass("Detalles de la Cuenta");
			return true;
		} catch (Exception ex) {
			markFail("Detalles de la Cuenta", ex.getMessage());
			return false;
		}
	}

	private boolean runTusNegociosStep() {
		try {
			ensureTextVisible(10, "Tus Negocios");
			ensureTextVisible(8, "Agregar Negocio");
			ensureTextVisible(8, "Tienes 2 de 3 negocios");

			final String sectionText = extractSectionText("Tus Negocios");
			final long nonEmptyLines = sectionText.lines().map(String::trim).filter(s -> !s.isEmpty()).count();
			if (nonEmptyLines < 3) {
				throw new AssertionError("No se detect\u00f3 una lista de negocios visible.");
			}

			markPass("Tus Negocios");
			return true;
		} catch (Exception ex) {
			markFail("Tus Negocios", ex.getMessage());
			return false;
		}
	}

	private boolean runTerminosStep() {
		try {
			termsFinalUrl = validateLegalPage("T\u00e9rminos y Condiciones", "T\u00e9rminos y Condiciones",
					"05-terminos-y-condiciones");
			markPass("T\u00e9rminos y Condiciones");
			return true;
		} catch (Exception ex) {
			markFail("T\u00e9rminos y Condiciones", ex.getMessage());
			return false;
		}
	}

	private boolean runPoliticaPrivacidadStep() {
		try {
			privacyFinalUrl = validateLegalPage("Pol\u00edtica de Privacidad", "Pol\u00edtica de Privacidad",
					"06-politica-de-privacidad");
			markPass("Pol\u00edtica de Privacidad");
			return true;
		} catch (Exception ex) {
			markFail("Pol\u00edtica de Privacidad", ex.getMessage());
			return false;
		}
	}

	private String validateLegalPage(final String linkText, final String headingText, final String screenshotName) {
		final Set<String> beforeHandles = driver.getWindowHandles();
		clickByAnyText(linkText);
		waitForUiLoad();

		boolean openedNewTab = false;
		if (driver.getWindowHandles().size() > beforeHandles.size()) {
			openedNewTab = true;
			for (final String handle : driver.getWindowHandles()) {
				if (!beforeHandles.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForUiLoad();
		}

		ensureTextVisible(15, headingText);
		final String bodyText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
		if (bodyText.trim().length() < 80) {
			throw new AssertionError("El contenido legal visible es demasiado corto para validarse.");
		}

		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiLoad();

		ensureTextVisible(15, "Secci\u00f3n Legal");
		return finalUrl;
	}

	private void writeFinalReport() throws IOException {
		final Path reportFile = screenshotDir.resolve("final-report.txt");
		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		sb.append("timestamp=").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
				.append(System.lineSeparator());
		sb.append("screenshots_path=").append(screenshotDir.toAbsolutePath()).append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("RESULTS").append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			sb.append("- ").append(field).append(": ").append(report.getOrDefault(field, "FAIL - Missing result"))
					.append(System.lineSeparator());
		}

		sb.append(System.lineSeparator());
		sb.append("EVIDENCE").append(System.lineSeparator());
		sb.append("- Terminos y Condiciones URL: ").append(termsFinalUrl).append(System.lineSeparator());
		sb.append("- Politica de Privacidad URL: ").append(privacyFinalUrl).append(System.lineSeparator());

		Files.writeString(reportFile, sb.toString());
		System.out.println(sb);
		System.out.println("Final report written to: " + reportFile.toAbsolutePath());
	}

	private void assertAllStepsPass() {
		for (final String field : REPORT_FIELDS) {
			final String result = report.getOrDefault(field, "FAIL - Missing result");
			Assert.assertTrue("Validation failed for '" + field + "': " + result, result.startsWith("PASS"));
		}
	}

	private WebDriver newWebDriver() {
		final String remoteUrl = firstNonBlank(System.getProperty("saleads.remoteUrl"), System.getenv("SELENIUM_REMOTE_URL"));
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "false"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		if (isNonBlank(remoteUrl)) {
			try {
				return new RemoteWebDriver(new URL(remoteUrl), options);
			} catch (MalformedURLException ex) {
				throw new IllegalArgumentException("Invalid saleads.remoteUrl: " + remoteUrl, ex);
			}
		}

		return new ChromeDriver(options);
	}

	private Path createScreenshotDir() throws IOException {
		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		final Path dir = Paths.get("target", "saleads-e2e", "screenshots", runId);
		Files.createDirectories(dir);
		return dir;
	}

	private String takeScreenshot(final String checkpointName) {
		final String safeName = checkpointName.replaceAll("[^a-zA-Z0-9._-]", "_");
		final String fileName = String.format("%02d_%s.png", screenshotCounter++, safeName);
		final Path output = screenshotDir.resolve(fileName);
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		try {
			Files.copy(screenshot.toPath(), output, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			throw new RuntimeException("No se pudo guardar screenshot en " + output.toAbsolutePath(), ex);
		}
		return output.toAbsolutePath().toString();
	}

	private String takeFullPageScreenshot(final String checkpointName) {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			final Number pageWidth = (Number) js
					.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, document.documentElement.clientWidth);");
			final Number pageHeight = (Number) js
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, document.documentElement.clientHeight);");

			final int targetWidth = Math.min(Math.max(pageWidth.intValue(), 1280), 1920);
			final int targetHeight = Math.min(Math.max(pageHeight.intValue(), 1080), 5000);
			driver.manage().window().setSize(new Dimension(targetWidth, targetHeight));
			waitForUiLoad();
			return takeScreenshot(checkpointName);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiLoad();
		}
	}

	private void clickByAnyText(final String... texts) {
		final WebElement element = findClickableByAnyText(15, texts);
		if (element == null) {
			throw new AssertionError("No clickable element found for texts: " + Arrays.toString(texts));
		}
		clickAndWait(element);
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiLoad();
	}

	private void waitForUiLoad() {
		try {
			wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		} catch (TimeoutException ignored) {
			// SPA transitions may not change readyState, continue.
		}
	}

	private void ensureTextVisible(final int timeoutSeconds, final String text) {
		waitForVisibleText(timeoutSeconds, text);
	}

	private void ensureAnyTextVisible(final int timeoutSeconds, final String... texts) {
		AssertionError lastError = null;
		for (final String text : texts) {
			try {
				waitForVisibleText(timeoutSeconds, text);
				return;
			} catch (AssertionError ex) {
				lastError = ex;
			}
		}
		throw new AssertionError("No se encontr\u00f3 ninguno de los textos visibles: " + Arrays.toString(texts), lastError);
	}

	private void waitForVisibleText(final int timeoutSeconds, final String text) {
		final By by = By.xpath("//*[contains(normalize-space(.),\"" + escapeXpath(text) + "\")]");
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(by));
		} catch (TimeoutException ex) {
			throw new AssertionError("No se encontr\u00f3 texto visible: " + text, ex);
		}
	}

	private boolean isTextCurrentlyVisible(final String text) {
		try {
			return !driver.findElements(By.xpath("//*[contains(normalize-space(.),\"" + escapeXpath(text) + "\")]")).isEmpty();
		} catch (Exception ex) {
			return false;
		}
	}

	private boolean isAnyVisible(final int timeoutSeconds, final By... selectors) {
		final WebDriverWait customWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		for (final By selector : selectors) {
			try {
				customWait.until(ExpectedConditions.visibilityOfElementLocated(selector));
				return true;
			} catch (TimeoutException ignored) {
				// try next selector
			}
		}
		return false;
	}

	private WebElement findClickableByAnyText(final int timeoutSeconds, final String... texts) {
		final WebDriverWait customWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		for (final String text : texts) {
			final By by = By.xpath(
					"//*[self::button or self::a or @role='button' or self::span or self::div][contains(normalize-space(.),\""
							+ escapeXpath(text) + "\")]");
			try {
				return customWait.until(ExpectedConditions.elementToBeClickable(by));
			} catch (TimeoutException ignored) {
				// try next text
			}
		}
		return null;
	}

	private String extractSectionText(final String sectionHeadingText) {
		final String escaped = escapeXpath(sectionHeadingText);
		final By sectionBy = By.xpath(
				"(//*[contains(normalize-space(.),\"" + escaped + "\")]/ancestor::*[self::section or self::div])[1]");
		try {
			return wait.until(ExpectedConditions.visibilityOfElementLocated(sectionBy)).getText();
		} catch (TimeoutException ex) {
			throw new AssertionError("No se pudo obtener texto de secci\u00f3n: " + sectionHeadingText, ex);
		}
	}

	private void selectGoogleAccountIfPresent(final String email) {
		try {
			final WebElement account = findClickableByAnyText(10, email);
			if (account != null) {
				clickAndWait(account);
			}
		} catch (Exception ignored) {
			// If account chooser does not appear, continue.
		}
	}

	private String firstNonBlank(final String first, final String second) {
		if (isNonBlank(first)) {
			return first.trim();
		}
		if (isNonBlank(second)) {
			return second.trim();
		}
		return null;
	}

	private boolean isNonBlank(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	private String escapeXpath(final String value) {
		return value.replace("\"", "\\\"");
	}

	private void initializeReport() {
		for (final String field : REPORT_FIELDS) {
			report.put(field, "FAIL - Not executed");
		}
	}

	private boolean markDependencyFailure(final String field, final String dependency) {
		markFail(field, "Prerequisite step failed: " + dependency);
		return false;
	}

	private void markPass(final String field) {
		report.put(field, "PASS");
	}

	private void markFail(final String field, final String detail) {
		report.put(field, "FAIL - " + (detail == null ? "Unknown error" : detail));
	}
}
