package io.proleap.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
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

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private final Map<String, StepResult> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String currentStepName;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue(
				"Enable with -Dsaleads.e2e.enabled=true",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

		initReport();

		final String runId = TS_FORMAT.format(LocalDateTime.now());
		evidenceDir = Path.of("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.e2e.headless", "false"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--window-size=1920,1080");

		final String userDataDir = System.getProperty("saleads.chrome.userDataDir", "").trim();
		if (!userDataDir.isEmpty()) {
			options.addArguments("--user-data-dir=" + userDataDir);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
		wait.pollingEvery(Duration.ofMillis(250));

		final String loginUrl = System.getProperty(
				"saleads.login.url",
				System.getenv().getOrDefault("SALEADS_LOGIN_URL", "")).trim();
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
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
		executeStep(LOGIN, this::loginWithGoogle);
		executeStep(MI_NEGOCIO_MENU, this::openMiNegocioMenu);
		executeStep(AGREGAR_NEGOCIO_MODAL, this::validateAgregarNegocioModal);
		executeStep(ADMINISTRAR_NEGOCIOS_VIEW, this::openAdministrarNegocios);
		executeStep(INFORMACION_GENERAL, this::validateInformacionGeneral);
		executeStep(DETALLES_CUENTA, this::validateDetallesCuenta);
		executeStep(TUS_NEGOCIOS, this::validateTusNegocios);
		executeStep(TERMINOS, () -> validateLegalLink(
				"T\u00e9rminos y Condiciones",
				"T\u00e9rminos y Condiciones",
				"08-terminos"));
		executeStep(PRIVACIDAD, () -> validateLegalLink(
				"Pol\u00edtica de Privacidad",
				"Pol\u00edtica de Privacidad",
				"09-privacidad"));
		assertAllStepsPassed();
	}

	private void loginWithGoogle() throws IOException {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickByAnyVisibleText(List.of(
				"Sign in with Google",
				"Iniciar sesi\u00f3n con Google",
				"Continuar con Google",
				"Google"));

		final boolean switchedToPopup = switchToNewWindowIfOpened(handlesBeforeClick);
		waitForUiToLoad();

		if (isGooglePage()) {
			selectGoogleAccountIfPresent(System.getProperty(
					"saleads.google.account",
					"juanlucasbarbiergarzon@gmail.com"));
			waitForUiToLoad();
		}

		if (switchedToPopup && driver.getWindowHandles().contains(appHandle)) {
			driver.switchTo().window(appHandle);
		}

		waitUntilAnyTextVisible(List.of("Negocio", "Mi Negocio", "Dashboard", "Panel"));
		waitUntilVisibleText("Negocio");
		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws IOException {
		waitUntilVisibleText("Negocio");
		clickByAnyVisibleText(List.of("Mi Negocio"));
		waitUntilVisibleText("Agregar Negocio");
		waitUntilVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByAnyVisibleText(List.of("Agregar Negocio"));
		waitUntilVisibleText("Crear Nuevo Negocio");
		waitUntilVisibleText("Nombre del Negocio");
		waitUntilVisibleText("Tienes 2 de 3 negocios");
		waitUntilVisibleText("Cancelar");
		waitUntilVisibleText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final WebElement input = findVisibleElement(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or @name='businessName' or @id='businessName']"));
		if (input != null) {
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatizacion");
			waitForUiToLoad();
		}
		clickByAnyVisibleText(List.of("Cancelar"));
	}

	private void openAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickByAnyVisibleText(List.of("Mi Negocio"));
		}
		clickByAnyVisibleText(List.of("Administrar Negocios"));
		waitUntilVisibleText("Informaci\u00f3n General");
		waitUntilVisibleText("Detalles de la Cuenta");
		waitUntilVisibleText("Tus Negocios");
		waitUntilVisibleText("Secci\u00f3n Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		waitUntilVisibleText("Informaci\u00f3n General");
		waitUntilVisibleText("BUSINESS PLAN");
		waitUntilVisibleText("Cambiar Plan");

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("User email is not visible", EMAIL_PATTERN.matcher(bodyText).find());

		final String bodyWithoutEmail = EMAIL_PATTERN.matcher(bodyText).replaceAll("");
		Assert.assertTrue(
				"User name is not visible",
				bodyWithoutEmail.replaceAll("\\s+", " ").trim().length() > 20);
	}

	private void validateDetallesCuenta() {
		waitUntilVisibleText("Cuenta creada");
		waitUntilVisibleText("Estado activo");
		waitUntilVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		waitUntilVisibleText("Tus Negocios");
		waitUntilVisibleText("Agregar Negocio");
		waitUntilVisibleText("Tienes 2 de 3 negocios");

		final WebElement section = findVisibleElement(
				By.xpath("//*[contains(normalize-space(.),'Tus Negocios')]/ancestor::*[self::section or self::div][1]"));
		Assert.assertNotNull("Business section is missing", section);
		final List<WebElement> listLikeElements = section.findElements(
				By.xpath(".//li | .//table | .//tbody/tr | .//div[contains(@class,'card')]"));
		Assert.assertFalse("Business list is not visible", listLikeElements.isEmpty());
	}

	private void validateLegalLink(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String appHandle = driver.getWindowHandle();
		final String urlBeforeClick = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByAnyVisibleText(List.of(linkText));

		final boolean switchedToNewTab = waitForNewWindowOrUrlChange(handlesBeforeClick, urlBeforeClick);
		waitForUiToLoad();
		waitUntilVisibleText(headingText);

		final String pageText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Legal page content is missing", pageText.trim().length() > 300);

		takeScreenshot(screenshotName);
		appendCurrentStepDetails("Final URL: " + driver.getCurrentUrl());

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private boolean waitForNewWindowOrUrlChange(final Set<String> handlesBeforeClick, final String oldUrl) {
		try {
			wait.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size()
					|| !d.getCurrentUrl().equals(oldUrl));
		} catch (final TimeoutException ex) {
			throw new AssertionError("No new tab or navigation detected after legal link click.");
		}

		if (driver.getWindowHandles().size() > handlesBeforeClick.size()) {
			switchToNewWindowIfOpened(handlesBeforeClick);
			return true;
		}

		return false;
	}

	private void selectGoogleAccountIfPresent(final String email) {
		final List<WebElement> candidates = findVisibleElements(By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(email) + ")]"));
		if (candidates.isEmpty()) {
			return;
		}
		clickElement(candidates.get(0));
		waitForUiToLoad();
	}

	private void clickByAnyVisibleText(final List<String> textCandidates) {
		WebElement element = null;
		for (final String text : textCandidates) {
			element = findClickableByText(text);
			if (element != null) {
				break;
			}
		}
		Assert.assertNotNull("No clickable element found for texts: " + textCandidates, element);
		clickElement(element);
		waitForUiToLoad();
	}

	private WebElement findClickableByText(final String text) {
		final By locator = By.xpath(
				"//*[self::button or self::a or @role='button' or self::div]"
						+ "[contains(normalize-space(.), " + toXPathLiteral(text) + ")]");
		final List<WebElement> found = findVisibleElements(locator);
		for (final WebElement element : found) {
			if (element.isEnabled()) {
				return element;
			}
		}
		return null;
	}

	private void waitUntilVisibleText(final String text) {
		wait.until(d -> isTextVisible(text));
	}

	private void waitUntilAnyTextVisible(final List<String> textCandidates) {
		wait.until(d -> {
			for (final String text : textCandidates) {
				if (isTextVisible(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isTextVisible(final String text) {
		return !findVisibleElements(By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]")).isEmpty();
	}

	private List<WebElement> findVisibleElements(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		final List<WebElement> visible = new ArrayList<>();
		for (final WebElement element : elements) {
			try {
				if (element.isDisplayed()) {
					visible.add(element);
				}
			} catch (final Exception ignored) {
				// stale element or transient DOM updates during polling
			}
		}
		return visible;
	}

	private WebElement findVisibleElement(final By locator) {
		final List<WebElement> visible = findVisibleElements(locator);
		return visible.isEmpty() ? null : visible.get(0);
	}

	private void clickElement(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private boolean switchToNewWindowIfOpened(final Set<String> handlesBeforeClick) {
		final Set<String> handlesAfterClick = driver.getWindowHandles();
		if (handlesAfterClick.size() <= handlesBeforeClick.size()) {
			return false;
		}
		for (final String handle : handlesAfterClick) {
			if (!handlesBeforeClick.contains(handle)) {
				driver.switchTo().window(handle);
				return true;
			}
		}
		return false;
	}

	private boolean isGooglePage() {
		final String url = driver.getCurrentUrl().toLowerCase();
		final String title = driver.getTitle().toLowerCase();
		return url.contains("accounts.google.") || title.contains("google");
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(400);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String name) throws IOException {
		final Path screenshotFile = evidenceDir.resolve(name + ".png");
		final Path tmp = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(tmp, screenshotFile, StandardCopyOption.REPLACE_EXISTING);
	}

	private void executeStep(final String stepName, final StepAction action) {
		currentStepName = stepName;
		try {
			action.run();
			report.put(stepName, report.get(stepName).asPass());
		} catch (final Throwable error) {
			report.put(stepName, report.get(stepName).asFail(error.getMessage()));
		} finally {
			currentStepName = null;
		}
	}

	private void appendCurrentStepDetails(final String details) {
		if (currentStepName == null) {
			return;
		}
		final StepResult current = report.get(currentStepName);
		if (current == null) {
			return;
		}
		report.put(currentStepName, current.withDetails(details));
	}

	private void assertAllStepsPassed() {
		final StringBuilder summary = new StringBuilder();
		boolean allPassed = true;
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final StepResult result = entry.getValue();
			if (!result.passed) {
				allPassed = false;
			}
			summary.append(entry.getKey())
					.append(": ")
					.append(result.passed ? "PASS" : "FAIL")
					.append(result.details.isEmpty() ? "" : " (" + result.details + ")")
					.append(System.lineSeparator());
		}
		Assert.assertTrue("One or more validations failed:" + System.lineSeparator() + summary, allPassed);
	}

	private void initReport() {
		report.put(LOGIN, StepResult.notRun());
		report.put(MI_NEGOCIO_MENU, StepResult.notRun());
		report.put(AGREGAR_NEGOCIO_MODAL, StepResult.notRun());
		report.put(ADMINISTRAR_NEGOCIOS_VIEW, StepResult.notRun());
		report.put(INFORMACION_GENERAL, StepResult.notRun());
		report.put(DETALLES_CUENTA, StepResult.notRun());
		report.put(TUS_NEGOCIOS, StepResult.notRun());
		report.put(TERMINOS, StepResult.notRun());
		report.put(PRIVACIDAD, StepResult.notRun());
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}
		final StringBuilder content = new StringBuilder();
		content.append("SaleADS Mi Negocio Workflow Final Report").append(System.lineSeparator());
		content.append("Evidence Directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		content.append(System.lineSeparator());
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			content.append(entry.getKey())
					.append(": ")
					.append(entry.getValue().passed ? "PASS" : "FAIL")
					.append(entry.getValue().details.isEmpty() ? "" : " - " + entry.getValue().details)
					.append(System.lineSeparator());
		}
		Files.writeString(evidenceDir.resolve("final-report.txt"), content.toString());
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
		for (int index = 0; index < chars.length; index++) {
			if (index > 0) {
				result.append(", ");
			}
			if (chars[index] == '\'') {
				result.append("\"'\"");
			} else {
				result.append("'").append(chars[index]).append("'");
			}
		}
		result.append(")");
		return result.toString();
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult notRun() {
			return new StepResult(false, "NOT RUN");
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details == null ? "No details" : details);
		}

		private StepResult asPass() {
			return new StepResult(true, "NOT RUN".equals(details) ? "" : details);
		}

		private StepResult asFail(final String errorDetails) {
			final String message = errorDetails == null ? "No details" : errorDetails;
			if (details == null || details.isEmpty() || "NOT RUN".equals(details)) {
				return new StepResult(false, message);
			}
			return new StepResult(false, details + " | " + message);
		}

		private StepResult withDetails(final String extraDetails) {
			if (extraDetails == null || extraDetails.isEmpty()) {
				return this;
			}
			if (details == null || details.isEmpty() || "NOT RUN".equals(details)) {
				return new StepResult(passed, extraDetails);
			}
			return new StepResult(passed, details + " | " + extraDetails);
		}
	}
}
