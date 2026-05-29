package io.proleap.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Full E2E workflow for SaleADS "Mi Negocio" module.
 *
 * Runtime configuration:
 * - saleads.url or SALEADS_URL (required): Environment login URL (dev/staging/prod).
 * - saleads.headless (optional, default true): Run Chrome in headless mode.
 * - saleads.timeout.seconds (optional, default 30): Explicit wait timeout.
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;

	@Before
	public void setUp() throws IOException {
		final String configuredUrl = getConfig("saleads.url", "SALEADS_URL");
		Assume.assumeTrue(
				"Missing SaleADS environment URL. Set -Dsaleads.url=<login-url> or SALEADS_URL to run this test.",
				configuredUrl != null && !configuredUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(getConfig("saleads.headless", "SALEADS_HEADLESS", "true"));
		final int timeoutSeconds = Integer.parseInt(getConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", "30"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--window-size=1920,1080");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		screenshotDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(screenshotDir);

		driver.get(configuredUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		try {
			printFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(REPORT_DETALLES, this::stepValidateDetallesCuenta);
		runStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(REPORT_TERMINOS, () -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "terminos"));
		runStep(REPORT_PRIVACIDAD, () -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "privacidad"));

		Assert.assertTrue("Workflow failures: " + String.join(" | ", failures), failures.isEmpty());
	}

	private void stepLoginWithGoogle() {
		clickFirstVisibleText(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Continuar con Google",
				"Acceder con Google",
				"Google");
		waitForUiToLoad();

		selectGoogleAccountIfPrompted();

		assertLeftSidebarVisible();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		clickIfVisible("Negocio");
		clickFirstVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickFirstVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertTextVisible("Crear Nuevo Negocio");
		assertInputByLabelExists("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		captureScreenshot("03-crear-negocio-modal");

		typeIfVisible("Nombre del Negocio", "Negocio Prueba Automatización");
		clickFirstVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() {
		if (!isTextVisible("Administrar Negocios", 3)) {
			clickIfVisible("Negocio");
			clickIfVisible("Mi Negocio");
		}
		clickFirstVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		captureScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("Información General");
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
		assertEmailVisible();
		assertLikelyUserNameVisible();
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertBusinessListVisible();
	}

	private void stepValidateLegalLink(final String linkText, final String headingText, final String screenshotPrefix) {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickFirstVisibleText(linkText);
		waitForUiToLoad();

		final String targetHandle = waitForNewTab(beforeHandles).orElse(originalHandle);
		driver.switchTo().window(targetHandle);
		waitForUiToLoad();

		assertTextVisible(headingText);
		assertLegalContentVisible();
		captureScreenshot("05-" + screenshotPrefix + "-page");
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (!targetHandle.equals(originalHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void runStep(final String name, final Runnable step) {
		try {
			step.run();
			report.put(name, true);
		} catch (Exception ex) {
			report.put(name, false);
			failures.add(name + " -> " + ex.getMessage());
			captureScreenshot("failed-" + slugify(name));
		}
	}

	private void clickFirstVisibleText(final String... texts) {
		WebElement element = null;
		for (final String text : texts) {
			element = findVisibleElementByText(text).orElse(null);
			if (element != null) {
				break;
			}
		}
		if (element == null) {
			throw new IllegalStateException("Could not find clickable element for texts: " + String.join(", ", texts));
		}
		scrollIntoView(element);
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiToLoad();
	}

	private Optional<WebElement> findVisibleElementByText(final String text) {
		final String escapedText = escapeXpath(text);
		final String xpath = "//*[self::button or self::a or @role='button' or self::div or self::span]"
				+ "[contains(normalize-space(.), " + escapedText + ")]";
		try {
			final List<WebElement> candidates = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.xpath(xpath)));
			return candidates.stream().filter(WebElement::isDisplayed).findFirst();
		} catch (TimeoutException ex) {
			return Optional.empty();
		}
	}

	private void clickIfVisible(final String text) {
		findVisibleElementByText(text).ifPresent(element -> {
			scrollIntoView(element);
			element.click();
			waitForUiToLoad();
		});
	}

	private void assertTextVisible(final String text) {
		final String escapedText = escapeXpath(text);
		final String xpath = "//*[contains(normalize-space(.), " + escapedText + ")]";
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
	}

	private boolean isTextVisible(final String text, final int timeoutSeconds) {
		final String escapedText = escapeXpath(text);
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		try {
			shortWait.until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[contains(normalize-space(.), " + escapedText + ")]")));
			return true;
		} catch (TimeoutException ex) {
			return false;
		}
	}

	private void assertInputByLabelExists(final String labelText) {
		final String escapedLabel = escapeXpath(labelText);
		final String inputByLabel = "//label[contains(normalize-space(.), " + escapedLabel + ")]"
				+ "/following::input[1]";
		final String inputByPlaceholder = "//input[contains(@placeholder, " + escapedLabel + ")]";
		if (isElementVisible(By.xpath(inputByLabel), 2) || isElementVisible(By.xpath(inputByPlaceholder), 2)) {
			return;
		}
		throw new IllegalStateException("Input field not found for label: " + labelText);
	}

	private void typeIfVisible(final String labelText, final String value) {
		final String escapedLabel = escapeXpath(labelText);
		final String inputByLabel = "//label[contains(normalize-space(.), " + escapedLabel + ")]/following::input[1]";
		final String inputByPlaceholder = "//input[contains(@placeholder, " + escapedLabel + ")]";
		By selector = By.xpath(inputByLabel);
		if (!isElementVisible(selector, 2)) {
			selector = By.xpath(inputByPlaceholder);
		}
		if (!isElementVisible(selector, 2)) {
			return;
		}
		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
		input.clear();
		input.sendKeys(value);
	}

	private void assertLeftSidebarVisible() {
		final String sidebarXpath = "//aside | //nav[contains(@class,'sidebar')] | //*[@aria-label='sidebar']";
		if (isElementVisible(By.xpath(sidebarXpath), 5)) {
			return;
		}
		assertTextVisible("Negocio");
	}

	private void selectGoogleAccountIfPrompted() {
		final Set<String> handles = driver.getWindowHandles();
		for (final String handle : handles) {
			driver.switchTo().window(handle);
			final Optional<WebElement> emailOption = findVisibleElementByText(ACCOUNT_EMAIL);
			if (emailOption.isPresent()) {
				scrollIntoView(emailOption.get());
				emailOption.get().click();
				waitForUiToLoad();
				return;
			}
		}
	}

	private Optional<String> waitForNewTab(final Set<String> previousHandles) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));
		try {
			shortWait.until((ExpectedCondition<Boolean>) d -> d != null && d.getWindowHandles().size() > previousHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!previousHandles.contains(handle)) {
					return Optional.of(handle);
				}
			}
		} catch (TimeoutException ignored) {
			// Link might open in same tab.
		}
		return Optional.empty();
	}

	private void assertEmailVisible() {
		final String pageText = driver.findElement(By.tagName("body")).getText();
		final Matcher matcher = EMAIL_PATTERN.matcher(pageText);
		if (!matcher.find()) {
			throw new IllegalStateException("User email not visible in the page content.");
		}
	}

	private void assertLikelyUserNameVisible() {
		final String pageText = driver.findElement(By.tagName("body")).getText();
		final String[] lines = pageText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.length() < 3 || line.length() > 80) {
				continue;
			}
			final String normalized = normalize(line);
			if (normalized.contains("@")
					|| normalized.contains("informacion general")
					|| normalized.contains("business plan")
					|| normalized.contains("cambiar plan")
					|| normalized.contains("detalles de la cuenta")
					|| normalized.contains("tus negocios")
					|| normalized.contains("seccion legal")
					|| normalized.contains("agregar negocio")
					|| normalized.contains("terminos")
					|| normalized.contains("politica")
					|| normalized.contains("cuenta creada")
					|| normalized.contains("estado activo")
					|| normalized.contains("idioma seleccionado")) {
				continue;
			}
			if (line.matches("[\\p{L}][\\p{L} .'-]{2,}")) {
				return;
			}
		}
		throw new IllegalStateException("Could not validate visible user name text.");
	}

	private void assertBusinessListVisible() {
		final String sectionXpath = "//*[contains(normalize-space(.), 'Tus Negocios')]";
		final WebElement section = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(sectionXpath)));
		final List<WebElement> items = section.findElements(
				By.xpath(".//*[self::li or self::tr or contains(@class,'item') or contains(@class,'card')]"));
		if (!items.isEmpty()) {
			return;
		}
		if (section.getText().split("\\R").length >= 3) {
			return;
		}
		throw new IllegalStateException("Business list is not visible in 'Tus Negocios' section.");
	}

	private void assertLegalContentVisible() {
		final String bodyText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
		if (bodyText.length() < 120) {
			throw new IllegalStateException("Legal page content seems too short to be valid.");
		}
	}

	private boolean isElementVisible(final By by, final int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(by));
			return true;
		} catch (TimeoutException ex) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		wait.until(driverRef -> {
			if (driverRef == null) {
				return false;
			}
			final Object readyState = ((JavascriptExecutor) driverRef).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(readyState)) || "interactive".equals(String.valueOf(readyState));
		});
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void captureScreenshot(final String name) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final Path outputPath = screenshotDir.resolve(slugify(name) + ".png");
		try {
			final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(source, outputPath, StandardCopyOption.REPLACE_EXISTING);
			System.out.println("Screenshot: " + outputPath.toAbsolutePath());
		} catch (Exception ex) {
			System.out.println("Could not write screenshot " + outputPath + ": " + ex.getMessage());
		}
	}

	private void printFinalReport() {
		if (report.isEmpty()) {
			return;
		}
		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		report.forEach((step, passed) -> System.out.println(step + ": " + (passed ? "PASS" : "FAIL")));
		legalUrls.forEach((label, url) -> System.out.println(label + " URL: " + url));
		if (!failures.isEmpty()) {
			System.out.println("Failures:");
			failures.forEach(f -> System.out.println("- " + f));
		}
	}

	private static String getConfig(final String propertyKey, final String envKey) {
		final String fromProperty = System.getProperty(propertyKey);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}
		final String fromEnv = System.getenv(envKey);
		return fromEnv != null && !fromEnv.isBlank() ? fromEnv : null;
	}

	private static String getConfig(final String propertyKey, final String envKey, final String defaultValue) {
		return Optional.ofNullable(getConfig(propertyKey, envKey)).orElse(defaultValue);
	}

	private static String normalize(final String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT);
	}

	private static String slugify(final String value) {
		return normalize(value).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
	}

	private static String escapeXpath(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder concat = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				concat.append(", \"'\", ");
			}
			concat.append("'").append(parts[i]).append("'");
		}
		concat.append(")");
		return concat.toString();
	}
}
