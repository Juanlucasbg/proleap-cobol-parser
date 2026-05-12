package io.proleap.cobol;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String loginUrl = getRequiredValue("saleads.login.url", "SALEADS_LOGIN_URL");
		final Path evidenceDir = createEvidenceDirectory();

		final Map<String, Boolean> stepStatus = new LinkedHashMap<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		final ChromeOptions options = createChromeOptions();
		final WebDriver driver = new ChromeDriver(options);
		final WebDriverWait wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		try {
			driver.get(loginUrl);
			waitForUiToLoad(driver, wait);

			stepStatus.put("Login", executeStep(driver, wait, evidenceDir, "login-dashboard", () -> {
				clickByVisibleText(driver, wait, "Sign in with Google", "Iniciar sesión con Google", "Google");
				waitForUiToLoad(driver, wait);
				selectGoogleAccountIfPrompted(driver, wait);

				assertVisible(driver, wait,
						By.xpath("//*[normalize-space()='Negocio' or contains(normalize-space(),'Negocio')]"),
						"main application interface");
				assertVisible(driver, wait, By.xpath(
						"//aside | //nav[contains(@class, 'sidebar')] | //div[contains(@class, 'sidebar')]"),
						"left sidebar");
				captureScreenshot(driver, evidenceDir, "01-dashboard-loaded");
			}));

			stepStatus.put("Mi Negocio menu", executeStep(driver, wait, evidenceDir, "mi-negocio-menu", () -> {
				clickByVisibleText(driver, wait, "Mi Negocio");
				assertVisibleByText(driver, wait, "Agregar Negocio");
				assertVisibleByText(driver, wait, "Administrar Negocios");
				captureScreenshot(driver, evidenceDir, "02-mi-negocio-expanded");
			}));

			stepStatus.put("Agregar Negocio modal",
					executeStep(driver, wait, evidenceDir, "agregar-negocio-modal", () -> {
						clickByVisibleText(driver, wait, "Agregar Negocio");
						assertVisibleByText(driver, wait, "Crear Nuevo Negocio");
						assertVisibleByText(driver, wait, "Nombre del Negocio");
						assertVisibleByText(driver, wait, "Tienes 2 de 3 negocios");
						assertVisibleByText(driver, wait, "Cancelar");
						assertVisibleByText(driver, wait, "Crear Negocio");

						final WebElement businessNameInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
								By.xpath("//input[@placeholder='Nombre del Negocio' or @name='Nombre del Negocio' "
										+ "or @aria-label='Nombre del Negocio' or "
										+ "ancestor::*[contains(., 'Nombre del Negocio')]//input]")));
						businessNameInput.click();
						businessNameInput.clear();
						businessNameInput.sendKeys("Negocio Prueba Automatización");
						clickByVisibleText(driver, wait, "Cancelar");
						captureScreenshot(driver, evidenceDir, "03-agregar-negocio-modal");
					}));

			stepStatus.put("Administrar Negocios view",
					executeStep(driver, wait, evidenceDir, "administrar-negocios-view", () -> {
						if (!isVisibleByText(driver, "Administrar Negocios")) {
							clickByVisibleText(driver, wait, "Mi Negocio");
						}

						clickByVisibleText(driver, wait, "Administrar Negocios");
						assertVisibleByText(driver, wait, "Información General");
						assertVisibleByText(driver, wait, "Detalles de la Cuenta");
						assertVisibleByText(driver, wait, "Tus Negocios");
						assertVisibleByText(driver, wait, "Sección Legal");
						captureScreenshot(driver, evidenceDir, "04-administrar-negocios");
					}));

			stepStatus.put("Información General",
					executeStep(driver, wait, evidenceDir, "informacion-general", () -> {
						assertVisible(driver, wait, By.xpath("//*[contains(@class, 'name') or contains(@class, 'user')]"),
								"user name");
						assertVisible(driver, wait, By.xpath("//*[contains(text(), '@')]"), "user email");
						assertVisibleByText(driver, wait, "BUSINESS PLAN");
						assertVisibleByText(driver, wait, "Cambiar Plan");
					}));

			stepStatus.put("Detalles de la Cuenta",
					executeStep(driver, wait, evidenceDir, "detalles-cuenta", () -> {
						assertVisibleByText(driver, wait, "Cuenta creada");
						assertVisibleByText(driver, wait, "Estado activo");
						assertVisibleByText(driver, wait, "Idioma seleccionado");
					}));

			stepStatus.put("Tus Negocios", executeStep(driver, wait, evidenceDir, "tus-negocios", () -> {
				assertVisibleByText(driver, wait, "Tus Negocios");
				assertVisibleByText(driver, wait, "Agregar Negocio");
				assertVisibleByText(driver, wait, "Tienes 2 de 3 negocios");
			}));

			stepStatus.put("Términos y Condiciones",
					executeStep(driver, wait, evidenceDir, "terminos-y-condiciones", () -> {
						final String finalUrl = validateLegalPage(driver, wait, evidenceDir, "Términos y Condiciones",
								"Términos y Condiciones", "05-terminos-y-condiciones");
						legalUrls.put("Términos y Condiciones", finalUrl);
					}));

			stepStatus.put("Política de Privacidad",
					executeStep(driver, wait, evidenceDir, "politica-de-privacidad", () -> {
						final String finalUrl = validateLegalPage(driver, wait, evidenceDir, "Política de Privacidad",
								"Política de Privacidad", "06-politica-de-privacidad");
						legalUrls.put("Política de Privacidad", finalUrl);
					}));
		} finally {
			writeFinalReport(evidenceDir, stepStatus, legalUrls);
			driver.quit();
		}

		assertFalse("Some SaleADS Mi Negocio validations failed. See report at: " + evidenceDir.toAbsolutePath(),
				stepStatus.containsValue(Boolean.FALSE));
	}

	private boolean executeStep(final WebDriver driver, final WebDriverWait wait, final Path evidenceDir,
			final String failureScreenshotName, final Step step) {
		try {
			step.run();
			return true;
		} catch (final Exception ex) {
			captureScreenshot(driver, evidenceDir, "error-" + failureScreenshotName);
			return false;
		}
	}

	private String validateLegalPage(final WebDriver driver, final WebDriverWait wait, final Path evidenceDir,
			final String linkText, final String expectedHeading, final String screenshotName) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String urlBeforeClick = driver.getCurrentUrl();

		clickByVisibleText(driver, wait, linkText);

		wait.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size()
				|| !urlBeforeClick.equalsIgnoreCase(d.getCurrentUrl()));

		final Set<String> handlesAfterClick = driver.getWindowHandles();
		final boolean openedNewTab = handlesAfterClick.size() > handlesBeforeClick.size();

		if (openedNewTab) {
			for (final String handle : handlesAfterClick) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		waitForUiToLoad(driver, wait);
		assertVisibleByText(driver, wait, expectedHeading);
		assertVisible(driver, wait,
				By.xpath("//main//*[string-length(normalize-space()) > 80] | //article//*[string-length(normalize-space()) > 80]"),
				"legal content");
		captureScreenshot(driver, evidenceDir, screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad(driver, wait);
		return finalUrl;
	}

	private void selectGoogleAccountIfPrompted(final WebDriver driver, final WebDriverWait wait) {
		try {
			final WebDriverWait googleWait = new WebDriverWait(driver, Duration.ofSeconds(8));
			final WebElement accountElement = googleWait
					.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[contains(text(), '" + GOOGLE_ACCOUNT + "')]")));
			accountElement.click();
			waitForUiToLoad(driver, wait);
		} catch (final Exception ignored) {
			// Google account chooser may not appear if user is already authenticated.
		}
	}

	private void clickByVisibleText(final WebDriver driver, final WebDriverWait wait, final String... candidates) {
		Exception lastException = null;

		for (final String candidate : candidates) {
			try {
				final By locator = By.xpath(
						"(//button[normalize-space()='" + candidate + "' or contains(normalize-space(),'" + candidate + "')]"
								+ "|//a[normalize-space()='" + candidate + "' or contains(normalize-space(),'" + candidate + "')]"
								+ "|//*[@role='button'][normalize-space()='" + candidate
								+ "' or contains(normalize-space(),'" + candidate + "')]"
								+ "|//*[@role='menuitem'][normalize-space()='" + candidate
								+ "' or contains(normalize-space(),'" + candidate + "')]"
								+ "|//span[normalize-space()='" + candidate + "' or contains(normalize-space(),'" + candidate + "')]/ancestor::*[self::button or self::a][1])[1]");
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
				element.click();
				waitForUiToLoad(driver, wait);
				return;
			} catch (final Exception ex) {
				lastException = ex;
			}
		}

		throw new IllegalStateException("Could not click any element with visible text: " + String.join(", ", candidates),
				lastException);
	}

	private boolean isVisibleByText(final WebDriver driver, final String text) {
		return !driver.findElements(By.xpath("//*[normalize-space()='" + text + "' or contains(normalize-space(),'" + text + "')]"))
				.isEmpty();
	}

	private void assertVisibleByText(final WebDriver driver, final WebDriverWait wait, final String text) {
		assertVisible(driver, wait, By.xpath("//*[normalize-space()='" + text + "' or contains(normalize-space(),'" + text + "')]"),
				text);
	}

	private void assertVisible(final WebDriver driver, final WebDriverWait wait, final By locator,
			final String description) {
		final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		assertTrue("Expected visible element: " + description, element.isDisplayed());
		waitForUiToLoad(driver, wait);
	}

	private void waitForUiToLoad(final WebDriver driver, final WebDriverWait wait) {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		wait.until(d -> !Boolean.TRUE.equals(
				((JavascriptExecutor) d).executeScript("return !!document.querySelector('[aria-busy=\"true\"], .spinner, .loading')")));
	}

	private void captureScreenshot(final WebDriver driver, final Path evidenceDir, final String name) {
		try {
			final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(evidenceDir.resolve(name + ".png"), screenshot);
		} catch (final Exception ignored) {
			// Best effort screenshot for evidence; failures should not hide assertion outcomes.
		}
	}

	private ChromeOptions createChromeOptions() {
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless",
				System.getenv().getOrDefault("SALEADS_HEADLESS", "true")));

		if (headless) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		return options;
	}

	private Path createEvidenceDirectory() throws IOException {
		final String runStamp = TIMESTAMP_FORMATTER.format(Instant.now());
		final Path folder = Paths.get("target", "saleads-evidence", runStamp);
		Files.createDirectories(folder);
		return folder;
	}

	private void writeFinalReport(final Path evidenceDir, final Map<String, Boolean> stepStatus,
			final Map<String, String> legalUrls) throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio full workflow report\n");
		report.append("======================================\n\n");

		for (final Map.Entry<String, Boolean> entry : stepStatus.entrySet()) {
			report.append(entry.getKey()).append(": ").append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL")
					.append('\n');
		}

		report.append("\nFinal URLs\n");
		report.append("----------\n");

		if (legalUrls.isEmpty()) {
			report.append("No legal URLs captured.\n");
		} else {
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				report.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), report.toString(), StandardCharsets.UTF_8);
	}

	private String getRequiredValue(final String systemPropertyName, final String envName) {
		final String value = System.getProperty(systemPropertyName, System.getenv(envName));
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException(
					"Missing required login URL. Provide -" + systemPropertyName + "=<login_url> or " + envName
							+ " environment variable.");
		}
		return value.trim();
	}

	@FunctionalInterface
	private interface Step {
		void run() throws Exception;
	}
}
