package io.proleap.saleads.e2e;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioFullWorkflowTest {

	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Información General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "Términos y Condiciones";
	private static final String PRIVACIDAD = "Política de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private WebDriverWait shortWait;
	private Path screenshotDirectory;
	private final Map<String, Boolean> reportStatus = new LinkedHashMap<String, Boolean>();
	private final Map<String, String> reportDetails = new LinkedHashMap<String, String>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<String, String>();

	@Before
	public void setUp() throws IOException {
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		final long waitSeconds = Long.parseLong(System.getProperty("saleads.waitSeconds", "25"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		shortWait = new WebDriverWait(driver, Duration.ofSeconds(7));

		screenshotDirectory = Path.of(System.getProperty("saleads.screenshotDir", "target/surefire-reports/saleads-screenshots"));
		Files.createDirectories(screenshotDirectory);

		initializeReport();

		final String startUrl = System.getProperty("saleads.startUrl", "").trim();
		if (!startUrl.isEmpty()) {
			driver.get(startUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		try {
			stepLoginWithGoogle();
			stepOpenMiNegocioMenu();
			stepValidateAgregarNegocioModal();
			stepOpenAdministrarNegociosAndValidateSections();
			stepValidateInformacionGeneral();
			stepValidateDetallesCuenta();
			stepValidateTusNegocios();
			stepValidateTerminos();
			stepValidatePrivacidad();
		} finally {
			printFinalReport();
		}

		final List<String> failedSteps = new ArrayList<String>();
		for (final Map.Entry<String, Boolean> statusEntry : reportStatus.entrySet()) {
			if (!Boolean.TRUE.equals(statusEntry.getValue())) {
				failedSteps.add(statusEntry.getKey() + " -> " + reportDetails.get(statusEntry.getKey()));
			}
		}

		assertTrue("One or more validations failed:\n" + String.join("\n", failedSteps), failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() {
		try {
			final String applicationHandle = driver.getWindowHandle();
			final Set<String> beforeHandles = new LinkedHashSet<String>(driver.getWindowHandles());
			clickByVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
					"Login with Google", "Acceder con Google", "Iniciar sesión"));
			waitForUiToLoad();

			switchToNewestWindowIfPresent(beforeHandles);
			clickIfVisibleByText("juanlucasbarbiergarzon@gmail.com");
			waitForUiToLoad();
			switchToWindowIfPresent(applicationHandle);
			waitForUiToLoad();

			final boolean sidebarVisible = isSidebarVisible();
			final boolean appLoaded = sidebarVisible || isAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio", "Dashboard"));
			captureScreenshot("01_dashboard_loaded");

			markResult(LOGIN, appLoaded && sidebarVisible,
					buildDetail(appLoaded && sidebarVisible, "Main application and sidebar are visible.",
							"Main app or sidebar was not found after Google login."));
		} catch (final Exception exception) {
			captureScreenshotQuietly("01_login_failure");
			markResult(LOGIN, false, "Login step failed: " + exception.getMessage());
		}
	}

	private void stepOpenMiNegocioMenu() {
		try {
			clickIfVisibleByText("Negocio");
			clickByVisibleText(Arrays.asList("Mi Negocio"));
			waitForUiToLoad();

			final boolean submenuExpanded = isAnyTextVisible(Arrays.asList("Agregar Negocio", "Administrar Negocios"));
			final boolean agregarVisible = isTextVisible("Agregar Negocio");
			final boolean administrarVisible = isTextVisible("Administrar Negocios");
			captureScreenshot("02_mi_negocio_menu_expanded");

			final boolean passed = submenuExpanded && agregarVisible && administrarVisible;
			markResult(MI_NEGOCIO_MENU, passed,
					buildDetail(passed, "Mi Negocio submenu expanded with expected options visible.",
							"Mi Negocio submenu/options were not fully visible."));
		} catch (final Exception exception) {
			captureScreenshotQuietly("02_mi_negocio_menu_failure");
			markResult(MI_NEGOCIO_MENU, false, "Mi Negocio menu step failed: " + exception.getMessage());
		}
	}

	private void stepValidateAgregarNegocioModal() {
		try {
			clickByVisibleText(Arrays.asList("Agregar Negocio"));
			waitForUiToLoad();

			final boolean modalTitle = isTextVisible("Crear Nuevo Negocio");
			final boolean businessNameInput = isAnyElementVisible(Arrays.asList(
					By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
					By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
					By.xpath("//input[contains(@name,'nombre') or contains(@name,'business')]"),
					By.xpath("//textarea[contains(@placeholder,'Nombre del Negocio')]")));
			final boolean quotaText = isTextVisible("Tienes 2 de 3 negocios");
			final boolean cancelButton = isTextVisible("Cancelar");
			final boolean createButton = isTextVisible("Crear Negocio");
			captureScreenshot("03_agregar_negocio_modal");

			if (businessNameInput) {
				typeIfVisible(Arrays.asList(By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
						By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
						By.xpath("//input[contains(@name,'nombre') or contains(@name,'business')]")),
						"Negocio Prueba Automatización");
			}
			clickIfVisibleByText("Cancelar");
			waitForUiToLoad();

			final boolean passed = modalTitle && businessNameInput && quotaText && cancelButton && createButton;
			markResult(AGREGAR_NEGOCIO_MODAL, passed,
					buildDetail(passed, "Agregar Negocio modal contains all required elements.",
							"Agregar Negocio modal validations were not fully satisfied."));
		} catch (final Exception exception) {
			captureScreenshotQuietly("03_agregar_negocio_modal_failure");
			markResult(AGREGAR_NEGOCIO_MODAL, false, "Agregar Negocio modal step failed: " + exception.getMessage());
		}
	}

	private void stepOpenAdministrarNegociosAndValidateSections() {
		try {
			ensureMiNegocioExpanded();
			clickByVisibleText(Arrays.asList("Administrar Negocios"));
			waitForUiToLoad();

			final boolean infoGeneral = isTextVisible("Información General");
			final boolean detallesCuenta = isTextVisible("Detalles de la Cuenta");
			final boolean tusNegocios = isTextVisible("Tus Negocios");
			final boolean seccionLegal = isTextVisible("Sección Legal");
			captureScreenshot("04_administrar_negocios_page");

			final boolean passed = infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
			markResult(ADMINISTRAR_NEGOCIOS_VIEW, passed,
					buildDetail(passed, "Administrar Negocios view contains all required sections.",
							"One or more required account sections are missing."));
		} catch (final Exception exception) {
			captureScreenshotQuietly("04_administrar_negocios_failure");
			markResult(ADMINISTRAR_NEGOCIOS_VIEW, false, "Administrar Negocios step failed: " + exception.getMessage());
		}
	}

	private void stepValidateInformacionGeneral() {
		try {
			final String expectedEmail = System.getProperty("saleads.expectedEmail", "juanlucasbarbiergarzon@gmail.com");
			final String expectedUserName = System.getProperty("saleads.expectedUserName", "").trim();

			final boolean nameVisible;
			if (!expectedUserName.isEmpty()) {
				nameVisible = isTextVisible(expectedUserName);
			} else {
				nameVisible = isAnyTextVisible(Arrays.asList("Nombre", "Name")) || hasReadableTextNearEmail();
			}

			final boolean emailVisible = isTextVisible(expectedEmail) || pageContainsEmail();
			final boolean businessPlanVisible = isTextVisible("BUSINESS PLAN");
			final boolean cambiarPlanVisible = isTextVisible("Cambiar Plan");

			final boolean passed = nameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible;
			markResult(INFORMACION_GENERAL, passed,
					buildDetail(passed, "Información General section matches expected profile and plan details.",
							"Información General validations failed (name/email/plan/button)."));
		} catch (final Exception exception) {
			markResult(INFORMACION_GENERAL, false, "Información General validation failed: " + exception.getMessage());
		}
	}

	private void stepValidateDetallesCuenta() {
		try {
			final boolean cuentaCreada = isTextVisible("Cuenta creada");
			final boolean estadoActivo = isTextVisible("Estado activo");
			final boolean idiomaSeleccionado = isTextVisible("Idioma seleccionado");

			final boolean passed = cuentaCreada && estadoActivo && idiomaSeleccionado;
			markResult(DETALLES_CUENTA, passed,
					buildDetail(passed, "Detalles de la Cuenta section shows required account metadata.",
							"Missing one or more required fields in Detalles de la Cuenta."));
		} catch (final Exception exception) {
			markResult(DETALLES_CUENTA, false, "Detalles de la Cuenta validation failed: " + exception.getMessage());
		}
	}

	private void stepValidateTusNegocios() {
		try {
			final boolean businessListVisible = isAnyTextVisible(Arrays.asList("Tus Negocios", "Negocio"))
					&& isAnyElementVisible(Arrays.asList(By.cssSelector("table"), By.cssSelector("ul"), By.cssSelector("[role='list']"),
							By.xpath("//*[contains(@class,'card') and (contains(.,'Negocio') or contains(.,'negocio'))]")));
			final boolean agregarButton = isTextVisible("Agregar Negocio");
			final boolean quotaText = isTextVisible("Tienes 2 de 3 negocios");

			final boolean passed = businessListVisible && agregarButton && quotaText;
			markResult(TUS_NEGOCIOS, passed,
					buildDetail(passed, "Tus Negocios section contains list, CTA and quota text.",
							"Tus Negocios section is missing required business list/button/quota content."));
		} catch (final Exception exception) {
			markResult(TUS_NEGOCIOS, false, "Tus Negocios validation failed: " + exception.getMessage());
		}
	}

	private void stepValidateTerminos() {
		validateLegalLink("Términos y Condiciones", TERMINOS, "08_terminos_y_condiciones");
	}

	private void stepValidatePrivacidad() {
		validateLegalLink("Política de Privacidad", PRIVACIDAD, "09_politica_de_privacidad");
	}

	private void validateLegalLink(final String linkText, final String reportKey, final String screenshotName) {
		try {
			final String applicationHandle = driver.getWindowHandle();
			final Set<String> beforeHandles = new LinkedHashSet<String>(driver.getWindowHandles());
			final String originalUrl = driver.getCurrentUrl();

			clickByVisibleText(Arrays.asList(linkText));
			waitForUiToLoad();

			String legalHandle = applicationHandle;
			final Set<String> afterHandles = driver.getWindowHandles();
			if (afterHandles.size() > beforeHandles.size()) {
				for (final String handle : afterHandles) {
					if (!beforeHandles.contains(handle)) {
						legalHandle = handle;
						break;
					}
				}
				driver.switchTo().window(legalHandle);
				waitForUiToLoad();
			}

			final boolean headingVisible = isTextVisible(linkText);
			final boolean legalContentVisible = getPageText().trim().length() > 200;
			final String finalUrl = driver.getCurrentUrl();
			capturedUrls.put(reportKey, finalUrl);
			captureScreenshot(screenshotName);

			final boolean passed = headingVisible && legalContentVisible;
			markResult(reportKey, passed,
					buildDetail(passed, "Legal page validated at URL: " + finalUrl,
							"Legal page content/heading not found. URL reached: " + finalUrl));

			if (!legalHandle.equals(applicationHandle)) {
				driver.close();
				driver.switchTo().window(applicationHandle);
				waitForUiToLoad();
			} else if (!driver.getCurrentUrl().equals(originalUrl)) {
				driver.navigate().back();
				waitForUiToLoad();
			}
		} catch (final Exception exception) {
			captureScreenshotQuietly(screenshotName + "_failure");
			markResult(reportKey, false, "Legal link validation failed for '" + linkText + "': " + exception.getMessage());
		}
	}

	private void ensureMiNegocioExpanded() {
		if (!isTextVisible("Administrar Negocios") || !isTextVisible("Agregar Negocio")) {
			clickIfVisibleByText("Mi Negocio");
			waitForUiToLoad();
		}
	}

	private void switchToNewestWindowIfPresent(final Set<String> previousHandles) {
		try {
			shortWait.until(currentDriver -> currentDriver.getWindowHandles().size() > previousHandles.size());
		} catch (final Exception ignored) {
		}

		final Set<String> currentHandles = driver.getWindowHandles();
		if (currentHandles.size() <= previousHandles.size()) {
			return;
		}
		for (final String currentHandle : currentHandles) {
			if (!previousHandles.contains(currentHandle)) {
				driver.switchTo().window(currentHandle);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void switchToWindowIfPresent(final String windowHandle) {
		try {
			wait.until(currentDriver -> currentDriver.getWindowHandles().contains(windowHandle));
			driver.switchTo().window(windowHandle);
		} catch (final Exception ignored) {
			for (final String availableHandle : driver.getWindowHandles()) {
				driver.switchTo().window(availableHandle);
				if (isSidebarVisible() || isAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio", "Dashboard"))) {
					break;
				}
			}
		}
	}

	private void clickByVisibleText(final List<String> texts) {
		Exception lastError = null;
		for (final String text : texts) {
			try {
				final WebElement element = waitForClickableByText(text);
				clickWithWait(element);
				return;
			} catch (final Exception exception) {
				lastError = exception;
			}
		}
		throw new IllegalStateException("Unable to click any visible target text: " + texts, lastError);
	}

	private void clickIfVisibleByText(final String text) {
		try {
			final WebElement element = waitForClickableByText(text);
			clickWithWait(element);
		} catch (final Exception ignored) {
		}
	}

	private void clickWithWait(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private WebElement waitForClickableByText(final String text) {
		return wait.until(driverState -> {
			final List<WebElement> candidates = driverState.findElements(By.xpath(textLocator(text)));
			for (final WebElement candidate : candidates) {
				if (isInteractable(candidate)) {
					return toClickableAncestor(candidate);
				}
			}
			return null;
		});
	}

	private WebElement toClickableAncestor(final WebElement element) {
		if (isInteractable(element)) {
			return element;
		}
		try {
			final List<WebElement> clickableAncestors = element.findElements(By.xpath(
					"./ancestor-or-self::*[self::button or self::a or @role='button' or contains(@class,'btn') or @type='button']"));
			for (final WebElement clickableAncestor : clickableAncestors) {
				if (isInteractable(clickableAncestor)) {
					return clickableAncestor;
				}
			}
		} catch (final Exception ignored) {
		}
		return element;
	}

	private boolean isInteractable(final WebElement element) {
		try {
			return element.isDisplayed() && element.isEnabled();
		} catch (final StaleElementReferenceException staleElementReferenceException) {
			return false;
		}
	}

	private String textLocator(final String text) {
		final String literal = toXPathLiteral(text);
		return "//*[contains(normalize-space(.)," + literal + ")]";
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder literalBuilder = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int index = 0; index < parts.length; index++) {
			if (index > 0) {
				literalBuilder.append(",\"'\",");
			}
			literalBuilder.append("'").append(parts[index]).append("'");
		}
		literalBuilder.append(")");
		return literalBuilder.toString();
	}

	private void typeIfVisible(final List<By> locators, final String value) {
		for (final By locator : locators) {
			try {
				final WebElement input = shortWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				input.clear();
				input.sendKeys(value);
				waitForUiToLoad();
				return;
			} catch (final Exception ignored) {
			}
		}
	}

	private boolean isTextVisible(final String text) {
		try {
			shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(textLocator(text))));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private boolean isAnyTextVisible(final List<String> texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return true;
			}
		}
		return false;
	}

	private boolean isAnyElementVisible(final List<By> locators) {
		for (final By locator : locators) {
			try {
				final WebElement element = shortWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				if (element.isDisplayed()) {
					return true;
				}
			} catch (final Exception ignored) {
			}
		}
		return false;
	}

	private boolean isSidebarVisible() {
		return isAnyElementVisible(Arrays.asList(By.cssSelector("aside"), By.cssSelector("nav"),
				By.cssSelector("[class*='sidebar']"), By.cssSelector("[data-testid*='sidebar']")));
	}

	private boolean hasReadableTextNearEmail() {
		try {
			final Optional<WebElement> emailElement = driver.findElements(By.xpath("//*[contains(text(),'@')]")).stream()
					.filter(WebElement::isDisplayed).findFirst();
			if (emailElement.isPresent()) {
				final String parentText = emailElement.get().findElement(By.xpath("./ancestor::*[1]")).getText();
				return parentText != null && parentText.replace(emailElement.get().getText(), "").trim().length() >= 3;
			}
		} catch (final NoSuchElementException ignored) {
		}
		return false;
	}

	private boolean pageContainsEmail() {
		return EMAIL_PATTERN.matcher(getPageText()).find();
	}

	private String getPageText() {
		try {
			final Object innerText = ((JavascriptExecutor) driver)
					.executeScript("return document && document.body ? document.body.innerText : '';");
			return innerText == null ? "" : String.valueOf(innerText);
		} catch (final Exception exception) {
			return "";
		}
	}

	private void waitForUiToLoad() {
		try {
			final ExpectedCondition<Boolean> pageLoadCondition = currentDriver -> {
				try {
					final Object state = ((JavascriptExecutor) currentDriver).executeScript("return document.readyState");
					return "complete".equals(state) || "interactive".equals(state);
				} catch (final Exception exception) {
					return true;
				}
			};
			wait.until(pageLoadCondition);
		} catch (final Exception ignored) {
		}

		try {
			Thread.sleep(500L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String checkpointName) {
		try {
			final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final String fileName = checkpointName + "_" + TIMESTAMP_FORMATTER.format(LocalDateTime.now()) + ".png";
			Files.copy(source.toPath(), screenshotDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception exception) {
			throw new IllegalStateException("Failed to capture screenshot for checkpoint '" + checkpointName + "'", exception);
		}
	}

	private void captureScreenshotQuietly(final String checkpointName) {
		try {
			captureScreenshot(checkpointName);
		} catch (final Exception ignored) {
		}
	}

	private String buildDetail(final boolean passed, final String passDetail, final String failDetail) {
		return passed ? passDetail : failDetail;
	}

	private void markResult(final String stepName, final boolean passed, final String detail) {
		reportStatus.put(stepName, Boolean.valueOf(passed));
		reportDetails.put(stepName, detail);
	}

	private void initializeReport() {
		markResult(LOGIN, false, "Not executed.");
		markResult(MI_NEGOCIO_MENU, false, "Not executed.");
		markResult(AGREGAR_NEGOCIO_MODAL, false, "Not executed.");
		markResult(ADMINISTRAR_NEGOCIOS_VIEW, false, "Not executed.");
		markResult(INFORMACION_GENERAL, false, "Not executed.");
		markResult(DETALLES_CUENTA, false, "Not executed.");
		markResult(TUS_NEGOCIOS, false, "Not executed.");
		markResult(TERMINOS, false, "Not executed.");
		markResult(PRIVACIDAD, false, "Not executed.");
	}

	private void printFinalReport() {
		System.out.println("===== SaleADS Mi Negocio Full Test Report =====");
		for (final String key : reportStatus.keySet()) {
			final String status = Boolean.TRUE.equals(reportStatus.get(key)) ? "PASS" : "FAIL";
			final String detail = reportDetails.get(key);
			System.out.println("- " + key + ": " + status + " | " + detail);
		}
		if (!capturedUrls.isEmpty()) {
			System.out.println("----- Captured Legal URLs -----");
			for (final Map.Entry<String, String> urlEntry : capturedUrls.entrySet()) {
				System.out.println("* " + urlEntry.getKey() + ": " + urlEntry.getValue());
			}
		}
		System.out.println("Screenshots directory: " + screenshotDirectory.toAbsolutePath());
		System.out.println("==============================================");
	}
}
