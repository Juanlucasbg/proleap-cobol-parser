package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.openqa.selenium.Dimension;
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

public class SaleadsMiNegocioWorkflowE2ETest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}][\\p{L}\\s'.-]{2,}$");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String MODAL_TEST_BUSINESS_NAME = "Negocio Prueba Automatización";

	private static class StepResult {
		private boolean passed;
		private final List<String> details = new ArrayList<>();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private final Map<String, StepResult> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor jsExecutor;
	private Path evidenceRoot;
	private Path screenshotDir;
	private String appWindowHandle;
	private String termsUrl = "";
	private String privacyUrl = "";
	private int explicitTimeoutSeconds;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = getSetting("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL (or -Dsaleads.login.url) to the SaleADS login page for your environment.",
				loginUrl != null && !loginUrl.isBlank());

		evidenceRoot = Paths.get(getSettingOrDefault("saleads.evidence.dir", "SALEADS_EVIDENCE_DIR", "target/saleads-e2e"));
		screenshotDir = evidenceRoot.resolve("screenshots");
		Files.createDirectories(screenshotDir);

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(getSettingOrDefault("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,2200", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		explicitTimeoutSeconds = Integer
				.parseInt(getSettingOrDefault("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", "30"));
		wait = new WebDriverWait(driver, Duration.ofSeconds(explicitTimeoutSeconds));
		jsExecutor = (JavascriptExecutor) driver;

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
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaDePrivacidad);

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				failedSteps.add(entry.getKey() + " -> " + String.join(" | ", entry.getValue().details));
			}
		}

		assertTrue("Failed workflow validations:\n" + String.join("\n", failedSteps), failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		appWindowHandle = driver.getWindowHandle();
		clickByAnyVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Acceder con Google", "Google");
		waitForUiToLoad();

		handleGoogleAccountSelectionIfShown();
		switchToWindow(appWindowHandle);
		waitForUiToLoad();

		waitForAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Inicio");
		final boolean hasSidebar = hasVisibleElement(By.xpath("//aside|//nav[contains(@class,'sidebar')]"), 8)
				|| hasVisibleElement(By.xpath("//*[normalize-space()='Negocio' or normalize-space()='Mi Negocio']"), 8);
		assertTrue("Left sidebar navigation is not visible.", hasSidebar);

		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		if (!hasVisibleText("Mi Negocio", 3)) {
			clickByAnyVisibleText("Negocio");
			waitForUiToLoad();
		}

		clickByAnyVisibleText("Mi Negocio");
		waitForUiToLoad();

		waitForAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByAnyVisibleText("Agregar Negocio");
		waitForUiToLoad();

		waitForAnyVisibleText("Crear Nuevo Negocio");
		waitForAnyVisibleText("Nombre del Negocio");
		waitForAnyVisibleText("Tienes 2 de 3 negocios");
		waitForAnyVisibleText("Cancelar");
		waitForAnyVisibleText("Crear Negocio");

		final WebElement businessNameField = waitForVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre') or @name='name' or @id='name' or @aria-label='Nombre del Negocio']"),
				15);
		businessNameField.click();
		businessNameField.clear();
		businessNameField.sendKeys(MODAL_TEST_BUSINESS_NAME);

		takeScreenshot("03-agregar-negocio-modal");
		clickByAnyVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!hasVisibleText("Administrar Negocios", 3)) {
			clickByAnyVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		clickByAnyVisibleText("Administrar Negocios");
		waitForUiToLoad();

		waitForAnyVisibleText("Información General");
		waitForAnyVisibleText("Detalles de la Cuenta", "Detalles de la cuenta");
		waitForAnyVisibleText("Tus Negocios");
		waitForAnyVisibleText("Sección Legal", "Seccion Legal");

		driver.manage().window().setSize(new Dimension(1920, 3200));
		waitForUiToLoad();
		takeScreenshot("04-administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		final WebElement infoSection = sectionByHeading("Información General", "Informacion General");
		final List<String> sectionLines = extractVisibleSectionLines(infoSection);

		assertTrue("User email is not visible in Información General.", containsEmail(sectionLines));
		assertTrue("User name is not visible in Información General.", containsName(sectionLines));
		assertSectionContainsText(sectionLines, "BUSINESS PLAN");
		waitForAnyVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesDeLaCuenta() throws Exception {
		waitForAnyVisibleText("Cuenta creada");
		waitForAnyVisibleText("Estado activo");
		waitForAnyVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() throws Exception {
		final WebElement section = sectionByHeading("Tus Negocios");
		waitForAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Tienes 2 de 3 negocios");

		final boolean hasBusinessList = hasVisibleElement(By.xpath(
				".//li[normalize-space()] | .//table/tbody/tr | .//*[contains(@class,'business') and normalize-space()]"),
				section, 8);
		assertTrue("Business list is not visible in 'Tus Negocios'.", hasBusinessList);
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		termsUrl = openLegalLinkAndValidate("Términos y Condiciones", "Terminos y Condiciones");
		takeScreenshot("05-terminos-y-condiciones");
	}

	private void stepValidatePoliticaDePrivacidad() throws Exception {
		privacyUrl = openLegalLinkAndValidate("Política de Privacidad", "Politica de Privacidad");
		takeScreenshot("06-politica-de-privacidad");
	}

	private void runStep(final String stepName, final StepAction action) {
		final StepResult stepResult = new StepResult();
		try {
			action.run();
			stepResult.passed = true;
			stepResult.details.add("PASS");
		} catch (final Throwable ex) {
			stepResult.passed = false;
			stepResult.details.add(cleanMessage(ex));
			try {
				takeScreenshot("failure-" + normalizeFileName(stepName));
			} catch (final Exception ignored) {
				// ignore screenshot errors while recording failure
			}
		}
		report.put(stepName, stepResult);
	}

	private String openLegalLinkAndValidate(final String... linkTextVariants) throws Exception {
		switchToWindow(appWindowHandle);
		waitForUiToLoad();

		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String originalUrl = driver.getCurrentUrl();

		clickByAnyVisibleText(linkTextVariants);
		waitForUiToLoad();

		String legalHandle = driver.getWindowHandle();
		boolean openedNewTab = false;

		try {
			wait.until(d -> d.getWindowHandles().size() > handlesBefore.size()
					|| !d.getCurrentUrl().equals(originalUrl));
		} catch (final TimeoutException timeout) {
			// Continue and validate on current context if no obvious navigation event was detected.
		}

		final Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > handlesBefore.size()) {
			for (final String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					legalHandle = handle;
					openedNewTab = true;
					break;
				}
			}
			switchToWindow(legalHandle);
		}

		waitForUiToLoad();
		waitForAnyVisibleText(linkTextVariants);
		assertLegalContentVisible();
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			switchToWindow(appWindowHandle);
			waitForUiToLoad();
		} else if (!finalUrl.equals(originalUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void assertLegalContentVisible() {
		final WebElement body = waitForVisible(By.tagName("body"), 15);
		final String bodyText = body.getText().trim();
		assertTrue("Legal content text is not visible.", bodyText.length() > 100);
	}

	private WebElement sectionByHeading(final String... headings) {
		final WebElement headingElement = waitForVisible(byVisibleText(headings), 15);
		final WebElement section = findClosestSection(headingElement);
		return section == null ? headingElement : section;
	}

	private WebElement findClosestSection(final WebElement element) {
		try {
			return element.findElement(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
		} catch (final NoSuchElementException ex) {
			return null;
		}
	}

	private List<String> extractVisibleSectionLines(final WebElement section) {
		final List<String> lines = new ArrayList<>();
		final List<WebElement> candidates = section
				.findElements(By.xpath(".//*[self::p or self::span or self::div or self::h1 or self::h2 or self::h3]"));
		for (final WebElement element : candidates) {
			if (!element.isDisplayed()) {
				continue;
			}
			final String text = element.getText().trim();
			if (!text.isBlank()) {
				lines.add(text);
			}
		}
		return lines;
	}

	private boolean containsEmail(final List<String> sectionLines) {
		for (final String line : sectionLines) {
			for (final String token : line.split("\\s+")) {
				if (EMAIL_PATTERN.matcher(token.trim()).matches()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean containsName(final List<String> sectionLines) {
		final Set<String> ignored = new LinkedHashSet<>(Arrays.asList("INFORMACIÓN GENERAL", "INFORMACION GENERAL",
				"BUSINESS PLAN", "CAMBIAR PLAN"));
		for (final String line : sectionLines) {
			final String normalized = normalizeText(line).toUpperCase(Locale.ROOT);
			if (ignored.contains(normalized)) {
				continue;
			}
			if (line.contains("@")) {
				continue;
			}
			if (NAME_PATTERN.matcher(line.trim()).matches()) {
				return true;
			}
		}
		return false;
	}

	private void assertSectionContainsText(final List<String> sectionLines, final String expected) {
		final String expectedNormalized = normalizeText(expected);
		for (final String line : sectionLines) {
			if (normalizeText(line).contains(expectedNormalized)) {
				return;
			}
		}
		throw new AssertionError("Expected text '" + expected + "' was not found in the section.");
	}

	private void handleGoogleAccountSelectionIfShown() {
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		try {
			wait.withTimeout(Duration.ofSeconds(10)).until(d -> d.getWindowHandles().size() > handlesBefore.size()
					|| hasVisibleText(GOOGLE_ACCOUNT_EMAIL, 2));
		} catch (final TimeoutException ex) {
			wait.withTimeout(Duration.ofSeconds(explicitTimeoutSeconds));
			return;
		}

		wait.withTimeout(Duration.ofSeconds(explicitTimeoutSeconds));
		Set<String> handlesNow = driver.getWindowHandles();
		if (handlesNow.size() > handlesBefore.size()) {
			for (final String handle : handlesNow) {
				if (!handlesBefore.contains(handle)) {
					switchToWindow(handle);
					break;
				}
			}
		}

		if (hasVisibleText(GOOGLE_ACCOUNT_EMAIL, 5)) {
			clickByAnyVisibleText(GOOGLE_ACCOUNT_EMAIL);
			waitForUiToLoad();
		}

		handlesNow = driver.getWindowHandles();
		if (handlesNow.contains(appWindowHandle)) {
			switchToWindow(appWindowHandle);
		}
	}

	private void clickByAnyVisibleText(final String... textOptions) {
		final By locator = byVisibleText(textOptions);
		final WebElement element = wait.until(d -> {
			for (final WebElement candidate : d.findElements(locator)) {
				if (candidate.isDisplayed() && candidate.isEnabled()) {
					return candidate;
				}
			}
			return null;
		});
		element.click();
		waitForUiToLoad();
	}

	private By byVisibleText(final String... textOptions) {
		final List<String> optionPredicates = new ArrayList<>();
		for (final String text : textOptions) {
			final String normalized = normalizeForXPath(text);
			optionPredicates.add("(translate(normalize-space(.),'" + upperCaseAlphabet() + "','" + lowerCaseAlphabet()
					+ "')='" + normalized + "' or contains(translate(normalize-space(.),'" + upperCaseAlphabet() + "','"
					+ lowerCaseAlphabet() + "'),'" + normalized + "'))");
		}

		final String textPredicate = String.join(" or ", optionPredicates);
		final String xpath = "//*[(self::button or self::a or self::span or self::div or self::p or @role='button' "
				+ "or self::h1 or self::h2 or self::h3) and (" + textPredicate + ")]";
		return By.xpath(xpath);
	}

	private void waitForAnyVisibleText(final String... textOptions) {
		waitForVisible(byVisibleText(textOptions), 20);
	}

	private boolean hasVisibleText(final String text, final int timeoutSeconds) {
		return hasVisibleElement(byVisibleText(text), timeoutSeconds);
	}

	private boolean hasVisibleElement(final By by, final int timeoutSeconds) {
		try {
			waitForVisible(by, timeoutSeconds);
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private boolean hasVisibleElement(final By by, final WebElement root, final int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(d -> {
				final List<WebElement> elements = root.findElements(by);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return true;
					}
				}
				return false;
			});
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private WebElement waitForVisible(final By by, final int timeoutSeconds) {
		return new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(d -> {
			for (final WebElement candidate : d.findElements(by)) {
				if (candidate.isDisplayed()) {
					return candidate;
				}
			}
			return null;
		});
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(jsExecutor.executeScript("return document.readyState")));
		try {
			Thread.sleep(500L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void switchToWindow(final String handle) {
		driver.switchTo().window(handle);
	}

	private void takeScreenshot(final String label) throws IOException {
		final Path screenshotPath = screenshotDir.resolve(Instant.now().toEpochMilli() + "-" + normalizeFileName(label) + ".png");
		final Path tempPath = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(tempPath, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() throws IOException {
		if (evidenceRoot == null) {
			return;
		}
		Files.createDirectories(evidenceRoot);

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("# SaleADS Mi Negocio Full Test Report\n\n");
		reportBuilder.append("Generated at: ").append(Instant.now()).append("\n\n");
		reportBuilder.append("| Step | Status | Details |\n");
		reportBuilder.append("|---|---|---|\n");

		final List<String> orderedFields = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Términos y Condiciones", "Política de Privacidad");

		for (final String field : orderedFields) {
			final StepResult stepResult = report.get(field);
			final String status = stepResult != null && stepResult.passed ? "PASS" : "FAIL";
			final String details = stepResult == null ? "Not executed."
					: String.join(" ; ", stepResult.details).replace("|", "/");
			reportBuilder.append("| ").append(field).append(" | ").append(status).append(" | ").append(details)
					.append(" |\n");
		}

		reportBuilder.append("\n## Captured URLs\n\n");
		reportBuilder.append("- Términos y Condiciones: ").append(termsUrl.isBlank() ? "N/A" : termsUrl).append("\n");
		reportBuilder.append("- Política de Privacidad: ").append(privacyUrl.isBlank() ? "N/A" : privacyUrl).append("\n");

		Files.writeString(evidenceRoot.resolve("final-report.md"), reportBuilder.toString());
	}

	private String getSetting(final String property, final String envName) {
		final String fromProperty = System.getProperty(property);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}
		final String fromEnv = System.getenv(envName);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv.trim();
		}
		return null;
	}

	private String getSettingOrDefault(final String property, final String envName, final String defaultValue) {
		final String value = getSetting(property, envName);
		return value == null ? defaultValue : value;
	}

	private String cleanMessage(final Throwable ex) {
		final String message = ex.getMessage();
		return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message.replace('\n', ' ').trim();
	}

	private String normalizeFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String normalizeText(final String value) {
		final String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return normalized.toLowerCase(Locale.ROOT).trim();
	}

	private String normalizeForXPath(final String value) {
		return value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
	}

	private String upperCaseAlphabet() {
		return "ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ";
	}

	private String lowerCaseAlphabet() {
		return "abcdefghijklmnopqrstuvwxyzáéíóúüñ";
	}
}
