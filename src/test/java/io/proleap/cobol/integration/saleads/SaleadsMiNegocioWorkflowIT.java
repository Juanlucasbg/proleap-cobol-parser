package io.proleap.cobol.integration.saleads;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow for SaleADS "Mi Negocio".
 *
 * <p>
 * Runtime controls:
 * <ul>
 * <li>SALEADS_E2E_ENABLED=true</li>
 * <li>SALEADS_LOGIN_URL=https://... (current environment login page)</li>
 * <li>SALEADS_HEADLESS=true|false (optional, default true)</li>
 * <li>SALEADS_EXPECTED_USER_NAME=... (optional, improves name validation)</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowIT {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(25);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b");

	private static final List<String> FINAL_REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu",
			"Agregar Negocio modal", "Administrar Negocios view", "Información General", "Detalles de la Cuenta",
			"Tus Negocios", "Términos y Condiciones", "Política de Privacidad");

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final List<String> evidenceNotes = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(readEnv("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Enable with SALEADS_E2E_ENABLED=true", enabled);

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the login page of the target environment",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(readEnv("SALEADS_HEADLESS", "true"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
		evidenceDir = Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		driver.get(loginUrl);
		waitForUiLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		finalReport.put("Login", runStep("Step 1 - Login with Google", this::stepLoginWithGoogle));
		finalReport.put("Mi Negocio menu", runStep("Step 2 - Open Mi Negocio menu", this::stepOpenMiNegocioMenu));
		finalReport.put("Agregar Negocio modal",
				runStep("Step 3 - Validate Agregar Negocio modal", this::stepValidateAgregarNegocioModal));
		finalReport.put("Administrar Negocios view",
				runStep("Step 4 - Open Administrar Negocios", this::stepOpenAdministrarNegocios));
		finalReport.put("Información General",
				runStep("Step 5 - Validate Información General", this::stepValidateInformacionGeneral));
		finalReport.put("Detalles de la Cuenta",
				runStep("Step 6 - Validate Detalles de la Cuenta", this::stepValidateDetallesCuenta));
		finalReport.put("Tus Negocios", runStep("Step 7 - Validate Tus Negocios", this::stepValidateTusNegocios));
		finalReport.put("Términos y Condiciones",
				runStep("Step 8 - Validate Términos y Condiciones", () -> termsUrl = openAndValidateLegal(
						"Términos y Condiciones", "Términos y Condiciones", "08-terminos-condiciones")));
		finalReport.put("Política de Privacidad",
				runStep("Step 9 - Validate Política de Privacidad", () -> privacyUrl = openAndValidateLegal(
						"Política de Privacidad", "Política de Privacidad", "09-politica-privacidad")));

		writeFinalReport();
		Assert.assertTrue(buildFinalAssertionMessage(), finalReport.values().stream().allMatch(Boolean::booleanValue));
	}

	private void stepLoginWithGoogle() throws IOException {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Inicia sesión con Google",
				"Continuar con Google", "Login with Google");
		waitForUiLoad();
		chooseGoogleAccountIfSelectorAppears(GOOGLE_ACCOUNT_EMAIL);

		Assert.assertTrue("Main application interface did not appear.", anyElementVisible(
				By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[contains(@class, 'sidebar')]")));
		Assert.assertTrue("Left sidebar navigation is not visible.",
				isTextVisible("Negocio") || isTextVisible("Mi Negocio"));

		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		if (!isTextVisible("Mi Negocio")) {
			clickByVisibleText("Negocio");
			waitForUiLoad();
		}

		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		Assert.assertTrue("'Agregar Negocio' is not visible.", isTextVisible("Agregar Negocio"));
		Assert.assertTrue("'Administrar Negocios' is not visible.", isTextVisible("Administrar Negocios"));

		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForUiLoad();

		Assert.assertTrue("Modal title 'Crear Nuevo Negocio' is missing.", isTextVisible("Crear Nuevo Negocio"));
		Assert.assertTrue("Input field 'Nombre del Negocio' is missing.",
				anyElementVisible(By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
						By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]")));
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' is missing.", isTextVisible("Tienes 2 de 3 negocios"));
		Assert.assertTrue("Button 'Cancelar' is missing.", isTextVisible("Cancelar"));
		Assert.assertTrue("Button 'Crear Negocio' is missing.", isTextVisible("Crear Negocio"));

		typeInBusinessNameIfPresent("Negocio Prueba Automatización");
		captureScreenshot("03-agregar-negocio-modal");

		clickByVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		Assert.assertTrue("Section 'Información General' is missing.", isTextVisible("Información General"));
		Assert.assertTrue("Section 'Detalles de la Cuenta' is missing.", isTextVisible("Detalles de la Cuenta"));
		Assert.assertTrue("Section 'Tus Negocios' is missing.", isTextVisible("Tus Negocios"));
		Assert.assertTrue("Section 'Sección Legal' is missing.", isTextVisible("Sección Legal"));

		captureScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		Assert.assertTrue("Text 'BUSINESS PLAN' is missing.", isTextVisible("BUSINESS PLAN"));
		Assert.assertTrue("Button 'Cambiar Plan' is missing.", isTextVisible("Cambiar Plan"));

		final String infoText = sectionTextOrWholePage("Información General");
		Assert.assertTrue("User email is not visible in Información General.", EMAIL_PATTERN.matcher(infoText).find());
		Assert.assertTrue("User name is not visible in Información General.", hasLikelyUserName(infoText));
	}

	private void stepValidateDetallesCuenta() {
		Assert.assertTrue("'Cuenta creada' is missing.", isTextVisible("Cuenta creada"));
		Assert.assertTrue("'Estado activo' is missing.", isTextVisible("Estado activo"));
		Assert.assertTrue("'Idioma seleccionado' is missing.", isTextVisible("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		Assert.assertTrue("Business list section 'Tus Negocios' is missing.", isTextVisible("Tus Negocios"));
		Assert.assertTrue("Button 'Agregar Negocio' is missing in business section.", isTextVisible("Agregar Negocio"));
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' is missing.", isTextVisible("Tienes 2 de 3 negocios"));
	}

	private String openAndValidateLegal(final String linkText, final String headingText, final String screenshotFile)
			throws IOException {
		final String returnUrl = driver.getCurrentUrl();
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForUiLoad();

		boolean openedNewTab = false;
		String newHandle = null;
		try {
			wait.until((ExpectedCondition<Boolean>) d -> d != null && d.getWindowHandles().size() > handlesBefore.size());
			final Set<String> handlesAfter = driver.getWindowHandles();
			for (final String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					newHandle = handle;
					break;
				}
			}
			if (newHandle != null) {
				driver.switchTo().window(newHandle);
				openedNewTab = true;
				waitForUiLoad();
			}
		} catch (final TimeoutException ignored) {
			// Same-tab navigation is valid.
		}

		Assert.assertTrue("Expected legal heading is not visible: " + headingText, isTextVisible(headingText));
		final String bodyText = visibleBodyText();
		Assert.assertTrue("Legal content text is not visible.", bodyText.replace(headingText, "").trim().length() > 120);

		captureScreenshot(screenshotFile);
		final String legalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiLoad();
		} else {
			driver.navigate().to(returnUrl);
			waitForUiLoad();
		}

		return legalUrl;
	}

	private boolean runStep(final String stepName, final CheckedStep action) {
		try {
			action.execute();
			evidenceNotes.add("PASS - " + stepName);
			return true;
		} catch (final Exception e) {
			evidenceNotes.add("FAIL - " + stepName + " -> " + rootMessage(e));
			captureScreenshotQuietly("failure-" + slug(stepName));
			return false;
		}
	}

	private void clickByVisibleText(final String... textOptions) {
		final WebElement element = findVisibleClickableByText(textOptions);
		if (element == null) {
			throw new NoSuchElementException("Could not find clickable element for text options: "
					+ Arrays.stream(textOptions).collect(Collectors.joining(", ")));
		}
		element.click();
	}

	private WebElement findVisibleClickableByText(final String... textOptions) {
		for (final String text : textOptions) {
			final List<By> locators = Arrays.asList(
					By.xpath("//button[contains(normalize-space(.), '" + text + "')]"),
					By.xpath("//a[contains(normalize-space(.), '" + text + "')]"),
					By.xpath("//li[contains(normalize-space(.), '" + text + "')]"),
					By.xpath("//p[contains(normalize-space(.), '" + text + "')]/ancestor::*[self::button or self::a][1]"),
					By.xpath("//*[@role='button' and contains(normalize-space(.), '" + text + "')]"),
					By.xpath("//span[contains(normalize-space(.), '" + text + "')]/ancestor::*[self::button or self::a][1]"));

			for (final By locator : locators) {
				for (final WebElement element : driver.findElements(locator)) {
					if (element.isDisplayed() && element.isEnabled()) {
						return element;
					}
				}
			}
		}
		return null;
	}

	private boolean anyElementVisible(final By... locators) {
		for (final By locator : locators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isTextVisible(final String text) {
		for (final WebElement element : driver.findElements(By.xpath("//*[contains(normalize-space(.), '" + text + "')]"))) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void chooseGoogleAccountIfSelectorAppears(final String accountEmail) {
		final String initialHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		try {
			wait.until((ExpectedCondition<Boolean>) d -> d != null && d.getWindowHandles().size() > handlesBefore.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			// Google flow may remain in same tab.
		}

		try {
			final WebElement accountChoice = findVisibleClickableByText(accountEmail);
			if (accountChoice != null) {
				accountChoice.click();
				waitForUiLoad();
			}
		} catch (final Exception ignored) {
			// Account may already be authenticated and skipped.
		}

		for (final String handle : driver.getWindowHandles()) {
			if (handle.equals(initialHandle)) {
				driver.switchTo().window(handle);
				break;
			}
		}

		waitForUiLoad();
	}

	private void typeInBusinessNameIfPresent(final String businessName) {
		final List<By> candidateLocators = Arrays.asList(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));

		for (final By locator : candidateLocators) {
			for (final WebElement input : driver.findElements(locator)) {
				if (input.isDisplayed() && input.isEnabled()) {
					input.click();
					input.clear();
					input.sendKeys(businessName);
					return;
				}
			}
		}
	}

	private String sectionTextOrWholePage(final String sectionTitle) {
		final List<WebElement> candidates = driver
				.findElements(By.xpath("//*[self::section or self::div][.//*[contains(normalize-space(.), '" + sectionTitle + "')]]"));
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				return candidate.getText();
			}
		}
		return visibleBodyText();
	}

	private String visibleBodyText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private boolean hasLikelyUserName(final String infoText) {
		final String expectedUserName = System.getenv("SALEADS_EXPECTED_USER_NAME");
		if (expectedUserName != null && !expectedUserName.isBlank()) {
			return infoText.toLowerCase().contains(expectedUserName.toLowerCase());
		}

		final List<String> filteredLines = Arrays.stream(infoText.split("\\R")).map(String::trim).filter(s -> !s.isBlank())
				.filter(s -> !s.contains("@")).filter(s -> !s.equalsIgnoreCase("Información General"))
				.filter(s -> !s.equalsIgnoreCase("BUSINESS PLAN")).filter(s -> !s.equalsIgnoreCase("Cambiar Plan"))
				.filter(s -> s.length() > 2).collect(Collectors.toList());

		return !filteredLines.isEmpty();
	}

	private void waitForUiLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		try {
			Thread.sleep(400L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String name) throws IOException {
		final Path screenshotPath = evidenceDir.resolve(name + ".png");
		Files.copy(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath(), screenshotPath,
				StandardCopyOption.REPLACE_EXISTING);
		evidenceNotes.add("Screenshot: " + screenshotPath);
	}

	private void captureScreenshotQuietly(final String name) {
		try {
			captureScreenshot(name);
		} catch (final IOException ignored) {
			// Ignore screenshot failures in cleanup paths.
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio - Final Report").append(System.lineSeparator());
		reportBuilder.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator()).append(System.lineSeparator());

		for (final String field : FINAL_REPORT_FIELDS) {
			final boolean passed = finalReport.getOrDefault(field, false);
			reportBuilder.append(field).append(": ").append(passed ? "PASS" : "FAIL").append(System.lineSeparator());
		}

		reportBuilder.append(System.lineSeparator()).append("Evidence").append(System.lineSeparator());
		reportBuilder.append("Términos y Condiciones URL: ").append(termsUrl).append(System.lineSeparator());
		reportBuilder.append("Política de Privacidad URL: ").append(privacyUrl).append(System.lineSeparator());

		reportBuilder.append(System.lineSeparator()).append("Execution notes").append(System.lineSeparator());
		for (final String note : evidenceNotes) {
			reportBuilder.append("- ").append(note).append(System.lineSeparator());
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), reportBuilder.toString(), StandardCharsets.UTF_8);
	}

	private String buildFinalAssertionMessage() {
		final String failingSteps = finalReport.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.joining(", "));
		return "One or more validations failed: " + failingSteps + ". Evidence directory: " + evidenceDir;
	}

	private String readEnv(final String name, final String defaultValue) {
		final String value = System.getenv(name);
		return value == null ? defaultValue : value;
	}

	private String rootMessage(final Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getClass().getSimpleName() + ": " + current.getMessage();
	}

	private String slug(final String input) {
		return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	@FunctionalInterface
	private interface CheckedStep {
		void execute() throws Exception;
	}
}
