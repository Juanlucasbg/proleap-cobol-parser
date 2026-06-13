package io.proleap.cobol.e2e.saleads;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
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

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final DateTimeFormatter DIR_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
	private static final String LEGAL_TERMS = "T\u00E9rminos y Condiciones";
	private static final String LEGAL_PRIVACY = "Pol\u00EDtica de Privacidad";

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private int screenshotCounter = 0;

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String loginUrl = firstPresent("SALEADS_LOGIN_URL", "saleads.login.url");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL (or -Dsaleads.login.url) to the environment login page before running this test.",
				loginUrl != null && !loginUrl.isBlank());

		setup();
		try {
			driver.get(loginUrl);
			waitForUi();

			runStep("Login", this::stepLogin);
			runStep("Mi Negocio menu", this::stepMiNegocioMenu);
			runStep("Agregar Negocio modal", this::stepAgregarNegocioModal);
			runStep("Administrar Negocios view", this::stepAdministrarNegociosView);
			runStep("Informaci\u00F3n General", this::stepInformacionGeneral);
			runStep("Detalles de la Cuenta", this::stepDetallesCuenta);
			runStep("Tus Negocios", this::stepTusNegocios);
			runStep("T\u00E9rminos y Condiciones", this::stepTerminosYCondiciones);
			runStep("Pol\u00EDtica de Privacidad", this::stepPoliticaPrivacidad);
		} finally {
			writeReports();
			tearDown();
		}

		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(Map.Entry::getKey).toList();
		Assert.assertTrue("SaleADS workflow failures: " + failedSteps, failedSteps.isEmpty());
	}

	private void setup() throws IOException {
		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1440,1024");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (isHeadless()) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String dirName = "saleads-mi-negocio-" + LocalDateTime.now().format(DIR_TIMESTAMP);
		evidenceDir = Path.of("target", "saleads-evidence", dirName).toAbsolutePath();
		Files.createDirectories(evidenceDir);
	}

	private void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	private void stepLogin() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesi\u00F3n con Google", "Continuar con Google");
		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");
		assertMainInterfaceVisible();
		assertSidebarVisible();
		takeScreenshot("dashboard-loaded");
	}

	private void stepMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("mi-negocio-expanded");
	}

	private void stepAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeScreenshot("crear-nuevo-negocio-modal");

		typeInNombreNegocioField("Negocio Prueba Automatizacion");
		clickByVisibleText("Cancelar");
	}

	private void stepAdministrarNegociosView() throws Exception {
		expandMiNegocioIfCollapsed();
		clickByVisibleText("Administrar Negocios");
		assertTextVisible("Informaci\u00F3n General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Secci\u00F3n Legal");
		takeFullPageScreenshot("administrar-negocios-cuenta");
	}

	private void stepInformacionGeneral() {
		assertAnyTextVisible("BUSINESS PLAN", "Business Plan");
		assertTextVisible("Cambiar Plan");
		assertAccountIdentityVisible();
	}

	private void stepDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertAnyTextVisible("Estado activo", "Estado Activo");
		assertAnyTextVisible("Idioma seleccionado", "Idioma Seleccionado");
	}

	private void stepTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepTerminosYCondiciones() throws Exception {
		final String finalUrl = openLegalLink(LEGAL_TERMS, LEGAL_TERMS, "terminos-y-condiciones");
		legalUrls.put(LEGAL_TERMS, finalUrl);
	}

	private void stepPoliticaPrivacidad() throws Exception {
		final String finalUrl = openLegalLink(LEGAL_PRIVACY, LEGAL_PRIVACY, "politica-de-privacidad");
		legalUrls.put(LEGAL_PRIVACY, finalUrl);
	}

	private String openLegalLink(final String linkText, final String headingText, final String screenshotName) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> previousWindows = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);
		final boolean openedNewTab = waitForNewTab(previousWindows, Duration.ofSeconds(10));
		if (openedNewTab) {
			switchToNewWindow(previousWindows);
		}

		waitForUi();
		assertTextVisible(headingText);
		assertLegalTextVisible();
		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUi();
		} else {
			driver.navigate().back();
			waitForUi();
		}

		return finalUrl;
	}

	private boolean waitForNewTab(final Set<String> previousWindows, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(d -> d.getWindowHandles().size() > previousWindows.size());
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void switchToNewWindow(final Set<String> previousWindows) {
		final Set<String> currentWindows = new LinkedHashSet<>(driver.getWindowHandles());
		currentWindows.removeAll(previousWindows);
		if (currentWindows.isEmpty()) {
			throw new AssertionError("Expected a new tab, but no new window handle was found.");
		}
		driver.switchTo().window(currentWindows.iterator().next());
	}

	private void runStep(final String stepName, final StepAction action) {
		final StepResult stepResult = new StepResult();
		try {
			action.run();
			stepResult.passed = true;
			stepResult.message = "PASS";
		} catch (final Throwable throwable) {
			stepResult.passed = false;
			stepResult.message = safeMessage(throwable);
		}

		report.put(stepName, stepResult);
	}

	private void writeReports() throws IOException {
		final StringBuilder markdown = new StringBuilder();
		markdown.append("# SaleADS Mi Negocio Workflow Report\n\n");
		markdown.append("| Step | Status | Details |\n");
		markdown.append("| --- | --- | --- |\n");

		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			markdown.append("| ").append(entry.getKey()).append(" | ")
					.append(entry.getValue().passed ? "PASS" : "FAIL").append(" | ")
					.append(escapePipe(entry.getValue().message)).append(" |\n");
		}

		if (!legalUrls.isEmpty()) {
			markdown.append("\n## Final Legal URLs\n\n");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				markdown.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
			}
		}

		final Path markdownReport = evidenceDir.resolve("report.md");
		Files.writeString(markdownReport, markdown.toString());

		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"results\": [\n");

		final List<Map.Entry<String, StepResult>> entries = new ArrayList<>(report.entrySet());
		for (int i = 0; i < entries.size(); i++) {
			final Map.Entry<String, StepResult> entry = entries.get(i);
			json.append("    {\n");
			json.append("      \"step\": \"").append(escapeJson(entry.getKey())).append("\",\n");
			json.append("      \"status\": \"").append(entry.getValue().passed ? "PASS" : "FAIL").append("\",\n");
			json.append("      \"details\": \"").append(escapeJson(entry.getValue().message)).append("\"\n");
			json.append("    }");
			if (i < entries.size() - 1) {
				json.append(",");
			}
			json.append("\n");
		}

		json.append("  ],\n");
		json.append("  \"legalUrls\": {\n");

		final List<Map.Entry<String, String>> legalEntries = new ArrayList<>(legalUrls.entrySet());
		for (int i = 0; i < legalEntries.size(); i++) {
			final Map.Entry<String, String> entry = legalEntries.get(i);
			json.append("    \"").append(escapeJson(entry.getKey())).append("\": ")
					.append("\"").append(escapeJson(entry.getValue())).append("\"");
			if (i < legalEntries.size() - 1) {
				json.append(",");
			}
			json.append("\n");
		}

		json.append("  }\n");
		json.append("}\n");

		final Path jsonReport = evidenceDir.resolve("report.json");
		Files.writeString(jsonReport, json.toString());
	}

	private void waitForUi() {
		wait.until((ExpectedCondition<Boolean>) d -> {
			final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return readyState != null && "complete".equals(readyState.toString());
		});

		try {
			new WebDriverWait(driver, Duration.ofSeconds(6)).until(d -> {
				final List<WebElement> loaders = d.findElements(
						By.cssSelector("[aria-busy='true'], .loading, .loader, .spinner, .skeleton"));
				for (final WebElement loader : loaders) {
					if (loader.isDisplayed()) {
						return false;
					}
				}
				return true;
			});
		} catch (final TimeoutException ignored) {
			// Non-blocking: some environments keep a hidden spinner container mounted.
		}
	}

	private void clickByVisibleText(final String... textOptions) {
		NoSuchElementException notFound = null;
		for (final String textOption : textOptions) {
			try {
				final WebElement element = waitForVisible(By.xpath(
						"//*[self::button or self::a or @role='button' or self::span or self::div][contains(normalize-space(.), "
								+ toXpathLiteral(textOption) + ")]"),
						Duration.ofSeconds(12));
				clickElement(element);
				return;
			} catch (final NoSuchElementException exception) {
				notFound = exception;
			}
		}

		throw notFound != null ? notFound
				: new NoSuchElementException("Could not find a clickable element by visible text.");
	}

	private void clickElement(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception ignored) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUi();
	}

	private WebElement waitForVisible(final By by, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(driverRef -> {
				final List<WebElement> elements = driverRef.findElements(by);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
				return null;
			});
		} catch (final TimeoutException exception) {
			throw new NoSuchElementException("No visible element found for locator: " + by, exception);
		}
	}

	private void assertTextVisible(final String text) {
		waitForVisible(By.xpath("//*[contains(normalize-space(.), " + toXpathLiteral(text) + ")]"), Duration.ofSeconds(20));
	}

	private void assertAnyTextVisible(final String... texts) {
		NoSuchElementException exception = null;
		for (final String text : texts) {
			try {
				assertTextVisible(text);
				return;
			} catch (final NoSuchElementException currentException) {
				exception = currentException;
			}
		}

		throw exception != null ? exception : new NoSuchElementException("Could not find any expected text.");
	}

	private void assertMainInterfaceVisible() {
		assertAnyTextVisible("Dashboard", "Inicio", "Negocio", "Mi Negocio");
	}

	private void assertSidebarVisible() {
		final List<WebElement> sidebarCandidates = driver.findElements(By.cssSelector("aside, nav"));
		for (final WebElement sidebar : sidebarCandidates) {
			if (sidebar.isDisplayed()) {
				return;
			}
		}

		throw new AssertionError("Left sidebar navigation is not visible.");
	}

	private void assertAccountIdentityVisible() {
		final List<WebElement> identityCandidates = driver.findElements(By.xpath(
				"//*[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '@')]"));
		for (final WebElement candidate : identityCandidates) {
			if (candidate.isDisplayed()) {
				return;
			}
		}

		throw new AssertionError("Expected to find user identity details (name/email) in Informacion General.");
	}

	private void assertLegalTextVisible() {
		final WebElement body = waitForVisible(By.tagName("body"), Duration.ofSeconds(20));
		final String bodyText = body.getText().replace('\n', ' ').replaceAll("\\s+", " ").trim();
		if (bodyText.length() < 120) {
			throw new AssertionError("Legal content seems too short. Visible content length: " + bodyText.length());
		}
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		try {
			final WebElement account = waitForVisible(
					By.xpath("//*[contains(normalize-space(.), " + toXpathLiteral(accountEmail) + ")]"),
					Duration.ofSeconds(8));
			clickElement(account);
		} catch (final NoSuchElementException ignored) {
			// The account selector is not always shown when a session is already authenticated.
		}
	}

	private void expandMiNegocioIfCollapsed() {
		final boolean submenuVisible = isTextVisible("Administrar Negocios", Duration.ofSeconds(4));
		if (!submenuVisible) {
			clickByVisibleText("Mi Negocio");
		}
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			waitForVisible(By.xpath("//*[contains(normalize-space(.), " + toXpathLiteral(text) + ")]"), timeout);
			return true;
		} catch (final NoSuchElementException ignored) {
			return false;
		}
	}

	private void typeInNombreNegocioField(final String value) {
		By inputLocator = By.xpath("//input[contains(@placeholder, " + toXpathLiteral("Nombre del Negocio") + ")]");
		if (!hasVisibleElement(inputLocator, Duration.ofSeconds(4))) {
			inputLocator = By.xpath(
					"//*[contains(normalize-space(.), " + toXpathLiteral("Nombre del Negocio") + ")]/following::input[1]");
		}

		final WebElement input = waitForVisible(inputLocator, Duration.ofSeconds(10));
		input.clear();
		input.sendKeys(value);
	}

	private boolean hasVisibleElement(final By by, final Duration timeout) {
		try {
			waitForVisible(by, timeout);
			return true;
		} catch (final NoSuchElementException ignored) {
			return false;
		}
	}

	private void takeScreenshot(final String name) throws IOException {
		screenshotCounter++;
		final String fileName = String.format(Locale.ROOT, "%02d-%s.png", screenshotCounter, slugify(name));
		final Path filePath = evidenceDir.resolve(fileName);
		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshotFile.toPath(), filePath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void takeFullPageScreenshot(final String name) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		final Number rawHeight = (Number) ((JavascriptExecutor) driver)
				.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
		final int fullHeight = Math.min(Math.max(rawHeight.intValue() + 200, originalSize.getHeight()), 5000);
		driver.manage().window().setSize(new Dimension(Math.max(originalSize.getWidth(), 1440), fullHeight));
		waitForUi();
		takeScreenshot(name + "-full");
		driver.manage().window().setSize(originalSize);
		waitForUi();
	}

	private boolean isHeadless() {
		final String value = firstPresent("SALEADS_HEADLESS", "saleads.headless");
		if (value == null || value.isBlank()) {
			return true;
		}
		return Boolean.parseBoolean(value.trim());
	}

	private String firstPresent(final String envName, final String propertyName) {
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		return null;
	}

	private String slugify(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				result.append(", ");
			}
			if (chars[i] == '\'') {
				result.append("\"'\"");
			} else if (chars[i] == '"') {
				result.append("'\"'");
			} else {
				result.append("'").append(chars[i]).append("'");
			}
		}
		result.append(")");
		return result.toString();
	}

	private String escapePipe(final String value) {
		return value == null ? "" : value.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
	}

	private String safeMessage(final Throwable throwable) {
		final String message = throwable.getMessage();
		if (message == null || message.isBlank()) {
			return throwable.getClass().getSimpleName();
		}
		return message.replace('\n', ' ').replace('\r', ' ');
	}

	private String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private boolean passed;
		private String message;
	}
}
