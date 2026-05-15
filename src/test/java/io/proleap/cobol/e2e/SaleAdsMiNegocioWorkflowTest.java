package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String REPORT_POLITICA = "Pol\u00edtica de Privacidad";

	private static final String LABEL_NEGOCIO = "Negocio";
	private static final String LABEL_MI_NEGOCIO = "Mi Negocio";
	private static final String LABEL_AGREGAR_NEGOCIO = "Agregar Negocio";
	private static final String LABEL_ADMINISTRAR_NEGOCIOS = "Administrar Negocios";
	private static final String LABEL_CREAR_NUEVO_NEGOCIO = "Crear Nuevo Negocio";
	private static final String LABEL_NOMBRE_NEGOCIO = "Nombre del Negocio";
	private static final String LABEL_NEGOCIOS_PLAN = "Tienes 2 de 3 negocios";
	private static final String LABEL_CANCELAR = "Cancelar";
	private static final String LABEL_CREAR_NEGOCIO = "Crear Negocio";
	private static final String LABEL_INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String LABEL_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String LABEL_TUS_NEGOCIOS = "Tus Negocios";
	private static final String LABEL_SECCION_LEGAL = "Secci\u00f3n Legal";
	private static final String LABEL_BUSINESS_PLAN = "BUSINESS PLAN";
	private static final String LABEL_CAMBIAR_PLAN = "Cambiar Plan";
	private static final String LABEL_CUENTA_CREADA = "Cuenta creada";
	private static final String LABEL_ESTADO_ACTIVO = "Estado activo";
	private static final String LABEL_IDIOMA_SELECCIONADO = "Idioma seleccionado";
	private static final String LABEL_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String LABEL_POLITICA = "Pol\u00edtica de Privacidad";
	private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatizaci\u00f3n";

	private final Map<String, String> report = new LinkedHashMap<String, String>();
	private final Map<String, String> evidenceUrls = new LinkedHashMap<String, String>();
	private final List<String> failures = new ArrayList<String>();

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor js;
	private Duration timeout;
	private Path screenshotDir;
	private Path reportFile;

	@Before
	public void setUp() throws IOException {
		initializeReport();

		timeout = Duration.ofSeconds(parseLongEnv("SALEADS_TIMEOUT_SECONDS", 30L));
		screenshotDir = Path.of(getEnv("SALEADS_SCREENSHOT_DIR", "target/surefire-reports/saleads-mi-negocio-screenshots"));
		reportFile = Path.of(getEnv("SALEADS_REPORT_FILE", "target/surefire-reports/saleads-mi-negocio-report.txt"));
		Files.createDirectories(screenshotDir);
		if (reportFile.getParent() != null) {
			Files.createDirectories(reportFile.getParent());
		}

		driver = createWebDriver();
		wait = new WebDriverWait(driver, timeout);
		js = (JavascriptExecutor) driver;
	}

	@After
	public void tearDown() throws IOException {
		writeReportToFile();
		if (driver != null) {
			driver.quit();
		}
		if (!failures.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow failures:\n - " + String.join("\n - ", failures));
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() throws Exception {
		final String loginUrl = getEnv("SALEADS_LOGIN_URL", "");
		Assume.assumeTrue(
				"Skipping test: configure SALEADS_LOGIN_URL with the current environment login page URL.",
				loginUrl != null && !loginUrl.trim().isEmpty());

		driver.get(loginUrl.trim());
		waitForUiToLoad();

		executeStep(REPORT_LOGIN, new StepAction() {
			@Override
			public void run() throws Exception {
				clickAndWait(byGoogleLoginButton());
				selectGoogleAccountIfVisible(GOOGLE_ACCOUNT_EMAIL);

				waitUntilVisible(bySidebarNavigation());
				waitUntilVisible(byVisibleText(LABEL_NEGOCIO));
				captureScreenshot("01-dashboard-loaded");
			}
		});

		executeStep(REPORT_MI_NEGOCIO_MENU, new StepAction() {
			@Override
			public void run() throws Exception {
				waitUntilVisible(bySidebarNavigation());
				clickAndWait(byClickableText(LABEL_MI_NEGOCIO));
				waitUntilVisible(byVisibleText(LABEL_AGREGAR_NEGOCIO));
				waitUntilVisible(byVisibleText(LABEL_ADMINISTRAR_NEGOCIOS));
				captureScreenshot("02-mi-negocio-menu-expanded");
			}
		});

		executeStep(REPORT_AGREGAR_NEGOCIO_MODAL, new StepAction() {
			@Override
			public void run() throws Exception {
				clickAndWait(byClickableText(LABEL_AGREGAR_NEGOCIO));
				waitUntilVisible(byVisibleText(LABEL_CREAR_NUEVO_NEGOCIO));
				waitUntilVisible(byVisibleText(LABEL_NOMBRE_NEGOCIO));
				waitUntilVisible(byVisibleText(LABEL_NEGOCIOS_PLAN));
				waitUntilVisible(byClickableText(LABEL_CANCELAR));
				waitUntilVisible(byClickableText(LABEL_CREAR_NEGOCIO));
				captureScreenshot("03-agregar-negocio-modal");

				WebElement nombreNegocioInput = waitUntilVisible(byInputForLabel(LABEL_NOMBRE_NEGOCIO));
				nombreNegocioInput.click();
				nombreNegocioInput.clear();
				nombreNegocioInput.sendKeys(TEST_BUSINESS_NAME);
				clickAndWait(byClickableText(LABEL_CANCELAR));
				waitUntilInvisible(byVisibleText(LABEL_CREAR_NUEVO_NEGOCIO));
			}
		});

		executeStep(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, new StepAction() {
			@Override
			public void run() throws Exception {
				ensureMiNegocioMenuExpanded();
				clickAndWait(byClickableText(LABEL_ADMINISTRAR_NEGOCIOS));

				waitUntilVisible(byVisibleText(LABEL_INFORMACION_GENERAL));
				waitUntilVisible(byVisibleText(LABEL_DETALLES_CUENTA));
				waitUntilVisible(byVisibleText(LABEL_TUS_NEGOCIOS));
				waitUntilVisible(byVisibleText(LABEL_SECCION_LEGAL));
				captureScreenshot("04-administrar-negocios-page");
			}
		});

		executeStep(REPORT_INFORMACION_GENERAL, new StepAction() {
			@Override
			public void run() throws Exception {
				waitUntilVisible(byVisibleText(LABEL_INFORMACION_GENERAL));
				assertEmailVisible();
				waitUntilVisible(byVisibleText(LABEL_BUSINESS_PLAN));
				waitUntilVisible(byClickableText(LABEL_CAMBIAR_PLAN));
				assertUserNameLikeTextVisible();
			}
		});

		executeStep(REPORT_DETALLES_CUENTA, new StepAction() {
			@Override
			public void run() throws Exception {
				waitUntilVisible(byVisibleText(LABEL_DETALLES_CUENTA));
				waitUntilVisible(byVisibleText(LABEL_CUENTA_CREADA));
				waitUntilVisible(byVisibleText(LABEL_ESTADO_ACTIVO));
				waitUntilVisible(byVisibleText(LABEL_IDIOMA_SELECCIONADO));
			}
		});

		executeStep(REPORT_TUS_NEGOCIOS, new StepAction() {
			@Override
			public void run() throws Exception {
				waitUntilVisible(byVisibleText(LABEL_TUS_NEGOCIOS));
				waitUntilVisible(byClickableText(LABEL_AGREGAR_NEGOCIO));
				waitUntilVisible(byVisibleText(LABEL_NEGOCIOS_PLAN));
				assertBusinessListVisible();
			}
		});

		executeStep(REPORT_TERMINOS, new StepAction() {
			@Override
			public void run() throws Exception {
				openAndValidateLegalLink(LABEL_TERMINOS, LABEL_TERMINOS, "08-terminos-y-condiciones", REPORT_TERMINOS);
			}
		});

		executeStep(REPORT_POLITICA, new StepAction() {
			@Override
			public void run() throws Exception {
				openAndValidateLegalLink(LABEL_POLITICA, LABEL_POLITICA, "09-politica-de-privacidad", REPORT_POLITICA);
			}
		});
	}

	private void initializeReport() {
		report.put(REPORT_LOGIN, "NOT_RUN");
		report.put(REPORT_MI_NEGOCIO_MENU, "NOT_RUN");
		report.put(REPORT_AGREGAR_NEGOCIO_MODAL, "NOT_RUN");
		report.put(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, "NOT_RUN");
		report.put(REPORT_INFORMACION_GENERAL, "NOT_RUN");
		report.put(REPORT_DETALLES_CUENTA, "NOT_RUN");
		report.put(REPORT_TUS_NEGOCIOS, "NOT_RUN");
		report.put(REPORT_TERMINOS, "NOT_RUN");
		report.put(REPORT_POLITICA, "NOT_RUN");
	}

	private WebDriver createWebDriver() {
		final String browser = getEnv("SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(getEnv("SALEADS_HEADLESS", "true"));
		final WebDriver builtDriver;

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			builtDriver = new FirefoxDriver(firefoxOptions);
			break;
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			chromeOptions.addArguments("--window-size=1920,1080");
			chromeOptions.addArguments("--disable-gpu");
			chromeOptions.addArguments("--no-sandbox");
			chromeOptions.addArguments("--disable-dev-shm-usage");
			builtDriver = new ChromeDriver(chromeOptions);
			break;
		}

		builtDriver.manage().window().setSize(new Dimension(1920, 1080));
		builtDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
		return builtDriver;
	}

	private void executeStep(final String reportKey, final StepAction action) {
		try {
			action.run();
			report.put(reportKey, "PASS");
		} catch (final Throwable throwable) {
			report.put(reportKey, "FAIL");
			failures.add(reportKey + " - " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
			try {
				captureScreenshot("failed-" + toSlug(reportKey));
			} catch (final Exception screenshotError) {
				failures.add(reportKey + " - screenshot failure: " + screenshotError.getMessage());
			}
		}
	}

	private void ensureMiNegocioMenuExpanded() {
		if (driver.findElements(byVisibleText(LABEL_ADMINISTRAR_NEGOCIOS)).isEmpty()) {
			clickAndWait(byClickableText(LABEL_MI_NEGOCIO));
		}
		waitUntilVisible(byVisibleText(LABEL_ADMINISTRAR_NEGOCIOS));
	}

	private void openAndValidateLegalLink(final String linkText, final String headingText, final String screenshotName,
			final String reportKey) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickAndWait(byClickableText(linkText));

		final String newWindow = waitForNewWindow(handlesBeforeClick, Duration.ofSeconds(5));
		final boolean openedNewWindow = newWindow != null;
		if (openedNewWindow) {
			driver.switchTo().window(newWindow);
			waitForUiToLoad();
		}

		waitUntilVisible(byVisibleText(headingText));
		assertLegalContentVisible();
		captureScreenshot(screenshotName);
		evidenceUrls.put(reportKey, driver.getCurrentUrl());

		if (openedNewWindow) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private String waitForNewWindow(final Set<String> previousHandles, final Duration waitDuration) {
		final Instant timeoutAt = Instant.now().plus(waitDuration);
		while (Instant.now().isBefore(timeoutAt)) {
			for (final String handle : driver.getWindowHandles()) {
				if (!previousHandles.contains(handle)) {
					return handle;
				}
			}
			sleepMillis(200);
		}
		return null;
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		final String currentWindow = driver.getWindowHandle();
		final Set<String> allHandles = driver.getWindowHandles();

		for (final String handle : allHandles) {
			driver.switchTo().window(handle);
			final List<WebElement> accountMatches = driver
					.findElements(By.xpath("//*[contains(normalize-space(.),'" + escapeXpath(accountEmail) + "')]"));
			if (!accountMatches.isEmpty()) {
				clickAndWait(By.xpath("//*[contains(normalize-space(.),'" + escapeXpath(accountEmail) + "')]"));
				break;
			}
		}

		driver.switchTo().window(currentWindow);
		waitForUiToLoad();
	}

	private void assertEmailVisible() {
		final By emailLocator = By.xpath("//*[contains(normalize-space(.), '@') and contains(normalize-space(.), '.')]");
		waitUntilVisible(emailLocator);
	}

	private void assertUserNameLikeTextVisible() {
		final List<WebElement> candidates = driver.findElements(By.xpath(
				"//section//*[string-length(normalize-space(.)) >= 3 and not(contains(normalize-space(.), '@'))] | "
						+ "//div//*[string-length(normalize-space(.)) >= 3 and not(contains(normalize-space(.), '@'))]"));
		for (final WebElement candidate : candidates) {
			final String text = candidate.getText().trim();
			if (!text.isEmpty()
					&& !text.equalsIgnoreCase(LABEL_INFORMACION_GENERAL)
					&& !text.equalsIgnoreCase(LABEL_DETALLES_CUENTA)
					&& !text.equalsIgnoreCase(LABEL_BUSINESS_PLAN)
					&& !text.equalsIgnoreCase(LABEL_CAMBIAR_PLAN)) {
				return;
			}
		}
		throw new AssertionError("No user-name-like text was detected on the account view.");
	}

	private void assertBusinessListVisible() {
		final List<WebElement> cardsOrRows = driver.findElements(By.xpath(
				"//*[self::table or self::ul or self::ol or self::tbody or self::section or self::div]"
						+ "[.//*[contains(normalize-space(.),'" + escapeXpath(LABEL_TUS_NEGOCIOS) + "')]]"
						+ "//*[self::li or self::tr or self::article or self::div][string-length(normalize-space(.)) >= 3]"));
		if (cardsOrRows.isEmpty()) {
			throw new AssertionError("Business list does not appear to be visible.");
		}
	}

	private void assertLegalContentVisible() {
		final List<WebElement> legalParagraphs = driver.findElements(By.xpath(
				"//p[string-length(normalize-space(.)) > 40] | //article//*[string-length(normalize-space(.)) > 40]"));
		if (legalParagraphs.isEmpty()) {
			throw new AssertionError("Legal content text was not found.");
		}
	}

	private WebElement waitUntilVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void waitUntilInvisible(final By locator) {
		wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
	}

	private void clickAndWait(final By locator) {
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
		scrollIntoView(element);
		try {
			element.click();
		} catch (final Exception clickFailure) {
			js.executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(new java.util.function.Function<WebDriver, Boolean>() {
			@Override
			public Boolean apply(final WebDriver webDriver) {
				return "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState"));
			}
		});
		sleepMillis(300);
	}

	private void scrollIntoView(final WebElement element) {
		js.executeScript("arguments[0].scrollIntoView({block:'center', inline:'center'});", element);
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final Path target = screenshotDir.resolve(checkpointName + ".png");
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeReportToFile() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio workflow report").append(System.lineSeparator());
		builder.append("Generated at: ").append(Instant.now().toString()).append(System.lineSeparator());
		builder.append(System.lineSeparator());

		for (final Map.Entry<String, String> entry : report.entrySet()) {
			builder.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}

		if (!evidenceUrls.isEmpty()) {
			builder.append(System.lineSeparator());
			builder.append("Captured final URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : evidenceUrls.entrySet()) {
				builder.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		if (!failures.isEmpty()) {
			builder.append(System.lineSeparator());
			builder.append("Failures").append(System.lineSeparator());
			for (final String failure : failures) {
				builder.append("- ").append(failure).append(System.lineSeparator());
			}
		}

		Files.writeString(reportFile, builder.toString());
	}

	private By bySidebarNavigation() {
		return By.xpath("//aside | //nav[contains(@class,'sidebar') or .//*[contains(normalize-space(.),'" + escapeXpath(LABEL_NEGOCIO) + "')]]");
	}

	private By byGoogleLoginButton() {
		return By.xpath("//button[contains(normalize-space(.),'Google')]"
				+ " | //a[contains(normalize-space(.),'Google')]"
				+ " | //*[@role='button' and contains(normalize-space(.),'Google')]");
	}

	private By byClickableText(final String text) {
		return By.xpath("//button[normalize-space(.)='" + escapeXpath(text) + "']"
				+ " | //a[normalize-space(.)='" + escapeXpath(text) + "']"
				+ " | //*[@role='button' and normalize-space(.)='" + escapeXpath(text) + "']"
				+ " | //*[self::span or self::div][normalize-space(.)='" + escapeXpath(text)
				+ "']/ancestor::*[self::button or self::a or @role='button'][1]"
				+ " | //*[self::span or self::div][normalize-space(.)='" + escapeXpath(text) + "']");
	}

	private By byVisibleText(final String text) {
		return By.xpath("//*[normalize-space(.)='" + escapeXpath(text) + "']");
	}

	private By byInputForLabel(final String labelText) {
		return By.xpath("//label[contains(normalize-space(.),'" + escapeXpath(labelText)
				+ "')]/following::input[1] | //input[@placeholder='" + escapeXpath(labelText) + "']"
				+ " | //input[contains(@aria-label,'" + escapeXpath(labelText) + "')]");
	}

	private String getEnv(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		return value.trim();
	}

	private long parseLongEnv(final String key, final long defaultValue) {
		final String raw = getEnv(key, Long.toString(defaultValue));
		try {
			return Long.parseLong(raw);
		} catch (final NumberFormatException exception) {
			return defaultValue;
		}
	}

	private String toSlug(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String escapeXpath(final String text) {
		if (!text.contains("'")) {
			return text;
		}
		return text.replace("'", "\\'");
	}

	private void sleepMillis(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private interface StepAction {
		void run() throws Exception;
	}
}
