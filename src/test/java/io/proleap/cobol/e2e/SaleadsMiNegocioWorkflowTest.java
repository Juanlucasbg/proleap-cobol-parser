package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow for SaleADS "Mi Negocio", based on visible text selectors.
 *
 * <p>
 * This test is disabled by default. Enable it with:
 * </p>
 *
 * <ul>
 * <li>Environment variable: {@code SALEADS_E2E_ENABLED=true}</li>
 * <li>or system property: {@code -Dsaleads.e2e.enabled=true}</li>
 * </ul>
 *
 * <p>
 * The script does not hardcode any SaleADS domain. Provide the login URL through:
 * </p>
 *
 * <ul>
 * <li>Environment variable: {@code SALEADS_LOGIN_URL}</li>
 * <li>or system property: {@code -Dsaleads.login.url=...}</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, Boolean> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private final Map<String, Path> screenshots = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true (or -Dsaleads.e2e.enabled=true) to run this e2e test.",
				isEnabled());

		evidenceDir = Files.createDirectories(
				Paths.get("target", "saleads-evidence", "run-" + TIMESTAMP_FORMAT.format(LocalDateTime.now())));

		try {
			driver = createDriver();
			wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

			final String loginUrl = readSetting("SALEADS_LOGIN_URL", "saleads.login.url");
			if (loginUrl != null && !loginUrl.isBlank()) {
				driver.get(loginUrl);
			}
			waitForUiLoad();

			runStep("Login", this::loginWithGoogle);
			runStep("Mi Negocio menu", this::openMiNegocioMenu);
			runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::openAdministrarNegocios);
			runStep("Información General", this::validateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
			runStep("Tus Negocios", this::validateTusNegocios);
			runStep("Términos y Condiciones", this::validateTerminosYCondiciones);
			runStep("Política de Privacidad", this::validatePoliticaPrivacidad);
		} finally {
			writeFinalReport();
			if (driver != null) {
				driver.quit();
			}
		}

		assertTrue("One or more validations failed. Review " + evidenceDir.resolve("final-report.txt"),
				stepStatus.values().stream().allMatch(Boolean::booleanValue));
	}

	private void loginWithGoogle() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");

		final String googleEmail = firstNonBlank(
				readSetting("SALEADS_GOOGLE_EMAIL", "saleads.google.email"), DEFAULT_GOOGLE_EMAIL);

		clickIfVisible(googleEmail);
		waitForUiLoad();

		assertAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Inicio");
		assertTrue("Left sidebar should be visible", hasVisibleElement(By.xpath("//aside | //nav")));
		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws Exception {
		expandMiNegocioMenu();
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		assertAnyVisibleText("Crear Nuevo Negocio");
		assertTrue("Input field 'Nombre del Negocio' should exist",
				hasVisibleElement(By.xpath(
						"//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')]")));
		assertAnyVisibleText("Tienes 2 de 3 negocios");
		assertAnyVisibleText("Cancelar");
		assertAnyVisibleText("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal");

		final WebElement input = firstVisible(By.xpath(
				"//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')]"));
		if (input != null) {
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
		}

		clickByVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void openAdministrarNegocios() throws Exception {
		expandMiNegocioMenu();
		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertAnyVisibleText("Información General");
		assertAnyVisibleText("Detalles de la Cuenta");
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Sección Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		assertAnyVisibleText("Información General");
		assertTrue("User name should be visible in account summary", hasUserNameLikeValueVisible());
		assertTrue("User email should be visible in account summary", hasVisibleEmail());
		assertAnyVisibleText("BUSINESS PLAN");
		assertAnyVisibleText("Cambiar Plan");
	}

	private void validateDetallesCuenta() {
		assertAnyVisibleText("Cuenta creada");
		assertAnyVisibleText("Estado activo");
		assertAnyVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertAnyVisibleText("Tus Negocios");
		assertTrue("Business list should be visible", hasBusinessListVisible());
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
	}

	private void validateTerminosYCondiciones() throws Exception {
		termsUrl = openLegalLinkAndValidate(new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
				new String[] { "Términos y Condiciones", "Terminos y Condiciones" }, "05-terminos-y-condiciones");
	}

	private void validatePoliticaPrivacidad() throws Exception {
		privacyUrl = openLegalLinkAndValidate(new String[] { "Política de Privacidad", "Politica de Privacidad" },
				new String[] { "Política de Privacidad", "Politica de Privacidad" }, "06-politica-de-privacidad");
	}

	private String openLegalLinkAndValidate(final String[] linkTextCandidates, final String[] headingCandidates,
			final String screenshotName) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkTextCandidates);

		boolean switchedToNewTab = false;
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT)
					.until(webDriver -> webDriver.getWindowHandles().size() > windowsBeforeClick.size());
			for (String handle : driver.getWindowHandles()) {
				if (!windowsBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					switchedToNewTab = true;
					break;
				}
			}
		} catch (TimeoutException ignored) {
			// same-tab navigation is valid as well
		}

		waitForUiLoad();
		assertAnyVisibleText(headingCandidates);
		assertTrue("Legal content text should be visible", hasVisibleElement(By.xpath("//p[normalize-space()!='']")));
		takeScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiLoad();
		assertAnyVisibleText("Sección Legal", "Información General", "Tus Negocios");

		return finalUrl;
	}

	private void expandMiNegocioMenu() {
		if (isAnyTextVisible("Agregar Negocio", "Administrar Negocios")) {
			return;
		}

		clickIfVisible("Negocio");
		waitForUiLoad();
		clickIfVisible("Mi Negocio");
		waitForUiLoad();
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepStatus.put(stepName, true);
			stepDetails.put(stepName, "PASS");
		} catch (Throwable throwable) {
			stepStatus.put(stepName, false);
			stepDetails.put(stepName, sanitizeDetail(throwable.getMessage()));
			try {
				takeScreenshot("failed-" + normalizeFileName(stepName));
			} catch (Exception ignored) {
				// best effort evidence
			}
		}
	}

	private void clickByVisibleText(final String... textCandidates) {
		Objects.requireNonNull(textCandidates, "textCandidates");

		Throwable lastError = null;

		for (String candidate : textCandidates) {
			if (candidate == null || candidate.isBlank()) {
				continue;
			}

			try {
				final WebElement element = waitForClickableByText(candidate, SHORT_TIMEOUT);
				scrollIntoView(element);
				try {
					element.click();
				} catch (Exception clickException) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				}
				waitForUiLoad();
				return;
			} catch (Throwable throwable) {
				lastError = throwable;
			}
		}

		throw new NoSuchElementException(
				"Unable to click any element by visible text: " + Arrays.toString(textCandidates), lastError);
	}

	private void clickIfVisible(final String... textCandidates) {
		for (String candidate : textCandidates) {
			if (isAnyTextVisible(candidate)) {
				clickByVisibleText(candidate);
				return;
			}
		}
	}

	private void assertAnyVisibleText(final String... textCandidates) {
		if (!isAnyTextVisible(textCandidates)) {
			throw new AssertionError("Expected visible text not found: " + Arrays.toString(textCandidates));
		}
	}

	private boolean isAnyTextVisible(final String... textCandidates) {
		for (String candidate : textCandidates) {
			if (candidate == null || candidate.isBlank()) {
				continue;
			}
			if (isTextVisible(candidate)) {
				return true;
			}
		}
		return false;
	}

	private boolean isTextVisible(final String text) {
		final String literal = toXPathLiteral(text);
		final List<WebElement> elements = driver.findElements(
				By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"));
		for (WebElement element : elements) {
			if (safeIsDisplayed(element)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasVisibleElement(final By by) {
		for (WebElement element : driver.findElements(by)) {
			if (safeIsDisplayed(element)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasVisibleEmail() {
		for (WebElement element : driver.findElements(By.xpath("//*[contains(normalize-space(), '@')]"))) {
			if (!safeIsDisplayed(element)) {
				continue;
			}

			final String text = element.getText() == null ? "" : element.getText().trim();
			if (EMAIL_PATTERN.matcher(text).matches()) {
				return true;
			}

			for (String token : text.split("\\s+")) {
				if (EMAIL_PATTERN.matcher(token).matches()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean hasUserNameLikeValueVisible() {
		return hasVisibleElement(By.xpath(
				"//*[contains(translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚ', 'abcdefghijklmnopqrstuvwxyzáéíóú'), 'nombre')]/following::*[normalize-space()!=''][1]"))
				|| hasVisibleElement(By.xpath(
						"//*[contains(translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚ', 'abcdefghijklmnopqrstuvwxyzáéíóú'), 'información general')]/following::*[self::h1 or self::h2 or self::h3 or self::span or self::div][normalize-space()!=''][1]"));
	}

	private boolean hasBusinessListVisible() {
		return hasVisibleElement(By.xpath(
				"//*[contains(normalize-space(), 'Tus Negocios')]/following::*[self::ul or self::table or self::tbody or @role='table' or contains(@class, 'list') or contains(@class, 'table')][1]"))
				|| hasVisibleElement(By.xpath(
						"//*[contains(normalize-space(), 'Tus Negocios')]/following::*[self::li or self::tr][normalize-space()!='']"));
	}

	private void waitForUiLoad() {
		wait.until(webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));

		final List<By> commonLoadingIndicators = Arrays.asList(By.cssSelector("[aria-busy='true']"),
				By.cssSelector("[role='progressbar']"), By.cssSelector(".loading"), By.cssSelector(".spinner"));

		for (By indicator : commonLoadingIndicators) {
			try {
				new WebDriverWait(driver, Duration.ofSeconds(2))
						.until(ExpectedConditions.invisibilityOfElementLocated(indicator));
			} catch (TimeoutException ignored) {
				// indicator might not belong to this app state
			}
		}
	}

	private WebElement waitForClickableByText(final String text, final Duration timeout) {
		final String literal = toXPathLiteral(text);
		final By[] candidates = new By[] {
				By.xpath("//*[self::button or self::a or @role='button'][normalize-space()=" + literal + "]"),
				By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(), " + literal + ")]"),
				By.xpath("//*[normalize-space()=" + literal
						+ "]/ancestor-or-self::*[self::button or self::a or @role='button' or self::li or self::div][1]"),
				By.xpath("//*[contains(normalize-space(), " + literal
						+ ")]/ancestor-or-self::*[self::button or self::a or @role='button' or self::li or self::div][1]") };

		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		Throwable lastError = null;
		for (By candidate : candidates) {
			try {
				return shortWait.until(ExpectedConditions.elementToBeClickable(candidate));
			} catch (Throwable throwable) {
				lastError = throwable;
			}
		}

		throw new NoSuchElementException("No clickable element found with text: " + text, lastError);
	}

	private WebElement firstVisible(final By by) {
		for (WebElement element : driver.findElements(by)) {
			if (safeIsDisplayed(element)) {
				return element;
			}
		}
		return null;
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private Path takeScreenshot(final String checkpointName) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return null;
		}

		final String fileName = normalizeFileName(checkpointName) + "-" + TIMESTAMP_FORMAT.format(LocalDateTime.now())
				+ ".png";
		final Path target = evidenceDir.resolve(fileName);
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		screenshots.put(checkpointName, target);
		return target;
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio workflow report");
		lines.add("Generated at: " + LocalDateTime.now());
		lines.add("");
		lines.add("Final status by section:");

		for (String reportField : REPORT_FIELDS) {
			final boolean passed = stepStatus.getOrDefault(reportField, false);
			lines.add("- " + reportField + ": " + (passed ? "PASS" : "FAIL"));
			if (!passed) {
				lines.add("  detail: " + stepDetails.getOrDefault(reportField, "No detail provided"));
			}
		}

		lines.add("");
		lines.add("Captured URLs:");
		lines.add("- Términos y Condiciones: " + termsUrl);
		lines.add("- Política de Privacidad: " + privacyUrl);
		lines.add("");
		lines.add("Screenshots:");
		for (Map.Entry<String, Path> entry : screenshots.entrySet()) {
			lines.add("- " + entry.getKey() + ": " + entry.getValue());
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.write(reportPath, lines, StandardCharsets.UTF_8);
	}

	private WebDriver createDriver() {
		final String browser = firstNonBlank(readSetting("SALEADS_BROWSER", "saleads.browser"), "chrome");
		final boolean headless = Boolean.parseBoolean(firstNonBlank(readSetting("SALEADS_HEADLESS", "saleads.headless"),
				Boolean.TRUE.toString()));
		final String remoteUrl = readSetting("SELENIUM_REMOTE_URL", "selenium.remote.url");

		final Capabilities capabilities = buildCapabilities(browser, headless);

		if (remoteUrl != null && !remoteUrl.isBlank()) {
			try {
				return new RemoteWebDriver(java.net.URI.create(remoteUrl).toURL(), capabilities);
			} catch (Exception exception) {
				throw new IllegalArgumentException("Invalid SELENIUM_REMOTE_URL: " + remoteUrl, exception);
			}
		}

		if ("firefox".equalsIgnoreCase(browser)) {
			return new FirefoxDriver((FirefoxOptions) capabilities);
		}

		return new ChromeDriver((ChromeOptions) capabilities);
	}

	private Capabilities buildCapabilities(final String browser, final boolean headless) {
		if ("firefox".equalsIgnoreCase(browser)) {
			final FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			options.addArguments("--width=1920");
			options.addArguments("--height=1080");
			return options;
		}

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (headless) {
			options.addArguments("--headless=new");
		}
		return options;
	}

	private boolean isEnabled() {
		return Boolean.parseBoolean(
				firstNonBlank(readSetting("SALEADS_E2E_ENABLED", "saleads.e2e.enabled"), Boolean.FALSE.toString()));
	}

	private static String readSetting(final String envName, final String propertyName) {
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return System.getProperty(propertyName);
	}

	private static String firstNonBlank(final String first, final String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		return second;
	}

	private static boolean safeIsDisplayed(final WebElement element) {
		try {
			return element.isDisplayed();
		} catch (Exception ignored) {
			return false;
		}
	}

	private static String sanitizeDetail(final String detail) {
		if (detail == null || detail.isBlank()) {
			return "Unknown failure";
		}
		return detail.replace('\n', ' ').trim();
	}

	private static String normalizeFileName(final String raw) {
		return raw.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder literal = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			literal.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				literal.append(", \"'\", ");
			}
		}
		literal.append(")");
		return literal.toString();
	}

	private interface StepAction {
		void run() throws Exception;
	}
}
