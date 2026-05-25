package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
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

public class SaleadsMiNegocioFullTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(6);
	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Política de Privacidad";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private String applicationWindowHandle;
	private String termsFinalUrl;
	private String privacyFinalUrl;

	@Before
	public void setUp() throws Exception {
		final String loginUrl = requiredEnv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("SALEADS_LOGIN_URL must be configured to run this E2E test.",
				loginUrl != null && !loginUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-gpu");

		if (!"false".equalsIgnoreCase(env("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDirectory = createEvidenceDirectory();

		driver.get(loginUrl);
		waitForUiToSettle();
		applicationWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() {
		final boolean loginPassed = runStep(STEP_LOGIN, this::loginWithGoogleAndValidate);

		if (!loginPassed) {
			markStepFailed(STEP_MI_NEGOCIO_MENU, "Skipped because login failed.");
			markStepFailed(STEP_AGREGAR_NEGOCIO_MODAL, "Skipped because login failed.");
			markStepFailed(STEP_ADMINISTRAR_NEGOCIOS, "Skipped because login failed.");
			markStepFailed(STEP_INFO_GENERAL, "Skipped because login failed.");
			markStepFailed(STEP_DETALLES_CUENTA, "Skipped because login failed.");
			markStepFailed(STEP_TUS_NEGOCIOS, "Skipped because login failed.");
			markStepFailed(STEP_TERMINOS, "Skipped because login failed.");
			markStepFailed(STEP_PRIVACIDAD, "Skipped because login failed.");
			printReport();
			fail("Login step failed, remaining steps were skipped.");
			return;
		}

		runStep(STEP_MI_NEGOCIO_MENU, this::openMiNegocioMenuAndValidate);
		runStep(STEP_AGREGAR_NEGOCIO_MODAL, this::validateAgregarNegocioModal);
		runStep(STEP_ADMINISTRAR_NEGOCIOS, this::openAdministrarNegociosAndValidate);
		runStep(STEP_INFO_GENERAL, this::validateInformacionGeneral);
		runStep(STEP_DETALLES_CUENTA, this::validateDetallesCuenta);
		runStep(STEP_TUS_NEGOCIOS, this::validateTusNegocios);
		runStep(STEP_TERMINOS, this::validateTerminosYCondiciones);
		runStep(STEP_PRIVACIDAD, this::validatePoliticaPrivacidad);

		printReport();

		final List<String> failedSteps = new ArrayList<>();
		for (Map.Entry<String, Boolean> stepResult : report.entrySet()) {
			if (!stepResult.getValue()) {
				failedSteps.add(stepResult.getKey());
			}
		}

		if (!failedSteps.isEmpty()) {
			fail("Failed steps: " + failedSteps + ". Details: " + failures);
		}
	}

	private void loginWithGoogleAndValidate() throws Exception {
		clickFirstAvailableText(List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Google", "Iniciar sesión", "Login"));

		// If Google account chooser appears, pick configured account.
		clickVisibleTextIfPresent(env("SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_ACCOUNT), Duration.ofSeconds(8));

		assertTrue("Main application interface should be visible after login.",
				isAnyVisible(By.xpath("//aside"), By.xpath("//nav"), byText("Negocio")));
		assertTrue("Left sidebar navigation should be visible.", isAnyVisible(byText("Negocio"), By.xpath("//aside")));

		takeScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenuAndValidate() throws Exception {
		clickVisibleText("Mi Negocio");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws Exception {
		ensureMiNegocioExpanded();
		clickVisibleText("Agregar Negocio");

		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTrue("Nombre del Negocio input should be visible.",
				isAnyVisible(By.xpath("//input[contains(@placeholder, 'Nombre')]"),
						By.xpath("//*[normalize-space()='Nombre del Negocio']/following::input[1]")));
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal");

		final WebElement nombreDelNegocioInput = firstVisible(By.xpath("//input[contains(@placeholder, 'Nombre')]"),
				By.xpath("//*[normalize-space()='Nombre del Negocio']/following::input[1]"));
		nombreDelNegocioInput.click();
		nombreDelNegocioInput.sendKeys("Negocio Prueba Automatización");

		clickVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byText("Crear Nuevo Negocio")));
		waitForUiToSettle();
	}

	private void openAdministrarNegociosAndValidate() throws Exception {
		ensureMiNegocioExpanded();
		clickVisibleText("Administrar Negocios");

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");

		takeScreenshot("04-administrar-negocios-view");
	}

	private void validateInformacionGeneral() {
		assertTextVisible("Información General");
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");

		final String pageText = driver.findElement(By.tagName("body")).getText();
		final String configuredEmail = env("SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_ACCOUNT);
		final boolean hasAnyEmail = pageText.matches("(?s).*[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}.*");
		final boolean hasConfiguredEmail = pageText.contains(configuredEmail);
		assertTrue("User email should be visible in Información General.", hasAnyEmail || hasConfiguredEmail);

		final String expectedUserName = env("SALEADS_EXPECTED_USER_NAME", "");
		if (!expectedUserName.isBlank()) {
			assertTextVisible(expectedUserName);
		} else {
			assertTrue("A user name-like value should be visible in Información General.", hasUserNameLikeText());
		}
	}

	private void validateDetallesCuenta() {
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void validateTerminosYCondiciones() throws Exception {
		termsFinalUrl = openLegalLinkValidateAndReturn("Términos y Condiciones", "Términos y Condiciones",
				"08-terminos-y-condiciones");
		assertFalse("Final URL for Términos y Condiciones should not be blank.",
				termsFinalUrl == null || termsFinalUrl.isBlank());
	}

	private void validatePoliticaPrivacidad() throws Exception {
		privacyFinalUrl = openLegalLinkValidateAndReturn("Política de Privacidad", "Política de Privacidad",
				"09-politica-de-privacidad");
		assertFalse("Final URL for Política de Privacidad should not be blank.",
				privacyFinalUrl == null || privacyFinalUrl.isBlank());
	}

	private String openLegalLinkValidateAndReturn(final String linkText, final String pageHeading,
			final String screenshotName) throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickVisibleText(linkText);

		boolean openedNewTab = false;
		try {
			openedNewTab = new WebDriverWait(driver, Duration.ofSeconds(10))
					.until((ExpectedCondition<Boolean>) wd -> wd.getWindowHandles().size() > handlesBeforeClick.size());
		} catch (TimeoutException ignored) {
			openedNewTab = false;
		}

		if (openedNewTab) {
			switchToNewWindow(handlesBeforeClick);
		}

		waitForUiToSettle();
		assertTextVisible(pageHeading);

		final String legalText = driver.findElement(By.tagName("body")).getText().trim();
		assertTrue("Legal content text should be visible.", legalText.length() > 120);

		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToSettle();
		} else {
			driver.navigate().back();
			waitForUiToSettle();
		}

		// Ensure we are back in app tab even if the browser changed focus.
		driver.switchTo().window(applicationWindowHandle);
		waitForUiToSettle();
		return finalUrl;
	}

	private boolean runStep(final String name, final ThrowingRunnable stepAction) {
		try {
			stepAction.run();
			report.put(name, true);
			return true;
		} catch (Throwable exception) {
			markStepFailed(name, exception.getMessage());
			try {
				takeScreenshot("fail-" + slugify(name));
			} catch (Exception ignored) {
				// Best-effort evidence capture after step failure.
			}
			return false;
		}
	}

	private void markStepFailed(final String stepName, final String reason) {
		report.put(stepName, false);
		failures.add(stepName + " -> " + reason);
	}

	private void printReport() {
		System.out.println("=== saleads_mi_negocio_full_test final report ===");
		printStep(STEP_LOGIN);
		printStep(STEP_MI_NEGOCIO_MENU);
		printStep(STEP_AGREGAR_NEGOCIO_MODAL);
		printStep(STEP_ADMINISTRAR_NEGOCIOS);
		printStep(STEP_INFO_GENERAL);
		printStep(STEP_DETALLES_CUENTA);
		printStep(STEP_TUS_NEGOCIOS);
		printStep(STEP_TERMINOS);
		printStep(STEP_PRIVACIDAD);
		System.out.println("Terms URL: " + valueOrPlaceholder(termsFinalUrl));
		System.out.println("Privacy URL: " + valueOrPlaceholder(privacyFinalUrl));
		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
	}

	private void printStep(final String stepName) {
		final boolean passed = report.getOrDefault(stepName, false);
		System.out.println(stepName + ": " + (passed ? "PASS" : "FAIL"));
	}

	private String valueOrPlaceholder(final String value) {
		if (value == null || value.isBlank()) {
			return "N/A";
		}

		return value;
	}

	private void ensureMiNegocioExpanded() throws Exception {
		if (!isTextVisible("Agregar Negocio", Duration.ofSeconds(2))
				|| !isTextVisible("Administrar Negocios", Duration.ofSeconds(2))) {
			clickVisibleText("Mi Negocio");
		}
	}

	private void clickFirstAvailableText(final List<String> texts) throws Exception {
		for (String text : texts) {
			if (clickVisibleTextIfPresent(text, Duration.ofSeconds(3))) {
				return;
			}
		}

		throw new NoSuchElementException("Unable to find any clickable element for texts: " + texts);
	}

	private void clickVisibleText(final String text) throws Exception {
		final WebElement clickable = waitForVisibleElementByText(text, DEFAULT_TIMEOUT);
		scrollIntoView(clickable);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(clickable));
			clickable.click();
		} catch (Exception ignored) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
		}
		waitForUiToSettle();
	}

	private boolean clickVisibleTextIfPresent(final String text, final Duration timeout) throws Exception {
		try {
			final WebElement clickable = waitForVisibleElementByText(text, timeout);
			scrollIntoView(clickable);
			try {
				clickable.click();
			} catch (Exception ignored) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
			}
			waitForUiToSettle();
			return true;
		} catch (TimeoutException ignored) {
			return false;
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void waitForUiToSettle() throws InterruptedException {
		wait.until(webDriver -> ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));

		try {
			wait.withTimeout(Duration.ofSeconds(6))
					.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(
							"//*[contains(@class, 'spinner') or contains(@class, 'loading') or contains(@class, 'progress')]")));
		} catch (TimeoutException ignored) {
			// Loader may not be present on every page.
		} finally {
			wait.withTimeout(DEFAULT_TIMEOUT);
		}

		Thread.sleep(500L);
	}

	private boolean isAnyVisible(final By... locators) {
		for (By locator : locators) {
			try {
				new WebDriverWait(driver, SHORT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
				return true;
			} catch (TimeoutException ignored) {
				// Continue trying the next locator.
			}
		}

		return false;
	}

	private WebElement firstVisible(final By... locators) {
		for (By locator : locators) {
			try {
				return new WebDriverWait(driver, SHORT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
			} catch (TimeoutException ignored) {
				// Continue trying the next locator.
			}
		}

		throw new NoSuchElementException("No expected visible element matched for locators.");
	}

	private void assertTextVisible(final String text) {
		final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(byText(text)));
		assertTrue("Text should be visible: " + text, element.isDisplayed());
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(byText(text)));
			return true;
		} catch (TimeoutException ignored) {
			return false;
		}
	}

	private boolean hasUserNameLikeText() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final String[] lines = bodyText.split("\\R");
		for (String line : lines) {
			final String normalized = line.trim();
			if (normalized.isEmpty()) {
				continue;
			}

			if (normalized.equalsIgnoreCase("Información General") || normalized.equalsIgnoreCase("BUSINESS PLAN")
					|| normalized.equalsIgnoreCase("Cambiar Plan") || normalized.contains("@")
					|| normalized.equalsIgnoreCase("Cuenta creada") || normalized.equalsIgnoreCase("Estado activo")
					|| normalized.equalsIgnoreCase("Idioma seleccionado")) {
				continue;
			}

			if (normalized.matches(".*[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}.*\\s+.*[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}.*")) {
				return true;
			}
		}

		return false;
	}

	private void switchToNewWindow(final Set<String> originalHandles) {
		for (String handle : driver.getWindowHandles()) {
			if (!originalHandles.contains(handle)) {
				driver.switchTo().window(handle);
				return;
			}
		}

		throw new NoSuchElementException("Expected a new browser tab/window, but none was found.");
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path directory = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(directory);
		return directory;
	}

	private void takeScreenshot(final String filename) throws IOException {
		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshotFile.toPath(), evidenceDirectory.resolve(filename + ".png"),
				StandardCopyOption.REPLACE_EXISTING);
	}

	private String env(final String key, final String fallback) {
		final String value = System.getenv(key);
		return value == null ? fallback : value;
	}

	private String requiredEnv(final String key) {
		return System.getenv(key);
	}

	private String slugify(final String text) {
		final String normalized = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private By byText(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath("//*[normalize-space()=" + literal + "]");
	}

	private WebElement waitForVisibleElementByText(final String text, final Duration timeout) {
		final String literal = xpathLiteral(text);
		final By byExactText = By.xpath("//*[normalize-space()=" + literal + "]");

		return new WebDriverWait(driver, timeout).until(webDriver -> {
			for (WebElement element : webDriver.findElements(byExactText)) {
				try {
					if (!element.isDisplayed()) {
						continue;
					}

					return resolveClickableAncestor(element);
				} catch (Exception ignored) {
					// Retry on stale/intermediate rendering state.
				}
			}

			return null;
		});
	}

	private WebElement resolveClickableAncestor(final WebElement element) {
		try {
			return element.findElement(By.xpath("./ancestor-or-self::*[self::a or self::button or @role='button'][1]"));
		} catch (NoSuchElementException ignored) {
			return element;
		}
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char current = chars[i];
			if (i > 0) {
				builder.append(", ");
			}

			if (current == '\'') {
				builder.append("\"'\"");
			} else {
				builder.append("'").append(current).append("'");
			}
		}

		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
