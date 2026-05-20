package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, String> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private final Map<String, String> checkpointScreenshots = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;
	private Path screenshotsDir;
	private int screenshotCounter;

	@Test
	public void runSaleadsMiNegocioWorkflow() throws Exception {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		assumeTrue("Enable this E2E test with -Dsaleads.e2e.enabled=true", enabled);

		final String startUrl = System.getProperty("saleads.e2e.startUrl", "").trim();
		assumeTrue("Provide the current environment login page with -Dsaleads.e2e.startUrl=<url>", !startUrl.isEmpty());

		startDriver();
		driver.get(startUrl);
		waitForUiToLoad();

		try {
			runStep("Login", this::stepLoginWithGoogle);
			runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			runStep("Información General", this::stepValidateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			runStep("Tus Negocios", this::stepValidateTusNegocios);
			runStep("Términos y Condiciones", () -> stepValidateLegalDocument("Términos y Condiciones"));
			runStep("Política de Privacidad", () -> stepValidateLegalDocument("Política de Privacidad"));
		} finally {
			writeFinalReport();
		}

		final List<String> failedSteps = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			if ("FAIL".equals(stepResults.get(field))) {
				failedSteps.add(field);
			}
		}

		assertTrue("Workflow failures: " + failedSteps + ". See final-report.md under " + artifactsDir, failedSteps.isEmpty());
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	private void startDriver() throws IOException {
		final LocalDateTime now = LocalDateTime.now();
		final String runId = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

		artifactsDir = Path.of("target", "saleads-e2e", TEST_NAME, runId);
		screenshotsDir = artifactsDir.resolve("screenshots");
		Files.createDirectories(screenshotsDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--lang=es-ES");

		if (Boolean.parseBoolean(System.getProperty("saleads.e2e.headless", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		final long timeoutSeconds = Long.parseLong(System.getProperty("saleads.e2e.timeoutSeconds", "30"));
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			stepResults.put(reportField, "PASS");
		} catch (final Throwable throwable) {
			stepResults.put(reportField, "FAIL");
			stepErrors.put(reportField, rootCauseMessage(throwable));
			safeCapture("fail_" + reportField);
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		clickGoogleLoginButton();
		selectGoogleAccountIfVisible(handlesBeforeClick, GOOGLE_ACCOUNT);
		switchToWindowContainingAppShell();

		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside | //nav")));
		waitForVisibleText("Negocio");
		captureCheckpoint("dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickIfPresent("Negocio");
		clickByVisibleText("Mi Negocio");

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		captureCheckpoint("mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		waitForVisibleText("Crear Nuevo Negocio");
		assertTrue("Expected input field 'Nombre del Negocio'",
				isElementVisible(By.xpath(
						"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio']"
								+ " | //label[normalize-space()='Nombre del Negocio']/following::input[1]")));
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");
		captureCheckpoint("agregar_negocio_modal");

		typeIfPresent("Nombre del Negocio", "Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']")));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		clickIfPresent("Mi Negocio");
		clickByVisibleText("Administrar Negocios");

		waitForVisibleText("Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal");
		captureCheckpoint("administrar_negocios_view");
	}

	private void stepValidateInformacionGeneral() {
		waitForVisibleText("Información General");
		waitForVisibleText("BUSINESS PLAN");
		waitForVisibleText("Cambiar Plan");

		final String pageText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected a visible user email", Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}").matcher(pageText).find());
		assertTrue("Expected a visible user name",
				Pattern.compile("(?m)^[A-Za-zÁÉÍÓÚÑáéíóúñ][A-Za-zÁÉÍÓÚÑáéíóúñ\\s]{2,}$").matcher(pageText).find());
	}

	private void stepValidateDetallesCuenta() {
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");

		final boolean listVisible = isElementVisible(
				By.xpath("//*[normalize-space()='Tus Negocios']/ancestor::*[self::section or self::div][1]//li"
						+ " | //*[normalize-space()='Tus Negocios']/ancestor::*[self::section or self::div][1]//table//tr"
						+ " | //*[normalize-space()='Tus Negocios']/ancestor::*[self::section or self::div][1]//*[contains(@class,'card')]"));
		assertTrue("Expected visible business list", listVisible);
	}

	private void stepValidateLegalDocument(final String linkText) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickLegalLink(linkText);
		final String newTabHandle = waitForNewTab(handlesBeforeClick);
		if (newTabHandle != null) {
			driver.switchTo().window(newTabHandle);
			waitForUiToLoad();
		}

		waitForVisibleText(linkText);
		final String legalText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected legal content text", legalText != null && legalText.trim().length() > 200);
		captureCheckpoint("legal_" + linkText);

		legalUrls.put(linkText, driver.getCurrentUrl());

		if (newTabHandle != null) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void clickGoogleLoginButton() {
		final String[] candidates = { "Sign in with Google", "Iniciar sesión con Google", "Continuar con Google" };
		for (final String text : candidates) {
			if (clickIfPresent(text)) {
				return;
			}
		}

		final By containsGoogle = By.xpath(
				"//button[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'google')]"
						+ " | //a[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'google')]");
		wait.until(ExpectedConditions.elementToBeClickable(containsGoogle)).click();
		waitForUiToLoad();
	}

	private void selectGoogleAccountIfVisible(final Set<String> handlesBeforeLoginClick, final String accountEmail) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(25).toMillis();
		while (System.currentTimeMillis() < deadline) {
			final Set<String> handlesNow = driver.getWindowHandles();
			for (final String handle : handlesNow) {
				driver.switchTo().window(handle);
				final By emailLocator = By.xpath("//*[normalize-space()=" + toXpathLiteral(accountEmail) + "]");
				if (isElementVisible(emailLocator)) {
					wait.until(ExpectedConditions.elementToBeClickable(emailLocator)).click();
					waitForUiToLoad();
					return;
				}
			}

			if (!handlesNow.equals(handlesBeforeLoginClick)) {
				waitFor(400);
			} else {
				waitFor(250);
			}
		}
	}

	private void switchToWindowContainingAppShell() {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(90).toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (isElementVisible(By.xpath("//aside | //nav")) && isElementVisible(By.xpath("//*[normalize-space()='Negocio']"))) {
					waitForUiToLoad();
					return;
				}
			}
			waitFor(500);
		}

		throw new TimeoutException("Main application interface with left sidebar was not detected after Google login.");
	}

	private void clickLegalLink(final String linkText) {
		final WebElement legalSectionTitle = waitForVisibleText("Sección Legal");
		final List<WebElement> scopedMatches = legalSectionTitle
				.findElements(By.xpath("./ancestor::*[self::section or self::div][1]//*[self::a or self::button][normalize-space()="
						+ toXpathLiteral(linkText) + "]"));
		if (!scopedMatches.isEmpty()) {
			wait.until(ExpectedConditions.elementToBeClickable(scopedMatches.get(0))).click();
			waitForUiToLoad();
			return;
		}

		clickByVisibleText(linkText);
	}

	private String waitForNewTab(final Set<String> handlesBeforeClick) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
		while (System.currentTimeMillis() < deadline) {
			final Set<String> handlesNow = driver.getWindowHandles();
			if (handlesNow.size() > handlesBeforeClick.size()) {
				for (final String handle : handlesNow) {
					if (!handlesBeforeClick.contains(handle)) {
						return handle;
					}
				}
			}
			waitFor(250);
		}
		return null;
	}

	private void clickByVisibleText(final String text) {
		final String literal = toXpathLiteral(text);
		final By locator = By.xpath("//button[normalize-space()=" + literal + " or .//*[normalize-space()=" + literal + "]]"
				+ " | //a[normalize-space()=" + literal + " or .//*[normalize-space()=" + literal + "]]"
				+ " | //*[@role='button' and normalize-space()=" + literal + "]");
		wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
		waitForUiToLoad();
	}

	private boolean clickIfPresent(final String text) {
		final String literal = toXpathLiteral(text);
		final By locator = By.xpath("//button[normalize-space()=" + literal + " or .//*[normalize-space()=" + literal + "]]"
				+ " | //a[normalize-space()=" + literal + " or .//*[normalize-space()=" + literal + "]]"
				+ " | //*[@role='button' and normalize-space()=" + literal + "]");
		try {
			final WebElement element = new WebDriverWait(driver, Duration.ofSeconds(3))
					.until(ExpectedConditions.elementToBeClickable(locator));
			element.click();
			waitForUiToLoad();
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void typeIfPresent(final String placeholder, final String value) {
		final By locator = By.xpath("//input[@placeholder=" + toXpathLiteral(placeholder) + "]");
		if (isElementVisible(locator)) {
			final WebElement input = driver.findElement(locator);
			input.clear();
			input.sendKeys(value);
		}
	}

	private WebElement waitForVisibleText(final String text) {
		final By locator = By.xpath("//*[normalize-space()=" + toXpathLiteral(text) + "]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private boolean isElementVisible(final By by) {
		try {
			final List<WebElement> elements = driver.findElements(by);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final NoSuchElementException noSuchElementException) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		waitFor(250);
	}

	private void waitFor(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting", interruptedException);
		}
	}

	private void captureCheckpoint(final String checkpointName) throws IOException {
		final String screenshotFile = screenshot(++screenshotCounter + "_" + checkpointName);
		checkpointScreenshots.put(checkpointName, screenshotFile);
	}

	private void safeCapture(final String checkpointName) {
		try {
			captureCheckpoint(checkpointName);
		} catch (final IOException ignored) {
			// best effort only
		}
	}

	private String screenshot(final String baseName) throws IOException {
		final String safeName = baseName.replaceAll("[^a-zA-Z0-9_-]", "_");
		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = screenshotsDir.resolve(safeName + ".png");
		Files.copy(screenshotFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		return destination.toString();
	}

	private void writeFinalReport() throws IOException {
		Files.createDirectories(artifactsDir);
		final StringBuilder report = new StringBuilder();
		report.append("# ").append(TEST_NAME).append(System.lineSeparator()).append(System.lineSeparator());
		report.append("## Final Report").append(System.lineSeparator()).append(System.lineSeparator());
		report.append("| Validation | Result |").append(System.lineSeparator());
		report.append("|---|---|").append(System.lineSeparator());
		for (final String field : REPORT_FIELDS) {
			report.append("| ").append(field).append(" | ")
					.append(stepResults.getOrDefault(field, "FAIL"))
					.append(" |")
					.append(System.lineSeparator());
		}

		if (!stepErrors.isEmpty()) {
			report.append(System.lineSeparator()).append("## Errors").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : stepErrors.entrySet()) {
				report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		report.append(System.lineSeparator()).append("## Legal URLs").append(System.lineSeparator());
		report.append("- Términos y Condiciones: ").append(legalUrls.getOrDefault("Términos y Condiciones", "N/A"))
				.append(System.lineSeparator());
		report.append("- Política de Privacidad: ").append(legalUrls.getOrDefault("Política de Privacidad", "N/A"))
				.append(System.lineSeparator());

		report.append(System.lineSeparator()).append("## Screenshots").append(System.lineSeparator());
		for (final Map.Entry<String, String> entry : checkpointScreenshots.entrySet()) {
			report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}

		Files.writeString(artifactsDir.resolve("final-report.md"), report.toString(), StandardCharsets.UTF_8);
	}

	private String rootCauseMessage(final Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getClass().getSimpleName() + ": " + current.getMessage();
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char ch = chars[i];
			if (i > 0) {
				result.append(",");
			}
			if (ch == '\'') {
				result.append("\"'\"");
			} else if (ch == '"') {
				result.append("'\"'");
			} else {
				result.append("'").append(ch).append("'");
			}
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
