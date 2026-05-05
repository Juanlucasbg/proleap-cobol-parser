/*
 * Copyright (C) 2026
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.HasFullPageScreenshot;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * End-to-end flow for SaleADS "Mi Negocio" module.
 *
 * <p>
 * Usage:
 * </p>
 * <ul>
 * <li>Set SALEADS_E2E=true to enable this test.</li>
 * <li>Set SALEADS_LOGIN_URL to the environment-specific login URL.</li>
 * <li>Optional: set SALEADS_GOOGLE_ACCOUNT and SALEADS_HEADLESS.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final String UPPER_CASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ";
	private static final String LOWER_CASE = "abcdefghijklmnopqrstuvwxyzáéíóúüñ";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E=true to run this live UI test.",
				Boolean.parseBoolean(getEnv("SALEADS_E2E", "false")));

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL for the target environment.", loginUrl != null && !loginUrl.isBlank());

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (Boolean.parseBoolean(getEnv("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(Long.parseLong(getEnv("SALEADS_TIMEOUT_SECONDS", "30"))));
		evidenceDir = createEvidenceDirectory();
		initializeReport();

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final boolean loginOk = executeStep("Login", this::stepLoginWithGoogle);
		final boolean menuOk = executeStep("Mi Negocio menu", () -> {
			assertTrue("Login must succeed before opening Mi Negocio.", loginOk);
			stepOpenMiNegocioMenu();
		});
		final boolean modalOk = executeStep("Agregar Negocio modal", () -> {
			assertTrue("Mi Negocio menu must be available before opening modal.", menuOk);
			stepValidateAgregarNegocioModal();
		});
		final boolean administrarOk = executeStep("Administrar Negocios view", () -> {
			assertTrue("Mi Negocio menu must be available before opening Administrar Negocios.", menuOk);
			stepOpenAdministrarNegocios();
		});

		executeStep("Información General", () -> {
			assertTrue("Administrar Negocios view must be loaded first.", administrarOk);
			stepValidateInformacionGeneral();
		});
		executeStep("Detalles de la Cuenta", () -> {
			assertTrue("Administrar Negocios view must be loaded first.", administrarOk);
			stepValidateDetallesCuenta();
		});
		executeStep("Tus Negocios", () -> {
			assertTrue("Administrar Negocios view must be loaded first.", administrarOk);
			stepValidateTusNegocios();
		});
		executeStep("Términos y Condiciones", () -> {
			assertTrue("Administrar Negocios view must be loaded first.", administrarOk);
			stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos");
		});
		executeStep("Política de Privacidad", () -> {
			assertTrue("Administrar Negocios view must be loaded first.", administrarOk);
			stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09-privacidad");
		});

		final List<String> failedChecks = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		assertTrue("Some validations failed: " + failedChecks, failedChecks.isEmpty());
		assertTrue("Agregar Negocio modal step failed.", modalOk);
	}

	private void stepLoginWithGoogle() throws IOException {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiToLoad();

		final String googleAccount = getEnv("SALEADS_GOOGLE_ACCOUNT", "juanlucasbarbiergarzon@gmail.com");
		selectGoogleAccountIfVisible(googleAccount);
		waitForUiToLoad();

		assertAnyVisibleText("Mi Negocio", "Negocio");
		assertSidebarVisible();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		assertSidebarVisible();
		clickIfVisibleText("Negocio");
		waitForUiToLoad();
		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertAnyVisibleText("Crear Nuevo Negocio");
		final WebElement businessNameField = waitForVisible(findInputForLabelOrPlaceholder("Nombre del Negocio"));
		assertAnyVisibleText("Tienes 2 de 3 negocios");
		assertAnyVisibleText("Cancelar");
		assertAnyVisibleText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		businessNameField.click();
		businessNameField.clear();
		businessNameField.sendKeys("Negocio Prueba Automatizacion");
		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isAnyVisibleTextPresent("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertAnyVisibleText("Información General");
		assertAnyVisibleText("Detalles de la Cuenta");
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Sección Legal");
		takeScreenshot("04-administrar-negocios", true);
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisibleText("Información General");
		assertAnyVisibleText("BUSINESS PLAN");
		assertAnyVisibleText("Cambiar Plan");

		final String pageText = normalize(driver.findElement(By.tagName("body")).getText());
		assertTrue("Expected an email in Información General.", EMAIL_PATTERN.matcher(pageText).find());
		assertTrue("Expected user name visibility in Información General.", hasLikelyUserName(pageText));
	}

	private void stepValidateDetallesCuenta() {
		assertAnyVisibleText("Cuenta creada");
		assertAnyVisibleText("Estado activo");
		assertAnyVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();
		final String beforeUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		String activeWindow = appWindow;
		try {
			wait.until(d -> d.getWindowHandles().size() > beforeHandles.size() || !d.getCurrentUrl().equals(beforeUrl));
		} catch (final TimeoutException timeoutException) {
			// Keep going with current tab checks to provide a deterministic failure message.
		}

		final Set<String> afterHandles = driver.getWindowHandles();
		final Optional<String> newHandle = afterHandles.stream().filter(handle -> !beforeHandles.contains(handle)).findFirst();
		if (newHandle.isPresent()) {
			activeWindow = newHandle.get();
			driver.switchTo().window(activeWindow);
			waitForUiToLoad();
		}

		assertAnyVisibleText(expectedHeading);
		assertTrue("Expected legal content text to be visible for " + linkText + ".",
				normalize(driver.findElement(By.tagName("body")).getText()).length() > 250);

		takeScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (!activeWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private boolean executeStep(final String reportField, final CheckedRunnable step) throws IOException {
		try {
			step.run();
			report.put(reportField, Boolean.TRUE);
			return true;
		} catch (final Exception exception) {
			report.put(reportField, Boolean.FALSE);
			takeScreenshot("failed-" + sanitizeFileName(reportField));
			return false;
		}
	}

	private void initializeReport() {
		report.put("Login", Boolean.FALSE);
		report.put("Mi Negocio menu", Boolean.FALSE);
		report.put("Agregar Negocio modal", Boolean.FALSE);
		report.put("Administrar Negocios view", Boolean.FALSE);
		report.put("Información General", Boolean.FALSE);
		report.put("Detalles de la Cuenta", Boolean.FALSE);
		report.put("Tus Negocios", Boolean.FALSE);
		report.put("Términos y Condiciones", Boolean.FALSE);
		report.put("Política de Privacidad", Boolean.FALSE);
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio workflow result").append(System.lineSeparator());
		reportBuilder.append("==================================").append(System.lineSeparator());

		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			reportBuilder.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL")
					.append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			reportBuilder.append(System.lineSeparator()).append("Final URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> legalEntry : legalUrls.entrySet()) {
				reportBuilder.append(legalEntry.getKey()).append(": ").append(legalEntry.getValue())
						.append(System.lineSeparator());
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), reportBuilder.toString());
	}

	private void waitForUiToLoad() {
		wait.until(driverRef -> ((JavascriptExecutor) driverRef).executeScript("return document.readyState").equals("complete"));
		waitFor(500);
	}

	private void clickByVisibleText(final String... texts) {
		final List<String> errors = new ArrayList<>();

		for (final String text : texts) {
			final String normalizedText = normalize(text);
			final By locator = By.xpath("//*[contains(translate(normalize-space(.),'" + UPPER_CASE + "','" + LOWER_CASE
					+ "')," + toXpathString(normalizedText.toLowerCase(Locale.ROOT)) + ")]");
			final List<WebElement> candidates = driver.findElements(locator);

			for (final WebElement candidate : candidates) {
				try {
					if (!candidate.isDisplayed()) {
						continue;
					}

					final WebElement clickable = toClickableElement(candidate);
					wait.until(ExpectedConditions.elementToBeClickable(clickable)).click();
					waitForUiToLoad();
					return;
				} catch (final Exception clickError) {
					errors.add(clickError.getMessage());
				}
			}
		}

		throw new AssertionError("Could not click any element with visible text " + String.join(", ", texts)
				+ ". Attempts: " + errors.size());
	}

	private void clickIfVisibleText(final String text) {
		if (isAnyVisibleTextPresent(text)) {
			clickByVisibleText(text);
		}
	}

	private boolean isAnyVisibleTextPresent(final String text) {
		final String normalizedText = normalize(text).toLowerCase(Locale.ROOT);
		final String xpath = "//*[contains(translate(normalize-space(.),'" + UPPER_CASE + "','" + LOWER_CASE + "'),"
				+ toXpathString(normalizedText) + ")]";
		return driver.findElements(By.xpath(xpath)).stream().anyMatch(WebElement::isDisplayed);
	}

	private void assertAnyVisibleText(final String... texts) {
		for (final String text : texts) {
			if (isAnyVisibleTextPresent(text)) {
				return;
			}
		}
		throw new AssertionError("None of the expected visible texts were found: " + String.join(", ", texts));
	}

	private void assertSidebarVisible() {
		final List<By> sidebarLocators = List.of(By.tagName("aside"),
				By.xpath("//nav[contains(@class,'sidebar') or contains(@id,'sidebar') or .//a]"));
		for (final By locator : sidebarLocators) {
			final List<WebElement> found = driver.findElements(locator);
			if (found.stream().anyMatch(WebElement::isDisplayed)) {
				return;
			}
		}
		throw new AssertionError("Left sidebar navigation is not visible.");
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		try {
			final String locatorText = normalize(accountEmail).toLowerCase(Locale.ROOT);
			final By accountLocator = By.xpath(
					"//*[contains(translate(normalize-space(.),'" + UPPER_CASE + "','" + LOWER_CASE + "'),"
							+ toXpathString(locatorText) + ")]");
			final WebElement accountElement = wait.withTimeout(Duration.ofSeconds(8))
					.until(ExpectedConditions.visibilityOfElementLocated(accountLocator));
			accountElement.click();
		} catch (final TimeoutException timeoutException) {
			// Account picker is optional when session is already authenticated.
		} finally {
			wait.withTimeout(Duration.ofSeconds(Long.parseLong(getEnv("SALEADS_TIMEOUT_SECONDS", "30"))));
		}
	}

	private By findInputForLabelOrPlaceholder(final String text) {
		final String normalized = normalize(text).toLowerCase(Locale.ROOT);
		final String inputByAria = "//input[contains(translate(@aria-label,'" + UPPER_CASE + "','" + LOWER_CASE + "'),"
				+ toXpathString(normalized) + ") or contains(translate(@placeholder,'" + UPPER_CASE + "','" + LOWER_CASE
				+ "')," + toXpathString(normalized) + ")]";
		final String inputByLabelAssociation = "//label[contains(translate(normalize-space(.),'" + UPPER_CASE + "','"
				+ LOWER_CASE + "')," + toXpathString(normalized)
				+ ")]/following::input[1]";
		return By.xpath(inputByAria + " | " + inputByLabelAssociation);
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private WebElement toClickableElement(final WebElement element) {
		WebElement current = element;
		for (int attempt = 0; attempt < 5; attempt++) {
			final String tagName = current.getTagName().toLowerCase(Locale.ROOT);
			final String role = current.getAttribute("role");
			if ("a".equals(tagName) || "button".equals(tagName) || "input".equals(tagName) || "button".equals(role)) {
				return current;
			}
			try {
				current = current.findElement(By.xpath(".."));
			} catch (final Exception ignored) {
				return element;
			}
		}
		return element;
	}

	private void takeScreenshot(final String name) throws IOException {
		takeScreenshot(name, false);
	}

	private void takeScreenshot(final String name, final boolean preferFullPage) throws IOException {
		final byte[] screenshot;
		if (preferFullPage && driver instanceof HasFullPageScreenshot) {
			screenshot = ((HasFullPageScreenshot) driver).getFullPageScreenshotAs(OutputType.BYTES);
		} else {
			screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		}
		Files.copy(new java.io.ByteArrayInputStream(screenshot), evidenceDir.resolve(name + ".png"),
				StandardCopyOption.REPLACE_EXISTING);
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		return Files.createDirectories(Path.of("target", "saleads-evidence", timestamp));
	}

	private String sanitizeFileName(final String text) {
		return normalize(text).replaceAll("[^a-zA-Z0-9._-]", "-").toLowerCase(Locale.ROOT);
	}

	private String normalize(final String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	private String toXpathString(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder result = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				result.append(", \"'\", ");
			}
			result.append("'").append(parts[i]).append("'");
		}
		result.append(")");
		return result.toString();
	}

	private boolean hasLikelyUserName(final String text) {
		final String[] lines = text.split("\\R");
		for (final String line : lines) {
			final String normalized = normalize(line).trim();
			if (normalized.length() < 5 || normalized.length() > 80) {
				continue;
			}
			final String lower = normalized.toLowerCase(Locale.ROOT);
			if (lower.contains("@") || lower.contains("business plan") || lower.contains("cambiar plan")
					|| lower.contains("informacion general")) {
				continue;
			}
			if (normalized.matches("[A-Za-z]+\\s+[A-Za-z].*")) {
				return true;
			}
		}
		return false;
	}

	private void waitFor(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String getEnv(final String key, final String fallback) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? fallback : value;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
