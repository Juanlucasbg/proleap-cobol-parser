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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * End-to-end UI workflow test for SaleADS "Mi Negocio" module.
 *
 * <p>
 * Configuration:
 * </p>
 * <ul>
 * <li>URL: use one of system property saleads.url, env SALEADS_URL, env BASE_URL</li>
 * <li>Headless: use system property saleads.headless=true (default false)</li>
 * <li>Google account preference: env SALEADS_GOOGLE_ACCOUNT
 * (default juanlucasbarbiergarzon@gmail.com)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
	private static final Duration QUICK_TIMEOUT = Duration.ofSeconds(8);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> finalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;

	@Before
	public void setUp() throws IOException {
		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-notifications");
		options.addArguments("--lang=es-ES");

		if (Boolean.parseBoolean(System.getProperty("saleads.headless", "false"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		evidenceDirectory = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDirectory);

		initializeReport();
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
		final String applicationUrl = firstNonBlank(System.getProperty("saleads.url"), System.getenv("SALEADS_URL"),
				System.getenv("BASE_URL"));

		if (applicationUrl == null) {
			throw new IllegalStateException(
					"No URL configured. Set -Dsaleads.url, SALEADS_URL, or BASE_URL to the current SaleADS environment login URL.");
		}

		driver.get(applicationUrl);
		waitForUiLoad();

		runStep(REPORT_LOGIN, this::executeLoginStep);
		runStep(REPORT_MI_NEGOCIO_MENU, this::executeOpenMiNegocioMenuStep);
		runStep(REPORT_AGREGAR_MODAL, this::executeAgregarNegocioModalStep);
		runStep(REPORT_ADMIN_VIEW, this::executeAdministrarNegociosStep);
		runStep(REPORT_INFO_GENERAL, this::executeInformacionGeneralValidation);
		runStep(REPORT_DETALLES, this::executeDetallesCuentaValidation);
		runStep(REPORT_TUS_NEGOCIOS, this::executeTusNegociosValidation);
		runStep(REPORT_TERMINOS, () -> executeLegalLinkValidation("Términos y Condiciones", "Términos y Condiciones",
				"08-terminos-condiciones", REPORT_TERMINOS));
		runStep(REPORT_PRIVACIDAD, () -> executeLegalLinkValidation("Política de Privacidad", "Política de Privacidad",
				"09-politica-privacidad", REPORT_PRIVACIDAD));

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> step : report.entrySet()) {
			if (!step.getValue()) {
				failedSteps.add(step.getKey());
			}
		}

		assertTrue("Some workflow validations failed: " + failedSteps + ". Details: " + failures, failedSteps.isEmpty());
	}

	private void executeLoginStep() throws IOException {
		final String preferredGoogleAccount = firstNonBlank(System.getenv("SALEADS_GOOGLE_ACCOUNT"),
				"juanlucasbarbiergarzon@gmail.com");

		clickByAnyVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
				"Continuar con Google", "Google"));
		waitForUiLoad();

		handleGooglePopupIfPresent(preferredGoogleAccount);
		waitForUiLoad();
		waitForMainApplicationVisible();
		takeScreenshot("01-dashboard-loaded");
	}

	private void executeOpenMiNegocioMenuStep() throws IOException {
		waitForVisibleText("Negocio");
		clickByVisibleText("Negocio");
		waitForUiLoad();
		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void executeAgregarNegocioModalStep() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForUiLoad();

		waitForVisibleText("Crear Nuevo Negocio");
		waitForAnyElement(Arrays.asList(By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]"),
				By.xpath("//*[contains(normalize-space(.),'Nombre del Negocio')]")));
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal");

		final WebElement input = firstVisibleElement(Arrays.asList(By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"), By.xpath("//input")));
		input.click();
		input.sendKeys(Keys.chord(Keys.CONTROL, "a"), "Negocio Prueba Automatización");
		waitForUiLoad();
		clickByVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void executeAdministrarNegociosStep() throws IOException {
		expandMiNegocioMenuIfCollapsed();
		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		waitForVisibleText("Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal");
		takeFullPageScreenshot("04-administrar-negocios-full");
	}

	private void executeInformacionGeneralValidation() {
		waitForVisibleText("Información General");
		waitForVisibleText("BUSINESS PLAN");
		waitForVisibleText("Cambiar Plan");

		final String pageText = driver.findElement(By.tagName("body")).getText();
		if (!EMAIL_PATTERN.matcher(pageText).find()) {
			throw new AssertionError("Expected user email was not visible in Información General section.");
		}

		final boolean hasLikelyUserName = pageText.lines().map(String::trim)
				.anyMatch(line -> line.length() >= 4 && line.length() <= 80 && !line.contains("@")
						&& !line.equalsIgnoreCase("Información General") && !line.equalsIgnoreCase("BUSINESS PLAN")
						&& !line.equalsIgnoreCase("Cambiar Plan"));
		if (!hasLikelyUserName) {
			throw new AssertionError("Expected user name was not visible in Información General section.");
		}
	}

	private void executeDetallesCuentaValidation() {
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void executeTusNegociosValidation() {
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
	}

	private void executeLegalLinkValidation(final String linkText, final String expectedHeading, final String screenshotName,
			final String reportField) throws IOException {
		waitForVisibleText("Sección Legal");
		final String originalWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForUiLoad();

		final String targetHandle = waitForNewHandleOrSameTab(handlesBeforeClick);
		final boolean openedNewTab = targetHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(targetHandle);
			waitForUiLoad();
		}

		waitForVisibleText(expectedHeading);

		final String legalText = driver.findElement(By.tagName("body")).getText();
		if (legalText.trim().length() < 120) {
			throw new AssertionError("Expected legal content text to be visible on: " + linkText);
		}

		takeScreenshot(screenshotName);
		final String url = driver.getCurrentUrl();
		finalUrls.put(reportField, url);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
			waitForVisibleText("Sección Legal");
		}
	}

	private void handleGooglePopupIfPresent(final String preferredGoogleAccount) {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();

		try {
			final String popupHandle = wait.withTimeout(QUICK_TIMEOUT)
					.until(driverRef -> getNewHandle(beforeHandles, driverRef.getWindowHandles()));

			if (popupHandle != null) {
				driver.switchTo().window(popupHandle);
				waitForUiLoad();

				clickByAnyVisibleText(Arrays.asList(preferredGoogleAccount, "Elegir una cuenta", "Use another account"));
				waitForUiLoad();

				if (driver.getWindowHandles().contains(originalWindow)) {
					driver.switchTo().window(originalWindow);
				}
			}
		} catch (final TimeoutException ignored) {
			// Login may remain in same tab or user may already be authenticated.
		}
	}

	private void waitForMainApplicationVisible() {
		waitForAnyElement(Arrays.asList(By.xpath("//*[contains(normalize-space(.),'Mi Negocio')]"),
				By.xpath("//*[contains(normalize-space(.),'Negocio')]"), By.xpath("//aside"),
				By.xpath("//*[contains(@class,'sidebar')]")));
	}

	private void expandMiNegocioMenuIfCollapsed() {
		if (!isVisibleText("Administrar Negocios")) {
			if (isVisibleText("Negocio")) {
				clickByVisibleText("Negocio");
				waitForUiLoad();
			}
			if (isVisibleText("Mi Negocio")) {
				clickByVisibleText("Mi Negocio");
				waitForUiLoad();
			}
		}
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			report.put(reportField, true);
		} catch (final Exception ex) {
			report.put(reportField, false);
			failures.add(reportField + ": " + ex.getMessage());
		}
	}

	private void initializeReport() {
		for (final String key : Arrays.asList(REPORT_LOGIN, REPORT_MI_NEGOCIO_MENU, REPORT_AGREGAR_MODAL, REPORT_ADMIN_VIEW,
				REPORT_INFO_GENERAL, REPORT_DETALLES, REPORT_TUS_NEGOCIOS, REPORT_TERMINOS, REPORT_PRIVACIDAD)) {
			report.put(key, false);
		}
	}

	private void clickByVisibleText(final String text) {
		final List<WebElement> candidates = driver.findElements(By.xpath(
				"//*[normalize-space(.)='" + text + "' or contains(normalize-space(.),'" + text + "')]"));
		for (final WebElement candidate : candidates) {
			if (isInteractable(candidate)) {
				candidate.click();
				return;
			}

			try {
				final WebElement clickableAncestor = candidate
						.findElement(By.xpath("ancestor-or-self::*[self::button or self::a or @role='button'][1]"));
				if (isInteractable(clickableAncestor)) {
					clickableAncestor.click();
					return;
				}
			} catch (final NoSuchElementException ignored) {
				// continue
			}
		}

		throw new NoSuchElementException("Could not click any visible element with text: " + text);
	}

	private void clickByAnyVisibleText(final List<String> texts) {
		for (final String text : texts) {
			if (isVisibleText(text)) {
				clickByVisibleText(text);
				return;
			}
		}
		throw new NoSuchElementException("Could not find any clickable text from: " + texts);
	}

	private void waitForVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[normalize-space(.)='" + text + "' or contains(normalize-space(.),'" + text + "')]")));
	}

	private boolean isVisibleText(final String text) {
		try {
			final List<WebElement> elements = driver.findElements(
					By.xpath("//*[normalize-space(.)='" + text + "' or contains(normalize-space(.),'" + text + "')]"));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void waitForAnyElement(final List<By> selectors) {
		wait.until(driverRef -> {
			for (final By selector : selectors) {
				final List<WebElement> elements = driverRef.findElements(selector);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			}
			return false;
		});
	}

	private WebElement firstVisibleElement(final List<By> selectors) {
		for (final By selector : selectors) {
			final List<WebElement> elements = driver.findElements(selector);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}

		throw new NoSuchElementException("No visible element found for selectors: " + selectors);
	}

	private boolean isInteractable(final WebElement element) {
		try {
			return element != null && element.isDisplayed() && element.isEnabled();
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void waitForUiLoad() {
		wait.until(driverRef -> {
			final Object state = ((JavascriptExecutor) driverRef).executeScript("return document.readyState");
			return Objects.equals("complete", state) || Objects.equals("interactive", state);
		});
	}

	private String waitForNewHandleOrSameTab(final Set<String> oldHandles) {
		try {
			return new WebDriverWait(driver, QUICK_TIMEOUT)
					.until(driverRef -> getNewHandle(oldHandles, driverRef.getWindowHandles()));
		} catch (final TimeoutException ignored) {
			return null;
		}
	}

	private String getNewHandle(final Set<String> oldHandles, final Set<String> currentHandles) {
		for (final String handle : currentHandles) {
			if (!oldHandles.contains(handle)) {
				return handle;
			}
		}
		return null;
	}

	private void takeScreenshot(final String name) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDirectory.resolve(name + ".png");
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private void takeFullPageScreenshot(final String name) throws IOException {
		final org.openqa.selenium.Dimension originalSize = driver.manage().window().getSize();
		try {
			final Long docHeight = (Long) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			final int targetHeight = Math.max(1080, Math.min(docHeight == null ? 1080 : docHeight.intValue(), 8000));
			driver.manage().window().setSize(new Dimension(originalSize.getWidth(), targetHeight));
			waitForUiLoad();
			takeScreenshot(name);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiLoad();
		}
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private void printFinalReport() {
		System.out.println("===== SaleADS Mi Negocio Final Report =====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}

		if (!finalUrls.isEmpty()) {
			System.out.println("---- Captured URLs ----");
			for (final Map.Entry<String, String> entry : finalUrls.entrySet()) {
				System.out.println(entry.getKey() + ": " + entry.getValue());
			}
		}

		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
		if (!failures.isEmpty()) {
			System.out.println("---- Failure Details ----");
			for (final String failure : failures) {
				System.out.println(failure);
			}
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
