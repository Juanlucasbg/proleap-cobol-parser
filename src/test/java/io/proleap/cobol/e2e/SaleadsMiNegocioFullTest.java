package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Opt-in E2E workflow for SaleADS "Mi Negocio".
 * <p>
 * This test is disabled by default to keep the main parser test suite isolated.
 * Enable with:
 * <pre>
 *   mvn -Dtest=SaleadsMiNegocioFullTest \
 *       -Dsaleads.e2e.enabled=true \
 *       -Dsaleads.login.url=https://your-environment-login-page \
 *       test
 * </pre>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(35);
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private Path reportFile;
	private String startUrl;
	private String expectedGoogleEmail;
	private String expectedUserName;
	private String appWindow;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(config("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("SaleADS E2E disabled. Enable with -Dsaleads.e2e.enabled=true", enabled);

		startUrl = config("saleads.login.url", "SALEADS_LOGIN_URL", "");
		Assume.assumeTrue("Missing login URL. Set -Dsaleads.login.url or SALEADS_LOGIN_URL.",
				startUrl != null && !startUrl.isBlank());

		expectedGoogleEmail = config("saleads.google.email", "SALEADS_GOOGLE_EMAIL",
				"juanlucasbarbiergarzon@gmail.com");
		expectedUserName = config("saleads.user.name", "SALEADS_USER_NAME", "");

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(config("saleads.headless", "SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,2000");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--lang=es-ES");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
		driver.get(startUrl);
		waitForUiLoad();

		final String timestamp = LocalDateTime.now().format(TS_FORMAT);
		screenshotDir = Path.of("target", "saleads-e2e", "screenshots", timestamp);
		Files.createDirectories(screenshotDir);
		reportFile = Path.of("target", "saleads-e2e", "saleads-mi-negocio-report-" + timestamp + ".md");
		Files.createDirectories(reportFile.getParent());
	}

	@Test
	public void saleadsMiNegocioModuleWorkflow() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminos);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		writeFinalReport();
		assertFalse("SaleADS Mi Negocio workflow had failing validations:\n" + String.join("\n", failures), !failures.isEmpty());
	}

	@After
	public void tearDown() throws IOException {
		if (!stepResults.isEmpty() && reportFile != null) {
			writeFinalReport();
		}
		if (driver != null) {
			driver.quit();
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		clickByVisibleTexts(Arrays.asList(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Iniciar sesion con Google",
				"Continuar con Google",
				"Continuar con google",
				"Google"));

		waitForUiLoad();
		handleGoogleAccountSelector();
		waitForUiLoad();

		appWindow = driver.getWindowHandle();
		assertAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Panel"));
		assertTrue("Left sidebar navigation was not visible", isLeftSidebarVisible());

		saveScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		assertTrue("Left sidebar navigation was not visible", isLeftSidebarVisible());
		clickByVisibleTexts(Arrays.asList("Mi Negocio", "Negocio"));
		waitForUiLoad();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		saveScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleTexts(Arrays.asList("Agregar Negocio"));
		waitForUiLoad();

		assertAnyTextVisible(Arrays.asList("Crear Nuevo Negocio"));
		assertAnyElementVisible(Arrays.asList(
				By.xpath("//*[self::label or self::span][contains(normalize-space(.), 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]")));
		assertAnyTextVisible(Arrays.asList("Tienes 2 de 3 negocios"));
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		saveScreenshot("03-agregar-negocio-modal");

		final Optional<WebElement> nombreInput = findFirstVisible(Arrays.asList(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]")));
		if (nombreInput.isPresent()) {
			nombreInput.get().click();
			nombreInput.get().sendKeys(Keys.chord(Keys.CONTROL, "a"));
			nombreInput.get().sendKeys("Negocio Prueba Automatizacion");
		}
		clickByVisibleTexts(Arrays.asList("Cancelar"));
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisibleQuick("Administrar Negocios")) {
			clickByVisibleTexts(Arrays.asList("Mi Negocio", "Negocio"));
			waitForUiLoad();
		}
		clickByVisibleTexts(Arrays.asList("Administrar Negocios"));
		waitForUiLoad();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertAnyTextVisible(Arrays.asList("Sección Legal", "Seccion Legal"));
		saveScreenshot("04-administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("Información General");
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");

		assertTrue("Expected user email was not visible: " + expectedGoogleEmail,
				getBodyText().contains(expectedGoogleEmail));

		if (expectedUserName != null && !expectedUserName.isBlank()) {
			assertTrue("Expected user name was not visible: " + expectedUserName,
					getBodyText().contains(expectedUserName));
		} else {
			assertTrue("Could not detect a visible user name near account details",
					bodyContainsLikelyUserName(getBodyText(), expectedGoogleEmail));
		}
	}

	private void stepValidateDetallesCuenta() {
		assertAnyTextVisible(Arrays.asList("Cuenta creada"));
		assertAnyTextVisible(Arrays.asList("Estado activo", "Estado Activo"));
		assertAnyTextVisible(Arrays.asList("Idioma seleccionado", "Idioma Seleccionado"));
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertAnyTextVisible(Arrays.asList("Tienes 2 de 3 negocios"));

		final Optional<WebElement> negociosSection = findFirstVisible(Arrays.asList(
				By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]"),
				By.xpath("//section[.//*[contains(normalize-space(.), 'Tus Negocios')]]")));
		assertTrue("Could not locate business list section", negociosSection.isPresent());

		final int businessRows = negociosSection.get().findElements(
				By.xpath(".//li | .//tr | .//*[@role='listitem'] | .//*[@data-testid='business-item']")).size();
		assertTrue("Business list is not visible", businessRows > 0 || negociosSection.get().getText().length() > 80);
	}

	private void stepValidateTerminos() throws IOException {
		final String previousWindow = driver.getWindowHandle();
		final Set<String> beforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleTexts(Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"));
		switchToPossiblyNewWindow(beforeClick);
		waitForUiLoad();

		assertAnyTextVisible(Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"));
		assertTrue("Expected legal content text on terms page", getBodyText().trim().length() > 120);
		termsUrl = driver.getCurrentUrl();
		saveScreenshot("05-terminos-y-condiciones");

		returnToApplication(previousWindow);
		waitForUiLoad();
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		final String previousWindow = driver.getWindowHandle();
		final Set<String> beforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleTexts(Arrays.asList("Política de Privacidad", "Politica de Privacidad"));
		switchToPossiblyNewWindow(beforeClick);
		waitForUiLoad();

		assertAnyTextVisible(Arrays.asList("Política de Privacidad", "Politica de Privacidad"));
		assertTrue("Expected legal content text on privacy page", getBodyText().trim().length() > 120);
		privacyUrl = driver.getCurrentUrl();
		saveScreenshot("06-politica-de-privacidad");

		returnToApplication(previousWindow);
		waitForUiLoad();
	}

	private void runStep(final String stepName, final StepBody body) {
		final StepResult result = new StepResult(stepName);
		try {
			body.run();
			result.status = "PASS";
		} catch (final Throwable throwable) {
			result.status = "FAIL";
			result.details = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
			failures.add(stepName + " -> " + result.details);
			try {
				result.screenshotPath = saveScreenshot(slug(stepName) + "-failed");
			} catch (final IOException ioEx) {
				result.details = result.details + " | screenshot error: " + ioEx.getMessage();
			}
		}
		stepResults.put(stepName, result);
	}

	private void handleGoogleAccountSelector() {
		final Optional<String> appWindowCandidate = Optional.ofNullable(driver.getWindowHandle());

		// Account chooser often appears in a popup; switch if present.
		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() > 1) {
			final String lastWindow = new ArrayList<>(handles).get(handles.size() - 1);
			driver.switchTo().window(lastWindow);
		}

		try {
			wait.until((ExpectedCondition<Boolean>) wd -> wd != null &&
					(wd.getCurrentUrl().contains("accounts.google.com")
							|| normalize(wd.getTitle()).contains("google")));
		} catch (final TimeoutException ignored) {
			return;
		}

		final Optional<WebElement> accountOption = findFirstVisible(Arrays.asList(
				By.xpath("//*[contains(normalize-space(.), '" + expectedGoogleEmail + "')]"),
				By.xpath("//*[@data-identifier='" + expectedGoogleEmail + "']")));
		accountOption.ifPresent(WebElement::click);
		waitForUiLoad();

		// If flow stayed on popup, return to the app window once available.
		if (appWindowCandidate.isPresent() && driver.getWindowHandles().contains(appWindowCandidate.get())) {
			driver.switchTo().window(appWindowCandidate.get());
		} else if (!driver.getWindowHandles().isEmpty()) {
			driver.switchTo().window(new ArrayList<>(driver.getWindowHandles()).get(0));
		}
	}

	private void switchToPossiblyNewWindow(final Set<String> beforeClick) {
		Set<String> afterClick = driver.getWindowHandles();
		if (afterClick.size() <= beforeClick.size()) {
			try {
				new WebDriverWait(driver, Duration.ofSeconds(8))
						.until((ExpectedCondition<Boolean>) wd -> wd != null
								&& wd.getWindowHandles().size() > beforeClick.size());
			} catch (final TimeoutException ignored) {
				// Page probably navigated in the same tab.
			}
			afterClick = driver.getWindowHandles();
		}
		if (afterClick.size() > beforeClick.size()) {
			for (final String window : afterClick) {
				if (!beforeClick.contains(window)) {
					driver.switchTo().window(window);
					return;
				}
			}
		}
	}

	private void returnToApplication(final String previousWindow) {
		final Set<String> currentHandles = driver.getWindowHandles();
		if (currentHandles.size() > 1 && currentHandles.contains(previousWindow)) {
			final String current = driver.getWindowHandle();
			if (!current.equals(previousWindow)) {
				driver.close();
				driver.switchTo().window(previousWindow);
				return;
			}
		}
		if (currentHandles.contains(previousWindow)) {
			driver.switchTo().window(previousWindow);
		} else if (!currentHandles.isEmpty()) {
			driver.switchTo().window(new ArrayList<>(currentHandles).get(0));
		}
	}

	private String saveScreenshot(final String checkpointName) throws IOException {
		final Path target = screenshotDir.resolve(checkpointName + ".png");
		final java.io.File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		return target.toString();
	}

	private void clickByVisibleTexts(final List<String> texts) {
		final Optional<WebElement> match = findClickableText(texts);
		assertTrue("Could not find clickable element by text options: " + texts, match.isPresent());
		match.get().click();
		waitForUiLoad();
	}

	private Optional<WebElement> findClickableText(final List<String> texts) {
		for (final String text : texts) {
			final List<By> candidates = Arrays.asList(
					By.xpath("//*[self::button or self::a or @role='button'][normalize-space(.)='" + text + "']"),
					By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.), '" + text + "')]"),
					By.xpath("//*[normalize-space(.)='" + text + "']"));
			for (final By by : candidates) {
				final Optional<WebElement> candidate = findFirstVisible(Arrays.asList(by));
				if (candidate.isPresent()) {
					return candidate;
				}
			}
		}
		return Optional.empty();
	}

	private Optional<WebElement> findFirstVisible(final List<By> locators) {
		for (final By locator : locators) {
			try {
				wait.until(d -> d != null && !d.findElements(locator).isEmpty());
				final List<WebElement> elements = driver.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return Optional.of(element);
					}
				}
			} catch (final TimeoutException ignored) {
				// try next locator
			}
		}
		return Optional.empty();
	}

	private boolean isLeftSidebarVisible() {
		final Optional<WebElement> sidebar = findFirstVisible(Arrays.asList(
				By.xpath("//aside"),
				By.xpath("//*[@role='navigation']"),
				By.xpath("//*[contains(@class, 'sidebar')]")));
		if (sidebar.isPresent()) {
			return true;
		}
		return isTextVisibleQuick("Mi Negocio") || isTextVisibleQuick("Negocio");
	}

	private void assertTextVisible(final String text) {
		assertAnyTextVisible(Arrays.asList(text));
	}

	private void assertAnyTextVisible(final List<String> textCandidates) {
		final boolean visible = textCandidates.stream().anyMatch(this::isTextVisibleQuick);
		assertTrue("None of these texts are visible: " + textCandidates, visible);
	}

	private boolean isTextVisibleQuick(final String text) {
		try {
			final List<WebElement> elements = driver.findElements(By.xpath("//*[contains(normalize-space(.), '" + text + "')]"));
			return elements.stream().anyMatch(WebElement::isDisplayed);
		} catch (final Exception ex) {
			return false;
		}
	}

	private void assertAnyElementVisible(final List<By> locators) {
		assertTrue("Expected at least one matching visible element for locators", findFirstVisible(locators).isPresent());
	}

	private void waitForUiLoad() {
		try {
			wait.until(driverReadyStateComplete());
		} catch (final TimeoutException ignored) {
			// keep going to avoid hard blocking on SPAs that keep loading indicators.
		}

		final List<By> busyIndicators = Arrays.asList(
				By.cssSelector("[aria-busy='true']"),
				By.cssSelector(".loading"),
				By.cssSelector(".spinner"),
				By.cssSelector("[data-testid*='loading']"));
		for (final By indicator : busyIndicators) {
			try {
				wait.until(d -> d != null && d.findElements(indicator).stream().noneMatch(WebElement::isDisplayed));
			} catch (final TimeoutException ignored) {
				// If one indicator exists persistently, do not block forever.
			}
		}

		try {
			Thread.sleep(400L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private ExpectedCondition<Boolean> driverReadyStateComplete() {
		return wd -> {
			if (wd == null) {
				return false;
			}
			final Object state = ((JavascriptExecutor) wd).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(state));
		};
	}

	private String getBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception ex) {
			return "";
		}
	}

	private boolean bodyContainsLikelyUserName(final String bodyText, final String email) {
		final List<String> lines = Arrays.stream(bodyText.split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.collect(Collectors.toList());

		for (final String line : lines) {
			final String normalized = normalize(line);
			if (line.contains("@")
					|| normalized.contains("business plan")
					|| normalized.contains("cambiar plan")
					|| normalized.contains("informacion general")
					|| normalized.contains("detalles de la cuenta")
					|| normalized.contains("tus negocios")
					|| normalized.contains("seccion legal")) {
				continue;
			}
			if (line.equals(email)) {
				continue;
			}
			if (line.length() >= 3 && line.length() <= 80 && line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private String config(final String propertyKey, final String envKey, final String defaultValue) {
		final String property = System.getProperty(propertyKey);
		if (property != null && !property.isBlank()) {
			return property;
		}
		final String env = System.getenv(envKey);
		if (env != null && !env.isBlank()) {
			return env;
		}
		return defaultValue;
	}

	private String normalize(final String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT);
	}

	private String slug(final String value) {
		return normalize(value).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("# SaleADS Mi Negocio E2E Report\n\n");
		report.append("- Start URL: ").append(startUrl).append("\n");
		report.append("- Timestamp: ").append(LocalDateTime.now()).append("\n");
		report.append("- Screenshots: ").append(screenshotDir).append("\n");
		report.append("- Términos URL: ").append(termsUrl).append("\n");
		report.append("- Política URL: ").append(privacyUrl).append("\n\n");
		report.append("## Step Results\n\n");

		for (final String stepName : Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad")) {
			final StepResult result = stepResults.getOrDefault(stepName, StepResult.notExecuted(stepName));
			report.append("- ").append(stepName).append(": ").append(result.status);
			if (result.details != null && !result.details.isBlank()) {
				report.append(" (").append(result.details).append(")");
			}
			report.append("\n");
		}
		report.append("\n");

		if (!failures.isEmpty()) {
			report.append("## Failures\n\n");
			for (final String failure : failures) {
				report.append("- ").append(failure).append("\n");
			}
		}
		Files.writeString(reportFile, report.toString());
	}

	@FunctionalInterface
	private interface StepBody {
		void run() throws Exception;
	}

	private static class StepResult {
		private final String stepName;
		private String status = "NOT_EXECUTED";
		private String details = "";
		private String screenshotPath = "";

		private StepResult(final String stepName) {
			this.stepName = stepName;
		}

		private static StepResult notExecuted(final String stepName) {
			return new StepResult(stepName);
		}
	}
}
