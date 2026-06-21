package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String LOGIN_FIELD = "Login";
	private static final String MENU_FIELD = "Mi Negocio menu";
	private static final String MODAL_FIELD = "Agregar Negocio modal";
	private static final String ADMIN_FIELD = "Administrar Negocios view";
	private static final String INFO_FIELD = "Información General";
	private static final String ACCOUNT_FIELD = "Detalles de la Cuenta";
	private static final String BUSINESSES_FIELD = "Tus Negocios";
	private static final String TERMS_FIELD = "Términos y Condiciones";
	private static final String PRIVACY_FIELD = "Política de Privacidad";

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, String> stepResults = new LinkedHashMap<>();
	private final Map<String, String> evidenceUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String loginUrl;

	@Before
	public void setUp() throws IOException {
		initializeResults();

		loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"Provide saleads.login.url system property or SALEADS_LOGIN_URL env var for the current SaleADS environment.",
				loginUrl != null && !loginUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		final String headlessSetting = firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"));
		final boolean headless = headlessSetting == null || Boolean.parseBoolean(headlessSetting);
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		evidenceDir = Path.of("target", "saleads-evidence");
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final boolean loginOk = executeStep(LOGIN_FIELD, this::loginWithGoogleAndValidateDashboard);
		final boolean menuOk = executeWithPrecondition(loginOk, MENU_FIELD, this::openMiNegocioMenu);
		final boolean modalOk = executeWithPrecondition(menuOk, MODAL_FIELD, this::validateAgregarNegocioModal);
		final boolean adminOk = executeWithPrecondition(menuOk, ADMIN_FIELD, this::openAdministrarNegociosAndValidateSections);
		final boolean infoOk = executeWithPrecondition(adminOk, INFO_FIELD, this::validateInformacionGeneral);
		final boolean accountOk = executeWithPrecondition(adminOk, ACCOUNT_FIELD, this::validateDetallesCuenta);
		final boolean businessesOk = executeWithPrecondition(adminOk, BUSINESSES_FIELD, this::validateTusNegocios);
		final boolean termsOk = executeWithPrecondition(adminOk, TERMS_FIELD,
				() -> validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos"));
		final boolean privacyOk = executeWithPrecondition(adminOk, PRIVACY_FIELD,
				() -> validateLegalLink("Política de Privacidad", "Política de Privacidad", "09-privacidad"));

		final boolean allPassed = loginOk && menuOk && modalOk && adminOk && infoOk && accountOk && businessesOk && termsOk
				&& privacyOk;
		assertTrue("SaleADS Mi Negocio workflow failed.\n" + renderFinalReport(), allPassed);
	}

	private void loginWithGoogleAndValidateDashboard() throws IOException {
		driver.get(loginUrl);
		waitForUiLoad();

		if (!clickAnyVisibleText(Duration.ofSeconds(12), "Sign in with Google", "Iniciar sesión con Google",
				"Continuar con Google", "Login with Google", "Ingresar con Google")) {
			clickAnyVisibleText(Duration.ofSeconds(12), "Iniciar sesión", "Iniciar Sesión", "Sign in", "Login");
			clickAnyVisibleText(Duration.ofSeconds(12), "Sign in with Google", "Iniciar sesión con Google",
					"Continuar con Google", "Login with Google", "Ingresar con Google");
		}

		waitForUiLoad();
		clickAnyVisibleText(Duration.ofSeconds(8), "juanlucasbarbiergarzon@gmail.com");

		waitUntilAnyTextVisible(Duration.ofSeconds(40), "Negocio", "Mi Negocio", "Dashboard");
		assertTrue("Expected left sidebar navigation to be visible.", isSidebarVisible());
		takeScreenshot("01-dashboard");
	}

	private void openMiNegocioMenu() throws IOException {
		clickAnyVisibleText(Duration.ofSeconds(15), "Negocio");
		waitForUiLoad();
		clickAnyVisibleText(Duration.ofSeconds(15), "Mi Negocio");
		waitForUiLoad();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expandido");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickAnyVisibleText(Duration.ofSeconds(12), "Agregar Negocio");
		waitForUiLoad();

		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		takeScreenshot("03-modal-crear-negocio");

		final WebElement nameInput = waitForVisible(By.xpath("//input[@placeholder='Nombre del Negocio' or @name='nombreNegocio' or @type='text']"),
				Duration.ofSeconds(8));
		nameInput.click();
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatización");
		clickAnyVisibleText(Duration.ofSeconds(10), "Cancelar");
		waitUntilTextNotVisible("Crear Nuevo Negocio", Duration.ofSeconds(10));
	}

	private void openAdministrarNegociosAndValidateSections() throws IOException {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(4))) {
			clickAnyVisibleText(Duration.ofSeconds(8), "Mi Negocio");
		}

		clickAnyVisibleText(Duration.ofSeconds(12), "Administrar Negocios");
		waitForUiLoad();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		assertTextVisible("Información General");
		assertAnyTextVisible("Nombre", "Usuario", "Nombre completo");
		assertAnyTextVisible("Email", "Correo", "Correo electrónico");
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final Matcher matcher = EMAIL_PATTERN.matcher(bodyText);
		assertTrue("Expected at least one visible user email in Información General.", matcher.find());
	}

	private void validateDetallesCuenta() {
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void validateLegalLink(final String linkText, final String headingText, final String screenshotPrefix)
			throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickAnyVisibleText(Duration.ofSeconds(12), linkText);
		waitForUiLoad();

		final String newHandle = waitForNewWindow(beforeHandles, Duration.ofSeconds(10));
		final boolean openedNewTab = newHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		}

		assertTextVisible(headingText);
		final String legalText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected legal content text to be visible for " + linkText + ".",
				legalText != null && legalText.trim().length() > headingText.length() + 80);

		evidenceUrls.put(linkText, driver.getCurrentUrl());
		takeScreenshot(screenshotPrefix);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private boolean executeWithPrecondition(final boolean precondition, final String reportField, final StepAction action) {
		if (!precondition) {
			stepResults.put(reportField, "FAIL - blocked by previous step failure");
			return false;
		}

		return executeStep(reportField, action);
	}

	private boolean executeStep(final String reportField, final StepAction action) {
		try {
			action.run();
			stepResults.put(reportField, "PASS");
			return true;
		} catch (final Exception ex) {
			stepResults.put(reportField, "FAIL - " + ex.getMessage());
			return false;
		}
	}

	private void initializeResults() {
		stepResults.put(LOGIN_FIELD, "FAIL - not executed");
		stepResults.put(MENU_FIELD, "FAIL - not executed");
		stepResults.put(MODAL_FIELD, "FAIL - not executed");
		stepResults.put(ADMIN_FIELD, "FAIL - not executed");
		stepResults.put(INFO_FIELD, "FAIL - not executed");
		stepResults.put(ACCOUNT_FIELD, "FAIL - not executed");
		stepResults.put(BUSINESSES_FIELD, "FAIL - not executed");
		stepResults.put(TERMS_FIELD, "FAIL - not executed");
		stepResults.put(PRIVACY_FIELD, "FAIL - not executed");
	}

	private void assertTextVisible(final String text) {
		final WebElement element = waitForTextVisible(text, DEFAULT_TIMEOUT);
		assertTrue("Expected text to be displayed: " + text, element.isDisplayed());
	}

	private void assertAnyTextVisible(final String... candidates) {
		for (final String candidate : candidates) {
			if (isTextVisible(candidate, Duration.ofSeconds(5))) {
				return;
			}
		}

		throw new AssertionError("Expected one of these labels to be visible: " + String.join(", ", candidates));
	}

	private boolean clickAnyVisibleText(final Duration timeout, final String... texts) {
		for (final String text : texts) {
			try {
				final WebElement element = waitForTextVisible(text, timeout);
				final WebElement clickable = resolveClickableElement(element);
				waitUntilClickable(clickable, timeout);
				clickable.click();
				waitForUiLoad();
				return true;
			} catch (final Exception ignored) {
				// try next text candidate
			}
		}
		return false;
	}

	private WebElement resolveClickableElement(final WebElement element) {
		final String tag = element.getTagName().toLowerCase();
		if ("button".equals(tag) || "a".equals(tag) || "input".equals(tag)) {
			return element;
		}

		final java.util.List<WebElement> ancestors = element
				.findElements(By.xpath("./ancestor::*[self::button or self::a or @role='button'][1]"));
		if (!ancestors.isEmpty()) {
			return ancestors.get(0);
		}

		return element;
	}

	private WebElement waitForTextVisible(final String text, final Duration timeout) {
		final By locator = By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]");
		return waitForVisible(locator, timeout);
	}

	private WebElement waitForVisible(final By locator, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		return shortWait.until(d -> {
			final java.util.List<WebElement> candidates = d.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					return candidate;
				}
			}
			return null;
		});
	}

	private void waitUntilClickable(final WebElement element, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		shortWait.until(d -> {
			try {
				return element.isDisplayed() && element.isEnabled();
			} catch (final Exception ex) {
				return false;
			}
		});
	}

	private void waitForUiLoad() {
		try {
			wait.until((ExpectedCondition<Boolean>) d -> "complete"
					.equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final TimeoutException ignored) {
			// Continue with explicit element waits when readyState is not enough.
		}
	}

	private void waitUntilAnyTextVisible(final Duration timeout, final String... texts) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		shortWait.until(d -> {
			for (final String text : texts) {
				if (isTextVisible(text, Duration.ofSeconds(1))) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isSidebarVisible() {
		try {
			final java.util.List<WebElement> sidebars = driver.findElements(By.xpath("//aside | //nav"));
			for (final WebElement sidebar : sidebars) {
				if (sidebar.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final Exception ex) {
			return false;
		}
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			waitForTextVisible(text, timeout);
			return true;
		} catch (final Exception ex) {
			return false;
		}
	}

	private void waitUntilTextNotVisible(final String text, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		shortWait.until(d -> !isTextVisible(text, Duration.ofSeconds(1)));
	}

	private String waitForNewWindow(final Set<String> previousHandles, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			return shortWait.until(d -> {
				final Set<String> currentHandles = d.getWindowHandles();
				if (currentHandles.size() <= previousHandles.size()) {
					return null;
				}
				for (final String handle : currentHandles) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException ex) {
			return null;
		}
	}

	private void takeScreenshot(final String prefix) throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
				.format(Instant.now());
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(prefix + "-" + timestamp + ".png");
		Files.copy(source.toPath(), target);
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final Path reportPath = evidenceDir.resolve("saleads-mi-negocio-final-report.txt");
		Files.writeString(reportPath, renderFinalReport(), StandardCharsets.UTF_8);
	}

	private String renderFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("Final Report - saleads_mi_negocio_full_test").append(System.lineSeparator());
		for (final Map.Entry<String, String> entry : stepResults.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}
		if (!evidenceUrls.isEmpty()) {
			builder.append("Captured URLs:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : evidenceUrls.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}
		return builder.toString();
	}

	private String firstNonBlank(final String first, final String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		if (second != null && !second.isBlank()) {
			return second;
		}
		return null;
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder concat = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				concat.append(", \"'\", ");
			}
			concat.append("'").append(parts[i]).append("'");
		}
		concat.append(")");
		return concat.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
