package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);

	private final Map<String, Boolean> reportStatus = new LinkedHashMap<>();
	private final Map<String, String> reportDetails = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Duration waitTimeout;
	private Path evidenceDirectory;
	private String applicationHandle;

	@Before
	public void setUp() throws Exception {
		evidenceDirectory = Paths.get("target", "saleads-mi-negocio-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDirectory);

		driver = createDriver();
		waitTimeout = getDurationFromEnv("SALEADS_TIMEOUT_SECONDS", DEFAULT_WAIT);
		wait = new WebDriverWait(driver, waitTimeout);
		driver.manage().window().setSize(new Dimension(1920, 1080));

		final String loginUrl = readEnv("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiToSettle();
		}

		applicationHandle = driver.getWindowHandle();
		final String currentUrl = normalize(driver.getCurrentUrl());
		if (loginUrl == null && (currentUrl.isBlank() || "about:blank".equals(currentUrl) || "data:,".equals(currentUrl))) {
			throw new IllegalStateException(
					"No login page found. Provide SALEADS_LOGIN_URL or start the browser already on the SaleADS login page.");
		}
	}

	@After
	public void tearDown() throws Exception {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalLink(
				"Términos y Condiciones",
				new String[] { "Terminos y Condiciones" },
				"Términos y Condiciones",
				"08-terminos-y-condiciones",
				"Términos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalLink(
				"Política de Privacidad",
				new String[] { "Politica de Privacidad" },
				"Política de Privacidad",
				"09-politica-de-privacidad",
				"Política de Privacidad"));

		final boolean allPassed = REPORT_FIELDS.stream().allMatch(field -> Boolean.TRUE.equals(reportStatus.get(field)));
		assertTrue("One or more validations failed. Check " + evidenceDirectory.resolve("final-report.txt"), allPassed);
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleText(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Iniciar sesion con Google",
				"Ingresar con Google",
				"Continuar con Google",
				"Google");

		selectGoogleAccountIfPrompted();
		waitForMainApplication();

		assertAnyElementVisible(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[@role='navigation']"));
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		if (!isAnyTextVisible("Agregar Negocio", "Administrar Negocios")) {
			clickIfVisibleText(Duration.ofSeconds(5), "Negocio");
			clickIfVisibleText(Duration.ofSeconds(5), "Mi Negocio");
		}

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		assertTextVisible("Crear Nuevo Negocio");
		assertAnyElementVisible(
				By.xpath("//label[contains(normalize-space(), " + xpathLiteral("Nombre del Negocio") + ")]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[@aria-label='Nombre del Negocio']"));
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		captureScreenshot("03-agregar-negocio-modal");

		final WebElement input = firstVisibleElement(
				By.xpath("//label[contains(normalize-space(), " + xpathLiteral("Nombre del Negocio")
						+ ")]/following::input[1]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[@aria-label='Nombre del Negocio']"));
		if (input != null) {
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatizacion");
		}

		clickIfVisibleText(Duration.ofSeconds(5), "Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral("Crear Nuevo Negocio") + ")]")));
		waitForUiToSettle();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickIfVisibleText(Duration.ofSeconds(5), "Mi Negocio");
			clickIfVisibleText(Duration.ofSeconds(5), "Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal", "Seccion Legal");
		captureFullPageScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		assertTextVisible("Información General");
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
		assertEmailVisible();
		assertPossibleNameVisible();
	}

	private void stepValidateDetallesCuenta() throws Exception {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() throws Exception {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertBusinessListVisible();
	}

	private void stepValidateLegalLink(
			final String linkText,
			final String[] fallbackLinkTexts,
			final String headingText,
			final String screenshotName,
			final String urlReportKey) throws Exception {

		final String appHandleBefore = driver.getWindowHandle();
		final String previousUrl = normalize(driver.getCurrentUrl());
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		final List<String> linkCandidates = new ArrayList<>();
		linkCandidates.add(linkText);
		linkCandidates.addAll(Arrays.asList(fallbackLinkTexts));
		clickByVisibleText(linkCandidates.toArray(new String[0]));

		waitForNavigationOrNewTab(previousUrl, handlesBefore);

		boolean openedNewTab = false;
		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				openedNewTab = true;
				break;
			}
		}

		assertTextVisible(headingText);
		assertLegalContentVisible();
		captureScreenshot(screenshotName);
		capturedUrls.put(urlReportKey, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandleBefore);
			waitForUiToSettle();
		} else {
			driver.navigate().back();
			waitForUiToSettle();
		}

		applicationHandle = appHandleBefore;
		waitForAccountPageContext();
	}

	private WebDriver createDriver() throws Exception {
		final ChromeOptions chromeOptions = new ChromeOptions();
		chromeOptions.addArguments("--window-size=1920,1080");
		chromeOptions.addArguments("--disable-dev-shm-usage");
		chromeOptions.addArguments("--no-sandbox");

		final String headless = normalize(readEnv("SALEADS_HEADLESS"));
		if (headless.isBlank() || Boolean.parseBoolean(headless)) {
			chromeOptions.addArguments("--headless=new");
		}

		final String remoteUrl = readEnv("SELENIUM_REMOTE_URL");
		if (remoteUrl != null && !remoteUrl.isBlank()) {
			return new RemoteWebDriver(new java.net.URI(remoteUrl).toURL(), chromeOptions);
		}

		return new ChromeDriver(chromeOptions);
	}

	private void runStep(final String reportField, final StepAction stepAction) {
		try {
			stepAction.run();
			reportStatus.put(reportField, Boolean.TRUE);
		} catch (final Throwable ex) {
			reportStatus.put(reportField, Boolean.FALSE);
			reportDetails.put(reportField, simplifyError(ex));
			captureScreenshotQuietly("failure-" + toSlug(reportField));
		}
	}

	private void waitForMainApplication() {
		final WebDriverWait longWait = new WebDriverWait(driver, Duration.ofSeconds(120));
		longWait.until(ignored -> {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (isMainAppVisible()) {
					applicationHandle = handle;
					return true;
				}
			}
			return false;
		});
		driver.switchTo().window(applicationHandle);
		waitForUiToSettle();
	}

	private boolean isMainAppVisible() {
		return isAnyElementVisible(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[@role='navigation']"))
				&& isAnyTextVisible("Negocio", "Mi Negocio");
	}

	private void selectGoogleAccountIfPrompted() {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
		while (System.currentTimeMillis() < deadline) {
			final Set<String> handles = driver.getWindowHandles();
			for (final String handle : handles) {
				driver.switchTo().window(handle);
				if (clickIfVisibleText(Duration.ofSeconds(1), GOOGLE_ACCOUNT)) {
					waitForUiToSettle();
					return;
				}
			}
			sleep(400);
		}
		driver.switchTo().window(applicationHandle);
	}

	private void waitForNavigationOrNewTab(final String previousUrl, final Set<String> handlesBefore) {
		final WebDriverWait legalWait = new WebDriverWait(driver, Duration.ofSeconds(40));
		legalWait.until(ignored -> {
			final Set<String> currentHandles = driver.getWindowHandles();
			if (currentHandles.size() > handlesBefore.size()) {
				return true;
			}
			return !normalize(driver.getCurrentUrl()).equals(previousUrl);
		});
		waitForUiToSettle();
	}

	private void waitForAccountPageContext() {
		final WebDriverWait pageWait = new WebDriverWait(driver, Duration.ofSeconds(30));
		pageWait.until(ignored -> isAnyTextVisible("Información General", "Detalles de la Cuenta", "Tus Negocios"));
	}

	private void clickByVisibleText(final String... candidates) {
		final WebElement clickable = waitForClickableText(candidates);
		try {
			clickable.click();
		} catch (final Exception clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
		}
		waitForUiToSettle();
	}

	private boolean clickIfVisibleText(final Duration timeout, final String... candidates) {
		try {
			final WebElement clickable = waitForClickableText(timeout, candidates);
			try {
				clickable.click();
			} catch (final Exception clickFailure) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
			}
			waitForUiToSettle();
			return true;
		} catch (final RuntimeException ex) {
			return false;
		}
	}

	private WebElement waitForClickableText(final String... candidates) {
		return waitForClickableText(waitTimeout, candidates);
	}

	private WebElement waitForClickableText(final Duration timeout, final String... candidates) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		return localWait.until(ignored -> {
			for (final String candidate : candidates) {
				final WebElement raw = findVisibleTextElement(candidate);
				if (raw == null) {
					continue;
				}
				final WebElement clickable = resolveClickableElement(raw);
				if (clickable != null && clickable.isDisplayed()) {
					return clickable;
				}
			}
			return null;
		});
	}

	private WebElement findVisibleTextElement(final String text) {
		final String literal = xpathLiteral(text);
		final List<By> candidateLocators = Arrays.asList(
				By.xpath("//*[self::button or self::a or @role='button' or @role='menuitem'][normalize-space()="
						+ literal + "]"),
				By.xpath("//*[self::button or self::a or @role='button' or @role='menuitem'][contains(normalize-space(),"
						+ literal + ")]"),
				By.xpath("//*[normalize-space()=" + literal + "]"),
				By.xpath("//*[contains(normalize-space()," + literal + ")]"));

		for (final By locator : candidateLocators) {
			final List<WebElement> matches = driver.findElements(locator);
			for (final WebElement element : matches) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		return null;
	}

	private WebElement resolveClickableElement(final WebElement element) {
		final String tagName = normalize(element.getTagName()).toLowerCase(Locale.ROOT);
		if ("button".equals(tagName) || "a".equals(tagName)) {
			return element;
		}
		if ("button".equals(normalize(element.getAttribute("role")).toLowerCase(Locale.ROOT))
				|| "menuitem".equals(normalize(element.getAttribute("role")).toLowerCase(Locale.ROOT))) {
			return element;
		}
		try {
			return element.findElement(By.xpath("./ancestor::*[self::button or self::a or @role='button' or @role='menuitem'][1]"));
		} catch (final NoSuchElementException notFound) {
			return element;
		}
	}

	private void assertTextVisible(final String... textOptions) {
		if (!isAnyTextVisible(textOptions)) {
			throw new AssertionError("Expected text was not visible: " + Arrays.toString(textOptions));
		}
	}

	private boolean isAnyTextVisible(final String... textOptions) {
		for (final String text : textOptions) {
			if (findVisibleTextElement(text) != null) {
				return true;
			}
		}
		return false;
	}

	private void assertAnyElementVisible(final By... locators) {
		if (!isAnyElementVisible(locators)) {
			throw new AssertionError("Expected one of the locators to be visible.");
		}
	}

	private boolean isAnyElementVisible(final By... locators) {
		for (final By locator : locators) {
			final List<WebElement> matches = driver.findElements(locator);
			for (final WebElement match : matches) {
				if (match.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private WebElement firstVisibleElement(final By... locators) {
		for (final By locator : locators) {
			for (final WebElement match : driver.findElements(locator)) {
				if (match.isDisplayed()) {
					return match;
				}
			}
		}
		return null;
	}

	private void assertEmailVisible() {
		final String bodyText = normalize(driver.findElement(By.tagName("body")).getText());
		final Matcher matcher = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(bodyText);
		if (!matcher.find()) {
			throw new AssertionError("No visible email found in account information.");
		}
	}

	private void assertPossibleNameVisible() {
		final String bodyText = normalize(driver.findElement(By.tagName("body")).getText());
		final String[] lines = bodyText.split("\\R");
		for (final String line : lines) {
			final String candidate = line.trim();
			if (candidate.isEmpty()) {
				continue;
			}
			if (candidate.contains("@") || candidate.equalsIgnoreCase("BUSINESS PLAN")
					|| candidate.equalsIgnoreCase("Cambiar Plan")
					|| candidate.equalsIgnoreCase("Información General")
					|| candidate.equalsIgnoreCase("Detalles de la Cuenta")
					|| candidate.equalsIgnoreCase("Tus Negocios")) {
				continue;
			}
			final String[] tokens = candidate.split("\\s+");
			if (tokens.length >= 2) {
				return;
			}
		}
		throw new AssertionError("No probable user name line was detected.");
	}

	private void assertBusinessListVisible() {
		final By[] listLocators = new By[] {
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral("Tus Negocios")
						+ ")]/following::*[self::ul or self::ol or self::table][1]"),
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral("Tus Negocios")
						+ ")]/following::*[self::li or self::tr][1]"),
				By.xpath("//table//tr[td]"),
				By.xpath("//ul/li") };
		assertAnyElementVisible(listLocators);
	}

	private void assertLegalContentVisible() {
		final String bodyText = normalize(driver.findElement(By.tagName("body")).getText());
		if (bodyText.length() < 200) {
			throw new AssertionError("Legal content looks too short to be valid.");
		}
	}

	private void waitForUiToSettle() {
		try {
			wait.until(ignored -> "complete".equals(
					((JavascriptExecutor) driver).executeScript("return document.readyState")));
		} catch (final TimeoutException timeoutException) {
			// Continue even if a SPA never returns complete consistently.
		}
		sleep(500);
	}

	private void captureScreenshot(final String fileNamePrefix) throws IOException {
		final Path screenshotPath = evidenceDirectory.resolve(fileNamePrefix + ".png");
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshotPath, screenshot);
	}

	private void captureFullPageScreenshot(final String fileNamePrefix) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			final Long scrollWidth = (Long) js.executeScript(
					"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);");
			final Long scrollHeight = (Long) js.executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");

			final int targetWidth = Math.max(1280, scrollWidth == null ? 1280 : scrollWidth.intValue());
			final int targetHeight = Math.min(3500, Math.max(900, scrollHeight == null ? 900 : scrollHeight.intValue()));
			driver.manage().window().setSize(new Dimension(targetWidth, targetHeight));
			waitForUiToSettle();
			captureScreenshot(fileNamePrefix);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToSettle();
		}
	}

	private void captureScreenshotQuietly(final String fileNamePrefix) {
		try {
			captureScreenshot(fileNamePrefix);
		} catch (final Exception ignored) {
			// Best effort evidence on failures.
		}
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDirectory == null) {
			return;
		}

		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("Evidence directory: " + evidenceDirectory.toAbsolutePath());
		lines.add("");
		lines.add("Final Report");
		for (final String field : REPORT_FIELDS) {
			final boolean pass = Boolean.TRUE.equals(reportStatus.get(field));
			final String detail = reportDetails.get(field);
			final String detailSuffix = (detail == null || detail.isBlank()) ? "" : " (" + detail + ")";
			lines.add("- " + field + ": " + (pass ? "PASS" : "FAIL") + detailSuffix);
		}

		lines.add("");
		lines.add("Captured URLs");
		lines.add("- Términos y Condiciones: " + capturedUrls.getOrDefault("Términos y Condiciones", "N/A"));
		lines.add("- Política de Privacidad: " + capturedUrls.getOrDefault("Política de Privacidad", "N/A"));

		Files.write(evidenceDirectory.resolve("final-report.txt"), lines);
	}

	private Duration getDurationFromEnv(final String key, final Duration defaultValue) {
		final String value = readEnv(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Duration.ofSeconds(Long.parseLong(value.trim()));
		} catch (final NumberFormatException ex) {
			return defaultValue;
		}
	}

	private String readEnv(final String key) {
		return System.getenv(key);
	}

	private String simplifyError(final Throwable ex) {
		final String message = normalize(ex.getMessage());
		if (!message.isBlank()) {
			return message;
		}
		return ex.getClass().getSimpleName();
	}

	private String normalize(final String value) {
		return value == null ? "" : value.trim();
	}

	private String toSlug(final String text) {
		return normalize(text)
				.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-|-$)", "");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder out = new StringBuilder("concat(");
		for (int i = 0; i < value.length(); i++) {
			final String character = String.valueOf(value.charAt(i));
			if ("'".equals(character)) {
				out.append("\"'\"");
			} else if ("\"".equals(character)) {
				out.append("'\"'");
			} else {
				out.append("'").append(character).append("'");
			}
			if (i < value.length() - 1) {
				out.append(",");
			}
		}
		out.append(")");
		return out.toString();
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
