package io.proleap.saleads;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
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

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String TERMS_TEXT = "T\u00e9rminos y Condiciones";
	private static final String PRIVACY_TEXT = "Pol\u00edtica de Privacidad";
	private static final String LEGAL_SECTION_TEXT = "Secci\u00f3n Legal";

	private static final List<String> REPORT_FIELDS = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Informaci\u00f3n General", "Detalles de la Cuenta", "Tus Negocios",
			"T\u00e9rminos y Condiciones", "Pol\u00edtica de Privacidad");

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration CLICK_STABILIZATION_DELAY = Duration.ofMillis(750);

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String applicationWindowHandle;
	private StepResult activeStep;

	@Before
	public void setUp() throws IOException {
		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDir = createEvidenceDir();

		final String loginUrl = readEnv("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
		} else {
			final String currentUrl = driver.getCurrentUrl();
			final boolean blankPage = currentUrl == null || currentUrl.isBlank() || "about:blank".equals(currentUrl)
					|| "data:,".equals(currentUrl);
			if (blankPage) {
				throw new IllegalStateException(
						"Browser started on a blank page. Provide SALEADS_LOGIN_URL or start from a preloaded SaleADS login session.");
			}
		}

		applicationWindowHandle = driver.getWindowHandle();
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		printFinalReport();
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
		runStep("Informaci\u00f3n General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("T\u00e9rminos y Condiciones", () -> stepValidateLegalLink(TERMS_TEXT));
		runStep("Pol\u00edtica de Privacidad", () -> stepValidateLegalLink(PRIVACY_TEXT));

		final List<String> failedSteps = new ArrayList<>();
		for (String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			if (result == null || !result.passed) {
				failedSteps.add(field);
			}
		}

		Assert.assertTrue("One or more workflow validations failed: " + failedSteps, failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		final Set<String> handlesBeforeLogin = driver.getWindowHandles();
		final WebElement loginButton = findFirstVisibleByExactOrContainsText(List.of("Sign in with Google", "Google",
				"Iniciar sesi\u00f3n con Google", "Continuar con Google", "Acceder con Google"));
		clickAndWait(loginButton);

		handleGoogleAccountSelectionIfPresent(handlesBeforeLogin, "juanlucasbarbiergarzon@gmail.com");

		// Main interface + left sidebar validations.
		assertAnyVisibleText(List.of("Negocio", "Mi Negocio"));
		findSidebarOrFail();

		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		final WebElement sidebar = findSidebarOrFail();
		openMiNegocioMenu(sidebar);

		assertAnyVisibleText(List.of("Agregar Negocio"));
		assertAnyVisibleText(List.of("Administrar Negocios"));
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickAndWait(findVisibleElementByText("Agregar Negocio"));

		assertAnyVisibleText(List.of("Crear Nuevo Negocio"));
		findInputForNombreNegocio();
		assertAnyVisibleText(List.of("Tienes 2 de 3 negocios"));
		findVisibleElementByText("Cancelar");
		findVisibleElementByText("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal");

		// Optional interaction requested by workflow.
		final WebElement nombreInput = findInputForNombreNegocio();
		nombreInput.click();
		nombreInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "Negocio Prueba Automatizaci\u00f3n");
		clickAndWait(findVisibleElementByText("Cancelar"));
		assertModalClosed("Crear Nuevo Negocio");
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureMiNegocioMenuExpanded();
		clickAndWait(findVisibleElementByText("Administrar Negocios"));

		assertAnyVisibleText(List.of("Informaci\u00f3n General"));
		assertAnyVisibleText(List.of("Detalles de la Cuenta"));
		assertAnyVisibleText(List.of("Tus Negocios"));
		assertAnyVisibleText(List.of(LEGAL_SECTION_TEXT));
		takeScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisibleText(List.of("BUSINESS PLAN"));
		findVisibleElementByText("Cambiar Plan");

		final String bodyText = getBodyText();
		assertContainsEmail(bodyText);
		assertLikelyUsernameVisible(bodyText);
	}

	private void stepValidateDetallesCuenta() {
		assertAnyVisibleText(List.of("Cuenta creada"));
		assertAnyVisibleText(List.of("Estado activo"));
		assertAnyVisibleText(List.of("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionRootByHeading("Tus Negocios");
		findElementInside(section, By.xpath(".//*[normalize-space()='Agregar Negocio']"));
		assertAnyVisibleText(List.of("Tienes 2 de 3 negocios"));

		final List<WebElement> businessEntries = section
				.findElements(By.xpath(".//ul/li | .//ol/li | .//table/tbody/tr | .//table/tr[position()>1]"));
		final String sectionText = normalizeWhitespace(section.getText());
		final boolean hasStructuredList = !businessEntries.isEmpty();
		final boolean hasMeaningfulTextBeyondHeading = sectionText.length() > 60;

		Assert.assertTrue("No visible business list/content was found in 'Tus Negocios'.",
				hasStructuredList || hasMeaningfulTextBeyondHeading);
	}

	private void stepValidateLegalLink(final String linkText) throws IOException {
		waitForUiToLoad();

		final String currentAppHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String urlBeforeClick = driver.getCurrentUrl();
		final WebElement link = findVisibleElementByText(linkText);
		clickAndWait(link);

		final String openedHandle = waitForNewTabOrNavigation(handlesBeforeClick, urlBeforeClick);
		final boolean openedNewTab = openedHandle != null;
		if (openedNewTab) {
			driver.switchTo().window(openedHandle);
			waitForUiToLoad();
		}

		if (TERMS_TEXT.equals(linkText)) {
			assertAnyVisibleText(List.of(TERMS_TEXT, "Terminos y Condiciones"));
		} else if (PRIVACY_TEXT.equals(linkText)) {
			assertAnyVisibleText(List.of(PRIVACY_TEXT, "Politica de Privacidad"));
		}

		final String legalBodyText = normalizeWhitespace(getBodyText());
		Assert.assertTrue("Legal page content appears empty for: " + linkText, legalBodyText.length() > 120);

		final String screenshotName = TERMS_TEXT.equals(linkText) ? "05-terms-page" : "06-privacy-page";
		takeScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();
		appendDetail(report.get(linkText), "Final URL: " + finalUrl);

		// Cleanup: return to the application tab.
		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(currentAppHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void runStep(final String stepName, final CheckedRunnable action) {
		final StepResult result = new StepResult();
		report.put(stepName, result);
		activeStep = result;
		try {
			action.run();
			result.passed = true;
			if (result.details.isBlank()) {
				result.details = "PASS";
			}
		} catch (Throwable ex) {
			result.passed = false;
			result.details = ex.getMessage() == null ? ex.getClass().getSimpleName()
					: ex.getClass().getSimpleName() + ": " + ex.getMessage();
		} finally {
			activeStep = null;
		}
	}

	private void appendDetail(final StepResult result, final String detail) {
		if (result == null || detail == null || detail.isBlank()) {
			return;
		}

		if (result.details == null || result.details.isBlank() || "PASS".equals(result.details)) {
			result.details = detail;
		} else {
			result.details = result.details + " | " + detail;
		}
	}

	private WebDriver createDriver() {
		final String remoteUrl = readEnv("SALEADS_WEBDRIVER_URL");
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		final String headless = readEnv("SALEADS_HEADLESS");
		if (headless != null && Boolean.parseBoolean(headless)) {
			options.addArguments("--headless=new");
		}

		if (remoteUrl != null && !remoteUrl.isBlank()) {
			try {
				return new RemoteWebDriver(new URL(remoteUrl), options);
			} catch (MalformedURLException ex) {
				throw new IllegalArgumentException("Invalid SALEADS_WEBDRIVER_URL: " + remoteUrl, ex);
			}
		}

		return new ChromeDriver(options);
	}

	private Path createEvidenceDir() throws IOException {
		final String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC)
				.format(Instant.now());
		final Path outputDir = Path.of("target", "saleads-evidence", stamp);
		Files.createDirectories(outputDir);
		return outputDir;
	}

	private void takeScreenshot(final String name) throws IOException {
		waitForUiToLoad();
		final File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(name + ".png");
		Files.copy(src.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		appendDetail(activeStep, "Screenshot: " + target);
	}

	private void waitForUiToLoad() {
		wait.until((ExpectedCondition<Boolean>) wd -> "complete"
				.equals(((JavascriptExecutor) wd).executeScript("return document.readyState")));
		try {
			Thread.sleep(CLICK_STABILIZATION_DELAY.toMillis());
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiToLoad();
	}

	private void handleGoogleAccountSelectionIfPresent(final Set<String> handlesBeforeLogin, final String email) {
		try {
			wait.until(driver -> driver.getWindowHandles().size() > handlesBeforeLogin.size() || isTextVisible(email));
		} catch (TimeoutException ignored) {
			// Continue and attempt account selector lookup in current window.
		}

		final Set<String> handlesAfterLogin = driver.getWindowHandles();
		for (String handle : handlesAfterLogin) {
			driver.switchTo().window(handle);
			waitForUiToLoad();
			final List<WebElement> emailCandidates = findElementsByText(email);
			if (!emailCandidates.isEmpty()) {
				clickAndWait(emailCandidates.get(0));
				break;
			}
		}

		if (handlesAfterLogin.contains(applicationWindowHandle)) {
			driver.switchTo().window(applicationWindowHandle);
			waitForUiToLoad();
		}
	}

	private WebElement findSidebarOrFail() {
		return findAnyVisibleElement(List.of(
				By.xpath("//aside[.//*[contains(normalize-space(), 'Negocio') or contains(normalize-space(), 'Mi Negocio')]]"),
				By.xpath("//nav[.//*[contains(normalize-space(), 'Negocio') or contains(normalize-space(), 'Mi Negocio')]]")));
	}

	private void openMiNegocioMenu(final WebElement sidebar) {
		final List<By> selectors = List.of(By.xpath(".//*[normalize-space()='Mi Negocio']"),
				By.xpath(".//*[normalize-space()='Negocio']"));
		for (By selector : selectors) {
			try {
				final WebElement element = findElementInside(sidebar, selector);
				clickAndWait(element);
				if (isTextVisible("Agregar Negocio") || isTextVisible("Administrar Negocios")) {
					return;
				}
			} catch (RuntimeException ignored) {
				// Try next selector.
			}
		}

		throw new AssertionError("Could not expand the Mi Negocio menu.");
	}

	private void ensureMiNegocioMenuExpanded() {
		if (!isTextVisible("Administrar Negocios") || !isTextVisible("Agregar Negocio")) {
			openMiNegocioMenu(findSidebarOrFail());
		}
	}

	private void assertContainsEmail(final String text) {
		final Pattern emailPattern = Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");
		final Matcher matcher = emailPattern.matcher(text);
		Assert.assertTrue("Expected a visible user email in Informaci\u00f3n General.", matcher.find());
	}

	private void assertLikelyUsernameVisible(final String text) {
		final String normalized = normalizeWhitespace(text);
		final boolean knownEmailUserVisible = normalized.contains("juanlucasbarbiergarzon@gmail.com");

		boolean hasNonEmailIdentityLine = false;
		for (String line : text.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.contains("@")) {
				continue;
			}
			if (trimmed.equalsIgnoreCase("Informaci\u00f3n General") || trimmed.equalsIgnoreCase("BUSINESS PLAN")
					|| trimmed.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}
			if (trimmed.length() > 2) {
				hasNonEmailIdentityLine = true;
				break;
			}
		}

		Assert.assertTrue("Expected a visible user name in Informaci\u00f3n General.",
				knownEmailUserVisible || hasNonEmailIdentityLine);
	}

	private WebElement findSectionRootByHeading(final String heading) {
		final WebElement headingElement = findVisibleElementByText(heading);
		try {
			return headingElement.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		} catch (NoSuchElementException ex) {
			return driver.findElement(By.tagName("body"));
		}
	}

	private String waitForNewTabOrNavigation(final Set<String> handlesBeforeClick, final String urlBeforeClick) {
		try {
			wait.until(driver -> {
				final Set<String> currentHandles = driver.getWindowHandles();
				if (currentHandles.size() > handlesBeforeClick.size()) {
					return true;
				}
				final String currentUrl = driver.getCurrentUrl();
				return currentUrl != null && !currentUrl.equals(urlBeforeClick);
			});
		} catch (TimeoutException ex) {
			throw new AssertionError("Neither new tab nor navigation was detected after clicking legal link.");
		}

		final Set<String> handlesAfterClick = driver.getWindowHandles();
		for (String handle : handlesAfterClick) {
			if (!handlesBeforeClick.contains(handle)) {
				return handle;
			}
		}

		return null;
	}

	private void assertModalClosed(final String modalTitle) {
		try {
			wait.until(ExpectedConditions.invisibilityOfElementLocated(
					By.xpath("//*[normalize-space()=" + quoteXpath(modalTitle) + "]")));
		} catch (TimeoutException ex) {
			throw new AssertionError("Modal did not close after clicking Cancelar.");
		}
	}

	private WebElement findInputForNombreNegocio() {
		return findAnyVisibleElement(List.of(By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[normalize-space()='Nombre del Negocio']/following::input[1]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@name, 'nombre') or contains(@id, 'nombre')]")));
	}

	private WebElement findVisibleElementByText(final String text) {
		final List<WebElement> candidates = findElementsByText(text);
		if (candidates.isEmpty()) {
			throw new AssertionError("No visible element found with text: " + text);
		}
		return candidates.get(0);
	}

	private List<WebElement> findElementsByText(final String text) {
		final By exact = By.xpath("//*[normalize-space()=" + quoteXpath(text) + "]");
		final By contains = By.xpath("//*[contains(normalize-space(), " + quoteXpath(text) + ")]");
		final List<WebElement> results = new ArrayList<>();
		results.addAll(driver.findElements(exact));
		results.addAll(driver.findElements(contains));
		results.removeIf(e -> !e.isDisplayed());
		return results;
	}

	private WebElement findFirstVisibleByExactOrContainsText(final List<String> texts) {
		for (String text : texts) {
			final List<WebElement> candidates = findElementsByText(text);
			if (!candidates.isEmpty()) {
				return candidates.get(0);
			}
		}
		throw new AssertionError("No visible element matched any expected text: " + texts);
	}

	private void assertAnyVisibleText(final List<String> texts) {
		for (String text : texts) {
			if (isTextVisible(text)) {
				return;
			}
		}
		throw new AssertionError("None of the expected visible texts were found: " + texts);
	}

	private boolean isTextVisible(final String text) {
		return !findElementsByText(text).isEmpty();
	}

	private WebElement findAnyVisibleElement(final List<By> locators) {
		for (By locator : locators) {
			try {
				final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				if (element.isDisplayed()) {
					return element;
				}
			} catch (TimeoutException ignored) {
				// Try next locator.
			}
		}
		throw new AssertionError("No visible element found for expected locators: " + locators);
	}

	private WebElement findElementInside(final WebElement root, final By locator) {
		final List<WebElement> candidates = root.findElements(locator);
		for (WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				return candidate;
			}
		}
		throw new AssertionError("No visible nested element found for locator: " + locator);
	}

	private String getBodyText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private String normalizeWhitespace(final String value) {
		return value == null ? "" : value.replaceAll("\\s+", " ").trim();
	}

	private String quoteXpath(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		final String[] parts = text.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(",\"'\",");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private String readEnv(final String key) {
		return System.getenv(key);
	}

	private void printFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("\nSALEADS_MI_NEGOCIO_FINAL_REPORT\n");
		for (String field : REPORT_FIELDS) {
			final StepResult step = report.get(field);
			if (step == null) {
				builder.append("- ").append(field).append(": FAIL (Not executed)\n");
				continue;
			}
			builder.append("- ").append(field).append(": ").append(step.passed ? "PASS" : "FAIL");
			if (step.details != null && !step.details.isBlank()) {
				builder.append(" | ").append(step.details);
			}
			builder.append('\n');
		}
		System.out.println(builder);
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static class StepResult {
		private boolean passed;
		private String details = "";
	}
}
