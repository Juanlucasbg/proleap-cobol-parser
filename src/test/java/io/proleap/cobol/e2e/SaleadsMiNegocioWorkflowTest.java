package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_POLITICA = "Política de Privacidad";

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern LETTERS_PATTERN = Pattern.compile(".*[\\p{L}].*[\\p{L}].*");

	private final Map<String, StepReport> reportByStep = new LinkedHashMap<>();
	private final List<String> legalUrls = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this test.",
				Boolean.parseBoolean(readEnv("SALEADS_E2E_ENABLED", "false")));

		screenshotDir = Path.of("target", "saleads-e2e-screenshots",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)));
		Files.createDirectories(screenshotDir);
	}

	@After
	public void tearDown() {
		try {
			logFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		initializeDriver();
		runWorkflow();
		assertAllStepsPassed();
	}

	private void initializeDriver() {
		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	private WebDriver createDriver() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (Boolean.parseBoolean(readEnv("SALEADS_E2E_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = System.getenv("SALEADS_E2E_REMOTE_URL");

		if (remoteUrl != null && !remoteUrl.isBlank()) {
			try {
				return new RemoteWebDriver(java.net.URI.create(remoteUrl).toURL(), options);
			} catch (final Exception ex) {
				throw new IllegalArgumentException("Invalid SALEADS_E2E_REMOTE_URL: " + remoteUrl, ex);
			}
		}

		return new ChromeDriver(options);
	}

	private void runWorkflow() throws Exception {
		if (!executeStep(STEP_LOGIN, this::stepLogin)) {
			markRemainingAsFailed("Blocked after login failure.");
			return;
		}

		if (!executeStep(STEP_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu)) {
			markRemainingAsFailed("Blocked after Mi Negocio menu failure.");
			return;
		}

		if (!executeStep(STEP_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal)) {
			markRemainingAsFailed("Blocked after Agregar Negocio modal failure.");
			return;
		}

		if (!executeStep(STEP_ADMINISTRAR_NEGOCIOS, this::stepOpenAdministrarNegocios)) {
			markRemainingAsFailed("Blocked after Administrar Negocios view failure.");
			return;
		}

		if (!executeStep(STEP_INFO_GENERAL, this::stepValidateInformacionGeneral)) {
			markRemainingAsFailed("Blocked after Información General failure.");
			return;
		}

		if (!executeStep(STEP_DETALLES_CUENTA, this::stepValidateDetallesDeCuenta)) {
			markRemainingAsFailed("Blocked after Detalles de la Cuenta failure.");
			return;
		}

		if (!executeStep(STEP_TUS_NEGOCIOS, this::stepValidateTusNegocios)) {
			markRemainingAsFailed("Blocked after Tus Negocios failure.");
			return;
		}

		if (!executeStep(STEP_TERMINOS, () -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones"))) {
			markRemainingAsFailed("Blocked after Términos y Condiciones failure.");
			return;
		}

		executeStep(STEP_POLITICA, () -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad"));
	}

	private void stepLogin() throws Exception {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		assertTrue("SALEADS_LOGIN_URL must be set to the current environment login page.",
				loginUrl != null && !loginUrl.isBlank());

		driver.get(loginUrl);
		waitForUiToLoad();

		final Set<String> handlesBeforeLogin = driver.getWindowHandles();
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiToLoad();

		switchToNewTabIfNeeded(handlesBeforeLogin);
		selectGoogleAccountIfPrompted();
		waitForUiToLoad();

		switchBackToApplicationWindow();
		waitForAnyVisibleText("Negocio", "Mi Negocio");
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		waitForUiToLoad();
		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		waitForAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Administrar Negocios");

		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForUiToLoad();

		waitForAnyVisibleText("Crear Nuevo Negocio");
		waitForInputByLabel("Nombre del Negocio");
		waitForAnyVisibleText("Tienes 2 de 3 negocios");
		waitForAnyVisibleText("Cancelar");
		waitForAnyVisibleText("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal");

		typeIntoInputByLabel("Nombre del Negocio", "Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioMenuExpanded();
		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		waitForAnyVisibleText("Información General");
		waitForAnyVisibleText("Detalles de la Cuenta");
		waitForAnyVisibleText("Tus Negocios");
		waitForAnyVisibleText("Sección Legal");

		takeScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionWithHeading("Información General");
		final String sectionText = section.getText();

		assertTrue("Información General should contain a user email.", containsEmail(sectionText));
		assertTrue("Información General should contain a likely user name.", containsLikelyUserName(sectionText));
		assertTrue("Información General should contain BUSINESS PLAN.", sectionText.contains("BUSINESS PLAN"));
		assertVisibleInSection(section, "Cambiar Plan");
	}

	private void stepValidateDetallesDeCuenta() {
		final WebElement section = findSectionWithHeading("Detalles de la Cuenta");
		assertVisibleInSection(section, "Cuenta creada");
		assertVisibleInSection(section, "Estado activo");
		assertVisibleInSection(section, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionWithHeading("Tus Negocios");
		assertVisibleInSection(section, "Agregar Negocio");
		assertVisibleInSection(section, "Tienes 2 de 3 negocios");

		final String text = section.getText().trim();
		assertTrue("Tus Negocios should show at least one business entry.", text.length() > "Tus Negocios".length() + 10);
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading) throws Exception {
		final String appHandle = appWindowHandle;
		final Set<String> beforeHandles = driver.getWindowHandles();
		final String currentUrlBeforeClick = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		wait.until(anyOf(
				drv -> drv.getWindowHandles().size() > beforeHandles.size(),
				drv -> !drv.getCurrentUrl().equals(currentUrlBeforeClick)));

		final Optional<String> newHandle = findNewHandle(beforeHandles, driver.getWindowHandles());
		boolean switchedToNewTab = false;

		if (newHandle.isPresent()) {
			driver.switchTo().window(newHandle.get());
			switchedToNewTab = true;
		}

		waitForUiToLoad();
		waitForAnyVisibleText(expectedHeading);
		assertTrue("Legal content must be visible on " + expectedHeading + " page.", visibleTextLength() > 150);

		legalUrls.add(expectedHeading + " -> " + driver.getCurrentUrl());
		takeScreenshot("legal-" + sanitizeFileName(expectedHeading));

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		waitForAnyVisibleText("Sección Legal");
	}

	private void ensureMiNegocioMenuExpanded() {
		if (!isAnyTextVisible(Duration.ofSeconds(2), "Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}
	}

	private void selectGoogleAccountIfPrompted() {
		if (!isAnyTextVisible(SHORT_TIMEOUT, GOOGLE_ACCOUNT_EMAIL)) {
			return;
		}

		clickByVisibleText(GOOGLE_ACCOUNT_EMAIL);
		waitForUiToLoad();
	}

	private void switchToNewTabIfNeeded(final Set<String> previousHandles) {
		final Set<String> currentHandles = driver.getWindowHandles();
		if (currentHandles.size() <= previousHandles.size()) {
			return;
		}

		for (final String handle : currentHandles) {
			if (!previousHandles.contains(handle)) {
				driver.switchTo().window(handle);
				return;
			}
		}
	}

	private void switchBackToApplicationWindow() {
		if (driver.getWindowHandle().equals(appWindowHandle)) {
			return;
		}

		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private boolean executeStep(final String stepName, final StepAction action) {
		try {
			action.run();
			reportByStep.put(stepName, StepReport.pass());
			return true;
		} catch (final Exception ex) {
			reportByStep.put(stepName, StepReport.fail(ex.getMessage()));
			takeScreenshotQuietly("failure-" + sanitizeFileName(stepName));
			return false;
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failedSteps = reportByStep.entrySet().stream()
				.filter(entry -> !entry.getValue().passed)
				.map(entry -> entry.getKey() + " (" + entry.getValue().detail + ")")
				.collect(Collectors.toList());

		assertTrue("One or more steps failed: " + failedSteps, failedSteps.isEmpty());
	}

	private void markRemainingAsFailed(final String reason) {
		for (final String step : Arrays.asList(STEP_LOGIN, STEP_MI_NEGOCIO_MENU, STEP_AGREGAR_NEGOCIO_MODAL,
				STEP_ADMINISTRAR_NEGOCIOS, STEP_INFO_GENERAL, STEP_DETALLES_CUENTA, STEP_TUS_NEGOCIOS, STEP_TERMINOS,
				STEP_POLITICA)) {
			if (!reportByStep.containsKey(step)) {
				reportByStep.put(step, StepReport.fail(reason));
			}
		}
	}

	private void logFinalReport() {
		if (reportByStep.isEmpty()) {
			return;
		}

		System.out.println("=== SaleADS Mi Negocio Full Workflow Report ===");
		reportStep(STEP_LOGIN);
		reportStep(STEP_MI_NEGOCIO_MENU);
		reportStep(STEP_AGREGAR_NEGOCIO_MODAL);
		reportStep(STEP_ADMINISTRAR_NEGOCIOS);
		reportStep(STEP_INFO_GENERAL);
		reportStep(STEP_DETALLES_CUENTA);
		reportStep(STEP_TUS_NEGOCIOS);
		reportStep(STEP_TERMINOS);
		reportStep(STEP_POLITICA);

		if (!legalUrls.isEmpty()) {
			System.out.println("Captured legal URLs:");
			for (final String legalUrl : legalUrls) {
				System.out.println(" - " + legalUrl);
			}
		}

		System.out.println("Screenshots: " + screenshotDir.toAbsolutePath());
		System.out.println("===============================================");
	}

	private void reportStep(final String step) {
		final StepReport stepReport = reportByStep.get(step);
		if (stepReport == null) {
			System.out.println(step + ": FAIL (step not executed)");
			return;
		}

		System.out.println(step + ": " + (stepReport.passed ? "PASS" : "FAIL")
				+ (stepReport.detail == null ? "" : " - " + stepReport.detail));
	}

	private void clickByVisibleText(final String... candidateTexts) {
		for (final String candidateText : candidateTexts) {
			final List<By> selectors = List.of(
					By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.), "
							+ xpathLiteral(candidateText) + ")]"),
					By.xpath("//span[contains(normalize-space(.), " + xpathLiteral(candidateText)
							+ ")]/ancestor::*[self::button or self::a or @role='button'][1]"),
					By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(candidateText) + ")]"));

			for (final By selector : selectors) {
				final List<WebElement> elements = driver.findElements(selector);
				for (final WebElement element : elements) {
					if (!element.isDisplayed()) {
						continue;
					}
					scrollIntoView(element);
					try {
						wait.until(ExpectedConditions.elementToBeClickable(element)).click();
					} catch (final Exception clickError) {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
					}
					waitForUiToLoad();
					return;
				}
			}
		}

		throw new AssertionError("Unable to find clickable element with visible text in candidates: "
				+ Arrays.toString(candidateTexts));
	}

	private void waitForAnyVisibleText(final String... texts) {
		assertFalse("At least one text must be provided.", texts.length == 0);

		wait.until(anyOf(Arrays.stream(texts).map(text -> (ExpectedCondition<Boolean>) drv -> {
			final String xpath = "//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]";
			return drv.findElements(By.xpath(xpath)).stream().anyMatch(WebElement::isDisplayed);
		}).collect(Collectors.toList())));
	}

	private void waitForInputByLabel(final String label) {
		final String selector = "//label[contains(normalize-space(.), " + xpathLiteral(label)
				+ ")]/following::input[1] | //input[@placeholder=" + xpathLiteral(label) + "]";
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(selector)));
	}

	private void typeIntoInputByLabel(final String label, final String text) {
		final String selector = "//label[contains(normalize-space(.), " + xpathLiteral(label)
				+ ")]/following::input[1] | //input[@placeholder=" + xpathLiteral(label) + "]";
		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(selector)));
		input.click();
		input.sendKeys(Keys.chord(Keys.CONTROL, "a"));
		input.sendKeys(Keys.DELETE);
		input.sendKeys(text);
		waitForUiToLoad();
	}

	private WebElement findSectionWithHeading(final String headingText) {
		final String sectionXpath = "//*[self::section or self::div or self::article][.//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::h6]"
				+ "[contains(normalize-space(.), " + xpathLiteral(headingText) + ")]]";
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(sectionXpath)));
	}

	private void assertVisibleInSection(final WebElement section, final String text) {
		final List<WebElement> matches = section.findElements(By.xpath(".//*[contains(normalize-space(.), "
				+ xpathLiteral(text) + ")]"));
		final boolean visible = matches.stream().anyMatch(WebElement::isDisplayed);
		assertTrue("Expected to find text '" + text + "' in section.", visible);
	}

	private boolean isAnyTextVisible(final Duration timeout, final String... texts) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			shortWait.until(anyOf(Arrays.stream(texts).map(text -> (ExpectedCondition<Boolean>) drv -> {
				final String xpath = "//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]";
				return drv.findElements(By.xpath(xpath)).stream().anyMatch(WebElement::isDisplayed);
			}).collect(Collectors.toList())));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		wait.until(driver -> {
			final List<WebElement> loadingElements = driver.findElements(
					By.xpath("//*[contains(@class,'loading') or contains(@class,'spinner') or @aria-busy='true']"));
			return loadingElements.stream().noneMatch(WebElement::isDisplayed);
		});
	}

	private String takeScreenshot(final String checkpointName) throws IOException {
		final Path target = screenshotDir.resolve(checkpointName + ".png");
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		return target.toAbsolutePath().toString();
	}

	private void takeScreenshotQuietly(final String checkpointName) {
		try {
			takeScreenshot(checkpointName);
		} catch (final Exception ignored) {
			// Best effort only.
		}
	}

	private String readEnv(final String name, final String defaultValue) {
		final String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return value;
	}

	private Optional<String> findNewHandle(final Set<String> previousHandles, final Set<String> currentHandles) {
		for (final String handle : currentHandles) {
			if (!previousHandles.contains(handle)) {
				return Optional.of(handle);
			}
		}
		return Optional.empty();
	}

	private String sanitizeFileName(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private boolean containsEmail(final String value) {
		final Matcher matcher = EMAIL_PATTERN.matcher(value);
		return matcher.find();
	}

	private boolean containsLikelyUserName(final String value) {
		final Set<String> lines = new LinkedHashSet<>(Arrays.asList(value.split("\\R")));
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.contains("@")) {
				continue;
			}
			if (!LETTERS_PATTERN.matcher(trimmed).matches()) {
				continue;
			}
			if (trimmed.equalsIgnoreCase("Información General") || trimmed.equalsIgnoreCase("BUSINESS PLAN")
					|| trimmed.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}
			return true;
		}
		return false;
	}

	private int visibleTextLength() {
		final String text = driver.findElement(By.tagName("body")).getText();
		return text == null ? 0 : text.trim().length();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	@SafeVarargs
	private final ExpectedCondition<Boolean> anyOf(final ExpectedCondition<Boolean>... conditions) {
		return anyOf(Arrays.asList(conditions));
	}

	private ExpectedCondition<Boolean> anyOf(final List<ExpectedCondition<Boolean>> conditions) {
		return drv -> {
			for (final ExpectedCondition<Boolean> condition : conditions) {
				try {
					if (Boolean.TRUE.equals(condition.apply(drv))) {
						return true;
					}
				} catch (final Exception ignored) {
					// Continue evaluating additional conditions.
				}
			}
			return false;
		};
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepReport {
		final boolean passed;
		final String detail;

		private StepReport(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}

		static StepReport pass() {
			return new StepReport(true, null);
		}

		static StepReport fail(final String detail) {
			return new StepReport(false, detail == null ? "No detail provided." : detail);
		}
	}
}
