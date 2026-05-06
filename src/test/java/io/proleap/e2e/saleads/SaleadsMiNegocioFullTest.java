package io.proleap.e2e.saleads;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
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

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+");

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MENU = "Mi Negocio menu";
	private static final String REPORT_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private final Map<String, String> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDir = Files.createDirectories(Path.of("target", "saleads-evidence"));

		report.put(REPORT_LOGIN, "FAIL");
		report.put(REPORT_MENU, "FAIL");
		report.put(REPORT_MODAL, "FAIL");
		report.put(REPORT_ADMIN, "FAIL");
		report.put(REPORT_INFO_GENERAL, "FAIL");
		report.put(REPORT_DETALLES, "FAIL");
		report.put(REPORT_TUS_NEGOCIOS, "FAIL");
		report.put(REPORT_TERMINOS, "FAIL");
		report.put(REPORT_PRIVACIDAD, "FAIL");
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		runStep(REPORT_LOGIN, this::loginWithGoogleAndValidateMainUi);
		runStep(REPORT_MENU, this::openMiNegocioMenuAndValidate);
		runStep(REPORT_MODAL, this::validateAgregarNegocioModal);
		runStep(REPORT_ADMIN, this::openAdministrarNegociosAndValidate);
		runStep(REPORT_INFO_GENERAL, this::validateInformacionGeneral);
		runStep(REPORT_DETALLES, this::validateDetallesDeLaCuenta);
		runStep(REPORT_TUS_NEGOCIOS, this::validateTusNegocios);
		runStep(REPORT_TERMINOS, () -> validateLegalDocument("Términos y Condiciones", "Terminos y Condiciones",
				"Términos y Condiciones", "terminos-y-condiciones"));
		runStep(REPORT_PRIVACIDAD, () -> validateLegalDocument("Política de Privacidad", "Politica de Privacidad",
				"Política de Privacidad", "politica-de-privacidad"));

		printReport();

		if (!failures.isEmpty()) {
			Assert.fail("Workflow validation failed:\n" + String.join("\n", failures));
		}
	}

	private void loginWithGoogleAndValidateMainUi() throws Exception {
		ensureOnLoginPage();

		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = driver.getWindowHandles();

		final WebElement loginButton = firstVisible(Duration.ofSeconds(20),
				By.xpath("//button[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'google')]"),
				By.xpath("//a[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'google')]"),
				By.xpath("//*[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'sign in')]"));

		clickAndWait(loginButton);
		final String popupWindow = waitForNewWindow(windowsBefore, Duration.ofSeconds(12));

		if (popupWindow != null) {
			driver.switchTo().window(popupWindow);
			waitForUiToSettle();
			selectGoogleAccountIfVisible();
			driver.switchTo().window(appWindow);
		} else {
			selectGoogleAccountIfVisible();
		}

		waitUntilVisible(By.xpath("//*[contains(normalize-space(),'Negocio')]"), Duration.ofSeconds(60));
		Assert.assertTrue("Main application UI should be visible",
				isVisible(By.xpath("//aside|//nav"), Duration.ofSeconds(20)));
		Assert.assertTrue("Left sidebar navigation should be visible",
				isVisible(By.xpath("//*[contains(normalize-space(),'Negocio')]"), Duration.ofSeconds(20)));

		captureScreenshot("01-dashboard-loaded", false);
	}

	private void openMiNegocioMenuAndValidate() throws Exception {
		Assert.assertTrue("Left sidebar should be visible before opening menu",
				isVisible(By.xpath("//aside|//nav"), Duration.ofSeconds(20)));
		Assert.assertTrue("Negocio section label should be visible",
				isVisible(By.xpath("//*[normalize-space()='Negocio' or contains(normalize-space(),'Negocio')]"),
						Duration.ofSeconds(20)));

		clickAndWait(firstVisible(Duration.ofSeconds(20),
				By.xpath("//*[normalize-space()='Mi Negocio']"),
				By.xpath("//*[contains(normalize-space(),'Mi Negocio')]")));

		Assert.assertTrue("Mi Negocio submenu should expand",
				isVisible(By.xpath("//*[normalize-space()='Agregar Negocio']"), Duration.ofSeconds(15)));
		Assert.assertTrue("'Agregar Negocio' should be visible",
				isVisible(By.xpath("//*[normalize-space()='Agregar Negocio']"), Duration.ofSeconds(15)));
		Assert.assertTrue("'Administrar Negocios' should be visible",
				isVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"), Duration.ofSeconds(15)));

		captureScreenshot("02-mi-negocio-menu-expanded", false);
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickAndWait(By.xpath("//*[normalize-space()='Agregar Negocio']"));
		waitUntilVisible(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']"), Duration.ofSeconds(20));

		Assert.assertTrue("Modal title should be visible",
				isVisible(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']"), Duration.ofSeconds(10)));
		Assert.assertTrue("'Nombre del Negocio' input should exist",
				isVisible(By.xpath("//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio']"
						+ "| //label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
						Duration.ofSeconds(10)));
		Assert.assertTrue("'Tienes 2 de 3 negocios' text should be visible",
				isVisible(By.xpath("//*[contains(normalize-space(),'Tienes 2 de 3 negocios')]"), Duration.ofSeconds(10)));
		Assert.assertTrue("'Cancelar' button should be present",
				isVisible(By.xpath("//button[normalize-space()='Cancelar']"), Duration.ofSeconds(10)));
		Assert.assertTrue("'Crear Negocio' button should be present",
				isVisible(By.xpath("//button[normalize-space()='Crear Negocio']"), Duration.ofSeconds(10)));

		captureScreenshot("03-agregar-negocio-modal", false);

		final WebElement nombreInput = firstVisible(Duration.ofSeconds(10),
				By.xpath("//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio']"),
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"));
		nombreInput.click();
		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatizacion");
		clickAndWait(By.xpath("//button[normalize-space()='Cancelar']"));
	}

	private void openAdministrarNegociosAndValidate() throws Exception {
		if (!isVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"), Duration.ofSeconds(3))) {
			clickAndWait(firstVisible(Duration.ofSeconds(10), By.xpath("//*[normalize-space()='Mi Negocio']"),
					By.xpath("//*[contains(normalize-space(),'Mi Negocio')]")));
		}

		clickAndWait(By.xpath("//*[normalize-space()='Administrar Negocios']"));

		Assert.assertTrue("'Información General' section should exist",
				isVisible(By.xpath("//*[normalize-space()='Información General']"), Duration.ofSeconds(20)));
		Assert.assertTrue("'Detalles de la Cuenta' section should exist",
				isVisible(By.xpath("//*[normalize-space()='Detalles de la Cuenta']"), Duration.ofSeconds(20)));
		Assert.assertTrue("'Tus Negocios' section should exist",
				isVisible(By.xpath("//*[normalize-space()='Tus Negocios']"), Duration.ofSeconds(20)));
		Assert.assertTrue("'Sección Legal' section should exist",
				isVisible(By.xpath("//*[normalize-space()='Sección Legal']"), Duration.ofSeconds(20)));

		captureScreenshot("04-administrar-negocios-view", true);
	}

	private void validateInformacionGeneral() {
		final String sectionText = sectionText("Información General");
		Assert.assertTrue("User email should be visible in Información General",
				EMAIL_PATTERN.matcher(sectionText).find());
		Assert.assertTrue("User name should be visible in Información General", containsLikelyUserName(sectionText));
		Assert.assertTrue("'BUSINESS PLAN' text should be visible",
				isVisible(By.xpath("//*[contains(normalize-space(),'BUSINESS PLAN')]"), Duration.ofSeconds(10)));
		Assert.assertTrue("'Cambiar Plan' button should be visible",
				isVisible(By.xpath("//button[normalize-space()='Cambiar Plan']"
						+ "| //*[normalize-space()='Cambiar Plan' and (self::a or self::button)]"), Duration.ofSeconds(10)));
	}

	private void validateDetallesDeLaCuenta() {
		Assert.assertTrue("'Cuenta creada' should be visible",
				isVisible(By.xpath("//*[contains(normalize-space(),'Cuenta creada')]"), Duration.ofSeconds(10)));
		Assert.assertTrue("'Estado activo' should be visible",
				isVisible(By.xpath("//*[contains(normalize-space(),'Estado activo')]"), Duration.ofSeconds(10)));
		Assert.assertTrue("'Idioma seleccionado' should be visible",
				isVisible(By.xpath("//*[contains(normalize-space(),'Idioma seleccionado')]"), Duration.ofSeconds(10)));
	}

	private void validateTusNegocios() {
		final String sectionText = sectionText("Tus Negocios");
		Assert.assertTrue("Business list should be visible", hasLikelyBusinessEntries(sectionText));
		Assert.assertTrue("'Agregar Negocio' button should exist in Tus Negocios",
				isVisible(By.xpath("//button[normalize-space()='Agregar Negocio']"
						+ "| //*[normalize-space()='Agregar Negocio' and (self::a or self::button)]"), Duration.ofSeconds(10)));
		Assert.assertTrue("'Tienes 2 de 3 negocios' should be visible",
				isVisible(By.xpath("//*[contains(normalize-space(),'Tienes 2 de 3 negocios')]"), Duration.ofSeconds(10)));
	}

	private void validateLegalDocument(final String primaryLinkText, final String fallbackLinkText,
			final String expectedHeading, final String screenshotBaseName) throws Exception {
		final String applicationWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = driver.getWindowHandles();

		final WebElement link = firstVisible(Duration.ofSeconds(20),
				By.xpath("//*[normalize-space()=" + xpathLiteral(primaryLinkText) + "]"),
				By.xpath("//*[normalize-space()=" + xpathLiteral(fallbackLinkText) + "]"));
		clickAndWait(link);

		final String legalWindow = waitForNewWindow(windowsBefore, Duration.ofSeconds(8));
		final boolean openedNewTab = legalWindow != null;

		if (openedNewTab) {
			driver.switchTo().window(legalWindow);
			waitForUiToSettle();
		}

		final String headingWithoutDiacritics = removeDiacritics(expectedHeading);
		final By headingLocator = By.xpath(
				"//*[contains(normalize-space(), " + xpathLiteral(expectedHeading) + ")"
						+ " or contains(normalize-space(), " + xpathLiteral(headingWithoutDiacritics) + ")]");

		waitUntilVisible(headingLocator, Duration.ofSeconds(20));
		Assert.assertTrue("Legal heading should be visible for " + expectedHeading,
				isVisible(headingLocator, Duration.ofSeconds(10)));
		Assert.assertTrue("Legal content text should be visible for " + expectedHeading,
				hasSubstantialPageText(80));

		captureScreenshot("05-" + screenshotBaseName, true);
		legalUrls.put(expectedHeading, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(applicationWindow);
			waitForUiToSettle();
		} else {
			driver.navigate().back();
			waitForUiToSettle();
		}
	}

	private String sectionText(final String headingText) {
		final WebElement heading = waitUntilVisible(By.xpath("//*[normalize-space()=" + xpathLiteral(headingText) + "]"),
				Duration.ofSeconds(20));
		final WebElement section = heading
				.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		return section.getText();
	}

	private void ensureOnLoginPage() {
		String configuredLoginUrl = firstNonBlank(
				System.getProperty("saleads.login.url"),
				System.getenv("SALEADS_LOGIN_URL"),
				System.getProperty("saleads.base.url"),
				System.getenv("SALEADS_BASE_URL"),
				System.getenv("BASE_URL"));

		final String currentUrl = driver.getCurrentUrl();
		if (currentUrl == null || currentUrl.startsWith("data:") || currentUrl.startsWith("about:blank")) {
			Assert.assertNotNull(
					"Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) to run against the current environment login page.",
					configuredLoginUrl);
			driver.get(configuredLoginUrl);
		}

		waitForUiToSettle();
	}

	private void selectGoogleAccountIfVisible() {
		if (isVisible(By.xpath("//*[contains(normalize-space(),'juanlucasbarbiergarzon@gmail.com')]"),
				Duration.ofSeconds(6))) {
			clickAndWait(By.xpath("//*[contains(normalize-space(),'juanlucasbarbiergarzon@gmail.com')]"));
		}
	}

	private WebDriver createDriver() {
		final String browser = firstNonBlank(
				System.getProperty("saleads.browser"),
				System.getenv("SALEADS_BROWSER"),
				"chrome").toLowerCase();
		final boolean headless = Boolean.parseBoolean(
				firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "true"));

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			final WebDriver firefox = new FirefoxDriver(firefoxOptions);
			firefox.manage().window().maximize();
			return firefox;
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			chromeOptions.addArguments("--disable-gpu");
			chromeOptions.addArguments("--window-size=1920,1080");
			final WebDriver chrome = new ChromeDriver(chromeOptions);
			chrome.manage().window().maximize();
			return chrome;
		}
	}

	private void clickAndWait(final By locator) {
		clickAndWait(waitUntilVisible(locator, DEFAULT_TIMEOUT));
	}

	private void clickAndWait(final WebElement element) {
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToSettle();
	}

	private void waitForUiToSettle() {
		final ExpectedCondition<Boolean> readyStateComplete = webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState"));
		new WebDriverWait(driver, Duration.ofSeconds(20)).until(readyStateComplete);
		sleep(900L);
	}

	private WebElement waitUntilVisible(final By locator, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			waitUntilVisible(locator, timeout);
			return true;
		} catch (final TimeoutException | NoSuchElementException ignored) {
			return false;
		}
	}

	private WebElement firstVisible(final Duration timeout, final By... locators) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();

		while (System.currentTimeMillis() < deadline) {
			for (final By locator : locators) {
				for (final WebElement element : driver.findElements(locator)) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			sleep(250L);
		}

		throw new NoSuchElementException("No visible element found for provided locators.");
	}

	private String waitForNewWindow(final Set<String> existingWindows, final Duration timeout) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String handle : driver.getWindowHandles()) {
				if (!existingWindows.contains(handle)) {
					return handle;
				}
			}
			sleep(250L);
		}
		return null;
	}

	private boolean hasSubstantialPageText(final int minLength) {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		return bodyText != null && bodyText.trim().length() >= minLength;
	}

	private boolean containsLikelyUserName(final String sectionText) {
		if (sectionText == null) {
			return false;
		}

		final String[] lines = sectionText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.length() < 3) {
				continue;
			}
			if (line.equalsIgnoreCase("Información General")
					|| line.equalsIgnoreCase("BUSINESS PLAN")
					|| line.equalsIgnoreCase("Cambiar Plan")
					|| line.contains("@")) {
				continue;
			}
			if (line.matches(".*[A-Za-z].*")) {
				return true;
			}
		}

		return false;
	}

	private boolean hasLikelyBusinessEntries(final String sectionText) {
		if (sectionText == null || sectionText.trim().isEmpty()) {
			return false;
		}

		final String[] lines = sectionText.split("\\R");
		int meaningfulLines = 0;
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()
					|| line.equalsIgnoreCase("Tus Negocios")
					|| line.equalsIgnoreCase("Agregar Negocio")
					|| line.contains("Tienes 2 de 3 negocios")) {
				continue;
			}
			meaningfulLines++;
		}
		return meaningfulLines > 0;
	}

	private void captureScreenshot(final String checkpointName, final boolean fullPage) throws IOException {
		final String safeName = checkpointName.replaceAll("[^A-Za-z0-9_.-]", "_");
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
		final Path screenshotPath = evidenceDir.resolve(timestamp + "_" + safeName + ".png");

		Dimension originalSize = null;
		if (fullPage) {
			originalSize = driver.manage().window().getSize();
			try {
				final long width = ((Number) ((JavascriptExecutor) driver).executeScript(
						"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, 1920);"))
								.longValue();
				final long height = ((Number) ((JavascriptExecutor) driver).executeScript(
						"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, 1080);"))
								.longValue();
				driver.manage().window().setSize(new Dimension((int) width, (int) height));
				waitForUiToSettle();
			} catch (final Exception ignored) {
				// If resizing is blocked by browser/driver policy, capture regular viewport screenshot.
			}
		}

		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), screenshotPath);

		if (fullPage && originalSize != null) {
			driver.manage().window().setSize(originalSize);
			waitForUiToSettle();
		}
	}

	private void runStep(final String label, final ThrowingRunnable action) {
		try {
			action.run();
			report.put(label, "PASS");
		} catch (final Throwable error) {
			report.put(label, "FAIL");
			failures.add(label + ": " + error.getMessage());
			try {
				captureScreenshot("FAILED-" + label, false);
			} catch (final Exception ignored) {
				// Preserve original failure details.
			}
		}
	}

	private void printReport() {
		System.out.println("==== SaleADS Mi Negocio Workflow Report ====");
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}

		if (!legalUrls.isEmpty()) {
			System.out.println("---- Legal URLs ----");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(entry.getKey() + " URL: " + entry.getValue());
			}
		}

		System.out.println("Screenshots directory: " + evidenceDir.toAbsolutePath());
	}

	private static String xpathLiteral(final String value) {
		if (value.contains("'") && value.contains("\"")) {
			final StringBuilder literal = new StringBuilder("concat(");
			for (int i = 0; i < value.length(); i++) {
				final String c = String.valueOf(value.charAt(i));
				if ("'".equals(c)) {
					literal.append("\"").append(c).append("\"");
				} else {
					literal.append("'").append(c).append("'");
				}
				if (i < value.length() - 1) {
					literal.append(",");
				}
			}
			literal.append(")");
			return literal.toString();
		}

		if (value.contains("'")) {
			return "\"" + value + "\"";
		}

		return "'" + value + "'";
	}

	private static String firstNonBlank(final String... values) {
		if (values == null) {
			return null;
		}
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private static String removeDiacritics(final String value) {
		if (value == null) {
			return null;
		}
		final String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
		return normalized.replaceAll("\\p{M}", "");
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
