package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end test for the SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * The test is environment-agnostic and uses visible labels/text instead of hardcoded domains.
 * Configure runtime values with system properties or environment variables:
 * </p>
 *
 * <ul>
 * <li>saleads.e2e.enabled / SALEADS_E2E_ENABLED = true</li>
 * <li>saleads.login.url / SALEADS_LOGIN_URL (login page URL for the current environment)</li>
 * <li>saleads.google.email / SALEADS_GOOGLE_EMAIL (defaults to juanlucasbarbiergarzon@gmail.com)</li>
 * <li>saleads.headless / SALEADS_HEADLESS (defaults to false)</li>
 * <li>saleads.timeout.seconds / SALEADS_TIMEOUT_SECONDS (defaults to 30)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final String LOGIN_FIELD = "Login";
	private static final String MI_NEGOCIO_MENU_FIELD = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL_FIELD = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW_FIELD = "Administrar Negocios view";
	private static final String INFO_GENERAL_FIELD = "Información General";
	private static final String DETALLES_CUENTA_FIELD = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS_FIELD = "Tus Negocios";
	private static final String TERMINOS_FIELD = "Términos y Condiciones";
	private static final String PRIVACIDAD_FIELD = "Política de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String expectedGoogleEmail;
	private String terminosFinalUrl;
	private String privacidadFinalUrl;
	private final Map<String, StepStatus> finalReport = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final boolean enabled = getBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false);
		Assume.assumeTrue("Set saleads.e2e.enabled=true (or SALEADS_E2E_ENABLED=true) to run this E2E test.", enabled);

		final String loginUrl = getConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set saleads.login.url (or SALEADS_LOGIN_URL) to the login URL in the target SaleADS environment.",
				loginUrl != null && !loginUrl.isBlank());

		expectedGoogleEmail = Optional.ofNullable(getConfig("saleads.google.email", "SALEADS_GOOGLE_EMAIL"))
				.filter(value -> !value.isBlank())
				.orElse("juanlucasbarbiergarzon@gmail.com");

		final boolean headless = getBooleanConfig("saleads.headless", "SALEADS_HEADLESS", false);
		final int timeoutSeconds = getIntConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", 30);

		final ChromeOptions chromeOptions = new ChromeOptions();
		chromeOptions.addArguments("--window-size=1600,1200");
		chromeOptions.addArguments("--disable-notifications");
		chromeOptions.addArguments("--disable-popup-blocking");
		chromeOptions.addArguments("--lang=es-ES");

		if (headless) {
			chromeOptions.addArguments("--headless=new");
		}

		driver = new ChromeDriver(chromeOptions);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		evidenceDir = Paths.get("target", "saleads-evidence",
				DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now()));
		Files.createDirectories(evidenceDir);

		driver.get(loginUrl);
		waitForDocumentReady();
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflowTest() throws IOException {
		// Step 1
		if (runStep(LOGIN_FIELD, this::stepLoginWithGoogle)) {
			// Step 2
			if (runStep(MI_NEGOCIO_MENU_FIELD, this::stepOpenMiNegocioMenu)) {
				// Step 3
				runStep(AGREGAR_NEGOCIO_MODAL_FIELD, this::stepValidateAgregarNegocioModal);
			} else {
				markBlocked(AGREGAR_NEGOCIO_MODAL_FIELD, "Mi Negocio menu did not open.");
			}

			// Step 4
			if (runStep(ADMINISTRAR_NEGOCIOS_VIEW_FIELD, this::stepOpenAdministrarNegocios)) {
				// Step 5
				runStep(INFO_GENERAL_FIELD, this::stepValidateInformacionGeneral);
				// Step 6
				runStep(DETALLES_CUENTA_FIELD, this::stepValidateDetallesCuenta);
				// Step 7
				runStep(TUS_NEGOCIOS_FIELD, this::stepValidateTusNegocios);
				// Step 8
				runStep(TERMINOS_FIELD, () -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones",
						"08-terminos-y-condiciones"));
				// Step 9
				runStep(PRIVACIDAD_FIELD,
						() -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad",
								"09-politica-de-privacidad"));
			} else {
				markBlocked(INFO_GENERAL_FIELD, "Administrar Negocios page did not load.");
				markBlocked(DETALLES_CUENTA_FIELD, "Administrar Negocios page did not load.");
				markBlocked(TUS_NEGOCIOS_FIELD, "Administrar Negocios page did not load.");
				markBlocked(TERMINOS_FIELD, "Administrar Negocios page did not load.");
				markBlocked(PRIVACIDAD_FIELD, "Administrar Negocios page did not load.");
			}
		} else {
			markBlocked(MI_NEGOCIO_MENU_FIELD, "Login failed.");
			markBlocked(AGREGAR_NEGOCIO_MODAL_FIELD, "Login failed.");
			markBlocked(ADMINISTRAR_NEGOCIOS_VIEW_FIELD, "Login failed.");
			markBlocked(INFO_GENERAL_FIELD, "Login failed.");
			markBlocked(DETALLES_CUENTA_FIELD, "Login failed.");
			markBlocked(TUS_NEGOCIOS_FIELD, "Login failed.");
			markBlocked(TERMINOS_FIELD, "Login failed.");
			markBlocked(PRIVACIDAD_FIELD, "Login failed.");
		}

		assertFalse("At least one required validation failed. See target/saleads-evidence report.", hasFailedSteps());
	}

	private void stepLoginWithGoogle() throws IOException {
		clickFirstAvailableText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Login con Google", "Entrar con Google");

		completeGoogleAccountSelectionIfShown(expectedGoogleEmail);

		waitForAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Panel", "Inicio");

		assertTrue("Main application interface was not detected.", isAnyVisible(By.tagName("aside"),
				By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar') or contains(@id,'sidebar')]")));
		assertTrue("Left sidebar navigation was not detected.",
				isAnyVisible(By.xpath("//aside//*[normalize-space()='Negocio']"),
						By.xpath("//nav//*[normalize-space()='Negocio']"),
						By.xpath("//*[contains(@class,'sidebar')]//*[normalize-space()='Negocio']")));

		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		expandMiNegocioMenu();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");

		takeScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickVisibleText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");

		assertTrue("Expected input field 'Nombre del Negocio' not found.",
				isAnyVisible(By.xpath(
						"//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
						By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
						By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
						By.xpath("//input[contains(@name,'nombre')]")));
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final WebElement nameInput = firstVisibleElement(By.xpath(
				"//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
				By.xpath("//input[contains(@name,'nombre')]"));

		nameInput.click();
		nameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
		nameInput.sendKeys("Negocio Prueba Automatización");

		takeScreenshot("03-agregar-negocio-modal");

		clickVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']")));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioMenu();
		clickVisibleText("Administrar Negocios");

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");

		takeScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		final String sectionText = sectionTextByHeading("Información General");
		assertTrue("User email is not visible in Información General.", EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("Expected 'BUSINESS PLAN' text not found in Información General.",
				sectionText.contains("BUSINESS PLAN"));
		assertTrue("Expected 'Cambiar Plan' button/text not found in Información General.",
				sectionText.contains("Cambiar Plan"));

		final List<String> candidateLines = meaningfulLines(sectionText);
		assertTrue("A likely user name was not detected in Información General section.",
				candidateLines.stream()
						.anyMatch(line -> !line.contains("@") && !line.contains("BUSINESS PLAN")
								&& !line.contains("Cambiar Plan") && line.length() >= 5));
	}

	private void stepValidateDetallesCuenta() {
		final String sectionText = sectionTextByHeading("Detalles de la Cuenta");
		assertTrue("'Cuenta creada' is not visible.", sectionText.contains("Cuenta creada"));
		assertTrue("'Estado activo' is not visible.", sectionText.contains("Estado activo"));
		assertTrue("'Idioma seleccionado' is not visible.", sectionText.contains("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final String sectionText = sectionTextByHeading("Tus Negocios");
		assertTrue("Business list section text is unexpectedly empty.", meaningfulLines(sectionText).size() >= 3);
		assertTrue("'Agregar Negocio' button/text was not visible in Tus Negocios section.",
				sectionText.contains("Agregar Negocio"));
		assertTrue("'Tienes 2 de 3 negocios' was not visible in Tus Negocios section.",
				sectionText.contains("Tienes 2 de 3 negocios"));
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String currentUrlBeforeClick = driver.getCurrentUrl();
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickVisibleText(linkText);

		wait.until((ExpectedCondition<Boolean>) webDriver -> {
			final boolean newTabOpened = webDriver.getWindowHandles().size() > handlesBeforeClick.size();
			final boolean headingVisible = isTextVisible(expectedHeading);
			final boolean urlChanged = !Objects.equals(currentUrlBeforeClick, webDriver.getCurrentUrl());
			return newTabOpened || headingVisible || urlChanged;
		});

		final Set<String> handlesAfterClick = driver.getWindowHandles();
		boolean openedNewTab = handlesAfterClick.size() > handlesBeforeClick.size();
		if (openedNewTab) {
			for (final String handle : handlesAfterClick) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForDocumentReady();
		}

		assertVisibleText(expectedHeading);

		final String pageText = safeBodyText();
		assertTrue("Legal content text is not visible for " + linkText + ".",
				meaningfulLines(pageText).size() >= 4 || pageText.length() > 300);

		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();
		if ("Términos y Condiciones".equals(linkText)) {
			terminosFinalUrl = finalUrl;
		} else if ("Política de Privacidad".equals(linkText)) {
			privacidadFinalUrl = finalUrl;
		}

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForDocumentReady();
		} else {
			driver.navigate().back();
			waitForDocumentReady();
		}

		assertTrue("Could not return to application context after validating " + linkText + ".",
				isAnyVisible(By.xpath("//*[normalize-space()='Sección Legal']"),
						By.xpath("//*[normalize-space()='Administrar Negocios']"),
						By.xpath("//*[normalize-space()='Información General']")));
	}

	private void expandMiNegocioMenu() {
		if (isTextVisible("Agregar Negocio") && isTextVisible("Administrar Negocios")) {
			return;
		}

		if (isTextVisible("Negocio")) {
			clickVisibleText("Negocio");
		}

		clickVisibleText("Mi Negocio");
		waitForDocumentReady();
	}

	private void completeGoogleAccountSelectionIfShown(final String accountEmail) {
		final Set<String> handlesBefore = driver.getWindowHandles();

		waitUntilAnyTextOrTimeout(8, accountEmail, "Elige una cuenta", "Choose an account", "Usa otra cuenta",
				"Use another account");

		Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > handlesBefore.size()) {
			for (final String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForDocumentReady();
		}

		if (isTextVisible("Elige una cuenta") || isTextVisible("Choose an account") || isTextVisible(accountEmail)) {
			clickVisibleText(accountEmail);
			waitForDocumentReady();
		}

		handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > 1) {
			for (final String handle : handlesAfter) {
				if (!handle.equals(driver.getWindowHandle())) {
					driver.switchTo().window(handle);
					waitForDocumentReady();
					break;
				}
			}
		}
	}

	private boolean runStep(final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			finalReport.put(stepName, StepStatus.pass("PASS"));
			return true;
		} catch (final Throwable throwable) {
			finalReport.put(stepName, StepStatus.fail("FAIL: " + throwable.getMessage()));
			return false;
		}
	}

	private void markBlocked(final String stepName, final String reason) {
		finalReport.put(stepName, StepStatus.fail("FAIL (blocked): " + reason));
	}

	private boolean hasFailedSteps() {
		for (final StepStatus status : finalReport.values()) {
			if (!status.passed) {
				return true;
			}
		}
		return false;
	}

	private void writeFinalReport() throws IOException {
		if (finalReport.isEmpty()) {
			return;
		}

		final List<String> orderedFields = Arrays.asList(LOGIN_FIELD, MI_NEGOCIO_MENU_FIELD, AGREGAR_NEGOCIO_MODAL_FIELD,
				ADMINISTRAR_NEGOCIOS_VIEW_FIELD, INFO_GENERAL_FIELD, DETALLES_CUENTA_FIELD, TUS_NEGOCIOS_FIELD,
				TERMINOS_FIELD, PRIVACIDAD_FIELD);

		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test - Final Report");
		lines.add("Evidence directory: " + evidenceDir.toAbsolutePath());
		lines.add("");

		System.out.println("========== saleads_mi_negocio_full_test FINAL REPORT ==========");
		for (final String field : orderedFields) {
			final StepStatus status = finalReport.getOrDefault(field, StepStatus.fail("FAIL: Not executed."));
			String details = status.details;
			if (TERMINOS_FIELD.equals(field) && terminosFinalUrl != null) {
				details += " | Final URL: " + terminosFinalUrl;
			} else if (PRIVACIDAD_FIELD.equals(field) && privacidadFinalUrl != null) {
				details += " | Final URL: " + privacidadFinalUrl;
			}

			final String line = field + ": " + (status.passed ? "PASS" : "FAIL") + " - " + details;
			lines.add(line);
			System.out.println(line);
		}
		System.out.println("===============================================================");

		Files.write(evidenceDir.resolve("final-report.txt"), lines);
	}

	private void clickFirstAvailableText(final String... labels) {
		Throwable lastFailure = null;
		for (final String label : labels) {
			try {
				clickVisibleText(label);
				return;
			} catch (final Throwable throwable) {
				lastFailure = throwable;
			}
		}

		throw new AssertionError("None of the target labels were clickable: " + Arrays.toString(labels), lastFailure);
	}

	private void clickVisibleText(final String label) {
		final List<By> locators = Arrays.asList(By.xpath("//button[normalize-space()=" + asXpathLiteral(label) + "]"),
				By.xpath("//a[normalize-space()=" + asXpathLiteral(label) + "]"),
				By.xpath("//*[(@role='button' or @role='menuitem') and normalize-space()=" + asXpathLiteral(label) + "]"),
				By.xpath("//*[normalize-space()=" + asXpathLiteral(label) + "]"));

		Throwable lastFailure = null;
		for (final By locator : locators) {
			try {
				final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				wait.until(ExpectedConditions.elementToBeClickable(element));
				safeClick(element);
				waitForDocumentReady();
				return;
			} catch (final Throwable throwable) {
				lastFailure = throwable;
			}
		}

		throw new AssertionError("Could not click visible text: " + label, lastFailure);
	}

	private void safeClick(final WebElement element) {
		try {
			element.click();
		} catch (final Throwable clickError) {
			try {
				new Actions(driver).moveToElement(element).click().perform();
			} catch (final Throwable actionClickError) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			}
		}
	}

	private void waitForAnyVisibleText(final String... labels) {
		wait.until(webDriver -> {
			for (final String label : labels) {
				if (isTextVisible(label)) {
					return true;
				}
			}
			return false;
		});
	}

	private void waitUntilAnyTextOrTimeout(final int timeoutSeconds, final String... labels) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(webDriver -> {
				for (final String label : labels) {
					if (isTextVisible(label)) {
						return true;
					}
				}
				return false;
			});
		} catch (final TimeoutException ignored) {
			// Optional wait, continue without failing.
		}
	}

	private boolean isTextVisible(final String text) {
		try {
			final List<WebElement> elements = driver
					.findElements(By.xpath("//*[normalize-space()=" + asXpathLiteral(text) + "]"));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final NoSuchElementException | StaleElementReferenceException ignored) {
			return false;
		}
	}

	private void assertVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[normalize-space()=" + asXpathLiteral(text) + "]")));
	}

	private boolean isAnyVisible(final By... locators) {
		for (final By locator : locators) {
			try {
				for (final WebElement element : driver.findElements(locator)) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			} catch (final Throwable ignored) {
				// Ignore and try next locator.
			}
		}
		return false;
	}

	private WebElement firstVisibleElement(final By... locators) {
		for (final By locator : locators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		throw new AssertionError("No visible element found for locators.");
	}

	private String sectionTextByHeading(final String headingText) {
		final String xpath = "//*[normalize-space()=" + asXpathLiteral(headingText)
				+ "]/ancestor::*[self::section or self::div][1]";
		final WebElement headingContainer = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
		return headingContainer.getText();
	}

	private List<String> meaningfulLines(final String text) {
		final List<String> lines = new ArrayList<>();
		for (final String rawLine : text.split("\\R")) {
			final String line = rawLine == null ? "" : rawLine.trim();
			if (!line.isEmpty()) {
				lines.add(line);
			}
		}
		return lines;
	}

	private String safeBodyText() {
		try {
			return Optional.ofNullable(driver.findElement(By.tagName("body")).getText()).orElse("");
		} catch (final Throwable ignored) {
			return "";
		}
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final byte[] imageBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final String fileName = checkpointName + ".png";
		Files.write(evidenceDir.resolve(fileName), imageBytes);
	}

	private void waitForDocumentReady() {
		wait.until(webDriver -> {
			try {
				final Object readyState = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
				return "complete".equals(String.valueOf(readyState));
			} catch (final Throwable ignored) {
				return true;
			}
		});
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < value.length(); i++) {
			final String character = String.valueOf(value.charAt(i));
			if (i > 0) {
				builder.append(",");
			}
			if ("'".equals(character)) {
				builder.append("\"'\"");
			} else if ("\"".equals(character)) {
				builder.append("'\"'");
			} else {
				builder.append("'").append(character).append("'");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String getConfig(final String propertyKey, final String envKey) {
		final String value = System.getProperty(propertyKey);
		if (value != null && !value.isBlank()) {
			return value;
		}
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return null;
	}

	private boolean getBooleanConfig(final String propertyKey, final String envKey, final boolean defaultValue) {
		final String value = getConfig(propertyKey, envKey);
		return value == null ? defaultValue : Boolean.parseBoolean(value);
	}

	private int getIntConfig(final String propertyKey, final String envKey, final int defaultValue) {
		final String value = getConfig(propertyKey, envKey);
		if (value == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value);
		} catch (final NumberFormatException exception) {
			return defaultValue;
		}
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepStatus {
		private final boolean passed;
		private final String details;

		private StepStatus(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepStatus pass(final String details) {
			return new StepStatus(true, details);
		}

		private static StepStatus fail(final String details) {
			return new StepStatus(false, details);
		}
	}
}
