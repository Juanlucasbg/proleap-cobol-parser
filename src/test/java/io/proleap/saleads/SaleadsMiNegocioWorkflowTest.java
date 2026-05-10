package io.proleap.saleads;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

public class SaleadsMiNegocioWorkflowTest {

	private static final String TEST_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private String termsUrl = "NOT_CAPTURED";
	private String privacyUrl = "NOT_CAPTURED";

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();

		if (configAsBoolean("saleads.headless", "SALEADS_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(configAsInt("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", 30)));

		screenshotDir = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(TS_FORMAT));
		Files.createDirectories(screenshotDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final Map<String, String> failures = new LinkedHashMap<>();

		try {
			openLoginPageWhenConfigured();

			finalReport.put("Login", loginWithGoogle(failures));
			finalReport.put("Mi Negocio menu", openMiNegocioMenu(failures));
			finalReport.put("Agregar Negocio modal", validateAgregarNegocioModal(failures));
			finalReport.put("Administrar Negocios view", openAdministrarNegociosView(failures));
			finalReport.put("Información General", validateInformacionGeneral(failures));
			finalReport.put("Detalles de la Cuenta", validateDetallesDeLaCuenta(failures));
			finalReport.put("Tus Negocios", validateTusNegocios(failures));
			finalReport.put("Términos y Condiciones", validateLegalLink("Términos y Condiciones", "08-terminos", failures, true));
			finalReport.put("Política de Privacidad", validateLegalLink("Política de Privacidad", "09-privacidad", failures, false));
		} finally {
			printFinalReport(failures);
		}

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			if (!Boolean.TRUE.equals(entry.getValue())) {
				failedSteps.add(entry.getKey());
			}
		}

		Assert.assertTrue("Workflow failed in steps: " + failedSteps, failedSteps.isEmpty());
	}

