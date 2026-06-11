package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowE2ETest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration
			.ofSeconds(Long.parseLong(getValue("saleads.e2e.timeout.seconds", "SALEADS_E2E_TIMEOUT_SECONDS", "45")));
	private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");

	private final Map<String, StepResult> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(getValue("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Set -Dsaleads.e2e.enabled=true (or SALEADS_E2E_ENABLED=true) to run this E2E test.",
				enabled);

		final String loginUrl = getValue("saleads.login.url", "SALEADS_LOGIN_URL", "");
		Assume.assumeTrue("Set -Dsaleads.login.url=<env login URL> (or SALEADS_LOGIN_URL).",
				loginUrl != null && !loginUrl.isBlank());

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		screenshotDirectory = Path.of("target", "saleads-e2e-screenshots");
		Files.createDirectories(screenshotDirectory);

		driver.get(loginUrl);
		appWindowHandle = driver.getWindowHandle();
		waitForUiToSettle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final boolean loginOk = runStep("Login", () -> {
			doLoginWithGoogle();
			captureScreenshot("01-dashboard-loaded");
			return null;
		});

		final boolean menuOk = runStep("Mi Negocio menu", () -> {
			require(loginOk, "Cannot continue when login validation fails.");
			openMiNegocioMenu();
			captureScreenshot("02-mi-negocio-menu-expanded");
			return null;
		});

		final boolean addBusinessOk = runStep("Agregar Negocio modal", () -> {
			require(menuOk, "Cannot validate Agregar Negocio modal when Mi Negocio menu is not available.");
			validateAgregarNegocioModal();
			captureScreenshot("03-agregar-negocio-modal");
			return null;
		});

		final boolean adminViewOk = runStep("Administrar Negocios view", () -> {
			require(loginOk, "Cannot open Administrar Negocios without a logged-in session.");
			openAdministrarNegocios();
			captureScreenshot("04-administrar-negocios-view");
			return null;
		});

		final boolean infoGeneralOk = runStep("Información General", () -> {
			require(adminViewOk, "Cannot validate Información General when account page is unavailable.");
			validateInformacionGeneral();
			return null;
		});

		final boolean accountDetailsOk = runStep("Detalles de la Cuenta", () -> {
			require(adminViewOk, "Cannot validate account details when account page is unavailable.");
			validateDetallesDeLaCuenta();
			return null;
		});

		final boolean businessesOk = runStep("Tus Negocios", () -> {
			require(adminViewOk, "Cannot validate business list when account page is unavailable.");
			validateTusNegocios();
			return null;
		});

		runStep("Términos y Condiciones", () -> {
			require(adminViewOk, "Cannot validate legal links when account page is unavailable.");
			final String termsUrl = openLegalDocument("Términos y Condiciones", "Términos y Condiciones",
					"08-terminos-y-condiciones");
			return "URL final: " + termsUrl;
		});

		runStep("Política de Privacidad", () -> {
			require(adminViewOk, "Cannot validate legal links when account page is unavailable.");
			final String privacyUrl = openLegalDocument("Política de Privacidad", "Política de Privacidad",
					"09-politica-de-privacidad");
			return "URL final: " + privacyUrl;
		});

		// Keep booleans referenced so failures in critical sections remain visible even
		// if later checks are skipped.
		if (!loginOk || !menuOk || !addBusinessOk || !adminViewOk || !infoGeneralOk || !accountDetailsOk || !businessesOk) {
			// no-op
		}

		final String renderedReport = renderReport();
		System.out.println("\n=== SaleADS Mi Negocio workflow report ===\n" + renderedReport);
		Assert.assertTrue("One or more validation steps failed:\n" + renderedReport, allStepsPassed());
	}

	private void doLoginWithGoogle() {
		clickFirstVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Login with Google", "Acceder con Google"));

		selectGoogleAccountIfChooserAppears();
		waitForMainAppInterface();
		assertLeftSidebarVisible();
	}

	private void openMiNegocioMenu() {
		assertLeftSidebarVisible();
		clickIfVisible("Negocio");
		clickFirstVisibleText(List.of("Mi Negocio"));

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
	}

	private void validateAgregarNegocioModal() {
		clickFirstVisibleText(List.of("Agregar Negocio"));

		assertTextVisible("Crear Nuevo Negocio");
		assertElementVisible(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')]"));
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		final WebElement businessName = waitForVisible(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')]"));
		businessName.click();
		businessName.clear();
		businessName.sendKeys("Negocio Prueba Automatización");
		clickFirstVisibleText(List.of("Cancelar"));
	}

	private void openAdministrarNegocios() {
		if (!isTextVisible("Administrar Negocios")) {
			clickIfVisible("Mi Negocio");
		}
		clickFirstVisibleText(List.of("Administrar Negocios"));

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTrueWithMessage(isTextVisible("Sección Legal") || isTextVisible("Términos y Condiciones"),
				"Expected legal section to be visible.");
	}

	private void validateInformacionGeneral() {
		assertTextVisible("Información General");
		assertTrueWithMessage(EMAIL_PATTERN.matcher(getBodyText()).find(),
				"Expected a visible user email in the account page.");
		assertTrueWithMessage(isTextVisible("Nombre") || isTextVisible("Usuario") || hasAtLeastTwoWordLine(),
				"Expected visible user name information.");
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
	}

	private void validateDetallesDeLaCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTrueWithMessage(hasBusinessListSignals(), "Expected visible business list entries.");
	}

	private String openLegalDocument(final String linkText, final String expectedHeading, final String screenshotName) {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> previousWindows = driver.getWindowHandles();

		clickFirstVisibleText(List.of(linkText));
		sleep(500);

		String targetWindow = originalWindow;
		final Instant timeoutAt = Instant.now().plusSeconds(8);
		while (Instant.now().isBefore(timeoutAt)) {
			final Set<String> currentWindows = driver.getWindowHandles();
			if (currentWindows.size() > previousWindows.size()) {
				for (final String handle : currentWindows) {
					if (!previousWindows.contains(handle)) {
						targetWindow = handle;
						break;
					}
				}
				break;
			}
			sleep(250);
		}

		if (!targetWindow.equals(originalWindow)) {
			driver.switchTo().window(targetWindow);
		}

		waitForUiToSettle();
		assertTextVisible(expectedHeading);
		assertTrueWithMessage(getBodyText().length() > 120, "Expected visible legal content text.");
		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (!targetWindow.equals(originalWindow)) {
			driver.close();
			driver.switchTo().window(originalWindow);
		} else if (!driver.getWindowHandle().equals(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToSettle();
		return finalUrl;
	}

	private void selectGoogleAccountIfChooserAppears() {
		final Instant timeoutAt = Instant.now().plusSeconds(15);
		boolean chooserDetected = false;

		while (Instant.now().isBefore(timeoutAt)) {
			try {
				for (final String handle : driver.getWindowHandles()) {
					driver.switchTo().window(handle);

					if (isTextVisible("Choose an account") || isTextVisible("Elige una cuenta")
							|| isTextVisible("Selecciona una cuenta")) {
						chooserDetected = true;
					}

					final List<WebElement> matchingAccounts = driver
							.findElements(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(GOOGLE_ACCOUNT_EMAIL)
									+ ")]"));
					for (final WebElement element : matchingAccounts) {
						if (element.isDisplayed()) {
							wait.until(ExpectedConditions.elementToBeClickable(element)).click();
							waitForUiToSettle();
							return;
						}
					}
				}
			} catch (final NoSuchWindowException ignored) {
				// Window can close while iterating handles.
			}

			sleep(300);
		}

		if (chooserDetected) {
			Assert.fail("Google account chooser appeared but target account was not selectable: " + GOOGLE_ACCOUNT_EMAIL);
		}
	}

	private void waitForMainAppInterface() {
		wait.until(driverInstance -> {
			final Object readyState = ((JavascriptExecutor) driverInstance).executeScript("return document.readyState");
			return hasVisibleElement(By.cssSelector("aside"), By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar')]"))
					&& "complete".equals(String.valueOf(readyState))
					&& (isTextVisible("Negocio") || isTextVisible("Mi Negocio") || isTextVisible("Dashboard"));
		});
	}

	private void assertLeftSidebarVisible() {
		assertTrueWithMessage(
				hasVisibleElement(By.cssSelector("aside"), By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar')]")),
				"Expected the left sidebar navigation to be visible.");
	}

	private void clickFirstVisibleText(final List<String> labels) {
		for (final String label : labels) {
			final By locator = By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.), "
					+ xpathLiteral(label) + ")]");
			final List<WebElement> candidates = driver.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					scrollIntoView(candidate);
					wait.until(ExpectedConditions.elementToBeClickable(candidate)).click();
					waitForUiToSettle();
					return;
				}
			}
		}
		Assert.fail("Could not find clickable element for any of the labels: " + labels);
	}

	private void clickIfVisible(final String label) {
		final By locator = By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.), "
				+ xpathLiteral(label) + ")]");
		final List<WebElement> candidates = driver.findElements(locator);
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				scrollIntoView(candidate);
				new Actions(driver).moveToElement(candidate).click().perform();
				waitForUiToSettle();
				return;
			}
		}
	}

	private boolean hasVisibleElement(final By... locators) {
		for (final By locator : locators) {
			if (!driver.findElements(locator).isEmpty()) {
				for (final WebElement element : driver.findElements(locator)) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void assertElementVisible(final By locator) {
		waitForVisible(locator);
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private boolean isTextVisible(final String text) {
		final List<WebElement> matches = driver.findElements(
				By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]"));
		for (final WebElement match : matches) {
			if (match.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void assertTextVisible(final String text) {
		wait.until(driverInstance -> isTextVisible(text));
	}

	private boolean hasBusinessListSignals() {
		return hasVisibleElement(By.xpath("//li[contains(@class,'business') or .//*[contains(@class,'business')]]"),
				By.xpath("//*[contains(@class,'business-card')]"), By.xpath("//table//tr[position()>1]"),
				By.xpath("//ul/li"));
	}

	private boolean hasAtLeastTwoWordLine() {
		final String[] lines = getBodyText().split("\\R");
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.split("\\s+").length >= 2 && !EMAIL_PATTERN.matcher(trimmed).find() && trimmed.length() > 5) {
				return true;
			}
		}
		return false;
	}

	private String getBodyText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private void waitForUiToSettle() {
		wait.until(driverInstance -> {
			final Object readyState = ((JavascriptExecutor) driverInstance).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(readyState));
		});
	}

	private void captureScreenshot(final String checkpointName) {
		try {
			final String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
			final Path screenshotPath = screenshotDirectory.resolve(checkpointName + "-" + timestamp + ".png");
			final Path sourcePath = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(sourcePath, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
			System.out.println("Screenshot captured: " + screenshotPath.toAbsolutePath());
		} catch (final Exception exception) {
			throw new AssertionError("Failed to capture screenshot for checkpoint " + checkpointName, exception);
		}
	}

	private WebDriver createDriver() {
		final String browser = getValue("saleads.e2e.browser", "SALEADS_E2E_BROWSER", "chrome").toLowerCase();

		if ("firefox".equals(browser)) {
			final FirefoxOptions options = new FirefoxOptions();
			options.addArguments("--width=1600");
			options.addArguments("--height=1200");
			return new FirefoxDriver(options);
		}

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--start-maximized");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (Boolean.parseBoolean(getValue("saleads.e2e.headless", "SALEADS_E2E_HEADLESS", "false"))) {
			options.addArguments("--headless=new");
			options.addArguments("--window-size=1600,1200");
		}
		return new ChromeDriver(options);
	}

	private boolean runStep(final String stepName, final StepAction action) {
		try {
			final String detail = action.run();
			report.put(stepName, StepResult.passed(detail));
			return true;
		} catch (final Throwable throwable) {
			report.put(stepName, StepResult.failed(throwable.getMessage()));
			return false;
		}
	}

	private boolean allStepsPassed() {
		for (final StepResult result : report.values()) {
			if (!result.passed) {
				return false;
			}
		}
		return true;
	}

	private String renderReport() {
		final List<String> lines = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final StepResult result = entry.getValue();
			final String detail = result.detail == null || result.detail.isBlank() ? "" : " (" + result.detail + ")";
			lines.add("- " + entry.getKey() + ": " + (result.passed ? "PASS" : "FAIL") + detail);
		}
		return String.join("\n", lines);
	}

	private static void require(final boolean condition, final String message) {
		if (!condition) {
			Assert.fail(message);
		}
	}

	private static String getValue(final String propertyKey, final String envKey, final String defaultValue) {
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

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int index = 0; index < chars.length; index++) {
			final String chunk = chars[index] == '\'' ? "\"'\"" : "'" + chars[index] + "'";
			builder.append(chunk);
			if (index < chars.length - 1) {
				builder.append(", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private static void assertTrueWithMessage(final boolean condition, final String message) {
		if (!condition) {
			Assert.fail(message);
		}
	}

	private static final class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}

		private static StepResult passed(final String detail) {
			return new StepResult(true, detail);
		}

		private static StepResult failed(final String detail) {
			return new StepResult(false, detail);
		}
	}

	@FunctionalInterface
	private interface StepAction {
		String run() throws Exception;
	}

	private void scrollIntoView(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		} catch (final Exception ignored) {
			// ignore and continue using default click behavior
		}
	}
}
