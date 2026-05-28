package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
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
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(SaleadsMiNegocioFullWorkflowTest.class);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private String appWindowHandle;

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		screenshotDirectory = Path.of("target", "saleads-mi-negocio-evidence", timestamp);
		Files.createDirectories(screenshotDirectory);

		final var options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "saleads.headless", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		final String debuggerAddress = readConfig("SALEADS_CHROME_DEBUGGER_ADDRESS", "saleads.chrome.debugger.address",
				null);
		if (debuggerAddress != null && !debuggerAddress.isBlank()) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress.trim());
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));

		final String loginUrl = readConfig("SALEADS_LOGIN_URL", "saleads.login.url", null);
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.navigate().to(loginUrl.trim());
			waitForUiToSettle();
		} else if (debuggerAddress == null || debuggerAddress.isBlank()) {
			throw new IllegalStateException(
					"Provide SALEADS_LOGIN_URL (or -Dsaleads.login.url) or attach to an already-open browser via "
							+ "SALEADS_CHROME_DEBUGGER_ADDRESS (or -Dsaleads.chrome.debugger.address).");
		}

		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		logFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegociosView);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		executeStep("Política de Privacidad", this::stepValidatePoliticaDePrivacidad);

		final boolean allPassed = finalReport.values().stream().allMatch(Boolean::booleanValue);
		Assert.assertTrue("At least one workflow validation failed. Evidence folder: " + screenshotDirectory, allPassed);
	}

	private void stepLoginWithGoogle() throws Exception {
		final Set<String> windowsBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		clickByVisibleText("Google");

		final String googleWindowHandle = waitForNewWindow(windowsBeforeClick, Duration.ofSeconds(12));
		if (googleWindowHandle != null) {
			driver.switchTo().window(googleWindowHandle);
		}

		selectGoogleAccountIfVisible(readConfig("SALEADS_GOOGLE_ACCOUNT", "saleads.google.account", DEFAULT_GOOGLE_ACCOUNT));

		if (googleWindowHandle != null) {
			waitForUiToSettle();
			if (driver.getWindowHandles().contains(appWindowHandle)) {
				driver.switchTo().window(appWindowHandle);
			}
		}

		waitForUiToSettle();
		assertAnyVisible("main application interface", Arrays.asList(By.xpath("//main"), By.xpath("//aside"), By.xpath("//nav")));
		assertAnyVisible("left sidebar navigation",
				Arrays.asList(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar')]")));

		captureCheckpoint("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureCheckpoint("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		assertTextVisible("Crear Nuevo Negocio");
		assertAnyVisible("Nombre del Negocio input", Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[@aria-label='Nombre del Negocio']")));
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		final WebElement businessNameInput = waitForAnyVisible(Arrays.asList(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[@aria-label='Nombre del Negocio']"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]")),
				Duration.ofSeconds(10), true);
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");

		captureCheckpoint("03-agregar-negocio-modal");
		clickByVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegociosView() throws Exception {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(3))) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertAnyVisible("Sección Legal", Arrays.asList(By.xpath("//*[contains(normalize-space(.), 'Sección Legal')]"),
				By.xpath("//*[contains(normalize-space(.), 'Legal')]")));

		captureCheckpoint("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("Información General");
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");

		final String pageText = bodyText();
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(pageText);
		Assert.assertTrue("Expected a visible user email.", emailMatcher.find());

		final String infoSectionText = sectionTextNearHeading("Información General");
		Assert.assertTrue("Expected user name-like text in 'Información General'.", containsLikelyHumanName(infoSectionText));
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertAnyVisible("Estado activo", Arrays.asList(By.xpath("//*[contains(normalize-space(.), 'Estado activo')]"),
				By.xpath("//*[contains(normalize-space(.), 'Estado') and contains(normalize-space(.), 'activo')]")));
		assertAnyVisible("Idioma seleccionado",
				Arrays.asList(By.xpath("//*[contains(normalize-space(.), 'Idioma seleccionado')]"),
						By.xpath("//*[contains(normalize-space(.), 'Idioma')]")));
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertAnyVisible("business list",
				Arrays.asList(By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]/following::*[self::ul or self::table][1]"),
						By.xpath("//*[contains(@class, 'business') or contains(@class, 'negocio')]")));
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		validateLegalDocument("Términos y Condiciones", "Términos y Condiciones", "08-terminos-y-condiciones");
	}

	private void stepValidatePoliticaDePrivacidad() throws Exception {
		validateLegalDocument("Política de Privacidad", "Política de Privacidad", "09-politica-de-privacidad");
	}

	private void validateLegalDocument(final String linkText, final String headingText, final String screenshotName)
			throws Exception {
		final Set<String> windowsBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		clickByVisibleText(linkText);

		final String newWindowHandle = waitForNewWindow(windowsBeforeClick, Duration.ofSeconds(10));
		final boolean openedInNewTab = newWindowHandle != null;
		if (openedInNewTab) {
			driver.switchTo().window(newWindowHandle);
		}

		assertTextVisible(headingText);
		final String legalPageText = bodyText();
		Assert.assertTrue("Expected legal content text on " + headingText + " page.", legalPageText.trim().length() > 120);

		captureCheckpoint(screenshotName);
		legalUrls.put(headingText, driver.getCurrentUrl());
		LOGGER.info("{} URL: {}", headingText, driver.getCurrentUrl());

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else {
			driver.navigate().back();
			waitForUiToSettle();
		}

		assertAnyVisible("application after legal navigation",
				Arrays.asList(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[contains(normalize-space(.), 'Negocio')]")));
	}

	private void executeStep(final String stepName, final StepAction stepAction) {
		try {
			stepAction.execute();
			finalReport.put(stepName, true);
			LOGGER.info("STEP PASS: {}", stepName);
		} catch (final Throwable error) {
			finalReport.put(stepName, false);
			LOGGER.error("STEP FAIL: {}", stepName, error);
			try {
				captureCheckpoint("failure-" + slugify(stepName));
			} catch (final IOException screenshotError) {
				LOGGER.error("Unable to capture failure screenshot for step {}", stepName, screenshotError);
			}
		}
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		final List<By> accountLocators = Arrays.asList(
				By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(accountEmail) + ")]"),
				By.xpath("//div[@data-email=" + xpathLiteral(accountEmail) + "]"),
				By.xpath("//li[contains(normalize-space(.), " + xpathLiteral(accountEmail) + ")]"));

		final WebElement accountElement = waitForAnyVisible(accountLocators, Duration.ofSeconds(12), false);
		if (accountElement != null) {
			click(accountElement);
		}
	}

	private void clickByVisibleText(final String text) {
		final String literal = xpathLiteral(text);
		final List<By> clickCandidates = new ArrayList<>();
		clickCandidates.add(By.xpath("//button[contains(normalize-space(.), " + literal + ")]"));
		clickCandidates.add(By.xpath("//a[contains(normalize-space(.), " + literal + ")]"));
		clickCandidates.add(By.xpath("//*[@role='button' and contains(normalize-space(.), " + literal + ")]"));
		clickCandidates.add(By.xpath("//*[self::span or self::div or self::li][contains(normalize-space(.), " + literal + ")]"));
		clickCandidates.add(By.xpath("//*[contains(normalize-space(.), " + literal + ")]"));

		final WebElement clickable = waitForAnyVisible(clickCandidates, Duration.ofSeconds(20), true);
		click(clickable);
		waitForUiToSettle();
	}

	private void click(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception ignored) {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void assertTextVisible(final String text) {
		final String literal = xpathLiteral(text);
		final By locator = By.xpath("//*[contains(normalize-space(.), " + literal + ")]");
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final TimeoutException timeoutException) {
			Assert.fail("Expected visible text: " + text);
		}
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		final String literal = xpathLiteral(text);
		final By locator = By.xpath("//*[contains(normalize-space(.), " + literal + ")]");
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void assertAnyVisible(final String description, final List<By> locators) {
		final WebElement element = waitForAnyVisible(locators, Duration.ofSeconds(20), false);
		Assert.assertNotNull("Expected visible element for " + description, element);
	}

	private WebElement waitForAnyVisible(final List<By> locators, final Duration timeout, final boolean failIfMissing) {
		try {
			return new WebDriverWait(driver, timeout).until(d -> {
				for (final By locator : locators) {
					for (final WebElement element : d.findElements(locator)) {
						if (element.isDisplayed()) {
							return element;
						}
					}
				}
				return null;
			});
		} catch (final TimeoutException timeoutException) {
			if (failIfMissing) {
				Assert.fail("Could not find visible element for locators: " + locators);
			}
			return null;
		}
	}

	private void waitForUiToSettle() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Some intermediate auth pages can block this check; continue.
		}

		try {
			Thread.sleep(600);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String waitForNewWindow(final Set<String> oldHandles, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(d -> d.getWindowHandles().size() > oldHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!oldHandles.contains(handle)) {
					return handle;
				}
			}
		} catch (final TimeoutException ignored) {
			// Legal pages may open in the same tab. Login may also continue in current tab.
		}

		return null;
	}

	private Path captureCheckpoint(final String checkpointName) throws IOException {
		final String fileName = slugify(checkpointName) + ".png";
		final Path destination = screenshotDirectory.resolve(fileName);
		final File screenshotSource = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshotSource.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		LOGGER.info("Checkpoint captured: {}", destination);
		return destination;
	}

	private String sectionTextNearHeading(final String headingText) {
		final String literal = xpathLiteral(headingText);
		final WebElement heading = waitForAnyVisible(
				Arrays.asList(By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(.), " + literal + ")]"),
						By.xpath("//*[contains(normalize-space(.), " + literal + ")]")),
				Duration.ofSeconds(12), true);
		final WebElement section = heading.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		return section.getText();
	}

	private boolean containsLikelyHumanName(final String text) {
		final List<String> nonNameTokens = Arrays.asList("INFORMACIÓN GENERAL", "BUSINESS PLAN", "CAMBIAR PLAN");
		for (final String rawLine : text.split("\\R")) {
			final String line = rawLine.trim();
			if (line.isBlank() || line.length() < 4 || line.contains("@") || line.matches(".*\\d.*")) {
				continue;
			}

			final String normalized = line.toUpperCase();
			if (nonNameTokens.stream().anyMatch(normalized::contains)) {
				continue;
			}

			if (line.contains(" ")) {
				return true;
			}
		}

		return false;
	}

	private String bodyText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private String readConfig(final String envKey, final String systemPropertyKey, final String defaultValue) {
		final String fromEnv = System.getenv(envKey);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		final String fromSystemProperty = System.getProperty(systemPropertyKey);
		if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
			return fromSystemProperty;
		}

		return defaultValue;
	}

	private String slugify(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder concat = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				concat.append(", \"'\", ");
			}
			concat.append("'").append(parts[i]).append("'");
		}
		concat.append(")");
		return concat.toString();
	}

	private void logFinalReport() {
		if (finalReport.isEmpty()) {
			return;
		}

		LOGGER.info("=== SaleADS Mi Negocio Final Report ===");
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			LOGGER.info("{}: {}", entry.getKey(), entry.getValue() ? "PASS" : "FAIL");
		}
		for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
			LOGGER.info("{} final URL: {}", legalUrl.getKey(), legalUrl.getValue());
		}
		LOGGER.info("Evidence directory: {}", screenshotDirectory);
	}

	@FunctionalInterface
	private interface StepAction {
		void execute() throws Exception;
	}
}