	private void openLoginPageWhenConfigured() {
		final boolean assumePageIsAlreadyOpen = configAsBoolean("saleads.assume.login.page.open", "SALEADS_ASSUME_LOGIN_PAGE_OPEN", false);
		if (assumePageIsAlreadyOpen) {
			return;
		}

		final String loginUrl = configAsString("saleads.login.url", "SALEADS_LOGIN_URL");
		Assert.assertTrue(
				"Login URL is required when saleads.assume.login.page.open is false. Configure saleads.login.url or SALEADS_LOGIN_URL.",
				loginUrl != null && !loginUrl.isBlank());

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	private boolean loginWithGoogle(final Map<String, String> failures) {
		try {
			clickByVisibleText(
					"Sign in with Google",
					"Iniciar sesión con Google",
					"Ingresar con Google",
					"Continuar con Google",
					"Google");
			waitForUiToLoad();

			handleGoogleAccountPickerIfPresent();
			waitForMainApplicationVisible();
			takeScreenshot("01-dashboard-loaded");
			return true;
		} catch (final Exception ex) {
			failures.put("Login", ex.getMessage());
			takeScreenshot("01-login-failed");
			return false;
		}
	}

	private boolean openMiNegocioMenu(final Map<String, String> failures) {
		try {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();

			assertTextVisible("Agregar Negocio");
			assertTextVisible("Administrar Negocios");

			takeScreenshot("02-mi-negocio-expanded");
			return true;
		} catch (final Exception ex) {
			failures.put("Mi Negocio menu", ex.getMessage());
			takeScreenshot("02-mi-negocio-failed");
			return false;
		}
	}

	private boolean validateAgregarNegocioModal(final Map<String, String> failures) {
		try {
			clickByVisibleText("Agregar Negocio");
			waitForUiToLoad();

			assertTextVisible("Crear Nuevo Negocio");
			assertBusinessNameFieldVisible();
			assertTextVisible("Tienes 2 de 3 negocios");
			assertTextVisible("Cancelar");
			assertTextVisible("Crear Negocio");

			takeScreenshot("03-agregar-negocio-modal");

			final WebElement businessNameInput = findBusinessNameInput();
			businessNameInput.click();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatizacion");
			waitForUiToLoad();

			clickByVisibleText("Cancelar");
			waitForUiToLoad();
			return true;
		} catch (final Exception ex) {
			failures.put("Agregar Negocio modal", ex.getMessage());
			takeScreenshot("03-agregar-negocio-failed");
			return false;
		}
	}

	private boolean openAdministrarNegociosView(final Map<String, String> failures) {
		try {
			ensureMiNegocioExpanded();
			clickByVisibleText("Administrar Negocios");
			waitForUiToLoad();

			assertTextVisible("Información General");
			assertTextVisible("Detalles de la Cuenta");
			assertTextVisible("Tus Negocios");
			assertTextVisible("Sección Legal");

			takeScreenshot("04-administrar-negocios");
			return true;
		} catch (final Exception ex) {
			failures.put("Administrar Negocios view", ex.getMessage());
			takeScreenshot("04-administrar-negocios-failed");
			return false;
		}
	}

	private boolean validateInformacionGeneral(final Map<String, String> failures) {
		try {
			assertAnyVisible(By.xpath("//section//*[contains(normalize-space(),'@')]"),
					By.xpath("//*[contains(normalize-space(),'@')]"));
			assertAnyVisible(By.xpath("//section//*[contains(normalize-space(),'BUSINESS PLAN')]"),
					By.xpath("//*[contains(normalize-space(),'BUSINESS PLAN')]"));
			assertTextVisible("Cambiar Plan");
			return true;
		} catch (final Exception ex) {
			failures.put("Información General", ex.getMessage());
			return false;
		}
	}

	private boolean validateDetallesDeLaCuenta(final Map<String, String> failures) {
		try {
			assertTextVisible("Cuenta creada");
			assertTextVisible("Estado activo");
			assertTextVisible("Idioma seleccionado");
			return true;
		} catch (final Exception ex) {
			failures.put("Detalles de la Cuenta", ex.getMessage());
			return false;
		}
	}

	private boolean validateTusNegocios(final Map<String, String> failures) {
		try {
			assertTextVisible("Tus Negocios");
			assertTextVisible("Agregar Negocio");
			assertTextVisible("Tienes 2 de 3 negocios");
			return true;
		} catch (final Exception ex) {
			failures.put("Tus Negocios", ex.getMessage());
			return false;
		}
	}

	private boolean validateLegalLink(final String linkText, final String screenshotPrefix, final Map<String, String> failures,
			final boolean saveAsTerms) {
		final String failureKey = saveAsTerms ? "Términos y Condiciones" : "Política de Privacidad";

		try {
			final String appWindow = driver.getWindowHandle();
			final Set<String> beforeHandles = driver.getWindowHandles();

			clickByVisibleText(linkText);
			waitForUiToLoad();

			final String resolvedHandle = resolveLegalPageHandle(beforeHandles, appWindow);
			driver.switchTo().window(resolvedHandle);
			waitForUiToLoad();

			assertAnyVisible(byText(linkText), By.xpath("//h1"), By.xpath("//h2"));
			assertAnyVisible(By.xpath("//p[string-length(normalize-space()) > 30]"), By.xpath("//main//*"));

			takeScreenshot(screenshotPrefix);
			final String finalUrl = driver.getCurrentUrl();
			if (saveAsTerms) {
				termsUrl = finalUrl;
			} else {
				privacyUrl = finalUrl;
			}

			if (!resolvedHandle.equals(appWindow)) {
				driver.close();
				driver.switchTo().window(appWindow);
			} else {
				driver.navigate().back();
			}

			waitForUiToLoad();
			assertTextVisible("Sección Legal");
			return true;
		} catch (final Exception ex) {
			failures.put(failureKey, ex.getMessage());
			takeScreenshot(screenshotPrefix + "-failed");
			return false;
		}
	}

	private void handleGoogleAccountPickerIfPresent() {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> previousHandles = driver.getWindowHandles();

		try {
			new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(d -> d.getWindowHandles().size() > previousHandles.size() || !d.findElements(byText(TEST_ACCOUNT_EMAIL)).isEmpty());
		} catch (final TimeoutException ignored) {
			return;
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!handle.equals(originalWindow)) {
				driver.switchTo().window(handle);
				break;
			}
		}

		try {
			clickByVisibleText(TEST_ACCOUNT_EMAIL);
			waitForUiToLoad();
		} catch (final RuntimeException ignored) {
			// Account picker can be skipped if session is already authenticated.
		} finally {
			if (driver.getWindowHandles().contains(originalWindow)) {
				driver.switchTo().window(originalWindow);
			}
		}
	}

	private void waitForMainApplicationVisible() {
		wait.until(d -> isDisplayed(By.cssSelector("aside")) || isDisplayed(byText("Negocio")) || isDisplayed(byText("Mi Negocio")));
		wait.until(d -> isDisplayed(By.cssSelector("aside")) || isDisplayed(By.xpath("//*[contains(@class,'sidebar')]")));
	}

