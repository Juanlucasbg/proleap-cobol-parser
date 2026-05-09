package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
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

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private boolean reportPrinted;

	private final Map<String, StepResult> report = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (isHeadlessEnabled()) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		evidenceDir = Paths.get("target", "saleads-evidence", TIMESTAMP_FORMAT.format(LocalDateTime.now())).toAbsolutePath();
		Files.createDirectories(evidenceDir);

		final String loginUrl = readValue("saleads.login.url", "SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() throws IOException {
		if (!reportPrinted) {
			printReport();
		}

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() throws IOException {
		executeStep("Login", this::loginWithGoogleAndValidateDashboard);
		executeStep("Mi Negocio menu", this::openMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::openAdministrarNegocios);
		executeStep("Información General", this::validateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		executeStep("Tus Negocios", this::validateTusNegocios);
		executeStep("Términos y Condiciones", this::validateTerminosYCondiciones);
		executeStep("Política de Privacidad", this::validatePoliticaDePrivacidad);

		printReport();
		reportPrinted = true;

		final List<String> failedSections = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				failedSections.add(entry.getKey());
			}
		}

		assertTrue("Some validations failed. Sections: " + String.join(", ", failedSections), failedSections.isEmpty());
	}

	private StepResult loginWithGoogleAndValidateDashboard() throws IOException {
		final List<String> notes = new ArrayList<>();

		if (!isSidebarVisible()) {
			final Set<String> handlesBeforeLoginClick = driver.getWindowHandles();
			clickByText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
			waitForUiToLoad();
			notes.add("Clicked Google login button.");

			switchToNewestWindowIfNeeded(handlesBeforeLoginClick);
			selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com", notes);
			returnToApplicationWindow();
		} else {
			notes.add("Session was already authenticated.");
		}

		waitUntilVisible(byTextContains("Negocio"), byTextContains("Mi Negocio"), bySidebarLocator());
		final boolean mainAppVisible = isAnyVisible(byTextContains("Negocio"), byTextContains("Mi Negocio"));
		final boolean sidebarVisible = isSidebarVisible();
		captureScreenshot("01-dashboard-loaded.png");

		return StepResult.from(mainAppVisible && sidebarVisible, notes,
				"Expected main application interface and sidebar after login.");
	}

	private StepResult openMiNegocioMenu() throws IOException {
		final List<String> notes = new ArrayList<>();

		clickIfVisible("Negocio");
		clickByText("Mi Negocio");
		waitForUiToLoad();

		final boolean submenuExpanded = isAnyVisible(byTextContains("Agregar Negocio"), byTextContains("Administrar Negocios"));
		final boolean agregarVisible = isAnyVisible(byTextContains("Agregar Negocio"));
		final boolean administrarVisible = isAnyVisible(byTextContains("Administrar Negocios"));
		captureScreenshot("02-mi-negocio-expanded.png");

		notes.add("Expanded Mi Negocio section from sidebar.");
		return StepResult.from(submenuExpanded && agregarVisible && administrarVisible, notes,
				"Expected expanded submenu with Agregar Negocio and Administrar Negocios.");
	}

	private StepResult validateAgregarNegocioModal() throws IOException {
		final List<String> notes = new ArrayList<>();

		clickByText("Agregar Negocio");
		waitForUiToLoad();
		waitUntilVisible(byTextContains("Crear Nuevo Negocio"));

		final boolean titleVisible = isAnyVisible(byTextContains("Crear Nuevo Negocio"));
		final boolean inputVisible = isAnyVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio')] | //label[contains(normalize-space(),'Nombre del Negocio')]"));
		final boolean planTextVisible = isAnyVisible(byTextContains("Tienes 2 de 3 negocios"));
		final boolean cancelButtonVisible = isAnyVisible(byButtonOrLinkText("Cancelar"));
		final boolean createButtonVisible = isAnyVisible(byButtonOrLinkText("Crear Negocio"));

		captureScreenshot("03-agregar-negocio-modal.png");

		final List<WebElement> nameInputs = findVisibleElements(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio') or @name='nombreNegocio']"));
		if (!nameInputs.isEmpty()) {
			nameInputs.get(0).click();
			nameInputs.get(0).sendKeys("Negocio Prueba Automatizacion");
			notes.add("Typed sample business name in modal.");
		}

		if (isAnyVisible(byButtonOrLinkText("Cancelar"))) {
			clickByText("Cancelar");
			waitForUiToLoad();
			notes.add("Closed modal using Cancelar.");
		}

		return StepResult.from(titleVisible && inputVisible && planTextVisible && cancelButtonVisible && createButtonVisible, notes,
				"Expected Crear Nuevo Negocio modal with required fields and actions.");
	}

	private StepResult openAdministrarNegocios() throws IOException {
		final List<String> notes = new ArrayList<>();

		if (!isAnyVisible(byTextContains("Administrar Negocios"))) {
			clickIfVisible("Mi Negocio");
		}

		clickByText("Administrar Negocios");
		waitForUiToLoad();

		final boolean infoGeneral = isAnyVisible(byTextContains("Información General"));
		final boolean detallesCuenta = isAnyVisible(byTextContains("Detalles de la Cuenta"));
		final boolean tusNegocios = isAnyVisible(byTextContains("Tus Negocios"));
		final boolean seccionLegal = isAnyVisible(byTextContains("Sección Legal"));

		captureScreenshot("04-administrar-negocios-page.png");
		notes.add("Opened account management page.");

		return StepResult.from(infoGeneral && detallesCuenta && tusNegocios && seccionLegal, notes,
				"Expected all account sections in Administrar Negocios view.");
	}

	private StepResult validateInformacionGeneral() {
		final List<String> notes = new ArrayList<>();
		final WebElement infoGeneralSection = findSectionByHeader("Información General");

		final boolean userEmailVisible = infoGeneralSection != null
				&& !infoGeneralSection.findElements(By.xpath(".//*[contains(normalize-space(),'@')]")).isEmpty();
		final boolean userNameVisible = infoGeneralSection != null
				&& !infoGeneralSection.findElements(By.xpath(".//*[normalize-space()!='' and string-length(normalize-space()) > 3]"))
						.isEmpty();
		final boolean businessPlanVisible = isAnyVisible(byTextContains("BUSINESS PLAN"));
		final boolean cambiarPlanVisible = isAnyVisible(byButtonOrLinkText("Cambiar Plan"));

		notes.add("Validated Información General section fields.");
		return StepResult.from(userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible, notes,
				"Expected user name/email, BUSINESS PLAN and Cambiar Plan button.");
	}

	private StepResult validateDetallesDeLaCuenta() {
		final List<String> notes = new ArrayList<>();

		final boolean cuentaCreadaVisible = isAnyVisible(byTextContains("Cuenta creada"));
		final boolean estadoActivoVisible = isAnyVisible(byTextContains("Estado activo"));
		final boolean idiomaSeleccionadoVisible = isAnyVisible(byTextContains("Idioma seleccionado"));

		notes.add("Validated Detalles de la Cuenta labels.");
		return StepResult.from(cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible, notes,
				"Expected Cuenta creada, Estado activo and Idioma seleccionado.");
	}

	private StepResult validateTusNegocios() {
		final List<String> notes = new ArrayList<>();
		final WebElement section = findSectionByHeader("Tus Negocios");

		final boolean hasBusinessList = section != null && !section.findElements(By.xpath(
				".//li | .//tr | .//article | .//div[contains(@class,'business') or contains(@class,'negocio')]")).isEmpty();
		final boolean addBusinessButton = isAnyVisible(byButtonOrLinkText("Agregar Negocio"));
		final boolean usageTextVisible = isAnyVisible(byTextContains("Tienes 2 de 3 negocios"));

		notes.add("Validated Tus Negocios content and controls.");
		return StepResult.from(hasBusinessList && addBusinessButton && usageTextVisible, notes,
				"Expected business list, Agregar Negocio button and plan usage text.");
	}

	private StepResult validateTerminosYCondiciones() throws IOException {
		return validateLegalDocument("Términos y Condiciones", "08-terminos-y-condiciones.png");
	}

	private StepResult validatePoliticaDePrivacidad() throws IOException {
		return validateLegalDocument("Política de Privacidad", "09-politica-de-privacidad.png");
	}

	private StepResult validateLegalDocument(final String linkText, final String screenshotName) throws IOException {
		final List<String> notes = new ArrayList<>();
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByText(linkText);
		waitForUiToLoad();
		final String targetWindow = switchToNewestWindowIfNeeded(handlesBeforeClick);

		waitUntilVisible(byTextContains(linkText));
		final boolean headingVisible = isAnyVisible(byTextContains(linkText));
		final boolean legalContentVisible = isAnyVisible(By.xpath("//p[string-length(normalize-space()) > 80]"),
				By.xpath("//main//*[string-length(normalize-space()) > 80]"),
				By.xpath("//article//*[string-length(normalize-space()) > 80]"));
		final String finalUrl = driver.getCurrentUrl();
		captureScreenshot(screenshotName);

		notes.add("Final URL: " + finalUrl);
		notes.add("Validated legal content visibility.");

		if (!targetWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
			notes.add("Closed legal tab and returned to application.");
		} else {
			driver.navigate().back();
			waitForUiToLoad();
			notes.add("Navigated back to application.");
		}

		return StepResult.from(headingVisible && legalContentVisible, notes,
				"Expected legal page heading and content for " + linkText + ".");
	}

	private void executeStep(final String name, final StepExecutor stepSupplier) {
		try {
			report.put(name, stepSupplier.execute());
		} catch (final Exception e) {
			final List<String> notes = new ArrayList<>();
			notes.add("Unexpected error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
			report.put(name, new StepResult(false, notes, "Step execution threw an exception."));
		}
	}

	private void printReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Workflow Report");
		lines.add("Evidence directory: " + evidenceDir);
		lines.add("");

		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final String stepName = entry.getKey();
			final StepResult result = entry.getValue();
			lines.add(stepName + ": " + (result.passed ? "PASS" : "FAIL"));
			for (final String note : result.notes) {
				lines.add("  - " + note);
			}
			if (!result.passed) {
				lines.add("  - Failure reason: " + result.failureReason);
			}
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.write(reportPath, lines);

		for (final String line : lines) {
			System.out.println(line);
		}
	}

	private boolean isHeadlessEnabled() {
		final String value = readValue("saleads.headless", "SALEADS_HEADLESS");
		return value == null || value.isBlank() || Boolean.parseBoolean(value);
	}

	private String readValue(final String systemProperty, final String envVar) {
		final String propertyValue = System.getProperty(systemProperty);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		final String envValue = System.getenv(envVar);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		return null;
	}

	private void waitForUiToLoad() {
		try {
			wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		} catch (final TimeoutException ignored) {
			// Some single-page transitions never fully update document.readyState, so we continue.
		}

		try {
			Thread.sleep(500);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void clickByText(final String... texts) {
		Exception latestError = null;
		for (final String text : texts) {
			try {
				final By locator = byButtonOrLinkText(text);
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
				element.click();
				waitForUiToLoad();
				return;
			} catch (final Exception e) {
				latestError = e;
			}
		}

		throw new IllegalStateException("Could not click any element using text options: " + String.join(", ", texts),
				latestError);
	}

	private void clickIfVisible(final String text) {
		final List<WebElement> elements = findVisibleElements(byButtonOrLinkText(text));
		if (!elements.isEmpty()) {
			elements.get(0).click();
			waitForUiToLoad();
		}
	}

	private void waitUntilVisible(final By... locators) {
		wait.until(driver -> {
			for (final By locator : locators) {
				if (isAnyVisible(locator)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isAnyVisible(final By... locators) {
		for (final By locator : locators) {
			if (!findVisibleElements(locator).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private List<WebElement> findVisibleElements(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		final List<WebElement> visibleElements = new ArrayList<>();
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				visibleElements.add(element);
			}
		}
		return visibleElements;
	}

	private boolean isSidebarVisible() {
		return isAnyVisible(bySidebarLocator(), By.xpath("//aside"), By.xpath("//nav"));
	}

	private By bySidebarLocator() {
		return By.xpath(
				"//aside | //nav[contains(@class,'sidebar') or contains(@class,'menu')] | //*[@role='navigation']");
	}

	private By byTextContains(final String text) {
		return By.xpath("//*[contains(normalize-space(),'" + text + "')]");
	}

	private By byButtonOrLinkText(final String text) {
		return By.xpath("//button[contains(normalize-space(),'" + text + "')] | //a[contains(normalize-space(),'" + text
				+ "')] | //*[@role='button' and contains(normalize-space(),'" + text + "')]");
	}

	private Path captureScreenshot(final String fileName) throws IOException {
		final byte[] screenshotData = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final Path destination = evidenceDir.resolve(fileName);
		return Files.write(destination, screenshotData);
	}

	private String switchToNewestWindowIfNeeded(final Set<String> previousHandles) {
		try {
			wait.until(driver -> driver.getWindowHandles().size() >= previousHandles.size());
		} catch (final TimeoutException ignored) {
			// Continue with current handle if no additional handle was detected.
		}

		final Set<String> currentHandles = driver.getWindowHandles();
		for (final String handle : currentHandles) {
			if (!previousHandles.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return handle;
			}
		}

		return driver.getWindowHandle();
	}

	private void selectGoogleAccountIfVisible(final String accountEmail, final List<String> notes) {
		final By accountLocator = By.xpath("//*[contains(normalize-space(),'" + accountEmail + "')]");
		final List<WebElement> matchingAccounts = findVisibleElements(accountLocator);
		if (!matchingAccounts.isEmpty()) {
			matchingAccounts.get(0).click();
			waitForUiToLoad();
			notes.add("Selected Google account: " + accountEmail);
		} else {
			notes.add("Google account selector did not appear or account was already selected.");
		}
	}

	private void returnToApplicationWindow() {
		wait.until(driver -> driver.getWindowHandles().size() >= 1);
		final Set<String> handles = driver.getWindowHandles();
		for (final String handle : handles) {
			driver.switchTo().window(handle);
			if (isSidebarVisible() || isAnyVisible(byTextContains("Mi Negocio"), byTextContains("Negocio"))) {
				waitForUiToLoad();
				return;
			}
		}
	}

	private WebElement findSectionByHeader(final String headerText) {
		final List<WebElement> headers = findVisibleElements(
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5][contains(normalize-space(),'"
						+ headerText + "')] | //*[contains(@class,'section') and contains(normalize-space(),'" + headerText
						+ "')]"));
		if (!headers.isEmpty()) {
			return headers.get(0).findElement(By.xpath("./ancestor-or-self::*[1]"));
		}

		return null;
	}

	private static final class StepResult {
		private final boolean passed;
		private final List<String> notes;
		private final String failureReason;

		private StepResult(final boolean passed, final List<String> notes, final String failureReason) {
			this.passed = passed;
			this.notes = notes;
			this.failureReason = failureReason;
		}

		private static StepResult from(final boolean passed, final List<String> notes, final String failureReason) {
			return new StepResult(passed, notes, failureReason);
		}
	}

	@FunctionalInterface
	private interface StepExecutor {
		StepResult execute() throws Exception;
	}
}
