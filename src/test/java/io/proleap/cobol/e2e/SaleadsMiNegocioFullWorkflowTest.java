package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Environment-agnostic SaleADS end-to-end test for the "Mi Negocio" workflow.
 *
 * To run:
 * SALEADS_E2E_ENABLED=true SALEADS_LOGIN_URL=https://<env-login-url> mvn -Dtest=SaleadsMiNegocioFullWorkflowTest test
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
	private static final List<String> GOOGLE_LOGIN_TEXTS = Arrays.asList("Sign in with Google", "Iniciar sesión con Google",
			"Continuar con Google", "Login with Google");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private String termsAndConditionsUrl;
	private String privacyPolicyUrl;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(envOrProperty("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run the SaleADS UI workflow test.", enabled);

		final String loginUrl = envOrProperty("SALEADS_LOGIN_URL", "").trim();
		Assume.assumeTrue("Set SALEADS_LOGIN_URL with the current environment login page URL.", !loginUrl.isEmpty());

		final boolean headless = Boolean.parseBoolean(envOrProperty("SALEADS_HEADLESS", "true"));
		final int timeoutSeconds = Integer.parseInt(envOrProperty("SALEADS_WAIT_TIMEOUT_SECONDS", "35"));
		screenshotDir = Paths.get(envOrProperty("SALEADS_SCREENSHOT_DIR", "target/saleads-screenshots"));
		Files.createDirectories(screenshotDir);

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage");
		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		driver.navigate().to(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTermsAndConditions);
		runStep("Política de Privacidad", this::stepValidatePrivacyPolicy);
		writeAndAssertFinalReport();
	}

	private void stepLoginWithGoogle() throws Exception {
		clickFirstVisibleText(GOOGLE_LOGIN_TEXTS);
		waitForUiToLoad();

		final String googleAccount = envOrProperty("SALEADS_GOOGLE_ACCOUNT_EMAIL", DEFAULT_GOOGLE_ACCOUNT);
		clickIfVisibleText(googleAccount, Duration.ofSeconds(12));
		waitForUiToLoad();

		wait.until(ExpectedConditions.or(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside")),
				ExpectedConditions.visibilityOfElementLocated(textLocator("Negocio"))));
		assertTrue("Left sidebar navigation should be visible.", hasVisible(By.xpath("//aside|//nav")));
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickIfVisibleText("Negocio", Duration.ofSeconds(5));
		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertVisibleText("Crear Nuevo Negocio");
		assertAnyVisible(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[normalize-space()='Nombre del Negocio']/following::input[1]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		takeScreenshot("03-crear-negocio-modal");

		final WebElement businessNameInput = findFirstVisible(Duration.ofSeconds(5),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[normalize-space()='Nombre del Negocio']/following::input[1]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));
		if (businessNameInput != null) {
			businessNameInput.click();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatización");
		}

		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!hasVisible(textLocator("Administrar Negocios"))) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("Información General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String pageText = visibleBodyText();
		assertTrue("User email should be visible in Información General.",
				EMAIL_PATTERN.matcher(pageText).find());
		assertTrue("A likely user name should be visible.",
				hasLikelyUserName(pageText));
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateTermsAndConditions() throws Exception {
		termsAndConditionsUrl = validateLegalDocument("Términos y Condiciones", "08-terminos-y-condiciones");
	}

	private void stepValidatePrivacyPolicy() throws Exception {
		privacyPolicyUrl = validateLegalDocument("Política de Privacidad", "09-politica-de-privacidad");
	}

	private String validateLegalDocument(final String linkText, final String screenshotName) throws Exception {
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String appUrlBeforeClick = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		final String newWindowHandle = waitForNewWindow(handlesBeforeClick, Duration.ofSeconds(12));
		if (newWindowHandle != null) {
			driver.switchTo().window(newWindowHandle);
			waitForUiToLoad();
		}

		assertVisibleText(linkText);
		final String legalContentText = visibleBodyText();
		assertTrue("Legal content text should be visible for " + linkText + ".",
				legalContentText != null && legalContentText.trim().length() > 120);
		takeScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();
		if (newWindowHandle != null) {
			driver.close();
			final String appWindow = handlesBeforeClick.iterator().next();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else if (!Objects.equals(appUrlBeforeClick, finalUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private String waitForNewWindow(final Set<String> handlesBeforeClick, final Duration timeout) throws InterruptedException {
		final Instant expiresAt = Instant.now().plus(timeout);
		while (Instant.now().isBefore(expiresAt)) {
			final Set<String> currentHandles = driver.getWindowHandles();
			if (currentHandles.size() > handlesBeforeClick.size()) {
				for (final String handle : currentHandles) {
					if (!handlesBeforeClick.contains(handle)) {
						return handle;
					}
				}
			}
			Thread.sleep(250L);
		}
		return null;
	}

	private void runStep(final String reportField, final StepExecutable stepExecutable) throws Exception {
		try {
			stepExecutable.run();
			finalReport.put(reportField, true);
		} catch (final AssertionError | Exception e) {
			finalReport.put(reportField, false);
			final String failure = reportField + ": " + e.getMessage();
			failures.add(failure);
			takeScreenshot("fail-" + sanitizeFileName(reportField));
		}
	}

	private void writeAndAssertFinalReport() throws IOException {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio - Final Report").append(System.lineSeparator());
		reportBuilder.append("================================").append(System.lineSeparator());
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			reportBuilder.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL")
					.append(System.lineSeparator());
		}
		if (termsAndConditionsUrl != null) {
			reportBuilder.append("Términos y Condiciones URL: ").append(termsAndConditionsUrl).append(System.lineSeparator());
		}
		if (privacyPolicyUrl != null) {
			reportBuilder.append("Política de Privacidad URL: ").append(privacyPolicyUrl).append(System.lineSeparator());
		}
		if (!failures.isEmpty()) {
			reportBuilder.append("Failures:").append(System.lineSeparator());
			for (final String failure : failures) {
				reportBuilder.append("- ").append(failure).append(System.lineSeparator());
			}
		}

		final Path reportPath = screenshotDir.resolve("final-report.txt");
		Files.writeString(reportPath, reportBuilder.toString(), StandardCharsets.UTF_8);
		System.out.println(reportBuilder);

		assertTrue("One or more workflow validations failed. Check " + reportPath + " for details.", failures.isEmpty());
	}

	private void clickFirstVisibleText(final List<String> texts) {
		Exception lastError = null;
		for (final String text : texts) {
			try {
				clickByVisibleText(text);
				return;
			} catch (final RuntimeException e) {
				lastError = e;
			}
		}
		throw new AssertionError("Unable to click any Google login button text. Tried: " + texts, lastError);
	}

	private void clickByVisibleText(final String text) {
		final WebElement element = wait.until(driverValue -> findClickable(text));
		if (element == null) {
			throw new AssertionError("Element with visible text not found: " + text);
		}
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void clickIfVisibleText(final String text, final Duration timeout) {
		final Instant expiresAt = Instant.now().plus(timeout);
		while (Instant.now().isBefore(expiresAt)) {
			final WebElement element = findClickable(text);
			if (element != null) {
				try {
					element.click();
				} catch (final Exception clickError) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				}
				waitForUiToLoad();
				return;
			}
			sleepSilently(300L);
		}
	}

	private WebElement findClickable(final String text) {
		final List<WebElement> candidates = driver.findElements(textLocator(text));
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed() && candidate.isEnabled()) {
				return candidate;
			}
		}
		return null;
	}

	private By textLocator(final String text) {
		return By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]");
	}

	private void assertVisibleText(final String text) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(textLocator(text)));
		} catch (final TimeoutException timeoutException) {
			throw new AssertionError("Expected visible text not found: " + text, timeoutException);
		}
	}

	private void assertAnyVisible(final By... locators) {
		for (final By locator : locators) {
			if (hasVisible(locator)) {
				return;
			}
		}
		throw new AssertionError("None of the expected elements are visible: " + Arrays.toString(locators));
	}

	private boolean hasVisible(final By locator) {
		try {
			return !wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(locator)).isEmpty();
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private WebElement findFirstVisible(final Duration timeout, final By... locators) {
		final Instant expiresAt = Instant.now().plus(timeout);
		while (Instant.now().isBefore(expiresAt)) {
			for (final By locator : locators) {
				final List<WebElement> foundElements = driver.findElements(locator);
				for (final WebElement element : foundElements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			sleepSilently(250L);
		}
		return null;
	}

	private String visibleBodyText() {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
	}

	private boolean hasLikelyUserName(final String pageText) {
		final List<String> ignoredTokens = Arrays.asList("información general", "business plan", "cambiar plan", "detalles de la cuenta",
				"tus negocios", "sección legal", "cuenta creada", "estado activo", "idioma seleccionado");
		final String[] lines = pageText.split("\\R");
		for (final String line : lines) {
			final String normalizedLine = line.trim();
			if (normalizedLine.length() < 3 || normalizedLine.length() > 70 || normalizedLine.contains("@")) {
				continue;
			}

			final String lowerLine = normalizedLine.toLowerCase();
			boolean ignored = false;
			for (final String token : ignoredTokens) {
				if (lowerLine.contains(token)) {
					ignored = true;
					break;
				}
			}
			if (!ignored && normalizedLine.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private void waitForUiToLoad() {
		wait.until(driverValue -> "complete"
				.equals(((JavascriptExecutor) driverValue).executeScript("return document.readyState")));
		sleepSilently(500L);
	}

	private void takeScreenshot(final String name) {
		if (driver == null) {
			return;
		}
		try {
			final byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			final Path screenshotPath = screenshotDir.resolve(sanitizeFileName(name) + ".png");
			Files.write(screenshotPath, screenshotBytes);
		} catch (final Exception ignored) {
			// Screenshot failures should not stop the workflow execution.
		}
	}

	private String sanitizeFileName(final String text) {
		return text.toLowerCase().replaceAll("[^a-z0-9-]+", "-").replaceAll("^-+|-+$", "");
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final String[] parts = text.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String envOrProperty(final String key, final String fallback) {
		final String envValue = System.getenv(key);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		final String propertyValue = System.getProperty(key);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		return fallback;
	}

	private void sleepSilently(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Sleep interrupted", interruptedException);
		}
	}

	@FunctionalInterface
	private interface StepExecutable {
		void run() throws Exception;
	}
}
