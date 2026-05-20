package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.Assume;
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
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * Execution is gated by SALEADS_RUN_E2E=true so this test is skipped by
 * default in regular CI.
 * </p>
 */
public class SaleadsMiNegocioFullTest {

	private static final String DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> notes = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue(
				"Set SALEADS_RUN_E2E=true to execute SaleADS UI workflow validation.",
				readBooleanEnv("SALEADS_RUN_E2E", false));

		final String baseUrl = readEnv("SALEADS_LOGIN_URL");
		final String accountEmail = readEnv("SALEADS_GOOGLE_ACCOUNT_EMAIL", DEFAULT_ACCOUNT_EMAIL);
		final String expectedUserName = readEnv("SALEADS_EXPECTED_USER_NAME");
		final long timeoutSeconds = readLongEnv("SALEADS_WAIT_SECONDS", 25L);
		final String browser = readEnv("SALEADS_BROWSER", "chrome");

		evidenceDir = buildEvidenceDir();
		driver = createDriver(browser);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		try {
			driver.manage().window().maximize();

			prepareStartPage(baseUrl);
			final boolean loggedIn = stepLoginWithGoogle(accountEmail);
			report.put("Login", loggedIn);

			final boolean menuValidated = loggedIn && stepOpenMiNegocioMenu();
			report.put("Mi Negocio menu", menuValidated);

			final boolean modalValidated = menuValidated && stepValidateAgregarNegocioModal();
			report.put("Agregar Negocio modal", modalValidated);

			final boolean adminViewValidated = menuValidated && stepOpenAdministrarNegocios();
			report.put("Administrar Negocios view", adminViewValidated);

			final boolean infoGeneralValidated = adminViewValidated
					&& stepValidateInformacionGeneral(accountEmail, expectedUserName);
			report.put("Información General", infoGeneralValidated);

			final boolean detallesCuentaValidated = adminViewValidated && stepValidateDetallesCuenta();
			report.put("Detalles de la Cuenta", detallesCuentaValidated);

			final boolean tusNegociosValidated = adminViewValidated && stepValidateTusNegocios();
			report.put("Tus Negocios", tusNegociosValidated);

			final boolean termsValidated = adminViewValidated
					&& stepValidateLegalLink("Términos y Condiciones", "terms-and-conditions");
			report.put("Términos y Condiciones", termsValidated);

			final boolean privacyValidated = adminViewValidated
					&& stepValidateLegalLink("Política de Privacidad", "privacy-policy");
			report.put("Política de Privacidad", privacyValidated);

			writeFinalReport();
			assertFalse("One or more workflow validations failed. Check report under: " + evidenceDir, report.containsValue(false));
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	private boolean stepLoginWithGoogle(final String accountEmail) {
		try {
			clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
			waitForUiSettled();

			selectGoogleAccountIfPresent(accountEmail);
			waitForUiSettled();

			requireVisible("Negocio", Duration.ofSeconds(60));
			requireSidebarVisible();
			captureScreenshot("01-dashboard-loaded");
			return true;
		} catch (final Exception e) {
			notes.add("Login step failed: " + e.getMessage());
			captureScreenshotSafe("01-login-failure");
			return false;
		}
	}

	private boolean stepOpenMiNegocioMenu() {
		try {
			clickByVisibleText("Negocio");
			waitForUiSettled();
			clickByVisibleText("Mi Negocio");
			waitForUiSettled();

			requireVisible("Agregar Negocio", Duration.ofSeconds(20));
			requireVisible("Administrar Negocios", Duration.ofSeconds(20));
			captureScreenshot("02-mi-negocio-expanded");
			return true;
		} catch (final Exception e) {
			notes.add("Mi Negocio menu step failed: " + e.getMessage());
			captureScreenshotSafe("02-mi-negocio-failure");
			return false;
		}
	}

	private boolean stepValidateAgregarNegocioModal() {
		try {
			clickByVisibleText("Agregar Negocio");
			waitForUiSettled();

			requireVisible("Crear Nuevo Negocio", Duration.ofSeconds(20));
			requireElement(By.xpath("//input[contains(@placeholder,'Nombre del Negocio') or @name='nombre' or @name='businessName']"),
					Duration.ofSeconds(20));
			requireVisible("Tienes 2 de 3 negocios", Duration.ofSeconds(20));
			requireVisible("Cancelar", Duration.ofSeconds(20));
			requireVisible("Crear Negocio", Duration.ofSeconds(20));
			captureScreenshot("03-agregar-negocio-modal");

			// Optional interaction requested by the workflow.
			final WebElement input = findFirstVisibleElement(
					By.xpath("//input[contains(@placeholder,'Nombre del Negocio') or @name='nombre' or @name='businessName']"));
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
			waitForUiSettled();
			clickByVisibleText("Cancelar");
			waitForUiSettled();
			return true;
		} catch (final Exception e) {
			notes.add("Agregar Negocio modal step failed: " + e.getMessage());
			captureScreenshotSafe("03-agregar-negocio-failure");
			return false;
		}
	}

	private boolean stepOpenAdministrarNegocios() {
		try {
			expandMiNegocioIfCollapsed();
			clickByVisibleText("Administrar Negocios");
			waitForUiSettled();

			requireVisible("Información General", Duration.ofSeconds(30));
			requireVisible("Detalles de la Cuenta", Duration.ofSeconds(30));
			requireVisible("Tus Negocios", Duration.ofSeconds(30));
			requireVisible("Sección Legal", Duration.ofSeconds(30));
			captureScreenshot("04-administrar-negocios");
			return true;
		} catch (final Exception e) {
			notes.add("Administrar Negocios view step failed: " + e.getMessage());
			captureScreenshotSafe("04-administrar-negocios-failure");
			return false;
		}
	}

	private boolean stepValidateInformacionGeneral(final String accountEmail, final String expectedUserName) {
		try {
			requireVisible("Información General", Duration.ofSeconds(20));
			requireVisible("BUSINESS PLAN", Duration.ofSeconds(20));
			requireVisible("Cambiar Plan", Duration.ofSeconds(20));

			if (!accountEmail.isBlank()) {
				requireVisible(accountEmail, Duration.ofSeconds(20));
			}

			if (!expectedUserName.isBlank()) {
				requireVisible(expectedUserName, Duration.ofSeconds(20));
			} else {
				requireElement(
						By.xpath("//*[contains(text(),'@')]/preceding::*[normalize-space(text())!=''][1]"),
						Duration.ofSeconds(20));
			}
			return true;
		} catch (final Exception e) {
			notes.add("Información General step failed: " + e.getMessage());
			captureScreenshotSafe("05-informacion-general-failure");
			return false;
		}
	}

	private boolean stepValidateDetallesCuenta() {
		try {
			requireVisible("Cuenta creada", Duration.ofSeconds(20));
			requireVisible("Estado activo", Duration.ofSeconds(20));
			requireVisible("Idioma seleccionado", Duration.ofSeconds(20));
			return true;
		} catch (final Exception e) {
			notes.add("Detalles de la Cuenta step failed: " + e.getMessage());
			captureScreenshotSafe("06-detalles-cuenta-failure");
			return false;
		}
	}

	private boolean stepValidateTusNegocios() {
		try {
			requireVisible("Tus Negocios", Duration.ofSeconds(20));
			requireVisible("Agregar Negocio", Duration.ofSeconds(20));
			requireVisible("Tienes 2 de 3 negocios", Duration.ofSeconds(20));
			requireElement(
					By.xpath("//*[contains(normalize-space(.),'Tus Negocios')]//following::*[self::ul or self::table or self::div][1]"),
					Duration.ofSeconds(20));
			return true;
		} catch (final Exception e) {
			notes.add("Tus Negocios step failed: " + e.getMessage());
			captureScreenshotSafe("07-tus-negocios-failure");
			return false;
		}
	}

	private boolean stepValidateLegalLink(final String linkText, final String screenshotPrefix) {
		String originalWindow = null;
		String currentWindowAfterClick = null;
		boolean openedInNewTab = false;

		try {
			originalWindow = driver.getWindowHandle();
			final String originalUrl = driver.getCurrentUrl();
			final Set<String> beforeHandles = driver.getWindowHandles();

			clickByVisibleText(linkText);
			waitForUiSettled();

			wait.until(d -> d.getWindowHandles().size() > beforeHandles.size()
					|| !d.getCurrentUrl().equals(originalUrl)
					|| isTextVisible(linkText));
			openedInNewTab = driver.getWindowHandles().size() > beforeHandles.size();
			if (openedInNewTab) {
				currentWindowAfterClick = newestWindowHandle(beforeHandles);
				driver.switchTo().window(currentWindowAfterClick);
			}

			requireVisible(linkText, Duration.ofSeconds(30));
			requireElement(By.xpath("//main//*[string-length(normalize-space(.)) > 120] | //article//*[string-length(normalize-space(.)) > 120] | //p[string-length(normalize-space(.)) > 120]"),
					Duration.ofSeconds(30));

			captureScreenshot(screenshotPrefix);
			final String finalUrl = driver.getCurrentUrl();
			notes.add(linkText + " final URL: " + finalUrl);
			return true;
		} catch (final TimeoutException e) {
			notes.add(linkText + " step failed: no navigation or destination page detected.");
			captureScreenshotSafe(screenshotPrefix + "-failure");
			return false;
		} catch (final Exception e) {
			notes.add(linkText + " step failed: " + e.getMessage());
			captureScreenshotSafe(screenshotPrefix + "-failure");
			return false;
		} finally {
			restoreApplicationContext(originalWindow, currentWindowAfterClick, openedInNewTab);
		}
	}

	private void restoreApplicationContext(final String originalWindow, final String currentWindowAfterClick,
			final boolean openedInNewTab) {
		try {
			if (openedInNewTab && currentWindowAfterClick != null && !currentWindowAfterClick.equals(originalWindow)) {
				driver.close();
				driver.switchTo().window(originalWindow);
				waitForUiSettled();
				requireVisible("Información General", Duration.ofSeconds(30));
				return;
			}

			if (originalWindow != null) {
				driver.switchTo().window(originalWindow);
			}
			if (!isTextVisible("Información General")) {
				driver.navigate().back();
				waitForUiSettled();
				requireVisible("Información General", Duration.ofSeconds(30));
			}
		} catch (final Exception e) {
			notes.add("Cleanup warning after legal navigation: " + e.getMessage());
		}
	}

	private void prepareStartPage(final String baseUrl) {
		if (!baseUrl.isBlank()) {
			driver.get(baseUrl);
			waitForUiSettled();
			return;
		}

		notes.add("SALEADS_LOGIN_URL not provided. Set it to start from the login page in standalone runs.");
	}

	private void expandMiNegocioIfCollapsed() {
		if (isElementVisible(By.xpath("//*[contains(normalize-space(.),'Administrar Negocios')]"))) {
			return;
		}

		clickByVisibleText("Mi Negocio");
		waitForUiSettled();
	}

	private void requireSidebarVisible() {
		requireElement(By.xpath("//aside | //nav"), Duration.ofSeconds(30));
	}

	private void clickByVisibleText(final String... texts) {
		for (final String text : texts) {
			final List<By> candidates = buildTextBasedClickLocators(text);
			for (final By locator : candidates) {
				final List<WebElement> elements = driver.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed() && element.isEnabled()) {
						wait.until(ExpectedConditions.elementToBeClickable(element)).click();
						return;
					}
				}
			}
		}

