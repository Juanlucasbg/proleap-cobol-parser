package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
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

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final boolean e2eEnabled = Boolean.parseBoolean(envOrDefault("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this workflow test.", e2eEnabled);

		final String debuggerAddress = trimToNull(System.getenv("SALEADS_CHROME_DEBUGGER_ADDRESS"));
		final String loginUrl = trimToNull(System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue("Set SALEADS_LOGIN_URL or SALEADS_CHROME_DEBUGGER_ADDRESS.", loginUrl != null || debuggerAddress != null);

		initializeReport();
		setupScreenshotDir();
		setupDriver(debuggerAddress);

		try {
			if (loginUrl != null) {
				driver.get(loginUrl);
				waitForDocumentReady();
			}

			final boolean loginOk = runStep("Login", this::stepLoginWithGoogle);

			if (!loginOk) {
				markRemainingStepsAsFailed("Mi Negocio menu");
				printFinalReport();
				assertAllPassed();
				return;
			}

			final boolean menuOk = runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			final boolean modalOk = runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			final boolean adminOk = runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);

			if (!menuOk || !modalOk || !adminOk) {
				markRemainingStepsAsFailed("Información General");
				printFinalReport();
				assertAllPassed();
				return;
			}

			runStep("Información General", this::stepValidateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			runStep("Tus Negocios", this::stepValidateTusNegocios);
			runStep("Términos y Condiciones", () -> stepValidateLegalPage("Términos y Condiciones"));
			runStep("Política de Privacidad", () -> stepValidateLegalPage("Política de Privacidad"));

			printFinalReport();
			assertAllPassed();
		} finally {
			tearDownDriver();
		}
	}

	@After
	public void afterEach() {
		tearDownDriver();
	}

	private void setupDriver(final String debuggerAddress) {
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "false"));

		if (headless) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (debuggerAddress != null) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByAnyText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"));
		waitForDocumentReady();
		selectGoogleAccountIfPrompted();
		waitForSidebarAndMainInterface();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		waitForSidebarAndMainInterface();
		clickByAnyText(Arrays.asList("Negocio"));
		clickByAnyText(Arrays.asList("Mi Negocio"));
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByAnyText(Arrays.asList("Agregar Negocio"));

		final WebElement modalTitle = waitForVisibleByAnyText(Arrays.asList("Crear Nuevo Negocio"));
		assertTrue("Modal title was not visible.", modalTitle.isDisplayed());

		final WebElement businessNameInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio')]"
						+ " | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]")));
		assertTrue("Input 'Nombre del Negocio' was not visible.", businessNameInput.isDisplayed());
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		captureScreenshot("03-agregar-negocio-modal");

		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		clickByAnyText(Arrays.asList("Cancelar"));

		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.),'Crear Nuevo Negocio')]")));
		waitForDocumentReady();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		expandMiNegocioIfNeeded();
		clickByAnyText(Arrays.asList("Administrar Negocios"));
		waitForDocumentReady();
		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		captureScreenshot("04-administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		final String sectionText = section.getText();

		final Matcher emailMatcher = EMAIL_PATTERN.matcher(sectionText);
		assertTrue("User email was not visible in 'Información General'.", emailMatcher.find());

		assertTrue("User name was not visible in 'Información General'.", hasLikelyUserName(sectionText));
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		assertTrue("Business list was not visible.", section.getText().trim().length() > 20);
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalPage(final String linkText) throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String previousUrl = driver.getCurrentUrl();

		clickByAnyText(legalTextVariants(linkText));

		final String targetHandle = waitForTargetWindowHandle(handlesBeforeClick, originalHandle);
		if (targetHandle != null) {
			driver.switchTo().window(targetHandle);
		} else {
			wait.until(d -> !d.getCurrentUrl().equals(previousUrl));
		}

		waitForDocumentReady();
		waitForVisibleByAnyText(legalTextVariants(linkText));
		assertLegalContentVisible();
		captureScreenshot("legal-" + sanitizeForFileName(linkText));
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (targetHandle != null && !targetHandle.equals(originalHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else {
			driver.navigate().back();
		}

		waitForDocumentReady();
		assertVisibleText("Sección Legal");
	}

	private void waitForSidebarAndMainInterface() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside | //nav")));
		waitForVisibleByAnyText(Arrays.asList("Negocio", "Mi Negocio", "Dashboard"));
	}

	private void expandMiNegocioIfNeeded() {
		if (isVisibleTextPresent("Administrar Negocios") && isVisibleTextPresent("Agregar Negocio")) {
			return;
		}

		if (!isVisibleTextPresent("Mi Negocio")) {
			clickByAnyText(Arrays.asList("Negocio"));
		}

		clickByAnyText(Arrays.asList("Mi Negocio"));
	}

	private WebElement findSectionByHeading(final String heading) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//*[self::section or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4 or self::p][contains(normalize-space(.),"
						+ quoted(heading) + ")]][1]")));
	}

	private boolean hasLikelyUserName(final String sectionText) {
		final List<String> ignoredTokens = Arrays.asList("información general", "business plan", "cambiar plan");
		final String[] lines = sectionText.split("\\r?\\n");

		for (final String line : lines) {
			final String normalized = line.trim();
			if (normalized.isEmpty()) {
				continue;
			}

			final String lower = normalized.toLowerCase(Locale.ROOT);
			if (ignoredTokens.stream().anyMatch(lower::contains)) {
				continue;
			}

			if (EMAIL_PATTERN.matcher(normalized).find()) {
				continue;
			}

			if (normalized.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ]{3,}.*")) {
				return true;
			}
		}

		return false;
	}

	private void assertLegalContentVisible() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p[normalize-space(string())!='']"));
		final boolean found = paragraphs.stream().map(WebElement::getText).anyMatch(text -> text.trim().length() > 40);
		assertTrue("Legal content text was not visible.", found);
	}

	private String waitForTargetWindowHandle(final Set<String> handlesBeforeClick, final String currentHandle) {
		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);

		try {
			return shortWait.until(d -> {
				final Set<String> handlesAfter = d.getWindowHandles();
				if (handlesAfter.size() <= handlesBeforeClick.size()) {
					return null;
				}

				return handlesAfter.stream().filter(handle -> !handlesBeforeClick.contains(handle)).findFirst().orElse(currentHandle);
			});
		} catch (final TimeoutException ignored) {
			return null;
		}
	}

	private void clickByAnyText(final List<String> texts) {
		final WebElement element = waitForClickableByAnyText(texts);
		scrollIntoView(element);
		element.click();
		waitForDocumentReady();
	}

	private void selectGoogleAccountIfPrompted() {
		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);
		try {
			final WebElement account = shortWait.until(ExpectedConditions.elementToBeClickable(
					By.xpath("//*[contains(normalize-space(.)," + quoted(GOOGLE_ACCOUNT_EMAIL) + ")]")));
			scrollIntoView(account);
			account.click();
			waitForDocumentReady();
		} catch (final TimeoutException ignored) {
			// Account chooser does not always appear when there is an existing session.
		}
	}

	private void assertVisibleText(final String text) {
		final WebElement element = waitForVisibleByAnyText(Arrays.asList(text));
		assertTrue("Expected visible text was not found: " + text, element.isDisplayed());
	}

	private boolean isVisibleTextPresent(final String text) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);
			shortWait.until(ExpectedConditions.visibilityOfElementLocated(textLocator(text)));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private WebElement waitForClickableByAnyText(final List<String> texts) {
		final List<By> locators = texts.stream().map(this::clickableTextLocator).collect(Collectors.toList());
		return wait.until(d -> {
			for (final By locator : locators) {
				try {
					final List<WebElement> elements = d.findElements(locator);
					for (final WebElement element : elements) {
						if (element.isDisplayed() && element.isEnabled()) {
							return element;
						}
					}
				} catch (final NoSuchElementException ignored) {
					// Keep iterating through fallback locators.
				}
			}
			return null;
		});
	}

	private WebElement waitForVisibleByAnyText(final List<String> texts) {
		final List<By> locators = texts.stream().map(this::textLocator).collect(Collectors.toList());
		return wait.until(d -> {
			for (final By locator : locators) {
				try {
					final List<WebElement> elements = d.findElements(locator);
					for (final WebElement element : elements) {
						if (element.isDisplayed()) {
							return element;
						}
					}
				} catch (final NoSuchElementException ignored) {
					// Keep iterating through fallback locators.
				}
			}
			return null;
		});
	}

	private By clickableTextLocator(final String text) {
		final String content = quoted(text);
		return By.xpath(
				"//button[contains(normalize-space(.)," + content + ")]"
						+ " | //a[contains(normalize-space(.)," + content + ")]"
						+ " | //*[@role='button' and contains(normalize-space(.)," + content + ")]"
						+ " | //div[contains(@class,'menu') and contains(normalize-space(.)," + content + ")]"
						+ " | //li[contains(normalize-space(.)," + content + ")]"
						+ " | //span[contains(normalize-space(.)," + content + ")]");
	}

	private By textLocator(final String text) {
		final String content = quoted(text);
		return By.xpath(
				"//*[self::h1 or self::h2 or self::h3 or self::h4 or self::p or self::span or self::a or self::button or self::div or self::li]"
						+ "[contains(normalize-space(.)," + content + ")]");
	}

	private List<String> legalTextVariants(final String legalName) {
		if ("Términos y Condiciones".equals(legalName)) {
			return Arrays.asList("Términos y Condiciones", "Terminos y Condiciones");
		}

		if ("Política de Privacidad".equals(legalName)) {
			return Arrays.asList("Política de Privacidad", "Politica de Privacidad");
		}

		return Arrays.asList(legalName);
	}

	private boolean runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, Boolean.TRUE);
			return true;
		} catch (final Exception ex) {
			report.put(stepName, Boolean.FALSE);
			System.err.println("Step failed: " + stepName);
			ex.printStackTrace();
			return false;
		}
	}

	private void markRemainingStepsAsFailed(final String fromField) {
		int index = REPORT_FIELDS.indexOf(fromField);
		if (index < 0) {
			index = 0;
		}

		for (int i = index; i < REPORT_FIELDS.size(); i++) {
			report.putIfAbsent(REPORT_FIELDS.get(i), Boolean.FALSE);
		}
	}

	private void setupScreenshotDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		screenshotDir = Paths.get("target", "e2e-screenshots", "saleads_mi_negocio_full_test", timestamp);
		Files.createDirectories(screenshotDir);
	}

	private void captureScreenshot(final String name) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = screenshotDir.resolve(sanitizeForFileName(name) + ".png");
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private String sanitizeForFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	private void waitForDocumentReady() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void initializeReport() {
		report.clear();
		for (final String field : REPORT_FIELDS) {
			report.put(field, Boolean.FALSE);
		}
	}

	private void printFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("\n=== SaleADS Mi Negocio Workflow Report ===\n");
		for (final String field : REPORT_FIELDS) {
			final boolean passed = report.getOrDefault(field, Boolean.FALSE);
			builder.append(String.format("%s: %s%n", field, passed ? "PASS" : "FAIL"));
		}

		if (!legalUrls.isEmpty()) {
			builder.append("\nLegal URLs:\n");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				builder.append(String.format("- %s: %s%n", entry.getKey(), entry.getValue()));
			}
		}

		builder.append("\nScreenshots directory: ").append(screenshotDir).append('\n');
		System.out.println(builder);
	}

	private void assertAllPassed() {
		final List<String> failedFields = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			if (!report.getOrDefault(field, Boolean.FALSE)) {
				failedFields.add(field);
			}
		}

		assertTrue("One or more required validations failed: " + failedFields, failedFields.isEmpty());
	}

	private String quoted(final String value) {
		return "'" + value.replace("'", "\\'") + "'";
	}

	private String envOrDefault(final String key, final String fallback) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		return value;
	}

	private String trimToNull(final String value) {
		if (value == null) {
			return null;
		}

		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private void tearDownDriver() {
		if (driver != null) {
			try {
				driver.quit();
			} catch (final Exception ignored) {
				// Keep cleanup quiet to avoid masking the real assertion failure.
			}
			driver = null;
			wait = null;
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
