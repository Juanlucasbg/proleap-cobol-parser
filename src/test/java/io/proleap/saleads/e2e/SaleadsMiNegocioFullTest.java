package io.proleap.saleads.e2e;

import org.junit.Assert;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(6);

	private static final List<String> REPORT_FIELDS = List.of(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private final Map<String, StepOutcome> stepOutcomes = new LinkedHashMap<>();
	private final Map<String, String> evidenceUrls = new LinkedHashMap<>();

	@Test
	public void saleadsMiNegocioWorkflow() throws Exception {
		final String loginUrl = firstNonBlank(setting("SALEADS_LOGIN_URL"), setting("SALEADS_BASE_URL"));
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL or SALEADS_BASE_URL to execute " + TEST_NAME + ".",
				loginUrl != null && !loginUrl.isBlank());

		createScreenshotDir();
		initializeWebDriver();

		try {
			driver.get(loginUrl);
			waitForUiToLoad();

			runStep("Login", this::stepLoginWithGoogle);
			runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			runStep("Información General", this::stepValidateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			runStep("Tus Negocios", this::stepValidateTusNegocios);
			runStep("Términos y Condiciones",
					() -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08_terminos"));
			runStep("Política de Privacidad",
					() -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09_politica_privacidad"));

			final String finalReport = buildFinalReport();
			System.out.println(finalReport);

			Assert.assertTrue("One or more SaleADS validations failed.\n" + finalReport, allStepsPassed());
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		if (!isSidebarVisible()) {
			clickAnyText(List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google"), true);
			clickByTextIfPresent(GOOGLE_ACCOUNT_EMAIL);
		}

		Assert.assertTrue("Main application interface is not visible after login.", isMainAppVisible());
		Assert.assertTrue("Left sidebar navigation is not visible after login.", isSidebarVisible());
		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByTextIfPresent("Negocio");
		clickAnyText(List.of("Mi Negocio"), true);

		assertVisibleText("Agregar Negocio", "Expected 'Agregar Negocio' option to be visible.");
		assertVisibleText("Administrar Negocios", "Expected 'Administrar Negocios' option to be visible.");

		takeScreenshot("02_mi_negocio_expanded_menu");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickAnyText(List.of("Agregar Negocio"), true);

		assertVisibleText("Crear Nuevo Negocio", "Expected 'Crear Nuevo Negocio' modal title.");
		assertVisibleText("Nombre del Negocio", "Expected 'Nombre del Negocio' field label.");
		assertVisible(By.xpath(
				"//div[@role='dialog' or contains(@class,'modal')]//input"
						+ " | //input[@placeholder='Nombre del Negocio']"),
				"Expected input field for 'Nombre del Negocio'.");
		assertVisibleText("Tienes 2 de 3 negocios", "Expected business quota text.");
		assertVisibleText("Cancelar", "Expected 'Cancelar' button in modal.");
		assertVisibleText("Crear Negocio", "Expected 'Crear Negocio' button in modal.");

		takeScreenshot("03_agregar_negocio_modal");

		typeIntoFirstVisibleInput("Negocio Prueba Automatización");
		clickAnyText(List.of("Cancelar"), true);
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		clickByTextIfPresent("Mi Negocio");
		clickAnyText(List.of("Administrar Negocios"), true);

		assertVisibleText("Información General", "Expected section 'Información General'.");
		assertVisibleText("Detalles de la Cuenta", "Expected section 'Detalles de la Cuenta'.");
		assertVisibleText("Tus Negocios", "Expected section 'Tus Negocios'.");
		assertVisibleText("Sección Legal", "Expected section 'Sección Legal'.");

		takeScreenshot("04_administrar_negocios_account_page");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("BUSINESS PLAN", "Expected 'BUSINESS PLAN' in Información General.");
		assertVisibleText("Cambiar Plan", "Expected 'Cambiar Plan' button.");
		Assert.assertTrue(
				"Expected user email to be visible.",
				isTextVisible(GOOGLE_ACCOUNT_EMAIL, SHORT_TIMEOUT) || pageContainsEmailAddress());

		String usernameHint = GOOGLE_ACCOUNT_EMAIL.split("@")[0];
		if (usernameHint.contains(".")) {
			usernameHint = usernameHint.split("\\.")[0];
		}

		By possibleName = By.xpath(
				"//*[contains(normalize-space(),'Información General')]"
						+ "/ancestor::*[self::section or self::div][1]"
						+ "//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::p]"
						+ "[string-length(normalize-space()) > 2 and not(contains(normalize-space(),'Información General'))]");
		Assert.assertTrue(
				"Expected user name to be visible in Información General.",
				isVisible(possibleName, SHORT_TIMEOUT) || pageTextContainsIgnoreCase(usernameHint));
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Cuenta creada", "Expected 'Cuenta creada' in account details.");
		assertVisibleText("Estado activo", "Expected 'Estado activo' in account details.");
		assertVisibleText("Idioma seleccionado", "Expected 'Idioma seleccionado' in account details.");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios", "Expected 'Tus Negocios' section.");
		assertVisibleText("Agregar Negocio", "Expected 'Agregar Negocio' button in businesses section.");
		assertVisibleText("Tienes 2 de 3 negocios", "Expected business quota text in businesses section.");

		By businessList = By.xpath(
				"//*[contains(normalize-space(),'Tus Negocios')]"
						+ "/ancestor::*[self::section or self::div][1]"
						+ "//*[self::li or self::tr or contains(@class,'business') or contains(@class,'card')]");
		assertVisible(businessList, "Expected at least one visible business item in 'Tus Negocios'.");
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String appHandle = driver.getWindowHandle();
		final String appUrlBeforeClick = driver.getCurrentUrl();
		final Set<String> windowHandlesBeforeClick = driver.getWindowHandles();

		clickAnyText(List.of(linkText), true);
		waitForUiToLoad();

		final String newWindowHandle = waitForNewWindow(windowHandlesBeforeClick, Duration.ofSeconds(10));
		final boolean openedNewTab = newWindowHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(newWindowHandle);
			waitForUiToLoad();
		} else {
			wait.until(d -> !appUrlBeforeClick.equals(d.getCurrentUrl()) || isTextVisible(expectedHeading, SHORT_TIMEOUT));
		}

		assertVisibleText(expectedHeading, "Expected legal heading '" + expectedHeading + "'.");
		assertVisible(By.xpath(
				"//main//*[string-length(normalize-space()) > 80]"
						+ " | //article//*[string-length(normalize-space()) > 80]"
						+ " | //p[string-length(normalize-space()) > 80]"),
				"Expected legal content text to be visible.");

		takeScreenshot(screenshotName);
		evidenceUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void initializeWebDriver() throws MalformedURLException {
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(firstNonBlank(setting("SALEADS_HEADLESS"), "true"));

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-gpu");

		if (headless) {
			options.addArguments("--headless=new");
		}

		final String userDataDir = setting("SALEADS_CHROME_USER_DATA_DIR");
		if (userDataDir != null && !userDataDir.isBlank()) {
			options.addArguments("--user-data-dir=" + userDataDir);
		}

		final String profileDirectory = setting("SALEADS_CHROME_PROFILE");
		if (profileDirectory != null && !profileDirectory.isBlank()) {
			options.addArguments("--profile-directory=" + profileDirectory);
		}

		final String seleniumRemoteUrl = setting("SELENIUM_REMOTE_URL");
		driver = seleniumRemoteUrl != null && !seleniumRemoteUrl.isBlank()
				? new RemoteWebDriver(new URL(seleniumRemoteUrl), options)
				: new ChromeDriver(options);

		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
	}

	private void runStep(final String reportName, final StepRunnable stepRunnable) {
		try {
			stepRunnable.run();
			stepOutcomes.put(reportName, StepOutcome.pass());
		} catch (Throwable throwable) {
			stepOutcomes.put(reportName, StepOutcome.fail(compactMessage(throwable)));
		}
	}

	private boolean allStepsPassed() {
		for (String field : REPORT_FIELDS) {
			if (!stepOutcomes.containsKey(field) || !stepOutcomes.get(field).passed) {
				return false;
			}
		}
		return true;
	}

	private String buildFinalReport() {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("=== SaleADS Mi Negocio Final Report ===").append(System.lineSeparator());

		for (String field : REPORT_FIELDS) {
			final StepOutcome outcome = stepOutcomes.get(field);
			if (outcome == null) {
				reportBuilder.append("FAIL - ").append(field).append(": Not executed.")
						.append(System.lineSeparator());
				continue;
			}

			reportBuilder.append(outcome.passed ? "PASS - " : "FAIL - ")
					.append(field);

			if (!outcome.passed && outcome.details != null && !outcome.details.isBlank()) {
				reportBuilder.append(": ").append(outcome.details);
			}

			reportBuilder.append(System.lineSeparator());
		}

		if (!evidenceUrls.isEmpty()) {
			reportBuilder.append("Final URLs:").append(System.lineSeparator());
			for (Map.Entry<String, String> entry : evidenceUrls.entrySet()) {
				reportBuilder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue())
						.append(System.lineSeparator());
			}
		}

		reportBuilder.append("Screenshots directory: ").append(screenshotDir).append(System.lineSeparator());
		return reportBuilder.toString();
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (Exception ignored) {
			// Keep tests resilient when readyState is unavailable in transient states.
		}

		try {
			Thread.sleep(700);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void clickAnyText(final List<String> texts, final boolean required) {
		Throwable lastFailure = null;
		for (String text : texts) {
			try {
				final By by = clickableByText(text);
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
				safeClick(element);
				return;
			} catch (Throwable throwable) {
				lastFailure = throwable;
			}
		}

		if (required) {
			throw new AssertionError(
					"Unable to click any of the expected labels: " + texts + ". "
							+ (lastFailure == null ? "" : compactMessage(lastFailure)));
		}
	}

	private void clickByTextIfPresent(final String text) {
		try {
			clickAnyText(List.of(text), false);
		} catch (Throwable ignored) {
			// Optional click.
		}
	}

	private void safeClick(final WebElement element) {
		try {
			element.click();
		} catch (Exception clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void typeIntoFirstVisibleInput(final String value) {
		final List<By> candidates = List.of(
				By.xpath("//div[@role='dialog' or contains(@class,'modal')]//input[not(@type='hidden')]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[not(@type='hidden')]"));

		for (By candidate : candidates) {
			try {
				WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(candidate));
				input.clear();
				input.sendKeys(value);
				waitForUiToLoad();
				return;
			} catch (Throwable ignored) {
				// Try the next candidate.
			}
		}
	}

	private void assertVisibleText(final String text, final String message) {
		Assert.assertTrue(message, isTextVisible(text, SHORT_TIMEOUT));
	}

	private void assertVisible(final By by, final String message) {
		Assert.assertTrue(message, isVisible(by, SHORT_TIMEOUT));
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		return isVisible(By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]"), timeout);
	}

	private boolean isVisible(final By by, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
			return true;
		} catch (TimeoutException timeoutException) {
			return false;
		}
	}

	private boolean isSidebarVisible() {
		By sidebar = By.xpath(
				"//aside//*[contains(normalize-space(),'Negocio') or contains(normalize-space(),'Mi Negocio')]"
						+ " | //nav//*[contains(normalize-space(),'Negocio') or contains(normalize-space(),'Mi Negocio')]"
						+ " | //*[@data-testid='sidebar']//*[contains(normalize-space(),'Negocio') or contains(normalize-space(),'Mi Negocio')]");
		return isVisible(sidebar, SHORT_TIMEOUT);
	}

	private boolean isMainAppVisible() {
		By appShell = By.xpath(
				"//main | //div[contains(@class,'dashboard')] | //div[contains(@class,'layout')] | //aside");
		return isVisible(appShell, SHORT_TIMEOUT);
	}

	private boolean pageContainsEmailAddress() {
		try {
			String pageText = driver.findElement(By.tagName("body")).getText();
			return Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(pageText).find();
		} catch (Exception exception) {
			return false;
		}
	}

	private boolean pageTextContainsIgnoreCase(final String candidateText) {
		if (candidateText == null || candidateText.isBlank()) {
			return false;
		}

		try {
			String pageText = driver.findElement(By.tagName("body")).getText();
			return pageText != null && pageText.toLowerCase().contains(candidateText.toLowerCase());
		} catch (Exception exception) {
			return false;
		}
	}

	private By clickableByText(final String text) {
		final String literal = xpathLiteral(text);
		final String ownText = "normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")";
		final String descendantText = ".//*[normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")]";

		final String xpath = "//button[" + ownText + " or " + descendantText + "]"
				+ " | //a[" + ownText + " or " + descendantText + "]"
				+ " | //*[@role='button' and (" + ownText + " or " + descendantText + ")]"
				+ " | //li[" + ownText + "]"
				+ " | //span[" + ownText + "]/ancestor::*[self::button or self::a or @role='button' or self::div][1]";

		return By.xpath(xpath);
	}

	private String waitForNewWindow(final Set<String> existingWindowHandles, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(d -> d.getWindowHandles().size() > existingWindowHandles.size());
			for (String handle : driver.getWindowHandles()) {
				if (!existingWindowHandles.contains(handle)) {
					return handle;
				}
			}
			return null;
		} catch (TimeoutException timeoutException) {
			return null;
		}
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final String fileName = checkpointName + ".png";
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = screenshotDir.resolve(fileName);
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private void createScreenshotDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		screenshotDir = Paths.get("target", "screenshots", TEST_NAME, timestamp);
		Files.createDirectories(screenshotDir);
	}

	private String setting(final String key) {
		return firstNonBlank(System.getProperty(key), System.getenv(key));
	}

	private String firstNonBlank(final String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final String[] parts = text.split("'");
		final List<String> concatParts = new ArrayList<>();
		for (int i = 0; i < parts.length; i++) {
			if (!parts[i].isEmpty()) {
				concatParts.add("'" + parts[i] + "'");
			}
			if (i < parts.length - 1) {
				concatParts.add("\"'\"");
			}
		}
		return "concat(" + concatParts.stream().collect(Collectors.joining(", ")) + ")";
	}

	private String compactMessage(final Throwable throwable) {
		if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
			return throwable == null ? "Unknown failure." : throwable.getClass().getSimpleName();
		}
		return throwable.getMessage().replace('\n', ' ').trim();
	}

	@FunctionalInterface
	private interface StepRunnable {
		void run() throws Exception;
	}

	private static class StepOutcome {
		private final boolean passed;
		private final String details;

		private StepOutcome(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepOutcome pass() {
			return new StepOutcome(true, "");
		}

		private static StepOutcome fail(final String details) {
			return new StepOutcome(false, details);
		}
	}
}
