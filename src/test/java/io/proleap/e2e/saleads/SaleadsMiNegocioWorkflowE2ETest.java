package io.proleap.e2e.saleads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioWorkflowE2ETest {

	private static final String RUN_E2E_PROPERTY = "run.saleads.e2e";
	private static final String LOGIN_URL_PROPERTY = "saleads.login.url";
	private static final String HEADLESS_PROPERTY = "saleads.headless";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> reportStatus = new LinkedHashMap<>();
	private final Map<String, String> reportDetails = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set -D" + RUN_E2E_PROPERTY + "=true to run this UI test.",
				Boolean.parseBoolean(System.getProperty(RUN_E2E_PROPERTY, "false")));

		screenshotDirectory = Paths.get("target", "saleads-e2e-screenshots",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(screenshotDirectory);

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(System.getProperty(HEADLESS_PROPERTY, "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String loginUrl = System.getProperty(LOGIN_URL_PROPERTY, "").trim();
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() {
		try {
			if (!reportStatus.isEmpty()) {
				System.out.println(renderFinalReport());
			}
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final List<String> failedSteps = reportStatus.entrySet().stream().filter(entry -> !entry.getValue())
				.map(Map.Entry::getKey).collect(Collectors.toList());

		assertTrue("Workflow contains failures: " + failedSteps + "\n" + renderFinalReport(), failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		ensureOnLoginPage();

		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeLogin = new LinkedHashSet<>(driver.getWindowHandles());

		final WebElement googleLoginButton = waitForAnyClickable(
				byClickableExactText("Sign in with Google"),
				byClickableExactText("Iniciar sesión con Google"),
				byClickableExactText("Login con Google"),
				byClickableContainingText("Google"));
		clickAndWait(googleLoginButton);

		selectGoogleAccountIfVisible(appWindow, handlesBeforeLogin);

		waitForAnyVisible(byExactText("Negocio"), By.xpath("//aside"), By.xpath("//nav"));
		waitForVisible(byExactText("Negocio"));
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		waitForVisible(byExactText("Negocio"));
		clickIfVisible(byClickableExactText("Negocio"));
		clickAndWait(waitForAnyClickable(byClickableExactText("Mi Negocio"), byClickableContainingText("Mi Negocio")));

		waitForVisible(byExactText("Agregar Negocio"));
		waitForVisible(byExactText("Administrar Negocios"));
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickAndWait(waitForAnyClickable(byClickableExactText("Agregar Negocio"), byClickableContainingText("Agregar Negocio")));

		final WebElement modal = waitForVisible(By.xpath("//*[self::div or self::section][.//*[normalize-space()="
				+ xpathLiteral("Crear Nuevo Negocio") + "]]"));
		assertElementVisible(modal, By.xpath(".//*[normalize-space()=" + xpathLiteral("Crear Nuevo Negocio") + "]"));
		assertElementVisible(modal, By.xpath(".//*[normalize-space()=" + xpathLiteral("Nombre del Negocio") + "]"));
		assertElementVisible(modal,
				By.xpath(".//*[contains(normalize-space(), " + xpathLiteral("Tienes 2 de 3 negocios") + ")]"));
		assertElementVisible(modal, By.xpath(".//*[normalize-space()=" + xpathLiteral("Cancelar") + "]"));
		assertElementVisible(modal, By.xpath(".//*[normalize-space()=" + xpathLiteral("Crear Negocio") + "]"));

		final List<WebElement> inputs = modal.findElements(By.xpath(".//input"));
		assertFalse("Expected an input field inside the modal.", inputs.isEmpty());
		inputs.get(0).click();
		inputs.get(0).clear();
		inputs.get(0).sendKeys("Negocio Prueba Automatización");

		takeScreenshot("03-agregar-negocio-modal");

		clickAndWait(waitForClickableWithin(modal, By.xpath(".//*[normalize-space()=" + xpathLiteral("Cancelar") + "]")));
		wait.until(ExpectedConditions.invisibilityOf(modal));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isVisible(byExactText("Administrar Negocios"))) {
			clickAndWait(waitForAnyClickable(byClickableExactText("Mi Negocio"), byClickableContainingText("Mi Negocio")));
		}

		clickAndWait(waitForAnyClickable(byClickableExactText("Administrar Negocios"),
				byClickableContainingText("Administrar Negocios")));

		waitForVisible(byExactText("Información General"));
		waitForVisible(byExactText("Detalles de la Cuenta"));
		waitForVisible(byExactText("Tus Negocios"));
		waitForVisible(byExactText("Sección Legal"));
		takeScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement infoSection = waitForSection("Información General");
		final String sectionText = normalizedText(infoSection);

		assertTrue("Expected user email in Información General section.", EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("Expected BUSINESS PLAN text in Información General section.", sectionText.contains("BUSINESS PLAN"));
		assertElementVisible(infoSection, By.xpath(".//*[normalize-space()=" + xpathLiteral("Cambiar Plan") + "]"));

		final boolean hasUserName = Arrays.stream(sectionText.split("\\R")).map(String::trim).filter(line -> !line.isEmpty())
				.anyMatch(line -> !line.contains("@") && !line.equalsIgnoreCase("BUSINESS PLAN")
						&& !line.equalsIgnoreCase("Cambiar Plan") && !line.equalsIgnoreCase("Información General"));
		assertTrue("Expected a user name text in Información General section.", hasUserName);
	}

	private void stepValidateDetallesCuenta() {
		final WebElement detailsSection = waitForSection("Detalles de la Cuenta");
		final String sectionText = normalizedText(detailsSection);

		assertTrue("Expected 'Cuenta creada' text.", sectionText.contains("Cuenta creada"));
		assertTrue("Expected 'Estado activo' text.", sectionText.contains("Estado activo"));
		assertTrue("Expected 'Idioma seleccionado' text.", sectionText.contains("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement businessesSection = waitForSection("Tus Negocios");
		final String sectionText = normalizedText(businessesSection);

		assertElementVisible(businessesSection,
				By.xpath(".//*[normalize-space()=" + xpathLiteral("Agregar Negocio") + "]"));
		assertTrue("Expected 'Tienes 2 de 3 negocios' text.", sectionText.contains("Tienes 2 de 3 negocios"));

		final List<WebElement> businessCandidates = businessesSection.findElements(By.xpath(
				".//li | .//article | .//tr | .//div[contains(@class,'negocio') or contains(@class,'business')]"));
		final long nonEmptyLines = Arrays.stream(sectionText.split("\\R")).map(String::trim).filter(line -> !line.isEmpty())
				.count();
		assertTrue("Expected business list content in Tus Negocios section.",
				!businessCandidates.isEmpty() || nonEmptyLines >= 4);
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		final String finalUrl = openLegalDocument("Términos y Condiciones", "Términos y Condiciones",
				"05-terminos-y-condiciones");
		reportDetails.put("Términos y Condiciones", "Final URL: " + finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		final String finalUrl = openLegalDocument("Política de Privacidad", "Política de Privacidad",
				"06-politica-de-privacidad");
		reportDetails.put("Política de Privacidad", "Final URL: " + finalUrl);
	}

	private String openLegalDocument(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		waitForVisible(byExactText("Sección Legal"));

		final String appWindow = driver.getWindowHandle();
		final String appUrlBeforeClick = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickAndWait(waitForAnyClickable(byClickableExactText(linkText), byClickableContainingText(linkText)));

		final String newWindow = waitForNewWindowHandle(handlesBeforeClick);
		final boolean openedNewWindow = newWindow != null;

		if (openedNewWindow) {
			driver.switchTo().window(newWindow);
			waitForUiToLoad();
		}

		waitForAnyVisible(byExactText(expectedHeading), byContainingText(expectedHeading));
		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewWindow) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
			waitForVisible(byExactText("Sección Legal"));
		} else if (!appUrlBeforeClick.equals(finalUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
			waitForVisible(byExactText("Sección Legal"));
		}

		return finalUrl;
	}

	private void ensureOnLoginPage() {
		final String currentUrl = driver.getCurrentUrl();
		final boolean looksBlank = currentUrl == null || currentUrl.isBlank() || "data:,".equals(currentUrl)
				|| "about:blank".equals(currentUrl);
		assertFalse("Browser is not on a login page. Provide -D" + LOGIN_URL_PROPERTY
				+ "=<saleads-login-url> for the current environment.", looksBlank);
	}

	private void selectGoogleAccountIfVisible(final String appWindow, final Set<String> handlesBeforeLogin) {
		final String newWindow = waitForNewWindowHandle(handlesBeforeLogin);
		if (newWindow != null) {
			driver.switchTo().window(newWindow);
			waitForUiToLoad();
		}

		final List<WebElement> accountCandidates = driver.findElements(
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]"));

		if (!accountCandidates.isEmpty()) {
			clickAndWait(accountCandidates.get(0));
		}

		try {
			if (!driver.getWindowHandle().equals(appWindow) && driver.getWindowHandles().contains(appWindow)) {
				driver.switchTo().window(appWindow);
				waitForUiToLoad();
			}
		} catch (final NoSuchWindowException ignored) {
			if (driver.getWindowHandles().contains(appWindow)) {
				driver.switchTo().window(appWindow);
				waitForUiToLoad();
			}
		}
	}

	private void runStep(final String stepName, final CheckedAction action) {
		try {
			action.run();
			reportStatus.put(stepName, true);
			reportDetails.putIfAbsent(stepName, "PASS");
		} catch (final Throwable throwable) {
			reportStatus.put(stepName, false);
			reportDetails.put(stepName, firstLine(throwable.getMessage()));
		}
	}

	private String renderFinalReport() {
		final StringBuilder report = new StringBuilder();
		report.append(System.lineSeparator()).append("=== SaleADS Mi Negocio Workflow Report ===")
				.append(System.lineSeparator());

		for (final Map.Entry<String, Boolean> stepResult : reportStatus.entrySet()) {
			final String step = stepResult.getKey();
			final String detail = reportDetails.getOrDefault(step, "");
			report.append(step).append(": ").append(Boolean.TRUE.equals(stepResult.getValue()) ? "PASS" : "FAIL");
			if (!detail.isBlank()) {
				report.append(" - ").append(detail);
			}
			report.append(System.lineSeparator());
		}

		report.append("Screenshots: ").append(screenshotDirectory.toAbsolutePath()).append(System.lineSeparator());
		report.append("=========================================").append(System.lineSeparator());
		return report.toString();
	}

	private WebElement waitForSection(final String titleText) {
		return waitForVisible(By.xpath(
				"//*[self::section or self::div][.//*[normalize-space()=" + xpathLiteral(titleText) + "]]"));
	}

	private void takeScreenshot(final String name) throws IOException {
		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = screenshotDirectory.resolve(name + ".png");
		Files.copy(screenshotFile.toPath(), destination);
	}

	private String waitForNewWindowHandle(final Set<String> previousHandles) {
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(webDriver -> webDriver.getWindowHandles().size() > previousHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!previousHandles.contains(handle)) {
					return handle;
				}
			}
		} catch (final TimeoutException ignored) {
			// No popup window was opened, continue in the current tab.
		}

		return null;
	}

	private WebElement waitForAnyVisible(final By... locators) {
		TimeoutException lastException = null;
		for (final By locator : locators) {
			try {
				return new WebDriverWait(driver, SHORT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final TimeoutException timeoutException) {
				lastException = timeoutException;
			}
		}

		if (lastException == null) {
			throw new TimeoutException("No locator provided.");
		}
		throw lastException;
	}

	private WebElement waitForAnyClickable(final By... locators) {
		TimeoutException lastException = null;
		for (final By locator : locators) {
			try {
				return new WebDriverWait(driver, SHORT_TIMEOUT).until(ExpectedConditions.elementToBeClickable(locator));
			} catch (final TimeoutException timeoutException) {
				lastException = timeoutException;
			}
		}

		if (lastException == null) {
			throw new TimeoutException("No locator provided.");
		}
		throw lastException;
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private WebElement waitForClickableWithin(final WebElement container, final By locator) {
		return wait.until(webDriver -> {
			try {
				final WebElement element = container.findElement(locator);
				return element.isDisplayed() && element.isEnabled() ? element : null;
			} catch (final NoSuchElementException exception) {
				return null;
			}
		});
	}

	private boolean isVisible(final By locator) {
		return driver.findElements(locator).stream().anyMatch(WebElement::isDisplayed);
	}

	private void clickIfVisible(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
			clickAndWait(elements.get(0));
		}
	}

	private void clickAndWait(final WebElement element) {
		try {
			element.click();
		} catch (final ElementClickInterceptedException exception) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
	}

	private void assertElementVisible(final WebElement container, final By locator) {
		final List<WebElement> elements = container.findElements(locator);
		assertFalse("Expected element not found in container: " + locator, elements.isEmpty());
		assertTrue("Expected element to be visible: " + locator, elements.stream().anyMatch(WebElement::isDisplayed));
	}

	private String normalizedText(final WebElement element) {
		return Arrays.stream(element.getText().split("\\R")).map(String::trim).filter(line -> !line.isEmpty())
				.collect(Collectors.joining(System.lineSeparator()));
	}

	private String firstLine(final String value) {
		if (value == null || value.isBlank()) {
			return "No error message available.";
		}
		return value.lines().findFirst().orElse(value);
	}

	private By byExactText(final String text) {
		return By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]");
	}

	private By byContainingText(final String text) {
		return By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]");
	}

	private By byClickableExactText(final String text) {
		return By.xpath("//*[self::button or self::a or @role='button' or self::span or self::div]"
				+ "[normalize-space()=" + xpathLiteral(text) + "]");
	}

	private By byClickableContainingText(final String text) {
		return By.xpath("//*[self::button or self::a or @role='button' or self::span or self::div]"
				+ "[contains(normalize-space(), " + xpathLiteral(text) + ")]");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final List<String> parts = new ArrayList<>();
		for (final String part : value.split("'")) {
			parts.add("'" + part + "'");
			parts.add("\"'\"");
		}
		parts.remove(parts.size() - 1);
		builder.append(String.join(", ", parts)).append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface CheckedAction {
		void run() throws Exception;
	}
}
