package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Términos y Condiciones";
	private static final String STEP_PRIVACY = "Política de Privacidad";

	private static final String REQUIRED_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
			Pattern.CASE_INSENSITIVE);

	private static final DateTimeFormatter RUN_FOLDER_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor javascript;
	private Path artifactsDirectory;
	private String termsFinalUrl = "";
	private String privacyFinalUrl = "";

	@Before
	public void setUp() throws Exception {
		assumeTrue("Set SALEADS_E2E_ENABLED=true (or -Dsaleads.e2e.enabled=true) to run this optional E2E test.",
				readBooleanSetting("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false));

		artifactsDirectory = Files.createDirectories(
				Path.of("target", "saleads-e2e-artifacts", RUN_FOLDER_TIME.format(Instant.now())));

		driver = createWebDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(readIntSetting("saleads.ui.timeout.seconds",
				"SALEADS_UI_TIMEOUT_SECONDS", 30)));

		if (driver instanceof JavascriptExecutor) {
			javascript = (JavascriptExecutor) driver;
		}

		driver.manage().window().setSize(new Dimension(1440, 1080));

		final String loginUrl = readTextSetting("saleads.login.url", "SALEADS_LOGIN_URL");
		if (hasText(loginUrl)) {
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
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final Map<String, StepResult> report = newLinkedReport();

		final boolean loginSuccess = executeStep(report, STEP_LOGIN, this::runLoginStep);
		if (!loginSuccess) {
			markBlocked(report, STEP_MENU, "Blocked because login did not complete.");
			markBlocked(report, STEP_MODAL, "Blocked because login did not complete.");
			markBlocked(report, STEP_ADMIN_VIEW, "Blocked because login did not complete.");
			markBlocked(report, STEP_INFO_GENERAL, "Blocked because login did not complete.");
			markBlocked(report, STEP_ACCOUNT_DETAILS, "Blocked because login did not complete.");
			markBlocked(report, STEP_BUSINESSES, "Blocked because login did not complete.");
			markBlocked(report, STEP_TERMS, "Blocked because login did not complete.");
			markBlocked(report, STEP_PRIVACY, "Blocked because login did not complete.");
		} else {
			final boolean menuSuccess = executeStep(report, STEP_MENU, this::runMiNegocioMenuStep);
			if (menuSuccess) {
				executeStep(report, STEP_MODAL, this::runAgregarNegocioModalStep);
			} else {
				markBlocked(report, STEP_MODAL, "Blocked because Mi Negocio menu was not available.");
			}

			final boolean adminSuccess = executeStep(report, STEP_ADMIN_VIEW, this::runAdministrarNegociosViewStep);
			if (!adminSuccess) {
				markBlocked(report, STEP_INFO_GENERAL, "Blocked because account view did not load.");
				markBlocked(report, STEP_ACCOUNT_DETAILS, "Blocked because account view did not load.");
				markBlocked(report, STEP_BUSINESSES, "Blocked because account view did not load.");
				markBlocked(report, STEP_TERMS, "Blocked because account view did not load.");
				markBlocked(report, STEP_PRIVACY, "Blocked because account view did not load.");
			} else {
				executeStep(report, STEP_INFO_GENERAL, this::runInformacionGeneralValidationStep);
				executeStep(report, STEP_ACCOUNT_DETAILS, this::runDetallesCuentaValidationStep);
				executeStep(report, STEP_BUSINESSES, this::runTusNegociosValidationStep);
				executeStep(report, STEP_TERMS, this::runTerminosValidationStep);
				executeStep(report, STEP_PRIVACY, this::runPoliticaPrivacidadValidationStep);
			}
		}

		final Path reportPath = writeFinalReport(report);
		assertTrue("Workflow validations failed. Report: " + reportPath + " | Failing steps: " + buildFailedStepSummary(report),
				allStepsPassed(report));
	}

	private void runLoginStep() throws Exception {
		assertCondition(clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Google"), "Could not find a Google login button.");

		selectGoogleAccountIfPresented(REQUIRED_GOOGLE_ACCOUNT);

		waitForAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Inicio");
		assertCondition(isElementVisible(By.xpath("//aside | //nav")), "Main app shell did not load.");
		assertCondition(
				isElementVisible(
						By.xpath("//aside//*[contains(normalize-space(), 'Negocio')] | //nav//*[contains(normalize-space(), 'Negocio')]")),
				"Sidebar navigation was not visible.");

		captureScreenshot("01-dashboard-loaded");
	}

	private void runMiNegocioMenuStep() throws Exception {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertVisibleAnyText("Agregar Negocio");
		assertVisibleAnyText("Administrar Negocios");

		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void runAgregarNegocioModalStep() throws Exception {
		clickByVisibleText("Agregar Negocio");

		assertVisibleAnyText("Crear Nuevo Negocio");
		assertCondition(findBusinessNameInput() != null, "Input field 'Nombre del Negocio' was not found.");
		assertVisibleAnyText("Tienes 2 de 3 negocios");
		assertVisibleAnyText("Cancelar");
		assertVisibleAnyText("Crear Negocio");

		captureScreenshot("03-agregar-negocio-modal");

		final WebElement input = findBusinessNameInput();
		if (input != null) {
			clickAndWait(input);
			input.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
			input.sendKeys("Negocio Prueba Automatización");
		}

		clickByVisibleText("Cancelar");
		waitUntilTextDisappears("Crear Nuevo Negocio");
	}

	private void runAdministrarNegociosViewStep() throws Exception {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForAnyVisibleText("Información General", "Detalles de la Cuenta", "Tus Negocios");

		assertVisibleAnyText("Información General");
		assertVisibleAnyText("Detalles de la Cuenta");
		assertVisibleAnyText("Tus Negocios");
		assertVisibleAnyText("Sección Legal", "Seccion Legal");

		captureScreenshot("04-administrar-negocios-view");
	}

	private void runInformacionGeneralValidationStep() {
		final WebElement section = findSectionContainer("Información General");
		final String text = normalizeWhitespace(section.getText());

		assertCondition(hasVisibleUsername(section, text), "User name is not visible in 'Información General'.");
		assertCondition(findEmail(text) != null, "User email is not visible in 'Información General'.");
		assertCondition(text.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN"),
				"'BUSINESS PLAN' text is not visible in 'Información General'.");
		assertCondition(text.contains("Cambiar Plan"), "'Cambiar Plan' button is not visible in 'Información General'.");
	}

	private void runDetallesCuentaValidationStep() {
		final WebElement section = findSectionContainer("Detalles de la Cuenta");
		final String text = normalizeWhitespace(section.getText());

		assertCondition(text.contains("Cuenta creada"), "'Cuenta creada' is not visible.");
		assertCondition(text.contains("Estado activo"), "'Estado activo' is not visible.");
		assertCondition(text.contains("Idioma seleccionado"), "'Idioma seleccionado' is not visible.");
	}

	private void runTusNegociosValidationStep() {
		final WebElement section = findSectionContainer("Tus Negocios");
		final String text = normalizeWhitespace(section.getText());

		assertCondition(isBusinessListVisible(section, text), "Business list is not visible in 'Tus Negocios'.");
		assertCondition(text.contains("Agregar Negocio"), "'Agregar Negocio' is not visible in 'Tus Negocios'.");
		assertCondition(text.contains("Tienes 2 de 3 negocios"),
				"'Tienes 2 de 3 negocios' is not visible in 'Tus Negocios'.");
	}

	private void runTerminosValidationStep() throws Exception {
		termsFinalUrl = openLegalDocumentAndReturn("Términos y Condiciones", "Términos y Condiciones",
				"05-terminos-y-condiciones");
	}

	private void runPoliticaPrivacidadValidationStep() throws Exception {
		privacyFinalUrl = openLegalDocumentAndReturn("Política de Privacidad", "Política de Privacidad",
				"06-politica-privacidad");
	}

	private String openLegalDocumentAndReturn(final String linkText, final String expectedHeading, final String screenshotName)
			throws Exception {
		final String appHandle = driver.getWindowHandle();
		final String currentUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);

		waitForNavigationOrNewTab(currentUrl, handlesBefore);
		final Set<String> handlesAfter = driver.getWindowHandles();
		final boolean newTabOpened = handlesAfter.size() > handlesBefore.size();

		if (newTabOpened) {
			switchToNewTab(handlesBefore, handlesAfter);
		}

		waitForAnyVisibleText(expectedHeading);
		assertLegalContentVisible(expectedHeading);
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (newTabOpened) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		waitForAnyVisibleText("Información General", "Sección Legal", "Seccion Legal");
		return finalUrl;
	}

	private void assertLegalContentVisible(final String expectedHeading) {
		final String bodyText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText()).toLowerCase(Locale.ROOT);
		assertCondition(bodyText.contains(normalizeWhitespace(expectedHeading).toLowerCase(Locale.ROOT)),
				"Legal heading '" + expectedHeading + "' was not present.");
		assertCondition(bodyText.length() >= 140, "Legal content was not visible.");
	}

	private void selectGoogleAccountIfPresented(final String accountEmail) {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handles = new LinkedHashSet<>(driver.getWindowHandles());
		if (handles.size() > 1) {
			final String latestHandle = new ArrayList<>(handles).get(handles.size() - 1);
			if (!Objects.equals(latestHandle, originalHandle)) {
				driver.switchTo().window(latestHandle);
				waitForUiToLoad();
			}
		}

		waitForUiToLoad();
		final boolean selectedFromCurrentPage = clickByVisibleTextIfPresent(8, accountEmail);

		if (selectedFromCurrentPage) {
			waitForUiToLoad();
		}

		if (!Objects.equals(driver.getWindowHandle(), originalHandle) && driver.getWindowHandles().contains(originalHandle)) {
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		}
	}

	private WebElement findBusinessNameInput() {
		final List<By> selectors = Arrays.asList(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"));

		for (final By selector : selectors) {
			for (final WebElement element : driver.findElements(selector)) {
				if (isDisplayed(element)) {
					return element;
				}
			}
		}

		return null;
	}

	private boolean isBusinessListVisible(final WebElement section, final String sectionText) {
		final List<WebElement> listElements = section.findElements(
				By.xpath(".//ul/li | .//table/tbody/tr | .//article | .//div[contains(@class, 'business')]"));
		if (!listElements.isEmpty()) {
			return true;
		}

		final String lower = sectionText.toLowerCase(Locale.ROOT);
		return lower.contains("negocio") || lower.contains("business");
	}

	private boolean hasVisibleUsername(final WebElement section, final String sectionText) {
		final String expectedName = readTextSetting("saleads.expected.user.name", "SALEADS_EXPECTED_USER_NAME");
		if (hasText(expectedName)) {
			return sectionText.contains(expectedName);
		}

		final String email = findEmail(sectionText);
		for (final String line : sectionText.split("\\R")) {
			final String candidate = line.trim();
			if (!hasText(candidate)) {
				continue;
			}

			final String candidateLower = candidate.toLowerCase(Locale.ROOT);
			if (candidateLower.contains("información general") || candidateLower.contains("business plan")
					|| candidateLower.contains("cambiar plan") || candidateLower.contains("plan")) {
				continue;
			}
			if (email != null && candidate.contains(email)) {
				continue;
			}
			if (candidate.length() > 2 && candidate.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}

		final List<WebElement> textNodes = section.findElements(By.xpath(".//*[self::h1 or self::h2 or self::h3 or self::p or self::span][normalize-space()]"));
		for (final WebElement node : textNodes) {
			final String text = normalizeWhitespace(node.getText());
			if (!hasText(text) || text.contains("@")) {
				continue;
			}

			final String lower = text.toLowerCase(Locale.ROOT);
			if (lower.contains("información general") || lower.contains("business plan") || lower.contains("cambiar plan")
					|| lower.contains("plan")) {
				continue;
			}
			if (text.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}

		return false;
	}

	private String findEmail(final String text) {
		final Matcher matcher = EMAIL_PATTERN.matcher(text);
		return matcher.find() ? matcher.group() : null;
	}

	private WebElement findSectionContainer(final String heading) {
		final WebElement headingElement = waitForVisibleText(heading);
		try {
			return headingElement.findElement(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
		} catch (final Exception ignored) {
			return headingElement;
		}
	}

	private boolean executeStep(final Map<String, StepResult> report, final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, StepResult.pass("PASS"));
			return true;
		} catch (final Exception exception) {
			captureScreenshotSilently("error-" + slugify(stepName));
			report.put(stepName, StepResult.fail(exception.getMessage()));
			return false;
		}
	}

	private Path writeFinalReport(final Map<String, StepResult> report) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Workflow Report").append(System.lineSeparator());
		builder.append("Generated (UTC): ").append(Instant.now()).append(System.lineSeparator());
		builder.append(System.lineSeparator());

		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			builder.append(entry.getKey()).append(": ").append(entry.getValue().status).append(" - ")
					.append(entry.getValue().detail).append(System.lineSeparator());
		}

		builder.append(System.lineSeparator());
		builder.append("Términos y Condiciones URL: ").append(hasText(termsFinalUrl) ? termsFinalUrl : "N/A")
				.append(System.lineSeparator());
		builder.append("Política de Privacidad URL: ").append(hasText(privacyFinalUrl) ? privacyFinalUrl : "N/A")
				.append(System.lineSeparator());

		final Path reportPath = artifactsDirectory.resolve("final-report.txt");
		Files.writeString(reportPath, builder.toString(), StandardCharsets.UTF_8);
		return reportPath;
	}

	private boolean allStepsPassed(final Map<String, StepResult> report) {
		for (final StepResult result : report.values()) {
			if (!"PASS".equals(result.status)) {
				return false;
			}
		}
		return true;
	}

	private String buildFailedStepSummary(final Map<String, StepResult> report) {
		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!"PASS".equals(entry.getValue().status)) {
				failed.add(entry.getKey() + " [" + entry.getValue().status + "]: " + entry.getValue().detail);
			}
		}
		return failed.isEmpty() ? "none" : String.join(" | ", failed);
	}

	private Map<String, StepResult> newLinkedReport() {
		final Map<String, StepResult> report = new LinkedHashMap<>();
		report.put(STEP_LOGIN, StepResult.pending());
		report.put(STEP_MENU, StepResult.pending());
		report.put(STEP_MODAL, StepResult.pending());
		report.put(STEP_ADMIN_VIEW, StepResult.pending());
		report.put(STEP_INFO_GENERAL, StepResult.pending());
		report.put(STEP_ACCOUNT_DETAILS, StepResult.pending());
		report.put(STEP_BUSINESSES, StepResult.pending());
		report.put(STEP_TERMS, StepResult.pending());
		report.put(STEP_PRIVACY, StepResult.pending());
		return report;
	}

	private void markBlocked(final Map<String, StepResult> report, final String step, final String reason) {
		report.put(step, StepResult.blocked(reason));
	}

	private void waitForNavigationOrNewTab(final String currentUrl, final Set<String> handlesBefore) {
		try {
			wait.until(driver -> {
				final boolean hasNewTab = driver.getWindowHandles().size() > handlesBefore.size();
				final boolean urlChanged = !Objects.equals(driver.getCurrentUrl(), currentUrl);
				return hasNewTab || urlChanged;
			});
		} catch (final Exception ignored) {
			// some SPA links keep same URL and load content asynchronously
		}
		waitForUiToLoad();
	}

	private void switchToNewTab(final Set<String> handlesBefore, final Set<String> handlesAfter) {
		if (handlesAfter.size() <= handlesBefore.size()) {
			return;
		}

		final List<String> newHandles = handlesAfter.stream().filter(handle -> !handlesBefore.contains(handle))
				.collect(Collectors.toList());
		if (!newHandles.isEmpty()) {
			driver.switchTo().window(newHandles.get(newHandles.size() - 1));
			waitForUiToLoad();
		}
	}

	private boolean clickByVisibleText(final String... preferredTexts) throws Exception {
		final WebElement element = waitForFirstVisibleElement(Duration.ofSeconds(20), preferredTexts);
		clickAndWait(element);
		return true;
	}

	private boolean clickByVisibleTextIfPresent(final int timeoutSeconds, final String... preferredTexts) {
		try {
			final WebElement element = waitForFirstVisibleElement(Duration.ofSeconds(timeoutSeconds), preferredTexts);
			clickAndWait(element);
			return true;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private WebElement waitForFirstVisibleElement(final Duration timeout, final String... preferredTexts) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		return shortWait.until(currentDriver -> {
			for (final String text : preferredTexts) {
				final By locator = textLocator(text);
				for (final WebElement element : currentDriver.findElements(locator)) {
					if (isDisplayed(element)) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private void clickAndWait(final WebElement element) {
		scrollToElement(element);
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		if (javascript != null) {
			try {
				wait.until(driver -> "complete".equals(javascript.executeScript("return document.readyState")));
			} catch (final Exception ignored) {
				// some browser contexts may not expose ready state during transitions
			}

			try {
				wait.until(driver -> {
					final Object active = javascript.executeScript("return window.jQuery ? jQuery.active : 0;");
					return active instanceof Number && ((Number) active).longValue() == 0L;
				});
			} catch (final Exception ignored) {
				// jQuery might not be present
			}
		}

		sleep(500);
	}

	private void waitUntilTextDisappears(final String text) {
		try {
			wait.until(ExpectedConditions.invisibilityOfElementLocated(textLocator(text)));
		} catch (final Exception ignored) {
			// modal may have already disappeared
		}
	}

	private void waitForAnyVisibleText(final String... texts) {
		final WebDriverWait textWait = new WebDriverWait(driver, Duration.ofSeconds(35));
		textWait.until(currentDriver -> {
			for (final String text : texts) {
				if (isTextVisible(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private void assertVisibleAnyText(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return;
			}
		}
		throw new IllegalStateException("None of the expected texts were visible: " + Arrays.toString(texts));
	}

	private boolean isTextVisible(final String text) {
		for (final WebElement element : driver.findElements(textLocator(text))) {
			if (isDisplayed(element)) {
				return true;
			}
		}
		return false;
	}

	private WebElement waitForVisibleText(final String text) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(textLocator(text)));
	}

	private By textLocator(final String text) {
		return By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]");
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final String[] parts = text.split("'");
		for (int index = 0; index < parts.length; index++) {
			builder.append("'").append(parts[index]).append("'");
			if (index < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private boolean isElementVisible(final By by) {
		for (final WebElement element : driver.findElements(by)) {
			if (isDisplayed(element)) {
				return true;
			}
		}
		return false;
	}

	private boolean isDisplayed(final WebElement element) {
		try {
			return element != null && element.isDisplayed();
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void scrollToElement(final WebElement element) {
		if (javascript != null) {
			try {
				javascript.executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
				return;
			} catch (final Exception ignored) {
				// fall back to standard click path
			}
		}
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = artifactsDirectory.resolve(checkpointName + ".png");
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureScreenshotSilently(final String checkpointName) {
		try {
			captureScreenshot(checkpointName);
		} catch (final Exception ignored) {
			// best effort evidence capture
		}
	}

	private WebDriver createWebDriver() throws MalformedURLException {
		final String remoteUrl = readTextSetting("saleads.remote.webdriver.url", "SALEADS_REMOTE_WEBDRIVER_URL");
		final boolean headless = readBooleanSetting("saleads.headless", "SALEADS_HEADLESS", true);
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1440,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (headless) {
			options.addArguments("--headless=new");
		}

		if (hasText(remoteUrl)) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}

		return new ChromeDriver(options);
	}

	private int readIntSetting(final String systemProperty, final String envVariable, final int defaultValue) {
		final String value = readTextSetting(systemProperty, envVariable);
		if (!hasText(value)) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (final NumberFormatException exception) {
			return defaultValue;
		}
	}

	private boolean readBooleanSetting(final String systemProperty, final String envVariable, final boolean defaultValue) {
		final String value = readTextSetting(systemProperty, envVariable);
		if (!hasText(value)) {
			return defaultValue;
		}
		return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
	}

	private String readTextSetting(final String systemProperty, final String envVariable) {
		final String byProperty = System.getProperty(systemProperty);
		if (hasText(byProperty)) {
			return byProperty.trim();
		}

		final String byEnvironment = System.getenv(envVariable);
		return hasText(byEnvironment) ? byEnvironment.trim() : null;
	}

	private boolean hasText(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	private void assertCondition(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private String normalizeWhitespace(final String text) {
		return text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	private String slugify(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final String status;
		private final String detail;

		private StepResult(final String status, final String detail) {
			this.status = status;
			this.detail = detail;
		}

		private static StepResult pass(final String detail) {
			return new StepResult("PASS", detail);
		}

		private static StepResult fail(final String detail) {
			return new StepResult("FAIL", detail == null ? "No detail available." : detail);
		}

		private static StepResult blocked(final String detail) {
			return new StepResult("FAIL", detail);
		}

		private static StepResult pending() {
			return new StepResult("FAIL", "Not executed.");
		}
	}
}
