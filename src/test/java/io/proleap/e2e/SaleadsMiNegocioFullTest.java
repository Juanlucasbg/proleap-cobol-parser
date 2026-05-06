package io.proleap.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private static final String TERMS_TEXT = "T\u00e9rminos y Condiciones";
	private static final String PRIVACY_TEXT = "Pol\u00edtica de Privacidad";
	private static final String INFO_GENERAL_TEXT = "Informaci\u00f3n General";
	private static final String LEGAL_SECTION_TEXT = "Secci\u00f3n Legal";

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);

	private final Map<String, String> validationStatus = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setup() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--lang=es-ES");

		final String headlessFlag = System.getenv().getOrDefault("SALEADS_HEADLESS", "true");
		if (Boolean.parseBoolean(headlessFlag)) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
				.withZone(ZoneOffset.UTC)
				.format(Instant.now());
		evidenceDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);

		final String loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getProperty("saleads.login.url"));
		if (loginUrl == null) {
			throw new IllegalStateException(
					"Set SALEADS_LOGIN_URL (or -Dsaleads.login.url) to the SaleADS login page for the target environment.");
		}

		driver.get(loginUrl);
		waitForUiToSettle();
	}

	@After
	public void teardown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runValidation(LOGIN, this::stepLoginWithGoogle);
		runValidation(MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runValidation(AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runValidation(ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		runValidation(INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		runValidation(DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runValidation(TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runValidation(TERMINOS, this::stepValidateTerminosYCondiciones);
		runValidation(PRIVACIDAD, this::stepValidatePoliticaPrivacidad);

		final Path reportPath = writeFinalReport();
		final String summary = buildSummary();
		assertTrue("One or more workflow validations failed. Report: " + reportPath + System.lineSeparator() + summary,
				allStepsPassed());
	}

	private void stepLoginWithGoogle() throws IOException {
		clickFirstVisible(
				By.xpath("//button[contains(normalize-space(),'Google')]"),
				By.xpath("//a[contains(normalize-space(),'Google')]"),
				By.xpath("//*[@role='button' and contains(normalize-space(),'Google')]"));

		waitForUiToSettle();

		final WebElement accountOption = findOptionalVisible(
				By.xpath("//*[normalize-space()=" + xpathLiteral("juanlucasbarbiergarzon@gmail.com") + "]"),
				Duration.ofSeconds(15));
		if (accountOption != null) {
			safeClick(accountOption);
			waitForUiToSettle();
		}

		assertVisibleAny("Expected main application interface / left sidebar after login.",
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar')]"));
		assertVisible(By.xpath("//*[normalize-space()=" + xpathLiteral("Negocio") + "]"),
				"Expected sidebar option 'Negocio' after login.");

		saveScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByText("Negocio");
		clickByText("Mi Negocio");

		assertVisible(By.xpath("//*[normalize-space()=" + xpathLiteral("Agregar Negocio") + "]"),
				"Expected submenu option 'Agregar Negocio'.");
		assertVisible(By.xpath("//*[normalize-space()=" + xpathLiteral("Administrar Negocios") + "]"),
				"Expected submenu option 'Administrar Negocios'.");

		saveScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByText("Agregar Negocio");

		assertVisible(By.xpath("//*[normalize-space()=" + xpathLiteral("Crear Nuevo Negocio") + "]"),
				"Expected modal title 'Crear Nuevo Negocio'.");
		assertVisibleAny("Expected 'Nombre del Negocio' input field.",
				By.xpath("//label[contains(normalize-space(), " + xpathLiteral("Nombre del Negocio") + ")]"),
				By.xpath("//input[contains(@placeholder, " + xpathLiteral("Nombre del Negocio") + ")]"),
				By.xpath("//input[contains(@aria-label, " + xpathLiteral("Nombre del Negocio") + ")]"));
		assertVisible(By.xpath("//*[contains(normalize-space(), " + xpathLiteral("Tienes 2 de 3 negocios") + ")]"),
				"Expected business quota text.");
		assertVisible(By.xpath("//button[normalize-space()=" + xpathLiteral("Cancelar") + "]"),
				"Expected 'Cancelar' button.");
		assertVisible(By.xpath("//button[normalize-space()=" + xpathLiteral("Crear Negocio") + "]"),
				"Expected 'Crear Negocio' button.");

		saveScreenshot("03-crear-nuevo-negocio-modal.png");

		final WebElement businessNameInput = findOptionalVisible(
				By.xpath("//input[contains(@placeholder, " + xpathLiteral("Nombre del Negocio")
						+ ") or contains(@aria-label, " + xpathLiteral("Nombre del Negocio") + ")]"),
				SHORT_TIMEOUT);
		if (businessNameInput != null) {
			businessNameInput.click();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		}
		clickByText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureMiNegocioIsExpanded();
		clickByText("Administrar Negocios");

		assertVisible(By.xpath("//*[contains(normalize-space()," + xpathLiteral(INFO_GENERAL_TEXT) + ")]"),
				"Expected section 'Informacion General'.");
		assertVisible(By.xpath("//*[contains(normalize-space()," + xpathLiteral("Detalles de la Cuenta") + ")]"),
				"Expected section 'Detalles de la Cuenta'.");
		assertVisible(By.xpath("//*[contains(normalize-space()," + xpathLiteral("Tus Negocios") + ")]"),
				"Expected section 'Tus Negocios'.");
		assertVisible(By.xpath("//*[contains(normalize-space()," + xpathLiteral(LEGAL_SECTION_TEXT) + ")]"),
				"Expected section 'Seccion Legal'.");

		saveScreenshot("04-administrar-negocios-page.png");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading(INFO_GENERAL_TEXT);
		final String sectionText = section.getText();

		assertCondition(Pattern.compile("(?is).*[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}.*").matcher(sectionText).matches(),
				"Expected a visible user email in 'Informacion General'.");
		assertCondition(containsLikelyUserName(sectionText),
				"Expected a visible user name in 'Informacion General'.");
		assertCondition(sectionText.contains("BUSINESS PLAN"),
				"Expected text 'BUSINESS PLAN' in 'Informacion General'.");
		assertVisible(By.xpath("//button[normalize-space()=" + xpathLiteral("Cambiar Plan") + "]"),
				"Expected button 'Cambiar Plan'.");
	}

	private void stepValidateDetallesCuenta() {
		assertVisible(By.xpath("//*[contains(normalize-space()," + xpathLiteral("Cuenta creada") + ")]"),
				"Expected 'Cuenta creada'.");
		assertVisible(By.xpath("//*[contains(normalize-space()," + xpathLiteral("Estado activo") + ")]"),
				"Expected 'Estado activo'.");
		assertVisible(By.xpath("//*[contains(normalize-space()," + xpathLiteral("Idioma seleccionado") + ")]"),
				"Expected 'Idioma seleccionado'.");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final String sectionText = section.getText();

		assertCondition(!section.findElements(By.xpath(".//li | .//tr | .//*[contains(@class,'negocio')]")).isEmpty()
				|| sectionText.contains("Negocio"), "Expected visible business list in 'Tus Negocios'.");
		assertCondition(sectionText.contains("Agregar Negocio"), "Expected button text 'Agregar Negocio'.");
		assertCondition(sectionText.contains("Tienes 2 de 3 negocios"), "Expected business quota text in 'Tus Negocios'.");
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		final String finalUrl = clickLegalLinkAndValidate(
				TERMS_TEXT,
				TERMS_TEXT,
				"05-terminos-y-condiciones.png");
		legalUrls.put(TERMINOS, finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		final String finalUrl = clickLegalLinkAndValidate(
				PRIVACY_TEXT,
				PRIVACY_TEXT,
				"06-politica-de-privacidad.png");
		legalUrls.put(PRIVACIDAD, finalUrl);
	}

	private String clickLegalLinkAndValidate(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> existingWindows = driver.getWindowHandles();

		clickByText(linkText);

		boolean newTabOpened = false;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(ExpectedConditions.numberOfWindowsToBe(existingWindows.size() + 1));
			newTabOpened = true;
		} catch (final TimeoutException ignored) {
			// Link navigated in the same tab.
		}

		if (newTabOpened) {
			final Set<String> currentWindows = driver.getWindowHandles();
			for (final String handle : currentWindows) {
				if (!existingWindows.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		waitForUiToSettle();
		assertVisible(By.xpath("//*[contains(normalize-space()," + xpathLiteral(headingText) + ")]"),
				"Expected legal heading '" + headingText + "'.");
		assertCondition(driver.getPageSource().length() > 500, "Expected visible legal content text.");

		saveScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (newTabOpened) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiToSettle();
		assertVisible(By.xpath("//*[contains(normalize-space()," + xpathLiteral(LEGAL_SECTION_TEXT) + ")]"),
				"Expected to return to SaleADS account page.");
		return finalUrl;
	}

	private void runValidation(final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			validationStatus.put(stepName, "PASS");
		} catch (final Exception ex) {
			validationStatus.put(stepName, "FAIL - " + ex.getMessage());
		}
	}

	private void ensureMiNegocioIsExpanded() {
		final WebElement administrar = findOptionalVisible(
				By.xpath("//*[normalize-space()=" + xpathLiteral("Administrar Negocios") + "]"),
				SHORT_TIMEOUT);
		if (administrar != null) {
			return;
		}

		clickByText("Negocio");
		clickByText("Mi Negocio");
	}

	private WebElement findSectionByHeading(final String heading) {
		final String headingLiteral = xpathLiteral(heading);
		final By sectionBy = By.xpath(
				"//*[normalize-space()=" + headingLiteral + "]/ancestor::*[self::section or self::div][1]");
		final WebElement section = wait.until(ExpectedConditions.visibilityOfElementLocated(sectionBy));
		assertCondition(section != null, "Expected section for heading '" + heading + "'.");
		return section;
	}

	private void clickByText(final String text) {
		clickFirstVisible(
				By.xpath("//button[normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//a[normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//*[@role='button' and normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]"));
	}

	private void clickFirstVisible(final By... locators) {
		WebElement element = null;
		for (final By locator : locators) {
			element = findOptionalVisible(locator, SHORT_TIMEOUT);
			if (element != null) {
				break;
			}
		}
		assertCondition(element != null, "Could not find clickable element.");
		safeClick(element);
		waitForUiToSettle();
	}

	private WebElement findOptionalVisible(final By locator, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			return shortWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final TimeoutException ex) {
			return null;
		}
	}

	private void assertVisible(final By locator, final String failureMessage) {
		final WebElement element = findOptionalVisible(locator, DEFAULT_TIMEOUT);
		assertCondition(element != null, failureMessage);
	}

	private void assertVisibleAny(final String failureMessage, final By... locators) {
		for (final By locator : locators) {
			if (findOptionalVisible(locator, SHORT_TIMEOUT) != null) {
				return;
			}
		}
		assertCondition(false, failureMessage);
	}

	private void saveScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), evidenceDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private void safeClick(final WebElement element) {
		try {
			new Actions(driver).moveToElement(element).pause(Duration.ofMillis(200)).perform();
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception ex) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void waitForUiToSettle() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Fallback for cross-origin or transient loading states.
		}

		try {
			Thread.sleep(500);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private Path writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("# SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
		report.append(System.lineSeparator());
		report.append("- Generated at: ").append(Instant.now()).append(System.lineSeparator());
		report.append("- Evidence directory: ").append(evidenceDir).append(System.lineSeparator());
		report.append(System.lineSeparator());
		report.append("## Step Results").append(System.lineSeparator());
		for (final Map.Entry<String, String> entry : validationStatus.entrySet()) {
			report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}
		report.append(System.lineSeparator());
		report.append("## Final URLs").append(System.lineSeparator());
		report.append("- ").append(TERMINOS).append(": ")
				.append(legalUrls.getOrDefault(TERMINOS, "N/A")).append(System.lineSeparator());
		report.append("- ").append(PRIVACIDAD).append(": ")
				.append(legalUrls.getOrDefault(PRIVACIDAD, "N/A")).append(System.lineSeparator());

		final Path reportPath = evidenceDir.resolve("final-report.md");
		Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
		return reportPath;
	}

	private boolean allStepsPassed() {
		for (final String status : validationStatus.values()) {
			if (!"PASS".equals(status)) {
				return false;
			}
		}
		return true;
	}

	private String buildSummary() {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, String> entry : validationStatus.entrySet()) {
			if (!"PASS".equals(entry.getValue())) {
				failedSteps.add(entry.getKey() + " => " + entry.getValue());
			}
		}
		return failedSteps.isEmpty() ? "All validations passed." : String.join(System.lineSeparator(), failedSteps);
	}

	private boolean containsLikelyUserName(final String text) {
		for (final String line : text.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.contains("@")) {
				continue;
			}
			if ("BUSINESS PLAN".equals(trimmed)) {
				continue;
			}
			if ("Cambiar Plan".equals(trimmed)) {
				continue;
			}
			if (INFO_GENERAL_TEXT.equals(trimmed) || "Informacion General".equals(trimmed)) {
				continue;
			}
			if (trimmed.length() >= 3 && trimmed.matches(".*[\\p{L}].*")) {
				return true;
			}
		}
		return false;
	}

	private void assertCondition(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		return "concat('" + text.replace("'", "',\"'\",'") + "')";
	}

	private String firstNonBlank(final String first, final String second) {
		if (first != null && !first.trim().isEmpty()) {
			return first.trim();
		}
		if (second != null && !second.trim().isEmpty()) {
			return second.trim();
		}
		return null;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
