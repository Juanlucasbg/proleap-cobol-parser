package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio" module.
 *
 * <p>Configuration:
 * <ul>
 * <li>SALEADS_LOGIN_URL (optional if attaching to an existing browser session)</li>
 * <li>SALEADS_DEBUGGER_ADDRESS (optional, e.g. localhost:9222)</li>
 * <li>SALEADS_HEADLESS (optional, defaults to true)</li>
 * <li>SALEADS_TIMEOUT_SECONDS (optional, defaults to 30)</li>
 * </ul>
 *
 * <p>Evidence is stored under target/saleads-evidence/&lt;timestamp&gt;.
 */
public class SaleadsMiNegocioFullTest {

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter EVIDENCE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final List<String> reportNotes = new ArrayList<>();
	private String termsAndConditionsUrl = "N/A";
	private String privacyPolicyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		initializeFinalReport();

		final String executionTs = LocalDateTime.now().format(EVIDENCE_TS);
		evidenceDirectory = Path.of("target", "saleads-evidence", executionTs);
		Files.createDirectories(evidenceDirectory);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		final String debuggerAddress = trimToNull(System.getenv("SALEADS_DEBUGGER_ADDRESS"));
		if (debuggerAddress != null) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		} else {
			final boolean headless = !"false".equalsIgnoreCase(System.getenv("SALEADS_HEADLESS"));
			if (headless) {
				options.addArguments("--headless=new");
			}
		}

		driver = new ChromeDriver(options);
		final long timeoutSeconds = parseLong(System.getenv("SALEADS_TIMEOUT_SECONDS"), 30L);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		final String loginUrl = trimToNull(System.getenv("SALEADS_LOGIN_URL"));
		if (loginUrl != null) {
			driver.get(loginUrl);
			waitForUiLoad();
		} else {
			final String currentUrl = driver.getCurrentUrl();
			if (currentUrl == null || currentUrl.startsWith("data:") || currentUrl.startsWith("about:")) {
				throw new AssertionError(
						"SALEADS_LOGIN_URL is required unless attaching to an existing session already on SaleADS login.");
			}
		}
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (!"true".equalsIgnoreCase(System.getenv("SALEADS_KEEP_BROWSER_OPEN")) && driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflowFullValidation() throws Exception {
		final boolean loginOk = executeStep("Login", this::stepLoginWithGoogle);
		final boolean menuOk = loginOk && executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		final boolean agregarModalOk = menuOk && executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		final boolean administrarViewOk = menuOk
				&& executeStep("Administrar Negocios view", this::stepOpenAdministrarNegociosView);
		final boolean infoGeneralOk = administrarViewOk
				&& executeStep("Información General", this::stepValidateInformacionGeneral);
		final boolean detallesCuentaOk = administrarViewOk
				&& executeStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		final boolean tusNegociosOk = administrarViewOk && executeStep("Tus Negocios", this::stepValidateTusNegocios);
		final boolean termsOk = administrarViewOk && executeStep("Términos y Condiciones",
				() -> validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08_terminos"));
		final boolean privacyOk = administrarViewOk && executeStep("Política de Privacidad",
				() -> validateLegalLink("Política de Privacidad", "Política de Privacidad", "09_privacidad"));

		final boolean allPassed = loginOk && menuOk && agregarModalOk && administrarViewOk && infoGeneralOk && detallesCuentaOk
				&& tusNegociosOk && termsOk && privacyOk;
		assertTrue("At least one SaleADS Mi Negocio validation failed. See final report in " + evidenceDirectory, allPassed);
	}

	private boolean stepLoginWithGoogle() throws IOException {
		final Set<String> handlesBeforeLoginClick = driver.getWindowHandles();
		final String applicationHandle = driver.getWindowHandle();

		final boolean loginClicked = clickByVisibleText("Sign in with Google", "Iniciar sesión con Google",
				"Iniciar sesion con Google", "Continuar con Google", "Ingresar con Google");
		if (!loginClicked) {
			reportNotes.add("Login with Google button not found.");
			takeScreenshot("01_login_button_not_found");
			return false;
		}

		switchToNewWindowIfOpened(handlesBeforeLoginClick);

		// Best-effort account selection in Google account chooser.
		clickByVisibleText("juanlucasbarbiergarzon@gmail.com");

		// If OAuth opened in a popup, continue from the original application window when available.
		try {
			if (driver.getWindowHandles().contains(applicationHandle)
					&& !safeEquals(driver.getWindowHandle(), applicationHandle)) {
				driver.switchTo().window(applicationHandle);
			}
		} catch (final Exception ignored) {
			// Keep flow resilient while Google auth tab/window is closing.
		}
		waitForUiLoad();

		final boolean sidebarVisible = isAnyVisible(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[@role='navigation']"));
		final boolean appVisible = isTextVisible("Negocio") || isTextVisible("Mi Negocio");
		takeScreenshot("01_dashboard_loaded");
		return sidebarVisible && appVisible;
	}

	private boolean stepOpenMiNegocioMenu() throws IOException {
		// Open the parent section if needed.
		clickByVisibleText("Negocio");
		waitForUiLoad();

		final boolean miNegocioClicked = clickByVisibleText("Mi Negocio");
		final boolean agregarVisible = waitForTextVisible("Agregar Negocio", 12);
		final boolean administrarVisible = waitForTextVisible("Administrar Negocios", 12);

		takeScreenshot("02_mi_negocio_menu_expanded");
		return miNegocioClicked && agregarVisible && administrarVisible;
	}

	private boolean stepValidateAgregarNegocioModal() throws IOException {
		final boolean addBusinessClicked = clickByVisibleText("Agregar Negocio");
		if (!addBusinessClicked) {
			reportNotes.add("'Agregar Negocio' option was not clickable.");
			takeScreenshot("03_agregar_negocio_not_clickable");
			return false;
		}

		final boolean modalTitleVisible = waitForTextVisible("Crear Nuevo Negocio", 12);
		final Optional<WebElement> nombreInput = findVisibleElement(By.xpath(
				"//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')] | //input[contains(@aria-label, 'Nombre del Negocio')]"),
				8);
		final boolean businessCounterVisible = isTextVisible("Tienes 2 de 3 negocios");
		final boolean cancelVisible = isTextVisible("Cancelar");
		final boolean createVisible = isTextVisible("Crear Negocio");

		if (nombreInput.isPresent()) {
			nombreInput.get().click();
			nombreInput.get().sendKeys(Keys.chord(Keys.CONTROL, "a"));
			nombreInput.get().sendKeys("Negocio Prueba Automatización");
		}

		takeScreenshot("03_agregar_negocio_modal");

		// Optional close action requested in the scenario.
		clickByVisibleText("Cancelar");
		waitForUiLoad();

		return modalTitleVisible && nombreInput.isPresent() && businessCounterVisible && cancelVisible && createVisible;
	}

	private boolean stepOpenAdministrarNegociosView() throws IOException {
		// Re-expand if collapsed.
		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		final boolean administrarClicked = clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		final boolean informacionGeneral = waitForTextVisible("Información General", 15);
		final boolean detallesCuenta = waitForTextVisible("Detalles de la Cuenta", 15);
		final boolean tusNegocios = waitForTextVisible("Tus Negocios", 15);
		final boolean seccionLegal = waitForTextVisible("Sección Legal", 15) || waitForTextVisible("Seccion Legal", 15);

		takeScreenshot("04_administrar_negocios_page");
		return administrarClicked && informacionGeneral && detallesCuenta && tusNegocios && seccionLegal;
	}

	private boolean stepValidateInformacionGeneral() {
		final Optional<WebElement> section = findSectionByHeading("Información General");
		if (!section.isPresent()) {
			reportNotes.add("Section 'Información General' not found.");
			return false;
		}

		final String sectionText = section.get().getText();
		final boolean userEmailVisible = EMAIL_PATTERN.matcher(sectionText).find();
		final boolean businessPlanVisible = containsIgnoreCase(sectionText, "BUSINESS PLAN");
		final boolean cambiarPlanVisible = containsIgnoreCase(sectionText, "Cambiar Plan");
		final boolean userNameVisible = hasLikelyUserName(sectionText);

		return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean stepValidateDetallesDeLaCuenta() {
		final Optional<WebElement> section = findSectionByHeading("Detalles de la Cuenta");
		if (!section.isPresent()) {
			reportNotes.add("Section 'Detalles de la Cuenta' not found.");
			return false;
		}

		final String text = section.get().getText();
		return containsIgnoreCase(text, "Cuenta creada") && containsIgnoreCase(text, "Estado activo")
				&& containsIgnoreCase(text, "Idioma seleccionado");
	}

	private boolean stepValidateTusNegocios() {
		final Optional<WebElement> section = findSectionByHeading("Tus Negocios");
		if (!section.isPresent()) {
			reportNotes.add("Section 'Tus Negocios' not found.");
			return false;
		}

		final String text = section.get().getText();
		final boolean listVisible = text.trim().length() > "Tus Negocios".length();
		return listVisible && containsIgnoreCase(text, "Agregar Negocio") && containsIgnoreCase(text, "Tienes 2 de 3 negocios");
	}

	private boolean validateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String appHandle = driver.getWindowHandle();
		final String appUrlBefore = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		final boolean clicked = clickByVisibleText(linkText);
		if (!clicked) {
			reportNotes.add("Could not click legal link: " + linkText);
			return false;
		}

		final String newHandle = waitForNewWindowHandle(handlesBeforeClick, 8);
		final boolean openedNewTab = newHandle != null;
		if (openedNewTab) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		}

		final boolean headingVisible = waitForTextVisible(expectedHeading, 15);
		final String bodyText = safeGetBodyText();
		final boolean legalTextVisible = bodyText.trim().length() > 150;
		final String finalUrl = driver.getCurrentUrl();

		takeScreenshot(screenshotName);

		if ("Términos y Condiciones".equals(linkText)) {
			termsAndConditionsUrl = finalUrl;
		} else if ("Política de Privacidad".equals(linkText)) {
			privacyPolicyUrl = finalUrl;
		}

		// Cleanup: return to the main app tab.
		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiLoad();
		} else if (!safeEquals(appUrlBefore, finalUrl)) {
			driver.navigate().back();
			waitForUiLoad();
		}

		return headingVisible && legalTextVisible;
	}

	private boolean executeStep(final String stepName, final StepAction stepAction) {
		try {
			final boolean result = stepAction.run();
			finalReport.put(stepName, result);
			return result;
		} catch (final Exception e) {
			finalReport.put(stepName, false);
			reportNotes.add(stepName + " failed with error: " + e.getMessage());
			try {
				takeScreenshot("error_" + sanitizeForFileName(stepName));
			} catch (final IOException ignored) {
				// Screenshot failure should not hide the root step failure.
			}
			return false;
		}
	}

	private void switchToNewWindowIfOpened(final Set<String> handlesBeforeClick) {
		final String newHandle = waitForNewWindowHandle(handlesBeforeClick, 10);
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		}
	}

	private String waitForNewWindowHandle(final Set<String> handlesBeforeClick, final long seconds) {
		try {
			return new WebDriverWait(driver, Duration.ofSeconds(seconds)).until(d -> {
				final Set<String> handlesNow = d.getWindowHandles();
				if (handlesNow.size() <= handlesBeforeClick.size()) {
					return null;
				}
				for (final String handle : handlesNow) {
					if (!handlesBeforeClick.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException e) {
			return null;
		}
	}

	private boolean clickByVisibleText(final String... textCandidates) {
		for (final String text : textCandidates) {
			final List<WebElement> elements = driver.findElements(clickableByText(text));
			for (final WebElement element : elements) {
				try {
					if (!element.isDisplayed()) {
						continue;
					}
					wait.until(ExpectedConditions.elementToBeClickable(element));
					element.click();
					waitForUiLoad();
					return true;
				} catch (final Exception clickError) {
					// Fallback with JS click for overlays/interception.
					try {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
						waitForUiLoad();
						return true;
					} catch (final Exception ignored) {
						// Try next candidate element/text.
					}
				}
			}
		}
		return false;
	}

	private boolean waitForTextVisible(final String text, final long seconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(seconds))
					.until(ExpectedConditions.visibilityOfElementLocated(visibleTextBy(text)));
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private boolean isTextVisible(final String text) {
		try {
			return driver.findElements(visibleTextBy(text)).stream().anyMatch(WebElement::isDisplayed);
		} catch (final NoSuchElementException e) {
			return false;
		}
	}

	private boolean isAnyVisible(final By... locators) {
		for (final By locator : locators) {
			if (driver.findElements(locator).stream().anyMatch(WebElement::isDisplayed)) {
				return true;
			}
		}
		return false;
	}

	private Optional<WebElement> findVisibleElement(final By locator, final long timeoutSeconds) {
		try {
			final WebElement element = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return Optional.of(element);
		} catch (final TimeoutException e) {
			return Optional.empty();
		}
	}

	private Optional<WebElement> findSectionByHeading(final String heading) {
		final String headingLiteral = toXPathLiteral(heading);
		final By sectionByHeading = By.xpath(
				"(//*[normalize-space()=" + headingLiteral + "]/ancestor::*[self::section or self::article or self::div][1])[1]");
		return findVisibleElement(sectionByHeading, 10);
	}

	private By visibleTextBy(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]");
	}

	private By clickableByText(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath(
				"//*[self::button or self::a or @role='button'][normalize-space()=" + literal + "]"
						+ " | //*[normalize-space()=" + literal + "]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"
						+ " | //*[normalize-space()=" + literal + "]");
	}

	private void waitForUiLoad() {
		try {
			wait.until(d -> {
				final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return state != null && "complete".equals(state.toString());
			});
		} catch (final Exception ignored) {
			// Keep test resilient when JS execution is blocked by page state.
		}
		sleep(400);
	}

	private Path takeScreenshot(final String checkpointName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String fileName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")) + "_"
				+ sanitizeForFileName(checkpointName) + ".png";
		final Path destination = evidenceDirectory.resolve(fileName);
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		reportNotes.add("Screenshot: " + destination);
		return destination;
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Full Test - Final Report");
		lines.add("Generated at: " + LocalDateTime.now());
		lines.add("");
		lines.add("Step results:");
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			lines.add("- " + entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		lines.add("");
		lines.add("Final URLs:");
		lines.add("- Términos y Condiciones: " + termsAndConditionsUrl);
		lines.add("- Política de Privacidad: " + privacyPolicyUrl);
		lines.add("");
		lines.add("Evidence and notes:");
		lines.addAll(reportNotes.isEmpty() ? List.of("- No additional notes.") : reportNotes);

		final Path reportPath = evidenceDirectory.resolve("final_report.txt");
		Files.write(reportPath, lines);
	}

	private void initializeFinalReport() {
		finalReport.clear();
		finalReport.put("Login", false);
		finalReport.put("Mi Negocio menu", false);
		finalReport.put("Agregar Negocio modal", false);
		finalReport.put("Administrar Negocios view", false);
		finalReport.put("Información General", false);
		finalReport.put("Detalles de la Cuenta", false);
		finalReport.put("Tus Negocios", false);
		finalReport.put("Términos y Condiciones", false);
		finalReport.put("Política de Privacidad", false);
	}

	private String safeGetBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception e) {
			return "";
		}
	}

	private boolean containsIgnoreCase(final String value, final String expected) {
		return value != null && expected != null
				&& value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
	}

	private boolean hasLikelyUserName(final String sectionText) {
		final List<String> excludedLabels = Arrays.asList("Información General", "Informacion General", "BUSINESS PLAN",
				"Cambiar Plan", "Cuenta creada", "Estado activo", "Idioma seleccionado");
		final List<String> candidateLines = Arrays.stream(sectionText.split("\\R")).map(String::trim)
				.filter(line -> !line.isEmpty()).collect(Collectors.toList());
		for (final String line : candidateLines) {
			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (excludedLabels.stream().anyMatch(label -> containsIgnoreCase(line, label))) {
				continue;
			}
			if (line.length() >= 3 && line.length() <= 60 && line.chars().anyMatch(Character::isLetter)) {
				return true;
			}
		}
		return false;
	}

	private long parseLong(final String value, final long fallback) {
		try {
			return value == null ? fallback : Long.parseLong(value.trim());
		} catch (final NumberFormatException e) {
			return fallback;
		}
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String sanitizeForFileName(final String input) {
		return input.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder xpath = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				xpath.append(", \"'\", ");
			}
			xpath.append("'").append(parts[i]).append("'");
		}
		xpath.append(")");
		return xpath.toString();
	}

	private boolean safeEquals(final String left, final String right) {
		if (left == null) {
			return right == null;
		}
		return left.equals(right);
	}

	@FunctionalInterface
	private interface StepAction {
		boolean run() throws Exception;
	}
}
