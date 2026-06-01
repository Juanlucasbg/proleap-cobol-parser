package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * <p>The test is intentionally environment-agnostic. It does not hardcode a domain and accepts
 * a login URL through SALEADS_LOGIN_URL (or -Dsaleads.login.url).</p>
 *
 * <p>Run configuration:</p>
 * <ul>
 *   <li>SALEADS_RUN_E2E=true to execute the test (otherwise skipped)</li>
 *   <li>SALEADS_LOGIN_URL=https://... optional if the browser session is already on login page</li>
 *   <li>SALEADS_SELENIUM_REMOTE_URL=http://grid:4444/wd/hub optional for Selenium Grid</li>
 *   <li>SALEADS_HEADLESS=true|false optional, default true</li>
 *   <li>SALEADS_TIMEOUT_SECONDS=30 optional, default 30</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MENU = "Mi Negocio menu";
	private static final String REPORT_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN = "Administrar Negocios view";
	private static final String REPORT_INFO = "Informaci\u00f3n General";
	private static final String REPORT_ACCOUNT = "Detalles de la Cuenta";
	private static final String REPORT_BUSINESSES = "Tus Negocios";
	private static final String REPORT_TERMS = "T\u00e9rminos y Condiciones";
	private static final String REPORT_PRIVACY = "Pol\u00edtica de Privacidad";

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration SHORT_WAIT = Duration.ofSeconds(8);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;
	private boolean reportWritten;

	@Before
	public void setUp() throws IOException, MalformedURLException {
		final boolean runEnabled = Boolean.parseBoolean(config("saleads.run.e2e", "SALEADS_RUN_E2E", "false"));
		Assume.assumeTrue(
				"Skipping SaleADS E2E test. Set SALEADS_RUN_E2E=true to execute browser automation.",
				runEnabled);

		final long timeoutSeconds = Long.parseLong(config("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", "30"));
		evidenceDir = Paths.get(config("saleads.evidence.dir", "SALEADS_EVIDENCE_DIR", "target/saleads-mi-negocio-evidence"))
				.toAbsolutePath()
				.normalize();
		Files.createDirectories(evidenceDir);

		driver = createDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		appWindowHandle = driver.getWindowHandle();

		final String loginUrl = config("saleads.login.url", "SALEADS_LOGIN_URL", "").trim();
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
			waitForUiToLoad();
			appWindowHandle = driver.getWindowHandle();
		}
	}

	@After
	public void tearDown() throws IOException {
		if (!reportWritten && !report.isEmpty()) {
			writeFinalReport();
		}

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMIN, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFO, this::stepValidateInformacionGeneral);
		runStep(REPORT_ACCOUNT, this::stepValidateDetallesCuenta);
		runStep(REPORT_BUSINESSES, this::stepValidateTusNegocios);
		runStep(REPORT_TERMS, () -> stepValidateLegalDocument("T\u00e9rminos y Condiciones", "05_terminos_page"));
		runStep(REPORT_PRIVACY, () -> stepValidateLegalDocument("Pol\u00edtica de Privacidad", "06_privacidad_page"));

		writeFinalReport();
		reportWritten = true;

		final List<String> failed = new ArrayList<>();
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().pass) {
				failed.add(entry.getKey() + " -> " + entry.getValue().details);
			}
		}

		Assert.assertTrue("SaleADS workflow assertions failed:\n" + String.join("\n", failed), failed.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		final String currentUrl = driver.getCurrentUrl();
		if ("about:blank".equalsIgnoreCase(currentUrl) || "data:,".equalsIgnoreCase(currentUrl)) {
			throw new AssertionError(
					"Browser is not on SaleADS login page. Provide SALEADS_LOGIN_URL or preloaded browser session.");
		}

		clickByVisibleText(
				"Sign in with Google",
				"Iniciar sesion con Google",
				"Iniciar sesi\u00f3n con Google",
				"Continuar con Google",
				"Login with Google");

		selectGoogleAccountIfPrompted();
		switchToAppWindow();

		final boolean mainAppVisible = isVisible(By.xpath("//aside"), Duration.ofSeconds(25))
				|| isVisible(byVisibleText("Negocio"), Duration.ofSeconds(25))
				|| isVisible(byVisibleText("Mi Negocio"), Duration.ofSeconds(25));
		Assert.assertTrue("Main application interface did not appear after Google login.", mainAppVisible);

		final boolean sidebarVisible = isVisible(By.xpath("//aside"), Duration.ofSeconds(15))
				|| isVisible(byVisibleText("Negocio"), Duration.ofSeconds(15));
		Assert.assertTrue("Left sidebar navigation is not visible.", sidebarVisible);

		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		ensureMenuExpandedForMiNegocio();

		Assert.assertTrue("Submenu item 'Agregar Negocio' is not visible.",
				isVisible(byVisibleText("Agregar Negocio"), Duration.ofSeconds(10)));
		Assert.assertTrue("Submenu item 'Administrar Negocios' is not visible.",
				isVisible(byVisibleText("Administrar Negocios"), Duration.ofSeconds(10)));

		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		assertTextVisible("Crear Nuevo Negocio", Duration.ofSeconds(15));
		assertNombreDelNegocioInputVisible();
		assertTextVisible("Tienes 2 de 3 negocios", Duration.ofSeconds(10));
		assertTextVisible("Cancelar", Duration.ofSeconds(10));
		assertTextVisible("Crear Negocio", Duration.ofSeconds(10));

		takeScreenshot("03_agregar_negocio_modal");

		final WebElement nombreInput = wait.until(ExpectedConditions.visibilityOfElementLocated(nombreNegocioInputLocator()));
		nombreInput.click();
		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatizacion");

		clickByVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isVisible(byVisibleText("Administrar Negocios"), Duration.ofSeconds(4))) {
			ensureMenuExpandedForMiNegocio();
		}

		clickByVisibleText("Administrar Negocios");

		assertTextVisible("Informaci\u00f3n General", Duration.ofSeconds(15));
		assertTextVisible("Detalles de la Cuenta", Duration.ofSeconds(15));
		assertTextVisible("Tus Negocios", Duration.ofSeconds(15));
		assertTextVisible("Secci\u00f3n Legal", Duration.ofSeconds(15));

		takeScreenshot("04_administrar_negocios_account_page");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		final WebElement section = sectionContainerByHeading("Informaci\u00f3n General");
		final String sectionText = normalizeSpaces(section.getText());

		final boolean hasEmail = sectionText.contains(GOOGLE_ACCOUNT_EMAIL) || EMAIL_PATTERN.matcher(sectionText).find();
		Assert.assertTrue("User email is not visible in Informacion General.", hasEmail);

		Assert.assertTrue("Text 'BUSINESS PLAN' is not visible in Informacion General.",
				sectionText.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN"));
		Assert.assertTrue("Button 'Cambiar Plan' is not visible in Informacion General.",
				isVisible(relativeTextLocator(section, "Cambiar Plan"), Duration.ofSeconds(8)));

		final boolean hasLikelyName = containsLikelyPersonName(sectionText);
		Assert.assertTrue("User name is not clearly visible in Informacion General.", hasLikelyName);
	}

	private void stepValidateDetallesCuenta() throws Exception {
		final WebElement section = sectionContainerByHeading("Detalles de la Cuenta");
		final String sectionText = normalizeSpaces(section.getText());

		Assert.assertTrue("'Cuenta creada' is not visible.", sectionText.contains("Cuenta creada"));
		Assert.assertTrue("'Estado activo' is not visible.", sectionText.contains("Estado activo"));
		Assert.assertTrue("'Idioma seleccionado' is not visible.", sectionText.contains("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() throws Exception {
		final WebElement section = sectionContainerByHeading("Tus Negocios");
		final String sectionText = normalizeSpaces(section.getText());

		Assert.assertTrue("Button 'Agregar Negocio' is not visible in Tus Negocios.",
				isVisible(relativeTextLocator(section, "Agregar Negocio"), Duration.ofSeconds(8)));
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' is not visible in Tus Negocios.",
				sectionText.contains("Tienes 2 de 3 negocios"));

		final List<WebElement> businessItems = section
				.findElements(By.xpath(".//li | .//tr | .//*[@role='row'] | .//*[contains(@class,'business')]"));
		boolean hasVisibleBusinessItem = false;
		for (WebElement item : businessItems) {
			if (item.isDisplayed()) {
				hasVisibleBusinessItem = true;
				break;
			}
		}
		Assert.assertTrue("Business list is not visible in Tus Negocios.", hasVisibleBusinessItem || sectionText.length() > 80);
	}

	private void stepValidateLegalDocument(final String linkText, final String screenshotName) throws Exception {
		switchToAppWindow();
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String originalUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		final boolean openedNewTab = waitForNavigationOrTab(handlesBeforeClick, originalUrl);

		assertTextVisible(linkText, Duration.ofSeconds(20));
		final String bodyText = normalizeSpaces(wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText());
		Assert.assertTrue("Legal content text is not visible for: " + linkText, bodyText.length() > 120);

		takeScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
			return;
		}

		if (!Objects.equals(originalUrl, driver.getCurrentUrl())) {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void runStep(final String reportKey, final CheckedStep step) {
		try {
			step.run();
			report.put(reportKey, StepResult.pass());
		} catch (Throwable throwable) {
			final StringBuilder details = new StringBuilder(rootMessage(throwable));
			try {
				final Path failureShot = takeScreenshot("failure_" + sanitizeFileName(reportKey));
				details.append(" | screenshot=").append(failureShot);
			} catch (Exception evidenceEx) {
				details.append(" | screenshot_error=").append(rootMessage(evidenceEx));
			}
			report.put(reportKey, StepResult.fail(details.toString()));
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder content = new StringBuilder();
		content.append("SaleADS Mi Negocio Final Report\n");
		content.append("Generated: ").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("\n\n");

		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			content.append("- ")
					.append(entry.getKey())
					.append(": ")
					.append(entry.getValue().pass ? "PASS" : "FAIL")
					.append('\n');
			if (!entry.getValue().details.isEmpty()) {
				content.append("  details: ").append(entry.getValue().details).append('\n');
			}
		}

		if (!legalUrls.isEmpty()) {
			content.append("\nLegal final URLs:\n");
			for (Map.Entry<String, String> legalEntry : legalUrls.entrySet()) {
				content.append("- ").append(legalEntry.getKey()).append(": ").append(legalEntry.getValue()).append('\n');
			}
		}

		final Path reportFile = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportFile, content.toString());
		System.out.println(content);
		System.out.println("Evidence saved at: " + evidenceDir);
	}

	private WebDriver createDriver() throws MalformedURLException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");
		if (Boolean.parseBoolean(config("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = config("saleads.selenium.remote.url", "SALEADS_SELENIUM_REMOTE_URL", "").trim();
		if (!remoteUrl.isEmpty()) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}
		return new ChromeDriver(options);
	}

	private void ensureMenuExpandedForMiNegocio() {
		if (isVisible(byVisibleText("Mi Negocio"), Duration.ofSeconds(5))) {
			clickByVisibleText("Mi Negocio");
			return;
		}

		if (isVisible(byVisibleText("Negocio"), Duration.ofSeconds(5))) {
			clickByVisibleText("Negocio");
		}
		clickByVisibleText("Mi Negocio");
	}

	private void selectGoogleAccountIfPrompted() {
		final Set<String> handles = driver.getWindowHandles();
		for (String handle : handles) {
			try {
				driver.switchTo().window(handle);
				if (isVisible(byVisibleText(GOOGLE_ACCOUNT_EMAIL), SHORT_WAIT)) {
					clickByVisibleText(GOOGLE_ACCOUNT_EMAIL);
					break;
				}
			} catch (NoSuchWindowException ignored) {
				// Window closed by OAuth flow while iterating.
			}
		}
	}

	private boolean waitForNavigationOrTab(final Set<String> handlesBeforeClick, final String originalUrl) {
		new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> {
			final boolean tabOpened = d.getWindowHandles().size() > handlesBeforeClick.size();
			final boolean urlChanged = !Objects.equals(originalUrl, d.getCurrentUrl());
			return tabOpened || urlChanged;
		});

		final Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > handlesBeforeClick.size()) {
			for (String handle : handlesAfter) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					waitForUiToLoad();
					return true;
				}
			}
		}

		waitForUiToLoad();
		return false;
	}

	private void assertNombreDelNegocioInputVisible() {
		Assert.assertTrue("Input field 'Nombre del Negocio' does not exist.",
				isVisible(nombreNegocioInputLocator(), Duration.ofSeconds(10)));
	}

	private By nombreNegocioInputLocator() {
		return By.xpath(
				"//label[contains(normalize-space(), " + toXPathLiteral("Nombre del Negocio") + ")]/following::input[1]"
						+ " | //input[contains(@placeholder, " + toXPathLiteral("Nombre del Negocio") + ")]");
	}

	private void assertTextVisible(final String text, final Duration timeout) {
		Assert.assertTrue("Expected visible text not found: " + text, isVisible(byVisibleText(text), timeout));
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (TimeoutException timeoutException) {
			return false;
		}
	}

	private void clickByVisibleText(final String... texts) {
		for (String text : texts) {
			final By locator = byVisibleText(text);
			try {
				final WebElement clickable = new WebDriverWait(driver, SHORT_WAIT)
						.until(ExpectedConditions.elementToBeClickable(locator));
				scrollIntoView(clickable);
				clickable.click();
				waitForUiToLoad();
				return;
			} catch (TimeoutException ignored) {
				// Try next candidate text.
			}
		}

		throw new NoSuchElementException("No clickable element found by visible text candidates: " + String.join(", ", texts));
	}

	private void switchToAppWindow() {
		final Set<String> handles = driver.getWindowHandles();
		if (appWindowHandle != null && handles.contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			return;
		}

		if (!handles.isEmpty()) {
			final String fallback = handles.iterator().next();
			driver.switchTo().window(fallback);
			appWindowHandle = fallback;
		}
	}

	private WebElement sectionContainerByHeading(final String headingText) {
		final WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(), " + toXPathLiteral(headingText) + ")]")));
		return heading.findElement(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
	}

	private By relativeTextLocator(final WebElement root, final String text) {
		final String id = root.getAttribute("id");
		if (id != null && !id.isBlank()) {
			return By.xpath("//*[@id=" + toXPathLiteral(id) + "]//*[contains(normalize-space(), " + toXPathLiteral(text) + ")]");
		}
		return By.xpath("//*[contains(normalize-space(), " + toXPathLiteral(text) + ")]");
	}

	private boolean containsLikelyPersonName(final String sectionText) {
		for (String rawLine : sectionText.split("\\R")) {
			final String line = normalizeSpaces(rawLine);
			if (line.isEmpty() || line.contains("@")) {
				continue;
			}

			final String upper = line.toUpperCase(Locale.ROOT);
			if (upper.contains("INFORMACION")
					|| upper.contains("INFORMACI\u00d3N")
					|| upper.contains("BUSINESS PLAN")
					|| upper.contains("CAMBIAR PLAN")) {
				continue;
			}

			if (line.matches("[\\p{L}][\\p{L} .'-]{2,}")) {
				return true;
			}
		}
		return false;
	}

	private By byVisibleText(final String text) {
		return By.xpath("//*[contains(normalize-space(), " + toXPathLiteral(text) + ")]");
	}

	private void waitForUiToLoad() {
		wait.until(driverInstance -> "complete".equals(
				((JavascriptExecutor) driverInstance).executeScript("return document.readyState")));
	}

	private Path takeScreenshot(final String checkpointName) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			throw new IllegalStateException("Current driver does not support screenshots.");
		}

		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDir.resolve(checkpointName + ".png");
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		return destination;
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private String normalizeSpaces(final String raw) {
		return raw == null ? "" : raw.replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
	}

	private String sanitizeFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
	}

	private String rootMessage(final Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.toString() : current.getMessage();
	}

	private String config(final String systemPropertyKey, final String envKey, final String defaultValue) {
		final String fromSystemProperty = System.getProperty(systemPropertyKey);
		if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
			return fromSystemProperty;
		}

		final String fromEnv = System.getenv(envKey);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		return defaultValue;
	}

	private String toXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char ch = chars[i];
			if (ch == '\'') {
				builder.append("\"'\"");
			} else if (ch == '"') {
				builder.append("'\"'");
			} else {
				builder.append('\'').append(ch).append('\'');
			}
			if (i < chars.length - 1) {
				builder.append(',');
			}
		}
		builder.append(')');
		return builder.toString();
	}

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean pass;
		private final String details;

		private StepResult(final boolean pass, final String details) {
			this.pass = pass;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details == null ? "" : details);
		}
	}
}
