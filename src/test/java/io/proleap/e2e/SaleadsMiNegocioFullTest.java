package io.proleap.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
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

/**
 * End-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * Environment variables:
 * <ul>
 * <li>SALEADS_LOGIN_URL (optional): login URL for the current environment.</li>
 * <li>SALEADS_EXPECTED_EMAIL (optional): defaults to juanlucasbarbiergarzon@gmail.com.</li>
 * <li>SALEADS_EXPECTED_NAME (optional): expected user full name in Informacion General.</li>
 * <li>SELENIUM_REMOTE_URL (optional): use a remote Selenium Grid endpoint.</li>
 * <li>SALEADS_HEADLESS (optional): defaults to true.</li>
 * <li>SALEADS_EVIDENCE_DIR (optional): defaults to target/saleads-evidence.</li>
 * </ul>
 * </p>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(25);
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private int screenshotCounter;

	private String expectedEmail;
	private String expectedName;

	@Before
	public void setUp() throws IOException {
		driver = buildDriver();
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
		driver.manage().window().setSize(new Dimension(1920, 1080));
		evidenceDir = buildEvidenceDirectory();
		screenshotCounter = 0;
		expectedEmail = env("SALEADS_EXPECTED_EMAIL", DEFAULT_GOOGLE_ACCOUNT);
		expectedName = env("SALEADS_EXPECTED_NAME", "");

		String loginUrl = env("SALEADS_LOGIN_URL", "");
		if (!loginUrl.isBlank()) {
			driver.get(loginUrl);
		}
		waitForUiReady();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() {
		initializeReport();

		report.put("Login", runStep(this::stepLoginWithGoogle));
		report.put("Mi Negocio menu", runStep(this::stepOpenMiNegocioMenu));
		report.put("Agregar Negocio modal", runStep(this::stepValidateAgregarNegocioModal));
		report.put("Administrar Negocios view", runStep(this::stepOpenAdministrarNegocios));
		report.put("Información General", runStep(this::stepValidateInformacionGeneral));
		report.put("Detalles de la Cuenta", runStep(this::stepValidateDetallesCuenta));
		report.put("Tus Negocios", runStep(this::stepValidateTusNegocios));
		report.put("Términos y Condiciones",
				runStep(() -> validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "terminos-condiciones")));
		report.put("Política de Privacidad",
				runStep(() -> validateLegalLink("Política de Privacidad", "Política de Privacidad", "politica-privacidad")));

		printFinalReport();

		boolean allPassed = report.values().stream().allMatch(Boolean::booleanValue);
		assertTrue("One or more workflow validations failed. Review report output and evidence screenshots.", allPassed);
	}

	private boolean stepLoginWithGoogle() {
		// Some environments may reuse authenticated sessions.
		if (isMainAppVisible()) {
			takeScreenshot("01-dashboard-loaded");
			return true;
		}

		WebElement googleButton = waitClickableByTexts("Sign in with Google", "Iniciar sesión con Google",
				"Iniciar sesion con Google", "Continuar con Google", "Google");
		clickAndWait(googleButton);
		selectGoogleAccountIfPresent(expectedEmail);

		boolean mainInterfaceVisible = isMainAppVisible();
		takeScreenshot("01-dashboard-loaded");
		return mainInterfaceVisible;
	}

	private boolean stepOpenMiNegocioMenu() {
		waitClickableByTexts("Negocio");
		clickByText("Mi Negocio");

		boolean submenuExpanded = anyTextVisible("Agregar Negocio") && anyTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded-menu");
		return submenuExpanded;
	}

	private boolean stepValidateAgregarNegocioModal() {
		clickByText("Agregar Negocio");

		boolean titleVisible = anyTextVisible("Crear Nuevo Negocio");
		boolean inputVisible = anyVisible(
				By.xpath("//label[contains(" + normalizedDomExpression("string(.)") + "," + xpathLiteral(normalizeForSearch("Nombre del Negocio"))
						+ ")]/following::input[1]"),
				By.xpath("//input[contains(" + normalizedDomExpression("@placeholder") + "," + xpathLiteral(normalizeForSearch("Nombre del Negocio"))
						+ ")]"),
				By.xpath("//input[contains(" + normalizedDomExpression("@aria-label") + "," + xpathLiteral(normalizeForSearch("Nombre del Negocio"))
						+ ")]"));
		boolean businessesLimitVisible = anyTextVisible("Tienes 2 de 3 negocios");
		boolean actionButtonsVisible = anyTextVisible("Cancelar") && anyTextVisible("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal");

		// Optional actions requested by workflow.
		WebElement nameField = firstVisible(
				By.xpath("//label[contains(" + normalizedDomExpression("string(.)") + "," + xpathLiteral(normalizeForSearch("Nombre del Negocio"))
						+ ")]/following::input[1]"),
				By.xpath("//input[contains(" + normalizedDomExpression("@placeholder") + "," + xpathLiteral(normalizeForSearch("Nombre del Negocio"))
						+ ")]"),
				By.xpath("//input[contains(" + normalizedDomExpression("@aria-label") + "," + xpathLiteral(normalizeForSearch("Nombre del Negocio"))
						+ ")]"));
		if (nameField != null) {
			nameField.click();
			nameField.clear();
			nameField.sendKeys("Negocio Prueba Automatización");
		}
		if (anyTextVisible("Cancelar")) {
			clickByText("Cancelar");
		}
		waitForUiReady();

		return titleVisible && inputVisible && businessesLimitVisible && actionButtonsVisible;
	}

	private boolean stepOpenAdministrarNegocios() {
		if (!anyTextVisible("Administrar Negocios")) {
			clickByText("Mi Negocio");
		}
		clickByText("Administrar Negocios");

		boolean informacionGeneral = anyTextVisible("Información General", "Informacion General");
		boolean detallesCuenta = anyTextVisible("Detalles de la Cuenta", "Detalles de la cuenta");
		boolean tusNegocios = anyTextVisible("Tus Negocios");
		boolean seccionLegal = anyTextVisible("Sección Legal", "Seccion Legal");

		takeFullPageScreenshot("04-administrar-negocios-view-full");
		return informacionGeneral && detallesCuenta && tusNegocios && seccionLegal;
	}

	private boolean isMainAppVisible() {
		boolean mainInterfaceVisible = anyVisible(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar')]"));
		boolean sidebarNavigationVisible = anyTextVisible("Negocio", "Mi Negocio");
		return mainInterfaceVisible && sidebarNavigationVisible;
	}

	private boolean stepValidateInformacionGeneral() {
		boolean emailVisible = anyTextVisible(expectedEmail);
		boolean businessPlanVisible = anyTextVisible("BUSINESS PLAN");
		boolean cambiarPlanVisible = anyTextVisible("Cambiar Plan");

		boolean userNameVisible;
		if (!expectedName.isBlank()) {
			userNameVisible = anyTextVisible(expectedName);
		} else {
			userNameVisible = anyTextVisible("Nombre", "Usuario") || hasNonEmailProfileTextNearInformacionGeneral();
		}

		return userNameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean stepValidateDetallesCuenta() {
		boolean cuentaCreadaVisible = anyTextVisible("Cuenta creada");
		boolean estadoActivoVisible = anyTextVisible("Estado activo");
		boolean idiomaSeleccionadoVisible = anyTextVisible("Idioma seleccionado");
		return cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible;
	}

	private boolean stepValidateTusNegocios() {
		boolean sectionVisible = anyTextVisible("Tus Negocios");
		boolean addButtonVisible = anyTextVisible("Agregar Negocio");
		boolean limitTextVisible = anyTextVisible("Tienes 2 de 3 negocios");
		boolean businessListVisible = anyVisible(
				By.xpath("//*[contains(" + normalizedDomExpression("string(.)") + "," + xpathLiteral(normalizeForSearch("Tus Negocios"))
						+ ")]/following::*[self::li or self::tr or contains(@class,'business')][1]"),
				By.xpath("//ul/li"),
				By.xpath("//table//tr"));
		return sectionVisible && addButtonVisible && limitTextVisible && businessListVisible;
	}

	private boolean validateLegalLink(String linkText, String headingText, String evidenceName) {
		String appHandle = driver.getWindowHandle();
		Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		String urlBefore = driver.getCurrentUrl();

		clickByText(linkText);
		waitForUiReady();

		String activeHandle = waitForNewTabOrNavigation(appHandle, handlesBefore, urlBefore);
		if (!activeHandle.equals(driver.getWindowHandle())) {
			driver.switchTo().window(activeHandle);
		}
		waitForUiReady();

		boolean headingVisible = anyTextVisible(headingText);
		boolean legalContentVisible = anyVisible(
				By.xpath("//article//*[string-length(normalize-space()) > 60]"),
				By.xpath("//main//*[string-length(normalize-space()) > 60]"),
				By.xpath("//p[string-length(normalize-space()) > 60]"));

		takeScreenshot("05-" + evidenceName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (!activeHandle.equals(appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiReady();
		} else if (!safeCurrentUrl().equals(urlBefore)) {
			driver.navigate().back();
			waitForUiReady();
		}

		return headingVisible && legalContentVisible;
	}

	private String waitForNewTabOrNavigation(String appHandle, Set<String> handlesBefore, String oldUrl) {
		Instant until = Instant.now().plus(Duration.ofSeconds(12));
		while (Instant.now().isBefore(until)) {
			Set<String> handlesNow = driver.getWindowHandles();
			if (handlesNow.size() > handlesBefore.size()) {
				for (String handle : handlesNow) {
					if (!handlesBefore.contains(handle)) {
						return handle;
					}
				}
			}
			if (!safeCurrentUrl().equals(oldUrl)) {
				return appHandle;
			}
			sleep(250);
		}
		return appHandle;
	}

	private void selectGoogleAccountIfPresent(String googleEmail) {
		List<By> accountSelectors = List.of(
				visibleTextLocator(googleEmail),
				visibleTextLocator("Use another account"),
				visibleTextLocator("Seleccionar una cuenta"));

		for (By selector : accountSelectors) {
			try {
				WebElement element = new WebDriverWait(driver, Duration.ofSeconds(8))
						.until(ExpectedConditions.elementToBeClickable(selector));
				clickAndWait(element);
				return;
			} catch (TimeoutException ignored) {
				// Account selector might not appear if session is already authenticated.
			}
		}
	}

	private void clickByText(String text) {
		WebElement target = waitClickableByTexts(text);
		clickAndWait(target);
	}

	private WebElement waitClickableByTexts(String... texts) {
		TimeoutException last = null;
		for (String text : texts) {
			By locator = clickableTextLocator(text);
			try {
				return wait.until(ExpectedConditions.elementToBeClickable(locator));
			} catch (TimeoutException ex) {
				last = ex;
			}
		}
		if (last != null) {
			throw last;
		}
		throw new TimeoutException("No clickable element found for text options.");
	}

	private void clickAndWait(WebElement element) {
		element.click();
		waitForUiReady();
	}

	private void waitForUiReady() {
		try {
			wait.until((ExpectedCondition<Boolean>) wd -> {
				Object state = ((JavascriptExecutor) wd).executeScript("return document.readyState");
				return "complete".equals(state);
			});
		} catch (Exception ignored) {
			// Some pages during redirects can transiently block script execution.
		}

		for (By spinner : loadingIndicators()) {
			try {
				wait.until(ExpectedConditions.invisibilityOfElementLocated(spinner));
			} catch (Exception ignored) {
				// Indicator not present is acceptable.
			}
		}
		sleep(350);
	}

	private List<By> loadingIndicators() {
		List<By> indicators = new ArrayList<>();
		indicators.add(By.cssSelector("[aria-busy='true']"));
		indicators.add(By.cssSelector(".spinner"));
		indicators.add(By.cssSelector(".loading"));
		indicators.add(By.cssSelector("[data-testid*='loading']"));
		return indicators;
	}

	private boolean anyTextVisible(String... texts) {
		for (String text : texts) {
			if (anyVisible(visibleTextLocator(text))) {
				return true;
			}
		}
		return false;
	}

	private boolean anyVisible(By... locators) {
		for (By locator : locators) {
			try {
				List<WebElement> elements = driver.findElements(locator);
				for (WebElement element : elements) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			} catch (Exception ignored) {
				// Continue trying other locators.
			}
		}
		return false;
	}

	private WebElement firstVisible(By... locators) {
		for (By locator : locators) {
			try {
				List<WebElement> elements = driver.findElements(locator);
				for (WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			} catch (Exception ignored) {
				// Try next locator.
			}
		}
		return null;
	}

	private boolean hasNonEmailProfileTextNearInformacionGeneral() {
		String containerXpath = "//*[contains(" + normalizedDomExpression("string(.)") + ","
				+ xpathLiteral(normalizeForSearch("Información General")) + ")]/ancestor::*[self::section or self::div][1]";
		List<WebElement> labels = driver.findElements(By.xpath(containerXpath
				+ "//*[self::h1 or self::h2 or self::h3 or self::span or self::p][string-length(normalize-space()) > 2]"));
		for (WebElement label : labels) {
			if (!label.isDisplayed()) {
				continue;
			}
			String text = normalizeForSearch(label.getText());
			if (text.isBlank()) {
				continue;
			}
			if (text.contains("@")) {
				continue;
			}
			if (text.equals(normalizeForSearch("Información General"))
					|| text.equals(normalizeForSearch("BUSINESS PLAN"))
					|| text.equals(normalizeForSearch("Cambiar Plan"))) {
				continue;
			}
			return true;
		}
		return false;
	}

	private By clickableTextLocator(String text) {
		String normalized = normalizeForSearch(text);
		String expr = normalizedDomExpression("string(.)");
		String value = xpathLiteral(normalized);
		return By.xpath(
				"(//button|//a|//*[@role='button']|//div[@role='button']|//span/ancestor::*[self::button or self::a][1])"
						+ "[contains(" + expr + "," + value + ")]");
	}

	private By visibleTextLocator(String text) {
		String expr = normalizedDomExpression("string(.)");
		return By.xpath("//*[contains(" + expr + "," + xpathLiteral(normalizeForSearch(text)) + ")]");
	}

	private String normalizedDomExpression(String domSource) {
		return "translate(translate(normalize-space(" + domSource + "),"
				+ "'ÁÉÍÓÚáéíóúÄËÏÖÜäëïöüÑñ',"
				+ "'AEIOUaeiouAEIOUaeiouNn'),"
				+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
				+ "'abcdefghijklmnopqrstuvwxyz')";
	}

	private String normalizeForSearch(String value) {
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT)
				.trim();
		return normalized.replaceAll("\\s+", " ");
	}

	private String xpathLiteral(String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		StringBuilder result = new StringBuilder("concat(");
		String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				result.append(", \"'\", ");
			}
			result.append("'").append(parts[i]).append("'");
		}
		result.append(")");
		return result.toString();
	}

	private void takeScreenshot(String name) {
		try {
			if (!(driver instanceof TakesScreenshot)) {
				return;
			}
			screenshotCounter++;
			String filename = String.format("%02d_%s.png", screenshotCounter, sanitizeFileName(name));
			File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			Files.copy(source.toPath(), evidenceDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception ignored) {
			// Test should not fail only because screenshot writing failed.
		}
	}

	private void takeFullPageScreenshot(String name) {
		Dimension originalSize = null;
		try {
			long scrollHeight = 0L;
			long scrollWidth = 0L;
			if (driver instanceof JavascriptExecutor) {
				Object height = ((JavascriptExecutor) driver)
						.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
				Object width = ((JavascriptExecutor) driver)
						.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);");
				if (height instanceof Number) {
					scrollHeight = ((Number) height).longValue();
				}
				if (width instanceof Number) {
					scrollWidth = ((Number) width).longValue();
				}
			}

			if (scrollHeight > 0 && scrollWidth > 0) {
				originalSize = driver.manage().window().getSize();
				int safeWidth = (int) Math.min(Math.max(scrollWidth + 40, 1280), 2200);
				int safeHeight = (int) Math.min(Math.max(scrollHeight + 120, 1080), 6000);
				driver.manage().window().setSize(new Dimension(safeWidth, safeHeight));
				waitForUiReady();
			}

			takeScreenshot(name);
		} catch (Exception ignored) {
			takeScreenshot(name);
		} finally {
			if (originalSize != null) {
				try {
					driver.manage().window().setSize(originalSize);
					waitForUiReady();
				} catch (Exception ignored) {
					// Ignore restore failures.
				}
			}
		}
	}

	private String sanitizeFileName(String input) {
		return input.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private Path buildEvidenceDirectory() throws IOException {
		String configuredRoot = env("SALEADS_EVIDENCE_DIR", "target/saleads-evidence");
		String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).format(java.time.LocalDateTime.now());
		Path path = Path.of(configuredRoot, runId);
		Files.createDirectories(path);
		return path;
	}

	private WebDriver buildDriver() {
		ChromeOptions options = new ChromeOptions();
		boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--disable-gpu");

		String remoteUrl = env("SELENIUM_REMOTE_URL", "");
		if (!remoteUrl.isBlank()) {
			try {
				return new RemoteWebDriver(new URL(remoteUrl), options);
			} catch (MalformedURLException ex) {
				throw new IllegalArgumentException("Invalid SELENIUM_REMOTE_URL: " + remoteUrl, ex);
			}
		}
		return new ChromeDriver(options);
	}

	private String env(String key, String defaultValue) {
		String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return value;
	}

	private boolean runStep(Step step) {
		try {
			return step.execute();
		} catch (Exception ex) {
			System.err.println("[ERROR] Step failure: " + ex.getMessage());
			return false;
		}
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

	private void printFinalReport() {
		System.out.println("========== SaleADS Mi Negocio Final Report ==========");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("---------- Captured Legal URLs ----------");
			for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(entry.getKey() + ": " + entry.getValue());
			}
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("=====================================================");
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (Exception ex) {
			return "";
		}
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface Step {
		boolean execute();
	}
}
