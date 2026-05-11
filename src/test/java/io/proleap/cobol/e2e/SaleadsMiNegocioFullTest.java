package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(10);
	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private String termsAndConditionsUrl = "N/A";
	private String privacyPolicyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();

		if (readBooleanConfig("SALEADS_HEADLESS", "saleads.headless", true)) {
			options.addArguments("--headless=new");
			options.addArguments("--window-size=1920,1080");
		}

		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDirectory = Paths.get("target", "evidence", TEST_NAME,
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDirectory);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		final String loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getProperty("saleads.login.url"));
		Assert.assertNotNull(
				"Missing login URL. Set SALEADS_LOGIN_URL environment variable or -Dsaleads.login.url system property.",
				loginUrl);

		driver.get(loginUrl);
		waitForUiToLoad();

		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesDeCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		executeStep("Política de Privacidad", this::stepValidatePoliticaDePrivacidad);

		printFinalReport();

		final List<String> failedFields = report.entrySet().stream().filter(entry -> !entry.getValue().pass)
				.map(entry -> entry.getKey() + " -> " + entry.getValue().detail).collect(Collectors.toList());

		Assert.assertTrue("One or more workflow validations failed:\n" + String.join("\n", failedFields),
				failedFields.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		clickVisibleText("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
				"Continuar con Google", "Google");
		handleGoogleAccountSelectorIfPresent();
		waitForSidebarNavigation();
		captureScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		expandMiNegocioMenuIfNeeded();
		assertVisibleText("Agregar Negocio", "Add Business");
		assertVisibleText("Administrar Negocios", "Manage Businesses");
		captureScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickVisibleText("Agregar Negocio", "Add Business");
		assertVisibleText("Crear Nuevo Negocio", "Create New Business");
		assertBusinessNameFieldVisible();
		assertVisibleText("Tienes 2 de 3 negocios", "You have 2 of 3 businesses");
		assertVisibleText("Cancelar", "Cancel");
		assertVisibleText("Crear Negocio", "Create Business");

		final WebElement nameInput = findBusinessNameInput();
		nameInput.click();
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatizacion");
		captureScreenshot("03-agregar-negocio-modal.png");

		clickVisibleText("Cancelar", "Cancel");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), " + toXpathLiteral("Crear Nuevo Negocio") + ")]")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioMenuIfNeeded();
		clickVisibleText("Administrar Negocios", "Manage Businesses");
		waitForUiToLoad();
		assertVisibleText("Información General", "Informacion General", "General Information");
		assertVisibleText("Detalles de la Cuenta", "Detalles de la cuenta", "Account Details");
		assertVisibleText("Tus Negocios", "Your Businesses");
		assertVisibleText("Sección Legal", "Seccion Legal", "Legal Section");
		captureScreenshot("04-administrar-negocios-view.png");
	}

	private void stepValidateInformacionGeneral() {
		final String sectionText = getSectionTextByHeading("Información General", "Informacion General",
				"General Information");

		assertTrue("Expected a visible user email in Información General section.", sectionText.contains("@"));
		assertTrue("Expected BUSINESS PLAN text in Información General section.",
				containsAny(sectionText, "BUSINESS PLAN", "Business Plan"));
		assertTrue("Expected Cambiar Plan button in Información General section.",
				containsAny(sectionText, "Cambiar Plan", "Change Plan"));

		// The account used for this workflow should be visible when the profile card is loaded.
		assertTrue("Expected the workflow account email to be visible in Información General section.",
				sectionText.contains(ACCOUNT_EMAIL));
	}

	private void stepValidateDetallesDeCuenta() {
		final String sectionText = getSectionTextByHeading("Detalles de la Cuenta", "Detalles de la cuenta",
				"Account Details");

		assertTrue("Missing 'Cuenta creada' in Detalles de la Cuenta section.",
				containsAny(sectionText, "Cuenta creada", "Account created"));
		assertTrue("Missing 'Estado activo' in Detalles de la Cuenta section.",
				containsAny(sectionText, "Estado activo", "Active status"));
		assertTrue("Missing 'Idioma seleccionado' in Detalles de la Cuenta section.",
				containsAny(sectionText, "Idioma seleccionado", "Selected language"));
	}

	private void stepValidateTusNegocios() {
		final String sectionText = getSectionTextByHeading("Tus Negocios", "Your Businesses");

		assertTrue("Expected business list text in Tus Negocios section.", sectionText.trim().length() > 0);
		assertTrue("Missing 'Agregar Negocio' button in Tus Negocios section.",
				containsAny(sectionText, "Agregar Negocio", "Add Business"));
		assertTrue("Missing business quota text in Tus Negocios section.",
				containsAny(sectionText, "Tienes 2 de 3 negocios", "You have 2 of 3 businesses"));
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		termsAndConditionsUrl = validateLegalDocumentAndReturnUrl(new String[] { "Términos y Condiciones",
				"Terminos y Condiciones", "Terms and Conditions" }, new String[] { "Términos y Condiciones",
						"Terminos y Condiciones", "Terms and Conditions" }, "05-terminos-y-condiciones.png");
	}

	private void stepValidatePoliticaDePrivacidad() throws IOException {
		privacyPolicyUrl = validateLegalDocumentAndReturnUrl(
				new String[] { "Política de Privacidad", "Politica de Privacidad", "Privacy Policy" },
				new String[] { "Política de Privacidad", "Politica de Privacidad", "Privacy Policy" },
				"06-politica-de-privacidad.png");
	}

	private String validateLegalDocumentAndReturnUrl(final String[] linkTexts, final String[] headingTexts,
			final String screenshotName) throws IOException {
		expandMiNegocioMenuIfNeeded();
		assertVisibleText("Sección Legal", "Seccion Legal", "Legal Section");

		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBeforeClick = driver.getWindowHandles();

		clickVisibleText(linkTexts);

		boolean openedNewTab = false;
		String activeWindow = appWindow;

		try {
			new WebDriverWait(driver, SHORT_TIMEOUT)
					.until(browser -> browser.getWindowHandles().size() > windowsBeforeClick.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!windowsBeforeClick.contains(handle)) {
					activeWindow = handle;
					break;
				}
			}
			openedNewTab = true;
		} catch (final TimeoutException ignored) {
			openedNewTab = false;
		}

		driver.switchTo().window(activeWindow);
		waitForUiToLoad();
		assertVisibleText(headingTexts);
		assertLegalContentVisible();
		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void assertBusinessNameFieldVisible() {
		final WebElement input = findBusinessNameInput();
		assertTrue("Nombre del Negocio field is not displayed.", input.isDisplayed());
	}

	private WebElement findBusinessNameInput() {
		final String labelLiteral = toXpathLiteral("Nombre del Negocio");
		final List<By> locators = List.of(
				By.xpath("//label[contains(normalize-space(), " + labelLiteral
						+ ")]/following::*[self::input or self::textarea][1]"),
				By.xpath("//input[contains(@placeholder, " + labelLiteral + ")]"),
				By.xpath("//textarea[contains(@placeholder, " + labelLiteral + ")]"));

		for (final By locator : locators) {
			final List<WebElement> matches = driver.findElements(locator);
			for (final WebElement match : matches) {
				if (match.isDisplayed()) {
					return match;
				}
			}
		}

		throw new NoSuchElementException("Could not find visible field for 'Nombre del Negocio'.");
	}

	private void stepValidateReportEntry(final String stepName, final Throwable error) {
		final String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
		report.put(stepName, new StepResult(false, message));
	}

	private void executeStep(final String stepName, final StepExecutor executor) {
		try {
			executor.execute();
			report.put(stepName, new StepResult(true, "PASS"));
		} catch (final Throwable error) {
			try {
				captureScreenshot("failed-" + sanitizeFileName(stepName) + ".png");
			} catch (final IOException ignored) {
				// Best effort evidence capture for failed steps.
			}
			stepValidateReportEntry(stepName, error);
		}
	}

	private void waitForSidebarNavigation() {
		wait.until(driver -> {
			final List<WebElement> sidebars = driver.findElements(By.xpath("//aside | //nav"));
			return sidebars.stream().anyMatch(WebElement::isDisplayed);
		});
		assertVisibleText("Negocio", "Business");
	}

	private void handleGoogleAccountSelectorIfPresent() {
		final String currentWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = driver.getWindowHandles();

		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(browser -> browser.getWindowHandles().size() > windowsBefore.size()
					|| isVisibleTextPresent(ACCOUNT_EMAIL) || driver.getCurrentUrl().contains("accounts.google.com"));
		} catch (final TimeoutException ignored) {
			// Google account selector may not appear when session is already authenticated.
		}

		String targetWindow = currentWindow;
		for (final String handle : driver.getWindowHandles()) {
			if (!windowsBefore.contains(handle)) {
				targetWindow = handle;
				break;
			}
		}
		driver.switchTo().window(targetWindow);

		if (isVisibleTextPresent(ACCOUNT_EMAIL)) {
			clickVisibleText(ACCOUNT_EMAIL);
		}

		if (driver.getWindowHandles().contains(currentWindow)) {
			driver.switchTo().window(currentWindow);
		}

		new WebDriverWait(driver, Duration.ofSeconds(60)).until(browser -> isVisibleTextPresent("Negocio")
				|| isVisibleTextPresent("Business") || browser.getCurrentUrl().contains("/dashboard"));
		waitForUiToLoad();
	}

	private void expandMiNegocioMenuIfNeeded() {
		if (isVisibleTextPresent("Agregar Negocio") && isVisibleTextPresent("Administrar Negocios")) {
			return;
		}

		if (isVisibleTextPresent("Mi Negocio")) {
			clickVisibleText("Mi Negocio");
		}

		if (!isVisibleTextPresent("Agregar Negocio") || !isVisibleTextPresent("Administrar Negocios")) {
			if (isVisibleTextPresent("Negocio")) {
				clickVisibleText("Negocio");
			}
			if (isVisibleTextPresent("Mi Negocio")) {
				clickVisibleText("Mi Negocio");
			}
		}

		assertVisibleText("Agregar Negocio", "Add Business");
		assertVisibleText("Administrar Negocios", "Manage Businesses");
	}

	private void clickVisibleText(final String... texts) {
		final List<WebElement> candidates = findVisibleTextCandidates(texts);
		if (candidates.isEmpty()) {
			throw new NoSuchElementException("No visible/clickable element found for text options: "
					+ String.join(", ", texts));
		}

		final WebElement element = candidates.get(0);
		scrollIntoView(element);

		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final RuntimeException clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiToLoad();
	}

	private List<WebElement> findVisibleTextCandidates(final String... texts) {
		final List<WebElement> results = new ArrayList<>();

		for (final String text : texts) {
			final String literal = toXpathLiteral(text);
			final List<By> queries = List.of(
					By.xpath("//button[normalize-space()=" + literal + "]"),
					By.xpath("//a[normalize-space()=" + literal + "]"),
					By.xpath("//*[@role='button' and normalize-space()=" + literal + "]"),
					By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(), " + literal
							+ ")]"),
					By.xpath("//*[normalize-space()=" + literal + "]"));

			for (final By query : queries) {
				for (final WebElement element : driver.findElements(query)) {
					if (element.isDisplayed() && element.getText() != null && !element.getText().trim().isEmpty()) {
						results.add(element);
					}
				}
				if (!results.isEmpty()) {
					return results;
				}
			}
		}

		return results;
	}

	private void assertVisibleText(final String... options) {
		wait.until(driver -> isVisibleTextPresent(options));
	}

	private boolean isVisibleTextPresent(final String... options) {
		for (final String option : options) {
			final String literal = toXpathLiteral(option);
			final List<WebElement> matches = driver
					.findElements(By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
			for (final WebElement match : matches) {
				if (match.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));

		try {
			wait.until(driver -> {
				final Object busy = ((JavascriptExecutor) driver).executeScript(
						"return Boolean(document.querySelector('[aria-busy=\"true\"], .loading, .spinner, .ant-spin-spinning'))");
				return Boolean.FALSE.equals(busy);
			});
		} catch (final TimeoutException ignored) {
			// Some pages do not expose deterministic loading indicators.
		}
	}

	private void assertLegalContentVisible() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p[string-length(normalize-space()) > 40]"));
		final boolean paragraphVisible = paragraphs.stream().anyMatch(WebElement::isDisplayed);
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected legal content text to be visible.", paragraphVisible || bodyText.length() > 200);
	}

	private String getSectionTextByHeading(final String... headings) {
		WebElement section = null;

		for (final String heading : headings) {
			final String literal = toXpathLiteral(heading);
			final List<By> locators = List.of(
					By.xpath("//*[self::section or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(), "
							+ literal + ")]]"),
					By.xpath(
							"//*[self::section or self::div][.//*[contains(normalize-space(), " + literal + ")]]"));

			for (final By locator : locators) {
				final List<WebElement> matches = driver.findElements(locator);
				for (final WebElement match : matches) {
					if (match.isDisplayed() && match.getText().contains(heading)) {
						section = match;
						break;
					}
				}
				if (section != null) {
					break;
				}
			}

			if (section != null) {
				break;
			}
		}

		if (section == null) {
			throw new NoSuchElementException("Could not locate section with heading options: " + String.join(", ", headings));
		}

		return section.getText();
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final Path screenshotPath = evidenceDirectory.resolve(fileName);
		final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshotPath, bytes);
	}

	private void printFinalReport() {
		System.out.println("=== " + TEST_NAME + " report ===");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final String status = entry.getValue().pass ? "PASS" : "FAIL";
			System.out.println("- " + entry.getKey() + ": " + status + " (" + entry.getValue().detail + ")");
		}
		System.out.println("- Términos y Condiciones URL: " + termsAndConditionsUrl);
		System.out.println("- Política de Privacidad URL: " + privacyPolicyUrl);
		System.out.println("- Evidence folder: " + evidenceDirectory.toAbsolutePath());
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		new Actions(driver).moveToElement(element).perform();
	}

	private boolean containsAny(final String source, final String... candidates) {
		for (final String candidate : candidates) {
			if (source.contains(candidate)) {
				return true;
			}
		}
		return false;
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private boolean readBooleanConfig(final String environmentVariable, final String propertyName,
			final boolean defaultValue) {
		final String value = firstNonBlank(System.getenv(environmentVariable), System.getProperty(propertyName));
		return value == null ? defaultValue : Boolean.parseBoolean(value);
	}

	private String sanitizeFileName(final String input) {
		return input.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part = chars[i] == '\'' ? "\"'\"" : "'" + chars[i] + "'";
			builder.append(part);
			if (i < chars.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepExecutor {
		void execute() throws Exception;
	}

	private static final class StepResult {
		private final boolean pass;
		private final String detail;

		private StepResult(final boolean pass, final String detail) {
			this.pass = pass;
			this.detail = detail;
		}
	}
}
