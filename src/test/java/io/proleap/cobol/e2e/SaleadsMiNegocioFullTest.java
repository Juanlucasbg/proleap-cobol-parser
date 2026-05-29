package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
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
 * SaleADS.ai Mi Negocio end-to-end workflow test.
 *
 * <p>This test is environment-agnostic: it does not hardcode any domain and expects the login URL
 * to be provided at runtime via SALEADS_LOGIN_URL.</p>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;
	private String terminosUrl = "";
	private String privacidadUrl = "";

	@Before
	public void setUp() throws IOException {
		final String loginUrl = readEnv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the environment login page before running this test.",
				loginUrl != null && !loginUrl.isBlank());

		this.driver = createWebDriver();
		this.wait = new WebDriverWait(driver, DEFAULT_WAIT);
		this.evidenceDir = createEvidenceDir();

		driver.get(loginUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaDePrivacidad);

		final List<String> failedSteps = stepResults.entrySet().stream()
				.filter(entry -> !entry.getValue())
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

		assertTrue("Workflow validations failed for: " + failedSteps + ". Check report in " + evidenceDir,
				failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		final Set<String> windowsBefore = driver.getWindowHandles();
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
				"Continuar con Google");

		selectGoogleAccountIfPrompted(windowsBefore, "juanlucasbarbiergarzon@gmail.com");
		switchBackToAppWindow();

		waitForAnyText("Negocio");
		assertSidebarVisible();
		takeScreenshot("01-dashboard");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		waitForAnyText("Agregar Negocio");
		waitForAnyText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");

		waitForAnyText("Crear Nuevo Negocio");
		waitForAnyText("Nombre del Negocio");
		waitForAnyText("Tienes 2 de 3 negocios");
		waitForAnyText("Cancelar");
		waitForAnyText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final List<WebElement> nombreFields = driver.findElements(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='nombreNegocio' or @aria-label='Nombre del Negocio']"
						+ " | //label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]"));
		if (!nombreFields.isEmpty() && nombreFields.get(0).isDisplayed()) {
			nombreFields.get(0).click();
			nombreFields.get(0).sendKeys("Negocio Prueba Automatizacion");
		}

		clickByVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(2))) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");

		waitForAnyText("Información General", "Informacion General");
		waitForAnyText("Detalles de la Cuenta", "Detalles de la cuenta");
		waitForAnyText("Tus Negocios");
		waitForAnyText("Sección Legal", "Seccion Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		waitForAnyText("Información General", "Informacion General");
		waitForAnyText("BUSINESS PLAN");
		waitForAnyText("Cambiar Plan");

		assertTrue("Expected a visible user email.", isEmailVisibleOnPage());
		assertTrue("Expected user name text to be visible.", isProbableUserNameVisible());
	}

	private void stepValidateDetallesDeLaCuenta() {
		waitForAnyText("Cuenta creada");
		waitForAnyText("Estado activo");
		waitForAnyText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitForAnyText("Tus Negocios");
		waitForAnyText("Agregar Negocio");
		waitForAnyText("Tienes 2 de 3 negocios");
		assertTrue("Expected business list/cards/table to be visible.", isBusinessListVisible());
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		terminosUrl = openLegalDocument("Términos y Condiciones", "Terminos y Condiciones", "08-terminos");
	}

	private void stepValidatePoliticaDePrivacidad() throws IOException {
		privacidadUrl = openLegalDocument("Política de Privacidad", "Politica de Privacidad", "09-privacidad");
	}

	private String openLegalDocument(final String textWithAccent, final String fallbackText, final String screenshotName)
			throws IOException {
		final String appHandleBefore = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(textWithAccent, fallbackText);

		final String newHandle = waitForNewTab(handlesBefore, Duration.ofSeconds(10));
		final boolean openedNewTab = newHandle != null;
		if (openedNewTab) {
			driver.switchTo().window(newHandle);
		}

		waitForAnyText(textWithAccent, fallbackText);
		assertLegalContentVisible(textWithAccent, fallbackText);
		takeScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandleBefore);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();

		return finalUrl;
	}

	private void assertLegalContentVisible(final String titleWithAccent, final String titleWithoutAccent) {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final String normalized = bodyText.toLowerCase(Locale.ROOT);

		assertTrue("Expected legal title to be visible.",
				normalized.contains(titleWithAccent.toLowerCase(Locale.ROOT))
						|| normalized.contains(titleWithoutAccent.toLowerCase(Locale.ROOT)));

		assertTrue("Expected legal page body content to be visible.", bodyText.trim().length() > 200);
	}

	private void runStep(final String label, final CheckedRunnable action) {
		try {
			action.run();
			stepResults.put(label, true);
		} catch (final Throwable ex) {
			stepResults.put(label, false);
			stepErrors.put(label, ex.getClass().getSimpleName() + ": " + ex.getMessage());
			try {
				takeScreenshot("error-" + sanitize(label));
			} catch (final IOException ignored) {
				// best effort screenshot
			}
		}
	}

	private void clickByVisibleText(final String... candidates) {
		Throwable lastError = null;
		for (final String candidate : candidates) {
			try {
				final By locator = By.xpath("//button[normalize-space()=" + xpathLiteral(candidate) + "]"
						+ " | //a[normalize-space()=" + xpathLiteral(candidate) + "]"
						+ " | //*[@role='button' and normalize-space()=" + xpathLiteral(candidate) + "]"
						+ " | //*[self::span or self::div][normalize-space()=" + xpathLiteral(candidate)
						+ "]/ancestor::*[self::button or self::a or @role='button' or self::li][1]");
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
				try {
					element.click();
				} catch (final Exception clickError) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				}
				waitForUiToLoad();
				return;
			} catch (final Throwable ex) {
				lastError = ex;
			}
		}
		throw new AssertionError("Could not click using visible text candidates: " + List.of(candidates), lastError);
	}

	private void waitForAnyText(final String... candidates) {
		final WebDriverWait localWait = new WebDriverWait(driver, DEFAULT_WAIT);
		localWait.until(ignored -> {
			for (final String candidate : candidates) {
				final By exact = By.xpath("//*[normalize-space()=" + xpathLiteral(candidate) + "]");
				final List<WebElement> elements = driver.findElements(exact);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			}
			return false;
		});
	}

	private void waitForUiToLoad() {
		wait.until(ignored -> "complete".equals(
				((JavascriptExecutor) driver).executeScript("return document.readyState")));
		wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
	}

	private void assertSidebarVisible() {
		final List<By> sidebarLocators = List.of(
				By.xpath("//aside"),
				By.xpath("//nav[contains(@class, 'sidebar') or contains(@aria-label, 'sidebar')]"),
				By.xpath("//div[contains(@class, 'sidebar')]"));
		for (final By locator : sidebarLocators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}

		throw new AssertionError("Left sidebar navigation is not visible.");
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			final WebDriverWait localWait = new WebDriverWait(driver, timeout);
			return localWait.until(ignored -> {
				final List<WebElement> elements = driver
						.findElements(By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]"));
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return true;
					}
				}
				return false;
			});
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void selectGoogleAccountIfPrompted(final Set<String> windowsBefore, final String email) {
		final long timeoutAt = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (System.nanoTime() < timeoutAt) {
			final Set<String> currentHandles = driver.getWindowHandles();
			final Set<String> candidates = new LinkedHashSet<>(currentHandles);
			candidates.addAll(windowsBefore);

			for (final String handle : candidates) {
				try {
					driver.switchTo().window(handle);
					final String url = driver.getCurrentUrl();
					final String pageText = driver.findElement(By.tagName("body")).getText();
					final boolean isGoogleSelector = url.contains("accounts.google.com")
							|| pageText.contains("Choose an account")
							|| pageText.contains("Elige una cuenta");
					if (isGoogleSelector) {
						final List<WebElement> emailTargets = driver.findElements(
								By.xpath("//*[normalize-space()=" + xpathLiteral(email) + "]"));
						if (!emailTargets.isEmpty() && emailTargets.get(0).isDisplayed()) {
							emailTargets.get(0).click();
							waitForUiToLoad();
							return;
						}
					}
				} catch (final Exception ignored) {
					// keep polling until timeout
				}
			}
			sleep(500);
		}
	}

	private void switchBackToAppWindow() {
		if (appWindowHandle != null) {
			driver.switchTo().window(appWindowHandle);
			return;
		}

		final List<String> handles = new ArrayList<>(driver.getWindowHandles());
		if (!handles.isEmpty()) {
			driver.switchTo().window(handles.get(0));
			appWindowHandle = handles.get(0);
		}
	}

	private String waitForNewTab(final Set<String> handlesBefore, final Duration timeout) {
		final long timeoutAt = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < timeoutAt) {
			final Set<String> currentHandles = driver.getWindowHandles();
			for (final String handle : currentHandles) {
				if (!handlesBefore.contains(handle)) {
					return handle;
				}
			}
			sleep(300);
		}
		return null;
	}

	private boolean isEmailVisibleOnPage() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		return EMAIL_PATTERN.matcher(bodyText).find();
	}

	private boolean isProbableUserNameVisible() {
		final List<WebElement> candidates = driver
				.findElements(By.xpath("//h1 | //h2 | //h3 | //p | //span"));
		for (final WebElement candidate : candidates) {
			if (!candidate.isDisplayed()) {
				continue;
			}
			final String text = candidate.getText().trim();
			if (text.length() < 3) {
				continue;
			}
			final String normalized = text.toLowerCase(Locale.ROOT);
			if (normalized.contains("business plan") || normalized.contains("cambiar plan")
					|| normalized.contains("información general") || normalized.contains("informacion general")) {
				continue;
			}
			if (!EMAIL_PATTERN.matcher(text).find()) {
				return true;
			}
		}
		return false;
	}

	private boolean isBusinessListVisible() {
		final List<WebElement> businessItems = driver.findElements(By.xpath(
				"//*[contains(@class,'business') or contains(@class,'negocio') or contains(@class,'card') or self::li or self::tr]"));
		for (final WebElement businessItem : businessItems) {
			if (businessItem.isDisplayed() && businessItem.getText().trim().length() > 1) {
				return true;
			}
		}
		return false;
	}

	private void takeScreenshot(final String label) throws IOException {
		final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(evidenceDir.resolve(sanitize(label) + ".png"), bytes);
	}

	private Path createEvidenceDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio Workflow Report").append('\n');
		report.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		report.append('\n');

		appendResult(report, "Login");
		appendResult(report, "Mi Negocio menu");
		appendResult(report, "Agregar Negocio modal");
		appendResult(report, "Administrar Negocios view");
		appendResult(report, "Información General");
		appendResult(report, "Detalles de la Cuenta");
		appendResult(report, "Tus Negocios");
		appendResult(report, "Términos y Condiciones");
		appendResult(report, "Política de Privacidad");
		report.append('\n');
		report.append("Final URL - Términos y Condiciones: ").append(terminosUrl).append('\n');
		report.append("Final URL - Política de Privacidad: ").append(privacidadUrl).append('\n');

		Files.writeString(evidenceDir.resolve("final-report.txt"), report.toString(), StandardCharsets.UTF_8);
	}

	private void appendResult(final StringBuilder report, final String label) {
		final boolean passed = stepResults.getOrDefault(label, false);
		report.append(label).append(": ").append(passed ? "PASS" : "FAIL");
		if (!passed && stepErrors.containsKey(label)) {
			report.append(" (").append(stepErrors.get(label)).append(')');
		}
		report.append('\n');
	}

	private WebDriver createWebDriver() {
		final String remoteWebDriverUrl = readEnv("SALEADS_REMOTE_WEBDRIVER_URL");
		final boolean headless = Boolean.parseBoolean(readEnv("SALEADS_HEADLESS", "true"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--disable-gpu", "--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage");

		if (remoteWebDriverUrl != null && !remoteWebDriverUrl.isBlank()) {
			try {
				return new RemoteWebDriver(new URL(remoteWebDriverUrl), options);
			} catch (final MalformedURLException ex) {
				throw new IllegalArgumentException("SALEADS_REMOTE_WEBDRIVER_URL is invalid: " + remoteWebDriverUrl, ex);
			}
		}

		return new ChromeDriver(options);
	}

	private static String sanitize(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-_]+", "-").replaceAll("-{2,}", "-")
				.replaceAll("^-|-$", "");
	}

	private static String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final String[] chunks = text.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < chunks.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(chunks[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private static void sleep(final long milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private static String readEnv(final String key) {
		return System.getenv(key);
	}

	private static String readEnv(final String key, final String fallback) {
		final String value = readEnv(key);
		return value == null || value.isBlank() ? fallback : value;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
