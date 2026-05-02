package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
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

/**
 * End-to-end workflow test for SaleADS Mi Negocio module.
 *
 * Runtime configuration:
 * -Dsaleads.login.url=<env login page URL> (required)
 * -Dsaleads.headless=true|false (optional, default true)
 * -Dsaleads.timeout.seconds=30 (optional)
 * -Dsaleads.selenium.remote.url=http://<grid>:4444/wd/hub (optional)
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Duration timeout;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException, MalformedURLException {
		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", null);
		Assume.assumeTrue("Set -Dsaleads.login.url (or SALEADS_LOGIN_URL) to run this E2E test.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"));
		final long timeoutSeconds = Long.parseLong(readConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", "30"));
		timeout = Duration.ofSeconds(timeoutSeconds);

		driver = buildWebDriver(headless);
		wait = new WebDriverWait(driver, timeout);
		driver.manage().window().setSize(new Dimension(1600, 1200));

		evidenceDir = Paths.get("target", "saleads-evidence", TS_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_AGREGAR_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMINISTRAR_VIEW, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(REPORT_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(REPORT_TERMINOS, () -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08_terminos.png"));
		runStep(REPORT_PRIVACIDAD, () -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09_politica.png"));

		final List<String> failures = new ArrayList<>();
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().pass) {
				failures.add(entry.getKey() + " -> " + entry.getValue().message);
			}
		}

		assertTrue("One or more validations failed: " + failures, failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		final WebElement loginButton = waitForAnyClickable(Arrays.asList(
				byClickableText("Sign in with Google"),
				byClickableText("Iniciar sesión con Google"),
				byClickableText("Iniciar sesion con Google"),
				byClickableText("Continuar con Google"),
				byClickableText("Ingresar con Google"),
				byClickableText("Google")));
		clickAndWait(loginButton);

		// Optional account picker selection.
		optionalClick(byClickableText(GOOGLE_EMAIL), Duration.ofSeconds(12));

		waitForAnyVisible(Arrays.asList(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar') or contains(@class,'Sidebar')]")));
		takeScreenshot("01_dashboard_loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		waitForAnyVisible(Arrays.asList(By.xpath("//aside"), By.xpath("//nav")));

		optionalClick(byClickableText("Negocio"), Duration.ofSeconds(3));
		clickAndWait(waitForAnyClickable(Arrays.asList(byClickableText("Mi Negocio"), byTextFallback("Mi Negocio"))));

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		takeScreenshot("02_mi_negocio_expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickAndWait(waitForAnyClickable(Arrays.asList(byClickableText("Agregar Negocio"), byTextFallback("Agregar Negocio"))));

		waitForVisibleText("Crear Nuevo Negocio");
		assertFalse("Expected 'Nombre del Negocio' input field.", driver.findElements(
				By.xpath("//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='Nombre del Negocio' or @name='nombreDelNegocio' or @name='nombre_negocio' or @id='nombreDelNegocio' or @id='nombre_negocio']")).isEmpty());
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForAnyVisible(Arrays.asList(byClickableText("Cancelar"), byTextFallback("Cancelar")));
		waitForAnyVisible(Arrays.asList(byClickableText("Crear Negocio"), byTextFallback("Crear Negocio")));

		takeScreenshot("03_agregar_negocio_modal.png");

		final List<WebElement> nombreField = driver.findElements(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='Nombre del Negocio' or @name='nombreDelNegocio' or @name='nombre_negocio' or @id='nombreDelNegocio' or @id='nombre_negocio']"));
		if (!nombreField.isEmpty()) {
			nombreField.get(0).clear();
			nombreField.get(0).sendKeys("Negocio Prueba Automatizacion");
		}

		clickAndWait(waitForAnyClickable(Arrays.asList(byClickableText("Cancelar"), byTextFallback("Cancelar"))));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (driver.findElements(byTextFallback("Administrar Negocios")).isEmpty()) {
			optionalClick(byClickableText("Mi Negocio"), Duration.ofSeconds(4));
		}

		clickAndWait(waitForAnyClickable(Arrays.asList(byClickableText("Administrar Negocios"), byTextFallback("Administrar Negocios"))));

		waitForVisibleText("Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal");
		takeFullPageScreenshot("04_administrar_negocios_view.png");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = sectionByHeading("Información General");
		final String sectionText = section.getText();

		assertTrue("User email is not visible in Informacion General section.", EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("BUSINESS PLAN text is missing.", sectionText.contains("BUSINESS PLAN"));
		assertTrue("Cambiar Plan button is missing.",
				!section.findElements(By.xpath(".//button[normalize-space()='Cambiar Plan'] | .//a[normalize-space()='Cambiar Plan']")).isEmpty());

		// User name: any non-empty visible line that is not an email and not known static labels.
		assertFalse("User name is not visible in Informacion General section.",
				section.findElements(By.xpath(
						".//*[normalize-space()!='' and not(contains(normalize-space(),'@')) and normalize-space()!='BUSINESS PLAN' and normalize-space()!='Cambiar Plan' and normalize-space()!='Información General' and normalize-space()!='Informacion General']"))
						.isEmpty());
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = sectionByHeading("Detalles de la Cuenta");
		final String sectionText = section.getText();
		assertTrue("'Cuenta creada' text is missing.", sectionText.contains("Cuenta creada"));
		assertTrue("'Estado activo' text is missing.", sectionText.contains("Estado activo"));
		assertTrue("'Idioma seleccionado' text is missing.", sectionText.contains("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = sectionByHeading("Tus Negocios");
		final String sectionText = section.getText();

		assertTrue("Business list is not visible.",
				!section.findElements(By.xpath(
						".//li[normalize-space()!=''] | .//tr[normalize-space()!=''] | .//div[normalize-space()!='' and not(.//button)]"))
						.isEmpty());
		assertFalse("Agregar Negocio button is missing inside Tus Negocios.",
				section.findElements(By.xpath(".//button[normalize-space()='Agregar Negocio'] | .//a[normalize-space()='Agregar Negocio']")).isEmpty());
		assertTrue("'Tienes 2 de 3 negocios' text is missing.", sectionText.contains("Tienes 2 de 3 negocios"));
	}

	private void stepValidateLegalLink(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickAndWait(waitForAnyClickable(Arrays.asList(byClickableText(linkText), byTextFallback(linkText))));

		boolean switchedToNewTab = false;
		try {
			new WebDriverWait(driver, timeout).until(d -> d.getWindowHandles().size() > handlesBeforeClick.size());
			final Set<String> afterClick = driver.getWindowHandles();
			for (String handle : afterClick) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					switchedToNewTab = true;
					break;
				}
			}
		} catch (TimeoutException ignored) {
			// Same-tab navigation is supported as a valid behavior.
		}

		waitForVisibleText(headingText);
		assertFalse("Expected visible legal content text for " + headingText + ".",
				driver.findElements(By.xpath("//p[string-length(normalize-space()) > 40] | //article//*[string-length(normalize-space()) > 40]"))
						.isEmpty());

		takeScreenshot(screenshotName);
		legalUrls.put(headingText, driver.getCurrentUrl());

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForVisibleText("Sección Legal");
		}
	}

	private void runStep(final String reportKey, final StepAction action) {
		try {
			action.run();
			report.put(reportKey, StepResult.pass());
		} catch (Throwable t) {
			try {
				takeScreenshot("FAILED_" + normalizeFilename(reportKey) + ".png");
			} catch (Exception ignored) {
				// Ignore screenshot failures while capturing error context.
			}
			report.put(reportKey, StepResult.fail(t.getClass().getSimpleName() + ": " + Objects.toString(t.getMessage(), "")));
		}
	}

	private WebElement sectionByHeading(final String headingText) {
		waitForVisibleText(headingText);
		return wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][normalize-space()=" + xpathLiteral(headingText)
						+ "]/ancestor::*[self::section or self::div][1]")));
	}

	private WebElement waitForAnyClickable(final List<By> locators) {
		return wait.until(driverArg -> {
			for (By locator : locators) {
				final List<WebElement> elements = driverArg.findElements(locator);
				for (WebElement element : elements) {
					if (element.isDisplayed() && element.isEnabled()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private WebElement waitForAnyVisible(final List<By> locators) {
		return wait.until(driverArg -> {
			for (By locator : locators) {
				for (WebElement element : driverArg.findElements(locator)) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private void waitForVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(byTextFallback(text)));
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiToLoad();
	}

	private void optionalClick(final By locator, final Duration optionalTimeout) {
		try {
			new WebDriverWait(driver, optionalTimeout).until(ExpectedConditions.elementToBeClickable(locator)).click();
			waitForUiToLoad();
		} catch (TimeoutException ignored) {
			// Optional element not present in this flow.
		}
	}

	private void waitForUiToLoad() {
		wait.until(driverArg -> {
			try {
				return "complete".equals(((JavascriptExecutor) driverArg).executeScript("return document.readyState"));
			} catch (Exception e) {
				return true;
			}
		});
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(evidenceDir.resolve(fileName), bytes);
	}

	private void takeFullPageScreenshot(final String fileName) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			if (driver instanceof JavascriptExecutor) {
				final JavascriptExecutor js = (JavascriptExecutor) driver;
				final Number fullWidth = (Number) js.executeScript(
						"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, window.innerWidth);");
				final Number fullHeight = (Number) js.executeScript(
						"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, window.innerHeight);");

				final int targetWidth = Math.min(Math.max(fullWidth.intValue(), originalSize.getWidth()), 2200);
				final int targetHeight = Math.min(Math.max(fullHeight.intValue(), originalSize.getHeight()), 9000);
				driver.manage().window().setSize(new Dimension(targetWidth, targetHeight));
				waitForUiToLoad();
			}
			takeScreenshot(fileName);
		} catch (RuntimeException ex) {
			takeScreenshot(fileName);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}
	}

	private By byClickableText(final String text) {
		final String escapedText = xpathLiteral(text);
		return By.xpath("//button[normalize-space()=" + escapedText + "]"
				+ " | //a[normalize-space()=" + escapedText + "]"
				+ " | //*[@role='button' and normalize-space()=" + escapedText + "]"
				+ " | //span[normalize-space()=" + escapedText + "]/ancestor::*[self::button or self::a][1]");
	}

	private By byTextFallback(final String text) {
		return By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char ch = chars[i];
			if (i > 0) {
				builder.append(",");
			}
			if (ch == '\'') {
				builder.append("\"'\"");
			} else if (ch == '\"') {
				builder.append("'\"'");
			} else {
				builder.append("'").append(ch).append("'");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private WebDriver buildWebDriver(final boolean headless) throws MalformedURLException {
		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1600,1200");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");

		final String remoteUrl = readConfig("saleads.selenium.remote.url", "SELENIUM_REMOTE_URL", null);
		if (remoteUrl != null && !remoteUrl.isBlank()) {
			return new RemoteWebDriver(URI.create(remoteUrl).toURL(), options);
		}
		return new ChromeDriver(options);
	}

	private String readConfig(final String propertyName, final String envName, final String defaultValue) {
		final String property = System.getProperty(propertyName);
		if (property != null && !property.isBlank()) {
			return property;
		}

		final String env = System.getenv(envName);
		if (env != null && !env.isBlank()) {
			return env;
		}

		return defaultValue;
	}

	private String normalizeFilename(final String input) {
		return input.toLowerCase().replaceAll("[^a-z0-9]+", "_");
	}

	private void writeFinalReport() throws IOException {
		if (report.isEmpty()) {
			return;
		}

		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Full Workflow - Final Report");
		lines.add("Evidence folder: " + evidenceDir.toAbsolutePath());
		lines.add("");

		final List<String> orderedFields = Arrays.asList(
				REPORT_LOGIN,
				REPORT_MI_NEGOCIO_MENU,
				REPORT_AGREGAR_MODAL,
				REPORT_ADMINISTRAR_VIEW,
				REPORT_INFO_GENERAL,
				REPORT_DETALLES_CUENTA,
				REPORT_TUS_NEGOCIOS,
				REPORT_TERMINOS,
				REPORT_PRIVACIDAD);

		for (String key : orderedFields) {
			final StepResult result = report.getOrDefault(key, StepResult.fail("NOT_EXECUTED"));
			lines.add(key + ": " + (result.pass ? "PASS" : "FAIL") + (result.message.isBlank() ? "" : " - " + result.message));
		}

		lines.add("");
		lines.add("Captured legal URLs:");
		lines.add("Términos y Condiciones: " + legalUrls.getOrDefault("Términos y Condiciones", "N/A"));
		lines.add("Política de Privacidad: " + legalUrls.getOrDefault("Política de Privacidad", "N/A"));

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.write(reportPath, lines);
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean pass;
		private final String message;

		private StepResult(final boolean pass, final String message) {
			this.pass = pass;
			this.message = message;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String message) {
			return new StepResult(false, message == null ? "" : message);
		}
	}
}
