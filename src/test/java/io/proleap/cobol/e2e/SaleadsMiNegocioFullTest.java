package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.openqa.selenium.Keys;
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

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private int screenshotIndex = 1;

	@Before
	public void setUp() throws IOException {
		assumeTrue("Enable with SALEADS_E2E_ENABLED=true.", isEnabled(System.getenv("SALEADS_E2E_ENABLED")));

		evidenceDirectory = Paths.get("target", "evidence", "saleads_mi_negocio_full_test",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDirectory);

		final String debuggerAddress = getenvOrEmpty("SALEADS_CHROME_DEBUGGER_ADDRESS");
		final String startUrl = getenvOrEmpty("SALEADS_START_URL");
		final ChromeOptions options = new ChromeOptions();

		if (!debuggerAddress.isEmpty()) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		if (debuggerAddress.isEmpty()) {
			assumeTrue("Set SALEADS_START_URL when not attaching to an existing browser session.", !startUrl.isEmpty());
			driver.get(startUrl);
		}

		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", () -> validateLegalLink("Términos y Condiciones", "Términos y Condiciones"));
		executeStep("Política de Privacidad", () -> validateLegalLink("Política de Privacidad", "Política de Privacidad"));

		printFinalReport();
		assertFalse("Workflow failed:\n" + String.join("\n", failures), !failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		clickAnyText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiToLoad();

		clickByTextIfVisible(ACCOUNT_EMAIL, SHORT_TIMEOUT);
		waitForUiToLoad();

		waitForVisibleText("Negocio", DEFAULT_TIMEOUT);
		assertTrue("Left sidebar navigation should be visible.",
				!driver.findElements(By.xpath("//aside | //nav")).isEmpty());

		takeScreenshot("dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByTextIfVisible("Negocio", SHORT_TIMEOUT);
		waitForUiToLoad();
		clickAnyText("Mi Negocio");
		waitForUiToLoad();

		waitForVisibleText("Agregar Negocio", DEFAULT_TIMEOUT);
		waitForVisibleText("Administrar Negocios", DEFAULT_TIMEOUT);

		takeScreenshot("mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickAnyText("Agregar Negocio");
		waitForUiToLoad();

		waitForVisibleText("Crear Nuevo Negocio", DEFAULT_TIMEOUT);
		waitForVisibleText("Nombre del Negocio", DEFAULT_TIMEOUT);
		waitForVisibleText("Tienes 2 de 3 negocios", DEFAULT_TIMEOUT);
		waitForVisibleText("Cancelar", DEFAULT_TIMEOUT);
		waitForVisibleText("Crear Negocio", DEFAULT_TIMEOUT);

		final WebElement businessNameInput = findBusinessNameInput();
		businessNameInput.click();
		businessNameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "Negocio Prueba Automatización");

		takeScreenshot("agregar-negocio-modal");

		clickAnyText("Cancelar");
		waitForUiToLoad();
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byVisibleText("Crear Nuevo Negocio")));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (driver.findElements(byVisibleText("Administrar Negocios")).isEmpty()) {
			clickByTextIfVisible("Mi Negocio", SHORT_TIMEOUT);
			waitForUiToLoad();
		}

		clickAnyText("Administrar Negocios");
		waitForUiToLoad();

		waitForVisibleText("Información General", DEFAULT_TIMEOUT);
		waitForVisibleText("Detalles de la Cuenta", DEFAULT_TIMEOUT);
		waitForVisibleText("Tus Negocios", DEFAULT_TIMEOUT);
		waitForVisibleText("Sección Legal", DEFAULT_TIMEOUT);

		takeScreenshot("administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		waitForVisibleText("BUSINESS PLAN", DEFAULT_TIMEOUT);
		waitForVisibleText("Cambiar Plan", DEFAULT_TIMEOUT);

		final String pageText = driver.findElement(By.tagName("body")).getText();
		final Pattern emailPattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
		assertTrue("User email should be visible.", emailPattern.matcher(pageText).find());
		assertTrue("User name should be visible near account info.", pageText.contains("@") && pageText.split("\\s+").length > 10);
	}

	private void stepValidateDetallesDeLaCuenta() {
		waitForVisibleText("Cuenta creada", DEFAULT_TIMEOUT);
		waitForVisibleText("Estado activo", DEFAULT_TIMEOUT);
		waitForVisibleText("Idioma seleccionado", DEFAULT_TIMEOUT);
	}

	private void stepValidateTusNegocios() {
		waitForVisibleText("Tus Negocios", DEFAULT_TIMEOUT);
		waitForVisibleText("Agregar Negocio", DEFAULT_TIMEOUT);
		waitForVisibleText("Tienes 2 de 3 negocios", DEFAULT_TIMEOUT);
	}

	private void validateLegalLink(final String linkText, final String expectedHeading) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String originalUrl = driver.getCurrentUrl();

		clickAnyText(linkText);

		boolean openedNewTab = false;
		wait.until(d -> {
			if (d.getWindowHandles().size() > handlesBefore.size()) {
				return true;
			}
			return !d.getCurrentUrl().equals(originalUrl);
		});

		final Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > handlesBefore.size()) {
			for (final String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					openedNewTab = true;
					break;
				}
			}
		}

		waitForUiToLoad();
		waitForVisibleText(expectedHeading, DEFAULT_TIMEOUT);
		final String legalPageText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal content should be visible for " + linkText + ".", legalPageText.trim().length() > 120);

		takeScreenshot(slugify(expectedHeading) + "-page");
		legalUrls.put(expectedHeading, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private WebElement findBusinessNameInput() {
		final By byLabel = By.xpath(
				"//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder,'Nombre del Negocio')] | //input[@name='businessName']");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(byLabel));
	}

	private void executeStep(final String reportField, final StepAction stepAction) {
		try {
			stepAction.run();
			report.put(reportField, Boolean.TRUE);
		} catch (final Throwable throwable) {
			report.put(reportField, Boolean.FALSE);
			failures.add(reportField + ": " + throwable.getMessage());
			try {
				takeScreenshot("failure-" + slugify(reportField));
			} catch (final IOException ignored) {
				// Keep original failure details when screenshot capture also fails.
			}
		}
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete"
				.equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		sleep(600);
	}

	private void clickAnyText(final String... textOptions) {
		Exception lastException = null;

		for (final String textOption : textOptions) {
			try {
				final WebElement clickable = wait.until(ExpectedConditions.elementToBeClickable(byClickableText(textOption)));
				clickable.click();
				waitForUiToLoad();
				return;
			} catch (final Exception exception) {
				lastException = exception;
			}
		}

		throw new IllegalStateException("Could not click any of texts " + Arrays.toString(textOptions), lastException);
	}

	private boolean clickByTextIfVisible(final String text, final Duration timeout) {
		try {
			final WebElement clickable = new WebDriverWait(driver, timeout)
					.until(ExpectedConditions.elementToBeClickable(byClickableText(text)));
			clickable.click();
			waitForUiToLoad();
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void waitForVisibleText(final String text, final Duration timeout) {
		new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
	}

	private By byVisibleText(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath("//*[contains(normalize-space(.), " + literal + ")]");
	}

	private By byClickableText(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath(
				"(//button[contains(normalize-space(.), "
						+ literal
						+ ")] | //a[contains(normalize-space(.), "
						+ literal
						+ ")] | //*[@role='button' and contains(normalize-space(.), "
						+ literal
						+ ")] | //span[contains(normalize-space(.), "
						+ literal + ")]/ancestor::button[1] | //span[contains(normalize-space(.), " + literal
						+ ")]/ancestor::a[1])[1]");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private void takeScreenshot(final String label) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String filename = String.format("%02d-%s.png", screenshotIndex++, slugify(label));
		final Path targetPath = evidenceDirectory.resolve(filename);
		Files.copy(screenshot.toPath(), targetPath);
	}

	private String slugify(final String text) {
		return text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private void printFinalReport() {
		System.out.println("saleads_mi_negocio_full_test report");
		for (final String field : REPORT_FIELDS) {
			final boolean result = report.getOrDefault(field, Boolean.FALSE);
			System.out.println("- " + field + ": " + (result ? "PASS" : "FAIL"));
		}

		if (!legalUrls.isEmpty()) {
			System.out.println("Captured URLs:");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println("  * " + entry.getKey() + ": " + entry.getValue());
			}
		}

		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean isEnabled(final String value) {
		return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
	}

	private String getenvOrEmpty(final String key) {
		final String value = System.getenv(key);
		return value == null ? "" : value.trim();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
