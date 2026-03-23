package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
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

public class SaleAdsMiNegocioWorkflowE2ETest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> failureDetails = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactDirectory;
	private Path reportPath;

	@Before
	public void setUp() throws IOException {
		final boolean runE2E = Boolean.parseBoolean(env("RUN_SALEADS_E2E", "false"));
		Assume.assumeTrue("RUN_SALEADS_E2E must be true to run this end-to-end test.", runE2E);

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("SALEADS_LOGIN_URL must point to the current SaleADS environment login page.",
				loginUrl != null && !loginUrl.isBlank());

		artifactDirectory = Path.of("target", "saleads-e2e-artifacts", timestamp());
		Files.createDirectories(artifactDirectory);
		reportPath = artifactDirectory.resolve(TEST_NAME + "_report.txt");

		final ChromeOptions chromeOptions = new ChromeOptions();
		if (Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"))) {
			chromeOptions.addArguments("--headless=new");
		}
		chromeOptions.addArguments("--window-size=1920,2200");
		chromeOptions.addArguments("--no-sandbox");
		chromeOptions.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(chromeOptions);
		wait = new WebDriverWait(driver, Duration.ofSeconds(Long.parseLong(env("SALEADS_WAIT_SECONDS", "30"))));

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep("Login", this::loginWithGoogle);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegocios);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", () -> validateLegalLink("Términos y Condiciones"));
		runStep("Política de Privacidad", () -> validateLegalLink("Política de Privacidad"));

		writeFinalReport();

		final List<String> failedSteps = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			if (!Boolean.TRUE.equals(stepStatus.get(field))) {
				failedSteps.add(field);
			}
		}

		assertTrue("Workflow completed with failed validations: " + failedSteps + ". Report: "
				+ (reportPath == null ? "not generated" : reportPath.toAbsolutePath()), failedSteps.isEmpty());
	}

	private void loginWithGoogle() throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeLogin = driver.getWindowHandles();

		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");

		final String googleWindowHandle = waitForNewWindowHandle(handlesBeforeLogin, Duration.ofSeconds(10));
		if (googleWindowHandle != null) {
			driver.switchTo().window(googleWindowHandle);
		}

		selectGoogleAccountIfVisible();

		if (googleWindowHandle != null && driver.getWindowHandles().contains(appHandle)) {
			driver.switchTo().window(appHandle);
		}

		if (driver.getCurrentUrl().contains("accounts.google.")) {
			wait.until(webDriver -> !webDriver.getCurrentUrl().contains("accounts.google."));
		}

		waitForUiToLoad();
		wait.until(webDriver -> isVisible(By.xpath("//aside | //nav")));
		wait.until(webDriver -> isTextVisible("Negocio") || isTextVisible("Mi Negocio"));

		captureScreenshot("01_dashboard_loaded");
	}

	private void openMiNegocioMenu() throws Exception {
		wait.until(webDriver -> isVisible(By.xpath("//aside | //nav")));

		clickIfVisible("Negocio");
		clickByVisibleText("Mi Negocio");

		waitForText("Agregar Negocio");
		waitForText("Administrar Negocios");

		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForText("Crear Nuevo Negocio");

		assertTrue("'Nombre del Negocio' input was not found.", isVisible(By.xpath(
				"//label[contains(normalize-space(), 'Nombre del Negocio')] | //input[@placeholder='Nombre del Negocio'] | //input[@aria-label='Nombre del Negocio']")));
		assertTrue("'Tienes 2 de 3 negocios' text was not found.", isTextVisible("Tienes 2 de 3 negocios"));
		assertTrue("'Cancelar' button was not found.", isVisible(byClickableText("Cancelar")));
		assertTrue("'Crear Negocio' button was not found.", isVisible(byClickableText("Crear Negocio")));

		captureScreenshot("03_agregar_negocio_modal");

		final WebElement businessNameInput = firstVisibleElement(By.xpath(
				"//input[@placeholder='Nombre del Negocio'] | //input[@aria-label='Nombre del Negocio'] | //label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]"));
		if (businessNameInput != null) {
			businessNameInput.click();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatización");
		}

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(), 'Crear Nuevo Negocio')]")));
		waitForUiToLoad();
	}

	private void openAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		waitForText("Información General");
		waitForText("Detalles de la Cuenta");
		waitForText("Tus Negocios");
		waitForText("Sección Legal");

		captureScreenshot("04_administrar_negocios_page");
	}

	private void validateInformacionGeneral() throws Exception {
		final WebElement section = waitForSection("Información General");
		assertTrue("'Información General' section has no visible content.", section.getText().trim().length() > 20);

		final String expectedUserName = env("SALEADS_EXPECTED_USER_NAME", "").trim();
		if (!expectedUserName.isEmpty()) {
			assertTrue("Expected user name was not found: " + expectedUserName, isTextVisible(expectedUserName));
		} else {
			assertTrue("No likely user name was found in 'Información General'.", hasLikelyUserName(section.getText()));
		}

		final String expectedUserEmail = env("SALEADS_EXPECTED_USER_EMAIL", "").trim();
		if (!expectedUserEmail.isEmpty()) {
			assertTrue("Expected user email was not found: " + expectedUserEmail, isTextVisible(expectedUserEmail));
		} else {
			assertTrue("No visible user email was found in the account page.", pageContainsEmail());
		}

		assertTrue("'BUSINESS PLAN' text was not found.", isTextVisible("BUSINESS PLAN"));
		assertTrue("'Cambiar Plan' button was not found.", isVisible(byClickableText("Cambiar Plan")));
	}

	private void validateDetallesDeLaCuenta() {
		assertTrue("'Cuenta creada' text was not found.", isTextVisible("Cuenta creada"));
		assertTrue("'Estado activo' text was not found.", isTextVisible("Estado activo"));
		assertTrue("'Idioma seleccionado' text was not found.", isTextVisible("Idioma seleccionado"));
	}

	private void validateTusNegocios() throws Exception {
		final WebElement section = waitForSection("Tus Negocios");
		assertTrue("'Agregar Negocio' button was not found.", isVisible(byClickableText("Agregar Negocio")));
		assertTrue("'Tienes 2 de 3 negocios' text was not found.", isTextVisible("Tienes 2 de 3 negocios"));

		final String sectionText = section.getText().replace("Tus Negocios", "").trim();
		assertTrue("Business list/content is not visible in 'Tus Negocios' section.", sectionText.length() > 10);
	}

	private void validateLegalLink(final String linkText) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final String appUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);

		final String legalHandle = waitForNewWindowHandle(handlesBeforeClick, Duration.ofSeconds(8));
		final boolean openedNewTab = legalHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(legalHandle);
		}

		waitForUiToLoad();
		waitForText(linkText);
		assertTrue("Legal content is not visible for link: " + linkText, hasLegalContent());

		captureScreenshot("05_" + sanitize(linkText));
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else if (!driver.getCurrentUrl().equals(appUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		wait.until(webDriver -> isTextVisible("Sección Legal") || isTextVisible("Administrar Negocios"));
	}

	private void runStep(final String stepName, final StepExecutor executor) throws IOException {
		try {
			executor.execute();
			stepStatus.put(stepName, true);
		} catch (final Throwable throwable) {
			stepStatus.put(stepName, false);
			failureDetails.put(stepName, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
			captureScreenshot("failed_" + sanitize(stepName));
		}
	}

	private void selectGoogleAccountIfVisible() throws Exception {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
		try {
			final WebElement account = shortWait.until(ExpectedConditions.elementToBeClickable(
					By.xpath("//*[contains(normalize-space(), " + xPathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]")));
			safeClick(account);
			waitForUiToLoad();
		} catch (final TimeoutException ignored) {
			// Account selector is optional.
		}
	}

	private void clickByVisibleText(final String... texts) throws Exception {
		Exception lastException = null;
		for (final String text : texts) {
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(byClickableText(text)));
				safeClick(element);
				waitForUiToLoad();
				return;
			} catch (final Exception exception) {
				lastException = exception;
			}
		}

		throw new NoSuchElementException(
				"Could not click any of these labels: " + Arrays.toString(texts) + ". Last error: " + lastException);
	}

	private void clickIfVisible(final String text) throws Exception {
		final List<WebElement> elements = driver.findElements(byClickableText(text));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				safeClick(element);
				waitForUiToLoad();
				return;
			}
		}
	}

	private WebElement waitForSection(final String headingText) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//*[self::section or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(), "
						+ xPathLiteral(headingText) + ")]][1]")));
	}

	private String waitForNewWindowHandle(final Set<String> previousHandles, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			return shortWait.until(webDriver -> {
				final Set<String> currentHandles = webDriver.getWindowHandles();
				for (final String handle : currentHandles) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException ignored) {
			return null;
		}
	}

	private void waitForText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), " + xPathLiteral(text) + ")]")));
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		wait.until(webDriver -> {
			final Object inFlight = ((JavascriptExecutor) webDriver)
					.executeScript("return (window.jQuery && window.jQuery.active) ? window.jQuery.active : 0;");
			return inFlight instanceof Number && ((Number) inFlight).intValue() == 0;
		});
	}

	private boolean isVisible(final By locator) {
		try {
			return !driver.findElements(locator).isEmpty()
					&& driver.findElements(locator).stream().anyMatch(WebElement::isDisplayed);
		} catch (final Exception ignored) {
			return false;
		}
	}

	private boolean isTextVisible(final String text) {
		final List<WebElement> elements = driver
				.findElements(By.xpath("//*[contains(normalize-space(), " + xPathLiteral(text) + ")]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private WebElement firstVisibleElement(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		return null;
	}

	private boolean pageContainsEmail() {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[contains(text(), '@')]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed() && EMAIL_PATTERN.matcher(element.getText()).find()) {
				return true;
			}
		}
		return false;
	}

	private boolean hasLikelyUserName(final String sectionText) {
		for (final String line : sectionText.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.length() < 3) {
				continue;
			}
			if (trimmed.contains("@")) {
				continue;
			}
			if (trimmed.equalsIgnoreCase("Información General")) {
				continue;
			}
			if (trimmed.toUpperCase().contains("BUSINESS PLAN")) {
				continue;
			}
			if (trimmed.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}
			if (trimmed.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private boolean hasLegalContent() {
		int textLength = 0;
		final List<WebElement> elements = driver.findElements(By.xpath("//p | //li | //article | //section"));
		for (final WebElement element : elements) {
			if (!element.isDisplayed()) {
				continue;
			}
			final String text = element.getText().trim();
			if (text.length() > 25) {
				textLength += text.length();
			}
		}
		return textLength > 180;
	}

	private void safeClick(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
			element.click();
		} catch (final Exception exception) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private By byClickableText(final String text) {
		final String literal = xPathLiteral(text);
		return By.xpath("//button[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"
				+ "|//a[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"
				+ "|//*[@role='button' and (normalize-space()=" + literal + " or contains(normalize-space(), " + literal
				+ "))]" + "|//*[self::span or self::div][normalize-space()=" + literal
				+ " or contains(normalize-space(), " + literal + ")]");
	}

	private void captureScreenshot(final String screenshotName) throws IOException {
		if (driver == null || artifactDirectory == null) {
			return;
		}

		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = artifactDirectory.resolve(sanitize(screenshotName) + ".png");
		Files.copy(screenshotFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() throws IOException {
		if (reportPath == null) {
			return;
		}

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("Test name: ").append(TEST_NAME).append("\n");
		reportBuilder.append("Generated at: ").append(LocalDateTime.now()).append("\n");
		reportBuilder.append("Artifacts directory: ").append(artifactDirectory.toAbsolutePath()).append("\n\n");
		reportBuilder.append("Step Results:\n");

		for (final String field : REPORT_FIELDS) {
			final Boolean passed = stepStatus.get(field);
			final String status = passed == null ? "NOT_RUN" : (passed ? "PASS" : "FAIL");
			reportBuilder.append("- ").append(field).append(": ").append(status).append("\n");
		}

		reportBuilder.append("\nLegal URLs:\n");
		reportBuilder.append("- Términos y Condiciones: ")
				.append(legalUrls.getOrDefault("Términos y Condiciones", "NOT_CAPTURED")).append("\n");
		reportBuilder.append("- Política de Privacidad: ")
				.append(legalUrls.getOrDefault("Política de Privacidad", "NOT_CAPTURED")).append("\n");

		if (!failureDetails.isEmpty()) {
			reportBuilder.append("\nFailure Details:\n");
			for (final Map.Entry<String, String> entry : failureDetails.entrySet()) {
				reportBuilder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
			}
		}

		Files.writeString(reportPath, reportBuilder.toString(), StandardCharsets.UTF_8);
	}

	private static String xPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char current = chars[i];
			if (i > 0) {
				builder.append(",");
			}
			if (current == '\'') {
				builder.append("\"'\"");
			} else {
				builder.append("'").append(current).append("'");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private static String sanitize(final String rawValue) {
		return rawValue.replaceAll("[^a-zA-Z0-9._-]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
	}

	private static String timestamp() {
		return DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
	}

	private static String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	@FunctionalInterface
	private interface StepExecutor {
		void execute() throws Exception;
	}
}
