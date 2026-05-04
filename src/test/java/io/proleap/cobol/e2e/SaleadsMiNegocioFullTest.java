package io.proleap.cobol.e2e;

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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Información General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "Términos y Condiciones";
	private static final String PRIVACIDAD = "Política de Privacidad";

	private final Map<String, StepOutcome> outcomes = new LinkedHashMap<>();
	private final Map<String, String> finalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true or -Dsaleads.e2e.enabled=true to run this test.",
				readBooleanFlag("saleads.e2e.enabled", "SALEADS_E2E_ENABLED"));

		final LocalDateTime now = LocalDateTime.now();
		final String runId = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		artifactsDir = Path.of("target", "saleads-e2e-artifacts", runId);
		Files.createDirectories(artifactsDir);

		initializeOutcomes();
		initializeDriver();
	}

	@After
	public void tearDown() {
		writeFinalReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep(LOGIN, this::stepLoginWithGoogle);
		runStep(MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		runStep(INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		runStep(DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(TERMINOS, () -> stepValidateLegalDocument("Términos y Condiciones", "08-terminos-condiciones"));
		runStep(PRIVACIDAD, () -> stepValidateLegalDocument("Política de Privacidad", "09-politica-privacidad"));

		final List<String> failedSteps = outcomes.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(entry -> entry.getKey() + " -> " + entry.getValue().detail).collect(Collectors.toList());
		assertTrue("One or more workflow validations failed:\n" + String.join("\n", failedSteps),
				failedSteps.isEmpty());
	}

	private void initializeOutcomes() {
		outcomes.put(LOGIN, StepOutcome.fail("Not executed"));
		outcomes.put(MI_NEGOCIO_MENU, StepOutcome.fail("Not executed"));
		outcomes.put(AGREGAR_NEGOCIO_MODAL, StepOutcome.fail("Not executed"));
		outcomes.put(ADMINISTRAR_NEGOCIOS_VIEW, StepOutcome.fail("Not executed"));
		outcomes.put(INFORMACION_GENERAL, StepOutcome.fail("Not executed"));
		outcomes.put(DETALLES_CUENTA, StepOutcome.fail("Not executed"));
		outcomes.put(TUS_NEGOCIOS, StepOutcome.fail("Not executed"));
		outcomes.put(TERMINOS, StepOutcome.fail("Not executed"));
		outcomes.put(PRIVACIDAD, StepOutcome.fail("Not executed"));
	}

	private void initializeDriver() {
		final ChromeOptions options = new ChromeOptions();
		if (readBooleanFlag("saleads.headless", "SALEADS_HEADLESS")) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);

		final String startUrl = readFirstPresent("saleads.login.url", "SALEADS_LOGIN_URL", "saleads.url", "SALEADS_URL");
		if (startUrl != null && !startUrl.isBlank()) {
			driver.get(startUrl);
			waitForUiToLoad();
		}
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			outcomes.put(stepName, StepOutcome.pass("Validated successfully"));
		} catch (final Throwable throwable) {
			outcomes.put(stepName, StepOutcome.fail(extractErrorDetail(throwable)));
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		if (!isAnyTextVisible(Duration.ofSeconds(8), "Negocio", "Mi Negocio")) {
			final String baseHandle = driver.getWindowHandle();
			final Set<String> handlesBeforeClick = driver.getWindowHandles();

			clickAnyText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
					"Continuar con Google", "Google");

			final String maybeNewHandle = waitForNewWindow(handlesBeforeClick, Duration.ofSeconds(12));
			if (maybeNewHandle != null) {
				driver.switchTo().window(maybeNewHandle);
				selectGoogleAccountIfVisible();
				driver.switchTo().window(baseHandle);
			} else {
				selectGoogleAccountIfVisible();
				if (driver.getWindowHandles().contains(baseHandle)) {
					driver.switchTo().window(baseHandle);
				}
			}
		}

		waitForAnyTextVisible(Duration.ofSeconds(40), "Negocio", "Mi Negocio");
		assertTrue("Left sidebar or menu entry was not visible after login.",
				isAnyTextVisible(Duration.ofSeconds(10), "Negocio", "Mi Negocio"));
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		expandMiNegocioMenu();
		waitForAnyTextVisible(Duration.ofSeconds(15), "Agregar Negocio");
		waitForAnyTextVisible(Duration.ofSeconds(15), "Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickAnyText("Agregar Negocio");
		waitForAnyTextVisible(Duration.ofSeconds(15), "Crear Nuevo Negocio");
		waitForAnyTextVisible(Duration.ofSeconds(10), "Nombre del Negocio");
		waitForAnyTextVisible(Duration.ofSeconds(10), "Tienes 2 de 3 negocios");
		waitForAnyTextVisible(Duration.ofSeconds(10), "Cancelar");
		waitForAnyTextVisible(Duration.ofSeconds(10), "Crear Negocio");

		final WebElement businessNameInput = waitForVisible(By.xpath(
				"//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"
						+ " | //input[contains(@placeholder,'Nombre del Negocio')]"));
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys(TEST_BUSINESS_NAME);
		waitForUiToLoad();

		takeScreenshot("03-agregar-negocio-modal");

		clickAnyText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(),'Crear Nuevo Negocio')]")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		expandMiNegocioMenu();
		clickAnyText("Administrar Negocios");
		waitForUiToLoad();

		waitForAnyTextVisible(Duration.ofSeconds(20), "Información General", "Informacion General");
		waitForAnyTextVisible(Duration.ofSeconds(10), "Detalles de la Cuenta");
		waitForAnyTextVisible(Duration.ofSeconds(10), "Tus Negocios");
		waitForAnyTextVisible(Duration.ofSeconds(10), "Sección Legal", "Seccion Legal");

		takeFullPageScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		waitForAnyTextVisible(Duration.ofSeconds(12), "Información General", "Informacion General");
		waitForAnyTextVisible(Duration.ofSeconds(8), GOOGLE_ACCOUNT);
		waitForAnyTextVisible(Duration.ofSeconds(8), "BUSINESS PLAN");
		waitForAnyTextVisible(Duration.ofSeconds(8), "Cambiar Plan");

		final WebElement infoSection = findSectionByTitle("Información General", "Informacion General");
		final String infoText = normalizeText(infoSection.getText());
		final boolean hasUserLikeText = Arrays.stream(infoSection.getText().split("\\R")).map(String::trim)
				.filter(text -> !text.isEmpty()).anyMatch(this::looksLikeUserNameLine);

		assertTrue("No user-name-like text found in Informacion General section.\nSection text: " + infoText,
				hasUserLikeText);
	}

	private void stepValidateDetallesCuenta() throws Exception {
		waitForAnyTextVisible(Duration.ofSeconds(12), "Detalles de la Cuenta");
		waitForAnyTextVisible(Duration.ofSeconds(8), "Cuenta creada");
		waitForAnyTextVisible(Duration.ofSeconds(8), "Estado activo");
		waitForAnyTextVisible(Duration.ofSeconds(8), "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() throws Exception {
		waitForAnyTextVisible(Duration.ofSeconds(12), "Tus Negocios");
		waitForAnyTextVisible(Duration.ofSeconds(8), "Agregar Negocio");
		waitForAnyTextVisible(Duration.ofSeconds(8), "Tienes 2 de 3 negocios");

		final WebElement negociosSection = findSectionByTitle("Tus Negocios");
		final String sectionText = normalizeText(negociosSection.getText());
		assertTrue("The business list appears empty.", sectionText.replace(normalizeText("Tus Negocios"), "").trim()
				.length() > normalizeText("Agregar Negocio Tienes 2 de 3 negocios").length());
	}

	private void stepValidateLegalDocument(final String linkText, final String screenshotName) throws Exception {
		waitForAnyTextVisible(Duration.ofSeconds(12), "Sección Legal", "Seccion Legal");
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String previousUrl = driver.getCurrentUrl();

		clickAnyText(linkText, removeDiacritics(linkText));

		String activeHandle = originalHandle;
		final String newHandle = waitForNewWindow(handlesBeforeClick, Duration.ofSeconds(8));
		if (newHandle != null) {
			activeHandle = newHandle;
			driver.switchTo().window(activeHandle);
		} else {
			wait.until(driverInstance -> !driverInstance.getCurrentUrl().equals(previousUrl));
		}

		waitForUiToLoad();
		waitForAnyTextVisible(Duration.ofSeconds(15), linkText, removeDiacritics(linkText));

		final WebElement body = waitForVisible(By.tagName("body"));
		final String bodyText = normalizeText(body.getText());
		assertTrue("No visible legal content found for " + linkText + ".",
				bodyText.length() > normalizeText(linkText).length() + 60);

		takeScreenshot(screenshotName);
		finalUrls.put(linkText, driver.getCurrentUrl());

		if (!activeHandle.equals(originalHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		waitForAnyTextVisible(Duration.ofSeconds(15), "Sección Legal", "Seccion Legal");
	}

	private void expandMiNegocioMenu() throws Exception {
		if (!isAnyTextVisible(SHORT_TIMEOUT, "Mi Negocio")) {
			clickAnyText("Negocio");
		}

		if (!isAnyTextVisible(SHORT_TIMEOUT, "Agregar Negocio", "Administrar Negocios")) {
			clickAnyText("Mi Negocio");
		}

		waitForAnyTextVisible(Duration.ofSeconds(12), "Agregar Negocio");
		waitForAnyTextVisible(Duration.ofSeconds(12), "Administrar Negocios");
	}

	private void selectGoogleAccountIfVisible() throws Exception {
		if (isAnyTextVisible(Duration.ofSeconds(10), GOOGLE_ACCOUNT)) {
			clickAnyText(GOOGLE_ACCOUNT);
			waitForUiToLoad();
		}
	}

	private void clickAnyText(final String... texts) throws Exception {
		Exception lastException = null;

		for (final String text : texts) {
			if (text == null || text.isBlank()) {
				continue;
			}

			try {
				final WebElement element = waitForVisible(buildClickableTextLocator(text));
				wait.until(ExpectedConditions.elementToBeClickable(element));
				element.click();
				waitForUiToLoad();
				return;
			} catch (final Exception exception) {
				lastException = exception;
			}
		}

		throw new NoSuchElementException("Unable to click an element with any of the texts: " + Arrays.toString(texts),
				lastException);
	}

	private By buildClickableTextLocator(final String text) {
		return By.xpath("//button[normalize-space(.)='" + text + "']"
				+ " | //a[normalize-space(.)='" + text + "']"
				+ " | //*[@role='button' and normalize-space(.)='" + text + "']"
				+ " | //*[self::span or self::div][normalize-space(.)='" + text + "']"
				+ " | //button[contains(normalize-space(.),'" + text + "')]"
				+ " | //a[contains(normalize-space(.),'" + text + "')]"
				+ " | //*[@role='button' and contains(normalize-space(.),'" + text + "')]"
				+ " | //*[self::span or self::div][contains(normalize-space(.),'" + text + "')]");
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private boolean isAnyTextVisible(final Duration timeout, final String... texts) {
		try {
			waitForAnyTextVisible(timeout, texts);
			return true;
		} catch (final Exception exception) {
			return false;
		}
	}

	private void waitForAnyTextVisible(final Duration timeout, final String... texts) {
		final WebDriverWait customWait = new WebDriverWait(driver, timeout);
		customWait.until(driverInstance -> {
			for (final String text : texts) {
				if (text == null || text.isBlank()) {
					continue;
				}

				final List<WebElement> elements = driverInstance
						.findElements(By.xpath("//*[contains(normalize-space(.),'" + text + "')]"));
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			}
			return false;
		});
	}

	private void waitForUiToLoad() {
		try {
			wait.until(driverInstance -> {
				final Object readyState = ((JavascriptExecutor) driverInstance)
						.executeScript("return document.readyState");
				return "complete".equals(readyState);
			});
		} catch (final TimeoutException exception) {
			// Continue: some SPA interactions do not update document.readyState.
		}

		try {
			wait.until(driverInstance -> Boolean.TRUE.equals(((JavascriptExecutor) driverInstance)
					.executeScript("return (window.jQuery ? jQuery.active === 0 : true);")));
		} catch (final TimeoutException exception) {
			// Continue when jQuery is not used.
		}
	}

	private String waitForNewWindow(final Set<String> existingHandles, final Duration timeout) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			shortWait.until(driverInstance -> driverInstance.getWindowHandles().size() > existingHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!existingHandles.contains(handle)) {
					return handle;
				}
			}
			return null;
		} catch (final TimeoutException exception) {
			return null;
		}
	}

	private WebElement findSectionByTitle(final String... possibleTitles) {
		final List<Exception> exceptions = new ArrayList<>();
		for (final String title : possibleTitles) {
			try {
				return waitForVisible(By.xpath(
						"//*[self::section or self::div][.//*[contains(normalize-space(.),'" + title + "')]]"));
			} catch (final Exception exception) {
				exceptions.add(exception);
			}
		}

		throw new NoSuchElementException("Could not locate section with titles: " + Arrays.toString(possibleTitles),
				exceptions.isEmpty() ? null : exceptions.get(0));
	}

	private boolean looksLikeUserNameLine(final String line) {
		final String normalized = normalizeText(line);
		if (normalized.isEmpty() || normalized.contains("@") || normalized.matches(".*\\d.*")) {
			return false;
		}
		if (normalized.contains("informacion general") || normalized.contains("business plan")
				|| normalized.contains("cambiar plan") || normalized.contains("cuenta creada")
				|| normalized.contains("estado activo") || normalized.contains("idioma seleccionado")) {
			return false;
		}
		return normalized.length() >= 4;
	}

	private void takeScreenshot(final String checkpoint) throws IOException {
		final Path screenshotPath = artifactsDir.resolve(checkpoint + ".png");
		final Path sourcePath = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(sourcePath, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void takeFullPageScreenshot(final String checkpoint) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			final Long documentHeight = (Long) js.executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			final int targetHeight = documentHeight == null ? 2200 : (int) Math.min(Math.max(documentHeight, 1080L), 4000L);
			driver.manage().window().setSize(new Dimension(1920, targetHeight));
			waitForUiToLoad();
			takeScreenshot(checkpoint);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}
	}

	private void writeFinalReport() {
		if (artifactsDir == null) {
			return;
		}

		try {
			final List<String> lines = new ArrayList<>();
			lines.add("saleads_mi_negocio_full_test");
			lines.add("Result summary:");
			lines.add("");

			for (final Map.Entry<String, StepOutcome> entry : outcomes.entrySet()) {
				lines.add(entry.getKey() + ": " + (entry.getValue().passed ? "PASS" : "FAIL") + " - "
						+ entry.getValue().detail);
			}

			lines.add("");
			lines.add("Captured final URLs:");
			lines.add("Términos y Condiciones: "
					+ Optional.ofNullable(finalUrls.get("Términos y Condiciones")).orElse("N/A"));
			lines.add("Política de Privacidad: "
					+ Optional.ofNullable(finalUrls.get("Política de Privacidad")).orElse("N/A"));

			Files.write(artifactsDir.resolve("final-report.txt"), lines);
		} catch (final Exception exception) {
			System.out.println("Unable to write SaleADS E2E report: " + exception.getMessage());
		}
	}

	private static String extractErrorDetail(final Throwable throwable) {
		final String message = throwable.getMessage();
		if (message == null || message.isBlank()) {
			return throwable.getClass().getSimpleName();
		}
		return throwable.getClass().getSimpleName() + ": " + message;
	}

	private static String readFirstPresent(final String systemPropertyA, final String envVariableA,
			final String systemPropertyB, final String envVariableB) {
		final String[] values = new String[] { System.getProperty(systemPropertyA), System.getenv(envVariableA),
				System.getProperty(systemPropertyB), System.getenv(envVariableB) };
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private static boolean readBooleanFlag(final String systemProperty, final String envVariable) {
		final String systemValue = System.getProperty(systemProperty);
		if (systemValue != null) {
			return Boolean.parseBoolean(systemValue);
		}
		final String envValue = System.getenv(envVariable);
		return envValue != null && Boolean.parseBoolean(envValue);
	}

	private static String normalizeText(final String value) {
		if (value == null) {
			return "";
		}
		final String noDiacritics = removeDiacritics(value);
		return noDiacritics.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
	}

	private static String removeDiacritics(final String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepOutcome {
		private final boolean passed;
		private final String detail;

		private StepOutcome(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}

		private static StepOutcome pass(final String detail) {
			return new StepOutcome(true, detail);
		}

		private static StepOutcome fail(final String detail) {
			return new StepOutcome(false, detail);
		}
	}
}
