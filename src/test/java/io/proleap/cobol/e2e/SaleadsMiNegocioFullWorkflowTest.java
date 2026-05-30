package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Environment-agnostic E2E flow for SaleADS.ai "Mi Negocio".
 *
 * Required input:
 * - SALEADS_LOGIN_URL (or -Dsaleads.loginUrl): login URL for the current
 * environment.
 *
 * Optional input:
 * - SALEADS_BROWSER (or -Dsaleads.browser): chrome (default) or firefox.
 * - SALEADS_HEADLESS (or -Dsaleads.headless): true (default) or false.
 * - SALEADS_REMOTE_WEBDRIVER_URL (or -Dsaleads.remoteWebDriverUrl): remote grid URL.
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MENU = "Mi Negocio menu";
	private static final String REPORT_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO = "Informaci\u00f3n General";
	private static final String REPORT_DETAILS = "Detalles de la Cuenta";
	private static final String REPORT_BUSINESSES = "Tus Negocios";
	private static final String REPORT_TERMS = "T\u00e9rminos y Condiciones";
	private static final String REPORT_PRIVACY = "Pol\u00edtica de Privacidad";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> stepReport = new LinkedHashMap<>();
	private final Map<String, String> failureMessages = new LinkedHashMap<>();
	private final Map<String, String> legalEvidenceUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private int screenshotCounter = 0;

	@Before
	public void setUp() throws Exception {
		screenshotDir = Paths.get("target", "saleads-evidence", "screenshots");
		Files.createDirectories(screenshotDir);

		driver = createWebDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		driver.manage().window().setSize(new Dimension(1920, 1080));

		final String loginUrl = requireConfig("saleads.loginUrl", "SALEADS_LOGIN_URL");
		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFO, this::stepValidateInformacionGeneral);
		runStep(REPORT_DETAILS, this::stepValidateDetallesDeLaCuenta);
		runStep(REPORT_BUSINESSES, this::stepValidateTusNegocios);
		runStep(REPORT_TERMS, () -> stepValidateLegalDocument(REPORT_TERMS,
				Arrays.asList("Terminos y Condiciones", "T\u00e9rminos y Condiciones"),
				Arrays.asList("Terminos y Condiciones", "T\u00e9rminos y Condiciones")));
		runStep(REPORT_PRIVACY, () -> stepValidateLegalDocument(REPORT_PRIVACY,
				Arrays.asList("Politica de Privacidad", "Pol\u00edtica de Privacidad"),
				Arrays.asList("Politica de Privacidad", "Pol\u00edtica de Privacidad")));

		printFinalReport();

		boolean allPassed = stepReport.values().stream().allMatch(Boolean::booleanValue);
		assertTrue("At least one workflow validation failed. Check report output for details.", allPassed);
	}

	private void stepLoginWithGoogle() throws IOException {
		clickByAnyText(Arrays.asList("Sign in with Google", "Iniciar sesion con Google", "Continuar con Google", "Google"));
		handleGoogleAccountSelection("juanlucasbarbiergarzon@gmail.com");

		waitForAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Inicio"));
		waitForSidebar();
		captureScreenshot("dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickIfPresent(Arrays.asList("Negocio"));
		clickByAnyText(Arrays.asList("Mi Negocio"));

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByAnyText(Arrays.asList("Agregar Negocio"));
		assertVisibleText("Crear Nuevo Negocio");
		assertInputOrLabelPresent("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		captureScreenshot("agregar-negocio-modal");

		WebElement input = waitForElement(
				By.xpath("//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio']"));
		input.clear();
		input.sendKeys("Negocio Prueba Automatizacion");
		clickByAnyText(Arrays.asList("Cancelar"));
		waitForUiToLoad();
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byTextContains("Crear Nuevo Negocio")));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureAdminMenuVisible();
		clickByAnyText(Arrays.asList("Administrar Negocios"));

		assertVisibleAnyText(Arrays.asList("Informacion General", "Informacion general", "Informacion",
				"Informaci\u00f3n General", "Informaci\u00f3n general"));
		assertVisibleAnyText(Arrays.asList("Detalles de la Cuenta", "Detalles de la cuenta"));
		assertVisibleAnyText(Arrays.asList("Tus Negocios"));
		assertVisibleAnyText(Arrays.asList("Seccion Legal", "Secci\u00f3n Legal", "Legal"));
		captureFullPageScreenshot("administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleAnyText(Arrays.asList("BUSINESS PLAN"));
		assertVisibleAnyText(Arrays.asList("Cambiar Plan"));

		String pageText = getPageText();
		Matcher matcher = EMAIL_PATTERN.matcher(pageText);
		assertTrue("User email should be visible", matcher.find());

		List<String> candidateLines = new ArrayList<>();
		for (String line : pageText.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.length() < 3) {
				continue;
			}
			if (trimmed.contains("@")) {
				continue;
			}
			String lower = trimmed.toLowerCase(Locale.ROOT);
			if (lower.contains("informacion") || lower.contains("business plan") || lower.contains("cambiar plan")
					|| lower.contains("cuenta creada") || lower.contains("estado activo")
					|| lower.contains("idioma seleccionado")) {
				continue;
			}
			if (trimmed.matches(".*[A-Za-z].*")) {
				candidateLines.add(trimmed);
			}
		}
		assertTrue("User name should be visible in Informacion General", !candidateLines.isEmpty());
	}

	private void stepValidateDetallesDeLaCuenta() {
		assertVisibleAnyText(Arrays.asList("Cuenta creada"));
		assertVisibleAnyText(Arrays.asList("Estado activo"));
		assertVisibleAnyText(Arrays.asList("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		assertVisibleAnyText(Arrays.asList("Tus Negocios"));
		assertVisibleAnyText(Arrays.asList("Agregar Negocio"));
		assertVisibleAnyText(Arrays.asList("Tienes 2 de 3 negocios"));
	}

	private void stepValidateLegalDocument(final String evidenceKey, final List<String> linkTexts,
			final List<String> headingTexts) throws IOException {
		String appWindow = driver.getWindowHandle();
		Set<String> handlesBefore = driver.getWindowHandles();
		String currentUrl = driver.getCurrentUrl();

		clickByAnyText(linkTexts);
		String targetWindow = waitForNavigationOrNewTab(handlesBefore, currentUrl, appWindow);
		driver.switchTo().window(targetWindow);
		waitForUiToLoad();

		assertVisibleAnyText(headingTexts);
		String legalText = getPageText();
		assertTrue("Legal content text should be visible", legalText != null && legalText.trim().length() > 120);

		String prefix = sanitize(evidenceKey);
		captureScreenshot(prefix + "-legal-page");
		legalEvidenceUrls.put(evidenceKey, driver.getCurrentUrl());

		if (!Objects.equals(targetWindow, appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private WebDriver createWebDriver() throws Exception {
		boolean headless = Boolean.parseBoolean(getConfig("saleads.headless", "SALEADS_HEADLESS", "true"));
		String browser = getConfig("saleads.browser", "SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		String remoteUrl = getConfig("saleads.remoteWebDriverUrl", "SALEADS_REMOTE_WEBDRIVER_URL", null);

		switch (browser) {
		case "firefox":
			FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			return remoteUrl != null ? new RemoteWebDriver(new URL(remoteUrl), firefoxOptions) : new FirefoxDriver(firefoxOptions);
		case "chrome":
		default:
			ChromeOptions chromeOptions = new ChromeOptions();
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			chromeOptions.addArguments("--disable-gpu");
			chromeOptions.addArguments("--no-sandbox");
			chromeOptions.addArguments("--window-size=1920,1080");
			chromeOptions.addArguments("--lang=es-ES");
			return remoteUrl != null ? new RemoteWebDriver(URI.create(remoteUrl).toURL(), chromeOptions)
					: new ChromeDriver(chromeOptions);
		}
	}

	private void runStep(final String field, final CheckedRunnable runnable) {
		try {
			runnable.run();
			stepReport.put(field, Boolean.TRUE);
		} catch (Throwable t) {
			stepReport.put(field, Boolean.FALSE);
			failureMessages.put(field, t.getMessage());
		}
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio workflow report ===");
		System.out.println("Login: " + toPassFail(stepReport.get(REPORT_LOGIN)));
		System.out.println("Mi Negocio menu: " + toPassFail(stepReport.get(REPORT_MENU)));
		System.out.println("Agregar Negocio modal: " + toPassFail(stepReport.get(REPORT_MODAL)));
		System.out.println("Administrar Negocios view: " + toPassFail(stepReport.get(REPORT_ADMIN_VIEW)));
		System.out.println("Informaci\u00f3n General: " + toPassFail(stepReport.get(REPORT_INFO)));
		System.out.println("Detalles de la Cuenta: " + toPassFail(stepReport.get(REPORT_DETAILS)));
		System.out.println("Tus Negocios: " + toPassFail(stepReport.get(REPORT_BUSINESSES)));
		System.out.println("T\u00e9rminos y Condiciones: " + toPassFail(stepReport.get(REPORT_TERMS)));
		System.out.println("Pol\u00edtica de Privacidad: " + toPassFail(stepReport.get(REPORT_PRIVACY)));

		if (!legalEvidenceUrls.isEmpty()) {
			System.out.println("Legal URLs:");
			legalEvidenceUrls.forEach((k, v) -> System.out.println(" - " + k + ": " + v));
		}

		if (!failureMessages.isEmpty()) {
			System.out.println("Validation errors:");
			failureMessages.forEach((k, v) -> System.out.println(" - " + k + ": " + v));
		}
	}

	private void handleGoogleAccountSelection(final String email) {
		String currentWindow = driver.getWindowHandle();
		Set<String> initialHandles = driver.getWindowHandles();
		Instant deadline = Instant.now().plus(Duration.ofSeconds(20));

		while (Instant.now().isBefore(deadline)) {
			Set<String> nowHandles = driver.getWindowHandles();
			if (nowHandles.size() > initialHandles.size()) {
				for (String handle : nowHandles) {
					if (!initialHandles.contains(handle)) {
						driver.switchTo().window(handle);
						clickIfPresent(Arrays.asList(email));
						waitForUiToLoad();
						driver.switchTo().window(currentWindow);
						waitForUiToLoad();
						return;
					}
				}
			}

			if (isTextPresent(email)) {
				clickIfPresent(Arrays.asList(email));
				waitForUiToLoad();
				return;
			}
			sleep(300);
		}
	}

	private void ensureAdminMenuVisible() {
		if (isTextPresent("Administrar Negocios")) {
			return;
		}
		clickIfPresent(Arrays.asList("Mi Negocio"));
		if (!isTextPresent("Administrar Negocios")) {
			clickIfPresent(Arrays.asList("Negocio"));
		}
	}

	private String waitForNavigationOrNewTab(final Set<String> handlesBefore, final String urlBefore, final String appWindow) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
		while (Instant.now().isBefore(deadline)) {
			Set<String> currentHandles = driver.getWindowHandles();
			if (currentHandles.size() > handlesBefore.size()) {
				for (String handle : currentHandles) {
					if (!handlesBefore.contains(handle)) {
						return handle;
					}
				}
			}
			if (!Objects.equals(urlBefore, driver.getCurrentUrl())) {
				return appWindow;
			}
			sleep(250);
		}
		throw new TimeoutException("Expected a new tab or URL change after clicking legal link.");
	}

	private void waitForSidebar() {
		wait.until(d -> {
			for (By locator : Arrays.asList(By.tagName("aside"), By.xpath("//nav"), By.xpath("//*[@role='navigation']"))) {
				for (WebElement element : d.findElements(locator)) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			}
			return false;
		});
	}

	private void clickByAnyText(final List<String> texts) {
		WebElement element = waitForAnyVisibleText(texts);
		scrollIntoView(element);
		wait.until(ExpectedConditions.elementToBeClickable(element));
		try {
			element.click();
		} catch (RuntimeException e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private boolean clickIfPresent(final List<String> texts) {
		for (String text : texts) {
			try {
				List<WebElement> elements = driver.findElements(byTextContains(text));
				for (WebElement element : elements) {
					if (element.isDisplayed()) {
						scrollIntoView(element);
						try {
							new Actions(driver).moveToElement(element).pause(Duration.ofMillis(100)).click().perform();
						} catch (RuntimeException ignored) {
							((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
						}
						waitForUiToLoad();
						return true;
					}
				}
			} catch (RuntimeException ignored) {
				// Keep searching for other text options.
			}
		}
		return false;
	}

	private void assertVisibleText(final String text) {
		WebElement element = waitForAnyVisibleText(Arrays.asList(text));
		assertTrue("Expected visible text: " + text, element.isDisplayed());
	}

	private void assertVisibleAnyText(final List<String> texts) {
		WebElement element = waitForAnyVisibleText(texts);
		assertTrue("Expected at least one visible text from: " + texts, element.isDisplayed());
	}

	private void assertInputOrLabelPresent(final String text) {
		By locator = By.xpath("//label[contains(normalize-space(.), " + toXPathLiteral(text)
				+ ")] | //input[@placeholder=" + toXPathLiteral(text) + " or @aria-label=" + toXPathLiteral(text) + "]");
		WebElement element = waitForElement(locator);
		assertTrue("Expected input or label: " + text, element.isDisplayed());
	}

	private WebElement waitForAnyVisibleText(final List<String> texts) {
		return wait.until(d -> {
			for (String text : texts) {
				for (WebElement candidate : d.findElements(byTextContains(text))) {
					if (candidate.isDisplayed()) {
						return candidate;
					}
				}
			}
			return null;
		});
	}

	private WebElement waitForElement(final By by) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private boolean isTextPresent(final String text) {
		for (WebElement candidate : driver.findElements(byTextContains(text))) {
			if (candidate.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private String getPageText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			wait.until(d -> {
				List<WebElement> spinners = d.findElements(By.cssSelector(
						"[aria-busy='true'], .loading, .spinner, .ant-spin-spinning, [data-testid='loading'], [data-testid='spinner']"));
				for (WebElement spinner : spinners) {
					if (spinner.isDisplayed()) {
						return false;
					}
				}
				return true;
			});
		} catch (TimeoutException ignored) {
			// Some pages do not expose stable loading indicators.
		}
		sleep(400);
	}

	private void captureScreenshot(final String label) throws IOException {
		screenshotCounter++;
		Path path = screenshotDir.resolve(String.format("%02d-%s.png", screenshotCounter, sanitize(label)));
		File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), path, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Saved screenshot: " + path);
	}

	private void captureFullPageScreenshot(final String label) throws IOException {
		Dimension originalSize = driver.manage().window().getSize();
		try {
			long width = ((Number) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.documentElement.scrollWidth, document.body.scrollWidth);"))
					.longValue();
			long height = ((Number) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.documentElement.scrollHeight, document.body.scrollHeight);"))
					.longValue();

			int targetWidth = (int) Math.min(Math.max(width + 120, originalSize.getWidth()), 2000);
			int targetHeight = (int) Math.min(Math.max(height + 200, originalSize.getHeight()), 4000);
			driver.manage().window().setSize(new Dimension(targetWidth, targetHeight));
			waitForUiToLoad();
			captureScreenshot(label);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}
	}

	private static String getConfig(final String propertyName, final String envName, final String defaultValue) {
		String fromProperty = System.getProperty(propertyName);
		if (fromProperty != null && !fromProperty.trim().isEmpty()) {
			return fromProperty.trim();
		}

		String fromEnv = System.getenv(envName);
		if (fromEnv != null && !fromEnv.trim().isEmpty()) {
			return fromEnv.trim();
		}

		return defaultValue;
	}

	private static String requireConfig(final String propertyName, final String envName) {
		String value = getConfig(propertyName, envName, null);
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("Missing required configuration: " + propertyName + " or " + envName);
		}
		return value;
	}

	private static String toPassFail(final Boolean value) {
		return Boolean.TRUE.equals(value) ? "PASS" : "FAIL";
	}

	private static By byTextContains(final String text) {
		return By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]");
	}

	private static String sanitize(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static String toXPathLiteral(final String value) {
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

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void scrollIntoView(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		} catch (NoSuchElementException ignored) {
			// Ignore if element vanishes after re-render.
		}
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
