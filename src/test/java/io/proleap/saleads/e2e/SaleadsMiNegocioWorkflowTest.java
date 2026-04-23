package io.proleap.saleads.e2e;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
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

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
			Pattern.CASE_INSENSITIVE);
	private static final List<String> REPORT_FIELDS = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> finalReport = new LinkedHashMap<>();
	private final Map<String, String> metadata = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Path reportPath;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		initializeReport();
		createEvidenceDirectory();

		final String baseUrl = firstNonBlank(System.getProperty("saleads.baseUrl"), System.getenv("SALEADS_BASE_URL"));

		try {
			Assume.assumeTrue("Set saleads.baseUrl or SALEADS_BASE_URL to run the SaleADS workflow test.",
					baseUrl != null && !baseUrl.isBlank());

			startDriver();
			driver.get(baseUrl);
			waitForUiToLoad();

			final boolean loginPassed = runStep("Login", this::stepLoginWithGoogle);

			final boolean menuPassed;
			if (loginPassed) {
				menuPassed = runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			} else {
				menuPassed = false;
				markPrerequisiteFailure("Mi Negocio menu", "Login failed.");
			}

			if (menuPassed) {
				runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
				runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			} else {
				markPrerequisiteFailure("Agregar Negocio modal", "Mi Negocio menu failed.");
				markPrerequisiteFailure("Administrar Negocios view", "Mi Negocio menu failed.");
			}

			final boolean administrarNegociosPassed = finalReport.get("Administrar Negocios view").passed;
			if (administrarNegociosPassed) {
				runStep("Información General", this::stepValidateInformacionGeneral);
				runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
				runStep("Tus Negocios", this::stepValidateTusNegocios);
				runStep("Términos y Condiciones", this::stepValidateTerminosCondiciones);
				runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);
			} else {
				markPrerequisiteFailure("Información General", "Administrar Negocios view failed.");
				markPrerequisiteFailure("Detalles de la Cuenta", "Administrar Negocios view failed.");
				markPrerequisiteFailure("Tus Negocios", "Administrar Negocios view failed.");
				markPrerequisiteFailure("Términos y Condiciones", "Administrar Negocios view failed.");
				markPrerequisiteFailure("Política de Privacidad", "Administrar Negocios view failed.");
			}
		} finally {
			safeQuitDriver();
			writeFinalReport();
		}

		final List<String> failedValidations = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			if (!finalReport.get(field).passed) {
				failedValidations.add(field);
			}
		}

		if (!failedValidations.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow failed validations: " + failedValidations + ". Report: "
					+ reportPath.toAbsolutePath());
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByAnyVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Acceder con Google", "Google");
		maybeSelectGoogleAccount();

		waitForSidebar();
		assertVisibleText("Negocio");
		addScreenshot("Login", "01-dashboard-loaded.png", false);
		finalReport.get("Login").details = "Dashboard loaded and left sidebar is visible.";
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		waitForSidebar();
		ensureMiNegocioSubmenuExpanded();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		addScreenshot("Mi Negocio menu", "02-mi-negocio-expanded.png", false);
		finalReport.get("Mi Negocio menu").details = "Mi Negocio expanded with expected submenu entries.";
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		ensureMiNegocioSubmenuExpanded();
		clickByAnyVisibleText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		assertAnyElementVisible(By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final WebElement nameInput = findFirstVisible(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
		if (nameInput != null) {
			scrollTo(nameInput);
			nameInput.click();
			nameInput.sendKeys("Negocio Prueba Automatización");
		}

		addScreenshot("Agregar Negocio modal", "03-agregar-negocio-modal.png", false);
		clickByAnyVisibleText("Cancelar");
		finalReport.get("Agregar Negocio modal").details = "Crear Nuevo Negocio modal validated and closed.";
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioSubmenuExpanded();
		clickByAnyVisibleText("Administrar Negocios");

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		addScreenshot("Administrar Negocios view", "04-administrar-negocios-full.png", true);
		finalReport.get("Administrar Negocios view").details = "Administrar Negocios page loaded with required sections.";
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionContainer("Información General");
		final String sectionText = section.getText();
		assertCondition(hasEmail(sectionText), "Expected user email in Información General.");
		assertCondition(hasLikelyUserName(sectionText), "Expected user name in Información General.");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
		finalReport.get("Información General").details = "Name, email, BUSINESS PLAN and Cambiar Plan were validated.";
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
		finalReport.get("Detalles de la Cuenta").details = "Detalles de la Cuenta fields are visible.";
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionContainer("Tus Negocios");
		final String sectionText = section.getText();
		assertCondition(sectionText.replace("Tus Negocios", "").trim().length() > 10,
				"Expected business list content under Tus Negocios.");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		finalReport.get("Tus Negocios").details = "Tus Negocios list, button and quota text were validated.";
	}

	private void stepValidateTerminosCondiciones() throws Exception {
		final String url = validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "05-terminos.png");
		metadata.put("Términos y Condiciones URL", url);
		finalReport.get("Términos y Condiciones").details = "Legal page validated. URL: " + url;
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		final String url = validateLegalLink("Política de Privacidad", "Política de Privacidad", "06-privacidad.png");
		metadata.put("Política de Privacidad URL", url);
		finalReport.get("Política de Privacidad").details = "Legal page validated. URL: " + url;
	}

	private String validateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String previousUrl = driver.getCurrentUrl();

		clickLegalLink(linkText);
		final String targetWindow = waitForNewWindowOrNavigation(windowsBeforeClick, previousUrl);
		driver.switchTo().window(targetWindow);
		waitForUiToLoad();

		assertVisibleText(expectedHeading);
		assertCondition(extractVisibleBodyText().length() > 120, "Expected legal content text on " + expectedHeading);
		addScreenshot(expectedHeading, screenshotName, false);
		final String resolvedUrl = driver.getCurrentUrl();

		if (!targetWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else if (!resolvedUrl.equals(previousUrl)) {
			driver.navigate().back();
		}

		waitForUiToLoad();
		return resolvedUrl;
	}

	private void clickLegalLink(final String linkText) {
		final WebElement legalSection = findSectionContainer("Sección Legal");
		final List<WebElement> localLinks = legalSection
				.findElements(By.xpath(".//a[contains(normalize-space(.), " + xpathLiteral(linkText)
						+ ")] | .//button[contains(normalize-space(.), " + xpathLiteral(linkText)
						+ ")] | .//*[@role='button' and contains(normalize-space(.), " + xpathLiteral(linkText) + ")]"));

		for (final WebElement candidate : localLinks) {
			if (candidate.isDisplayed()) {
				clickElement(candidate);
				return;
			}
		}

		clickByAnyVisibleText(linkText);
	}

	private void maybeSelectGoogleAccount() {
		final String originalWindow = driver.getWindowHandle();
		final long timeoutAt = System.currentTimeMillis() + 20_000L;

		while (System.currentTimeMillis() < timeoutAt) {
			for (final String windowHandle : driver.getWindowHandles()) {
				driver.switchTo().window(windowHandle);

				if (clickIfVisible(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(GOOGLE_ACCOUNT_EMAIL)
						+ ")]"))) {
					waitForUiToLoad();
					driver.switchTo().window(originalWindow);
					return;
				}

				final WebElement emailInput = findFirstVisible(By.cssSelector("input[type='email']"));
				if (emailInput != null) {
					emailInput.clear();
					emailInput.sendKeys(GOOGLE_ACCOUNT_EMAIL);
					emailInput.sendKeys(Keys.ENTER);
					waitForUiToLoad();
					driver.switchTo().window(originalWindow);
					return;
				}
			}

			sleep(400);
		}

		driver.switchTo().window(originalWindow);
	}

	private String waitForNewWindowOrNavigation(final Set<String> windowsBeforeClick, final String previousUrl) {
		final long timeoutAt = System.currentTimeMillis() + DEFAULT_TIMEOUT.toMillis();

		while (System.currentTimeMillis() < timeoutAt) {
			final Set<String> currentWindows = driver.getWindowHandles();
			if (currentWindows.size() > windowsBeforeClick.size()) {
				for (final String handle : currentWindows) {
					if (!windowsBeforeClick.contains(handle)) {
						return handle;
					}
				}
			}

			if (!driver.getCurrentUrl().equals(previousUrl)) {
				return driver.getWindowHandle();
			}

			sleep(250);
		}

		return driver.getWindowHandle();
	}

	private void startDriver() {
		final boolean headless = Boolean.parseBoolean(
				firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "true"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox", "--disable-gpu");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
	}

	private void waitForSidebar() {
		assertAnyElementVisible(By.xpath("//aside"),
				By.xpath("//*[@role='navigation']"),
				By.xpath("//*[contains(translate(@class, 'SIDEBAR', 'sidebar'), 'sidebar')]"));
	}

	private void clickByAnyVisibleText(final String... texts) {
		Exception lastError = null;
		for (final String text : texts) {
			final List<By> candidateLocators = List.of(
					By.xpath("//button[contains(normalize-space(.), " + xpathLiteral(text) + ")]"),
					By.xpath("//a[contains(normalize-space(.), " + xpathLiteral(text) + ")]"),
					By.xpath("//*[@role='button' and contains(normalize-space(.), " + xpathLiteral(text) + ")]"),
					By.xpath("//*[@role='menuitem' and contains(normalize-space(.), " + xpathLiteral(text) + ")]"),
					By.xpath(
							"//div[contains(@class, 'menu') and contains(normalize-space(.), " + xpathLiteral(text) + ")]"),
					By.xpath("//span[contains(normalize-space(.), " + xpathLiteral(text) + ")]"));

			for (final By locator : candidateLocators) {
				try {
					final WebElement candidate = waitUntilVisibleAndClickable(locator, SHORT_TIMEOUT);
					clickElement(candidate);
					return;
				} catch (final Exception e) {
					lastError = e;
				}
			}
		}

		throw new AssertionError("Unable to click any of expected texts: " + String.join(", ", texts), lastError);
	}

	private boolean clickIfVisible(final By by) {
		final WebElement element = findFirstVisible(by);
		if (element == null) {
			return false;
		}

		clickElement(element);
		return true;
	}

	private void clickElement(final WebElement element) {
		scrollTo(element);

		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiToLoad();
	}

	private WebElement waitUntilVisibleAndClickable(final By locator) {
		return waitUntilVisibleAndClickable(locator, DEFAULT_TIMEOUT);
	}

	private WebElement waitUntilVisibleAndClickable(final By locator, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(driverInstance -> {
			final List<WebElement> candidates = driverInstance.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed() && candidate.isEnabled()) {
					return candidate;
				}
			}

			return null;
		});
	}

	private void waitForUiToLoad() {
		wait.until(driverInstance -> "complete"
				.equals(((JavascriptExecutor) driverInstance).executeScript("return document.readyState")));
		sleep(450);
	}

	private void assertVisibleText(final String expectedText) {
		try {
			new WebDriverWait(driver, DEFAULT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(expectedText) + ")]")));
		} catch (final TimeoutException timeoutException) {
			throw new AssertionError("Expected visible text not found: " + expectedText, timeoutException);
		}
	}

	private void assertAnyElementVisible(final By... locators) {
		for (final By locator : locators) {
			if (findFirstVisible(locator) != null) {
				return;
			}
		}

		throw new AssertionError("Expected at least one visible element for locators.");
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

		return null;
	}

	private WebElement findSectionContainer(final String headingText) {
		final WebElement heading = waitUntilVisible(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(headingText)
				+ ")]"), DEFAULT_TIMEOUT);

		WebElement current = heading;
		WebElement bestContainer = heading;
		int bestLength = heading.getText() == null ? 0 : heading.getText().length();

		for (int depth = 0; depth < 6; depth++) {
			final WebElement parent;
			try {
				parent = current.findElement(By.xpath(".."));
			} catch (final Exception noParent) {
				break;
			}

			if (parent == current) {
				break;
			}

			final String parentText = parent.getText();
			if (parentText != null && parentText.contains(headingText) && parentText.length() > bestLength) {
				bestContainer = parent;
				bestLength = parentText.length();
			}

			current = parent;
		}

		return bestContainer;
	}

	private WebElement waitUntilVisible(final By locator, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(driverInstance -> {
			final List<WebElement> candidates = driverInstance.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					return candidate;
				}
			}
			return null;
		});
	}

	private void ensureMiNegocioSubmenuExpanded() {
		if (isTextVisible("Agregar Negocio") && isTextVisible("Administrar Negocios")) {
			return;
		}

		clickByAnyVisibleText("Negocio");
		clickByAnyVisibleText("Mi Negocio");
		waitForUiToLoad();

		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			clickByAnyVisibleText("Mi Negocio");
		}

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
	}

	private boolean isTextVisible(final String text) {
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]")));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private boolean hasEmail(final String text) {
		final Matcher matcher = EMAIL_PATTERN.matcher(text);
		return matcher.find();
	}

	private boolean hasLikelyUserName(final String text) {
		final String[] lines = text.split("\\R");
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.contains("@")) {
				continue;
			}
			if (trimmed.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN")) {
				continue;
			}
			if (trimmed.equalsIgnoreCase("Información General") || trimmed.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}
			if (trimmed.matches(".*[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}.*")) {
				return true;
			}
		}

		return false;
	}

	private String extractVisibleBodyText() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//main//*[self::p or self::li] | //body//*[self::p or self::li]"));
		final StringBuilder text = new StringBuilder();
		for (final WebElement paragraph : paragraphs) {
			if (paragraph.isDisplayed()) {
				final String value = paragraph.getText().trim();
				if (!value.isEmpty()) {
					text.append(value).append('\n');
				}
			}
		}
		return text.toString();
	}

	private void addScreenshot(final String field, final String fileName, final boolean fullPage) throws IOException {
		final Path output = evidenceDir.resolve(fileName);
		if (fullPage) {
			captureBestEffortFullPageScreenshot(output);
		} else {
			captureViewportScreenshot(output);
		}
		finalReport.get(field).screenshots.add(output.getFileName().toString());
	}

	private void captureViewportScreenshot(final Path output) throws IOException {
		final byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(output, png);
	}

	private void captureBestEffortFullPageScreenshot(final Path output) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final Number contentHeight = (Number) ((JavascriptExecutor) driver).executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight,"
							+ "document.body.offsetHeight, document.documentElement.offsetHeight);");
			final int targetHeight = Math.min(Math.max(contentHeight.intValue() + 160, 1080), 10000);
			driver.manage().window().setSize(new Dimension(1920, targetHeight));
			waitForUiToLoad();
			captureViewportScreenshot(output);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}
	}

	private void createEvidenceDirectory() throws IOException {
		final String executionId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Paths.get("target", "saleads-evidence", executionId);
		Files.createDirectories(evidenceDir);
		reportPath = evidenceDir.resolve("final-report.txt");
	}

	private void initializeReport() {
		for (final String field : REPORT_FIELDS) {
			finalReport.put(field, new StepResult());
		}
	}

	private void safeQuitDriver() {
		if (driver != null) {
			try {
				driver.quit();
			} catch (final Exception ignored) {
				// no-op
			}
		}
	}

	private boolean runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			finalReport.get(reportField).passed = true;
			if (finalReport.get(reportField).details == null || finalReport.get(reportField).details.isBlank()) {
				finalReport.get(reportField).details = "All validations passed.";
			}
			return true;
		} catch (final Exception | AssertionError error) {
			finalReport.get(reportField).passed = false;
			finalReport.get(reportField).details = error.getMessage();
			if (driver != null) {
				try {
					addScreenshot(reportField, sanitizeFileName(reportField) + "-failure.png", false);
				} catch (final Exception ignored) {
					// no-op
				}
			}
			return false;
		}
	}

	private void markPrerequisiteFailure(final String field, final String detail) {
		finalReport.get(field).passed = false;
		finalReport.get(field).details = detail;
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder output = new StringBuilder();
		output.append("saleads_mi_negocio_full_test").append('\n');
		output.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n').append('\n');
		output.append("Step results").append('\n');

		for (final String field : REPORT_FIELDS) {
			final StepResult result = finalReport.get(field);
			output.append("- ").append(field).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (result.details != null && !result.details.isBlank()) {
				output.append(" | ").append(result.details);
			}
			output.append('\n');
			if (!result.screenshots.isEmpty()) {
				output.append("  screenshots: ").append(String.join(", ", result.screenshots)).append('\n');
			}
		}

		if (!metadata.isEmpty()) {
			output.append('\n').append("Metadata").append('\n');
			for (final Map.Entry<String, String> entry : metadata.entrySet()) {
				output.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		Files.writeString(reportPath, output.toString(), StandardCharsets.UTF_8);
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int index = 0; index < chars.length; index++) {
			final String literal = chars[index] == '\'' ? "\"'\"" : "'" + chars[index] + "'";
			result.append(literal);
			if (index < chars.length - 1) {
				result.append(",");
			}
		}
		result.append(")");
		return result.toString();
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private String sanitizeFileName(final String rawText) {
		return rawText.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
	}

	private void assertCondition(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private void scrollTo(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void sleep(final long milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private boolean passed = false;
		private String details = "Step not executed.";
		private final List<String> screenshots = new ArrayList<>();
	}
}
