package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(10);
	private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

	private static final String FIELD_LOGIN = "Login";
	private static final String FIELD_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String FIELD_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String FIELD_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String FIELD_INFORMACION_GENERAL = "Información General";
	private static final String FIELD_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String FIELD_TUS_NEGOCIOS = "Tus Negocios";
	private static final String FIELD_TERMINOS = "Términos y Condiciones";
	private static final String FIELD_PRIVACIDAD = "Política de Privacidad";

	private static final List<String> REPORT_FIELDS = List.of(
			FIELD_LOGIN,
			FIELD_MI_NEGOCIO_MENU,
			FIELD_AGREGAR_NEGOCIO_MODAL,
			FIELD_ADMINISTRAR_NEGOCIOS,
			FIELD_INFORMACION_GENERAL,
			FIELD_DETALLES_CUENTA,
			FIELD_TUS_NEGOCIOS,
			FIELD_TERMINOS,
			FIELD_PRIVACIDAD);

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor js;
	private String appWindowHandle;
	private Path screenshotDir;
	private Path reportFile;
	private final Map<String, StepResult> results = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this test.",
				Boolean.parseBoolean(config("SALEADS_E2E_ENABLED", "false")));

		final String entryUrl = config("SALEADS_ENTRY_URL", "").trim();
		Assume.assumeTrue("Set SALEADS_ENTRY_URL to the SaleADS login page for the target environment.",
				!entryUrl.isEmpty());

		final String runId = RUN_ID_FORMAT.format(Instant.now());
		screenshotDir = Paths.get("target", "e2e-screenshots", TEST_NAME, runId);
		Files.createDirectories(screenshotDir);
		final Path reportDir = Paths.get("target", "e2e-reports");
		Files.createDirectories(reportDir);
		reportFile = reportDir.resolve(TEST_NAME + "-" + runId + ".txt");

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		js = (JavascriptExecutor) driver;
		driver.manage().window().maximize();
		driver.get(entryUrl);
		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();

		for (final String field : REPORT_FIELDS) {
			results.put(field, new StepResult(false, "Not executed"));
		}
	}

	@After
	public void tearDown() throws IOException {
		try {
			if (reportFile != null) {
				writeFinalReport();
			}
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() throws IOException {
		final boolean loginOk = runStep(FIELD_LOGIN, this::stepLoginWithGoogle);
		final boolean menuOk = runStep(FIELD_MI_NEGOCIO_MENU, () -> requireThen(loginOk, this::stepOpenMiNegocioMenu));
		final boolean modalOk = runStep(FIELD_AGREGAR_NEGOCIO_MODAL,
				() -> requireThen(loginOk && menuOk, this::stepValidateAgregarNegocioModal));
		final boolean administrarOk = runStep(FIELD_ADMINISTRAR_NEGOCIOS,
				() -> requireThen(loginOk, this::stepOpenAdministrarNegocios));
		final boolean infoOk = runStep(FIELD_INFORMACION_GENERAL,
				() -> requireThen(administrarOk, this::stepValidateInformacionGeneral));
		final boolean detallesOk = runStep(FIELD_DETALLES_CUENTA,
				() -> requireThen(administrarOk, this::stepValidateDetallesCuenta));
		final boolean negociosOk = runStep(FIELD_TUS_NEGOCIOS,
				() -> requireThen(administrarOk, this::stepValidateTusNegocios));
		final boolean terminosOk = runStep(FIELD_TERMINOS,
				() -> requireThen(administrarOk, this::stepValidateTerminosYCondiciones));
		final boolean privacidadOk = runStep(FIELD_PRIVACIDAD,
				() -> requireThen(administrarOk, this::stepValidatePoliticaPrivacidad));

		final boolean allPassed = loginOk
				&& menuOk
				&& modalOk
				&& administrarOk
				&& infoOk
				&& detallesOk
				&& negociosOk
				&& terminosOk
				&& privacidadOk;

		final String report = buildFinalReport();
		System.out.println(report);
		Assert.assertTrue("One or more workflow validations failed.\n" + report, allPassed);
	}

	private void stepLoginWithGoogle() throws IOException {
		final Set<String> beforeHandles = driver.getWindowHandles();
		clickAnyByText(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Continuar con Google",
				"Acceder con Google",
				"Login with Google");

		waitForUiLoad();
		switchToGoogleWindowIfOpened(beforeHandles);
		selectGoogleAccountIfPrompted("juanlucasbarbiergarzon@gmail.com");
		switchToAppWindow();

		Assert.assertTrue("Main application interface did not appear after login.", waitForAnyTextVisible(
				SHORT_TIMEOUT, "Dashboard", "Inicio", "Negocio", "Mi Negocio"));

		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("(//aside | //nav)[1]")));
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("(//aside | //nav)[1]")));
		clickIfVisible("Negocio", Duration.ofSeconds(5));
		clickAnyByText("Mi Negocio");

		waitForTextVisible("Agregar Negocio");
		waitForTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded-menu");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickAnyByText("Agregar Negocio");
		waitForTextVisible("Crear Nuevo Negocio");
		waitForTextVisible("Nombre del Negocio");
		waitForTextVisible("Tienes 2 de 3 negocios");
		waitForTextVisible("Cancelar");
		waitForTextVisible("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//input[@placeholder='Nombre del Negocio' or @name='Nombre del Negocio' or @name='businessName' or @id='businessName' or @aria-label='Nombre del Negocio']")));
		input.click();
		waitForUiLoad();
		input.sendKeys("Negocio Prueba Automatización");
		clickAnyByText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']")));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureMiNegocioMenuExpanded();
		clickAnyByText("Administrar Negocios");
		waitForTextVisible("Información General");
		waitForTextVisible("Detalles de la Cuenta");
		waitForTextVisible("Tus Negocios");
		waitForTextVisible("Sección Legal");
		captureScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		waitForTextVisible("Información General");
		Assert.assertTrue("Expected at least one email in Información General.",
				waitForRegexInBody("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));
		waitForTextVisible("BUSINESS PLAN");
		waitForTextVisible("Cambiar Plan");
		Assert.assertTrue("Could not detect a likely user name in Información General section.",
				waitForLikelyUserName());
	}

	private void stepValidateDetallesCuenta() {
		waitForTextVisible("Cuenta creada");
		waitForTextVisible("Estado activo");
		waitForTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitForTextVisible("Tus Negocios");
		waitForTextVisible("Agregar Negocio");
		waitForTextVisible("Tienes 2 de 3 negocios");
		Assert.assertTrue("Business list was not detected in Tus Negocios.",
				wait.until(d -> !d.findElements(By.xpath("//*[contains(@class,'business') or contains(@class,'negocio')]")).isEmpty()
						|| !d.findElements(By.xpath("//*[contains(normalize-space(),'Negocio')]")).isEmpty()));
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		final LegalValidationResult result = validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "05-terminos");
		Assert.assertTrue(result.message, result.passed);
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		final LegalValidationResult result = validateLegalLink("Política de Privacidad", "Política de Privacidad", "06-privacidad");
		Assert.assertTrue(result.message, result.passed);
	}

	private LegalValidationResult validateLegalLink(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final Set<String> beforeHandles = driver.getWindowHandles();
		final String startingHandle = driver.getWindowHandle();
		final String startingUrl = driver.getCurrentUrl();

		boolean openedNewTab = false;
		try {
			clickAnyByText(linkText);

			try {
				new WebDriverWait(driver, SHORT_TIMEOUT).until(d -> d.getWindowHandles().size() > beforeHandles.size());
				final Set<String> afterHandles = new LinkedHashSet<>(driver.getWindowHandles());
				afterHandles.removeAll(beforeHandles);
				if (!afterHandles.isEmpty()) {
					final String newHandle = afterHandles.iterator().next();
					driver.switchTo().window(newHandle);
					waitForUiLoad();
					openedNewTab = true;
				}
			} catch (final Exception ignored) {
				// Same-tab navigation is valid for this workflow.
			}

			waitForTextVisible(headingText);
			final String bodyText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
			final boolean hasLegalContent = bodyText != null && bodyText.trim().length() > 120;
			if (!hasLegalContent) {
				return new LegalValidationResult(false, "Legal content for '" + headingText + "' is too short or missing.");
			}

			captureScreenshot(screenshotName);
			final String finalUrl = driver.getCurrentUrl();
			System.out.println(headingText + " final URL: " + finalUrl);
			return new LegalValidationResult(true, headingText + " validated at URL: " + finalUrl);
		} finally {
			// Always return to the application window, even when assertions fail.
			try {
				if (openedNewTab && !driver.getWindowHandle().equals(startingHandle)) {
					driver.close();
					if (driver.getWindowHandles().contains(startingHandle)) {
						driver.switchTo().window(startingHandle);
					} else if (!driver.getWindowHandles().isEmpty()) {
						driver.switchTo().window(driver.getWindowHandles().iterator().next());
					}
					waitForUiLoad();
				} else if (driver.getWindowHandles().contains(startingHandle)) {
					driver.switchTo().window(startingHandle);
					if (!Objects.equals(driver.getCurrentUrl(), startingUrl)) {
						driver.navigate().back();
						waitForUiLoad();
					}
				}
			} catch (final Exception ignored) {
				// Cleanup should be best effort and must not mask step failures.
			}
			appWindowHandle = startingHandle;
		}
	}

	private WebDriver createDriver() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (Boolean.parseBoolean(config("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = config("SELENIUM_REMOTE_URL", "").trim();
		if (!remoteUrl.isEmpty()) {
			try {
				return new RemoteWebDriver(new java.net.URL(remoteUrl), options);
			} catch (final Exception e) {
				throw new IllegalStateException("Unable to create RemoteWebDriver: " + e.getMessage(), e);
			}
		}
		return new ChromeDriver(options);
	}

	private void waitForUiLoad() {
		wait.until((ExpectedCondition<Boolean>) d -> {
			final Object readyState = js.executeScript("return document.readyState");
			return Objects.equals("complete", readyState);
		});
		try {
			Thread.sleep(500);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void switchToGoogleWindowIfOpened(final Set<String> beforeHandles) {
		final Set<String> currentHandles = driver.getWindowHandles();
		if (currentHandles.size() <= beforeHandles.size()) {
			return;
		}
		final List<String> newHandles = new ArrayList<>(currentHandles);
		newHandles.removeAll(beforeHandles);
		if (!newHandles.isEmpty()) {
			driver.switchTo().window(newHandles.get(0));
			waitForUiLoad();
		}
	}

	private void selectGoogleAccountIfPrompted(final String email) {
		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (clickIfVisible(email, SHORT_TIMEOUT)) {
				waitForUiLoad();
				return;
			}
		}
	}

	private void switchToAppWindow() {
		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForUiLoad();
			return;
		}
		final String fallbackHandle = driver.getWindowHandles().iterator().next();
		driver.switchTo().window(fallbackHandle);
		appWindowHandle = fallbackHandle;
		waitForUiLoad();
	}

	private void ensureMiNegocioMenuExpanded() {
		if (isTextVisibleNow("Administrar Negocios") && isTextVisibleNow("Agregar Negocio")) {
			return;
		}
		clickIfVisible("Mi Negocio", Duration.ofSeconds(8));
		waitForTextVisible("Administrar Negocios");
		waitForTextVisible("Agregar Negocio");
	}

	private void clickAnyByText(final String... texts) {
		Exception lastException = null;
		for (final String text : texts) {
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(clickableTextXpath(text))));
				scrollIntoView(element);
				element.click();
				waitForUiLoad();
				return;
			} catch (final Exception e) {
				lastException = e;
			}
		}
		throw new IllegalStateException("Could not click any expected visible text target: " + String.join(", ", texts),
				lastException);
	}

	private boolean clickIfVisible(final String text, final Duration timeout) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			final WebElement element = shortWait.until(
					ExpectedConditions.elementToBeClickable(By.xpath(clickableTextXpath(text))));
			scrollIntoView(element);
			element.click();
			waitForUiLoad();
			return true;
		} catch (final Exception e) {
			return false;
		}
	}

	private String clickableTextXpath(final String text) {
		final String literal = xpathLiteral(text);
		return "("
				+ "//*[self::button or self::a or @role='button'][normalize-space()=" + literal + "]"
				+ " | //*[(self::div or self::span or self::li) and normalize-space()=" + literal + "]"
				+ " | //*[self::button or self::a or @role='button'][contains(normalize-space(), " + literal + ")]"
				+ " | //*[(self::div or self::span or self::li) and contains(normalize-space(), " + literal + ")]"
				+ ")[1]";
	}

	private void waitForTextVisible(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]")));
	}

	private boolean waitForAnyTextVisible(final Duration timeout, final String... texts) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			return shortWait.until(d -> {
				for (final String text : texts) {
					if (!d.findElements(By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]")).isEmpty()) {
						return true;
					}
				}
				return false;
			});
		} catch (final Exception e) {
			return false;
		}
	}

	private boolean waitForLikelyUserName() {
		try {
			return new WebDriverWait(driver, SHORT_TIMEOUT).until(d -> {
				final List<WebElement> candidates = d.findElements(By.xpath("//*[contains(normalize-space(), '@')]"));
				for (final WebElement emailCandidate : candidates) {
					final String text = emailCandidate.getText();
					if (text != null && text.contains("@")) {
						return true;
					}
				}

				final String body = d.findElement(By.tagName("body")).getText();
				return body != null && body.matches("(?s).*\\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\\b.*");
			});
		} catch (final Exception e) {
			return false;
		}
	}

	private boolean waitForRegexInBody(final String regex) {
		try {
			return new WebDriverWait(driver, SHORT_TIMEOUT).until(d -> {
				final String bodyText = d.findElement(By.tagName("body")).getText();
				return bodyText != null && bodyText.matches("(?s).*" + regex + ".*");
			});
		} catch (final Exception e) {
			return false;
		}
	}

	private boolean isTextVisibleNow(final String text) {
		return !driver.findElements(By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]")).isEmpty();
	}

	private void scrollIntoView(final WebElement element) {
		js.executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private void captureScreenshot(final String checkpoint) throws IOException {
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final Path file = screenshotDir.resolve(checkpoint + ".png");
		Files.write(file, screenshot);
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("=== SaleADS Mi Negocio Workflow Report ===\n");
		builder.append("Test: ").append(TEST_NAME).append('\n');
		builder.append("Screenshots: ").append(screenshotDir).append('\n');
		builder.append('\n');

		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.getOrDefault(field, new StepResult(false, "No result"));
			builder.append(field)
					.append(": ")
					.append(result.passed ? "PASS" : "FAIL")
					.append(" - ")
					.append(result.message)
					.append('\n');
		}
		return builder.toString();
	}

	private void writeFinalReport() throws IOException {
		final String report = buildFinalReport();
		Files.writeString(reportFile, report);
		System.out.println("Final report written to: " + reportFile);
		System.out.println(report);
	}

	private String config(final String key, final String defaultValue) {
		final String property = System.getProperty(key);
		if (property != null && !property.isBlank()) {
			return property;
		}
		final String env = System.getenv(key);
		if (env != null && !env.isBlank()) {
			return env;
		}
		return defaultValue;
	}

	private String xpathLiteral(final String value) {
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

	private boolean runStep(final String field, final CheckedRunnable runnable) {
		try {
			runnable.run();
			results.put(field, new StepResult(true, "Validation completed"));
			return true;
		} catch (final Throwable e) {
			final String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			results.put(field, new StepResult(false, message));
			try {
				captureScreenshot("failure-" + field.replaceAll("[^A-Za-z0-9_-]", "_"));
			} catch (final IOException ignored) {
				// best effort only
			}
			return false;
		}
	}

	private void requireThen(final boolean requirement, final CheckedRunnable runnable) throws Exception {
		if (!requirement) {
			throw new IllegalStateException("Blocked by previous failed prerequisite step.");
		}
		runnable.run();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String message;

		private StepResult(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message;
		}
	}

	private static final class LegalValidationResult {
		private final boolean passed;
		private final String message;

		private LegalValidationResult(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message;
		}
	}
}
