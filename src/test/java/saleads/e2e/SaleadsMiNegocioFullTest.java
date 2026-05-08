package saleads.e2e;

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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
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

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getProperty("saleads.login.url"));
		Assert.assertNotNull(
				"Set SALEADS_LOGIN_URL or -Dsaleads.login.url to the current environment login page.",
				loginUrl);

		evidenceDir = Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(firstNonBlank(System.getenv("SALEADS_HEADLESS"), "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.get(loginUrl);
		waitForUiLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final Map<String, String> report = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		runStep("Login", report, failures, () -> {
			clickFirstMatchingText("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
					"Continuar con Google", "Google");
			waitForUiLoad();
			selectGoogleAccountIfPresent(GOOGLE_ACCOUNT_EMAIL);

			waitForVisibleAny(By.cssSelector("aside"), By.cssSelector("nav"));
			waitForVisibleTextAny("Negocio", "Business", "Dashboard");
			captureScreenshot("01-dashboard-loaded.png");
		});

		runStep("Mi Negocio menu", report, failures, () -> {
			clickIfPresent("Negocio");
			clickFirstMatchingText("Mi Negocio");
			waitForUiLoad();

			waitForVisibleTextAny("Agregar Negocio");
			waitForVisibleTextAny("Administrar Negocios");
			captureScreenshot("02-mi-negocio-menu-expanded.png");
		});

		runStep("Agregar Negocio modal", report, failures, () -> {
			clickFirstMatchingText("Agregar Negocio");
			waitForVisibleTextAny("Crear Nuevo Negocio");

			waitForVisibleTextAny("Nombre del Negocio");
			waitForVisibleTextAny("Tienes 2 de 3 negocios");
			waitForVisibleTextAny("Cancelar");
			waitForVisibleTextAny("Crear Negocio");
			captureScreenshot("03-agregar-negocio-modal.png");

			final WebElement businessNameInput = waitForVisibleAny(
					By.xpath("//input[@placeholder='Nombre del Negocio']"),
					By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"),
					By.xpath("//input[contains(@name, 'nombre')]"));
			businessNameInput.click();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatizacion");
			clickFirstMatchingText("Cancelar");
			wait.until(ExpectedConditions.invisibilityOfElementLocated(
					By.xpath("//*[contains(normalize-space(.), " + toXpathLiteral("Crear Nuevo Negocio") + ")]")));
			waitForUiLoad();
		});

		runStep("Administrar Negocios view", report, failures, () -> {
			expandMiNegocioIfCollapsed();
			clickFirstMatchingText("Administrar Negocios");
			waitForUiLoad();

			waitForVisibleTextAny("Información General", "Informacion General");
			waitForVisibleTextAny("Detalles de la Cuenta");
			waitForVisibleTextAny("Tus Negocios");
			waitForVisibleTextAny("Sección Legal", "Seccion Legal");
			captureScreenshot("04-administrar-negocios-page.png");
		});

		runStep("Información General", report, failures, () -> {
			final WebElement section = sectionByHeading("Información General", "Informacion General");
			final String text = section.getText();
			Assert.assertTrue("User name should be visible in Informacion General section.", containsLikelyName(text));
			Assert.assertTrue("User email should be visible in Informacion General section.",
					EMAIL_PATTERN.matcher(text).find());
			Assert.assertTrue("BUSINESS PLAN text should be visible.", text.contains("BUSINESS PLAN"));
			assertClickableText("Cambiar Plan");
		});

		runStep("Detalles de la Cuenta", report, failures, () -> {
			final WebElement section = sectionByHeading("Detalles de la Cuenta");
			final String text = section.getText();
			Assert.assertTrue("'Cuenta creada' should be visible.", containsIgnoreCase(text, "Cuenta creada"));
			Assert.assertTrue("'Estado activo' should be visible.", containsIgnoreCase(text, "Estado activo"));
			Assert.assertTrue("'Idioma seleccionado' should be visible.", containsIgnoreCase(text, "Idioma seleccionado"));
		});

		runStep("Tus Negocios", report, failures, () -> {
			final WebElement section = sectionByHeading("Tus Negocios");
			final String text = section.getText();
			Assert.assertFalse("Business list section should not be empty.", text.trim().isEmpty());
			findClickableText("Agregar Negocio");
			Assert.assertTrue("'Tienes 2 de 3 negocios' should be visible.", text.contains("Tienes 2 de 3 negocios"));
		});

		runStep("Términos y Condiciones", report, failures, () -> {
			final String url = openLegalContentAndReturn(
					Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
					Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
					"08-terminos-y-condiciones.png");
			legalUrls.put("Términos y Condiciones", url);
		});

		runStep("Política de Privacidad", report, failures, () -> {
			final String url = openLegalContentAndReturn(
					Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
					Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
					"09-politica-de-privacidad.png");
			legalUrls.put("Política de Privacidad", url);
		});

		printFinalReport(report, legalUrls);
		Assert.assertTrue("One or more validation steps failed:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private void runStep(final String fieldName, final Map<String, String> report, final List<String> failures,
			final StepAction action) {
		try {
			action.run();
			report.put(fieldName, "PASS");
		} catch (final Throwable throwable) {
			report.put(fieldName, "FAIL");
			final String reason = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
			failures.add(fieldName + " -> " + reason);
			try {
				captureScreenshot("fail-" + sanitizeFileName(fieldName) + ".png");
			} catch (final IOException ignored) {
				// Ignore screenshot capture errors to avoid hiding primary failures.
			}
		}
	}

	private String openLegalContentAndReturn(final List<String> clickableTexts, final List<String> headingTexts,
			final String screenshotName) throws IOException {
		final String appHandle = driver.getWindowHandle();
		final String appUrlBefore = driver.getCurrentUrl();
		final int handleCountBefore = driver.getWindowHandles().size();

		clickFirstMatchingText(clickableTexts.toArray(new String[0]));
		waitForUiLoad();

		boolean openedNewTab = false;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(d -> d.getWindowHandles().size() > handleCountBefore || !d.getCurrentUrl().equals(appUrlBefore));
		} catch (final TimeoutException timeoutException) {
			throw new AssertionError("No navigation detected after legal link click.");
		}

		if (driver.getWindowHandles().size() > handleCountBefore) {
			for (final String handle : driver.getWindowHandles()) {
				if (!handle.equals(appHandle)) {
					driver.switchTo().window(handle);
					openedNewTab = true;
					break;
				}
			}
		}

		waitForUiLoad();
		waitForVisibleTextAny(headingTexts.toArray(new String[0]));

		final String legalText = driver.findElement(By.tagName("body")).getText();
		Assert.assertFalse("Legal content should be visible.", legalText.trim().isEmpty());

		captureScreenshot(screenshotName);
		final String legalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiLoad();
		waitForVisibleTextAny("Sección Legal", "Seccion Legal");
		return legalUrl;
	}

	private void expandMiNegocioIfCollapsed() {
		if (!isVisible(SHORT_TIMEOUT, textLocator("Administrar Negocios"))) {
			clickIfPresent("Negocio");
			clickIfPresent("Mi Negocio");
			waitForUiLoad();
		}
	}

	private void selectGoogleAccountIfPresent(final String accountEmail) {
		final By accountLocator = textLocator(accountEmail);
		if (isVisible(Duration.ofSeconds(15), accountLocator)) {
			wait.until(ExpectedConditions.elementToBeClickable(accountLocator)).click();
			waitForUiLoad();
		}
	}

	private void clickIfPresent(final String text) {
		final By locator = textLocator(text);
		if (isVisible(SHORT_TIMEOUT, locator)) {
			wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
			waitForUiLoad();
		}
	}

	private void clickFirstMatchingText(final String... texts) {
		for (final String text : texts) {
			final By locator = textLocator(text);
			if (isVisible(SHORT_TIMEOUT, locator)) {
				wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
				waitForUiLoad();
				return;
			}
		}
		throw new NoSuchElementException("Could not find clickable element with texts: " + Arrays.toString(texts));
	}

	private WebElement findClickableText(final String text) {
		final By locator = textLocator(text);
		return wait.until(ExpectedConditions.elementToBeClickable(locator));
	}

	private void assertClickableText(final String text) {
		Assert.assertNotNull("Expected clickable text not found: " + text, findClickableText(text));
	}

	private WebElement sectionByHeading(final String... headings) {
		for (final String heading : headings) {
			final String headingLiteral = toXpathLiteral(heading);
			final By locator = By.xpath(
					"//*[contains(normalize-space(.), " + headingLiteral + ")]/ancestor::*[self::section or self::div][1]");
			if (isVisible(SHORT_TIMEOUT, locator)) {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			}
		}
		throw new NoSuchElementException("Could not find section with headings: " + Arrays.toString(headings));
	}

	private WebElement waitForVisibleAny(final By... locators) {
		for (final By locator : locators) {
			if (isVisible(SHORT_TIMEOUT, locator)) {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			}
		}
		throw new NoSuchElementException("None of the expected locators became visible.");
	}

	private void waitForVisibleTextAny(final String... texts) {
		for (final String text : texts) {
			final By locator = textLocator(text);
			if (isVisible(SHORT_TIMEOUT, locator)) {
				wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return;
			}
		}
		throw new NoSuchElementException("Could not find visible text among: " + Arrays.toString(texts));
	}

	private boolean isVisible(final Duration timeout, final By locator) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private By textLocator(final String text) {
		final String literal = toXpathLiteral(text);
		return By.xpath(
				"//*[self::button or self::a or @role='button' or self::div or self::span][contains(normalize-space(.), "
						+ literal + ")]");
	}

	private String toXpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				builder.append(",");
			}
			if (chars[i] == '\'') {
				builder.append("\"'\"");
			} else if (chars[i] == '"') {
				builder.append("'\"'");
			} else {
				builder.append("'").append(chars[i]).append("'");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private void waitForUiLoad() {
		wait.until(driverInstance -> "complete".equals(((JavascriptExecutor) driverInstance).executeScript("return document.readyState")));
		try {
			Thread.sleep(400);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), evidenceDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
	}

	private void printFinalReport(final Map<String, String> report, final Map<String, String> legalUrls) {
		System.out.println("=== SaleADS Mi Negocio Full Test Report ===");
		final List<String> expectedFields = Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad");

		for (final String field : expectedFields) {
			System.out.println(field + ": " + report.getOrDefault(field, "FAIL"));
		}

		if (!legalUrls.isEmpty()) {
			System.out.println("--- Legal URLs ---");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(entry.getKey() + ": " + entry.getValue());
			}
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private boolean containsLikelyName(final String sectionText) {
		final String[] lines = sectionText.split("\\R");
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(trimmed).find()) {
				continue;
			}
			if (containsIgnoreCase(trimmed, "business plan") || containsIgnoreCase(trimmed, "cambiar plan")) {
				continue;
			}
			return true;
		}
		return false;
	}

	private boolean containsIgnoreCase(final String text, final String expected) {
		return text.toLowerCase().contains(expected.toLowerCase());
	}

	private String sanitizeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-");
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
