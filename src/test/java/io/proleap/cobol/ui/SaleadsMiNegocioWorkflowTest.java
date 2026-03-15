package io.proleap.cobol.ui;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String REPORT_INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Pol\u00edtica de Privacidad";

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final Path evidenceDir = createEvidenceDir();
		final Map<String, Boolean> report = initReport();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		WebDriver driver = null;
		try {
			driver = createDriver();
			final WebDriverWait wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
			driver.manage().window().maximize();

			openLoginPageIfProvided(driver);
			waitForUiLoad(driver);

			report.put(REPORT_LOGIN, executeLoginStep(driver, wait, evidenceDir));
			report.put(REPORT_MI_NEGOCIO_MENU, openMiNegocioMenu(driver, wait, evidenceDir));
			report.put(REPORT_AGREGAR_NEGOCIO_MODAL, validateAgregarNegocioModal(driver, wait, evidenceDir));
			report.put(REPORT_ADMINISTRAR_NEGOCIOS, openAdministrarNegocios(driver, wait, evidenceDir));
			report.put(REPORT_INFORMACION_GENERAL, validateInformacionGeneral(driver, evidenceDir));
			report.put(REPORT_DETALLES_CUENTA, validateDetallesCuenta(driver, evidenceDir));
			report.put(REPORT_TUS_NEGOCIOS, validateTusNegocios(driver, evidenceDir));
			report.put(REPORT_TERMINOS, validateLegalLink(driver, wait, evidenceDir, legalUrls,
					"T\u00e9rminos y Condiciones",
					Arrays.asList("Terminos y Condiciones", "T\u00e9rminos y Condiciones"), "08_terminos"));
			report.put(REPORT_PRIVACIDAD, validateLegalLink(driver, wait, evidenceDir, legalUrls,
					"Pol\u00edtica de Privacidad",
					Arrays.asList("Politica de Privacidad", "Pol\u00edtica de Privacidad"), "09_privacidad"));

			writeFinalReport(evidenceDir, report, legalUrls);

			final List<String> failedSteps = report.entrySet().stream()
					.filter(entry -> !Boolean.TRUE.equals(entry.getValue()))
					.map(Map.Entry::getKey)
					.collect(Collectors.toList());
			if (!failedSteps.isEmpty()) {
				fail("SaleADS Mi Negocio workflow validation failed for: " + String.join(", ", failedSteps)
						+ ". Evidence folder: " + evidenceDir.toAbsolutePath());
			}
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	private WebDriver createDriver() {
		final ChromeOptions options = new ChromeOptions();
		final String headless = System.getenv("SALEADS_HEADLESS");
		if (headless == null || !"false".equalsIgnoreCase(headless.trim())) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		return new ChromeDriver(options);
	}

	private void openLoginPageIfProvided(final WebDriver driver) {
		final String envUrl = System.getenv("SALEADS_LOGIN_URL");
		if (envUrl != null && !envUrl.isBlank()) {
			driver.get(envUrl.trim());
		}
	}

	private boolean executeLoginStep(final WebDriver driver, final WebDriverWait wait, final Path evidenceDir)
			throws IOException {
		if (isSidebarVisible(driver)) {
			takeScreenshot(driver, evidenceDir, "01_dashboard_loaded_already_logged");
			return true;
		}

		final boolean clickedLoginButton = clickAnyVisibleText(driver, wait,
				Arrays.asList("Sign in with Google", "Continuar con Google", "Iniciar sesi\u00f3n con Google",
						"Inicia sesi\u00f3n con Google", "Login", "Iniciar sesi\u00f3n", "Ingresar"),
				DEFAULT_TIMEOUT);
		if (!clickedLoginButton) {
			takeScreenshot(driver, evidenceDir, "01_login_button_not_found");
			return false;
		}
		waitForUiLoad(driver);

		clickAnyVisibleText(driver, wait, Arrays.asList("juanlucasbarbiergarzon@gmail.com"), SHORT_TIMEOUT);
		waitForUiLoad(driver);

		final boolean mainAppVisible = waitUntilVisible(driver, By.xpath("//main | //*[@role='main']"), DEFAULT_TIMEOUT)
				|| waitUntilVisible(driver, By.xpath("//*[contains(@class,'dashboard') or contains(@id,'dashboard')]"),
						SHORT_TIMEOUT);
		final boolean sidebarVisible = waitUntilVisible(driver, By.xpath("//aside | //nav"), DEFAULT_TIMEOUT)
				&& isSidebarVisible(driver);

		takeScreenshot(driver, evidenceDir, "01_dashboard_loaded");
		return mainAppVisible && sidebarVisible;
	}

	private boolean openMiNegocioMenu(final WebDriver driver, final WebDriverWait wait, final Path evidenceDir)
			throws IOException {
		if (!isSidebarVisible(driver)) {
			takeScreenshot(driver, evidenceDir, "02_sidebar_not_visible");
			return false;
		}

		clickAnyVisibleText(driver, wait, Arrays.asList("Negocio"), SHORT_TIMEOUT);
		final boolean clickedMiNegocio = clickAnyVisibleText(driver, wait, Arrays.asList("Mi Negocio"), DEFAULT_TIMEOUT);
		if (!clickedMiNegocio) {
			takeScreenshot(driver, evidenceDir, "02_mi_negocio_not_clickable");
			return false;
		}
		waitForUiLoad(driver);

		final boolean agregarVisible = isAnyTextVisible(driver, Arrays.asList("Agregar Negocio"));
		final boolean administrarVisible = isAnyTextVisible(driver, Arrays.asList("Administrar Negocios"));
		takeScreenshot(driver, evidenceDir, "02_menu_expanded");
		return agregarVisible && administrarVisible;
	}

	private boolean validateAgregarNegocioModal(final WebDriver driver, final WebDriverWait wait, final Path evidenceDir)
			throws IOException {
		final boolean clickedAgregar = clickAnyVisibleText(driver, wait, Arrays.asList("Agregar Negocio"), DEFAULT_TIMEOUT);
		if (!clickedAgregar) {
			takeScreenshot(driver, evidenceDir, "03_agregar_negocio_click_failed");
			return false;
		}
		waitForUiLoad(driver);

		final boolean titleVisible = isAnyTextVisible(driver,
				Arrays.asList("Crear Nuevo Negocio", "Crear nuevo negocio"));
		final boolean nombreVisible = isAnyTextVisible(driver,
				Arrays.asList("Nombre del Negocio", "Nombre de Negocio", "Nombre del negocio"));
		final boolean quotaVisible = isAnyTextVisible(driver, Arrays.asList("Tienes 2 de 3 negocios"));
		final boolean cancelVisible = isAnyTextVisible(driver, Arrays.asList("Cancelar"));
		final boolean createVisible = isAnyTextVisible(driver, Arrays.asList("Crear Negocio"));

		fillFieldByLabel(driver, wait, Arrays.asList("Nombre del Negocio", "Nombre del negocio"),
				"Negocio Prueba Automatizacion");
		clickAnyVisibleText(driver, wait, Arrays.asList("Cancelar"), SHORT_TIMEOUT);
		waitForUiLoad(driver);

		takeScreenshot(driver, evidenceDir, "03_agregar_negocio_modal");
		return titleVisible && nombreVisible && quotaVisible && cancelVisible && createVisible;
	}

	private boolean openAdministrarNegocios(final WebDriver driver, final WebDriverWait wait, final Path evidenceDir)
			throws IOException {
		if (!isAnyTextVisible(driver, Arrays.asList("Administrar Negocios"))) {
			clickAnyVisibleText(driver, wait, Arrays.asList("Mi Negocio"), SHORT_TIMEOUT);
			waitForUiLoad(driver);
		}

		final boolean clickedAdministrar = clickAnyVisibleText(driver, wait, Arrays.asList("Administrar Negocios"),
				DEFAULT_TIMEOUT);
		if (!clickedAdministrar) {
			takeScreenshot(driver, evidenceDir, "04_administrar_negocios_click_failed");
			return false;
		}
		waitForUiLoad(driver);

		final boolean infoGeneral = isAnyTextVisible(driver,
				Arrays.asList("Informacion General", "Informaci\u00f3n General"));
		final boolean detallesCuenta = isAnyTextVisible(driver, Arrays.asList("Detalles de la Cuenta"));
		final boolean tusNegocios = isAnyTextVisible(driver, Arrays.asList("Tus Negocios"));
		final boolean seccionLegal = isAnyTextVisible(driver,
				Arrays.asList("Seccion Legal", "Secci\u00f3n Legal"));

		takeScreenshot(driver, evidenceDir, "04_administrar_negocios_full");
		return infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
	}

	private boolean validateInformacionGeneral(final WebDriver driver, final Path evidenceDir) throws IOException {
		final boolean nameVisible = isLikelyUserNameVisible(driver);
		final boolean emailVisible = isAnyVisibleCss(driver, "//*[contains(text(), '@')]");
		final boolean businessPlanVisible = isAnyTextVisible(driver, Arrays.asList("BUSINESS PLAN"));
		final boolean changePlanVisible = isAnyTextVisible(driver, Arrays.asList("Cambiar Plan"));
		takeScreenshot(driver, evidenceDir, "05_informacion_general");
		return nameVisible && emailVisible && businessPlanVisible && changePlanVisible;
	}

	private boolean validateDetallesCuenta(final WebDriver driver, final Path evidenceDir) throws IOException {
		final boolean cuentaCreada = isAnyTextVisible(driver,
				Arrays.asList("Cuenta creada", "Cuenta Creada"));
		final boolean estadoActivo = isAnyTextVisible(driver,
				Arrays.asList("Estado activo", "Estado Activo"));
		final boolean idiomaSeleccionado = isAnyTextVisible(driver,
				Arrays.asList("Idioma seleccionado", "Idioma Seleccionado"));
		takeScreenshot(driver, evidenceDir, "06_detalles_cuenta");
		return cuentaCreada && estadoActivo && idiomaSeleccionado;
	}

	private boolean validateTusNegocios(final WebDriver driver, final Path evidenceDir) throws IOException {
		final boolean businessListVisible = isAnyTextVisible(driver,
				Arrays.asList("Tus Negocios", "Negocios"));
		final boolean addButtonVisible = isAnyTextVisible(driver, Arrays.asList("Agregar Negocio"));
		final boolean quotaVisible = isAnyTextVisible(driver, Arrays.asList("Tienes 2 de 3 negocios"));
		takeScreenshot(driver, evidenceDir, "07_tus_negocios");
		return businessListVisible && addButtonVisible && quotaVisible;
	}

	private boolean validateLegalLink(final WebDriver driver, final WebDriverWait wait, final Path evidenceDir,
			final Map<String, String> legalUrls, final String reportKey, final List<String> expectedHeadings,
			final String screenshotName) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());

		final boolean clicked = clickAnyVisibleText(driver, wait, expectedHeadings, DEFAULT_TIMEOUT);
		if (!clicked) {
			takeScreenshot(driver, evidenceDir, screenshotName + "_link_not_found");
			return false;
		}

		waitForWindowOrUrlChange(driver, beforeHandles);
		waitForUiLoad(driver);

		final String activeWindow = switchToNewWindowIfPresent(driver, beforeHandles);
		final boolean headingVisible = isAnyTextVisible(driver, expectedHeadings);
		final boolean legalTextVisible = getBodyTextLength(driver) > 120;
		final String finalUrl = driver.getCurrentUrl();
		legalUrls.put(reportKey, finalUrl);

		takeScreenshot(driver, evidenceDir, screenshotName);

		returnToAppWindow(driver, appWindow, activeWindow);
		waitForUiLoad(driver);

		return headingVisible && legalTextVisible;
	}

	private void waitForWindowOrUrlChange(final WebDriver driver, final Set<String> beforeHandles) {
		final String beforeUrl = driver.getCurrentUrl();
		final WebDriverWait shortWait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		try {
			shortWait.until(webDriver -> {
				final boolean newWindow = webDriver.getWindowHandles().size() > beforeHandles.size();
				final boolean urlChanged = !beforeUrl.equals(webDriver.getCurrentUrl());
				return newWindow || urlChanged;
			});
		} catch (final TimeoutException ignored) {
			// Continue with best-effort validation and evidence capture.
		}
	}

	private String switchToNewWindowIfPresent(final WebDriver driver, final Set<String> beforeHandles) {
		for (final String handle : driver.getWindowHandles()) {
			if (!beforeHandles.contains(handle)) {
				driver.switchTo().window(handle);
				return handle;
			}
		}
		return driver.getWindowHandle();
	}

	private void returnToAppWindow(final WebDriver driver, final String appWindow, final String activeWindow) {
		if (!appWindow.equals(activeWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
			return;
		}

		try {
			driver.navigate().back();
		} catch (final Exception ignored) {
			// Leave current page as-is if browser history is unavailable.
		}
	}

	private void fillFieldByLabel(final WebDriver driver, final WebDriverWait wait, final List<String> labels,
			final String value) {
		for (final String label : labels) {
			final List<WebElement> labelElements = driver.findElements(
					By.xpath("//*[normalize-space()='" + escapeQuotes(label) + "']"));
			for (final WebElement element : labelElements) {
				try {
					final WebElement input = element.findElement(By.xpath(".//following::input[1]"));
					wait.until(ExpectedConditions.visibilityOf(input));
					input.clear();
					input.sendKeys(value);
					return;
				} catch (final NoSuchElementException ignored) {
					// Try next candidate label.
				}
			}
		}
	}

	private boolean clickAnyVisibleText(final WebDriver driver, final WebDriverWait wait, final List<String> texts,
			final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		for (final String text : texts) {
			final By exact = By.xpath("//*[self::button or self::a or @role='button' or self::span or self::div]"
					+ "[normalize-space()='" + escapeQuotes(text) + "']");
			final By contains = By.xpath("//*[self::button or self::a or @role='button' or self::span or self::div]"
					+ "[contains(normalize-space(),'" + escapeQuotes(text) + "')]");

			final List<By> selectors = Arrays.asList(exact, contains);
			for (final By selector : selectors) {
				try {
					final List<WebElement> matches = localWait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(selector));
					for (final WebElement match : matches) {
						if (!match.isDisplayed()) {
							continue;
						}
						scrollIntoView(driver, match);
						clickRobust(driver, wait, match);
						waitForUiLoad(driver);
						return true;
					}
				} catch (final TimeoutException ignored) {
					// Try next selector variant.
				}
			}
		}
		return false;
	}

	private void clickRobust(final WebDriver driver, final WebDriverWait wait, final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void scrollIntoView(final WebDriver driver, final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private boolean isSidebarVisible(final WebDriver driver) {
		return isAnyVisibleCss(driver, "//aside") || isAnyVisibleCss(driver, "//nav");
	}

	private boolean isAnyTextVisible(final WebDriver driver, final List<String> texts) {
		for (final String text : texts) {
			if (isAnyVisibleCss(driver, "//*[normalize-space()='" + escapeQuotes(text) + "']")
					|| isAnyVisibleCss(driver, "//*[contains(normalize-space(),'" + escapeQuotes(text) + "')]")) {
				return true;
			}
		}
		return false;
	}

	private boolean isAnyVisibleCss(final WebDriver driver, final String xpath) {
		for (final WebElement element : driver.findElements(By.xpath(xpath))) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean waitUntilVisible(final WebDriver driver, final By by, final Duration timeout) {
		try {
			final WebDriverWait wait = new WebDriverWait(driver, timeout);
			wait.until(ExpectedConditions.visibilityOfElementLocated(by));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private boolean isLikelyUserNameVisible(final WebDriver driver) {
		final List<WebElement> candidates = new ArrayList<>(driver.findElements(By.xpath(
				"//*[contains(@class,'user') or contains(@class,'name') or contains(@data-testid,'user') or contains(text(), 'Hola')]")));
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed() && candidate.getText() != null && candidate.getText().trim().length() > 2) {
				return true;
			}
		}
		return false;
	}

	private int getBodyTextLength(final WebDriver driver) {
		final List<WebElement> body = driver.findElements(By.tagName("body"));
		if (body.isEmpty()) {
			return 0;
		}
		final String text = body.get(0).getText();
		return text == null ? 0 : text.trim().length();
	}

	private void waitForUiLoad(final WebDriver driver) {
		try {
			final WebDriverWait wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
			wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Keep best-effort behavior for pages with continuous background loading.
		}
		try {
			Thread.sleep(700);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final WebDriver driver, final Path evidenceDir, final String checkpoint) throws IOException {
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final Path screenshotPath = evidenceDir.resolve(checkpoint + ".png");
		Files.write(screenshotPath, screenshot);
	}

	private Map<String, Boolean> initReport() {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		report.put(REPORT_LOGIN, false);
		report.put(REPORT_MI_NEGOCIO_MENU, false);
		report.put(REPORT_AGREGAR_NEGOCIO_MODAL, false);
		report.put(REPORT_ADMINISTRAR_NEGOCIOS, false);
		report.put(REPORT_INFORMACION_GENERAL, false);
		report.put(REPORT_DETALLES_CUENTA, false);
		report.put(REPORT_TUS_NEGOCIOS, false);
		report.put(REPORT_TERMINOS, false);
		report.put(REPORT_PRIVACIDAD, false);
		return report;
	}

	private void writeFinalReport(final Path evidenceDir, final Map<String, Boolean> report,
			final Map<String, String> legalUrls) throws IOException {
		final StringBuilder out = new StringBuilder();
		out.append("SaleADS Mi Negocio - Final Report").append(System.lineSeparator());
		out.append("Generated: ").append(LocalDateTime.now()).append(System.lineSeparator());
		out.append(System.lineSeparator());
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			out.append(entry.getKey()).append(": ").append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL")
					.append(System.lineSeparator());
		}
		out.append(System.lineSeparator());
		if (!legalUrls.isEmpty()) {
			out.append("Legal URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				out.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}
		Files.writeString(evidenceDir.resolve("final-report.txt"), out.toString());
	}

	private Path createEvidenceDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		final Path evidenceDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private String escapeQuotes(final String text) {
		return text.replace("'", "\\'");
	}
}
