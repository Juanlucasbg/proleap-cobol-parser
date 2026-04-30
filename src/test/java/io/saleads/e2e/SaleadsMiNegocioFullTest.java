package io.saleads.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
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
import java.util.Locale;
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
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end SaleADS Mi Negocio workflow test.
 *
 * <p>
 * Execution is opt-in to avoid impacting the existing parser test suite.
 * Enable with: -Dsaleads.e2e.enabled=true
 * </p>
 *
 * <p>
 * Useful system properties:
 * </p>
 * <ul>
 * <li>saleads.e2e.enabled=true|false (default false)</li>
 * <li>saleads.login.url=https://... (optional, environment-agnostic)</li>
 * <li>saleads.headless=true|false (default false)</li>
 * <li>saleads.screenshots.dir=target/saleads-screenshots (optional)</li>
 * <li>saleads.account.email=juanlucasbarbiergarzon@gmail.com (optional override)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(20);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDir;
	private String appTabHandle;

	private final Map<String, String> report = new LinkedHashMap<>();
	private String termsUrl = "";
	private String privacyUrl = "";

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue("Skipping SaleADS E2E test. Enable with -Dsaleads.e2e.enabled=true", enabled);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-popup-blocking");
		options.addArguments("--disable-notifications");

		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "false"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		screenshotsDir = createScreenshotsDir();

		final String loginUrl = System.getProperty("saleads.login.url", "").trim();
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
			waitForUiToSettle();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		ensureNotBlank("Current URL is blank. Provide -Dsaleads.login.url or open login before execution.",
				driver.getCurrentUrl());

		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final List<String> failures = new ArrayList<>();
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			if (!entry.getValue().startsWith("PASS")) {
				failures.add(entry.getKey() + ": " + entry.getValue());
			}
		}

		final String finalReport = buildFinalReport();
		System.out.println(finalReport);

		if (!failures.isEmpty()) {
			fail("One or more SaleADS validations failed:\n" + finalReport);
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		clickFirstVisibleByText(Arrays.asList("Sign in with Google", "Iniciar con Google", "Continuar con Google",
				"Iniciar sesión con Google"));
		waitForUiToSettle();

		final String expectedEmail = System.getProperty("saleads.account.email", "juanlucasbarbiergarzon@gmail.com")
				.trim();
		final List<WebElement> emailOptions = driver.findElements(containsTextXpath(expectedEmail));
		if (!emailOptions.isEmpty() && emailOptions.get(0).isDisplayed()) {
			emailOptions.get(0).click();
			waitForUiToSettle();
		}

		assertAnyVisible("Main app interface was not found after login.",
				By.xpath("//aside|//*[@role='navigation']|//*[contains(@class,'sidebar')]"));
		assertAnyVisible("Left sidebar navigation not visible.",
				By.xpath("//aside|//*[@role='navigation']|//*[contains(@class,'sidebar')]"));

		appTabHandle = driver.getWindowHandle();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickIfVisible(By.xpath("//*[normalize-space()='Negocio']"));
		waitForUiToSettle();
		clickFirstVisibleByText(Arrays.asList("Mi Negocio"));
		waitForUiToSettle();

		assertAnyVisible("'Agregar Negocio' option is not visible.", containsTextXpath("Agregar Negocio"));
		assertAnyVisible("'Administrar Negocios' option is not visible.", containsTextXpath("Administrar Negocios"));
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickFirstVisibleByText(Arrays.asList("Agregar Negocio"));
		waitForUiToSettle();

		assertAnyVisible("Modal title 'Crear Nuevo Negocio' is not visible.", containsTextXpath("Crear Nuevo Negocio"));
		assertAnyVisible("Input field 'Nombre del Negocio' is missing.",
				By.xpath("//input[@placeholder='Nombre del Negocio']|//label[contains(normalize-space(.),'Nombre del Negocio')]"));
		assertAnyVisible("Text 'Tienes 2 de 3 negocios' is not visible.", containsTextXpath("Tienes 2 de 3 negocios"));
		assertAnyVisible("Button 'Cancelar' is not visible.", byButtonText("Cancelar"));
		assertAnyVisible("Button 'Crear Negocio' is not visible.", byButtonText("Crear Negocio"));

		takeScreenshot("03-agregar-negocio-modal");

		final List<WebElement> nameField = driver
				.findElements(By.xpath("//input[@placeholder='Nombre del Negocio']|//input[contains(@name,'negocio')]"));
		if (!nameField.isEmpty() && nameField.get(0).isDisplayed()) {
			nameField.get(0).click();
			nameField.get(0).clear();
			nameField.get(0).sendKeys("Negocio Prueba Automatizacion");
			waitForUiToSettle();
		}

		clickFirstVisibleByText(Arrays.asList("Cancelar"));
		waitForUiToSettle();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		expandMiNegocioIfCollapsed();
		clickFirstVisibleByText(Arrays.asList("Administrar Negocios"));
		waitForUiToSettle();

		assertAnyVisible("Section 'Información General' does not exist.", containsTextXpath("Información General"));
		assertAnyVisible("Section 'Detalles de la Cuenta' does not exist.", containsTextXpath("Detalles de la Cuenta"));
		assertAnyVisible("Section 'Tus Negocios' does not exist.", containsTextXpath("Tus Negocios"));
		assertAnyVisible("Section 'Sección Legal' does not exist.", containsTextXpath("Sección Legal"));
		takeScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		final String text = normalizedText(section.getText());

		assertContainsCaseInsensitive(text, "BUSINESS PLAN", "Text 'BUSINESS PLAN' is not visible.");
		assertContainsCaseInsensitive(text, "Cambiar Plan", "Button text 'Cambiar Plan' is not visible.");
		assertTrue("User email is not visible.", EMAIL_PATTERN.matcher(text).find());

		final String[] lines = text.split("\\n");
		boolean foundLikelyUserName = false;
		for (final String line : lines) {
			final String value = line.trim();
			if (value.isEmpty()) {
				continue;
			}

			final String lower = value.toLowerCase(Locale.ROOT);
			if (!EMAIL_PATTERN.matcher(value).find() && !lower.contains("información general") && !lower.contains("business")
					&& !lower.contains("cambiar plan") && value.length() >= 3) {
				foundLikelyUserName = true;
				break;
			}
		}
		assertTrue("User name is not visible.", foundLikelyUserName);
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		final String text = normalizedText(section.getText());
		assertContainsCaseInsensitive(text, "Cuenta creada", "'Cuenta creada' is not visible.");
		assertContainsCaseInsensitive(text, "Estado activo", "'Estado activo' is not visible.");
		assertContainsCaseInsensitive(text, "Idioma seleccionado", "'Idioma seleccionado' is not visible.");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final String text = normalizedText(section.getText());

		assertTrue("Business list is not visible.", text.length() > 20);
		assertContainsCaseInsensitive(text, "Agregar Negocio", "Button 'Agregar Negocio' is missing.");
		assertContainsCaseInsensitive(text, "Tienes 2 de 3 negocios", "Text 'Tienes 2 de 3 negocios' is not visible.");
	}

	private void stepValidateTerminosCondiciones() throws Exception {
		termsUrl = validateLegalLink("Términos y Condiciones", "08-terminos-condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		privacyUrl = validateLegalLink("Política de Privacidad", "09-politica-privacidad");
	}

	private String validateLegalLink(final String legalText, final String screenshotName) throws Exception {
		expandMiNegocioIfCollapsed();
		final WebElement link = wait.until(ExpectedConditions.elementToBeClickable(containsTextXpath(legalText)));

		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String originalUrl = driver.getCurrentUrl();

		link.click();
		waitForUiToSettle();

		boolean switchedToNewTab = false;
		final String newHandle = waitForNewWindowHandle(handlesBefore, Duration.ofSeconds(6));
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			switchedToNewTab = true;
			waitForUiToSettle();
		} else if (driver.getCurrentUrl().equals(originalUrl)) {
			// Some environments need an additional settle cycle after click-driven navigation.
			waitForUiToSettle();
		}

		assertAnyVisible("Expected legal heading '" + legalText + "' is not visible.", containsTextXpath(legalText));

		final String bodyText = normalizedText(driver.findElement(By.tagName("body")).getText());
		assertTrue("Legal content text is not visible for '" + legalText + "'.", bodyText.length() > 120);

		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();
		ensureNotBlank("Captured legal URL is empty for " + legalText, finalUrl);

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToSettle();
		} else if (!originalUrl.equals(finalUrl)) {
			driver.navigate().back();
			waitForUiToSettle();
		}

		if (appTabHandle != null && !appTabHandle.isEmpty()) {
			driver.switchTo().window(appTabHandle);
		}

		return finalUrl;
	}

	private void expandMiNegocioIfCollapsed() {
		final List<WebElement> adminItems = driver.findElements(containsTextXpath("Administrar Negocios"));
		if (!adminItems.isEmpty() && adminItems.get(0).isDisplayed()) {
			return;
		}

		final List<WebElement> miNegocioItems = driver.findElements(containsTextXpath("Mi Negocio"));
		if (!miNegocioItems.isEmpty() && miNegocioItems.get(0).isDisplayed()) {
			miNegocioItems.get(0).click();
			waitForUiToSettle();
		}
	}

	private WebElement findSectionByHeading(final String headingText) {
		final By locator = By.xpath("//*[normalize-space()='" + headingText
				+ "']/ancestor::*[self::section or self::div][1] | //*[contains(normalize-space(.),'" + headingText
				+ "')]/ancestor::*[self::section or self::div][1]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void runStep(final String stepName, final StepRunnable runnable) {
		try {
			runnable.run();
			report.put(stepName, "PASS");
		} catch (final Exception ex) {
			report.put(stepName, "FAIL - " + safeMessage(ex));
			try {
				takeScreenshot("failed-" + stepName.toLowerCase(Locale.ROOT).replace(" ", "-"));
			} catch (final Exception ignored) {
				// Best-effort screenshot only.
			}
		}
	}

	private void clickFirstVisibleByText(final List<String> options) {
		for (final String text : options) {
			final List<WebElement> matches = driver.findElements(containsTextXpath(text));
			for (final WebElement element : matches) {
				if (element.isDisplayed() && element.isEnabled()) {
					element.click();
					waitForUiToSettle();
					return;
				}
			}
		}
		throw new IllegalStateException("None of the expected clickable texts were found: " + options);
	}

	private void clickIfVisible(final By by) {
		final List<WebElement> found = driver.findElements(by);
		if (!found.isEmpty() && found.get(0).isDisplayed() && found.get(0).isEnabled()) {
			found.get(0).click();
			waitForUiToSettle();
		}
	}

	private void waitForUiToSettle() {
		try {
			wait.until(documentReady());
		} catch (final Exception ignored) {
			// Some SPAs do not reliably report complete state transitions.
		}

		try {
			Thread.sleep(700);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private ExpectedCondition<Boolean> documentReady() {
		return driverInstance -> {
			if (!(driverInstance instanceof JavascriptExecutor)) {
				return true;
			}

			final Object state = ((JavascriptExecutor) driverInstance).executeScript("return document.readyState");
			return state != null && "complete".equalsIgnoreCase(state.toString());
		};
	}

	private void assertAnyVisible(final String errorMessage, final By... locators) {
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}
		throw new IllegalStateException(errorMessage);
	}

	private void assertContainsCaseInsensitive(final String source, final String expected, final String errorMessage) {
		if (!source.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT))) {
			throw new IllegalStateException(errorMessage + " Source: " + source);
		}
	}

	private String normalizedText(final String text) {
		return text == null ? "" : text.replace('\r', '\n').replaceAll("\\n{2,}", "\n").trim();
	}

	private By containsTextXpath(final String text) {
		final String escaped = escapeXpathText(text);
		return By.xpath("//*[contains(normalize-space(.), " + escaped + ")]");
	}

	private By byButtonText(final String text) {
		final String escaped = escapeXpathText(text);
		return By.xpath("//button[contains(normalize-space(.), " + escaped + ")]");
	}

	private String escapeXpathText(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		return "concat('" + text.replace("'", "',\"'\",'") + "')";
	}

	private String waitForNewWindowHandle(final Set<String> handlesBefore, final Duration timeout) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			return shortWait.until(driverInstance -> {
				final Set<String> handlesAfter = driverInstance.getWindowHandles();
				for (final String handle : handlesAfter) {
					if (!handlesBefore.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException ignored) {
			return null;
		}
	}

	private Path createScreenshotsDir() throws IOException {
		final String configured = System.getProperty("saleads.screenshots.dir", "target/saleads-screenshots").trim();
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Paths.get(configured, timestamp);
		return Files.createDirectories(dir);
	}

	private void takeScreenshot(final String name) throws IOException {
		final String safeName = name.replaceAll("[^a-zA-Z0-9-_.]", "_");
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = screenshotsDir.resolve(safeName + ".png");
		Files.copy(source.toPath(), destination);
	}

	private void ensureNotBlank(final String message, final String value) {
		if (value == null || value.trim().isEmpty() || "about:blank".equalsIgnoreCase(value.trim())) {
			throw new IllegalStateException(message);
		}
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Workflow - Final Report\n");
		builder.append("Screenshots directory: ").append(screenshotsDir.toAbsolutePath()).append("\n");
		builder.append("--------------------------------------------------\n");

		appendReportLine(builder, "Login");
		appendReportLine(builder, "Mi Negocio menu");
		appendReportLine(builder, "Agregar Negocio modal");
		appendReportLine(builder, "Administrar Negocios view");
		appendReportLine(builder, "Información General");
		appendReportLine(builder, "Detalles de la Cuenta");
		appendReportLine(builder, "Tus Negocios");
		appendReportLine(builder, "Términos y Condiciones");
		appendReportLine(builder, "Política de Privacidad");

		builder.append("--------------------------------------------------\n");
		builder.append("Términos y Condiciones URL: ").append(termsUrl.isEmpty() ? "N/A" : termsUrl).append("\n");
		builder.append("Política de Privacidad URL: ").append(privacyUrl.isEmpty() ? "N/A" : privacyUrl).append("\n");

		return builder.toString();
	}

	private void appendReportLine(final StringBuilder builder, final String field) {
		builder.append(field).append(": ").append(report.getOrDefault(field, "FAIL - Not executed")).append("\n");
	}

	private String safeMessage(final Exception ex) {
		if (ex.getMessage() == null || ex.getMessage().trim().isEmpty()) {
			return ex.getClass().getSimpleName();
		}
		return ex.getMessage().replace('\n', ' ').trim();
	}

	@FunctionalInterface
	private interface StepRunnable {
		void run() throws Exception;
	}
}
