package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
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
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end coverage for the SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * Environment-agnostic by design:
 * </p>
 * <ul>
 * <li>Does not hardcode domains.</li>
 * <li>Uses visible text selectors wherever possible.</li>
 * <li>Stores evidence screenshots under target/saleads-evidence/.</li>
 * </ul>
 *
 * <p>
 * Optional runtime properties:
 * </p>
 * <ul>
 * <li>-Dsaleads.loginUrl=https://&lt;environment-login-page&gt;</li>
 * <li>-Dsaleads.headless=true|false (default true)</li>
 * <li>-Dsaleads.timeoutSeconds=30</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, String> finalReport = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appHandle;
	private String termsFinalUrl;
	private String privacyFinalUrl;

	@Before
	public void setUp() throws IOException {
		evidenceDir = Paths.get("target", "saleads-evidence", "saleads_mi_negocio_full_test");
		Files.createDirectories(evidenceDir);

		final String loginUrl = firstNonBlank(System.getProperty("saleads.loginUrl"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue("Provide -Dsaleads.loginUrl (or SALEADS_LOGIN_URL) for the current environment.", loginUrl != null);

		final boolean headless = Boolean.parseBoolean(
				firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "true"));
		final int timeoutSeconds = Integer
				.parseInt(firstNonBlank(System.getProperty("saleads.timeoutSeconds"), System.getenv("SALEADS_TIMEOUT_SECONDS"), "30"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-notifications");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		driver.get(loginUrl);
		waitForUiToLoad();
		appHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep("Login", this::loginWithGoogleAndValidateDashboard);
		runStep("Mi Negocio menu", this::openMiNegocioAndValidateSubmenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegociosAndValidateSections);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", this::validateTerminosYCondiciones);
		runStep("Política de Privacidad", this::validatePoliticaDePrivacidad);

		printFinalReport();

		if (!failures.isEmpty()) {
			fail("saleads_mi_negocio_full_test failed validations:\n - " + String.join("\n - ", failures));
		}
	}

	private void loginWithGoogleAndValidateDashboard() {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Login with Google",
				"Ingresar con Google");

		clickByVisibleTextIfPresent("juanlucasbarbiergarzon@gmail.com");

		assertVisibleByText("Negocio", "Mi Negocio");
		assertVisibleElement(By.xpath("//aside | //nav"));
		takeScreenshot("01-dashboard-loaded.png", false);
	}

	private void openMiNegocioAndValidateSubmenu() {
		clickByVisibleTextIfPresent("Negocio");
		clickByVisibleText("Mi Negocio");

		assertVisibleByText("Agregar Negocio");
		assertVisibleByText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded.png", false);
	}

	private void validateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");

		assertVisibleByText("Crear Nuevo Negocio");
		assertVisibleByText("Nombre del Negocio");
		assertVisibleByText("Tienes 2 de 3 negocios");
		assertVisibleByText("Cancelar");
		assertVisibleByText("Crear Negocio");

		final By nombreInput = By.xpath(
				"//input[@name='businessName' or @placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or ancestor::*[.//*[normalize-space()='Nombre del Negocio']]]");
		final WebElement input = waitForVisible(nombreInput);
		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatización");

		takeScreenshot("03-agregar-negocio-modal.png", false);
		clickByVisibleText("Cancelar");
	}

	private void openAdministrarNegociosAndValidateSections() {
		expandMiNegocioIfNeeded();
		clickByVisibleText("Administrar Negocios");

		assertVisibleByText("Información General");
		assertVisibleByText("Detalles de la Cuenta");
		assertVisibleByText("Tus Negocios");
		assertVisibleByText("Sección Legal");
		takeScreenshot("04-administrar-negocios-full.png", true);
	}

	private void validateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		final String sectionText = visibleText(section);

		assertTrue("Expected user email to be visible in Información General.", EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("Expected user name to be visible in Información General.", containsLikelyUserName(sectionText));
		assertTrue("Expected BUSINESS PLAN text.", sectionText.contains("BUSINESS PLAN"));
		assertTrue("Expected Cambiar Plan button.", hasVisibleText(section, "Cambiar Plan"));
	}

	private void validateDetallesDeLaCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		final String sectionText = visibleText(section);

		assertTrue("Expected Cuenta creada text.", sectionText.contains("Cuenta creada"));
		assertTrue("Expected Estado activo text.", sectionText.contains("Estado activo"));
		assertTrue("Expected Idioma seleccionado text.", sectionText.contains("Idioma seleccionado"));
	}

	private void validateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final String sectionText = visibleText(section);

		assertTrue("Expected business list content.", sectionText.length() > 40);
		assertTrue("Expected Agregar Negocio button.", hasVisibleText(section, "Agregar Negocio"));
		assertTrue("Expected 'Tienes 2 de 3 negocios' text.", sectionText.contains("Tienes 2 de 3 negocios"));
	}

	private void validateTerminosYCondiciones() {
		termsFinalUrl = validateLegalPage("Términos y Condiciones", "Términos y Condiciones", "05-terminos-y-condiciones.png");
	}

	private void validatePoliticaDePrivacidad() {
		privacyFinalUrl = validateLegalPage("Política de Privacidad", "Política de Privacidad", "06-politica-de-privacidad.png");
	}

	private String validateLegalPage(final String linkText, final String headingText, final String screenshotName) {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickByVisibleText(linkText);
		final String openedHandle = switchToNewWindowIfOpened(beforeHandles);
		waitForUiToLoad();

		assertVisibleByText(headingText);
		final String bodyText = visibleText(driver.findElement(By.tagName("body")));
		assertTrue("Expected legal content text for " + headingText + ".", bodyText.length() > 120);
		takeScreenshot(screenshotName, false);

		final String finalUrl = driver.getCurrentUrl();

		if (openedHandle != null) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		if (!driver.getWindowHandle().equals(appHandle)) {
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void runStep(final String name, final ThrowingRunnable step) {
		try {
			step.run();
			finalReport.put(name, "PASS");
		} catch (final Throwable throwable) {
			finalReport.put(name, "FAIL");
			failures.add(name + ": " + throwable.getMessage());
		}
	}

	private void printFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("\n=== saleads_mi_negocio_full_test FINAL REPORT ===\n");
		finalReport.forEach((key, value) -> builder.append(key).append(": ").append(value).append("\n"));
		if (termsFinalUrl != null) {
			builder.append("Términos y Condiciones URL: ").append(termsFinalUrl).append("\n");
		}
		if (privacyFinalUrl != null) {
			builder.append("Política de Privacidad URL: ").append(privacyFinalUrl).append("\n");
		}
		builder.append("Evidence directory: ").append(evidenceDir).append("\n");
		System.out.println(builder);
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
	}

	private void expandMiNegocioIfNeeded() {
		if (!isVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"))) {
			clickByVisibleTextIfPresent("Negocio");
			clickByVisibleText("Mi Negocio");
		}
	}

	private String switchToNewWindowIfOpened(final Set<String> beforeHandles) {
		try {
			wait.until(driver -> driver.getWindowHandles().size() > beforeHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!beforeHandles.contains(handle)) {
					driver.switchTo().window(handle);
					return handle;
				}
			}
		} catch (final TimeoutException timeoutException) {
			// Link opened in the same tab, which is also a valid navigation pattern.
		}
		return null;
	}

	private void clickByVisibleText(final String... texts) {
		for (final String text : texts) {
			final List<By> candidates = byVisibleTextCandidates(text);
			for (final By locator : candidates) {
				if (isVisible(locator)) {
					wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
					waitForUiToLoad();
					return;
				}
			}
		}

		throw new NoSuchElementException("No visible clickable element found for texts: " + String.join(", ", texts));
	}

	private void clickByVisibleTextIfPresent(final String text) {
		for (final By locator : byVisibleTextCandidates(text)) {
			if (isVisible(locator)) {
				wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
				waitForUiToLoad();
				return;
			}
		}
	}

	private List<By> byVisibleTextCandidates(final String text) {
		final String literal = xpathLiteral(text);
		final String containsLiteral = xpathLiteral(text.toLowerCase());
		final List<By> locators = new ArrayList<>();

		locators.add(By.xpath("//button[normalize-space()=" + literal + "] | //a[normalize-space()=" + literal + "]"
				+ " | //*[@role='button' and normalize-space()=" + literal + "]"));
		locators.add(By.xpath("//*[normalize-space()=" + literal + "]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"));
		locators.add(By.xpath("//button[contains(translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), "
				+ containsLiteral + ")] | //a[contains(translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), "
				+ containsLiteral + ")] | //*[@role='button' and contains(translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), "
				+ containsLiteral + ")]"));
		return locators;
	}

	private void assertVisibleByText(final String... texts) {
		for (final String text : texts) {
			final String literal = xpathLiteral(text);
			final By locator = By.xpath("//*[normalize-space()=" + literal + "]");
			if (isVisible(locator)) {
				return;
			}
		}

		throw new NoSuchElementException("None of the expected visible texts were found: " + String.join(", ", texts));
	}

	private boolean isVisible(final By locator) {
		try {
			wait.withTimeout(Duration.ofSeconds(2));
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		} finally {
			final int timeoutSeconds = Integer.parseInt(
					firstNonBlank(System.getProperty("saleads.timeoutSeconds"), System.getenv("SALEADS_TIMEOUT_SECONDS"), "30"));
			wait.withTimeout(Duration.ofSeconds(timeoutSeconds));
		}
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void assertVisibleElement(final By locator) {
		waitForVisible(locator);
	}

	private WebElement findSectionByHeading(final String headingText) {
		final String literal = xpathLiteral(headingText);
		final By locator = By.xpath("//*[self::section or self::div][.//*[normalize-space()=" + literal + "]][1]");
		return waitForVisible(locator);
	}

	private boolean containsLikelyUserName(final String text) {
		final String[] lines = text.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.contains("@")) {
				continue;
			}
			if (line.equalsIgnoreCase("BUSINESS PLAN") || line.equalsIgnoreCase("Cambiar Plan")
					|| line.equalsIgnoreCase("Información General")) {
				continue;
			}
			if (line.matches("^[\\p{L}][\\p{L} .'-]{2,60}$")) {
				return true;
			}
		}
		return false;
	}

	private boolean hasVisibleText(final WebElement scope, final String expectedText) {
		final String literal = xpathLiteral(expectedText);
		final List<WebElement> matches = scope.findElements(By.xpath(".//*[normalize-space()=" + literal + "]"));
		for (final WebElement match : matches) {
			if (match.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private String visibleText(final WebElement element) {
		final String text = element.getText();
		return text == null ? "" : text.trim();
	}

	private void takeScreenshot(final String fileName, final boolean fullPage) {
		final Path screenshotPath = evidenceDir.resolve(fileName);
		try {
			if (fullPage && driver instanceof ChromiumDriver) {
				final Map<String, Object> params = new HashMap<>();
				params.put("captureBeyondViewport", true);
				params.put("fromSurface", true);

				final Object data = ((ChromiumDriver) driver).executeCdpCommand("Page.captureScreenshot", params).get("data");
				if (data instanceof String) {
					Files.write(screenshotPath, Base64.getDecoder().decode((String) data));
					return;
				}
			}

			final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(screenshotPath, bytes);
		} catch (final IOException ioException) {
			throw new RuntimeException("Failed to write screenshot " + screenshotPath + ": " + ioException.getMessage(), ioException);
		}
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final Matcher matcher = Pattern.compile("([^'\"]+)|'|\"").matcher(text);
		boolean first = true;
		while (matcher.find()) {
			if (!first) {
				builder.append(",");
			}
			final String token = matcher.group();
			if ("'".equals(token)) {
				builder.append("\"'\"");
			} else if ("\"".equals(token)) {
				builder.append("'\"'");
			} else {
				builder.append("'").append(token).append("'");
			}
			first = false;
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
