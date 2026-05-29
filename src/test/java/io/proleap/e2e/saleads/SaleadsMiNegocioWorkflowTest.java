package io.proleap.e2e.saleads;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

public class SaleadsMiNegocioWorkflowTest {

	private static final List<String> REPORT_FIELDS = List.of(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Informacion General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Terminos y Condiciones",
			"Politica de Privacidad");

	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final int DEFAULT_TIMEOUT_SECONDS = 30;

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private final List<String> screenshotFiles = new ArrayList<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = getConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		final String debuggerAddress = getConfig("saleads.debuggerAddress", "SALEADS_DEBUGGER_ADDRESS");

		Assume.assumeTrue(
				"Provide -Dsaleads.login.url or -Dsaleads.debuggerAddress (or equivalent env vars).",
				hasText(loginUrl) || hasText(debuggerAddress));

		evidenceDir = createEvidenceDirectory();
		driver = createDriver(debuggerAddress);
		wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));

		if (hasText(loginUrl)) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() {
		writeFinalReportSafely();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		runStep("Login", () -> {
			loginWithGoogle();
			assertMainInterfaceVisible();
			captureScreenshot("01-dashboard-loaded.png");
		});

		runStep("Mi Negocio menu", () -> {
			openMiNegocioMenu();
			assertVisibleText("Agregar Negocio");
			assertVisibleText("Administrar Negocios");
			captureScreenshot("02-mi-negocio-menu-expanded.png");
		});

		runStep("Agregar Negocio modal", () -> {
			clickByVisibleText("Agregar Negocio");
			assertVisibleText("Crear Nuevo Negocio");
			assertNombreDelNegocioInputPresent();
			assertVisibleText("Tienes 2 de 3 negocios");
			assertVisibleText("Cancelar");
			assertVisibleText("Crear Negocio");
			captureScreenshot("03-agregar-negocio-modal.png");

			typeIntoNombreDelNegocio("Negocio Prueba Automatizacion");
			clickByVisibleText("Cancelar");
		});

		runStep("Administrar Negocios view", () -> {
			ensureMiNegocioSubmenuExpanded();
			clickByVisibleText("Administrar Negocios");
			waitForUiToLoad();
			assertVisibleText("Informacion General", "Informaci\u00F3n General");
			assertVisibleText("Detalles de la Cuenta");
			assertVisibleText("Tus Negocios");
			assertVisibleText("Seccion Legal", "Secci\u00F3n Legal");
			captureFullPageScreenshot("04-administrar-negocios-view-full.png");
		});

		runStep("Informacion General", () -> {
			assertVisibleText("Informacion General", "Informaci\u00F3n General");
			assertAnyVisible(By.xpath("//*[contains(normalize-space(),'@')]"), "User email");
			assertAnyVisible(By.xpath("//*[contains(normalize-space(),'Nombre') or contains(normalize-space(),'Usuario')]"),
					"User name");
			assertVisibleText("BUSINESS PLAN");
			assertVisibleText("Cambiar Plan");
		});

		runStep("Detalles de la Cuenta", () -> {
			assertVisibleText("Detalles de la Cuenta");
			assertVisibleText("Cuenta creada");
			assertVisibleText("Estado activo");
			assertVisibleText("Idioma seleccionado");
		});

		runStep("Tus Negocios", () -> {
			assertVisibleText("Tus Negocios");
			assertAnyVisible(By.xpath("//ul[.//*[contains(normalize-space(),'Negocio')]] | //table | //div[contains(@class,'card')]"),
					"Business list");
			assertVisibleText("Agregar Negocio");
			assertVisibleText("Tienes 2 de 3 negocios");
		});

		runStep("Terminos y Condiciones", () -> {
			final String finalUrl = openLegalLinkAndReturn(
					"Terminos y Condiciones",
					"T\u00E9rminos y Condiciones",
					"05-terminos-y-condiciones.png");
			capturedUrls.put("Terminos y Condiciones", finalUrl);
		});

		runStep("Politica de Privacidad", () -> {
			final String finalUrl = openLegalLinkAndReturn(
					"Politica de Privacidad",
					"Pol\u00EDtica de Privacidad",
					"06-politica-de-privacidad.png");
			capturedUrls.put("Politica de Privacidad", finalUrl);
		});

		writeFinalReportSafely();
		final List<String> failedSteps = stepStatus.entrySet().stream()
				.filter(entry -> !"PASS".equals(entry.getValue()))
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());
		Assert.assertTrue("Workflow validations failed. Check report in " + evidenceDir + ". Failed steps: " + failedSteps,
				failedSteps.isEmpty());
	}

	private void runStep(final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			stepStatus.put(stepName, "PASS");
			stepDetails.put(stepName, "Validated successfully.");
		} catch (final Throwable throwable) {
			stepStatus.put(stepName, "FAIL");
			stepDetails.put(stepName, sanitize(throwable.getMessage()));
		}
	}

	private void loginWithGoogle() {
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickGoogleSignInButton();
		selectGoogleAccountIfPrompted(handlesBeforeClick);
		waitForUiToLoad();
	}

	private void clickGoogleSignInButton() {
		final By[] loginLocators = new By[] {
				By.xpath("//button[contains(normalize-space(.),'Google')]"),
				By.xpath("//a[contains(normalize-space(.),'Google')]"),
				By.xpath("//*[@role='button' and contains(normalize-space(.),'Google')]") };

		for (final By locator : loginLocators) {
			final List<WebElement> matches = driver.findElements(locator);
			if (!matches.isEmpty()) {
				wait.until(ExpectedConditions.elementToBeClickable(matches.get(0))).click();
				waitForUiToLoad();
				return;
			}
		}

		throw new AssertionError("Google sign-in button was not found.");
	}

	private void selectGoogleAccountIfPrompted(final Set<String> handlesBeforeClick) {
		String activeHandle = driver.getWindowHandle();
		final Set<String> currentHandles = driver.getWindowHandles();
		if (currentHandles.size() > handlesBeforeClick.size()) {
			for (final String handle : currentHandles) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					activeHandle = handle;
					break;
				}
			}
		}

		final By googleChooserMarker = By.xpath("//*[contains(.,'Choose an account') or contains(.,'Elige una cuenta')]");
		final By accountLocator = By.xpath("//*[contains(normalize-space(.),'" + GOOGLE_ACCOUNT + "')]");
		final boolean chooserVisible = isVisible(googleChooserMarker, 8);

		if (chooserVisible || driver.getCurrentUrl().contains("accounts.google.com")) {
			wait.until(ExpectedConditions.elementToBeClickable(accountLocator)).click();
			waitForUiToLoad();
		}

		if (!driver.getWindowHandles().contains(activeHandle)) {
			final String anyHandle = driver.getWindowHandles().iterator().next();
			driver.switchTo().window(anyHandle);
		}
	}

	private void assertMainInterfaceVisible() {
		assertAnyVisible(
				By.xpath("//aside | //nav[.//*[contains(normalize-space(.),'Negocio')]] | //div[contains(@class,'sidebar')]"),
				"Main sidebar navigation");
	}

	private void openMiNegocioMenu() {
		assertMainInterfaceVisible();
		clickByVisibleText("Negocio");
		waitForUiToLoad();
		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();
	}

	private void ensureMiNegocioSubmenuExpanded() {
		if (!isVisible(By.xpath("//*[normalize-space(.)='Administrar Negocios']"), 2)) {
			openMiNegocioMenu();
		}
	}

	private void assertNombreDelNegocioInputPresent() {
		final By byLabel = By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]");
		final By byInput = By.xpath(
				"//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder,'Nombre del Negocio')]");

		assertAnyVisible(byLabel, "Nombre del Negocio label");
		assertAnyVisible(byInput, "Nombre del Negocio input");
	}

	private void typeIntoNombreDelNegocio(final String value) {
		final By byInput = By.xpath(
				"//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder,'Nombre del Negocio')]");
		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(byInput));
		input.click();
		input.clear();
		input.sendKeys(value);
		waitForUiToLoad();
	}

	private String openLegalLinkAndReturn(final String linkText, final String headingText, final String screenshotName) {
		final String appHandle = driver.getWindowHandle();
		final String previousUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText, headingText);
		waitForUiToLoad();

		String openedHandle = null;
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
			openedHandle = shortWait.until(d -> {
				final Set<String> handlesAfter = d.getWindowHandles();
				if (handlesAfter.size() > handlesBefore.size()) {
					for (final String handle : handlesAfter) {
						if (!handlesBefore.contains(handle)) {
							return handle;
						}
					}
				}
				return null;
			});
		} catch (final TimeoutException ignored) {
			// No new tab. Navigation in same tab is also valid.
		}

		if (openedHandle != null) {
			driver.switchTo().window(openedHandle);
		} else if (driver.getCurrentUrl().equals(previousUrl)) {
			throw new AssertionError("Legal link did not open a new tab and did not change the current URL.");
		}

		waitForUiToLoad();
		assertVisibleText(headingText, linkText);
		assertLegalContentVisible();
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (openedHandle != null) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void assertLegalContentVisible() {
		final JavascriptExecutor js = (JavascriptExecutor) driver;
		final Object hasText = js.executeScript(
				"return Array.from(document.querySelectorAll('p,li,div')).some(e => (e.innerText || '').trim().length > 80);");
		Assert.assertTrue("Legal content text is not visible.", Boolean.TRUE.equals(hasText));
	}

	private void clickByVisibleText(final String... textOptions) {
		AssertionError lastError = null;
		for (final String text : textOptions) {
			final List<By> locators = List.of(
					By.xpath("//button[normalize-space(.)='" + text + "']"),
					By.xpath("//a[normalize-space(.)='" + text + "']"),
					By.xpath("//*[@role='button' and normalize-space(.)='" + text + "']"),
					By.xpath("//*[normalize-space(.)='" + text + "']/ancestor::*[self::button or self::a or @role='button'][1]"),
					By.xpath("//*[normalize-space(.)='" + text + "']"));

			for (final By locator : locators) {
				try {
					final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
					element.click();
					waitForUiToLoad();
					return;
				} catch (final Throwable throwable) {
					lastError = new AssertionError("Unable to click element with text: " + text);
				}
			}
		}
		throw lastError != null ? lastError : new AssertionError("No text option provided for click.");
	}

	private void assertVisibleText(final String... textOptions) {
		for (final String text : textOptions) {
			final By locator = By.xpath("//*[normalize-space(.)='" + text + "']");
			if (isVisible(locator, 5)) {
				return;
			}
		}
		throw new AssertionError("None of the expected texts are visible: " + List.of(textOptions));
	}

	private void assertAnyVisible(final By locator, final String label) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final Throwable throwable) {
			throw new AssertionError(label + " is not visible.");
		}
	}

	private boolean isVisible(final By locator, final int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final Throwable ignored) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		wait.until(d -> {
			final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(readyState);
		});
		try {
			Thread.sleep(700L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private WebDriver createDriver(final String debuggerAddress) {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		if (!Boolean.parseBoolean(getConfig("saleads.headed", "SALEADS_HEADED"))) {
			options.addArguments("--headless=new");
		}

		if (hasText(debuggerAddress)) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		}

		return new ChromeDriver(options);
	}

	private void captureScreenshot(final String filename) {
		try {
			final byte[] data = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			final Path output = evidenceDir.resolve(filename);
			Files.write(output, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			screenshotFiles.add(output.toString());
		} catch (final IOException ioException) {
			throw new AssertionError("Failed to capture screenshot " + filename + ": " + ioException.getMessage());
		}
	}

	private void captureFullPageScreenshot(final String filename) {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			final Number totalHeight = (Number) js.executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, window.innerHeight);");
			final Number totalWidth = (Number) js.executeScript(
					"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, window.innerWidth);");
			driver.manage().window().setSize(new Dimension(Math.min(totalWidth.intValue(), 1920), totalHeight.intValue()));
			waitForUiToLoad();
			captureScreenshot(filename);
		} finally {
			driver.manage().window().setSize(originalSize);
		}
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private String getConfig(final String propertyName, final String envName) {
		final String propertyValue = System.getProperty(propertyName);
		if (hasText(propertyValue)) {
			return propertyValue;
		}
		return System.getenv(envName);
	}

	private boolean hasText(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	private String sanitize(final String value) {
		if (value == null) {
			return "No additional details.";
		}
		return value.replace('\n', ' ').replace('\r', ' ').trim();
	}

	private void writeFinalReportSafely() {
		if (evidenceDir == null) {
			return;
		}
		try {
			writeFinalReport();
		} catch (final IOException ignored) {
			// Do not mask test outcome if report writing fails.
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("# SaleADS Mi Negocio Workflow Report\n\n");
		report.append("Evidence directory: ").append(evidenceDir).append("\n\n");
		report.append("| Validation | Status | Details |\n");
		report.append("| --- | --- | --- |\n");

		for (final String field : REPORT_FIELDS) {
			final String status = stepStatus.getOrDefault(field, "FAIL");
			final String details = stepDetails.getOrDefault(field, "Step did not run.");
			report.append("| ")
					.append(field)
					.append(" | ")
					.append(status)
					.append(" | ")
					.append(escapePipes(details))
					.append(" |\n");
		}

		report.append("\n## Captured URLs\n\n");
		if (capturedUrls.isEmpty()) {
			report.append("- No external legal URLs captured.\n");
		} else {
			capturedUrls.forEach((name, url) -> report.append("- ").append(name).append(": ").append(url).append('\n'));
		}

		report.append("\n## Screenshots\n\n");
		if (screenshotFiles.isEmpty()) {
			report.append("- No screenshots captured.\n");
		} else {
			screenshotFiles.forEach(path -> report.append("- ").append(path).append('\n'));
		}

		Files.writeString(
				evidenceDir.resolve("final-report.md"),
				report.toString(),
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
	}

	private String escapePipes(final String input) {
		return input.replace("|", "\\|");
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
