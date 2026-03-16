package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.NoSuchElementException;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchWindowException;
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

/**
 * End-to-end smoke test for the SaleADS "Mi Negocio" workflow.
 *
 * <p>This test is environment-agnostic: it does not hardcode any SaleADS URL.
 * Provide the start login URL through SALEADS_START_URL env var or
 * -Dsaleads.startUrl JVM property.</p>
 */
public class SaleAdsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Información General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "Términos y Condiciones";
	private static final String PRIVACIDAD = "Política de Privacidad";

	private static final List<String> REPORT_FIELDS = Arrays.asList(LOGIN, MI_NEGOCIO_MENU, AGREGAR_NEGOCIO_MODAL,
			ADMINISTRAR_NEGOCIOS_VIEW, INFORMACION_GENERAL, DETALLES_CUENTA, TUS_NEGOCIOS, TERMINOS, PRIVACIDAD);

	private final Map<String, Boolean> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepNotes = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final String startUrl = firstNonBlank(System.getProperty("saleads.startUrl"), System.getenv("SALEADS_START_URL"));
		Assume.assumeTrue(
				"Set SALEADS_START_URL (or -Dsaleads.startUrl) to the SaleADS login page for the target environment.",
				startUrl != null && !startUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1680,1080");
		if (Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		evidenceDir = Paths.get("target", "saleads-evidence", "run-" + LocalDateTime.now().format(TS_FORMAT));
		Files.createDirectories(evidenceDir);

		driver.navigate().to(startUrl.trim());
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep(LOGIN, this::loginWithGoogleAndValidateDashboard);
		runStep(MI_NEGOCIO_MENU, () -> {
			requireStep(LOGIN);
			openMiNegocioMenuAndValidate();
		});
		runStep(AGREGAR_NEGOCIO_MODAL, () -> {
			requireStep(MI_NEGOCIO_MENU);
			validateAgregarNegocioModal();
		});
		runStep(ADMINISTRAR_NEGOCIOS_VIEW, () -> {
			requireStep(MI_NEGOCIO_MENU);
			openAdministrarNegociosAndValidateSections();
		});
		runStep(INFORMACION_GENERAL, () -> {
			requireStep(ADMINISTRAR_NEGOCIOS_VIEW);
			validateInformacionGeneral();
		});
		runStep(DETALLES_CUENTA, () -> {
			requireStep(ADMINISTRAR_NEGOCIOS_VIEW);
			validateDetallesCuenta();
		});
		runStep(TUS_NEGOCIOS, () -> {
			requireStep(ADMINISTRAR_NEGOCIOS_VIEW);
			validateTusNegocios();
		});
		runStep(TERMINOS, () -> {
			requireStep(ADMINISTRAR_NEGOCIOS_VIEW);
			validateLegalPage("Términos y Condiciones", "Terminos y Condiciones", "08_terminos-y-condiciones.png");
		});
		runStep(PRIVACIDAD, () -> {
			requireStep(ADMINISTRAR_NEGOCIOS_VIEW);
			validateLegalPage("Política de Privacidad", "Politica de Privacidad", "09_politica-de-privacidad.png");
		});

		printFinalReport();

		Assert.assertTrue("One or more workflow validations failed: " + failingSteps(), failingSteps().isEmpty());
	}

	private void loginWithGoogleAndValidateDashboard() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		selectGoogleAccountIfVisible();

		assertAnyTextVisible("Negocio");
		assertAnyVisibleElement(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar')]"));
		captureScreenshot("01_dashboard-loaded.png");
	}

	private void openMiNegocioMenuAndValidate() throws Exception {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Administrar Negocios");
		captureScreenshot("02_mi-negocio-menu-expanded.png");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		assertAnyTextVisible("Crear Nuevo Negocio");
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio') or @name='businessName']"
						+ " | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]")));
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertButtonVisible("Cancelar");
		assertButtonVisible("Crear Negocio");
		captureScreenshot("03_agregar-negocio-modal.png");

		final WebElement nameInput = findFirstVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio') or @name='businessName']"
						+ " | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));
		nameInput.click();
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatización");
		waitForUiToLoad();

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(
				"//*[contains(normalize-space(.),'Crear Nuevo Negocio') and (self::h1 or self::h2 or self::h3 or self::div)]")));
	}

	private void openAdministrarNegociosAndValidateSections() throws Exception {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");

		assertAnyTextVisible("Información General", "Informacion General");
		assertAnyTextVisible("Detalles de la Cuenta");
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Sección Legal", "Seccion Legal");
		captureFullPageScreenshot("04_administrar-negocios-page.png");
	}

	private void validateInformacionGeneral() throws Exception {
		assertAnyTextVisible("Información General", "Informacion General");
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("User email should be visible in Información General.",
				bodyText.matches("(?s).*\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b.*"));
		findFirstVisible(By.xpath(
				"//*[contains(normalize-space(.),'@')]/preceding::*[self::h1 or self::h2 or self::h3 or self::p or self::span]"
						+ "[normalize-space(.)!='' and not(contains(normalize-space(.),'@'))][1]"));
		assertAnyTextVisible("BUSINESS PLAN");
		assertButtonVisible("Cambiar Plan");
	}

	private void validateDetallesCuenta() throws Exception {
		assertAnyTextVisible("Cuenta creada");
		assertAnyTextVisible("Estado activo");
		assertAnyTextVisible("Idioma seleccionado");
	}

	private void validateTusNegocios() throws Exception {
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");
	}

	private void validateLegalPage(final String title, final String fallbackTitle, final String screenshotName)
			throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		clickByVisibleText(title, fallbackTitle);

		final String legalHandle = switchToLegalPageHandle(handlesBeforeClick, appHandle);
		assertAnyTextVisible(title, fallbackTitle);

		final String legalBodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Expected legal content for " + title, legalBodyText != null && legalBodyText.trim().length() > 120);

		final String legalUrl = safeCurrentUrl();
		stepNotes.put(title, "URL: " + legalUrl);
		captureScreenshot(screenshotName);

		if (!appHandle.equals(legalHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private String switchToLegalPageHandle(final Set<String> handlesBeforeClick, final String appHandle) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until((ExpectedCondition<Boolean>) d -> d != null && d.getWindowHandles().size() > handlesBeforeClick.size());
		} catch (final TimeoutException ignored) {
			return appHandle;
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBeforeClick.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return handle;
			}
		}
		return appHandle;
	}

	private void selectGoogleAccountIfVisible() {
		final Set<String> originalHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String originalHandle = driver.getWindowHandle();

		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until((ExpectedCondition<Boolean>) d -> d != null && d.getWindowHandles().size() > originalHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!originalHandles.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			// Continue in the current tab if no popup window was opened.
		}

		try {
			final By accountSelector = By.xpath("//*[contains(normalize-space(.)," + asXpathLiteral(GOOGLE_ACCOUNT_EMAIL)
					+ ")] | //*[@data-email=" + asXpathLiteral(GOOGLE_ACCOUNT_EMAIL) + "]");
			new WebDriverWait(driver, Duration.ofSeconds(12))
					.until(ExpectedConditions.elementToBeClickable(accountSelector))
					.click();
			waitForUiToLoad();
		} catch (final TimeoutException ignored) {
			// Account chooser does not always appear (already authenticated session).
		}

		try {
			if (!originalHandle.equals(driver.getWindowHandle())) {
				driver.switchTo().window(originalHandle);
			}
		} catch (final NoSuchWindowException ignored) {
			// If Google flow closed original handle, Selenium will continue on active one.
		}
	}

	private void runStep(final String name, final StepRunnable runnable) {
		try {
			runnable.run();
			stepStatus.put(name, true);
		} catch (final Exception ex) {
			stepStatus.put(name, false);
			stepNotes.putIfAbsent(name, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
		}
	}

	private void requireStep(final String name) {
		if (!Boolean.TRUE.equals(stepStatus.get(name))) {
			throw new IllegalStateException("Blocked by failed prerequisite: " + name);
		}
	}

	private List<String> failingSteps() {
		final List<String> failures = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			if (!Boolean.TRUE.equals(stepStatus.get(field))) {
				failures.add(field);
			}
		}
		return failures;
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		for (final String field : REPORT_FIELDS) {
			final boolean pass = Boolean.TRUE.equals(stepStatus.get(field));
			final String note = stepNotes.getOrDefault(field, "");
			System.out.println(field + ": " + (pass ? "PASS" : "FAIL") + (note.isBlank() ? "" : " | " + note));
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private void clickByVisibleText(final String... visibleTexts) {
		final WebElement element = findByVisibleText(visibleTexts);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private WebElement findByVisibleText(final String... visibleTexts) {
		for (final String text : visibleTexts) {
			final String literal = asXpathLiteral(text);
			final By locator = By.xpath(
					"//button[contains(normalize-space(.)," + literal + ")]"
							+ " | //a[contains(normalize-space(.)," + literal + ")]"
							+ " | //*[@role='button' and contains(normalize-space(.)," + literal + ")]"
							+ " | //li[contains(normalize-space(.)," + literal + ")]"
							+ " | //*[contains(@class,'menu') and contains(normalize-space(.)," + literal + ")]"
							+ " | //*[contains(normalize-space(.)," + literal + ")]");

			final List<WebElement> candidates = driver.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					return candidate;
				}
			}
		}
		throw new NoSuchElementException("No visible element found using texts " + Arrays.toString(visibleTexts));
	}

	private void assertButtonVisible(final String buttonText) {
		final String literal = asXpathLiteral(buttonText);
		final By buttonBy = By.xpath("//button[contains(normalize-space(.)," + literal + ")]"
				+ " | //*[@role='button' and contains(normalize-space(.)," + literal + ")]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(buttonBy));
	}

	private void assertAnyVisibleElement(final By... selectors) {
		for (final By selector : selectors) {
			if (!driver.findElements(selector).isEmpty()) {
				for (final WebElement candidate : driver.findElements(selector)) {
					if (candidate.isDisplayed()) {
						return;
					}
				}
			}
		}
		throw new NoSuchElementException("None of the expected elements are visible");
	}

	private void assertAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			if (isAnyTextVisible(text)) {
				return;
			}
		}
		throw new NoSuchElementException("None of the expected texts are visible: " + Arrays.toString(texts));
	}

	private boolean isAnyTextVisible(final String text) {
		final String literal = asXpathLiteral(text);
		final By locator = By.xpath("//*[contains(normalize-space(.)," + literal + ")]");
		try {
			new WebDriverWait(driver, Duration.ofSeconds(6))
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private WebElement findFirstVisible(final By locator) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		for (final WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		throw new NoSuchElementException("Element not visible for locator: " + locator);
	}

	private void waitForUiToLoad() {
		try {
			wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Some SPA transitions never report "complete". Keep going with explicit assertions.
		}
	}

	private void captureScreenshot(final String filename) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), evidenceDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureFullPageScreenshot(final String filename) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final Number fullWidth = (Number) ((JavascriptExecutor) driver).executeScript(
					"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, window.innerWidth);");
			final Number fullHeight = (Number) ((JavascriptExecutor) driver).executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, window.innerHeight);");
			driver.manage().window().setSize(new Dimension(fullWidth.intValue(), fullHeight.intValue()));
			waitForUiToLoad();
			captureScreenshot(filename);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (final Exception ignored) {
			return "unavailable";
		}
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			if (chars[i] == '\'') {
				sb.append("\"'\"");
			} else if (chars[i] == '"') {
				sb.append("'\"'");
			} else {
				sb.append('\'').append(chars[i]).append('\'');
			}
		}
		sb.append(')');
		return sb.toString();
	}

	@FunctionalInterface
	private interface StepRunnable {
		void run() throws Exception;
	}
}
