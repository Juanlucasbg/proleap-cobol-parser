package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end UI test for the SaleADS "Mi Negocio" module workflow.
 *
 * <p>
 * Required environment variables:
 * </p>
 * <ul>
 * <li>SALEADS_LOGIN_URL: login URL of the current SaleADS environment (dev/staging/prod).</li>
 * </ul>
 *
 * <p>
 * Optional environment variables:
 * </p>
 * <ul>
 * <li>SALEADS_GOOGLE_ACCOUNT: defaults to juanlucasbarbiergarzon@gmail.com.</li>
 * <li>SELENIUM_REMOTE_URL: if set, the test uses a remote Selenium Grid.</li>
 * <li>SALEADS_HEADLESS: set true to run headless when using local ChromeDriver.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(10);
	private static final String GOOGLE_ACCOUNT_DEFAULT = "juanlucasbarbiergarzon@gmail.com";

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES = "Detalles de la Cuenta";
	private static final String REPORT_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private final Map<String, String> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private int screenshotCounter;
	private String terminosUrl = "N/A";
	private String privacidadUrl = "N/A";

	@Before
	public void setup() throws Exception {
		initReport();
		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		screenshotDir = Path.of("target", "saleads-evidence", runId);
		Files.createDirectories(screenshotDir);
	}

	@After
	public void tearDown() throws Exception {
		printFinalReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String loginUrl = fromEnvOrProperty("SALEADS_LOGIN_URL", null);
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to run this E2E test.", loginUrl != null && !loginUrl.isBlank());

		driver.get(loginUrl);
		waitForUiToLoad();

		boolean canContinue = true;

		canContinue = runStep(REPORT_LOGIN, canContinue, () -> {
			loginWithGoogle();
			validateDashboardLoaded();
			captureScreenshot("01-dashboard-loaded");
		});

		canContinue = runStep(REPORT_MENU, canContinue, () -> {
			openMiNegocioMenu();
			assertVisibleText("Agregar Negocio");
			assertVisibleText("Administrar Negocios");
			captureScreenshot("02-mi-negocio-menu-expanded");
		});

		canContinue = runStep(REPORT_AGREGAR_MODAL, canContinue, () -> {
			clickByVisibleText("Agregar Negocio");
			assertVisibleText("Crear Nuevo Negocio");
			assertAnyVisible(By.xpath(
					"//label[contains(normalize-space(.), 'Nombre del Negocio')] | //input[@placeholder='Nombre del Negocio'] | //input[contains(@aria-label, 'Nombre del Negocio')]"));
			assertVisibleText("Tienes 2 de 3 negocios");
			assertVisibleText("Cancelar");
			assertVisibleText("Crear Negocio");
			captureScreenshot("03-agregar-negocio-modal");

			final List<WebElement> nameInputs = visibleElements(By.xpath(
					"//input[@placeholder='Nombre del Negocio'] | //input[contains(@aria-label, 'Nombre del Negocio')] | //label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));

			if (!nameInputs.isEmpty()) {
				final WebElement input = nameInputs.get(0);
				input.click();
				input.sendKeys(Keys.chord(Keys.CONTROL, "a"));
				input.sendKeys("Negocio Prueba Automatización");
				waitForUiToLoad();
			}

			clickByVisibleText("Cancelar");
		});

		canContinue = runStep(REPORT_ADMIN_VIEW, canContinue, () -> {
			ensureMiNegocioMenuExpanded();
			clickByVisibleText("Administrar Negocios");

			assertVisibleText("Información General");
			assertVisibleText("Detalles de la Cuenta");
			assertVisibleText("Tus Negocios");
			assertVisibleText("Sección Legal");
			captureScreenshot("04-administrar-negocios");
		});

		canContinue = runStep(REPORT_INFO_GENERAL, canContinue, () -> {
			final WebElement infoSection = sectionContainer("Información General");
			final String infoText = infoSection.getText();

			assertTrue("User email not visible.", containsVisibleEmail(infoText) || isTextVisible("@"));
			assertTrue("User name not clearly visible.", containsLikelyName(infoText));
			assertTrue("BUSINESS PLAN text not visible.",
					infoText.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN") || isTextVisible("BUSINESS PLAN"));
			assertVisibleText("Cambiar Plan");
		});

		canContinue = runStep(REPORT_DETALLES, canContinue, () -> {
			assertVisibleText("Cuenta creada");
			assertVisibleText("Estado activo");
			assertVisibleText("Idioma seleccionado");
		});

		canContinue = runStep(REPORT_NEGOCIOS, canContinue, () -> {
			final WebElement negociosSection = sectionContainer("Tus Negocios");
			assertVisibleText("Agregar Negocio");
			assertVisibleText("Tienes 2 de 3 negocios");

			final String negociosText = negociosSection.getText().replace("Tus Negocios", "").trim();
			assertTrue("Business list is not visible.", !negociosText.isBlank());
		});

		canContinue = runStep(REPORT_TERMINOS, canContinue, () -> {
			terminosUrl = validateLegalPageAndReturn("Términos y Condiciones", "Términos y Condiciones",
					"05-terminos-y-condiciones");
		});

		runStep(REPORT_PRIVACIDAD, canContinue, () -> {
			privacidadUrl = validateLegalPageAndReturn("Política de Privacidad", "Política de Privacidad",
					"06-politica-de-privacidad");
		});

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			if (!entry.getValue().startsWith("PASS")) {
				failedSteps.add(entry.getKey() + " -> " + entry.getValue());
			}
		}

		assertTrue("Workflow failed:\n" + String.join("\n", failedSteps), failedSteps.isEmpty());
	}

	private void loginWithGoogle() {
		clickByAnyVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Google"));
		waitForUiToLoad();

		final String account = fromEnvOrProperty("SALEADS_GOOGLE_ACCOUNT", GOOGLE_ACCOUNT_DEFAULT);
		maybeSwitchToNewestWindow();

		final By accountSelector = By.xpath("//*[normalize-space(.)=" + asXpathLiteral(account)
				+ "] | //*[@data-identifier=" + asXpathLiteral(account) + "]");
		clickIfVisible(accountSelector);

		waitForUiToLoad();
		maybeSwitchToNewestWindow();
	}

	private void validateDashboardLoaded() {
		assertAnyVisible(By.xpath("//aside | //nav"));
		assertAnyVisible(By.xpath("//*[contains(normalize-space(.), 'Negocio') or contains(normalize-space(.), 'Mi Negocio')]"));
	}

	private void openMiNegocioMenu() {
		if (!isTextVisible("Mi Negocio")) {
			clickByVisibleText("Negocio");
		}

		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();
		ensureMiNegocioMenuExpanded();
	}

	private void ensureMiNegocioMenuExpanded() {
		if (isTextVisible("Agregar Negocio") && isTextVisible("Administrar Negocios")) {
			return;
		}

		if (isTextVisible("Mi Negocio")) {
			clickByVisibleText("Mi Negocio");
		} else if (isTextVisible("Negocio")) {
			clickByVisibleText("Negocio");
			clickByVisibleText("Mi Negocio");
		}

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
	}

	private String validateLegalPageAndReturn(final String linkText, final String headingText, final String screenshotName)
			throws Exception {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> originalHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String originalUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);

		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);
		try {
			shortWait.until(driver -> driver.getWindowHandles().size() > originalHandles.size()
					|| !driver.getCurrentUrl().equals(originalUrl) || isTextVisible(headingText));
		} catch (final TimeoutException e) {
			// continue to heading validation for detailed assertion
		}

		boolean openedNewTab = false;
		final Set<String> currentHandles = driver.getWindowHandles();
		if (currentHandles.size() > originalHandles.size()) {
			for (final String handle : currentHandles) {
				if (!originalHandles.contains(handle)) {
					driver.switchTo().window(handle);
					openedNewTab = true;
					break;
				}
			}
		}

		waitForUiToLoad();
		assertVisibleText(headingText);
		assertTrue("Legal content not visible for " + headingText, visiblePageTextLength() > 120);
		captureScreenshot(screenshotName);
		final String capturedUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		assertVisibleText("Sección Legal");
		return capturedUrl;
	}

	private int visiblePageTextLength() {
		try {
			final WebElement body = driver.findElement(By.tagName("body"));
			return body.getText().trim().length();
		} catch (final NoSuchElementException e) {
			return 0;
		}
	}

	private WebElement sectionContainer(final String heading) {
		assertVisibleText(heading);
		final List<WebElement> sections = visibleElements(By.xpath(
				"//*[normalize-space(.)=" + asXpathLiteral(heading)
						+ "]/ancestor::*[self::section or self::div or self::article][1]"));
		assertTrue("Section container not found for heading: " + heading, !sections.isEmpty());
		return sections.get(0);
	}

	private boolean containsVisibleEmail(final String text) {
		final String[] tokens = text.split("\\s+");
		for (final String token : tokens) {
			if (token.contains("@") && token.contains(".")) {
				return true;
			}
		}
		return false;
	}

	private boolean containsLikelyName(final String text) {
		final String normalized = text.replace('\r', '\n');
		final String[] lines = normalized.split("\\n");
		final List<String> ignored = Arrays.asList("información general", "business plan", "cambiar plan", "plan",
				"cuenta creada", "estado activo", "idioma seleccionado", "tus negocios", "sección legal",
				"términos y condiciones", "política de privacidad");

		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.length() < 3 || line.contains("@")) {
				continue;
			}

			final String lower = line.toLowerCase(Locale.ROOT);
			boolean skip = false;
			for (final String keyword : ignored) {
				if (lower.contains(keyword)) {
					skip = true;
					break;
				}
			}

			if (!skip && line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}

		return false;
	}

	private boolean runStep(final String reportField, final boolean canRun, final ThrowingRunnable runnable)
			throws Exception {
		if (!canRun) {
			report.put(reportField, "FAIL - Blocked by previous step failure.");
			return false;
		}

		try {
			runnable.run();
			report.put(reportField, "PASS");
			return true;
		} catch (final Exception e) {
			report.put(reportField, "FAIL - " + e.getMessage());
			return false;
		}
	}

	private void initReport() {
		report.put(REPORT_LOGIN, "NOT_RUN");
		report.put(REPORT_MENU, "NOT_RUN");
		report.put(REPORT_AGREGAR_MODAL, "NOT_RUN");
		report.put(REPORT_ADMIN_VIEW, "NOT_RUN");
		report.put(REPORT_INFO_GENERAL, "NOT_RUN");
		report.put(REPORT_DETALLES, "NOT_RUN");
		report.put(REPORT_NEGOCIOS, "NOT_RUN");
		report.put(REPORT_TERMINOS, "NOT_RUN");
		report.put(REPORT_PRIVACIDAD, "NOT_RUN");
	}

	private void printFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append('\n');
		builder.append("SALEADS_MI_NEGOCIO_FINAL_REPORT\n");
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}
		builder.append("- Términos y Condiciones URL: ").append(terminosUrl).append('\n');
		builder.append("- Política de Privacidad URL: ").append(privacidadUrl).append('\n');
		builder.append("- Screenshots directory: ").append(screenshotDir).append('\n');
		System.out.println(builder.toString());
	}

	private WebDriver createDriver() throws Exception {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		final boolean headless = Boolean.parseBoolean(fromEnvOrProperty("SALEADS_HEADLESS", "false"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = fromEnvOrProperty("SELENIUM_REMOTE_URL", null);
		if (remoteUrl != null && !remoteUrl.isBlank()) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}

		return new ChromeDriver(options);
	}

	private void captureScreenshot(final String name) throws Exception {
		screenshotCounter++;
		final String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]+", "-").replaceAll("-+", "-")
				.replaceAll("(^-|-$)", "");
		final Path screenshotPath = screenshotDir.resolve(String.format("%02d-%s.png", screenshotCounter, normalized));
		final File file = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(file.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void clickByAnyVisibleText(final List<String> texts) {
		Exception lastException = null;
		for (final String text : texts) {
			try {
				clickByVisibleText(text);
				return;
			} catch (final Exception e) {
				lastException = e;
			}
		}

		throw new IllegalStateException("Could not click by any text option: " + texts, lastException);
	}

	private void clickByVisibleText(final String text) {
		final By locator = By.xpath("//button[normalize-space(.)=" + asXpathLiteral(text)
				+ "] | //a[normalize-space(.)=" + asXpathLiteral(text)
				+ "] | //*[@role='button' and normalize-space(.)=" + asXpathLiteral(text)
				+ "] | //*[normalize-space(.)=" + asXpathLiteral(text) + "]");
		final WebElement element = firstVisible(locator);
		wait.until(ExpectedConditions.elementToBeClickable(element));
		scrollIntoView(element);
		element.click();
		waitForUiToLoad();
	}

	private boolean clickIfVisible(final By locator) {
		final List<WebElement> elements = visibleElements(locator);
		if (elements.isEmpty()) {
			return false;
		}

		final WebElement element = elements.get(0);
		scrollIntoView(element);
		element.click();
		waitForUiToLoad();
		return true;
	}

	private void assertVisibleText(final String text) {
		assertAnyVisible(By.xpath("//*[normalize-space(.)=" + asXpathLiteral(text)
				+ "] | //*[contains(normalize-space(.), " + asXpathLiteral(text) + ")]"));
	}

	private boolean isTextVisible(final String text) {
		return !visibleElements(By.xpath("//*[contains(normalize-space(.), " + asXpathLiteral(text) + ")]")).isEmpty();
	}

	private void assertAnyVisible(final By locator) {
		final WebElement visibleElement = firstVisible(locator);
		assertTrue("Expected visible element for locator: " + locator, visibleElement.isDisplayed());
	}

	private WebElement firstVisible(final By locator) {
		wait.until((ExpectedCondition<Boolean>) driver -> !driver.findElements(locator).isEmpty());
		final List<WebElement> candidates = driver.findElements(locator);
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				return candidate;
			}
		}

		throw new IllegalStateException("No visible element found for locator: " + locator);
	}

	private List<WebElement> visibleElements(final By locator) {
		final List<WebElement> result = new ArrayList<>();
		for (final WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				result.add(element);
			}
		}
		return result;
	}

	private void waitForUiToLoad() {
		wait.until(driver -> {
			try {
				return "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState"));
			} catch (final Exception e) {
				return true;
			}
		});

		try {
			Thread.sleep(500);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center',inline:'center'});",
				element);
	}

	private void maybeSwitchToNewestWindow() {
		final Set<String> handles = driver.getWindowHandles();
		String last = null;
		for (final String handle : handles) {
			last = handle;
		}
		if (last != null && !driver.getWindowHandle().equals(last)) {
			driver.switchTo().window(last);
		}
	}

	private String fromEnvOrProperty(final String key, final String defaultValue) {
		final String prop = System.getProperty(key);
		if (prop != null && !prop.isBlank()) {
			return prop.trim();
		}

		final String env = System.getenv(key);
		if (env != null && !env.isBlank()) {
			return env.trim();
		}

		return defaultValue;
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder sb = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(", \"'\", ");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
