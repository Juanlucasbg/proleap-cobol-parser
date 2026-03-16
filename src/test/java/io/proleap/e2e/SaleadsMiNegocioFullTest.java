/*
 * Copyright (C) 2026
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package io.proleap.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * E2E workflow test for SaleADS "Mi Negocio" module.
 *
 * <p>
 * The test is intentionally environment agnostic. Provide SALEADS_URL as an
 * environment variable and run this test with SALEADS_E2E_ENABLED=true.
 * </p>
 */
public class SaleadsMiNegocioFullTest {

	private static final String EMAIL_TO_SELECT = "juanlucasbarbiergarzon@gmail.com";
	private static final String HEADING_INFO_GENERAL = "Informaci\u00F3n General";
	private static final String HEADING_DETALLES = "Detalles de la Cuenta";
	private static final String HEADING_TUS_NEGOCIOS = "Tus Negocios";
	private static final String HEADING_LEGAL = "Secci\u00F3n Legal";
	private static final String HEADING_TERMINOS = "T\u00E9rminos y Condiciones";
	private static final String HEADING_PRIVACIDAD = "Pol\u00EDtica de Privacidad";
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, String> evidence = new LinkedHashMap<>();
	private final Instant testStartedAt = Instant.now();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path reportDirectory;
	private Path screenshotsDirectory;

	@Before
	public void setUp() throws IOException {
		final boolean e2eEnabled = Boolean.parseBoolean(envOrDefault("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Skipping SaleADS E2E. Set SALEADS_E2E_ENABLED=true to execute.", e2eEnabled);

		reportDirectory = Path.of(envOrDefault("SALEADS_E2E_REPORT_DIR", "target/saleads-e2e"));
		screenshotsDirectory = reportDirectory.resolve("screenshots");
		Files.createDirectories(screenshotsDirectory);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(25));
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
	public void saleads_mi_negocio_full_test() throws Exception {
		final String saleadsUrl = envOrDefault("SALEADS_URL", "").trim();
		Assume.assumeTrue("Skipping SaleADS E2E. Set SALEADS_URL to the login page of your environment.",
				!saleadsUrl.isEmpty());

		driver.get(saleadsUrl);
		waitForUiLoad();

		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Informaci\u00F3n General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("T\u00E9rminos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Pol\u00EDtica de Privacidad", this::stepValidatePoliticaPrivacidad);

		assertAllStepsPassed();
	}

	private void stepLoginWithGoogle() throws Exception {
		if (!isAnyTextVisible("Negocio", "Mi Negocio")) {
			clickByVisibleText("Sign in with Google", "Continue with Google", "Login with Google",
					"Iniciar sesi\u00F3n con Google", "Iniciar sesion con Google");
			waitForUiLoad();
		}

		selectGoogleAccountIfVisible(EMAIL_TO_SELECT);
		waitForUiLoad();

		assertTrue("Expected app interface to be visible after login.",
				isAnyTextVisible("Negocio", "Mi Negocio", "Administrar Negocios"));
		assertTrue("Expected left sidebar navigation to be visible.", isSidebarVisible());

		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		assertTextVisible("Submenu item 'Agregar Negocio' is not visible.", "Agregar Negocio");
		assertTextVisible("Submenu item 'Administrar Negocios' is not visible.", "Administrar Negocios");

		captureScreenshot("02_menu_mi_negocio_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForUiLoad();

		assertTextVisible("Modal title 'Crear Nuevo Negocio' was not found.", "Crear Nuevo Negocio");
		waitForAnyVisible(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));
		assertTextVisible("Text 'Tienes 2 de 3 negocios' is not visible.", "Tienes 2 de 3 negocios");
		assertTextVisible("Button 'Cancelar' is not visible.", "Cancelar");
		assertTextVisible("Button 'Crear Negocio' is not visible.", "Crear Negocio");

		captureScreenshot("03_agregar_negocio_modal");

		final WebElement nameInput = findFirstVisibleElement(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));
		nameInput.click();
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatizacion");
		waitForUiLoad();

		clickByVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioSubmenuExpanded();
		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertAnyTextVisible("Section 'Informacion General' is not visible.", HEADING_INFO_GENERAL, "Informacion General");
		assertAnyTextVisible("Section 'Detalles de la Cuenta' is not visible.", HEADING_DETALLES);
		assertAnyTextVisible("Section 'Tus Negocios' is not visible.", HEADING_TUS_NEGOCIOS);
		assertAnyTextVisible("Section 'Seccion Legal' is not visible.", HEADING_LEGAL, "Seccion Legal");

		captureScreenshot("04_administrar_negocios_view");
	}

	private void stepValidateInformacionGeneral() {
		final String pageText = normalizedText(driver.findElement(By.tagName("body")).getText());

		assertAnyTextVisible("Expected section heading Informacion General.", HEADING_INFO_GENERAL, "Informacion General");
		assertTrue("Expected a user email to be visible in Informacion General or page.",
				EMAIL_PATTERN.matcher(pageText).find());
		assertAnyTextVisible("Expected text 'BUSINESS PLAN' to be visible.", "BUSINESS PLAN");
		assertAnyTextVisible("Expected button 'Cambiar Plan' to be visible.", "Cambiar Plan");

		// Heuristic: a likely user name should appear somewhere near account information.
		assertTrue("Expected a likely user name to be visible.", hasLikelyUserName(pageText));
	}

