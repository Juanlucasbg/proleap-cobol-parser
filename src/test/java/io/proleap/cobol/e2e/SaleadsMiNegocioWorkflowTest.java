package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.After;
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

public class SaleadsMiNegocioWorkflowTest {

	private static final String EMAIL_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Informacion General";
	private static final String REPORT_DETALLES = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Terminos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Politica de Privacidad";

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);

	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDirectory;
	private String termsUrl = "";
	private String privacyUrl = "";

	@Before
	public void setUp() throws IOException {
		final String loginUrl = readEnv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to execute SaleADS workflow test.", !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(readEnvOrDefault("SALEADS_HEADLESS", "true"));
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		screenshotsDirectory = createScreenshotDirectory();
		initializeReport();

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		executeLoginStep();
		executeMiNegocioMenuStep();
		executeAgregarNegocioModalStep();
		executeAdministrarNegociosViewStep();
		executeInformacionGeneralStep();
		executeDetallesCuentaStep();
		executeTusNegociosStep();
		executeTerminosStep();
		executePrivacidadStep();
		writeFinalReport();
		assertAllStepsPassed();
	}

	private void executeLoginStep() {
		final String stepName = REPORT_LOGIN;

		try {
			if (!leftSidebarVisible()) {
				final Set<String> previousHandles = driver.getWindowHandles();
				clickByVisibleText(
						List.of("Sign in with Google", "Login with Google", "Iniciar sesion con Google", "Continuar con Google"));
				switchToGoogleWindowIfOpened(previousHandles);
				selectGoogleAccountIfVisible();
				switchBackToApplicationWindow();
			}

			wait.until(d -> leftSidebarVisible());
			assertTrue("Main application interface was not displayed.", leftSidebarVisible());
			assertTrue("Left sidebar is not visible.",
					isAnyTextVisible(List.of("Mi Negocio", "Negocio", "Dashboard", "Inicio"), SHORT_TIMEOUT));
			takeScreenshot("01-dashboard-loaded");
			markStepPassed(stepName);
		} catch (final Throwable ex) {
			markStepFailed(stepName, ex);
		}
	}

	private void executeMiNegocioMenuStep() {
		final String stepName = REPORT_MENU;

		try {
			clickByVisibleText(List.of("Negocio"));
			clickByVisibleText(List.of("Mi Negocio"));

			assertTrue("Agregar Negocio option is missing.",
					isAnyTextVisible(List.of("Agregar Negocio"), DEFAULT_TIMEOUT));
			assertTrue("Administrar Negocios option is missing.",
					isAnyTextVisible(List.of("Administrar Negocios"), DEFAULT_TIMEOUT));
			takeScreenshot("02-mi-negocio-menu-expanded");
			markStepPassed(stepName);
		} catch (final Throwable ex) {
			markStepFailed(stepName, ex);
		}
	}

	private void executeAgregarNegocioModalStep() {
		final String stepName = REPORT_AGREGAR_MODAL;

		try {
			clickByVisibleText(List.of("Agregar Negocio"));
			waitForUiToLoad();

			assertTrue("Modal title was not found.",
					isAnyTextVisible(List.of("Crear Nuevo Negocio"), DEFAULT_TIMEOUT));
			assertTrue("Nombre del Negocio input was not found.", existsVisibleElement(
					By.xpath("//input[contains(@placeholder,'Nombre del Negocio') or @name='businessName']"), DEFAULT_TIMEOUT)
					|| isAnyTextVisible(List.of("Nombre del Negocio"), DEFAULT_TIMEOUT));
			assertTrue("Business quota text is missing.",
					isAnyTextVisible(List.of("Tienes 2 de 3 negocios"), DEFAULT_TIMEOUT));
			assertTrue("Cancelar button is missing.", isAnyTextVisible(List.of("Cancelar"), DEFAULT_TIMEOUT));
			assertTrue("Crear Negocio button is missing.", isAnyTextVisible(List.of("Crear Negocio"), DEFAULT_TIMEOUT));

			takeScreenshot("03-agregar-negocio-modal");

			final WebElement nameInput = findOptionalVisibleElement(By.xpath(
					"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio') or @name='businessName']"),
					SHORT_TIMEOUT);
			if (nameInput != null) {
				nameInput.click();
				nameInput.clear();
				nameInput.sendKeys("Negocio Prueba Automatizacion");
				waitForUiToLoad();
			}

			clickByVisibleText(List.of("Cancelar"));
			markStepPassed(stepName);
		} catch (final Throwable ex) {
			markStepFailed(stepName, ex);
		}
	}

	private void executeAdministrarNegociosViewStep() {
		final String stepName = REPORT_ADMIN_VIEW;

		try {
			if (!isAnyTextVisible(List.of("Administrar Negocios"), SHORT_TIMEOUT)) {
				clickByVisibleText(List.of("Mi Negocio"));
			}

			clickByVisibleText(List.of("Administrar Negocios"));
			waitForUiToLoad();

			assertTrue("Informacion General section is missing.",
					isAnyTextVisible(List.of("Informacion General", "Informaci\u00f3n General"), DEFAULT_TIMEOUT));
			assertTrue("Detalles de la Cuenta section is missing.",
					isAnyTextVisible(List.of("Detalles de la Cuenta"), DEFAULT_TIMEOUT));
			assertTrue("Tus Negocios section is missing.", isAnyTextVisible(List.of("Tus Negocios"), DEFAULT_TIMEOUT));
			assertTrue("Seccion Legal section is missing.",
					isAnyTextVisible(List.of("Seccion Legal", "Secci\u00f3n Legal"), DEFAULT_TIMEOUT));

			takeScreenshot("04-administrar-negocios");
			markStepPassed(stepName);
		} catch (final Throwable ex) {
			markStepFailed(stepName, ex);
		}
	}

	private void executeInformacionGeneralStep() {
		final String stepName = REPORT_INFO_GENERAL;

		try {
			final String expectedName = readEnv("SALEADS_EXPECTED_USER_NAME");
			final String expectedEmail = readEnvOrDefault("SALEADS_EXPECTED_USER_EMAIL", EMAIL_ACCOUNT);

			final boolean userNameVisible = !expectedName.isBlank() ? isAnyTextVisible(List.of(expectedName), SHORT_TIMEOUT)
					: isAnyTextVisible(List.of("Nombre", "Usuario", "Name"), SHORT_TIMEOUT);
			final boolean userEmailVisible = isAnyTextVisible(List.of(expectedEmail), SHORT_TIMEOUT)
					|| existsVisibleElement(By.xpath("//*[contains(normalize-space(.), '@')]"), SHORT_TIMEOUT);

			assertTrue("User name was not visible in Informacion General.", userNameVisible);
			assertTrue("User email was not visible in Informacion General.", userEmailVisible);
			assertTrue("BUSINESS PLAN text is missing.", isAnyTextVisible(List.of("BUSINESS PLAN"), SHORT_TIMEOUT));
			assertTrue("Cambiar Plan button is missing.",
					isAnyTextVisible(List.of("Cambiar Plan"), SHORT_TIMEOUT));

			markStepPassed(stepName);
		} catch (final Throwable ex) {
			markStepFailed(stepName, ex);
		}
	}

	private void executeDetallesCuentaStep() {
		final String stepName = REPORT_DETALLES;

		try {
			assertTrue("Cuenta creada text is missing.",
					isAnyTextVisible(List.of("Cuenta creada"), SHORT_TIMEOUT));
			assertTrue("Estado activo text is missing.",
					isAnyTextVisible(List.of("Estado activo"), SHORT_TIMEOUT));
			assertTrue("Idioma seleccionado text is missing.",
					isAnyTextVisible(List.of("Idioma seleccionado"), SHORT_TIMEOUT));
			markStepPassed(stepName);
		} catch (final Throwable ex) {
			markStepFailed(stepName, ex);
		}
	}

	private void executeTusNegociosStep() {
		final String stepName = REPORT_TUS_NEGOCIOS;

		try {
			assertTrue("Tus Negocios section header is missing.",
					isAnyTextVisible(List.of("Tus Negocios"), SHORT_TIMEOUT));
			assertTrue("Agregar Negocio button is missing in Tus Negocios section.",
					isAnyTextVisible(List.of("Agregar Negocio"), SHORT_TIMEOUT));
			assertTrue("Business quota text is missing in Tus Negocios section.",
					isAnyTextVisible(List.of("Tienes 2 de 3 negocios"), SHORT_TIMEOUT));
			assertTrue("Business list is not visible.",
					existsVisibleElement(By.xpath("//*[contains(@class,'business') or self::li or self::table or self::tr]"),
							SHORT_TIMEOUT));
			markStepPassed(stepName);
		} catch (final Throwable ex) {
			markStepFailed(stepName, ex);
		}
	}

	private void executeTerminosStep() {
		final String stepName = REPORT_TERMINOS;

		try {
			termsUrl = openAndValidateLegalLink(List.of("Terminos y Condiciones", "T\u00e9rminos y Condiciones"),
					List.of("Terminos y Condiciones", "T\u00e9rminos y Condiciones"), "05-terminos");
			markStepPassed(stepName);
		} catch (final Throwable ex) {
			markStepFailed(stepName, ex);
		}
	}

	private void executePrivacidadStep() {
		final String stepName = REPORT_PRIVACIDAD;

		try {
			privacyUrl = openAndValidateLegalLink(List.of("Politica de Privacidad", "Pol\u00edtica de Privacidad"),
					List.of("Politica de Privacidad", "Pol\u00edtica de Privacidad"), "06-politica-privacidad");
			markStepPassed(stepName);
		} catch (final Throwable ex) {
			markStepFailed(stepName, ex);
		}
	}

	private String openAndValidateLegalLink(final List<String> linkTexts, final List<String> expectedHeadings,
			final String screenshotName) throws IOException {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> oldHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkTexts);
		waitForUiToLoad();

		final String targetWindow = waitForTargetWindow(oldHandles);
		final boolean openedNewWindow = targetWindow != null;

		if (openedNewWindow) {
			driver.switchTo().window(targetWindow);
			waitForUiToLoad();
		}

		assertTrue("Expected legal heading was not found.", isAnyTextVisible(expectedHeadings, DEFAULT_TIMEOUT));
		assertTrue("No visible legal body content found.",
				existsVisibleElement(By.xpath("//p[string-length(normalize-space(.)) > 40]"), SHORT_TIMEOUT)
						|| existsVisibleElement(By.xpath("//article//*[string-length(normalize-space(.)) > 40]"),
								SHORT_TIMEOUT));

		final String finalUrl = driver.getCurrentUrl();
		takeScreenshot(screenshotName);

		if (openedNewWindow) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void switchToGoogleWindowIfOpened(final Set<String> previousHandles) {
		final String targetWindow = waitForTargetWindow(previousHandles);
		if (targetWindow != null) {
			driver.switchTo().window(targetWindow);
			waitForUiToLoad();
		}
	}

	private void switchBackToApplicationWindow() {
		final List<String> handles = new ArrayList<>(driver.getWindowHandles());
		for (final String handle : handles) {
			driver.switchTo().window(handle);
			if (!driver.getCurrentUrl().contains("accounts.google.com")) {
				waitForUiToLoad();
				return;
			}
		}
	}

	private void selectGoogleAccountIfVisible() {
		if (driver.getCurrentUrl().contains("accounts.google.com")
				|| isAnyTextVisible(List.of("Choose an account", "Elegir una cuenta"), SHORT_TIMEOUT)) {
			clickByVisibleText(List.of(EMAIL_ACCOUNT));
			waitForUiToLoad();
		}
	}

	private boolean leftSidebarVisible() {
		return existsVisibleElement(
				By.xpath("//aside | //nav[contains(@class,'sidebar')] | //div[contains(@class,'sidebar')]"),
				SHORT_TIMEOUT);
	}

	private void clickByVisibleText(final List<String> labels) {
		Objects.requireNonNull(labels, "labels");
		RuntimeException lastException = null;

		for (final String label : labels) {
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(
						By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(label) + ")]")));
				scrollIntoView(element);
				try {
					element.click();
				} catch (final Exception clickIssue) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				}
				waitForUiToLoad();
				return;
			} catch (final RuntimeException ex) {
				lastException = ex;
			}
		}

		if (lastException != null) {
			throw lastException;
		}
		throw new NoSuchElementException("No visible element found for labels: " + labels);
	}

	private boolean isAnyTextVisible(final List<String> texts, final Duration timeout) {
		for (final String text : texts) {
			if (existsVisibleElement(By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]"),
					timeout)) {
				return true;
			}
		}
		return false;
	}

	private boolean existsVisibleElement(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private WebElement findOptionalVisibleElement(final By locator, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final TimeoutException ex) {
			return null;
		}
	}

	private void waitForUiToLoad() {
		final ExpectedCondition<Boolean> pageLoadCondition = d -> "complete"
				.equals(((JavascriptExecutor) d).executeScript("return document.readyState"));
		wait.until(pageLoadCondition);
		try {
			Thread.sleep(350);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final Path outputPath = screenshotsDirectory.resolve(fileName + ".png");
		final Path screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(screenshot, outputPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private String waitForTargetWindow(final Set<String> previousHandles) {
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(d -> d.getWindowHandles().size() > previousHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!previousHandles.contains(handle)) {
					return handle;
				}
			}
		} catch (final TimeoutException ex) {
			return null;
		}
		return null;
	}

	private Path createScreenshotDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path directory = Paths.get("target", "screenshots", "saleads-mi-negocio", timestamp);
		Files.createDirectories(directory);
		return directory;
	}

	private void initializeReport() {
		stepResults.put(REPORT_LOGIN, false);
		stepResults.put(REPORT_MENU, false);
		stepResults.put(REPORT_AGREGAR_MODAL, false);
		stepResults.put(REPORT_ADMIN_VIEW, false);
		stepResults.put(REPORT_INFO_GENERAL, false);
		stepResults.put(REPORT_DETALLES, false);
		stepResults.put(REPORT_TUS_NEGOCIOS, false);
		stepResults.put(REPORT_TERMINOS, false);
		stepResults.put(REPORT_PRIVACIDAD, false);
	}

	private void markStepPassed(final String stepName) {
		stepResults.put(stepName, true);
	}

	private void markStepFailed(final String stepName, final Throwable ex) {
		stepResults.put(stepName, false);
		stepErrors.put(stepName, ex.getClass().getSimpleName() + ": " + ex.getMessage());
		try {
			takeScreenshot("failure-" + normalizeName(stepName));
		} catch (final IOException ioEx) {
			stepErrors.put(stepName, stepErrors.get(stepName) + " | screenshot error: " + ioEx.getMessage());
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio Full Test Report\n");
		reportBuilder.append("===================================\n");
		for (final Map.Entry<String, Boolean> entry : stepResults.entrySet()) {
			reportBuilder.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL");
			final String error = stepErrors.get(entry.getKey());
			if (error != null) {
				reportBuilder.append(" (").append(error).append(")");
			}
			reportBuilder.append('\n');
		}
		reportBuilder.append('\n');
		reportBuilder.append("Terminos URL: ").append(termsUrl.isBlank() ? "N/A" : termsUrl).append('\n');
		reportBuilder.append("Politica URL: ").append(privacyUrl.isBlank() ? "N/A" : privacyUrl).append('\n');
		reportBuilder.append("Screenshots directory: ").append(screenshotsDirectory.toAbsolutePath()).append('\n');

		final Path reportPath = screenshotsDirectory.resolve("final-report.txt");
		Files.writeString(reportPath, reportBuilder.toString());
		System.out.println(reportBuilder);
	}

	private void assertAllStepsPassed() {
		for (final Map.Entry<String, Boolean> entry : stepResults.entrySet()) {
			if (!entry.getValue()) {
				fail("Workflow failed. See final report under " + screenshotsDirectory.toAbsolutePath());
			}
		}
	}

	private static String normalizeName(final String raw) {
		return raw.toLowerCase().replace(' ', '-');
	}

	private static String readEnv(final String name) {
		final String value = System.getenv(name);
		return value == null ? "" : value.trim();
	}

	private static String readEnvOrDefault(final String name, final String defaultValue) {
		final String value = readEnv(name);
		return value.isBlank() ? defaultValue : value;
	}

	private static String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder literal = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char c = chars[i];
			if (c == '\'') {
				literal.append("\"'\"");
			} else if (c == '\"') {
				literal.append("'\"'");
			} else {
				literal.append("'").append(c).append("'");
			}
			if (i < chars.length - 1) {
				literal.append(',');
			}
		}
		literal.append(')');
		return literal.toString();
	}
}
