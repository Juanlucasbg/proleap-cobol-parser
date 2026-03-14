package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Base64;
import java.util.HashMap;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * SaleADS E2E workflow for Mi Negocio module.
 *
 * <p>Runtime config:
 * <ul>
 *   <li>SALEADS_RUN_E2E=true to enable this test (disabled by default)</li>
 *   <li>SALEADS_LOGIN_URL=https://... (optional but recommended)</li>
 *   <li>SALEADS_HEADLESS=true|false (default: true)</li>
 * </ul>
 */
public class SaleAdsMiNegocioFullTest {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_WAIT_TIMEOUT = Duration.ofSeconds(8);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDir;
	private final Map<String, String> legalUrlEvidence = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	@Before
	public void setUp() throws IOException {
		assumeTrue("Skipping E2E test. Set SALEADS_RUN_E2E=true to execute.",
				Boolean.parseBoolean(env("SALEADS_RUN_E2E", "false")));

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);

		screenshotsDir = Paths.get("target", "saleads-screenshots");
		Files.createDirectories(screenshotsDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final Map<String, Boolean> report = createInitialReport();

		final String loginUrl = env("SALEADS_LOGIN_URL", "").trim();
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
			waitForUiLoad();
		}

		runStep(report, "Login", this::executeLoginStep);
		runStep(report, "Mi Negocio menu", this::executeMiNegocioMenuStep);
		runStep(report, "Agregar Negocio modal", this::executeAgregarNegocioModalStep);
		runStep(report, "Administrar Negocios view", this::executeAdministrarNegociosStep);
		runStep(report, "Información General", this::executeInformacionGeneralStep);
		runStep(report, "Detalles de la Cuenta", this::executeDetallesCuentaStep);
		runStep(report, "Tus Negocios", this::executeTusNegociosStep);
		runStep(report, "Términos y Condiciones",
				() -> executeLegalPageStep("Términos y Condiciones", "Términos y Condiciones", "05-terminos"));
		runStep(report, "Política de Privacidad",
				() -> executeLegalPageStep("Política de Privacidad", "Política de Privacidad", "06-politica"));

