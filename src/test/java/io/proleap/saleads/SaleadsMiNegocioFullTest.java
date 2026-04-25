package io.proleap.saleads;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.net.URL;
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
import java.util.Set;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String LOGIN_URL_PROPERTY = "saleads.login.url";
	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String HEADLESS_PROPERTY = "saleads.headless";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String REMOTE_URL_PROPERTY = "selenium.remote.url";
	private static final String REMOTE_URL_ENV = "SELENIUM_REMOTE_URL";
	private static final String WAIT_SECONDS_PROPERTY = "saleads.wait.seconds";
	private static final int DEFAULT_WAIT_SECONDS = 30;
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws Exception {
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);

		evidenceDir = Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		wait = new WebDriverWait(buildDriver(), Duration.ofSeconds(resolveWaitSeconds()));
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegociosView);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad"));

		printFinalReport();
		Assert.assertTrue("Hay validaciones en FAIL:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private WebDriver buildDriver() throws Exception {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (Boolean.parseBoolean(resolveConfig(HEADLESS_PROPERTY, HEADLESS_ENV, "true"))) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = resolveConfig(REMOTE_URL_PROPERTY, REMOTE_URL_ENV, "");
		if (!remoteUrl.isBlank()) {
			driver = new RemoteWebDriver(new URL(remoteUrl), options);
		} else {
			WebDriverManager.chromedriver().setup();
			driver = new ChromeDriver(options);
		}

		return driver;
	}

	private void stepLoginWithGoogle() throws Exception {
		navigateToLoginPage();
		capture("01-login-page");

		final WebElement loginButton = clickFirstClickableByText(
				"Iniciar sesión con Google",
				"Sign in with Google",
				"Continuar con Google",
				"Login with Google");
		clickAndWait(loginButton);

		completeGoogleSelectorIfPresent();
		waitForMainApplication();
		capture("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		ensureStepPassed("Login");
		clickByText("Negocio");
		clickByText("Mi Negocio");

		waitVisibleByText("Agregar Negocio");
		waitVisibleByText("Administrar Negocios");
		capture("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		ensureStepPassed("Mi Negocio menu");
		clickByText("Agregar Negocio");

		waitVisibleByText("Crear Nuevo Negocio");
		waitVisibleByText("Nombre del Negocio");
		waitVisibleByText("Tienes 2 de 3 negocios");
		waitVisibleByText("Cancelar");
		waitVisibleByText("Crear Negocio");
		capture("03-agregar-negocio-modal");

		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or " +
						"ancestor::*[contains(normalize-space(.), 'Nombre del Negocio')]]")));
		input.clear();
		input.sendKeys("Negocio Prueba Automatización");

		clickByText("Cancelar");
		waitForDocumentReady();
	}

	private void stepOpenAdministrarNegociosView() throws Exception {
		ensureStepPassed("Mi Negocio menu");
		expandMiNegocioIfNeeded();
		clickByText("Administrar Negocios");

		waitVisibleByText("Información General");
		waitVisibleByText("Detalles de la Cuenta");
		waitVisibleByText("Tus Negocios");
		waitVisibleByText("Sección Legal");
		capture("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		ensureStepPassed("Administrar Negocios view");
		final WebElement infoSection = sectionByHeading("Información General");
		final String sectionText = infoSection.getText();

		Assert.assertTrue("No se encontró email en Información General", EMAIL_PATTERN.matcher(sectionText).find());
		Assert.assertTrue("No se encontró nombre visible en Información General",
				sectionText.replace("Información General", "").trim().length() > 8);
		waitVisibleByText("BUSINESS PLAN");
		waitVisibleByText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() throws Exception {
		ensureStepPassed("Administrar Negocios view");
		waitVisibleByText("Cuenta creada");
		waitVisibleByText("Estado activo");
		waitVisibleByText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() throws Exception {
		ensureStepPassed("Administrar Negocios view");
		final WebElement businessesSection = sectionByHeading("Tus Negocios");
		Assert.assertTrue("La sección Tus Negocios no parece contener elementos", businessesSection.getText().length() > 50);
		waitVisibleByText("Agregar Negocio");
		waitVisibleByText("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(final String linkText) throws Exception {
		ensureStepPassed("Administrar Negocios view");

		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String beforeUrl = driver.getCurrentUrl();

		clickByText(linkText);

		final String legalHandle = waitForLegalTabOrNavigation(beforeHandles, beforeUrl);
		final boolean openedNewTab = legalHandle != null;
		if (openedNewTab) {
			driver.switchTo().window(legalHandle);
		}

		waitVisibleByText(linkText);
		final String legalBodyText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
		Assert.assertTrue("No se detectó contenido legal suficiente para " + linkText, legalBodyText.length() > 120);

		capture("05-" + sanitize(linkText));
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitVisibleByText("Administrar Negocios");
		} else {
			driver.navigate().back();
			waitForDocumentReady();
			waitVisibleByText("Administrar Negocios");
		}
	}

	private void runStep(final String label, final StepAction stepAction) {
		try {
			stepAction.run();
			report.put(label, true);
		} catch (Exception ex) {
			report.put(label, false);
			failures.add(label + ": " + ex.getMessage());
			try {
				capture("FAIL-" + sanitize(label));
			} catch (Exception ignored) {
				// Intentionally ignored: test should preserve original failure reason.
			}
		}
	}

	private void printFinalReport() {
		System.out.println("===== SaleADS Mi Negocio Full Test Report =====");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		for (Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
			System.out.println("URL final " + legalUrl.getKey() + ": " + legalUrl.getValue());
		}
		System.out.println("Evidencias: " + evidenceDir.toAbsolutePath());
		System.out.println("===============================================");
	}

	private void ensureStepPassed(final String stepLabel) {
		if (!Boolean.TRUE.equals(report.get(stepLabel))) {
			throw new IllegalStateException("Precondición no cumplida. El paso previo no pasó: " + stepLabel);
		}
	}

	private void navigateToLoginPage() {
		final String loginUrl = resolveConfig(LOGIN_URL_PROPERTY, LOGIN_URL_ENV, "");
		if (loginUrl.isBlank()) {
			throw new IllegalArgumentException(
					"Debe definirse la URL de login en -D" + LOGIN_URL_PROPERTY + " o variable " + LOGIN_URL_ENV);
		}

		driver.get(loginUrl);
		waitForDocumentReady();
	}

	private void completeGoogleSelectorIfPresent() throws Exception {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(90).toMillis();

		while (System.currentTimeMillis() < deadline) {
			waitForDocumentReady();
			switchToMostRelevantWindow();

			if (isVisible(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(GOOGLE_EMAIL) + ")]"))) {
				clickByText(GOOGLE_EMAIL);
				waitForDocumentReady();
			}

			if (isMainApplicationLoaded()) {
				return;
			}

			sleepMillis(750);
		}

		throw new IllegalStateException("No se pudo completar el login y llegar a la app principal en el tiempo esperado");
	}

	private void waitForMainApplication() {
		wait.until(driver -> isMainApplicationLoaded());
	}

	private boolean isMainApplicationLoaded() {
		final List<By> sidebarLocators = Arrays.asList(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class, 'sidebar')]"));

		boolean sidebarVisible = false;
		for (By locator : sidebarLocators) {
			if (isVisible(locator)) {
				sidebarVisible = true;
				break;
			}
		}

		return sidebarVisible && isVisible(By.xpath(
				"//*[contains(normalize-space(.), 'Mi Negocio') or contains(normalize-space(.), 'Negocio')]"));
	}

	private void expandMiNegocioIfNeeded() throws Exception {
		if (isVisible(By.xpath("//*[contains(normalize-space(.), 'Administrar Negocios')]"))) {
			return;
		}

		clickByText("Mi Negocio");
		waitVisibleByText("Administrar Negocios");
	}

	private WebElement sectionByHeading(final String heading) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//*[self::section or self::div][.//*[contains(normalize-space(.), " + xpathLiteral(heading) + ")]]")));
	}

	private WebElement clickByText(final String text) throws Exception {
		final WebElement element = clickFirstClickableByText(text);
		clickAndWait(element);
		return element;
	}

	private WebElement clickFirstClickableByText(final String... texts) {
		Exception lastException = null;
		for (String text : texts) {
			try {
				return wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
						"//*[self::button or self::a or self::span or self::div or @role='button']" +
								"[contains(normalize-space(.), " + xpathLiteral(text) + ")]")));
			} catch (Exception ex) {
				lastException = ex;
			}
		}

		throw new IllegalStateException("No se encontró elemento clickable para textos: " + Arrays.toString(texts), lastException);
	}

	private void clickAndWait(final WebElement element) throws Exception {
		wait.until(ExpectedConditions.visibilityOf(element));
		element.click();
		waitForDocumentReady();
		sleepMillis(500);
	}

	private WebElement waitVisibleByText(final String text) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]")));
	}

	private String waitForLegalTabOrNavigation(final Set<String> beforeHandles, final String beforeUrl) {
		wait.until((ExpectedCondition<Boolean>) d -> d != null &&
				(d.getWindowHandles().size() > beforeHandles.size() || !d.getCurrentUrl().equals(beforeUrl)));

		final Set<String> nowHandles = driver.getWindowHandles();
		for (String handle : nowHandles) {
			if (!beforeHandles.contains(handle)) {
				return handle;
			}
		}

		return null;
	}

	private void switchToMostRelevantWindow() {
		final Set<String> handles = driver.getWindowHandles();
		for (String handle : handles) {
			driver.switchTo().window(handle);
			final String currentUrl = driver.getCurrentUrl();
			if (!currentUrl.contains("accounts.google.com")) {
				return;
			}
		}
	}

	private void waitForDocumentReady() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
	}

	private boolean isVisible(final By by) {
		try {
			return !driver.findElements(by).isEmpty() && driver.findElement(by).isDisplayed();
		} catch (Exception ex) {
			return false;
		}
	}

	private void capture(final String name) throws Exception {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(name + ".png");
		Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private int resolveWaitSeconds() {
		final String configured = System.getProperty(WAIT_SECONDS_PROPERTY, String.valueOf(DEFAULT_WAIT_SECONDS));
		try {
			return Integer.parseInt(configured.trim());
		} catch (NumberFormatException ex) {
			return DEFAULT_WAIT_SECONDS;
		}
	}

	private String resolveConfig(final String property, final String env, final String defaultValue) {
		final String fromProperty = System.getProperty(property);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}

		final String fromEnv = System.getenv(env);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		return defaultValue;
	}

	private String sanitize(final String text) {
		return text.toLowerCase()
				.replace(" ", "-")
				.replace("á", "a")
				.replace("é", "e")
				.replace("í", "i")
				.replace("ó", "o")
				.replace("ú", "u")
				.replaceAll("[^a-z0-9\\-]", "");
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final String[] parts = text.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private void sleepMillis(final long millis) throws InterruptedException {
		Thread.sleep(millis);
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
