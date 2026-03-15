package io.proleap.cobol.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * End-to-end flow for saleads_mi_negocio_full_test.
 *
 * <p>Execution is disabled by default to avoid running a live browser in regular CI:
 * run with -DrunSaleadsMiNegocioFullTest=true and provide -Dsaleads.login.url (or SALEADS_LOGIN_URL).
 */
public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String STEP_POLITICA = "Pol\u00edtica de Privacidad";

	private static final List<String> REPORT_FIELDS = Arrays.asList(
			STEP_LOGIN,
			STEP_MI_NEGOCIO_MENU,
			STEP_AGREGAR_NEGOCIO_MODAL,
			STEP_ADMINISTRAR_NEGOCIOS_VIEW,
			STEP_INFO_GENERAL,
			STEP_DETALLES_CUENTA,
			STEP_TUS_NEGOCIOS,
			STEP_TERMINOS,
			STEP_POLITICA
	);

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor jsExecutor;
	private Path evidenceDir;
	private String applicationWindowHandle;
	private String termsFinalUrl = "N/A";
	private String privacyFinalUrl = "N/A";

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Pattern whitespacePattern = Pattern.compile("\\s+");

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue(
				"Set -DrunSaleadsMiNegocioFullTest=true to execute this live E2E test.",
				Boolean.parseBoolean(System.getProperty("runSaleadsMiNegocioFullTest", "false")));

		driver = createDriver();
		wait = new WebDriverWait(driver, timeoutFromConfig("saleads.timeout.seconds", 30));
		jsExecutor = (JavascriptExecutor) driver;
		evidenceDir = createEvidenceDir();

		final String loginUrl = resolveLoginUrl();
		Assert.assertTrue(
				"Login URL missing. Provide -Dsaleads.login.url or SALEADS_LOGIN_URL.",
				loginUrl != null && !loginUrl.isBlank());

		driver.get(loginUrl);
		applicationWindowHandle = driver.getWindowHandle();
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		executeStep(STEP_LOGIN, this::stepLoginWithGoogle);
		executeStep(STEP_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		executeStep(STEP_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		executeStep(STEP_ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		executeStep(STEP_INFO_GENERAL, this::stepValidateInformacionGeneral);
		executeStep(STEP_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		executeStep(STEP_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		executeStep(STEP_TERMINOS, () -> {
			termsFinalUrl = stepValidateLegalDocument("T\u00e9rminos y Condiciones", "terminos-condiciones");
		});
		executeStep(STEP_POLITICA, () -> {
			privacyFinalUrl = stepValidateLegalDocument("Pol\u00edtica de Privacidad", "politica-privacidad");
		});

		writeFinalReport();

		final List<String> failedSteps = report.entrySet().stream()
				.filter(entry -> !entry.getValue().pass)
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

		Assert.assertTrue("Failed validation steps: " + failedSteps, failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		final Set<String> beforeHandles = driver.getWindowHandles();
		clickByVisibleText("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google", "Google");
		waitForUiToLoad();

		// If Google account selection opens in a new tab/window, choose the account there.
		final String maybeNewWindow = waitForNewWindow(beforeHandles, Duration.ofSeconds(8));
		if (maybeNewWindow != null) {
			driver.switchTo().window(maybeNewWindow);
			optionalClickVisibleText(Duration.ofSeconds(8), ACCOUNT_EMAIL);
			waitForUiToLoad();
			try {
				wait.until(ExpectedConditions.numberOfWindowsToBe(1));
			} catch (TimeoutException ignored) {
				// Continue; some environments keep the OAuth tab open.
			}
		} else {
			optionalClickVisibleText(Duration.ofSeconds(8), ACCOUNT_EMAIL);
		}

		switchToApplicationWindow();
		waitForAnyVisibleText(Duration.ofSeconds(30), "Negocio", "Mi Negocio");
		assertSidebarVisible();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		assertSidebarVisible();
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		waitForVisibleText("Crear Nuevo Negocio");
		waitForVisibleText("Nombre del Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final Optional<WebElement> nameInput = findBusinessNameInput();
		if (nameInput.isPresent()) {
			WebElement input = nameInput.get();
			input.click();
			input.sendKeys(Keys.chord(Keys.CONTROL, "a"), "Negocio Prueba Automatizacion");
		}

		clickByVisibleText("Cancelar");
		wait.until((ExpectedCondition<Boolean>) wd -> !isTextVisible("Crear Nuevo Negocio"));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForVisibleText("Informaci\u00f3n General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Secci\u00f3n Legal");
		captureScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Informaci\u00f3n General");

		final boolean hasEmail = hasVisibleElement(
				section,
				By.xpath(".//*[contains(normalize-space(.), '@')]"));
		Assert.assertTrue("Expected user email in Informacion General section.", hasEmail);

		final List<String> visibleTexts = visibleTexts(section);
		final boolean hasLikelyName = visibleTexts.stream()
				.map(this::normalizeWhitespace)
				.map(String::toLowerCase)
				.anyMatch(text -> !text.isBlank()
						&& !text.contains("@")
						&& !text.contains("informaci")
						&& !text.contains("business plan")
						&& !text.contains("cambiar plan")
						&& text.length() >= 3);
		Assert.assertTrue("Expected user name-like text in Informacion General section.", hasLikelyName);

		waitForVisibleText("BUSINESS PLAN");
		waitForVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");

		final boolean hasListLikeContent = hasVisibleElement(
				section,
				By.xpath(".//li | .//tr | .//article | .//div[contains(@class,'business') or contains(@class,'negocio')]"));
		Assert.assertTrue("Expected business list content in Tus Negocios section.", hasListLikeContent);

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
	}

	private String stepValidateLegalDocument(final String linkText, final String screenshotLabel) throws Exception {
		switchToApplicationWindow();

		final Set<String> beforeHandles = driver.getWindowHandles();
		final String previousUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);

		final String newWindowHandle = waitForNewWindow(beforeHandles, Duration.ofSeconds(10));
		final boolean openedNewWindow = newWindowHandle != null;
		if (openedNewWindow) {
			driver.switchTo().window(newWindowHandle);
		} else {
			wait.until(d -> !Objects.equals(previousUrl, d.getCurrentUrl()));
		}

		waitForVisibleText(linkText);
		validateLegalContentVisible();
		captureScreenshot("05-" + screenshotLabel);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewWindow) {
			driver.close();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		switchToApplicationWindow();
		waitForVisibleText("Secci\u00f3n Legal");
		return finalUrl;
	}

	private void validateLegalContentVisible() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
		final String bodyText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		Assert.assertTrue("Expected legal content text to be visible.", bodyText.length() >= 120);
	}

	private void executeStep(final String stepName, final CheckedStep step) {
		try {
			step.run();
			report.put(stepName, StepResult.pass("PASS"));
		} catch (Throwable throwable) {
			final String message = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
			report.put(stepName, StepResult.fail(message));
		}
	}

	private WebDriver createDriver() {
		final String browser = System.getProperty("saleads.browser", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));

		switch (browser) {
		case "firefox":
			WebDriverManager.firefoxdriver().setup();
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			firefoxOptions.addArguments("--width=1920", "--height=1080");
			return new FirefoxDriver(firefoxOptions);
		case "edge":
			WebDriverManager.edgedriver().setup();
			final EdgeOptions edgeOptions = new EdgeOptions();
			if (headless) {
				edgeOptions.addArguments("--headless=new");
			}
			edgeOptions.addArguments("--window-size=1920,1080");
			return new EdgeDriver(edgeOptions);
		case "chrome":
		default:
			WebDriverManager.chromedriver().setup();
			final ChromeOptions chromeOptions = new ChromeOptions();
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			chromeOptions.addArguments("--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage");
			return new ChromeDriver(chromeOptions);
		}
	}

	private Path createEvidenceDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path outputPath = Paths.get("target", "saleads-evidence", TEST_NAME + "-" + timestamp);
		Files.createDirectories(outputPath);
		return outputPath;
	}

	private Duration timeoutFromConfig(final String propertyName, final int defaultSeconds) {
		final String timeoutValue = System.getProperty(propertyName, String.valueOf(defaultSeconds));
		try {
			return Duration.ofSeconds(Long.parseLong(timeoutValue));
		} catch (NumberFormatException ignored) {
			return Duration.ofSeconds(defaultSeconds);
		}
	}

	private String resolveLoginUrl() {
		return firstNonBlank(
				System.getProperty("saleads.login.url"),
				System.getenv("SALEADS_LOGIN_URL"),
				buildLoginFromBase(System.getProperty("saleads.base.url"), System.getProperty("saleads.login.path", "/login")),
				buildLoginFromBase(System.getenv("SALEADS_BASE_URL"), System.getProperty("saleads.login.path", "/login"))
		);
	}

	private String buildLoginFromBase(final String base, final String loginPath) {
		if (base == null || base.isBlank()) {
			return null;
		}
		final String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		final String normalizedPath = loginPath.startsWith("/") ? loginPath : "/" + loginPath;
		return normalizedBase + normalizedPath;
	}

	private String firstNonBlank(final String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete".equals(jsExecutor.executeScript("return document.readyState")));
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void clickByVisibleText(final String... visibleTexts) {
		final WebElement element = wait.until(driver -> {
			for (String text : visibleTexts) {
				final Optional<WebElement> found = findClickableVisibleByText(text);
				if (found.isPresent()) {
					return found.get();
				}
			}
			return null;
		});

		scrollIntoView(element);
		element.click();
		waitForUiToLoad();
	}

	private void optionalClickVisibleText(final Duration timeout, final String... visibleTexts) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			final WebElement element = shortWait.until(driver -> {
				for (String text : visibleTexts) {
					final Optional<WebElement> found = findClickableVisibleByText(text);
					if (found.isPresent()) {
						return found.get();
					}
				}
				return null;
			});
			scrollIntoView(element);
			element.click();
			waitForUiToLoad();
		} catch (TimeoutException ignored) {
			// Optional click; continue.
		}
	}

	private void waitForAnyVisibleText(final Duration timeout, final String... visibleTexts) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		shortWait.until(driver -> {
			for (String text : visibleTexts) {
				if (isTextVisible(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private WebElement waitForVisibleText(final String visibleText) {
		return wait.until(driver -> findVisibleByText(visibleText).orElse(null));
	}

	private Optional<WebElement> findVisibleByText(final String text) {
		final String escaped = xpathLiteral(text);
		final List<WebElement> candidates = driver.findElements(By.xpath(
				"//*[contains(normalize-space(.), " + escaped + ")]"));
		return candidates.stream().filter(WebElement::isDisplayed).findFirst();
	}

	private Optional<WebElement> findClickableVisibleByText(final String text) {
		final String escaped = xpathLiteral(text);
		final String xpath = String.join(" | ",
				"//button[contains(normalize-space(.), " + escaped + ")]",
				"//a[contains(normalize-space(.), " + escaped + ")]",
				"//*[@role='button' and contains(normalize-space(.), " + escaped + ")]",
				"//*[self::div or self::span or self::p][contains(normalize-space(.), " + escaped + ")]");

		final List<WebElement> candidates = driver.findElements(By.xpath(xpath));
		return candidates.stream()
				.filter(WebElement::isDisplayed)
				.filter(this::isElementUsable)
				.findFirst();
	}

	private boolean isElementUsable(final WebElement element) {
		try {
			return element.isEnabled();
		} catch (NoSuchElementException ignored) {
			return false;
		}
	}

	private Optional<WebElement> findBusinessNameInput() {
		final List<WebElement> labels = driver.findElements(By.xpath("//label[contains(., 'Nombre del Negocio')]"));
		for (WebElement label : labels) {
			if (!label.isDisplayed()) {
				continue;
			}
			final String forId = label.getAttribute("for");
			if (forId != null && !forId.isBlank()) {
				final List<WebElement> linkedInputs = driver.findElements(By.id(forId));
				for (WebElement linkedInput : linkedInputs) {
					if (linkedInput.isDisplayed()) {
						return Optional.of(linkedInput);
					}
				}
			}
		}

		return driver.findElements(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='nombreNegocio' or @aria-label='Nombre del Negocio']"))
				.stream()
				.filter(WebElement::isDisplayed)
				.findFirst();
	}

	private WebElement findSectionByHeading(final String headingText) {
		waitForVisibleText(headingText);
		final String headingLiteral = xpathLiteral(headingText);
		final List<WebElement> containers = driver.findElements(By.xpath(
				"//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5][contains(normalize-space(.), "
						+ headingLiteral + ")]/ancestor::*[self::section or self::div][1]"));
		return containers.stream().filter(WebElement::isDisplayed).findFirst()
				.orElseThrow(() -> new AssertionError("Unable to find section for heading: " + headingText));
	}

	private boolean hasVisibleElement(final WebElement scope, final By locator) {
		return scope.findElements(locator).stream().anyMatch(WebElement::isDisplayed);
	}

	private List<String> visibleTexts(final WebElement scope) {
		return scope.findElements(By.xpath(".//*")).stream()
				.filter(WebElement::isDisplayed)
				.map(WebElement::getText)
				.map(this::normalizeWhitespace)
				.filter(text -> !text.isBlank())
				.collect(Collectors.toList());
	}

	private void assertSidebarVisible() {
		final boolean sidebarVisible = driver.findElements(By.xpath("//aside | //nav")).stream()
				.anyMatch(element -> element.isDisplayed() && normalizeWhitespace(element.getText()).length() >= 3);
		Assert.assertTrue("Expected left sidebar navigation to be visible.", sidebarVisible);
	}

	private String waitForNewWindow(final Set<String> oldHandles, final Duration timeout) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			return shortWait.until(d -> {
				Set<String> handles = d.getWindowHandles();
				if (handles.size() <= oldHandles.size()) {
					return null;
				}
				for (String handle : handles) {
					if (!oldHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (TimeoutException ignored) {
			return null;
		}
	}

	private void switchToApplicationWindow() {
		Set<String> handles = driver.getWindowHandles();
		if (handles.contains(applicationWindowHandle)) {
			driver.switchTo().window(applicationWindowHandle);
			return;
		}

		applicationWindowHandle = handles.iterator().next();
		driver.switchTo().window(applicationWindowHandle);
	}

	private void scrollIntoView(final WebElement element) {
		jsExecutor.executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private String normalizeWhitespace(final String text) {
		return whitespacePattern.matcher(text == null ? "" : text).replaceAll(" ").trim();
	}

	private boolean isTextVisible(final String text) {
		return findVisibleByText(text).isPresent();
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		StringBuilder builder = new StringBuilder("concat(");
		char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			String part = String.valueOf(chars[i]);
			if ("'".equals(part)) {
				builder.append("\"'\"");
			} else {
				builder.append("'").append(part).append("'");
			}
			if (i < chars.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(sanitizeFileName(checkpointName) + ".png");
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private String sanitizeFileName(final String value) {
		return value.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private void writeFinalReport() throws IOException {
		final Path reportFile = evidenceDir.resolve("final-report.md");
		final List<String> lines = new ArrayList<>();
		lines.add("# saleads_mi_negocio_full_test");
		lines.add("");
		lines.add("## Final Report");
		lines.add("");

		for (String field : REPORT_FIELDS) {
			final StepResult result = report.getOrDefault(field, StepResult.fail("Step not executed"));
			lines.add("- " + field + ": " + (result.pass ? "PASS" : "FAIL"));
			if (!result.message.isBlank() && !"PASS".equals(result.message)) {
				lines.add("  - Detail: " + result.message);
			}
		}

		lines.add("");
		lines.add("- T\u00e9rminos y Condiciones final URL: " + termsFinalUrl);
		lines.add("- Pol\u00edtica de Privacidad final URL: " + privacyFinalUrl);
		lines.add("- Evidence directory: " + evidenceDir.toAbsolutePath());

		Files.write(reportFile, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		System.out.println(String.join(System.lineSeparator(), lines));
	}

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean pass;
		private final String message;

		private StepResult(final boolean pass, final String message) {
			this.pass = pass;
			this.message = message == null ? "" : message;
		}

		private static StepResult pass(final String message) {
			return new StepResult(true, message);
		}

		private static StepResult fail(final String message) {
			return new StepResult(false, message);
		}
	}
}
