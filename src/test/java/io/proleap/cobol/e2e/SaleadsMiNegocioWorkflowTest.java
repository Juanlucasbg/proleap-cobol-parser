package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, Boolean> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		final boolean runE2E = Boolean.parseBoolean(readSetting("run.saleads.e2e", "RUN_SALEADS_E2E", "false"));
		Assume.assumeTrue("Set -Drun.saleads.e2e=true (or RUN_SALEADS_E2E=true) to execute this workflow test.", runE2E);

		evidenceDir = createEvidenceDirectory();

		final ChromeOptions chromeOptions = new ChromeOptions();
		chromeOptions.addArguments("--window-size=1920,1080");
		chromeOptions.addArguments("--disable-dev-shm-usage");
		chromeOptions.addArguments("--no-sandbox");
		chromeOptions.addArguments("--lang=es-ES");

		final boolean headless = Boolean.parseBoolean(readSetting("saleads.headless", "SALEADS_HEADLESS", "true"));
		if (headless) {
			chromeOptions.addArguments("--headless=new");
		}

		final String userDataDir = readSetting("saleads.chromeUserDataDir", "SALEADS_CHROME_USER_DATA_DIR", "");
		if (!userDataDir.isBlank()) {
			chromeOptions.addArguments("--user-data-dir=" + userDataDir);
		}

		final String profileDir = readSetting("saleads.chromeProfileDir", "SALEADS_CHROME_PROFILE_DIR", "");
		if (!profileDir.isBlank()) {
			chromeOptions.addArguments("--profile-directory=" + profileDir);
		}

		driver = new ChromeDriver(chromeOptions);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String loginUrl = readSetting("saleads.loginUrl", "SALEADS_LOGIN_URL", "");
		if (!loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiLoad();
		}

		appWindowHandle = driver.getWindowHandle();

		runStep("Login", this::loginWithGoogle);
		runStepIfPrerequisitePassed("Login", "Mi Negocio menu", this::openMiNegocioMenu);
		runStepIfPrerequisitePassed("Mi Negocio menu", "Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStepIfPrerequisitePassed("Mi Negocio menu", "Administrar Negocios view", this::openAdministrarNegocios);
		runStepIfPrerequisitePassed("Administrar Negocios view", "Información General", this::validateInformacionGeneral);
		runStepIfPrerequisitePassed("Administrar Negocios view", "Detalles de la Cuenta", this::validateDetallesCuenta);
		runStepIfPrerequisitePassed("Administrar Negocios view", "Tus Negocios", this::validateTusNegocios);
		runStepIfPrerequisitePassed("Administrar Negocios view", "Términos y Condiciones",
				() -> validateLegalDocument("Términos y Condiciones", "Términos y Condiciones", "05-terminos-condiciones.png"));
		runStepIfPrerequisitePassed("Administrar Negocios view", "Política de Privacidad",
				() -> validateLegalDocument("Política de Privacidad", "Política de Privacidad", "06-politica-privacidad.png"));

		final String reportText = buildReport();
		final Path finalReportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(finalReportPath, reportText);
		System.out.println(reportText);

		final List<String> failedSteps = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			if (!Boolean.TRUE.equals(stepStatus.get(field))) {
				failedSteps.add(field);
			}
		}

		if (!failedSteps.isEmpty()) {
			fail("SaleADS workflow failed in: " + failedSteps + ". Evidence: " + evidenceDir.toAbsolutePath());
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	private void loginWithGoogle() throws IOException {
		final Set<String> handlesBeforeLogin = new LinkedHashSet<>(driver.getWindowHandles());
		final boolean hasGoogleButton = isVisible(byAnyVisibleText("Sign in with Google", "Iniciar sesión con Google",
				"Continuar con Google", "Acceder con Google"), SHORT_TIMEOUT);

		if (hasGoogleButton) {
			clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
					"Acceder con Google");
			waitForUiLoad();
			switchToNewestWindowIfOpened(handlesBeforeLogin);
			if (isVisible(byAnyVisibleText(ACCOUNT_EMAIL), SHORT_TIMEOUT)) {
				clickByVisibleText(ACCOUNT_EMAIL);
				waitForUiLoad();
			}
		}

		waitForMainApplicationShell();
		takeScreenshot("01-dashboard-loaded.png");
	}

	private void openMiNegocioMenu() throws IOException {
		if (!isVisible(byAnyVisibleText("Mi Negocio"), SHORT_TIMEOUT) && isVisible(byAnyVisibleText("Negocio"), SHORT_TIMEOUT)) {
			clickByVisibleText("Negocio");
			waitForUiLoad();
		}

		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForUiLoad();

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final WebElement nombreInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='businessName']")));
		nombreInput.click();
		nombreInput.sendKeys("Negocio Prueba Automatización");
		takeScreenshot("03-agregar-negocio-modal.png");

		clickByVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void openAdministrarNegocios() throws IOException {
		if (!isVisible(byAnyVisibleText("Administrar Negocios"), SHORT_TIMEOUT)) {
			clickByVisibleText("Mi Negocio");
			waitForUiLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		takeScreenshot("04-administrar-negocios-view.png");
	}

	private void validateInformacionGeneral() {
		assertVisibleText("Información General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
		assertTrue("No user email was detected on screen.", hasVisibleEmail());
		assertTrue("No user name-like text was detected in Información General.", hasUserNameLikeText());
	}

	private void validateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertTrue("No business entries detected in Tus Negocios.", hasBusinessEntries());
	}

	private void validateLegalDocument(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		driver.switchTo().window(appWindowHandle);
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);
		waitForUiLoad();

		final String legalWindowHandle = switchToNewestWindowIfOpened(handlesBeforeClick);
		if (legalWindowHandle == null) {
			waitForUiLoad();
		}

		assertVisibleText(expectedHeading);
		assertTrue("Legal content did not render for " + expectedHeading + ".", hasLegalContent());
		takeScreenshot(screenshotName);
		capturedUrls.put(expectedHeading, driver.getCurrentUrl());

		if (legalWindowHandle != null) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private boolean runStep(final String stepName, final CheckedRunnable action) {
		try {
			action.run();
			stepStatus.put(stepName, Boolean.TRUE);
			stepDetails.put(stepName, "PASS");
			return true;
		} catch (final Exception error) {
			stepStatus.put(stepName, Boolean.FALSE);
			stepDetails.put(stepName, error.getMessage());
			return false;
		}
	}

	private boolean runStepIfPrerequisitePassed(final String prerequisiteStep, final String stepName,
			final CheckedRunnable action) {
		if (!Boolean.TRUE.equals(stepStatus.get(prerequisiteStep))) {
			stepStatus.put(stepName, Boolean.FALSE);
			stepDetails.put(stepName, "SKIPPED because prerequisite failed: " + prerequisiteStep);
			return false;
		}
		return runStep(stepName, action);
	}

	private void waitForMainApplicationShell() {
		wait.until(driver -> isVisible(byAnyVisibleText("Negocio", "Mi Negocio"), SHORT_TIMEOUT)
				&& !driver.findElements(By.xpath("//aside | //nav")).isEmpty());
	}

	private void clickByVisibleText(final String... texts) {
		for (final String text : texts) {
			final List<WebElement> candidates = driver.findElements(byAnyVisibleText(text));
			for (final WebElement candidate : candidates) {
				if (!candidate.isDisplayed()) {
					continue;
				}

				try {
					wait.until(ExpectedConditions.elementToBeClickable(candidate));
					candidate.click();
					return;
				} catch (final Exception clickError) {
					try {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", candidate);
						return;
					} catch (final Exception jsClickError) {
						// Try next candidate.
					}
				}
			}
		}

		throw new IllegalStateException("Could not click any element by visible text: " + Arrays.toString(texts));
	}

	private By byAnyVisibleText(final String... texts) {
		final StringBuilder expression = new StringBuilder();
		for (int i = 0; i < texts.length; i++) {
			if (i > 0) {
				expression.append(" or ");
			}
			expression.append("normalize-space()=").append(asXPathLiteral(texts[i]));
		}
		return By.xpath("//*[" + expression + "]");
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void assertVisibleText(final String text) {
		final By locator = byAnyVisibleText(text);
		wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private String switchToNewestWindowIfOpened(final Set<String> handlesBefore) {
		final Set<String> handlesAfter = new LinkedHashSet<>(driver.getWindowHandles());
		handlesAfter.removeAll(handlesBefore);

		if (handlesAfter.isEmpty()) {
			return null;
		}

		final String newestHandle = handlesAfter.iterator().next();
		driver.switchTo().window(newestHandle);
		waitForUiLoad();
		return newestHandle;
	}

	private boolean hasVisibleEmail() {
		final String pageText = driver.findElement(By.tagName("body")).getText();
		return EMAIL_PATTERN.matcher(pageText).find();
	}

	private boolean hasUserNameLikeText() {
		final String pageText = driver.findElement(By.tagName("body")).getText();
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(pageText);
		final String email = emailMatcher.find() ? emailMatcher.group() : ACCOUNT_EMAIL;

		final String[] lines = pageText.split("\\R");
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}

			if (trimmed.equalsIgnoreCase("Información General") || trimmed.equalsIgnoreCase("BUSINESS PLAN")
					|| trimmed.equalsIgnoreCase("Cambiar Plan") || trimmed.equalsIgnoreCase("Detalles de la Cuenta")
					|| trimmed.equalsIgnoreCase("Tus Negocios") || trimmed.contains("@") || trimmed.equals(email)) {
				continue;
			}

			if (trimmed.matches("^[\\p{L}][\\p{L}\\s'.-]{2,}$")) {
				return true;
			}
		}

		return false;
	}

	private boolean hasBusinessEntries() {
		final List<WebElement> entries = driver.findElements(
				By.xpath("//section[.//*[normalize-space()='Tus Negocios']]//*[self::li or self::tr or self::article]"));
		if (!entries.isEmpty()) {
			for (final WebElement entry : entries) {
				if (entry.isDisplayed() && !entry.getText().trim().isEmpty()) {
					return true;
				}
			}
		}

		final String bodyText = driver.findElement(By.tagName("body")).getText().toLowerCase(Locale.ROOT);
		return bodyText.contains("negocio");
	}

	private boolean hasLegalContent() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p[string-length(normalize-space()) > 40]"));
		for (final WebElement paragraph : paragraphs) {
			if (paragraph.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void waitForUiLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final Path destination = evidenceDir.resolve(fileName);
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private String buildReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS - Mi Negocio workflow result").append(System.lineSeparator());
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator())
				.append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			final boolean passed = Boolean.TRUE.equals(stepStatus.get(field));
			builder.append(field).append(": ").append(passed ? "PASS" : "FAIL");
			if (stepDetails.containsKey(field) && !passed) {
				builder.append(" -> ").append(stepDetails.get(field));
			}
			builder.append(System.lineSeparator());
		}

		if (!capturedUrls.isEmpty()) {
			builder.append(System.lineSeparator()).append("Captured URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : capturedUrls.entrySet()) {
				builder.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		return builder.toString();
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		return Files.createDirectories(Path.of("target", "saleads-evidence", timestamp));
	}

	private String readSetting(final String systemPropertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(systemPropertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private String asXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder literal = new StringBuilder("concat(");
		final char[] characters = text.toCharArray();
		for (int i = 0; i < characters.length; i++) {
			if (i > 0) {
				literal.append(", ");
			}
			final char character = characters[i];
			if (character == '\'') {
				literal.append("\"'\"");
			} else if (character == '"') {
				literal.append("'\"'");
			} else {
				literal.append("'").append(character).append("'");
			}
		}
		literal.append(")");
		return literal.toString();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
