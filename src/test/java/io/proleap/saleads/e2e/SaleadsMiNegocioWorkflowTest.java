package io.proleap.saleads.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * End-to-end workflow for SaleADS Mi Negocio module:
 * - Works with any SaleADS environment by using saleads.login.url / SALEADS_LOGIN_URL as runtime input.
 * - Uses visible text locators first and waits for UI after each interaction.
 * - Captures screenshots for required checkpoints.
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration LEGAL_TIMEOUT = Duration.ofSeconds(45);
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String OPTIONAL_BUSINESS_NAME = "Negocio Prueba Automatizacion";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactDir;
	private String appWindowHandle;

	private final LinkedHashMap<String, Boolean> reportResults = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> reportDetails = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue("Enable this test with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true",
				Boolean.parseBoolean(readConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false")));
		Assume.assumeTrue("Set saleads.login.url or SALEADS_LOGIN_URL for target SaleADS environment",
				!readConfig("saleads.login.url", "SALEADS_LOGIN_URL", "").isBlank());

		artifactDir = Path.of("target", "saleads-e2e");
		Files.createDirectories(artifactDir);

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);
		driver.manage().window().maximize();
	}

	@After
	public void tearDown() {
		writeFinalReportFile();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep("Login", this::loginWithGoogleAndValidateDashboard);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAndValidateAdministrarNegocios);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", this::validateTerminosYCondiciones);
		runStep("Política de Privacidad", this::validatePoliticaPrivacidad);

		String reportSummary = buildReportSummary();
		System.out.println(reportSummary);
		Assert.assertTrue("SaleADS Mi Negocio workflow has failures:\n" + reportSummary, allStepsPassed());
	}

	private void loginWithGoogleAndValidateDashboard() throws Exception {
		navigateToLoginPage();
		appWindowHandle = driver.getWindowHandle();
		Set<String> handlesBeforeLogin = driver.getWindowHandles();

		clickFirstAvailableText(Arrays.asList("Sign in with Google", "Iniciar sesion con Google", "Continuar con Google",
				"Login con Google", "Ingresar con Google"), "Google login button");
		waitForUiLoad();

		String googleHandle = waitForNewTabIfAny(handlesBeforeLogin, Duration.ofSeconds(15));
		if (googleHandle != null) {
			driver.switchTo().window(googleHandle);
			waitForUiLoad();
		}

		selectGoogleAccountIfPrompted(GOOGLE_ACCOUNT_EMAIL);
		waitForUiLoad();

		if (!driver.getWindowHandle().equals(appWindowHandle) && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForUiLoad();
		}

		assertAnyTextVisible(textOptions("Negocio"), "main application interface");
		assertTrue(isAnyVisible(Arrays.asList(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar')]"))),
				"left sidebar navigation is visible");

		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws Exception {
		clickFirstAvailableText(textOptions("Mi Negocio"), "Mi Negocio menu");
		assertAnyTextVisible(textOptions("Agregar Negocio"), "expanded submenu");
		assertAnyTextVisible(textOptions("Administrar Negocios"), "expanded submenu");
		takeScreenshot("02-mi-negocio-expanded-menu");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickFirstAvailableText(textOptions("Agregar Negocio"), "Agregar Negocio option");
		assertAnyTextVisible(textOptions("Crear Nuevo Negocio"), "Crear Nuevo Negocio modal");
		assertTrue(isAnyVisible(Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"))),
				"Nombre del Negocio input field exists");
		assertAnyTextVisible(textOptions("Tienes 2 de 3 negocios"), "business count in modal");
		assertAnyTextVisible(textOptions("Cancelar"), "Cancelar button");
		assertAnyTextVisible(textOptions("Crear Negocio"), "Crear Negocio button");
		takeScreenshot("03-agregar-negocio-modal");

		WebElement input = firstVisibleElement(Arrays.asList(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]")));
		if (input != null) {
			input.click();
			waitForUiLoad();
			input.clear();
			input.sendKeys(OPTIONAL_BUSINESS_NAME);
		}

		clickFirstAvailableText(textOptions("Cancelar"), "Cerrar modal con Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio') or contains(normalize-space(.), 'Crear Nuevo Negocio')]")));
		waitForUiLoad();
	}

	private void openAndValidateAdministrarNegocios() throws Exception {
		if (!isAnyTextVisible(textOptions("Administrar Negocios"))) {
			clickFirstAvailableText(textOptions("Mi Negocio"), "re-expand Mi Negocio menu");
		}
		clickFirstAvailableText(textOptions("Administrar Negocios"), "Administrar Negocios option");
		waitForUiLoad();

		assertAnyTextVisible(textOptions("Información General"), "account page section");
		assertAnyTextVisible(textOptions("Detalles de la Cuenta"), "account page section");
		assertAnyTextVisible(textOptions("Tus Negocios"), "account page section");
		assertAnyTextVisible(textOptions("Sección Legal"), "account page section");
		takeFullPageScreenshot("04-administrar-negocios-view-full");
	}

	private void validateInformacionGeneral() {
		WebElement section = findSectionByHeadings(textOptions("Información General"));
		assertTrue(section != null, "Información General section exists");
		String text = section == null ? "" : section.getText();

		assertTrue(hasEmail(text), "user email is visible");
		assertTrue(hasLikelyUserName(text), "user name is visible");
		assertTrue(normalizeForComparison(text).contains(normalizeForComparison("BUSINESS PLAN")),
				"BUSINESS PLAN text is visible");

		WebElement cambiarPlan = firstVisibleElement(Arrays.asList(
				By.xpath("//button[contains(normalize-space(.), 'Cambiar Plan')]"),
				By.xpath("//a[contains(normalize-space(.), 'Cambiar Plan')]"),
				By.xpath("//*[contains(@role, 'button') and contains(normalize-space(.), 'Cambiar Plan')]")));
		assertTrue(cambiarPlan != null, "Cambiar Plan button is visible");
	}

	private void validateDetallesCuenta() {
		WebElement section = findSectionByHeadings(textOptions("Detalles de la Cuenta"));
		assertTrue(section != null, "Detalles de la Cuenta section exists");
		String text = section == null ? "" : section.getText();

		assertTrue(containsAll(text, Arrays.asList("Cuenta creada", "Estado activo", "Idioma seleccionado")),
				"Cuenta creada, Estado activo and Idioma seleccionado are visible");
	}

	private void validateTusNegocios() {
		WebElement section = findSectionByHeadings(textOptions("Tus Negocios"));
		assertTrue(section != null, "Tus Negocios section exists");
		String text = section == null ? "" : section.getText();

		assertTrue(text.trim().length() > "Tus Negocios".length(), "business list is visible");
		assertTrue(isAnyVisible(Arrays.asList(
				By.xpath("//button[contains(normalize-space(.), 'Agregar Negocio')]"),
				By.xpath("//a[contains(normalize-space(.), 'Agregar Negocio')]"))),
				"Agregar Negocio button exists");
		assertTrue(containsAny(normalizeForComparison(text), textOptions("Tienes 2 de 3 negocios")),
				"Tienes 2 de 3 negocios text is visible");
	}

	private void validateTerminosYCondiciones() throws Exception {
		validateLegalLink(textOptions("Términos y Condiciones"), textOptions("Términos y Condiciones"),
				"05-terminos-y-condiciones", "Términos y Condiciones");
	}

	private void validatePoliticaPrivacidad() throws Exception {
		validateLegalLink(textOptions("Política de Privacidad"), textOptions("Política de Privacidad"),
				"06-politica-de-privacidad", "Política de Privacidad");
	}

	private void validateLegalLink(final List<String> linkTexts, final List<String> headingTexts, final String screenshotName,
			final String legalReportKey) throws Exception {
		Set<String> handlesBefore = driver.getWindowHandles();
		String currentHandle = driver.getWindowHandle();
		String urlBefore = driver.getCurrentUrl();

		clickFirstAvailableText(linkTexts, "legal link");

		String switchedHandle = waitForNewTabIfAny(handlesBefore, LEGAL_TIMEOUT);
		boolean openedNewTab = switchedHandle != null;
		if (openedNewTab) {
			driver.switchTo().window(switchedHandle);
		}

		waitForUiLoad();
		assertAnyTextVisible(headingTexts, "legal page heading");

		String pageText = driver.findElement(By.tagName("body")).getText();
		assertTrue(pageText.length() > 120, "legal content text is visible");

		takeScreenshot(screenshotName);
		legalUrls.put(legalReportKey, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(currentHandle);
		} else if (!driver.getCurrentUrl().equals(urlBefore)) {
			driver.navigate().back();
		}

		driver.switchTo().window(appWindowHandle);
		waitForUiLoad();
	}

	private WebDriver createDriver() throws MalformedURLException {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-notifications");
		options.addArguments("--window-size=1920,1080");
		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "false"))) {
			options.addArguments("--headless=new");
		}

		String remoteUrl = readConfig("saleads.remote.url", "SALEADS_REMOTE_URL", "");
		if (!remoteUrl.isBlank()) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}

		WebDriverManager.chromedriver().setup();
		return new ChromeDriver(options);
	}

	private void navigateToLoginPage() {
		String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", "");
		driver.get(loginUrl);
		waitForUiLoad();
	}

	private void selectGoogleAccountIfPrompted(final String email) {
		if (isAnyTextVisible(textOptions(email))) {
			try {
				clickText(email, "Google account selector");
				waitForUiLoad();
			} catch (Exception ignored) {
				// Optional selector: not all environments show account chooser.
			}
		}
	}

	private void clickFirstAvailableText(final List<String> texts, final String actionDescription) throws Exception {
		Exception latest = null;
		for (String text : texts) {
			try {
				clickText(text, actionDescription);
				return;
			} catch (Exception ex) {
				latest = ex;
			}
		}
		throw new IllegalStateException("Could not click any expected text for " + actionDescription + ": " + texts,
				latest);
	}

	private void clickText(final String text, final String actionDescription) throws Exception {
		WebElement clickable = waitForClickableByTexts(textOptions(text));
		scrollIntoView(clickable);
		try {
			clickable.click();
		} catch (Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
		}
		waitForUiLoad();
	}

	private WebElement waitForClickableByTexts(final List<String> texts) {
		for (String text : texts) {
			List<By> candidates = Arrays.asList(
					By.xpath("//button[normalize-space()='" + text + "' or contains(normalize-space(.), '" + text + "')]"),
					By.xpath("//a[normalize-space()='" + text + "' or contains(normalize-space(.), '" + text + "')]"),
					By.xpath("//*[contains(@role, 'button') and (normalize-space()='" + text + "' or contains(normalize-space(.), '" + text + "'))]"),
					By.xpath("//*[normalize-space()='" + text + "']"),
					By.xpath("//*[contains(normalize-space(.), '" + text + "')]"));

			for (By locator : candidates) {
				try {
					return wait.until(ExpectedConditions.elementToBeClickable(locator));
				} catch (Exception ignored) {
					// Try next locator.
				}
			}
		}

		throw new IllegalStateException("Unable to find clickable element with expected texts: " + texts);
	}

	private WebElement firstVisibleElement(final List<By> candidates) {
		for (By by : candidates) {
			List<WebElement> elements = driver.findElements(by);
			for (WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		return null;
	}

	private boolean isAnyVisible(final List<By> candidates) {
		return firstVisibleElement(candidates) != null;
	}

	private void assertAnyTextVisible(final List<String> texts, final String expectation) {
		assertTrue(isAnyTextVisible(texts), expectation + " (missing any of: " + texts + ")");
	}

	private boolean isAnyTextVisible(final List<String> texts) {
		for (String text : texts) {
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(
						By.xpath("//*[contains(normalize-space(.), '" + text + "')]")));
				return true;
			} catch (Exception ignored) {
				// keep searching
			}
		}
		return false;
	}

	private WebElement findSectionByHeadings(final List<String> headings) {
		for (String heading : headings) {
			List<By> candidates = Arrays.asList(
					By.xpath("//*[self::section or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(.), '" + heading + "')]]"),
					By.xpath("//*[contains(normalize-space(.), '" + heading + "')]/ancestor::*[self::section or self::div][1]"));
			WebElement section = firstVisibleElement(candidates);
			if (section != null) {
				return section;
			}
		}
		return null;
	}

	private boolean containsAll(final String text, final List<String> expectedTexts) {
		String normalized = normalizeForComparison(text);
		for (String expected : expectedTexts) {
			if (!containsAny(normalized, textOptions(expected))) {
				return false;
			}
		}
		return true;
	}

	private boolean hasEmail(final String text) {
		return EMAIL_PATTERN.matcher(text).find();
	}

	private boolean hasLikelyUserName(final String text) {
		List<String> lines = Arrays.stream(text.split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isBlank())
				.filter(line -> !line.equalsIgnoreCase("Informacion General"))
				.filter(line -> !line.equalsIgnoreCase("BUSINESS PLAN"))
				.filter(line -> !line.equalsIgnoreCase("Cambiar Plan"))
				.filter(line -> !line.contains("@"))
				.collect(Collectors.toList());

		return lines.stream().anyMatch(line -> line.replaceAll("[^A-Za-zÁÉÍÓÚáéíóúÑñ ]", "").trim().split("\\s+").length >= 2);
	}

	private List<String> textOptions(final String text) {
		LinkedHashSet<String> options = new LinkedHashSet<>();
		options.add(text);

		String deaccented = removeAccents(text);
		options.add(deaccented);
		options.add(text.replace("ó", "o").replace("á", "a").replace("é", "e").replace("í", "i").replace("ú", "u")
				.replace("Ó", "O").replace("Á", "A").replace("É", "E").replace("Í", "I").replace("Ú", "U"));

		return new ArrayList<>(options).stream().filter(option -> option != null && !option.isBlank())
				.collect(Collectors.toList());
	}

	private String removeAccents(final String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	private boolean containsAny(final String haystack, final List<String> needles) {
		for (String needle : needles) {
			if (normalizeForComparison(haystack).contains(normalizeForComparison(needle))) {
				return true;
			}
		}
		return false;
	}

	private String waitForNewTabIfAny(final Set<String> handlesBefore, final Duration timeout) {
		WebDriverWait legalWait = new WebDriverWait(driver, timeout);
		try {
			legalWait.until((ExpectedCondition<Boolean>) d -> d != null && d.getWindowHandles().size() > handlesBefore.size());
		} catch (Exception ignored) {
			return null;
		}

		Set<String> handlesAfter = driver.getWindowHandles();
		for (String handle : handlesAfter) {
			if (!handlesBefore.contains(handle)) {
				return handle;
			}
		}
		return null;
	}

	private void waitForUiLoad() {
		wait.until((ExpectedCondition<Boolean>) wd -> {
			Object readyState = ((JavascriptExecutor) wd).executeScript("return document.readyState");
			return "complete".equals(readyState);
		});
		waitForMilliseconds(500);
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void waitForMilliseconds(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String name) throws IOException {
		Path target = artifactDir.resolve(name + ".png");
		File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private void takeFullPageScreenshot(final String name) throws IOException {
		Dimension original = driver.manage().window().getSize();
		try {
			Long fullHeight = (Long) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			int targetHeight = Math.min(4000, Math.max(original.getHeight(), fullHeight == null ? 1080 : fullHeight.intValue()));
			driver.manage().window().setSize(new Dimension(original.getWidth(), targetHeight));
			waitForMilliseconds(300);
			takeScreenshot(name);
		} finally {
			driver.manage().window().setSize(original);
		}
	}

	private void runStep(final String stepName, final StepAction stepAction) {
		try {
			stepAction.run();
			reportResults.put(stepName, true);
			reportDetails.put(stepName, "PASS");
		} catch (Throwable throwable) {
			reportResults.put(stepName, false);
			String detail = throwable.getMessage() == null ? throwable.toString() : throwable.getMessage();
			reportDetails.put(stepName, detail);
			captureStepFailure(stepName);
		}
	}

	private void captureStepFailure(final String stepName) {
		try {
			String safeName = stepName.toLowerCase().replace(" ", "-");
			takeScreenshot("failure-" + safeName);
		} catch (Exception ignored) {
			// Best effort evidence on failures.
		}
	}

	private boolean allStepsPassed() {
		return reportResults.values().stream().allMatch(Boolean::booleanValue);
	}

	private String buildReportSummary() {
		List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio workflow report (" + Instant.now().toString() + ")");

		List<String> orderedFields = Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad");

		for (String field : orderedFields) {
			boolean pass = reportResults.getOrDefault(field, false);
			String detail = reportDetails.getOrDefault(field, "Not executed");
			lines.add("- " + field + ": " + (pass ? "PASS" : "FAIL") + " | " + detail);
		}

		if (!legalUrls.isEmpty()) {
			lines.add("Legal final URLs:");
			for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
				lines.add("  - " + entry.getKey() + ": " + entry.getValue());
			}
		}

		lines.add("Screenshots directory: " + artifactDir.toAbsolutePath());
		return String.join(System.lineSeparator(), lines);
	}

	private void writeFinalReportFile() {
		if (artifactDir == null) {
			return;
		}

		try {
			String report = buildReportSummary();
			Files.writeString(artifactDir.resolve("final-report.txt"), report + System.lineSeparator());
		} catch (Exception ignored) {
			// Avoid masking test result due to report I/O issues.
		}
	}

	private String readConfig(final String propertyName, final String envName, final String defaultValue) {
		String fromProperty = System.getProperty(propertyName);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}

		String fromEnv = System.getenv(envName);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		return defaultValue;
	}

	private String normalize(final String input) {
		return input == null ? "" : input.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	private String normalizeForComparison(final String input) {
		return removeAccents(normalize(input)).toLowerCase();
	}

	private void assertTrue(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
