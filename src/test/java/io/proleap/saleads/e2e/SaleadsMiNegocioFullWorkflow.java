package io.proleap.saleads.e2e;

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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
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

/**
 * Portable Selenium workflow runner for validating SaleADS Mi Negocio features.
 *
 * <p>
 * Configuration:
 * </p>
 * <ul>
 * <li>SALEADS_LOGIN_URL or -Dsaleads.login.url=https://... (required unless attaching to an existing browser)</li>
 * <li>SALEADS_DEBUGGER_ADDRESS or -Dsaleads.debugger.address=127.0.0.1:9222 (optional)</li>
 * <li>SALEADS_HEADLESS or -Dsaleads.headless=true|false (default false)</li>
 * <li>SALEADS_TIMEOUT_SECONDS or -Dsaleads.timeout.seconds=30 (default 30)</li>
 * </ul>
 *
 * <p>
 * Screenshots are saved to target/saleads-evidence/&lt;timestamp&gt;.
 * </p>
 */
public class SaleadsMiNegocioFullWorkflow {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Duration timeout;
	private final Path evidenceDir;

	private WebDriver driver;
	private WebDriverWait wait;
	private String appWindowHandle;
	private String termsAndConditionsUrl = "N/A";
	private String privacyPolicyUrl = "N/A";

	public SaleadsMiNegocioFullWorkflow() throws IOException {
		timeout = Duration.ofSeconds(resolveTimeoutSeconds());
		evidenceDir = createEvidenceDirectory();
	}

	public static void main(final String[] args) throws Exception {
		final SaleadsMiNegocioFullWorkflow workflow = new SaleadsMiNegocioFullWorkflow();
		final boolean success = workflow.execute();
		System.exit(success ? 0 : 1);
	}

	public boolean execute() {
		boolean success;
		try {
			initDriver();
			runSteps();
		} catch (final Exception e) {
			recordFailure("Login", "Unexpected setup failure: " + safeMessage(e));
		} finally {
			printFinalReport();
			quitDriver();
		}

		success = allStepsPassed();
		if (!success) {
			throw new IllegalStateException("One or more validations failed. Review the final report and screenshots.");
		}
		return true;
	}

	private void runSteps() {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		executeStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Login with Google");
		handleGoogleAccountSelection();

		waitForSidebar();
		takeScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleTextIfPresent("Negocio");
		clickByVisibleText("Mi Negocio");
		assertTextVisible("Agregar Negocio", "Agregar Negocio is not visible after expanding Mi Negocio.");
		assertTextVisible("Administrar Negocios", "Administrar Negocios is not visible after expanding Mi Negocio.");
		takeScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio", "Modal title 'Crear Nuevo Negocio' is not visible.");
		assertInputExists("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios", "Business limit text is not visible.");
		assertTextVisible("Cancelar", "Button 'Cancelar' is not visible.");
		assertTextVisible("Crear Negocio", "Button 'Crear Negocio' is not visible.");
		takeScreenshot("03-agregar-negocio-modal.png");

		typeInInputIfPresent("Nombre del Negocio", "Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(4))) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		assertTextVisible("Información General", "Section 'Información General' is missing.");
		assertTextVisible("Detalles de la Cuenta", "Section 'Detalles de la Cuenta' is missing.");
		assertTextVisible("Tus Negocios", "Section 'Tus Negocios' is missing.");
		assertTextVisible("Sección Legal", "Section 'Sección Legal' is missing.");
		takeScreenshot("04-administrar-negocios-view.png");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("Información General", "Section header 'Información General' is missing.");
		final String pageText = normalizedText(driver.findElement(By.tagName("body")).getText());
		assertCondition(findAnyEmail(pageText) != null, "User email was not detected in the account page.");
		assertCondition(isUserNameVisibleNearEmail(), "User name was not detected near the email.");
		assertTextVisible("BUSINESS PLAN", "Text 'BUSINESS PLAN' is not visible.");
		assertTextVisible("Cambiar Plan", "Button 'Cambiar Plan' is not visible.");
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Cuenta creada", "'Cuenta creada' is not visible.");
		assertTextVisible("Estado activo", "'Estado activo' is not visible.");
		assertTextVisible("Idioma seleccionado", "'Idioma seleccionado' is not visible.");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios", "Section 'Tus Negocios' is missing.");
		assertTextVisible("Agregar Negocio", "Button 'Agregar Negocio' is missing in business section.");
		assertTextVisible("Tienes 2 de 3 negocios", "Business counter text is not visible.");
		assertCondition(hasBusinessListLikeContent(), "Business list content was not detected.");
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		termsAndConditionsUrl = openLegalDocumentAndReturnUrl("Términos y Condiciones", "08-terminos-condiciones.png");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		privacyPolicyUrl = openLegalDocumentAndReturnUrl("Política de Privacidad", "09-politica-privacidad.png");
	}

	private String openLegalDocumentAndReturnUrl(final String linkText, final String screenshotName) throws Exception {
		final String sourceWindow = ensureAppWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);
		final String targetWindow = waitForNewWindowOrStay(beforeHandles);
		if (targetWindow != null) {
			driver.switchTo().window(targetWindow);
		}

		assertTextVisible(linkText, "Heading '" + linkText + "' is not visible.");
		assertCondition(hasLegalContent(), "Legal content text is not visible for '" + linkText + "'.");
		takeScreenshot(screenshotName);
		final String url = driver.getCurrentUrl();

		if (targetWindow != null) {
			driver.close();
			driver.switchTo().window(sourceWindow);
			waitForUiReady();
		} else {
			driver.navigate().back();
			waitForUiReady();
			driver.switchTo().window(sourceWindow);
		}
		return url;
	}

