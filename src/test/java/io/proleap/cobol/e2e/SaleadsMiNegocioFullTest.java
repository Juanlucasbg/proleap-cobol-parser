package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * End-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * Run with:
 *
 * mvn -Dtest=SaleadsMiNegocioFullTest test
 *
 * Required env vars:
 * - SALEADS_LOGIN_URL: login URL for the current environment.
 *
 * Optional env vars:
 * - SALEADS_SELENIUM_REMOTE_URL: Remote Selenium Grid URL.
 * - SALEADS_HEADLESS: "true" (default) or "false".
 */
public class SaleadsMiNegocioFullTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private static final String FIELD_LOGIN = "Login";
	private static final String FIELD_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String FIELD_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String FIELD_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String FIELD_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String FIELD_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String FIELD_TUS_NEGOCIOS = "Tus Negocios";
	private static final String FIELD_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String FIELD_POLITICA = "Pol\u00edtica de Privacidad";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> details = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		driver = createDriver();
		wait = new WebDriverWait(driver, TIMEOUT);

		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("SALEADS_LOGIN_URL must be provided for this test.", loginUrl != null && !loginUrl.isBlank());

		driver.get(loginUrl);
		waitForUiLoad();

		runStep(FIELD_LOGIN, this::stepLogin);
		runStep(FIELD_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(FIELD_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(FIELD_ADMINISTRAR_NEGOCIOS, this::stepOpenAdministrarNegocios);
		runStep(FIELD_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(FIELD_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(FIELD_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(FIELD_TERMINOS, () -> stepValidateLegalDocument("T\u00e9rminos y Condiciones",
				"T\u00e9rminos y Condiciones", "terminos-y-condiciones.png", "terminos_url"));
		runStep(FIELD_POLITICA, () -> stepValidateLegalDocument("Pol\u00edtica de Privacidad",
				"Pol\u00edtica de Privacidad", "politica-de-privacidad.png", "politica_url"));

		final Path reportPath = evidenceDir.resolve("final-report.json");
		Files.writeString(reportPath, toJsonReport(), StandardCharsets.UTF_8);
		System.out.println("SaleADS final report: " + reportPath.toAbsolutePath());
		System.out.println(toJsonReport());

		assertTrue("One or more validations failed.\n" + summarizeFailures(), allPassed());
	}

	private void stepLogin() throws IOException {
		if (!isAnyElementVisible(By.xpath("//aside | //nav | //*[@role='navigation']"), Duration.ofSeconds(5))) {
			clickByVisibleText("Google", "Sign in", "Iniciar sesi\u00f3n", "Ingresar", "Login");
			clickIfVisible(By.xpath("//*[contains(normalize-space(.),'" + ACCOUNT_EMAIL + "')]"), Duration.ofSeconds(10));
		}

		waitForAnyVisible(List.of(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[contains(normalize-space(.),'Negocio')]")));
		assertVisible(By.xpath("//aside | //nav | //*[@role='navigation']"), "Left sidebar navigation is not visible.");
		captureScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertVisible(By.xpath("//*[contains(normalize-space(.),'Agregar Negocio')]"),
				"'Agregar Negocio' should be visible.");
		assertVisible(By.xpath("//*[contains(normalize-space(.),'Administrar Negocios')]"),
				"'Administrar Negocios' should be visible.");
		captureScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		assertVisible(By.xpath("//*[contains(normalize-space(.),'Crear Nuevo Negocio')]"),
				"Modal title 'Crear Nuevo Negocio' should be visible.");
		assertVisible(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')] | //label[contains(normalize-space(.),'Nombre del Negocio')]"),
				"'Nombre del Negocio' input should be visible.");
		assertVisible(By.xpath("//*[contains(normalize-space(.),'Tienes 2 de 3 negocios')]"),
				"'Tienes 2 de 3 negocios' should be visible.");
		assertVisible(By.xpath("//button[contains(normalize-space(.),'Cancelar')]"), "'Cancelar' button should be visible.");
		assertVisible(By.xpath("//button[contains(normalize-space(.),'Crear Negocio')]"), "'Crear Negocio' button should be visible.");
		captureScreenshot("03-agregar-negocio-modal.png");

		clickIfVisible(By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"), Duration.ofSeconds(3));
		typeIfVisible(By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"), "Negocio Prueba Automatizacion");
		clickByVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isAnyElementVisible(By.xpath("//*[contains(normalize-space(.),'Administrar Negocios')]"), Duration.ofSeconds(3))) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		assertVisible(By.xpath("//*[contains(normalize-space(.),'Informaci\u00f3n General')]"),
				"'Informacion General' section should exist.");
		assertVisible(By.xpath("//*[contains(normalize-space(.),'Detalles de la Cuenta')]"),
				"'Detalles de la Cuenta' section should exist.");
		assertVisible(By.xpath("//*[contains(normalize-space(.),'Tus Negocios')]"), "'Tus Negocios' section should exist.");
		assertVisible(By.xpath("//*[contains(normalize-space(.),'Secci\u00f3n Legal')]"), "'Seccion Legal' section should exist.");
		captureScreenshot("04-administrar-negocios-view.png");
	}

	private void stepValidateInformacionGeneral() {
		assertTrue("User name is not visible.",
				isAnyElementVisible(By.xpath(
						"//*[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'juan')]"),
						Duration.ofSeconds(5)));
		assertTrue("User email is not visible.", isAnyElementVisible(By.xpath("//*[contains(normalize-space(.),'@')]"), Duration.ofSeconds(5))
				|| isAnyElementVisible(By.xpath("//*[contains(normalize-space(.),'" + ACCOUNT_EMAIL + "')]"), Duration.ofSeconds(5)));
		assertVisible(By.xpath("//*[contains(normalize-space(.),'BUSINESS PLAN')]"), "'BUSINESS PLAN' should be visible.");
		assertVisible(By.xpath("//button[contains(normalize-space(.),'Cambiar Plan')]"), "'Cambiar Plan' button should be visible.");
	}

	private void stepValidateDetallesCuenta() {
		assertVisible(By.xpath("//*[contains(normalize-space(.),'Cuenta creada')]"), "'Cuenta creada' should be visible.");
		assertVisible(By.xpath("//*[contains(normalize-space(.),'Estado activo')]"), "'Estado activo' should be visible.");
		assertVisible(By.xpath("//*[contains(normalize-space(.),'Idioma seleccionado')]"),
				"'Idioma seleccionado' should be visible.");
	}

	private void stepValidateTusNegocios() {
		assertVisible(By.xpath("//*[contains(normalize-space(.),'Tus Negocios')]"), "Business list section should be visible.");
		assertVisible(By.xpath("//*[contains(normalize-space(.),'Agregar Negocio')]"), "'Agregar Negocio' should be visible.");
		assertVisible(By.xpath("//*[contains(normalize-space(.),'Tienes 2 de 3 negocios')]"),
				"'Tienes 2 de 3 negocios' should be visible.");
	}

	private void stepValidateLegalDocument(final String linkText, final String headingText, final String screenshotName,
			final String urlKey) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final String appUrl = driver.getCurrentUrl();
		final Set<String> windowsBefore = driver.getWindowHandles();

		clickByVisibleText(linkText);

		wait.until(d -> d.getWindowHandles().size() > windowsBefore.size() || !d.getCurrentUrl().isBlank());
		switchToNewestWindowIfPresent(windowsBefore);
		waitForUiLoad();

		assertVisible(By.xpath("//*[contains(normalize-space(.),'" + headingText + "')]"),
				"Expected heading '" + headingText + "' was not visible.");
		assertTrue("Expected legal text to be visible for '" + headingText + "'.", hasVisibleParagraphText());

		legalUrls.put(urlKey, driver.getCurrentUrl());
		captureScreenshot(screenshotName);

		if (driver.getWindowHandles().size() > 1 && !driver.getWindowHandle().equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else if (driver.getWindowHandle().equals(appWindow) && !driver.getCurrentUrl().equals(appUrl)) {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String label, final ThrowingRunnable body) {
		try {
			body.run();
			report.put(label, true);
			details.put(label, "PASS");
		} catch (final Throwable error) {
			report.put(label, false);
			details.put(label, "FAIL: " + sanitize(error.getMessage()));
		}
	}

	private WebDriver createDriver() {
		final String remoteUrl = System.getenv("SALEADS_SELENIUM_REMOTE_URL");
		final boolean headless = !"false".equalsIgnoreCase(System.getenv("SALEADS_HEADLESS"));
		final ChromeOptions options = new ChromeOptions();

		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");

		if (remoteUrl != null && !remoteUrl.isBlank()) {
			try {
				return new RemoteWebDriver(new URL(remoteUrl), options);
			} catch (final MalformedURLException e) {
				throw new IllegalArgumentException("Invalid SALEADS_SELENIUM_REMOTE_URL: " + remoteUrl, e);
			}
		}

		WebDriverManager.chromedriver().setup();
		return new ChromeDriver(options);
	}

	private void clickByVisibleText(final String... texts) {
		for (final String text : texts) {
			final List<By> locators = new ArrayList<>();
			locators.add(By.xpath("//button[contains(normalize-space(.),'" + text + "')]"));
			locators.add(By.xpath("//a[contains(normalize-space(.),'" + text + "')]"));
			locators.add(By.xpath("//*[@role='button' and contains(normalize-space(.),'" + text + "')]"));
			locators.add(By.xpath("//*[contains(normalize-space(.),'" + text + "')]/ancestor-or-self::*[self::button or self::a][1]"));
			locators.add(By.xpath("//*[contains(normalize-space(.),'" + text + "')]"));

			for (final By locator : locators) {
				try {
					final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
					element.click();
					waitForUiLoad();
					return;
				} catch (final WebDriverException ignored) {
					// try fallback locator
				}
			}
		}

		throw new AssertionError("Could not find clickable element with text candidates: " + String.join(", ", texts));
	}

	private void clickIfVisible(final By locator, final Duration timeout) {
		try {
			final WebElement element = new WebDriverWait(driver, timeout)
					.until(ExpectedConditions.elementToBeClickable(locator));
			element.click();
			waitForUiLoad();
		} catch (final WebDriverException ignored) {
			// optional action
		}
	}

	private void typeIfVisible(final By locator, final String value) {
		try {
			final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			input.clear();
			input.sendKeys(value);
		} catch (final WebDriverException ignored) {
			// optional action
		}
	}

	private void waitForUiLoad() {
		try {
			wait.until(d -> {
				final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(readyState) || "interactive".equals(readyState);
			});
		} catch (final WebDriverException ignored) {
			// best effort
		}
	}

	private void waitForAnyVisible(final List<By> locators) {
		wait.until(d -> locators.stream().anyMatch(locator -> !d.findElements(locator).isEmpty()));
	}

	private boolean isAnyElementVisible(final By locator, final Duration timeout) {
		try {
			final WebDriverWait localWait = new WebDriverWait(driver, timeout);
			localWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final WebDriverException ignored) {
			return false;
		}
	}

	private void assertVisible(final By locator, final String failureMessage) {
		assertTrue(failureMessage, isAnyElementVisible(locator, Duration.ofSeconds(20)));
	}

	private boolean hasVisibleParagraphText() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p | //article | //section"));
		for (final WebElement paragraph : paragraphs) {
			if (paragraph.isDisplayed()) {
				final String text = paragraph.getText();
				if (text != null && text.trim().length() >= 30) {
					return true;
				}
			}
		}
		return false;
	}

	private void switchToNewestWindowIfPresent(final Set<String> windowsBefore) {
		for (final String window : driver.getWindowHandles()) {
			if (!windowsBefore.contains(window)) {
				driver.switchTo().window(window);
				return;
			}
		}
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(evidenceDir.resolve(fileName), screenshot);
	}

	private boolean allPassed() {
		return report.values().stream().allMatch(Boolean.TRUE::equals);
	}

	private String summarizeFailures() {
		final StringBuilder builder = new StringBuilder();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(details.get(entry.getKey())).append('\n');
			}
		}
		return builder.toString();
	}

	private String toJsonReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		builder.append("  \"report\": {\n");
		int index = 0;
		for (final String field : report.keySet()) {
			builder.append("    \"").append(escapeJson(field)).append("\": \"")
					.append(report.get(field) ? "PASS" : "FAIL").append("\"");
			if (index < report.size() - 1) {
				builder.append(',');
			}
			builder.append('\n');
			index++;
		}
		builder.append("  },\n");
		builder.append("  \"details\": {\n");
		index = 0;
		for (final String field : details.keySet()) {
			builder.append("    \"").append(escapeJson(field)).append("\": \"")
					.append(escapeJson(details.get(field))).append("\"");
			if (index < details.size() - 1) {
				builder.append(',');
			}
			builder.append('\n');
			index++;
		}
		builder.append("  },\n");
		builder.append("  \"legal_urls\": {\n");
		builder.append("    \"terminos\": \"").append(escapeJson(legalUrls.getOrDefault("terminos_url", ""))).append("\",\n");
		builder.append("    \"politica\": \"").append(escapeJson(legalUrls.getOrDefault("politica_url", ""))).append("\"\n");
		builder.append("  }\n");
		builder.append("}\n");
		return builder.toString();
	}

	private String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String sanitize(final String value) {
		if (value == null) {
			return "Unexpected error";
		}
		return value.replace('\n', ' ').replace('\r', ' ').trim();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
