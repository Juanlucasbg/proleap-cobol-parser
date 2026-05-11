package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow validation for SaleADS "Mi Negocio" module.
 *
 * <p>
 * This test is environment-agnostic and does not hardcode domains.
 * It is intentionally opt-in to avoid breaking normal parser test runs.
 * </p>
 *
 * <p>
 * Run example:
 * mvn -Dtest=SaleadsMiNegocioWorkflowTest
 *     -Dsaleads.run.e2e=true
 *     -Dsaleads.login.url=https://YOUR-ENV-LOGIN-URL
 *     test
 * </p>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO = "Información General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Términos y Condiciones";
	private static final String STEP_PRIVACY = "Política de Privacidad";

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final List<Path> screenshots = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path outputDir;
	private String termsUrl = "";
	private String privacyUrl = "";

	@Before
	public void setUp() throws IOException {
		final boolean runE2e = readBooleanConfig("saleads.run.e2e", "SALEADS_RUN_E2E", false);
		Assume.assumeTrue("Set -Dsaleads.run.e2e=true (or SALEADS_RUN_E2E=true) to run this live E2E test.", runE2e);

		final String loginUrl = readStringConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set -Dsaleads.login.url=<login-page-url> (or SALEADS_LOGIN_URL) because this test is URL-agnostic.",
				loginUrl != null && !loginUrl.isBlank());

		outputDir = buildOutputDir();
		Files.createDirectories(outputDir);

		final ChromeOptions options = new ChromeOptions();
		if (readBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		try {
			writeFinalReport();
		} catch (IOException ignored) {
			// Avoid masking test failures with report I/O issues.
		}

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep(STEP_LOGIN, this::stepLoginWithGoogle);
		runStep(STEP_MENU, this::stepOpenMiNegocioMenu);
		runStep(STEP_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(STEP_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		runStep(STEP_INFO, this::stepValidateInformacionGeneral);
		runStep(STEP_ACCOUNT_DETAILS, this::stepValidateDetallesCuenta);
		runStep(STEP_BUSINESSES, this::stepValidateTusNegocios);
		runStep(STEP_TERMS, this::stepValidateTerminos);
		runStep(STEP_PRIVACY, this::stepValidatePrivacidad);

		final boolean allPassed = report.values().stream().allMatch(step -> step.passed);
		assertTrue("Some SaleADS workflow validations failed. See final report in " + outputDir.toAbsolutePath(),
				allPassed);
	}

	private void stepLoginWithGoogle() throws IOException {
		clickByTextVariants("Sign in with Google", "Login with Google", "Iniciar sesion con Google",
				"Iniciar sesion", "Continuar con Google");

		chooseGoogleAccountIfPrompted(readStringConfig("saleads.google.email", "SALEADS_GOOGLE_EMAIL",
				"juanlucasbarbiergarzon@gmail.com"));

		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Negocio", "Mi Negocio", "Dashboard", "Panel");
		assertVisibleByTextAny("Negocio", "Mi Negocio");
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByTextVariants("Negocio");
		clickByTextVariants("Mi Negocio");
		ensureSubmenuExpanded();

		assertVisibleByTextAny("Agregar Negocio");
		assertVisibleByTextAny("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded-menu");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByTextVariants("Agregar Negocio");
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Crear Nuevo Negocio");

		assertVisibleByTextAny("Crear Nuevo Negocio");
		assertVisibleByTextAny("Nombre del Negocio");
		assertVisibleByTextAny("Tienes 2 de 3 negocios");
		assertVisibleByTextAny("Cancelar");
		assertVisibleByTextAny("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		typeIntoInput("Nombre del Negocio", "Negocio Prueba Automatizacion");
		clickByTextVariants("Cancelar");
		waitUntilTextNotVisible("Crear Nuevo Negocio", DEFAULT_TIMEOUT);
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureSubmenuExpanded();
		clickByTextVariants("Administrar Negocios");
		waitForUiToLoad();

		assertVisibleByTextAny("Informacion General", "Información General");
		assertVisibleByTextAny("Detalles de la Cuenta");
		assertVisibleByTextAny("Tus Negocios");
		assertVisibleByTextAny("Seccion Legal", "Sección Legal");
		captureScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSection("Informacion General", "Información General")
				.orElseThrow(() -> new NoSuchElementException("No se encontro la seccion 'Informacion General'."));
		final List<String> sectionTexts = collectVisibleTexts(section);

		final boolean hasEmail = sectionTexts.stream().map(String::trim).anyMatch(text -> EMAIL_PATTERN.matcher(text).find())
				|| isAnyVisibleByText("@");
		assertTrue("No visible user email in 'Informacion General'.", hasEmail);

		final boolean hasName = sectionTexts.stream().map(String::trim).anyMatch(this::looksLikeUserName);
		assertTrue("No visible user name in 'Informacion General'.", hasName);

		assertVisibleByTextAny("BUSINESS PLAN");
		assertVisibleByTextAny("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleByTextAny("Cuenta creada");
		assertVisibleByTextAny("Estado activo");
		assertVisibleByTextAny("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSection("Tus Negocios")
				.orElseThrow(() -> new NoSuchElementException("No se encontro la seccion 'Tus Negocios'."));

		assertVisibleByTextAny("Agregar Negocio");
		assertVisibleByTextAny("Tienes 2 de 3 negocios");

		final long meaningfulItems = collectVisibleTexts(section).stream().map(String::trim)
				.filter(text -> !text.isEmpty())
				.filter(text -> !equalsAnyIgnoreCase(text, "Tus Negocios", "Agregar Negocio", "Tienes 2 de 3 negocios"))
				.count();
		assertTrue("No visible business list content in 'Tus Negocios'.", meaningfulItems > 0);
	}

	private void stepValidateTerminos() throws IOException {
		termsUrl = clickLegalLinkValidateAndReturn("Terminos y Condiciones", "Términos y Condiciones",
				"05-terminos-y-condiciones");
	}

	private void stepValidatePrivacidad() throws IOException {
		privacyUrl = clickLegalLinkValidateAndReturn("Politica de Privacidad", "Política de Privacidad",
				"06-politica-de-privacidad");
	}

	private String clickLegalLinkValidateAndReturn(final String linkTextWithoutAccent, final String linkTextWithAccent,
			final String screenshotName) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final String appUrlBefore = driver.getCurrentUrl();
		final Set<String> windowsBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickByTextVariants(linkTextWithAccent, linkTextWithoutAccent);
		waitForUiToLoad();

		final String newWindow = waitForNewWindow(windowsBeforeClick, Duration.ofSeconds(10));
		final boolean openedNewWindow = newWindow != null;

		if (openedNewWindow) {
			driver.switchTo().window(newWindow);
			waitForUiToLoad();
		}

		waitForAnyVisibleText(DEFAULT_TIMEOUT, linkTextWithAccent, linkTextWithoutAccent);
		assertVisibleByTextAny(linkTextWithAccent, linkTextWithoutAccent);
		assertTrue("Legal content text is not visible for '" + linkTextWithAccent + "'.", hasVisibleLegalContent());
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (openedNewWindow) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		if (!appUrlBefore.equals(driver.getCurrentUrl())) {
			waitForAnyVisibleText(DEFAULT_TIMEOUT, "Informacion General", "Información General", "Seccion Legal",
					"Sección Legal");
		}

		return finalUrl;
	}

	private boolean hasVisibleLegalContent() {
		final List<WebElement> paragraphs = driver
				.findElements(By.xpath("//p[normalize-space()!=''] | //article//*[normalize-space()!='']"));

		return paragraphs.stream().filter(WebElement::isDisplayed).map(WebElement::getText).map(String::trim)
				.anyMatch(text -> text.length() > 40);
	}

	private void chooseGoogleAccountIfPrompted(final String accountEmail) {
		final String appWindow = driver.getWindowHandle();
		new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.getWindowHandles().size() > 1
				|| isAnyVisibleByText(accountEmail) || isAnyVisibleByText("Elige una cuenta")
				|| isAnyVisibleByText("Choose an account") || isAnyVisibleByText("Negocio")
				|| isAnyVisibleByText("Mi Negocio"));

		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() > 1) {
			for (final String handle : handles) {
				if (!handle.equals(appWindow)) {
					driver.switchTo().window(handle);
					waitForUiToLoad();
					break;
				}
			}
		}

		final boolean accountVisible = isAnyVisibleByText(accountEmail);
		if (accountVisible) {
			clickByTextVariants(accountEmail);
			waitForUiToLoad();
		}

		if (driver.getWindowHandles().contains(appWindow)) {
			driver.switchTo().window(appWindow);
		}
	}

	private void ensureSubmenuExpanded() {
		final boolean hasSubmenu = isAnyVisibleByText("Agregar Negocio") && isAnyVisibleByText("Administrar Negocios");
		if (!hasSubmenu) {
			clickByTextVariants("Mi Negocio");
		}
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Agregar Negocio", "Administrar Negocios");
	}

	private Optional<WebElement> findSection(final String... headingVariants) {
		for (final String heading : headingVariants) {
			final String headingLiteral = toXPathLiteral(heading);
			final List<WebElement> candidates = driver.findElements(By.xpath(
					"//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::h6 or self::span or self::div]"
							+ "[contains(normalize-space(), " + headingLiteral + ")]"));

			for (final WebElement headingElement : candidates) {
				if (!headingElement.isDisplayed()) {
					continue;
				}

				try {
					final WebElement container = headingElement
							.findElement(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
					if (container.isDisplayed()) {
						return Optional.of(container);
					}
				} catch (NoSuchElementException ignored) {
					// Keep searching.
				}
			}
		}
		return Optional.empty();
	}

	private List<String> collectVisibleTexts(final WebElement root) {
		final List<String> values = new ArrayList<>();
		final List<WebElement> nodes = root.findElements(By.xpath(".//*[normalize-space()!='']"));
		for (final WebElement node : nodes) {
			if (node.isDisplayed()) {
				final String text = node.getText().trim();
				if (!text.isEmpty()) {
					values.add(text);
				}
			}
		}
		return values;
	}

	private boolean looksLikeUserName(final String value) {
		final String text = value.trim();
		if (text.isEmpty() || text.length() < 3 || text.length() > 80) {
			return false;
		}
		if (text.contains("@") || text.contains(":")) {
			return false;
		}
		if (equalsAnyIgnoreCase(text, "Informacion General", "Información General", "BUSINESS PLAN", "Cambiar Plan",
				"Cuenta creada", "Estado activo", "Idioma seleccionado")) {
			return false;
		}
		return text.matches("[\\p{L} .'-]{3,80}");
	}

	private void typeIntoInput(final String fieldText, final String value) {
		final String literal = toXPathLiteral(fieldText);
		final List<By> locators = Arrays.asList(
				By.xpath("//input[@placeholder=" + literal + "]"),
				By.xpath("//label[contains(normalize-space(), " + literal + ")]/following::input[1]"),
				By.xpath("//input[@aria-label=" + literal + "]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"));

		for (final By locator : locators) {
			try {
				final WebElement input = new WebDriverWait(driver, SHORT_TIMEOUT)
						.until(ExpectedConditions.visibilityOfElementLocated(locator));
				scrollIntoView(input);
				input.clear();
				input.sendKeys(value);
				waitForUiToLoad();
				return;
			} catch (TimeoutException ignored) {
				// Try next locator.
			}
		}

		throw new NoSuchElementException("No input found for field '" + fieldText + "'.");
	}

	private void clickByTextVariants(final String... textVariants) {
		final List<String> tried = new ArrayList<>();

		for (final String variant : textVariants) {
			for (final String candidate : textCandidates(variant)) {
				tried.add(candidate);
				try {
					final WebElement element = new WebDriverWait(driver, SHORT_TIMEOUT)
							.until(ExpectedConditions.elementToBeClickable(byVisibleTextClickable(candidate)));
					scrollIntoView(element);
					try {
						element.click();
					} catch (Exception clickFailure) {
						new Actions(driver).moveToElement(element).click().perform();
					}
					waitForUiToLoad();
					return;
				} catch (TimeoutException ignored) {
					// Try next candidate.
				}
			}
		}

		throw new NoSuchElementException("Could not click element by visible text. Tried: " + tried);
	}

	private void waitForAnyVisibleText(final Duration timeout, final String... textVariants) {
		new WebDriverWait(driver, timeout).until(d -> {
			for (final String variant : textVariants) {
				if (isAnyVisibleByText(variant)) {
					return true;
				}
			}
			return false;
		});
	}

	private void assertVisibleByTextAny(final String... textVariants) {
		for (final String variant : textVariants) {
			if (isAnyVisibleByText(variant)) {
				return;
			}
		}
		throw new AssertionError("Expected visible text not found. Variants: " + Arrays.toString(textVariants));
	}

	private boolean isAnyVisibleByText(final String text) {
		for (final String candidate : textCandidates(text)) {
			final List<WebElement> elements = driver.findElements(byVisibleText(candidate));
			final boolean anyVisible = elements.stream().anyMatch(WebElement::isDisplayed);
			if (anyVisible) {
				return true;
			}
		}
		return false;
	}

	private void waitUntilTextNotVisible(final String text, final Duration timeout) {
		new WebDriverWait(driver, timeout).until(ExpectedConditions.invisibilityOfElementLocated(byVisibleText(text)));
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));

		try {
			Thread.sleep(500);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String waitForNewWindow(final Set<String> windowsBeforeClick, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(d -> {
				final Set<String> current = d.getWindowHandles();
				if (current.size() <= windowsBeforeClick.size()) {
					return null;
				}
				for (final String handle : current) {
					if (!windowsBeforeClick.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (TimeoutException ignored) {
			return null;
		}
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final String safeName = sanitizeFileName(checkpointName);
		final Path screenshotPath = outputDir.resolve(safeName + ".png");
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshotPath, screenshot);
		screenshots.add(screenshotPath);
	}

	private void runStep(final String reportName, final StepAction action) {
		try {
			action.run();
			report.put(reportName, StepResult.pass());
		} catch (Throwable error) {
			report.put(reportName, StepResult.fail(error.getMessage()));
		}
	}

	private void writeFinalReport() throws IOException {
		if (outputDir == null) {
			return;
		}

		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Workflow Report").append(System.lineSeparator());
		builder.append("Generated UTC: ").append(Instant.now()).append(System.lineSeparator());
		builder.append(System.lineSeparator());

		appendReportLine(builder, STEP_LOGIN);
		appendReportLine(builder, STEP_MENU);
		appendReportLine(builder, STEP_MODAL);
		appendReportLine(builder, STEP_ADMIN_VIEW);
		appendReportLine(builder, STEP_INFO);
		appendReportLine(builder, STEP_ACCOUNT_DETAILS);
		appendReportLine(builder, STEP_BUSINESSES);
		appendReportLine(builder, STEP_TERMS);
		appendReportLine(builder, STEP_PRIVACY);

		builder.append(System.lineSeparator());
		builder.append("Final URLs").append(System.lineSeparator());
		builder.append("- Terminos y Condiciones: ").append(termsUrl.isBlank() ? "N/A" : termsUrl)
				.append(System.lineSeparator());
		builder.append("- Politica de Privacidad: ").append(privacyUrl.isBlank() ? "N/A" : privacyUrl)
				.append(System.lineSeparator());
		builder.append(System.lineSeparator());
		builder.append("Screenshots").append(System.lineSeparator());
		for (final Path screenshotPath : screenshots) {
			builder.append("- ").append(screenshotPath.toAbsolutePath()).append(System.lineSeparator());
		}

		Files.write(outputDir.resolve("final-report.txt"), builder.toString().getBytes(StandardCharsets.UTF_8));
	}

	private void appendReportLine(final StringBuilder builder, final String stepName) {
		final StepResult stepResult = report.getOrDefault(stepName, StepResult.fail("Step not executed"));
		builder.append("- ").append(stepName).append(": ").append(stepResult.passed ? "PASS" : "FAIL");
		if (!stepResult.passed && stepResult.details != null && !stepResult.details.isBlank()) {
			builder.append(" (").append(stepResult.details).append(")");
		}
		builder.append(System.lineSeparator());
	}

	private Path buildOutputDir() {
		final String configured = readStringConfig("saleads.output.dir", "SALEADS_OUTPUT_DIR");
		if (configured != null && !configured.isBlank()) {
			return Paths.get(configured);
		}

		return Paths.get("target", "saleads-e2e", TIMESTAMP_FORMAT.format(Instant.now()));
	}

	private String readStringConfig(final String propertyName, final String envName) {
		return readStringConfig(propertyName, envName, null);
	}

	private String readStringConfig(final String propertyName, final String envName, final String fallback) {
		return Optional.ofNullable(System.getProperty(propertyName)).filter(value -> !value.isBlank())
				.or(() -> Optional.ofNullable(System.getenv(envName)).filter(value -> !value.isBlank())).orElse(fallback);
	}

	private boolean readBooleanConfig(final String propertyName, final String envName, final boolean fallback) {
		final String value = readStringConfig(propertyName, envName);
		if (value == null) {
			return fallback;
		}

		return Boolean.parseBoolean(value);
	}

	private By byVisibleText(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]");
	}

	private By byVisibleTextClickable(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath(
				"//*[(self::button or self::a or @role='button' or @role='menuitem' or @role='link' or @tabindex='0' or self::span or self::div)"
						+ " and (normalize-space()=" + literal + " or contains(normalize-space(), " + literal + "))]");
	}

	private List<String> textCandidates(final String text) {
		final String trimmed = text.trim();
		final String normalized = normalizeAscii(trimmed);

		final LinkedHashSet<String> candidates = new LinkedHashSet<>();
		candidates.add(trimmed);
		if (!normalized.equals(trimmed)) {
			candidates.add(normalized);
		}
		return new ArrayList<>(candidates);
	}

	private String normalizeAscii(final String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder expression = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int index = 0; index < chars.length; index++) {
			final char c = chars[index];
			if (index > 0) {
				expression.append(", ");
			}
			if (c == '\'') {
				expression.append("\"'\"");
			} else {
				expression.append("'").append(c).append("'");
			}
		}
		expression.append(")");
		return expression.toString();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private boolean equalsAnyIgnoreCase(final String value, final String... options) {
		for (final String option : options) {
			if (value.equalsIgnoreCase(option)) {
				return true;
			}
		}
		return false;
	}

	private String sanitizeFileName(final String value) {
		return value.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
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
