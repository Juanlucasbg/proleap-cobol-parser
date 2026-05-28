package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
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
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration
			.ofSeconds(Long.parseLong(System.getProperty("saleads.timeoutSeconds", "25")));
	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-evidence");
	private static final String EXPECTED_EMAIL = System.getProperty("saleads.expectedEmail",
			"juanlucasbarbiergarzon@gmail.com");
	private static final String STEPS_REPORT_FILE = "final-report.json";
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Informaci\u00F3n General", "Detalles de la Cuenta", "Tus Negocios",
			"T\u00E9rminos y Condiciones", "Pol\u00EDtica de Privacidad");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor js;
	private String appWindowHandle;
	private String termsFinalUrl = "";
	private String privacyFinalUrl = "";

	@Before
	public void setUp() throws IOException {
		Files.createDirectories(EVIDENCE_DIR);
		driver = buildDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		js = (JavascriptExecutor) driver;
		driver.manage().window().maximize();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
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
		runStep("Informaci\u00F3n General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("T\u00E9rminos y Condiciones", () -> termsFinalUrl = stepValidateLegalLink("T\u00E9rminos y Condiciones",
				"05-terminos-y-condiciones"));
		runStep("Pol\u00EDtica de Privacidad",
				() -> privacyFinalUrl = stepValidateLegalLink("Pol\u00EDtica de Privacidad", "06-politica-de-privacidad"));

		writeFinalReport();
		assertAllStepsPass();
	}

	private WebDriver buildDriver() {
		final String debuggerAddress = System.getProperty("saleads.debuggerAddress", "localhost:9222");
		final boolean attachToExisting = Boolean.parseBoolean(System.getProperty("saleads.attachToExisting", "true"));
		final boolean headless = Boolean
				.parseBoolean(System.getProperty("saleads.headless", attachToExisting ? "false" : "true"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-notifications");
		options.addArguments("--start-maximized");
		if (headless) {
			options.addArguments("--headless=new");
			options.addArguments("--window-size=1920,1080");
			options.addArguments("--no-sandbox");
			options.addArguments("--disable-dev-shm-usage");
		}

		if (attachToExisting) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
			try {
				return new ChromeDriver(options);
			} catch (RuntimeException attachError) {
				throw new IllegalStateException(
						"Could not attach to Chrome at " + debuggerAddress + ". Start Chrome with "
								+ "--remote-debugging-port=9222 and open SaleADS login page, then retry.",
						attachError);
			}
		}

		final WebDriver newDriver = new ChromeDriver(options);
		final String configuredUrl = System.getProperty("saleads.url", "");
		if (!configuredUrl.isBlank()) {
			newDriver.get(configuredUrl);
		}
		return newDriver;
	}

	private void stepLoginWithGoogle() throws IOException {
		clickByVisibleTextVariant("Sign in with Google", "Iniciar sesi\u00F3n con Google", "Ingresar con Google",
				"Continuar con Google", "Google");
		waitForUiToLoad();
		maybeSelectGoogleAccount();
		waitForUiToLoad();

		waitForAnyVisible(DEFAULT_TIMEOUT, By.xpath("//aside"), By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar') or contains(@class,'Sidebar')]"));
		waitForAnyVisible(DEFAULT_TIMEOUT, byContainsText("Negocio"), byContainsText("Mi Negocio"));
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		waitForAnyVisible(DEFAULT_TIMEOUT, By.xpath("//aside"), By.xpath("//nav"));
		clickByVisibleTextVariant("Negocio");
		waitForUiToLoad();
		clickByVisibleTextVariant("Mi Negocio");
		waitForUiToLoad();

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleTextVariant("Agregar Negocio");
		waitForUiToLoad();

		waitForVisibleText("Crear Nuevo Negocio");
		waitForAnyVisible(DEFAULT_TIMEOUT, byContainsText("Nombre del Negocio"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[@name='nombreNegocio' or @name='businessName']"));
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		WebElement nameField = findAnyVisible(By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[@name='nombreNegocio' or @name='businessName']"), By.xpath("//input"));
		safeClick(nameField);
		nameField.clear();
		nameField.sendKeys("Negocio Prueba Automatizacion");
		waitForUiToLoad();
		clickByVisibleTextVariant("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleTextVariant("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleTextVariant("Administrar Negocios");
		waitForUiToLoad();

		waitForVisibleText("Informaci\u00F3n General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Secci\u00F3n Legal");
		captureScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		WebElement generalSection = waitForAnyVisible(DEFAULT_TIMEOUT,
				By.xpath("//*[self::section or self::div][.//*[contains(.,'Informaci\u00F3n General')]]"),
				By.xpath("//*[self::section or self::div][.//*[contains(.,'Informacion General')]]"));
		String sectionText = normalizeWhitespace(generalSection.getText());

		assertTrue("User name is not visible in Informacion General.",
				containsLikelyUserName(sectionText) || sectionText.toLowerCase(Locale.ROOT).contains("nombre"));
		assertTrue("User email is not visible in Informacion General.",
				sectionText.contains(EXPECTED_EMAIL) || containsAnyEmail(sectionText));
		waitForVisibleText("BUSINESS PLAN");
		waitForVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		WebElement businessesSection = waitForAnyVisible(DEFAULT_TIMEOUT,
				By.xpath("//*[self::section or self::div][.//*[contains(.,'Tus Negocios')]]"));
		assertTrue("Business list is not visible in Tus Negocios.", businessesSection.isDisplayed());
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
	}

	private String stepValidateLegalLink(String linkText, String screenshotName) throws IOException {
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String originHandle = driver.getWindowHandle();

		clickByVisibleTextVariant(linkText, linkText.replace("\u00E9", "e").replace("\u00ED", "i"));
		waitForUiToLoad();

		String activeHandle = originHandle;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(d -> d != null && d.getWindowHandles().size() > beforeHandles.size());
			Set<String> afterHandles = driver.getWindowHandles();
			for (String handle : afterHandles) {
				if (!beforeHandles.contains(handle)) {
					driver.switchTo().window(handle);
					activeHandle = handle;
					waitForUiToLoad();
					break;
				}
			}
		} catch (TimeoutException ignored) {
			// Same-tab navigation is valid for legal pages.
		}

		waitForAnyVisible(DEFAULT_TIMEOUT, byContainsText(linkText), byContainsText(linkText.replace("\u00E9", "e")
				.replace("\u00ED", "i")), By.xpath("//h1"), By.xpath("//main"), By.xpath("//article"));

		String bodyText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		assertTrue("Legal content text is not visible for " + linkText + ".", bodyText.length() > 120);
		captureScreenshot(screenshotName);

		String finalUrl = driver.getCurrentUrl();
		returnToApplicationTab(originHandle, activeHandle);
		return finalUrl;
	}

	private void returnToApplicationTab(String originHandle, String activeHandle) {
		try {
			if (!originHandle.equals(activeHandle)) {
				driver.close();
				driver.switchTo().window(originHandle);
			} else {
				driver.navigate().back();
			}
			waitForUiToLoad();
			if (driver.getWindowHandles().contains(appWindowHandle)) {
				driver.switchTo().window(appWindowHandle);
			}
		} catch (NoSuchWindowException ignored) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private void maybeSelectGoogleAccount() {
		List<By> accountLocators = Arrays.asList(byContainsText(EXPECTED_EMAIL), byContainsText("Choose an account"),
				byContainsText("Elegir una cuenta"));
		Set<String> handles = driver.getWindowHandles();

		for (String handle : handles) {
			driver.switchTo().window(handle);
			for (By locator : accountLocators) {
				WebElement account = findAnyVisible(locator);
				if (account != null) {
					safeClick(account);
					waitForUiToLoad();
					return;
				}
			}
		}

		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private void runStep(String stepName, StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, new StepResult("PASS", "Validation completed."));
		} catch (Throwable ex) {
			try {
				captureScreenshot(slugify(stepName) + "-failed");
			} catch (IOException ignored) {
				// Continue reporting even if screenshot capture fails.
			}
			stepResults.put(stepName, new StepResult("FAIL", normalizeWhitespace(ex.getMessage())));
		}
	}

	private void assertAllStepsPass() {
		List<String> failed = stepResults.entrySet().stream().filter(entry -> !"PASS".equals(entry.getValue().status))
				.map(Map.Entry::getKey).collect(Collectors.toList());
		assertTrue("Workflow completed with failed validations: " + failed, failed.isEmpty());
	}

	private void writeFinalReport() throws IOException {
		for (String field : REPORT_FIELDS) {
			stepResults.putIfAbsent(field, new StepResult("FAIL", "Step was not executed."));
		}

		StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"generatedAt\": \"").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("\",\n");
		json.append("  \"termsUrl\": \"").append(escapeJson(termsFinalUrl)).append("\",\n");
		json.append("  \"privacyUrl\": \"").append(escapeJson(privacyFinalUrl)).append("\",\n");
		json.append("  \"results\": {\n");

		for (int i = 0; i < REPORT_FIELDS.size(); i++) {
			String field = REPORT_FIELDS.get(i);
			StepResult result = stepResults.get(field);
			json.append("    \"").append(escapeJson(field)).append("\": {\n");
			json.append("      \"status\": \"").append(result.status).append("\",\n");
			json.append("      \"details\": \"").append(escapeJson(result.details)).append("\"\n");
			json.append("    }");
			if (i < REPORT_FIELDS.size() - 1) {
				json.append(",");
			}
			json.append("\n");
		}

		json.append("  }\n");
		json.append("}\n");
		Files.writeString(EVIDENCE_DIR.resolve(STEPS_REPORT_FILE), json.toString());
	}

	private void clickByVisibleTextVariant(String... textVariants) {
		List<By> locators = new ArrayList<>();
		for (String variant : textVariants) {
			locators.add(By.xpath(
					"//*[self::button or self::a or @role='button' or self::div][contains(normalize-space(.),"
							+ escapeXPathValue(variant) + ")]"));
			locators.add(byContainsText(variant));
		}

		WebElement element = waitForAnyVisible(DEFAULT_TIMEOUT, locators.toArray(new By[0]));
		safeClick(element);
		waitForUiToLoad();
	}

	private void safeClick(WebElement element) {
		wait.until(driver -> element.isDisplayed() && element.isEnabled());
		js.executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		element.click();
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete"
				.equals(((JavascriptExecutor) driver).executeScript("return document.readyState").toString()));
	}

	private WebElement waitForVisibleText(String text) {
		return waitForAnyVisible(DEFAULT_TIMEOUT, byContainsText(text), byContainsText(text.replace("\u00E9", "e")
				.replace("\u00F3", "o").replace("\u00ED", "i")));
	}

	private boolean isTextVisible(String text) {
		return findAnyVisible(byContainsText(text)) != null;
	}

	private WebElement waitForAnyVisible(Duration timeout, By... locators) {
		long endAt = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < endAt) {
			WebElement element = findAnyVisible(locators);
			if (element != null) {
				return element;
			}
			sleep(250);
		}
		throw new TimeoutException("Could not find a visible element for locators: " + Arrays.toString(locators));
	}

	private WebElement findAnyVisible(By... locators) {
		for (By locator : locators) {
			List<WebElement> elements = driver.findElements(locator);
			for (WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		return null;
	}

	private void captureScreenshot(String screenshotName) throws IOException {
		byte[] content = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(EVIDENCE_DIR.resolve(screenshotName + ".png"), content);
	}

	private By byContainsText(String text) {
		return By.xpath("//*[contains(normalize-space(.)," + escapeXPathValue(text) + ")]");
	}

	private String escapeXPathValue(String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		String[] parts = value.split("'");
		StringBuilder concat = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				concat.append(", \"'\", ");
			}
			concat.append("'").append(parts[i]).append("'");
		}
		concat.append(")");
		return concat.toString();
	}

	private boolean containsAnyEmail(String text) {
		return Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(text).find();
	}

	private boolean containsLikelyUserName(String text) {
		return Pattern.compile("(?m)^[A-Za-z\\p{L}]{2,}(\\s+[A-Za-z\\p{L}]{2,}){1,}$").matcher(text).find();
	}

	private String normalizeWhitespace(String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("\\s+", " ").trim();
	}

	private String slugify(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String escapeJson(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final String status;
		private final String details;

		private StepResult(String status, String details) {
			this.status = status;
			this.details = details;
		}
	}
}
