package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow validation for SaleADS Mi Negocio module.
 *
 * <p>Runtime config:
 * <ul>
 *   <li>SALEADS_LOGIN_URL or -Dsaleads.login.url: login page URL</li>
 *   <li>SALEADS_HEADLESS or -Dsaleads.headless: defaults to true</li>
 *   <li>SALEADS_WAIT_SECONDS or -Dsaleads.wait.seconds: defaults to 25</li>
 *   <li>SALEADS_EXPECTED_USER_NAME or -Dsaleads.expected.user.name: optional exact user name</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Informacion General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Terminos y Condiciones",
			"Politica de Privacidad");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = getConfig("SALEADS_LOGIN_URL", "saleads.login.url");
		Assume.assumeTrue(
				"Missing SALEADS_LOGIN_URL or -Dsaleads.login.url. Skipping UI E2E run.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(getConfigOrDefault("SALEADS_HEADLESS", "saleads.headless", "true"));
		final int waitSeconds = Integer.parseInt(getConfigOrDefault("SALEADS_WAIT_SECONDS", "saleads.wait.seconds", "25"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));

		evidenceDir = Paths.get("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

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
	public void saleadsMiNegocioFullTest() {
		runStep("Login", this::validateLoginFlow);
		runStep("Mi Negocio menu", this::validateMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::validateAdministrarNegociosView);
		runStep("Informacion General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Terminos y Condiciones", () -> validateLegalLink(
				Arrays.asList("T\u00e9rminos y Condiciones", "Terminos y Condiciones"),
				Arrays.asList("T\u00e9rminos y Condiciones", "Terminos y Condiciones"),
				"08-terminos-y-condiciones"));
		runStep("Politica de Privacidad", () -> validateLegalLink(
				Arrays.asList("Pol\u00edtica de Privacidad", "Politica de Privacidad"),
				Arrays.asList("Pol\u00edtica de Privacidad", "Politica de Privacidad"),
				"09-politica-de-privacidad"));

		final String report = buildFinalReport();
		System.out.println(report);

		final List<String> failedSteps = new ArrayList<>();
		for (String field : REPORT_FIELDS) {
			if (!stepResults.containsKey(field) || !stepResults.get(field).passed) {
				failedSteps.add(field);
			}
		}

		if (!failedSteps.isEmpty()) {
			fail("One or more workflow validations failed: " + failedSteps + "\n\n" + report);
		}
	}

	private String validateLoginFlow() throws IOException {
		clickFirstByVisibleText(Arrays.asList(
				"Sign in with Google",
				"Login with Google",
				"Iniciar sesi\u00f3n con Google",
				"Continuar con Google",
				"Ingresar con Google"));
		waitForUiToLoad();

		clickIfVisibleByText("juanlucasbarbiergarzon@gmail.com", Duration.ofSeconds(8));
		waitForUiToLoad();

		waitUntilVisible(By.xpath("//aside | //nav"));
		assertVisibleAnyText(Arrays.asList("Negocio", "Mi Negocio"));
		captureScreenshot("01-dashboard-loaded");
		return "Dashboard and left sidebar are visible.";
	}

	private String validateMiNegocioMenu() throws IOException {
		clickIfVisibleByText("Negocio", Duration.ofSeconds(3));
		clickFirstByVisibleText(Arrays.asList("Mi Negocio", "Mi negocio"));
		waitForUiToLoad();

		assertVisibleAnyText(Arrays.asList("Agregar Negocio"));
		assertVisibleAnyText(Arrays.asList("Administrar Negocios"));
		captureScreenshot("02-mi-negocio-menu-expanded");
		return "Mi Negocio menu expanded with both submenu options visible.";
	}

	private String validateAgregarNegocioModal() throws IOException {
		clickFirstByVisibleText(Arrays.asList("Agregar Negocio"));
		waitForUiToLoad();

		assertVisibleAnyText(Arrays.asList("Crear Nuevo Negocio"));
		final By nombreNegocioInput = By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::*[self::input or self::textarea][1]"
						+ " | //input[contains(@placeholder, 'Nombre del Negocio')]");
		waitUntilVisible(nombreNegocioInput);
		assertVisibleAnyText(Arrays.asList("Tienes 2 de 3 negocios"));
		assertVisibleAnyText(Arrays.asList("Cancelar"));
		assertVisibleAnyText(Arrays.asList("Crear Negocio"));

		captureScreenshot("03-crear-nuevo-negocio-modal");

		final WebElement input = waitUntilVisible(nombreNegocioInput);
		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatizacion");
		clickFirstByVisibleText(Arrays.asList("Cancelar"));
		waitForUiToLoad();
		return "Crear Nuevo Negocio modal validated and closed with Cancelar.";
	}

	private String validateAdministrarNegociosView() throws IOException {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(3))) {
			clickFirstByVisibleText(Arrays.asList("Mi Negocio", "Mi negocio"));
			waitForUiToLoad();
		}

		clickFirstByVisibleText(Arrays.asList("Administrar Negocios"));
		waitForUiToLoad();

		assertVisibleAnyText(Arrays.asList("Informaci\u00f3n General", "Informacion General"));
		assertVisibleAnyText(Arrays.asList("Detalles de la Cuenta"));
		assertVisibleAnyText(Arrays.asList("Tus Negocios"));
		assertVisibleAnyText(Arrays.asList("Secci\u00f3n Legal", "Seccion Legal"));

		captureScreenshot("04-administrar-negocios-view");
		return "Administrar Negocios view and all target sections are visible.";
	}

	private String validateInformacionGeneral() {
		assertVisibleAnyText(Arrays.asList("Informaci\u00f3n General", "Informacion General"));
		assertVisibleAnyText(Arrays.asList("BUSINESS PLAN"));
		assertVisibleAnyText(Arrays.asList("Cambiar Plan"));

		final String bodyText = safeBodyText();
		final Matcher matcher = EMAIL_PATTERN.matcher(bodyText);
		assertTrue("User email is not visible in Informacion General.", matcher.find());
		final String email = matcher.group();

		final String expectedName = getConfig("SALEADS_EXPECTED_USER_NAME", "saleads.expected.user.name");
		if (expectedName != null && !expectedName.isBlank()) {
			assertVisibleAnyText(Arrays.asList(expectedName));
		} else {
			assertTrue("User name was not confidently detected near the email.",
					hasLikelyNameNearEmail(bodyText, email));
		}

		return "Informacion General validated. Email detected: " + email;
	}

	private String validateDetallesCuenta() {
		assertVisibleAnyText(Arrays.asList("Detalles de la Cuenta"));
		assertVisibleAnyText(Arrays.asList("Cuenta creada"));
		assertVisibleAnyText(Arrays.asList("Estado activo", "Estado Activo"));
		assertVisibleAnyText(Arrays.asList("Idioma seleccionado"));
		return "Detalles de la Cuenta fields are visible.";
	}

	private String validateTusNegocios() {
		assertVisibleAnyText(Arrays.asList("Tus Negocios"));
		assertVisibleAnyText(Arrays.asList("Agregar Negocio"));
		assertVisibleAnyText(Arrays.asList("Tienes 2 de 3 negocios"));

		final By businessListLocator = By.xpath(
				"//*[contains(normalize-space(.), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]"
						+ "//*[self::li or self::tr or @role='row' or contains(@class, 'business') or contains(@class, 'negocio')]");
		wait.until(d -> !d.findElements(businessListLocator).isEmpty());
		return "Tus Negocios list and controls are visible.";
	}

	private String validateLegalLink(final List<String> linkTexts, final List<String> expectedHeadings, final String screenshotPrefix)
			throws IOException {
		final String applicationTab = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String originalUrl = driver.getCurrentUrl();

		clickFirstByVisibleText(linkTexts);
		waitForUiToLoad();

		final boolean openedNewTab = wait.until(d -> d.getWindowHandles().size() > handlesBefore.size()
				|| !d.getCurrentUrl().equals(originalUrl));

		if (!openedNewTab) {
			fail("Legal link did not trigger navigation or open a new tab.");
		}

		if (driver.getWindowHandles().size() > handlesBefore.size()) {
			for (String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		waitForUiToLoad();
		assertVisibleAnyText(expectedHeadings);
		assertTrue("Legal content appears to be empty.",
				safeBodyText().replaceAll("\\s+", " ").trim().length() > 120);

		final String finalUrl = driver.getCurrentUrl();
		captureScreenshot(screenshotPrefix);

		if (driver.getWindowHandles().size() > handlesBefore.size()) {
			driver.close();
			driver.switchTo().window(applicationTab);
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		assertTrue("Did not return to the application tab after validating legal link.",
				driver.getWindowHandle().equals(applicationTab));
		return "Validated legal page URL: " + finalUrl;
	}

	private void runStep(final String stepName, final CheckedSupplier<String> validator) {
		try {
			final String details = validator.get();
			stepResults.put(stepName, StepResult.pass(details));
		} catch (Throwable error) {
			final String screenshotName = "error-" + toSlug(stepName);
			try {
				captureScreenshot(screenshotName);
			} catch (IOException ignored) {
				// Screenshot best effort for failed step.
			}
			final String errorMessage = Optional.ofNullable(error.getMessage()).orElse(error.getClass().getSimpleName());
			stepResults.put(stepName, StepResult.fail(errorMessage + " (screenshot: " + screenshotName + ".png)"));
		}
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Test - Final Report\n");
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		builder.append('\n');

		for (String field : REPORT_FIELDS) {
			final StepResult result = stepResults.getOrDefault(field, StepResult.fail("Step not executed."));
			builder.append("- ").append(field).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (result.details != null && !result.details.isBlank()) {
				builder.append(" | ").append(result.details);
			}
			builder.append('\n');
		}

		return builder.toString();
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		try {
			Thread.sleep(600L);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private WebElement waitUntilVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void assertVisibleAnyText(final List<String> candidateTexts) {
		final Optional<WebElement> element = findVisibleElementByTexts(candidateTexts, Duration.ofSeconds(8));
		assertTrue("None of the expected texts were visible: " + candidateTexts, element.isPresent());
	}

	private void clickFirstByVisibleText(final List<String> candidateTexts) {
		final WebElement element = findVisibleElementByTexts(candidateTexts, Duration.ofSeconds(10))
				.orElseThrow(() -> new AssertionError("Could not find clickable element for texts: " + candidateTexts));
		clickElement(element);
		waitForUiToLoad();
	}

	private void clickIfVisibleByText(final String text, final Duration timeout) {
		findVisibleElementByTexts(Arrays.asList(text), timeout).ifPresent(element -> {
			clickElement(element);
			waitForUiToLoad();
		});
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		return findVisibleElementByTexts(Arrays.asList(text), timeout).isPresent();
	}

	private Optional<WebElement> findVisibleElementByTexts(final List<String> candidateTexts, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		for (String text : candidateTexts) {
			try {
				final String literal = escapeXpath(text);
				final By locator = By.xpath("//*[normalize-space(text())=" + literal
						+ " or normalize-space(.)=" + literal + "]");
				localWait.until(d -> d.findElements(locator).stream().anyMatch(WebElement::isDisplayed));
				final List<WebElement> elements = driver.findElements(locator);
				for (WebElement element : elements) {
					if (element.isDisplayed()) {
						return Optional.of(element);
					}
				}
			} catch (TimeoutException ignored) {
				// Try next candidate text.
			}
		}
		return Optional.empty();
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void captureScreenshot(final String baseName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path screenshotPath = evidenceDir.resolve(baseName + ".png");
		Files.copy(screenshot.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private String safeBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (NoSuchElementException ex) {
			return "";
		}
	}

	private boolean hasLikelyNameNearEmail(final String bodyText, final String email) {
		final String[] lines = bodyText.split("\\R");
		for (int i = 0; i < lines.length; i++) {
			if (!lines[i].contains(email)) {
				continue;
			}

			final List<String> candidates = new ArrayList<>();
			if (i > 0) {
				candidates.add(lines[i - 1].trim());
			}
			if (i + 1 < lines.length) {
				candidates.add(lines[i + 1].trim());
			}

			for (String candidate : candidates) {
				if (candidate.length() < 3 || candidate.contains("@")) {
					continue;
				}
				final String upper = candidate.toUpperCase();
				if (upper.contains("BUSINESS PLAN")
						|| upper.contains("CAMBIAR PLAN")
						|| upper.contains("INFORMACION GENERAL")
						|| upper.contains("INFORMACI\u00d3N GENERAL")) {
					continue;
				}
				if (candidate.matches("[\\p{L}][\\p{L} .'-]{2,}")) {
					return true;
				}
			}
		}
		return false;
	}

	private String escapeXpath(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		final String[] parts = text.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String toSlug(final String value) {
		return value.toLowerCase()
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-|-$)", "");
	}

	private String getConfig(final String envKey, final String propertyKey) {
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		return null;
	}

	private String getConfigOrDefault(final String envKey, final String propertyKey, final String fallback) {
		final String value = getConfig(envKey, propertyKey);
		return value == null ? fallback : value;
	}

	private interface CheckedSupplier<T> {
		T get() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
