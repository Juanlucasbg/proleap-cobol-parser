package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final String ENABLE_FLAG = "saleads.mi.negocio.enabled";
	private static final String ENABLE_ENV = "SALEADS_MI_NEGOCIO_ENABLED";
	private static final String BASE_URL_PROPERTY = "saleads.base.url";
	private static final String BASE_URL_ENV = "SALEADS_BASE_URL";
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> urlEvidence = new LinkedHashMap<>();
	private final List<String> notes = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;

	@Test
	public void saleads_mi_negocio_full_test() throws Exception {
		Assume.assumeTrue("Set " + ENABLE_FLAG + "=true (or " + ENABLE_ENV + "=true) to run this environment-dependent UI test.",
				isEnabled());

		setupDriver();
		initializeReport();

		performStep("Login", this::validateLoginFlow);
		performStep("Mi Negocio menu", this::validateMiNegocioMenu);
		performStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		performStep("Administrar Negocios view", this::validateAdministrarNegociosView);
		performStep("Información General", this::validateInformacionGeneral);
		performStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		performStep("Tus Negocios", this::validateTusNegocios);
		performStep("Términos y Condiciones", () -> validateLegalLink("Términos y Condiciones", "Términos y Condiciones"));
		performStep("Política de Privacidad", () -> validateLegalLink("Política de Privacidad", "Política de Privacidad"));

		final String summary = buildSummary();
		System.out.println(summary);
		assertTrue("Mi Negocio workflow validations failed.\n" + summary, report.values().stream().allMatch(Boolean::booleanValue));
	}

	@After
	public void cleanup() {
		if (driver != null) {
			driver.quit();
		}
	}

	private void setupDriver() throws IOException {
		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--window-size=1920,1080");
		if (Boolean.parseBoolean(System.getProperty("saleads.headless", System.getenv().getOrDefault("SALEADS_HEADLESS", "false")))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		screenshotDirectory = Files.createDirectories(Path.of("target", "saleads-mi-negocio-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT))));

		final String baseUrl = firstNonBlank(System.getProperty(BASE_URL_PROPERTY), System.getenv(BASE_URL_ENV));
		if (baseUrl != null) {
			driver.get(baseUrl);
		} else {
			notes.add("No base URL provided; test assumes WebDriver opens on the SaleADS login page.");
		}

		waitForUiLoad();
	}

	private void initializeReport() {
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
	}

	private boolean validateLoginFlow() throws Exception {
		final WebElement loginButton = waitForAnyVisibleText("Sign in with Google", "Iniciar con Google", "Continuar con Google",
				"Iniciar sesión con Google");
		clickAndWait(loginButton);
		chooseGoogleAccountIfPrompted(GOOGLE_ACCOUNT);

		final boolean appLoaded = isAnyTextVisible("Negocio", "Mi Negocio", "Dashboard", "Inicio");
		final boolean sidebarVisible = isSidebarVisible();
		captureScreenshot("01-dashboard");
		return appLoaded && sidebarVisible;
	}

	private boolean validateMiNegocioMenu() throws Exception {
		expandMiNegocioMenuIfNeeded();
		final boolean addVisible = isTextVisible("Agregar Negocio");
		final boolean adminVisible = isTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
		return addVisible && adminVisible;
	}

	private boolean validateAgregarNegocioModal() throws Exception {
		clickText("Agregar Negocio");
		final boolean titleVisible = isTextVisible("Crear Nuevo Negocio");
		final boolean businessNameVisible = isAnyElementPresent(By.xpath("//label[contains(normalize-space(), 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"));
		final boolean quotaVisible = isTextVisible("Tienes 2 de 3 negocios");
		final boolean buttonsVisible = isTextVisible("Cancelar") && isTextVisible("Crear Negocio");

		captureScreenshot("03-agregar-negocio-modal");

		if (businessNameVisible) {
			typeIfPresent(By.xpath("//input[contains(@placeholder, 'Nombre del Negocio') or @name='nombreNegocio' or @name='businessName']"),
					"Negocio Prueba Automatización");
		}

		if (isTextVisible("Cancelar")) {
			clickText("Cancelar");
		}

		return titleVisible && businessNameVisible && quotaVisible && buttonsVisible;
	}

	private boolean validateAdministrarNegociosView() throws Exception {
		expandMiNegocioMenuIfNeeded();
		clickText("Administrar Negocios");

		final boolean infoGeneral = isTextVisible("Información General");
		final boolean detallesCuenta = isTextVisible("Detalles de la Cuenta");
		final boolean tusNegocios = isTextVisible("Tus Negocios");
		final boolean seccionLegal = isTextVisible("Sección Legal");

		captureFullPageScreenshot("04-administrar-negocios-full");
		return infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
	}

	private boolean validateInformacionGeneral() {
		final String bodyText = safeBodyText();
		final boolean userNameVisible = hasLikelyUserName(bodyText);
		final boolean userEmailVisible = EMAIL_PATTERN.matcher(bodyText).find();
		final boolean planVisible = isTextVisible("BUSINESS PLAN");
		final boolean changePlanVisible = isTextVisible("Cambiar Plan");
		return userNameVisible && userEmailVisible && planVisible && changePlanVisible;
	}

	private boolean validateDetallesCuenta() {
		return isTextVisible("Cuenta creada") && isTextVisible("Estado activo") && isTextVisible("Idioma seleccionado");
	}

	private boolean validateTusNegocios() {
		final boolean sectionVisible = isTextVisible("Tus Negocios");
		final boolean addButton = isTextVisible("Agregar Negocio");
		final boolean quota = isTextVisible("Tienes 2 de 3 negocios");
		final boolean businessListPresent = isAnyElementPresent(By.xpath("//section//*[contains(normalize-space(),'Tus Negocios')]//ul/li"),
				By.xpath("//*[contains(normalize-space(),'Tus Negocios')]/following::*[self::ul or self::table][1]//*"));
		return sectionVisible && addButton && quota && businessListPresent;
	}

	private boolean validateLegalLink(final String linkText, final String headingText) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> before = driver.getWindowHandles();
		final String beforeUrl = driver.getCurrentUrl();

		clickText(linkText);

		String targetWindow = appWindow;
		wait.until(d -> d.getWindowHandles().size() > before.size()
				|| !d.findElements(byVisibleText(headingText)).isEmpty()
				|| !beforeUrl.equals(driver.getCurrentUrl()));

		final Set<String> after = driver.getWindowHandles();
		if (after.size() > before.size()) {
			for (String handle : after) {
				if (!before.contains(handle)) {
					targetWindow = handle;
					break;
				}
			}
			driver.switchTo().window(targetWindow);
			waitForUiLoad();
		}

		final boolean headingVisible = isTextVisible(headingText);
		final boolean legalContentVisible = safeBodyText().length() > 100;
		urlEvidence.put(linkText, driver.getCurrentUrl());
		captureScreenshot("legal-" + sanitize(linkText));

		if (!targetWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		return headingVisible && legalContentVisible;
	}

	private void performStep(final String stepName, final Step step) {
		try {
			report.put(stepName, step.run());
		} catch (Exception ex) {
			report.put(stepName, false);
			notes.add(stepName + " failed with exception: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
			try {
				captureScreenshot("error-" + sanitize(stepName));
			} catch (IOException ignored) {
				notes.add("Unable to capture error screenshot for " + stepName);
			}
		}
	}

	private void expandMiNegocioMenuIfNeeded() {
		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			if (isTextVisible("Negocio")) {
				clickText("Negocio");
			}
			if (isTextVisible("Mi Negocio")) {
				clickText("Mi Negocio");
			}
		}
	}

	private void chooseGoogleAccountIfPrompted(final String accountEmail) {
		try {
			if (isTextVisible(accountEmail)) {
				clickText(accountEmail);
			} else if (isAnyElementPresent(By.xpath("//div[contains(@data-email, " + xpathLiteral(accountEmail) + ")]"))) {
				click(By.xpath("//div[contains(@data-email, " + xpathLiteral(accountEmail) + ")]"));
			}
			waitForUiLoad();
		} catch (Exception ignored) {
			notes.add("Google account selector did not appear or account was already selected.");
		}
	}

	private void clickText(final String text) {
		click(waitForElementToClickByText(text));
	}

	private void click(final By by) {
		click(wait.until(ExpectedConditions.elementToBeClickable(by)));
	}

	private void click(final WebElement element) {
		element.click();
		waitForUiLoad();
	}

	private void clickAndWait(final WebElement element) {
		element.click();
		waitForUiLoad();
	}

	private WebElement waitForAnyVisibleText(final String... texts) {
		return wait.until(d -> {
			for (String text : texts) {
				List<WebElement> elements = d.findElements(byVisibleText(text));
				for (WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private WebElement waitForElementToClickByText(final String text) {
		return wait.until(ExpectedConditions.elementToBeClickable(byClickableText(text)));
	}

	private boolean isTextVisible(final String text) {
		try {
			wait.withTimeout(Duration.ofSeconds(10)).until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
			wait.withTimeout(DEFAULT_TIMEOUT);
			return true;
		} catch (TimeoutException ex) {
			wait.withTimeout(DEFAULT_TIMEOUT);
			return false;
		}
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (String text : texts) {
			if (isTextVisible(text)) {
				return true;
			}
		}
		return false;
	}

	private boolean isAnyElementPresent(final By... byCandidates) {
		for (By by : byCandidates) {
			if (!driver.findElements(by).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private boolean isSidebarVisible() {
		return isAnyElementPresent(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar')]"));
	}

	private void waitForUiLoad() {
		wait.until(webDriver -> {
			final Object ready = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(ready));
		});
	}

	private void typeIfPresent(final By by, final String text) {
		final List<WebElement> elements = driver.findElements(by);
		if (!elements.isEmpty()) {
			WebElement input = elements.get(0);
			input.click();
			input.clear();
			input.sendKeys(text);
			waitForUiLoad();
		}
	}

	private void captureScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = screenshotDirectory.resolve(sanitize(name) + ".png");
		Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureFullPageScreenshot(final String name) throws IOException {
		final JavascriptExecutor js = (JavascriptExecutor) driver;
		final long originalHeight = driver.manage().window().getSize().getHeight();
		final long pageHeight = ((Number) js.executeScript(
				"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);")).longValue();

		try {
			driver.manage().window().setSize(new org.openqa.selenium.Dimension(1920, (int) Math.min(pageHeight, 10000)));
			waitForUiLoad();
			captureScreenshot(name);
		} finally {
			driver.manage().window().setSize(new org.openqa.selenium.Dimension(1920, (int) originalHeight));
		}
	}

	private String safeBodyText() {
		final List<WebElement> body = driver.findElements(By.tagName("body"));
		if (body.isEmpty()) {
			return "";
		}
		return body.get(0).getText();
	}

	private boolean hasLikelyUserName(final String text) {
		if (text == null || text.isBlank()) {
			return false;
		}

		final String[] lines = text.split("\\R");
		for (String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.length() >= 4 && trimmed.length() <= 80 && trimmed.contains(" ")
					&& !trimmed.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN")
					&& !trimmed.toLowerCase(Locale.ROOT).contains("información general")
					&& !trimmed.toLowerCase(Locale.ROOT).contains("detalles de la cuenta")) {
				return true;
			}
		}

		return false;
	}

	private By byVisibleText(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]");
	}

	private By byClickableText(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath("//button[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"
				+ "|//a[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"
				+ "|//*[@role='button' and (normalize-space()=" + literal + " or contains(normalize-space(), " + literal + "))]"
				+ "|//*[self::span or self::div or self::li][normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]");
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final String[] parts = text.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(",\"'\",");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	private String buildSummary() {
		final StringBuilder sb = new StringBuilder();
		sb.append("\n==== SaleADS Mi Negocio Workflow Report ====\n");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			sb.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append('\n');
		}
		if (!urlEvidence.isEmpty()) {
			sb.append("\nCaptured legal URLs:\n");
			for (Map.Entry<String, String> entry : urlEvidence.entrySet()) {
				sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}
		sb.append("\nScreenshot directory: ").append(screenshotDirectory.toAbsolutePath()).append('\n');
		if (!notes.isEmpty()) {
			sb.append("\nNotes:\n");
			for (String note : notes) {
				sb.append("- ").append(note).append('\n');
			}
		}
		return sb.toString();
	}

	private String sanitize(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private boolean isEnabled() {
		return Boolean.parseBoolean(firstNonBlank(System.getProperty(ENABLE_FLAG), System.getenv(ENABLE_ENV), "false"));
	}

	private String firstNonBlank(final String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface Step {
		boolean run() throws Exception;
	}
}
