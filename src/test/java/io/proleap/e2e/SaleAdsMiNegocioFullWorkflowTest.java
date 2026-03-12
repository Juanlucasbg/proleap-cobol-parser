package io.proleap.e2e;

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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(35);
	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Path finalReportPath;
	private String appWindowHandle;
	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));

		if (headless) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,2400");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		evidenceDir = Paths.get("target", "saleads-evidence", "saleads_mi_negocio_full_test_" + timestamp).toAbsolutePath();
		Files.createDirectories(evidenceDir);
		finalReportPath = evidenceDir.resolve("final-report.txt");
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = resolveLoginUrl();
		Assert.assertNotNull(
				"Login URL not provided. Set -Dsaleads.url or SALEADS_URL/SALEADS_LOGIN_URL for the current environment.",
				loginUrl);

		driver.get(loginUrl);
		waitForPageToLoad();
		appWindowHandle = driver.getWindowHandle();

		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		writeFinalReport();
		assertAllStepsPassed();
	}

	private void stepLoginWithGoogle() {
		final Set<String> handlesBeforeLogin = new LinkedHashSet<>(driver.getWindowHandles());
		clickByAnyVisibleText(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Inicia sesión con Google",
				"Continuar con Google",
				"Google");

		final String possiblePopup = waitForNewWindow(handlesBeforeLogin, Duration.ofSeconds(15));
		if (possiblePopup != null) {
			driver.switchTo().window(possiblePopup);
		}

		selectGoogleAccountIfVisible();

		// OAuth may return in popup/new tab or directly in app tab.
		switchToApplicationWindow();
		waitForPageToLoad();
		waitForAnyVisibleText(
				Duration.ofSeconds(60),
				"Negocio",
				"Mi Negocio",
				"Dashboard",
				"Inicio");

		final boolean sidebarVisible = isElementVisible(By.xpath("//aside")) || isElementVisible(By.xpath("//nav"));
		Assert.assertTrue("Main app sidebar is not visible after login.", sidebarVisible);

		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() {
		if (!isAnyTextVisible("Mi Negocio")) {
			clickByAnyVisibleText("Negocio");
		}

		clickByAnyVisibleText("Mi Negocio");
		waitForPageToLoad();

		Assert.assertTrue("'Agregar Negocio' should be visible in expanded submenu.", isAnyTextVisible("Agregar Negocio"));
		Assert.assertTrue(
				"'Administrar Negocios' should be visible in expanded submenu.",
				isAnyTextVisible("Administrar Negocios"));

		captureScreenshot("02_mi_negocio_expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText(Duration.ofSeconds(20), "Crear Nuevo Negocio");

		Assert.assertTrue(
				"Modal title 'Crear Nuevo Negocio' should be visible.",
				isAnyTextVisible("Crear Nuevo Negocio"));
		Assert.assertTrue(
				"Input field 'Nombre del Negocio' should exist.",
				isElementVisible(By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]"))
						|| isElementVisible(By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]")));
		Assert.assertTrue(
				"Text 'Tienes 2 de 3 negocios' should be visible.",
				isAnyTextVisible("Tienes 2 de 3 negocios"));
		Assert.assertTrue("'Cancelar' button should be visible.", isAnyTextVisible("Cancelar"));
		Assert.assertTrue("'Crear Negocio' button should be visible.", isAnyTextVisible("Crear Negocio"));

		captureScreenshot("03_agregar_negocio_modal");

		typeIntoBusinessNameField("Negocio Prueba Automatización");
		clickByAnyVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(),'Crear Nuevo Negocio')]")));
		waitForPageToLoad();
	}

	private void stepOpenAdministrarNegocios() {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickByAnyVisibleText("Mi Negocio");
		}

		clickByAnyVisibleText("Administrar Negocios");
		waitForPageToLoad();

		Assert.assertTrue("Section 'Información General' should exist.", isAnyTextVisible("Información General"));
		Assert.assertTrue("Section 'Detalles de la Cuenta' should exist.", isAnyTextVisible("Detalles de la Cuenta"));
		Assert.assertTrue("Section 'Tus Negocios' should exist.", isAnyTextVisible("Tus Negocios"));
		Assert.assertTrue("Section 'Sección Legal' should exist.", isAnyTextVisible("Sección Legal"));

		driver.manage().window().setSize(new Dimension(1920, 3000));
		waitForPageToLoad();
		captureScreenshot("04_administrar_negocios_page");
	}

	private void stepValidateInformacionGeneral() {
		Assert.assertTrue("Text 'BUSINESS PLAN' should be visible.", isAnyTextVisible("BUSINESS PLAN"));
		Assert.assertTrue("Button 'Cambiar Plan' should be visible.", isAnyTextVisible("Cambiar Plan"));
		Assert.assertTrue(
				"User email should be visible in 'Información General'.",
				isAnyTextVisible(GOOGLE_ACCOUNT_EMAIL) || hasEmailTextVisible());
		Assert.assertTrue(
				"User name should be visible in 'Información General'.",
				isAnyTextVisible("Nombre", "Usuario", "Perfil") || hasLikelyUserNameText());
	}

	private void stepValidateDetallesCuenta() {
		Assert.assertTrue("'Cuenta creada' should be visible.", isAnyTextVisible("Cuenta creada"));
		Assert.assertTrue("'Estado activo' should be visible.", isAnyTextVisible("Estado activo"));
		Assert.assertTrue("'Idioma seleccionado' should be visible.", isAnyTextVisible("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		Assert.assertTrue("Section 'Tus Negocios' should be visible.", isAnyTextVisible("Tus Negocios"));
		Assert.assertTrue("Button 'Agregar Negocio' should exist.", isAnyTextVisible("Agregar Negocio"));
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' should be visible.", isAnyTextVisible("Tienes 2 de 3 negocios"));
		Assert.assertTrue("Business list should be visible.", hasBusinessListVisible());
	}

	private void stepValidateTerminosYCondiciones() {
		validateLegalPage(
				"Términos y Condiciones",
				new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
				"05_terminos_y_condiciones");
	}

	private void stepValidatePoliticaPrivacidad() {
		validateLegalPage(
				"Política de Privacidad",
				new String[] { "Política de Privacidad", "Politica de Privacidad" },
				"06_politica_privacidad");
	}

	private void validateLegalPage(
			final String reportKey,
			final String[] linkTexts,
			final String screenshotName) {
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String appUrlBeforeClick = driver.getCurrentUrl();

		clickByAnyVisibleText(linkTexts);

		final String legalTabHandle = waitForNewWindow(handlesBeforeClick, Duration.ofSeconds(10));
		if (legalTabHandle != null) {
			driver.switchTo().window(legalTabHandle);
		}

		waitForPageToLoad();

		if (reportKey.equals("Términos y Condiciones")) {
			waitForAnyVisibleText(Duration.ofSeconds(30), "Términos y Condiciones", "Terminos y Condiciones");
			Assert.assertTrue(
					"Terms heading should be visible.",
					isAnyTextVisible("Términos y Condiciones", "Terminos y Condiciones"));
		} else {
			waitForAnyVisibleText(Duration.ofSeconds(30), "Política de Privacidad", "Politica de Privacidad");
			Assert.assertTrue(
					"Privacy heading should be visible.",
					isAnyTextVisible("Política de Privacidad", "Politica de Privacidad"));
		}

		Assert.assertTrue("Legal content text should be visible.", hasLegalContentVisible());
		legalUrls.put(reportKey, driver.getCurrentUrl());
		captureScreenshot(screenshotName);

		if (legalTabHandle != null) {
			driver.close();
			switchToApplicationWindow();
		} else {
			if (!driver.getCurrentUrl().equals(appUrlBeforeClick)) {
				driver.navigate().back();
			}
			switchToApplicationWindow();
		}

		waitForPageToLoad();
	}

	private void runStep(final String reportName, final CheckedStep step) {
		try {
			step.run();
			stepStatus.put(reportName, "PASS");
		} catch (final Throwable throwable) {
			stepStatus.put(reportName, "FAIL");
			stepErrors.put(reportName, sanitizeFailureMessage(throwable));
			captureScreenshot("fail_" + toSlug(reportName));
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failed = REPORT_FIELDS.stream()
				.filter(field -> !"PASS".equals(stepStatus.getOrDefault(field, "FAIL")))
				.collect(Collectors.toList());

		if (!failed.isEmpty()) {
			Assert.fail("Workflow validation failed for: " + String.join(", ", failed) + ". See report: " + finalReportPath);
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		report.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		report.append("Evidence directory: ").append(evidenceDir).append(System.lineSeparator());
		report.append(System.lineSeparator());
		report.append("PASS/FAIL by validation step").append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			report.append("- ")
					.append(field)
					.append(": ")
					.append(stepStatus.getOrDefault(field, "FAIL"))
					.append(System.lineSeparator());
		}

		report.append(System.lineSeparator());
		report.append("Legal page final URLs").append(System.lineSeparator());
		report.append("- Términos y Condiciones URL: ")
				.append(legalUrls.getOrDefault("Términos y Condiciones", "N/A"))
				.append(System.lineSeparator());
		report.append("- Política de Privacidad URL: ")
				.append(legalUrls.getOrDefault("Política de Privacidad", "N/A"))
				.append(System.lineSeparator());

		if (!stepErrors.isEmpty()) {
			report.append(System.lineSeparator());
			report.append("Failure details").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : stepErrors.entrySet()) {
				report.append("- ")
						.append(entry.getKey())
						.append(": ")
						.append(entry.getValue())
						.append(System.lineSeparator());
			}
		}

		Files.write(finalReportPath, report.toString().getBytes(StandardCharsets.UTF_8));
	}

	private void clickByAnyVisibleText(final String... texts) {
		RuntimeException lastError = null;
		for (final String text : texts) {
			try {
				final WebElement base = waitForVisibleText(text, Duration.ofSeconds(12));
				final WebElement clickable = toClickableElement(base);
				clickAndWait(clickable);
				return;
			} catch (final RuntimeException exception) {
				lastError = exception;
			}
		}

		final NoSuchElementException error = new NoSuchElementException(
				"Unable to find clickable element using texts: " + Arrays.toString(texts));
		if (lastError != null) {
			error.initCause(lastError);
		}
		throw error;
	}

	private WebElement toClickableElement(final WebElement element) {
		try {
			return element.findElement(
					By.xpath("./ancestor-or-self::*[self::button or self::a or @role='button' or @onclick][1]"));
		} catch (final NoSuchElementException notClickable) {
			return element;
		}
	}

	private void clickAndWait(final WebElement element) {
		wait.until(driver -> element.isDisplayed());
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForPageToLoad();
	}

	private WebElement waitForVisibleText(final String text, final Duration timeout) {
		final By locator = By.xpath("//*[normalize-space()=" + asXpathLiteral(text) + " or contains(normalize-space(),"
				+ asXpathLiteral(text) + ")]");
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		return shortWait.until(d -> {
			for (final WebElement element : d.findElements(locator)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private void waitForAnyVisibleText(final Duration timeout, final String... texts) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		shortWait.until(d -> {
			for (final String text : texts) {
				if (isTextVisibleNow(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			if (isTextVisibleNow(text)) {
				return true;
			}
		}
		return false;
	}

	private boolean isTextVisibleNow(final String text) {
		final By locator = By.xpath("//*[normalize-space()=" + asXpathLiteral(text) + " or contains(normalize-space(),"
				+ asXpathLiteral(text) + ")]");
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void typeIntoBusinessNameField(final String value) {
		final List<By> candidates = Arrays.asList(
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[@name='businessName']"));

		for (final By locator : candidates) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed() && element.isEnabled()) {
					element.clear();
					element.sendKeys(value);
					return;
				}
			}
		}
	}

	private boolean hasEmailTextVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final Pattern emailPattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
		return emailPattern.matcher(bodyText).find();
	}

	private boolean hasLikelyUserNameText() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		return bodyText.contains("Nombre") || bodyText.contains("Usuario") || bodyText.contains("Perfil");
	}

	private boolean hasBusinessListVisible() {
		final List<By> listLocators = Arrays.asList(
				By.xpath("//*[contains(normalize-space(),'Tus Negocios')]/ancestor::*[self::section or self::div][1]//table//tr"),
				By.xpath("//*[contains(normalize-space(),'Tus Negocios')]/ancestor::*[self::section or self::div][1]//ul/li"),
				By.xpath("//*[contains(normalize-space(),'Tus Negocios')]/ancestor::*[self::section or self::div][1]//*[@role='row']"),
				By.xpath("//*[contains(normalize-space(),'Tus Negocios')]/ancestor::*[self::section or self::div][1]//*[contains(@class,'card')]"));

		for (final By locator : listLocators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean hasLegalContentVisible() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p[normalize-space()]"));
		for (final WebElement paragraph : paragraphs) {
			if (paragraph.isDisplayed() && paragraph.getText().trim().length() > 80) {
				return true;
			}
		}

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		return bodyText != null && bodyText.trim().length() > 300;
	}

	private boolean isElementVisible(final By by) {
		for (final WebElement element : driver.findElements(by)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void selectGoogleAccountIfVisible() {
		if (isAnyTextVisible(GOOGLE_ACCOUNT_EMAIL)) {
			clickByAnyVisibleText(GOOGLE_ACCOUNT_EMAIL);
			waitForPageToLoad();
		}
	}

	private void switchToApplicationWindow() {
		final Set<String> handles = driver.getWindowHandles();

		if (appWindowHandle != null && handles.contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			return;
		}

		for (final String handle : handles) {
			driver.switchTo().window(handle);
			final String currentUrl = driver.getCurrentUrl();
			if (currentUrl != null && !currentUrl.contains("accounts.google.com")) {
				appWindowHandle = handle;
				return;
			}
		}

		appWindowHandle = handles.iterator().next();
		driver.switchTo().window(appWindowHandle);
	}

	private String waitForNewWindow(final Set<String> previousHandles, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			return shortWait.until(d -> {
				final Set<String> currentHandles = d.getWindowHandles();
				if (currentHandles.size() <= previousHandles.size()) {
					return null;
				}

				for (final String handle : currentHandles) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}

				return null;
			});
		} catch (final Exception ignored) {
			return null;
		}
	}

	private void waitForPageToLoad() {
		wait.until(d -> {
			try {
				final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(String.valueOf(state)) || "interactive".equals(String.valueOf(state));
			} catch (final Exception ignored) {
				return true;
			}
		});
	}

	private void captureScreenshot(final String screenshotName) {
		try {
			final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path destination = evidenceDir.resolve(toSlug(screenshotName) + ".png");
			Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception ignored) {
			// Best effort screenshot capture.
		}
	}

	private String resolveLoginUrl() {
		final List<String> candidates = new ArrayList<>();
		candidates.add(System.getProperty("saleads.url"));
		candidates.add(System.getenv("SALEADS_URL"));
		candidates.add(System.getenv("SALEADS_LOGIN_URL"));

		for (final String candidate : candidates) {
			if (candidate != null && !candidate.trim().isEmpty()) {
				return candidate.trim();
			}
		}
		return null;
	}

	private static String toSlug(final String raw) {
		return raw.toLowerCase()
				.replaceAll("[^a-z0-9]+", "_")
				.replaceAll("^_+", "")
				.replaceAll("_+$", "");
	}

	private String sanitizeFailureMessage(final Throwable throwable) {
		final String message = throwable.getMessage();
		if (message == null || message.trim().isEmpty()) {
			return throwable.getClass().getSimpleName();
		}
		return message.replaceAll("[\\r\\n]+", " ").trim();
	}

	private static String asXpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder concat = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char current = chars[i];
			if (current == '\'') {
				concat.append("\"'\"");
			} else {
				concat.append('\'').append(current).append('\'');
			}
			if (i < chars.length - 1) {
				concat.append(',');
			}
		}
		concat.append(')');
		return concat.toString();
	}

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}
}
