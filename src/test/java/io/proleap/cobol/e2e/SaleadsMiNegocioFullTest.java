package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(25);
	private static final Duration CLICK_WAIT = Duration.ofSeconds(10);
	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-evidence");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		assumeTrue("Skipping SaleADS E2E test. Set RUN_SALEADS_E2E=true to enable.",
				Boolean.parseBoolean(env("RUN_SALEADS_E2E", "false")));
		Files.createDirectories(EVIDENCE_DIR);
		driver = createDriver();
		driver.manage().window().setSize(new Dimension(1920, 2000));
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		final String loginUrl = env("SALEADS_LOGIN_URL");
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
			waitForUiLoad();
		}

		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		printFinalReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones", "terminos-y-condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad", "politica-de-privacidad"));

		assertTrue("One or more workflow steps failed. See SALEADS FINAL REPORT in test logs.", allStepsPassed());
	}

	private String stepLoginWithGoogle() throws IOException {
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google", "Google");
		switchToNewWindowIfPresent(beforeHandles, Duration.ofSeconds(8));
		selectGoogleAccountIfShown("juanlucasbarbiergarzon@gmail.com");
		returnToAppAfterGoogleFlow();

		waitForSidebar();
		assertTextVisible("Negocio");
		captureScreenshot("01-dashboard-loaded");
		return "Dashboard loaded and sidebar visible.";
	}

	private String stepOpenMiNegocioMenu() throws IOException {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded");
		return "Mi Negocio submenu expanded.";
	}

	private String stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");

		assertTextVisible("Crear Nuevo Negocio");
		assertElementVisible(By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]|//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]|//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]")));
		input.click();
		input.sendKeys("Negocio Prueba Automatización");
		waitForUiLoad();

		clickByVisibleText("Cancelar");
		return "Agregar Negocio modal validated.";
	}

	private String stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioIfCollapsed();
		clickByVisibleText("Administrar Negocios");

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		captureFullPageScreenshot("04-administrar-negocios-view");
		return "Administrar Negocios loaded.";
	}

	private String stepValidateInformacionGeneral() {
		final WebElement infoSection = sectionContainingHeading("Información General");
		final String text = infoSection.getText();

		assertTrue("Expected user email in Información General.", EMAIL_PATTERN.matcher(text).find());
		assertTrue("Expected BUSINESS PLAN text.", text.contains("BUSINESS PLAN"));
		assertTrue("Expected Cambiar Plan button/text.", text.contains("Cambiar Plan"));
		assertTrue("Expected user name-like text in Información General.", hasNameLikeText(text));
		return "Información General validated.";
	}

	private String stepValidateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
		return "Detalles de la Cuenta validated.";
	}

	private String stepValidateTusNegocios() {
		final WebElement businessSection = sectionContainingHeading("Tus Negocios");
		final String text = businessSection.getText();

		assertTrue("Expected business list/details to be visible.", text.replace("Tus Negocios", "").trim().length() > 10);
		assertTrue("Expected Agregar Negocio in Tus Negocios section.", text.contains("Agregar Negocio"));
		assertTrue("Expected quota text in Tus Negocios section.", text.contains("Tienes 2 de 3 negocios"));
		return "Tus Negocios validated.";
	}

	private String stepValidateLegalLink(final String linkText, final String screenshotName) throws IOException {
		final String originatingHandle = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String startingUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		final String newHandle = waitForNewWindow(beforeHandles, Duration.ofSeconds(12));
		boolean switchedToNewTab = false;

		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			switchedToNewTab = true;
		}

		waitForUiLoad();
		assertTextVisible(linkText);

		final String pageText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected legal content text for " + linkText, pageText.trim().length() > 120);

		captureScreenshot("05-" + screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(originatingHandle);
			waitForUiLoad();
		} else if (!startingUrl.equals(finalUrl)) {
			driver.navigate().back();
			waitForUiLoad();
		}

		driver.switchTo().window(appWindowHandle);
		return "Validated URL: " + finalUrl;
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			final String message = action.run();
			report.put(stepName, StepResult.passed(message));
		} catch (final Throwable throwable) {
			final String errorMessage = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getMessage();
			report.put(stepName, StepResult.failed(errorMessage));

			try {
				captureScreenshot("FAILED-" + toSlug(stepName));
			} catch (final IOException ignored) {
				// no-op: best effort evidence capture
			}
		}
	}

	private WebDriver createDriver() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,2000");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (Boolean.parseBoolean(env("HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = env("SELENIUM_REMOTE_URL");
		if (!remoteUrl.isEmpty()) {
			try {
				return new RemoteWebDriver(new URL(remoteUrl), options);
			} catch (final MalformedURLException e) {
				fail("Invalid SELENIUM_REMOTE_URL: " + remoteUrl);
			}
		}

		return new ChromeDriver(options);
	}

	private void waitForSidebar() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside | //nav")));
	}

	private void expandMiNegocioIfCollapsed() {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(2))) {
			clickByVisibleText("Mi Negocio");
		}
	}

	private void selectGoogleAccountIfShown(final String accountEmail) {
		try {
			if (isTextVisible(accountEmail, Duration.ofSeconds(10))) {
				clickByVisibleText(accountEmail);
			}
		} catch (final Throwable ignored) {
			// No account picker means user was already authenticated.
		}
		waitForUiLoad();
	}

	private void returnToAppAfterGoogleFlow() {
		final long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
		while (System.nanoTime() < deadline) {
			if (driver.getWindowHandles().contains(appWindowHandle)) {
				driver.switchTo().window(appWindowHandle);
			}

			if (isTextVisible("Negocio", Duration.ofSeconds(2))) {
				return;
			}

			final Set<String> handles = driver.getWindowHandles();
			if (handles.size() > 1) {
				for (final String handle : handles) {
					driver.switchTo().window(handle);
					final String url = safeCurrentUrl();
					if (!url.contains("accounts.google.com")) {
						appWindowHandle = handle;
						waitForUiLoad();
						return;
					}
				}
			}

			try {
				Thread.sleep(300);
			} catch (final InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void clickByVisibleText(final String... candidateTexts) {
		TimeoutException lastTimeout = null;

		for (final String text : candidateTexts) {
			try {
				final WebElement clickable = new WebDriverWait(driver, CLICK_WAIT).until(
						ExpectedConditions.elementToBeClickable(clickableLocatorByText(text)));
				clickable.click();
				waitForUiLoad();
				return;
			} catch (final TimeoutException timeout) {
				lastTimeout = timeout;
				final List<WebElement> candidates = driver.findElements(visibleTextLocator(text));
				for (final WebElement candidate : candidates) {
					if (!candidate.isDisplayed()) {
						continue;
					}
					try {
						candidate.click();
						waitForUiLoad();
						return;
					} catch (final Throwable ignored) {
						if (driver instanceof JavascriptExecutor) {
							((JavascriptExecutor) driver).executeScript("arguments[0].click();", candidate);
							waitForUiLoad();
							return;
						}
					}
				}
			}
		}

		throw new AssertionError("Could not click element using any visible text: " + String.join(", ", candidateTexts),
				lastTimeout);
	}

	private void waitForUiLoad() {
		try {
			wait.until(driver -> {
				if (!(driver instanceof JavascriptExecutor)) {
					return true;
				}
				final Object state = ((JavascriptExecutor) driver).executeScript("return document.readyState");
				return "complete".equals(String.valueOf(state));
			});
		} catch (final TimeoutException ignored) {
			// Some SPA routes never transition document.readyState again.
		}

		try {
			Thread.sleep(600);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void assertTextVisible(final String text) {
		assertElementVisible(visibleTextLocator(text));
	}

	private void assertElementVisible(final By by) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(visibleTextLocator(text)));
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private WebElement sectionContainingHeading(final String headingText) {
		final By locator = By.xpath("//*[self::section or self::div][.//*[contains(normalize-space(.), "
				+ xpathString(headingText) + ")]][1]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private By visibleTextLocator(final String text) {
		return By.xpath("//*[contains(normalize-space(.), " + xpathString(text) + ")]");
	}

	private By clickableLocatorByText(final String text) {
		return By.xpath("(//button[contains(normalize-space(.), " + xpathString(text) + ")]"
				+ "|//a[contains(normalize-space(.), " + xpathString(text) + ")]"
				+ "|//*[@role='button' and contains(normalize-space(.), " + xpathString(text) + ")]"
				+ "|//input[((@type='button' or @type='submit') and contains(@value, " + xpathString(text) + "))]"
				+ "|//*[contains(@class, 'btn') and contains(normalize-space(.), " + xpathString(text) + ")])[1]");
	}

	private String waitForNewWindow(final Set<String> existingHandles, final Duration timeout) {
		final long deadline = System.nanoTime() + timeout.toNanos();

		while (System.nanoTime() < deadline) {
			final Set<String> currentHandles = driver.getWindowHandles();
			for (final String handle : currentHandles) {
				if (!existingHandles.contains(handle)) {
					return handle;
				}
			}

			try {
				Thread.sleep(200);
			} catch (final InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		return null;
	}

	private void switchToNewWindowIfPresent(final Set<String> existingHandles, final Duration timeout) {
		final String newHandle = waitForNewWindow(existingHandles, timeout);
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		}
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshotFile.toPath(), EVIDENCE_DIR.resolve(toSlug(checkpointName) + ".png"));
	}

	private void captureFullPageScreenshot(final String checkpointName) throws IOException {
		if (!(driver instanceof TakesScreenshot) || !(driver instanceof JavascriptExecutor)) {
			captureScreenshot(checkpointName);
			return;
		}

		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final Object value = ((JavascriptExecutor) driver).executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, 1200);");
			final int fullHeight = Math.min(Integer.parseInt(String.valueOf(value)), 7000);
			driver.manage().window().setSize(new Dimension(Math.max(originalSize.width, 1440), fullHeight));
			waitForUiLoad();
			captureScreenshot(checkpointName);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiLoad();
		}
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (final Throwable ignored) {
			return "";
		}
	}

	private boolean hasNameLikeText(final String sectionText) {
		final String compact = sectionText.replace('\n', ' ').replaceAll("\\s+", " ").trim();
		final List<String> obviousLabels = List.of("Información General", "BUSINESS PLAN", "Cambiar Plan");

		String filtered = compact;
		for (final String label : obviousLabels) {
			filtered = filtered.replace(label, "");
		}

		final Pattern namePattern = Pattern.compile("\\b[\\p{L}]{2,}\\s+[\\p{L}]{2,}\\b");
		return namePattern.matcher(filtered).find();
	}

	private String xpathString(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String chunk = String.valueOf(chars[i]);
			if ("'".equals(chunk)) {
				builder.append("\"'\"");
			} else if ("\"".equals(chunk)) {
				builder.append("'\"'");
			} else {
				builder.append("'").append(chunk).append("'");
			}
			if (i < chars.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String toSlug(final String input) {
		return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String env(final String key) {
		return env(key, "");
	}

	private String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value.trim();
	}

	private boolean allStepsPassed() {
		for (final StepResult result : report.values()) {
			if (!result.passed) {
				return false;
			}
		}
		return report.size() == 9;
	}

	private void printFinalReport() {
		final List<String> reportOrder = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Términos y Condiciones", "Política de Privacidad");

		System.out.println();
		System.out.println("===== SALEADS FINAL REPORT =====");

		for (final String field : reportOrder) {
			final StepResult result = report.getOrDefault(field, StepResult.failed("Not executed"));
			final String status = result.passed ? "PASS" : "FAIL";
			System.out.println(field + ": " + status + " - " + result.message);
		}

		System.out.println("Evidence directory: " + EVIDENCE_DIR.toAbsolutePath());
		System.out.println("================================");
		System.out.println();
	}

	@FunctionalInterface
	private interface StepAction {
		String run() throws Exception;
	}

	private static class StepResult {
		private final boolean passed;
		private final String message;

		private StepResult(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message;
		}

		private static StepResult passed(final String message) {
			return new StepResult(true, message);
		}

		private static StepResult failed(final String message) {
			return new StepResult(false, message);
		}
	}
}
