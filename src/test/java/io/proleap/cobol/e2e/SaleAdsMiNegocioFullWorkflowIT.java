package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * E2E integration test for the SaleADS Mi Negocio flow.
 *
 * <p>
 * Environment values:
 * <ul>
 * <li>SALEADS_LOGIN_URL (required): login page URL for the active environment.</li>
 * <li>SALEADS_EXPECTED_USER_NAME (optional): exact user name to validate in Informacion General.</li>
 * <li>SALEADS_HEADLESS (optional, default true): true/false to run headless.</li>
 * <li>SALEADS_UI_TIMEOUT_SECONDS (optional, default 35): explicit wait timeout.</li>
 * </ul>
 */
public class SaleAdsMiNegocioFullWorkflowIT {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL_REPORT = "Informacion General";
	private static final String DETALLES_CUENTA_REPORT = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS_REPORT = "Tus Negocios";
	private static final String TERMINOS_REPORT = "Terminos y Condiciones";
	private static final String POLITICA_REPORT = "Politica de Privacidad";
	private static final String INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String SECCION_LEGAL = "Secci\u00f3n Legal";
	private static final String TERMINOS_LINK = "T\u00e9rminos y Condiciones";
	private static final String POLITICA_LINK = "Pol\u00edtica de Privacidad";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");

	private static final List<String> REPORT_FIELDS = Arrays.asList(LOGIN, MI_NEGOCIO_MENU, AGREGAR_NEGOCIO_MODAL,
			ADMINISTRAR_NEGOCIOS_VIEW, INFORMACION_GENERAL_REPORT, DETALLES_CUENTA_REPORT, TUS_NEGOCIOS_REPORT,
			TERMINOS_REPORT, POLITICA_REPORT);

	@Test
	public void saleadsMiNegocioWorkflow() throws Exception {
		final Map<String, StepResult> report = initializeReport();
		final Path screenshotDir = prepareScreenshotDir();
		final String[] termsUrlHolder = new String[] { "N/A" };
		final String[] privacyUrlHolder = new String[] { "N/A" };
		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL");

		WebDriver driver = null;
		try {
			if (isBlank(loginUrl)) {
				report.put(LOGIN,
						StepResult.fail("Missing SALEADS_LOGIN_URL (or -Dsaleads.login.url). URL must be provided."));
				throw new IllegalStateException(
						"Provide SALEADS_LOGIN_URL (or -Dsaleads.login.url) to run against the desired environment.");
			}

			driver = createChromeDriver();
			driver.get(loginUrl);
			waitForUiToLoad(driver, timeoutSeconds());

			final WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds()));

			runStep(report, LOGIN, () -> {
				clickOneOfTexts(driver, wait,
						Arrays.asList("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google",
								"Ingresar con Google", "Acceder con Google", "Google"));
				waitForUiToLoad(driver, timeoutSeconds());
				selectGoogleAccountIfShown(driver, "juanlucasbarbiergarzon@gmail.com");
				switchToWindowContainingApp(driver);
				waitForAnyVisible(driver, wait, By.xpath("//aside"), By.xpath("//*[contains(@class, 'sidebar')]"),
						By.xpath("//nav"), By.xpath("//*[normalize-space()='Negocio']"),
						By.xpath("//*[normalize-space()='Mi Negocio']"));
				captureScreenshot(driver, screenshotDir, "01_dashboard_loaded");
			});

			runStep(report, MI_NEGOCIO_MENU, () -> {
				clickOneOfTexts(driver, wait, Arrays.asList("Negocio"));
				waitForUiToLoad(driver, timeoutSeconds());
				clickOneOfTexts(driver, wait, Arrays.asList("Mi Negocio"));
				waitForUiToLoad(driver, timeoutSeconds());
				waitForVisibleByText(driver, "Agregar Negocio", Duration.ofSeconds(timeoutSeconds()));
				waitForVisibleByText(driver, "Administrar Negocios", Duration.ofSeconds(timeoutSeconds()));
				captureScreenshot(driver, screenshotDir, "02_mi_negocio_menu_expanded");
			});

			runStep(report, AGREGAR_NEGOCIO_MODAL, () -> {
				clickOneOfTexts(driver, wait, Arrays.asList("Agregar Negocio"));
				waitForVisibleByText(driver, "Crear Nuevo Negocio", Duration.ofSeconds(timeoutSeconds()));
				waitForVisibleByText(driver, "Nombre del Negocio", Duration.ofSeconds(timeoutSeconds()));
				waitForVisibleByText(driver, "Tienes 2 de 3 negocios", Duration.ofSeconds(timeoutSeconds()));
				waitForVisibleByText(driver, "Cancelar", Duration.ofSeconds(timeoutSeconds()));
				waitForVisibleByText(driver, "Crear Negocio", Duration.ofSeconds(timeoutSeconds()));
				captureScreenshot(driver, screenshotDir, "03_agregar_negocio_modal");

				final WebElement input = findFirstVisible(driver,
						By.xpath("//input[@placeholder='Nombre del Negocio']"),
						By.xpath("//div[@role='dialog']//input[1]"), By.xpath("//input[contains(@name, 'business')]"),
						By.xpath("//input[contains(@name, 'nombre')]"));
				if (input != null) {
					input.clear();
					input.sendKeys("Negocio Prueba Automatizacion");
				}
				clickOneOfTexts(driver, wait, Arrays.asList("Cancelar"));
				waitForUiToLoad(driver, timeoutSeconds());
			});

