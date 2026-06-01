package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> reportByField = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;
	private String termsAndConditionsUrl = "N/A";
	private String privacyPolicyUrl = "N/A";

	@Before
	public void setUp() throws Exception {
		final boolean e2eEnabled = Boolean.parseBoolean(getConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("SaleADS E2E test disabled. Enable with -Dsaleads.e2e.enabled=true.", e2eEnabled);

		evidenceDir = Path.of("target", "saleads-e2e");
		Files.createDirectories(evidenceDir);
		Files.createDirectories(evidenceDir.resolve("screenshots"));

		for (final String field : REPORT_FIELDS) {
			reportByField.put(field, StepResult.notRun());
		}

		final ChromeOptions options = new ChromeOptions();
		final boolean headed = Boolean.parseBoolean(getConfig("saleads.e2e.headed", "SALEADS_E2E_HEADED", "false"));
		if (!headed) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String loginUrl = getRequiredConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		final String googleAccount = getConfig("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);

		runStep("Login", () -> {
			driver.get(loginUrl);
			waitForUiToLoad();
			appWindowHandle = driver.getWindowHandle();

			final Set<String> handlesBeforeLoginClick = driver.getWindowHandles();
			clickFirstVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
					"Ingresar con Google", "Login with Google"));
			waitForUiToLoad();
			selectGoogleAccountIfPresent(handlesBeforeLoginClick, googleAccount);

			waitForAnyVisible(Arrays.asList(By.cssSelector("aside"), By.xpath("//nav"), byText("Mi Negocio"), byText("Negocio")));
			assertTrue("Left sidebar navigation is not visible.", isAnyVisible(
					Arrays.asList(By.cssSelector("aside"), By.xpath("//nav"), By.xpath("//div[contains(@class, 'sidebar')]")),
					Duration.ofSeconds(10)));
			captureScreenshot("01-dashboard-loaded.png");
		});

		runStep("Mi Negocio menu", () -> {
			expandMiNegocioMenu();
			assertVisibleText("Agregar Negocio");
			assertVisibleText("Administrar Negocios");
			captureScreenshot("02-mi-negocio-menu-expanded.png");
		});

		runStep("Agregar Negocio modal", () -> {
			clickFirstVisibleText(Arrays.asList("Agregar Negocio"));
			waitForUiToLoad();

			assertVisibleText("Crear Nuevo Negocio");
			assertTrue("'Nombre del Negocio' input field does not exist.",
					isAnyVisible(Arrays.asList(
							By.xpath("//label[normalize-space()='Nombre del Negocio']/following::input[1]"),
							By.xpath("//input[@placeholder='Nombre del Negocio']"), byText("Nombre del Negocio")),
							Duration.ofSeconds(10)));
			assertVisibleText("Tienes 2 de 3 negocios");
			assertVisibleText("Cancelar");
			assertVisibleText("Crear Negocio");
			captureScreenshot("03-crear-nuevo-negocio-modal.png");

			fillBusinessNameAndCancel("Negocio Prueba Automatización");
			waitForUiToLoad();
		});

		runStep("Administrar Negocios view", () -> {
			expandMiNegocioMenu();
			clickFirstVisibleText(Arrays.asList("Administrar Negocios"));
			waitForUiToLoad();

			assertVisibleText("Información General");
			assertVisibleText("Detalles de la Cuenta");
			assertVisibleText("Tus Negocios");
			assertVisibleText("Sección Legal");
			captureScreenshot("04-administrar-negocios.png");
		});

		runStep("Información General", () -> {
			final WebElement section = findSectionContainer("Información General");
			assertTrue("User name is not visible in 'Información General'.", containsLikelyUserName(section.getText()));
			assertTrue("User email is not visible in 'Información General'.", containsEmail(section.getText()));
			assertSectionContainsText(section, "BUSINESS PLAN");
			assertSectionContainsText(section, "Cambiar Plan");
		});

		runStep("Detalles de la Cuenta", () -> {
			final WebElement section = findSectionContainer("Detalles de la Cuenta");
			assertSectionContainsText(section, "Cuenta creada");
			assertSectionContainsText(section, "Estado activo");
			assertSectionContainsText(section, "Idioma seleccionado");
		});

		runStep("Tus Negocios", () -> {
			final WebElement section = findSectionContainer("Tus Negocios");
			assertTrue("Business list is not visible in 'Tus Negocios'.", containsBusinessListContent(section.getText()));
			assertSectionContainsText(section, "Agregar Negocio");
			assertSectionContainsText(section, "Tienes 2 de 3 negocios");
		});

		runStep("Términos y Condiciones", () -> {
			termsAndConditionsUrl = openLegalLinkAndCaptureEvidence("Términos y Condiciones", "Términos y Condiciones",
					"05-terminos-y-condiciones.png");
		});

		runStep("Política de Privacidad", () -> {
			privacyPolicyUrl = openLegalLinkAndCaptureEvidence("Política de Privacidad", "Política de Privacidad",
					"06-politica-de-privacidad.png");
		});

		writeFinalReport();
		assertAllStepsPassed();
	}

	private void runStep(final String reportField, final ThrowingRunnable stepAction) {
		Objects.requireNonNull(reportField, "reportField");
		Objects.requireNonNull(stepAction, "stepAction");

		try {
			stepAction.run();
			reportByField.put(reportField, StepResult.pass("Validation successful."));
		} catch (final Exception ex) {
			reportByField.put(reportField, StepResult.fail(ex.getMessage()));
			captureScreenshot("error-" + fileSafeName(reportField) + ".png");
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failingSteps = new ArrayList<>();

		for (final Map.Entry<String, StepResult> entry : reportByField.entrySet()) {
			if (!"PASS".equals(entry.getValue().status)) {
				failingSteps.add(entry.getKey() + " (" + entry.getValue().status + ": " + entry.getValue().details + ")");
			}
		}

		assertTrue("Some validations failed:\n - " + String.join("\n - ", failingSteps), failingSteps.isEmpty());
	}

	private void expandMiNegocioMenu() {
		if (isTextVisible("Agregar Negocio", Duration.ofSeconds(2))
				&& isTextVisible("Administrar Negocios", Duration.ofSeconds(2))) {
			return;
		}

		clickIfVisibleText("Negocio", Duration.ofSeconds(3));
		waitForUiToLoad();
		clickIfVisibleText("Mi Negocio", Duration.ofSeconds(3));
		waitForUiToLoad();

		if (!isTextVisible("Agregar Negocio", Duration.ofSeconds(3))) {
			clickIfVisibleText("Mi Negocio", Duration.ofSeconds(3));
			waitForUiToLoad();
		}
	}

	private void fillBusinessNameAndCancel(final String businessName) {
		final Optional<WebElement> input = findFirstVisible(
				Arrays.asList(By.xpath("//label[normalize-space()='Nombre del Negocio']/following::input[1]"),
						By.xpath("//input[@placeholder='Nombre del Negocio']")),
				Duration.ofSeconds(5));

		input.ifPresent(element -> {
			element.click();
			element.clear();
			element.sendKeys(businessName);
		});

		clickIfVisibleText("Cancelar", Duration.ofSeconds(5));
	}

	private String openLegalLinkAndCaptureEvidence(final String linkText, final String expectedHeading,
			final String screenshotName) {
		waitForUiToLoad();
		assertVisibleText("Sección Legal");

		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickFirstVisibleText(Arrays.asList(linkText));
		waitForUiToLoad();

		final String newHandle = waitForNewWindowHandle(handlesBeforeClick, Duration.ofSeconds(10));
		final boolean openedNewTab = newHandle != null;
		if (openedNewTab) {
			driver.switchTo().window(newHandle);
		}

		waitForUiToLoad();
		assertVisibleText(expectedHeading);
		assertTrue("Legal content text is not visible for '" + linkText + "'.", hasLegalContent(driver));

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else if (!isTextVisible("Sección Legal", Duration.ofSeconds(3))) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}

		return finalUrl;
	}

	private boolean hasLegalContent(final WebDriver activeDriver) {
		final String bodyText = activeDriver.findElement(By.tagName("body")).getText();
		return bodyText != null && bodyText.trim().length() > 120;
	}

	private WebElement findSectionContainer(final String sectionTitle) {
		assertVisibleText(sectionTitle);

		final String sectionLiteral = xpathLiteral(sectionTitle);
		final By sectionBy = By.xpath(
				"(//*[self::section or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4][normalize-space()="
						+ sectionLiteral + "]]"
						+ "|//*[self::section or self::div][.//*[normalize-space()=" + sectionLiteral + "]])[1]");

		return wait.until(ExpectedConditions.visibilityOfElementLocated(sectionBy));
	}

	private boolean containsBusinessListContent(final String sectionText) {
		if (sectionText == null) {
			return false;
		}

		final String normalized = sectionText.toLowerCase();
		return normalized.contains("negocio")
				&& normalized.replace("tus negocios", "").replace("agregar negocio", "").trim().length() > 25;
	}

	private boolean containsLikelyUserName(final String sectionText) {
		if (sectionText == null || sectionText.isBlank()) {
			return false;
		}

		final String[] lines = sectionText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}

			final String lowerLine = line.toLowerCase();
			if (lowerLine.contains("información general") || lowerLine.contains("business plan")
					|| lowerLine.contains("cambiar plan") || line.contains("@")) {
				continue;
			}

			if (line.length() >= 3 && line.chars().anyMatch(Character::isLetter)) {
				return true;
			}
		}

		return false;
	}

	private boolean containsEmail(final String sectionText) {
		if (sectionText == null) {
			return false;
		}
		return sectionText.matches("(?s).*\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b.*");
	}

	private void assertSectionContainsText(final WebElement section, final String expectedText) {
		final String sectionText = section.getText();
		assertTrue("Expected text '" + expectedText + "' was not found in section.\nActual text:\n" + sectionText,
				sectionText != null && sectionText.contains(expectedText));
	}

	private void clickFirstVisibleText(final List<String> candidates) {
		for (final String candidate : candidates) {
			final Optional<WebElement> element = findFirstVisible(
					Arrays.asList(clickableByText(candidate), byText(candidate)), Duration.ofSeconds(4));
			if (element.isPresent()) {
				clickElement(element.get());
				return;
			}
		}

		throw new AssertionError("Could not click any visible element with text candidates: " + candidates);
	}

	private void clickIfVisibleText(final String text, final Duration timeout) {
		final Optional<WebElement> element = findFirstVisible(Arrays.asList(clickableByText(text), byText(text)), timeout);
		element.ifPresent(this::clickElement);
	}

	private void clickElement(final WebElement element) {
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void selectGoogleAccountIfPresent(final Set<String> handlesBeforeClick, final String accountEmail) {
		final String popupHandle = waitForNewWindowHandle(handlesBeforeClick, Duration.ofSeconds(8));
		if (popupHandle != null) {
			driver.switchTo().window(popupHandle);
			waitForUiToLoad();
		}

		clickIfVisibleText(accountEmail, Duration.ofSeconds(8));
		waitForUiToLoad();

		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private String waitForNewWindowHandle(final Set<String> oldHandles, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(driver -> driver.getWindowHandles().size() > oldHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!oldHandles.contains(handle)) {
					return handle;
				}
			}
			return null;
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private Optional<WebElement> findFirstVisible(final List<By> locators, final Duration timeout) {
		for (final By locator : locators) {
			try {
				final WebElement element = new WebDriverWait(driver, timeout)
						.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return Optional.of(element);
			} catch (final TimeoutException timeoutException) {
				// try next candidate locator
			}
		}
		return Optional.empty();
	}

	private boolean isAnyVisible(final List<By> locators, final Duration timeout) {
		return findFirstVisible(locators, timeout).isPresent();
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		return findFirstVisible(Arrays.asList(byText(text)), timeout).isPresent();
	}

	private void waitForAnyVisible(final List<By> locators) {
		assertTrue("None of the expected elements became visible: " + locators, isAnyVisible(locators, WAIT_TIMEOUT));
	}

	private void assertVisibleText(final String text) {
		final By by = byText(text);
		wait.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private By byText(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath("//*[normalize-space()=" + literal + " or .//*[normalize-space()=" + literal + "]]");
	}

	private By clickableByText(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath("(//button[normalize-space()=" + literal + " or .//*[normalize-space()=" + literal + "]]"
				+ "|//a[normalize-space()=" + literal + " or .//*[normalize-space()=" + literal + "]]"
				+ "|//*[@role='button' and (normalize-space()=" + literal + " or .//*[normalize-space()=" + literal + "])]"
				+ "|//*[normalize-space()=" + literal + "])[1]");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder concatExpression = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String current = String.valueOf(chars[i]);
			if ("'".equals(current)) {
				concatExpression.append("\"'\"");
			} else if ("\"".equals(current)) {
				concatExpression.append("'\"'");
			} else {
				concatExpression.append("'").append(current).append("'");
			}
			if (i < chars.length - 1) {
				concatExpression.append(", ");
			}
		}
		concatExpression.append(")");
		return concatExpression.toString();
	}

	private void waitForUiToLoad() {
		final ExpectedCondition<Boolean> documentReady = webDriver -> {
			final Object state = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
			return state != null && "complete".equals(state.toString());
		};
		wait.until(documentReady);
		sleepMillis(350);
	}

	private void sleepMillis(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for UI load.", interruptedException);
		}
	}

	private void captureScreenshot(final String filename) {
		if (driver == null) {
			return;
		}

		try {
			final byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			final Path destination = evidenceDir.resolve("screenshots").resolve(filename);
			Files.copy(new java.io.ByteArrayInputStream(screenshotBytes), destination, StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException ioException) {
			throw new UncheckedIOException("Could not save screenshot: " + filename, ioException);
		}
	}

	private String fileSafeName(final String value) {
		return value.toLowerCase().replace(' ', '-').replace("ó", "o").replace("í", "i").replace("á", "a")
				.replace("é", "e").replace("ú", "u").replace("ñ", "n");
	}

	private void writeFinalReport() {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio Workflow - Final Report").append(System.lineSeparator());
		reportBuilder.append("========================================").append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			final StepResult result = reportByField.getOrDefault(field, StepResult.notRun());
			reportBuilder.append("- ").append(field).append(": ").append(result.status);
			if (result.details != null && !result.details.isBlank()) {
				reportBuilder.append(" (").append(result.details).append(")");
			}
			reportBuilder.append(System.lineSeparator());
		}

		reportBuilder.append(System.lineSeparator());
		reportBuilder.append("Términos y Condiciones URL: ").append(termsAndConditionsUrl).append(System.lineSeparator());
		reportBuilder.append("Política de Privacidad URL: ").append(privacyPolicyUrl).append(System.lineSeparator());
		reportBuilder.append("Screenshots directory: ").append(evidenceDir.resolve("screenshots")).append(System.lineSeparator());

		try {
			Files.writeString(evidenceDir.resolve("report.txt"), reportBuilder.toString(), StandardCharsets.UTF_8);
		} catch (final IOException ioException) {
			throw new UncheckedIOException("Could not write final report.", ioException);
		}
	}

	private String getConfig(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private String getRequiredConfig(final String propertyName, final String envName) {
		final String value = getConfig(propertyName, envName, "");
		assertTrue("Missing required configuration. Provide -D" + propertyName + " or env var " + envName + ".",
				value != null && !value.isBlank());
		return value;
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static class StepResult {
		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult("PASS", details);
		}

		private static StepResult fail(final String details) {
			return new StepResult("FAIL", details == null ? "Validation failed." : details);
		}

		private static StepResult notRun() {
			return new StepResult("NOT_RUN", "Step did not execute.");
		}
	}
}
