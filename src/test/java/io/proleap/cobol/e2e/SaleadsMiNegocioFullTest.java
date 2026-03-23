package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final DateTimeFormatter REPORT_TIME_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME
			.withZone(ZoneOffset.UTC);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private Path reportDir;
	private boolean enabled;

	private final Map<String, String> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepNotes = new LinkedHashMap<>();

	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final boolean runE2e = Boolean.parseBoolean(System.getProperty("runSaleadsE2E", "false"));
		Assume.assumeTrue("Set -DrunSaleadsE2E=true to execute this opt-in end-to-end test.", runE2e);

		final String loginUrl = System.getProperty("saleads.login.url", "").trim();
		Assume.assumeTrue("Set -Dsaleads.login.url=<saleads-login-page> (works for dev/staging/prod).",
				!loginUrl.isEmpty());

		ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(System.getProperty("saleads.headless", "false"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		driver = new ChromeDriver(options);

		final long waitSeconds = Long.parseLong(System.getProperty("saleads.wait.seconds", "30"));
		wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));

		screenshotDir = Paths.get("target", "saleads-screenshots", TEST_NAME);
		reportDir = Paths.get("target", "saleads-reports");
		Files.createDirectories(screenshotDir);
		Files.createDirectories(reportDir);

		for (final String field : REPORT_FIELDS) {
			stepResults.put(field, "FAIL");
		}

		enabled = true;
		driver.get(loginUrl);
		waitForUiLoad();
	}

	@Test
	public void runFullWorkflow() {
		final List<String> failures = new ArrayList<>();

		final boolean loginOk = runStep("Login", failures, this::stepLoginWithGoogle);
		if (!loginOk) {
			markBlocked("Mi Negocio menu");
			markBlocked("Agregar Negocio modal");
			markBlocked("Administrar Negocios view");
			markBlocked("Información General");
			markBlocked("Detalles de la Cuenta");
			markBlocked("Tus Negocios");
			markBlocked("Términos y Condiciones");
			markBlocked("Política de Privacidad");
		} else {
			final boolean menuOk = runStep("Mi Negocio menu", failures, this::stepOpenMiNegocioMenu);
			if (!menuOk) {
				markBlocked("Agregar Negocio modal");
				markBlocked("Administrar Negocios view");
				markBlocked("Información General");
				markBlocked("Detalles de la Cuenta");
				markBlocked("Tus Negocios");
				markBlocked("Términos y Condiciones");
				markBlocked("Política de Privacidad");
			} else {
				runStep("Agregar Negocio modal", failures, this::stepValidateAgregarModal);
				final boolean administrarOk = runStep("Administrar Negocios view", failures, this::stepOpenAdministrarNegocios);
				if (!administrarOk) {
					markBlocked("Información General");
					markBlocked("Detalles de la Cuenta");
					markBlocked("Tus Negocios");
					markBlocked("Términos y Condiciones");
					markBlocked("Política de Privacidad");
				} else {
					runStep("Información General", failures, this::stepValidateInformacionGeneral);
					runStep("Detalles de la Cuenta", failures, this::stepValidateDetallesCuenta);
					runStep("Tus Negocios", failures, this::stepValidateTusNegocios);
					runStep("Términos y Condiciones", failures, this::stepValidateTerminosCondiciones);
					runStep("Política de Privacidad", failures, this::stepValidatePoliticaPrivacidad);
				}
			}
		}

		if (!failures.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow validation failed:\n - " + String.join("\n - ", failures));
		}
	}

	@After
	public void tearDown() throws IOException {
		if (enabled) {
			writeFinalReport();
		}

		if (driver != null) {
			driver.quit();
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		final Set<String> windowsBefore = driver.getWindowHandles();
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
				"Continuar con Google", "Login con Google", "Google");

		switchToNewWindowIfOpened(windowsBefore, Duration.ofSeconds(12));
		selectGoogleAccountIfVisible();
		waitForMainInterface();

		assertAnyElementVisible("Expected left sidebar navigation to be visible after login.",
				By.xpath("//aside[not(contains(@style,'display: none'))]"),
				By.xpath("//nav[contains(@class,'sidebar') or contains(@class,'Sidebar')]"),
				By.xpath("//*[contains(@class,'sidebar') or contains(@class,'Sidebar')]"));
		takeCheckpoint("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeCheckpoint("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		assertAnyTextVisible(Arrays.asList("Crear Nuevo Negocio"));
		assertAnyElementVisible("Expected input field 'Nombre del Negocio' in modal.",
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));
		assertAnyTextVisible(Arrays.asList("Tienes 2 de 3 negocios"));
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeCheckpoint("03_agregar_negocio_modal");

		typeInNombreNegocioFieldIfPresent("Negocio Prueba Automatizacion");
		clickByVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isAnyTextVisible(Arrays.asList("Administrar Negocios"))) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertAnyTextVisible(Arrays.asList("Información General", "Informacion General"));
		assertAnyTextVisible(Arrays.asList("Detalles de la Cuenta"));
		assertAnyTextVisible(Arrays.asList("Tus Negocios"));
		assertAnyTextVisible(Arrays.asList("Sección Legal", "Seccion Legal"));
		takeCheckpoint("04_administrar_negocios_view");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement infoSection = findSectionByHeading(Arrays.asList("Información General", "Informacion General"));
		final String sectionText = normalizeWhitespace(infoSection.getText());

		final String expectedName = System.getProperty("saleads.expected.user.name", "").trim();
		if (!expectedName.isEmpty()) {
			if (!containsIgnoreCase(sectionText, expectedName)) {
				throw new AssertionError(
						"Expected user name '" + expectedName + "' not found in 'Información General'.");
			}
		} else if (!containsIgnoreCase(sectionText, "Nombre") && !containsIgnoreCase(sectionText, "Usuario")
				&& !containsIgnoreCase(sectionText, "User")) {
			throw new AssertionError(
					"Could not infer user name visibility in 'Información General'. "
							+ "Set -Dsaleads.expected.user.name for strict validation.");
		}

		final String expectedEmail = System.getProperty("saleads.expected.user.email",
				System.getProperty("saleads.google.account", "juanlucasbarbiergarzon@gmail.com"));
		if (!containsIgnoreCase(sectionText, expectedEmail) && !EMAIL_PATTERN.matcher(sectionText).find()) {
			throw new AssertionError("User email is not visible in 'Información General'.");
		}

		assertAnyTextVisible(Arrays.asList("BUSINESS PLAN"));
		assertAnyTextVisible(Arrays.asList("Cambiar Plan"));
	}

	private void stepValidateDetallesCuenta() {
		final WebElement detailsSection = findSectionByHeading(Arrays.asList("Detalles de la Cuenta"));
		final String detailsText = normalizeWhitespace(detailsSection.getText());

		assertContains(detailsText, "Cuenta creada", "Missing 'Cuenta creada' in 'Detalles de la Cuenta'.");
		assertContainsAny(detailsText, Arrays.asList("Estado activo", "Estado Activo", "Activo"),
				"Missing active status information in 'Detalles de la Cuenta'.");
		assertContainsAny(detailsText, Arrays.asList("Idioma seleccionado", "Idioma"),
				"Missing selected language information in 'Detalles de la Cuenta'.");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading(Arrays.asList("Tus Negocios"));
		final String text = normalizeWhitespace(section.getText());

		final List<WebElement> businessRows = section.findElements(By.xpath(
				".//li | .//tr | .//*[contains(@class,'business') or contains(@class,'Business') or contains(@class,'negocio')]"));
		if (businessRows.isEmpty() && text.length() < 40) {
			throw new AssertionError("Business list is not clearly visible in 'Tus Negocios'.");
		}

		assertAnyElementVisible("Expected 'Agregar Negocio' button inside 'Tus Negocios'.",
				By.xpath("//*[contains(normalize-space(.),'Tus Negocios')]//ancestor::*[self::section or self::div][1]"
						+ "//*[self::button or self::a or @role='button'][contains(normalize-space(.),'Agregar Negocio')]"),
				By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.),'Agregar Negocio')]"));
		assertAnyTextVisible(Arrays.asList("Tienes 2 de 3 negocios"));
	}

	private void stepValidateTerminosCondiciones() throws Exception {
		termsUrl = openLegalDocumentAndReturnUrl(Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
				Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"), "05_terminos_y_condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		privacyUrl = openLegalDocumentAndReturnUrl(Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
				Arrays.asList("Política de Privacidad", "Politica de Privacidad"), "06_politica_de_privacidad");
	}

	private String openLegalDocumentAndReturnUrl(final List<String> linkTexts, final List<String> headingTexts,
			final String checkpointFileName) throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText(linkTexts.toArray(new String[0]));

		final String newHandle = waitForNewWindowHandle(handlesBefore, Duration.ofSeconds(8));
		final boolean openedInNewTab = newHandle != null;
		if (openedInNewTab) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		}

		waitForAnyTextVisible(headingTexts, Duration.ofSeconds(40));
		final String legalText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		if (legalText.length() < 120) {
			throw new AssertionError("Legal content is not visible or too short after opening " + linkTexts.get(0) + ".");
		}

		takeCheckpoint(checkpointFileName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiLoad();
		} else if (!normalizeWhitespace(finalUrl).equals(normalizeWhitespace(originalUrl))) {
			driver.navigate().back();
			waitForUiLoad();
		}

		return finalUrl;
	}

	private boolean runStep(final String field, final List<String> failures, final StepAction action) {
		try {
			action.run();
			stepResults.put(field, "PASS");
			return true;
		} catch (final Throwable t) {
			stepResults.put(field, "FAIL");
			final String message = safeMessage(t);
			stepNotes.put(field, message);
			failures.add(field + ": " + message);
			takeFailureCheckpoint(field);
			return false;
		}
	}

	private void markBlocked(final String field) {
		if ("PASS".equals(stepResults.get(field))) {
			return;
		}
		stepResults.put(field, "FAIL");
		stepNotes.putIfAbsent(field, "Blocked by a prerequisite step failure.");
	}

	private void switchToNewWindowIfOpened(final Set<String> windowsBefore, final Duration timeout) throws InterruptedException {
		final String newHandle = waitForNewWindowHandle(windowsBefore, timeout);
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		}
	}

	private String waitForNewWindowHandle(final Set<String> windowsBefore, final Duration timeout)
			throws InterruptedException {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() <= deadline) {
			final Set<String> currentWindows = driver.getWindowHandles();
			for (final String handle : currentWindows) {
				if (!windowsBefore.contains(handle)) {
					return handle;
				}
			}
			Thread.sleep(250);
		}
		return null;
	}

	private void selectGoogleAccountIfVisible() throws InterruptedException {
		final String accountEmail = System.getProperty("saleads.google.account", "juanlucasbarbiergarzon@gmail.com");
		final List<WebElement> candidates = driver
				.findElements(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(accountEmail) + ")]"));

		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				scrollIntoView(candidate);
				safeClick(candidate);
				waitForUiLoad();
				return;
			}
		}
	}

	private void waitForMainInterface() {
		try {
			waitForAnyTextVisible(Arrays.asList("Negocio", "Dashboard", "Panel"), Duration.ofSeconds(60));
		} catch (final RuntimeException primaryFailure) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (isAnyTextVisible(Arrays.asList("Negocio", "Dashboard", "Panel"))) {
					return;
				}
			}
			throw primaryFailure;
		}
	}

	private void clickByVisibleText(final String... texts) throws Exception {
		final List<String> lookupTexts = Arrays.asList(texts);
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
		while (System.currentTimeMillis() <= deadline) {
			for (final String text : lookupTexts) {
				for (final WebElement element : findClickableCandidates(text)) {
					if (isInteractable(element)) {
						scrollIntoView(element);
						safeClick(element);
						waitForUiLoad();
						return;
					}
				}
			}
			Thread.sleep(250);
		}

		throw new NoSuchElementException(
				"Could not find clickable element with visible text: " + String.join(" | ", lookupTexts));
	}

	private List<WebElement> findClickableCandidates(final String text) {
		final String literal = xpathLiteral(text);
		final String xpath = "//*[self::button or self::a or @role='button' or contains(@class,'btn')]"
				+ "[contains(normalize-space(.)," + literal + ")]"
				+ " | //*[contains(normalize-space(.)," + literal + ")]"
				+ "/ancestor-or-self::*[self::button or self::a or @role='button' or contains(@class,'btn')][1]";
		return driver.findElements(By.xpath(xpath));
	}

	private void typeInNombreNegocioFieldIfPresent(final String businessName) throws InterruptedException {
		final List<WebElement> fields = driver.findElements(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio')] | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));
		for (final WebElement field : fields) {
			if (field.isDisplayed() && field.isEnabled()) {
				field.clear();
				field.sendKeys(businessName);
				waitForUiLoad();
				return;
			}
		}
	}

	private WebElement findSectionByHeading(final List<String> headingTexts) {
		for (final String heading : headingTexts) {
			final String literal = xpathLiteral(heading);
			final List<WebElement> sections = driver.findElements(By.xpath(
					"//*[contains(normalize-space(.)," + literal + ")]/ancestor::*[self::section or self::div][1]"));
			for (final WebElement section : sections) {
				if (section.isDisplayed()) {
					return section;
				}
			}
		}

		throw new NoSuchElementException("Could not find section for headings: " + String.join(" | ", headingTexts));
	}

	private void assertTextVisible(final String text) {
		assertAnyTextVisible(Arrays.asList(text));
	}

	private void assertAnyTextVisible(final List<String> texts) {
		waitForAnyTextVisible(texts, Duration.ofSeconds(25));
	}

	private void waitForAnyTextVisible(final List<String> texts, final Duration timeout) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() <= deadline) {
			if (isAnyTextVisible(texts)) {
				return;
			}
			sleepQuietly(200);
		}

		throw new TimeoutException("Expected visible text not found: " + String.join(" | ", texts));
	}

	private boolean isAnyTextVisible(final List<String> texts) {
		for (final String text : texts) {
			final String literal = xpathLiteral(text);
			final List<WebElement> elements = driver.findElements(By.xpath("//*[contains(normalize-space(.)," + literal + ")]"));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void assertAnyElementVisible(final String failureMessage, final By... locators) {
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}
		throw new AssertionError(failureMessage);
	}

	private void waitForUiLoad() {
		waitForDocumentReady();
		waitForLoadingIndicatorsToDisappear();
		sleepQuietly(350);
	}

	private void waitForDocumentReady() {
		try {
			wait.until((ExpectedCondition<Boolean>) webDriver -> "complete".equals(
					String.valueOf(((JavascriptExecutor) webDriver).executeScript("return document.readyState"))));
		} catch (final TimeoutException ignored) {
			// The app may stay in interactive states while data fetches continue.
		}
	}

	private void waitForLoadingIndicatorsToDisappear() {
		final By loadingBy = By.xpath(
				"//*[contains(@class,'loading') or contains(@class,'spinner') or contains(@class,'skeleton') or @aria-busy='true']");
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10)).until(webDriver -> {
				for (final WebElement spinner : webDriver.findElements(loadingBy)) {
					if (spinner.isDisplayed()) {
						return false;
					}
				}
				return true;
			});
		} catch (final TimeoutException ignored) {
			// Not all applications expose deterministic loading indicators.
		}
	}

	private String takeCheckpoint(final String name) throws IOException {
		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.ROOT).withZone(ZoneOffset.UTC)
				.format(Instant.now());
		final Path destination = screenshotDir.resolve(name + "_" + timestamp + ".png");
		Files.copy(screenshotFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		return destination.toString();
	}

	private void takeFailureCheckpoint(final String stepName) {
		try {
			takeCheckpoint("failure_" + sanitizeFileToken(stepName));
		} catch (final IOException ignored) {
			// Best-effort evidence capture.
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("Test Name: ").append(TEST_NAME).append('\n');
		report.append("Generated At (UTC): ").append(REPORT_TIME_FORMAT.format(Instant.now())).append('\n');
		report.append('\n');
		report.append("Final Report (PASS/FAIL)\n");
		report.append("========================\n");
		for (final String field : REPORT_FIELDS) {
			report.append(field).append(": ").append(stepResults.getOrDefault(field, "FAIL"));
			final String note = stepNotes.get(field);
			if (note != null && !note.isBlank()) {
				report.append(" (").append(note).append(')');
			}
			report.append('\n');
		}

		report.append('\n');
		report.append("Evidence\n");
		report.append("========\n");
		report.append("Screenshots Directory: ").append(screenshotDir.toAbsolutePath()).append('\n');
		report.append("Terminos y Condiciones URL: ").append(termsUrl).append('\n');
		report.append("Politica de Privacidad URL: ").append(privacyUrl).append('\n');

		final Path reportPath = reportDir.resolve(TEST_NAME + "_report.txt");
		Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
		System.out.println("SaleADS report generated at: " + reportPath.toAbsolutePath());
	}

	private void safeClick(final WebElement element) {
		try {
			element.click();
		} catch (final RuntimeException clickFailure) {
			new Actions(driver).moveToElement(element).click().perform();
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private boolean isInteractable(final WebElement element) {
		try {
			return element.isDisplayed() && element.isEnabled();
		} catch (final RuntimeException ignored) {
			return false;
		}
	}

	private static void assertContains(final String source, final String expected, final String failureMessage) {
		if (!containsIgnoreCase(source, expected)) {
			throw new AssertionError(failureMessage);
		}
	}

	private static void assertContainsAny(final String source, final List<String> expectedValues,
			final String failureMessage) {
		for (final String expected : expectedValues) {
			if (containsIgnoreCase(source, expected)) {
				return;
			}
		}
		throw new AssertionError(failureMessage);
	}

	private static boolean containsIgnoreCase(final String source, final String expected) {
		return source.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
	}

	private static String normalizeWhitespace(final String value) {
		return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	private static String sanitizeFileToken(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
	}

	private static String safeMessage(final Throwable t) {
		final String message = t.getMessage();
		return (message == null || message.isBlank()) ? t.getClass().getSimpleName() : message;
	}

	private static String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final String[] parts = text.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private static void sleepQuietly(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
