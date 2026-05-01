package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(25);
	private static final Duration SHORT_WAIT = Duration.ofSeconds(8);
	private static final DateTimeFormatter STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private String terminosFinalUrl = "N/A";
	private String privacidadFinalUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final String chromeDriverPath = envOrProperty("CHROMEDRIVER_PATH", "chromedriver.path");
		if (chromeDriverPath != null && !chromeDriverPath.isBlank()) {
			System.setProperty("webdriver.chrome.driver", chromeDriverPath);
		}

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1000");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--lang=es-ES");

		if (Boolean.parseBoolean(envOrProperty("SALEADS_HEADLESS", "saleads.headless", "false"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500));
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		screenshotDir = Paths.get("target", "saleads-e2e-screenshots", STAMP_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(screenshotDir);

		initializeReport();
	}

	@After
	public void tearDown() {
		try {
			printFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String loginUrl = envOrProperty("SALEADS_LOGIN_URL", "saleads.login.url");
		assertTrue("A login URL is required. Set SALEADS_LOGIN_URL env var or -Dsaleads.login.url=<url>", loginUrl != null && !loginUrl.isBlank());

		// Step 1: Login with Google
		runStep(REPORT_LOGIN, () -> {
			driver.get(loginUrl);
			waitForUiLoad();

			clickByVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesion con Google", "Iniciar sesión con Google", "Continuar con Google"),
					true);
			switchToNewestTabIfOpened();
			selectGoogleAccountIfShown(GOOGLE_ACCOUNT_EMAIL);
			switchToAppWindow(loginUrl);
			waitForUiLoad();

			assertAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Inicio"), "Main application interface not detected after login.");
			assertTrue("Left sidebar not visible after login.", isSidebarVisible());

			captureScreenshot("01-dashboard-loaded");
		});

		// Step 2: Open Mi Negocio menu
		runStep(REPORT_MI_NEGOCIO_MENU, () -> {
			openMiNegocioMenu();
			assertAnyVisibleText(Arrays.asList("Agregar Negocio"), "'Agregar Negocio' option not visible.");
			assertAnyVisibleText(Arrays.asList("Administrar Negocios"), "'Administrar Negocios' option not visible.");
			captureScreenshot("02-mi-negocio-expanded");
		});

		// Step 3: Validate Agregar Negocio modal
		runStep(REPORT_AGREGAR_MODAL, () -> {
			clickByVisibleText(Arrays.asList("Agregar Negocio"), true);

			assertAnyVisibleText(Arrays.asList("Crear Nuevo Negocio"), "Modal title 'Crear Nuevo Negocio' not visible.");
			assertInputFieldVisibleByLabelOrPlaceholder("Nombre del Negocio");
			assertAnyVisibleText(Arrays.asList("Tienes 2 de 3 negocios"), "Expected plan/limit text not visible.");
			assertAnyVisibleText(Arrays.asList("Cancelar"), "Button 'Cancelar' not present.");
			assertAnyVisibleText(Arrays.asList("Crear Negocio"), "Button 'Crear Negocio' not present.");

			captureScreenshot("03-agregar-negocio-modal");

			clickInputByLabelOrPlaceholder("Nombre del Negocio");
			sendKeysActiveElement("Negocio Prueba Automatizacion");
			waitForUiLoad();
			clickByVisibleText(Arrays.asList("Cancelar"), true);
		});

		// Step 4: Open Administrar Negocios
		runStep(REPORT_ADMINISTRAR_VIEW, () -> {
			openMiNegocioMenu();
			clickByVisibleText(Arrays.asList("Administrar Negocios"), true);

			assertAnyVisibleText(Arrays.asList("Informacion General", "Información General"), "Section 'Informacion General' not found.");
			assertAnyVisibleText(Arrays.asList("Detalles de la Cuenta"), "Section 'Detalles de la Cuenta' not found.");
			assertAnyVisibleText(Arrays.asList("Tus Negocios"), "Section 'Tus Negocios' not found.");
			assertAnyVisibleText(Arrays.asList("Seccion Legal", "Sección Legal"), "Section 'Seccion Legal' not found.");
			captureScreenshot("04-administrar-negocios-full");
		});

		// Step 5: Validate Informacion General
		runStep(REPORT_INFO_GENERAL, () -> {
			assertLikelyUserNameVisible();
			assertLikelyEmailVisible();
			assertAnyVisibleText(Arrays.asList("BUSINESS PLAN"), "'BUSINESS PLAN' text not found.");
			assertAnyVisibleText(Arrays.asList("Cambiar Plan"), "'Cambiar Plan' button not found.");
		});

		// Step 6: Validate Detalles de la Cuenta
		runStep(REPORT_DETALLES_CUENTA, () -> {
			assertAnyVisibleText(Arrays.asList("Cuenta creada"), "'Cuenta creada' is not visible.");
			assertAnyVisibleText(Arrays.asList("Estado activo"), "'Estado activo' is not visible.");
			assertAnyVisibleText(Arrays.asList("Idioma seleccionado"), "'Idioma seleccionado' is not visible.");
		});

		// Step 7: Validate Tus Negocios
		runStep(REPORT_TUS_NEGOCIOS, () -> {
			assertAnyVisibleText(Arrays.asList("Tus Negocios"), "Business section title not visible.");
			assertAnyVisibleText(Arrays.asList("Agregar Negocio"), "'Agregar Negocio' button in business section not found.");
			assertAnyVisibleText(Arrays.asList("Tienes 2 de 3 negocios"), "'Tienes 2 de 3 negocios' text not visible in business section.");
		});

		// Step 8: Validate Terminos y Condiciones
		runStep(REPORT_TERMINOS, () -> {
			terminosFinalUrl = openLegalLinkAndValidate("Terminos y Condiciones", "Términos y Condiciones", "08-terminos-condiciones");
		});

		// Step 9: Validate Politica de Privacidad
		runStep(REPORT_PRIVACIDAD, () -> {
			privacidadFinalUrl = openLegalLinkAndValidate("Politica de Privacidad", "Política de Privacidad", "09-politica-privacidad");
		});

		assertReportHasNoFailures();
	}

	private void initializeReport() {
		for (final String key : Arrays.asList(REPORT_LOGIN, REPORT_MI_NEGOCIO_MENU, REPORT_AGREGAR_MODAL, REPORT_ADMINISTRAR_VIEW,
				REPORT_INFO_GENERAL, REPORT_DETALLES_CUENTA, REPORT_TUS_NEGOCIOS, REPORT_TERMINOS, REPORT_PRIVACIDAD)) {
			report.put(key, StepResult.pending());
		}
	}

	private void runStep(final String reportKey, final StepAction action) {
		try {
			action.run();
			report.put(reportKey, StepResult.pass());
		} catch (final Throwable t) {
			report.put(reportKey, StepResult.fail(t.getMessage()));
		}
	}

	private String openLegalLinkAndValidate(final String... namesAndScreenshot) throws IOException {
		final String fallbackName = namesAndScreenshot[0];
		final String accentName = namesAndScreenshot[1];
		final String screenshotName = namesAndScreenshot[2];

		final String originalTab = driver.getWindowHandle();
		final Set<String> before = driver.getWindowHandles();
		final String originalUrl = driver.getCurrentUrl();

		clickByVisibleText(Arrays.asList(accentName, fallbackName), true);
		final Set<String> after = waitForLegalNavigation(before.size(), originalUrl);

		String targetTab = originalTab;

		if (after.size() > before.size()) {
			for (final String handle : after) {
				if (!before.contains(handle)) {
					targetTab = handle;
					break;
				}
			}
			driver.switchTo().window(targetTab);
		}

		waitForUiLoad();
		assertAnyVisibleText(Arrays.asList(accentName, fallbackName), "Expected legal page heading not visible: " + accentName);
		assertLegalContentVisible();
		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (!targetTab.equals(originalTab)) {
			driver.close();
			driver.switchTo().window(originalTab);
		} else {
			driver.navigate().back();
		}
		waitForUiLoad();

		return finalUrl;
	}

	private Set<String> waitForLegalNavigation(final int beforeSize, final String originalUrl) {
		final WebDriverWait legalWait = new WebDriverWait(driver, DEFAULT_WAIT);
		return legalWait.until((ExpectedCondition<Set<String>>) d -> {
			if (d == null) {
				return driver.getWindowHandles();
			}
			final Set<String> handles = d.getWindowHandles();
			if (handles.size() > beforeSize) {
				return handles;
			}
			final String currentUrl = d.getCurrentUrl();
			if (currentUrl != null && !currentUrl.equals(originalUrl)) {
				return handles;
			}
			return null;
		});
	}

	private void switchToNewestTabIfOpened() {
		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() <= 1) {
			return;
		}
		String newest = driver.getWindowHandle();
		for (final String handle : handles) {
			newest = handle;
		}
		driver.switchTo().window(newest);
		waitForUiLoad();
	}

	private void switchToAppWindow(final String expectedAppUrl) {
		final String expectedHost = extractHost(expectedAppUrl);
		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			final String current = driver.getCurrentUrl();
			if (current == null || current.isBlank()) {
				continue;
			}
			if (!current.startsWith("http")) {
				continue;
			}
			if (expectedHost.isBlank() || expectedHost.equalsIgnoreCase(extractHost(current))) {
				return;
			}
		}
	}

	private void openMiNegocioMenu() {
		try {
			clickByVisibleText(Arrays.asList("Mi Negocio"), false);
		} catch (final RuntimeException e) {
			clickByVisibleText(Arrays.asList("Negocio"), false);
		}
		waitForUiLoad();
	}

	private void clickByVisibleText(final List<String> candidates, final boolean strict) {
		WebElement target = null;
		for (final String candidate : candidates) {
			target = findFirstVisibleByExactOrContains(candidate);
			if (target != null) {
				break;
			}
		}

		if (target == null) {
			if (strict) {
				throw new NoSuchElementException("No clickable element found by visible text: " + candidates);
			}
			return;
		}
		final WebElement finalTarget = target;

		scrollIntoView(finalTarget);
		try {
			wait.until(d -> finalTarget.isDisplayed() && finalTarget.isEnabled());
		} catch (final TimeoutException ignored) {
			// fall back to direct click attempt
		}

		try {
			finalTarget.click();
		} catch (final Exception clickEx) {
			new Actions(driver).moveToElement(finalTarget).click().perform();
		}
		waitForUiLoad();
	}

	private WebElement findFirstVisibleByExactOrContains(final String text) {
		final List<By> selectors = new ArrayList<>();
		final String escaped = xpathLiteral(text);
		selectors.add(By.xpath("//*[normalize-space(text())=" + escaped + "]"));
		selectors.add(By.xpath("//*[contains(normalize-space(text()), " + escaped + ")]"));
		selectors.add(By.xpath("//button[normalize-space(.)=" + escaped + "]"));
		selectors.add(By.xpath("//a[normalize-space(.)=" + escaped + "]"));
		selectors.add(By.xpath("//span[normalize-space(.)=" + escaped + "]"));

		for (final By selector : selectors) {
			final List<WebElement> elements = driver.findElements(selector);
			for (final WebElement element : elements) {
				if (isInteractable(element)) {
					return element;
				}
			}
		}
		return null;
	}

	private void assertAnyVisibleText(final List<String> candidates, final String failureMessage) {
		for (final String candidate : candidates) {
			if (isVisibleTextPresent(candidate)) {
				return;
			}
		}
		throw new AssertionError(failureMessage);
	}

	private boolean isVisibleTextPresent(final String value) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_WAIT);
			return shortWait.until(d -> {
				if (d == null) {
					return false;
				}
				final String textLower = value.toLowerCase(Locale.ROOT);
				for (final WebElement element : d.findElements(By.xpath("//*[normalize-space(text()) or normalize-space(.)]"))) {
					if (!element.isDisplayed()) {
						continue;
					}
					final String normalized = element.getText() == null ? "" : element.getText().trim().toLowerCase(Locale.ROOT);
					if (normalized.equals(textLower) || normalized.contains(textLower)) {
						return true;
					}
				}
				return false;
			});
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private boolean isSidebarVisible() {
		final List<By> sidebarCandidates = Arrays.asList(By.xpath("//aside"), By.xpath("//*[@role='navigation']"),
				By.xpath("//*[contains(@class,'sidebar')]"), By.xpath("//*[contains(@class,'SideBar')]"));
		for (final By candidate : sidebarCandidates) {
			for (final WebElement element : driver.findElements(candidate)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void assertInputFieldVisibleByLabelOrPlaceholder(final String fieldName) {
		final String escaped = xpathLiteral(fieldName);
		final List<By> selectors = Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), " + escaped + ")]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, " + escaped + ")]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"));

		for (final By selector : selectors) {
			for (final WebElement input : driver.findElements(selector)) {
				if (input.isDisplayed()) {
					return;
				}
			}
		}
		throw new AssertionError("Input field not visible for: " + fieldName);
	}

	private void clickInputByLabelOrPlaceholder(final String fieldName) {
		final String escaped = xpathLiteral(fieldName);
		final List<By> selectors = Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), " + escaped + ")]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, " + escaped + ")]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"));

		for (final By selector : selectors) {
			for (final WebElement input : driver.findElements(selector)) {
				if (input.isDisplayed()) {
					scrollIntoView(input);
					input.click();
					return;
				}
			}
		}
		throw new NoSuchElementException("Could not find input field for: " + fieldName);
	}

	private void sendKeysActiveElement(final String value) {
		driver.switchTo().activeElement().clear();
		driver.switchTo().activeElement().sendKeys(value);
	}

	private void selectGoogleAccountIfShown(final String accountEmail) {
		final String escaped = xpathLiteral(accountEmail);
		final List<By> googleSelectors = Arrays.asList(
				By.xpath("//*[contains(normalize-space(.), " + escaped + ")]"),
				By.xpath("//*[contains(@data-email, " + escaped + ")]"),
				By.xpath("//*[@id='identifierId']"));

		for (final By selector : googleSelectors) {
			final List<WebElement> elements = driver.findElements(selector);
			for (final WebElement element : elements) {
				if (!element.isDisplayed()) {
					continue;
				}
				final String tag = element.getTagName();
				if ("input".equalsIgnoreCase(tag)) {
					element.clear();
					element.sendKeys(accountEmail);
					clickByVisibleText(Arrays.asList("Siguiente", "Next"), true);
				} else {
					element.click();
				}
				return;
			}
		}
	}

	private void waitForUiLoad() {
		wait.until(d -> {
			if (d == null) {
				return false;
			}
			final String state = (String) ((org.openqa.selenium.JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(state);
		});

		// Give UI frameworks a brief settle window after clicks and nav.
		try {
			Thread.sleep(350);
		} catch (final InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private void assertLikelyUserNameVisible() {
		final List<WebElement> candidates = driver.findElements(By.xpath("//*[contains(@class,'name') or contains(@class,'user')]"));
		for (final WebElement candidate : candidates) {
			if (!candidate.isDisplayed()) {
				continue;
			}
			final String text = candidate.getText() == null ? "" : candidate.getText().trim();
			if (text.length() > 2 && !text.contains("@")) {
				return;
			}
		}

		// Fallback by detecting two-word capitalized visible text.
		for (final WebElement element : driver.findElements(By.xpath("//*"))) {
			if (!element.isDisplayed()) {
				continue;
			}
			final String text = element.getText() == null ? "" : element.getText().trim();
			if (text.matches(".*\\b[A-Z][a-z]+\\s+[A-Z][a-z]+\\b.*")) {
				return;
			}
		}
		throw new AssertionError("Could not confidently detect a visible user name.");
	}

	private void assertLikelyEmailVisible() {
		for (final WebElement element : driver.findElements(By.xpath("//*"))) {
			if (!element.isDisplayed()) {
				continue;
			}
			final String text = element.getText() == null ? "" : element.getText().trim();
			if (text.matches(".*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*")) {
				return;
			}
		}
		throw new AssertionError("Could not detect a visible email address.");
	}

	private void assertLegalContentVisible() {
		final List<String> legalKeywords = Arrays.asList("terminos", "términos", "privacidad", "datos personales", "aceptacion", "aceptación",
				"uso", "responsabilidad", "derechos");
		final String pageText = driver.findElements(By.xpath("//body")).stream().map(WebElement::getText).collect(Collectors.joining(" ")).toLowerCase(Locale.ROOT);

		for (final String keyword : legalKeywords) {
			if (pageText.contains(keyword)) {
				return;
			}
		}
		throw new AssertionError("Expected legal content was not detected on page.");
	}

	private void scrollIntoView(final WebElement element) {
		((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
	}

	private boolean isInteractable(final WebElement element) {
		try {
			return element != null && element.isDisplayed() && element.isEnabled();
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void captureScreenshot(final String name) throws IOException {
		final Path target = screenshotDir.resolve(name + ".png");
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("[saleads-e2e] Screenshot: " + target.toAbsolutePath());
	}

	private void printFinalReport() {
		System.out.println();
		System.out.println("=== saleads_mi_negocio_full_test :: Final Report ===");
		report.forEach((k, v) -> System.out.println("- " + k + ": " + v.status + (v.details == null ? "" : " (" + v.details + ")")));
		System.out.println("- Terminos y Condiciones URL: " + sanitizeUrl(terminosFinalUrl));
		System.out.println("- Politica de Privacidad URL: " + sanitizeUrl(privacidadFinalUrl));
		System.out.println("=====================================================");
	}

	private void assertReportHasNoFailures() {
		final List<String> failedKeys = report.entrySet().stream().filter(e -> "FAIL".equals(e.getValue().status)).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		assertTrue("One or more required workflow sections failed: " + failedKeys, failedKeys.isEmpty());
	}

	private String sanitizeUrl(final String input) {
		if (input == null || input.isBlank()) {
			return "N/A";
		}
		try {
			final URI uri = URI.create(input);
			if (uri.getScheme() == null || uri.getScheme().isBlank()) {
				return "N/A";
			}
			final String host = uri.getHost() == null ? "" : uri.getHost();
			final String path = uri.getPath() == null ? "" : uri.getPath();
			final String query = uri.getQuery() == null ? "" : "?...";
			return uri.getScheme() + "://" + host + path + query;
		} catch (final Exception e) {
			return input;
		}
	}

	private String extractHost(final String url) {
		if (url == null || url.isBlank()) {
			return "";
		}
		try {
			final URI uri = URI.create(url);
			return uri.getHost() == null ? "" : uri.getHost();
		} catch (final Exception e) {
			return "";
		}
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String piece = String.valueOf(chars[i]);
			if (i > 0) {
				sb.append(',');
			}
			if ("'".equals(piece)) {
				sb.append("\"'\"");
			} else {
				sb.append("'").append(piece).append("'");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	private String envOrProperty(final String envName, final String propertyName) {
		return envOrProperty(envName, propertyName, null);
	}

	private String envOrProperty(final String envName, final String propertyName, final String defaultValue) {
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		final String propValue = System.getProperty(propertyName);
		if (propValue != null && !propValue.isBlank()) {
			return propValue;
		}
		return defaultValue;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details;
		}

		static StepResult pending() {
			return new StepResult("PENDING", null);
		}

		static StepResult pass() {
			return new StepResult("PASS", null);
		}

		static StepResult fail(final String details) {
			return new StepResult("FAIL", details);
		}
	}
}