			runStep(report, ADMINISTRAR_NEGOCIOS_VIEW, () -> {
				if (!isTextVisible(driver, "Administrar Negocios", Duration.ofSeconds(3))) {
					clickOneOfTexts(driver, wait, Arrays.asList("Mi Negocio"));
				}
				clickOneOfTexts(driver, wait, Arrays.asList("Administrar Negocios"));
				waitForUiToLoad(driver, timeoutSeconds());

				waitForVisibleByText(driver, INFORMACION_GENERAL, Duration.ofSeconds(timeoutSeconds()));
				waitForVisibleByText(driver, DETALLES_CUENTA, Duration.ofSeconds(timeoutSeconds()));
				waitForVisibleByText(driver, "Tus Negocios", Duration.ofSeconds(timeoutSeconds()));
				waitForVisibleByText(driver, SECCION_LEGAL, Duration.ofSeconds(timeoutSeconds()));
				captureScreenshot(driver, screenshotDir, "04_administrar_negocios_view");
			});

			runStep(report, INFORMACION_GENERAL_REPORT, () -> {
				final WebElement section = findSectionByHeading(driver, INFORMACION_GENERAL);
				final String sectionText = section.getText();
				assertTrue("User email should be visible in Informacion General.",
						EMAIL_PATTERN.matcher(sectionText).find());

				final String expectedUserName = readConfig("saleads.expected.user.name", "SALEADS_EXPECTED_USER_NAME");
				if (!isBlank(expectedUserName)) {
					assertTrue("Expected user name was not found: " + expectedUserName,
							sectionText.contains(expectedUserName));
				} else {
					assertTrue("A user name-like text should be visible.",
							hasNameLikeLine(sectionText, expectedUserName));
				}

				waitForVisibleByText(driver, "BUSINESS PLAN", Duration.ofSeconds(timeoutSeconds()));
				waitForVisibleByText(driver, "Cambiar Plan", Duration.ofSeconds(timeoutSeconds()));
			});

			runStep(report, DETALLES_CUENTA_REPORT, () -> {
				waitForVisibleByText(driver, "Cuenta creada", Duration.ofSeconds(timeoutSeconds()));
				waitForVisibleByText(driver, "Estado activo", Duration.ofSeconds(timeoutSeconds()));
				waitForVisibleByText(driver, "Idioma seleccionado", Duration.ofSeconds(timeoutSeconds()));
			});

			runStep(report, TUS_NEGOCIOS_REPORT, () -> {
				final WebElement section = findSectionByHeading(driver, "Tus Negocios");
				waitForVisibleByText(driver, "Agregar Negocio", Duration.ofSeconds(timeoutSeconds()));
				waitForVisibleByText(driver, "Tienes 2 de 3 negocios", Duration.ofSeconds(timeoutSeconds()));

				final List<WebElement> businessEntries = section
						.findElements(By.xpath(".//li | .//tr | .//*[contains(@class, 'business')]"));
				assertTrue("Business list should be visible in Tus Negocios.", !businessEntries.isEmpty());
			});

			runStep(report, TERMINOS_REPORT, () -> {
				termsUrlHolder[0] = validateLegalLink(driver, wait, TERMINOS_LINK, TERMINOS_LINK, screenshotDir,
						"05_terminos_condiciones");
			});

