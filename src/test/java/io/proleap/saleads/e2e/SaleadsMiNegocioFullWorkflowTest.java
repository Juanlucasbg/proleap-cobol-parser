package io.proleap.saleads.e2e;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final StepReport report = new StepReport();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;
	private String appWindowHandle;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		artifactsDir = Paths.get("target", "saleads-e2e-artifacts", LocalDateTime.now().format(TS_FORMAT));
		Files.createDirectories(artifactsDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1200");
		if (envBoolean("SALEADS_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}

		final String profileDir = env("SALEADS_CHROME_PROFILE_DIR");
		if (profileDir != null) {
			options.addArguments("--user-data-dir=" + profileDir);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(envInt("SALEADS_WAIT_SECONDS", 20)));

		final String loginUrl = env("SALEADS_LOGIN_URL");
		if (loginUrl != null) {
			driver.get(loginUrl);
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() throws IOException {
		writeReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", this::stepValidateTerminos);
		executeStep("Política de Privacidad", this::stepValidatePrivacidad);

		if (report.hasFailures()) {
			fail("SaleADS Mi Negocio workflow has failed checks. Review report: " + artifactsDir.resolve("final-report.txt"));
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		appWindowHandle = driver.getWindowHandle();

		clickByFirstVisibleText(List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"),
				Duration.ofSeconds(20));
		waitForUiLoad();

		// Optional Google account picker step.
		clickByVisibleTextIfPresent("juanlucasbarbiergarzon@gmail.com", Duration.ofSeconds(8));
		waitForUiLoad();

		waitForAnyVisibleText(List.of("Mi Negocio", "Negocio"), Duration.ofSeconds(30));
		expectVisibleText("Mi Negocio", Duration.ofSeconds(10), "Left sidebar navigation should be visible after login.");
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByVisibleTextIfPresent("Negocio", Duration.ofSeconds(10));
		clickByVisibleText("Mi Negocio", Duration.ofSeconds(10));

		expectVisibleText("Agregar Negocio", Duration.ofSeconds(10), "Expected 'Agregar Negocio' option in expanded submenu.");
		expectVisibleText("Administrar Negocios", Duration.ofSeconds(10),
				"Expected 'Administrar Negocios' option in expanded submenu.");
		takeScreenshot("02-mi-negocio-expanded-menu");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio", Duration.ofSeconds(10));

		expectVisibleText("Crear Nuevo Negocio", Duration.ofSeconds(10), "Expected modal title 'Crear Nuevo Negocio'.");
		expectVisibleText("Nombre del Negocio", Duration.ofSeconds(10), "Expected business name input label.");
		expectVisibleText("Tienes 2 de 3 negocios", Duration.ofSeconds(10), "Expected quota text.");
		expectVisibleText("Cancelar", Duration.ofSeconds(10), "Expected cancel button.");
		expectVisibleText("Crear Negocio", Duration.ofSeconds(10), "Expected create button.");
		takeScreenshot("03-agregar-negocio-modal");

		typeIntoBusinessNameIfPresent("Negocio Prueba Automatizacion");
		clickByVisibleText("Cancelar", Duration.ofSeconds(10));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		clickByVisibleTextIfPresent("Mi Negocio", Duration.ofSeconds(8));
		clickByVisibleText("Administrar Negocios", Duration.ofSeconds(10));

		expectVisibleText("Información General", Duration.ofSeconds(15), "Expected 'Información General' section.");
		expectVisibleText("Detalles de la Cuenta", Duration.ofSeconds(15), "Expected 'Detalles de la Cuenta' section.");
		expectVisibleText("Tus Negocios", Duration.ofSeconds(15), "Expected 'Tus Negocios' section.");
		expectVisibleText("Sección Legal", Duration.ofSeconds(15), "Expected 'Sección Legal' section.");
		takeScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		expectVisibleAny(List.of("BUSINESS PLAN", "Business Plan"), Duration.ofSeconds(8), "Expected plan label.");
		expectVisibleText("Cambiar Plan", Duration.ofSeconds(8), "Expected 'Cambiar Plan' button.");

		final List<WebElement> userSignals = driver
				.findElements(By.xpath("//*[contains(text(), '@') or contains(normalize-space(), 'gmail.com')]"));
		if (userSignals.isEmpty()) {
			throw new AssertionError("Expected user identity data (name/email) in 'Información General'.");
		}
	}

	private void stepValidateDetallesCuenta() {
		expectVisibleAny(List.of("Cuenta creada", "Cuenta Creada"), Duration.ofSeconds(8), "Expected account creation text.");
		expectVisibleAny(List.of("Estado activo", "Estado Activo"), Duration.ofSeconds(8), "Expected active status text.");
		expectVisibleAny(List.of("Idioma seleccionado", "Idioma Seleccionado"), Duration.ofSeconds(8),
				"Expected selected language text.");
	}

	private void stepValidateTusNegocios() {
		expectVisibleText("Tus Negocios", Duration.ofSeconds(8), "Expected businesses section.");
		expectVisibleText("Agregar Negocio", Duration.ofSeconds(8), "Expected add business button.");
		expectVisibleText("Tienes 2 de 3 negocios", Duration.ofSeconds(8), "Expected business quota text.");

		final List<WebElement> businessRows = driver
				.findElements(By.xpath("//*[contains(@class,'business') or contains(normalize-space(),'Negocio')]"));
		if (businessRows.isEmpty()) {
			throw new AssertionError("Expected business list/content inside 'Tus Negocios'.");
		}
	}

	private void stepValidateTerminos() throws IOException {
		ensureOnApplicationWindow();
		final String previousUrl = driver.getCurrentUrl();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickByVisibleText("Términos y Condiciones", Duration.ofSeconds(10));
		final NavigationResult navigation = awaitNavigation(previousUrl, beforeHandles);

		expectVisibleText("Términos y Condiciones", Duration.ofSeconds(15), "Expected terms heading.");
		expectLegalContent();
		termsUrl = driver.getCurrentUrl();
		takeScreenshot("05-terminos-y-condiciones");

		restoreApplicationContext(navigation.openedNewTab);
	}

	private void stepValidatePrivacidad() throws IOException {
		ensureOnApplicationWindow();
		final String previousUrl = driver.getCurrentUrl();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickByVisibleText("Política de Privacidad", Duration.ofSeconds(10));
		final NavigationResult navigation = awaitNavigation(previousUrl, beforeHandles);

		expectVisibleText("Política de Privacidad", Duration.ofSeconds(15), "Expected privacy heading.");
		expectLegalContent();
		privacyUrl = driver.getCurrentUrl();
		takeScreenshot("06-politica-de-privacidad");

		restoreApplicationContext(navigation.openedNewTab);
	}

	private void executeStep(final String stepName, final StepAction action) throws IOException {
		try {
			action.run();
			report.pass(stepName);
		} catch (final Throwable t) {
			report.fail(stepName, t.getMessage());
			takeScreenshot(safeName(stepName) + "-failure");
		}
	}

	private void writeReport() throws IOException {
		final StringBuilder out = new StringBuilder();
		out.append("saleads_mi_negocio_full_test").append('\n');
		out.append("Artifacts directory: ").append(artifactsDir.toAbsolutePath()).append('\n').append('\n');
		for (final Map.Entry<String, StepOutcome> entry : report.results.entrySet()) {
			out.append(entry.getKey()).append(": ").append(entry.getValue().status);
			if (entry.getValue().message != null) {
				out.append(" - ").append(entry.getValue().message);
			}
			out.append('\n');
		}
		out.append('\n');
		out.append("Términos y Condiciones URL: ").append(termsUrl).append('\n');
		out.append("Política de Privacidad URL: ").append(privacyUrl).append('\n');

		Files.writeString(artifactsDir.resolve("final-report.txt"), out.toString(), StandardCharsets.UTF_8);
	}

	private void clickByVisibleText(final String text, final Duration timeout) {
		clickByFirstVisibleText(List.of(text), timeout);
	}

	private void clickByVisibleTextIfPresent(final String text, final Duration timeout) {
		try {
			clickByVisibleText(text, timeout);
		} catch (final RuntimeException ignored) {
			// Optional click path.
		}
	}

	private void clickByFirstVisibleText(final List<String> texts, final Duration timeout) {
		RuntimeException lastError = null;
		for (final String text : texts) {
			try {
				clickOnce(text, timeout);
				return;
			} catch (final RuntimeException e) {
				lastError = e;
			}
		}
		throw lastError == null ? new NoSuchElementException("Could not click any text candidate: " + texts) : lastError;
	}

	private void clickOnce(final String text, final Duration timeout) {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			final List<WebElement> elements = driver.findElements(By.xpath(clickableXPath(text)));
			for (final WebElement element : elements) {
				if (!element.isDisplayed()) {
					continue;
				}
				try {
					((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
					element.click();
					waitForUiLoad();
					return;
				} catch (final Exception clickError) {
					try {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
						waitForUiLoad();
						return;
					} catch (final Exception jsClickError) {
						// Keep trying until timeout.
					}
				}
			}

			sleep(250);
		}
		throw new NoSuchElementException("Could not click a visible element with text: " + text);
	}

	private void waitForAnyVisibleText(final List<String> texts, final Duration timeout) {
		final WebDriverWait customWait = new WebDriverWait(driver, timeout);
		customWait.until(d -> {
			for (final String text : texts) {
				if (isVisibleText(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private void expectVisibleText(final String text, final Duration timeout, final String errorMessage) {
		final WebDriverWait customWait = new WebDriverWait(driver, timeout);
		try {
			customWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(anyTagVisibleTextXPath(text))));
		} catch (final TimeoutException e) {
			throw new AssertionError(errorMessage);
		}
	}

	private void expectVisibleAny(final List<String> texts, final Duration timeout, final String errorMessage) {
		try {
			waitForAnyVisibleText(texts, timeout);
		} catch (final TimeoutException e) {
			throw new AssertionError(errorMessage);
		}
	}

	private void expectLegalContent() {
		final List<WebElement> legalContent = driver
				.findElements(By.xpath("//p[string-length(normalize-space()) > 40] | //div[string-length(normalize-space()) > 80]"));
		if (legalContent.isEmpty()) {
			throw new AssertionError("Expected legal content text.");
		}
	}

	private NavigationResult awaitNavigation(final String previousUrl, final Set<String> oldHandles) {
		final WebDriverWait navWait = new WebDriverWait(driver, Duration.ofSeconds(20));
		navWait.until((ExpectedCondition<Boolean>) d -> {
			if (d == null) {
				return false;
			}
			if (d.getWindowHandles().size() > oldHandles.size()) {
				return true;
			}
			return !previousUrl.equals(d.getCurrentUrl());
		});

		final Set<String> currentHandles = driver.getWindowHandles();
		if (currentHandles.size() > oldHandles.size()) {
			for (final String handle : currentHandles) {
				if (!oldHandles.contains(handle)) {
					driver.switchTo().window(handle);
					waitForUiLoad();
					return new NavigationResult(true);
				}
			}
		}

		waitForUiLoad();
		return new NavigationResult(false);
	}

	private void restoreApplicationContext(final boolean openedNewTab) {
		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
			waitForUiLoad();
			return;
		}

		driver.navigate().back();
		waitForUiLoad();
		ensureOnApplicationWindow();
	}

	private void ensureOnApplicationWindow() {
		if (appWindowHandle != null && !driver.getWindowHandle().equals(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForUiLoad();
		}
	}

	private void typeIntoBusinessNameIfPresent(final String value) {
		final List<By> selectors = List.of(
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[contains(@name,'negocio') or contains(@id,'negocio')]"));

		for (final By selector : selectors) {
			final List<WebElement> inputs = driver.findElements(selector);
			for (final WebElement input : inputs) {
				if (!input.isDisplayed()) {
					continue;
				}
				input.clear();
				input.sendKeys(value);
				return;
			}
		}
	}

	private Path takeScreenshot(final String fileName) throws IOException {
		final byte[] image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final Path screenshot = artifactsDir.resolve(fileName + ".png");
		Files.copy(new java.io.ByteArrayInputStream(image), screenshot, StandardCopyOption.REPLACE_EXISTING);
		return screenshot;
	}

	private void waitForUiLoad() {
		wait.until(d -> {
			if (d == null) {
				return false;
			}
			return "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState"));
		});
		sleep(700);
	}

	private boolean isVisibleText(final String text) {
		final List<WebElement> elements = driver.findElements(By.xpath(anyTagVisibleTextXPath(text)));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private static String clickableXPath(final String text) {
		final String escaped = escapeXPathLiteral(text);
		return "//*[self::a or self::button or @role='button' or self::span or self::div or self::p]"
				+ "[contains(normalize-space(), " + escaped + ") or normalize-space() = " + escaped + "]";
	}

	private static String anyTagVisibleTextXPath(final String text) {
		final String escaped = escapeXPathLiteral(text);
		return "//*[contains(normalize-space(), " + escaped + ") or normalize-space() = " + escaped + "]";
	}

	private static String escapeXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder out = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				out.append(", \"'\", ");
			}
			out.append("'").append(parts[i]).append("'");
		}
		out.append(")");
		return out.toString();
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private static String safeName(final String value) {
		return value.toLowerCase().replace(" ", "-").replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o")
				.replace("ú", "u");
	}

	private static String env(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private static int envInt(final String key, final int fallback) {
		final String value = env(key);
		if (value == null) {
			return fallback;
		}

		try {
			return Integer.parseInt(value);
		} catch (final NumberFormatException nfe) {
			return fallback;
		}
	}

	private static boolean envBoolean(final String key, final boolean fallback) {
		final String value = env(key);
		if (value == null) {
			return fallback;
		}
		return "true".equalsIgnoreCase(value) || "1".equals(value);
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class NavigationResult {
		private final boolean openedNewTab;

		private NavigationResult(final boolean openedNewTab) {
			this.openedNewTab = openedNewTab;
		}
	}

	private static final class StepReport {
		private static final String PASS = "PASS";
		private static final String FAIL = "FAIL";

		private final Map<String, StepOutcome> results = new LinkedHashMap<>();

		private void pass(final String step) {
			results.put(step, new StepOutcome(PASS, null));
		}

		private void fail(final String step, final String message) {
			results.put(step, new StepOutcome(FAIL, message));
		}

		private boolean hasFailures() {
			for (final StepOutcome outcome : new ArrayList<>(results.values())) {
				if (FAIL.equals(outcome.status)) {
					return true;
				}
			}
			return false;
		}
	}

	private static final class StepOutcome {
		private final String status;
		private final String message;

		private StepOutcome(final String status, final String message) {
			this.status = status;
			this.message = message;
		}
	}
}
