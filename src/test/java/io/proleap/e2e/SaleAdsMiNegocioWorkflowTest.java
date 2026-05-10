package io.proleap.e2e;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioWorkflowTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN = "Administrar Negocios view";
	private static final String STEP_INFO = "Información General";
	private static final String STEP_ACCOUNT = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Términos y Condiciones";
	private static final String STEP_PRIVACY = "Política de Privacidad";

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(35);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final DateTimeFormatter screenshotFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDir;
	private String appWindowHandle;

	@Before
	public void setup() throws IOException {
		final boolean enabled = Boolean.parseBoolean(setting("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to execute this workflow test.", enabled);

		final String loginUrl = setting("SALEADS_LOGIN_URL", "");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the environment login page.", !loginUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--lang=es-419");

		final boolean headless = Boolean.parseBoolean(setting("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
		screenshotsDir = Path.of("target", "saleads-e2e-screenshots");
		Files.createDirectories(screenshotsDir);

		driver.get(loginUrl);
		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		initializeReport();

		report.put(STEP_LOGIN, runStep(this::loginWithGoogleAndValidateDashboard));
		if (!report.get(STEP_LOGIN)) {
			markRemainingAsBlocked();
			printReport();
			Assert.fail("Login failed. Remaining steps were blocked.");
		}

		report.put(STEP_MENU, runStep(this::openMiNegocioMenu));
		report.put(STEP_MODAL, runStep(this::validateAgregarNegocioModal));
		report.put(STEP_ADMIN, runStep(this::openAdministrarNegocios));
		report.put(STEP_INFO, runStep(this::validateInformacionGeneral));
		report.put(STEP_ACCOUNT, runStep(this::validateDetallesDeLaCuenta));
		report.put(STEP_BUSINESSES, runStep(this::validateTusNegocios));
		report.put(STEP_TERMS, runStep(() -> validateLegalPage("Términos y Condiciones", "terminos-y-condiciones")));
		report.put(STEP_PRIVACY, runStep(() -> validateLegalPage("Política de Privacidad", "politica-de-privacidad")));

		printReport();
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!Boolean.TRUE.equals(entry.getValue())) {
				failedSteps.add(entry.getKey());
			}
		}

		Assert.assertTrue("Failed workflow validations: " + failedSteps, failedSteps.isEmpty());
	}

	private void initializeReport() {
		report.put(STEP_LOGIN, false);
		report.put(STEP_MENU, false);
		report.put(STEP_MODAL, false);
		report.put(STEP_ADMIN, false);
		report.put(STEP_INFO, false);
		report.put(STEP_ACCOUNT, false);
		report.put(STEP_BUSINESSES, false);
		report.put(STEP_TERMS, false);
		report.put(STEP_PRIVACY, false);
	}

	private void markRemainingAsBlocked() {
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!Boolean.TRUE.equals(entry.getValue())) {
				entry.setValue(false);
			}
		}
	}

	private boolean runStep(final CheckedRunnable step) {
		try {
			step.run();
			return true;
		} catch (final Exception ex) {
			System.err.println("Step failed: " + ex.getMessage());
			ex.printStackTrace(System.err);
			return false;
		}
	}

	private void loginWithGoogleAndValidateDashboard() throws IOException {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		selectGoogleAccountIfVisible(setting("SALEADS_GOOGLE_ACCOUNT", "juanlucasbarbiergarzon@gmail.com"));
		waitForAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Panel", "Inicio");
		captureScreenshot("01-dashboard-loaded");
		assertAnyVisibleText("Negocio", "Mi Negocio");
	}

	private void openMiNegocioMenu() throws IOException {
		if (!isAnyTextVisible("Mi Negocio") && isAnyTextVisible("Negocio")) {
			clickByVisibleText("Negocio");
		}
		clickByVisibleText("Mi Negocio");
		waitForAnyVisibleText("Agregar Negocio", "Administrar Negocios");
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Crear Nuevo Negocio");
		assertAnyVisibleText("Nombre del Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
		assertAnyVisibleText("Cancelar");
		assertAnyVisibleText("Crear Negocio");

		final WebElement businessNameInput = waitForVisibleElement(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombre' or @name='businessName']"));
		businessNameInput.click();
		businessNameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "Negocio Prueba Automatización");
		captureScreenshot("03-agregar-negocio-modal");
		clickByVisibleText("Cancelar");
	}

	private void openAdministrarNegocios() throws IOException {
		expandMiNegocioIfNeeded();
		clickByVisibleText("Administrar Negocios");
		waitForAnyVisibleText("Información General", "Detalles de la Cuenta", "Tus Negocios", "Sección Legal");
		assertAnyVisibleText("Información General");
		assertAnyVisibleText("Detalles de la Cuenta");
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Sección Legal");
		captureScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		assertAnyVisibleText("Información General");
		assertAnyVisibleText("BUSINESS PLAN");
		assertAnyVisibleText("Cambiar Plan");
		assertEmailVisible();
		assertLikelyUserNameVisible();
	}

	private void validateDetallesDeLaCuenta() {
		assertAnyVisibleText("Detalles de la Cuenta");
		assertAnyVisibleText("Cuenta creada");
		assertAnyVisibleText("Estado activo");
		assertAnyVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
	}

	private void validateLegalPage(final String linkText, final String screenshotSuffix) throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();
		final String originalUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		waitForWindowOrNavigation(beforeHandles, originalUrl);

		boolean openedNewTab = false;
		final Set<String> afterHandles = driver.getWindowHandles();
		if (afterHandles.size() > beforeHandles.size()) {
			openedNewTab = true;
			for (final String handle : afterHandles) {
				if (!beforeHandles.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForUiLoad();
		}

		assertAnyVisibleText(linkText);
		assertLegalContentVisible();
		captureScreenshot("05-" + screenshotSuffix);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void expandMiNegocioIfNeeded() {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final Set<String> handlesBeforeSwitch = new LinkedHashSet<>(driver.getWindowHandles());

		try {
			wait.withTimeout(Duration.ofSeconds(12)).until((ExpectedCondition<Boolean>) d -> {
				if (d == null) {
					return false;
				}
				return d.getWindowHandles().size() > handlesBeforeSwitch.size() || isAnyTextVisible(email);
			});
		} catch (final TimeoutException ignored) {
			// Account selection may not appear when Google session is already active.
		} finally {
			wait.withTimeout(WAIT_TIMEOUT);
		}

		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() > handlesBeforeSwitch.size()) {
			for (final String handle : handles) {
				if (!Objects.equals(handle, appWindowHandle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForUiLoad();
		}

		if (isAnyTextVisible(email)) {
			clickByVisibleText(email);
		}

		for (final String handle : driver.getWindowHandles()) {
			if (Objects.equals(handle, appWindowHandle)) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				return;
			}
		}
	}

	private void waitForWindowOrNavigation(final Set<String> previousHandles, final String previousUrl) {
		wait.until((ExpectedCondition<Boolean>) d -> {
			if (d == null) {
				return false;
			}

			final boolean windowIncreased = d.getWindowHandles().size() > previousHandles.size();
			final boolean urlChanged = !Objects.equals(previousUrl, d.getCurrentUrl());

			return windowIncreased || urlChanged;
		});
	}

	private void waitForUiLoad() {
		wait.until(d -> {
			if (d == null) {
				return false;
			}
			return "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState"));
		});
	}

	private void waitForAnyVisibleText(final String... texts) {
		wait.until(d -> isAnyTextVisible(texts));
	}

	private void clickByVisibleText(final String... texts) {
		final WebElement element = wait.until(d -> firstVisibleTextElement(texts));
		try {
			element.click();
		} catch (final Exception ex) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiLoad();
	}

	private WebElement waitForVisibleElement(final By locator) {
		return wait.until(d -> {
			if (d == null) {
				return null;
			}
			final List<WebElement> elements = d.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private WebElement firstVisibleTextElement(final String... texts) {
		final By locator = byAnyVisibleText(texts);
		final List<WebElement> candidates = driver.findElements(locator);
		for (final WebElement candidate : candidates) {
			try {
				if (candidate.isDisplayed()) {
					return candidate;
				}
			} catch (final NoSuchElementException ignored) {
				// Element is stale or detached; keep scanning.
			}
		}
		return null;
	}

	private boolean isAnyTextVisible(final String... texts) {
		final By locator = byAnyVisibleText(texts);
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			try {
				if (element.isDisplayed()) {
					return true;
				}
			} catch (final Exception ignored) {
				// Continue scanning other matches.
			}
		}
		return false;
	}

	private void assertAnyVisibleText(final String... texts) {
		if (!isAnyTextVisible(texts)) {
			Assert.fail("Expected visible text not found: " + Arrays.toString(texts));
		}
	}

	private void assertEmailVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Expected an email in Información General section.", EMAIL_PATTERN.matcher(bodyText).find());
	}

	private void assertLikelyUserNameVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final String[] lines = bodyText.split("\\R");
		for (final String line : lines) {
			final String cleaned = line.trim();
			if (cleaned.isBlank()) {
				continue;
			}
			if (cleaned.equalsIgnoreCase("Información General")
					|| cleaned.equalsIgnoreCase("BUSINESS PLAN")
					|| cleaned.equalsIgnoreCase("Cambiar Plan")
					|| cleaned.contains("@")
					|| cleaned.length() < 4) {
				continue;
			}
			if (cleaned.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return;
			}
		}
		Assert.fail("Could not confirm a visible user name in Información General.");
	}

	private void assertLegalContentVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText().trim();
		Assert.assertTrue("Expected legal page content text.", bodyText.length() > 180);
	}

	private By byAnyVisibleText(final String... texts) {
		final StringBuilder xpath = new StringBuilder();
		for (int i = 0; i < texts.length; i++) {
			if (i > 0) {
				xpath.append(" | ");
			}
			xpath.append("//*[normalize-space()=").append(xpathLiteral(texts[i])).append("]");
		}
		return By.xpath(xpath.toString());
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if (i > 0) {
				result.append(", ");
			}
			if (c == '\'') {
				result.append("\"'\"");
			} else if (c == '"') {
				result.append("'\"'");
			} else {
				result.append("'").append(c).append("'");
			}
		}
		result.append(")");
		return result.toString();
	}

	private void captureScreenshot(final String checkpoint) throws IOException {
		final String fileName = screenshotFormatter.format(LocalDateTime.now()) + "-" + checkpoint + ".png";
		final Path destination = screenshotsDir.resolve(fileName);
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Screenshot: " + destination.toAbsolutePath());
	}

	private String setting(final String key, final String fallback) {
		final String propertyValue = System.getProperty(key);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(key);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return fallback;
	}

	private void printReport() {
		System.out.println("\n=== SaleADS Mi Negocio Workflow Report ===");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println("- " + entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("Legal URLs:");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println("* " + entry.getKey() + " => " + entry.getValue());
			}
		}
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
