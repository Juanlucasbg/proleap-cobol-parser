package io.proleap.saleads.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.Assert;
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
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final Duration POLL_INTERVAL = Duration.ofMillis(250);
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> checkpointScreenshots = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Duration timeout;
	private Path reportDirectory;
	private Path screenshotDirectory;

	@Test
	public void saleadsMiNegocioWorkflowTest() throws Exception {
		initializeConfiguration();
		startBrowser();

		try {
			openLoginPageWhenConfigured();

			runStep("Login", this::stepLoginWithGoogle);
			runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::stepOpenAdministrarNegociosView);
			runStep("Información General", this::stepValidateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
			runStep("Tus Negocios", this::stepValidateTusNegocios);
			runStep("Términos y Condiciones", () -> stepValidateLegalDocument("Términos y Condiciones",
					"terms_and_conditions.png", "terminosYCondicionesFinalUrl"));
			runStep("Política de Privacidad", () -> stepValidateLegalDocument("Política de Privacidad",
					"privacy_policy.png", "politicaDePrivacidadFinalUrl"));
		} finally {
			try {
				writeFinalReport();
			} finally {
				if (driver != null) {
					driver.quit();
				}
			}
		}

		final boolean allPassed = stepResults.values().stream().allMatch(StepResult::isPassed);
		Assert.assertTrue("One or more workflow validations failed. Check target/saleads-evidence/final-report.json",
				allPassed);
	}

	private void stepLoginWithGoogle() throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeLogin = driver.getWindowHandles();

		clickByVisibleText("login_google_button", "Sign in with Google", "Iniciar sesión con Google",
				"Iniciar sesion con Google", "Continuar con Google", "Acceder con Google", "Google");

		final Optional<String> popupHandle = waitForNewWindow(handlesBeforeLogin, Duration.ofSeconds(12));
		popupHandle.ifPresent(handle -> driver.switchTo().window(handle));

		selectGoogleAccountIfPresent();

		// Login can redirect either in a popup or in the current tab.
		if (popupHandle.isPresent()) {
			waitUntilCondition(Duration.ofSeconds(40), d -> d.getWindowHandles().contains(appHandle));
			driver.switchTo().window(appHandle);
		}

		waitForAnyVisibleText(Duration.ofSeconds(50), "Mi Negocio", "Negocio", "Dashboard", "Panel");
		assertSidebarVisible();
		captureViewportScreenshot("dashboard_loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("sidebar_negocio", "Negocio");
		clickByVisibleText("sidebar_mi_negocio", "Mi Negocio");
		waitForAnyVisibleText(Duration.ofSeconds(20), "Agregar Negocio", "Administrar Negocios");
		assertAnyVisibleText("Expected 'Agregar Negocio' option to be visible.", "Agregar Negocio");
		assertAnyVisibleText("Expected 'Administrar Negocios' option to be visible.", "Administrar Negocios");
		captureViewportScreenshot("mi_negocio_expanded_menu.png");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("mi_negocio_agregar_negocio", "Agregar Negocio");
		waitForAnyVisibleText(Duration.ofSeconds(20), "Crear Nuevo Negocio");

		assertAnyVisibleText("Missing modal title 'Crear Nuevo Negocio'.", "Crear Nuevo Negocio");
		assertElementVisible(
				By.xpath("//*[contains(normalize-space(.), 'Nombre del Negocio')] | //input[@placeholder='Nombre del Negocio']"),
				"Missing 'Nombre del Negocio' input field.");
		assertAnyVisibleText("Missing business quota text.", "Tienes 2 de 3 negocios");
		assertAnyVisibleText("Missing 'Cancelar' button.", "Cancelar");
		assertAnyVisibleText("Missing 'Crear Negocio' button.", "Crear Negocio");
		captureViewportScreenshot("agregar_negocio_modal.png");

		typeIntoFieldNearText("Nombre del Negocio", "Negocio Prueba Automatización");
		clickByVisibleText("agregar_negocio_modal_cancel", "Cancelar");
		waitUntilCondition(Duration.ofSeconds(10), d -> !isAnyTextVisible("Crear Nuevo Negocio"))
				.orElseThrow(() -> new AssertionError("Modal did not close after clicking 'Cancelar'."));
	}

	private void stepOpenAdministrarNegociosView() throws Exception {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickByVisibleText("sidebar_mi_negocio_reopen", "Mi Negocio");
		}

		clickByVisibleText("mi_negocio_administrar_negocios", "Administrar Negocios");
		waitForAnyVisibleText(Duration.ofSeconds(25), "Información General", "Informacion General");

		assertAnyVisibleText("Missing 'Información General' section.", "Información General", "Informacion General");
		assertAnyVisibleText("Missing 'Detalles de la Cuenta' section.", "Detalles de la Cuenta");
		assertAnyVisibleText("Missing 'Tus Negocios' section.", "Tus Negocios");
		assertAnyVisibleText("Missing 'Sección Legal' section.", "Sección Legal", "Seccion Legal");
		captureFullPageScreenshot("administrar_negocios_account_page.png");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisibleText("Missing plan badge text 'BUSINESS PLAN'.", "BUSINESS PLAN");
		assertAnyVisibleText("Missing button 'Cambiar Plan'.", "Cambiar Plan");
		assertUserEmailVisible();
		assertUserNameVisible();
	}

	private void stepValidateDetallesDeLaCuenta() {
		assertAnyVisibleText("Missing 'Cuenta creada' field.", "Cuenta creada");
		assertAnyVisibleText("Missing 'Estado activo' field.", "Estado activo");
		assertAnyVisibleText("Missing 'Idioma seleccionado' field.", "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertAnyVisibleText("Missing 'Tus Negocios' title.", "Tus Negocios");
		assertAnyVisibleText("Missing 'Agregar Negocio' button in business section.", "Agregar Negocio");
		assertAnyVisibleText("Missing business quota text in business section.", "Tienes 2 de 3 negocios");
		assertBusinessListVisible();
	}

	private void stepValidateLegalDocument(final String legalLinkText, final String screenshotName, final String urlKey)
			throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText("legal_link_click", legalLinkText, removeAccentsFromCommonTerms(legalLinkText));

		waitUntilCondition(Duration.ofSeconds(20), d -> d.getWindowHandles().size() > handlesBefore.size()
				|| !Objects.equals(d.getCurrentUrl(), originalUrl))
						.orElseThrow(() -> new AssertionError("Legal link did not trigger navigation or new tab."));

		final Optional<String> newHandle = findNewestWindowHandle(handlesBefore);
		final boolean openedNewTab = newHandle.isPresent();

		if (openedNewTab) {
			driver.switchTo().window(newHandle.get());
		}

		if ("Términos y Condiciones".equals(legalLinkText)) {
			waitForAnyVisibleText(Duration.ofSeconds(20), "Términos y Condiciones", "Terminos y Condiciones");
			assertAnyVisibleText("Missing legal heading for Términos y Condiciones.", "Términos y Condiciones",
					"Terminos y Condiciones");
		} else {
			waitForAnyVisibleText(Duration.ofSeconds(20), "Política de Privacidad", "Politica de Privacidad");
			assertAnyVisibleText("Missing legal heading for Política de Privacidad.", "Política de Privacidad",
					"Politica de Privacidad");
		}

		assertLegalContentVisible();
		captureViewportScreenshot(screenshotName);
		legalUrls.put(urlKey, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToSettle();
		} else {
			driver.navigate().back();
			waitForUiToSettle();
		}
	}

	private void initializeConfiguration() throws IOException {
		timeout = Duration.ofSeconds(Long.parseLong(readConfiguration("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", "35")));
		reportDirectory = Paths.get(readConfiguration("saleads.evidence.dir", "SALEADS_EVIDENCE_DIR", "target/saleads-evidence"));
		screenshotDirectory = reportDirectory.resolve("screenshots");
		Files.createDirectories(screenshotDirectory);
	}

	private void startBrowser() {
		final boolean headless = Boolean.parseBoolean(readConfiguration("saleads.headless", "SALEADS_HEADLESS", "true"));
		final String debuggerAddress = readConfiguration("saleads.chrome.debuggerAddress",
				"SALEADS_CHROME_DEBUGGER_ADDRESS", "");

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--window-size=1920,2200");
		options.addArguments("--lang=es");
		if (headless) {
			options.addArguments("--headless=new");
		}

		if (!debuggerAddress.isBlank()) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		} else {
			WebDriverManager.chromedriver().setup();
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, timeout);
		wait.pollingEvery(POLL_INTERVAL);
	}

	private void openLoginPageWhenConfigured() {
		final String loginUrl = readConfiguration("saleads.login.url", "SALEADS_LOGIN_URL", "");
		if (!loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiToSettle();
			return;
		}

		final String currentUrl = driver.getCurrentUrl();
		if (currentUrl == null || currentUrl.isBlank() || "data:,".equals(currentUrl) || "about:blank".equals(currentUrl)) {
			throw new IllegalStateException(
					"No login URL provided and browser did not start on a SaleADS login page. Set SALEADS_LOGIN_URL or attach to an existing browser with SALEADS_CHROME_DEBUGGER_ADDRESS.");
		}
	}

	private void runStep(final String stepName, final StepExecutable executable) {
		try {
			executable.run();
			stepResults.put(stepName, StepResult.pass("All validations passed."));
		} catch (final Exception | AssertionError error) {
			final String screenshot = "failure_" + sanitizeFileName(stepName) + ".png";
			try {
				captureViewportScreenshot(screenshot);
			} catch (final Exception ignored) {
				// If screenshot capture also fails, keep the original error.
			}

			final String details = error.getClass().getSimpleName() + ": " + error.getMessage();
			stepResults.put(stepName, StepResult.fail(details));
		}
	}

	private void clickByVisibleText(final String clickName, final String... texts) throws Exception {
		final WebElement target = waitUntilCondition(Duration.ofSeconds(20), d -> findVisibleClickableElement(texts))
				.orElseThrow(() -> new AssertionError("Unable to find clickable element by text for " + clickName));
		safeClick(target);
		waitForUiToSettle();
	}

	private WebElement findVisibleClickableElement(final String... texts) {
		for (final String text : texts) {
			if (text == null || text.isBlank()) {
				continue;
			}

			final String textLiteral = xpathLiteral(text.trim());
			final By locator = By.xpath(
					"//*[contains(normalize-space(.), " + textLiteral + ")]/ancestor-or-self::*[self::button or self::a or @role='button' or self::li][1]");
			final List<WebElement> candidates = driver.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (isDisplayed(candidate)) {
					return candidate;
				}
			}
		}

		return null;
	}

	private void safeClick(final WebElement element) throws Exception {
		scrollIntoView(element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickError) {
			try {
				new Actions(driver).moveToElement(element).click().perform();
			} catch (final Exception actionError) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			}
		}
	}

	private void selectGoogleAccountIfPresent() throws Exception {
		final Optional<WebElement> accountOption = waitUntilCondition(Duration.ofSeconds(15),
				d -> findVisibleElementByText(GOOGLE_ACCOUNT).orElse(null));

		if (accountOption.isPresent()) {
			safeClick(accountOption.get());
			waitForUiToSettle();
		}
	}

	private void assertSidebarVisible() {
		assertElementVisible(By.xpath("//aside | //*[@role='navigation']"),
				"Sidebar was not found after login.");
		assertAnyVisibleText("Expected left navigation content after login.", "Negocio", "Mi Negocio");
	}

	private void assertElementVisible(final By by, final String message) {
		final List<WebElement> elements = driver.findElements(by);
		final boolean found = elements.stream().anyMatch(this::isDisplayed);
		Assert.assertTrue(message, found);
	}

	private void assertAnyVisibleText(final String message, final String... texts) {
		for (final String text : texts) {
			if (isAnyTextVisible(text)) {
				return;
			}
		}

		Assert.fail(message);
	}

	private boolean isAnyTextVisible(final String text) {
		if (text == null || text.isBlank()) {
			return false;
		}

		final Optional<WebElement> visible = findVisibleElementByText(text);
		return visible.isPresent();
	}

	private Optional<WebElement> findVisibleElementByText(final String text) {
		final String textLiteral = xpathLiteral(text.trim());
		final List<WebElement> candidates = driver.findElements(By.xpath("//*[contains(normalize-space(.), " + textLiteral + ")]"));
		return candidates.stream().filter(this::isDisplayed).findFirst();
	}

	private void waitForAnyVisibleText(final Duration customTimeout, final String... texts) throws Exception {
		waitUntilCondition(customTimeout, d -> {
			for (final String text : texts) {
				if (isAnyTextVisible(text)) {
					return true;
				}
			}
			return false;
		}).orElseThrow(() -> new AssertionError("Expected visible text was not found: " + String.join(", ", texts)));
	}

	private void typeIntoFieldNearText(final String labelText, final String value) throws Exception {
		final String labelLiteral = xpathLiteral(labelText);
		final By preferredInput = By.xpath(
				"(//*[contains(normalize-space(.), " + labelLiteral + ")]/following::input[1] | //input[@placeholder='"
						+ labelText + "'])[1]");
		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(preferredInput));
		scrollIntoView(input);
		input.clear();
		input.sendKeys(value);
		waitForUiToSettle();
	}

	private void assertUserEmailVisible() {
		final List<WebElement> visibleElements = driver.findElements(By.xpath("//*[string-length(normalize-space(.)) > 0]"));
		final boolean emailVisible = visibleElements.stream().filter(this::isDisplayed).map(WebElement::getText)
				.map(String::trim).anyMatch(text -> EMAIL_PATTERN.matcher(text).matches());
		Assert.assertTrue("Expected user email to be visible in 'Información General'.", emailVisible);
	}

	private void assertUserNameVisible() {
		final List<WebElement> visibleElements = driver.findElements(By.xpath("//*[string-length(normalize-space(.)) > 0]"));
		final boolean nameVisible = visibleElements.stream().filter(this::isDisplayed).map(WebElement::getText)
				.map(String::trim).anyMatch(text -> text.length() >= 3 && !text.contains("@")
						&& !"BUSINESS PLAN".equalsIgnoreCase(text) && !"Cambiar Plan".equalsIgnoreCase(text)
						&& !"Información General".equalsIgnoreCase(text) && !"Informacion General".equalsIgnoreCase(text));
		Assert.assertTrue("Expected user name to be visible in 'Información General'.", nameVisible);
	}

	private void assertBusinessListVisible() {
		final By businessListLocator = By.xpath(
				"//*[contains(normalize-space(.), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]//*[self::li or self::tr or @role='row' or @role='listitem']");
		final List<WebElement> listItems = driver.findElements(businessListLocator);
		final boolean listVisible = listItems.stream().anyMatch(this::isDisplayed);
		Assert.assertTrue("Expected business list entries to be visible in 'Tus Negocios'.", listVisible);
	}

	private void assertLegalContentVisible() {
		final List<WebElement> paragraphCandidates = driver
				.findElements(By.xpath("//p[string-length(normalize-space(.)) > 80] | //article//*[string-length(normalize-space(.)) > 80]"));
		final boolean legalTextVisible = paragraphCandidates.stream().anyMatch(this::isDisplayed);
		Assert.assertTrue("Expected legal content text to be visible.", legalTextVisible);
	}

	private Optional<String> waitForNewWindow(final Set<String> existingHandles, final Duration customTimeout)
			throws Exception {
		final WebDriverWait localWait = new WebDriverWait(driver, customTimeout);
		localWait.pollingEvery(POLL_INTERVAL);
		try {
			final String handle = localWait.until(d -> {
				final Set<String> currentHandles = d.getWindowHandles();
				return currentHandles.stream().filter(candidate -> !existingHandles.contains(candidate)).findFirst()
						.orElse(null);
			});
			return Optional.ofNullable(handle);
		} catch (final TimeoutException timeoutException) {
			return Optional.empty();
		}
	}

	private Optional<String> findNewestWindowHandle(final Set<String> previousHandles) {
		final Set<String> currentHandles = driver.getWindowHandles();
		return currentHandles.stream().filter(handle -> !previousHandles.contains(handle)).findFirst();
	}

	private void waitForUiToSettle() {
		wait.until(d -> {
			final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(readyState) || "interactive".equals(readyState);
		});
	}

	private void captureViewportScreenshot(final String fileName) throws IOException {
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final Path target = screenshotDirectory.resolve(fileName);
		Files.write(target, screenshot);
		checkpointScreenshots.add(target.toString());
	}

	private void captureFullPageScreenshot(final String fileName) throws IOException {
		if (driver instanceof ChromiumDriver) {
			try {
				final Map<String, Object> parameters = new HashMap<>();
				parameters.put("format", "png");
				parameters.put("fromSurface", true);
				parameters.put("captureBeyondViewport", true);
				final Object data = ((ChromiumDriver) driver).executeCdpCommand("Page.captureScreenshot", parameters)
						.get("data");
				if (data instanceof String) {
					final Path target = screenshotDirectory.resolve(fileName);
					Files.write(target, Base64.getDecoder().decode((String) data));
					checkpointScreenshots.add(target.toString());
					return;
				}
			} catch (final Exception ignored) {
				// Fallback to regular viewport screenshot below.
			}
		}

		captureViewportScreenshot(fileName);
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"testName\": \"").append(TEST_NAME).append("\",\n");
		json.append("  \"generatedAtUtc\": \"").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("\",\n");
		json.append("  \"screenshotsDirectory\": \"").append(escapeJson(screenshotDirectory.toString())).append("\",\n");
		json.append("  \"steps\": [\n");

		int index = 0;
		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			if (index > 0) {
				json.append(",\n");
			}
			json.append("    {\n");
			json.append("      \"name\": \"").append(escapeJson(entry.getKey())).append("\",\n");
			json.append("      \"status\": \"").append(entry.getValue().isPassed() ? "PASS" : "FAIL").append("\",\n");
			json.append("      \"details\": \"").append(escapeJson(entry.getValue().getDetails())).append("\"\n");
			json.append("    }");
			index++;
		}

		json.append("\n  ],\n");
		json.append("  \"legalUrls\": {\n");
		json.append("    \"terminosYCondiciones\": \"")
				.append(escapeJson(legalUrls.getOrDefault("terminosYCondicionesFinalUrl", ""))).append("\",\n");
		json.append("    \"politicaDePrivacidad\": \"")
				.append(escapeJson(legalUrls.getOrDefault("politicaDePrivacidadFinalUrl", ""))).append("\"\n");
		json.append("  },\n");
		json.append("  \"checkpointScreenshots\": [\n");
		for (int i = 0; i < checkpointScreenshots.size(); i++) {
			if (i > 0) {
				json.append(",\n");
			}
			json.append("    \"").append(escapeJson(checkpointScreenshots.get(i))).append("\"");
		}
		json.append("\n  ]\n");
		json.append("}\n");

		Files.createDirectories(reportDirectory);
		Files.write(reportDirectory.resolve("final-report.json"), json.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String readConfiguration(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				result.append(", ");
			}
			if (chars[i] == '\'') {
				result.append("\"'\"");
			} else {
				result.append("'").append(chars[i]).append("'");
			}
		}
		result.append(")");
		return result.toString();
	}

	private String removeAccentsFromCommonTerms(final String text) {
		return text.replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
				.replace("Á", "A").replace("É", "E").replace("Í", "I").replace("Ó", "O").replace("Ú", "U");
	}

	private String sanitizeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "_");
	}

	private String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	private boolean isDisplayed(final WebElement element) {
		try {
			return element != null && element.isDisplayed();
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'center'});",
				element);
	}

	private <T> Optional<T> waitUntilCondition(final Duration customTimeout, final java.util.function.Function<WebDriver, T> condition)
			throws Exception {
		final WebDriverWait localWait = new WebDriverWait(driver, customTimeout);
		localWait.pollingEvery(POLL_INTERVAL);
		try {
			final T value = localWait.until(condition);
			return Optional.ofNullable(value);
		} catch (final TimeoutException timeoutException) {
			return Optional.empty();
		}
	}

	@FunctionalInterface
	private interface StepExecutable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		public static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		public static StepResult fail(final String details) {
			return new StepResult(false, details);
		}

		public boolean isPassed() {
			return passed;
		}

		public String getDetails() {
			return details;
		}
	}
}
