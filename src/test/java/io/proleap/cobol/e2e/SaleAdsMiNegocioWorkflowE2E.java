package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioWorkflowE2E {

	private static final String GOOGLE_TEST_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private int screenshotCounter;

	@Before
	public void setUp() throws IOException {
		evidenceDirectory = Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDirectory);

		driver = buildDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(readTimeoutSeconds()));
		driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500));
		driver.manage().window().maximize();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep("Login", this::runLoginStep);
		runStep("Mi Negocio menu", this::runMiNegocioMenuStep);
		runStep("Agregar Negocio modal", this::runAgregarNegocioModalStep);
		runStep("Administrar Negocios view", this::runAdministrarNegociosStep);
		runStep("Información General", this::runInformacionGeneralStep);
		runStep("Detalles de la Cuenta", this::runDetallesCuentaStep);
		runStep("Tus Negocios", this::runTusNegociosStep);
		runStep("Términos y Condiciones", () -> runLegalStep("Términos y Condiciones", "Términos y Condiciones",
				"terminos-y-condiciones"));
		runStep("Política de Privacidad",
				() -> runLegalStep("Política de Privacidad", "Política de Privacidad", "politica-de-privacidad"));

		final List<String> failedSteps = new ArrayList<>();
		for (Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			if (!entry.getValue().passed) {
				failedSteps.add(entry.getKey() + " -> " + entry.getValue().details);
			}
		}

		Assert.assertTrue(
				"One or more SaleADS Mi Negocio validations failed. See target/saleads-evidence for screenshots and final-report.txt. "
						+ failedSteps,
				failedSteps.isEmpty());
	}

	private void runLoginStep() {
		openConfiguredLoginUrlIfPresent();

		final Set<String> handlesBeforeLogin = driver.getWindowHandles();
		clickByAnyText("Sign in with Google", "Login with Google", "Continuar con Google", "Iniciar con Google");
		switchToNewWindowIfOpened(handlesBeforeLogin);
		selectGoogleAccountIfVisible(GOOGLE_TEST_ACCOUNT);
		waitForUiLoad();

		assertAnyVisible(By.xpath("//aside"),
				By.xpath("//*[contains(normalize-space(), " + toXPathLiteral("Negocio") + ")]"));
		captureScreenshot("01-dashboard-loaded");
	}

	private void runMiNegocioMenuStep() {
		assertAnyVisible(By.xpath("//aside"));
		clickByAnyText("Mi Negocio");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void runAgregarNegocioModalStep() {
		ensureMiNegocioExpanded();
		clickByAnyText("Agregar Negocio");

		assertTextVisible("Crear Nuevo Negocio");
		waitUntilVisible(By.xpath(
				"//label[contains(normalize-space(), " + toXPathLiteral("Nombre del Negocio") + ")]/following::input[1]"
						+ "|//input[contains(@placeholder, " + toXPathLiteral("Nombre del Negocio") + ")]"
						+ "|//input[contains(@aria-label, " + toXPathLiteral("Nombre del Negocio") + ")]"));
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		// Optional interaction requested by the workflow.
		typeIntoNombreNegocioField("Negocio Prueba Automatización");
		clickByAnyText("Cancelar");
		waitUntilInvisible(By.xpath("//*[contains(normalize-space(), " + toXPathLiteral("Crear Nuevo Negocio") + ")]"));
	}

	private void runAdministrarNegociosStep() {
		ensureMiNegocioExpanded();
		clickByAnyText("Administrar Negocios");

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		captureScreenshot("04-administrar-negocios-view");
	}

	private void runInformacionGeneralStep() {
		final WebElement section = waitUntilVisible(sectionLocator("Información General"));
		assertSectionContainsEmail(section);
		assertSectionContainsLikelyName(section);
		assertTextVisibleWithin(section, "BUSINESS PLAN");
		assertButtonVisibleWithin(section, "Cambiar Plan");
	}

	private void runDetallesCuentaStep() {
		final WebElement section = waitUntilVisible(sectionLocator("Detalles de la Cuenta"));
		assertTextVisibleWithin(section, "Cuenta creada");
		assertTextVisibleWithin(section, "Estado activo");
		assertTextVisibleWithin(section, "Idioma seleccionado");
	}

	private void runTusNegociosStep() {
		final WebElement section = waitUntilVisible(sectionLocator("Tus Negocios"));
		assertButtonVisibleWithin(section, "Agregar Negocio");
		assertTextVisibleWithin(section, "Tienes 2 de 3 negocios");

		final List<WebElement> businessEntries = section
				.findElements(By.xpath(".//*[not(self::h1 or self::h2 or self::h3) and contains(normalize-space(), "
						+ toXPathLiteral("Negocio") + ")]"));
		Assert.assertFalse("Business list is not visible in 'Tus Negocios' section.", businessEntries.isEmpty());
	}

	private void runLegalStep(final String linkText, final String expectedHeading, final String screenshotName) {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickByAnyText(linkText);

		final String legalWindow = switchToNewWindowIfOpened(handlesBeforeClick);
		waitForUiLoad();
		assertTextVisible(expectedHeading);
		assertLegalContentVisible();
		capturedUrls.put(linkText, driver.getCurrentUrl());
		captureScreenshot(screenshotName);

		if (!legalWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, StepResult.pass());
		} catch (final Exception | AssertionError error) {
			final String details = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
			stepResults.put(stepName, StepResult.fail(details));
			captureScreenshot("failure-" + slugify(stepName));
		}
	}

	private void openConfiguredLoginUrlIfPresent() {
		final String configuredUrl = System.getenv("SALEADS_LOGIN_URL");
		if (configuredUrl != null && !configuredUrl.isBlank()) {
			driver.get(configuredUrl.trim());
			waitForUiLoad();
		}
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final By accountOption = By.xpath("//*[contains(normalize-space(), " + toXPathLiteral(email) + ")]");
		final List<WebElement> options = driver.findElements(accountOption);
		if (!options.isEmpty()) {
			clickElement(options.get(0));
			waitForUiLoad();
		}
	}

	private void ensureMiNegocioExpanded() {
		if (driver.findElements(By.xpath("//*[contains(normalize-space(), " + toXPathLiteral("Agregar Negocio") + ")]"))
				.isEmpty()) {
			clickByAnyText("Mi Negocio");
		}
	}

	private By sectionLocator(final String heading) {
		return By.xpath("//section[.//*[contains(normalize-space(), " + toXPathLiteral(heading) + ")]]"
				+ "|//*[self::div or self::article][.//*[contains(normalize-space(), " + toXPathLiteral(heading) + ")]]");
	}

	private void typeIntoNombreNegocioField(final String value) {
		final By fieldLocator = By.xpath(
				"//label[contains(normalize-space(), " + toXPathLiteral("Nombre del Negocio") + ")]/following::input[1]"
						+ "|//input[contains(@placeholder, " + toXPathLiteral("Nombre del Negocio") + ")]"
						+ "|//input[contains(@aria-label, " + toXPathLiteral("Nombre del Negocio") + ")]");
		final WebElement field = waitUntilVisible(fieldLocator);
		field.clear();
		field.sendKeys(value);
	}

	private void assertLegalContentVisible() {
		final WebElement body = waitUntilVisible(By.tagName("body"));
		final String bodyText = body.getText() == null ? "" : body.getText().trim();
		Assert.assertTrue("Legal content text is not visible.", bodyText.length() >= 80);
	}

	private void assertSectionContainsEmail(final WebElement section) {
		final String sectionText = section.getText();
		boolean found = false;
		for (String token : sectionText.split("\\s+")) {
			if (EMAIL_PATTERN.matcher(token.trim()).matches()) {
				found = true;
				break;
			}
		}
		Assert.assertTrue("User email is not visible in 'Información General'.", found);
	}

	private void assertSectionContainsLikelyName(final WebElement section) {
		final String[] lines = section.getText().split("\\R");
		for (String line : lines) {
			final String candidate = line == null ? "" : line.trim();
			if (candidate.isEmpty()) {
				continue;
			}
			if (candidate.contains("@")) {
				continue;
			}
			if (candidate.equalsIgnoreCase("Información General") || candidate.equalsIgnoreCase("BUSINESS PLAN")
					|| candidate.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}
			if (candidate.matches(".*\\p{L}+\\s+\\p{L}+.*")) {
				return;
			}
		}

		Assert.fail("User name is not visible in 'Información General'.");
	}

	private void assertTextVisible(final String text) {
		waitUntilVisible(
				By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + " or contains(normalize-space(), "
						+ toXPathLiteral(text) + ")]"));
	}

	private void assertTextVisibleWithin(final WebElement context, final String text) {
		final String xpath = ".//*[normalize-space()=" + toXPathLiteral(text) + " or contains(normalize-space(), "
				+ toXPathLiteral(text) + ")]";
		wait.until(driver -> !context.findElements(By.xpath(xpath)).isEmpty());
	}

	private void assertButtonVisibleWithin(final WebElement context, final String buttonText) {
		final String xpath = ".//button[normalize-space()=" + toXPathLiteral(buttonText)
				+ " or contains(normalize-space(), " + toXPathLiteral(buttonText) + ")]" + "|.//*[@role='button'"
				+ " and (normalize-space()=" + toXPathLiteral(buttonText) + " or contains(normalize-space(), "
				+ toXPathLiteral(buttonText) + "))]";
		wait.until(driver -> !context.findElements(By.xpath(xpath)).isEmpty());
	}

	private void clickByAnyText(final String... candidates) {
		Exception lastError = null;
		for (String candidate : candidates) {
			try {
				final String literal = toXPathLiteral(candidate);
				final String xpath = "(//button[normalize-space()=" + literal + " or contains(normalize-space(), "
						+ literal + ")]" + "|//a[normalize-space()=" + literal + " or contains(normalize-space(), "
						+ literal + ")]" + "|//*[@role='button' and (normalize-space()=" + literal
						+ " or contains(normalize-space(), " + literal + "))]"
						+ "|//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")])[1]";
				final WebElement element = waitUntilClickable(By.xpath(xpath));
				clickElement(element);
				return;
			} catch (Exception error) {
				lastError = error;
			}
		}

		throw new AssertionError("Unable to click any of the expected labels: " + String.join(", ", candidates),
				lastError);
	}

	private String switchToNewWindowIfOpened(final Set<String> previousHandles) {
		try {
			wait.until(driver -> driver.getWindowHandles().size() > previousHandles.size());
		} catch (TimeoutException ignored) {
			return driver.getWindowHandle();
		}

		for (String handle : driver.getWindowHandles()) {
			if (!previousHandles.contains(handle)) {
				driver.switchTo().window(handle);
				return handle;
			}
		}

		return driver.getWindowHandle();
	}

	private void clickElement(final WebElement element) {
		scrollIntoView(element);
		try {
			element.click();
		} catch (Exception ignored) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiLoad();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private WebElement waitUntilVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private WebElement waitUntilClickable(final By locator) {
		return wait.until(ExpectedConditions.elementToBeClickable(locator));
	}

	private void waitUntilInvisible(final By locator) {
		wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
	}

	private void assertAnyVisible(final By... locators) {
		for (By locator : locators) {
			if (!driver.findElements(locator).isEmpty()) {
				return;
			}
		}
		wait.until(driver -> {
			for (By locator : locators) {
				if (!driver.findElements(locator).isEmpty()) {
					return true;
				}
			}
			return false;
		});
	}

	private void waitForUiLoad() {
		wait.until(driver -> "complete"
				.equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
	}

	private void captureScreenshot(final String label) {
		try {
			final File file = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final String fileName = String.format("%02d-%s.png", ++screenshotCounter, slugify(label));
			Files.copy(file.toPath(), evidenceDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception ignored) {
			// Test result should not be blocked by screenshot capture failures.
		}
	}

	private void writeFinalReport() throws IOException {
		if (stepResults.isEmpty()) {
			return;
		}

		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Test\n");
		builder.append("============================\n");
		for (Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			builder.append(entry.getKey()).append(": ").append(entry.getValue().passed ? "PASS" : "FAIL");
			if (!entry.getValue().passed && entry.getValue().details != null && !entry.getValue().details.isBlank()) {
				builder.append(" (").append(entry.getValue().details).append(")");
			}
			builder.append('\n');
		}

		if (!capturedUrls.isEmpty()) {
			builder.append("\nCaptured URLs\n");
			builder.append("-------------\n");
			for (Map.Entry<String, String> entry : capturedUrls.entrySet()) {
				builder.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		Files.writeString(evidenceDirectory.resolve("final-report.txt"), builder.toString());
	}

	private int readTimeoutSeconds() {
		final String configuredTimeout = System.getenv("SALEADS_TIMEOUT_SECONDS");
		if (configuredTimeout == null || configuredTimeout.isBlank()) {
			return 30;
		}

		try {
			return Integer.parseInt(configuredTimeout.trim());
		} catch (NumberFormatException ignored) {
			return 30;
		}
	}

	private WebDriver buildDriver() {
		final String browser = System.getenv().getOrDefault("SALEADS_BROWSER", "chrome").trim().toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "false"));

		if ("firefox".equals(browser)) {
			final FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			return new FirefoxDriver(options);
		}

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-dev-shm-usage", "--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}
		return new ChromeDriver(options);
	}

	private String slugify(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run();
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
