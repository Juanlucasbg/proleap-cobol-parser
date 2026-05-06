package io.proleap.cobol.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.Assert;
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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String[] REPORT_FIELDS = new String[] { "Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad" };

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final Map<String, String> stepResults = createDefaultReport();
		final Map<String, String> evidence = new LinkedHashMap<>();
		final String googleAccount = systemValue("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT",
				DEFAULT_GOOGLE_ACCOUNT);
		final String configuredUserName = systemValue("saleads.user.name", "SALEADS_USER_NAME", "");
		final Path screenshotsDir = Paths.get(systemValue("saleads.screenshots.dir", "SALEADS_SCREENSHOTS_DIR",
				"target/saleads-e2e/screenshots"));
		final Path reportPath = Paths.get(systemValue("saleads.report.path", "SALEADS_REPORT_PATH",
				"target/saleads-e2e/saleads_mi_negocio_full_test_report.json"));

		final WebDriver driver = createDriver();
		try {
			final WebDriverWait wait = new WebDriverWait(driver, WAIT_TIMEOUT);
			openLoginPageIfProvided(driver, wait);

			runStep(stepResults, "Login", () -> {
				loginWithGoogle(driver, wait, googleAccount);
				takeScreenshot(driver, screenshotsDir, "step-1-dashboard-loaded.png");
			});

			runStep(stepResults, "Mi Negocio menu", () -> {
				openMiNegocioMenu(driver, wait);
				takeScreenshot(driver, screenshotsDir, "step-2-mi-negocio-menu-expanded.png");
			});

			runStep(stepResults, "Agregar Negocio modal", () -> {
				openAgregarNegocioModal(driver, wait);
				takeScreenshot(driver, screenshotsDir, "step-3-agregar-negocio-modal.png");
				optionalFillAndCancelNegocioModal(driver, wait);
			});

			runStep(stepResults, "Administrar Negocios view", () -> {
				openAdministrarNegocios(driver, wait);
				takeScreenshot(driver, screenshotsDir, "step-4-administrar-negocios-page.png");
			});

			runStep(stepResults, "Información General", () -> validateInformacionGeneral(driver, wait, googleAccount,
					configuredUserName));
			runStep(stepResults, "Detalles de la Cuenta", () -> validateDetallesCuenta(wait));
			runStep(stepResults, "Tus Negocios", () -> validateTusNegocios(wait));

			runStep(stepResults, "Términos y Condiciones", () -> {
				final String termsUrl = openLegalLinkValidateAndReturn(driver, wait, "Términos y Condiciones",
						"Términos y Condiciones", screenshotsDir, "step-8-terminos-y-condiciones.png");
				evidence.put("Términos y Condiciones URL", termsUrl);
			});

			runStep(stepResults, "Política de Privacidad", () -> {
				final String privacyUrl = openLegalLinkValidateAndReturn(driver, wait, "Política de Privacidad",
						"Política de Privacidad", screenshotsDir, "step-9-politica-de-privacidad.png");
				evidence.put("Política de Privacidad URL", privacyUrl);
			});
		} finally {
			writeReport(reportPath, stepResults, evidence);
			driver.quit();
		}

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, String> entry : stepResults.entrySet()) {
			if (!"PASS".equals(entry.getValue())) {
				failedSteps.add(entry.getKey());
			}
		}

		Assert.assertTrue("One or more SaleADS validations failed: " + failedSteps + ". Check report at "
				+ reportPath.toAbsolutePath(), failedSteps.isEmpty());
	}

	private void loginWithGoogle(final WebDriver driver, final WebDriverWait wait, final String googleAccount) {
		if (isEmpty(driver.getCurrentUrl()) || "data:,".equals(driver.getCurrentUrl()) || "about:blank".equals(driver.getCurrentUrl())) {
			throw new IllegalStateException(
					"Browser is not on a SaleADS login page. Provide -Dsaleads.login.url or SALEADS_LOGIN_URL.");
		}

		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickByAnyVisibleText(driver, wait, "Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Iniciar con Google", "Google");
		waitForUiToSettle(driver, wait);
		selectGoogleAccountIfVisible(driver, wait, handlesBeforeClick, googleAccount);

		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside|//nav")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Negocio') or contains(normalize-space(), 'Dashboard')]")));
	}

	private void openMiNegocioMenu(final WebDriver driver, final WebDriverWait wait) {
		clickByAnyVisibleText(driver, wait, "Mi Negocio", "Negocio");
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Agregar Negocio')]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Administrar Negocios')]")));
	}

	private void openAgregarNegocioModal(final WebDriver driver, final WebDriverWait wait) {
		clickByAnyVisibleText(driver, wait, "Agregar Negocio");
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Crear Nuevo Negocio')]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//input[@placeholder='Nombre del Negocio' or @name='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @id='Nombre del Negocio']|//*[contains(normalize-space(), 'Nombre del Negocio')]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Tienes 2 de 3 negocios')]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[normalize-space()='Cancelar']")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[normalize-space()='Crear Negocio']")));
	}

	private void optionalFillAndCancelNegocioModal(final WebDriver driver, final WebDriverWait wait) {
		final List<WebElement> nameInputs = driver.findElements(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @id='Nombre del Negocio']"));
		if (!nameInputs.isEmpty()) {
			nameInputs.get(0).click();
			nameInputs.get(0).clear();
			nameInputs.get(0).sendKeys("Negocio Prueba Automatización");
		}
		clickByAnyVisibleText(driver, wait, "Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Crear Nuevo Negocio')]")));
	}

	private void openAdministrarNegocios(final WebDriver driver, final WebDriverWait wait) {
		ensureMiNegocioExpanded(driver, wait);
		clickByAnyVisibleText(driver, wait, "Administrar Negocios");
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Información General')]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Detalles de la Cuenta')]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Tus Negocios')]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Sección Legal')]")));
	}

	private void validateInformacionGeneral(final WebDriver driver, final WebDriverWait wait, final String googleAccount,
			final String configuredUserName) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Información General')]")));

		if (isEmpty(configuredUserName)) {
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
					"(//h1|//h2|//h3|//h4|//p|//span)[string-length(normalize-space()) > 2 and not(contains(normalize-space(), '@')) and not(contains(normalize-space(), 'Información General')) and not(contains(normalize-space(), 'BUSINESS PLAN')) and not(contains(normalize-space(), 'Cambiar Plan'))]")));
		} else {
			wait.until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[contains(normalize-space(), " + asXpathLiteral(configuredUserName) + ")]")));
		}

		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), " + asXpathLiteral(googleAccount) + ")]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'BUSINESS PLAN')]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[normalize-space()='Cambiar Plan']")));
	}

	private void validateDetallesCuenta(final WebDriverWait wait) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Cuenta creada')]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Estado activo')]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Idioma seleccionado')]")));
	}

	private void validateTusNegocios(final WebDriverWait wait) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Tus Negocios')]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Agregar Negocio')]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), 'Tienes 2 de 3 negocios')]")));
	}

	private String openLegalLinkValidateAndReturn(final WebDriver driver, final WebDriverWait wait, final String linkText,
			final String headingText, final Path screenshotsDir, final String screenshotName) throws IOException {
		final String applicationWindow = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByAnyVisibleText(driver, wait, linkText);
		waitForUiToSettle(driver, wait);
		final String legalWindow = waitForNewWindow(driver, handlesBeforeClick, Duration.ofSeconds(10));
		final boolean openedInNewTab = legalWindow != null;

		if (openedInNewTab) {
			driver.switchTo().window(legalWindow);
			waitForUiToSettle(driver, wait);
		}

		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), " + asXpathLiteral(headingText) + ")]")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//body//*[string-length(normalize-space()) > 40]")));
		takeScreenshot(driver, screenshotsDir, screenshotName);

		final String legalUrl = driver.getCurrentUrl();

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(applicationWindow);
		} else {
			driver.navigate().back();
			waitForUiToSettle(driver, wait);
			if (!isEmpty(originalUrl)) {
				wait.until(ExpectedConditions.urlContains(extractHostPathHint(originalUrl)));
			}
		}

		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside|//nav")));
		return legalUrl;
	}

	private void ensureMiNegocioExpanded(final WebDriver driver, final WebDriverWait wait) {
		final List<WebElement> administrarNegocios = driver
				.findElements(By.xpath("//*[contains(normalize-space(), 'Administrar Negocios')]"));
		if (!administrarNegocios.isEmpty() && administrarNegocios.get(0).isDisplayed()) {
			return;
		}

		final List<String> expandCandidates = Arrays.asList("Mi Negocio", "Negocio");
		for (final String candidate : expandCandidates) {
			try {
				clickByAnyVisibleText(driver, wait, candidate);
				wait.until(ExpectedConditions.visibilityOfElementLocated(
						By.xpath("//*[contains(normalize-space(), 'Administrar Negocios')]")));
				return;
			} catch (final RuntimeException ignored) {
				// Continue trying the next candidate.
			}
		}
	}

	private void clickByAnyVisibleText(final WebDriver driver, final WebDriverWait wait, final String... texts) {
		RuntimeException failure = null;
		for (final String text : texts) {
			try {
				final By exact = By.xpath(
						"//*[self::button or self::a or self::span or self::div or @role='button'][normalize-space()="
								+ asXpathLiteral(text) + "]");
				final By contains = By.xpath(
						"//*[self::button or self::a or self::span or self::div or @role='button'][contains(normalize-space(), "
								+ asXpathLiteral(text) + ")]");

				final WebElement element = firstVisibleClickable(wait, exact, contains);
				element.click();
				waitForUiToSettle(driver, wait);
				return;
			} catch (final RuntimeException ex) {
				failure = ex;
			}
		}

		if (failure == null) {
			throw new IllegalStateException("No visible text candidates provided to click.");
		}
		throw failure;
	}

	private WebElement firstVisibleClickable(final WebDriverWait wait, final By... locators) {
		for (final By locator : locators) {
			try {
				return wait.until(ExpectedConditions.elementToBeClickable(locator));
			} catch (final TimeoutException ignored) {
				// Try next locator.
			}
		}
		throw new TimeoutException("Element was not clickable for any locator candidate.");
	}

	private void selectGoogleAccountIfVisible(final WebDriver driver, final WebDriverWait wait,
			final Set<String> handlesBeforeClick, final String googleAccount) {
		final String originalHandle = driver.getWindowHandle();
		final String popupHandle = waitForNewWindow(driver, handlesBeforeClick, Duration.ofSeconds(10));
		if (popupHandle != null) {
			driver.switchTo().window(popupHandle);
		}

		final By accountLocator = By.xpath("//*[contains(normalize-space(), " + asXpathLiteral(googleAccount) + ")]");
		try {
			final WebElement account = new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(ExpectedConditions.elementToBeClickable(accountLocator));
			account.click();
			waitForUiToSettle(driver, wait);
		} catch (final TimeoutException ignored) {
			// Account picker might be skipped if user is already authenticated.
		}

		if (popupHandle != null) {
			driver.switchTo().window(originalHandle);
		}
	}

	private String waitForNewWindow(final WebDriver driver, final Set<String> handlesBeforeClick,
			final Duration timeout) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			final Set<String> currentHandles = driver.getWindowHandles();
			for (final String handle : currentHandles) {
				if (!handlesBeforeClick.contains(handle)) {
					return handle;
				}
			}
			sleep(250);
		}
		return null;
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for UI.", interruptedException);
		}
	}

	private void waitForUiToSettle(final WebDriver driver, final WebDriverWait wait) {
		final ExpectedCondition<Boolean> domReady = webDriver -> {
			if (!(webDriver instanceof JavascriptExecutor)) {
				return true;
			}
			final Object readyState = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
			return "complete".equals(readyState) || "interactive".equals(readyState);
		};
		wait.until(domReady);
		sleep(600);
	}

	private void openLoginPageIfProvided(final WebDriver driver, final WebDriverWait wait) {
		final String loginUrl = systemValue("saleads.login.url", "SALEADS_LOGIN_URL", "");
		if (!isEmpty(loginUrl)) {
			driver.get(loginUrl);
			waitForUiToSettle(driver, wait);
		}
	}

	private WebDriver createDriver() {
		final String browser = systemValue("saleads.browser", "SALEADS_BROWSER", "chrome").toLowerCase();
		final boolean headless = Boolean.parseBoolean(systemValue("saleads.headless", "SALEADS_HEADLESS", "true"));

		if ("firefox".equals(browser)) {
			WebDriverManager.firefoxdriver().setup();
			final FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			options.addArguments("--width=1920");
			options.addArguments("--height=1080");
			return new FirefoxDriver(options);
		}

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		return new ChromeDriver(options);
	}

	private void runStep(final Map<String, String> report, final String reportField, final StepAction action) {
		try {
			action.run();
			report.put(reportField, "PASS");
		} catch (final Throwable throwable) {
			report.put(reportField, "FAIL");
			System.err.println("Step '" + reportField + "' failed: " + throwable.getMessage());
			throwable.printStackTrace(System.err);
		}
	}

	private Map<String, String> createDefaultReport() {
		final Map<String, String> result = new LinkedHashMap<>();
		for (final String field : REPORT_FIELDS) {
			result.put(field, "FAIL");
		}
		return result;
	}

	private void takeScreenshot(final WebDriver driver, final Path screenshotsDir, final String filename)
			throws IOException {
		Files.createDirectories(screenshotsDir);
		final File screenshotSource = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshotSource.toPath(), screenshotsDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeReport(final Path reportPath, final Map<String, String> stepResults, final Map<String, String> evidence)
			throws IOException {
		if (reportPath.getParent() != null) {
			Files.createDirectories(reportPath.getParent());
		}
		final StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		builder.append("  \"results\": {\n");
		int index = 0;
		for (final Map.Entry<String, String> entry : stepResults.entrySet()) {
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(escapeJson(entry.getValue())).append("\"");
			builder.append(index < stepResults.size() - 1 ? ",\n" : "\n");
			index++;
		}
		builder.append("  },\n");
		builder.append("  \"evidence\": {\n");
		int evidenceIndex = 0;
		for (final Map.Entry<String, String> entry : evidence.entrySet()) {
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(escapeJson(entry.getValue())).append("\"");
			builder.append(evidenceIndex < evidence.size() - 1 ? ",\n" : "\n");
			evidenceIndex++;
		}
		builder.append("  }\n");
		builder.append("}\n");

		Files.writeString(reportPath, builder.toString());
		System.out.println(builder);
	}

	private String systemValue(final String propertyKey, final String envKey, final String defaultValue) {
		final String systemProperty = System.getProperty(propertyKey);
		if (!isEmpty(systemProperty)) {
			return systemProperty.trim();
		}
		final String envValue = System.getenv(envKey);
		if (!isEmpty(envValue)) {
			return envValue.trim();
		}
		return defaultValue;
	}

	private String extractHostPathHint(final String url) {
		if (isEmpty(url)) {
			return "";
		}
		final int protocolIndex = url.indexOf("://");
		final String noProtocol = protocolIndex >= 0 ? url.substring(protocolIndex + 3) : url;
		final int firstSlash = noProtocol.indexOf('/');
		if (firstSlash < 0) {
			return noProtocol;
		}
		return noProtocol.substring(0, firstSlash + 1);
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char c = chars[i];
			if (c == '\'') {
				builder.append("\"'\"");
			} else {
				builder.append("'").append(c).append("'");
			}
			if (i < chars.length - 1) {
				builder.append(", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String escapeJson(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private boolean isEmpty(final String value) {
		return value == null || value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
