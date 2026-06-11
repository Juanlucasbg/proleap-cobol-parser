package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String RUN_FLAG = "saleads.e2e.enabled";
	private static final String LOGIN_URL_PROPERTY = "saleads.loginUrl";
	private static final String HEADLESS_PROPERTY = "saleads.headless";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDir;
	private String loginUrl;

	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> finalReport = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getProperty(RUN_FLAG, "false"));
		Assume.assumeTrue("Skipping SaleADS E2E test. Enable with -D" + RUN_FLAG + "=true", enabled);

		loginUrl = firstNonBlank(System.getProperty(LOGIN_URL_PROPERTY), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"Set -D" + LOGIN_URL_PROPERTY + "=<env login URL> or SALEADS_LOGIN_URL environment variable.",
				loginUrl != null);

		final boolean headless = Boolean
				.parseBoolean(firstNonBlank(System.getProperty(HEADLESS_PROPERTY), System.getenv("SALEADS_HEADLESS"), "true"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));

		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		screenshotsDir = Path.of("target", "saleads-mi-negocio-e2e", timestamp).toAbsolutePath();
		Files.createDirectories(screenshotsDir);

		initFinalReport();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		driver.get(loginUrl);
		waitForUiLoad();

		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		printFinalReport();

		Assert.assertTrue("SaleADS Mi Negocio workflow validation failed:\n"
				+ String.join("\n", failures), failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		clickFirstMatchingText(List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"));
		waitForUiLoad();

		clickTextIfVisible("juanlucasbarbiergarzon@gmail.com");
		waitForUiLoad();

		waitForAnyVisibleText(List.of("Negocio", "Mi Negocio"), 45);

		final boolean sidebarVisible = hasDisplayedElement(By.xpath(
				"//aside | //nav | //*[@role='navigation']"));
		Assert.assertTrue("Left sidebar navigation is not visible after login.", sidebarVisible);

		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickTextIfVisible("Negocio");
		clickFirstMatchingText(List.of("Mi Negocio", "Mi negocio"));

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickFirstMatchingText(List.of("Agregar Negocio", "Agregar negocio"));
		waitForVisibleText("Crear Nuevo Negocio");

		final WebElement modal = wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(@role,'dialog') or contains(@class,'modal')][.//*[normalize-space(.)='Crear Nuevo Negocio']]")));

		Assert.assertTrue("Input field 'Nombre del Negocio' was not found in modal.",
				!modal.findElements(By.xpath(".//input")).isEmpty()
						&& (hasVisibleTextIn(modal, "Nombre del Negocio")
								|| hasDisplayedElementIn(modal, By.xpath(".//input[contains(@placeholder,'Negocio') or contains(@aria-label,'Negocio')]"))));

		Assert.assertTrue("Expected text 'Tienes 2 de 3 negocios' is missing.",
				hasVisibleTextIn(modal, "Tienes 2 de 3 negocios"));
		Assert.assertTrue("Button 'Cancelar' is missing.", hasDisplayedElementIn(modal, relativeTextLocator("Cancelar")));
		Assert.assertTrue("Button 'Crear Negocio' is missing.",
				hasDisplayedElementIn(modal, relativeTextLocator("Crear Negocio")));

		captureScreenshot("03-agregar-negocio-modal");

		clickTextIfVisible("Nombre del Negocio");
		typeIfVisible(By.xpath("//input[contains(@placeholder,'Negocio') or contains(@aria-label,'Negocio')]"),
				"Negocio Prueba Automatización");
		clickFirstMatchingText(List.of("Cancelar"));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		clickTextIfVisible("Mi Negocio");
		clickFirstMatchingText(List.of("Administrar Negocios"));
		waitForUiLoad();

		waitForVisibleText("Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal");

		captureScreenshot("04-administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() {
		waitForVisibleText("BUSINESS PLAN");
		waitForVisibleText("Cambiar Plan");

		final WebElement section = findSectionContaining("Información General");
		final String sectionText = section.getText();

		Assert.assertTrue("User email is not visible in 'Información General'.",
				EMAIL_PATTERN.matcher(normalizeWhitespace(sectionText)).find());

		final List<String> lines = sectionText.lines().map(String::trim).filter(line -> !line.isBlank()).collect(Collectors.toList());
		Assert.assertTrue("User name is not clearly visible in 'Información General'.", lines.size() >= 3);
	}

	private void stepValidateDetallesCuenta() {
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionContaining("Tus Negocios");
		Assert.assertTrue("Business list is not visible in 'Tus Negocios'.",
				hasDisplayedElementIn(section, By.xpath(".//ul | .//table | .//div[contains(@class,'list') or contains(@class,'card')]")));
		Assert.assertTrue("Button 'Agregar Negocio' is missing in 'Tus Negocios'.",
				hasDisplayedElementIn(section, relativeTextLocator("Agregar Negocio")));
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' is missing in 'Tus Negocios'.",
				hasVisibleTextIn(section, "Tienes 2 de 3 negocios"));
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		final String finalUrl = openAndValidateLegalLink("Términos y Condiciones", "Términos y Condiciones",
				"05-terminos-y-condiciones");
		System.out.println("Final URL (Términos y Condiciones): " + finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		final String finalUrl = openAndValidateLegalLink("Política de Privacidad", "Política de Privacidad",
				"06-politica-de-privacidad");
		System.out.println("Final URL (Política de Privacidad): " + finalUrl);
	}

	private String openAndValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String urlBeforeClick = driver.getCurrentUrl();

		clickFirstMatchingText(List.of(linkText));

		wait.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size()
				|| !d.getCurrentUrl().equals(urlBeforeClick));

		final Set<String> handlesAfterClick = driver.getWindowHandles();
		if (handlesAfterClick.size() > handlesBeforeClick.size()) {
			switchToNewestHandle(handlesBeforeClick, handlesAfterClick);
		}

		waitForUiLoad();
		waitForVisibleText(expectedHeading);

		final String bodyText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		Assert.assertTrue("Legal content text is not visible for '" + expectedHeading + "'.",
				bodyText.length() > 120);

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (driver.getWindowHandles().size() > 1 && !driver.getWindowHandle().equals(appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiLoad();
		} else if (!driver.getCurrentUrl().equals(urlBeforeClick)) {
			driver.navigate().back();
			waitForUiLoad();
		}

		return finalUrl;
	}

	private void runStep(final String reportKey, final CheckedRunnable step) {
		try {
			step.run();
			finalReport.put(reportKey, "PASS");
		} catch (Throwable throwable) {
			finalReport.put(reportKey, "FAIL");
			failures.add(reportKey + ": " + throwable.getMessage());
		}
	}

	private void initFinalReport() {
		finalReport.put("Login", "FAIL");
		finalReport.put("Mi Negocio menu", "FAIL");
		finalReport.put("Agregar Negocio modal", "FAIL");
		finalReport.put("Administrar Negocios view", "FAIL");
		finalReport.put("Información General", "FAIL");
		finalReport.put("Detalles de la Cuenta", "FAIL");
		finalReport.put("Tus Negocios", "FAIL");
		finalReport.put("Términos y Condiciones", "FAIL");
		finalReport.put("Política de Privacidad", "FAIL");
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Full Test Report ===");
		finalReport.forEach((field, status) -> System.out.println(field + ": " + status));
		System.out.println("Screenshots directory: " + screenshotsDir);
	}

	private void clickFirstMatchingText(final List<String> texts) {
		Throwable lastError = null;
		for (String text : texts) {
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(textLocator(text)));
				safeClick(element);
				waitForUiLoad();
				return;
			} catch (Throwable throwable) {
				lastError = throwable;
			}
		}

		throw new IllegalStateException("Unable to click any of the expected texts: " + texts, lastError);
	}

	private void clickTextIfVisible(final String text) {
		final List<WebElement> elements = driver.findElements(textLocator(text));
		for (WebElement element : elements) {
			if (element.isDisplayed()) {
				safeClick(element);
				waitForUiLoad();
				return;
			}
		}
	}

	private void typeIfVisible(final By locator, final String value) {
		final List<WebElement> inputs = driver.findElements(locator);
		for (WebElement input : inputs) {
			if (input.isDisplayed()) {
				input.clear();
				input.sendKeys(value);
				return;
			}
		}
	}

	private void safeClick(final WebElement element) {
		try {
			element.click();
		} catch (Exception e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void waitForVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(textLocator(text)));
	}

	private void waitForAnyVisibleText(final List<String> texts, final int timeoutSeconds) {
		final WebDriverWait localWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		localWait.until(d -> texts.stream().anyMatch(this::hasVisibleText));
	}

	private boolean hasVisibleText(final String text) {
		return driver.findElements(textLocator(text)).stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean hasVisibleTextIn(final WebElement parent, final String text) {
		return parent.findElements(relativeTextLocator(text)).stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean hasDisplayedElement(final By locator) {
		return driver.findElements(locator).stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean hasDisplayedElementIn(final WebElement parent, final By locator) {
		return parent.findElements(locator).stream().anyMatch(WebElement::isDisplayed);
	}

	private WebElement findSectionContaining(final String headingText) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//*[self::section or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span][contains(normalize-space(.),"
						+ asXpathLiteral(headingText) + ")]]")));
	}

	private void waitForUiLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		waitForLoadingIndicatorsToDisappear();
	}

	private void waitForLoadingIndicatorsToDisappear() {
		final List<By> indicators = List.of(
				By.cssSelector("[aria-busy='true']"),
				By.cssSelector(".spinner, .loading, .loader"),
				By.xpath("//*[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'cargando')]"));

		for (By indicator : indicators) {
			try {
				new WebDriverWait(driver, Duration.ofSeconds(5))
						.until(ExpectedConditions.numberOfElementsToBe(indicator, 0));
			} catch (Exception ignored) {
				// Some pages keep skeleton/spinner nodes mounted; this should not fail the step.
			}
		}
	}

	private void switchToNewestHandle(final Set<String> oldHandles, final Set<String> newHandles) {
		for (String handle : newHandles) {
			if (!oldHandles.contains(handle)) {
				driver.switchTo().window(handle);
				return;
			}
		}
	}

	private void captureScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = screenshotsDir.resolve(toSafeFilename(name) + ".png");
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private By textLocator(final String text) {
		final String literal = asXpathLiteral(text);
		return By.xpath(
				"//*[normalize-space(.)=" + literal + "]"
						+ " | //*[(self::button or self::a or @role='button') and contains(normalize-space(.), " + literal + ")]"
						+ " | //*[self::span or self::div][normalize-space(text())=" + literal + "]");
	}

	private By relativeTextLocator(final String text) {
		final String literal = asXpathLiteral(text);
		return By.xpath(
				".//*[normalize-space(.)=" + literal + "]"
						+ " | .//*[(self::button or self::a or @role='button') and contains(normalize-space(.), " + literal + ")]");
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char ch = chars[i];
			if (ch == '\'') {
				builder.append("\"'\"");
			} else if (ch == '"') {
				builder.append("'\"'");
			} else {
				builder.append("'").append(ch).append("'");
			}
			if (i < chars.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String toSafeFilename(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
	}

	private String firstNonBlank(final String... values) {
		for (String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private String normalizeWhitespace(final String value) {
		return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
