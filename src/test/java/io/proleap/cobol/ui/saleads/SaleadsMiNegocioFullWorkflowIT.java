package io.proleap.cobol.ui.saleads;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowIT {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Set<String> STEP_ORDER = new LinkedHashSet<>(Arrays.asList("Login", "Mi Negocio menu",
			"Agregar Negocio modal", "Administrar Negocios view", "Información General", "Detalles de la Cuenta",
			"Tus Negocios", "Términos y Condiciones", "Política de Privacidad"));

	private final Map<String, String> checkpoints = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsRoot;
	private Path screenshotsDir;

	@Test
	public void runSaleadsMiNegocioWorkflow() throws Exception {
		final String runId = TIMESTAMP_FORMAT.format(LocalDateTime.now());
		artifactsRoot = Paths.get("target", "saleads", "mi-negocio-full-test", runId);
		screenshotsDir = artifactsRoot.resolve("screenshots");
		Files.createDirectories(screenshotsDir);

		for (final String step : STEP_ORDER) {
			stepStatus.put(step, "FAIL");
			stepDetails.put(step, "Step did not run.");
		}

		try {
			setupDriver();
			runStep("Login", this::runLoginStep);
			runStep("Mi Negocio menu", this::runMiNegocioMenuStep);
			runStep("Agregar Negocio modal", this::runAgregarNegocioModalStep);
			runStep("Administrar Negocios view", this::runAdministrarNegociosViewStep);
			runStep("Información General", this::runInformacionGeneralStep);
			runStep("Detalles de la Cuenta", this::runDetallesCuentaStep);
			runStep("Tus Negocios", this::runTusNegociosStep);
			runStep("Términos y Condiciones", () -> runLegalLinkStep("Términos y Condiciones"));
			runStep("Política de Privacidad", () -> runLegalLinkStep("Política de Privacidad"));
		} finally {
			writeFinalReport();
			teardownDriver();
		}

		if (!failures.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow failures:\n - " + String.join("\n - ", failures));
		}
	}

	private void runLoginStep() throws Exception {
		final String loginUrl = resolveValue("saleads.login.url", "SALEADS_LOGIN_URL");
		if (isBlank(loginUrl)) {
			throw new IllegalStateException(
					"Missing login URL. Set -Dsaleads.login.url or SALEADS_LOGIN_URL for the environment under test.");
		}

		driver.get(loginUrl);
		waitForUiLoad();

		clickFirstVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Login with Google", "Google"));

		selectGoogleAccountIfPresent(GOOGLE_ACCOUNT_EMAIL);
		assertAnyVisibleText(Arrays.asList("Mi Negocio", "Negocio", "Dashboard", "Inicio"),
				"Main application interface did not appear after login.");
		assertSidebarVisible();
		checkpoints.put("dashboard_loaded", captureScreenshot("01-dashboard-loaded"));
	}

	private void runMiNegocioMenuStep() throws Exception {
		clickFirstVisibleText(Arrays.asList("Negocio"));
		clickFirstVisibleText(Arrays.asList("Mi Negocio"));

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		checkpoints.put("mi_negocio_menu_expanded", captureScreenshot("02-mi-negocio-menu-expanded"));
	}

	private void runAgregarNegocioModalStep() throws Exception {
		clickFirstVisibleText(Arrays.asList("Agregar Negocio"));
		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		checkpoints.put("agregar_negocio_modal", captureScreenshot("03-agregar-negocio-modal"));

		typeIntoFirstVisibleInput("Negocio Prueba Automatización");
		clickFirstVisibleText(Arrays.asList("Cancelar"));
		waitForUiLoad();
	}

	private void runAdministrarNegociosViewStep() throws Exception {
		if (!isAnyTextVisible(Arrays.asList("Administrar Negocios"))) {
			clickFirstVisibleText(Arrays.asList("Mi Negocio"));
		}

		clickFirstVisibleText(Arrays.asList("Administrar Negocios"));
		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertAnyVisibleText(Arrays.asList("Sección Legal", "Legal"), "Legal section is not visible.");

		checkpoints.put("administrar_negocios_view", captureScreenshot("04-administrar-negocios-view"));
	}

	private void runInformacionGeneralStep() {
		assertVisibleText("Información General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
		assertPageContainsEmail();
		assertUserNameLikelyVisible();
	}

	private void runDetallesCuentaStep() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void runTusNegociosStep() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private void runLegalLinkStep(final String linkText) throws Exception {
		final String previousHandle = driver.getWindowHandle();
		final int handlesBefore = driver.getWindowHandles().size();

		clickFirstVisibleText(Arrays.asList(linkText));
		waitForUiLoad();

		String activeHandle = previousHandle;
		if (driver.getWindowHandles().size() > handlesBefore) {
			activeHandle = switchToNewestHandle();
			waitForUiLoad();
		}

		assertVisibleText(linkText);
		assertLegalContentVisible();

		final String slug = linkText.toLowerCase().replace(" ", "-");
		checkpoints.put(slug, captureScreenshot("05-legal-" + slug));
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (!previousHandle.equals(activeHandle)) {
			driver.close();
			driver.switchTo().window(previousHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void setupDriver() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		final boolean headless = Boolean.parseBoolean(resolveValue("saleads.headless", "SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
	}

	private void teardownDriver() {
		if (driver != null) {
			driver.quit();
		}
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepStatus.put(stepName, "PASS");
			stepDetails.put(stepName, "Completed successfully.");
		} catch (final Exception | AssertionError error) {
			stepStatus.put(stepName, "FAIL");
			final String details = safeMessage(error);
			stepDetails.put(stepName, details);
			failures.add(stepName + ": " + details);

			try {
				checkpoints.put("failure_" + stepName.toLowerCase().replace(" ", "_"),
						captureScreenshot("failure-" + stepName.toLowerCase().replace(" ", "-")));
			} catch (final Exception ignored) {
				// ignore screenshot errors on failure handling
			}
		}
	}

	private void selectGoogleAccountIfPresent(final String email) {
		final String originalHandle = driver.getWindowHandle();
		final int handlesBefore = driver.getWindowHandles().size();

		try {
			new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(d -> d.getWindowHandles().size() > handlesBefore || isTextPresent(email));
		} catch (final TimeoutException ignored) {
			return;
		}

		if (driver.getWindowHandles().size() > handlesBefore) {
			final String popupHandle = switchToNewestHandle();
			clickAccountIfVisible(email);

			try {
				new WebDriverWait(driver, Duration.ofSeconds(15))
						.until(d -> d.getWindowHandles().size() == handlesBefore || !d.getCurrentUrl().contains("accounts.google"));
			} catch (final TimeoutException ignored) {
				// account chooser can keep the tab open in some flows
			}

			if (!originalHandle.equals(popupHandle) && driver.getWindowHandles().contains(originalHandle)) {
				driver.switchTo().window(originalHandle);
			}
		} else {
			clickAccountIfVisible(email);
			if (driver.getCurrentUrl().contains("accounts.google")) {
				try {
					new WebDriverWait(driver, Duration.ofSeconds(20))
							.until(ExpectedConditions.not(ExpectedConditions.urlContains("accounts.google")));
				} catch (final TimeoutException ignored) {
					// login may continue manually for MFA/challenge
				}
			}
		}

		waitForUiLoad();
	}

	private void clickAccountIfVisible(final String email) {
		final List<WebElement> matches = findVisibleElementsByText(email);
		if (!matches.isEmpty()) {
			matches.get(0).click();
			waitForUiLoad();
		}
	}

	private String switchToNewestHandle() {
		String newest = driver.getWindowHandle();
		for (final String handle : driver.getWindowHandles()) {
			newest = handle;
		}
		driver.switchTo().window(newest);
		return newest;
	}

	private void clickFirstVisibleText(final List<String> texts) {
		Exception lastError = null;
		for (final String text : texts) {
			try {
				final WebElement clickable = wait.until(ExpectedConditions.elementToBeClickable(byVisibleText(text)));
				clickable.click();
				waitForUiLoad();
				return;
			} catch (final Exception error) {
				lastError = error;
			}
		}

		throw new NoSuchElementException("Could not click any element with text: " + texts + ". Last error: "
				+ (lastError == null ? "n/a" : safeMessage(lastError)));
	}

	private void typeIntoFirstVisibleInput(final String value) {
		final List<By> candidates = Arrays.asList(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or contains(@aria-label, 'Nombre del Negocio') or contains(@name, 'nombre')]"),
				By.xpath("//div[@role='dialog']//input"), By.xpath("//input"));
		for (final By candidate : candidates) {
			final List<WebElement> elements = driver.findElements(candidate);
			for (final WebElement element : elements) {
				if (element.isDisplayed() && element.isEnabled()) {
					element.clear();
					element.sendKeys(value);
					waitForUiLoad();
					return;
				}
			}
		}
	}

	private void assertVisibleText(final String text) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
		} catch (final TimeoutException error) {
			throw new AssertionError("Expected visible text not found: " + text);
		}
	}

	private void assertAnyVisibleText(final List<String> texts, final String errorMessage) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return;
			}
		}
		throw new AssertionError(errorMessage + " Missing any of: " + texts);
	}

	private void assertSidebarVisible() {
		final List<By> candidates = Arrays.asList(
				By.xpath("//aside//*[contains(normalize-space(.), 'Negocio') or contains(normalize-space(.), 'Mi Negocio')]"),
				By.xpath("//nav//*[contains(normalize-space(.), 'Negocio') or contains(normalize-space(.), 'Mi Negocio')]"),
				By.xpath("//*[contains(@class, 'sidebar') and (contains(., 'Negocio') or contains(., 'Mi Negocio'))]"));

		for (final By candidate : candidates) {
			final List<WebElement> elements = driver.findElements(candidate);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}

		throw new AssertionError("Left sidebar navigation is not visible.");
	}

	private void assertPageContainsEmail() {
		final String pageText = driver.findElement(By.tagName("body")).getText();
		if (!EMAIL_PATTERN.matcher(pageText).find()) {
			throw new AssertionError("User email is not visible on the account page.");
		}
	}

	private void assertUserNameLikelyVisible() {
		final String pageText = driver.findElement(By.tagName("body")).getText();
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(pageText);
		final String email = emailMatcher.find() ? emailMatcher.group() : "";

		final Pattern fullNamePattern = Pattern.compile("\\b[\\p{L}]{2,}(?:\\s+[\\p{L}]{2,}){1,}\\b");
		final Matcher nameMatcher = fullNamePattern.matcher(pageText);
		while (nameMatcher.find()) {
			final String candidate = nameMatcher.group();
			if (!candidate.equalsIgnoreCase("Información General") && !candidate.equalsIgnoreCase("Detalles de la Cuenta")
					&& !candidate.equalsIgnoreCase("Tus Negocios") && !candidate.equalsIgnoreCase("Sección Legal")
					&& !candidate.contains("Términos") && !candidate.contains("Política")
					&& !candidate.equalsIgnoreCase("BUSINESS PLAN") && !candidate.contains(email)) {
				return;
			}
		}
		throw new AssertionError("Could not confirm user name visibility.");
	}

	private void assertLegalContentVisible() {
		final String body = driver.findElement(By.tagName("body")).getText();
		if (isBlank(body) || body.trim().length() < 120) {
			throw new AssertionError("Legal content text is not visible.");
		}
	}

	private boolean isAnyTextVisible(final List<String> texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return true;
			}
		}
		return false;
	}

	private boolean isTextVisible(final String text) {
		try {
			return wait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text))).isDisplayed();
		} catch (final Exception ignored) {
			return false;
		}
	}

	private boolean isTextPresent(final String text) {
		return !driver.findElements(byVisibleText(text)).isEmpty();
	}

	private By byVisibleText(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath("//*[normalize-space(.)=" + literal + " or contains(normalize-space(.), " + literal + ")]");
	}

	private List<WebElement> findVisibleElementsByText(final String text) {
		final List<WebElement> results = new ArrayList<>();
		for (final WebElement element : driver.findElements(byVisibleText(text))) {
			if (element.isDisplayed()) {
				results.add(element);
			}
		}
		return results;
	}

	private String captureScreenshot(final String checkpoint) throws IOException {
		final Path outputPath = screenshotsDir.resolve(checkpoint + ".png");
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, outputPath, StandardCopyOption.REPLACE_EXISTING);
		return outputPath.toString();
	}

	private void waitForUiLoad() {
		try {
			wait.until(driverRef -> "complete"
					.equals(((JavascriptExecutor) driverRef).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// fallback to small pause when document state is not available
		}

		try {
			Thread.sleep(900L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void writeFinalReport() throws IOException {
		Files.createDirectories(artifactsRoot);
		final Path reportPath = artifactsRoot.resolve("saleads_mi_negocio_full_test_report.json");
		final String json = buildReportJson();
		Files.write(reportPath, json.getBytes(StandardCharsets.UTF_8));
	}

	private String buildReportJson() {
		final StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"test_name\": \"saleads_mi_negocio_full_test\",\n");
		builder.append("  \"generated_at\": \"").append(LocalDateTime.now()).append("\",\n");
		builder.append("  \"results\": {\n");

		int index = 0;
		for (final String step : STEP_ORDER) {
			builder.append("    \"").append(escapeJson(step)).append("\": {\n");
			builder.append("      \"status\": \"").append(escapeJson(stepStatus.get(step))).append("\",\n");
			builder.append("      \"details\": \"").append(escapeJson(stepDetails.get(step))).append("\"\n");
			builder.append("    }");
			index++;
			builder.append(index < STEP_ORDER.size() ? ",\n" : "\n");
		}

		builder.append("  },\n");
		builder.append("  \"checkpoints\": ").append(stringMapToJson(checkpoints)).append(",\n");
		builder.append("  \"final_urls\": ").append(stringMapToJson(legalUrls)).append("\n");
		builder.append("}\n");
		return builder.toString();
	}

	private String stringMapToJson(final Map<String, String> values) {
		final StringBuilder builder = new StringBuilder();
		builder.append("{");
		int index = 0;
		for (final Map.Entry<String, String> entry : values.entrySet()) {
			builder.append("\n    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(escapeJson(entry.getValue())).append("\"");
			index++;
			builder.append(index < values.size() ? "," : "");
		}
		if (!values.isEmpty()) {
			builder.append("\n  ");
		}
		builder.append("}");
		return builder.toString();
	}

	private String resolveValue(final String systemProperty, final String envVariable) {
		return resolveValue(systemProperty, envVariable, "");
	}

	private String resolveValue(final String systemProperty, final String envVariable, final String defaultValue) {
		final String fromSystemProperty = System.getProperty(systemProperty);
		if (!isBlank(fromSystemProperty)) {
			return fromSystemProperty;
		}

		final String fromEnv = System.getenv(envVariable);
		if (!isBlank(fromEnv)) {
			return fromEnv;
		}

		return defaultValue;
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private String safeMessage(final Throwable throwable) {
		final String message = throwable.getMessage();
		return isBlank(message) ? throwable.getClass().getSimpleName() : message;
	}

	private String escapeJson(final String value) {
		if (value == null) {
			return "";
		}

		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	private String toXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final char[] characters = text.toCharArray();
		for (int i = 0; i < characters.length; i++) {
			final String character = String.valueOf(characters[i]);
			if ("'".equals(character)) {
				builder.append("\"'\"");
			} else if ("\"".equals(character)) {
				builder.append("'\"'");
			} else {
				builder.append("'").append(character).append("'");
			}

			if (i + 1 < characters.length) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
