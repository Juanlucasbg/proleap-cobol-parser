package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
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
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio".
 *
 * The test is intentionally URL-agnostic. Provide saleads.url when you want the
 * test to navigate to a specific environment.
 */
public class SaleadsMiNegocioFullTest {

	private static final String DEFAULT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter SCREENSHOT_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set -Dsaleads.e2e.enabled=true to run this environment-dependent test.",
				getConfigValue("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false").equalsIgnoreCase("true"));

		final long timeoutSeconds = Long
				.parseLong(getConfigValue("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", "30"));
		final boolean headless = Boolean.parseBoolean(getConfigValue("saleads.headless", "SALEADS_HEADLESS", "true"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		screenshotDir = Paths.get("target", "saleads-e2e", LocalDateTime.now().format(SCREENSHOT_TS));
		Files.createDirectories(screenshotDir);

		final String url = getConfigValue("saleads.url", "SALEADS_URL", "").trim();
		if (!url.isEmpty()) {
			driver.get(url);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad"));

		printFinalReport();

		if (finalReport.containsValue(Boolean.FALSE)) {
			final StringBuilder message = new StringBuilder("Mi Negocio workflow failed:\n");
			for (final String failure : failures) {
				message.append("- ").append(failure).append('\n');
			}
			Assert.fail(message.toString());
		}
	}

	private void stepLoginWithGoogle() {
		if (!isSidebarVisible()) {
			clickContainingIgnoreCase("google");
			waitForUiToLoad();

			final String googleEmail = getConfigValue("saleads.google.email", "SALEADS_GOOGLE_EMAIL", DEFAULT_EMAIL);
			clickTextIfVisibleAnyWindow(googleEmail, Duration.ofSeconds(20));
			waitForUiToLoad();
		}

		assertTrue("Main application interface was not detected after login.",
				isVisible(By.xpath("//main | //aside | //nav")));
		assertTrue("Left sidebar navigation is not visible after login.", isSidebarVisible());
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		assertTrue("Sidebar navigation should be visible before opening Mi Negocio.", isSidebarVisible());
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertVisibleText("Crear Nuevo Negocio");
		assertTrue("Input 'Nombre del Negocio' is missing.", isVisible(nombreNegocioInputLocator()));
		assertVisibleText("Tienes 2 de 3 negocios");
		assertButtonVisible("Cancelar");
		assertButtonVisible("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final WebElement nameInput = wait.until(ExpectedConditions.visibilityOfElementLocated(nombreNegocioInputLocator()));
		nameInput.click();
		nameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		waitForElementToDisappear(By.xpath("//*[contains(normalize-space(.), " + asXpathLiteral("Crear Nuevo Negocio") + ")]"));
	}

	private void stepOpenAdministrarNegocios() {
		expandMiNegocioIfCollapsed();
		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		captureFullPageScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		final String expectedUserName = getConfigValue("saleads.expected.user.name", "SALEADS_EXPECTED_USER_NAME", "")
				.trim();
		if (!expectedUserName.isEmpty()) {
			assertVisibleText(expectedUserName);
		} else {
			assertTrue("User name was not confidently found in 'Información General'.",
					hasLabeledValue(Set.of("Nombre", "Name", "Usuario")));
		}

		final String expectedEmail = getConfigValue("saleads.google.email", "SALEADS_GOOGLE_EMAIL", DEFAULT_EMAIL);
		final boolean emailVisible = isTextVisible(expectedEmail) || bodyContainsPattern(EMAIL_PATTERN);
		assertTrue("User email is not visible in account information.", emailVisible);

		assertVisibleText("BUSINESS PLAN");
		assertButtonVisible("Cambiar Plan");
	}

	private void stepValidateDetallesDeLaCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertButtonVisible("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertTrue("Business list is not visible in 'Tus Negocios'.", hasBusinessList());
	}

	private void stepValidateLegalLink(final String linkText) {
		final String originalHandle = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final int windowCountBefore = driver.getWindowHandles().size();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		if (driver.getWindowHandles().size() > windowCountBefore) {
			for (final String handle : driver.getWindowHandles()) {
				if (!handle.equals(originalHandle)) {
					driver.switchTo().window(handle);
					waitForUiToLoad();
					break;
				}
			}
		}

		assertVisibleText(linkText);
		final String bodyText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
		assertTrue("Legal content text is too short or missing for '" + linkText + "'.", bodyText != null && bodyText.trim().length() > 120);

		captureScreenshot("05-legal-" + slugify(linkText));
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (!driver.getWindowHandle().equals(originalHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else if (!driver.getCurrentUrl().equals(originalUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void runStep(final String reportKey, final StepAction action) {
		try {
			action.run();
			finalReport.put(reportKey, Boolean.TRUE);
		} catch (final Exception | AssertionError e) {
			finalReport.put(reportKey, Boolean.FALSE);
			failures.add(reportKey + ": " + e.getMessage());
			captureScreenshot("failed-" + slugify(reportKey));
		}
	}

	private void printFinalReport() {
		System.out.println("=== saleads_mi_negocio_full_test ===");
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("--- Captured legal URLs ---");
			for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
				System.out.println(legalUrl.getKey() + ": " + legalUrl.getValue());
			}
		}
		System.out.println("Screenshots folder: " + screenshotDir.toAbsolutePath());
	}

	private void clickByVisibleText(final String text) {
		final String literal = asXpathLiteral(text);
		final By by = By.xpath("(//*[self::button or self::a or @role='button'][contains(normalize-space(.), " + literal + ")]"
				+ " | //*[(self::span or self::div or self::p or self::h1 or self::h2 or self::h3) and contains(normalize-space(.), "
				+ literal + ")]/ancestor::*[self::button or self::a or @role='button'][1])[1]");
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
		clickAndWait(element);
	}

	private void clickContainingIgnoreCase(final String text) {
		final String lower = text.toLowerCase(Locale.ROOT);
		final By by = By.xpath("(//*[self::button or self::a or @role='button']"
				+ "[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), "
				+ asXpathLiteral(lower) + ")])[1]");
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
		clickAndWait(element);
	}

	private boolean clickTextIfVisibleAnyWindow(final String text, final Duration timeout) {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				final By textBy = By.xpath("//*[contains(normalize-space(.), " + asXpathLiteral(text) + ")]");
				final List<WebElement> elements = driver.findElements(textBy);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						clickAndWait(element);
						return true;
					}
				}
			}
			sleep(400);
		}
		return false;
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		sleep(350);
	}

	private void waitForElementToDisappear(final By by) {
		new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.invisibilityOfElementLocated(by));
	}

	private void assertVisibleText(final String text) {
		final By by = By.xpath("//*[contains(normalize-space(.), " + asXpathLiteral(text) + ")]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private boolean isTextVisible(final String text) {
		final By by = By.xpath("//*[contains(normalize-space(.), " + asXpathLiteral(text) + ")]");
		return isVisible(by);
	}

	private boolean isVisible(final By by) {
		final List<WebElement> elements = driver.findElements(by);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void assertButtonVisible(final String text) {
		final By by = By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.), " + asXpathLiteral(text) + ")]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private By nombreNegocioInputLocator() {
		return By.xpath("(//label[contains(normalize-space(.), " + asXpathLiteral("Nombre del Negocio") + ")]/following::input[1]"
				+ " | //input[contains(@placeholder, " + asXpathLiteral("Nombre del Negocio") + ")]"
				+ " | //input[contains(@aria-label, " + asXpathLiteral("Nombre del Negocio") + ")])[1]");
	}

	private void expandMiNegocioIfCollapsed() {
		if (!isTextVisible("Administrar Negocios")) {
			if (!isTextVisible("Mi Negocio")) {
				clickByVisibleText("Negocio");
			}
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}
	}

	private boolean isSidebarVisible() {
		final By sidebarBy = By.xpath("(//aside | //nav)[.//*[contains(normalize-space(.), " + asXpathLiteral("Negocio")
				+ ") or contains(normalize-space(.), " + asXpathLiteral("Mi Negocio") + ")]]");
		return isVisible(sidebarBy);
	}

	private boolean hasBusinessList() {
		final List<WebElement> entries = driver.findElements(By.xpath(
				"//section[.//*[contains(normalize-space(.), " + asXpathLiteral("Tus Negocios") + ")]]//li[normalize-space()!='']"
						+ " | //section[.//*[contains(normalize-space(.), " + asXpathLiteral("Tus Negocios")
						+ ")]]//tr[normalize-space()!='']"
						+ " | //section[.//*[contains(normalize-space(.), " + asXpathLiteral("Tus Negocios")
						+ ")]]//article[normalize-space()!='']"));
		for (final WebElement entry : entries) {
			if (entry.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean hasLabeledValue(final Set<String> labels) {
		for (final String label : labels) {
			final String literal = asXpathLiteral(label);
			final By by = By.xpath("//*[contains(normalize-space(.), " + literal + ")]/ancestor::*[self::div or self::section or self::article][1]"
					+ "//*[self::span or self::div or self::p][normalize-space()!='']");
			final List<WebElement> elements = driver.findElements(by);
			for (final WebElement element : elements) {
				final String value = element.getText().trim();
				if (element.isDisplayed() && !value.equalsIgnoreCase(label) && value.length() > 1) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean bodyContainsPattern(final Pattern pattern) {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		return bodyText != null && pattern.matcher(bodyText).find();
	}

	private void captureScreenshot(final String name) {
		try {
			final byte[] image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			final Path file = screenshotDir.resolve(LocalDateTime.now().format(SCREENSHOT_TS) + "-" + slugify(name) + ".png");
			Files.write(file, image);
		} catch (final Exception e) {
			failures.add("Screenshot capture failed for " + name + ": " + e.getMessage());
		}
	}

	private void captureFullPageScreenshot(final String name) {
		try {
			if (driver instanceof ChromiumDriver) {
				final Map<String, Object> params = new LinkedHashMap<>();
				params.put("captureBeyondViewport", Boolean.TRUE);
				params.put("fromSurface", Boolean.TRUE);
				final Map<String, Object> screenshot = ((ChromiumDriver) driver).executeCdpCommand("Page.captureScreenshot", params);
				final String base64 = (String) screenshot.get("data");
				final byte[] bytes = Base64.getDecoder().decode(base64);
				final Path file = screenshotDir.resolve(LocalDateTime.now().format(SCREENSHOT_TS) + "-" + slugify(name) + ".png");
				Files.write(file, bytes);
			} else {
				captureScreenshot(name);
			}
		} catch (final Exception e) {
			captureScreenshot(name);
		}
	}

	private void assertTrue(final String message, final boolean condition) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String getConfigValue(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private String slugify(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part = String.valueOf(chars[i]);
			if (i > 0) {
				result.append(",");
			}
			if ("'".equals(part)) {
				result.append("\"").append(part).append("\"");
			} else {
				result.append("'").append(part).append("'");
			}
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run();
	}
}
