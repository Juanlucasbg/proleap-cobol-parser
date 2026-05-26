package io.proleap.saleads;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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
import org.openqa.selenium.support.ui.WebDriverWait;

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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;

	@Before
	public void setUp() throws IOException {
		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-popup-blocking");
		options.addArguments("--lang=es-ES");

		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(readTimeoutSeconds()));

		evidenceDirectory = Paths.get("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDirectory);

		final String loginUrl = readRequiredConfig("saleads.loginUrl", "SALEADS_LOGIN_URL");
		driver.get(loginUrl);
		waitForUiToSettle();
	}

	@Test
	public void validateMiNegocioWorkflow() throws Exception {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones",
				() -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "terminos-y-condiciones.png"));
		executeStep("Política de Privacidad",
				() -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "politica-de-privacidad.png"));

		final String report = buildFinalReport();
		writeFinalReport(report);
		System.out.println(report);

		final boolean allPassed = REPORT_FIELDS.stream().allMatch(step -> Boolean.TRUE.equals(stepResults.get(step)));
		if (!allPassed) {
			Assert.fail(report);
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		final Set<String> windowHandlesBeforeLogin = new LinkedHashSet<>(driver.getWindowHandles());
		final String appWindowBeforeLogin = driver.getWindowHandle();

		clickAnyVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiToSettle();

		switchToAnyNewWindow(windowHandlesBeforeLogin);
		selectGoogleAccountIfPrompted();
		switchBackToAppWindow(appWindowBeforeLogin);

		waitForVisibleText("Negocio", 90);
		waitForVisibleElement(By.xpath("//aside | //nav"), 30);
		takeScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickVisibleTextIfPresent("Negocio", 10);
		clickAnyVisibleText("Mi Negocio");
		waitForVisibleText("Agregar Negocio", 30);
		waitForVisibleText("Administrar Negocios", 30);
		takeScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickAnyVisibleText("Agregar Negocio");
		waitForVisibleText("Crear Nuevo Negocio", 30);
		waitForVisibleText("Nombre del Negocio", 30);
		waitForVisibleText("Tienes 2 de 3 negocios", 30);
		waitForVisibleText("Cancelar", 30);
		waitForVisibleText("Crear Negocio", 30);

		final WebElement nombreNegocioInput = waitForVisibleElement(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio' "
						+ "or ancestor::*[.//*[normalize-space()='Nombre del Negocio']]//input]"),
				15);
		nombreNegocioInput.click();
		nombreNegocioInput.clear();
		nombreNegocioInput.sendKeys("Negocio Prueba Automatización");
		takeScreenshot("03-agregar-negocio-modal.png");
		clickAnyVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		clickVisibleTextIfPresent("Mi Negocio", 10);
		clickAnyVisibleText("Administrar Negocios");
		waitForVisibleText("Información General", 30);
		waitForVisibleText("Detalles de la Cuenta", 30);
		waitForVisibleText("Tus Negocios", 30);
		waitForVisibleText("Sección Legal", 30);
		takeScreenshot("04-administrar-negocios.png");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		final WebElement section = sectionContainerByHeading("Información General");
		final String sectionText = section.getText();

		final Matcher emailMatcher = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(sectionText);
		Assert.assertTrue("No se encontró un correo visible en Información General.", emailMatcher.find());

		final String cleaned = sectionText
				.replace("Información General", "")
				.replace("BUSINESS PLAN", "")
				.replace("Cambiar Plan", "")
				.replace(emailMatcher.group(), "")
				.trim();
		Assert.assertTrue("No se detectó un nombre visible en Información General.",
				Pattern.compile("\\b\\p{L}{2,}\\b").matcher(cleaned).find());

		Assert.assertTrue("No se encontró el texto BUSINESS PLAN.", sectionText.contains("BUSINESS PLAN"));
		Assert.assertTrue("No se encontró el botón Cambiar Plan.", sectionText.contains("Cambiar Plan"));
	}

	private void stepValidateDetallesCuenta() throws Exception {
		final WebElement section = sectionContainerByHeading("Detalles de la Cuenta");
		final String sectionText = section.getText();

		Assert.assertTrue("No se encontró 'Cuenta creada'.", sectionText.contains("Cuenta creada"));
		Assert.assertTrue("No se encontró 'Estado activo'.", sectionText.contains("Estado activo"));
		Assert.assertTrue("No se encontró 'Idioma seleccionado'.", sectionText.contains("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() throws Exception {
		final WebElement section = sectionContainerByHeading("Tus Negocios");
		final String sectionText = section.getText();

		Assert.assertFalse("La sección Tus Negocios no contiene información visible.", sectionText.trim().isEmpty());
		Assert.assertTrue("No se encontró el botón Agregar Negocio en Tus Negocios.", sectionText.contains("Agregar Negocio"));
		Assert.assertTrue("No se encontró el texto 'Tienes 2 de 3 negocios' en Tus Negocios.",
				sectionText.contains("Tienes 2 de 3 negocios"));
	}

	private void stepValidateLegalLink(final String linkText, final String headingText, final String screenshotName)
			throws Exception {
		waitForVisibleText("Sección Legal", 30);

		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		clickAnyVisibleText(linkText);

		final String openedHandle = waitForNewWindow(handlesBefore, 15);
		final boolean openedInNewTab = openedHandle != null;

		if (openedInNewTab) {
			driver.switchTo().window(openedHandle);
			waitForUiToSettle();
		}

		waitForVisibleText(headingText, 40);
		assertLegalContentVisible();
		takeScreenshot(screenshotName);
		legalUrls.put(headingText, driver.getCurrentUrl());

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToSettle();
		} else {
			driver.navigate().back();
			waitForVisibleText("Sección Legal", 40);
		}
	}

	private void executeStep(final String stepName, final CheckedRunnable action) {
		try {
			action.run();
			stepResults.put(stepName, true);
		} catch (final Exception | AssertionError ex) {
			stepResults.put(stepName, false);
			stepErrors.put(stepName, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
			safeFailureScreenshot(stepName);
		}
	}

	private WebElement sectionContainerByHeading(final String headingText) {
		final WebElement heading = waitForVisibleText(headingText, 30);
		try {
			return heading.findElement(By.xpath(
					"./ancestor::section[1] | ./ancestor::article[1] | ./ancestor::div[contains(@class, 'card')][1] | ./ancestor::div[1]"));
		} catch (final NoSuchElementException ex) {
			return heading;
		}
	}

	private void selectGoogleAccountIfPrompted() {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(45).toMillis();
		while (System.currentTimeMillis() < deadline) {
			final Set<String> handles = driver.getWindowHandles();
			for (final String handle : handles) {
				driver.switchTo().window(handle);
				final WebElement accountOption = findVisibleElementByText(GOOGLE_ACCOUNT_EMAIL, true);
				if (accountOption != null) {
					clickElement(accountOption);
					waitForUiToSettle();
					return;
				}
			}
			sleep(500);
		}
	}

	private void switchBackToAppWindow(final String originalWindow) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(90).toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (hasVisibleText("Negocio")) {
					return;
				}
			}
			sleep(750);
		}
		driver.switchTo().window(originalWindow);
		waitForUiToSettle();
	}

	private void switchToAnyNewWindow(final Set<String> previousHandles) {
		final String newHandle = waitForNewWindow(previousHandles, 10);
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiToSettle();
		}
	}

	private String waitForNewWindow(final Set<String> previousHandles, final long timeoutSeconds) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(timeoutSeconds).toMillis();
		while (System.currentTimeMillis() < deadline) {
			final Set<String> currentHandles = driver.getWindowHandles();
			if (currentHandles.size() > previousHandles.size()) {
				for (final String handle : currentHandles) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}
			}
			sleep(300);
		}
		return null;
	}

	private void assertLegalContentVisible() {
		final List<WebElement> contentCandidates = driver.findElements(By.xpath(
				"//main//*[self::p or self::li][string-length(normalize-space()) > 35] "
						+ "| //article//*[self::p or self::li][string-length(normalize-space()) > 35] "
						+ "| //body//*[self::p or self::li][string-length(normalize-space()) > 35]"));

		boolean hasVisibleContent = false;
		for (final WebElement contentCandidate : contentCandidates) {
			if (isDisplayed(contentCandidate)) {
				hasVisibleContent = true;
				break;
			}
		}
		Assert.assertTrue("No se detectó contenido legal visible.", hasVisibleContent);
	}

	private void clickAnyVisibleText(final String... texts) {
		final List<String> notFound = new ArrayList<>();
		for (final String text : texts) {
			final WebElement clickable = waitForClickableText(text, 10);
			if (clickable != null) {
				clickElement(clickable);
				waitForUiToSettle();
				return;
			}
			notFound.add(text);
		}
		Assert.fail("No se pudo hacer click en ningún texto visible: " + String.join(", ", notFound));
	}

	private void clickVisibleTextIfPresent(final String text, final long timeoutSeconds) {
		final WebElement clickable = waitForClickableText(text, timeoutSeconds);
		if (clickable != null) {
			clickElement(clickable);
			waitForUiToSettle();
		}
	}

	private WebElement waitForVisibleText(final String text, final long timeoutSeconds) {
		final WebDriverWait customWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		return customWait.until(d -> {
			final WebElement element = findVisibleElementByText(text, false);
			if (element != null) {
				return element;
			}
			return findVisibleElementByText(text, true);
		});
	}

	private WebElement waitForVisibleElement(final By locator, final long timeoutSeconds) {
		final WebDriverWait customWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		return customWait.until(d -> {
			final List<WebElement> elements = d.findElements(locator);
			for (final WebElement element : elements) {
				if (isDisplayed(element)) {
					return element;
				}
			}
			return null;
		});
	}

	private WebElement waitForClickableText(final String text, final long timeoutSeconds) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(timeoutSeconds).toMillis();
		while (System.currentTimeMillis() < deadline) {
			final WebElement candidate = findClickableElementByText(text);
			if (candidate != null) {
				return candidate;
			}
			sleep(250);
		}
		return null;
	}

	private WebElement findClickableElementByText(final String text) {
		final String literal = asXPathLiteral(text);
		final List<By> clickLocators = Arrays.asList(
				By.xpath("//button[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"),
				By.xpath("//a[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"),
				By.xpath("//*[@role='button' and (normalize-space()=" + literal + " or contains(normalize-space(), " + literal + "))]"),
				By.xpath(
						"//*[self::span or self::div][normalize-space()=" + literal + " or contains(normalize-space(), " + literal
								+ ")]/ancestor::*[self::button or self::a or @role='button'][1]"));

		for (final By locator : clickLocators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (isDisplayed(element) && isEnabled(element)) {
					return element;
				}
			}
		}
		return null;
	}

	private WebElement findVisibleElementByText(final String text, final boolean contains) {
		final String literal = asXPathLiteral(text);
		final By locator = contains
				? By.xpath("//*[contains(normalize-space(), " + literal + ")]")
				: By.xpath("//*[normalize-space()=" + literal + "]");

		for (final WebElement element : driver.findElements(locator)) {
			if (isDisplayed(element)) {
				return element;
			}
		}
		return null;
	}

	private boolean hasVisibleText(final String text) {
		try {
			return findVisibleElementByText(text, true) != null;
		} catch (final Exception ex) {
			return false;
		}
	}

	private void clickElement(final WebElement element) {
		try {
			scrollIntoView(element);
			new Actions(driver).moveToElement(element).pause(Duration.ofMillis(100)).perform();
			element.click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center', inline: 'center'});", element);
	}

	private void waitForUiToSettle() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final TimeoutException timeoutException) {
			// some SPA transitions may not flip readyState quickly; continue with explicit pause.
		}
		sleep(500);
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), evidenceDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private void safeFailureScreenshot(final String stepName) {
		try {
			takeScreenshot("failure-" + slug(stepName) + ".png");
		} catch (final IOException ignored) {
			// no-op
		}
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio - Final Report\n");
		builder.append("Evidence directory: ").append(evidenceDirectory).append('\n');
		builder.append('\n');

		for (final String field : REPORT_FIELDS) {
			final boolean passed = Boolean.TRUE.equals(stepResults.get(field));
			builder.append(field).append(": ").append(passed ? "PASS" : "FAIL");
			if (!passed && stepErrors.containsKey(field)) {
				builder.append(" - ").append(stepErrors.get(field));
			}
			builder.append('\n');
		}

		builder.append('\n');
		builder.append("Captured legal URLs:\n");
		builder.append("Términos y Condiciones URL: ")
				.append(legalUrls.getOrDefault("Términos y Condiciones", "N/A"))
				.append('\n');
		builder.append("Política de Privacidad URL: ")
				.append(legalUrls.getOrDefault("Política de Privacidad", "N/A"))
				.append('\n');

		return builder.toString();
	}

	private void writeFinalReport(final String report) throws IOException {
		Files.writeString(evidenceDirectory.resolve("final-report.txt"), report, StandardCharsets.UTF_8);
	}

	private String readRequiredConfig(final String propertyName, final String envName) {
		final String value = readConfig(propertyName, envName, null);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing required configuration. Provide system property '" + propertyName
					+ "' or environment variable '" + envName + "'.");
		}
		return value.trim();
	}

	private String readConfig(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private long readTimeoutSeconds() {
		final String value = readConfig("saleads.timeoutSeconds", "SALEADS_TIMEOUT_SECONDS", "30");
		try {
			return Long.parseLong(value);
		} catch (final NumberFormatException ex) {
			return 30;
		}
	}

	private String asXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final String[] parts = text.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(",\"'\",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String slug(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private boolean isDisplayed(final WebElement element) {
		try {
			return element != null && element.isDisplayed();
		} catch (final StaleElementReferenceException ignored) {
			return false;
		}
	}

	private boolean isEnabled(final WebElement element) {
		try {
			return element != null && element.isEnabled();
		} catch (final StaleElementReferenceException ignored) {
			return false;
		}
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
