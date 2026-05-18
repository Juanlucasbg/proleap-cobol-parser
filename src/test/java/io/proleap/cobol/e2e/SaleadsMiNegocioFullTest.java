package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
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

/**
 * Full workflow E2E for SaleADS Mi Negocio module.
 *
 * <p>
 * Configuration:
 * <ul>
 * <li>SALEADS_LOGIN_URL (optional): Login URL for the active environment.</li>
 * <li>SALEADS_HEADLESS (optional): true/false, defaults to true.</li>
 * </ul>
 *
 * <p>
 * The test never hardcodes a domain and validates UI by visible text.
 */
public class SaleadsMiNegocioFullTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String DEFAULT_BUSINESS_NAME = "Negocio Prueba Automatizacion";
	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final List<String> REPORT_ORDER = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private Path evidenceDir;
	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws IOException {
		evidenceDir = Path.of("target", "saleads-evidence", LocalDateTime.now().format(TS_FORMATTER));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		if (isHeadlessEnabled()) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(20));
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		report.put("Login", executeStep(this::validateLoginWithGoogle));
		report.put("Mi Negocio menu", executeStep(this::validateMiNegocioMenu));
		report.put("Agregar Negocio modal", executeStep(this::validateAgregarNegocioModal));
		report.put("Administrar Negocios view", executeStep(this::validateAdministrarNegociosView));
		report.put("Información General", executeStep(this::validateInformacionGeneralSection));
		report.put("Detalles de la Cuenta", executeStep(this::validateDetallesCuentaSection));
		report.put("Tus Negocios", executeStep(this::validateTusNegociosSection));
		report.put("Términos y Condiciones", executeStep(() -> validateLegalDocument("Términos y Condiciones")));
		report.put("Política de Privacidad", executeStep(() -> validateLegalDocument("Política de Privacidad")));

		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue().pass)
				.map(entry -> entry.getKey() + ": " + entry.getValue().details).collect(Collectors.toList());

		Assert.assertTrue("One or more SaleADS workflow validations failed:\n" + String.join("\n", failedSteps),
				failedSteps.isEmpty());
	}

	private StepResult validateLoginWithGoogle() throws IOException {
		final String configuredLoginUrl = getenv("SALEADS_LOGIN_URL");
		if (configuredLoginUrl != null && !configuredLoginUrl.isBlank()) {
			driver.get(configuredLoginUrl);
			waitForUiToSettle();
		} else if ("about:blank".equals(driver.getCurrentUrl()) || "data:,".equals(driver.getCurrentUrl())) {
			throw new AssertionError(
					"No login page available. Provide SALEADS_LOGIN_URL or start this test from a SaleADS login page.");
		}

		if (!isSidebarVisible()) {
			clickOneOf("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
			waitForUiToSettle();
			clickIfVisible(By.xpath("//*[contains(normalize-space(.), " + xPathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]"),
					Duration.ofSeconds(8));
			waitForUiToSettle();
		}

		wait.until(d -> isSidebarVisible());
		final String screenshot = captureScreenshot("01-dashboard-loaded");

		return StepResult.pass("Main app interface loaded and sidebar visible.", screenshot, null);
	}

	private StepResult validateMiNegocioMenu() throws IOException {
		clickByVisibleText("Mi Negocio");
		waitForUiToSettle();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		final String screenshot = captureScreenshot("02-mi-negocio-expanded");

		return StepResult.pass("Mi Negocio menu expanded with expected options.", screenshot, null);
	}

	private StepResult validateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForUiToSettle();

		assertVisibleText("Crear Nuevo Negocio");
		assertAnyVisible(By.xpath("//*[contains(normalize-space(.), 'Nombre del Negocio')]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"), By.xpath("//input[contains(@name, 'negocio')]"));
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		clickIfVisible(By.xpath("//input[@placeholder='Nombre del Negocio']"), Duration.ofSeconds(2));
		typeIfVisible(By.xpath("//input[@placeholder='Nombre del Negocio']"), DEFAULT_BUSINESS_NAME);
		clickIfVisible(By.xpath("//*[self::button or self::a][normalize-space(.)='Cancelar']"), Duration.ofSeconds(4));
		waitForUiToSettle();

		final String screenshot = captureScreenshot("03-agregar-negocio-modal");
		return StepResult.pass("Agregar Negocio modal validated.", screenshot, null);
	}

	private StepResult validateAdministrarNegociosView() throws IOException {
		if (!isVisible(By.xpath("//*[normalize-space(.)='Administrar Negocios']"))) {
			clickByVisibleText("Mi Negocio");
			waitForUiToSettle();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToSettle();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");

		final String screenshot = captureScreenshot("04-administrar-negocios");
		return StepResult.pass("Administrar Negocios view loaded with all sections.", screenshot, null);
	}

	private StepResult validateInformacionGeneralSection() {
		final WebElement section = findSectionByTitle("Información General");
		final String sectionText = section.getText();

		Assert.assertTrue("User email is not visible in Información General.",
				EMAIL_PATTERN.matcher(sectionText).find() || EMAIL_PATTERN.matcher(driver.findElement(By.tagName("body")).getText()).find());
		Assert.assertTrue("Business plan label not visible.", sectionText.contains("BUSINESS PLAN"));
		assertVisibleText("Cambiar Plan");

		final boolean hasNameCandidate = sectionText.lines().map(String::trim).filter(line -> !line.isBlank())
				.filter(line -> !line.contains("@")).filter(line -> !line.equalsIgnoreCase("Información General"))
				.filter(line -> !line.equalsIgnoreCase("BUSINESS PLAN")).anyMatch(line -> line.split("\\s+").length >= 2);
		Assert.assertTrue("User name is not clearly visible in Información General.", hasNameCandidate);

		return StepResult.pass("Información General content validated.", null, null);
	}

	private StepResult validateDetallesCuentaSection() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
		return StepResult.pass("Detalles de la Cuenta validated.", null, null);
	}

	private StepResult validateTusNegociosSection() {
		final WebElement section = findSectionByTitle("Tus Negocios");
		final String sectionText = section.getText();

		Assert.assertTrue("Business list is not visible in Tus Negocios.", hasBusinessListLikeContent(sectionText));
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		return StepResult.pass("Tus Negocios section validated.", null, null);
	}

	private StepResult validateLegalDocument(final String linkText) throws IOException {
		final String appHandle = driver.getWindowHandle();
		final String urlBefore = driver.getCurrentUrl();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);

		wait.until(d -> d.getWindowHandles().size() > handlesBefore.size() || !urlBefore.equals(d.getCurrentUrl()));
		switchToNewTabIfPresent(handlesBefore);
		waitForUiToSettle();

		assertVisibleText(linkText);
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Legal content is not visible for " + linkText + ".", bodyText.trim().length() > 120);

		final String screenshot = captureScreenshot(
				"Términos y Condiciones".equals(linkText) ? "05-terminos-y-condiciones" : "06-politica-de-privacidad");
		final String finalUrl = driver.getCurrentUrl();

		if (!driver.getWindowHandle().equals(appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else if (!urlBefore.equals(finalUrl)) {
			driver.navigate().back();
		}
		waitForUiToSettle();

		return StepResult.pass(linkText + " validated.", screenshot, finalUrl);
	}

	private StepResult executeStep(final StepAction action) {
		try {
			return action.run();
		} catch (final Throwable error) {
			return StepResult.fail(error.getMessage());
		}
	}

	private WebElement findSectionByTitle(final String titleText) {
		final WebElement title = wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::div][normalize-space(.)="
						+ xPathLiteral(titleText) + "]")));
		return title.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
	}

	private void clickByVisibleText(final String text) {
		final By locator = By.xpath(
				"//*[self::button or self::a or @role='button' or self::span][normalize-space(.)=" + xPathLiteral(text)
						+ "]");
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
		element.click();
		waitForUiToSettle();
	}

	private void clickOneOf(final String... candidates) {
		for (final String candidate : candidates) {
			final By locator = By.xpath(
					"//*[self::button or self::a or @role='button' or self::span][contains(normalize-space(.), "
							+ xPathLiteral(candidate) + ")]");
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					element.click();
					return;
				}
			}
		}
		throw new AssertionError("Could not find any clickable element for candidates: " + String.join(", ", candidates));
	}

	private void clickIfVisible(final By locator, final Duration timeout) {
		try {
			final WebElement element = new WebDriverWait(driver, timeout)
					.until(ExpectedConditions.elementToBeClickable(locator));
			element.click();
		} catch (final TimeoutException ignored) {
			// Optional action.
		}
	}

	private void typeIfVisible(final By locator, final String value) {
		final List<WebElement> elements = driver.findElements(locator);
		if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
			elements.get(0).clear();
			elements.get(0).sendKeys(value);
		}
	}

	private void switchToNewTabIfPresent(final Set<String> previousHandles) {
		for (final String handle : driver.getWindowHandles()) {
			if (!previousHandles.contains(handle)) {
				driver.switchTo().window(handle);
				return;
			}
		}
	}

	private boolean isSidebarVisible() {
		return isVisible(By.xpath("//aside")) && (isVisible(By.xpath("//*[contains(normalize-space(.), 'Mi Negocio')]"))
				|| isVisible(By.xpath("//*[contains(normalize-space(.), 'Negocio')]")));
	}

	private boolean hasBusinessListLikeContent(final String sectionText) {
		final List<String> lines = sectionText.lines().map(String::trim).filter(line -> !line.isBlank())
				.filter(line -> !line.equalsIgnoreCase("Tus Negocios")).collect(Collectors.toList());
		return lines.size() >= 3;
	}

	private boolean isVisible(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		return !elements.isEmpty() && elements.stream().anyMatch(WebElement::isDisplayed);
	}

	private void assertVisibleText(final String text) {
		assertAnyVisible(By.xpath("//*[normalize-space(.)=" + xPathLiteral(text) + "]"),
				By.xpath("//*[contains(normalize-space(.), " + xPathLiteral(text) + ")]"));
	}

	private void assertAnyVisible(final By... locators) {
		for (final By locator : locators) {
			if (isVisible(locator)) {
				return;
			}
		}
		throw new AssertionError("Expected element was not visible for locators: " + List.of(locators));
	}

	private void waitForUiToSettle() {
		wait.until(driver -> "complete"
				.equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		try {
			Thread.sleep(350L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String captureScreenshot(final String checkpointName) throws IOException {
		final Path target = evidenceDir.resolve(checkpointName + ".png");
		final byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(target, screenshotBytes);
		return target.toString();
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("Generated at: " + LocalDateTime.now());
		lines.add("");

		for (final String field : REPORT_ORDER) {
			final StepResult result = report.getOrDefault(field, StepResult.fail("Not executed"));
			lines.add(field + ": " + (result.pass ? "PASS" : "FAIL"));
			lines.add("  Details: " + result.details);
			if (result.screenshotPath != null) {
				lines.add("  Screenshot: " + result.screenshotPath);
			}
			if (result.finalUrl != null) {
				lines.add("  Final URL: " + result.finalUrl);
			}
			lines.add("");
		}

		Files.write(evidenceDir.resolve("final-report.txt"), lines);
	}

	private boolean isHeadlessEnabled() {
		return Boolean.parseBoolean(getenvOrDefault("SALEADS_HEADLESS", "true"));
	}

	private String getenv(final String key) {
		return System.getenv(key);
	}

	private String getenvOrDefault(final String key, final String defaultValue) {
		final String value = getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private String xPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	@FunctionalInterface
	private interface StepAction {
		StepResult run() throws Exception;
	}

	private static class StepResult {
		private final boolean pass;
		private final String details;
		private final String screenshotPath;
		private final String finalUrl;

		private StepResult(final boolean pass, final String details, final String screenshotPath, final String finalUrl) {
			this.pass = pass;
			this.details = details;
			this.screenshotPath = screenshotPath;
			this.finalUrl = finalUrl;
		}

		private static StepResult pass(final String details, final String screenshotPath, final String finalUrl) {
			return new StepResult(true, details, screenshotPath, finalUrl);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details == null ? "No details." : details, null, null);
		}
	}
}
