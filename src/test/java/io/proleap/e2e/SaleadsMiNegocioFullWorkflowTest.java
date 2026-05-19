package io.proleap.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Full UI workflow validation for the SaleADS "Mi Negocio" module.
 *
 * <p>This test is environment-agnostic and does not hardcode any domain.
 * Provide the login page URL at runtime using SALEADS_LOGIN_URL.</p>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS_CONDICIONES = "T\u00e9rminos y Condiciones";
	private static final String POLITICA_PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private static final List<String> REPORT_FIELDS = Arrays.asList(
			LOGIN,
			MI_NEGOCIO_MENU,
			AGREGAR_NEGOCIO_MODAL,
			ADMINISTRAR_NEGOCIOS_VIEW,
			INFORMACION_GENERAL,
			DETALLES_CUENTA,
			TUS_NEGOCIOS,
			TERMINOS_CONDICIONES,
			POLITICA_PRIVACIDAD);

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Path finalReportPath;
	private String appWindowHandle;
	private String termsFinalUrl = "N/A";
	private String privacyFinalUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		for (final String reportField : REPORT_FIELDS) {
			stepStatus.put(reportField, "NOT_RUN");
			stepDetails.put(reportField, "");
		}

		evidenceDir = buildEvidenceDirectory();
		finalReportPath = evidenceDir.resolve("final_report.txt");

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = !"false".equalsIgnoreCase(env("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (loginUrl == null || loginUrl.trim().isEmpty()) {
			Assert.fail("SALEADS_LOGIN_URL is required. The test is environment-agnostic and needs the login URL at runtime.");
		}

		driver.get(loginUrl.trim());
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		try {
			if (evidenceDir != null) {
				writeFinalReport();
			}
		} catch (final IOException ignored) {
			// Best effort report writing even when browser interaction fails.
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String googleAccount = env("SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);

		runStep(LOGIN, () -> {
			clickByAnyVisibleText("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Ingresar con Google",
					"Continuar con Google", "Google");

			// Optional account chooser handling in the Google selection screen.
			if (isVisibleText(googleAccount, Duration.ofSeconds(7))) {
				clickByAnyVisibleText(googleAccount);
			}

			waitForAnyVisibleText("Mi Negocio", "Negocio");
			waitForSidebarNavigation();
			appWindowHandle = driver.getWindowHandle();
			captureScreenshot("01_dashboard_loaded.png");
		});

		runStep(MI_NEGOCIO_MENU, () -> {
			clickByAnyVisibleText("Mi Negocio");
			requireVisibleText("Agregar Negocio");
			requireVisibleText("Administrar Negocios");
			captureScreenshot("02_mi_negocio_menu_expanded.png");
		});

		runStep(AGREGAR_NEGOCIO_MODAL, () -> {
			clickByAnyVisibleText("Agregar Negocio");
			requireVisibleText("Crear Nuevo Negocio");
			final WebElement businessNameInput = waitForBusinessNameInput();
			Assert.assertNotNull("Input 'Nombre del Negocio' was not found.", businessNameInput);

			requireVisibleText("Tienes 2 de 3 negocios");
			requireClickableText("Cancelar");
			requireClickableText("Crear Negocio");
			captureScreenshot("03_agregar_negocio_modal.png");

			businessNameInput.click();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatizaci\u00f3n");
			clickByAnyVisibleText("Cancelar");
			waitUntilTextNotVisible("Crear Nuevo Negocio", Duration.ofSeconds(12));
		});

		runStep(ADMINISTRAR_NEGOCIOS_VIEW, () -> {
			if (!isVisibleText("Administrar Negocios", Duration.ofSeconds(3))) {
				clickByAnyVisibleText("Mi Negocio");
			}
			clickByAnyVisibleText("Administrar Negocios");

			requireVisibleText("Informaci\u00f3n General");
			requireVisibleText("Detalles de la Cuenta");
			requireVisibleText("Tus Negocios");
			requireVisibleText("Secci\u00f3n Legal");
			captureFullPageScreenshot("04_administrar_negocios_full_page.png");
		});

		runStep(INFORMACION_GENERAL, () -> {
			final WebElement infoSection = findSectionByHeading("Informaci\u00f3n General");
			assertSectionContainsEmail(infoSection, googleAccount);
			assertSectionContainsLikelyUserName(infoSection);
			assertSectionContainsText(infoSection, "BUSINESS PLAN");
			assertSectionHasClickableText(infoSection, "Cambiar Plan");
		});

		runStep(DETALLES_CUENTA, () -> {
			final WebElement detailsSection = findSectionByHeading("Detalles de la Cuenta");
			assertSectionContainsText(detailsSection, "Cuenta creada");
			assertSectionContainsText(detailsSection, "Estado activo");
			assertSectionContainsText(detailsSection, "Idioma seleccionado");
		});

		runStep(TUS_NEGOCIOS, () -> {
			final WebElement businessesSection = findSectionByHeading("Tus Negocios");
			assertSectionHasBusinessList(businessesSection);
			assertSectionHasClickableText(businessesSection, "Agregar Negocio");
			assertSectionContainsText(businessesSection, "Tienes 2 de 3 negocios");
		});

		runStep(TERMINOS_CONDICIONES, () -> {
			termsFinalUrl = validateLegalDocument("T\u00e9rminos y Condiciones", "T\u00e9rminos y Condiciones",
					"05_terminos_y_condiciones.png");
		});

		runStep(POLITICA_PRIVACIDAD, () -> {
			privacyFinalUrl = validateLegalDocument("Pol\u00edtica de Privacidad", "Pol\u00edtica de Privacidad",
					"06_politica_de_privacidad.png");
		});

		writeFinalReport();
		assertAllStepsPassed();
	}

	private void runStep(final String stepName, final StepExecution execution) {
		try {
			execution.run();
			stepStatus.put(stepName, "PASS");
			stepDetails.put(stepName, "");
		} catch (final Throwable throwable) {
			stepStatus.put(stepName, "FAIL");
			stepDetails.put(stepName, firstNonBlankMessage(throwable));
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failures = new ArrayList<>();
		for (final String reportField : REPORT_FIELDS) {
			if (!"PASS".equals(stepStatus.get(reportField))) {
				failures.add(reportField + " => " + stepStatus.get(reportField) + " (" + stepDetails.get(reportField) + ")");
			}
		}

		Assert.assertTrue(
				"SaleADS Mi Negocio workflow has failing validations:\n"
						+ String.join("\n", failures)
						+ "\n\nFinal report: " + finalReportPath.toAbsolutePath(),
				failures.isEmpty());
	}

	private void waitForSidebarNavigation() {
		final By sidebarLocator = By.xpath(
				"(//aside[.//*[contains(normalize-space(.), 'Negocio')]] | //nav[.//*[contains(normalize-space(.), 'Negocio')]])[1]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(sidebarLocator));
	}

	private WebElement waitForBusinessNameInput() {
		final By locator = By.xpath(
				"(//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"
						+ " | //input[contains(@placeholder, 'Nombre del Negocio')]"
						+ " | //input[@name='nombreNegocio'])[1]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void clickByAnyVisibleText(final String... visibleTexts) {
		final WebElement element = findClickableByAnyVisibleText(visibleTexts);
		clickAndWait(element);
	}

	private WebElement findClickableByAnyVisibleText(final String... visibleTexts) {
		for (final String text : visibleTexts) {
			final By locator = By.xpath(
					"(//*[self::button or self::a or @role='button' or @role='menuitem' or self::span or self::div]"
							+ "[contains(normalize-space(.), " + toXPathLiteral(text) + ")])[1]");
			try {
				return wait.until(ExpectedConditions.elementToBeClickable(locator));
			} catch (final TimeoutException ignored) {
				// Try next candidate text.
			}
		}
		throw new AssertionError("No clickable element found for texts: " + Arrays.toString(visibleTexts));
	}

	private void clickAndWait(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			element.click();
		} catch (final RuntimeException clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete".equals(
				((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		pause(400);
	}

	private void waitForAnyVisibleText(final String... texts) {
		for (final String text : texts) {
			if (isVisibleText(text, Duration.ofSeconds(10))) {
				return;
			}
		}
		throw new AssertionError("None of the expected texts are visible: " + Arrays.toString(texts));
	}

	private boolean isVisibleText(final String text, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
			return true;
		} catch (final TimeoutException exception) {
			return false;
		}
	}

	private void requireVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
	}

	private void requireClickableText(final String text) {
		wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
				"(//*[self::button or self::a or @role='button' or self::span or self::div]"
						+ "[contains(normalize-space(.), " + toXPathLiteral(text) + ")])[1]")));
	}

	private void waitUntilTextNotVisible(final String text, final Duration timeout) {
		new WebDriverWait(driver, timeout).until(ExpectedConditions.invisibilityOfElementLocated(byVisibleText(text)));
	}

	private By byVisibleText(final String text) {
		return By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]");
	}

	private WebElement findSectionByHeading(final String headingText) {
		final WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(headingText)));
		try {
			return heading.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		} catch (final NoSuchElementException noParentContainer) {
			return heading;
		}
	}

	private void assertSectionContainsText(final WebElement section, final String expectedText) {
		final String text = section.getText();
		Assert.assertTrue("Expected section to contain text '" + expectedText + "' but found:\n" + text,
				text.contains(expectedText));
	}

	private void assertSectionHasClickableText(final WebElement section, final String expectedText) {
		final List<WebElement> matching = section.findElements(By.xpath(
				".//*[self::button or self::a or @role='button' or self::span or self::div]"
						+ "[contains(normalize-space(.), " + toXPathLiteral(expectedText) + ")]"));
		Assert.assertFalse("Expected clickable text '" + expectedText + "' inside section.", matching.isEmpty());
	}

	private void assertSectionContainsEmail(final WebElement section, final String expectedEmail) {
		final String sectionText = section.getText();
		final boolean containsExpectedEmail = sectionText.contains(expectedEmail);
		final boolean containsAnyEmail = sectionText.matches("(?s).*\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b.*");
		Assert.assertTrue("Expected user email in section but found:\n" + sectionText,
				containsExpectedEmail || containsAnyEmail);
	}

	private void assertSectionContainsLikelyUserName(final WebElement section) {
		final String[] lines = section.getText().split("\\R");
		boolean hasLikelyName = false;
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.equalsIgnoreCase("Informaci\u00f3n General") || trimmed.equalsIgnoreCase("BUSINESS PLAN")
					|| trimmed.equalsIgnoreCase("Cambiar Plan") || trimmed.contains("@")) {
				continue;
			}
			if (trimmed.length() >= 3) {
				hasLikelyName = true;
				break;
			}
		}
		Assert.assertTrue("Expected visible user name text in 'Informaci\u00f3n General' section.", hasLikelyName);
	}

	private void assertSectionHasBusinessList(final WebElement section) {
		final List<WebElement> explicitItems = section.findElements(
				By.xpath(".//li | .//tr[td] | .//div[contains(@class, 'business')] | .//*[@data-business-id]"));
		if (!explicitItems.isEmpty()) {
			return;
		}

		final String text = section.getText()
				.replace("Tus Negocios", "")
				.replace("Agregar Negocio", "")
				.replace("Tienes 2 de 3 negocios", "")
				.trim();
		Assert.assertTrue("Expected visible business list content in 'Tus Negocios' section.", text.length() > 2);
	}

	private String validateLegalDocument(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String originalHandle = appWindowHandle == null ? driver.getWindowHandle() : appWindowHandle;
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String originalUrl = driver.getCurrentUrl();

		clickByAnyVisibleText(linkText);

		final long timeoutAt = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
		boolean switchedWindow = false;
		while (System.currentTimeMillis() < timeoutAt) {
			final Set<String> currentHandles = new LinkedHashSet<>(driver.getWindowHandles());
			if (currentHandles.size() > handlesBefore.size()) {
				for (final String candidate : currentHandles) {
					if (!handlesBefore.contains(candidate)) {
						driver.switchTo().window(candidate);
						switchedWindow = true;
						break;
					}
				}
				break;
			}
			if (!originalUrl.equals(driver.getCurrentUrl())) {
				break;
			}
			pause(250);
		}

		waitForUiToLoad();
		requireVisibleText(headingText);

		final String bodyText = driver.findElement(By.tagName("body")).getText().trim();
		Assert.assertTrue("Expected legal content text to be visible for '" + headingText + "'.", bodyText.length() > 120);

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (switchedWindow) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), evidenceDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureFullPageScreenshot(final String fileName) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();

		final JavascriptExecutor javascriptExecutor = (JavascriptExecutor) driver;
		final Long scrollWidth = ((Number) javascriptExecutor.executeScript(
				"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, 1920);")).longValue();
		final Long scrollHeight = ((Number) javascriptExecutor.executeScript(
				"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, 1080);")).longValue();

		driver.manage().window().setSize(new Dimension(scrollWidth.intValue(), Math.min(scrollHeight.intValue(), 8000)));
		waitForUiToLoad();
		captureScreenshot(fileName);

		driver.manage().window().setSize(originalSize);
		waitForUiToLoad();
	}

	private Path buildEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("saleads_mi_negocio_full_test\n");
		reportBuilder.append("evidence_dir: ").append(evidenceDir.toAbsolutePath()).append('\n');
		reportBuilder.append('\n');
		reportBuilder.append("Final Report (PASS/FAIL)\n");
		reportBuilder.append("------------------------\n");
		for (final String field : REPORT_FIELDS) {
			reportBuilder.append(field).append(": ").append(stepStatus.get(field));
			final String detail = stepDetails.get(field);
			if (detail != null && !detail.isBlank()) {
				reportBuilder.append(" | detail: ").append(detail);
			}
			reportBuilder.append('\n');
		}
		reportBuilder.append('\n');
		reportBuilder.append("T\u00e9rminos y Condiciones URL: ").append(termsFinalUrl).append('\n');
		reportBuilder.append("Pol\u00edtica de Privacidad URL: ").append(privacyFinalUrl).append('\n');

		Files.writeString(finalReportPath, reportBuilder.toString(), StandardCharsets.UTF_8);
	}

	private String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value.trim();
	}

	private String firstNonBlankMessage(final Throwable throwable) {
		Throwable cursor = throwable;
		while (cursor != null) {
			final String message = cursor.getMessage();
			if (message != null && !message.isBlank()) {
				return message.replace('\n', ' ').trim();
			}
			cursor = cursor.getCause();
		}
		return throwable.getClass().getSimpleName();
	}

	private void pause(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String toXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder concat = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				concat.append(", ");
			}
			if (chars[i] == '\'') {
				concat.append("\"'\"");
			} else if (chars[i] == '"') {
				concat.append("'\"'");
			} else {
				concat.append('\'').append(chars[i]).append('\'');
			}
		}
		concat.append(')');
		return concat.toString();
	}

	@FunctionalInterface
	private interface StepExecution {
		void run() throws Exception;
	}
}
