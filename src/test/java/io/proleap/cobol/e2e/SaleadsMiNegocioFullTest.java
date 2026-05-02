package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final String DEFAULT_REPORT_DIR = "target/e2e-reports/saleads-mi-negocio";

	private final Map<String, String> finalReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> timeline = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path runDir;

	@Before
	public void setUp() throws IOException {
		runDir = Files.createDirectories(Paths.get(DEFAULT_REPORT_DIR, TS_FORMATTER.format(Instant.now())));
		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
	}

	@After
	public void tearDown() throws IOException {
		if (driver != null) {
			driver.quit();
		}
		writeReportFiles();
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		boolean loginPass = stepLoginWithGoogle();
		finalReport.put("Login", asPassFail(loginPass));

		boolean menuPass = stepOpenMiNegocioMenu();
		finalReport.put("Mi Negocio menu", asPassFail(menuPass));

		boolean modalPass = stepValidateAgregarNegocioModal();
		finalReport.put("Agregar Negocio modal", asPassFail(modalPass));

		boolean administrarPass = stepOpenAdministrarNegocios();
		finalReport.put("Administrar Negocios view", asPassFail(administrarPass));

		boolean infoGeneralPass = stepValidateInformacionGeneral();
		finalReport.put("Informacion General", asPassFail(infoGeneralPass));

		boolean detallesPass = stepValidateDetallesCuenta();
		finalReport.put("Detalles de la Cuenta", asPassFail(detallesPass));

		boolean tusNegociosPass = stepValidateTusNegocios();
		finalReport.put("Tus Negocios", asPassFail(tusNegociosPass));

		boolean termsPass = stepValidateLegalLink("Terminos y Condiciones", "Terminos y Condiciones",
				"step08-terminos");
		finalReport.put("Terminos y Condiciones", asPassFail(termsPass));

		boolean privacyPass = stepValidateLegalLink("Politica de Privacidad", "Politica de Privacidad",
				"step09-politica");
		finalReport.put("Politica de Privacidad", asPassFail(privacyPass));

		boolean allPass = loginPass && menuPass && modalPass && administrarPass && infoGeneralPass && detallesPass
				&& tusNegociosPass && termsPass && privacyPass;
		record("Final status: " + asPassFail(allPass));
		assertTrue("At least one workflow validation failed. See report in " + runDir.toAbsolutePath(), allPass);
	}

	private WebDriver createDriver() {
		String browser = Optional.ofNullable(System.getenv("SALEADS_E2E_BROWSER")).orElse("chrome").toLowerCase();
		boolean headless = Boolean.parseBoolean(Optional.ofNullable(System.getenv("SALEADS_E2E_HEADLESS")).orElse("true"));

		if ("firefox".equals(browser)) {
			FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			return new FirefoxDriver(options);
		}

		ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1600,1200");
		return new ChromeDriver(options);
	}

	private boolean stepLoginWithGoogle() {
		boolean passed = true;
		record("Step 1 - Login with Google");
		try {
			String loginUrl = Optional.ofNullable(System.getenv("SALEADS_LOGIN_URL")).orElse("").trim();
			if (loginUrl.isEmpty()) {
				throw new IllegalStateException(
						"SALEADS_LOGIN_URL is required because this test launches a new browser session.");
			}
			driver.get(loginUrl);
			waitForUiLoad();

			clickByVisibleTextContains(List.of("Sign in with Google", "Iniciar sesion con Google", "Google"));
			waitForUiLoad();
			selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");

			waitForAnyVisibleText(List.of("Negocio", "Mi Negocio", "Dashboard", "Inicio", "Panel"), DEFAULT_TIMEOUT);
			assertVisibleInSidebar();
			takeScreenshot("step01-dashboard-loaded");
		} catch (Exception ex) {
			passed = false;
			record("Step 1 failure: " + ex.getMessage());
			safeScreenshot("step01-failure");
		}
		return passed;
	}

	private boolean stepOpenMiNegocioMenu() {
		boolean passed = true;
		record("Step 2 - Open Mi Negocio menu");
		try {
			clickByVisibleTextContains(List.of("Mi Negocio", "Negocio"));
			waitForUiLoad();

			waitForAnyVisibleText(List.of("Agregar Negocio"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("Administrar Negocios"), DEFAULT_TIMEOUT);
			takeScreenshot("step02-mi-negocio-menu-expanded");
		} catch (Exception ex) {
			passed = false;
			record("Step 2 failure: " + ex.getMessage());
			safeScreenshot("step02-failure");
		}
		return passed;
	}

	private boolean stepValidateAgregarNegocioModal() {
		boolean passed = true;
		record("Step 3 - Validate Agregar Negocio modal");
		try {
			clickByVisibleTextContains(List.of("Agregar Negocio"));
			waitForUiLoad();

			waitForAnyVisibleText(List.of("Crear Nuevo Negocio"), DEFAULT_TIMEOUT);
			assertAnyElementPresent(List.of("Nombre del Negocio"));
			waitForAnyVisibleText(List.of("Tienes 2 de 3 negocios"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("Cancelar"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("Crear Negocio"), DEFAULT_TIMEOUT);
			takeScreenshot("step03-agregar-negocio-modal");

			typeIntoFieldByLabelOrPlaceholder(List.of("Nombre del Negocio"), "Negocio Prueba Automatizacion");
			clickByVisibleTextContains(List.of("Cancelar"));
			waitForUiLoad();
		} catch (Exception ex) {
			passed = false;
			record("Step 3 failure: " + ex.getMessage());
			safeScreenshot("step03-failure");
		}
		return passed;
	}

	private boolean stepOpenAdministrarNegocios() {
		boolean passed = true;
		record("Step 4 - Open Administrar Negocios");
		try {
			expandMiNegocioIfNeeded();
			clickByVisibleTextContains(List.of("Administrar Negocios"));
			waitForUiLoad();

			waitForAnyVisibleText(List.of("Informacion General", "Información General"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("Detalles de la Cuenta"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("Tus Negocios"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("Seccion Legal", "Sección Legal"), DEFAULT_TIMEOUT);
			takeScreenshot("step04-administrar-negocios");
		} catch (Exception ex) {
			passed = false;
			record("Step 4 failure: " + ex.getMessage());
			safeScreenshot("step04-failure");
		}
		return passed;
	}

	private boolean stepValidateInformacionGeneral() {
		boolean passed = true;
		record("Step 5 - Validate Informacion General");
		try {
			waitForAnyVisibleText(List.of("Informacion General", "Información General"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("@"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("Nombre", "Usuario", "Name"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("BUSINESS PLAN"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("Cambiar Plan"), DEFAULT_TIMEOUT);
		} catch (Exception ex) {
			passed = false;
			record("Step 5 failure: " + ex.getMessage());
			safeScreenshot("step05-failure");
		}
		return passed;
	}

	private boolean stepValidateDetallesCuenta() {
		boolean passed = true;
		record("Step 6 - Validate Detalles de la Cuenta");
		try {
			waitForAnyVisibleText(List.of("Cuenta creada"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("Estado activo"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("Idioma seleccionado"), DEFAULT_TIMEOUT);
		} catch (Exception ex) {
			passed = false;
			record("Step 6 failure: " + ex.getMessage());
			safeScreenshot("step06-failure");
		}
		return passed;
	}

	private boolean stepValidateTusNegocios() {
		boolean passed = true;
		record("Step 7 - Validate Tus Negocios");
		try {
			waitForAnyVisibleText(List.of("Tus Negocios"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("Agregar Negocio"), DEFAULT_TIMEOUT);
			waitForAnyVisibleText(List.of("Tienes 2 de 3 negocios"), DEFAULT_TIMEOUT);
		} catch (Exception ex) {
			passed = false;
			record("Step 7 failure: " + ex.getMessage());
			safeScreenshot("step07-failure");
		}
		return passed;
	}

	private boolean stepValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotPrefix) {
		boolean passed = true;
		record("Step legal validation - " + linkText);
		String appHandle = driver.getWindowHandle();
		String startUrl = driver.getCurrentUrl();
		try {
			Set<String> beforeHandles = driver.getWindowHandles();
			clickByVisibleTextContains(List.of(linkText));
			waitForUiLoad();
			String targetHandle = waitForPossibleNewTab(beforeHandles);

			if (targetHandle != null) {
				driver.switchTo().window(targetHandle);
				waitForUiLoad();
			}

			waitForAnyVisibleText(List.of(expectedHeading), DEFAULT_TIMEOUT);
			assertLegalContentVisible();
			String finalUrl = driver.getCurrentUrl();
			legalUrls.put(linkText, finalUrl);
			record(linkText + " URL: " + finalUrl);
			takeScreenshot(screenshotPrefix + "-page");

			// cleanup: return to app tab as requested.
			if (targetHandle != null) {
				driver.close();
				driver.switchTo().window(appHandle);
			} else {
				driver.navigate().back();
			}
			waitForUiLoad();
		} catch (Exception ex) {
			passed = false;
			record(linkText + " failure: " + ex.getMessage());
			safeScreenshot(screenshotPrefix + "-failure");
			try {
				if (!driver.getWindowHandle().equals(appHandle)) {
					driver.switchTo().window(appHandle);
				}
				if (driver.getCurrentUrl().equals(startUrl)) {
					waitForUiLoad();
				}
			} catch (Exception ignore) {
				// best-effort cleanup on failure.
			}
		}
		return passed;
	}

	private void expandMiNegocioIfNeeded() {
		if (!isAnyVisibleTextPresent(List.of("Administrar Negocios"), SHORT_TIMEOUT)) {
			clickByVisibleTextContains(List.of("Mi Negocio", "Negocio"));
			waitForUiLoad();
		}
	}

	private void clickByVisibleTextContains(final List<String> texts) {
		for (String text : texts) {
			List<By> candidates = textLocators(text);
			for (By locator : candidates) {
				try {
					WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
					scrollIntoView(element);
					element.click();
					waitForUiLoad();
					return;
				} catch (Exception ignore) {
					// Try the next locator variation.
				}
			}
		}
		throw new IllegalStateException("Could not find clickable element for any text: " + texts);
	}

	private void waitForAnyVisibleText(final List<String> texts, final Duration timeout) {
		WebDriverWait localWait = new WebDriverWait(driver, timeout);
		for (String text : texts) {
			for (By locator : textLocators(text)) {
				try {
					localWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
					return;
				} catch (TimeoutException ignore) {
					// continue
				}
			}
		}
		throw new IllegalStateException("Expected visible text not found: " + texts);
	}

	private boolean isAnyVisibleTextPresent(final List<String> texts, final Duration timeout) {
		try {
			waitForAnyVisibleText(texts, timeout);
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	private void assertAnyElementPresent(final List<String> texts) {
		for (String text : texts) {
			for (By locator : textLocators(text)) {
				if (!driver.findElements(locator).isEmpty()) {
					return;
				}
			}
		}
		throw new IllegalStateException("Expected element not present for any text: " + texts);
	}

	private void assertVisibleInSidebar() {
		List<By> sidebarSelectors = List.of(By.cssSelector("aside"), By.cssSelector("[class*='sidebar']"),
				By.cssSelector("nav"));
		for (By by : sidebarSelectors) {
			List<WebElement> elements = driver.findElements(by);
			for (WebElement element : elements) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}
		throw new IllegalStateException("Left sidebar/navigation not visible after login.");
	}

	private void typeIntoFieldByLabelOrPlaceholder(final List<String> labels, final String value) {
		for (String label : labels) {
			String safe = xpathTextContains(label);
			List<By> fieldLocators = List.of(
					By.xpath("//label[contains(translate(normalize-space(.), 'ÁÉÍÓÚÜÑ', 'AEIOUUN'), '" + safe
							+ "')]/following::input[1]"),
					By.xpath("//input[contains(translate(@placeholder, 'ÁÉÍÓÚÜÑ', 'AEIOUUN'), '" + safe + "')]"),
					By.xpath("//textarea[contains(translate(@placeholder, 'ÁÉÍÓÚÜÑ', 'AEIOUUN'), '" + safe + "')]"));

			for (By locator : fieldLocators) {
				List<WebElement> fields = driver.findElements(locator);
				for (WebElement field : fields) {
					if (field.isDisplayed()) {
						field.clear();
						field.sendKeys(value);
						return;
					}
				}
			}
		}
		throw new IllegalStateException("Could not type value; field not found for labels: " + labels);
	}

	private List<By> textLocators(final String value) {
		String safeUpper = xpathTextContains(value);
		String safeLower = safeUpper.toLowerCase();
		return List.of(
				By.xpath("//*[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑabcdefghijklmnopqrstuvwxyzáéíóúüñ', "
						+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUNABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUN'), '" + safeUpper
						+ "') or contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑabcdefghijklmnopqrstuvwxyzáéíóúüñ', "
						+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUNABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUN'), '" + safeLower + "')]"),
				By.xpath("//button[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑabcdefghijklmnopqrstuvwxyzáéíóúüñ', "
						+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUNABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUN'), '" + safeUpper
						+ "') or contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑabcdefghijklmnopqrstuvwxyzáéíóúüñ', "
						+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUNABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUN'), '" + safeLower + "')]"),
				By.xpath("//a[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑabcdefghijklmnopqrstuvwxyzáéíóúüñ', "
						+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUNABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUN'), '" + safeUpper
						+ "') or contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑabcdefghijklmnopqrstuvwxyzáéíóúüñ', "
						+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUNABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUN'), '" + safeLower + "')]"),
				By.xpath("//span[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑabcdefghijklmnopqrstuvwxyzáéíóúüñ', "
						+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUNABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUN'), '" + safeUpper
						+ "') or contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑabcdefghijklmnopqrstuvwxyzáéíóúüñ', "
						+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUNABCDEFGHIJKLMNOPQRSTUVWXYZAEIOUUN'), '" + safeLower + "')]"));
	}

	private void waitForUiLoad() {
		wait.until(webDriver -> "complete".equals(((org.openqa.selenium.JavascriptExecutor) webDriver)
				.executeScript("return document.readyState")));
	}

	private String waitForPossibleNewTab(final Set<String> previousHandles) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			Set<String> now = driver.getWindowHandles();
			if (now.size() > previousHandles.size()) {
				for (String handle : now) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}
			}
			try {
				Thread.sleep(200);
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		return null;
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		if (!isAnyVisibleTextPresent(List.of(accountEmail), SHORT_TIMEOUT)) {
			return;
		}
		try {
			clickByVisibleTextContains(List.of(accountEmail));
			waitForUiLoad();
		} catch (Exception ex) {
			record("Google account selector detected but could not click requested account: " + ex.getMessage());
		}
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		String fileName = checkpointName + ".png";
		Files.copy(screenshot.toPath(), runDir.resolve(fileName));
		record("Screenshot: " + fileName);
	}

	private void safeScreenshot(final String checkpointName) {
		try {
			takeScreenshot(checkpointName);
		} catch (Exception ignore) {
			record("Screenshot skipped for " + checkpointName);
		}
	}

	private void writeReportFiles() throws IOException {
		StringBuilder finalReportBuilder = new StringBuilder();
		finalReportBuilder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		for (Map.Entry<String, String> entry : finalReport.entrySet()) {
			finalReportBuilder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue())
					.append(System.lineSeparator());
		}

		Path finalReportPath = runDir.resolve("final-report.txt");
		Files.write(finalReportPath, finalReportBuilder.toString().getBytes(StandardCharsets.UTF_8));

		Path timelinePath = runDir.resolve("timeline.txt");
		Files.write(timelinePath, timeline, StandardCharsets.UTF_8);

		if (!legalUrls.isEmpty()) {
			StringBuilder legalUrlBuilder = new StringBuilder();
			for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
				legalUrlBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
			Files.write(runDir.resolve("legal-urls.txt"), legalUrlBuilder.toString().getBytes(StandardCharsets.UTF_8));
		}
	}

	private void record(final String message) {
		timeline.add(Instant.now().toString() + " | " + message);
	}

	private String asPassFail(final boolean pass) {
		return pass ? "PASS" : "FAIL";
	}

	private String xpathTextContains(final String text) {
		return Pattern.compile("\\s+").matcher(text).replaceAll(" ").trim().toUpperCase();
	}

	private void assertLegalContentVisible() {
		String pageText = driver.findElement(By.tagName("body")).getText();
		Matcher matcher = Pattern.compile("[A-Za-z0-9]{4,}\\s+[A-Za-z0-9]{4,}\\s+[A-Za-z0-9]{4,}").matcher(pageText);
		if (!matcher.find()) {
			throw new IllegalStateException("Legal content text was not detected on page.");
		}
	}

	private void scrollIntoView(final WebElement element) {
		((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});",
				element);
	}
}
