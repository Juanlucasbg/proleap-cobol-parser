package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchWindowException;
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

	private static final String ENABLED_PROPERTY = "saleads.e2e.enabled";
	private static final String LOGIN_URL_PROPERTY = "saleads.login.url";
	private static final String HEADLESS_PROPERTY = "saleads.headless";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informacion General";
	private static final String STEP_DETALLES = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Terminos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Politica de Privacidad";

	private final Map<String, StepResult> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private String appWindowHandle;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final boolean enabled = getBoolean(ENABLED_PROPERTY, "SALEADS_E2E_ENABLED", false);
		Assume.assumeTrue("Set -D" + ENABLED_PROPERTY + "=true (or SALEADS_E2E_ENABLED=true) to run this E2E test.",
				enabled);

		final ChromeOptions options = new ChromeOptions();
		if (getBoolean(HEADLESS_PROPERTY, "SALEADS_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-gpu");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		evidenceDirectory = Paths.get("target", "saleads-e2e", "screenshots");
		Files.createDirectories(evidenceDirectory);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		initializeReport();

		final String loginUrl = firstNonBlank(System.getProperty(LOGIN_URL_PROPERTY), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"Missing SaleADS login URL. Set -D" + LOGIN_URL_PROPERTY + "=... (or SALEADS_LOGIN_URL=...) to run.",
				loginUrl != null);

		driver.get(loginUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();

		executeStep(STEP_LOGIN, this::stepLoginWithGoogle);
		executeStep(STEP_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		executeStep(STEP_AGREGAR_MODAL, this::stepValidateAgregarNegocioModal);
		executeStep(STEP_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		executeStep(STEP_INFO_GENERAL, this::stepValidateInformacionGeneral);
		executeStep(STEP_DETALLES, this::stepValidateDetallesCuenta);
		executeStep(STEP_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		executeStep(STEP_TERMINOS, this::stepValidateTerminosYCondiciones);
		executeStep(STEP_PRIVACIDAD, this::stepValidatePoliticaPrivacidad);

		writeFinalReport();
		assertAllStepsPassed();
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleTextCandidates(Arrays.asList("Sign in with Google", "Iniciar sesion con Google",
				"Iniciar sesion con google", "Entrar con Google", "Continuar con Google", "Google"));
		selectGoogleAccountIfPrompted("juanlucasbarbiergarzon@gmail.com");

		assertTextVisible("Negocio");
		assertLeftNavigationVisible();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		ensureMenuExpanded();
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleTextCandidates(Arrays.asList("Agregar Negocio"));
		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		typeIntoField("Nombre del Negocio", "Negocio Prueba Automatizacion");
		clickByVisibleTextCandidates(Arrays.asList("Cancelar"));
		waitForUiToLoad();
		captureScreenshot("03-agregar-negocio-modal");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMenuExpanded();
		clickByVisibleTextCandidates(Arrays.asList("Administrar Negocios"));
		waitForUiToLoad();

		assertTextVisible("Informacion General", "Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Seccion Legal", "Sección Legal");
		captureScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("@");
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		termsUrl = openLegalDocumentAndValidate(Arrays.asList("Terminos y Condiciones", "Términos y Condiciones"),
				Arrays.asList("Terminos y Condiciones", "Términos y Condiciones"), "08-terminos-condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		privacyUrl = openLegalDocumentAndValidate(Arrays.asList("Politica de Privacidad", "Política de Privacidad"),
				Arrays.asList("Politica de Privacidad", "Política de Privacidad"), "09-politica-privacidad");
	}

	private String openLegalDocumentAndValidate(final List<String> linkTexts, final List<String> expectedHeadings,
			final String screenshotName) throws Exception {
		final Set<String> windowsBeforeClick = driver.getWindowHandles();
		clickByVisibleTextCandidates(linkTexts);
		waitForUiToLoad();

		final boolean openedNewTab = waitForNewTab(windowsBeforeClick.size(), Duration.ofSeconds(8));
		String activeWindow = driver.getWindowHandle();

		if (openedNewTab) {
			activeWindow = switchToNewestWindow(windowsBeforeClick);
		}

		assertAnyTextVisible(expectedHeadings);
		assertLegalBodyVisible();
		final String finalUrl = driver.getCurrentUrl();
		captureScreenshot(screenshotName);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		return finalUrl;
	}

	private void initializeReport() {
		report.clear();

		report.put(STEP_LOGIN, StepResult.pending());
		report.put(STEP_MI_NEGOCIO_MENU, StepResult.pending());
		report.put(STEP_AGREGAR_MODAL, StepResult.pending());
		report.put(STEP_ADMIN_VIEW, StepResult.pending());
		report.put(STEP_INFO_GENERAL, StepResult.pending());
		report.put(STEP_DETALLES, StepResult.pending());
		report.put(STEP_TUS_NEGOCIOS, StepResult.pending());
		report.put(STEP_TERMINOS, StepResult.pending());
		report.put(STEP_PRIVACIDAD, StepResult.pending());
	}

	private void executeStep(final String stepName, final CheckedRunnable action) {
		try {
			action.run();
			report.put(stepName, StepResult.passed());
		} catch (final Throwable error) {
			report.put(stepName, StepResult.failed(error.getMessage()));
		}
	}

	private void assertAllStepsPassed() {
		final StringBuilder failures = new StringBuilder();

		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				failures.append("\n- ").append(entry.getKey()).append(": ").append(entry.getValue().details);
			}
		}

		assertTrue("One or more validation steps failed:" + failures, failures.length() == 0);
	}

	private void writeFinalReport() throws IOException {
		final Path reportDirectory = Paths.get("target", "saleads-e2e");
		Files.createDirectories(reportDirectory);

		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Full Test Report");
		lines.add("");

		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			lines.add(entry.getKey() + ": " + (entry.getValue().passed ? "PASS" : "FAIL"));
			if (entry.getValue().details != null && !entry.getValue().details.isBlank()) {
				lines.add("  Details: " + entry.getValue().details);
			}
		}

		lines.add("");
		lines.add("Terminos y Condiciones URL: " + termsUrl);
		lines.add("Politica de Privacidad URL: " + privacyUrl);
		lines.add("Screenshots directory: " + evidenceDirectory.toAbsolutePath());

		Files.write(reportDirectory.resolve("final-report.txt"), lines, StandardCharsets.UTF_8);
	}

	private void selectGoogleAccountIfPrompted(final String email) {
		final Duration timeout = Duration.ofSeconds(12);
		final long end = System.currentTimeMillis() + timeout.toMillis();

		while (System.currentTimeMillis() < end) {
			for (final String handle : driver.getWindowHandles()) {
				try {
					driver.switchTo().window(handle);
					final List<WebElement> accountEntries = driver.findElements(By.xpath(
							"//*[self::div or self::span or self::p or self::button][contains(normalize-space(), "
									+ toXpathLiteral(email) + ")]"));

					for (final WebElement entry : accountEntries) {
						if (entry.isDisplayed()) {
							clickAndWait(entry);
							driver.switchTo().window(appWindowHandle);
							return;
						}
					}
				} catch (final NoSuchWindowException ignored) {
					// The popup can close itself after account selection.
				}
			}

			sleep(300);
		}

		driver.switchTo().window(appWindowHandle);
	}

	private void ensureMenuExpanded() {
		if (isVisibleByText("Administrar Negocios") && isVisibleByText("Agregar Negocio")) {
			return;
		}

		clickByVisibleTextCandidates(Arrays.asList("Negocio", "Mi Negocio"));
		waitForUiToLoad();

		if (!isVisibleByText("Administrar Negocios")) {
			clickByVisibleTextCandidates(Arrays.asList("Mi Negocio"));
			waitForUiToLoad();
		}
	}

	private void typeIntoField(final String fieldLabel, final String text) {
		final String locator = "(//label[contains(normalize-space(), " + toXpathLiteral(fieldLabel)
				+ ")]/following::input[1]) | (//input[@placeholder=" + toXpathLiteral(fieldLabel) + "])";

		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(locator)));
		input.click();
		input.clear();
		input.sendKeys(text);
		waitForUiToLoad();
	}

	private void assertLeftNavigationVisible() {
		final List<WebElement> navCandidates = driver.findElements(By.cssSelector("aside, nav"));

		for (final WebElement nav : navCandidates) {
			if (nav.isDisplayed()) {
				return;
			}
		}

		throw new AssertionError("Left sidebar navigation is not visible.");
	}

	private void assertAnyTextVisible(final List<String> textOptions) {
		for (final String option : textOptions) {
			if (isVisibleByText(option)) {
				return;
			}
		}

		throw new AssertionError("Expected one of these texts to be visible: " + textOptions);
	}

	private void assertTextVisible(final String... textOptions) {
		assertAnyTextVisible(Arrays.asList(textOptions));
	}

	private void assertLegalBodyVisible() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p[string-length(normalize-space()) > 30]"));

		for (final WebElement paragraph : paragraphs) {
			if (paragraph.isDisplayed()) {
				return;
			}
		}

		throw new AssertionError("Legal content text is not visible.");
	}

	private boolean isVisibleByText(final String text) {
		final String literal = toXpathLiteral(text);
		final List<WebElement> candidates = driver
				.findElements(By.xpath("//*[contains(normalize-space(), " + literal + ")]"));

		for (final WebElement candidate : candidates) {
			try {
				if (candidate.isDisplayed()) {
					return true;
				}
			} catch (final Exception ignored) {
				// Ignore stale elements while polling for visibility.
			}
		}

		return false;
	}

	private void clickByVisibleTextCandidates(final List<String> candidateTexts) {
		final WebElement target = wait.until(driver -> {
			for (final String text : candidateTexts) {
				final WebElement clickable = firstVisibleClickableByText(text);
				if (clickable != null) {
					return clickable;
				}
			}

			return null;
		});

		if (target == null) {
			throw new NoSuchElementException("Unable to locate clickable element using text options: " + candidateTexts);
		}

		clickAndWait(target);
	}

	private WebElement firstVisibleClickableByText(final String text) {
		final String exact = toXpathLiteral(text);
		final List<String> xpaths = Arrays.asList(
				"(//button[normalize-space()=" + exact + "] | //a[normalize-space()=" + exact
						+ "] | //*[@role='button' and normalize-space()=" + exact + "] | //li[normalize-space()=" + exact
						+ "] | //span[normalize-space()=" + exact + "])",
				"(//button[contains(normalize-space(), " + exact + ")] | //a[contains(normalize-space(), " + exact
						+ ")] | //*[@role='button' and contains(normalize-space(), " + exact
						+ ")] | //li[contains(normalize-space(), " + exact + ")] | //span[contains(normalize-space(), "
						+ exact + ")])");

		for (final String xpath : xpaths) {
			final List<WebElement> elements = driver.findElements(By.xpath(xpath));
			for (final WebElement element : elements) {
				try {
					if (element.isDisplayed() && element.isEnabled()) {
						return element;
					}
				} catch (final Exception ignored) {
					// Ignore stale references while searching.
				}
			}
		}

		return null;
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		element.click();
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		sleep(400);
	}

	private void captureScreenshot(final String name) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDirectory.resolve(name + ".png");
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private boolean waitForNewTab(final int previousWindowCount, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(driver -> driver.getWindowHandles().size() > previousWindowCount);
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private String switchToNewestWindow(final Set<String> windowsBeforeClick) {
		for (final String window : driver.getWindowHandles()) {
			if (!windowsBeforeClick.contains(window)) {
				driver.switchTo().window(window);
				return window;
			}
		}

		return driver.getWindowHandle();
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}

		return null;
	}

	private boolean getBoolean(final String systemProperty, final String environmentVariable, final boolean defaultValue) {
		return parseBoolean(() -> System.getProperty(systemProperty), () -> System.getenv(environmentVariable), defaultValue);
	}

	private boolean parseBoolean(final Supplier<String> systemValueSupplier, final Supplier<String> envValueSupplier,
			final boolean defaultValue) {
		final String propertyValue = systemValueSupplier.get();
		if (propertyValue != null) {
			return Boolean.parseBoolean(propertyValue);
		}

		final String envValue = envValueSupplier.get();
		if (envValue != null) {
			return Boolean.parseBoolean(envValue);
		}

		return defaultValue;
	}

	private String toXpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				builder.append(", ");
			}

			if (chars[i] == '\'') {
				builder.append("\"'\"");
			} else if (chars[i] == '"') {
				builder.append("'\"'");
			} else {
				builder.append("'").append(chars[i]).append("'");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private interface CheckedRunnable {

		void run() throws Exception;
	}

	private static final class StepResult {

		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pending() {
			return new StepResult(false, "Not executed");
		}

		private static StepResult passed() {
			return new StepResult(true, null);
		}

		private static StepResult failed(final String details) {
			return new StepResult(false, details == null ? "No details available" : details);
		}
	}
}
