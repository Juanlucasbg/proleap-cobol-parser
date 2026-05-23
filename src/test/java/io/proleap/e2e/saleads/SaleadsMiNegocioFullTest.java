package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Términos y Condiciones";
	private static final String STEP_PRIVACY = "Política de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> evidence = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		final boolean runE2e = Boolean.parseBoolean(env("SALEADS_RUN_E2E", "false"));
		Assume.assumeTrue("Set SALEADS_RUN_E2E=true to run this live E2E workflow.", runE2e);

		evidenceDirectory = Files.createDirectories(Path.of("target", "saleads-mi-negocio-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox", "--disable-gpu");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(25));

		final String loginUrl = env("SALEADS_LOGIN_URL", "").trim();
		if (loginUrl.isEmpty()) {
			throw new IllegalStateException(
					"SALEADS_LOGIN_URL is required so the test can open the current environment login page.");
		}

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		printFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() {
		runStep(STEP_LOGIN, this::loginWithGoogleAndValidateSidebar);
		runStep(STEP_MENU, this::openMiNegocioMenu);
		runStep(STEP_MODAL, this::validateAgregarNegocioModal);
		runStep(STEP_ADMIN_VIEW, this::openAdministrarNegocios);
		runStep(STEP_INFO_GENERAL, this::validateInformacionGeneral);
		runStep(STEP_ACCOUNT_DETAILS, this::validateDetallesCuenta);
		runStep(STEP_BUSINESSES, this::validateTusNegocios);
		runStep(STEP_TERMS, () -> validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos"));
		runStep(STEP_PRIVACY, () -> validateLegalLink("Política de Privacidad", "Política de Privacidad", "09-privacidad"));

		assertTrue("Workflow completed with failures:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private void loginWithGoogleAndValidateSidebar() throws IOException {
		clickByAnyVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Continuar con Google Workspace", "Google"));

		selectGoogleAccountIfPresented();

		waitUntilAnyVisibleText(Arrays.asList("Negocio", "Dashboard", "Inicio"), Duration.ofSeconds(40));
		assertAnyElementVisible(Arrays.asList(By.cssSelector("aside"), By.cssSelector("[class*='sidebar']"),
				By.xpath("//*[normalize-space()='Negocio']")));

		captureScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws IOException {
		expandNegocioSectionIfNeeded();
		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");

		captureScreenshot("02-mi-negocio-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		captureScreenshot("03-agregar-negocio-modal");

		typeInModalInput("Nombre del Negocio", "Negocio Prueba Automatizacion");
		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(textLocatorExact("Crear Nuevo Negocio")));
		waitForUiToLoad();
	}

	private void openAdministrarNegocios() throws IOException {
		expandMiNegocioIfCollapsed();
		clickByVisibleText("Administrar Negocios");

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");

		captureScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		assertVisibleText("Información General");
		assertAnyElementVisible(Arrays.asList(By.xpath("//*[contains(normalize-space(),'@')]"), textLocatorContains("Nombre"),
				textLocatorContains("Usuario"), textLocatorContains("Name")));
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
	}

	private void validateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		final WebElement negociosSection = wait.until(ExpectedConditions
				.visibilityOfElementLocated(By.xpath("//*[normalize-space()='Tus Negocios']/ancestor::*[1]")));
		assertTrue("Business list is not visible in 'Tus Negocios'.", negociosSection.getText().length() > 50);
	}

	private void validateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText(linkText);

		wait.until(webDriver -> {
			final boolean openedNewTab = webDriver.getWindowHandles().size() > handlesBefore.size();
			return openedNewTab || isVisibleNow(expectedHeading);
		});

		final Set<String> handlesAfter = driver.getWindowHandles();
		boolean switchedToNewTab = false;

		for (final String handle : handlesAfter) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				switchedToNewTab = true;
				break;
			}
		}

		waitForUiToLoad();
		assertVisibleText(expectedHeading);
		assertTrue("Legal content text is not visible for " + expectedHeading + ".",
				driver.findElement(By.tagName("body")).getText().trim().length() > 180);

		captureScreenshot(screenshotName);
		evidence.put(linkText + " URL", driver.getCurrentUrl());

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		assertVisibleText("Sección Legal");
	}

	private void selectGoogleAccountIfPresented() {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));
			final WebElement account = shortWait
					.until(ExpectedConditions.visibilityOfElementLocated(textLocatorContains(GOOGLE_ACCOUNT_EMAIL)));
			clickElement(account);
		} catch (final TimeoutException ignored) {
			// Account selector may be skipped if user is already authenticated.
		}
	}

	private void expandNegocioSectionIfNeeded() {
		if (!isVisibleNow("Mi Negocio")) {
			clickByVisibleText("Negocio");
			waitForUiToLoad();
		}
	}

	private void expandMiNegocioIfCollapsed() {
		if (!isVisibleNow("Administrar Negocios")) {
			if (!isVisibleNow("Mi Negocio")) {
				expandNegocioSectionIfNeeded();
			}
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}
	}

	private void typeInModalInput(final String fieldLabel, final String value) {
		try {
			final WebElement label = wait
					.until(ExpectedConditions.visibilityOfElementLocated(textLocatorContains(fieldLabel)));
			final WebElement modal = findModalContainer(label);
			WebElement input = null;
			final String htmlFor = label.getAttribute("for");
			if (htmlFor != null && !htmlFor.isBlank()) {
				input = modal.findElement(By.id(htmlFor));
			}
			if (input == null) {
				input = modal.findElement(By.xpath(".//input[not(@type='hidden')]"));
			}
			input.clear();
			input.sendKeys(value);
		} catch (final NoSuchElementException ex) {
			throw new IllegalStateException("Could not type in field '" + fieldLabel + "' inside modal.", ex);
		}
	}

	private WebElement findModalContainer(final WebElement referenceElement) {
		WebElement cursor = referenceElement;
		for (int i = 0; i < 8; i++) {
			final String role = cursor.getAttribute("role");
			final String className = cursor.getAttribute("class");
			if ("dialog".equalsIgnoreCase(role)
					|| (className != null && className.toLowerCase(Locale.ROOT).contains("modal"))) {
				return cursor;
			}
			cursor = cursor.findElement(By.xpath("./.."));
		}
		return driver.findElement(By.tagName("body"));
	}

	private void runStep(final String name, final StepAction action) {
		try {
			action.run();
			report.put(name, Boolean.TRUE);
		} catch (final Exception ex) {
			report.put(name, Boolean.FALSE);
			failures.add(name + " -> " + ex.getMessage());
			try {
				captureScreenshot("error-" + slug(name));
			} catch (final IOException ignored) {
				// Best-effort error evidence.
			}
		}
	}

	private void clickByAnyVisibleText(final List<String> texts) {
		for (final String text : texts) {
			if (isVisibleNow(text)) {
				clickByVisibleText(text);
				return;
			}
		}

		for (final String text : texts) {
			try {
				clickByVisibleText(text);
				return;
			} catch (final RuntimeException ignored) {
				// Try next alternative.
			}
		}

		throw new IllegalStateException("Could not find a clickable element with any text in " + texts + ".");
	}

	private void clickByVisibleText(final String text) {
		final WebElement element = wait.until(ExpectedConditions
				.presenceOfElementLocated(By.xpath("(" + textLocatorExact(text).toString().replace("By.xpath: ", "")
						+ " | " + textLocatorContains(text).toString().replace("By.xpath: ", "") + ")[1]")));
		clickElement(element);
	}

	private void clickElement(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitUntilAnyVisibleText(final List<String> texts, final Duration timeout) {
		new WebDriverWait(driver, timeout).until(webDriver -> {
			for (final String text : texts) {
				if (isVisibleNow(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private void assertVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(textLocatorContains(text)));
	}

	private void assertAnyElementVisible(final List<By> locators) {
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}
		throw new IllegalStateException("None of the expected elements are visible: " + locators);
	}

	private boolean isVisibleNow(final String text) {
		final List<WebElement> exact = driver.findElements(textLocatorExact(text));
		for (final WebElement element : exact) {
			if (element.isDisplayed()) {
				return true;
			}
		}

		final List<WebElement> contains = driver.findElements(textLocatorContains(text));
		for (final WebElement element : contains) {
			if (element.isDisplayed()) {
				return true;
			}
		}

		return false;
	}

	private void captureScreenshot(final String checkpoint) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDirectory.resolve(checkpoint + ".png");
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		evidence.put("Screenshot " + checkpoint, destination.toAbsolutePath().toString());
	}

	private By textLocatorExact(final String text) {
		return By.xpath("//*[normalize-space()=" + asXPathLiteral(text) + "]");
	}

	private By textLocatorContains(final String text) {
		return By.xpath("//*[contains(normalize-space(), " + asXPathLiteral(text) + ")]");
	}

	private String asXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder expression = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				expression.append(", \"'\", ");
			}
			expression.append("'").append(parts[i]).append("'");
		}
		expression.append(")");
		return expression.toString();
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		try {
			Thread.sleep(300);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value;
	}

	private String slug(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private void printFinalReport() {
		if (!report.isEmpty()) {
			System.out.println("\n=== SaleADS Mi Negocio Workflow Report ===");
			report.forEach((name, status) -> System.out.println("- " + name + ": " + (status ? "PASS" : "FAIL")));
		}
		if (!evidence.isEmpty()) {
			System.out.println("\n=== Evidence ===");
			evidence.forEach((name, value) -> System.out.println("- " + name + ": " + value));
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
