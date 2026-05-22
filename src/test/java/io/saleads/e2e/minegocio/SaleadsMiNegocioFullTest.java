package io.saleads.e2e.minegocio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * End-to-end test for SaleADS "Mi Negocio" module workflow.
 *
 * <p>
 * This test is disabled by default to keep the parser test suite stable.
 * Enable it with:
 * <ul>
 * <li>-Dsaleads.e2e.enabled=true</li>
 * <li>-Dsaleads.login.url=https://&lt;environment-login-url&gt;</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor javascriptExecutor;
	private Path screenshotDir;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = getBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false);
		Assume.assumeTrue(
				"Skipping SaleADS E2E workflow. Set -Dsaleads.e2e.enabled=true to execute this test.",
				enabled);

		final String loginUrl = getConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Missing login URL. Provide -Dsaleads.login.url=<environment login URL>.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = getBooleanConfig("saleads.e2e.headless", "SALEADS_E2E_HEADLESS", true);
		final long timeoutSeconds = getLongConfig("saleads.e2e.timeout.seconds", "SALEADS_E2E_TIMEOUT_SECONDS", 30L);
		final String screenshotPath = getConfigOrDefault("saleads.e2e.screenshot.dir", "SALEADS_E2E_SCREENSHOT_DIR",
				"target/screenshots/saleads-mi-negocio");

		screenshotDir = Paths.get(screenshotPath).toAbsolutePath();
		Files.createDirectories(screenshotDir);

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		javascriptExecutor = (JavascriptExecutor) driver;

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() throws IOException {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		final Map<String, String> legalUrlEvidence = new LinkedHashMap<>();

		runStep(report, "Login", () -> stepLoginWithGoogle());
		runStep(report, "Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep(report, "Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep(report, "Administrar Negocios view", this::stepOpenAdministrarNegociosView);
		runStep(report, "Información General", this::stepValidateInformacionGeneral);
		runStep(report, "Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep(report, "Tus Negocios", this::stepValidateTusNegocios);
		runStep(report, "Términos y Condiciones",
				() -> legalUrlEvidence.put("Términos y Condiciones",
						stepValidateLegalPage("Términos y Condiciones", "Términos y Condiciones",
								"checkpoint-08-terminos")));
		runStep(report, "Política de Privacidad",
				() -> legalUrlEvidence.put("Política de Privacidad",
						stepValidateLegalPage("Política de Privacidad", "Política de Privacidad",
								"checkpoint-09-politica-privacidad")));

		printFinalReport(report, legalUrlEvidence);
		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		Assert.assertTrue("Some validations failed: " + failedSteps, failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiToLoad();

		clickIfVisible("juanlucasbarbiergarzon@gmail.com");
		waitForUiToLoad();

		waitForAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Panel");
		assertLeftSidebarVisible();
		captureScreenshot("checkpoint-01-dashboard");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickIfVisible("Negocio");
		waitForUiToLoad();

		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("checkpoint-02-menu-mi-negocio-expandido");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertVisibleText("Crear Nuevo Negocio");
		assertInputForLabelExists("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final WebElement field = findInputForLabel("Nombre del Negocio");
		field.click();
		field.sendKeys("Negocio Prueba Automatización");
		field.sendKeys(Keys.TAB);

		captureScreenshot("checkpoint-03-modal-crear-negocio");
		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegociosView() throws IOException {
		if (!isVisibleTextPresent("Administrar Negocios")) {
			clickIfVisible("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		captureScreenshot("checkpoint-04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("Información General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String pageText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Expected an email to be visible in the account information.",
				EMAIL_PATTERN.matcher(pageText).find());
		Assert.assertTrue("Expected a likely user name in Información General section.",
				hasLikelyUserNameInSection("Información General"));
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		Assert.assertTrue("Expected business list content under 'Tus Negocios' section.",
				hasSectionContent("Tus Negocios"));
	}

	private String stepValidateLegalPage(final String linkText, final String headingText, final String screenshotLabel)
			throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		final String newHandle = waitForNewWindowHandle(handlesBeforeClick);
		final boolean switchedToNewTab = newHandle != null;
		if (switchedToNewTab) {
			driver.switchTo().window(newHandle);
			waitForUiToLoad();
		}

		assertVisibleText(headingText);
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Expected legal content text to be visible for " + headingText + ".",
				bodyText != null && bodyText.trim().length() > 120);

		captureScreenshot(screenshotLabel);
		final String finalUrl = driver.getCurrentUrl();

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();
		return finalUrl;
	}

	private void runStep(final Map<String, Boolean> report, final String reportKey, final StepRunner step) {
		try {
			step.run();
			report.put(reportKey, true);
		} catch (final Throwable throwable) {
			report.put(reportKey, false);
			System.err.println("[FAIL] " + reportKey + " -> " + throwable.getMessage());
		}
	}

	private void printFinalReport(final Map<String, Boolean> report, final Map<String, String> legalUrls) {
		System.out.println();
		System.out.println("===== SaleADS Mi Negocio - Final Report =====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			final String status = entry.getValue() ? "PASS" : "FAIL";
			System.out.println(String.format(Locale.ROOT, "- %s: %s", entry.getKey(), status));
		}
		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			System.out.println(String.format(Locale.ROOT, "  URL %s: %s", entry.getKey(), entry.getValue()));
		}
		System.out.println("Screenshots directory: " + screenshotDir);
		System.out.println("=============================================");
		System.out.println();
	}

	private void assertLeftSidebarVisible() {
		final List<By> sidebarCandidates = List.of(
				By.cssSelector("aside"),
				By.cssSelector("nav"),
				By.xpath("//div[contains(@class,'sidebar')]"),
				By.xpath("//*[contains(@class,'side') and contains(@class,'bar')]"));

		boolean visible = false;
		for (final By candidate : sidebarCandidates) {
			for (final WebElement element : driver.findElements(candidate)) {
				if (element.isDisplayed()) {
					visible = true;
					break;
				}
			}
			if (visible) {
				break;
			}
		}
		Assert.assertTrue("Expected left sidebar navigation to be visible after login.", visible);
	}

	private void assertVisibleText(final String expectedText) {
		waitForText(expectedText);
	}

	private void assertInputForLabelExists(final String labelText) {
		final WebElement input = findInputForLabel(labelText);
		Assert.assertNotNull("Expected input for label '" + labelText + "' to exist.", input);
	}

	private boolean hasSectionContent(final String headingText) {
		final WebElement heading = waitForText(headingText);
		final WebElement section = heading.findElement(By.xpath("ancestor::*[self::section or self::div][1]"));
		final String[] lines = section.getText().split("\\R");
		int nonEmptyLines = 0;
		for (final String rawLine : lines) {
			if (!rawLine.trim().isEmpty()) {
				nonEmptyLines++;
			}
		}
		return nonEmptyLines >= 3;
	}

	private boolean hasLikelyUserNameInSection(final String sectionTitle) {
		final WebElement heading = waitForText(sectionTitle);
		final WebElement section = heading.findElement(By.xpath("ancestor::*[self::section or self::div][1]"));

		final List<String> knownLabels = List.of(sectionTitle, "BUSINESS PLAN", "Cambiar Plan", "Cuenta creada",
				"Estado activo", "Idioma seleccionado", "Detalles de la Cuenta", "Tus Negocios", "Sección Legal");
		final String sectionText = section.getText();
		final String[] lines = sectionText.split("\\R");

		for (final String lineRaw : lines) {
			final String line = lineRaw.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (containsLabel(line, knownLabels)) {
				continue;
			}

			final String alphaNumeric = line.replaceAll("[^\\p{L} ]", "").trim();
			if (alphaNumeric.length() >= 3 && alphaNumeric.contains(" ")) {
				return true;
			}
		}
		return false;
	}

	private boolean containsLabel(final String line, final List<String> labels) {
		final String normalizedLine = normalizeForComparison(line);
		for (final String label : labels) {
			if (normalizedLine.equals(normalizeForComparison(label))) {
				return true;
			}
		}
		return false;
	}

	private String normalizeForComparison(final String text) {
		final String normalized = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return normalized.toLowerCase(Locale.ROOT).trim();
	}

	private WebElement waitForText(final String text) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[normalize-space()="
				+ toXPathLiteral(text) + "]")));
	}

	private void waitForAnyVisibleText(final String... texts) {
		wait.until(driver -> {
			for (final String text : texts) {
				if (isVisibleTextPresent(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isVisibleTextPresent(final String text) {
		final By locator = By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + "]");
		for (final WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void clickByVisibleText(final String... candidates) {
		Throwable lastError = null;
		for (final String candidate : candidates) {
			try {
				final List<WebElement> visibleElements = wait.until(visibleElementsByText(candidate));
				for (final WebElement element : visibleElements) {
					if (element.isDisplayed()) {
						clickElement(element);
						return;
					}
				}
			} catch (final Throwable throwable) {
				lastError = throwable;
			}
		}
		throw new AssertionError("Could not click any visible element for texts: " + String.join(", ", candidates),
				lastError);
	}

	private void clickIfVisible(final String text) {
		final By locator = By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + "]");
		for (final WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				clickElement(element);
				return;
			}
		}
	}

	private ExpectedCondition<List<WebElement>> visibleElementsByText(final String text) {
		final By locator = By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + "]");
		return webDriver -> {
			final List<WebElement> visible = new ArrayList<>();
			for (final WebElement element : webDriver.findElements(locator)) {
				if (element.isDisplayed()) {
					visible.add(element);
				}
			}
			return visible.isEmpty() ? null : visible;
		};
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Throwable clickError) {
			javascriptExecutor.executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private String waitForNewWindowHandle(final Set<String> handlesBeforeClick) {
		try {
			return wait.until(webDriver -> {
				final Set<String> currentHandles = webDriver.getWindowHandles();
				if (currentHandles.size() <= handlesBeforeClick.size()) {
					return null;
				}
				for (final String handle : currentHandles) {
					if (!handlesBeforeClick.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private WebElement findInputForLabel(final String labelText) {
		final List<By> locators = List.of(
				By.xpath("//label[normalize-space()=" + toXPathLiteral(labelText)
						+ "]/following::*[self::input or self::textarea][1]"),
				By.xpath("//input[@placeholder=" + toXPathLiteral(labelText) + "]"),
				By.xpath("//textarea[@placeholder=" + toXPathLiteral(labelText) + "]"),
				By.xpath("//*[normalize-space()=" + toXPathLiteral(labelText)
						+ "]/following::*[self::input or self::textarea][1]"));

		for (final By locator : locators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		throw new AssertionError("Could not find input field for label: " + labelText);
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete".equals(javascriptExecutor.executeScript("return document.readyState")));
		wait.until(driver -> (Boolean) javascriptExecutor.executeScript("return !!document.body"));
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String timestamp = TS_FORMATTER.format(Instant.now());
		final Path target = screenshotDir.resolve(timestamp + "-" + checkpointName + ".png");
		Files.copy(screenshot.toPath(), target);
		System.out.println("Saved screenshot: " + target);
	}

	private String getConfig(final String sysProp, final String envVar) {
		final String propValue = System.getProperty(sysProp);
		if (propValue != null && !propValue.isBlank()) {
			return propValue.trim();
		}
		final String envValue = System.getenv(envVar);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		return null;
	}

	private String getConfigOrDefault(final String sysProp, final String envVar, final String fallback) {
		final String value = getConfig(sysProp, envVar);
		return value == null ? fallback : value;
	}

	private boolean getBooleanConfig(final String sysProp, final String envVar, final boolean fallback) {
		final String value = getConfig(sysProp, envVar);
		return value == null ? fallback : Boolean.parseBoolean(value);
	}

	private long getLongConfig(final String sysProp, final String envVar, final long fallback) {
		final String value = getConfig(sysProp, envVar);
		if (value == null) {
			return fallback;
		}
		try {
			return Long.parseLong(value);
		} catch (final NumberFormatException ignored) {
			return fallback;
		}
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String delimiter = chars[i] == '\'' ? "\"" : "'";
			result.append(delimiter).append(chars[i]).append(delimiter);
			if (i < chars.length - 1) {
				result.append(",");
			}
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface StepRunner {
		void run() throws Exception;
	}
}
