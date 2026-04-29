package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFORMACION_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_POLITICA = "Política de Privacidad";

	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final LinkedHashMap<String, String> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	private Path artifactsDir;
	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setup() throws IOException {
		final boolean enabled = Boolean.parseBoolean(env("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this E2E test.", enabled);

		initReport();
		artifactsDir = Path.of("target", "saleads-e2e", TS_FORMATTER.format(LocalDateTime.now()));
		Files.createDirectories(artifactsDir);

		driver = buildDriver();
		driver.manage().window().setSize(new Dimension(1600, 1200));
		wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(30));
	}

	@After
	public void teardown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		executeStep(REPORT_LOGIN, this::loginWithGoogle);
		executeStep(REPORT_MI_NEGOCIO_MENU, this::openMiNegocioMenu);
		executeStep(REPORT_AGREGAR_NEGOCIO_MODAL, this::validateAgregarNegocioModal);
		executeStep(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, this::openAdministrarNegocios);
		executeStep(REPORT_INFORMACION_GENERAL, this::validateInformacionGeneral);
		executeStep(REPORT_DETALLES_CUENTA, this::validateDetallesCuenta);
		executeStep(REPORT_TUS_NEGOCIOS, this::validateTusNegocios);
		executeStep(REPORT_TERMINOS, () -> validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "terminos"));
		executeStep(REPORT_POLITICA, () -> validateLegalLink("Política de Privacidad", "Política de Privacidad", "politica-privacidad"));

		assertFalse("One or more workflow validations failed. See report file in " + artifactsDir,
				!failures.isEmpty());
	}

	private void initReport() {
		report.put(REPORT_LOGIN, "NOT_RUN");
		report.put(REPORT_MI_NEGOCIO_MENU, "NOT_RUN");
		report.put(REPORT_AGREGAR_NEGOCIO_MODAL, "NOT_RUN");
		report.put(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, "NOT_RUN");
		report.put(REPORT_INFORMACION_GENERAL, "NOT_RUN");
		report.put(REPORT_DETALLES_CUENTA, "NOT_RUN");
		report.put(REPORT_TUS_NEGOCIOS, "NOT_RUN");
		report.put(REPORT_TERMINOS, "NOT_RUN");
		report.put(REPORT_POLITICA, "NOT_RUN");
	}

	private WebDriver buildDriver() {
		final String remoteUrl = env("SALEADS_SELENIUM_REMOTE_URL", "");
		final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");

		if (!remoteUrl.isBlank()) {
			try {
				return new RemoteWebDriver(java.net.URI.create(remoteUrl).toURL(), options);
			} catch (final Exception ex) {
				throw new IllegalArgumentException("Invalid SALEADS_SELENIUM_REMOTE_URL: " + remoteUrl, ex);
			}
		}

		return new ChromeDriver(options);
	}

	private void loginWithGoogle() throws Exception {
		final String loginUrl = env("SALEADS_LOGIN_URL", "");
		final String accountEmail = env("SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com");

		if (!loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiLoad();
		}

		final String appWindow = driver.getWindowHandle();
		final Set<String> initialWindows = driver.getWindowHandles();

		clickFirst(
				By.xpath("//*[self::button or self::a][contains(normalize-space(.), 'Google')]"),
				By.xpath("//*[self::button or self::a][contains(normalize-space(.), 'Sign in with Google')]"),
				By.xpath("//*[self::button or self::a][contains(normalize-space(.), 'Iniciar con Google')]"));

		maybeSwitchToNewWindow(initialWindows);
		maybeSelectGoogleAccount(accountEmail);
		maybeReturnToWindow(appWindow);

		waitVisibleAny(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(normalize-space(.), 'Negocio')]"),
				By.xpath("//*[contains(normalize-space(.), 'Mi Negocio')]"));

		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws Exception {
		waitVisibleAny(By.xpath("//aside"), By.xpath("//nav"));

		clickIfVisible(By.xpath("//*[contains(normalize-space(.), 'Negocio')]"));
		clickFirst(
				By.xpath("//*[self::a or self::button or self::div][contains(normalize-space(.), 'Mi Negocio')]"));

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickFirst(By.xpath("//*[self::a or self::button or self::div][contains(normalize-space(.), 'Agregar Negocio')]"));
		waitVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final List<WebElement> fields = driver.findElements(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='nombre' or @name='businessName' or @type='text']"));
		if (!fields.isEmpty()) {
			fields.get(0).click();
			fields.get(0).sendKeys("Negocio Prueba Automatización");
		}

		takeScreenshot("03-agregar-negocio-modal");
		clickIfVisible(By.xpath("//*[self::button or self::a][contains(normalize-space(.), 'Cancelar')]"));
	}

	private void openAdministrarNegocios() throws Exception {
		ensureMiNegocioExpanded();
		clickFirst(By.xpath("//*[self::a or self::button or self::div][contains(normalize-space(.), 'Administrar Negocios')]"));
		waitVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		waitVisibleText("Información General");
		assertVisibleAny(
				By.xpath("//*[contains(normalize-space(.), '@')]"),
				By.xpath("//*[contains(normalize-space(.), 'BUSINESS PLAN')]"));
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
	}

	private void validateDetallesCuenta() {
		waitVisibleText("Detalles de la Cuenta");
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		waitVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleAny(
				By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]"),
				By.xpath("//table"),
				By.xpath("//ul"));
	}

	private void validateLegalLink(final String linkText, final String headingText, final String screenshotSlug)
			throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeClickWindows = driver.getWindowHandles();

		clickFirst(By.xpath("//*[self::a or self::button][contains(normalize-space(.), '" + linkText + "')]"));
		waitForUiLoad();

		final String legalWindow = switchToNewWindowIfAny(beforeClickWindows);
		waitVisibleText(headingText);

		final WebElement body = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
		Assert.assertTrue("Expected legal content to be visible for " + linkText,
				body.getText() != null && body.getText().trim().length() > 40);

		takeScreenshot("05-" + screenshotSlug);
		capturedUrls.put(linkText, driver.getCurrentUrl());

		if (legalWindow != null && !Objects.equals(legalWindow, appWindow)) {
			driver.close();
		}
		driver.switchTo().window(appWindow);
		waitForUiLoad();
	}

	private void ensureMiNegocioExpanded() {
		if (driver.findElements(By.xpath("//*[contains(normalize-space(.), 'Administrar Negocios')]")).isEmpty()) {
			clickIfVisible(By.xpath("//*[contains(normalize-space(.), 'Mi Negocio')]"));
		}
	}

	private void maybeSelectGoogleAccount(final String accountEmail) {
		try {
			clickIfVisible(By.xpath("//*[contains(normalize-space(.), '" + accountEmail + "')]"));
			waitForUiLoad();
		} catch (final Exception ignored) {
			// Account selector may not appear if session is already authenticated.
		}
	}

	private void maybeSwitchToNewWindow(final Set<String> initialWindows) {
		switchToNewWindowIfAny(initialWindows);
	}

	private String switchToNewWindowIfAny(final Set<String> windowsBeforeClick) {
		try {
			wait.withTimeout(java.time.Duration.ofSeconds(10)).until(d -> d.getWindowHandles().size() > windowsBeforeClick.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!windowsBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					waitForUiLoad();
					return handle;
				}
			}
		} catch (final TimeoutException ignored) {
			// Navigation could happen in the same tab.
		} finally {
			wait.withTimeout(java.time.Duration.ofSeconds(30));
		}
		return driver.getWindowHandle();
	}

	private void maybeReturnToWindow(final String appWindow) {
		final Set<String> handles = driver.getWindowHandles();
		if (handles.contains(appWindow)) {
			driver.switchTo().window(appWindow);
			waitForUiLoad();
			return;
		}

		if (!handles.isEmpty()) {
			driver.switchTo().window(handles.iterator().next());
			waitForUiLoad();
		}
	}

	private void executeStep(final String label, final Step step) {
		try {
			step.run();
			report.put(label, "PASS");
		} catch (final Throwable ex) {
			report.put(label, "FAIL");
			failures.add(label + ": " + ex.getMessage());
			try {
				takeScreenshot("error-" + normalizeFileName(label));
			} catch (final Exception ignored) {
				// Best effort screenshot.
			}
		}
	}

	private void clickFirst(final By... selectors) {
		for (final By selector : selectors) {
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(selector));
				element.click();
				waitForUiLoad();
				return;
			} catch (final Exception ignored) {
				// Try next selector.
			}
		}
		throw new AssertionError("Unable to click any expected selector.");
	}

	private void clickIfVisible(final By selector) {
		final List<WebElement> elements = driver.findElements(selector);
		if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
			elements.get(0).click();
			waitForUiLoad();
		}
	}

	private void waitVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.), '" + text + "')]")));
	}

	private void assertVisibleText(final String text) {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[contains(normalize-space(.), '" + text + "')]"));
		Assert.assertTrue("Expected to find visible text: " + text,
				elements.stream().anyMatch(WebElement::isDisplayed));
	}

	private void assertVisibleAny(final By... selectors) {
		for (final By selector : selectors) {
			try {
				final List<WebElement> elements = driver.findElements(selector);
				if (elements.stream().anyMatch(WebElement::isDisplayed)) {
					return;
				}
			} catch (final Exception ignored) {
				// Try next selector.
			}
		}
		throw new AssertionError("Expected at least one of the target elements to be visible.");
	}

	private void waitVisibleAny(final By... selectors) {
		wait.until(d -> {
			for (final By selector : selectors) {
				try {
					final List<WebElement> elements = d.findElements(selector);
					if (elements.stream().anyMatch(WebElement::isDisplayed)) {
						return true;
					}
				} catch (final Exception ignored) {
					// Try next.
				}
			}
			return false;
		});
	}

	private void waitForUiLoad() {
		wait.until(d -> {
			try {
				final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(readyState) || "interactive".equals(readyState);
			} catch (final Exception ex) {
				return true;
			}
		});
	}

	private void takeScreenshot(final String name) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final Path output = artifactsDir.resolve(normalizeFileName(name) + ".png");
		final byte[] data = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(output, data);
	}

	private void writeFinalReport() throws IOException {
		if (artifactsDir == null) {
			return;
		}

		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test\n");
		sb.append("Artifacts directory: ").append(artifactsDir).append('\n');
		sb.append('\n');
		sb.append("Validation summary:\n");
		report.forEach((key, value) -> sb.append("- ").append(key).append(": ").append(value).append('\n'));

		if (!capturedUrls.isEmpty()) {
			sb.append('\n');
			sb.append("Captured legal URLs:\n");
			capturedUrls.forEach((key, value) -> sb.append("- ").append(key).append(": ").append(value).append('\n'));
		}

		if (!failures.isEmpty()) {
			sb.append('\n');
			sb.append("Failures:\n");
			for (final String failure : failures) {
				sb.append("- ").append(failure).append('\n');
			}
		}

		Files.writeString(artifactsDir.resolve("final-report.txt"), sb.toString());
		System.out.println(sb);
	}

	private static String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private static String normalizeFileName(final String raw) {
		return raw.toLowerCase()
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-|-$)", "");
	}

	@FunctionalInterface
	private interface Step {
		void run() throws Exception;
	}
}
