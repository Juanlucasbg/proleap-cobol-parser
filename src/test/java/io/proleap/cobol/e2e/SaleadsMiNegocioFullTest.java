package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
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
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end UI test for the SaleADS "Mi Negocio" module workflow.
 *
 * <p>
 * This test intentionally avoids hardcoded environment URLs. Provide the target login URL with
 * either SALEADS_BASE_URL env var or -Dsaleads.baseUrl system property.
 * </p>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final DateTimeFormatter REPORT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String DEFAULT_BUSINESS_NAME = "Negocio Prueba Automatizacion";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	private final LinkedHashMap<String, StepResult> reportResults = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws Exception {
		evidenceDir = Paths.get(readSetting("saleads.evidenceDir", "SALEADS_EVIDENCE_DIR", "target/saleads-evidence"));
		Files.createDirectories(evidenceDir);

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);
		driver.manage().window().maximize();

		final String baseUrl = readSetting("saleads.baseUrl", "SALEADS_BASE_URL", "");
		if (baseUrl == null || baseUrl.trim().isEmpty()) {
			throw new IllegalStateException(
					"Missing SaleADS login URL. Set SALEADS_BASE_URL or -Dsaleads.baseUrl to the login page of the current environment.");
		}
		driver.get(baseUrl);
		waitForUiToSettle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", this::stepValidateTerminosCondiciones);
		executeStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		writeFinalReport();
		assertNoFailures();
	}

	private void stepLoginWithGoogle() throws Exception {
		clickFirstVisibleText("Sign in with Google", "Iniciar sesion con Google", "Inicia sesion con Google",
				"Continuar con Google", "Ingresar con Google", "Login con Google");

		waitForUiToSettle();
		selectGoogleAccountIfRequested(readSetting("saleads.googleAccount", "SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT));

		assertAnyVisible(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar')]"));
		assertVisibleTextAny("Negocio", "NEGOCIO");

		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickIfVisibleText("Negocio");
		clickFirstVisibleText("Mi Negocio");

		waitForUiToSettle();
		assertVisibleTextAny("Agregar Negocio");
		assertVisibleTextAny("Administrar Negocios");

		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickFirstVisibleText("Agregar Negocio");
		waitForUiToSettle();

		assertVisibleTextAny("Crear Nuevo Negocio");
		assertVisibleTextAny("Nombre del Negocio");
		assertVisibleTextAny("Tienes 2 de 3 negocios");
		assertVisibleTextAny("Cancelar");
		assertVisibleTextAny("Crear Negocio");

		takeScreenshot("03_agregar_negocio_modal");

		final WebElement businessInput = findFirstVisible(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[@name='nombreNegocio']"),
				By.xpath("//input[@name='businessName']"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"));

		if (businessInput != null) {
			businessInput.clear();
			businessInput.sendKeys(readSetting("saleads.newBusinessName", "SALEADS_NEW_BUSINESS_NAME", DEFAULT_BUSINESS_NAME));
		}

		clickFirstVisibleText("Cancelar");
		waitForTextToDisappear("Crear Nuevo Negocio");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		clickIfVisibleText("Mi Negocio");
		clickFirstVisibleText("Administrar Negocios");
		waitForUiToSettle();

		assertVisibleTextAny("Informacion General", "Información General");
		assertVisibleTextAny("Detalles de la Cuenta");
		assertVisibleTextAny("Tus Negocios");
		assertVisibleTextAny("Seccion Legal", "Sección Legal");

		takeScreenshot("04_administrar_negocios_page");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleTextAny("Informacion General", "Información General");
		assertVisibleEmail();
		assertVisibleTextAny("BUSINESS PLAN");
		assertVisibleTextAny("Cambiar Plan");
		assertLikelyUserNameVisible();
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleTextAny("Cuenta creada");
		assertVisibleTextAny("Estado activo");
		assertVisibleTextAny("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleTextAny("Tus Negocios");
		assertVisibleTextAny("Agregar Negocio");
		assertVisibleTextAny("Tienes 2 de 3 negocios");
		assertBusinessListVisible();
	}

	private void stepValidateTerminosCondiciones() throws Exception {
		validateLegalLink("Terminos y Condiciones", "Términos y Condiciones", "05_terminos_y_condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		validateLegalLink("Politica de Privacidad", "Política de Privacidad", "06_politica_de_privacidad");
	}

	private void validateLegalLink(final String asciiLabel, final String accentedLabel, final String screenshotName)
			throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickFirstVisibleText(accentedLabel, asciiLabel);
		waitForUiToSettle();

		final boolean openedNewTab = waitForNewWindow(handlesBeforeClick);
		if (openedNewTab) {
			switchToNewWindow(handlesBeforeClick);
			waitForUiToSettle();
		}

		assertVisibleTextAny(accentedLabel, asciiLabel);
		assertLegalContentVisible();
		takeScreenshot(screenshotName);

		legalUrls.put(accentedLabel, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToSettle();
		} else {
			driver.navigate().back();
			waitForUiToSettle();
		}
	}

	private void executeStep(final String stepName, final CheckedAction action) {
		try {
			action.run();
			reportResults.put(stepName, StepResult.passed());
		} catch (final Throwable throwable) {
			reportResults.put(stepName, StepResult.failed(throwable));
			try {
				takeScreenshot("FAILED_" + sanitizeName(stepName));
			} catch (final Exception ignored) {
				// Keep the original failure as the step result.
			}
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder content = new StringBuilder();
		content.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		content.append("Executed at: ").append(REPORT_TIME_FORMAT.format(LocalDateTime.now())).append(System.lineSeparator());
		content.append(System.lineSeparator());
		content.append("Step results:").append(System.lineSeparator());

		for (final Map.Entry<String, StepResult> entry : reportResults.entrySet()) {
			content.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().passed ? "PASS" : "FAIL");
			if (entry.getValue().failureMessage != null) {
				content.append(" - ").append(entry.getValue().failureMessage);
			}
			content.append(System.lineSeparator());
		}

		content.append(System.lineSeparator());
		content.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
			content.append("- ").append(legalUrl.getKey()).append(" URL: ").append(legalUrl.getValue())
					.append(System.lineSeparator());
		}

		final Path reportFile = evidenceDir.resolve("final-report.txt");
		Files.write(reportFile, content.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(content);
	}

	private void assertNoFailures() {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : reportResults.entrySet()) {
			if (!entry.getValue().passed) {
				failedSteps.add(entry.getKey() + " => " + entry.getValue().failureMessage);
			}
		}
		if (!failedSteps.isEmpty()) {
			Assert.fail("Failed workflow validations: " + failedSteps);
		}
	}

	private WebDriver createDriver() throws MalformedURLException {
		final String remoteUrl = readSetting("saleads.remoteUrl", "SELENIUM_REMOTE_URL", "");
		final boolean headless = Boolean
				.parseBoolean(readSetting("saleads.headless", "SALEADS_HEADLESS", "false"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--window-size=1920,1080");
		if (headless) {
			options.addArguments("--headless=new");
		}

		if (remoteUrl != null && !remoteUrl.trim().isEmpty()) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}
		return new ChromeDriver(options);
	}

	private void selectGoogleAccountIfRequested(final String accountEmail) throws InterruptedException {
		final long deadline = System.currentTimeMillis() + SHORT_TIMEOUT.toMillis();
		while (System.currentTimeMillis() < deadline) {
			switchToGoogleWindowIfPresent();

			final WebElement accountOption = findFirstVisibleNoWait(
					By.xpath("//*[normalize-space()=" + toXPathLiteral(accountEmail) + "]"),
					By.xpath("//*[contains(normalize-space(), " + toXPathLiteral(accountEmail) + ")]"));

			if (accountOption != null) {
				safeClick(accountOption);
				waitForUiToSettle();
				return;
			}

			Thread.sleep(300L);
		}
	}

	private void switchToGoogleWindowIfPresent() {
		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			final String url = driver.getCurrentUrl();
			if (url != null && url.contains("accounts.google.")) {
				return;
			}
		}
	}

	private boolean waitForNewWindow(final Set<String> handlesBeforeClick) {
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(drv -> drv.getWindowHandles().size() > handlesBeforeClick.size());
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void switchToNewWindow(final Set<String> handlesBeforeClick) {
		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBeforeClick.contains(handle)) {
				driver.switchTo().window(handle);
				return;
			}
		}
		throw new IllegalStateException("Expected a new browser tab but none was found.");
	}

	private void waitForUiToSettle() {
		wait.until(driverInstance -> "complete"
				.equals(((JavascriptExecutor) driverInstance).executeScript("return document.readyState")));
	}

	private void waitForTextToDisappear(final String text) {
		final String xpath = "//*[normalize-space()=" + toXPathLiteral(text) + "]";
		new WebDriverWait(driver, DEFAULT_TIMEOUT).until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(xpath)));
	}

	private void clickFirstVisibleText(final String... labels) {
		for (final String label : labels) {
			final WebElement element = findClickableByText(label);
			if (element != null) {
				safeClick(element);
				waitForUiToSettle();
				return;
			}
		}
		Assert.fail("Could not find clickable element with visible text options: " + Arrays.toString(labels));
	}

	private void clickIfVisibleText(final String label) {
		final WebElement element = findClickableByText(label);
		if (element != null) {
			safeClick(element);
			waitForUiToSettle();
		}
	}

	private WebElement findClickableByText(final String label) {
		final String literal = toXPathLiteral(label);
		return findFirstVisible(
				By.xpath("//button[normalize-space()=" + literal + "]"),
				By.xpath("//a[normalize-space()=" + literal + "]"),
				By.xpath("//*[@role='button' and normalize-space()=" + literal + "]"),
				By.xpath("//*[normalize-space()=" + literal + "]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"),
				By.xpath("//*[normalize-space()=" + literal + "]"));
	}

	private void assertVisibleTextAny(final String... labels) {
		for (final String label : labels) {
			final WebElement element = waitForVisibleText(label);
			if (element != null) {
				return;
			}
		}
		Assert.fail("Expected visible text not found. Options: " + Arrays.toString(labels));
	}

	private WebElement waitForVisibleText(final String text) {
		final String literal = toXPathLiteral(text);
		final List<By> selectors = Arrays.asList(
				By.xpath("//*[normalize-space()=" + literal + "]"),
				By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
		try {
			return new WebDriverWait(driver, DEFAULT_TIMEOUT).until(drv -> {
				for (final By selector : selectors) {
					final WebElement visible = firstDisplayed(drv.findElements(selector));
					if (visible != null) {
						return visible;
					}
				}
				return null;
			});
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private void assertVisibleEmail() {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[contains(normalize-space(), '@')]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed() && EMAIL_PATTERN.matcher(element.getText()).find()) {
				return;
			}
		}
		Assert.fail("Expected visible user email was not found.");
	}

	private void assertLikelyUserNameVisible() {
		final String configuredName = readSetting("saleads.userName", "SALEADS_USER_NAME", "");
		if (configuredName != null && !configuredName.trim().isEmpty()) {
			assertVisibleTextAny(configuredName.trim());
			return;
		}

		final List<String> excludedTexts = Arrays.asList("informacion general", "información general", "business plan",
				"cambiar plan", "detalles de la cuenta", "cuenta creada", "estado activo", "idioma seleccionado",
				"tus negocios", "seccion legal", "sección legal");

		final List<WebElement> candidates = driver.findElements(By.xpath("//h1|//h2|//h3|//p|//span"));
		for (final WebElement candidate : candidates) {
			if (!candidate.isDisplayed()) {
				continue;
			}
			final String text = candidate.getText();
			final String normalizedText = normalizeForCompare(text);
			if (normalizedText.contains("@") || normalizedText.length() < 5 || excludedTexts.contains(normalizedText)) {
				continue;
			}
			if (text.trim().matches("(?i).*[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}.*")) {
				return;
			}
		}
		Assert.fail("Could not validate a visible user name. Provide SALEADS_USER_NAME for strict validation.");
	}

	private void assertBusinessListVisible() {
		final WebElement sectionTitle = waitForVisibleText("Tus Negocios");
		if (sectionTitle == null) {
			Assert.fail("Missing 'Tus Negocios' section heading.");
			return;
		}

		final WebElement sectionContainer = sectionTitle.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		final List<WebElement> listCandidates = sectionContainer
				.findElements(By.xpath(".//li[normalize-space()] | .//tr[normalize-space()] | .//*[@role='row']"));
		if (!listCandidates.isEmpty()) {
			return;
		}

		final String sectionText = normalizeForCompare(sectionContainer.getText());
		if (sectionText.length() < 35) {
			Assert.fail("Business list content is not visible in 'Tus Negocios'.");
		}
	}

	private void assertLegalContentVisible() {
		final List<WebElement> contentCandidates = driver.findElements(By.xpath("//p|//article|//section"));
		for (final WebElement candidate : contentCandidates) {
			if (candidate.isDisplayed() && normalizeForCompare(candidate.getText()).length() > 120) {
				return;
			}
		}
		Assert.fail("Legal content text is not visible.");
	}

	private void assertAnyVisible(final By... selectors) {
		try {
			new WebDriverWait(driver, DEFAULT_TIMEOUT).until(drv -> {
				for (final By selector : selectors) {
					final WebElement element = firstDisplayed(drv.findElements(selector));
					if (element != null) {
						return true;
					}
				}
				return false;
			});
		} catch (final TimeoutException timeoutException) {
			Assert.fail("Expected at least one visible element from selectors: " + Arrays.toString(selectors));
		}
	}

	private WebElement findFirstVisible(final By... selectors) {
		try {
			return new WebDriverWait(driver, DEFAULT_TIMEOUT).until(drv -> {
				for (final By selector : selectors) {
					final WebElement element = firstDisplayed(drv.findElements(selector));
					if (element != null) {
						return element;
					}
				}
				return null;
			});
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private WebElement findFirstVisibleNoWait(final By... selectors) {
		for (final By selector : selectors) {
			final WebElement element = firstDisplayed(driver.findElements(selector));
			if (element != null) {
				return element;
			}
		}
		return null;
	}

	private WebElement firstDisplayed(final List<WebElement> elements) {
		for (final WebElement element : elements) {
			try {
				if (element.isDisplayed()) {
					return element;
				}
			} catch (final NoSuchElementException ignored) {
				// Ignore detached nodes.
			}
		}
		return null;
	}

	private void safeClick(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private Path takeScreenshot(final String name) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String fileName = String.format(Locale.ROOT, "%s_%s.png", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")),
				sanitizeName(name));
		final Path target = evidenceDir.resolve(fileName);
		Files.copy(source.toPath(), target);
		return target;
	}

	private String sanitizeName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
	}

	private String readSetting(final String sysProperty, final String envVar, final String fallback) {
		final String fromSystem = System.getProperty(sysProperty);
		if (fromSystem != null && !fromSystem.trim().isEmpty()) {
			return fromSystem.trim();
		}
		final String fromEnv = System.getenv(envVar);
		if (fromEnv != null && !fromEnv.trim().isEmpty()) {
			return fromEnv.trim();
		}
		return fallback;
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder xpath = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			xpath.append("'").append(parts[i]).append("'");
			if (i != parts.length - 1) {
				xpath.append(", \"'\", ");
			}
		}
		xpath.append(")");
		return xpath.toString();
	}

	private String normalizeForCompare(final String value) {
		return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.trim()
				.toLowerCase(Locale.ROOT);
	}

	@FunctionalInterface
	private interface CheckedAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String failureMessage;

		private StepResult(final boolean passed, final String failureMessage) {
			this.passed = passed;
			this.failureMessage = failureMessage;
		}

		private static StepResult passed() {
			return new StepResult(true, null);
		}

		private static StepResult failed(final Throwable throwable) {
			final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getMessage();
			return new StepResult(false, message);
		}
	}
}
