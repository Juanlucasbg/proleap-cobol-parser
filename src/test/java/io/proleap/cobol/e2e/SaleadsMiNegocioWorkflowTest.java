package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end UI workflow test for SaleADS Mi Negocio module.
 *
 * <p>
 * Runtime configuration:
 * </p>
 * <ul>
 * <li>SALEADS_LOGIN_URL or -Dsaleads.login.url (required)</li>
 * <li>SALEADS_HEADLESS or -Dsaleads.headless (optional, default: true)</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String MODAL_TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final Map<String, String> reportDetails = new LinkedHashMap<>();
	private String appTabHandle;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL or -Dsaleads.login.url to the current environment login page.",
				loginUrl != null && !loginUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.get(loginUrl);
		waitForUiLoad();

		appTabHandle = driver.getWindowHandle();
		screenshotDir = Paths.get("target", "screenshots", "saleads-mi-negocio", TS_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(screenshotDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalDocument("Términos y Condiciones", "Términos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalDocument("Política de Privacidad", "Política de Privacidad"));

		printFinalReport();
		assertTrue("At least one SaleADS workflow section failed. See logs above.", finalReport.values().stream().allMatch(Boolean::booleanValue));
	}

	private void stepLoginWithGoogle() {
		if (!isMainAppVisible()) {
			Set<String> handlesBeforeLoginClick = driver.getWindowHandles();
			clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google");
			handleGoogleAccountSelectionIfPresent(handlesBeforeLoginClick);
			waitUntilMainAppVisible();
		}

		assertVisibleText("Negocio");
		assertSidebarVisible();
		takeScreenshot("01-dashboard-loaded", false);
	}

	private void stepOpenMiNegocioMenu() {
		assertVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded", false);
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");

		assertVisibleText("Crear Nuevo Negocio");
		assertElementExists(By.xpath("//*[contains(normalize-space(.), 'Nombre del Negocio')]"));
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal", false);

		WebElement nameInput = findBusinessNameInput();
		nameInput.click();
		waitForUiLoad();
		nameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), MODAL_TEST_BUSINESS_NAME);
		waitForUiLoad();

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]")));
	}

	private void stepOpenAdministrarNegocios() {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		takeScreenshot("04-administrar-negocios-page", true);
	}

	private void stepValidateInformacionGeneral() {
		assertElementExists(By.xpath(
				"//*[contains(normalize-space(.), 'Información General')]/following::*[not(self::script)][string-length(normalize-space(.)) > 2 and not(contains(normalize-space(.), '@'))][1]"));
		assertElementExists(By.xpath("//section//*[contains(normalize-space(.), '@')] | //main//*[contains(normalize-space(.), '@')]"));
		assertElementExists(By.xpath("//section//*[contains(normalize-space(.), 'BUSINESS PLAN')] | //main//*[contains(normalize-space(.), 'BUSINESS PLAN')]"));
		assertVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertElementExists(By.xpath("//section//*[contains(normalize-space(.), 'Negocio')] | //main//*[contains(normalize-space(.), 'Negocio')]"));
	}

	private void stepValidateLegalDocument(final String linkText, final String headingText) {
		switchToAppTab();
		Set<String> handlesBeforeClick = driver.getWindowHandles();
		String urlBeforeClick = driver.getCurrentUrl();

		clickByVisibleText(linkText);

		boolean newTabOpened = waitForNewTab(handlesBeforeClick, SHORT_TIMEOUT);
		if (newTabOpened) {
			switchToNewestTab(handlesBeforeClick);
		}

		waitForUiLoad();
		assertVisibleText(headingText);
		assertElementExists(By.xpath("//main//*[string-length(normalize-space(.)) > 80] | //body//*[string-length(normalize-space(.)) > 120]"));
		takeScreenshot("legal-" + slug(linkText), false);

		String legalUrl = driver.getCurrentUrl();
		System.out.println("[LEGAL_URL] " + linkText + ": " + legalUrl);

		if (newTabOpened) {
			driver.close();
			switchToAppTab();
		} else if (!urlBeforeClick.equalsIgnoreCase(legalUrl)) {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String reportLabel, final Runnable action) {
		try {
			action.run();
			finalReport.put(reportLabel, Boolean.TRUE);
			reportDetails.put(reportLabel, "PASS");
		} catch (Throwable t) {
			finalReport.put(reportLabel, Boolean.FALSE);
			reportDetails.put(reportLabel, "FAIL - " + t.getMessage());
			takeScreenshot("failure-" + slug(reportLabel), false);
		}
	}

	private void printFinalReport() {
		System.out.println("===== SaleADS Mi Negocio Final Report =====");
		for (Map.Entry<String, String> entry : reportDetails.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
		System.out.println("===========================================");
	}

	private void handleGoogleAccountSelectionIfPresent(final Set<String> handlesBeforeClick) {
		waitForUiLoad();

		boolean newTabOpened = waitForNewTab(handlesBeforeClick, SHORT_TIMEOUT);
		if (newTabOpened) {
			switchToNewestTab(handlesBeforeClick);
		}

		Optional<WebElement> accountOption = findVisibleElementByText(SHORT_TIMEOUT, GOOGLE_ACCOUNT_EMAIL);
		if (accountOption.isPresent()) {
			clickElement(accountOption.get());
		}

		if (newTabOpened) {
			wait.until(driver -> driver.getWindowHandles().size() >= 1);
			if (driver.getWindowHandles().contains(appTabHandle)) {
				switchToAppTab();
			}
		}
	}

	private void waitUntilMainAppVisible() {
		wait.until(driver -> isMainAppVisible());
	}

	private boolean isMainAppVisible() {
		return isSidebarVisible(false) && isTextVisible("Negocio");
	}

	private void assertSidebarVisible() {
		assertTrue("Left sidebar navigation is not visible.", isSidebarVisible(true));
	}

	private boolean isSidebarVisible(final boolean useWait) {
		try {
			WebDriverWait localWait = useWait ? wait : new WebDriverWait(driver, Duration.ofSeconds(1));
			localWait.until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//aside | //nav[.//*[contains(normalize-space(.), 'Negocio')]]")));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	private void assertVisibleText(final String text) {
		assertTrue("Expected visible text not found: " + text, isTextVisible(text));
	}

	private boolean isTextVisible(final String text) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(textLocator(text))));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	private void assertElementExists(final By by) {
		wait.until(ExpectedConditions.presenceOfElementLocated(by));
	}

	private WebElement findBusinessNameInput() {
		List<By> candidates = new ArrayList<>();
		candidates.add(By.xpath("//input[@placeholder='Nombre del Negocio']"));
		candidates.add(By.xpath("//input[@name='businessName']"));
		candidates.add(By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));
		candidates.add(By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));

		for (By candidate : candidates) {
			try {
				WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(candidate));
				if (element != null) {
					return element;
				}
			} catch (TimeoutException ignored) {
				// Try next locator.
			}
		}

		throw new NoSuchElementException("Business name input was not found in Crear Nuevo Negocio modal.");
	}

	private void clickByVisibleText(final String... textOptions) {
		Throwable lastFailure = null;
		for (String text : textOptions) {
			try {
				WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(clickableTextLocator(text))));
				clickElement(element);
				return;
			} catch (Throwable t) {
				lastFailure = t;
			}
		}

		throw new NoSuchElementException("No clickable element found for texts: " + String.join(", ", textOptions)
				+ ". Last error: " + (lastFailure != null ? lastFailure.getMessage() : "none"));
	}

	private Optional<WebElement> findVisibleElementByText(final Duration timeout, final String text) {
		try {
			WebDriverWait localWait = new WebDriverWait(driver, timeout);
			WebElement element = localWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(textLocator(text))));
			return Optional.ofNullable(element);
		} catch (TimeoutException e) {
			return Optional.empty();
		}
	}

	private void clickElement(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiLoad();
	}

	private boolean waitForNewTab(final Set<String> existingHandles, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until((ExpectedCondition<Boolean>) d -> d != null
					&& d.getWindowHandles().size() > existingHandles.size());
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	private void switchToNewestTab(final Set<String> previousHandles) {
		Set<String> handlesAfter = driver.getWindowHandles();
		for (String handle : handlesAfter) {
			if (!previousHandles.contains(handle)) {
				driver.switchTo().window(handle);
				return;
			}
		}
	}

	private void switchToAppTab() {
		if (driver.getWindowHandles().contains(appTabHandle)) {
			driver.switchTo().window(appTabHandle);
		}
		waitForUiLoad();
	}

	private void waitForUiLoad() {
		wait.until(driver -> "complete".equalsIgnoreCase(
				String.valueOf(((JavascriptExecutor) driver).executeScript("return document.readyState"))));
		wait.until(driver -> {
			Object pendingFetches = ((JavascriptExecutor) driver)
					.executeScript("return document.querySelectorAll('.loading,.spinner,[aria-busy=\"true\"]').length;");
			if (pendingFetches instanceof Number) {
				return ((Number) pendingFetches).intValue() == 0;
			}
			return true;
		});
	}

	private void takeScreenshot(final String checkpointName, final boolean fullPage) {
		if (driver == null) {
			return;
		}

		try {
			if (fullPage) {
				resizeForFullPageCapture();
			}

			File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			Path targetFile = screenshotDir.resolve(slug(checkpointName) + ".png");
			Files.copy(screenshot.toPath(), targetFile);
			System.out.println("[SCREENSHOT] " + targetFile.toAbsolutePath());
		} catch (Throwable t) {
			System.out.println("[SCREENSHOT_ERROR] " + checkpointName + ": " + t.getMessage());
		}
	}

	private void resizeForFullPageCapture() {
		try {
			JavascriptExecutor js = (JavascriptExecutor) driver;
			long pageHeight = toLong(js.executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, "
							+ "document.body.offsetHeight, document.documentElement.offsetHeight);"));
			long pageWidth = toLong(js.executeScript(
					"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, "
							+ "document.body.offsetWidth, document.documentElement.offsetWidth);"));

			int targetHeight = (int) Math.min(Math.max(pageHeight, 1080), 5000);
			int targetWidth = (int) Math.min(Math.max(pageWidth, 1280), 1920);
			driver.manage().window().setSize(new Dimension(targetWidth, targetHeight));
			waitForUiLoad();
		} catch (Throwable ignored) {
			// Non-critical.
		}
	}

	private long toLong(final Object value) {
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		return 0L;
	}

	private String readConfig(final String sysProp, final String envVar) {
		return readConfig(sysProp, envVar, null);
	}

	private String readConfig(final String sysProp, final String envVar, final String fallback) {
		String value = System.getProperty(sysProp);
		if (value == null || value.isBlank()) {
			value = System.getenv(envVar);
		}
		if (value == null || value.isBlank()) {
			value = fallback;
		}
		return value;
	}

	private String clickableTextLocator(final String text) {
		String literal = xPathLiteral(text);
		return "("
				+ "//*[self::button or self::a or @role='button'][contains(normalize-space(.), " + literal + ")]"
				+ " | //*[contains(@class, 'menu') or contains(@class, 'nav')][contains(normalize-space(.), " + literal + ")]"
				+ " | //*[@type='button' and contains(normalize-space(.), " + literal + ")]"
				+ ")[1]";
	}

	private String textLocator(final String text) {
		return "//*[contains(normalize-space(.), " + xPathLiteral(text) + ")]";
	}

	private String slug(final String input) {
		String normalized = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		return normalized.isBlank() ? "checkpoint" : normalized;
	}

	private String xPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		StringBuilder sb = new StringBuilder("concat(");
		String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(", \"'\", ");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}
}
