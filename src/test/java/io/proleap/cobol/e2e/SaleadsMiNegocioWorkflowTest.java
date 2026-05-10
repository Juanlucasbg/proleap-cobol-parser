package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	private static final String EXPECTED_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final LinkedHashMap<String, Boolean> stepResults = new LinkedHashMap<String, Boolean>();

	private final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<String, String>();

	private Path evidenceDirectory;

	private WebDriver driver;

	private WebDriverWait wait;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this workflow test.",
				Boolean.parseBoolean(envOrDefault("SALEADS_E2E_ENABLED", "false")));

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the login page URL of the target environment.",
				loginUrl != null && !loginUrl.trim().isEmpty());

		initializeStepResults();
		evidenceDirectory = createEvidenceDirectory();
		driver = createWebDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().window().setSize(new Dimension(1920, 1080));
		driver.get(loginUrl.trim());
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
		runStep("Login", new StepAction() {
			@Override
			public void run() throws Exception {
				loginWithGoogleAndValidateDashboard();
			}
		});
		runStep("Mi Negocio menu", new StepAction() {
			@Override
			public void run() throws Exception {
				openMiNegocioAndValidateMenu();
			}
		});
		runStep("Agregar Negocio modal", new StepAction() {
			@Override
			public void run() throws Exception {
				validateAgregarNegocioModal();
			}
		});
		runStep("Administrar Negocios view", new StepAction() {
			@Override
			public void run() throws Exception {
				openAdministrarNegociosAndValidateSections();
			}
		});
		runStep("Información General", new StepAction() {
			@Override
			public void run() {
				validateInformacionGeneral();
			}
		});
		runStep("Detalles de la Cuenta", new StepAction() {
			@Override
			public void run() {
				validateDetallesDeLaCuenta();
			}
		});
		runStep("Tus Negocios", new StepAction() {
			@Override
			public void run() {
				validateTusNegocios();
			}
		});
		runStep("Términos y Condiciones", new StepAction() {
			@Override
			public void run() throws Exception {
				validateLegalPage("Términos y Condiciones", "Términos y Condiciones", "08-terminos-y-condiciones.png");
			}
		});
		runStep("Política de Privacidad", new StepAction() {
			@Override
			public void run() throws Exception {
				validateLegalPage("Política de Privacidad", "Política de Privacidad", "09-politica-de-privacidad.png");
			}
		});

		final String report = buildFinalReport();
		Files.writeString(evidenceDirectory.resolve("final-report.txt"), report, StandardCharsets.UTF_8);
		System.out.println(report);
		assertTrue("Some validations failed.\n" + report, allStepsPassed());
	}

	private void loginWithGoogleAndValidateDashboard() throws Exception {
		final String appWindowHandle = driver.getWindowHandle();

		if (!isAnyTextVisible("Negocio", "Mi Negocio", "Administrar Negocios")) {
			clickFirstVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
					"Ingresar con Google", "Google");
			selectGoogleAccountIfPresent(EXPECTED_GOOGLE_EMAIL);
			switchToWindow(appWindowHandle);
		}

		waitForAnyVisibleText("Negocio", "Mi Negocio");
		assertTrue("Expected left sidebar navigation to be visible.", isAnyTextVisible("Negocio", "Mi Negocio"));
		takeScreenshot("01-dashboard-loaded.png");
	}

	private void openMiNegocioAndValidateMenu() throws Exception {
		expandMiNegocioMenu();
		requireVisibleText("Agregar Negocio");
		requireVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickFirstVisibleText("Agregar Negocio");
		requireVisibleText("Crear Nuevo Negocio");
		assertElementVisible(By.xpath("//*[normalize-space()=" + xPathLiteral("Nombre del Negocio")
				+ "] | //input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio']"));
		requireVisibleText("Tienes 2 de 3 negocios");
		requireVisibleText("Cancelar");
		requireVisibleText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal.png");

		final WebElement input = findVisibleElement(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio']"));
		input.clear();
		input.sendKeys("Negocio Prueba Automatización");
		clickFirstVisibleText("Cancelar");
	}

	private void openAdministrarNegociosAndValidateSections() throws Exception {
		expandMiNegocioMenu();
		clickFirstVisibleText("Administrar Negocios");
		requireVisibleText("Información General");
		requireVisibleText("Detalles de la Cuenta");
		requireVisibleText("Tus Negocios");
		requireVisibleText("Sección Legal");
		takeScreenshot("04-administrar-negocios-view.png");
	}

	private void validateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		final String sectionText = section.getText();
		final String expectedEmail = envOrDefault("SALEADS_EXPECTED_EMAIL", EXPECTED_GOOGLE_EMAIL);

		assertTrue("Expected user email to be visible.", EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("Expected selected user email to be visible.", sectionText.toLowerCase().contains(expectedEmail.toLowerCase()));
		assertTrue("Expected user name to be visible.", containsLikelyUserName(sectionText));
		requireVisibleText("BUSINESS PLAN");
		requireVisibleText("Cambiar Plan");
	}

	private void validateDetallesDeLaCuenta() {
		requireVisibleText("Cuenta creada");
		requireVisibleText("Estado activo");
		requireVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		findSectionByHeading("Tus Negocios");
		assertElementVisible(By.xpath("//button[normalize-space()='Agregar Negocio'] | //a[normalize-space()='Agregar Negocio']"));
		requireVisibleText("Tienes 2 de 3 negocios");
	}

	private void validateLegalPage(final String linkText, final String expectedHeading, final String screenshotName)
			throws Exception {
		final String applicationWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		clickFirstVisibleText(linkText);

		final String legalWindowHandle = waitForNewWindowHandle(handlesBefore, Duration.ofSeconds(8));
		final boolean openedNewTab = legalWindowHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(legalWindowHandle);
		}

		waitForAnyVisibleText(expectedHeading);
		assertLegalContentVisible(expectedHeading);
		takeScreenshot(screenshotName);
		legalUrls.put(expectedHeading, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			switchToWindow(applicationWindow);
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void assertLegalContentVisible(final String heading) {
		final String bodyText = findVisibleElement(By.tagName("body")).getText();
		final String contentWithoutHeading = bodyText.replace(heading, "").trim();
		assertTrue("Expected legal content text to be visible.", contentWithoutHeading.length() > 100);
	}

	private void expandMiNegocioMenu() throws Exception {
		if (!isAnyTextVisible("Agregar Negocio", "Administrar Negocios")) {
			if (isTextVisible("Negocio")) {
				clickFirstVisibleText("Negocio");
			}
			clickFirstVisibleText("Mi Negocio");
		}
		waitForUiToLoad();
	}

	private void selectGoogleAccountIfPresent(final String email) throws Exception {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
		final String emailXPath = "//*[normalize-space()=" + xPathLiteral(email) + "]";

		while (System.currentTimeMillis() < deadline) {
			for (final String handle : driver.getWindowHandles()) {
				try {
					driver.switchTo().window(handle);
					final List<WebElement> matches = driver.findElements(By.xpath(emailXPath));
					for (final WebElement match : matches) {
						if (match.isDisplayed()) {
							clickElement(match);
							return;
						}
					}
				} catch (NoSuchWindowException ex) {
					// Window closed while iterating.
				}
			}
			Thread.sleep(400);
		}
	}

	private String waitForNewWindowHandle(final Set<String> handlesBefore, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(new org.openqa.selenium.support.ui.ExpectedCondition<Boolean>() {
				@Override
				public Boolean apply(final WebDriver d) {
					return d.getWindowHandles().size() > handlesBefore.size();
				}
			});

			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					return handle;
				}
			}
		} catch (TimeoutException ex) {
			return null;
		}

		return null;
	}

	private void requireVisibleText(final String text) {
		waitForAnyVisibleText(text);
	}

	private void waitForAnyVisibleText(final String... texts) {
		new WebDriverWait(driver, DEFAULT_TIMEOUT).until(new org.openqa.selenium.support.ui.ExpectedCondition<Boolean>() {
			@Override
			public Boolean apply(final WebDriver d) {
				for (final String text : texts) {
					if (isTextVisible(text)) {
						return true;
					}
				}
				return false;
			}
		});
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return true;
			}
		}

		return false;
	}

	private boolean isTextVisible(final String text) {
		final String xpath = "//*[normalize-space()=" + xPathLiteral(text) + "]";
		for (final WebElement element : driver.findElements(By.xpath(xpath))) {
			try {
				if (element.isDisplayed()) {
					return true;
				}
			} catch (RuntimeException ex) {
				// Retry in next iteration.
			}
		}

		return false;
	}

	private void clickFirstVisibleText(final String... texts) throws Exception {
		for (final String text : texts) {
			final String xpath = "(//button[normalize-space()=" + xPathLiteral(text) + "]" + " | //a[normalize-space()="
					+ xPathLiteral(text) + "]" + " | //*[@role='button' and normalize-space()=" + xPathLiteral(text) + "]"
					+ " | //*[normalize-space()=" + xPathLiteral(text)
					+ "]/ancestor::*[self::button or self::a or @role='button'][1]" + " | //*[normalize-space()="
					+ xPathLiteral(text) + "])[1]";

			final List<WebElement> elements = driver.findElements(By.xpath(xpath));
			for (final WebElement element : elements) {
				try {
					if (element.isDisplayed()) {
						clickElement(element);
						return;
					}
				} catch (RuntimeException ex) {
					// Try next candidate.
				}
			}
		}

		throw new NoSuchElementException("Could not click any element with visible text: " + Arrays.toString(texts));
	}

	private void clickElement(final WebElement element) throws Exception {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		try {
			element.click();
		} catch (RuntimeException ex) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		new WebDriverWait(driver, DEFAULT_TIMEOUT).until(new org.openqa.selenium.support.ui.ExpectedCondition<Boolean>() {
			@Override
			public Boolean apply(final WebDriver d) {
				final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(String.valueOf(readyState));
			}
		});
	}

	private void switchToWindow(final String handle) {
		driver.switchTo().window(handle);
		waitForUiToLoad();
	}

	private void takeScreenshot(final String filename) throws IOException {
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(evidenceDirectory.resolve(filename), screenshot);
	}

	private void takeFailureScreenshot(final String stepName) {
		if (driver == null || evidenceDirectory == null) {
			return;
		}

		try {
			final String screenshotName = "failed-" + sanitize(stepName) + ".png";
			final Path screenshotPath = evidenceDirectory.resolve(screenshotName);
			Files.copy(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath(), screenshotPath,
					StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | RuntimeException ignored) {
		}
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, true);
		} catch (Exception ex) {
			stepResults.put(stepName, false);
			takeFailureScreenshot(stepName);
			System.err.println("Step failed [" + stepName + "]: " + ex.getMessage());
		}
	}

	private WebElement findVisibleElement(final By locator) {
		final List<WebElement> elements = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(locator));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return element;
			}
		}

		throw new NoSuchElementException("No visible element found for locator: " + locator);
	}

	private void assertElementVisible(final By locator) {
		assertTrue("Expected element to be visible for locator: " + locator, findVisibleElement(locator).isDisplayed());
	}

	private WebElement findSectionByHeading(final String heading) {
		final String xpath = "//*[normalize-space()=" + xPathLiteral(heading)
				+ "]/ancestor::*[self::section or self::div or self::article][1]";
		return findVisibleElement(By.xpath(xpath));
	}

	private boolean containsLikelyUserName(final String sectionText) {
		final String normalized = sectionText.replace('\n', ' ').trim();
		final String stripped = normalized.replace("BUSINESS PLAN", "").replace("Cambiar Plan", "")
				.replace(envOrDefault("SALEADS_EXPECTED_EMAIL", EXPECTED_GOOGLE_EMAIL), "").trim();
		return stripped.length() > 5;
	}

	private boolean allStepsPassed() {
		for (final Map.Entry<String, Boolean> result : stepResults.entrySet()) {
			if (!result.getValue().booleanValue()) {
				return false;
			}
		}

		return true;
	}

	private String buildFinalReport() {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test final report").append(System.lineSeparator());

		for (final String reportField : REPORT_FIELDS) {
			report.append("- ").append(reportField).append(": ")
					.append(stepResults.get(reportField).booleanValue() ? "PASS" : "FAIL").append(System.lineSeparator());
		}

		if (legalUrls.containsKey("Términos y Condiciones")) {
			report.append("- Términos y Condiciones URL: ").append(legalUrls.get("Términos y Condiciones"))
					.append(System.lineSeparator());
		}

		if (legalUrls.containsKey("Política de Privacidad")) {
			report.append("- Política de Privacidad URL: ").append(legalUrls.get("Política de Privacidad"))
					.append(System.lineSeparator());
		}

		report.append("- Evidence directory: ").append(evidenceDirectory.toAbsolutePath());
		return report.toString();
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path path = Path.of("target", "saleads-evidence", "saleads_mi_negocio_full_test-" + timestamp);
		Files.createDirectories(path);
		return path;
	}

	private void initializeStepResults() {
		for (final String field : REPORT_FIELDS) {
			stepResults.put(field, false);
		}
	}

	private String sanitize(final String input) {
		return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String envOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
	}

	private String xPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			result.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				result.append(",\"'\",");
			}
		}
		result.append(")");
		return result.toString();
	}

	private WebDriver createWebDriver() throws Exception {
		final String remoteUrl = System.getenv("SELENIUM_REMOTE_URL");
		final String browser = envOrDefault("SALEADS_BROWSER", "chrome").toLowerCase();

		if (remoteUrl != null && !remoteUrl.trim().isEmpty()) {
			if ("firefox".equals(browser)) {
				final FirefoxOptions options = new FirefoxOptions();
				options.addArguments("--width=1920");
				options.addArguments("--height=1080");
				return new RemoteWebDriver(new java.net.URL(remoteUrl), options);
			}

			final ChromeOptions options = new ChromeOptions();
			options.addArguments("--window-size=1920,1080");
			if (Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true"))) {
				options.addArguments("--headless=new");
			}
			return new RemoteWebDriver(new java.net.URL(remoteUrl), options);
		}

		if ("firefox".equals(browser)) {
			final FirefoxOptions options = new FirefoxOptions();
			if (Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true"))) {
				options.addArguments("--headless");
			}
			return new FirefoxDriver(options);
		}

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		return new ChromeDriver(options);
	}

	private interface StepAction {
		void run() throws Exception;
	}
}
