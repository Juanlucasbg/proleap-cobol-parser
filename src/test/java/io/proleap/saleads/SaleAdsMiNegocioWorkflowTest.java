package io.proleap.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleAdsMiNegocioWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter EVIDENCE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String termsFinalUrl = "";
	private String privacyFinalUrl = "";

	@Before
	public void setUp() throws Exception {
		evidenceDir = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(EVIDENCE_TS));
		Files.createDirectories(evidenceDir);

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,2200");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);
	}

	@After
	public void tearDown() throws Exception {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", "").trim();
		if (loginUrl.isEmpty()) {
			fail("Missing SaleADS login URL. Provide -Dsaleads.login.url or SALEADS_LOGIN_URL.");
		}

		driver.get(loginUrl);
		waitForUiToLoad();

		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final Path reportPath = writeFinalReport();
		System.out.println("SaleADS report written to: " + reportPath.toAbsolutePath());
		failIfAnyStepFailed();
	}

	private void stepLoginWithGoogle() throws Exception {
		clickUsingVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");

		final String googleAccount = readConfig("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT",
				"juanlucasbarbiergarzon@gmail.com");
		clickIfVisible(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(googleAccount) + ")]"), SHORT_TIMEOUT);

		waitForAnyVisible(DEFAULT_TIMEOUT, By.xpath("//aside"), By.xpath("//nav"),
				By.xpath("//*[contains(normalize-space(.),'Negocio')]"));

		final List<WebElement> sidebarCandidates = driver
				.findElements(By.xpath("//aside | //nav[.//*[contains(normalize-space(.),'Negocio')]]"));
		assertTrue("Left sidebar navigation is not visible.", sidebarCandidates.stream().anyMatch(WebElement::isDisplayed));

		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickIfVisible(byVisibleText("Negocio"), SHORT_TIMEOUT);
		clickUsingVisibleText("Mi Negocio");

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickUsingVisibleText("Agregar Negocio");

		waitForVisibleText("Crear Nuevo Negocio");
		waitForVisibleText("Nombre del Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final List<WebElement> nameInputs = driver.findElements(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='Nombre del Negocio' or @aria-label='Nombre del Negocio']"));
		if (!nameInputs.isEmpty() && nameInputs.get(0).isDisplayed()) {
			nameInputs.get(0).click();
			nameInputs.get(0).clear();
			nameInputs.get(0).sendKeys("Negocio Prueba Automatizacion");
			waitForUiToLoad();
		}

		clickUsingVisibleText("Cancelar");
		waitForInvisibility(By.xpath("//*[contains(normalize-space(.),'Crear Nuevo Negocio')]"));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		clickIfVisible(byVisibleText("Mi Negocio"), SHORT_TIMEOUT);
		clickUsingVisibleText("Administrar Negocios");

		waitForVisibleText("Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal");
		takeFullPageScreenshot("04-administrar-negocios-view-full");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		final WebElement section = getSectionContainer("Información General");

		final boolean emailVisible = section.findElements(By.xpath(".//*[contains(text(),'@')]")).stream()
				.map(WebElement::getText).map(String::trim).anyMatch(text -> EMAIL_PATTERN.matcher(text).find());
		assertTrue("User email is not visible in Información General.", emailVisible);

		final boolean userNameVisible = section
				.findElements(By.xpath(".//*[self::h1 or self::h2 or self::h3 or self::h4 or self::p or self::span]"))
				.stream().map(WebElement::getText).map(String::trim)
				.anyMatch(text -> !text.isEmpty() && !text.contains("@") && !text.equals("Información General")
						&& !text.equals("BUSINESS PLAN") && !text.equals("Cambiar Plan"));
		assertTrue("User name is not visible in Información General.", userNameVisible);

		waitForVisibleTextInside(section, "BUSINESS PLAN");
		waitForVisibleTextInside(section, "Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = getSectionContainer("Detalles de la Cuenta");
		waitForVisibleTextInside(section, "Cuenta creada");
		waitForVisibleTextInside(section, "Estado activo");
		waitForVisibleTextInside(section, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = getSectionContainer("Tus Negocios");
		waitForVisibleTextInside(section, "Agregar Negocio");
		waitForVisibleTextInside(section, "Tienes 2 de 3 negocios");

		final long contentRows = section
				.findElements(By.xpath(".//*[self::li or self::tr or self::article or self::div or self::span]"))
				.stream().map(WebElement::getText).map(String::trim)
				.filter(text -> !text.isEmpty() && !text.equals("Tus Negocios") && !text.equals("Agregar Negocio")
						&& !text.contains("Tienes 2 de 3 negocios"))
				.count();
		assertTrue("Business list is not visible in Tus Negocios.", contentRows > 0);
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		termsFinalUrl = validateLegalNavigation("Términos y Condiciones", "08-terminos-y-condiciones",
				"Términos y Condiciones", "Terminos y Condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		privacyFinalUrl = validateLegalNavigation("Política de Privacidad", "09-politica-privacidad",
				"Política de Privacidad", "Politica de Privacidad");
	}

	private String validateLegalNavigation(final String linkText, final String screenshotName,
			final String... headingAlternatives) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickUsingVisibleText(linkText);

		String legalWindow = appWindow;
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT)
					.until(webDriver -> webDriver.getWindowHandles().size() > handlesBefore.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					legalWindow = handle;
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			legalWindow = appWindow;
		}

		driver.switchTo().window(legalWindow);
		waitForUiToLoad();

		final List<By> headingLocators = new ArrayList<>();
		for (final String heading : headingAlternatives) {
			headingLocators.add(By.xpath("//*[self::h1 or self::h2 or self::h3][contains(normalize-space(.),"
					+ xpathLiteral(heading) + ")]"));
		}
		waitForAnyVisible(DEFAULT_TIMEOUT, headingLocators.toArray(new By[0]));

		final WebElement legalBody = wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[self::main or self::article or self::section or self::div][string-length(normalize-space(.)) > 100]")));
		assertTrue("Legal content text is not visible for " + linkText + ".", legalBody.isDisplayed());

		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (!legalWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();
		waitForVisibleText("Sección Legal");

		return finalUrl;
	}

	private void runStep(final String stepName, final ThrowingRunnable action) {
		try {
			action.run();
			stepResults.put(stepName, new StepResult(true, "PASS"));
		} catch (final Throwable e) {
			stepResults.put(stepName, new StepResult(false, safeMessage(e)));
			try {
				takeScreenshot("error-" + stepName.toLowerCase().replace(" ", "-"));
			} catch (final Exception ignored) {
				// Ignore screenshot failures in error handling path.
			}
		}
	}

	private Path writeFinalReport() throws IOException {
		final List<String> orderedFields = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Términos y Condiciones", "Política de Privacidad");

		final StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio Workflow - Final Report\n");
		report.append("=========================================\n\n");

		for (final String field : orderedFields) {
			final StepResult result = stepResults.getOrDefault(field, new StepResult(false, "NOT_EXECUTED"));
			report.append("- ").append(field).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (!result.passed) {
				report.append(" (").append(result.message).append(")");
			}
			report.append('\n');
		}

		report.append('\n');
		report.append("Final URL - Términos y Condiciones: ").append(termsFinalUrl.isEmpty() ? "N/A" : termsFinalUrl)
				.append('\n');
		report.append("Final URL - Política de Privacidad: ").append(privacyFinalUrl.isEmpty() ? "N/A" : privacyFinalUrl)
				.append('\n');
		report.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
		System.out.println(report);
		return reportPath;
	}

	private String summarizeFailures() {
		final StringBuilder failures = new StringBuilder();
		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			if (!entry.getValue().passed) {
				failures.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().message).append('\n');
			}
		}
		return failures.toString();
	}

	private void failIfAnyStepFailed() {
		final String summary = summarizeFailures();
		if (!summary.isEmpty()) {
			fail("Workflow validation failed.\n" + summary);
		}
	}

	private void clickUsingVisibleText(final String... labels) {
		final By locator = byVisibleText(labels);
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));

		try {
			element.click();
		} catch (final Exception clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiToLoad();
	}

	private void clickIfVisible(final By locator, final Duration timeout) {
		try {
			final WebElement element = new WebDriverWait(driver, timeout)
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			try {
				element.click();
			} catch (final Exception clickFailure) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			}
			waitForUiToLoad();
		} catch (final TimeoutException ignored) {
			// Optional click target was not present.
		}
	}

	private void waitForVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]")));
	}

	private void waitForVisibleTextInside(final WebElement container, final String text) {
		wait.until(driverRef -> container
				.findElements(By.xpath(".//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]")).stream()
				.anyMatch(WebElement::isDisplayed));
	}

	private WebElement getSectionContainer(final String sectionTitle) {
		final WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(.),"
						+ xpathLiteral(sectionTitle) + ")]")));

		WebElement current = heading;
		for (int i = 0; i < 5; i++) {
			try {
				final WebElement parent = current.findElement(By.xpath(".."));
				if (parent.getText() != null && parent.getText().contains(sectionTitle) && parent.getText().length() > 20) {
					current = parent;
				} else {
					break;
				}
			} catch (final StaleElementReferenceException | org.openqa.selenium.NoSuchElementException e) {
				break;
			}
		}
		return current;
	}

	private void waitForInvisibility(final By locator) {
		wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
	}

	private void waitForUiToLoad() {
		wait.until(driverRef -> "complete"
				.equals(((JavascriptExecutor) driverRef).executeScript("return document.readyState")));
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(
					"//*[contains(@class,'loading') or contains(@class,'spinner') or @role='progressbar']")));
		} catch (final TimeoutException ignored) {
			// Continue even if explicit spinner markers are not present.
		}
	}

	private WebElement waitForAnyVisible(final Duration timeout, final By... locators) {
		return new WebDriverWait(driver, timeout).until(driverRef -> {
			for (final By locator : locators) {
				final List<WebElement> elements = driverRef.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final String safeName = checkpointName.toLowerCase().replaceAll("[^a-z0-9._-]", "-");
		final Path screenshotPath = evidenceDir.resolve(safeName + ".png");
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshotPath, screenshot);
	}

	private void takeFullPageScreenshot(final String checkpointName) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final Long width = ((Number) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);"))
					.longValue();
			final Long height = ((Number) ((JavascriptExecutor) driver).executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);")).longValue();
			final int targetWidth = Math.max(1280, width.intValue() + 120);
			final int targetHeight = Math.max(2200, Math.min(12000, height.intValue() + 120));
			driver.manage().window().setSize(new Dimension(targetWidth, targetHeight));
			waitForUiToLoad();
			takeScreenshot(checkpointName);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}
	}

	private By byVisibleText(final String... labels) {
		final StringBuilder xpath = new StringBuilder();
		xpath.append("//*[(self::button or self::a or @role='button' or self::span or self::div or self::li)");
		xpath.append(" and (");
		for (int i = 0; i < labels.length; i++) {
			if (i > 0) {
				xpath.append(" or ");
			}
			xpath.append("contains(normalize-space(.),").append(xpathLiteral(labels[i])).append(")");
		}
		xpath.append(")]");
		return By.xpath(xpath.toString());
	}

	private String readConfig(final String systemProperty, final String environmentVariable, final String fallback) {
		final String value = System.getProperty(systemProperty);
		if (value != null && !value.trim().isEmpty()) {
			return value;
		}

		final String envValue = System.getenv(environmentVariable);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue;
		}
		return fallback;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private String safeMessage(final Throwable e) {
		final String message = e.getMessage();
		return (message == null || message.trim().isEmpty()) ? e.getClass().getSimpleName() : message;
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static class StepResult {
		private final boolean passed;
		private final String message;

		private StepResult(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message;
		}
	}
}
