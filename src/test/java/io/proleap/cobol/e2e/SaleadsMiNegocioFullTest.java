package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String INFO_GENERAL = "Informaci\u00f3n General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String SECCION_LEGAL = "Secci\u00f3n Legal";
	private static final String TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String POLITICA = "Pol\u00edtica de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Duration timeout;
	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this UI test.",
				getBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false));

		timeout = Duration.ofSeconds(getIntConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", 30));
		driver = buildDriver();
		wait = new WebDriverWait(driver, timeout);

		final String evidencePath = getStringConfig("saleads.evidence.dir", "SALEADS_EVIDENCE_DIR", "target/saleads-evidence");
		evidenceDir = Paths.get(evidencePath);
		Files.createDirectories(evidenceDir);

		final String startUrl = getStringConfig("saleads.start.url", "SALEADS_START_URL", null);
		if (startUrl != null) {
			driver.get(startUrl);
			waitForUiSettled();
		} else {
			final String currentUrl = driver.getCurrentUrl();
			Assume.assumeTrue("Provide SALEADS_START_URL for a fresh browser session.",
					currentUrl != null && !currentUrl.startsWith("about:blank") && !currentUrl.startsWith("data:,"));
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		boolean canContinue = runStep("Login", () -> {
			stepLoginWithGoogle();
			captureScreenshot("01-dashboard-loaded");
		});

		canContinue = runDependentStep(canContinue, "Mi Negocio menu", () -> {
			stepOpenMiNegocioMenu();
			captureScreenshot("02-mi-negocio-expanded");
		});

		canContinue = runDependentStep(canContinue, "Agregar Negocio modal", () -> {
			stepAgregarNegocioModal();
			captureScreenshot("03-agregar-negocio-modal");
		});

		canContinue = runDependentStep(canContinue, "Administrar Negocios view", () -> {
			stepOpenAdministrarNegocios();
			captureScreenshot("04-administrar-negocios");
		});

		canContinue = runDependentStep(canContinue, "Informaci\u00f3n General", this::stepValidateInformacionGeneral);
		canContinue = runDependentStep(canContinue, "Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		canContinue = runDependentStep(canContinue, "Tus Negocios", this::stepValidateTusNegocios);

		canContinue = runDependentStep(canContinue, "T\u00e9rminos y Condiciones", () -> {
			stepValidateLegalLink(TERMINOS, TERMINOS, "terms");
			captureScreenshot("05-terminos-y-condiciones");
		});

		runDependentStep(canContinue, "Pol\u00edtica de Privacidad", () -> {
			stepValidateLegalLink(POLITICA, POLITICA, "privacy");
			captureScreenshot("06-politica-de-privacidad");
		});

		printFinalReport();
		assertAllStepsPassed();
	}

	private void stepLoginWithGoogle() {
		final Set<String> beforeClickHandles = driver.getWindowHandles();

		clickAnyVisibleText(List.of("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google", "Google"));
		waitForUiSettled();
		switchToNewWindowIfOpened(beforeClickHandles);

		clickVisibleTextIfPresent(DEFAULT_GOOGLE_ACCOUNT, Duration.ofSeconds(12));
		waitForUiSettled();

		final boolean appLoaded = waitForAnyVisibleText(
				List.of("Mi Negocio", "Negocio", "Dashboard", "Inicio"), Duration.ofSeconds(60));
		assertTrue("Main application interface did not load after Google login.", appLoaded);

		assertTrue("Left sidebar navigation is not visible after login.",
				isAnyTextVisible(List.of("Negocio", "Mi Negocio", "Dashboard")));
	}

	private void stepOpenMiNegocioMenu() {
		clickAnyVisibleText(List.of("Negocio"));
		waitForUiSettled();
		clickAnyVisibleText(List.of("Mi Negocio"));
		waitForUiSettled();

		assertTrue("'Agregar Negocio' should be visible in the expanded submenu.",
				isAnyTextVisible(List.of("Agregar Negocio")));
		assertTrue("'Administrar Negocios' should be visible in the expanded submenu.",
				isAnyTextVisible(List.of("Administrar Negocios")));
	}

	private void stepAgregarNegocioModal() {
		clickAnyVisibleText(List.of("Agregar Negocio"));
		assertTrue("Modal title 'Crear Nuevo Negocio' is not visible.",
				waitForAnyVisibleText(List.of("Crear Nuevo Negocio"), Duration.ofSeconds(20)));

		assertTrue("Input field 'Nombre del Negocio' not found.",
				isAnyElementVisible(List.of(
						By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
						By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
						By.xpath("//*[normalize-space()='Nombre del Negocio']/following::input[1]"))));

		assertTrue("Usage text should be visible in modal.",
				isAnyTextVisible(List.of("Tienes 2 de 3 negocios")));
		assertTrue("'Cancelar' button should be present.",
				isAnyTextVisible(List.of("Cancelar")));
		assertTrue("'Crear Negocio' button should be present.",
				isAnyTextVisible(List.of("Crear Negocio")));

		typeBusinessNameIfPossible("Negocio Prueba Automatizacion");
		clickAnyVisibleText(List.of("Cancelar"));
		waitUntilTextDisappears("Crear Nuevo Negocio");
	}

	private void stepOpenAdministrarNegocios() {
		if (!isAnyTextVisible(List.of("Administrar Negocios"))) {
			clickAnyVisibleText(List.of("Mi Negocio"));
			waitForUiSettled();
		}

		clickAnyVisibleText(List.of("Administrar Negocios"));
		waitForUiSettled();

		assertTrue("Section '" + INFO_GENERAL + "' is missing.",
				isAnyTextVisible(List.of(INFO_GENERAL, "Informacion General")));
		assertTrue("Section '" + DETALLES_CUENTA + "' is missing.",
				isAnyTextVisible(List.of(DETALLES_CUENTA)));
		assertTrue("Section 'Tus Negocios' is missing.",
				isAnyTextVisible(List.of("Tus Negocios")));
		assertTrue("Section '" + SECCION_LEGAL + "' is missing.",
				isAnyTextVisible(List.of(SECCION_LEGAL, "Seccion Legal")));
	}

	private void stepValidateInformacionGeneral() {
		final String expectedEmail = getStringConfig("saleads.expected.email", "SALEADS_EXPECTED_EMAIL", DEFAULT_GOOGLE_ACCOUNT);
		final String expectedName = getStringConfig("saleads.expected.name", "SALEADS_EXPECTED_NAME", null);

		if (expectedName != null) {
			assertTrue("Expected user name '" + expectedName + "' is not visible.",
					isAnyTextVisible(List.of(expectedName)));
		} else {
			assertTrue("User name should be visible in '" + INFO_GENERAL + "'.", looksLikeUserNameVisible());
		}

		assertTrue("User email is not visible in '" + INFO_GENERAL + "'.",
				isAnyTextVisible(List.of(expectedEmail, DEFAULT_GOOGLE_ACCOUNT)));
		assertTrue("'BUSINESS PLAN' text is not visible.", isAnyTextVisible(List.of("BUSINESS PLAN")));
		assertTrue("'Cambiar Plan' button is not visible.", isAnyTextVisible(List.of("Cambiar Plan")));
	}

	private void stepValidateDetallesCuenta() {
		assertTrue("'Cuenta creada' is not visible.", isAnyTextVisible(List.of("Cuenta creada")));
		assertTrue("'Estado activo' is not visible.",
				isAnyTextVisible(List.of("Estado activo", "Estado Activo")));
		assertTrue("'Idioma seleccionado' is not visible.",
				isAnyTextVisible(List.of("Idioma seleccionado")));
	}

	private void stepValidateTusNegocios() {
		assertTrue("Business list header 'Tus Negocios' is not visible.",
				isAnyTextVisible(List.of("Tus Negocios")));
		assertTrue("'Agregar Negocio' button is missing in business section.",
				isAnyTextVisible(List.of("Agregar Negocio")));
		assertTrue("'Tienes 2 de 3 negocios' should be visible in business section.",
				isAnyTextVisible(List.of("Tienes 2 de 3 negocios")));

		final boolean hasBusinessRows = isAnyElementVisible(List.of(
				By.xpath("//*[normalize-space()='Tus Negocios']/following::*[self::li or self::tr][1]"),
				By.xpath("//*[normalize-space()='Tus Negocios']/following::div[string-length(normalize-space()) > 2][1]")));
		assertTrue("Business list appears empty or not visible.", hasBusinessRows);
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading, final String key) {
		final String appHandle = driver.getWindowHandle();
		final Set<String> beforeClickHandles = driver.getWindowHandles();
		final String currentUrl = driver.getCurrentUrl();

		clickAnyVisibleText(List.of(linkText));
		waitForUiSettled();

		final String newHandle = waitForNewHandle(beforeClickHandles, Duration.ofSeconds(10));
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
		} else {
			waitForUrlChange(currentUrl, Duration.ofSeconds(10));
		}

		assertTrue("Expected legal heading '" + expectedHeading + "' is not visible.",
				waitForAnyVisibleText(List.of(expectedHeading, stripAccents(expectedHeading)), Duration.ofSeconds(30)));

		final boolean hasLegalText = isAnyElementVisible(List.of(
				By.xpath("//p[string-length(normalize-space()) > 40]"),
				By.xpath("//article//*[string-length(normalize-space()) > 40]"),
				By.xpath("//main//*[string-length(normalize-space()) > 40]")));
		assertTrue("Legal content text is not visible.", hasLegalText);

		legalUrls.put(key, driver.getCurrentUrl());

		if (newHandle != null) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiSettled();
		} else {
			driver.navigate().back();
			waitForUiSettled();
		}
	}

	private boolean runDependentStep(final boolean canContinue, final String name, final CheckedStep step) throws Exception {
		if (!canContinue) {
			report.put(name, false);
			System.out.println("[BLOCKED] " + name + " not executed because a previous step failed.");
			return false;
		}
		return runStep(name, step);
	}

	private boolean runStep(final String name, final CheckedStep step) throws Exception {
		try {
			step.run();
			report.put(name, true);
			System.out.println("[PASS] " + name);
			return true;
		} catch (final Exception exception) {
			report.put(name, false);
			System.out.println("[FAIL] " + name + " -> " + exception.getMessage());
			captureScreenshot("error-" + name);
			return false;
		}
	}

	private WebDriver buildDriver() {
		final String remoteUrl = getStringConfig("saleads.remote.webdriver.url", "SALEADS_REMOTE_WEBDRIVER_URL", null);
		final boolean headless = getBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true);

		if (remoteUrl != null) {
			final ChromeOptions remoteOptions = new ChromeOptions();
			if (headless) {
				remoteOptions.addArguments("--headless=new");
			}
			remoteOptions.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");
			try {
				return new RemoteWebDriver(java.net.URI.create(remoteUrl).toURL(), remoteOptions);
			} catch (final Exception exception) {
				throw new IllegalArgumentException("Invalid SALEADS_REMOTE_WEBDRIVER_URL: " + remoteUrl, exception);
			}
		}

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");
		return new ChromeDriver(options);
	}

	private void waitForUiSettled() {
		try {
			new WebDriverWait(driver, timeout).until((ExpectedCondition<Boolean>) wd -> {
				if (!(wd instanceof JavascriptExecutor)) {
					return true;
				}
				final Object state = ((JavascriptExecutor) wd).executeScript("return document.readyState");
				return "complete".equals(String.valueOf(state));
			});
		} catch (final TimeoutException ignored) {
			// Continue even if the page keeps polling.
		}

		try {
			new WebDriverWait(driver, Duration.ofSeconds(8)).until(
					ExpectedConditions.invisibilityOfElementLocated(
							By.cssSelector(".spinner,.loading,.loader,[aria-busy='true'],[role='progressbar']")));
		} catch (final TimeoutException ignored) {
			// Continue when no spinner selector matches.
		}
	}

	private void clickAnyVisibleText(final List<String> textCandidates) {
		final List<Exception> errors = new ArrayList<>();
		for (final String text : textCandidates) {
			try {
				final WebElement element = waitUntilClickableText(text, Duration.ofSeconds(12));
				clickElement(element);
				waitForUiSettled();
				return;
			} catch (final Exception exception) {
				errors.add(exception);
			}
		}

		throw new NoSuchElementException("None of these texts was clickable: " + textCandidates + " / errors: " + errors.size());
	}

	private void clickVisibleTextIfPresent(final String text, final Duration maxWait) {
		try {
			final WebElement element = waitUntilClickableText(text, maxWait);
			clickElement(element);
		} catch (final Exception ignored) {
			// Account chooser may be skipped if the session is already authenticated.
		}
	}

	private WebElement waitUntilClickableText(final String text, final Duration maxWait) {
		final By locator = By.xpath(
				"(//*[normalize-space()=" + xpathLiteral(text)
						+ "]/ancestor-or-self::*[self::button or self::a or @role='button' or @role='link' or @role='menuitem'])[1]"
						+ " | //*[(self::button or self::a or @role='button' or @role='link' or @role='menuitem') and normalize-space()="
						+ xpathLiteral(text) + "]"
						+ " | //*[normalize-space()=" + xpathLiteral(text) + "]");
		return new WebDriverWait(driver, maxWait).until(ExpectedConditions.elementToBeClickable(locator));
	}

	private boolean waitForAnyVisibleText(final List<String> texts, final Duration maxWait) {
		final Instant deadline = Instant.now().plus(maxWait);
		while (Instant.now().isBefore(deadline)) {
			if (isAnyTextVisible(texts)) {
				return true;
			}
			sleepMillis(300);
		}
		return false;
	}

	private boolean isAnyTextVisible(final List<String> texts) {
		for (final String text : texts) {
			final By exact = By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]");
			if (isVisible(exact)) {
				return true;
			}

			final By contains = By.xpath("//*[contains(normalize-space()," + xpathLiteral(text) + ")]");
			if (isVisible(contains)) {
				return true;
			}
		}
		return false;
	}

	private boolean isAnyElementVisible(final List<By> locators) {
		for (final By locator : locators) {
			if (isVisible(locator)) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisible(final By locator) {
		try {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		} catch (final Exception ignored) {
			// Keep probing alternative selectors.
		}
		return false;
	}

	private void clickElement(final WebElement element) {
		try {
			element.click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void switchToNewWindowIfOpened(final Set<String> oldHandles) {
		final String newHandle = waitForNewHandle(oldHandles, Duration.ofSeconds(8));
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
		}
	}

	private String waitForNewHandle(final Set<String> oldHandles, final Duration maxWait) {
		final Instant deadline = Instant.now().plus(maxWait);
		while (Instant.now().isBefore(deadline)) {
			final Set<String> currentHandles = driver.getWindowHandles();
			if (currentHandles.size() > oldHandles.size()) {
				for (final String handle : currentHandles) {
					if (!oldHandles.contains(handle)) {
						return handle;
					}
				}
			}
			sleepMillis(200);
		}
		return null;
	}

	private void waitForUrlChange(final String previousUrl, final Duration maxWait) {
		try {
			new WebDriverWait(driver, maxWait).until(wd -> !previousUrl.equals(wd.getCurrentUrl()));
		} catch (final TimeoutException ignored) {
			// Some legal links may open in-place without changing URL immediately.
		}
	}

	private void waitUntilTextDisappears(final String text) {
		final By textLocator = By.xpath("//*[contains(normalize-space()," + xpathLiteral(text) + ")]");
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.invisibilityOfElementLocated(textLocator));
		} catch (final TimeoutException ignored) {
			// Closing animation can be slow; continue.
		}
	}

	private void typeBusinessNameIfPossible(final String value) {
		final List<By> locators = List.of(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
				By.xpath("//*[normalize-space()='Nombre del Negocio']/following::input[1]"));

		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed() && element.isEnabled()) {
					element.clear();
					element.sendKeys(value);
					return;
				}
			}
		}
	}

	private boolean looksLikeUserNameVisible() {
		final List<By> nameLocators = List.of(
				By.xpath("//*[contains(normalize-space(), '@')]/preceding::*[self::h1 or self::h2 or self::h3 or self::p or self::span][string-length(normalize-space()) > 2][1]"),
				By.xpath("//*[normalize-space()='" + INFO_GENERAL + "']/following::*[self::h1 or self::h2 or self::h3 or self::p or self::span][string-length(normalize-space()) > 2 and not(contains(normalize-space(), '@'))][1]"));
		return isAnyElementVisible(nameLocators);
	}

	private void captureScreenshot(final String label) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final String fileName = String.format("%d-%s.png", Instant.now().toEpochMilli(), sanitize(label));
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		try {
			Files.copy(screenshot.toPath(), evidenceDir.resolve(fileName));
			System.out.println("Screenshot captured: " + evidenceDir.resolve(fileName));
		} catch (final IOException ioException) {
			System.out.println("Screenshot capture failed: " + ioException.getMessage());
		}
	}

	private String sanitize(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private void printFinalReport() {
		System.out.println("\n==== SaleADS Mi Negocio Final Report ====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}

		if (!legalUrls.isEmpty()) {
			System.out.println("Legal final URLs:");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println("- " + entry.getKey() + ": " + entry.getValue());
			}
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("=========================================\n");
	}

	private void assertAllStepsPassed() {
		final StringBuilder failures = new StringBuilder();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue()) {
				if (failures.length() > 0) {
					failures.append(", ");
				}
				failures.append(entry.getKey());
			}
		}
		assertTrue("One or more workflow sections failed: " + failures, failures.length() == 0);
	}

	private String stripAccents(final String text) {
		return text.replace("\u00e9", "e").replace("\u00ed", "i").replace("\u00f3", "o").replace("\u00e1", "a").replace("\u00fa", "u");
	}

	private boolean getBooleanConfig(final String propertyKey, final String envKey, final boolean defaultValue) {
		final String value = getStringConfig(propertyKey, envKey, null);
		if (value == null) {
			return defaultValue;
		}
		return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
	}

	private int getIntConfig(final String propertyKey, final String envKey, final int defaultValue) {
		final String value = getStringConfig(propertyKey, envKey, null);
		if (value == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (final NumberFormatException ignored) {
			return defaultValue;
		}
	}

	private String getStringConfig(final String propertyKey, final String envKey, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue.trim();
		}
		return defaultValue;
	}

	private void sleepMillis(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}
}
