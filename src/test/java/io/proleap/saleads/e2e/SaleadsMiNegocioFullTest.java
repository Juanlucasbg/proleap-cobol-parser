package io.proleap.saleads.e2e;

import java.io.File;
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
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end test for the SaleADS Mi Negocio workflow.
 *
 * Required runtime configuration:
 * - SALEADS_LOGIN_URL: login page URL for the current environment.
 *
 * Optional runtime configuration:
 * - SELENIUM_REMOTE_URL: run against a remote Selenium grid.
 * - SALEADS_HEADLESS: "true" (default) or "false".
 * - SALEADS_GOOGLE_EMAIL: account to click in Google selector
 *   (default: juanlucasbarbiergarzon@gmail.com).
 */
public class SaleadsMiNegocioFullTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);

	private final Map<String, Boolean> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private String googleAccountEmail;

	@Before
	public void setUp() throws IOException {
		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		screenshotDirectory = Path.of("target", "saleads-evidence", runId);
		Files.createDirectories(screenshotDirectory);

		googleAccountEmail = getEnv("SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com");
		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiLoad();
		} else {
			// The workflow expects starting on the login page. If no URL is given, skip.
			Assume.assumeTrue(
					"SALEADS_LOGIN_URL is required to open the SaleADS login page for the target environment.",
					false);
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		executeStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final StringBuilder report = new StringBuilder();
		report.append("\nFinal report (saleads_mi_negocio_full_test)\n");
		report.append("Evidence directory: ").append(screenshotDirectory.toAbsolutePath()).append('\n');
		report.append('\n');

		boolean allPassed = true;
		for (final String field : requiredReportFields()) {
			final boolean passed = stepStatus.getOrDefault(field, false);
			allPassed = allPassed && passed;
			report.append("- ").append(field).append(": ").append(passed ? "PASS" : "FAIL");
			if (stepErrors.containsKey(field)) {
				report.append(" (").append(stepErrors.get(field)).append(")");
			}
			report.append('\n');
		}

		if (!legalUrls.isEmpty()) {
			report.append('\n');
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				report.append("Final URL [").append(entry.getKey()).append("]: ").append(entry.getValue()).append('\n');
			}
		}

		System.out.println(report);
		Assert.assertTrue(report.toString(), allPassed);
	}

	private void stepLoginWithGoogle() throws Exception {
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
		clickByVisibleText(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Inicia sesión con Google",
				"Continuar con Google",
				"Google");

		waitForUiLoad();
		switchToNewestWindowIfOpened(beforeHandles);

		if (isTextPresent(googleAccountEmail, Duration.ofSeconds(10))) {
			clickByVisibleText(googleAccountEmail);
			waitForUiLoad();
		}

		switchToNonGoogleWindow();
		waitForAnyText(Duration.ofSeconds(60), "Negocio", "Mi Negocio");
		assertSidebarVisible();
		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		if (!isTextPresent("Mi Negocio", Duration.ofSeconds(3))) {
			clickByVisibleText("Negocio");
			waitForUiLoad();
		}

		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		requireVisibleText("Agregar Negocio");
		requireVisibleText("Administrar Negocios");
		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForUiLoad();

		requireVisibleText("Crear Nuevo Negocio");
		requireVisibleText("Nombre del Negocio");
		requireVisibleText("Tienes 2 de 3 negocios");
		requireVisibleText("Cancelar");
		requireVisibleText("Crear Negocio");
		takeScreenshot("03_agregar_negocio_modal");

		final WebElement input = findVisibleElement(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='businessName' or contains(@aria-label,'Nombre del Negocio')]"));
		if (input != null) {
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
		}

		clickByVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextPresent("Administrar Negocios", Duration.ofSeconds(3))) {
			clickByVisibleText("Mi Negocio");
			waitForUiLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		requireVisibleText("Información General");
		requireVisibleText("Detalles de la Cuenta");
		requireVisibleText("Tus Negocios");
		requireVisibleText("Sección Legal");
		takeScreenshot("04_administrar_negocios_view");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		requireVisibleText("BUSINESS PLAN");
		requireVisibleText("Cambiar Plan");
		assertUserNameAndEmailVisible();
		takeScreenshot("05_informacion_general");
	}

	private void stepValidateDetallesCuenta() throws Exception {
		assertPageContainsIgnoreCase("Cuenta creada");
		assertPageContainsIgnoreCase("Estado activo");
		assertPageContainsIgnoreCase("Idioma seleccionado");
		takeScreenshot("06_detalles_cuenta");
	}

	private void stepValidateTusNegocios() throws Exception {
		requireVisibleText("Tus Negocios");
		requireVisibleText("Agregar Negocio");
		requireVisibleText("Tienes 2 de 3 negocios");

		final List<WebElement> businessCandidates = driver.findElements(By.xpath(
				"//*[contains(translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚ', 'abcdefghijklmnopqrstuvwxyzáéíóú'), 'tus negocios')]/ancestor::*[1]//*[self::li or self::tr or contains(@class,'business') or contains(@class,'negocio') or contains(@class,'card')]"));
		boolean visibleBusinessList = false;
		for (final WebElement candidate : businessCandidates) {
			if (candidate.isDisplayed()) {
				visibleBusinessList = true;
				break;
			}
		}
		Assert.assertTrue("Business list is not visible in 'Tus Negocios' section.", visibleBusinessList);
		takeScreenshot("07_tus_negocios");
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08_terminos_condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		validateLegalLink("Política de Privacidad", "Política de Privacidad", "09_politica_privacidad");
	}

	private void validateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws Exception {
		final String appHandle = driver.getWindowHandle();
		final String appUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);
		waitForUiLoad();

		final String activeHandle = wait.until(d -> {
			final Set<String> currentHandles = d.getWindowHandles();
			if (currentHandles.size() > handlesBeforeClick.size()) {
				for (final String handle : currentHandles) {
					if (!handlesBeforeClick.contains(handle)) {
						return handle;
					}
				}
			}
			if (!appUrl.equals(d.getCurrentUrl())) {
				return appHandle;
			}
			return null;
		});

		if (!driver.getWindowHandle().equals(activeHandle)) {
			driver.switchTo().window(activeHandle);
		}

		waitForAnyText(Duration.ofSeconds(30), expectedHeading);
		assertPageContainsIgnoreCase(expectedHeading);

		final String legalText = driver.findElement(By.tagName("body")).getText().trim();
		Assert.assertTrue("Legal content appears empty for: " + linkText, legalText.length() > 100);

		final String finalUrl = driver.getCurrentUrl();
		legalUrls.put(linkText, finalUrl);
		takeScreenshot(screenshotName);

		if (!activeHandle.equals(appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiLoad();
	}

	private void executeStep(final String name, final CheckedStep step) {
		try {
			step.run();
			stepStatus.put(name, true);
		} catch (final Throwable throwable) {
			stepStatus.put(name, false);
			stepErrors.put(name, sanitizeError(throwable.getMessage()));
			try {
				takeScreenshot("failure_" + slug(name));
			} catch (final Exception ignored) {
				// no-op
			}
		}
	}

	private WebDriver createDriver() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		final boolean headless = Boolean.parseBoolean(getEnv("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = System.getenv("SELENIUM_REMOTE_URL");
		if (remoteUrl != null && !remoteUrl.isBlank()) {
			try {
				return new RemoteWebDriver(java.net.URI.create(remoteUrl).toURL(), options);
			} catch (final Exception exception) {
				throw new IllegalArgumentException("Invalid SELENIUM_REMOTE_URL: " + remoteUrl, exception);
			}
		}
		return new ChromeDriver(options);
	}

	private void clickByVisibleText(final String... texts) throws Exception {
		Exception lastException = null;
		for (final String text : texts) {
			try {
				final By exact = By.xpath("//*[self::button or self::a or self::span or self::div][normalize-space()="
						+ xpathLiteral(text) + "]");
				final By contains = By.xpath("//*[self::button or self::a or self::span or self::div][contains(normalize-space(),"
						+ xpathLiteral(text) + ")]");

				final WebElement element = findVisibleElement(exact) != null ? findVisibleElement(exact)
						: findVisibleElement(contains);
				if (element != null) {
					try {
						element.click();
					} catch (final Exception clickException) {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
					}
					waitForUiLoad();
					return;
				}
			} catch (final Exception e) {
				lastException = e;
			}
		}

		if (lastException != null) {
			throw lastException;
		}
		throw new NoSuchElementException("Could not click any element with visible text: " + Arrays.toString(texts));
	}

	private WebElement findVisibleElement(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		return null;
	}

	private void waitForUiLoad() throws InterruptedException {
		wait.until((ExpectedCondition<Boolean>) d -> {
			final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(state) || "interactive".equals(state);
		});
		Thread.sleep(700L);
	}

	private void requireVisibleText(final String text) {
		Assert.assertTrue("Expected visible text not found: " + text, isTextPresent(text, DEFAULT_WAIT));
	}

	private boolean isTextPresent(final String text, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(d -> {
				final List<WebElement> exactMatches = d.findElements(By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]"));
				for (final WebElement element : exactMatches) {
					if (element.isDisplayed()) {
						return true;
					}
				}

				final List<WebElement> partialMatches = d
						.findElements(By.xpath("//*[contains(normalize-space()," + xpathLiteral(text) + ")]"));
				for (final WebElement element : partialMatches) {
					if (element.isDisplayed()) {
						return true;
					}
				}
				return false;
			});
			return true;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void waitForAnyText(final Duration timeout, final String... texts) {
		final List<String> expectedTexts = Arrays.asList(texts);
		new WebDriverWait(driver, timeout).until(d -> {
			for (final String text : expectedTexts) {
				if (isTextPresent(text, Duration.ofSeconds(1))) {
					return true;
				}
			}
			return false;
		});
	}

	private void assertSidebarVisible() {
		final boolean sidebarByStructure = findVisibleElement(By.xpath("//aside")) != null
				|| findVisibleElement(By.xpath("//nav")) != null;
		final boolean sidebarByText = isTextPresent("Negocio", Duration.ofSeconds(5))
				|| isTextPresent("Mi Negocio", Duration.ofSeconds(5));

		Assert.assertTrue("Main application interface/sidebar is not visible.", sidebarByStructure || sidebarByText);
	}

	private void assertUserNameAndEmailVisible() {
		final List<WebElement> elementsWithAt = driver.findElements(By.xpath("//*[contains(normalize-space(), '@')]"));
		boolean emailFound = false;
		boolean probableNameFound = false;

		for (final WebElement element : elementsWithAt) {
			if (!element.isDisplayed()) {
				continue;
			}

			final String text = normalize(element.getText());
			if (!EMAIL_PATTERN.matcher(text).find()) {
				continue;
			}

			emailFound = true;
			try {
				final WebElement parent = element.findElement(By.xpath(".."));
				final String parentText = normalize(parent.getText());
				for (final String line : parentText.split("\\R")) {
					final String trimmed = normalize(line);
					if (!trimmed.isEmpty() && !trimmed.contains("@") && trimmed.length() >= 3) {
						probableNameFound = true;
						break;
					}
				}
			} catch (final Exception ignored) {
				// no-op
			}
		}

		Assert.assertTrue("User email is not visible.", emailFound);
		Assert.assertTrue("User name is not visible near the user email.", probableNameFound);
	}

	private void assertPageContainsIgnoreCase(final String text) {
		final String bodyText = normalize(driver.findElement(By.tagName("body")).getText()).toLowerCase(Locale.ROOT);
		final String expected = normalize(text).toLowerCase(Locale.ROOT);
		Assert.assertTrue("Expected text not found in page body: " + text, bodyText.contains(expected));
	}

	private void switchToNewestWindowIfOpened(final Set<String> previousHandles) {
		try {
			wait.until(d -> d.getWindowHandles().size() >= previousHandles.size());
			final Set<String> currentHandles = driver.getWindowHandles();
			if (currentHandles.size() > previousHandles.size()) {
				for (final String handle : currentHandles) {
					if (!previousHandles.contains(handle)) {
						driver.switchTo().window(handle);
						return;
					}
				}
			}
		} catch (final Exception ignored) {
			// no-op
		}
	}

	private void switchToNonGoogleWindow() {
		final String currentUrl = safeCurrentUrl();
		if (!currentUrl.contains("google.")) {
			return;
		}

		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (!safeCurrentUrl().contains("google.")) {
				return;
			}
		}
	}

	private String safeCurrentUrl() {
		try {
			final String url = driver.getCurrentUrl();
			return url == null ? "" : url.toLowerCase(Locale.ROOT);
		} catch (final Exception ignored) {
			return "";
		}
	}

	private void takeScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String filename = name + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")) + ".png";
		final Path destination = screenshotDirectory.resolve(filename);
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private static String getEnv(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return value;
	}

	private static String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final String[] parts = text.split("'");
		final List<String> tokens = new ArrayList<>();
		for (int i = 0; i < parts.length; i++) {
			if (!parts[i].isEmpty()) {
				tokens.add("'" + parts[i] + "'");
			}
			if (i < parts.length - 1) {
				tokens.add("\"'\"");
			}
		}
		return "concat(" + String.join(",", tokens) + ")";
	}

	private static String normalize(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace('\u00A0', ' ').trim();
	}

	private static String sanitizeError(final String message) {
		if (message == null || message.isBlank()) {
			return "Unexpected error";
		}
		return message.replace('\n', ' ').replace('\r', ' ');
	}

	private static String slug(final String value) {
		return value.toLowerCase(Locale.ROOT)
				.replace(" ", "_")
				.replace("á", "a")
				.replace("é", "e")
				.replace("í", "i")
				.replace("ó", "o")
				.replace("ú", "u");
	}

	private static List<String> requiredReportFields() {
		return Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad");
	}

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}
}
