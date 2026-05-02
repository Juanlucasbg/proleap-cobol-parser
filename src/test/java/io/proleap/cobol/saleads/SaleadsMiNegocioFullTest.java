package io.proleap.cobol.saleads;

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
import org.openqa.selenium.WindowType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Full UI workflow validation for SaleADS "Mi Negocio" module.
 *
 * Runtime inputs:
 * - saleads.baseUrl (optional): when provided, test opens this URL and performs full login flow.
 * - saleads.headless (optional, default true): run Chrome headless if true.
 *
 * Notes:
 * - Selectors prioritize visible text and role-like patterns to be environment-portable.
 * - Evidence screenshots and report are written to target/saleads-evidence/.
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_WAIT_TIMEOUT = Duration.ofSeconds(8);
	private static final Duration UI_SETTLE_DELAY = Duration.ofMillis(800);
	private static final String TARGET_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter REPORT_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, Boolean> validationResults = new LinkedHashMap<>();
	private final List<String> notes = new ArrayList<>();

	@Before
	public void setUp() throws IOException {
		final String runTs = LocalDateTime.now().format(REPORT_TS);
		evidenceDir = Paths.get("target", "saleads-evidence", runTs);
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (Boolean.parseBoolean(System.getProperty("saleads.headless", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		Assume.assumeTrue("Skipping SaleADS UI test unless -Dsaleads.runUiTest=true",
				Boolean.parseBoolean(System.getProperty("saleads.runUiTest", "false")));

		recordValidation("Login", runLoginStep());
		recordValidation("Mi Negocio menu", runOpenMiNegocioMenuStep());
		recordValidation("Agregar Negocio modal", runAgregarNegocioModalStep());
		recordValidation("Administrar Negocios view", runAdministrarNegociosStep());
		recordValidation("Información General", runInformacionGeneralValidation());
		recordValidation("Detalles de la Cuenta", runDetallesCuentaValidation());
		recordValidation("Tus Negocios", runTusNegociosValidation());
		recordValidation("Términos y Condiciones", runTerminosYCondicionesValidation());
		recordValidation("Política de Privacidad", runPoliticaPrivacidadValidation());

		final List<String> failed = new ArrayList<>();
		for (Map.Entry<String, Boolean> entry : validationResults.entrySet()) {
			if (!entry.getValue()) {
				failed.add(entry.getKey());
			}
		}

		Assert.assertTrue(
				"Some workflow validations failed: " + failed + ". See evidence under: " + evidenceDir.toAbsolutePath(),
				failed.isEmpty());
	}

	private boolean runLoginStep() throws IOException {
		try {
			openInitialContext();
			waitForUiToLoad();

			final WebElement googleButton = findClickableByVisibleTextAny(
					"Sign in with Google",
					"Iniciar con Google",
					"Iniciar sesión con Google",
					"Continuar con Google",
					"Google");
			googleButton.click();
			waitForUiToLoad();

			selectGoogleAccountIfVisible();
			waitForMainAppLoaded();
			takeScreenshot("step1-dashboard-loaded");
			return true;
		} catch (Exception exception) {
			notes.add("Login step failed: " + exception.getMessage());
			takeScreenshotSilently("step1-login-failed");
			return false;
		}
	}

	private boolean runOpenMiNegocioMenuStep() throws IOException {
		try {
			final WebElement miNegocioOption = clickSidebarText("Mi Negocio", "Negocio");
			waitForUiToLoad();
			Assert.assertNotNull("Expected Mi Negocio/Negocio option in sidebar.", miNegocioOption);
			assertVisibleTextPresent("Agregar Negocio");
			assertVisibleTextPresent("Administrar Negocios");
			takeScreenshot("step2-mi-negocio-expanded");
			return true;
		} catch (Exception exception) {
			notes.add("Mi Negocio menu step failed: " + exception.getMessage());
			takeScreenshotSilently("step2-mi-negocio-failed");
			return false;
		}
	}

	private boolean runAgregarNegocioModalStep() throws IOException {
		try {
			findClickableByVisibleTextAny("Agregar Negocio").click();
			waitForUiToLoad();

			assertVisibleTextPresent("Crear Nuevo Negocio");
			findInputByLabelOrPlaceholder("Nombre del Negocio");
			assertVisibleTextPresent("Tienes 2 de 3 negocios");
			assertVisibleTextPresent("Cancelar");
			assertVisibleTextPresent("Crear Negocio");
			takeScreenshot("step3-agregar-negocio-modal");

			// Optional action sequence requested in task.
			final WebElement businessNameInput = findInputByLabelOrPlaceholder("Nombre del Negocio");
			businessNameInput.click();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatizacion");
			findClickableByVisibleTextAny("Cancelar").click();
			waitForUiToLoad();
			return true;
		} catch (Exception exception) {
			notes.add("Agregar Negocio modal step failed: " + exception.getMessage());
			takeScreenshotSilently("step3-agregar-negocio-modal-failed");
			return false;
		}
	}

	private boolean runAdministrarNegociosStep() throws IOException {
		try {
			ensureMiNegocioExpanded();
			findClickableByVisibleTextAny("Administrar Negocios").click();
			waitForUiToLoad();

			assertVisibleTextPresent("Informacion General", "Información General");
			assertVisibleTextPresent("Detalles de la Cuenta");
			assertVisibleTextPresent("Tus Negocios");
			assertVisibleTextPresent("Seccion Legal", "Sección Legal");
			takeScreenshot("step4-administrar-negocios");
			return true;
		} catch (Exception exception) {
			notes.add("Administrar Negocios step failed: " + exception.getMessage());
			takeScreenshotSilently("step4-administrar-negocios-failed");
			return false;
		}
	}

	private boolean runInformacionGeneralValidation() throws IOException {
		try {
			assertVisibleTextRegexPresent(".+@.+\\..+");
			assertVisibleTextPresent("BUSINESS PLAN");
			assertVisibleTextPresent("Cambiar Plan");
			assertAnyNonEmptyHeadingOrStrongTextVisible();
			return true;
		} catch (Exception exception) {
			notes.add("Informacion General validation failed: " + exception.getMessage());
			takeScreenshotSilently("step5-informacion-general-failed");
			return false;
		}
	}

	private boolean runDetallesCuentaValidation() throws IOException {
		try {
			assertVisibleTextPresent("Cuenta creada");
			assertVisibleTextPresent("Estado activo");
			assertVisibleTextPresent("Idioma seleccionado");
			return true;
		} catch (Exception exception) {
			notes.add("Detalles de la Cuenta validation failed: " + exception.getMessage());
			takeScreenshotSilently("step6-detalles-cuenta-failed");
			return false;
		}
	}

	private boolean runTusNegociosValidation() throws IOException {
		try {
			assertVisibleTextPresent("Tus Negocios");
			assertVisibleTextPresent("Agregar Negocio");
			assertVisibleTextPresent("Tienes 2 de 3 negocios");
			return true;
		} catch (Exception exception) {
			notes.add("Tus Negocios validation failed: " + exception.getMessage());
			takeScreenshotSilently("step7-tus-negocios-failed");
			return false;
		}
	}

	private boolean runTerminosYCondicionesValidation() throws IOException {
		try {
			final String url = openLegalLinkAndValidate(
					"step8-terminos-y-condiciones",
					"Terminos y Condiciones",
					"Términos y Condiciones");
			notes.add("Terminos y Condiciones final URL: " + url);
			return true;
		} catch (Exception exception) {
			notes.add("Terminos y Condiciones validation failed: " + exception.getMessage());
			takeScreenshotSilently("step8-terminos-y-condiciones-failed");
			return false;
		}
	}

	private boolean runPoliticaPrivacidadValidation() throws IOException {
		try {
			final String url = openLegalLinkAndValidate(
					"step9-politica-privacidad",
					"Politica de Privacidad",
					"Política de Privacidad");
			notes.add("Politica de Privacidad final URL: " + url);
			return true;
		} catch (Exception exception) {
			notes.add("Politica de Privacidad validation failed: " + exception.getMessage());
			takeScreenshotSilently("step9-politica-privacidad-failed");
			return false;
		}
	}

	private String openLegalLinkAndValidate(final String screenshotName, final String... linkTexts) throws IOException {
		ensureOnApplicationTab();
		final String originalTab = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();

		findClickableByVisibleTextAny(linkTexts).click();
		waitForUiToLoad();

		String activeHandle = originalTab;
		final Set<String> afterHandles = wait.until((ExpectedCondition<Set<String>>) webDriver ->
				webDriver != null && webDriver.getWindowHandles().size() >= beforeHandles.size()
						? webDriver.getWindowHandles()
						: null);

		for (String handle : afterHandles) {
			if (!beforeHandles.contains(handle)) {
				activeHandle = handle;
				break;
			}
		}

		if (!activeHandle.equals(originalTab)) {
			driver.switchTo().window(activeHandle);
			waitForUiToLoad();
		}

		assertVisibleTextPresent(linkTexts);
		assertAnyParagraphLikeContent();
		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (!activeHandle.equals(originalTab)) {
			driver.close();
			driver.switchTo().window(originalTab);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void openInitialContext() {
		final String baseUrl = System.getProperty("saleads.baseUrl", "").trim();
		if (!baseUrl.isEmpty()) {
			driver.get(baseUrl);
		}
	}

	private void waitForMainAppLoaded() {
		wait.until(webDriver -> webDriver != null && webDriver.getCurrentUrl() != null);
		wait.until(webDriver -> {
			if (webDriver == null) {
				return false;
			}
			final List<WebElement> sidebarCandidates = webDriver.findElements(By.xpath(
					"//*[self::nav or contains(@class,'sidebar') or contains(@class,'SideBar') or @role='navigation']"));
			return !sidebarCandidates.isEmpty();
		});
	}

	private WebElement clickSidebarText(final String... possibleTexts) {
		for (String text : possibleTexts) {
			final List<By> locators = List.of(
					By.xpath("//*[self::nav or @role='navigation']//*[normalize-space()='" + text + "']"),
					By.xpath("//*[contains(@class,'sidebar') or contains(@class,'SideBar')]//*[normalize-space()='" + text + "']"),
					By.xpath("//*[normalize-space()='" + text + "']"));
			for (By locator : locators) {
				try {
					final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
					scrollIntoView(element);
					element.click();
					return element;
				} catch (TimeoutException ignored) {
					// Try next locator/text.
				}
			}
		}
		return null;
	}

	private void ensureMiNegocioExpanded() {
		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			final WebElement menu = clickSidebarText("Mi Negocio", "Negocio");
			if (menu != null) {
				waitForUiToLoad();
			}
		}
	}

	private void selectGoogleAccountIfVisible() {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_WAIT_TIMEOUT);
			final WebElement accountOption = shortWait.until(ExpectedConditions.elementToBeClickable(
					By.xpath("//*[contains(normalize-space(),'" + TARGET_GOOGLE_ACCOUNT + "')]")));
			scrollIntoView(accountOption);
			accountOption.click();
		} catch (TimeoutException ignored) {
			notes.add("Google account selector not shown. Continuing with existing authenticated session if any.");
		}
	}

	private void assertVisibleTextPresent(final String... possibleTexts) {
		boolean found = false;
		Exception lastError = null;
		for (String text : possibleTexts) {
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
						"//*[contains(normalize-space(),'" + text + "')]")));
				found = true;
				break;
			} catch (Exception exception) {
				lastError = exception;
			}
		}
		if (!found) {
			throw new AssertionError("Expected visible text was not found: " + String.join(" | ", possibleTexts), lastError);
		}
	}

	private void assertVisibleTextRegexPresent(final String regex) {
		wait.until(webDriver -> {
			if (webDriver == null) {
				return false;
			}
			final List<WebElement> visibleElements = webDriver.findElements(By.xpath("//*"));
			for (WebElement element : visibleElements) {
				try {
					if (element.isDisplayed() && element.getText() != null && element.getText().matches("(?s).*" + regex + ".*")) {
						return true;
					}
				} catch (Exception ignored) {
					// Skip stale/inaccessible elements.
				}
			}
			return false;
		});
	}

	private void assertAnyNonEmptyHeadingOrStrongTextVisible() {
		wait.until(webDriver -> {
			if (webDriver == null) {
				return false;
			}
			final List<WebElement> elements = webDriver.findElements(By.xpath("//h1|//h2|//h3|//h4|//strong|//b"));
			for (WebElement element : elements) {
				if (element.isDisplayed() && element.getText() != null && !element.getText().trim().isEmpty()) {
					return true;
				}
			}
			return false;
		});
	}

	private void assertAnyParagraphLikeContent() {
		wait.until(webDriver -> {
			if (webDriver == null) {
				return false;
			}
			final List<WebElement> paragraphs = webDriver.findElements(By.xpath("//p|//li|//article//*[self::p or self::li]"));
			int visibleNonEmptyCount = 0;
			for (WebElement paragraph : paragraphs) {
				if (paragraph.isDisplayed()) {
					final String text = paragraph.getText();
					if (text != null && text.trim().length() > 20) {
						visibleNonEmptyCount++;
						if (visibleNonEmptyCount >= 2) {
							return true;
						}
					}
				}
			}
			return false;
		});
	}

	private WebElement findInputByLabelOrPlaceholder(final String labelText) {
		final List<By> locators = List.of(
				By.xpath("//label[contains(normalize-space(),'" + labelText + "')]/following::input[1]"),
				By.xpath("//input[@placeholder='" + labelText + "']"),
				By.xpath("//input[contains(@placeholder,'" + labelText + "')]"),
				By.xpath("//input[contains(@aria-label,'" + labelText + "')]"));
		Exception lastError = null;
		for (By locator : locators) {
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (Exception exception) {
				lastError = exception;
			}
		}
		throw new AssertionError("Input not found for label/placeholder: " + labelText, lastError);
	}

	private WebElement findClickableByVisibleTextAny(final String... possibleTexts) {
		Exception lastError = null;
		for (String text : possibleTexts) {
			final List<By> locators = List.of(
					By.xpath("//button[normalize-space()='" + text + "']"),
					By.xpath("//a[normalize-space()='" + text + "']"),
					By.xpath("//*[(@role='button' or self::button or self::a) and contains(normalize-space(),'" + text + "')]"),
					By.xpath("//*[contains(normalize-space(),'" + text + "')]"));
			for (By locator : locators) {
				try {
					final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
					scrollIntoView(element);
					// Validate clickability if possible, otherwise fallback to JS click.
					try {
						wait.until(ExpectedConditions.elementToBeClickable(element));
						return element;
					} catch (Exception ignored) {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
						return element;
					}
				} catch (Exception exception) {
					lastError = exception;
				}
			}
		}
		throw new AssertionError("Clickable element not found by visible text: " + String.join(" | ", possibleTexts), lastError);
	}

	private boolean isTextVisible(final String text) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_WAIT_TIMEOUT);
			shortWait.until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[contains(normalize-space(),'" + text + "')]")));
			return true;
		} catch (TimeoutException exception) {
			return false;
		}
	}

	private void scrollIntoView(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center',inline:'nearest'});", element);
		} catch (Exception ignored) {
			new Actions(driver).moveToElement(element).perform();
		}
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> webDriver != null && "complete".equals(
				((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		try {
			Thread.sleep(UI_SETTLE_DELAY.toMillis());
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void ensureOnApplicationTab() {
		if (driver.getWindowHandles().isEmpty()) {
			driver.switchTo().newWindow(WindowType.TAB);
		}
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final Path screenshotPath = evidenceDir.resolve(safeFileName(fileName) + ".png");
		try (FileOutputStream out = new FileOutputStream(screenshotPath.toFile())) {
			out.write(png);
		}
	}

	private void takeScreenshotSilently(final String fileName) {
		try {
			takeScreenshot(fileName);
		} catch (Exception ignored) {
			// Intentional no-op for failure path capture best effort.
		}
	}

	private String safeFileName(final String value) {
		return value.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-+", "")
				.replaceAll("-+$", "");
	}

	private void recordValidation(final String label, final boolean passed) {
		validationResults.put(label, passed);
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio Full Test Report").append('\n');
		report.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n').append('\n');
		report.append("Result by validation field:").append('\n');

		for (Map.Entry<String, Boolean> entry : validationResults.entrySet()) {
			report.append("- ")
					.append(entry.getKey())
					.append(": ")
					.append(entry.getValue() ? "PASS" : "FAIL")
					.append('\n');
		}

		if (!notes.isEmpty()) {
			report.append('\n').append("Execution notes:").append('\n');
			for (String note : notes) {
				report.append("- ").append(note).append('\n');
			}
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.write(reportPath, report.toString().getBytes(StandardCharsets.UTF_8));
	}
}
