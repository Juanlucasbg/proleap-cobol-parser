package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
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

/**
 * Opt-in E2E workflow for SaleADS Mi Negocio module.
 *
 * <p>
 * This test is disabled by default and only runs with:
 * <code>-Dsaleads.e2e.enabled=true</code>
 * </p>
 */
public class SaleadsMiNegocioWorkflowE2ETest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEXT_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String TEXT_POLITICA = "Pol\u00edtica de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	private final Map<String, Boolean> statusBySection = new LinkedHashMap<>();
	private final Map<String, String> detailBySection = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue("Set -Dsaleads.e2e.enabled=true to run this E2E test.", enabled);

		final String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		evidenceDir = Path.of("target", "saleads-e2e-evidence", timestamp);
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(System.getProperty("saleads.e2e.headless", "false"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		final String userDataDir = firstNonBlank(System.getProperty("saleads.e2e.chromeUserDataDir"),
				System.getenv("SALEADS_E2E_CHROME_USER_DATA_DIR"));
		if (userDataDir != null) {
			options.addArguments("--user-data-dir=" + userDataDir);
		}

		driver = new ChromeDriver(options);
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);

		final long waitSeconds = Long.parseLong(System.getProperty("saleads.e2e.waitSeconds", "25"));
		wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));

		final String loginUrl = firstNonBlank(System.getProperty("saleads.e2e.url"), System.getenv("SALEADS_E2E_URL"));
		if (loginUrl != null) {
			driver.get(loginUrl);
			waitForUiToLoad();
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
		// Step 1
		statusBySection.put("Login", runStep("Login", this::validateLoginWithGoogle));
		// Step 2
		statusBySection.put("Mi Negocio menu", runStep("Mi Negocio menu", this::validateMiNegocioMenu));
		// Step 3
		statusBySection.put("Agregar Negocio modal", runStep("Agregar Negocio modal", this::validateAgregarNegocioModal));
		// Step 4
		statusBySection.put("Administrar Negocios view",
				runStep("Administrar Negocios view", this::validateAdministrarNegociosView));
		// Step 5
		statusBySection.put("Informaci\u00f3n General",
				runStep("Informaci\u00f3n General", this::validateInformacionGeneral));
		// Step 6
		statusBySection.put("Detalles de la Cuenta",
				runStep("Detalles de la Cuenta", this::validateDetallesCuenta));
		// Step 7
		statusBySection.put("Tus Negocios", runStep("Tus Negocios", this::validateTusNegocios));
		// Step 8
		statusBySection.put(TEXT_TERMINOS, runStep(TEXT_TERMINOS, this::validateTerminosYCondiciones));
		// Step 9
		statusBySection.put(TEXT_POLITICA, runStep(TEXT_POLITICA, this::validatePoliticaPrivacidad));

		final Path reportPath = writeFinalReport();
		final boolean allPassed = statusBySection.values().stream().allMatch(Boolean::booleanValue);
		assertTrue("Some SaleADS Mi Negocio validations failed. See report at: " + reportPath, allPassed);
	}

	private boolean runStep(final String section, final Step step) {
		try {
			step.execute();
			detailBySection.put(section, "PASS");
			return true;
		} catch (final Throwable ex) {
			detailBySection.put(section, "FAIL: " + ex.getMessage());
			try {
				takeScreenshot("fail-" + sanitize(section));
			} catch (final Exception ignored) {
				// Best effort evidence capture.
			}
			return false;
		}
	}

	private void validateLoginWithGoogle() throws IOException {
		clickByVisibleText("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google", "Google");
		waitForUiToLoad();

		// Account chooser is conditional and only clicked when visible.
		if (isVisible(By.xpath("//*[normalize-space()=" + xpathLiteral(GOOGLE_ACCOUNT_EMAIL) + "]"), 8)) {
			click(By.xpath("//*[normalize-space()=" + xpathLiteral(GOOGLE_ACCOUNT_EMAIL) + "]"));
			waitForUiToLoad();
		}

		waitForVisible(By.xpath("//aside | //nav"), "Main navigation/sidebar should be visible after login.");
		waitForVisible(By.xpath("//*[contains(normalize-space(), 'Negocio')]"),
				"Sidebar should expose Negocio navigation.");

		takeScreenshot("01-dashboard-loaded");
	}

	private void validateMiNegocioMenu() throws IOException {
		clickIfVisible(By.xpath("//*[self::button or self::a][contains(normalize-space(), 'Negocio')]"));
		waitForUiToLoad();

		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");

		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForVisibleText("Crear Nuevo Negocio");
		waitForVisibleText("Nombre del Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal");

		final WebElement nombreField = findFirstVisible(Arrays.asList(
				By.xpath("//input[@placeholder=" + xpathLiteral("Nombre del Negocio") + "]"),
				By.xpath("//label[contains(normalize-space(), " + xpathLiteral("Nombre del Negocio")
						+ ")]/following::input[1]"),
				By.xpath("//input")));
		nombreField.click();
		nombreField.sendKeys(Keys.chord(Keys.CONTROL, "a"));
		nombreField.sendKeys("Negocio Prueba Automatizacion");

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral("Crear Nuevo Negocio") + ")]")));
		waitForUiToLoad();
	}

	private void validateAdministrarNegociosView() throws IOException {
		if (!isVisible(By.xpath("//*[contains(normalize-space(), " + xpathLiteral("Administrar Negocios") + ")]"), 2)) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		waitForVisibleText("Informaci\u00f3n General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Secci\u00f3n Legal");

		takeScreenshot("04-administrar-negocios-view");
	}

	private void validateInformacionGeneral() {
		waitForVisibleText("Informaci\u00f3n General");
		waitForVisible(By.xpath("//*[contains(normalize-space(), '@')]"),
				"An email should be visible in Informacion General.");
		waitForVisibleText("BUSINESS PLAN");
		waitForVisibleText("Cambiar Plan");
	}

	private void validateDetallesCuenta() {
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
	}

	private void validateTerminosYCondiciones() throws IOException {
		legalUrls.put(TEXT_TERMINOS, openAndValidateLegalLink(TEXT_TERMINOS, "08-terminos"));
	}

	private void validatePoliticaPrivacidad() throws IOException {
		legalUrls.put(TEXT_POLITICA, openAndValidateLegalLink(TEXT_POLITICA, "09-politica"));
	}

	private String openAndValidateLegalLink(final String linkText, final String screenshotName) throws IOException {
		final String appHandle = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String previousUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		String targetHandle = appHandle;
		try {
			wait.until(d -> d.getWindowHandles().size() > beforeHandles.size());
			targetHandle = driver.getWindowHandles().stream().filter(h -> !beforeHandles.contains(h)).findFirst()
					.orElse(appHandle);
		} catch (final TimeoutException ignored) {
			// Link likely navigated in same tab.
		}

		if (!Objects.equals(targetHandle, appHandle)) {
			driver.switchTo().window(targetHandle);
			waitForUiToLoad();
		}

		waitForVisibleText(linkText);
		waitForVisible(By.xpath("//p[string-length(normalize-space()) > 30]"),
				"Expected legal content paragraph to be visible.");

		takeScreenshot(screenshotName + "-legal-page");
		final String finalUrl = driver.getCurrentUrl();

		if (!Objects.equals(targetHandle, appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else if (!Objects.equals(previousUrl, finalUrl)) {
			driver.navigate().back();
		}
		waitForUiToLoad();

		return finalUrl;
	}

	private void waitForVisibleText(final String text) {
		waitForVisible(
				By.xpath("//*[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), "
						+ xpathLiteral(text) + ")]"),
				"Expected visible text: " + text);
	}

	private void clickByVisibleText(final String... texts) {
		final List<By> locators = new ArrayList<>();
		for (final String text : texts) {
			final String literal = xpathLiteral(text);
			locators.add(By.xpath("//button[normalize-space()=" + literal + "]"));
			locators.add(By.xpath("//a[normalize-space()=" + literal + "]"));
			locators.add(By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(), " + literal
					+ ")]"));
			locators.add(By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
		}

		final WebElement element = findFirstVisible(locators);
		element.click();
		waitForUiToLoad();
	}

	private void clickIfVisible(final By locator) {
		if (isVisible(locator, 3)) {
			click(locator);
		}
	}

	private void click(final By locator) {
		wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
		waitForUiToLoad();
	}

	private WebElement findFirstVisible(final List<By> locators) {
		Exception lastError = null;
		for (final By locator : locators) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final Exception ex) {
				lastError = ex;
			}
		}

		throw new NoSuchElementException("Could not find visible element by provided locators: " + locators
				+ (lastError != null ? ". Last error: " + lastError.getMessage() : ""));
	}

	private WebElement waitForVisible(final By locator, final String failureMessage) {
		try {
			return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final Exception ex) {
			throw new AssertionError(failureMessage, ex);
		}
	}

	private boolean isVisible(final By locator, final long timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		try {
			wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Some app transitions are SPA-driven and may not update readyState.
		}

		try {
			Thread.sleep(750);
		} catch (final InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final String fileName = new SimpleDateFormat("HHmmss-SSS").format(new Date()) + "-" + checkpointName + ".png";
		final Path target = evidenceDir.resolve(fileName);
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(target, screenshot);
	}

	private Path writeFinalReport() throws IOException {
		final Path reportPath = evidenceDir.resolve("final-report.txt");
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Full Workflow Report\n");
		sb.append("======================================\n\n");
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		sb.append("Current application URL: ").append(driver.getCurrentUrl()).append("\n\n");

		sb.append("Step Statuses\n");
		sb.append("-------------\n");
		for (final Map.Entry<String, Boolean> entry : statusBySection.entrySet()) {
			sb.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL");
			final String detail = detailBySection.get(entry.getKey());
			if (detail != null && detail.startsWith("FAIL")) {
				sb.append(" (").append(detail).append(')');
			}
			sb.append('\n');
		}

		if (!legalUrls.isEmpty()) {
			sb.append("\nCaptured Legal URLs\n");
			sb.append("-------------------\n");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		final List<String> failures = statusBySection.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		sb.append("\nFinal Result: ").append(failures.isEmpty() ? "PASS" : "FAIL").append('\n');
		if (!failures.isEmpty()) {
			sb.append("Failed sections: ").append(String.join(", ", failures)).append('\n');
		}

		Files.writeString(reportPath, sb.toString());
		return reportPath;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char c = chars[i];
			if (c == '\'') {
				sb.append("\"'\"");
			} else if (c == '"') {
				sb.append("'\"'");
			} else {
				sb.append("'").append(c).append("'");
			}
			if (i < chars.length - 1) {
				sb.append(", ");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private static String sanitize(final String value) {
		return value.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	@FunctionalInterface
	private interface Step {
		void execute() throws Exception;
	}
}
