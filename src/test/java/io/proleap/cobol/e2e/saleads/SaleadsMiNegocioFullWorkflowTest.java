package io.proleap.cobol.e2e.saleads;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow validation for SaleADS Mi Negocio module.
 *
 * <p>Configuration:</p>
 * <ul>
 *   <li>SALEADS_LOGIN_URL or -Dsaleads.login.url (required)</li>
 *   <li>SALEADS_BROWSER or -Dsaleads.browser (default: chrome)</li>
 *   <li>SALEADS_HEADLESS or -Dsaleads.headless (default: true)</li>
 *   <li>SALEADS_TIMEOUT_SECONDS or -Dsaleads.timeout.seconds (default: 25)</li>
 *   <li>SALEADS_EXPECTED_USER_NAME or -Dsaleads.expected.user.name (optional)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[A-Za-z]{2,}");

	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, StepOutcome> outcomes = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		driver = createDriver();
		final long timeoutSeconds = Long.parseLong(config("SALEADS_TIMEOUT_SECONDS", "saleads.timeout.seconds", "25"));
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence", runId);
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() throws IOException {
		ensureReportFields();
		writeReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final boolean loginOk = runStep("Login", this::stepLoginWithGoogle);

		if (!loginOk) {
			markBlocked("Mi Negocio menu", "Login failed");
			markBlocked("Agregar Negocio modal", "Login failed");
			markBlocked("Administrar Negocios view", "Login failed");
			markBlocked("Información General", "Login failed");
			markBlocked("Detalles de la Cuenta", "Login failed");
			markBlocked("Tus Negocios", "Login failed");
			markBlocked("Términos y Condiciones", "Login failed");
			markBlocked("Política de Privacidad", "Login failed");
			Assert.fail("Login failed. See evidence and report artifacts.");
		}

		final boolean menuOk = runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		if (!menuOk) {
			markBlocked("Agregar Negocio modal", "Mi Negocio menu failed");
			markBlocked("Administrar Negocios view", "Mi Negocio menu failed");
			markBlocked("Información General", "Administrar Negocios view blocked");
			markBlocked("Detalles de la Cuenta", "Administrar Negocios view blocked");
			markBlocked("Tus Negocios", "Administrar Negocios view blocked");
			markBlocked("Términos y Condiciones", "Administrar Negocios view blocked");
			markBlocked("Política de Privacidad", "Administrar Negocios view blocked");
			Assert.fail("Mi Negocio menu step failed. See evidence and report artifacts.");
		}

		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);

		final boolean administrarOk = runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		if (!administrarOk) {
			markBlocked("Información General", "Administrar Negocios view failed");
			markBlocked("Detalles de la Cuenta", "Administrar Negocios view failed");
			markBlocked("Tus Negocios", "Administrar Negocios view failed");
			markBlocked("Términos y Condiciones", "Administrar Negocios view failed");
			markBlocked("Política de Privacidad", "Administrar Negocios view failed");
			Assert.fail("Administrar Negocios view step failed. See evidence and report artifacts.");
		}

		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		ensureReportFields();
		Assert.assertTrue("One or more validations failed. See target/saleads-mi-negocio-final-report.md", allPassed());
	}

	private void stepLoginWithGoogle() throws IOException {
		final String loginUrl = config("SALEADS_LOGIN_URL", "saleads.login.url", "");
		Assert.assertFalse("SALEADS_LOGIN_URL or -Dsaleads.login.url is required.", loginUrl.isBlank());

		driver.get(loginUrl);
		waitForUiToLoad();

		final Set<String> initialHandles = driver.getWindowHandles();
		clickFirstVisibleText(Arrays.asList(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Continuar con Google",
				"Continue with Google",
				"Google"), false);

		selectGoogleAccountIfPrompted(initialHandles);
		waitForMainApplication();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		expandMiNegocioMenu();
		Assert.assertTrue("Expected submenu option 'Agregar Negocio' to be visible.", isTextVisible("Agregar Negocio"));
		Assert.assertTrue("Expected submenu option 'Administrar Negocios' to be visible.",
				isTextVisible("Administrar Negocios"));
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickFirstVisibleText(Collections.singletonList("Agregar Negocio"), true);

		assertTextVisible("Crear Nuevo Negocio");
		findVisibleElementByText("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		final WebElement negocioInput = findBusinessNameInput();
		negocioInput.click();
		negocioInput.clear();
		negocioInput.sendKeys("Negocio Prueba Automatización");
		waitForUiToLoad();

		captureScreenshot("03-agregar-negocio-modal");

		clickFirstVisibleText(Collections.singletonList("Cancelar"), true);
		wait.until(ExpectedConditions.invisibilityOfElementLocated(textLocator("Crear Nuevo Negocio")));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioMenu();
		clickFirstVisibleText(Collections.singletonList("Administrar Negocios"), true);

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");

		captureScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		final String sectionText = normalizeWhitespace(section.getText());

		Assert.assertTrue("Expected 'BUSINESS PLAN' in Información General.",
				containsIgnoreCase(sectionText, "BUSINESS PLAN"));
		Assert.assertTrue("Expected button/text 'Cambiar Plan'.", isTextVisible("Cambiar Plan"));

		final String expectedUserName = config("SALEADS_EXPECTED_USER_NAME", "saleads.expected.user.name", "");
		if (!expectedUserName.isBlank()) {
			Assert.assertTrue("Expected configured user name was not visible: " + expectedUserName,
					containsIgnoreCase(driver.findElement(By.tagName("body")).getText(), expectedUserName));
		} else {
			Assert.assertTrue("User name is not clearly visible in Información General.",
					hasLikelyDisplayedName(sectionText));
		}

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("User email is not visible in the account view.",
				EMAIL_PATTERN.matcher(sectionText).find() || EMAIL_PATTERN.matcher(bodyText).find());
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		final String sectionText = normalizeWhitespace(section.getText());

		Assert.assertTrue("Expected 'Cuenta creada' label.", containsIgnoreCase(sectionText, "Cuenta creada"));
		Assert.assertTrue("Expected 'Estado activo' label.", containsIgnoreCase(sectionText, "Estado activo"));
		Assert.assertTrue("Expected 'Idioma seleccionado' label.",
				containsIgnoreCase(sectionText, "Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final String sectionText = normalizeWhitespace(section.getText());

		Assert.assertTrue("Expected button/text 'Agregar Negocio'.", containsIgnoreCase(sectionText, "Agregar Negocio"));
		Assert.assertTrue("Expected quota text 'Tienes 2 de 3 negocios'.",
				containsIgnoreCase(sectionText, "Tienes 2 de 3 negocios"));

		final List<String> meaningfulLines = extractMeaningfulLines(sectionText, Arrays.asList(
				"tus negocios",
				"agregar negocio",
				"tienes 2 de 3 negocios"));
		Assert.assertFalse("Business list is not visible in 'Tus Negocios' section.", meaningfulLines.isEmpty());
	}

	private void stepValidateTerminosCondiciones() throws IOException {
		final String finalUrl = openLegalDocument(
				"Términos y Condiciones",
				"Términos y Condiciones",
				"08-terminos-condiciones");
		legalUrls.put("Términos y Condiciones", finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		final String finalUrl = openLegalDocument(
				"Política de Privacidad",
				"Política de Privacidad",
				"09-politica-privacidad");
		legalUrls.put("Política de Privacidad", finalUrl);
	}

	private String openLegalDocument(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String initialUrl = driver.getCurrentUrl();

		clickFirstVisibleText(Collections.singletonList(linkText), false);
		waitForUiToLoad();

		final String newHandle = waitForNewWindow(handlesBeforeClick, 10);
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiToLoad();
		} else {
			try {
				wait.until(d -> !d.getCurrentUrl().equals(initialUrl));
			} catch (final TimeoutException ignored) {
				// Legal content may load via in-page transition without URL change.
			}
			waitForUiToLoad();
		}

		assertTextVisible(headingText);
		final String legalText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		Assert.assertTrue("Legal content text is not visible for: " + headingText, legalText.length() > 120);

		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (!driver.getWindowHandle().equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		waitForAnyVisibleText(Arrays.asList("Sección Legal", "Información General", "Tus Negocios"));

		return finalUrl;
	}

	private WebDriver createDriver() {
		final String browser = config("SALEADS_BROWSER", "saleads.browser", "chrome").toLowerCase(Locale.ROOT).trim();
		final boolean headless = Boolean.parseBoolean(config("SALEADS_HEADLESS", "saleads.headless", "true"));

		if ("firefox".equals(browser)) {
			final FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			return new FirefoxDriver(options);
		}

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (headless) {
			options.addArguments("--headless=new");
		}
		return new ChromeDriver(options);
	}

	private void expandMiNegocioMenu() {
		if (isTextVisible("Agregar Negocio") && isTextVisible("Administrar Negocios")) {
			return;
		}

		clickTextIfPresent(Collections.singletonList("Negocio"));
		waitForUiToLoad();

		clickFirstVisibleText(Collections.singletonList("Mi Negocio"), true);

		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			clickTextIfPresent(Collections.singletonList("Mi Negocio"));
			waitForUiToLoad();
		}
	}

	private void selectGoogleAccountIfPrompted(final Set<String> handlesBeforeLoginClick) {
		final String appWindow = driver.getWindowHandle();
		final String popupWindow = waitForNewWindow(handlesBeforeLoginClick, 8);

		if (popupWindow != null) {
			driver.switchTo().window(popupWindow);
			waitForUiToLoad();
		}

		clickTextIfPresent(Collections.singletonList(GOOGLE_ACCOUNT_EMAIL));
		waitForUiToLoad();

		if (!driver.getWindowHandle().equals(appWindow)) {
			if (driver.getWindowHandles().contains(appWindow)) {
				driver.switchTo().window(appWindow);
			}
		}
	}

	private void waitForMainApplication() {
		waitForAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio", "Dashboard"));
		Assert.assertTrue("Main application sidebar is not visible.", isSidebarVisible());
	}

	private boolean isSidebarVisible() {
		final List<By> sidebarLocators = Arrays.asList(
				By.cssSelector("aside"),
				By.cssSelector("nav"),
				By.cssSelector("[class*='sidebar']"),
				By.cssSelector("[id*='sidebar']"));

		for (final By locator : sidebarLocators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (isDisplayed(element)) {
					return true;
				}
			}
		}

		return false;
	}

	private WebElement findBusinessNameInput() {
		final By inputLocator = By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio']"
						+ " | //label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(inputLocator));
	}

	private WebElement findSectionByHeading(final String headingText) {
		final WebElement heading = findVisibleElementByText(headingText);
		try {
			return heading.findElement(By.xpath(
					"./ancestor::*[self::section or self::article or self::div][1]"));
		} catch (final NoSuchElementException ignored) {
			return heading;
		}
	}

	private boolean runStep(final String stepName, final CheckedAction action) {
		try {
			action.run();
			outcomes.put(stepName, StepOutcome.pass());
			return true;
		} catch (final Throwable t) {
			outcomes.put(stepName, StepOutcome.fail(cleanErrorMessage(t)));
			captureScreenshotSafe("failure-" + sanitizeFileSegment(stepName));
			return false;
		}
	}

	private void markBlocked(final String stepName, final String reason) {
		if (!outcomes.containsKey(stepName)) {
			outcomes.put(stepName, StepOutcome.fail("Blocked: " + reason));
		}
	}

	private boolean allPassed() {
		for (final String reportField : REPORT_FIELDS) {
			final StepOutcome outcome = outcomes.get(reportField);
			if (outcome == null || !"PASS".equals(outcome.status)) {
				return false;
			}
		}
		return true;
	}

	private void ensureReportFields() {
		for (final String reportField : REPORT_FIELDS) {
			if (!outcomes.containsKey(reportField)) {
				outcomes.put(reportField, StepOutcome.fail("Not executed."));
			}
		}
	}

	private void writeReport() throws IOException {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("# SaleADS Mi Negocio Full Test Report\n\n");
		reportBuilder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append("\n\n");
		reportBuilder.append("| Validation | Status | Details |\n");
		reportBuilder.append("|---|---|---|\n");

		for (final String reportField : REPORT_FIELDS) {
			final StepOutcome outcome = outcomes.get(reportField);
			final String status = outcome == null ? "FAIL" : outcome.status;
			final String detail = outcome == null ? "Not executed." : sanitizeReportText(outcome.detail);
			reportBuilder.append("| ").append(reportField).append(" | ").append(status).append(" | ")
					.append(detail).append(" |\n");
		}

		reportBuilder.append("\n## Final URLs\n");
		reportBuilder.append("- Términos y Condiciones: ")
				.append(legalUrls.getOrDefault("Términos y Condiciones", "N/A")).append("\n");
		reportBuilder.append("- Política de Privacidad: ")
				.append(legalUrls.getOrDefault("Política de Privacidad", "N/A")).append("\n");

		final byte[] reportBytes = reportBuilder.toString().getBytes(StandardCharsets.UTF_8);

		final Path rootReport = Paths.get("target", "saleads-mi-negocio-final-report.md");
		Files.createDirectories(rootReport.getParent());
		Files.write(rootReport, reportBytes);

		final Path runReport = evidenceDir.resolve("final-report.md");
		Files.write(runReport, reportBytes);
	}

	private void captureScreenshot(final String name) throws IOException {
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final Path screenshotPath = evidenceDir.resolve(sanitizeFileSegment(name) + ".png");
		Files.copy(new java.io.ByteArrayInputStream(screenshot), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureScreenshotSafe(final String name) {
		try {
			captureScreenshot(name);
		} catch (final Throwable ignored) {
			// ignore screenshot failures in cleanup path
		}
	}

	private void clickFirstVisibleText(final List<String> candidateTexts, final boolean waitAfterClick) {
		Throwable lastError = null;
		for (final String candidateText : candidateTexts) {
			final String xpathLiteral = toXPathLiteral(candidateText);
			final By primaryLocator = By.xpath(
					"//button[normalize-space()=" + xpathLiteral + " or contains(normalize-space(), " + xpathLiteral + ")]"
							+ " | //a[normalize-space()=" + xpathLiteral + " or contains(normalize-space(), " + xpathLiteral + ")]"
							+ " | //*[@role='button' and (normalize-space()=" + xpathLiteral
							+ " or contains(normalize-space(), " + xpathLiteral + "))]"
							+ " | //*[(self::span or self::div or self::p or self::li) and normalize-space()="
							+ xpathLiteral + "]");

			final List<WebElement> candidates = driver.findElements(primaryLocator);
			for (final WebElement element : candidates) {
				if (isDisplayed(element)) {
					try {
						clickElement(element);
						if (waitAfterClick) {
							waitForUiToLoad();
						}
						return;
					} catch (final Throwable t) {
						lastError = t;
					}
				}
			}

			final List<WebElement> fallbackCandidates = driver.findElements(textLocator(candidateText));
			for (final WebElement element : fallbackCandidates) {
				if (isDisplayed(element)) {
					try {
						clickElement(element);
						if (waitAfterClick) {
							waitForUiToLoad();
						}
						return;
					} catch (final Throwable t) {
						lastError = t;
					}
				}
			}
		}

		throw new NoSuchElementException("Unable to click visible element by text candidates: " + candidateTexts
				+ (lastError == null ? "" : " (last error: " + cleanErrorMessage(lastError) + ")"));
	}

	private boolean clickTextIfPresent(final List<String> candidateTexts) {
		for (final String candidateText : candidateTexts) {
			final List<WebElement> elements = driver.findElements(textLocator(candidateText));
			for (final WebElement element : elements) {
				if (isDisplayed(element)) {
					try {
						clickElement(element);
						return true;
					} catch (final Throwable ignored) {
						// try next candidate
					}
				}
			}
		}

		return false;
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Throwable clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final TimeoutException ignored) {
			// some SPAs remain interactive before complete; keep going
		}

		try {
			wait.until(d -> {
				final List<WebElement> loaders = d.findElements(By.cssSelector(
						"[aria-busy='true'], .loading, .loader, .spinner, .skeleton, [role='progressbar']"));
				for (final WebElement loader : loaders) {
					if (isDisplayed(loader)) {
						return false;
					}
				}
				return true;
			});
		} catch (final TimeoutException ignored) {
			// continue if no explicit loader detection is possible
		}
	}

	private void waitForAnyVisibleText(final List<String> candidateTexts) {
		wait.until(d -> {
			for (final String text : candidateTexts) {
				if (!d.findElements(textLocator(text)).isEmpty() && isTextVisible(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private WebElement findVisibleElementByText(final String text) {
		final By locator = textLocator(text);
		wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		for (final WebElement element : driver.findElements(locator)) {
			if (isDisplayed(element)) {
				return element;
			}
		}
		throw new NoSuchElementException("Visible element not found for text: " + text);
	}

	private void assertTextVisible(final String text) {
		Assert.assertTrue("Text not visible: " + text, isTextVisible(text));
	}

	private boolean isTextVisible(final String text) {
		final By locator = textLocator(text);
		try {
			for (final WebElement element : driver.findElements(locator)) {
				if (isDisplayed(element)) {
					return true;
				}
			}
		} catch (final Throwable ignored) {
			// return false below
		}
		return false;
	}

	private By textLocator(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]");
	}

	private String waitForNewWindow(final Set<String> existingHandles, final int timeoutSeconds) {
		try {
			return new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(d -> {
				for (final String handle : d.getWindowHandles()) {
					if (!existingHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException e) {
			return null;
		}
	}

	private String config(final String envName, final String propertyName, final String defaultValue) {
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		return defaultValue;
	}

	private boolean containsIgnoreCase(final String text, final String target) {
		return text.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
	}

	private boolean hasLikelyDisplayedName(final String sectionText) {
		final List<String> forbiddenTokens = Arrays.asList(
				"información general",
				"business plan",
				"cambiar plan",
				"nombre",
				"correo",
				"email");

		for (final String line : sectionText.split("\\R")) {
			final String candidate = line.trim();
			if (candidate.isEmpty() || candidate.length() < 3 || candidate.length() > 60) {
				continue;
			}

			if (candidate.contains("@")) {
				continue;
			}

			String normalized = candidate.toLowerCase(Locale.ROOT);
			boolean forbidden = false;
			for (final String token : forbiddenTokens) {
				if (normalized.contains(token)) {
					forbidden = true;
					break;
				}
			}

			if (!forbidden) {
				return true;
			}
		}

		return false;
	}

	private List<String> extractMeaningfulLines(final String sectionText, final List<String> excludedLines) {
		final List<String> lines = new ArrayList<>();
		for (final String rawLine : sectionText.split("\\R")) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}

			boolean excluded = false;
			final String lower = line.toLowerCase(Locale.ROOT);
			for (final String excludedToken : excludedLines) {
				if (lower.equals(excludedToken.toLowerCase(Locale.ROOT))) {
					excluded = true;
					break;
				}
			}
			if (!excluded) {
				lines.add(line);
			}
		}
		return lines;
	}

	private String normalizeWhitespace(final String text) {
		return text == null ? "" : text.replace('\u00A0', ' ').trim();
	}

	private boolean isDisplayed(final WebElement element) {
		try {
			return element.isDisplayed();
		} catch (final Throwable ignored) {
			return false;
		}
	}

	private String cleanErrorMessage(final Throwable throwable) {
		if (throwable == null) {
			return "Unknown error.";
		}
		final String message = throwable.getMessage();
		if (message == null || message.isBlank()) {
			return throwable.getClass().getSimpleName();
		}
		return sanitizeReportText(message);
	}

	private String sanitizeReportText(final String text) {
		return text.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
	}

	private String sanitizeFileSegment(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-")
				.replaceAll("(^-|-$)", "");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char character = chars[i];
			if (character == '\'') {
				result.append("\"'\"");
			} else if (character == '"') {
				result.append("'\"'");
			} else {
				result.append("'").append(character).append("'");
			}

			if (i < chars.length - 1) {
				result.append(", ");
			}
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface CheckedAction {
		void run() throws Exception;
	}

	private static final class StepOutcome {
		private final String status;
		private final String detail;

		private StepOutcome(final String status, final String detail) {
			this.status = status;
			this.detail = detail;
		}

		private static StepOutcome pass() {
			return new StepOutcome("PASS", "OK");
		}

		private static StepOutcome fail(final String detail) {
			return new StepOutcome("FAIL", detail == null || detail.isBlank() ? "Validation failed." : detail);
		}
	}
}
