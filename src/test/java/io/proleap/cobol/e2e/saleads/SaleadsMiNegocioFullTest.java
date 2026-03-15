package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private static final String TARGET_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final Duration UI_TIMEOUT = Duration.ofSeconds(30);

	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> stepResults = new LinkedHashMap<>();

	private Path artifactDirectory;
	private String appWindowHandle;
	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws IOException {
		for (final String field : REPORT_FIELDS) {
			stepResults.put(field, "NOT_EXECUTED");
		}

		final boolean enabled = Boolean.parseBoolean(readSetting("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"This E2E is opt-in. Set -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true to execute it.",
				enabled);

		artifactDirectory = createArtifactDirectory();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");

		final boolean headless = Boolean.parseBoolean(readSetting("saleads.headless", "SALEADS_HEADLESS", "false"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, UI_TIMEOUT);
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String loginUrl = readSetting("saleads.login.url", "SALEADS_LOGIN_URL", "");
		Assume.assumeTrue(
				"Missing login URL. Set -Dsaleads.login.url=<env-login-url> or SALEADS_LOGIN_URL environment variable.",
				loginUrl != null && !loginUrl.isBlank());

		runStep("Login", () -> doLoginStep(loginUrl));
		runStep("Mi Negocio menu", this::doMiNegocioMenuStep);
		runStep("Agregar Negocio modal", this::doAgregarNegocioModalStep);
		runStep("Administrar Negocios view", this::doAdministrarNegociosStep);
		runStep("Información General", this::doInformacionGeneralStep);
		runStep("Detalles de la Cuenta", this::doDetallesCuentaStep);
		runStep("Tus Negocios", this::doTusNegociosStep);
		runStep("Términos y Condiciones", () -> doLegalDocumentStep("Términos y Condiciones", "08_terminos.png"));
		runStep("Política de Privacidad", () -> doLegalDocumentStep("Política de Privacidad", "09_privacidad.png"));

		if (!failures.isEmpty()) {
			throw new AssertionError("SaleADS Mi Negocio workflow failed:\n - " + String.join("\n - ", failures)
					+ "\nArtifacts: " + artifactDirectory.toAbsolutePath());
		}
	}

	@After
	public void tearDown() throws IOException {
		if (driver != null) {
			driver.quit();
		}
		writeFinalReport();
	}

	private void doLoginStep(final String loginUrl) throws Exception {
		driver.get(loginUrl);
		waitForUiLoad();

		clickVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		trySelectGoogleAccount(TARGET_ACCOUNT_EMAIL);

		waitForMainApplicationLoaded();
		captureScreenshot("01_dashboard_loaded.png");
	}

	private void doMiNegocioMenuStep() throws Exception {
		switchToApplicationWindow();
		clickVisibleText("Negocio");
		clickVisibleText("Mi Negocio");

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02_mi_negocio_menu_expanded.png");
	}

	private void doAgregarNegocioModalStep() throws Exception {
		switchToApplicationWindow();
		clickVisibleText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		captureScreenshot("03_agregar_negocio_modal.png");

		typeOnFirstVisibleInput("Negocio Prueba Automatización");
		clickVisibleText("Cancelar");
	}

	private void doAdministrarNegociosStep() throws Exception {
		switchToApplicationWindow();
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(2))) {
			clickVisibleText("Mi Negocio");
		}
		clickVisibleText("Administrar Negocios");

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");

		captureScreenshot("04_administrar_negocios_view.png");
	}

	private void doInformacionGeneralStep() {
		assertVisibleText("Información General");
		assertAnyVisibleTextMatching(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String sectionText = readSectionText("Información General");
		final String emailPattern = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}";
		final String reduced = sectionText.replace("Información General", "").replace("BUSINESS PLAN", "")
				.replace("Cambiar Plan", "").replaceAll(emailPattern, "").trim();
		assertTrue("User name is visible.", reduced.length() > 2);
	}

	private void doDetallesCuentaStep() {
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void doTusNegociosStep() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		final String sectionText = readSectionText("Tus Negocios");
		assertTrue("Business list is visible.", sectionText.replace("Tus Negocios", "").trim().length() > 0);
	}

	private void doLegalDocumentStep(final String linkText, final String screenshotName) throws Exception {
		switchToApplicationWindow();
		final String originHandle = driver.getWindowHandle();
		final String originUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickVisibleText(linkText);
		waitForUiLoad();

		String targetHandle = originHandle;
		try {
			targetHandle = new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> {
				final Set<String> currentHandles = d.getWindowHandles();
				if (currentHandles.size() > handlesBefore.size()) {
					for (final String handle : currentHandles) {
						if (!handlesBefore.contains(handle)) {
							return handle;
						}
					}
				}

				if (!d.getCurrentUrl().equals(originUrl)) {
					return d.getWindowHandle();
				}

				return null;
			});
		} catch (final TimeoutException ignored) {
			// Keep current tab if no explicit tab switch is detected.
		}

		driver.switchTo().window(targetHandle);
		waitForUiLoad();
		assertVisibleText(linkText);

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal content text is visible.", bodyText != null && bodyText.trim().length() > 120);

		captureScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (!driver.getWindowHandle().equals(originHandle)) {
			driver.close();
			driver.switchTo().window(originHandle);
			waitForUiLoad();
		} else if (!originUrl.equals(driver.getCurrentUrl())) {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String stepName, final CheckedStep step) {
		try {
			step.run();
			stepResults.put(stepName, "PASS");
		} catch (final Throwable throwable) {
			final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getMessage();
			stepResults.put(stepName, "FAIL");
			failures.add(stepName + ": " + message);
			captureScreenshot("error_" + sanitizeFileName(stepName) + ".png");
		}
	}

	private void waitForMainApplicationLoaded() throws Exception {
		waitForUiLoad();
		new WebDriverWait(driver, Duration.ofSeconds(90))
				.until(d -> isTextVisible("Negocio", Duration.ofSeconds(1)) || isTextVisible("Mi Negocio", Duration.ofSeconds(1))
						|| !d.findElements(By.xpath("//aside")).isEmpty());
		appWindowHandle = driver.getWindowHandle();
	}

	private void trySelectGoogleAccount(final String email) throws Exception {
		final List<String> handles = new ArrayList<>(driver.getWindowHandles());
		driver.switchTo().window(handles.get(handles.size() - 1));
		waitForUiLoad();

		if (isTextVisible(email, Duration.ofSeconds(15))) {
			clickVisibleText(email);
			waitForUiLoad();
		}
	}

	private void switchToApplicationWindow() {
		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private void clickVisibleText(final String... preferredTexts) throws Exception {
		for (final String text : preferredTexts) {
			final Duration timeout = Duration.ofSeconds(8);
			final WebDriverWait localWait = new WebDriverWait(driver, timeout);
			try {
				final WebElement element = localWait.until(d -> firstVisibleClickableElementByText(text));
				clickAndWait(element);
				return;
			} catch (final TimeoutException ignored) {
				// Try next text variant.
			}
		}

		throw new NoSuchElementException("Could not find visible clickable element with text: "
				+ Arrays.toString(preferredTexts));
	}

	private WebElement firstVisibleClickableElementByText(final String text) {
		final List<By> locators = Arrays.asList(
				By.xpath("//button[contains(normalize-space(.), " + xpathLiteral(text) + ")]"),
				By.xpath("//a[contains(normalize-space(.), " + xpathLiteral(text) + ")]"),
				By.xpath("//*[@role='button' and contains(normalize-space(.), " + xpathLiteral(text) + ")]"),
				By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]/ancestor::button[1]"),
				By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]/ancestor::a[1]"));

		for (final By locator : locators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element != null && element.isDisplayed() && element.isEnabled()) {
					return element;
				}
			}
		}

		return null;
	}

	private void clickAndWait(final WebElement element) throws Exception {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiLoad();
	}

	private void typeOnFirstVisibleInput(final String value) {
		final List<By> locators = Arrays.asList(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[not(@type='hidden')]"));

		for (final By locator : locators) {
			for (final WebElement input : driver.findElements(locator)) {
				if (input.isDisplayed() && input.isEnabled()) {
					input.clear();
					input.sendKeys(value);
					return;
				}
			}
		}
	}

	private void assertVisibleText(final String text) {
		final boolean visible = isTextVisible(text, UI_TIMEOUT);
		assertTrue("Expected visible text: " + text, visible);
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			final WebDriverWait localWait = new WebDriverWait(driver, timeout);
			return localWait.until(d -> {
				final List<WebElement> elements = d
						.findElements(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]"));
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return true;
					}
				}
				return false;
			});
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void assertAnyVisibleTextMatching(final Pattern pattern) {
		final String allText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected a visible value matching " + pattern.pattern(), pattern.matcher(allText).find());
	}

	private String readSectionText(final String headingText) {
		final List<WebElement> headings = driver
				.findElements(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(headingText) + ")]"));
		for (final WebElement heading : headings) {
			if (!heading.isDisplayed()) {
				continue;
			}

			final List<WebElement> containers = heading.findElements(By.xpath("ancestor::section[1]"));
			if (!containers.isEmpty()) {
				return containers.get(0).getText();
			}

			final List<WebElement> fallback = heading.findElements(By.xpath("ancestor::div[1]"));
			if (!fallback.isEmpty()) {
				return fallback.get(0).getText();
			}
		}

		return driver.findElement(By.tagName("body")).getText();
	}

	private void waitForUiLoad() throws Exception {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		Thread.sleep(450L);
	}

	private void captureScreenshot(final String fileName) {
		if (driver == null || artifactDirectory == null) {
			return;
		}

		try {
			final Path targetFile = artifactDirectory.resolve(fileName);
			final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception ignored) {
			// Best effort evidence capture.
		}
	}

	private void writeFinalReport() throws IOException {
		if (artifactDirectory == null) {
			return;
		}

		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("timestamp=" + LocalDateTime.now());
		lines.add("");
		lines.add("STEP RESULTS");
		for (final String reportField : REPORT_FIELDS) {
			lines.add(reportField + ": " + stepResults.getOrDefault(reportField, "NOT_EXECUTED"));
		}
		lines.add("");
		lines.add("LEGAL URLS");
		lines.add("Términos y Condiciones URL: " + legalUrls.getOrDefault("Términos y Condiciones", "N/A"));
		lines.add("Política de Privacidad URL: " + legalUrls.getOrDefault("Política de Privacidad", "N/A"));
		lines.add("");
		lines.add("FAILURES");
		if (failures.isEmpty()) {
			lines.add("None");
		} else {
			lines.addAll(failures);
		}

		Files.write(artifactDirectory.resolve("final_report.txt"), lines, StandardCharsets.UTF_8);
	}

	private Path createArtifactDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		final Path directory = Path.of("target", "saleads-artifacts", "saleads_mi_negocio_full_test_" + timestamp);
		Files.createDirectories(directory);
		return directory;
	}

	private String readSetting(final String propertyName, final String envName, final String defaultValue) {
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

	private String sanitizeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "_");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}
}
