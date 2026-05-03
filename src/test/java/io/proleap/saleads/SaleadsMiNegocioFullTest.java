package io.proleap.saleads;

import java.io.File;
import java.io.IOException;
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
import java.util.Objects;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * SaleADS end-to-end UI workflow for Mi Negocio module.
 *
 * <p>This test is environment-agnostic and avoids hardcoded domains. Provide
 * the login URL via SALEADS_LOGIN_URL or -Dsaleads.login.url.
 */
public class SaleadsMiNegocioFullTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String DEFAULT_TIMEOUT_SECONDS = "25";
	private static final String DEFAULT_HEADLESS = "true";
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private final Map<String, String> report = new LinkedHashMap<>();
	private final List<String> failedFields = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String loginUrl;
	private String googleAccountEmail;
	private String expectedUserEmail;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		loginUrl = getRequiredConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL or -Dsaleads.login.url to run this workflow.",
				loginUrl != null && !loginUrl.isBlank());

		googleAccountEmail = getConfig("saleads.google.email", "SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_ACCOUNT);
		expectedUserEmail = getConfig("saleads.user.email", "SALEADS_USER_EMAIL", googleAccountEmail);
		final int timeoutSeconds = Integer
				.parseInt(getConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS));
		final boolean headless = Boolean
				.parseBoolean(getConfig("saleads.headless", "SALEADS_HEADLESS", DEFAULT_HEADLESS));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		screenshotDir = Paths.get("target", "saleads-screenshots", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
		Files.createDirectories(screenshotDir);
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
		runStep("Login", this::loginWithGoogle);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegocios);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", () -> validateLegalPage("Términos y Condiciones", "08_terminos_condiciones"));
		runStep("Política de Privacidad", () -> validateLegalPage("Política de Privacidad", "09_politica_privacidad"));

		if (!failedFields.isEmpty()) {
			Assert.fail("Workflow validations failed: " + failedFields + ". Full report: " + report);
		}
	}

	private void loginWithGoogle() throws IOException {
		driver.get(loginUrl);
		waitForUiToSettle();

		clickFirstMatchingText(Arrays.asList("Sign in with Google", "Login with Google", "Iniciar sesión con Google",
				"Inicia sesión con Google", "Continuar con Google", "Google"));

		// Optional account-picker step on Google identity pages.
		clickTextIfVisible(googleAccountEmail, Duration.ofSeconds(10));

		waitForAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio"), Duration.ofSeconds(40));
		waitForSidebar();
		appWindowHandle = driver.getWindowHandle();
		takeScreenshot("01_dashboard_loaded");
	}

	private void openMiNegocioMenu() throws IOException {
		waitForSidebar();
		clickFirstMatchingText(Arrays.asList("Mi Negocio", "Negocio"));
		waitForUiToSettle();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02_mi_negocio_expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickFirstMatchingText(Arrays.asList("Agregar Negocio"));
		waitForUiToSettle();

		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeScreenshot("03_agregar_negocio_modal");

		final WebElement businessNameInput = waitForFirstVisibleElement(Arrays.asList(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[@name='nombreNegocio']"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]")));

		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");

		clickFirstMatchingText(Arrays.asList("Cancelar"));
		waitForTextToDisappear("Crear Nuevo Negocio");
	}

	private void openAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickFirstMatchingText(Arrays.asList("Mi Negocio", "Negocio"));
			waitForUiToSettle();
		}

		clickFirstMatchingText(Arrays.asList("Administrar Negocios"));
		waitForUiToSettle();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		takeScreenshot("04_administrar_negocios");
	}

	private void validateInformacionGeneral() {
		waitForTextVisible("Información General");
		assertTextVisible(expectedUserEmail);
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");

		final String pageText = safeGetText(By.tagName("body"));
		Assert.assertTrue("Expected a visible user name on the account page.", hasLikelyUserName(pageText, expectedUserEmail));
	}

	private void validateDetallesCuenta() {
		waitForTextVisible("Detalles de la Cuenta");
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		waitForTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertBusinessListVisible();
	}

	private void validateLegalPage(final String legalLinkText, final String screenshotName) throws IOException {
		waitForTextVisible("Sección Legal");
		final String originalWindow = driver.getWindowHandle();
		final Set<String> oldHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String previousUrl = driver.getCurrentUrl();

		clickFirstMatchingText(Arrays.asList(legalLinkText));

		boolean openedNewTab = false;
		try {
			wait.until(d -> d.getWindowHandles().size() > oldHandles.size() || !Objects.equals(d.getCurrentUrl(), previousUrl));
		} catch (final TimeoutException e) {
			throw new AssertionError("No navigation detected after clicking legal link: " + legalLinkText, e);
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!oldHandles.contains(handle)) {
				driver.switchTo().window(handle);
				openedNewTab = true;
				break;
			}
		}

		waitForUiToSettle();
		assertTextVisible(legalLinkText);
		assertLegalContentVisible();
		takeScreenshot(screenshotName);
		legalUrls.put(legalLinkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiToSettle();
		driver.switchTo().window(appWindowHandle == null ? originalWindow : appWindowHandle);
		waitForTextVisible("Sección Legal");
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			report.put(reportField, "PASS");
		} catch (final Throwable t) {
			report.put(reportField, "FAIL - " + t.getMessage());
			failedFields.add(reportField);
		}
	}

	private void clickFirstMatchingText(final List<String> textCandidates) {
		AssertionError lastFailure = null;
		for (final String text : textCandidates) {
			try {
				final By clickableByText = By.xpath("((//button)|(//a)|(//*[@role='button'])|(//*[contains(@class, 'btn')]))"
						+ "[contains(normalize-space(.), " + xpathLiteral(text) + ")][1]");
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(clickableByText));
				clickAndWait(element);
				return;
			} catch (final TimeoutException | StaleElementReferenceException ex) {
				lastFailure = new AssertionError("Could not click element with text: " + text, ex);
			}
		}

		if (lastFailure != null) {
			throw lastFailure;
		}

		throw new AssertionError("No text candidates were provided for click action.");
	}

	private void clickTextIfVisible(final String text, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			final By accountBy = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")][1]");
			final WebElement candidate = shortWait.until(ExpectedConditions.elementToBeClickable(accountBy));
			clickAndWait(candidate);
		} catch (final TimeoutException ignored) {
			// Optional UI branch (account selector may not appear if already signed in).
		}
	}

	private void clickAndWait(final WebElement element) {
		element.click();
		waitForUiToSettle();
	}

	private void waitForUiToSettle() {
		try {
			wait.until(d -> {
				final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(String.valueOf(readyState)) || "interactive".equals(String.valueOf(readyState));
			});
		} catch (final TimeoutException ignored) {
			// Keep going even if the page has long-lived async requests.
		}

		try {
			Thread.sleep(400L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void assertTextVisible(final String text) {
		waitForTextVisible(text);
	}

	private void waitForTextVisible(final String text) {
		final By textBy = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(textBy));
	}

	private void waitForAnyTextVisible(final List<String> textCandidates, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		localWait.until(d -> {
			for (final String text : textCandidates) {
				final By textBy = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
				final List<WebElement> elements = d.findElements(textBy);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			}
			return false;
		});
	}

	private void waitForTextToDisappear(final String text) {
		final By textBy = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(textBy));
	}

	private void waitForSidebar() {
		wait.until(d -> {
			final List<WebElement> sidebars = d.findElements(By.xpath("//aside | //nav"));
			for (final WebElement sidebar : sidebars) {
				if (sidebar.isDisplayed()) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isTextVisible(final String text) {
		final By textBy = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
		for (final WebElement element : driver.findElements(textBy)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private WebElement waitForFirstVisibleElement(final List<By> selectors) {
		TimeoutException lastTimeout = null;
		for (final By selector : selectors) {
			try {
				final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
				if (element.isDisplayed()) {
					return element;
				}
			} catch (final TimeoutException e) {
				lastTimeout = e;
			}
		}
		throw new AssertionError("Could not find a visible element for selectors: " + selectors, lastTimeout);
	}

	private void assertBusinessListVisible() {
		final List<By> listCandidates = Arrays.asList(By.xpath("//ul[.//li]"), By.xpath("//table[.//tr]"),
				By.xpath("//*[contains(@class, 'business') and (.//button or .//a)]"));

		for (final By candidate : listCandidates) {
			for (final WebElement element : driver.findElements(candidate)) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}

		throw new AssertionError("Business list is not visible in 'Tus Negocios'.");
	}

	private void assertLegalContentVisible() {
		final String bodyText = safeGetText(By.tagName("body"));
		final int contentLength = bodyText == null ? 0 : bodyText.replaceAll("\\s+", " ").trim().length();
		Assert.assertTrue("Legal content text is too short or not visible.", contentLength > 250);
	}

	private String safeGetText(final By by) {
		try {
			return driver.findElement(by).getText();
		} catch (final NoSuchElementException e) {
			return "";
		}
	}

	private boolean hasLikelyUserName(final String pageText, final String email) {
		if (pageText == null) {
			return false;
		}

		final String normalized = pageText.replace('\n', ' ').replace('\r', ' ').trim();
		if (!normalized.contains(email)) {
			return false;
		}

		// Basic heuristic: any two-word alphabetic token near account data.
		return normalized.matches("(?s).*[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}\\s+[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}.*");
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = screenshotDir.resolve(checkpointName + ".png");
		Files.copy(screenshotFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("[Checkpoint] screenshot=" + target);
	}

	private void printFinalReport() {
		if (report.isEmpty()) {
			return;
		}

		System.out.println("===== SaleADS Mi Negocio Final Report =====");
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("----- Legal URLs -----");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(entry.getKey() + ": " + entry.getValue());
			}
		}
		System.out.println("Screenshots directory: " + screenshotDir);
		System.out.println("===========================================");
	}

	private String getRequiredConfig(final String propertyKey, final String envKey) {
		final String fromProperty = System.getProperty(propertyKey);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}

		final String fromEnv = System.getenv(envKey);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv.trim();
		}

		return null;
	}

	private String getConfig(final String propertyKey, final String envKey, final String fallback) {
		final String value = getRequiredConfig(propertyKey, envKey);
		return value == null ? fallback : value;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder concatBuilder = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				concatBuilder.append(", \"'\", ");
			}
			concatBuilder.append("'").append(parts[i]).append("'");
		}
		concatBuilder.append(")");
		return concatBuilder.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
