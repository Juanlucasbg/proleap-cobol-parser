package io.proleap.cobol.e2e;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow validation for the SaleADS "Mi Negocio" module.
 *
 * <p>
 * This test is environment agnostic and requires the login page URL via one of:
 * </p>
 * <ul>
 * <li>System property: -Dsaleads.loginUrl=https://your-environment/login</li>
 * <li>Environment variable: SALEADS_LOGIN_URL=https://your-environment/login</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String STEP_INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String STEP_POLITICA = "Pol\u00edtica de Privacidad";

	private static final List<String> REPORT_ORDER = List.of(STEP_LOGIN, STEP_MI_NEGOCIO_MENU, STEP_AGREGAR_NEGOCIO_MODAL,
			STEP_ADMINISTRAR_NEGOCIOS_VIEW, STEP_INFORMACION_GENERAL, STEP_DETALLES_CUENTA, STEP_TUS_NEGOCIOS, STEP_TERMINOS,
			STEP_POLITICA);

	private static final class StepResult {
		private final List<String> failures = new ArrayList<>();

		private void fail(final String message) {
			failures.add(message);
		}

		private boolean isPassed() {
			return failures.isEmpty();
		}
	}

	private String loginUrl;
	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;
	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void before() throws IOException {
		loginUrl = readConfig("saleads.loginUrl", "SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set -Dsaleads.loginUrl or SALEADS_LOGIN_URL to run SaleADS E2E validations in any target environment.",
				loginUrl != null && !loginUrl.isBlank());

		evidenceDir = Path.of("target", "saleads-mi-negocio-evidence");
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		if (!Boolean.parseBoolean(System.getProperty("saleads.headed", "false"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.get(loginUrl);
		appWindowHandle = driver.getWindowHandle();
		waitForUiToLoad();
	}

	@After
	public void after() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		executeStep(STEP_LOGIN, this::executeLoginStep);
		executeStep(STEP_MI_NEGOCIO_MENU, this::executeMiNegocioMenuStep);
		executeStep(STEP_AGREGAR_NEGOCIO_MODAL, this::executeAgregarNegocioModalStep);
		executeStep(STEP_ADMINISTRAR_NEGOCIOS_VIEW, this::executeAdministrarNegociosStep);
		executeStep(STEP_INFORMACION_GENERAL, this::executeInformacionGeneralStep);
		executeStep(STEP_DETALLES_CUENTA, this::executeDetallesCuentaStep);
		executeStep(STEP_TUS_NEGOCIOS, this::executeTusNegociosStep);
		executeStep(STEP_TERMINOS, ignored -> executeLegalLinkStep(STEP_TERMINOS, "08-terminos"));
		executeStep(STEP_POLITICA, ignored -> executeLegalLinkStep(STEP_POLITICA, "09-politica"));

		final List<String> failedSteps = new ArrayList<>();
		for (final String step : REPORT_ORDER) {
			final StepResult result = stepResults.get(step);
			if (result == null || !result.isPassed()) {
				failedSteps.add(step);
			}
		}

		if (!failedSteps.isEmpty()) {
			fail("SaleADS workflow validations failed: " + failedSteps);
		}
	}

	private void executeLoginStep(final StepResult step) throws IOException {
		final List<String> loginTexts = List.of("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google",
				"Iniciar sesi\u00f3n", "Ingresar con Google");
		final WebElement loginButton = waitForClickableByTexts(loginTexts, Duration.ofSeconds(20));
		if (loginButton == null) {
			step.fail("Could not find a Google login button.");
			return;
		}

		final int handlesBeforeClick = driver.getWindowHandles().size();
		clickAndWait(loginButton);
		selectGoogleAccountIfPrompted();
		switchToNewestWindowIfNeeded(handlesBeforeClick);

		waitForUiToLoad();
		final boolean onGoogleDomain = driver.getCurrentUrl().contains("accounts.google.com");
		if (onGoogleDomain) {
			step.fail("Application did not return from Google login.");
		}

		final boolean mainInterfaceVisible = isAnyVisible(By.xpath("//aside|//nav|//*[@role='navigation']"))
				|| isAnyVisible(byText("Negocio")) || isAnyVisible(byText("Mi Negocio"));
		if (!mainInterfaceVisible) {
			step.fail("Main application interface or left sidebar is not visible.");
		}

		captureScreenshot("01-dashboard-loaded");
	}

	private void executeMiNegocioMenuStep(final StepResult step) throws IOException {
		expandNegocioSectionIfPresent();
		clickByText("Mi Negocio", step);

		if (!isAnyVisible(byText("Agregar Negocio"))) {
			step.fail("Submenu entry 'Agregar Negocio' is not visible.");
		}
		if (!isAnyVisible(byText("Administrar Negocios"))) {
			step.fail("Submenu entry 'Administrar Negocios' is not visible.");
		}

		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void executeAgregarNegocioModalStep(final StepResult step) throws IOException {
		clickByText("Agregar Negocio", step);
		waitForUiToLoad();

		if (!isAnyVisible(byText("Crear Nuevo Negocio"))) {
			step.fail("Modal title 'Crear Nuevo Negocio' was not found.");
		}

		final WebElement nombreInput = findFirstVisibleElement(List.of(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[normalize-space(.)='Nombre del Negocio']/following::input[1]"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]")));
		if (nombreInput == null) {
			step.fail("Input field 'Nombre del Negocio' was not found.");
		}

		if (!isAnyVisible(byText("Tienes 2 de 3 negocios"))) {
			step.fail("Expected quota text 'Tienes 2 de 3 negocios' was not found.");
		}
		if (!isAnyVisible(byText("Cancelar"))) {
			step.fail("Button 'Cancelar' is missing.");
		}
		if (!isAnyVisible(byText("Crear Negocio"))) {
			step.fail("Button 'Crear Negocio' is missing.");
		}

		captureScreenshot("03-agregar-negocio-modal");

		if (nombreInput != null) {
			nombreInput.click();
			nombreInput.clear();
			nombreInput.sendKeys("Negocio Prueba Automatizacion");
		}
		clickByText("Cancelar", step);
		waitForUiToLoad();
	}

	private void executeAdministrarNegociosStep(final StepResult step) throws IOException {
		if (!isAnyVisible(byText("Administrar Negocios"))) {
			expandNegocioSectionIfPresent();
			clickByText("Mi Negocio", step);
		}
		clickByText("Administrar Negocios", step);
		waitForUiToLoad();

		if (!isAnyVisibleAnyText(List.of("Informaci\u00f3n General", "Informacion General"))) {
			step.fail("Section 'Informacion General' was not found.");
		}
		if (!isAnyVisible(byText("Detalles de la Cuenta"))) {
			step.fail("Section 'Detalles de la Cuenta' was not found.");
		}
		if (!isAnyVisible(byText("Tus Negocios"))) {
			step.fail("Section 'Tus Negocios' was not found.");
		}
		if (!isAnyVisibleAnyText(List.of("Secci\u00f3n Legal", "Seccion Legal"))) {
			step.fail("Section 'Seccion Legal' was not found.");
		}

		captureScreenshot("04-administrar-negocios-account-page");
	}

	private void executeInformacionGeneralStep(final StepResult step) {
		final String bodyText = getBodyText();
		if (extractFirstEmail(bodyText) == null) {
			step.fail("User email was not found on screen.");
		}

		final WebElement section = findFirstVisibleElement(List.of(byText("Informaci\u00f3n General"), byText("Informacion General")));
		if (section == null) {
			step.fail("Could not locate the 'Informacion General' section.");
		} else if (!containsPotentialUserNameText(section.getText())) {
			step.fail("User name-like text was not identified in 'Informacion General'.");
		}

		if (!isAnyVisible(byText("BUSINESS PLAN"))) {
			step.fail("Text 'BUSINESS PLAN' is not visible.");
		}
		if (!isAnyVisible(byText("Cambiar Plan"))) {
			step.fail("Button 'Cambiar Plan' is not visible.");
		}
	}

	private void executeDetallesCuentaStep(final StepResult step) {
		if (!isAnyVisibleAnyText(List.of("Cuenta creada", "Cuenta Creada"))) {
			step.fail("Text 'Cuenta creada' is not visible.");
		}
		if (!isAnyVisibleAnyText(List.of("Estado activo", "Estado Activo"))) {
			step.fail("Text 'Estado activo' is not visible.");
		}
		if (!isAnyVisibleAnyText(List.of("Idioma seleccionado", "Idioma Seleccionado"))) {
			step.fail("Text 'Idioma seleccionado' is not visible.");
		}
	}

	private void executeTusNegociosStep(final StepResult step) {
		final WebElement section = findFirstVisibleElement(List.of(byText("Tus Negocios")));
		if (section == null) {
			step.fail("Section 'Tus Negocios' is not visible.");
		} else {
			final boolean hasBusinessRows = section.getText() != null && section.getText().trim().length() > "Tus Negocios".length();
			if (!hasBusinessRows) {
				step.fail("Business list content is not visible in 'Tus Negocios'.");
			}
		}

		if (!isAnyVisible(byText("Agregar Negocio"))) {
			step.fail("Button 'Agregar Negocio' is missing in 'Tus Negocios'.");
		}
		if (!isAnyVisible(byText("Tienes 2 de 3 negocios"))) {
			step.fail("Text 'Tienes 2 de 3 negocios' is missing in 'Tus Negocios'.");
		}
	}

	private void executeLegalLinkStep(final String legalTitle, final String screenshotNamePrefix) throws IOException {
		final StepResult step = stepResults.get(legalTitle);
		if (step == null) {
			return;
		}

		final String previousWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String previousUrl = driver.getCurrentUrl();

		clickByText(legalTitle, step);

		try {
			wait.withTimeout(Duration.ofSeconds(20)).until(d -> {
				final Set<String> currentHandles = d.getWindowHandles();
				return currentHandles.size() > handlesBeforeClick.size() || !d.getCurrentUrl().equals(previousUrl);
			});
		} catch (final TimeoutException e) {
			step.fail("Click on '" + legalTitle + "' did not navigate or open a new tab.");
		} finally {
			wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		}

		boolean openedNewTab = false;
		final Set<String> handlesAfterClick = driver.getWindowHandles();
		if (handlesAfterClick.size() > handlesBeforeClick.size()) {
			for (final String handle : handlesAfterClick) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					openedNewTab = true;
					break;
				}
			}
		}

		waitForUiToLoad();
		final String headingFallback = legalTitle.replace("\u00e9", "e").replace("\u00ed", "i");
		if (!isAnyVisibleAnyText(List.of(legalTitle, headingFallback))) {
			step.fail("Heading '" + legalTitle + "' is not visible.");
		}
		final String bodyText = getBodyText();
		if (bodyText.trim().length() < 150) {
			step.fail("Legal content text appears too short or missing.");
		}

		captureScreenshot(screenshotNamePrefix + "-legal-page");
		legalUrls.put(legalTitle, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			try {
				driver.switchTo().window(previousWindow);
			} catch (final NoSuchWindowException e) {
				driver.switchTo().window(appWindowHandle);
			}
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
	}

	private void executeStep(final String stepName, final StepAction action) {
		final StepResult step = new StepResult();
		stepResults.put(stepName, step);

		try {
			action.run(step);
		} catch (final Throwable throwable) {
			step.fail("Unexpected error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
		}
	}

	private void selectGoogleAccountIfPrompted() {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				final List<WebElement> accounts = visibleElements(byText(GOOGLE_ACCOUNT_EMAIL));
				if (!accounts.isEmpty()) {
					clickAndWait(accounts.get(0));
					waitForUiToLoad();
					break;
				}
			}
			if (driver.getWindowHandles().contains(appWindowHandle)) {
				driver.switchTo().window(appWindowHandle);
			}
			if (!driver.getCurrentUrl().contains("accounts.google.com")) {
				break;
			}
			sleepMillis(500);
		}
	}

	private void switchToNewestWindowIfNeeded(final int handlesBeforeClick) {
		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() <= handlesBeforeClick) {
			return;
		}

		for (final String handle : handles) {
			if (!handle.equals(appWindowHandle)) {
				driver.switchTo().window(handle);
			}
		}
	}

	private void expandNegocioSectionIfPresent() {
		final WebElement negocio = waitForClickableByTexts(List.of("Negocio"), Duration.ofSeconds(4));
		if (negocio != null) {
			clickAndWait(negocio);
		}
	}

	private void clickByText(final String text, final StepResult step) {
		final WebElement element = waitForClickableByTexts(List.of(text), Duration.ofSeconds(15));
		if (element == null) {
			step.fail("Could not find clickable element with text '" + text + "'.");
			return;
		}
		clickAndWait(element);
	}

	private WebElement waitForClickableByTexts(final List<String> texts, final Duration timeout) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String text : texts) {
				for (final WebElement element : visibleElements(byClickableText(text))) {
					try {
						if (element.isEnabled()) {
							return element;
						}
					} catch (final WebDriverException ignored) {
						// Keep searching if DOM changed.
					}
				}
			}
			sleepMillis(350);
		}
		return null;
	}

	private void clickAndWait(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final ElementClickInterceptedException e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final TimeoutException ignored) {
			// Some SPA transitions may not reach complete again; proceed with fallback.
		}
		sleepMillis(700);
	}

	private void captureScreenshot(final String name) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final Path destination = evidenceDir.resolve(name + ".png");
		Files.copy(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath(), destination,
				StandardCopyOption.REPLACE_EXISTING);
	}

	private boolean isAnyVisibleAnyText(final List<String> texts) {
		for (final String text : texts) {
			if (isAnyVisible(byText(text))) {
				return true;
			}
		}
		return false;
	}

	private boolean isAnyVisible(final By by) {
		return !visibleElements(by).isEmpty();
	}

	private List<WebElement> visibleElements(final By by) {
		final List<WebElement> visible = new ArrayList<>();
		for (final WebElement element : driver.findElements(by)) {
			try {
				if (element.isDisplayed()) {
					visible.add(element);
				}
			} catch (final WebDriverException ignored) {
				// Element became stale, ignore and continue.
			}
		}
		return visible;
	}

	private WebElement findFirstVisibleElement(final List<By> selectors) {
		for (final By selector : selectors) {
			final List<WebElement> elements = visibleElements(selector);
			if (!elements.isEmpty()) {
				return elements.get(0);
			}
		}
		return null;
	}

	private String getBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final WebDriverException e) {
			return "";
		}
	}

	private String extractFirstEmail(final String text) {
		final Matcher matcher = EMAIL_PATTERN.matcher(text);
		if (matcher.find()) {
			return matcher.group();
		}
		return null;
	}

	private boolean containsPotentialUserNameText(final String text) {
		if (text == null) {
			return false;
		}
		final String[] lines = text.split("\\R");
		for (final String line : lines) {
			final String candidate = line.trim();
			if (candidate.isEmpty() || candidate.contains("@")) {
				continue;
			}
			if (candidate.equalsIgnoreCase("Informacion General") || candidate.equalsIgnoreCase("Informaci\u00f3n General")
					|| candidate.equalsIgnoreCase("BUSINESS PLAN") || candidate.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}
			if (candidate.length() >= 3 && candidate.matches(".*[A-Za-z].*")) {
				return true;
			}
		}
		return false;
	}

	private By byText(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath("//*[normalize-space(.)=" + literal + "]");
	}

	private By byClickableText(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath(
				"//button[normalize-space(.)="
						+ literal
						+ "]|//a[normalize-space(.)="
						+ literal
						+ "]|//*[@role='button' and normalize-space(.)="
						+ literal
						+ "]|//span[normalize-space(.)="
						+ literal
						+ "]|//div[normalize-space(.)="
						+ literal + "]");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				result.append(',');
			}
			if (chars[i] == '\'') {
				result.append("\"'\"");
			} else if (chars[i] == '"') {
				result.append("'\"'");
			} else {
				result.append('\'').append(chars[i]).append('\'');
			}
		}
		result.append(')');
		return result.toString();
	}

	private void writeFinalReport() throws IOException {
		final Path outputDir = evidenceDir == null ? Path.of("target") : evidenceDir;
		Files.createDirectories(outputDir);
		final Path reportPath = outputDir.resolve("final-report.md");

		final StringBuilder report = new StringBuilder();
		report.append("# SaleADS Mi Negocio Workflow Report").append(System.lineSeparator()).append(System.lineSeparator());
		report.append("| Validation | Status |").append(System.lineSeparator());
		report.append("| --- | --- |").append(System.lineSeparator());
		for (final String step : REPORT_ORDER) {
			final StepResult stepResult = stepResults.get(step);
			final String status = stepResult != null && stepResult.isPassed() ? "PASS" : "FAIL";
			report.append("| ").append(step).append(" | ").append(status).append(" |").append(System.lineSeparator());
		}

		report.append(System.lineSeparator()).append("## Failed checks").append(System.lineSeparator());
		for (final String step : REPORT_ORDER) {
			final StepResult stepResult = stepResults.get(step);
			if (stepResult == null) {
				report.append("- ").append(step).append(": not executed").append(System.lineSeparator());
			} else if (!stepResult.isPassed()) {
				for (final String failure : stepResult.failures) {
					report.append("- ").append(step).append(": ").append(failure).append(System.lineSeparator());
				}
			}
		}

		report.append(System.lineSeparator()).append("## Legal URLs").append(System.lineSeparator());
		report.append("- T\u00e9rminos y Condiciones: ")
				.append(legalUrls.getOrDefault(STEP_TERMINOS, "not captured"))
				.append(System.lineSeparator());
		report.append("- Pol\u00edtica de Privacidad: ")
				.append(legalUrls.getOrDefault(STEP_POLITICA, "not captured"))
				.append(System.lineSeparator());

		Files.writeString(reportPath, report.toString());
		System.out.println(report);
	}

	private String readConfig(final String propertyName, final String envName) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		return null;
	}

	private void sleepMillis(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run(StepResult step) throws Exception;
	}
}
