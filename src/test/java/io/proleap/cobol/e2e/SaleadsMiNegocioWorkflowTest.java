package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
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
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private final LinkedHashMap<String, Boolean> stepResults = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> stepErrors = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> finalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private WebDriverWait longWait;
	private Path screenshotDir;
	private String loginUrl;
	private String googleAccount;
	private String appWindowHandle;

	@Before
	public void setUp() throws Exception {
		for (final String field : REPORT_FIELDS) {
			stepResults.put(field, Boolean.FALSE);
		}

		loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getenv("SALEADS_URL"),
				System.getenv("BASE_URL"));
		googleAccount = firstNonBlank(System.getenv("SALEADS_GOOGLE_ACCOUNT"), DEFAULT_GOOGLE_ACCOUNT);
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL) to run this environment-agnostic workflow test.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(firstNonBlank(System.getenv("SALEADS_HEADLESS"), "true"));
		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(20));
		longWait = new WebDriverWait(driver, Duration.ofSeconds(120));

		final String screenshotPath = firstNonBlank(System.getenv("SALEADS_SCREENSHOT_DIR"), "target/saleads-evidence");
		screenshotDir = Paths.get(screenshotPath);
		Files.createDirectories(screenshotDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", () -> stepValidateLegalDocument("Términos y Condiciones"));
		executeStep("Política de Privacidad", () -> stepValidateLegalDocument("Política de Privacidad"));

		final String finalReport = buildFinalReport();
		System.out.println(finalReport);

		final boolean allPassed = stepResults.values().stream().allMatch(Boolean.TRUE::equals);
		Assert.assertTrue("Mi Negocio workflow has failed validations.\n" + finalReport, allPassed);
	}

	private void stepLoginWithGoogle() throws IOException {
		driver.get(loginUrl);
		waitForUiSettled();

		clickGoogleLoginEntry();
		waitForUiSettled();
		selectGoogleAccountIfPrompted();
		waitForApplicationShell();

		assertVisibleText("Negocio");
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		ensureApplicationWindow();
		expandMiNegocioMenu();
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded-menu");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickAndWait(clickableTextLocator("Agregar Negocio"));
		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final By nombreNegocioInput = By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio' or @name='businessName']");
		wait.until(ExpectedConditions.visibilityOfElementLocated(nombreNegocioInput));
		driver.findElement(nombreNegocioInput).click();
		driver.findElement(nombreNegocioInput).clear();
		driver.findElement(nombreNegocioInput).sendKeys("Negocio Prueba Automatizacion");

		captureScreenshot("03-agregar-negocio-modal");
		clickAndWait(clickableTextLocator("Cancelar"));
		wait.until(ExpectedConditions.invisibilityOfElementLocated(visibleTextLocator("Crear Nuevo Negocio")));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioMenu();
		clickAndWait(clickableTextLocator("Administrar Negocios"));

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		captureScreenshot("04-administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() {
		final String pageText = getPageText();
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String visibleEmail = extractFirstEmail(pageText);
		Assert.assertNotNull("User email is not visible in Información General.", visibleEmail);

		final boolean hasLikelyUserName = hasLikelyUserNameNearEmail(pageText);
		Assert.assertTrue("User name is not visible in Información General.", hasLikelyUserName);
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		final String pageText = getPageText();
		Assert.assertTrue("Business list is not visible.", pageText.contains("Negocio") || pageText.contains("negocio"));
	}

	private void stepValidateLegalDocument(final String linkText) throws IOException {
		ensureApplicationWindow();
		final String currentUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickAndWait(clickableTextLocator(linkText));
		final String legalHandle = waitForNewWindowHandle(handlesBefore, Duration.ofSeconds(10));
		final boolean openedNewTab = legalHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(legalHandle);
		} else {
			longWait.until((ExpectedCondition<Boolean>) d -> !Objects.equals(d.getCurrentUrl(), currentUrl));
		}

		waitForUiSettled();
		assertVisibleText(linkText);

		final String pageText = getPageText();
		Assert.assertTrue("Legal content is not visible for " + linkText + ".",
				pageText != null && pageText.trim().replace(linkText, "").trim().length() > 80);
		finalUrls.put(linkText, driver.getCurrentUrl());
		captureScreenshot("legal-" + slugify(linkText));

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else {
			driver.navigate().back();
			waitForUiSettled();
		}
	}

	private void executeStep(final String stepName, final StepAction stepAction) {
		try {
			stepAction.run();
			stepResults.put(stepName, Boolean.TRUE);
		} catch (final Throwable throwable) {
			stepResults.put(stepName, Boolean.FALSE);
			stepErrors.put(stepName, throwable.getMessage());
			try {
				captureScreenshot("failed-" + slugify(stepName));
			} catch (final IOException ignored) {
				// avoid masking original assertion
			}
		}
	}

	private void clickGoogleLoginEntry() {
		final List<By> candidates = Arrays.asList(
				By.xpath("//button[contains(normalize-space(),'Google')]"),
				By.xpath("//a[contains(normalize-space(),'Google')]"),
				By.xpath("//*[(@role='button' or self::button) and contains(normalize-space(),'Google')]"),
				By.xpath("//*[contains(normalize-space(),'Sign in with Google') or contains(normalize-space(),'Iniciar sesión con Google') or contains(normalize-space(),'Continuar con Google')]"));
		for (final By candidate : candidates) {
			if (isVisible(candidate, 3)) {
				clickAndWait(candidate);
				return;
			}
		}

		Assert.fail("Could not locate a login entry point with visible text containing Google.");
	}

	private void selectGoogleAccountIfPrompted() {
		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() > 1) {
			for (final String handle : handles) {
				driver.switchTo().window(handle);
				if (!isVisible(visibleTextLocator("Google"), 1)) {
					continue;
				}

				if (isVisible(visibleTextLocator(googleAccount), 5)) {
					clickAndWait(visibleTextLocator(googleAccount));
					return;
				}
			}
		}

		if (isVisible(visibleTextLocator(googleAccount), 5)) {
			clickAndWait(visibleTextLocator(googleAccount));
		}
	}

	private void waitForApplicationShell() {
		longWait.until((ExpectedCondition<Boolean>) d -> {
			for (final String handle : d.getWindowHandles()) {
				d.switchTo().window(handle);
				if (isVisible(visibleTextLocator("Negocio"), 2)) {
					appWindowHandle = handle;
					return true;
				}
			}
			return false;
		});

		driver.switchTo().window(appWindowHandle);
		waitForUiSettled();
	}

	private void expandMiNegocioMenu() {
		if (!isVisible(visibleTextLocator("Mi Negocio"), 4)) {
			clickAndWait(clickableTextLocator("Negocio"));
		}
		clickAndWait(clickableTextLocator("Mi Negocio"));
		wait.until(ExpectedConditions.visibilityOfElementLocated(visibleTextLocator("Agregar Negocio")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(visibleTextLocator("Administrar Negocios")));
	}

	private void clickAndWait(final By locator) {
		wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
		waitForUiSettled();
	}

	private void assertVisibleText(final String visibleText) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(visibleTextLocator(visibleText)));
	}

	private String waitForNewWindowHandle(final Set<String> handlesBefore, final Duration timeout) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() <= deadline) {
			final Set<String> currentHandles = driver.getWindowHandles();
			for (final String handle : currentHandles) {
				if (!handlesBefore.contains(handle)) {
					return handle;
				}
			}
			sleep(250);
		}
		return null;
	}

	private void ensureApplicationWindow() {
		if (appWindowHandle == null) {
			appWindowHandle = driver.getWindowHandle();
		}
		driver.switchTo().window(appWindowHandle);
		waitForUiSettled();
	}

	private String getPageText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private void waitForUiSettled() {
		wait.until((ExpectedCondition<Boolean>) d -> {
			final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "interactive".equals(readyState) || "complete".equals(readyState);
		});
		sleep(400);
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
		final Path screenshotPath = screenshotDir.resolve(timestamp + "_" + slugify(checkpointName) + ".png");
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), screenshotPath);
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder("Final Report - SaleADS Mi Negocio Workflow\n");
		for (final Map.Entry<String, Boolean> entry : stepResults.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL");
			final String error = stepErrors.get(entry.getKey());
			if (error != null && !error.isBlank()) {
				builder.append(" (").append(error).append(")");
			}
			builder.append("\n");
		}
		if (!finalUrls.isEmpty()) {
			builder.append("Captured legal URLs:\n");
			for (final Map.Entry<String, String> legalUrl : finalUrls.entrySet()) {
				builder.append("- ").append(legalUrl.getKey()).append(": ").append(legalUrl.getValue()).append("\n");
			}
		}
		return builder.toString();
	}

	private boolean isVisible(final By locator, final int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private String extractFirstEmail(final String pageText) {
		final Matcher matcher = EMAIL_PATTERN.matcher(pageText);
		if (matcher.find()) {
			return matcher.group();
		}
		return null;
	}

	private boolean hasLikelyUserNameNearEmail(final String pageText) {
		final String[] lines = pageText.split("\\R+");
		for (int i = 0; i < lines.length; i++) {
			final String line = lines[i].trim();
			if (!EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			for (int j = Math.max(0, i - 4); j < i; j++) {
				final String candidate = lines[j].trim();
				if (candidate.length() < 3 || candidate.contains("@")) {
					continue;
				}
				if (isStaticLabel(candidate)) {
					continue;
				}
				return true;
			}
		}
		return false;
	}

	private boolean isStaticLabel(final String line) {
		final String normalized = line.toUpperCase();
		return normalized.equals("INFORMACIÓN GENERAL") || normalized.equals("BUSINESS PLAN")
				|| normalized.equals("CAMBIAR PLAN") || normalized.equals("DETALLES DE LA CUENTA")
				|| normalized.equals("TUS NEGOCIOS") || normalized.equals("SECCIÓN LEGAL")
				|| normalized.equals("CUENTA CREADA") || normalized.equals("ESTADO ACTIVO")
				|| normalized.equals("IDIOMA SELECCIONADO");
	}

	private By visibleTextLocator(final String text) {
		return By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + "]");
	}

	private By clickableTextLocator(final String text) {
		return By.xpath("//button[normalize-space()=" + toXPathLiteral(text) + "]"
				+ " | //a[normalize-space()=" + toXPathLiteral(text) + "]"
				+ " | //*[@role='button' and normalize-space()=" + toXPathLiteral(text) + "]"
				+ " | //*[(self::span or self::div) and normalize-space()=" + toXPathLiteral(text) + "]");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder expression = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part = String.valueOf(chars[i]);
			if (i > 0) {
				expression.append(",");
			}
			if ("'".equals(part)) {
				expression.append("\"'\"");
			} else if ("\"".equals(part)) {
				expression.append("'\"'");
			} else {
				expression.append("'").append(part).append("'");
			}
		}
		expression.append(")");
		return expression.toString();
	}

	private String slugify(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(interruptedException);
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
