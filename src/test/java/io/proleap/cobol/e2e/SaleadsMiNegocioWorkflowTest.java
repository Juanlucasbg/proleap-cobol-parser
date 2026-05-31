package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
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

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> finalReport = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(readConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"SaleADS E2E test is disabled. Set SALEADS_E2E_ENABLED=true to execute this workflow end-to-end.",
				enabled);

		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", "").trim();
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL (or -Dsaleads.login.url) to the current environment login page before running this test.",
				!loginUrl.isEmpty());

		final int timeoutSeconds = Integer
				.parseInt(readConfig("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", "30").trim());
		final boolean headless = Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"));

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu");

		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDirectory = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDirectory);

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		try {
			if (evidenceDirectory != null && !finalReport.isEmpty()) {
				writeFinalReport();
			}
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		final StepResult login = runStep("Login", this::executeLoginStep);
		final StepResult miNegocioMenu = login.isPassed() ? runStep("Mi Negocio menu", this::executeMiNegocioMenuStep)
				: blockedStep("Mi Negocio menu", "Blocked because Login step failed.");

		final StepResult agregarNegocioModal = miNegocioMenu.isPassed()
				? runStep("Agregar Negocio modal", this::executeAgregarNegocioModalStep)
				: blockedStep("Agregar Negocio modal", "Blocked because Mi Negocio menu step failed.");

		final StepResult administrarNegociosView = miNegocioMenu.isPassed()
				? runStep("Administrar Negocios view", this::executeAdministrarNegociosViewStep)
				: blockedStep("Administrar Negocios view", "Blocked because Mi Negocio menu step failed.");

		final StepResult informacionGeneral = administrarNegociosView.isPassed()
				? runStep("Información General", this::executeInformacionGeneralStep)
				: blockedStep("Información General", "Blocked because Administrar Negocios view step failed.");

		final StepResult detallesCuenta = administrarNegociosView.isPassed()
				? runStep("Detalles de la Cuenta", this::executeDetallesCuentaStep)
				: blockedStep("Detalles de la Cuenta", "Blocked because Administrar Negocios view step failed.");

		final StepResult tusNegocios = administrarNegociosView.isPassed()
				? runStep("Tus Negocios", this::executeTusNegociosStep)
				: blockedStep("Tus Negocios", "Blocked because Administrar Negocios view step failed.");

		final StepResult terminos = administrarNegociosView.isPassed()
				? runStep("Términos y Condiciones",
						step -> executeLegalLinkStep(step, "Términos y Condiciones", "Terminos y Condiciones",
								"Términos y Condiciones", "Terminos y Condiciones", "08-terminos-y-condiciones"))
				: blockedStep("Términos y Condiciones", "Blocked because Administrar Negocios view step failed.");

		final StepResult privacidad = administrarNegociosView.isPassed()
				? runStep("Política de Privacidad",
						step -> executeLegalLinkStep(step, "Política de Privacidad", "Politica de Privacidad",
								"Política de Privacidad", "Politica de Privacidad", "09-politica-de-privacidad"))
				: blockedStep("Política de Privacidad", "Blocked because Administrar Negocios view step failed.");

		ensureReportFields();
		printFinalReportToConsole();
		assertFinalReport();
	}

	private void executeLoginStep(final StepResult step) {
		final String applicationWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new HashSet<>(driver.getWindowHandles());

		clickByText("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
				"Continuar con Google", "Login with Google", "Ingresar con Google", "Google");

		switchToGoogleContextIfNeeded(handlesBeforeClick);
		selectGoogleAccountIfPrompted(step);
		returnToApplicationWindow(applicationWindow);
		waitForUiToLoad();

		final boolean mainInterfaceVisible = isElementVisible(By.tagName("main"))
				|| isElementVisible(By.cssSelector("[role='main']")) || isTextVisible("Panel", "Dashboard", "Inicio");
		validate(step, "Main application interface appears.", mainInterfaceVisible);

		final boolean sidebarVisible = isElementVisible(By.tagName("aside")) || isTextVisible("Negocio", "Mi Negocio");
		validate(step, "Left sidebar navigation is visible.", sidebarVisible);

		captureScreenshot(step, "01-dashboard-loaded");
	}

	private void executeMiNegocioMenuStep(final StepResult step) {
		validate(step, "Section 'Negocio' is visible.", isTextVisible("Negocio"));

		clickByText("Mi Negocio");

		final boolean submenuExpanded = isTextVisible("Agregar Negocio") && isTextVisible("Administrar Negocios");
		validate(step, "Submenu expands after clicking 'Mi Negocio'.", submenuExpanded);
		validate(step, "'Agregar Negocio' is visible.", isTextVisible("Agregar Negocio"));
		validate(step, "'Administrar Negocios' is visible.", isTextVisible("Administrar Negocios"));

		captureScreenshot(step, "02-mi-negocio-expanded-menu");
	}

	private void executeAgregarNegocioModalStep(final StepResult step) {
		clickByText("Agregar Negocio");

		waitForVisibleText("Crear Nuevo Negocio");
		validate(step, "Modal title 'Crear Nuevo Negocio' is visible.", isTextVisible("Crear Nuevo Negocio"));

		final WebElement negocioInput = findNombreNegocioInput();
		validate(step, "Input field 'Nombre del Negocio' exists.", negocioInput != null);
		validate(step, "Text 'Tienes 2 de 3 negocios' is visible.", isTextVisible("Tienes 2 de 3 negocios"));
		validate(step, "Button 'Cancelar' is visible.", isTextVisible("Cancelar"));
		validate(step, "Button 'Crear Negocio' is visible.", isTextVisible("Crear Negocio"));

		captureScreenshot(step, "03-agregar-negocio-modal");

		if (negocioInput != null) {
			scrollIntoView(negocioInput);
			negocioInput.click();
			negocioInput.clear();
			negocioInput.sendKeys("Negocio Prueba Automatización");
		}

		clickByText("Cancelar");
		waitUntilTextIsNotVisible("Crear Nuevo Negocio");
	}

	private void executeAdministrarNegociosViewStep(final StepResult step) {
		if (!isTextVisible("Administrar Negocios")) {
			clickByText("Mi Negocio");
		}

		clickByText("Administrar Negocios");
		waitForVisibleText("Información General", "Informacion General");

		validate(step, "Section 'Información General' exists.", isTextVisible("Información General", "Informacion General"));
		validate(step, "Section 'Detalles de la Cuenta' exists.",
				isTextVisible("Detalles de la Cuenta", "Detalles de la Cuenta"));
		validate(step, "Section 'Tus Negocios' exists.", isTextVisible("Tus Negocios"));
		validate(step, "Section 'Sección Legal' exists.", isTextVisible("Sección Legal", "Seccion Legal"));

		captureScreenshot(step, "04-administrar-negocios-page");
	}

	private void executeInformacionGeneralStep(final StepResult step) {
		final WebElement section = findSectionContainer("Información General", "Informacion General");
		validate(step, "Información General section is present.", section != null);

		final String sectionText = section == null ? "" : section.getText();

		validate(step, "User name is visible.", hasLikelyUserName(sectionText));
		validate(step, "User email is visible.", containsEmail(sectionText));
		validate(step, "Text 'BUSINESS PLAN' is visible.", containsIgnoreCase(sectionText, "BUSINESS PLAN"));
		validate(step, "Button 'Cambiar Plan' is visible.", isTextVisible("Cambiar Plan"));
	}

	private void executeDetallesCuentaStep(final StepResult step) {
		final WebElement section = findSectionContainer("Detalles de la Cuenta", "Detalles de la Cuenta");
		validate(step, "Detalles de la Cuenta section is present.", section != null);

		final String sectionText = section == null ? "" : section.getText();

		validate(step, "'Cuenta creada' is visible.", containsIgnoreCase(sectionText, "Cuenta creada"));
		validate(step, "'Estado activo' is visible.",
				containsIgnoreCase(sectionText, "Estado activo")
						|| (containsIgnoreCase(sectionText, "Estado") && containsIgnoreCase(sectionText, "activo")));
		validate(step, "'Idioma seleccionado' is visible.", containsIgnoreCase(sectionText, "Idioma seleccionado"));
	}

	private void executeTusNegociosStep(final StepResult step) {
		final WebElement section = findSectionContainer("Tus Negocios");
		validate(step, "Tus Negocios section is present.", section != null);

		final String sectionText = section == null ? "" : section.getText();
		final boolean hasBusinessList = section != null
				&& (!section.findElements(By.xpath(".//li")).isEmpty() || !section.findElements(By.xpath(".//tr")).isEmpty()
						|| sectionText.split("\\R").length >= 4);

		validate(step, "Business list is visible.", hasBusinessList);
		validate(step, "Button 'Agregar Negocio' exists.", section != null && sectionText.contains("Agregar Negocio"));
		validate(step, "Text 'Tienes 2 de 3 negocios' is visible.", containsIgnoreCase(sectionText, "Tienes 2 de 3 negocios"));
	}

	private void executeLegalLinkStep(final StepResult step, final String linkText, final String linkTextAlt,
			final String headingText, final String headingTextAlt, final String screenshotName) {
		final String applicationWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new HashSet<>(driver.getWindowHandles());
		final String initialUrl = driver.getCurrentUrl();

		clickByText(linkText, linkTextAlt);

		boolean openedNewTab = false;
		try {
			wait.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size()
					|| !safeString(d.getCurrentUrl()).equals(initialUrl));
			openedNewTab = driver.getWindowHandles().size() > handlesBeforeClick.size();
		} catch (final TimeoutException timeout) {
			step.fail("No navigation detected after clicking '" + linkText + "'.");
			return;
		}

		if (openedNewTab) {
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		waitForUiToLoad();

		validate(step, "Heading '" + headingText + "' is visible.", isTextVisible(headingText, headingTextAlt));
		validate(step, "Legal content text is visible.", isLegalContentVisible());
		step.addNote("Final URL: " + driver.getCurrentUrl());

		captureScreenshot(step, screenshotName);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(applicationWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private WebElement findNombreNegocioInput() {
		final List<By> locators = Arrays.asList(
				By.xpath("//input[@name='nombreNegocio' or @name='businessName']"),
				By.xpath("//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"));

		for (final By locator : locators) {
			try {
				final WebElement element = new WebDriverWait(driver, Duration.ofSeconds(3))
						.until(ExpectedConditions.visibilityOfElementLocated(locator));
				if (element.isDisplayed()) {
					return element;
				}
			} catch (final TimeoutException ignored) {
				// try next locator
			}
		}

		return null;
	}

	private WebElement findSectionContainer(final String... sectionTitles) {
		WebElement heading = null;

		for (final String title : sectionTitles) {
			for (final By locator : sectionHeadingLocators(title)) {
				try {
					heading = new WebDriverWait(driver, Duration.ofSeconds(4))
							.until(ExpectedConditions.visibilityOfElementLocated(locator));
					if (heading != null) {
						break;
					}
				} catch (final TimeoutException ignored) {
					// try next locator
				}
			}

			if (heading != null) {
				break;
			}
		}

		if (heading == null) {
			return null;
		}

		try {
			return heading.findElement(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
		} catch (final NoSuchElementException noContainer) {
			return heading;
		}
	}

	private void selectGoogleAccountIfPrompted(final StepResult step) {
		if (!isGoogleAuthPage()) {
			return;
		}

		if (clickByTextIfPresent(8, GOOGLE_ACCOUNT_EMAIL)) {
			step.addNote("Google account selector displayed and target account selected.");
			waitForUiToLoad();
			return;
		}

		try {
			final WebElement emailInput = new WebDriverWait(driver, Duration.ofSeconds(5))
					.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[type='email']")));
			emailInput.clear();
			emailInput.sendKeys(GOOGLE_ACCOUNT_EMAIL);
			emailInput.sendKeys(Keys.ENTER);
			step.addNote("Google email input was shown and filled.");
			waitForUiToLoad();
		} catch (final TimeoutException noEmailInput) {
			step.addNote("Google selector did not require account selection (session might already be authenticated).");
		}
	}

	private void switchToGoogleContextIfNeeded(final Set<String> handlesBeforeClick) {
		try {
			wait.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size() || isGoogleAuthPage());
		} catch (final TimeoutException ignored) {
			// login can complete without opening Google chooser
		}

		final Set<String> handlesAfterClick = driver.getWindowHandles();
		if (handlesAfterClick.size() > handlesBeforeClick.size()) {
			for (final String handle : handlesAfterClick) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}
	}

	private void returnToApplicationWindow(final String applicationWindow) {
		if (!driver.getWindowHandles().contains(applicationWindow)) {
			return;
		}

		driver.switchTo().window(applicationWindow);
	}

	private void clickByText(final String... candidateTexts) {
		final WebElement element = waitForClickableText(candidateTexts);
		scrollIntoView(element);
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private boolean clickByTextIfPresent(final int timeoutSeconds, final String... candidateTexts) {
		for (final String text : candidateTexts) {
			for (final By locator : clickableTextLocators(text)) {
				try {
					final WebElement candidate = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
							.until(ExpectedConditions.elementToBeClickable(locator));
					scrollIntoView(candidate);
					candidate.click();
					waitForUiToLoad();
					return true;
				} catch (final TimeoutException ignored) {
					// try next locator
				}
			}
		}

		return false;
	}

	private WebElement waitForClickableText(final String... candidateTexts) {
		for (final String text : candidateTexts) {
			for (final By locator : clickableTextLocators(text)) {
				try {
					return wait.until(ExpectedConditions.elementToBeClickable(locator));
				} catch (final TimeoutException ignored) {
					// try next locator
				}
			}
		}

		throw new TimeoutException("Unable to find a clickable element with visible text: " + Arrays.toString(candidateTexts));
	}

	private boolean isTextVisible(final String... candidateTexts) {
		for (final String text : candidateTexts) {
			for (final By locator : textLocators(text)) {
				try {
					final WebElement element = new WebDriverWait(driver, Duration.ofSeconds(2))
							.until(ExpectedConditions.visibilityOfElementLocated(locator));
					if (element.isDisplayed()) {
						return true;
					}
				} catch (final TimeoutException ignored) {
					// try next locator
				}
			}
		}

		return false;
	}

	private void waitForVisibleText(final String... candidateTexts) {
		for (final String text : candidateTexts) {
			for (final By locator : textLocators(text)) {
				try {
					wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
					return;
				} catch (final TimeoutException ignored) {
					// try next locator
				}
			}
		}

		throw new TimeoutException("Unable to find visible text: " + Arrays.toString(candidateTexts));
	}

	private void waitUntilTextIsNotVisible(final String text) {
		for (final By locator : textLocators(text)) {
			try {
				wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
				return;
			} catch (final TimeoutException ignored) {
				// try next locator
			}
		}
	}

	private boolean isElementVisible(final By locator) {
		try {
			final WebElement element = new WebDriverWait(driver, Duration.ofSeconds(2))
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return element.isDisplayed();
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete"
					.equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final TimeoutException ignored) {
			// some pages keep loading async resources; continue with functional waits
		}

		try {
			Thread.sleep(500);
		} catch (final InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final StepResult step, final String checkpointName) {
		try {
			final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			final Path screenshotPath = evidenceDirectory.resolve(checkpointName + ".png");
			Files.write(screenshotPath, screenshot);
			step.addEvidence(screenshotPath.toString());
		} catch (final Exception screenshotError) {
			step.addNote("Could not capture screenshot '" + checkpointName + "': " + screenshotError.getMessage());
		}
	}

	private boolean containsEmail(final String text) {
		final Matcher matcher = EMAIL_PATTERN.matcher(safeString(text));
		return matcher.find();
	}

	private boolean hasLikelyUserName(final String sectionText) {
		final String normalized = safeString(sectionText);
		if (normalized.isEmpty()) {
			return false;
		}

		final String[] lines = normalized.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			final String lineLower = line.toLowerCase(Locale.ROOT);

			if (line.isEmpty() || line.length() < 3 || line.contains("@")) {
				continue;
			}

			if (lineLower.contains("información general") || lineLower.contains("informacion general")
					|| lineLower.contains("business plan") || lineLower.contains("cambiar plan")
					|| lineLower.contains("cuenta") || lineLower.contains("detalles")
					|| lineLower.contains("tus negocios")) {
				continue;
			}

			return true;
		}

		return false;
	}

	private boolean containsIgnoreCase(final String text, final String expected) {
		return safeString(text).toLowerCase(Locale.ROOT).contains(safeString(expected).toLowerCase(Locale.ROOT));
	}

	private boolean isLegalContentVisible() {
		try {
			final WebElement body = driver.findElement(By.tagName("body"));
			final String bodyText = safeString(body.getText()).trim();
			return bodyText.length() > 120;
		} catch (final NoSuchElementException missingBody) {
			return false;
		}
	}

	private boolean isGoogleAuthPage() {
		final String currentUrl = safeString(driver.getCurrentUrl()).toLowerCase(Locale.ROOT);
		final String title = safeString(driver.getTitle()).toLowerCase(Locale.ROOT);
		return currentUrl.contains("accounts.google.com") || title.contains("google");
	}

	private List<By> clickableTextLocators(final String text) {
		final String literal = toXPathLiteral(text);
		return Arrays.asList(By.xpath("//button[normalize-space()=" + literal + "]"),
				By.xpath("//a[normalize-space()=" + literal + "]"),
				By.xpath("//*[@role='button' and normalize-space()=" + literal + "]"),
				By.xpath("//*[normalize-space()=" + literal + "]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"),
				By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
	}

	private List<By> textLocators(final String text) {
		final String literal = toXPathLiteral(text);
		return Arrays.asList(By.xpath("//*[normalize-space()=" + literal + "]"),
				By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
	}

	private List<By> sectionHeadingLocators(final String text) {
		final String literal = toXPathLiteral(text);
		return Arrays.asList(By.xpath("//h1[normalize-space()=" + literal + "]"),
				By.xpath("//h2[normalize-space()=" + literal + "]"),
				By.xpath("//h3[normalize-space()=" + literal + "]"),
				By.xpath("//h4[normalize-space()=" + literal + "]"),
				By.xpath("//*[contains(@class, 'title') and normalize-space()=" + literal + "]"),
				By.xpath("//*[normalize-space()=" + literal + "]"));
	}

	private void validate(final StepResult step, final String validation, final boolean condition) {
		if (!condition) {
			step.fail(validation);
		}
	}

	private StepResult runStep(final String stepName, final StepExecutor executor) {
		final StepResult result = new StepResult(stepName);

		try {
			executor.execute(result);
		} catch (final Exception error) {
			result.fail("Unexpected step error: " + error.getMessage());
		}

		finalReport.put(stepName, result);
		return result;
	}

	private StepResult blockedStep(final String stepName, final String reason) {
		final StepResult result = new StepResult(stepName);
		result.fail(reason);
		finalReport.put(stepName, result);
		return result;
	}

	private void ensureReportFields() {
		for (final String field : REPORT_FIELDS) {
			if (!finalReport.containsKey(field)) {
				blockedStep(field, "Step was not executed.");
			}
		}
	}

	private void assertFinalReport() {
		final List<String> failingSteps = new ArrayList<>();

		for (final String field : REPORT_FIELDS) {
			final StepResult result = finalReport.get(field);
			if (result != null && !result.isPassed()) {
				failingSteps.add(field + " -> " + String.join("; ", result.failures));
			}
		}

		if (!failingSteps.isEmpty()) {
			throw new AssertionError("SaleADS Mi Negocio workflow failures:\n - " + String.join("\n - ", failingSteps)
					+ "\nEvidence directory: " + evidenceDirectory.toAbsolutePath());
		}
	}

	private void printFinalReportToConsole() {
		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		for (final String field : REPORT_FIELDS) {
			final StepResult result = finalReport.get(field);
			final String status = result == null ? "FAIL" : (result.isPassed() ? "PASS" : "FAIL");
			System.out.println(field + ": " + status);
			if (result != null) {
				for (final String failure : result.failures) {
					System.out.println("  - " + failure);
				}
				for (final String note : result.notes) {
					System.out.println("  * " + note);
				}
				for (final String evidence : result.evidence) {
					System.out.println("  + screenshot: " + evidence);
				}
			}
		}
		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Final Report");
		lines.add("Run at: " + LocalDateTime.now());
		lines.add("Evidence directory: " + evidenceDirectory.toAbsolutePath());
		lines.add("");

		for (final String field : REPORT_FIELDS) {
			final StepResult result = finalReport.get(field);
			final String status = result == null ? "FAIL" : (result.isPassed() ? "PASS" : "FAIL");
			lines.add(field + ": " + status);

			if (result != null) {
				for (final String failure : result.failures) {
					lines.add("  - " + failure);
				}

				for (final String note : result.notes) {
					lines.add("  * " + note);
				}

				for (final String evidence : result.evidence) {
					lines.add("  + screenshot: " + evidence);
				}
			}
		}

		Files.write(evidenceDirectory.resolve("final-report.txt"), lines);
	}

	private String readConfig(final String systemProperty, final String envVar, final String defaultValue) {
		final String propertyValue = System.getProperty(systemProperty);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envVar);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private String toXPathLiteral(final String value) {
		if (value.contains("'") && value.contains("\"")) {
			final StringBuilder builder = new StringBuilder("concat(");
			final char[] chars = value.toCharArray();
			for (int i = 0; i < chars.length; i++) {
				final String literal = chars[i] == '\'' ? "\"'\"" : "'" + chars[i] + "'";
				builder.append(literal);
				if (i < chars.length - 1) {
					builder.append(", ");
				}
			}
			builder.append(")");
			return builder.toString();
		}

		if (value.contains("'")) {
			return "\"" + value + "\"";
		}

		return "'" + value + "'";
	}

	private String safeString(final String value) {
		return value == null ? "" : value;
	}

	@FunctionalInterface
	private interface StepExecutor {
		void execute(StepResult step) throws Exception;
	}

	private static final class StepResult {
		private final String name;
		private final List<String> failures = new ArrayList<>();
		private final List<String> notes = new ArrayList<>();
		private final List<String> evidence = new ArrayList<>();

		private StepResult(final String name) {
			this.name = name;
		}

		private void fail(final String message) {
			failures.add(message);
		}

		private void addNote(final String message) {
			notes.add(message);
		}

		private void addEvidence(final String path) {
			evidence.add(path);
		}

		private boolean isPassed() {
			return failures.isEmpty();
		}

		@Override
		public String toString() {
			return "StepResult{name='" + name + "', failures=" + failures + ", notes=" + notes + ", evidence=" + evidence
					+ "}";
		}
	}
}
