package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration STEP_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration UI_LOAD_TIMEOUT = Duration.ofSeconds(20);

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> evidence = new LinkedHashMap<>();

	private ChromeDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (Boolean.parseBoolean(readSetting("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, STEP_TIMEOUT);
		evidenceDir = Paths.get("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		initializeReport();

		final String loginUrl = readSetting("saleads.login.url", "SALEADS_LOGIN_URL", null);
		if (loginUrl == null || loginUrl.isBlank()) {
			throw new AssertionError(
					"Missing SaleADS login URL. Provide -Dsaleads.login.url=<url> or SALEADS_LOGIN_URL.");
		}

		driver.get(loginUrl);
		waitForUiToLoad();

		runLoginWithGoogleStep();
		runMiNegocioMenuStep();
		runAgregarNegocioModalStep();
		runAdministrarNegociosStep();
		runInformacionGeneralStep();
		runDetallesCuentaStep();
		runTusNegociosStep();
		runLegalDocumentStep("T\u00e9rminos y Condiciones", "T\u00e9rminos y Condiciones", "T\u00e9rminos y Condiciones",
				"08-terminos");
		runLegalDocumentStep("Pol\u00edtica de Privacidad", "Pol\u00edtica de Privacidad", "Pol\u00edtica de Privacidad",
				"09-politica");

		printFinalReport();
		assertTrue("One or more workflow validations failed. Check console output and evidence folder: " + evidenceDir,
				report.values().stream().allMatch(Boolean::booleanValue));
	}

	private void initializeReport() {
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Informaci\u00f3n General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("T\u00e9rminos y Condiciones", false);
		report.put("Pol\u00edtica de Privacidad", false);
	}

	private void runLoginWithGoogleStep() throws IOException {
		boolean success = true;
		try {
			if (!hasAppMainInterface()) {
				clickByAnyVisibleText(List.of("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google",
						"Ingresar con Google", "Login with Google"), true);
				selectGoogleAccountIfVisible(GOOGLE_ACCOUNT_EMAIL);
			}

			success &= isAnyTextVisible(List.of("Mi Negocio", "Negocio"));
			success &= isSidebarVisible();
			takeViewportScreenshot("01-dashboard-loaded");
		} catch (Exception ex) {
			success = false;
			takeViewportScreenshot("01-login-failed");
		}
		report.put("Login", success);
	}

	private void runMiNegocioMenuStep() throws IOException {
		boolean success = true;
		try {
			clickByAnyVisibleText(List.of("Negocio"), false);
			clickByAnyVisibleText(List.of("Mi Negocio"), true);
			success &= isAnyTextVisible(List.of("Agregar Negocio"));
			success &= isAnyTextVisible(List.of("Administrar Negocios"));
			takeViewportScreenshot("02-mi-negocio-menu-expanded");
		} catch (Exception ex) {
			success = false;
			takeViewportScreenshot("02-mi-negocio-menu-failed");
		}
		report.put("Mi Negocio menu", success);
	}

	private void runAgregarNegocioModalStep() throws IOException {
		boolean success = true;
		try {
			clickByAnyVisibleText(List.of("Agregar Negocio"), true);
			success &= waitForVisibleText("Crear Nuevo Negocio");
			success &= waitForVisibleText("Nombre del Negocio");
			success &= waitForVisibleText("Tienes 2 de 3 negocios");
			success &= waitForVisibleText("Cancelar");
			success &= waitForVisibleText("Crear Negocio");

			typeInFirstVisibleInput("Nombre del Negocio", "Negocio Prueba Automatizacion");
			clickByAnyVisibleText(List.of("Cancelar"), true);
			takeViewportScreenshot("03-agregar-negocio-modal");
		} catch (Exception ex) {
			success = false;
			takeViewportScreenshot("03-agregar-negocio-modal-failed");
		}
		report.put("Agregar Negocio modal", success);
	}

	private void runAdministrarNegociosStep() throws IOException {
		boolean success = true;
		try {
			ensureMenuOptionVisible("Administrar Negocios");
			clickByAnyVisibleText(List.of("Administrar Negocios"), true);

			success &= waitForVisibleText("Informaci\u00f3n General");
			success &= waitForVisibleText("Detalles de la Cuenta");
			success &= waitForVisibleText("Tus Negocios");
			success &= waitForVisibleText("Secci\u00f3n Legal");
			takeFullPageScreenshot("04-administrar-negocios-view");
		} catch (Exception ex) {
			success = false;
			takeViewportScreenshot("04-administrar-negocios-failed");
		}
		report.put("Administrar Negocios view", success);
	}

	private void runInformacionGeneralStep() {
		boolean success = true;
		success &= isAnyTextVisible(List.of("Informaci\u00f3n General"));
		success &= isEmailVisible();
		success &= isAnyTextVisible(List.of("BUSINESS PLAN"));
		success &= isAnyTextVisible(List.of("Cambiar Plan"));
		success &= hasLabeledValue("Nombre");
		report.put("Informaci\u00f3n General", success);
	}

	private void runDetallesCuentaStep() {
		boolean success = true;
		success &= isAnyTextVisible(List.of("Cuenta creada"));
		success &= isAnyTextVisible(List.of("Estado activo", "Estado Activo"));
		success &= isAnyTextVisible(List.of("Idioma seleccionado"));
		report.put("Detalles de la Cuenta", success);
	}

	private void runTusNegociosStep() {
		boolean success = true;
		success &= isAnyTextVisible(List.of("Tus Negocios"));
		success &= isAnyTextVisible(List.of("Agregar Negocio"));
		success &= isAnyTextVisible(List.of("Tienes 2 de 3 negocios"));
		success &= isBusinessListVisible();
		report.put("Tus Negocios", success);
	}

	private void runLegalDocumentStep(final String linkText, final String expectedHeading, final String reportKey,
			final String screenshotPrefix) throws IOException {
		boolean success = true;
		String finalUrl = "N/A";

		final String appWindowHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String appUrlBeforeClick = driver.getCurrentUrl();

		try {
			clickByAnyVisibleText(List.of(linkText), true);

			final String newHandle = waitForNewWindowHandle(handlesBefore);
			if (newHandle != null) {
				driver.switchTo().window(newHandle);
				waitForUiToLoad();
			}

			success &= waitForVisibleText(expectedHeading);
			success &= hasLegalContent();
			finalUrl = driver.getCurrentUrl();

			takeViewportScreenshot(screenshotPrefix + "-page");
		} catch (Exception ex) {
			success = false;
			takeViewportScreenshot(screenshotPrefix + "-failed");
		} finally {
			evidence.put(reportKey + " URL", finalUrl);
			restoreAppContext(appWindowHandle, appUrlBeforeClick);
		}

		report.put(reportKey, success);
	}

	private void restoreAppContext(final String appWindowHandle, final String appUrlBeforeClick) {
		final Set<String> handles = new LinkedHashSet<>(driver.getWindowHandles());
		if (handles.contains(appWindowHandle)) {
			for (final String handle : handles) {
				if (!handle.equals(appWindowHandle)) {
					driver.switchTo().window(handle);
					driver.close();
				}
			}
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		}

		if (!driver.getCurrentUrl().equals(appUrlBeforeClick)) {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private boolean hasAppMainInterface() {
		return isSidebarVisible() && isAnyTextVisible(List.of("Mi Negocio", "Negocio"));
	}

	private boolean hasLabeledValue(final String labelText) {
		final String xpath = "//*[contains(normalize-space(), " + asXPathLiteral(labelText)
				+ ")]/following::*[normalize-space()][1]";
		final List<WebElement> candidates = driver.findElements(By.xpath(xpath));
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed() && !candidate.getText().trim().isEmpty()
					&& !candidate.getText().trim().equalsIgnoreCase(labelText)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasLegalContent() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p[normalize-space()]"));
		int totalLength = 0;
		for (final WebElement paragraph : paragraphs) {
			if (paragraph.isDisplayed()) {
				totalLength += paragraph.getText().trim().length();
			}
		}

		return totalLength >= 150;
	}

	private boolean isSidebarVisible() {
		final List<WebElement> sidebars = new ArrayList<>();
		sidebars.addAll(driver.findElements(By.xpath("//aside")));
		sidebars.addAll(driver.findElements(By.xpath("//nav")));
		for (final WebElement sidebar : sidebars) {
			if (sidebar.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean isBusinessListVisible() {
		final List<WebElement> collections = driver.findElements(By.xpath(
				"//*[contains(normalize-space(),'Tus Negocios')]/ancestor::*[self::section or self::div][1]"
						+ "//*[self::li or self::table or @role='row' or contains(@class,'business') or contains(@class,'negocio')]"));
		for (final WebElement collection : collections) {
			if (collection.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean isEmailVisible() {
		final Pattern emailPattern = Pattern.compile(".+@.+\\..+");
		final List<WebElement> withAt = driver.findElements(By.xpath("//*[contains(normalize-space(),'@')]"));
		for (final WebElement element : withAt) {
			if (element.isDisplayed() && emailPattern.matcher(element.getText().trim()).matches()) {
				return true;
			}
		}
		return false;
	}

	private boolean waitForVisibleText(final String text) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(textXPath(text))));
			return true;
		} catch (TimeoutException timeout) {
			return false;
		}
	}

	private boolean isAnyTextVisible(final List<String> candidates) {
		for (final String text : candidates) {
			final List<WebElement> elements = driver.findElements(By.xpath(textXPath(text)));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void ensureMenuOptionVisible(final String text) {
		if (!isAnyTextVisible(List.of(text))) {
			clickByAnyVisibleText(List.of("Mi Negocio"), true);
		}
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String maybeNewHandle = waitForNewWindowHandle(handlesBefore);
		if (maybeNewHandle != null) {
			driver.switchTo().window(maybeNewHandle);
		}

		if (isAnyTextVisible(List.of(email))) {
			clickByAnyVisibleText(List.of(email), true);
		}

		// Return to app window if Google login opened in a separate tab.
		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (!driver.getCurrentUrl().contains("accounts.google.com")) {
				break;
			}
		}
		waitForUiToLoad();
	}

	private String waitForNewWindowHandle(final Set<String> handlesBefore) {
		try {
			wait.until(new ExpectedCondition<Boolean>() {
				@Override
				public Boolean apply(final org.openqa.selenium.WebDriver webDriver) {
					return webDriver != null && webDriver.getWindowHandles().size() > handlesBefore.size();
				}
			});
		} catch (TimeoutException timeout) {
			return null;
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBefore.contains(handle)) {
				return handle;
			}
		}
		return null;
	}

	private void typeInFirstVisibleInput(final String labelOrPlaceholder, final String value) {
		final String inputXPath = "//input[contains(@placeholder, " + asXPathLiteral(labelOrPlaceholder)
				+ ") or contains(@aria-label, " + asXPathLiteral(labelOrPlaceholder)
				+ ") or @name=" + asXPathLiteral(labelOrPlaceholder) + "]";
		final List<WebElement> inputs = driver.findElements(By.xpath(inputXPath));
		for (final WebElement input : inputs) {
			if (input.isDisplayed()) {
				input.click();
				input.clear();
				input.sendKeys(value);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void clickByAnyVisibleText(final List<String> texts, final boolean required) {
		for (final String text : texts) {
			final List<WebElement> matches = driver.findElements(By.xpath(textXPath(text)));
			for (final WebElement match : matches) {
				if (!match.isDisplayed()) {
					continue;
				}
				clickElementOrClosestClickable(match);
				waitForUiToLoad();
				return;
			}
		}

		if (required) {
			throw new AssertionError("Unable to click a visible element with text: " + texts);
		}
	}

	private void clickElementOrClosestClickable(final WebElement element) {
		WebElement target = element;
		final String clickableAncestorXPath = "./ancestor-or-self::*[self::a or self::button or @role='button' or @tabindex='0'][1]";
		final List<WebElement> clickableAncestors = element.findElements(By.xpath(clickableAncestorXPath));
		if (!clickableAncestors.isEmpty()) {
			target = clickableAncestors.get(0);
		}

		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", target);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(target)).click();
		} catch (WebDriverException ex) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", target);
		}
	}

	private void waitForUiToLoad() {
		final WebDriverWait uiWait = new WebDriverWait(driver, UI_LOAD_TIMEOUT);
		uiWait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		waitForBusyIndicatorsToDisappear();
	}

	private void waitForBusyIndicatorsToDisappear() {
		final List<By> busySelectors = List.of(By.xpath("//*[@aria-busy='true']"),
				By.xpath("//*[contains(@class,'spinner') or contains(@class,'loading')]"));
		for (final By selector : busySelectors) {
			try {
				new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.invisibilityOfElementLocated(selector));
			} catch (TimeoutException ignored) {
				// UI may not expose loading indicators. Keep flow resilient.
			}
		}
	}

	private void takeViewportScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(name + ".png");
		Files.copy(screenshot.toPath(), target);
		evidence.put(name + " screenshot", target.toAbsolutePath().toString());
	}

	private void takeFullPageScreenshot(final String name) throws IOException {
		try {
			@SuppressWarnings("unchecked")
			final Map<String, Object> metrics = (Map<String, Object>) driver.executeCdpCommand("Page.getLayoutMetrics", Map.of());
			@SuppressWarnings("unchecked")
			final Map<String, Object> contentSize = (Map<String, Object>) metrics.get("contentSize");
			final Number width = (Number) contentSize.get("width");
			final Number height = (Number) contentSize.get("height");
			final Map<String, Object> clip = Map.of("x", 0, "y", 0, "width", width.doubleValue(), "height",
					height.doubleValue(), "scale", 1);
			final String base64Screenshot = (String) driver.executeCdpCommand("Page.captureScreenshot",
					Map.of("format", "png", "captureBeyondViewport", true, "fromSurface", true, "clip", clip)).get("data");

			final Path target = evidenceDir.resolve(name + "-full.png");
			Files.write(target, Base64.getDecoder().decode(base64Screenshot));
			evidence.put(name + " screenshot", target.toAbsolutePath().toString());
		} catch (Exception ex) {
			takeViewportScreenshot(name + "-fallback");
		}
	}

	private String textXPath(final String text) {
		final String literal = asXPathLiteral(text);
		return "//*[normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")]";
	}

	private String asXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	private String readSetting(final String systemPropertyName, final String envVarName, final String defaultValue) {
		final String fromProperty = System.getProperty(systemPropertyName);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}

		final String fromEnv = System.getenv(envVarName);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		return defaultValue;
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Workflow Report ===");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(" - " + entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		for (final Map.Entry<String, String> entry : evidence.entrySet()) {
			System.out.println(" - " + entry.getKey() + ": " + entry.getValue());
		}
	}
}
