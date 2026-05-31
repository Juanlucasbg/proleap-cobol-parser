package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow for the SaleADS "Mi Negocio" module.
 *
 * Runtime configuration:
 * - SALEADS_URL or -Dsaleads.url: target login URL for current environment.
 * - SALEADS_HEADLESS or -Dsaleads.headless: true/false, defaults to true.
 */
public class SaleadsMiNegocioWorkflowE2ETest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (Boolean.parseBoolean(getConfig("SALEADS_HEADLESS", "saleads.headless", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		final String loginUrl = getConfig("SALEADS_URL", "saleads.url", null);
		if (loginUrl == null || loginUrl.isBlank()) {
			throw new IllegalStateException(
					"Missing login URL. Set SALEADS_URL or -Dsaleads.url for the target SaleADS environment.");
		}

		driver.navigate().to(loginUrl);
		waitForUiLoad();
	}

	@After
	public void tearDown() {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::validateLoginWithGoogle);
		runStep("Mi Negocio menu", this::validateMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::validateAdministrarNegociosView);
		runStep("Informaci\u00f3n General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("T\u00e9rminos y Condiciones", this::validateTerminosYCondiciones);
		runStep("Pol\u00edtica de Privacidad", this::validatePoliticaPrivacidad);

		assertTrue("One or more SaleADS workflow validations failed.\n" + renderReport(), allStepsPassed());
	}

	private void validateLoginWithGoogle() throws IOException {
		clickByAnyVisibleText("Sign in with Google", "Iniciar sesion con Google", "Continuar con Google", "Google");
		waitForUiLoad();
		selectGoogleAccountIfVisible();
		waitForUiLoad();

		// Main interface + left sidebar validation.
		assertVisibleByAnyText("Negocio", "Dashboard", "Tablero");
		assertSidebarVisible();
		captureScreenshot("01-dashboard-loaded.png");
	}

	private void validateMiNegocioMenu() throws IOException {
		expandMiNegocioMenu();
		assertVisibleByAnyText("Agregar Negocio");
		assertVisibleByAnyText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByAnyVisibleText("Agregar Negocio");
		waitForUiLoad();

		assertVisibleByAnyText("Crear Nuevo Negocio");
		assertVisibleByAnyText("Nombre del Negocio");
		assertVisibleByAnyText("Tienes 2 de 3 negocios");
		assertVisibleByAnyText("Cancelar");
		assertVisibleByAnyText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal.png");

		final WebElement businessNameInput = findVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio') or @name='businessName' or @id='businessName']"),
				Duration.ofSeconds(8));
		assertNotNull("Nombre del Negocio input was not found.", businessNameInput);
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");

		clickByAnyVisibleText("Cancelar");
		waitUntilTextNotVisible("Crear Nuevo Negocio");
	}

	private void validateAdministrarNegociosView() throws IOException {
		expandMiNegocioMenu();
		clickByAnyVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertVisibleByAnyText("Informacion General", "Informacion general", "Informaci\u00f3n General");
		assertVisibleByAnyText("Detalles de la Cuenta", "Detalles de la cuenta");
		assertVisibleByAnyText("Tus Negocios", "Tus negocios");
		assertVisibleByAnyText("Seccion Legal", "Seccion legal", "Secci\u00f3n Legal");
		captureScreenshot("04-administrar-negocios-view.png");
	}

	private void validateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Informacion General", "Informaci\u00f3n General");
		assertNotNull("Informacion General section is not visible.", section);

		final String sectionText = section.getText();
		assertTrue("User name is not visible in Informacion General.", looksLikeNameIsPresent(sectionText));
		assertTrue("User email is not visible in Informacion General.", looksLikeEmailIsPresent(sectionText));
		assertTrue("BUSINESS PLAN is not visible.", containsIgnoreCase(sectionText, "BUSINESS PLAN"));
		assertTrue("Cambiar Plan button is not visible.",
				containsIgnoreCase(sectionText, "Cambiar Plan") || isAnyTextVisible("Cambiar Plan"));
	}

	private void validateDetallesCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		assertNotNull("Detalles de la Cuenta section is not visible.", section);

		final String sectionText = section.getText();
		assertTrue("'Cuenta creada' is not visible.", containsIgnoreCase(sectionText, "Cuenta creada"));
		assertTrue("'Estado activo' is not visible.", containsIgnoreCase(sectionText, "Estado activo"));
		assertTrue("'Idioma seleccionado' is not visible.", containsIgnoreCase(sectionText, "Idioma seleccionado"));
	}

	private void validateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		assertNotNull("Tus Negocios section is not visible.", section);

		assertTrue("Agregar Negocio button is not visible in Tus Negocios.",
				containsIgnoreCase(section.getText(), "Agregar Negocio") || isAnyTextVisible("Agregar Negocio"));
		assertTrue("'Tienes 2 de 3 negocios' is not visible.",
				containsIgnoreCase(section.getText(), "Tienes 2 de 3 negocios")
						|| isAnyTextVisible("Tienes 2 de 3 negocios"));

		final List<WebElement> listCandidates = section.findElements(
				By.xpath(".//li | .//tr | .//article | .//*[contains(@class,'card')] | .//*[contains(@class,'business')]"));
		assertTrue("Business list is not visible in Tus Negocios.", listCandidates.stream().anyMatch(this::isDisplayedSafe));
	}

	private void validateTerminosYCondiciones() throws IOException {
		validateLegalLink("Terminos y Condiciones", "T\u00e9rminos y Condiciones", "08-terminos-y-condiciones.png",
				"Terminos y Condiciones");
	}

	private void validatePoliticaPrivacidad() throws IOException {
		validateLegalLink("Politica de Privacidad", "Pol\u00edtica de Privacidad", "09-politica-de-privacidad.png",
				"Politica de Privacidad");
	}

	private void validateLegalLink(final String plainTextLink, final String accentedHeading, final String screenshotName,
			final String reportKey) throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();
		final String appUrlBefore = driver.getCurrentUrl();

		clickByAnyVisibleText(plainTextLink, accentedHeading);
		waitForUiLoad();
		switchToNewWindowIfOpened(beforeHandles);

		assertVisibleByAnyText(plainTextLink, accentedHeading);
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal content text is not visible for: " + reportKey, bodyText != null && bodyText.trim().length() > 80);

		captureScreenshot(screenshotName);
		legalUrls.put(reportKey, driver.getCurrentUrl());

		if (!Objects.equals(driver.getWindowHandle(), originalHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiLoad();
			return;
		}

		if (!Objects.equals(driver.getCurrentUrl(), appUrlBefore)) {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, Boolean.TRUE);
		} catch (final Throwable error) {
			report.put(stepName, Boolean.FALSE);
			stepDetails.put(stepName, error.getClass().getSimpleName() + ": " + error.getMessage());
		}
	}

	private void expandMiNegocioMenu() {
		if (isAnyTextVisible("Agregar Negocio") && isAnyTextVisible("Administrar Negocios")) {
			return;
		}

		clickByAnyVisibleText("Negocio");
		waitForUiLoad();
		if (isAnyTextVisible("Agregar Negocio") && isAnyTextVisible("Administrar Negocios")) {
			return;
		}

		clickByAnyVisibleText("Mi Negocio");
		waitForUiLoad();
		assertVisibleByAnyText("Agregar Negocio");
		assertVisibleByAnyText("Administrar Negocios");
	}

	private void selectGoogleAccountIfVisible() {
		final List<WebElement> accountChoices = driver.findElements(
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]"));
		for (final WebElement candidate : accountChoices) {
			if (!isDisplayedSafe(candidate)) {
				continue;
			}
			safeClick(candidate);
			waitForUiLoad();
			return;
		}
	}

	private void assertSidebarVisible() {
		final List<WebElement> candidates = driver.findElements(By.xpath("//aside | //nav"));
		final boolean hasVisibleSidebar = candidates.stream().anyMatch(this::isDisplayedSafe);
		assertTrue("Left sidebar navigation is not visible.", hasVisibleSidebar);
	}

	private void assertVisibleByAnyText(final String... textCandidates) {
		if (isAnyTextVisible(textCandidates)) {
			return;
		}
		throw new AssertionError("None of the expected visible texts were found: " + String.join(", ", textCandidates));
	}

	private boolean isAnyTextVisible(final String... textCandidates) {
		for (final String text : textCandidates) {
			if (text == null || text.isBlank()) {
				continue;
			}
			final By by = By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]");
			final List<WebElement> elements = driver.findElements(by);
			for (final WebElement element : elements) {
				if (isDisplayedSafe(element)) {
					return true;
				}
			}
		}
		return false;
	}

	private void clickByAnyVisibleText(final String... textCandidates) {
		RuntimeException lastError = null;
		for (final String text : textCandidates) {
			try {
				clickByVisibleText(text);
				return;
			} catch (final RuntimeException error) {
				lastError = error;
			}
		}
		throw new RuntimeException("Unable to click any expected text: " + String.join(", ", textCandidates), lastError);
	}

	private void clickByVisibleText(final String text) {
		final List<By> locators = new ArrayList<>();
		locators.add(By.xpath("//button[contains(normalize-space(.)," + xpathLiteral(text) + ")]"));
		locators.add(By.xpath("//a[contains(normalize-space(.)," + xpathLiteral(text) + ")]"));
		locators.add(By.xpath("//*[@role='button' and contains(normalize-space(.)," + xpathLiteral(text) + ")]"));
		locators.add(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]"));

		for (final By locator : locators) {
			final List<WebElement> candidates = driver.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (!isDisplayedSafe(candidate)) {
					continue;
				}
				safeClick(candidate);
				waitForUiLoad();
				return;
			}
		}

		throw new RuntimeException("Unable to find visible clickable element for text: " + text);
	}

	private void safeClick(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
			return;
		} catch (final Exception ignored) {
			// Fallback to JS click when Selenium click is intercepted.
		}

		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
	}

	private WebElement findVisible(final By locator, final Duration timeout) {
		try {
			final WebDriverWait customWait = new WebDriverWait(driver, timeout);
			return customWait.until(ignoredDriver -> {
				for (final WebElement element : driver.findElements(locator)) {
					if (isDisplayedSafe(element)) {
						return element;
					}
				}
				return null;
			});
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private WebElement findSectionByHeading(final String... headingCandidates) {
		for (final String heading : headingCandidates) {
			final By sectionLocator = By.xpath(
					"//*[self::section or self::div or self::article][.//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::p][contains(normalize-space(.),"
							+ xpathLiteral(heading) + ")]]");
			final WebElement section = findVisible(sectionLocator, Duration.ofSeconds(8));
			if (section != null) {
				return section;
			}
		}
		return null;
	}

	private void switchToNewWindowIfOpened(final Set<String> beforeHandles) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
			shortWait.until(ignoredDriver -> driver.getWindowHandles().size() > beforeHandles.size());
		} catch (final TimeoutException ignored) {
			// Continue with the active tab if a new tab did not open.
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!beforeHandles.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				return;
			}
		}
	}

	private void waitUntilTextNotVisible(final String text) {
		final By locator = By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]");
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));
		shortWait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
	}

	private void waitForUiLoad() {
		wait.until(ignoredDriver -> "complete"
				.equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));

		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(3));
			shortWait.until(ignoredDriver -> {
				final List<WebElement> busyElements = driver.findElements(By
						.xpath("//*[contains(@class,'loading') or contains(@class,'spinner') or @aria-busy='true']"));
				return busyElements.stream().noneMatch(this::isDisplayedSafe);
			});
		} catch (final TimeoutException ignored) {
			// Not all pages use generic loading indicators. DOM ready is enough.
		}
	}

	private boolean isDisplayedSafe(final WebElement element) {
		try {
			return element.isDisplayed();
		} catch (final StaleElementReferenceException stale) {
			return false;
		}
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDir.resolve(fileName);
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() {
		try {
			if (evidenceDir == null) {
				return;
			}
			final Path reportFile = evidenceDir.resolve("final-report.txt");
			Files.writeString(reportFile, renderReport(), StandardCharsets.UTF_8);
		} catch (final IOException ignored) {
			// Keep teardown resilient even if report writing fails.
		}
	}

	private String renderReport() {
		final StringBuilder output = new StringBuilder();
		output.append("SaleADS Mi Negocio workflow report").append(System.lineSeparator());
		output.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		output.append(System.lineSeparator());
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			output.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL");
			if (!entry.getValue() && stepDetails.containsKey(entry.getKey())) {
				output.append(" - ").append(stepDetails.get(entry.getKey()));
			}
			output.append(System.lineSeparator());
		}
		output.append(System.lineSeparator());
		if (!legalUrls.isEmpty()) {
			output.append("Final legal URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				output.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}
		return output.toString();
	}

	private boolean allStepsPassed() {
		return !report.isEmpty() && report.values().stream().allMatch(Boolean::booleanValue);
	}

	private boolean containsIgnoreCase(final String text, final String expected) {
		return text != null && expected != null && text.toLowerCase().contains(expected.toLowerCase());
	}

	private boolean looksLikeEmailIsPresent(final String text) {
		return text != null && text.matches("(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*");
	}

	private boolean looksLikeNameIsPresent(final String text) {
		if (text == null) {
			return false;
		}
		// A simple heuristic: at least one two-token line without '@' and not obvious labels.
		final String[] lines = text.split("\\R");
		for (final String line : lines) {
			final String normalized = line.trim();
			if (normalized.isEmpty() || normalized.contains("@")) {
				continue;
			}
			if (containsIgnoreCase(normalized, "informacion general")
					|| containsIgnoreCase(normalized, "business plan")
					|| containsIgnoreCase(normalized, "cambiar plan")) {
				continue;
			}
			if (normalized.matches(".*[A-Za-z][A-Za-z .'-]{3,}.*")) {
				return true;
			}
		}
		return false;
	}

	private String getConfig(final String envVar, final String systemProperty, final String defaultValue) {
		final String envValue = System.getenv(envVar);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		final String propertyValue = System.getProperty(systemProperty);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		return defaultValue;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}
}
