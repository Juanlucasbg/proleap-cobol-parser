package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String ENABLED_FLAG = "SALEADS_E2E_ENABLED";
	private static final String LOGIN_URL_KEY = "SALEADS_LOGIN_URL";
	private static final String GOOGLE_ACCOUNT_KEY = "SALEADS_GOOGLE_ACCOUNT";
	private static final String EXPECTED_USER_NAME_KEY = "SALEADS_EXPECTED_USER_NAME";
	private static final String HEADLESS_KEY = "SALEADS_HEADLESS";
	private static final String TIMEOUT_SECONDS_KEY = "SALEADS_TIMEOUT_SECONDS";
	private static final String SCREENSHOT_DIR_KEY = "SALEADS_SCREENSHOT_DIR";

	private static final String LOGIN_REPORT = "Login";
	private static final String MI_NEGOCIO_MENU_REPORT = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL_REPORT = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_REPORT = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL_REPORT = "Información General";
	private static final String DETALLES_CUENTA_REPORT = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS_REPORT = "Tus Negocios";
	private static final String TERMINOS_REPORT = "Términos y Condiciones";
	private static final String PRIVACIDAD_REPORT = "Política de Privacidad";

	private static final DateTimeFormatter CHECKPOINT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private String appWindowHandle;
	private boolean executionEnabled;

	private final List<String> failures = new ArrayList<>();
	private final Map<String, Boolean> results = new LinkedHashMap<>();
	private final Map<String, String> finalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		initializeResultMatrix();

		Assume.assumeTrue(
				"Skipping SaleADS E2E test. Set SALEADS_E2E_ENABLED=true to execute it.",
				Boolean.parseBoolean(readConfig(ENABLED_FLAG, "false")));

		final String loginUrl = readConfig(LOGIN_URL_KEY, "").trim();
		Assume.assumeTrue("Skipping SaleADS E2E test. SALEADS_LOGIN_URL is required.", !loginUrl.isEmpty());
		executionEnabled = true;

		final boolean headless = Boolean.parseBoolean(readConfig(HEADLESS_KEY, "true"));
		final long timeoutSeconds = Long.parseLong(readConfig(TIMEOUT_SECONDS_KEY, "30"));
		screenshotDirectory = Paths.get(readConfig(SCREENSHOT_DIR_KEY, "target/saleads-screenshots"));
		Files.createDirectories(screenshotDirectory);

		driver = createChromeDriver(headless);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		driver.manage().window().setSize(new Dimension(1600, 1200));
		driver.navigate().to(loginUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (!executionEnabled) {
			System.out.println("=== SaleADS Mi Negocio Final Report ===");
			System.out.println("SKIPPED: set SALEADS_E2E_ENABLED=true and SALEADS_LOGIN_URL to run this workflow.");
		} else {
			printFinalReport();
		}
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep(LOGIN_REPORT, this::validateLogin);
		runStep(MI_NEGOCIO_MENU_REPORT, this::validateMiNegocioMenu);
		runStep(AGREGAR_NEGOCIO_MODAL_REPORT, this::validateAgregarNegocioModal);
		runStep(ADMINISTRAR_NEGOCIOS_REPORT, this::openAdministrarNegocios);
		runStep(INFORMACION_GENERAL_REPORT, this::validateInformacionGeneral);
		runStep(DETALLES_CUENTA_REPORT, this::validateDetallesCuenta);
		runStep(TUS_NEGOCIOS_REPORT, this::validateTusNegocios);
		runStep(TERMINOS_REPORT, () -> validateLegalLink("Términos y Condiciones", "Terminos y Condiciones", "08-terminos"));
		runStep(PRIVACIDAD_REPORT, () -> validateLegalLink("Política de Privacidad", "Politica de Privacidad", "09-politica"));

		if (!failures.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failures:\n - " + String.join("\n - ", failures));
		}
	}

	private void validateLogin() throws IOException {
		clickByText("Sign in with Google", "Login with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
				"Continuar con Google");
		handleGoogleAccountSelection();
		waitForAppSidebar();
		takeScreenshot("01-dashboard-loaded");
	}

	private void validateMiNegocioMenu() throws IOException {
		clickIfPresent("Negocio");
		clickByText("Mi Negocio");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		typeIfPresent(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder,'Nombre del Negocio')]"),
				"Negocio Prueba Automatizacion");
		clickIfPresent("Cancelar");
	}

	private void openAdministrarNegocios() throws IOException {
		expandMiNegocioIfCollapsed();
		clickByText("Administrar Negocios");
		assertVisibleText("Información General", "Informacion General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal", "Seccion Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String googleAccount = readConfig(GOOGLE_ACCOUNT_KEY, "juanlucasbarbiergarzon@gmail.com");
		assertVisibleText(googleAccount);
		assertUserNameIsVisible(googleAccount);
	}

	private void validateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertBusinessListVisible();
	}

	private void validateLegalLink(final String linkText, final String fallbackLinkText, final String screenshotName)
			throws IOException {
		final String previousUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		clickByText(linkText, fallbackLinkText);

		wait.until(webDriver -> webDriver.getWindowHandles().size() > handlesBefore.size() || !previousUrl.equals(webDriver.getCurrentUrl()));
		final boolean openedNewTab = driver.getWindowHandles().size() > handlesBefore.size();
		if (openedNewTab) {
			switchToNewestWindow(handlesBefore);
		}

		assertLegalHeadingVisible(linkText, fallbackLinkText);
		final String legalPageText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected legal content text to be visible.", legalPageText != null && legalPageText.length() > 120);

		final String reportKey = linkText.contains("Términos") ? TERMINOS_REPORT : PRIVACIDAD_REPORT;
		finalUrls.put(reportKey, driver.getCurrentUrl());
		takeScreenshot(screenshotName);

		returnToApplicationTab(handlesBefore, openedNewTab);
	}

	private void runStep(final String stepName, final StepAction stepAction) {
		try {
			stepAction.run();
			results.put(stepName, Boolean.TRUE);
		} catch (final Exception | AssertionError error) {
			results.put(stepName, Boolean.FALSE);
			failures.add(stepName + " => " + rootMessage(error));
			safeFailureScreenshot(stepName);
		}
	}

	private void waitForAppSidebar() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside | //nav | //*[@role='navigation']")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(anyVisibleTextLocator("Negocio")));
	}

	private void handleGoogleAccountSelection() {
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		wait.withTimeout(Duration.ofSeconds(20))
				.until(webDriver -> webDriver.getWindowHandles().size() > handlesBefore.size() || webDriver.getCurrentUrl().contains("google")
						|| isVisibleNow("Negocio"));
		wait = new WebDriverWait(driver, Duration.ofSeconds(Long.parseLong(readConfig(TIMEOUT_SECONDS_KEY, "30"))));

		if (driver.getWindowHandles().size() > handlesBefore.size()) {
			switchToNewestWindow(handlesBefore);
		}

		if (driver.getCurrentUrl().contains("google")) {
			final String googleAccount = readConfig(GOOGLE_ACCOUNT_KEY, "juanlucasbarbiergarzon@gmail.com");
			clickIfPresent(googleAccount);
			clickIfPresent("Next");
			clickIfPresent("Siguiente");
			clickIfPresent("Continuar");
		}

		switchToApplicationWindow();
	}

	private void expandMiNegocioIfCollapsed() {
		if (!isVisibleNow("Administrar Negocios")) {
			clickIfPresent("Negocio");
			clickByText("Mi Negocio");
		}
		wait.until(ExpectedConditions.visibilityOfElementLocated(anyVisibleTextLocator("Administrar Negocios")));
	}

	private void assertUserNameIsVisible(final String googleAccount) {
		final String expectedUserName = readConfig(EXPECTED_USER_NAME_KEY, "").trim();
		if (!expectedUserName.isEmpty()) {
			assertVisibleText(expectedUserName);
			return;
		}

		final WebElement emailElement = wait.until(ExpectedConditions.visibilityOfElementLocated(anyVisibleTextLocator(googleAccount)));
		final WebElement surroundingContainer = emailElement.findElement(By.xpath("ancestor::*[self::section or self::div][1]"));
		final String[] lines = surroundingContainer.getText().split("\\R");
		for (final String line : lines) {
			final String normalized = line.trim();
			if (!normalized.isEmpty() && !normalized.equals(googleAccount) && !normalized.contains("@")
					&& !normalized.equalsIgnoreCase("BUSINESS PLAN") && !normalized.equalsIgnoreCase("Cambiar Plan")) {
				return;
			}
		}

		fail("User name is not visible near the user email.");
	}

	private void assertBusinessListVisible() {
		final List<By> candidateLocators = Arrays.asList(
				By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]/following::*[self::ul or self::table][1]"),
				By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]/following::*[contains(@class,'list') or contains(@class,'table')][1]"),
				By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]/following::*[contains(@class,'card')][1]"));

		for (final By locator : candidateLocators) {
			if (!driver.findElements(locator).isEmpty() && driver.findElement(locator).isDisplayed()) {
				return;
			}
		}

		fail("Business list section is not visible.");
	}

	private void returnToApplicationTab(final Set<String> handlesBeforeClick, final boolean openedNewTab) {
		if (openedNewTab && driver.getWindowHandles().size() > handlesBeforeClick.size() && !driver.getWindowHandle().equals(appWindowHandle)) {
			driver.close();
		}

		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}

		if (!openedNewTab) {
			driver.navigate().back();
		}

		waitForUiToLoad();
	}

	private void switchToApplicationWindow() {
		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (isVisibleNow("Negocio")) {
				appWindowHandle = handle;
				return;
			}
		}

		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private void switchToNewestWindow(final Set<String> handlesBefore) {
		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void assertVisibleText(final String... values) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(anyVisibleTextLocator(values)));
	}

	private void assertLegalHeadingVisible(final String... values) {
		final String clause = joinTextContainsClauses(values);
		final By headingLocator = By.xpath("//h1[" + clause + "] | //h2[" + clause + "] | //h3[" + clause + "]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(headingLocator));
	}

	private void clickByText(final String... values) {
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(anyInteractiveTextLocator(values)));
		element.click();
		waitForUiToLoad();
	}

	private void clickIfPresent(final String... values) {
		final List<WebElement> elements = driver.findElements(anyInteractiveTextLocator(values));
		if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
			elements.get(0).click();
			waitForUiToLoad();
		}
	}

	private void typeIfPresent(final By locator, final String text) {
		final List<WebElement> elements = driver.findElements(locator);
		if (!elements.isEmpty()) {
			final WebElement input = elements.get(0);
			input.clear();
			input.sendKeys(text);
			waitForUiToLoad();
		}
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> {
			if (!(webDriver instanceof JavascriptExecutor)) {
				return true;
			}

			final Object readyState = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
			return "complete".equals(readyState);
		});
	}

	private boolean isVisibleNow(final String... texts) {
		final List<WebElement> elements = driver.findElements(anyVisibleTextLocator(texts));
		return !elements.isEmpty() && elements.get(0).isDisplayed();
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String timestamp = LocalDateTime.now().format(CHECKPOINT_FORMATTER);
		final Path target = screenshotDirectory.resolve(timestamp + "-" + checkpointName + ".png");
		Files.copy(source.toPath(), target);
	}

	private void safeFailureScreenshot(final String stepName) {
		if (driver == null || screenshotDirectory == null) {
			return;
		}

		try {
			takeScreenshot("failure-" + slug(stepName));
		} catch (final IOException ignored) {
			// Ignore screenshot errors to preserve the root test error.
		}
	}

	private WebDriver createChromeDriver(final boolean headless) {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1200");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		return new ChromeDriver(options);
	}

	private String readConfig(final String key, final String defaultValue) {
		final String propertyValue = System.getProperty(key);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(key);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private By anyVisibleTextLocator(final String... values) {
		final String clause = joinTextContainsClauses(values);
		return By.xpath("//*[" + clause + "]");
	}

	private By anyInteractiveTextLocator(final String... values) {
		final String clause = joinTextContainsClauses(values);
		return By.xpath("//*[self::button or self::a or @role='button' or self::span or self::div][" + clause + "]");
	}

	private String joinTextContainsClauses(final String... values) {
		final List<String> nonEmptyValues = new ArrayList<>();
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				nonEmptyValues.add(value.trim());
			}
		}

		if (nonEmptyValues.isEmpty()) {
			return "false()";
		}

		final StringBuilder builder = new StringBuilder();
		for (int i = 0; i < nonEmptyValues.size(); i++) {
			if (i > 0) {
				builder.append(" or ");
			}
			builder.append("contains(normalize-space(.), ");
			builder.append(xpathLiteral(nonEmptyValues.get(i)));
			builder.append(")");
		}
		return builder.toString();
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				builder.append(", ");
			}
			if (chars[i] == '\'') {
				builder.append("\"'\"");
			} else if (chars[i] == '"') {
				builder.append("'\"'");
			} else {
				builder.append('\'').append(chars[i]).append('\'');
			}
		}
		builder.append(')');
		return builder.toString();
	}

	private String rootMessage(final Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}

		final String message = current.getMessage();
		if (message == null || message.trim().isEmpty()) {
			return current.getClass().getSimpleName();
		}
		return message.trim();
	}

	private String slug(final String value) {
		final Matcher matcher = Pattern.compile("[A-Za-z0-9]+").matcher(value);
		final StringBuilder builder = new StringBuilder();
		while (matcher.find()) {
			if (builder.length() > 0) {
				builder.append('-');
			}
			builder.append(matcher.group().toLowerCase());
		}
		return builder.toString();
	}

	private void initializeResultMatrix() {
		results.clear();
		results.put(LOGIN_REPORT, Boolean.FALSE);
		results.put(MI_NEGOCIO_MENU_REPORT, Boolean.FALSE);
		results.put(AGREGAR_NEGOCIO_MODAL_REPORT, Boolean.FALSE);
		results.put(ADMINISTRAR_NEGOCIOS_REPORT, Boolean.FALSE);
		results.put(INFORMACION_GENERAL_REPORT, Boolean.FALSE);
		results.put(DETALLES_CUENTA_REPORT, Boolean.FALSE);
		results.put(TUS_NEGOCIOS_REPORT, Boolean.FALSE);
		results.put(TERMINOS_REPORT, Boolean.FALSE);
		results.put(PRIVACIDAD_REPORT, Boolean.FALSE);
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		for (final Map.Entry<String, Boolean> resultEntry : results.entrySet()) {
			System.out.println(resultEntry.getKey() + ": " + (resultEntry.getValue() ? "PASS" : "FAIL"));
			if (finalUrls.containsKey(resultEntry.getKey())) {
				System.out.println("  Final URL: " + finalUrls.get(resultEntry.getKey()));
			}
		}
		System.out.println("Screenshot directory: " + (screenshotDirectory == null ? "not-created" : screenshotDirectory.toAbsolutePath()));
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
