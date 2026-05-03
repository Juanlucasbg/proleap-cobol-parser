package io.proleap.saleads;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
			Pattern.CASE_INSENSITIVE);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, Boolean> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepNotes = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		evidenceDir = Files.createDirectories(Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));

		final String debuggerAddress = firstNonBlank(System.getProperty("saleads.debuggerAddress"),
				System.getenv("SALEADS_DEBUGGER_ADDRESS"));
		final String loginUrl = firstNonBlank(System.getProperty("saleads.loginUrl"), System.getenv("SALEADS_LOGIN_URL"));
		final boolean headless = Boolean.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"),
				System.getenv("SALEADS_HEADLESS"), "false"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (headless) {
			options.addArguments("--headless=new");
		}

		if (isBlank(debuggerAddress)) {
			WebDriverManager.chromedriver().setup();
			driver = new ChromeDriver(options);
			if (isBlank(loginUrl)) {
				throw new IllegalStateException(
						"No SaleADS login page provided. Set -Dsaleads.loginUrl / SALEADS_LOGIN_URL "
								+ "or attach with -Dsaleads.debuggerAddress while browser is already on login.");
			}
			driver.get(loginUrl);
		} else {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
			driver = new ChromeDriver(options);
		}

		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegociosView);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final List<String> failedSteps = new ArrayList<>();
		for (Map.Entry<String, Boolean> entry : stepStatus.entrySet()) {
			if (!entry.getValue()) {
				failedSteps.add(entry.getKey());
			}
		}

		Assert.assertTrue(
				"One or more validations failed: " + failedSteps + ". Check evidence at " + evidenceDir.toAbsolutePath(),
				failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickByVisibleText("Sign in with Google", "Sign in Google", "Iniciar sesión con Google", "Iniciar con Google",
				"Continuar con Google", "Google");
		waitForUiToLoad();

		final Optional<String> maybeGoogleWindow = waitForNewWindow(beforeHandles, Duration.ofSeconds(10));
		if (maybeGoogleWindow.isPresent()) {
			driver.switchTo().window(maybeGoogleWindow.get());
			waitForUiToLoad();
		}

		clickIfVisibleByText(GOOGLE_ACCOUNT_EMAIL);
		waitForUiToLoad();

		if (driver.getWindowHandles().contains(appWindow)) {
			driver.switchTo().window(appWindow);
		}

		waitUntilAnyTextVisible(DEFAULT_TIMEOUT, "Mi Negocio", "Negocio");
		assertSidebarVisible();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		expandMiNegocioMenu();
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		findNombreDelNegocioInput();
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final WebElement nombreInput = findNombreDelNegocioInput();
		nombreInput.click();
		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegociosView() throws Exception {
		expandMiNegocioMenu();
		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		captureScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement infoSection = findSectionByHeading("Información General");
		final List<String> lines = normalizedLines(infoSection.getText());
		final boolean emailVisible = lines.stream().anyMatch(line -> EMAIL_PATTERN.matcher(line).matches());
		Assert.assertTrue("Expected user email to be visible in 'Información General'.", emailVisible);

		final boolean hasNonEmailIdentityLine = lines.stream().anyMatch(line -> !EMAIL_PATTERN.matcher(line).matches()
				&& !line.equalsIgnoreCase("Información General") && !line.equalsIgnoreCase("BUSINESS PLAN")
				&& !line.equalsIgnoreCase("Cambiar Plan") && line.length() >= 3);
		Assert.assertTrue("Expected a visible user name/value in 'Información General'.", hasNonEmailIdentityLine);

		assertAnyTextVisible("BUSINESS PLAN", "Business Plan");
		assertTextVisible("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		findSectionByHeading("Detalles de la Cuenta");
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement businessesSection = findSectionByHeading("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");

		final List<WebElement> listLikeElements = businessesSection
				.findElements(By.xpath(".//li | .//tr | .//*[contains(@class,'business')] | .//*[contains(@class,'negocio')]"));
		final boolean businessListVisible = listLikeElements.stream().anyMatch(WebElement::isDisplayed)
				|| normalizedLines(businessesSection.getText()).size() >= 4;
		Assert.assertTrue("Expected business list/content to be visible in 'Tus Negocios'.", businessListVisible);
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		final String url = validateLegalLink("Términos y Condiciones",
				Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"), "08-terminos-condiciones");
		capturedUrls.put("Términos y Condiciones", url);
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		final String url = validateLegalLink("Política de Privacidad",
				Arrays.asList("Política de Privacidad", "Politica de Privacidad"), "09-politica-privacidad");
		capturedUrls.put("Política de Privacidad", url);
	}

	private String validateLegalLink(final String linkText, final List<String> headingCandidates, final String screenshotName)
			throws Exception {
		final String appWindow = driver.getWindowHandle();
		final String urlBeforeClick = driver.getCurrentUrl();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		final Optional<String> maybeNewWindow = waitForNewWindow(handlesBefore, Duration.ofSeconds(8));
		final boolean openedNewTab = maybeNewWindow.isPresent();
		if (openedNewTab) {
			driver.switchTo().window(maybeNewWindow.get());
			waitForUiToLoad();
		}

		assertAnyTextVisible(headingCandidates.toArray(new String[0]));
		final String legalBodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Expected legal content text to be visible for " + linkText,
				legalBodyText != null && legalBodyText.trim().length() >= 100);

		captureScreenshot(screenshotName);
		final String legalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else if (!urlBeforeClick.equals(legalUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		assertTextVisible("Sección Legal");
		return legalUrl;
	}

	private void runStep(final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			stepStatus.put(stepName, true);
			stepNotes.put(stepName, "PASS");
		} catch (Throwable e) {
			stepStatus.put(stepName, false);
			stepNotes.put(stepName, firstNonBlank(e.getMessage(), e.getClass().getSimpleName()));
			captureScreenshotQuietly("FAIL-" + sanitizeFileName(stepName));
		}
	}

	private void expandMiNegocioMenu() throws Exception {
		if (isTextVisible("Agregar Negocio", SHORT_TIMEOUT) && isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			return;
		}

		clickByVisibleText("Mi Negocio", "Negocio");
		waitForUiToLoad();

		if (!isTextVisible("Agregar Negocio", SHORT_TIMEOUT) || !isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
	}

	private void assertSidebarVisible() {
		final List<WebElement> sidebars = driver.findElements(By.xpath("//aside | //nav[contains(@class,'sidebar')] | //nav"));
		final boolean sidebarVisible = sidebars.stream().anyMatch(WebElement::isDisplayed);
		Assert.assertTrue("Expected left sidebar navigation to be visible.", sidebarVisible);
	}

	private WebElement findNombreDelNegocioInput() {
		return wait.until(d -> {
			final List<WebElement> candidates = d.findElements(By.xpath(
					"//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"
							+ " | //input[contains(@placeholder,'Nombre del Negocio')]"
							+ " | //input[contains(@aria-label,'Nombre del Negocio')]"));
			for (WebElement element : candidates) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private WebElement findSectionByHeading(final String heading) {
		final WebElement headingElement = findVisibleByText(heading, DEFAULT_TIMEOUT);
		final List<WebElement> containers = driver.findElements(By.xpath("//*[contains(normalize-space(.),"
				+ xpathLiteral(heading) + ")]/ancestor::section[1] | //*[contains(normalize-space(.)," + xpathLiteral(heading)
				+ ")]/ancestor::div[1]"));

		for (WebElement container : containers) {
			if (container.isDisplayed()) {
				return container;
			}
		}
		return headingElement;
	}

	private void clickByVisibleText(final String... texts) throws Exception {
		Exception lastError = null;
		for (String text : texts) {
			try {
				final WebElement element = findVisibleByText(text, DEFAULT_TIMEOUT);
				wait.until(ExpectedConditions.elementToBeClickable(element));
				try {
					element.click();
				} catch (Exception clickError) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				}
				waitForUiToLoad();
				return;
			} catch (Exception e) {
				lastError = e;
			}
		}
		throw new IllegalStateException("Could not click any of these visible texts: " + Arrays.toString(texts), lastError);
	}

	private void clickIfVisibleByText(final String text) {
		try {
			if (isTextVisible(text, SHORT_TIMEOUT)) {
				clickByVisibleText(text);
			}
		} catch (Exception ignored) {
			// Intentionally ignored: account chooser may be skipped when already authenticated.
		}
	}

	private void assertTextVisible(final String text) {
		findVisibleByText(text, DEFAULT_TIMEOUT);
	}

	private void assertAnyTextVisible(final String... texts) {
		for (String text : texts) {
			if (isTextVisible(text, SHORT_TIMEOUT)) {
				return;
			}
		}
		throw new IllegalStateException("None of the expected texts is visible: " + Arrays.toString(texts));
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			findVisibleByText(text, timeout);
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	private WebElement findVisibleByText(final String text, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		return shortWait.until(d -> {
			final String literal = xpathLiteral(text);
			final List<WebElement> elements = d.findElements(By.xpath(
					"//*[self::a or self::button or self::span or self::div or self::li or self::p or self::h1 or self::h2 or self::h3]"
							+ "[contains(normalize-space(.)," + literal + ")]"));
			for (WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private Optional<String> waitForNewWindow(final Set<String> previousHandles, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			return Optional.ofNullable(shortWait.until(d -> {
				final Set<String> current = new LinkedHashSet<>(d.getWindowHandles());
				current.removeAll(previousHandles);
				return current.isEmpty() ? null : current.iterator().next();
			}));
		} catch (TimeoutException timeoutException) {
			return Optional.empty();
		}
	}

	private void waitUntilAnyTextVisible(final Duration timeout, final String... texts) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		shortWait.until(d -> {
			for (String text : texts) {
				if (isTextVisible(text, Duration.ofSeconds(2))) {
					return true;
				}
			}
			return false;
		});
	}

	private void waitForUiToLoad() {
		wait.until(d -> {
			final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(state);
		});
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private Path captureScreenshot(final String name) throws IOException {
		final Path targetPath = evidenceDir.resolve(sanitizeFileName(name) + ".png");
		final java.io.File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshotFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
		return targetPath;
	}

	private void captureScreenshotQuietly(final String name) {
		try {
			captureScreenshot(name);
		} catch (Exception ignored) {
			// Intentionally ignored to avoid masking prior errors.
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("Test: ").append(TEST_NAME).append(System.lineSeparator());
		report.append("Generated: ").append(LocalDateTime.now()).append(System.lineSeparator());
		report.append(System.lineSeparator());
		report.append("Step results:").append(System.lineSeparator());

		appendStepReport(report, "Login");
		appendStepReport(report, "Mi Negocio menu");
		appendStepReport(report, "Agregar Negocio modal");
		appendStepReport(report, "Administrar Negocios view");
		appendStepReport(report, "Información General");
		appendStepReport(report, "Detalles de la Cuenta");
		appendStepReport(report, "Tus Negocios");
		appendStepReport(report, "Términos y Condiciones");
		appendStepReport(report, "Política de Privacidad");

		if (!capturedUrls.isEmpty()) {
			report.append(System.lineSeparator()).append("Captured URLs:").append(System.lineSeparator());
			for (Map.Entry<String, String> entry : capturedUrls.entrySet()) {
				report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), report.toString());
	}

	private void appendStepReport(final StringBuilder report, final String stepName) {
		final boolean passed = stepStatus.getOrDefault(stepName, false);
		final String note = stepNotes.getOrDefault(stepName, "NOT_RUN");
		report.append("- ").append(stepName).append(": ").append(passed ? "PASS" : "FAIL");
		if (!passed) {
			report.append(" (").append(note).append(")");
		}
		report.append(System.lineSeparator());
	}

	private List<String> normalizedLines(final String text) {
		final List<String> lines = new ArrayList<>();
		for (String line : text.split("\\R")) {
			final String normalized = line == null ? "" : line.trim();
			if (!normalized.isEmpty()) {
				lines.add(normalized);
			}
		}
		return lines;
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part = chars[i] == '\'' ? "\"'\"" : "'" + chars[i] + "'";
			sb.append(part);
			if (i < chars.length - 1) {
				sb.append(",");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	private static String sanitizeFileName(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-_]+", "_").replaceAll("_+", "_");
	}

	private static String firstNonBlank(final String... values) {
		for (String value : values) {
			if (!isBlank(value)) {
				return value.trim();
			}
		}
		return null;
	}

	private static boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
