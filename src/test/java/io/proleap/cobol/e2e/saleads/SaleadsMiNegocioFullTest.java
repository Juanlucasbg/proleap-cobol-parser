package io.proleap.cobol.e2e.saleads;

import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_WAIT_TIMEOUT = Duration.ofSeconds(6);
	private static final Pattern EMAIL_PATTERN = Pattern.compile(".+@.+\\..+");
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws Exception {
		final boolean enabled = Boolean.parseBoolean(config("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"Set SALEADS_E2E_ENABLED=true (or -Dsaleads.e2e.enabled=true) to run the SaleADS E2E workflow test.",
				enabled);

		final String loginUrl = config("saleads.login.url", "SALEADS_LOGIN_URL", "");
		Assume.assumeTrue(
				"Provide SALEADS_LOGIN_URL (or -Dsaleads.login.url=...) with the current environment login page.",
				!loginUrl.isBlank());

		driver = createDriver();
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
		screenshotDirectory = Files.createDirectories(Paths.get("target", "saleads-e2e-screenshots",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));

		driver.manage().timeouts().implicitlyWait(Duration.ZERO);
		driver.manage().window().setSize(new Dimension(1440, 1800));
		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (!stepResults.isEmpty()) {
			printReport();
		}

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final boolean loginPassed = runStep("Login", this::loginWithGoogle);
		final boolean menuPassed = loginPassed ? runStep("Mi Negocio menu", this::openMiNegocioMenu)
				: markBlocked("Mi Negocio menu", "blocked because Login failed");
		final boolean modalPassed = menuPassed ? runStep("Agregar Negocio modal", this::validateAgregarNegocioModal)
				: markBlocked("Agregar Negocio modal", "blocked because Mi Negocio menu failed");
		final boolean administrarPassed = menuPassed
				? runStep("Administrar Negocios view", this::openAdministrarNegociosView)
				: markBlocked("Administrar Negocios view", "blocked because Mi Negocio menu failed");
		final boolean infoPassed = administrarPassed ? runStep("Información General", this::validateInformacionGeneral)
				: markBlocked("Información General", "blocked because Administrar Negocios view failed");
		final boolean detallesPassed = administrarPassed
				? runStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta)
				: markBlocked("Detalles de la Cuenta", "blocked because Administrar Negocios view failed");
		final boolean negociosPassed = administrarPassed ? runStep("Tus Negocios", this::validateTusNegocios)
				: markBlocked("Tus Negocios", "blocked because Administrar Negocios view failed");
		final boolean termsPassed = administrarPassed
				? runStep("Términos y Condiciones", this::validateTerminosYCondiciones)
				: markBlocked("Términos y Condiciones", "blocked because Administrar Negocios view failed");
		final boolean privacyPassed = administrarPassed
				? runStep("Política de Privacidad", this::validatePoliticaDePrivacidad)
				: markBlocked("Política de Privacidad", "blocked because Administrar Negocios view failed");

		Assert.assertTrue("Final report contains FAIL entries.\n" + buildReportBody(),
				loginPassed && menuPassed && modalPassed && administrarPassed && infoPassed && detallesPassed
						&& negociosPassed && termsPassed && privacyPassed);
	}

	private void loginWithGoogle() throws Exception {
		clickByText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		selectGoogleAccountIfPrompted("juanlucasbarbiergarzon@gmail.com");

		waitForAnyText("Negocio", "Mi Negocio", "Dashboard", "Inicio");
		waitForVisible(By.xpath("//aside | //nav"));
		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws Exception {
		waitForVisible(By.xpath("//aside | //nav"));
		clickByText("Mi Negocio");

		waitForAnyText("Agregar Negocio", "Administrar Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickByText("Agregar Negocio");

		waitForVisibleText("Crear Nuevo Negocio");
		waitForVisibleText("Nombre del Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final WebElement modal = findVisibleElement(By.xpath(
				"//*[(@role='dialog' or contains(@class,'modal')) and .//*[contains(normalize-space(.),'Crear Nuevo Negocio')]]"));

		if (modal != null) {
			final List<WebElement> inputs = modal.findElements(By.xpath(".//input"));

			if (!inputs.isEmpty()) {
				final WebElement input = inputs.get(0);
				input.click();
				input.clear();
				input.sendKeys("Negocio Prueba Automatización");
			}

			final WebElement cancelButton = findVisibleElementWithin(modal,
					By.xpath(".//*[self::button or self::a][contains(normalize-space(.),'Cancelar')]"));
			if (cancelButton != null) {
				clickAndWait(cancelButton);
			}
		} else {
			clickByText("Cancelar");
		}
	}

	private void openAdministrarNegociosView() throws Exception {
		if (findVisibleElement(By.xpath("//*[contains(normalize-space(.),'Administrar Negocios')]")) == null) {
			clickByText("Mi Negocio");
		}

		clickByText("Administrar Negocios");
		waitForVisibleText("Información General", "Informacion General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal", "Seccion Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		final WebElement section = sectionByHeading("Información General", "Informacion General");
		final String sectionText = section.getText();
		Assert.assertTrue("Expected user email inside Información General.", EMAIL_PATTERN.matcher(sectionText).find());
		waitForVisibleText("BUSINESS PLAN");
		waitForVisibleText("Cambiar Plan");

		final String expectedUserName = config("saleads.expected.user.name", "SALEADS_EXPECTED_USER_NAME", "").trim();
		if (!expectedUserName.isEmpty()) {
			waitForVisibleText(expectedUserName);
		} else {
			final boolean hasNameLikeLine = Arrays.stream(sectionText.split("\\R"))
					.map(String::trim)
					.filter(line -> !line.isEmpty())
					.anyMatch(this::looksLikeUserName);
			Assert.assertTrue(
					"Expected a user-name-like text in Información General. Set SALEADS_EXPECTED_USER_NAME for strict validation.",
					hasNameLikeLine);
		}
	}

	private void validateDetallesDeLaCuenta() {
		sectionByHeading("Detalles de la Cuenta");
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		final WebElement section = sectionByHeading("Tus Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");

		final List<WebElement> candidateRows = section
				.findElements(By.xpath(".//li | .//tr[td] | .//article | .//div[contains(@class,'card')]"));
		Assert.assertTrue("Expected a visible business list in section 'Tus Negocios'.",
				!candidateRows.isEmpty() || section.getText().toLowerCase(Locale.ROOT).contains("negocio"));
	}

	private void validateTerminosYCondiciones() throws Exception {
		termsUrl = openLegalLinkAndValidate("Términos y Condiciones", "Terminos y Condiciones", "05-terminos");
	}

	private void validatePoliticaDePrivacidad() throws Exception {
		privacyUrl = openLegalLinkAndValidate("Política de Privacidad", "Politica de Privacidad", "06-politica-privacidad");
	}

	private String openLegalLinkAndValidate(final String expectedHeading, final String headingWithoutAccents,
			final String screenshotName) throws Exception {
		final String appWindowHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickByText(expectedHeading, headingWithoutAccents);

		String targetHandle = appWindowHandle;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBeforeClick.contains(handle)) {
					targetHandle = handle;
					break;
				}
			}
		} catch (final TimeoutException timeoutException) {
			// Link navigated in the same tab; continue validation there.
		}

		driver.switchTo().window(targetHandle);
		waitForUiToLoad();
		waitForVisibleText(expectedHeading, headingWithoutAccents);

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Expected legal content text for " + expectedHeading + ".", bodyText.trim().length() > 120);
		takeScreenshot(screenshotName);

		final String legalUrl = driver.getCurrentUrl();
		if (!targetHandle.equals(appWindowHandle)) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();
		return legalUrl;
	}

	private void selectGoogleAccountIfPrompted(final String emailAddress) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		try {
			new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size() || isGoogleAuthContext());
		} catch (final TimeoutException timeoutException) {
			return;
		}

		String googleHandle = appWindow;
		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBeforeClick.contains(handle)) {
				googleHandle = handle;
				break;
			}
		}
		driver.switchTo().window(googleHandle);

		if (isGoogleAuthContext()) {
			clickByText(emailAddress);
			waitForUiToLoad();
		}

		if (!googleHandle.equals(appWindow) && driver.getWindowHandles().contains(appWindow)) {
			driver.switchTo().window(appWindow);
		}
	}

	private WebDriver createDriver() {
		final String browser = config("saleads.browser", "SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		if (!"chrome".equals(browser)) {
			throw new IllegalArgumentException("Unsupported browser '" + browser
					+ "'. This test currently supports Chrome only (use SALEADS_BROWSER=chrome).");
		}

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean
				.parseBoolean(config("saleads.headless", "SALEADS_HEADLESS", "true").toLowerCase(Locale.ROOT));

		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1440,1800");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--disable-gpu");
		return new ChromeDriver(options);
	}

	private boolean runStep(final String stepName, final CheckedRunnable step) {
		try {
			step.run();
			stepResults.put(stepName, StepResult.passed("All validations passed"));
			return true;
		} catch (final Exception exception) {
			stepResults.put(stepName, StepResult.failed(exception.getClass().getSimpleName() + ": " + exception.getMessage()));
			return false;
		}
	}

	private boolean markBlocked(final String stepName, final String reason) {
		stepResults.put(stepName, StepResult.failed(reason));
		return false;
	}

	private void printReport() {
		System.out.println(buildReportBody());
	}

	private String buildReportBody() {
		final StringBuilder report = new StringBuilder();
		report.append("=== SaleADS Mi Negocio Final Report ===").append(System.lineSeparator());
		for (final String field : REPORT_FIELDS) {
			final StepResult result = stepResults.getOrDefault(field, StepResult.failed("not executed"));
			report.append("- ").append(field).append(": ").append(result.passed ? "PASS" : "FAIL").append(" - ")
					.append(result.message).append(System.lineSeparator());
		}
		report.append("- Términos y Condiciones URL: ").append(termsUrl).append(System.lineSeparator());
		report.append("- Política de Privacidad URL: ").append(privacyUrl).append(System.lineSeparator());
		report.append("- Screenshot directory: ").append(screenshotDirectory == null ? "N/A" : screenshotDirectory.toAbsolutePath());
		return report.toString();
	}

	private WebElement sectionByHeading(final String... headingTexts) {
		for (final String headingText : headingTexts) {
			final String literal = xpathLiteral(headingText);
			final By locator = By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5]"
					+ "[contains(normalize-space(.), " + literal + ")]/ancestor::*[self::section or self::div][1]");
			final WebElement section = findVisibleElement(locator);
			if (section != null) {
				return section;
			}
		}
		throw new NoSuchElementException("Unable to find section for headings: " + Arrays.toString(headingTexts));
	}

	private void clickByText(final String... textOptions) throws Exception {
		WebElement clickTarget = null;
		for (final String textOption : textOptions) {
			clickTarget = findVisibleElement(By.xpath(clickableTextXPath(textOption)));
			if (clickTarget != null) {
				break;
			}
		}
		if (clickTarget == null) {
			throw new NoSuchElementException("Unable to find clickable element with any text: " + Arrays.toString(textOptions));
		}
		clickAndWait(clickTarget);
	}

	private void clickAndWait(final WebElement element) throws Exception {
		scrollIntoView(element);
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiToLoad();
	}

	private void waitForAnyText(final String... textOptions) {
		boolean visible = false;
		for (final String textOption : textOptions) {
			if (findVisibleElement(By.xpath(anyTextXPath(textOption)), SHORT_WAIT_TIMEOUT) != null) {
				visible = true;
				break;
			}
		}
		if (!visible) {
			throw new NoSuchElementException("None of the expected texts are visible: " + Arrays.toString(textOptions));
		}
	}

	private void waitForVisibleText(final String... textOptions) {
		waitForAnyText(textOptions);
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private WebElement findVisibleElement(final By locator) {
		return findVisibleElement(locator, SHORT_WAIT_TIMEOUT);
	}

	private WebElement findVisibleElement(final By locator, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private WebElement findVisibleElementWithin(final WebElement container, final By locator) {
		try {
			final List<WebElement> elements = container.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		} catch (final NoSuchElementException noSuchElementException) {
			return null;
		}
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = screenshotDirectory.resolve(sanitizeFilename(checkpointName) + ".png");
		Files.copy(screenshotFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Screenshot captured: " + destination.toAbsolutePath());
	}

	private void waitForUiToLoad() throws Exception {
		wait.until(webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		Thread.sleep(350);
	}

	private boolean isGoogleAuthContext() {
		final String currentUrl = driver.getCurrentUrl() == null ? "" : driver.getCurrentUrl();
		final String currentTitle = driver.getTitle() == null ? "" : driver.getTitle();
		return currentUrl.contains("accounts.google.com") || currentTitle.toLowerCase(Locale.ROOT).contains("google");
	}

	private String config(final String propertyName, final String envName, final String defaultValue) {
		final String property = System.getProperty(propertyName);
		if (property != null && !property.isBlank()) {
			return property.trim();
		}
		final String env = System.getenv(envName);
		if (env != null && !env.isBlank()) {
			return env.trim();
		}
		return defaultValue;
	}

	private String clickableTextXPath(final String text) {
		final String literal = xpathLiteral(text);
		return "(//*[self::button or self::a or self::span or @role='button' or self::div]"
				+ "[contains(normalize-space(.), " + literal + ")])[1]";
	}

	private String anyTextXPath(final String text) {
		final String literal = xpathLiteral(text);
		return "//*[contains(normalize-space(.), " + literal + ")]";
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
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

	private String sanitizeFilename(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-_.]+", "-");
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({behavior: 'instant', block: 'center', inline: 'center'});", element);
	}

	private boolean looksLikeUserName(final String line) {
		final String normalized = line.toLowerCase(Locale.ROOT);
		if (normalized.contains("@") || normalized.contains("business plan") || normalized.contains("cambiar plan")
				|| normalized.contains("informacion") || normalized.contains("información")) {
			return false;
		}
		return line.matches(".*[A-Za-z].*");
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {

		private final boolean passed;
		private final String message;

		private StepResult(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message;
		}

		private static StepResult passed(final String message) {
			return new StepResult(true, message);
		}

		private static StepResult failed(final String message) {
			return new StepResult(false, message);
		}
	}
}
