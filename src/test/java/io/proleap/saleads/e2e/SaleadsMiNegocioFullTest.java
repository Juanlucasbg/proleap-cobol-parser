package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioFullTest {

	private static final Logger LOG = LoggerFactory.getLogger(SaleadsMiNegocioFullTest.class);

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String LOGIN_FIELD = "Login";
	private static final String MI_NEGOCIO_MENU_FIELD = "Mi Negocio menu";
	private static final String AGREGAR_MODAL_FIELD = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_FIELD = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL_FIELD = "Información General";
	private static final String DETALLES_CUENTA_FIELD = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS_FIELD = "Tus Negocios";
	private static final String TERMINOS_FIELD = "Términos y Condiciones";
	private static final String PRIVACIDAD_FIELD = "Política de Privacidad";

	private final Map<String, StepResult> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor javascriptExecutor;
	private Path evidenceDirectory;
	private String startUrl;
	private String terminosUrl = "N/A";
	private String privacidadUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(readConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"SaleADS E2E test is disabled. Set SALEADS_E2E_ENABLED=true or -Dsaleads.e2e.enabled=true to run it.",
				enabled);

		startUrl = readConfig("saleads.start.url", "SALEADS_START_URL", "");
		final String remoteUrl = readConfig("saleads.selenium.remote.url", "SELENIUM_REMOTE_URL", "");
		final boolean headless = Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"));

		driver = createDriver(remoteUrl, headless);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		javascriptExecutor = (JavascriptExecutor) driver;
		evidenceDirectory = createEvidenceDirectory();
		LOG.info("Evidence will be stored in {}", evidenceDirectory.toAbsolutePath());

		if (!startUrl.isBlank()) {
			driver.get(startUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() {
		printFinalReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		initializeReport();

		final boolean loginOk = runStep(LOGIN_FIELD, this::stepLoginWithGoogle);
		final boolean miNegocioMenuOk = runDependentStep(loginOk, MI_NEGOCIO_MENU_FIELD, this::stepOpenMiNegocioMenu,
				"Login step did not complete successfully.");
		final boolean agregarModalOk = runDependentStep(miNegocioMenuOk, AGREGAR_MODAL_FIELD,
				this::stepValidateAgregarNegocioModal, "Mi Negocio menu did not expand successfully.");
		final boolean administrarOk = runDependentStep(miNegocioMenuOk, ADMINISTRAR_NEGOCIOS_FIELD,
				this::stepOpenAdministrarNegocios, "Mi Negocio menu did not expand successfully.");

		runDependentStep(administrarOk, INFORMACION_GENERAL_FIELD, this::stepValidateInformacionGeneral,
				"Administrar Negocios view was not reachable.");
		runDependentStep(administrarOk, DETALLES_CUENTA_FIELD, this::stepValidateDetallesCuenta,
				"Administrar Negocios view was not reachable.");
		runDependentStep(administrarOk, TUS_NEGOCIOS_FIELD, this::stepValidateTusNegocios,
				"Administrar Negocios view was not reachable.");
		runDependentStep(administrarOk, TERMINOS_FIELD, this::stepValidateTerminosYCondiciones,
				"Administrar Negocios view was not reachable.");
		runDependentStep(administrarOk, PRIVACIDAD_FIELD, this::stepValidatePoliticaPrivacidad,
				"Administrar Negocios view was not reachable.");

		final List<String> failures = report.entrySet().stream().filter(entry -> !entry.getValue().isPass())
				.map(entry -> entry.getKey() + " (" + entry.getValue().details + ")").collect(Collectors.toList());

		assertTrue("SaleADS Mi Negocio workflow validations failed: " + failures, failures.isEmpty());
	}

	private String stepLoginWithGoogle() throws IOException {
		if (startUrl.isBlank() && "about:blank".equals(driver.getCurrentUrl())) {
			throw new IllegalStateException(
					"No login URL provided and browser is on about:blank. Use SALEADS_START_URL when not preloaded.");
		}

		clickByVisibleText(Arrays.asList("Sign in with Google", "Login with Google", "Iniciar sesión con Google",
				"Iniciar sesion con Google", "Continuar con Google", "Google"));
		clickByVisibleTextIfPresent("juanlucasbarbiergarzon@gmail.com", Duration.ofSeconds(8));

		waitForAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio"), Duration.ofSeconds(45));
		waitForVisibleElement(By.xpath("//aside | //nav"), Duration.ofSeconds(30));

		takeScreenshot("01-dashboard-loaded");
		return "Main interface loaded and sidebar is visible.";
	}

	private String stepOpenMiNegocioMenu() throws IOException {
		waitForAnyVisibleText(Arrays.asList("Negocio"), Duration.ofSeconds(20));
		clickByVisibleText(Arrays.asList("Mi Negocio"));
		waitForVisibleText("Agregar Negocio", Duration.ofSeconds(20));
		waitForVisibleText("Administrar Negocios", Duration.ofSeconds(20));

		takeScreenshot("02-mi-negocio-expanded-menu");
		return "Mi Negocio menu expanded and expected submenu entries are visible.";
	}

	private String stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText(Arrays.asList("Agregar Negocio"));
		waitForVisibleText("Crear Nuevo Negocio", Duration.ofSeconds(20));
		waitForVisibleText("Nombre del Negocio", Duration.ofSeconds(20));
		waitForVisibleText("Tienes 2 de 3 negocios", Duration.ofSeconds(20));
		waitForVisibleText("Cancelar", Duration.ofSeconds(20));
		waitForVisibleText("Crear Negocio", Duration.ofSeconds(20));

		takeScreenshot("03-agregar-negocio-modal");

		fillInputIfPresent("Nombre del Negocio", "Negocio Prueba Automatización");
		clickByVisibleText(Arrays.asList("Cancelar"));
		waitForTextToDisappear("Crear Nuevo Negocio", Duration.ofSeconds(20));
		return "Agregar Negocio modal rendered expected fields and actions.";
	}

	private String stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(5))) {
			clickByVisibleText(Arrays.asList("Mi Negocio"));
		}

		clickByVisibleText(Arrays.asList("Administrar Negocios"));
		waitForVisibleText("Información General", Duration.ofSeconds(30));
		waitForVisibleText("Detalles de la Cuenta", Duration.ofSeconds(30));
		waitForVisibleText("Tus Negocios", Duration.ofSeconds(30));
		waitForVisibleText("Sección Legal", Duration.ofSeconds(30));

		takeScreenshot("04-administrar-negocios-page");
		return "Administrar Negocios page sections are visible.";
	}

	private String stepValidateInformacionGeneral() {
		final String sectionText = getSectionText("Información General");
		final boolean hasEmail = EMAIL_PATTERN.matcher(sectionText).find();
		final boolean hasName = hasLikelyUserName(sectionText);

		if (!hasEmail) {
			throw new AssertionError("User email not detected in Información General section.");
		}
		if (!hasName) {
			throw new AssertionError("User name not detected in Información General section.");
		}
		if (!isTextVisible("BUSINESS PLAN", Duration.ofSeconds(20))) {
			throw new AssertionError("BUSINESS PLAN text is not visible.");
		}
		if (!isTextVisible("Cambiar Plan", Duration.ofSeconds(20))) {
			throw new AssertionError("Cambiar Plan button is not visible.");
		}

		return "Información General shows user name, email, BUSINESS PLAN, and Cambiar Plan.";
	}

	private String stepValidateDetallesCuenta() {
		final String sectionText = getSectionText("Detalles de la Cuenta");
		assertContains(sectionText, "Cuenta creada");
		assertContains(sectionText, "Estado activo");
		assertContains(sectionText, "Idioma seleccionado");
		return "Detalles de la Cuenta section includes expected account metadata.";
	}

	private String stepValidateTusNegocios() {
		final String sectionText = getSectionText("Tus Negocios");
		assertContains(sectionText, "Agregar Negocio");
		assertContains(sectionText, "Tienes 2 de 3 negocios");

		final long informativeLines = Arrays.stream(sectionText.split("\\R")).map(String::trim)
				.filter(line -> !line.isEmpty()).filter(line -> !"Tus Negocios".equalsIgnoreCase(line))
				.filter(line -> !"Agregar Negocio".equalsIgnoreCase(line)).count();

		if (informativeLines < 2) {
			throw new AssertionError("Business list does not appear to contain visible business entries.");
		}

		return "Tus Negocios section shows business list, add button, and quota text.";
	}

	private String stepValidateTerminosYCondiciones() throws IOException {
		terminosUrl = validateLegalLink("Términos y Condiciones", "05-terminos-y-condiciones");
		return "Legal link validated with URL: " + terminosUrl;
	}

	private String stepValidatePoliticaPrivacidad() throws IOException {
		privacidadUrl = validateLegalLink("Política de Privacidad", "06-politica-de-privacidad");
		return "Legal link validated with URL: " + privacidadUrl;
	}

	private String validateLegalLink(final String linkText, final String screenshotName) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText(Arrays.asList(linkText));

		final String destinationWindow = waitForDestinationWindow(handlesBefore, Duration.ofSeconds(10)).orElse(appWindow);
		driver.switchTo().window(destinationWindow);
		waitForUiToLoad();
		waitForVisibleText(linkText, Duration.ofSeconds(30));

		final String pageText = driver.findElement(By.tagName("body")).getText();
		if (pageText.trim().length() < 100) {
			throw new AssertionError("Expected legal content is not visible for " + linkText + ".");
		}

		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (!destinationWindow.equals(appWindow)) {
			driver.close();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		driver.switchTo().window(appWindow);
		waitForAnyVisibleText(Arrays.asList("Sección Legal", "Información General"), Duration.ofSeconds(20));
		return finalUrl;
	}

	private Optional<String> waitForDestinationWindow(final Set<String> handlesBefore, final Duration timeout) {
		final WebDriverWait windowWait = new WebDriverWait(driver, timeout);
		try {
			return Optional.ofNullable(windowWait.until(d -> {
				final Set<String> currentHandles = d.getWindowHandles();
				if (currentHandles.size() <= handlesBefore.size()) {
					return null;
				}

				for (final String handle : currentHandles) {
					if (!handlesBefore.contains(handle)) {
						return handle;
					}
				}

				return null;
			}));
		} catch (final TimeoutException timeoutException) {
			return Optional.empty();
		}
	}

	private WebDriver createDriver(final String remoteUrl, final boolean headless) {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (headless) {
			options.addArguments("--headless=new");
		}

		if (!remoteUrl.isBlank()) {
			try {
				return new RemoteWebDriver(new URL(remoteUrl), options);
			} catch (final MalformedURLException malformedURLException) {
				throw new IllegalArgumentException("Invalid SELENIUM_REMOTE_URL: " + remoteUrl, malformedURLException);
			}
		}

		WebDriverManager.chromedriver().setup();
		return new ChromeDriver(options);
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		return Files.createDirectories(Path.of("target", "saleads-evidence", timestamp));
	}

	private void clickByVisibleText(final List<String> texts) {
		final List<String> attemptedTexts = new ArrayList<>();

		for (final String text : texts) {
			attemptedTexts.add(text);
			final Optional<WebElement> candidate = findElementByVisibleText(text, Duration.ofSeconds(8));

			if (candidate.isPresent()) {
				clickElement(candidate.get());
				waitForUiToLoad();
				return;
			}
		}

		throw new NoSuchElementException("Could not locate clickable element for texts: " + attemptedTexts);
	}

	private void clickByVisibleTextIfPresent(final String text, final Duration timeout) {
		final Optional<WebElement> candidate = findElementByVisibleText(text, timeout);

		if (candidate.isPresent()) {
			clickElement(candidate.get());
			waitForUiToLoad();
		}
	}

	private Optional<WebElement> findElementByVisibleText(final String text, final Duration timeout) {
		final String literal = xPathLiteral(text);
		final List<By> locators = Arrays.asList(
				By.xpath("//button[contains(normalize-space(.), " + literal + ")]"),
				By.xpath("//a[contains(normalize-space(.), " + literal + ")]"),
				By.xpath("//*[@role='button' and contains(normalize-space(.), " + literal + ")]"),
				By.xpath("//*[contains(normalize-space(.), " + literal + ")]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"),
				By.xpath("//*[contains(normalize-space(.), " + literal + ")]"));

		for (final By locator : locators) {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);

			try {
				final WebElement found = shortWait.until(d -> {
					final List<WebElement> elements = d.findElements(locator);

					for (final WebElement element : elements) {
						if (element.isDisplayed()) {
							return element;
						}
					}

					return null;
				});

				if (found != null) {
					return Optional.of(found);
				}
			} catch (final TimeoutException timeoutException) {
				// Continue searching with next locator strategy.
			}
		}

		return Optional.empty();
	}

	private void fillInputIfPresent(final String labelText, final String value) {
		final Optional<WebElement> input = findInputByLabel(labelText, Duration.ofSeconds(8));

		if (input.isPresent()) {
			final WebElement field = input.get();
			field.click();
			field.sendKeys(Keys.chord(Keys.CONTROL, "a"));
			field.sendKeys(value);
			waitForUiToLoad();
		}
	}

	private Optional<WebElement> findInputByLabel(final String labelText, final Duration timeout) {
		final String literal = xPathLiteral(labelText);
		final List<By> locators = Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), " + literal + ")]/following::input[1]"),
				By.xpath("//input[@placeholder and contains(@placeholder, " + literal + ")]"),
				By.xpath("//input[contains(@aria-label, " + literal + ")]"));

		for (final By locator : locators) {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			try {
				final WebElement element = shortWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return Optional.of(element);
			} catch (final TimeoutException timeoutException) {
				// Continue searching with next locator strategy.
			}
		}

		return Optional.empty();
	}

	private WebElement waitForVisibleText(final String text, final Duration timeout) {
		final String literal = xPathLiteral(text);
		final By locator = By.xpath("//*[contains(normalize-space(.), " + literal + ")]");
		return waitForVisibleElement(locator, timeout);
	}

	private WebElement waitForVisibleElement(final By locator, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void waitForAnyVisibleText(final List<String> texts, final Duration timeout) {
		final long deadline = System.nanoTime() + timeout.toNanos();

		while (System.nanoTime() < deadline) {
			for (final String text : texts) {
				if (isTextVisible(text, Duration.ofSeconds(1))) {
					return;
				}
			}
		}

		throw new TimeoutException("Timed out waiting for any text to become visible: " + texts);
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			waitForVisibleText(text, timeout);
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void waitForTextToDisappear(final String text, final Duration timeout) {
		final String literal = xPathLiteral(text);
		final By locator = By.xpath("//*[contains(normalize-space(.), " + literal + ")]");
		new WebDriverWait(driver, timeout).until(ExpectedConditions.invisibilityOfElementLocated(locator));
	}

	private void clickElement(final WebElement element) {
		wait.until(ExpectedConditions.visibilityOf(element));
		javascriptExecutor.executeScript("arguments[0].scrollIntoView({block:'center'});", element);

		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception exception) {
			javascriptExecutor.executeScript("arguments[0].click();", element);
		}
	}

	private String getSectionText(final String sectionHeading) {
		final String literal = xPathLiteral(sectionHeading);
		final By sectionLocator = By.xpath(
				"//section[.//*[contains(normalize-space(.), " + literal + ")]] | //div[.//*[contains(normalize-space(.), "
						+ literal + ")] and (.//h1 or .//h2 or .//h3 or .//h4)]");

		try {
			final WebElement section = waitForVisibleElement(sectionLocator, Duration.ofSeconds(20));
			return section.getText();
		} catch (final TimeoutException timeoutException) {
			throw new AssertionError("Section not found: " + sectionHeading, timeoutException);
		}
	}

	private boolean hasLikelyUserName(final String sectionText) {
		final List<String> lines = Arrays.stream(sectionText.split("\\R")).map(String::trim).filter(line -> !line.isEmpty())
				.collect(Collectors.toList());

		for (final String line : lines) {
			if (line.equalsIgnoreCase("Información General") || line.equalsIgnoreCase("BUSINESS PLAN")
					|| line.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}

			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}

			if (line.length() >= 3) {
				return true;
			}
		}

		return false;
	}

	private void assertContains(final String actual, final String expected) {
		if (actual == null || !actual.contains(expected)) {
			throw new AssertionError("Expected to find text \"" + expected + "\" but it was not present.");
		}
	}

	private void waitForUiToLoad() {
		wait.until(driverInstance -> "complete".equals(javascriptExecutor.executeScript("return document.readyState")));

		try {
			new WebDriverWait(driver, Duration.ofSeconds(2)).until(driverInstance -> driver
					.findElements(By.xpath(
							"//*[contains(@class,'loading') or contains(@class,'spinner') or contains(@aria-busy,'true')]"))
					.stream().noneMatch(WebElement::isDisplayed));
		} catch (final TimeoutException timeoutException) {
			// Best-effort wait for transient loading indicators.
		}
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final String safeName = checkpointName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-");
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDirectory.resolve(safeName + ".png");
		Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		LOG.info("Screenshot captured at {}", target.toAbsolutePath());
	}

	private void initializeReport() {
		report.clear();
		report.put(LOGIN_FIELD, StepResult.pending());
		report.put(MI_NEGOCIO_MENU_FIELD, StepResult.pending());
		report.put(AGREGAR_MODAL_FIELD, StepResult.pending());
		report.put(ADMINISTRAR_NEGOCIOS_FIELD, StepResult.pending());
		report.put(INFORMACION_GENERAL_FIELD, StepResult.pending());
		report.put(DETALLES_CUENTA_FIELD, StepResult.pending());
		report.put(TUS_NEGOCIOS_FIELD, StepResult.pending());
		report.put(TERMINOS_FIELD, StepResult.pending());
		report.put(PRIVACIDAD_FIELD, StepResult.pending());
	}

	private boolean runDependentStep(final boolean dependencyOk, final String reportField, final WorkflowStep step,
			final String skipReason) {
		if (!dependencyOk) {
			report.put(reportField, StepResult.fail("SKIPPED: " + skipReason));
			return false;
		}

		return runStep(reportField, step);
	}

	private boolean runStep(final String reportField, final WorkflowStep step) {
		try {
			final String details = step.execute();
			report.put(reportField, StepResult.pass(details));
			return true;
		} catch (final Exception exception) {
			try {
				takeScreenshot("failure-" + reportField.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-"));
			} catch (final IOException ioException) {
				LOG.warn("Could not capture failure screenshot for {}", reportField, ioException);
			}

			report.put(reportField, StepResult.fail(exception.getMessage()));
			LOG.error("Step '{}' failed", reportField, exception);
			return false;
		}
	}

	private String readConfig(final String propertyKey, final String envKey, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		return defaultValue;
	}

	private String xPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final String[] parts = text.split("'");

		for (int index = 0; index < parts.length; index++) {
			builder.append("'").append(parts[index]).append("'");
			if (index != parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}

		builder.append(")");
		return builder.toString();
	}

	private void printFinalReport() {
		if (report.isEmpty()) {
			return;
		}

		LOG.info("============== SaleADS Mi Negocio Final Report ==============");
		report.forEach((field, result) -> LOG.info("{} -> {} ({})", field, result.status, result.details));
		LOG.info("Términos y Condiciones URL: {}", terminosUrl);
		LOG.info("Política de Privacidad URL: {}", privacidadUrl);
		LOG.info("==============================================================");
	}

	@FunctionalInterface
	private interface WorkflowStep {
		String execute() throws Exception;
	}

	private static final class StepResult {
		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details;
		}

		private static StepResult pending() {
			return new StepResult("FAIL", "NOT RUN");
		}

		private static StepResult pass(final String details) {
			return new StepResult("PASS", details);
		}

		private static StepResult fail(final String details) {
			return new StepResult("FAIL", details == null ? "No details available." : details);
		}

		private boolean isPass() {
			return "PASS".equals(status);
		}
	}
}
