package io.proleap.e2e.saleads;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow for SaleADS "Mi Negocio".
 *
 * Required runtime environment variables:
 * - SALEADS_BASE_URL: Login URL for the current SaleADS environment, unless using SALEADS_DEBUGGER_ADDRESS.
 *
 * Optional environment variables:
 * - SALEADS_DEBUGGER_ADDRESS (attach to an already-open Chrome session, e.g. localhost:9222)
 * - SALEADS_HEADLESS (default: true)
 * - SALEADS_TIMEOUT_SECONDS (default: 30)
 * - SALEADS_EXPECTED_USER_NAME
 * - SALEADS_EXPECTED_USER_EMAIL (default: juanlucasbarbiergarzon@gmail.com)
 */
public class SaleadsMiNegocioWorkflowE2ETest {

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private static final DateTimeFormatter FILE_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);

	private final Map<String, String> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		final String debuggerAddress = getEnvOrDefault("SALEADS_DEBUGGER_ADDRESS", "").trim();

		if (!debuggerAddress.isEmpty()) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		}

		if (debuggerAddress.isEmpty() && Boolean.parseBoolean(getEnvOrDefault("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(getEnvAsInt("SALEADS_TIMEOUT_SECONDS", 30)));
		evidenceDir = Files.createDirectories(Path.of("target", "saleads-evidence",
				FILE_TS_FORMATTER.format(Instant.now()) + "-saleads-mi-negocio-full-test"));

		for (final String field : REPORT_FIELDS) {
			report.put(field, "NOT_RUN");
		}
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String baseUrl = getEnvOrDefault("SALEADS_BASE_URL", "").trim();
		if (!baseUrl.isEmpty()) {
			driver.get(baseUrl);
			waitForUiToLoad();
		} else {
			final String currentUrl = driver.getCurrentUrl();
			final boolean hasUsableCurrentPage = currentUrl != null && !currentUrl.isBlank()
					&& !"about:blank".equals(currentUrl) && !currentUrl.startsWith("data:");
			Assert.assertTrue(
					"Set SALEADS_BASE_URL, or set SALEADS_DEBUGGER_ADDRESS with an already-open browser on the SaleADS login page.",
					hasUsableCurrentPage);
		}

		executeStep("Login", this::runStepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::runStepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::runStepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::runStepOpenAdministrarNegocios);
		executeStep("Información General", this::runStepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::runStepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::runStepValidateTusNegocios);
		executeStep("Términos y Condiciones", () -> runStepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones"));
		executeStep("Política de Privacidad", () -> runStepValidateLegalLink("Política de Privacidad", "Política de Privacidad"));

		final List<String> failures = report.entrySet().stream().filter(entry -> !entry.getValue().startsWith("PASS"))
				.map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.toList());
		Assert.assertTrue("One or more workflow validations failed:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private void runStepLoginWithGoogle() throws Exception {
		clickAnyVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Login with Google");
		waitForUiToLoad();

		// Optional Google account chooser selection.
		if (isTextVisibleNow("juanlucasbarbiergarzon@gmail.com")) {
			clickByVisibleText("juanlucasbarbiergarzon@gmail.com");
			waitForUiToLoad();
		}

		waitForAnyVisibleText(Arrays.asList("Negocio", "Dashboard", "Panel", "Inicio"), "Main application interface");
		assertSidebarVisible();
		captureScreenshot("01-dashboard-loaded");
	}

	private void runStepOpenMiNegocioMenu() throws Exception {
		waitForAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio"), "left sidebar navigation");

		// Some layouts require opening the parent item first.
		if (!isTextVisibleNow("Mi Negocio")) {
			clickByVisibleText("Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void runStepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertTextVisible("Crear Nuevo Negocio");
		assertBusinessNameInputVisible();
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		typeInBusinessNameField("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void runStepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisibleNow("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		captureFullPageScreenshot("04-administrar-negocios-account-page");
	}

	private void runStepValidateInformacionGeneral() throws Exception {
		assertSectionVisible("Información General");
		assertUserNameVisible();
		assertUserEmailVisible();
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
	}

	private void runStepValidateDetallesCuenta() throws Exception {
		assertSectionVisible("Detalles de la Cuenta");
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void runStepValidateTusNegocios() throws Exception {
		assertSectionVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void runStepValidateLegalLink(final String linkText, final String headingText) throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = new HashSet<>(driver.getWindowHandles());
		final String currentUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);

		wait.until(driver -> driver.getWindowHandles().size() > handlesBefore.size()
				|| !driver.getCurrentUrl().equals(currentUrl));

		final Set<String> handlesAfter = driver.getWindowHandles();
		String newHandle = null;

		if (handlesAfter.size() > handlesBefore.size()) {
			for (final String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					newHandle = handle;
					break;
				}
			}
		}

		if (newHandle != null) {
			driver.switchTo().window(newHandle);
		}

		waitForUiToLoad();
		assertTextVisible(headingText);
		assertLegalContentVisible();

		captureScreenshot("05-legal-" + sanitizeFileName(headingText));
		legalUrls.put(headingText, driver.getCurrentUrl());

		// Cleanup: return to the app tab.
		if (newHandle != null) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void executeStep(final String fieldName, final StepExecutable executable) {
		try {
			executable.execute();
			report.put(fieldName, "PASS");
		} catch (final Throwable ex) {
			report.put(fieldName, "FAIL: " + summarizeException(ex));
			safeCaptureFailureScreenshot(fieldName);
		}
	}

	private void safeCaptureFailureScreenshot(final String fieldName) {
		try {
			captureScreenshot("failure-" + sanitizeFileName(fieldName));
		} catch (final Exception ignored) {
			// Best-effort only.
		}
	}

	private void assertSidebarVisible() {
		final boolean visible = hasVisibleElements(By.xpath("//aside | //nav")) || isTextVisibleNow("Negocio");
		Assert.assertTrue("Left sidebar navigation is not visible.", visible);
	}

	private void assertBusinessNameInputVisible() {
		final By inputBy = By.xpath(
				"//input[contains(translate(@placeholder,'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚ','abcdefghijklmnopqrstuvwxyzáéíóú'),'nombre del negocio')]"
						+ " | //label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]");
		Assert.assertTrue("Input field 'Nombre del Negocio' was not found.", hasVisibleElements(inputBy));
	}

	private void typeInBusinessNameField(final String businessName) {
		final By inputBy = By.xpath(
				"//input[contains(translate(@placeholder,'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚ','abcdefghijklmnopqrstuvwxyzáéíóú'),'nombre del negocio')]"
						+ " | //label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]");
		final WebElement input = wait.until(driver -> firstVisibleElement(inputBy));
		scrollIntoView(input);
		input.sendKeys(Keys.chord(Keys.CONTROL, "a"));
		input.sendKeys(Keys.BACK_SPACE);
		input.sendKeys(businessName);
	}

	private void assertSectionVisible(final String sectionTitle) {
		assertTextVisible(sectionTitle);
	}

	private void assertUserNameVisible() {
		final String expectedName = getEnvOrDefault("SALEADS_EXPECTED_USER_NAME", "").trim();

		if (!expectedName.isEmpty()) {
			assertTextVisible(expectedName);
			return;
		}

		final String sectionText = getSectionText("Información General");
		final Pattern userNamePattern = Pattern.compile("\\b[\\p{L}]{2,}(?:\\s+[\\p{L}]{2,})+\\b");
		final Matcher matcher = userNamePattern.matcher(sectionText);
		Assert.assertTrue("User name was not found in 'Información General'.", matcher.find());
	}

	private void assertUserEmailVisible() {
		final String expectedEmail = getEnvOrDefault("SALEADS_EXPECTED_USER_EMAIL",
				"juanlucasbarbiergarzon@gmail.com").trim();
		if (!expectedEmail.isEmpty() && isTextVisibleNow(expectedEmail)) {
			return;
		}

		final String sectionText = getSectionText("Información General");
		final Pattern emailPattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
		final Matcher matcher = emailPattern.matcher(sectionText);
		Assert.assertTrue("User email was not found in 'Información General'.", matcher.find());
	}

	private String getSectionText(final String sectionHeading) {
		final String headingLiteral = xpathLiteral(sectionHeading);
		final By sectionBy = By.xpath(
				"//*[normalize-space()=" + headingLiteral + "]/ancestor::*[self::section or self::div][1]");
		final WebElement section = wait.until(driver -> firstVisibleElement(sectionBy));
		return section == null ? "" : section.getText();
	}

	private void assertLegalContentVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Legal content text is not visible.", bodyText != null && bodyText.trim().length() > 80);
	}

	private void clickAnyVisibleText(final String... texts) {
		Exception lastException = null;
		for (final String text : texts) {
			try {
				clickByVisibleText(text);
				return;
			} catch (final Exception ex) {
				lastException = ex;
			}
		}

		throw new AssertionError(
				"Could not click any of the expected visible texts: " + String.join(", ", texts), lastException);
	}

	private void clickByVisibleText(final String visibleText) {
		final String exactText = xpathLiteral(visibleText);
		final By exactLocator = By.xpath("//*[self::button or self::a or self::span or self::div or self::p or self::li]"
				+ "[normalize-space()=" + exactText + "]");
		final By containsLocator = By.xpath("//*[self::button or self::a or self::span or self::div or self::p or self::li]"
				+ "[contains(normalize-space(), " + exactText + ")]");

		final WebElement target = wait.until(driver -> {
			final WebElement exact = firstVisibleElement(exactLocator);
			if (exact != null) {
				return exact;
			}
			return firstVisibleElement(containsLocator);
		});

		if (target == null) {
			throw new NoSuchElementException("No visible element found with text: " + visibleText);
		}

		scrollIntoView(target);
		final WebElement clickableTarget = target;
		wait.until(driver -> clickableTarget.isDisplayed() && clickableTarget.isEnabled());
		target.click();
		waitForUiToLoad();
	}

	private void assertTextVisible(final String text) {
		waitForAnyVisibleText(List.of(text), text);
	}

	private void waitForAnyVisibleText(final List<String> texts, final String targetDescription) {
		wait.until(driver -> {
			for (final String text : texts) {
				if (isTextVisibleNow(text)) {
					return true;
				}
			}
			return false;
		});

		// settle async rendering after text appears.
		waitForUiToLoad();
	}

	private boolean isTextVisibleNow(final String text) {
		final String literal = xpathLiteral(text);
		final By exactBy = By.xpath("//*[normalize-space()=" + literal + "]");
		final By containsBy = By.xpath("//*[contains(normalize-space()," + literal + ")]");
		return hasVisibleElements(exactBy) || hasVisibleElements(containsBy);
	}

	private WebElement firstVisibleElement(final By by) {
		for (final WebElement element : driver.findElements(by)) {
			try {
				if (element.isDisplayed()) {
					return element;
				}
			} catch (final Exception ignored) {
				// element went stale or became detached
			}
		}
		return null;
	}

	private boolean hasVisibleElements(final By by) {
		return firstVisibleElement(by) != null;
	}

	private void waitForUiToLoad() {
		waitForDocumentReadyState();
		try {
			Thread.sleep(350);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void waitForDocumentReadyState() {
		final ExpectedCondition<Boolean> pageLoadCondition = driver -> {
			if (!(driver instanceof JavascriptExecutor)) {
				return true;
			}
			final Object state = ((JavascriptExecutor) driver).executeScript("return document.readyState");
			return "complete".equals(state);
		};

		try {
			wait.until(pageLoadCondition);
		} catch (final TimeoutException ignored) {
			// Single-page apps can keep loading resources forever; this is best-effort.
		}
	}

	private void scrollIntoView(final WebElement element) {
		if (driver instanceof JavascriptExecutor) {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'center'});",
					element);
		}
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final String fileName = FILE_TS_FORMATTER.format(Instant.now()) + "-" + sanitizeFileName(checkpointName) + ".png";
		final Path target = evidenceDir.resolve(fileName);
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureFullPageScreenshot(final String checkpointName) throws IOException {
		if (!(driver instanceof JavascriptExecutor)) {
			captureScreenshot(checkpointName);
			return;
		}

		final JavascriptExecutor js = (JavascriptExecutor) driver;
		final Number widthNumber = (Number) js.executeScript(
				"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, 1920);");
		final Number heightNumber = (Number) js.executeScript(
				"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, 1080);");

		final int width = Math.min(Math.max(widthNumber.intValue(), 1280), 2560);
		final int height = Math.min(Math.max(heightNumber.intValue(), 1080), 6000);
		final Dimension originalSize = driver.manage().window().getSize();

		driver.manage().window().setSize(new Dimension(width, height));
		waitForUiToLoad();
		captureScreenshot(checkpointName);
		driver.manage().window().setSize(originalSize);
		waitForUiToLoad();
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("executed_at_utc=" + Instant.now());
		lines.add("");
		lines.add("Step report:");
		for (final String field : REPORT_FIELDS) {
			lines.add("- " + field + ": " + report.getOrDefault(field, "NOT_RUN"));
		}
		lines.add("");
		lines.add("Legal URLs:");
		if (legalUrls.isEmpty()) {
			lines.add("- none captured");
		} else {
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				lines.add("- " + entry.getKey() + ": " + entry.getValue());
			}
		}

		Files.write(evidenceDir.resolve("final-report.txt"), lines);
	}

	private int getEnvAsInt(final String name, final int defaultValue) {
		final String rawValue = getEnvOrDefault(name, String.valueOf(defaultValue)).trim();
		try {
			return Integer.parseInt(rawValue);
		} catch (final NumberFormatException ignored) {
			return defaultValue;
		}
	}

	private String getEnvOrDefault(final String name, final String defaultValue) {
		final Map<String, String> env = new HashMap<>(System.getenv());
		return env.getOrDefault(name, defaultValue);
	}

	private String summarizeException(final Throwable exception) {
		final String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}

		String singleLine = message.replace('\n', ' ').replace('\r', ' ').trim();
		if (singleLine.length() > 220) {
			singleLine = singleLine.substring(0, 220) + "...";
		}

		return singleLine;
	}

	private String sanitizeFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'");
		return "concat('" + String.join("',\"'\",'", parts) + "')";
	}

	@FunctionalInterface
	private interface StepExecutable {
		void execute() throws Exception;
	}
}
