package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration LONG_TIMEOUT = Duration.ofSeconds(60);
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, String> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private Path artifactsDir;
	private String appWindowHandle;
	private String preLegalUrl;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(
				getConfig("SALEADS_E2E_ENABLED", "saleads.e2e.enabled", "false").trim());
		Assume.assumeTrue(
				"Set SALEADS_E2E_ENABLED=true (or -Dsaleads.e2e.enabled=true) to run the SaleADS E2E workflow test.",
				enabled);

		artifactsDir = Paths
				.get(getConfig("SALEADS_ARTIFACTS_DIR", "saleads.artifactsDir", "target/saleads-mi-negocio-artifacts"));
		Files.createDirectories(artifactsDir);

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean
				.parseBoolean(getConfig("SALEADS_HEADLESS", "saleads.headless", "true").trim());
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);

		final String loginUrl = getRequiredConfig("SALEADS_LOGIN_URL", "saleads.loginUrl");
		driver.get(loginUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		try {
			runStep("Login", () -> {
				clickByVisibleText("Sign in with Google", "Iniciar con Google", "Continuar con Google", "Google");
				selectGoogleAccountIfPresented();
				waitForAnyVisibleText(LONG_TIMEOUT, "Mi Negocio", "Negocio", "Dashboard", "Panel", "Inicio");
				assertAnyVisible("Expected main application interface after Google login.",
						By.xpath("//aside"),
						By.xpath("//*[contains(@class,'sidebar')]"),
						By.xpath("//*[contains(@class,'navigation')]"),
						By.xpath("//*[contains(normalize-space(.)," + toXPathLiteral("Negocio") + ")]"));
				saveScreenshot("01-dashboard-loaded", false);
			});

			runStep("Mi Negocio menu", () -> {
				expandMiNegocioMenuIfNeeded();
				assertVisibleText("Agregar Negocio", DEFAULT_TIMEOUT);
				assertVisibleText("Administrar Negocios", DEFAULT_TIMEOUT);
				saveScreenshot("02-mi-negocio-menu-expanded", false);
			});

			runStep("Agregar Negocio modal", () -> {
				clickByVisibleText("Agregar Negocio");
				assertVisibleText("Crear Nuevo Negocio", DEFAULT_TIMEOUT);
				assertVisibleText("Nombre del Negocio", DEFAULT_TIMEOUT);
				assertVisibleText("Tienes 2 de 3 negocios", DEFAULT_TIMEOUT);
				assertVisibleText("Cancelar", DEFAULT_TIMEOUT);
				assertVisibleText("Crear Negocio", DEFAULT_TIMEOUT);

				typeIntoFieldNearLabel("Nombre del Negocio", "Negocio Prueba Automatización");
				clickByVisibleText("Cancelar");
				waitForUiToLoad();
				saveScreenshot("03-agregar-negocio-modal", false);
			});

			runStep("Administrar Negocios view", () -> {
				expandMiNegocioMenuIfNeeded();
				clickByVisibleText("Administrar Negocios");
				waitForUiToLoad();
				assertVisibleText("Información General", LONG_TIMEOUT);
				assertVisibleText("Detalles de la Cuenta", LONG_TIMEOUT);
				assertVisibleText("Tus Negocios", LONG_TIMEOUT);
				assertVisibleText("Sección Legal", LONG_TIMEOUT);
				saveScreenshot("04-administrar-negocios", true);
				preLegalUrl = driver.getCurrentUrl();
			});

			runStep("Información General", () -> {
				final WebElement section = sectionWithHeading("Información General");
				final String text = section.getText();
				assertContainsEmail(text, "User email is not visible in 'Información General'.");
				assertTrue("User name is not visible in 'Información General'.",
						hasLikelyUserName(text));
				assertVisibleText("BUSINESS PLAN", DEFAULT_TIMEOUT);
				assertVisibleText("Cambiar Plan", DEFAULT_TIMEOUT);
			});

			runStep("Detalles de la Cuenta", () -> {
				final WebElement section = sectionWithHeading("Detalles de la Cuenta");
				assertContainsText(section.getText(), "Cuenta creada", "Missing 'Cuenta creada' in account details.");
				assertContainsText(section.getText(), "Estado activo", "Missing 'Estado activo' in account details.");
				assertContainsText(section.getText(), "Idioma seleccionado",
						"Missing 'Idioma seleccionado' in account details.");
			});

			runStep("Tus Negocios", () -> {
				final WebElement section = sectionWithHeading("Tus Negocios");
				assertContainsText(section.getText(), "Agregar Negocio", "Missing 'Agregar Negocio' in business section.");
				assertContainsText(section.getText(), "Tienes 2 de 3 negocios",
						"Missing business quota text in business section.");
			});

			runStep("Términos y Condiciones", () -> validateLegalLink("Términos y Condiciones",
					"Términos y Condiciones", "05-terminos-y-condiciones"));

			runStep("Política de Privacidad", () -> validateLegalLink("Política de Privacidad", "Política de Privacidad",
					"06-politica-de-privacidad"));
		} finally {
			writeFinalReport();
		}

		if (!failures.isEmpty()) {
			fail("SaleADS Mi Negocio full workflow failed:\n - " + String.join("\n - ", failures));
		}
	}

	private void runStep(final String reportKey, final CheckedRunnable runnable) {
		try {
			runnable.run();
			report.put(reportKey, "PASS");
		} catch (final Exception ex) {
			report.put(reportKey, "FAIL");
			failures.add(reportKey + ": " + ex.getMessage());
		}
	}

	private void expandMiNegocioMenuIfNeeded() {
		if (!isVisibleText("Mi Negocio", Duration.ofSeconds(4)) && isVisibleText("Negocio", Duration.ofSeconds(4))) {
			clickByVisibleText("Negocio");
		}
		clickByVisibleText("Mi Negocio");
	}

	private void selectGoogleAccountIfPresented() {
		final Set<String> handles = new LinkedHashSet<>(driver.getWindowHandles());

		for (final String handle : handles) {
			driver.switchTo().window(handle);
			if (isVisibleText(ACCOUNT_EMAIL, Duration.ofSeconds(5))) {
				clickByVisibleText(ACCOUNT_EMAIL);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void validateLegalLink(final String linkText, final String expectedHeading, final String screenshotBaseName)
			throws IOException {
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String urlBeforeClick = driver.getCurrentUrl();
		clickByVisibleText(linkText);

		final String legalHandle = waitForNewWindowHandle(handlesBeforeClick, Duration.ofSeconds(12));
		if (legalHandle != null) {
			driver.switchTo().window(legalHandle);
		}

		waitForUiToLoad();
		assertVisibleText(expectedHeading, LONG_TIMEOUT);

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected legal content text for " + expectedHeading + ".", bodyText != null && bodyText.trim().length() > 120);

		saveScreenshot(screenshotBaseName, false);
		legalUrls.put(expectedHeading, driver.getCurrentUrl());

		if (legalHandle != null && !legalHandle.equals(appWindowHandle)) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else if (!urlBeforeClick.equals(driver.getCurrentUrl())) {
			driver.navigate().back();
		}

		if (preLegalUrl != null && !preLegalUrl.equals(driver.getCurrentUrl())) {
			driver.get(preLegalUrl);
		}
		waitForUiToLoad();
		assertAnyVisible("Could not return to the application tab after validating legal page.",
				By.xpath("//*[contains(normalize-space(.)," + toXPathLiteral("Sección Legal") + ")]"),
				By.xpath("//*[contains(normalize-space(.)," + toXPathLiteral("Administrar Negocios") + ")]"),
				By.xpath("//*[contains(normalize-space(.)," + toXPathLiteral("Mi Negocio") + ")]"));
	}

	private void clickByVisibleText(final String... labels) {
		Exception lastError = null;
		for (final String label : labels) {
			final String literal = toXPathLiteral(label);
			final List<By> locators = Arrays.asList(
					By.xpath("//button[contains(normalize-space(.)," + literal + ")]"),
					By.xpath("//a[contains(normalize-space(.)," + literal + ")]"),
					By.xpath("//*[@role='button' and contains(normalize-space(.)," + literal + ")]"),
					By.xpath("//li[contains(normalize-space(.)," + literal + ")]"),
					By.xpath("//span[contains(normalize-space(.)," + literal + ")]/ancestor::*[self::button or self::a or @role='button' or self::li][1]"));

			for (final By locator : locators) {
				final long end = System.currentTimeMillis() + DEFAULT_TIMEOUT.toMillis();
				while (System.currentTimeMillis() < end) {
					try {
						final List<WebElement> elements = driver.findElements(locator);
						for (final WebElement element : elements) {
							if (!element.isDisplayed() || !element.isEnabled()) {
								continue;
							}

							scrollIntoView(element);
							try {
								element.click();
							} catch (final Exception ignored) {
								((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
							}
							waitForUiToLoad();
							return;
						}
					} catch (final Exception ex) {
						lastError = ex;
					}
					sleep(200);
				}
			}
		}

		throw new IllegalStateException(
				"Could not click any visible element by text: " + Arrays.toString(labels)
						+ (lastError == null ? "" : " (" + lastError.getMessage() + ")"));
	}

	private void typeIntoFieldNearLabel(final String labelText, final String value) {
		final String labelLiteral = toXPathLiteral(labelText);
		final List<By> locators = Arrays.asList(
				By.xpath("//label[contains(normalize-space(.)," + labelLiteral + ")]/following::input[1]"),
				By.xpath("//input[contains(@placeholder," + labelLiteral + ")]"),
				By.xpath("//*[contains(normalize-space(.)," + labelLiteral + ")]/following::input[1]"));

		for (final By locator : locators) {
			final List<WebElement> inputs = driver.findElements(locator);
			for (final WebElement input : inputs) {
				if (!input.isDisplayed() || !input.isEnabled()) {
					continue;
				}
				scrollIntoView(input);
				input.click();
				input.clear();
				input.sendKeys(value);
				waitForUiToLoad();
				return;
			}
		}

		throw new IllegalStateException("Could not find input field for label: " + labelText);
	}

	private void assertVisibleText(final String text, final Duration timeout) {
		waitForVisibleText(text, timeout);
	}

	private void waitForVisibleText(final String text, final Duration timeout) {
		final String literal = toXPathLiteral(text);
		final By locator = By.xpath(
				"//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::h6 or self::button or self::a or self::label or self::span or self::p or self::div][contains(normalize-space(.),"
						+ literal + ")]");
		new WebDriverWait(driver, timeout).until(d -> isAnyDisplayed(locator));
	}

	private void waitForAnyVisibleText(final Duration timeout, final String... texts) {
		new WebDriverWait(driver, timeout).until(d -> {
			for (final String text : texts) {
				if (isVisibleText(text, Duration.ofSeconds(1))) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isVisibleText(final String text, final Duration timeout) {
		final String literal = toXPathLiteral(text);
		final By locator = By.xpath("//*[contains(normalize-space(.)," + literal + ")]");
		final long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			if (isAnyDisplayed(locator)) {
				return true;
			}
			sleep(150);
		}
		return false;
	}

	private boolean isAnyDisplayed(final By locator) {
		try {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		} catch (final Exception ignored) {
			// Do nothing, just keep waiting.
		}
		return false;
	}

	private void waitForUiToLoad() {
		new WebDriverWait(driver, LONG_TIMEOUT).until(d -> "complete"
				.equals(((JavascriptExecutor) d).executeScript("return document.readyState")));

		final By loadingLocator = By
				.xpath("//*[contains(@class,'loading') or contains(@class,'spinner') or @aria-busy='true']");
		final long end = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
		while (System.currentTimeMillis() < end) {
			if (!isAnyDisplayed(loadingLocator)) {
				break;
			}
			sleep(150);
		}
	}

	private WebElement sectionWithHeading(final String heading) {
		assertVisibleText(heading, LONG_TIMEOUT);
		final String literal = toXPathLiteral(heading);
		final By locator = By.xpath(
				"//*[self::section or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::h6 or self::span or self::p][contains(normalize-space(.),"
						+ literal + ")]]");
		final List<WebElement> sections = driver.findElements(locator);
		for (final WebElement section : sections) {
			if (section.isDisplayed()) {
				return section;
			}
		}
		throw new IllegalStateException("Could not find visible section containing heading: " + heading);
	}

	private void assertContainsEmail(final String text, final String message) {
		final Pattern pattern = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
		assertTrue(message, text != null && pattern.matcher(text).find());
	}

	private boolean hasLikelyUserName(final String sectionText) {
		if (sectionText == null) {
			return false;
		}

		final String[] lines = sectionText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if ("Información General".equalsIgnoreCase(line) || "BUSINESS PLAN".equalsIgnoreCase(line)
					|| "Cambiar Plan".equalsIgnoreCase(line)) {
				continue;
			}
			if (line.contains("@")) {
				continue;
			}
			if (line.length() >= 3) {
				return true;
			}
		}
		return false;
	}

	private void assertContainsText(final String haystack, final String needle, final String errorMessage) {
		assertTrue(errorMessage, haystack != null && haystack.contains(needle));
	}

	private void assertAnyVisible(final String errorMessage, final By... locators) {
		for (final By locator : locators) {
			if (isAnyDisplayed(locator)) {
				return;
			}
		}
		throw new IllegalStateException(errorMessage);
	}

	private String waitForNewWindowHandle(final Set<String> handlesBefore, final Duration timeout) {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			final Set<String> currentHandles = driver.getWindowHandles();
			for (final String handle : currentHandles) {
				if (!handlesBefore.contains(handle)) {
					return handle;
				}
			}
			sleep(150);
		}
		return null;
	}

	private void saveScreenshot(final String screenshotName, final boolean fullPage) throws IOException {
		final String fileName = screenshotName + ".png";
		final Path targetPath = artifactsDir.resolve(fileName);

		if (fullPage && driver instanceof ChromiumDriver) {
			final Map<String, Object> params = new LinkedHashMap<>();
			params.put("captureBeyondViewport", true);
			params.put("fromSurface", true);
			final Object data = ((ChromiumDriver) driver).executeCdpCommand("Page.captureScreenshot", params).get("data");
			if (data instanceof String) {
				Files.write(targetPath, java.util.Base64.getDecoder().decode((String) data));
				return;
			}
		}

		final Path tempPath = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder markdown = new StringBuilder();
		markdown.append("# SaleADS Mi Negocio Full Workflow Report\n\n");
		markdown.append("| Checkpoint | Result |\n");
		markdown.append("| --- | --- |\n");
		for (final String field : REPORT_FIELDS) {
			final String status = report.getOrDefault(field, "NOT_RUN");
			markdown.append("| ").append(field).append(" | ").append(status).append(" |\n");
		}

		markdown.append("\n## Evidence\n");
		markdown.append("- Dashboard: `01-dashboard-loaded.png`\n");
		markdown.append("- Mi Negocio menu: `02-mi-negocio-menu-expanded.png`\n");
		markdown.append("- Agregar Negocio modal: `03-agregar-negocio-modal.png`\n");
		markdown.append("- Administrar Negocios page: `04-administrar-negocios.png`\n");
		markdown.append("- Términos y Condiciones page: `05-terminos-y-condiciones.png`\n");
		markdown.append("- Política de Privacidad page: `06-politica-de-privacidad.png`\n");

		if (!legalUrls.isEmpty()) {
			markdown.append("\n## Final URLs\n");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				markdown.append("- ").append(entry.getKey()).append(": `").append(entry.getValue()).append("`\n");
			}
		}

		if (!failures.isEmpty()) {
			markdown.append("\n## Failures\n");
			for (final String failure : failures) {
				markdown.append("- ").append(failure).append("\n");
			}
		}

		final Path reportPath = artifactsDir.resolve("final-report.md");
		Files.writeString(reportPath, markdown.toString(), StandardCharsets.UTF_8);
		System.out.println("SaleADS final report: " + reportPath.toAbsolutePath());
	}

	private String getConfig(final String envName, final String propertyName, final String defaultValue) {
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue;
		}

		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue;
		}

		return defaultValue;
	}

	private String getRequiredConfig(final String envName, final String propertyName) {
		final String value = getConfig(envName, propertyName, "").trim();
		if (value.isEmpty()) {
			throw new IllegalStateException("Missing required configuration. Set " + envName + " or -D" + propertyName);
		}
		return value;
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'", -1);
		final StringBuilder result = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			result.append("'").append(parts[i]).append("'");
			if (i != parts.length - 1) {
				result.append(",\"'\",");
			}
		}
		result.append(")");
		return result.toString();
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
