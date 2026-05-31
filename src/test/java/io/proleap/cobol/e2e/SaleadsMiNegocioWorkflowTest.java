package io.proleap.cobol.e2e;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String AGREGAR_NEGOCIO = "Agregar Negocio";

	private static final String ADMINISTRAR_NEGOCIOS = "Administrar Negocios";

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);

	private static final String INFORMACION_GENERAL = "Informaci\u00f3n General";

	private static final String NOMBRE_NEGOCIO = "Nombre del Negocio";

	private static final String POLITICA_DE_PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private static final String REPORTE_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";

	private static final String REPORTE_AGREGAR_NEGOCIO = "Agregar Negocio modal";

	private static final String REPORTE_DETALLES_CUENTA = "Detalles de la Cuenta";

	private static final String REPORTE_INFO_GENERAL = "Informaci\u00f3n General";

	private static final String REPORTE_LOGIN = "Login";

	private static final String REPORTE_MI_NEGOCIO_MENU = "Mi Negocio menu";

	private static final String REPORTE_POLITICA = "Pol\u00edtica de Privacidad";

	private static final String REPORTE_TERMINOS = "T\u00e9rminos y Condiciones";

	private static final String REPORTE_TUS_NEGOCIOS = "Tus Negocios";

	private static final String TERMINOS_Y_CONDICIONES = "T\u00e9rminos y Condiciones";

	private static final String TEXTO_CAPACIDAD_NEGOCIOS = "Tienes 2 de 3 negocios";

	private static final String TUS_NEGOCIOS = "Tus Negocios";

	private static final String USER_EMAIL_DEFAULT = "juanlucasbarbiergarzon@gmail.com";

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();

	private WebDriver driver;

	private int screenshotCounter = 0;

	private Path screenshotDirectory;

	private String politicaUrl = "";

	private String terminosUrl = "";

	private WebDriverWait wait;

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue(
				"Set SALEADS_E2E_ENABLED=true to run SaleADS UI automation in this repository.",
				Boolean.parseBoolean(env("SALEADS_E2E_ENABLED", "false")));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (Boolean.parseBoolean(env("SALEADS_HEADLESS", "false"))) {
			options.addArguments("--headless=new");
		}

		final String chromeUserDataDir = env("SALEADS_CHROME_USER_DATA_DIR", "");
		if (!chromeUserDataDir.isBlank()) {
			options.addArguments("--user-data-dir=" + chromeUserDataDir);
		}

		final String chromeProfileDir = env("SALEADS_CHROME_PROFILE_DIR", "");
		if (!chromeProfileDir.isBlank()) {
			options.addArguments("--profile-directory=" + chromeProfileDir);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		screenshotDirectory = Path.of("target", "saleads-screenshots", runId);
		Files.createDirectories(screenshotDirectory);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		initializeReport();

		try {
			final String loginUrl = requiredEnv("SALEADS_LOGIN_URL");
			driver.get(loginUrl);
			waitForUiLoadAfterClick();
		} catch (final Exception e) {
			throw new IllegalStateException(
					"Could not open SaleADS login page. Set SALEADS_LOGIN_URL to the login URL of the current environment.",
					e);
		}

		runStep(REPORTE_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORTE_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORTE_AGREGAR_NEGOCIO, this::stepValidateAgregarNegocioModal);
		runStep(REPORTE_ADMINISTRAR_NEGOCIOS, this::stepOpenAdministrarNegocios);
		runStep(REPORTE_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(REPORTE_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(REPORTE_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(REPORTE_TERMINOS, this::stepValidateTerminosYCondiciones);
		runStep(REPORTE_POLITICA, this::stepValidatePoliticaDePrivacidad);

		printFinalReport();
		assertAllStepsPassed();
	}

	private void assertAllStepsPassed() {
		final List<String> failedSteps = new ArrayList<>();

		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			if (!entry.getValue()) {
				failedSteps.add(entry.getKey());
			}
		}

		assertTrue("One or more required validations failed: " + failedSteps, failedSteps.isEmpty());
	}

	private void captureCheckpointScreenshot(final String checkpointName) throws Exception {
		final String filename = String.format("%02d-%s.png", ++screenshotCounter, sanitizeName(checkpointName));
		final Path screenshotPath = screenshotDirectory.resolve(filename);
		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshotFile.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("[saleads-e2e] Screenshot: " + screenshotPath.toAbsolutePath());
	}

	private void clickByVisibleText(final String text) {
		final WebElement element = findFirstVisibleElementByText(text);

		if (element == null) {
			throw new NoSuchElementException("Could not find clickable element by visible text: " + text);
		}

		clickElementAndWaitForUiLoad(element);
	}

	private void clickElementAndWaitForUiLoad(final WebElement element) {
		scrollIntoView(element);

		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final Exception e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiLoadAfterClick();
	}

	private boolean clickFirstAvailableText(final String... candidateTexts) {
		for (final String candidateText : candidateTexts) {
			final WebElement candidate = findFirstVisibleElementByText(candidateText);
			if (candidate != null) {
				clickElementAndWaitForUiLoad(candidate);
				return true;
			}
		}

		return false;
	}

	private String env(final String name, final String defaultValue) {
		final String value = System.getenv(name);
		return value == null ? defaultValue : value.trim();
	}

	private WebElement findFirstVisibleElementByText(final String text) {
		final String literal = toXpathLiteral(text);
		final List<By> strategies = Arrays.asList(
				By.xpath(
						"//*[self::button or self::a or @role='button' or self::span or self::div][normalize-space()="
								+ literal + "]"),
				By.xpath("//*[normalize-space()=" + literal + "]"),
				By.xpath("//*[contains(normalize-space(), " + literal + ")]"));

		for (final By strategy : strategies) {
			final List<WebElement> elements = driver.findElements(strategy);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}

		return null;
	}

	private WebElement findNombreNegocioInput() {
		final List<By> strategies = Arrays.asList(
				By.xpath("//input[@placeholder=" + toXpathLiteral(NOMBRE_NEGOCIO) + "]"),
				By.xpath("//input[@aria-label=" + toXpathLiteral(NOMBRE_NEGOCIO) + "]"),
				By.xpath("//label[contains(normalize-space(), " + toXpathLiteral(NOMBRE_NEGOCIO)
						+ ")]/following::input[1]"),
				By.xpath("//input[contains(@name, 'nombre') or contains(@id, 'nombre')]"));

		for (final By strategy : strategies) {
			for (final WebElement element : driver.findElements(strategy)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}

		return null;
	}

	private WebElement findSectionContainer(final String headingText) {
		final String headingLiteral = toXpathLiteral(headingText);
		final List<By> candidates = Arrays.asList(
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::p]"
						+ "[contains(normalize-space(), " + headingLiteral + ")]"),
				By.xpath("//*[contains(normalize-space(), " + headingLiteral + ")]"));

		for (final By candidate : candidates) {
			final List<WebElement> headingElements = driver.findElements(candidate);
			for (final WebElement heading : headingElements) {
				if (!heading.isDisplayed()) {
					continue;
				}

				WebElement container = heading;
				for (int i = 0; i < 4; i++) {
					try {
						container = container.findElement(By.xpath("./.."));
					} catch (final NoSuchElementException e) {
						break;
					}
				}

				if (container.isDisplayed()) {
					return container;
				}
			}
		}

		return null;
	}

	private void initializeReport() {
		finalReport.clear();
		finalReport.put(REPORTE_LOGIN, false);
		finalReport.put(REPORTE_MI_NEGOCIO_MENU, false);
		finalReport.put(REPORTE_AGREGAR_NEGOCIO, false);
		finalReport.put(REPORTE_ADMINISTRAR_NEGOCIOS, false);
		finalReport.put(REPORTE_INFO_GENERAL, false);
		finalReport.put(REPORTE_DETALLES_CUENTA, false);
		finalReport.put(REPORTE_TUS_NEGOCIOS, false);
		finalReport.put(REPORTE_TERMINOS, false);
		finalReport.put(REPORTE_POLITICA, false);
	}

	private boolean isEmailVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final Matcher matcher = EMAIL_PATTERN.matcher(bodyText);
		return matcher.find();
	}

	private boolean isLikelyUserNameVisible() {
		final WebElement section = findSectionContainer(INFORMACION_GENERAL);
		final String sourceText = section == null ? driver.findElement(By.tagName("body")).getText() : section.getText();

		for (final String rawLine : sourceText.split("\\R")) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}

			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}

			if (line.equals(INFORMACION_GENERAL) || line.equalsIgnoreCase("BUSINESS PLAN")
					|| line.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}

			if (line.contains("Cuenta creada") || line.contains("Estado activo") || line.contains("Idioma seleccionado")) {
				continue;
			}

			if (line.matches(".*[A-Za-z].*") && !line.matches("^[0-9\\-:/. ]+$")) {
				return true;
			}
		}

		return false;
	}

	private boolean isTextVisible(final String text) {
		final String literal = toXpathLiteral(text);
		final List<WebElement> elements = driver.findElements(By.xpath("//*[normalize-space()=" + literal + "]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}

		return false;
	}

	private boolean isTextVisibleContaining(final String text) {
		final String literal = toXpathLiteral(text);
		final List<WebElement> elements = driver.findElements(By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}

		return false;
	}

	private boolean isWindowCountGreaterThan(final int count) {
		return driver.getWindowHandles().size() > count;
	}

	private String openLegalLinkAndValidate(final String linkText, final String expectedHeading, final String screenshotName)
			throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final int windowCountBeforeClick = windowsBeforeClick.size();

		clickByVisibleText(linkText);
		waitForUiLoadAfterClick();

		boolean switchedToNewWindow = false;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12))
					.until(webDriver -> isWindowCountGreaterThan(windowCountBeforeClick));
			for (final String handle : driver.getWindowHandles()) {
				if (!windowsBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					switchedToNewWindow = true;
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			// same tab navigation is acceptable for this workflow
		}

		waitForUiLoadAfterClick();
		assertTrue("Expected heading not visible for legal page: " + expectedHeading,
				isTextVisible(expectedHeading) || isTextVisibleContaining(expectedHeading));

		final String pageText = driver.findElement(By.tagName("body")).getText().trim();
		assertTrue("Expected legal page text content to be visible for: " + linkText, pageText.length() > 120);

		captureCheckpointScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();
		System.out.println("[saleads-e2e] Final URL for " + linkText + ": " + finalUrl);

		if (switchedToNewWindow) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoadAfterClick();
		} else {
			driver.navigate().back();
			waitForUiLoadAfterClick();
		}

		return finalUrl;
	}

	private void printFinalReport() {
		System.out.println("[saleads-e2e] Final step report:");
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			final String status = entry.getValue() ? "PASS" : "FAIL";
			System.out.println("[saleads-e2e] - " + entry.getKey() + ": " + status);
		}
		System.out.println("[saleads-e2e] - T\u00e9rminos y Condiciones URL: "
				+ (terminosUrl.isBlank() ? "N/A" : terminosUrl));
		System.out.println(
				"[saleads-e2e] - Pol\u00edtica de Privacidad URL: " + (politicaUrl.isBlank() ? "N/A" : politicaUrl));
		System.out.println("[saleads-e2e] Screenshots directory: " + screenshotDirectory.toAbsolutePath());
	}

	private String requiredEnv(final String envName) {
		final String value = env(envName, "");
		if (value.isBlank()) {
			throw new IllegalStateException("Required environment variable is missing: " + envName);
		}
		return value;
	}

	private void runStep(final String reportField, final StepAction stepAction) {
		try {
			stepAction.run();
			finalReport.put(reportField, true);
		} catch (final Exception | AssertionError e) {
			finalReport.put(reportField, false);
			System.err.println("[saleads-e2e] Step failed: " + reportField + " - " + e.getMessage());
			try {
				captureCheckpointScreenshot("FAILED-" + reportField);
			} catch (final Exception screenshotError) {
				System.err.println("[saleads-e2e] Could not capture failure screenshot: " + screenshotError.getMessage());
			}
		}
	}

	private String sanitizeName(final String rawName) {
		return rawName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({behavior: 'instant', block: 'center', inline: 'center'});", element);
	}

	private void stepLoginWithGoogle() throws Exception {
		final boolean loginClicked = clickFirstAvailableText(
				"Sign in with Google",
				"Iniciar sesi\u00f3n con Google",
				"Login with Google",
				"Continuar con Google",
				"Ingresar con Google",
				"Google");
		assertTrue("Could not find login button by visible text.", loginClicked);

		final String accountEmail = env("SALEADS_GOOGLE_EMAIL", USER_EMAIL_DEFAULT);
		final WebElement accountElement = findFirstVisibleElementByText(accountEmail);
		if (accountElement != null) {
			clickElementAndWaitForUiLoad(accountElement);
		}

		new WebDriverWait(driver, Duration.ofSeconds(60))
				.until(webDriver -> isTextVisible("Mi Negocio") || isTextVisible("Negocio"));

		assertTrue("Main application interface did not appear after Google login.",
				isTextVisible("Mi Negocio") || isTextVisibleContaining("Dashboard")
						|| isTextVisibleContaining("Negocio"));

		assertTrue("Left sidebar navigation is not visible.", isTextVisible("Negocio") || isTextVisible("Mi Negocio"));
		captureCheckpointScreenshot("01-dashboard-loaded");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible(ADMINISTRAR_NEGOCIOS)) {
			clickFirstAvailableText("Mi Negocio", "Negocio");
			waitForUiLoadAfterClick();
		}

		clickByVisibleText(ADMINISTRAR_NEGOCIOS);
		waitForUiLoadAfterClick();

		assertTrue("Informaci\u00f3n General section not visible.", isTextVisibleContaining(INFORMACION_GENERAL));
		assertTrue("Detalles de la Cuenta section not visible.", isTextVisibleContaining("Detalles de la Cuenta"));
		assertTrue("Tus Negocios section not visible.", isTextVisibleContaining(TUS_NEGOCIOS));
		assertTrue("Secci\u00f3n Legal section not visible.", isTextVisibleContaining("Secci\u00f3n Legal"));

		captureCheckpointScreenshot("04-administrar-negocios-page");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickFirstAvailableText("Negocio", "Mi Negocio");
		waitForUiLoadAfterClick();

		if (!isTextVisible(AGREGAR_NEGOCIO) || !isTextVisible(ADMINISTRAR_NEGOCIOS)) {
			clickByVisibleText("Mi Negocio");
			waitForUiLoadAfterClick();
		}

		assertTrue("'Agregar Negocio' submenu option not visible.", isTextVisible(AGREGAR_NEGOCIO));
		assertTrue("'Administrar Negocios' submenu option not visible.", isTextVisible(ADMINISTRAR_NEGOCIOS));

		captureCheckpointScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText(AGREGAR_NEGOCIO);
		waitForUiLoadAfterClick();

		assertTrue("Modal title 'Crear Nuevo Negocio' is not visible.", isTextVisibleContaining("Crear Nuevo Negocio"));
		assertTrue("Input label 'Nombre del Negocio' is not visible.", isTextVisibleContaining(NOMBRE_NEGOCIO));
		assertTrue("Capacity text is not visible in the modal.", isTextVisibleContaining(TEXTO_CAPACIDAD_NEGOCIOS));
		assertTrue("'Cancelar' button is not visible.", isTextVisible("Cancelar"));
		assertTrue("'Crear Negocio' button is not visible.", isTextVisible("Crear Negocio"));

		final WebElement nombreNegocioInput = findNombreNegocioInput();
		assertNotNull("Could not find input field for 'Nombre del Negocio'.", nombreNegocioInput);

		captureCheckpointScreenshot("03-agregar-negocio-modal");

		nombreNegocioInput.click();
		nombreNegocioInput.clear();
		nombreNegocioInput.sendKeys("Negocio Prueba Automatizaci\u00f3n");
		waitForUiLoadAfterClick();

		clickByVisibleText("Cancelar");
		waitForUiLoadAfterClick();
	}

	private void stepValidateDetallesCuenta() {
		assertTrue("'Cuenta creada' text not visible.", isTextVisibleContaining("Cuenta creada"));
		assertTrue("'Estado activo' text not visible.", isTextVisibleContaining("Estado activo"));
		assertTrue("'Idioma seleccionado' text not visible.", isTextVisibleContaining("Idioma seleccionado"));
	}

	private void stepValidateInformacionGeneral() {
		assertTrue("Could not detect visible user name in Informaci\u00f3n General section.", isLikelyUserNameVisible());
		assertTrue("Could not detect visible user email in Informaci\u00f3n General section.", isEmailVisible());
		assertTrue("'BUSINESS PLAN' text not visible.", isTextVisibleContaining("BUSINESS PLAN"));
		assertTrue("'Cambiar Plan' button not visible.", isTextVisible("Cambiar Plan"));
	}

	private void stepValidatePoliticaDePrivacidad() throws Exception {
		politicaUrl = openLegalLinkAndValidate(POLITICA_DE_PRIVACIDAD, POLITICA_DE_PRIVACIDAD,
				"06-politica-privacidad");
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		terminosUrl = openLegalLinkAndValidate(TERMINOS_Y_CONDICIONES, TERMINOS_Y_CONDICIONES,
				"05-terminos-condiciones");
	}

	private void stepValidateTusNegocios() {
		assertTrue("Could not locate section heading 'Tus Negocios'.", isTextVisibleContaining(TUS_NEGOCIOS));
		assertTrue("'Agregar Negocio' button not visible in Tus Negocios section.", isTextVisible(AGREGAR_NEGOCIO));
		assertTrue("Business capacity text not visible in Tus Negocios section.",
				isTextVisibleContaining(TEXTO_CAPACIDAD_NEGOCIOS));

		final WebElement section = findSectionContainer(TUS_NEGOCIOS);
		assertNotNull("Could not resolve 'Tus Negocios' section container.", section);

		final List<WebElement> listCandidates = section
				.findElements(By.xpath(".//*[self::li or self::tr or self::article or self::section or self::div]"));
		boolean hasVisibleListContent = false;

		for (final WebElement candidate : listCandidates) {
			final String text = candidate.getText().trim();
			if (!candidate.isDisplayed() || text.isEmpty()) {
				continue;
			}

			if (text.equals(TUS_NEGOCIOS) || text.equals(AGREGAR_NEGOCIO) || text.equals(TEXTO_CAPACIDAD_NEGOCIOS)) {
				continue;
			}

			hasVisibleListContent = true;
			break;
		}

		assertTrue("Business list content is not visible in 'Tus Negocios'.", hasVisibleListContent);
	}

	private String toXpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final String[] parts = text.split("'");
		for (int i = 0; i < parts.length; i++) {
			result.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				result.append(",\"'\",");
			}
		}
		result.append(")");
		return result.toString();
	}

	private void waitForUiLoadAfterClick() {
		wait.until(webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));

		try {
			Thread.sleep(800L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {

		void run() throws Exception;
	}
}
