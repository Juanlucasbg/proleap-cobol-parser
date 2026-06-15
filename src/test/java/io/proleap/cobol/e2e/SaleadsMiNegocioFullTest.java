package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

public class SaleadsMiNegocioFullTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String STEP_DETALLES = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Pol\u00edtica de Privacidad";
	private static final String ACCENTED_CHARS = "ÁÉÍÓÚÜÑáéíóúüñ";
	private static final String PLAIN_CHARS = "AEIOUUNaeiouun";

	private final Map<String, String> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;

	private String startUrl;
	private String googleAccountEmail;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this external workflow test.",
				Boolean.parseBoolean(env("SALEADS_E2E_ENABLED", "false")));

		startUrl = System.getenv("SALEADS_START_URL");
		Assume.assumeTrue("Set SALEADS_START_URL to the login page URL for the current environment.",
				startUrl != null && !startUrl.isBlank());

		googleAccountEmail = env("SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com");
		final int timeoutSeconds = Integer.parseInt(env("SALEADS_E2E_TIMEOUT_SECONDS", "30"));
		final boolean headless = Boolean.parseBoolean(env("SALEADS_E2E_HEADLESS", "true"));

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence", timestamp);
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--lang=es-ES");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1));

		initReport();
	}

	@After
	public void tearDown() throws IOException {
		if (driver != null) {
			driver.quit();
		}

		writeSummary();
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		driver.get(startUrl);
		waitForUiLoad();

		executeStep(STEP_LOGIN, this::stepLoginWithGoogle);
		executeStep(STEP_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		executeStep(STEP_MODAL, this::stepValidateAgregarNegocioModal);
		executeStep(STEP_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		executeStep(STEP_INFO_GENERAL, this::stepValidateInformacionGeneral);
		executeStep(STEP_DETALLES, this::stepValidateDetallesDeLaCuenta);
		executeStep(STEP_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		executeStep(STEP_TERMINOS, () -> stepValidateLegalLink("T\u00e9rminos y Condiciones", "T\u00e9rminos y Condiciones",
				"08-terminos-y-condiciones.png"));
		executeStep(STEP_PRIVACIDAD, () -> stepValidateLegalLink("Pol\u00edtica de Privacidad", "Pol\u00edtica de Privacidad",
				"09-politica-de-privacidad.png"));

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			if (!entry.getValue().startsWith("PASS")) {
				failedSteps.add(entry.getKey() + " -> " + entry.getValue());
			}
		}

		if (!failedSteps.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failed. See summary at " + summaryPath() + ". Failures: " + failedSteps);
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> knownHandles = driver.getWindowHandles();

		clickByVisibleText("Google");
		waitForUiLoad();

		final String popupHandle = waitForNewHandle(knownHandles, 8);
		if (popupHandle != null) {
			driver.switchTo().window(popupHandle);
			waitForUiLoad();

			clickByVisibleText(googleAccountEmail);
			waitForUiLoad();

			driver.switchTo().window(appWindow);
		} else {
			clickIfVisible(googleAccountEmail);
		}

		waitForUiLoad();
		waitForVisibleAny(By.xpath(containsTextXpath("Mi Negocio")), By.xpath(containsTextXpath("Negocio")),
				By.xpath("//aside"), By.xpath("//nav"));
		assertTrue("Left sidebar navigation should be visible after login.", isAnyDisplayed(By.xpath("//aside"),
				By.xpath("//nav"), By.xpath(containsTextXpath("Mi Negocio"))));

		captureScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickIfVisible("Negocio");
		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		assertTrue("Expected 'Agregar Negocio' to be visible.", isTextVisible("Agregar Negocio"));
		assertTrue("Expected 'Administrar Negocios' to be visible.", isTextVisible("Administrar Negocios"));

		captureScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForUiLoad();

		assertTrue("Expected modal title 'Crear Nuevo Negocio'.", isTextVisible("Crear Nuevo Negocio"));
		assertTrue("Expected field label 'Nombre del Negocio'.", isTextVisible("Nombre del Negocio"));
		assertTrue("Expected business quota text 'Tienes 2 de 3 negocios'.", isTextVisible("Tienes 2 de 3 negocios"));
		assertTrue("Expected button 'Cancelar'.", isTextVisible("Cancelar"));
		assertTrue("Expected button 'Crear Negocio'.", isTextVisible("Crear Negocio"));

		captureScreenshot("03-agregar-negocio-modal.png");

		final WebElement nameInput = waitForVisibleAny(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"));
		nameInput.click();
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatizacion");
		waitForUiLoad();

		clickByVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickIfVisible("Negocio");
			clickIfVisible("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertTrue("Expected section 'Informacion General'.", isTextVisible("Informaci\u00f3n General"));
		assertTrue("Expected section 'Detalles de la Cuenta'.", isTextVisible("Detalles de la Cuenta"));
		assertTrue("Expected section 'Tus Negocios'.", isTextVisible("Tus Negocios"));
		assertTrue("Expected section 'Seccion Legal'.", isTextVisible("Secci\u00f3n Legal"));

		captureScreenshot("04-administrar-negocios.png");
	}

	private void stepValidateInformacionGeneral() {
		assertTrue("Expected user email to be visible in Informacion General.", isTextVisible(googleAccountEmail));
		assertTrue("Expected plan label 'BUSINESS PLAN'.", isTextVisible("BUSINESS PLAN"));
		assertTrue("Expected 'Cambiar Plan' button.", isTextVisible("Cambiar Plan"));
		assertTrue("Expected a visible profile name heading.", isAnyDisplayed(By.xpath("//h1[normalize-space()]"),
				By.xpath("//h2[normalize-space()]"), By.xpath("//*[contains(@class,'name') and normalize-space()]")));
	}

	private void stepValidateDetallesDeLaCuenta() {
		assertTrue("Expected 'Cuenta creada' text.", isTextVisible("Cuenta creada"));
		assertTrue("Expected 'Estado activo' text.", isTextVisible("Estado activo"));
		assertTrue("Expected 'Idioma seleccionado' text.", isTextVisible("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		assertTrue("Expected section title 'Tus Negocios'.", isTextVisible("Tus Negocios"));
		assertTrue("Expected 'Agregar Negocio' button in business section.", isTextVisible("Agregar Negocio"));
		assertTrue("Expected business quota text 'Tienes 2 de 3 negocios'.", isTextVisible("Tienes 2 de 3 negocios"));
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String urlBeforeClick = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		waitForUiLoad();

		final String newHandle = waitForNewHandle(handlesBeforeClick, 8);
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		} else {
			wait.until(d -> !d.getCurrentUrl().equals(urlBeforeClick) || isTextVisible(expectedHeading));
			waitForUiLoad();
		}

		assertTrue("Expected heading '" + expectedHeading + "'.", isTextVisible(expectedHeading));
		assertTrue("Expected legal content text to be visible.",
				driver.findElement(By.tagName("body")).getText().trim().length() > 200);

		captureScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (!driver.getWindowHandle().equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiLoad();
		waitForVisibleAny(By.xpath(containsTextXpath("Administrar Negocios")),
				By.xpath(containsTextXpath("Secci\u00f3n Legal")));
	}

	private void executeStep(final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			report.put(stepName, "PASS");
		} catch (final Throwable t) {
			report.put(stepName, "FAIL: " + t.getClass().getSimpleName() + " - " + safeMessage(t));
		}
	}

	private WebElement waitForVisibleAny(final By... locators) {
		return wait.until(d -> {
			for (final By locator : locators) {
				for (final WebElement candidate : d.findElements(locator)) {
					if (candidate.isDisplayed()) {
						return candidate;
					}
				}
			}
			return null;
		});
	}

	private void clickByVisibleText(final String text) {
		final String clickableXpath = "//*[self::button or self::a or @role='button' or self::li or self::span or self::div]"
				+ "[contains(translate(normalize-space(.), '" + ACCENTED_CHARS + "', '" + PLAIN_CHARS + "'), "
				+ xpathLiteral(normalizeForSearch(text)) + ")]";
		final String genericXpath = containsTextXpath(text);
		final WebElement element = waitForVisibleAny(By.xpath(clickableXpath), By.xpath(genericXpath));
		clickElement(element);
	}

	private void clickIfVisible(final String text) {
		final String xpath = containsTextXpath(text);
		for (final WebElement element : driver.findElements(By.xpath(xpath))) {
			if (element.isDisplayed()) {
				clickElement(element);
				return;
			}
		}
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception ignored) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiLoad();
	}

	private boolean isAnyDisplayed(final By... locators) {
		for (final By locator : locators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean isTextVisible(final String text) {
		final String xpath = containsTextXpath(text);
		for (final WebElement element : driver.findElements(By.xpath(xpath))) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(evidenceDir.resolve(fileName), screenshot);
	}

	private void waitForUiLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));

		try {
			wait.until(ExpectedConditions.invisibilityOfElementLocated(
					By.xpath("//*[contains(@class,'loading') or contains(@class,'spinner')]")));
		} catch (final TimeoutException ignored) {
			// Loading indicators can be absent in some views.
		}
	}

	private String containsTextXpath(final String text) {
		return "//*[contains(translate(normalize-space(.), '" + ACCENTED_CHARS + "', '" + PLAIN_CHARS + "'), "
				+ xpathLiteral(normalizeForSearch(text)) + ")]";
	}

	private String normalizeForSearch(final String text) {
		return text.replace('Á', 'A').replace('É', 'E').replace('Í', 'I').replace('Ó', 'O').replace('Ú', 'U')
				.replace('Ü', 'U').replace('Ñ', 'N').replace('á', 'a').replace('é', 'e').replace('í', 'i')
				.replace('ó', 'o').replace('ú', 'u').replace('ü', 'u').replace('ñ', 'n');
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
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

	private String waitForNewHandle(final Set<String> knownHandles, final int secondsToWait) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(secondsToWait));

		try {
			return shortWait.until(d -> {
				final Set<String> currentHandles = d.getWindowHandles();
				if (currentHandles.size() <= knownHandles.size()) {
					return null;
				}

				for (final String handle : currentHandles) {
					if (!knownHandles.contains(handle)) {
						return handle;
					}
				}

				return null;
			});
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private void initReport() {
		report.put(STEP_LOGIN, "NOT_RUN");
		report.put(STEP_MI_NEGOCIO_MENU, "NOT_RUN");
		report.put(STEP_MODAL, "NOT_RUN");
		report.put(STEP_ADMIN_VIEW, "NOT_RUN");
		report.put(STEP_INFO_GENERAL, "NOT_RUN");
		report.put(STEP_DETALLES, "NOT_RUN");
		report.put(STEP_TUS_NEGOCIOS, "NOT_RUN");
		report.put(STEP_TERMINOS, "NOT_RUN");
		report.put(STEP_PRIVACIDAD, "NOT_RUN");
	}

	private void writeSummary() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test summary").append(System.lineSeparator());
		builder.append(System.lineSeparator());
		builder.append("Login: ").append(report.get(STEP_LOGIN)).append(System.lineSeparator());
		builder.append("Mi Negocio menu: ").append(report.get(STEP_MI_NEGOCIO_MENU)).append(System.lineSeparator());
		builder.append("Agregar Negocio modal: ").append(report.get(STEP_MODAL)).append(System.lineSeparator());
		builder.append("Administrar Negocios view: ").append(report.get(STEP_ADMIN_VIEW)).append(System.lineSeparator());
		builder.append("Informaci\u00f3n General: ").append(report.get(STEP_INFO_GENERAL)).append(System.lineSeparator());
		builder.append("Detalles de la Cuenta: ").append(report.get(STEP_DETALLES)).append(System.lineSeparator());
		builder.append("Tus Negocios: ").append(report.get(STEP_TUS_NEGOCIOS)).append(System.lineSeparator());
		builder.append("T\u00e9rminos y Condiciones: ").append(report.get(STEP_TERMINOS)).append(System.lineSeparator());
		builder.append("Pol\u00edtica de Privacidad: ").append(report.get(STEP_PRIVACIDAD)).append(System.lineSeparator());
		builder.append(System.lineSeparator());
		builder.append("Final URLs:").append(System.lineSeparator());
		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}

		Files.writeString(summaryPath(), builder.toString(), StandardCharsets.UTF_8);
	}

	private Path summaryPath() {
		return evidenceDir.resolve("summary.txt");
	}

	private String safeMessage(final Throwable throwable) {
		if (throwable.getMessage() == null || throwable.getMessage().isBlank()) {
			return "no-message";
		}
		return throwable.getMessage().replace('\n', ' ').replace('\r', ' ');
	}

	private String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
