package io.proleap.cobol.e2e.saleads;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.openqa.selenium.Dimension;
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

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String accountPageUrl;

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to run this environment-agnostic E2E.", loginUrl != null && !loginUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));

		if (headless) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		evidenceDir = Paths.get("target", "saleads-evidence", DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now()));
		Files.createDirectories(evidenceDir);

		driver.get(loginUrl);
		waitForDocumentReady();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void shouldValidateMiNegocioModuleWorkflow() throws Exception {
		runStep("Login", this::loginWithGoogleAndValidateDashboard);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegocios);
		runStep("Informacion General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Terminos y Condiciones", () -> validateLegalDocument("Términos y Condiciones", "Términos y Condiciones", "08-terminos-y-condiciones"));
		runStep("Politica de Privacidad", () -> validateLegalDocument("Política de Privacidad", "Política de Privacidad", "09-politica-de-privacidad"));

		final Path reportPath = writeSummaryReport();
		final List<String> failedSteps = stepResults.entrySet().stream().filter(entry -> !entry.getValue().pass).map(Map.Entry::getKey).collect(Collectors.toList());

		Assert.assertTrue("Workflow validation failed for: " + failedSteps + ". See report: " + reportPath.toAbsolutePath(), failedSteps.isEmpty());
	}

	private void loginWithGoogleAndValidateDashboard() throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBeforeLogin = driver.getWindowHandles();
		final String selectedAccount = System.getenv().getOrDefault("SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);

		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google");

		final String popupWindow = waitForNewWindow(windowsBeforeLogin, Duration.ofSeconds(10));
		if (popupWindow != null) {
			driver.switchTo().window(popupWindow);
			waitForDocumentReady();
		}

		clickIfVisible(Duration.ofSeconds(10), selectedAccount);

		if (!driver.getWindowHandle().equals(appWindow) && driver.getWindowHandles().contains(appWindow)) {
			driver.switchTo().window(appWindow);
		}

		waitUntilAnyTextVisible(Duration.ofSeconds(60), "Negocio", "Mi Negocio", "Dashboard", "Panel");

		Assert.assertTrue("Main app interface did not load.", isAnyTextVisible("Negocio", "Mi Negocio", "Dashboard", "Panel"));
		Assert.assertTrue("Left sidebar navigation is not visible.", isSidebarVisible());

		captureScreenshot("01-dashboard-loaded", false);
	}

	private void openMiNegocioMenu() throws IOException {
		if (!isAnyTextVisible("Mi Negocio")) {
			clickByVisibleText("Negocio");
		}

		clickByVisibleText("Mi Negocio");

		waitUntilAnyTextVisible(DEFAULT_TIMEOUT, "Agregar Negocio", "Administrar Negocios");
		Assert.assertTrue("Submenu item 'Agregar Negocio' is not visible.", isTextVisible("Agregar Negocio"));
		Assert.assertTrue("Submenu item 'Administrar Negocios' is not visible.", isTextVisible("Administrar Negocios"));

		captureScreenshot("02-mi-negocio-menu-expanded", false);
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");

		final WebElement modal = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral("Crear Nuevo Negocio") + ")]/ancestor::*[@role='dialog' or contains(@class, 'modal')][1]")));

		Assert.assertTrue("Modal title 'Crear Nuevo Negocio' is missing.", modal.getText().contains("Crear Nuevo Negocio"));
		Assert.assertTrue("Field label 'Nombre del Negocio' is missing.", modal.getText().contains("Nombre del Negocio"));
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' is missing.", modal.getText().contains("Tienes 2 de 3 negocios"));
		Assert.assertTrue("Button 'Cancelar' is missing.", modal.getText().contains("Cancelar"));
		Assert.assertTrue("Button 'Crear Negocio' is missing.", modal.getText().contains("Crear Negocio"));

		final List<WebElement> inputCandidates = modal.findElements(By.xpath(".//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio') or @type='text']"));
		Assert.assertFalse("Input field 'Nombre del Negocio' was not found.", inputCandidates.isEmpty());

		final WebElement nameInput = inputCandidates.get(0);
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatizacion");

		captureScreenshot("03-agregar-negocio-modal", false);

		clickByVisibleText("Cancelar");
		waitUntilTextNotVisible(Duration.ofSeconds(10), "Crear Nuevo Negocio");
	}

	private void openAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");

		waitUntilAnyTextVisible(DEFAULT_TIMEOUT, "Información General", "Detalles de la Cuenta", "Tus Negocios", "Sección Legal");
		Assert.assertTrue("Section 'Información General' is missing.", isTextVisible("Información General"));
		Assert.assertTrue("Section 'Detalles de la Cuenta' is missing.", isTextVisible("Detalles de la Cuenta"));
		Assert.assertTrue("Section 'Tus Negocios' is missing.", isTextVisible("Tus Negocios"));
		Assert.assertTrue("Section 'Sección Legal' is missing.", isTextVisible("Sección Legal"));

		accountPageUrl = driver.getCurrentUrl();
		captureScreenshot("04-administrar-negocios", true);
	}

	private void validateInformacionGeneral() {
		final String expectedUserName = System.getenv("SALEADS_EXPECTED_USER_NAME");
		final String selectedAccount = System.getenv().getOrDefault("SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);

		if (expectedUserName != null && !expectedUserName.isBlank()) {
			Assert.assertTrue("Expected user name is not visible in 'Información General'.", isTextVisible(expectedUserName));
		} else {
			Assert.assertTrue("Could not infer a visible user name in 'Información General'.", isAnyTextVisible("Nombre", "Usuario", "Perfil"));
		}

		final boolean emailFound = isTextVisible(selectedAccount) || EMAIL_PATTERN.matcher(driver.getPageSource()).find();
		Assert.assertTrue("User email is not visible in 'Información General'.", emailFound);

		Assert.assertTrue("Text 'BUSINESS PLAN' is missing.", isTextVisible("BUSINESS PLAN"));
		Assert.assertTrue("Button 'Cambiar Plan' is missing.", isTextVisible("Cambiar Plan"));
	}

	private void validateDetallesCuenta() {
		Assert.assertTrue("'Cuenta creada' is missing.", isTextVisible("Cuenta creada"));
		Assert.assertTrue("'Estado activo' is missing.", isTextVisible("Estado activo"));
		Assert.assertTrue("'Idioma seleccionado' is missing.", isTextVisible("Idioma seleccionado"));
	}

	private void validateTusNegocios() {
		Assert.assertTrue("Business list section is not visible.", isTextVisible("Tus Negocios"));
		Assert.assertTrue("Button 'Agregar Negocio' is missing.", isTextVisible("Agregar Negocio"));
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' is missing.", isTextVisible("Tienes 2 de 3 negocios"));
	}

	private void validateLegalDocument(final String linkText, final String headingText, final String screenshotBaseName) throws IOException {
		final String applicationWindow = driver.getWindowHandle();
		final String startUrl = driver.getCurrentUrl();
		final Set<String> windowsBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);

		final String newWindow = waitForNewWindow(windowsBeforeClick, Duration.ofSeconds(10));
		final boolean openedNewTab = newWindow != null;

		if (openedNewTab) {
			driver.switchTo().window(newWindow);
		}

		wait.until((ExpectedCondition<Boolean>) d -> !driver.getCurrentUrl().isBlank() && (!driver.getCurrentUrl().equals(startUrl) || openedNewTab));
		waitForDocumentReady();

		Assert.assertTrue("Heading '" + headingText + "' is not visible.", isTextVisible(headingText));

		final List<WebElement> legalParagraphs = driver.findElements(By.xpath("//p[string-length(normalize-space(.)) > 40]"));
		Assert.assertFalse("Legal content text is missing for '" + headingText + "'.", legalParagraphs.isEmpty());

		legalUrls.put(headingText, driver.getCurrentUrl());
		captureScreenshot(screenshotBaseName, false);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(applicationWindow);
		} else {
			if (accountPageUrl != null && !accountPageUrl.isBlank()) {
				driver.navigate().to(accountPageUrl);
			} else {
				driver.navigate().back();
			}
		}

		waitForDocumentReady();
		waitUntilAnyTextVisible(DEFAULT_TIMEOUT, "Sección Legal", "Tus Negocios");
	}

	private void runStep(final String stepName, final CheckedRunnable checkedRunnable) {
		try {
			checkedRunnable.run();
			stepResults.put(stepName, StepResult.pass());
		} catch (final Exception ex) {
			stepResults.put(stepName, StepResult.fail(ex.getMessage()));
		}
	}

	private void waitForDocumentReady() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
	}

	private void waitUntilAnyTextVisible(final Duration timeout, final String... texts) {
		final WebDriverWait customWait = new WebDriverWait(driver, timeout);
		customWait.until(d -> isAnyTextVisible(texts));
	}

	private void waitUntilTextNotVisible(final Duration timeout, final String text) {
		final WebDriverWait customWait = new WebDriverWait(driver, timeout);
		customWait.until(d -> !isTextVisible(text));
	}

	private boolean isAnyTextVisible(final String... texts) {
		return Arrays.stream(texts).anyMatch(this::isTextVisible);
	}

	private boolean isTextVisible(final String text) {
		final List<WebElement> candidates = driver.findElements(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]"));
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean isSidebarVisible() {
		final List<WebElement> sidebars = driver.findElements(By.xpath("//nav | //aside | //*[@role='navigation' or contains(@class, 'sidebar')]"));
		for (final WebElement sidebar : sidebars) {
			if (sidebar.isDisplayed() && (sidebar.getText().contains("Negocio") || sidebar.getText().contains("Mi Negocio"))) {
				return true;
			}
		}
		return false;
	}

	private void clickByVisibleText(final String... texts) {
		Exception lastError = null;
		for (final String text : texts) {
			try {
				final String xpath = "(//*[self::button or self::a or self::li or self::span or self::div or @role='button'][contains(normalize-space(.), "
						+ xpathLiteral(text) + ")])[1]";
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(xpath)));
				element.click();
				waitForDocumentReady();
				return;
			} catch (final Exception ex) {
				lastError = ex;
			}
		}

		throw new IllegalStateException("Could not click any element with text options: " + Arrays.toString(texts), lastError);
	}

	private void clickIfVisible(final Duration timeout, final String text) {
		try {
			final WebDriverWait customWait = new WebDriverWait(driver, timeout);
			final String xpath = "(//*[self::button or self::a or self::li or self::span or self::div or @role='button'][contains(normalize-space(.), "
					+ xpathLiteral(text) + ")])[1]";
			final WebElement element = customWait.until(ExpectedConditions.elementToBeClickable(By.xpath(xpath)));
			element.click();
			waitForDocumentReady();
		} catch (final TimeoutException ignored) {
			// Google account selector is optional depending on active session.
		}
	}

	private String waitForNewWindow(final Set<String> windowsBeforeClick, final Duration timeout) {
		final WebDriverWait customWait = new WebDriverWait(driver, timeout);
		try {
			return customWait.until(d -> {
				final Set<String> currentHandles = d.getWindowHandles();
				if (currentHandles.size() <= windowsBeforeClick.size()) {
					return null;
				}

				for (final String handle : currentHandles) {
					if (!windowsBeforeClick.contains(handle)) {
						return handle;
					}
				}

				return null;
			});
		} catch (final TimeoutException ignored) {
			return null;
		}
	}

	private void captureScreenshot(final String name, final boolean fullPage) throws IOException {
		final Path target = evidenceDir.resolve(name + ".png");
		Dimension originalSize = null;

		if (fullPage) {
			originalSize = driver.manage().window().getSize();
			final long height = ((Number) ((JavascriptExecutor) driver).executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);")).longValue();
			final int resizedHeight = (int) Math.min(3200L, Math.max(height + 200L, 1200L));
			driver.manage().window().setSize(new Dimension(1920, resizedHeight));
			waitForDocumentReady();
		}

		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(target, screenshot);

		if (fullPage && originalSize != null) {
			driver.manage().window().setSize(originalSize);
			waitForDocumentReady();
		}
	}

	private Path writeSummaryReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("# SaleADS Mi Negocio Full Workflow Report");
		lines.add("");
		lines.add("| Checkpoint | Status | Details |");
		lines.add("|---|---|---|");

		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			final String status = entry.getValue().pass ? "PASS" : "FAIL";
			final String details = entry.getValue().details == null ? "" : entry.getValue().details.replace('\n', ' ');
			lines.add("| " + entry.getKey() + " | " + status + " | " + details + " |");
		}

		lines.add("");
		lines.add("## Captured legal URLs");
		if (legalUrls.isEmpty()) {
			lines.add("- None");
		} else {
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				lines.add("- " + entry.getKey() + ": " + entry.getValue());
			}
		}

		lines.add("");
		lines.add("## Evidence Directory");
		lines.add("- " + evidenceDir.toAbsolutePath());

		final Path reportPath = evidenceDir.resolve("summary-report.md");
		Files.write(reportPath, lines);
		return reportPath;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		for (int index = 0; index < value.length(); index++) {
			final char current = value.charAt(index);
			if (current == '\'') {
				builder.append("\"'\"");
			} else {
				builder.append('\'').append(current).append('\'');
			}

			if (index < value.length() - 1) {
				builder.append(',');
			}
		}
		builder.append(')');
		return builder.toString();
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static class StepResult {
		private final boolean pass;
		private final String details;

		private StepResult(final boolean pass, final String details) {
			this.pass = pass;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details == null ? "" : details);
		}
	}
}
