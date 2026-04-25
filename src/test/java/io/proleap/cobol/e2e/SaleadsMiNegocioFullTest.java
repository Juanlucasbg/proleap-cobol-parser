package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for SaleADS Mi Negocio workflow.
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>SALEADS_LOGIN_URL (preferred) or SALEADS_BASE_URL: login page URL.</li>
 *   <li>SALEADS_HEADLESS: true/false (default true).</li>
 *   <li>SALEADS_EXPECTED_USER_NAME: optional expected visible user name.</li>
 * </ul>
 *
 * <p>Evidence artifacts are written into target/saleads-evidence/&lt;timestamp&gt;.
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(6);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final List<String> orderedReportFields = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true"));

		if (headless) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--disable-popup-blocking");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		evidenceDir = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getenv("SALEADS_BASE_URL"));
		assertNotNull("Set SALEADS_LOGIN_URL or SALEADS_BASE_URL to the current environment login page.", loginUrl);

		driver.get(loginUrl);
		waitForUiLoad();

		final boolean loginOk = executeStep("Login", () -> {
			loginWithGoogle();
			captureCheckpoint("01_dashboard_loaded");
			return "Dashboard loaded and sidebar visible.";
		});

		final boolean menuOk = loginOk
				? executeStep("Mi Negocio menu", () -> {
					openMiNegocioMenu();
					captureCheckpoint("02_mi_negocio_menu_expanded");
					return "Mi Negocio expanded with Agregar Negocio and Administrar Negocios.";
				})
				: blockStep("Mi Negocio menu", "Blocked because Login failed.");

		final boolean modalOk = menuOk
				? executeStep("Agregar Negocio modal", () -> {
					validateAgregarNegocioModal();
					captureCheckpoint("03_agregar_negocio_modal");
					return "Crear Nuevo Negocio modal validated.";
				})
				: blockStep("Agregar Negocio modal", "Blocked because Mi Negocio menu failed.");

		final boolean administrarOk = modalOk
				? executeStep("Administrar Negocios view", () -> {
					openAdministrarNegocios();
					captureCheckpoint("04_administrar_negocios_account_page");
					return "Account page sections are visible.";
				})
				: blockStep("Administrar Negocios view", "Blocked because Agregar Negocio modal failed.");

		final boolean infoOk = administrarOk
				? executeStep("Información General", () -> {
					validateInformacionGeneral();
					return "Información General validated.";
				})
				: blockStep("Información General", "Blocked because Administrar Negocios view failed.");

		final boolean detallesOk = infoOk
				? executeStep("Detalles de la Cuenta", () -> {
					assertTextVisible("Cuenta creada");
					assertTextVisible("Estado activo");
					assertTextVisible("Idioma seleccionado");
					return "Detalles de la Cuenta validated.";
				})
				: blockStep("Detalles de la Cuenta", "Blocked because Información General failed.");

		final boolean negociosOk = detallesOk
				? executeStep("Tus Negocios", () -> {
					validateTusNegocios();
					return "Tus Negocios validated.";
				})
				: blockStep("Tus Negocios", "Blocked because Detalles de la Cuenta failed.");

		final boolean terminosOk = negociosOk
				? executeStep("Términos y Condiciones", () -> {
					final String url = openLegalDocumentAndReturnUrl("Términos y Condiciones", "Términos y Condiciones",
							"08_terminos_y_condiciones");
					return "Final URL: " + url;
				})
				: blockStep("Términos y Condiciones", "Blocked because Tus Negocios failed.");

		if (terminosOk) {
			executeStep("Política de Privacidad", () -> {
				final String url = openLegalDocumentAndReturnUrl("Política de Privacidad", "Política de Privacidad",
						"09_politica_de_privacidad");
				return "Final URL: " + url;
			});
		} else {
			blockStep("Política de Privacidad", "Blocked because Términos y Condiciones failed.");
		}

		final Path reportPath = writeFinalReport();
		final boolean hasFailure = orderedReportFields.stream()
				.map(stepResults::get)
				.anyMatch(result -> result == null || !result.pass);

		assertFalse("One or more workflow validations failed. See report: " + reportPath.toAbsolutePath(), hasFailure);
	}

	private void loginWithGoogle() throws IOException {
		final WebElement loginButton = findFirstVisibleClickableByAnyText(Arrays.asList(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Iniciar sesion con Google",
				"Login with Google",
				"Ingresar con Google",
				"Continuar con Google"));

		clickAndWait(loginButton);
		selectGoogleAccountIfPrompted("juanlucasbarbiergarzon@gmail.com");

		assertSidebarVisible();
		assertAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio"));
	}

	private void openMiNegocioMenu() throws IOException {
		clickTextAndWait("Negocio");
		clickTextAndWait("Mi Negocio");

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickTextAndWait("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		final WebElement input = findVisibleInputNearLabel("Nombre del Negocio");
		if (input != null) {
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatizacion");
		}

		clickTextAndWait("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byExactVisibleText("Crear Nuevo Negocio")));
	}

	private void openAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			clickTextAndWait("Mi Negocio");
		}

		clickTextAndWait("Administrar Negocios");
		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
	}

	private void validateInformacionGeneral() {
		final String bodyText = getVisibleBodyText();
		final String email = extractEmail(bodyText);
		assertNotNull("User email not visible in account page.", email);

		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");

		final String configuredName = firstNonBlank(System.getenv("SALEADS_EXPECTED_USER_NAME"));
		if (configuredName != null) {
			assertTextVisible(configuredName);
		} else {
			final String infoText = getSectionText("Información General");
			assertTrue("Could not infer user name visibility in Información General section.",
					containsLikelyPersonName(infoText, email));
		}
	}

	private void validateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");

		final String sectionText = getSectionText("Tus Negocios");
		final String compact = sectionText.replace('\n', ' ').replaceAll("\\s+", " ").trim();
		assertTrue("Business list does not look visible in Tus Negocios section.", compact.length() > 40);
	}

	private String openLegalDocumentAndReturnUrl(final String linkText, final String expectedHeading,
			final String screenshotName) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final String oldUrl = driver.getCurrentUrl();
		final Set<String> existingHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickTextAndWait(linkText);

		final String newHandle = waitForNewHandle(existingHandles, Duration.ofSeconds(10));
		final boolean openedNewTab = newHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(newHandle);
		} else {
			waitForUrlChange(oldUrl, Duration.ofSeconds(15));
		}

		waitForUiLoad();
		assertTextVisible(expectedHeading);

		final String legalBody = getVisibleBodyText().replaceAll("\\s+", " ").trim();
		assertTrue("Legal content text seems empty for " + expectedHeading, legalBody.length() > 120);

		captureCheckpoint(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		assertAnyTextVisible(Arrays.asList("Sección Legal", "Información General", "Tus Negocios"));
		return finalUrl;
	}

	private void selectGoogleAccountIfPrompted(final String accountEmail) {
		final long timeoutAt = System.currentTimeMillis() + Duration.ofSeconds(25).toMillis();

		while (System.currentTimeMillis() < timeoutAt) {
			final List<String> handles = new ArrayList<>(driver.getWindowHandles());
			for (final String handle : handles) {
				driver.switchTo().window(handle);

				final WebElement accountRow = findVisibleElementByText(accountEmail, Duration.ofSeconds(1));
				if (accountRow != null) {
					clickAndWait(accountRow);
					return;
				}
			}

			if (isTextVisible("Negocio", Duration.ofSeconds(1)) || isTextVisible("Mi Negocio", Duration.ofSeconds(1))) {
				return;
			}

			sleepSilently(700);
		}
	}

	private void assertSidebarVisible() {
		final List<WebElement> navCandidates = driver.findElements(By.cssSelector("aside, nav"));
		final boolean visible = navCandidates.stream().anyMatch(WebElement::isDisplayed);
		assertTrue("Left sidebar/navigation is not visible after login.", visible);
	}

	private void clickTextAndWait(final String text) {
		final WebElement element = findFirstVisibleClickableByAnyText(Arrays.asList(text));
		clickAndWait(element);
	}

	private void clickAndWait(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiLoad();
	}

	private void assertTextVisible(final String text) {
		final WebElement element = findVisibleElementByText(text, DEFAULT_TIMEOUT);
		assertNotNull("Expected visible text not found: " + text, element);
	}

	private void assertAnyTextVisible(final List<String> texts) {
		for (final String text : texts) {
			if (isTextVisible(text, Duration.ofSeconds(3))) {
				return;
			}
		}
		throw new AssertionError("None of the expected visible texts were found: " + texts);
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		return findVisibleElementByText(text, timeout) != null;
	}

	private WebElement findFirstVisibleClickableByAnyText(final List<String> texts) {
		final long timeoutAt = System.currentTimeMillis() + DEFAULT_TIMEOUT.toMillis();
		Throwable lastError = null;

		while (System.currentTimeMillis() < timeoutAt) {
			for (final String text : texts) {
				try {
					for (final By by : clickableTextLocators(text)) {
						final List<WebElement> elements = driver.findElements(by);
						for (final WebElement element : elements) {
							if (element.isDisplayed() && element.isEnabled()) {
								return element;
							}
						}
					}
				} catch (final Throwable error) {
					lastError = error;
				}
			}

			sleepSilently(300);
		}

		if (lastError != null) {
			throw new AssertionError("Clickable element not found for any text: " + texts, lastError);
		}

		throw new AssertionError("Clickable element not found for any text: " + texts);
	}

	private WebElement findVisibleElementByText(final String text, final Duration timeout) {
		final long timeoutAt = System.currentTimeMillis() + timeout.toMillis();

		while (System.currentTimeMillis() < timeoutAt) {
			for (final By by : visibleTextLocators(text)) {
				final List<WebElement> elements = driver.findElements(by);
				for (final WebElement element : elements) {
					try {
						if (element.isDisplayed()) {
							return element;
						}
					} catch (final Exception ignored) {
						// Stale or detached elements are retried on next poll.
					}
				}
			}
			sleepSilently(250);
		}

		return null;
	}

	private List<By> visibleTextLocators(final String text) {
		final String literal = xpathLiteral(text);
		final List<By> bys = new ArrayList<>();
		bys.add(By.xpath("//*[normalize-space(text())=" + literal + "]"));
		bys.add(By.xpath("//*[normalize-space(.)=" + literal + "]"));
		bys.add(By.xpath("//*[contains(normalize-space(.)," + literal + ")]"));
		return bys;
	}

	private List<By> clickableTextLocators(final String text) {
		final String literal = xpathLiteral(text);
		final List<By> bys = new ArrayList<>();
		bys.add(By.xpath(
				"//*[self::button or self::a or @role='button' or @type='button'][contains(normalize-space(.)," + literal
						+ ")]"));
		bys.add(By.xpath(
				"//*[contains(normalize-space(.)," + literal + ")]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"));
		bys.add(By.xpath("//*[contains(normalize-space(.)," + literal + ")]"));
		return bys;
	}

	private By byExactVisibleText(final String text) {
		return By.xpath("//*[normalize-space(.)=" + xpathLiteral(text) + "]");
	}

	private String getSectionText(final String headingText) {
		final WebElement heading = findVisibleElementByText(headingText, DEFAULT_TIMEOUT);
		assertNotNull("Heading not found: " + headingText, heading);

		WebElement section = null;
		try {
			section = heading.findElement(By.xpath("./ancestor::section[1]"));
		} catch (final NoSuchElementException ignored) {
			// Fall back to nearest div container.
		}

		if (section == null) {
			try {
				section = heading.findElement(By.xpath("./ancestor::div[1]"));
			} catch (final NoSuchElementException ignored) {
				section = heading;
			}
		}

		return section.getText();
	}

	private WebElement findVisibleInputNearLabel(final String labelText) {
		final String labelLiteral = xpathLiteral(labelText);
		final List<By> inputLocators = Arrays.asList(
				By.xpath("//label[contains(normalize-space(.)," + labelLiteral + ")]/following::input[1]"),
				By.xpath("//input[contains(@placeholder," + labelLiteral + ")]"),
				By.xpath("//div[contains(normalize-space(.)," + labelLiteral + ")]//input[1]"));

		for (final By locator : inputLocators) {
			for (final WebElement input : driver.findElements(locator)) {
				if (input.isDisplayed()) {
					return input;
				}
			}
		}

		return null;
	}

	private String extractEmail(final String text) {
		final Matcher matcher = EMAIL_PATTERN.matcher(text);
		return matcher.find() ? matcher.group() : null;
	}

	private boolean containsLikelyPersonName(final String infoSectionText, final String email) {
		final String[] lines = infoSectionText.split("\\R");
		for (String line : lines) {
			line = line.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.contains(email)) {
				continue;
			}

			final String lower = line.toLowerCase();
			if (lower.contains("información general") || lower.contains("business plan") || lower.contains("cambiar plan")) {
				continue;
			}

			if (line.matches(".*[A-Za-zÁÉÍÓÚÑáéíóúñ].*") && line.length() >= 4) {
				return true;
			}
		}

		return false;
	}

	private void waitForUiLoad() {
		waitForDocumentReady();
		sleepSilently(450);
	}

	private void waitForDocumentReady() {
		final ExpectedCondition<Boolean> documentReady = webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState"));
		wait.until(documentReady);
	}

	private String waitForNewHandle(final Set<String> existingHandles, final Duration timeout) {
		try {
			final WebDriverWait localWait = new WebDriverWait(driver, timeout);
			return localWait.until(webDriver -> {
				for (final String handle : webDriver.getWindowHandles()) {
					if (!existingHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException ignored) {
			return null;
		}
	}

	private void waitForUrlChange(final String oldUrl, final Duration timeout) {
		try {
			final WebDriverWait localWait = new WebDriverWait(driver, timeout);
			localWait.until(webDriver -> !oldUrl.equals(webDriver.getCurrentUrl()));
		} catch (final TimeoutException ignored) {
			// Some SPA transitions may keep the same URL; text assertions still validate destination.
		}
	}

	private String getVisibleBodyText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private void captureCheckpoint(final String checkpointName) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDir.resolve(checkpointName + ".png");
		Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private boolean executeStep(final String stepName, final StepExecutable executable) {
		try {
			final String detail = executable.execute();
			stepResults.put(stepName, StepResult.pass(detail));
			return true;
		} catch (final Throwable error) {
			String screenshotPath = "not-captured";
			try {
				captureCheckpoint("failure_" + sanitizeFilePart(stepName));
				screenshotPath = evidenceDir.resolve("failure_" + sanitizeFilePart(stepName) + ".png").toString();
			} catch (final IOException ignored) {
				// best-effort evidence capture
			}
			final String detail = "Error: " + error.getMessage() + " | Screenshot: " + screenshotPath;
			stepResults.put(stepName, StepResult.fail(detail));
			return false;
		}
	}

	private boolean blockStep(final String stepName, final String reason) {
		stepResults.put(stepName, StepResult.fail(reason));
		return false;
	}

	private Path writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test").append('\n');
		report.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n').append('\n');

		for (final String field : orderedReportFields) {
			final StepResult result = stepResults.get(field);
			if (result == null) {
				report.append(field).append(": FAIL (missing result)").append('\n');
				continue;
			}

			report.append(field).append(": ").append(result.pass ? "PASS" : "FAIL");
			if (result.detail != null && !result.detail.trim().isEmpty()) {
				report.append(" - ").append(result.detail);
			}
			report.append('\n');
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, report.toString());
		System.out.println(report);
		return reportPath;
	}

	private String sanitizeFilePart(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}

	private String envOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder result = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				result.append(", \"'\", ");
			}
			result.append("'").append(parts[i]).append("'");
		}
		result.append(")");
		return result.toString();
	}

	private void sleepSilently(final long milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepExecutable {
		String execute() throws Exception;
	}

	private static class StepResult {
		private final boolean pass;
		private final String detail;

		private StepResult(final boolean pass, final String detail) {
			this.pass = pass;
			this.detail = detail;
		}

		private static StepResult pass(final String detail) {
			return new StepResult(true, detail);
		}

		private static StepResult fail(final String detail) {
			return new StepResult(false, detail);
		}
	}
}
