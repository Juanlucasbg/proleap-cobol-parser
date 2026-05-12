package io.proleap.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import java.util.Map;
import java.util.Set;

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

public class SaleAdsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String loginUrl;

	@Before
	public void setUp() throws IOException {
		loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("SALEADS_LOGIN_URL must point to the login page of the current environment.",
				loginUrl != null && !loginUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		if (!"false".equalsIgnoreCase(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDir = Paths.get("target", "saleads-evidence", TS_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();

		runStep(report, failures, "Login", this::stepLoginWithGoogle);
		runStep(report, failures, "Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep(report, failures, "Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep(report, failures, "Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep(report, failures, "Informacion General", this::stepValidateInformacionGeneral);
		runStep(report, failures, "Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep(report, failures, "Tus Negocios", this::stepValidateTusNegocios);
		runStep(report, failures, "Terminos y Condiciones",
				() -> legalUrls.put("Terminos y Condiciones URL",
						validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos-condiciones")));
		runStep(report, failures, "Politica de Privacidad",
				() -> legalUrls.put("Politica de Privacidad URL",
						validateLegalLink("Política de Privacidad", "Política de Privacidad", "09-politica-privacidad")));

		printFinalReport(report, legalUrls);
		assertFalse("Failures:\n" + String.join("\n", failures), !failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		driver.get(loginUrl);
		waitForUiToLoad();

		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Iniciar con Google");
		handleGoogleAccountSelector(appHandle, handlesBeforeClick);

		assertAnyVisible(By.cssSelector("aside"), By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar')]"));
		assertTextVisible("Negocio");
		saveScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		assertTextVisible("Negocio");
		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		saveScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		saveScreenshot("03-agregar-negocio-modal");

		final WebElement nombreNegocioInput = findInputForNombreNegocio();
		nombreNegocioInput.click();
		nombreNegocioInput.clear();
		nombreNegocioInput.sendKeys("Negocio Prueba Automatizacion");
		clickByVisibleText("Cancelar");
		waitUntilNotVisible(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']"));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureMiNegocioMenuExpanded();
		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		saveScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("Información General");
		assertEmailVisible();
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertAnyVisible(By.xpath("//*[contains(@class,'business')]"), By.xpath("//table"), By.xpath("//ul"),
				By.xpath("//div[contains(@class,'card')]"));
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private String validateLegalLink(final String linkText, final String expectedHeading, final String screenshotPrefix)
			throws IOException {
		final String appHandle = driver.getWindowHandle();
		final String urlBeforeClick = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForLegalNavigation(urlBeforeClick, handlesBeforeClick);

		final Set<String> handlesAfterClick = driver.getWindowHandles();
		final boolean openedNewTab = handlesAfterClick.size() > handlesBeforeClick.size();
		if (openedNewTab) {
			final String newHandle = resolveNewHandle(handlesBeforeClick, handlesAfterClick);
			driver.switchTo().window(newHandle);
		}

		waitForUiToLoad();
		assertTextVisible(expectedHeading);
		assertLegalContentVisible();
		saveScreenshot(screenshotPrefix);

		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		assertTextVisible("Sección Legal");
		return finalUrl;
	}

	private void handleGoogleAccountSelector(final String appHandle, final Set<String> handlesBeforeClick) {
		waitForGoogleWindowOrSelector(handlesBeforeClick);
		final Set<String> handlesAfterClick = driver.getWindowHandles();

		if (handlesAfterClick.size() > handlesBeforeClick.size()) {
			final String newHandle = resolveNewHandle(handlesBeforeClick, handlesAfterClick);
			driver.switchTo().window(newHandle);
		}

		clickByVisibleTextIfPresent(GOOGLE_ACCOUNT_EMAIL, "Choose an account", "Elige una cuenta");

		if (!appHandle.equals(driver.getWindowHandle()) && driver.getWindowHandles().contains(appHandle)) {
			driver.switchTo().window(appHandle);
		}

		waitForUiToLoad();
	}

	private void ensureMiNegocioMenuExpanded() {
		if (!isVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"))) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}
	}

	private WebElement findInputForNombreNegocio() {
		final List<By> inputCandidates = List.of(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"));

		for (final By candidate : inputCandidates) {
			if (isVisible(candidate)) {
				return wait.until(ExpectedConditions.elementToBeClickable(candidate));
			}
		}

		throw new AssertionError("Could not find input field 'Nombre del Negocio'.");
	}

	private void clickByVisibleText(final String... textOptions) {
		for (final String text : textOptions) {
			final By locator = By.xpath("//*[normalize-space()=" + asXpathLiteral(text) + "]");
			if (isVisible(locator)) {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
				clickElement(element);
				return;
			}
		}

		throw new AssertionError("Could not find clickable element with text: " + String.join(", ", textOptions));
	}

	private void clickByVisibleTextIfPresent(final String... textOptions) {
		for (final String text : textOptions) {
			final By locator = By.xpath("//*[normalize-space()=" + asXpathLiteral(text) + "]");
			if (isVisible(locator)) {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
				clickElement(element);
				return;
			}
		}
	}

	private void clickElement(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		try {
			element.click();
		} catch (final Exception clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> ((JavascriptExecutor) webDriver).executeScript("return document.readyState")
				.toString()
				.equals("complete"));
	}

	private void waitForGoogleWindowOrSelector(final Set<String> handlesBeforeClick) {
		new WebDriverWait(driver, DEFAULT_TIMEOUT).until(webDriver -> {
			final boolean newWindowOpened = webDriver.getWindowHandles().size() > handlesBeforeClick.size();
			final boolean emailIsVisible = isVisible(
					By.xpath("//*[contains(normalize-space(), " + asXpathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]"));
			return newWindowOpened || emailIsVisible;
		});
	}

	private void waitForLegalNavigation(final String urlBeforeClick, final Set<String> handlesBeforeClick) {
		new WebDriverWait(driver, DEFAULT_TIMEOUT).until(webDriver -> webDriver.getWindowHandles().size() > handlesBeforeClick.size()
				|| !urlBeforeClick.equals(webDriver.getCurrentUrl()));
	}

	private void waitUntilNotVisible(final By locator) {
		wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
	}

	private boolean isVisible(final By locator) {
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void assertTextVisible(final String text) {
		final By locator = By.xpath("//*[normalize-space()=" + asXpathLiteral(text) + "]");
		assertTrue("Expected visible text: " + text, isVisible(locator));
	}

	private void assertEmailVisible() {
		final By emailLocator = By.xpath("//*[contains(text(),'@') and contains(text(),'.')]");
		assertTrue("Expected at least one visible email in account information.", isVisible(emailLocator));
	}

	private void assertLegalContentVisible() {
		final List<WebElement> legalParagraphs = driver.findElements(By.xpath("//p[normalize-space()]"));
		boolean foundMeaningfulText = false;
		for (final WebElement paragraph : legalParagraphs) {
			if (paragraph.isDisplayed() && paragraph.getText().trim().length() > 80) {
				foundMeaningfulText = true;
				break;
			}
		}
		assertTrue("Expected legal content text to be visible.", foundMeaningfulText);
	}

	private void assertAnyVisible(final By... options) {
		for (final By option : options) {
			if (isVisible(option)) {
				return;
			}
		}
		throw new AssertionError("None of the expected elements were visible.");
	}

	private String resolveNewHandle(final Set<String> previousHandles, final Set<String> currentHandles) {
		for (final String handle : currentHandles) {
			if (!previousHandles.contains(handle)) {
				return handle;
			}
		}
		throw new AssertionError("A new tab/window was expected but no new handle was found.");
	}

	private Path saveScreenshot(final String checkpointName) throws IOException {
		assertNotNull("WebDriver is not initialized.", driver);
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(checkpointName + ".png");
		Files.copy(screenshot.toPath(), target);
		return target;
	}

	private void runStep(final Map<String, Boolean> report, final List<String> failures, final String stepName,
			final StepAction action) {
		try {
			action.run();
			report.put(stepName, Boolean.TRUE);
		} catch (final Throwable throwable) {
			report.put(stepName, Boolean.FALSE);
			final String message = throwable.getMessage() == null || throwable.getMessage().isBlank()
					? throwable.getClass().getSimpleName()
					: throwable.getMessage();
			failures.add(stepName + " -> " + message);
		}
	}

	private void printFinalReport(final Map<String, Boolean> report, final Map<String, String> legalUrls) {
		System.out.println("==== SaleADS Mi Negocio Final Report ====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder concat = new StringBuilder("concat(");
		for (int index = 0; index < parts.length; index++) {
			if (index > 0) {
				concat.append(", \"'\", ");
			}
			concat.append("'").append(parts[index]).append("'");
		}
		concat.append(")");
		return concat.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
