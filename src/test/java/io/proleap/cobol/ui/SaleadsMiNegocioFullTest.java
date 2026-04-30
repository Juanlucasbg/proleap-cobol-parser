package io.proleap.cobol.ui;

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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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
import org.openqa.selenium.WindowType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Informaci\u00F3n General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "T\u00E9rminos y Condiciones";
	private static final String PRIVACIDAD = "Pol\u00EDtica de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(25);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> failures = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		evidenceDir = Paths.get("target", "saleads-evidence", LocalDateTime.now()
				.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = readBoolean("saleads.headless", "SALEADS_HEADLESS", true);
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		appWindowHandle = driver.getWindowHandle();

		final String loginUrl = readString("saleads.url", "SALEADS_URL", "SALEADS_LOGIN_URL");
		if (loginUrl == null || loginUrl.isBlank()) {
			throw new IllegalStateException("Missing SaleADS login URL. Set saleads.url or SALEADS_URL or SALEADS_LOGIN_URL.");
		}

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		try {
			printReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflowTest() {
		runStep(LOGIN, this::stepLoginWithGoogle);
		runStep(MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		runStep(INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		runStep(DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(TERMINOS, () -> stepValidateLegalLink(TERMINOS, "08-terminos-condiciones"));
		runStep(PRIVACIDAD, () -> stepValidateLegalLink(PRIVACIDAD, "09-politica-privacidad"));

		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue()) {
				failed.add(entry.getKey());
			}
		}

		assertTrue("Workflow completed with failures: " + failed + ". Details: " + failures, failed.isEmpty());
	}

	private void stepLoginWithGoogle() {
		final Set<String> beforeClickHandles = driver.getWindowHandles();
		clickByVisibleTextOrFail("Sign in with Google", "Iniciar sesi\u00F3n con Google", "Continuar con Google",
				"Login with Google");
		waitForUiToLoad();

		switchToNewWindowIfOpened(beforeClickHandles);
		selectGoogleAccountIfPrompted();
		switchBackToAppWindow();

		waitForAnyVisibleText("Negocio", "Mi Negocio");
		waitForSidebar();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		waitForSidebar();
		if (!isVisibleText("Agregar Negocio") || !isVisibleText("Administrar Negocios")) {
			clickByVisibleTextOrFail("Mi Negocio");
			waitForUiToLoad();
		}

		waitForAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleTextOrFail("Agregar Negocio");
		waitForUiToLoad();

		waitForAnyVisibleText("Crear Nuevo Negocio");
		waitForInputByLabelOrPlaceholder("Nombre del Negocio");
		waitForAnyVisibleText("Tienes 2 de 3 negocios");
		waitForAnyVisibleText("Cancelar");
		waitForAnyVisibleText("Crear Negocio");

		final WebElement businessNameInput = waitForInputByLabelOrPlaceholder("Nombre del Negocio");
		businessNameInput.click();
		businessNameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
		businessNameInput.sendKeys("Negocio Prueba Automatizaci\u00F3n");

		takeScreenshot("03-agregar-negocio-modal");

		clickByVisibleTextOrFail("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byVisibleText("Crear Nuevo Negocio")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() {
		if (!isVisibleText("Administrar Negocios")) {
			clickByVisibleTextOrFail("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleTextOrFail("Administrar Negocios");
		waitForUiToLoad();

		waitForAnyVisibleText("Informaci\u00F3n General");
		waitForAnyVisibleText("Detalles de la Cuenta");
		waitForAnyVisibleText("Tus Negocios");
		waitForAnyVisibleText("Secci\u00F3n Legal");

		takeScreenshot("04-administrar-negocios-full-page");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = waitForSection("Informaci\u00F3n General");
		final String sectionText = section.getText();

		final boolean hasEmail = EMAIL_PATTERN.matcher(sectionText).find();
		assertTrue("Expected user email in Informaci\u00F3n General section.", hasEmail);

		final boolean hasLikelyName = Arrays.stream(sectionText.split("\\R")).map(String::trim)
				.filter(line -> !line.isEmpty())
				.filter(line -> !line.equalsIgnoreCase("Informaci\u00F3n General"))
				.filter(line -> !line.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN"))
				.filter(line -> !line.equalsIgnoreCase("Cambiar Plan"))
				.anyMatch(line -> !EMAIL_PATTERN.matcher(line).find() && line.length() >= 3);
		assertTrue("Expected user name in Informaci\u00F3n General section.", hasLikelyName);

		assertTrue("Expected BUSINESS PLAN text.", containsText(sectionText, "BUSINESS PLAN"));
		waitForAnyVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		waitForSection("Detalles de la Cuenta");
		waitForAnyVisibleText("Cuenta creada");
		waitForAnyVisibleText("Estado activo");
		waitForAnyVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = waitForSection("Tus Negocios");
		waitForAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Tienes 2 de 3 negocios");

		final boolean hasBusinessListContent = section.findElements(By.xpath(".//li|.//tr|.//tbody/tr|.//div")).stream()
				.anyMatch(WebElement::isDisplayed);
		assertTrue("Expected visible business list content in Tus Negocios section.", hasBusinessListContent);
	}

	private void stepValidateLegalLink(final String linkText, final String screenshotName) {
		final String startHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleTextOrFail(linkText);
		waitForUiToLoad();

		final boolean openedNewTab = switchToNewWindowIfOpened(handlesBeforeClick);
		waitForAnyVisibleText(linkText);

		final String bodyText = safeBodyText();
		assertTrue("Expected legal content text for " + linkText + ".", bodyText != null && bodyText.length() > 120);

		takeScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(startHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		waitForAnyVisibleText("Secci\u00F3n Legal");
	}

	private void runStep(final String name, final Runnable stepLogic) {
		try {
			stepLogic.run();
			report.put(name, true);
		} catch (final Throwable error) {
			report.put(name, false);
			failures.put(name, error.getMessage());
			takeScreenshot("failed-" + normalizeForFileName(name));
			ensureApplicationWindow();
		}
	}

	private void switchBackToAppWindow() {
		if (!driver.getWindowHandle().equals(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
		waitForUiToLoad();
	}

	private boolean switchToNewWindowIfOpened(final Set<String> previousHandles) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12))
					.until(d -> d.getWindowHandles().size() > previousHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!previousHandles.contains(handle)) {
					driver.switchTo().window(handle);
					waitForUiToLoad();
					return true;
				}
			}
		} catch (final TimeoutException ignored) {
			// No new tab/window opened.
		}
		return false;
	}

	private void selectGoogleAccountIfPrompted() {
		final List<By> possibleAccountChoosers = Arrays.asList(byVisibleText(GOOGLE_ACCOUNT_EMAIL),
				byContainsVisibleText("Elegir una cuenta"), byContainsVisibleText("Choose an account"));

		for (final By locator : possibleAccountChoosers) {
			final Optional<WebElement> element = findVisible(locator);
			if (element.isPresent()) {
				if (locator.equals(byVisibleText(GOOGLE_ACCOUNT_EMAIL))) {
					safeClick(element.get());
					waitForUiToLoad();
				}
				return;
			}
		}
	}

	private void waitForSidebar() {
		wait.until(driver -> !driver
				.findElements(By.xpath("//aside[.//*[contains(normalize-space(.), 'Negocio')]]"
						+ "|//nav[.//*[contains(normalize-space(.), 'Negocio')]]"))
				.isEmpty() || isVisibleText("Negocio"));
	}

	private WebElement waitForSection(final String sectionTitle) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::h6][contains(normalize-space(.), '"
						+ sectionTitle
						+ "')]/ancestor::*[self::section or self::div][1]")));
	}

	private WebElement waitForInputByLabelOrPlaceholder(final String label) {
		final String xpath = "//label[contains(normalize-space(.), '" + label + "')]/following::input[1]"
				+ "|//input[@placeholder='" + label + "']"
				+ "|//input[contains(@aria-label, '" + label + "')]";
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
	}

	private void clickByVisibleTextOrFail(final String... texts) {
		for (final String text : texts) {
			final Optional<WebElement> element = findVisible(clickableByVisibleText(text));
			if (element.isPresent()) {
				safeClick(element.get());
				waitForUiToLoad();
				return;
			}
		}
		throw new NoSuchElementException("Could not find clickable element by visible text: " + Arrays.toString(texts));
	}

	private By clickableByVisibleText(final String text) {
		return By.xpath("//button[normalize-space(.)='" + text + "']"
				+ "|//a[normalize-space(.)='" + text + "']"
				+ "|//*[@role='button' and normalize-space(.)='" + text + "']"
				+ "|//*[@role='menuitem' and normalize-space(.)='" + text + "']"
				+ "|//*[normalize-space(.)='" + text + "']/ancestor::*[self::button or self::a or @role='button' or @role='menuitem'][1]");
	}

	private By byVisibleText(final String text) {
		return By.xpath("//*[normalize-space(.)='" + text + "']");
	}

	private By byContainsVisibleText(final String text) {
		return By.xpath("//*[contains(normalize-space(.), '" + text + "')]");
	}

	private Optional<WebElement> findVisible(final By locator) {
		for (final WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				return Optional.of(element);
			}
		}
		return Optional.empty();
	}

	private void waitForAnyVisibleText(final String... texts) {
		wait.until(d -> {
			for (final String text : texts) {
				if (isVisibleText(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isVisibleText(final String text) {
		try {
			for (final WebElement element : driver.findElements(byContainsVisibleText(text))) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final NoSuchElementException ignored) {
			return false;
		}
	}

	private void safeClick(final WebElement element) {
		scrollToElement(element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception ignored) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void scrollToElement(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
	}

	private void takeScreenshot(final String name) {
		if (driver == null) {
			return;
		}
		try {
			final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path target = evidenceDir.resolve(normalizeForFileName(name) + ".png");
			Files.copy(screenshot.toPath(), target);
		} catch (final Exception ignored) {
			// Do not fail the workflow due to screenshot write errors.
		}
	}

	private String safeBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception ignored) {
			return null;
		}
	}

	private void ensureApplicationWindow() {
		try {
			if (!driver.getWindowHandles().contains(appWindowHandle)) {
				appWindowHandle = driver.switchTo().newWindow(WindowType.TAB).getWindowHandle();
			}
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		} catch (final Exception ignored) {
			// best-effort stabilization for subsequent steps
		}
	}

	private boolean containsText(final String value, final String expected) {
		return value != null && value.toUpperCase(Locale.ROOT).contains(expected.toUpperCase(Locale.ROOT));
	}

	private String normalizeForFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String readString(final String propertyName, final String... envNames) {
		final String property = System.getProperty(propertyName);
		if (property != null && !property.isBlank()) {
			return property;
		}
		for (final String envName : envNames) {
			final String envValue = System.getenv(envName);
			if (envValue != null && !envValue.isBlank()) {
				return envValue;
			}
		}
		return null;
	}

	private boolean readBoolean(final String propertyName, final String envName, final boolean defaultValue) {
		final String property = System.getProperty(propertyName);
		if (property != null && !property.isBlank()) {
			return Boolean.parseBoolean(property);
		}
		final String env = System.getenv(envName);
		if (env != null && !env.isBlank()) {
			return Boolean.parseBoolean(env);
		}
		return defaultValue;
	}

	private void printReport() {
		System.out.println();
		System.out.println("========== SaleADS Mi Negocio Final Report ==========");
		printReportRow(LOGIN);
		printReportRow(MI_NEGOCIO_MENU);
		printReportRow(AGREGAR_NEGOCIO_MODAL);
		printReportRow(ADMINISTRAR_NEGOCIOS_VIEW);
		printReportRow(INFORMACION_GENERAL);
		printReportRow(DETALLES_CUENTA);
		printReportRow(TUS_NEGOCIOS);
		printReportRow(TERMINOS);
		printReportRow(PRIVACIDAD);
		if (!legalUrls.isEmpty()) {
			System.out.println("Legal URLs:");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(" - " + entry.getKey() + ": " + entry.getValue());
			}
		}
		if (!failures.isEmpty()) {
			System.out.println("Failure details:");
			for (final Map.Entry<String, String> entry : failures.entrySet()) {
				System.out.println(" - " + entry.getKey() + ": " + entry.getValue());
			}
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("=====================================================");
		System.out.println();
	}

	private void printReportRow(final String key) {
		final boolean status = report.getOrDefault(key, false);
		System.out.println(String.format("%-30s : %s", key, status ? "PASS" : "FAIL"));
	}
}
