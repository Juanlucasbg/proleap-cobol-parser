package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
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
 * End-to-end workflow validation for SaleADS "Mi Negocio".
 *
 * <p>
 * Runtime configuration:
 * <ul>
 * <li>SALEADS_LOGIN_URL (or -Dsaleads.login.url)</li>
 * <li>SALEADS_GOOGLE_ACCOUNT (or -Dsaleads.google.account), default:
 * juanlucasbarbiergarzon@gmail.com</li>
 * <li>SALEADS_EXPECTED_USER_NAME (or -Dsaleads.expected.user.name), optional</li>
 * <li>SALEADS_HEADLESS (or -Dsaleads.headless), default true</li>
 * <li>SALEADS_WAIT_SECONDS (or -Dsaleads.wait.seconds), default 30</li>
 * <li>SALEADS_ARTIFACTS_DIR (or -Dsaleads.artifacts.dir), default
 * target/saleads-mi-negocio-artifacts</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, StepStatus> stepReport = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;
	private String appWindowHandle;
	private String expectedUserName;

	@Before
	public void setUp() throws IOException {
		final boolean headless = Boolean.parseBoolean(config("SALEADS_HEADLESS", "saleads.headless", "true"));
		final long waitSeconds = Long.parseLong(config("SALEADS_WAIT_SECONDS", "saleads.wait.seconds", "30"));

		artifactsDir = Paths
				.get(config("SALEADS_ARTIFACTS_DIR", "saleads.artifacts.dir", "target/saleads-mi-negocio-artifacts"));
		Files.createDirectories(artifactsDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		expectedUserName = config("SALEADS_EXPECTED_USER_NAME", "saleads.expected.user.name", "");
	}

	@After
	public void tearDown() {
		printStepReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String loginUrl = config("SALEADS_LOGIN_URL", "saleads.login.url", "");
		Assume.assumeTrue("SALEADS_LOGIN_URL (or -Dsaleads.login.url) is required to run this E2E workflow.",
				!loginUrl.isBlank());
		final String googleAccount = config("SALEADS_GOOGLE_ACCOUNT", "saleads.google.account", DEFAULT_GOOGLE_ACCOUNT);

		driver.get(loginUrl);
		waitForUiLoad();

		runStep("Login", () -> loginWithGoogle(googleAccount));
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegocios);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", this::validateTerminosYCondiciones);
		runStep("Política de Privacidad", this::validatePoliticaPrivacidad);

		assertNoFailedSteps();
	}

	private String loginWithGoogle(final String googleAccount) {
		appWindowHandle = driver.getWindowHandle();
		final Set<String> windowsBeforeLoginClick = driver.getWindowHandles();

		clickByVisibleTextCandidates("Sign in with Google", "Iniciar sesión con Google", "Acceder con Google",
				"Continuar con Google", "Ingresar con Google", "Google");
		waitForUiLoad();

		switchToNewWindowIfPresent(windowsBeforeLoginClick);
		selectGoogleAccountIfVisible(googleAccount);
		waitForAppShell();

		assertAnyVisible("Expected main interface after login.", By.cssSelector("aside"), By.cssSelector("nav"),
				By.xpath("//*[normalize-space()='Negocio']"), By.xpath("//*[normalize-space()='Mi Negocio']"));
		saveScreenshot("01-dashboard-loaded");
		return "Dashboard loaded";
	}

	private String openMiNegocioMenu() {
		clickIfVisibleText("Negocio");
		clickByVisibleTextCandidates("Mi Negocio");

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		saveScreenshot("02-mi-negocio-menu-expanded");
		return "Mi Negocio submenu expanded";
	}

	private String validateAgregarNegocioModal() {
		clickByVisibleTextCandidates("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");

		assertAnyVisible("Field 'Nombre del Negocio' should exist.",
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"));
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		saveScreenshot("03-agregar-negocio-modal");

		final WebElement nameField = firstVisible(
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"));
		nameField.click();
		nameField.clear();
		nameField.sendKeys("Negocio Prueba Automatización");
		clickByVisibleTextCandidates("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']")));
		return "Agregar Negocio modal validated";
	}

	private String openAdministrarNegocios() {
		clickIfVisibleText("Negocio");
		clickIfVisibleText("Mi Negocio");
		clickByVisibleTextCandidates("Administrar Negocios");

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		saveScreenshot("04-administrar-negocios");
		return "Account page loaded";
	}

	private String validateInformacionGeneral() {
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
		assertAnyVisible("Expected user email in Información General section.",
				By.xpath("//*[contains(text(),'@') and not(contains(@href,'mailto'))]"));
		assertUserNameVisible();
		return "Información General section validated";
	}

	private String validateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
		return "Detalles de la Cuenta section validated";
	}

	private String validateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");

		final WebElement section = firstVisible(By.xpath(
				"//*[normalize-space()='Tus Negocios']/ancestor::*[self::section or self::div][1]"),
				By.xpath("//*[normalize-space()='Tus Negocios']/following::*[self::section or self::div][1]"));
		final List<WebElement> businessEntries = section.findElements(By.xpath(
				".//li | .//tr | .//article | .//*[contains(@class,'business')] | .//*[contains(@class,'negocio')]"));
		Assert.assertFalse("Expected business list to be visible in 'Tus Negocios'.", businessEntries.isEmpty());
		return "Tus Negocios section validated";
	}

	private String validateTerminosYCondiciones() {
		final String finalUrl = openLegalLinkAndReturn("Términos y Condiciones", "08-terminos-y-condiciones");
		return "URL: " + finalUrl;
	}

	private String validatePoliticaPrivacidad() {
		final String finalUrl = openLegalLinkAndReturn("Política de Privacidad", "09-politica-de-privacidad");
		return "URL: " + finalUrl;
	}

	private String openLegalLinkAndReturn(final String linkText, final String screenshotName) {
		final String originHandle = driver.getWindowHandle();
		final String originUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleTextCandidates(linkText);

		final String legalHandle = waitForLegalPageHandle(handlesBeforeClick, originHandle, originUrl);
		final boolean openedNewTab = legalHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(legalHandle);
		}

		assertTextVisible(linkText);
		assertLegalContentVisible();
		saveScreenshot(screenshotName);

		final String finalLegalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		assertAnyVisible("Expected to be back in application after legal link validation.",
				By.xpath("//*[normalize-space()='Sección Legal']"), By.xpath("//*[normalize-space()='Mi Negocio']"),
				By.xpath("//*[normalize-space()='Administrar Negocios']"));
		return finalLegalUrl;
	}

	private String waitForLegalPageHandle(final Set<String> handlesBeforeClick, final String originHandle,
			final String originUrl) {
		wait.until(driver -> {
			final Set<String> currentHandles = driver.getWindowHandles();
			final boolean hasNewTab = currentHandles.size() > handlesBeforeClick.size();
			final boolean sameTabNavigated = !originUrl.equals(driver.getCurrentUrl());
			return hasNewTab || sameTabNavigated;
		});

		final Set<String> handlesAfterClick = driver.getWindowHandles();
		for (final String handle : handlesAfterClick) {
			if (!handlesBeforeClick.contains(handle)) {
				return handle;
			}
		}

		if (handlesAfterClick.contains(originHandle)) {
			driver.switchTo().window(originHandle);
		}
		return null;
	}

	private void selectGoogleAccountIfVisible(final String googleAccount) {
		final List<By> selectors = Arrays.asList(
				By.xpath("//*[normalize-space()=" + asXpathLiteral(googleAccount) + "]"),
				By.xpath("//*[contains(@data-email," + asXpathLiteral(googleAccount) + ")]"));

		for (final By selector : selectors) {
			if (isVisible(selector, 8)) {
				click(firstVisible(selector));
				waitForUiLoad();
				return;
			}
		}
	}

	private void switchToNewWindowIfPresent(final Set<String> handlesBeforeClick) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(driver -> driver.getWindowHandles().size() > handlesBeforeClick.size());
		} catch (final TimeoutException timeoutException) {
			// Google selector may render in the same tab, so this is not necessarily a failure.
		}

		final Set<String> handlesAfterClick = driver.getWindowHandles();
		for (final String handle : handlesAfterClick) {
			if (!handlesBeforeClick.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				return;
			}
		}
	}

	private void waitForAppShell() {
		wait.until(driver -> {
			final Set<String> handles = driver.getWindowHandles();
			for (final String handle : handles) {
				driver.switchTo().window(handle);
				if (isVisibleNow(By.cssSelector("aside")) || isVisibleNow(By.xpath("//*[normalize-space()='Negocio']"))
						|| isVisibleNow(By.xpath("//*[normalize-space()='Mi Negocio']"))) {
					appWindowHandle = handle;
					return true;
				}
			}
			return false;
		});

		driver.switchTo().window(appWindowHandle);
		waitForUiLoad();
	}

	private void assertUserNameVisible() {
		if (!expectedUserName.isBlank()) {
			assertTextVisible(expectedUserName);
			return;
		}

		assertAnyVisible(
				"User name should be visible. Configure SALEADS_EXPECTED_USER_NAME for strict validation if needed.",
				By.xpath(
						"//*[normalize-space()='Información General']/following::*[self::h1 or self::h2 or self::h3 or self::p or self::span][normalize-space()!='' and not(contains(text(),'@'))][1]"),
				By.xpath(
						"//*[contains(@class,'name') and normalize-space()!='']"),
				By.xpath(
						"//*[contains(@class,'user') and normalize-space()!='' and not(contains(text(),'@'))]"));
	}

	private void assertLegalContentVisible() {
		assertAnyVisible("Expected legal content text to be visible.",
				By.xpath("//p[string-length(normalize-space()) > 40]"),
				By.xpath("//li[string-length(normalize-space()) > 40]"),
				By.xpath("//div[string-length(normalize-space()) > 80]"));
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			final String details = action.run();
			final String screenshotPath = captureFailureScreenshot(stepName + "-pass");
			stepReport.put(stepName, StepStatus.pass(details, screenshotPath));
		} catch (final Throwable error) {
			String screenshotPath = "";
			try {
				screenshotPath = captureFailureScreenshot(stepName + "-failure");
			} catch (final Throwable screenshotError) {
				screenshotPath = "Failed to capture screenshot: " + screenshotError.getMessage();
			}
			final String details = error.getMessage() == null ? error.toString() : error.getMessage();
			stepReport.put(stepName, StepStatus.fail(details, screenshotPath));
		}
	}

	private void assertNoFailedSteps() {
		final List<String> failed = stepReport.entrySet().stream().filter(entry -> !entry.getValue().pass)
				.map(entry -> entry.getKey() + " -> " + entry.getValue().details).collect(Collectors.toList());
		Assert.assertTrue("SaleADS workflow failed:\n" + String.join("\n", failed), failed.isEmpty());
	}

	private void printStepReport() {
		if (stepReport.isEmpty()) {
			return;
		}

		System.out.println("\n=== SaleADS Mi Negocio final report ===");
		for (final Map.Entry<String, StepStatus> entry : stepReport.entrySet()) {
			final StepStatus status = entry.getValue();
			System.out.println("- " + entry.getKey() + ": " + (status.pass ? "PASS" : "FAIL"));
			if (status.details != null && !status.details.isBlank()) {
				System.out.println("  details: " + status.details);
			}
			if (status.screenshotPath != null && !status.screenshotPath.isBlank()) {
				System.out.println("  screenshot: " + status.screenshotPath);
			}
		}
		System.out.println("=== End report ===\n");
	}

	private void clickByVisibleTextCandidates(final String... candidates) {
		final List<Throwable> failures = new ArrayList<>();
		for (final String candidate : candidates) {
			try {
				clickByVisibleText(candidate);
				return;
			} catch (final Throwable failure) {
				failures.add(failure);
			}
		}

		throw new AssertionError("Could not click any candidate text: " + Arrays.toString(candidates), failures.get(0));
	}

	private void clickByVisibleText(final String text) {
		final String literal = asXpathLiteral(text);
		final WebElement element = firstVisible(By.xpath(
				"//*[normalize-space()=" + literal
						+ " and not(self::script)] | //*[(self::button or self::a or @role='button') and normalize-space()="
						+ literal + "]"));
		click(element);
		waitForUiLoad();
	}

	private void clickIfVisibleText(final String text) {
		final String literal = asXpathLiteral(text);
		final By locator = By.xpath("//*[normalize-space()=" + literal + "]");
		if (isVisible(locator, 2)) {
			click(firstVisible(locator));
			waitForUiLoad();
		}
	}

	private void click(final WebElement element) {
		final WebElement clickable = closestClickable(element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(clickable));
			clickable.click();
		} catch (final Throwable firstFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
		}
	}

	private WebElement closestClickable(final WebElement element) {
		final List<WebElement> clickables = element.findElements(By.xpath(
				"./ancestor-or-self::button | ./ancestor-or-self::a | ./ancestor-or-self::*[@role='button'] | ./ancestor-or-self::*[contains(@class,'clickable')]"));
		return clickables.isEmpty() ? element : clickables.get(0);
	}

	private WebElement firstVisible(final By... locators) {
		TimeoutException lastException = null;
		for (final By locator : locators) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final TimeoutException timeoutException) {
				lastException = timeoutException;
			}
		}
		throw lastException != null ? lastException : new TimeoutException("No visible elements found.");
	}

	private void assertTextVisible(final String text) {
		final String literal = asXpathLiteral(text);
		assertAnyVisible("Expected visible text: " + text, By.xpath("//*[normalize-space()=" + literal + "]"),
				By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
	}

	private void assertAnyVisible(final String message, final By... locators) {
		for (final By locator : locators) {
			if (isVisible(locator, 2)) {
				return;
			}
		}
		throw new AssertionError(message);
	}

	private boolean isVisible(final By locator, final int timeoutSeconds) {
		try {
			return new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(locator)) != null;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private boolean isVisibleNow(final By locator) {
		try {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final NoSuchElementException exception) {
			return false;
		}
	}

	private void waitForUiLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
	}

	private void saveScreenshot(final String name) {
		captureFailureScreenshot(name);
	}

	private String captureFailureScreenshot(final String name) {
		if (!(driver instanceof TakesScreenshot)) {
			return "";
		}

		final String fileName = slug(name) + "-" + System.currentTimeMillis() + ".png";
		final Path screenshotPath = artifactsDir.resolve(fileName);

		try {
			final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			Files.copy(source.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
			return screenshotPath.toAbsolutePath().toString();
		} catch (final IOException ioException) {
			throw new UncheckedIOException(ioException);
		}
	}

	private String slug(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String config(final String envName, final String propertyName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder xpath = new StringBuilder("concat(");
		for (int index = 0; index < parts.length; index++) {
			xpath.append("'").append(parts[index]).append("'");
			if (index < parts.length - 1) {
				xpath.append(", \"'\", ");
			}
		}
		xpath.append(")");
		return xpath.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		String run() throws Exception;
	}

	private static class StepStatus {
		private final boolean pass;
		private final String details;
		private final String screenshotPath;

		private StepStatus(final boolean pass, final String details, final String screenshotPath) {
			this.pass = pass;
			this.details = details;
			this.screenshotPath = screenshotPath;
		}

		private static StepStatus pass(final String details, final String screenshotPath) {
			return new StepStatus(true, details, screenshotPath);
		}

		private static StepStatus fail(final String details, final String screenshotPath) {
			return new StepStatus(false, details, screenshotPath);
		}
	}
}
