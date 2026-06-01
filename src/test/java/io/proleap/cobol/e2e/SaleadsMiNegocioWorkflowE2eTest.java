package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Opt-in Selenium E2E test for SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * Enable with:
 * </p>
 *
 * <pre>
 * SALEADS_E2E_ENABLED=true
 * SALEADS_LOGIN_URL=https://{env-host}/login
 * </pre>
 *
 * <p>
 * Optional:
 * </p>
 *
 * <pre>
 * SALEADS_HEADLESS=false
 * </pre>
 */
public class SaleadsMiNegocioWorkflowE2eTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_E2E_ENABLED", "false"));
		assumeTrue("Skipping SaleADS E2E test because SALEADS_E2E_ENABLED is not true.", enabled);

		final String loginUrl = Optional.ofNullable(System.getenv("SALEADS_LOGIN_URL")).orElse("").trim();
		assumeTrue("Skipping SaleADS E2E test because SALEADS_LOGIN_URL is not set.", !loginUrl.isEmpty());

		final Path artifactsDir = createArtifactsDir();
		final WorkflowReport report = new WorkflowReport(REPORT_FIELDS);

		WebDriver driver = null;
		try {
			driver = createChromeDriver();
			driver.get(loginUrl);
			waitForUiLoad(driver);

			runLoginStep(driver, artifactsDir, report);
			runMiNegocioMenuStep(driver, artifactsDir, report);
			runAgregarNegocioModalStep(driver, artifactsDir, report);
			runAdministrarNegociosStep(driver, artifactsDir, report);
			runInformacionGeneralStep(driver, report);
			runDetallesCuentaStep(driver, report);
			runTusNegociosStep(driver, report);
			runTerminosCondicionesStep(driver, artifactsDir, report);
			runPoliticaPrivacidadStep(driver, artifactsDir, report);
		} finally {
			if (driver != null) {
				driver.quit();
			}
			report.writeToMarkdown(artifactsDir.resolve("final-report.md"));
		}

		assertTrue("SaleADS workflow validation failed. See target/saleads-e2e/**/final-report.md for details.",
				report.allPassed());
	}

	private WebDriver createChromeDriver() {
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = !"false".equalsIgnoreCase(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));

		if (headless) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");
		return new ChromeDriver(options);
	}

	private void runLoginStep(final WebDriver driver, final Path artifactsDir, final WorkflowReport report) {
		report.execute("Login", () -> {
			if (!isVisibleTextPresent(driver, "Negocio", Duration.ofSeconds(4))) {
				clickByVisibleText(driver, Arrays.asList("Sign in with Google", "Iniciar sesión con Google",
						"Iniciar sesion con Google", "Continuar con Google", "Google"));
				waitForUiLoad(driver);

				selectGoogleAccountIfPrompted(driver, "juanlucasbarbiergarzon@gmail.com");
				waitForUiLoad(driver);
			}

			assertVisibleText(driver, "Negocio", DEFAULT_TIMEOUT);
			assertAnyVisible(driver, DEFAULT_TIMEOUT, By.xpath("//aside"), By.xpath("//nav"));
			captureScreenshot(driver, artifactsDir, "01-dashboard-loaded", false);
		});
	}

	private void runMiNegocioMenuStep(final WebDriver driver, final Path artifactsDir, final WorkflowReport report) {
		report.execute("Mi Negocio menu", () -> {
			expandMiNegocioMenu(driver);

			assertVisibleText(driver, "Agregar Negocio", DEFAULT_TIMEOUT);
			assertVisibleText(driver, "Administrar Negocios", DEFAULT_TIMEOUT);
			captureScreenshot(driver, artifactsDir, "02-mi-negocio-menu-expanded", false);
		});
	}

	private void runAgregarNegocioModalStep(final WebDriver driver, final Path artifactsDir, final WorkflowReport report) {
		report.execute("Agregar Negocio modal", () -> {
			clickByVisibleText(driver, Arrays.asList("Agregar Negocio"));
			waitForUiLoad(driver);

			assertVisibleText(driver, "Crear Nuevo Negocio", DEFAULT_TIMEOUT);
			assertVisibleText(driver, "Nombre del Negocio", DEFAULT_TIMEOUT);
			assertVisibleText(driver, "Tienes 2 de 3 negocios", DEFAULT_TIMEOUT);
			assertVisibleText(driver, "Cancelar", DEFAULT_TIMEOUT);
			assertVisibleText(driver, "Crear Negocio", DEFAULT_TIMEOUT);

			final Optional<WebElement> nombreInput = findVisibleElement(driver, Duration.ofSeconds(5),
					By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
					By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
					By.xpath("//input[@name='businessName' or @id='businessName']"));

			assertTrue("Expected input for 'Nombre del Negocio'.", nombreInput.isPresent());
			nombreInput.get().click();
			nombreInput.get().clear();
			nombreInput.get().sendKeys("Negocio Prueba Automatización");

			captureScreenshot(driver, artifactsDir, "03-agregar-negocio-modal", false);
			clickByVisibleText(driver, Arrays.asList("Cancelar"));
			waitForUiLoad(driver);
		});
	}

	private void runAdministrarNegociosStep(final WebDriver driver, final Path artifactsDir, final WorkflowReport report) {
		report.execute("Administrar Negocios view", () -> {
			expandMiNegocioMenu(driver);
			clickByVisibleText(driver, Arrays.asList("Administrar Negocios"));
			waitForUiLoad(driver);

			assertVisibleText(driver, "Información General", DEFAULT_TIMEOUT);
			assertVisibleText(driver, "Detalles de la Cuenta", DEFAULT_TIMEOUT);
			assertVisibleText(driver, "Tus Negocios", DEFAULT_TIMEOUT);
			assertVisibleText(driver, "Sección Legal", DEFAULT_TIMEOUT);
			captureScreenshot(driver, artifactsDir, "04-administrar-negocios-view", true);
		});
	}

	private void runInformacionGeneralStep(final WebDriver driver, final WorkflowReport report) {
		report.execute("Información General", () -> {
			assertVisibleText(driver, "Información General", DEFAULT_TIMEOUT);
			assertAnyVisible(driver, DEFAULT_TIMEOUT,
					By.xpath("//*[contains(normalize-space(.), 'Información General')]/ancestor::*[self::section or self::div][1]//*[contains(normalize-space(.), '@')]"),
					By.xpath("//*[contains(normalize-space(.), '@')]"));
			assertAnyVisible(driver, DEFAULT_TIMEOUT,
					By.xpath("//*[contains(normalize-space(.), 'Información General')]/ancestor::*[self::section or self::div][1]//*[string-length(normalize-space(.)) > 2 and not(contains(normalize-space(.), '@')) and not(self::h1) and not(self::h2) and not(self::h3)]"));
			assertAnyVisible(driver, DEFAULT_TIMEOUT,
					By.xpath("//*[contains(translate(normalize-space(.), 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), 'BUSINESS PLAN')]"));
			assertVisibleText(driver, "Cambiar Plan", DEFAULT_TIMEOUT);
		});
	}

	private void runDetallesCuentaStep(final WebDriver driver, final WorkflowReport report) {
		report.execute("Detalles de la Cuenta", () -> {
			assertVisibleText(driver, "Cuenta creada", DEFAULT_TIMEOUT);
			assertVisibleText(driver, "Estado activo", DEFAULT_TIMEOUT);
			assertVisibleText(driver, "Idioma seleccionado", DEFAULT_TIMEOUT);
		});
	}

	private void runTusNegociosStep(final WebDriver driver, final WorkflowReport report) {
		report.execute("Tus Negocios", () -> {
			assertVisibleText(driver, "Tus Negocios", DEFAULT_TIMEOUT);
			assertVisibleText(driver, "Agregar Negocio", DEFAULT_TIMEOUT);
			assertVisibleText(driver, "Tienes 2 de 3 negocios", DEFAULT_TIMEOUT);
			assertAnyVisible(driver, DEFAULT_TIMEOUT,
					By.xpath("//section[contains(., 'Tus Negocios')]//ul"),
					By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]/following::*[self::ul or self::table][1]"));
		});
	}

	private void runTerminosCondicionesStep(final WebDriver driver, final Path artifactsDir, final WorkflowReport report) {
		report.execute("Términos y Condiciones", () -> {
			final String legalUrl = openLegalContentAndReturn(driver, artifactsDir, "Términos y Condiciones",
					"08-terminos-y-condiciones");
			report.addDetail("Términos y Condiciones", "URL: " + legalUrl);
		});
	}

	private void runPoliticaPrivacidadStep(final WebDriver driver, final Path artifactsDir, final WorkflowReport report) {
		report.execute("Política de Privacidad", () -> {
			final String legalUrl = openLegalContentAndReturn(driver, artifactsDir, "Política de Privacidad",
					"09-politica-privacidad");
			report.addDetail("Política de Privacidad", "URL: " + legalUrl);
		});
	}

	private String openLegalContentAndReturn(final WebDriver driver, final Path artifactsDir, final String legalText,
			final String screenshotName) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String currentUrl = driver.getCurrentUrl();

		clickByVisibleText(driver, Arrays.asList(legalText));
		waitForNavigationOrNewTab(driver, beforeClick, currentUrl);

		final Set<String> afterClick = driver.getWindowHandles();
		final Set<String> newHandles = new LinkedHashSet<>(afterClick);
		newHandles.removeAll(beforeClick);

		final boolean openedNewTab = !newHandles.isEmpty();
		final String legalWindow = openedNewTab ? newHandles.iterator().next() : appWindow;

		if (openedNewTab) {
			driver.switchTo().window(legalWindow);
		}

		waitForUiLoad(driver);
		assertVisibleText(driver, legalText, DEFAULT_TIMEOUT);
		assertAnyVisible(driver, DEFAULT_TIMEOUT,
				By.xpath("//main//*[string-length(normalize-space(.)) > 100]"),
				By.xpath("//article//*[string-length(normalize-space(.)) > 100]"),
				By.xpath("//body//*[string-length(normalize-space(.)) > 150]"));

		captureScreenshot(driver, artifactsDir, screenshotName, true);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else if (!currentUrl.equals(finalUrl)) {
			driver.navigate().back();
			waitForUiLoad(driver);
		}

		return finalUrl;
	}

	private void expandMiNegocioMenu(final WebDriver driver) {
		if (isVisibleTextPresent(driver, "Agregar Negocio", Duration.ofSeconds(3))
				&& isVisibleTextPresent(driver, "Administrar Negocios", Duration.ofSeconds(3))) {
			return;
		}

		clickIfVisible(driver, Duration.ofSeconds(5), Arrays.asList("Negocio"));
		clickByVisibleText(driver, Arrays.asList("Mi Negocio"));
		waitForUiLoad(driver);
	}

	private void selectGoogleAccountIfPrompted(final WebDriver driver, final String accountEmail) {
		final String originalHandle = driver.getWindowHandle();
		try {
			final Set<String> handles = driver.getWindowHandles();
			for (final String handle : handles) {
				driver.switchTo().window(handle);
				if (isVisibleTextPresent(driver, accountEmail, Duration.ofSeconds(3))) {
					clickByVisibleText(driver, Arrays.asList(accountEmail));
					waitForUiLoad(driver);
					break;
				}
			}
		} catch (final NoSuchWindowException ignored) {
			// The OAuth popup may close itself after account selection.
		} finally {
			if (driver.getWindowHandles().contains(originalHandle)) {
				driver.switchTo().window(originalHandle);
			}
		}
	}

	private void waitForNavigationOrNewTab(final WebDriver driver, final Set<String> handlesBefore,
			final String currentUrl) {
		final WebDriverWait wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		wait.until((ExpectedCondition<Boolean>) d -> {
			if (d == null) {
				return false;
			}

			if (d.getWindowHandles().size() > handlesBefore.size()) {
				return true;
			}

			return !currentUrl.equals(d.getCurrentUrl());
		});
	}

	private void clickByVisibleText(final WebDriver driver, final List<String> candidates) {
		final List<By> locators = new ArrayList<>();
		for (final String candidate : candidates) {
			final String quoted = quoteXPath(candidate);
			locators.add(By.xpath("//button[contains(normalize-space(.), " + quoted + ")]"));
			locators.add(By.xpath("//a[contains(normalize-space(.), " + quoted + ")]"));
			locators.add(By.xpath("//*[@role='button' and contains(normalize-space(.), " + quoted + ")]"));
			locators.add(By.xpath(
					"//*[contains(normalize-space(.), " + quoted + ")]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"));
		}

		WebElement target = null;
		for (final By locator : locators) {
			try {
				target = new WebDriverWait(driver, Duration.ofSeconds(4))
						.until(ExpectedConditions.elementToBeClickable(locator));
				break;
			} catch (final TimeoutException ignored) {
				// Try next locator.
			}
		}

		assertTrue("Could not click any element matching visible text candidates: " + candidates, target != null);
		target.click();
		waitForUiLoad(driver);
	}

	private void clickIfVisible(final WebDriver driver, final Duration timeout, final List<String> candidates) {
		for (final String candidate : candidates) {
			final Optional<WebElement> element = findVisibleElement(driver, timeout,
					By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.), "
							+ quoteXPath(candidate) + ")]"));

			if (element.isPresent()) {
				new Actions(driver).moveToElement(element.get()).pause(Duration.ofMillis(150)).perform();
				element.get().click();
				waitForUiLoad(driver);
				return;
			}
		}
	}

	private void assertVisibleText(final WebDriver driver, final String text, final Duration timeout) {
		final String quoted = quoteXPath(text);
		final By locator = By.xpath("//*[contains(normalize-space(.), " + quoted + ")]");
		new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private boolean isVisibleTextPresent(final WebDriver driver, final String text, final Duration timeout) {
		final String quoted = quoteXPath(text);
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions
					.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.), " + quoted + ")]")));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private Optional<WebElement> findVisibleElement(final WebDriver driver, final Duration timeout, final By... locators) {
		for (final By locator : locators) {
			try {
				final WebElement visible = new WebDriverWait(driver, timeout)
						.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return Optional.ofNullable(visible);
			} catch (final TimeoutException ignored) {
				// Try next locator.
			}
		}

		return Optional.empty();
	}

	private void assertAnyVisible(final WebDriver driver, final Duration timeout, final By... locators) {
		final Optional<WebElement> found = findVisibleElement(driver, timeout, locators);
		assertTrue("Expected at least one locator to be visible.", found.isPresent());
	}

	private void waitForUiLoad(final WebDriver driver) {
		new WebDriverWait(driver, DEFAULT_TIMEOUT).until(d -> {
			if (d == null) {
				return false;
			}

			final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(readyState));
		});
	}

	private Path createArtifactsDir() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path dir = Path.of("target", "saleads-e2e", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private Path captureScreenshot(final WebDriver driver, final Path artifactsDir, final String name,
			final boolean fullPage) throws IOException {
		final TakesScreenshot screenshotDriver = (TakesScreenshot) driver;
		Dimension originalSize = null;

		if (fullPage) {
			originalSize = driver.manage().window().getSize();
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			final Number width = (Number) js.executeScript(
					"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, window.innerWidth);");
			final Number height = (Number) js.executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, window.innerHeight);");

			final int targetWidth = Math.min(Math.max(width.intValue(), 1280), 2400);
			final int targetHeight = Math.min(Math.max(height.intValue(), 900), 12000);
			driver.manage().window().setSize(new Dimension(targetWidth, targetHeight));
			waitForUiLoad(driver);
		}

		final File source = screenshotDriver.getScreenshotAs(OutputType.FILE);
		final Path screenshot = artifactsDir.resolve(name + ".png");
		Files.copy(source.toPath(), screenshot, StandardCopyOption.REPLACE_EXISTING);

		if (originalSize != null) {
			driver.manage().window().setSize(originalSize);
			waitForUiLoad(driver);
		}

		return screenshot;
	}

	private String quoteXPath(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
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

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}

	private static final class WorkflowReport {
		private final Map<String, StepResult> results = new LinkedHashMap<>();

		private WorkflowReport(final List<String> orderedFields) {
			for (final String field : orderedFields) {
				results.put(field, StepResult.pending());
			}
		}

		private void execute(final String field, final CheckedStep step) {
			try {
				step.run();
				results.put(field, StepResult.pass());
			} catch (final Throwable ex) {
				results.put(field, StepResult.fail(ex.getMessage()));
			}
		}

		private void addDetail(final String field, final String detail) {
			final StepResult existing = results.get(field);
			if (existing == null) {
				return;
			}

			results.put(field, existing.withAdditionalDetail(detail));
		}

		private boolean allPassed() {
			return results.values().stream().allMatch(StepResult::isPass);
		}

		private void writeToMarkdown(final Path outputFile) throws IOException {
			final StringBuilder markdown = new StringBuilder();
			markdown.append("# SaleADS Mi Negocio Workflow Report\n\n");
			markdown.append("| Validation | Status | Detail |\n");
			markdown.append("|---|---|---|\n");

			for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
				markdown.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue().status)
						.append(" | ")
						.append(entry.getValue().detail.replace("|", "\\|").replace("\n", "<br/>"))
						.append(" |\n");
			}

			Files.writeString(outputFile, markdown.toString());
		}
	}

	private static final class StepResult {
		private final String status;
		private final String detail;

		private StepResult(final String status, final String detail) {
			this.status = status;
			this.detail = detail;
		}

		private static StepResult pending() {
			return new StepResult("PENDING", "");
		}

		private static StepResult pass() {
			return new StepResult("PASS", "");
		}

		private static StepResult fail(final String detail) {
			final String normalized = Optional.ofNullable(detail).orElse("No error message");
			return new StepResult("FAIL", normalized);
		}

		private boolean isPass() {
			return "PASS".equals(status);
		}

		private StepResult withAdditionalDetail(final String extra) {
			if (extra == null || extra.isBlank()) {
				return this;
			}

			if (detail == null || detail.isBlank()) {
				return new StepResult(status, extra);
			}

			return new StepResult(status, detail + "\n" + extra);
		}
	}
}
