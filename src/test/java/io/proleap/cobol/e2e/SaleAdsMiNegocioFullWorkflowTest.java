package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_ADD_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_GENERAL_INFO = "Informaci\u00F3n General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "T\u00E9rminos y Condiciones";
	private static final String STEP_PRIVACY = "Pol\u00EDtica de Privacidad";

	private static final String TITLE_CREAR_NEGOCIO = "Crear Nuevo Negocio";
	private static final String TEXT_NEGOCIO = "Negocio";
	private static final String TEXT_MI_NEGOCIO = "Mi Negocio";
	private static final String TEXT_AGREGAR_NEGOCIO = "Agregar Negocio";
	private static final String TEXT_ADMIN_NEGOCIOS = "Administrar Negocios";
	private static final String TEXT_INFO_GENERAL = "Informaci\u00F3n General";
	private static final String TEXT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TEXT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String TEXT_LEGAL = "Secci\u00F3n Legal";
	private static final String TEXT_TERMS = "T\u00E9rminos y Condiciones";
	private static final String TEXT_PRIVACY = "Pol\u00EDtica de Privacidad";
	private static final String TEXT_BUSINESS_PLAN = "BUSINESS PLAN";
	private static final String TEXT_CAMBIAR_PLAN = "Cambiar Plan";
	private static final String TEXT_CUENTA_CREADA = "Cuenta creada";
	private static final String TEXT_ESTADO_ACTIVO = "Estado activo";
	private static final String TEXT_IDIOMA = "Idioma seleccionado";
	private static final String TEXT_2_OF_3 = "Tienes 2 de 3 negocios";
	private static final String TEXT_NOMBRE_NEGOCIO = "Nombre del Negocio";
	private static final String TEXT_CANCELAR = "Cancelar";
	private static final String TEXT_CREAR_NEGOCIO = "Crear Negocio";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindow;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";
	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private String loginUrl;

	@Before
	public void setUp() throws IOException {
		loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to run SaleADS E2E validation.",
				loginUrl != null && !loginUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDir = createEvidenceDir();

		driver.get(loginUrl);
		waitForUiLoad();
		appWindow = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep(STEP_LOGIN, this::validateLoginAndSidebar);
		runStep(STEP_MENU, this::openMiNegocioMenu);
		runStep(STEP_ADD_MODAL, this::validateAgregarNegocioModal);
		runStep(STEP_ADMIN_VIEW, this::openAdministrarNegociosAndValidateSections);
		runStep(STEP_GENERAL_INFO, this::validateInformacionGeneral);
		runStep(STEP_ACCOUNT_DETAILS, this::validateDetallesCuenta);
		runStep(STEP_BUSINESSES, this::validateTusNegocios);
		runStep(STEP_TERMS, () -> validateLegalDocument(TEXT_TERMS, "08-terminos"));
		runStep(STEP_PRIVACY, () -> validateLegalDocument(TEXT_PRIVACY, "09-politica"));

		printReport();
		final String failures = report.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(entry -> "- " + entry.getKey() + ": " + entry.getValue().details).reduce("", (left, right) -> left + right + "\n");
		Assert.assertTrue("SaleADS Mi Negocio workflow had validation failures:\n" + failures, failures.isBlank());
	}

	private void validateLoginAndSidebar() throws IOException {
		final Set<String> handlesBeforeLogin = driver.getWindowHandles();
		clickByVisibleText("Sign in with Google", "Iniciar sesi\u00F3n con Google", "Continuar con Google", "Google");
		waitForUiLoad();
		selectGoogleAccountIfPrompted(handlesBeforeLogin, GOOGLE_ACCOUNT_EMAIL);

		wait.until(driver -> isAnyVisibleTextPresent(TEXT_NEGOCIO, TEXT_MI_NEGOCIO, "Dashboard"));
		final By sidebarLocator = By.xpath(
				"//*[(@role='navigation' or contains(@class,'sidebar') or contains(@class,'Side')) and not(contains(@style,'display: none'))]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(sidebarLocator));

		captureScreenshot("01-dashboard-loaded");
		appWindow = driver.getWindowHandle();
	}

	private void openMiNegocioMenu() throws IOException {
		clickByVisibleText(TEXT_NEGOCIO);
		clickByVisibleText(TEXT_MI_NEGOCIO);

		assertVisibleText(TEXT_AGREGAR_NEGOCIO);
		assertVisibleText(TEXT_ADMIN_NEGOCIOS);
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByVisibleText(TEXT_AGREGAR_NEGOCIO);
		assertVisibleText(TITLE_CREAR_NEGOCIO);
		assertVisibleText(TEXT_2_OF_3);
		assertVisibleText(TEXT_CANCELAR);
		assertVisibleText(TEXT_CREAR_NEGOCIO);

		final By nombreNegocioInput = By.xpath(
				"//label[contains(normalize-space(.), " + asXPathLiteral(TEXT_NOMBRE_NEGOCIO) + ")]/following::input[1]"
						+ " | //input[contains(@placeholder, " + asXPathLiteral(TEXT_NOMBRE_NEGOCIO) + ")]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(nombreNegocioInput)).sendKeys("Negocio Prueba Automatizacion");

		captureScreenshot("03-agregar-negocio-modal");
		clickByVisibleText(TEXT_CANCELAR);
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.), "
				+ asXPathLiteral(TITLE_CREAR_NEGOCIO) + ")]")));
	}

	private void openAdministrarNegociosAndValidateSections() throws IOException {
		if (!isAnyVisibleTextPresent(TEXT_ADMIN_NEGOCIOS)) {
			clickByVisibleText(TEXT_MI_NEGOCIO);
		}
		clickByVisibleText(TEXT_ADMIN_NEGOCIOS);
		waitForUiLoad();

		assertVisibleText(TEXT_INFO_GENERAL);
		assertVisibleText(TEXT_DETALLES_CUENTA);
		assertVisibleText(TEXT_TUS_NEGOCIOS);
		assertVisibleText(TEXT_LEGAL);
		captureScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		assertVisibleText(TEXT_INFO_GENERAL);
		assertVisibleText(TEXT_BUSINESS_PLAN);
		assertVisibleText(TEXT_CAMBIAR_PLAN);

		final By emailLocator = By.xpath(
				"//*[contains(normalize-space(.), '@') and (contains(normalize-space(.), '.com') or contains(normalize-space(.), '.ai'))]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(emailLocator));

		final By userNameLocator = By.xpath(
				"//*[contains(@class,'name') or contains(@class,'user') or self::h1 or self::h2][string-length(normalize-space(.)) > 3]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(userNameLocator));
	}

	private void validateDetallesCuenta() {
		assertVisibleText(TEXT_DETALLES_CUENTA);
		assertVisibleText(TEXT_CUENTA_CREADA);
		assertVisibleText(TEXT_ESTADO_ACTIVO);
		assertVisibleText(TEXT_IDIOMA);
	}

	private void validateTusNegocios() {
		assertVisibleText(TEXT_TUS_NEGOCIOS);
		assertVisibleText(TEXT_AGREGAR_NEGOCIO);
		assertVisibleText(TEXT_2_OF_3);

		final By businessListLocator = By.xpath(
				"//*[contains(@class,'business') or contains(@class,'negocio') or self::table or self::ul or self::ol]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(businessListLocator));
	}

	private void validateLegalDocument(final String linkText, final String screenshotPrefix) throws IOException {
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String startHandle = driver.getWindowHandle();
		final String startUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);

		final String destinationHandle = waitForLegalNavigation(handlesBefore, startUrl);
		driver.switchTo().window(destinationHandle);
		waitForUiLoad();

		assertVisibleText(linkText);
		assertLegalContentVisible();
		captureScreenshot(screenshotPrefix + "-legal-page");
		final String finalUrl = driver.getCurrentUrl();

		if (!destinationHandle.equals(startHandle)) {
			driver.close();
			driver.switchTo().window(startHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiLoad();
		appWindow = driver.getWindowHandle();
		if (TEXT_TERMS.equals(linkText)) {
			termsUrl = finalUrl;
		} else if (TEXT_PRIVACY.equals(linkText)) {
			privacyUrl = finalUrl;
		}
	}

	private String waitForLegalNavigation(final Set<String> handlesBefore, final String startUrl) {
		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);

		try {
			return shortWait.until(newWindowHandle(handlesBefore));
		} catch (final TimeoutException ignored) {
			wait.until((ExpectedCondition<Boolean>) driver -> !driver.getCurrentUrl().equals(startUrl));
			return driver.getWindowHandle();
		}
	}

	private ExpectedCondition<String> newWindowHandle(final Set<String> handlesBefore) {
		return webDriver -> {
			final Set<String> currentHandles = webDriver.getWindowHandles();
			if (currentHandles.size() > handlesBefore.size()) {
				for (final String handle : currentHandles) {
					if (!handlesBefore.contains(handle)) {
						return handle;
					}
				}
			}
			return null;
		};
	}

	private void assertLegalContentVisible() {
		final By contentLocator = By.xpath(
				"//main//p[string-length(normalize-space(.)) > 40] | //article//p[string-length(normalize-space(.)) > 40] | //p[string-length(normalize-space(.)) > 120]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(contentLocator));
	}

	private void selectGoogleAccountIfPrompted(final Set<String> handlesBeforeLogin, final String accountEmail) {
		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);
		try {
			final String newHandle = shortWait.until(newWindowHandle(handlesBeforeLogin));
			driver.switchTo().window(newHandle);
		} catch (final TimeoutException ignored) {
			// Google flow may happen in current tab or skip due existing auth session.
		}

		final String currentUrl = driver.getCurrentUrl();
		if (currentUrl != null && currentUrl.contains("accounts.google.com")) {
			try {
				final Optional<WebElement> accountOption = findVisibleElementByText(accountEmail);
				if (accountOption.isPresent()) {
					accountOption.get().click();
				}
			} catch (final Exception ignored) {
				// If selector does not appear, Google might already be authenticated.
			}
		}

		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (!driver.getCurrentUrl().contains("accounts.google.com")) {
				appWindow = handle;
				break;
			}
		}
	}

	private void clickByVisibleText(final String... candidateTexts) {
		WebElement element = null;
		for (final String text : candidateTexts) {
			final Optional<WebElement> current = findVisibleElementByText(text);
			if (current.isPresent()) {
				element = current.get();
				break;
			}
		}
		if (element == null) {
			throw new NoSuchElementException("Could not find clickable element with visible text: "
					+ String.join(", ", candidateTexts));
		}

		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiLoad();
	}

	private Optional<WebElement> findVisibleElementByText(final String text) {
		final String normalizedText = text.toLowerCase();
		final String containsExpr = "contains(translate(normalize-space(.),"
				+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZ\u00C1\u00C9\u00CD\u00D3\u00DA\u00D1',"
				+ "'abcdefghijklmnopqrstuvwxyz\u00E1\u00E9\u00ED\u00F3\u00FA\u00F1'),"
				+ asXPathLiteral(normalizedText.toLowerCase()) + ")";
		final By locator = By.xpath(
				"(//*[self::a or self::button or @role='button' or self::span or self::div or self::li or self::p]["
						+ containsExpr + " and not(ancestor-or-self::*[@aria-hidden='true'])])[1]");

		try {
			return Optional.of(wait.until(ExpectedConditions.visibilityOfElementLocated(locator)));
		} catch (final TimeoutException ignored) {
			return Optional.empty();
		}
	}

	private boolean isAnyVisibleTextPresent(final String... texts) {
		for (final String text : texts) {
			if (findVisibleElementByText(text).isPresent()) {
				return true;
			}
		}
		return false;
	}

	private void assertVisibleText(final String text) {
		if (!findVisibleElementByText(text).isPresent()) {
			throw new AssertionError("Visible text not found: " + text);
		}
	}

	private void waitForUiLoad() {
		wait.until(driver -> "complete"
				.equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
	}

	private void captureScreenshot(final String name) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(name + ".png");
		Files.copy(source.toPath(), target);
	}

	private Path createEvidenceDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private void runStep(final String stepName, final StepExecution action) {
		try {
			action.execute();
			String details = "PASS";
			if (STEP_TERMS.equals(stepName)) {
				details = "PASS | URL: " + termsUrl;
			} else if (STEP_PRIVACY.equals(stepName)) {
				details = "PASS | URL: " + privacyUrl;
			}
			report.put(stepName, StepResult.pass(details));
		} catch (final Exception exception) {
			report.put(stepName, StepResult.fail(exception.getClass().getSimpleName() + ": " + exception.getMessage()));
		}
	}

	private void printReport() {
		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			System.out.println(
					entry.getKey() + " => " + (entry.getValue().passed ? "PASS" : "FAIL") + " | " + entry.getValue().details);
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private String asXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String current = String.valueOf(chars[i]);
			if ("'".equals(current)) {
				builder.append("\"'\"");
			} else {
				builder.append("'").append(current).append("'");
			}
			if (i < chars.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepExecution {
		void execute() throws Exception;
	}

	private static class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
