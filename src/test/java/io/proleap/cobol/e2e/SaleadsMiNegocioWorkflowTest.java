package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.Assume;
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
import org.openqa.selenium.remote.RemoteWebDriver;

public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");

	private static final String LOGIN_STEP = "Login";
	private static final String MENU_STEP = "Mi Negocio menu";
	private static final String MODAL_STEP = "Agregar Negocio modal";
	private static final String ADMIN_STEP = "Administrar Negocios view";
	private static final String INFO_STEP = "Informaci\u00f3n General";
	private static final String DETAILS_STEP = "Detalles de la Cuenta";
	private static final String BUSINESSES_STEP = "Tus Negocios";
	private static final String TERMS_STEP = "T\u00e9rminos y Condiciones";
	private static final String PRIVACY_STEP = "Pol\u00edtica de Privacidad";

	private static final List<String> REPORT_FIELDS = Arrays.asList(LOGIN_STEP, MENU_STEP, MODAL_STEP, ADMIN_STEP, INFO_STEP,
			DETAILS_STEP, BUSINESSES_STEP, TERMS_STEP, PRIVACY_STEP);

	private final Map<String, String> report = new LinkedHashMap<>();
	private final Map<String, String> errors = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private Path artifactDir;
	private Path screenshotDir;
	private String appWindowHandle;

	@Test
	public void testSaleadsMiNegocioWorkflow() throws Exception {
		Assume.assumeTrue("Enable with SALEADS_E2E_ENABLED=true or -Dsaleads.e2e.enabled=true.",
				getBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false));
		initializeReport();
		artifactDir = createArtifactDir();
		screenshotDir = Files.createDirectories(artifactDir.resolve("screenshots"));

		try {
			driver = createDriver();

			final String loginUrl = getRequiredConfig("saleads.loginUrl", "SALEADS_LOGIN_URL");
			driver.get(loginUrl);
			waitForUiToLoad();
			appWindowHandle = driver.getWindowHandle();

			runStep(LOGIN_STEP, this::executeLoginStep);
			runStep(MENU_STEP, this::executeMiNegocioMenuStep);
			runStep(MODAL_STEP, this::executeAgregarNegocioModalStep);
			runStep(ADMIN_STEP, this::executeAdministrarNegociosStep);
			runStep(INFO_STEP, this::executeInformacionGeneralStep);
			runStep(DETAILS_STEP, this::executeDetallesCuentaStep);
			runStep(BUSINESSES_STEP, this::executeTusNegociosStep);
			runStep(TERMS_STEP, () -> executeLegalStep("T\u00e9rminos y Condiciones", "T\u00e9rminos y Condiciones",
					"terms-and-conditions-url", "05-terminos-y-condiciones.png"));
			runStep(PRIVACY_STEP, () -> executeLegalStep("Pol\u00edtica de Privacidad", "Pol\u00edtica de Privacidad",
					"privacy-policy-url", "06-politica-de-privacidad.png"));
		} finally {
			writeFinalReport();

			if (driver != null) {
				driver.quit();
			}
		}

		final List<String> failed = report.entrySet().stream().filter(entry -> "FAIL".equals(entry.getValue()))
				.map(Map.Entry::getKey).collect(Collectors.toList());
		if (!failed.isEmpty()) {
			fail("Failed validations: " + failed + ". See " + artifactDir.resolve("final-report.md"));
		}
	}

	private void executeLoginStep() throws Exception {
		clickFirstVisibleText("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Ingresar con Google",
				"Continuar con Google");
		waitForUiToLoad();
		selectGoogleAccountIfVisible(getConfig("saleads.googleEmail", "SALEADS_GOOGLE_EMAIL",
				"juanlucasbarbiergarzon@gmail.com"));
		waitForMainInterface();
		captureScreenshot("01-dashboard-loaded.png");
	}

	private void executeMiNegocioMenuStep() throws Exception {
		ensureApplicationWindow();
		if (!isVisibleText("Mi Negocio")) {
			clickFirstVisibleText("Negocio");
			waitForUiToLoad();
		}
		clickFirstVisibleText("Mi Negocio");
		waitForUiToLoad();
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void executeAgregarNegocioModalStep() throws Exception {
		ensureApplicationWindow();
		if (!isVisibleText("Agregar Negocio")) {
			clickFirstVisibleText("Mi Negocio");
			waitForUiToLoad();
		}
		clickFirstVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		findVisibleElement(By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"
				+ " | //input[@name='nombreNegocio']"
				+ " | //label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				"Nombre del Negocio input");
		captureScreenshot("03-agregar-negocio-modal.png");

		if (clickVisibleTextIfPresent("Cancelar")) {
			waitForUiToLoad();
		}
	}

	private void executeAdministrarNegociosStep() throws Exception {
		ensureApplicationWindow();
		if (!isVisibleText("Administrar Negocios")) {
			clickFirstVisibleText("Mi Negocio");
			waitForUiToLoad();
		}
		clickFirstVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertVisibleText("Informaci\u00f3n General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Secci\u00f3n Legal");
		captureScreenshot("04-administrar-negocios-view.png");
	}

	private void executeInformacionGeneralStep() {
		assertVisibleText("Informaci\u00f3n General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String expectedEmail = getConfig("saleads.googleEmail", "SALEADS_GOOGLE_EMAIL",
				"juanlucasbarbiergarzon@gmail.com");
		assertVisibleText(expectedEmail);

		final String expectedName = getConfig("saleads.expectedUserName", "SALEADS_EXPECTED_USER_NAME", "");
		if (!expectedName.isBlank()) {
			assertVisibleText(expectedName);
		} else {
			assertTrue("Could not infer a visible user name from Informacion General section.",
					looksLikeAUserName(getNearbySectionText("Informaci\u00f3n General")));
		}
	}

	private void executeDetallesCuentaStep() {
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void executeTusNegociosStep() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		final WebElement sectionHeading = findVisibleElement(byVisibleText("Tus Negocios"), "Tus Negocios heading");
		final WebElement section = firstAncestorContainer(sectionHeading);
		final List<WebElement> listCandidates = section.findElements(
				By.xpath(".//li | .//tr | .//*[@role='row'] | .//*[contains(@class,'business')] | .//*[contains(@class,'negocio')]"));
		assertTrue("Expected a visible business list in Tus Negocios section.",
				!listCandidates.isEmpty() || section.getText().split("\\R").length >= 5);
	}

	private void executeLegalStep(final String linkText, final String expectedHeading, final String urlKey,
			final String screenshotName) throws Exception {
		ensureApplicationWindow();
		final String startHandle = driver.getWindowHandle();
		final String startUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickFirstVisibleText(linkText);
		waitForUiToLoad();

		final String legalHandle = waitForLegalDocument(startHandle, handlesBefore, startUrl, expectedHeading);
		if (legalHandle != null) {
			driver.switchTo().window(legalHandle);
		}

		assertVisibleText(expectedHeading);
		assertLegalContentVisible();
		captureScreenshot(screenshotName);
		capturedUrls.put(urlKey, driver.getCurrentUrl());

		// If legal content opened in a new tab, close it and return to app.
		if (legalHandle != null && !legalHandle.equals(startHandle)) {
			driver.close();
			driver.switchTo().window(startHandle);
		} else if (!startUrl.equals(driver.getCurrentUrl())) {
			driver.navigate().back();
		}

		waitForUiToLoad();
		ensureApplicationWindow();
	}

	private String waitForLegalDocument(final String startHandle, final Set<String> handlesBefore, final String startUrl,
			final String expectedHeading) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(25).toMillis();

		while (System.currentTimeMillis() < deadline) {
			final Set<String> currentHandles = driver.getWindowHandles();
			for (final String handle : currentHandles) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					if (isVisibleText(expectedHeading) || !startUrl.equals(driver.getCurrentUrl())) {
						return handle;
					}
				}
			}

			driver.switchTo().window(startHandle);
			if (isVisibleText(expectedHeading) || !startUrl.equals(driver.getCurrentUrl())) {
				return startHandle;
			}
			pause(350);
		}

		throw new TimeoutException("Legal page did not load for link: " + expectedHeading);
	}

	private void assertLegalContentVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected legal content text to be visible.", bodyText != null && bodyText.trim().length() > 150);
	}

	private void runStep(final String stepName, final ThrowingRunnable stepAction) {
		try {
			stepAction.run();
			report.put(stepName, "PASS");
		} catch (final Exception exception) {
			report.put(stepName, "FAIL");
			errors.put(stepName, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
		}
	}

	private WebDriver createDriver() {
		final String remoteWebDriverUrl = getConfig("saleads.webdriverUrl", "SALEADS_WEBDRIVER_URL", "");
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1440,1800");
		if (getBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}

		if (!remoteWebDriverUrl.isBlank()) {
			try {
				return new RemoteWebDriver(java.net.URI.create(remoteWebDriverUrl).toURL(), options);
			} catch (final java.net.MalformedURLException malformedURLException) {
				throw new IllegalArgumentException("Invalid SALEADS_WEBDRIVER_URL value: " + remoteWebDriverUrl,
						malformedURLException);
			}
		}
		return new ChromeDriver(options);
	}

	private void waitForMainInterface() {
		waitForCondition(
				() -> hasVisibleSidebar() && (isVisibleText("Mi Negocio") || isVisibleText("Negocio")),
				Duration.ofSeconds(40),
				"Main application interface and left sidebar were not visible after login.");
		appWindowHandle = driver.getWindowHandle();
	}

	private boolean hasVisibleSidebar() {
		final List<WebElement> candidates = driver.findElements(By.xpath("//aside | //nav"));
		for (final WebElement candidate : candidates) {
			try {
				if (candidate.isDisplayed()) {
					return true;
				}
			} catch (final StaleElementReferenceException ignored) {
				// retry on next loop
			}
		}
		return false;
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(25).toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String handle : new ArrayList<>(driver.getWindowHandles())) {
				driver.switchTo().window(handle);
				if (clickVisibleTextIfPresent(email)) {
					waitForUiToLoad();
					return;
				}
			}
			pause(300);
		}
	}

	private void ensureApplicationWindow() {
		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private void waitForUiToLoad() {
		waitForCondition(() -> {
			try {
				final Object readyState = ((JavascriptExecutor) driver).executeScript("return document.readyState");
				return "complete".equals(readyState);
			} catch (final Exception ignored) {
				return false;
			}
		}, Duration.ofSeconds(15), "Document readyState did not reach complete.");
		pause(350);
	}

	private void assertVisibleText(final String text) {
		findVisibleElement(byVisibleText(text), "visible text: " + text);
	}

	private boolean isVisibleText(final String text) {
		return !visibleElements(byVisibleText(text)).isEmpty();
	}

	private void clickFirstVisibleText(final String... candidates) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String candidate : candidates) {
				if (clickVisibleTextIfPresent(candidate)) {
					return;
				}
			}
			pause(250);
		}
		throw new NoSuchElementException("Could not click any visible text candidate: " + Arrays.toString(candidates));
	}

	private boolean clickVisibleTextIfPresent(final String candidate) {
		for (final WebElement element : visibleElements(byClickableText(candidate))) {
			try {
				scrollIntoView(element);
				element.click();
				waitForUiToLoad();
				return true;
			} catch (final Exception ignored) {
				// Try next candidate.
			}
		}
		return false;
	}

	private WebElement findVisibleElement(final By locator, final String description) {
		final long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT.toMillis();
		while (System.currentTimeMillis() < deadline) {
			final List<WebElement> elements = visibleElements(locator);
			if (!elements.isEmpty()) {
				return elements.get(0);
			}
			pause(250);
		}
		throw new TimeoutException("Timed out waiting for " + description);
	}

	private List<WebElement> visibleElements(final By locator) {
		final List<WebElement> visible = new ArrayList<>();
		for (final WebElement element : driver.findElements(locator)) {
			try {
				if (element.isDisplayed()) {
					visible.add(element);
				}
			} catch (final StaleElementReferenceException ignored) {
				// stale element can happen during rerenders
			}
		}
		return visible;
	}

	private WebElement firstAncestorContainer(final WebElement element) {
		final List<WebElement> containers = element.findElements(By.xpath("ancestor::section[1] | ancestor::article[1]"
				+ " | ancestor::div[contains(@class,'card')][1] | ancestor::div[1]"));
		return containers.isEmpty() ? element : containers.get(0);
	}

	private String getNearbySectionText(final String headingText) {
		final WebElement heading = findVisibleElement(byVisibleText(headingText), headingText);
		return firstAncestorContainer(heading).getText();
	}

	private boolean looksLikeAUserName(final String sectionText) {
		for (final String line : sectionText.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if ("Informaci\u00f3n General".equalsIgnoreCase(trimmed) || "BUSINESS PLAN".equalsIgnoreCase(trimmed)
					|| "Cambiar Plan".equalsIgnoreCase(trimmed) || EMAIL_PATTERN.matcher(trimmed).find()) {
				continue;
			}
			if (trimmed.matches(".*[A-Za-z].*") && trimmed.split("\\s+").length >= 2) {
				return true;
			}
		}
		return false;
	}

	private Path createArtifactDir() throws IOException {
		final String configured = getConfig("saleads.artifactDir", "SALEADS_ARTIFACT_DIR", "target/saleads-mi-negocio");
		return Files.createDirectories(Path.of(configured));
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), screenshotDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() throws IOException {
		if (artifactDir == null) {
			return;
		}

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("# SaleADS Mi Negocio Full Workflow Report\n\n");
		reportBuilder.append("| Validation | Status |\n");
		reportBuilder.append("| --- | --- |\n");
		for (final String field : REPORT_FIELDS) {
			reportBuilder.append("| ").append(field).append(" | ").append(report.getOrDefault(field, "FAIL")).append(" |\n");
		}

		if (!errors.isEmpty()) {
			reportBuilder.append("\n## Errors\n");
			for (final Map.Entry<String, String> entry : errors.entrySet()) {
				reportBuilder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		if (!capturedUrls.isEmpty()) {
			reportBuilder.append("\n## Captured URLs\n");
			for (final Map.Entry<String, String> entry : capturedUrls.entrySet()) {
				reportBuilder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		reportBuilder.append("\n## Evidence\n");
		reportBuilder.append("- Screenshots directory: ").append(screenshotDir).append('\n');

		Files.writeString(artifactDir.resolve("final-report.md"), reportBuilder.toString());
	}

	private void initializeReport() {
		for (final String field : REPORT_FIELDS) {
			report.put(field, "FAIL");
		}
	}

	private void waitForCondition(final BooleanSupplier condition, final Duration timeout, final String errorMessage) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			pause(200);
		}
		throw new TimeoutException(errorMessage);
	}

	private void scrollIntoView(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		} catch (final Exception ignored) {
			// best effort
		}
	}

	private By byVisibleText(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath("//*[contains(normalize-space(.), " + literal + ")]");
	}

	private By byClickableText(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath("//button[contains(normalize-space(.), " + literal + ")]"
				+ " | //a[contains(normalize-space(.), " + literal + ")]"
				+ " | //*[@role='button' and contains(normalize-space(.), " + literal + ")]"
				+ " | //span[contains(normalize-space(.), " + literal + ")]"
				+ " | //div[contains(normalize-space(.), " + literal + ")]");
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final String[] parts = text.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int index = 0; index < parts.length; index++) {
			if (index > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[index]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private boolean getBooleanConfig(final String systemProperty, final String envVariable, final boolean defaultValue) {
		return Boolean.parseBoolean(getConfig(systemProperty, envVariable, String.valueOf(defaultValue)));
	}

	private String getRequiredConfig(final String systemProperty, final String envVariable) {
		final String value = getConfig(systemProperty, envVariable, "").trim();
		if (value.isEmpty()) {
			throw new IllegalStateException(
					"Missing required configuration. Set env " + envVariable + " or system property " + systemProperty + ".");
		}
		return value;
	}

	private String getConfig(final String systemProperty, final String envVariable, final String defaultValue) {
		final String propertyValue = System.getProperty(systemProperty);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		final String envValue = System.getenv(envVariable);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return defaultValue;
	}

	private void pause(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Execution interrupted.", interruptedException);
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
