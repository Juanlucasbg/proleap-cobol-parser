package e2e;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaleAdsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, StepResult> finalReport = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();
	private int screenshotCounter = 1;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = readFlag("saleads.test.enabled", "SALEADS_TEST_ENABLED", false);
		Assume.assumeTrue(
				"SaleADS E2E test disabled. Enable with -Dsaleads.test.enabled=true or SALEADS_TEST_ENABLED=true.",
				enabled
		);

		final ChromeOptions chromeOptions = new ChromeOptions();
		if (readFlag("saleads.headless", "SALEADS_HEADLESS", true)) {
			chromeOptions.addArguments("--headless=new");
		}
		chromeOptions.addArguments("--window-size=1920,1200");
		chromeOptions.addArguments("--disable-gpu");
		chromeOptions.addArguments("--no-sandbox");
		chromeOptions.addArguments("--disable-dev-shm-usage");
		chromeOptions.addArguments("--lang=es-ES");

		final String debuggerAddress = readSetting("saleads.debuggerAddress", "SALEADS_DEBUGGER_ADDRESS");
		if (!debuggerAddress.isBlank()) {
			chromeOptions.setExperimentalOption("debuggerAddress", debuggerAddress);
		}

		driver = new ChromeDriver(chromeOptions);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().window().setSize(new Dimension(1920, 1200));
		evidenceDir = buildEvidenceDirectory();
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		initializeReport();

		executeStep("Login", () -> {
			openLoginPageIfConfigured();
			waitForUiToLoad();
			clickUsingVisibleTexts(
					"Sign in with Google",
					"Iniciar sesión con Google",
					"Continuar con Google",
					"Login with Google",
					"Acceder con Google"
			);
			selectGoogleAccountIfShown("juanlucasbarbiergarzon@gmail.com");
			waitForMainApplicationInterface();
			captureScreenshot("dashboard_loaded", false);
		});

		executeStep("Mi Negocio menu", () -> {
			expandMiNegocioMenu();
			assertVisibleTexts("Agregar Negocio");
			assertVisibleTexts("Administrar Negocios");
			captureScreenshot("mi_negocio_menu_expanded", false);
		});

		executeStep("Agregar Negocio modal", () -> {
			clickUsingVisibleTexts("Agregar Negocio");
			waitForUiToLoad();
			assertVisibleTexts("Crear Nuevo Negocio");
			assertVisibleTexts("Nombre del Negocio");
			assertVisibleTexts("Tienes 2 de 3 negocios");
			assertVisibleTexts("Cancelar", "Crear Negocio");

			final WebElement businessNameInput = findVisibleElement(By.xpath(
					"//input[contains(@placeholder,'Nombre') or contains(@aria-label,'Nombre') or contains(@name,'nombre')]"
			), SHORT_TIMEOUT);
			businessNameInput.click();
			waitForUiToLoad();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatización");
			waitForUiToLoad();
			captureScreenshot("agregar_negocio_modal", false);
			clickUsingVisibleTexts("Cancelar");
			waitForUiToLoad();
		});

		executeStep("Administrar Negocios view", () -> {
			expandMiNegocioMenu();
			clickUsingVisibleTexts("Administrar Negocios");
			waitForUiToLoad();
			assertVisibleTexts("Información General");
			assertVisibleTexts("Detalles de la Cuenta");
			assertVisibleTexts("Tus Negocios");
			assertVisibleTexts("Sección Legal");
			captureScreenshot("administrar_negocios", true);
		});

		executeStep("Información General", () -> {
			assertVisibleTexts("BUSINESS PLAN");
			assertVisibleTexts("Cambiar Plan");
			assertEmailPresent();
			assertUserNamePresent();
		});

		executeStep("Detalles de la Cuenta", () -> {
			assertVisibleTexts("Cuenta creada");
			assertVisibleTexts("Estado activo");
			assertVisibleTexts("Idioma seleccionado");
		});

		executeStep("Tus Negocios", () -> {
			assertVisibleTexts("Tus Negocios");
			assertVisibleTexts("Agregar Negocio");
			assertVisibleTexts("Tienes 2 de 3 negocios");
			assertBusinessListVisible();
		});

		executeStep("Términos y Condiciones", () -> {
			final String legalUrl = openLegalLinkAndValidate(
					new String[]{"Términos y Condiciones", "Terminos y Condiciones"},
					new String[]{"Términos y Condiciones", "Terminos y Condiciones"},
					"terminos_y_condiciones"
			);
			capturedUrls.put("Términos y Condiciones", legalUrl);
		});

		executeStep("Política de Privacidad", () -> {
			final String legalUrl = openLegalLinkAndValidate(
					new String[]{"Política de Privacidad", "Politica de Privacidad"},
					new String[]{"Política de Privacidad", "Politica de Privacidad"},
					"politica_de_privacidad"
			);
			capturedUrls.put("Política de Privacidad", legalUrl);
		});

		writeFinalReportArtifact();
		assertAllStepsPassed();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	private void executeStep(final String stepName, final CheckedStep checkedStep) {
		try {
			checkedStep.run();
			finalReport.put(stepName, StepResult.pass());
		} catch (Exception ex) {
			final String screenshotPath = safelyCaptureFailureScreenshot(stepName);
			final String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			finalReport.put(stepName, StepResult.fail(message, screenshotPath));
		}
	}

	private void initializeReport() {
		finalReport.put("Login", StepResult.pending());
		finalReport.put("Mi Negocio menu", StepResult.pending());
		finalReport.put("Agregar Negocio modal", StepResult.pending());
		finalReport.put("Administrar Negocios view", StepResult.pending());
		finalReport.put("Información General", StepResult.pending());
		finalReport.put("Detalles de la Cuenta", StepResult.pending());
		finalReport.put("Tus Negocios", StepResult.pending());
		finalReport.put("Términos y Condiciones", StepResult.pending());
		finalReport.put("Política de Privacidad", StepResult.pending());
	}

	private void openLoginPageIfConfigured() {
		final String startUrl = readSetting("saleads.startUrl", "SALEADS_START_URL");
		if (!startUrl.isBlank()) {
			driver.navigate().to(startUrl);
			return;
		}

		if ("about:blank".equalsIgnoreCase(driver.getCurrentUrl())) {
			throw new IllegalStateException(
					"Start URL not provided. Set -Dsaleads.startUrl or SALEADS_START_URL " +
							"to the current SaleADS login page URL for your target environment."
			);
		}
	}

	private void waitForMainApplicationInterface() {
		waitForUiToLoad();
		final boolean sidebarVisible = isAnyVisible(
				By.xpath("//aside//*[contains(normalize-space(.),'Negocio')]"),
				By.xpath("//nav//*[contains(normalize-space(.),'Negocio')]"),
				By.xpath("//*[contains(normalize-space(.),'Mi Negocio')]")
		);

		if (!sidebarVisible) {
			throw new AssertionError("Main application sidebar not visible after login.");
		}
	}

	private void expandMiNegocioMenu() {
		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			clickUsingVisibleTexts("Negocio", "Mi Negocio");
			waitForUiToLoad();
		}

		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			clickUsingVisibleTexts("Mi Negocio");
			waitForUiToLoad();
		}
	}

	private String openLegalLinkAndValidate(
			final String[] linkTextCandidates,
			final String[] headingCandidates,
			final String screenshotName
	) throws IOException {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickUsingVisibleTexts(linkTextCandidates);
		waitForUiToLoad();

		String targetWindow = originalWindow;
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(
					d -> d.getWindowHandles().size() > handlesBefore.size()
			);
			for (String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					targetWindow = handle;
					break;
				}
			}
			driver.switchTo().window(targetWindow);
		} catch (TimeoutException ignored) {
			targetWindow = originalWindow;
		}

		waitForUiToLoad();
		assertVisibleTexts(headingCandidates);
		assertLegalTextVisible();
		captureScreenshot(screenshotName, false);
		final String finalUrl = driver.getCurrentUrl();

		if (!targetWindow.equals(originalWindow)) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void selectGoogleAccountIfShown(final String email) {
		waitForUiToLoad();
		final Set<String> handles = driver.getWindowHandles();
		for (String handle : handles) {
			driver.switchTo().window(handle);
			final By emailOption = By.xpath(
					"//*[contains(normalize-space(.)," + toXpathLiteral(email) + ")]"
			);
			if (!driver.findElements(emailOption).isEmpty()) {
				final WebElement emailElement = findVisibleElement(emailOption, SHORT_TIMEOUT);
				emailElement.click();
				waitForUiToLoad();
				break;
			}
		}

		final List<String> handlesAsList = new ArrayList<>(driver.getWindowHandles());
		if (!handlesAsList.isEmpty()) {
			driver.switchTo().window(handlesAsList.get(0));
		}
	}

	private void clickUsingVisibleTexts(final String... texts) {
		AssertionError lastError = null;
		for (String text : texts) {
			try {
				clickUsingVisibleText(text);
				return;
			} catch (AssertionError error) {
				lastError = error;
			}
		}
		throw lastError == null ? new AssertionError("Unable to click any target text.") : lastError;
	}

	private void clickUsingVisibleText(final String text) {
		final String safeText = toXpathLiteral(text);
		final List<By> selectors = List.of(
				By.xpath(
						"//*[self::button or self::a or @role='button' or self::span or self::div or self::li]" +
								"[normalize-space(.)=" + safeText + "]"
				),
				By.xpath(
						"//*[self::button or self::a or @role='button' or self::span or self::div or self::li]" +
								"[contains(normalize-space(.)," + safeText + ")]"
				)
		);

		for (By by : selectors) {
			try {
				final WebElement element = findVisibleElement(by, SHORT_TIMEOUT);
				wait.until(ExpectedConditions.elementToBeClickable(element));
				element.click();
				waitForUiToLoad();
				return;
			} catch (RuntimeException ignored) {
				// Try another selector pattern for the same visible text.
			}
		}

		throw new AssertionError("Could not click visible text: " + text);
	}

	private void assertVisibleTexts(final String... texts) {
		for (String text : texts) {
			if (!isTextVisible(text)) {
				throw new AssertionError("Expected visible text not found: " + text);
			}
		}
	}

	private boolean isTextVisible(final String text) {
		final String safeText = toXpathLiteral(text);
		return isAnyVisible(
				By.xpath("//*[normalize-space(.)=" + safeText + "]"),
				By.xpath("//*[contains(normalize-space(.)," + safeText + ")]")
		);
	}

	private boolean isAnyVisible(final By... selectors) {
		for (By selector : selectors) {
			try {
				for (WebElement element : driver.findElements(selector)) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			} catch (NoSuchElementException ignored) {
				// Check the next selector.
			}
		}
		return false;
	}

	private WebElement findVisibleElement(final By selector, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(d -> {
			List<WebElement> elements = d.findElements(selector);
			for (WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private void assertEmailPresent() {
		final WebElement body = findVisibleElement(By.tagName("body"), SHORT_TIMEOUT);
		final Matcher matcher = EMAIL_PATTERN.matcher(body.getText());
		if (!matcher.find()) {
			throw new AssertionError("Expected a visible user email on the account page.");
		}
	}

	private void assertUserNamePresent() {
		final List<By> candidateSelectors = List.of(
				By.xpath("//*[contains(translate(normalize-space(.), 'NOMBRE', 'nombre'), 'nombre')]"),
				By.xpath("//section[contains(.,'Información General')]//*[self::h1 or self::h2 or self::h3 or self::p or self::span]")
		);

		for (By candidateSelector : candidateSelectors) {
			for (WebElement element : driver.findElements(candidateSelector)) {
				final String text = element.getText().trim();
				if (element.isDisplayed() && text.length() > 2 && !text.contains("@")) {
					return;
				}
			}
		}

		throw new AssertionError("Could not confirm a visible user name in Información General.");
	}

	private void assertBusinessListVisible() {
		final List<By> listSelectors = List.of(
				By.xpath("//*[contains(normalize-space(.),'Tus Negocios')]//following::*[self::ul or self::ol or self::table][1]"),
				By.xpath("//*[contains(normalize-space(.),'Tus Negocios')]//following::*[contains(@class,'list') or contains(@class,'table') or contains(@class,'card')][1]")
		);

		for (By selector : listSelectors) {
			if (isAnyVisible(selector)) {
				return;
			}
		}

		throw new AssertionError("Business list not visible in 'Tus Negocios'.");
	}

	private void assertLegalTextVisible() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p[string-length(normalize-space(.)) > 40]"));
		for (WebElement paragraph : paragraphs) {
			if (paragraph.isDisplayed()) {
				return;
			}
		}
		throw new AssertionError("No legal content paragraph was visible.");
	}

	private void waitForUiToLoad() {
		wait.until((ExpectedCondition<Boolean>) wd -> {
			if (!(wd instanceof JavascriptExecutor)) {
				return true;
			}
			Object readyState = ((JavascriptExecutor) wd).executeScript("return document.readyState");
			return "complete".equals(readyState);
		});

		try {
			Thread.sleep(400);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private Path captureScreenshot(final String checkpointName, final boolean fullPage) throws IOException {
		final String fileName = String.format(
				Locale.ROOT,
				"%02d_%s.png",
				screenshotCounter++,
				checkpointName.replaceAll("[^a-zA-Z0-9_\\-]", "_")
		);
		final Path screenshotPath = evidenceDir.resolve(fileName);

		Dimension originalSize = driver.manage().window().getSize();
		if (fullPage) {
			final Number pageHeight = (Number) ((JavascriptExecutor) driver).executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);"
			);
			final int targetHeight = Math.min(Math.max(pageHeight.intValue(), originalSize.getHeight()), 5000);
			driver.manage().window().setSize(new Dimension(originalSize.getWidth(), targetHeight));
			waitForUiToLoad();
		}

		final Path tempScreenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(tempScreenshot, screenshotPath, StandardCopyOption.REPLACE_EXISTING);

		if (fullPage) {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}

		return screenshotPath;
	}

	private String safelyCaptureFailureScreenshot(final String stepName) {
		try {
			final Path failurePath = captureScreenshot("failure_" + stepName, false);
			return evidenceDir.relativize(failurePath).toString();
		} catch (Exception ignored) {
			return "";
		}
	}

	private void writeFinalReportArtifact() throws IOException {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("saleads_mi_negocio_full_test final report").append(System.lineSeparator());
		reportBuilder.append("evidence_dir=").append(evidenceDir).append(System.lineSeparator());
		reportBuilder.append(System.lineSeparator());

		for (Map.Entry<String, StepResult> entry : finalReport.entrySet()) {
			final StepResult result = entry.getValue();
			reportBuilder
					.append(entry.getKey())
					.append(": ")
					.append(result.status)
					.append(System.lineSeparator());
			if (!result.details.isBlank()) {
				reportBuilder.append("  details: ").append(result.details).append(System.lineSeparator());
			}
			if (!result.failureScreenshot.isBlank()) {
				reportBuilder
						.append("  failure_screenshot: ")
						.append(result.failureScreenshot)
						.append(System.lineSeparator());
			}
		}

		if (!capturedUrls.isEmpty()) {
			reportBuilder.append(System.lineSeparator()).append("captured_urls").append(System.lineSeparator());
			for (Map.Entry<String, String> urlEntry : capturedUrls.entrySet()) {
				reportBuilder
						.append("  ")
						.append(urlEntry.getKey())
						.append(": ")
						.append(urlEntry.getValue())
						.append(System.lineSeparator());
			}
		}

		final Path reportPath = evidenceDir.resolve("final_report.txt");
		Files.writeString(reportPath, reportBuilder.toString());
	}

	private void assertAllStepsPassed() {
		final List<String> failedSteps = new ArrayList<>();
		for (Map.Entry<String, StepResult> entry : finalReport.entrySet()) {
			if (!"PASS".equals(entry.getValue().status)) {
				failedSteps.add(entry.getKey() + " (" + entry.getValue().details + ")");
			}
		}

		if (!failedSteps.isEmpty()) {
			Assert.fail("One or more SaleADS workflow validations failed: " + failedSteps);
		}
	}

	private Path buildEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		final Path path = Path.of("target", "saleads-mi-negocio-evidence", timestamp);
		Files.createDirectories(path);
		return path;
	}

	private boolean readFlag(final String propertyName, final String envName, final boolean defaultValue) {
		final String value = readSetting(propertyName, envName);
		if (value.isBlank()) {
			return defaultValue;
		}
		return "true".equalsIgnoreCase(value.trim());
	}

	private String readSetting(final String propertyName, final String envName) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		return "";
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		StringBuilder builder = new StringBuilder("concat(");
		String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}

	private static class StepResult {
		private final String status;
		private final String details;
		private final String failureScreenshot;

		private StepResult(final String status, final String details, final String failureScreenshot) {
			this.status = status;
			this.details = details;
			this.failureScreenshot = failureScreenshot;
		}

		private static StepResult pending() {
			return new StepResult("PENDING", "", "");
		}

		private static StepResult pass() {
			return new StepResult("PASS", "", "");
		}

		private static StepResult fail(final String details, final String failureScreenshot) {
			return new StepResult("FAIL", details, failureScreenshot);
		}
	}
}
