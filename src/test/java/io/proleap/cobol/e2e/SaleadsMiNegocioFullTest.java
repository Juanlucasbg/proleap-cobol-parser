package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioFullTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private final Map<String, StepResult> results = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String applicationWindowHandle;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = environment("SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"SALEADS_LOGIN_URL must be provided so the test can open the environment login page without hardcoding a domain.",
				loginUrl != null && !loginUrl.isBlank());

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		final String headless = environment("SALEADS_HEADLESS");
		if (headless == null || Boolean.parseBoolean(headless)) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		final String chromeUserDataDir = environment("SALEADS_CHROME_USER_DATA_DIR");
		if (chromeUserDataDir != null && !chromeUserDataDir.isBlank()) {
			options.addArguments("--user-data-dir=" + chromeUserDataDir);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(20));
		evidenceDir = Path.of("target", "saleads-evidence");
		Files.createDirectories(evidenceDir);

		driver.get(loginUrl);
		waitForUiToSettle();
	}

	@After
	public void tearDown() {
		writeFinalReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		executeStep(REPORT_LOGIN, this::validateLoginWithGoogle);
		executeStep(REPORT_MI_NEGOCIO_MENU, this::validateMiNegocioMenuExpansion);
		executeStep(REPORT_AGREGAR_MODAL, this::validateAgregarNegocioModal);
		executeStep(REPORT_ADMIN_VIEW, this::validateAdministrarNegociosView);
		executeStep(REPORT_INFO_GENERAL, this::validateInformacionGeneral);
		executeStep(REPORT_DETALLES_CUENTA, this::validateDetallesCuenta);
		executeStep(REPORT_TUS_NEGOCIOS, this::validateTusNegocios);
		executeStep(REPORT_TERMINOS, () -> validateLegalPage("Términos y Condiciones", "Términos y Condiciones",
				"08-terminos-y-condiciones.png"));
		executeStep(REPORT_PRIVACIDAD, () -> validateLegalPage("Política de Privacidad", "Política de Privacidad",
				"09-politica-de-privacidad.png"));

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			if (!entry.getValue().pass) {
				failedSteps.add(entry.getKey() + ": " + entry.getValue().details);
			}
		}

		assertTrue("One or more workflow validations failed:\n" + String.join("\n", failedSteps), failedSteps.isEmpty());
	}

	private void validateLoginWithGoogle() throws IOException {
		if (!isSidebarVisible()) {
			clickFirstVisibleText(List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
					"Login with Google", "Google"));
			waitForUiToSettle();

			if (isTextVisible(ACCOUNT_EMAIL, 8)) {
				clickByText(ACCOUNT_EMAIL);
				waitForUiToSettle();
			}
		}

		assertTrue("Main app interface was not detected after Google login.", isSidebarVisible());
		assertTrue("Left sidebar navigation is not visible.", isSidebarVisible());
		applicationWindowHandle = driver.getWindowHandle();
		takeScreenshot("01-dashboard-loaded.png");
	}

	private void validateMiNegocioMenuExpansion() throws IOException {
		waitForSidebarNavigation();

		if (!isTextVisible("Mi Negocio", 5)) {
			clickByText("Negocio");
		}

		clickByText("Mi Negocio");
		waitForUiToSettle();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByText("Agregar Negocio");
		waitForUiToSettle();

		assertTextVisible("Crear Nuevo Negocio");
		assertVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio')] | //label[contains(normalize-space(),'Nombre del Negocio')]"));
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal.png");

		if (isVisible(By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"), 5)) {
			final WebElement nameInput = waitForVisible(By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"), 10);
			nameInput.click();
			nameInput.clear();
			nameInput.sendKeys("Negocio Prueba Automatizacion");
			waitForUiToSettle();
		}

		clickByText("Cancelar");
		waitForUiToSettle();
	}

	private void validateAdministrarNegociosView() throws IOException {
		if (!isTextVisible("Administrar Negocios", 5)) {
			clickByText("Mi Negocio");
		}

		clickByText("Administrar Negocios");
		waitForUiToSettle();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		takeScreenshot("04-administrar-negocios-view.png");
	}

	private void validateInformacionGeneral() {
		assertTextVisible("Información General");
		assertTextVisible(ACCOUNT_EMAIL);
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");

		final WebElement section = waitForSection("Información General");
		final String sectionText = section.getText();
		assertTrue("Could not detect a likely user name in Informacion General section.",
				containsLikelyUserName(sectionText));
	}

	private void validateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void validateLegalPage(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String appHandle = applicationWindowHandle != null ? applicationWindowHandle : driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByText(linkText);
		waitForUiToSettle();

		String newHandle = null;
		for (int i = 0; i < 20; i++) {
			final Set<String> currentHandles = driver.getWindowHandles();
			if (currentHandles.size() > handlesBeforeClick.size()) {
				for (final String handle : currentHandles) {
					if (!handlesBeforeClick.contains(handle)) {
						newHandle = handle;
						break;
					}
				}
				break;
			}
			sleep(250);
		}

		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiToSettle();
		}

		assertTextVisible(headingText);
		final String pageText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		assertTrue("Legal content text appears to be empty for " + linkText, pageText.length() > 150);

		takeScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();
		final StepResult stepResult = Objects.requireNonNull(results.get(resolveReportField(linkText)));
		stepResult.details = "validated | final_url=" + finalUrl;

		if (newHandle != null) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToSettle();
		} else if (!Objects.equals(driver.getWindowHandle(), appHandle)) {
			driver.switchTo().window(appHandle);
			waitForUiToSettle();
		} else {
			driver.navigate().back();
			waitForUiToSettle();
		}
	}

	private String resolveReportField(final String linkText) {
		if ("Términos y Condiciones".equals(linkText)) {
			return REPORT_TERMINOS;
		}
		return REPORT_PRIVACIDAD;
	}

	private void executeStep(final String reportField, final StepAction action) {
		final StepResult result = new StepResult();
		results.put(reportField, result);

		try {
			action.run();
			result.pass = true;
			if (result.details == null || result.details.isBlank()) {
				result.details = "validated";
			}
		} catch (final Throwable throwable) {
			result.pass = false;
			result.details = normalizeWhitespace(throwable.getMessage());
			try {
				takeScreenshot("failure-" + sanitizeForFileName(reportField) + ".png");
			} catch (final Exception ignored) {
				// Ignore screenshot errors during failure handling.
			}
		}
	}

	private void clickFirstVisibleText(final List<String> labels) {
		for (final String label : labels) {
			if (isTextVisible(label, 3)) {
				clickByText(label);
				return;
			}
		}

		throw new IllegalStateException("None of the expected labels were found: " + labels);
	}

	private void clickByText(final String text) {
		final By primaryLocator = By.xpath(
				"//*[normalize-space()=" + xpathString(text)
						+ " and (self::button or self::a or self::span or self::div or self::li or self::p or @role='button')]"
						+ " | //*[(self::button or self::a or @role='button') and contains(normalize-space(),"
						+ xpathString(text) + ")]");
		final By fallbackLocator = By.xpath("//*[normalize-space()=" + xpathString(text)
				+ " or contains(normalize-space()," + xpathString(text) + ")]");

		WebElement textElement;
		try {
			textElement = waitForVisible(primaryLocator, 10);
		} catch (final TimeoutException timeoutException) {
			textElement = waitForVisible(fallbackLocator, 20);
		}

		WebElement clickable = textElement;
		try {
			clickable = textElement
					.findElement(By.xpath("./ancestor-or-self::*[self::button or self::a or @role='button'][1]"));
		} catch (final Exception ignored) {
			// Fall back to clicking the text element itself when no explicit clickable ancestor exists.
		}

		wait.until(ExpectedConditions.elementToBeClickable(clickable)).click();
		waitForUiToSettle();
	}

	private void assertTextVisible(final String text) {
		final By locator = By.xpath("//*[normalize-space()=" + xpathString(text) + " or contains(normalize-space(),"
				+ xpathString(text) + ")]");
		assertVisible(locator);
	}

	private WebElement waitForSection(final String sectionTitle) {
		final By locator = By.xpath(
				"//*[self::section or self::div][.//*[normalize-space()=" + xpathString(sectionTitle) + "]]");
		return waitForVisible(locator, 20);
	}

	private boolean containsLikelyUserName(final String text) {
		final String[] lines = text.split("\n");
		for (String line : lines) {
			line = normalizeWhitespace(line);
			if (line.isBlank()) {
				continue;
			}
			final String normalized = normalizeForComparison(line);
			if (normalized.contains("@") || normalized.contains("informacion general")
					|| normalized.contains("business plan") || normalized.contains("cambiar plan")
					|| normalized.contains("cuenta creada") || normalized.contains("estado activo")
					|| normalized.contains("idioma seleccionado") || normalized.contains("agregar negocio")
					|| normalized.contains("tienes 2 de 3 negocios")) {
				continue;
			}
			if (line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*") && line.length() >= 3) {
				return true;
			}
		}
		return false;
	}

	private void assertVisible(final By by) {
		waitForVisible(by, 20);
	}

	private boolean isVisible(final By by, final int seconds) {
		try {
			waitForVisible(by, seconds);
			return true;
		} catch (final Exception exception) {
			return false;
		}
	}

	private WebElement waitForVisible(final By by, final int seconds) {
		return new WebDriverWait(driver, Duration.ofSeconds(seconds)).until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private boolean isTextVisible(final String text, final int seconds) {
		final By locator = By.xpath("//*[normalize-space()=" + xpathString(text) + " or contains(normalize-space(),"
				+ xpathString(text) + ")]");
		return isVisible(locator, seconds);
	}

	private boolean isSidebarVisible() {
		return isVisible(By.xpath("//aside | //nav"), 10) && isTextVisible("Negocio", 10);
	}

	private void waitForSidebarNavigation() {
		waitForVisible(By.xpath("//aside | //nav"), 20);
		waitForUiToSettle();
	}

	private void waitForUiToSettle() {
		new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> "complete"
				.equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		sleep(500);
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final File rawScreenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path targetPath = evidenceDir.resolve(fileName);
		Files.copy(rawScreenshot.toPath(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"test_name\": \"saleads_mi_negocio_full_test\",\n");
		builder.append("  \"generated_at\": \"").append(OffsetDateTime.now()).append("\",\n");
		builder.append("  \"results\": {\n");

		int index = 0;
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(entry.getValue().pass ? "PASS" : "FAIL").append("\"");
			if (index < results.size() - 1) {
				builder.append(",");
			}
			builder.append("\n");
			index++;
		}

		builder.append("  },\n");
		builder.append("  \"details\": {\n");

		index = 0;
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(escapeJson(entry.getValue().details)).append("\"");
			if (index < results.size() - 1) {
				builder.append(",");
			}
			builder.append("\n");
			index++;
		}

		builder.append("  },\n");
		builder.append("  \"evidence_directory\": \"").append(escapeJson(evidenceDir.toString())).append("\"\n");
		builder.append("}\n");

		try {
			Files.writeString(evidenceDir.resolve("final-report.json"), builder.toString(), StandardCharsets.UTF_8);
		} catch (final IOException exception) {
			throw new RuntimeException("Unable to write final report JSON.", exception);
		}
	}

	private String sanitizeForFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
	}

	private String normalizeWhitespace(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
	}

	private String environment(final String key) {
		return System.getenv(key);
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for UI to settle.", interruptedException);
		}
	}

	private String xpathString(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String escapeJson(final String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String normalizeForComparison(final String value) {
		final String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
		return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private boolean pass;
		private String details;
	}
}
