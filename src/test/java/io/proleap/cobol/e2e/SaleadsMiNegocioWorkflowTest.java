package io.proleap.cobol.e2e;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * E2E workflow validation for SaleADS "Mi Negocio" module.
 *
 * <p>
 * Run using:
 * </p>
 * <pre>
 * mvn -Dtest=SaleadsMiNegocioWorkflowTest \
 *     -Dsaleads.login.url=https://your-current-saleads-environment/login \
 *     -Dsaleads.headless=false test
 * </pre>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String NEW_BUSINESS_NAME = "Negocio Prueba Automatizacion";

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor js;
	private Path evidenceDir;
	private final AtomicInteger screenshotCounter = new AtomicInteger(0);
	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> failureDetails = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		initializeReport();

		final ChromeOptions options = new ChromeOptions();
		if (readBooleanSetting("saleads.headless", "SALEADS_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-gpu");
		options.addArguments("--lang=es-ES");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(readLongSetting("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", 30)));
		js = (JavascriptExecutor) driver;

		evidenceDir = Path.of("target", "saleads-evidence", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		if (loginUrl == null) {
			throw new IllegalStateException("Missing login URL. Set -Dsaleads.login.url or SALEADS_LOGIN_URL.");
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
	public void saleadsMiNegocioFullWorkflow() {
		executeStep("Login", this::loginWithGoogleAndValidateDashboard);
		executeStep("Mi Negocio menu", this::openMiNegocioMenuAndValidate);
		executeStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::openAdministrarNegociosAndValidateSections);
		executeStep("Información General", this::validateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		executeStep("Tus Negocios", this::validateTusNegocios);
		executeStep("Términos y Condiciones", () -> validateLegalDocument("Términos y Condiciones", "Términos y Condiciones", "terminos-y-condiciones"));
		executeStep("Política de Privacidad", () -> validateLegalDocument("Política de Privacidad", "Política de Privacidad", "politica-de-privacidad"));

		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).collect(Collectors.toList());
		assertTrue("Workflow failed for steps: " + failedSteps + ". Review evidence in " + evidenceDir, failedSteps.isEmpty());
	}

	private void loginWithGoogleAndValidateDashboard() {
		if (isAnyVisible(By.xpath("//aside"), By.xpath("//nav//*[contains(@class,'sidebar')]"))) {
			captureScreenshot("dashboard-loaded");
			return;
		}

		clickByText("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google", "Continuar con Google", "Google");

		// If Google account chooser appears, select the requested account.
		tryClickVisible(By.xpath("//*[contains(normalize-space(.)," + toXPathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]"), Duration.ofSeconds(12));
		waitForUiToLoad();

		waitForAnyVisible(
				By.xpath("//aside"),
				By.xpath("//*[contains(@class,'sidebar')]"),
				By.xpath("//nav//*[contains(normalize-space(.),'Mi Negocio') or contains(normalize-space(.),'Negocio')]"));
		captureScreenshot("dashboard-loaded");
	}

	private void openMiNegocioMenuAndValidate() {
		expandMiNegocioMenuIfNeeded();
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		captureScreenshot("mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() {
		expandMiNegocioMenuIfNeeded();
		clickByText("Agregar Negocio");
		waitForVisibleText("Crear Nuevo Negocio");

		final WebElement businessNameInput = waitForAnyVisible(
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"));

		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");
		captureScreenshot("agregar-negocio-modal");

		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys(NEW_BUSINESS_NAME);
		clickByText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.),'Crear Nuevo Negocio')]")));
		waitForUiToLoad();
	}

	private void openAdministrarNegociosAndValidateSections() {
		expandMiNegocioMenuIfNeeded();
		clickByText("Administrar Negocios");
		waitForUiToLoad();

		waitForVisibleText("Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal");
		captureScreenshot("administrar-negocios-account-page");
	}

	private void validateInformacionGeneral() {
		waitForVisibleText("Información General");
		waitForVisibleText("BUSINESS PLAN");
		waitForVisibleText("Cambiar Plan");

		assertTrue("Expected a visible email in Información General section.", hasVisibleEmail());
		assertTrue("Expected a visible user-name related label/value.", hasVisibleNameRelatedText());
	}

	private void validateDetallesDeLaCuenta() {
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");

		final List<WebElement> potentialItems = driver.findElements(By.xpath(
				"//*[contains(normalize-space(.),'Tus Negocios')]/ancestor::*[self::section or self::div][1]//*[self::li or self::tr or contains(@class,'business') or contains(@class,'negocio')]"));
		assertTrue("Expected business list/content to be visible in 'Tus Negocios'.",
				potentialItems.stream().anyMatch(WebElement::isDisplayed));
	}

	private void validateLegalDocument(final String linkText, final String headingText, final String screenshotName) {
		final String appWindow = driver.getWindowHandle();
		final Set<String> oldHandles = driver.getWindowHandles();
		final String appUrlBeforeClick = driver.getCurrentUrl();

		clickByText(linkText);
		waitForUiToLoad();

		boolean openedNewTab = false;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15)).until((ExpectedCondition<Boolean>) d -> d != null && d.getWindowHandles().size() > oldHandles.size());
			openedNewTab = true;
		} catch (final TimeoutException ignored) {
			openedNewTab = false;
		}

		if (openedNewTab) {
			switchToNewestTab(oldHandles);
		}

		waitForUiToLoad();
		waitForVisibleText(headingText);
		assertTrue("Expected legal content text to be visible for " + headingText, hasSubstantialParagraphText());

		legalUrls.put(headingText, driver.getCurrentUrl());
		captureScreenshot(screenshotName);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
			if (driver.getCurrentUrl().equals(appUrlBeforeClick)) {
				waitForUiToLoad();
			}
		}

		waitForUiToLoad();
	}

	private void expandMiNegocioMenuIfNeeded() {
		if (isTextVisible("Agregar Negocio") && isTextVisible("Administrar Negocios")) {
			return;
		}

		if (isTextVisible("Negocio")) {
			clickByText("Negocio");
		}

		if (isTextVisible("Mi Negocio")) {
			clickByText("Mi Negocio");
		}

		waitForUiToLoad();
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
	}

	private boolean hasVisibleEmail() {
		return driver.findElements(By.xpath("//*[contains(normalize-space(.),'@')]")).stream()
				.filter(WebElement::isDisplayed)
				.map(WebElement::getText)
				.anyMatch(text -> EMAIL_PATTERN.matcher(text).find());
	}

	private boolean hasVisibleNameRelatedText() {
		return isTextVisible("Nombre") || isTextVisible("Usuario") || isTextVisible("Perfil") || isTextVisible("Cuenta");
	}

	private boolean hasSubstantialParagraphText() {
		return driver.findElements(By.xpath("//p[string-length(normalize-space(.)) >= 40]")).stream().anyMatch(WebElement::isDisplayed);
	}

	private void executeStep(final String stepName, final ThrowingRunnable step) {
		try {
			step.run();
			report.put(stepName, true);
		} catch (final Exception ex) {
			report.put(stepName, false);
			failureDetails.put(stepName, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
			captureScreenshot("failed-" + sanitize(stepName));
		}
	}

	private void printFinalReport() {
		System.out.println("==== SaleADS Mi Negocio Full Test Report ====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			final String details = failureDetails.containsKey(entry.getKey()) ? " | " + failureDetails.get(entry.getKey()) : "";
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL") + details);
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("---- Legal URLs ----");
			legalUrls.forEach((name, url) -> System.out.println(name + ": " + url));
		}
		if (evidenceDir != null) {
			System.out.println("Evidence path: " + evidenceDir.toAbsolutePath());
		}
		System.out.println("=============================================");
	}

	private void initializeReport() {
		report.clear();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
	}

	private void captureScreenshot(final String name) {
		if (driver == null || evidenceDir == null) {
			return;
		}

		try {
			final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final String filename = String.format("%02d-%s.png", screenshotCounter.incrementAndGet(), sanitize(name));
			Files.copy(source.toPath(), evidenceDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception ignored) {
			// Keep workflow running even if evidence capture fails.
		}
	}

	private void clickByText(final String... candidates) {
		Exception lastException = null;
		for (final String text : candidates) {
			try {
				final WebElement element = waitForVisibleText(text);
				scrollIntoView(element);
				try {
					wait.until(ExpectedConditions.elementToBeClickable(element)).click();
				} catch (final Exception clickException) {
					js.executeScript("arguments[0].click();", element);
				}
				waitForUiToLoad();
				return;
			} catch (final Exception ex) {
				lastException = ex;
			}
		}

		throw new IllegalStateException("Unable to click any text candidate: " + String.join(", ", candidates), lastException);
	}

	private void tryClickVisible(final By by, final Duration timeout) {
		try {
			final WebElement element = new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
			scrollIntoView(element);
			try {
				wait.until(ExpectedConditions.elementToBeClickable(element)).click();
			} catch (final Exception clickException) {
				js.executeScript("arguments[0].click();", element);
			}
			waitForUiToLoad();
		} catch (final Exception ignored) {
			// Optional click.
		}
	}

	private WebElement waitForVisibleText(final String text) {
		final By locator = By.xpath("//*[contains(normalize-space(.)," + toXPathLiteral(text) + ")]");
		return wait.until(driver -> {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed() && element.getText() != null && element.getText().contains(text)) {
					return element;
				}
			}
			return null;
		});
	}

	@SafeVarargs
	private final WebElement waitForAnyVisible(final By... locators) {
		return wait.until(driver -> {
			for (final By locator : locators) {
				final List<WebElement> elements = driver.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	@SafeVarargs
	private final boolean isAnyVisible(final By... locators) {
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isTextVisible(final String text) {
		final By locator = By.xpath("//*[contains(normalize-space(.)," + toXPathLiteral(text) + ")]");
		for (final WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete".equals(js.executeScript("return document.readyState")));
		// Extra tiny pause for dynamic UI transitions/render cycles.
		waitABeat(300);
	}

	private void switchToNewestTab(final Set<String> oldHandles) {
		final List<String> newHandles = new ArrayList<>(driver.getWindowHandles());
		newHandles.removeAll(oldHandles);
		if (newHandles.isEmpty()) {
			throw new IllegalStateException("Expected a new browser tab/window to open but none was detected.");
		}
		driver.switchTo().window(newHandles.get(0));
	}

	private void scrollIntoView(final WebElement element) {
		js.executeScript("arguments[0].scrollIntoView({block: 'center', inline: 'center'});", element);
		waitABeat(150);
	}

	private void waitABeat(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean readBooleanSetting(final String systemProperty, final String envVar, final boolean defaultValue) {
		final String raw = firstNonBlank(System.getProperty(systemProperty), System.getenv(envVar));
		return raw == null ? defaultValue : Boolean.parseBoolean(raw);
	}

	private long readLongSetting(final String systemProperty, final String envVar, final long defaultValue) {
		final String raw = firstNonBlank(System.getProperty(systemProperty), System.getenv(envVar));
		if (raw == null) {
			return defaultValue;
		}
		try {
			return Long.parseLong(raw);
		} catch (final NumberFormatException ex) {
			return defaultValue;
		}
	}

	private String sanitize(final String value) {
		return value.toLowerCase()
				.replace("á", "a")
				.replace("é", "e")
				.replace("í", "i")
				.replace("ó", "o")
				.replace("ú", "u")
				.replace("ñ", "n")
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-|-$)", "");
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part;
			if (chars[i] == '\'') {
				part = "\"'\"";
			} else if (chars[i] == '"') {
				part = "'\"'";
			} else {
				final int start = i;
				while (i < chars.length && chars[i] != '\'' && chars[i] != '"') {
					i++;
				}
				part = "'" + value.substring(start, i) + "'";
				i--;
			}
			result.append(part).append(",");
		}
		if (result.charAt(result.length() - 1) == ',') {
			result.setLength(result.length() - 1);
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
