package saleads;

import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MENU = "Mi Negocio menu";
	private static final String REPORT_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN = "Administrar Negocios view";
	private static final String REPORT_GENERAL = "Información General";
	private static final String REPORT_DETALLES = "Detalles de la Cuenta";
	private static final String REPORT_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private static final List<String> REPORT_ORDER = Arrays.asList(REPORT_LOGIN, REPORT_MENU, REPORT_MODAL, REPORT_ADMIN,
			REPORT_GENERAL, REPORT_DETALLES, REPORT_NEGOCIOS, REPORT_TERMINOS, REPORT_PRIVACIDAD);

	private static final String STATUS_PASS = "PASS";
	private static final String STATUS_FAIL = "FAIL";
	private static final String STATUS_BLOCKED = "BLOCKED";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private int screenshotCounter;
	private final Map<String, String> statusByStep = new LinkedHashMap<>();
	private final Map<String, String> detailsByStep = new LinkedHashMap<>();
	private String termsUrl = "";
	private String privacyUrl = "";

	@Before
	public void setUp() throws IOException {
		for (final String reportField : REPORT_ORDER) {
			statusByStep.put(reportField, STATUS_BLOCKED);
			detailsByStep.put(reportField, "Not executed.");
		}

		final Path evidenceRoot = Path.of("target", "saleads-evidence");
		Files.createDirectories(evidenceRoot);
		evidenceDir = evidenceRoot.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		driver = createWebDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(getIntConfig("SALEADS_WAIT_SECONDS", "saleads.wait.seconds", 20)));

		final String loginUrl = getConfig("SALEADS_LOGIN_URL", "saleads.login.url");
		Assume.assumeTrue(
				"SALEADS_LOGIN_URL (or -Dsaleads.login.url) is required to run this E2E test without hardcoding any domain.",
				loginUrl != null && !loginUrl.isBlank());
		driver.get(loginUrl);
		waitForUiLoad();
	}

	@After
	public void tearDown() {
		printReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final boolean loginOk = runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		final boolean menuOk = runStepWithDependency(REPORT_MENU, loginOk, REPORT_LOGIN, this::stepOpenMiNegocioMenu);
		final boolean modalOk = runStepWithDependency(REPORT_MODAL, menuOk, REPORT_MENU, this::stepValidateAgregarModal);
		final boolean adminOk = runStepWithDependency(REPORT_ADMIN, menuOk, REPORT_MENU, this::stepOpenAdministrarNegocios);

		runStepWithDependency(REPORT_GENERAL, adminOk, REPORT_ADMIN, this::stepValidateInformacionGeneral);
		runStepWithDependency(REPORT_DETALLES, adminOk, REPORT_ADMIN, this::stepValidateDetallesCuenta);
		runStepWithDependency(REPORT_NEGOCIOS, adminOk, REPORT_ADMIN, this::stepValidateTusNegocios);
		runStepWithDependency(REPORT_TERMINOS, adminOk, REPORT_ADMIN, this::stepValidateTerminos);
		runStepWithDependency(REPORT_PRIVACIDAD, adminOk, REPORT_ADMIN, this::stepValidatePrivacidad);

		final List<String> failed = REPORT_ORDER.stream().filter(step -> !STATUS_PASS.equals(statusByStep.get(step)))
				.collect(Collectors.toList());
		Assert.assertTrue("SaleADS Mi Negocio workflow failed in: " + failed, failed.isEmpty());
	}

	private boolean runStepWithDependency(final String stepName, final boolean dependencyOk, final String dependencyStep,
			final Runnable action) {
		if (!dependencyOk) {
			recordBlocked(stepName, "Blocked because '" + dependencyStep + "' did not pass.");
			return false;
		}
		return runStep(stepName, action);
	}

	private boolean runStep(final String stepName, final Runnable action) {
		try {
			action.run();
			recordPass(stepName, "Validation completed.");
			return true;
		} catch (final Throwable throwable) {
			recordFail(stepName, throwable.getMessage());
			takeScreenshot("failed-" + stepName.toLowerCase(Locale.ROOT).replace(" ", "-"));
			return false;
		}
	}

	private void stepLoginWithGoogle() {
		final String googleAccountEmail = getConfig("SALEADS_GOOGLE_ACCOUNT_EMAIL", "saleads.google.account.email") != null
				? getConfig("SALEADS_GOOGLE_ACCOUNT_EMAIL", "saleads.google.account.email")
				: "juanlucasbarbiergarzon@gmail.com";
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Login with Google",
				"Entrar con Google");
		switchToNewWindowIfPresent(beforeHandles);

		if (isTextVisible(googleAccountEmail)) {
			clickByVisibleText(googleAccountEmail);
		}

		waitForMainApplication();
		Assert.assertTrue("Left sidebar navigation is not visible after login.", isSidebarVisibleQuick());
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		if (!isTextVisible("Mi Negocio")) {
			clickByVisibleText("Negocio");
		}
		clickByVisibleText("Mi Negocio");

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarModal() {
		clickByVisibleText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		assertAnyVisible(By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(), 'Nombre del Negocio')]"));
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		takeScreenshot("03-crear-nuevo-negocio-modal");

		final WebElement input = findVisibleElement(By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"));
		if (input != null) {
			input.click();
			waitForUiLoad();
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
		}

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']")));
	}

	private void stepOpenAdministrarNegocios() {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}
		clickByVisibleText("Administrar Negocios");

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		takeScreenshot("04-administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("Información General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String pageText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		Assert.assertTrue("Expected a visible user email in Información General.", EMAIL_PATTERN.matcher(pageText).find());
		final String nonEmailText = EMAIL_PATTERN.matcher(pageText).replaceAll("").trim();
		Assert.assertTrue("Expected visible user-identifying text besides email.", nonEmailText.length() > 0);
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminos() {
		termsUrl = validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "05-terminos-y-condiciones");
	}

	private void stepValidatePrivacidad() {
		privacyUrl = validateLegalLink("Política de Privacidad", "Política de Privacidad", "06-politica-de-privacidad");
	}

	private String validateLegalLink(final String linkText, final String headingText, final String screenshotName) {
		final String appWindow = driver.getWindowHandle();
		final String beforeUrl = driver.getCurrentUrl();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickByVisibleText(linkText);

		new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> d.getWindowHandles().size() > beforeHandles.size()
				|| !normalizeWhitespace(d.getCurrentUrl()).equals(normalizeWhitespace(beforeUrl)));

		boolean openedNewTab = false;
		if (driver.getWindowHandles().size() > beforeHandles.size()) {
			openedNewTab = true;
			final String newHandle = driver.getWindowHandles().stream().filter(handle -> !beforeHandles.contains(handle))
					.findFirst().orElse(appWindow);
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		} else {
			waitForUiLoad();
		}

		assertVisibleText(headingText);
		final String bodyText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		Assert.assertTrue("Expected legal content text for " + headingText, bodyText.length() > 120);

		takeScreenshot(screenshotName);
		final String legalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		return legalUrl;
	}

	private void clickByVisibleText(final String... textOptions) {
		Throwable lastError = null;
		for (final String text : textOptions) {
			try {
				final WebElement element = waitForDisplayedTextElement(text);
				safeClick(element);
				waitForUiLoad();
				return;
			} catch (final Throwable throwable) {
				lastError = throwable;
			}
		}
		throw new IllegalStateException("Could not click any text option: " + Arrays.toString(textOptions), lastError);
	}

	private WebElement waitForDisplayedTextElement(final String text) {
		final By by = By.xpath("//*[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), "
				+ xpathLiteral(text) + ")]");
		return wait.until(d -> {
			final List<WebElement> elements = d.findElements(by);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return firstClickableAncestor(element);
				}
			}
			return null;
		});
	}

	private WebElement firstClickableAncestor(final WebElement element) {
		WebElement current = element;
		for (int i = 0; i < 6; i++) {
			final String tag = current.getTagName();
			final String role = current.getAttribute("role");
			final boolean clickable = "button".equalsIgnoreCase(tag) || "a".equalsIgnoreCase(tag)
					|| "button".equalsIgnoreCase(role) || current.getAttribute("onclick") != null;
			if (clickable) {
				return current;
			}
			try {
				current = current.findElement(By.xpath("./.."));
			} catch (final NoSuchElementException noSuchElementException) {
				return element;
			}
		}
		return element;
	}

	private void safeClick(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final Throwable clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void assertVisibleText(final String text) {
		Assert.assertTrue("Expected visible text: " + text, isTextVisible(text));
	}

	private boolean isTextVisible(final String text) {
		final String xpath = "//*[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), "
				+ xpathLiteral(text) + ")]";
		final List<WebElement> elements = driver.findElements(By.xpath(xpath));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void assertAnyVisible(final By... locators) {
		for (final By locator : locators) {
			final WebElement found = findVisibleElement(locator);
			if (found != null) {
				return;
			}
		}
		throw new AssertionError("None of the expected elements are visible: " + Arrays.toString(locators));
	}

	private WebElement findVisibleElement(final By locator) {
		for (final WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		return null;
	}

	private void waitForMainApplication() {
		final WebDriverWait postLoginWait = new WebDriverWait(driver, Duration.ofSeconds(120));
		postLoginWait.until(d -> {
			for (final String handle : d.getWindowHandles()) {
				d.switchTo().window(handle);
				if (isSidebarVisibleQuick()) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isSidebarVisibleQuick() {
		final List<By> candidates = Arrays.asList(By.cssSelector("aside"), By.xpath("//*[contains(@class,'sidebar')]"),
				By.xpath("//*[normalize-space()='Negocio']"), By.xpath("//*[normalize-space()='Mi Negocio']"));
		for (final By locator : candidates) {
			final WebElement visibleElement = findVisibleElement(locator);
			if (visibleElement != null) {
				return true;
			}
		}
		return false;
	}

	private void switchToNewWindowIfPresent(final Set<String> beforeHandles) {
		final Set<String> afterHandles = driver.getWindowHandles();
		if (afterHandles.size() <= beforeHandles.size()) {
			return;
		}
		for (final String handle : afterHandles) {
			if (!beforeHandles.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				return;
			}
		}
	}

	private void waitForUiLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(600);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String name) {
		try {
			screenshotCounter++;
			final String fileName = String.format("%02d-%s.png", screenshotCounter, sanitize(name));
			final Path destination = evidenceDir.resolve(fileName);
			final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
			System.out.println("Screenshot captured: " + destination.toAbsolutePath());
		} catch (final IOException ioException) {
			throw new UncheckedIOException("Failed taking screenshot: " + name, ioException);
		}
	}

	private String sanitize(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-")
				.replaceAll("(^-|-$)", "");
	}

	private String xpathLiteral(final String value) {
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

	private WebDriver createWebDriver() throws MalformedURLException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--lang=es-ES");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (getBooleanConfig("SALEADS_HEADLESS", "saleads.headless", true)) {
			options.addArguments("--headless=new");
		}

		final String remoteWebDriverUrl = getConfig("SALEADS_REMOTE_WEBDRIVER_URL", "saleads.remote.webdriver.url");
		if (remoteWebDriverUrl != null && !remoteWebDriverUrl.isBlank()) {
			return new RemoteWebDriver(new URL(remoteWebDriverUrl), options);
		}

		WebDriverManager.chromedriver().setup();
		return new ChromeDriver(options);
	}

	private String getConfig(final String envVar, final String sysProperty) {
		final String propertyValue = System.getProperty(sysProperty);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		final String envValue = System.getenv(envVar);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		return null;
	}

	private int getIntConfig(final String envVar, final String sysProperty, final int defaultValue) {
		final String raw = getConfig(envVar, sysProperty);
		if (raw == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(raw);
		} catch (final NumberFormatException numberFormatException) {
			return defaultValue;
		}
	}

	private boolean getBooleanConfig(final String envVar, final String sysProperty, final boolean defaultValue) {
		final String raw = getConfig(envVar, sysProperty);
		if (raw == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(raw);
	}

	private String normalizeWhitespace(final String value) {
		return value == null ? "" : value.replaceAll("\\s+", " ").trim();
	}

	private void recordPass(final String step, final String detail) {
		statusByStep.put(step, STATUS_PASS);
		detailsByStep.put(step, detail);
	}

	private void recordFail(final String step, final String detail) {
		statusByStep.put(step, STATUS_FAIL);
		detailsByStep.put(step, detail == null ? "No details." : detail);
	}

	private void recordBlocked(final String step, final String detail) {
		statusByStep.put(step, STATUS_BLOCKED);
		detailsByStep.put(step, detail);
	}

	private void printReport() {
		System.out.println("\n==== SaleADS Mi Negocio Full Workflow Report ====");
		for (final String step : REPORT_ORDER) {
			System.out.println(step + ": " + statusByStep.get(step) + " - " + detailsByStep.get(step));
		}
		final List<String> failedSteps = new ArrayList<>();
		for (final String step : REPORT_ORDER) {
			if (!STATUS_PASS.equals(statusByStep.get(step))) {
				failedSteps.add(step + "=" + statusByStep.get(step));
			}
		}
		System.out.println("Terms final URL: " + termsUrl);
		System.out.println("Privacy final URL: " + privacyUrl);
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("Summary: " + (failedSteps.isEmpty() ? "PASS" : "FAIL " + failedSteps));
		System.out.println("===============================================\n");
	}
}
