package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
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

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue(
				"SaleADS E2E is disabled. Enable with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true.",
				getBooleanSetting("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false));

		final ChromeOptions options = new ChromeOptions();
		if (getBooleanSetting("saleads.e2e.headless", "SALEADS_E2E_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);

		openLoginPage();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones", "08-terminos"));
		runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad", "09-politica-privacidad"));

		final Path reportPath = writeFinalReport();
		assertAllStepsPassed(reportPath);
	}

	private String stepLoginWithGoogle() throws Exception {
		clickByAnyText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Acceder con Google");
		maybeSelectGoogleAccount();

		Assert.assertFalse(
				"Still on Google authentication page after login.",
				driver.getCurrentUrl().toLowerCase(Locale.ROOT).contains("accounts.google"));
		requireVisibleText("Negocio");
		final Path screenshot = takeScreenshot("01-dashboard-loaded");
		return "Dashboard loaded. Screenshot: " + screenshot.toAbsolutePath();
	}

	private String stepOpenMiNegocioMenu() throws Exception {
		clickIfVisible("Negocio");
		clickByAnyText("Mi Negocio");

		requireVisibleText("Agregar Negocio");
		requireVisibleText("Administrar Negocios");
		final Path screenshot = takeScreenshot("02-mi-negocio-menu-expanded");
		return "Mi Negocio expanded. Screenshot: " + screenshot.toAbsolutePath();
	}

	private String stepValidateAgregarNegocioModal() throws Exception {
		clickByAnyText("Agregar Negocio");

		requireVisibleText("Crear Nuevo Negocio");
		final WebElement businessNameInput = findBusinessNameInput();
		Assert.assertNotNull("Input field 'Nombre del Negocio' was not found.", businessNameInput);
		requireVisibleText("Tienes 2 de 3 negocios");
		requireVisibleText("Cancelar");
		requireVisibleText("Crear Negocio");
		final Path screenshot = takeScreenshot("03-agregar-negocio-modal");

		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		clickByAnyText("Cancelar");

		return "Agregar Negocio modal validated. Screenshot: " + screenshot.toAbsolutePath();
	}

	private String stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			clickByAnyText("Mi Negocio");
		}

		clickByAnyText("Administrar Negocios");
		requireVisibleText("Información General");
		requireVisibleText("Detalles de la Cuenta");
		requireVisibleText("Tus Negocios");
		requireVisibleText("Sección Legal");
		final Path screenshot = takeScreenshot("04-administrar-negocios");
		return "Administrar Negocios loaded. Screenshot: " + screenshot.toAbsolutePath();
	}

	private String stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		final String sectionText = normalizeWhitespace(section.getText());
		Assert.assertTrue("User email is not visible in 'Información General'.", EMAIL_PATTERN.matcher(sectionText).find());
		Assert.assertTrue("User name is not clearly visible in 'Información General'.", hasLikelyDisplayName(sectionText));
		Assert.assertTrue("Text 'BUSINESS PLAN' is not visible.", sectionText.contains("BUSINESS PLAN"));
		Assert.assertTrue("Button 'Cambiar Plan' is not visible.", isTextVisible("Cambiar Plan", SHORT_TIMEOUT));
		return "Información General validated.";
	}

	private String stepValidateDetallesDeLaCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		final String sectionText = normalizeWhitespace(section.getText()).toLowerCase(Locale.ROOT);
		Assert.assertTrue("'Cuenta creada' is not visible.", sectionText.contains("cuenta creada"));
		Assert.assertTrue("'Estado activo' is not visible.", sectionText.contains("estado activo")
				|| (sectionText.contains("estado") && sectionText.contains("activo")));
		Assert.assertTrue("'Idioma seleccionado' is not visible.", sectionText.contains("idioma seleccionado"));
		return "Detalles de la Cuenta validated.";
	}

	private String stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final String sectionText = normalizeWhitespace(section.getText());
		Assert.assertTrue("Business list is not visible in 'Tus Negocios'.", hasBusinessEntries(section, sectionText));
		Assert.assertTrue("Button 'Agregar Negocio' is not visible.", sectionText.contains("Agregar Negocio"));
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' is not visible.", sectionText.contains("Tienes 2 de 3 negocios"));
		return "Tus Negocios validated.";
	}

	private String stepValidateLegalLink(final String legalLinkText, final String screenshotName) throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> previousHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String originalUrl = driver.getCurrentUrl();

		clickByAnyText(legalLinkText);
		final String legalHandle = waitForLegalWindow(previousHandles, originalHandle, originalUrl);
		driver.switchTo().window(legalHandle);
		waitForUiToLoad();

		requireVisibleText(legalLinkText);
		final String bodyText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		Assert.assertTrue("Legal content text is not visible for '" + legalLinkText + "'.", bodyText.length() > 120);

		final String finalUrl = driver.getCurrentUrl();
		final Path screenshot = takeScreenshot(screenshotName);

		if (!legalHandle.equals(originalHandle)) {
			driver.close();
		}
		driver.switchTo().window(originalHandle);
		waitForUiToLoad();
		if (legalHandle.equals(originalHandle)) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return "Validated URL: " + finalUrl + " | Screenshot: " + screenshot.toAbsolutePath();
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			final String detail = action.execute();
			stepResults.put(reportField, new StepResult(true, detail == null ? "" : detail));
		} catch (final Throwable throwable) {
			final Path screenshot = takeScreenshot("failed-" + sanitizeFilename(reportField));
			final String detail = throwable.getClass().getSimpleName() + ": " + safeMessage(throwable)
					+ " | Failure screenshot: " + screenshot.toAbsolutePath();
			stepResults.put(reportField, new StepResult(false, detail));
		}
	}

	private void openLoginPage() {
		final String loginUrl = getStringSetting("saleads.login.url", "SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl.trim());
			waitForUiToLoad();
		}

		if ("about:blank".equalsIgnoreCase(driver.getCurrentUrl())) {
			Assert.fail("No login page available. Provide -Dsaleads.login.url or SALEADS_LOGIN_URL.");
		}
	}

	private void maybeSelectGoogleAccount() {
		final Optional<WebElement> accountOption = findClickableByText(GOOGLE_ACCOUNT_EMAIL, Duration.ofSeconds(12));
		if (accountOption.isPresent()) {
			clickElement(accountOption.get());
			waitForUiToLoad();
		}
	}

	private Optional<WebElement> findClickableByText(final String text, final Duration timeout) {
		final List<By> locators = clickableLocators(text);
		final long deadline = System.currentTimeMillis() + timeout.toMillis();

		while (System.currentTimeMillis() < deadline) {
			for (final By locator : locators) {
				for (final WebElement element : driver.findElements(locator)) {
					try {
						if (element.isDisplayed() && element.isEnabled()) {
							return Optional.of(element);
						}
					} catch (final StaleElementReferenceException ignored) {
						// ignore stale element and continue scanning.
					}
				}
			}
			sleepSilently(250);
		}
		return Optional.empty();
	}

	private void clickByAnyText(final String... textCandidates) {
		final List<String> misses = new ArrayList<>();
		for (final String text : textCandidates) {
			final Optional<WebElement> element = findClickableByText(text, SHORT_TIMEOUT);
			if (element.isPresent()) {
				clickElement(element.get());
				waitForUiToLoad();
				return;
			}
			misses.add(text);
		}
		throw new NoSuchElementException("Could not find clickable element by visible text: " + misses);
	}

	private void clickIfVisible(final String text) {
		final Optional<WebElement> element = findClickableByText(text, Duration.ofSeconds(2));
		if (element.isPresent()) {
			clickElement(element.get());
			waitForUiToLoad();
		}
	}

	private void clickElement(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private List<By> clickableLocators(final String text) {
		final String literal = xpathLiteral(text);
		return Arrays.asList(
				By.xpath("//button[contains(normalize-space(.), " + literal + ")]"),
				By.xpath("//a[contains(normalize-space(.), " + literal + ")]"),
				By.xpath("//*[@role='button' and contains(normalize-space(.), " + literal + ")]"),
				By.xpath("//*[contains(@class, 'button') and contains(normalize-space(.), " + literal + ")]"),
				By.xpath("//label[contains(normalize-space(.), " + literal + ")]"));
	}

	private WebElement requireVisibleText(final String text) {
		final String literal = xpathLiteral(text);
		final By locator = By.xpath(
				"//*[not(self::html or self::body or self::script or self::style) and contains(normalize-space(.), "
						+ literal + ")]");
		return wait.until(driverInstance -> {
			for (final WebElement element : driverInstance.findElements(locator)) {
				try {
					if (element.isDisplayed()) {
						return element;
					}
				} catch (final StaleElementReferenceException ignored) {
					// retry while waiting.
				}
			}
			return null;
		});
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			final String literal = xpathLiteral(text);
			final By locator = By.xpath(
					"//*[not(self::html or self::body or self::script or self::style) and contains(normalize-space(.), "
							+ literal + ")]");
			new WebDriverWait(driver, timeout).until(d -> {
				for (final WebElement element : d.findElements(locator)) {
					if (element.isDisplayed()) {
						return true;
					}
				}
				return false;
			});
			return true;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private WebElement findBusinessNameInput() {
		final List<By> locators = Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));

		for (final By locator : locators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		return null;
	}

	private WebElement findSectionByHeading(final String headingText) {
		final String literal = xpathLiteral(headingText);
		final By locator = By.xpath(
				"(//*[self::section or self::article or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5][contains(normalize-space(.), "
						+ literal + ")]])[1]");
		return wait.until(driverInstance -> {
			for (final WebElement section : driverInstance.findElements(locator)) {
				if (section.isDisplayed()) {
					return section;
				}
			}
			return null;
		});
	}

	private boolean hasLikelyDisplayName(final String text) {
		final List<String> ignoredTokens = Arrays.asList(
				"información general", "business plan", "cambiar plan", "cuenta creada", "estado activo",
				"idioma seleccionado", "sección legal", "tienes 2 de 3 negocios");
		for (final String line : text.split("\\R")) {
			final String trimmed = line.trim();
			final String lower = trimmed.toLowerCase(Locale.ROOT);
			if (trimmed.length() < 3 || trimmed.length() > 60) {
				continue;
			}
			if (!trimmed.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*") || trimmed.contains("@")) {
				continue;
			}
			boolean ignored = false;
			for (final String token : ignoredTokens) {
				if (lower.contains(token)) {
					ignored = true;
					break;
				}
			}
			if (!ignored) {
				return true;
			}
		}
		return false;
	}

	private boolean hasBusinessEntries(final WebElement section, final String sectionText) {
		final List<WebElement> listEntries = section.findElements(
				By.xpath(".//li[normalize-space()] | .//tr[normalize-space()] | .//*[contains(@class, 'business')]"));
		if (!listEntries.isEmpty()) {
			return true;
		}
		int meaningfulLines = 0;
		for (final String line : sectionText.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.equalsIgnoreCase("Tus Negocios") || trimmed.equalsIgnoreCase("Agregar Negocio")
					|| trimmed.equalsIgnoreCase("Tienes 2 de 3 negocios")) {
				continue;
			}
			meaningfulLines++;
		}
		return meaningfulLines > 0;
	}

	private String waitForLegalWindow(final Set<String> previousHandles, final String originalHandle,
			final String originalUrl) {
		final ExpectedCondition<String> waitCondition = driverInstance -> {
			final Set<String> handles = driverInstance.getWindowHandles();
			for (final String handle : handles) {
				if (!previousHandles.contains(handle)) {
					return handle;
				}
			}
			final String currentUrl = driverInstance.getCurrentUrl();
			if (!normalizeWhitespace(currentUrl).equals(normalizeWhitespace(originalUrl))) {
				return originalHandle;
			}
			return null;
		};
		return new WebDriverWait(driver, Duration.ofSeconds(15)).until(waitCondition);
	}

	private void waitForUiToLoad() {
		wait.until(driverInstance -> {
			final Object readyState = ((JavascriptExecutor) driverInstance).executeScript("return document.readyState");
			return "complete".equals(readyState);
		});
		sleepSilently(600);
	}

	private Path takeScreenshot(final String checkpointName) {
		if (driver == null) {
			return Paths.get("screenshot-unavailable-driver-null");
		}
		try {
			final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")) + "-"
					+ sanitizeFilename(checkpointName) + ".png";
			final Path target = evidenceDir.resolve(name);
			Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
			return target;
		} catch (final Exception error) {
			return Paths.get("screenshot-unavailable-" + sanitizeFilename(error.getClass().getSimpleName()));
		}
	}

	private Path writeFinalReport() throws IOException {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio Workflow Final Report").append(System.lineSeparator());
		reportBuilder.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		reportBuilder.append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			final StepResult result = stepResults.getOrDefault(field, new StepResult(false, "Step was not executed."));
			reportBuilder.append(field).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (!result.detail.isBlank()) {
				reportBuilder.append(" - ").append(result.detail);
			}
			reportBuilder.append(System.lineSeparator());
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, reportBuilder.toString());
		System.out.println(reportBuilder);
		System.out.println("Final report path: " + reportPath.toAbsolutePath());
		return reportPath;
	}

	private void assertAllStepsPassed(final Path reportPath) {
		final List<String> failures = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			final StepResult result = stepResults.get(field);
			if (result == null || !result.passed) {
				failures.add(field + " -> " + (result == null ? "No result recorded" : result.detail));
			}
		}

		Assert.assertTrue("One or more workflow validations failed. See report: " + reportPath.toAbsolutePath()
				+ System.lineSeparator() + String.join(System.lineSeparator(), failures), failures.isEmpty());
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private String sanitizeFilename(final String raw) {
		return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]+", "-").replaceAll("-{2,}", "-")
				.replaceAll("^-|-$", "");
	}

	private String getStringSetting(final String propertyKey, final String envKey) {
		final String fromProperty = System.getProperty(propertyKey);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}
		final String fromEnv = System.getenv(envKey);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}
		return null;
	}

	private boolean getBooleanSetting(final String propertyKey, final String envKey, final boolean defaultValue) {
		final String raw = getStringSetting(propertyKey, envKey);
		if (raw == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(raw);
	}

	private String normalizeWhitespace(final String raw) {
		return raw == null ? "" : raw.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
	}

	private String safeMessage(final Throwable throwable) {
		return throwable.getMessage() == null ? "(no message)" : normalizeWhitespace(throwable.getMessage());
	}

	private void sleepSilently(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		String execute() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}
	}
}
