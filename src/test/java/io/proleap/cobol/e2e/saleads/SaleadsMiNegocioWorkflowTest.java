package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
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

public class SaleadsMiNegocioWorkflowTest {

	private static final String ENV_START_URL = "SALEADS_START_URL";
	private static final String ENV_REMOTE_URL = "SALEADS_REMOTE_WEBDRIVER_URL";
	private static final String ENV_BROWSER = "SALEADS_BROWSER";
	private static final String ENV_HEADLESS = "SALEADS_HEADLESS";
	private static final String ENV_TIMEOUT_SECONDS = "SALEADS_TIMEOUT_SECONDS";
	private static final String ENV_GOOGLE_ACCOUNT = "SALEADS_GOOGLE_ACCOUNT";
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");
	private static final Path EVIDENCE_DIR = Paths.get("target", "surefire-reports", "saleads-mi-negocio");
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT;

	private final Map<String, StepStatus> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private String appWindowHandle;
	private int screenshotIndex;

	@Before
	public void setUp() throws Exception {
		Files.createDirectories(EVIDENCE_DIR);

		for (final String field : REPORT_FIELDS) {
			stepResults.put(field, new StepStatus());
		}

		final String startUrl = env(ENV_START_URL);
		Assume.assumeTrue("Set " + ENV_START_URL + " to the SaleADS login URL for the current environment.",
				startUrl != null && !startUrl.isBlank());

		driver = createDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds()));
		driver.manage().window().setSize(new Dimension(1920, 1080));
		driver.get(startUrl);
		waitForUiToSettle();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() throws Exception {
		writeFinalReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad"));

		assertAllStepsPassed();
	}

	private void stepLoginWithGoogle() {
		final String reportField = "Login";
		final boolean alreadyInApp = isVisibleAny(sidebarLocators());

		if (!alreadyInApp) {
			final Set<String> windowHandlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

			clickByVisibleTexts(reportField, "Click login button", "Sign in with Google", "Iniciar sesión con Google",
					"Iniciar Sesión con Google", "Continuar con Google", "Login with Google", "Ingresar con Google");

			waitForUiToSettle();
			selectGoogleAccountIfVisible(windowHandlesBeforeClick);
		} else {
			addNote(reportField, "Login button was not required because the app was already visible.");
		}

		ensureVisible(reportField, "Main application interface appears", sidebarLocators());
		ensureVisible(reportField, "Left sidebar navigation is visible",
				By.xpath("//aside//*[self::a or self::button or self::span][normalize-space()]"),
				By.xpath("//nav//*[self::a or self::button or self::span][normalize-space()]"));
		captureScreenshot("dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		final String reportField = "Mi Negocio menu";

		clickByVisibleTexts(reportField, "Open Negocio section", "Negocio");
		clickByVisibleTexts(reportField, "Open Mi Negocio menu", "Mi Negocio");

		ensureVisible(reportField, "Submenu item 'Agregar Negocio' is visible", clickableText("Agregar Negocio"),
				textContains("Agregar Negocio"));
		ensureVisible(reportField, "Submenu item 'Administrar Negocios' is visible", clickableText("Administrar Negocios"),
				textContains("Administrar Negocios"));
		captureScreenshot("mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		final String reportField = "Agregar Negocio modal";

		clickByVisibleTexts(reportField, "Open Agregar Negocio modal", "Agregar Negocio");
		ensureVisible(reportField, "Modal title 'Crear Nuevo Negocio' is visible", textContains("Crear Nuevo Negocio"));
		ensureVisible(reportField, "Input field 'Nombre del Negocio' exists", By.xpath(
				"//label[contains(normalize-space(), 'Nombre del Negocio')]/following::*[self::input or self::textarea][1]"),
				By.xpath(
						"//*[contains(normalize-space(), 'Nombre del Negocio')]/following::*[self::input or self::textarea][1]"));
		ensureVisible(reportField, "Business quota text is visible", textContains("Tienes 2 de 3 negocios"));
		ensureVisible(reportField, "Button 'Cancelar' is present", clickableText("Cancelar"));
		ensureVisible(reportField, "Button 'Crear Negocio' is present", clickableText("Crear Negocio"));
		captureScreenshot("agregar-negocio-modal");

		final WebElement nombreNegocioField = findVisibleElement(Duration.ofSeconds(4),
				By.xpath("//label[contains(normalize-space(), 'Nombre del Negocio')]/following::*[self::input][1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre')]"));

		if (nombreNegocioField != null) {
			nombreNegocioField.click();
			nombreNegocioField.clear();
			nombreNegocioField.sendKeys("Negocio Prueba Automatización");
			addNote(reportField, "Optional action executed: typed test business name.");
		}

		clickByVisibleTexts(reportField, "Close modal using Cancelar", "Cancelar");
	}

	private void stepOpenAdministrarNegocios() {
		final String reportField = "Administrar Negocios view";

		if (!isVisibleAny(clickableText("Administrar Negocios"), textContains("Administrar Negocios"))) {
			clickByVisibleTexts(reportField, "Expand Mi Negocio to reveal Administrar Negocios", "Mi Negocio");
		}

		clickByVisibleTexts(reportField, "Open Administrar Negocios page", "Administrar Negocios");

		ensureVisible(reportField, "Section 'Información General' exists", textContains("Información General"),
				textContains("Informacion General"));
		ensureVisible(reportField, "Section 'Detalles de la Cuenta' exists", textContains("Detalles de la Cuenta"));
		ensureVisible(reportField, "Section 'Tus Negocios' exists", textContains("Tus Negocios"));
		ensureVisible(reportField, "Section 'Sección Legal' exists", textContains("Sección Legal"), textContains("Seccion Legal"));
		captureScreenshot("administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		final String reportField = "Información General";

		ensureVisible(reportField, "User email is visible", By.xpath("//*[contains(normalize-space(), '@')]"));
		ensureVisible(reportField, "User name is visible",
				By.xpath(
						"//*[contains(normalize-space(),'Información General') or contains(normalize-space(),'Informacion General')]/following::*[self::h1 or self::h2 or self::h3 or self::p or self::span][not(contains(normalize-space(),'@'))][string-length(normalize-space()) > 2][1]"),
				By.xpath(
						"//*[contains(@class,'name') and not(contains(normalize-space(),'@')) and string-length(normalize-space()) > 2]"));
		ensureVisible(reportField, "Text 'BUSINESS PLAN' is visible", textContains("BUSINESS PLAN"));
		ensureVisible(reportField, "Button 'Cambiar Plan' is visible", clickableText("Cambiar Plan"),
				textContains("Cambiar Plan"));
	}

	private void stepValidateDetallesCuenta() {
		final String reportField = "Detalles de la Cuenta";

		ensureVisible(reportField, "'Cuenta creada' is visible", textContains("Cuenta creada"));
		ensureVisible(reportField, "'Estado activo' is visible", textContains("Estado activo"));
		ensureVisible(reportField, "'Idioma seleccionado' is visible", textContains("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final String reportField = "Tus Negocios";

		ensureVisible(reportField, "Section header 'Tus Negocios' is visible", textContains("Tus Negocios"));
		ensureVisible(reportField, "Business list is visible",
				By.xpath(
						"//*[contains(normalize-space(),'Tus Negocios')]/following::*[(self::li or self::tr or self::div)[string-length(normalize-space()) > 2]][1]"),
				By.xpath("//table//tr[1]"), By.xpath("//ul/li[1]"));
		ensureVisible(reportField, "Button 'Agregar Negocio' exists", clickableText("Agregar Negocio"),
				textContains("Agregar Negocio"));
		ensureVisible(reportField, "Text 'Tienes 2 de 3 negocios' is visible", textContains("Tienes 2 de 3 negocios"));
	}

	private void stepValidateLegalLink(final String reportField, final String linkText) {
		final String urlBeforeClick = safeCurrentUrl();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleTexts(reportField, "Open legal link: " + linkText, linkText);

		final String newHandle = waitForNewWindowHandle(handlesBeforeClick, Duration.ofSeconds(8));
		boolean openedNewTab = false;

		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			openedNewTab = true;
		} else {
			waitForUrlChange(urlBeforeClick, Duration.ofSeconds(8));
		}

		waitForUiToSettle();

		ensureVisible(reportField, "Heading '" + linkText + "' is visible", textContains(linkText));
		ensureVisible(reportField, "Legal content text is visible",
				By.xpath("//main//*[self::p or self::div][string-length(normalize-space()) > 60][1]"),
				By.xpath("//article//*[self::p or self::div][string-length(normalize-space()) > 60][1]"),
				By.xpath("//*[self::p or self::div][string-length(normalize-space()) > 100][1]"));
		captureScreenshot(slugify(linkText) + "-page");

		final String finalUrl = safeCurrentUrl();
		legalUrls.put(linkText, finalUrl);
		addNote(reportField, "Final URL: " + finalUrl);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiToSettle();
	}

	private void runStep(final String reportField, final StepAction action) {
		final StepStatus status = stepResults.get(reportField);
		status.executed = true;

		try {
			action.run();
			if (status.passed && status.notes.isEmpty()) {
				status.notes.add("Step completed successfully.");
			}
		} catch (final Throwable throwable) {
			status.passed = false;
			status.notes.add("Failure: " + throwable.getMessage());
			captureScreenshot(slugify(reportField) + "-failure");
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failedSteps = stepResults.entrySet().stream()
				.filter(entry -> !entry.getValue().executed || !entry.getValue().passed).map(Map.Entry::getKey)
				.collect(Collectors.toList());

		if (!failedSteps.isEmpty()) {
			fail("SaleADS workflow validation failed for: " + failedSteps + ". Check " + EVIDENCE_DIR.resolve("final-report.txt"));
		}
	}

	private void clickByVisibleTexts(final String reportField, final String actionDescription, final String... texts) {
		WebElement target = null;

		for (final String text : texts) {
			target = findVisibleElement(Duration.ofSeconds(6), clickableText(text), textEquals(text), textContains(text));
			if (target != null) {
				break;
			}
		}

		if (target == null) {
			stepResults.get(reportField).passed = false;
			throw new AssertionError("Unable to locate clickable element for texts: " + Arrays.toString(texts));
		}

		scrollIntoView(target);
		wait.until(ExpectedConditions.elementToBeClickable(target)).click();
		addNote(reportField, actionDescription + " -> " + target.getText().trim());
		waitForUiToSettle();
	}

	private WebElement ensureVisible(final String reportField, final String validationDescription, final By... locators) {
		final WebElement visibleElement = findVisibleElement(Duration.ofSeconds(timeoutSeconds()), locators);

		if (visibleElement == null) {
			stepResults.get(reportField).passed = false;
			throw new AssertionError("Validation failed: " + validationDescription);
		}

		addNote(reportField, "PASS: " + validationDescription);
		return visibleElement;
	}

	private WebElement findVisibleElement(final Duration timeout, final By... locators) {
		final Instant end = Instant.now().plus(timeout);

		while (Instant.now().isBefore(end)) {
			for (final By locator : locators) {
				try {
					final List<WebElement> elements = driver.findElements(locator);
					for (final WebElement element : elements) {
						if (element != null && element.isDisplayed()) {
							return element;
						}
					}
				} catch (final RuntimeException ignored) {
					// Retry loop handles stale and transient DOM states.
				}
			}

			sleep(Duration.ofMillis(250));
		}

		return null;
	}

	private boolean isVisibleAny(final By... locators) {
		return findVisibleElement(Duration.ofSeconds(3), locators) != null;
	}

	private void selectGoogleAccountIfVisible(final Set<String> handlesBeforeClick) {
		final String accountEmail = envOrDefault(ENV_GOOGLE_ACCOUNT, DEFAULT_GOOGLE_ACCOUNT);
		final String popupHandle = waitForNewWindowHandle(handlesBeforeClick, Duration.ofSeconds(8));

		if (popupHandle != null) {
			driver.switchTo().window(popupHandle);
		}

		final WebElement accountOption = findVisibleElement(Duration.ofSeconds(8), textEquals(accountEmail),
				textContains(accountEmail), By.xpath("//div[contains(@data-identifier," + xPathLiteral(accountEmail) + ")]"));

		if (accountOption != null) {
			scrollIntoView(accountOption);
			accountOption.click();
			waitForUiToSettle();
		}

		if (popupHandle != null) {
			driver.switchTo().window(appWindowHandle);
			waitForUiToSettle();
		}
	}

	private void waitForUiToSettle() {
		try {
			wait.until(driverInstance -> {
				if (!(driverInstance instanceof JavascriptExecutor)) {
					return true;
				}

				final Object state = ((JavascriptExecutor) driverInstance).executeScript("return document.readyState");
				return "complete".equals(state);
			});
		} catch (final TimeoutException ignored) {
			// The DOM can stay busy because of background requests. Continue with a short pause.
		}

		sleep(Duration.ofMillis(750));
	}

	private void waitForUrlChange(final String previousUrl, final Duration timeout) {
		if (previousUrl == null || previousUrl.isBlank()) {
			return;
		}

		try {
			new WebDriverWait(driver, timeout).until(driverInstance -> !previousUrl.equals(safeCurrentUrl()));
		} catch (final TimeoutException ignored) {
			// Some environments keep legal pages in dynamic routers and URL may not change immediately.
		}
	}

	private String waitForNewWindowHandle(final Set<String> handlesBeforeClick, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(driverInstance -> driverInstance.getWindowHandles().size() > handlesBeforeClick.size());
		} catch (final TimeoutException ignored) {
			return null;
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBeforeClick.contains(handle)) {
				return handle;
			}
		}

		return null;
	}

	private void addNote(final String reportField, final String note) {
		stepResults.get(reportField).notes.add(note);
	}

	private void captureScreenshot(final String checkpointName) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final String indexedName = String.format("%02d_%s.png", ++screenshotIndex, slugify(checkpointName));
		final Path destination = EVIDENCE_DIR.resolve(indexedName);

		try {
			Files.write(destination, screenshot);
		} catch (final IOException ignored) {
			// Do not stop the test on screenshot write issues.
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test\n");
		report.append("generated_at=").append(DATE_FORMATTER.format(Instant.now())).append("\n");
		report.append("evidence_dir=").append(EVIDENCE_DIR.toAbsolutePath()).append("\n\n");
		report.append("Final report\n");

		for (final String field : REPORT_FIELDS) {
			final StepStatus status = stepResults.get(field);
			final String stepResult = status.executed && status.passed ? "PASS" : "FAIL";
			report.append("- ").append(field).append(": ").append(stepResult).append("\n");

			for (final String note : status.notes) {
				report.append("  - ").append(note).append("\n");
			}
		}

		if (!legalUrls.isEmpty()) {
			report.append("\nCaptured URLs\n");
			for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
				report.append("- ").append(legalUrl.getKey()).append(": ").append(legalUrl.getValue()).append("\n");
			}
		}

		final Path reportFile = EVIDENCE_DIR.resolve("final-report.txt");
		Files.writeString(reportFile, report.toString(), StandardCharsets.UTF_8);
		System.out.println(report);
	}

	private WebDriver createDriver() throws MalformedURLException {
		final String browser = envOrDefault(ENV_BROWSER, "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(envOrDefault(ENV_HEADLESS, "true"));
		final String remoteUrl = env(ENV_REMOTE_URL);

		final MutableCapabilities options;

		if ("firefox".equals(browser)) {
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			options = firefoxOptions;
		} else if ("chrome".equals(browser)) {
			final ChromeOptions chromeOptions = new ChromeOptions();
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			chromeOptions.addArguments("--window-size=1920,1080");
			chromeOptions.addArguments("--disable-dev-shm-usage");
			chromeOptions.addArguments("--no-sandbox");
			options = chromeOptions;
		} else {
			throw new IllegalArgumentException("Unsupported browser: " + browser + ". Use chrome or firefox.");
		}

		if (remoteUrl != null && !remoteUrl.isBlank()) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}

		if ("firefox".equals(browser)) {
			return new FirefoxDriver((FirefoxOptions) options);
		}

		return new ChromeDriver((ChromeOptions) options);
	}

	private By[] sidebarLocators() {
		return new By[] { By.xpath("//aside//*[contains(normalize-space(),'Negocio') or contains(normalize-space(),'Mi Negocio')]"),
				By.xpath("//nav//*[contains(normalize-space(),'Negocio') or contains(normalize-space(),'Mi Negocio')]"),
				textContains("Negocio"), textContains("Mi Negocio") };
	}

	private By textEquals(final String text) {
		return By.xpath("//*[normalize-space() = " + xPathLiteral(text) + "]");
	}

	private By textContains(final String text) {
		return By.xpath("//*[contains(normalize-space(), " + xPathLiteral(text) + ")]");
	}

	private By clickableText(final String text) {
		return By.xpath("//button[normalize-space() = " + xPathLiteral(text) + "]" + " | //a[normalize-space() = "
				+ xPathLiteral(text) + "]" + " | //*[@role='button' and normalize-space() = " + xPathLiteral(text) + "]"
				+ " | //*[self::span or self::div][normalize-space() = " + xPathLiteral(text)
				+ "]/ancestor::*[self::button or self::a or @role='button'][1]");
	}

	private void scrollIntoView(final WebElement element) {
		if (driver instanceof JavascriptExecutor) {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		}
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (final RuntimeException ignored) {
			return "";
		}
	}

	private int timeoutSeconds() {
		final String rawValue = env(ENV_TIMEOUT_SECONDS);

		if (rawValue == null || rawValue.isBlank()) {
			return 25;
		}

		try {
			return Integer.parseInt(rawValue);
		} catch (final NumberFormatException ignored) {
			return 25;
		}
	}

	private String env(final String key) {
		return System.getenv(key);
	}

	private String envOrDefault(final String key, final String defaultValue) {
		final String value = env(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private void sleep(final Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String slugify(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String xPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();

		for (int i = 0; i < chars.length; i++) {
			final String c = String.valueOf(chars[i]);
			if ("'".equals(c)) {
				sb.append("\"'\"");
			} else if ("\"".equals(c)) {
				sb.append("'\"'");
			} else {
				sb.append("'").append(c).append("'");
			}

			if (i < chars.length - 1) {
				sb.append(", ");
			}
		}

		sb.append(")");
		return sb.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run();
	}

	private static class StepStatus {
		private boolean executed;
		private boolean passed = true;
		private final List<String> notes = new ArrayList<>();
	}
}
