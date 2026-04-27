package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Full E2E workflow test for SaleADS "Mi Negocio" module.
 *
 * The test is intentionally URL-agnostic and can run in dev/staging/prod as long
 * as the browser starts on a SaleADS login page, or an URL is provided through:
 * <pre>
 * -Dsaleads.login.url=https://...
 * </pre>
 *
 * By default this test is skipped in CI to avoid flaky external UI dependencies.
 * Enable it with:
 * <pre>
 * -Dsaleads.e2e.enabled=true
 * </pre>
 */
public class SaleadsMiNegocioFullTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN = "Administrar Negocios view";
	private static final String STEP_INFO = "Informacion General";
	private static final String STEP_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Terminos y Condiciones";
	private static final String STEP_PRIVACY = "Politica de Privacidad";

	private final Duration defaultTimeout = Duration
			.ofSeconds(Long.parseLong(System.getProperty("saleads.timeout.seconds", "30")));
	private final String googleEmail = System.getProperty("saleads.google.email",
			"juanlucasbarbiergarzon@gmail.com");
	private final String expectedUserName = System.getProperty("saleads.user.name", "").trim();
	private final Path screenshotsDir = Paths.get(System.getProperty("saleads.screenshots.dir",
			"target/surefire-reports/saleads-e2e/screenshots"));
	private final Path reportPath = Paths
			.get(System.getProperty("saleads.report.path", "target/surefire-reports/saleads-e2e/report.txt"));

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final List<String> legalUrls = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws Exception {
		final boolean e2eEnabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue("Skipping SaleADS UI test. Use -Dsaleads.e2e.enabled=true to execute.", e2eEnabled);

		Files.createDirectories(screenshotsDir);
		Files.createDirectories(reportPath.getParent());
		driver = createDriver();
		wait = new WebDriverWait(driver, defaultTimeout);

		final String loginUrl = System.getProperty("saleads.login.url", "").trim();
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
		}
		waitForUiLoad();
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() throws Exception {
		executeStep(STEP_LOGIN, this::loginWithGoogleAndValidateDashboard);
		executeStep(STEP_MENU, this::openMiNegocioMenuAndValidateOptions);
		executeStep(STEP_MODAL, this::validateAgregarNegocioModal);
		executeStep(STEP_ADMIN, this::openAdministrarNegociosAndValidateSections);
		executeStep(STEP_INFO, this::validateInformacionGeneralSection);
		executeStep(STEP_DETAILS, this::validateDetallesCuentaSection);
		executeStep(STEP_BUSINESSES, this::validateTusNegociosSection);
		executeStep(STEP_TERMS, this::validateTerminosLink);
		executeStep(STEP_PRIVACY, this::validatePoliticaPrivacidadLink);

		final String summary = buildSummary();
		assertTrue("One or more SaleADS workflow validations failed.\n" + summary, allStepsPassed());
	}

	private void loginWithGoogleAndValidateDashboard() throws Exception {
		final String appWindowHandle = driver.getWindowHandle();
		clickByVisibleText("Sign in with Google", "Iniciar sesion con Google", "Iniciar sesión con Google",
				"Continuar con Google", "Ingresar con Google", "Google");

		attemptGoogleAccountSelection(appWindowHandle);
		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}

		// Dashboard + sidebar confirmation.
		waitForAnyVisibleText(defaultTimeout, "Negocio", "Mi Negocio", "Dashboard", "Inicio");
		waitForLeftSidebar();
		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenuAndValidateOptions() throws Exception {
		clickByVisibleText("Mi Negocio");
		waitForAnyVisibleText(defaultTimeout, "Agregar Negocio");
		waitForAnyVisibleText(defaultTimeout, "Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForAnyVisibleText(defaultTimeout, "Crear Nuevo Negocio");

		waitForAnyVisibleText(defaultTimeout, "Nombre del Negocio");
		waitForAnyVisibleText(defaultTimeout, "Tienes 2 de 3 negocios");
		waitForAnyVisibleText(defaultTimeout, "Cancelar");
		waitForAnyVisibleText(defaultTimeout, "Crear Negocio");

		final By businessNameInputBy = By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio')]"
						+ " | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]");
		final WebElement businessNameInput = wait.until(ExpectedConditions.visibilityOfElementLocated(businessNameInputBy));
		businessNameInput.click();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");

		takeScreenshot("03-crear-negocio-modal");
		clickByVisibleText("Cancelar");
	}

	private void openAdministrarNegociosAndValidateSections() throws Exception {
		ensureMiNegocioSubmenuVisible();
		clickByVisibleText("Administrar Negocios");
		waitForSection("Informacion General", "Información General");
		waitForSection("Detalles de la Cuenta");
		waitForSection("Tus Negocios");
		waitForSection("Seccion Legal", "Sección Legal");
		takeScreenshot("04-administrar-negocios-page");
	}

	private void validateInformacionGeneralSection() {
		final WebElement section = waitForSection("Informacion General", "Información General");

		if (!expectedUserName.isEmpty()) {
			assertVisibleInContainer(section, expectedUserName);
		} else {
			assertVisibleInContainer(section, "Nombre", "Name", "Usuario", "User");
		}

		assertVisible(googleEmail);
		assertVisibleInContainer(section, "BUSINESS PLAN");
		assertVisibleInContainer(section, "Cambiar Plan");
	}

	private void validateDetallesCuentaSection() {
		final WebElement section = waitForSection("Detalles de la Cuenta");
		assertVisibleInContainer(section, "Cuenta creada");
		assertVisibleInContainer(section, "Estado activo");
		assertVisibleInContainer(section, "Idioma seleccionado");
	}

	private void validateTusNegociosSection() {
		final WebElement section = waitForSection("Tus Negocios");
		assertVisibleInContainer(section, "Agregar Negocio");
		assertVisibleInContainer(section, "Tienes 2 de 3 negocios");

		final List<WebElement> businessEntries = section
				.findElements(By.xpath(".//li | .//tr | .//*[contains(@class,'business')]"));
		final String sectionText = normalizeText(section.getText());
		final boolean listVisible = !businessEntries.isEmpty() || sectionText.contains("negocio");
		assertTrue("'Tus Negocios' list/content is not visible.", listVisible);
	}

	private void validateTerminosLink() throws Exception {
		final String finalUrl = validateLegalLinkAndReturn(
				new String[] { "Terminos y Condiciones", "Términos y Condiciones" },
				new String[] { "Terminos y Condiciones", "Términos y Condiciones" }, "05-terminos-y-condiciones");
		legalUrls.add("Terminos y Condiciones URL: " + finalUrl);
	}

	private void validatePoliticaPrivacidadLink() throws Exception {
		final String finalUrl = validateLegalLinkAndReturn(
				new String[] { "Politica de Privacidad", "Política de Privacidad" },
				new String[] { "Politica de Privacidad", "Política de Privacidad" }, "06-politica-privacidad");
		legalUrls.add("Politica de Privacidad URL: " + finalUrl);
	}

	private String validateLegalLinkAndReturn(final String[] linkTexts, final String[] headingTexts, final String screenshotName)
			throws Exception {
		final String appWindowHandle = driver.getWindowHandle();
		final String startUrl = driver.getCurrentUrl();
		final Set<String> initialHandles = driver.getWindowHandles();

		clickByVisibleText(linkTexts);

		final String targetWindow = waitForLegalTargetWindow(initialHandles, startUrl);
		if (targetWindow != null) {
			driver.switchTo().window(targetWindow);
		}

		waitForUiLoad();
		waitForAnyVisibleText(defaultTimeout, headingTexts);

		final String bodyText = normalizeText(driver.findElement(By.tagName("body")).getText());
		assertTrue("Legal content text is not visible.", bodyText.length() > 120);

		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (targetWindow != null) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		waitForSection("Seccion Legal", "Sección Legal");
		return finalUrl;
	}

	private String waitForLegalTargetWindow(final Set<String> initialHandles, final String startUrl) {
		final long timeoutAt = System.currentTimeMillis() + defaultTimeout.toMillis();
		while (System.currentTimeMillis() < timeoutAt) {
			final Set<String> currentHandles = driver.getWindowHandles();
			for (final String handle : currentHandles) {
				if (!initialHandles.contains(handle)) {
					return handle;
				}
			}
			if (!driver.getCurrentUrl().equals(startUrl)) {
				return null;
			}
			sleepSilently(250);
		}
		return null;
	}

	private void attemptGoogleAccountSelection(final String appWindowHandle) {
		final long timeoutAt = System.currentTimeMillis() + defaultTimeout.toMillis();
		while (System.currentTimeMillis() < timeoutAt) {
			final Set<String> handles = driver.getWindowHandles();
			for (final String handle : handles) {
				driver.switchTo().window(handle);
				if (isTextVisible(googleEmail, Duration.ofSeconds(1))) {
					try {
						clickByVisibleText(googleEmail);
						waitForUiLoad();
						return;
					} catch (final RuntimeException ignored) {
						// Account selector may not require interaction if SSO already resolves.
					}
				}
			}

			if (isTextVisible("Negocio", Duration.ofSeconds(1)) || isTextVisible("Mi Negocio", Duration.ofSeconds(1))) {
				return;
			}
			sleepSilently(500);
		}

		if (handlesContain(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private void ensureMiNegocioSubmenuVisible() {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(2))) {
			clickByVisibleText("Mi Negocio");
		}
		waitForAnyVisibleText(defaultTimeout, "Administrar Negocios");
	}

	private void waitForLeftSidebar() {
		wait.until(driverRef -> {
			final List<WebElement> navCandidates = driverRef
					.findElements(By.xpath("//aside | //nav | //*[@role='navigation']"));
			for (final WebElement nav : navCandidates) {
				if (nav.isDisplayed()) {
					final String text = normalizeText(nav.getText());
					if (text.contains("negocio")) {
						return true;
					}
				}
			}
			return false;
		});
	}

	private void executeStep(final String stepName, final StepAction action) {
		final Instant start = Instant.now();
		try {
			action.run();
			report.put(stepName, new StepResult(true, "PASS", Duration.between(start, Instant.now()).toMillis()));
		} catch (final Throwable throwable) {
			final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getMessage();
			report.put(stepName, new StepResult(false, "FAIL: " + message, Duration.between(start, Instant.now()).toMillis()));
		}
	}

	private WebDriver createDriver() throws MalformedURLException {
		final String browser = System.getProperty("saleads.browser", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "false"));
		final String remoteUrl = System.getProperty("saleads.remote.url", "").trim();

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			return remoteUrl.isEmpty() ? new FirefoxDriver(firefoxOptions)
					: new RemoteWebDriver(new URL(remoteUrl), firefoxOptions);
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.addArguments("--window-size=1920,1080");
			chromeOptions.addArguments("--disable-dev-shm-usage");
			chromeOptions.addArguments("--no-sandbox");
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			return remoteUrl.isEmpty() ? new ChromeDriver(chromeOptions)
					: new RemoteWebDriver(new URL(remoteUrl), chromeOptions);
		}
	}

	private void clickByVisibleText(final String... texts) {
		RuntimeException failure = null;

		for (final String text : texts) {
			final By locator = byVisibleText(text);
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
				element.click();
				waitForUiLoad();
				return;
			} catch (final RuntimeException clickFailure) {
				failure = clickFailure;
			}
		}

		if (failure == null) {
			throw new IllegalStateException("No visible-text click targets provided.");
		}
		throw failure;
	}

	private WebElement waitForAnyVisibleText(final Duration timeout, final String... texts) {
		RuntimeException failure = null;
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);

		for (final String text : texts) {
			try {
				return shortWait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
			} catch (final RuntimeException ex) {
				failure = ex;
			}
		}

		if (failure == null) {
			throw new IllegalStateException("No text candidates provided.");
		}
		throw failure;
	}

	private WebElement waitForSection(final String... headingTexts) {
		final WebElement heading = waitForAnyVisibleText(defaultTimeout, headingTexts);
		final By containerBy = By.xpath("./ancestor::*[self::section or self::article or self::div][1]");
		try {
			return heading.findElement(containerBy);
		} catch (final RuntimeException ex) {
			return heading;
		}
	}

	private void assertVisible(final String... texts) {
		waitForAnyVisibleText(defaultTimeout, texts);
	}

	private void assertVisibleInContainer(final WebElement container, final String... texts) {
		for (final String text : texts) {
			final String escaped = escapeXpathText(text);
			final List<WebElement> found = container
					.findElements(By.xpath(".//*[contains(normalize-space(.), " + escaped + ")]"));
			for (final WebElement element : found) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}
		throw new AssertionError("Expected text not visible in section: " + String.join(", ", texts));
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void waitForUiLoad() {
		wait.until(driverRef -> {
			final Object state = ((JavascriptExecutor) driverRef).executeScript("return document.readyState");
			return "complete".equals(state);
		});

		try {
			wait.until(driverRef -> {
				final Object activeRequests = ((JavascriptExecutor) driverRef)
						.executeScript("return window.jQuery ? jQuery.active : 0;");
				if (activeRequests instanceof Number) {
					return ((Number) activeRequests).intValue() == 0;
				}
				return true;
			});
		} catch (final RuntimeException ignored) {
			// jQuery not used in all environments.
		}

		sleepSilently(400);
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = screenshotsDir.resolve(checkpointName + ".png");
		Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private boolean handlesContain(final String handle) {
		return driver != null && driver.getWindowHandles().contains(handle);
	}

	private void sleepSilently(final long millis) {
		try {
			TimeUnit.MILLISECONDS.sleep(millis);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private By byVisibleText(final String text) {
		final String escaped = escapeXpathText(text);
		return By.xpath("//*[self::a or self::button or self::span or self::div or self::p or self::h1 or self::h2 "
				+ "or self::h3 or self::h4 or self::label][contains(normalize-space(.), " + escaped + ")]");
	}

	private String escapeXpathText(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		return "concat('" + text.replace("'", "',\"'\",'") + "')";
	}

	private String normalizeText(final String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private boolean allStepsPassed() {
		for (final StepResult result : report.values()) {
			if (!result.passed) {
				return false;
			}
		}
		return true;
	}

	private String buildSummary() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio workflow report\n");
		builder.append("================================\n");

		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().statusText).append(" (")
					.append(entry.getValue().durationMillis).append(" ms)\n");
		}

		for (final String legalUrl : legalUrls) {
			builder.append("- ").append(legalUrl).append('\n');
		}

		builder.append("- Screenshots directory: ").append(screenshotsDir.toAbsolutePath()).append('\n');
		builder.append("- Generated at: ").append(Instant.now()).append('\n');
		return builder.toString();
	}

	private void writeReport() throws IOException {
		Files.writeString(reportPath, buildSummary(), StandardCharsets.UTF_8);
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String statusText;
		private final long durationMillis;

		private StepResult(final boolean passed, final String statusText, final long durationMillis) {
			this.passed = passed;
			this.statusText = statusText;
			this.durationMillis = durationMillis;
		}
	}
}
