package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(20);
	private static final Duration QUICK_WAIT = Duration.ofSeconds(5);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;

	@Before
	public void setUp() throws IOException {
		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-notifications");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		if (Boolean.parseBoolean(getConfig("SALEADS_HEADLESS", "saleads.headless", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		screenshotDirectory = Paths.get("target", "screenshots", "saleads-mi-negocio-full-test");
		Files.createDirectories(screenshotDirectory);

		final String loginUrl = getConfig("SALEADS_LOGIN_URL", "saleads.login.url", "").trim();
		if (loginUrl.isEmpty()) {
			throw new IllegalStateException(
					"SALEADS_LOGIN_URL (or -Dsaleads.login.url) is required to open the login page in the active environment.");
		}

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		try {
			printFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		executeStep("Login", this::performGoogleLoginAndValidateDashboard);
		executeStep("Mi Negocio menu", this::openMiNegocioMenuAndValidate);
		executeStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::openAdministrarNegociosAndValidateSections);
		executeStep("Información General", this::validateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		executeStep("Tus Negocios", this::validateTusNegocios);
		executeStep("Términos y Condiciones", () -> validateLegalDocument("Términos y Condiciones",
				new String[] { "Términos y Condiciones", "Terminos y Condiciones" }, "08-terminos-y-condiciones"));
		executeStep("Política de Privacidad", () -> validateLegalDocument("Política de Privacidad",
				new String[] { "Política de Privacidad", "Politica de Privacidad" }, "09-politica-de-privacidad"));

		if (!failures.isEmpty()) {
			Assert.fail("Workflow validation failed:\n - " + String.join("\n - ", failures));
		}
	}

	private void performGoogleLoginAndValidateDashboard() {
		final Set<String> windowsBeforeLoginClick = driver.getWindowHandles();
		clickByVisibleTextAny("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Iniciar con Google", "Google");
		waitForUiToLoad();

		switchToNewWindowIfPresent(windowsBeforeLoginClick);
		handleGoogleAccountSelectorIfPresent();
		returnToAppWindow();

		waitForAnyVisibleText("Negocio", "Mi Negocio");
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside | //nav")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//main | //div[@id='root']")));
		captureScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenuAndValidate() {
		clickByVisibleTextIfPresentAny("Negocio");
		clickByVisibleTextAny("Mi Negocio");
		waitForUiToLoad();

		assertVisibleTextAny("Agregar Negocio");
		assertVisibleTextAny("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() {
		clickByVisibleTextAny("Agregar Negocio");
		waitForUiToLoad();

		assertVisibleTextAny("Crear Nuevo Negocio");
		assertElementExists(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='businessName' or @id='businessName'] | //label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				"Input 'Nombre del Negocio'");
		assertVisibleTextContainsAny("Tienes 2 de 3 negocios");
		assertVisibleTextAny("Cancelar");
		assertVisibleTextAny("Crear Negocio");

		final WebElement businessNameInput = findFirstVisibleElement(
				By.xpath(
						"//input[@placeholder='Nombre del Negocio' or @name='businessName' or @id='businessName'] | //label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				DEFAULT_WAIT);
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		waitForUiToLoad();

		captureScreenshot("03-agregar-negocio-modal");
		clickByVisibleTextAny("Cancelar");
		waitForUiToLoad();
	}

	private void openAdministrarNegociosAndValidateSections() {
		if (!isAnyTextVisible(QUICK_WAIT, "Administrar Negocios")) {
			clickByVisibleTextAny("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleTextAny("Administrar Negocios");
		waitForUiToLoad();

		assertVisibleTextAny("Información General", "Informacion General");
		assertVisibleTextAny("Detalles de la Cuenta", "Detalles de la Cuenta");
		assertVisibleTextAny("Tus Negocios");
		assertVisibleTextAny("Sección Legal", "Seccion Legal");
		captureFullPageScreenshot("04-administrar-negocios-account-page");
	}

	private void validateInformacionGeneral() {
		final WebElement section = getSectionByHeading("Información General", "Informacion General");
		final String sectionText = section.getText();

		if (!EMAIL_PATTERN.matcher(sectionText).find()) {
			throw new AssertionError("Email not visible inside 'Información General'.");
		}

		if (!containsLikelyUserName(sectionText)) {
			throw new AssertionError("User name not detected inside 'Información General'.");
		}

		assertSectionContainsAny(section, "BUSINESS PLAN");
		assertSectionContainsAny(section, "Cambiar Plan");
	}

	private void validateDetallesDeLaCuenta() {
		final WebElement section = getSectionByHeading("Detalles de la Cuenta");
		assertSectionContainsAny(section, "Cuenta creada");
		assertSectionContainsAny(section, "Estado activo");
		assertSectionContainsAny(section, "Idioma seleccionado");
	}

	private void validateTusNegocios() {
		final WebElement section = getSectionByHeading("Tus Negocios");
		assertSectionContainsAny(section, "Agregar Negocio");
		assertSectionContainsAny(section, "Tienes 2 de 3 negocios");

		final String text = section.getText();
		if (text.trim().length() < 40) {
			throw new AssertionError("'Tus Negocios' appears empty or missing business details.");
		}
	}

	private void validateLegalDocument(final String reportName, final String[] headingCandidates,
			final String screenshotName) {
		final String appWindow = driver.getWindowHandle();
		final Set<String> previousWindows = driver.getWindowHandles();

		clickLegalLink(reportName);
		waitForUiToLoad();

		final String newWindow = waitForNewWindow(previousWindows, QUICK_WAIT);
		if (newWindow != null) {
			driver.switchTo().window(newWindow);
			waitForUiToLoad();
		}

		assertVisibleTextAny(headingCandidates);
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//p[string-length(normalize-space()) > 20]")));
		captureScreenshot(screenshotName);
		legalUrls.put(reportName, driver.getCurrentUrl());

		if (newWindow != null) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void clickLegalLink(final String linkText) {
		WebElement legalSection = getSectionByHeading("Sección Legal", "Seccion Legal");

		final String escapedLink = toXPathLiteral(linkText);
		final String linkXpath = ".//*[self::a or self::button][contains(normalize-space(.), " + escapedLink + ")]";

		try {
			final WebElement link = new WebDriverWait(driver, DEFAULT_WAIT)
					.until(ExpectedConditions.elementToBeClickable(legalSection.findElement(By.xpath(linkXpath))));
			link.click();
		} catch (NoSuchElementException ex) {
			clickByVisibleTextAny(linkText);
		}
	}

	private WebElement getSectionByHeading(final String... headingCandidates) {
		final List<By> candidateLocators = new ArrayList<>();

		for (String heading : headingCandidates) {
			final String headingLiteral = toXPathLiteral(heading);
			candidateLocators.add(By.xpath(
					"//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(.), " + headingLiteral
							+ ")]/ancestor::*[self::section or self::article or self::div][1]"));
			candidateLocators.add(By.xpath("//*[self::section or self::article or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(.), "
					+ headingLiteral + ")]][1]"));
		}

		return findFirstVisibleElement(candidateLocators, DEFAULT_WAIT);
	}

	private void executeStep(final String stepName, final Runnable stepAction) {
		try {
			stepAction.run();
			report.put(stepName, Boolean.TRUE);
		} catch (Throwable throwable) {
			report.put(stepName, Boolean.FALSE);
			failures.add(stepName + ": " + throwable.getMessage());
		}
	}

	private void handleGoogleAccountSelectorIfPresent() {
		if (!isAnyTextVisible(QUICK_WAIT, GOOGLE_ACCOUNT_EMAIL)) {
			return;
		}

		clickByVisibleTextAny(GOOGLE_ACCOUNT_EMAIL);
		waitForUiToLoad();
	}

	private void returnToAppWindow() {
		if (isOnGoogleDomain()) {
			final long deadline = System.currentTimeMillis() + DEFAULT_WAIT.toMillis();
			while (isOnGoogleDomain() && System.currentTimeMillis() < deadline) {
				for (String windowHandle : driver.getWindowHandles()) {
					driver.switchTo().window(windowHandle);
					if (!isOnGoogleDomain()) {
						waitForUiToLoad();
						return;
					}
				}
			}
		}
	}

	private void waitForUiToLoad() {
		try {
			new WebDriverWait(driver, DEFAULT_WAIT).until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver)
					.executeScript("return document.readyState")));
		} catch (TimeoutException ignored) {
			// Some SPA states may never report complete after in-app transitions.
		}

		try {
			Thread.sleep(500L);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void clickByVisibleTextAny(final String... textCandidates) {
		final List<By> locators = new ArrayList<>();
		for (String text : textCandidates) {
			final String escapedText = toXPathLiteral(text);
			locators.add(By.xpath("//*[self::button or self::a or self::span or self::div][normalize-space()="
					+ escapedText + "]"));
			locators.add(By.xpath(
					"//*[self::button or self::a or self::span or self::div][contains(normalize-space(), " + escapedText
							+ ")]"));
		}

		final WebElement element = findFirstVisibleElement(locators, DEFAULT_WAIT);
		new WebDriverWait(driver, DEFAULT_WAIT).until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiToLoad();
	}

	private void clickByVisibleTextIfPresentAny(final String... textCandidates) {
		try {
			if (isAnyTextVisible(QUICK_WAIT, textCandidates)) {
				clickByVisibleTextAny(textCandidates);
			}
		} catch (AssertionError ignored) {
			// Optional click.
		}
	}

	private void assertVisibleTextAny(final String... textCandidates) {
		if (!isAnyTextVisible(DEFAULT_WAIT, textCandidates)) {
			throw new AssertionError("Expected visible text not found: " + String.join(" | ", textCandidates));
		}
	}

	private void assertVisibleTextContainsAny(final String... textCandidates) {
		for (String candidate : textCandidates) {
			final String escaped = toXPathLiteral(candidate);
			final By locator = By.xpath("//*[contains(normalize-space(), " + escaped + ")]");
			try {
				new WebDriverWait(driver, DEFAULT_WAIT).until(ExpectedConditions.visibilityOfElementLocated(locator));
				return;
			} catch (TimeoutException ignored) {
				// Try next candidate.
			}
		}
		throw new AssertionError("Expected partial text not found: " + String.join(" | ", textCandidates));
	}

	private boolean isAnyTextVisible(final Duration timeout, final String... textCandidates) {
		for (String text : textCandidates) {
			final String escaped = toXPathLiteral(text);
			final By exact = By.xpath("//*[normalize-space()=" + escaped + "]");
			final By contains = By.xpath("//*[contains(normalize-space(), " + escaped + ")]");
			try {
				new WebDriverWait(driver, timeout).until(ExpectedConditions.or(
						ExpectedConditions.visibilityOfElementLocated(exact),
						ExpectedConditions.visibilityOfElementLocated(contains)));
				return true;
			} catch (TimeoutException ignored) {
				// Try next candidate.
			}
		}
		return false;
	}

	private void waitForAnyVisibleText(final String... textCandidates) {
		assertVisibleTextAny(textCandidates);
	}

	private void assertElementExists(final By locator, final String label) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (TimeoutException timeoutException) {
			throw new AssertionError(label + " was not found.");
		}
	}

	private void assertSectionContainsAny(final WebElement section, final String... texts) {
		final String sectionText = section.getText();
		for (String text : texts) {
			if (sectionText.contains(text)) {
				return;
			}
		}
		throw new AssertionError("Section does not contain expected text(s): " + String.join(" | ", texts));
	}

	private WebElement findFirstVisibleElement(final By locator, final Duration timeout) {
		return findFirstVisibleElement(List.of(locator), timeout);
	}

	private WebElement findFirstVisibleElement(final List<By> locators, final Duration timeout) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		Throwable lastError = null;

		while (System.currentTimeMillis() < deadline) {
			for (By locator : locators) {
				try {
					WebElement element = driver.findElement(locator);
					if (element.isDisplayed()) {
						return element;
					}
				} catch (Throwable throwable) {
					lastError = throwable;
				}
			}

			try {
				Thread.sleep(150L);
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Interrupted while waiting for visible element.");
			}
		}

		throw new AssertionError("Could not find visible element. Last error: "
				+ (lastError == null ? "unknown" : lastError.getMessage()));
	}

	private String waitForNewWindow(final Set<String> previousHandles, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(webDriver -> webDriver.getWindowHandles().size() > previousHandles.size());
			Set<String> currentHandles = new LinkedHashSet<>(driver.getWindowHandles());
			currentHandles.removeAll(previousHandles);
			return currentHandles.isEmpty() ? null : currentHandles.iterator().next();
		} catch (TimeoutException timeoutException) {
			return null;
		}
	}

	private void switchToNewWindowIfPresent(final Set<String> previousHandles) {
		final String newWindow = waitForNewWindow(previousHandles, QUICK_WAIT);
		if (newWindow != null) {
			driver.switchTo().window(newWindow);
			waitForUiToLoad();
		}
	}

	private void captureScreenshot(final String name) {
		try {
			final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			final Path targetPath = screenshotDirectory.resolve(name + "-" + timestamp + ".png");
			final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(targetPath, screenshot);
		} catch (IOException ioException) {
			throw new IllegalStateException("Unable to write screenshot: " + ioException.getMessage(), ioException);
		}
	}

	private void captureFullPageScreenshot(final String name) {
		final Dimension originalSize = driver.manage().window().getSize();
		final JavascriptExecutor javascriptExecutor = (JavascriptExecutor) driver;

		try {
			final Number scrollHeight = (Number) javascriptExecutor
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			final int targetHeight = Math.max(originalSize.getHeight(), Math.min(scrollHeight.intValue(), 8000));
			driver.manage().window().setSize(new Dimension(originalSize.getWidth(), targetHeight));
			waitForUiToLoad();
			captureScreenshot(name);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}
	}

	private String getConfig(final String envName, final String propertyName, final String defaultValue) {
		return firstNonBlank(() -> System.getenv(envName), () -> System.getProperty(propertyName), () -> defaultValue);
	}

	@SafeVarargs
	private final String firstNonBlank(final Supplier<String>... suppliers) {
		for (Supplier<String> supplier : suppliers) {
			final String value = supplier.get();
			if (value != null && !value.isBlank()) {
				return value;
			}
		}

		return "";
	}

	private boolean containsLikelyUserName(final String sectionText) {
		final String normalized = sectionText.replace("\r", "");
		final String[] lines = normalized.split("\n");

		for (String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}

			final String lower = line.toLowerCase();
			if (lower.contains("información general") || lower.contains("informacion general")
					|| lower.contains("business plan") || lower.contains("cambiar plan")
					|| lower.contains("cuenta creada") || lower.contains("estado activo")
					|| lower.contains("idioma seleccionado") || line.contains("@")) {
				continue;
			}

			if (line.length() >= 4 && line.split("\\s+").length >= 2) {
				return true;
			}
		}

		return false;
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		char[] characters = value.toCharArray();
		for (int i = 0; i < characters.length; i++) {
			String character = String.valueOf(characters[i]);
			if ("'".equals(character)) {
				builder.append("\"'\"");
			} else if ("\"".equals(character)) {
				builder.append("'\"'");
			} else {
				builder.append("'").append(character).append("'");
			}
			if (i < characters.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private boolean isOnGoogleDomain() {
		final String currentUrl = driver.getCurrentUrl();
		return currentUrl != null && currentUrl.contains("accounts.google.");
	}

	private void printFinalReport() {
		System.out.println("=== saleads_mi_negocio_full_test report ===");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.printf("%s: %s%n", entry.getKey(), Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL");
		}

		if (!legalUrls.isEmpty()) {
			System.out.println("Legal URLs:");
			for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.printf("- %s: %s%n", entry.getKey(), entry.getValue());
			}
		}
		System.out.println("===========================================");
	}
}
