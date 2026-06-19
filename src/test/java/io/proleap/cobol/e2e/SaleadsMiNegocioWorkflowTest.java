package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
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
import java.util.Set;
import java.util.stream.Collectors;

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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Informacion General", "Detalles de la Cuenta", "Tus Negocios",
			"Terminos y Condiciones", "Politica de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, String> stepReport = new LinkedHashMap<>();
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue(
				"Enable this live E2E with SALEADS_E2E_ENABLED=true. Optionally set SALEADS_LOGIN_URL and SALEADS_HEADLESS=false.",
				"true".equalsIgnoreCase(readEnv("SALEADS_E2E_ENABLED", "false")));

		for (final String field : REPORT_FIELDS) {
			stepReport.put(field, "FAIL - not executed");
		}

		evidenceDir = Path.of("target", "evidence", "saleads-mi-negocio",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().window().maximize();

		final String loginUrl = readEnv("SALEADS_LOGIN_URL", "").trim();
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
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
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		boolean proceed = true;
		proceed = runDependentStep("Login", proceed, this::stepLogin);
		proceed = runDependentStep("Mi Negocio menu", proceed, this::stepMiNegocioMenu);
		proceed = runDependentStep("Agregar Negocio modal", proceed, this::stepAgregarNegocioModal);
		proceed = runDependentStep("Administrar Negocios view", proceed, this::stepAdministrarNegociosView);
		proceed = runDependentStep("Informacion General", proceed, this::stepInformacionGeneral);
		proceed = runDependentStep("Detalles de la Cuenta", proceed, this::stepDetallesCuenta);
		proceed = runDependentStep("Tus Negocios", proceed, this::stepTusNegocios);
		proceed = runDependentStep("Terminos y Condiciones", proceed, this::stepTerminosYCondiciones);
		runDependentStep("Politica de Privacidad", proceed, this::stepPoliticaPrivacidad);

		final List<String> failures = stepReport.entrySet().stream().filter(entry -> !entry.getValue().startsWith("PASS"))
				.map(entry -> entry.getKey() + " -> " + entry.getValue()).collect(Collectors.toList());

		assertTrue("One or more validations failed. Report: " + evidenceDir.resolve("final-report.md"), failures.isEmpty());
	}

	private void stepLogin() throws Exception {
		if (driver.getCurrentUrl().startsWith("about:blank")) {
			throw new IllegalStateException(
					"Browser is on about:blank. Provide SALEADS_LOGIN_URL or pre-load the SaleADS login page before running.");
		}

		clickAnyVisibleText(
				Arrays.asList("Sign in with Google", "Iniciar sesion con Google", "Iniciar sesion", "Continuar con Google"));
		waitForUiLoad();

		clickIfVisible(By.xpath("//*[contains(normalize-space(.), 'juanlucasbarbiergarzon@gmail.com')]"), 8);
		waitForUiLoad();

		waitForAnyTexts(Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Panel"), 20);
		assertTrue("Left sidebar navigation is not visible",
				isVisible(By.xpath("//aside | //nav[contains(@class, 'sidebar')]"), 8) || isTextVisible("Negocio", 8));

		captureScreenshot("01-dashboard-loaded");
	}

	private void stepMiNegocioMenu() throws Exception {
		clickAnyVisibleText(Arrays.asList("Negocio"));
		waitForUiLoad();
		clickAnyVisibleText(Arrays.asList("Mi Negocio"));
		waitForUiLoad();

		assertTextVisible("Agregar Negocio", 10);
		assertTextVisible("Administrar Negocios", 10);
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepAgregarNegocioModal() throws Exception {
		clickAnyVisibleText(Arrays.asList("Agregar Negocio"));
		waitForUiLoad();

		assertTextVisible("Crear Nuevo Negocio", 10);
		assertTrue("Input 'Nombre del Negocio' does not exist",
				isVisible(By.xpath("//label[contains(., 'Nombre del Negocio')]/following::input[1] | "
						+ "//input[contains(@placeholder, 'Nombre del Negocio')]"), 10));
		assertTextVisible("Tienes 2 de 3 negocios", 10);
		assertTextVisible("Cancelar", 10);
		assertTextVisible("Crear Negocio", 10);

		captureScreenshot("03-agregar-negocio-modal");

		final By businessNameInput = By.xpath("//label[contains(., 'Nombre del Negocio')]/following::input[1] | "
				+ "//input[contains(@placeholder, 'Nombre del Negocio')]");
		if (isVisible(businessNameInput, 4)) {
			final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(businessNameInput));
			input.clear();
			input.sendKeys("Negocio Prueba Automatizacion");
		}

		clickIfVisible(textLocator("Cancelar"), 5);
		waitForUiLoad();
	}

	private void stepAdministrarNegociosView() throws Exception {
		if (!isTextVisible("Administrar Negocios", 4)) {
			clickAnyVisibleText(Arrays.asList("Mi Negocio"));
			waitForUiLoad();
		}

		clickAnyVisibleText(Arrays.asList("Administrar Negocios"));
		waitForUiLoad();

		assertTextVisible("Informacion General", 12);
		assertTextVisible("Detalles de la Cuenta", 12);
		assertTextVisible("Tus Negocios", 12);
		assertTextVisible("Seccion Legal", 12);
		captureScreenshot("04-administrar-negocios-view");
	}

	private void stepInformacionGeneral() throws Exception {
		assertTrue("User name is not visible",
				isTextVisible("Nombre", 6) || isTextVisible("Usuario", 6) || isTextVisible("Perfil", 6));
		assertTrue("User email is not visible", isTextVisible("@", 6) || isTextVisible("juanlucasbarbiergarzon@gmail.com", 6));
		assertTextVisible("BUSINESS PLAN", 10);
		assertTextVisible("Cambiar Plan", 10);
	}

	private void stepDetallesCuenta() throws Exception {
		assertTextVisible("Cuenta creada", 10);
		assertTextVisible("Estado activo", 10);
		assertTextVisible("Idioma seleccionado", 10);
	}

	private void stepTusNegocios() throws Exception {
		assertTextVisible("Tus Negocios", 10);
		assertTextVisible("Agregar Negocio", 10);
		assertTextVisible("Tienes 2 de 3 negocios", 10);
	}

	private void stepTerminosYCondiciones() throws Exception {
		termsUrl = openLegalLinkAndReturn(Arrays.asList("Terminos y Condiciones", "Términos y Condiciones"),
				"08-terminos-y-condiciones");
		assertTextVisible("Terminos y Condiciones", 12);
		assertTrue("Legal content text is not visible",
				isTextVisible("terminos", 8) || isTextVisible("condiciones", 8) || isTextVisible("uso", 8));
		captureScreenshot("08-terminos-y-condiciones");
	}

	private void stepPoliticaPrivacidad() throws Exception {
		privacyUrl = openLegalLinkAndReturn(Arrays.asList("Politica de Privacidad", "Política de Privacidad"),
				"09-politica-de-privacidad");
		assertTextVisible("Politica de Privacidad", 12);
		assertTrue("Legal content text is not visible",
				isTextVisible("privacidad", 8) || isTextVisible("datos", 8) || isTextVisible("informacion", 8));
		captureScreenshot("09-politica-de-privacidad");
	}

	private String openLegalLinkAndReturn(final List<String> possibleLinkTexts, final String screenshotPrefix) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final String appUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickAnyVisibleText(possibleLinkTexts);
		waitForUiLoad();

		boolean switchedToNewTab = false;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(d -> d.getWindowHandles().size() > handlesBefore.size() || !d.getCurrentUrl().equals(appUrl));
		} catch (final TimeoutException timeout) {
			throw new IllegalStateException("Legal page did not open after clicking link: " + possibleLinkTexts, timeout);
		}

		final Set<String> currentHandles = new LinkedHashSet<>(driver.getWindowHandles());
		currentHandles.removeAll(handlesBefore);
		if (!currentHandles.isEmpty()) {
			driver.switchTo().window(currentHandles.iterator().next());
			switchedToNewTab = true;
			waitForUiLoad();
		}

		captureScreenshot(screenshotPrefix + "-page");
		final String legalUrl = driver.getCurrentUrl();

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiLoad();
		return legalUrl;
	}

	private boolean runDependentStep(final String reportField, final boolean canProceed, final StepAction stepAction) {
		if (!canProceed) {
			stepReport.put(reportField, "FAIL - blocked by previous step failure");
			return false;
		}

		try {
			stepAction.run();
			stepReport.put(reportField, "PASS");
			return true;
		} catch (final Exception exception) {
			stepReport.put(reportField, "FAIL - " + exception.getMessage());
			try {
				captureScreenshot("error-" + sanitize(reportField));
			} catch (final Exception ignored) {
				// ignore screenshot failures during error handling
			}
			return false;
		}
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder markdown = new StringBuilder();
		markdown.append("# SaleADS Mi Negocio Workflow Report\n\n");
		markdown.append("| Checkpoint | Result |\n");
		markdown.append("|---|---|\n");
		for (final String field : REPORT_FIELDS) {
			markdown.append("| ").append(field).append(" | ").append(stepReport.getOrDefault(field, "FAIL - missing")).append(" |\n");
		}
		markdown.append("\n");
		markdown.append("- Terminos y Condiciones URL: ").append(termsUrl).append("\n");
		markdown.append("- Politica de Privacidad URL: ").append(privacyUrl).append("\n");
		markdown.append("- Evidence directory: ").append(evidenceDir.toAbsolutePath()).append("\n");

		Files.writeString(evidenceDir.resolve("final-report.md"), markdown.toString());
	}

	private void clickAnyVisibleText(final List<String> texts) throws Exception {
		Exception lastError = null;
		for (final String text : texts) {
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(textLocator(text)));
				element.click();
				return;
			} catch (final Exception exception) {
				lastError = exception;
			}
		}

		throw new IllegalStateException("Could not click any of the texts: " + texts, lastError);
	}

	private void clickIfVisible(final By locator, final int timeoutSeconds) {
		try {
			final WebElement element = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.elementToBeClickable(locator));
			element.click();
		} catch (final TimeoutException timeout) {
			// optional click
		}
	}

	private void assertTextVisible(final String text, final int timeoutSeconds) {
		assertTrue("Expected text not visible: " + text, isTextVisible(text, timeoutSeconds));
	}

	private boolean isTextVisible(final String text, final int timeoutSeconds) {
		return isVisible(textLocator(text), timeoutSeconds);
	}

	private boolean isVisible(final By locator, final int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException timeout) {
			return false;
		}
	}

	private void waitForAnyTexts(final List<String> texts, final int timeoutSeconds) {
		new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(d -> {
			for (final String text : texts) {
				if (isTextVisible(text, 1)) {
					return true;
				}
			}
			return false;
		});
	}

	private void waitForUiLoad() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> {
				if (!(d instanceof JavascriptExecutor)) {
					return true;
				}
				final Object ready = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(String.valueOf(ready));
			});
			Thread.sleep(350);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		} catch (final Exception ignored) {
			// best effort UI stabilization
		}
	}

	private By textLocator(final String text) {
		final String literal = xpathLiteral(text.toLowerCase(Locale.ROOT));
		return By.xpath("//*[self::a or self::button or self::span or self::div or self::p or self::h1 or self::h2 or self::h3 or self::label]"
				+ "[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÑ', 'abcdefghijklmnopqrstuvwxyzáéíóúñ'), "
				+ literal + ")]");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final List<String> parts = new ArrayList<>();
		for (final String segment : value.split("'")) {
			parts.add("'" + segment + "'");
		}
		return "concat(" + String.join(", \"'\", ", parts) + ")";
	}

	private void captureScreenshot(final String label) throws IOException {
		final Path destination = evidenceDir
				.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")) + "-" + sanitize(label) + ".png");
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private String sanitize(final String raw) {
		return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]+", "-").replaceAll("\\-+", "-").replaceAll("^\\-|\\-$",
				"");
	}

	private String readEnv(final String name, final String defaultValue) {
		final String value = System.getenv(name);
		return value == null ? defaultValue : value;
	}

	private WebDriver createDriver() {
		final String browser = readEnv("SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = "true".equalsIgnoreCase(readEnv("SALEADS_HEADLESS", "true"));
		if ("firefox".equals(browser)) {
			final FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			return new FirefoxDriver(options);
		}

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}
		return new ChromeDriver(options);
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
