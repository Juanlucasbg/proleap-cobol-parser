package io.proleap.saleads;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MENU = "Mi Negocio menu";
	private static final String REPORT_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN = "Administrar Negocios view";
	private static final String REPORT_INFO = "Información General";
	private static final String REPORT_ACCOUNT = "Detalles de la Cuenta";
	private static final String REPORT_BUSINESS = "Tus Negocios";
	private static final String REPORT_TERMS = "Términos y Condiciones";
	private static final String REPORT_PRIVACY = "Política de Privacidad";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, Throwable> failures = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;

	@Before
	public void setUp() throws IOException {
		final ChromeOptions chromeOptions = new ChromeOptions();
		if (Boolean.parseBoolean(readConfig("saleads.headless", "true"))) {
			chromeOptions.addArguments("--headless=new");
		}
		chromeOptions.addArguments("--window-size=1920,1080");
		chromeOptions.addArguments("--no-sandbox");
		chromeOptions.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(chromeOptions);

		final long timeoutSeconds = Long.parseLong(readConfig("saleads.timeout.seconds", "30"));
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		evidenceDirectory = Paths.get("target", "saleads-evidence",
				new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
		Files.createDirectories(evidenceDirectory);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String loginUrl = readConfig("saleads.login.url", System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"This test requires saleads.login.url system property or SALEADS_LOGIN_URL environment variable.",
				loginUrl != null && !loginUrl.isBlank());

		runStep(REPORT_LOGIN, () -> loginWithGoogle(loginUrl));
		runStep(REPORT_MENU, this::openMiNegocioMenu);
		runStep(REPORT_MODAL, this::validateAgregarNegocioModal);
		runStep(REPORT_ADMIN, this::openAdministrarNegociosPage);
		runStep(REPORT_INFO, this::validateInformacionGeneralSection);
		runStep(REPORT_ACCOUNT, this::validateDetallesCuentaSection);
		runStep(REPORT_BUSINESS, this::validateTusNegociosSection);
		runStep(REPORT_TERMS, () -> validateLegalLink("Términos y Condiciones", "Términos y Condiciones",
				"08_terminos_condiciones"));
		runStep(REPORT_PRIVACY,
				() -> validateLegalLink("Política de Privacidad", "Política de Privacidad", "09_politica_privacidad"));

		printFinalReport();
		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.toList();
		Assert.assertTrue("One or more validations failed: " + failedSteps, failedSteps.isEmpty());
	}

	private void loginWithGoogle(final String loginUrl) throws IOException {
		driver.get(loginUrl);
		waitForUiToLoad();

		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Login with Google");
		waitForUiToLoad();

		tryClickByExactTextIfVisible(GOOGLE_ACCOUNT_EMAIL);
		waitForUiToLoad();

		waitForSidebar();
		waitForVisibleText("Negocio");
		saveScreenshot("01_dashboard_loaded");
	}

	private void openMiNegocioMenu() throws IOException {
		waitForSidebar();
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		saveScreenshot("02_mi_negocio_menu_expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForVisibleText("Crear Nuevo Negocio");
		waitForVisibleText("Nombre del Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");
		saveScreenshot("03_agregar_negocio_modal");

		final WebElement nombreInput = findVisibleElement(
				By.xpath("//input[@placeholder=" + asXpathLiteral("Nombre del Negocio") + " or @aria-label="
						+ asXpathLiteral("Nombre del Negocio") + "]"
						+ " | //label[contains(normalize-space(.), " + asXpathLiteral("Nombre del Negocio")
						+ ")]/following::input[1]"));
		if (nombreInput != null) {
			nombreInput.click();
			nombreInput.clear();
			nombreInput.sendKeys("Negocio Prueba Automatización");
			waitForUiToLoad();
		}

		clickByVisibleText("Cancelar");
		waitForInvisibility("Crear Nuevo Negocio");
	}

	private void openAdministrarNegociosPage() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForVisibleText("Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal");
		saveScreenshot("04_administrar_negocios_page");
	}

	private void validateInformacionGeneralSection() {
		waitForVisibleText("Información General");

		final String pageText = visibleBodyText();
		Assert.assertTrue("Expected user email to be visible in Información General.",
				findVisibleEmailFromPage().isPresent());
		Assert.assertTrue("Expected user name text to be visible in Información General.",
				hasPotentialUserName(pageText));
		Assert.assertTrue("Expected BUSINESS PLAN text to be visible.", pageText.contains("BUSINESS PLAN"));
		waitForVisibleText("Cambiar Plan");
	}

	private void validateDetallesCuentaSection() {
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void validateTusNegociosSection() {
		final WebElement sectionHeader = waitForVisibleText("Tus Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");

		final String sectionText = sectionHeader.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"))
				.getText();
		Assert.assertTrue("Expected business list content to be visible.", sectionText.split("\\R").length >= 3);
	}

	private void validateLegalLink(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		final String newHandle = waitForNewWindow(handlesBeforeClick);
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiToLoad();
		}

		waitForVisibleText(headingText);
		final String legalBodyText = visibleBodyText();
		Assert.assertTrue("Expected legal content text to be visible for " + linkText, legalBodyText.length() > 80);

		saveScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (newHandle != null) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		waitForVisibleText("Sección Legal");
	}

	private void runStep(final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			report.put(stepName, Boolean.TRUE);
		} catch (final Throwable throwable) {
			report.put(stepName, Boolean.FALSE);
			failures.put(stepName, throwable);
			System.err.println("Step failed: " + stepName + " -> " + throwable.getMessage());
			try {
				saveScreenshot("failed_" + slug(stepName));
			} catch (final IOException ignored) {
				// Best effort evidence capture.
			}
		}
	}

	private void clickByVisibleText(final String... texts) {
		final List<String> errors = new ArrayList<>();
		for (final String text : texts) {
			try {
				if (clickByExactText(text) || clickByContainsText(text)) {
					waitForUiToLoad();
					return;
				}
			} catch (final RuntimeException runtimeException) {
				errors.add(text + ": " + runtimeException.getMessage());
			}
		}
		throw new NoSuchElementException("Unable to click element using visible text options " + String.join(", ", texts)
				+ ". Errors: " + errors);
	}

	private boolean clickByExactText(final String text) {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[normalize-space(.)=" + asXpathLiteral(text) + "]"));
		for (final WebElement element : elements) {
			if (isElementUsable(element)) {
				try {
					wait.until(ExpectedConditions.elementToBeClickable(element)).click();
					return true;
				} catch (final RuntimeException ignored) {
					// Try next matching element.
				}
			}
		}
		return false;
	}

	private boolean clickByContainsText(final String text) {
		final List<WebElement> elements = driver.findElements(
				By.xpath("//*[contains(normalize-space(.), " + asXpathLiteral(text) + ")]"));
		for (final WebElement element : elements) {
			if (isElementUsable(element)) {
				try {
					wait.until(ExpectedConditions.elementToBeClickable(element)).click();
					return true;
				} catch (final RuntimeException ignored) {
					// Try next matching element.
				}
			}
		}
		return false;
	}

	private void tryClickByExactTextIfVisible(final String text) {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[normalize-space(.)=" + asXpathLiteral(text) + "]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				element.click();
				waitForUiToLoad();
				return;
			}
		}
	}

	private boolean isTextVisible(final String text) {
		return driver.findElements(By.xpath("//*[normalize-space(.)=" + asXpathLiteral(text) + "]")).stream()
				.anyMatch(WebElement::isDisplayed);
	}

	private void waitForInvisibility(final String text) {
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space(.)=" + asXpathLiteral(text) + "]")));
	}

	private WebElement waitForVisibleText(final String text) {
		return wait.until(ExpectedConditions
				.visibilityOfElementLocated(By.xpath("//*[normalize-space(.)=" + asXpathLiteral(text) + "]")));
	}

	private void waitForSidebar() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside | //nav")));
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
	}

	private String waitForNewWindow(final Set<String> handlesBeforeClick) {
		try {
			wait.until(webDriver -> webDriver.getWindowHandles().size() > handlesBeforeClick.size());
		} catch (final TimeoutException ignored) {
			return null;
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBeforeClick.contains(handle)) {
				return handle;
			}
		}

		return null;
	}

	private java.util.Optional<String> findVisibleEmailFromPage() {
		final List<WebElement> candidates = driver.findElements(By.xpath("//*[contains(normalize-space(.), '@')]"));
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				final String[] tokens = candidate.getText().split("\\s+");
				for (final String token : tokens) {
					if (EMAIL_PATTERN.matcher(token.trim()).matches()) {
						return java.util.Optional.of(token.trim());
					}
				}
			}
		}
		return java.util.Optional.empty();
	}

	private boolean hasPotentialUserName(final String pageText) {
		final String normalized = pageText.replaceAll("[\\r\\n]+", " ").replaceAll("\\s{2,}", " ").trim();
		final String withoutKnownLabels = normalized.replace("Información General", "").replace("BUSINESS PLAN", "")
				.replace("Cambiar Plan", "").replace("Cuenta creada", "").replace("Estado activo", "")
				.replace("Idioma seleccionado", "");
		return withoutKnownLabels.matches(".*[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}\\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}.*");
	}

	private String visibleBodyText() {
		return driver.findElement(By.tagName("body")).getText().trim();
	}

	private WebElement findVisibleElement(final By locator) {
		for (final WebElement element : driver.findElements(locator)) {
			if (isElementUsable(element)) {
				return element;
			}
		}
		return null;
	}

	private boolean isElementUsable(final WebElement element) {
		try {
			return element.isDisplayed() && element.isEnabled();
		} catch (final RuntimeException runtimeException) {
			return false;
		}
	}

	private void saveScreenshot(final String checkpointName) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDirectory.resolve(checkpointName + ".png");
		Files.copy(source.toPath(), target);
	}

	private String readConfig(final String propertyName, final String fallback) {
		final String fromProperty = System.getProperty(propertyName);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}
		return fallback;
	}

	private String slug(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("_+", "_")
				.replaceAll("^_|_$", "");
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				builder.append(",");
			}
			if (chars[i] == '\'') {
				builder.append("\"'\"");
			} else {
				builder.append("'").append(chars[i]).append("'");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Full Test Report ===");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("=== Final legal URLs ===");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(entry.getKey() + ": " + entry.getValue());
			}
		}
		if (!failures.isEmpty()) {
			System.out.println("=== Failure details ===");
			for (final Map.Entry<String, Throwable> entry : failures.entrySet()) {
				System.out.println(entry.getKey() + ": " + entry.getValue().getClass().getSimpleName() + " -> "
						+ entry.getValue().getMessage());
			}
		}
		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
