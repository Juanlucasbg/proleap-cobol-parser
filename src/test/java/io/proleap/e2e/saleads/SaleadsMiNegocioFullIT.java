package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Standalone E2E workflow for SaleADS "Mi Negocio". This class is intentionally
 * named with IT suffix so it does not run in the default parser test suite.
 */
public class SaleadsMiNegocioFullIT {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, String> finalReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> stepErrors = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;

	@Before
	public void setup() throws IOException {
		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDir = Files.createDirectories(Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));

		final String startUrl = envOrNull("SALEADS_START_URL");
		if (startUrl != null && !startUrl.isBlank()) {
			driver.get(startUrl);
		}

		appWindowHandle = driver.getWindowHandle();
		waitForUiLoad();

		if (driver.getCurrentUrl() == null || driver.getCurrentUrl().startsWith("data:")
				|| "about:blank".equals(driver.getCurrentUrl())) {
			fail("Browser did not start on a SaleADS page. Set SALEADS_START_URL or attach to an existing session "
					+ "with CHROME_DEBUGGER_ADDRESS.");
		}
	}

	@After
	public void teardown() {
		try {
			writeReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalDocument("Términos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalDocument("Política de Privacidad"));

		if (hasFailures()) {
			fail("SaleADS Mi Negocio workflow failed. See report in " + evidenceDir + " and details: " + stepErrors);
		}
	}

	private void stepLoginWithGoogle() {
		final Set<String> handlesBeforeLogin = driver.getWindowHandles();
		final WebElement loginButton = findClickableByAnyText("Sign in with Google", "Iniciar sesión con Google",
				"Continuar con Google", "Acceder con Google", "Google");
		clickAndWait(loginButton);
		selectGoogleAccountIfVisible(handlesBeforeLogin, envOrDefault("SALEADS_GOOGLE_ACCOUNT_EMAIL", DEFAULT_GOOGLE_ACCOUNT));

		assertVisibleTextAny("Negocio", "Mi Negocio", "Dashboard", "Panel");
		assertVisibleText("Negocio");
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		assertInputExistsForNombreNegocio();
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final WebElement nombreInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or @aria-label='Nombre del Negocio' "
						+ "or ancestor::*[.//label[contains(normalize-space(),'Nombre del Negocio')]]//input]")));
		nombreInput.click();
		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatización");
		waitForUiLoad();
		clickByVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() {
		expandMiNegocioIfCollapsed();
		clickByVisibleText("Administrar Negocios");
		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		takeScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("Información General");
		assertVisibleEmail();
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
		assertSectionHasAtLeastTexts("Información General", 2);
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertSectionHasAtLeastTexts("Tus Negocios", 1);
	}

	private void stepValidateLegalDocument(final String linkText) {
		final String appHandleBeforeClick = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForUiLoad();
		switchToPotentiallyNewTab(handlesBefore, appHandleBeforeClick);

		assertVisibleText(linkText);
		assertLegalContentVisible();
		takeScreenshot(linkText.equals("Términos y Condiciones") ? "05-terminos-condiciones" : "06-politica-privacidad");
		legalUrls.put(linkText, driver.getCurrentUrl());

		returnToApplicationTab(appHandleBeforeClick);
		assertVisibleText("Sección Legal");
	}

	private void runStep(final String label, final StepAction action) {
		try {
			action.run();
			finalReport.put(label, "PASS");
		} catch (final Exception ex) {
			finalReport.put(label, "FAIL");
			stepErrors.add(label + ": " + ex.getMessage());
			takeScreenshot("fail-" + sanitizeForFile(label));
		}
	}

	private void clickByVisibleText(final String text) {
		clickAndWait(findClickableByAnyText(text));
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiLoad();
	}

	private void waitForUiLoad() {
		try {
			Thread.sleep(500);
		} catch (final InterruptedException ie) {
			Thread.currentThread().interrupt();
		}

		try {
			wait.until(d -> "complete"
					.equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final TimeoutException ignored) {
			// Keep the test resilient when pages update asynchronously.
		}
	}

	private void selectGoogleAccountIfVisible(final Set<String> handlesBeforeLogin, final String email) {
		try {
			switchToPotentiallyNewTab(handlesBeforeLogin, appWindowHandle);
		} catch (final Exception ignored) {
			// Login can happen in the same tab without opening a separate chooser.
		}

		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);
		try {
			final WebElement accountOption = shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
					"//*[contains(normalize-space(),'" + email + "') or contains(normalize-space(),'Choose an account') "
							+ "or contains(normalize-space(),'Elige una cuenta')]")));
			if (!accountOption.getText().contains(email)) {
				final WebElement emailOption = findClickableByAnyText(email);
				clickAndWait(emailOption);
			} else {
				clickAndWait(accountOption);
			}
		} catch (final TimeoutException ignored) {
			// Account picker is optional when session is already authenticated.
		}

		try {
			driver.switchTo().window(appWindowHandle);
		} catch (final NoSuchElementException ignored) {
			// If the original handle no longer exists, stay on the current one.
		}
		waitForUiLoad();
	}

	private void switchToPotentiallyNewTab(final Set<String> previousHandles, final String fallbackHandle) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));
		try {
			shortWait.until(d -> d.getWindowHandles().size() >= previousHandles.size());
		} catch (final TimeoutException ignored) {
			// No tab count change detected.
		}

		final Set<String> currentHandles = driver.getWindowHandles();
		for (final String handle : currentHandles) {
			if (!previousHandles.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				return;
			}
		}

		driver.switchTo().window(fallbackHandle);
		waitForUiLoad();
	}

	private void returnToApplicationTab(final String appHandleBeforeClick) {
		final String currentHandle = driver.getWindowHandle();
		if (!currentHandle.equals(appHandleBeforeClick) && driver.getWindowHandles().contains(appHandleBeforeClick)) {
			driver.close();
			driver.switchTo().window(appHandleBeforeClick);
			waitForUiLoad();
			return;
		}

		driver.navigate().back();
		waitForUiLoad();
	}

	private void expandMiNegocioIfCollapsed() {
		final List<WebElement> administrarOptions = driver.findElements(
				By.xpath("//*[normalize-space()='Administrar Negocios' or contains(normalize-space(),'Administrar Negocios')]"));
		if (!administrarOptions.isEmpty() && administrarOptions.get(0).isDisplayed()) {
			return;
		}
		clickByVisibleText("Mi Negocio");
	}

	private WebElement findClickableByAnyText(final String... texts) {
		Exception lastFailure = null;
		for (final String text : texts) {
			try {
				return wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
						"//*[self::a or self::button or @role='button' or self::span or self::div]"
								+ "[normalize-space()='" + text + "' or contains(normalize-space(),'" + text + "')]")));
			} catch (final Exception ex) {
				lastFailure = ex;
			}
		}
		throw new IllegalStateException("Could not find clickable element for any text: " + String.join(", ", texts),
				lastFailure);
	}

	private void assertVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[normalize-space()='" + text + "' or contains(normalize-space(),'" + text + "')]")));
	}

	private void assertVisibleTextAny(final String... candidates) {
		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);
		Exception lastFailure = null;
		for (final String candidate : candidates) {
			try {
				shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
						"//*[normalize-space()='" + candidate + "' or contains(normalize-space(),'" + candidate + "')]")));
				return;
			} catch (final Exception ex) {
				lastFailure = ex;
			}
		}
		throw new IllegalStateException("None of the expected texts were visible: " + String.join(", ", candidates),
				lastFailure);
	}

	private void assertInputExistsForNombreNegocio() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or @aria-label='Nombre del Negocio' "
						+ "or ancestor::*[.//label[contains(normalize-space(),'Nombre del Negocio')]]//input]")));
	}

	private void assertVisibleEmail() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(),'@') and contains(normalize-space(),'.')]")));
	}

	private void assertSectionHasAtLeastTexts(final String sectionTitle, final int minimumTextItems) {
		final List<WebElement> values = driver.findElements(By.xpath("//section[.//*[contains(normalize-space(),'"
				+ sectionTitle + "')]]//*[string-length(normalize-space()) > 2]"));
		assertTrue("Expected section '" + sectionTitle + "' to contain visible text values", values.size() >= minimumTextItems);
	}

	private void assertLegalContentVisible() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//main//*[string-length(normalize-space()) > 40] | //article//*[string-length(normalize-space()) > 40] | //body//*[string-length(normalize-space()) > 40]")));
	}

	private void takeScreenshot(final String name) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		try {
			final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(evidenceDir.resolve(sanitizeForFile(name) + ".png"), screenshot);
		} catch (final Exception ex) {
			stepErrors.add("screenshot-" + name + ": " + ex.getMessage());
		}
	}

	private WebDriver createDriver() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1200");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		final String debuggerAddress = envOrNull("CHROME_DEBUGGER_ADDRESS");
		if (debuggerAddress != null && !debuggerAddress.isBlank()) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		}

		final String headless = envOrDefault("HEADLESS", "false");
		if ("true".equalsIgnoreCase(headless)) {
			options.addArguments("--headless=new");
		}

		return new ChromeDriver(options);
	}

	private void writeReport() {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test report").append(System.lineSeparator());
		report.append("Evidence directory: ").append(evidenceDir).append(System.lineSeparator()).append(System.lineSeparator());
		report.append("Step results:").append(System.lineSeparator());

		final String[] requiredFields = new String[] { "Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Términos y Condiciones", "Política de Privacidad" };
		for (final String field : requiredFields) {
			report.append("- ").append(field).append(": ").append(finalReport.getOrDefault(field, "NOT_EXECUTED"))
					.append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			report.append(System.lineSeparator()).append("Captured legal URLs:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		if (!stepErrors.isEmpty()) {
			report.append(System.lineSeparator()).append("Errors:").append(System.lineSeparator());
			for (final String error : stepErrors) {
				report.append("- ").append(error).append(System.lineSeparator());
			}
		}

		try {
			Files.writeString(evidenceDir.resolve("final-report.txt"), report.toString());
		} catch (final IOException ioException) {
			throw new RuntimeException("Could not write final report", ioException);
		}

		System.out.println(report);
	}

	private boolean hasFailures() {
		return finalReport.values().stream().anyMatch("FAIL"::equalsIgnoreCase);
	}

	private String sanitizeForFile(final String text) {
		return text.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}

	private String envOrDefault(final String key, final String defaultValue) {
		final String value = envOrNull(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private String envOrNull(final String key) {
		return System.getenv(key);
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