		printFinalReport(report);
		assertTrue("One or more SaleADS workflow validations failed. See report and logs.",
				report.values().stream().allMatch(Boolean::booleanValue));
	}

	private void executeLoginStep() throws Exception {
		final String currentUrl = driver.getCurrentUrl();
		if (currentUrl == null || currentUrl.trim().isEmpty() || "about:blank".equals(currentUrl)) {
			throw new IllegalStateException(
					"Browser is not on a login page. Provide SALEADS_LOGIN_URL or open the login page before test run.");
		}

		final WebElement googleLoginButton = firstVisibleByText(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Continuar con Google",
				"Login with Google");
		clickAndWait(googleLoginButton);

		final Optional<WebElement> accountSelector = tryVisibleByText(SHORT_WAIT_TIMEOUT, GOOGLE_ACCOUNT);
		if (accountSelector.isPresent()) {
			clickAndWait(accountSelector.get());
		}

		assertAnyTextVisible("Negocio", "Mi Negocio", "Dashboard");
		assertSidebarVisible();
		captureScreenshot("01-dashboard-loaded");
	}

	private void executeMiNegocioMenuStep() throws Exception {
		clickByVisibleText("Mi Negocio");
		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void executeAgregarNegocioModalStep() throws Exception {
		clickByVisibleText("Agregar Negocio");
		assertAnyTextVisible("Crear Nuevo Negocio");
		findBusinessNameInput();
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertAnyTextVisible("Cancelar");
		assertAnyTextVisible("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final WebElement businessNameInput = findBusinessNameInput();
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		waitForUiLoad();

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(textLocator("Crear Nuevo Negocio")));
	}

	private void executeAdministrarNegociosStep() throws Exception {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		assertAnyTextVisible("Información General");
		assertAnyTextVisible("Detalles de la Cuenta");
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Sección Legal");
		captureFullPageScreenshot("04-administrar-negocios-full");
	}

	private void executeInformacionGeneralStep() {
		final WebElement section = sectionByHeading("Información General");
		final String text = section.getText();
		assertTrue("Expected user email to be visible in Información General section.", EMAIL_PATTERN.matcher(text).find());
		assertTrue("Expected user name-like text to be visible in Información General section.",
				hasNameLikeText(text));
		assertAnyTextVisible("BUSINESS PLAN");
		assertAnyTextVisible("Cambiar Plan");
	}

	private void executeDetallesCuentaStep() {
		sectionByHeading("Detalles de la Cuenta");
		assertAnyTextVisible("Cuenta creada");
		assertAnyTextVisible("Estado activo");
		assertAnyTextVisible("Idioma seleccionado");
	}

	private void executeTusNegociosStep() {
		final WebElement section = sectionByHeading("Tus Negocios");
		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertTrue("Expected Tus Negocios section content to be visible.", section.getText().trim().length() > 20);
	}

	private void executeLegalPageStep(final String linkText, final String expectedHeading, final String screenshotPrefix)
			throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);

		String legalHandle = appHandle;
		boolean openedNewTab = false;

		try {
			legalHandle = new WebDriverWait(driver, SHORT_WAIT_TIMEOUT).until(d -> {
				final Set<String> handlesAfterClick = d.getWindowHandles();
				for (final String handle : handlesAfterClick) {
					if (!handlesBeforeClick.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
			openedNewTab = legalHandle != null && !appHandle.equals(legalHandle);
		} catch (final TimeoutException ignored) {
			openedNewTab = false;
		}

		if (openedNewTab) {
			driver.switchTo().window(legalHandle);
			waitForUiLoad();
		}

		assertAnyTextVisible(expectedHeading);
		assertLegalContentVisible();
		legalUrlEvidence.put(linkText, driver.getCurrentUrl());
		captureScreenshot(screenshotPrefix + "-legal-page");

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final Map<String, Boolean> report, final String reportKey, final Step step) {
		try {
			step.run();
			report.put(reportKey, true);
		} catch (final Throwable t) {
			report.put(reportKey, false);
			final String failure = reportKey + ": " + t.getMessage();
			failures.add(failure);
			System.err.println("Step failed -> " + failure);
		}
	}

	private Map<String, Boolean> createInitialReport() {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
		return report;
	}

	private void printFinalReport(final Map<String, Boolean> report) {
		System.out.println();
		System.out.println("======== SaleADS Mi Negocio Workflow Report ========");
		report.forEach((step, passed) -> System.out.println(step + ": " + (passed ? "PASS" : "FAIL")));

		if (!legalUrlEvidence.isEmpty()) {
			System.out.println("---- Legal URL Evidence ----");
			legalUrlEvidence.forEach((name, url) -> System.out.println(name + " URL: " + url));
		}

		if (!failures.isEmpty()) {
			System.out.println("---- Failures ----");
			failures.forEach(System.out::println);
		}
		System.out.println("====================================================");
		System.out.println();
	}

	private void clickByVisibleText(final String text) {
		clickAndWait(firstClickableByText(text));
	}

	private void clickAndWait(final WebElement element) {
		scrollIntoView(element);
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiLoad();
	}

	private void waitForUiLoad() {
		final ExpectedCondition<Boolean> jsLoad = webDriver -> {
			if (!(webDriver instanceof JavascriptExecutor)) {
				return true;
			}

			final Object readyState = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(readyState));
		};

		wait.until(jsLoad);
	}

	private void assertSidebarVisible() {
		wait.until(ExpectedConditions.or(
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside")),
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//nav")),
				ExpectedConditions.visibilityOfElementLocated(
						By.xpath("//*[contains(@class,'sidebar') and not(contains(@style,'display: none'))]"))));
	}

	private void assertAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return;
			}
		}
		throw new NoSuchElementException("None of the expected visible texts were found: " + Arrays.toString(texts));
	}

	private boolean isTextVisible(final String text) {
		try {
			final List<WebElement> elements = driver.findElements(textLocator(text));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final Throwable ignored) {
			return false;
		}
	}

	private WebElement firstVisibleByText(final String... texts) {
		final List<String> expectedTexts = Arrays.asList(texts);
		return wait.until(d -> {
			for (final String text : expectedTexts) {
				final List<WebElement> elements = d.findElements(textLocator(text));
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private WebElement firstClickableByText(final String... texts) {
		final List<String> expectedTexts = Arrays.asList(texts);
		return wait.until(d -> {
			for (final String text : expectedTexts) {
				for (final WebElement candidate : d.findElements(textLocator(text))) {
					final WebElement clickable = toClickableElement(candidate);
					if (clickable != null && clickable.isDisplayed() && clickable.isEnabled()) {
						return clickable;
					}
				}
			}
			return null;
		});
	}

	private Optional<WebElement> tryVisibleByText(final Duration timeout, final String text) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			final WebElement element = shortWait.until(d -> {
				for (final WebElement candidate : d.findElements(textLocator(text))) {
					if (candidate.isDisplayed()) {
						return candidate;
					}
				}
				return null;
			});
			return Optional.ofNullable(element);
		} catch (final TimeoutException ignored) {
			return Optional.empty();
		}
	}

	private WebElement findBusinessNameInput() {
		final List<By> locators = List.of(
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"));

		for (final By locator : locators) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final TimeoutException ignored) {
				// Try next locator
			}
		}

		throw new NoSuchElementException("Could not find input field 'Nombre del Negocio'.");
	}

	private WebElement toClickableElement(final WebElement candidate) {
		if (candidate == null || !candidate.isDisplayed()) {
			return null;
		}

		if (isClickableTag(candidate)) {
			return candidate;
		}

		final List<WebElement> clickableAncestors = candidate.findElements(By.xpath(
				"./ancestor-or-self::button | ./ancestor-or-self::a | ./ancestor-or-self::*[@role='button']"));

		// The last matching node on this axis is the nearest clickable ancestor.
		for (int i = clickableAncestors.size() - 1; i >= 0; i--) {
			final WebElement ancestor = clickableAncestors.get(i);
			if (ancestor.isDisplayed() && ancestor.isEnabled()) {
				return ancestor;
			}
		}

		return null;
	}

	private boolean isClickableTag(final WebElement element) {
		final String tagName = element.getTagName();
		if ("button".equalsIgnoreCase(tagName) || "a".equalsIgnoreCase(tagName)) {
			return true;
		}

		final String role = element.getAttribute("role");
		return role != null && "button".equalsIgnoreCase(role);
	}

	private WebElement sectionByHeading(final String headingText) {
		final String escaped = escapeXpathText(headingText);
		final By headingLocator = By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::p]"
				+ "[contains(normalize-space(), " + escaped + ")]");
		final WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(headingLocator));
		final WebElement section = heading.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		assertTrue("Expected section heading to be visible: " + headingText, heading.isDisplayed());
		assertTrue("Expected section container to be visible for: " + headingText, section.isDisplayed());
		return section;
	}

	private void assertLegalContentVisible() {
		final By legalBodyLocator = By.xpath(
				"//article//*[string-length(normalize-space()) > 60] | //main//*[string-length(normalize-space()) > 60] | //p[string-length(normalize-space()) > 60]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(legalBodyLocator));
	}

	private void scrollIntoView(final WebElement element) {
		if (driver instanceof JavascriptExecutor) {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		}
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final String timestamp = LocalDateTime.now().format(TS_FORMAT);
		final String fileName = checkpointName + "-" + timestamp + ".png";
		final Path destination = screenshotsDir.resolve(fileName);
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Screenshot saved: " + destination.toAbsolutePath());
	}

	private void captureFullPageScreenshot(final String checkpointName) throws IOException {
		try {
			if (driver instanceof ChromeDriver) {
				final ChromeDriver chromeDriver = (ChromeDriver) driver;
				final Map<String, Object> metrics = chromeDriver.executeCdpCommand("Page.getLayoutMetrics", new HashMap<>());
				@SuppressWarnings("unchecked")
				final Map<String, Object> contentSize = (Map<String, Object>) metrics.get("contentSize");

				final Map<String, Object> clip = new HashMap<>();
				clip.put("x", 0);
				clip.put("y", 0);
				clip.put("width", contentSize.get("width"));
				clip.put("height", contentSize.get("height"));
				clip.put("scale", 1);

				final Map<String, Object> params = new HashMap<>();
				params.put("format", "png");
				params.put("captureBeyondViewport", true);
				params.put("fromSurface", true);
				params.put("clip", clip);

				final Map<String, Object> screenshot = chromeDriver.executeCdpCommand("Page.captureScreenshot", params);
				final String data = String.valueOf(screenshot.get("data"));
				final byte[] bytes = Base64.getDecoder().decode(data);

				final String timestamp = LocalDateTime.now().format(TS_FORMAT);
				final Path destination = screenshotsDir.resolve(checkpointName + "-" + timestamp + ".png");
				Files.write(destination, bytes);
				System.out.println("Full-page screenshot saved: " + destination.toAbsolutePath());
				return;
			}
		} catch (final ClassCastException | FileAlreadyExistsException ignored) {
			// Fallback to viewport screenshot below.
		} catch (final Throwable ignored) {
			// Fallback to viewport screenshot below.
		}

		captureScreenshot(checkpointName);
	}

	private By textLocator(final String text) {
		final String escaped = escapeXpathText(text);
		return By.xpath("//*[contains(normalize-space(), " + escaped + ")]");
	}

	private String env(final String key, final String fallback) {
		final String value = System.getenv(key);
		return value != null ? value : fallback;
	}

	private boolean hasNameLikeText(final String sectionText) {
		final Set<String> ignored = new LinkedHashSet<>(Arrays.asList(
				"Información", "General", "BUSINESS", "PLAN", "Cambiar", "Plan"));
		final String[] lines = sectionText.split("\\R");
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(trimmed).find()) {
				continue;
			}
			if (ignored.contains(trimmed)) {
				continue;
			}
			if (trimmed.length() >= 3 && trimmed.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private String escapeXpathText(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final String[] parts = text.split("'");
		final StringBuilder result = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			result.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				result.append(", \"'\", ");
			}
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface Step {
		void run() throws Exception;
	}
}
