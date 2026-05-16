package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.openqa.selenium.WindowType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for the "Mi Negocio" workflow.
 *
 * <p>
 * This test is environment-agnostic and does not hardcode any SaleADS domain.
 * Provide the login page URL with SALEADS_START_URL or -Dsaleads.start.url.
 * </p>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Path SCREENSHOT_DIR = Path.of("target", "screenshots", "saleads-mi-negocio");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> reportDetails = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private String originalApplicationWindow;

	@Before
	public void setup() throws IOException {
		Files.createDirectories(SCREENSHOT_DIR);

		final ChromeOptions options = new ChromeOptions();
		if (readBoolean("saleads.headless", "SALEADS_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(readLong("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", 30)));

		final String startUrl = readConfig("saleads.start.url", "SALEADS_START_URL", "");
		assertTrue("Missing start URL. Set SALEADS_START_URL or -Dsaleads.start.url.", !startUrl.isBlank());
		driver.get(startUrl);
		waitForUiLoad();
		originalApplicationWindow = driver.getWindowHandle();
	}

	@After
	public void tearDown() throws IOException {
		writeReportFile();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runSection("Login", this::loginWithGoogleAndValidateSidebar);
		runSection("Mi Negocio menu", this::openMiNegocioMenu);
		runSection("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runSection("Administrar Negocios view", this::openAdministrarNegociosAndValidateSections);
		runSection("Información General", this::validateInformacionGeneral);
		runSection("Detalles de la Cuenta", this::validateDetallesCuenta);
		runSection("Tus Negocios", this::validateTusNegocios);
		runSection("Términos y Condiciones", () -> validateLegalPage("Términos y Condiciones", "Términos y Condiciones", "terminos-condiciones"));
		runSection("Política de Privacidad", () -> validateLegalPage("Política de Privacidad", "Política de Privacidad", "politica-privacidad"));

		final List<String> failed = report.entrySet().stream()
				.filter(entry -> !Boolean.TRUE.equals(entry.getValue()))
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

		assertTrue("FAIL sections: " + failed + "\nDetails: " + reportDetails, failed.isEmpty());
	}

	private void loginWithGoogleAndValidateSidebar() {
		clickFirstVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiLoad();
		selectGoogleAccountIfVisible();

		waitForAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Inicio");
		assertVisible(By.xpath("//aside | //nav | //*[contains(@class,'sidebar')]"), "Sidebar navigation was not visible");
		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() {
		clickFirstVisibleText("Negocio", "Mi Negocio");

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() {
		clickFirstVisibleText("Agregar Negocio");

		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		typeIfVisible(By.xpath("//input[@placeholder='Nombre del Negocio' or @name='nombreNegocio' or @id='nombreNegocio']"),
				"Negocio Prueba Automatización");
		clickFirstVisibleText("Cancelar");
	}

	private void openAdministrarNegociosAndValidateSections() {
		ensureMiNegocioMenuExpanded();
		clickFirstVisibleText("Administrar Negocios");

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		takeScreenshot("04-administrar-negocios-page");
	}

	private void validateInformacionGeneral() {
		assertVisible(By.xpath("//*[contains(@class,'user') or contains(@class,'profile') or contains(@class,'name')]"),
				"User name block is not visible");
		assertVisible(By.xpath("//*[contains(text(),'@')]"), "User email is not visible");
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
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

	private void validateLegalPage(final String linkText, final String heading, final String screenshotBaseName) {
		ensureApplicationWindow();
		final String currentUrl = driver.getCurrentUrl();
		final Set<String> beforeWindows = driver.getWindowHandles();

		clickFirstVisibleText(linkText);

		final String legalWindow = waitForNewWindowIfAny(beforeWindows);
		boolean openedInNewWindow = false;
		if (legalWindow != null) {
			driver.switchTo().window(legalWindow);
			openedInNewWindow = true;
		} else if (driver.getCurrentUrl().equals(currentUrl)) {
			throw new AssertionError("Legal navigation for '" + linkText + "' did not change page or open a tab");
		}

		waitForUiLoad();
		assertTextVisible(heading);
		assertVisible(By.xpath("//p[string-length(normalize-space()) > 30] | //article | //main"),
				"Legal content was not visible");
		takeScreenshot("05-" + screenshotBaseName);
		reportDetails.put(linkText + " URL", driver.getCurrentUrl());

		if (openedInNewWindow) {
			driver.close();
			driver.switchTo().window(originalApplicationWindow);
		} else {
			driver.navigate().back();
		}
		waitForUiLoad();
	}

	private void ensureMiNegocioMenuExpanded() {
		if (!isTextVisibleQuick("Administrar Negocios")) {
			clickFirstVisibleText("Mi Negocio", "Negocio");
		}
	}

	private void selectGoogleAccountIfVisible() {
		final String googleEmail = readConfig("saleads.google.email", "SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL);
		final long timeoutMillis = Duration.ofSeconds(30).toMillis();
		final long started = System.currentTimeMillis();

		while (System.currentTimeMillis() - started < timeoutMillis) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				final List<WebElement> candidates = driver.findElements(By.xpath("//*[contains(normalize-space(),"
						+ asXPathLiteral(googleEmail) + ")]"));
				for (final WebElement candidate : candidates) {
					if (candidate.isDisplayed()) {
						candidate.click();
						waitForUiLoad();
						driver.switchTo().window(originalApplicationWindow);
						return;
					}
				}
			}

			if (isTextVisibleQuick("Negocio") || isTextVisibleQuick("Mi Negocio")) {
				driver.switchTo().window(originalApplicationWindow);
				return;
			}

			sleep(400);
		}
	}

	private void runSection(final String section, final Step step) {
		try {
			step.execute();
			report.put(section, true);
		} catch (final Throwable throwable) {
			report.put(section, false);
			reportDetails.put(section, throwable.getMessage());
			takeScreenshot("failure-" + sanitize(section));
		}
	}

	private void waitForAnyVisibleText(final String... texts) {
		wait.until(driverState -> {
			for (final String text : texts) {
				if (isTextVisibleQuick(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private void clickFirstVisibleText(final String... texts) {
		Throwable lastError = null;
		for (final String text : texts) {
			try {
				final WebElement element = findVisibleTextElement(text);
				scrollIntoView(element);
				wait.until(ExpectedConditions.elementToBeClickable(element)).click();
				waitForUiLoad();
				return;
			} catch (final Throwable throwable) {
				lastError = throwable;
			}
		}

		throw new AssertionError("Could not click any of texts: " + String.join(", ", texts), lastError);
	}

	private WebElement findVisibleTextElement(final String text) {
		final By by = By.xpath("(//*[normalize-space()=" + asXPathLiteral(text)
				+ " or contains(normalize-space(), " + asXPathLiteral(text) + ")])[1]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private void typeIfVisible(final By locator, final String value) {
		try {
			final WebElement field = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			field.clear();
			field.sendKeys(value);
		} catch (final TimeoutException ignored) {
			// Optional action: this field can vary by implementation.
		}
	}

	private void assertTextVisible(final String text) {
		final By by = By.xpath("//*[normalize-space()=" + asXPathLiteral(text) + " or contains(normalize-space(), "
				+ asXPathLiteral(text) + ")]");
		assertVisible(by, "Expected text not visible: " + text);
	}

	private void assertVisible(final By locator, final String errorMessage) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final TimeoutException timeoutException) {
			throw new AssertionError(errorMessage, timeoutException);
		}
	}

	private boolean isTextVisibleQuick(final String text) {
		try {
			final List<WebElement> elements = driver
					.findElements(By.xpath("//*[normalize-space()=" + asXPathLiteral(text) + " or contains(normalize-space(), "
							+ asXPathLiteral(text) + ")]"));
			return elements.stream().anyMatch(WebElement::isDisplayed);
		} catch (final NoSuchElementException exception) {
			return false;
		}
	}

	private void ensureApplicationWindow() {
		try {
			driver.switchTo().window(originalApplicationWindow);
		} catch (final Throwable ignored) {
			originalApplicationWindow = driver.getWindowHandle();
		}
	}

	private String waitForNewWindowIfAny(final Set<String> previousWindows) {
		final long timeoutMillis = Duration.ofSeconds(15).toMillis();
		final long started = System.currentTimeMillis();

		while (System.currentTimeMillis() - started < timeoutMillis) {
			final Set<String> currentWindows = driver.getWindowHandles();
			if (currentWindows.size() > previousWindows.size()) {
				for (final String handle : currentWindows) {
					if (!previousWindows.contains(handle)) {
						return handle;
					}
				}
			}
			sleep(200);
		}
		return null;
	}

	private void takeScreenshot(final String checkpoint) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		try {
			final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(SCREENSHOT_DIR.resolve(checkpoint + ".png"), bytes);
		} catch (final IOException ignored) {
			// Keep test flow running even if screenshot persistence fails.
		}
	}

	private void waitForUiLoad() {
		wait.until(driverState -> "complete"
				.equals(((JavascriptExecutor) driverState).executeScript("return document.readyState")));
		wait.until(driverState -> {
			final Object active = ((JavascriptExecutor) driverState)
					.executeScript("return (window.jQuery && jQuery.active) ? jQuery.active : 0");
			if (active instanceof Number) {
				return ((Number) active).intValue() == 0;
			}
			return true;
		});
		sleep(350);
	}

	private void writeReportFile() throws IOException {
		final StringBuilder content = new StringBuilder();
		content.append("SaleADS Mi Negocio Workflow Report\n");
		content.append("=================================\n");
		for (final String section : List.of(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad")) {
			final boolean ok = Boolean.TRUE.equals(report.get(section));
			content.append(section).append(": ").append(ok ? "PASS" : "FAIL").append('\n');
		}

		if (!reportDetails.isEmpty()) {
			content.append("\nDetails:\n");
			for (final Map.Entry<String, String> entry : reportDetails.entrySet()) {
				content.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		Files.writeString(SCREENSHOT_DIR.resolve("report.txt"), content.toString());
	}

	private boolean readBoolean(final String propertyName, final String envName, final boolean defaultValue) {
		return Boolean.parseBoolean(readConfig(propertyName, envName, String.valueOf(defaultValue)));
	}

	private long readLong(final String propertyName, final String envName, final long defaultValue) {
		final String value = readConfig(propertyName, envName, String.valueOf(defaultValue));
		try {
			return Long.parseLong(value);
		} catch (final NumberFormatException ignored) {
			return defaultValue;
		}
	}

	private String readConfig(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private String asXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final List<String> literals = new ArrayList<>();
		for (int i = 0; i < parts.length; i++) {
			if (!parts[i].isEmpty()) {
				literals.add("'" + parts[i] + "'");
			}
			if (i < parts.length - 1) {
				literals.add("\"'\"");
			}
		}
		return "concat(" + String.join(", ", literals) + ")";
	}

	private String sanitize(final String input) {
		return input.toLowerCase().replaceAll("[^a-z0-9]+", "-");
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});",
				element);
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(interruptedException);
		}
	}

	@FunctionalInterface
	private interface Step {
		void execute();
	}
}
