package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String REPORT_NAME = "saleads_mi_negocio_full_test";

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> results = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private final Map<String, String> stepUrls = new LinkedHashMap<>();

	private String appWindowHandle;
	private Path evidenceDirectory;
	private String loginUrl;
	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(firstNonBlank(System.getProperty("saleads.e2e.enabled"),
				System.getenv("SALEADS_E2E_ENABLED"), "false"));
		Assume.assumeTrue("Enable this test with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true.", enabled);

		loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"), "");
		assertFalse(
				"Missing SaleADS login URL. Provide -Dsaleads.login.url=<url> or SALEADS_LOGIN_URL. The URL is environment-specific.",
				loginUrl.trim().isEmpty());

		evidenceDirectory = Files.createDirectories(Paths.get("target", "saleads-evidence",
				DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-') + "-" + UUID.randomUUID()));
		driver = createDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(getTimeoutSeconds()));

		driver.get(loginUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() throws IOException {
		if (evidenceDirectory != null) {
			writeFinalReport();
		}

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		executeStep("Política de Privacidad", this::stepValidatePoliticaDePrivacidad);

		final List<String> failed = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.get(field);
			if (result == null || !result.passed) {
				failed.add(field);
			}
		}

		assertTrue("One or more SaleADS validations failed: " + failed, failed.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		final boolean clickedLogin = clickFirstByExactText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google",
				"Ingresar con Google", "Continuar con Google", "Acceder con Google"));
		if (!clickedLogin) {
			final WebElement googleButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
					"//button[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'google')]"
							+ " | //a[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'google')]"
							+ " | //*[@role='button' and contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'google')]")));
			clickAndWait(googleButton);
		}

		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");

		wait.until((ExpectedCondition<Boolean>) d -> isSidebarVisible() || isVisibleText("Negocio")
				|| isVisibleText("Mi Negocio") || isVisibleText("Administrar Negocios"));
		assertTrue("Main application interface did not load after Google login.",
				isSidebarVisible() || isVisibleText("Negocio") || isVisibleText("Mi Negocio"));

		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickIfPresent("Negocio");
		clickByExactText("Mi Negocio");
		waitForUiToLoad();

		assertTrue("Expected 'Agregar Negocio' to be visible in the expanded menu.", isVisibleText("Agregar Negocio"));
		assertTrue("Expected 'Administrar Negocios' to be visible in the expanded menu.",
				isVisibleText("Administrar Negocios"));

		captureScreenshot("02_mi_negocio_menu");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByExactText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final WebElement businessInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio')] | //label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]")));
		assertTrue("Expected input field 'Nombre del Negocio' to exist.", businessInput.isDisplayed());

		captureScreenshot("03_agregar_negocio_modal");

		businessInput.click();
		businessInput.clear();
		businessInput.sendKeys("Negocio Prueba Automatizacion");
		clickByExactText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(
				"//*[contains(normalize-space(.), 'Crear Nuevo Negocio') and not(self::script) and not(self::style)]")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isVisibleText("Administrar Negocios")) {
			clickByExactText("Mi Negocio");
		}

		clickByExactText("Administrar Negocios");
		waitForUiToLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertTrue("Expected legal section to be visible.",
				isVisibleText("Sección Legal") || isVisibleText("Terminos y Condiciones")
						|| isVisibleText("Términos y Condiciones"));

		captureScreenshot("04_administrar_negocios");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		assertVisibleText("Información General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String sectionText = textAroundHeading("Información General");
		final String email = extractEmail(sectionText);
		assertFalse("Expected user email to be visible in 'Información General'.", email.isEmpty());
		assertTrue("Expected user name to be visible in 'Información General'.",
				containsPotentialName(sectionText, email));
	}

	private void stepValidateDetallesDeLaCuenta() throws Exception {
		assertVisibleText("Detalles de la Cuenta");
		assertTrue("Expected 'Cuenta creada' to be visible.",
				isVisibleText("Cuenta creada") || isVisibleText("Cuenta Creada"));
		assertTrue("Expected 'Estado activo' to be visible.",
				isVisibleText("Estado activo") || isVisibleText("Estado Activo"));
		assertTrue("Expected 'Idioma seleccionado' to be visible.",
				isVisibleText("Idioma seleccionado") || isVisibleText("Idioma Seleccionado"));
	}

	private void stepValidateTusNegocios() throws Exception {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		final String finalUrl = validateLegalLink("Términos y Condiciones", "08_terminos");
		assertFalse("Expected final URL for 'Términos y Condiciones'.", finalUrl.isEmpty());
		stepDetails.put("Términos y Condiciones", "Final URL: " + finalUrl);
		stepUrls.put("Términos y Condiciones", finalUrl);
	}

	private void stepValidatePoliticaDePrivacidad() throws Exception {
		final String finalUrl = validateLegalLink("Política de Privacidad", "09_politica_privacidad");
		assertFalse("Expected final URL for 'Política de Privacidad'.", finalUrl.isEmpty());
		stepDetails.put("Política de Privacidad", "Final URL: " + finalUrl);
		stepUrls.put("Política de Privacidad", finalUrl);
	}

	private String validateLegalLink(final String linkText, final String screenshotName) throws Exception {
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String currentUrlBeforeClick = driver.getCurrentUrl();

		clickByExactText(linkText);

		String selectedHandle = null;
		try {
			selectedHandle = wait.until(d -> {
				final Set<String> handlesNow = d.getWindowHandles();
				if (handlesNow.size() > handlesBefore.size()) {
					for (final String handle : handlesNow) {
						if (!handlesBefore.contains(handle)) {
							return handle;
						}
					}
				}
				return null;
			});
		} catch (final TimeoutException ignored) {
			// Same-tab navigation is valid for these links.
		}

		if (selectedHandle != null) {
			driver.switchTo().window(selectedHandle);
		}

		waitForUiToLoad();

		final boolean headingVisible = isVisibleText(linkText) || isVisibleText(normalizeAscii(linkText));
		assertTrue("Expected legal heading '" + linkText + "' to be visible.", headingVisible);

		final String legalPageText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected legal content text to be visible.",
				legalPageText != null && legalPageText.trim().replace("\n", " ").length() > 120);

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (selectedHandle != null) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else if (!Objects.equals(currentUrlBeforeClick, driver.getCurrentUrl())) {
			driver.navigate().back();
		}

		waitForUiToLoad();
		return finalUrl;
	}

	private void selectGoogleAccountIfVisible(final String email) {
		try {
			final WebElement emailAccount = new WebDriverWait(driver, Duration.ofSeconds(12))
					.until(ExpectedConditions.elementToBeClickable(By.xpath(
							"//*[contains(normalize-space(.), '" + email + "') and (self::div or self::span or self::button or self::li)]")));
			clickAndWait(emailAccount);
		} catch (final TimeoutException ignored) {
			// Account picker did not appear, likely already authenticated.
		}
	}

	private void executeStep(final String reportField, final ThrowingRunnable action) throws IOException {
		try {
			action.run();
			final String screenshot = captureScreenshot("pass_" + slug(reportField));
			final String details = stepDetails.getOrDefault(reportField, "");
			final String url = stepUrls.getOrDefault(reportField, driver.getCurrentUrl());
			results.put(reportField, StepResult.pass().withDetails(details).withScreenshot(screenshot).withUrl(url));
		} catch (final Exception ex) {
			final String screenshot = captureScreenshot("fail_" + slug(reportField));
			results.put(reportField, StepResult.fail(ex.getMessage()).withScreenshot(screenshot).withUrl(driver.getCurrentUrl()));
		}
	}

	private WebDriver createDriver() {
		final String browser = firstNonBlank(System.getProperty("saleads.browser"), System.getenv("SALEADS_BROWSER"), "chrome")
				.toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(
				firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "false"));

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			return new FirefoxDriver(firefoxOptions);
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.addArguments("--window-size=1920,1080");
			chromeOptions.addArguments("--disable-gpu");
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			return new ChromeDriver(chromeOptions);
		}
	}

	private int getTimeoutSeconds() {
		final String timeoutValue = firstNonBlank(System.getProperty("saleads.timeout.seconds"),
				System.getenv("SALEADS_TIMEOUT_SECONDS"), "30");
		return Integer.parseInt(timeoutValue);
	}

	private boolean clickFirstByExactText(final List<String> candidateTexts) {
		for (final String text : candidateTexts) {
			try {
				clickByExactText(text);
				return true;
			} catch (final Exception ignored) {
				// Try next text variant.
			}
		}
		return false;
	}

	private void clickByExactText(final String text) {
		final String safeText = text.replace("'", "\\'");
		final By selector = By.xpath("//button[normalize-space(.)='" + safeText + "']"
				+ " | //a[normalize-space(.)='" + safeText + "']"
				+ " | //*[@role='button' and normalize-space(.)='" + safeText + "']"
				+ " | //*[(self::span or self::div or self::li) and normalize-space(.)='" + safeText + "']");

		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(selector));
		clickAndWait(element);
	}

	private void clickIfPresent(final String text) {
		final String safeText = text.replace("'", "\\'");
		final List<WebElement> elements = driver.findElements(By.xpath("//*[normalize-space(.)='" + safeText + "']"));
		for (final WebElement element : elements) {
			if (element.isDisplayed() && element.isEnabled()) {
				element.click();
				waitForUiToLoad();
				return;
			}
		}
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiToLoad();
	}

	private boolean isSidebarVisible() {
		final List<By> candidates = Arrays.asList(By.cssSelector("aside"), By.cssSelector("nav[aria-label]"),
				By.xpath("//aside//*[contains(normalize-space(.), 'Mi Negocio') or contains(normalize-space(.), 'Negocio')]"),
				By.xpath("//nav//*[contains(normalize-space(.), 'Mi Negocio') or contains(normalize-space(.), 'Negocio')]"));
		for (final By by : candidates) {
			try {
				final List<WebElement> elements = driver.findElements(by);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			} catch (final Exception ignored) {
				// Continue with other selectors.
			}
		}
		return false;
	}

	private boolean isVisibleText(final String text) {
		final String safeText = text.replace("'", "\\'");
		final By by = By.xpath("//*[normalize-space(.)='" + safeText + "']");
		final List<WebElement> elements = driver.findElements(by);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void assertVisibleText(final String text) {
		final String safeText = text.replace("'", "\\'");
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[normalize-space(.)='" + safeText + "']")));
	}

	private String textAroundHeading(final String headingText) {
		final String safeText = headingText.replace("'", "\\'");
		final By by = By.xpath("//*[normalize-space(.)='" + safeText + "']");
		final WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
		final WebElement container = heading.findElement(By.xpath(
				"./ancestor::*[self::section or self::article or self::div][.//*[normalize-space(.)='" + safeText + "']][1]"));
		return container.getText();
	}

	private String extractEmail(final String text) {
		if (text == null) {
			return "";
		}
		final Matcher matcher = EMAIL_PATTERN.matcher(text);
		if (matcher.find()) {
			return matcher.group(0);
		}
		return "";
	}

	private boolean containsPotentialName(final String sectionText, final String email) {
		if (sectionText == null || sectionText.trim().isEmpty()) {
			return false;
		}

		final String[] lines = sectionText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.equalsIgnoreCase("Información General") || line.equalsIgnoreCase("BUSINESS PLAN")
					|| line.equalsIgnoreCase("Cambiar Plan") || line.toLowerCase(Locale.ROOT).contains("correo")
					|| line.toLowerCase(Locale.ROOT).contains("email")) {
				continue;
			}
			if (!email.isEmpty() && line.contains(email)) {
				continue;
			}
			if (line.contains("@")) {
				continue;
			}
			if (line.length() > 2) {
				return true;
			}
		}

		return false;
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Continue if readyState cannot be read.
		}

		final List<By> loaders = Arrays.asList(By.cssSelector(".loading"), By.cssSelector(".spinner"),
				By.cssSelector("[aria-busy='true']"), By.xpath("//*[contains(translate(@class,'LOADINGSPINER','loadingspiner'),'loading')]"),
				By.xpath("//*[contains(translate(@class,'LOADINGSPINER','loadingspiner'),'spinner')]"));
		for (final By loader : loaders) {
			try {
				wait.until(ExpectedConditions.invisibilityOfElementLocated(loader));
			} catch (final Exception ignored) {
				// Ignore if this loader is not present.
			}
		}
	}

	private String captureScreenshot(final String checkpointName) throws IOException {
		final String screenshotFilename = checkpointName + ".png";
		final Path screenshotPath = evidenceDirectory.resolve(screenshotFilename);
		final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshotPath, bytes);
		return screenshotPath.toString();
	}

	private void writeFinalReport() throws IOException {
		for (final String reportField : REPORT_FIELDS) {
			results.putIfAbsent(reportField, StepResult.fail("Step not executed."));
		}

		final StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"name\": \"").append(REPORT_NAME).append("\",\n");
		sb.append("  \"generatedAt\": \"").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("\",\n");
		sb.append("  \"evidenceDirectory\": \"").append(jsonEscape(evidenceDirectory.toString())).append("\",\n");
		sb.append("  \"results\": [\n");

		for (int i = 0; i < REPORT_FIELDS.size(); i++) {
			final String reportField = REPORT_FIELDS.get(i);
			final StepResult result = results.get(reportField);
			sb.append("    {\n");
			sb.append("      \"field\": \"").append(jsonEscape(reportField)).append("\",\n");
			sb.append("      \"status\": \"").append(result.passed ? "PASS" : "FAIL").append("\",\n");
			sb.append("      \"details\": \"").append(jsonEscape(result.details)).append("\",\n");
			sb.append("      \"screenshot\": \"").append(jsonEscape(result.screenshot)).append("\",\n");
			sb.append("      \"url\": \"").append(jsonEscape(result.url)).append("\"\n");
			sb.append("    }");
			if (i < REPORT_FIELDS.size() - 1) {
				sb.append(",");
			}
			sb.append("\n");
		}

		sb.append("  ]\n");
		sb.append("}\n");

		final Path reportPath = evidenceDirectory.resolve(REPORT_NAME + "_report.json");
		Files.write(reportPath, sb.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String jsonEscape(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return "";
	}

	private String normalizeAscii(final String text) {
		if (text == null) {
			return "";
		}
		return text.replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
				.replace('Á', 'A').replace('É', 'E').replace('Í', 'I').replace('Ó', 'O').replace('Ú', 'U');
	}

	private String slug(final String text) {
		return normalizeAscii(text).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("_+", "_")
				.replaceAll("^_|_$", "");
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static class StepResult {
		private final String details;
		private final boolean passed;
		private final String screenshot;
		private final String url;

		private StepResult(final boolean passed, final String details, final String screenshot, final String url) {
			this.passed = passed;
			this.details = details == null ? "" : details;
			this.screenshot = screenshot == null ? "" : screenshot;
			this.url = url == null ? "" : url;
		}

		private static StepResult pass() {
			return new StepResult(true, "", "", "");
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details, "", "");
		}

		private StepResult withDetails(final String newDetails) {
			return new StepResult(passed, newDetails, screenshot, url);
		}

		private StepResult withScreenshot(final String newScreenshot) {
			return new StepResult(passed, details, newScreenshot, url);
		}

		private StepResult withUrl(final String newUrl) {
			return new StepResult(passed, details, screenshot, newUrl);
		}
	}
}