		throw new NoSuchElementException("No clickable element found for texts: " + String.join(", ", texts));
	}

	private List<By> buildTextBasedClickLocators(final String text) {
		final String literal = xpathLiteral(text);
		final List<By> locators = new ArrayList<>();
		locators.add(By.xpath(
				"//button[contains(normalize-space(.), " + literal + ")] | //a[contains(normalize-space(.), " + literal + ")]"));
		locators.add(By.xpath("//li[contains(normalize-space(.), " + literal + ")]"));
		locators.add(By.xpath("//div[@role='button' and contains(normalize-space(.), " + literal + ")]"));
		locators.add(By.xpath("//span[contains(normalize-space(.), " + literal + ")]/ancestor::*[self::button or self::a][1]"));
		locators.add(By.xpath("//*[contains(normalize-space(.), " + literal + ")]"));
		return locators;
	}

	private void selectGoogleAccountIfPresent(final String accountEmail) {
		try {
			final By emailEntry = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(accountEmail) + ")]");
			wait.withTimeout(Duration.ofSeconds(8)).until(ExpectedConditions.visibilityOfElementLocated(emailEntry));
			clickByVisibleText(accountEmail);
			waitForUiSettled();
		} catch (final TimeoutException ignored) {
			// Account chooser can be skipped when already authenticated.
		} finally {
			wait.withTimeout(Duration.ofSeconds(readLongEnv("SALEADS_WAIT_SECONDS", 25L)));
		}
	}

	private WebElement findFirstVisibleElement(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void requireElement(final By locator, final Duration timeout) {
		new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void requireVisible(final String text, final Duration timeout) {
		final By by = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
		new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private boolean isElementVisible(final By by) {
		final List<WebElement> elements = driver.findElements(by);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean isTextVisible(final String text) {
		return isElementVisible(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]"));
	}

	private String newestWindowHandle(final Set<String> oldHandles) {
		for (final String handle : driver.getWindowHandles()) {
			if (!oldHandles.contains(handle)) {
				return handle;
			}
		}
		throw new IllegalStateException("New window handle was not detected.");
	}

	private void waitForUiSettled() {
		wait.until((ExpectedCondition<Boolean>) d -> {
			final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "interactive".equals(state) || "complete".equals(state);
		});
	}

	private void captureScreenshot(final String baseName) {
		try {
			final Path target = evidenceDir.resolve(baseName + ".png");
			final Path screenshotPath = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(screenshotPath, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException e) {
			notes.add("Screenshot warning (" + baseName + "): " + e.getMessage());
		}
	}

	private void captureScreenshotSafe(final String baseName) {
		try {
			captureScreenshot(baseName);
		} catch (final RuntimeException ignored) {
			// Keep workflow reporting resilient even if screenshot capture fails.
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test\n");
		sb.append("Executed at: ").append(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append('\n');
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append("\n\n");
		sb.append("Final Report:\n");

		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append('\n');
		}

		if (!notes.isEmpty()) {
			sb.append("\nNotes:\n");
			for (final String note : notes) {
				sb.append("- ").append(note).append('\n');
			}
		}

		final Path reportFile = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportFile, sb.toString(), StandardCharsets.UTF_8);
		System.out.println(sb);
	}

	private Path buildEvidenceDir() throws IOException {
		final String configured = readEnv("SALEADS_SCREENSHOT_DIR", "target/saleads-evidence");
		final String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Paths.get(configured, "run-" + timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private WebDriver createDriver(final String browser) {
		final boolean headless = readBooleanEnv("SALEADS_HEADLESS", false);
		final String normalized = browser.toLowerCase(Locale.ROOT).trim();

		if ("firefox".equals(normalized)) {
			final FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			return new FirefoxDriver(options);
		}

		if ("edge".equals(normalized)) {
			final EdgeOptions options = new EdgeOptions();
			if (headless) {
				options.addArguments("--headless=new");
			}
			return new EdgeDriver(options);
		}

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--disable-gpu");
		options.addArguments("--window-size=1920,1080");
		return new ChromeDriver(options);
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
			if (i > 0) {
				sb.append(", ");
			}
			if (c == '\'') {
				sb.append("\"'\"");
			} else if (c == '"') {
				sb.append("'\"'");
			} else {
				sb.append('\'').append(c).append('\'');
			}
		}
		sb.append(')');
		return sb.toString();
	}

	private String readEnv(final String key) {
		final String value = System.getenv(key);
		return value == null ? "" : value.trim();
	}

	private String readEnv(final String key, final String fallback) {
		final String value = readEnv(key);
		return value.isBlank() ? fallback : value;
	}

	private long readLongEnv(final String key, final long fallback) {
		final String value = readEnv(key);
		if (value.isBlank()) {
			return fallback;
		}

		try {
			return Long.parseLong(value);
		} catch (final NumberFormatException e) {
			notes.add("Invalid numeric value for " + key + ": " + value + ". Using fallback: " + fallback);
			return fallback;
		}
	}

	private boolean readBooleanEnv(final String key, final boolean fallback) {
		final String value = readEnv(key);
		if (value.isBlank()) {
			return fallback;
		}
		return Boolean.parseBoolean(value);
	}
}
