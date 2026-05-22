package io.proleap.cobol.e2e;

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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN = "Administrar Negocios view";
	private static final String STEP_INFO = "Información General";
	private static final String STEP_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Términos y Condiciones";
	private static final String STEP_PRIVACY = "Política de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private final Map<String, String> evidence = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = getConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL or -Dsaleads.login.url with the environment login URL to run this E2E test.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(getConfigWithDefault("saleads.headless", "SALEADS_HEADLESS", "true"));
		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		appWindowHandle = driver.getWindowHandle();

		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		screenshotDir = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(screenshotDir);

		driver.get(loginUrl);
		waitForUiToSettle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		executeStep(STEP_LOGIN, this::stepLoginWithGoogle);
		executeStep(STEP_MENU, this::stepOpenMiNegocioMenu);
		executeStep(STEP_MODAL, this::stepValidateAgregarNegocioModal);
		executeStep(STEP_ADMIN, this::stepOpenAdministrarNegocios);
		executeStep(STEP_INFO, this::stepValidateInformacionGeneral);
		executeStep(STEP_DETAILS, this::stepValidateDetallesCuenta);
		executeStep(STEP_BUSINESSES, this::stepValidateTusNegocios);
		executeStep(STEP_TERMS, () -> stepValidateLegalLink("Términos y Condiciones", "Terminos y Condiciones",
				"terms-and-conditions", STEP_TERMS));
		executeStep(STEP_PRIVACY,
				() -> stepValidateLegalLink("Política de Privacidad", "Politica de Privacidad", "privacy-policy",
						STEP_PRIVACY));

		printFinalReport();
		final boolean allPassed = stepResults.values().stream().allMatch(Boolean::booleanValue);
		Assert.assertTrue("One or more SaleADS Mi Negocio validations failed. Review test output for details.", allPassed);
	}

	private void stepLoginWithGoogle() throws Exception {
		final Set<String> initialHandles = driver.getWindowHandles();
		clickByAnyText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google", "Continuar con Google",
				"Google");
		switchToNewWindowIfOpened(initialHandles);

		clickIfVisible(Duration.ofSeconds(10), "juanlucasbarbiergarzon@gmail.com");
		switchBackToAppWindow();

		waitForAnyText(Duration.ofSeconds(45), "Negocio", "Mi Negocio");
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside|//nav")));
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickIfVisible(Duration.ofSeconds(8), "Negocio");
		clickByAnyText("Mi Negocio");

		if (!isAnyTextVisible(Duration.ofSeconds(5), "Agregar Negocio", "Administrar Negocios")) {
			// Retry once because some sidebars toggle on second click.
			clickByAnyText("Mi Negocio");
		}

		waitForAnyText(Duration.ofSeconds(20), "Agregar Negocio");
		waitForAnyText(Duration.ofSeconds(20), "Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByAnyText("Agregar Negocio");

		waitForAnyText(Duration.ofSeconds(20), "Crear Nuevo Negocio");
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//input[@placeholder='Nombre del Negocio'] | //input[contains(@aria-label,'Nombre del Negocio')] | //label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]")));
		waitForAnyText(Duration.ofSeconds(20), "Tienes 2 de 3 negocios");
		waitForAnyText(Duration.ofSeconds(20), "Cancelar");
		waitForAnyText(Duration.ofSeconds(20), "Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final Optional<WebElement> nameField = findFirstVisible(Duration.ofSeconds(5),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"));
		if (nameField.isPresent()) {
			nameField.get().click();
			nameField.get().sendKeys("Negocio Prueba Automatización");
			waitForUiToSettle();
		}
		clickByAnyText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byText("Crear Nuevo Negocio")));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isAnyTextVisible(Duration.ofSeconds(5), "Administrar Negocios")) {
			clickIfVisible(Duration.ofSeconds(8), "Negocio");
			clickByAnyText("Mi Negocio");
		}

		clickByAnyText("Administrar Negocios");
		waitForAnyText(Duration.ofSeconds(30), "Información General");
		waitForAnyText(Duration.ofSeconds(30), "Detalles de la Cuenta");
		waitForAnyText(Duration.ofSeconds(30), "Tus Negocios");
		waitForAnyText(Duration.ofSeconds(30), "Sección Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		final WebElement section = findSectionByTitle("Información General");
		final List<String> sectionTexts = collectVisibleTexts(section);
		final boolean hasEmail = sectionTexts.stream().anyMatch(text -> EMAIL_PATTERN.matcher(text).matches());
		final boolean hasName = sectionTexts.stream().anyMatch(this::looksLikeName);

		Assert.assertTrue("Expected user name to be visible in Información General.", hasName);
		Assert.assertTrue("Expected user email to be visible in Información General.", hasEmail);
		waitForAnyText(Duration.ofSeconds(10), "BUSINESS PLAN");
		waitForAnyText(Duration.ofSeconds(10), "Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		findSectionByTitle("Detalles de la Cuenta");
		waitForAnyText(Duration.ofSeconds(10), "Cuenta creada");
		waitForAnyText(Duration.ofSeconds(10), "Estado activo");
		waitForAnyText(Duration.ofSeconds(10), "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByTitle("Tus Negocios");
		waitForAnyText(Duration.ofSeconds(10), "Agregar Negocio");
		waitForAnyText(Duration.ofSeconds(10), "Tienes 2 de 3 negocios");

		final List<WebElement> candidates = section.findElements(By.xpath(".//li | .//tr | .//article | .//div[@role='row']"));
		final boolean hasVisibleCandidates = candidates.stream().anyMatch(WebElement::isDisplayed);
		final boolean hasBusinessCard = !section.findElements(By.xpath(".//*[contains(@class,'business')]")).isEmpty();
		Assert.assertTrue("Expected business list entries to be visible in Tus Negocios.",
				hasVisibleCandidates || hasBusinessCard);
	}

	private void stepValidateLegalLink(final String linkText, final String fallbackLinkText, final String screenshotName,
			final String reportKey) throws Exception {
		final String currentHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByAnyText(linkText, fallbackLinkText);
		final Optional<String> newWindowHandle = waitForNewWindow(handlesBeforeClick, Duration.ofSeconds(10));
		if (newWindowHandle.isPresent()) {
			driver.switchTo().window(newWindowHandle.get());
		}

		waitForAnyText(Duration.ofSeconds(20), linkText, fallbackLinkText);
		assertLegalContentVisible();
		takeScreenshot(screenshotName);
		evidence.put(reportKey + " URL", driver.getCurrentUrl());

		if (newWindowHandle.isPresent()) {
			driver.close();
			driver.switchTo().window(currentHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToSettle();
	}

	private void executeStep(final String stepName, final CheckedRunnable step) {
		try {
			step.run();
			stepResults.put(stepName, Boolean.TRUE);
		} catch (final Throwable throwable) {
			stepResults.put(stepName, Boolean.FALSE);
			stepErrors.put(stepName, throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage());
			try {
				takeScreenshot("failure-" + sanitizeFileName(stepName));
			} catch (final Exception ignored) {
				// Best effort evidence collection for failed steps.
			}
		}
	}

	private void clickByAnyText(final String... texts) {
		WebDriverException lastException = null;
		for (final String text : texts) {
			try {
				final WebElement target = wait.until(ExpectedConditions.elementToBeClickable(byClickableText(text)));
				scrollIntoView(target);
				target.click();
				waitForUiToSettle();
				return;
			} catch (final TimeoutException | WebDriverException exception) {
				lastException = exception;
			}
		}
		throw new AssertionError("Could not click any expected text: " + String.join(", ", texts), lastException);
	}

	private void clickIfVisible(final Duration timeout, final String text) {
		final Optional<WebElement> element = findVisibleElementByText(timeout, text);
		if (element.isPresent()) {
			scrollIntoView(element.get());
			element.get().click();
			waitForUiToSettle();
		}
	}

	private Optional<WebElement> findVisibleElementByText(final Duration timeout, final String text) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			return Optional.of(shortWait.until(ExpectedConditions.visibilityOfElementLocated(byText(text))));
		} catch (final TimeoutException ignored) {
			return Optional.empty();
		}
	}

	private void waitForAnyText(final Duration timeout, final String... texts) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		shortWait.until(d -> {
			for (final String text : texts) {
				final List<WebElement> matches = d.findElements(byText(text));
				for (final WebElement match : matches) {
					if (match.isDisplayed()) {
						return true;
					}
				}
			}
			return false;
		});
	}

	private boolean isAnyTextVisible(final Duration timeout, final String... texts) {
		try {
			waitForAnyText(timeout, texts);
			return true;
		} catch (final TimeoutException exception) {
			return false;
		}
	}

	@SafeVarargs
	private final Optional<WebElement> findFirstVisible(final Duration timeout, final By... locators) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		for (final By locator : locators) {
			try {
				final WebElement element = shortWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return Optional.of(element);
			} catch (final TimeoutException ignored) {
				// Continue trying the next locator.
			}
		}
		return Optional.empty();
	}

	private Optional<String> waitForNewWindow(final Set<String> previousHandles, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			return Optional.of(shortWait.until(d -> {
				final Set<String> currentHandles = d.getWindowHandles();
				if (currentHandles.size() <= previousHandles.size()) {
					return null;
				}
				for (final String handle : currentHandles) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			}));
		} catch (final TimeoutException ignored) {
			return Optional.empty();
		}
	}

	private void switchToNewWindowIfOpened(final Set<String> previousHandles) {
		final Optional<String> newWindow = waitForNewWindow(previousHandles, Duration.ofSeconds(10));
		newWindow.ifPresent(handle -> driver.switchTo().window(handle));
	}

	private void switchBackToAppWindow() {
		if (!driver.getWindowHandles().contains(appWindowHandle)) {
			appWindowHandle = driver.getWindowHandle();
			return;
		}
		driver.switchTo().window(appWindowHandle);
	}

	private WebElement findSectionByTitle(final String title) {
		waitForAnyText(Duration.ofSeconds(15), title);
		final Optional<WebElement> section = findFirstVisible(Duration.ofSeconds(10),
				By.xpath("//section[.//*[normalize-space()=" + toXPathLiteral(title) + "]]"),
				By.xpath("//div[contains(@class,'card') and .//*[normalize-space()=" + toXPathLiteral(title) + "]]"),
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][normalize-space()=" + toXPathLiteral(title)
						+ "]/ancestor::*[self::section or self::div][1]"));
		return section.orElseThrow(() -> new AssertionError("Could not locate section titled: " + title));
	}

	private void assertLegalContentVisible() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p | //li"));
		int totalChars = 0;
		for (final WebElement paragraph : paragraphs) {
			if (paragraph.isDisplayed()) {
				totalChars += paragraph.getText().trim().length();
			}
			if (totalChars > 120) {
				return;
			}
		}
		throw new AssertionError("Expected legal content text to be visible.");
	}

	private List<String> collectVisibleTexts(final WebElement root) {
		final List<WebElement> nodes = root.findElements(By.xpath(".//*[not(self::script) and not(self::style)]"));
		final List<String> values = new ArrayList<>();
		for (final WebElement node : nodes) {
			if (!node.isDisplayed()) {
				continue;
			}
			final String text = node.getText() == null ? "" : node.getText().trim();
			if (!text.isBlank()) {
				values.add(text);
			}
		}
		return values;
	}

	private boolean looksLikeName(final String value) {
		final String normalized = value.trim();
		if (normalized.isBlank()) {
			return false;
		}
		if (normalized.equalsIgnoreCase("Información General") || normalized.equalsIgnoreCase("BUSINESS PLAN")
				|| normalized.equalsIgnoreCase("Cambiar Plan")) {
			return false;
		}
		if (EMAIL_PATTERN.matcher(normalized).matches()) {
			return false;
		}
		return normalized.length() >= 3 && normalized.length() <= 80;
	}

	private void waitForUiToSettle() {
		wait.until(driverRef -> {
			final Object readyState = ((JavascriptExecutor) driverRef).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(readyState));
		});
		try {
			Thread.sleep(800L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String fileNamePrefix) throws IOException {
		final String fileName = sanitizeFileName(fileNamePrefix) + ".png";
		final Path screenshotPath = screenshotDir.resolve(fileName);
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
		evidence.put("Screenshot " + fileNamePrefix, screenshotPath.toString());
	}

	private void printFinalReport() {
		final List<String> orderedFields = List.of(STEP_LOGIN, STEP_MENU, STEP_MODAL, STEP_ADMIN, STEP_INFO, STEP_DETAILS,
				STEP_BUSINESSES, STEP_TERMS, STEP_PRIVACY);
		System.out.println("=== SaleADS Mi Negocio Workflow Final Report ===");
		for (final String field : orderedFields) {
			final boolean passed = Boolean.TRUE.equals(stepResults.get(field));
			System.out.println(field + ": " + (passed ? "PASS" : "FAIL"));
			if (!passed && stepErrors.containsKey(field)) {
				System.out.println("  Error: " + stepErrors.get(field));
			}
		}
		if (!evidence.isEmpty()) {
			System.out.println("=== Evidence ===");
			evidence.forEach((key, value) -> System.out.println(key + ": " + value));
		}
	}

	private By byClickableText(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath("//button[normalize-space()=" + literal + "] | //a[normalize-space()=" + literal
				+ "] | //*[@role='button' and normalize-space()=" + literal + "] | //*[normalize-space()=" + literal + "]");
	}

	private By byText(final String text) {
		return By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + "]");
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});",
				element);
	}

	private String sanitizeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String getConfig(final String systemPropertyName, final String environmentVariableName) {
		final String fromProperty = System.getProperty(systemPropertyName);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}
		final String fromEnvironment = System.getenv(environmentVariableName);
		if (fromEnvironment != null && !fromEnvironment.isBlank()) {
			return fromEnvironment.trim();
		}
		return null;
	}

	private String getConfigWithDefault(final String systemPropertyName, final String environmentVariableName,
			final String fallbackValue) {
		final String configured = getConfig(systemPropertyName, environmentVariableName);
		return configured == null ? fallbackValue : configured;
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final char[] characters = value.toCharArray();
		for (int i = 0; i < characters.length; i++) {
			if (i > 0) {
				builder.append(", ");
			}
			if (characters[i] == '\'') {
				builder.append("\"'\"");
			} else if (characters[i] == '"') {
				builder.append("'\"'");
			} else {
				builder.append("'").append(characters[i]).append("'");
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
