package io.proleap.cobol.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> failureReasons = new LinkedHashMap<>();
	private final List<Path> screenshots = new ArrayList<>();
	private final AtomicInteger screenshotCounter = new AtomicInteger(1);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path reportDir;
	private Path screenshotDir;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";
	private String appHandle;

	@Before
	public void setUp() throws IOException {
		REPORT_FIELDS.forEach(field -> stepStatus.put(field, "NOT_RUN"));
		reportDir = Paths.get("target", "surefire-reports", "saleads-mi-negocio");
		screenshotDir = reportDir.resolve("screenshots");
		Files.createDirectories(screenshotDir);

		driver = createDriver();
		driver.manage().window().setSize(new Dimension(1920, 1080));
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds()));
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		try {
			runStep("Login", this::stepLoginWithGoogle);
			runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			runStep("Información General", this::stepValidateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			runStep("Tus Negocios", this::stepValidateTusNegocios);
			runStep("Términos y Condiciones", this::stepValidateTerminosCondiciones);
			runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);
		} finally {
			writeFinalReport();
		}

		assertFalse("Failing validations: " + failureReasons, !failureReasons.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		navigateToLoginPageIfProvided();
		waitForUi();
		appHandle = driver.getWindowHandle();

		Set<String> before = new LinkedHashSet<>(driver.getWindowHandles());
		clickByText(List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"));
		selectGoogleAccountIfShown(before, GOOGLE_ACCOUNT_EMAIL);
		switchToApplicationWindow();

		waitForAnyText(List.of("Negocio", "Mi Negocio", "Dashboard", "Tablero"));
		assertVisible(By.xpath("//aside | //nav"), "Sidebar is not visible after login.");
		capture("01-dashboard");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByText(List.of("Mi Negocio"));
		waitForUi();
		assertAnyTextVisible(List.of("Agregar Negocio"));
		assertAnyTextVisible(List.of("Administrar Negocios"));
		capture("02-mi-negocio-menu");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByText(List.of("Agregar Negocio"));
		waitForUi();

		assertAnyTextVisible(List.of("Crear Nuevo Negocio"));
		WebElement nameInput = waitForAnyElement(List.of(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]")));
		assertNotNull("Nombre del Negocio input not found.", nameInput);
		assertAnyTextVisible(List.of("Tienes 2 de 3 negocios"));
		assertAnyTextVisible(List.of("Cancelar"));
		assertAnyTextVisible(List.of("Crear Negocio"));
		capture("03-agregar-negocio-modal");

		nameInput.click();
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatizacion");
		clickByText(List.of("Cancelar"));
		waitForUi();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isAnyTextVisible(List.of("Administrar Negocios"))) {
			clickByText(List.of("Mi Negocio"));
			waitForUi();
		}
		clickByText(List.of("Administrar Negocios"));
		waitForUi();
		assertAnyTextVisible(List.of("Información General"));
		assertAnyTextVisible(List.of("Detalles de la Cuenta"));
		assertAnyTextVisible(List.of("Tus Negocios"));
		assertAnyTextVisible(List.of("Sección Legal"));
		capture("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		WebElement section = sectionFromHeading("Información General");
		assertNotNull("Información General section missing.", section);
		assertHasEmail(section);
		assertSectionHasText(section, "BUSINESS PLAN");
		assertSectionHasText(section, "Cambiar Plan");
		assertTrue("User name was not detected.", likelyHasName(section.getText()));
	}

	private void stepValidateDetallesCuenta() {
		WebElement section = sectionFromHeading("Detalles de la Cuenta");
		assertNotNull("Detalles de la Cuenta section missing.", section);
		assertSectionHasText(section, "Cuenta creada");
		assertSectionHasText(section, "Estado activo");
		assertSectionHasText(section, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		WebElement section = sectionFromHeading("Tus Negocios");
		assertNotNull("Tus Negocios section missing.", section);
		assertSectionHasText(section, "Agregar Negocio");
		assertSectionHasText(section, "Tienes 2 de 3 negocios");
		List<WebElement> rows = section.findElements(By.xpath(
				".//*[self::li or self::tr or self::article or contains(@class,'card') or contains(@class,'business') or contains(@class,'negocio')]"));
		assertTrue("Business list not visible.", rows.size() > 0);
	}

	private void stepValidateTerminosCondiciones() throws Exception {
		termsUrl = openLegalAndReturn("Términos y Condiciones", "05-terminos");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		privacyUrl = openLegalAndReturn("Política de Privacidad", "06-politica");
	}

	private String openLegalAndReturn(String linkText, String screenshotName) throws Exception {
		String currentAppHandle = driver.getWindowHandle();
		String previousUrl = safeCurrentUrl();
		Set<String> before = new LinkedHashSet<>(driver.getWindowHandles());

		clickByText(List.of(linkText));
		waitForUi();
		switchToNewTabOrNavigation(before, previousUrl);
		assertAnyTextVisible(List.of(linkText));
		assertLegalTextVisible();
		capture(screenshotName);
		String finalUrl = safeCurrentUrl();

		if (driver.getWindowHandles().contains(currentAppHandle)) {
			driver.switchTo().window(currentAppHandle);
			waitForUi();
		}
		return finalUrl;
	}

	private void navigateToLoginPageIfProvided() {
		String loginUrl = System.getenv().getOrDefault("SALEADS_LOGIN_URL", "").trim();
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
			return;
		}
		String current = safeCurrentUrl();
		if (current == null || current.isBlank() || "about:blank".equalsIgnoreCase(current)) {
			throw new IllegalStateException(
					"SALEADS_LOGIN_URL is not set and browser is not on login page. Set SALEADS_LOGIN_URL for target environment.");
		}
	}

	private void selectGoogleAccountIfShown(Set<String> handlesBeforeClick, String email) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12))
					.until(d -> d.getWindowHandles().size() >= handlesBeforeClick.size());
		} catch (TimeoutException ignored) {
			// Account chooser did not appear.
		}

		for (String handle : driver.getWindowHandles()) {
			if (!handlesBeforeClick.contains(handle)) {
				driver.switchTo().window(handle);
				break;
			}
		}

		try {
			WebElement account = waitForElement(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(email) + ")]"), 8);
			if (account != null) {
				safeClick(account);
				waitForUi();
			}
		} catch (Exception ignored) {
			// Already authenticated or selector did not render.
		}
	}

	private void switchToApplicationWindow() {
		for (String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (!safeCurrentUrl().toLowerCase(Locale.ROOT).contains("accounts.google.com")) {
				appHandle = handle;
				return;
			}
		}
		if (appHandle != null && driver.getWindowHandles().contains(appHandle)) {
			driver.switchTo().window(appHandle);
		}
	}

	private void switchToNewTabOrNavigation(Set<String> handlesBeforeClick, String previousUrl) {
		wait.until((ExpectedCondition<Boolean>) d -> d.getWindowHandles().size() > handlesBeforeClick.size()
				|| !safeCurrentUrl().equals(previousUrl));
		for (String handle : driver.getWindowHandles()) {
			if (!handlesBeforeClick.contains(handle)) {
				driver.switchTo().window(handle);
				return;
			}
		}
	}

	private void runStep(String step, ThrowingRunnable action) {
		try {
			action.run();
			stepStatus.put(step, "PASS");
		} catch (Throwable t) {
			stepStatus.put(step, "FAIL");
			failureReasons.put(step, t.getClass().getSimpleName() + ": " + t.getMessage());
			try {
				capture("error-" + sanitize(step));
			} catch (Exception ignored) {
				// Best effort evidence.
			}
		}
	}

	private void writeFinalReport() throws IOException {
		Path report = reportDir.resolve("saleads-mi-negocio-final-report.txt");
		StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Full Test - Final Report\n");
		sb.append("Generated at: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
		for (String field : REPORT_FIELDS) {
			sb.append(field).append(": ").append(stepStatus.getOrDefault(field, "NOT_RUN")).append("\n");
			if (failureReasons.containsKey(field)) {
				sb.append("  Reason: ").append(failureReasons.get(field)).append("\n");
			}
		}
		sb.append("\nEvidence:\n");
		for (Path shot : screenshots) {
			sb.append("- Screenshot: ").append(shot).append("\n");
		}
		sb.append("- Términos y Condiciones URL: ").append(termsUrl).append("\n");
		sb.append("- Política de Privacidad URL: ").append(privacyUrl).append("\n");
		Files.writeString(report, sb.toString(), StandardCharsets.UTF_8);
	}

	private void waitForUi() {
		try {
			wait.until(d -> "complete"
					.equals(String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"))));
		} catch (Exception ignored) {
			// Some transitions are async redirects; continue.
		}
		try {
			new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(d -> d.findElements(By.xpath(
							"//*[contains(@class,'spinner') or contains(@class,'loading') or contains(@class,'loader')]")).isEmpty());
		} catch (Exception ignored) {
			// Best effort stability check.
		}
	}

	private void clickByText(List<String> texts) {
		WebElement element = waitForAnyElement(textLocators(texts));
		assertNotNull("Could not find clickable text among: " + texts, element);
		safeClick(element);
		waitForUi();
	}

	private void safeClick(WebElement element) {
		try {
			element.click();
		} catch (Exception e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void assertAnyTextVisible(List<String> texts) {
		assertNotNull("Expected visible text not found: " + texts, waitForAnyText(texts));
	}

	private boolean isAnyTextVisible(List<String> texts) {
		return waitForAnyText(texts, 3) != null;
	}

	private WebElement waitForAnyText(List<String> texts) {
		return waitForAnyText(texts, (int) timeoutSeconds());
	}

	private WebElement waitForAnyText(List<String> texts, int timeoutSec) {
		return waitForAnyElement(textLocators(texts), timeoutSec);
	}

	private List<By> textLocators(List<String> texts) {
		List<By> locators = new ArrayList<>();
		for (String text : texts) {
			String literal = xpathLiteral(text);
			locators.add(By.xpath(
					"//*[self::a or self::button or self::span or self::div or self::p or self::h1 or self::h2 or self::h3][contains(normalize-space(.), "
							+ literal + ")]"));
		}
		return locators;
	}

	private WebElement waitForAnyElement(List<By> locators) {
		return waitForAnyElement(locators, (int) timeoutSeconds());
	}

	private WebElement waitForAnyElement(List<By> locators, int timeoutSec) {
		WebDriverWait customWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSec));
		return customWait.until(d -> {
			for (By by : locators) {
				WebElement el = firstVisible(d.findElements(by));
				if (el != null) {
					return el;
				}
			}
			return null;
		});
	}

	private WebElement waitForElement(By locator, int timeoutSec) {
		WebDriverWait customWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSec));
		return customWait.until(d -> firstVisible(d.findElements(locator)));
	}

	private WebElement firstVisible(List<WebElement> elements) {
		for (WebElement el : elements) {
			try {
				if (el.isDisplayed()) {
					return el;
				}
			} catch (Exception ignored) {
				// Stale element; skip.
			}
		}
		return null;
	}

	private void assertVisible(By locator, String message) {
		assertNotNull(message, waitForElement(locator, (int) timeoutSeconds()));
	}

	private WebElement sectionFromHeading(String heading) {
		String literal = xpathLiteral(heading);
		WebElement headingElement = waitForElement(
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::p or self::span][contains(normalize-space(.), "
						+ literal + ")]"),
				12);
		if (headingElement == null) {
			return null;
		}
		try {
			return headingElement.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		} catch (Exception e) {
			return headingElement;
		}
	}

	private void assertSectionHasText(WebElement section, String text) {
		assertTrue("Expected text '" + text + "' not found in section.",
				section.getText().toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)));
	}

	private void assertHasEmail(WebElement section) {
		for (String token : section.getText().split("\\s+")) {
			if (EMAIL_PATTERN.matcher(token.trim()).matches()) {
				return;
			}
		}
		throw new AssertionError("User email is not visible.");
	}

	private boolean likelyHasName(String sectionText) {
		String normalized = sectionText.replaceAll("\\s+", " ").trim();
		String[] tokens = normalized.split(" ");
		int nonEmailWords = 0;
		for (String token : tokens) {
			if (!token.contains("@") && token.matches(".*[A-Za-z].*")) {
				nonEmailWords++;
				if (nonEmailWords >= 2) {
					return true;
				}
			}
		}
		return false;
	}

	private void assertLegalTextVisible() {
		List<WebElement> legal = driver.findElements(By.xpath(
				"//p[string-length(normalize-space()) > 80] | //li[string-length(normalize-space()) > 80] | //div[string-length(normalize-space()) > 120]"));
		assertTrue("Legal content text is not visible.", legal.size() > 0);
	}

	private Path capture(String name) throws IOException {
		File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		String fileName = String.format("%02d-%s.png", screenshotCounter.getAndIncrement(), sanitize(name));
		Path target = screenshotDir.resolve(fileName);
		Files.copy(src.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		screenshots.add(target);
		return target;
	}

	private WebDriver createDriver() throws MalformedURLException {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		String remote = System.getenv().getOrDefault("SALEADS_REMOTE_WEBDRIVER_URL", "").trim();
		if (!remote.isEmpty()) {
			return new RemoteWebDriver(new URL(remote), options);
		}

		WebDriverManager.chromedriver().setup();
		return new ChromeDriver(options);
	}

	private long timeoutSeconds() {
		return Long.parseLong(System.getenv().getOrDefault("SALEADS_TIMEOUT_SECONDS", "30"));
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (Exception e) {
			return "";
		}
	}

	private String sanitize(String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("-{2,}", "-")
				.replaceAll("^-|-$", "");
	}

	private String xpathLiteral(String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		StringBuilder out = new StringBuilder("concat(");
		String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			out.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				out.append(",\"'\",");
			}
		}
		out.append(")");
		return out.toString();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
