package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.File;
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
import java.util.stream.Collectors;

import org.junit.After;
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

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * SaleADS.ai workflow validation for "Mi Negocio".
 *
 * <p>
 * Runtime configuration:
 * </p>
 *
 * <ul>
 * <li>saleads.start.url (optional): login page URL for the current environment.</li>
 * <li>saleads.browser (optional): chrome (default) or firefox.</li>
 * <li>saleads.headless (optional): true/false (default true).</li>
 * <li>saleads.google.account (optional): Google account to select when prompted.</li>
 * <li>saleads.screenshots.dir (optional): evidence output folder.</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowIT {

	private static final Duration UI_WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_WAIT_TIMEOUT = Duration.ofSeconds(8);
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDir;
	private String appWindowHandle;
	private int screenshotCounter;
	private String termsAndConditionsUrl = "N/A";
	private String privacyPolicyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		driver = createDriver();
		wait = new WebDriverWait(driver, UI_WAIT_TIMEOUT);
		driver.manage().window().maximize();
		screenshotsDir = buildScreenshotsDirectory();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTermsAndConditions);
		runStep("Política de Privacidad", this::stepValidatePrivacyPolicy);

		final String report = buildFinalReport();
		System.out.println(report);
		assertTrue("SaleADS Mi Negocio workflow failed.\n" + report,
				finalReport.values().stream().allMatch(Boolean::booleanValue));
	}

	private void stepLoginWithGoogle() throws Exception {
		final String configuredUrl = readConfig("saleads.start.url", "SALEADS_START_URL", "");
		if (!configuredUrl.isBlank()) {
			driver.navigate().to(configuredUrl);
		}

		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();
		final Set<String> windowsBeforeLoginClick = driver.getWindowHandles();
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
				"Login with Google", "Continuar con Google");
		waitForUiLoad();
		switchToLatestWindowIfOpened(windowsBeforeLoginClick);
		selectGoogleAccountIfPrompted();
		switchBackToAppWindow();

		assertTrue("Main application interface did not appear.", waitForVisibleText("Negocio", UI_WAIT_TIMEOUT)
				|| waitForVisibleText("Mi Negocio", UI_WAIT_TIMEOUT));
		assertTrue("Left sidebar navigation is not visible.", isSidebarVisible());
		captureScreenshot("dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		assertTrue("Left sidebar is not visible.", isSidebarVisible());
		clickIfVisible("Negocio");
		waitForUiLoad();
		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		assertTrue("Mi Negocio submenu did not expand.", waitForVisibleText("Agregar Negocio", SHORT_WAIT_TIMEOUT));
		assertTrue("'Agregar Negocio' is not visible.", waitForVisibleText("Agregar Negocio", SHORT_WAIT_TIMEOUT));
		assertTrue("'Administrar Negocios' is not visible.",
				waitForVisibleText("Administrar Negocios", SHORT_WAIT_TIMEOUT));
		captureScreenshot("mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForUiLoad();

		assertTrue("Modal title 'Crear Nuevo Negocio' is not visible.",
				waitForVisibleText("Crear Nuevo Negocio", UI_WAIT_TIMEOUT));
		assertTrue("'Nombre del Negocio' field is missing.", isNombreDelNegocioInputVisible());
		assertTrue("'Tienes 2 de 3 negocios' text is missing.",
				waitForVisibleText("Tienes 2 de 3 negocios", SHORT_WAIT_TIMEOUT));
		assertTrue("'Cancelar' button is missing.", waitForVisibleText("Cancelar", SHORT_WAIT_TIMEOUT));
		assertTrue("'Crear Negocio' button is missing.", waitForVisibleText("Crear Negocio", SHORT_WAIT_TIMEOUT));

		captureScreenshot("agregar-negocio-modal");
		typeNombreNegocioIfPresent("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		waitForUiLoad();
		assertTrue("Modal did not close after 'Cancelar'.",
				!waitForVisibleText("Crear Nuevo Negocio", Duration.ofSeconds(3)));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!waitForVisibleText("Administrar Negocios", Duration.ofSeconds(3))) {
			clickIfVisible("Mi Negocio");
			waitForUiLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertTrue("'Información General' section is not visible.",
				waitForVisibleText("Información General", UI_WAIT_TIMEOUT));
		assertTrue("'Detalles de la Cuenta' section is not visible.",
				waitForVisibleText("Detalles de la Cuenta", SHORT_WAIT_TIMEOUT));
		assertTrue("'Tus Negocios' section is not visible.", waitForVisibleText("Tus Negocios", SHORT_WAIT_TIMEOUT));
		assertTrue("'Sección Legal' section is not visible.", waitForVisibleText("Sección Legal", SHORT_WAIT_TIMEOUT));

		captureScreenshot("administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() {
		assertTrue("Section 'Información General' is missing.",
				waitForVisibleText("Información General", SHORT_WAIT_TIMEOUT));
		assertTrue("User email is not visible.", isAnyEmailVisible());
		assertTrue("User name is not visible.", isLikelyUserNameVisible());
		assertTrue("'BUSINESS PLAN' is not visible.", waitForVisibleText("BUSINESS PLAN", SHORT_WAIT_TIMEOUT));
		assertTrue("'Cambiar Plan' button is not visible.", waitForVisibleText("Cambiar Plan", SHORT_WAIT_TIMEOUT));
	}

	private void stepValidateDetallesCuenta() {
		assertTrue("'Cuenta creada' is not visible.", waitForVisibleText("Cuenta creada", SHORT_WAIT_TIMEOUT));
		assertTrue("'Estado activo' is not visible.", waitForVisibleText("Estado activo", SHORT_WAIT_TIMEOUT));
		assertTrue("'Idioma seleccionado' is not visible.",
				waitForVisibleText("Idioma seleccionado", SHORT_WAIT_TIMEOUT));
	}

	private void stepValidateTusNegocios() {
		assertTrue("'Tus Negocios' section is not visible.", waitForVisibleText("Tus Negocios", SHORT_WAIT_TIMEOUT));
		assertTrue("Business list is not visible.", isBusinessListVisible());
		assertTrue("'Agregar Negocio' button is not visible.", waitForVisibleText("Agregar Negocio", SHORT_WAIT_TIMEOUT));
		assertTrue("'Tienes 2 de 3 negocios' is not visible.",
				waitForVisibleText("Tienes 2 de 3 negocios", SHORT_WAIT_TIMEOUT));
	}

	private void stepValidateTermsAndConditions() throws Exception {
		termsAndConditionsUrl = validateLegalLink("Términos y Condiciones", "Términos y Condiciones",
				"terminos-y-condiciones");
	}

	private void stepValidatePrivacyPolicy() throws Exception {
		privacyPolicyUrl = validateLegalLink("Política de Privacidad", "Política de Privacidad", "politica-privacidad");
	}

	private String validateLegalLink(final String linkText, final String headingText, final String screenshotName)
			throws Exception {
		final String appHandleBeforeClick = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickByVisibleText(linkText);
		waitForUiLoad();

		final String legalHandle = switchToLatestWindowIfOpened(handlesBeforeClick) ? driver.getWindowHandle()
				: appHandleBeforeClick;

		assertTrue("Heading '" + headingText + "' not found.", waitForVisibleText(headingText, UI_WAIT_TIMEOUT));
		assertTrue("Legal content text is not visible for '" + headingText + "'.", isLegalContentVisible());

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (!legalHandle.equals(appHandleBeforeClick)) {
			driver.close();
			driver.switchTo().window(appHandleBeforeClick);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		return finalUrl;
	}

	private WebDriver createDriver() {
		final String browser = readConfig("saleads.browser", "SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean
				.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true").toLowerCase(Locale.ROOT));

		if ("firefox".equals(browser)) {
			WebDriverManager.firefoxdriver().setup();
			final FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			return new FirefoxDriver(options);
		}

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-gpu");
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}
		return new ChromeDriver(options);
	}

	private Path buildScreenshotsDirectory() throws IOException {
		final String configuredPath = readConfig("saleads.screenshots.dir", "SALEADS_SCREENSHOTS_DIR",
				"target/saleads-mi-negocio-evidence");
		final Path basePath = Path.of(configuredPath);
		final Path path = basePath.resolve(LocalDateTime.now().format(FILE_TS));
		Files.createDirectories(path);
		return path;
	}

	private void runStep(final String field, final StepAction action) {
		try {
			action.execute();
			finalReport.put(field, true);
		} catch (final Throwable throwable) {
			finalReport.put(field, false);
			stepErrors.put(field, throwable.getMessage() == null ? throwable.toString() : throwable.getMessage());
			try {
				captureScreenshot("error-" + sanitizeForFileName(field));
			} catch (final Exception ignored) {
				// ignore screenshot capture failures for the final report
			}
		}
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Workflow - Final Report\n");
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL");
			if (!entry.getValue() && stepErrors.containsKey(entry.getKey())) {
				builder.append(" (").append(stepErrors.get(entry.getKey())).append(")");
			}
			builder.append('\n');
		}
		builder.append("Evidence directory: ").append(screenshotsDir.toAbsolutePath()).append('\n');
		builder.append("Términos y Condiciones URL: ").append(termsAndConditionsUrl).append('\n');
		builder.append("Política de Privacidad URL: ").append(privacyPolicyUrl).append('\n');
		return builder.toString();
	}

	private void clickByVisibleText(final String... candidates) {
		final List<String> errors = new ArrayList<>();
		for (final String candidate : candidates) {
			try {
				final WebElement element = findVisibleByText(candidate);
				scrollIntoView(element);
				wait.until(d -> element.isDisplayed() && element.isEnabled());
				try {
					element.click();
				} catch (final Exception clickError) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				}
				waitForUiLoad();
				return;
			} catch (final Exception error) {
				errors.add(candidate + " => " + error.getClass().getSimpleName());
			}
		}

		throw new IllegalStateException("Could not click using visible text. Candidates: "
				+ String.join(", ", candidates) + ". Attempts: " + String.join(" | ", errors));
	}

	private void clickIfVisible(final String text) {
		try {
			final WebElement element = findVisibleByText(text);
			scrollIntoView(element);
			element.click();
		} catch (final Exception ignored) {
			// optional click helper
		}
	}

	private WebElement findVisibleByText(final String text) {
		final String literal = xpathLiteral(text);
		final String lowerLiteral = xpathLiteral(text.toLowerCase(Locale.ROOT));

		final String exact = "//*[self::button or self::a or self::span or self::div or self::li or self::p or self::h1 or self::h2 or self::h3]"
				+ "[normalize-space(.)=" + literal + "]";
		final String contains = "//*[self::button or self::a or self::span or self::div or self::li or self::p or self::h1 or self::h2 or self::h3]"
				+ "[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), "
				+ lowerLiteral + ")]";

		try {
			return wait.until(d -> firstVisible(d.findElements(By.xpath(exact))));
		} catch (final TimeoutException ignored) {
			return wait.until(d -> firstVisible(d.findElements(By.xpath(contains))));
		}
	}

	private boolean waitForVisibleText(final String text, final Duration timeout) {
		final String lowerText = text.toLowerCase(Locale.ROOT);
		final String containsTextXpath = "//*[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), "
				+ xpathLiteral(lowerText) + ")]";

		try {
			new WebDriverWait(driver, timeout).until(d -> {
				for (final WebElement element : d.findElements(By.xpath(containsTextXpath))) {
					if (element.isDisplayed()) {
						return true;
					}
				}
				return false;
			});
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void waitForUiLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final TimeoutException ignored) {
			// some SPAs can keep transitions active; continue with best effort
		}

		final By loaders = By.xpath(
				"//*[contains(translate(@class, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),'loading') "
						+ "or contains(translate(@class, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),'spinner') "
						+ "or contains(translate(@class, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),'skeleton') "
						+ "or @aria-busy='true']");
		try {
			new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.invisibilityOfElementLocated(loaders));
		} catch (final TimeoutException ignored) {
			// optional wait only
		}
	}

	private void selectGoogleAccountIfPrompted() {
		final String account = readConfig("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);
		if (waitForVisibleText(account, Duration.ofSeconds(10))) {
			clickByVisibleText(account);
		}
	}

	private void switchBackToAppWindow() {
		if (appWindowHandle == null) {
			return;
		}

		try {
			driver.switchTo().window(appWindowHandle);
		} catch (final NoSuchElementException ignored) {
			// app window might already be active or replaced in flow
		}
	}

	private boolean switchToLatestWindowIfOpened(final Set<String> handlesBeforeClick) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size());
			final Set<String> handlesAfterClick = driver.getWindowHandles();
			final List<String> newHandles = handlesAfterClick.stream().filter(h -> !handlesBeforeClick.contains(h))
					.collect(Collectors.toList());
			if (!newHandles.isEmpty()) {
				driver.switchTo().window(newHandles.get(newHandles.size() - 1));
				waitForUiLoad();
				return true;
			}
		} catch (final TimeoutException ignored) {
			// no new tab/window
		}
		return false;
	}

	private boolean isSidebarVisible() {
		final List<By> candidates = List.of(By.xpath("//aside"), By.xpath("//nav"),
				By.xpath("//*[contains(translate(@class, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),'sidebar')]"));
		for (final By by : candidates) {
			for (final WebElement element : driver.findElements(by)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isNombreDelNegocioInputVisible() {
		final String lower = "nombre del negocio";
		final By byPlaceholder = By.xpath("//input[contains(translate(@placeholder, 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), "
				+ xpathLiteral(lower) + ")]");
		final By byAriaLabel = By.xpath("//input[contains(translate(@aria-label, 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), "
				+ xpathLiteral(lower) + ")]");
		final By byLabel = By.xpath("//label[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), "
				+ xpathLiteral(lower) + ")]/following::input[1]");

		return hasVisibleElement(byPlaceholder) || hasVisibleElement(byAriaLabel) || hasVisibleElement(byLabel);
	}

	private void typeNombreNegocioIfPresent(final String businessName) {
		final List<By> candidates = List.of(
				By.xpath("//input[contains(translate(@placeholder, 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), 'nombre del negocio')]"),
				By.xpath("//input[contains(translate(@aria-label, 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), 'nombre del negocio')]"),
				By.xpath("//label[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), 'nombre del negocio')]/following::input[1]"));
		for (final By by : candidates) {
			for (final WebElement input : driver.findElements(by)) {
				if (input.isDisplayed()) {
					input.clear();
					input.sendKeys(businessName);
					return;
				}
			}
		}
	}

	private boolean isAnyEmailVisible() {
		final By emailBy = By.xpath(
				"//*[contains(normalize-space(.), '@') and (contains(normalize-space(.), '.com') or contains(normalize-space(.), '.net') or contains(normalize-space(.), '.org'))]");
		return hasVisibleElement(emailBy);
	}

	private boolean isLikelyUserNameVisible() {
		final By textBy = By.xpath("//h1|//h2|//h3|//span|//p|//div");
		for (final WebElement element : driver.findElements(textBy)) {
			if (!element.isDisplayed()) {
				continue;
			}

			final String text = normalize(element.getText());
			if (text.isBlank() || text.length() < 3) {
				continue;
			}
			if (text.contains("@")) {
				continue;
			}

			final String lower = text.toLowerCase(Locale.ROOT);
			if (lower.contains("información general") || lower.contains("business plan") || lower.contains("cambiar plan")
					|| lower.contains("detalles de la cuenta") || lower.contains("tus negocios")) {
				continue;
			}

			// looks like a real person or account label (letters and spaces only).
			if (text.matches("[\\p{L}][\\p{L}\\s]{2,}")) {
				return true;
			}
		}
		return false;
	}

	private boolean isBusinessListVisible() {
		final List<By> candidates = List.of(By.xpath("//ul/li"), By.xpath("//table//tr"),
				By.xpath("//*[contains(translate(@class, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),'business')]"),
				By.xpath("//*[contains(translate(@class, 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzáéíóúüñ'),'negocio')]"));
		for (final By by : candidates) {
			for (final WebElement element : driver.findElements(by)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isLegalContentVisible() {
		final By byParagraph = By.xpath("//p[string-length(normalize-space(.)) > 60]");
		final By byMainText = By.xpath("//main//*[string-length(normalize-space(.)) > 120]");
		return hasVisibleElement(byParagraph) || hasVisibleElement(byMainText);
	}

	private boolean hasVisibleElement(final By by) {
		try {
			for (final WebElement element : driver.findElements(by)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		} catch (final Exception ignored) {
			// best effort
		}
		return false;
	}

	private WebElement firstVisible(final List<WebElement> elements) {
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		return null;
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private Path captureScreenshot(final String name) throws IOException {
		final String safeName = String.format("%02d-%s", ++screenshotCounter, sanitizeForFileName(name));
		final Path screenshotPath = screenshotsDir.resolve(safeName + ".png");
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
		return screenshotPath;
	}

	private String readConfig(final String propertyKey, final String envKey, final String defaultValue) {
		final String property = System.getProperty(propertyKey);
		if (property != null && !property.isBlank()) {
			return property;
		}

		final String env = System.getenv(envKey);
		if (env != null && !env.isBlank()) {
			return env;
		}
		return defaultValue;
	}

	private String sanitizeForFileName(final String raw) {
		return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String normalize(final String text) {
		return text == null ? "" : text.trim().replaceAll("\\s+", " ");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'");
		final StringBuilder result = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				result.append(", \"'\", ");
			}
			result.append("'").append(parts[i]).append("'");
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void execute() throws Exception;
	}
}
