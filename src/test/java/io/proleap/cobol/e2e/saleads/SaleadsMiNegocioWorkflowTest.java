package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * Run with:
 * <pre>
 * mvn -Dsaleads.e2e.enabled=true -Dsaleads.baseUrl=https://YOUR-ENV/login -Dtest=SaleadsMiNegocioWorkflowTest test
 * </pre>
 *
 * <p>
 * Notes:
 * <ul>
 *   <li>The test does not hardcode any domain; the login URL is injected via property/env var.</li>
 *   <li>Screenshots and a PASS/FAIL report are stored under {@code target/saleads-evidence/<timestamp>/}.</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Informaci\u00f3n General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"T\u00e9rminos y Condiciones",
			"Pol\u00edtica de Privacidad");

	private final Map<String, String> report = new LinkedHashMap<>();
	private int screenshotIndex = 1;
	private Path evidenceRunDirectory;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(getConfigValue("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Enable with -Dsaleads.e2e.enabled=true", enabled);

		final String baseUrl = getConfigValue("saleads.baseUrl", "SALEADS_BASE_URL", "");
		Assume.assumeTrue("Provide login page with -Dsaleads.baseUrl or SALEADS_BASE_URL", baseUrl != null && !baseUrl.isBlank());

		for (final String field : REPORT_FIELDS) {
			report.put(field, "FAIL");
		}

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceRunDirectory = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceRunDirectory);

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(getConfigValue("saleads.headless", "SALEADS_HEADLESS", "false"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));

		driver.get(baseUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		if (evidenceRunDirectory != null) {
			writeFinalReport();
		}
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final boolean loginOk = executeStep("Login", () -> {
			loginWithGoogle();
			captureScreenshot("01-dashboard-loaded");
		});

		if (!loginOk) {
			markBlocked("Login failed", "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
					"Informaci\u00f3n General", "Detalles de la Cuenta", "Tus Negocios",
					"T\u00e9rminos y Condiciones", "Pol\u00edtica de Privacidad");
			assertAllReportFieldsPass();
			return;
		}

		final boolean menuOk = executeStep("Mi Negocio menu", () -> {
			openMiNegocioMenu();
			assertTextVisible("Agregar Negocio");
			assertTextVisible("Administrar Negocios");
			captureScreenshot("02-mi-negocio-menu-expanded");
		});

		final boolean modalOk = executeStep("Agregar Negocio modal", () -> {
			Assume.assumeTrue("Mi Negocio menu was not available", menuOk);
			clickByVisibleText("Agregar Negocio");
			assertTextVisible("Crear Nuevo Negocio");
			assertTextVisible("Nombre del Negocio");
			assertTextVisible("Tienes 2 de 3 negocios");
			assertTextVisible("Cancelar");
			assertTextVisible("Crear Negocio");
			captureScreenshot("03-agregar-negocio-modal");

			typeIfVisible("Nombre del Negocio", "Negocio Prueba Automatizacion");
			clickByVisibleText("Cancelar");
			waitForUiToLoad();
		});

		final boolean administrarOk = executeStep("Administrar Negocios view", () -> {
			Assume.assumeTrue("Previous navigation step failed", menuOk || modalOk);
			reopenMiNegocioMenuIfNeeded();
			clickByVisibleText("Administrar Negocios");
			assertTextVisible("Informacion General", "Informaci\u00f3n General");
			assertTextVisible("Detalles de la Cuenta");
			assertTextVisible("Tus Negocios");
			assertTextVisible("Seccion Legal", "Secci\u00f3n Legal");
			captureScreenshot("04-administrar-negocios");
		});

		executeStep("Informaci\u00f3n General", () -> {
			Assume.assumeTrue("Administrar Negocios view was not loaded", administrarOk);
			assertTextVisible("Informacion General", "Informaci\u00f3n General");
			assertTextVisible("BUSINESS PLAN");
			assertTextVisible("Cambiar Plan");
			assertVisibleTextContainsEmail();
			assertUserNameLikeTextPresent();
		});

		executeStep("Detalles de la Cuenta", () -> {
			Assume.assumeTrue("Administrar Negocios view was not loaded", administrarOk);
			assertTextVisible("Cuenta creada");
			assertTextVisible("Estado activo");
			assertTextVisible("Idioma seleccionado");
		});

		executeStep("Tus Negocios", () -> {
			Assume.assumeTrue("Administrar Negocios view was not loaded", administrarOk);
			assertTextVisible("Tus Negocios");
			assertTextVisible("Agregar Negocio");
			assertTextVisible("Tienes 2 de 3 negocios");
		});

		executeStep("T\u00e9rminos y Condiciones", () -> {
			Assume.assumeTrue("Administrar Negocios view was not loaded", administrarOk);
			reopenLegalSectionIfNeeded();
			termsUrl = openAndValidateLegalLink(
					new String[] { "Terminos y Condiciones", "T\u00e9rminos y Condiciones" },
					new String[] { "Terminos y Condiciones", "T\u00e9rminos y Condiciones" },
					"05-terminos-y-condiciones");
		});

		executeStep("Pol\u00edtica de Privacidad", () -> {
			Assume.assumeTrue("Administrar Negocios view was not loaded", administrarOk);
			reopenLegalSectionIfNeeded();
			privacyUrl = openAndValidateLegalLink(
					new String[] { "Politica de Privacidad", "Pol\u00edtica de Privacidad" },
					new String[] { "Politica de Privacidad", "Pol\u00edtica de Privacidad" },
					"06-politica-de-privacidad");
		});

		assertAllReportFieldsPass();
	}

	private void loginWithGoogle() throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText(
				"Sign in with Google",
				"Iniciar sesion con Google",
				"Iniciar sesi\u00f3n con Google",
				"Continuar con Google",
				"Ingresar con Google");

		switchToNewWindowIfOpened(handlesBefore);
		selectGoogleAccountIfPrompted("juanlucasbarbiergarzon@gmail.com");

		if (!driver.getWindowHandle().equals(originalHandle) && driver.getWindowHandles().contains(originalHandle)) {
			driver.switchTo().window(originalHandle);
		}

		waitForUiToLoad();
		assertSidebarVisible();
	}

	private void openMiNegocioMenu() {
		assertSidebarVisible();
		if (isTextVisible("Mi Negocio")) {
			clickByVisibleText("Mi Negocio");
		} else {
			clickByVisibleText("Negocio");
			waitForUiToLoad();
			clickByVisibleText("Mi Negocio");
		}
		waitForUiToLoad();
	}

	private void reopenMiNegocioMenuIfNeeded() {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}
	}

	private void reopenLegalSectionIfNeeded() {
		if (!isTextVisible("Seccion Legal") && !isTextVisible("Secci\u00f3n Legal")
				&& !isTextVisible("Terminos y Condiciones") && !isTextVisible("T\u00e9rminos y Condiciones")) {
			assertTextVisible("Administrar Negocios");
			assertTextVisible("Seccion Legal", "Secci\u00f3n Legal");
		}
	}

	private String openAndValidateLegalLink(final String[] linkTexts, final String[] headingTexts, final String screenshotName)
			throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String currentUrlBefore = driver.getCurrentUrl();

		clickByVisibleText(linkTexts);
		final String activeHandle = switchToNewWindowIfOpened(handlesBefore);
		if (activeHandle == null) {
			wait.until(ExpectedConditions.not(ExpectedConditions.urlToBe(currentUrlBefore)));
			waitForUiToLoad();
		}

		assertTextVisible(headingTexts);
		assertLegalBodyTextVisible();
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();
		if (activeHandle != null && !activeHandle.equals(originalHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
			assertTextVisible("Seccion Legal", "Secci\u00f3n Legal");
		}
		return finalUrl;
	}

	private void assertSidebarVisible() {
		final boolean sidebarByAria = isAnyElementVisible(By.cssSelector("aside, nav, [aria-label='sidebar'], [data-testid='sidebar']"));
		final boolean sidebarByText = isTextVisible("Negocio") || isTextVisible("Mi Negocio");
		assertTrue("Left sidebar navigation should be visible after login.", sidebarByAria || sidebarByText);
	}

	private void assertLegalBodyTextVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText().trim();
		assertTrue("Legal content text should be visible.", bodyText.length() > 200);
	}

	private void assertVisibleTextContainsEmail() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected user email to be visible in account information.", EMAIL_PATTERN.matcher(bodyText).find());
	}

	private void assertUserNameLikeTextPresent() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final String[] lines = bodyText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.length() < 4) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (line.equalsIgnoreCase("Informacion General") || line.equalsIgnoreCase("Informaci\u00f3n General")
					|| line.equalsIgnoreCase("BUSINESS PLAN") || line.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}
			if (line.matches("[A-Za-z\\u00c0-\\u017f]+\\s+[A-Za-z\\u00c0-\\u017f].*")) {
				return;
			}
		}
		fail("Expected a visible user name-like text in Informacion General.");
	}

	private boolean executeStep(final String reportField, final StepAction stepAction) {
		try {
			stepAction.run();
			report.put(reportField, "PASS");
			return true;
		} catch (final org.junit.AssumptionViolatedException assumption) {
			report.put(reportField, "FAIL");
			return false;
		} catch (final Exception e) {
			report.put(reportField, "FAIL");
			try {
				captureScreenshot("failure-" + reportField.toLowerCase(Locale.ROOT).replace(' ', '-'));
			} catch (final IOException ignored) {
				// Best effort screenshot on failure.
			}
			return false;
		}
	}

	private void markBlocked(final String reason, final String... fields) {
		for (final String field : fields) {
			report.put(field, "FAIL (" + reason + ")");
		}
	}

	private void assertAllReportFieldsPass() {
		final List<String> failedFields = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			final String value = report.getOrDefault(field, "FAIL");
			if (!value.startsWith("PASS")) {
				failedFields.add(field + "=" + value);
			}
		}
		assertTrue("One or more validations failed: " + failedFields, failedFields.isEmpty());
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		for (final String field : REPORT_FIELDS) {
			builder.append(field).append(": ").append(report.getOrDefault(field, "FAIL")).append(System.lineSeparator());
		}
		builder.append("T\u00e9rminos y Condiciones URL: ").append(termsUrl).append(System.lineSeparator());
		builder.append("Pol\u00edtica de Privacidad URL: ").append(privacyUrl).append(System.lineSeparator());
		builder.append("Evidence directory: ").append(evidenceRunDirectory.toAbsolutePath()).append(System.lineSeparator());

		final Path reportPath = evidenceRunDirectory.resolve("saleads_mi_negocio_full_test_report.txt");
		Files.writeString(reportPath, builder.toString());
	}

	private String switchToNewWindowIfOpened(final Set<String> handlesBefore) {
		try {
			wait.until(driverInstance -> driverInstance.getWindowHandles().size() > handlesBefore.size());
			final Set<String> handlesAfter = new LinkedHashSet<>(driver.getWindowHandles());
			handlesAfter.removeAll(handlesBefore);
			if (!handlesAfter.isEmpty()) {
				final String newHandle = handlesAfter.iterator().next();
				driver.switchTo().window(newHandle);
				waitForUiToLoad();
				return newHandle;
			}
		} catch (final TimeoutException ignored) {
			// Same-tab navigation is valid too.
		}
		return null;
	}

	private void selectGoogleAccountIfPrompted(final String accountEmail) {
		final String currentUrl = driver.getCurrentUrl();
		final String title = driver.getTitle();
		final boolean googleAuthContext = currentUrl.contains("google.") || title.toLowerCase(Locale.ROOT).contains("google");
		if (!googleAuthContext) {
			return;
		}

		if (isTextVisible(accountEmail)) {
			clickByVisibleText(accountEmail);
		}
		waitForUiToLoad();
	}

	private void typeIfVisible(final String fieldLabel, final String value) {
		final String labelLiteral = toXPathLiteral(fieldLabel);
		final List<By> locators = Arrays.asList(
				By.xpath("//input[@placeholder=" + labelLiteral + "]"),
				By.xpath("//label[normalize-space(.)=" + labelLiteral + "]/following::input[1]"),
				By.xpath("//input[@aria-label=" + labelLiteral + "]"));

		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed() && element.isEnabled()) {
					element.clear();
					element.sendKeys(value);
					return;
				}
			}
		}
	}

	private void clickByVisibleText(final String... textOptions) {
		WebElement element = waitForFirstVisibleText(true, textOptions);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final Exception clickFailure) {
			element = waitForFirstVisibleText(false, textOptions);
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void assertTextVisible(final String... textOptions) {
		waitForFirstVisibleText(false, textOptions);
	}

	private boolean isTextVisible(final String text) {
		try {
			findFirstVisibleElementByText(text, false);
			return true;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private WebElement waitForFirstVisibleText(final boolean clickableOnly, final String... textOptions) {
		return wait.until(driverInstance -> {
			for (final String text : textOptions) {
				try {
					return findFirstVisibleElementByText(text, clickableOnly);
				} catch (final Exception ignored) {
					// Continue trying other text alternatives until timeout.
				}
			}
			return null;
		});
	}

	private WebElement findFirstVisibleElementByText(final String text, final boolean clickableOnly) {
		final String literal = toXPathLiteral(text);
		final List<By> locators = new ArrayList<>();
		if (clickableOnly) {
			locators.add(By.xpath("//*[self::button or self::a or @role='button'][normalize-space(.)=" + literal + "]"));
			locators.add(By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.)," + literal + ")]"));
		}
		locators.add(By.xpath("//*[normalize-space(.)=" + literal + "]"));
		locators.add(By.xpath("//*[contains(normalize-space(.)," + literal + ")]"));

		for (final By locator : locators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		throw new IllegalStateException("Visible element not found for text: " + text);
	}

	private boolean isAnyElementVisible(final By locator) {
		for (final WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void waitForUiToLoad() {
		wait.until(driverInstance -> "complete".equals(
				((JavascriptExecutor) driverInstance).executeScript("return document.readyState")));

		final List<By> loadingIndicators = Arrays.asList(
				By.cssSelector("[aria-busy='true']"),
				By.cssSelector(".loading"),
				By.cssSelector(".loader"),
				By.cssSelector(".spinner"),
				By.cssSelector(".ant-spin-spinning"));

		for (final By indicator : loadingIndicators) {
			try {
				wait.until(ExpectedConditions.invisibilityOfElementLocated(indicator));
			} catch (final TimeoutException ignored) {
				// Not all pages use the same loading indicators.
			}
		}
	}

	private void captureScreenshot(final String name) throws IOException {
		final String safeName = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		final String fileName = String.format("%02d-%s.png", screenshotIndex++, safeName);
		final Path targetFile = evidenceRunDirectory.resolve(fileName);
		final Path screenshotSource = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(screenshotSource, targetFile, StandardCopyOption.REPLACE_EXISTING);
	}

	private String getConfigValue(final String propertyName, final String envName, final String defaultValue) {
		final String systemValue = System.getProperty(propertyName);
		if (systemValue != null && !systemValue.isBlank()) {
			return systemValue;
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return defaultValue;
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder concatBuilder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				concatBuilder.append(", \"'\", ");
			}
			concatBuilder.append("'").append(parts[i]).append("'");
		}
		concatBuilder.append(")");
		return concatBuilder.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
