package io.proleap.cobol.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * End-to-end UI workflow test for SaleADS Mi Negocio module.
 *
 * Environment assumptions:
 *  - Browser starts on SaleADS login page from current environment.
 *  - Domain is not hardcoded and can vary (dev/staging/prod).
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(25);
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String REPORT_FILE_NAME = "saleads_mi_negocio_full_test_report.txt";
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, Boolean> stepResult = new LinkedHashMap<>();
	private final List<String> notes = new ArrayList<>();
	private String termsUrl = "";
	private String privacyUrl = "";

	@Before
	public void setUp() throws IOException {
		final String testRunId = LocalDateTime.now().format(TS_FORMAT);
		evidenceDir = Paths.get("target", "saleads-evidence", "mi-negocio-" + testRunId);
		Files.createDirectories(evidenceDir);

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new");
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--lang=es-ES");
		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
	}

	@After
	public void tearDown() throws IOException {
		writeReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		// Step 1: Login with Google
		boolean step1 = false;
		try {
			driver.get(resolveLoginUrl());
			waitForUiSettled();

			final WebElement googleButton = findByVisibleTextClickTarget(
					"Sign in with Google", "Iniciar con Google", "Google", "Continuar con Google");
			assertNotNull("No se encontró botón de login con Google.", googleButton);
			safeClick(googleButton);
			waitForUiSettled();
			selectGoogleAccountIfShown(GOOGLE_ACCOUNT);

			assertTrue("No se detectó interfaz principal tras login.", isMainInterfaceVisible());
			assertTrue("No se detectó sidebar izquierdo.", isSidebarVisible());
			captureScreenshot("01-dashboard-loaded");

			step1 = true;
		} catch (Exception e) {
			notes.add("Step 1 failure: " + e.getMessage());
		}
		stepResult.put("Login", step1);

		// Step 2: Open Mi Negocio menu
		boolean step2 = false;
		try {
			expandMiNegocioMenu();
			assertTrue("No se visualiza 'Agregar Negocio'.", isAnyVisibleTextPresent("Agregar Negocio"));
			assertTrue("No se visualiza 'Administrar Negocios'.", isAnyVisibleTextPresent("Administrar Negocios"));
			captureScreenshot("02-mi-negocio-expanded");
			step2 = true;
		} catch (Exception e) {
			notes.add("Step 2 failure: " + e.getMessage());
		}
		stepResult.put("Mi Negocio menu", step2);

		// Step 3: Validate Agregar Negocio modal
		boolean step3 = false;
		try {
			clickByVisibleText("Agregar Negocio");
			waitForUiSettled();

			assertTrue("No aparece título 'Crear Nuevo Negocio'.", isAnyVisibleTextPresent("Crear Nuevo Negocio"));
			assertTrue("No aparece campo 'Nombre del Negocio'.", isInputRelatedTextVisible("Nombre del Negocio"));
			assertTrue("No aparece texto de cupo de negocios.", isAnyVisibleTextPresent("Tienes 2 de 3 negocios"));
			assertTrue("No aparece botón 'Cancelar'.", isAnyVisibleTextPresent("Cancelar"));
			assertTrue("No aparece botón 'Crear Negocio'.", isAnyVisibleTextPresent("Crear Negocio"));

			captureScreenshot("03-agregar-negocio-modal");
			optionalTypeBusinessNameAndCancel();
			step3 = true;
		} catch (Exception e) {
			notes.add("Step 3 failure: " + e.getMessage());
		}
		stepResult.put("Agregar Negocio modal", step3);

		// Step 4: Open Administrar Negocios
		boolean step4 = false;
		try {
			expandMiNegocioMenu();
			clickByVisibleText("Administrar Negocios");
			waitForUiSettled();

			assertTrue("No existe sección 'Información General'.", isAnyVisibleTextPresent("Información General"));
			assertTrue("No existe sección 'Detalles de la Cuenta'.", isAnyVisibleTextPresent("Detalles de la Cuenta"));
			assertTrue("No existe sección 'Tus Negocios'.", isAnyVisibleTextPresent("Tus Negocios"));
			assertTrue("No existe sección 'Sección Legal'.", isAnyVisibleTextPresent("Sección Legal"));
			captureScreenshot("04-administrar-negocios-view");
			step4 = true;
		} catch (Exception e) {
			notes.add("Step 4 failure: " + e.getMessage());
		}
		stepResult.put("Administrar Negocios view", step4);

		// Step 5: Validate Información General
		boolean step5 = false;
		try {
			assertTrue("No se encontró email visible de usuario.", hasVisibleEmail());
			assertTrue("No se detectó nombre visible de usuario.", hasLikelyUserNameVisible());
			assertTrue("No aparece texto BUSINESS PLAN.", isAnyVisibleTextPresent("BUSINESS PLAN"));
			assertTrue("No aparece botón Cambiar Plan.", isAnyVisibleTextPresent("Cambiar Plan"));
			step5 = true;
		} catch (Exception e) {
			notes.add("Step 5 failure: " + e.getMessage());
		}
		stepResult.put("Información General", step5);

		// Step 6: Validate Detalles de la Cuenta
		boolean step6 = false;
		try {
			assertTrue("No aparece 'Cuenta creada'.", isAnyVisibleTextPresent("Cuenta creada"));
			assertTrue("No aparece 'Estado activo'.", isAnyVisibleTextPresent("Estado activo"));
			assertTrue("No aparece 'Idioma seleccionado'.", isAnyVisibleTextPresent("Idioma seleccionado"));
			step6 = true;
		} catch (Exception e) {
			notes.add("Step 6 failure: " + e.getMessage());
		}
		stepResult.put("Detalles de la Cuenta", step6);

		// Step 7: Validate Tus Negocios
		boolean step7 = false;
		try {
			assertTrue("No se detectó bloque de lista de negocios.", isAnyVisibleTextPresent("Tus Negocios"));
			assertTrue("No existe botón Agregar Negocio en sección.", isAnyVisibleTextPresent("Agregar Negocio"));
			assertTrue("No aparece texto de cupo en sección.", isAnyVisibleTextPresent("Tienes 2 de 3 negocios"));
			step7 = true;
		} catch (Exception e) {
			notes.add("Step 7 failure: " + e.getMessage());
		}
		stepResult.put("Tus Negocios", step7);

		// Step 8: Validate Términos y Condiciones
		boolean step8 = false;
		try {
			termsUrl = openLegalLinkAndValidate("Términos y Condiciones", "08-terminos");
			step8 = true;
		} catch (Exception e) {
			notes.add("Step 8 failure: " + e.getMessage());
		}
		stepResult.put("Términos y Condiciones", step8);

		// Step 9: Validate Política de Privacidad
		boolean step9 = false;
		try {
			privacyUrl = openLegalLinkAndValidate("Política de Privacidad", "09-politica-privacidad");
			step9 = true;
		} catch (Exception e) {
			notes.add("Step 9 failure: " + e.getMessage());
		}
		stepResult.put("Política de Privacidad", step9);

		assertFalse("Workflow completed with FAIL states. Review generated report.", stepResult.containsValue(false));
	}

	private String resolveLoginUrl() {
		final String envUrl = System.getenv("SALEADS_LOGIN_URL");
		if (envUrl != null && !envUrl.isBlank()) {
			return envUrl.trim();
		}

		final String propertyUrl = System.getProperty("saleads.login.url");
		if (propertyUrl != null && !propertyUrl.isBlank()) {
			return propertyUrl.trim();
		}

		throw new IllegalStateException(
				"No login URL provided. Set SALEADS_LOGIN_URL env var or -Dsaleads.login.url JVM property.");
	}

	private void selectGoogleAccountIfShown(final String accountEmail) {
		try {
			waitForUiSettled();
			final Optional<WebElement> accountOption = findVisibleElementContainingText(accountEmail);
			if (accountOption.isPresent()) {
				safeClick(accountOption.get());
				waitForUiSettled();
				return;
			}

			final List<WebElement> emailFields = driver
					.findElements(By.xpath("//input[contains(@type,'email') or contains(@autocomplete,'username')]"));
			if (!emailFields.isEmpty() && emailFields.get(0).isDisplayed()) {
				emailFields.get(0).clear();
				emailFields.get(0).sendKeys(accountEmail);
				emailFields.get(0).sendKeys(Keys.ENTER);
				waitForUiSettled();
			}
		} catch (Exception ignored) {
			notes.add("Google account selector not explicitly detected; proceeding with current session state.");
		}
	}

	private boolean isMainInterfaceVisible() {
		return isSidebarVisible() || isAnyVisibleTextPresent("Dashboard") || isAnyVisibleTextPresent("Negocio");
	}

	private boolean isSidebarVisible() {
		final List<By> sidebarCandidates = List.of(
				By.cssSelector("aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar')]"),
				By.xpath("//*[contains(@class,'SideBar')]"));
		for (By by : sidebarCandidates) {
			final List<WebElement> elements = driver.findElements(by);
			for (WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void expandMiNegocioMenu() {
		waitForUiSettled();
		clickIfVisible("Negocio");
		if (!isAnyVisibleTextPresent("Mi Negocio")) {
			notes.add("Text 'Mi Negocio' not visible after clicking 'Negocio'. Attempting direct click.");
		}
		clickByVisibleText("Mi Negocio");
		waitForUiSettled();
	}

	private void optionalTypeBusinessNameAndCancel() {
		final Optional<WebElement> input = findVisibleInputByRelatedText("Nombre del Negocio");
		if (input.isPresent()) {
			input.get().click();
			input.get().sendKeys("Negocio Prueba Automatización");
		}
		clickByVisibleText("Cancelar");
		waitForUiSettled();
	}

	private String openLegalLinkAndValidate(final String linkText, final String screenshotPrefix) throws IOException {
		waitForUiSettled();
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String originalUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		waitForUiSettled();

		wait.until(anyNewWindowOrSameNavigated(handlesBefore, originalUrl));
		final boolean openedNewWindow = driver.getWindowHandles().size() > handlesBefore.size();
		final String targetWindow;
		if (openedNewWindow) {
			targetWindow = getNewWindowHandle(handlesBefore);
		} else {
			targetWindow = appWindow;
		}
		driver.switchTo().window(targetWindow);
		waitForUiSettled();

		assertTrue("No se encontró encabezado legal esperado: " + linkText, isAnyVisibleTextPresent(linkText));
		assertTrue("No se encontró contenido legal visible para: " + linkText, hasSubstantialVisibleText());
		captureScreenshot(screenshotPrefix + "-page");
		final String url = driver.getCurrentUrl();

		if (!targetWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}
		waitForUiSettled();
		return url;
	}

	private ExpectedCondition<Boolean> anyNewWindowOrSameNavigated(final Set<String> previousHandles,
			final String originalUrl) {
		return webDriver -> {
			final Set<String> nowHandles = webDriver.getWindowHandles();
			if (nowHandles.size() > previousHandles.size()) {
				return true;
			}
			final String currentUrl = webDriver.getCurrentUrl();
			if (currentUrl != null && !currentUrl.equals(originalUrl)) {
				return true;
			}
			return null;
		};
	}

	private void clickIfVisible(final String text) {
		final WebElement element = findByVisibleTextClickTarget(text);
		if (element != null) {
			safeClick(element);
		}
	}

	private String getNewWindowHandle(final Set<String> previousHandles) {
		for (String handle : driver.getWindowHandles()) {
			if (!previousHandles.contains(handle)) {
				return handle;
			}
		}
		throw new NoSuchElementException("No new window handle was found after legal link click.");
	}

	private void clickByVisibleText(final String text) {
		final WebElement element = findByVisibleTextClickTarget(text);
		if (element == null) {
			throw new NoSuchElementException("Clickable element not found by text: " + text);
		}
		safeClick(element);
	}

	private WebElement findByVisibleTextClickTarget(final String... candidateTexts) {
		for (String text : candidateTexts) {
			final List<By> locators = List.of(
					By.xpath("//button[normalize-space()='" + text + "']"),
					By.xpath("//a[normalize-space()='" + text + "']"),
					By.xpath("//*[self::span or self::div or self::p][normalize-space()='" + text + "']"),
					By.xpath("//*[contains(normalize-space(.),'" + text + "')]"));
			for (By by : locators) {
				final List<WebElement> elements = driver.findElements(by);
				for (WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
		}
		return null;
	}

	private Optional<WebElement> findVisibleElementContainingText(final String text) {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[contains(normalize-space(.),'" + text + "')]"));
		for (WebElement element : elements) {
			if (element.isDisplayed()) {
				return Optional.of(element);
			}
		}
		return Optional.empty();
	}

	private boolean isAnyVisibleTextPresent(final String text) {
		try {
			return wait.until(driver -> findVisibleElementContainingText(text).isPresent());
		} catch (TimeoutException e) {
			return false;
		}
	}

	private Optional<WebElement> findVisibleInputByRelatedText(final String labelText) {
		final List<By> locators = List.of(
				By.xpath("//label[contains(normalize-space(.),'" + labelText + "')]/following::input[1]"),
				By.xpath("//input[@placeholder='" + labelText + "']"),
				By.xpath("//input[contains(@aria-label,'" + labelText + "')]"));
		for (By by : locators) {
			final List<WebElement> elements = driver.findElements(by);
			for (WebElement element : elements) {
				if (element.isDisplayed()) {
					return Optional.of(element);
				}
			}
		}
		return Optional.empty();
	}

	private boolean isInputRelatedTextVisible(final String labelText) {
		return isAnyVisibleTextPresent(labelText) || findVisibleInputByRelatedText(labelText).isPresent();
	}

	private boolean hasVisibleEmail() {
		final List<WebElement> emailLike = driver.findElements(By.xpath("//*[contains(text(),'@')]"));
		for (WebElement element : emailLike) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean hasLikelyUserNameVisible() {
		final List<WebElement> candidates = driver.findElements(By.xpath("//h1|//h2|//h3|//p|//span"));
		for (WebElement candidate : candidates) {
			if (!candidate.isDisplayed()) {
				continue;
			}
			final String text = candidate.getText();
			if (text != null && text.trim().length() > 3 && !text.contains("@") && text.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private boolean hasSubstantialVisibleText() {
		final List<WebElement> blocks = driver.findElements(By.xpath("//p|//article|//section|//div"));
		for (WebElement block : blocks) {
			if (!block.isDisplayed()) {
				continue;
			}
			final String text = block.getText();
			if (text != null && text.trim().length() >= 80) {
				return true;
			}
		}
		return false;
	}

	private void safeClick(final WebElement element) {
		try {
			wait.until(driver -> element.isDisplayed() && element.isEnabled());
			element.click();
		} catch (Exception e) {
			new Actions(driver).moveToElement(element).pause(Duration.ofMillis(150)).click().perform();
		}
		waitForUiSettled();
	}

	private void waitForUiSettled() {
		wait.until(driver -> {
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			final Object readyState = js.executeScript("return document.readyState");
			return "complete".equals(readyState);
		});
		try {
			Thread.sleep(700);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final Path screenshotPath = evidenceDir.resolve(checkpointName + ".png");
		Files.write(screenshotPath, screenshot);
	}

	private void writeReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test\n");
		report.append("generatedAt=").append(LocalDateTime.now()).append('\n');
		report.append("currentUrl=").append(safeCurrentUrl()).append('\n');
		report.append('\n');
		report.append("Final Report\n");
		for (Map.Entry<String, Boolean> entry : stepResult.entrySet()) {
			report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append('\n');
		}
		report.append('\n');
		report.append("Términos y Condiciones URL=").append(termsUrl).append('\n');
		report.append("Política de Privacidad URL=").append(privacyUrl).append('\n');
		report.append("Evidence directory=").append(evidenceDir.toAbsolutePath()).append('\n');

		if (!notes.isEmpty()) {
			report.append('\n').append("Notes\n");
			for (String note : notes) {
				report.append("- ").append(note).append('\n');
			}
		}

		Files.writeString(evidenceDir.resolve(REPORT_FILE_NAME), report.toString());
	}

	private String safeCurrentUrl() {
		try {
			return driver == null ? "" : URI.create(driver.getCurrentUrl()).toString();
		} catch (Exception e) {
			return "";
		}
	}
}