			runStep(report, POLITICA_REPORT, () -> {
				privacyUrlHolder[0] = validateLegalLink(driver, wait, POLITICA_LINK, POLITICA_LINK, screenshotDir,
						"06_politica_privacidad");
			});
		} catch (final Exception e) {
			if (report.get(LOGIN).state == StepState.NOT_RUN) {
				report.put(LOGIN, StepResult.fail("Unexpected setup error: " + e.getMessage()));
			}
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}

		printFinalReport(report, termsUrlHolder[0], privacyUrlHolder[0], screenshotDir);
		assertAllStepsPassed(report);
	}

	private WebDriver createChromeDriver() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		final String headless = readConfig("saleads.headless", "SALEADS_HEADLESS");
		if (isBlank(headless) || Boolean.parseBoolean(headless)) {
			options.addArguments("--headless=new");
		}

		return new ChromeDriver(options);
	}

	private void runStep(final Map<String, StepResult> report, final String stepName, final StepAction stepAction) {
		try {
			stepAction.run();
			report.put(stepName, StepResult.pass());
		} catch (final Exception e) {
			report.put(stepName, StepResult.fail(compactError(e)));
		}
	}

	private String validateLegalLink(final WebDriver driver, final WebDriverWait wait, final String linkText,
			final String headingText, final Path screenshotDir, final String screenshotName) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeClickHandles = driver.getWindowHandles();
		clickOneOfTexts(driver, wait, Arrays.asList(linkText));
		waitForUiToLoad(driver, timeoutSeconds());

		boolean openedNewTab = false;
		final Instant start = Instant.now();
		while (Duration.between(start, Instant.now()).getSeconds() < timeoutSeconds()) {
			final Set<String> handles = driver.getWindowHandles();
			if (handles.size() > beforeClickHandles.size()) {
				for (final String handle : handles) {
					if (!beforeClickHandles.contains(handle)) {
						driver.switchTo().window(handle);
						openedNewTab = true;
						break;
					}
				}
				break;
			}
			if (isTextVisible(driver, headingText, Duration.ofSeconds(1))) {
				break;
			}
		}

		waitForVisibleByText(driver, headingText, Duration.ofSeconds(timeoutSeconds()));
		waitForAnyVisible(driver, new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds())),
				By.xpath("//p[string-length(normalize-space()) > 40]"),
				By.xpath("//*[string-length(normalize-space()) > 100]"));
		captureScreenshot(driver, screenshotDir, screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad(driver, timeoutSeconds());
		waitForAnyVisible(driver, wait, By.xpath("//*[normalize-space()='" + INFORMACION_GENERAL + "']"),
				By.xpath("//*[normalize-space()='" + SECCION_LEGAL + "']"));
		return finalUrl;
	}

	private void waitForUiToLoad(final WebDriver driver, final int timeoutSeconds) {
		final WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		wait.until((ExpectedCondition<Boolean>) d -> {
			if (d == null) {
				return false;
			}
			final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return Objects.equals("complete", String.valueOf(readyState));
		});
	}

	private void clickOneOfTexts(final WebDriver driver, final WebDriverWait wait, final List<String> texts) {
		Exception lastError = null;
		for (final String text : texts) {
			try {
				final WebElement target = waitForVisibleByText(driver, text, Duration.ofSeconds(8));
				clickElement(driver, wait, target);
				waitForUiToLoad(driver, timeoutSeconds());
				return;
			} catch (final Exception e) {
				lastError = e;
			}
		}
		throw new IllegalStateException("Could not click any element with visible texts: " + texts, lastError);
	}

	private void clickElement(final WebDriver driver, final WebDriverWait wait, final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
			return;
		} catch (final Exception ignored) {
			// fallback below
		}

		try {
			final WebElement clickableAncestor = element.findElement(
					By.xpath("./ancestor-or-self::*[self::button or self::a or @role='button' or @tabindex][1]"));
			wait.until(ExpectedConditions.elementToBeClickable(clickableAncestor)).click();
			return;
		} catch (final Exception ignored) {
			// fallback below
		}

		((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
	}

	private WebElement waitForVisibleByText(final WebDriver driver, final String text, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		return shortWait.until(d -> findVisibleByText(d, text));
	}

	private WebElement findVisibleByText(final WebDriver driver, final String text) {
		final List<WebElement> allCandidates = new ArrayList<>();
		allCandidates.addAll(driver.findElements(By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]")));
		allCandidates.addAll(driver.findElements(By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]")));

		for (final WebElement element : allCandidates) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		return null;
	}

	private WebElement findFirstVisible(final WebDriver driver, final By... selectors) {
		for (final By selector : selectors) {
			for (final WebElement element : driver.findElements(selector)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		return null;
	}

	private boolean isTextVisible(final WebDriver driver, final String text, final Duration timeout) {
		try {
			waitForVisibleByText(driver, text, timeout);
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private void waitForAnyVisible(final WebDriver driver, final WebDriverWait wait, final By... selectors) {
		wait.until(d -> {
			for (final By selector : selectors) {
				final List<WebElement> elements = d.findElements(selector);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			}
			return false;
		});
	}

	private void selectGoogleAccountIfShown(final WebDriver driver, final String email) {
		final Instant start = Instant.now();
		while (Duration.between(start, Instant.now()).getSeconds() < timeoutSeconds()) {
			for (final String handle : new LinkedHashSet<>(driver.getWindowHandles())) {
				driver.switchTo().window(handle);
				if (isTextVisible(driver, email, Duration.ofSeconds(1))) {
					final WebElement account = waitForVisibleByText(driver, email, Duration.ofSeconds(5));
					account.click();
					waitForUiToLoad(driver, timeoutSeconds());
					return;
				}
			}
		}
	}

	private void switchToWindowContainingApp(final WebDriver driver) {
		final Instant start = Instant.now();
		while (Duration.between(start, Instant.now()).getSeconds() < timeoutSeconds()) {
			for (final String handle : new LinkedHashSet<>(driver.getWindowHandles())) {
				driver.switchTo().window(handle);
				if (isTextVisible(driver, "Negocio", Duration.ofSeconds(1))
						|| isTextVisible(driver, "Mi Negocio", Duration.ofSeconds(1))
						|| !driver.findElements(By.xpath("//aside | //nav")).isEmpty()) {
					return;
				}
			}
		}
	}

	private WebElement findSectionByHeading(final WebDriver driver, final String headingText) {
		final WebElement heading = waitForVisibleByText(driver, headingText, Duration.ofSeconds(timeoutSeconds()));
		return heading.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
	}

	private boolean hasNameLikeLine(final String sectionText, final String expectedUserName) {
		if (!isBlank(expectedUserName)) {
			return sectionText.contains(expectedUserName);
		}

		final String[] ignoredTokens = new String[] { INFORMACION_GENERAL.toLowerCase(), "business plan", "cambiar plan",
				"cuenta creada", "estado activo", "idioma seleccionado", "tus negocios", "seccion legal" };
		final String[] lines = sectionText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine == null ? "" : rawLine.trim();
			if (line.length() < 3 || EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}

			boolean ignore = false;
			for (final String token : ignoredTokens) {
				if (line.toLowerCase().contains(token)) {
					ignore = true;
					break;
				}
			}
			if (!ignore) {
				return true;
			}
		}
		return false;
	}

	private Path prepareScreenshotDir() throws Exception {
		final Path dir = Paths.get("target", "saleads-evidence", TEST_NAME);
		Files.createDirectories(dir);
		return dir;
	}

	private void captureScreenshot(final WebDriver driver, final Path screenshotDir, final String checkpointName)
			throws Exception {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String fileName = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-") + "_"
				+ checkpointName + ".png";
		Files.copy(source.toPath(), screenshotDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private Map<String, StepResult> initializeReport() {
		final Map<String, StepResult> report = new LinkedHashMap<>();
		for (final String field : REPORT_FIELDS) {
			report.put(field, StepResult.notRun());
		}
		return report;
	}

	private void printFinalReport(final Map<String, StepResult> report, final String termsUrl, final String privacyUrl,
			final Path screenshotDir) {
		System.out.println("\n=== " + TEST_NAME + " - Final Report ===");
		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			System.out.println(field + ": " + result.state + (isBlank(result.detail) ? "" : " - " + result.detail));
		}
		System.out.println("Terminos y Condiciones URL: " + termsUrl);
		System.out.println("Politica de Privacidad URL: " + privacyUrl);
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
	}

	private void assertAllStepsPassed(final Map<String, StepResult> report) {
		final List<String> failures = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			if (result.state != StepState.PASS) {
				failures.add(field + " -> " + result.state + (isBlank(result.detail) ? "" : " (" + result.detail + ")"));
			}
		}
		if (!failures.isEmpty()) {
			fail("Some validation steps failed: " + failures);
		}
	}

	private int timeoutSeconds() {
		final String configured = readConfig("saleads.ui.timeout.seconds", "SALEADS_UI_TIMEOUT_SECONDS");
		if (isBlank(configured)) {
			return 35;
		}
		try {
			return Integer.parseInt(configured);
		} catch (final NumberFormatException e) {
			return 35;
		}
	}

	private String readConfig(final String systemPropertyName, final String envName) {
		final String fromProperty = System.getProperty(systemPropertyName);
		if (!isBlank(fromProperty)) {
			return fromProperty.trim();
		}
		final String fromEnv = System.getenv(envName);
		return isBlank(fromEnv) ? null : fromEnv.trim();
	}

	private String compactError(final Exception e) {
		if (e == null) {
			return "Unknown error";
		}
		final String message = e.getMessage();
		if (!isBlank(message)) {
			return message;
		}
		return e.getClass().getSimpleName();
	}

	private String xpathLiteral(final String text) {
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

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private enum StepState {
		PASS, FAIL, NOT_RUN
	}

	private static class StepResult {
		private final StepState state;
		private final String detail;

		private StepResult(final StepState state, final String detail) {
			this.state = state;
			this.detail = detail;
		}

		private static StepResult pass() {
			return new StepResult(StepState.PASS, "");
		}

		private static StepResult fail(final String detail) {
			return new StepResult(StepState.FAIL, detail);
		}

		private static StepResult notRun() {
			return new StepResult(StepState.NOT_RUN, "Not executed");
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
