package io.proleap.e2e.saleads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Términos y Condiciones";
	private static final String STEP_PRIVACY = "Política de Privacidad";

	private final Map<String, String> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appHandle;
	private String selectedGoogleEmail;

	@Before
	public void setUp() throws IOException {
		initializeReport();

		final boolean enabled = Boolean.parseBoolean(env("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"Set SALEADS_E2E_ENABLED=true to run this live UI test against a SaleADS environment.",
				enabled);

		driver = createDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(intEnv("SALEADS_WAIT_SECONDS", 25)));
		evidenceDir = Paths.get("target", "saleads-evidence", DIR_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		selectedGoogleEmail = env("SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com");

		final String startUrl = System.getenv("SALEADS_START_URL");
		if (!isBlank(startUrl)) {
			driver.get(startUrl);
		}
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() throws Exception {
		executeStep(STEP_LOGIN, this::stepLoginWithGoogle);
		executeStep(STEP_MENU, this::stepOpenMiNegocioMenu);
		executeStep(STEP_MODAL, this::stepValidateAgregarNegocioModal);
		executeStep(STEP_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		executeStep(STEP_INFO_GENERAL, this::stepValidateInformacionGeneral);
		executeStep(STEP_ACCOUNT_DETAILS, this::stepValidateDetallesCuenta);
		executeStep(STEP_BUSINESSES, this::stepValidateTusNegocios);
		executeStep(STEP_TERMS, this::stepValidateTerminos);
		executeStep(STEP_PRIVACY, this::stepValidatePoliticaPrivacidad);
	}

	private void stepLoginWithGoogle() throws Exception {
		final String originalHandle = driver.getWindowHandle();

		clickOneOfTexts("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google", "Google");
		waitForUiToLoad();

		if (waitForNewWindow(Duration.ofSeconds(10))) {
			switchToNewestWindow(originalHandle);
			waitForUiToLoad();
		}

		selectGoogleAccountIfPrompted(selectedGoogleEmail);

		if (!driver.getWindowHandle().equals(originalHandle) && driver.getWindowHandles().contains(originalHandle)) {
			driver.switchTo().window(originalHandle);
		}

		waitForUiToLoad();
		assertVisibleText("Negocio");
		assertSidebarVisible();
		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		assertSidebarVisible();
		clickVisibleText("Negocio");
		waitForUiToLoad();
		clickVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		takeScreenshot("03_agregar_negocio_modal");

		typeIntoInputNearLabel("Nombre del Negocio", "Negocio Prueba Automatizacion");
		clickVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		expandMiNegocioIfNeeded();
		clickVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertOneOfVisibleTexts("Información General", "Informacion General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertOneOfVisibleTexts("Sección Legal", "Seccion Legal");
		takeScreenshot("04_administrar_negocios_view");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = sectionContainerByHeading("Información General", "Informacion General");
		final String text = section.getText();

		assertTrue("Expected an email in Informacion General.", text.contains("@"));
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
		assertTrue("Expected a user name-like text in Informacion General.",
				hasLikelyUserNameLine(text));
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

		final List<WebElement> cards = visibleElements(By.xpath(
				"//*[contains(@class,'business') or contains(@class,'negocio') or self::li or self::article][normalize-space()]"));
		assertFalse("Expected at least one visible business list item.", cards.isEmpty());
	}

	private void stepValidateTerminos() throws Exception {
		validateLegalLink(STEP_TERMS, "08_terminos_y_condiciones", "Términos y Condiciones", "Terminos y Condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		validateLegalLink(STEP_PRIVACY, "09_politica_de_privacidad", "Política de Privacidad", "Politica de Privacidad");
	}

	private void validateLegalLink(final String legalReportName, final String screenshotName, final String... legalTexts)
			throws Exception {
		appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickOneOfTexts(legalTexts);
		waitForUiToLoad();

		boolean openedNewTab = waitForNewWindow(Duration.ofSeconds(10));
		if (openedNewTab) {
			switchToNewestWindow(appHandle);
			waitForUiToLoad();
		}

		assertOneOfVisibleTexts(legalTexts);
		assertLegalContentVisible();
		takeScreenshot(screenshotName);
		legalUrls.put(legalReportName, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
			return;
		}

		final Set<String> handlesAfterClick = new LinkedHashSet<>(driver.getWindowHandles());
		if (handlesAfterClick.equals(handlesBeforeClick)) {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void selectGoogleAccountIfPrompted(final String email) {
		final By emailLocator = By.xpath(
				"//*[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '"
						+ email.toLowerCase(Locale.ROOT) + "')]");
		final List<WebElement> candidates = visibleElements(emailLocator);
		if (candidates.isEmpty()) {
			return;
		}
		clickWithUiWait(candidates.get(0));
		waitForUiToLoad();
	}

	private void clickOneOfTexts(final String... texts) {
		Throwable lastError = null;
		for (final String text : texts) {
			try {
				clickVisibleText(text);
				return;
			} catch (final Throwable t) {
				lastError = t;
			}
		}
		throw new AssertionError("Could not find a clickable element with any of these texts: " + String.join(", ", texts),
				lastError);
	}

	private void clickVisibleText(final String text) {
		final List<By> locators = new ArrayList<>();
		locators.add(By.xpath("//*[normalize-space()='" + text + "']"));
		locators.add(By.xpath("//*[contains(normalize-space(),'" + text + "')]"));
		locators.add(By.xpath("//button[normalize-space()='" + text + "']"));
		locators.add(By.xpath("//a[normalize-space()='" + text + "']"));

		for (final By locator : locators) {
			final List<WebElement> elements = visibleElements(locator);
			if (elements.isEmpty()) {
				continue;
			}
			clickWithUiWait(elements.get(0));
			return;
		}

		throw new AssertionError("Element with visible text not found: " + text);
	}

	private void assertVisibleText(final String text) {
		final By exact = By.xpath("//*[normalize-space()='" + text + "']");
		final By contains = By.xpath("//*[contains(normalize-space(),'" + text + "')]");
		try {
			wait.until(ExpectedConditions.or(
					ExpectedConditions.visibilityOfElementLocated(exact),
					ExpectedConditions.visibilityOfElementLocated(contains)));
		} catch (final TimeoutException e) {
			throw new AssertionError("Expected visible text not found: " + text, e);
		}
	}

	private void assertOneOfVisibleTexts(final String... texts) {
		Throwable lastError = null;
		for (final String text : texts) {
			try {
				assertVisibleText(text);
				return;
			} catch (final Throwable t) {
				lastError = t;
			}
		}
		throw new AssertionError("None of the expected texts are visible: " + String.join(", ", texts), lastError);
	}

	private void assertSidebarVisible() {
		final List<WebElement> sidebarCandidates = visibleElements(By.xpath("//aside | //nav"));
		assertFalse("Expected left sidebar navigation to be visible.", sidebarCandidates.isEmpty());
	}

	private void assertLegalContentVisible() {
		final WebElement body = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
		final String bodyText = body.getText().trim();
		assertTrue("Expected legal page text content to be visible.", bodyText.length() > 120);
	}

	private void typeIntoInputNearLabel(final String label, final String value) {
		final By nearLabel = By.xpath(
				"//label[contains(normalize-space(),'" + label + "')]/following::input[1] | " +
				"//*[contains(normalize-space(),'" + label + "')]/following::input[1]");
		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(nearLabel));
		input.clear();
		input.sendKeys(value);
		waitForUiToLoad();
	}

	private WebElement sectionContainerByHeading(final String... headingTexts) {
		for (final String headingText : headingTexts) {
			final By sectionLocator = By.xpath(
					"//*[contains(normalize-space(),'" + headingText + "')]/ancestor::*[self::section or self::div][1]");
			final List<WebElement> sections = visibleElements(sectionLocator);
			if (!sections.isEmpty()) {
				return sections.get(0);
			}
		}
		throw new AssertionError("Section heading not found for any of: " + String.join(", ", headingTexts));
	}

	private boolean hasLikelyUserNameLine(final String sectionText) {
		final String[] lines = sectionText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.contains("@")) {
				continue;
			}
			if ("Informacion General".equalsIgnoreCase(line) || "Información General".equalsIgnoreCase(line)) {
				continue;
			}
			if ("BUSINESS PLAN".equalsIgnoreCase(line) || "Cambiar Plan".equalsIgnoreCase(line)) {
				continue;
			}
			if (line.length() >= 3) {
				return true;
			}
		}
		return false;
	}

	private void expandMiNegocioIfNeeded() {
		if (!visibleElements(By.xpath("//*[contains(normalize-space(),'Administrar Negocios')]")).isEmpty()) {
			return;
		}
		clickVisibleText("Mi Negocio");
		waitForUiToLoad();
	}

	private void clickWithUiWait(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		} catch (final Exception ignored) {
			// Non-fatal for drivers/pages that do not allow JS execution in current context.
		}

		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final Exception clickError) {
			try {
				new Actions(driver).moveToElement(element).click().perform();
			} catch (final Exception actionError) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			}
		}
		waitForUiToLoad();
	}

	private List<WebElement> visibleElements(final By locator) {
		try {
			final List<WebElement> all = driver.findElements(locator);
			final List<WebElement> visible = new ArrayList<>();
			for (final WebElement element : all) {
				if (element.isDisplayed()) {
					visible.add(element);
				}
			}
			return visible;
		} catch (final NoSuchElementException e) {
			return List.of();
		}
	}

	private boolean waitForNewWindow(final Duration timeout) {
		final int currentSize = driver.getWindowHandles().size();
		try {
			new WebDriverWait(driver, timeout).until((ExpectedCondition<Boolean>) wd -> wd != null
					&& wd.getWindowHandles().size() > currentSize);
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private void switchToNewestWindow(final String previousHandle) {
		final Set<String> handles = driver.getWindowHandles();
		for (final String handle : handles) {
			if (!handle.equals(previousHandle)) {
				driver.switchTo().window(handle);
				return;
			}
		}
	}

	private void waitForUiToLoad() {
		try {
			wait.until(driver -> {
				if (!(driver instanceof JavascriptExecutor)) {
					return true;
				}
				final Object state = ((JavascriptExecutor) driver).executeScript("return document.readyState");
				return "complete".equals(state) || "interactive".equals(state);
			});
		} catch (final Exception ignored) {
			// Some pages (auth / cross-domain transitions) may not expose readyState reliably.
		}
	}

	private void executeStep(final String stepName, final StepAction action) throws Exception {
		try {
			action.run();
			report.put(stepName, "PASS");
		} catch (final Throwable t) {
			report.put(stepName, "FAIL");
			throw t;
		}
	}

	private void initializeReport() {
		report.put(STEP_LOGIN, "NOT_RUN");
		report.put(STEP_MENU, "NOT_RUN");
		report.put(STEP_MODAL, "NOT_RUN");
		report.put(STEP_ADMIN_VIEW, "NOT_RUN");
		report.put(STEP_INFO_GENERAL, "NOT_RUN");
		report.put(STEP_ACCOUNT_DETAILS, "NOT_RUN");
		report.put(STEP_BUSINESSES, "NOT_RUN");
		report.put(STEP_TERMS, "NOT_RUN");
		report.put(STEP_PRIVACY, "NOT_RUN");
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final String name = checkpointName + ".png";
		final Path target = evidenceDir.resolve(name);
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(target, screenshot);
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			evidenceDir = Paths.get("target", "saleads-evidence", DIR_FORMAT.format(LocalDateTime.now()));
			Files.createDirectories(evidenceDir);
		}

		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		sb.append("generated_at=").append(LocalDateTime.now()).append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("RESULTS").append(System.lineSeparator());
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			sb.append(System.lineSeparator());
			sb.append("LEGAL_URLS").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		final Path reportFile = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportFile, sb.toString(), StandardCharsets.UTF_8);
	}

	private WebDriver createDriver() {
		final String browser = env("SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "false"));

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefox = new FirefoxOptions();
			if (headless) {
				firefox.addArguments("-headless");
			}
			return new FirefoxDriver(firefox);
		case "edge":
			final EdgeOptions edge = new EdgeOptions();
			if (headless) {
				edge.addArguments("--headless=new");
			}
			edge.addArguments("--window-size=1920,1080");
			return new EdgeDriver(edge);
		case "chrome":
		default:
			final ChromeOptions chrome = new ChromeOptions();
			if (headless) {
				chrome.addArguments("--headless=new");
			}
			chrome.addArguments("--window-size=1920,1080");
			return new ChromeDriver(chrome);
		}
	}

	private String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return isBlank(value) ? defaultValue : value;
	}

	private int intEnv(final String key, final int defaultValue) {
		final String value = System.getenv(key);
		if (isBlank(value)) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (final NumberFormatException e) {
			return defaultValue;
		}
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
