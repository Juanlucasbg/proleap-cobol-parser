package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioWorkflowTest {

	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String NORMALIZED_TEXT_EXPR = "translate(normalize-space(.), "
			+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑabcdefghijklmnopqrstuvwxyzáéíóúüñ', "
			+ "'abcdefghijklmnopqrstuvwxyzaeiouunabcdefghijklmnopqrstuvwxyzaeiouun')";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean
				.parseBoolean(readSetting("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Enable this test with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true.", enabled);

		final String startUrl = readSetting("saleads.start.url", "SALEADS_START_URL", "");
		Assume.assumeTrue("Provide the login page with -Dsaleads.start.url=<url> or SALEADS_START_URL.",
				!startUrl.isBlank());

		final String browser = readSetting("saleads.browser", "SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(readSetting("saleads.headless", "SALEADS_HEADLESS", "true"));
		final long timeoutSeconds = Long
				.parseLong(readSetting("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", "30"));

		evidenceDir = createEvidenceDir();
		driver = createWebDriver(browser, headless);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		driver.manage().window().setSize(new Dimension(1920, 1080));
		driver.get(startUrl);
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
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalDocument("Términos y Condiciones",
				new String[] { "Términos y Condiciones", "Terminos y Condiciones" }, "08-terminos"));
		runStep("Política de Privacidad", () -> stepValidateLegalDocument("Política de Privacidad",
				new String[] { "Política de Privacidad", "Politica de Privacidad" }, "09-privacidad"));

		writeFinalReport();

		if (!failures.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failed:\n - " + String.join("\n - ", failures));
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		if (!isAnyTextVisible("Mi Negocio", "Negocio")) {
			clickByText("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
					"Continuar con Google", "Google");
		}

		clickIfVisible("juanlucasbarbiergarzon@gmail.com");
		waitForAnyVisibleText("Negocio", "Mi Negocio");

		assertVisible(By.xpath("//aside | //nav"), "Sidebar is not visible after login.");
		assertTrue("Main application navigation is not visible after login.", isAnyTextVisible("Negocio", "Mi Negocio"));
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		if (!isAnyTextVisible("Mi Negocio")) {
			clickIfVisible("Negocio");
		}
		clickByText("Mi Negocio");
		waitForAnyVisibleText("Agregar Negocio", "Administrar Negocios");

		assertTrue("'Agregar Negocio' should be visible.", isAnyTextVisible("Agregar Negocio"));
		assertTrue("'Administrar Negocios' should be visible.", isAnyTextVisible("Administrar Negocios"));
		takeScreenshot("02-mi-negocio-expanded-menu");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByText("Agregar Negocio");
		waitForAnyVisibleText("Crear Nuevo Negocio");

		assertTrue("Modal title 'Crear Nuevo Negocio' is missing.", isAnyTextVisible("Crear Nuevo Negocio"));
		assertVisible(By.xpath(
				"//input[@name='businessName' or @name='nombreNegocio' or contains(@placeholder,'Nombre') or contains(@aria-label,'Nombre')]"),
				"'Nombre del Negocio' input is missing.");
		assertTrue("'Tienes 2 de 3 negocios' text is missing.", isAnyTextVisible("Tienes 2 de 3 negocios", "2 de 3"));
		assertTrue("'Cancelar' button is missing.", isAnyTextVisible("Cancelar"));
		assertTrue("'Crear Negocio' button is missing.", isAnyTextVisible("Crear Negocio"));

		WebElement input = firstVisible(By.xpath(
				"//input[@name='businessName' or @name='nombreNegocio' or contains(@placeholder,'Nombre') or contains(@aria-label,'Nombre')]"));
		input.click();
		input.sendKeys(Keys.chord(Keys.CONTROL, "a"), "Negocio Prueba Automatizacion");
		takeScreenshot("03-agregar-negocio-modal");
		clickByText("Cancelar");
		waitForUiToLoad();
		assertFalse("Modal should be closed after clicking Cancelar.", isAnyTextVisible("Crear Nuevo Negocio"));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickByText("Mi Negocio");
		}
		clickByText("Administrar Negocios");
		waitForAnyVisibleText("Información General", "Informacion General");

		assertTrue("'Información General' section is missing.", isAnyTextVisible("Información General", "Informacion General"));
		assertTrue("'Detalles de la Cuenta' section is missing.",
				isAnyTextVisible("Detalles de la Cuenta", "Detalles de la Cuenta"));
		assertTrue("'Tus Negocios' section is missing.", isAnyTextVisible("Tus Negocios"));
		assertTrue("'Sección Legal' section is missing.", isAnyTextVisible("Sección Legal", "Seccion Legal"));
		takeScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		assertTrue("Text 'BUSINESS PLAN' is missing.", isAnyTextVisible("BUSINESS PLAN"));
		assertTrue("Button 'Cambiar Plan' is missing.", isAnyTextVisible("Cambiar Plan"));

		final String pageText = getPageText();
		assertTrue("No email was found in account information.", EMAIL_PATTERN.matcher(pageText).find());

		List<String> lines = Arrays.stream(pageText.split("\\R")).map(String::trim).filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
		boolean hasPotentialUserName = lines.stream().anyMatch(line -> {
			String normalized = normalize(line);
			return !line.contains("@") && normalized.length() >= 5 && !normalized.contains("informacion general")
					&& !normalized.contains("business plan") && !normalized.contains("cambiar plan")
					&& !normalized.contains("detalles de la cuenta") && !normalized.contains("tus negocios");
		});
		assertTrue("User name was not detected as visible text.", hasPotentialUserName);
	}

	private void stepValidateDetallesCuenta() {
		assertTrue("'Cuenta creada' is missing.", isAnyTextVisible("Cuenta creada"));
		assertTrue("'Estado activo' is missing.", isAnyTextVisible("Estado activo"));
		assertTrue("'Idioma seleccionado' is missing.", isAnyTextVisible("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		assertTrue("'Tus Negocios' section is missing.", isAnyTextVisible("Tus Negocios"));
		assertTrue("'Agregar Negocio' button is missing in 'Tus Negocios'.", isAnyTextVisible("Agregar Negocio"));
		assertTrue("'Tienes 2 de 3 negocios' is missing in 'Tus Negocios'.", isAnyTextVisible("Tienes 2 de 3 negocios", "2 de 3"));

		WebElement section = firstVisible(By.xpath("//*[contains(" + NORMALIZED_TEXT_EXPR + ", 'tus negocios')]"));
		WebElement container = section.findElement(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
		List<WebElement> candidateBusinessItems = container
				.findElements(By.xpath(".//li | .//tr | .//article | .//div[contains(@class,'card')]"));
		assertFalse("Business list appears empty.", candidateBusinessItems.isEmpty());
	}

	private void stepValidateLegalDocument(final String reportName, final String[] linkTexts, final String screenshotName)
			throws IOException {
		String appHandle = driver.getWindowHandle();
		String appUrl = driver.getCurrentUrl();
		Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickByText(linkTexts);

		Set<String> currentHandles = wait.until(drv -> {
			Set<String> handles = drv.getWindowHandles();
			if (handles.size() > beforeHandles.size()) {
				return handles;
			}
			String currentUrl = drv.getCurrentUrl();
			return !currentUrl.equals(appUrl) || isAnyTextVisible(linkTexts) ? handles : null;
		});

		boolean openedNewTab = currentHandles.size() > beforeHandles.size();
		if (openedNewTab) {
			for (String handle : currentHandles) {
				if (!beforeHandles.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		waitForAnyVisibleText(linkTexts);
		assertTrue(reportName + " heading is missing.", isAnyTextVisible(linkTexts));
		assertTrue(reportName + " did not navigate to a legal destination.",
				openedNewTab || !driver.getCurrentUrl().equals(appUrl));

		String bodyText = getPageText();
		assertTrue(reportName + " legal content appears empty.", bodyText != null && bodyText.trim().length() > 150);

		legalUrls.put(reportName, driver.getCurrentUrl());
		takeScreenshot(screenshotName);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		if (openedNewTab) {
			assertTrue("Failed to return to the application tab.", driver.getCurrentUrl().contains(normalizeUrlHost(appUrl)));
		}
	}

	private void runStep(final String reportName, final StepAction action) {
		try {
			action.execute();
			report.put(reportName, Boolean.TRUE);
		} catch (Throwable throwable) {
			report.put(reportName, Boolean.FALSE);
			failures.add(reportName + ": " + throwable.getMessage());
			try {
				takeScreenshot("failure-" + safeName(reportName));
			} catch (IOException ioException) {
				failures.add(reportName + ": failed to capture screenshot after error: " + ioException.getMessage());
			}
		}
	}

	private WebDriver createWebDriver(final String browser, final boolean headless) {
		if ("firefox".equals(browser)) {
			FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			return new FirefoxDriver(options);
		}

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}
		return new ChromeDriver(options);
	}

	private void clickByText(final String... candidateTexts) {
		By locator = byClickableText(candidateTexts);
		WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
		scrollIntoView(element);
		element.click();
		waitForUiToLoad();
	}

	private void clickIfVisible(final String... candidateTexts) {
		try {
			WebElement element = shortWait(2).until(ExpectedConditions.visibilityOfElementLocated(byClickableText(candidateTexts)));
			scrollIntoView(element);
			element.click();
			waitForUiToLoad();
		} catch (TimeoutException ignored) {
			// Optional click.
		}
	}

	private WebElement firstVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void assertVisible(final By locator, final String message) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (TimeoutException timeoutException) {
			fail(message);
		}
	}

	private void waitForAnyVisibleText(final String... candidateTexts) {
		wait.until(drv -> isAnyTextVisible(candidateTexts));
	}

	private boolean isAnyTextVisible(final String... candidateTexts) {
		try {
			return driver.findElements(byVisibleText(candidateTexts)).stream().anyMatch(WebElement::isDisplayed);
		} catch (Exception ignored) {
			return false;
		}
	}

	private By byVisibleText(final String... candidateTexts) {
		String predicate = Arrays.stream(candidateTexts).map(this::containsTextExpr).collect(Collectors.joining(" or "));
		return By.xpath("//*[" + predicate + "]");
	}

	private By byClickableText(final String... candidateTexts) {
		String predicate = Arrays.stream(candidateTexts).map(this::containsTextExpr).collect(Collectors.joining(" or "));
		return By.xpath("(//button[" + predicate + "]"
				+ " | //a[" + predicate + "]"
				+ " | //*[@role='button' and (" + predicate + ")]"
				+ " | //li[" + predicate + "]"
				+ " | //span[" + predicate + "]"
				+ " | //div[" + predicate + "])[1]");
	}

	private String containsTextExpr(final String text) {
		return "contains(" + NORMALIZED_TEXT_EXPR + ", " + asXpathLiteral(normalize(text)) + ")";
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		new Actions(driver).moveToElement(element).perform();
	}

	private void waitForUiToLoad() {
		wait.until(drv -> {
			Object state = ((JavascriptExecutor) drv).executeScript("return document.readyState");
			return "complete".equals(state) || "interactive".equals(state);
		});

		try {
			Thread.sleep(400);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		Path screenshotPath = evidenceDir.resolve(checkpointName + ".png");
		byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshotPath, screenshot);
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Test Report").append(System.lineSeparator());
		builder.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator()).append(System.lineSeparator());

		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			builder.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			builder.append(System.lineSeparator()).append("Legal URLs:").append(System.lineSeparator());
			for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		if (!failures.isEmpty()) {
			builder.append(System.lineSeparator()).append("Failures:").append(System.lineSeparator());
			for (String failureLine : failures) {
				builder.append("- ").append(failureLine).append(System.lineSeparator());
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), builder.toString());
	}

	private Path createEvidenceDir() throws IOException {
		Path root = Paths.get("target", "saleads-evidence");
		Path runDir = root.resolve(LocalDateTime.now().format(RUN_ID_FORMATTER));
		return Files.createDirectories(runDir);
	}

	private String readSetting(final String propertyName, final String environmentName, final String defaultValue) {
		String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		String environmentValue = System.getenv(environmentName);
		if (environmentValue != null && !environmentValue.isBlank()) {
			return environmentValue.trim();
		}

		return defaultValue;
	}

	private String normalize(final String value) {
		String noDiacritics = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return noDiacritics.toLowerCase(Locale.ROOT).trim();
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		String[] parts = value.split("'");
		StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private String safeName(final String text) {
		return normalize(text).replaceAll("[^a-z0-9]+", "-");
	}

	private String getPageText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (Exception exception) {
			return "";
		}
	}

	private String normalizeUrlHost(final String url) {
		return url.replaceFirst("^https?://", "").split("/")[0];
	}

	private WebDriverWait shortWait(final long seconds) {
		return new WebDriverWait(driver, Duration.ofSeconds(seconds));
	}

	@FunctionalInterface
	private interface StepAction {
		void execute() throws Exception;
	}
}
