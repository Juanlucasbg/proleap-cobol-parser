package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow validation for SaleADS "Mi Negocio" module.
 *
 * <p>This test is environment-agnostic: it accepts any SaleADS URL through
 * {@code -Dsaleads.url=} or {@code SALEADS_URL}. If no URL is provided, it assumes
 * the active browser session is already on the SaleADS login page.
 */
public class SaleAdsMiNegocioFullTest {

	private static final String GOOGLE_EMAIL_DEFAULT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

	private WebDriver driver;
	private WebDriverWait wait;
	private Duration timeout;
	private Path runDirectory;
	private String appWindowHandle;

	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final Map<String, String> failureDetails = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws Exception {
		timeout = Duration.ofSeconds(Long.parseLong(readConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", "40")));
		runDirectory = buildRunDirectory();
		Files.createDirectories(runDirectory);

		driver = createWebDriver();
		wait = new WebDriverWait(driver, timeout);
		driver.manage().window().setSize(new Dimension(1920, 1080));

		final String baseUrl = readConfig("saleads.url", "SALEADS_URL", "").trim();
		if (!baseUrl.isEmpty()) {
			driver.get(baseUrl);
		}

		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() throws Exception {
		stepResults.put("Login", runStep("Login", this::stepLoginWithGoogle));
		stepResults.put("Mi Negocio menu", runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu));
		stepResults.put("Agregar Negocio modal", runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal));
		stepResults.put("Administrar Negocios view", runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios));
		stepResults.put("Información General", runStep("Información General", this::stepValidateInformacionGeneral));
		stepResults.put("Detalles de la Cuenta", runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta));
		stepResults.put("Tus Negocios", runStep("Tus Negocios", this::stepValidateTusNegocios));
		stepResults.put("Términos y Condiciones", runStep("Términos y Condiciones", () -> stepValidateLegalPage("Términos y Condiciones")));
		stepResults.put("Política de Privacidad", runStep("Política de Privacidad", () -> stepValidateLegalPage("Política de Privacidad")));

