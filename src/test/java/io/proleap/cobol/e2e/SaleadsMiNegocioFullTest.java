package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
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

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String STEP_DETALLES = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private final Map<String, String> report = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private String appHandle;
	private String terminosUrl = "N/A";
	private String privacidadUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		final String headlessFlag = getEnv("SALEADS_HEADLESS");
		if (headlessFlag == null || Boolean.parseBoolean(headlessFlag)) {
			options.addArguments("--headless=new");
		}

		final String debuggerAddress = getEnv("SALEADS_DEBUGGER_ADDRESS");
		if (debuggerAddress != null) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(getLongEnv("SALEADS_WAIT_SECONDS", 30)));
		screenshotDirectory = createScreenshotDirectory();
		appHandle = driver.getWindowHandle();

		final String startUrl = firstNonBlank(getEnv("SALEADS_LOGIN_URL"), getEnv("SALEADS_START_URL"),
				getEnv("SALEADS_BASE_URL"));
		if (startUrl != null && isBlank(driver.getCurrentUrl())) {
			driver.navigate().to(startUrl);
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() {
		runStep(STEP_LOGIN, this::stepLoginWithGoogle);
		runStep(STEP_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(STEP_AGREGAR_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(STEP_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		runStep(STEP_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(STEP_DETALLES, this::stepValidateDetallesCuenta);
		runStep(STEP_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(STEP_TERMINOS, this::stepValidateTerminosYCondiciones);
		runStep(STEP_PRIVACIDAD, this::stepValidatePoliticaPrivacidad);

		printFinalReport();
		Assert.assertTrue("One or more SaleADS validations failed.\n" + buildReportSummary(), allPassed());
	}

	private void stepLoginWithGoogle() {
		if (isVisible(byAnyText("Negocio", "Mi Negocio"), 5)) {
			Assert.assertTrue("Left sidebar navigation is not visible.", isVisible(sidebarLocator(), 10));
			captureScreenshot("01_dashboard_loaded");
			return;
		}

		final WebElement loginButton = findFirstVisible(15,
				By.xpath("//button[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'google')]"),
				By.xpath("//a[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'google')]"),
				By.xpath("//*[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'sign in')]"),
				By.xpath("//*[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'iniciar sesion')]"));
		Assert.assertNotNull("Google login button not found.", loginButton);

		final Set<String> handlesBeforeLoginClick = driver.getWindowHandles();
		clickAndWait(loginButton);
		switchToAnyNewWindow(handlesBeforeLoginClick, 8);

		final WebElement accountOption = findFirstVisible(5,
				By.xpath("//*[normalize-space()='" + GOOGLE_ACCOUNT_EMAIL + "']"),
				By.xpath("//*[contains(normalize-space(.),'" + GOOGLE_ACCOUNT_EMAIL + "')]"));
		if (accountOption != null) {
			clickAndWait(accountOption);
		}

		waitForApplicationUi();
		Assert.assertTrue("Main application interface did not appear.", isVisible(byAnyText("Negocio", "Mi Negocio"), 30));
		Assert.assertTrue("Left sidebar navigation is not visible.", isVisible(sidebarLocator(), 30));
		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() {
		final WebElement negocioSection = waitForVisible(byClickableText("Negocio"), 20);
		clickAndWait(negocioSection);

		final WebElement miNegocio = waitForVisible(byClickableText("Mi Negocio"), 20);
		clickAndWait(miNegocio);

		Assert.assertTrue("Agregar Negocio is not visible.", isVisible(byAnyText("Agregar Negocio"), 20));
		Assert.assertTrue("Administrar Negocios is not visible.", isVisible(byAnyText("Administrar Negocios"), 20));
		captureScreenshot("02_mi_negocio_expanded_menu");
	}

	private void stepValidateAgregarNegocioModal() {
		final WebElement agregarNegocioOption = waitForVisible(byClickableText("Agregar Negocio"), 20);
		clickAndWait(agregarNegocioOption);

		Assert.assertTrue("Modal title Crear Nuevo Negocio not visible.", isVisible(byAnyText("Crear Nuevo Negocio"), 20));
		Assert.assertTrue("Nombre del Negocio input is missing.", isVisible(
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"
						+ " | //input[contains(@placeholder,'Nombre del Negocio')]"
						+ " | //input[contains(@aria-label,'Nombre del Negocio')]"),
				20));
		Assert.assertTrue("Business quota text not visible.", isVisible(byAnyText("Tienes 2 de 3 negocios"), 20));
		Assert.assertTrue("Cancelar button not visible.", isVisible(byAnyText("Cancelar"), 20));
		Assert.assertTrue("Crear Negocio button not visible.", isVisible(byAnyText("Crear Negocio"), 20));
		captureScreenshot("03_agregar_negocio_modal");

		final WebElement nameInput = findFirstVisible(3,
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"));
		if (nameInput != null) {
			nameInput.clear();
			nameInput.sendKeys("Negocio Prueba Automatizacion");
		}

		final WebElement cancelarButton = waitForVisible(byClickableText("Cancelar"), 10);
		clickAndWait(cancelarButton);
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byAnyText("Crear Nuevo Negocio")));
	}

	private void stepOpenAdministrarNegocios() {
		if (!isVisible(byAnyText("Administrar Negocios"), 3)) {
			final WebElement miNegocio = waitForVisible(byClickableText("Mi Negocio"), 10);
			clickAndWait(miNegocio);
		}

		final WebElement administrarNegocios = waitForVisible(byClickableText("Administrar Negocios"), 15);
		clickAndWait(administrarNegocios);

		Assert.assertTrue("Informacion General section is missing.", isVisible(byAnyText("Informacion General", "Informaci\u00f3n General"), 20));
		Assert.assertTrue("Detalles de la Cuenta section is missing.", isVisible(byAnyText("Detalles de la Cuenta"), 20));
		Assert.assertTrue("Tus Negocios section is missing.", isVisible(byAnyText("Tus Negocios"), 20));
		Assert.assertTrue("Seccion Legal section is missing.", isVisible(byAnyText("Seccion Legal", "Secci\u00f3n Legal"), 20));
		captureScreenshot("04_administrar_negocios_view");
	}

	private void stepValidateInformacionGeneral() {
		final String informationText = textAroundHeading("Informacion General", "Informaci\u00f3n General");
		Assert.assertTrue("User name is not visible in Informacion General.", containsLikelyName(informationText));
		Assert.assertTrue("User email is not visible in Informacion General.",
				Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(informationText).find());
		Assert.assertTrue("BUSINESS PLAN text is missing.", isVisible(byAnyText("BUSINESS PLAN"), 10));
		Assert.assertTrue("Cambiar Plan button is missing.", isVisible(byAnyText("Cambiar Plan"), 10));
	}

	private void stepValidateDetallesCuenta() {
		Assert.assertTrue("Cuenta creada is missing.", isVisible(byAnyText("Cuenta creada"), 10));
		Assert.assertTrue("Estado activo is missing.", isVisible(byAnyText("Estado activo"), 10));
		Assert.assertTrue("Idioma seleccionado is missing.", isVisible(byAnyText("Idioma seleccionado"), 10));
	}

	private void stepValidateTusNegocios() {
		final WebElement tusNegociosHeading = waitForVisible(byAnyText("Tus Negocios"), 10);
		final WebElement sectionContainer = nearestSectionContainer(tusNegociosHeading);
		final List<WebElement> possibleBusinessItems = sectionContainer.findElements(
				By.xpath(".//li | .//tr | .//*[contains(@class,'card')] | .//*[contains(@class,'business')]"));

		Assert.assertTrue("Business list is not visible.", !possibleBusinessItems.isEmpty() || sectionContainer.getText().length() > 60);
		Assert.assertTrue("Agregar Negocio button is missing in Tus Negocios section.", isVisible(byAnyText("Agregar Negocio"), 10));
		Assert.assertTrue("Business quota text is missing in Tus Negocios section.", isVisible(byAnyText("Tienes 2 de 3 negocios"), 10));
	}

	private void stepValidateTerminosYCondiciones() {
		terminosUrl = openLegalLinkAndValidate("T\u00e9rminos y Condiciones", "Terminos y Condiciones", "08_terminos_y_condiciones");
	}

	private void stepValidatePoliticaPrivacidad() {
		privacidadUrl = openLegalLinkAndValidate("Pol\u00edtica de Privacidad", "Politica de Privacidad", "09_politica_de_privacidad");
	}

	private String openLegalLinkAndValidate(final String primaryText, final String fallbackText, final String screenshotName) {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final WebElement legalLink = waitForVisible(byClickableText(primaryText, fallbackText), 15);
		clickAndWait(legalLink);

		final String newTabHandle = switchToAnyNewWindow(handlesBeforeClick, 8);
		if (newTabHandle == null) {
			waitForUiLoad();
		}

		Assert.assertTrue("Legal page heading is not visible for " + primaryText + ".", isVisible(byAnyText(primaryText, fallbackText), 20));
		final String bodyText = waitForVisible(By.tagName("body"), 20).getText();
		Assert.assertTrue("Legal content text is not visible for " + primaryText + ".", bodyText != null && bodyText.trim().length() > 120);
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (newTabHandle != null) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		if (appHandle != null && driver.getWindowHandles().contains(appHandle)) {
			driver.switchTo().window(appHandle);
		}

		return finalUrl;
	}

	private WebElement waitForVisible(final By locator, final int timeoutSeconds) {
		return new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
				.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private WebElement findFirstVisible(final int timeoutSeconds, final By... locators) {
		final long timeoutMillis = Duration.ofSeconds(timeoutSeconds).toMillis();
		final long startMillis = System.currentTimeMillis();

		while (System.currentTimeMillis() - startMillis < timeoutMillis) {
			for (final By locator : locators) {
				final List<WebElement> elements = driver.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			sleepSilently(300);
		}

		return null;
	}

	private boolean isVisible(final By locator, final int timeoutSeconds) {
		try {
			waitForVisible(locator, timeoutSeconds);
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiLoad();
	}

	private void waitForUiLoad() {
		wait.until(driverInstance -> "complete".equals(((org.openqa.selenium.JavascriptExecutor) driverInstance)
				.executeScript("return document.readyState")));
		sleepSilently(400);
	}

	private void waitForApplicationUi() {
		final WebDriverWait appWait = new WebDriverWait(driver, Duration.ofSeconds(40));
		appWait.until(driverInstance -> {
			for (final String handle : driverInstance.getWindowHandles()) {
				driverInstance.switchTo().window(handle);
				if (isElementPresent(sidebarLocator()) || isElementPresent(byAnyText("Negocio", "Mi Negocio"))) {
					appHandle = handle;
					return true;
				}
			}
			return false;
		});
		driver.switchTo().window(appHandle);
		waitForUiLoad();
	}

	private String switchToAnyNewWindow(final Set<String> handlesBefore, final int timeoutSeconds) {
		final long timeoutMillis = Duration.ofSeconds(timeoutSeconds).toMillis();
		final long startMillis = System.currentTimeMillis();

		while (System.currentTimeMillis() - startMillis < timeoutMillis) {
			final Set<String> currentHandles = driver.getWindowHandles();
			if (currentHandles.size() > handlesBefore.size()) {
				for (final String handle : currentHandles) {
					if (!handlesBefore.contains(handle)) {
						driver.switchTo().window(handle);
						waitForUiLoad();
						return handle;
					}
				}
			}
			sleepSilently(250);
		}

		return null;
	}

	private By sidebarLocator() {
		return By.xpath("//aside | //nav");
	}

	private By byAnyText(final String... texts) {
		final StringBuilder xpath = new StringBuilder("//*[");
		for (int i = 0; i < texts.length; i++) {
			final String escapedText = escapeXpathText(texts[i]);
			xpath.append("contains(normalize-space(.),").append(escapedText).append(")");
			if (i < texts.length - 1) {
				xpath.append(" or ");
			}
		}
		xpath.append("]");
		return By.xpath(xpath.toString());
	}

	private By byClickableText(final String... texts) {
		final StringBuilder xpath = new StringBuilder("//*[self::a or self::button or @role='button' or self::span or self::div][");
		for (int i = 0; i < texts.length; i++) {
			final String escapedText = escapeXpathText(texts[i]);
			xpath.append("contains(normalize-space(.),").append(escapedText).append(")");
			if (i < texts.length - 1) {
				xpath.append(" or ");
			}
		}
		xpath.append("]");
		return By.xpath(xpath.toString());
	}

	private WebElement nearestSectionContainer(final WebElement element) {
		try {
			return element.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		} catch (final NoSuchElementException ex) {
			return element;
		}
	}

	private String textAroundHeading(final String... headingTexts) {
		for (final String headingText : headingTexts) {
			final List<WebElement> candidates = driver.findElements(byAnyText(headingText));
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					return nearestSectionContainer(candidate).getText();
				}
			}
		}
		return driver.findElement(By.tagName("body")).getText();
	}

	private boolean containsLikelyName(final String text) {
		if (text == null) {
			return false;
		}
		final String cleaned = text.replace("Informacion General", "").replace("Informaci\u00f3n General", "")
				.replace("BUSINESS PLAN", "").replace("Cambiar Plan", "");
		return Pattern.compile("(?i)\\b[\\p{L}]{2,}\\s+[\\p{L}]{2,}\\b").matcher(cleaned).find();
	}

	private boolean isElementPresent(final By locator) {
		return !driver.findElements(locator).isEmpty();
	}

	private void captureScreenshot(final String name) {
		try {
			final Path target = screenshotDirectory.resolve(name + ".png");
			final byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(target, screenshotBytes);
		} catch (final IOException ex) {
			throw new IllegalStateException("Could not capture screenshot " + name, ex);
		}
	}

	private void runStep(final String stepName, final ThrowingRunnable runnable) {
		try {
			runnable.run();
			report.put(stepName, "PASS");
		} catch (final Throwable ex) {
			report.put(stepName, "FAIL: " + ex.getMessage());
		}
	}

	private boolean allPassed() {
		for (final String result : report.values()) {
			if (!result.startsWith("PASS")) {
				return false;
			}
		}
		return true;
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Workflow Report ===");
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			System.out.println("- " + entry.getKey() + ": " + entry.getValue());
		}
		System.out.println("- Terminos y Condiciones URL: " + terminosUrl);
		System.out.println("- Politica de Privacidad URL: " + privacidadUrl);
		System.out.println("- Screenshots directory: " + screenshotDirectory.toAbsolutePath());
		System.out.println("==========================================");
	}

	private String buildReportSummary() {
		final StringBuilder summary = new StringBuilder();
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			summary.append(entry.getKey()).append(" -> ").append(entry.getValue()).append('\n');
		}
		summary.append("Terminos y Condiciones URL -> ").append(terminosUrl).append('\n');
		summary.append("Politica de Privacidad URL -> ").append(privacidadUrl).append('\n');
		summary.append("Screenshots directory -> ").append(screenshotDirectory.toAbsolutePath());
		return summary.toString();
	}

	private String escapeXpathText(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final String[] parts = text.split("'");
		final StringBuilder escaped = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			escaped.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				escaped.append(",\"'\",");
			}
		}
		escaped.append(")");
		return escaped.toString();
	}

	private Path createScreenshotDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		final Path path = Paths.get("target", "screenshots", "saleads_mi_negocio_full_test", timestamp);
		return Files.createDirectories(path);
	}

	private static String getEnv(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return value.trim();
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private static long getLongEnv(final String key, final long defaultValue) {
		final String value = getEnv(key);
		if (value == null) {
			return defaultValue;
		}
		try {
			return Long.parseLong(value);
		} catch (final NumberFormatException ex) {
			return defaultValue;
		}
	}

	private static boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty() || "data:,".equals(value) || "about:blank".equals(value);
	}

	private static void sleepSilently(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
