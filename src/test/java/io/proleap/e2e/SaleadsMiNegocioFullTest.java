package io.proleap.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
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

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, StepOutcome> report = new LinkedHashMap<>();
	private final Path artifactsDir = Paths.get("target", "saleads-e2e");
	private final Path screenshotsDir = artifactsDir.resolve("screenshots");

	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws Exception {
		Files.createDirectories(screenshotsDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,2200");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		final String headless = System.getenv("SALEADS_HEADLESS");
		if (headless == null || Boolean.parseBoolean(headless)) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = System.getenv("SELENIUM_REMOTE_URL");
		if (remoteUrl != null && !remoteUrl.isBlank()) {
			driver = new RemoteWebDriver(new URL(remoteUrl), options);
		} else {
			driver = new ChromeDriver(options);
		}

		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		initializeReport();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runValidation("Login", this::validateLoginWithGoogle);
		runValidation("Mi Negocio menu", this::validateMiNegocioMenu);
		runValidation("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runValidation("Administrar Negocios view", this::validateAdministrarNegociosView);
		runValidation("Información General", this::validateInformacionGeneral);
		runValidation("Detalles de la Cuenta", this::validateDetallesCuenta);
		runValidation("Tus Negocios", this::validateTusNegocios);
		runValidation("Términos y Condiciones", this::validateTerminosCondiciones);
		runValidation("Política de Privacidad", this::validatePoliticaPrivacidad);

		final String finalReport = buildFinalReport();
		writeFinalReport(finalReport);
		System.out.println(finalReport);

		final boolean allPassed = report.values().stream().allMatch(step -> step.passed);
		assertTrue("One or more validations failed.\n\n" + finalReport, allPassed);
	}

	private String validateLoginWithGoogle() throws Exception {
		navigateToLoginPageIfProvided();

		final String appWindow = driver.getWindowHandle();
		final int handlesBeforeClick = driver.getWindowHandles().size();

		clickGoogleLoginButton();
		selectGoogleAccountIfPrompted(appWindow, handlesBeforeClick);

		waitForAnyVisible(Duration.ofSeconds(45), Arrays.asList(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(normalize-space(), 'Negocio')]"),
				By.xpath("//*[contains(normalize-space(), 'Mi Negocio')]")));

		final String screenshot = captureScreenshot("01-dashboard-loaded");
		return "Dashboard loaded; sidebar visible; screenshot=" + screenshot;
	}

	private String validateMiNegocioMenu() throws Exception {
		waitVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");
		waitVisibleText("Agregar Negocio");
		waitVisibleText("Administrar Negocios");

		final String screenshot = captureScreenshot("02-mi-negocio-menu-expanded");
		return "Menu expanded with both submenu options; screenshot=" + screenshot;
	}

	private String validateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitVisibleText("Crear Nuevo Negocio");
		waitVisibleText("Nombre del Negocio");
		waitVisibleText("Tienes 2 de 3 negocios");
		waitVisibleText("Cancelar");
		waitVisibleText("Crear Negocio");

		final String screenshot = captureScreenshot("03-agregar-negocio-modal");

		final WebElement input = findFirstVisible(Arrays.asList(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//*[normalize-space()='Nombre del Negocio']/following::input[1]")));
		clickElement(input);
		input.clear();
		input.sendKeys("Negocio Prueba Automatización");
		waitForUiLoad();

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[normalize-space()='Crear Nuevo Negocio']")));

		return "Modal validated and closed by Cancelar; screenshot=" + screenshot;
	}

	private String validateAdministrarNegociosView() throws Exception {
		if (!isVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"), Duration.ofSeconds(4))) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitVisibleText("Información General");
		waitVisibleText("Detalles de la Cuenta");
		waitVisibleText("Tus Negocios");
		waitVisibleText("Sección Legal");

		final String screenshot = captureScreenshot("04-administrar-negocios");
		return "Account page sections visible; screenshot=" + screenshot;
	}

	private String validateInformacionGeneral() {
		waitVisibleText("BUSINESS PLAN");
		waitVisibleText("Cambiar Plan");

		final String sectionText = extractSectionText("Información General");
		final boolean hasEmail = sectionText.matches("(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*");
		if (!hasEmail) {
			throw new AssertionError("No visible email found in Información General.");
		}

		boolean hasNameLikeText = false;
		for (final String rawLine : sectionText.split("\\R")) {
			final String line = rawLine.trim();
			if (line.isEmpty() || line.contains("@") || line.equalsIgnoreCase("Información General")
					|| line.contains("BUSINESS PLAN") || line.contains("Cambiar Plan")) {
				continue;
			}
			if (line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				hasNameLikeText = true;
				break;
			}
		}

		if (!hasNameLikeText) {
			throw new AssertionError("No user name-like text found in Información General.");
		}

		return "User name and email visible; BUSINESS PLAN and Cambiar Plan visible.";
	}

	private String validateDetallesCuenta() {
		waitVisibleText("Cuenta creada");
		waitVisibleText("Estado activo");
		waitVisibleText("Idioma seleccionado");
		return "Cuenta creada, Estado activo and Idioma seleccionado are visible.";
	}

	private String validateTusNegocios() {
		waitVisibleText("Tus Negocios");
		waitVisibleText("Agregar Negocio");
		waitVisibleText("Tienes 2 de 3 negocios");

		final String sectionText = extractSectionText("Tus Negocios");
		long contentLines = Arrays.stream(sectionText.split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isEmpty()
						&& !line.equalsIgnoreCase("Tus Negocios")
						&& !line.contains("Agregar Negocio")
						&& !line.contains("Tienes 2 de 3 negocios"))
				.count();

		if (contentLines < 1) {
			throw new AssertionError("Business list content is not visible in Tus Negocios.");
		}

		return "Business list and usage quota are visible.";
	}

	private String validateTerminosCondiciones() throws Exception {
		final String finalUrl = openLegalDocumentAndValidate("Términos y Condiciones", "Términos y Condiciones",
				"05-terminos-condiciones");
		return "Legal page validated; final_url=" + finalUrl;
	}

	private String validatePoliticaPrivacidad() throws Exception {
		final String finalUrl = openLegalDocumentAndValidate("Política de Privacidad", "Política de Privacidad",
				"06-politica-privacidad");
		return "Legal page validated; final_url=" + finalUrl;
	}

	private String openLegalDocumentAndValidate(final String linkText, final String headingText, final String screenshotName)
			throws Exception {
		final String appHandle = driver.getWindowHandle();
		final String appUrlBefore = driver.getCurrentUrl();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText(linkText);

		String activeHandle = appHandle;
		boolean openedNewTab = false;
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(12).toMillis();
		while (System.currentTimeMillis() < deadline) {
			if (driver.getWindowHandles().size() > handlesBefore.size()) {
				openedNewTab = true;
				break;
			}
			if (!safeCurrentUrl().equals(appUrlBefore)) {
				break;
			}
			sleep(250);
		}

		if (openedNewTab) {
			final Set<String> afterHandles = driver.getWindowHandles();
			for (final String handle : afterHandles) {
				if (!handlesBefore.contains(handle)) {
					activeHandle = handle;
					break;
				}
			}
			driver.switchTo().window(activeHandle);
			waitForUiLoad();
		}

		waitVisibleText(headingText);
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		if (bodyText.trim().length() < 120) {
			throw new AssertionError("Legal content text appears too short for " + headingText + ".");
		}

		final String screenshot = captureScreenshot(screenshotName);
		final String finalUrl = safeCurrentUrl();
		System.out.println("Validated " + headingText + " at URL: " + finalUrl + " | screenshot=" + screenshot);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiLoad();
		waitForAnyVisible(Duration.ofSeconds(20), Arrays.asList(
				By.xpath("//*[normalize-space()='Sección Legal']"),
				By.xpath("//*[normalize-space()='Información General']"),
				By.xpath("//*[normalize-space()='Tus Negocios']")));

		return finalUrl;
	}

	private void runValidation(final String field, final StepAction action) {
		try {
			final String details = action.run();
			report.put(field, new StepOutcome(true, details));
		} catch (final Throwable ex) {
			final String screenshot = safeCapture("error-" + sanitize(field));
			final String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
			report.put(field, new StepOutcome(false, message + " | screenshot=" + screenshot));
		}
	}

	private void initializeReport() {
		final List<String> orderedFields = Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad");
		for (final String field : orderedFields) {
			report.put(field, new StepOutcome(false, "NOT_EXECUTED"));
		}
	}

	private String buildFinalReport() {
		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		sb.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		sb.append(System.lineSeparator());
		for (final Map.Entry<String, StepOutcome> entry : report.entrySet()) {
			final String status = entry.getValue().passed ? "PASS" : "FAIL";
			sb.append("- ").append(entry.getKey()).append(": ").append(status);
			if (entry.getValue().details != null && !entry.getValue().details.isBlank()) {
				sb.append(" | ").append(entry.getValue().details);
			}
			sb.append(System.lineSeparator());
		}
		return sb.toString();
	}

	private void writeFinalReport(final String content) throws IOException {
		Files.createDirectories(artifactsDir);
		final Path reportPath = artifactsDir.resolve("final-report.txt");
		Files.writeString(reportPath, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
	}

	private void navigateToLoginPageIfProvided() {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiLoad();
			return;
		}

		final String currentUrl = safeCurrentUrl();
		if ("data:,".equals(currentUrl) || "about:blank".equals(currentUrl)) {
			throw new IllegalStateException(
					"Browser is blank. Provide SALEADS_LOGIN_URL or pre-open the SaleADS login page.");
		}
	}

	private void clickGoogleLoginButton() {
		final List<By> googleLocators = Arrays.asList(
				By.xpath("//button[contains(normalize-space(), 'Google')]"),
				By.xpath("//a[contains(normalize-space(), 'Google')]"),
				By.xpath("//*[@role='button' and contains(normalize-space(), 'Google')]"),
				By.xpath("//*[contains(normalize-space(), 'Sign in with Google')]"),
				By.xpath("//*[contains(normalize-space(), 'Iniciar sesión con Google')]"));
		clickFirstAvailable(googleLocators, Duration.ofSeconds(20));
	}

	private void selectGoogleAccountIfPrompted(final String appWindow, final int handlesBeforeClick) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
		while (System.currentTimeMillis() < deadline) {
			if (driver.getWindowHandles().size() > handlesBeforeClick) {
				break;
			}
			if (safeCurrentUrl().contains("accounts.google.com")) {
				break;
			}
			sleep(300);
		}

		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() > handlesBeforeClick) {
			String popupHandle = null;
			for (final String handle : handles) {
				if (!handle.equals(appWindow)) {
					popupHandle = handle;
					break;
				}
			}
			if (popupHandle != null) {
				driver.switchTo().window(popupHandle);
				waitForUiLoad();
				selectSpecificGoogleAccountIfVisible();

				final long closeDeadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
				while (System.currentTimeMillis() < closeDeadline && driver.getWindowHandles().contains(popupHandle)) {
					sleep(250);
				}

				driver.switchTo().window(appWindow);
				waitForUiLoad();
			}
		} else if (safeCurrentUrl().contains("accounts.google.com")) {
			selectSpecificGoogleAccountIfVisible();
			waitForUiLoad();
		}
	}

	private void selectSpecificGoogleAccountIfVisible() {
		final By accountBy = By.xpath("//*[contains(normalize-space(), " + xpathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]");
		if (isVisible(accountBy, Duration.ofSeconds(8))) {
			clickElement(waitVisible(accountBy));
		}
	}

	private void clickByVisibleText(final String text) {
		final String exact = xpathLiteral(text);
		final String contains = xpathLiteral(text);

		final List<By> candidates = Arrays.asList(
				By.xpath("//button[normalize-space()=" + exact + "]"),
				By.xpath("//a[normalize-space()=" + exact + "]"),
				By.xpath("//*[@role='button' and normalize-space()=" + exact + "]"),
				By.xpath("//*[normalize-space()=" + exact + "]"),
				By.xpath("//button[contains(normalize-space(), " + contains + ")]"),
				By.xpath("//a[contains(normalize-space(), " + contains + ")]"),
				By.xpath("//*[@role='button' and contains(normalize-space(), " + contains + ")]"),
				By.xpath("//*[contains(normalize-space(), " + contains + ")]"));

		clickFirstAvailable(candidates, Duration.ofSeconds(20));
	}

	private void clickFirstAvailable(final List<By> locators, final Duration timeout) {
		Exception lastError = null;
		for (final By locator : locators) {
			try {
				final WebElement element = new WebDriverWait(driver, timeout)
						.until(ExpectedConditions.elementToBeClickable(locator));
				clickElement(element);
				return;
			} catch (final Exception ex) {
				lastError = ex;
			}
		}

		throw new NoSuchElementException("Could not click any expected element. Last error: "
				+ (lastError == null ? "none" : lastError.getMessage()));
	}

	private WebElement findFirstVisible(final List<By> locators) {
		for (final By locator : locators) {
			if (isVisible(locator, Duration.ofSeconds(4))) {
				return waitVisible(locator);
			}
		}
		throw new NoSuchElementException("No visible element found for provided locators.");
	}

	private void clickElement(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		element.click();
		waitForUiLoad();
	}

	private void waitVisibleText(final String text) {
		waitVisible(By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]"));
	}

	private WebElement waitVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void waitForAnyVisible(final Duration timeout, final List<By> locators) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final By locator : locators) {
				if (isVisible(locator, Duration.ofSeconds(1))) {
					return;
				}
			}
			sleep(200);
		}

		throw new TimeoutException("None of the expected elements became visible in time.");
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private String extractSectionText(final String headingText) {
		final WebElement heading = waitVisible(By.xpath("//*[normalize-space()=" + xpathLiteral(headingText) + "]"));
		final List<By> sectionCandidates = new ArrayList<>();
		sectionCandidates.add(By.xpath(
				"./ancestor::*[self::section or self::article or self::div][.//*[normalize-space()="
						+ xpathLiteral(headingText) + "]][1]"));
		sectionCandidates.add(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));

		for (final By by : sectionCandidates) {
			try {
				final WebElement section = heading.findElement(by);
				final String text = section.getText();
				if (text != null && !text.isBlank()) {
					return text;
				}
			} catch (final Exception ignored) {
				// try next candidate
			}
		}

		return driver.findElement(By.tagName("body")).getText();
	}

	private void waitForUiLoad() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15)).until(webDriver -> {
				final Object readyState = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
				return readyState != null && "complete".equals(readyState.toString());
			});
		} catch (final Exception ignored) {
			// Some cross-origin pages do not allow reliable readyState checks.
		}
		sleep(500);
	}

	private String captureScreenshot(final String checkpointName) throws IOException {
		final String fileName = TS_FORMAT.format(LocalDateTime.now()) + "-" + sanitize(checkpointName) + ".png";
		final Path target = screenshotsDir.resolve(fileName);
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		return target.toString();
	}

	private String safeCapture(final String checkpointName) {
		try {
			return captureScreenshot(checkpointName);
		} catch (final Exception ignored) {
			return "screenshot_unavailable";
		}
	}

	private String safeCurrentUrl() {
		try {
			final String url = driver.getCurrentUrl();
			return url == null ? "" : url;
		} catch (final Exception ignored) {
			return "";
		}
	}

	private static String xpathLiteral(final String value) {
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

	private static String sanitize(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		String run() throws Exception;
	}

	private static final class StepOutcome {
		private final boolean passed;
		private final String details;

		private StepOutcome(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}
	}
}
