package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * <p>This test is environment-agnostic and never hardcodes a domain. Provide the login page URL using
 * one of the following:
 * <ul>
 *   <li>System property: -Dsaleads.login.url=https://your-env/login</li>
 *   <li>Environment variable: SALEADS_LOGIN_URL</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter EVIDENCE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Provide saleads login URL with -Dsaleads.login.url or SALEADS_LOGIN_URL.",
				loginUrl != null && !loginUrl.isBlank());

		evidenceDir = Paths.get("target", "saleads-evidence", EVIDENCE_STAMP.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		final ChromeOptions chromeOptions = new ChromeOptions();
		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			chromeOptions.addArguments("--headless=new");
		}

		chromeOptions.addArguments("--window-size=1920,1080");
		chromeOptions.addArguments("--disable-dev-shm-usage");
		chromeOptions.addArguments("--no-sandbox");

		driver = new ChromeDriver(chromeOptions);
		wait = new WebDriverWait(driver, Duration.ofSeconds(Integer.parseInt(readConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", "30"))));
		driver.get(loginUrl);
		waitForUiLoad();
	}

	@After
	public void tearDown() {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		finalReport.put("Login", runStep(this::stepLoginWithGoogle));
		finalReport.put("Mi Negocio menu", runStep(this::stepOpenMiNegocioMenu));
		finalReport.put("Agregar Negocio modal", runStep(this::stepValidateAgregarNegocioModal));
		finalReport.put("Administrar Negocios view", runStep(this::stepOpenAdministrarNegocios));
		finalReport.put("Informaci\u00F3n General", runStep(this::stepValidateInformacionGeneral));
		finalReport.put("Detalles de la Cuenta", runStep(this::stepValidateDetallesCuenta));
		finalReport.put("Tus Negocios", runStep(this::stepValidateTusNegocios));
		finalReport.put("T\u00E9rminos y Condiciones", runStep(this::stepValidateTerminosYCondiciones));
		finalReport.put("Pol\u00EDtica de Privacidad", runStep(this::stepValidatePoliticaDePrivacidad));

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			if (!entry.getValue()) {
				failedSteps.add(entry.getKey());
			}
		}

		Assert.assertTrue("SaleADS Mi Negocio workflow failed steps: " + failedSteps, failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() {
		clickFirstAvailableText(
				"Sign in with Google",
				"Iniciar sesi\u00F3n con Google",
				"Continuar con Google",
				"Iniciar sesi\u00F3n",
				"Google");

		// Optional step if Google account chooser appears.
		if (isTextVisible(GOOGLE_ACCOUNT_EMAIL, 8)) {
			clickText(GOOGLE_ACCOUNT_EMAIL);
		}

		waitForAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Panel");
		waitForSidebar();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		waitForSidebar();
		clickText("Negocio");
		clickText("Mi Negocio");

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickText("Agregar Negocio");

		waitForVisibleText("Crear Nuevo Negocio");
		waitForVisibleText("Nombre del Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");
		Assert.assertTrue("Input for Nombre del Negocio was not found.",
				hasVisibleElement(By.xpath("//input[not(@type='hidden')]")));

		captureScreenshot("03-agregar-negocio-modal");

		// Optional action requested: type in name then close with Cancelar.
		typeIntoFirstVisibleInput("Negocio Prueba Automatizaci\u00F3n");
		clickText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() {
		if (!isTextVisible("Administrar Negocios", 3)) {
			clickText("Mi Negocio");
		}
		clickText("Administrar Negocios");

		waitForVisibleText("Informaci\u00F3n General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Secci\u00F3n Legal");

		captureScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		final String sectionText = sectionTextByHeading("Informaci\u00F3n General");
		Assert.assertTrue("Expected user email to be visible in Informacion General section.",
				EMAIL_PATTERN.matcher(driver.getPageSource()).find() || EMAIL_PATTERN.matcher(sectionText).find());
		Assert.assertTrue("Expected BUSINESS PLAN text to be visible.", isTextVisible("BUSINESS PLAN", 5));
		Assert.assertTrue("Expected Cambiar Plan button to be visible.", isTextVisible("Cambiar Plan", 5));
		Assert.assertTrue("Expected user name to be visible.", hasLikelyUserName(sectionText));
	}

	private void stepValidateDetallesCuenta() {
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosYCondiciones() {
		final String url = openLegalLinkAndValidate(
				"T\u00E9rminos y Condiciones",
				"T\u00E9rminos y Condiciones",
				"05-terminos-y-condiciones");
		legalUrls.put("T\u00E9rminos y Condiciones URL", url);
	}

	private void stepValidatePoliticaDePrivacidad() {
		final String url = openLegalLinkAndValidate(
				"Pol\u00EDtica de Privacidad",
				"Pol\u00EDtica de Privacidad",
				"06-politica-de-privacidad");
		legalUrls.put("Pol\u00EDtica de Privacidad URL", url);
	}

	private String openLegalLinkAndValidate(final String linkText, final String headingText, final String screenshotName) {
		final String originalWindow = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickText(linkText);

		String legalWindowHandle = null;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12))
					.until(d -> d.getWindowHandles().size() > handlesBefore.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					legalWindowHandle = handle;
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			legalWindowHandle = null;
		}

		if (legalWindowHandle != null) {
			driver.switchTo().window(legalWindowHandle);
			waitForUiLoad();
		} else {
			new WebDriverWait(driver, Duration.ofSeconds(12))
					.until((ExpectedCondition<Boolean>) d -> !d.getCurrentUrl().equals(originalUrl) || isTextVisible(headingText, 2));
		}

		waitForVisibleText(headingText);
		Assert.assertTrue("Expected legal content text to be visible.",
				hasVisibleElement(By.xpath("//*[self::p or self::li or self::div][string-length(normalize-space()) > 140]")));
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (legalWindowHandle != null) {
			driver.close();
			driver.switchTo().window(originalWindow);
		} else {
			driver.navigate().back();
		}
		waitForUiLoad();
		return finalUrl;
	}

	private boolean runStep(final CheckedRunnable action) {
		try {
			action.run();
			return true;
		} catch (final AssertionError | RuntimeException ex) {
			captureScreenshot("failed-" + sanitizeFileName(ex.getClass().getSimpleName()) + "-" + System.currentTimeMillis());
			return false;
		} catch (final Exception ex) {
			captureScreenshot("failed-Exception-" + System.currentTimeMillis());
			return false;
		}
	}

	private void waitForSidebar() {
		Assert.assertTrue("Left sidebar navigation is not visible.",
				hasVisibleElement(By.xpath("//aside | //*[@role='navigation'] | //nav")));
	}

	private void clickFirstAvailableText(final String... texts) {
		RuntimeException latest = null;
		for (final String text : texts) {
			try {
				clickText(text);
				return;
			} catch (final RuntimeException ex) {
				latest = ex;
			}
		}
		throw new RuntimeException("No target text was clickable for any of: " + Arrays.toString(texts), latest);
	}

	private void clickText(final String text) {
		final By locator = byClickableText(text);
		final WebElement clickable = wait.until(ExpectedConditions.elementToBeClickable(locator));
		scrollIntoView(clickable);
		clickable.click();
		waitForUiLoad();
		sleep(700);
	}

	private void waitForAnyVisibleText(final String... candidates) {
		RuntimeException latest = null;
		for (final String candidate : candidates) {
			try {
				waitForVisibleText(candidate);
				return;
			} catch (final RuntimeException ex) {
				latest = ex;
			}
		}
		throw new RuntimeException("No expected text became visible: " + Arrays.toString(candidates), latest);
	}

	private WebElement waitForVisibleText(final String text) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(byText(text)));
	}

	private boolean isTextVisible(final String text, final int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(byText(text)));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void typeIntoFirstVisibleInput(final String value) {
		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//input[not(@type='hidden')]")));
		input.clear();
		input.sendKeys(value);
		sleep(300);
	}

	private boolean hasVisibleElement(final By by) {
		try {
			final List<WebElement> elements = driver.findElements(by);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final NoSuchElementException ex) {
			return false;
		}
	}

	private String sectionTextByHeading(final String headingText) {
		final String literal = toXPathLiteral(headingText);
		final By sectionBy = By.xpath(
				"(//*[contains(normalize-space(.), " + literal + ")]/ancestor::*[self::section or self::div][.//*[contains(normalize-space(.), "
						+ literal + ")]])[1]");
		try {
			final WebElement section = wait.until(ExpectedConditions.visibilityOfElementLocated(sectionBy));
			return section.getText();
		} catch (final TimeoutException ex) {
			return "";
		}
	}

	private boolean hasLikelyUserName(final String text) {
		for (final String rawLine : text.split("\\R")) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}

			final String lower = line.toLowerCase(Locale.ROOT);
			if (lower.contains("informaci") || lower.contains("business plan") || lower.contains("cambiar plan")
					|| EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}

			if (line.matches(".*[A-Za-z].*") && line.replaceAll("[^A-Za-z ]", "").trim().length() >= 4) {
				return true;
			}
		}
		return false;
	}

	private void captureScreenshot(final String checkpointName) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path output = evidenceDir.resolve(sanitizeFileName(checkpointName) + ".png");
		try {
			Files.copy(screenshot.toPath(), output, StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException ignored) {
			// Best-effort evidence capture should not stop the workflow.
		}
	}

	private void writeFinalReport() {
		if (evidenceDir == null) {
			return;
		}

		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Workflow Final Report");
		lines.add("=======================================");
		lines.add("Evidence directory: " + evidenceDir.toAbsolutePath());
		lines.add("");
		lines.add("Step Results:");

		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			lines.add("- " + entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}

		if (!legalUrls.isEmpty()) {
			lines.add("");
			lines.add("Captured legal URLs:");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				lines.add("- " + entry.getKey() + ": " + entry.getValue());
			}
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		try {
			Files.write(reportPath, lines, StandardCharsets.UTF_8);
		} catch (final IOException ignored) {
			// Best-effort reporting only.
		}
	}

	private void waitForUiLoad() {
		wait.until((ExpectedCondition<Boolean>) d -> {
			final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(readyState);
		});
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'center'});", element);
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private By byText(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath("//*[contains(normalize-space(.), " + literal + ")]");
	}

	private By byClickableText(final String text) {
		final String literal = toXPathLiteral(text);
		final String containsText = "contains(normalize-space(.), " + literal + ")";
		final String button = "//button[" + containsText + "]";
		final String anchor = "//a[" + containsText + "]";
		final String roleButton = "//*[@role='button' and " + containsText + "]";
		final String clickableContainer =
				"//*[self::div or self::span][" + containsText + " and (contains(@class,'btn') or contains(@class,'button') or @onclick)]";
		final String fallback = "//*[" + containsText + "]";
		return By.xpath("(" + button + " | " + anchor + " | " + roleButton + " | " + clickableContainer + " | " + fallback + ")[1]");
	}

	private String readConfig(final String systemPropertyKey, final String environmentKey) {
		return readConfig(systemPropertyKey, environmentKey, null);
	}

	private String readConfig(final String systemPropertyKey, final String environmentKey, final String defaultValue) {
		final String propertyValue = System.getProperty(systemPropertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		final String envValue = System.getenv(environmentKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		return defaultValue;
	}

	private String sanitizeFileName(final String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
	}

	private String toXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final String[] parts = text.split("'");
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

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
