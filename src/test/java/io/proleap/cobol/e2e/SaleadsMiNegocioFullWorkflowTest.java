package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String evidenceDirDisplay = "";
	private final Map<String, StepOutcome> stepOutcomes = new LinkedHashMap<>();
	private final Map<String, String> checkpointScreenshots = new LinkedHashMap<>();
	private final Map<String, String> finalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(
				readConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Set saleads.e2e.enabled=true (or SALEADS_E2E_ENABLED=true) to run this E2E test.",
				enabled);

		final String startUrl = readConfig("saleads.start.url", "SALEADS_START_URL", "").trim();
		Assume.assumeTrue(
				"Set saleads.start.url (or SALEADS_START_URL) to the SaleADS login page of the current environment.",
				!startUrl.isEmpty());

		final boolean headless = Boolean.parseBoolean(
				readConfig("saleads.e2e.headless", "SALEADS_E2E_HEADLESS", "true"));
		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));

		final String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT));
		evidenceDir = Paths.get("target", "saleads-evidence", stamp);
		Files.createDirectories(evidenceDir);
		evidenceDirDisplay = evidenceDir.toAbsolutePath().toString();

		driver.get(startUrl);
		waitForUiToSettle();
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String googleAccountEmail = readConfig("saleads.google.email", "SALEADS_GOOGLE_EMAIL",
				DEFAULT_GOOGLE_ACCOUNT).trim();

		runStep("Login", () -> loginWithGoogle(googleAccountEmail));
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegocios);
		runStep("Información General", () -> validateInformacionGeneral(googleAccountEmail));
		runStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones",
				() -> validateLegalDocument("Términos y Condiciones", "Términos y Condiciones"));
		runStep("Política de Privacidad", () -> validateLegalDocument("Política de Privacidad", "Política de Privacidad"));

		final Path reportFile = writeFinalReport();
		final List<String> failures = stepOutcomes.entrySet().stream().filter(e -> !e.getValue().passed)
				.map(Map.Entry::getKey).collect(Collectors.toList());
		Assert.assertTrue("Workflow has failing steps: " + failures + ". Report: " + reportFile.toAbsolutePath(),
				failures.isEmpty());
	}

	@After
	public void tearDown() throws IOException {
		try {
			if (driver != null) {
				driver.quit();
			}
		} finally {
			if (!stepOutcomes.isEmpty()) {
				writeFinalReport();
			}
		}
	}

	private void loginWithGoogle(final String googleAccountEmail) throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Acceder con Google", "Google");

		final String popupHandle = waitForNewWindowHandle(handlesBefore, Duration.ofSeconds(20));
		if (popupHandle != null) {
			driver.switchTo().window(popupHandle);
			waitForUiToSettle();
		}

		clickByVisibleTextIfPresent(googleAccountEmail);

		if (!driver.getWindowHandle().equals(originalHandle)) {
			wait.until(d -> d.getWindowHandles().contains(originalHandle));
			driver.switchTo().window(originalHandle);
		}

		assertAnyTextVisible("Negocio", "Mi Negocio");
		assertAnyElementVisible(By.tagName("aside"), By.xpath("//nav"));
		checkpointScreenshots.put("Dashboard", captureScreenshot("01-dashboard-loaded"));
	}

	private void openMiNegocioMenu() throws IOException {
		clickByVisibleTextIfPresent("Negocio");
		clickByVisibleText("Mi Negocio");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		checkpointScreenshots.put("Mi Negocio menu", captureScreenshot("02-mi-negocio-menu-expanded"));
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		checkpointScreenshots.put("Agregar Negocio modal", captureScreenshot("03-agregar-negocio-modal"));

		final WebElement input = findFirstVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or @aria-label='Nombre del Negocio' or @name='businessName']"));
		if (input != null) {
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
		}

		clickByVisibleText("Cancelar");
	}

	private void openAdministrarNegocios() throws IOException {
		clickByVisibleTextIfPresent("Mi Negocio");
		clickByVisibleText("Administrar Negocios");
		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertAnyTextVisible("Sección Legal", "Términos y Condiciones");
		checkpointScreenshots.put("Administrar Negocios", captureScreenshot("04-administrar-negocios"));
	}

	private void validateInformacionGeneral(final String googleAccountEmail) {
		assertTextVisible("Información General");
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
		assertTextVisible(googleAccountEmail);

		final WebElement section = findSectionByHeading("Información General");
		Assert.assertNotNull("No se encontró la sección Información General.", section);
		final String sectionText = section.getText().replace(googleAccountEmail, "").replace("Información General", "")
				.replace("BUSINESS PLAN", "").replace("Cambiar Plan", "").trim();
		Assert.assertTrue("Expected a visible user name in Información General.", sectionText.length() > 2);
	}

	private void validateDetallesDeLaCuenta() {
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Cuenta creada");
		assertAnyTextVisible("Estado activo", "Estado Activo");
		assertAnyTextVisible("Idioma seleccionado", "Idioma Seleccionado");
	}

	private void validateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");

		final WebElement section = findSectionByHeading("Tus Negocios");
		Assert.assertNotNull("No se encontró la sección Tus Negocios.", section);
		final String sectionText = section.getText().replace("Tus Negocios", "").replace("Agregar Negocio", "")
				.replace("Tienes 2 de 3 negocios", "").trim();
		Assert.assertTrue("Expected visible business list content in Tus Negocios.", sectionText.length() > 2);
	}

	private void validateLegalDocument(final String linkText, final String expectedHeading) throws IOException {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String startingUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		final String newHandle = waitForNewWindowHandle(handlesBefore, Duration.ofSeconds(10));
		final boolean openedNewTab = newHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(newHandle);
			waitForUiToSettle();
		} else {
			wait.until(d -> !Objects.equals(d.getCurrentUrl(), startingUrl) || isTextVisible(expectedHeading));
		}

		assertTextVisible(expectedHeading);
		final String bodyText = driver.findElement(By.tagName("body")).getText().trim();
		Assert.assertTrue("Expected legal content text on " + expectedHeading + " page.", bodyText.length() > 120);

		checkpointScreenshots.put(linkText, captureScreenshot(normalizeFileName("05-" + linkText)));
		finalUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToSettle();
	}

	private void runStep(final String stepName, final CheckedRunnable action) {
		try {
			action.run();
			stepOutcomes.put(stepName, StepOutcome.pass());
		} catch (final Throwable error) {
			final String failureScreenshot = safeCaptureScreenshot("fail-" + normalizeFileName(stepName));
			stepOutcomes.put(stepName, StepOutcome.fail(error.getClass().getSimpleName() + ": " + error.getMessage(),
					failureScreenshot));
		}
	}

	private Path writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return Paths.get("target", "saleads-evidence", "final-report-unavailable.txt");
		}

		final Path reportFile = evidenceDir.resolve("final-report.txt");
		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("Evidence directory: " + evidenceDirDisplay);
		lines.add("");
		lines.add("PASS/FAIL by validation step:");

		for (final String field : REPORT_FIELDS) {
			final StepOutcome outcome = stepOutcomes.get(field);
			if (outcome == null) {
				lines.add("- " + field + ": FAIL (step did not execute)");
				continue;
			}
			lines.add("- " + field + ": " + (outcome.passed ? "PASS" : "FAIL"));
			if (!outcome.passed && outcome.details != null && !outcome.details.isEmpty()) {
				lines.add("  reason: " + outcome.details);
			}
			if (!outcome.passed && outcome.screenshotPath != null && !outcome.screenshotPath.isEmpty()) {
				lines.add("  screenshot: " + outcome.screenshotPath);
			}
		}

		lines.add("");
		lines.add("Screenshots:");
		for (final Map.Entry<String, String> entry : checkpointScreenshots.entrySet()) {
			lines.add("- " + entry.getKey() + ": " + entry.getValue());
		}

		lines.add("");
		lines.add("Final URLs:");
		for (final Map.Entry<String, String> entry : finalUrls.entrySet()) {
			lines.add("- " + entry.getKey() + ": " + entry.getValue());
		}

		Files.write(reportFile, lines, StandardCharsets.UTF_8);
		System.out.println(String.join(System.lineSeparator(), lines));
		return reportFile;
	}

	private void clickByVisibleText(final String... candidates) {
		final WebElement element = wait.until(d -> findFirstClickableByText(candidates));
		scrollIntoView(element);
		element.click();
		waitForUiToSettle();
	}

	private void clickByVisibleTextIfPresent(final String text) {
		final WebElement element = findFirstClickableByText(text);
		if (element != null) {
			scrollIntoView(element);
			element.click();
			waitForUiToSettle();
		}
	}

	private WebElement findFirstClickableByText(final String... candidates) {
		for (final String candidate : candidates) {
			for (final By by : textLocators(candidate)) {
				final List<WebElement> elements = driver.findElements(by);
				for (final WebElement element : elements) {
					if (element.isDisplayed() && element.isEnabled()) {
						return element;
					}
				}
			}
		}
		return null;
	}

	private boolean isTextVisible(final String text) {
		for (final By by : textLocators(text)) {
			final List<WebElement> elements = driver.findElements(by);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void assertTextVisible(final String text) {
		wait.until(d -> isTextVisible(text));
	}

	private void assertAnyTextVisible(final String... texts) {
		wait.until(d -> {
			for (final String text : texts) {
				if (isTextVisible(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private void assertAnyElementVisible(final By... locators) {
		wait.until(d -> {
			for (final By locator : locators) {
				for (final WebElement element : d.findElements(locator)) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			}
			return false;
		});
	}

	private WebElement findSectionByHeading(final String heading) {
		final String headingLiteral = xpathLiteral(heading);
		final By locator = By.xpath(
				"(//*[self::section or self::div or self::article][.//*[contains(normalize-space(), " + headingLiteral
						+ ")]])[1]");
		return findFirstVisible(locator);
	}

	private WebElement findFirstVisible(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		return null;
	}

	private List<By> textLocators(final String text) {
		final String literal = xpathLiteral(text);
		final List<By> locators = new ArrayList<>();
		locators.add(By.xpath("//*[normalize-space()=" + literal + "]"));
		locators.add(By.xpath(
				"//*[self::a or self::button or @role='button' or self::span or self::div][contains(normalize-space(), "
						+ literal + ")]"));
		return locators;
	}

	private String captureScreenshot(final String name) throws IOException {
		final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final String fileName = normalizeFileName(name) + ".png";
		final Path destination = evidenceDir.resolve(fileName);
		Files.write(destination, bytes);
		return destination.toAbsolutePath().toString();
	}

	private String safeCaptureScreenshot(final String name) {
		try {
			return captureScreenshot(name);
		} catch (final Exception ignored) {
			return "";
		}
	}

	private void waitForUiToSettle() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		waitForLoadingIndicatorsToDisappear();
	}

	private void waitForLoadingIndicatorsToDisappear() {
		final By loadingLocator = By.xpath(
				"//*[contains(translate(@class, 'LOADINGSPNR', 'loadingspnr'), 'loading') or contains(translate(@class, 'SPINNER', 'spinner'), 'spinner') or contains(translate(@class, 'PROGRESS', 'progress'), 'progress') or @aria-busy='true']");
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(ExpectedConditions.invisibilityOfElementLocated(loadingLocator));
		} catch (final TimeoutException ignored) {
			// Some pages keep background loaders mounted; a timeout here is non-fatal.
		}
	}

	private String waitForNewWindowHandle(final Set<String> handlesBefore, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(d -> d.getWindowHandles().size() > handlesBefore.size());
		} catch (final TimeoutException ignored) {
			return null;
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBefore.contains(handle)) {
				return handle;
			}
		}
		return null;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	private String normalizeFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("-{2,}", "-")
				.replaceAll("(^-|-$)", "");
	}

	private String readConfig(final String propertyKey, final String envKey, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue;
		}

		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue;
		}

		return defaultValue;
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private record StepOutcome(boolean passed, String details, String screenshotPath) {
		private static StepOutcome pass() {
			return new StepOutcome(true, "", "");
		}

		private static StepOutcome fail(final String details, final String screenshotPath) {
			return new StepOutcome(false, details, screenshotPath);
		}
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
