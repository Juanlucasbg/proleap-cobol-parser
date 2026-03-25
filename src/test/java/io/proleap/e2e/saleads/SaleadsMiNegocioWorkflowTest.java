package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assume;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow for SaleADS "Mi Negocio" module.
 *
 * <p>This test is intentionally disabled by default to avoid affecting normal unit-test runs.
 * Enable with: -Dsaleads.e2e.enabled=true</p>
 *
 * <p>Environment assumptions:
 * <ul>
 *   <li>Browser starts on SaleADS login page, or base URL is passed with -Dsaleads.baseUrl=...</li>
 *   <li>Google account can be selected, if prompted</li>
 * </ul>
 * </p>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String ENABLED_PROP = "saleads.e2e.enabled";
	private static final String BASE_URL_PROP = "saleads.baseUrl";
	private static final String HEADLESS_PROP = "saleads.headless";
	private static final String GOOGLE_EMAIL_PROP = "saleads.google.email";
	private static final String DEBUGGER_ADDRESS_PROP = "saleads.chrome.debuggerAddress";

	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";
	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration LOGIN_TIMEOUT = Duration.ofSeconds(90);

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informacion General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESS_LIST = "Tus Negocios";
	private static final String STEP_TERMS = "Terminos y Condiciones";
	private static final String STEP_PRIVACY = "Politica de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private String googleEmail;
	private Path artifactsDir;
	private Path screenshotsDir;
	private final Map<String, String> stepResult = new LinkedHashMap<>();
	private final List<String> reportNotes = new ArrayList<>();
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";
	private boolean hardFailure;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set -Dsaleads.e2e.enabled=true to run this test.",
				Boolean.parseBoolean(System.getProperty(ENABLED_PROP, "false")));

		initializeStepDefaults();
		googleEmail = System.getProperty(GOOGLE_EMAIL_PROP, DEFAULT_GOOGLE_EMAIL);

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT));
		artifactsDir = Path.of("target", "saleads-mi-negocio", timestamp);
		screenshotsDir = artifactsDir.resolve("screenshots");
		Files.createDirectories(screenshotsDir);

		final ChromeOptions options = new ChromeOptions();
		final String debuggerAddress = System.getProperty(DEBUGGER_ADDRESS_PROP, "").trim();

		if (!debuggerAddress.isEmpty()) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		} else {
			final boolean headless = Boolean.parseBoolean(System.getProperty(HEADLESS_PROP, "false"));
			if (headless) {
				options.addArguments("--headless=new");
			}
			options.addArguments("--window-size=1920,1080");
			options.addArguments("--disable-gpu");
			options.addArguments("--no-sandbox");
			options.addArguments("--disable-dev-shm-usage");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);

		final String baseUrl = System.getProperty(BASE_URL_PROP, "").trim();
		if (!baseUrl.isEmpty()) {
			driver.get(baseUrl);
			waitForUiLoad();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep(STEP_LOGIN, this::stepLoginWithGoogle);
		runStep(STEP_MENU, this::stepOpenMiNegocioMenu);
		runStep(STEP_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(STEP_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		runStep(STEP_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(STEP_ACCOUNT_DETAILS, this::stepValidateDetallesCuenta);
		runStep(STEP_BUSINESS_LIST, this::stepValidateTusNegocios);
		runStep(STEP_TERMS, () -> {
			termsUrl = stepValidateLegalDocument("Términos y Condiciones", "Términos y Condiciones", "08_terminos");
		});
		runStep(STEP_PRIVACY, () -> {
			privacyUrl = stepValidateLegalDocument("Política de Privacidad", "Política de Privacidad", "09_politica");
		});

		final List<String> failures = stepResult.entrySet().stream()
				.filter(entry -> !"PASS".equals(entry.getValue()))
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());
		assertTrue("Failing workflow steps: " + failures + ". See report under: " + artifactsDir, failures.isEmpty());
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	private void stepLoginWithGoogle() {
		WebElement loginButton = firstVisible(
				byXPathText("Sign in with Google"),
				byXPathText("Iniciar sesión con Google"),
				byXPathText("Iniciar sesion con Google"),
				byXPathText("Google"),
				byXPathText("Iniciar sesión"),
				byXPathText("Iniciar sesion"),
				byXPathText("Login"));

		String appWindow = driver.getWindowHandle();
		Set<String> beforeHandles = driver.getWindowHandles();

		clickAndWait(loginButton);

		String authWindow = waitForNewWindowIfAny(beforeHandles);
		if (authWindow != null) {
			driver.switchTo().window(authWindow);
			waitForUiLoad();
		}

		selectGoogleAccountIfVisible(googleEmail);

		if (!driver.getWindowHandle().equals(appWindow) && driver.getWindowHandles().contains(appWindow)) {
			driver.switchTo().window(appWindow);
		}

		waitLong().until(d -> isSidebarVisible() && !isAnyTextPresent("Sign in with Google"));
		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() {
		wait.until(d -> isSidebarVisible());

		WebElement negocioSection = firstVisible(
				byXPathText("Negocio"),
				byXPathText("Mi Negocio"));
		clickAndWait(negocioSection);

		WebElement miNegocioOption = firstVisible(
				byXPathText("Mi Negocio"),
				byXPathText("Mi negocio"));
		clickAndWait(miNegocioOption);

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02_menu_expandido");
	}

	private void stepValidateAgregarNegocioModal() {
		WebElement agregarNegocio = firstVisible(byXPathText("Agregar Negocio"));
		clickAndWait(agregarNegocio);

		assertTextVisible("Crear Nuevo Negocio");
		WebElement nombreNegocio = firstVisible(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[@name='nombreNegocio']"),
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"));
		assertVisible(nombreNegocio);
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		nombreNegocio.click();
		nombreNegocio.clear();
		nombreNegocio.sendKeys(TEST_BUSINESS_NAME);

		takeScreenshot("03_modal_crear_negocio");
		clickAndWait(firstVisible(byXPathText("Cancelar")));
	}

	private void stepOpenAdministrarNegocios() {
		if (!isAnyTextPresent("Administrar Negocios")) {
			WebElement miNegocio = firstVisible(byXPathText("Mi Negocio"));
			clickAndWait(miNegocio);
		}

		WebElement administrarNegocios = firstVisible(byXPathText("Administrar Negocios"));
		clickAndWait(administrarNegocios);

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");

		takeScreenshot("04_administrar_negocios");
	}

	private void stepValidateInformacionGeneral() {
		WebElement infoSection = sectionByHeading("Información General");
		assertVisible(infoSection);

		final String sectionText = normalize(infoSection.getText());
		assertTrue("Expected visible user name.", hasVisibleNonTrivialValue(infoSection, "name", "nombre", "@"));
		assertTrue("Expected visible user email.", sectionText.contains("@"));
		assertTrue("Expected BUSINESS PLAN text.", sectionText.contains("business plan"));
		assertElementPresentWithin(infoSection, byXPathText("Cambiar Plan"));
	}

	private void stepValidateDetallesCuenta() {
		WebElement detailsSection = sectionByHeading("Detalles de la Cuenta");
		assertVisible(detailsSection);
		assertTextInside(detailsSection, "Cuenta creada");
		assertTextInside(detailsSection, "Estado activo");
		assertTextInside(detailsSection, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		WebElement businessSection = sectionByHeading("Tus Negocios");
		assertVisible(businessSection);

		List<WebElement> rows = businessSection.findElements(By.xpath(".//*[self::li or self::tr or self::div][contains(normalize-space(), 'Negocio')]"));
		assertTrue("Expected business list to be visible.", !rows.isEmpty() || businessSection.getText().length() > 30);
		assertElementPresentWithin(businessSection, byXPathText("Agregar Negocio"));
		assertTextInside(businessSection, "Tienes 2 de 3 negocios");
	}

	private String stepValidateLegalDocument(final String linkText, final String headingText, final String screenshotName) {
		WebElement legalSection = sectionByHeading("Sección Legal");
		assertVisible(legalSection);

		String appWindow = driver.getWindowHandle();
		Set<String> beforeHandles = driver.getWindowHandles();
		String previousUrl = driver.getCurrentUrl();

		WebElement link = firstVisibleWithin(legalSection, byXPathText(linkText), By.linkText(linkText));
		clickAndWait(link);

		String newWindow = waitForNewWindowIfAny(beforeHandles);
		if (newWindow != null) {
			driver.switchTo().window(newWindow);
			waitForUiLoad();
		} else {
			wait.until(d -> !Objects.equals(previousUrl, d.getCurrentUrl()) || isAnyTextPresent(headingText));
			waitForUiLoad();
		}

		assertTextVisible(headingText);
		assertLegalContentVisible();
		takeScreenshot(screenshotName);

		String finalUrl = driver.getCurrentUrl();
		reportNotes.add(linkText + " URL: " + finalUrl);

		if (newWindow != null) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
			wait.until(d -> isAnyTextPresent("Sección Legal"));
		}

		return finalUrl;
	}

	private void runStep(final String reportField, final Runnable action) {
		if (hardFailure) {
			stepResult.put(reportField, "FAIL");
			reportNotes.add(reportField + ": skipped because a previous fatal step failed.");
			return;
		}

		try {
			action.run();
			stepResult.put(reportField, "PASS");
		} catch (Exception ex) {
			stepResult.put(reportField, "FAIL");
			reportNotes.add(reportField + " failed: " + ex.getMessage());
			takeScreenshotQuietly("fail_" + sanitizeName(reportField));

			// If we cannot login or cannot reach account page, following steps are not reliable.
			if (STEP_LOGIN.equals(reportField) || STEP_ADMIN_VIEW.equals(reportField)) {
				hardFailure = true;
			}
		}
	}

	private void initializeStepDefaults() {
		stepResult.put(STEP_LOGIN, "FAIL");
		stepResult.put(STEP_MENU, "FAIL");
		stepResult.put(STEP_MODAL, "FAIL");
		stepResult.put(STEP_ADMIN_VIEW, "FAIL");
		stepResult.put(STEP_INFO_GENERAL, "FAIL");
		stepResult.put(STEP_ACCOUNT_DETAILS, "FAIL");
		stepResult.put(STEP_BUSINESS_LIST, "FAIL");
		stepResult.put(STEP_TERMS, "FAIL");
		stepResult.put(STEP_PRIVACY, "FAIL");
	}

	private void writeFinalReport() throws IOException {
		if (artifactsDir == null) {
			return;
		}

		Files.createDirectories(artifactsDir);
		Path reportFile = artifactsDir.resolve("final-report.md");
		Path jsonFile = artifactsDir.resolve("final-report.json");

		StringBuilder md = new StringBuilder();
		md.append("# SaleADS Mi Negocio Workflow Report\n\n");
		md.append("- Timestamp: ").append(LocalDateTime.now()).append("\n");
		md.append("- Terms URL: ").append(termsUrl).append("\n");
		md.append("- Privacy URL: ").append(privacyUrl).append("\n\n");
		md.append("## PASS/FAIL by Validation Step\n\n");
		for (Map.Entry<String, String> entry : stepResult.entrySet()) {
			md.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
		}

		if (!reportNotes.isEmpty()) {
			md.append("\n## Notes\n\n");
			for (String note : reportNotes) {
				md.append("- ").append(note).append("\n");
			}
		}

		Files.writeString(reportFile, md.toString(), StandardCharsets.UTF_8);

		String json = "{\n"
				+ "  \"login\": \"" + stepResult.get(STEP_LOGIN) + "\",\n"
				+ "  \"miNegocioMenu\": \"" + stepResult.get(STEP_MENU) + "\",\n"
				+ "  \"agregarNegocioModal\": \"" + stepResult.get(STEP_MODAL) + "\",\n"
				+ "  \"administrarNegociosView\": \"" + stepResult.get(STEP_ADMIN_VIEW) + "\",\n"
				+ "  \"informacionGeneral\": \"" + stepResult.get(STEP_INFO_GENERAL) + "\",\n"
				+ "  \"detallesCuenta\": \"" + stepResult.get(STEP_ACCOUNT_DETAILS) + "\",\n"
				+ "  \"tusNegocios\": \"" + stepResult.get(STEP_BUSINESS_LIST) + "\",\n"
				+ "  \"terminosYCondiciones\": \"" + stepResult.get(STEP_TERMS) + "\",\n"
				+ "  \"politicaPrivacidad\": \"" + stepResult.get(STEP_PRIVACY) + "\",\n"
				+ "  \"terminosUrl\": \"" + escapeJson(termsUrl) + "\",\n"
				+ "  \"politicaUrl\": \"" + escapeJson(privacyUrl) + "\"\n"
				+ "}\n";
		Files.writeString(jsonFile, json, StandardCharsets.UTF_8);
	}

	private void waitForUiLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
	}

	private WebDriverWait waitLong() {
		return new WebDriverWait(driver, LOGIN_TIMEOUT);
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiLoad();
	}

	private void assertVisible(final WebElement element) {
		assertTrue("Expected visible element.", element != null && element.isDisplayed());
	}

	private void assertTextVisible(final String text) {
		final String escaped = escapeXPathText(text);
		By locator = By.xpath("//*[contains(normalize-space(), " + escaped + ")]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void assertTextInside(final WebElement container, final String text) {
		final String escaped = escapeXPathText(text);
		List<WebElement> elements = container.findElements(By.xpath(".//*[contains(normalize-space(), " + escaped + ")]"));
		assertTrue("Expected text in section: " + text, elements.stream().anyMatch(WebElement::isDisplayed));
	}

	private void assertElementPresentWithin(final WebElement container, final By locator) {
		List<WebElement> elements = container.findElements(locator);
		assertTrue("Expected element in section using locator: " + locator,
				elements.stream().anyMatch(WebElement::isDisplayed));
	}

	private WebElement sectionByHeading(final String heading) {
		final String escaped = escapeXPathText(heading);
		By by = By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::div][contains(normalize-space(), "
				+ escaped + ")]/ancestor::*[self::section or self::article or self::div][1]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private boolean isSidebarVisible() {
		List<By> candidates = Arrays.asList(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar') or contains(@id,'sidebar')]"));
		for (By by : candidates) {
			for (WebElement el : driver.findElements(by)) {
				if (el.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private String waitForNewWindowIfAny(final Set<String> beforeHandles) {
		AtomicBoolean found = new AtomicBoolean(false);
		try {
			wait.until(d -> {
				Set<String> current = d.getWindowHandles();
				if (current.size() > beforeHandles.size()) {
					found.set(true);
					return true;
				}
				return false;
			});
		} catch (TimeoutException ignored) {
			// no new window opened; continue in same tab
		}

		if (!found.get()) {
			return null;
		}

		for (String handle : driver.getWindowHandles()) {
			if (!beforeHandles.contains(handle)) {
				return handle;
			}
		}
		return null;
	}

	private void selectGoogleAccountIfVisible(final String email) {
		try {
			WebElement account = firstVisible(
					By.xpath("//*[contains(normalize-space(), " + escapeXPathText(email) + ")]"),
					By.xpath("//*[contains(@data-email, " + escapeXPathText(email) + ")]"));
			clickAndWait(account);
		} catch (Exception ignored) {
			// Not every environment will display account picker.
		}
	}

	private boolean hasVisibleNonTrivialValue(final WebElement container, final String... hints) {
		final String lowercase = normalize(container.getText());
		for (String hint : hints) {
			if (lowercase.contains(hint.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return lowercase.length() > 25;
	}

	private void assertLegalContentVisible() {
		List<WebElement> paragraphs = driver.findElements(By.xpath("//p[normalize-space()]"));
		boolean visibleParagraph = paragraphs.stream().anyMatch(el -> el.isDisplayed() && el.getText().trim().length() > 20);
		if (visibleParagraph) {
			return;
		}

		String bodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected visible legal content text.", bodyText != null && bodyText.trim().length() > 100);
	}

	private boolean isAnyTextPresent(final String text) {
		try {
			String escaped = escapeXPathText(text);
			return driver.findElements(By.xpath("//*[contains(normalize-space(), " + escaped + ")]"))
					.stream()
					.anyMatch(WebElement::isDisplayed);
		} catch (NoSuchElementException ex) {
			return false;
		}
	}

	private WebElement firstVisible(final By... locators) {
		TimeoutException lastTimeout = null;
		for (By locator : locators) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (TimeoutException ex) {
				lastTimeout = ex;
			}
		}
		if (lastTimeout != null) {
			throw lastTimeout;
		}
		throw new TimeoutException("No visible locator matched.");
	}

	private WebElement firstVisibleWithin(final WebElement container, final By... locators) {
		for (By locator : locators) {
			List<WebElement> elements = container.findElements(locator);
			for (WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		throw new TimeoutException("No visible element matched inside section.");
	}

	private By byXPathText(final String text) {
		final String escaped = escapeXPathText(text);
		return By.xpath(
				"//*[self::a or self::button or @role='button' or self::span or self::div][contains(normalize-space(), " + escaped + ")]");
	}

	private String escapeXPathText(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		String[] parts = value.split("'");
		StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String normalize(final String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private void takeScreenshot(final String name) {
		try {
			final Path screenshot = screenshotsDir.resolve(name + ".png");
			final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(source, screenshot, StandardCopyOption.REPLACE_EXISTING);
			reportNotes.add("Screenshot: " + screenshot);
		} catch (Exception ex) {
			reportNotes.add("Failed to capture screenshot [" + name + "]: " + ex.getMessage());
		}
	}

	private void takeScreenshotQuietly(final String name) {
		try {
			takeScreenshot(name);
		} catch (Exception ignored) {
			// no-op
		}
	}

	private String sanitizeName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
	}

	private String escapeJson(final String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
