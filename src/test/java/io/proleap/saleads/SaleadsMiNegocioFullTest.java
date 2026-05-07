package io.proleap.saleads;

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
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchWindowException;
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
 * Environment-agnostic end-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * Required runtime configuration:
 * - SALEADS_E2E_ENABLED=true (or -Dsaleads.e2e.enabled=true).
 * - SALEADS_URL (or -Dsaleads.url): login URL for the current environment.
 * - SALEADS_HEADLESS (optional, default true): run browser headless.
 * - SALEADS_ASSUME_CURRENT_PAGE=true (optional): skip navigation and assume the browser is already on login page.
 */
public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		final boolean e2eEnabled = Boolean.parseBoolean(readConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("SaleADS E2E test disabled. Set SALEADS_E2E_ENABLED=true to run.", e2eEnabled);

		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));

		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		evidenceDir = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);

		final boolean assumeCurrentPage = Boolean
				.parseBoolean(readConfig("saleads.assume.current.page", "SALEADS_ASSUME_CURRENT_PAGE", "false"));
		final String loginUrl = readConfig("saleads.url", "SALEADS_URL", "").trim();
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
		} else if (!assumeCurrentPage) {
			throw new IllegalStateException(
					"Missing SaleADS login page. Set SALEADS_URL (or -Dsaleads.url), or set SALEADS_ASSUME_CURRENT_PAGE=true.");
		}
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
	public void saleadsMiNegocioFullTest() throws Exception {
		stepResults.put("Login", executeStep(this::stepLoginWithGoogle));
		stepResults.put("Mi Negocio menu", executeStep(this::stepOpenMiNegocioMenu));
		stepResults.put("Agregar Negocio modal", executeStep(this::stepValidateAgregarNegocioModal));
		stepResults.put("Administrar Negocios view", executeStep(this::stepOpenAdministrarNegociosView));
		stepResults.put("Información General", executeStep(this::stepValidateInformacionGeneral));
		stepResults.put("Detalles de la Cuenta", executeStep(this::stepValidateDetallesDeLaCuenta));
		stepResults.put("Tus Negocios", executeStep(this::stepValidateTusNegocios));
		stepResults.put("Términos y Condiciones",
				executeStep(() -> stepValidateLegalLink(new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
						new String[] { "Términos y Condiciones", "Terminos y Condiciones" }, "08-terminos-y-condiciones.png")));
		stepResults.put("Política de Privacidad",
				executeStep(() -> stepValidateLegalLink(new String[] { "Política de Privacidad", "Politica de Privacidad" },
						new String[] { "Política de Privacidad", "Politica de Privacidad" }, "09-politica-de-privacidad.png")));

		final List<String> failedSteps = stepResults.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		assertTrue("Workflow validation failed for steps: " + failedSteps, failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickByVisibleText("Sign in with Google", "Iniciar con Google", "Continuar con Google", "Google");
		waitForUiToLoad();

		handleGoogleAccountSelectorIfPresent(handlesBeforeClick);

		waitForAnyVisibleText("Negocio", "Mi Negocio");
		assertTrue("Main application interface is not visible", isAnyVisible(By.xpath("//aside"), By.xpath("//nav")));
		assertTrue("Left sidebar navigation is not visible", isAnyVisible(By.xpath("//aside//*[contains(., 'Negocio')]"),
				By.xpath("//nav//*[contains(., 'Negocio')]"), byVisibleText("Negocio")));

		takeScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByVisibleText("Negocio");
		waitForUiToLoad();
		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal.png");

		final WebElement nombreInput = findFirstVisible(By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(., 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@name, 'nombre') or contains(@id, 'nombre')]"));
		nombreInput.click();
		nombreInput.sendKeys("Negocio Prueba Automatizacion");
		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegociosView() throws IOException {
		if (!isAnyVisible(byVisibleText("Administrar Negocios"))) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertAnyTextVisible("Información General", "Informacion General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertAnyTextVisible("Sección Legal", "Seccion Legal");
		takeScreenshot("04-administrar-negocios-view-full.png");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement infoSection = findSectionByHeading("Información General", "Informacion General");
		final String sectionText = infoSection.getText();

		assertTrue("Expected user email in Informacion General",
				EMAIL_PATTERN.matcher(sectionText).find() || EMAIL_PATTERN.matcher(getPageText()).find());
		assertTrue("Expected visible user name in Informacion General", hasLikelyUserName(sectionText));
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
	}

	private void stepValidateDetallesDeLaCuenta() {
		findSectionByHeading("Detalles de la Cuenta");
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement negociosSection = findSectionByHeading("Tus Negocios");
		final String sectionText = negociosSection.getText();

		assertTrue("Expected business list content in Tus Negocios", sectionText.replace("Tus Negocios", "").trim().length() > 20);
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(final String[] linkTextOptions, final String[] expectedHeadingOptions, final String screenshotFileName)
			throws IOException {
		final String appHandle = driver.getWindowHandle();
		final String appUrlBefore = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkTextOptions);
		waitForUiToLoad();

		final String maybeNewHandle = waitForNewWindow(handlesBeforeClick, Duration.ofSeconds(10));
		boolean openedNewTab = maybeNewHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(maybeNewHandle);
			waitForUiToLoad();
		}

		waitForAnyVisibleText(expectedHeadingOptions);
		final String bodyText = getPageText();
		assertTrue("Legal content text should be visible for " + expectedHeadingOptions[0], bodyText.length() > 200);
		takeScreenshot(screenshotFileName);
		System.out.println(expectedHeadingOptions[0] + " final URL: " + driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
			wait.until(ExpectedConditions.urlToBe(appUrlBefore));
		}
		waitForUiToLoad();
	}

	private void handleGoogleAccountSelectorIfPresent(final Set<String> handlesBeforeClick) {
		final String popupHandle = waitForNewWindow(handlesBeforeClick, Duration.ofSeconds(12));
		if (popupHandle != null) {
			try {
				driver.switchTo().window(popupHandle);
				waitForUiToLoad();
				clickVisibleIfPresent(Duration.ofSeconds(8), byVisibleText(GOOGLE_ACCOUNT_EMAIL),
						By.xpath("//*[contains(normalize-space(), " + toXPathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]"));
				waitForUiToLoad();
			} catch (NoSuchWindowException ignored) {
				// Google popup may auto-close immediately after account selection.
			}
		} else {
			clickVisibleIfPresent(Duration.ofSeconds(3), byVisibleText(GOOGLE_ACCOUNT_EMAIL),
					By.xpath("//*[contains(normalize-space(), " + toXPathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]"));
		}

		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (!driver.getCurrentUrl().contains("accounts.google.com")) {
				return;
			}
		}
	}

	private boolean executeStep(final CheckedRunnable step) {
		try {
			step.run();
			return true;
		} catch (final Exception ex) {
			System.err.println("Step failed: " + ex.getMessage());
			ex.printStackTrace(System.err);
			return false;
		}
	}

	private void clickByVisibleText(final String... textOptions) {
		final List<By> candidateLocators = new ArrayList<>();
		for (final String text : textOptions) {
			candidateLocators.add(byVisibleText(text));
			candidateLocators.add(By.xpath("//button[contains(normalize-space(), " + toXPathLiteral(text) + ")]"));
			candidateLocators.add(By.xpath("//a[contains(normalize-space(), " + toXPathLiteral(text) + ")]"));
		}

		final WebElement target = findFirstVisible(candidateLocators.toArray(new By[0]));
		target.click();
	}

	private void clickVisibleIfPresent(final Duration timeout, final By... locators) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		for (final By locator : locators) {
			try {
				final WebElement element = shortWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				element.click();
				return;
			} catch (final TimeoutException ignored) {
				// Try the next locator.
			}
		}
	}

	private WebElement findFirstVisible(final By... locators) {
		TimeoutException lastTimeout = null;
		for (final By locator : locators) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final TimeoutException timeoutException) {
				lastTimeout = timeoutException;
			}
		}
		throw new NoSuchElementException("Could not find visible element for provided locators."
				+ (lastTimeout == null ? "" : " Last timeout: " + lastTimeout.getMessage()));
	}

	private void assertTextVisible(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
	}

	private void assertAnyTextVisible(final String... textOptions) {
		final List<By> locators = new ArrayList<>();
		for (final String textOption : textOptions) {
			locators.add(byVisibleText(textOption));
		}
		findFirstVisible(locators.toArray(new By[0]));
	}

	private boolean isAnyVisible(final By... locators) {
		for (final By locator : locators) {
			try {
				if (!driver.findElements(locator).isEmpty() && driver.findElement(locator).isDisplayed()) {
					return true;
				}
			} catch (final RuntimeException ignored) {
				// Continue trying other locators.
			}
		}
		return false;
	}

	private void waitForAnyVisibleText(final String... texts) {
		final List<By> locators = new ArrayList<>();
		for (final String text : texts) {
			locators.add(byVisibleText(text));
		}
		findFirstVisible(locators.toArray(new By[0]));
	}

	private WebElement findSectionByHeading(final String... headingOptions) {
		final List<By> locators = new ArrayList<>();
		for (final String heading : headingOptions) {
			locators.add(By.xpath("//section[.//*[contains(normalize-space(), " + toXPathLiteral(heading) + ")]]"));
			locators.add(By.xpath("//div[.//*[contains(normalize-space(), " + toXPathLiteral(heading) + ")]]"));
			locators.add(By.xpath(
					"//*[contains(normalize-space(), " + toXPathLiteral(heading) + ")]/ancestor::*[self::section or self::div][1]"));
		}
		return findFirstVisible(locators.toArray(new By[0]));
	}

	private void waitForUiToLoad() {
		wait.until(driverInstance -> "complete".equals(((JavascriptExecutor) driverInstance).executeScript("return document.readyState")));
		try {
			Thread.sleep(700);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), evidenceDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private String waitForNewWindow(final Set<String> oldHandles, final Duration timeout) {
		final long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			final Set<String> currentHandles = driver.getWindowHandles();
			for (final String handle : currentHandles) {
				if (!oldHandles.contains(handle)) {
					return handle;
				}
			}
			try {
				Thread.sleep(250);
			} catch (final InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	private String getPageText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private boolean hasLikelyUserName(final String sectionText) {
		for (final String line : sectionText.split("\\R")) {
			final String cleaned = line.trim();
			if (cleaned.isEmpty() || "Información General".equalsIgnoreCase(cleaned) || "Informacion General".equalsIgnoreCase(cleaned)
					|| "BUSINESS PLAN".equalsIgnoreCase(cleaned) || "Cambiar Plan".equalsIgnoreCase(cleaned)
					|| EMAIL_PATTERN.matcher(cleaned).find()) {
				continue;
			}
			if (cleaned.length() > 2 && cleaned.contains(" ")) {
				return true;
			}
		}
		return false;
	}

	private By byVisibleText(final String text) {
		return By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + " or contains(normalize-space(), " + toXPathLiteral(text) + ")]");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(", \"'\", ");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	private String readConfig(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return defaultValue;
	}

	private void printFinalReport() {
		if (stepResults.isEmpty()) {
			return;
		}
		System.out.println("SaleADS Mi Negocio Final Report");
		for (final Map.Entry<String, Boolean> entry : stepResults.entrySet()) {
			System.out.printf("- %s: %s%n", entry.getKey(), entry.getValue() ? "PASS" : "FAIL");
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
