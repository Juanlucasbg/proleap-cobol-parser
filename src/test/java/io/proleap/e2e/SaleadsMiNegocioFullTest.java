package io.proleap.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end test for SaleADS "Mi Negocio" workflow.
 *
 * This test is environment-agnostic:
 * - No hardcoded SaleADS URL.
 * - It can start from SALEADS_LOGIN_URL if provided.
 * - Otherwise it expects the browser to already be on the login page after opening.
 *
 * Optional environment variables:
 * - SALEADS_LOGIN_URL
 * - SALEADS_REMOTE_WEBDRIVER_URL
 * - SALEADS_HEADLESS (default: true)
 * - SALEADS_WAIT_SECONDS (default: 30)
 */
public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;
	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private String administrarNegociosUrl;

	@Before
	public void setUp() throws IOException {
		artifactsDir = Paths.get("target", "saleads-mi-negocio-evidence", LocalDateTime.now().format(TS_FORMAT));
		Files.createDirectories(artifactsDir);

		final Duration waitDuration = Duration.ofSeconds(getIntEnv("SALEADS_WAIT_SECONDS", 30));
		driver = buildDriver();
		wait = new WebDriverWait(driver, waitDuration);
		driver.manage().window().maximize();

		final String loginUrl = getEnv("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() throws IOException {
		writeReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() throws IOException {
		runStep("Login", this::step1LoginWithGoogle);
		runStep("Mi Negocio menu", this::step2OpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::step3ValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::step4OpenAdministrarNegocios);
		runStep("Informacion General", this::step5ValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::step6ValidateDetallesCuenta);
		runStep("Tus Negocios", this::step7ValidateTusNegocios);
		runStep("Terminos y Condiciones", () -> step8ValidateLegalPage("Términos y Condiciones", "Términos y Condiciones", "terminos-y-condiciones"));
		runStep("Politica de Privacidad", () -> step8ValidateLegalPage("Política de Privacidad", "Política de Privacidad", "politica-de-privacidad"));

		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : stepResults.entrySet()) {
			if (!Boolean.TRUE.equals(entry.getValue())) {
				failed.add(entry.getKey());
			}
		}

		assertTrue("One or more workflow validations failed: " + failed + ". Evidence: " + artifactsDir.toAbsolutePath(), failed.isEmpty());
	}

	private void step1LoginWithGoogle() throws Exception {
		clickByVisibleTextAny("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiToLoad();
		selectGoogleAccountIfShown();

		final boolean mainInterfaceVisible = isVisibleAny(
				By.xpath("//*[contains(@class,'sidebar')]"),
				By.xpath("//aside"),
				By.xpath("//nav"));
		final boolean leftSidebarVisible = isVisibleAny(
				By.xpath("//aside//*[contains(.,'Negocio') or contains(.,'Mi Negocio')]"),
				By.xpath("//*[contains(@class,'sidebar')]//*[contains(.,'Negocio') or contains(.,'Mi Negocio')]"));

		assertTrue("Main application interface is not visible after login.", mainInterfaceVisible);
		assertTrue("Left sidebar navigation is not visible after login.", leftSidebarVisible);
		saveScreenshot("01-dashboard-loaded");
	}

	private void step2OpenMiNegocioMenu() throws Exception {
		clickIfVisible("Negocio");
		clickByVisibleTextAny("Mi Negocio");

		waitUntilTextVisible("Agregar Negocio");
		waitUntilTextVisible("Administrar Negocios");
		saveScreenshot("02-mi-negocio-menu-expanded");
	}

	private void step3ValidateAgregarNegocioModal() throws Exception {
		clickByVisibleTextAny("Agregar Negocio");
		waitUntilTextVisible("Crear Nuevo Negocio");

		assertTrue("Modal title 'Crear Nuevo Negocio' is missing.", isTextVisible("Crear Nuevo Negocio"));
		assertTrue("'Nombre del Negocio' field not found.", isVisibleAny(
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]")));
		assertTrue("'Tienes 2 de 3 negocios' not found in modal.", isTextVisible("Tienes 2 de 3 negocios"));
		assertTrue("'Cancelar' button not found in modal.", isTextVisible("Cancelar"));
		assertTrue("'Crear Negocio' button not found in modal.", isTextVisible("Crear Negocio"));

		final WebElement input = firstVisible(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));
		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatización");
		clickByVisibleTextAny("Cancelar");
		waitForUiToLoad();

		saveScreenshot("03-agregar-negocio-modal");
	}

	private void step4OpenAdministrarNegocios() throws Exception {
		clickIfVisible("Mi Negocio");
		clickByVisibleTextAny("Administrar Negocios");
		waitForUiToLoad();

		waitUntilTextVisible("Información General");
		waitUntilTextVisible("Detalles de la Cuenta");
		waitUntilTextVisible("Tus Negocios");
		waitUntilTextVisible("Sección Legal");

		administrarNegociosUrl = driver.getCurrentUrl();
		saveScreenshot("04-administrar-negocios-page");
	}

	private void step5ValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		final String sectionText = safeText(section);

		assertTrue("Could not find a likely user name in 'Información General'.", hasLikelyUserName(sectionText));
		assertTrue("Could not find a user email in 'Información General'.", EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("'BUSINESS PLAN' is missing from 'Información General'.", sectionText.contains("BUSINESS PLAN"));
		assertTrue("'Cambiar Plan' button is missing.", isVisibleAny(
				By.xpath("//*[normalize-space(.)='Cambiar Plan']"),
				By.xpath("//button[contains(normalize-space(.),'Cambiar Plan')]")));
	}

	private void step6ValidateDetallesCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		final String sectionText = safeText(section);

		assertTrue("'Cuenta creada' is missing.", sectionText.contains("Cuenta creada"));
		assertTrue("'Estado activo' is missing.", sectionText.contains("Estado activo"));
		assertTrue("'Idioma seleccionado' is missing.", sectionText.contains("Idioma seleccionado"));
	}

	private void step7ValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final String sectionText = safeText(section);

		assertTrue("'Tus Negocios' section appears empty.", sectionText.trim().length() > "Tus Negocios".length());
		assertTrue("'Agregar Negocio' button is missing in 'Tus Negocios'.", sectionText.contains("Agregar Negocio") || isTextVisible("Agregar Negocio"));
		assertTrue("'Tienes 2 de 3 negocios' is missing in 'Tus Negocios'.", sectionText.contains("Tienes 2 de 3 negocios") || isTextVisible("Tienes 2 de 3 negocios"));
	}

	private void step8ValidateLegalPage(final String linkText, final String expectedHeading, final String evidenceName) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleTextAny(linkText);
		waitForUiToLoad();

		String activeWindow = appWindow;
		try {
			wait.until(d -> d.getWindowHandles().size() > windowsBefore.size());
			final Set<String> windowsAfter = new LinkedHashSet<>(driver.getWindowHandles());
			windowsAfter.removeAll(windowsBefore);
			if (!windowsAfter.isEmpty()) {
				activeWindow = windowsAfter.iterator().next();
				driver.switchTo().window(activeWindow);
				waitForUiToLoad();
			}
		} catch (final TimeoutException ignored) {
			// Same-tab navigation is also valid for this workflow.
		}

		waitUntilTextVisible(expectedHeading);
		final String bodyText = safeText(driver.findElement(By.tagName("body")));
		assertTrue("Expected legal heading '" + expectedHeading + "' is missing.", bodyText.contains(expectedHeading));
		assertTrue("Legal page content appears empty for '" + expectedHeading + "'.", bodyText.trim().length() > expectedHeading.length() + 80);

		saveScreenshot("05-" + evidenceName);
		legalUrls.put(expectedHeading, driver.getCurrentUrl());

		if (!activeWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
			return;
		}

		if (administrarNegociosUrl != null && !administrarNegociosUrl.isBlank()) {
			driver.navigate().to(administrarNegociosUrl);
			waitForUiToLoad();
		}
	}

	private WebDriver buildDriver() throws MalformedURLException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (getBooleanEnv("SALEADS_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = getEnv("SALEADS_REMOTE_WEBDRIVER_URL");
		if (remoteUrl != null && !remoteUrl.isBlank()) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}
		return new ChromeDriver(options);
	}

	private void runStep(final String stepName, final CheckedRunnable action) throws IOException {
		try {
			action.run();
			stepResults.put(stepName, true);
		} catch (final Exception ex) {
			stepResults.put(stepName, false);
			saveScreenshot("FAILED-" + normalize(stepName));
			System.err.println("Step failed: " + stepName + " -> " + ex.getMessage());
		}
	}

	private void selectGoogleAccountIfShown() {
		try {
			final WebElement account = new WebDriverWait(driver, Duration.ofSeconds(10)).until(
					ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]")));
			safeClick(account);
			waitForUiToLoad();
		} catch (final TimeoutException ignored) {
			// Account selector not shown in this run, which is acceptable.
		}
	}

	private void clickIfVisible(final String text) {
		try {
			final WebElement element = firstVisible(clickableTextLocator(text), containsTextLocator(text));
			safeClick(element);
			waitForUiToLoad();
		} catch (final Exception ignored) {
			// Optional click, used only to expand potentially collapsed navigation.
		}
	}

	private void clickByVisibleTextAny(final String... texts) {
		Exception lastError = null;
		for (final String text : texts) {
			try {
				final WebElement element = firstVisible(clickableTextLocator(text), containsTextLocator(text));
				safeClick(element);
				waitForUiToLoad();
				return;
			} catch (final Exception ex) {
				lastError = ex;
			}
		}
		throw new NoSuchElementException("Could not click any element with texts: " + String.join(", ", texts), lastError);
	}

	private void safeClick(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private WebElement firstVisible(final By... locators) {
		for (final By locator : locators) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final TimeoutException ignored) {
				// Try next locator.
			}
		}
		throw new NoSuchElementException("No visible element found with provided locators.");
	}

	private void waitUntilTextVisible(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(containsTextLocator(text)));
	}

	private boolean isTextVisible(final String text) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(containsTextLocator(text)));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private boolean isVisibleAny(final By... locators) {
		for (final By locator : locators) {
			try {
				final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				if (element.isDisplayed()) {
					return true;
				}
			} catch (final TimeoutException ignored) {
				// Try next locator.
			}
		}
		return false;
	}

	private WebElement findSectionByHeading(final String headingText) {
		final String headingLiteral = xpathLiteral(headingText);
		final By sectionByHeading = By.xpath("//section[.//*[normalize-space(.)=" + headingLiteral + "]] | //div[.//*[normalize-space(.)=" + headingLiteral + "]][1]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(sectionByHeading));
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		try {
			Thread.sleep(450L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void saveScreenshot(final String label) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = artifactsDir.resolve(normalize(label) + ".png");
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test\n");
		report.append("evidence_dir=").append(artifactsDir.toAbsolutePath()).append('\n');
		report.append('\n');
		report.append("Final Report\n");
		report.append("============\n");
		report.append("Login: ").append(passFail("Login")).append('\n');
		report.append("Mi Negocio menu: ").append(passFail("Mi Negocio menu")).append('\n');
		report.append("Agregar Negocio modal: ").append(passFail("Agregar Negocio modal")).append('\n');
		report.append("Administrar Negocios view: ").append(passFail("Administrar Negocios view")).append('\n');
		report.append("Información General: ").append(passFail("Informacion General")).append('\n');
		report.append("Detalles de la Cuenta: ").append(passFail("Detalles de la Cuenta")).append('\n');
		report.append("Tus Negocios: ").append(passFail("Tus Negocios")).append('\n');
		report.append("Términos y Condiciones: ").append(passFail("Terminos y Condiciones")).append('\n');
		report.append("Política de Privacidad: ").append(passFail("Politica de Privacidad")).append('\n');
		report.append('\n');
		report.append("Legal URLs\n");
		report.append("==========\n");
		for (final Map.Entry<String, String> urlEntry : legalUrls.entrySet()) {
			report.append(urlEntry.getKey()).append(": ").append(urlEntry.getValue()).append('\n');
		}

		Files.writeString(artifactsDir.resolve("final-report.txt"), report.toString());
		System.out.println(report);
	}

	private String passFail(final String key) {
		return Boolean.TRUE.equals(stepResults.get(key)) ? "PASS" : "FAIL";
	}

	private String safeText(final WebElement element) {
		return element == null ? "" : element.getText();
	}

	private boolean hasLikelyUserName(final String sectionText) {
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(sectionText);
		String email = null;
		if (emailMatcher.find()) {
			email = emailMatcher.group();
		}

		for (final String line : sectionText.split("\\R")) {
			final String normalized = line.trim();
			if (normalized.isEmpty()) {
				continue;
			}
			if (normalized.equals("Información General") || normalized.equals("BUSINESS PLAN") || normalized.equals("Cambiar Plan")) {
				continue;
			}
			if (email != null && normalized.contains(email)) {
				continue;
			}
			if (normalized.length() >= 4 && normalized.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private By clickableTextLocator(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath("//button[normalize-space(.)=" + literal + "] | //a[normalize-space(.)=" + literal + "] | //*[@role='button' and normalize-space(.)=" + literal + "]");
	}

	private By containsTextLocator(final String text) {
		return By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder concatBuilder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				concatBuilder.append(", \"'\", ");
			}
			concatBuilder.append("'").append(parts[i]).append("'");
		}
		concatBuilder.append(")");
		return concatBuilder.toString();
	}

	private String getEnv(final String key) {
		return System.getenv(key);
	}

	private boolean getBooleanEnv(final String key, final boolean defaultValue) {
		final String value = getEnv(key);
		return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
	}

	private int getIntEnv(final String key, final int defaultValue) {
		final String value = getEnv(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (final NumberFormatException numberFormatException) {
			return defaultValue;
		}
	}

	private String normalize(final String text) {
		return text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