	private void stepValidateDetallesCuenta() {
		assertAnyTextVisible("Expected text 'Cuenta creada' to be visible.", "Cuenta creada");
		assertAnyTextVisible("Expected text indicating active status.", "Estado activo", "Activo");
		assertAnyTextVisible("Expected text 'Idioma seleccionado' to be visible.", "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertAnyTextVisible("Expected section heading 'Tus Negocios'.", HEADING_TUS_NEGOCIOS);
		assertAnyTextVisible("Expected button 'Agregar Negocio'.", "Agregar Negocio");
		assertAnyTextVisible("Expected text 'Tienes 2 de 3 negocios'.", "Tienes 2 de 3 negocios");

		final WebElement section = findClosestSectionForText(HEADING_TUS_NEGOCIOS);
		final boolean hasBusinessList = !section.findElements(By.xpath(".//li|.//tr|.//*[contains(@class, 'business')]"))
				.isEmpty() || section.getText().split("\\R").length >= 3;
		assertTrue("Expected visible business list in 'Tus Negocios' section.", hasBusinessList);
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		final String finalUrl = validateLegalLinkAndReturn("T\u00E9rminos y Condiciones", "Terminos y Condiciones",
				HEADING_TERMINOS, "Terminos y Condiciones", "05_terminos_condiciones");
		evidence.put("terminos_url", finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		final String finalUrl = validateLegalLinkAndReturn(HEADING_PRIVACIDAD, "Politica de Privacidad",
				HEADING_PRIVACIDAD, "Politica de Privacidad", "06_politica_privacidad");
		evidence.put("politica_privacidad_url", finalUrl);
	}

	private String validateLegalLinkAndReturn(final String linkTextPrimary, final String linkTextFallback,
			final String headingPrimary, final String headingFallback, final String screenshotName) throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String previousUrl = driver.getCurrentUrl();

		clickByVisibleText(linkTextPrimary, linkTextFallback);

		boolean openedNewTab = false;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15))
					.until(d -> d.getWindowHandles().size() > handlesBefore.size()
							|| !d.getCurrentUrl().equalsIgnoreCase(previousUrl));
		} catch (final TimeoutException ignored) {
			// Continue with current context and perform validations anyway.
		}

		final Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > handlesBefore.size()) {
			for (final String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					openedNewTab = true;
					break;
				}
			}
		}

		waitForUiLoad();

		assertAnyTextVisible("Expected legal page heading to be visible.", headingPrimary, headingFallback);
		final String bodyText = normalizedText(driver.findElement(By.tagName("body")).getText());
		assertTrue("Expected legal content text to be visible.", bodyText.length() > 200);

		captureScreenshot(screenshotName);
		final String legalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiLoad();
		assertAnyTextVisible("Expected to return to account/application page.", HEADING_INFO_GENERAL, "Informacion General");
		return legalUrl;
	}

	private void ensureMiNegocioSubmenuExpanded() throws Exception {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiLoad();
		}
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) throws Exception {
		final By accountLocator = By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(accountEmail) + ")]");
		if (isElementVisible(accountLocator, Duration.ofSeconds(10))) {
			final WebElement account = wait.until(ExpectedConditions.elementToBeClickable(accountLocator));
			account.click();
			waitForUiLoad();
		}
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			stepResults.put(reportField, StepResult.pass());
		} catch (final Throwable ex) {
			stepResults.put(reportField, StepResult.fail(ex.getMessage()));
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			if (!entry.getValue().passed) {
				failedSteps.add(entry.getKey() + " -> " + entry.getValue().message);
			}
		}

		assertTrue(
				"One or more SaleADS Mi Negocio validations failed:\n - "
						+ String.join("\n - ", failedSteps)
						+ "\nSee report in: " + reportDirectory.toAbsolutePath(),
				failedSteps.isEmpty());
	}

	private void writeFinalReport() throws IOException {
		if (reportDirectory == null) {
			return;
		}

		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"executedAt\": \"").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("\",\n");
		json.append("  \"startedAt\": \"").append(DateTimeFormatter.ISO_INSTANT.format(testStartedAt)).append("\",\n");
		json.append("  \"results\": {\n");

		final List<String> orderedFields = List.of(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Informaci\u00F3n General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"T\u00E9rminos y Condiciones",
				"Pol\u00EDtica de Privacidad");

		for (int i = 0; i < orderedFields.size(); i++) {
			final String field = orderedFields.get(i);
			final StepResult result = stepResults.getOrDefault(field, StepResult.fail("Not executed"));
			json.append("    \"").append(escapeJson(field)).append("\": {\n");
			json.append("      \"status\": \"").append(result.passed ? "PASS" : "FAIL").append("\",\n");
			json.append("      \"message\": \"").append(escapeJson(result.message)).append("\"\n");
			json.append("    }");
			if (i < orderedFields.size() - 1) {
				json.append(",");
			}
			json.append("\n");
		}
		json.append("  },\n");

		json.append("  \"evidence\": {\n");
		int index = 0;
		for (final Map.Entry<String, String> entry : evidence.entrySet()) {
			json.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(escapeJson(entry.getValue())).append("\"");
			if (index < evidence.size() - 1) {
				json.append(",");
			}
			json.append("\n");
			index++;
		}
		json.append("  }\n");
		json.append("}\n");

		final Path reportPath = reportDirectory.resolve("saleads_mi_negocio_full_test_report.json");
		Files.writeString(reportPath, json.toString(), StandardCharsets.UTF_8);
	}

	private void captureScreenshot(final String checkpoint) throws IOException {
		final String filename = checkpoint + "_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(
				java.time.ZonedDateTime.now()) + ".png";
		final Path targetPath = screenshotsDirectory.resolve(filename);
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
		evidence.put("screenshot_" + checkpoint, targetPath.toAbsolutePath().toString());
	}

	private void waitForUiLoad() throws Exception {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		Thread.sleep(400L);
	}

	private void clickByVisibleText(final String... texts) throws Exception {
		final WebElement element = findClickableByVisibleText(texts);
		try {
			element.click();
		} catch (final Exception ignored) {
			new Actions(driver).moveToElement(element).click().perform();
		}
		waitForUiLoad();
	}

	private WebElement findClickableByVisibleText(final String... texts) {
		for (final String text : texts) {
			final List<By> locators = List.of(
					By.xpath("//button[normalize-space()=" + toXPathLiteral(text) + "]"),
					By.xpath("//a[normalize-space()=" + toXPathLiteral(text) + "]"),
					By.xpath("//*[@role='button' and normalize-space()=" + toXPathLiteral(text) + "]"),
					By.xpath("//*[self::span or self::div][normalize-space()=" + toXPathLiteral(text) + "]"));

			for (final By locator : locators) {
				try {
					return wait.until(ExpectedConditions.elementToBeClickable(locator));
				} catch (final TimeoutException ignored) {
					// Try next locator.
				}
			}
		}
		throw new AssertionError("Could not find clickable element with visible text: " + String.join(", ", texts));
	}

	private void assertTextVisible(final String failureMessage, final String text) {
		assertTrue(failureMessage, isAnyTextVisible(text));
	}

	private void assertAnyTextVisible(final String failureMessage, final String... texts) {
		assertTrue(failureMessage, isAnyTextVisible(texts));
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			if (isElementVisible(By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]"),
					Duration.ofSeconds(4))) {
				return true;
			}
		}
		return false;
	}

	private boolean isElementVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private boolean isSidebarVisible() {
		final List<WebElement> sidebars = driver.findElements(By.xpath(
				"//aside | //nav[contains(@class, 'sidebar')] | //*[@role='navigation']"));
		for (final WebElement sidebar : sidebars) {
			if (sidebar.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void waitForAnyVisible(final By... locators) {
		for (final By locator : locators) {
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return;
			} catch (final TimeoutException ignored) {
				// Try next locator.
			}
		}
		throw new AssertionError("None of the expected elements became visible.");
	}

	private WebElement findFirstVisibleElement(final By... locators) {
		for (final By locator : locators) {
			final List<WebElement> matches = driver.findElements(locator);
			for (final WebElement match : matches) {
				if (match.isDisplayed()) {
					return match;
				}
			}
		}
		throw new AssertionError("No visible element found for supplied locators.");
	}

	private WebElement findClosestSectionForText(final String headingText) {
		final WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(headingText) + ")]")));
		final List<By> containerLocators = List.of(
				By.xpath("./ancestor::section[1]"),
				By.xpath("./ancestor::article[1]"),
				By.xpath("./ancestor::div[1]"));

		for (final By locator : containerLocators) {
			final List<WebElement> containers = heading.findElements(locator);
			if (!containers.isEmpty()) {
				return containers.get(0);
			}
		}

		return heading;
	}

	private boolean hasLikelyUserName(final String pageText) {
		final String[] lines = pageText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.length() < 4 || line.length() > 60) {
				continue;
			}
			final String lowercase = line.toLowerCase();
			if (lowercase.contains("@") || lowercase.contains("informacion") || lowercase.contains("business plan")
					|| lowercase.contains("cambiar plan") || lowercase.contains("cuenta")
					|| lowercase.contains("estado")) {
				continue;
			}
			if (line.matches(".*[A-Za-z\\u00C0-\\u017F]{2,}.*")) {
				return true;
			}
		}
		return false;
	}

	private String envOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value;
	}

	private String normalizedText(final String input) {
		return input == null ? "" : input.replace('\u00A0', ' ').trim();
	}

	private String escapeJson(final String value) {
		return value == null ? "" : value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String message;

		private StepResult(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message == null ? "" : message;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String message) {
			return new StepResult(false, message);
		}
	}
}
