package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String DEFAULT_EXPECTED_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private String termsUrl = "";
	private String privacyUrl = "";

	@Before
	public void setUp() throws IOException {
		final String saleadsBaseUrl = System.getenv("SALEADS_BASE_URL");
		Assume.assumeTrue(
				"Skipping SaleADS E2E workflow because SALEADS_BASE_URL is not set. "
						+ "Set SALEADS_BASE_URL to the login page URL for the current environment.",
				saleadsBaseUrl != null && !saleadsBaseUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new");
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDirectory = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDirectory);

		driver.get(saleadsBaseUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStep("Login", this::loginWithGoogleAndValidateAppShell);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegociosAndValidateSections);
		runStep("Información General", this::validateInformacionGeneralSection);
		runStep("Detalles de la Cuenta", this::validateDetallesDeLaCuentaSection);
		runStep("Tus Negocios", this::validateTusNegociosSection);
		runStep("Términos y Condiciones", this::validateTermsAndConditions);
		runStep("Política de Privacidad", this::validatePrivacyPolicy);

		final String report = buildFinalReport();
		System.out.println(report);

		assertTrue(report, failures.isEmpty());
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, Boolean.TRUE);
		} catch (final Throwable error) {
			stepResults.put(stepName, Boolean.FALSE);
			failures.add(stepName + ": " + error.getMessage());
		}
	}

	private void loginWithGoogleAndValidateAppShell() throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowHandlesBefore = driver.getWindowHandles();

		clickVisibleText("Sign in with Google", "Login with Google", "Iniciar sesion con Google",
				"Iniciar sesión con Google", "Continuar con Google", "Google");

		handleGoogleAccountSelection(appWindow, windowHandlesBefore, DEFAULT_EXPECTED_EMAIL);

		final boolean dashboardLoaded = waitUntil(
				() -> isAnyVisibleTextPresent("Mi Negocio", "Negocio") && isSidebarVisible(), Duration.ofSeconds(90));
		assertTrue("Main app shell did not load after Google login.", dashboardLoaded);

		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws IOException {
		assertTrue("Left sidebar navigation is not visible.", isSidebarVisible());

		clickVisibleText("Mi Negocio");

		assertTrue("Mi Negocio submenu did not expand with Agregar Negocio.",
				waitUntil(() -> isAnyVisibleTextPresent("Agregar Negocio"), Duration.ofSeconds(20)));
		assertTrue("Mi Negocio submenu did not show Administrar Negocios.",
				waitUntil(() -> isAnyVisibleTextPresent("Administrar Negocios"), Duration.ofSeconds(20)));

		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickVisibleText("Agregar Negocio");

		assertTrue("Modal title 'Crear Nuevo Negocio' is not visible.",
				waitUntil(() -> isAnyVisibleTextPresent("Crear Nuevo Negocio"), Duration.ofSeconds(20)));

		final By businessNameInputLocator = By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio') or "
						+ "ancestor::*[.//*[contains(normalize-space(.),'Nombre del Negocio')]]]");
		assertTrue("Input field 'Nombre del Negocio' does not exist.",
				!driver.findElements(businessNameInputLocator).isEmpty());
		assertTrue("Text 'Tienes 2 de 3 negocios' is not visible.",
				isAnyVisibleTextPresent("Tienes 2 de 3 negocios"));
		assertTrue("Button 'Cancelar' is not present.", isAnyVisibleTextPresent("Cancelar"));
		assertTrue("Button 'Crear Negocio' is not present.", isAnyVisibleTextPresent("Crear Negocio"));

		takeScreenshot("03-agregar-negocio-modal");

		// Optional action sequence requested by workflow.
		final List<WebElement> nameInputs = driver.findElements(businessNameInputLocator);
		if (!nameInputs.isEmpty()) {
			nameInputs.get(0).click();
			nameInputs.get(0).clear();
			nameInputs.get(0).sendKeys("Negocio Prueba Automatizacion");
		}

		if (isAnyVisibleTextPresent("Cancelar")) {
			clickVisibleText("Cancelar");
			waitUntil(() -> !isAnyVisibleTextPresent("Crear Nuevo Negocio"), Duration.ofSeconds(10));
		}
	}

	private void openAdministrarNegociosAndValidateSections() throws IOException {
		if (!isAnyVisibleTextPresent("Administrar Negocios")) {
			clickVisibleText("Mi Negocio");
		}

		clickVisibleText("Administrar Negocios");

		assertTrue("Section 'Informacion General' was not found.",
				waitUntil(() -> isAnyVisibleTextPresent("Informacion General", "Información General"), Duration.ofSeconds(30)));
		assertTrue("Section 'Detalles de la Cuenta' was not found.",
				isAnyVisibleTextPresent("Detalles de la Cuenta"));
		assertTrue("Section 'Tus Negocios' was not found.", isAnyVisibleTextPresent("Tus Negocios"));
		assertTrue("Section 'Seccion Legal' was not found.",
				isAnyVisibleTextPresent("Seccion Legal", "Sección Legal"));

		takeScreenshot("04-administrar-negocios-page");
	}

	private void validateInformacionGeneralSection() {
		final String expectedEmail = Optional.ofNullable(System.getenv("SALEADS_EXPECTED_EMAIL"))
				.filter(value -> !value.isBlank()).orElse(DEFAULT_EXPECTED_EMAIL);
		final String expectedName = Optional.ofNullable(System.getenv("SALEADS_EXPECTED_USER_NAME")).orElse("").trim();

		if (!expectedName.isBlank()) {
			assertTrue("Configured user name is not visible in Informacion General.",
					isAnyVisibleTextPresent(expectedName));
		} else {
			assertTrue("No visible user name cue found in Informacion General.",
					isAnyVisibleTextPresent("Nombre", "Usuario", "Perfil"));
		}

		assertTrue("User email is not visible in Informacion General.", isAnyVisibleTextPresent(expectedEmail));
		assertTrue("Text 'BUSINESS PLAN' is not visible.", isAnyVisibleTextPresent("BUSINESS PLAN"));
		assertTrue("Button 'Cambiar Plan' is not visible.", isAnyVisibleTextPresent("Cambiar Plan"));
	}

	private void validateDetallesDeLaCuentaSection() {
		assertTrue("'Cuenta creada' is not visible.", isAnyVisibleTextPresent("Cuenta creada"));
		assertTrue("'Estado activo' is not visible.", isAnyVisibleTextPresent("Estado activo"));
		assertTrue("'Idioma seleccionado' is not visible.", isAnyVisibleTextPresent("Idioma seleccionado"));
	}

	private void validateTusNegociosSection() {
		assertTrue("'Tus Negocios' section is not visible.", isAnyVisibleTextPresent("Tus Negocios"));
		assertTrue("Button 'Agregar Negocio' is not visible in Tus Negocios.", isAnyVisibleTextPresent("Agregar Negocio"));
		assertTrue("Text 'Tienes 2 de 3 negocios' is not visible in Tus Negocios.",
				isAnyVisibleTextPresent("Tienes 2 de 3 negocios"));

		final Optional<WebElement> negociosSection = findContainerByHeading("Tus Negocios");
		assertTrue("Business list is not visible in Tus Negocios section.",
				negociosSection.map(section -> section.getText().trim().length() > 30).orElse(Boolean.FALSE));
	}

	private void validateTermsAndConditions() throws IOException {
		termsUrl = openLegalLinkAndValidate(
				new String[] { "Terminos y Condiciones", "Términos y Condiciones" },
				new String[] { "Terminos y Condiciones", "Términos y Condiciones" }, "05-terminos-y-condiciones");
	}

	private void validatePrivacyPolicy() throws IOException {
		privacyUrl = openLegalLinkAndValidate(
				new String[] { "Politica de Privacidad", "Política de Privacidad" },
				new String[] { "Politica de Privacidad", "Política de Privacidad" }, "06-politica-de-privacidad");
	}

	private String openLegalLinkAndValidate(final String[] linkTexts, final String[] headingTexts, final String screenshotName)
			throws IOException {
		final String appWindow = driver.getWindowHandle();
		final String currentUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickVisibleText(linkTexts);

		final boolean openedNewTab = waitUntil(() -> driver.getWindowHandles().size() > handlesBefore.size(),
				Duration.ofSeconds(15));

		if (openedNewTab) {
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForUiToLoad();
		}

		assertTrue("Legal page heading is not visible.",
				waitUntil(() -> isAnyVisibleTextPresent(headingTexts), Duration.ofSeconds(30)));

		final String pageText = normalizedBodyText();
		assertTrue("Legal content text is not visible.", pageText.length() > 120);

		takeScreenshot(screenshotName);

		final String legalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else if (!legalUrl.equals(currentUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return legalUrl;
	}

	private void handleGoogleAccountSelection(final String appWindow, final Set<String> handlesBefore, final String accountEmail) {
		final boolean popupOpened = waitUntil(() -> driver.getWindowHandles().size() > handlesBefore.size(),
				Duration.ofSeconds(20));

		if (popupOpened) {
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForUiToLoad();

			if (isAnyVisibleTextPresent(accountEmail)) {
				clickVisibleText(accountEmail);
			}

			waitUntil(() -> driver.getWindowHandles().size() == handlesBefore.size(), Duration.ofSeconds(45));
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else if (isAnyVisibleTextPresent("Choose an account", "Selecciona una cuenta", accountEmail)) {
			if (isAnyVisibleTextPresent(accountEmail)) {
				clickVisibleText(accountEmail);
			}
			waitForUiToLoad();
		}
	}

	private void clickVisibleText(final String... texts) {
		RuntimeException lastError = null;
		for (final String text : texts) {
			final String xpath = "//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]";
			final List<WebElement> matches = driver.findElements(By.xpath(xpath));
			for (final WebElement match : matches) {
				if (!match.isDisplayed()) {
					continue;
				}

				final WebElement clickableTarget = resolveClickableTarget(match);
				try {
					wait.until(ExpectedConditions.elementToBeClickable(clickableTarget));
					clickableTarget.click();
					waitForUiToLoad();
					return;
				} catch (final Exception clickError) {
					try {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickableTarget);
						waitForUiToLoad();
						return;
					} catch (final Exception jsError) {
						lastError = new RuntimeException(
								"Failed to click visible text '" + text + "': " + jsError.getMessage(), jsError);
					}
				}
			}
		}

		throw new RuntimeException("No clickable visible element found for any of: " + Arrays.toString(texts), lastError);
	}

	private WebElement resolveClickableTarget(final WebElement element) {
		try {
			return element.findElement(By.xpath(
					"./ancestor-or-self::*[self::button or self::a or @role='button' or @onclick or self::label][1]"));
		} catch (final Exception ignored) {
			return element;
		}
	}

	private boolean isSidebarVisible() {
		final List<By> sidebarLocators = List.of(By.tagName("aside"),
				By.xpath("//nav[contains(normalize-space(.), 'Negocio') or contains(normalize-space(.), 'Mi Negocio')]"));

		for (final By locator : sidebarLocators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean isAnyVisibleTextPresent(final String... texts) {
		final String normalizedPageText = normalizedBodyText();
		for (final String text : texts) {
			if (normalizedPageText.contains(normalize(text))) {
				return true;
			}
		}
		return false;
	}

	private Optional<WebElement> findContainerByHeading(final String... headingTexts) {
		for (final String headingText : headingTexts) {
			final String headingXPath = "//*[contains(normalize-space(.), " + xpathLiteral(headingText)
					+ ")]/ancestor::*[self::section or self::article or self::div][1]";
			for (final WebElement section : driver.findElements(By.xpath(headingXPath))) {
				if (section.isDisplayed()) {
					return Optional.of(section);
				}
			}
		}
		return Optional.empty();
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path targetFile = evidenceDirectory.resolve(fileName + ".png");
		Files.copy(screenshot.toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING);
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));

		try {
			Thread.sleep(350);
		} catch (final InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean waitUntil(final BooleanSupplier condition, final Duration timeout) {
		final long timeoutAt = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < timeoutAt) {
			try {
				if (condition.getAsBoolean()) {
					return true;
				}
			} catch (final Exception ignored) {
				// Retry until timeout because dynamic pages often transiently fail while rendering.
			}

			try {
				Thread.sleep(250);
			} catch (final InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	private String normalizedBodyText() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		return normalize(bodyText);
	}

	private String normalize(final String value) {
		final String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
		return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
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

	private String buildFinalReport() {
		final StringBuilder report = new StringBuilder();
		report.append(System.lineSeparator()).append("SaleADS Mi Negocio Full Workflow Report")
				.append(System.lineSeparator()).append("Evidence directory: ").append(evidenceDirectory.toAbsolutePath())
				.append(System.lineSeparator()).append(System.lineSeparator());

		final List<String> orderedFields = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Términos y Condiciones", "Política de Privacidad");

		for (final String field : orderedFields) {
			final boolean passed = Boolean.TRUE.equals(stepResults.get(field));
			report.append(field).append(": ").append(passed ? "PASS" : "FAIL").append(System.lineSeparator());
		}

		if (!termsUrl.isBlank()) {
			report.append("Términos y Condiciones URL: ").append(termsUrl).append(System.lineSeparator());
		}
		if (!privacyUrl.isBlank()) {
			report.append("Política de Privacidad URL: ").append(privacyUrl).append(System.lineSeparator());
		}

		if (!failures.isEmpty()) {
			report.append(System.lineSeparator()).append("Failures:").append(System.lineSeparator());
			for (final String failure : failures) {
				report.append("- ").append(failure).append(System.lineSeparator());
			}
		}

		return report.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
