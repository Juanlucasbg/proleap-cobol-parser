package io.proleap.cobol.e2e.saleads;

import java.io.File;
import java.io.IOException;
import java.net.URL;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, String> stepResults = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> finalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private String appWindowHandle;
	private Path evidenceDirectory;

	@Before
	public void setUp() throws Exception {
		final boolean runE2E = Boolean.parseBoolean(System.getProperty("saleads.runE2E", "false"));
		Assume.assumeTrue("Set -Dsaleads.runE2E=true to run SaleADS UI workflow.", runE2E);

		final String startUrl = firstNonBlank(System.getProperty("saleads.startUrl"), System.getenv("SALEADS_START_URL"));
		Assume.assumeTrue("Provide -Dsaleads.startUrl=<current-environment-login-url>.", startUrl != null);

		for (final String field : REPORT_FIELDS) {
			stepResults.put(field, "NOT_RUN");
		}

		driver = buildDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		evidenceDirectory = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDirectory);

		driver.get(startUrl);
		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::loginWithGoogle);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegocios);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", this::validateTerminosYCondiciones);
		runStep("Política de Privacidad", this::validatePoliticaDePrivacidad);

		if (!failures.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow failed:\n - " + String.join("\n - ", failures)
					+ "\nEvidence directory: " + evidenceDirectory.toAbsolutePath());
		}
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();

		if (driver != null) {
			driver.quit();
		}
	}

	private void loginWithGoogle() throws IOException {
		clickByVisibleText("Sign in with Google", "Continuar con Google", "Iniciar sesión con Google", "Google");
		waitForUiLoad();
		handleGoogleAccountSelectorIfPresent();

		assertVisibleByText("Negocio");
		assertAnyVisible("main application container",
				By.xpath("//aside | //nav | //*[@role='navigation'] | //*[contains(@class, 'sidebar')]"));

		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws IOException {
		assertVisibleByText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertVisibleByText("Agregar Negocio");
		assertVisibleByText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		assertVisibleByText("Crear Nuevo Negocio");
		assertAnyVisible("Nombre del Negocio input", By.xpath(
				"//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder,'Nombre del Negocio')]"));
		assertVisibleByText("Tienes 2 de 3 negocios");
		assertVisibleByText("Cancelar");
		assertVisibleByText("Crear Negocio");

		final Optional<WebElement> nombreInput = findVisible(Duration.ofSeconds(3), By.xpath(
				"//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder,'Nombre del Negocio')]"));
		if (nombreInput.isPresent()) {
			nombreInput.get().click();
			nombreInput.get().clear();
			nombreInput.get().sendKeys("Negocio Prueba Automatizacion");
		}

		takeScreenshot("03-crear-negocio-modal");
		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(textLocator("Crear Nuevo Negocio")));
		waitForUiLoad();
	}

	private void openAdministrarNegocios() throws IOException {
		if (!isVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"), Duration.ofSeconds(3))) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertVisibleByText("Información General");
		assertVisibleByText("Detalles de la Cuenta");
		assertVisibleByText("Tus Negocios");
		assertVisibleByText("Sección Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		assertVisibleByText("Información General");
		assertAnyVisible("user name value",
				By.xpath(
						"//*[contains(normalize-space(),'Información General')]/following::*[self::h1 or self::h2 or self::h3 or self::p or self::span][normalize-space()!='' and not(contains(normalize-space(),'@')) and not(contains(normalize-space(),'BUSINESS PLAN'))][1]"));
		assertAnyVisible("user email value", By.xpath("//*[contains(normalize-space(),'@')]"));
		assertVisibleByText("BUSINESS PLAN");
		assertVisibleByText("Cambiar Plan");
	}

	private void validateDetallesDeLaCuenta() {
		assertVisibleByText("Detalles de la Cuenta");
		assertVisibleByText("Cuenta creada");
		assertVisibleByText("Estado activo");
		assertVisibleByText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertVisibleByText("Tus Negocios");
		assertVisibleByText("Agregar Negocio");
		assertVisibleByText("Tienes 2 de 3 negocios");
		assertAnyVisible("business list", By.xpath(
				"//*[contains(normalize-space(),'Tus Negocios')]/following::*[(self::li or self::tr or self::div)[normalize-space()!='']][1]"));
	}

	private void validateTerminosYCondiciones() throws IOException {
		final String url = openLegalDocument("Términos y Condiciones", "Términos y Condiciones", "05-terminos-condiciones");
		finalUrls.put("Términos y Condiciones", url);
	}

	private void validatePoliticaDePrivacidad() throws IOException {
		final String url = openLegalDocument("Política de Privacidad", "Política de Privacidad", "06-politica-privacidad");
		finalUrls.put("Política de Privacidad", url);
	}

	private String openLegalDocument(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);
		waitForUiLoad();

		final String newWindow = waitForNewWindow(beforeHandles, Duration.ofSeconds(10)).orElse(null);
		if (newWindow != null) {
			driver.switchTo().window(newWindow);
			waitForUiLoad();
		}

		assertVisibleByText(headingText);
		assertAnyVisible("legal content body",
				By.xpath("//main//*[string-length(normalize-space()) > 40] | //article//*[string-length(normalize-space()) > 40] | //p[string-length(normalize-space()) > 40]"));
		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (newWindow != null) {
			driver.close();
			driver.switchTo().window(originalWindow);
		} else if (!driver.getWindowHandle().equals(originalWindow)) {
			driver.switchTo().window(originalWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiLoad();
		appWindowHandle = originalWindow;
		return finalUrl;
	}

	private void handleGoogleAccountSelectorIfPresent() {
		waitForUiLoad();

		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() > 1) {
			for (final String handle : handles) {
				if (!handle.equals(appWindowHandle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		final Optional<WebElement> accountOption = findVisible(Duration.ofSeconds(10),
				By.xpath("//*[normalize-space()=" + xpathLiteral(GOOGLE_ACCOUNT_EMAIL) + "]"),
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]"));
		accountOption.ifPresent(this::clickElementAndWait);

		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		} else if (!driver.getWindowHandles().isEmpty()) {
			driver.switchTo().window(driver.getWindowHandles().iterator().next());
			appWindowHandle = driver.getWindowHandle();
		}

		wait.until(ExpectedConditions.visibilityOfElementLocated(textLocator("Negocio")));
		waitForUiLoad();
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, "PASS");
		} catch (final Exception e) {
			stepResults.put(stepName, "FAIL");
			failures.add(stepName + ": " + normalizeMessage(e));
		}
	}

	private WebDriver buildDriver() throws Exception {
		final String remoteUrl = firstNonBlank(System.getProperty("saleads.remoteUrl"), System.getenv("SELENIUM_REMOTE_URL"));
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "false"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1200");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		if (remoteUrl != null) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}

		return new ChromeDriver(options);
	}

	private void clickByVisibleText(final String... texts) {
		final List<By> locators = new ArrayList<>();
		for (final String text : texts) {
			locators.add(By.xpath("//*[self::button or self::a or @role='button' or self::span or self::div][normalize-space()="
					+ xpathLiteral(text) + "]"));
			locators.add(By.xpath(
					"//*[self::button or self::a or @role='button' or self::span or self::div][contains(normalize-space(), "
							+ xpathLiteral(text) + ")]"));
		}

		final WebElement clickable = waitForAnyVisible(Duration.ofSeconds(20), locators.toArray(new By[0]));
		clickElementAndWait(clickable);
	}

	private void clickElementAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiLoad();
	}

	private void assertVisibleByText(final String text) {
		assertAnyVisible("text '" + text + "'", textLocator(text));
	}

	private void assertAnyVisible(final String description, final By... locators) {
		try {
			waitForAnyVisible(Duration.ofSeconds(20), locators);
		} catch (final TimeoutException timeoutException) {
			throw new AssertionError("Expected visible element not found: " + description, timeoutException);
		}
	}

	private Optional<WebElement> findVisible(final Duration timeout, final By... locators) {
		try {
			return Optional.of(waitForAnyVisible(timeout, locators));
		} catch (final TimeoutException timeoutException) {
			return Optional.empty();
		}
	}

	private WebElement waitForAnyVisible(final Duration timeout, final By... locators) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		final ExpectedCondition<WebElement> expectedCondition = webDriver -> {
			for (final By locator : locators) {
				final List<WebElement> candidates = webDriver.findElements(locator);
				for (final WebElement candidate : candidates) {
					if (candidate.isDisplayed()) {
						return candidate;
					}
				}
			}
			return null;
		};

		return shortWait.until(expectedCondition);
	}

	private Optional<String> waitForNewWindow(final Set<String> handlesBefore, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			return Optional.ofNullable(shortWait.until(webDriver -> {
				for (final String handle : webDriver.getWindowHandles()) {
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

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			waitForAnyVisible(timeout, locator);
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void waitForUiLoad() {
		wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
	}

	private void takeScreenshot(final String name) throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("HHmmss-SSS").format(LocalDateTime.now());
		final Path screenshotPath = evidenceDirectory.resolve(name + "-" + timestamp + ".png");
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private By textLocator(final String text) {
		return By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "] | //*[contains(normalize-space(), "
				+ xpathLiteral(text) + ")]");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(",\"'\",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String firstNonBlank(final String first, final String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		if (second != null && !second.isBlank()) {
			return second;
		}
		return null;
	}

	private void writeFinalReport() throws IOException {
		if (stepResults.isEmpty() || evidenceDirectory == null) {
			return;
		}

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		reportBuilder.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		reportBuilder.append(System.lineSeparator());
		for (final String field : REPORT_FIELDS) {
			reportBuilder.append(field).append(": ").append(stepResults.getOrDefault(field, "NOT_RUN"))
					.append(System.lineSeparator());
		}
		reportBuilder.append(System.lineSeparator());
		if (!finalUrls.isEmpty()) {
			reportBuilder.append("Final URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : finalUrls.entrySet()) {
				reportBuilder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue())
						.append(System.lineSeparator());
			}
		}

		final Path reportPath = evidenceDirectory.resolve("final-report.txt");
		Files.writeString(reportPath, reportBuilder.toString());
		System.out.println(reportBuilder);
		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
	}

	private String normalizeMessage(final Exception exception) {
		final Throwable cause = exception.getCause();
		if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
			return cause.getMessage();
		}
		if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
			return exception.getMessage();
		}
		return exception.getClass().getSimpleName();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
