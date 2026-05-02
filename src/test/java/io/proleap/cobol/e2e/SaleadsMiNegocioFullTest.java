package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
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

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);

	private final Map<String, String> finalReport = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor js;
	private Path screenshotsDir;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue(
				"Enable with -Dsaleads.e2e.enabled=true. Optional -Dsaleads.initial.url=<url> for the active environment.",
				enabled);

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--start-maximized");
		options.addArguments("--window-size=1920,1080");

		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "false"));
		if (headless) {
			options.addArguments("--headless=new");
			options.addArguments("--disable-gpu");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		js = (JavascriptExecutor) driver;
		screenshotsDir = Files.createDirectories(Path.of("target", "saleads-e2e",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));

		final String initialUrl = System.getProperty("saleads.initial.url", "").trim();
		if (!initialUrl.isEmpty()) {
			driver.get(initialUrl);
		}

		waitForUiLoad();
	}

	@Test
	public void saleads_mi_negocio_full_test() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones",
				() -> stepValidateLegalDocument("Términos y Condiciones", "Términos y Condiciones", "terminos"));
		runStep("Política de Privacidad",
				() -> stepValidateLegalDocument("Política de Privacidad", "Política de Privacidad", "politica-privacidad"));

		printFinalReport();
		assertFalse("One or more workflow validations failed:\n" + String.join("\n", failures), !failures.isEmpty());
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	private void stepLoginWithGoogle() {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Google");

		selectGoogleAccountIfVisible(System.getProperty("saleads.google.account", "juanlucasbarbiergarzon@gmail.com"));

		waitForAnyVisible(By.xpath("//aside"), By.xpath("//nav"), byVisibleText("Negocio"));
		assertTrue("Main app interface was not detected.", isAnyVisible(By.xpath("//aside"), By.xpath("//nav")));
		assertVisibleText("Negocio");

		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");

		takeScreenshot("02-mi-negocio-expanded-menu");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal");
		optionalFillBusinessName();
		clickByVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() {
		if (!isVisible(byVisibleText("Administrar Negocios"), SHORT_TIMEOUT)) {
			if (isVisible(byVisibleText("Mi Negocio"), SHORT_TIMEOUT)) {
				clickByVisibleText("Mi Negocio");
			} else {
				clickByVisibleText("Negocio", "Mi Negocio");
			}
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");

		takeFullPageScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String expectedEmail = System.getProperty("saleads.google.account", "juanlucasbarbiergarzon@gmail.com");
		assertVisibleText(expectedEmail);
		assertTrue("User name was not detected in Información General section.",
				isAnyVisible(byVisibleText("Nombre"), By.xpath("//*[contains(@class,'name') and string-length(normalize-space(.)) > 0]")));
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
	}

	private void stepValidateLegalDocument(final String linkText, final String headingText, final String screenshotSlug) {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String previousUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);

		final boolean openedNewTab = waitForNewTabOrNavigation(handlesBeforeClick, previousUrl);
		if (openedNewTab) {
			switchToNewestWindow(handlesBeforeClick);
		}

		waitForUiLoad();
		assertVisibleText(headingText);
		assertTrue("Legal content body is not visible for " + headingText, isLegalContentVisible());

		takeScreenshot("05-" + screenshotSlug);
		System.out.println("Final URL [" + linkText + "]: " + driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiLoad();
		} else if (!driver.getCurrentUrl().equals(previousUrl)) {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String reportField, final Runnable stepAction) {
		try {
			stepAction.run();
			finalReport.put(reportField, "PASS");
		} catch (final Throwable failure) {
			finalReport.put(reportField, "FAIL");
			failures.add(reportField + ": " + failure.getMessage());
			takeScreenshot("failed-" + slug(reportField));
		}
	}

	private void clickByVisibleText(final String... candidates) {
		for (final String candidate : candidates) {
			final By locator = byVisibleText(candidate);
			if (isVisible(locator, SHORT_TIMEOUT)) {
				final WebElement target = wait.until(ExpectedConditions.elementToBeClickable(locator));
				scrollIntoView(target);
				target.click();
				waitForUiLoad();
				return;
			}
		}

		throw new NoSuchElementException("No clickable element found by visible text: " + String.join(", ", candidates));
	}

	private void optionalFillBusinessName() {
		final By inputLocator = By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or @aria-label='Nombre del Negocio' or @name='Nombre del Negocio' or @name='businessName']");
		if (isVisible(inputLocator, SHORT_TIMEOUT)) {
			final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(inputLocator));
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
			waitForUiLoad();
		}
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		final Set<String> originalHandles = new LinkedHashSet<>(driver.getWindowHandles());
		waitForUiLoad();

		try {
			wait.withTimeout(Duration.ofSeconds(15))
					.until(driverInstance -> driverInstance.getWindowHandles().size() >= originalHandles.size());
		} catch (final TimeoutException ignored) {
			// Continue in same tab if no account selector window appears.
		} finally {
			wait.withTimeout(DEFAULT_TIMEOUT);
		}

		switchToNewestWindow(originalHandles);

		final By accountLocator = byVisibleText(accountEmail);
		if (isVisible(accountLocator, SHORT_TIMEOUT)) {
			clickByVisibleText(accountEmail);
			waitForUiLoad();
		}

		if (!driver.getWindowHandles().containsAll(originalHandles)) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (isAnyVisible(By.xpath("//aside"), By.xpath("//nav"), byVisibleText("Negocio"))) {
					return;
				}
			}
		}
	}

	private boolean isLegalContentVisible() {
		final List<By> contentLocators = List.of(By.xpath("//main//*[self::p or self::li][string-length(normalize-space(.)) > 40]"),
				By.xpath("//article//*[self::p or self::li][string-length(normalize-space(.)) > 40]"),
				By.xpath("//*[self::p or self::li][string-length(normalize-space(.)) > 40]"));
		for (final By locator : contentLocators) {
			if (isVisible(locator, SHORT_TIMEOUT)) {
				return true;
			}
		}
		return false;
	}

	private void waitForUiLoad() {
		try {
			wait.until(driverInstance -> "complete".equals(js.executeScript("return document.readyState")));
		} catch (final TimeoutException ignored) {
			// Some SPAs do not report complete during transitions.
		}
		wait.until(driverInstance -> Boolean.TRUE.equals(js.executeScript("return !!document.body")));
	}

	private void waitForAnyVisible(final By... locators) {
		wait.until(driverInstance -> isAnyVisible(locators));
	}

	private boolean isAnyVisible(final By... locators) {
		for (final By locator : locators) {
			if (isVisible(locator, SHORT_TIMEOUT)) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void assertVisibleText(final String text) {
		assertTrue("Text was not visible: " + text, isVisible(byVisibleText(text), DEFAULT_TIMEOUT));
	}

	private By byVisibleText(final String text) {
		return By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]");
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final String[] parts = text.split("'");
		final StringBuilder result = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				result.append(", \"'\", ");
			}
			result.append("'").append(parts[i]).append("'");
		}
		result.append(")");
		return result.toString();
	}

	private void switchToNewestWindow(final Set<String> previousHandles) {
		for (final String currentHandle : driver.getWindowHandles()) {
			if (!previousHandles.contains(currentHandle)) {
				driver.switchTo().window(currentHandle);
				return;
			}
		}
	}

	private boolean waitForNewTabOrNavigation(final Set<String> handlesBeforeClick, final String urlBeforeClick) {
		try {
			return wait.until(driverInstance -> driverInstance.getWindowHandles().size() > handlesBeforeClick.size()
					|| !driverInstance.getCurrentUrl().equals(urlBeforeClick));
		} catch (final TimeoutException timeout) {
			throw new TimeoutException("Expected new tab or page navigation did not happen.", timeout);
		}
	}

	private void scrollIntoView(final WebElement element) {
		js.executeScript("arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});", element);
	}

	private void takeScreenshot(final String name) {
		if (driver == null) {
			return;
		}

		try {
			final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path destination = screenshotsDir.resolve(slug(name) + ".png");
			Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
			System.out.println("Screenshot: " + destination.toAbsolutePath());
		} catch (final Exception screenshotFailure) {
			failures.add("Screenshot capture failed (" + name + "): " + screenshotFailure.getMessage());
		}
	}

	private void takeFullPageScreenshot(final String name) {
		final Dimension originalWindowSize = driver.manage().window().getSize();
		try {
			final Number pageHeight = (Number) js.executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight,"
							+ "document.body.offsetHeight, document.documentElement.offsetHeight);");
			final int targetHeight = Math.min(pageHeight.intValue(), 3800);
			driver.manage().window().setSize(new Dimension(originalWindowSize.getWidth(), Math.max(targetHeight, 1080)));
			waitForUiLoad();
			takeScreenshot(name);
		} finally {
			driver.manage().window().setSize(originalWindowSize);
		}
	}

	private String slug(final String text) {
		return text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private void printFinalReport() {
		System.out.println("===== saleads_mi_negocio_full_test report =====");
		for (final String reportField : List.of("Login", "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
				"Información General", "Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones",
				"Política de Privacidad")) {
			final String status = finalReport.getOrDefault(reportField, "FAIL");
			System.out.println(reportField + ": " + status);
		}
		System.out.println("Artifacts directory: " + screenshotsDir.toAbsolutePath());
		System.out.println("===============================================");
	}
}
