package io.proleap.cobol.e2e.saleads;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private int screenshotIndex;

	@Before
	public void setUp() throws IOException {
		driver = createDriver();
		driver.manage().window().setSize(new Dimension(1600, 1000));
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		screenshotDirectory = createScreenshotDirectory();
		navigateToLoginPage();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final Map<String, Boolean> report = initializeReport();
		final List<String> failures = new ArrayList<String>();
		final Map<String, String> legalUrls = new LinkedHashMap<String, String>();

		boolean canContinue = runStep("Login", report, failures, new ThrowingRunnable() {
			@Override
			public void run() {
				stepLogin();
			}
		}, true);

		canContinue = runStep("Mi Negocio menu", report, failures, new ThrowingRunnable() {
			@Override
			public void run() {
				stepOpenMiNegocioMenu();
			}
		}, canContinue);

		canContinue = runStep("Agregar Negocio modal", report, failures, new ThrowingRunnable() {
			@Override
			public void run() {
				stepValidateAgregarNegocioModal();
			}
		}, canContinue);

		canContinue = runStep("Administrar Negocios view", report, failures, new ThrowingRunnable() {
			@Override
			public void run() {
				stepOpenAdministrarNegocios();
			}
		}, canContinue);

		canContinue = runStep("Información General", report, failures, new ThrowingRunnable() {
			@Override
			public void run() {
				stepValidateInformacionGeneral();
			}
		}, canContinue);

		canContinue = runStep("Detalles de la Cuenta", report, failures, new ThrowingRunnable() {
			@Override
			public void run() {
				stepValidateDetallesCuenta();
			}
		}, canContinue);

		canContinue = runStep("Tus Negocios", report, failures, new ThrowingRunnable() {
			@Override
			public void run() {
				stepValidateTusNegocios();
			}
		}, canContinue);

		canContinue = runStep("Términos y Condiciones", report, failures, new ThrowingRunnable() {
			@Override
			public void run() throws IOException {
				final String finalUrl = openAndValidateLegalPage("Términos y Condiciones", "Términos y Condiciones",
						"legal_terminos_y_condiciones");
				legalUrls.put("Términos y Condiciones", finalUrl);
			}
		}, canContinue);

		runStep("Política de Privacidad", report, failures, new ThrowingRunnable() {
			@Override
			public void run() throws IOException {
				final String finalUrl = openAndValidateLegalPage("Política de Privacidad", "Política de Privacidad",
						"legal_politica_de_privacidad");
				legalUrls.put("Política de Privacidad", finalUrl);
			}
		}, canContinue);

		final String finalReport = buildFinalReport(report, legalUrls, failures);
		System.out.println(finalReport);

		Assert.assertTrue("One or more workflow validations failed.\n" + finalReport, allPassed(report));
	}

	private WebDriver createDriver() {
		final String remoteUrl = firstNonBlank(System.getenv("SALEADS_SELENIUM_REMOTE_URL"),
				System.getProperty("saleads.selenium.remote.url"));
		final boolean headless = Boolean.parseBoolean(firstNonBlank(System.getenv("SALEADS_HEADLESS"),
				System.getProperty("saleads.headless"), "true"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1000");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		if (remoteUrl != null) {
			return createRemoteDriver(remoteUrl, options);
		}

		return new ChromeDriver(options);
	}

	private WebDriver createRemoteDriver(final String remoteUrl, final MutableCapabilities capabilities) {
		try {
			return new RemoteWebDriver(new URL(remoteUrl), capabilities);
		} catch (final MalformedURLException e) {
			throw new IllegalArgumentException("Invalid SALEADS_SELENIUM_REMOTE_URL: " + remoteUrl, e);
		}
	}

	private void navigateToLoginPage() {
		final String loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getProperty("saleads.login.url"));

		if (loginUrl != null) {
			driver.get(loginUrl);
			waitForUiToLoad();
			return;
		}

		final String currentUrl = driver.getCurrentUrl();
		final boolean alreadyOnPage = currentUrl != null && !currentUrl.trim().isEmpty() && !"about:blank".equals(currentUrl)
				&& !currentUrl.startsWith("data:");

		Assert.assertTrue(
				"No login URL configured and browser is not on an existing page. "
						+ "Set SALEADS_LOGIN_URL (or -Dsaleads.login.url) or start from a browser session already on the login page.",
				alreadyOnPage);
		waitForUiToLoad();
	}

	private void stepLogin() throws IOException {
		final Set<String> existingWindows = driver.getWindowHandles();
		final String appWindow = driver.getWindowHandle();

		clickByTextContains("google");

		final String popupWindow = waitForNewWindow(existingWindows, Duration.ofSeconds(10));
		if (popupWindow != null) {
			driver.switchTo().window(popupWindow);
		}

		clickIfVisible(byExactText(GOOGLE_ACCOUNT_EMAIL), Duration.ofSeconds(10));

		if (popupWindow != null && driver.getWindowHandles().contains(appWindow)) {
			driver.switchTo().window(appWindow);
		}

		waitUntilAnyVisible(Duration.ofSeconds(60), byContainsText("Negocio"), By.xpath("//aside"), By.xpath("//nav"));
		Assert.assertTrue("Main application interface did not appear after Google login.",
				isAnyVisible(Duration.ofSeconds(15), By.xpath("//main"), By.xpath("//aside"), By.xpath("//nav")));
		Assert.assertTrue("Left sidebar navigation is not visible.",
				isAnyVisible(Duration.ofSeconds(15), By.xpath("//aside"), By.xpath("//nav//*[contains(normalize-space(.), 'Negocio')]")));

		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		waitUntilVisible(byVisibleText("Agregar Negocio"), Duration.ofSeconds(20));
		waitUntilVisible(byVisibleText("Administrar Negocios"), Duration.ofSeconds(20));

		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitUntilVisible(byVisibleText("Crear Nuevo Negocio"), Duration.ofSeconds(20));

		Assert.assertTrue("Input field 'Nombre del Negocio' is missing.", isAnyVisible(Duration.ofSeconds(10),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]")));
		waitUntilVisible(byContainsText("Tienes 2 de 3 negocios"), Duration.ofSeconds(10));
		waitUntilVisible(byVisibleText("Cancelar"), Duration.ofSeconds(10));
		waitUntilVisible(byVisibleText("Crear Negocio"), Duration.ofSeconds(10));

		fillInputIfVisible("Nombre del Negocio", "Negocio Prueba Automatizacion");
		captureScreenshot("03_agregar_negocio_modal");
		clickByVisibleText("Cancelar");
		waitUntilInvisible(byVisibleText("Crear Nuevo Negocio"), Duration.ofSeconds(10));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioMenuIfNeeded();
		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		waitUntilVisible(byVisibleText("Información General"), Duration.ofSeconds(30));
		waitUntilVisible(byVisibleText("Detalles de la Cuenta"), Duration.ofSeconds(30));
		waitUntilVisible(byVisibleText("Tus Negocios"), Duration.ofSeconds(30));
		waitUntilVisible(byVisibleText("Sección Legal"), Duration.ofSeconds(30));

		captureScreenshot("04_administrar_negocios_view");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = sectionWithHeading("Información General");
		final String text = normalizeWhitespace(section.getText());

		Assert.assertTrue("User email is not visible in Información General.", EMAIL_PATTERN.matcher(text).find());
		Assert.assertTrue("User name is not visible in Información General.", containsLikelyUserName(text));
		Assert.assertTrue("BUSINESS PLAN is not visible in Información General.", text.contains("BUSINESS PLAN"));
		Assert.assertTrue("Cambiar Plan button is missing in Información General.", isElementVisibleWithin(section,
				By.xpath(".//*[self::button or self::a][contains(normalize-space(.), 'Cambiar Plan')]")));
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = sectionWithHeading("Detalles de la Cuenta");
		final String text = normalizeWhitespace(section.getText());

		Assert.assertTrue("'Cuenta creada' is not visible.", text.contains("Cuenta creada"));
		Assert.assertTrue("'Estado activo' is not visible.", text.contains("Estado activo"));
		Assert.assertTrue("'Idioma seleccionado' is not visible.", text.contains("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = sectionWithHeading("Tus Negocios");
		final String text = normalizeWhitespace(section.getText());

		Assert.assertTrue("Business list is not visible in Tus Negocios.", hasBusinessList(section));
		Assert.assertTrue("'Agregar Negocio' button is missing in Tus Negocios.",
				isElementVisibleWithin(section, By.xpath(".//*[self::button or self::a][normalize-space()='Agregar Negocio']")));
		Assert.assertTrue("'Tienes 2 de 3 negocios' is not visible in Tus Negocios.", text.contains("Tienes 2 de 3 negocios"));
	}

	private String openAndValidateLegalPage(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);

		final String newWindowHandle = waitForNewWindow(windowsBeforeClick, Duration.ofSeconds(10));
		final boolean openedInNewTab = newWindowHandle != null;

		if (openedInNewTab) {
			driver.switchTo().window(newWindowHandle);
		}

		waitUntilVisible(byVisibleText(headingText), Duration.ofSeconds(30));

		final String bodyText = normalizeWhitespace(waitUntilVisible(By.tagName("body"), Duration.ofSeconds(10)).getText());
		Assert.assertTrue("Legal content text is not visible for " + linkText + ".", bodyText.length() > 120);

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();
		Assert.assertTrue("Final URL is empty for " + linkText + ".", finalUrl != null && !finalUrl.trim().isEmpty());

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		waitUntilVisible(byVisibleText("Sección Legal"), Duration.ofSeconds(30));
		return finalUrl;
	}

	private void expandMiNegocioMenuIfNeeded() {
		if (!isAnyVisible(Duration.ofSeconds(2), byVisibleText("Administrar Negocios"))) {
			clickByVisibleText("Mi Negocio");
		}
	}

	private void clickByVisibleText(final String text) {
		final By locator = By.xpath(
				"//button[normalize-space()='" + text + "']"
						+ " | //a[normalize-space()='" + text + "']"
						+ " | //*[@role='button' and normalize-space()='" + text + "']"
						+ " | //*[@role='menuitem' and normalize-space()='" + text + "']"
						+ " | //li[normalize-space()='" + text + "']"
						+ " | //span[normalize-space()='" + text + "']/ancestor::*[self::button or self::a or @role='button' or @role='menuitem' or self::li][1]");
		click(locator);
	}

	private void clickByTextContains(final String text) {
		final String lowerCase = text.toLowerCase(Locale.ROOT);
		final By locator = By.xpath(
				"//button[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'), '" + lowerCase
						+ "')]"
						+ " | //a[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'), '" + lowerCase
						+ "')]"
						+ " | //*[@role='button' and contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'), '"
						+ lowerCase + "')]");
		click(locator);
	}

	private void click(final By locator) {
		waitUntilVisible(locator, Duration.ofSeconds(20));
		wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
		waitForUiToLoad();
	}

	private void clickIfVisible(final By locator, final Duration timeout) {
		if (isAnyVisible(timeout, locator)) {
			wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
			waitForUiToLoad();
		}
	}

	private void fillInputIfVisible(final String label, final String value) {
		final By inputBy = By.xpath("//label[contains(normalize-space(), '" + label + "')]/following::input[1]"
				+ " | //input[@placeholder='" + label + "']"
				+ " | //input[contains(@aria-label, '" + label + "')]");

		if (isAnyVisible(Duration.ofSeconds(5), inputBy)) {
			final WebElement input = waitUntilVisible(inputBy, Duration.ofSeconds(5));
			input.clear();
			input.sendKeys(value);
			waitForUiToLoad();
		}
	}

	private WebElement sectionWithHeading(final String heading) {
		final By headingLocator = byVisibleText(heading);
		final WebElement headingElement = waitUntilVisible(headingLocator, Duration.ofSeconds(20));
		final WebElement section = headingElement
				.findElement(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
		return section;
	}

	private boolean hasBusinessList(final WebElement section) {
		return isElementVisibleWithin(section, By.xpath(".//li")) || isElementVisibleWithin(section, By.xpath(".//table"))
				|| isElementVisibleWithin(section, By.xpath(".//*[contains(@class, 'business')]"));
	}

	private boolean containsLikelyUserName(final String text) {
		final String[] lines = text.split("\\n");
		for (final String line : lines) {
			final String value = line.trim();
			if (value.isEmpty()) {
				continue;
			}
			if ("Información General".equals(value) || "BUSINESS PLAN".equals(value) || value.contains("@")
					|| value.contains("Cambiar Plan")) {
				continue;
			}
			if (value.matches(".*[A-Za-z].*")) {
				return true;
			}
		}
		return false;
	}

	private boolean runStep(final String stepName, final Map<String, Boolean> report, final List<String> failures,
			final ThrowingRunnable step, final boolean canContinue) {
		if (!canContinue) {
			report.put(stepName, Boolean.FALSE);
			failures.add(stepName + ": Not executed because a previous step failed.");
			return false;
		}

		try {
			step.run();
			report.put(stepName, Boolean.TRUE);
			return true;
		} catch (final Throwable e) {
			report.put(stepName, Boolean.FALSE);
			failures.add(stepName + ": " + rootMessage(e));
			return false;
		}
	}

	private boolean allPassed(final Map<String, Boolean> report) {
		for (final Boolean value : report.values()) {
			if (!Boolean.TRUE.equals(value)) {
				return false;
			}
		}
		return true;
	}

	private Map<String, Boolean> initializeReport() {
		final Map<String, Boolean> report = new LinkedHashMap<String, Boolean>();
		report.put("Login", Boolean.FALSE);
		report.put("Mi Negocio menu", Boolean.FALSE);
		report.put("Agregar Negocio modal", Boolean.FALSE);
		report.put("Administrar Negocios view", Boolean.FALSE);
		report.put("Información General", Boolean.FALSE);
		report.put("Detalles de la Cuenta", Boolean.FALSE);
		report.put("Tus Negocios", Boolean.FALSE);
		report.put("Términos y Condiciones", Boolean.FALSE);
		report.put("Política de Privacidad", Boolean.FALSE);
		return report;
	}

	private String buildFinalReport(final Map<String, Boolean> report, final Map<String, String> legalUrls,
			final List<String> failures) {
		final StringBuilder sb = new StringBuilder();
		sb.append("Final report for ").append(TEST_NAME).append('\n');
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL")
					.append('\n');
		}
		if (!legalUrls.isEmpty()) {
			sb.append("Final legal URLs:\n");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}
		sb.append("Screenshots: ").append(screenshotDirectory.toAbsolutePath()).append('\n');
		if (!failures.isEmpty()) {
			sb.append("Failure details:\n");
			for (final String failure : failures) {
				sb.append("  - ").append(failure).append('\n');
			}
		}
		return sb.toString();
	}

	private Path createScreenshotDirectory() throws IOException {
		final String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
		final Path directory = Paths.get("target", "screenshots", TEST_NAME, timestamp);
		return Files.createDirectories(directory);
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		screenshotIndex++;
		final String fileName = String.format("%02d_%s.png", Integer.valueOf(screenshotIndex), sanitizeForFileName(checkpointName));
		final Path output = screenshotDirectory.resolve(fileName);
		Files.copy(screenshot.toPath(), output, StandardCopyOption.REPLACE_EXISTING);
	}

	private String sanitizeForFileName(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("(^_|_$)", "");
	}

	private String waitForNewWindow(final Set<String> previousHandles, final Duration timeout) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			return shortWait.until(drv -> {
				final Set<String> handles = drv.getWindowHandles();
				for (final String handle : handles) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException e) {
			return null;
		}
	}

	private By byVisibleText(final String text) {
		return By.xpath("//*[normalize-space()='" + text + "']");
	}

	private By byContainsText(final String text) {
		return By.xpath("//*[contains(normalize-space(), '" + text + "')]");
	}

	private By byExactText(final String text) {
		return By.xpath("//*[normalize-space()='" + text + "']");
	}

	private WebElement waitUntilVisible(final By locator, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void waitUntilAnyVisible(final Duration timeout, final By... locators) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		shortWait.until(drv -> {
			for (final By locator : locators) {
				if (!drv.findElements(locator).isEmpty() && drv.findElement(locator).isDisplayed()) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isAnyVisible(final Duration timeout, final By... locators) {
		try {
			waitUntilAnyVisible(timeout, locators);
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private boolean isElementVisibleWithin(final WebElement root, final By locator) {
		try {
			final List<WebElement> candidates = root.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final NoSuchElementException e) {
			return false;
		}
	}

	private void waitUntilInvisible(final By locator, final Duration timeout) {
		new WebDriverWait(driver, timeout).until(ExpectedConditions.invisibilityOfElementLocated(locator));
	}

	private void waitForUiToLoad() {
		try {
			wait.until(drv -> "complete".equals(((JavascriptExecutor) drv).executeScript("return document.readyState")));
		} catch (final TimeoutException e) {
			// Continue execution when the page keeps long-polling while content is already visible.
		}

		try {
			Thread.sleep(400L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String normalizeWhitespace(final String value) {
		return value == null ? "" : value.replace('\u00A0', ' ').trim();
	}

	private String rootMessage(final Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.toString() : current.getMessage();
	}

	private String firstNonBlank(final String... candidates) {
		for (final String candidate : candidates) {
			if (candidate != null && !candidate.trim().isEmpty()) {
				return candidate.trim();
			}
		}
		return null;
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
