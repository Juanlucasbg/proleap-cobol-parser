package io.proleap.cobol.e2e.saleads;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration STEP_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> stepReport = new LinkedHashMap<>();
	private final Map<String, String> failureReasons = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		final String targetUrl = getConfigValue("saleads.url", "SALEADS_URL");
		Assume.assumeTrue(
				"Provide SaleADS login URL via -Dsaleads.url=<url> or SALEADS_URL environment variable.",
				targetUrl != null && !targetUrl.isBlank());

		evidenceDir = buildEvidenceDir();
		driver = new ChromeDriver(buildChromeOptions());
		wait = new WebDriverWait(driver, STEP_TIMEOUT);

		driver.manage().window().maximize();
		driver.get(targetUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
		captureScreenshot("00_login_page");
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones",
				"08_terminos_y_condiciones", "Términos y Condiciones", "Terminos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad",
				"09_politica_de_privacidad", "Política de Privacidad", "Politica de Privacidad"));

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> step : stepReport.entrySet()) {
			if (!step.getValue().booleanValue()) {
				failedSteps.add(step.getKey() + " -> " + failureReasons.get(step.getKey()));
			}
		}

		if (!failedSteps.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow failed:\n" + String.join("\n", failedSteps));
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		if (isSidebarVisible()) {
			captureScreenshot("01_dashboard_loaded");
			return;
		}

		clickByVisibleText("Sign in with Google", "Login with Google", "Iniciar sesión con Google",
				"Iniciar sesion con Google");

		selectGoogleAccountIfShown(GOOGLE_ACCOUNT_EMAIL);
		waitUntilMainApplicationIsVisible();
		Assert.assertTrue("Left sidebar navigation should be visible after login.", isSidebarVisible());
		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		Assert.assertTrue("Sidebar must be visible.", isSidebarVisible());
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Administrar Negocios");
		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		assertAnyTextVisible("Crear Nuevo Negocio");
		assertAnyTextVisible("Nombre del Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertAnyTextVisible("Cancelar");
		assertAnyTextVisible("Crear Negocio");

		captureScreenshot("03_agregar_negocio_modal");
		typeIfVisible(By.xpath("//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio']"),
				"Negocio Prueba Automatizacion");
		clickByVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		expandMiNegocioIfNeeded();
		clickByVisibleText("Administrar Negocios");

		assertAnyTextVisible("Información General", "Informacion General");
		assertAnyTextVisible("Detalles de la Cuenta", "Detalles de la Cuenta");
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Sección Legal", "Seccion Legal");
		captureScreenshot("04_administrar_negocios");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		assertTextPresentByPatterns(
				By.xpath("//*[contains(normalize-space(.), '@') and not(self::script)]"),
				"User email should be visible in Informacion General section.");
		assertAnyTextVisible("BUSINESS PLAN");
		assertAnyTextVisible("Cambiar Plan");
		assertTextPresentByPatterns(
				By.xpath("//section//*[self::h1 or self::h2 or self::h3 or self::p or self::span][string-length(normalize-space(.)) > 2]"),
				"User name should be visible in Informacion General section.");
	}

	private void stepValidateDetallesCuenta() throws Exception {
		assertAnyTextVisible("Cuenta creada");
		assertAnyTextVisible("Estado activo");
		assertAnyTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() throws Exception {
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertTextPresentByPatterns(
				By.xpath("//*[contains(@class, 'business') or contains(@class, 'negocio') or self::table or self::ul]"),
				"Business list container should be visible.");
	}

	private void stepValidateLegalLink(final String linkText, final String screenshotName, final String... headings)
			throws Exception {
		final String startingHandle = driver.getWindowHandle();
		final String startingUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);

		final String targetHandle = resolveTargetWindowHandle(handlesBeforeClick, startingHandle, startingUrl);
		if (!targetHandle.equals(driver.getWindowHandle())) {
			driver.switchTo().window(targetHandle);
		}

		waitForUiToLoad();
		assertAnyTextVisible(headings);
		assertLegalBodyTextVisible();
		captureScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (!targetHandle.equals(startingHandle)) {
			driver.close();
			driver.switchTo().window(startingHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void runStep(final String reportName, final StepExecutor executor) {
		try {
			executor.run();
			stepReport.put(reportName, Boolean.TRUE);
		} catch (final Exception e) {
			stepReport.put(reportName, Boolean.FALSE);
			failureReasons.put(reportName, e.getMessage());
			try {
				captureScreenshot("failed_" + sanitizeFileName(reportName));
			} catch (final Exception ignored) {
				// best effort evidence capture
			}
		}
	}

	private void waitUntilMainApplicationIsVisible() {
		wait.until(driver -> isSidebarVisible());
	}

	private void expandMiNegocioIfNeeded() {
		if (!isAnyTextVisibleNow("Administrar Negocios")) {
			clickByVisibleText("Negocio");
			clickByVisibleText("Mi Negocio");
		}
	}

	private void selectGoogleAccountIfShown(final String email) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);
			final WebElement account = shortWait.until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(email) + ")]")));
			clickAndWait(account);
		} catch (final TimeoutException ignored) {
			// Account chooser is optional; in some runs user may already be logged in.
		}
	}

	private void clickByVisibleText(final String... textOptions) {
		final WebElement element = wait.until(driver -> findFirstVisibleElementByText(textOptions));
		clickAndWait(element);
	}

	private WebElement findFirstVisibleElementByText(final String... textOptions) {
		for (final String text : textOptions) {
			final List<By> candidates = buildTextLocators(text);
			for (final By locator : candidates) {
				for (final WebElement element : driver.findElements(locator)) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
		}
		return null;
	}

	private List<By> buildTextLocators(final String text) {
		final String value = toXPathLiteral(text);
		final List<By> locators = new ArrayList<>();
		locators.add(By.xpath("//button[normalize-space(.) = " + value + "]"));
		locators.add(By.xpath("//a[normalize-space(.) = " + value + "]"));
		locators.add(By.xpath("//*[@role='button' and normalize-space(.) = " + value + "]"));
		locators.add(By.xpath("//*[normalize-space(.) = " + value + "]"));
		locators.add(By.xpath("//*[contains(normalize-space(.), " + value + ")]"));
		return locators;
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.visibilityOf(element));
		try {
			element.click();
		} catch (final Exception ignored) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		final ExpectedCondition<Boolean> readyStateComplete = wd -> "complete"
				.equals(((JavascriptExecutor) wd).executeScript("return document.readyState"));
		wait.until(readyStateComplete);
	}

	private boolean isSidebarVisible() {
		for (final WebElement sidebar : driver.findElements(By.xpath("//aside | //nav"))) {
			if (sidebar.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void assertAnyTextVisible(final String... textOptions) {
		boolean found = false;
		for (final String text : textOptions) {
			if (isAnyTextVisibleNow(text)) {
				found = true;
				break;
			}
		}
		if (found) {
			return;
		}

		wait.until(driver -> {
			for (final String text : textOptions) {
				if (isAnyTextVisibleNow(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isAnyTextVisibleNow(final String text) {
		final By textLocator = By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]");
		for (final WebElement element : driver.findElements(textLocator)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void assertTextPresentByPatterns(final By locator, final String failureMessage) {
		final List<WebElement> elements = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(locator));
		boolean foundVisibleText = false;
		for (final WebElement element : elements) {
			if (element.isDisplayed() && !element.getText().isBlank()) {
				foundVisibleText = true;
				break;
			}
		}
		Assert.assertTrue(failureMessage, foundVisibleText);
	}

	private void assertLegalBodyTextVisible() {
		final By legalParagraphs = By.xpath("//main//p | //article//p | //p");
		final List<WebElement> paragraphs = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(legalParagraphs));
		boolean found = false;
		for (final WebElement paragraph : paragraphs) {
			if (paragraph.isDisplayed() && paragraph.getText().trim().length() > 40) {
				found = true;
				break;
			}
		}
		Assert.assertTrue("Expected legal content text to be visible.", found);
	}

	private void typeIfVisible(final By locator, final String value) {
		try {
			final WebElement field = new WebDriverWait(driver, SHORT_TIMEOUT)
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			field.clear();
			field.sendKeys(value);
			waitForUiToLoad();
		} catch (final TimeoutException ignored) {
			// Optional action by requirement.
		}
	}

	private String resolveTargetWindowHandle(final Set<String> handlesBeforeClick, final String currentHandle,
			final String currentUrl) {
		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);
		try {
			return shortWait.until(driver -> {
				final Set<String> handlesNow = driver.getWindowHandles();
				if (handlesNow.size() > handlesBeforeClick.size()) {
					for (final String handle : handlesNow) {
						if (!handlesBeforeClick.contains(handle)) {
							return handle;
						}
					}
				}

				if (!driver.getCurrentUrl().equals(currentUrl)) {
					return currentHandle;
				}

				return null;
			});
		} catch (final TimeoutException e) {
			return currentHandle;
		}
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final Path destination = evidenceDir.resolve(checkpointName + ".png");
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private Path buildEvidenceDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		final Path dir = Path.of("target", "saleads-evidence", "saleads_mi_negocio_full_test", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder builder = new StringBuilder();
		builder.append("Final Report - saleads_mi_negocio_full_test\n");
		builder.append("===========================================\n");

		final String[] reportFields = { "Login", "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
				"Información General", "Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones",
				"Política de Privacidad" };

		for (final String field : reportFields) {
			final Boolean passed = stepReport.get(field);
			final String status = passed == null ? "NOT_RUN" : (passed.booleanValue() ? "PASS" : "FAIL");
			builder.append("- ").append(field).append(": ").append(status).append('\n');
			if (Boolean.FALSE.equals(passed)) {
				builder.append("  reason: ").append(failureReasons.get(field)).append('\n');
			}
		}

		if (!legalUrls.isEmpty()) {
			builder.append("\nFinal URLs\n");
			builder.append("----------\n");
			for (final Map.Entry<String, String> urlEntry : legalUrls.entrySet()) {
				builder.append("- ").append(urlEntry.getKey()).append(": ").append(urlEntry.getValue()).append('\n');
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), builder.toString());
	}

	private ChromeOptions buildChromeOptions() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--window-size=1920,1080");

		final String headlessValue = getConfigValue("saleads.headless", "SALEADS_HEADLESS");
		if (headlessValue == null || Boolean.parseBoolean(headlessValue)) {
			options.addArguments("--headless=new");
		}

		return options;
	}

	private String getConfigValue(final String propertyName, final String envName) {
		final String fromProperty = System.getProperty(propertyName);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}
		final String fromEnv = System.getenv(envName);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}
		return null;
	}

	private String sanitizeFileName(final String value) {
		return value.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
	}

	private String toXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final String[] parts = text.split("'");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepExecutor {
		void run() throws Exception;
	}
}
