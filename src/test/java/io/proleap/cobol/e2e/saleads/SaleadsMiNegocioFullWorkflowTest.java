package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.text.Normalizer;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
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
 * End-to-end workflow test for SaleADS "Mi Negocio" module.
 *
 * <p>
 * Runtime configuration:
 * <ul>
 * <li>-Dsaleads.login.url=https://&lt;env-login-url&gt; (or env SALEADS_LOGIN_URL)</li>
 * <li>-Dsaleads.headless=true|false (default false)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(10);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Información General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "Términos y Condiciones";
	private static final String POLITICA = "Política de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();

		if (Boolean.parseBoolean(System.getProperty("saleads.headless", "false"))) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDir = createEvidenceDirectory();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));

		Assert.assertNotNull(
				"Missing login URL. Provide -Dsaleads.login.url or SALEADS_LOGIN_URL for the target environment login page.",
				loginUrl);

		driver.get(loginUrl);
		waitForUiToLoad();

		executeStep(LOGIN, this::stepLoginWithGoogle);
		executeStep(MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		executeStep(AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		executeStep(ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		executeStep(INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		executeStep(DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		executeStep(TUS_NEGOCIOS, this::stepValidateTusNegocios);
		executeStep(TERMINOS, () -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "terminos"));
		executeStep(POLITICA, () -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "politica"));

		writeFinalReport();
		assertAllStepsPassed();
	}

	private void stepLoginWithGoogle() throws IOException {
		clickByAnyVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		maybeSelectGoogleAccount("juanlucasbarbiergarzon@gmail.com");

		waitForAnyVisibleText("Negocio", "Mi Negocio");
		assertAnyVisible("Negocio", "Mi Negocio");
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		waitForAnyVisibleText("Negocio", "Mi Negocio");
		clickByAnyVisibleText("Mi Negocio");
		waitForAnyVisibleText("Agregar Negocio", "Administrar Negocios");

		assertAnyVisible("Agregar Negocio");
		assertAnyVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Crear Nuevo Negocio");

		assertAnyVisible("Crear Nuevo Negocio");
		assertTrue("Expected input 'Nombre del Negocio' was not visible.", hasVisibleInputNombreNegocio());
		assertAnyVisible("Tienes 2 de 3 negocios");
		assertAnyVisible("Cancelar");
		assertAnyVisible("Crear Negocio");

		final WebElement input = firstVisibleElement(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio') or contains(@name,'nombre')]"),
				SHORT_TIMEOUT);
		input.clear();
		input.sendKeys("Negocio Prueba Automatizacion");
		takeScreenshot("03-agregar-negocio-modal");

		clickByAnyVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioIfCollapsed();
		clickByAnyVisibleText("Administrar Negocios");
		waitForAnyVisibleText("Información General", "Informacion General");
		waitForAnyVisibleText("Detalles de la Cuenta");
		waitForAnyVisibleText("Tus Negocios");
		waitForAnyVisibleText("Sección Legal", "Seccion Legal");

		assertAnyVisible("Información General", "Informacion General");
		assertAnyVisible("Detalles de la Cuenta");
		assertAnyVisible("Tus Negocios");
		assertAnyVisible("Sección Legal", "Seccion Legal");
		takeFullPageScreenshot("04-administrar-negocios-page-full");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisible("Información General", "Informacion General");
		final String pageText = normalizeSpace(visiblePageText());

		assertTrue("Expected user name to be visible in account information.", containsLikelyName(pageText));
		assertTrue("Expected user email to be visible in account information.", EMAIL_PATTERN.matcher(pageText).find());
		assertAnyVisible("BUSINESS PLAN");
		assertAnyVisible("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertAnyVisible("Cuenta creada");
		assertAnyVisible("Estado activo");
		assertAnyVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertAnyVisible("Tus Negocios");
		assertAnyVisible("Agregar Negocio");
		assertAnyVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(final String linkText, final String headingText, final String screenshotPrefix)
			throws IOException {
		ensureOnAdministrarNegociosPage();

		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = driver.getWindowHandles();
		final String initialUrl = driver.getCurrentUrl();

		clickByAnyVisibleText(linkText);

		boolean openedNewTab = false;
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT)
					.until(d -> d.getWindowHandles().size() > windowsBefore.size() || !Objects.equals(d.getCurrentUrl(), initialUrl));
		} catch (final TimeoutException ignored) {
			// Continue and validate with current context.
		}

		if (driver.getWindowHandles().size() > windowsBefore.size()) {
			openedNewTab = true;
			switchToNewestWindow(windowsBefore);
		}

		waitForUiToLoad();
		waitForAnyVisibleText(headingText, withoutDiacritics(headingText));
		assertAnyVisible(headingText, withoutDiacritics(headingText));

		final String bodyText = normalizeSpace(visiblePageText());
		assertTrue("Expected legal content text to be visible for " + headingText + ".", bodyText.length() >= 120);

		takeScreenshot("05-" + screenshotPrefix + "-legal-page");
		legalUrls.put(headingText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		ensureOnAdministrarNegociosPage();
	}

	private void executeStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, StepResult.pass("Validation completed."));
		} catch (final Exception ex) {
			report.put(stepName, StepResult.fail(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));

			try {
				takeScreenshot("error-" + sanitize(stepName));
			} catch (final Exception ignored) {
				// Keep the test moving to produce a full report.
			}
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				failedSteps.add(entry.getKey() + " -> " + entry.getValue().details);
			}
		}

		if (!failedSteps.isEmpty()) {
			Assert.fail("One or more validations failed:\n - " + String.join("\n - ", failedSteps));
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test\n");
		sb.append("Generated at: ").append(LocalDateTime.now()).append('\n');
		sb.append("Evidence directory: ").append(evidenceDir).append('\n');
		sb.append('\n');
		sb.append("PASS/FAIL Summary:\n");

		final String[] orderedFields = new String[] { LOGIN, MI_NEGOCIO_MENU, AGREGAR_NEGOCIO_MODAL, ADMINISTRAR_NEGOCIOS_VIEW,
				INFORMACION_GENERAL, DETALLES_CUENTA, TUS_NEGOCIOS, TERMINOS, POLITICA };

		for (final String field : orderedFields) {
			final StepResult result = report.getOrDefault(field, StepResult.fail("Step did not execute."));
			sb.append("- ").append(field).append(": ").append(result.passed ? "PASS" : "FAIL");

			if (result.details != null && !result.details.isBlank()) {
				sb.append(" (").append(result.details).append(')');
			}

			sb.append('\n');
		}

		sb.append('\n');
		sb.append("Final URLs:\n");
		sb.append("- Términos y Condiciones: ")
				.append(legalUrls.getOrDefault("Términos y Condiciones", "N/A"))
				.append('\n');
		sb.append("- Política de Privacidad: ")
				.append(legalUrls.getOrDefault("Política de Privacidad", "N/A"))
				.append('\n');

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, sb.toString());
		System.out.println(sb);
	}

	private void expandMiNegocioIfCollapsed() {
		if (hasAnyVisibleText("Administrar Negocios")) {
			return;
		}

		clickByAnyVisibleText("Mi Negocio");
		waitForAnyVisibleText("Administrar Negocios");
	}

	private void ensureOnAdministrarNegociosPage() {
		if (hasAnyVisibleText("Sección Legal") || hasAnyVisibleText("Seccion Legal")) {
			return;
		}

		expandMiNegocioIfCollapsed();
		clickByAnyVisibleText("Administrar Negocios");
		waitForAnyVisibleText("Sección Legal", "Seccion Legal");
	}

	private void switchToNewestWindow(final Set<String> windowsBefore) {
		for (final String handle : driver.getWindowHandles()) {
			if (!windowsBefore.contains(handle)) {
				driver.switchTo().window(handle);
				return;
			}
		}
	}

	private boolean hasVisibleInputNombreNegocio() {
		return hasAnyVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio') or contains(@name,'nombre')]"))
				|| hasAnyVisibleText("Nombre del Negocio");
	}

	private void maybeSelectGoogleAccount(final String email) {
		try {
			final WebElement account = firstVisibleElement(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(email) + ")]"),
					Duration.ofSeconds(8));
			safeClick(account);
			waitForUiToLoad();
		} catch (final Exception ignored) {
			// Account chooser might not appear for pre-authenticated sessions.
		}
	}

	private void clickByAnyVisibleText(final String... texts) {
		Exception lastError = null;

		for (final String text : texts) {
			try {
				final WebElement element = firstVisibleElement(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]"),
						SHORT_TIMEOUT);
				safeClick(element);
				waitForUiToLoad();
				return;
			} catch (final Exception ex) {
				lastError = ex;
			}
		}

		throw new AssertionError("Could not click any element with text candidates: " + String.join(", ", texts), lastError);
	}

	private void assertAnyVisible(final String... texts) {
		assertTrue("Expected one of these texts to be visible: " + String.join(", ", texts), hasAnyVisibleText(texts));
	}

	private boolean hasAnyVisibleText(final String... texts) {
		for (final String text : texts) {
			if (hasAnyVisible(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]"))) {
				return true;
			}
		}
		return false;
	}

	private boolean hasAnyVisible(final By by) {
		try {
			final List<WebElement> elements = driver.findElements(by);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void waitForAnyVisibleText(final String... texts) {
		wait.until(d -> hasAnyVisibleText(texts));
	}

	private WebElement firstVisibleElement(final By by, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(d -> {
			for (final WebElement element : d.findElements(by)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private void safeClick(final WebElement element) {
		wait.until(ExpectedConditions.visibilityOf(element));

		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception ignored) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private String visiblePageText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));

		try {
			Thread.sleep(700);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String name) throws IOException {
		final Path screenshotPath = evidenceDir.resolve(sanitize(name) + ".png");
		final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshotPath, bytes);
	}

	private void takeFullPageScreenshot(final String name) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		final Number scrollWidth = (Number) ((JavascriptExecutor) driver)
				.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, window.innerWidth);");
		final Number scrollHeight = (Number) ((JavascriptExecutor) driver)
				.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, window.innerHeight);");

		final int width = Math.max(originalSize.width, scrollWidth.intValue());
		final int height = Math.max(originalSize.height, scrollHeight.intValue());

		driver.manage().window().setSize(new Dimension(width, height));
		waitForUiToLoad();
		takeScreenshot(name);
		driver.manage().window().setSize(originalSize);
		waitForUiToLoad();
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(path);
		return path;
	}

	private String firstNonBlank(final String first, final String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		if (second != null && !second.isBlank()) {
			return second;
		}
		return null;
	}

	private boolean containsLikelyName(final String pageText) {
		final String[] lines = pageText.split("\\R");

		for (final String line : lines) {
			final String clean = normalizeSpace(line);
			final String lower = clean.toLowerCase(Locale.ROOT);

			if (clean.length() < 4 || clean.length() > 80) {
				continue;
			}
			if (lower.contains("@") || lower.contains("business plan") || lower.contains("información general")
					|| lower.contains("informacion general")) {
				continue;
			}
			if (clean.matches("[\\p{L}][\\p{L} .'-]{2,}")) {
				return true;
			}
		}

		return false;
	}

	private String normalizeSpace(final String value) {
		return value == null ? "" : value.replaceAll("\\s+", " ").trim();
	}

	private String sanitize(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
	}

	private String withoutDiacritics(final String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
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
			if (i > 0) {
				sb.append(',');
			}
			if (chars[i] == '\'') {
				sb.append("\"").append('\'').append("\"");
			} else {
				sb.append("'").append(chars[i]).append("'");
			}
		}
		sb.append(')');
		return sb.toString();
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

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
