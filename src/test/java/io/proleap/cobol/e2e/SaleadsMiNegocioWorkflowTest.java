package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * End-to-end workflow test for SaleADS.ai Mi Negocio module.
 * <p>
 * Environment configuration:
 * <ul>
 * <li>SALEADS_BASE_URL or -Dsaleads.base.url (required)</li>
 * <li>SALEADS_GOOGLE_ACCOUNT or -Dsaleads.google.account (optional, default:
 * juanlucasbarbiergarzon@gmail.com)</li>
 * <li>SALEADS_HEADLESS or -Dsaleads.headless (optional, default: true)</li>
 * <li>SALEADS_TIMEOUT_SECONDS or -Dsaleads.timeout.seconds (optional, default:
 * 35)</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final String baseUrl = getConfigValue("SALEADS_BASE_URL", "saleads.base.url", null);
		Assume.assumeTrue("Set SALEADS_BASE_URL or -Dsaleads.base.url to execute this test.",
				baseUrl != null && !baseUrl.isBlank());

		final boolean headless = Boolean
				.parseBoolean(getConfigValue("SALEADS_HEADLESS", "saleads.headless", "true"));
		final int timeoutSeconds = Integer
				.parseInt(getConfigValue("SALEADS_TIMEOUT_SECONDS", "saleads.timeout.seconds", "35"));

		evidenceDir = Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1200", "--disable-notifications", "--disable-dev-shm-usage",
				"--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		WebDriverManager.chromedriver().setup();
		driver = new ChromeDriver(options);
		driver.manage().window().setSize(new Dimension(1600, 1200));
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		driver.get(baseUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep("Login", this::loginWithGoogle);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegociosView);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", result -> validateLegalLink("Términos y Condiciones", result));
		runStep("Política de Privacidad", result -> validateLegalLink("Política de Privacidad", result));

		final Path reportPath = writeFinalReport();
		assertAllRequiredStepsPassed(reportPath);
	}

	private void loginWithGoogle(final StepResult result) {
		final String appTab = driver.getWindowHandle();
		final Set<String> windowsBeforeLoginClick = new HashSet<>(driver.getWindowHandles());
		clickFirstVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
				"Continuar con Google", "Google");

		final String accountEmail = getConfigValue("SALEADS_GOOGLE_ACCOUNT", "saleads.google.account",
				DEFAULT_GOOGLE_ACCOUNT);
		handleGoogleAccountSelection(accountEmail, appTab, windowsBeforeLoginClick);

		assertAnyVisible("Main application interface appears",
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[normalize-space()='Mi Negocio']"),
				By.xpath("//*[normalize-space()='Negocio']"));
		assertAnyVisible("Left sidebar navigation is visible",
				By.xpath("//aside"),
				By.xpath("//nav[contains(@class,'sidebar')]"),
				By.xpath("//*[contains(@class,'sidebar')]"));

		result.screenshotPath = captureScreenshot("01-dashboard-loaded");
		result.notes.add("Dashboard loaded successfully after Google login.");
	}

	private void openMiNegocioMenu(final StepResult result) {
		ensureMiNegocioExpanded();

		assertVisibleByText("Agregar Negocio", "Agregar Negocio option");
		assertVisibleByText("Administrar Negocios", "Administrar Negocios option");

		result.screenshotPath = captureScreenshot("02-mi-negocio-expanded");
		result.notes.add("Mi Negocio menu expanded and options are visible.");
	}

	private void validateAgregarNegocioModal(final StepResult result) {
		clickByVisibleText("Agregar Negocio");
		assertVisibleByText("Crear Nuevo Negocio", "modal title Crear Nuevo Negocio");
		assertAnyVisible("Nombre del Negocio input",
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[@aria-label='Nombre del Negocio']"),
				By.xpath("//*[normalize-space()='Nombre del Negocio']/following::input[1]"));
		assertVisibleByText("Tienes 2 de 3 negocios", "business limit text");
		assertVisibleByText("Cancelar", "Cancelar button");
		assertVisibleByText("Crear Negocio", "Crear Negocio button");

		final Optional<WebElement> businessNameInput = findFirstVisible(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[@aria-label='Nombre del Negocio']"),
				By.xpath("//*[normalize-space()='Nombre del Negocio']/following::input[1]"),
				By.xpath("//div[contains(@role,'dialog')]//input[1]"));
		businessNameInput.ifPresent(input -> {
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
		});

		result.screenshotPath = captureScreenshot("03-crear-negocio-modal");
		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[normalize-space()='Crear Nuevo Negocio']")));
		waitForUiToLoad();
		result.notes.add("Agregar Negocio modal validated and closed.");
	}

	private void openAdministrarNegociosView(final StepResult result) {
		ensureMiNegocioExpanded();
		clickByVisibleText("Administrar Negocios");

		assertVisibleByText("Información General", "Información General section");
		assertVisibleByText("Detalles de la Cuenta", "Detalles de la Cuenta section");
		assertVisibleByText("Tus Negocios", "Tus Negocios section");
		assertVisibleByText("Sección Legal", "Sección Legal section");

		result.screenshotPath = captureScreenshot("04-administrar-negocios");
		result.notes.add("Administrar Negocios page loaded with all required sections.");
	}

	private void validateInformacionGeneral(final StepResult result) {
		final WebElement section = findSectionByHeading("Información General");
		final String sectionText = normalizeWhitespace(section.getText());

		assertTrue("User email should be visible in Información General",
				EMAIL_PATTERN.matcher(sectionText).find());
		assertVisibleByText("BUSINESS PLAN", "BUSINESS PLAN text");
		assertVisibleByText("Cambiar Plan", "Cambiar Plan button");

		final String withoutHeading = sectionText.replace("Información General", "").trim();
		assertTrue("User name should be visible in Información General",
				withoutHeading.matches(".*[\\p{L}]{2,}.*"));

		result.screenshotPath = captureScreenshot("05-informacion-general");
		result.notes.add("Información General validated (name, email, plan, and action button).");
	}

	private void validateDetallesCuenta(final StepResult result) {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		final String sectionText = normalizeWhitespace(section.getText()).toLowerCase();

		assertTrue("'Cuenta creada' should be visible", sectionText.contains("cuenta creada"));
		assertTrue("'Estado activo' should be visible", sectionText.contains("estado activo"));
		assertTrue("'Idioma seleccionado' should be visible", sectionText.contains("idioma seleccionado"));

		result.screenshotPath = captureScreenshot("06-detalles-cuenta");
		result.notes.add("Detalles de la Cuenta validated.");
	}

	private void validateTusNegocios(final StepResult result) {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final String sectionText = normalizeWhitespace(section.getText());

		assertTrue("Business list should be visible",
				sectionText.replace("Tus Negocios", "").trim().length() > 0);
		assertVisibleByText("Agregar Negocio", "Agregar Negocio action in Tus Negocios");
		assertTrue("'Tienes 2 de 3 negocios' should be visible", sectionText.contains("Tienes 2 de 3 negocios"));

		result.screenshotPath = captureScreenshot("07-tus-negocios");
		result.notes.add("Tus Negocios validated.");
	}

	private void validateLegalLink(final String linkText, final StepResult result) {
		final String appTab = driver.getWindowHandle();
		final String appUrl = driver.getCurrentUrl();
		final Set<String> windowsBeforeClick = new HashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);
		waitForUiToLoad();

		final boolean openedNewTab = waitForNewTab(windowsBeforeClick);
		if (openedNewTab) {
			switchToNewestTab(windowsBeforeClick);
		}

		assertVisibleByText(linkText, linkText + " heading");
		final String bodyText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		assertTrue("Legal content text should be visible for " + linkText, bodyText.length() > 120);

		result.screenshotPath = captureScreenshot(slug("08-09-" + linkText));
		result.finalUrl = driver.getCurrentUrl();
		result.notes.add("Validated legal page URL: " + result.finalUrl);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appTab);
			waitForUiToLoad();
			return;
		}

		driver.navigate().back();
		waitForUiToLoad();
		if (!driver.getWindowHandle().equals(appTab) || !driver.getCurrentUrl().contains(baseDomain(appUrl))) {
			driver.get(appUrl);
			waitForUiToLoad();
		}
	}

	private void runStep(final String stepName, final StepExecutor executor) {
		final StepResult result = new StepResult();

		try {
			executor.execute(result);
			result.status = "PASS";
		} catch (final Throwable throwable) {
			result.status = "FAIL";
			result.notes.add(throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
			if (driver != null) {
				try {
					result.screenshotPath = captureScreenshot(slug("failed-" + stepName));
				} catch (final RuntimeException screenshotError) {
					result.notes.add("Unable to capture screenshot on failure: " + screenshotError.getMessage());
				}
			}
		}

		if (result.screenshotPath == null && driver != null) {
			result.screenshotPath = captureScreenshot(slug("checkpoint-" + stepName));
		}
		stepResults.put(stepName, result);
	}

	private Path writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio workflow final report\n");
		report.append("Generated at: ")
				.append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
				.append('\n');
		report.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append("\n\n");

		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			final String stepName = entry.getKey();
			final StepResult result = entry.getValue();

			report.append(stepName).append(": ").append(result.status).append('\n');
			if (result.finalUrl != null) {
				report.append("  URL: ").append(result.finalUrl).append('\n');
			}
			if (result.screenshotPath != null) {
				report.append("  Screenshot: ").append(result.screenshotPath.toAbsolutePath()).append('\n');
			}
			for (final String note : result.notes) {
				report.append("  Note: ").append(note).append('\n');
			}
			report.append('\n');
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, report.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		System.out.println(report);
		System.out.println("Saved report to: " + reportPath.toAbsolutePath());
		return reportPath;
	}

	private void assertAllRequiredStepsPassed(final Path reportPath) throws IOException {
		final List<String> requiredOrder = Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad");

		final List<String> failures = new ArrayList<>();
		for (final String stepName : requiredOrder) {
			final StepResult result = stepResults.get(stepName);
			if (result == null || !"PASS".equals(result.status)) {
				failures.add(stepName);
			}
		}

		assertTrue("Workflow contains failed steps: " + failures + ". See report: " + reportPath.toAbsolutePath(),
				failures.isEmpty());
	}

	private void ensureMiNegocioExpanded() {
		if (isTextVisible("Administrar Negocios") && isTextVisible("Agregar Negocio")) {
			return;
		}

		if (isTextVisible("Negocio")) {
			clickByVisibleText("Negocio");
		}
		if (isTextVisible("Mi Negocio")) {
			clickByVisibleText("Mi Negocio");
		}
		waitForUiToLoad();
	}

	private void handleGoogleAccountSelection(final String accountEmail, final String appTab,
			final Set<String> windowsBeforeLoginClick) {
		final boolean newTabOpened = waitForNewTab(windowsBeforeLoginClick);
		if (newTabOpened) {
			switchToNewestTab(windowsBeforeLoginClick);
		}

		if (isTextVisible(accountEmail)) {
			clickByVisibleText(accountEmail);
			waitForUiToLoad();
		}

		if (driver.getWindowHandles().contains(appTab)) {
			driver.switchTo().window(appTab);
			waitForUiToLoad();
		}
	}

	private void clickFirstVisibleText(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				clickByVisibleText(text);
				return;
			}
		}

		throw new AssertionError("None of the expected texts were visible: " + Arrays.toString(texts));
	}

	private void clickByVisibleText(final String text) {
		final Optional<WebElement> visibleElement = findFirstVisible(
				By.xpath("//button[normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//a[normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//*[@role='button' and normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//*[normalize-space()=" + xpathLiteral(text)
						+ "]/ancestor::*[self::button or self::a or @role='button'][1]"),
				By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]"));

		final WebElement element = visibleElement
				.orElseThrow(() -> new AssertionError("Could not locate clickable element with text: " + text));

		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiToLoad();
	}

	private void assertVisibleByText(final String text, final String description) {
		assertAnyVisible(description,
				By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]"));
	}

	private void assertAnyVisible(final String description, final By... locators) {
		final Optional<WebElement> visible = findFirstVisible(locators);
		assertTrue(description + " was not visible.", visible.isPresent());
	}

	private Optional<WebElement> findFirstVisible(final By... locators) {
		for (final By locator : locators) {
			try {
				final List<WebElement> elements = driver.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return Optional.of(element);
					}
				}
			} catch (final RuntimeException ignored) {
				// Continue to next locator in case of stale/intercepted rendering state.
			}
		}
		return Optional.empty();
	}

	private WebElement findSectionByHeading(final String headingText) {
		final String headingLiteral = xpathLiteral(headingText);
		final By sectionLocator = By.xpath(
				"//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::p][normalize-space()="
						+ headingLiteral + "]/ancestor::*[self::section or self::div][1]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(sectionLocator));
	}

	private boolean isTextVisible(final String text) {
		return findFirstVisible(By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]")).isPresent();
	}

	private boolean waitForNewTab(final Set<String> previousWindows) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(7))
					.until(d -> d.getWindowHandles().size() > previousWindows.size());
			return true;
		} catch (final RuntimeException ignored) {
			return false;
		}
	}

	private void switchToNewestTab(final Set<String> previousWindows) {
		for (final String handle : driver.getWindowHandles()) {
			if (!previousWindows.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));

		final By busySelectors = By.cssSelector(
				"[aria-busy='true'], .loading, .loader, .spinner, [data-testid*='loading'], [class*='loading']");
		try {
			new WebDriverWait(driver, Duration.ofSeconds(5))
					.until(d -> d.findElements(busySelectors).stream().noneMatch(WebElement::isDisplayed));
		} catch (final RuntimeException ignored) {
			// Continue when no known loader convention is present.
		}
	}

	private Path captureScreenshot(final String fileName) {
		try {
			final Path destination = evidenceDir.resolve(slug(fileName) + ".png");
			final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(destination, screenshot, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			return destination;
		} catch (final IOException ioException) {
			throw new RuntimeException("Failed to save screenshot: " + fileName, ioException);
		}
	}

	private String getConfigValue(final String envName, final String propertyName, final String defaultValue) {
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		return defaultValue;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char current = chars[i];
			if (i > 0) {
				builder.append(", ");
			}
			if (current == '\'') {
				builder.append("\"'\"");
			} else {
				builder.append("'").append(current).append("'");
			}
		}
		builder.append(')');
		return builder.toString();
	}

	private String slug(final String input) {
		return input.toLowerCase()
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-+", "")
				.replaceAll("-+$", "");
	}

	private String normalizeWhitespace(final String value) {
		return value == null ? "" : value.replaceAll("\\s+", " ").trim();
	}

	private String baseDomain(final String url) {
		final String withoutProtocol = url.replaceFirst("^https?://", "");
		final int slashIndex = withoutProtocol.indexOf('/');
		return slashIndex >= 0 ? withoutProtocol.substring(0, slashIndex) : withoutProtocol;
	}

	@FunctionalInterface
	private interface StepExecutor {
		void execute(StepResult result);
	}

	private static final class StepResult {
		private String status = "FAIL";
		private Path screenshotPath;
		private String finalUrl;
		private final List<String> notes = new ArrayList<>();
	}
}
