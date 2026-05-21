package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioWorkflowE2ETest {

	private static final Logger LOG = LoggerFactory.getLogger(SaleadsMiNegocioWorkflowE2ETest.class);

	private static final String ENV_LOGIN_URL = "SALEADS_LOGIN_URL";
	private static final String ENV_WAIT_SECONDS = "SALEADS_WAIT_SECONDS";
	private static final String ENV_HEADLESS = "SALEADS_HEADLESS";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> stepStatus = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDir;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final String loginUrl = getConfigValue(ENV_LOGIN_URL, null);
		Assume.assumeTrue("Set " + ENV_LOGIN_URL + " to the login page URL for the target SaleADS environment.",
				loginUrl != null && !loginUrl.trim().isEmpty());

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (Boolean.parseBoolean(getConfigValue(ENV_HEADLESS, "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(Long.parseLong(getConfigValue(ENV_WAIT_SECONDS, "30"))));

		screenshotsDir = Paths.get("target", "saleads-screenshots",
				DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()));
		Files.createDirectories(screenshotsDir);

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
		executeStep("Login", this::loginWithGoogleAndValidate);
		executeStep("Mi Negocio menu", this::openMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::openAdministrarNegocios);
		executeStep("Informacion General", this::validateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		executeStep("Tus Negocios", this::validateTusNegocios);
		executeStep("Terminos y Condiciones", () -> validateLegalPage("T\u00e9rminos y Condiciones", "08-terminos"));
		executeStep("Politica de Privacidad", () -> validateLegalPage("Pol\u00edtica de Privacidad", "09-politica"));

		logFinalReport();
		Assert.assertTrue("Workflow validation failures:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private void loginWithGoogleAndValidate() throws IOException {
		clickFirstVisibleByText(Arrays.asList("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Google"));
		selectGoogleAccountIfPresented();
		waitForUiToLoad();

		final WebElement sidebar = waitForVisibleAny(
				By.xpath("//aside"),
				By.xpath("//nav"),
				byTextContains("Negocio"),
				byTextContains("Mi Negocio"));

		Assert.assertNotNull("Main application interface did not appear after login.", sidebar);
		Assert.assertTrue("Left sidebar navigation is not visible.", sidebar.isDisplayed());
		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws IOException {
		expandNegocioIfNeeded();
		clickVisibleElement(byExactText("Mi Negocio"));
		waitForUiToLoad();

		Assert.assertTrue("Expected Agregar Negocio submenu option to be visible.", isTextVisible("Agregar Negocio"));
		Assert.assertTrue("Expected Administrar Negocios submenu option to be visible.",
				isTextVisible("Administrar Negocios"));
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickVisibleElement(byExactText("Agregar Negocio"));
		waitForVisible(byExactText("Crear Nuevo Negocio"));

		Assert.assertTrue("Modal title Crear Nuevo Negocio is not visible.", isTextVisible("Crear Nuevo Negocio"));
		Assert.assertTrue("Input field label Nombre del Negocio is not present.",
				isAnyElementVisible(By.xpath(
						"//label[contains(normalize-space(.),'Nombre del Negocio')] | //input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio')]")));
		Assert.assertTrue("Text Tienes 2 de 3 negocios is not visible.", isTextVisible("Tienes 2 de 3 negocios"));
		Assert.assertTrue("Cancelar button is not visible.", isTextVisible("Cancelar"));
		Assert.assertTrue("Crear Negocio button is not visible.", isTextVisible("Crear Negocio"));

		takeScreenshot("03-agregar-negocio-modal");
		fillBusinessNameAndCancel();
	}

	private void openAdministrarNegocios() throws IOException {
		expandMiNegocioIfCollapsed();
		clickVisibleElement(byExactText("Administrar Negocios"));
		waitForUiToLoad();

		assertVisibleText("Informaci\u00f3n General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Secci\u00f3n Legal");
		takeScreenshot("04-administrar-negocios-page");
	}

	private void validateInformacionGeneral() {
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
		assertAnyVisible(
				By.xpath("//section[contains(.,'Informaci\u00f3n General')]//*[contains(text(),'@')]"),
				By.xpath("//*[contains(text(),'@')]"));
		assertAnyVisible(
				By.xpath("//section[contains(.,'Informaci\u00f3n General')]//h1"),
				By.xpath("//section[contains(.,'Informaci\u00f3n General')]//h2"),
				By.xpath("//section[contains(.,'Informaci\u00f3n General')]//p[normalize-space(.)!='']"));
	}

	private void validateDetallesDeLaCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertAnyVisible(
				By.xpath("//section[contains(.,'Tus Negocios')]//li"),
				By.xpath("//section[contains(.,'Tus Negocios')]//table"),
				By.xpath("//section[contains(.,'Tus Negocios')]//div[contains(@class,'business')]"));
	}

	private void validateLegalPage(final String linkText, final String screenshotPrefix) throws IOException {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickVisibleElement(byExactText(linkText));
		waitForUiToLoad();

		final String newHandle = waitForNewWindowHandle(beforeHandles, Duration.ofSeconds(10));
		final boolean openedNewTab = newHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(newHandle);
			waitForUiToLoad();
		}

		assertVisibleText(linkText);
		assertAnyVisible(By.xpath("//article//*[normalize-space(.)!='']"), By.xpath("//main//*[normalize-space(.)!='']"),
				By.xpath("//body//*[normalize-space(.)!='']"));

		takeScreenshot(screenshotPrefix + "-legal-page");
		final String currentUrl = driver.getCurrentUrl();
		LOG.info("{} URL: {}", linkText, currentUrl);

		if ("T\u00e9rminos y Condiciones".equals(linkText)) {
			termsUrl = currentUrl;
		}
		if ("Pol\u00edtica de Privacidad".equals(linkText)) {
			privacyUrl = currentUrl;
		}

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		assertVisibleText("Secci\u00f3n Legal");
	}

	private void executeStep(final String reportKey, final StepAction action) {
		try {
			action.run();
			stepStatus.put(reportKey, "PASS");
		} catch (final Throwable throwable) {
			stepStatus.put(reportKey, "FAIL");
			final String failureMessage = reportKey + " -> " + throwable.getMessage();
			failures.add(failureMessage);
			LOG.error("Step failed: {}", failureMessage, throwable);
			try {
				takeScreenshot(reportKey.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-failure");
			} catch (final IOException screenshotError) {
				failures.add(reportKey + " -> could not capture failure screenshot: " + screenshotError.getMessage());
			}
		}
	}

	private void fillBusinessNameAndCancel() {
		final List<By> nameInputSelectors = Arrays.asList(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));

		for (final By selector : nameInputSelectors) {
			if (isAnyElementVisible(selector)) {
				final WebElement input = waitForVisible(selector);
				input.click();
				input.sendKeys(Keys.chord(Keys.CONTROL, "a"));
				input.sendKeys("Negocio Prueba Automatizacion");
				break;
			}
		}

		clickVisibleElement(byExactText("Cancelar"));
		waitForUiToLoad();
	}

	private void expandNegocioIfNeeded() {
		if (!isTextVisible("Mi Negocio") && isTextVisible("Negocio")) {
			clickVisibleElement(byExactText("Negocio"));
		}
	}

	private void expandMiNegocioIfCollapsed() {
		if (!isTextVisible("Administrar Negocios") && isTextVisible("Mi Negocio")) {
			clickVisibleElement(byExactText("Mi Negocio"));
			waitForUiToLoad();
		}
	}

	private void selectGoogleAccountIfPresented() {
		final By accountEntry = By.xpath("//*[contains(normalize-space(.),'" + GOOGLE_ACCOUNT_EMAIL + "')]");
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
			final WebElement account = shortWait.until(d -> findDisplayedElement(accountEntry));
			if (account != null) {
				safeClick(account);
				waitForUiToLoad();
			}
		} catch (final TimeoutException timeoutException) {
			LOG.info("Google account chooser not displayed, continuing login flow.");
		}
	}

	private String waitForNewWindowHandle(final Set<String> previousHandles, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(d -> {
				for (final String handle : d.getWindowHandles()) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private void assertVisibleText(final String text) {
		Assert.assertTrue("Expected visible text: " + text, isTextVisible(text));
	}

	private void assertAnyVisible(final By... selectors) {
		final WebElement element = waitForVisibleAny(selectors);
		Assert.assertNotNull("None of the expected elements became visible.", element);
	}

	private boolean isTextVisible(final String text) {
		return isAnyElementVisible(byExactText(text), byTextContains(text));
	}

	private boolean isAnyElementVisible(final By... selectors) {
		for (final By selector : selectors) {
			if (findDisplayedElement(selector) != null) {
				return true;
			}
		}
		return false;
	}

	private WebElement waitForVisibleAny(final By... selectors) {
		return wait.until(d -> {
			for (final By selector : selectors) {
				final WebElement element = findDisplayedElement(selector);
				if (element != null) {
					return element;
				}
			}
			return null;
		});
	}

	private WebElement waitForVisible(final By selector) {
		return wait.until(d -> findDisplayedElement(selector));
	}

	private WebElement findDisplayedElement(final By selector) {
		for (final WebElement element : driver.findElements(selector)) {
			try {
				if (element.isDisplayed()) {
					return element;
				}
			} catch (final Exception ignored) {
				// Ignore stale/intermediate DOM state and continue scanning.
			}
		}
		return null;
	}

	private void clickFirstVisibleByText(final List<String> textCandidates) {
		for (final String text : textCandidates) {
			final By exact = byExactText(text);
			if (isAnyElementVisible(exact)) {
				clickVisibleElement(exact);
				return;
			}
			final By contains = byTextContains(text);
			if (isAnyElementVisible(contains)) {
				clickVisibleElement(contains);
				return;
			}
		}
		throw new AssertionError("Could not find a visible clickable element for any of: " + textCandidates);
	}

	private void clickVisibleElement(final By selector) {
		final WebElement element = waitForVisible(selector);
		safeClick(element);
		waitForUiToLoad();
	}

	private void safeClick(final WebElement element) {
		try {
			element.click();
		} catch (final Exception clickException) {
			try {
				new Actions(driver).moveToElement(element).click().perform();
			} catch (final Exception actionClickException) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			}
		}
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
	}

	private void takeScreenshot(final String fileNamePrefix) throws IOException {
		final Path screenshotPath = screenshotsDir.resolve(fileNamePrefix + ".png");
		final Path sourceFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(sourceFile, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
		LOG.info("Screenshot saved: {}", screenshotPath.toAbsolutePath());
	}

	private By byExactText(final String text) {
		final String escapedText = escapeForXPath(text);
		return By.xpath("//*[normalize-space(.)=" + escapedText + "]");
	}

	private By byTextContains(final String text) {
		final String escapedText = escapeForXPath(text);
		return By.xpath("//*[contains(normalize-space(.)," + escapedText + ")]");
	}

	private String escapeForXPath(final String value) {
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

	private String getConfigValue(final String key, final String defaultValue) {
		return firstNonBlank(() -> System.getenv(key), () -> System.getProperty(key), () -> defaultValue);
	}

	@SafeVarargs
	private final String firstNonBlank(final Supplier<String>... suppliers) {
		for (final Supplier<String> supplier : suppliers) {
			final String value = supplier.get();
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private void logFinalReport() {
		LOG.info("=== Final Validation Report ===");
		LOG.info("Login: {}", stepStatus.getOrDefault("Login", "NOT RUN"));
		LOG.info("Mi Negocio menu: {}", stepStatus.getOrDefault("Mi Negocio menu", "NOT RUN"));
		LOG.info("Agregar Negocio modal: {}", stepStatus.getOrDefault("Agregar Negocio modal", "NOT RUN"));
		LOG.info("Administrar Negocios view: {}", stepStatus.getOrDefault("Administrar Negocios view", "NOT RUN"));
		LOG.info("Informacion General: {}", stepStatus.getOrDefault("Informacion General", "NOT RUN"));
		LOG.info("Detalles de la Cuenta: {}", stepStatus.getOrDefault("Detalles de la Cuenta", "NOT RUN"));
		LOG.info("Tus Negocios: {}", stepStatus.getOrDefault("Tus Negocios", "NOT RUN"));
		LOG.info("Terminos y Condiciones: {}", stepStatus.getOrDefault("Terminos y Condiciones", "NOT RUN"));
		LOG.info("Politica de Privacidad: {}", stepStatus.getOrDefault("Politica de Privacidad", "NOT RUN"));
		LOG.info("T\u00e9rminos y Condiciones URL: {}", termsUrl);
		LOG.info("Pol\u00edtica de Privacidad URL: {}", privacyUrl);
		LOG.info("Screenshots directory: {}", screenshotsDir.toAbsolutePath());
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
