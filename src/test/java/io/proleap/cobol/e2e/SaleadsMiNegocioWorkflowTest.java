package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
	private static final List<String> FINAL_REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu",
			"Agregar Negocio modal", "Administrar Negocios view", "Información General", "Detalles de la Cuenta",
			"Tus Negocios", "Términos y Condiciones", "Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, String> report = new LinkedHashMap<>();
	private String appWindowHandle;
	private String termsAndConditionsUrl = "N/A";
	private String privacyPolicyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		for (final String field : FINAL_REPORT_FIELDS) {
			report.put(field, "FAIL - Not executed.");
		}

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Path.of("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);

		driver = createWebDriver();
		wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(30));
		driver.manage().window().setSize(new Dimension(1440, 1080));
	}

	@After
	public void tearDown() {
		try {
			System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
			System.out.println("Términos y Condiciones URL: " + termsAndConditionsUrl);
			System.out.println("Política de Privacidad URL: " + privacyPolicyUrl);
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = readSetting("saleads.login.url", "SALEADS_LOGIN_URL", "");
		Assume.assumeTrue(
				"Set saleads.login.url (or SALEADS_LOGIN_URL) to the current environment login page before running this test.",
				!loginUrl.isBlank());

		final boolean loginOk = executeStep("Login", () -> stepLoginWithGoogle(loginUrl));
		final boolean menuOk = executeStep("Mi Negocio menu", () -> {
			requirePreviousStep(loginOk, "Login");
			stepOpenMiNegocioMenu();
		});
		final boolean addBusinessModalOk = executeStep("Agregar Negocio modal", () -> {
			requirePreviousStep(menuOk, "Mi Negocio menu");
			stepValidateAgregarNegocioModal();
		});
		final boolean manageBusinessOk = executeStep("Administrar Negocios view", () -> {
			requirePreviousStep(menuOk, "Mi Negocio menu");
			stepOpenAdministrarNegocios();
		});
		final boolean infoGeneralOk = executeStep("Información General", () -> {
			requirePreviousStep(manageBusinessOk, "Administrar Negocios view");
			stepValidateInformacionGeneral();
		});
		final boolean accountDetailsOk = executeStep("Detalles de la Cuenta", () -> {
			requirePreviousStep(manageBusinessOk, "Administrar Negocios view");
			stepValidateDetallesCuenta();
		});
		final boolean businessesOk = executeStep("Tus Negocios", () -> {
			requirePreviousStep(manageBusinessOk, "Administrar Negocios view");
			stepValidateTusNegocios();
		});
		final boolean termsOk = executeStep("Términos y Condiciones", () -> {
			requirePreviousStep(manageBusinessOk && businessesOk, "Administrar Negocios view / Tus Negocios");
			termsAndConditionsUrl = stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones",
					"05-terminos-y-condiciones");
		});
		final boolean privacyOk = executeStep("Política de Privacidad", () -> {
			requirePreviousStep(manageBusinessOk && businessesOk, "Administrar Negocios view / Tus Negocios");
			privacyPolicyUrl = stepValidateLegalLink("Política de Privacidad", "Política de Privacidad",
					"06-politica-de-privacidad");
		});

		printFinalReport();

		final boolean allPassed = loginOk && menuOk && addBusinessModalOk && manageBusinessOk && infoGeneralOk
				&& accountDetailsOk && businessesOk && termsOk && privacyOk;
		assertTrue("One or more validations failed.\n" + buildReportSummary(), allPassed);
	}

	private WebDriver createWebDriver() throws MalformedURLException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-gpu", "--no-sandbox", "--window-size=1440,1080", "--lang=es-ES");

		final boolean headless = Boolean
				.parseBoolean(readSetting("saleads.headless", "SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		final String userDataDir = readSetting("saleads.chrome.userDataDir", "SALEADS_CHROME_USER_DATA_DIR", "");
		if (!userDataDir.isBlank()) {
			options.addArguments("--user-data-dir=" + userDataDir);
		}

		final String profileDir = readSetting("saleads.chrome.profileDir", "SALEADS_CHROME_PROFILE_DIR", "");
		if (!profileDir.isBlank()) {
			options.addArguments("--profile-directory=" + profileDir);
		}

		final String remoteUrl = readSetting("saleads.selenium.remote.url", "SALEADS_SELENIUM_REMOTE_URL", "");
		if (!remoteUrl.isBlank()) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}

		return new ChromeDriver(options);
	}

	private void stepLoginWithGoogle(final String loginUrl) throws IOException {
		driver.get(loginUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();

		final Set<String> windowsBeforeLoginClick = new LinkedHashSet<>(driver.getWindowHandles());
		clickByVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Acceder con Google", "Google"));

		final Optional<String> maybeGoogleWindow = waitForNewWindow(windowsBeforeLoginClick, 12);
		if (maybeGoogleWindow.isPresent()) {
			driver.switchTo().window(maybeGoogleWindow.get());
			waitForUiToLoad();
		}

		clickIfVisible(By.xpath(
				"//*[contains(normalize-space(.), 'juanlucasbarbiergarzon@gmail.com') and (self::div or self::span or self::button)]"),
				10);

		if (maybeGoogleWindow.isPresent()) {
			waitUntil(driverInstance -> driverInstance.getWindowHandles().contains(appWindowHandle), 30,
					"Main application window did not remain available after Google sign-in.");
			driver.switchTo().window(appWindowHandle);
		}

		waitForUiToLoad();
		assertVisibleText("Negocio", 30);
		assertAnyVisible(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//main"));
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByVisibleText(Arrays.asList("Negocio"));
		clickByVisibleText(Arrays.asList("Mi Negocio"));

		assertVisibleText("Agregar Negocio", 20);
		assertVisibleText("Administrar Negocios", 20);
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText(Arrays.asList("Agregar Negocio"));

		assertVisibleText("Crear Nuevo Negocio", 20);
		assertAnyVisible(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));
		assertVisibleText("Tienes 2 de 3 negocios", 20);
		assertVisibleText("Cancelar", 20);
		assertVisibleText("Crear Negocio", 20);
		takeScreenshot("03-agregar-negocio-modal");

		final Optional<WebElement> maybeInput = findVisibleElement(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
		if (maybeInput.isPresent()) {
			final WebElement input = maybeInput.get();
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
			waitForUiToLoad();
		}

		clickByVisibleText(Arrays.asList("Cancelar"));
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios", 3)) {
			clickByVisibleText(Arrays.asList("Mi Negocio", "Negocio"));
		}
		if (!isTextVisible("Administrar Negocios", 3)) {
			clickByVisibleText(Arrays.asList("Mi Negocio"));
		}

		clickByVisibleText(Arrays.asList("Administrar Negocios"));

		assertVisibleText("Información General", 30);
		assertVisibleText("Detalles de la Cuenta", 30);
		assertVisibleText("Tus Negocios", 30);
		assertVisibleText("Sección Legal", 30);
		takeScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		final String sectionText = normalizedWhitespace(section.getText());

		assertTrue("Expected an email to be visible in Información General.",
				EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("Expected a user name to be visible in Información General.", looksLikeUserNameIsVisible(sectionText));
		assertTrue("Expected BUSINESS PLAN text to be visible.", containsNormalized(sectionText, "BUSINESS PLAN"));
		assertTrue("Expected Cambiar Plan button to be visible.",
				isTextVisible("Cambiar Plan", 10));
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		final String sectionText = normalizedWhitespace(section.getText());

		assertTrue("Expected 'Cuenta creada' in Detalles de la Cuenta.",
				containsNormalized(sectionText, "Cuenta creada"));
		assertTrue("Expected 'Estado activo' in Detalles de la Cuenta.",
				containsNormalized(sectionText, "Estado activo"));
		assertTrue("Expected 'Idioma seleccionado' in Detalles de la Cuenta.",
				containsNormalized(sectionText, "Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final String sectionText = normalizedWhitespace(section.getText());

		assertTrue("Expected business list area in Tus Negocios.", hasVisibleBusinessList(section, sectionText));
		assertTrue("Expected Agregar Negocio button in Tus Negocios.",
				containsNormalized(sectionText, "Agregar Negocio") || isTextVisible("Agregar Negocio", 10));
		assertTrue("Expected 'Tienes 2 de 3 negocios' in Tus Negocios.",
				containsNormalized(sectionText, "Tienes 2 de 3 negocios"));
	}

	private String stepValidateLegalLink(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		waitForUiToLoad();

		final String sourceWindow = driver.getWindowHandle();
		final String sourceUrl = driver.getCurrentUrl();
		final Set<String> windowsBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(Arrays.asList(linkText));

		final Optional<String> maybeNewWindow = waitForNewWindow(windowsBefore, 10);
		if (maybeNewWindow.isPresent()) {
			driver.switchTo().window(maybeNewWindow.get());
			waitForUiToLoad();
		} else {
			waitUntil(driverInstance -> !driverInstance.getCurrentUrl().equals(sourceUrl), 15,
					"Legal link click did not open a new tab nor navigate.");
		}

		assertVisibleText(headingText, 30);
		final String bodyText = normalizedWhitespace(driver.findElement(By.tagName("body")).getText());
		assertTrue("Expected legal content text for " + headingText + ".", bodyText.length() > 80);
		takeScreenshot(screenshotName);

		final String legalUrl = driver.getCurrentUrl();

		if (maybeNewWindow.isPresent()) {
			driver.close();
			driver.switchTo().window(sourceWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
			assertVisibleText("Sección Legal", 30);
		}

		return legalUrl;
	}

	private void clickByVisibleText(final List<String> candidates) {
		WebElement element = null;
		for (final String candidate : candidates) {
			final String literal = xpathLiteral(candidate);
			final By by = By.xpath(
					"(//*[self::button or self::a or self::span or self::div or @role='button']"
							+ "[normalize-space(.)=" + literal + " or .//*[normalize-space(.)=" + literal + "]])[1]");
			try {
				element = wait.until(ExpectedConditions.elementToBeClickable(by));
				break;
			} catch (final TimeoutException ignored) {
				// try next text candidate
			}
		}

		if (element == null) {
			throw new NoSuchElementException("No clickable element found for texts: " + candidates);
		}

		element.click();
		waitForUiToLoad();
	}

	private void clickIfVisible(final By locator, final int timeoutSeconds) {
		try {
			final WebElement element = new WebDriverWait(driver, java.time.Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.elementToBeClickable(locator));
			element.click();
			waitForUiToLoad();
		} catch (final TimeoutException ignored) {
			// Account picker is optional in some environments/sessions.
		}
	}

	private Optional<String> waitForNewWindow(final Set<String> windowsBefore, final int timeoutSeconds) {
		try {
			new WebDriverWait(driver, java.time.Duration.ofSeconds(timeoutSeconds))
					.until((ExpectedCondition<Boolean>) drv -> drv.getWindowHandles().size() > windowsBefore.size());

			final Set<String> windowsAfter = driver.getWindowHandles();
			for (final String handle : windowsAfter) {
				if (!windowsBefore.contains(handle)) {
					return Optional.of(handle);
				}
			}
		} catch (final TimeoutException ignored) {
			// Same-tab navigation path.
		}

		return Optional.empty();
	}

	private void assertVisibleText(final String text, final int timeoutSeconds) {
		final String literal = xpathLiteral(text);
		final By by = By.xpath("//*[contains(normalize-space(.), " + literal + ")]");
		new WebDriverWait(driver, java.time.Duration.ofSeconds(timeoutSeconds))
				.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private boolean isTextVisible(final String text, final int timeoutSeconds) {
		try {
			assertVisibleText(text, timeoutSeconds);
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	@SafeVarargs
	private final void assertAnyVisible(final By... locators) {
		for (final By locator : locators) {
			if (!driver.findElements(locator).isEmpty() && driver.findElement(locator).isDisplayed()) {
				return;
			}
		}

		for (final By locator : locators) {
			if (!driver.findElements(locator).isEmpty()) {
				for (final WebElement element : driver.findElements(locator)) {
					if (element.isDisplayed()) {
						return;
					}
				}
			}
		}

		throw new NoSuchElementException("None of the expected locators are visible: " + Arrays.toString(locators));
	}

	@SafeVarargs
	private final Optional<WebElement> findVisibleElement(final By... locators) {
		for (final By locator : locators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return Optional.of(element);
				}
			}
		}

		return Optional.empty();
	}

	private WebElement findSectionByHeading(final String headingText) {
		final String headingLiteral = xpathLiteral(headingText);
		final By sectionBy = By.xpath(
				"(//*[self::section or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::span]"
						+ "[contains(normalize-space(.), " + headingLiteral + ")]])[1]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(sectionBy));
	}

	private boolean hasVisibleBusinessList(final WebElement section, final String sectionText) {
		final List<By> listLocators = Arrays.asList(By.xpath(".//li"), By.xpath(".//tr"),
				By.xpath(".//*[contains(@class, 'business')]"), By.xpath(".//*[contains(@data-testid, 'business')]"),
				By.xpath(".//*[contains(@class, 'card')]"));

		for (final By locator : listLocators) {
			for (final WebElement item : section.findElements(locator)) {
				if (item.isDisplayed()) {
					return true;
				}
			}
		}

		final List<String> lines = new ArrayList<>();
		for (final String line : sectionText.split("\\R")) {
			final String trimmed = line.trim();
			if (!trimmed.isEmpty()) {
				lines.add(trimmed);
			}
		}

		return lines.size() >= 3;
	}

	private boolean looksLikeUserNameIsVisible(final String sectionText) {
		final List<String> lines = new ArrayList<>();
		for (final String line : sectionText.split("\\R")) {
			final String trimmed = line.trim();
			if (!trimmed.isEmpty()) {
				lines.add(trimmed);
			}
		}

		final Set<String> ignoredTokens = new LinkedHashSet<>(Arrays.asList("informacion general", "business plan",
				"cambiar plan", "plan", "correo", "email"));
		for (final String line : lines) {
			final String normalized = normalize(line);
			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (normalized.length() < 3) {
				continue;
			}
			boolean ignored = false;
			for (final String token : ignoredTokens) {
				if (normalized.contains(token)) {
					ignored = true;
					break;
				}
			}
			if (!ignored) {
				return true;
			}
		}

		return false;
	}

	private void waitForUiToLoad() {
		wait.until(driverInstance -> "complete".equals(
				((JavascriptExecutor) driverInstance).executeScript("return document.readyState")));
		try {
			Thread.sleep(350L);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for UI to settle.", ex);
		}
	}

	private void takeScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path output = evidenceDir.resolve(name + ".png");
		Files.copy(screenshot.toPath(), output, StandardCopyOption.REPLACE_EXISTING);
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final String[] parts = text.split("'");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private boolean executeStep(final String reportField, final StepOperation operation) {
		try {
			operation.run();
			report.put(reportField, "PASS");
			return true;
		} catch (final Throwable ex) {
			final String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			report.put(reportField, "FAIL - " + reason);
			return false;
		}
	}

	private void requirePreviousStep(final boolean previousStepResult, final String dependencyName) {
		if (!previousStepResult) {
			throw new IllegalStateException("Blocked because prerequisite step failed: " + dependencyName);
		}
	}

	private void printFinalReport() {
		System.out.println("Final validation report:");
		for (final String field : FINAL_REPORT_FIELDS) {
			System.out.println(" - " + field + ": " + report.get(field));
		}
	}

	private String buildReportSummary() {
		final StringBuilder summary = new StringBuilder();
		for (final String field : FINAL_REPORT_FIELDS) {
			summary.append(field).append(": ").append(report.get(field)).append(System.lineSeparator());
		}
		summary.append("Términos y Condiciones URL: ").append(termsAndConditionsUrl).append(System.lineSeparator());
		summary.append("Política de Privacidad URL: ").append(privacyPolicyUrl).append(System.lineSeparator());
		return summary.toString();
	}

	private String readSetting(final String propertyKey, final String envKey, final String fallback) {
		final String fromProperty = System.getProperty(propertyKey);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}

		final String fromEnv = System.getenv(envKey);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv.trim();
		}

		return fallback;
	}

	private String normalizedWhitespace(final String value) {
		return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	private boolean containsNormalized(final String text, final String expected) {
		return normalize(text).contains(normalize(expected));
	}

	private String normalize(final String text) {
		return java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "").toLowerCase();
	}

	private void waitUntil(final ExpectedCondition<Boolean> condition, final int timeoutSeconds, final String message) {
		new WebDriverWait(driver, java.time.Duration.ofSeconds(timeoutSeconds)).withMessage(message).until(condition);
	}

	@FunctionalInterface
	private interface StepOperation {
		void run() throws Exception;
	}
}
