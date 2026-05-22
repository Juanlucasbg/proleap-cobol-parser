package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.JavascriptExecutor;
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

public class SaleAdsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final List<String> REPORT_FIELDS = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws Exception {
		evidenceDir = Path.of(System.getProperty("saleads.evidence.dir", "target/saleads-evidence"));
		Files.createDirectories(evidenceDir);

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		wait.pollingEvery(Duration.ofMillis(250));

		openConfiguredStartUrlOrFailFast();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::loginWithGoogleAndValidateShell);
		runStep("Mi Negocio menu", this::openMiNegocioMenuAndValidateOptions);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegociosAndValidateSections);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", () -> validateLegalLink("Términos y Condiciones", "Términos y Condiciones",
				"05-terminos-y-condiciones.png"));
		runStep("Política de Privacidad",
				() -> validateLegalLink("Política de Privacidad", "Política de Privacidad", "06-politica-privacidad.png"));

		assertTrue(buildFailureSummary(), allStepsPassed());
	}

	private WebDriver createDriver() throws Exception {
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");

		final String remoteUrl = firstNonBlank(System.getProperty("saleads.selenium.remote.url"),
				System.getenv("SALEADS_SELENIUM_REMOTE_URL"));
		if (remoteUrl != null) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}
		return new ChromeDriver(options);
	}

	private void openConfiguredStartUrlOrFailFast() {
		final String startUrl = firstNonBlank(System.getProperty("saleads.start.url"), System.getenv("SALEADS_START_URL"));
		if (startUrl != null) {
			driver.get(startUrl);
			waitForUiToLoad();
			return;
		}

		final String currentUrl = safeCurrentUrl();
		if (currentUrl == null || currentUrl.isBlank() || currentUrl.startsWith("about:blank")
				|| currentUrl.startsWith("data:,")) {
			throw new IllegalStateException(
					"No SaleADS login page available. Set -Dsaleads.start.url=<current environment login URL>.");
		}
	}

	private void loginWithGoogleAndValidateShell() throws IOException {
		clickFirstVisibleText(List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Google"));
		selectGoogleAccountIfPrompted(GOOGLE_ACCOUNT_EMAIL);
		waitForApplicationShell();
		takeScreenshot("01-dashboard-loaded.png");

		final boolean sidebarVisible = isElementVisible(By.cssSelector("aside, nav"), 10)
				|| isTextVisible("Negocio", 5);
		assertTrue("Left sidebar navigation is not visible after login.", sidebarVisible);
	}

	private void openMiNegocioMenuAndValidateOptions() throws IOException {
		clickIfVisible("Negocio");
		clickByText("Mi Negocio");

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByText("Agregar Negocio");

		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal.png");

		final WebElement nombreInput = findNombreNegocioInput();
		assertTrue("Input field 'Nombre del Negocio' was not found.", nombreInput != null);
		nombreInput.click();
		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatización");
		waitForUiToLoad();

		clickByText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byExactVisibleText("Crear Nuevo Negocio")));
		waitForUiToLoad();
	}

	private void openAdministrarNegociosAndValidateSections() throws IOException {
		if (!isTextVisible("Administrar Negocios", 3)) {
			clickIfVisible("Negocio");
			clickIfVisible("Mi Negocio");
		}
		clickByText("Administrar Negocios");

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		takeScreenshot("04-administrar-negocios.png");
	}

	private void validateInformacionGeneral() {
		assertTextVisible("Información General");
		assertTrue("User email is not visible in Información General.", isAnyEmailVisible());
		assertTrue("User name was not clearly visible near user profile information.", isLikelyUserNameVisible());
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
	}

	private void validateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void validateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final Set<String> previousHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String originHandle = driver.getWindowHandle();
		final String originUrl = safeCurrentUrl();

		clickByText(linkText);
		wait.until(d -> d.getWindowHandles().size() > previousHandles.size()
				|| !Objects.equals(originUrl, safeCurrentUrl()));

		final String destinationHandle = switchToDestinationHandle(previousHandles, originHandle);
		waitForUiToLoad();

		assertLegalHeadingVisible(expectedHeading);
		assertLegalBodyVisible();
		takeScreenshot(screenshotName);
		legalUrls.put(linkText, safeCurrentUrl());

		if (!originHandle.equals(destinationHandle)) {
			driver.close();
			driver.switchTo().window(originHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();
		assertTextVisible("Sección Legal");
	}

	private String switchToDestinationHandle(final Set<String> previousHandles, final String originHandle) {
		final Set<String> currentHandles = driver.getWindowHandles();
		for (final String handle : currentHandles) {
			if (!previousHandles.contains(handle)) {
				driver.switchTo().window(handle);
				return handle;
			}
		}
		driver.switchTo().window(originHandle);
		return originHandle;
	}

	private void selectGoogleAccountIfPrompted(final String accountEmail) {
		for (final String handle : new ArrayList<>(driver.getWindowHandles())) {
			driver.switchTo().window(handle);
			if (isTextVisible(accountEmail, 3)) {
				clickByText(accountEmail);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void waitForApplicationShell() {
		wait.until(d -> {
			for (final String handle : d.getWindowHandles()) {
				d.switchTo().window(handle);
				final String currentUrl = safeCurrentUrl().toLowerCase(Locale.ROOT);
				final boolean googlePage = currentUrl.contains("accounts.google.");
				final boolean sidebarReady = isElementVisible(By.cssSelector("aside, nav"), 1) || isTextVisible("Negocio", 1);
				if (!googlePage && sidebarReady) {
					appWindowHandle = handle;
					return true;
				}
			}
			return false;
		});

		if (appWindowHandle != null) {
			driver.switchTo().window(appWindowHandle);
		}
		waitForUiToLoad();
	}

	private void clickByText(final String text) {
		wait.until(ExpectedConditions.elementToBeClickable(byExactVisibleText(text))).click();
		waitForUiToLoad();
	}

	private void clickFirstVisibleText(final List<String> texts) {
		for (final String text : texts) {
			if (isElementVisible(byExactVisibleText(text), 4)) {
				clickByText(text);
				return;
			}
		}
		throw new IllegalStateException("Unable to find a visible/clickable Google login button.");
	}

	private void clickIfVisible(final String text) {
		if (isElementVisible(byExactVisibleText(text), 3)) {
			clickByText(text);
		}
	}

	private void assertTextVisible(final String text) {
		assertTrue("Expected text is not visible: " + text, isTextVisible(text, 15));
	}

	private boolean isTextVisible(final String text, final int seconds) {
		return isElementVisible(byExactVisibleText(text), seconds);
	}

	private boolean isElementVisible(final By by, final int seconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(seconds))
					.until(ExpectedConditions.visibilityOfElementLocated(by));
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private boolean isAnyEmailVisible() {
		final String text = driver.findElement(By.tagName("body")).getText();
		return EMAIL_PATTERN.matcher(text).find();
	}

	private boolean isLikelyUserNameVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final String[] lines = bodyText.split("\\R");
		for (int i = 0; i < lines.length; i++) {
			if (EMAIL_PATTERN.matcher(lines[i]).find()) {
				for (int back = i - 1; back >= 0 && back >= i - 3; back--) {
					final String candidate = lines[back].trim();
					if (!candidate.isEmpty() && !candidate.contains("@") && candidate.length() > 2
							&& !candidate.toLowerCase(Locale.ROOT).contains("información general")) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private WebElement findNombreNegocioInput() {
		final List<By> candidates = List.of(
				By.xpath("//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"));

		for (final By locator : candidates) {
			final List<WebElement> matches = driver.findElements(locator);
			if (!matches.isEmpty()) {
				return matches.get(0);
			}
		}
		return null;
	}

	private void assertLegalHeadingVisible(final String headingText) {
		final By headingLocator = By.xpath("//h1[normalize-space()=" + toXPathLiteral(headingText)
				+ "] | //h2[normalize-space()=" + toXPathLiteral(headingText) + "] | //h3[normalize-space()="
				+ toXPathLiteral(headingText) + "]");
		if (!isElementVisible(headingLocator, 20)) {
			assertTrue("Expected legal heading not visible: " + headingText, isTextVisible(headingText, 20));
		}
	}

	private void assertLegalBodyVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText().trim();
		assertTrue("Legal content seems too short to be valid.", bodyText.length() >= 150);
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(350);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final Path screenshotPath = evidenceDir.resolve(fileName);
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void runStep(final String stepName, final StepAction stepAction) {
		try {
			stepAction.run();
			report.put(stepName, StepResult.pass());
		} catch (final Throwable error) {
			report.put(stepName, StepResult.fail(error.getMessage()));
		}
	}

	private boolean allStepsPassed() {
		for (final String field : REPORT_FIELDS) {
			if (!report.containsKey(field) || !report.get(field).passed) {
				return false;
			}
		}
		return true;
	}

	private String buildFailureSummary() {
		final StringBuilder builder = new StringBuilder("SaleADS Mi Negocio workflow failures:");
		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			if (result == null || !result.passed) {
				builder.append(System.lineSeparator()).append("- ").append(field).append(": ")
						.append(result == null ? "Not executed" : result.reason);
			}
		}
		builder.append(System.lineSeparator()).append("Evidence dir: ").append(evidenceDir.toAbsolutePath());
		return builder.toString();
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		builder.append("Generated at: ").append(Instant.now()).append(System.lineSeparator());
		builder.append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.getOrDefault(field, StepResult.fail("Not executed"));
			builder.append(field).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (result.reason != null && !result.reason.isBlank()) {
				builder.append(" - ").append(result.reason);
			}
			builder.append(System.lineSeparator());
		}

		builder.append(System.lineSeparator()).append("Final URLs").append(System.lineSeparator());
		builder.append("Términos y Condiciones: ").append(legalUrls.getOrDefault("Términos y Condiciones", "N/A"))
				.append(System.lineSeparator());
		builder.append("Política de Privacidad: ").append(legalUrls.getOrDefault("Política de Privacidad", "N/A"))
				.append(System.lineSeparator());

		Files.writeString(evidenceDir.resolve("final-report.txt"), builder.toString(), StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (final Exception e) {
			return "";
		}
	}

	private static String firstNonBlank(final String first, final String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		if (second != null && !second.isBlank()) {
			return second;
		}
		return null;
	}

	private static By byExactVisibleText(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath("//*[normalize-space()=" + literal + "]");
	}

	private static String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder concat = new StringBuilder("concat(");
		final Matcher matcher = Pattern.compile("([^'\"]+)|'|\"").matcher(value);
		boolean first = true;
		while (matcher.find()) {
			if (!first) {
				concat.append(", ");
			}
			final String token = matcher.group();
			if ("'".equals(token)) {
				concat.append("\"'\"");
			} else if ("\"".equals(token)) {
				concat.append("'\"'");
			} else {
				concat.append("'").append(token).append("'");
			}
			first = false;
		}
		return concat.append(")").toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String reason;

		private StepResult(final boolean passed, final String reason) {
			this.passed = passed;
			this.reason = reason;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String reason) {
			final String safeReason = reason == null || reason.isBlank() ? "Validation failed" : reason;
			return new StepResult(false, safeReason);
		}
	}
}
