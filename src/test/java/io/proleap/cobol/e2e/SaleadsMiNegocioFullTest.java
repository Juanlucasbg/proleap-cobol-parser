package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * E2E automation for SaleADS Mi Negocio module.
 *
 * Runtime configuration:
 * - SALEADS_LOGIN_URL (optional): login URL for current environment.
 * - SALEADS_HEADLESS (optional): true/false, defaults to true.
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_WAIT = Duration.ofSeconds(8);

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> legalUrls = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1200");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);

		evidenceDir = Path.of("target", "surefire-reports", "saleads-mi-negocio-evidence");
		Files.createDirectories(evidenceDir);

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.navigate().to(loginUrl);
			waitForPageReady();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		initializeReport();

		try {
			stepLoginWithGoogle();
			stepOpenMiNegocioMenu();
			stepValidateAgregarNegocioModal();
			stepOpenAdministrarNegocios();
			stepValidateInformacionGeneral();
			stepValidateDetallesCuenta();
			stepValidateTusNegocios();
			stepValidateTerminosYCondiciones();
			stepValidatePoliticaPrivacidad();
		} finally {
			writeFinalReport();
		}

		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!Boolean.TRUE.equals(entry.getValue())) {
				failed.add(entry.getKey());
			}
		}

		Assert.assertTrue("SaleADS Mi Negocio workflow failed checks: " + failed, failed.isEmpty());
	}

	private void initializeReport() {
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
	}

	private void stepLoginWithGoogle() throws IOException {
		final WebElement loginButton = findFirstVisible(
				By.xpath("//button[normalize-space()='Sign in with Google']"),
				By.xpath("//*[self::button or self::a][contains(normalize-space(), 'Google')]"),
				By.xpath("//button[contains(normalize-space(), 'Iniciar') or contains(normalize-space(), 'Ingresar')]"));

		clickAndWait(loginButton);
		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");

		final boolean hasSidebar = isVisible(By.xpath("//*[contains(@class,'sidebar') or @role='navigation']"), WAIT_TIMEOUT)
				|| isVisible(By.xpath("//*[contains(normalize-space(), 'Negocio')]"), WAIT_TIMEOUT);
		report.put("Login", hasSidebar);

		if (hasSidebar) {
			captureScreenshot("01_dashboard_loaded");
		}
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		final WebElement negocioSection = findFirstVisible(
				By.xpath("//*[self::button or self::a or self::div][normalize-space()='Negocio']"),
				By.xpath("//*[self::button or self::a or self::div][contains(normalize-space(), 'Negocio')]"));
		clickAndWait(negocioSection);

		final WebElement miNegocio = findFirstVisible(
				By.xpath("//*[self::button or self::a][normalize-space()='Mi Negocio']"),
				By.xpath("//*[self::button or self::a][contains(normalize-space(), 'Mi Negocio')]"));
		clickAndWait(miNegocio);

		final boolean expanded = isVisible(By.xpath("//*[self::button or self::a][normalize-space()='Agregar Negocio']"), SHORT_WAIT)
				&& isVisible(By.xpath("//*[self::button or self::a][normalize-space()='Administrar Negocios']"), SHORT_WAIT);
		report.put("Mi Negocio menu", expanded);

		if (expanded) {
			captureScreenshot("02_mi_negocio_menu_expanded");
		}
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickAndWait(wait.until(ExpectedConditions.elementToBeClickable(
				By.xpath("//*[self::button or self::a][normalize-space()='Agregar Negocio']"))));

		final boolean modalTitle = isVisible(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']"), SHORT_WAIT);
		final boolean nameField = isVisible(By.xpath("//input[@placeholder='Nombre del Negocio' or @name='businessName']"), SHORT_WAIT)
				|| isVisible(By.xpath("//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]"), SHORT_WAIT);
		final boolean limitText = isVisible(By.xpath("//*[contains(normalize-space(),'Tienes 2 de 3 negocios')]"), SHORT_WAIT);
		final boolean actionsPresent = isVisible(By.xpath("//button[normalize-space()='Cancelar']"), SHORT_WAIT)
				&& isVisible(By.xpath("//button[normalize-space()='Crear Negocio']"), SHORT_WAIT);

		final boolean pass = modalTitle && nameField && limitText && actionsPresent;
		report.put("Agregar Negocio modal", pass);

		if (pass) {
			captureScreenshot("03_agregar_negocio_modal");
		}

		if (nameField) {
			try {
				final WebElement input = findFirstVisible(
						By.xpath("//input[@placeholder='Nombre del Negocio' or @name='businessName']"),
						By.xpath("//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]"));
				input.click();
				input.clear();
				input.sendKeys("Negocio Prueba Automatizacion");
			} catch (final RuntimeException ignored) {
				// Optional action; no hard fail.
			}
		}

		clickAndWait(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[normalize-space()='Cancelar']"))));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureMiNegocioExpanded();
		clickAndWait(wait.until(ExpectedConditions.elementToBeClickable(
				By.xpath("//*[self::button or self::a][normalize-space()='Administrar Negocios']"))));

		final boolean infoGeneral = isVisible(By.xpath("//*[contains(normalize-space(), 'Informacion General') or contains(normalize-space(), 'Información General')]"), WAIT_TIMEOUT);
		final boolean detallesCuenta = isVisible(By.xpath("//*[contains(normalize-space(), 'Detalles de la Cuenta')]"), WAIT_TIMEOUT);
		final boolean tusNegocios = isVisible(By.xpath("//*[contains(normalize-space(), 'Tus Negocios')]"), WAIT_TIMEOUT);
		final boolean legal = isVisible(By.xpath("//*[contains(normalize-space(), 'Seccion Legal') or contains(normalize-space(), 'Sección Legal')]"), WAIT_TIMEOUT);
		final boolean pass = infoGeneral && detallesCuenta && tusNegocios && legal;
		report.put("Administrar Negocios view", pass);

		if (pass) {
			captureFullPageScreenshot("04_administrar_negocios_view");
		}
	}

	private void stepValidateInformacionGeneral() {
		final boolean userName = isVisible(By.xpath(
				"//*[contains(normalize-space(), 'Informacion General') or contains(normalize-space(), 'Información General')]/following::*[contains(@class,'name') or contains(@class,'user')][1]"),
				SHORT_WAIT) || isVisible(By.xpath("//*[contains(@class, 'user-name')]"), SHORT_WAIT);

		final boolean email = isVisible(By.xpath("//*[contains(@class,'email') or contains(normalize-space(), '@')]"), SHORT_WAIT);
		final boolean businessPlan = isVisible(By.xpath("//*[contains(normalize-space(), 'BUSINESS PLAN')]"), SHORT_WAIT);
		final boolean cambiarPlan = isVisible(By.xpath("//button[normalize-space()='Cambiar Plan']"), SHORT_WAIT)
				|| isVisible(By.xpath("//*[self::a or self::button][contains(normalize-space(), 'Cambiar Plan')]"), SHORT_WAIT);

		report.put("Información General", userName && email && businessPlan && cambiarPlan);
	}

	private void stepValidateDetallesCuenta() {
		final boolean cuentaCreada = isVisible(By.xpath("//*[contains(normalize-space(), 'Cuenta creada')]"), SHORT_WAIT);
		final boolean estadoActivo = isVisible(By.xpath("//*[contains(normalize-space(), 'Estado activo')]"), SHORT_WAIT);
		final boolean idiomaSeleccionado = isVisible(By.xpath("//*[contains(normalize-space(), 'Idioma seleccionado')]"), SHORT_WAIT);

		report.put("Detalles de la Cuenta", cuentaCreada && estadoActivo && idiomaSeleccionado);
	}

	private void stepValidateTusNegocios() {
		final boolean businessList = isVisible(By.xpath("//*[contains(normalize-space(), 'Tus Negocios')]"), SHORT_WAIT)
				&& isVisible(By.xpath("//*[contains(@class,'business') or contains(@class,'negocio')]"), SHORT_WAIT);
		final boolean addButton = isVisible(By.xpath(
				"//*[contains(normalize-space(), 'Tus Negocios')]/following::button[normalize-space()='Agregar Negocio'][1]"),
				SHORT_WAIT) || isVisible(By.xpath("//button[normalize-space()='Agregar Negocio']"), SHORT_WAIT);
		final boolean quota = isVisible(By.xpath("//*[contains(normalize-space(), 'Tienes 2 de 3 negocios')]"), SHORT_WAIT);

		report.put("Tus Negocios", businessList && addButton && quota);
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		clickLegalLinkAndValidate("Términos y Condiciones", "05_terminos_y_condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		clickLegalLinkAndValidate("Política de Privacidad", "06_politica_de_privacidad");
	}

	private void clickLegalLinkAndValidate(final String linkText, final String screenshotName) throws IOException {
		final String originalWindow = driver.getWindowHandle();
		final String accountPageUrl = driver.getCurrentUrl();
		if (appWindowHandle == null) {
			appWindowHandle = originalWindow;
		}
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickAndWait(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[self::a or self::button][normalize-space()='" + linkText + "']"))));

		final String finalWindow = switchToNewWindowIfOpened(beforeHandles);
		waitForPageReady();

		final boolean heading = isVisible(By.xpath("//*[contains(normalize-space(), '" + linkText + "')]"), WAIT_TIMEOUT);
		final boolean hasContent = isVisible(By.xpath("//p[string-length(normalize-space()) > 30]"), WAIT_TIMEOUT)
				|| isVisible(By.xpath("//article//*[string-length(normalize-space()) > 30]"), WAIT_TIMEOUT);
		final boolean pass = heading && hasContent;

		if ("Términos y Condiciones".equals(linkText)) {
			report.put("Términos y Condiciones", pass);
		} else {
			report.put("Política de Privacidad", pass);
		}

		captureScreenshot(screenshotName);
		legalUrls.add(linkText + " URL: " + driver.getCurrentUrl());

		// Cleanup: return to application tab/window.
		if (!finalWindow.equals(originalWindow)) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else {
			driver.navigate().back();
			waitForPageReady();
			if (!isVisible(By.xpath("//*[contains(normalize-space(), 'Seccion Legal') or contains(normalize-space(), 'Sección Legal')]"), SHORT_WAIT)) {
				driver.navigate().to(accountPageUrl);
			}
		}
		waitForPageReady();
	}

	private void ensureMiNegocioExpanded() {
		if (isVisible(By.xpath("//*[self::button or self::a][normalize-space()='Administrar Negocios']"), SHORT_WAIT)) {
			return;
		}

		try {
			final WebElement negocioSection = findFirstVisible(
					By.xpath("//*[self::button or self::a or self::div][normalize-space()='Negocio']"),
					By.xpath("//*[self::button or self::a or self::div][contains(normalize-space(), 'Negocio')]"));
			clickAndWait(negocioSection);
		} catch (final RuntimeException ignored) {
			// Continue to Mi Negocio click fallback.
		}

		clickAndWait(wait.until(ExpectedConditions.elementToBeClickable(
				By.xpath("//*[self::button or self::a][contains(normalize-space(), 'Mi Negocio')]"))));
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_WAIT);
			final WebElement accountTile = shortWait.until(ExpectedConditions.elementToBeClickable(
					By.xpath("//*[contains(normalize-space(), '" + accountEmail + "')]")));
			accountTile.click();
			waitForPageReady();
		} catch (final TimeoutException ignored) {
			// Account selector may not appear when already authenticated.
		}
	}

	@SafeVarargs
	private final WebElement findFirstVisible(final By... locators) {
		TimeoutException lastTimeout = null;
		for (final By locator : locators) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final TimeoutException e) {
				lastTimeout = e;
			}
		}
		throw new RuntimeException("Could not find visible element with configured selectors.", lastTimeout);
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForPageReady();
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private void waitForPageReady() {
		wait.until((ExpectedCondition<Boolean>) webDriver -> {
			final Object state = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
			return "complete".equals(state) || "interactive".equals(state);
		});

		try {
			wait.until(ExpectedConditions.invisibilityOfElementLocated(
					By.xpath("//*[contains(@class,'spinner') or contains(@class,'loading') or @aria-busy='true']")));
		} catch (final TimeoutException ignored) {
			// Not all views have loaders.
		}
	}

	private String switchToNewWindowIfOpened(final Set<String> beforeHandles) {
		try {
			wait.until(driver -> driver.getWindowHandles().size() > beforeHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!beforeHandles.contains(handle)) {
					driver.switchTo().window(handle);
					return handle;
				}
			}
		} catch (final TimeoutException ignored) {
			// Link opened in current tab; keep current handle.
		}
		return driver.getWindowHandle();
	}

	private void captureScreenshot(final String baseName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
		final Path target = evidenceDir.resolve(baseName + "_" + timestamp + ".png");
		Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureFullPageScreenshot(final String baseName) throws IOException {
		final JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
		final Long originalHeight = ((Number) jsExecutor.executeScript("return window.innerHeight;")).longValue();
		final Long fullHeight = ((Number) jsExecutor.executeScript(
				"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);")).longValue();

		try {
			jsExecutor.executeScript("window.scrollTo(0, 0);");
			jsExecutor.executeScript("document.body.style.zoom='100%';");
			jsExecutor.executeScript("window.resizeTo(arguments[0], arguments[1]);", 1600, Math.min(fullHeight + 200, 5000));
			waitForPageReady();
			captureScreenshot(baseName + "_full");
		} finally {
			jsExecutor.executeScript("window.resizeTo(arguments[0], arguments[1]);", 1600, originalHeight.intValue());
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Workflow Result\n");
		sb.append("=================================\n");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			sb.append(entry.getKey()).append(": ").append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL").append("\n");
		}

		if (!legalUrls.isEmpty()) {
			sb.append("\nCaptured legal URLs\n");
			sb.append("-------------------\n");
			for (final String url : legalUrls) {
				sb.append(url).append("\n");
			}
		}

		final Path reportFile = evidenceDir.resolve("final_report.txt");
		Files.writeString(reportFile, sb.toString());
		System.out.println(sb);
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}
}
