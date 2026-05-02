package io.proleap.e2e;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for SaleADS Mi Negocio flow.
 *
 * <p>
 * Configuration (all optional):
 * <ul>
 * <li>saleads.login.url or SALEADS_LOGIN_URL</li>
 * <li>saleads.google.email or SALEADS_GOOGLE_EMAIL (defaults to juanlucasbarbiergarzon@gmail.com)</li>
 * <li>saleads.e2e.enabled or SALEADS_E2E_ENABLED (defaults to false)</li>
 * <li>saleads.headless or SALEADS_HEADLESS (defaults to true)</li>
 * <li>saleads.wait.seconds or SALEADS_WAIT_SECONDS (defaults to 30)</li>
 * <li>saleads.selenium.remote.url or SELENIUM_REMOTE_URL</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter EVIDENCE_DIR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String configuredGoogleEmail;

	@Before
	public void setUp() throws IOException, MalformedURLException {
		final boolean e2eEnabled = Boolean.parseBoolean(
				readConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false").toLowerCase(Locale.ROOT));
		Assume.assumeTrue("Skipping SaleADS E2E test: set saleads.e2e.enabled=true to run it.", e2eEnabled);

		initializeReport();
		evidenceDir = Files.createDirectories(
				Path.of("target", "saleads-evidence", LocalDateTime.now().format(EVIDENCE_DIR_FORMAT)));
		configuredGoogleEmail = readConfig("saleads.google.email", "SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL);

		driver = createDriver();
		wait = new WebDriverWait(driver,
				Duration.ofSeconds(Long.parseLong(readConfig("saleads.wait.seconds", "SALEADS_WAIT_SECONDS", "30"))));
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);

		try {
			driver.manage().window().maximize();
		} catch (Exception ignored) {
			// Headless and remote runners may not support window maximize.
		}

		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", "");
		if (!loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() {
		writeFinalReportFile();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_AGREGAR_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(REPORT_DETALLES, this::stepValidateDetallesCuenta);
		runStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(REPORT_TERMINOS, () -> stepValidateLegalDocument("Términos y Condiciones", "Términos y Condiciones",
				"step-08-terminos.png"));
		runStep(REPORT_PRIVACIDAD, () -> stepValidateLegalDocument("Política de Privacidad", "Política de Privacidad",
				"step-09-privacidad.png"));

		final String summary = buildSummaryText();
		Assert.assertTrue("SaleADS Mi Negocio flow failed.\n" + summary + "\nEvidence: " + evidenceDir.toAbsolutePath(),
				failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		clickFirstByVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Login with Google", "Google"));
		selectGoogleAccountIfPrompted(configuredGoogleEmail);

		assertVisibleContainsIgnoreCase("Negocio");
		assertLeftSidebarVisible();
		takeScreenshot("step-01-dashboard.png");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickIfVisible("Negocio");
		clickFirstByVisibleText(Arrays.asList("Mi Negocio"));

		assertVisibleContainsIgnoreCase("Agregar Negocio");
		assertVisibleContainsIgnoreCase("Administrar Negocios");
		takeScreenshot("step-02-menu-expandido.png");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickFirstByVisibleText(Arrays.asList("Agregar Negocio"));

		assertVisibleContainsIgnoreCase("Crear Nuevo Negocio");
		final WebElement businessNameInput = waitForBusinessNameInput();
		assertVisibleContainsIgnoreCase("Tienes 2 de 3 negocios");
		assertVisibleContainsIgnoreCase("Cancelar");
		assertVisibleContainsIgnoreCase("Crear Negocio");
		takeScreenshot("step-03-modal-agregar-negocio.png");

		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		waitForUiToLoad();
		clickFirstByVisibleText(Arrays.asList("Cancelar"));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		clickIfVisible("Mi Negocio");
		clickFirstByVisibleText(Arrays.asList("Administrar Negocios"));

		assertVisibleContainsIgnoreCase("Información General");
		assertVisibleContainsIgnoreCase("Detalles de la Cuenta");
		assertVisibleContainsIgnoreCase("Tus Negocios");
		assertVisibleContainsIgnoreCase("Sección Legal");
		takeScreenshot("step-04-administrar-negocios.png");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleContainsIgnoreCase("BUSINESS PLAN");
		assertVisibleContainsIgnoreCase("Cambiar Plan");
		assertVisibleContainsIgnoreCase(configuredGoogleEmail);
		assertBodyContainsRegex("[A-Za-zÀ-ÿ]{2,}(\\s+[A-Za-zÀ-ÿ]{2,})+", "Expected a visible user name in account view.");
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleContainsIgnoreCase("Cuenta creada");
		assertVisibleContainsIgnoreCase("Estado activo");
		assertVisibleContainsIgnoreCase("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleContainsIgnoreCase("Tus Negocios");
		assertVisibleContainsIgnoreCase("Agregar Negocio");
		assertVisibleContainsIgnoreCase("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalDocument(final String linkText, final String headingText, final String screenshotFileName)
			throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickFirstByVisibleText(Arrays.asList(linkText));

		wait.until((ExpectedCondition<Boolean>) d -> d != null && (d.getWindowHandles().size() > handlesBeforeClick.size()
				|| !Objects.equals(originalUrl, d.getCurrentUrl())));

		final Set<String> handlesAfterClick = new LinkedHashSet<>(driver.getWindowHandles());
		boolean openedNewTab = false;
		if (handlesAfterClick.size() > handlesBeforeClick.size()) {
			handlesAfterClick.removeAll(handlesBeforeClick);
			final String newHandle = handlesAfterClick.iterator().next();
			driver.switchTo().window(newHandle);
			openedNewTab = true;
		}

		waitForUiToLoad();
		assertVisibleContainsIgnoreCase(headingText);
		assertLegalContentVisible(headingText);
		takeScreenshot(screenshotFileName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();
	}

	private WebDriver createDriver() throws MalformedURLException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--window-size=1920,1080");

		final boolean headless = Boolean
				.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true").toLowerCase(Locale.ROOT));
		if (headless) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = readConfig("saleads.selenium.remote.url", "SELENIUM_REMOTE_URL", "");
		if (!remoteUrl.isBlank()) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}
		return new ChromeDriver(options);
	}

	private void selectGoogleAccountIfPrompted(final String accountEmail) {
		final By accountLocator = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(accountEmail) + ")]");
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));
			final WebElement accountOption = shortWait.until(ExpectedConditions.visibilityOfElementLocated(accountLocator));
			safeClick(accountOption);
			waitForUiToLoad();
		} catch (TimeoutException ignored) {
			// Account chooser is not always shown when SSO session is already active.
		}
	}

	private void clickIfVisible(final String text) {
		final List<WebElement> candidates = driver.findElements(byContainsTextIgnoreCase(text));
		if (!candidates.isEmpty() && candidates.get(0).isDisplayed()) {
			safeClick(resolveClickableElement(candidates.get(0)));
			waitForUiToLoad();
		}
	}

	private void clickFirstByVisibleText(final List<String> candidateTexts) {
		Exception lastError = null;
		for (final String text : candidateTexts) {
			for (final By locator : clickLocatorsForText(text)) {
				try {
					final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
					safeClick(resolveClickableElement(element));
					waitForUiToLoad();
					return;
				} catch (Exception e) {
					lastError = e;
				}
			}
		}
		throw new NoSuchElementException("Could not click any of texts: " + candidateTexts, lastError);
	}

	private List<By> clickLocatorsForText(final String text) {
		final String literal = xpathLiteral(text);
		return Arrays.asList(
				By.xpath("//button[normalize-space(.)=" + literal + "]"),
				By.xpath("//a[normalize-space(.)=" + literal + "]"),
				By.xpath("//*[@role='button' and normalize-space(.)=" + literal + "]"),
				By.xpath("//*[normalize-space(.)=" + literal + "]"),
				By.xpath("//*[contains(normalize-space(.), " + literal + ")]"));
	}

	private WebElement resolveClickableElement(final WebElement element) {
		final List<WebElement> clickableAncestors = element
				.findElements(By.xpath("ancestor-or-self::*[self::button or self::a or @role='button' or @onclick]"));
		if (!clickableAncestors.isEmpty()) {
			return clickableAncestors.get(0);
		}
		return element;
	}

	private void safeClick(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (Exception e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void assertVisibleContainsIgnoreCase(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(byContainsTextIgnoreCase(text)));
	}

	private void assertLeftSidebarVisible() {
		final List<By> sidebarLocators = Arrays.asList(By.cssSelector("aside"), By.cssSelector("[class*='sidebar']"),
				By.xpath("//nav[contains(@class, 'sidebar')]"), By.xpath("//nav"));
		Exception lastError = null;
		for (final By locator : sidebarLocators) {
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return;
			} catch (Exception e) {
				lastError = e;
			}
		}
		throw new AssertionError("Left sidebar navigation was not visible.", lastError);
	}

	private WebElement waitForBusinessNameInput() {
		final List<By> locators = Arrays.asList(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(translate(@name, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'negocio')]"));
		Exception lastError = null;
		for (final By locator : locators) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (Exception e) {
				lastError = e;
			}
		}
		throw new NoSuchElementException("Could not find 'Nombre del Negocio' input.", lastError);
	}

	private void assertBodyContainsRegex(final String regex, final String errorMessage) {
		final Pattern pattern = Pattern.compile(regex);
		final String bodyText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
		Assert.assertTrue(errorMessage, pattern.matcher(bodyText).find());
	}

	private void assertLegalContentVisible(final String headingText) {
		final String bodyText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
		final String normalized = bodyText == null ? "" : bodyText.replaceAll("\\s+", " ").trim();
		Assert.assertTrue("Expected legal content text to be visible for " + headingText + ".",
				normalized.length() > headingText.length() + 100);
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDir.resolve(fileName);
		Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private By byContainsTextIgnoreCase(final String text) {
		final String lowercaseText = text.toLowerCase(Locale.ROOT);
		final String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ";
		final String lower = "abcdefghijklmnopqrstuvwxyzáéíóúüñ";
		return By.xpath(
				"//*[contains(translate(normalize-space(.), " + xpathLiteral(upper) + ", " + xpathLiteral(lower) + "), "
						+ xpathLiteral(lowercaseText) + ")]");
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (Exception ignored) {
			// Some routes may keep loading indicators while still allowing interaction.
		}
		try {
			Thread.sleep(500);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private void runStep(final String reportKey, final StepAction action) {
		try {
			action.execute();
			report.put(reportKey, Boolean.TRUE);
		} catch (Exception e) {
			report.put(reportKey, Boolean.FALSE);
			failures.add(reportKey + ": " + e.getMessage());
			try {
				takeScreenshot("failed-" + sanitizeFileName(reportKey) + ".png");
			} catch (Exception ignored) {
				// Ignore screenshot failures while handling original failure.
			}
		}
	}

	private void initializeReport() {
		report.clear();
		for (final String key : Arrays.asList(REPORT_LOGIN, REPORT_MI_NEGOCIO_MENU, REPORT_AGREGAR_MODAL, REPORT_ADMIN_VIEW,
				REPORT_INFO_GENERAL, REPORT_DETALLES, REPORT_TUS_NEGOCIOS, REPORT_TERMINOS, REPORT_PRIVACIDAD)) {
			report.put(key, Boolean.FALSE);
		}
	}

	private void writeFinalReportFile() {
		if (evidenceDir == null) {
			return;
		}
		try {
			Files.writeString(evidenceDir.resolve("final-report.txt"), buildSummaryText());
		} catch (IOException ignored) {
			// Keep teardown resilient.
		}
	}

	private String buildSummaryText() {
		final StringBuilder builder = new StringBuilder();
		builder.append("Final report").append(System.lineSeparator());
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ")
					.append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL").append(System.lineSeparator());
		}
		if (!legalUrls.isEmpty()) {
			builder.append(System.lineSeparator()).append("Legal URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue())
						.append(System.lineSeparator());
			}
		}
		if (!failures.isEmpty()) {
			builder.append(System.lineSeparator()).append("Failure details").append(System.lineSeparator());
			for (final String failure : failures) {
				builder.append("- ").append(failure).append(System.lineSeparator());
			}
		}
		return builder.toString();
	}

	private String sanitizeFileName(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
	}

	private String readConfig(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return defaultValue;
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part = String.valueOf(chars[i]);
			if (i > 0) {
				sb.append(", ");
			}
			if ("'".equals(part)) {
				sb.append("\"'\"");
			} else if ("\"".equals(part)) {
				sb.append("'\"'");
			} else {
				sb.append("'").append(part).append("'");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void execute() throws Exception;
	}
}
