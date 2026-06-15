package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
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

public class SaleadsMiNegocioWorkflowE2ETest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final List<String> legalUrls = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;

	@Before
	public void setUp() throws IOException {
		final boolean e2eEnabled = Boolean.parseBoolean(env("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"Set SALEADS_E2E_ENABLED=true to run the SaleADS Mi Negocio workflow test.",
				e2eEnabled);

		final String startUrl = env("SALEADS_START_URL", "");
		Assume.assumeTrue(
				"Set SALEADS_START_URL to the SaleADS login page URL for your current environment.",
				!startUrl.isBlank());

		evidenceDirectory = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(RUN_ID_FORMAT));
		Files.createDirectories(evidenceDirectory);

		final ChromeOptions chromeOptions = new ChromeOptions();
		chromeOptions.addArguments("--window-size=1920,1080");
		chromeOptions.addArguments("--disable-gpu");
		chromeOptions.addArguments("--no-sandbox");
		if (Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"))) {
			chromeOptions.addArguments("--headless=new");
		}

		driver = new ChromeDriver(chromeOptions);
		wait = new WebDriverWait(driver, Duration.ofSeconds(Integer.parseInt(env("SALEADS_TIMEOUT_SECONDS", "30"))));
		driver.get(startUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Informacion General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Terminos y Condiciones", () -> stepValidateLegalLink("T\u00e9rminos y Condiciones",
				"T\u00e9rminos y Condiciones", "08-terminos-y-condiciones"));
		runStep("Politica de Privacidad", () -> stepValidateLegalLink("Pol\u00edtica de Privacidad",
				"Pol\u00edtica de Privacidad", "09-politica-de-privacidad"));

		writeFinalReport();
		assertTrue("One or more workflow validations failed. Check target/saleads-evidence report.",
				finalReport.values().stream().allMatch(Boolean::booleanValue));
	}

	private void stepLoginWithGoogle() throws Exception {
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickFirstVisibleText("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google",
				"Google");
		switchToNewestTabIfOpened(handlesBeforeClick);
		maybeSelectGoogleAccount();

		if (driver.getWindowHandles().size() > 1) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (!driver.getCurrentUrl().contains("accounts.google.com")) {
					break;
				}
			}
		}

		waitUntilVisibleByAnyText("Negocio", "Mi Negocio");
		assertAnyVisible("Left sidebar navigation should be visible.",
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[normalize-space()='Negocio']"),
				By.xpath("//*[normalize-space()='Mi Negocio']"));
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		waitUntilVisibleByAnyText("Negocio", "Mi Negocio");

		if (isTextVisible("Negocio")) {
			clickFirstVisibleText("Negocio");
		}

		clickFirstVisibleText("Mi Negocio");
		waitUntilVisibleByAnyText("Agregar Negocio", "Administrar Negocios");

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickFirstVisibleText("Agregar Negocio");
		waitUntilVisibleByAnyText("Crear Nuevo Negocio");

		assertTextVisible("Crear Nuevo Negocio");
		assertAnyVisible("Nombre del Negocio input should exist.",
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		typeIfVisible("Nombre del Negocio", "Negocio Prueba Automatizacion");
		clickFirstVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios")) {
			clickFirstVisibleText("Mi Negocio");
		}
		clickFirstVisibleText("Administrar Negocios");
		waitUntilVisibleByAnyText("Informacion General", "Informaci\u00f3n General");

		assertAnyVisible("Informacion General section should exist.",
				By.xpath("//*[normalize-space()='Informaci\u00f3n General']"),
				By.xpath("//*[normalize-space()='Informacion General']"));
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertAnyVisible("Legal section should exist.",
				By.xpath("//*[contains(normalize-space(.),'Seccion Legal')]"),
				By.xpath("//*[contains(normalize-space(.),'Secci\u00f3n Legal')]"));
		takeScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisible("User email should be visible.",
				By.xpath("//*[contains(normalize-space(.), '@')]"),
				By.xpath("//*[contains(normalize-space(.), '" + GOOGLE_ACCOUNT_EMAIL + "')]"));
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");

		final List<WebElement> possibleNameElements = driver.findElements(By.xpath(
				"//*[normalize-space() and not(contains(normalize-space(.), '@')) and not(contains(normalize-space(.), 'BUSINESS PLAN')) and not(contains(normalize-space(.), 'Cambiar Plan')) and not(contains(normalize-space(.), 'Informaci')) and string-length(normalize-space(.)) > 2]"));
		assertTrue("User name should be visible.", !possibleNameElements.isEmpty());
	}

	private void stepValidateDetallesCuenta() {
		assertAnyVisible("Cuenta creada should be visible.",
				By.xpath("//*[contains(normalize-space(.), 'Cuenta creada')]"));
		assertAnyVisible("Estado activo should be visible.",
				By.xpath("//*[contains(normalize-space(.), 'Estado activo')]"));
		assertAnyVisible("Idioma seleccionado should be visible.",
				By.xpath("//*[contains(normalize-space(.), 'Idioma seleccionado')]"));
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertAnyVisible("Business list should be visible.",
				By.xpath("//*[contains(@class,'business')]"),
				By.xpath("//*[contains(@class,'negocio')]"),
				By.xpath("//*[contains(normalize-space(.), 'Negocio')]"));
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String appTab = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String previousUrl = driver.getCurrentUrl();

		clickFirstVisibleText(linkText);

		wait.until(d -> d.getWindowHandles().size() > handlesBefore.size()
				|| !d.getCurrentUrl().equals(previousUrl));
		switchToNewestTabIfOpened(handlesBefore);
		waitUntilVisibleByAnyText(expectedHeading);

		assertTextVisible(expectedHeading);
		assertAnyVisible("Legal content text should be visible.",
				By.xpath("//p[string-length(normalize-space()) > 40]"),
				By.xpath("//article//*[string-length(normalize-space()) > 40]"),
				By.xpath("//main//*[string-length(normalize-space()) > 40]"));

		takeScreenshot(screenshotName);
		legalUrls.add(linkText + " => " + driver.getCurrentUrl());

		if (!driver.getWindowHandle().equals(appTab)) {
			driver.close();
			driver.switchTo().window(appTab);
		} else if (!driver.getCurrentUrl().equals(previousUrl)) {
			driver.navigate().back();
		}
		waitForUiToLoad();
	}

	private void runStep(final String stepName, final StepAction stepAction) {
		try {
			stepAction.run();
			finalReport.put(stepName, true);
		} catch (final Exception error) {
			finalReport.put(stepName, false);
			try {
				takeScreenshot("failed-" + sanitizeFileName(stepName));
			} catch (final Exception ignored) {
				// Ignore screenshot failures during failure handling.
			}
			System.err.println("Step failed: " + stepName + " - " + error.getMessage());
		}
	}

	private void maybeSelectGoogleAccount() {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
		try {
			final WebElement account = shortWait.until(ExpectedConditions.elementToBeClickable(
					By.xpath("//*[normalize-space()='" + GOOGLE_ACCOUNT_EMAIL + "']")));
			account.click();
			waitForUiToLoad();
		} catch (final TimeoutException ignored) {
			// Account chooser is optional and might not appear if the session is already authenticated.
		}
	}

	private void clickFirstVisibleText(final String... texts) {
		Exception lastError = null;
		for (final String text : texts) {
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(
						By.xpath("(//*[normalize-space()=" + xpathLiteral(text)
								+ " and (self::button or self::a or @role='button' or self::div or self::span)]"
								+ "|//*[.//*[normalize-space()=" + xpathLiteral(text)
								+ "] and (self::button or self::a or @role='button' or self::div or self::span)])[1]")));
				element.click();
				waitForUiToLoad();
				return;
			} catch (final Exception error) {
				lastError = error;
			}
		}
		throw new IllegalStateException("Could not click any of the expected visible texts: " + String.join(", ", texts),
				lastError);
	}

	private void typeIfVisible(final String fieldLabelText, final String value) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
		final List<By> candidates = List.of(
				By.xpath("//input[@placeholder=" + xpathLiteral(fieldLabelText) + "]"),
				By.xpath("//input[contains(@aria-label," + xpathLiteral(fieldLabelText) + ")]"),
				By.xpath("//label[contains(normalize-space(.)," + xpathLiteral(fieldLabelText) + ")]/following::input[1]"));

		for (final By candidate : candidates) {
			try {
				final WebElement input = shortWait.until(ExpectedConditions.visibilityOfElementLocated(candidate));
				input.click();
				input.clear();
				input.sendKeys(value);
				waitForUiToLoad();
				return;
			} catch (final Exception ignored) {
				// Try next locator.
			}
		}
	}

	private void waitUntilVisibleByAnyText(final String... texts) {
		wait.until(driverInstance -> {
			for (final String text : texts) {
				if (isTextVisible(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isTextVisible(final String text) {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[normalize-space()=" + xpathLiteral(text)
				+ " or contains(normalize-space(), " + xpathLiteral(text) + ")]"));
		return elements.stream().anyMatch(WebElement::isDisplayed);
	}

	private void assertTextVisible(final String text) {
		assertTrue("Expected text to be visible: " + text, isTextVisible(text));
	}

	private void assertAnyVisible(final String message, final By... locators) {
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			if (elements.stream().anyMatch(WebElement::isDisplayed)) {
				return;
			}
		}
		throw new IllegalStateException(message);
	}

	private void waitForUiToLoad() {
		try {
			wait.until(driverInstance -> "complete".equals(((JavascriptExecutor) driverInstance)
					.executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Ignore and rely on DOM queries when the app has ongoing async requests.
		}

		try {
			Thread.sleep(350L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void switchToNewestTabIfOpened(final Set<String> handlesBefore) {
		final Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() <= handlesBefore.size()) {
			return;
		}

		for (final String handle : handlesAfter) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void takeScreenshot(final String name) throws IOException {
		final Path screenshotPath = evidenceDirectory.resolve(sanitizeFileName(name) + ".png");
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshotPath, screenshot);
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio Full Test Report").append(System.lineSeparator());
		reportBuilder.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		reportBuilder.append(System.lineSeparator());
		reportBuilder.append("Validation status:").append(System.lineSeparator());
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			reportBuilder.append("- ").append(entry.getKey()).append(": ")
					.append(entry.getValue() ? "PASS" : "FAIL")
					.append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			reportBuilder.append(System.lineSeparator());
			reportBuilder.append("Legal URLs:").append(System.lineSeparator());
			for (final String legalUrl : legalUrls) {
				reportBuilder.append("- ").append(legalUrl).append(System.lineSeparator());
			}
		}

		final Path reportPath = evidenceDirectory.resolve("final-report.txt");
		Files.writeString(reportPath, reportBuilder.toString());
		System.out.println(reportBuilder);
	}

	private String env(final String key, final String defaultValue) {
		return System.getenv().getOrDefault(key, defaultValue);
	}

	private String sanitizeFileName(final String input) {
		return input.toLowerCase()
				.replace(' ', '-')
				.replaceAll("[^a-z0-9\\-]+", "");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final StringBuilder literal = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				literal.append(", \"'\", ");
			}
			literal.append("'").append(parts[i]).append("'");
		}
		literal.append(")");
		return literal.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
