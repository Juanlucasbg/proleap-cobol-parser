package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.After;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioFullTest {

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

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(6);

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final DateTimeFormatter screenshotTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String applicationAccountUrl;
	private String termsFinalUrl = "N/A";
	private String privacyFinalUrl = "N/A";

	@Before
	public void setup() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this external E2E flow.",
				Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_E2E_ENABLED", "false")));

		for (final String field : REPORT_FIELDS) {
			report.put(field, false);
		}

		screenshotDir = Path.of("target", "saleads-evidence");
		Files.createDirectories(screenshotDir);

		final String remoteUrl = emptyToNull(System.getenv("SALEADS_SELENIUM_REMOTE_URL"));
		driver = remoteUrl == null ? createLocalChromeDriver() : createRemoteDriver(remoteUrl);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().window().maximize();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		final String loginUrl = emptyToNull(System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the environment login page URL.", loginUrl != null);

		final boolean loginOk = runStep("Login", () -> stepLogin(loginUrl));
		final boolean menuOk = loginOk && runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		final boolean modalOk = menuOk && runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		final boolean adminOk = menuOk && runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		final boolean infoOk = adminOk && runStep("Información General", this::stepValidateInformacionGeneral);
		final boolean detailsOk = adminOk && runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		final boolean businessOk = adminOk && runStep("Tus Negocios", this::stepValidateTusNegocios);
		final boolean termsOk = adminOk && runStep("Términos y Condiciones", this::stepValidateTerminos);
		final boolean privacyOk = adminOk && runStep("Política de Privacidad", this::stepValidatePrivacidad);

		if (!menuOk) {
			failures.add("Skipped dependent steps because 'Mi Negocio menu' failed.");
		} else if (!adminOk) {
			failures.add("Skipped account section validations because 'Administrar Negocios view' failed.");
		}

		final String finalReport = buildFinalReport(loginOk, menuOk, modalOk, adminOk, infoOk, detailsOk, businessOk, termsOk,
				privacyOk);
		System.out.println(finalReport);

		assertTrue(finalReport, failures.isEmpty() && report.values().stream().allMatch(Boolean::booleanValue));
	}

	private boolean stepLogin(final String loginUrl) {
		driver.get(loginUrl);
		waitForUiToLoad();

		clickByAnyText(List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"));
		waitForUiToLoad();

		// Optional Google account selector step.
		clickIfVisible("juanlucasbarbiergarzon@gmail.com", SHORT_TIMEOUT);
		waitForUiToLoad();

		final boolean mainUiVisible = isAnyVisible(List.of(
				By.xpath("//*[contains(normalize-space(.), 'Dashboard')]"),
				By.xpath("//*[contains(normalize-space(.), 'Inicio')]"),
				By.xpath("//aside"),
				By.xpath("//nav")));
		final boolean sidebarVisible = isAnyVisible(List.of(
				By.xpath("//aside"),
				By.xpath("//nav"),
				textContainsLocator("Negocio")));

		takeScreenshot("01-dashboard-loaded");
		return mainUiVisible && sidebarVisible;
	}

	private boolean stepOpenMiNegocioMenu() {
		clickByAnyText(List.of("Mi Negocio", "Negocio"));
		waitForUiToLoad();

		final boolean agregarVisible = isVisible(textContainsLocator("Agregar Negocio"), DEFAULT_TIMEOUT);
		final boolean administrarVisible = isVisible(textContainsLocator("Administrar Negocios"), DEFAULT_TIMEOUT);
		takeScreenshot("02-mi-negocio-menu-expanded");
		return agregarVisible && administrarVisible;
	}

	private boolean stepValidateAgregarNegocioModal() {
		clickByAnyText(List.of("Agregar Negocio"));
		waitForUiToLoad();

		final boolean modalTitle = isVisible(textContainsLocator("Crear Nuevo Negocio"), DEFAULT_TIMEOUT);
		final boolean nombreInput = isAnyVisible(List.of(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]")));
		final boolean quotaText = isVisible(textContainsLocator("Tienes 2 de 3 negocios"), DEFAULT_TIMEOUT);
		final boolean cancelar = isVisible(textContainsLocator("Cancelar"), DEFAULT_TIMEOUT);
		final boolean crear = isVisible(textContainsLocator("Crear Negocio"), DEFAULT_TIMEOUT);

		takeScreenshot("03-agregar-negocio-modal");

		if (nombreInput) {
			final WebElement input = findFirstVisible(List.of(
					By.xpath("//input[@placeholder='Nombre del Negocio']"),
					By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]")),
					DEFAULT_TIMEOUT);
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
			waitForUiToLoad();
		}

		clickByAnyText(List.of("Cancelar"));
		waitForUiToLoad();

		return modalTitle && nombreInput && quotaText && cancelar && crear;
	}

	private boolean stepOpenAdministrarNegocios() {
		if (!isVisible(textContainsLocator("Administrar Negocios"), SHORT_TIMEOUT)) {
			clickByAnyText(List.of("Mi Negocio", "Negocio"));
			waitForUiToLoad();
		}

		clickByAnyText(List.of("Administrar Negocios"));
		waitForUiToLoad();

		final boolean infoGeneral = isVisible(textContainsLocator("Información General"), DEFAULT_TIMEOUT);
		final boolean detallesCuenta = isVisible(textContainsLocator("Detalles de la Cuenta"), DEFAULT_TIMEOUT);
		final boolean tusNegocios = isVisible(textContainsLocator("Tus Negocios"), DEFAULT_TIMEOUT);
		final boolean seccionLegal = isVisible(textContainsLocator("Sección Legal"), DEFAULT_TIMEOUT);

		applicationAccountUrl = driver.getCurrentUrl();
		takeScreenshot("04-administrar-negocios-full-page");
		return infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
	}

	private boolean stepValidateInformacionGeneral() {
		final boolean userNameVisible = isAnyVisible(List.of(
				By.xpath("//*[contains(@class,'user') and string-length(normalize-space(.)) > 0]"),
				By.xpath("//*[contains(normalize-space(.), '@')]/preceding::*[1][string-length(normalize-space(.)) > 0]")));
		final boolean userEmailVisible = isAnyVisible(List.of(By.xpath("//*[contains(normalize-space(.), '@')]")));
		final boolean businessPlanVisible = isVisible(textContainsLocator("BUSINESS PLAN"), DEFAULT_TIMEOUT);
		final boolean cambiarPlanVisible = isVisible(textContainsLocator("Cambiar Plan"), DEFAULT_TIMEOUT);
		return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean stepValidateDetallesCuenta() {
		return isVisible(textContainsLocator("Cuenta creada"), DEFAULT_TIMEOUT)
				&& isVisible(textContainsLocator("Estado activo"), DEFAULT_TIMEOUT)
				&& isVisible(textContainsLocator("Idioma seleccionado"), DEFAULT_TIMEOUT);
	}

	private boolean stepValidateTusNegocios() {
		final boolean sectionVisible = isVisible(textContainsLocator("Tus Negocios"), DEFAULT_TIMEOUT);
		final boolean addButtonVisible = isVisible(textContainsLocator("Agregar Negocio"), DEFAULT_TIMEOUT);
		final boolean quotaVisible = isVisible(textContainsLocator("Tienes 2 de 3 negocios"), DEFAULT_TIMEOUT);
		return sectionVisible && addButtonVisible && quotaVisible;
	}

	private boolean stepValidateTerminos() {
		final LegalValidationResult result = validateLegalLink("Términos y Condiciones", "Términos y Condiciones",
				"08-terminos");
		termsFinalUrl = result.finalUrl;
		return result.ok;
	}

	private boolean stepValidatePrivacidad() {
		final LegalValidationResult result = validateLegalLink("Política de Privacidad", "Política de Privacidad",
				"09-politica-privacidad");
		privacyFinalUrl = result.finalUrl;
		return result.ok;
	}

	private LegalValidationResult validateLegalLink(final String linkText, final String headingText,
			final String screenshotNamePrefix) {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = driver.getWindowHandles();

		clickByAnyText(List.of(linkText));
		waitForUiToLoad();

		final WebDriverWait legalWait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		legalWait.until(d -> d.getWindowHandles().size() > windowsBefore.size()
				|| !d.getCurrentUrl().equals(applicationAccountUrl));

		final Set<String> windowsAfter = driver.getWindowHandles();
		final Set<String> newHandles = new LinkedHashSet<>(windowsAfter);
		newHandles.removeAll(windowsBefore);

		boolean switchedToNewTab = false;
		if (!newHandles.isEmpty()) {
			driver.switchTo().window(newHandles.iterator().next());
			switchedToNewTab = true;
			waitForUiToLoad();
		}

		final boolean headingVisible = isVisible(textContainsLocator(headingText), DEFAULT_TIMEOUT);
		final boolean legalContentVisible = isAnyVisible(List.of(
				By.xpath("//p[string-length(normalize-space(.)) > 20]"),
				By.xpath("//article//*[string-length(normalize-space(.)) > 20]"),
				By.xpath("//*[contains(normalize-space(.), 'condiciones') or contains(normalize-space(.), 'privacidad')]")));

		final String finalUrl = driver.getCurrentUrl();
		takeScreenshot(screenshotNamePrefix + "-legal-page");

		// Return to app context.
		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
		} else if (applicationAccountUrl != null) {
			driver.navigate().to(applicationAccountUrl);
		}
		waitForUiToLoad();

		return new LegalValidationResult(headingVisible && legalContentVisible, finalUrl);
	}

	private boolean runStep(final String field, final Supplier<Boolean> action) {
		try {
			final boolean ok = action.get();
			report.put(field, ok);
			if (!ok) {
				failures.add(field + ": validation returned false.");
			}
			return ok;
		} catch (final Exception exception) {
			report.put(field, false);
			failures.add(field + ": " + exception.getMessage());
			takeScreenshot("error-" + slug(field));
			return false;
		}
	}

	private String buildFinalReport(final boolean loginOk, final boolean menuOk, final boolean modalOk, final boolean adminOk,
			final boolean infoOk, final boolean detailsOk, final boolean businessOk, final boolean termsOk,
			final boolean privacyOk) {
		final StringBuilder builder = new StringBuilder();
		builder.append("\n==== SaleADS Mi Negocio Workflow Report ====\n");
		builder.append(formatLine("Login", loginOk));
		builder.append(formatLine("Mi Negocio menu", menuOk));
		builder.append(formatLine("Agregar Negocio modal", modalOk));
		builder.append(formatLine("Administrar Negocios view", adminOk));
		builder.append(formatLine("Información General", infoOk));
		builder.append(formatLine("Detalles de la Cuenta", detailsOk));
		builder.append(formatLine("Tus Negocios", businessOk));
		builder.append(formatLine("Términos y Condiciones", termsOk));
		builder.append(formatLine("Política de Privacidad", privacyOk));
		builder.append("Términos URL final: ").append(termsFinalUrl).append("\n");
		builder.append("Política URL final: ").append(privacyFinalUrl).append("\n");
		builder.append("Screenshots: ").append(screenshotDir.toAbsolutePath()).append("\n");

		if (!failures.isEmpty()) {
			builder.append("Failures:\n");
			for (final String failure : failures) {
				builder.append(" - ").append(failure).append("\n");
			}
		}

		return builder.toString();
	}

	private String formatLine(final String field, final boolean ok) {
		return field + ": " + (ok ? "PASS" : "FAIL") + "\n";
	}

	private WebDriver createLocalChromeDriver() {
		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = buildChromeOptions();
		return new ChromeDriver(options);
	}

	private WebDriver createRemoteDriver(final String remoteUrl) {
		final ChromeOptions options = buildChromeOptions();
		try {
			return new RemoteWebDriver(java.net.URI.create(remoteUrl).toURL(), options);
		} catch (final Exception exception) {
			throw new IllegalStateException("Invalid SALEADS_SELENIUM_REMOTE_URL: " + remoteUrl, exception);
		}
	}

	private ChromeOptions buildChromeOptions() {
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--window-size=1920,1080");
		return options;
	}

	private void clickByAnyText(final List<String> texts) {
		for (final String text : texts) {
			final List<WebElement> candidates = visibleElements(textClickLocator(text));
			if (!candidates.isEmpty()) {
				wait.until(ExpectedConditions.elementToBeClickable(candidates.get(0))).click();
				waitForUiToLoad();
				return;
			}
		}
		throw new IllegalStateException("None of these texts were clickable: " + texts);
	}

	private void clickIfVisible(final String text, final Duration timeout) {
		try {
			final WebElement element = new WebDriverWait(driver, timeout).until(d -> {
				final List<WebElement> shown = d.findElements(textClickLocator(text)).stream()
						.filter(WebElement::isDisplayed)
						.collect(Collectors.toList());
				return shown.isEmpty() ? null : shown.get(0);
			});
			element.click();
		} catch (final Exception ignored) {
			// Optional click for account selector when it appears.
		}
	}

	private boolean isVisible(final By by, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
			return true;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private boolean isAnyVisible(final List<By> locators) {
		for (final By locator : locators) {
			if (isVisible(locator, SHORT_TIMEOUT)) {
				return true;
			}
		}
		return false;
	}

	private WebElement findFirstVisible(final List<By> locators, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		for (final By locator : locators) {
			try {
				return localWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (final Exception ignored) {
				// Try next locator.
			}
		}
		throw new IllegalStateException("No visible element found for locators " + locators);
	}

	private List<WebElement> visibleElements(final By locator) {
		return driver.findElements(locator).stream()
				.filter(WebElement::isDisplayed)
				.collect(Collectors.toList());
	}

	private By textContainsLocator(final String text) {
		return By.xpath("//*[contains(normalize-space(.), " + toXpathLiteral(text) + ")]");
	}

	private By textClickLocator(final String text) {
		return By.xpath(
				"//*[self::button or self::a or @role='button' or self::span or self::div][contains(normalize-space(.), "
						+ toXpathLiteral(text) + ")]");
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final String joined = String.join(", \"'\", ", java.util.Arrays.stream(parts).map(part -> "'" + part + "'")
				.collect(Collectors.toList()));
		return "concat(" + joined + ")";
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Some SPA interactions do not trigger full readyState changes.
		}
		try {
			Thread.sleep(700);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String checkpoint) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final String fileName = checkpoint + "-" + LocalDateTime.now().format(screenshotTimestamp) + ".png";
		final Path output = screenshotDir.resolve(fileName);
		try {
			final byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(output, screenshotBytes);
		} catch (final IOException ignored) {
			// Best effort evidence collection.
		}
	}

	private String slug(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-");
	}

	private String emptyToNull(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static final class LegalValidationResult {
		private final boolean ok;
		private final String finalUrl;

		private LegalValidationResult(final boolean ok, final String finalUrl) {
			this.ok = ok;
			this.finalUrl = finalUrl;
		}
	}
}
