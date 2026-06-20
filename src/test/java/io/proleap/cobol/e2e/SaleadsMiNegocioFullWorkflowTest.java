package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow test for SaleADS.ai "Mi Negocio" module.
 *
 * <p>
 * This test intentionally avoids hardcoded domains. Provide the login URL through
 * SALEADS_LOGIN_URL or -Dsaleads.login.url.
 * </p>
 *
 * <p>
 * Optional runtime settings:
 * </p>
 * <ul>
 * <li>SALEADS_BROWSER / -Dsaleads.browser (chrome|firefox|edge)</li>
 * <li>SALEADS_HEADLESS / -Dsaleads.headless (true|false)</li>
 * <li>SALEADS_GOOGLE_ACCOUNT / -Dsaleads.google.account (default:
 * juanlucasbarbiergarzon@gmail.com)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(6);
	private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(45);
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

	private static final List<String> REPORT_FIELDS = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepStatus> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		for (final String field : REPORT_FIELDS) {
			report.put(field, StepStatus.fail("NOT EXECUTED"));
		}

		driver = createDriver();
		driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence",
				LocalDateTime.now(ZoneOffset.UTC).format(TIMESTAMP));
		Files.createDirectories(evidenceDir);

		final String loginUrl = requiredConfig("SALEADS_LOGIN_URL", "saleads.login.url");
		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		try {
			final boolean loginOk = runStep("Login", true, this::loginWithGoogle);
			final boolean menuOk = runStep("Mi Negocio menu", loginOk, this::openMiNegocioMenu);
			runStep("Agregar Negocio modal", menuOk, this::validateAgregarNegocioModal);

			final boolean administrarOk = runStep("Administrar Negocios view", menuOk, this::openAdministrarNegocios);
			runStep("Información General", administrarOk, this::validateInformacionGeneral);
			runStep("Detalles de la Cuenta", administrarOk, this::validateDetallesCuenta);
			runStep("Tus Negocios", administrarOk, this::validateTusNegocios);
			runStep("Términos y Condiciones", administrarOk,
					() -> validateLegalPage("Términos y Condiciones", "08-terminos-y-condiciones"));
			runStep("Política de Privacidad", administrarOk,
					() -> validateLegalPage("Política de Privacidad", "09-politica-de-privacidad"));
		} finally {
			writeFinalReport();
		}

		assertTrue("One or more workflow validations failed. See report at: " + evidenceDir.resolve("final-report.md"),
				report.values().stream().allMatch(StepStatus::isPass));
	}

	private void loginWithGoogle() throws IOException {
		clickFirstVisibleByText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Google");

		final String accountEmail = optionalConfig("SALEADS_GOOGLE_ACCOUNT", "saleads.google.account",
				"juanlucasbarbiergarzon@gmail.com");
		selectGoogleAccountIfPrompted(accountEmail);

		waitForVisibleText(DEFAULT_TIMEOUT, "Negocio", "Mi Negocio", "Dashboard", "Panel");
		assertElementVisible(findSidebar(), "Left sidebar navigation is not visible.");
		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws IOException {
		assertElementVisible(findSidebar(), "Left sidebar navigation is not visible.");
		clickFirstVisibleByText("Negocio");
		clickFirstVisibleByText("Mi Negocio");
		waitForVisibleText(DEFAULT_TIMEOUT, "Agregar Negocio");
		waitForVisibleText(DEFAULT_TIMEOUT, "Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickFirstVisibleByText("Agregar Negocio");
		waitForVisibleText(DEFAULT_TIMEOUT, "Crear Nuevo Negocio");
		waitForVisibleText(DEFAULT_TIMEOUT, "Nombre del Negocio");
		waitForVisibleText(DEFAULT_TIMEOUT, "Tienes 2 de 3 negocios");
		waitForVisibleText(DEFAULT_TIMEOUT, "Cancelar");
		waitForVisibleText(DEFAULT_TIMEOUT, "Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal");

		tryTypeInBusinessNameField("Negocio Prueba Automatización");
		clickFirstVisibleByText("Cancelar");
		waitUntilTextNotVisible("Crear Nuevo Negocio");
	}

	private void openAdministrarNegocios() throws IOException {
		expandMiNegocioIfNeeded();
		clickFirstVisibleByText("Administrar Negocios");
		waitForVisibleText(DEFAULT_TIMEOUT, "Información General");
		waitForVisibleText(DEFAULT_TIMEOUT, "Detalles de la Cuenta");
		waitForVisibleText(DEFAULT_TIMEOUT, "Tus Negocios");
		waitForVisibleText(DEFAULT_TIMEOUT, "Sección Legal");

		takeFullPageScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		waitForVisibleText(DEFAULT_TIMEOUT, "Información General");
		waitForVisibleText(DEFAULT_TIMEOUT, "BUSINESS PLAN");
		waitForVisibleText(DEFAULT_TIMEOUT, "Cambiar Plan");

		final String pageText = normalizedVisibleText();
		assertCondition(EMAIL_PATTERN.matcher(pageText).find(), "User email is not visible.");
		assertCondition(hasLikelyUserName(pageText), "User name is not visible.");
	}

	private void validateDetallesCuenta() {
		waitForVisibleText(DEFAULT_TIMEOUT, "Detalles de la Cuenta");
		waitForVisibleText(DEFAULT_TIMEOUT, "Cuenta creada");
		waitForVisibleText(DEFAULT_TIMEOUT, "Estado activo");
		waitForVisibleText(DEFAULT_TIMEOUT, "Idioma seleccionado");
	}

	private void validateTusNegocios() {
		waitForVisibleText(DEFAULT_TIMEOUT, "Tus Negocios");
		waitForVisibleText(DEFAULT_TIMEOUT, "Agregar Negocio");
		waitForVisibleText(DEFAULT_TIMEOUT, "Tienes 2 de 3 negocios");
	}

	private void validateLegalPage(final String legalLinkText, final String screenshotName) throws IOException {
		waitForVisibleText(DEFAULT_TIMEOUT, "Sección Legal");
		final String preClickUrl = driver.getCurrentUrl();
		final Set<String> beforeHandles = driver.getWindowHandles();
		final String originatingHandle = driver.getWindowHandle();

		clickFirstVisibleByText(legalLinkText);
		final String destinationHandle = waitForNavigationOrNewTab(beforeHandles, preClickUrl);

		final boolean switchedToNewTab = destinationHandle != null && !destinationHandle.equals(originatingHandle);
		if (switchedToNewTab) {
			driver.switchTo().window(destinationHandle);
			waitForUiToLoad();
		}

		waitForVisibleText(DEFAULT_TIMEOUT, legalLinkText);
		final String bodyText = normalizedVisibleText();
		assertCondition(bodyText.length() > 200, "Legal content text is too short or not visible.");

		legalUrls.put(legalLinkText, driver.getCurrentUrl());
		takeScreenshot(screenshotName);

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(originatingHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private boolean runStep(final String reportField, final boolean dependencyOk, final StepExecutable executable) {
		if (!dependencyOk) {
			report.put(reportField, StepStatus.fail("BLOCKED BY PREVIOUS FAILURE"));
			return false;
		}

		try {
			executable.run();
			report.put(reportField, StepStatus.pass("PASS"));
			return true;
		} catch (final Throwable throwable) {
			report.put(reportField, StepStatus.fail(safeMessage(throwable)));
			return false;
		}
	}

	private void selectGoogleAccountIfPrompted(final String email) {
		if (isTextVisible(SHORT_TIMEOUT, email)) {
			clickFirstVisibleByText(email);
			return;
		}

		final List<By> emailInputs = List.of(By.xpath("//input[@type='email']"), By.xpath("//input[@name='identifier']"));
		for (final By locator : emailInputs) {
			final List<WebElement> matching = driver.findElements(locator);
			if (!matching.isEmpty() && matching.get(0).isDisplayed()) {
				matching.get(0).clear();
				matching.get(0).sendKeys(email);
				matching.get(0).sendKeys(Keys.ENTER);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void expandMiNegocioIfNeeded() {
		if (isTextVisible(SHORT_TIMEOUT, "Administrar Negocios")) {
			return;
		}

		if (isTextVisible(SHORT_TIMEOUT, "Mi Negocio")) {
			clickFirstVisibleByText("Mi Negocio");
		} else {
			clickFirstVisibleByText("Negocio");
			clickFirstVisibleByText("Mi Negocio");
		}
		waitForVisibleText(DEFAULT_TIMEOUT, "Administrar Negocios");
	}

	private void tryTypeInBusinessNameField(final String businessName) {
		final List<By> candidateLocators = List.of(
				By.xpath("//input[@placeholder='Nombre del Negocio' or @name='businessName' or @id='businessName']"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));

		for (final By locator : candidateLocators) {
			final List<WebElement> matching = driver.findElements(locator);
			if (!matching.isEmpty() && matching.get(0).isDisplayed()) {
				matching.get(0).click();
				matching.get(0).clear();
				matching.get(0).sendKeys(businessName);
				return;
			}
		}
	}

	private WebElement findSidebar() {
		final List<By> locators = List.of(By.xpath("//aside"),
				By.xpath("//nav[contains(@class, 'sidebar') or @aria-label='Sidebar']"),
				By.xpath("//nav[.//*[contains(normalize-space(.), 'Negocio')]]"));

		for (final By locator : locators) {
			final List<WebElement> found = driver.findElements(locator);
			if (!found.isEmpty() && found.get(0).isDisplayed()) {
				return found.get(0);
			}
		}

		throw new NoSuchElementException("Sidebar element was not found.");
	}

	private String waitForNavigationOrNewTab(final Set<String> beforeHandles, final String oldUrl) {
		final WebDriverWait localWait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		try {
			return localWait.until(d -> {
				final Set<String> currentHandles = d.getWindowHandles();
				if (currentHandles.size() > beforeHandles.size()) {
					final Set<String> newHandles = new java.util.HashSet<>(currentHandles);
					newHandles.removeAll(beforeHandles);
					if (!newHandles.isEmpty()) {
						return newHandles.iterator().next();
					}
				}
				if (!oldUrl.equals(d.getCurrentUrl())) {
					return d.getWindowHandle();
				}
				return null;
			});
		} catch (final TimeoutException timeoutException) {
			throw new AssertionError("No navigation or new tab detected after clicking legal link.", timeoutException);
		}
	}

	private void clickFirstVisibleByText(final String... texts) {
		final WebElement target = findFirstVisibleByText(DEFAULT_TIMEOUT, texts);
		target.click();
		waitForUiToLoad();
	}

	private WebElement findFirstVisibleByText(final Duration timeout, final String... texts) {
		final long deadlineMs = System.currentTimeMillis() + timeout.toMillis();
		final List<String> errors = new ArrayList<>();

		while (System.currentTimeMillis() < deadlineMs) {
			for (final String text : texts) {
				try {
					final List<WebElement> candidates = driver.findElements(byText(text));
					for (final WebElement element : candidates) {
						if (element.isDisplayed()) {
							return element;
						}
					}
				} catch (final NoSuchElementException ignored) {
					// Best-effort loop, keep polling while waiting.
				} catch (final Exception exception) {
					errors.add("[" + text + "] " + exception.getMessage());
				}
			}
			sleep(250);
		}

		throw new AssertionError("Could not find any visible element using texts " + List.of(texts) + ". " + errors);
	}

	private void waitForVisibleText(final Duration timeout, final String... texts) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		try {
			localWait.until(d -> {
				for (final String text : texts) {
					final List<WebElement> matches = d.findElements(byText(text));
					for (final WebElement match : matches) {
						if (match.isDisplayed()) {
							return true;
						}
					}
				}
				return false;
			});
		} catch (final TimeoutException timeoutException) {
			throw new AssertionError("Expected visible text not found: " + List.of(texts), timeoutException);
		}
	}

	private void waitUntilTextNotVisible(final String text) {
		final WebDriverWait localWait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		localWait.until(ExpectedConditions.invisibilityOfElementLocated(byText(text)));
	}

	private boolean isTextVisible(final Duration timeout, final String text) {
		try {
			waitForVisibleText(timeout, text);
			return true;
		} catch (final AssertionError ignored) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		final ExpectedCondition<Boolean> documentReady = d -> "complete"
				.equals(((JavascriptExecutor) d).executeScript("return document.readyState"));
		wait.until(documentReady);
		sleep(300);
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(evidenceDir.resolve(fileName + ".png"), screenshot);
	}

	private void takeFullPageScreenshot(final String fileName) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		final JavascriptExecutor js = (JavascriptExecutor) driver;
		final Number fullHeight = (Number) js
				.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
		final Number fullWidth = (Number) js
				.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);");

		final int screenshotHeight = Math.min(Math.max(fullHeight.intValue() + 200, 900), 3200);
		final int screenshotWidth = Math.min(Math.max(fullWidth.intValue() + 100, 1366), 1920);

		driver.manage().window().setSize(new Dimension(screenshotWidth, screenshotHeight));
		sleep(350);
		takeScreenshot(fileName);
		driver.manage().window().setSize(originalSize);
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder reportText = new StringBuilder();
		reportText.append("# SaleADS Mi Negocio - Final Report\n\n");
		for (final String field : REPORT_FIELDS) {
			final StepStatus status = report.get(field);
			reportText.append("- ").append(field).append(": ").append(status.pass ? "PASS" : "FAIL").append(" - ")
					.append(status.detail).append("\n");
		}

		if (!legalUrls.isEmpty()) {
			reportText.append("\n## Captured legal URLs\n");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				reportText.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
			}
		}

		reportText.append("\n## Evidence directory\n");
		reportText.append(evidenceDir.toAbsolutePath()).append("\n");

		Files.writeString(evidenceDir.resolve("final-report.md"), reportText.toString());
	}

	private String normalizedVisibleText() {
		return driver.findElement(By.tagName("body")).getText().replace('\n', ' ').replaceAll("\\s{2,}", " ").trim();
	}

	private boolean hasLikelyUserName(final String pageText) {
		final String[] tokens = pageText.split(" ");
		for (int i = 0; i < tokens.length - 1; i++) {
			final String candidate = tokens[i] + " " + tokens[i + 1];
			if (candidate.length() < 4) {
				continue;
			}
			final String lower = candidate.toLowerCase(Locale.ROOT);
			if (lower.contains("@") || lower.contains("business") || lower.contains("plan")
					|| lower.contains("información") || lower.contains("cambiar")) {
				continue;
			}
			if (Character.isUpperCase(candidate.charAt(0))) {
				return true;
			}
		}
		return false;
	}

	private void assertElementVisible(final WebElement element, final String message) {
		assertCondition(element != null && element.isDisplayed(), message);
	}

	private void assertCondition(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private String requiredConfig(final String envName, final String propertyName) {
		final String value = optionalConfig(envName, propertyName, null);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(
					"Missing required config. Set " + envName + " or -D" + propertyName + "=...");
		}
		return value;
	}

	private String optionalConfig(final String envName, final String propertyName, final String defaultValue) {
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

	private WebDriver createDriver() {
		final String browser = optionalConfig("SALEADS_BROWSER", "saleads.browser", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean
				.parseBoolean(optionalConfig("SALEADS_HEADLESS", "saleads.headless", "false").toLowerCase(Locale.ROOT));

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			return new FirefoxDriver(firefoxOptions);
		case "edge":
			final EdgeOptions edgeOptions = new EdgeOptions();
			if (headless) {
				edgeOptions.addArguments("--headless=new");
			}
			edgeOptions.addArguments("--window-size=1366,900");
			return new EdgeDriver(edgeOptions);
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			chromeOptions.addArguments("--window-size=1366,900");
			chromeOptions.addArguments("--disable-dev-shm-usage");
			chromeOptions.addArguments("--no-sandbox");
			return new ChromeDriver(chromeOptions);
		}
	}

	private By byText(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath("//*[contains(normalize-space(.), " + literal + ")]");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private void sleep(final long milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for UI.", interruptedException);
		}
	}

	private String safeMessage(final Throwable throwable) {
		final String message = throwable.getMessage();
		return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
	}

	@FunctionalInterface
	private interface StepExecutable {
		void run() throws Exception;
	}

	private static final class StepStatus {
		private final boolean pass;
		private final String detail;

		private StepStatus(final boolean pass, final String detail) {
			this.pass = pass;
			this.detail = detail;
		}

		private static StepStatus pass(final String detail) {
			return new StepStatus(true, detail);
		}

		private static StepStatus fail(final String detail) {
			return new StepStatus(false, detail);
		}

		private boolean isPass() {
			return pass;
		}
	}
}
