package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowE2ETest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();
	private final List<String> runErrors = new ArrayList<>();

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(readConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"SaleADS E2E is disabled. Use -Dsaleads.e2e.enabled=true and provide -Dsaleads.e2e.startUrl=<url>.",
				enabled);

		final String startUrl = readConfig("saleads.e2e.startUrl", "SALEADS_E2E_START_URL", "");
		Assume.assumeTrue("SaleADS E2E start URL is required via -Dsaleads.e2e.startUrl or SALEADS_E2E_START_URL.",
				startUrl != null && !startUrl.isBlank());

		final String browser = readConfig("saleads.e2e.browser", "SALEADS_E2E_BROWSER", "chrome")
				.toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(readConfig("saleads.e2e.headless", "SALEADS_E2E_HEADLESS", "true"));
		final int timeoutSeconds = Integer.parseInt(readConfig("saleads.e2e.timeoutSeconds",
				"SALEADS_E2E_TIMEOUT_SECONDS", "30"));

		driver = createDriver(browser, headless);
		driver.manage().window().setSize(new Dimension(1920, 1080));
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		evidenceDirectory = Paths.get("target", "surefire-reports", "saleads-e2e",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDirectory);

		driver.get(startUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void shouldValidateMiNegocioWorkflow() throws IOException {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		writeFinalReport();
		Assert.assertTrue(buildFailureSummary(), runErrors.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		switchToNewTabIfPresent(handlesBeforeClick);
		selectGoogleAccountIfRequested(ACCOUNT_EMAIL);
		waitForSidebarInAnyOpenWindow();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		waitForSidebar();
		clickVisibleText("Negocio");
		clickVisibleText("Mi Negocio");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickVisibleText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		WebElement nameInput = waitForFirstVisible(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='nombreNegocio' or @aria-label='Nombre del Negocio']"
						+ " | //label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"));
		nameInput.click();
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatizacion");
		captureScreenshot("03-agregar-negocio-modal");
		clickVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisibleNow("Administrar Negocios")) {
			clickVisibleText("Mi Negocio");
		}
		clickVisibleText("Administrar Negocios");
		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		captureScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		WebElement section = waitForFirstVisible(By.xpath("//*[normalize-space()='Información General']"));
		WebElement sectionContainer = closestContainer(section);
		String content = normalizeWhitespace(sectionContainer.getText());

		Assert.assertTrue("User email should be visible in Información General.",
				EMAIL_PATTERN.matcher(content).find());
		Assert.assertTrue("BUSINESS PLAN should be visible in Información General.",
				content.contains("BUSINESS PLAN"));
		Assert.assertTrue("'Cambiar Plan' should be visible in Información General.",
				content.contains("Cambiar Plan"));

		String expectedName = readConfig("saleads.e2e.expectedUserName", "SALEADS_E2E_EXPECTED_USER_NAME", "").trim();
		if (!expectedName.isEmpty()) {
			Assert.assertTrue("Configured user name should be visible in Información General.",
					content.contains(expectedName));
		} else {
			Assert.assertTrue("A user name-like label should be visible in Información General.",
					containsNameLikeText(content));
		}
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		openAndValidateLegalLink("Términos y Condiciones", "08-terminos-y-condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		openAndValidateLegalLink("Política de Privacidad", "09-politica-de-privacidad");
	}

	private void openAndValidateLegalLink(final String linkText, final String screenshotName) throws Exception {
		waitForTextVisible("Sección Legal");
		String appHandle = driver.getWindowHandle();
		Set<String> handlesBeforeClick = driver.getWindowHandles();
		String appUrlBeforeClick = driver.getCurrentUrl();

		clickVisibleText(linkText);
		boolean openedNewTab = switchToNewTabIfPresent(handlesBeforeClick);
		waitForUiToLoad();

		assertTextVisible(linkText);
		Assert.assertTrue("Legal content should be visible for " + linkText + ".",
				normalizeWhitespace(driver.findElement(By.tagName("body")).getText()).length() > 300);
		capturedUrls.put(linkText, driver.getCurrentUrl());
		captureScreenshot(screenshotName);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
			waitForUiToLoad();
			if (!driver.getCurrentUrl().equals(appUrlBeforeClick)) {
				driver.get(appUrlBeforeClick);
				waitForUiToLoad();
			}
		}
	}

	private void runStep(final String reportField, final StepAction stepAction) {
		try {
			stepAction.run();
			stepStatus.put(reportField, "PASS");
		} catch (Throwable throwable) {
			String error = Optional.ofNullable(throwable.getMessage()).orElse(throwable.getClass().getName());
			stepStatus.put(reportField, "FAIL");
			runErrors.add(reportField + " -> " + error);
			captureScreenshot("FAIL-" + sanitizeFileName(reportField));
		}
	}

	private WebDriver createDriver(final String browser, final boolean headless) {
		if ("firefox".equals(browser)) {
			FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("--headless");
			}
			return new FirefoxDriver(options);
		}

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--no-sandbox", "--disable-dev-shm-usage");
		if (headless) {
			options.addArguments("--headless=new");
		}
		return new ChromeDriver(options);
	}

	private void clickVisibleText(final String... texts) {
		WebElement clickableElement = waitForAnyClickableText(texts);
		scrollIntoView(clickableElement);
		clickableElement.click();
		waitForUiToLoad();
	}

	private WebElement waitForAnyClickableText(final String... texts) {
		List<By> locators = new ArrayList<>();
		for (String text : texts) {
			String quoted = quoteForXpath(text);
			locators.add(By.xpath("//button[normalize-space()=" + quoted + " or contains(normalize-space()," + quoted + ")]"));
			locators.add(By.xpath("//a[normalize-space()=" + quoted + " or contains(normalize-space()," + quoted + ")]"));
			locators.add(By.xpath(
					"//*[@role='button' or @role='menuitem'][normalize-space()=" + quoted + " or contains(normalize-space(),"
							+ quoted + ")]"));
			locators.add(By.xpath("//*[normalize-space()=" + quoted + " or contains(normalize-space()," + quoted + ")]"));
		}

		return wait.until(driver -> {
			for (By locator : locators) {
				List<WebElement> elements = driver.findElements(locator);
				for (WebElement element : elements) {
					if (element.isDisplayed() && element.isEnabled()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private void waitForSidebar() {
		wait.until(driver -> {
			List<WebElement> navCandidates = driver.findElements(By.xpath("//aside | //nav"));
			boolean navVisible = navCandidates.stream().anyMatch(WebElement::isDisplayed);
			return navVisible && isTextVisibleNow("Negocio");
		});
	}

	private void waitForSidebarInAnyOpenWindow() {
		wait.until(driver -> {
			for (String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				List<WebElement> navCandidates = driver.findElements(By.xpath("//aside | //nav"));
				boolean navVisible = navCandidates.stream().anyMatch(WebElement::isDisplayed);
				if (navVisible && isTextVisibleNow("Negocio")) {
					return true;
				}
			}
			return false;
		});
	}

	private void assertTextVisible(final String text) {
		WebElement element = waitForTextVisible(text);
		Assert.assertTrue("Expected visible text: " + text, element.isDisplayed());
	}

	private WebElement waitForTextVisible(final String text) {
		String quoted = quoteForXpath(text);
		By locator = By.xpath("//*[normalize-space()=" + quoted + " or contains(normalize-space()," + quoted + ")]");
		return waitForFirstVisible(locator);
	}

	private boolean isTextVisibleNow(final String text) {
		String quoted = quoteForXpath(text);
		By locator = By.xpath("//*[normalize-space()=" + quoted + " or contains(normalize-space()," + quoted + ")]");
		return driver.findElements(locator).stream().anyMatch(WebElement::isDisplayed);
	}

	private WebElement waitForFirstVisible(final By locator) {
		return wait.until(driver -> driver.findElements(locator).stream().filter(WebElement::isDisplayed).findFirst()
				.orElse(null));
	}

	private void waitForUiToLoad() {
		wait.until(driver -> {
			try {
				Object readyState = ((JavascriptExecutor) driver).executeScript("return document.readyState");
				return "complete".equals(readyState);
			} catch (Exception e) {
				return false;
			}
		});
		try {
			wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(
					"[aria-busy='true'], .loading, .loader, .spinner, .ant-spin-spinning")));
		} catch (TimeoutException ignored) {
			// Loader selectors are best-effort and may not exist on every screen.
		}
	}

	private void selectGoogleAccountIfRequested(final String accountEmail) {
		boolean accountVisible = false;
		try {
			WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
			String quoted = quoteForXpath(accountEmail);
			shortWait.until(d -> d.findElements(By.xpath("//*[contains(normalize-space()," + quoted + ")]")).stream()
					.anyMatch(WebElement::isDisplayed));
			accountVisible = true;
		} catch (TimeoutException ignored) {
			// Account chooser might not appear when session is already authenticated.
		}

		if (accountVisible) {
			clickVisibleText(accountEmail);
		}
	}

	private boolean switchToNewTabIfPresent(final Set<String> handlesBeforeClick) {
		try {
			wait.until(driver -> driver.getWindowHandles().size() > handlesBeforeClick.size());
		} catch (TimeoutException ignored) {
			return false;
		}

		Set<String> handlesAfterClick = driver.getWindowHandles();
		for (String handle : handlesAfterClick) {
			if (!handlesBeforeClick.contains(handle)) {
				driver.switchTo().window(handle);
				return true;
			}
		}
		return false;
	}

	private void captureScreenshot(final String name) {
		if (driver == null || evidenceDirectory == null) {
			return;
		}
		try {
			Path target = evidenceDirectory.resolve(sanitizeFileName(name) + ".png");
			byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(target, screenshot);
		} catch (Exception ignored) {
			// Evidence collection should not fail the functional test flow.
		}
	}

	private void writeFinalReport() throws IOException {
		Path reportPath = evidenceDirectory.resolve("final-report.txt");
		StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio Workflow - Final Report\n");
		report.append("Generated at: ").append(LocalDateTime.now()).append('\n');
		report.append("Evidence directory: ").append(evidenceDirectory.toAbsolutePath()).append("\n\n");

		for (String field : REPORT_FIELDS) {
			report.append(field).append(": ").append(stepStatus.getOrDefault(field, "NOT_EXECUTED")).append('\n');
		}

		if (!capturedUrls.isEmpty()) {
			report.append("\nCaptured URLs:\n");
			for (Map.Entry<String, String> entry : capturedUrls.entrySet()) {
				report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		if (!runErrors.isEmpty()) {
			report.append("\nErrors:\n");
			for (String runError : runErrors) {
				report.append("- ").append(runError).append('\n');
			}
		}

		Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
	}

	private String buildFailureSummary() {
		if (runErrors.isEmpty()) {
			return "All report fields passed.";
		}

		String statusSummary = REPORT_FIELDS.stream()
				.map(field -> field + "=" + stepStatus.getOrDefault(field, "NOT_EXECUTED"))
				.collect(Collectors.joining(", "));
		String errorsSummary = runErrors.stream().collect(Collectors.joining(" | "));
		return "One or more validation steps failed. Status: [" + statusSummary + "]. Errors: " + errorsSummary;
	}

	private WebElement closestContainer(final WebElement element) {
		return element.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
	}

	private boolean containsNameLikeText(final String content) {
		return Arrays.stream(content.split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.anyMatch(line -> line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}\\s+[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}.*"));
	}

	private String normalizeWhitespace(final String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFKC).replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center',inline:'center'});",
				element);
	}

	private String quoteForXpath(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		StringBuilder builder = new StringBuilder("concat(");
		String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(",\"'\",");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private String sanitizeFileName(final String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String readConfig(final String property, final String environment, final String defaultValue) {
		String fromProperty = System.getProperty(property);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}
		String fromEnvironment = System.getenv(environment);
		if (fromEnvironment != null && !fromEnvironment.isBlank()) {
			return fromEnvironment;
		}
		return defaultValue;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
