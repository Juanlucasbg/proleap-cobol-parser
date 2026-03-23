package io.proleap.e2e;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end UI workflow for SaleADS "Mi Negocio".
 *
 * <p>
 * Runtime configuration:
 * <ul>
 * <li>SALEADS_LOGIN_URL: required login page URL for the current environment</li>
 * <li>SALEADS_HEADLESS: optional, defaults to true</li>
 * <li>SALEADS_WAIT_SECONDS: optional, defaults to 30</li>
 * </ul>
 * </p>
 */
public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	private final Map<String, StepResult> report = new LinkedHashMap<>();

	private String terminosUrl = "N/A";
	private String privacidadUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("SALEADS_LOGIN_URL must be set to run this workflow.", loginUrl != null && !loginUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (toBoolean(System.getenv("SALEADS_HEADLESS"), true)) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(getInt("SALEADS_WAIT_SECONDS", 30)));
		wait.pollingEvery(Duration.ofMillis(250));

		final String runFolder = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Path.of("target", "saleads-evidence", runFolder);
		Files.createDirectories(evidenceDir);

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final String finalReport = buildFinalReport();
		final List<String> failedSteps = new ArrayList<>();

		for (final String step : REPORT_FIELDS) {
			final StepResult result = report.get(step);
			if (result == null || !result.passed) {
				failedSteps.add(step);
			}
		}

		assertTrue("Some validation steps failed: " + failedSteps + System.lineSeparator() + finalReport, failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Login con Google");
		waitForUiToLoad();

		selectGoogleAccountIfShown();
		waitForUiToLoad();

		final boolean mainInterfaceVisible = waitForAnyVisibleText(Duration.ofSeconds(45), "Dashboard", "Negocio", "Mi Negocio",
				"Administrar Negocios");
		final boolean sidebarVisible = hasVisibleElement(By.cssSelector("aside"))
				|| hasVisibleElement(By.xpath("//*[normalize-space()='Negocio' or normalize-space()='Mi Negocio']"));

		assertTrue("Main application interface should be visible after login.", mainInterfaceVisible);
		assertTrue("Left sidebar navigation should be visible after login.", sidebarVisible);

		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickIfVisible("Negocio");
		clickByVisibleText("Mi Negocio");

		final boolean submenuExpanded = waitForAnyVisibleText(Duration.ofSeconds(15), "Agregar Negocio", "Administrar Negocios");
		assertTrue("Mi Negocio submenu should expand.", submenuExpanded);
		assertTrue("\"Agregar Negocio\" should be visible.", isVisibleText("Agregar Negocio"));
		assertTrue("\"Administrar Negocios\" should be visible.", isVisibleText("Administrar Negocios"));

		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		assertVisibleText("Crear Nuevo Negocio", Duration.ofSeconds(20));
		assertTrue("Input field 'Nombre del Negocio' must exist.", hasVisibleElement(By
				.xpath("//label[normalize-space()='Nombre del Negocio']/following::input[1] | //input[@placeholder='Nombre del Negocio']")));
		assertTrue("'Tienes 2 de 3 negocios' must be visible.", isVisibleText("Tienes 2 de 3 negocios"));
		assertTrue("'Cancelar' button must be present.", hasVisibleElement(By.xpath("//button[normalize-space()='Cancelar']")));
		assertTrue("'Crear Negocio' button must be present.", hasVisibleElement(By.xpath("//button[normalize-space()='Crear Negocio']")));

		captureScreenshot("03_agregar_negocio_modal");

		typeIfVisible(By.xpath(
				"//label[normalize-space()='Nombre del Negocio']/following::input[1] | //input[@placeholder='Nombre del Negocio']"),
				"Negocio Prueba Automatización");
		clickIfVisible("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isVisibleText("Administrar Negocios")) {
			clickIfVisible("Negocio");
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertVisibleText("Información General", Duration.ofSeconds(20));
		assertTrue("'Detalles de la Cuenta' section should exist.", isVisibleText("Detalles de la Cuenta"));
		assertTrue("'Tus Negocios' section should exist.", isVisibleText("Tus Negocios"));
		assertTrue("'Sección Legal' section should exist.", isVisibleText("Sección Legal"));

		captureScreenshot("04_administrar_negocios_view");
	}

	private void stepValidateInformacionGeneral() {
		final String section = extractSectionText("Información General", "Detalles de la Cuenta");
		final Pattern emailPattern = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

		assertTrue("User name should be visible in 'Información General'.", hasLikelyUserName(section));
		assertTrue("User email should be visible in 'Información General'.",
				emailPattern.matcher(section).find() || section.contains(GOOGLE_ACCOUNT_EMAIL));
		assertTrue("'BUSINESS PLAN' text should be visible.", section.contains("BUSINESS PLAN") || isVisibleText("BUSINESS PLAN"));
		assertTrue("'Cambiar Plan' button should be visible.", section.contains("Cambiar Plan") || isVisibleText("Cambiar Plan"));
	}

	private void stepValidateDetallesCuenta() {
		final String section = extractSectionText("Detalles de la Cuenta", "Tus Negocios");

		assertTrue("'Cuenta creada' should be visible.", containsAllWords(section, "cuenta", "creada"));
		assertTrue("'Estado activo' should be visible.", containsAllWords(section, "estado", "activo"));
		assertTrue("'Idioma seleccionado' should be visible.", containsAllWords(section, "idioma", "seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final String section = extractSectionText("Tus Negocios", "Sección Legal");

		assertTrue("Business list should be visible.", hasBusinessList(section));
		assertTrue("'Agregar Negocio' button should exist.", section.contains("Agregar Negocio") || isVisibleText("Agregar Negocio"));
		assertTrue("'Tienes 2 de 3 negocios' should be visible.",
				section.contains("Tienes 2 de 3 negocios") || isVisibleText("Tienes 2 de 3 negocios"));
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		terminosUrl = openLegalLinkAndValidate("Términos y Condiciones", "Términos y Condiciones", "05_terminos_condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		privacidadUrl = openLegalLinkAndValidate("Política de Privacidad", "Política de Privacidad", "06_politica_privacidad");
	}

	private String openLegalLinkAndValidate(final String linkText, final String headingText, final String screenshotName)
			throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> previousHandles = driver.getWindowHandles();
		final String urlBeforeClick = driver.getCurrentUrl();

		clickByVisibleText(linkText);

		boolean openedInNewTab = false;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> d.getWindowHandles().size() > previousHandles.size());
			openedInNewTab = true;
		} catch (final TimeoutException ignored) {
			openedInNewTab = false;
		}

		if (openedInNewTab) {
			for (final String handle : driver.getWindowHandles()) {
				if (!previousHandles.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		} else {
			new WebDriverWait(driver, Duration.ofSeconds(15)).until(ExpectedConditions.not(ExpectedConditions.urlToBe(urlBeforeClick)));
		}

		waitForUiToLoad();
		assertVisibleText(headingText, Duration.ofSeconds(20));

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal page must contain substantial visible content.", bodyText != null && bodyText.trim().length() > 200);

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		return finalUrl;
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, new StepResult(true, "PASS"));
		} catch (final Throwable t) {
			report.put(stepName, new StepResult(false, summarizeException(t)));
			captureScreenshotQuietly("failed_" + slugify(stepName));
		}
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		for (final String field : REPORT_FIELDS) {
			report.computeIfAbsent(field, key -> new StepResult(false, "NOT EXECUTED"));
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), buildFinalReport());
	}

	private String buildFinalReport() {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio - Final Report").append(System.lineSeparator());
		sb.append("Evidence directory: ").append(evidenceDir == null ? "N/A" : evidenceDir.toAbsolutePath())
				.append(System.lineSeparator()).append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			if (result == null) {
				sb.append(field).append(": FAIL (NOT EXECUTED)");
			} else {
				sb.append(field).append(": ").append(result.passed ? "PASS" : "FAIL");
				if (!result.passed && result.details != null && !result.details.isBlank()) {
					sb.append(" - ").append(result.details);
				}
			}
			sb.append(System.lineSeparator());
		}

		sb.append(System.lineSeparator()).append("Final URL - Términos y Condiciones: ").append(terminosUrl)
				.append(System.lineSeparator());
		sb.append("Final URL - Política de Privacidad: ").append(privacidadUrl).append(System.lineSeparator());
		return sb.toString();
	}

	private void waitForUiToLoad() {
		wait.until(driverInstance -> "complete"
				.equals(((JavascriptExecutor) driverInstance).executeScript("return document.readyState")));

		try {
			Thread.sleep(500L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean waitForAnyVisibleText(final Duration timeout, final String... candidateTexts) {
		try {
			new WebDriverWait(driver, timeout).until(d -> {
				for (final String text : candidateTexts) {
					if (isVisibleText(text)) {
						return true;
					}
				}
				return false;
			});
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void clickByVisibleText(final String... texts) {
		for (final String text : texts) {
			if (tryClickText(text)) {
				waitForUiToLoad();
				return;
			}
		}

		throw new IllegalStateException("Could not click any element by visible text: " + Arrays.toString(texts));
	}

	private void clickIfVisible(final String text) {
		tryClickText(text);
	}

	private boolean tryClickText(final String text) {
		final List<By> selectors = Arrays.asList(
				By.xpath("//button[normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//a[normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//*[@role='button' and normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//*[self::span or self::div or self::p][normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]"));

		for (final By selector : selectors) {
			final List<WebElement> candidates = driver.findElements(selector);
			for (final WebElement candidate : candidates) {
				if (!candidate.isDisplayed()) {
					continue;
				}

				try {
					scrollIntoView(candidate);
					wait.until(ExpectedConditions.elementToBeClickable(candidate));
					candidate.click();
					return true;
				} catch (final Exception clickException) {
					try {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", candidate);
						return true;
					} catch (final Exception ignored) {
						// try next candidate
					}
				}
			}
		}

		return false;
	}

	private void selectGoogleAccountIfShown() {
		if (isVisibleText(GOOGLE_ACCOUNT_EMAIL)) {
			clickByVisibleText(GOOGLE_ACCOUNT_EMAIL);
			return;
		}

		waitForAnyVisibleText(Duration.ofSeconds(5), "Choose an account", "Elige una cuenta");
		if (isVisibleText(GOOGLE_ACCOUNT_EMAIL)) {
			clickByVisibleText(GOOGLE_ACCOUNT_EMAIL);
		}
	}

	private boolean isVisibleText(final String text) {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void assertVisibleText(final String text, final Duration timeout) {
		new WebDriverWait(driver, timeout).until(d -> isVisibleText(text));
	}

	private boolean hasVisibleElement(final By selector) {
		for (final WebElement element : driver.findElements(selector)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void typeIfVisible(final By selector, final String value) {
		for (final WebElement input : driver.findElements(selector)) {
			if (input.isDisplayed()) {
				input.clear();
				input.sendKeys(value);
				return;
			}
		}
	}

	private String extractSectionText(final String startHeading, final String nextHeading) {
		final String fullText = driver.findElement(By.tagName("body")).getText();
		final int start = indexOfIgnoreCase(fullText, startHeading);

		if (start < 0) {
			return "";
		}

		int end = fullText.length();
		if (nextHeading != null && !nextHeading.isBlank()) {
			final int next = indexOfIgnoreCase(fullText, nextHeading);
			if (next > start) {
				end = next;
			}
		}

		return fullText.substring(start, end).trim();
	}

	private boolean hasLikelyUserName(final String sectionText) {
		for (final String line : sectionText.split("\\R")) {
			final String value = line.trim();
			if (value.isEmpty()) {
				continue;
			}
			if ("Información General".equalsIgnoreCase(value) || "BUSINESS PLAN".equalsIgnoreCase(value)
					|| "Cambiar Plan".equalsIgnoreCase(value) || value.contains("@")) {
				continue;
			}
			if (value.length() >= 3 && !value.matches(".*\\d.*")) {
				return true;
			}
		}
		return false;
	}

	private boolean hasBusinessList(final String sectionText) {
		int nonEmptyLines = 0;
		for (final String line : sectionText.split("\\R")) {
			if (!line.trim().isEmpty()) {
				nonEmptyLines++;
			}
		}
		return nonEmptyLines >= 4;
	}

	private boolean containsAllWords(final String text, final String... words) {
		final String lower = text.toLowerCase(Locale.ROOT);
		for (final String word : words) {
			if (!lower.contains(word.toLowerCase(Locale.ROOT))) {
				return false;
			}
		}
		return true;
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final Path target = evidenceDir.resolve(slugify(checkpointName) + ".png");
		Files.copy(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath(), target,
				StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureScreenshotQuietly(final String checkpointName) {
		try {
			captureScreenshot(checkpointName);
		} catch (final Exception ignored) {
			// intentionally ignored
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private int indexOfIgnoreCase(final String source, final String target) {
		return source.toLowerCase(Locale.ROOT).indexOf(target.toLowerCase(Locale.ROOT));
	}

	private String summarizeException(final Throwable t) {
		final String message = t.getMessage();
		if (message == null || message.isBlank()) {
			return t.getClass().getSimpleName();
		}

		final String oneLine = message.replaceAll("\\s+", " ").trim();
		return oneLine.length() > 200 ? oneLine.substring(0, 200) + "..." : oneLine;
	}

	private String slugify(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			sb.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				sb.append(", \"'\", ");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	private int getInt(final String envKey, final int defaultValue) {
		try {
			final String value = System.getenv(envKey);
			return value == null ? defaultValue : Integer.parseInt(value.trim());
		} catch (final NumberFormatException ignored) {
			return defaultValue;
		}
	}

	private boolean toBoolean(final String value, final boolean defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()) || "yes".equalsIgnoreCase(value.trim());
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}
	}
}
