package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
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

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(4);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");

	private static final String FIELD_LOGIN = "Login";
	private static final String FIELD_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String FIELD_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String FIELD_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String FIELD_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String FIELD_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String FIELD_TUS_NEGOCIOS = "Tus Negocios";
	private static final String FIELD_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String FIELD_POLITICA = "Pol\u00edtica de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private String loginUrl;
	private String googleAccountEmail;
	private Path evidenceDirectory;

	private final Map<String, StepOutcome> outcomes = new LinkedHashMap<>();
	private final List<String> legalUrls = new ArrayList<>();

	@Before
	public void setUp() throws IOException {
		final boolean e2eEnabled = Boolean.parseBoolean(
				readConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"Skipping SaleADS E2E test. Enable with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true.",
				e2eEnabled);

		loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", "");
		Assume.assumeTrue(
				"Skipping SaleADS E2E test. Provide login URL with -Dsaleads.login.url=<url> or SALEADS_LOGIN_URL.",
				!loginUrl.isBlank());

		googleAccountEmail = readConfig("saleads.google.account.email", "SALEADS_GOOGLE_ACCOUNT_EMAIL",
				"juanlucasbarbiergarzon@gmail.com");

		final boolean headless = Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"));
		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1600,2000", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		evidenceDirectory = Paths.get("target", "saleads-e2e-evidence", timestamp);
		Files.createDirectories(evidenceDirectory);

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		printFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep(FIELD_LOGIN, this::performLoginStep);
		runStep(FIELD_MI_NEGOCIO_MENU, this::openMiNegocioMenuStep);
		runStep(FIELD_AGREGAR_NEGOCIO_MODAL, this::validateAgregarNegocioModalStep);
		runStep(FIELD_ADMINISTRAR_NEGOCIOS, this::openAdministrarNegociosStep);
		runStep(FIELD_INFO_GENERAL, this::validateInformacionGeneralStep);
		runStep(FIELD_DETALLES_CUENTA, this::validateDetallesCuentaStep);
		runStep(FIELD_TUS_NEGOCIOS, this::validateTusNegociosStep);
		runStep(FIELD_TERMINOS, () -> validateLegalLinkStep("T\u00e9rminos y Condiciones", "T\u00e9rminos y Condiciones",
				"terminos-y-condiciones"));
		runStep(FIELD_POLITICA, () -> validateLegalLinkStep("Pol\u00edtica de Privacidad", "Pol\u00edtica de Privacidad",
				"politica-de-privacidad"));

		final List<String> failedFields = outcomes.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(Map.Entry::getKey).collect(Collectors.toList());
		assertTrue("One or more workflow steps failed: " + failedFields, failedFields.isEmpty());
	}

	private void performLoginStep() throws Exception {
		clickByAnyVisibleText(List.of("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google",
				"Acceder con Google", "Google"));
		waitForUiToLoad();
		selectGoogleAccountIfPrompted();

		assertTrue("Main application interface did not load.",
				isAnyTextVisible(List.of("Negocio", "Mi Negocio", "Dashboard"), Duration.ofSeconds(60)));
		assertTrue("Left sidebar navigation is not visible.", isSidebarVisible());
		captureScreenshot("01-dashboard-loaded", false);
	}

	private void openMiNegocioMenuStep() throws Exception {
		expandMiNegocioMenu();

		assertTrue("'Agregar Negocio' is not visible.", isTextVisible("Agregar Negocio", DEFAULT_TIMEOUT));
		assertTrue("'Administrar Negocios' is not visible.", isTextVisible("Administrar Negocios", DEFAULT_TIMEOUT));
		captureScreenshot("02-mi-negocio-menu-expanded", false);
	}

	private void validateAgregarNegocioModalStep() throws Exception {
		clickByAnyVisibleText(List.of("Agregar Negocio"));
		assertTrue("Modal title 'Crear Nuevo Negocio' is missing.",
				isTextVisible("Crear Nuevo Negocio", DEFAULT_TIMEOUT));

		final By businessNameInput = By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')] | //input[contains(@aria-label, 'Nombre del Negocio')]");
		final WebElement input = findVisibleElement(businessNameInput, DEFAULT_TIMEOUT)
				.orElseThrow(() -> new AssertionError("Input field 'Nombre del Negocio' was not found."));
		assertTrue("'Tienes 2 de 3 negocios' text not found.", isTextVisible("Tienes 2 de 3 negocios", DEFAULT_TIMEOUT));
		assertTrue("'Cancelar' button not present.", isTextVisible("Cancelar", DEFAULT_TIMEOUT));
		assertTrue("'Crear Negocio' button not present.", isTextVisible("Crear Negocio", DEFAULT_TIMEOUT));
		captureScreenshot("03-crear-negocio-modal", false);

		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatizacion");
		clickByAnyVisibleText(List.of("Cancelar"));
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[self::h1 or self::h2 or self::div][contains(normalize-space(.), 'Crear Nuevo Negocio')]")));
		waitForUiToLoad();
	}

	private void openAdministrarNegociosStep() throws Exception {
		if (!isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			expandMiNegocioMenu();
		}

		clickByAnyVisibleText(List.of("Administrar Negocios"));
		waitForUiToLoad();

		assertTrue("'Informacion General' section missing.", isTextVisible("Informaci\u00f3n General", DEFAULT_TIMEOUT));
		assertTrue("'Detalles de la Cuenta' section missing.",
				isTextVisible("Detalles de la Cuenta", DEFAULT_TIMEOUT));
		assertTrue("'Tus Negocios' section missing.", isTextVisible("Tus Negocios", DEFAULT_TIMEOUT));
		assertTrue("'Seccion Legal' section missing.", isTextVisible("Secci\u00f3n Legal", DEFAULT_TIMEOUT));
		captureScreenshot("04-administrar-negocios-page", true);
	}

	private void validateInformacionGeneralStep() throws Exception {
		final WebElement section = findSectionByTitle("Informaci\u00f3n General");
		final String sectionText = section.getText();
		assertTrue("User email is not visible in 'Informacion General'.", EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("User name is not visible in 'Informacion General'.", containsLikelyUserName(sectionText));
		assertTrue("'BUSINESS PLAN' text missing in 'Informacion General'.",
				containsIgnoreCase(sectionText, "BUSINESS PLAN"));
		assertTrue("'Cambiar Plan' button missing in 'Informacion General'.", isTextVisibleInside(section, "Cambiar Plan"));
	}

	private void validateDetallesCuentaStep() throws Exception {
		final WebElement section = findSectionByTitle("Detalles de la Cuenta");
		final String sectionText = section.getText();
		assertTrue("'Cuenta creada' is not visible.", containsIgnoreCase(sectionText, "Cuenta creada"));
		assertTrue("'Estado activo' is not visible.",
				containsIgnoreCase(sectionText, "Estado activo") || containsIgnoreCase(sectionText, "Activo"));
		assertTrue("'Idioma seleccionado' is not visible.", containsIgnoreCase(sectionText, "Idioma seleccionado"));
	}

	private void validateTusNegociosStep() throws Exception {
		final WebElement section = findSectionByTitle("Tus Negocios");
		final String sectionText = section.getText();
		assertTrue("Business list is not visible.", section.findElements(By.xpath(".//li | .//table | .//div")).size() > 0);
		assertTrue("'Agregar Negocio' button missing in 'Tus Negocios'.", isTextVisibleInside(section, "Agregar Negocio"));
		assertTrue("'Tienes 2 de 3 negocios' text missing in 'Tus Negocios'.",
				containsIgnoreCase(sectionText, "Tienes 2 de 3 negocios"));
	}

	private void validateLegalLinkStep(final String linkText, final String expectedHeading, final String screenshotName)
			throws Exception {
		final String appHandle = driver.getWindowHandle();
		final String initialUrl = driver.getCurrentUrl();
		final Set<String> existingHandles = driver.getWindowHandles();

		clickByAnyVisibleText(List.of(linkText));

		final String destinationHandle = waitForNewWindowOrNavigation(existingHandles, initialUrl);
		if (destinationHandle != null && !destinationHandle.equals(driver.getWindowHandle())) {
			driver.switchTo().window(destinationHandle);
		}
		waitForUiToLoad();

		assertTrue("Expected legal heading '" + expectedHeading + "' not found.",
				isTextVisible(expectedHeading, DEFAULT_TIMEOUT));

		final String legalText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal content text is not visible.", legalText.trim().length() > 120);

		captureScreenshot("05-" + screenshotName, false);
		legalUrls.add(linkText + " -> " + driver.getCurrentUrl());

		if (!driver.getWindowHandle().equals(appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();
	}

	private void expandMiNegocioMenu() throws Exception {
		if (!isSidebarVisible()) {
			throw new AssertionError("Sidebar is not visible.");
		}

		if (!isTextVisible("Mi Negocio", SHORT_TIMEOUT) && isTextVisible("Negocio", SHORT_TIMEOUT)) {
			clickByAnyVisibleText(List.of("Negocio"));
		}

		if (!isTextVisible("Agregar Negocio", SHORT_TIMEOUT) || !isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			clickByAnyVisibleText(List.of("Mi Negocio"));
		}

		if (!isTextVisible("Agregar Negocio", SHORT_TIMEOUT) || !isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			clickByAnyVisibleText(List.of("Mi Negocio"));
		}
	}

	private void selectGoogleAccountIfPrompted() {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(18));
			shortWait.until(webDriver -> webDriver.getWindowHandles().size() > 0);

			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);

				final Optional<WebElement> accountOption = findVisibleElement(
						By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(googleAccountEmail) + ")]"),
						Duration.ofSeconds(3));
				if (accountOption.isPresent()) {
					clickElementAndWait(accountOption.get());
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			// Google account selector is optional in some environments.
		} finally {
			if (driver.getWindowHandles().size() > 1) {
				for (final String handle : driver.getWindowHandles()) {
					driver.switchTo().window(handle);
					final String currentUrl = Optional.ofNullable(driver.getCurrentUrl()).orElse("");
					if (!currentUrl.contains("accounts.google.com")) {
						return;
					}
				}
			}
		}
	}

	private String waitForNewWindowOrNavigation(final Set<String> existingHandles, final String initialUrl) {
		final WebDriverWait mediumWait = new WebDriverWait(driver, Duration.ofSeconds(20));
		return mediumWait.until(webDriver -> {
			final Set<String> updatedHandles = webDriver.getWindowHandles();
			for (final String handle : updatedHandles) {
				if (!existingHandles.contains(handle)) {
					return handle;
				}
			}

			final String currentUrl = Optional.ofNullable(webDriver.getCurrentUrl()).orElse("");
			if (!currentUrl.isBlank() && !currentUrl.equals(initialUrl)) {
				return webDriver.getWindowHandle();
			}

			return null;
		});
	}

	private boolean isSidebarVisible() {
		return findVisibleElement(
				By.xpath("//aside | //nav[contains(@class, 'sidebar')] | //div[contains(@class, 'sidebar')]"),
				Duration.ofSeconds(8)).isPresent() || isAnyTextVisible(List.of("Negocio", "Mi Negocio"), Duration.ofSeconds(8));
	}

	private boolean containsLikelyUserName(final String text) {
		final List<String> ignoredLabels = List.of("informacion general", "business plan", "cambiar plan", "cuenta",
				"estado", "idioma", "tus negocios", "seccion legal");
		for (final String rawLine : text.split("\\R")) {
			final String line = rawLine.trim();
			if (line.length() < 3 || line.contains("@") || EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}

			final String normalized = line.toLowerCase();
			if (ignoredLabels.stream().anyMatch(normalized::contains)) {
				continue;
			}

			if (line.matches(".*\\p{L}.*")) {
				return true;
			}
		}
		return false;
	}

	private WebElement findSectionByTitle(final String title) {
		final By locator = By.xpath(
				"//*[self::h1 or self::h2 or self::h3 or self::h4 or self::div][contains(normalize-space(.), "
						+ xpathLiteral(title)
						+ ")]/ancestor::*[self::section or self::article or self::div][1]");
		return findVisibleElement(locator, DEFAULT_TIMEOUT)
				.orElseThrow(() -> new AssertionError("Section with title '" + title + "' was not found."));
	}

	private void clickByAnyVisibleText(final List<String> textCandidates) throws Exception {
		for (final String textCandidate : textCandidates) {
			final Optional<WebElement> exactMatch = findClickableByText(textCandidate, true);
			if (exactMatch.isPresent()) {
				clickElementAndWait(exactMatch.get());
				return;
			}

			final Optional<WebElement> containsMatch = findClickableByText(textCandidate, false);
			if (containsMatch.isPresent()) {
				clickElementAndWait(containsMatch.get());
				return;
			}
		}
		throw new AssertionError("No clickable element found by visible text: " + textCandidates);
	}

	private Optional<WebElement> findClickableByText(final String text, final boolean exactMatch) {
		final String matcher = exactMatch ? "normalize-space(.) = " + xpathLiteral(text)
				: "contains(normalize-space(.), " + xpathLiteral(text) + ")";
		final By locator = By.xpath("//button[" + matcher + "] | //a[" + matcher + "] | //*[@role='button' and " + matcher
				+ "] | //*[self::span and " + matcher + "]");
		return findVisibleElement(locator, Duration.ofSeconds(5));
	}

	private Optional<WebElement> findVisibleElement(final By locator, final Duration timeout) {
		try {
			final WebDriverWait customWait = new WebDriverWait(driver, timeout);
			return Optional.of(customWait.until(webDriver -> {
				final List<WebElement> elements = webDriver.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
				return null;
			}));
		} catch (final TimeoutException ex) {
			return Optional.empty();
		}
	}

	private void clickElementAndWait(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private boolean isAnyTextVisible(final List<String> textCandidates, final Duration timeout) {
		for (final String text : textCandidates) {
			if (isTextVisible(text, timeout)) {
				return true;
			}
		}
		return false;
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		final By locator = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
		return findVisibleElement(locator, timeout).isPresent();
	}

	private boolean isTextVisibleInside(final WebElement container, final String text) {
		try {
			return container.findElements(By.xpath(".//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]")).stream()
					.anyMatch(WebElement::isDisplayed);
		} catch (final NoSuchElementException e) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		try {
			wait.until(webDriver -> "complete"
					.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		} catch (final TimeoutException ignored) {
			// Some SPAs keep loading resources in background; continue after timeout.
		}
		try {
			Thread.sleep(700);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String checkpointName, final boolean fullPage) throws IOException {
		Dimension originalSize = null;
		try {
			if (fullPage) {
				originalSize = driver.manage().window().getSize();
				final Long requiredWidth = convertToLong(((JavascriptExecutor) driver)
						.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);"));
				final Long requiredHeight = convertToLong(((JavascriptExecutor) driver).executeScript(
						"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);"));
				driver.manage().window()
						.setSize(new Dimension(requiredWidth.intValue() + 120, Math.min(requiredHeight.intValue() + 120, 9000)));
				waitForUiToLoad();
			}

			final byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			final Path screenshotPath = evidenceDirectory.resolve(checkpointName + ".png");
			Files.write(screenshotPath, screenshotBytes);
		} finally {
			if (fullPage && originalSize != null) {
				driver.manage().window().setSize(originalSize);
				waitForUiToLoad();
			}
		}
	}

	private Long convertToLong(final Object value) {
		if (value instanceof Long) {
			return (Long) value;
		}
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		return Long.valueOf(String.valueOf(value));
	}

	private String readConfig(final String systemPropertyName, final String envName, final String defaultValue) {
		final String systemValue = System.getProperty(systemPropertyName);
		if (systemValue != null && !systemValue.isBlank()) {
			return systemValue;
		}
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return defaultValue;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private boolean containsIgnoreCase(final String text, final String expected) {
		return text.toLowerCase().contains(expected.toLowerCase());
	}

	private void runStep(final String fieldName, final StepRunnable stepRunnable) {
		try {
			stepRunnable.run();
			outcomes.put(fieldName, StepOutcome.pass());
		} catch (final Exception | AssertionError stepError) {
			outcomes.put(fieldName, StepOutcome.fail(stepError.getMessage()));
		}
	}

	private void printFinalReport() {
		if (outcomes.isEmpty()) {
			return;
		}

		System.out.println("========== SaleADS Mi Negocio Workflow Report ==========");
		for (final Map.Entry<String, StepOutcome> entry : outcomes.entrySet()) {
			final String status = entry.getValue().passed ? "PASS" : "FAIL";
			final String detail = entry.getValue().detail == null ? "" : " | " + entry.getValue().detail;
			System.out.println(entry.getKey() + ": " + status + detail);
		}

		if (!legalUrls.isEmpty()) {
			System.out.println("---------- Legal URLs ----------");
			for (final String legalUrl : legalUrls) {
				System.out.println(legalUrl);
			}
		}

		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
		System.out.println("========================================================");
	}

	@FunctionalInterface
	private interface StepRunnable {
		void run() throws Exception;
	}

	private static final class StepOutcome {
		private final boolean passed;
		private final String detail;

		private StepOutcome(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}

		private static StepOutcome pass() {
			return new StepOutcome(true, null);
		}

		private static StepOutcome fail(final String detail) {
			return new StepOutcome(false, detail);
		}
	}
}
