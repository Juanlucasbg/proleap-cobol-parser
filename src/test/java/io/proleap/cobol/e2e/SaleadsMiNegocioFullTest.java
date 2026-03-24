package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end Selenium workflow for SaleADS "Mi Negocio".
 *
 * Runtime controls:
 * -Dsaleads.e2e.enabled=true
 * -Dsaleads.login.url=https://<current-env-login-url> (optional; if omitted, assumes page already opened)
 * -Dsaleads.remote.webdriver.url=http://<grid-host>:4444/wd/hub (optional)
 * -Dsaleads.headless=true|false (default false)
 * -Dsaleads.timeout.seconds=35
 * -Dsaleads.ui.pause.millis=700
 */
public class SaleadsMiNegocioFullTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MENU = "Mi Negocio menu";
	private static final String REPORT_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN = "Administrar Negocios view";
	private static final String REPORT_INFO = "Información General";
	private static final String REPORT_ACCOUNT = "Detalles de la Cuenta";
	private static final String REPORT_BUSINESSES = "Tus Negocios";
	private static final String REPORT_TERMS = "Términos y Condiciones";
	private static final String REPORT_PRIVACY = "Política de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
	private static final Pattern LIKELY_NAME_PATTERN = Pattern.compile("^[\\p{L}][\\p{L}\\s'\\-]{2,60}$");
	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private final Map<String, StepOutcome> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue(
				"Enable with -Dsaleads.e2e.enabled=true to run this UI test.",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

		setup();

		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMIN, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFO, this::stepValidateInformacionGeneral);
		runStep(REPORT_ACCOUNT, this::stepValidateDetallesCuenta);
		runStep(REPORT_BUSINESSES, this::stepValidateTusNegocios);
		runStep(REPORT_TERMS, () -> stepValidateLegalLink("Términos y Condiciones", REPORT_TERMS));
		runStep(REPORT_PRIVACY, () -> stepValidateLegalLink("Política de Privacidad", REPORT_PRIVACY));

		writeFinalReport();
		assertAllStepsPassed();
	}

	@After
	public void teardown() {
		if (driver != null) {
			driver.quit();
		}
	}

	private void setup() throws Exception {
		final String runId = RUN_ID_FORMATTER.format(LocalDateTime.now());
		evidenceDir = Path.of("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);

		final int timeoutSeconds = Integer.parseInt(System.getProperty("saleads.timeout.seconds", "35"));
		final long clickPauseMillis = Long.parseLong(System.getProperty("saleads.ui.pause.millis", "700"));
		System.setProperty("saleads.ui.pause.effective", String.valueOf(clickPauseMillis));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (Boolean.parseBoolean(System.getProperty("saleads.headless", "false"))) {
			options.addArguments("--headless=new");
			options.addArguments("--window-size=1920,1080");
		}

		final String remoteWebDriverUrl = trimToNull(System.getProperty("saleads.remote.webdriver.url"));
		if (remoteWebDriverUrl != null) {
			driver = new RemoteWebDriver(new URL(remoteWebDriverUrl), options);
		} else {
			driver = new ChromeDriver(options);
		}

		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		driver.manage().window().maximize();

		final String loginUrl = trimToNull(System.getProperty("saleads.login.url"));
		if (loginUrl != null) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		// If an authenticated session is already present, continue with validations.
		if (!isTextVisibleQuick("Mi Negocio") && !isTextVisibleQuick("Negocio")) {
			clickByVisibleTextCandidates(List.of(
					"Sign in with Google",
					"Iniciar sesión con Google",
					"Iniciar sesion con Google",
					"Ingresar con Google",
					"Continuar con Google",
					"Login with Google",
					"Google"));

			selectGoogleAccountIfPrompted("juanlucasbarbiergarzon@gmail.com");
		}

		assertTrue("Main application interface should be visible after login.", isMainInterfaceVisible());
		assertTrue("Left sidebar navigation should be visible after login.", isSidebarVisible());

		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		assertPreviousStepPassed(REPORT_LOGIN);

		waitForVisibleText("Negocio");
		if (!isTextVisibleQuick("Mi Negocio")) {
			clickByVisibleTextCandidates(List.of("Negocio"));
		}
		clickByVisibleTextCandidates(List.of("Mi Negocio"));

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");

		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		assertPreviousStepPassed(REPORT_MENU);

		clickByVisibleTextCandidates(List.of("Agregar Negocio"));
		waitForVisibleText("Crear Nuevo Negocio");

		waitForVisibleAny(List.of(
				By.xpath("//label[normalize-space()='Nombre del Negocio']"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[@name='nombre' or @name='businessName']")));
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");

		takeScreenshot("03_agregar_negocio_modal");

		// Optional flow requested by prompt.
		Optional<WebElement> maybeInput = findVisibleAny(List.of(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[@name='nombre']"),
				By.xpath("//input[@name='businessName']"),
				By.xpath("//label[normalize-space()='Nombre del Negocio']/following::input[1]")));
		if (maybeInput.isPresent()) {
			maybeInput.get().clear();
			maybeInput.get().sendKeys("Negocio Prueba Automatización");
		}

		clickByVisibleTextCandidates(List.of("Cancelar"));
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		assertPreviousStepPassed(REPORT_MODAL);

		if (!isTextVisibleQuick("Administrar Negocios")) {
			clickByVisibleTextCandidates(List.of("Mi Negocio"));
		}

		clickByVisibleTextCandidates(List.of("Administrar Negocios"));
		waitForUiToLoad();

		waitForVisibleText("Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal");

		takeScreenshot("04_administrar_negocios_page");
	}

	private void stepValidateInformacionGeneral() {
		assertPreviousStepPassed(REPORT_ADMIN);

		waitForVisibleText("Información General");
		assertTrue("User email should be visible.", isEmailVisibleOnPage());
		assertTrue("A likely user name should be visible.", isLikelyUserNameVisible());
		waitForVisibleText("BUSINESS PLAN");
		waitForVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertPreviousStepPassed(REPORT_ADMIN);

		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertPreviousStepPassed(REPORT_ADMIN);

		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
		assertTrue("Business list should be visible.", isBusinessListVisible());
	}

	private void stepValidateLegalLink(final String legalLinkText, final String reportKey) throws Exception {
		assertPreviousStepPassed(REPORT_ADMIN);

		final String appWindowHandle = driver.getWindowHandle();
		final String appUrlBeforeClick = safeCurrentUrl();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleTextCandidates(List.of(legalLinkText));

		String activeWindow = appWindowHandle;
		boolean openedNewTab = false;

		try {
			wait.until(d -> d.getWindowHandles().size() > handlesBefore.size() || !safeCurrentUrl().equals(appUrlBeforeClick));
		} catch (TimeoutException ignored) {
			// Continue; heading validation below gives the authoritative result.
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				openedNewTab = true;
				activeWindow = handle;
				waitForUiToLoad();
				break;
			}
		}

		waitForVisibleText(legalLinkText);
		assertTrue("Legal content text should be visible for " + legalLinkText, isLegalContentVisible());

		final String legalUrl = safeCurrentUrl();
		legalUrls.put(reportKey, legalUrl);
		takeScreenshot("legal_" + sanitize(legalLinkText));

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		} else if (!activeWindow.equals(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void runStep(final String key, final ThrowingRunnable runnable) {
		try {
			runnable.run();
			report.put(key, StepOutcome.pass());
		} catch (Throwable t) {
			report.put(key, StepOutcome.fail(t));
			try {
				takeScreenshot("failure_" + sanitize(key));
			} catch (Exception ignored) {
				// Best effort evidence capture.
			}
		}
	}

	private void assertPreviousStepPassed(final String key) {
		final StepOutcome prior = report.get(key);
		if (prior == null || !prior.passed) {
			throw new IllegalStateException("Required previous step failed: " + key);
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, StepOutcome> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				failed.add(entry.getKey());
			}
		}

		assertTrue("One or more workflow steps failed: " + failed + ". See report under " + evidenceDir,
				failed.isEmpty());
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Full Test Report").append(System.lineSeparator());
		sb.append("Generated: ").append(LocalDateTime.now()).append(System.lineSeparator());
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		sb.append(System.lineSeparator());

		appendReportField(sb, REPORT_LOGIN);
		appendReportField(sb, REPORT_MENU);
		appendReportField(sb, REPORT_MODAL);
		appendReportField(sb, REPORT_ADMIN);
		appendReportField(sb, REPORT_INFO);
		appendReportField(sb, REPORT_ACCOUNT);
		appendReportField(sb, REPORT_BUSINESSES);
		appendReportField(sb, REPORT_TERMS);
		appendReportField(sb, REPORT_PRIVACY);

		sb.append(System.lineSeparator());
		sb.append("Legal URLs:").append(System.lineSeparator());
		sb.append(" - ").append(REPORT_TERMS).append(": ")
				.append(legalUrls.getOrDefault(REPORT_TERMS, "N/A")).append(System.lineSeparator());
		sb.append(" - ").append(REPORT_PRIVACY).append(": ")
				.append(legalUrls.getOrDefault(REPORT_PRIVACY, "N/A")).append(System.lineSeparator());

		final Path reportFile = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportFile, sb.toString());

		System.out.println(sb);
	}

	private void appendReportField(final StringBuilder sb, final String key) {
		final StepOutcome outcome = report.get(key);
		if (outcome == null) {
			sb.append(" - ").append(key).append(": FAIL (step did not run)").append(System.lineSeparator());
			return;
		}

		sb.append(" - ").append(key).append(": ").append(outcome.passed ? "PASS" : "FAIL");
		if (!outcome.passed && outcome.message != null) {
			sb.append(" (").append(outcome.message).append(")");
		}
		sb.append(System.lineSeparator());
	}

	private void selectGoogleAccountIfPrompted(final String email) {
		final List<By> selectors = List.of(
				By.xpath("//*[normalize-space()=" + xpathLiteral(email) + "]"),
				By.xpath("//*[contains(@data-email," + xpathLiteral(email) + ")]"),
				By.xpath("//*[contains(@aria-label," + xpathLiteral(email) + ")]"));

		for (final By selector : selectors) {
			try {
				final WebElement candidate = new WebDriverWait(driver, Duration.ofSeconds(8))
						.until(ExpectedConditions.elementToBeClickable(selector));
				clickAndWait(candidate);
				return;
			} catch (TimeoutException ignored) {
				// Try the next selector.
			}
		}
	}

	private WebElement clickByVisibleTextCandidates(final List<String> candidates) {
		for (final String candidate : candidates) {
			final Optional<WebElement> element = findVisibleAny(List.of(
					By.xpath("//button[normalize-space()=" + xpathLiteral(candidate) + "]"),
					By.xpath("//a[normalize-space()=" + xpathLiteral(candidate) + "]"),
					By.xpath("//*[@role='button' and normalize-space()=" + xpathLiteral(candidate) + "]"),
					By.xpath("//*[@role='menuitem' and normalize-space()=" + xpathLiteral(candidate) + "]"),
					By.xpath("//*[self::button or self::a][contains(normalize-space(), " + xpathLiteral(candidate) + ")]"),
					By.xpath("//*[contains(normalize-space(), " + xpathLiteral(candidate)
							+ ") and (@role='button' or @role='menuitem' or @role='link')]")));
			if (element.isPresent()) {
				clickAndWait(element.get());
				return element.get();
			}
		}

		throw new NoSuchElementException("Unable to locate clickable element by text candidates: " + candidates);
	}

	private Optional<WebElement> findVisibleAny(final List<By> locators) {
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return Optional.of(element);
				}
			}
		}
		return Optional.empty();
	}

	private WebElement waitForVisibleAny(final List<By> locators) {
		return wait.until(d -> findVisibleAny(locators).orElse(null));
	}

	private WebElement waitForVisibleText(final String text) {
		return waitForVisibleAny(List.of(
				By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]")));
	}

	private boolean isTextVisibleQuick(final String text) {
		try {
			return findVisibleAny(List.of(
					By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]"),
					By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]"))).isPresent();
		} catch (Exception e) {
			return false;
		}
	}

	private boolean isMainInterfaceVisible() {
		return isTextVisibleQuick("Negocio")
				|| isTextVisibleQuick("Mi Negocio")
				|| isTextVisibleQuick("Dashboard")
				|| isTextVisibleQuick("Panel");
	}

	private boolean isSidebarVisible() {
		final Optional<WebElement> sidebar = findVisibleAny(List.of(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar')]")));
		if (sidebar.isPresent()) {
			return true;
		}
		return isTextVisibleQuick("Negocio") || isTextVisibleQuick("Mi Negocio");
	}

	private boolean isEmailVisibleOnPage() {
		try {
			final String text = driver.findElement(By.tagName("body")).getText();
			return EMAIL_PATTERN.matcher(text).find();
		} catch (Exception e) {
			return false;
		}
	}

	private boolean isLikelyUserNameVisible() {
		try {
			final String bodyText = driver.findElement(By.tagName("body")).getText();
			final String[] lines = bodyText.split("\\r?\\n");
			for (final String rawLine : lines) {
				final String line = rawLine.trim();
				if (line.isEmpty()) {
					continue;
				}
				if (EMAIL_PATTERN.matcher(line).find()) {
					continue;
				}
				if (isUiLabel(line)) {
					continue;
				}
				if (LIKELY_NAME_PATTERN.matcher(line).matches() && line.split("\\s+").length <= 4) {
					return true;
				}
			}
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	private boolean isUiLabel(final String line) {
		final String lower = line.toLowerCase(Locale.ROOT);
		return lower.contains("información general")
				|| lower.contains("informacion general")
				|| lower.contains("detalles de la cuenta")
				|| lower.contains("tus negocios")
				|| lower.contains("sección legal")
				|| lower.contains("seccion legal")
				|| lower.contains("cambiar plan")
				|| lower.contains("business plan");
	}

	private boolean isBusinessListVisible() {
		final List<By> listHeuristics = List.of(
				By.xpath("//*[contains(@class,'business') and not(self::button)]"),
				By.xpath("//*[contains(@class,'negocio') and not(self::button)]"),
				By.xpath("//section[.//*[contains(normalize-space(),'Tus Negocios')]]//*[self::li or self::tr or @role='row']"),
				By.xpath("//*[contains(text(),'Negocio') and not(self::button)]"));

		for (final By locator : listHeuristics) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isLegalContentVisible() {
		try {
			final String body = driver.findElement(By.tagName("body")).getText().trim();
			return body.length() >= 120;
		} catch (Exception e) {
			return false;
		}
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (Exception e) {
			return "";
		}
	}

	private void clickAndWait(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		} catch (Exception ignored) {
			// Fallback to direct click.
		}

		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));

		final long pauseMillis = Long.parseLong(System.getProperty("saleads.ui.pause.effective", "700"));
		try {
			Thread.sleep(pauseMillis);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String label) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDir.resolve(sanitize(label) + ".png");
		Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private String sanitize(final String raw) {
		return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	private String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
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

	private static class StepOutcome {
		private final boolean passed;
		private final String message;

		private StepOutcome(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message;
		}

		private static StepOutcome pass() {
			return new StepOutcome(true, null);
		}

		private static StepOutcome fail(final Throwable throwable) {
			final String detail = throwable == null ? "Unknown error" : throwable.getClass().getSimpleName()
					+ ": " + throwable.getMessage();
			return new StepOutcome(false, detail);
		}
	}
}
