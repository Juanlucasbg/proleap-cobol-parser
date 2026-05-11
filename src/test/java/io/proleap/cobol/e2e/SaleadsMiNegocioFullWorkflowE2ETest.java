package io.proleap.cobol.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullWorkflowE2ETest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private String loginUrl;
	private String termsAndConditionsUrl;
	private String privacyPolicyUrl;

	@Before
	public void setUp() throws Exception {
		final boolean enabled = Boolean.parseBoolean(readConfig("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run SaleADS E2E tests.", enabled);

		loginUrl = readConfig("SALEADS_LOGIN_URL", "").trim();
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the active SaleADS login page.", !loginUrl.isEmpty());

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1200");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "false"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		screenshotDirectory = createScreenshotDirectory();

		driver.get(loginUrl);
		waitForUiLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		executeStep("Política de Privacidad", this::stepValidatePoliticaDePrivacidad);

		final String finalReport = buildFinalReport();
		System.out.println(finalReport);

		final boolean allPassed = report.values().stream().allMatch(Boolean::booleanValue);
		Assert.assertTrue("One or more validations failed.\n" + finalReport, allPassed);
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleTextContaining("Google");

		final List<WebElement> accountOptions = driver.findElements(By.xpath(
				"//*[normalize-space()='juanlucasbarbiergarzon@gmail.com']"));
		if (!accountOptions.isEmpty() && accountOptions.get(0).isDisplayed()) {
			clickAndWait(accountOptions.get(0));
		}

		wait.until(ExpectedConditions.or(
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside")),
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//nav")),
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(@class,'sidebar')]"))));
		waitForTextVisible("Negocio");
		captureScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		waitForTextVisible("Agregar Negocio");
		waitForTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded-menu.png");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		final WebElement modal = waitForVisible(By.xpath(
				"//div[@role='dialog' or contains(@class,'modal') or .//*[normalize-space()='Crear Nuevo Negocio']]"));

		assertTextInScope(modal, "Crear Nuevo Negocio");
		assertTextInScope(modal, "Nombre del Negocio");
		assertTextInScope(modal, "Tienes 2 de 3 negocios");
		assertTextInScope(modal, "Cancelar");
		assertTextInScope(modal, "Crear Negocio");

		final WebElement businessNameInput = findFirstDisplayed(modal,
				By.xpath(".//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio']"),
				By.xpath(".//input"));
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");

		captureScreenshot("03-agregar-negocio-modal.png");
		clickByVisibleTextInScope(modal, "Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		waitForTextVisible("Información General");
		waitForTextVisible("Detalles de la Cuenta");
		waitForTextVisible("Tus Negocios");
		waitForTextVisible("Sección Legal");
		captureScreenshot("04-administrar-negocios-page.png");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = sectionByHeading("Información General");
		final String text = section.getText();

		Assert.assertTrue("User name is not visible in Información General.", containsLikelyName(text));
		Assert.assertTrue("User email is not visible in Información General.", containsEmail(text));
		Assert.assertTrue("BUSINESS PLAN text is missing.", containsText(text, "BUSINESS PLAN"));
		Assert.assertTrue("Cambiar Plan button is missing.", containsText(text, "Cambiar Plan"));
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = sectionByHeading("Detalles de la Cuenta");
		assertTextInScope(section, "Cuenta creada");
		assertTextInScope(section, "Estado activo");
		assertTextInScope(section, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = sectionByHeading("Tus Negocios");
		assertTextInScope(section, "Agregar Negocio");
		assertTextInScope(section, "Tienes 2 de 3 negocios");
		Assert.assertTrue("Business list is not visible in Tus Negocios.", hasBusinessList(section));
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		termsAndConditionsUrl = validateLegalLink("Términos y Condiciones",
				"Términos y Condiciones", "08-terminos-y-condiciones.png");
	}

	private void stepValidatePoliticaDePrivacidad() throws Exception {
		privacyPolicyUrl = validateLegalLink("Política de Privacidad",
				"Política de Privacidad", "09-politica-de-privacidad.png");
	}

	private String validateLegalLink(final String linkText, final String headingText, final String screenshotName)
			throws Exception {
		final String applicationWindow = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> initialWindows = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForUiLoad();

		final String newWindow = waitForNewWindow(initialWindows);
		final boolean openedNewTab = newWindow != null;
		if (openedNewTab) {
			driver.switchTo().window(newWindow);
			waitForUiLoad();
		}

		waitForTextVisible(headingText);
		Assert.assertTrue("Legal content text is not visible for " + linkText + ".", hasSubstantialText());
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(applicationWindow);
			waitForUiLoad();
		} else if (!driver.getCurrentUrl().equals(originalUrl)) {
			driver.navigate().back();
			waitForUiLoad();
		}

		return finalUrl;
	}

	private void executeStep(final String reportField, final CheckedRunnable step) {
		try {
			step.run();
			report.put(reportField, Boolean.TRUE);
		} catch (final Throwable throwable) {
			report.put(reportField, Boolean.FALSE);
			System.err.println("Step failed: " + reportField + " -> " + throwable.getMessage());
			throwable.printStackTrace(System.err);
		}
	}

	private WebElement sectionByHeading(final String headingText) {
		final String headingLiteral = xpathLiteral(headingText);
		final By sectionBy = By.xpath(
				"//*[self::section or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4][normalize-space()="
						+ headingLiteral + "]]");
		return waitForVisible(sectionBy);
	}

	private void clickByVisibleText(final String text) {
		final String literal = xpathLiteral(text);
		final By by = By.xpath(
				"(//button[normalize-space()=" + literal + "] | //a[normalize-space()=" + literal
						+ "] | //*[@role='button' and normalize-space()=" + literal
						+ "] | //span[normalize-space()=" + literal + "] | //div[normalize-space()=" + literal + "])[1]");
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
		clickAndWait(element);
	}

	private void clickByVisibleTextContaining(final String partialText) {
		final String literal = xpathLiteral(partialText);
		final By by = By.xpath(
				"(//button[contains(normalize-space(), " + literal + ")] | //a[contains(normalize-space(), " + literal
						+ ")] | //*[@role='button' and contains(normalize-space(), " + literal + ")])[1]");
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
		clickAndWait(element);
	}

	private void clickByVisibleTextInScope(final WebElement scope, final String text) {
		final String literal = xpathLiteral(text);
		final List<WebElement> candidates = scope.findElements(By.xpath(
				".//button[normalize-space()=" + literal + "] | .//a[normalize-space()=" + literal
						+ "] | .//*[@role='button' and normalize-space()=" + literal
						+ "] | .//span[normalize-space()=" + literal + "]"));

		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed() && candidate.isEnabled()) {
				clickAndWait(candidate);
				return;
			}
		}

		throw new NoSuchElementException("No clickable element with text '" + text + "' in scope.");
	}

	private void clickAndWait(final WebElement element) {
		element.click();
		waitForUiLoad();
	}

	private void waitForUiLoad() {
		final ExpectedCondition<Boolean> jsLoaded = webDriver -> {
			final Object readyState = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(readyState));
		};

		wait.until(jsLoaded);
		wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
	}

	private WebElement waitForVisible(final By by) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private void waitForTextVisible(final String text) {
		final String literal = xpathLiteral(text);
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]")));
	}

	private boolean isTextVisible(final String text) {
		final String literal = xpathLiteral(text);
		return !driver.findElements(
				By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"))
				.isEmpty();
	}

	private void assertTextInScope(final WebElement scope, final String text) {
		final String literal = xpathLiteral(text);
		final List<WebElement> matches = scope.findElements(
				By.xpath(".//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"));
		boolean visible = false;
		for (final WebElement match : matches) {
			if (match.isDisplayed()) {
				visible = true;
				break;
			}
		}
		Assert.assertTrue("Expected text not found in section/modal: " + text, visible);
	}

	private WebElement findFirstDisplayed(final WebElement scope, final By... candidates) {
		for (final By candidate : candidates) {
			final List<WebElement> elements = scope.findElements(candidate);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		throw new NoSuchElementException("No matching visible element found.");
	}

	private boolean containsEmail(final String text) {
		final Matcher matcher = EMAIL_PATTERN.matcher(text);
		return matcher.find();
	}

	private boolean containsLikelyName(final String text) {
		final String[] ignored = {"información general", "business plan", "cambiar plan"};
		final String[] lines = text.split("\\R");

		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}

			final String lower = trimmed.toLowerCase(Locale.ROOT);
			if (lower.contains("@")) {
				continue;
			}

			boolean skip = false;
			for (final String ignoredTerm : ignored) {
				if (lower.contains(ignoredTerm)) {
					skip = true;
					break;
				}
			}
			if (skip) {
				continue;
			}

			final String cleaned = trimmed.replaceAll("[^A-Za-zÀ-ÿ\\s]", " ").replaceAll("\\s+", " ").trim();
			if (cleaned.split(" ").length >= 2 && cleaned.length() >= 5) {
				return true;
			}
		}

		return false;
	}

	private boolean containsText(final String source, final String expected) {
		return source.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
	}

	private boolean hasBusinessList(final WebElement section) {
		final List<WebElement> structuredEntries = section.findElements(
				By.xpath(".//li | .//tr[.//td] | .//*[contains(@class,'business') or contains(@class,'negocio')]"));
		for (final WebElement entry : structuredEntries) {
			if (entry.isDisplayed() && !entry.getText().trim().isEmpty()) {
				return true;
			}
		}

		final String[] lines = section.getText().split("\\R");
		int meaningfulLines = 0;
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			final String lower = trimmed.toLowerCase(Locale.ROOT);
			if (lower.contains("tus negocios") || lower.contains("agregar negocio")
					|| lower.contains("tienes 2 de 3 negocios")) {
				continue;
			}
			meaningfulLines++;
		}

		return meaningfulLines >= 1;
	}

	private boolean hasSubstantialText() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p[normalize-space()]"));
		int totalLength = 0;
		for (final WebElement paragraph : paragraphs) {
			if (paragraph.isDisplayed()) {
				totalLength += paragraph.getText().trim().length();
			}
		}
		return totalLength >= 120;
	}

	private String waitForNewWindow(final Set<String> existingWindowHandles) {
		try {
			return new WebDriverWait(driver, Duration.ofSeconds(8)).until(webDriver -> {
				final Set<String> currentHandles = webDriver.getWindowHandles();
				if (currentHandles.size() <= existingWindowHandles.size()) {
					return null;
				}
				for (final String handle : currentHandles) {
					if (!existingWindowHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private String buildFinalReport() {
		final List<String> lines = new ArrayList<>();
		lines.add("Final Report - " + TEST_NAME);

		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			lines.add(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}

		lines.add("Términos y Condiciones URL: " + safeValue(termsAndConditionsUrl));
		lines.add("Política de Privacidad URL: " + safeValue(privacyPolicyUrl));
		lines.add("Screenshots: " + screenshotDirectory.toAbsolutePath());
		return String.join(System.lineSeparator(), lines);
	}

	private String safeValue(final String value) {
		return value == null || value.trim().isEmpty() ? "N/A" : value;
	}

	private Path createScreenshotDirectory() throws IOException {
		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Paths.get("target", "saleads-e2e", TEST_NAME, runId);
		Files.createDirectories(path);
		return path;
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = screenshotDirectory.resolve(fileName);
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private String readConfig(final String key, final String fallback) {
		final String propertyValue = System.getProperty(key);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue;
		}

		final String envValue = System.getenv(key);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue;
		}

		return fallback;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i != parts.length - 1) {
				builder.append(",\"'\",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
