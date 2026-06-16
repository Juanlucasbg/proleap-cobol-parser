package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
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
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String PASS = "PASS";
	private static final String FAIL = "FAIL";

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(20);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final List<String> evidence = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Path reportPath;

	private String expectedEmail;
	private String expectedUserName;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final boolean e2eEnabled = Boolean.parseBoolean(
				getConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"Enable this test with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true.",
				e2eEnabled);

		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
		evidenceDir = Path.of("target", "surefire-reports", "saleads-mi-negocio-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		reportPath = evidenceDir.resolve("saleads-mi-negocio-report.txt");

		expectedEmail = getConfig("saleads.expected.email", "SALEADS_EXPECTED_EMAIL",
				"juanlucasbarbiergarzon@gmail.com");
		expectedUserName = getConfig("saleads.expected.username", "SALEADS_EXPECTED_USERNAME", "");

		final ChromeOptions options = new ChromeOptions();
		final String debuggerAddress = getConfig("saleads.debugger.address", "SALEADS_DEBUGGER_ADDRESS", "");

		if (!debuggerAddress.isBlank()) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		} else {
			final boolean headless = Boolean
					.parseBoolean(getConfig("saleads.headless", "SALEADS_HEADLESS", "true"));
			if (headless) {
				options.addArguments("--headless=new");
			}
			options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox",
					"--disable-dev-shm-usage");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		final String startUrl = getConfig("saleads.start.url", "SALEADS_START_URL", "");
		if (!startUrl.isBlank() && debuggerAddress.isBlank()) {
			driver.get(startUrl);
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			try {
				driver.quit();
			} catch (Exception ignored) {
				// no-op
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final boolean loginOk = runStep("Login", this::loginWithGoogleAndValidateDashboard);
		final boolean menuOk = runStep("Mi Negocio menu", loginOk, this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", menuOk, this::validateAgregarNegocioModal);
		final boolean administrarOk = runStep("Administrar Negocios view", menuOk, this::openAdministrarNegocios);
		runStep("Información General", administrarOk, this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", administrarOk, this::validateDetallesCuenta);
		runStep("Tus Negocios", administrarOk, this::validateTusNegocios);
		runStep("Términos y Condiciones", administrarOk, this::validateTerminosCondiciones);
		runStep("Política de Privacidad", administrarOk, this::validatePoliticaPrivacidad);

		writeFinalReport();
		assertAllStepsPassed();
	}

	private boolean runStep(final String name, final CheckedRunnable action) {
		try {
			action.run();
			report.put(name, StepResult.pass());
			return true;
		} catch (Throwable t) {
			report.put(name, StepResult.fail(t.getMessage()));
			return false;
		}
	}

	private boolean runStep(final String name, final boolean prerequisite, final CheckedRunnable action) {
		if (!prerequisite) {
			report.put(name, StepResult.fail("Skipped because a prerequisite step failed."));
			return false;
		}
		return runStep(name, action);
	}

	private void loginWithGoogleAndValidateDashboard() throws Exception {
		if (!isSidebarVisible()) {
			clickByVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google",
					"Iniciar sesion con Google", "Continuar con Google", "Acceder con Google", "Google"));
			waitForUiLoad();
			selectGoogleAccountIfVisible(expectedEmail);
		}

		new WebDriverWait(driver, Duration.ofSeconds(45)).until(d -> isSidebarVisible());
		assertAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio"));
		saveCheckpoint("01_dashboard_loaded");
	}

	private void openMiNegocioMenu() throws Exception {
		clickIfVisible(Arrays.asList("Negocio"));
		clickByVisibleText(Arrays.asList("Mi Negocio"));
		waitForUiLoad();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		saveCheckpoint("02_mi_negocio_menu_expanded");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickByVisibleText(Arrays.asList("Agregar Negocio"));
		waitForUiLoad();

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final WebElement nombreInput = findNombreNegocioInput();
		nombreInput.click();
		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatización");
		saveCheckpoint("03_agregar_negocio_modal");

		clickByVisibleText(Arrays.asList("Cancelar"));
		waitForUiLoad();
	}

	private void openAdministrarNegocios() throws Exception {
		if (!isVisibleText("Administrar Negocios", Duration.ofSeconds(4))) {
			clickIfVisible(Arrays.asList("Mi Negocio"));
		}
		clickByVisibleText(Arrays.asList("Administrar Negocios"));
		waitForUiLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertAnyVisibleText(Arrays.asList("Sección Legal", "Seccion Legal"));
		saveCheckpoint("04_administrar_negocios_view");
	}

	private void validateInformacionGeneral() {
		assertVisibleText("Información General");
		assertVisibleText(expectedEmail);
		assertAnyVisibleText(Arrays.asList("BUSINESS PLAN"));
		assertVisibleText("Cambiar Plan");

		final String infoText = getSectionText("Información General");
		final String compactText = normalize(infoText);
		if (!expectedUserName.isBlank()) {
			if (!compactText.contains(normalize(expectedUserName))) {
				throw new AssertionError("Expected user name not found in Información General.");
			}
		} else if (!containsPossibleName(compactText)) {
			throw new AssertionError(
					"Could not identify a visible user name in Información General. Provide SALEADS_EXPECTED_USERNAME.");
		}
	}

	private void validateDetallesCuenta() {
		assertAnyVisibleText(Arrays.asList("Detalles de la Cuenta", "Detalles de la cuenta"));
		assertAnyVisibleText(Arrays.asList("Cuenta creada"));
		assertAnyVisibleText(Arrays.asList("Estado activo", "Estado Activo"));
		assertAnyVisibleText(Arrays.asList("Idioma seleccionado", "Idioma Seleccionado"));
	}

	private void validateTusNegocios() {
		assertAnyVisibleText(Arrays.asList("Tus Negocios"));
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		final String negociosSectionText = getSectionText("Tus Negocios");
		if (normalize(negociosSectionText).length() < 25) {
			throw new AssertionError("Business list content is not visible in section 'Tus Negocios'.");
		}
	}

	private void validateTerminosCondiciones() throws Exception {
		termsUrl = openLegalLinkAndValidate("Términos y Condiciones", "Términos y Condiciones",
				"08_terminos_condiciones");
	}

	private void validatePoliticaPrivacidad() throws Exception {
		privacyUrl = openLegalLinkAndValidate("Política de Privacidad", "Política de Privacidad",
				"09_politica_privacidad");
	}

	private String openLegalLinkAndValidate(final String linkText, final String expectedHeading,
			final String checkpointName) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> previousHandles = driver.getWindowHandles();
		final String previousUrl = driver.getCurrentUrl();

		clickByVisibleText(Arrays.asList(linkText));
		waitForUiLoad();

		final String newHandle = waitForNewWindow(previousHandles, Duration.ofSeconds(8));
		final boolean openedInNewTab = newHandle != null;

		if (openedInNewTab) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		}

		assertVisibleText(expectedHeading);
		final String bodyText = normalize(driver.findElement(By.tagName("body")).getText());
		if (bodyText.replace(normalize(expectedHeading), "").trim().length() < 80) {
			throw new AssertionError("Legal content text is not sufficiently visible for '" + expectedHeading + "'.");
		}

		final String finalUrl = driver.getCurrentUrl();
		saveCheckpoint(checkpointName);

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else if (!previousUrl.equals(driver.getCurrentUrl())) {
			driver.navigate().back();
		}

		waitForUiLoad();
		return finalUrl;
	}

	private void selectGoogleAccountIfVisible(final String email) {
		for (int i = 0; i < 3; i++) {
			if (clickIfVisible(Arrays.asList(email), Duration.ofSeconds(4))) {
				waitForUiLoad();
				return;
			}

			for (String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (clickIfVisible(Arrays.asList(email), Duration.ofSeconds(2))) {
					waitForUiLoad();
					return;
				}
			}
			sleep(500);
		}
	}

	private WebElement findNombreNegocioInput() {
		final By explicitLabelInput = By.xpath(
				"//*[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]");
		final By placeholderInput = By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]");

		for (By locator : Arrays.asList(explicitLabelInput, placeholderInput, By.xpath("//input"))) {
			for (WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed() && element.isEnabled()) {
					return element;
				}
			}
		}
		throw new NoSuchElementException("Could not find 'Nombre del Negocio' input field.");
	}

	private void assertAllStepsPassed() {
		final List<String> failedSteps = new ArrayList<>();
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!PASS.equals(entry.getValue().status)) {
				failedSteps.add(entry.getKey() + " (" + entry.getValue().details + ")");
			}
		}

		if (!failedSteps.isEmpty()) {
			throw new AssertionError("One or more workflow validations failed. Report: " + reportPath + " | "
					+ String.join("; ", failedSteps));
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Workflow Report").append(System.lineSeparator());
		sb.append("Generated: ").append(LocalDateTime.now()).append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("Final PASS/FAIL by validation block").append(System.lineSeparator());

		final List<String> orderedFields = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Términos y Condiciones", "Política de Privacidad");

		for (String field : orderedFields) {
			final StepResult result = report.getOrDefault(field,
					StepResult.fail("Not executed due to earlier failure."));
			sb.append("- ").append(field).append(": ").append(result.status);
			if (!result.details.isBlank()) {
				sb.append(" (").append(result.details).append(")");
			}
			sb.append(System.lineSeparator());
		}

		sb.append(System.lineSeparator());
		sb.append("Captured URLs").append(System.lineSeparator());
		sb.append("- Términos y Condiciones URL: ").append(termsUrl).append(System.lineSeparator());
		sb.append("- Política de Privacidad URL: ").append(privacyUrl).append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("Screenshots").append(System.lineSeparator());
		for (String screenshot : evidence) {
			sb.append("- ").append(screenshot).append(System.lineSeparator());
		}

		Files.writeString(reportPath, sb.toString(), StandardCharsets.UTF_8);
	}

	private void saveCheckpoint(final String label) throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("HHmmss").format(LocalDateTime.now());
		final String fileName = sanitizeFileName(label + "_" + timestamp) + ".png";
		final Path screenshotPath = evidenceDir.resolve(fileName);
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
		evidence.add(screenshotPath.toString());
	}

	private String waitForNewWindow(final Set<String> previousHandles, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(d -> {
				final Set<String> handles = d.getWindowHandles();
				if (handles.size() <= previousHandles.size()) {
					return null;
				}
				for (String handle : handles) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (TimeoutException e) {
			return null;
		}
	}

	private boolean isSidebarVisible() {
		final List<By> sidebarLocators = Arrays.asList(By.xpath("//aside"),
				By.xpath("//nav[contains(@class, 'sidebar') or contains(@id, 'sidebar')]"), By.xpath("//nav"));
		for (By locator : sidebarLocators) {
			for (WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed() && normalize(element.getText()).contains("negocio")) {
					return true;
				}
			}
		}

		return isVisibleText("Mi Negocio", Duration.ofSeconds(2)) || isVisibleText("Negocio", Duration.ofSeconds(2));
	}

	private void clickByVisibleText(final List<String> labels) {
		final WebElement element = findClickableByText(labels, DEFAULT_WAIT);
		clickElement(element);
		waitForUiLoad();
	}

	private boolean clickIfVisible(final List<String> labels) {
		return clickIfVisible(labels, Duration.ofSeconds(3));
	}

	private boolean clickIfVisible(final List<String> labels, final Duration timeout) {
		try {
			final WebElement element = findClickableByText(labels, timeout);
			clickElement(element);
			waitForUiLoad();
			return true;
		} catch (RuntimeException ex) {
			return false;
		}
	}

	private void clickElement(final WebElement element) {
		try {
			element.click();
		} catch (Exception ex) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private WebElement findClickableByText(final List<String> labels, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(d -> {
			for (String label : labels) {
				for (By locator : clickableLocators(label)) {
					for (WebElement candidate : d.findElements(locator)) {
						if (candidate.isDisplayed() && candidate.isEnabled()) {
							return candidate;
						}
					}
				}
			}
			return null;
		});
	}

	private List<By> clickableLocators(final String text) {
		final String literal = xpathLiteral(text);
		return Arrays.asList(
				By.xpath("//*[normalize-space()=" + literal
						+ "]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"),
				By.xpath("//*[contains(normalize-space(), " + literal
						+ ")]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"),
				By.xpath("//button[normalize-space()=" + literal + "]"),
				By.xpath("//a[normalize-space()=" + literal + "]"),
				By.xpath("//*[normalize-space()=" + literal + "]"));
	}

	private void assertVisibleText(final String text) {
		if (!isVisibleText(text, DEFAULT_WAIT)) {
			throw new AssertionError("Expected visible text not found: " + text);
		}
	}

	private void assertAnyVisibleText(final List<String> texts) {
		for (String text : texts) {
			if (isVisibleText(text, DEFAULT_WAIT)) {
				return;
			}
		}
		throw new AssertionError("None of the expected visible texts were found: " + texts);
	}

	private boolean isVisibleText(final String text, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(d -> {
				for (By locator : textLocators(text)) {
					for (WebElement element : d.findElements(locator)) {
						if (element.isDisplayed()) {
							return true;
						}
					}
				}
				return false;
			});
		} catch (TimeoutException ex) {
			return false;
		}
	}

	private List<By> textLocators(final String text) {
		final String literal = xpathLiteral(text);
		return Arrays.asList(By.xpath("//*[normalize-space()=" + literal + "]"),
				By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
	}

	private String getSectionText(final String sectionHeading) {
		final String headingLiteral = xpathLiteral(sectionHeading);
		final List<By> locators = Arrays.asList(
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(), "
						+ headingLiteral + ")]/ancestor::*[self::section or self::div][1]"),
				By.xpath("//*[contains(normalize-space(), " + headingLiteral + ")]/ancestor::section[1]"));

		for (By locator : locators) {
			for (WebElement section : driver.findElements(locator)) {
				if (section.isDisplayed()) {
					return section.getText();
				}
			}
		}

		return driver.findElement(By.tagName("body")).getText();
	}

	private boolean containsPossibleName(final String normalizedText) {
		final String cleaned = normalizedText.replace(normalize(expectedEmail), " ")
				.replace("informacion general", " ").replace("business plan", " ").replace("cambiar plan", " ");

		final String[] parts = cleaned.trim().split("\\s+");
		int words = 0;
		for (String part : parts) {
			if (part.length() > 2 && !EMAIL_PATTERN.matcher(part).matches()) {
				words++;
				if (words >= 2) {
					return true;
				}
			}
		}
		return false;
	}

	private String normalize(final String value) {
		return value == null ? "" : value.toLowerCase().replaceAll("\\s+", " ").trim();
	}

	private String sanitizeFileName(final String text) {
		return text.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private void waitForUiLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (Exception ignored) {
			// no-op
		}
		sleep(400);
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String getConfig(final String propertyKey, final String envKey, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String character = String.valueOf(chars[i]);
			if (i > 0) {
				sb.append(", ");
			}
			if ("'".equals(character)) {
				sb.append("\"'\"");
			} else {
				sb.append("'").append(character).append("'");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details == null ? "" : details;
		}

		private static StepResult pass() {
			return new StepResult(PASS, "");
		}

		private static StepResult fail(final String details) {
			return new StepResult(FAIL, details == null ? "" : details.replace('\n', ' '));
		}
	}
}
