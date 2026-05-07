package io.proleap.saleads.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
	private static final Set<String> STATIC_INFO_LABELS = Set.of(
			"INFORMACION GENERAL",
			"INFORMACIÓN GENERAL",
			"BUSINESS PLAN",
			"CAMBIAR PLAN",
			"PLAN",
			"EMAIL",
			"CORREO",
			"NOMBRE");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String googleAccountEmail;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this E2E workflow.",
				Boolean.parseBoolean(env("SALEADS_E2E_ENABLED", "false")));

		final long timeoutSeconds = Long.parseLong(env("SALEADS_TIMEOUT_SECONDS", "30"));
		driver = createDriver(env("SALEADS_BROWSER", "chrome"), Boolean.parseBoolean(env("SALEADS_HEADLESS", "true")));
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		driver.manage().window().setSize(new Dimension(1920, 1080));
		googleAccountEmail = env("SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com");

		final String runId = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
		evidenceDir = Path.of("target", "evidence", TEST_NAME, runId);
		Files.createDirectories(evidenceDir);

		final String startUrl = firstNonBlank(
				System.getenv("SALEADS_START_URL"),
				System.getenv("SALEADS_BASE_URL"),
				System.getenv("SALEADS_URL"));
		if (startUrl == null) {
			throw new IllegalStateException("Set SALEADS_START_URL (or SALEADS_BASE_URL / SALEADS_URL) for the login page.");
		}

		driver.get(startUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		writeReportToFile();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalDocument("Términos y Condiciones", "Términos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalDocument("Política de Privacidad", "Política de Privacidad"));

		printFinalReport();
		final boolean allPassed = stepResults.values().stream().allMatch(StepResult::passed);
		Assert.assertTrue("At least one required validation failed. Check final report and screenshots.", allPassed);
	}

	private void stepLoginWithGoogle() {
		clickByVisibleText(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Inicia sesión con Google",
				"Continuar con Google",
				"Ingresar con Google",
				"Login with Google");
		selectGoogleAccountIfVisible();

		waitUntilAnyVisible(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(normalize-space(.), 'Negocio')]"),
				By.xpath("//*[contains(normalize-space(.), 'Mi Negocio')]"));

		Assert.assertTrue("Left sidebar navigation must be visible after login.", isAnyVisible(
				By.xpath("//aside"),
				By.xpath("//nav[.//*[contains(normalize-space(.), 'Negocio')]]"),
				By.xpath("//*[contains(@class,'sidebar')]")));

		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() {
		clickByVisibleText("Mi Negocio");

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		takeScreenshot("03_agregar_negocio_modal");

		final WebElement nombreInput = findFirstVisible(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[@aria-label='Nombre del Negocio']"),
				By.xpath("//*[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
		if (nombreInput != null) {
			nombreInput.click();
			nombreInput.clear();
			nombreInput.sendKeys("Negocio Prueba Automatización");
			waitForUiToLoad();
		}

		if (isTextVisible("Cancelar", Duration.ofSeconds(5))) {
			clickByVisibleText("Cancelar");
		}
	}

	private void stepOpenAdministrarNegocios() {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(3))) {
			clickByVisibleText("Mi Negocio");
		}
		clickByVisibleText("Administrar Negocios");

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		takeScreenshot("04_administrar_negocios_view");
	}

	private void stepValidateInformacionGeneral() {
		final String sectionText = getSectionTextByHeading("Información General");
		Assert.assertTrue("Expected user email in 'Información General'.", EMAIL_PATTERN.matcher(sectionText).find());
		Assert.assertTrue("Expected a user name-like value in 'Información General'.", containsNameLikeText(sectionText));
		Assert.assertTrue("Expected 'BUSINESS PLAN' in 'Información General'.", containsIgnoringCase(sectionText, "BUSINESS PLAN"));
		Assert.assertTrue("Expected 'Cambiar Plan' button text in 'Información General'.",
				containsIgnoringCase(sectionText, "Cambiar Plan"));
	}

	private void stepValidateDetallesCuenta() {
		final String sectionText = getSectionTextByHeading("Detalles de la Cuenta");
		Assert.assertTrue("Expected 'Cuenta creada'.", containsIgnoringCase(sectionText, "Cuenta creada"));
		Assert.assertTrue("Expected 'Estado activo'.", containsIgnoringCase(sectionText, "Estado activo"));
		Assert.assertTrue("Expected 'Idioma seleccionado'.", containsIgnoringCase(sectionText, "Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final String sectionText = section.getText();
		Assert.assertTrue("Expected 'Agregar Negocio' in 'Tus Negocios'.", containsIgnoringCase(sectionText, "Agregar Negocio"));
		Assert.assertTrue("Expected 'Tienes 2 de 3 negocios' in 'Tus Negocios'.",
				containsIgnoringCase(sectionText, "Tienes 2 de 3 negocios"));

		final boolean hasListLikeItems = !section.findElements(By.xpath(".//li|.//tr|.//article|.//section")).isEmpty()
				|| sectionText.split("\\R").length >= 4;
		Assert.assertTrue("Business list should be visible in 'Tus Negocios'.", hasListLikeItems);
	}

	private void stepValidateLegalDocument(final String linkText, final String expectedHeading) {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);
		final String maybeNewHandle = waitForNewWindowHandle(handlesBefore, Duration.ofSeconds(8));
		if (maybeNewHandle != null) {
			driver.switchTo().window(maybeNewHandle);
			waitForUiToLoad();
		}

		assertVisibleText(expectedHeading);
		assertLegalContentVisible();
		takeScreenshot("05_legal_" + sanitizeFileName(linkText));
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (maybeNewHandle != null) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void assertLegalContentVisible() {
		final WebElement content = findFirstVisible(
				By.xpath("//main"),
				By.xpath("//article"),
				By.xpath("//body"));
		Assert.assertNotNull("Expected legal content container.", content);
		Assert.assertTrue("Expected legal content text.", content.getText().trim().length() > 120);
	}

	private void runStep(final String stepName, final Runnable stepBody) {
		try {
			stepBody.run();
			stepResults.put(stepName, new StepResult(true, "PASS"));
		} catch (final Throwable t) {
			stepResults.put(stepName, new StepResult(false, truncate(t.getMessage(), 280)));
		}
	}

	private void printFinalReport() {
		System.out.println("\n================ FINAL REPORT: " + TEST_NAME + " ================");
		stepResults.forEach((name, result) -> {
			final String status = result.passed() ? "PASS" : "FAIL";
			System.out.println(name + ": " + status + " - " + result.details());
		});
		if (!legalUrls.isEmpty()) {
			System.out.println("Final URLs:");
			legalUrls.forEach((name, url) -> System.out.println(" - " + name + ": " + url));
		}
		System.out.println("Evidence folder: " + evidenceDir.toAbsolutePath());
		System.out.println("==============================================================\n");
	}

	private void writeReportToFile() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder content = new StringBuilder();
		content.append("test: ").append(TEST_NAME).append('\n');
		content.append("timestamp: ").append(Instant.now()).append('\n');
		content.append('\n');
		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			content.append(entry.getKey())
					.append(": ")
					.append(entry.getValue().passed() ? "PASS" : "FAIL")
					.append(" - ")
					.append(entry.getValue().details())
					.append('\n');
		}
		if (!legalUrls.isEmpty()) {
			content.append('\n').append("final_urls:\n");
			legalUrls.forEach((name, url) -> content.append("- ").append(name).append(": ").append(url).append('\n'));
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), content.toString());
	}

	private void takeScreenshot(final String checkpointName) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		try {
			final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path target = evidenceDir.resolve(sanitizeFileName(checkpointName) + ".png");
			Files.copy(screenshotFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException ignored) {
		}
	}

	private WebDriver createDriver(final String browserRaw, final boolean headless) {
		final String browser = browserRaw.toLowerCase(Locale.ROOT);
		switch (browser) {
			case "firefox": {
				final FirefoxOptions options = new FirefoxOptions();
				if (headless) {
					options.addArguments("-headless");
				}
				return new FirefoxDriver(options);
			}
			case "edge": {
				final EdgeOptions options = new EdgeOptions();
				if (headless) {
					options.addArguments("--headless=new");
				}
				return new EdgeDriver(options);
			}
			case "chrome":
			default: {
				final ChromeOptions options = new ChromeOptions();
				options.addArguments("--window-size=1920,1080");
				options.addArguments("--disable-gpu");
				options.addArguments("--no-sandbox");
				options.addArguments("--disable-dev-shm-usage");
				if (headless) {
					options.addArguments("--headless=new");
				}
				return new ChromeDriver(options);
			}
		}
	}

	private void clickByVisibleText(final String... texts) {
		for (final String text : texts) {
			final WebElement target = findClickableByVisibleText(text, Duration.ofSeconds(8));
			if (target != null) {
				clickElement(target);
				return;
			}
		}
		throw new AssertionError("Could not find clickable element with texts: " + Arrays.toString(texts));
	}

	private WebElement findClickableByVisibleText(final String text, final Duration timeout) {
		final List<By> locators = List.of(
				By.xpath("//button[normalize-space()=" + toXPathLiteral(text) + "]"),
				By.xpath("//a[normalize-space()=" + toXPathLiteral(text) + "]"),
				By.xpath("//*[@role='button' and normalize-space()=" + toXPathLiteral(text) + "]"),
				By.xpath("//*[self::span or self::div][normalize-space()=" + toXPathLiteral(text)
						+ "]/ancestor::*[self::button or self::a or @role='button'][1]"),
				By.xpath("//*[self::span or self::div][contains(normalize-space(), " + toXPathLiteral(text)
						+ ")]/ancestor::*[self::button or self::a or @role='button'][1]"),
				By.xpath("//button[contains(normalize-space(), " + toXPathLiteral(text) + ")]"),
				By.xpath("//a[contains(normalize-space(), " + toXPathLiteral(text) + ")]"));

		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		for (final By by : locators) {
			try {
				return localWait.until(d -> d.findElements(by).stream()
						.filter(WebElement::isDisplayed)
						.findFirst()
						.orElse(null));
			} catch (final TimeoutException ignored) {
			}
		}
		return null;
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(d -> element.isDisplayed() && element.isEnabled());
			element.click();
		} catch (final Exception clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private WebElement findByVisibleText(final String text, final Duration timeout) {
		final List<By> locators = List.of(
				By.xpath("//button[normalize-space()=" + toXPathLiteral(text) + "]"),
				By.xpath("//a[normalize-space()=" + toXPathLiteral(text) + "]"),
				By.xpath("//*[@role='button' and normalize-space()=" + toXPathLiteral(text) + "]"),
				By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + "]"),
				By.xpath("//*[contains(normalize-space(), " + toXPathLiteral(text) + ")]"));

		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		for (final By by : locators) {
			try {
				return localWait.until(d -> d.findElements(by).stream()
						.filter(WebElement::isDisplayed)
						.findFirst()
						.orElse(null));
			} catch (final TimeoutException ignored) {
			}
		}
		return null;
	}

	private void waitForUiToLoad() {
		final ExpectedCondition<Boolean> readyState = d -> "complete".equals(
				((JavascriptExecutor) d).executeScript("return document.readyState"));
		wait.until(readyState);
		waitForLoadingOverlays();
		sleep(400);
	}

	private void waitForLoadingOverlays() {
		final List<By> overlays = List.of(
				By.cssSelector("[aria-busy='true']"),
				By.cssSelector(".loading"),
				By.cssSelector(".spinner"),
				By.cssSelector(".ant-spin-spinning"),
				By.cssSelector("mat-progress-spinner"));
		for (final By overlay : overlays) {
			try {
				new WebDriverWait(driver, Duration.ofSeconds(2))
						.until(d -> d.findElements(overlay).stream().noneMatch(WebElement::isDisplayed));
			} catch (final TimeoutException ignored) {
			}
		}
	}

	private void selectGoogleAccountIfVisible() {
		if (isTextVisible(googleAccountEmail, Duration.ofSeconds(8))) {
			clickByVisibleText(googleAccountEmail);
			return;
		}

		final WebElement accountRow = findFirstVisible(
				By.xpath("//*[contains(@data-identifier, " + toXPathLiteral(googleAccountEmail) + ")]"),
				By.xpath("//*[contains(normalize-space(), " + toXPathLiteral(googleAccountEmail) + ")]"));
		if (accountRow != null) {
			clickElement(accountRow);
		}
	}

	private void assertVisibleText(final String text) {
		Assert.assertTrue("Expected visible text: " + text, isTextVisible(text, Duration.ofSeconds(15)));
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		return findByVisibleText(text, timeout) != null;
	}

	private boolean isAnyVisible(final By... locators) {
		return Arrays.stream(locators).anyMatch(this::isVisible);
	}

	private boolean isVisible(final By locator) {
		try {
			return driver.findElements(locator).stream().anyMatch(WebElement::isDisplayed);
		} catch (final Exception e) {
			return false;
		}
	}

	private void waitUntilAnyVisible(final By... locators) {
		wait.until(d -> isAnyVisible(locators));
	}

	private WebElement findFirstVisible(final By... locators) {
		for (final By locator : locators) {
			try {
				final WebElement match = wait.until(d -> d.findElements(locator).stream()
						.filter(WebElement::isDisplayed)
						.findFirst()
						.orElse(null));
				if (match != null) {
					return match;
				}
			} catch (final TimeoutException ignored) {
			}
		}
		return null;
	}

	private WebElement findSectionByHeading(final String heading) {
		final WebElement headingElement = findByVisibleText(heading, Duration.ofSeconds(15));
		if (headingElement == null) {
			throw new AssertionError("Could not find heading: " + heading);
		}

		final JavascriptExecutor js = (JavascriptExecutor) driver;
		final WebElement container = (WebElement) js.executeScript(
				"let e = arguments[0];"
						+ "while (e && e !== document.body) {"
						+ "  if (['SECTION','ARTICLE','MAIN','DIV'].includes(e.tagName) && e.innerText && e.innerText.length > 40) return e;"
						+ "  e = e.parentElement;"
						+ "}"
						+ "return document.body;",
				headingElement);
		return container;
	}

	private String getSectionTextByHeading(final String heading) {
		final WebElement section = findSectionByHeading(heading);
		return section.getText();
	}

	private boolean containsNameLikeText(final String sectionText) {
		final List<String> lines = Arrays.stream(sectionText.split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.collect(Collectors.toList());

		for (final String line : lines) {
			final String upper = line.toUpperCase(Locale.ROOT);
			if (STATIC_INFO_LABELS.contains(upper)) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (!line.matches(".*[A-Za-z].*")) {
				continue;
			}
			if (line.matches(".*\\d.*")) {
				continue;
			}
			if (line.length() < 3) {
				continue;
			}
			return true;
		}
		return false;
	}

	private String waitForNewWindowHandle(final Set<String> previousHandles, final Duration timeout) {
		final WebDriverWait newWindowWait = new WebDriverWait(driver, timeout);
		try {
			return newWindowWait.until(d -> {
				final Set<String> current = d.getWindowHandles();
				final List<String> newHandles = current.stream()
						.filter(h -> !previousHandles.contains(h))
						.collect(Collectors.toList());
				return newHandles.isEmpty() ? null : newHandles.get(0);
			});
		} catch (final TimeoutException ignored) {
			return null;
		}
	}

	private static boolean containsIgnoringCase(final String text, final String expected) {
		return text.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
	}

	private static String truncate(final String input, final int maxLength) {
		if (input == null || input.length() <= maxLength) {
			return input == null ? "No details." : input;
		}
		return input.substring(0, maxLength) + "...";
	}

	private static String sanitizeFileName(final String raw) {
		return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private static String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value.trim();
	}

	private static String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final List<String> parts = new ArrayList<>();
		for (final String part : value.split("'")) {
			parts.add("'" + part + "'");
		}
		return "concat(" + String.join(",\"'\",", parts) + ")";
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private record StepResult(boolean passed, String details) {
	}
}
