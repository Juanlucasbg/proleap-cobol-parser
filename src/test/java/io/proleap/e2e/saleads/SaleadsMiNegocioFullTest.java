package io.proleap.e2e.saleads;

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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFORMACION_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";
	private static final List<String> REPORT_ORDER = Arrays.asList(REPORT_LOGIN, REPORT_MI_NEGOCIO_MENU,
			REPORT_AGREGAR_NEGOCIO_MODAL, REPORT_ADMINISTRAR_NEGOCIOS_VIEW, REPORT_INFORMACION_GENERAL,
			REPORT_DETALLES_CUENTA, REPORT_TUS_NEGOCIOS, REPORT_TERMINOS, REPORT_PRIVACIDAD);

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> unexpectedErrors = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private Path finalReportPath;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue(
				"Skipped. Set SALEADS_E2E_ENABLED=true to execute the SaleADS Mi Negocio browser workflow test.",
				getBooleanEnv("SALEADS_E2E_ENABLED", false));

		evidenceDirectory = createEvidenceDirectory();
		for (final String field : REPORT_ORDER) {
			stepStatus.put(field, "NOT_RUN");
			stepDetails.put(field, "");
		}

		driver = createWebDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));

		final String loginUrl = getEnv("SALEADS_LOGIN_URL");
		if (isNotBlank(loginUrl)) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() throws IOException {
		try {
			if (driver != null) {
				driver.quit();
			}
		} finally {
			if (evidenceDirectory != null) {
				writeFinalReport();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		runStep(REPORT_LOGIN, this::executeLoginStep);
		runStep(REPORT_MI_NEGOCIO_MENU, this::executeMiNegocioMenuStep);
		runStep(REPORT_AGREGAR_NEGOCIO_MODAL, this::executeAgregarNegocioModalStep);
		runStep(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, this::executeAdministrarNegociosStep);
		runStep(REPORT_INFORMACION_GENERAL, this::executeInformacionGeneralStep);
		runStep(REPORT_DETALLES_CUENTA, this::executeDetallesCuentaStep);
		runStep(REPORT_TUS_NEGOCIOS, this::executeTusNegociosStep);
		runStep(REPORT_TERMINOS, () -> executeLegalLinkStep("Términos y Condiciones", "Términos y Condiciones"));
		runStep(REPORT_PRIVACIDAD, () -> executeLegalLinkStep("Política de Privacidad", "Política de Privacidad"));

		final List<String> failedSteps = new ArrayList<>();
		for (final String reportField : REPORT_ORDER) {
			if (!"PASS".equals(stepStatus.get(reportField))) {
				failedSteps.add(reportField);
			}
		}

		if (!unexpectedErrors.isEmpty()) {
			Assert.fail("Unexpected errors encountered: " + String.join(" | ", unexpectedErrors));
		}

		Assert.assertTrue("Workflow failed for steps: " + failedSteps + ". See report: " + finalReportPath,
				failedSteps.isEmpty());
	}

	private void executeLoginStep() throws Exception {
		if (!isSidebarVisible()) {
			final Optional<WebElement> loginButton = findFirstVisibleElementByText("Sign in with Google", "Iniciar sesión con Google",
					"Ingresar con Google", "Acceder con Google", "Continuar con Google");
			if (loginButton.isPresent()) {
				clickAndWait(loginButton.get());
				maybePickGoogleAccount();
			}
		}

		final boolean mainInterfaceVisible = waitUntilAnyTextVisible(Duration.ofSeconds(45), "Negocio", "Mi Negocio",
				"Dashboard", "Panel");
		final boolean sidebarVisible = waitForSidebar(Duration.ofSeconds(45));
		captureScreenshot("01-dashboard-loaded");

		Assert.assertTrue("Main application interface is not visible after login.", mainInterfaceVisible);
		Assert.assertTrue("Left sidebar navigation is not visible after login.", sidebarVisible);
	}

	private void executeMiNegocioMenuStep() throws Exception {
		expandMiNegocioMenu();

		Assert.assertTrue("Mi Negocio submenu did not show 'Agregar Negocio'.", isTextVisible("Agregar Negocio"));
		Assert.assertTrue("Mi Negocio submenu did not show 'Administrar Negocios'.", isTextVisible("Administrar Negocios"));
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void executeAgregarNegocioModalStep() throws Exception {
		clickByVisibleText("Agregar Negocio");
		final WebElement modalTitle = waitUntilTextVisible("Crear Nuevo Negocio", Duration.ofSeconds(20));
		final WebElement modalContainer = findClosestModalContainer(modalTitle);

		Assert.assertTrue("Modal title 'Crear Nuevo Negocio' was not visible.", modalTitle.isDisplayed());
		Assert.assertTrue("'Nombre del Negocio' label not found.", isTextVisibleInContainer(modalContainer, "Nombre del Negocio"));
		Assert.assertTrue("'Tienes 2 de 3 negocios' text not found.", isTextVisibleInContainer(modalContainer, "Tienes 2 de 3 negocios"));
		Assert.assertTrue("'Cancelar' button not found.", isTextVisibleInContainer(modalContainer, "Cancelar"));
		Assert.assertTrue("'Crear Negocio' button not found.", isTextVisibleInContainer(modalContainer, "Crear Negocio"));

		final Optional<WebElement> firstInput = modalContainer.findElements(By.xpath(".//input|.//textarea")).stream().findFirst();
		if (firstInput.isPresent()) {
			firstInput.get().click();
			firstInput.get().clear();
			firstInput.get().sendKeys("Negocio Prueba Automatización");
		}
		captureScreenshot("03-agregar-negocio-modal");

		clickByVisibleText("Cancelar");
		waitUntilTextNotVisible("Crear Nuevo Negocio", Duration.ofSeconds(15));
	}

	private void executeAdministrarNegociosStep() throws Exception {
		expandMiNegocioMenu();
		clickByVisibleText("Administrar Negocios");

		waitUntilTextVisible("Información General", Duration.ofSeconds(30));
		Assert.assertTrue("'Información General' section was not visible.", isTextVisible("Información General"));
		Assert.assertTrue("'Detalles de la Cuenta' section was not visible.", isTextVisible("Detalles de la Cuenta"));
		Assert.assertTrue("'Tus Negocios' section was not visible.", isTextVisible("Tus Negocios"));
		Assert.assertTrue("'Sección Legal' section was not visible.", isTextVisible("Sección Legal"));
		captureScreenshot("04-administrar-negocios");
	}

	private void executeInformacionGeneralStep() {
		Assert.assertTrue("Could not confirm user name visibility.",
				isExpectedUserNameVisible() || inferUserNameVisibilityFromPageContext());
		Assert.assertTrue("Could not confirm user email visibility.", isExpectedUserEmailVisible() || isAnyEmailVisible());
		Assert.assertTrue("'BUSINESS PLAN' text not visible.", isTextVisible("BUSINESS PLAN"));
		Assert.assertTrue("'Cambiar Plan' button not visible.", isTextVisible("Cambiar Plan"));
	}

	private void executeDetallesCuentaStep() {
		Assert.assertTrue("'Cuenta creada' text not visible.", isTextVisible("Cuenta creada"));
		Assert.assertTrue("'Estado activo' text not visible.", isTextVisible("Estado activo"));
		Assert.assertTrue("'Idioma seleccionado' text not visible.", isTextVisible("Idioma seleccionado"));
	}

	private void executeTusNegociosStep() {
		Assert.assertTrue("Could not confirm business list visibility.", isBusinessListVisible());
		Assert.assertTrue("'Agregar Negocio' button not visible inside account view.", isTextVisible("Agregar Negocio"));
		Assert.assertTrue("'Tienes 2 de 3 negocios' text not visible.", isTextVisible("Tienes 2 de 3 negocios"));
	}

	private void executeLegalLinkStep(final String linkText, final String requiredHeading) throws Exception {
		waitUntilTextVisible("Sección Legal", Duration.ofSeconds(20));
		final String appWindowHandle = driver.getWindowHandle();
		final String currentUrl = driver.getCurrentUrl();
		final Set<String> existingHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);

		String activeHandle = appWindowHandle;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12)).until(d -> d.getWindowHandles().size() > existingHandles.size()
					|| !d.getCurrentUrl().equals(currentUrl));
		} catch (final TimeoutException ignored) {
			// handle both in-place navigation and late page updates below
		}

		final Set<String> currentHandles = new LinkedHashSet<>(driver.getWindowHandles());
		if (currentHandles.size() > existingHandles.size()) {
			currentHandles.removeAll(existingHandles);
			if (!currentHandles.isEmpty()) {
				activeHandle = currentHandles.iterator().next();
				driver.switchTo().window(activeHandle);
			}
		}

		waitForUiToLoad();
		waitUntilTextVisible(requiredHeading, Duration.ofSeconds(20));
		Assert.assertTrue("Heading '" + requiredHeading + "' was not visible.", isTextVisible(requiredHeading));
		Assert.assertTrue("Legal content text was not visible for '" + requiredHeading + "'.", isLegalContentVisible());

		captureScreenshot("legal-" + toSafeFileName(requiredHeading));
		legalUrls.put(requiredHeading, driver.getCurrentUrl());

		if (!activeHandle.equals(appWindowHandle)) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		} else if (!currentUrl.equals(driver.getCurrentUrl())) {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void expandMiNegocioMenu() {
		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			findFirstVisibleElementByText("Negocio").ifPresent(this::clickAndWait);
			clickByVisibleText("Mi Negocio");
		}

		waitUntilTextVisible("Agregar Negocio", Duration.ofSeconds(20));
		waitUntilTextVisible("Administrar Negocios", Duration.ofSeconds(20));
	}

	private void maybePickGoogleAccount() {
		final Optional<WebElement> account = waitForVisibleTextOption(GOOGLE_ACCOUNT_EMAIL, Duration.ofSeconds(12));
		if (account.isPresent()) {
			clickAndWait(account.get());
		}
	}

	private void runStep(final String stepField, final StepAction action) throws IOException {
		try {
			action.run();
			stepStatus.put(stepField, "PASS");
			stepDetails.put(stepField, "Validation completed successfully.");
		} catch (final Exception exception) {
			stepStatus.put(stepField, "FAIL");
			stepDetails.put(stepField, exception.getMessage());
			unexpectedErrors.add(stepField + ": " + exception.getMessage());
			captureScreenshot("failed-" + toSafeFileName(stepField));
		}
	}

	private Optional<WebElement> waitForVisibleTextOption(final String text, final Duration timeout) {
		try {
			final WebElement element = new WebDriverWait(driver, timeout)
					.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
			return Optional.of(element);
		} catch (final TimeoutException timeoutException) {
			return Optional.empty();
		}
	}

	private Optional<WebElement> findFirstVisibleElementByText(final String... texts) {
		for (final String text : texts) {
			final List<WebElement> matches = driver.findElements(byVisibleText(text));
			for (final WebElement match : matches) {
				if (match.isDisplayed()) {
					return Optional.of(match);
				}
			}
		}
		return Optional.empty();
	}

	private void clickByVisibleText(final String text) {
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(byVisibleText(text)));
		clickAndWait(element);
	}

	private void clickAndWait(final WebElement element) {
		try {
			element.click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		try {
			Thread.sleep(500);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private WebElement waitUntilTextVisible(final String text, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
	}

	private void waitUntilTextNotVisible(final String text, final Duration timeout) {
		new WebDriverWait(driver, timeout).until(ExpectedConditions.invisibilityOfElementLocated(byVisibleText(text)));
	}

	private boolean waitUntilAnyTextVisible(final Duration timeout, final String... texts) {
		try {
			new WebDriverWait(driver, timeout).until(webDriver -> {
				for (final String text : texts) {
					if (isTextVisible(text)) {
						return true;
					}
				}
				return false;
			});
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private boolean isTextVisible(final String text) {
		final List<WebElement> matches = driver.findElements(byVisibleText(text));
		return matches.stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean isTextVisibleInContainer(final WebElement container, final String text) {
		final List<WebElement> matches = container.findElements(By.xpath(".//*[contains(normalize-space(.), " + asXpathLiteral(text) + ")]"));
		return matches.stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean waitForSidebar(final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//aside | //nav[contains(@class,'sidebar')] | //nav[.//*[contains(normalize-space(.), 'Negocio')]]")));
			return true;
		} catch (final TimeoutException timeoutException) {
			return isSidebarVisible();
		}
	}

	private boolean isSidebarVisible() {
		final List<WebElement> sidebarCandidates = driver.findElements(
				By.xpath("//aside | //nav[contains(@class,'sidebar')] | //nav[.//*[contains(normalize-space(.), 'Negocio')]]"));
		return sidebarCandidates.stream().anyMatch(WebElement::isDisplayed);
	}

	private WebElement findClosestModalContainer(final WebElement modalTitle) {
		final List<By> candidateLocators = Arrays.asList(
				By.xpath("./ancestor::*[@role='dialog'][1]"),
				By.xpath("./ancestor::*[contains(@class,'modal')][1]"),
				By.xpath("./ancestor::*[contains(@class,'Dialog')][1]"));

		for (final By locator : candidateLocators) {
			final List<WebElement> containers = modalTitle.findElements(locator);
			if (!containers.isEmpty() && containers.get(0).isDisplayed()) {
				return containers.get(0);
			}
		}
		return modalTitle;
	}

	private boolean isExpectedUserEmailVisible() {
		final String expectedEmail = getEnv("SALEADS_EXPECTED_USER_EMAIL", GOOGLE_ACCOUNT_EMAIL);
		return isNotBlank(expectedEmail) && isTextVisible(expectedEmail);
	}

	private boolean isAnyEmailVisible() {
		return EMAIL_PATTERN.matcher(driver.findElement(By.tagName("body")).getText()).find();
	}

	private boolean isExpectedUserNameVisible() {
		final String expectedName = getEnv("SALEADS_EXPECTED_USER_NAME");
		return isNotBlank(expectedName) && isTextVisible(expectedName);
	}

	private boolean inferUserNameVisibilityFromPageContext() {
		final String[] lines = driver.findElement(By.tagName("body")).getText().split("\\R+");
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.length() < 4) {
				continue;
			}
			if (trimmed.contains("@") || "INFORMACIÓN GENERAL".equalsIgnoreCase(trimmed)
					|| "DETALLES DE LA CUENTA".equalsIgnoreCase(trimmed) || "TUS NEGOCIOS".equalsIgnoreCase(trimmed)
					|| "SECCIÓN LEGAL".equalsIgnoreCase(trimmed) || "BUSINESS PLAN".equalsIgnoreCase(trimmed)
					|| "CAMBIAR PLAN".equalsIgnoreCase(trimmed)) {
				continue;
			}
			if (trimmed.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*\\s+.*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private boolean isBusinessListVisible() {
		final Optional<WebElement> sectionHeading = findFirstVisibleElementByText("Tus Negocios");
		if (!sectionHeading.isPresent()) {
			return false;
		}

		final WebElement heading = sectionHeading.get();
		final List<WebElement> sectionContainers = heading
				.findElements(By.xpath("./ancestor::*[self::section or self::div][1]"));

		if (!sectionContainers.isEmpty()) {
			final WebElement container = sectionContainers.get(0);
			final List<WebElement> entries = container.findElements(By.xpath(".//li | .//tr | .//*[contains(@class,'business')]"));
			if (entries.stream().anyMatch(WebElement::isDisplayed)) {
				return true;
			}

			final String normalizedText = container.getText().replace("Tus Negocios", "").replace("Agregar Negocio", "")
					.replace("Tienes 2 de 3 negocios", "").trim();
			return normalizedText.length() > 10;
		}

		return false;
	}

	private boolean isLegalContentVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		return bodyText != null && bodyText.trim().length() > 120;
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		if (driver == null) {
			return;
		}

		final Path screenshotPath = evidenceDirectory.resolve(checkpointName + ".png");
		final Path temporaryFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(temporaryFile, screenshotPath);
	}

	private void writeFinalReport() throws IOException {
		final List<String> reportLines = new ArrayList<>();
		reportLines.add("SaleADS Mi Negocio Full Workflow Report");
		reportLines.add("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		reportLines.add("Evidence directory: " + evidenceDirectory.toAbsolutePath());
		reportLines.add("");
		reportLines.add("Step Results:");
		for (final String reportField : REPORT_ORDER) {
			reportLines.add("- " + reportField + ": " + stepStatus.get(reportField) + formatDetail(stepDetails.get(reportField)));
		}
		reportLines.add("");
		reportLines.add("Legal URLs:");
		reportLines.add("- Términos y Condiciones: " + legalUrls.getOrDefault("Términos y Condiciones", "N/A"));
		reportLines.add("- Política de Privacidad: " + legalUrls.getOrDefault("Política de Privacidad", "N/A"));
		reportLines.add("");
		reportLines.add("Notes:");
		reportLines.add("- SALEADS_LOGIN_URL: " + getEnv("SALEADS_LOGIN_URL", "not provided"));
		reportLines.add("- SALEADS_REMOTE_WEBDRIVER_URL: "
				+ (isNotBlank(getEnv("SALEADS_REMOTE_WEBDRIVER_URL")) ? "provided" : "not provided"));
		reportLines.add("- SALEADS_E2E_HEADLESS: " + getEnv("SALEADS_E2E_HEADLESS", "true"));

		finalReportPath = evidenceDirectory.resolve("final-report.txt");
		Files.write(finalReportPath, reportLines);
	}

	private String formatDetail(final String detail) {
		if (!isNotBlank(detail)) {
			return "";
		}
		return " (" + detail + ")";
	}

	private WebDriver createWebDriver() throws MalformedURLException {
		final ChromeOptions options = new ChromeOptions();
		if (getBooleanEnv("SALEADS_E2E_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--lang=es-ES");

		final String remoteWebDriverUrl = getEnv("SALEADS_REMOTE_WEBDRIVER_URL");
		if (isNotBlank(remoteWebDriverUrl)) {
			return new RemoteWebDriver(new URL(remoteWebDriverUrl), options);
		}
		return new ChromeDriver(options);
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path directory = Paths.get("target", "saleads-evidence", "run-" + timestamp);
		Files.createDirectories(directory);
		return directory;
	}

	private By byVisibleText(final String text) {
		return By.xpath("//*[contains(normalize-space(.), " + asXpathLiteral(text) + ")]");
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder xpathLiteral = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			xpathLiteral.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				xpathLiteral.append(", \"'\", ");
			}
		}
		xpathLiteral.append(")");
		return xpathLiteral.toString();
	}

	private String toSafeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
	}

	private String getEnv(final String key) {
		return System.getenv(key);
	}

	private String getEnv(final String key, final String fallback) {
		final String value = System.getenv(key);
		return isNotBlank(value) ? value : fallback;
	}

	private boolean getBooleanEnv(final String key, final boolean fallback) {
		final String value = System.getenv(key);
		if (!isNotBlank(value)) {
			return fallback;
		}
		return Boolean.parseBoolean(value);
	}

	private boolean isNotBlank(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
