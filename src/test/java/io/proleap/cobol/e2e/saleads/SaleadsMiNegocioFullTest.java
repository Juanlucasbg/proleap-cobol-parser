package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
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
import org.openqa.selenium.WindowType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_POLITICA = "Política de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private final Map<String, Boolean> reportStatus = new LinkedHashMap<>();
	private final Map<String, String> reportFailures = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();
	private final List<String> screenshots = new ArrayList<>();
	private String applicationWindowHandle;
	private long timeoutSeconds;

	@Before
	public void setUp() throws IOException {
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		timeoutSeconds = Long.parseLong(System.getenv().getOrDefault("SALEADS_TIMEOUT_SECONDS", "30"));
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final String configuredEvidencePath = System.getenv("SALEADS_EVIDENCE_DIR");
		evidenceDirectory = configuredEvidencePath == null || configuredEvidencePath.isBlank()
				? Paths.get("target", "saleads-evidence", timestamp)
				: Paths.get(configuredEvidencePath, "saleads-evidence-" + timestamp);
		Files.createDirectories(evidenceDirectory);

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1400");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		final String loginUrl = requiredEnv("SALEADS_LOGIN_URL");
		driver.get(loginUrl);
		waitForUiLoad();
		applicationWindowHandle = driver.getWindowHandle();
		checkpointScreenshot("00-initial-login-page");
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_AGREGAR_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(REPORT_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(REPORT_TERMINOS, () -> stepValidateLegalDocument("Términos y Condiciones", "terminos-y-condiciones"));
		runStep(REPORT_POLITICA, () -> stepValidateLegalDocument("Política de Privacidad", "politica-de-privacidad"));

		writeFinalReport();

		assertTrue("FAILURES:\n" + buildFailuresSummary(), reportFailures.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		if (isMainInterfaceVisible()) {
			checkpointScreenshot("01-dashboard-loaded");
			return;
		}

		final Set<String> handlesBeforeLogin = driver.getWindowHandles();
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");

		final String googlePopupHandle = waitForNewWindowHandle(handlesBeforeLogin, 12);
		if (googlePopupHandle != null) {
			driver.switchTo().window(googlePopupHandle);
			waitForUiLoad();
			selectGoogleAccountIfVisible();
			switchToApplicationWindow();
		}

		waitForMainInterface();
		assertVisibleText("Negocio");
		assertAnyVisible(By.xpath("//aside"), By.xpath("//nav"));
		checkpointScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		switchToApplicationWindow();
		expandMiNegocioIfCollapsed();
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		checkpointScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		switchToApplicationWindow();
		expandMiNegocioIfCollapsed();
		clickByVisibleText("Agregar Negocio");

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final WebElement input = waitForAnyVisible(By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"), By.xpath("//input"));
		input.clear();
		input.sendKeys("Negocio Prueba Automatización");
		checkpointScreenshot("03-agregar-negocio-modal");
		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byVisibleTextContains("Crear Nuevo Negocio")));
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		switchToApplicationWindow();
		expandMiNegocioIfCollapsed();
		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();
		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		checkpointScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("Información General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final WebElement section = sectionByHeading("Información General");
		final List<WebElement> emailCandidates = section.findElements(By.xpath(".//*[contains(normalize-space(.), '@')]"));
		if (emailCandidates.isEmpty()) {
			fail("User email is not visible in Información General.");
		}

		final List<WebElement> userNameCandidates = section
				.findElements(By.xpath(".//*[self::h1 or self::h2 or self::h3 or self::p or self::span]"
						+ "[string-length(normalize-space()) > 2]"
						+ "[not(contains(normalize-space(.), '@'))]"
						+ "[not(contains(normalize-space(.), 'BUSINESS PLAN'))]"
						+ "[not(contains(normalize-space(.), 'Información General'))]"
						+ "[not(contains(normalize-space(.), 'Cambiar Plan'))]"));
		if (userNameCandidates.isEmpty()) {
			fail("User name is not visible in Información General.");
		}
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		final WebElement section = sectionByHeading("Tus Negocios");
		final List<WebElement> businessRows = section.findElements(By.xpath(
				".//*[self::li or self::tr or contains(@class,'business') or contains(@class,'card') or contains(@class,'item')]"));
		if (businessRows.isEmpty()) {
			fail("Business list is not visible in Tus Negocios.");
		}
	}

	private void stepValidateLegalDocument(final String linkText, final String screenshotPrefix) throws IOException {
		switchToApplicationWindow();
		final String appUrlBefore = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForUiLoad();

		final String newHandle = waitForNewWindowHandle(handlesBeforeClick, 8);
		final boolean openedNewTab = newHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		}

		assertVisibleText(linkText);
		assertAnyVisible(By.xpath("//p[string-length(normalize-space()) > 40]"),
				By.xpath("//article//*[string-length(normalize-space()) > 40]"),
				By.xpath("//*[contains(@class,'content')]//*[string-length(normalize-space()) > 40]"));

		checkpointScreenshot("05-" + screenshotPrefix);
		capturedUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			switchToApplicationWindow();
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
			if (!driver.getCurrentUrl().equals(appUrlBefore)) {
				driver.get(appUrlBefore);
				waitForUiLoad();
			}
		}
	}

	private void selectGoogleAccountIfVisible() {
		final List<WebElement> accountOptions = driver.findElements(byVisibleTextContains(GOOGLE_ACCOUNT_EMAIL));
		if (!accountOptions.isEmpty() && accountOptions.get(0).isDisplayed()) {
			accountOptions.get(0).click();
			waitForUiLoad();
		}
	}

	private void waitForMainInterface() {
		if (!waitForAnyVisibleWithin((int) timeoutSeconds, By.xpath("//aside"), By.xpath("//nav"),
				byVisibleTextContains("Mi Negocio"), byVisibleTextContains("Negocio"))) {
			fail("Main application interface did not load after login. Current URL: " + driver.getCurrentUrl());
		}
	}

	private void expandMiNegocioIfCollapsed() {
		if (!isVisible(byVisibleTextContains("Administrar Negocios"))) {
			clickByVisibleText("Mi Negocio", "Negocio");
		}
		if (!waitForAnyVisibleWithin((int) timeoutSeconds, byVisibleTextContains("Agregar Negocio"),
				byVisibleTextContains("Administrar Negocios"))) {
			fail("Mi Negocio submenu did not expand. Current URL: " + driver.getCurrentUrl());
		}
	}

	private WebElement sectionByHeading(final String headingText) {
		final String headingLiteral = toXpathLiteral(headingText);
		final List<By> locators = List.of(
				By.xpath("//*[self::section or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4]"
						+ "[contains(normalize-space(.), " + headingLiteral + ")]]"),
				By.xpath("//*[contains(@class,'card') or contains(@class,'section')][.//*[contains(normalize-space(.), "
						+ headingLiteral + ")]]"));

		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}

		fail("Section not found for heading: " + headingText);
		return null;
	}

	private void clickByVisibleText(final String... texts) {
		final List<By> locators = new ArrayList<>();
		for (final String text : texts) {
			final String literal = toXpathLiteral(text);
			locators.add(By.xpath(
					"//*[self::button or self::a or @role='button' or contains(@class,'btn')][contains(normalize-space(.), "
							+ literal + ")]"));
			locators.add(By.xpath("//*[contains(normalize-space(.), " + literal + ")]"));
		}

		final WebElement element = waitForAnyVisible(locators.toArray(new By[0]));
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiLoad();
	}

	private WebElement waitForAnyVisible(final By... locators) {
		return wait.until(driver -> {
			for (final By locator : locators) {
				final List<WebElement> elements = driver.findElements(locator);
				for (final WebElement element : elements) {
					try {
						if (element.isDisplayed()) {
							return element;
						}
					} catch (final StaleElementReferenceException ignored) {
						// retry via wait loop
					}
				}
			}
			return null;
		});
	}

	private void assertVisibleText(final String text) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleTextContains(text)));
		} catch (final TimeoutException ex) {
			fail("Expected visible text not found: " + text);
		}
	}

	private void assertAnyVisible(final By... locators) {
		try {
			waitForAnyVisible(locators);
		} catch (final TimeoutException ex) {
			fail("None of the expected elements are visible.");
		}
	}

	private boolean waitForAnyVisibleWithin(final int seconds, final By... locators) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(seconds));
			return shortWait.until(driver -> {
				for (final By locator : locators) {
					if (isVisible(locator)) {
						return true;
					}
				}
				return false;
			});
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void waitForUiLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		try {
			Thread.sleep(500L);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private String waitForNewWindowHandle(final Set<String> handlesBefore, final int seconds) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(seconds));
			return shortWait.until(driver -> {
				final Set<String> handlesAfter = driver.getWindowHandles();
				for (final String handle : handlesAfter) {
					if (!handlesBefore.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException ex) {
			return null;
		}
	}

	private void switchToApplicationWindow() {
		final Set<String> handles = driver.getWindowHandles();
		if (handles.contains(applicationWindowHandle)) {
			driver.switchTo().window(applicationWindowHandle);
			return;
		}

		// Fallback: if the original window closed, use the first available tab.
		if (!handles.isEmpty()) {
			final String fallbackHandle = handles.iterator().next();
			driver.switchTo().window(fallbackHandle);
			applicationWindowHandle = fallbackHandle;
			return;
		}

		driver.switchTo().newWindow(WindowType.TAB);
		applicationWindowHandle = driver.getWindowHandle();
	}

	private By byVisibleTextContains(final String text) {
		return By.xpath("//*[contains(normalize-space(.), " + toXpathLiteral(text) + ")]");
	}

	private boolean isVisible(final By locator) {
		try {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final NoSuchElementException ex) {
			return false;
		}
	}

	private void checkpointScreenshot(final String name) throws IOException {
		final Path targetPath = evidenceDirectory.resolve(name + ".png");
		final Path sourcePath = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
		screenshots.add(targetPath.toString());
	}

	private void runStep(final String reportField, final StepAction stepAction) {
		try {
			stepAction.run();
			reportStatus.put(reportField, Boolean.TRUE);
		} catch (final Throwable throwable) {
			reportStatus.put(reportField, Boolean.FALSE);
			final String message = throwable.getMessage() == null ? throwable.toString() : throwable.getMessage();
			String failureScreenshot = "";
			try {
				final String screenshotName = "failure-" + sanitizeFileName(reportField);
				checkpointScreenshot(screenshotName);
				failureScreenshot = evidenceDirectory.resolve(screenshotName + ".png").toString();
			} catch (final Exception ignored) {
				// keep original failure if screenshot capture is not possible
			}
			reportFailures.put(reportField,
					message + "\nCurrent URL: " + safeCurrentUrl()
							+ (failureScreenshot.isBlank() ? "" : "\nFailure screenshot: " + failureScreenshot));
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"goal\": \"saleads_mi_negocio_full_test\",\n");
		builder.append("  \"evidence_directory\": \"").append(escapeJson(evidenceDirectory.toString())).append("\",\n");
		builder.append("  \"status\": {\n");

		final List<Map.Entry<String, Boolean>> statuses = new ArrayList<>(reportStatus.entrySet());
		for (int i = 0; i < statuses.size(); i++) {
			final Map.Entry<String, Boolean> entry = statuses.get(i);
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(entry.getValue() ? "PASS" : "FAIL").append("\"");
			builder.append(i < statuses.size() - 1 ? ",\n" : "\n");
		}
		builder.append("  },\n");

		builder.append("  \"captured_urls\": {\n");
		final List<Map.Entry<String, String>> urls = new ArrayList<>(capturedUrls.entrySet());
		for (int i = 0; i < urls.size(); i++) {
			final Map.Entry<String, String> entry = urls.get(i);
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(escapeJson(entry.getValue())).append("\"");
			builder.append(i < urls.size() - 1 ? ",\n" : "\n");
		}
		builder.append("  },\n");

		builder.append("  \"screenshots\": [\n");
		for (int i = 0; i < screenshots.size(); i++) {
			builder.append("    \"").append(escapeJson(screenshots.get(i))).append("\"");
			builder.append(i < screenshots.size() - 1 ? ",\n" : "\n");
		}
		builder.append("  ],\n");

		builder.append("  \"failures\": {\n");
		final List<Map.Entry<String, String>> failures = new ArrayList<>(reportFailures.entrySet());
		for (int i = 0; i < failures.size(); i++) {
			final Map.Entry<String, String> entry = failures.get(i);
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(escapeJson(entry.getValue())).append("\"");
			builder.append(i < failures.size() - 1 ? ",\n" : "\n");
		}
		builder.append("  }\n");
		builder.append("}\n");

		final Path reportPath = evidenceDirectory.resolve("final-report.json");
		Files.writeString(reportPath, builder.toString());
	}

	private String buildFailuresSummary() {
		if (reportFailures.isEmpty()) {
			return "All checks passed.";
		}

		final StringBuilder summary = new StringBuilder();
		for (final Map.Entry<String, String> entry : reportFailures.entrySet()) {
			summary.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}
		return summary.toString();
	}

	private String requiredEnv(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing required environment variable: " + key);
		}
		return value;
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder literal = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			literal.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				literal.append(", \"'\", ");
			}
		}
		literal.append(")");
		return literal.toString();
	}

	private String escapeJson(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	private boolean isMainInterfaceVisible() {
		return isVisible(By.xpath("//aside")) || isVisible(By.xpath("//nav")) || isVisible(byVisibleTextContains("Mi Negocio"))
				|| isVisible(byVisibleTextContains("Negocio"));
	}

	private String sanitizeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (final Exception ignored) {
			return "unavailable";
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
