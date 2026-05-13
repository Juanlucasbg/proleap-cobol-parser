package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.After;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);

	private final Map<String, Boolean> statusByStep = new LinkedHashMap<>();
	private final Map<String, String> detailByStep = new LinkedHashMap<>();
	private final Path screenshotDir = Paths.get("target", "saleads-screenshots");

	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws Exception {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		final String headless = envOrDefault("HEADLESS", "true");
		if (!"false".equalsIgnoreCase(headless)) {
			options.addArguments("--headless=new");
		}

		final String seleniumRemoteUrl = envOrDefault("SELENIUM_REMOTE_URL", "");
		if (!seleniumRemoteUrl.isBlank()) {
			driver = new RemoteWebDriver(URI.create(seleniumRemoteUrl).toURL(), options);
		} else {
			driver = new ChromeDriver(options);
		}

		wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		Files.createDirectories(screenshotDir);

		final String saleAdsLoginUrl = envOrDefault("SALEADS_LOGIN_URL", "");
		if (!saleAdsLoginUrl.isBlank()) {
			driver.get(saleAdsLoginUrl);
		}
		waitForUiLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final String report = buildFinalReport();
		System.out.println(report);

		final boolean allPassed = statusByStep.values().stream().allMatch(Boolean::booleanValue);
		assertTrue("Some SaleADS validations failed.\n" + report, allPassed);
	}

	private void stepLoginWithGoogle() throws IOException {
		clickAndWaitFirstVisible("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
				"Continuar con Google", "Google");

		if (isVisible(By.xpath("//*[normalize-space(.)=" + toXPathLiteral(GOOGLE_ACCOUNT_EMAIL)
				+ " or @data-email=" + toXPathLiteral(GOOGLE_ACCOUNT_EMAIL) + "]"), 8)) {
			clickAndWait(By.xpath("//*[normalize-space(.)=" + toXPathLiteral(GOOGLE_ACCOUNT_EMAIL)
					+ " or @data-email=" + toXPathLiteral(GOOGLE_ACCOUNT_EMAIL) + "]"));
		}

		waitUntilAnyVisible(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar')]"));
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickIfVisible("Negocio", 5);
		clickAndWaitFirstVisible("Mi Negocio", "Mi negocio");

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickAndWaitFirstVisible("Agregar Negocio");

		assertVisibleText("Crear Nuevo Negocio");
		findNombreDelNegocioInput();
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final WebElement nombreDelNegocio = findNombreDelNegocioInput();
		nombreDelNegocio.click();
		nombreDelNegocio.clear();
		nombreDelNegocio.sendKeys("Negocio Prueba Automatización");
		clickAndWaitFirstVisible("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isVisible(byVisibleTextContains("Administrar Negocios"), 3)) {
			clickAndWaitFirstVisible("Mi Negocio", "Mi negocio");
		}
		clickAndWaitFirstVisible("Administrar Negocios");

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		captureScreenshot("04-administrar-negocios-full-page");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("Información General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String infoSectionText = extractNearestSectionText("Información General");
		assertTrue("Expected an email-like value in Información General.",
				infoSectionText.matches("(?s).*\\S+@\\S+\\.\\S+.*"));
		assertTrue("Expected user profile details (including user name) in Información General section.",
				infoSectionText.lines().map(String::trim).filter(line -> !line.isEmpty()).count() >= 4);
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

		final boolean hasList = isVisible(By.xpath(
				"//section[contains(.,'Tus Negocios')]//*[self::li or self::table or self::tbody or contains(@class,'business')]"),
				3) || isVisible(By.xpath("//*[contains(.,'Tus Negocios')]//*[self::li or self::table or self::tbody]"), 3);
		assertTrue("Expected a visible business list in Tus Negocios section.", hasList);
	}

	private void stepValidateTerminosCondiciones() throws IOException {
		final String url = validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "05-terminos");
		detailByStep.put("Términos y Condiciones", "URL: " + url);
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		final String url = validateLegalLink("Política de Privacidad", "Política de Privacidad", "06-politica");
		detailByStep.put("Política de Privacidad", "URL: " + url);
	}

	private String validateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String applicationWindow = driver.getWindowHandle();
		final String applicationUrl = driver.getCurrentUrl();
		final Set<String> windowHandlesBefore = driver.getWindowHandles();

		clickAndWaitFirstVisible(linkText);

		boolean switchedToNewTab = false;
		try {
			wait.until(d -> d.getWindowHandles().size() > windowHandlesBefore.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!windowHandlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					switchedToNewTab = true;
					break;
				}
			}
		} catch (final TimeoutException timeout) {
			// same-tab navigation is valid for this workflow
		}

		assertVisibleText(expectedHeading);
		final String legalText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected legal content text to be visible for " + expectedHeading + ".",
				legalText.trim().length() > 60);
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();
		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(applicationWindow);
			waitForUiLoad();
		} else if (!applicationUrl.equals(finalUrl)) {
			driver.navigate().back();
			waitForUiLoad();
		}

		return finalUrl;
	}

	private void runStep(final String stepName, final StepAction stepAction) {
		try {
			stepAction.run();
			statusByStep.put(stepName, true);
			if (!detailByStep.containsKey(stepName)) {
				detailByStep.put(stepName, "PASS");
			}
		} catch (final Exception ex) {
			statusByStep.put(stepName, false);
			detailByStep.put(stepName, ex.getMessage());
			try {
				captureScreenshot("failure-" + slug(stepName));
			} catch (final IOException ignored) {
				// best effort only
			}
		}
	}

	private String buildFinalReport() {
		final StringBuilder sb = new StringBuilder();
		sb.append("Final report for saleads_mi_negocio_full_test").append(System.lineSeparator());
		for (final String step : statusByStep.keySet()) {
			sb.append("- ").append(step).append(": ").append(statusByStep.get(step) ? "PASS" : "FAIL");
			if (detailByStep.containsKey(step)) {
				sb.append(" | ").append(detailByStep.get(step));
			}
			sb.append(System.lineSeparator());
		}
		return sb.toString();
	}

	private WebElement findNombreDelNegocioInput() {
		final By byPlaceholder = By
				.xpath("//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio']");
		if (isVisible(byPlaceholder, 3)) {
			return wait.until(ExpectedConditions.visibilityOfElementLocated(byPlaceholder));
		}

		final By byLabel = By.xpath(
				"//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1] | //*[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(byLabel));
	}

	private String extractNearestSectionText(final String headingText) {
		final By sectionContainerBy = By.xpath(
				"(//*[contains(normalize-space(.)," + toXPathLiteral(headingText)
						+ ")])[1]/ancestor::*[self::section or self::article or self::div][1]");
		if (isVisible(sectionContainerBy, 6)) {
			return wait.until(ExpectedConditions.visibilityOfElementLocated(sectionContainerBy)).getText();
		}

		return driver.findElement(By.tagName("body")).getText();
	}

	private void clickAndWaitFirstVisible(final String... visibleTexts) {
		for (final String text : visibleTexts) {
			final By locator = byVisibleTextContains(text);
			if (isVisible(locator, 4)) {
				clickAndWait(locator);
				return;
			}
		}
		throw new IllegalStateException("Could not find clickable element for any of: " + String.join(", ", visibleTexts));
	}

	private void clickIfVisible(final String text, final int timeoutSeconds) {
		final By locator = byVisibleTextContains(text);
		if (isVisible(locator, timeoutSeconds)) {
			clickAndWait(locator);
		}
	}

	private void clickAndWait(final By by) {
		wait.until(ExpectedConditions.elementToBeClickable(by)).click();
		waitForUiLoad();
	}

	private void waitUntilAnyVisible(final By... locators) {
		for (final By locator : locators) {
			if (isVisible(locator, 10)) {
				return;
			}
		}
		throw new IllegalStateException("None of the expected sidebar/navigation elements became visible.");
	}

	private void assertVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleTextContains(text)));
	}

	private boolean isVisible(final By by, final int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(by));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private By byVisibleTextContains(final String text) {
		return By.xpath("//*[self::a or self::button or self::span or self::div or self::h1 or self::h2 or self::h3 "
				+ "or self::p or self::label][contains(normalize-space(.)," + toXPathLiteral(text) + ")]");
	}

	private String toXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		final StringBuilder sb = new StringBuilder("concat(");
		final String[] parts = text.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(", \"'\", ");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	private void waitForUiLoad() {
		try {
			wait.until(driver -> {
				if (!(driver instanceof JavascriptExecutor)) {
					return true;
				}
				final Object state = ((JavascriptExecutor) driver).executeScript("return document.readyState");
				return "complete".equals(state);
			});
		} catch (final TimeoutException ignored) {
			// some SPA views do not transition readyState; best-effort wait is enough.
		}
		try {
			Thread.sleep(500L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String name) throws IOException {
		final String ts = TS_FORMAT.format(Instant.now());
		final String fileName = ts + "-" + slug(name) + ".png";
		final byte[] imageData = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshotDir.resolve(fileName), imageData);
	}

	private String slug(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "").replaceAll("-+$", "");
	}

	private String envOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value.trim();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
