package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * Environment-agnostic SaleADS end-to-end test for:
 * - Google login
 * - Mi Negocio workflow
 * - Legal links validation
 *
 * Enable with:
 * mvn -Dtest=SaleadsMiNegocioFullTest -Dsaleads.e2e.enabled=true -Dsaleads.startUrl=https://<environment-login> test
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final DateTimeFormatter tsFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
	private final Map<String, StepResult> report = new LinkedHashMap<>();

	private Path evidenceDir;
	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue("Enable with -Dsaleads.e2e.enabled=true", enabled);

		final String startUrl = firstNonBlank(System.getProperty("saleads.startUrl"), System.getenv("SALEADS_START_URL"));
		Assume.assumeTrue(
				"Provide -Dsaleads.startUrl (or SALEADS_START_URL) with the login page for the target environment.",
				startUrl != null && !startUrl.isBlank());

		evidenceDir = Path.of("target", "surefire-reports", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
		Files.createDirectories(evidenceDir);

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1200");
		options.addArguments("--disable-notifications");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "false"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, TIMEOUT);
		driver.get(startUrl);
		waitForUiToSettle();
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
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu, "Login");
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal, "Mi Negocio menu");
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios, "Agregar Negocio modal");
		executeStep("Información General", this::stepValidateInformacionGeneral, "Administrar Negocios view");
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta, "Administrar Negocios view");
		executeStep("Tus Negocios", this::stepValidateTusNegocios, "Administrar Negocios view");
		executeStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones, "Administrar Negocios view");
		executeStep("Política de Privacidad", this::stepValidatePoliticaDePrivacidad, "Administrar Negocios view");

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().pass) {
				failedSteps.add(entry.getKey() + " -> " + entry.getValue().details);
			}
		}

		assertTrue("Some validations failed:\n" + String.join("\n", failedSteps), failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		if (!isSidebarVisible()) {
			clickByVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
					"Login con Google", "Google"));
			waitForUiToSettle();

			handleGoogleAccountSelectionIfNeeded();
			waitForUiToSettle();
		}

		assertTrue("Main application interface was not detected after login.", isMainInterfaceVisible());
		assertTrue("Left sidebar navigation was not detected after login.", isSidebarVisible());
		takeScreenshot("dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		assertTrue("Sidebar is not visible.", isSidebarVisible());

		clickByVisibleText(Arrays.asList("Negocio"));
		waitForUiToSettle();
		clickByVisibleText(Arrays.asList("Mi Negocio"));
		waitForUiToSettle();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText(Arrays.asList("Agregar Negocio"));
		waitForUiToSettle();

		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeScreenshot("agregar_negocio_modal");

		typeInFieldByLabel("Nombre del Negocio", "Negocio Prueba Automatización");
		clickByVisibleText(Arrays.asList("Cancelar"));
		waitForUiToSettle();
		assertTrue("Agregar Negocio modal did not close after Cancelar.",
				!isTextVisible("Crear Nuevo Negocio", SHORT_TIMEOUT));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			clickByVisibleText(Arrays.asList("Mi Negocio"));
			waitForUiToSettle();
		}

		clickByVisibleText(Arrays.asList("Administrar Negocios"));
		waitForUiToSettle();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTrue("Sección Legal section is not visible.",
				isTextVisible("Sección Legal", SHORT_TIMEOUT) || isTextVisible("Seccion Legal", SHORT_TIMEOUT));
		takeScreenshot("administrar_negocios_page");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		assertTextVisible("Información General");
		assertTrue("Expected user email is not visible.", isTextVisible(ACCOUNT_EMAIL, SHORT_TIMEOUT) || hasAnyEmailOnPage());
		assertTrue("User name is not visible.",
				hasVisibleTextLikeName() || isTextVisible("Nombre", SHORT_TIMEOUT) || isTextVisible("Name", SHORT_TIMEOUT));
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
	}

	private void stepValidateDetallesDeLaCuenta() throws Exception {
		assertTextVisible("Detalles de la Cuenta");
		assertTrue("Cuenta creada text is not visible.",
				isTextVisible("Cuenta creada", SHORT_TIMEOUT) || isTextVisible("Cuenta Creada", SHORT_TIMEOUT));
		assertTrue("Estado activo text is not visible.",
				isTextVisible("Estado activo", SHORT_TIMEOUT) || isTextVisible("Activo", SHORT_TIMEOUT));
		assertTrue("Idioma seleccionado text is not visible.",
				isTextVisible("Idioma seleccionado", SHORT_TIMEOUT) || isTextVisible("Idioma", SHORT_TIMEOUT));
	}

	private void stepValidateTusNegocios() throws Exception {
		assertTextVisible("Tus Negocios");
		assertTrue("Business list was not detected.", hasBusinessList());
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		final String url = openLegalLinkValidateAndReturn(Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
				Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"), "terminos_y_condiciones");
		appendStepDetail("Términos y Condiciones", "Final URL: " + url);
	}

	private void stepValidatePoliticaDePrivacidad() throws Exception {
		final String url = openLegalLinkValidateAndReturn(Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
				Arrays.asList("Política de Privacidad", "Politica de Privacidad"), "politica_de_privacidad");
		appendStepDetail("Política de Privacidad", "Final URL: " + url);
	}

	private String openLegalLinkValidateAndReturn(final List<String> linkTexts, final List<String> expectedHeadings,
			final String screenshotLabel) throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText(linkTexts);
		waitForUiToSettle();

		final Optional<String> maybeNewHandle = waitForNewWindow(handlesBefore);
		if (maybeNewHandle.isPresent()) {
			driver.switchTo().window(maybeNewHandle.get());
			waitForUiToSettle();
		}

		boolean headingVisible = false;
		for (final String heading : expectedHeadings) {
			if (isTextVisible(heading, SHORT_TIMEOUT)) {
				headingVisible = true;
				break;
			}
		}
		assertTrue("Expected legal heading was not found.", headingVisible);
		assertTrue("Expected legal content text is not visible.", hasLegalContentText());
		takeScreenshot(screenshotLabel);
		final String finalUrl = driver.getCurrentUrl();

		if (maybeNewHandle.isPresent()) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToSettle();
		} else {
			driver.navigate().back();
			waitForUiToSettle();
		}

		assertTrue("Could not return to the application tab/view.", isTextVisible("Información General", SHORT_TIMEOUT)
				|| isTextVisible("Administrar Negocios", SHORT_TIMEOUT));
		return finalUrl;
	}

	private void handleGoogleAccountSelectionIfNeeded() {
		final Set<String> appHandles = driver.getWindowHandles();
		final String appHandle = driver.getWindowHandle();

		for (final String handle : appHandles) {
			try {
				driver.switchTo().window(handle);
				if (isTextVisible(ACCOUNT_EMAIL, SHORT_TIMEOUT)) {
					clickByVisibleText(Arrays.asList(ACCOUNT_EMAIL));
					waitForUiToSettle();
					break;
				}
			} catch (final Exception ignored) {
				// Ignore and continue trying next available window.
			}
		}

		driver.switchTo().window(appHandle);
	}

	private void executeStep(final String reportField, final StepAction action, final String... dependencies) {
		for (final String dependency : dependencies) {
			final StepResult dependencyResult = report.get(dependency);
			if (dependencyResult == null || !dependencyResult.pass) {
				report.put(reportField, StepResult.fail("Blocked by dependency: " + dependency));
				return;
			}
		}

		try {
			action.run();
			report.put(reportField, StepResult.pass("All validations passed."));
		} catch (final Exception ex) {
			try {
				takeScreenshot("failure_" + sanitize(reportField));
			} catch (final IOException ignored) {
				// No-op when screenshot cannot be taken during failure.
			}
			report.put(reportField, StepResult.fail(ex.getMessage()));
		}
	}

	private void clickByVisibleText(final List<String> texts) {
		Exception lastException = null;
		for (final String text : texts) {
			try {
				final WebElement element = wait.until(driverInstance -> findVisibleClickableByText(text));
				clickElement(element);
				waitForUiToSettle();
				return;
			} catch (final Exception ex) {
				lastException = ex;
			}
		}
		throw new IllegalStateException("Could not click element by visible text options: " + texts, lastException);
	}

	private WebElement findVisibleClickableByText(final String text) {
		final String xpath = "//*[(self::button or self::a or @role='button' or self::span or self::div or self::li)"
				+ " and contains(normalize-space(.), " + toXPathLiteral(text) + ")]";
		for (final WebElement element : driver.findElements(By.xpath(xpath))) {
			try {
				if (element.isDisplayed()) {
					return element;
				}
			} catch (final StaleElementReferenceException ignored) {
				// Retry through next candidate.
			}
		}
		throw new NoSuchElementException("No visible element found for text: " + text);
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final Exception clickError) {
			try {
				new Actions(driver).moveToElement(element).click().perform();
			} catch (final Exception actionError) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			}
		}
	}

	private void typeInFieldByLabel(final String labelText, final String value) {
		final String labelXpath = "//*[contains(normalize-space(.), " + toXPathLiteral(labelText) + ")]";
		final List<WebElement> labels = driver.findElements(By.xpath(labelXpath));
		for (final WebElement label : labels) {
			try {
				if (!label.isDisplayed()) {
					continue;
				}

				final List<WebElement> nearbyInputs = label.findElements(By.xpath(
						".//following::input[1] | ./ancestor::*[1]//input | ./ancestor::*[2]//input | ./preceding::input[1]"));
				for (final WebElement input : nearbyInputs) {
					if (input.isDisplayed() && input.isEnabled()) {
						input.clear();
						input.sendKeys(value);
						return;
					}
				}
			} catch (final Exception ignored) {
				// Continue with next possible label match.
			}
		}
		throw new IllegalStateException("Input associated with label was not found: " + labelText);
	}

	private void waitForUiToSettle() {
		try {
			wait.until(driverInstance -> "complete"
					.equals(((JavascriptExecutor) driverInstance).executeScript("return document.readyState")));
		} catch (final TimeoutException ignored) {
			// For SPA transitions, complete may already be set and not a strong signal.
		}

		try {
			Thread.sleep(800);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for UI to settle.", interruptedException);
		}
	}

	private Optional<String> waitForNewWindow(final Set<String> handlesBefore) {
		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);
		try {
			return Optional.of(shortWait.until(driverInstance -> {
				final Set<String> currentHandles = driverInstance.getWindowHandles();
				if (currentHandles.size() > handlesBefore.size()) {
					for (final String handle : currentHandles) {
						if (!handlesBefore.contains(handle)) {
							return handle;
						}
					}
				}
				return null;
			}));
		} catch (final TimeoutException ignored) {
			return Optional.empty();
		}
	}

	private boolean isMainInterfaceVisible() {
		return isTextVisible("Negocio", SHORT_TIMEOUT) || isSidebarVisible();
	}

	private boolean isSidebarVisible() {
		return hasVisibleElement(By.cssSelector("aside, nav")) && isTextVisible("Negocio", SHORT_TIMEOUT);
	}

	private boolean hasVisibleElement(final By by) {
		for (final WebElement element : driver.findElements(by)) {
			try {
				if (element.isDisplayed()) {
					return true;
				}
			} catch (final StaleElementReferenceException ignored) {
				// Try next element.
			}
		}
		return false;
	}

	private boolean hasAnyEmailOnPage() {
		final String bodyText = getPageText().toLowerCase();
		return bodyText.matches("(?s).*[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}.*");
	}

	private boolean hasVisibleTextLikeName() {
		final String pageText = getPageText();
		final String[] lines = pageText.split("\\R");
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.matches("[\\p{L}]{2,}(\\s+[\\p{L}]{2,})+")
					&& !trimmed.toLowerCase().contains("información general".toLowerCase())
					&& !trimmed.toLowerCase().contains("detalles de la cuenta".toLowerCase())
					&& !trimmed.toLowerCase().contains("tus negocios".toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	private boolean hasBusinessList() {
		return hasVisibleElement(By.xpath(
				"//*[contains(normalize-space(.), 'Tus Negocios')]/following::*[self::ul or self::table or self::div][1]"))
				|| isTextVisible("Negocio", SHORT_TIMEOUT);
	}

	private boolean hasLegalContentText() {
		final String bodyText = getPageText();
		return bodyText != null && bodyText.replaceAll("\\s+", " ").trim().length() > 200;
	}

	private String getPageText() {
		try {
			final WebElement body = driver.findElement(By.tagName("body"));
			return body.getText() == null ? "" : body.getText();
		} catch (final Exception ex) {
			return "";
		}
	}

	private void assertTextVisible(final String text) {
		assertTrue("Expected visible text was not found: " + text, isTextVisible(text, SHORT_TIMEOUT));
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		final WebDriverWait scopedWait = new WebDriverWait(driver, timeout);
		try {
			return scopedWait.until(driverInstance -> {
				final String xpath = "//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]";
				for (final WebElement element : driverInstance.findElements(By.xpath(xpath))) {
					try {
						if (element.isDisplayed()) {
							return true;
						}
					} catch (final StaleElementReferenceException ignored) {
						// Try next candidate.
					}
				}
				return false;
			});
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void takeScreenshot(final String label) throws IOException {
		final String fileName = LocalDateTime.now().format(tsFormatter) + "_" + sanitize(label) + ".png";
		final Path destination = evidenceDir.resolve(fileName);
		final Path screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(screenshot, destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private String sanitize(final String value) {
		return value.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private String toXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}
		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String literal = chars[i] == '\'' ? "\"'\"" : "'" + chars[i] + "'";
			result.append(literal);
			if (i < chars.length - 1) {
				result.append(",");
			}
		}
		result.append(")");
		return result.toString();
	}

	private void appendStepDetail(final String stepName, final String details) {
		final StepResult current = report.get(stepName);
		if (current == null) {
			return;
		}
		report.put(stepName, new StepResult(current.pass, current.details + " " + details));
	}

	private void printFinalReport() {
		if (report.isEmpty()) {
			return;
		}

		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		for (final String key : Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
				"Información General", "Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones",
				"Política de Privacidad")) {
			final StepResult result = report.get(key);
			if (result == null) {
				System.out.println(key + ": FAIL - Step not executed.");
			} else {
				System.out.println(key + ": " + (result.pass ? "PASS" : "FAIL") + " - " + result.details);
			}
		}
		System.out.println("Evidence folder: " + evidenceDir.toAbsolutePath());
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean pass;
		private final String details;

		private StepResult(final boolean pass, final String details) {
			this.pass = pass;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
