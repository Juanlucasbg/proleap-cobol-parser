package io.proleap.e2e.saleads;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioWorkflowE2ETest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String SAMPLE_BUSINESS_NAME = "Negocio Prueba Automatizacion";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration LONG_TIMEOUT = Duration.ofSeconds(45);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern FULL_NAME_PATTERN = Pattern
			.compile("[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ'\\-]+\\s+[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ'\\-]+");

	private static final List<String> REPORT_FIELDS = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Informacion General", "Detalles de la Cuenta", "Tus Negocios",
			"Terminos y Condiciones", "Politica de Privacidad");

	private final Map<String, StepOutcome> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String appHandle;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set -Dsaleads.e2e.enabled=true to run this external E2E flow.",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

		final String saleadsUrl = System.getProperty("saleads.url", "").trim();
		Assume.assumeTrue(
				"Set -Dsaleads.url to the SaleADS login page for the target environment (dev/staging/prod).",
				!saleadsUrl.isEmpty());

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");
		if (Boolean.parseBoolean(System.getProperty("saleads.e2e.headless", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.get(saleadsUrl);
		waitForUiToLoad();
		appHandle = driver.getWindowHandle();

		final String runStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		screenshotDir = Path.of("target", "saleads-e2e-screenshots", runStamp);
		Files.createDirectories(screenshotDir);

		for (final String field : REPORT_FIELDS) {
			report.put(field, StepOutcome.pending());
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Informacion General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Terminos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones",
				"08-terminos-y-condiciones"));
		runStep("Politica de Privacidad",
				() -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09-politica-privacidad"));

		Assert.assertTrue("Some validations failed.\n" + buildFinalReport(), allFieldsPassed());
	}

	private void stepLoginWithGoogle() {
		clickFirstClickableByText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Login with Google", "Google");
		maybeSelectGoogleAccount();

		waitForAnyVisibleText(LONG_TIMEOUT, "Negocio", "Mi Negocio", "Dashboard", "Inicio");
		Assert.assertTrue("Main application interface did not appear.", isAnyTextVisible("Negocio", "Mi Negocio", "Inicio"));
		Assert.assertTrue("Left sidebar navigation is not visible.",
				isAnyVisibleElementPresent(By.xpath("//aside//*[contains(normalize-space(), 'Negocio')]"),
						By.xpath("//*[contains(@class,'sidebar') and .//*[contains(normalize-space(), 'Negocio')]]"),
						By.xpath("//*[contains(normalize-space(), 'Mi Negocio')]")));

		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		clickIfClickableByText("Negocio");
		clickFirstClickableByText("Mi Negocio");

		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Agregar Negocio", "Administrar Negocios");
		Assert.assertTrue("Expected expanded menu option 'Agregar Negocio' was not visible.", isTextVisible("Agregar Negocio"));
		Assert.assertTrue("Expected expanded menu option 'Administrar Negocios' was not visible.",
				isTextVisible("Administrar Negocios"));

		takeScreenshot("02-mi-negocio-expanded-menu");
	}

	private void stepValidateAgregarNegocioModal() {
		clickFirstClickableByText("Agregar Negocio");

		waitForVisibleText(DEFAULT_TIMEOUT, "Crear Nuevo Negocio");
		Assert.assertTrue("Input field 'Nombre del Negocio' is missing.",
				isAnyVisibleElementPresent(
						By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
						By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
						By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]")));
		Assert.assertTrue("Expected text 'Tienes 2 de 3 negocios' was not visible.", isTextVisible("Tienes 2 de 3 negocios"));
		Assert.assertTrue("Button 'Cancelar' is missing.", isTextVisible("Cancelar"));
		Assert.assertTrue("Button 'Crear Negocio' is missing.", isTextVisible("Crear Negocio"));

		takeScreenshot("03-agregar-negocio-modal");

		final WebElement nameInput = firstVisibleElement(By.xpath("//input[contains(@placeholder,'Nombre')]"),
				Duration.ofSeconds(3));
		if (nameInput != null) {
			nameInput.clear();
			nameInput.sendKeys(SAMPLE_BUSINESS_NAME);
		}
		clickFirstClickableByText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() {
		if (!isTextVisible("Administrar Negocios")) {
			clickFirstClickableByText("Mi Negocio");
		}
		clickFirstClickableByText("Administrar Negocios");

		waitForAnyVisibleText(LONG_TIMEOUT, "Información General", "Detalles de la Cuenta", "Tus Negocios", "Sección Legal");
		Assert.assertTrue("Section 'Información General' is not visible.", isTextVisible("Información General"));
		Assert.assertTrue("Section 'Detalles de la Cuenta' is not visible.", isTextVisible("Detalles de la Cuenta"));
		Assert.assertTrue("Section 'Tus Negocios' is not visible.", isTextVisible("Tus Negocios"));
		Assert.assertTrue("Section 'Sección Legal' is not visible.", isTextVisible("Sección Legal"));

		takeScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		final String sectionText = getSectionText("Información General");
		Assert.assertTrue("Section 'Información General' is missing or empty.", !sectionText.isBlank());
		Assert.assertTrue("User name was not found in 'Información General'.", FULL_NAME_PATTERN.matcher(sectionText).find());
		Assert.assertTrue("User email was not found in 'Información General'.", EMAIL_PATTERN.matcher(sectionText).find());
		Assert.assertTrue("Text 'BUSINESS PLAN' is missing.", sectionText.contains("BUSINESS PLAN"));
		Assert.assertTrue("Button 'Cambiar Plan' is not visible.", isTextVisible("Cambiar Plan"));
	}

	private void stepValidateDetallesCuenta() {
		final String sectionText = getSectionText("Detalles de la Cuenta");
		Assert.assertTrue("Section 'Detalles de la Cuenta' is missing or empty.", !sectionText.isBlank());
		Assert.assertTrue("'Cuenta creada' is not visible.", containsIgnoreCase(sectionText, "Cuenta creada"));
		Assert.assertTrue("'Estado activo' is not visible.", containsIgnoreCase(sectionText, "Estado")
				&& containsIgnoreCase(sectionText, "activo"));
		Assert.assertTrue("'Idioma seleccionado' is not visible.", containsIgnoreCase(sectionText, "Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionContainer("Tus Negocios");
		Assert.assertNotNull("Section 'Tus Negocios' is missing.", section);
		final String sectionText = section.getText();

		Assert.assertTrue("Business list is not visible.", hasBusinessList(section));
		Assert.assertTrue("Button 'Agregar Negocio' is not visible in 'Tus Negocios'.",
				containsIgnoreCase(sectionText, "Agregar Negocio"));
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' is not visible in 'Tus Negocios'.",
				containsIgnoreCase(sectionText, "Tienes 2 de 3 negocios"));
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName) {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickFirstClickableByText(linkText);

		final String targetHandle = waitForLegalHandle(handlesBeforeClick, originalHandle);
		final boolean openedNewTab = !Objects.equals(targetHandle, originalHandle);
		if (openedNewTab) {
			driver.switchTo().window(targetHandle);
			waitForUiToLoad();
		}

		waitForVisibleText(LONG_TIMEOUT, expectedHeading);
		Assert.assertTrue("Heading '" + expectedHeading + "' is not visible.", isTextVisible(expectedHeading));
		Assert.assertTrue("Legal content text is not visible for '" + expectedHeading + "'.", hasLegalContentVisible());

		final Path screenshotPath = takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();
		appendStepDetail(normalizeReportField(linkText), "URL: " + finalUrl + " | Screenshot: " + screenshotPath);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		appHandle = originalHandle;
	}

	private String waitForLegalHandle(final Set<String> handlesBeforeClick, final String fallbackHandle) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(d -> d.getWindowHandles().size() != handlesBeforeClick.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBeforeClick.contains(handle)) {
					return handle;
				}
			}
		} catch (final TimeoutException ignored) {
			// Link may open in same tab.
		}
		return fallbackHandle;
	}

	private void maybeSelectGoogleAccount() {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String handle : new ArrayList<>(driver.getWindowHandles())) {
				driver.switchTo().window(handle);
				final WebElement accountOption = firstVisibleElement(
						By.xpath("//*[contains(normalize-space(), " + toXPathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]"),
						Duration.ofSeconds(1));
				if (accountOption != null) {
					safeClick(accountOption);
					waitForUiToLoad();
					driver.switchTo().window(appHandle);
					return;
				}
			}
			if (isAnyTextVisible("Negocio", "Mi Negocio", "Dashboard", "Inicio")) {
				driver.switchTo().window(appHandle);
				return;
			}
			sleep(500);
		}
		driver.switchTo().window(appHandle);
	}

	private void runStep(final String stepName, final StepAction stepAction) {
		try {
			stepAction.execute();
			markStepPass(stepName);
		} catch (final Throwable error) {
			report.put(stepName, StepOutcome.fail(error.getMessage()));
		}
	}

	private boolean allFieldsPassed() {
		for (final String field : REPORT_FIELDS) {
			if (report.get(field).status != StepStatus.PASS) {
				return false;
			}
		}
		return true;
	}

	private String getSectionText(final String sectionTitle) {
		final WebElement section = findSectionContainer(sectionTitle);
		return section == null ? "" : section.getText();
	}

	private WebElement findSectionContainer(final String sectionTitle) {
		waitForVisibleText(DEFAULT_TIMEOUT, sectionTitle);
		final String literal = toXPathLiteral(sectionTitle);
		final List<By> candidateLocators = List.of(
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(), " + literal
						+ ")]/ancestor::*[self::section or self::article or self::div][1]"),
				By.xpath("//*[contains(@class,'section') and .//*[contains(normalize-space(), " + literal + ")]]"),
				By.xpath("//*[contains(normalize-space(), " + literal + ")]"));

		for (final By locator : candidateLocators) {
			final WebElement element = firstVisibleElement(locator, Duration.ofSeconds(2));
			if (element != null) {
				return element;
			}
		}
		return null;
	}

	private boolean hasBusinessList(final WebElement section) {
		final List<WebElement> listItems = section.findElements(By.xpath(".//li | .//tr | .//article"));
		if (!listItems.isEmpty()) {
			return true;
		}
		final List<WebElement> cards = section.findElements(By.xpath(".//*[contains(@class,'business') or contains(@class,'negocio')]"));
		return !cards.isEmpty();
	}

	private boolean hasLegalContentVisible() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p[string-length(normalize-space()) > 60]"));
		if (!paragraphs.isEmpty()) {
			return true;
		}
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		return bodyText != null && bodyText.length() > 250;
	}

	private void clickIfClickableByText(final String text) {
		final WebElement candidate = firstVisibleClickableElement(text, Duration.ofSeconds(2));
		if (candidate != null) {
			safeClick(candidate);
			waitForUiToLoad();
		}
	}

	private void clickFirstClickableByText(final String... textCandidates) {
		for (final String text : textCandidates) {
			final WebElement candidate = firstVisibleClickableElement(text, Duration.ofSeconds(4));
			if (candidate != null) {
				safeClick(candidate);
				waitForUiToLoad();
				return;
			}
		}
		throw new AssertionError("Could not click any element with visible text: " + String.join(", ", textCandidates));
	}

	private WebElement firstVisibleClickableElement(final String text, final Duration timeout) {
		final String literal = toXPathLiteral(text);
		final List<By> locators = List.of(
				By.xpath("//button[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"),
				By.xpath("//a[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"),
				By.xpath("//*[contains(@role,'button') and (normalize-space()=" + literal + " or contains(normalize-space(), "
						+ literal + "))]"),
				By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"));

		for (final By locator : locators) {
			try {
				final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
				return shortWait.until(d -> {
					for (final WebElement element : d.findElements(locator)) {
						if (element.isDisplayed() && element.isEnabled()) {
							return element;
						}
					}
					return null;
				});
			} catch (final TimeoutException ignored) {
				// Try next locator strategy.
			}
		}
		return null;
	}

	private WebElement firstVisibleElement(final By locator, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(d -> {
				for (final WebElement element : d.findElements(locator)) {
					if (element.isDisplayed()) {
						return element;
					}
				}
				return null;
			});
		} catch (final TimeoutException ignored) {
			return null;
		}
	}

	private boolean isAnyVisibleElementPresent(final By... locators) {
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
		final String literal = toXPathLiteral(text);
		final List<WebElement> elements = driver.findElements(
				By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return true;
			}
		}
		return false;
	}

	private void waitForAnyVisibleText(final Duration timeout, final String... texts) {
		new WebDriverWait(driver, timeout).until(d -> isAnyTextVisible(texts));
	}

	private void waitForVisibleText(final Duration timeout, final String text) {
		final String literal = toXPathLiteral(text);
		new WebDriverWait(driver, timeout).until(ExpectedConditions
				.visibilityOfElementLocated(By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space(), "
						+ literal + ")]")));
	}

	private void safeClick(final WebElement element) {
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete"
				.equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		sleep(700);
	}

	private Path takeScreenshot(final String logicalName) {
		final String normalizedName = logicalName.replaceAll("[^a-zA-Z0-9-_]", "_");
		final Path targetPath = screenshotDir.resolve(normalizedName + ".png");

		try {
			final File sourceFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
			return targetPath;
		} catch (final IOException error) {
			throw new RuntimeException("Failed to save screenshot: " + targetPath, error);
		}
	}

	private void appendStepDetail(final String stepName, final String extraDetail) {
		final StepOutcome current = report.get(stepName);
		if (current == null) {
			return;
		}
		final String mergedDetail = current.detail == null || current.detail.isBlank() ? extraDetail
				: current.detail + " | " + extraDetail;
		report.put(stepName, new StepOutcome(current.status, mergedDetail));
	}

	private void markStepPass(final String stepName) {
		final StepOutcome existing = report.get(stepName);
		if (existing == null || existing.detail == null || existing.detail.isBlank()
				|| "Not executed".equals(existing.detail)) {
			report.put(stepName, StepOutcome.pass("PASS"));
			return;
		}

		report.put(stepName, StepOutcome.pass(existing.detail));
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("=== SaleADS Mi Negocio workflow report ===").append(System.lineSeparator());
		for (final String field : REPORT_FIELDS) {
			final StepOutcome outcome = report.get(field);
			builder.append("- ").append(field).append(": ").append(outcome.status);
			if (outcome.detail != null && !outcome.detail.isBlank()) {
				builder.append(" (").append(outcome.detail).append(")");
			}
			builder.append(System.lineSeparator());
		}
		builder.append("Screenshots directory: ").append(screenshotDir.toAbsolutePath());
		return builder.toString();
	}

	private static boolean containsIgnoreCase(final String source, final String expected) {
		return source.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
	}

	private static String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		return "concat('" + String.join("',\"'\",'", parts) + "')";
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private static String normalizeReportField(final String fieldText) {
		final String compact = fieldText.trim().toLowerCase(Locale.ROOT);
		if (compact.contains("terminos")) {
			return "Terminos y Condiciones";
		}
		if (compact.contains("privacidad")) {
			return "Politica de Privacidad";
		}
		return fieldText;
	}

	@After
	public void tearDown() {
		try {
			if (!report.isEmpty()) {
				System.out.println(buildFinalReport());
			}
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void execute();
	}

	private enum StepStatus {
		PASS, FAIL, PENDING
	}

	private static final class StepOutcome {
		private final StepStatus status;
		private final String detail;

		private StepOutcome(final StepStatus status, final String detail) {
			this.status = status;
			this.detail = detail;
		}

		private static StepOutcome pass(final String detail) {
			return new StepOutcome(StepStatus.PASS, detail);
		}

		private static StepOutcome fail(final String detail) {
			return new StepOutcome(StepStatus.FAIL, detail);
		}

		private static StepOutcome pending() {
			return new StepOutcome(StepStatus.PENDING, "Not executed");
		}
	}
}
