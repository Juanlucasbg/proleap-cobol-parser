package io.proleap.e2e.saleads;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
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

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class SaleadsMiNegocioWorkflowE2ETest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(20);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> reportNotes = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws Exception {
		final boolean enabled = getBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false);
		Assume.assumeTrue("Set -Dsaleads.e2e.enabled=true (or SALEADS_E2E_ENABLED=true) to run this test.", enabled);

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		evidenceDir = Paths.get("target", "saleads-evidence", DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-"));
		Files.createDirectories(evidenceDir);

		final String startUrl = getStringConfig("saleads.startUrl", "SALEADS_START_URL", "");
		if (!startUrl.isBlank()) {
			driver.navigate().to(startUrl);
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() {
		try {
			printFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones", "Terminos y Condiciones"));
		executeStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad", "Politica de Privacidad"));

		final boolean allPassed = report.values().stream().allMatch(Boolean::booleanValue);
		Assert.assertTrue("One or more Mi Negocio workflow validations failed. See final report above.", allPassed);
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByAnyText("Sign in with Google", "Iniciar sesion con Google", "Continuar con Google");

		switchToNewestWindow();
		waitForUiLoad();

		if (isVisibleText(GOOGLE_ACCOUNT_EMAIL, Duration.ofSeconds(8))) {
			clickByAnyText(GOOGLE_ACCOUNT_EMAIL);
			waitForUiLoad();
		}

		switchToNewestWindow();
		waitForUiLoad();

		assertAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Panel");
		assertSidebarVisible();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		assertSidebarVisible();
		assertAnyVisibleText("Negocio");

		if (!isVisibleText("Mi Negocio", Duration.ofSeconds(2))) {
			clickByAnyText("Negocio");
		}
		ensureMiNegocioExpanded();
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByAnyText("Agregar Negocio");
		assertAnyVisibleText("Crear Nuevo Negocio");
		assertAnyVisibleText("Nombre del Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
		assertAnyVisibleText("Cancelar");
		assertAnyVisibleText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		typeIntoField("Nombre del Negocio", "Negocio Prueba Automatizacion");
		clickByAnyText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioExpanded();
		clickByAnyText("Administrar Negocios");
		waitForUiLoad();

		assertAnyVisibleText("Información General", "Informacion General");
		assertAnyVisibleText("Detalles de la Cuenta");
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Sección Legal", "Seccion Legal");

		// Use a taller viewport to approximate a full-page capture across environments.
		driver.manage().window().setSize(new Dimension(1920, 2800));
		captureScreenshot("04-administrar-negocios-account-page-full");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisibleText("Información General", "Informacion General");
		assertAnyVisibleText("BUSINESS PLAN");
		assertAnyVisibleText("Cambiar Plan");
		assertAnyVisiblePattern("@");
	}

	private void stepValidateDetallesCuenta() {
		assertAnyVisibleText("Cuenta creada");
		assertAnyVisibleText("Estado activo", "Estado Activo");
		assertAnyVisibleText("Idioma seleccionado", "Idioma Seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(final String canonicalLegalName, final String... additionalNames) throws Exception {
		assertAnyVisibleText("Sección Legal", "Seccion Legal");
		final String[] legalNames = mergeLabels(canonicalLegalName, additionalNames);

		final String originalWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
		clickByAnyText(legalNames);

		final String legalWindow = waitForNewWindowIfAny(beforeHandles, Duration.ofSeconds(10));
		if (legalWindow != null) {
			driver.switchTo().window(legalWindow);
			waitForUiLoad();
		}

		assertAnyVisibleText(legalNames);
		assertAnyVisiblePattern("[A-Za-z]{4,}");
		captureScreenshot("05-legal-" + sanitizeFileName(canonicalLegalName));
		reportNotes.put(canonicalLegalName + " URL", driver.getCurrentUrl());

		if (legalWindow != null) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void ensureMiNegocioExpanded() throws Exception {
		if (!isVisibleText("Agregar Negocio", Duration.ofSeconds(2)) || !isVisibleText("Administrar Negocios", Duration.ofSeconds(2))) {
			clickByAnyText("Mi Negocio");
		}
		waitForUiLoad();
	}

	private WebDriver createDriver() throws Exception {
		final boolean headless = getBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true);
		final String remoteUrl = getStringConfig("saleads.remoteUrl", "SALEADS_REMOTE_URL", "");

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (headless) {
			options.addArguments("--headless=new");
		}

		if (!remoteUrl.isBlank()) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}
		return new ChromeDriver(options);
	}

	private void executeStep(final String reportField, final StepAction action) {
		try {
			action.run();
			report.put(reportField, true);
			reportNotes.put(reportField, "PASS");
		} catch (Exception | AssertionError error) {
			report.put(reportField, false);
			reportNotes.put(reportField, "FAIL: " + error.getMessage());
			try {
				captureScreenshot("fail-" + sanitizeFileName(reportField));
			} catch (Exception ignored) {
				// best-effort evidence capture
			}
		}
	}

	private void clickByAnyText(final String... labels) throws Exception {
		Exception last = null;
		for (String label : labels) {
			try {
				final By by = byVisibleText(label);
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(by));
				element.click();
				waitForUiLoad();
				return;
			} catch (Exception e) {
				last = e;
				try {
					final WebElement fallback = findClickableByNormalizedText(label);
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", fallback);
					waitForUiLoad();
					return;
				} catch (Exception ignored) {
					// continue trying other labels
				}
			}
		}
		throw new NoSuchElementException("Could not click any label: " + Arrays.toString(labels) + ". Last error: " + (last == null ? "n/a" : last.getMessage()));
	}

	private void typeIntoField(final String fieldLabel, final String value) {
		final String labelLiteral = toXPathLiteral(fieldLabel);
		final By by = By.xpath("//label[contains(normalize-space(), " + labelLiteral + ")]/following::input[1]");
		WebElement input;
		try {
			input = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
		} catch (TimeoutException firstIgnored) {
			try {
				input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//input[@placeholder=" + labelLiteral + " or @aria-label=" + labelLiteral + "]")));
			} catch (TimeoutException secondIgnored) {
				input = findInputByNormalizedLabel(fieldLabel);
			}
		}
		input.clear();
		input.sendKeys(value);
	}

	private void assertSidebarVisible() {
		final boolean sidebarVisible = isVisible(By.xpath("//aside"))
				|| isVisible(By.xpath("//nav"))
				|| isVisible(By.xpath("//*[contains(@class,'sidebar')]"));
		Assert.assertTrue("Left sidebar navigation is not visible.", sidebarVisible);
	}

	private void assertAnyVisibleText(final String... texts) {
		for (String text : texts) {
			if (isVisibleText(text, Duration.ofSeconds(8))) {
				return;
			}
		}
		Assert.fail("None of the expected texts are visible: " + Arrays.toString(texts));
	}

	private void assertAnyVisiblePattern(final String regex) {
		final String pageText = getPageText();
		final boolean matched = "@".equals(regex) ? pageText.contains("@") : Pattern.compile(regex).matcher(pageText).find();
		Assert.assertTrue("Expected visible content matching pattern: " + regex, matched);
	}

	private boolean isVisible(final By by) {
		try {
			return driver.findElement(by).isDisplayed();
		} catch (Exception ignored) {
			return false;
		}
	}

	private boolean isVisibleText(final String text, final Duration timeout) {
		final String literal = toXPathLiteral(text);
		final By exact = By.xpath("//*[normalize-space()=" + literal + "]");
		final By contains = By.xpath("//*[contains(normalize-space(), " + literal + ")]");
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		try {
			localWait.until(ExpectedConditions.or(
					ExpectedConditions.visibilityOfElementLocated(exact),
					ExpectedConditions.visibilityOfElementLocated(contains)
			));
			return true;
		} catch (Exception ignored) {
			return normalize(getPageText()).contains(normalize(text));
		}
	}

	private WebElement findClickableByNormalizedText(final String label) {
		final String normalized = normalize(label);
		final List<WebElement> elements = driver.findElements(By.xpath("//button|//a|//*[@role='button']|//span|//div"));
		for (WebElement element : elements) {
			try {
				if (!element.isDisplayed()) {
					continue;
				}
				final String text = normalize(element.getText());
				if (!text.isEmpty() && (text.equals(normalized) || text.contains(normalized))) {
					return element;
				}
			} catch (Exception ignored) {
				// continue scanning
			}
		}
		throw new NoSuchElementException("No clickable element found for normalized label: " + label);
	}

	private WebElement findInputByNormalizedLabel(final String label) {
		final String normalizedLabel = normalize(label);
		final List<WebElement> labels = driver.findElements(By.xpath("//label"));
		for (WebElement webLabel : labels) {
			try {
				if (!webLabel.isDisplayed()) {
					continue;
				}
				if (!normalize(webLabel.getText()).contains(normalizedLabel)) {
					continue;
				}
				final String forId = webLabel.getAttribute("for");
				if (forId != null && !forId.isBlank()) {
					final WebElement byId = driver.findElement(By.id(forId));
					if (byId.isDisplayed()) {
						return byId;
					}
				}
			} catch (Exception ignored) {
				// continue scanning labels
			}
		}
		final List<WebElement> inputs = driver.findElements(By.xpath("//input"));
		for (WebElement input : inputs) {
			try {
				if (!input.isDisplayed()) {
					continue;
				}
				final String placeholder = normalize(String.valueOf(input.getAttribute("placeholder")));
				final String ariaLabel = normalize(String.valueOf(input.getAttribute("aria-label")));
				if (placeholder.contains(normalizedLabel) || ariaLabel.contains(normalizedLabel)) {
					return input;
				}
			} catch (Exception ignored) {
				// continue scanning inputs
			}
		}
		throw new NoSuchElementException("Could not locate input field for label: " + label);
	}

	private String waitForNewWindowIfAny(final Set<String> beforeHandles, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		try {
			localWait.until(d -> d.getWindowHandles().size() > beforeHandles.size());
			for (String handle : driver.getWindowHandles()) {
				if (!beforeHandles.contains(handle)) {
					return handle;
				}
			}
			return null;
		} catch (TimeoutException ignored) {
			return null;
		}
	}

	private void switchToNewestWindow() {
		final Set<String> handles = driver.getWindowHandles();
		if (handles.isEmpty()) {
			return;
		}
		final String newest = handles.stream().reduce((first, second) -> second).orElse(driver.getWindowHandle());
		driver.switchTo().window(newest);
	}

	private void waitForUiLoad() {
		try {
			wait.until(d -> ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete"));
		} catch (Exception ignored) {
			// Continue even when the app uses long-lived async rendering.
		}
	}

	private void captureScreenshot(final String name) throws Exception {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-")
				+ "-" + sanitizeFileName(name) + ".png");
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("EVIDENCE_SCREENSHOT: " + target.toAbsolutePath());
	}

	private void printFinalReport() {
		if (report.isEmpty()) {
			return;
		}
		System.out.println("FINAL_REPORT_START");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		if (!reportNotes.isEmpty()) {
			System.out.println("FINAL_REPORT_NOTES");
			reportNotes.forEach((k, v) -> System.out.println(k + " => " + v));
		}
		System.out.println("FINAL_REPORT_END");
	}

	private static By byVisibleText(final String text) {
		final String literal = toXPathLiteral(text);
		return By.xpath("//*[self::button or self::a or self::span or self::div][normalize-space()=" + literal
				+ " or contains(normalize-space(), " + literal + ")]");
	}

	private String[] mergeLabels(final String first, final String... others) {
		final String[] merged = new String[others.length + 1];
		merged[0] = first;
		System.arraycopy(others, 0, merged, 1, others.length);
		return merged;
	}

	private String getPageText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (Exception ignored) {
			return "";
		}
	}

	private static String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	private static String sanitizeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static String normalize(final String value) {
		final String safe = value == null ? "" : value;
		return Normalizer.normalize(safe, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase()
				.trim();
	}

	private static String getStringConfig(final String systemProperty, final String envVar, final String defaultValue) {
		final String fromProperty = System.getProperty(systemProperty);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}
		final String fromEnv = System.getenv(envVar);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv.trim();
		}
		return defaultValue;
	}

	private static boolean getBooleanConfig(final String systemProperty, final String envVar, final boolean defaultValue) {
		final String value = getStringConfig(systemProperty, envVar, String.valueOf(defaultValue));
		return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
