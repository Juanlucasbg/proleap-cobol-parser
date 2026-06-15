package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio".
 *
 * Required environment:
 * - SALEADS_LOGIN_URL: login URL for current environment.
 *
 * Optional environment:
 * - SALEADS_HEADLESS: true/false, defaults to true.
 * - SALEADS_WAIT_SECONDS: explicit wait timeout, defaults to 25.
 */
public class SaleAdsMiNegocioWorkflowTest {

	private static final String TEST_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = env("SALEADS_LOGIN_URL", null);
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to run SaleADS UI workflow test.", loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));
		final long waitSeconds = Long.parseLong(env("SALEADS_WAIT_SECONDS", "25"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		screenshotDirectory = Files.createDirectories(Paths.get("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		try {
			if (screenshotDirectory != null) {
				System.out.println(buildFinalReport());
			}
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::loginWithGoogleAndValidateDashboard);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegociosAndValidateSections);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", () -> validateLegalDocument("Términos y Condiciones", "terminos-y-condiciones.png"));
		runStep("Política de Privacidad", () -> validateLegalDocument("Política de Privacidad", "politica-de-privacidad.png"));

		assertTrue("Workflow report contains failures:\n" + buildFinalReport(), report.values().stream().allMatch(r -> r.pass));
	}

	private void loginWithGoogleAndValidateDashboard() throws IOException {
		final Set<String> windowHandlesBeforeLogin = new LinkedHashSet<>(driver.getWindowHandles());
		clickAndWait(firstVisibleClickable(By.xpath(
				"//button[contains(normalize-space(.), 'Google')] | //a[contains(normalize-space(.), 'Google')] | //*[@role='button' and contains(normalize-space(.), 'Google')]"),
				"Google login button"));

		switchToNewestWindowIfOpened(windowHandlesBeforeLogin);
		selectGoogleAccountIfVisible(TEST_ACCOUNT);
		switchToApplicationWindow();

		assertAnyVisible("Main application interface",
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar')]"));
		assertAnyVisible("Left sidebar navigation",
				By.xpath("//aside"),
				By.xpath("//*[contains(@class,'sidebar')]"),
				By.xpath("//nav[contains(@class,'side') or contains(@class,'menu')]"));

		saveScreenshot("01-dashboard-loaded.png");
	}

	private void openMiNegocioMenu() throws IOException {
		clickVisibleText("Negocio");
		clickVisibleText("Mi Negocio");

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		saveScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickVisibleText("Agregar Negocio");

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		saveScreenshot("03-agregar-negocio-modal.png");

		typeIfPresent(By.xpath(
				"//input[@placeholder='Nombre del Negocio'] | //input[@name='nombreNegocio'] | //input[contains(@aria-label,'Nombre del Negocio')]"),
				"Negocio Prueba Automatizacion");
		clickVisibleText("Cancelar");
	}

	private void openAdministrarNegociosAndValidateSections() throws IOException {
		if (!isVisible(By.xpath("//*[normalize-space(.)='Administrar Negocios']"), Duration.ofSeconds(4))) {
			clickVisibleText("Mi Negocio");
		}

		clickVisibleText("Administrar Negocios");

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		saveScreenshot("04-administrar-negocios-view.png");
	}

	private void validateInformacionGeneral() {
		assertVisibleText("Información General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String pageText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected user email to be visible.", EMAIL_PATTERN.matcher(pageText).find());

		final String withoutLabels = pageText.replace("Información General", "").replace("BUSINESS PLAN", "")
				.replace("Cambiar Plan", "").trim();
		assertTrue("Expected user name/details text to be visible.", withoutLabels.length() > 30);
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

		assertAnyVisible("Business list",
				By.xpath("//ul"),
				By.xpath("//table"),
				By.xpath("//*[contains(@class,'business')]"),
				By.xpath("//*[contains(@class,'negocio')]"));
	}

	private void validateLegalDocument(final String legalLinkText, final String screenshotName) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final String currentUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickVisibleText(legalLinkText);

		wait.until(d -> d.getWindowHandles().size() > handlesBefore.size() || !d.getCurrentUrl().equals(currentUrl));

		boolean openedInNewTab = false;
		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				openedInNewTab = true;
				break;
			}
		}

		assertVisibleText(legalLinkText);
		final String legalPageText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected legal content text to be visible.", legalPageText.trim().length() > 120);

