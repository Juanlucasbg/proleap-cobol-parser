package io.saleads.e2e;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final Duration MEDIUM_TIMEOUT = Duration.ofSeconds(20);
	private static final Duration LONG_TIMEOUT = Duration.ofSeconds(40);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private Path evidenceDir;

	@Test
	public void saleadsMiNegocioFullTest() {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run SaleADS E2E workflow validation.",
				Boolean.parseBoolean(readConfig("SALEADS_E2E_ENABLED", "false")));

		try {
			initializeDriverAndEvidence();
			openLoginPageIfConfigured();

			runStep("Login", this::validateLoginWithGoogle);
			runStep("Mi Negocio menu", this::openMiNegocioMenu);
			runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::openAdministrarNegociosView);
			runStep("Información General", this::validateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
			runStep("Tus Negocios", this::validateTusNegocios);
			runStep("Términos y Condiciones", () -> validateLegalLink("Términos y Condiciones", "terminos_y_condiciones", "Términos y Condiciones"));
			runStep("Política de Privacidad", () -> validateLegalLink("Política de Privacidad", "politica_de_privacidad", "Política de Privacidad"));
		} finally {
			printFinalReport();
			closeDriver();
		}

		assertAllValidationsPassed();
	}

	private void initializeDriverAndEvidence() {
		evidenceDir = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))).toAbsolutePath();

		try {
			Files.createDirectories(evidenceDir);
		} catch (final IOException e) {
			throw new IllegalStateException("Unable to create evidence directory: " + evidenceDir, e);
		}

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		final String debuggerAddress = readConfig("SALEADS_CHROME_DEBUGGER_ADDRESS", "");
		if (!debuggerAddress.isBlank()) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		}

		final String remoteWebDriverUrl = readConfig("SALEADS_SELENIUM_REMOTE_URL", "");
		if (!remoteWebDriverUrl.isBlank()) {
			try {
				driver = new RemoteWebDriver(new URL(remoteWebDriverUrl), options);
			} catch (final MalformedURLException e) {
				throw new IllegalArgumentException("Invalid SALEADS_SELENIUM_REMOTE_URL: " + remoteWebDriverUrl, e);
			}
		} else {
			driver = new ChromeDriver(options);
		}
	}

	private void openLoginPageIfConfigured() {
		final String startUrl = readConfig("SALEADS_START_URL", "");
		if (!startUrl.isBlank()) {
			driver.get(startUrl);
			waitForUiToLoad();
			return;
		}

		final String currentUrl = safeCurrentUrl();
		if (currentUrl == null || currentUrl.isBlank() || "about:blank".equals(currentUrl) || "data:,".equals(currentUrl)) {
			Assume.assumeTrue(
					"No SaleADS page available. Provide SALEADS_START_URL, or attach to an already-open Chrome with SALEADS_CHROME_DEBUGGER_ADDRESS.",
					false);
		}
	}

	private void validateLoginWithGoogle() throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeLoginClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickByAnyVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Ingresar con Google", "Google"), MEDIUM_TIMEOUT);

		switchToPopupIfOpened(handlesBeforeLoginClick);
		selectGoogleAccountIfVisible();
		switchBackToApplicationTab(appHandle);

		waitForUiToLoad();
		assertVisibleText("Negocio", LONG_TIMEOUT, "Left sidebar navigation should be visible after login.");
		assertVisibleText("Mi Negocio", LONG_TIMEOUT, "Main application interface should be visible after login.");
		captureScreenshot("01_dashboard_loaded");
	}

	private void openMiNegocioMenu() throws Exception {
		assertVisibleText("Negocio", MEDIUM_TIMEOUT, "Left sidebar navigation should be visible.");
		clickByAnyVisibleText(Arrays.asList("Mi Negocio"), MEDIUM_TIMEOUT);

		assertVisibleText("Agregar Negocio", MEDIUM_TIMEOUT, "Submenu should show 'Agregar Negocio'.");
		assertVisibleText("Administrar Negocios", MEDIUM_TIMEOUT, "Submenu should show 'Administrar Negocios'.");
		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickByAnyVisibleText(Arrays.asList("Agregar Negocio"), MEDIUM_TIMEOUT);

		assertVisibleText("Crear Nuevo Negocio", MEDIUM_TIMEOUT, "Modal title 'Crear Nuevo Negocio' should be visible.");
		final WebElement businessNameInput = waitForVisibleAnyLocator(Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]")), MEDIUM_TIMEOUT);

		Assert.assertNotNull("Input field 'Nombre del Negocio' should exist.", businessNameInput);
		assertVisibleText("Tienes 2 de 3 negocios", MEDIUM_TIMEOUT, "Business usage text should be visible.");
		assertVisibleText("Cancelar", MEDIUM_TIMEOUT, "Button 'Cancelar' should be visible.");
		assertVisibleText("Crear Negocio", MEDIUM_TIMEOUT, "Button 'Crear Negocio' should be visible.");
		captureScreenshot("03_agregar_negocio_modal");

		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");

		clickByAnyVisibleText(Arrays.asList("Cancelar"), MEDIUM_TIMEOUT);
		waitForTextToDisappear("Crear Nuevo Negocio");
	}

	private void openAdministrarNegociosView() throws Exception {
		if (!isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			clickByAnyVisibleText(Arrays.asList("Mi Negocio"), MEDIUM_TIMEOUT);
		}

		clickByAnyVisibleText(Arrays.asList("Administrar Negocios"), MEDIUM_TIMEOUT);

		assertVisibleText("Información General", LONG_TIMEOUT, "Section 'Información General' should exist.");
		assertVisibleText("Detalles de la Cuenta", LONG_TIMEOUT, "Section 'Detalles de la Cuenta' should exist.");
		assertVisibleText("Tus Negocios", LONG_TIMEOUT, "Section 'Tus Negocios' should exist.");
		assertVisibleText("Sección Legal", LONG_TIMEOUT, "Section 'Sección Legal' should exist.");
		captureScreenshot("04_administrar_negocios_page");
	}

	private void validateInformacionGeneral() throws Exception {
		assertVisibleText("Información General", MEDIUM_TIMEOUT, "Section 'Información General' should be visible.");
		assertVisibleText("BUSINESS PLAN", MEDIUM_TIMEOUT, "Text 'BUSINESS PLAN' should be visible.");
		assertVisibleText("Cambiar Plan", MEDIUM_TIMEOUT, "Button 'Cambiar Plan' should be visible.");

		final String pageText = normalizedBodyText();
		Assert.assertTrue("A user email should be visible in 'Información General'.", EMAIL_PATTERN.matcher(pageText).find());
		Assert.assertTrue("A user name indicator should be visible in 'Información General'.",
				pageText.contains("Nombre") || pageText.contains("Usuario") || pageText.contains("Perfil") || pageText.contains("Cuenta"));
	}

	private void validateDetallesCuenta() {
		assertVisibleText("Cuenta creada", MEDIUM_TIMEOUT, "Text 'Cuenta creada' should be visible.");
		assertVisibleText("Estado activo", MEDIUM_TIMEOUT, "Text 'Estado activo' should be visible.");
		assertVisibleText("Idioma seleccionado", MEDIUM_TIMEOUT, "Text 'Idioma seleccionado' should be visible.");
	}

	private void validateTusNegocios() {
		final WebElement section = waitForVisibleTextElement("Tus Negocios", MEDIUM_TIMEOUT);
		Assert.assertNotNull("Section 'Tus Negocios' should be visible.", section);
		assertVisibleText("Agregar Negocio", MEDIUM_TIMEOUT, "Button 'Agregar Negocio' should be visible in business section.");
		assertVisibleText("Tienes 2 de 3 negocios", MEDIUM_TIMEOUT, "Text 'Tienes 2 de 3 negocios' should be visible.");

		final String pageText = normalizedBodyText();
		Assert.assertTrue("Business list should be visible.", pageText.contains("Tus Negocios") && pageText.length() > 200);
	}

	private void validateLegalLink(final String linkText, final String screenshotName, final String expectedHeading) throws Exception {
		final String appTabHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String previousUrl = safeCurrentUrl();

		clickByAnyVisibleText(Arrays.asList(linkText), MEDIUM_TIMEOUT);
		switchToPopupIfOpened(handlesBeforeClick);
		waitForUiToLoad();

		assertVisibleText(expectedHeading, LONG_TIMEOUT, "Legal page heading '" + expectedHeading + "' should be visible.");

		final String bodyText = normalizedBodyText();
		Assert.assertTrue("Legal content text should be visible for '" + expectedHeading + "'.", bodyText.length() > 300);

		captureScreenshot(screenshotName);
		legalUrls.put(expectedHeading, safeCurrentUrl());

		if (!Objects.equals(driver.getWindowHandle(), appTabHandle)) {
			driver.close();
			driver.switchTo().window(appTabHandle);
			waitForUiToLoad();
		} else if (!Objects.equals(previousUrl, safeCurrentUrl())) {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void selectGoogleAccountIfVisible() {
		try {
			clickByAnyVisibleText(Arrays.asList(GOOGLE_ACCOUNT_EMAIL), SHORT_TIMEOUT);
			waitForUiToLoad();
		} catch (final Exception ignored) {
			// Account selector may not appear if Google account session is already active.
		}
	}

	private void switchBackToApplicationTab(final String appHandle) {
		try {
			new WebDriverWait(driver, LONG_TIMEOUT).until(d -> d.getWindowHandles().contains(appHandle));
			driver.switchTo().window(appHandle);
		} catch (final TimeoutException e) {
			// If the app handle is gone (e.g. full redirect in same tab), keep current tab.
		}
	}

	private void switchToPopupIfOpened(final Set<String> handlesBeforeClick) {
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(d -> d.getWindowHandles().size() > handlesBeforeClick.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					return;
				}
			}
		} catch (final TimeoutException ignored) {
			// No popup opened: navigation may have happened in the same tab.
		}
	}

	private void clickByAnyVisibleText(final List<String> candidates, final Duration timeout) throws Exception {
		Exception lastError = null;

		for (final String candidate : candidates) {
			try {
				final WebElement element = waitForVisibleTextElement(candidate, timeout);
				element.click();
				waitForUiToLoad();
				return;
			} catch (final Exception e) {
				lastError = e;
			}
		}

		throw new IllegalStateException("Could not click any element with visible text in " + candidates, lastError);
	}

	private WebElement waitForVisibleTextElement(final String text, final Duration timeout) {
		final By locator = By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]");
		return new WebDriverWait(driver, timeout).until(d -> {
			for (final WebElement element : d.findElements(locator)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private WebElement waitForVisibleAnyLocator(final List<By> locators, final Duration timeout) {
		By activeLocator = null;

		for (final By locator : locators) {
			activeLocator = locator;
			try {
				return new WebDriverWait(driver, timeout).until(d -> {
					for (final WebElement element : d.findElements(locator)) {
						if (element.isDisplayed()) {
							return element;
						}
					}
					return null;
				});
			} catch (final TimeoutException ignored) {
				// Try the next locator.
			}
		}

		throw new IllegalStateException("Could not find visible element for locators: " + activeLocator);
	}

	private void waitForTextToDisappear(final String text) {
		final By locator = By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]");
		new WebDriverWait(driver, MEDIUM_TIMEOUT).until(d -> {
			for (final WebElement element : d.findElements(locator)) {
				if (element.isDisplayed()) {
					return false;
				}
			}
			return true;
		});
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			return waitForVisibleTextElement(text, timeout) != null;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void assertVisibleText(final String text, final Duration timeout, final String message) {
		Assert.assertTrue(message, isTextVisible(text, timeout));
	}

	private String normalizedBodyText() {
		final WebElement body = driver.findElement(By.tagName("body"));
		return body.getText().replaceAll("\\s+", " ").trim();
	}

	private void waitForUiToLoad() {
		final WebDriverWait wait = new WebDriverWait(driver, LONG_TIMEOUT);
		wait.until(d -> {
			final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(readyState) || "interactive".equals(readyState);
		});
		wait.until(d -> ((JavascriptExecutor) d).executeScript("return !!document.body"));
	}

	private void captureScreenshot(final String name) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDir.resolve(name + ".png");

		try {
			Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException e) {
			throw new IllegalStateException("Unable to store screenshot " + destination, e);
		}
	}

	private String readConfig(final String name, final String defaultValue) {
		final String fromProperty = System.getProperty(name);
		if (fromProperty != null) {
			return fromProperty;
		}

		final String fromEnvironment = System.getenv(name);
		if (fromEnvironment != null) {
			return fromEnvironment;
		}

		return defaultValue;
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (final Exception e) {
			return "";
		}
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String character = String.valueOf(chars[i]);
			if ("'".equals(character)) {
				builder.append("\"").append(character).append("\"");
			} else {
				builder.append("'").append(character).append("'");
			}
			if (i < chars.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			report.put(reportField, Boolean.TRUE);
		} catch (final Exception e) {
			report.put(reportField, Boolean.FALSE);
			System.err.println("[FAIL] " + reportField + " -> " + e.getMessage());
			e.printStackTrace(System.err);
		}
	}

	private void printFinalReport() {
		final List<String> orderedFields = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view", "Información General",
				"Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones", "Política de Privacidad");

		System.out.println("=== saleads_mi_negocio_full_test FINAL REPORT ===");
		for (final String field : orderedFields) {
			final boolean status = Boolean.TRUE.equals(report.get(field));
			System.out.println(field + ": " + (status ? "PASS" : "FAIL"));
		}

		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			System.out.println(entry.getKey() + " URL: " + entry.getValue());
		}

		if (evidenceDir != null) {
			System.out.println("Evidence directory: " + evidenceDir);
		}
	}

	private void assertAllValidationsPassed() {
		final List<String> failedFields = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!Boolean.TRUE.equals(entry.getValue())) {
				failedFields.add(entry.getKey());
			}
		}
		Assert.assertTrue("Some validations failed: " + failedFields, failedFields.isEmpty());
	}

	private void closeDriver() {
		if (driver != null) {
			try {
				driver.quit();
			} catch (final Exception ignored) {
				// no-op
			}
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
