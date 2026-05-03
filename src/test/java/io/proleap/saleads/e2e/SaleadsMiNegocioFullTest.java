package io.proleap.saleads.e2e;

import java.io.File;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for the Mi Negocio workflow.
 *
 * <p>
 * Environment-agnostic usage:
 * </p>
 * <ul>
 * <li>Option A: provide SALEADS_LOGIN_URL or -Dsaleads.login.url</li>
 * <li>Option B: omit URL and manually navigate the opened browser session to the login page</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration UI_TIMEOUT = Duration.ofSeconds(40);
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;
	private final AtomicInteger screenshotCounter = new AtomicInteger(0);
	private final LinkedHashMap<String, StepResult> stepResults = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		artifactsDir = Paths.get("target", "saleads-artifacts", runId);
		Files.createDirectories(artifactsDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		if (shouldRunHeadless()) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, UI_TIMEOUT);

		final String loginUrl = configuredLoginUrl();
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final Path reportPath = writeFinalReport();
		Assert.assertTrue("One or more validations failed. Review report: " + reportPath.toAbsolutePath(),
				allStepsPassed());
	}

	private void stepLoginWithGoogle() throws IOException {
		ensureCurrentPageLooksLikeLogin();

		final WebElement googleButton = findVisibleElement(By.xpath(
				"//*[self::button or self::a][contains(translate(normalize-space(.),"
					+ " 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), 'google')]"));
		clickElement(googleButton);

		selectGoogleAccountIfPrompted("juanlucasbarbiergarzon@gmail.com");
		waitForUiToLoad();

		waitForAnyVisibleElement(By.xpath("//main"), By.xpath("//aside"), By.xpath("//nav"));
		waitForVisibleText("Negocio");
		captureScreenshot("dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		waitForAnyVisibleElement(By.xpath("//aside"), By.xpath("//nav"));
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		captureScreenshot("mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		final WebElement title = waitForVisibleText("Crear Nuevo Negocio");
		final WebElement modal = nearestDialogContainer(title);

		waitForVisibleText("Nombre del Negocio");
		final List<WebElement> inputs = modal.findElements(By.xpath(".//input"));
		Assert.assertFalse("Expected at least one input in the business creation modal.", inputs.isEmpty());
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");

		captureScreenshot("agregar-negocio-modal");

		final WebElement businessNameInput = inputs.get(0);
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOf(modal));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureMiNegocioSubmenuIsVisible();
		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		waitForVisibleText("Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal");
		captureScreenshot("administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement infoSection = findSectionByTitle("Información General");
		final String sectionText = infoSection.getText();

		Assert.assertTrue("Expected email to be visible in Información General.",
				EMAIL_PATTERN.matcher(sectionText).find());
		Assert.assertTrue("Expected BUSINESS PLAN text in Información General.", containsIgnoreCase(sectionText,
				"BUSINESS PLAN"));
		Assert.assertTrue("Expected Cambiar Plan button in Información General.",
				containsIgnoreCase(sectionText, "Cambiar Plan"));
		Assert.assertTrue("Expected a user name-like value in Información General.", hasLikelyUserName(sectionText));
	}

	private void stepValidateDetallesCuenta() {
		final WebElement detallesSection = findSectionByTitle("Detalles de la Cuenta");
		final String sectionText = detallesSection.getText();

		Assert.assertTrue("Missing 'Cuenta creada'.", containsIgnoreCase(sectionText, "Cuenta creada"));
		Assert.assertTrue("Missing 'Estado activo'.", containsIgnoreCase(sectionText, "Estado activo"));
		Assert.assertTrue("Missing 'Idioma seleccionado'.", containsIgnoreCase(sectionText, "Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByTitle("Tus Negocios");
		final String sectionText = section.getText();

		Assert.assertTrue("Missing 'Agregar Negocio' button in Tus Negocios.", containsIgnoreCase(sectionText,
				"Agregar Negocio"));
		Assert.assertTrue("Missing business quota text in Tus Negocios.", containsIgnoreCase(sectionText,
				"Tienes 2 de 3 negocios"));
		Assert.assertTrue("Business list should be visible in Tus Negocios.", hasVisibleBusinessList(section));
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		validateLegalLink("Términos y Condiciones", "Términos y Condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		validateLegalLink("Política de Privacidad", "Política de Privacidad");
	}

	private void validateLegalLink(final String linkText, final String expectedHeading) throws IOException {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		final String destinationHandle = waitForDestinationHandle(handlesBefore, appHandle);
		final boolean openedNewTab = !appHandle.equals(destinationHandle);

		if (openedNewTab) {
			driver.switchTo().window(destinationHandle);
			waitForUiToLoad();
		}

		waitForVisibleText(expectedHeading);
		final String legalPageText = waitForVisibleElement(By.tagName("body")).getText();
		Assert.assertTrue("Expected legal content text for '" + expectedHeading + "'.", legalPageText.length() > 120);
		captureScreenshot(sanitize("legal-" + expectedHeading));

		legalUrls.put(expectedHeading, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		waitForVisibleText("Sección Legal");
	}

	private void ensureCurrentPageLooksLikeLogin() {
		final List<By> loginIndicators = Arrays.asList(
				By.xpath("//*[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'google')]"),
				By.xpath("//*[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'iniciar sesión')]"),
				By.xpath("//*[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'sign in')]"));

		for (final By indicator : loginIndicators) {
			if (isElementVisible(indicator, Duration.ofSeconds(4))) {
				return;
			}
		}

		throw new IllegalStateException(
				"The active page does not appear to be a login screen. "
						+ "Set SALEADS_LOGIN_URL or -Dsaleads.login.url for the current environment.");
	}

	private void selectGoogleAccountIfPrompted(final String email) {
		final By accountSelector = By
				.xpath("//*[normalize-space()=" + toXPathLiteral(email) + " or contains(normalize-space(),"
						+ toXPathLiteral(email) + ")]");
		if (isElementVisible(accountSelector, Duration.ofSeconds(8))) {
			clickElement(findVisibleElement(accountSelector));
		}
	}

	private void ensureMiNegocioSubmenuIsVisible() {
		if (!isElementVisible(By.xpath("//*[contains(normalize-space(), " + toXPathLiteral("Administrar Negocios") + ")]"),
				Duration.ofSeconds(3))) {
			clickByVisibleText("Mi Negocio");
		}
		waitForVisibleText("Administrar Negocios");
	}

	private void runStep(final String reportKey, final CheckedStep step) {
		try {
			step.execute();
			stepResults.put(reportKey, StepResult.pass());
		} catch (final Throwable t) {
			stepResults.put(reportKey, StepResult.fail(t.getMessage()));
			try {
				captureScreenshot("failure-" + sanitize(reportKey));
			} catch (final IOException ignored) {
				// best effort evidence on failure
			}
		}
	}

	private void clickByVisibleText(final String text) {
		final String literal = toXPathLiteral(text);
		final By locator = By.xpath(
				"//*[self::button or self::a or @role='button' or self::span or self::div or self::li]"
					+ "[normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")]");
		final WebElement element = findVisibleElement(locator);
		clickElement(resolveClickableElement(element));
	}

	private WebElement findSectionByTitle(final String title) {
		final WebElement header = waitForVisibleText(title);
		return header.findElement(By.xpath("ancestor::*[self::section or self::div][1]"));
	}

	private boolean hasVisibleBusinessList(final WebElement section) {
		final List<WebElement> businessCandidates = section
				.findElements(By.xpath(".//li | .//tr | .//article | .//div[contains(@class,'business')]"));
		for (final WebElement candidate : businessCandidates) {
			if (candidate.isDisplayed()) {
				return true;
			}
		}

		final String text = section.getText();
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
		return text.split("\\R").length > 4 || emailMatcher.find();
	}

	private boolean hasLikelyUserName(final String sectionText) {
		for (final String rawLine : sectionText.split("\\R+")) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}

			final String lower = line.toLowerCase(Locale.ROOT);
			if (lower.contains("información general") || lower.contains("business plan") || lower.contains("cambiar plan")
					|| line.contains("@")) {
				continue;
			}

			if (line.matches(".*[A-Za-zÁÉÍÓÚÜÑáéíóúüñ].*")) {
				return true;
			}
		}

		return false;
	}

	private void clickElement(final WebElement element) {
		scrollIntoView(element);
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until((ExpectedCondition<Boolean>) wd -> {
			final Object state = ((JavascriptExecutor) wd).executeScript("return document.readyState");
			return "complete".equals(state) || "interactive".equals(state);
		});
	}

	private WebElement waitForVisibleText(final String text) {
		final By locator = By.xpath("//*[contains(normalize-space(), " + toXPathLiteral(text) + ")]");
		return waitForVisibleElement(locator);
	}

	private WebElement waitForVisibleElement(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private WebElement waitForAnyVisibleElement(final By... locators) {
		return wait.until(driver -> {
			for (final By locator : locators) {
				final List<WebElement> elements = driver.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private WebElement findVisibleElement(final By locator) {
		return wait.until(driver -> {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private boolean isElementVisible(final By locator, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(driver -> {
				for (final WebElement element : driver.findElements(locator)) {
					if (element.isDisplayed()) {
						return true;
					}
				}
				return false;
			});
		} catch (final Exception e) {
			return false;
		}
	}

	private WebElement resolveClickableElement(final WebElement element) {
		final List<WebElement> clickables = element
				.findElements(By.xpath("ancestor-or-self::*[self::button or self::a or @role='button' or self::li]"));
		if (!clickables.isEmpty()) {
			return clickables.get(0);
		}
		return element;
	}

	private WebElement nearestDialogContainer(final WebElement elementInsideDialog) {
		final List<WebElement> dialogs = elementInsideDialog.findElements(
				By.xpath("ancestor::*[@role='dialog' or contains(@class,'modal') or contains(@class,'dialog')]"));
		if (!dialogs.isEmpty()) {
			return dialogs.get(0);
		}
		return elementInsideDialog;
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private Path captureScreenshot(final String label) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String fileName = String.format("%02d-%s.png", screenshotCounter.incrementAndGet(), sanitize(label));
		final Path destination = artifactsDir.resolve(fileName);
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		return destination;
	}

	private Path writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("Generated at: " + LocalDateTime.now());
		lines.add("Artifacts: " + artifactsDir.toAbsolutePath());
		lines.add("");
		lines.add("Final report:");

		for (final String field : REPORT_FIELDS) {
			final StepResult result = stepResults.getOrDefault(field, StepResult.fail("Step not executed"));
			lines.add("- " + field + ": " + (result.passed ? "PASS" : "FAIL"));
			if (result.details != null && !result.details.isBlank()) {
				lines.add("  details: " + result.details);
			}
		}

		if (!legalUrls.isEmpty()) {
			lines.add("");
			lines.add("Captured legal URLs:");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				lines.add("- " + entry.getKey() + ": " + entry.getValue());
			}
		}

		final Path reportPath = artifactsDir.resolve("final-report.txt");
		Files.write(reportPath, lines, StandardCharsets.UTF_8);
		return reportPath;
	}

	private boolean allStepsPassed() {
		for (final String field : REPORT_FIELDS) {
			final StepResult result = stepResults.get(field);
			if (result == null || !result.passed) {
				return false;
			}
		}
		return true;
	}

	private String waitForDestinationHandle(final Set<String> handlesBefore, final String fallbackHandle) {
		final long timeoutAt = System.currentTimeMillis() + Duration.ofSeconds(8).toMillis();
		while (System.currentTimeMillis() < timeoutAt) {
			final Set<String> handlesNow = driver.getWindowHandles();
			if (handlesNow.size() > handlesBefore.size()) {
				for (final String handle : handlesNow) {
					if (!handlesBefore.contains(handle)) {
						return handle;
					}
				}
			}
			sleepSilently(150);
		}
		return fallbackHandle;
	}

	private void sleepSilently(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean shouldRunHeadless() {
		final String property = System.getProperty("saleads.headless");
		if (property != null) {
			return Boolean.parseBoolean(property);
		}

		final String env = System.getenv("SALEADS_HEADLESS");
		if (env != null) {
			return Boolean.parseBoolean(env);
		}

		return true;
	}

	private String configuredLoginUrl() {
		final String property = System.getProperty("saleads.login.url");
		if (property != null && !property.isBlank()) {
			return property;
		}
		return System.getenv("SALEADS_LOGIN_URL");
	}

	private String sanitize(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				builder.append(", ");
			}
			final char c = chars[i];
			if (c == '\'') {
				builder.append("\"'\"");
			} else {
				builder.append("'").append(c).append("'");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private boolean containsIgnoreCase(final String text, final String fragment) {
		return text.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
	}

	@FunctionalInterface
	private interface CheckedStep {
		void execute() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, null);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details == null ? "No details" : details);
		}
	}
}