		saveScreenshot(screenshotName);
		step(legalLinkText).details.add("Final URL: " + driver.getCurrentUrl());

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		assertVisibleText("Sección Legal");
	}

	private void runStep(final String stepName, final StepAction action) {
		final StepResult result = step(stepName);

		try {
			action.run();
			result.pass = true;
			result.details.add("PASS");
		} catch (final Throwable t) {
			result.pass = false;
			result.details.add("FAIL: " + t.getMessage());
			try {
				saveScreenshot("failure-" + slug(stepName) + ".png");
			} catch (final IOException ioException) {
				result.details.add("Could not save failure screenshot: " + ioException.getMessage());
			}
		}
	}

	private StepResult step(final String stepName) {
		return report.computeIfAbsent(stepName, key -> new StepResult());
	}

	private String buildFinalReport() {
		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio workflow report:");
		for (final String field : Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
				"Información General", "Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones", "Política de Privacidad")) {
			final StepResult result = report.getOrDefault(field, new StepResult());
			lines.add("- " + field + ": " + (result.pass ? "PASS" : "FAIL"));
			for (final String detail : result.details) {
				lines.add("  - " + detail);
			}
		}
		lines.add("Evidence directory: " + screenshotDirectory.toAbsolutePath());
		return String.join(System.lineSeparator(), lines);
	}

	private void clickVisibleText(final String text) {
		clickAndWait(firstVisibleClickable(By.xpath(textXpath(text)), "Element with text '" + text + "'"));
	}

	private void assertVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(textXpath(text))));
	}

	private void assertAnyVisible(final String description, final By... locators) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final By locator : locators) {
				if (isVisible(locator, Duration.ofMillis(500))) {
					return;
				}
			}

			try {
				Thread.sleep(150);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		throw new AssertionError("Expected visible element not found: " + description);
	}

	private WebElement firstVisibleClickable(final By locator, final String description) {
		try {
			return wait.until(ExpectedConditions.elementToBeClickable(locator));
		} catch (final Throwable t) {
			throw new AssertionError("Could not find clickable element: " + description, t);
		}
	}

	private void clickAndWait(final WebElement element) {
		element.click();
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		if (driver instanceof JavascriptExecutor) {
			wait.until(d -> {
				final String ready = String.valueOf(((JavascriptExecutor) driver).executeScript("return document.readyState"));
				return "interactive".equals(ready) || "complete".equals(ready);
			});
		}

		try {
			Thread.sleep(800);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void switchToNewestWindowIfOpened(final Set<String> previousHandles) {
		wait.until(d -> d.getWindowHandles().size() >= previousHandles.size());

		for (final String handle : driver.getWindowHandles()) {
			if (!previousHandles.contains(handle)) {
				driver.switchTo().window(handle);
				return;
			}
		}
	}

	private void switchToApplicationWindow() {
		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (!isVisible(By.xpath("//button[contains(normalize-space(.), 'Google')]"), Duration.ofMillis(400))) {
				return;
			}
		}
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final By accountLocator = By.xpath(textXpath(email));
		if (isVisible(accountLocator, Duration.ofSeconds(8))) {
			clickAndWait(wait.until(ExpectedConditions.elementToBeClickable(accountLocator)));
		}
	}

	private void typeIfPresent(final By locator, final String value) {
		if (isVisible(locator, Duration.ofSeconds(4))) {
			final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			input.click();
			input.clear();
			input.sendKeys(value);
			waitForUiToLoad();
		}
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final Throwable ignored) {
			return false;
		}
	}

	private void saveScreenshot(final String screenshotName) throws IOException {
		final File raw = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(raw.toPath(), screenshotDirectory.resolve(screenshotName), StandardCopyOption.REPLACE_EXISTING);
	}

	private String textXpath(final String text) {
		final String escaped = escapeXpathText(text);
		return "//*[normalize-space(.)=" + escaped + "] | //*[contains(normalize-space(.), " + escaped + ")]";
	}

	private String escapeXpathText(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final String[] parts = text.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String slug(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
	}

	private String env(final String key, final String defaultValue) {
		final String property = System.getProperty(key);
		if (property != null && !property.isBlank()) {
			return property;
		}

		final String environmentValue = System.getenv(key);
		if (environmentValue != null && !environmentValue.isBlank()) {
			return environmentValue;
		}

		return defaultValue;
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepResult {
		boolean pass;
		final List<String> details = new ArrayList<>();
	}
}
