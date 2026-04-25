package io.proleap.cobol.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration LONG_TIMEOUT = Duration.ofSeconds(90);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad"
	);

	private final Map<String, Boolean> reportStatus = new LinkedHashMap<>();
	private final Map<String, String> reportDetail = new LinkedHashMap<>();
	private final Map<String, String> evidenceUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactDir;
	private String configuredUrl;

	@Before
	public void setUp() throws IOException {
		for (String field : REPORT_FIELDS) {
			reportStatus.put(field, Boolean.FALSE);
			reportDetail.put(field, "Not executed.");
		}

		final String runId = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
		artifactDir = Path.of("target", "saleads-mi-negocio-artifacts", runId);
		Files.createDirectories(artifactDir);

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(valueOrDefault(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "false"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = createDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		configuredUrl = firstNonBlank(System.getProperty("saleads.url"), System.getenv("SALEADS_URL"));
		if (configuredUrl != null) {
			driver.get(configuredUrl);
		} else {
			final String currentUrl = safeCurrentUrl();
			if (currentUrl == null || currentUrl.startsWith("data:") || "about:blank".equals(currentUrl)) {
				throw new IllegalStateException(
						"Browser is not on a SaleADS login page. Provide -Dsaleads.url=<environment login URL> or SALEADS_URL.");
			}
		}
		waitForUiLoad();
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		boolean continueFlow = true;

		continueFlow = executeStep("Login", continueFlow, this::stepLoginWithGoogle);
		continueFlow = executeStep("Mi Negocio menu", continueFlow, this::stepOpenMiNegocioMenu);
		continueFlow = executeStep("Agregar Negocio modal", continueFlow, this::stepValidateAgregarNegocioModal);
		continueFlow = executeStep("Administrar Negocios view", continueFlow, this::stepOpenAdministrarNegocios);
		continueFlow = executeStep("Información General", continueFlow, this::stepValidateInformacionGeneral);
		continueFlow = executeStep("Detalles de la Cuenta", continueFlow, this::stepValidateDetallesCuenta);
		continueFlow = executeStep("Tus Negocios", continueFlow, this::stepValidateTusNegocios);
		continueFlow = executeStep("Términos y Condiciones", continueFlow, this::stepValidateTerminos);
		executeStep("Política de Privacidad", continueFlow, this::stepValidatePoliticaPrivacidad);

		Assert.assertTrue("One or more SaleADS validations failed. See target/saleads-mi-negocio-artifacts report.",
				reportStatus.values().stream().allMatch(Boolean.TRUE::equals));
	}

	private boolean executeStep(final String field, final boolean continueFlow, final StepAction action) throws IOException {
		if (!continueFlow) {
			markFail(field, "Skipped because a previous step failed.");
			return false;
		}

		try {
			action.run();
			markPass(field);
			return true;
		} catch (Exception ex) {
			markFail(field, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
			takeScreenshot("failed-" + sanitizeFileName(field));
			return false;
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeLogin = new LinkedHashSet<>(driver.getWindowHandles());

		clickByAnyVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiLoad();

		selectGoogleAccountIfPrompted(handlesBeforeLogin, appWindow, GOOGLE_ACCOUNT_EMAIL);

		waitForAnyVisibleText(LONG_TIMEOUT, "Negocio", "Mi Negocio", "Dashboard", "Inicio");
		assertSidebarVisible();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		// If there is a top-level "Negocio" section, click it first.
		clickIfVisibleText("Negocio");
		clickByAnyVisibleText("Mi Negocio");
		waitForUiLoad();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByAnyVisibleText("Agregar Negocio");
		waitForUiLoad();

		final WebElement modalTitle = waitForVisibleElementWithText(DEFAULT_TIMEOUT, "Crear Nuevo Negocio");
		final WebElement modal = closestContainer(modalTitle);

		assertVisibleTextIn(modal, "Nombre del Negocio");
		assertVisibleTextIn(modal, "Tienes 2 de 3 negocios");
		assertVisibleTextIn(modal, "Cancelar");
		assertVisibleTextIn(modal, "Crear Negocio");

		final List<WebElement> inputs = modal.findElements(By.xpath(".//input"));
		Assert.assertFalse("Expected at least one input field in modal.", inputs.isEmpty());

		final WebElement nombreInput = inputs.get(0);
		nombreInput.click();
		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatización");
		waitForUiLoad();

		takeScreenshot("03-agregar-negocio-modal");

		clickByAnyVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isVisibleText("Administrar Negocios")) {
			clickByAnyVisibleText("Mi Negocio");
			waitForUiLoad();
		}

		clickByAnyVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");

		takeFullPageScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = sectionByHeading("Información General");
		final String sectionText = normalizeWhitespace(section.getText());

		Assert.assertTrue("Expected user email to be visible in Información General.",
				EMAIL_PATTERN.matcher(sectionText).find());

		final boolean hasNamedField = containsIgnoreCase(sectionText, "nombre")
				|| containsIgnoreCase(sectionText, "usuario")
				|| containsIgnoreCase(sectionText, "name");
		Assert.assertTrue("Expected user name to be visible in Información General.", hasNamedField);

		assertVisibleTextIn(section, "BUSINESS PLAN");
		assertVisibleTextIn(section, "Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = sectionByHeading("Detalles de la Cuenta");
		assertVisibleTextIn(section, "Cuenta creada");
		assertVisibleTextIn(section, "Estado activo");
		assertVisibleTextIn(section, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = sectionByHeading("Tus Negocios");
		assertVisibleTextIn(section, "Agregar Negocio");
		assertVisibleTextIn(section, "Tienes 2 de 3 negocios");

		final boolean hasListLikeContent = !section.findElements(By.xpath(".//li|.//tr|.//tbody/tr|.//*[contains(@class,'business')]")).isEmpty()
				|| normalizeWhitespace(section.getText()).split("\\s+").length > 12;
		Assert.assertTrue("Expected business list content to be visible in Tus Negocios.", hasListLikeContent);
	}

	private void stepValidateTerminos() throws IOException {
		final String finalUrl = openLegalLinkAndValidate("Términos y Condiciones", "Términos y Condiciones", "05-terminos-condiciones");
		evidenceUrls.put("Términos y Condiciones URL", finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		final String finalUrl = openLegalLinkAndValidate("Política de Privacidad", "Política de Privacidad", "06-politica-privacidad");
		evidenceUrls.put("Política de Privacidad URL", finalUrl);
	}

	private String openLegalLinkAndValidate(final String linkText, final String headingText, final String screenshotName) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String beforeUrl = safeCurrentUrl();

		clickByAnyVisibleText(linkText);
		waitForUiLoad();

		final String newHandle = waitForNewWindow(beforeHandles, Duration.ofSeconds(12));
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
			assertVisibleText(headingText);
			assertLegalContentVisible();
			takeScreenshot(screenshotName);
			final String finalUrl = safeCurrentUrl();
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
			assertVisibleText("Sección Legal");
			return finalUrl;
		}

		assertVisibleText(headingText);
		assertLegalContentVisible();
		takeScreenshot(screenshotName);
		final String finalUrl = safeCurrentUrl();

		if (beforeUrl != null && !beforeUrl.equals(finalUrl)) {
			driver.navigate().back();
			waitForUiLoad();
			assertVisibleText("Sección Legal");
		}
		return finalUrl;
	}

	private void selectGoogleAccountIfPrompted(final Set<String> beforeHandles, final String appWindow, final String accountEmail) {
		final Instant deadline = Instant.now().plus(Duration.ofSeconds(45));
		while (Instant.now().isBefore(deadline)) {
			if (isVisibleText("Negocio") || isVisibleText("Mi Negocio")) {
				switchToHandleIfPresent(appWindow);
				return;
			}

			final Set<String> allHandles = new LinkedHashSet<>(driver.getWindowHandles());
			final Set<String> candidateHandles = new LinkedHashSet<>(allHandles);
			candidateHandles.removeAll(beforeHandles);
			if (candidateHandles.isEmpty()) {
				candidateHandles.addAll(allHandles);
			}

			for (String handle : candidateHandles) {
				driver.switchTo().window(handle);
				final Optional<WebElement> maybeAccount = tryFindClickableByExactText(accountEmail, Duration.ofSeconds(2));
				if (maybeAccount.isPresent()) {
					maybeAccount.get().click();
					waitForUiLoad();
					switchToHandleIfPresent(appWindow);
					return;
				}
			}

			sleepMillis(800);
		}
		switchToHandleIfPresent(appWindow);
	}

	private WebDriver createDriver(final ChromeOptions options) {
		final String remoteUrl = firstNonBlank(System.getProperty("selenium.remoteUrl"), System.getenv("SELENIUM_REMOTE_URL"));
		if (remoteUrl != null) {
			try {
				return new RemoteWebDriver(new URL(remoteUrl), options);
			} catch (MalformedURLException ex) {
				throw new IllegalArgumentException("Invalid remote Selenium URL: " + remoteUrl, ex);
			}
		}
		WebDriverManager.chromedriver().setup();
		return new ChromeDriver(options);
	}

	private void assertSidebarVisible() {
		final List<WebElement> sidebars = driver.findElements(By.xpath("//aside|//nav"));
		for (WebElement element : sidebars) {
			if (element.isDisplayed()) {
				return;
			}
		}
		throw new AssertionError("Expected left sidebar navigation to be visible.");
	}

	private void assertLegalContentVisible() {
		final List<WebElement> contentNodes = driver.findElements(By.xpath("//p|//li|//article//div|//main//div"));
		for (WebElement node : contentNodes) {
			if (node.isDisplayed() && normalizeWhitespace(node.getText()).length() > 80) {
				return;
			}
		}
		throw new AssertionError("Expected legal content text to be visible.");
	}

	private WebElement sectionByHeading(final String heading) {
		final WebElement headingElement = waitForVisibleElementWithText(DEFAULT_TIMEOUT, heading);
		final List<By> containerLocators = Arrays.asList(
				By.xpath("./ancestor::section[1]"),
				By.xpath("./ancestor::article[1]"),
				By.xpath("./ancestor::div[contains(@class,'section')][1]"),
				By.xpath("./ancestor::div[1]")
		);

		for (By locator : containerLocators) {
			final List<WebElement> candidate = headingElement.findElements(locator);
			if (!candidate.isEmpty() && candidate.get(0).isDisplayed()) {
				return candidate.get(0);
			}
		}

		return closestContainer(headingElement);
	}

	private WebElement closestContainer(final WebElement element) {
		final List<By> locators = Arrays.asList(
				By.xpath("./ancestor::*[@role='dialog'][1]"),
				By.xpath("./ancestor::*[contains(@class,'modal')][1]"),
				By.xpath("./ancestor::section[1]"),
				By.xpath("./ancestor::div[1]")
		);

		for (By locator : locators) {
			final List<WebElement> containers = element.findElements(locator);
			if (!containers.isEmpty() && containers.get(0).isDisplayed()) {
				return containers.get(0);
			}
		}
		return element;
	}

	private void clickByAnyVisibleText(final String... texts) {
		final List<String> errors = new ArrayList<>();
		for (String text : texts) {
			final Optional<WebElement> found = tryFindClickableByExactText(text, Duration.ofSeconds(6));
			if (found.isPresent()) {
				found.get().click();
				waitForUiLoad();
				return;
			}
			errors.add(text);
		}

		throw new NoSuchElementException("Could not find clickable visible element by text. Tried: " + errors);
	}

	private void clickIfVisibleText(final String text) {
		final Optional<WebElement> maybe = tryFindClickableByExactText(text, Duration.ofSeconds(2));
		if (maybe.isPresent()) {
			maybe.get().click();
			waitForUiLoad();
		}
	}

	private Optional<WebElement> tryFindClickableByExactText(final String text, final Duration timeout) {
		final Instant deadline = Instant.now().plus(timeout);
		final By locator = By.xpath("//*[self::a or self::button or @role='button' or self::span or self::div]"
				+ "[contains(normalize-space(.), " + xpathLiteral(text) + ")]");

		while (Instant.now().isBefore(deadline)) {
			for (WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed() && element.isEnabled()) {
					return Optional.of(element);
				}
			}
			sleepMillis(200);
		}

		return Optional.empty();
	}

	private WebElement waitForVisibleElementWithText(final Duration timeout, final String text) {
		final Instant deadline = Instant.now().plus(timeout);
		final By locator = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");

		while (Instant.now().isBefore(deadline)) {
			for (WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			sleepMillis(250);
		}

		throw new NoSuchElementException("Could not find visible element with text: " + text);
	}

	private void waitForAnyVisibleText(final Duration timeout, final String... texts) {
		final Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			for (String text : texts) {
				if (isVisibleText(text)) {
					return;
				}
			}
			sleepMillis(300);
		}
		throw new AssertionError("Did not find any expected visible text: " + Arrays.toString(texts));
	}

	private boolean isVisibleText(final String text) {
		final By locator = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
		for (WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void assertVisibleText(final String text) {
		waitForVisibleElementWithText(DEFAULT_TIMEOUT, text);
	}

	private void assertVisibleTextIn(final WebElement scope, final String text) {
		final By locator = By.xpath(".//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
		for (WebElement element : scope.findElements(locator)) {
			if (element.isDisplayed()) {
				return;
			}
		}
		throw new AssertionError("Expected text [" + text + "] to be visible in scoped section.");
	}

	private String waitForNewWindow(final Set<String> beforeHandles, final Duration timeout) {
		final Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			final Set<String> current = driver.getWindowHandles();
			for (String handle : current) {
				if (!beforeHandles.contains(handle)) {
					return handle;
				}
			}
			sleepMillis(200);
		}
		return null;
	}

	private void switchToHandleIfPresent(final String handle) {
		for (String existing : driver.getWindowHandles()) {
			if (existing.equals(handle)) {
				driver.switchTo().window(handle);
				return;
			}
		}
	}

	private void waitForUiLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		sleepMillis(500);
	}

	private void takeScreenshot(final String name) throws IOException {
		final Path screenshotPath = artifactDir.resolve(name + ".png");
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void takeFullPageScreenshot(final String name) throws IOException {
		if (driver instanceof ChromiumDriver) {
			final Map<String, Object> params = new LinkedHashMap<>();
			params.put("format", "png");
			params.put("captureBeyondViewport", true);
			params.put("fromSurface", true);
			final Object response = ((ChromiumDriver) driver).executeCdpCommand("Page.captureScreenshot", params).get("data");
			if (response instanceof String) {
				final byte[] bytes = Base64.getDecoder().decode((String) response);
				Files.write(artifactDir.resolve(name + "-full.png"), bytes);
				return;
			}
		}
		takeScreenshot(name + "-viewport");
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test final report\n");
		report.append("artifact_dir: ").append(artifactDir.toAbsolutePath()).append('\n');
		report.append("base_url: ").append(configuredUrl == null ? "(pre-loaded browser page)" : configuredUrl).append('\n');
		report.append('\n');

		for (String field : REPORT_FIELDS) {
			final boolean passed = Boolean.TRUE.equals(reportStatus.get(field));
			report.append(field).append(": ").append(passed ? "PASS" : "FAIL").append('\n');
			if (!passed) {
				report.append("  detail: ").append(reportDetail.getOrDefault(field, "No details available.")).append('\n');
			}
		}

		if (!evidenceUrls.isEmpty()) {
			report.append('\n').append("captured_urls:\n");
			for (Map.Entry<String, String> entry : evidenceUrls.entrySet()) {
				report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		final Path reportFile = artifactDir.resolve("final-report.txt");
		Files.writeString(reportFile, report.toString());
		System.out.println(report);
	}

	private void markPass(final String field) {
		reportStatus.put(field, Boolean.TRUE);
		reportDetail.put(field, "OK");
	}

	private void markFail(final String field, final String detail) {
		reportStatus.put(field, Boolean.FALSE);
		reportDetail.put(field, detail == null ? "Unknown failure." : detail);
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (Exception ex) {
			return null;
		}
	}

	private static String valueOrDefault(final String first, final String second, final String fallback) {
		final String value = firstNonBlank(first, second);
		return value == null ? fallback : value;
	}

	private static String firstNonBlank(final String... values) {
		for (String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private static String sanitizeFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
	}

	private static String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		final String[] parts = text.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private static String normalizeWhitespace(final String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").trim();
	}

	private static boolean containsIgnoreCase(final String text, final String fragment) {
		return normalizeWhitespace(text).toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
	}

	private static void sleepMillis(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for UI.", ex);
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
