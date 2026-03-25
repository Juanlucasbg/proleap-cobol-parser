package io.proleap.cobol.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SaleadsMiNegocioFullTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Informacion General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Terminos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Politica de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> errors = new ArrayList<>();

	@Before
	public void setUp() throws IOException {
		final boolean uiEnabled = Boolean.parseBoolean(property("saleads.ui.enabled", "false"));
		Assume.assumeTrue("Set -Dsaleads.ui.enabled=true to run this UI workflow.", uiEnabled);

		final String url = property("saleads.url", System.getenv("SALEADS_URL"));
		Assume.assumeTrue("Set -Dsaleads.url=<env login URL> or SALEADS_URL.", url != null && !url.isBlank());

		WebDriverManager.chromedriver().setup();

		final ChromeOptions chromeOptions = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(property("saleads.headless", "true"));
		if (headless) {
			chromeOptions.addArguments("--headless=new");
		}

		chromeOptions.addArguments("--window-size=1920,1080");
		chromeOptions.addArguments("--no-sandbox");
		chromeOptions.addArguments("--disable-dev-shm-usage");
		chromeOptions.addArguments("--lang=es");

		driver = new ChromeDriver(chromeOptions);
		wait = new WebDriverWait(driver, Duration.ofSeconds(Long.parseLong(property("saleads.timeout.seconds", "30"))));

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		screenshotDir = Paths.get("target", "saleads-screenshots", runId);
		Files.createDirectories(screenshotDir);

		driver.get(url);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		final String appWindowHandle = driver.getWindowHandle();

		executeReportStep(REPORT_LOGIN, this::stepLoginWithGoogleAndValidateMainUi);
		executeReportStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		executeReportStep(REPORT_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		executeReportStep(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		executeReportStep(REPORT_INFO_GENERAL, this::stepValidateInformacionGeneral);
		executeReportStep(REPORT_DETALLES_CUENTA, this::stepValidateDetallesDeLaCuenta);
		executeReportStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		executeReportStep(REPORT_TERMINOS,
				() -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08_terminos_condiciones",
						appWindowHandle));
		executeReportStep(REPORT_PRIVACIDAD,
				() -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09_politica_privacidad",
						appWindowHandle));

		printFinalReport();

		if (!errors.isEmpty()) {
			Assert.fail("Workflow failures:\n- " + String.join("\n- ", errors));
		}
	}

	private void stepLoginWithGoogleAndValidateMainUi() throws IOException {
		clickAnyByVisibleText(List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"));

		// If Google account chooser appears, select the requested account.
		try {
			clickIfVisible(By.xpath("//*[contains(normalize-space(), 'juanlucasbarbiergarzon@gmail.com')]"), 12);
			waitForUiToLoad();
		} catch (TimeoutException ignored) {
			// Some sessions bypass account selection due to an existing login.
		}

		ensureAnyVisible(List.of(By.cssSelector("aside"), By.cssSelector("nav"), By.xpath("//*[normalize-space()='Negocio']")),
				"Main application layout/sidebar not visible after login.");
		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		ensureVisibleText("Agregar Negocio");
		ensureVisibleText("Administrar Negocios");
		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");

		ensureVisibleText("Crear Nuevo Negocio");
		ensureAnyVisible(List.of(By.xpath("//label[normalize-space()='Nombre del Negocio']"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"), By.xpath("//input[contains(@placeholder,'Negocio')]"),
				By.xpath("//input[contains(@name,'negocio')]")), "Input field 'Nombre del Negocio' not found.");
		ensureVisibleText("Tienes 2 de 3 negocios");
		ensureVisibleText("Cancelar");
		ensureVisibleText("Crear Negocio");
		captureScreenshot("03_agregar_negocio_modal");

		// Optional action from workflow: fill input and cancel.
		final WebElement businessNameInput = firstVisible(List.of(By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@placeholder,'Negocio')]"), By.xpath("//input[contains(@name,'negocio')]")));
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");

		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		// Expand again in case the menu collapsed after modal interactions.
		clickByVisibleText("Mi Negocio");
		clickByVisibleText("Administrar Negocios");

		ensureVisibleText("Información General");
		ensureVisibleText("Detalles de la Cuenta");
		ensureVisibleText("Tus Negocios");
		ensureVisibleText("Sección Legal");
		captureScreenshot("04_administrar_negocios_view");
	}

	private void stepValidateInformacionGeneral() {
		ensureVisibleText("Información General");
		ensureVisibleText("BUSINESS PLAN");
		ensureVisibleText("Cambiar Plan");

		final boolean emailVisible = anyVisible(By.xpath("//*[contains(text(), '@')]"));
		Assert.assertTrue("User email is not visible in 'Información General'.", emailVisible);

		final boolean userNameVisible = hasLikelyUserName();
		Assert.assertTrue("User name is not clearly visible in 'Información General'.", userNameVisible);
	}

	private void stepValidateDetallesDeLaCuenta() {
		ensureVisibleText("Cuenta creada");
		ensureVisibleText("Estado activo");
		ensureVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		ensureVisibleText("Tus Negocios");
		ensureVisibleText("Agregar Negocio");
		ensureVisibleText("Tienes 2 de 3 negocios");

		final WebElement tusNegociosSection = sectionByHeading("Tus Negocios");
		final List<WebElement> listCandidates = tusNegociosSection.findElements(
				By.xpath(".//li | .//tr | .//*[contains(@class,'card')] | .//*[contains(@class,'item')] | .//table//tbody//tr"));
		Assert.assertTrue("Business list not visible in 'Tus Negocios'.", !listCandidates.isEmpty());
	}

	private void stepValidateLegalLink(final String linkText, final String headingText, final String screenshotName,
			final String appWindowHandle) throws IOException {
		final String currentUrl = driver.getCurrentUrl();
		final Set<String> initialHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);
		waitForUiToLoad();

		String activeHandle = driver.getWindowHandle();
		boolean openedNewTab = false;
		if (driver.getWindowHandles().size() > initialHandles.size()) {
			openedNewTab = true;
			for (final String handle : driver.getWindowHandles()) {
				if (!initialHandles.contains(handle)) {
					driver.switchTo().window(handle);
					activeHandle = handle;
					break;
				}
			}
			waitForUiToLoad();
		}

		ensureVisibleText(headingText);
		ensureAnyVisible(List.of(By.xpath("//main//*[self::p or self::article or self::section]"),
				By.xpath("//*[self::p or self::article][string-length(normalize-space()) > 40]")),
				"Legal content text is not visible for '" + headingText + "'.");
		captureScreenshot(screenshotName);
		System.out.println("[" + headingText + "] final URL: " + driver.getCurrentUrl());

		if (openedNewTab && !activeHandle.equals(appWindowHandle)) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
			return;
		}

		if (!driver.getCurrentUrl().equals(currentUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void executeReportStep(final String stepName, final ThrowingRunnable runnable) {
		try {
			runnable.run();
			report.put(stepName, true);
		} catch (final Throwable t) {
			report.put(stepName, false);
			errors.add(stepName + ": " + t.getMessage());
			safeCaptureFailureScreenshot(stepName);
		}
	}

	private void printFinalReport() {
		System.out.println("===== Final Report =====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
	}

	private void clickByVisibleText(final String text) {
		final WebElement element = firstVisible(List.of(By.xpath("//*[normalize-space()=" + toXPathLiteral(text)
				+ " and (self::button or self::a or @role='button' or self::span or self::div)]")));
		clickAndWait(element);
	}

	private void clickAnyByVisibleText(final List<String> texts) {
		TimeoutException lastTimeout = null;
		for (final String text : texts) {
			try {
				clickByVisibleText(text);
				return;
			} catch (final TimeoutException t) {
				lastTimeout = t;
			}
		}
		throw new AssertionError("None of the expected visible-text buttons were found/clickable: " + texts, lastTimeout);
	}

	private void clickIfVisible(final By by, final int timeoutSeconds) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		final WebElement element = shortWait.until(d -> {
			for (final WebElement candidate : d.findElements(by)) {
				if (candidate.isDisplayed()) {
					return candidate;
				}
			}
			return null;
		});
		clickAndWait(element);
	}

	private WebElement firstVisible(final List<By> locators) {
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

	private void ensureVisibleText(final String text) {
		final WebElement element = firstVisible(List.of(By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + "]")));
		Assert.assertTrue("Expected visible text not found: " + text, element.isDisplayed());
	}

	private void ensureAnyVisible(final List<By> locators, final String errorMessage) {
		Assert.assertTrue(errorMessage, anyVisible(locators));
	}

	private boolean anyVisible(final List<By> locators) {
		for (final By locator : locators) {
			if (anyVisible(locator)) {
				return true;
			}
		}
		return false;
	}

	private boolean anyVisible(final By by) {
		try {
			final WebElement element = firstVisible(List.of(by));
			return element != null && element.isDisplayed();
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private WebElement sectionByHeading(final String heading) {
		final WebElement headingElement = firstVisible(List.of(By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][normalize-space()="
				+ toXPathLiteral(heading) + "]")));
		final WebElement section = headingElement.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		Assert.assertNotNull("Section not found for heading: " + heading, section);
		return section;
	}

	private boolean hasLikelyUserName() {
		final WebElement infoSection = sectionByHeading("Información General");
		final List<WebElement> textNodes = infoSection
				.findElements(By.xpath(".//*[self::p or self::span or self::div or self::h1 or self::h2 or self::h3 or self::h4]"));

		for (final WebElement node : textNodes) {
			if (!node.isDisplayed()) {
				continue;
			}

			final String text = node.getText() == null ? "" : node.getText().trim();
			final String normalized = text.toLowerCase(Locale.ROOT);
			if (text.isEmpty()) {
				continue;
			}
			if (text.contains("@")) {
				continue;
			}
			if (normalized.contains("información general") || normalized.contains("business plan")
					|| normalized.contains("cambiar plan")) {
				continue;
			}
			if (text.length() >= 3 && text.length() <= 80) {
				return true;
			}
		}
		return false;
	}

	private void clickAndWait(final WebElement element) {
		try {
			element.click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));

		final long maxWaitMs = 10000L;
		final long pollMs = 250L;
		final long started = System.currentTimeMillis();
		while (System.currentTimeMillis() - started < maxWaitMs) {
			final List<WebElement> loaders = driver.findElements(
					By.cssSelector("[aria-busy='true'], .spinner, .loading, .ant-spin-spinning, [role='progressbar']"));
			boolean visibleLoader = false;
			for (final WebElement loader : loaders) {
				if (loader.isDisplayed()) {
					visibleLoader = true;
					break;
				}
			}
			if (!visibleLoader) {
				return;
			}
			try {
				Thread.sleep(pollMs);
			} catch (final InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private void captureScreenshot(final String checkpoint) throws IOException {
		final Path screenshot = screenshotDir.resolve(checkpoint + ".png");
		final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshot, bytes);
	}

	private void safeCaptureFailureScreenshot(final String stepName) {
		try {
			final String normalized = stepName.toLowerCase(Locale.ROOT).replace(" ", "_");
			captureScreenshot("failure_" + normalized);
		} catch (final Exception ignored) {
			// Ignore screenshot capture failures in cleanup path.
		}
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
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

	private String property(final String key, final String fallback) {
		final String value = System.getProperty(key);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		return value;
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
