package io.proleap.cobol.e2e;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Environment-agnostic E2E validation for the SaleADS "Mi Negocio" workflow.
 *
 * Required env vars for automated runs:
 * - SALEADS_LOGIN_URL: current environment login URL.
 *
 * Optional env vars:
 * - SALEADS_HEADLESS: "true" or "false" (default: false).
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration LOGIN_TIMEOUT = Duration.ofSeconds(90);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(12);

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN = "Administrar Negocios view";
	private static final String STEP_INFO = "Información General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Términos y Condiciones";
	private static final String STEP_PRIVACY = "Política de Privacidad";

	private static final List<String> REPORT_FIELDS = Arrays.asList(STEP_LOGIN, STEP_MENU, STEP_MODAL, STEP_ADMIN,
			STEP_INFO, STEP_ACCOUNT_DETAILS, STEP_BUSINESSES, STEP_TERMS, STEP_PRIVACY);

	private WebDriver driver;
	private Path screenshotDirectory;
	private final Map<String, String> finalReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new HashMap<>();

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "false"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		screenshotDirectory = Paths.get("target", "saleads-e2e-screenshots",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(screenshotDirectory);

		openLoginPageIfConfigured();
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() {
		runStep(STEP_LOGIN, this::loginWithGoogleAndValidateDashboard);
		runStep(STEP_MENU, this::openMiNegocioMenuAndValidate);
		runStep(STEP_MODAL, this::validateAgregarNegocioModal);
		runStep(STEP_ADMIN, this::openAdministrarNegociosAndValidatePage);
		runStep(STEP_INFO, this::validateInformacionGeneralSection);
		runStep(STEP_ACCOUNT_DETAILS, this::validateDetallesCuentaSection);
		runStep(STEP_BUSINESSES, this::validateTusNegociosSection);
		runStep(STEP_TERMS, () -> validateLegalLink("Términos y Condiciones", "Términos y Condiciones", STEP_TERMS,
				"08-terminos-condiciones"));
		runStep(STEP_PRIVACY,
				() -> validateLegalLink("Política de Privacidad", "Política de Privacidad", STEP_PRIVACY,
						"09-politica-privacidad"));

		printFinalReport();
		assertNoFailures();
	}

	private void runStep(final String stepName, final ThrowingRunnable action) {
		try {
			action.run();
			finalReport.put(stepName, "PASS");
		} catch (final Exception e) {
			finalReport.put(stepName, "FAIL - " + conciseMessage(e));
			captureScreenshotQuietly("failure-" + sanitizeName(stepName));
		}
	}

	private void loginWithGoogleAndValidateDashboard() throws IOException {
		final Set<String> handlesBeforeLogin = new HashSet<>(driver.getWindowHandles());
		clickByVisibleText("Iniciar sesión con Google", "Sign in with Google", "Continuar con Google",
				"Login with Google", "Google");
		selectGoogleAccountIfShown(handlesBeforeLogin, GOOGLE_ACCOUNT_EMAIL);

		waitForAnyVisibleText(LOGIN_TIMEOUT, "Negocio", "Mi Negocio");
		captureScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenuAndValidate() throws IOException {
		openMiNegocioMenu();
		waitForVisibleText(DEFAULT_TIMEOUT, "Agregar Negocio");
		waitForVisibleText(DEFAULT_TIMEOUT, "Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForVisibleText(DEFAULT_TIMEOUT, "Crear Nuevo Negocio");
		waitForVisibleText(DEFAULT_TIMEOUT, "Nombre del Negocio");
		waitForVisibleText(DEFAULT_TIMEOUT, "Tienes 2 de 3 negocios");
		waitForVisibleText(DEFAULT_TIMEOUT, "Cancelar");
		waitForVisibleText(DEFAULT_TIMEOUT, "Crear Negocio");

		final WebElement input = waitForVisibleElement(DEFAULT_TIMEOUT,
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"
						+ " | //input[contains(@placeholder, 'Nombre del Negocio')]"
						+ " | //input[contains(@aria-label, 'Nombre del Negocio')]"));
		input.clear();
		input.sendKeys("Negocio Prueba Automatización");
		captureScreenshot("03-agregar-negocio-modal");

		clickByVisibleText("Cancelar");
		waitForTextToDisappear("Crear Nuevo Negocio");
	}

	private void openAdministrarNegociosAndValidatePage() throws IOException {
		openMiNegocioMenu();
		clickByVisibleText("Administrar Negocios");

		waitForVisibleText(DEFAULT_TIMEOUT, "Información General");
		waitForVisibleText(DEFAULT_TIMEOUT, "Detalles de la Cuenta");
		waitForVisibleText(DEFAULT_TIMEOUT, "Tus Negocios");
		waitForVisibleText(DEFAULT_TIMEOUT, "Sección Legal");
		captureScreenshot("04-administrar-negocios-page");
	}

	private void validateInformacionGeneralSection() {
		waitForVisibleText(DEFAULT_TIMEOUT, "Información General");
		final WebElement emailElement = waitForVisibleElement(DEFAULT_TIMEOUT, By.xpath("//*[contains(text(), '@')]"));
		final String emailText = normalize(emailElement.getText());
		if (emailText.isEmpty()) {
			throw new IllegalStateException("User email was not visible in Información General.");
		}

		final String surroundingText = normalize(
				emailElement.findElement(By.xpath("./ancestor::*[self::section or self::div][1]")).getText());
		if (!containsCandidateName(surroundingText, emailText)) {
			throw new IllegalStateException("User name was not found near the email in Información General.");
		}

		waitForVisibleText(DEFAULT_TIMEOUT, "BUSINESS PLAN");
		waitForVisibleText(DEFAULT_TIMEOUT, "Cambiar Plan");
	}

	private void validateDetallesCuentaSection() {
		waitForVisibleText(DEFAULT_TIMEOUT, "Detalles de la Cuenta");
		waitForVisibleText(DEFAULT_TIMEOUT, "Cuenta creada");
		waitForVisibleText(DEFAULT_TIMEOUT, "Estado activo");
		waitForVisibleText(DEFAULT_TIMEOUT, "Idioma seleccionado");
	}

	private void validateTusNegociosSection() {
		waitForVisibleText(DEFAULT_TIMEOUT, "Tus Negocios");
		waitForVisibleText(DEFAULT_TIMEOUT, "Agregar Negocio");
		waitForVisibleText(DEFAULT_TIMEOUT, "Tienes 2 de 3 negocios");

		final WebElement businessEntry = waitForVisibleElement(DEFAULT_TIMEOUT,
				By.xpath("//section//*[contains(normalize-space(.), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]"
						+ "//*[self::li or self::tr or self::article or self::div][normalize-space()][1]"
						+ " | //*[contains(normalize-space(.), 'Tus Negocios')]/following::*[self::li or self::tr or self::article][1]"));
		if (normalize(businessEntry.getText()).isEmpty()) {
			throw new IllegalStateException("Business list is not visible.");
		}
	}

	private void validateLegalLink(final String linkText, final String headingText, final String reportField,
			final String screenshotName) throws IOException {
		waitForVisibleText(DEFAULT_TIMEOUT, "Sección Legal");

		final String appHandle = driver.getWindowHandle();
		final String currentUrl = safeCurrentUrl();
		final Set<String> handlesBeforeClick = new HashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);

		final String newTabHandle = waitForNewWindow(handlesBeforeClick, SHORT_TIMEOUT);
		if (newTabHandle != null) {
			driver.switchTo().window(newTabHandle);
			waitForUiToLoad();
		}

		if (newTabHandle == null) {
			waitForUrlChange(currentUrl, DEFAULT_TIMEOUT);
		}

		waitForVisibleText(DEFAULT_TIMEOUT, headingText);
		waitForVisibleLegalContent();
		captureScreenshot(screenshotName);

		legalUrls.put(reportField, safeCurrentUrl());

		if (newTabHandle != null) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
			waitForVisibleText(DEFAULT_TIMEOUT, "Sección Legal");
		}
	}

	private void selectGoogleAccountIfShown(final Set<String> handlesBeforeLogin, final String accountEmail) {
		final String mainHandle = driver.getWindowHandle();
		final String popupHandle = waitForNewWindow(handlesBeforeLogin, SHORT_TIMEOUT);

		if (popupHandle != null) {
			driver.switchTo().window(popupHandle);
			waitForUiToLoad();
		}

		try {
			clickByVisibleText(Duration.ofSeconds(10), accountEmail);
			waitForUiToLoad();
		} catch (final Exception ignored) {
			// Account chooser may not appear in already-authenticated sessions.
		}

		if (popupHandle != null) {
			waitForGooglePopupToCloseOrReturn(mainHandle);
		}
	}

	private void waitForGooglePopupToCloseOrReturn(final String mainHandle) {
		try {
			new WebDriverWait(driver, DEFAULT_TIMEOUT).until((ExpectedCondition<Boolean>) d -> {
				if (d == null) {
					return false;
				}

				if (!d.getWindowHandles().contains(mainHandle)) {
					return false;
				}

				if (d.getWindowHandle().equals(mainHandle)) {
					return true;
				}

				if (d.getWindowHandles().size() == 1) {
					d.switchTo().window(mainHandle);
					return true;
				}

				return false;
			});
		} catch (final TimeoutException ignored) {
			// If popup is still open, continue with best-effort switch.
		}

		driver.switchTo().window(mainHandle);
		waitForUiToLoad();
	}

	private void openMiNegocioMenu() {
		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			try {
				clickByVisibleText(Duration.ofSeconds(8), "Negocio");
			} catch (final Exception ignored) {
				// Some environments have "Mi Negocio" directly in the sidebar.
			}

			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}
	}

	private void clickByVisibleText(final String... textCandidates) {
		clickByVisibleText(DEFAULT_TIMEOUT, textCandidates);
	}

	private void clickByVisibleText(final Duration timeout, final String... textCandidates) {
		final WebElement element = waitForAnyClickableByText(timeout, textCandidates);
		try {
			element.click();
		} catch (final StaleElementReferenceException e) {
			final WebElement refreshedElement = waitForAnyClickableByText(timeout, textCandidates);
			refreshedElement.click();
		}
		waitForUiToLoad();
	}

	private WebElement waitForAnyClickableByText(final Duration timeout, final String... textCandidates) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		return localWait.until(d -> {
			if (d == null) {
				return null;
			}

			for (final String textCandidate : textCandidates) {
				final By locator = By.xpath("("
						+ "//*[@role='button' or self::button or self::a][contains(normalize-space(.), "
						+ xpathLiteral(textCandidate) + ")]"
						+ " | //* [contains(normalize-space(.), " + xpathLiteral(textCandidate)
						+ ")]/ancestor-or-self::*[@role='button' or self::button or self::a][1]" + ")");
				final List<WebElement> matches = d.findElements(locator);
				for (final WebElement match : matches) {
					if (match.isDisplayed() && match.isEnabled()) {
						return match;
					}
				}
			}
			return null;
		});
	}

	private WebElement waitForVisibleText(final Duration timeout, final String text) {
		return waitForVisibleElement(timeout,
				By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]"));
	}

	private WebElement waitForAnyVisibleText(final Duration timeout, final String... textCandidates) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		return localWait.until(d -> {
			if (d == null) {
				return null;
			}

			for (final String textCandidate : textCandidates) {
				final List<WebElement> candidates = d
						.findElements(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(textCandidate) + ")]"));
				for (final WebElement candidate : candidates) {
					if (candidate.isDisplayed()) {
						return candidate;
					}
				}
			}

			return null;
		});
	}

	private WebElement waitForVisibleElement(final Duration timeout, final By locator) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		return localWait.until(d -> {
			if (d == null) {
				return null;
			}

			final List<WebElement> elements = d.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private void waitForVisibleLegalContent() {
		final WebDriverWait localWait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		final WebElement legalContent = localWait.until(d -> {
			if (d == null) {
				return null;
			}

			final List<WebElement> candidates = d.findElements(By.xpath(
					"//main//*[self::p or self::li][string-length(normalize-space(.)) > 40]"
							+ " | //article//*[self::p or self::li][string-length(normalize-space(.)) > 40]"
							+ " | //section//*[self::p or self::li][string-length(normalize-space(.)) > 40]"));
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					return candidate;
				}
			}
			return null;
		});

		if (legalContent == null) {
			throw new IllegalStateException("Legal content text is not visible.");
		}
	}

	private void waitForTextToDisappear(final String text) {
		final WebDriverWait localWait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		localWait.until(d -> {
			if (d == null) {
				return false;
			}

			final List<WebElement> matches = d
					.findElements(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]"));
			for (final WebElement match : matches) {
				if (match.isDisplayed()) {
					return false;
				}
			}
			return true;
		});
	}

	private void waitForUrlChange(final String currentUrl, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		localWait.until(d -> d != null && !safeCurrentUrl().equals(currentUrl));
	}

	private String waitForNewWindow(final Set<String> handlesBeforeClick, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		try {
			return localWait.until(d -> {
				if (d == null) {
					return null;
				}

				final Set<String> currentHandles = new HashSet<>(d.getWindowHandles());
				currentHandles.removeAll(handlesBeforeClick);
				if (currentHandles.isEmpty()) {
					return null;
				}
				return currentHandles.iterator().next();
			});
		} catch (final TimeoutException e) {
			return null;
		}
	}

	private void waitForUiToLoad() {
		final WebDriverWait localWait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		localWait.until(d -> {
			if (d == null) {
				return false;
			}

			return "complete"
					.equals(((JavascriptExecutor) d).executeScript("return document.readyState"));
		});
	}

	private void openLoginPageIfConfigured() {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl.trim());
			return;
		}

		final String currentUrl = safeCurrentUrl();
		if (isBlankBrowserPage(currentUrl)) {
			throw new IllegalStateException(
					"Set SALEADS_LOGIN_URL to run this test when starting from a blank browser page.");
		}
	}

	private boolean isBlankBrowserPage(final String url) {
		return url == null || url.isBlank() || "about:blank".equals(url) || "data:,".equals(url);
	}

	private boolean isTextVisible(final String text) {
		final List<WebElement> matches = driver
				.findElements(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]"));
		for (final WebElement match : matches) {
			if (match.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean containsCandidateName(final String surroundingText, final String email) {
		final List<String> ignoredFragments = Arrays.asList(email, "BUSINESS PLAN", "Cambiar Plan", "Información General");
		for (final String line : surroundingText.split("\\R")) {
			final String normalizedLine = normalize(line);
			if (normalizedLine.length() < 3 || normalizedLine.contains("@")) {
				continue;
			}

			boolean ignored = false;
			for (final String ignoredFragment : ignoredFragments) {
				if (normalizedLine.equalsIgnoreCase(normalize(ignoredFragment))) {
					ignored = true;
					break;
				}
			}

			if (!ignored) {
				return true;
			}
		}
		return false;
	}

	private void captureScreenshot(final String screenshotName) throws IOException {
		final Path targetFile = screenshotDirectory.resolve(sanitizeName(screenshotName) + ".png");
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(targetFile, screenshot);
	}

	private void captureScreenshotQuietly(final String screenshotName) {
		try {
			captureScreenshot(screenshotName);
		} catch (final IOException ignored) {
			// Keep original failure details if screenshot writing fails.
		}
	}

	private void printFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append(System.lineSeparator()).append("SaleADS Mi Negocio workflow result:")
				.append(System.lineSeparator());
		for (final String reportField : REPORT_FIELDS) {
			builder.append("- ").append(reportField).append(": ")
					.append(finalReport.getOrDefault(reportField, "FAIL - step did not run"))
					.append(System.lineSeparator());
			if (legalUrls.containsKey(reportField)) {
				builder.append("  URL: ").append(legalUrls.get(reportField)).append(System.lineSeparator());
			}
		}
		builder.append("Screenshots: ").append(screenshotDirectory.toAbsolutePath()).append(System.lineSeparator());
		System.out.println(builder.toString());
	}

	private void assertNoFailures() {
		final List<String> failures = new ArrayList<>();
		for (final String reportField : REPORT_FIELDS) {
			final String result = finalReport.getOrDefault(reportField, "FAIL - step did not run");
			if (result.startsWith("FAIL")) {
				failures.add(reportField + ": " + result);
			}
		}

		if (!failures.isEmpty()) {
			fail("Workflow failures:" + System.lineSeparator() + String.join(System.lineSeparator(), failures)
					+ System.lineSeparator() + "Screenshots: " + screenshotDirectory.toAbsolutePath());
		}
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (final Exception e) {
			return "";
		}
	}

	private String conciseMessage(final Exception exception) {
		final String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}
		return normalize(message).substring(0, Math.min(220, normalize(message).length()));
	}

	private static String sanitizeName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static String normalize(final String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ");
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder xpathBuilder = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			xpathBuilder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				xpathBuilder.append(", \"'\", ");
			}
		}
		xpathBuilder.append(")");
		return xpathBuilder.toString();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
