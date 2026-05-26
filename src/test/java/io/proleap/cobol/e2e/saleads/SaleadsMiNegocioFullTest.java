package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioFullTest {

	private interface StepAction {
		void run() throws Exception;
	}

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String STEP_INFORMACION_GENERAL = "Información General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_POLITICA = "Política de Privacidad";

	private static final List<String> STEP_ORDER = Arrays.asList(STEP_LOGIN, STEP_MI_NEGOCIO_MENU,
			STEP_AGREGAR_NEGOCIO_MODAL, STEP_ADMINISTRAR_NEGOCIOS_VIEW, STEP_INFORMACION_GENERAL,
			STEP_DETALLES_CUENTA, STEP_TUS_NEGOCIOS, STEP_TERMINOS, STEP_POLITICA);

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
			Pattern.CASE_INSENSITIVE);

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private Path finalReportPath;

	private String termsAndConditionsUrl;
	private String privacyPolicyUrl;
	private String applicationWindowHandle;

	@Before
	public void setUp() throws IOException {
		initializeReport();

		final boolean enabled = Boolean.parseBoolean(readConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"Set -Dsaleads.e2e.enabled=true (or SALEADS_E2E_ENABLED=true) to run the SaleADS E2E workflow test.",
				enabled);

		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", "");

		driver = createDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(getTimeoutSeconds()));
		driver.manage().window().setSize(new Dimension(1920, 1080));

		evidenceDirectory = Files
				.createDirectories(Path.of("target", "surefire-reports", "saleads_mi_negocio_full_test",
						LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));
		finalReportPath = evidenceDirectory.resolve("final-report.txt");

		if (!loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		executeStep(STEP_LOGIN, this::loginWithGoogle);
		executeStep(STEP_MI_NEGOCIO_MENU, this::openMiNegocioMenu);
		executeStep(STEP_AGREGAR_NEGOCIO_MODAL, this::validateAgregarNegocioModal);
		executeStep(STEP_ADMINISTRAR_NEGOCIOS_VIEW, this::openAdministrarNegocios);
		executeStep(STEP_INFORMACION_GENERAL, this::validateInformacionGeneral);
		executeStep(STEP_DETALLES_CUENTA, this::validateDetallesCuenta);
		executeStep(STEP_TUS_NEGOCIOS, this::validateTusNegocios);
		executeStep(STEP_TERMINOS, this::validateTerminosYCondiciones);
		executeStep(STEP_POLITICA, this::validatePoliticaDePrivacidad);

		writeFinalReport();
		assertNoFailedSteps();
	}

	private void initializeReport() {
		stepStatus.clear();
		stepDetails.clear();

		for (final String step : STEP_ORDER) {
			stepStatus.put(step, "NOT_RUN");
		}
	}

	private void executeStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepStatus.put(stepName, "PASS");
		} catch (final Exception | AssertionError e) {
			stepStatus.put(stepName, "FAIL");
			stepDetails.put(stepName, e.getClass().getSimpleName() + ": " + safeMessage(e));
		}
	}

	private WebDriver createDriver() {
		final String browser = readConfig("saleads.browser", "SALEADS_BROWSER", "chrome").toLowerCase();
		final boolean headless = Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"));

		switch (browser) {
		case "firefox":
			WebDriverManager.firefoxdriver().setup();
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			return new FirefoxDriver(firefoxOptions);
		case "edge":
			WebDriverManager.edgedriver().setup();
			final EdgeOptions edgeOptions = new EdgeOptions();
			if (headless) {
				edgeOptions.addArguments("--headless=new");
			}
			return new EdgeDriver(edgeOptions);
		case "chrome":
		default:
			WebDriverManager.chromedriver().setup();
			final ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			return new ChromeDriver(chromeOptions);
		}
	}

	private void loginWithGoogle() throws IOException {
		if (!(isTextVisible("Mi Negocio") && isSidebarVisible())) {
			clickFirstVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
					"Continuar con Google", "Acceder con Google", "Login with Google");
			waitForUiToLoad();

			if (isGoogleAccountSelectorVisible()) {
				clickFirstVisibleText(readConfig("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT",
						"juanlucasbarbiergarzon@gmail.com"));
				waitForUiToLoad();
			}
		}

		waitUntilAnyTextVisible("Negocio", "Mi Negocio");
		assertTrue("Expected left sidebar navigation to be visible after login.", isSidebarVisible());
		applicationWindowHandle = driver.getWindowHandle();
		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws IOException {
		waitUntilAnyTextVisible("Negocio", "Mi Negocio");

		if (isTextVisible("Negocio")) {
			clickVisibleText("Negocio");
		}

		clickVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertTextVisible("Crear Nuevo Negocio");
		assertInputVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final WebElement input = findFirstDisplayed(By.xpath(
				"//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')] | //input[contains(@aria-label, 'Nombre del Negocio')]"));
		if (input != null) {
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
		}

		clickVisibleText("Cancelar");
		waitUntilTextNotVisible("Crear Nuevo Negocio");
	}

	private void openAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		clickVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		final String sectionText = section.getText();

		assertTrue("Expected user email to be visible in Información General.", EMAIL_PATTERN.matcher(sectionText).find());
		assertLikelyUserNameVisible(sectionText);
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
	}

	private void validateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final String sectionText = section.getText();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");

		final List<WebElement> businessItems = section.findElements(By.xpath(
				".//li | .//tr[td] | .//*[contains(@class, 'business')] | .//*[contains(@class, 'negocio')]"));
		final boolean hasVisibleList = businessItems.stream().anyMatch(WebElement::isDisplayed);
		final boolean hasRichSectionText = sectionText.split("\\R").length >= 4;

		assertTrue("Expected a visible business list in Tus Negocios section.", hasVisibleList || hasRichSectionText);
	}

	private void validateTerminosYCondiciones() throws IOException {
		termsAndConditionsUrl = openLegalDocument("Términos y Condiciones", "Términos y Condiciones",
				"05-terminos-y-condiciones");
	}

	private void validatePoliticaDePrivacidad() throws IOException {
		privacyPolicyUrl = openLegalDocument("Política de Privacidad", "Política de Privacidad",
				"06-politica-de-privacidad");
	}

	private String openLegalDocument(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String appUrlBefore = driver.getCurrentUrl();
		final String appHandle = applicationWindowHandle == null ? driver.getWindowHandle() : applicationWindowHandle;
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickVisibleText(linkText);
		waitForUiToLoad();

		final String activeHandle = switchToNewTabIfPresent(handlesBefore, appUrlBefore);
		waitForUiToLoad();

		assertTextVisible(headingText);
		final String bodyText = driver.findElement(By.tagName("body")).getText().trim();
		assertTrue("Expected legal content text for page: " + headingText, bodyText.length() > 120);

		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (activeHandle != null && !activeHandle.equals(appHandle)) {
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else if (!appUrlBefore.equals(driver.getCurrentUrl())) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private String switchToNewTabIfPresent(final Set<String> handlesBefore, final String appUrlBefore) {
		try {
			wait.until(driverRef -> driverRef.getWindowHandles().size() > handlesBefore.size()
					|| !driverRef.getCurrentUrl().equals(appUrlBefore));
			final Set<String> handlesAfter = driver.getWindowHandles();

			if (handlesAfter.size() > handlesBefore.size()) {
				for (final String handle : handlesAfter) {
					if (!handlesBefore.contains(handle)) {
						driver.switchTo().window(handle);
						return handle;
					}
				}
			}

			return driver.getWindowHandle();
		} catch (final TimeoutException e) {
			return driver.getWindowHandle();
		}
	}

	private void assertTextVisible(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]")));
	}

	private void assertInputVisible(final String labelText) {
		final By locator = By.xpath(
				"//label[contains(normalize-space(), " + xpathLiteral(labelText) + ")]/following::input[1] | //input[contains(@placeholder, "
						+ xpathLiteral(labelText) + ")] | //input[contains(@aria-label, " + xpathLiteral(labelText) + ")]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void waitUntilAnyTextVisible(final String... texts) {
		wait.until(driverRef -> {
			for (final String text : texts) {
				if (isTextVisible(text)) {
					return true;
				}
			}

			return false;
		});
	}

	private void waitUntilTextNotVisible(final String text) {
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]")));
	}

	private void clickVisibleText(final String text) {
		final WebElement element = wait
				.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]")));
		clickElement(element);
		waitForUiToLoad();
	}

	private void clickFirstVisibleText(final String... candidates) {
		for (final String candidate : candidates) {
			final WebElement candidateElement = findFirstDisplayed(
					By.xpath("//*[normalize-space()=" + xpathLiteral(candidate) + "]"));

			if (candidateElement != null) {
				clickElement(candidateElement);
				waitForUiToLoad();
				return;
			}
		}

		throw new NoSuchElementException("None of the expected visible texts were found: " + Arrays.toString(candidates));
	}

	private void clickElement(final WebElement element) {
		try {
			final By clickableLocator = By.xpath(
					"./ancestor-or-self::*[self::button or self::a or @role='button' or @tabindex='0'][1]");
			final WebElement clickable = element.findElement(clickableLocator);
			wait.until(ExpectedConditions.elementToBeClickable(clickable)).click();
		} catch (final Exception firstFailure) {
			try {
				wait.until(ExpectedConditions.elementToBeClickable(element)).click();
			} catch (final Exception clickFailure) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			}
		}
	}

	private boolean isGoogleAccountSelectorVisible() {
		return driver.getCurrentUrl().contains("accounts.google.com")
				|| findFirstDisplayed(By.xpath("//*[contains(text(), '@gmail.com')]")) != null;
	}

	private boolean isTextVisible(final String text) {
		try {
			final List<WebElement> candidates = driver
					.findElements(By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]"));
			for (final WebElement element : candidates) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final Exception e) {
			return false;
		}
	}

	private boolean isSidebarVisible() {
		final List<WebElement> sidebarCandidates = driver.findElements(By.xpath("//aside | //nav"));
		for (final WebElement sidebar : sidebarCandidates) {
			if (sidebar.isDisplayed() && !sidebar.getText().isBlank()) {
				return true;
			}
		}

		return false;
	}

	private WebElement findSectionByHeading(final String headingText) {
		final WebElement headingElement = wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::span or self::p][normalize-space()="
						+ xpathLiteral(headingText) + "]")));

		final List<WebElement> ancestors = headingElement.findElements(By.xpath("./ancestor::*[self::section or self::div]"));
		for (final WebElement ancestor : ancestors) {
			if (ancestor.isDisplayed() && ancestor.getText().contains(headingText) && ancestor.getText().length() > 40) {
				return ancestor;
			}
		}

		return headingElement;
	}

	private void assertLikelyUserNameVisible(final String sectionText) {
		final List<String> ignoredTokens = Arrays.asList("Información General", "BUSINESS PLAN", "Cambiar Plan", "Plan",
				"Cuenta", "Estado", "Idioma", "Negocios");

		for (final String rawLine : sectionText.split("\\R")) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (ignoredTokens.stream().anyMatch(line::contains)) {
				continue;
			}
			if (line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return;
			}
		}

		throw new AssertionError("Expected a user name to be visible in Información General.");
	}

	private void takeScreenshot(final String screenshotName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDirectory.resolve(screenshotName + ".png");
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void waitForUiToLoad() {
		try {
			wait.until(driverRef -> "complete"
					.equals(((JavascriptExecutor) driverRef).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Keep moving even if browser blocks readyState reads.
		}

		try {
			wait.until(ExpectedConditions.invisibilityOfElementLocated(
					By.cssSelector(".loading,.loader,.spinner,[aria-busy='true'],[data-testid='loading']")));
		} catch (final TimeoutException ignored) {
			// Some views keep background indicators mounted.
		}
	}

	private WebElement findFirstDisplayed(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			try {
				if (element.isDisplayed()) {
					return element;
				}
			} catch (final Exception ignored) {
				// Ignore stale or detached elements and continue searching.
			}
		}

		return null;
	}

	private String xpathLiteral(final String input) {
		if (!input.contains("'")) {
			return "'" + input + "'";
		}

		if (!input.contains("\"")) {
			return "\"" + input + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final String[] parts = input.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(",\"'\",");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private String readConfig(final String propertyKey, final String envKey, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private long getTimeoutSeconds() {
		final String configuredTimeout = readConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", "25");
		try {
			return Long.parseLong(configuredTimeout);
		} catch (final NumberFormatException e) {
			return 25L;
		}
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("timestamp_utc=" + Instant.now());
		lines.add("evidence_directory=" + evidenceDirectory.toAbsolutePath());
		lines.add("");
		lines.add("Validation results:");
		for (final String step : STEP_ORDER) {
			final String status = stepStatus.getOrDefault(step, "NOT_RUN");
			lines.add(step + ": " + status);
			if (stepDetails.containsKey(step)) {
				lines.add("  detail: " + stepDetails.get(step));
			}
		}
		lines.add("");
		lines.add("Términos y Condiciones URL: " + (termsAndConditionsUrl == null ? "N/A" : termsAndConditionsUrl));
		lines.add("Política de Privacidad URL: " + (privacyPolicyUrl == null ? "N/A" : privacyPolicyUrl));

		Files.write(finalReportPath, lines, StandardCharsets.UTF_8);

		for (final String line : lines) {
			System.out.println(line);
		}
	}

	private void assertNoFailedSteps() {
		final List<String> failedSteps = new ArrayList<>();
		for (final String step : STEP_ORDER) {
			if ("FAIL".equals(stepStatus.get(step))) {
				failedSteps.add(step);
			}
		}

		assertTrue(
				"One or more SaleADS workflow validations failed: " + failedSteps + ". Review " + finalReportPath,
				failedSteps.isEmpty());
	}

	private String safeMessage(final Throwable throwable) {
		final String message = throwable.getMessage();
		return message == null || message.isBlank() ? "(no error message)" : message;
	}
}
