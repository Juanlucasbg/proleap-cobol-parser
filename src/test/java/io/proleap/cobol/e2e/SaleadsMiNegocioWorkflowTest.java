package io.proleap.cobol.e2e;

import static org.junit.Assert.fail;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.proleap.cobol.CobolTestBase;

public class SaleadsMiNegocioWorkflowTest extends CobolTestBase {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String REPORT_POLITICA = "Pol\u00edtica de Privacidad";

	private static final String ENABLED_ENV = "SALEADS_E2E_ENABLED";
	private static final String START_URL_ENV = "SALEADS_START_URL";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String TIMEOUT_ENV = "SALEADS_TIMEOUT_SECONDS";
	private static final String GOOGLE_EMAIL_ENV = "SALEADS_GOOGLE_ACCOUNT_EMAIL";
	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> statusByField = new LinkedHashMap<>();
	private final Map<String, String> failureByField = new LinkedHashMap<>();
	private final Map<String, String> evidenceUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Path reportPath;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this E2E suite.",
				"true".equalsIgnoreCase(readEnv(ENABLED_ENV, "false")));

		final String startUrl = readEnv(START_URL_ENV, "");
		Assume.assumeTrue("Set SALEADS_START_URL to the SaleADS login page URL for your environment.",
				startUrl != null && !startUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,2200");
		options.addArguments("--disable-notifications");
		options.addArguments("--lang=es-ES");
		if ("true".equalsIgnoreCase(readEnv(HEADLESS_ENV, "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(readTimeoutSeconds()));

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Paths.get("target", "saleads-mi-negocio-e2e", timestamp);
		Files.createDirectories(evidenceDir);
		reportPath = evidenceDir.resolve("final-report.txt");

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
		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		runStep(REPORT_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(REPORT_TERMINOS, this::stepValidateTerminosYCondiciones);
		runStep(REPORT_POLITICA, this::stepValidatePoliticaPrivacidad);

		writeFinalReport();
		failIfAnyStepFailed();
	}

	private void stepLoginWithGoogle() throws IOException {
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByAnyVisibleText("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Ingresar con Google",
				"Continuar con Google", "Google");
		switchToNewWindowIfPresent(handlesBeforeClick);
		trySelectGoogleAccount(readEnv(GOOGLE_EMAIL_ENV, DEFAULT_GOOGLE_EMAIL));

		waitForUiToLoad();
		waitForVisibleTextAny("Negocio");
		ensureElementVisible(By.xpath("//aside | //nav"), "Left sidebar navigation is not visible after login.");
		captureScreenshot("01_dashboard_loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickIfVisible("Negocio");
		clickByAnyVisibleText("Mi Negocio");

		waitForUiToLoad();
		waitForVisibleTextAny("Agregar Negocio");
		waitForVisibleTextAny("Administrar Negocios");
		captureScreenshot("02_mi_negocio_expanded_menu.png");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByAnyVisibleText("Agregar Negocio");
		waitForVisibleTextAny("Crear Nuevo Negocio");
		ensureElementVisible(By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"
						+ " | //label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				"Input field 'Nombre del Negocio' is missing.");
		waitForVisibleTextAny("Tienes 2 de 3 negocios");
		waitForVisibleTextAny("Cancelar");
		waitForVisibleTextAny("Crear Negocio");
		captureScreenshot("03_crear_nuevo_negocio_modal.png");

		final WebElement nameInput = firstVisibleElement(By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"
						+ " | //label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
		nameInput.click();
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatizacion");
		clickByAnyVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.), "
				+ xPathLiteral("Crear Nuevo Negocio") + ")]")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickByAnyVisibleText("Mi Negocio");
		}

		clickByAnyVisibleText("Administrar Negocios");
		waitForUiToLoad();

		waitForVisibleTextAny("Informaci\u00f3n General", "Informacion General");
		waitForVisibleTextAny("Detalles de la Cuenta");
		waitForVisibleTextAny("Tus Negocios");
		waitForVisibleTextAny("Secci\u00f3n Legal", "Seccion Legal");
		captureScreenshot("04_administrar_negocios_account_page.png");
	}

	private void stepValidateInformacionGeneral() {
		waitForVisibleTextAny("BUSINESS PLAN");
		waitForVisibleTextAny("Cambiar Plan");
		ensureAnyEmailVisible("User email is not visible in account page.");

		final WebElement section = sectionContainerByHeading("Informaci\u00f3n General", "Informacion General");
		final String normalized = section.getText().replace("\r", "");
		final List<String> candidates = Arrays.stream(normalized.split("\n")).map(String::trim).filter(line -> !line.isEmpty())
				.filter(line -> !line.toLowerCase().contains("informacion general"))
				.filter(line -> !line.toLowerCase().contains("informaci\u00f3n general"))
				.filter(line -> !line.toLowerCase().contains("business plan"))
				.filter(line -> !line.toLowerCase().contains("cambiar plan"))
				.filter(line -> !EMAIL_PATTERN.matcher(line).find()).collect(Collectors.toList());

		ensureTrue(!candidates.isEmpty(), "Unable to confirm that a user name is visible in Informacion General.");
	}

	private void stepValidateDetallesCuenta() {
		waitForVisibleTextAny("Cuenta creada");
		waitForVisibleTextAny("Estado activo");
		waitForVisibleTextAny("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitForVisibleTextAny("Tus Negocios");
		waitForVisibleTextAny("Agregar Negocio");
		waitForVisibleTextAny("Tienes 2 de 3 negocios");

		final WebElement section = sectionContainerByHeading("Tus Negocios");
		final List<WebElement> structuredRows = section.findElements(By.xpath(
				".//ul/li[normalize-space()] | .//table/tbody/tr[normalize-space()] | .//div[contains(@class, 'business') or contains(@class, 'negocio')]"));
		if (!structuredRows.isEmpty()) {
			return;
		}

		final String sectionText = section.getText().replace("\r", "");
		final List<String> contentLines = Arrays.stream(sectionText.split("\n")).map(String::trim).filter(line -> !line.isEmpty())
				.filter(line -> !line.equalsIgnoreCase("Tus Negocios")).filter(line -> !line.equalsIgnoreCase("Agregar Negocio"))
				.filter(line -> !line.equalsIgnoreCase("Tienes 2 de 3 negocios")).collect(Collectors.toList());
		ensureTrue(!contentLines.isEmpty(), "Business list is not visible in Tus Negocios.");
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		validateLegalLink("T\u00e9rminos y Condiciones",
				new String[] { "T\u00e9rminos y Condiciones", "Terminos y Condiciones" },
				new String[] { "T\u00e9rminos y Condiciones", "Terminos y Condiciones" }, "08_terminos_y_condiciones.png",
				"terminos_url");
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		validateLegalLink("Pol\u00edtica de Privacidad", new String[] { "Pol\u00edtica de Privacidad", "Politica de Privacidad" },
				new String[] { "Pol\u00edtica de Privacidad", "Politica de Privacidad" }, "09_politica_de_privacidad.png",
				"politica_url");
	}

	private void validateLegalLink(final String title, final String[] linkTexts, final String[] headingTexts,
			final String screenshotFileName, final String urlKey) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final String preClickUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByAnyVisibleText(linkTexts);
		waitForUiToLoad();

		final String activeWindow = waitForNavigationOrNewTab(handlesBeforeClick, preClickUrl);
		driver.switchTo().window(activeWindow);
		waitForUiToLoad();

		waitForVisibleTextAny(headingTexts);
		final String legalText = driver.findElement(By.tagName("body")).getText();
		ensureTrue(legalText != null && legalText.trim().length() > 120,
				"Legal content text is not visible for " + title + ".");
		captureScreenshot(screenshotFileName);
		evidenceUrls.put(urlKey, driver.getCurrentUrl());

		if (!activeWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		waitForVisibleTextAny("Tus Negocios", "Secci\u00f3n Legal", "Seccion Legal");
	}

	private void runStep(final String reportField, final CheckedRunnable runnable) {
		try {
			runnable.run();
			statusByField.put(reportField, Boolean.TRUE);
		} catch (final Throwable throwable) {
			statusByField.put(reportField, Boolean.FALSE);
			final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
			failureByField.put(reportField, message);
		}
	}

	private void writeFinalReport() throws IOException {
		final List<String> order = Arrays.asList(REPORT_LOGIN, REPORT_MI_NEGOCIO_MENU, REPORT_AGREGAR_NEGOCIO_MODAL,
				REPORT_ADMINISTRAR_NEGOCIOS_VIEW, REPORT_INFORMACION_GENERAL, REPORT_DETALLES_CUENTA, REPORT_TUS_NEGOCIOS,
				REPORT_TERMINOS, REPORT_POLITICA);

		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Workflow Report\n");
		builder.append("Generated: ").append(LocalDateTime.now()).append('\n');
		builder.append("Artifacts: ").append(evidenceDir.toAbsolutePath()).append('\n');
		builder.append('\n');
		builder.append("Final PASS/FAIL per step:\n");

		for (final String field : order) {
			final boolean passed = Boolean.TRUE.equals(statusByField.get(field));
			builder.append("- ").append(field).append(": ").append(passed ? "PASS" : "FAIL").append('\n');
			if (!passed && failureByField.containsKey(field)) {
				builder.append("  Detail: ").append(failureByField.get(field)).append('\n');
			}
		}

		builder.append('\n');
		builder.append("Captured URLs:\n");
		builder.append("- T\u00e9rminos y Condiciones: ").append(evidenceUrls.getOrDefault("terminos_url", "N/A")).append('\n');
		builder.append("- Pol\u00edtica de Privacidad: ").append(evidenceUrls.getOrDefault("politica_url", "N/A")).append('\n');

		Files.writeString(reportPath, builder.toString());
		System.out.println(builder);
	}

	private void failIfAnyStepFailed() {
		final List<String> failures = statusByField.entrySet().stream().filter(entry -> !Boolean.TRUE.equals(entry.getValue()))
				.map(Map.Entry::getKey).collect(Collectors.toList());

		if (!failures.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failed on: " + failures + ". See " + reportPath.toAbsolutePath());
		}
	}

	private void clickIfVisible(final String text) {
		try {
			final WebElement element = findVisibleElementByText(text, Duration.ofSeconds(3));
			if (element != null) {
				clickAndWait(element);
			}
		} catch (final Throwable ignored) {
			// Intentionally best-effort for optional menu expansion.
		}
	}

	private void clickByAnyVisibleText(final String... texts) {
		WebElement element = null;
		for (final String text : texts) {
			element = findVisibleElementByText(text, Duration.ofSeconds(8));
			if (element != null) {
				break;
			}
		}

		ensureTrue(element != null, "Unable to find a clickable element with visible text: " + Arrays.toString(texts));
		clickAndWait(element);
	}

	private WebElement findVisibleElementByText(final String text, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		final List<String> xpaths = Arrays.asList("//button[contains(normalize-space(.), " + xPathLiteral(text) + ")]",
				"//a[contains(normalize-space(.), " + xPathLiteral(text) + ")]",
				"//*[@role='button' and contains(normalize-space(.), " + xPathLiteral(text) + ")]",
				"//*[contains(normalize-space(.), " + xPathLiteral(text) + ")]");

		for (final String xpath : xpaths) {
			try {
				final WebElement element = shortWait.until(d -> {
					for (final WebElement candidate : d.findElements(By.xpath(xpath))) {
						if (candidate.isDisplayed()) {
							return candidate;
						}
					}
					return null;
				});
				if (element != null) {
					return element;
				}
			} catch (final Throwable ignored) {
				// Continue trying the next selector.
			}
		}
		return null;
	}

	private void clickAndWait(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Throwable clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForVisibleTextAny(final String... texts) {
		WebElement found = null;
		Throwable lastError = null;

		for (final String text : texts) {
			try {
				final By locator = By.xpath("//*[contains(normalize-space(.), " + xPathLiteral(text) + ")]");
				found = wait.until(d -> {
					for (final WebElement element : d.findElements(locator)) {
						if (element.isDisplayed()) {
							return element;
						}
					}
					return null;
				});
				if (found != null) {
					return;
				}
			} catch (final Throwable throwable) {
				lastError = throwable;
			}
		}

		throw new AssertionError("None of the expected texts are visible: " + Arrays.toString(texts), lastError);
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			final By locator = By.xpath("//*[contains(normalize-space(.), " + xPathLiteral(text) + ")]");
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void switchToNewWindowIfPresent(final Set<String> previousHandles) {
		final Set<String> currentHandles = driver.getWindowHandles();
		if (currentHandles.size() <= previousHandles.size()) {
			return;
		}

		for (final String handle : currentHandles) {
			if (!previousHandles.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void trySelectGoogleAccount(final String accountEmail) {
		try {
			final WebElement accountOption = findVisibleElementByText(accountEmail, Duration.ofSeconds(8));
			if (accountOption != null) {
				clickAndWait(accountOption);
			}
		} catch (final Throwable ignored) {
			// If account chooser is not shown, continue with the current flow.
		}
	}

	private String waitForNavigationOrNewTab(final Set<String> oldHandles, final String oldUrl) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(25));
		return shortWait.until(d -> {
			final Set<String> currentHandles = d.getWindowHandles();
			if (currentHandles.size() > oldHandles.size()) {
				for (final String handle : currentHandles) {
					if (!oldHandles.contains(handle)) {
						return handle;
					}
				}
			}

			if (!d.getCurrentUrl().equals(oldUrl)) {
				return d.getWindowHandle();
			}

			return null;
		});
	}

	private void ensureElementVisible(final By locator, final String message) {
		final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		ensureTrue(element != null, message);
	}

	private WebElement firstVisibleElement(final By locator) {
		return wait.until(d -> {
			for (final WebElement element : d.findElements(locator)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private WebElement sectionContainerByHeading(final String... headings) {
		for (final String heading : headings) {
			try {
				final By headingLocator = By
						.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::div or self::span]"
								+ "[contains(normalize-space(.), " + xPathLiteral(heading) + ")]");
				final WebElement headingElement = wait.until(ExpectedConditions.visibilityOfElementLocated(headingLocator));
				return headingElement.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
			} catch (final Throwable ignored) {
				// Continue trying alternative heading text.
			}
		}

		throw new AssertionError("Unable to find section container for headings: " + Arrays.toString(headings));
	}

	private void ensureAnyEmailVisible(final String failureMessage) {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final Matcher matcher = EMAIL_PATTERN.matcher(bodyText);
		ensureTrue(matcher.find(), failureMessage);
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(700L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), evidenceDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private String xPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final List<String> pieces = new ArrayList<>();
		for (final String piece : value.split("'")) {
			if (!piece.isEmpty()) {
				pieces.add("'" + piece + "'");
			}
			pieces.add("\"'\"");
		}
		if (!value.endsWith("'")) {
			pieces.remove(pieces.size() - 1);
		}
		return "concat(" + String.join(", ", pieces) + ")";
	}

	private int readTimeoutSeconds() {
		final String env = readEnv(TIMEOUT_ENV, "30");
		try {
			return Integer.parseInt(env);
		} catch (final NumberFormatException numberFormatException) {
			return 30;
		}
	}

	private String readEnv(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value.trim();
	}

	private void ensureTrue(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
