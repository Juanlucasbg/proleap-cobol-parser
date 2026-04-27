package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

	private final Map<String, String> finalReport = new LinkedHashMap<>();
	private final Map<String, String> extraDetails = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;
	private Path screenshotsDir;
	private String appTabHandle;

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(property("saleads.headless", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);

		artifactsDir = Path.of("target", "saleads-artifacts");
		screenshotsDir = artifactsDir.resolve("screenshots");
		Files.createDirectories(screenshotsDir);

		initializeReport();
	}

	@After
	public void tearDown() {
		try {
			writeFinalReport();
		} catch (final Exception e) {
			System.err.println("Failed to write report: " + e.getMessage());
		}
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::loginWithGoogleAndValidateSidebar);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegociosView);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", this::validateTerminosYCondiciones);
		runStep("Política de Privacidad", this::validatePoliticaPrivacidad);

		writeFinalReport();
		assertFalse("One or more SaleADS Mi Negocio validations failed. See target/saleads-artifacts/final-report.txt",
				finalReport.containsValue("FAIL"));
	}

	private void loginWithGoogleAndValidateSidebar() {
		goToLoginPageWhenConfigured();
		waitForUiLoad();

		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google", "Login with Google",
				"Continuar con Google");

		handleGoogleAccountPickerIfVisible();
		waitForMainAppAndSidebar();
		takeScreenshot("01_dashboard_loaded");
	}

	private void openMiNegocioMenu() {
		clickByVisibleText("Negocio");
		waitForUiLoad();
		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");

		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void validateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");
		waitForUiLoad();

		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		final WebElement nombreInput = firstVisibleElement(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio')]"),
				By.xpath("//*[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"));
		assertNotNull("Could not find input field 'Nombre del Negocio'.", nombreInput);
		nombreInput.click();
		nombreInput.sendKeys("Negocio Prueba Automatización");
		nombreInput.sendKeys(Keys.TAB);

		takeScreenshot("03_agregar_negocio_modal");
		clickByVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void openAdministrarNegociosView() {
		expandMiNegocioIfNeeded();
		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertAnyTextVisible("Sección Legal", "Seccion Legal");

		takeScreenshot("04_administrar_negocios");
	}

	private void validateInformacionGeneral() {
		final String bodyText = visibleBodyText();
		assertTrue("Expected user name and email in Información General.", bodyText.contains("@"));
		assertAnyTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
	}

	private void validateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertAnyTextVisible("Estado activo", "Estado Activo");
		assertAnyTextVisible("Idioma seleccionado", "Idioma Seleccionado");
	}

	private void validateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void validateTerminosYCondiciones() {
		validateLegalLink("Términos y Condiciones", "Terminos y Condiciones", "Términos y Condiciones");
	}

	private void validatePoliticaPrivacidad() {
		validateLegalLink("Política de Privacidad", "Politica de Privacidad", "Política de Privacidad");
	}

	private void validateLegalLink(final String expectedHeading, final String fallbackHeading, final String reportKey) {
		final String appUrlBefore = driver.getCurrentUrl();
		final String handleBefore = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText(expectedHeading, fallbackHeading);
		waitForUiLoad();
		switchToNewTabIfOpened(handlesBefore);

		assertAnyTextVisible(expectedHeading, fallbackHeading);
		final String bodyText = visibleBodyText();
		assertTrue("Expected legal content text for: " + reportKey, bodyText != null && bodyText.trim().length() > 120);

		takeScreenshot("legal_" + sanitize(reportKey));
		extraDetails.put(reportKey + " URL", driver.getCurrentUrl());

		returnToApplicationTab(handleBefore, appUrlBefore);
		assertTextVisible("Información General");
	}

	private void expandMiNegocioIfNeeded() {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiLoad();
		}
	}

	private void goToLoginPageWhenConfigured() {
		final String loginUrl = firstNonBlank(System.getProperty("saleads.loginUrl"), System.getenv("SALEADS_LOGIN_URL"),
				System.getProperty("saleads.baseUrl"), System.getenv("SALEADS_BASE_URL"), System.getProperty("baseUrl"),
				System.getenv("BASE_URL"));

		if (loginUrl != null) {
			driver.get(loginUrl);
			return;
		}

		final String currentUrl = driver.getCurrentUrl();
		if (currentUrl == null || !currentUrl.startsWith("http")) {
			throw new IllegalStateException(
					"No URL configured. Provide -Dsaleads.loginUrl or SALEADS_LOGIN_URL, or pre-open an environment login page.");
		}
	}

	private void handleGoogleAccountPickerIfVisible() {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(45).toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (isTextVisible(GOOGLE_ACCOUNT_EMAIL)) {
					clickByVisibleText(GOOGLE_ACCOUNT_EMAIL);
					waitForUiLoad();
					break;
				}
			}
			if (isMainAppVisible()) {
				return;
			}
			sleep(1000);
		}
	}

	private void waitForMainAppAndSidebar() {
		wait.until(driverRef -> isMainAppVisible());
		final WebElement sidebar = firstVisibleElement(By.tagName("aside"), By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar') or contains(@class,'Sidebar')]"));
		assertNotNull("Expected left sidebar navigation to be visible after login.", sidebar);
		appTabHandle = driver.getWindowHandle();
	}

	private boolean isMainAppVisible() {
		return isTextVisible("Mi Negocio") || isTextVisible("Negocio") || isTextVisible("Dashboard")
				|| isTextVisible("Información General");
	}

	private void returnToApplicationTab(final String originalHandle, final String appUrlBefore) {
		final Set<String> currentHandles = driver.getWindowHandles();
		if (currentHandles.size() > 1 && !Objects.equals(driver.getWindowHandle(), originalHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiLoad();
			return;
		}

		if (!Objects.equals(driver.getCurrentUrl(), appUrlBefore)) {
			driver.navigate().back();
			waitForUiLoad();
			if (!isTextVisible("Información General")) {
				driver.get(appUrlBefore);
				waitForUiLoad();
			}
		}
	}

	private void switchToNewTabIfOpened(final Set<String> handlesBefore) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until((ExpectedCondition<Boolean>) d -> d != null && d.getWindowHandles().size() > handlesBefore.size());
		} catch (final TimeoutException ignored) {
			// Link may navigate in same tab.
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				return;
			}
		}
	}

	private void clickByVisibleText(final String... candidates) {
		RuntimeException lastException = null;
		for (final String candidate : candidates) {
			try {
				final WebElement element = wait.until(driverRef -> firstClickableByText(candidate));
				jsClick(element);
				waitForUiLoad();
				return;
			} catch (final RuntimeException e) {
				lastException = e;
			}
		}

		throw new IllegalStateException("Could not click any visible text option: " + String.join(", ", candidates),
				lastException);
	}

	private WebElement firstClickableByText(final String text) {
		final String escaped = xpathLiteral(text);
		final String containsExpr = "contains(normalize-space(), " + escaped + ")";
		final String exactExpr = "normalize-space() = " + escaped;
		final List<By> selectors = List.of(
				By.xpath("//button[" + exactExpr + " or " + containsExpr + "]"),
				By.xpath("//a[" + exactExpr + " or " + containsExpr + "]"),
				By.xpath("//*[@role='button' and (" + exactExpr + " or " + containsExpr + ")]"),
				By.xpath("//li[" + exactExpr + " or " + containsExpr + "]"),
				By.xpath("//*[" + exactExpr + " or " + containsExpr + "]"));

		for (final By selector : selectors) {
			final List<WebElement> elements = driver.findElements(selector);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		return null;
	}

	private WebElement firstVisibleElement(final By... selectors) {
		return wait.until(driverRef -> {
			for (final By selector : selectors) {
				final List<WebElement> elements = driver.findElements(selector);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private void assertTextVisible(final String text) {
		assertTrue("Expected visible text: " + text, isTextVisible(text));
	}

	private void assertAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return;
			}
		}
		throw new AssertionError("Expected one of visible texts: " + String.join(", ", texts));
	}

	private boolean isTextVisible(final String text) {
		final String escaped = xpathLiteral(text);
		final List<By> selectors = List
				.of(By.xpath("//*[normalize-space() = " + escaped + "]"), By.xpath("//*[contains(normalize-space(), " + escaped + ")]"));

		for (final By selector : selectors) {
			final List<WebElement> elements = driver.findElements(selector);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private String visibleBodyText() {
		return firstVisibleElement(By.tagName("body")).getText();
	}

	private void waitForUiLoad() {
		wait.until(driverRef -> {
			try {
				return "complete".equals(((JavascriptExecutor) driverRef).executeScript("return document.readyState"));
			} catch (final Exception e) {
				return true;
			}
		});
		sleep(300);
	}

	private void jsClick(final WebElement element) {
		try {
			element.click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void runStep(final String reportField, final CheckedStep step) {
		try {
			step.run();
			finalReport.put(reportField, "PASS");
		} catch (final Throwable t) {
			finalReport.put(reportField, "FAIL");
			extraDetails.put(reportField + " error", t.getClass().getSimpleName() + ": " + t.getMessage());
			takeScreenshot("FAIL_" + sanitize(reportField));
		}
	}

	private void initializeReport() {
		finalReport.put("Login", "NOT_RUN");
		finalReport.put("Mi Negocio menu", "NOT_RUN");
		finalReport.put("Agregar Negocio modal", "NOT_RUN");
		finalReport.put("Administrar Negocios view", "NOT_RUN");
		finalReport.put("Información General", "NOT_RUN");
		finalReport.put("Detalles de la Cuenta", "NOT_RUN");
		finalReport.put("Tus Negocios", "NOT_RUN");
		finalReport.put("Términos y Condiciones", "NOT_RUN");
		finalReport.put("Política de Privacidad", "NOT_RUN");
	}

	private void writeFinalReport() {
		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("generatedAt=" + LocalDateTime.now());
		lines.add("");
		lines.add("Validation summary:");
		for (final Map.Entry<String, String> entry : finalReport.entrySet()) {
			lines.add("- " + entry.getKey() + ": " + entry.getValue());
		}

		if (!extraDetails.isEmpty()) {
			lines.add("");
			lines.add("Details:");
			for (final Map.Entry<String, String> detail : extraDetails.entrySet()) {
				lines.add("- " + detail.getKey() + ": " + detail.getValue());
			}
		}

		final Path reportPath = artifactsDir.resolve("final-report.txt");
		try {
			Files.createDirectories(artifactsDir);
			Files.write(reportPath, lines);
		} catch (final IOException e) {
			throw new IllegalStateException("Failed to write report file: " + reportPath, e);
		}
	}

	private void takeScreenshot(final String checkpoint) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String filename = FILE_TS.format(LocalDateTime.now()) + "_" + sanitize(checkpoint) + ".png";
		final Path target = screenshotsDir.resolve(filename);
		try {
			Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException e) {
			extraDetails.put("screenshot error " + checkpoint, e.getMessage());
		}
	}

	private static String sanitize(final String value) {
		final String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
		return normalized.replaceAll("^_+|_+$", "");
	}

	private static String property(final String key, final String defaultValue) {
		final String fromSystem = System.getProperty(key);
		if (fromSystem != null && !fromSystem.isBlank()) {
			return fromSystem;
		}
		final String fromEnv = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}
		return defaultValue;
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(", \"'\", ");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}
}
