package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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

/**
 * End-to-end workflow validation for SaleADS "Mi Negocio" module.
 *
 * <p>
 * The test intentionally avoids a hardcoded application URL. Configure
 * SALEADS_LOGIN_URL/saleads.login.url for the current environment, or attach to
 * an already-open Chrome session by setting
 * SALEADS_CHROME_DEBUGGER_ADDRESS/saleads.chrome.debuggerAddress.
 * </p>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

	private final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private Path screenshotDir;

	private WebDriver driver;
	private WebDriverWait wait;

	@Test
	public void saleads_mi_negocio_full_test() throws Exception {
		final List<String> failures = new ArrayList<>();
		initializeReport();

		try {
			screenshotDir = Paths.get("target", "saleads-mi-negocio-screenshots");
			Files.createDirectories(screenshotDir);

			driver = createWebDriver();
			wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

			runStep("Login", failures, this::validateLoginWithGoogle);
			runStep("Mi Negocio menu", failures, this::validateMiNegocioMenu);
			runStep("Agregar Negocio modal", failures, this::validateAgregarNegocioModal);
			runStep("Administrar Negocios view", failures, this::validateAdministrarNegociosView);
			runStep("Información General", failures, this::validateInformacionGeneral);
			runStep("Detalles de la Cuenta", failures, this::validateDetallesCuenta);
			runStep("Tus Negocios", failures, this::validateTusNegocios);
			runStep("Términos y Condiciones", failures,
					() -> legalUrls.put("Términos y Condiciones", validateLegalPage("Términos y Condiciones", "terminos")));
			runStep("Política de Privacidad", failures,
					() -> legalUrls.put("Política de Privacidad", validateLegalPage("Política de Privacidad", "privacidad")));
		} finally {
			writeFinalReport(failures);

			if (driver != null) {
				driver.quit();
			}
		}

		assertTrue("SaleADS Mi Negocio workflow failed:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private void initializeReport() {
		report.put("Login", Boolean.FALSE);
		report.put("Mi Negocio menu", Boolean.FALSE);
		report.put("Agregar Negocio modal", Boolean.FALSE);
		report.put("Administrar Negocios view", Boolean.FALSE);
		report.put("Información General", Boolean.FALSE);
		report.put("Detalles de la Cuenta", Boolean.FALSE);
		report.put("Tus Negocios", Boolean.FALSE);
		report.put("Términos y Condiciones", Boolean.FALSE);
		report.put("Política de Privacidad", Boolean.FALSE);
	}

	private void runStep(final String reportField, final List<String> failures, final StepAction action) {
		try {
			action.run();
			report.put(reportField, Boolean.TRUE);
		} catch (final Exception ex) {
			report.put(reportField, Boolean.FALSE);
			failures.add(reportField + ": " + summarizeException(ex));
			takeFailureScreenshot(reportField);
		}
	}

	private void validateLoginWithGoogle() throws Exception {
		openLoginPageIfProvided();

		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiToLoad();

		chooseGoogleAccountIfPrompted("juanlucasbarbiergarzon@gmail.com");

		wait.until(d -> hasVisibleElement(sidebarLocator()) || hasVisibleText("Mi Negocio") || hasVisibleText("Negocio"));
		assertVisible(sidebarLocator(), "Left sidebar navigation is visible");

		takeScreenshot("dashboard-loaded");
	}

	private void validateMiNegocioMenu() throws Exception {
		expandMiNegocioMenuIfNeeded();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");

		takeScreenshot("mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws Exception {
		expandMiNegocioMenuIfNeeded();
		clickByVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final WebElement nombreInput = findBusinessNameInput();
		nombreInput.click();
		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatización");

		takeScreenshot("agregar-negocio-modal");

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byContainsText("Crear Nuevo Negocio")));
		waitForUiToLoad();
	}

	private void validateAdministrarNegociosView() throws Exception {
		expandMiNegocioMenuIfNeeded();
		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");

		takeScreenshot("administrar-negocios-view");
	}

	private void validateInformacionGeneral() {
		assertVisibleText("Información General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String pageText = getPageText();
		assertTrue("User email should be visible", EMAIL_PATTERN.matcher(pageText).find());
		assertTrue("User name should be visible", hasLikelyUserName(pageText));
	}

	private void validateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() throws Exception {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private String validateLegalPage(final String linkText, final String screenshotPrefix) throws Exception {
		assertVisibleText("Sección Legal");

		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String initialUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		String targetHandle = appHandle;
		boolean openedNewTab = false;

		try {
			new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> {
				final Set<String> handles = d.getWindowHandles();
				return handles.size() > handlesBeforeClick.size() || !Objects.equals(d.getCurrentUrl(), initialUrl);
			});
		} catch (final TimeoutException ignored) {
			// Keep going; some environments can open in the same URL and still render content.
		}

		final Set<String> handlesAfterClick = driver.getWindowHandles();
		if (handlesAfterClick.size() > handlesBeforeClick.size()) {
			openedNewTab = true;
			for (final String handle : handlesAfterClick) {
				if (!handlesBeforeClick.contains(handle)) {
					targetHandle = handle;
					break;
				}
			}
			driver.switchTo().window(targetHandle);
			waitForUiToLoad();
		}

		assertVisibleText(linkText);
		final String legalText = getPageText();
		assertTrue("Legal content should be visible for " + linkText, legalText.length() > 100);

		takeScreenshot(screenshotPrefix + "-legal-page");
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void openLoginPageIfProvided() {
		final String loginUrl = readSetting("saleads.login.url", "SALEADS_LOGIN_URL");
		if (isBlank(loginUrl)) {
			final String currentUrl = driver.getCurrentUrl();
			if (currentUrl == null || currentUrl.isBlank() || currentUrl.startsWith("about:blank")
					|| currentUrl.startsWith("data:,") || currentUrl.startsWith("chrome://newtab")) {
				throw new IllegalStateException(
						"No login page configured. Set SALEADS_LOGIN_URL (or -Dsaleads.login.url), or attach to an existing logged-in browser with SALEADS_CHROME_DEBUGGER_ADDRESS.");
			}
			return;
		}

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	private WebDriver createWebDriver() throws MalformedURLException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--lang=es-ES");

		if (shouldRunHeadless()) {
			options.addArguments("--headless=new");
		}

		final String debuggerAddress = readSetting("saleads.chrome.debuggerAddress", "SALEADS_CHROME_DEBUGGER_ADDRESS");
		if (!isBlank(debuggerAddress)) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress.trim());
		}

		final String remoteWebDriverUrl = readSetting("saleads.remote.webdriver.url", "SALEADS_REMOTE_WEBDRIVER_URL");
		if (!isBlank(remoteWebDriverUrl)) {
			return new RemoteWebDriver(new URL(remoteWebDriverUrl.trim()), options);
		}

		return new ChromeDriver(options);
	}

	private boolean shouldRunHeadless() {
		final String value = readSetting("saleads.headless", "SALEADS_HEADLESS");
		if (isBlank(value)) {
			return true;
		}
		return Boolean.parseBoolean(value.trim());
	}

	private void expandMiNegocioMenuIfNeeded() throws Exception {
		assertVisible(sidebarLocator(), "Left sidebar navigation is visible");

		clickByVisibleText("Negocio");
		waitForUiToLoad();

		if (!hasVisibleText("Agregar Negocio") || !hasVisibleText("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}
	}

	private void chooseGoogleAccountIfPrompted(final String accountEmail) {
		final By accountBy = byContainsText(accountEmail);
		try {
			final WebElement accountOption = findVisible(accountBy, Duration.ofSeconds(20));
			accountOption.click();
			waitForUiToLoad();
		} catch (final TimeoutException ignored) {
			// Account chooser did not appear. Login may already be completed.
		}
	}

	private WebElement findBusinessNameInput() {
		final List<By> candidates = List.of(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));

		for (final By by : candidates) {
			final List<WebElement> elements = driver.findElements(by);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}

		throw new IllegalStateException("Input field 'Nombre del Negocio' not found.");
	}

	private void clickByVisibleText(final String... texts) throws Exception {
		Exception lastFailure = null;

		for (final String text : texts) {
			try {
				final WebElement element = findVisible(clickableTextLocator(text), Duration.ofSeconds(20));
				wait.until(ExpectedConditions.elementToBeClickable(element));
				element.click();
				waitForUiToLoad();
				return;
			} catch (final Exception ex) {
				lastFailure = ex;
			}
		}

		throw new IllegalStateException("Unable to click any element by visible text: " + String.join(", ", texts),
				lastFailure);
	}

	private void assertVisibleText(final String text) {
		findVisible(byContainsText(text), Duration.ofSeconds(20));
	}

	private void assertVisible(final By by, final String message) {
		final List<WebElement> matches = driver.findElements(by);
		for (final WebElement match : matches) {
			if (match.isDisplayed()) {
				return;
			}
		}
		throw new IllegalStateException(message);
	}

	private boolean hasVisibleText(final String text) {
		return hasVisibleElement(byContainsText(text));
	}

	private boolean hasVisibleElement(final By by) {
		final List<WebElement> elements = driver.findElements(by);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private WebElement findVisible(final By by, final Duration timeout) {
		final WebDriverWait customWait = new WebDriverWait(driver, timeout);
		return customWait.until(d -> {
			final List<WebElement> elements = d.findElements(by);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private By sidebarLocator() {
		return By.xpath("//aside | //nav | //*[@role='navigation'] | //*[contains(@class,'sidebar') or contains(@class,'SideBar')]");
	}

	private By clickableTextLocator(final String text) {
		final String literal = asXPathLiteral(text);
		return By.xpath(
				"//button[contains(normalize-space(.), " + literal + ")]"
						+ " | //a[contains(normalize-space(.), " + literal + ")]"
						+ " | //*[@role='button' and contains(normalize-space(.), " + literal + ")]"
						+ " | //*[self::span or self::div][contains(normalize-space(.), " + literal + ")]");
	}

	private By byContainsText(final String text) {
		final String literal = asXPathLiteral(text);
		return By.xpath("//*[contains(normalize-space(.), " + literal + ")]");
	}

	private String getPageText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> {
				final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return state != null && ("complete".equals(state.toString()) || "interactive".equals(state.toString()));
			});
		} catch (final Exception ignored) {
			// Keep proceeding if script execution is blocked by page transitions.
		}
	}

	private boolean hasLikelyUserName(final String pageText) {
		final String[] ignored = { "INFORMACIÓN GENERAL", "DETALLES DE LA CUENTA", "BUSINESS PLAN", "CAMBIAR PLAN",
				"TUS NEGOCIOS", "SECCIÓN LEGAL", "CUENTA CREADA", "ESTADO ACTIVO", "IDIOMA SELECCIONADO", "AGREGAR NEGOCIO" };

		for (final String line : pageText.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.length() < 3 || trimmed.length() > 60 || trimmed.contains("@")) {
				continue;
			}

			final String upper = trimmed.toUpperCase(Locale.ROOT);
			boolean skip = false;
			for (final String marker : ignored) {
				if (upper.contains(marker)) {
					skip = true;
					break;
				}
			}
			if (skip) {
				continue;
			}

			if (trimmed.matches(".*[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]{3,}.*")) {
				return true;
			}
		}

		return false;
	}

	private void takeScreenshot(final String label) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		try {
			final String name = Instant.now().toEpochMilli() + "-" + slug(label) + ".png";
			final Path outputPath = screenshotDir.resolve(name);
			Files.write(outputPath, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
		} catch (final Exception ignored) {
			// Best effort evidence capture.
		}
	}

	private void takeFailureScreenshot(final String stepName) {
		takeScreenshot("failure-" + stepName);
	}

	private String summarizeException(final Exception ex) {
		if (ex instanceof TimeoutException) {
			return "Timed out waiting for expected UI element or state.";
		}

		final String fallback = ex.getClass().getSimpleName();
		final String message = Optional.ofNullable(ex.getMessage()).orElse(fallback).trim();
		if (message.isEmpty()) {
			return fallback;
		}

		final String firstLine = message.split("\\R", 2)[0].trim();
		return firstLine.isEmpty() ? fallback : firstLine;
	}

	private void writeFinalReport(final List<String> failures) throws Exception {
		final Path reportPath = Paths.get("target", "saleads-mi-negocio-report.txt");
		Files.createDirectories(reportPath.getParent());

		final StringBuilder content = new StringBuilder();
		content.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		content.append("================================").append(System.lineSeparator()).append(System.lineSeparator());

		for (final Map.Entry<String, Boolean> item : report.entrySet()) {
			content.append(item.getKey()).append(": ").append(item.getValue() ? "PASS" : "FAIL").append(System.lineSeparator());
		}

		content.append(System.lineSeparator()).append("Final URLs").append(System.lineSeparator()).append("----------")
				.append(System.lineSeparator());
		content.append("Términos y Condiciones: ")
				.append(legalUrls.getOrDefault("Términos y Condiciones", "N/A"))
				.append(System.lineSeparator());
		content.append("Política de Privacidad: ")
				.append(legalUrls.getOrDefault("Política de Privacidad", "N/A"))
				.append(System.lineSeparator());

		content.append(System.lineSeparator()).append("Evidence directory: ").append(screenshotDir.toAbsolutePath())
				.append(System.lineSeparator());

		if (!failures.isEmpty()) {
			content.append(System.lineSeparator()).append("Failures").append(System.lineSeparator()).append("--------")
					.append(System.lineSeparator());
			for (final String failure : failures) {
				content.append("- ").append(failure).append(System.lineSeparator());
			}
		}

		Files.writeString(reportPath, content.toString());
	}

	private String readSetting(final String systemProperty, final String envVar) {
		final String fromProperty = System.getProperty(systemProperty);
		if (!isBlank(fromProperty)) {
			return fromProperty.trim();
		}

		final String fromEnv = System.getenv(envVar);
		if (!isBlank(fromEnv)) {
			return fromEnv.trim();
		}

		return null;
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private String slug(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String asXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
