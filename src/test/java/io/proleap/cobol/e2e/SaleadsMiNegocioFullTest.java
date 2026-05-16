package io.proleap.cobol.e2e;

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
import java.util.Objects;
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

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[^\\s@]+@[^\\s@]+\\.[^\\s@]+\\b");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;

	private final Map<String, String> finalReport = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();
	private final List<String> stepFailures = new ArrayList<>();

	@Before
	public void setUp() throws IOException {
		final String baseUrl = firstNonBlank(System.getProperty("saleads.url"), System.getProperty("saleads.baseUrl"),
				System.getenv("SALEADS_URL"), System.getenv("SALEADS_BASE_URL"));

		Assume.assumeTrue(
				"Set saleads.url/saleads.baseUrl or SALEADS_URL/SALEADS_BASE_URL to the login page URL for the target environment.",
				baseUrl != null);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--lang=es-ES");

		if (Boolean.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"),
				"true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String runTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDirectory = Paths.get("target", "saleads-evidence", runTimestamp);
		Files.createDirectories(evidenceDirectory);

		driver.get(baseUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Informacion General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Terminos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Politica de Privacidad", this::stepValidatePoliticaDePrivacidad);

		printFinalReport();

		if (!stepFailures.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow failed:\n - " + String.join("\n - ", stepFailures));
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		final Set<String> handlesBeforeLoginClick = driver.getWindowHandles();
		clickByTextAndWait("Google");
		chooseGoogleAccountIfVisible(handlesBeforeLoginClick);

		assertSidebarVisible();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		expandMiNegocioMenu();
		assertTextVisibleContains("Agregar Negocio");
		assertTextVisibleContains("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByTextAndWait("Agregar Negocio");

		assertTextVisibleContains("Crear Nuevo Negocio");
		assertTextVisibleContains("Nombre del Negocio");
		assertTextVisibleContains("Tienes 2 de 3 negocios");
		assertTextVisibleContains("Cancelar");
		assertTextVisibleContains("Crear Negocio");

		final WebElement businessNameInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')]")));
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		waitForUiToLoad();

		takeScreenshot("03-agregar-negocio-modal");
		clickByTextAndWait("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byContainsText("Crear Nuevo Negocio")));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioMenu();
		clickByTextAndWait("Administrar Negocios");

		assertTextVisibleContains("Informaci");
		assertTextVisibleContains("Detalles de la Cuenta");
		assertTextVisibleContains("Tus Negocios");
		assertTextVisibleContains("Secci");

		takeFullPageScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisibleContains("Informaci");
		assertTextVisibleContains("BUSINESS PLAN");
		assertTextVisibleContains("Cambiar Plan");

		final String pageText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
		Assert.assertTrue("User email is visible.", EMAIL_PATTERN.matcher(pageText).find());

		final String sanitizedText = pageText.replace("BUSINESS PLAN", "").replace("Cambiar Plan", "")
				.replaceAll("(?i)informaci[oó]n\\s+general", "").replaceAll("\\s+", " ").trim();
		Assert.assertTrue("User name is visible.", sanitizedText.matches(".*[A-Za-z]{3,}\\s+[A-Za-z]{3,}.*"));
	}

	private void stepValidateDetallesDeLaCuenta() {
		assertTextVisibleContains("Cuenta creada");
		assertTextVisibleContains("Estado activo");
		assertTextVisibleContains("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisibleContains("Tus Negocios");
		assertTextVisibleContains("Agregar Negocio");
		assertTextVisibleContains("Tienes 2 de 3 negocios");

		final List<WebElement> businessEntries = driver.findElements(By.xpath(
				"//*[contains(normalize-space(.), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]//*[self::li or self::tr or self::article or self::div[contains(@class,'card')]]"));
		Assert.assertFalse("Business list is visible.", businessEntries.isEmpty());
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		final String url = openLegalPageAndCaptureEvidence("rminos y Condiciones", "rminos y Condiciones",
				"08-terminos-y-condiciones");
		capturedUrls.put("Terminos y Condiciones URL", url);
	}

	private void stepValidatePoliticaDePrivacidad() throws IOException {
		final String url = openLegalPageAndCaptureEvidence("Privacidad", "Privacidad", "09-politica-de-privacidad");
		capturedUrls.put("Politica de Privacidad URL", url);
	}

	private void runStep(final String stepName, final StepAction stepAction) {
		try {
			stepAction.run();
			finalReport.put(stepName, "PASS");
		} catch (final Throwable throwable) {
			final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getMessage();
			finalReport.put(stepName, "FAIL - " + message);
			stepFailures.add(stepName + ": " + message);
			try {
				takeScreenshot("failed-" + toSlug(stepName));
			} catch (final IOException ignored) {
				// Best effort evidence capture on failures.
			}
		}
	}

	private String openLegalPageAndCaptureEvidence(final String linkTextFragment, final String headingTextFragment,
			final String screenshotName) throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByTextAndWait(linkTextFragment);

		try {
			new WebDriverWait(driver, Duration.ofSeconds(10)).until(webDriver -> webDriver.getWindowHandles().size() > handlesBeforeClick.size()
					|| !Objects.equals(originalUrl, webDriver.getCurrentUrl()));
		} catch (final TimeoutException ignored) {
			// Navigation can be slow; explicit checks below will fail with context if needed.
		}

		boolean switchedToNewTab = false;
		final Set<String> handlesAfterClick = driver.getWindowHandles();
		if (handlesAfterClick.size() > handlesBeforeClick.size()) {
			for (final String handle : handlesAfterClick) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					switchedToNewTab = true;
					break;
				}
			}
		}

		waitForUiToLoad();
		assertTextVisibleContains(headingTextFragment);

		final String legalBodyText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
		Assert.assertTrue("Legal content text is visible.", legalBodyText.trim().length() > 100);

		takeScreenshot(screenshotName);
		final String capturedUrl = driver.getCurrentUrl();

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return capturedUrl;
	}

	private void expandMiNegocioMenu() {
		assertSidebarVisible();

		if (!isTextVisibleContains("Mi Negocio")) {
			clickByTextAndWait("Negocio");
		}

		clickByTextAndWait("Mi Negocio");
		if (!isTextVisibleContains("Agregar Negocio") || !isTextVisibleContains("Administrar Negocios")) {
			clickByTextAndWait("Mi Negocio");
		}
	}

	private void chooseGoogleAccountIfVisible(final Set<String> originalHandles) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10)).until(
					webDriver -> webDriver.getWindowHandles().size() > originalHandles.size() || isTextVisibleContains("Google"));
		} catch (final TimeoutException ignored) {
			return;
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!originalHandles.contains(handle)) {
				driver.switchTo().window(handle);
				break;
			}
		}

		try {
			final WebElement accountOption = new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(ExpectedConditions.visibilityOfElementLocated(byContainsText(GOOGLE_ACCOUNT_EMAIL)));
			clickAndWait(accountOption);
		} catch (final TimeoutException ignored) {
			// Session may already be authenticated and skip account chooser.
		}

		for (final String handle : originalHandles) {
			if (driver.getWindowHandles().contains(handle)) {
				driver.switchTo().window(handle);
				break;
			}
		}

		waitForUiToLoad();
	}

	private void assertSidebarVisible() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//aside | //nav[contains(@class, 'sidebar')] | //nav[.//*[contains(normalize-space(.), 'Negocio')]]")));
	}

	private void clickByTextAndWait(final String textFragment) {
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(byClickableContainsText(textFragment)));
		clickAndWait(element);
	}

	private void clickAndWait(final WebElement element) {
		try {
			element.click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));

		try {
			new WebDriverWait(driver, Duration.ofSeconds(8)).until(ExpectedConditions.invisibilityOfElementLocated(
					By.xpath("//*[contains(@class,'loading') or contains(@class,'spinner') or contains(@class,'loader')]")));
		} catch (final TimeoutException ignored) {
			// Loader selectors are generic and may not exist in all environments.
		}
	}

	private boolean isTextVisibleContains(final String textFragment) {
		return driver.findElements(byContainsText(textFragment)).stream().anyMatch(WebElement::isDisplayed);
	}

	private void assertTextVisibleContains(final String textFragment) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(byContainsText(textFragment)));
	}

	private By byContainsText(final String textFragment) {
		return By.xpath("//*[contains(normalize-space(.), " + toXpathLiteral(textFragment) + ")]");
	}

	private By byClickableContainsText(final String textFragment) {
		final String literal = toXpathLiteral(textFragment);
		return By.xpath("//button[contains(normalize-space(.), " + literal + ")]" + " | //a[contains(normalize-space(.), "
				+ literal + ")]" + " | //*[@role='button' and contains(normalize-space(.), " + literal + ")]"
				+ " | //*[contains(@class,'sidebar') or contains(@class,'menu') or contains(@class,'nav')]//*[contains(normalize-space(.), "
				+ literal + ")]");
	}

	private void takeScreenshot(final String screenshotName) throws IOException {
		final Path destination = evidenceDirectory.resolve(screenshotName + ".png");
		final Path screenshotPath = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(screenshotPath, destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void takeFullPageScreenshot(final String screenshotName) throws IOException {
		final Dimension previousSize = driver.manage().window().getSize();
		final Long fullHeight = ((Number) ((JavascriptExecutor) driver).executeScript(
				"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);")).longValue();
		driver.manage().window().setSize(new Dimension(1920, Math.min(fullHeight.intValue() + 200, 6000)));
		waitForUiToLoad();
		takeScreenshot(screenshotName);
		driver.manage().window().setSize(previousSize);
		waitForUiToLoad();
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Workflow Report ===");
		for (final Map.Entry<String, String> entry : finalReport.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}

		for (final Map.Entry<String, String> entry : capturedUrls.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
	}

	private String toSlug(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder concat = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			concat.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				concat.append(", \"'\", ");
			}
		}
		concat.append(")");
		return concat.toString();
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
