package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
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

/**
 * Full Mi Negocio module workflow for SaleADS.ai.
 *
 * <p>
 * This test is intentionally text-driven and URL-agnostic so it can run on dev/staging/prod.
 * It should be enabled explicitly using:
 * </p>
 *
 * <pre>
 * mvn -Dsaleads.e2e.enabled=true -Dtest=SaleadsMiNegocioWorkflowTest test
 * </pre>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(20);
	private static final Duration QUICK_WAIT = Duration.ofSeconds(5);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, Boolean> report = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue("SaleADS E2E test is disabled. Set -Dsaleads.e2e.enabled=true to run.", enabled);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1000");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-gpu");

		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "false"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		evidenceDir = Path.of("target", "saleads-evidence",
				"saleads-mi-negocio-" + LocalDateTime.now().format(TS_FORMAT));
		Files.createDirectories(evidenceDir);

		final String startUrl = System.getProperty("saleads.start.url", "").trim();
		if (!startUrl.isEmpty()) {
			driver.get(startUrl);
		}
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		writeFinalReportFile();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		stepLoginWithGoogle();
		stepOpenMiNegocioMenu();
		stepValidateAgregarNegocioModal();
		stepOpenAdministrarNegocios();
		stepValidateInformacionGeneral();
		stepValidateDetallesCuenta();
		stepValidateTusNegocios();
		stepValidateTermsAndConditions();
		stepValidatePrivacyPolicy();

		logFinalReport();
		assertTrue("One or more workflow validations failed. See final report in logs and evidence directory.",
				report.values().stream().allMatch(Boolean::booleanValue));
	}

	private void stepLoginWithGoogle() {
		final String field = "Login";
		try {
			clickFirstMatching("Iniciar sesión con Google", "Sign in with Google", "Continuar con Google", "Google");
			waitForUiToLoad();

			selectGoogleAccountIfShown();

			final boolean mainInterfaceVisible = anyTextVisible("Negocio", "Dashboard", "Inicio", "Mi Negocio");
			final boolean sidebarVisible = isSidebarVisible();

			captureScreenshot("01-dashboard-loaded");
			record(field, mainInterfaceVisible && sidebarVisible);
		} catch (Exception ex) {
			record(field, false);
		}
	}

	private void stepOpenMiNegocioMenu() {
		final String field = "Mi Negocio menu";
		try {
			ensureSidebar();
			clickByVisibleText("Mi Negocio", "Negocio");
			waitForUiToLoad();

			final boolean submenuExpanded = anyTextVisible("Agregar Negocio", "Administrar Negocios");
			final boolean agregarVisible = textVisible("Agregar Negocio");
			final boolean administrarVisible = textVisible("Administrar Negocios");

			captureScreenshot("02-mi-negocio-menu-expanded");
			record(field, submenuExpanded && agregarVisible && administrarVisible);
		} catch (Exception ex) {
			record(field, false);
		}
	}

	private void stepValidateAgregarNegocioModal() {
		final String field = "Agregar Negocio modal";
		try {
			clickByVisibleText("Agregar Negocio");
			waitForUiToLoad();

			final boolean titleOk = anyTextVisible("Crear Nuevo Negocio");
			final boolean inputOk = anyTextVisible("Nombre del Negocio");
			final boolean quotaOk = anyTextVisible("Tienes 2 de 3 negocios");
			final boolean buttonsOk = textVisible("Cancelar") && textVisible("Crear Negocio");

			captureScreenshot("03-agregar-negocio-modal");
			optionalTypeBusinessNameAndCancel();
			record(field, titleOk && inputOk && quotaOk && buttonsOk);
		} catch (Exception ex) {
			record(field, false);
		}
	}

	private void stepOpenAdministrarNegocios() {
		final String field = "Administrar Negocios view";
		try {
			expandMiNegocioMenuIfCollapsed();
			clickByVisibleText("Administrar Negocios");
			waitForUiToLoad();

			final boolean infoGeneral = textVisible("Información General");
			final boolean detallesCuenta = textVisible("Detalles de la Cuenta");
			final boolean tusNegocios = textVisible("Tus Negocios");
			final boolean seccionLegal = anyTextVisible("Sección Legal", "Términos y Condiciones", "Política de Privacidad");

			captureFullPageScreenshot("04-administrar-negocios-account-page-full");
			record(field, infoGeneral && detallesCuenta && tusNegocios && seccionLegal);
		} catch (Exception ex) {
			record(field, false);
		}
	}

	private void stepValidateInformacionGeneral() {
		final String field = "Información General";
		try {
			final boolean userNameVisible = hasLikelyUserNameNearSection("Información General");
			final boolean userEmailVisible = pageSourceContains("@");
			final boolean planVisible = anyTextVisible("BUSINESS PLAN");
			final boolean changePlanVisible = textVisible("Cambiar Plan");

			record(field, userNameVisible && userEmailVisible && planVisible && changePlanVisible);
		} catch (Exception ex) {
			record(field, false);
		}
	}

	private void stepValidateDetallesCuenta() {
		final String field = "Detalles de la Cuenta";
		try {
			final boolean cuentaCreadaVisible = anyTextVisible("Cuenta creada");
			final boolean estadoActivoVisible = anyTextVisible("Estado activo");
			final boolean idiomaSeleccionadoVisible = anyTextVisible("Idioma seleccionado");

			record(field, cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible);
		} catch (Exception ex) {
			record(field, false);
		}
	}

	private void stepValidateTusNegocios() {
		final String field = "Tus Negocios";
		try {
			final boolean businessListVisible = sectionContainsAnyText("Tus Negocios", "Negocio", "negocio");
			final boolean addButtonVisible = textVisible("Agregar Negocio");
			final boolean quotaVisible = anyTextVisible("Tienes 2 de 3 negocios");

			record(field, businessListVisible && addButtonVisible && quotaVisible);
		} catch (Exception ex) {
			record(field, false);
		}
	}

	private void stepValidateTermsAndConditions() {
		final String field = "Términos y Condiciones";
		try {
			final String appWindow = driver.getWindowHandle();
			final int oldWindowCount = driver.getWindowHandles().size();

			clickByVisibleText("Términos y Condiciones");
			waitForUiToLoad();

			final boolean valid = openAndValidateLegalDestination(oldWindowCount, appWindow, "Términos y Condiciones",
					"08-terminos");
			record(field, valid);
		} catch (Exception ex) {
			record(field, false);
		}
	}

	private void stepValidatePrivacyPolicy() {
		final String field = "Política de Privacidad";
		try {
			final String appWindow = driver.getWindowHandle();
			final int oldWindowCount = driver.getWindowHandles().size();

			clickByVisibleText("Política de Privacidad");
			waitForUiToLoad();

			final boolean valid = openAndValidateLegalDestination(oldWindowCount, appWindow, "Política de Privacidad",
					"09-privacidad");
			record(field, valid);
		} catch (Exception ex) {
			record(field, false);
		}
	}

	private boolean openAndValidateLegalDestination(final int oldWindowCount, final String appWindow,
			final String heading, final String screenshotName) throws IOException {
		final boolean tabOpened = waitForWindowCountAtLeast(oldWindowCount + 1, QUICK_WAIT);
		if (tabOpened) {
			switchToNewestWindow();
		}

		waitForUiToLoad();
		final boolean headingVisible = anyTextVisible(heading);
		final boolean legalBodyVisible = hasLegalBodyContent();
		final String currentUrl = driver.getCurrentUrl();

		captureScreenshot(screenshotName + "-page");
		writeUrlFile(screenshotName + "-url.txt", currentUrl);

		if (tabOpened) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
		return headingVisible && legalBodyVisible;
	}

	private void optionalTypeBusinessNameAndCancel() {
		final List<WebElement> inputCandidates = driver.findElements(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio') "
						+ "or @name='businessName' or @id='businessName']"));
		if (!inputCandidates.isEmpty()) {
			final WebElement input = inputCandidates.get(0);
			wait.until(ExpectedConditions.visibilityOf(input));
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
		}
		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void selectGoogleAccountIfShown() {
		final Optional<WebElement> accountOption = findByVisibleTextNoThrow(GOOGLE_ACCOUNT_EMAIL, QUICK_WAIT);
		if (accountOption.isPresent()) {
			accountOption.get().click();
			waitForUiToLoad();
		}
	}

	private void ensureSidebar() {
		if (!isSidebarVisible()) {
			waitForUiToLoad();
		}
	}

	private void expandMiNegocioMenuIfCollapsed() {
		if (!textVisible("Administrar Negocios") || !textVisible("Agregar Negocio")) {
			clickByVisibleText("Mi Negocio", "Negocio");
			waitForUiToLoad();
		}
	}

	private boolean isSidebarVisible() {
		final List<WebElement> sidebar = driver.findElements(By.xpath(
				"//aside[not(contains(@style,'display: none'))] | //nav[.//*[contains(normalize-space(.),'Negocio')]]"));
		return !sidebar.isEmpty() && sidebar.stream().anyMatch(WebElement::isDisplayed);
	}

	private void clickFirstMatching(final String... labels) {
		RuntimeException lastException = null;
		for (final String label : labels) {
			try {
				clickByVisibleText(label);
				return;
			} catch (RuntimeException ex) {
				lastException = ex;
			}
		}
		throw (lastException != null) ? lastException : new IllegalStateException("No matching element found to click.");
	}

	private void clickByVisibleText(final String... labels) {
		RuntimeException lastException = null;
		for (final String label : labels) {
			try {
				final WebElement element = findByVisibleText(label, DEFAULT_WAIT);
				scrollIntoView(element);
				wait.until(ExpectedConditions.elementToBeClickable(element));
				element.click();
				waitForUiToLoad();
				return;
			} catch (RuntimeException ex) {
				lastException = ex;
			}
		}
		throw (lastException != null) ? lastException
				: new IllegalStateException("Could not click any element with labels: " + String.join(", ", labels));
	}

	private WebElement findByVisibleText(final String text, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(driverInstance -> {
			final List<WebElement> candidates = driverInstance.findElements(By.xpath(
					"//*[self::button or self::a or @role='button' or self::span or self::div or self::p or self::h1 or self::h2 or self::h3]"
							+ "[contains(normalize-space(.),\"" + escapeXpath(text) + "\")]"));
			return candidates.stream().filter(WebElement::isDisplayed).findFirst().orElse(null);
		});
	}

	private Optional<WebElement> findByVisibleTextNoThrow(final String text, final Duration timeout) {
		try {
			return Optional.ofNullable(findByVisibleText(text, timeout));
		} catch (TimeoutException ex) {
			return Optional.empty();
		}
	}

	private boolean textVisible(final String text) {
		return findByVisibleTextNoThrow(text, QUICK_WAIT).isPresent();
	}

	private boolean anyTextVisible(final String... options) {
		for (final String option : options) {
			if (textVisible(option)) {
				return true;
			}
		}
		return false;
	}

	private void waitForUiToLoad() {
		wait.until((ExpectedCondition<Boolean>) driverInstance -> {
			final Object state = ((JavascriptExecutor) driverInstance).executeScript("return document.readyState");
			return "complete".equals(state);
		});
		sleep(700);
	}

	private boolean waitForWindowCountAtLeast(final int minimum, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(drv -> drv.getWindowHandles().size() >= minimum);
			return true;
		} catch (TimeoutException ex) {
			return false;
		}
	}

	private void switchToNewestWindow() {
		final String current = driver.getWindowHandle();
		for (final String handle : driver.getWindowHandles()) {
			if (!handle.equals(current)) {
				driver.switchTo().window(handle);
			}
		}
	}

	private boolean hasLegalBodyContent() {
		final List<WebElement> contentCandidates = driver.findElements(By.xpath(
				"//main//*[string-length(normalize-space(text())) > 80] | //article//*[string-length(normalize-space(text())) > 80] | //body//*[string-length(normalize-space(text())) > 120]"));
		return contentCandidates.stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean hasLikelyUserNameNearSection(final String sectionText) {
		if (!textVisible(sectionText)) {
			return false;
		}
		final List<WebElement> possibleNameFields = driver.findElements(By.xpath(
				"//*[contains(normalize-space(.),'" + escapeXpath(sectionText) + "')]/ancestor::*[1]//*[self::p or self::span or self::div][string-length(normalize-space(text())) > 3 and string-length(normalize-space(text())) < 60]"));
		return possibleNameFields.stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean sectionContainsAnyText(final String sectionName, final String... markers) {
		if (!textVisible(sectionName)) {
			return false;
		}
		for (final String marker : markers) {
			if (anyTextVisible(marker)) {
				return true;
			}
		}
		return false;
	}

	private boolean pageSourceContains(final String fragment) {
		return driver.getPageSource().contains(fragment);
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});",
				element);
		sleep(200);
	}

	private void captureScreenshot(final String name) throws IOException {
		final Path target = evidenceDir.resolve(name + ".png");
		final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(target, bytes);
	}

	private void captureFullPageScreenshot(final String name) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			final long fullWidth = ((Number) js.executeScript(
					"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);")).longValue();
			final long fullHeight = ((Number) js.executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);")).longValue();
			final int width = (int) Math.max(1200L, Math.min(fullWidth, 3000L));
			final int height = (int) Math.max(900L, Math.min(fullHeight, 6000L));
			driver.manage().window().setSize(new Dimension(width, height));
			sleep(400);
			captureScreenshot(name);
		} finally {
			driver.manage().window().setSize(originalSize);
			sleep(200);
		}
	}

	private void writeUrlFile(final String filename, final String url) throws IOException {
		Files.writeString(evidenceDir.resolve(filename), url + System.lineSeparator());
	}

	private void record(final String field, final boolean pass) {
		report.put(field, pass);
	}

	private void logFinalReport() {
		final StringBuilder sb = new StringBuilder();
		sb.append(System.lineSeparator());
		sb.append("==== SaleADS Mi Negocio Final Report ====").append(System.lineSeparator());
		report.forEach((k, v) -> sb.append(k).append(": ").append(v ? "PASS" : "FAIL").append(System.lineSeparator()));
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		sb.append("=========================================").append(System.lineSeparator());
		System.out.println(sb.toString());
	}

	private void writeFinalReportFile() {
		if (evidenceDir == null || report.isEmpty()) {
			return;
		}
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Final Report").append(System.lineSeparator());
		report.forEach((k, v) -> sb.append(k).append(": ").append(v ? "PASS" : "FAIL").append(System.lineSeparator()));
		sb.append("Captured at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		try {
			Files.writeString(evidenceDir.resolve("final-report.txt"), sb.toString());
		} catch (IOException ignored) {
			// Report is already logged to stdout; failing here should not mask test result.
		}
	}

	private String escapeXpath(final String value) {
		return value.replace("\"", "\\\"");
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}
}
