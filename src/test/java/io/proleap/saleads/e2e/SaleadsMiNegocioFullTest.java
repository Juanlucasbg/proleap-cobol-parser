package io.proleap.saleads.e2e;

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
import java.util.Set;
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
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final DateTimeFormatter EVIDENCE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(25);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (!"false".equalsIgnoreCase(env("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		evidenceDir = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(EVIDENCE_TS));
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String saleadsUrl = env("SALEADS_URL", "").trim();
		Assume.assumeTrue("SALEADS_URL must point to the login page of the current SaleADS environment.",
				!saleadsUrl.isEmpty());

		driver.get(saleadsUrl);
		waitForUiToLoad();

		final Map<String, Boolean> report = createInitialReport();

		runStep(report, "Login", () -> {
			final Set<String> windowsBeforeLoginClick = driver.getWindowHandles();
			clickFirstVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
			chooseGoogleAccountIfSelectorAppears(windowsBeforeLoginClick);
			assertVisibleText("Negocio");
			captureScreenshot("01-dashboard-loaded");
		});

		runStep(report, "Mi Negocio menu", () -> {
			assertVisibleText("Negocio");
			clickByExactText("Mi Negocio");
			assertVisibleText("Agregar Negocio");
			assertVisibleText("Administrar Negocios");
			captureScreenshot("02-mi-negocio-expanded-menu");
		});

		runStep(report, "Agregar Negocio modal", () -> {
			clickByExactText("Agregar Negocio");
			assertVisibleText("Crear Nuevo Negocio");
			assertByLocator(By.xpath("//input[contains(@placeholder, 'Nombre del Negocio') or @name='businessName']"));
			assertVisibleText("Tienes 2 de 3 negocios");
			assertVisibleText("Cancelar");
			assertVisibleText("Crear Negocio");
			typeInFirstMatchingInput("Nombre del Negocio", "Negocio Prueba Automatizacion");
			captureScreenshot("03-agregar-negocio-modal");
			clickByExactText("Cancelar");
		});

		runStep(report, "Administrar Negocios view", () -> {
			expandMiNegocioIfNeeded();
			clickByExactText("Administrar Negocios");
			assertVisibleText("Informacion General", "Información General");
			assertVisibleText("Detalles de la Cuenta");
			assertVisibleText("Tus Negocios");
			assertVisibleText("Seccion Legal", "Sección Legal");
			captureScreenshot("04-administrar-negocios-page");
		});

		runStep(report, "Información General", () -> {
			assertUserNameVisible();
			assertEmailVisible();
			assertVisibleText("BUSINESS PLAN");
			assertVisibleText("Cambiar Plan");
		});

		runStep(report, "Detalles de la Cuenta", () -> {
			assertVisibleText("Cuenta creada");
			assertVisibleText("Estado activo");
			assertVisibleText("Idioma seleccionado");
		});

		runStep(report, "Tus Negocios", () -> {
			assertVisibleText("Tus Negocios");
			assertVisibleText("Agregar Negocio");
			assertVisibleText("Tienes 2 de 3 negocios");
		});

		runStep(report, "Términos y Condiciones", () -> {
			final String finalUrl = validateLegalLink("Terminos y Condiciones", "Términos y Condiciones",
					"08-terminos-condiciones");
			legalUrls.put("Términos y Condiciones", finalUrl);
		});

		runStep(report, "Política de Privacidad", () -> {
			final String finalUrl = validateLegalLink("Politica de Privacidad", "Política de Privacidad",
					"09-politica-privacidad");
			legalUrls.put("Política de Privacidad", finalUrl);
		});

		printFinalReport(report);

		final List<String> failed = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		Assert.assertTrue("One or more validations failed: " + failed, failed.isEmpty());
	}

	private Map<String, Boolean> createInitialReport() {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
		return report;
	}

	private void runStep(final Map<String, Boolean> report, final String reportKey, final CheckedRunnable action) {
		try {
			action.run();
			report.put(reportKey, true);
		} catch (final Throwable throwable) {
			report.put(reportKey, false);
			stepErrors.put(reportKey, throwable.getMessage());
			captureScreenshot("failed-" + reportKey);
		}
	}

	private String validateLegalLink(final String linkFallbackWithoutAccent, final String headingWithAccent,
			final String screenshotName) {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = driver.getWindowHandles();

		clickFirstVisibleText(headingWithAccent, linkFallbackWithoutAccent);
		waitForUiToLoad();

		final String legalWindow = waitForNewWindow(windowsBefore).orElse(appWindow);
		if (!appWindow.equals(legalWindow)) {
			driver.switchTo().window(legalWindow);
			waitForUiToLoad();
		}

		assertVisibleText(headingWithAccent, linkFallbackWithoutAccent);
		assertLegalTextIsVisible();
		captureScreenshot(screenshotName);

		final String url = driver.getCurrentUrl();

		if (!appWindow.equals(legalWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return url;
	}

	private java.util.Optional<String> waitForNewWindow(final Set<String> windowsBefore) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(6))
					.until(driverValue -> driverValue.getWindowHandles().size() > windowsBefore.size());
			return driver.getWindowHandles().stream().filter(handle -> !windowsBefore.contains(handle)).findFirst();
		} catch (final TimeoutException timeoutException) {
			return java.util.Optional.empty();
		}
	}

	private void assertLegalTextIsVisible() {
		final WebElement body = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
		final String normalized = body.getText().replaceAll("\\s+", " ").trim();
		Assert.assertTrue("Expected legal content text to be visible.", normalized.length() > 200);
	}

	private void expandMiNegocioIfNeeded() {
		if (!isVisibleText("Administrar Negocios")) {
			clickByExactText("Mi Negocio");
		}
	}

	private void clickByExactText(final String text) {
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
				"(//*[self::a or self::button or self::span or self::div or @role='button'][normalize-space()=" + xpath(text)
						+ "])[1]")));
		element.click();
		waitForUiToLoad();
	}

	private void clickFirstVisibleText(final String... candidates) {
		final List<String> tried = new ArrayList<>();
		for (final String candidate : candidates) {
			tried.add(candidate);
			final List<WebElement> elements = driver.findElements(By.xpath(
					"(//*[self::a or self::button or self::span or self::div or @role='button'][contains(normalize-space(), "
							+ xpath(candidate) + ")])[1]"));
			if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
				wait.until(ExpectedConditions.elementToBeClickable(elements.get(0))).click();
				waitForUiToLoad();
				return;
			}
		}
		Assert.fail("Could not click any visible element for: " + tried);
	}

	private void chooseGoogleAccountIfSelectorAppears(final Set<String> windowsBeforeLoginClick) {
		final String expectedEmail = env("SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com");
		final String initialWindow = driver.getWindowHandle();
		waitForPotentialGoogleWindow(windowsBeforeLoginClick);

		final List<String> handlesToTry = new ArrayList<>(driver.getWindowHandles());
		for (final String handle : handlesToTry) {
			driver.switchTo().window(handle);
			waitForUiToLoad();
			try {
				final WebElement account = new WebDriverWait(driver, Duration.ofSeconds(4))
						.until(ExpectedConditions.visibilityOfElementLocated(
								By.xpath("//*[contains(normalize-space(), " + xpath(expectedEmail) + ")]")));
				wait.until(ExpectedConditions.elementToBeClickable(account)).click();
				waitForUiToLoad();
				break;
			} catch (final TimeoutException ignored) {
				// Continue trying other windows in case Google opens a separate one.
			}
		}

		trySwitchToWindowContainingText("Negocio").orElseGet(() -> {
			if (driver.getWindowHandles().contains(initialWindow)) {
				driver.switchTo().window(initialWindow);
			} else {
				driver.switchTo().window(new ArrayList<>(driver.getWindowHandles()).get(0));
			}
			return null;
		});
	}

	private void waitForPotentialGoogleWindow(final Set<String> windowsBeforeLoginClick) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(6))
					.until(driverValue -> driverValue.getWindowHandles().size() > windowsBeforeLoginClick.size());
		} catch (final TimeoutException ignored) {
			// Most environments keep login in the same tab.
		}
	}

	private java.util.Optional<String> trySwitchToWindowContainingText(final String expectedText) {
		final List<String> handles = new ArrayList<>(driver.getWindowHandles());
		for (final String handle : handles) {
			driver.switchTo().window(handle);
			waitForUiToLoad();
			if (isVisibleText(expectedText)) {
				return java.util.Optional.of(handle);
			}
		}
		return java.util.Optional.empty();
	}

	private void assertVisibleText(final String... acceptedTexts) {
		for (final String text : acceptedTexts) {
			if (isVisibleText(text)) {
				return;
			}
		}
		Assert.fail("Expected to find visible text. Tried: " + java.util.Arrays.toString(acceptedTexts));
	}

	private boolean isVisibleText(final String text) {
		try {
			final String expression = "(//*[contains(normalize-space(), " + xpath(text) + ")])[1]";
			new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(expression)));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void assertByLocator(final By by) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private void assertAnyVisible(final By... locators) {
		for (final By locator : locators) {
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return;
			} catch (final TimeoutException ignored) {
				// Continue trying the next locator.
			}
		}
		Assert.fail("Expected at least one locator to be visible.");
	}

	private void assertUserNameVisible() {
		assertAnyVisible(
				By.xpath("//*[contains(@class,'name') and string-length(normalize-space()) > 2 and not(contains(normalize-space(), '@'))]"),
				By.xpath("//*[contains(@class,'user') and string-length(normalize-space()) > 2 and not(contains(normalize-space(), '@'))]"),
				By.xpath("//*[self::h1 or self::h2 or self::h3][string-length(normalize-space()) > 2 and not(contains(normalize-space(), '@'))]"));
	}

	private void assertEmailVisible() {
		assertByLocator(By.xpath("//*[contains(normalize-space(), '@') and contains(normalize-space(), '.')]"));
	}

	private void typeInFirstMatchingInput(final String placeholderText, final String value) {
		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"(//input[contains(@placeholder, " + xpath(placeholderText) + ") or @name='businessName'])[1]")));
		input.click();
		input.clear();
		input.sendKeys(value);
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(driverValue -> "complete"
				.equals(((JavascriptExecutor) driverValue).executeScript("return document.readyState")));
	}

	private void captureScreenshot(final String checkpointName) {
		try {
			final Path destination = evidenceDir.resolve(safeFileName(checkpointName) + ".png");
			final Path screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(screenshot, destination, StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception ignored) {
			// Never block the workflow because evidence capture failed.
		}
	}

	private String safeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9\\-]+", "-").replaceAll("-{2,}", "-").replaceAll("(^-|-$)", "");
	}

	private String xpath(final String value) {
		return "'" + value + "'";
	}

	private String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value;
	}

	private void printFinalReport(final Map<String, Boolean> report) {
		System.out.println("\n===== SaleADS Mi Negocio Final Report =====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
			if (!entry.getValue() && stepErrors.containsKey(entry.getKey())) {
				System.out.println("  Reason: " + stepErrors.get(entry.getKey()));
			}
		}
		for (final Map.Entry<String, String> legalEntry : legalUrls.entrySet()) {
			System.out.println(legalEntry.getKey() + " URL: " + legalEntry.getValue());
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("===========================================\n");
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
