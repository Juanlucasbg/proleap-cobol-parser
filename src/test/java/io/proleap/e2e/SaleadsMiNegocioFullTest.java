package io.proleap.e2e;

import static org.junit.Assert.assertFalse;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
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
 * Full workflow E2E for SaleADS "Mi Negocio" module.
 *
 * <p>Runtime configuration:
 * <ul>
 *   <li>SALEADS_LOGIN_URL (required): login page for current environment.</li>
 *   <li>SALEADS_GOOGLE_ACCOUNT (optional): defaults to juanlucasbarbiergarzon@gmail.com.</li>
 *   <li>SALEADS_HEADLESS (optional): true|false, defaults to true.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);
	private static final Pattern EMAIL_PATTERN =
			Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String loginUrl;
	private String googleAccount;
	private String appWindow;

	@Before
	public void setUp() throws IOException {
		loginUrl = requiredEnv("SALEADS_LOGIN_URL");
		googleAccount = optionalEnv("SALEADS_GOOGLE_ACCOUNT", "juanlucasbarbiergarzon@gmail.com");

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(optionalEnv("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--lang=es-ES");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDir = createEvidenceDirectory();
		initializeReport();

		driver.get(loginUrl);
		waitForUiToLoad();
		appWindow = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		try {
			printFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep("Login", this::loginWithGoogleAndValidateDashboard);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegocios);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesDeCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", () -> validateLegalDocument("Términos y Condiciones"));
		runStep("Política de Privacidad", () -> validateLegalDocument("Política de Privacidad"));

		assertTrue("One or more validations failed. Check console report and screenshots.",
				report.values().stream().allMatch(Boolean::booleanValue));
	}

	private void initializeReport() {
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
	}

	private void runStep(final String key, final CheckedRunnable action) {
		try {
			action.run();
			report.put(key, true);
			System.out.println("[PASS] " + key);
		} catch (final Exception ex) {
			report.put(key, false);
			System.err.println("[FAIL] " + key + " -> " + ex.getMessage());
			try {
				takeScreenshot("fail-" + key);
			} catch (final Exception screenshotError) {
				System.err.println("Could not capture failure screenshot for " + key + ": "
						+ screenshotError.getMessage());
			}
		}
	}

	private void loginWithGoogleAndValidateDashboard() throws IOException {
		clickFirstMatchingText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google",
				"Continuar con Google", "Google"));
		handleGoogleAccountSelectorIfPresent();
		switchToApplicationWindow();

		waitForAnyVisible(Arrays.asList(By.xpath("//aside"), By.xpath("//nav")));
		waitForText("Negocio");
		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws IOException {
		clickByText("Negocio");
		clickByText("Mi Negocio");

		waitForText("Agregar Negocio");
		waitForText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByText("Agregar Negocio");

		waitForText("Crear Nuevo Negocio");
		waitForText("Nombre del Negocio");
		waitForText("Tienes 2 de 3 negocios");
		waitForText("Cancelar");
		waitForText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final WebElement nameField = waitForAnyVisible(Arrays.asList(
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]")));
		nameField.click();
		nameField.clear();
		nameField.sendKeys("Negocio Prueba Automatización");
		waitForUiToLoad();

		clickByText("Cancelar");
	}

	private void openAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			clickByText("Mi Negocio");
		}

		clickByText("Administrar Negocios");

		waitForText("Información General");
		waitForText("Detalles de la Cuenta");
		waitForText("Tus Negocios");
		waitForText("Sección Legal");
		takeScreenshot("04-administrar-negocios-view");
	}

	private void validateInformacionGeneral() {
		waitForText("BUSINESS PLAN");
		waitForText("Cambiar Plan");

		final String bodyText = normalizedBodyText();
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(bodyText);
		assertTrue("Expected a visible user email.", emailMatcher.find());

		final String visibleNameToken = googleAccount.split("@")[0];
		assertTrue("Expected user name token to be visible in Información General.",
				bodyText.toLowerCase().contains(visibleNameToken.substring(0, Math.min(4, visibleNameToken.length()))
						.toLowerCase()));
	}

	private void validateDetallesDeCuenta() {
		waitForText("Cuenta creada");
		waitForText("Estado activo");
		waitForText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		waitForText("Tus Negocios");
		waitForText("Agregar Negocio");
		waitForText("Tienes 2 de 3 negocios");

		final WebElement section = waitForAnyVisible(Arrays.asList(
				By.xpath("//*[contains(normalize-space(.),'Tus Negocios')]/ancestor::section[1]"),
				By.xpath("//*[contains(normalize-space(.),'Tus Negocios')]/ancestor::div[1]")));

		final List<WebElement> listItems = new ArrayList<>();
		listItems.addAll(section.findElements(By.xpath(".//li[normalize-space(.)!='']")));
		listItems.addAll(section.findElements(By.xpath(".//tr[normalize-space(.)!='']")));
		listItems.addAll(section.findElements(By.xpath(".//div[contains(@class,'business')]")));
		assertFalse("Expected a business list in 'Tus Negocios' section.", listItems.isEmpty());
	}

	private void validateLegalDocument(final String documentName) throws IOException {
		switchToApplicationWindow();
		final Set<String> before = driver.getWindowHandles();
		clickByText(documentName);

		String targetWindow = null;
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(d -> d.getWindowHandles().size() > before.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!before.contains(handle)) {
					targetWindow = handle;
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			// The link likely opened in the same tab.
		}

		if (targetWindow != null) {
			driver.switchTo().window(targetWindow);
		}

		waitForUiToLoad();
		waitForText(documentName);

		final String legalContent = normalizedBodyText();
		assertTrue("Expected visible legal content for " + documentName + ".",
				legalContent.length() > 200 || legalContent.toLowerCase().contains("condiciones")
						|| legalContent.toLowerCase().contains("privacidad"));

		takeScreenshot("legal-" + documentName);
		legalUrls.put(documentName, driver.getCurrentUrl());

		if (targetWindow != null) {
			driver.close();
			switchToApplicationWindow();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void handleGoogleAccountSelectorIfPresent() {
		final Set<String> startingHandles = new LinkedHashSet<>(driver.getWindowHandles());

		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(
					d -> d.getWindowHandles().size() > startingHandles.size() || d.getCurrentUrl().contains("accounts.google"));
		} catch (final TimeoutException ignored) {
			// Google account selector did not appear (already logged in).
			return;
		}

		String googleWindow = driver.getWindowHandle();
		for (final String handle : driver.getWindowHandles()) {
			if (!startingHandles.contains(handle)) {
				googleWindow = handle;
				break;
			}
		}

		driver.switchTo().window(googleWindow);
		if (driver.getCurrentUrl().contains("accounts.google")) {
			clickFirstMatchingText(Arrays.asList(googleAccount, "Usar otra cuenta", "Choose an account"));
			waitForUiToLoad();
		}
	}

	private void switchToApplicationWindow() {
		if (!driver.getWindowHandles().contains(appWindow)) {
			appWindow = driver.getWindowHandle();
		}
		driver.switchTo().window(appWindow);
		waitForUiToLoad();
	}

	private void clickFirstMatchingText(final List<String> options) {
		Exception lastError = null;
		for (final String option : options) {
			try {
				clickByText(option);
				return;
			} catch (final Exception ex) {
				lastError = ex;
			}
		}
		throw new IllegalStateException("Could not click any expected text: " + options, lastError);
	}

	private void clickByText(final String text) {
		final String literal = xpathLiteral(text);
		final List<By> locators = Arrays.asList(
				By.xpath("//button[contains(normalize-space(.)," + literal + ")]"),
				By.xpath("//a[contains(normalize-space(.)," + literal + ")]"),
				By.xpath("//*[@role='button' and contains(normalize-space(.)," + literal + ")]"),
				By.xpath("//*[contains(normalize-space(.)," + literal + ")]/ancestor::button[1]"),
				By.xpath("//*[contains(normalize-space(.)," + literal + ")]/ancestor::a[1]"),
				By.xpath("//*[contains(normalize-space(.)," + literal + ")]"));

		Exception lastError = null;
		for (final By locator : locators) {
			try {
				final WebElement element =
						new WebDriverWait(driver, SHORT_TIMEOUT).until(ExpectedConditions.elementToBeClickable(locator));
				try {
					element.click();
				} catch (final Exception clickError) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				}
				waitForUiToLoad();
				return;
			} catch (final Exception ex) {
				lastError = ex;
			}
		}

		throw new IllegalStateException("Unable to click text: " + text, lastError);
	}

	private WebElement waitForAnyVisible(final List<By> candidates) {
		for (final By candidate : candidates) {
			try {
				return new WebDriverWait(driver, SHORT_TIMEOUT)
						.until(ExpectedConditions.visibilityOfElementLocated(candidate));
			} catch (final TimeoutException ignored) {
				// Try next candidate.
			}
		}
		throw new NoSuchElementException("None of expected elements became visible: " + candidates);
	}

	private void waitForText(final String text) {
		final String literal = xpathLiteral(text);
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(.)," + literal + ")]")));
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		final String literal = xpathLiteral(text);
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[contains(normalize-space(.)," + literal + ")]")));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		final ExpectedCondition<Boolean> pageLoaded = d ->
				((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete");
		try {
			wait.until(pageLoaded);
		} catch (final Exception ignored) {
			// Some SPAs keep pending requests; proceed with fallback delay.
		}

		try {
			Thread.sleep(700);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String normalizedBodyText() {
		final String body = driver.findElement(By.tagName("body")).getText();
		return body == null ? "" : body.trim().replaceAll("\\s+", " ");
	}

	private void takeScreenshot(final String checkpoint) throws IOException {
		final String safeName = checkpoint.toLowerCase().replaceAll("[^a-z0-9-]+", "-");
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDir.resolve(safeName + ".png");
		Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Screenshot captured: " + destination.toAbsolutePath());
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(path);
		return path;
	}

	private String requiredEnv(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalStateException("Missing required environment variable: " + key);
		}
		return value.trim();
	}

	private String optionalEnv(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		return value.trim();
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'");
		final StringBuilder xpath = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				xpath.append(", \"'\", ");
			}
			xpath.append("'").append(parts[i]).append("'");
		}
		xpath.append(")");
		return xpath.toString();
	}

	private void printFinalReport() {
		System.out.println("\n===== saleads_mi_negocio_full_test report =====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}

		if (!legalUrls.isEmpty()) {
			System.out.println("----- Legal URLs -----");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(entry.getKey() + " URL: " + entry.getValue());
			}
		}

		if (evidenceDir != null) {
			System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		}
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
