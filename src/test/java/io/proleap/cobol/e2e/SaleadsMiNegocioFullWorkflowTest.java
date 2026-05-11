package io.proleap.cobol.e2e;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
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

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String applicationWindowHandle;

	private final Map<String, StepResult> finalReport = new LinkedHashMap<>();

	@Before
	public void setUp() throws Exception {
		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", null);
		Assume.assumeTrue(
				"Set system property saleads.login.url or env SALEADS_LOGIN_URL with the current SaleADS login URL.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean
				.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", Boolean.TRUE.toString()));
		final int timeoutSeconds = Integer
				.parseInt(readConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", "30"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu");

		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		evidenceDir = Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		driver.get(loginUrl);
		waitForUiLoad();
		applicationWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		executeStep("Login", () -> {
			loginWithGoogle();
			validateMainInterfaceAndSidebar();
			captureScreenshot("01-dashboard-loaded");
		});

		executeStep("Mi Negocio menu", () -> {
			openMiNegocioMenu();
			assertVisibleText("Agregar Negocio");
			assertVisibleText("Administrar Negocios");
			captureScreenshot("02-mi-negocio-menu-expanded");
		});

		executeStep("Agregar Negocio modal", () -> {
			clickByText("Agregar Negocio");
			assertVisibleText("Crear Nuevo Negocio");
			assertNombreDelNegocioInputVisible();
			assertVisibleText("Tienes 2 de 3 negocios");
			assertVisibleText("Cancelar");
			assertVisibleText("Crear Negocio");

			final WebElement nombreDelNegocio = findNombreDelNegocioInput();
			nombreDelNegocio.click();
			nombreDelNegocio.clear();
			nombreDelNegocio.sendKeys("Negocio Prueba Automatización");
			captureScreenshot("03-agregar-negocio-modal");
			clickByText("Cancelar");
		});

		executeStep("Administrar Negocios view", () -> {
			if (!isTextVisible("Administrar Negocios", 2)) {
				clickByText("Mi Negocio");
			}

			clickByText("Administrar Negocios");
			assertVisibleText("Información General");
			assertVisibleText("Detalles de la Cuenta");
			assertVisibleText("Tus Negocios");
			assertVisibleText("Sección Legal");
			captureScreenshot("04-administrar-negocios");
			applicationWindowHandle = driver.getWindowHandle();
		});

		executeStep("Información General", () -> {
			assertVisibleText("BUSINESS PLAN");
			assertVisibleText("Cambiar Plan");

			final String pageText = driver.findElement(By.tagName("body")).getText();
			Assert.assertTrue("Expected user email to be visible in the page.",
					EMAIL_PATTERN.matcher(pageText).find());
			Assert.assertTrue("Expected user name to be visible in the page.",
					pageText.contains("Nombre") || hasLikelyName(pageText));
		});

		executeStep("Detalles de la Cuenta", () -> {
			assertVisibleText("Cuenta creada");
			assertVisibleText("Estado activo");
			assertVisibleText("Idioma seleccionado");
		});

		executeStep("Tus Negocios", () -> {
			assertVisibleText("Tus Negocios");
			assertVisibleText("Agregar Negocio");
			assertVisibleText("Tienes 2 de 3 negocios");
			Assert.assertTrue("Expected business list/content to be visible.", hasBusinessContent());
		});

		executeStep("Términos y Condiciones", () -> {
			validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos-y-condiciones");
		});

		executeStep("Política de Privacidad", () -> {
			validateLegalLink("Política de Privacidad", "Política de Privacidad", "09-politica-de-privacidad");
		});

		printFinalReport();

		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : finalReport.entrySet()) {
			if (!entry.getValue().passed) {
				failed.add(entry.getKey());
			}
		}

		Assert.assertTrue("Workflow finished with failed validations: " + failed, failed.isEmpty());
	}

	private void executeStep(final String stepName, final StepAction action) {
		try {
			action.run();
			finalReport.put(stepName, new StepResult(true, "PASS"));
		} catch (final Throwable error) {
			finalReport.put(stepName, new StepResult(false, error.getMessage()));
		}
	}

	private void loginWithGoogle() throws InterruptedException {
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickFirstByText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Login with Google", "Google"));

		final String maybeGoogleWindow = waitForNewWindow(handlesBeforeClick, 8);
		if (maybeGoogleWindow != null) {
			driver.switchTo().window(maybeGoogleWindow);
			waitForUiLoad();
		}

		if (isTextVisible(GOOGLE_ACCOUNT_EMAIL, 5)) {
			clickByText(GOOGLE_ACCOUNT_EMAIL);
		}

		waitForUiLoad();

		if (!driver.getWindowHandle().equals(applicationWindowHandle)
				&& driver.getWindowHandles().contains(applicationWindowHandle)) {
			driver.switchTo().window(applicationWindowHandle);
		}
	}

	private void validateMainInterfaceAndSidebar() {
		waitUntilAnyVisible(Arrays.asList(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[@role='navigation']")));
		final boolean sidebarVisible = isAnyVisible(
				Arrays.asList(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[@role='navigation']")), 10);
		Assert.assertTrue("Expected left sidebar navigation to be visible.", sidebarVisible);
	}

	private void openMiNegocioMenu() throws InterruptedException {
		waitUntilAnyVisible(Arrays.asList(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[@role='navigation']")));
		if (isTextVisible("Negocio", 2)) {
			clickByText("Negocio");
		}
		clickByText("Mi Negocio");
	}

	private void validateLegalLink(final String linkText, final String expectedHeading, final String screenshotPrefix)
			throws IOException, InterruptedException {
		final String originalHandle = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByText(linkText);
		final String newTabHandle = waitForNewWindow(handlesBefore, 6);
		boolean openedInNewTab = false;

		if (newTabHandle != null) {
			driver.switchTo().window(newTabHandle);
			openedInNewTab = true;
		}

		waitForUiLoad();
		assertVisibleText(expectedHeading);

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Expected legal content text to be visible.", bodyText != null && bodyText.trim().length() > 120);

		captureScreenshot(screenshotPrefix);
		System.out.println(linkText + " URL: " + driver.getCurrentUrl());

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else if (!driver.getCurrentUrl().equals(originalUrl)) {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void clickByText(final String text) throws InterruptedException {
		final String literal = toXPathLiteral(text);
		final List<By> locators = Arrays.asList(
				By.xpath("//button[normalize-space(.)=" + literal + "]"),
				By.xpath("//a[normalize-space(.)=" + literal + "]"),
				By.xpath("//*[@role='button' and normalize-space(.)=" + literal + "]"),
				By.xpath("//*[normalize-space(.)=" + literal + "]"));

		Exception lastError = null;
		for (final By locator : locators) {
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
				scrollIntoView(element);
				element.click();
				waitForUiLoad();
				return;
			} catch (final Exception error) {
				lastError = error;
			}
		}

		throw new AssertionError("Unable to click element with visible text: " + text, lastError);
	}

	private void clickFirstByText(final List<String> candidateTexts) throws InterruptedException {
		Exception lastError = null;
		for (final String text : candidateTexts) {
			try {
				clickByText(text);
				return;
			} catch (final Exception error) {
				lastError = error;
			}
		}

		throw new AssertionError("Unable to click any candidate text: " + candidateTexts, lastError);
	}

	private void assertVisibleText(final String text) {
		final String literal = toXPathLiteral(text);
		final By locator = By.xpath("//*[normalize-space(.)=" + literal + " or contains(normalize-space(.), " + literal + ")]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private boolean isTextVisible(final String text, final int timeoutSeconds) {
		final String literal = toXPathLiteral(text);
		final By locator = By.xpath("//*[normalize-space(.)=" + literal + " or contains(normalize-space(.), " + literal + ")]");
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException error) {
			return false;
		}
	}

	private void assertNombreDelNegocioInputVisible() {
		final WebElement input = findNombreDelNegocioInput();
		Assert.assertTrue("Expected 'Nombre del Negocio' input to be visible.", input.isDisplayed());
	}

	private WebElement findNombreDelNegocioInput() {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]")));
	}

	private boolean hasLikelyName(final String text) {
		final String[] lines = text.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.length() < 3) {
				continue;
			}
			if (line.equalsIgnoreCase("BUSINESS PLAN") || line.equalsIgnoreCase("Cambiar Plan")
					|| line.equalsIgnoreCase("Información General")) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private boolean hasBusinessContent() {
		try {
			final WebElement section = wait.until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[normalize-space(.)='Tus Negocios']/ancestor::*[self::section or self::div][1]")));
			final List<WebElement> candidates = section
					.findElements(By.xpath(".//li | .//tr | .//article | .//div[contains(@class, 'business')]"));
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed() && candidate.getText() != null && !candidate.getText().trim().isEmpty()) {
					return true;
				}
			}
			return section.getText() != null && section.getText().trim().length() > 40;
		} catch (final Exception error) {
			return false;
		}
	}

	private void waitForUiLoad() throws InterruptedException {
		wait.until(driverInstance -> "complete"
				.equals(((JavascriptExecutor) driverInstance).executeScript("return document.readyState")));
		Thread.sleep(500L);
	}

	private String waitForNewWindow(final Set<String> handlesBefore, final int timeoutSeconds) {
		try {
			return new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(driverInstance -> {
				final Set<String> currentHandles = driverInstance.getWindowHandles();
				for (final String handle : currentHandles) {
					if (!handlesBefore.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException error) {
			return null;
		}
	}

	private void waitUntilAnyVisible(final List<By> locators) {
		wait.until(driverInstance -> {
			for (final By locator : locators) {
				final List<WebElement> elements = driverInstance.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			}
			return false;
		});
	}

	private boolean isAnyVisible(final List<By> locators, final int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(driverInstance -> {
				for (final By locator : locators) {
					final List<WebElement> elements = driverInstance.findElements(locator);
					for (final WebElement element : elements) {
						if (element.isDisplayed()) {
							return true;
						}
					}
				}
				return false;
			});
			return true;
		} catch (final TimeoutException error) {
			return false;
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path targetFile = evidenceDir.resolve(checkpointName + ".png");
		Files.copy(screenshot.toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING);
	}

	private String readConfig(final String systemPropertyName, final String envVarName, final String defaultValue) {
		final String systemPropertyValue = System.getProperty(systemPropertyName);
		if (systemPropertyValue != null && !systemPropertyValue.isBlank()) {
			return systemPropertyValue.trim();
		}

		final String envValue = System.getenv(envVarName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private String toXPathLiteral(final String value) {
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

	private void printFinalReport() {
		System.out.println("==== Final Report: saleads_mi_negocio_full_test ====");
		for (final Map.Entry<String, StepResult> entry : finalReport.entrySet()) {
			final String status = entry.getValue().passed ? "PASS" : "FAIL";
			final String detail = entry.getValue().detail == null ? "" : " - " + entry.getValue().detail;
			System.out.println(entry.getKey() + ": " + status + detail);
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private static final class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
