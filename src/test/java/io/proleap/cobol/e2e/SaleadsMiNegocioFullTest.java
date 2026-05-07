package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Assume;
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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter EVIDENCE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;

	@Test
	public void saleadsMiNegocioWorkflow() throws Exception {
		initializeReport();
		evidenceDirectory = Files
				.createDirectories(Paths.get("target", "saleads-evidence", EVIDENCE_TIME_FORMAT.format(LocalDateTime.now())));

		final String saleadsUrl = firstNonBlank(System.getProperty("saleads.url"), System.getenv("SALEADS_URL"));
		Assume.assumeTrue(
				"Set -Dsaleads.url or SALEADS_URL to the current environment login page (dev/staging/prod).",
				saleadsUrl != null && !saleadsUrl.isBlank());

		driver = createDriver();
		wait = new WebDriverWait(driver, readTimeout());

		try {
			driver.get(saleadsUrl);
			waitForUiLoad();

			runStep("Login", this::stepLoginWithGoogle);
			runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			runStep("Información General", this::stepValidateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			runStep("Tus Negocios", this::stepValidateTusNegocios);
			runStep("Términos y Condiciones",
					() -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "terminos-y-condiciones"));
			runStep("Política de Privacidad",
					() -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "politica-de-privacidad"));
		} finally {
			writeFinalReport();
			printFinalReport();
			if (driver != null) {
				driver.quit();
			}
		}

		assertAllStepsPassed();
	}

	private String stepLoginWithGoogle() throws Exception {
		final String accountEmail = firstNonBlank(System.getProperty("saleads.google.account"),
				System.getenv("SALEADS_GOOGLE_ACCOUNT"), "juanlucasbarbiergarzon@gmail.com");

		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBeforeClick = driver.getWindowHandles();

		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
				"Continuar con Google", "Google");
		waitForUiLoad();

		final String popupHandle = waitForNewWindowHandle(windowsBeforeClick, Duration.ofSeconds(8));
		if (popupHandle != null) {
			driver.switchTo().window(popupHandle);
			waitForUiLoad();
		}

		if (isGoogleAccountsPage()) {
			clickByVisibleText(accountEmail);
			waitForUiLoad();
		}

		if (driver.getWindowHandles().contains(appWindow)) {
			driver.switchTo().window(appWindow);
		}

		waitForTextVisible("Negocio", Duration.ofSeconds(60));
		waitForTextVisible("Mi Negocio", Duration.ofSeconds(30));
		takeCheckpoint("01-dashboard-loaded", true);
		return "Dashboard loaded and sidebar visible.";
	}

	private String stepOpenMiNegocioMenu() throws Exception {
		waitForTextVisible("Negocio", readTimeout());
		clickByVisibleText("Negocio");
		waitForUiLoad();
		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		waitForTextVisible("Agregar Negocio", readTimeout());
		waitForTextVisible("Administrar Negocios", readTimeout());
		takeCheckpoint("02-mi-negocio-expanded", false);
		return "Mi Negocio expanded with expected submenu options.";
	}

	private String stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForUiLoad();

		waitForTextVisible("Crear Nuevo Negocio", readTimeout());
		waitForElementVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or @aria-label='Nombre del Negocio' or @name='nombreDelNegocio' or @name='businessName']"),
				readTimeout());
		waitForTextVisible("Tienes 2 de 3 negocios", readTimeout());
		waitForTextVisible("Cancelar", readTimeout());
		waitForTextVisible("Crear Negocio", readTimeout());
		takeCheckpoint("03-agregar-negocio-modal", false);

		final WebElement nombreNegocioInput = waitForElementVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or @aria-label='Nombre del Negocio' or @name='nombreDelNegocio' or @name='businessName']"),
				readTimeout());
		nombreNegocioInput.click();
		nombreNegocioInput.sendKeys("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		waitForUiLoad();
		return "Modal validated and closed with Cancelar.";
	}

	private String stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		waitForTextVisible("Información General", readTimeout());
		waitForTextVisible("Detalles de la Cuenta", readTimeout());
		waitForTextVisible("Tus Negocios", readTimeout());
		waitForTextVisible("Sección Legal", readTimeout());
		takeCheckpoint("04-administrar-negocios", true);
		return "Account page sections are visible.";
	}

	private String stepValidateInformacionGeneral() throws Exception {
		waitForTextVisible("Información General", readTimeout());

		final String infoSectionText = findClosestSectionText("Información General");
		assertTrue("Expected an email to be visible in Información General.",
				EMAIL_PATTERN.matcher(infoSectionText).find() || isTextVisible("juanlucasbarbiergarzon@gmail.com"));
		waitForTextVisible("BUSINESS PLAN", readTimeout());
		waitForTextVisible("Cambiar Plan", readTimeout());

		final String expectedName = firstNonBlank(System.getProperty("saleads.expected.name"),
				System.getenv("SALEADS_EXPECTED_NAME"));
		if (expectedName != null && !expectedName.isBlank()) {
			waitForTextVisible(expectedName, readTimeout());
		} else {
			assertTrue("User name was not clearly visible in Información General.",
					hasLikelyNameValue(infoSectionText, "BUSINESS PLAN", "Cambiar Plan", "Información General"));
		}

		return "User name, email, plan and CTA validated.";
	}

	private String stepValidateDetallesCuenta() throws Exception {
		waitForTextVisible("Detalles de la Cuenta", readTimeout());
		waitForTextVisible("Cuenta creada", readTimeout());
		waitForTextVisible("Estado activo", readTimeout());
		waitForTextVisible("Idioma seleccionado", readTimeout());
		return "Detalles de la Cuenta labels validated.";
	}

	private String stepValidateTusNegocios() throws Exception {
		waitForTextVisible("Tus Negocios", readTimeout());
		waitForTextVisible("Agregar Negocio", readTimeout());
		waitForTextVisible("Tienes 2 de 3 negocios", readTimeout());

		final String negociosSectionText = findClosestSectionText("Tus Negocios");
		assertTrue("Business list is not visible in Tus Negocios.",
				hasLikelyNameValue(negociosSectionText, "Tus Negocios", "Agregar Negocio", "Tienes 2 de 3 negocios"));
		return "Tus Negocios list and quota validated.";
	}

	private String stepValidateLegalLink(final String linkText, final String headingText, final String evidenceName)
			throws Exception {
		waitForTextVisible("Sección Legal", readTimeout());

		final String appWindow = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> windowsBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForUiLoad();

		final String popupHandle = waitForNewWindowHandle(windowsBeforeClick, Duration.ofSeconds(8));
		if (popupHandle != null) {
			driver.switchTo().window(popupHandle);
			waitForUiLoad();
		}

		waitForTextVisible(headingText, readTimeout());
		final String legalPageText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal content text is not visible for " + linkText + ".", legalPageText != null
				&& legalPageText.replace(headingText, "").trim().length() > 60);

		final String finalUrl = driver.getCurrentUrl();
		takeCheckpoint("05-" + evidenceName, true);

		if (popupHandle != null) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else if (!driver.getCurrentUrl().equals(originalUrl)) {
			driver.navigate().back();
			waitForUiLoad();
		}

		return "Validated legal page and captured URL: " + finalUrl;
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			final String details = action.run();
			stepResults.put(stepName, StepResult.pass(details));
		} catch (final Throwable error) {
			takeCheckpoint("failed-" + sanitizeFileName(stepName), false);
			stepResults.put(stepName, StepResult.fail(error.getMessage()));
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			if (!entry.getValue().pass) {
				failed.add(entry.getKey() + " -> " + entry.getValue().details);
			}
		}
		assertTrue("SaleADS Mi Negocio workflow failures:\n" + String.join("\n", failed), failed.isEmpty());
	}

	private void initializeReport() {
		for (final String reportField : REPORT_FIELDS) {
			stepResults.put(reportField, StepResult.fail("Not executed."));
		}
	}

	private WebDriver createDriver() {
		final String browser = firstNonBlank(System.getProperty("saleads.browser"), System.getenv("SALEADS_BROWSER"),
				"chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(
				firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "true"));

		if ("firefox".equals(browser)) {
			final FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			return new FirefoxDriver(options);
		}

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}
		return new ChromeDriver(options);
	}

	private Duration readTimeout() {
		final long timeoutSeconds = Long.parseLong(firstNonBlank(System.getProperty("saleads.timeout.seconds"),
				System.getenv("SALEADS_TIMEOUT_SECONDS"), "25"));
		return Duration.ofSeconds(timeoutSeconds);
	}

	private void waitForUiLoad() {
		try {
			wait.until(driver -> {
				final Object state = ((JavascriptExecutor) driver).executeScript("return document.readyState");
				return state != null && ("interactive".equals(state.toString()) || "complete".equals(state.toString()));
			});
			Thread.sleep(500L);
		} catch (final Exception ignored) {
			// Ignore transient readiness errors and keep flow resilient.
		}
	}

	private void waitForTextVisible(final String text, final Duration timeout) {
		final By by = By.xpath("//*[normalize-space()=" + escapeXPath(text) + " or contains(normalize-space(),"
				+ escapeXPath(text) + ")]");
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		localWait.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private WebElement waitForElementVisible(final By by, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		return localWait.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private void clickByVisibleText(final String... options) {
		Exception lastError = null;
		for (final String option : options) {
			try {
				final WebElement element = findClickableByVisibleText(option, readTimeout());
				clickElement(element);
				waitForUiLoad();
				return;
			} catch (final Exception e) {
				lastError = e;
			}
		}
		throw new NoSuchElementException(
				"Could not click any provided visible text option: " + Arrays.toString(options) + ". Last error: "
						+ (lastError == null ? "none" : lastError.getMessage()));
	}

	private WebElement findClickableByVisibleText(final String text, final Duration timeout) {
		final long deadline = System.nanoTime() + timeout.toNanos();
		final List<By> selectors = Arrays.asList(
				By.xpath("//button[normalize-space()=" + escapeXPath(text) + "]"),
				By.xpath("//a[normalize-space()=" + escapeXPath(text) + "]"),
				By.xpath("//*[@role='button' and normalize-space()=" + escapeXPath(text) + "]"),
				By.xpath("//*[normalize-space()=" + escapeXPath(text) + "]"),
				By.xpath("//button[contains(normalize-space()," + escapeXPath(text) + ")]"),
				By.xpath("//a[contains(normalize-space()," + escapeXPath(text) + ")]"),
				By.xpath("//*[@role='button' and contains(normalize-space()," + escapeXPath(text) + ")]"),
				By.xpath("//*[contains(normalize-space()," + escapeXPath(text) + ")]"));

		while (System.nanoTime() < deadline) {
			for (final By selector : selectors) {
				final List<WebElement> candidates = driver.findElements(selector);
				for (final WebElement candidate : candidates) {
					if (candidate.isDisplayed()) {
						return candidate;
					}
				}
			}
			try {
				Thread.sleep(250L);
			} catch (final InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				throw new TimeoutException("Interrupted while searching for: " + text);
			}
		}

		throw new TimeoutException("Timed out while searching for text: " + text);
	}

	private void clickElement(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private String waitForNewWindowHandle(final Set<String> existingHandles, final Duration timeout) {
		final long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			final Set<String> currentHandles = driver.getWindowHandles();
			for (final String handle : currentHandles) {
				if (!existingHandles.contains(handle)) {
					return handle;
				}
			}
			try {
				Thread.sleep(200L);
			} catch (final InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	private boolean isGoogleAccountsPage() {
		final String currentUrl = driver.getCurrentUrl();
		return currentUrl != null && currentUrl.contains("accounts.google.com");
	}

	private boolean isTextVisible(final String text) {
		final By by = By.xpath("//*[normalize-space()=" + escapeXPath(text) + " or contains(normalize-space(),"
				+ escapeXPath(text) + ")]");
		for (final WebElement element : driver.findElements(by)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private String findClosestSectionText(final String sectionTitle) {
		final List<WebElement> headings = driver
				.findElements(By.xpath("//*[normalize-space()=" + escapeXPath(sectionTitle) + "]"));
		for (final WebElement heading : headings) {
			try {
				final WebElement container = heading.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
				if (container.isDisplayed()) {
					final String text = container.getText();
					if (text != null && !text.isBlank()) {
						return text;
					}
				}
			} catch (final Exception ignored) {
				// Keep trying other possible containers.
			}
		}
		return driver.findElement(By.tagName("body")).getText();
	}

	private boolean hasLikelyNameValue(final String text, final String... knownLabels) {
		if (text == null || text.isBlank()) {
			return false;
		}

		final List<String> blocked = new ArrayList<>();
		for (final String label : knownLabels) {
			blocked.add(normalize(label));
		}

		final String[] lines = text.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine == null ? "" : rawLine.trim();
			if (line.length() < 3 || line.length() > 80) {
				continue;
			}

			final String normalized = normalize(line);
			if (normalized.isBlank() || blocked.contains(normalized) || EMAIL_PATTERN.matcher(line).find()
					|| line.matches(".*\\d.*")) {
				continue;
			}

			if (line.matches("[\\p{L}][\\p{L}.' -]{2,}")) {
				return true;
			}
		}

		return false;
	}

	private String normalize(final String input) {
		if (input == null) {
			return "";
		}
		final String noAccents = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return noAccents.toLowerCase(Locale.ROOT).trim();
	}

	private void takeCheckpoint(final String fileBaseName, final boolean fullPageHint) {
		if (driver == null || evidenceDirectory == null) {
			return;
		}

		try {
			final String suffix = fullPageHint ? "-full" : "";
			final Path screenshotPath = evidenceDirectory.resolve(sanitizeFileName(fileBaseName + suffix) + ".png");
			final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(screenshotPath, screenshot);
		} catch (final Exception ignored) {
			// A screenshot failure should not stop workflow execution.
		}
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDirectory == null) {
			return;
		}

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("saleads_mi_negocio_full_test\n");
		reportBuilder.append("Final Report\n\n");
		for (final String field : REPORT_FIELDS) {
			final StepResult result = stepResults.get(field);
			reportBuilder.append(field).append(": ").append(result.pass ? "PASS" : "FAIL");
			if (result.details != null && !result.details.isBlank()) {
				reportBuilder.append(" - ").append(result.details);
			}
			reportBuilder.append("\n");
		}

		Files.write(evidenceDirectory.resolve("final-report.txt"), reportBuilder.toString().getBytes(StandardCharsets.UTF_8));
	}

	private void printFinalReport() {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("\n=== saleads_mi_negocio_full_test ===\n");
		for (final String field : REPORT_FIELDS) {
			final StepResult result = stepResults.get(field);
			reportBuilder.append(field).append(": ").append(result.pass ? "PASS" : "FAIL");
			if (result.details != null && !result.details.isBlank()) {
				reportBuilder.append(" (").append(result.details).append(")");
			}
			reportBuilder.append("\n");
		}
		System.out.println(reportBuilder);
	}

	private String sanitizeFileName(final String fileName) {
		return fileName.replaceAll("[^a-zA-Z0-9._-]+", "_").toLowerCase(Locale.ROOT);
	}

	private String escapeXPath(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private interface StepAction {
		String run() throws Exception;
	}

	private static final class StepResult {
		private final boolean pass;
		private final String details;

		private StepResult(final boolean pass, final String details) {
			this.pass = pass;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
