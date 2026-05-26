package io.proleap.cobol.e2e;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * End-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * The test is environment-agnostic: it does not hardcode any domain and expects
 * the login URL to be provided through SALEADS_LOGIN_URL.
 * </p>
 */
public class SaleAdsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private static final String LOGIN_FIELD = "Login";
	private static final String MENU_FIELD = "Mi Negocio menu";
	private static final String ADD_MODAL_FIELD = "Agregar Negocio modal";
	private static final String MANAGE_VIEW_FIELD = "Administrar Negocios view";
	private static final String GENERAL_INFO_FIELD = "Información General";
	private static final String ACCOUNT_DETAILS_FIELD = "Detalles de la Cuenta";
	private static final String BUSINESSES_FIELD = "Tus Negocios";
	private static final String TERMS_FIELD = "Términos y Condiciones";
	private static final String PRIVACY_FIELD = "Política de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private final Map<String, String> report = new LinkedHashMap<>();
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this environment-dependent test.",
				envFlag("SALEADS_E2E_ENABLED", false));

		initializeReport();
		evidenceDirectory = Paths.get("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
		Files.createDirectories(evidenceDirectory);

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-dev-shm-usage", "--no-sandbox", "--window-size=1920,1080");

		if (envFlag("SALEADS_HEADLESS", false)) {
			options.addArguments("--headless=new");
		}

		final String chromeBinary = System.getenv("SALEADS_CHROME_BINARY");
		if (hasText(chromeBinary)) {
			options.setBinary(chromeBinary);
		}

		final String userDataDir = System.getenv("SALEADS_CHROME_USER_DATA_DIR");
		if (hasText(userDataDir)) {
			options.addArguments("--user-data-dir=" + userDataDir);
		}

		final String profileDirectory = System.getenv("SALEADS_CHROME_PROFILE_DIRECTORY");
		if (hasText(profileDirectory)) {
			options.addArguments("--profile-directory=" + profileDirectory);
		}

		driver = new ChromeDriver(options);
		driver.manage().window().maximize();
		wait = new WebDriverWait(driver, Duration.ofSeconds(readTimeoutSeconds()));

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"SALEADS_LOGIN_URL is required (the test must not hardcode environment-specific domains).",
				hasText(loginUrl));
		driver.get(loginUrl);
		waitForUiToSettle();
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
		runStep(LOGIN_FIELD, this::stepLoginWithGoogle);
		runStep(MENU_FIELD, this::stepOpenMiNegocioMenu);
		runStep(ADD_MODAL_FIELD, this::stepValidateAgregarNegocioModal);
		runStep(MANAGE_VIEW_FIELD, this::stepOpenAdministrarNegocios);
		runStep(GENERAL_INFO_FIELD, this::stepValidateInformacionGeneral);
		runStep(ACCOUNT_DETAILS_FIELD, this::stepValidateDetallesCuenta);
		runStep(BUSINESSES_FIELD, this::stepValidateTusNegocios);
		runStep(TERMS_FIELD, this::stepValidateTerminosYCondiciones);
		runStep(PRIVACY_FIELD, this::stepValidatePoliticaPrivacidad);

		final List<String> failures = report.entrySet().stream().filter(entry -> entry.getValue().startsWith("FAIL"))
				.map(entry -> "- " + entry.getKey() + ": " + entry.getValue()).collect(Collectors.toList());

		if (!failures.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow failed:\n" + String.join("\n", failures));
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		clickFirstVisibleByText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Google"));
		handleGoogleAccountSelectorIfPresent();

		assertTrue("Main application interface should be visible after login.",
				anyVisibleText(Arrays.asList("Dashboard", "Panel", "Mi Negocio", "Negocio", "Administrar Negocios")));

		final WebElement sidebar = findFirstVisible(Arrays.asList(By.xpath("//aside"), By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar') or contains(@class,'Sidebar')]")), Duration.ofSeconds(20));
		assertNotNull("Left sidebar navigation should be visible.", sidebar);
		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		expandMiNegocioMenuIfNeeded();
		assertVisibleByText("Agregar Negocio");
		assertVisibleByText("Administrar Negocios");
		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickFirstVisibleByText(Arrays.asList("Agregar Negocio"));
		assertVisibleByText("Crear Nuevo Negocio");
		assertVisibleByText("Nombre del Negocio");
		assertVisibleByText("Tienes 2 de 3 negocios");
		assertVisibleByText("Cancelar");
		assertVisibleByText("Crear Negocio");
		captureScreenshot("03_agregar_negocio_modal");

		final WebElement businessNameInput = findFirstVisible(
				Arrays.asList(By.xpath("//input[@placeholder='Nombre del Negocio']"),
						By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]")),
				Duration.ofSeconds(10));
		assertNotNull("Business name input should be available.", businessNameInput);
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		clickFirstVisibleByText(Arrays.asList("Cancelar"));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioMenuIfNeeded();
		clickFirstVisibleByText(Arrays.asList("Administrar Negocios"));
		assertVisibleByText("Información General");
		assertVisibleByText("Detalles de la Cuenta");
		assertVisibleByText("Tus Negocios");
		assertVisibleByText("Sección Legal");
		captureScreenshot("04_administrar_negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleByText("Información General");
		assertVisibleByText("BUSINESS PLAN");
		assertVisibleByText("Cambiar Plan");

		final String pageText = visiblePageText();
		assertTrue("User email should be visible in account information.",
				pageText.matches("(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*"));
		assertTrue("User name should be visible in account information.",
				pageText.matches("(?s).*(Nombre|Usuario|Perfil|Cuenta).*"));
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleByText("Detalles de la Cuenta");
		assertVisibleByText("Cuenta creada");
		assertVisibleByText("Estado activo");
		assertVisibleByText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleByText("Tus Negocios");
		assertVisibleByText("Agregar Negocio");
		assertVisibleByText("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		termsUrl = openLegalLinkAndValidate("Términos y Condiciones", "Términos y Condiciones",
				"08_terminos_condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		privacyUrl = openLegalLinkAndValidate("Política de Privacidad", "Política de Privacidad", "09_politica_privacidad");
	}

	private String openLegalLinkAndValidate(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String urlBeforeClick = safeCurrentUrl();

		clickFirstVisibleByText(Arrays.asList(linkText));
		waitForNavigationOrNewTab(handlesBefore, urlBeforeClick);

		final Set<String> handlesAfter = driver.getWindowHandles();
		final boolean openedNewTab = handlesAfter.size() > handlesBefore.size();
		if (openedNewTab) {
			final String newHandle = handlesAfter.stream().filter(handle -> !handlesBefore.contains(handle)).findFirst()
					.orElseThrow(() -> new IllegalStateException("A new tab was expected but not found."));
			driver.switchTo().window(newHandle);
			waitForUiToSettle();
		}

		assertVisibleByText(expectedHeading);
		assertTrue("Legal page content should be visible for " + expectedHeading, visiblePageText().trim().length() > 180);
		captureScreenshot(screenshotName);
		final String finalUrl = safeCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else if (!urlBeforeClick.equals(finalUrl)) {
			driver.navigate().back();
		}

		waitForUiToSettle();
		return finalUrl;
	}

	private void waitForNavigationOrNewTab(final Set<String> handlesBefore, final String urlBeforeClick) {
		final ExpectedCondition<Boolean> moved = webDriver -> {
			if (webDriver == null) {
				return false;
			}

			final boolean newTab = webDriver.getWindowHandles().size() > handlesBefore.size();
			final String currentUrl = safeCurrentUrl();
			final boolean sameTabNavigation = hasText(currentUrl) && !currentUrl.equals(urlBeforeClick);
			return newTab || sameTabNavigation;
		};

		try {
			wait.until(moved);
		} catch (final TimeoutException timeoutException) {
			// Some environments keep URL stable while loading content in-place.
		}
	}

	private void expandMiNegocioMenuIfNeeded() {
		if (allTextsVisible(Arrays.asList("Agregar Negocio", "Administrar Negocios"))) {
			return;
		}

		clickWhenVisible(Arrays.asList("Negocio", "Mi Negocio"));
		waitForUiToSettle();

		if (!allTextsVisible(Arrays.asList("Agregar Negocio", "Administrar Negocios"))) {
			clickWhenVisible(Arrays.asList("Mi Negocio", "Negocio"));
			waitForUiToSettle();
		}
	}

	private void handleGoogleAccountSelectorIfPresent() {
		final String originHandle = driver.getWindowHandle();
		switchToGoogleWindowIfPresent();

		final By accountOption = By.xpath("//*[contains(normalize-space(.),"
				+ toXpathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]");
		if (isVisible(accountOption, Duration.ofSeconds(12))) {
			final WebElement option = wait.until(ExpectedConditions.elementToBeClickable(accountOption));
			clickAndWait(option);
		}

		if (!driver.getWindowHandle().equals(originHandle) && driver.getWindowHandles().contains(originHandle)) {
			driver.switchTo().window(originHandle);
			waitForUiToSettle();
		}
	}

	private void switchToGoogleWindowIfPresent() {
		final Optional<String> googleHandle = driver.getWindowHandles().stream().filter(handle -> {
			driver.switchTo().window(handle);
			final String currentUrl = safeCurrentUrl();
			return hasText(currentUrl) && currentUrl.contains("accounts.google");
		}).findFirst();

		if (googleHandle.isPresent()) {
			driver.switchTo().window(googleHandle.get());
		}
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			report.put(reportField, "PASS");
		} catch (final Throwable throwable) {
			report.put(reportField, "FAIL - " + rootMessage(throwable));

			try {
				captureScreenshot("failure_" + slugify(reportField));
			} catch (final Exception screenshotException) {
				report.put(reportField,
						report.get(reportField) + " | screenshot_error=" + rootMessage(screenshotException));
			}
		}
	}

	private void initializeReport() {
		report.put(LOGIN_FIELD, "NOT_RUN");
		report.put(MENU_FIELD, "NOT_RUN");
		report.put(ADD_MODAL_FIELD, "NOT_RUN");
		report.put(MANAGE_VIEW_FIELD, "NOT_RUN");
		report.put(GENERAL_INFO_FIELD, "NOT_RUN");
		report.put(ACCOUNT_DETAILS_FIELD, "NOT_RUN");
		report.put(BUSINESSES_FIELD, "NOT_RUN");
		report.put(TERMS_FIELD, "NOT_RUN");
		report.put(PRIVACY_FIELD, "NOT_RUN");
	}

	private void writeFinalReport() throws IOException {
		if (report.isEmpty() || evidenceDirectory == null) {
			return;
		}

		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("timestamp=" + LocalDateTime.now());
		lines.add("");

		for (final Map.Entry<String, String> entry : report.entrySet()) {
			lines.add(entry.getKey() + ": " + entry.getValue());
		}

		lines.add("");
		lines.add("Términos y Condiciones URL: " + termsUrl);
		lines.add("Política de Privacidad URL: " + privacyUrl);

		Files.write(evidenceDirectory.resolve("final-report.txt"), lines);
	}

	private void clickFirstVisibleByText(final List<String> texts) {
		final WebElement element = findFirstVisibleByText(texts, Duration.ofSeconds(20));
		assertNotNull("No visible element found for text candidates: " + texts, element);
		clickAndWait(element);
	}

	private void clickWhenVisible(final List<String> texts) {
		final WebElement element = findFirstVisibleByText(texts, Duration.ofSeconds(10));
		if (element != null) {
			clickAndWait(element);
		}
	}

	private WebElement findFirstVisibleByText(final List<String> texts, final Duration timeout) {
		for (final String text : texts) {
			final By exact = By.xpath("//*[normalize-space(.)=" + toXpathLiteral(text) + "]");
			final WebElement exactElement = findVisible(exact, timeout);
			if (exactElement != null) {
				return exactElement;
			}

			final By contains = By.xpath("//*[contains(normalize-space(.)," + toXpathLiteral(text) + ")]");
			final WebElement containsElement = findVisible(contains, Duration.ofSeconds(6));
			if (containsElement != null) {
				return containsElement;
			}
		}

		return null;
	}

	private WebElement findVisible(final By locator, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private WebElement findFirstVisible(final List<By> locators, final Duration timeout) {
		for (final By locator : locators) {
			final WebElement found = findVisible(locator, timeout);
			if (found != null) {
				return found;
			}
		}

		return null;
	}

	private boolean anyVisibleText(final List<String> texts) {
		return texts.stream().anyMatch(this::isTextVisible);
	}

	private boolean allTextsVisible(final List<String> texts) {
		return texts.stream().allMatch(this::isTextVisible);
	}

	private boolean isTextVisible(final String text) {
		final By exact = By.xpath("//*[normalize-space(.)=" + toXpathLiteral(text) + "]");
		if (isVisible(exact, Duration.ofSeconds(6))) {
			return true;
		}

		final By contains = By.xpath("//*[contains(normalize-space(.)," + toXpathLiteral(text) + ")]");
		return isVisible(contains, Duration.ofSeconds(3));
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void assertVisibleByText(final String text) {
		assertTrue("Expected visible text: " + text, isTextVisible(text));
	}

	private String visiblePageText() {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		if (driver == null || evidenceDirectory == null) {
			return;
		}

		final Path screenshot = evidenceDirectory.resolve(
				checkpointName + "_" + LocalDateTime.now().format(FILE_STAMP) + ".png");
		final byte[] image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshot, image);
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiToSettle();
	}

	private void waitForUiToSettle() {
		if (driver == null) {
			return;
		}

		final ExpectedCondition<Boolean> domReady = current -> {
			final Object state = ((JavascriptExecutor) current).executeScript("return document.readyState");
			return state != null && "complete".equals(state.toString());
		};

		try {
			wait.until(domReady);
		} catch (final Exception ignored) {
			// Single-page app updates do not always trigger full document-ready changes.
		}

		try {
			Thread.sleep(600L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private int readTimeoutSeconds() {
		final String configured = System.getenv("SALEADS_TIMEOUT_SECONDS");
		if (hasText(configured)) {
			try {
				return Integer.parseInt(configured.trim());
			} catch (final NumberFormatException ignored) {
				return 35;
			}
		}

		return 35;
	}

	private boolean envFlag(final String name, final boolean defaultValue) {
		final String value = System.getenv(name);
		if (!hasText(value)) {
			return defaultValue;
		}

		return Boolean.parseBoolean(value.trim());
	}

	private boolean hasText(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (final Exception exception) {
			return "";
		}
	}

	private String rootMessage(final Throwable throwable) {
		Throwable cursor = throwable;
		while (cursor.getCause() != null) {
			cursor = cursor.getCause();
		}
		final String message = cursor.getMessage();
		return hasText(message) ? message : cursor.getClass().getSimpleName();
	}

	private String slugify(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
