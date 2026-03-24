package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end automation for SaleADS Mi Negocio workflow.
 *
 * <p>
 * This test is opt-in and skipped by default. Enable with:
 *
 * <pre>
 * mvn -Dtest=SaleadsMiNegocioFullWorkflowE2ETest \
 *     -Dsaleads.e2e.enabled=true \
 *     -Dsaleads.login.url=https://your-environment.example/login \
 *     test
 * </pre>
 */
public class SaleadsMiNegocioFullWorkflowE2ETest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "T\u00e9rminos y Condiciones";
	private static final String STEP_PRIVACY = "Pol\u00edtica de Privacidad";
	private static final String EXPECTED_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws Exception {
		final boolean enabled = getBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false);
		Assume.assumeTrue("SaleADS E2E is disabled. Set -Dsaleads.e2e.enabled=true to run.", enabled);

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().window().setSize(new Dimension(1600, 1000));
		screenshotsDir = createScreenshotsDir();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		initializeReport();
		openStartPageIfProvided();
		appWindowHandle = driver.getWindowHandle();

		runStep(STEP_LOGIN, this::stepLoginWithGoogle);
		runStep(STEP_MENU, this::stepOpenMiNegocioMenu);
		runStep(STEP_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(STEP_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		runStep(STEP_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(STEP_ACCOUNT_DETAILS, this::stepValidateDetallesCuenta);
		runStep(STEP_BUSINESSES, this::stepValidateTusNegocios);
		runStep(STEP_TERMS, this::stepValidateTerminos);
		runStep(STEP_PRIVACY, this::stepValidatePoliticaPrivacidad);

		final String summary = buildSummary();
		System.out.println(summary);

		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(Map.Entry::getKey).toList();
		Assert.assertTrue("SaleADS Mi Negocio workflow failed.\n" + summary, failedSteps.isEmpty());
	}

	private void initializeReport() {
		report.put(STEP_LOGIN, StepResult.notExecuted());
		report.put(STEP_MENU, StepResult.notExecuted());
		report.put(STEP_MODAL, StepResult.notExecuted());
		report.put(STEP_ADMIN_VIEW, StepResult.notExecuted());
		report.put(STEP_INFO_GENERAL, StepResult.notExecuted());
		report.put(STEP_ACCOUNT_DETAILS, StepResult.notExecuted());
		report.put(STEP_BUSINESSES, StepResult.notExecuted());
		report.put(STEP_TERMS, StepResult.notExecuted());
		report.put(STEP_PRIVACY, StepResult.notExecuted());
	}

	private void openStartPageIfProvided() {
		final String startUrl = getConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		if (startUrl != null && !startUrl.isBlank()) {
			driver.navigate().to(startUrl.trim());
		}
		waitForUiToLoad();
	}

	private void stepLoginWithGoogle() {
		clickByVisibleTextAndWait("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google",
				"Ingresar con Google", "Google");
		selectExpectedGoogleAccountIfPrompted();
		waitForUiToLoad();

		// Main application interface + left sidebar validation.
		waitForAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Panel");
		waitForAnyVisibleElement(By.xpath("//aside//*[normalize-space(.)!='']"), By.xpath("//nav//*[normalize-space(.)!='']"));

		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() {
		ensureMiNegocioMenuExpanded();
		waitForAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Administrar Negocios");
		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleTextAndWait("Agregar Negocio");
		waitForAnyVisibleText("Crear Nuevo Negocio");

		final WebElement businessNameInput = waitForAnyVisibleElement(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//*[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));

		waitForAnyVisibleText("Tienes 2 de 3 negocios");
		waitForAnyVisibleText("Cancelar");
		waitForAnyVisibleText("Crear Negocio");
		takeScreenshot("03_agregar_negocio_modal");

		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizaci\u00f3n");
		clickByVisibleTextAndWait("Cancelar");
		waitForAnyInvisibleText("Crear Nuevo Negocio");
	}

	private void stepOpenAdministrarNegocios() {
		ensureMiNegocioMenuExpanded();
		clickByVisibleTextAndWait("Administrar Negocios");
		waitForAnyVisibleText("Informaci\u00f3n General");
		waitForAnyVisibleText("Detalles de la Cuenta");
		waitForAnyVisibleText("Tus Negocios");
		waitForAnyVisibleText("Secci\u00f3n Legal");
		takeScreenshot("04_administrar_negocios_page");
	}

	private void stepValidateInformacionGeneral() {
		waitForAnyVisibleText("Informaci\u00f3n General");
		waitForAnyVisibleText("BUSINESS PLAN");
		waitForAnyVisibleText("Cambiar Plan");

		final String allVisibleText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		final boolean expectedEmailVisible = allVisibleText.contains(EXPECTED_GOOGLE_ACCOUNT);
		final boolean anyEmailVisible = Arrays.stream(allVisibleText.split("\\s+")).anyMatch(token -> EMAIL_PATTERN.matcher(token).matches());
		Assert.assertTrue("Expected user email must be visible in Informaci\u00f3n General.", expectedEmailVisible || anyEmailVisible);

		final boolean usernameLikeTextVisible = hasVisibleTextMatching(
				"(?i)^(?!informaci[o\u00f3]n$)(?!general$)(?!business$)(?!plan$)(?!cambiar$)[a-z][a-z .'-]{2,}$");
		Assert.assertTrue("A user name-like text must be visible in Informaci\u00f3n General.", usernameLikeTextVisible);
	}

	private void stepValidateDetallesCuenta() {
		waitForAnyVisibleText("Detalles de la Cuenta");
		waitForAnyVisibleText("Cuenta creada");
		waitForAnyVisibleText("Estado activo");
		waitForAnyVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitForAnyVisibleText("Tus Negocios");
		waitForAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Tienes 2 de 3 negocios");

		final WebElement section = waitForAnyVisibleElement(
				By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]"),
				By.xpath("//section[.//*[contains(normalize-space(.), 'Tus Negocios')]]"));

		final long visibleNodes = section.findElements(By.xpath(".//*[normalize-space(.)!='']")).stream().filter(WebElement::isDisplayed)
				.count();
		Assert.assertTrue("Business list should be visible in Tus Negocios section.", visibleNodes >= 3);
	}

	private void stepValidateTerminos() {
		final String url = openLegalLinkAndValidate(new String[] { "T\u00e9rminos y Condiciones", "Terminos y Condiciones" },
				new String[] { "T\u00e9rminos y Condiciones", "Terminos y Condiciones" }, "05_terminos_condiciones");
		legalUrls.put(STEP_TERMS, url);
	}

	private void stepValidatePoliticaPrivacidad() {
		final String url = openLegalLinkAndValidate(new String[] { "Pol\u00edtica de Privacidad", "Politica de Privacidad" },
				new String[] { "Pol\u00edtica de Privacidad", "Politica de Privacidad" }, "06_politica_privacidad");
		legalUrls.put(STEP_PRIVACY, url);
	}

	private String openLegalLinkAndValidate(final String[] linkTexts, final String[] headingTexts, final String screenshotName) {
		final String currentHandle = driver.getWindowHandle();
		final String oldUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleTextAndWait(linkTexts);

		final String targetHandle = waitForNewWindowHandle(handlesBeforeClick).orElse(currentHandle);
		final boolean switchedWindow = !targetHandle.equals(currentHandle);

		if (switchedWindow) {
			driver.switchTo().window(targetHandle);
			waitForUiToLoad();
		}

		waitForAnyVisibleText(headingTexts);
		final String pageText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		Assert.assertTrue("Legal content text should be visible.", pageText.length() > 120);

		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();
		final boolean navigated = switchedWindow || !oldUrl.equals(finalUrl);
		Assert.assertTrue("Legal link must open a new tab/window or navigate to a different URL.", navigated);

		if (switchedWindow) {
			driver.close();
			driver.switchTo().window(currentHandle);
			waitForUiToLoad();
		} else {
			if (!oldUrl.equals(finalUrl)) {
				driver.navigate().back();
				waitForUiToLoad();
			}
		}

		// Ensure we always return to the main app tab.
		if (!driver.getWindowHandle().equals(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void ensureMiNegocioMenuExpanded() {
		if (isAnyTextVisible("Agregar Negocio") && isAnyTextVisible("Administrar Negocios")) {
			return;
		}

		if (isAnyTextVisible("Mi Negocio")) {
			clickByVisibleTextAndWait("Mi Negocio");
		} else {
			clickByVisibleTextAndWait("Negocio");
			if (isAnyTextVisible("Mi Negocio")) {
				clickByVisibleTextAndWait("Mi Negocio");
			}
		}

		waitForAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Administrar Negocios");
	}

	private void selectExpectedGoogleAccountIfPrompted() {
		try {
			waitForUiToLoad();

			final Optional<String> maybeGoogleWindow = waitForNewWindowHandle(Set.of(driver.getWindowHandle()));
			if (maybeGoogleWindow.isPresent()) {
				driver.switchTo().window(maybeGoogleWindow.get());
				waitForUiToLoad();
			}

			if (isAnyTextVisible(EXPECTED_GOOGLE_ACCOUNT)) {
				clickByVisibleTextAndWait(EXPECTED_GOOGLE_ACCOUNT);
			}

			// Return to app tab/window if available.
			if (!driver.getWindowHandle().equals(appWindowHandle) && driver.getWindowHandles().contains(appWindowHandle)) {
				driver.switchTo().window(appWindowHandle);
				waitForUiToLoad();
			}
		} catch (final Exception ignored) {
			// Account chooser is optional depending on current authentication state.
		}
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.execute();
			report.put(stepName, StepResult.pass());
		} catch (final Throwable t) {
			report.put(stepName, StepResult.fail(t.getClass().getSimpleName() + ": " + t.getMessage()));
			takeScreenshot("FAIL_" + sanitizeFileName(stepName));
		}
	}

	private String buildSummary() {
		final StringBuilder sb = new StringBuilder();
		sb.append(System.lineSeparator());
		sb.append("SaleADS Mi Negocio workflow report").append(System.lineSeparator());
		sb.append("=================================").append(System.lineSeparator());
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			sb.append(entry.getKey()).append(": ").append(entry.getValue().passed ? "PASS" : "FAIL");
			if (!entry.getValue().detail.isBlank()) {
				sb.append(" - ").append(entry.getValue().detail);
			}
			sb.append(System.lineSeparator());
		}
		if (!legalUrls.isEmpty()) {
			sb.append(System.lineSeparator()).append("Final URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				sb.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}
		sb.append(System.lineSeparator());
		sb.append("Screenshots dir: ").append(screenshotsDir.toAbsolutePath());
		return sb.toString();
	}

	private WebDriver createDriver() throws MalformedURLException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--window-size=1600,1000");

		if (getBooleanConfig("saleads.e2e.headless", "SALEADS_E2E_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = getConfig("saleads.selenium.remote.url", "SALEADS_SELENIUM_REMOTE_URL");
		if (remoteUrl != null && !remoteUrl.isBlank()) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}

		return new ChromeDriver(options);
	}

	private Path createScreenshotsDir() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT).format(LocalDateTime.now());
		final Path path = Path.of("target", "saleads-e2e-screenshots", timestamp);
		Files.createDirectories(path);
		return path;
	}

	private void clickByVisibleTextAndWait(final String... texts) {
		final WebElement element = waitForClickableByVisibleText(texts);
		scrollIntoView(element);
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private WebElement waitForClickableByVisibleText(final String... texts) {
		return wait.until(driverRef -> {
			WebElement bestFallback = null;
			int bestFallbackTextLength = Integer.MAX_VALUE;

			for (final String text : texts) {
				for (final By locator : clickableTextLocators(text)) {
					final List<WebElement> candidates = driverRef.findElements(locator).stream().filter(WebElement::isDisplayed).toList();
					for (final WebElement candidate : candidates) {
						try {
							if (candidate.isEnabled()) {
								if (isLikelyClickable(candidate)) {
									return candidate;
								}

								final String tagName = Optional.ofNullable(candidate.getTagName()).orElse("").toLowerCase(Locale.ROOT);
								if ("html".equals(tagName) || "body".equals(tagName)) {
									continue;
								}

								final int textLength = normalizeWhitespace(candidate.getText()).length();
								if (textLength < bestFallbackTextLength) {
									bestFallback = candidate;
									bestFallbackTextLength = textLength;
								}
							}
						} catch (final Exception ignored) {
							// Continue with the next candidate.
						}
					}
				}
			}
			return bestFallback;
		});
	}

	private WebElement waitForAnyVisibleElement(final By... locators) {
		return wait.until(driverRef -> {
			for (final By locator : locators) {
				final List<WebElement> elements = driverRef.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private void waitForAnyVisibleText(final String... texts) {
		wait.until(driverRef -> isAnyTextVisible(texts));
	}

	private void waitForAnyInvisibleText(final String... texts) {
		wait.until(driverRef -> !isAnyTextVisible(texts));
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			for (final By locator : textLocators(text)) {
				for (final WebElement element : driver.findElements(locator)) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private List<By> textLocators(final String rawText) {
		final String exact = "//*[normalize-space(.)=" + xpathLiteral(rawText) + "]";
		final String contains = "//*[contains(normalize-space(.), " + xpathLiteral(rawText) + ")]";
		return Arrays.asList(By.xpath(exact), By.xpath(contains));
	}

	private List<By> clickableTextLocators(final String rawText) {
		final String literal = xpathLiteral(rawText);
		final String matchText = "(normalize-space(.)=" + literal + " or contains(normalize-space(.), " + literal + "))";
		final String matchValue = "(@value=" + literal + " or contains(@value, " + literal + "))";

		return Arrays.asList(
				By.xpath("//button[" + matchText + "]"),
				By.xpath("//a[" + matchText + "]"),
				By.xpath("//input[(@type='button' or @type='submit' or @type='reset') and " + matchValue + "]"),
				By.xpath("//*[@role='button' or @role='menuitem' or @role='link' or @role='tab'][" + matchText + "]"),
				By.xpath("//*[self::span or self::div or self::p][" + matchText
						+ "]/ancestor-or-self::*[self::button or self::a or @role='button' or @role='menuitem' or @role='link' or @onclick][1]"),
				By.xpath("//*[" + matchText + "]"));
	}

	private boolean isLikelyClickable(final WebElement element) {
		final String tagName = Optional.ofNullable(element.getTagName()).orElse("").toLowerCase(Locale.ROOT);
		if ("button".equals(tagName) || "a".equals(tagName)) {
			return true;
		}

		if ("input".equals(tagName)) {
			final String type = Optional.ofNullable(element.getAttribute("type")).orElse("").toLowerCase(Locale.ROOT);
			if ("button".equals(type) || "submit".equals(type) || "reset".equals(type) || "checkbox".equals(type)
					|| "radio".equals(type)) {
				return true;
			}
		}

		final String role = Optional.ofNullable(element.getAttribute("role")).orElse("").toLowerCase(Locale.ROOT);
		if ("button".equals(role) || "menuitem".equals(role) || "link".equals(role) || "tab".equals(role)) {
			return true;
		}

		final String onClick = Optional.ofNullable(element.getAttribute("onclick")).orElse("");
		if (!onClick.isBlank()) {
			return true;
		}

		final String classes = Optional.ofNullable(element.getAttribute("class")).orElse("").toLowerCase(Locale.ROOT);
		return classes.contains("btn") || classes.contains("button");
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
				sb.append(",");
			}
			if (chars[i] == '\'') {
				sb.append("\"'\"");
			} else {
				sb.append("'").append(chars[i]).append("'");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	private Optional<String> waitForNewWindowHandle(final Set<String> previousHandles) {
		try {
			return Optional.ofNullable(new WebDriverWait(driver, Duration.ofSeconds(10)).until(driverRef -> {
				for (final String handle : driverRef.getWindowHandles()) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			}));
		} catch (final TimeoutException timeout) {
			return Optional.empty();
		}
	}

	private void waitForUiToLoad() {
		wait.until(driverRef -> {
			try {
				final String readyState = String
						.valueOf(((JavascriptExecutor) driverRef).executeScript("return document.readyState;"));
				return "complete".equalsIgnoreCase(readyState) || "interactive".equalsIgnoreCase(readyState);
			} catch (final Exception e) {
				return true;
			}
		});

		try {
			new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.or(
					ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading")),
					ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".spinner")),
					ExpectedConditions.invisibilityOfElementLocated(By.cssSelector("[aria-busy='true']"))));
		} catch (final Exception ignored) {
			// Ignore missing loaders or short-lived transitions.
		}
	}

	private boolean hasVisibleTextMatching(final String regex) {
		final Pattern pattern = Pattern.compile(regex);
		final List<WebElement> allVisibleElements = driver.findElements(By.xpath("//*[normalize-space(.)!='']")).stream()
				.filter(WebElement::isDisplayed).toList();
		for (final WebElement element : allVisibleElements) {
			final String text = normalizeWhitespace(element.getText());
			if (!text.isBlank() && text.length() < 80 && pattern.matcher(text).matches()) {
				return true;
			}
		}
		return false;
	}

	private void takeScreenshot(final String name) {
		try {
			final File image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path target = screenshotsDir.resolve(sanitizeFileName(name) + ".png");
			Files.copy(image.toPath(), target);
		} catch (final Exception ignored) {
			// Ignore screenshot capture issues to avoid masking the original assertion.
		}
	}

	private void scrollIntoView(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		} catch (final Exception ignored) {
			// Non-fatal helper.
		}
	}

	private String sanitizeFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	private String normalizeWhitespace(final String value) {
		return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	private String getConfig(final String propertyName, final String envName) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return null;
	}

	private boolean getBooleanConfig(final String propertyName, final String envName, final boolean defaultValue) {
		final String raw = getConfig(propertyName, envName);
		return raw == null ? defaultValue : Boolean.parseBoolean(raw);
	}

	@FunctionalInterface
	private interface StepAction {
		void execute();
	}

	private static final class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail == null ? "" : detail;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String detail) {
			return new StepResult(false, detail);
		}

		private static StepResult notExecuted() {
			return new StepResult(false, "NOT EXECUTED");
		}
	}
}
