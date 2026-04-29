package io.proleap.saleads;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SaleadsMiNegocioWorkflowTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final List<String> REPORT_ORDER = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad"
	);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;
	private Path reportPath;

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		initializeStepResults();

		artifactsDir = Path.of("target", "saleads-e2e-artifacts", LocalDateTime.now().format(TS_FORMAT));
		Files.createDirectories(artifactsDir);

		driver = createDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(getIntProperty("saleads.timeout.seconds", 30)));
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
		driver.manage().window().maximize();

		final String startUrl = System.getProperty("saleads.startUrl", "").trim();
		if (!startUrl.isEmpty()) {
			driver.get(startUrl);
			waitForUiToSettle();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad"));

		reportPath = writeFinalReport();
		final List<String> failedSteps = REPORT_ORDER.stream()
				.filter(step -> stepResults.get(step).status == StepStatus.FAIL)
				.collect(Collectors.toList());

		Assert.assertTrue(
				"SaleADS Mi Negocio workflow failed in step(s): " + failedSteps + ". Report: " + reportPath.toAbsolutePath(),
				failedSteps.isEmpty()
		);
	}

	private void stepLoginWithGoogle() throws Exception {
		if (!isTextVisible("Negocio", 5)) {
			final Set<String> beforeHandles = driver.getWindowHandles();
			clickFirstVisibleText(
					"Sign in with Google",
					"Iniciar sesión con Google",
					"Iniciar sesion con Google",
					"Continuar con Google",
					"Login with Google",
					"Google"
			);

			final String appHandle = driver.getWindowHandle();
			final String oauthHandle = waitForNewWindow(beforeHandles, 8);
			if (oauthHandle != null) {
				driver.switchTo().window(oauthHandle);
			}

			selectGoogleAccountIfPrompted();

			if (oauthHandle != null) {
				waitForWindowCloseOrUrlChange(oauthHandle, 20);
				driver.switchTo().window(appHandle);
			}
		}

		waitUntilAnyVisible(20, By.cssSelector("aside"), By.cssSelector("nav"), containsText("Negocio"));
		assertTextVisible("Negocio");
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickIfVisible("Negocio");
		clickByVisibleText("Mi Negocio");

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		ensureMiNegocioExpanded();
		clickByVisibleText("Agregar Negocio");

		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextContains("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		final WebElement nameInput = findFirstVisible(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@placeholder, 'Nombre')]"),
				By.xpath("//input[contains(@aria-label, 'Nombre')]"),
				By.xpath("//input[contains(@name, 'nombre') or contains(@id, 'nombre')]")
		);
		nameInput.click();
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatización");

		takeScreenshot("03-agregar-negocio-modal");
		clickByVisibleText("Cancelar");
		waitForUiToSettle();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioExpanded();
		clickByVisibleText("Administrar Negocios");

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextContains("Sección Legal");
		takeFullPageScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("Información General");
		assertTextContains(ACCOUNT_EMAIL);
		assertTextContains("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");

		final WebElement emailElement = waitVisible(containsText(ACCOUNT_EMAIL), 15);
		final String userBlock = emailElement.findElement(By.xpath("./ancestor::*[self::div or self::section][1]")).getText();
		final String cleaned = userBlock
				.replace(ACCOUNT_EMAIL, "")
				.replace("BUSINESS PLAN", "")
				.replace("Cambiar Plan", "")
				.trim();

		Assert.assertTrue(
				"User name was not visible in Información General.",
				Pattern.compile(".*[\\p{L}].*").matcher(cleaned).matches()
		);
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Detalles de la Cuenta");
		assertTextContains("Cuenta creada");
		assertTextContains("Estado activo");
		assertTextContains("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextContains("Tienes 2 de 3 negocios");

		final WebElement section = waitVisible(exactText("Tus Negocios"), 15)
				.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		final List<WebElement> listItems = section.findElements(By.xpath(".//li | .//article | .//tr | .//*[contains(@class, 'card')]"));
		Assert.assertFalse("Business list is not visible in Tus Negocios.", listItems.isEmpty());
	}

	private void stepValidateLegalLink(final String linkText) throws Exception {
		assertTextContains("Sección Legal");

		final String appHandle = driver.getWindowHandle();
		final String appUrl = driver.getCurrentUrl();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickByVisibleText(linkText);

		final String newHandle = waitForNewWindow(beforeHandles, 8);
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
		}

		waitForUiToSettle();
		assertTextContains(linkText);

		final String bodyText = waitVisible(By.tagName("body"), 20).getText().trim();
		Assert.assertTrue("Legal content text is not visible for " + linkText, bodyText.length() > 120);

		legalUrls.put(linkText, driver.getCurrentUrl());
		takeScreenshot("05-legal-" + sanitize(linkText));

		if (newHandle != null) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else if (!driver.getCurrentUrl().equals(appUrl)) {
			driver.navigate().back();
			waitForUiToSettle();
		}
	}

	private void ensureMiNegocioExpanded() {
		if (!isTextVisible("Administrar Negocios", 3)) {
			clickIfVisible("Negocio");
		}
		if (!isTextVisible("Administrar Negocios", 3)) {
			clickByVisibleText("Mi Negocio");
		}
		assertTextVisible("Administrar Negocios");
	}

	private void selectGoogleAccountIfPrompted() {
		if (isTextVisible(ACCOUNT_EMAIL, 8)) {
			clickByVisibleText(ACCOUNT_EMAIL);
			return;
		}

		final List<WebElement> emailInputs = driver.findElements(By.xpath("//input[@type='email']"));
		if (!emailInputs.isEmpty() && emailInputs.get(0).isDisplayed()) {
			final WebElement input = emailInputs.get(0);
			input.clear();
			input.sendKeys(ACCOUNT_EMAIL);
			input.sendKeys(Keys.ENTER);
			waitForUiToSettle();
		}
	}

	private void runStep(final String reportField, final CheckedRunnable runnable) {
		try {
			runnable.run();
			stepResults.put(reportField, new StepResult(StepStatus.PASS, "Validation passed."));
		} catch (final Throwable throwable) {
			stepResults.put(reportField, new StepResult(StepStatus.FAIL, throwable.getMessage()));
		}
	}

	private Path writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio workflow report");
		lines.add("Generated at: " + LocalDateTime.now());
		lines.add("Artifacts directory: " + artifactsDir.toAbsolutePath());
		lines.add("");
		lines.add("Step results:");

		for (final String step : REPORT_ORDER) {
			final StepResult result = stepResults.get(step);
			lines.add("- " + step + ": " + result.status + " | " + nullToEmpty(result.details));
		}

		lines.add("");
		lines.add("Legal URLs:");
		lines.add("- Términos y Condiciones: " + legalUrls.getOrDefault("Términos y Condiciones", "N/A"));
		lines.add("- Política de Privacidad: " + legalUrls.getOrDefault("Política de Privacidad", "N/A"));

		final Path output = artifactsDir.resolve("final-report.txt");
		Files.write(output, lines);
		return output;
	}

	private WebDriver createDriver() {
		final String browser = System.getProperty("saleads.browser", "chrome").trim().toLowerCase();
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));

		switch (browser) {
			case "firefox":
				final FirefoxOptions firefoxOptions = new FirefoxOptions();
				if (headless) {
					firefoxOptions.addArguments("--headless");
				}
				return new FirefoxDriver(firefoxOptions);
			case "edge":
				final EdgeOptions edgeOptions = new EdgeOptions();
				if (headless) {
					edgeOptions.addArguments("--headless=new");
				}
				return new EdgeDriver(edgeOptions);
			case "chrome":
			default:
				final ChromeOptions chromeOptions = new ChromeOptions();
				if (headless) {
					chromeOptions.addArguments("--headless=new");
				}
				chromeOptions.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
				return new ChromeDriver(chromeOptions);
		}
	}

	private void clickIfVisible(final String text) {
		if (isTextVisible(text, 4)) {
			clickByVisibleText(text);
		}
	}

	private void clickByVisibleText(final String text) {
		final WebElement target = waitForAnyVisible(15,
				By.xpath("//button[normalize-space()=" + asXpathLiteral(text) + "]"),
				By.xpath("//a[normalize-space()=" + asXpathLiteral(text) + "]"),
				By.xpath("//*[@role='button' and normalize-space()=" + asXpathLiteral(text) + "]"),
				By.xpath("//*[normalize-space()=" + asXpathLiteral(text) + "]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"),
				By.xpath("//*[normalize-space()=" + asXpathLiteral(text) + "]")
		);
		scrollIntoView(target);
		target.click();
		waitForUiToSettle();
	}

	private void clickFirstVisibleText(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text, 3)) {
				clickByVisibleText(text);
				return;
			}
		}
		throw new IllegalStateException("None of the expected clickable texts were visible: " + Arrays.toString(texts));
	}

	private WebElement findFirstVisible(final By... locators) {
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		throw new IllegalStateException("No visible element found for locators: " + Arrays.toString(locators));
	}

	private void assertTextVisible(final String text) {
		waitVisible(exactText(text), 15);
	}

	private void assertTextContains(final String text) {
		waitVisible(containsText(text), 15);
	}

	private boolean isTextVisible(final String text, final int timeoutSeconds) {
		try {
			waitVisible(exactText(text), timeoutSeconds);
			return true;
		} catch (final Throwable ignored) {
			try {
				waitVisible(containsText(text), timeoutSeconds);
				return true;
			} catch (final Throwable ignoredAgain) {
				return false;
			}
		}
	}

	private WebElement waitVisible(final By locator, final int timeoutSeconds) {
		final WebDriverWait localWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		return localWait.until(drv -> {
			final List<WebElement> elements = drv.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private WebElement waitForAnyVisible(final int timeoutSeconds, final By... locators) {
		final WebDriverWait localWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		return localWait.until(drv -> {
			for (final By locator : locators) {
				final List<WebElement> elements = drv.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private void waitUntilAnyVisible(final int timeoutSeconds, final By... locators) {
		waitForAnyVisible(timeoutSeconds, locators);
	}

	private String waitForNewWindow(final Set<String> beforeHandles, final int timeoutSeconds) {
		try {
			final WebDriverWait localWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
			return localWait.until(drv -> {
				final Set<String> afterHandles = drv.getWindowHandles();
				if (afterHandles.size() <= beforeHandles.size()) {
					return null;
				}
				for (final String handle : afterHandles) {
					if (!beforeHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final Throwable ignored) {
			return null;
		}
	}

	private void waitForWindowCloseOrUrlChange(final String windowHandle, final int timeoutSeconds) {
		final String currentUrl = driver.getCurrentUrl();
		final WebDriverWait localWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		localWait.until(drv -> !drv.getWindowHandles().contains(windowHandle) || !drv.getCurrentUrl().equals(currentUrl));
	}

	private void waitForUiToSettle() {
		final JavascriptExecutor js = (JavascriptExecutor) driver;
		new WebDriverWait(driver, Duration.ofSeconds(20)).until(drv ->
				"complete".equals(js.executeScript("return document.readyState"))
		);

		final List<By> busyIndicators = Arrays.asList(
				By.cssSelector("[aria-busy='true']"),
				By.cssSelector(".spinner"),
				By.cssSelector(".loading"),
				By.cssSelector(".loader")
		);

		for (final By locator : busyIndicators) {
			try {
				new WebDriverWait(driver, Duration.ofSeconds(4)).until(drv -> drv.findElements(locator).stream().noneMatch(WebElement::isDisplayed));
			} catch (final Throwable ignored) {
				// no-op: this indicator is optional and may not exist.
			}
		}
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final Path output = artifactsDir.resolve(checkpointName + ".png");
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.copy(new java.io.ByteArrayInputStream(screenshot), output, StandardCopyOption.REPLACE_EXISTING);
	}

	private void takeFullPageScreenshot(final String checkpointName) throws IOException {
		final JavascriptExecutor js = (JavascriptExecutor) driver;
		final Long originalWidth = ((Number) js.executeScript("return window.outerWidth;")).longValue();
		final Long originalHeight = ((Number) js.executeScript("return window.outerHeight;")).longValue();
		final Long pageWidth = ((Number) js.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);")).longValue();
		final Long pageHeight = ((Number) js.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);")).longValue();

		try {
			driver.manage().window().setSize(new org.openqa.selenium.Dimension(pageWidth.intValue(), Math.min(pageHeight.intValue(), 8000)));
			waitForUiToSettle();
			takeScreenshot(checkpointName);
		} finally {
			driver.manage().window().setSize(new org.openqa.selenium.Dimension(originalWidth.intValue(), originalHeight.intValue()));
			waitForUiToSettle();
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void initializeStepResults() {
		for (final String step : REPORT_ORDER) {
			stepResults.put(step, new StepResult(StepStatus.NOT_EXECUTED, "Step was not executed."));
		}
	}

	private By exactText(final String text) {
		return By.xpath("//*[normalize-space()=" + asXpathLiteral(text) + "]");
	}

	private By containsText(final String text) {
		return By.xpath("//*[contains(normalize-space(), " + asXpathLiteral(text) + ")]");
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(", \"'\", ");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	private int getIntProperty(final String key, final int defaultValue) {
		try {
			return Integer.parseInt(System.getProperty(key, String.valueOf(defaultValue)).trim());
		} catch (final Exception ignored) {
			return defaultValue;
		}
	}

	private String sanitize(final String input) {
		return input.toLowerCase()
				.replace("á", "a")
				.replace("é", "e")
				.replace("í", "i")
				.replace("ó", "o")
				.replace("ú", "u")
				.replace("ñ", "n")
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-|-$)", "");
	}

	private String nullToEmpty(final String value) {
		return value == null ? "" : value;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private enum StepStatus {
		PASS,
		FAIL,
		NOT_EXECUTED
	}

	private static class StepResult {
		private final StepStatus status;
		private final String details;

		private StepResult(final StepStatus status, final String details) {
			this.status = status;
			this.details = details;
		}
	}
}
