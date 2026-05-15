package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.fail;

import java.io.File;
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
import java.util.Map;
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

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private Path finalReportPath;
	private int screenshotCounter = 0;

	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws Exception {
		final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue("Set SALEADS_LOGIN_URL or -Dsaleads.login.url to run this test.",
				loginUrl != null && !loginUrl.isBlank());

		final int timeoutSeconds = parseInt(firstNonBlank(System.getProperty("saleads.timeout.seconds"),
				System.getenv("SALEADS_TIMEOUT_SECONDS"), "30"), 30);
		final boolean headless = Boolean.parseBoolean(
				firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "true"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		evidenceDirectory = Paths.get("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDirectory);

		driver.get(loginUrl);
		waitForUiLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStep("Login", this::stepLoginWithGoogleAndValidateShell);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalDocument("Términos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalDocument("Política de Privacidad"));

		writeFinalReport();
		assertAllStepsPassed();
	}

	private void stepLoginWithGoogleAndValidateShell() throws Exception {
		if (isAnyTextVisible(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google"), 5)) {
			final String appHandle = driver.getWindowHandle();
			final Set<String> handlesBeforeClick = driver.getWindowHandles();

			clickByAnyVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
					"Iniciar con Google", "Google"));

			final String maybeNewGoogleHandle = waitForNewWindow(handlesBeforeClick, Duration.ofSeconds(12));
			if (maybeNewGoogleHandle != null) {
				driver.switchTo().window(maybeNewGoogleHandle);
				waitForUiLoad();
			}

			trySelectGoogleAccount(GOOGLE_ACCOUNT_EMAIL);

			if (driver.getWindowHandles().contains(appHandle)) {
				driver.switchTo().window(appHandle);
			}
		}

		waitForSidebar();
		assertAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio"), "Left sidebar with Negocio menu was not visible.");
		captureScreenshot("dashboard-loaded", false);
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		optionalClickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("mi-negocio-menu-expanded", false);
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		captureScreenshot("agregar-negocio-modal", false);

		final By nombreField = By.xpath(
				"//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder,'Nombre del Negocio')]");
		final List<WebElement> fields = driver.findElements(nombreField);
		if (!fields.isEmpty() && fields.get(0).isDisplayed()) {
			fields.get(0).click();
			waitForUiLoad();
			fields.get(0).sendKeys("Negocio Prueba Automatización");
		}
		clickByVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios", 3)) {
			optionalClickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertAnyTextVisible(Arrays.asList("Sección Legal", "Seccion Legal"), "Sección Legal was not visible.");
		captureScreenshot("administrar-negocios-page", true);
	}

	private void stepValidateInformacionGeneral() throws Exception {
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");

		final String pageText = visiblePageText();
		final String email = firstEmail(pageText);
		if (email == null) {
			throw new AssertionError("No user email was visible in Información General.");
		}

		final String userName = findPotentialUserName(pageText);
		if (userName == null) {
			throw new AssertionError("No user name candidate was visible in Información General.");
		}
	}

	private void stepValidateDetallesCuenta() throws Exception {
		assertTextVisible("Cuenta creada");
		assertAnyTextVisible(Arrays.asList("Estado activo", "Estado Activo"), "Estado activo was not visible.");
		assertAnyTextVisible(Arrays.asList("Idioma seleccionado", "Idioma Seleccionado"),
				"Idioma seleccionado was not visible.");
	}

	private void stepValidateTusNegocios() throws Exception {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");

		final String sectionText = sectionText("Tus Negocios");
		final String compact = sectionText.replace("Tus Negocios", "").trim();
		if (compact.length() < 20) {
			throw new AssertionError("Business list content appears empty in Tus Negocios.");
		}
	}

	private void stepValidateLegalDocument(final String linkText) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);

		final String legalHandle = waitForNewWindow(handlesBeforeClick, Duration.ofSeconds(10));
		final boolean openedInNewTab = legalHandle != null;
		if (openedInNewTab) {
			driver.switchTo().window(legalHandle);
			waitForUiLoad();
		}

		assertTextVisible(linkText);
		final String legalText = visiblePageText();
		if (legalText.length() < 200) {
			throw new AssertionError("Legal content text was too short for " + linkText + ".");
		}

		captureScreenshot(slug(linkText) + "-page", false);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String fieldName, final CheckedStep step) {
		try {
			step.run();
			stepResults.put(fieldName, true);
		} catch (final Throwable throwable) {
			stepResults.put(fieldName, false);
			stepErrors.put(fieldName, cleanError(throwable));
			captureScreenshot("failure-" + slug(fieldName), false);
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failed = REPORT_FIELDS.stream().filter(field -> !Boolean.TRUE.equals(stepResults.get(field)))
				.collect(Collectors.toList());

		if (!failed.isEmpty()) {
			final String reportLocation = finalReportPath == null ? "report not written" : finalReportPath.toString();
			fail("Mi Negocio workflow validation failed for steps: " + String.join(", ", failed)
					+ ". See report and screenshots under " + reportLocation);
		}
	}

	private void writeFinalReport() throws Exception {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test\n");
		report.append("Generated at: ").append(LocalDateTime.now()).append('\n');
		report.append('\n');
		report.append("Final Report\n");
		report.append("------------\n");

		for (final String field : REPORT_FIELDS) {
			final boolean passed = Boolean.TRUE.equals(stepResults.get(field));
			report.append(field).append(": ").append(passed ? "PASS" : "FAIL");
			if (!passed && stepErrors.containsKey(field)) {
				report.append(" (").append(stepErrors.get(field)).append(")");
			}
			report.append('\n');
		}

		report.append('\n');
		report.append("Evidence directory: ").append(evidenceDirectory).append('\n');
		report.append("Captured legal URLs:\n");
		if (legalUrls.isEmpty()) {
			report.append("- none\n");
		} else {
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		finalReportPath = evidenceDirectory.resolve("final-report.txt");
		Files.writeString(finalReportPath, report.toString(), StandardCharsets.UTF_8);
		System.out.println(report);
	}

	private void waitForSidebar() {
		wait.until(driver -> !driver
				.findElements(By.xpath("//aside | //nav[contains(@class,'sidebar')] | //nav[.//*[contains(.,'Negocio')]]"))
				.isEmpty());
		waitForUiLoad();
	}

	private void waitForUiLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		try {
			Thread.sleep(500);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void clickByAnyVisibleText(final List<String> texts) {
		for (final String text : texts) {
			if (tryClickByVisibleText(text)) {
				return;
			}
		}
		throw new NoSuchElementException("No clickable element found for any of texts: " + texts);
	}

	private void clickByVisibleText(final String text) {
		if (!tryClickByVisibleText(text)) {
			throw new NoSuchElementException("Could not click element with visible text: " + text);
		}
	}

	private void optionalClickByVisibleText(final String text) {
		tryClickByVisibleText(text);
	}

	private boolean tryClickByVisibleText(final String text) {
		final String textLiteral = toXPathLiteral(text);
		final List<By> locators = Arrays.asList(
				By.xpath("//button[normalize-space(.)=" + textLiteral + "] | //a[normalize-space(.)=" + textLiteral
						+ "] | //*[@role='button' and normalize-space(.)=" + textLiteral + "] | //li[normalize-space(.)="
						+ textLiteral + "]"),
				By.xpath("//*[self::button or self::a or @role='button' or self::li or self::span or self::div]"
						+ "[contains(normalize-space(.)," + textLiteral + ")]"),
				By.xpath("//*[normalize-space(.)=" + textLiteral + "]"));

		for (final By locator : locators) {
			final List<WebElement> candidates = driver.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (!candidate.isDisplayed()) {
					continue;
				}
				try {
					wait.until(ExpectedConditions.elementToBeClickable(candidate)).click();
					waitForUiLoad();
					return true;
				} catch (final Exception exception) {
					try {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", candidate);
						waitForUiLoad();
						return true;
					} catch (final Exception ignored) {
						// Try next candidate
					}
				}
			}
		}
		return false;
	}

	private boolean isAnyTextVisible(final List<String> texts, final int timeoutSeconds) {
		for (final String text : texts) {
			if (isTextVisible(text, timeoutSeconds)) {
				return true;
			}
		}
		return false;
	}

	private boolean isTextVisible(final String text, final int timeoutSeconds) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
			shortWait.until(ExpectedConditions.visibilityOfElementLocated(textLocator(text)));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void assertTextVisible(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(textLocator(text)));
	}

	private void assertAnyTextVisible(final List<String> texts, final String errorMessage) {
		for (final String text : texts) {
			if (isTextVisible(text, 5)) {
				return;
			}
		}
		throw new AssertionError(errorMessage);
	}

	private By textLocator(final String text) {
		final String textLiteral = toXPathLiteral(text);
		return By.xpath("//*[normalize-space(.)=" + textLiteral + " or contains(normalize-space(.)," + textLiteral + ")]");
	}

	private void trySelectGoogleAccount(final String accountEmail) {
		if (!isTextVisible(accountEmail, 8)) {
			return;
		}
		clickByVisibleText(accountEmail);
	}

	private String waitForNewWindow(final Set<String> previousHandles, final Duration timeout) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			return shortWait.until(webDriver -> {
				final Set<String> currentHandles = webDriver.getWindowHandles();
				if (currentHandles.size() <= previousHandles.size()) {
					return null;
				}
				for (final String handle : currentHandles) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private String visiblePageText() {
		final Object result = ((JavascriptExecutor) driver).executeScript(
				"return (document && document.body && document.body.innerText) ? document.body.innerText : '';");
		return result == null ? "" : result.toString();
	}

	private String firstEmail(final String pageText) {
		final java.util.regex.Matcher matcher = EMAIL_PATTERN.matcher(pageText);
		return matcher.find() ? matcher.group() : null;
	}

	private String findPotentialUserName(final String pageText) {
		final Set<String> ignored = new LinkedHashSet<>(Arrays.asList("Información General", "Detalles de la Cuenta",
				"Tus Negocios", "Sección Legal", "BUSINESS PLAN", "Cambiar Plan", "Cuenta creada", "Estado activo",
				"Idioma seleccionado", "Agregar Negocio", "Administrar Negocios", "Términos y Condiciones",
				"Política de Privacidad"));
		final List<String> lines = Arrays.stream(pageText.split("\\R")).map(String::trim).filter(line -> !line.isEmpty())
				.collect(Collectors.toCollection(ArrayList::new));

		for (final String line : lines) {
			if (ignored.contains(line) || EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (line.length() >= 3 && line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*") && !line.matches(".*\\d{2,}.*")) {
				return line;
			}
		}
		return null;
	}

	private String sectionText(final String sectionTitle) {
		final String titleLiteral = toXPathLiteral(sectionTitle);
		final By sectionLocator = By.xpath("//*[normalize-space(.)=" + titleLiteral + "]/ancestor::*[self::section or self::div][1]");
		final WebElement section = wait.until(ExpectedConditions.visibilityOfElementLocated(sectionLocator));
		return section.getText();
	}

	private void captureScreenshot(final String checkpoint, final boolean fullPagePreferred) {
		if (driver == null) {
			return;
		}
		try {
			final String fileName = String.format("%02d-%s.png", ++screenshotCounter, slug(checkpoint));
			final Path screenshotPath = evidenceDirectory.resolve(fileName);

			final File source;
			if (fullPagePreferred && driver instanceof HasFullPageScreenshot) {
				source = ((HasFullPageScreenshot) driver).getFullPageScreenshotAs(OutputType.FILE);
			} else {
				source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			}
			Files.copy(source.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception ignored) {
			// Screenshot capture should not hide the root cause of a failing step.
		}
	}

	private String slug(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String cleanError(final Throwable throwable) {
		final String message = throwable.getMessage();
		if (message == null || message.isBlank()) {
			return throwable.getClass().getSimpleName();
		}
		return message.replaceAll("\\s+", " ").trim();
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int index = 0; index < parts.length; index++) {
			builder.append("'").append(parts[index]).append("'");
			if (index < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private int parseInt(final String value, final int fallback) {
		try {
			return Integer.parseInt(value);
		} catch (final NumberFormatException numberFormatException) {
			return fallback;
		}
	}

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}
}