	private void ensureMiNegocioExpanded() {
		if (!isDisplayed(byText("Administrar Negocios"))) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}
	}

	private WebElement findBusinessNameInput() {
		final List<By> locators = List.of(
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"));

		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}

		throw new RuntimeException("Input field 'Nombre del Negocio' was not found.");
	}

	private void assertBusinessNameFieldVisible() {
		final WebElement field = findBusinessNameInput();
		Assert.assertTrue("Input field 'Nombre del Negocio' is not visible.", field.isDisplayed());
	}

	private void clickByVisibleText(final String... labels) {
		for (final String label : labels) {
			final List<WebElement> candidates = driver.findElements(byText(label));
			for (final WebElement element : candidates) {
				if (!element.isDisplayed()) {
					continue;
				}

				wait.until(ExpectedConditions.elementToBeClickable(element));
				((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
				element.click();
				waitForUiToLoad();
				return;
			}
		}

		throw new RuntimeException("Unable to find clickable element by visible text: " + String.join(", ", labels));
	}

	private void assertTextVisible(final String text) {
		final boolean visible = new WebDriverWait(driver, Duration.ofSeconds(15))
				.until(d -> !d.findElements(byText(text)).isEmpty() && isDisplayed(byText(text)));
		Assert.assertTrue("Expected visible text not found: " + text, visible);
	}

	private void assertAnyVisible(final By... locators) {
		final boolean found = new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> {
			for (final By locator : locators) {
				if (isDisplayed(locator)) {
					return true;
				}
			}
			return false;
		});
		Assert.assertTrue("Expected one of the locators to be visible.", found);
	}

	private String resolveLegalPageHandle(final Set<String> beforeHandles, final String appWindow) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.getWindowHandles().size() > beforeHandles.size());
		} catch (final TimeoutException ignored) {
			return appWindow;
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!beforeHandles.contains(handle)) {
				return handle;
			}
		}

		return appWindow;
	}

	private void waitForUiToLoad() {
		new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
	}

	private By byText(final String text) {
		final String literal = asXPathLiteral(text);
		final String xpath = "//*[self::button or self::a or self::span or self::div or self::p or self::h1 or self::h2 or self::h3 or self::li or self::label]"
				+ "[normalize-space() = " + literal + " or contains(normalize-space(), " + literal + ")]";
		return By.xpath(xpath);
	}

	private String asXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char c = chars[i];
			if (c == '\'') {
				sb.append("\"'\"");
			} else {
				sb.append("'").append(c).append("'");
			}

			if (i < chars.length - 1) {
				sb.append(",");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	private boolean isDisplayed(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void takeScreenshot(final String checkpointName) {
		try {
			final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final String fileName = checkpointName + "-" + LocalDateTime.now().format(TS_FORMAT) + ".png";
			Files.copy(source.toPath(), screenshotDir.resolve(fileName));
		} catch (final Exception ignored) {
			// No-op: evidence capture should never block test execution.
		}
	}

	private String configAsString(final String systemProperty, final String envVar) {
		final String fromProperty = System.getProperty(systemProperty);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}

		final String fromEnv = System.getenv(envVar);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		return null;
	}

	private int configAsInt(final String systemProperty, final String envVar, final int defaultValue) {
		final String raw = configAsString(systemProperty, envVar);
		if (raw == null) {
			return defaultValue;
		}

		return Integer.parseInt(raw);
	}

	private boolean configAsBoolean(final String systemProperty, final String envVar, final boolean defaultValue) {
		final String raw = configAsString(systemProperty, envVar);
		if (raw == null) {
			return defaultValue;
		}

		return Boolean.parseBoolean(raw);
	}

	private void printFinalReport(final Map<String, String> failures) {
		System.out.println("========================================");
		System.out.println("SaleADS Mi Negocio Workflow Final Report");
		System.out.println("========================================");

		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			final String status = Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL";
			System.out.println(entry.getKey() + ": " + status);

			if (failures.containsKey(entry.getKey())) {
				System.out.println("  reason: " + failures.get(entry.getKey()));
			}
		}

		System.out.println("Términos y Condiciones URL: " + termsUrl);
		System.out.println("Política de Privacidad URL: " + privacyUrl);
		System.out.println("Screenshots: " + screenshotDir.toAbsolutePath());
		System.out.println("========================================");
	}
}