	private void initDriver() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		if (resolveBoolean("saleads.headless", "SALEADS_HEADLESS", false)) {
			options.addArguments("--headless=new");
		}

		final String debuggerAddress = resolveConfig("saleads.debugger.address", "SALEADS_DEBUGGER_ADDRESS");
		if (!isBlank(debuggerAddress)) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress.trim());
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, timeout);

		if (isBlank(debuggerAddress)) {
			final String loginUrl = resolveConfig("saleads.login.url", "SALEADS_LOGIN_URL");
			if (isBlank(loginUrl)) {
				throw new IllegalArgumentException(
						"Set SALEADS_LOGIN_URL (or -Dsaleads.login.url) when not using SALEADS_DEBUGGER_ADDRESS.");
			}
			driver.get(loginUrl.trim());
			waitForUiReady();
		}

		appWindowHandle = driver.getWindowHandle();
	}

	private void handleGoogleAccountSelection() throws InterruptedException {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		boolean selectedAccount = false;

		while (System.currentTimeMillis() < end) {
			final Set<String> handles = driver.getWindowHandles();
			for (final String handle : handles) {
				driver.switchTo().window(handle);
				if (isTextVisible(ACCOUNT_EMAIL, Duration.ofSeconds(1))) {
					clickByVisibleText(ACCOUNT_EMAIL);
					selectedAccount = true;
					waitForUiReady();
				}
			}

			if (isSidebarVisible()) {
				appWindowHandle = driver.getWindowHandle();
				return;
			}

			if (selectedAccount) {
				// Continue waiting for redirect into the app after account selection.
				waitForUiReady();
			}
			Thread.sleep(350L);
		}

		waitForSidebar();
	}

	private void waitForSidebar() {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (isSidebarVisible()) {
					appWindowHandle = handle;
					return;
				}
			}
			sleepQuietly(300L);
		}
		throw new IllegalStateException("Main interface did not load or left sidebar is not visible.");
	}

	private boolean isSidebarVisible() {
		final List<WebElement> sidebars = driver.findElements(By.xpath("//aside | //nav"));
		boolean visibleSidebar = false;
		for (final WebElement sidebar : sidebars) {
			if (safeDisplayed(sidebar)) {
				visibleSidebar = true;
				break;
			}
		}
		if (!visibleSidebar) {
			return false;
		}
		return hasVisibleTextQuick("Negocio") || hasVisibleTextQuick("Mi Negocio");
	}

	private boolean hasBusinessListLikeContent() {
		if (isTextVisible("Tus Negocios", Duration.ofSeconds(2))) {
			final List<WebElement> listCandidates = driver
					.findElements(By.xpath("//*[self::ul or self::ol or contains(@class,'list') or contains(@class,'business')]"));
			for (final WebElement candidate : listCandidates) {
				if (safeDisplayed(candidate) && normalizedText(candidate.getText()).length() > 1) {
					return true;
				}
			}
		}

		// Fallback if DOM is card-based instead of list-based.
		final String body = normalizedText(driver.findElement(By.tagName("body")).getText());
		return body.contains("tienes 2 de 3 negocios");
	}

	private boolean hasLegalContent() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p | //article | //main"));
		for (final WebElement paragraph : paragraphs) {
			final String text = normalizedText(paragraph.getText());
			if (safeDisplayed(paragraph) && text.length() > 120) {
				return true;
			}
		}
		final String body = normalizedText(driver.findElement(By.tagName("body")).getText());
		return body.length() > 240;
	}

	private boolean isUserNameVisibleNearEmail() {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[contains(normalize-space(.), '@')]"));
		for (final WebElement element : elements) {
			if (!safeDisplayed(element)) {
				continue;
			}
			final String text = normalizedText(element.getText());
			final String email = findAnyEmail(text);
			if (email == null) {
				continue;
			}

			WebElement container = element;
			for (int depth = 0; depth < 3; depth++) {
				try {
					container = container.findElement(By.xpath(".."));
				} catch (final NoSuchElementException ignored) {
					break;
				}
				final List<WebElement> nearbyTexts = container.findElements(By.xpath(".//h1 | .//h2 | .//h3 | .//h4 | .//p | .//span"));
				for (final WebElement nearby : nearbyTexts) {
					if (!safeDisplayed(nearby)) {
						continue;
					}
					final String candidate = normalizedText(nearby.getText());
					if (looksLikeUserName(candidate)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean looksLikeUserName(final String candidate) {
		if (candidate == null || candidate.length() < 3 || candidate.contains("@")) {
			return false;
		}
		final String lower = candidate.toLowerCase(Locale.ROOT);
		final List<String> excluded = Arrays.asList("información general", "business plan", "cambiar plan",
				"detalles de la cuenta", "tus negocios", "sección legal");
		for (final String exclusion : excluded) {
			if (lower.contains(exclusion)) {
				return false;
			}
		}
		int letters = 0;
		for (int i = 0; i < candidate.length(); i++) {
			if (Character.isLetter(candidate.charAt(i))) {
				letters++;
			}
		}
		return letters >= 3;
	}

	private void assertInputExists(final String labelText) {
		final List<By> selectors = new ArrayList<>();
		selectors.add(By.xpath("//label[contains(normalize-space(.), " + xpathLiteral(labelText) + ")]"));
		selectors.add(By.xpath("//input[@placeholder=" + xpathLiteral(labelText) + "]"));
		selectors.add(By.xpath("//input[contains(@aria-label, " + xpathLiteral(labelText) + ")]"));

		for (final By selector : selectors) {
			final List<WebElement> elements = driver.findElements(selector);
			for (final WebElement element : elements) {
				if (safeDisplayed(element)) {
					return;
				}
			}
		}
		throw new IllegalStateException("Input field '" + labelText + "' was not found.");
	}

	private void typeInInputIfPresent(final String labelText, final String text) {
		final List<By> selectors = new ArrayList<>();
		selectors.add(By.xpath("//input[@placeholder=" + xpathLiteral(labelText) + "]"));
		selectors.add(By.xpath("//input[contains(@aria-label, " + xpathLiteral(labelText) + ")]"));
		selectors.add(By.xpath("//label[contains(normalize-space(.), " + xpathLiteral(labelText) + ")]/following::input[1]"));

		for (final By selector : selectors) {
			final List<WebElement> inputs = driver.findElements(selector);
			for (final WebElement input : inputs) {
				if (!safeDisplayed(input)) {
					continue;
				}
				input.click();
				input.clear();
				input.sendKeys(text);
				return;
			}
		}
	}

	private void assertTextVisible(final String text, final String error) {
		if (!isTextVisible(text, timeout)) {
			throw new IllegalStateException(error);
		}
	}

	private boolean isTextVisible(final String text, final Duration customTimeout) {
		final long end = System.currentTimeMillis() + customTimeout.toMillis();
		while (System.currentTimeMillis() < end) {
			if (hasVisibleTextQuick(text)) {
				return true;
			}
			sleepQuietly(250L);
		}
		return false;
	}

	private boolean hasVisibleTextQuick(final String text) {
		final String exactXpath = "//*[normalize-space(.)=" + xpathLiteral(text) + "]";
		final String containsXpath = "//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]";
		for (final String xpath : Arrays.asList(exactXpath, containsXpath)) {
			final List<WebElement> elements = driver.findElements(By.xpath(xpath));
			for (final WebElement element : elements) {
				if (safeDisplayed(element)) {
					return true;
				}
			}
		}
		return false;
	}

	private WebElement findVisibleByText(final String... candidateTexts) {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			for (final String text : candidateTexts) {
				final String exactXpath = "//*[normalize-space(.)=" + xpathLiteral(text) + "]";
				final String containsXpath = "//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]";
				for (final String xpath : Arrays.asList(exactXpath, containsXpath)) {
					final List<WebElement> elements = driver.findElements(By.xpath(xpath));
					for (final WebElement element : elements) {
						if (safeDisplayed(element)) {
							return element;
						}
					}
				}
			}
			sleepQuietly(200L);
		}
		throw new NoSuchElementException("Could not find visible element with texts " + Arrays.toString(candidateTexts));
	}

	private void clickByVisibleText(final String... candidateTexts) {
		final WebElement element = findVisibleByText(candidateTexts);
		clickAndWait(element);
	}

	private void clickByVisibleTextIfPresent(final String... candidateTexts) {
		try {
			final WebElement element = findVisibleByText(candidateTexts);
			clickAndWait(element);
		} catch (final NoSuchElementException ignored) {
			// No-op for optional click actions.
		}
	}

	private void clickAndWait(final WebElement element) {
		scrollIntoView(element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final ElementClickInterceptedException | TimeoutException e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiReady();
	}

	private String waitForNewWindowOrStay(final Set<String> beforeHandles) {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			final Set<String> currentHandles = driver.getWindowHandles();
			if (currentHandles.size() > beforeHandles.size()) {
				for (final String handle : currentHandles) {
					if (!beforeHandles.contains(handle)) {
						return handle;
					}
				}
			}
			sleepQuietly(200L);
		}
		return null;
	}

	private String ensureAppWindowHandle() {
		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			return appWindowHandle;
		}
		appWindowHandle = driver.getWindowHandle();
		return appWindowHandle;
	}

	private void waitForUiReady() {
		try {
			wait.until(d -> {
				final Object ready = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(String.valueOf(ready));
			});
		} catch (final TimeoutException ignored) {
			// Some external pages keep loading trackers alive; continue with best-effort synchronization.
		}

		final List<By> busyIndicators = Arrays.asList(By.cssSelector("[aria-busy='true']"), By.cssSelector(".loading"),
				By.cssSelector(".spinner"), By.cssSelector(".ant-spin-spinning"));
		for (final By indicator : busyIndicators) {
			try {
				wait.until(driverArg -> {
					final List<WebElement> elements = driverArg.findElements(indicator);
					for (final WebElement element : elements) {
						if (safeDisplayed(element)) {
							return false;
						}
					}
					return true;
				});
			} catch (final TimeoutException ignored) {
				// Move forward if no deterministic loading indicator is available.
			}
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'center'});",
				element);
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final Path output = evidenceDir.resolve(fileName);
		final Path temp = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(temp, output, StandardCopyOption.REPLACE_EXISTING);
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}
		final StringBuilder literal = new StringBuilder("concat(");
		for (int i = 0; i < text.length(); i++) {
			final String ch = String.valueOf(text.charAt(i));
			if (i > 0) {
				literal.append(',');
			}
			if ("'".equals(ch)) {
				literal.append("\"'\"");
			} else {
				literal.append("'").append(ch).append("'");
			}
		}
		literal.append(')');
		return literal.toString();
	}

	private String findAnyEmail(final String text) {
		final Matcher matcher = EMAIL_PATTERN.matcher(text);
		return matcher.find() ? matcher.group() : null;
	}

	private String normalizedText(final String text) {
		return text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private int resolveTimeoutSeconds() {
		final String value = resolveConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS");
		if (isBlank(value)) {
			return 30;
		}
		return Integer.parseInt(value);
	}

	private boolean resolveBoolean(final String propertyName, final String envName, final boolean defaultValue) {
		final String value = resolveConfig(propertyName, envName);
		if (isBlank(value)) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value);
	}

	private String resolveConfig(final String propertyName, final String envName) {
		final String property = System.getProperty(propertyName);
		if (!isBlank(property)) {
			return property;
		}
		return System.getenv(envName);
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private void executeStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, StepResult.pass("PASS"));
		} catch (final Exception e) {
			stepResults.put(stepName, StepResult.fail(safeMessage(e)));
		}
	}

	private void assertCondition(final boolean expression, final String message) {
		if (!expression) {
			throw new IllegalStateException(message);
		}
	}

	private void recordFailure(final String stepName, final String details) {
		stepResults.put(stepName, StepResult.fail(details));
	}

	private boolean allStepsPassed() {
		final List<String> expectedSteps = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Términos y Condiciones", "Política de Privacidad");

		for (final String step : expectedSteps) {
			final StepResult result = stepResults.get(step);
			if (result == null || !result.passed) {
				return false;
			}
		}
		return true;
	}

	private void printFinalReport() {
		System.out.println("===== SaleADS Mi Negocio Final Report =====");
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		final List<String> orderedSteps = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Términos y Condiciones", "Política de Privacidad");

		for (final String step : orderedSteps) {
			final StepResult result = stepResults.getOrDefault(step, StepResult.fail("Not executed."));
			System.out.println(step + ": " + (result.passed ? "PASS" : "FAIL") + " - " + result.details);
		}
		System.out.println("Términos y Condiciones URL: " + termsAndConditionsUrl);
		System.out.println("Política de Privacidad URL: " + privacyPolicyUrl);
		System.out.println("===========================================");
	}

	private void quitDriver() {
		if (driver != null) {
			driver.quit();
		}
	}

	private String safeMessage(final Exception e) {
		final String message = e.getMessage();
		return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
	}

	private static Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(TS_FORMATTER);
		final Path dir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private boolean safeDisplayed(final WebElement element) {
		try {
			return element.isDisplayed();
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void sleepQuietly(final long millis) {
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
