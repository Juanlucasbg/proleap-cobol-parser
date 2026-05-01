package io.proleap.saleads.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Environment-agnostic E2E test for the SaleADS Mi Negocio workflow.
 *
 * Runtime notes:
 * - Starts from the current URL loaded in the browser after boot.
 * - Does not depend on a specific domain.
 * - Uses visible-text-first locators and captures screenshots.
 *
 * Optional system properties:
 * -Dsaleads.startUrl=https://...    (if browser should navigate first)
 * -Dsaleads.googleEmail=...         (google selector account hint)
 * -Dsaleads.headless=true|false     (default true)
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final List<String> reportLines = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDir;
	private Path reportFile;
	private String appWindowHandle;
	private String googleEmail;

	@Before
	public void setUp() throws IOException {
		final boolean e2eEnabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue("Set -Dsaleads.e2e.enabled=true to execute this E2E test.", e2eEnabled);

		googleEmail = System.getProperty("saleads.googleEmail", DEFAULT_GOOGLE_EMAIL);

		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--lang=es-ES");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		driver.manage().window().setSize(new Dimension(1920, 1080));
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		String startUrl = System.getProperty("saleads.startUrl");
		if (startUrl == null || startUrl.isBlank()) {
			startUrl = System.getenv("SALEADS_START_URL");
		}

		if (startUrl != null && !startUrl.isBlank()) {
			driver.get(startUrl);
		}

		if (driver.getCurrentUrl() == null || driver.getCurrentUrl().startsWith("data:") || "about:blank".equals(driver.getCurrentUrl())) {
			throw new IllegalStateException(
				"No SaleADS login page loaded. Pass -Dsaleads.startUrl or SALEADS_START_URL in the environment.");
		}

		appWindowHandle = driver.getWindowHandle();
		createArtifactsLocations();
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
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		if (stepStatus.containsValue("FAIL")) {
			Assert.fail("One or more validation steps failed. Review the generated report: " + reportFile);
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		waitForUiSettled();
		WebElement googleButton = waitForVisibleClickableByText(
			"Sign in with Google",
			"Iniciar con Google",
			"Iniciar sesión con Google",
			"Continuar con Google",
			"Google"
		);
		safeClick(googleButton);
		waitForUiSettled();

		final String previousHandle = appWindowHandle;
		switchToNewestWindowIfOpened(previousHandle);

		maybeSelectGoogleAccount(googleEmail);

		switchBackToApplication(previousHandle);
		waitForUiSettled();

		assertAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Panel", "Inicio");
		assertSidebarVisible();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		waitForUiSettled();
		WebElement negocioSection = waitForVisibleClickableByText("Negocio", "Mi Negocio");
		safeClick(negocioSection);
		waitForUiSettled();

		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		WebElement addBusiness = waitForVisibleClickableByText("Agregar Negocio");
		safeClick(addBusiness);
		waitForUiSettled();

		assertAnyVisibleText("Crear Nuevo Negocio");
		findVisibleElementContainingText("Nombre del Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleClickableByText("Cancelar");
		waitForVisibleClickableByText("Crear Negocio");

		captureScreenshot("03-crear-negocio-modal");

		WebElement negocioNameInput = findInputNearLabel("Nombre del Negocio");
		if (negocioNameInput != null) {
			negocioNameInput.click();
			negocioNameInput.sendKeys("Negocio Prueba Automatizacion");
		}

		WebElement cancelButton = waitForVisibleClickableByText("Cancelar");
		safeClick(cancelButton);
		waitForUiSettled();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureMiNegocioExpanded();
		WebElement manageBusinesses = waitForVisibleClickableByText("Administrar Negocios");
		safeClick(manageBusinesses);
		waitForUiSettled();

		assertAnyVisibleText("Informacion General", "Información General");
		assertAnyVisibleText("Detalles de la Cuenta");
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Seccion Legal", "Sección Legal");
		captureScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		assertProfileNameAndEmailVisible();
		assertAnyVisibleText("BUSINESS PLAN");
		waitForVisibleClickableByText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertAnyVisibleText("Cuenta creada");
		assertAnyVisibleText("Estado activo");
		assertAnyVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertAnyVisibleText("Tus Negocios");
		waitForVisibleClickableByText("Agregar Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		validateLegalLink(
			"Terminos y Condiciones",
			"Términos y Condiciones",
			"08-terminos-condiciones"
		);
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		validateLegalLink(
			"Politica de Privacidad",
			"Política de Privacidad",
			"09-politica-privacidad"
		);
	}

	private void validateLegalLink(
		final String fallbackLinkText,
		final String expectedHeadingText,
		final String screenshotName
	) throws IOException {
		WebElement legalLink = waitForVisibleClickableByText(expectedHeadingText, fallbackLinkText);
		final String previousHandle = driver.getWindowHandle();
		final Set<String> previousHandles = driver.getWindowHandles();

		safeClick(legalLink);
		waitForUiSettled();

		boolean openedNewTab = waitForNewWindow(previousHandles);
		if (openedNewTab) {
			switchToNewestWindowIfOpened(previousHandle);
		}

		waitForUiSettled();
		assertAnyVisibleText(expectedHeadingText, fallbackLinkText);
		assertLegalContentVisible();

		captureScreenshot(screenshotName);
		appendReport("URL " + expectedHeadingText + ": " + driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(previousHandle);
		} else {
			driver.navigate().back();
			waitForUiSettled();
		}
		waitForUiSettled();
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			markStep(stepName, true, null);
		} catch (Exception ex) {
			markStep(stepName, false, ex.getMessage());
		}
	}

	private void markStep(final String stepName, final boolean passed, final String details) {
		final String status = passed ? "PASS" : "FAIL";
		stepStatus.put(stepName, status);
		final String line = passed
			? stepName + ": PASS"
			: stepName + ": FAIL - " + (details == null ? "Unknown error" : details);
		appendReport(line);
	}

	private void writeFinalReport() throws IOException {
		if (reportFile == null) {
			return;
		}

		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		sb.append("Final Step Report").append(System.lineSeparator());
		sb.append(System.lineSeparator());

		final List<String> orderedFields = List.of(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad"
		);

		for (String field : orderedFields) {
			sb.append(field)
				.append(": ")
				.append(stepStatus.getOrDefault(field, "NOT_RUN"))
				.append(System.lineSeparator());
		}

		sb.append(System.lineSeparator()).append("Details").append(System.lineSeparator());
		for (String line : reportLines) {
			sb.append("- ").append(line).append(System.lineSeparator());
		}

		Files.writeString(reportFile, sb.toString(), StandardCharsets.UTF_8);
	}

	private void createArtifactsLocations() throws IOException {
		final Path reportsRoot = Path.of("target", "surefire-reports");
		Files.createDirectories(reportsRoot);
		screenshotsDir = reportsRoot.resolve("saleads-mi-negocio-screenshots");
		Files.createDirectories(screenshotsDir);
		reportFile = reportsRoot.resolve("saleads-mi-negocio-final-report.txt");
	}

	private void captureScreenshot(final String name) throws IOException {
		final Path screenshotPath = screenshotsDir.resolve(name + ".png");
		final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshotPath, bytes);
		appendReport("Screenshot: " + screenshotPath);
	}

	private void appendReport(final String message) {
		reportLines.add(message);
	}

	private void ensureMiNegocioExpanded() {
		if (isAnyTextVisible("Agregar Negocio") && isAnyTextVisible("Administrar Negocios")) {
			return;
		}
		WebElement negocioSection = waitForVisibleClickableByText("Negocio", "Mi Negocio");
		safeClick(negocioSection);
		waitForUiSettled();
	}

	private void maybeSelectGoogleAccount(final String email) {
		final List<By> candidateSelectors = List.of(
			By.xpath("//*[contains(normalize-space(.),'" + escapeForXpath(email) + "')]"),
			By.xpath("//div[contains(@data-email,'" + escapeForXpath(email) + "')]"),
			By.xpath("//div[contains(@aria-label,'" + escapeForXpath(email) + "')]")
		);

		for (By selector : candidateSelectors) {
			try {
				WebElement account = shortWait().until(ExpectedConditions.visibilityOfElementLocated(selector));
				safeClick(account);
				waitForUiSettled();
				return;
			} catch (TimeoutException ignored) {
				// Account selector might be skipped in already-authenticated flows.
			}
		}
	}

	private void switchToNewestWindowIfOpened(final String currentWindow) {
		Set<String> handles = driver.getWindowHandles();
		if (handles.size() <= 1) {
			return;
		}
		for (String handle : handles) {
			if (!handle.equals(currentWindow)) {
				driver.switchTo().window(handle);
			}
		}
	}

	private void switchBackToApplication(final String fallbackHandle) {
		Set<String> handles = driver.getWindowHandles();
		if (handles.contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		} else if (handles.contains(fallbackHandle)) {
			driver.switchTo().window(fallbackHandle);
			appWindowHandle = fallbackHandle;
		} else if (!handles.isEmpty()) {
			String firstHandle = handles.iterator().next();
			driver.switchTo().window(firstHandle);
			appWindowHandle = firstHandle;
		}
	}

	private boolean waitForNewWindow(final Set<String> previousHandles) {
		try {
			shortWait().until((ExpectedCondition<Boolean>) wd -> wd != null && wd.getWindowHandles().size() > previousHandles.size());
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	private void waitForUiSettled() {
		wait.until(webDriver -> {
			if (webDriver == null) {
				return false;
			}
			String readyState = (String) ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
			return "complete".equals(readyState) || "interactive".equals(readyState);
		});
	}

	private void assertSidebarVisible() {
		List<By> sidebarCandidates = List.of(
			By.cssSelector("aside"),
			By.cssSelector("[class*='sidebar']"),
			By.cssSelector("nav")
		);
		for (By candidate : sidebarCandidates) {
			try {
				WebElement element = shortWait().until(ExpectedConditions.visibilityOfElementLocated(candidate));
				if (element.isDisplayed()) {
					return;
				}
			} catch (TimeoutException ignored) {
				// try next selector
			}
		}
		throw new AssertionError("Left sidebar navigation not visible.");
	}

	private void assertAnyVisibleText(final String... textCandidates) {
		if (!isAnyTextVisible(textCandidates)) {
			throw new AssertionError("None of these texts are visible: " + String.join(", ", textCandidates));
		}
	}

	private boolean isAnyTextVisible(final String... textCandidates) {
		for (String text : textCandidates) {
			try {
				findVisibleElementContainingText(text);
				return true;
			} catch (TimeoutException ignored) {
				// check next text
			}
		}
		return false;
	}

	private WebElement waitForVisibleClickableByText(final String... textCandidates) {
		for (String text : textCandidates) {
			List<By> selectors = buildVisibleTextSelectors(text);
			for (By selector : selectors) {
				try {
					return wait.until(ExpectedConditions.elementToBeClickable(selector));
				} catch (TimeoutException ignored) {
					// keep trying
				}
			}
		}
		throw new AssertionError("Clickable element not found by text: " + String.join(", ", textCandidates));
	}

	private WebElement findVisibleElementContainingText(final String text) {
		for (By selector : buildVisibleTextSelectors(text)) {
			try {
				return shortWait().until(ExpectedConditions.visibilityOfElementLocated(selector));
			} catch (TimeoutException ignored) {
				// continue
			}
		}
		throw new TimeoutException("Visible text not found: " + text);
	}

	private List<By> buildVisibleTextSelectors(final String text) {
		final String escaped = escapeForXpath(text);
		final String lowered = escapeForXpath(text.toLowerCase(Locale.ROOT));
		List<By> selectors = new ArrayList<>();
		selectors.add(By.xpath("//*[normalize-space(text())='" + escaped + "']"));
		selectors.add(By.xpath("//*[contains(normalize-space(.),'" + escaped + "')]"));
		selectors.add(By.xpath("//*[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzáéíóúüñ'), '" + lowered + "')]"));
		return selectors;
	}

	private WebElement findInputNearLabel(final String labelText) {
		try {
			WebElement label = findVisibleElementContainingText(labelText);
			WebElement maybeInput = label.findElement(By.xpath(".//following::input[1]"));
			if (maybeInput.isDisplayed()) {
				return maybeInput;
			}
		} catch (NoSuchElementException | TimeoutException ignored) {
			// fallback below
		}

		List<By> fallbacks = List.of(
			By.xpath("//input[@placeholder='Nombre del Negocio']"),
			By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"),
			By.cssSelector("input[type='text']")
		);
		for (By selector : fallbacks) {
			try {
				return shortWait().until(ExpectedConditions.visibilityOfElementLocated(selector));
			} catch (TimeoutException ignored) {
				// try next
			}
		}
		return null;
	}

	private void safeClick(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void assertLegalContentVisible() {
		List<String> candidateTexts = List.of("condiciones", "privacidad", "datos", "uso", "terminos", "política");
		for (String text : candidateTexts) {
			if (isAnyTextVisible(text)) {
				return;
			}
		}
		throw new AssertionError("Legal content text was not detected.");
	}

	private void assertProfileNameAndEmailVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final Matcher emailMatcher = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
			.matcher(bodyText);
		if (!emailMatcher.find()) {
			throw new AssertionError("No visible user email was detected in Informacion General.");
		}

		final String[] lines = bodyText.split("\\R");
		final int emailLineIndex = findLineIndexContaining(lines, emailMatcher.group());
		if (emailLineIndex < 0) {
			throw new AssertionError("User email exists but its display line could not be determined.");
		}

		final Pattern likelyNamePattern = Pattern.compile("^[\\p{L}][\\p{L}\\s.'-]{2,}$");
		final boolean nameVisible = isLikelyNameLine(lines, emailLineIndex - 1, likelyNamePattern)
			|| isLikelyNameLine(lines, emailLineIndex + 1, likelyNamePattern);

		if (!nameVisible) {
			throw new AssertionError("User name line was not detected near the visible email.");
		}
	}

	private int findLineIndexContaining(final String[] lines, final String value) {
		for (int i = 0; i < lines.length; i++) {
			if (lines[i] != null && lines[i].contains(value)) {
				return i;
			}
		}
		return -1;
	}

	private boolean isLikelyNameLine(final String[] lines, final int index, final Pattern pattern) {
		if (index < 0 || index >= lines.length) {
			return false;
		}
		final String trimmed = lines[index].trim();
		return !trimmed.isEmpty()
			&& !trimmed.contains("@")
			&& pattern.matcher(trimmed).matches();
	}

	private WebDriverWait shortWait() {
		return new WebDriverWait(driver, Duration.ofSeconds(8));
	}

	private String escapeForXpath(final String input) {
		if (!input.contains("'")) {
			return input;
		}

		String[] parts = input.split("'");
		StringBuilder concat = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			concat.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				concat.append(",\"'\",");
			}
		}
		concat.append(")");
		return concat.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
