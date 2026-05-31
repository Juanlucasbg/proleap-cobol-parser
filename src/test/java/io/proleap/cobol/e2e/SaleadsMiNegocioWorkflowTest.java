package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private Path screenshotsDirectory;
	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws IOException {
		screenshotsDirectory = Paths.get("target", "surefire-reports", "saleads-mi-negocio");
		Files.createDirectories(screenshotsDirectory);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--disable-gpu");

		if (isHeadlessEnabled()) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String loginUrl = configValue("saleads.login.url", "SALEADS_LOGIN_URL");
		assertNotNull("Missing login URL. Set SALEADS_LOGIN_URL or -Dsaleads.login.url.", loginUrl);

		driver.get(loginUrl);
		waitForUiToLoad();

		executeStep("Login", this::loginWithGoogle);
		executeStep("Mi Negocio menu", List.of("Login"), this::openMiNegocioMenu);
		executeStep("Agregar Negocio modal", List.of("Mi Negocio menu"), this::validateAgregarNegocioModal);
		executeStep("Administrar Negocios view", List.of("Mi Negocio menu"), this::openAdministrarNegocios);
		executeStep("Información General", List.of("Administrar Negocios view"), this::validateInformacionGeneral);
		executeStep("Detalles de la Cuenta", List.of("Administrar Negocios view"), this::validateDetallesCuenta);
		executeStep("Tus Negocios", List.of("Administrar Negocios view"), this::validateTusNegocios);
		executeStep("Términos y Condiciones", List.of("Administrar Negocios view"),
				() -> validateLegalDocument("Términos y Condiciones", "08-terminos-y-condiciones.png"));
		executeStep("Política de Privacidad", List.of("Administrar Negocios view"),
				() -> validateLegalDocument("Política de Privacidad", "09-politica-de-privacidad.png"));

		System.out.println("SALEADS_MI_NEGOCIO_FINAL_REPORT=" + finalReport);
		if (!legalUrls.isEmpty()) {
			System.out.println("SALEADS_MI_NEGOCIO_LEGAL_URLS=" + legalUrls);
		}

		if (!failures.isEmpty()) {
			final String joinedFailures = String.join(System.lineSeparator(), failures);
			throw new AssertionError("SaleADS Mi Negocio workflow failed:" + System.lineSeparator() + joinedFailures);
		}
	}

	private void loginWithGoogle() throws IOException {
		final WebElement googleLogin = waitForAnyVisibleElement(List.of(byClickableTextContains("Sign in with Google"),
				byClickableTextContains("Iniciar sesión con Google"), byClickableTextContains("Continuar con Google"),
				byClickableTextContains("Google")), DEFAULT_TIMEOUT);
		clickAndWait(googleLogin);

		selectGoogleAccountIfVisible(GOOGLE_ACCOUNT);
		waitForUiToLoad();

		final WebElement mainUi = waitForAnyVisibleElement(
				List.of(By.cssSelector("main"), By.xpath("//aside"), byTextContains("Negocio"), byTextContains("Dashboard")),
				Duration.ofSeconds(60));
		assertTrue("Main app interface should be visible after login.", mainUi.isDisplayed());

		final WebElement sidebar = waitForAnyVisibleElement(
				List.of(By.xpath("//aside"), By.xpath("//*[contains(@class,'sidebar')]"), byTextContains("Mi Negocio")),
				DEFAULT_TIMEOUT);
		assertTrue("Left sidebar should be visible after login.", sidebar.isDisplayed());

		captureScreenshot("01-dashboard-loaded.png");
	}

	private void openMiNegocioMenu() throws IOException {
		clickText(List.of("Negocio"));
		clickText(List.of("Mi Negocio"));

		assertVisibleText(List.of("Agregar Negocio"));
		assertVisibleText(List.of("Administrar Negocios"));

		captureScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickText(List.of("Agregar Negocio"));
		assertVisibleText(List.of("Crear Nuevo Negocio"));

		final WebElement businessNameInput = waitForAnyVisibleElement(List.of(
				By.xpath("//label[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', "
						+ "'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), 'nombre del negocio')]/following::input[1]"),
				By.xpath("//input[contains(translate(@placeholder, 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', "
						+ "'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), 'nombre del negocio')]"),
				By.xpath("//input[contains(translate(@aria-label, 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', "
						+ "'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), 'nombre del negocio')]")),
				DEFAULT_TIMEOUT);
		assertTrue("Nombre del Negocio input should be visible.", businessNameInput.isDisplayed());

		assertVisibleText(List.of("Tienes 2 de 3 negocios"));
		assertVisibleText(List.of("Cancelar"));
		assertVisibleText(List.of("Crear Negocio"));

		captureScreenshot("03-agregar-negocio-modal.png");

		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		clickText(List.of("Cancelar"));
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byTextContains("Crear Nuevo Negocio")));
		waitForUiToLoad();
	}

	private void openAdministrarNegocios() throws IOException {
		if (!isVisible(byTextContains("Administrar Negocios"), Duration.ofSeconds(2))) {
			clickText(List.of("Mi Negocio"));
		}

		clickText(List.of("Administrar Negocios"));
		waitForUiToLoad();

		assertVisibleText(List.of("Información General", "Informacion General"));
		assertVisibleText(List.of("Detalles de la Cuenta", "Detalles de la Cuenta"));
		assertVisibleText(List.of("Tus Negocios"));
		assertVisibleText(List.of("Sección Legal", "Seccion Legal"));

		scrollTop();
		captureScreenshot("04-administrar-negocios-page.png");
	}

	private void validateInformacionGeneral() {
		final WebElement infoSection = waitForAnyVisibleElement(List.of(byTextContains("Información General"),
				byTextContains("Informacion General")), DEFAULT_TIMEOUT);
		assertTrue(infoSection.isDisplayed());

		final List<WebElement> emails = driver
				.findElements(By.xpath("//*[contains(@href,'mailto:') or contains(normalize-space(.), '@')]"));
		assertFalse("User email should be visible.", emails.isEmpty());

		final List<WebElement> nameCandidates = driver.findElements(By.xpath(
				"//*[self::h1 or self::h2 or self::h3 or self::p or self::span]"
						+ "[string-length(normalize-space(.)) > 2 and string-length(normalize-space(.)) < 80"
						+ " and not(contains(normalize-space(.), '@'))]"));
		assertFalse("User name should be visible.", nameCandidates.isEmpty());

		assertVisibleText(List.of("BUSINESS PLAN"));
		assertVisibleText(List.of("Cambiar Plan"));
	}

	private void validateDetallesCuenta() {
		assertVisibleText(List.of("Cuenta creada"));
		assertVisibleText(List.of("Estado activo"));
		assertVisibleText(List.of("Idioma seleccionado"));
	}

	private void validateTusNegocios() {
		assertVisibleText(List.of("Tus Negocios"));
		assertVisibleText(List.of("Agregar Negocio"));
		assertVisibleText(List.of("Tienes 2 de 3 negocios"));

		final List<WebElement> rows = driver.findElements(By.xpath(
				"//*[contains(@class,'business') or contains(@class,'negocio') or self::li or self::tr]"
						+ "[string-length(normalize-space(.)) > 0]"));
		assertFalse("Business list should be visible.", rows.isEmpty());
	}

	private void validateLegalDocument(final String legalLinkText, final String screenshotName) throws IOException {
		final String applicationWindowHandle = driver.getWindowHandle();
		final Set<String> previousHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickText(List.of(legalLinkText));
		waitForUiToLoad();

		final String targetHandle = waitForNewWindow(previousHandles, Duration.ofSeconds(10));
		final boolean switchedToNewTab = targetHandle != null;

		if (switchedToNewTab) {
			driver.switchTo().window(targetHandle);
			waitForUiToLoad();
		}

		assertVisibleText(List.of(legalLinkText));
		final List<WebElement> legalContent = driver.findElements(By.xpath(
				"//p[string-length(normalize-space(.)) > 80] | //li[string-length(normalize-space(.)) > 80]"
						+ " | //div[string-length(normalize-space(.)) > 120]"));
		assertFalse("Legal content text should be visible.", legalContent.isEmpty());

		captureScreenshot(screenshotName);
		legalUrls.put(legalLinkText, driver.getCurrentUrl());

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(applicationWindowHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		assertVisibleText(List.of("Sección Legal", "Seccion Legal"));
	}

	private void executeStep(final String reportField, final StepAction action) {
		try {
			action.run();
			finalReport.put(reportField, Boolean.TRUE);
		} catch (final Throwable throwable) {
			finalReport.put(reportField, Boolean.FALSE);
			failures.add(reportField + " -> " + throwable.getMessage());
		}
	}

	private void executeStep(final String reportField, final List<String> prerequisites, final StepAction action) {
		for (final String prerequisite : prerequisites) {
			if (!Boolean.TRUE.equals(finalReport.get(prerequisite))) {
				finalReport.put(reportField, Boolean.FALSE);
				failures.add(reportField + " -> prerequisite failed: " + prerequisite);
				return;
			}
		}

		executeStep(reportField, action);
	}

	private void clickText(final List<String> labels) {
		final List<By> clickableSelectors = new ArrayList<>();
		for (final String label : labels) {
			clickableSelectors.add(byClickableTextContains(label));
		}

		final WebElement target = waitForAnyVisibleElement(clickableSelectors, DEFAULT_TIMEOUT);
		clickAndWait(target);
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.visibilityOf(element));
		scrollIntoView(element);

		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final RuntimeException runtimeException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiToLoad();
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(20));
			final WebElement accountOption = shortWait
					.until(ExpectedConditions.visibilityOfElementLocated(byClickableTextContains(accountEmail)));
			clickAndWait(accountOption);
		} catch (final TimeoutException timeoutException) {
			// The account picker is not always shown when the user is already authenticated.
		}
	}

	private void assertVisibleText(final List<String> acceptedTexts) {
		final List<By> selectors = new ArrayList<>();
		for (final String acceptedText : acceptedTexts) {
			selectors.add(byTextContains(acceptedText));
		}

		final WebElement element = waitForAnyVisibleElement(selectors, DEFAULT_TIMEOUT);
		assertTrue("Expected visible text not found: " + acceptedTexts, element.isDisplayed());
	}

	private boolean isVisible(final By selector, final Duration timeout) {
		try {
			waitForAnyVisibleElement(List.of(selector), timeout);
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private WebElement waitForAnyVisibleElement(final List<By> selectors, final Duration timeout) {
		final long deadline = System.nanoTime() + timeout.toNanos();
		TimeoutException lastTimeout = null;

		while (System.nanoTime() < deadline) {
			for (final By selector : selectors) {
				try {
					final List<WebElement> candidates = driver.findElements(selector);
					for (final WebElement candidate : candidates) {
						if (candidate.isDisplayed()) {
							return candidate;
						}
					}
				} catch (final TimeoutException timeoutException) {
					lastTimeout = timeoutException;
				}
			}

			sleep(Duration.ofMillis(250));
		}

		throw new TimeoutException("Unable to find visible element in selectors: " + selectors, lastTimeout);
	}

	private String waitForNewWindow(final Set<String> existingHandles, final Duration timeout) {
		final long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			for (final String handle : driver.getWindowHandles()) {
				if (!existingHandles.contains(handle)) {
					return handle;
				}
			}

			sleep(Duration.ofMillis(250));
		}

		return null;
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		sleep(Duration.ofMillis(800));
	}

	private void captureScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), screenshotsDirectory.resolve(name), StandardCopyOption.REPLACE_EXISTING);
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});",
				element);
	}

	private void scrollTop() {
		((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");
		waitForUiToLoad();
	}

	private By byTextContains(final String text) {
		final String lowered = text.toLowerCase(Locale.ROOT);
		return By.xpath("//*[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', "
				+ "'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), " + xpathLiteral(lowered) + ")]");
	}

	private By byClickableTextContains(final String text) {
		final String lowered = text.toLowerCase(Locale.ROOT);
		final String expression = "contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', "
				+ "'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), " + xpathLiteral(lowered) + ")";

		return By.xpath("//button[" + expression + "] | //a[" + expression + "] | //*[@role='button' and " + expression
				+ "] | //*[(contains(@class,'btn') or contains(@class,'button')) and " + expression + "] | //span["
				+ expression + "]/ancestor::*[self::button or self::a or @role='button'][1]");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int index = 0; index < parts.length; index++) {
			if (index > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[index]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private String configValue(final String systemProperty, final String environmentVariable) {
		final String fromSystemProperty = System.getProperty(systemProperty);
		if (fromSystemProperty != null && !fromSystemProperty.trim().isEmpty()) {
			return fromSystemProperty.trim();
		}

		final String fromEnvironment = System.getenv(environmentVariable);
		if (fromEnvironment != null && !fromEnvironment.trim().isEmpty()) {
			return fromEnvironment.trim();
		}

		return null;
	}

	private boolean isHeadlessEnabled() {
		final String value = configValue("saleads.headless", "SALEADS_HEADLESS");
		return value == null || Boolean.parseBoolean(value);
	}

	private void sleep(final Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(interruptedException);
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
