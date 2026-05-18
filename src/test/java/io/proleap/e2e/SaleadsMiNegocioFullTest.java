package io.proleap.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(6);
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String TERMS_LABEL = "T\u00E9rminos y Condiciones";
	private static final String PRIVACY_LABEL = "Pol\u00EDtica de Privacidad";
	private static final String INFO_GENERAL = "Informaci\u00F3n General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";

	private static final String XPATH_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\u00C1\u00C9\u00CD\u00D3\u00DA\u00DC\u00D1";
	private static final String XPATH_LOWER = "abcdefghijklmnopqrstuvwxyz\u00E1\u00E9\u00ED\u00F3\u00FA\u00FC\u00F1";

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Informaci\u00F3n General", "Detalles de la Cuenta", "Tus Negocios",
			"T\u00E9rminos y Condiciones", "Pol\u00EDtica de Privacidad");

	private final LinkedHashMap<String, String> report = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> failures = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String appWindowHandle;
	private String termsFinalUrl;
	private String privacyFinalUrl;

	@Before
	public void setUp() throws Exception {
		initializeReport();
		screenshotDir = Paths.get("target", "surefire-reports", "saleads-mi-negocio-" + Instant.now().toEpochMilli());
		Files.createDirectories(screenshotDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		if (Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "false"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String startUrl = System.getenv("SALEADS_START_URL");
		if (startUrl != null && !startUrl.isBlank()) {
			driver.get(startUrl);
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLogin);
		runStep("Mi Negocio menu", this::stepMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepAdministrarNegocios);
		runStep("Informaci\u00F3n General", this::stepInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepDetallesCuenta);
		runStep("Tus Negocios", this::stepTusNegocios);
		runStep("T\u00E9rminos y Condiciones", this::stepTerminos);
		runStep("Pol\u00EDtica de Privacidad", this::stepPrivacidad);

		printFinalReport();
		assertTrue("SaleADS Mi Negocio workflow failed.\n" + formatFailures(), failures.isEmpty());
	}

	private void stepLogin() throws Exception {
		if (!isSidebarVisible()) {
			final WebElement loginButton = waitForClickableByTexts("Sign in with Google", "Iniciar sesi\u00F3n con Google",
					"Ingresar con Google", "Google");
			clickAndWait(loginButton);
			selectGoogleAccountIfVisible();
		}

		waitForMainInterface();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepMiNegocioMenu() throws Exception {
		switchToAppWindow();
		clickIfVisibleByTexts("Negocio");
		clickByTexts("Mi Negocio");
		waitForUiLoad();

		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepAgregarNegocioModal() throws Exception {
		switchToAppWindow();
		clickByTexts("Agregar Negocio");
		waitForUiLoad();

		assertAnyTextVisible("Crear Nuevo Negocio");
		final WebElement nameField = waitForFirstVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio')] | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));
		assertNotNull("Nombre del Negocio input was not found.", nameField);
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertAnyTextVisible("Cancelar");
		assertAnyTextVisible("Crear Negocio");
		takeScreenshot("03-crear-nuevo-negocio-modal");

		nameField.click();
		nameField.sendKeys("Negocio Prueba Automatizacion");
		clickByTexts("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(textContainsLocator("Crear Nuevo Negocio")));
		waitForUiLoad();
	}

	private void stepAdministrarNegocios() throws Exception {
		switchToAppWindow();
		if (!isAnyTextVisible(SHORT_TIMEOUT, "Administrar Negocios")) {
			clickByTexts("Mi Negocio");
			waitForUiLoad();
		}

		clickByTexts("Administrar Negocios");
		waitForUiLoad();

		assertAnyTextVisible(INFO_GENERAL, "Informacion General");
		assertAnyTextVisible(DETALLES_CUENTA);
		assertAnyTextVisible(TUS_NEGOCIOS);
		assertAnyTextVisible("Seccion Legal", "Secci\u00F3n Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void stepInformacionGeneral() {
		final String sectionText = getSectionText(INFO_GENERAL, "Informacion General");
		assertContainsNormalized(sectionText, "business plan");
		assertContainsNormalized(sectionText, "cambiar plan");
		assertTrue("User email is not visible in Informacion General.",
				Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+").matcher(sectionText).find());
		assertTrue("User name was not found in Informacion General.", hasLikelyUserName(sectionText));
	}

	private void stepDetallesCuenta() {
		final String sectionText = getSectionText(DETALLES_CUENTA);
		assertContainsNormalized(sectionText, "cuenta creada");
		assertContainsNormalized(sectionText, "estado activo");
		assertContainsNormalized(sectionText, "idioma seleccionado");
	}

	private void stepTusNegocios() {
		final String sectionText = getSectionText(TUS_NEGOCIOS);
		assertContainsNormalized(sectionText, "agregar negocio");
		assertContainsNormalized(sectionText, "tienes 2 de 3 negocios");
		assertTrue("Business list is not visible in Tus Negocios.", sectionText.split("\\R").length >= 4);
	}

	private void stepTerminos() throws Exception {
		termsFinalUrl = validateLegalLink(new String[] { TERMS_LABEL, "Terminos y Condiciones" },
				new String[] { TERMS_LABEL, "Terminos y Condiciones" }, "08-terminos-y-condiciones");
	}

	private void stepPrivacidad() throws Exception {
		privacyFinalUrl = validateLegalLink(new String[] { PRIVACY_LABEL, "Politica de Privacidad" },
				new String[] { PRIVACY_LABEL, "Politica de Privacidad" }, "09-politica-de-privacidad");
	}

	private String validateLegalLink(final String[] clickableTexts, final String[] headingTexts, final String screenshotName)
			throws Exception {
		switchToAppWindow();
		final String currentWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickByTexts(clickableTexts);
		waitForUiLoad();

		final String newWindow = waitForNewWindow(beforeHandles, Duration.ofSeconds(10));
		if (newWindow != null) {
			driver.switchTo().window(newWindow);
		}

		assertAnyTextVisible(headingTexts);
		final String legalText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal content text is not visible.", normalize(legalText).length() > 120);
		takeScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();
		assertFalse("Final URL is empty.", finalUrl == null || finalUrl.isBlank());

		if (newWindow != null) {
			driver.close();
			driver.switchTo().window(currentWindow);
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		assertAnyTextVisible("Seccion Legal", "Secci\u00F3n Legal", DETALLES_CUENTA);
		return finalUrl;
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			report.put(reportField, "PASS");
		} catch (final Exception | AssertionError error) {
			report.put(reportField, "FAIL");
			failures.put(reportField, safeMessage(error));
		}
	}

	private void clickByTexts(final String... texts) throws Exception {
		final WebElement element = waitForClickableByTexts(texts);
		clickAndWait(element);
	}

	private void clickIfVisibleByTexts(final String... texts) throws Exception {
		final WebElement element = findVisibleByTexts(SHORT_TIMEOUT, texts);
		if (element != null) {
			clickAndWait(element);
		}
	}

	private WebElement waitForClickableByTexts(final String... texts) {
		Exception lastError = null;
		for (final String text : texts) {
			final By locator = clickableTextLocator(text);
			try {
				return wait.until(ExpectedConditions.elementToBeClickable(locator));
			} catch (final Exception error) {
				lastError = error;
			}
		}

		throw new IllegalStateException("No clickable element found for texts: " + String.join(", ", texts), lastError);
	}

	private WebElement waitForFirstVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private WebElement findVisibleByTexts(final Duration timeout, final String... texts) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		for (final String text : texts) {
			try {
				return shortWait.until(ExpectedConditions.visibilityOfElementLocated(textContainsLocator(text)));
			} catch (final Exception ignored) {
				// try next locator
			}
		}

		return null;
	}

	private void assertAnyTextVisible(final String... texts) {
		if (!isAnyTextVisible(DEFAULT_TIMEOUT, texts)) {
			throw new AssertionError("None of these texts were visible: " + String.join(", ", texts));
		}
	}

	private boolean isAnyTextVisible(final Duration timeout, final String... texts) {
		return findVisibleByTexts(timeout, texts) != null;
	}

	private void clickAndWait(final WebElement element) throws Exception {
		wait.until(ExpectedConditions.visibilityOf(element));
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			new Actions(driver).moveToElement(element).pause(Duration.ofMillis(150)).click().perform();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiLoad();
	}

	private void waitForUiLoad() throws Exception {
		wait.until(webDriver -> "complete"
				.equals(String.valueOf(((JavascriptExecutor) webDriver).executeScript("return document.readyState"))));
		Thread.sleep(300);
	}

	private void waitForMainInterface() {
		final Instant deadline = Instant.now().plus(Duration.ofSeconds(90));

		while (Instant.now().isBefore(deadline)) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (isSidebarVisible()) {
					appWindowHandle = handle;
					return;
				}
			}

			try {
				Thread.sleep(400);
			} catch (final InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting for main interface.", interruptedException);
			}
		}

		throw new TimeoutException("Main application interface and left sidebar were not detected.");
	}

	private void selectGoogleAccountIfVisible() throws Exception {
		final Instant deadline = Instant.now().plus(Duration.ofSeconds(25));
		while (Instant.now().isBefore(deadline)) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				final WebElement accountEntry = findVisibleByTexts(Duration.ofSeconds(1), GOOGLE_ACCOUNT);
				if (accountEntry != null) {
					clickAndWait(accountEntry);
					return;
				}
			}

			Thread.sleep(300);
		}
	}

	private void switchToAppWindow() {
		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		} else {
			appWindowHandle = driver.getWindowHandle();
		}
	}

	private String waitForNewWindow(final Set<String> existingHandles, final Duration timeout) {
		final Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			final Set<String> currentHandles = driver.getWindowHandles();
			if (currentHandles.size() > existingHandles.size()) {
				for (final String handle : currentHandles) {
					if (!existingHandles.contains(handle)) {
						return handle;
					}
				}
			}

			try {
				Thread.sleep(250);
			} catch (final InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		return null;
	}

	private String getSectionText(final String... sectionTitles) {
		for (final String sectionTitle : sectionTitles) {
			final WebElement titleElement = findVisibleByTexts(DEFAULT_TIMEOUT, sectionTitle);
			if (titleElement == null) {
				continue;
			}

			final List<WebElement> containers = titleElement
					.findElements(By.xpath("./ancestor::*[self::section or self::article or self::div]"));
			for (final WebElement container : containers) {
				final String text = container.getText();
				if (text != null && normalize(text).length() > normalize(sectionTitle).length() + 30) {
					return text;
				}
			}
		}

		throw new AssertionError("Section container not found for: " + String.join(", ", sectionTitles));
	}

	private boolean hasLikelyUserName(final String sectionText) {
		final String normalizedText = normalize(sectionText);
		final String[] lines = normalizedText.split("\\R");

		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty() || line.contains("@")) {
				continue;
			}

			if (line.contains("informacion general") || line.contains("business plan") || line.contains("cambiar plan")
					|| line.contains("cuenta creada") || line.contains("estado activo")) {
				continue;
			}

			if (line.matches(".*[a-z]{2,}.*")) {
				return true;
			}
		}

		return false;
	}

	private void assertContainsNormalized(final String source, final String expected) {
		assertTrue("Expected to find '" + expected + "' in section content.",
				normalize(source).contains(normalize(expected)));
	}

	private By textContainsLocator(final String visibleText) {
		final String lowered = visibleText.toLowerCase(Locale.ROOT);
		final String xpath = "//*[contains(translate(normalize-space(.),'" + XPATH_UPPER + "','" + XPATH_LOWER + "'),"
				+ xpathLiteral(lowered) + ")]";
		return By.xpath(xpath);
	}

	private By clickableTextLocator(final String visibleText) {
		final String lowered = visibleText.toLowerCase(Locale.ROOT);
		final String textPredicate = "contains(translate(normalize-space(.),'" + XPATH_UPPER + "','" + XPATH_LOWER + "'),"
				+ xpathLiteral(lowered) + ")";
		final String xpath = "(//button[" + textPredicate + "] | //a[" + textPredicate + "] | //*[@role='button' and "
				+ textPredicate + "] | //span[" + textPredicate + "] | //div[" + textPredicate + "])[1]";
		return By.xpath(xpath);
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder expression = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				expression.append(",");
			}
			if (chars[i] == '\'') {
				expression.append("\"'\"");
			} else {
				expression.append("'").append(chars[i]).append("'");
			}
		}
		expression.append(")");
		return expression.toString();
	}

	private String normalize(final String value) {
		if (value == null) {
			return "";
		}

		final String lower = value.toLowerCase(Locale.ROOT);
		final String withoutDiacritics = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return withoutDiacritics.trim();
	}

	private boolean isSidebarVisible() {
		try {
			final WebElement sidebar = new WebDriverWait(driver, SHORT_TIMEOUT)
					.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("aside, nav, [class*='sidebar']")));
			return sidebar != null && isAnyTextVisible(SHORT_TIMEOUT, "Mi Negocio", "Negocio");
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void takeScreenshot(final String fileName) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = screenshotDir.resolve(fileName + ".png");
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void initializeReport() {
		for (final String field : REPORT_FIELDS) {
			report.put(field, "NOT_RUN");
		}
	}

	private String envOrDefault(final String key, final String fallbackValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? fallbackValue : value;
	}

	private String safeMessage(final Throwable throwable) {
		if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
			return throwable == null ? "Unknown error." : throwable.getClass().getSimpleName();
		}

		return throwable.getMessage().replaceAll("\\s+", " ").trim();
	}

	private String formatFailures() {
		if (failures.isEmpty()) {
			return "No failures.";
		}

		final StringBuilder builder = new StringBuilder();
		failures.forEach((field, message) -> builder.append("- ").append(field).append(": ").append(message).append('\n'));
		return builder.toString();
	}

	private void printFinalReport() {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("\n===== SaleADS Mi Negocio Validation Report =====\n");
		for (final String field : REPORT_FIELDS) {
			reportBuilder.append(field).append(": ").append(report.get(field)).append('\n');
		}
		if (termsFinalUrl != null) {
			reportBuilder.append("Final URL (").append(TERMS_LABEL).append("): ").append(termsFinalUrl).append('\n');
		}
		if (privacyFinalUrl != null) {
			reportBuilder.append("Final URL (").append(PRIVACY_LABEL).append("): ").append(privacyFinalUrl).append('\n');
		}
		reportBuilder.append("Screenshots folder: ").append(screenshotDir.toAbsolutePath()).append('\n');
		reportBuilder.append("==============================================\n");
		System.out.println(reportBuilder);
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
