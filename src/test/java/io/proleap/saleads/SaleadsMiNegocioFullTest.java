package io.proleap.saleads;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
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

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMS = "T\u00e9rminos y Condiciones";
	private static final String STEP_PRIVACY = "Pol\u00edtica de Privacidad";

	private static final String TXT_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String TXT_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String TXT_SECCION_LEGAL = "Secci\u00f3n Legal";
	private static final String TXT_TERMS = "T\u00e9rminos y Condiciones";
	private static final String TXT_PRIVACY = "Pol\u00edtica de Privacidad";

	private static final String ACCOUNT_EMAIL = System.getProperty("saleads.google.email",
			"juanlucasbarbiergarzon@gmail.com");
	private static final String START_URL = firstNonBlank(System.getProperty("saleads.baseUrl"),
			System.getenv("SALEADS_BASE_URL"));
	private static final String BROWSER = System.getProperty("saleads.browser", "chrome");
	private static final boolean HEADLESS = Boolean.parseBoolean(System.getProperty("saleads.headless", "false"));
	private static final boolean E2E_ENABLED = Boolean.parseBoolean(System.getProperty("saleads.e2e", "false"));

	private final Map<String, StepResult> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Path reportPath;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Enable this test with -Dsaleads.e2e=true", E2E_ENABLED);

		evidenceDir = Files.createDirectories(Paths.get("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));
		reportPath = evidenceDir.resolve("final-report.txt");

		driver = createDriver();
		driver.manage().window().setSize(new Dimension(1440, 1024));
		wait = new WebDriverWait(driver, Duration.ofSeconds(25));

		if (START_URL != null) {
			driver.get(START_URL);
			waitForUiLoad();
		} else {
			final String currentUrl = driver.getCurrentUrl();
			Assert.assertNotEquals(
					"No start URL available. Pass -Dsaleads.baseUrl=<url> or set SALEADS_BASE_URL. "
							+ "This test never hardcodes a specific environment URL.",
					"about:blank", currentUrl);
			waitForUiLoad();
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
	public void saleads_mi_negocio_full_test() {
		executeStep(STEP_LOGIN, this::validateLoginWithGoogle);
		executeStep(STEP_MI_NEGOCIO_MENU, this::validateMiNegocioMenu);
		executeStep(STEP_MODAL, this::validateAgregarNegocioModal);
		executeStep(STEP_ADMIN_VIEW, this::validateAdministrarNegociosView);
		executeStep(STEP_INFO_GENERAL, this::validateInformacionGeneral);
		executeStep(STEP_ACCOUNT_DETAILS, this::validateDetallesCuenta);
		executeStep(STEP_TUS_NEGOCIOS, this::validateTusNegocios);
		executeStep(STEP_TERMS, stepResult -> validateLegalDocument(stepResult, TXT_TERMS, "08-terminos"));
		executeStep(STEP_PRIVACY, stepResult -> validateLegalDocument(stepResult, TXT_PRIVACY, "09-privacidad"));

		final List<String> failed = report.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(Map.Entry::getKey).collect(Collectors.toList());
		Assert.assertTrue("Some validations failed: " + failed + ". Check report at " + reportPath.toAbsolutePath(),
				failed.isEmpty());
	}

	private void validateLoginWithGoogle(final StepResult stepResult) throws IOException {
		if (isSidebarVisible()) {
			stepResult.notes.add("Sidebar already visible. Session appears authenticated.");
		} else {
			clickByVisibleText("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google", "Google");
			selectGoogleAccountIfSelectorAppears();
		}

		assertAnyTextVisible("Negocio", "Mi Negocio", "Dashboard");
		Assert.assertTrue("Expected left sidebar navigation to be visible after login.", isSidebarVisible());

		stepResult.evidence.add(takeScreenshot("01-dashboard-loaded"));
	}

	private void validateMiNegocioMenu(final StepResult stepResult) throws IOException {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Administrar Negocios");

		stepResult.evidence.add(takeScreenshot("02-mi-negocio-expanded"));
	}

	private void validateAgregarNegocioModal(final StepResult stepResult) throws IOException {
		clickByVisibleText("Agregar Negocio");

		assertAnyTextVisible("Crear Nuevo Negocio");
		assertAnyTextVisible("Nombre del Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertAnyTextVisible("Cancelar");
		assertAnyTextVisible("Crear Negocio");

		final WebElement modal = findContainerByText("Crear Nuevo Negocio");
		final List<WebElement> inputs = modal.findElements(By.xpath(".//input[not(@type='hidden')]"));
		Assert.assertFalse("Expected the modal to contain a visible input field.", inputs.isEmpty());

		stepResult.evidence.add(takeScreenshot("03-agregar-negocio-modal"));

		final WebElement input = inputs.get(0);
		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatizacion");

		clickByVisibleText("Cancelar");
		waitForTextToDisappear("Crear Nuevo Negocio");
	}

	private void validateAdministrarNegociosView(final StepResult stepResult) throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");

		assertAnyTextVisible(TXT_INFO_GENERAL, "Informacion General");
		assertAnyTextVisible(TXT_ACCOUNT_DETAILS);
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible(TXT_SECCION_LEGAL, "Seccion Legal");

		stepResult.evidence.add(takeScreenshot("04-administrar-negocios-view"));
	}

	private void validateInformacionGeneral(final StepResult stepResult) {
		final WebElement section = findContainerByText(TXT_INFO_GENERAL);
		final String sectionText = normalizeWhitespace(section.getText());

		assertContainsEmail(sectionText);
		assertAnyTextVisible("BUSINESS PLAN");
		assertAnyTextVisible("Cambiar Plan");
		assertLikelyUserNameVisible(section);
		stepResult.notes.add("Informacion General validated.");
	}

	private void validateDetallesCuenta(final StepResult stepResult) {
		assertAnyTextVisible("Cuenta creada");
		assertAnyTextVisible("Estado activo");
		assertAnyTextVisible("Idioma seleccionado");
		stepResult.notes.add("Detalles de la Cuenta validated.");
	}

	private void validateTusNegocios(final StepResult stepResult) {
		final WebElement section = findContainerByText("Tus Negocios");
		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");

		final boolean hasListStructure = !section.findElements(By.xpath(".//ul/li | .//table/tbody/tr | .//article"))
				.isEmpty();
		final boolean hasBusinessLikeEntries = !section
				.findElements(By.xpath(
						".//*[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'negocio') "
								+ "and not(contains(normalize-space(.), 'Tus Negocios')) "
								+ "and not(contains(normalize-space(.), 'Agregar Negocio')) "
								+ "and not(contains(normalize-space(.), 'Tienes 2 de 3 negocios'))]"))
				.isEmpty();
		Assert.assertTrue("Expected the business list to be visible in 'Tus Negocios'.",
				hasListStructure || hasBusinessLikeEntries);

		stepResult.notes.add("Tus Negocios validated.");
	}

	private void validateLegalDocument(final StepResult stepResult, final String linkText, final String screenshotPrefix)
			throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String startingUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);

		final String newWindowHandle = waitForNewWindow(handlesBeforeClick, Duration.ofSeconds(8));
		if (newWindowHandle != null) {
			driver.switchTo().window(newWindowHandle);
			waitForUiLoad();
			assertAnyTextVisible(linkText);
			assertLegalContentVisible();
			stepResult.finalUrl = driver.getCurrentUrl();
			stepResult.evidence.add(takeScreenshot(screenshotPrefix + "-page"));
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else {
			waitForUrlChangeOrText(startingUrl, linkText);
			assertAnyTextVisible(linkText);
			assertLegalContentVisible();
			stepResult.finalUrl = driver.getCurrentUrl();
			stepResult.evidence.add(takeScreenshot(screenshotPrefix + "-page"));
			driver.navigate().back();
			waitForUiLoad();
		}

		assertAnyTextVisible(TXT_SECCION_LEGAL, "Seccion Legal", "Tus Negocios");
	}

	private void executeStep(final String stepName, final StepAction action) {
		final StepResult result = new StepResult();
		try {
			action.run(result);
			result.passed = true;
		} catch (final Throwable throwable) {
			result.passed = false;
			result.error = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
			try {
				result.evidence.add(takeScreenshot("fail-" + slug(stepName)));
			} catch (final Exception screenshotError) {
				result.notes.add("Failed to capture failure screenshot: " + screenshotError.getMessage());
			}
		}
		report.put(stepName, result);
	}

	private WebDriver createDriver() {
		final String browser = BROWSER.toLowerCase(Locale.ROOT);
		if ("firefox".equals(browser)) {
			final FirefoxOptions options = new FirefoxOptions();
			if (HEADLESS) {
				options.addArguments("-headless");
			}
			return new FirefoxDriver(options);
		}

		final ChromeOptions options = new ChromeOptions();
		if (HEADLESS) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--window-size=1440,1024");
		return new ChromeDriver(options);
	}

	private void selectGoogleAccountIfSelectorAppears() {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String newWindow = waitForNewWindow(windowsBefore, Duration.ofSeconds(10));

		if (newWindow != null) {
			driver.switchTo().window(newWindow);
			waitForUiLoad();
		}

		final Optional<WebElement> account = findVisibleElement(By.xpath("//*[contains(normalize-space(), "
				+ xpathLiteral(ACCOUNT_EMAIL) + ") or @data-email=" + xpathLiteral(ACCOUNT_EMAIL) + "]"), 8);
		account.ifPresent(element -> {
			clickAndWait(element);
			waitForUiLoad();
		});

		if (newWindow != null) {
			waitUntilWindowCountAtLeast(1, Duration.ofSeconds(15));
			if (driver.getWindowHandles().contains(originalWindow)) {
				driver.switchTo().window(originalWindow);
			} else {
				driver.switchTo().window(driver.getWindowHandles().iterator().next());
			}
			waitForUiLoad();
		}
	}

	private void clickByVisibleText(final String... texts) {
		for (final String text : texts) {
			final Optional<WebElement> candidate = findClickableByText(text);
			if (candidate.isPresent()) {
				clickAndWait(candidate.get());
				return;
			}
		}

		throw new AssertionError("Could not find clickable element with visible text options: " + Arrays.toString(texts));
	}

	private Optional<WebElement> findClickableByText(final String text) {
		final List<By> selectors = new ArrayList<>();
		selectors.add(By.xpath("//button[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), "
				+ xpathLiteral(text) + ")]"));
		selectors.add(By.xpath("//a[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), "
				+ xpathLiteral(text) + ")]"));
		selectors.add(By.xpath("//*[@role='button'][normalize-space()=" + xpathLiteral(text)
				+ " or contains(normalize-space(), " + xpathLiteral(text) + ")]"));
		selectors.add(By.xpath("//*[self::div or self::span][normalize-space()=" + xpathLiteral(text)
				+ " or contains(normalize-space(), " + xpathLiteral(text)
				+ ")]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"));
		selectors.add(By.xpath("//*[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), "
				+ xpathLiteral(text) + ")]"));

		for (final By selector : selectors) {
			final Optional<WebElement> visible = findFirstVisibleEnabled(selector);
			if (visible.isPresent()) {
				return visible;
			}
		}

		return Optional.empty();
	}

	private Optional<WebElement> findFirstVisibleEnabled(final By by) {
		for (final WebElement element : driver.findElements(by)) {
			if (element.isDisplayed() && element.isEnabled()) {
				return Optional.of(element);
			}
		}
		return Optional.empty();
	}

	private Optional<WebElement> findVisibleElement(final By by, final int timeoutSeconds) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
			return Optional.of(shortWait.until(driverArg -> {
				for (final WebElement element : driverArg.findElements(by)) {
					if (element.isDisplayed()) {
						return element;
					}
				}
				return null;
			}));
		} catch (final TimeoutException timeoutException) {
			return Optional.empty();
		}
	}

	private void clickAndWait(final WebElement element) {
		scrollIntoView(element);
		wait.until(ExpectedConditions.elementToBeClickable(element));
		try {
			element.click();
		} catch (final RuntimeException runtimeException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiLoad();
	}

	private void assertAnyTextVisible(final String... texts) {
		AssertionError lastError = null;
		for (final String text : texts) {
			try {
				wait.until(driverArg -> isTextVisible(text));
				return;
			} catch (final TimeoutException timeoutException) {
				lastError = new AssertionError("Text not visible: " + text, timeoutException);
			}
		}
		throw lastError == null ? new AssertionError("No text options provided for visibility check.") : lastError;
	}

	private boolean isTextVisible(final String text) {
		final By by = By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]");
		for (final WebElement element : driver.findElements(by)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void waitForTextToDisappear(final String text) {
		final By by = By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]");
		wait.until(driverArg -> {
			for (final WebElement element : driverArg.findElements(by)) {
				if (element.isDisplayed()) {
					return false;
				}
			}
			return true;
		});
	}

	private WebElement findContainerByText(final String headingText) {
		final String query = "//*[contains(normalize-space(), " + xpathLiteral(headingText)
				+ ")]/ancestor::*[self::section or self::article or self::div][1]";
		final List<WebElement> candidates = driver.findElements(By.xpath(query));
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				return candidate;
			}
		}
		throw new AssertionError("Could not find visible container for heading/text: " + headingText);
	}

	private void assertContainsEmail(final String text) {
		final Matcher matcher = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(text);
		Assert.assertTrue("Expected user email to be visible.", matcher.find());
	}

	private void assertLikelyUserNameVisible(final WebElement section) {
		final List<WebElement> textNodes = section.findElements(By.xpath(
				".//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::p or self::span or self::strong]"));
		for (final WebElement element : textNodes) {
			if (!element.isDisplayed()) {
				continue;
			}
			final String text = normalizeWhitespace(element.getText());
			if (text.isEmpty()) {
				continue;
			}
			final String lowered = text.toLowerCase(Locale.ROOT);
			if (lowered.contains("informacion general") || lowered.contains("informaci\u00f3n general")
					|| lowered.contains("business plan") || lowered.contains("cambiar plan") || lowered.contains("@")) {
				continue;
			}
			if (text.matches(".*[A-Za-z].*")) {
				return;
			}
		}
		throw new AssertionError("Expected a likely user name to be visible in Informacion General section.");
	}

	private void assertLegalContentVisible() {
		final String bodyText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		Assert.assertTrue("Expected legal content text to be visible.", bodyText.length() > 120);
	}

	private String waitForNewWindow(final Set<String> previousHandles, final Duration timeout) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			return shortWait.until(driverArg -> {
				final Set<String> current = new LinkedHashSet<>(driverArg.getWindowHandles());
				current.removeAll(previousHandles);
				if (!current.isEmpty()) {
					return current.iterator().next();
				}
				return null;
			});
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private void waitForUrlChangeOrText(final String oldUrl, final String text) {
		wait.until(driverArg -> !driverArg.getCurrentUrl().equals(oldUrl) || isTextVisible(text));
		waitForUiLoad();
	}

	private void waitUntilWindowCountAtLeast(final int windowCount, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		shortWait.until(driverArg -> driverArg.getWindowHandles().size() >= windowCount);
	}

	private boolean isSidebarVisible() {
		final List<By> selectors = Arrays.asList(By.xpath("//aside"),
				By.xpath("//*[contains(@class, 'sidebar') or contains(@class, 'SideBar') or contains(@class, 'sidenav')]"),
				By.xpath("//nav[contains(@class, 'sidebar') or contains(@class, 'SideBar') or contains(@class, 'sidenav')]"),
				By.xpath("//nav[.//*[contains(normalize-space(), 'Mi Negocio') or contains(normalize-space(), 'Negocio')]]"));

		for (final By selector : selectors) {
			for (final WebElement element : driver.findElements(selector)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void waitForUiLoad() {
		wait.until(driverArg -> {
			final Object state = ((JavascriptExecutor) driverArg).executeScript("return document.readyState");
			return "complete".equals(state);
		});
	}

	private String takeScreenshot(final String prefix) throws IOException {
		final byte[] data = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final String filename = prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")) + ".png";
		final Path screenshotPath = evidenceDir.resolve(filename);
		Files.write(screenshotPath, data);
		return screenshotPath.toString();
	}

	private void writeFinalReport() throws IOException {
		if (reportPath == null) {
			return;
		}

		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test final report").append(System.lineSeparator());
		builder.append("generated_at=").append(LocalDateTime.now()).append(System.lineSeparator());
		builder.append(System.lineSeparator());

		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final String name = entry.getKey();
			final StepResult result = entry.getValue();
			builder.append(name).append(": ").append(result.passed ? "PASS" : "FAIL").append(System.lineSeparator());
			if (result.error != null) {
				builder.append("  error: ").append(result.error).append(System.lineSeparator());
			}
			if (result.finalUrl != null) {
				builder.append("  final_url: ").append(result.finalUrl).append(System.lineSeparator());
			}
			if (!result.evidence.isEmpty()) {
				builder.append("  evidence: ").append(String.join(", ", result.evidence)).append(System.lineSeparator());
			}
			if (!result.notes.isEmpty()) {
				builder.append("  notes: ").append(String.join(" | ", result.notes)).append(System.lineSeparator());
			}
			builder.append(System.lineSeparator());
		}

		Files.createDirectories(reportPath.getParent());
		Files.write(reportPath, builder.toString().getBytes(StandardCharsets.UTF_8));
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({block: 'center', inline: 'nearest', behavior: 'instant'});", element);
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part;
			if (chars[i] == '\'') {
				part = "\"'\"";
			} else if (chars[i] == '"') {
				part = "'\"'";
			} else {
				part = "'" + chars[i] + "'";
			}
			result.append(part);
			if (i < chars.length - 1) {
				result.append(",");
			}
		}
		result.append(")");
		return result.toString();
	}

	private static String normalizeWhitespace(final String input) {
		return input == null ? "" : input.replaceAll("\\s+", " ").trim();
	}

	private static String slug(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private interface StepAction {
		void run(StepResult stepResult) throws Exception;
	}

	private static class StepResult {
		private boolean passed;
		private String error;
		private String finalUrl;
		private final List<String> evidence = new ArrayList<>();
		private final List<String> notes = new ArrayList<>();
	}
}
