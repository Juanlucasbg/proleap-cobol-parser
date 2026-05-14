package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Opt-in SaleADS E2E test for validating the "Mi Negocio" workflow.
 *
 * <p>Execution example:
 *
 * <pre>
 * mvn -Dtest=SaleadsMiNegocioWorkflowTest \
 *   -Dsaleads.e2e.enabled=true \
 *   -Dsaleads.login.url=https://your-environment/login \
 *   -Dsaleads.e2e.headless=true \
 *   test
 * </pre>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final Duration QUICK_WAIT = Duration.ofSeconds(8);
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);

	private final AtomicInteger screenshotIndex = new AtomicInteger(1);
	private final Map<String, String> report = new LinkedHashMap<>();
	private final List<String> errors = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String termsUrl = "";
	private String privacyUrl = "";
	private String appWindowHandle = "";

	@Before
	public void setUp() throws IOException {
		final boolean enabled = readBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false);
		Assume.assumeTrue(
				"SaleADS E2E test is opt-in. Enable with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true.",
				enabled);

		final String screenshotRoot = readConfig("saleads.e2e.screenshots.dir", "SALEADS_E2E_SCREENSHOTS_DIR",
				"target/saleads-e2e-screenshots");
		screenshotDir = Paths.get(screenshotRoot, TS_FORMAT.format(Instant.now()));
		Files.createDirectories(screenshotDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1200");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		final boolean headless = readBooleanConfig("saleads.e2e.headless", "SALEADS_E2E_HEADLESS", true);
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void validateMiNegocioFullWorkflow() {
		initializeReport();

		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", "");
		Assume.assumeTrue(
				"Set SaleADS login URL with -Dsaleads.login.url=<url> or SALEADS_LOGIN_URL. "
						+ "No URL is hardcoded to keep this test environment-agnostic.",
				!loginUrl.isBlank());

		driver.get(loginUrl);
		waitForUiToSettle();
		appWindowHandle = driver.getWindowHandle();

		final boolean loginOk = runStep("Login", this::stepLoginWithGoogleAndValidateDashboard);
		final boolean menuOk = loginOk && runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		final boolean modalOk = menuOk && runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		final boolean adminOk = modalOk && runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		final boolean infoOk = adminOk && runStep("Información General", this::stepValidateInformacionGeneral);
		final boolean detailsOk = infoOk && runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		final boolean businessesOk = detailsOk && runStep("Tus Negocios", this::stepValidateTusNegocios);
		final boolean termsOk = businessesOk
				&& runStep("Términos y Condiciones",
						() -> termsUrl = stepOpenAndValidateLegalLink("Términos y Condiciones",
								"Términos y Condiciones", "08-terminos-y-condiciones"));
		final boolean privacyOk = termsOk
				&& runStep("Política de Privacidad",
						() -> privacyUrl = stepOpenAndValidateLegalLink("Política de Privacidad",
								"Política de Privacidad", "09-politica-de-privacidad"));

		markSkippedIfNotRun("Mi Negocio menu", loginOk);
		markSkippedIfNotRun("Agregar Negocio modal", menuOk);
		markSkippedIfNotRun("Administrar Negocios view", modalOk);
		markSkippedIfNotRun("Información General", adminOk);
		markSkippedIfNotRun("Detalles de la Cuenta", infoOk);
		markSkippedIfNotRun("Tus Negocios", detailsOk);
		markSkippedIfNotRun("Términos y Condiciones", businessesOk);
		markSkippedIfNotRun("Política de Privacidad", termsOk);

		printFinalReport();
		assertFalse("One or more workflow steps failed. See final report above.", hasFailure());
		assertFalse("Workflow had validation errors: " + String.join(" | ", errors), !errors.isEmpty());
	}

	private void stepLoginWithGoogleAndValidateDashboard() {
		clickGoogleLoginButton();
		trySelectGoogleAccount();
		waitForAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Panel"), DEFAULT_WAIT);
		assertVisibleAnyText(Arrays.asList("Negocio", "Mi Negocio"), "Left sidebar navigation is not visible.");
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		clickVisibleText("Negocio");
		waitForUiToSettle();
		clickVisibleText("Mi Negocio");
		waitForUiToSettle();
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickVisibleText("Agregar Negocio");
		waitForUiToSettle();

		assertVisibleText("Crear Nuevo Negocio");
		assertElementExists(
				By.xpath("//*[contains(normalize-space(.), 'Nombre del Negocio')]"
						+ " | //input[@placeholder='Nombre del Negocio']"
						+ " | //input[contains(@aria-label, 'Nombre del Negocio')]"),
				"No se encontró el campo 'Nombre del Negocio'.");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//input[@placeholder='Nombre del Negocio']"
						+ " | //input[contains(@aria-label, 'Nombre del Negocio')]"
						+ " | //input[ancestor::*[contains(normalize-space(.), 'Nombre del Negocio')]]")));
		input.clear();
		input.sendKeys("Negocio Prueba Automatización");
		clickVisibleText("Cancelar");
		waitForUiToSettle();
		wait.until(ExpectedConditions.invisibilityOfElementLocated(containsVisibleText("Crear Nuevo Negocio")));
	}

	private void stepOpenAdministrarNegocios() {
		ensureMiNegocioExpanded();
		clickVisibleText("Administrar Negocios");
		waitForUiToSettle();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleAnyText(Arrays.asList("Sección Legal", "Legal"), "No se encontró la sección legal.");
		captureScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertElementExists(By.xpath("//*[contains(@class, 'name') and string-length(normalize-space(.)) > 0]"
				+ " | //*[contains(@class, 'user') and string-length(normalize-space(.)) > 0]"
				+ " | //h1[string-length(normalize-space(.)) > 0]"), "No se encontró nombre visible de usuario.");
		assertElementExists(By.xpath("//*[contains(normalize-space(.), '@')]"), "No se encontró email visible de usuario.");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertElementExists(By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]/following::*[1]"
				+ " | //*[contains(@class, 'business')]"), "No se encontró lista de negocios.");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private String stepOpenAndValidateLegalLink(final String linkText, final String heading, final String screenshotName) {
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String currentHandle = driver.getWindowHandle();
		final String currentUrl = driver.getCurrentUrl();

		clickVisibleText(linkText);
		waitForUiToSettle();

		try {
			wait.until(d -> d != null && (d.getWindowHandles().size() > handlesBefore.size()
					|| !currentUrl.equals(d.getCurrentUrl())));
		} catch (TimeoutException ignored) {
			// Continue; some environments may render legal content without URL change.
		}

		final boolean openedNewTab = driver.getWindowHandles().size() > handlesBefore.size();

		if (openedNewTab) {
			final Set<String> handlesAfter = driver.getWindowHandles();
			for (String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		waitForUiToSettle();
		wait.until(ExpectedConditions.visibilityOfElementLocated(headingWithText(heading)));
		assertElementExists(By.xpath("//main//*[string-length(normalize-space(.)) > 80]"
				+ " | //article//*[string-length(normalize-space(.)) > 80]"
				+ " | //p[string-length(normalize-space(.)) > 50]"),
				"No se detectó contenido legal visible.");
		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(currentHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiToSettle();
		driver.switchTo().window(appWindowHandle);
		waitForUiToSettle();
		return finalUrl;
	}

	private void clickGoogleLoginButton() {
		final List<By> loginSelectors = Arrays.asList(
				containsVisibleText("Sign in with Google"),
				containsVisibleText("Iniciar sesión con Google"),
				containsVisibleText("Continuar con Google"),
				containsVisibleText("Login with Google"),
				By.xpath("//button[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
						+ " 'abcdefghijklmnopqrstuvwxyz'), 'google')]"
						+ " | //a[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
						+ " 'abcdefghijklmnopqrstuvwxyz'), 'google')]"));
		final WebElement loginButton = findFirstVisible(loginSelectors, DEFAULT_WAIT);
		clickElement(loginButton);
		waitForUiToSettle();
	}

	private void trySelectGoogleAccount() {
		try {
			final WebDriverWait quickWait = new WebDriverWait(driver, QUICK_WAIT);
			final WebElement account = quickWait.until(ExpectedConditions
					.visibilityOfElementLocated(By.xpath("//*[normalize-space(.)='" + GOOGLE_ACCOUNT_EMAIL + "']")));
			clickElement(account);
			waitForUiToSettle();
		} catch (TimeoutException ignored) {
			// Account selector is not always shown (existing Google session).
		}
	}

	private void ensureMiNegocioExpanded() {
		final boolean administrarVisible = isVisible(containsVisibleText("Administrar Negocios"), Duration.ofSeconds(2));
		if (administrarVisible) {
			return;
		}

		if (isVisible(containsVisibleText("Mi Negocio"), Duration.ofSeconds(5))) {
			clickVisibleText("Mi Negocio");
			waitForUiToSettle();
		}
	}

	private void clickVisibleText(final String text) {
		final WebElement element = findFirstVisible(Arrays.asList(
				By.xpath("//button[normalize-space(.)='" + text + "']"),
				By.xpath("//a[normalize-space(.)='" + text + "']"),
				By.xpath("//*[normalize-space(.)='" + text + "']")), DEFAULT_WAIT);
		clickElement(element);
	}

	private void clickElement(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		try {
			element.click();
		} catch (Exception e) {
			new Actions(driver).moveToElement(element).click().perform();
		}
		waitForUiToSettle();
	}

	private void waitForAnyVisibleText(final List<String> texts, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		localWait.until(d -> {
			for (String text : texts) {
				if (isVisible(containsVisibleText(text), Duration.ofMillis(700))) {
					return true;
				}
			}
			return false;
		});
	}

	private void assertVisibleText(final String text) {
		assertElementExists(containsVisibleText(text), "Expected visible text not found: " + text);
	}

	private void assertVisibleAnyText(final List<String> texts, final String errorMessage) {
		for (String text : texts) {
			if (isVisible(containsVisibleText(text), Duration.ofSeconds(2))) {
				return;
			}
		}
		throw new AssertionError(errorMessage + " (checked: " + texts + ")");
	}

	private void assertElementExists(final By by, final String errorMessage) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(by));
		} catch (TimeoutException e) {
			throw new AssertionError(errorMessage, e);
		}
	}

	private By containsVisibleText(final String expectedText) {
		final String folded = normalizeForXPath(expectedText);
		return By.xpath("//*[contains("
				+ "translate(translate(normalize-space(.), 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun'),"
				+ " 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '"
				+ folded + "')]");
	}

	private By headingWithText(final String expectedText) {
		final String folded = normalizeForXPath(expectedText);
		return By.xpath("//h1[contains(translate(translate(normalize-space(.), 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun'),"
				+ " 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '" + folded + "')]"
				+ " | //h2[contains(translate(translate(normalize-space(.), 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun'),"
				+ " 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '" + folded + "')]");
	}

	private String normalizeForXPath(final String input) {
		final String noDiacritics = Normalizer.normalize(input, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		return noDiacritics.toLowerCase(Locale.ROOT);
	}

	private boolean isVisible(final By by, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	private WebElement findFirstVisible(final List<By> selectors, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		return localWait.until(d -> {
			if (d == null) {
				return null;
			}
			for (By by : selectors) {
				try {
					final List<WebElement> elements = d.findElements(by);
					for (WebElement element : elements) {
						if (element.isDisplayed()) {
							return element;
						}
					}
				} catch (NoSuchElementException ignored) {
					// Try next selector.
				}
			}
			return null;
		});
	}

	private void waitForUiToSettle() {
		try {
			wait.until(d -> {
				if (d == null) {
					return false;
				}
				try {
					return "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState"));
				} catch (Exception e) {
					return true;
				}
			});
		} catch (TimeoutException ignored) {
			// Some SPA routes do not transition readyState. Continue with explicit element waits.
		}

		try {
			Thread.sleep(350);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean runStep(final String stepName, final CheckedRunnable stepAction) {
		try {
			stepAction.run();
			report.put(stepName, "PASS");
			return true;
		} catch (Throwable e) {
			report.put(stepName, "FAIL");
			errors.add(stepName + " -> " + e.getMessage());
			captureScreenshot("failed-" + sanitizeFileName(stepName));
			return false;
		}
	}

	private void initializeReport() {
		report.put("Login", "NOT RUN");
		report.put("Mi Negocio menu", "NOT RUN");
		report.put("Agregar Negocio modal", "NOT RUN");
		report.put("Administrar Negocios view", "NOT RUN");
		report.put("Información General", "NOT RUN");
		report.put("Detalles de la Cuenta", "NOT RUN");
		report.put("Tus Negocios", "NOT RUN");
		report.put("Términos y Condiciones", "NOT RUN");
		report.put("Política de Privacidad", "NOT RUN");
	}

	private void markSkippedIfNotRun(final String stepName, final boolean previousStepSucceeded) {
		if (!previousStepSucceeded && "NOT RUN".equals(report.get(stepName))) {
			report.put(stepName, "SKIPPED");
		}
	}

	private boolean hasFailure() {
		return report.values().stream().anyMatch(v -> "FAIL".equals(v));
	}

	private void printFinalReport() {
		System.out.println();
		System.out.println("===== SaleADS Mi Negocio Final Report =====");
		for (Map.Entry<String, String> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
		System.out.println("Términos y Condiciones URL: " + (termsUrl.isBlank() ? "N/A" : termsUrl));
		System.out.println("Política de Privacidad URL: " + (privacyUrl.isBlank() ? "N/A" : privacyUrl));
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
		if (!errors.isEmpty()) {
			System.out.println("Errors: " + String.join(" | ", errors));
		}
		System.out.println("===========================================");
		System.out.println();
	}

	private void captureScreenshot(final String label) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final String filename = String.format("%02d-%s.png", screenshotIndex.getAndIncrement(), sanitizeFileName(label));
		final Path target = screenshotDir.resolve(filename);
		try {
			final File file = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			Files.copy(file.toPath(), target);
		} catch (IOException ignored) {
			// Keep test flow even if screenshot write fails.
		}
	}

	private String sanitizeFileName(final String input) {
		return input.replaceAll("[^a-zA-Z0-9-_]+", "-").replaceAll("^-+|-+$", "").toLowerCase(Locale.ROOT);
	}

	private boolean readBooleanConfig(final String property, final String envName, final boolean defaultValue) {
		final String value = readConfig(property, envName, Boolean.toString(defaultValue));
		return "true".equalsIgnoreCase(value.trim());
	}

	private String readConfig(final String property, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(property);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run();
	}
}
