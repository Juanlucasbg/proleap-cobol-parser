package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio" module.
 *
 * Required runtime config:
 * -Dsaleads.login.url=<login-page-url>
 *
 * Optional runtime config:
 * -Dsaleads.headless=true|false (default: true)
 * -Dsaleads.timeout.seconds=30 (default: 30)
 */
public class SaleAdsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
			Pattern.CASE_INSENSITIVE);

	private static final By SIDEBAR_LOCATOR = By.xpath(
			"//aside[.//*[contains(normalize-space(.), 'Negocio') or contains(normalize-space(.), 'Mi Negocio')]]"
					+ " | //nav[.//*[contains(normalize-space(.), 'Negocio') or contains(normalize-space(.), 'Mi Negocio')]]"
					+ " | //*[(contains(@class, 'sidebar') or contains(@class, 'SideBar'))"
					+ " and .//*[contains(normalize-space(.), 'Negocio') or contains(normalize-space(.), 'Mi Negocio')]]");

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_DETALLES = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Política de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private String appWindowHandle;

	private final Map<String, Boolean> stepReport = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		initializeStepReport();

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean
				.parseBoolean(System.getProperty("saleads.headless", System.getenv().getOrDefault("SALEADS_HEADLESS", "true")));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu");

		driver = new ChromeDriver(options);
		final long timeoutSeconds = Long.parseLong(System.getProperty("saleads.timeout.seconds", "30"));
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDirectory = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDirectory);

		final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		if (loginUrl == null) {
			throw new IllegalArgumentException(
					"Missing login URL. Set -Dsaleads.login.url or SALEADS_LOGIN_URL so the test can open the current environment login page.");
		}

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep(STEP_LOGIN, this::loginWithGoogleAndValidateMainInterface);
		runStep(STEP_MENU, this::openMiNegocioMenuAndValidateOptions);
		runStep(STEP_MODAL, this::validateAgregarNegocioModal);
		runStep(STEP_ADMIN_VIEW, this::openAdministrarNegociosAndValidateSections);
		runStep(STEP_INFO_GENERAL, this::validateInformacionGeneralSection);
		runStep(STEP_DETALLES, this::validateDetallesCuentaSection);
		runStep(STEP_TUS_NEGOCIOS, this::validateTusNegociosSection);
		runStep(STEP_TERMINOS, () -> validateLegalLink("Términos y Condiciones", "terminos-condiciones"));
		runStep(STEP_PRIVACIDAD, () -> validateLegalLink("Política de Privacidad", "politica-privacidad"));

		printFinalReport();
		assertTrue("One or more validations failed. Review the report above.", stepReport.values().stream().allMatch(Boolean::booleanValue));
	}

	private void loginWithGoogleAndValidateMainInterface() throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final WebElement loginButton = waitForAnyVisibleText(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Continuar con Google",
				"Ingresar con Google",
				"Google");
		clickAndWait(loginButton);

		selectGoogleAccountIfPresented();
		switchToMainAppWindow(originalHandle);

		waitForAnyVisible(SIDEBAR_LOCATOR);
		waitForAnyVisibleText("Negocio", "Mi Negocio");

		captureScreenshot("01-dashboard-cargado");
	}

	private void openMiNegocioMenuAndValidateOptions() throws IOException {
		ensureSidebarAvailable();
		clickSidebarItem("Negocio");
		clickSidebarItem("Mi Negocio");

		waitForAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expandido");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickSidebarItem("Agregar Negocio");
		waitForAnyVisibleText("Crear Nuevo Negocio");
		waitForAnyVisibleText("Nombre del Negocio");
		waitForAnyVisibleText("Tienes 2 de 3 negocios");
		waitForAnyVisibleText("Cancelar");
		waitForAnyVisibleText("Crear Negocio");

		final WebElement nombreNegocioInput = waitForVisible(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='nombreNegocio' or @name='businessName']"
						+ " | //label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
		nombreNegocioInput.click();
		nombreNegocioInput.clear();
		nombreNegocioInput.sendKeys("Negocio Prueba Automatización");

		captureScreenshot("03-modal-crear-negocio");
		clickByVisibleText("Cancelar");
		waitForUiToLoad();
		waitForTextToDisappear("Crear Nuevo Negocio");
	}

	private void openAdministrarNegociosAndValidateSections() throws IOException {
		ensureSidebarAvailable();
		if (!isVisibleTextPresent("Administrar Negocios")) {
			clickSidebarItem("Mi Negocio");
		}
		clickSidebarItem("Administrar Negocios");

		waitForAnyVisibleText("Información General");
		waitForAnyVisibleText("Detalles de la Cuenta");
		waitForAnyVisibleText("Tus Negocios");
		waitForAnyVisibleText("Sección Legal");
		captureScreenshot("04-administrar-negocios-vista");
	}

	private void validateInformacionGeneralSection() {
		final WebElement section = waitForSection("Información General");
		final String sectionText = section.getText();
		assertTrue("Expected user name in Información General.", containsLikelyUserName(sectionText));
		assertTrue("Expected user email in Información General.", EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("Expected BUSINESS PLAN text in Información General.", sectionText.contains("BUSINESS PLAN"));
		assertTrue("Expected Cambiar Plan button in Información General.", isTextPresentInside(section, "Cambiar Plan"));
	}

	private void validateDetallesCuentaSection() {
		final WebElement section = waitForSection("Detalles de la Cuenta");
		assertTrue("Expected 'Cuenta creada' in Detalles de la Cuenta.", isTextPresentInside(section, "Cuenta creada"));
		assertTrue("Expected 'Estado activo' in Detalles de la Cuenta.", isTextPresentInside(section, "Estado activo"));
		assertTrue("Expected 'Idioma seleccionado' in Detalles de la Cuenta.", isTextPresentInside(section, "Idioma seleccionado"));
	}

	private void validateTusNegociosSection() {
		final WebElement section = waitForSection("Tus Negocios");
		final String sectionText = section.getText();
		assertTrue("Expected business list in Tus Negocios.", sectionText.contains("Negocio") || section.findElements(By.xpath(".//li | .//tr | .//article | .//div[contains(@class,'card')]")).size() > 0);
		assertTrue("Expected Agregar Negocio button in Tus Negocios.", isTextPresentInside(section, "Agregar Negocio"));
		assertTrue("Expected 'Tienes 2 de 3 negocios' in Tus Negocios.", sectionText.contains("Tienes 2 de 3 negocios"));
	}

	private void validateLegalLink(final String linkText, final String screenshotName) throws IOException {
		final String appHandleBeforeOpen = driver.getWindowHandle();
		final String appUrlBeforeOpen = driver.getCurrentUrl();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText(linkText);

		final String legalWindowHandle = waitForLegalPageToOpen(handlesBefore, appHandleBeforeOpen, appUrlBeforeOpen);
		driver.switchTo().window(legalWindowHandle);
		waitForUiToLoad();

		waitForAnyVisibleText(linkText);
		final String legalPageText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected legal content text on " + linkText + " page.", legalPageText != null && legalPageText.trim().length() > 120);

		captureScreenshot("05-" + screenshotName);
		stepDetails.put(linkText + " URL", driver.getCurrentUrl());

		returnToAppTab(appHandleBeforeOpen, legalWindowHandle);
	}

	private void returnToAppTab(final String appHandleBeforeOpen, final String legalWindowHandle) {
		if (!legalWindowHandle.equals(appHandleBeforeOpen) && driver.getWindowHandles().contains(legalWindowHandle)) {
			driver.close();
		}
		if (driver.getWindowHandles().contains(appHandleBeforeOpen)) {
			driver.switchTo().window(appHandleBeforeOpen);
			waitForUiToLoad();
			return;
		}
		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		}
	}

	private String waitForLegalPageToOpen(final Set<String> handlesBefore, final String appHandleBeforeOpen, final String appUrlBeforeOpen) {
		try {
			wait.until(d -> d.getWindowHandles().size() > handlesBefore.size() || !d.getCurrentUrl().equals(appUrlBeforeOpen));
		} catch (final TimeoutException timeoutException) {
			// Continue: we validate below and fail with clearer assertions if page did not open.
		}

		final Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > handlesBefore.size()) {
			for (final String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					return handle;
				}
			}
		}
		return appHandleBeforeOpen;
	}

	private void selectGoogleAccountIfPresented() {
		final List<WebElement> accountCandidates = visibleElementsByText(GOOGLE_ACCOUNT);
		if (!accountCandidates.isEmpty()) {
			clickAndWait(accountCandidates.get(0));
			return;
		}

		try {
			wait.until(d -> {
				for (final String handle : d.getWindowHandles()) {
					d.switchTo().window(handle);
					final List<WebElement> candidates = visibleElementsByText(GOOGLE_ACCOUNT);
					if (!candidates.isEmpty()) {
						clickAndWait(candidates.get(0));
						return true;
					}
				}
				return false;
			});
		} catch (final TimeoutException timeoutException) {
			// If selector did not appear, an already-authenticated session likely proceeded automatically.
		}
	}

	private void switchToMainAppWindow(final String originalHandle) {
		try {
			wait.until(d -> {
				for (final String handle : d.getWindowHandles()) {
					d.switchTo().window(handle);
					if (isVisible(SIDEBAR_LOCATOR) || isVisibleTextPresent("Mi Negocio") || isVisibleTextPresent("Negocio")) {
						appWindowHandle = handle;
						return true;
					}
				}
				return false;
			});
		} catch (final TimeoutException timeoutException) {
			if (driver.getWindowHandles().contains(originalHandle)) {
				driver.switchTo().window(originalHandle);
			}
		}

		if (appWindowHandle == null) {
			appWindowHandle = driver.getWindowHandle();
		}
		driver.switchTo().window(appWindowHandle);
		waitForUiToLoad();
	}

	private void ensureSidebarAvailable() {
		waitForAnyVisible(SIDEBAR_LOCATOR);
	}

	private WebElement waitForSection(final String headingText) {
		final String headingXpath = "//*[self::h1 or self::h2 or self::h3 or self::h4 or self::p or self::span]"
				+ "[contains(normalize-space(.), " + xpathLiteral(headingText) + ")]";
		final WebElement heading = waitForVisible(By.xpath(headingXpath));
		final By sectionLocator = By.xpath("./ancestor::*[self::section or self::div][1]");
		final List<WebElement> sections = heading.findElements(sectionLocator);
		if (!sections.isEmpty() && sections.get(0).isDisplayed()) {
			return sections.get(0);
		}
		return heading;
	}

	private boolean containsLikelyUserName(final String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		final String[] lines = text.split("\\R");
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.contains("@")) {
				continue;
			}
			if ("INFORMACIÓN GENERAL".equalsIgnoreCase(trimmed)) {
				continue;
			}
			if ("BUSINESS PLAN".equalsIgnoreCase(trimmed)) {
				continue;
			}
			if ("CAMBIAR PLAN".equalsIgnoreCase(trimmed)) {
				continue;
			}
			if (trimmed.length() >= 3) {
				return true;
			}
		}
		return false;
	}

	private boolean isTextPresentInside(final WebElement root, final String text) {
		final String xpath = ".//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]";
		for (final WebElement element : root.findElements(By.xpath(xpath))) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisible(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisibleTextPresent(final String text) {
		return !visibleElementsByText(text).isEmpty();
	}

	private WebElement waitForAnyVisibleText(final String... texts) {
		return wait.until(d -> {
			for (final String text : texts) {
				final List<WebElement> candidates = visibleElementsByText(text);
				if (!candidates.isEmpty()) {
					return candidates.get(0);
				}
			}
			return null;
		});
	}

	private List<WebElement> visibleElementsByText(final String text) {
		final List<WebElement> visible = new ArrayList<>();
		final String exact = "//*[normalize-space(text())=" + xpathLiteral(text) + "]";
		final String contains = "//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]";
		final List<WebElement> candidates = new ArrayList<>(driver.findElements(By.xpath(exact)));
		candidates.addAll(driver.findElements(By.xpath(contains)));
		for (final WebElement element : candidates) {
			try {
				if (element.isDisplayed()) {
					visible.add(element);
				}
			} catch (final Exception ignored) {
				// Ignore stale or detached elements while collecting visible options.
			}
		}
		return visible;
	}

	private WebElement waitForAnyVisible(final By... locators) {
		return wait.until(d -> {
			for (final By locator : locators) {
				final List<WebElement> elements = d.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(d -> {
			final List<WebElement> elements = d.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private void clickSidebarItem(final String text) {
		WebElement sidebar = waitForAnyVisible(SIDEBAR_LOCATOR);
		WebElement target = findVisibleInside(sidebar, text);
		if (target == null) {
			target = waitForAnyVisibleText(text);
		}
		clickAndWait(target);
	}

	private WebElement findVisibleInside(final WebElement root, final String text) {
		final String xpath = ".//*[normalize-space(text())=" + xpathLiteral(text) + "]"
				+ " | .//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]";
		for (final WebElement element : root.findElements(By.xpath(xpath))) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		return null;
	}

	private void clickByVisibleText(final String text) {
		final WebElement candidate = waitForAnyVisibleText(text);
		clickAndWait(candidate);
	}

	private void clickAndWait(final WebElement element) {
		scrollIntoView(element);
		try {
			element.click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void waitForUiToLoad() {
		wait.until((ExpectedCondition<Boolean>) d -> "complete".equals(
				((JavascriptExecutor) d).executeScript("return document.readyState")));
		waitForLoaderToDisappear();
	}

	private void waitForLoaderToDisappear() {
		final By loaders = By.xpath(
				"//*[contains(@class,'loading') or contains(@class,'spinner') or contains(@class,'Loader')"
						+ " or @role='progressbar']");
		wait.until(d -> {
			for (final WebElement loader : d.findElements(loaders)) {
				if (loader.isDisplayed()) {
					return false;
				}
			}
			return true;
		});
	}

	private void waitForTextToDisappear(final String text) {
		wait.until(d -> visibleElementsByText(text).isEmpty());
	}

	private void captureScreenshot(final String name) throws IOException {
		final String safeFileName = name.replaceAll("[^a-zA-Z0-9-_]", "_");
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDirectory.resolve(safeFileName + ".png");
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepReport.put(stepName, Boolean.TRUE);
			stepDetails.put(stepName, "PASS");
		} catch (final Exception exception) {
			stepReport.put(stepName, Boolean.FALSE);
			stepDetails.put(stepName, "FAIL - " + exception.getMessage());
		}
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		for (final Map.Entry<String, Boolean> entry : stepReport.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		System.out.println("--- Details ---");
		for (final Map.Entry<String, String> detail : stepDetails.entrySet()) {
			System.out.println(detail.getKey() + ": " + detail.getValue());
		}
		System.out.println("Evidence folder: " + evidenceDirectory.toAbsolutePath());
		System.out.println("=======================================");
	}

	private void initializeStepReport() {
		stepReport.put(STEP_LOGIN, Boolean.FALSE);
		stepReport.put(STEP_MENU, Boolean.FALSE);
		stepReport.put(STEP_MODAL, Boolean.FALSE);
		stepReport.put(STEP_ADMIN_VIEW, Boolean.FALSE);
		stepReport.put(STEP_INFO_GENERAL, Boolean.FALSE);
		stepReport.put(STEP_DETALLES, Boolean.FALSE);
		stepReport.put(STEP_TUS_NEGOCIOS, Boolean.FALSE);
		stepReport.put(STEP_TERMINOS, Boolean.FALSE);
		stepReport.put(STEP_PRIVACIDAD, Boolean.FALSE);
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int index = 0; index < chars.length; index++) {
			final String fragment = String.valueOf(chars[index]);
			if ("'".equals(fragment)) {
				builder.append("\"").append(fragment).append("\"");
			} else {
				builder.append("'").append(fragment).append("'");
			}
			if (index < chars.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
