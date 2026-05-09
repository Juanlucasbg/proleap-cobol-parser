package io.proleap.saleads;

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
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Full E2E for the SaleADS Mi Negocio workflow.
 *
 * <p>Configuration:
 * <ul>
 *   <li>Environment variable SALEADS_LOGIN_URL or system property saleads.login.url (required).</li>
 *   <li>Environment variable SALEADS_GOOGLE_ACCOUNT_EMAIL (optional, defaults to the requested account).</li>
 *   <li>Environment variable SALEADS_HEADLESS / system property saleads.headless (optional, default true).</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(10);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
	private static final String DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private int screenshotCounter = 0;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		if (isHeadless()) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDirectory = Path.of("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDirectory);

		final String loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getProperty("saleads.login.url"));
		if (loginUrl == null) {
			throw new IllegalStateException(
					"SALEADS_LOGIN_URL (or -Dsaleads.login.url) is required so the test can open the current environment login page.");
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
		executeStep("Login", this::loginWithGoogleAndValidateDashboard);
		executeStep("Mi Negocio menu", this::openMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::openAdministrarNegociosView);
		executeStep("Información General", this::validateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		executeStep("Tus Negocios", this::validateTusNegocios);
		executeStep("Términos y Condiciones",
				() -> validateLegalDocument("Términos y Condiciones", List.of("Términos y Condiciones"), true));
		executeStep("Política de Privacidad",
				() -> validateLegalDocument("Política de Privacidad", List.of("Política de Privacidad"), false));

		printFinalReport();

		final List<String> failed = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.toList();
		assertTrue("Workflow validation failed for: " + failed + ". T&C URL: " + termsUrl + ". Privacy URL: "
				+ privacyUrl + ".", failed.isEmpty());
	}

	private boolean loginWithGoogleAndValidateDashboard() {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeLoginClick = driver.getWindowHandles();

		final boolean loginClicked = clickFirstVisibleText("Sign in with Google", "Iniciar sesión con Google",
				"Continuar con Google");
		if (!loginClicked) {
			return false;
		}

		switchToNewWindowIfOpened(handlesBeforeLoginClick, SHORT_TIMEOUT);
		selectGoogleAccountIfPrompted();

		switchToApplicationWindow(appHandle);
		final boolean mainInterfaceVisible = waitForAnyTextVisible(DEFAULT_TIMEOUT, "Mi Negocio", "Negocio", "Dashboard",
				"Panel");
		final boolean leftSidebarVisible = isElementVisible(
				By.xpath("//aside | //nav | //div[contains(@class, 'sidebar') or contains(@class, 'SideBar')]"),
				SHORT_TIMEOUT) || isAnyTextVisible("Negocio", "Mi Negocio");

		captureScreenshot("dashboard-loaded");
		return mainInterfaceVisible && leftSidebarVisible;
	}

	private boolean openMiNegocioMenu() {
		final boolean clickedMiNegocio = clickFirstVisibleText("Mi Negocio")
				|| (clickFirstVisibleText("Negocio") && clickFirstVisibleText("Mi Negocio"));
		if (!clickedMiNegocio) {
			return false;
		}

		final boolean agregarNegocioVisible = isTextVisible("Agregar Negocio", SHORT_TIMEOUT);
		final boolean administrarVisible = isTextVisible("Administrar Negocios", SHORT_TIMEOUT);
		captureScreenshot("mi-negocio-menu-expanded");
		return agregarNegocioVisible && administrarVisible;
	}

	private boolean validateAgregarNegocioModal() {
		if (!clickFirstVisibleText("Agregar Negocio")) {
			return false;
		}

		final boolean titleVisible = isTextVisible("Crear Nuevo Negocio", SHORT_TIMEOUT);
		final boolean labelVisible = isTextVisible("Nombre del Negocio", SHORT_TIMEOUT);
		final WebElement businessNameInput = findFirstVisibleElement(List.of(
				By.xpath("//label[normalize-space()='Nombre del Negocio']/following::input[1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]")), SHORT_TIMEOUT);
		final boolean inputVisible = businessNameInput != null || labelVisible;
		final boolean quotaVisible = isTextVisible("Tienes 2 de 3 negocios", SHORT_TIMEOUT);
		final boolean cancelVisible = isTextVisible("Cancelar", SHORT_TIMEOUT);
		final boolean createVisible = isTextVisible("Crear Negocio", SHORT_TIMEOUT);

		captureScreenshot("agregar-negocio-modal");

		if (businessNameInput != null) {
			businessNameInput.click();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatizacion");
			waitForUiToLoad();
		}

		clickFirstVisibleText("Cancelar");
		waitForUiToLoad();

		return titleVisible && inputVisible && quotaVisible && cancelVisible && createVisible;
	}

	private boolean openAdministrarNegociosView() {
		if (!isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			clickFirstVisibleText("Mi Negocio");
		}

		final boolean adminClicked = clickFirstVisibleText("Administrar Negocios");
		if (!adminClicked) {
			return false;
		}

		final boolean informacionGeneralVisible = waitForAnyTextVisible(DEFAULT_TIMEOUT, "Información General");
		final boolean detallesVisible = isTextVisible("Detalles de la Cuenta", SHORT_TIMEOUT);
		final boolean tusNegociosVisible = isTextVisible("Tus Negocios", SHORT_TIMEOUT);
		final boolean legalSectionVisible = isTextVisible("Sección Legal", SHORT_TIMEOUT);

		captureScreenshot("administrar-negocios-page");
		return informacionGeneralVisible && detallesVisible && tusNegociosVisible && legalSectionVisible;
	}

	private boolean validateInformacionGeneral() {
		final String sectionText = getSectionText("Información General");
		final boolean userEmailVisible = EMAIL_PATTERN.matcher(sectionText).find();
		final boolean userNameVisible = hasNonEmailUserName(sectionText);
		final boolean businessPlanVisible = sectionText.toUpperCase().contains("BUSINESS PLAN")
				|| isTextVisible("BUSINESS PLAN", SHORT_TIMEOUT);
		final boolean cambiarPlanVisible = sectionText.contains("Cambiar Plan") || isTextVisible("Cambiar Plan", SHORT_TIMEOUT);
		return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean validateDetallesDeLaCuenta() {
		final String sectionText = getSectionText("Detalles de la Cuenta");
		final String normalized = sectionText.toLowerCase();
		final boolean cuentaCreadaVisible = normalized.contains("cuenta creada");
		final boolean estadoActivoVisible = normalized.contains("estado activo");
		final boolean idiomaVisible = normalized.contains("idioma seleccionado");
		return cuentaCreadaVisible && estadoActivoVisible && idiomaVisible;
	}

	private boolean validateTusNegocios() {
		final String sectionText = getSectionText("Tus Negocios");
		final boolean agregarButtonVisible = sectionText.contains("Agregar Negocio") || isTextVisible("Agregar Negocio", SHORT_TIMEOUT);
		final boolean quotaVisible = sectionText.contains("Tienes 2 de 3 negocios") || isTextVisible("Tienes 2 de 3 negocios", SHORT_TIMEOUT);

		final String cleaned = sectionText.replace("Tus Negocios", "").replace("Agregar Negocio", "")
				.replace("Tienes 2 de 3 negocios", "").trim();
		final boolean businessListVisible = cleaned.length() > 5;

		return businessListVisible && agregarButtonVisible && quotaVisible;
	}

	private boolean validateLegalDocument(final String linkText, final List<String> headingOptions,
			final boolean termsDocument) {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		final boolean linkClicked = clickFirstVisibleText(linkText);
		if (!linkClicked) {
			return false;
		}

		final String newHandle = switchToNewWindowIfOpened(handlesBeforeClick, SHORT_TIMEOUT);
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
		}

		waitForUiToLoad();

		boolean headingVisible = false;
		for (final String heading : headingOptions) {
			headingVisible = headingVisible || isTextVisible(heading, DEFAULT_TIMEOUT);
		}

		final boolean legalTextVisible = readBodyText().trim().length() > 120;
		captureScreenshot(termsDocument ? "terminos-y-condiciones" : "politica-de-privacidad");
		final String currentUrl = driver.getCurrentUrl();

		if (termsDocument) {
			termsUrl = currentUrl;
		} else {
			privacyUrl = currentUrl;
		}

		cleanupAndReturnToApplication(appHandle, newHandle);
		return headingVisible && legalTextVisible;
	}

	private void cleanupAndReturnToApplication(final String appHandle, final String legalHandle) {
		if (legalHandle != null) {
			try {
				driver.close();
			} catch (final NoSuchWindowException ignored) {
				// Tab may already be closed by browser policies.
			}

			driver.switchTo().window(appHandle);
			waitForUiToLoad();
			return;
		}

		driver.navigate().back();
		waitForUiToLoad();
	}

	private void executeStep(final String reportField, final StepAction action) {
		boolean passed = false;
		try {
			passed = action.run();
		} catch (final Exception ex) {
			System.err.println("Step '" + reportField + "' failed with exception: " + ex.getMessage());
			captureScreenshot(reportField + "-exception");
		}

		report.put(reportField, passed);
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Workflow Report ===");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			final String result = entry.getValue() ? "PASS" : "FAIL";
			System.out.println(entry.getKey() + ": " + result);
		}
		System.out.println("Términos y Condiciones URL: " + termsUrl);
		System.out.println("Política de Privacidad URL: " + privacyUrl);
		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
	}

	private void selectGoogleAccountIfPrompted() {
		final String accountEmail = firstNonBlank(System.getenv("SALEADS_GOOGLE_ACCOUNT_EMAIL"),
				System.getProperty("saleads.google.account.email"), DEFAULT_ACCOUNT_EMAIL);

		if (isTextVisible(accountEmail, SHORT_TIMEOUT)) {
			clickFirstVisibleText(accountEmail);
			waitForUiToLoad();
		}
	}

	private void switchToApplicationWindow(final String appHandle) {
		if (!driver.getWindowHandles().contains(appHandle)) {
			return;
		}

		driver.switchTo().window(appHandle);
		waitForUiToLoad();
	}

	private String switchToNewWindowIfOpened(final Set<String> handlesBeforeClick, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(d -> d.getWindowHandles().size() > handlesBeforeClick.size());
		} catch (final TimeoutException ignored) {
			return null;
		}

		final Set<String> handlesAfterClick = driver.getWindowHandles();
		for (final String handle : handlesAfterClick) {
			if (!handlesBeforeClick.contains(handle)) {
				return handle;
			}
		}

		return null;
	}

	private String getSectionText(final String headingText) {
		final WebElement heading = findVisibleElement(By.xpath("//*[normalize-space()=" + asXPathLiteral(headingText) + "]"),
				SHORT_TIMEOUT);
		if (heading == null) {
			return "";
		}

		final List<WebElement> possibleContainers = new ArrayList<>(heading
				.findElements(By.xpath("ancestor::*[self::section or self::article or self::div]")));
		possibleContainers.add(heading);

		String bestText = "";
		for (final WebElement container : possibleContainers) {
			try {
				final String text = container.getText();
				if (text != null && text.length() > bestText.length()) {
					bestText = text;
				}
			} catch (final StaleElementReferenceException ignored) {
				// Ignore stale containers and continue with remaining candidates.
			}
		}

		return bestText;
	}

	private boolean hasNonEmailUserName(final String text) {
		final String noEmail = EMAIL_PATTERN.matcher(text).replaceAll(" ");
		final String normalized = noEmail.replace("Información General", "").replace("BUSINESS PLAN", "")
				.replace("Cambiar Plan", "").replaceAll("\\s+", " ").trim();
		return normalized.matches(".*\\p{L}{2,}.*");
	}

	private boolean clickFirstVisibleText(final String... textOptions) {
		for (final String option : textOptions) {
			final WebElement candidate = findFirstVisibleElement(List.of(
					By.xpath("(//button[normalize-space()=" + asXPathLiteral(option) + "])[1]"),
					By.xpath("(//a[normalize-space()=" + asXPathLiteral(option) + "])[1]"),
					By.xpath("(//*[@role='button' and normalize-space()=" + asXPathLiteral(option) + "])[1]"),
					By.xpath("(//*[normalize-space()=" + asXPathLiteral(option) + "])[1]")), SHORT_TIMEOUT);
			if (candidate != null) {
				clickElement(candidate);
				waitForUiToLoad();
				return true;
			}
		}

		return false;
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final Exception ex) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private WebElement findFirstVisibleElement(final List<By> locators, final Duration timeout) {
		for (final By locator : locators) {
			final WebElement element = findVisibleElement(locator, timeout);
			if (element != null) {
				return element;
			}
		}

		return null;
	}

	private WebElement findVisibleElement(final By locator, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final TimeoutException ignored) {
			return null;
		}
	}

	private boolean isElementVisible(final By locator, final Duration timeout) {
		return findVisibleElement(locator, timeout) != null;
	}

	private boolean isAnyTextVisible(final String... textOptions) {
		for (final String text : textOptions) {
			if (isTextVisible(text, SHORT_TIMEOUT)) {
				return true;
			}
		}
		return false;
	}

	private boolean waitForAnyTextVisible(final Duration timeout, final String... textOptions) {
		try {
			return new WebDriverWait(driver, timeout).until(d -> {
				for (final String text : textOptions) {
					final List<WebElement> matches = d
							.findElements(By.xpath("//*[normalize-space()=" + asXPathLiteral(text) + "]"));
					for (final WebElement match : matches) {
						if (match.isDisplayed()) {
							return true;
						}
					}
				}
				return false;
			});
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		return isElementVisible(By.xpath("//*[normalize-space()=" + asXPathLiteral(text) + "]"), timeout);
	}

	private String readBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception ex) {
			return "";
		}
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> {
				try {
					final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
					return "complete".equals(String.valueOf(state));
				} catch (final Exception ignored) {
					return true;
				}
			});
		} catch (final TimeoutException ignored) {
			// If ready state never reaches complete, continue with explicit waits on target elements.
		}

		try {
			Thread.sleep(500);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String label) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String fileName = String.format("%02d-%s.png", ++screenshotCounter, sanitize(label));
		final Path target = evidenceDirectory.resolve(fileName);
		try {
			Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
			System.out.println("Saved screenshot: " + target.toAbsolutePath());
		} catch (final IOException ex) {
			System.err.println("Could not save screenshot '" + fileName + "': " + ex.getMessage());
		}
	}

	private boolean isHeadless() {
		final String value = firstNonBlank(System.getenv("SALEADS_HEADLESS"), System.getProperty("saleads.headless"),
				"true");
		return !"false".equalsIgnoreCase(value);
	}

	private String sanitize(final String raw) {
		return raw.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
	}

	private String asXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface StepAction {
		boolean run() throws Exception;
	}
}