		writeFinalReportAndAssert();
	}

	private void stepLoginWithGoogle() throws Exception {
		if (isCurrentPageBlank()) {
			throw new AssertionError("Browser is on about:blank. Provide -Dsaleads.url (or SALEADS_URL) or start on SaleADS login page.");
		}

		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		clickByVisibleText(new String[] { "Sign in with Google", "Iniciar con Google", "Continuar con Google", "Google" });
		selectGoogleAccountIfVisible(handlesBeforeClick);

		assertTextVisible("Negocio", "Mi Negocio");
		assertSidebarVisible();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		assertSidebarVisible();
		expandMiNegocioMenu();
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded-menu");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		expandMiNegocioMenu();
		clickByVisibleText(new String[] { "Agregar Negocio" });

		assertTextVisible("Crear Nuevo Negocio");
		WebElement input = waitForFirstVisible(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@name,'negocio') or contains(@id,'negocio') or contains(@name,'business') or contains(@id,'business')]"));
		assertTextVisible("Tienes 2 de 3 negocios");
		assertButtonVisible("Cancelar");
		assertButtonVisible("Crear Negocio");
		captureScreenshot("03-crear-nuevo-negocio-modal");

		// Optional action requested in the workflow.
		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatización");

		clickByVisibleTextWithinModal(new String[] { "Cancelar" });
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.),'Crear Nuevo Negocio')]")));
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		expandMiNegocioMenu();
		clickByVisibleText(new String[] { "Administrar Negocios" });
		waitForUiLoad();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		captureScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading(new String[] { "Información General", "Informacion General" });
		final String sectionText = normalizeWhitespace(section.getText());

		assertTrue("User email is not visible.", EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("User name is not visible.", hasPotentialName(sectionText));
		assertContainsText(sectionText, "BUSINESS PLAN");
		assertButtonVisible("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = findSectionByHeading(new String[] { "Detalles de la Cuenta" });
		final String sectionText = normalizeWhitespace(section.getText());

		assertContainsText(sectionText, "Cuenta creada");
		assertContainsText(sectionText, "Estado");
		assertContainsText(sectionText, "activo");
		assertContainsText(sectionText, "Idioma");
		assertContainsText(sectionText, "seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading(new String[] { "Tus Negocios" });
		final String sectionText = normalizeWhitespace(section.getText());

		assertTrue("Business list is not visible.", hasBusinessListIndicators(section));
		assertButtonVisible("Agregar Negocio");
		assertContainsText(sectionText, "Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalPage(final String linkText) throws Exception {
		final WebElement legalSection = findSectionByHeading(new String[] { "Sección Legal", "Seccion Legal" });
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String originalHandle = driver.getWindowHandle();

		clickByVisibleTextInside(legalSection, new String[] { linkText });

		final String targetHandle = waitForPotentialNewWindow(handlesBeforeClick);
		if (targetHandle != null) {
			driver.switchTo().window(targetHandle);
		}

		waitForUiLoad();
		assertTextVisible(linkText);
		assertLegalContentVisible();
		legalUrls.put(linkText, driver.getCurrentUrl());

		captureScreenshot("05-legal-" + slug(linkText));

		// Return to application context.
		if (targetHandle != null && !targetHandle.equals(originalHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiLoad();
		driver.switchTo().window(appWindowHandle);
	}

	private boolean runStep(final String stepName, final CheckedRunnable action) {
		try {
			action.run();
			return true;
		} catch (Exception ex) {
			final String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			failureDetails.put(stepName, message);
			captureScreenshot("failed-" + slug(stepName));
			return false;
		}
	}

	private WebDriver createWebDriver() throws Exception {
		final String browser = readConfig("saleads.browser", "SALEADS_BROWSER", "chrome").trim().toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"));
		final String remoteUrl = readConfig("saleads.remote.url", "SELENIUM_REMOTE_URL", "").trim();

		if ("firefox".equals(browser)) {
			final FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			return remoteUrl.isEmpty() ? new FirefoxDriver(options) : new RemoteWebDriver(URI.create(remoteUrl).toURL(), options);
		}

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		return remoteUrl.isEmpty() ? new ChromeDriver(options) : new RemoteWebDriver(URI.create(remoteUrl).toURL(), options);
	}

	private void selectGoogleAccountIfVisible(final Set<String> handlesBeforeClick) {
		final String googleEmail = readConfig("saleads.google.account.email", "SALEADS_GOOGLE_ACCOUNT_EMAIL", GOOGLE_EMAIL_DEFAULT);
		final String newWindowHandle = waitForPotentialNewWindow(handlesBeforeClick);
		final String currentHandle = driver.getWindowHandle();

		if (newWindowHandle != null) {
			driver.switchTo().window(newWindowHandle);
		}

		try {
			final WebElement accountOption = waitForFirstVisibleShort(
					By.xpath("//*[contains(normalize-space(.),'" + googleEmail + "')]"),
					By.xpath("//div[@data-email='" + googleEmail + "']"),
					By.xpath("//li[contains(.,'" + googleEmail + "')]"));
			clickElementAndWait(accountOption);
		} catch (TimeoutException timeoutException) {
			// Account chooser may not appear if the session is already authenticated.
		}

		final String targetHandle = appWindowHandle != null ? appWindowHandle : currentHandle;
		if (driver.getWindowHandles().contains(targetHandle)) {
			driver.switchTo().window(targetHandle);
		}
	}

	private void expandMiNegocioMenu() {
		if (isTextVisibleFast("Agregar Negocio") && isTextVisibleFast("Administrar Negocios")) {
			return;
		}

		clickByVisibleText(new String[] { "Mi Negocio", "Negocio" });
		if (!(isTextVisibleFast("Agregar Negocio") && isTextVisibleFast("Administrar Negocios"))) {
			clickByVisibleText(new String[] { "Negocio", "Mi Negocio" });
		}

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
	}

	private void clickByVisibleText(final String[] preferredTexts) {
		final List<By> locators = new ArrayList<>();
		for (final String text : preferredTexts) {
			locators.add(By.xpath("//button[contains(normalize-space(.),'" + text + "')]"));
			locators.add(By.xpath("//a[contains(normalize-space(.),'" + text + "')]"));
			locators.add(By.xpath("//*[@role='button' and contains(normalize-space(.),'" + text + "')]"));
			locators.add(By.xpath("//*[self::span or self::div or self::p][contains(normalize-space(.),'" + text + "')]/ancestor::*[self::button or self::a or @role='button'][1]"));
		}

		final WebElement element = waitForFirstVisible(locators.toArray(new By[0]));
		clickElementAndWait(element);
	}

	private void clickByVisibleTextInside(final WebElement container, final String[] preferredTexts) {
		for (final String text : preferredTexts) {
			final List<WebElement> candidates = container.findElements(By.xpath(".//button[contains(normalize-space(.),'" + text + "')]"
					+ "|.//a[contains(normalize-space(.),'" + text + "')]"
					+ "|.//*[@role='button' and contains(normalize-space(.),'" + text + "')]"));
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					clickElementAndWait(candidate);
					return;
				}
			}
		}

		throw new AssertionError("Unable to find clickable element with texts: " + Arrays.toString(preferredTexts));
	}

	private void clickByVisibleTextWithinModal(final String[] preferredTexts) {
		final WebElement modal = waitForFirstVisible(
				By.xpath("//*[@role='dialog']"),
				By.xpath("//div[contains(@class,'modal')]"),
				By.xpath("//*[contains(normalize-space(.),'Crear Nuevo Negocio')]/ancestor::*[@role='dialog' or contains(@class,'modal')][1]"));
		clickByVisibleTextInside(modal, preferredTexts);
	}

	private void clickElementAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiLoad();
	}

	private void waitForUiLoad() {
		wait.until(driverInstance -> {
			if (!(driverInstance instanceof JavascriptExecutor)) {
				return true;
			}
			final Object state = ((JavascriptExecutor) driverInstance).executeScript("return document.readyState");
			return "complete".equals(state) || "interactive".equals(state);
		});

		try {
			Thread.sleep(450);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private WebElement waitForFirstVisible(final By... locators) {
		return waitForFirstVisibleWithTimeout(timeout, locators);
	}

	private WebElement waitForFirstVisibleShort(final By... locators) {
		return waitForFirstVisibleWithTimeout(Duration.ofSeconds(8), locators);
	}

	private WebElement waitForFirstVisibleWithTimeout(final Duration maxWait, final By... locators) {
		final long deadline = System.currentTimeMillis() + maxWait.toMillis();
		Throwable lastError = null;

		while (System.currentTimeMillis() < deadline) {
			for (final By locator : locators) {
				try {
					final List<WebElement> elements = driver.findElements(locator);
					for (final WebElement element : elements) {
						if (element.isDisplayed()) {
							return element;
						}
					}
				} catch (Exception ex) {
					lastError = ex;
				}
			}

			try {
				Thread.sleep(200);
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		throw new TimeoutException("No visible element found for locators: " + Arrays.toString(locators), lastError);
	}

	private void assertSidebarVisible() {
		waitForFirstVisible(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar')]"),
				By.xpath("//*[contains(normalize-space(.),'Mi Negocio') or contains(normalize-space(.),'Negocio')]"));
	}

	private void assertTextVisible(final String... options) {
		final List<By> locators = new ArrayList<>();
		for (final String text : options) {
			locators.add(By.xpath("//*[contains(normalize-space(.),'" + text + "')]"));
		}
		waitForFirstVisible(locators.toArray(new By[0]));
	}

	private void assertButtonVisible(final String text) {
		waitForFirstVisible(
				By.xpath("//button[contains(normalize-space(.),'" + text + "')]"),
				By.xpath("//a[contains(normalize-space(.),'" + text + "')]"),
				By.xpath("//*[@role='button' and contains(normalize-space(.),'" + text + "')]"));
	}

	private boolean isTextVisibleFast(final String text) {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[contains(normalize-space(.),'" + text + "')]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private WebElement findSectionByHeading(final String[] headingOptions) {
		for (final String heading : headingOptions) {
			final List<WebElement> headings = driver.findElements(By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::h6 or self::p or self::span]"
					+ "[contains(normalize-space(.),'" + heading + "')]"));
			for (final WebElement headingElement : headings) {
				if (!headingElement.isDisplayed()) {
					continue;
				}
				final List<WebElement> sections = headingElement.findElements(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
				if (!sections.isEmpty() && sections.get(0).isDisplayed()) {
					return sections.get(0);
				}
			}
		}

		throw new AssertionError("Unable to locate section by heading options: " + Arrays.toString(headingOptions));
	}

	private void assertContainsText(final String source, final String expectedFragment) {
		assertTrue("Expected text '" + expectedFragment + "' was not found.", source.toLowerCase(Locale.ROOT).contains(expectedFragment.toLowerCase(Locale.ROOT)));
	}

	private boolean hasPotentialName(final String sectionText) {
		final String[] lines = sectionText.split("\\R+");
		for (final String rawLine : lines) {
			final String line = normalizeWhitespace(rawLine);
			if (line.length() < 3 || line.length() > 80) {
				continue;
			}
			final String lowered = line.toLowerCase(Locale.ROOT);
			if (line.contains("@")) {
				continue;
			}
			if (lowered.contains("información general") || lowered.contains("informacion general")) {
				continue;
			}
			if (lowered.contains("business plan") || lowered.contains("cambiar plan")) {
				continue;
			}
			if (lowered.contains("cuenta creada") || lowered.contains("estado activo") || lowered.contains("idioma seleccionado")) {
				continue;
			}
			if (line.matches(".*\\d.*")) {
				continue;
			}
			return true;
		}
		return false;
	}

	private boolean hasBusinessListIndicators(final WebElement section) {
		final List<WebElement> items = section.findElements(By.xpath(".//li | .//tr | .//*[@role='listitem'] | .//article | .//*[contains(@class,'business')]"));
		for (final WebElement item : items) {
			if (item.isDisplayed()) {
				return true;
			}
		}
		final String sectionText = normalizeWhitespace(section.getText());
		return sectionText.split("\\R+").length >= 3;
	}

	private void assertLegalContentVisible() {
		final String text = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		assertTrue("Legal content appears to be missing.", text.length() >= 120);
	}

	private String waitForPotentialNewWindow(final Set<String> handlesBeforeClick) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			final Set<String> handlesAfterClick = driver.getWindowHandles();
			if (handlesAfterClick.size() > handlesBeforeClick.size()) {
				for (final String handle : handlesAfterClick) {
					if (!handlesBeforeClick.contains(handle)) {
						return handle;
					}
				}
			}
			try {
				Thread.sleep(150);
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	private Path buildRunDirectory() {
		final String configuredOutputDir = readConfig("saleads.output.dir", "SALEADS_OUTPUT_DIR", "target/saleads-mi-negocio-full-test");
		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		return Paths.get(configuredOutputDir, runId);
	}

	private void captureScreenshot(final String name) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		try {
			final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path target = runDirectory.resolve("screenshot-" + slug(name) + ".png");
			Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception ignored) {
			// Never fail the test because screenshot capture failed.
		}
	}

	private void writeFinalReportAndAssert() throws IOException {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("saleads_mi_negocio_full_test").append('\n');
		reportBuilder.append("Output directory: ").append(runDirectory.toAbsolutePath()).append('\n');
		reportBuilder.append('\n');
		reportBuilder.append("Validation results:").append('\n');

		for (final Map.Entry<String, Boolean> entry : stepResults.entrySet()) {
			reportBuilder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append('\n');
			if (!entry.getValue() && failureDetails.containsKey(entry.getKey())) {
				reportBuilder.append("  Reason: ").append(failureDetails.get(entry.getKey())).append('\n');
			}
		}

		if (!legalUrls.isEmpty()) {
			reportBuilder.append('\n');
			reportBuilder.append("Captured legal URLs:").append('\n');
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				reportBuilder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		final Path reportPath = runDirectory.resolve("final-report.txt");
		Files.writeString(reportPath, reportBuilder.toString(), StandardCharsets.UTF_8);
		System.out.println(reportBuilder);

		assertTrue("One or more workflow validations failed. See report: " + reportPath.toAbsolutePath(), allStepsPassed());
	}

	private boolean allStepsPassed() {
		for (final Boolean passed : stepResults.values()) {
			if (!Boolean.TRUE.equals(passed)) {
				return false;
			}
		}
		return true;
	}

	private boolean isCurrentPageBlank() {
		final String currentUrl = driver.getCurrentUrl();
		return currentUrl == null || currentUrl.trim().isEmpty() || "about:blank".equalsIgnoreCase(currentUrl.trim());
	}

	private String readConfig(final String propertyKey, final String envKey, final String defaultValue) {
		final String fromProperty = System.getProperty(propertyKey);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}
		final String fromEnv = System.getenv(envKey);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}
		return defaultValue;
	}

	private String normalizeWhitespace(final String value) {
		return value == null ? "" : value.replaceAll("\\s+", " ").trim();
	}

	private String slug(final String value) {
		return normalizeWhitespace(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
