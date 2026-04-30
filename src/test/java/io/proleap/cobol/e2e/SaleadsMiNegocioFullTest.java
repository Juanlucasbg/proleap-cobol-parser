package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, List<String>> stepNotes = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final boolean e2eEnabled = Boolean.parseBoolean(env("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"Set SALEADS_E2E_ENABLED=true and SALEADS_START_URL=<login page URL> to run this E2E test.",
				e2eEnabled);

		final String startUrl = env("SALEADS_START_URL", "").trim();
		Assume.assumeTrue("SALEADS_START_URL must point to the login page in the current environment.",
				!startUrl.isEmpty());

		evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence");
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (Boolean.parseBoolean(env("SALEADS_HEADLESS", "false"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.get(startUrl);
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
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Informaci\u00f3n General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("T\u00e9rminos y Condiciones",
				() -> stepValidateLegalLink("T\u00e9rminos y Condiciones", "T\u00e9rminos y Condiciones", "08-terminos"));
		runStep("Pol\u00edtica de Privacidad",
				() -> stepValidateLegalLink("Pol\u00edtica de Privacidad", "Pol\u00edtica de Privacidad", "09-privacidad"));

		final String summary = buildSummary();
		assertTrue("One or more validations failed.\n" + summary, stepResults.values().stream().allMatch(StepResult::isPassed));
	}

	private void stepLoginWithGoogle() throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google"));

		final String targetHandle = waitForPossiblyNewTab(handlesBeforeClick);
		if (!originalHandle.equals(targetHandle)) {
			driver.switchTo().window(targetHandle);
		}

		selectGoogleAccountIfVisible(env("SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL));
		switchToWindowWithVisibleText(Arrays.asList("Negocio", "Mi Negocio"));
		assertAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio"), "Main application interface is visible");
		assertSidebarVisible();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		expandSidebarIfCollapsed();
		if (!isAnyTextVisible(Arrays.asList("Mi Negocio"))) {
			clickByVisibleText(Arrays.asList("Negocio"));
		}
		clickByVisibleText(Arrays.asList("Mi Negocio"));
		waitForUiToLoad();

		assertAnyVisibleText(Arrays.asList("Agregar Negocio"), "'Agregar Negocio' is visible");
		assertAnyVisibleText(Arrays.asList("Administrar Negocios"), "'Administrar Negocios' is visible");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText(Arrays.asList("Agregar Negocio"));
		assertAnyVisibleText(Arrays.asList("Crear Nuevo Negocio"), "Crear Nuevo Negocio modal title is visible");
		assertAnyVisibleText(Arrays.asList("Nombre del Negocio"), "Nombre del Negocio input label is visible");
		assertInputVisibleNearLabel("Nombre del Negocio");
		assertAnyVisibleText(Arrays.asList("Tienes 2 de 3 negocios"), "Business quota text is visible");
		assertAnyVisibleText(Arrays.asList("Cancelar"), "Cancelar button is visible");
		assertAnyVisibleText(Arrays.asList("Crear Negocio"), "Crear Negocio button is visible");
		takeScreenshot("03-agregar-negocio-modal");

		typeBusinessNameAndCancel();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandSidebarIfCollapsed();
		if (!isAnyTextVisible(Arrays.asList("Administrar Negocios"))) {
			clickByVisibleText(Arrays.asList("Mi Negocio"));
		}
		clickByVisibleText(Arrays.asList("Administrar Negocios"));
		waitForUiToLoad();

		assertAnyVisibleText(Arrays.asList("Informaci\u00f3n General", "Informacion General"), "Informaci\u00f3n General section exists");
		assertAnyVisibleText(Arrays.asList("Detalles de la Cuenta", "Detalles de la cuenta"), "Detalles de la Cuenta section exists");
		assertAnyVisibleText(Arrays.asList("Tus Negocios"), "Tus Negocios section exists");
		assertAnyVisibleText(Arrays.asList("Secci\u00f3n Legal", "Seccion Legal"), "Secci\u00f3n Legal section exists");
		takeScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("User email is visible", EMAIL_PATTERN.matcher(bodyText).find());
		assertTrue("User name is visible",
				containsIgnoreCase(bodyText, "Nombre") || containsIgnoreCase(bodyText, "Usuario"));
		assertAnyVisibleText(Arrays.asList("BUSINESS PLAN"), "BUSINESS PLAN text is visible");
		assertAnyVisibleText(Arrays.asList("Cambiar Plan"), "Cambiar Plan button is visible");
	}

	private void stepValidateDetallesCuenta() {
		assertAnyVisibleText(Arrays.asList("Cuenta creada"), "Cuenta creada is visible");
		assertAnyVisibleText(Arrays.asList("Estado activo"), "Estado activo is visible");
		assertAnyVisibleText(Arrays.asList("Idioma seleccionado"), "Idioma seleccionado is visible");
	}

	private void stepValidateTusNegocios() {
		assertAnyVisibleText(Arrays.asList("Tus Negocios"), "Business list section is visible");
		assertAnyVisibleText(Arrays.asList("Agregar Negocio"), "Agregar Negocio button exists");
		assertAnyVisibleText(Arrays.asList("Tienes 2 de 3 negocios"), "Business quota text is visible");
	}

	private void stepValidateLegalLink(final String linkText, final String heading, final String screenshotName) throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(Arrays.asList(linkText));

		final String targetHandle = waitForPossiblyNewTab(handlesBeforeClick);
		if (!originalHandle.equals(targetHandle)) {
			driver.switchTo().window(targetHandle);
		}

		waitForUiToLoad();
		assertAnyVisibleText(Arrays.asList(heading, heading.replace("\u00e9", "e"), heading.replace("\u00ed", "i")),
				heading + " heading is visible");

		final String bodyText = driver.findElement(By.tagName("body")).getText().trim();
		assertTrue("Legal content text is visible", bodyText.length() > 120);
		takeScreenshot(screenshotName);

		final String currentUrl = driver.getCurrentUrl();
		final String reportKey = linkText.startsWith("T") ? "T\u00e9rminos y Condiciones" : "Pol\u00edtica de Privacidad";
		appendToResult(reportKey, "Final URL: " + currentUrl);

		if (!originalHandle.equals(targetHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
	}

	private void typeBusinessNameAndCancel() {
		final List<By> inputCandidates = Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[@type='text']"));

		for (final By locator : inputCandidates) {
			final List<WebElement> candidates = driver.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed() && candidate.isEnabled()) {
					candidate.click();
					candidate.clear();
					candidate.sendKeys("Negocio Prueba Automatizacion");
					clickByVisibleText(Arrays.asList("Cancelar"));
					wait.until(ExpectedConditions.invisibilityOfElementLocated(
							By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]")));
					return;
				}
			}
		}

		clickByVisibleText(Arrays.asList("Cancelar"));
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]")));
	}

	private void assertInputVisibleNearLabel(final String labelText) {
		final By field = By.xpath("//label[contains(normalize-space(.), '" + labelText + "')]/following::input[1]");
		if (isElementVisible(field)) {
			return;
		}

		final By fallback = By.xpath("//input[contains(@placeholder, '" + labelText + "')]");
		assertTrue("Input field for '" + labelText + "' exists", isElementVisible(fallback));
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		final List<By> accountLocators = Arrays.asList(
				By.xpath("//*[contains(normalize-space(.), '" + accountEmail + "')]"),
				By.xpath("//div[@data-identifier='" + accountEmail + "']"));

		for (final By locator : accountLocators) {
			final List<WebElement> matches = driver.findElements(locator);
			for (final WebElement match : matches) {
				if (match.isDisplayed()) {
					match.click();
					waitForUiToLoad();
					return;
				}
			}
		}
	}

	private void expandSidebarIfCollapsed() {
		assertSidebarVisible();
	}

	private void assertSidebarVisible() {
		final boolean sidebarVisible = isElementVisible(By.xpath("//aside")) || isElementVisible(By.xpath("//nav"));
		assertTrue("Left sidebar navigation is visible", sidebarVisible);
	}

	private String waitForPossiblyNewTab(final Set<String> handlesBeforeClick) {
		try {
			return wait.until(driver -> {
				final Set<String> currentHandles = driver.getWindowHandles();
				if (currentHandles.size() > handlesBeforeClick.size()) {
					for (final String handle : currentHandles) {
						if (!handlesBeforeClick.contains(handle)) {
							return handle;
						}
					}
				}
				return driver.getWindowHandle();
			});
		} catch (final TimeoutException timeoutException) {
			return driver.getWindowHandle();
		}
	}

	private void clickByVisibleText(final List<String> candidates) {
		Throwable lastError = null;
		for (final String text : candidates) {
			final By locator = textLocator(text);
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
				element.click();
				waitForUiToLoad();
				return;
			} catch (final Throwable throwable) {
				lastError = throwable;
			}
		}

		throw new AssertionError("Unable to click any of the expected labels: " + candidates, lastError);
	}

	private By textLocator(final String text) {
		return By.xpath("//*[self::a or self::button or self::span or self::div][normalize-space(.)='" + text + "']");
	}

	private boolean isAnyTextVisible(final List<String> texts) {
		for (final String text : texts) {
			if (isElementVisible(textLocator(text))) {
				return true;
			}
		}
		return false;
	}

	private void assertAnyVisibleText(final List<String> texts, final String validationMessage) {
		assertTrue(validationMessage + " (expected one of " + texts + ")", isAnyTextVisible(texts));
	}

	private boolean isElementVisible(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete"
				.equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
	}

	private void runStep(final String name, final CheckedStep step) {
		try {
			step.run();
			stepResults.put(name, StepResult.passed());
		} catch (final Throwable throwable) {
			stepResults.put(name, StepResult.failed(throwable.getMessage()));
		}
	}

	private void appendToResult(final String name, final String note) {
		stepNotes.computeIfAbsent(name, ignored -> new ArrayList<>()).add(note);
		final StepResult current = stepResults.get(name);
		if (current == null) {
			return;
		}
		current.getNotes().add(note);
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, buildSummary());
	}

	private String buildSummary() {
		final List<String> orderedFields = Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Informaci\u00f3n General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"T\u00e9rminos y Condiciones",
				"Pol\u00edtica de Privacidad");

		final StringBuilder summary = new StringBuilder();
		for (final String field : orderedFields) {
			final StepResult result = stepResults.get(field);
			if (result == null) {
				summary.append(field).append(": FAIL (step did not execute)\n");
				continue;
			}

			summary.append(field).append(": ").append(result.isPassed() ? "PASS" : "FAIL");
			if (result.getFailureReason() != null && !result.getFailureReason().isBlank()) {
				summary.append(" - ").append(result.getFailureReason());
			}
			summary.append('\n');

			final List<String> notes = new ArrayList<>(result.getNotes());
			final List<String> deferredNotes = stepNotes.get(field);
			if (deferredNotes != null) {
				notes.addAll(deferredNotes);
			}

			for (final String note : notes) {
				summary.append("  - ").append(note).append('\n');
			}
		}

		return summary.toString();
	}

	private String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value;
	}

	private boolean containsIgnoreCase(final String haystack, final String needle) {
		return haystack.toLowerCase().contains(needle.toLowerCase());
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(evidenceDir.resolve(fileName + ".png"), screenshot);
	}

	private void switchToWindowWithVisibleText(final List<String> expectedTexts) {
		final String originalHandle = driver.getWindowHandle();
		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			waitForUiToLoad();
			if (isAnyTextVisible(expectedTexts)) {
				return;
			}
		}

		driver.switchTo().window(originalHandle);
		throw new AssertionError("Unable to find expected application content in any window: " + expectedTexts);
	}

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}

	private static class StepResult {
		private final boolean passed;
		private final String failureReason;
		private final List<String> notes = new ArrayList<>();

		private StepResult(final boolean passed, final String failureReason) {
			this.passed = passed;
			this.failureReason = failureReason;
		}

		static StepResult passed() {
			return new StepResult(true, null);
		}

		static StepResult failed(final String reason) {
			return new StepResult(false, reason);
		}

		boolean isPassed() {
			return passed;
		}

		String getFailureReason() {
			return failureReason;
		}

		List<String> getNotes() {
			return notes;
		}
	}
}
