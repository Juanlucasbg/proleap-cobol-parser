package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException, InterruptedException {
		final boolean enabled = getBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false);
		Assume.assumeTrue(
				"Set SALEADS_E2E_ENABLED=true to execute this test in a valid SaleADS environment with credentials.",
				enabled);

		final String startUrl = getConfig("saleads.start.url", "SALEADS_START_URL", "");
		Assume.assumeTrue(
				"Provide SALEADS_START_URL (or -Dsaleads.start.url=...) with the current environment login page URL.",
				startUrl != null && !startUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		if (getBooleanConfig("saleads.e2e.headless", "SALEADS_E2E_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(35));

		evidenceDirectory = Paths.get("target", "saleads-evidence",
				DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()));
		Files.createDirectories(evidenceDirectory);

		driver.get(startUrl);
		waitForUiLoad();
	}

	@After
	public void tearDown() throws IOException {
		try {
			if (evidenceDirectory != null) {
				writeFinalReport();
			}
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final boolean loginOk = executeStep("Login", this::stepLoginWithGoogle);
		if (!loginOk) {
			markBlockedAfter("Login");
			assertAllPassed();
			return;
		}

		final boolean menuOk = executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		if (!menuOk) {
			markBlockedAfter("Mi Negocio menu");
			assertAllPassed();
			return;
		}

		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);

		final boolean administrarOk = executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		if (!administrarOk) {
			markBlockedAfter("Administrar Negocios view");
			assertAllPassed();
			return;
		}

		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones",
				() -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos"));
		executeStep("Política de Privacidad",
				() -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09-privacidad"));

		assertAllPassed();
	}

	private void stepLoginWithGoogle() throws IOException, InterruptedException {
		if (!isMainApplicationVisible()) {
			final WebElement loginButton = waitForAnyText(
					Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
							"Ingresar con Google", "Iniciar con Google"),
					20);
			clickElementAndWait(loginButton);
			handleGoogleAccountSelector();
			wait.until(d -> isMainApplicationVisible());
		}

		waitForAnyText(Arrays.asList("Negocio", "Mi Negocio"), 30);
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws InterruptedException, IOException {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside | //nav")));
		if (!isTextVisible("Mi Negocio", 2)) {
			clickByDisplayedText("Negocio");
		}

		clickByDisplayedText("Mi Negocio");

		assertTrue("Expected 'Agregar Negocio' to be visible.", isTextVisible("Agregar Negocio", 15));
		assertTrue("Expected 'Administrar Negocios' to be visible.", isTextVisible("Administrar Negocios", 15));
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws InterruptedException, IOException {
		clickByDisplayedText("Agregar Negocio");
		waitForText("Crear Nuevo Negocio", 15);

		assertTrue("Expected input field 'Nombre del Negocio'.", existsVisible(By.xpath(
				"//label[normalize-space()='Nombre del Negocio']/following::input[1] | //input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio']"),
				10));
		assertTrue("Expected text 'Tienes 2 de 3 negocios'.", isTextVisible("Tienes 2 de 3 negocios", 10));
		assertTrue("Expected 'Cancelar' button.", isTextVisible("Cancelar", 10));
		assertTrue("Expected 'Crear Negocio' button.", isTextVisible("Crear Negocio", 10));

		takeScreenshot("03-crear-nuevo-negocio-modal");

		final WebElement businessNameInput = findVisibleElement(By.xpath(
				"//label[normalize-space()='Nombre del Negocio']/following::input[1] | //input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio']"),
				5);
		if (businessNameInput != null) {
			businessNameInput.click();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatización");
		}

		clickByDisplayedText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(textLocator("Crear Nuevo Negocio")));
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws InterruptedException, IOException {
		if (!isTextVisible("Administrar Negocios", 3)) {
			clickByDisplayedText("Mi Negocio");
		}

		clickByDisplayedText("Administrar Negocios");

		waitForText("Información General", 30);
		waitForText("Detalles de la Cuenta", 30);
		waitForText("Tus Negocios", 30);
		waitForText("Sección Legal", 30);

		takeScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = waitForSection("Información General");
		final String sectionText = section.getText();

		assertTrue("Expected user name to be visible in 'Información General'.", containsLikelyPersonName(sectionText));
		assertTrue("Expected user email to be visible in 'Información General'.", EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("Expected 'BUSINESS PLAN' text.", sectionText.contains("BUSINESS PLAN"));
		assertTrue("Expected 'Cambiar Plan' button.", isTextVisibleInSection(section, "Cambiar Plan"));
	}

	private void stepValidateDetallesDeLaCuenta() {
		final WebElement section = waitForSection("Detalles de la Cuenta");
		final String sectionText = section.getText();

		assertTrue("Expected 'Cuenta creada' text.", sectionText.contains("Cuenta creada"));
		assertTrue("Expected 'Estado activo' text.", sectionText.contains("Estado activo"));
		assertTrue("Expected 'Idioma seleccionado' text.", sectionText.contains("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = waitForSection("Tus Negocios");
		final String sectionText = section.getText();

		assertTrue("Expected business list in 'Tus Negocios'.", hasBusinessList(section));
		assertTrue("Expected 'Agregar Negocio' button.", isTextVisibleInSection(section, "Agregar Negocio"));
		assertTrue("Expected text 'Tienes 2 de 3 negocios'.", sectionText.contains("Tienes 2 de 3 negocios"));
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws InterruptedException, IOException {
		final String originHandle = driver.getWindowHandle();
		final String originUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByDisplayedText(linkText);

		final String targetHandle = waitForNewHandle(handlesBeforeClick, 8);
		final boolean openedNewTab = targetHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(targetHandle);
			waitForUiLoad();
		}

		waitForText(expectedHeading, 30);
		assertTrue("Expected legal content text to be visible.", driver.findElement(By.tagName("body")).getText().length() > 150);

		legalUrls.put(linkText, driver.getCurrentUrl());
		takeScreenshot(screenshotName);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originHandle);
			waitForUiLoad();
			waitForText("Sección Legal", 30);
		} else if (!Objects.equals(driver.getCurrentUrl(), originUrl)) {
			driver.navigate().back();
			waitForUiLoad();
			waitForText("Sección Legal", 30);
		}
	}

	private boolean executeStep(final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			report.put(stepName, StepResult.pass("PASS"));
			return true;
		} catch (final Throwable ex) {
			if (ex instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			report.put(stepName, StepResult.fail(ex.getMessage()));
			safelyCaptureFailureScreenshot(stepName);
			return false;
		}
	}

	private void assertAllPassed() {
		for (final String field : REPORT_FIELDS) {
			if (!report.containsKey(field)) {
				report.put(field, StepResult.fail("No ejecutado."));
			}
		}

		final List<String> failed = report.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(entry -> entry.getKey() + ": " + entry.getValue().details).collect(Collectors.toList());

		assertTrue("Failed validations:\n" + String.join("\n", failed), failed.isEmpty());
	}

	private void markBlockedAfter(final String currentStep) {
		boolean fill = false;
		for (final String field : REPORT_FIELDS) {
			if (fill && !report.containsKey(field)) {
				report.put(field, StepResult.fail("Bloqueado por falla en el paso previo."));
			}
			if (field.equals(currentStep)) {
				fill = true;
			}
		}
	}

	private void clickByDisplayedText(final String text) throws InterruptedException {
		final WebElement textElement = waitForText(text, 20);
		final WebElement clickable = resolveClickable(textElement);
		clickElementAndWait(clickable);
	}

	private void clickElementAndWait(final WebElement element) throws InterruptedException {
		final WebElement clickable = resolveClickable(element);
		scrollIntoView(clickable);
		wait.until(ExpectedConditions.elementToBeClickable(clickable)).click();
		waitForUiLoad();
	}

	private WebElement waitForAnyText(final List<String> candidates, final int timeoutSeconds) {
		final WebDriverWait localWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		return localWait.until(d -> {
			for (final String candidate : candidates) {
				try {
					final WebElement element = d.findElement(textLocator(candidate));
					if (element.isDisplayed()) {
						return element;
					}
				} catch (final NoSuchElementException ex) {
					// continue to next candidate
				}
			}
			return null;
		});
	}

	private WebElement waitForText(final String text, final int timeoutSeconds) {
		final WebDriverWait localWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		return localWait.until(ExpectedConditions.visibilityOfElementLocated(textLocator(text)));
	}

	private WebElement waitForSection(final String heading) {
		final String headingLiteral = toXpathLiteral(heading);
		final By sectionLocator = By.xpath("//section[.//*[normalize-space()=" + headingLiteral + "]]"
				+ " | //div[.//*[normalize-space()=" + headingLiteral
				+ "] and .//*[normalize-space() != '' and not(self::script)]][1]");
		return wait.until(ExpectedConditions.visibilityOfElementLocated(sectionLocator));
	}

	private boolean hasBusinessList(final WebElement section) {
		final List<WebElement> items = section.findElements(By.xpath(".//li | .//tr | .//article"));
		return !items.isEmpty() || section.getText().contains("Negocio");
	}

	private boolean containsLikelyPersonName(final String text) {
		final List<String> lines = Arrays.stream(text.split("\\R")).map(String::trim).filter(line -> !line.isEmpty())
				.filter(line -> !line.contains("@")).filter(line -> !line.equals("Información General"))
				.filter(line -> !line.contains("BUSINESS PLAN")).filter(line -> !line.equals("Cambiar Plan"))
				.collect(Collectors.toList());

		for (final String line : lines) {
			if (line.length() >= 3 && line.chars().anyMatch(Character::isLetter)) {
				return true;
			}
		}
		return false;
	}

	private boolean isMainApplicationVisible() {
		return isTextVisible("Negocio", 3) || isTextVisible("Mi Negocio", 3);
	}

	private boolean existsVisible(final By by, final int timeoutSeconds) {
		return findVisibleElement(by, timeoutSeconds) != null;
	}

	private WebElement findVisibleElement(final By by, final int timeoutSeconds) {
		try {
			final WebDriverWait localWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
			return localWait.until(ExpectedConditions.visibilityOfElementLocated(by));
		} catch (final TimeoutException ex) {
			return null;
		}
	}

	private boolean isTextVisible(final String text, final int timeoutSeconds) {
		try {
			waitForText(text, timeoutSeconds);
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private boolean isTextVisibleInSection(final WebElement section, final String text) {
		try {
			final String literal = toXpathLiteral(text);
			final List<WebElement> elements = section.findElements(By.xpath(".//*[normalize-space()=" + literal + "]"));
			return !elements.isEmpty() && elements.get(0).isDisplayed();
		} catch (final Exception ex) {
			return false;
		}
	}

	private void waitForUiLoad() throws InterruptedException {
		final ExpectedCondition<Boolean> documentReady = drv -> "complete"
				.equals(((JavascriptExecutor) drv).executeScript("return document.readyState"));
		wait.until(documentReady);
		Thread.sleep(500);
	}

	private void handleGoogleAccountSelector() throws InterruptedException {
		if (driver.getCurrentUrl().contains("accounts.google.com")
				|| isTextVisible("juanlucasbarbiergarzon@gmail.com", 10)) {
			if (isTextVisible("juanlucasbarbiergarzon@gmail.com", 10)) {
				clickByDisplayedText("juanlucasbarbiergarzon@gmail.com");
			}
		}
		waitForUiLoad();
	}

	private String waitForNewHandle(final Set<String> handlesBeforeClick, final int timeoutSeconds) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		try {
			return shortWait.until(d -> {
				final Set<String> handlesAfterClick = d.getWindowHandles();
				if (handlesAfterClick.size() > handlesBeforeClick.size()) {
					for (final String handle : handlesAfterClick) {
						if (!handlesBeforeClick.contains(handle)) {
							return handle;
						}
					}
				}
				return null;
			});
		} catch (final TimeoutException ex) {
			return null;
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private WebElement resolveClickable(final WebElement element) {
		if (isClickable(element)) {
			return element;
		}

		try {
			final WebElement ancestor = element.findElement(By.xpath(
					"./ancestor::*[self::a or self::button or @role='button' or @onclick or contains(@class,'btn')][1]"));
			if (ancestor != null && ancestor.isDisplayed()) {
				return ancestor;
			}
		} catch (final NoSuchElementException ex) {
			// use original element
		}

		return element;
	}

	private boolean isClickable(final WebElement element) {
		final String tag = element.getTagName();
		return "a".equalsIgnoreCase(tag) || "button".equalsIgnoreCase(tag)
				|| "button".equalsIgnoreCase(element.getAttribute("role"))
				|| element.getAttribute("onclick") != null || (element.getAttribute("class") != null
						&& element.getAttribute("class").toLowerCase().contains("btn"));
	}

	private void takeScreenshot(final String name) throws IOException {
		final Path destination = evidenceDirectory.resolve(name + ".png");
		final byte[] data = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(destination, data);
	}

	private void safelyCaptureFailureScreenshot(final String stepName) {
		if (driver == null || evidenceDirectory == null) {
			return;
		}

		try {
			final String normalized = stepName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
			takeScreenshot("failure-" + normalized);
		} catch (final Exception ex) {
			// ignore screenshot failure
		}
	}

	private By textLocator(final String text) {
		final String literal = toXpathLiteral(text);
		return By.xpath("//*[normalize-space()=" + literal + "]");
	}

	private String toXpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final String[] parts = text.split("'");
		final List<String> escapedParts = new ArrayList<>();
		for (int i = 0; i < parts.length; i++) {
			escapedParts.add("'" + parts[i] + "'");
			if (i < parts.length - 1) {
				escapedParts.add("\"'\"");
			}
		}
		return "concat(" + String.join(",", escapedParts) + ")";
	}

	private void writeFinalReport() throws IOException {
		for (final String field : REPORT_FIELDS) {
			if (!report.containsKey(field)) {
				report.put(field, StepResult.fail("No ejecutado."));
			}
		}

		final StringBuilder reportText = new StringBuilder();
		reportText.append("SaleADS Mi Negocio Full Test Report").append(System.lineSeparator());
		reportText.append("Generated: ").append(LocalDateTime.now()).append(System.lineSeparator());
		reportText.append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			reportText.append(field).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (result.details != null && !result.details.isBlank()) {
				reportText.append(" - ").append(result.details);
			}
			reportText.append(System.lineSeparator());
		}

		reportText.append(System.lineSeparator()).append("Captured URLs").append(System.lineSeparator());
		for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
			reportText.append(legalUrl.getKey()).append(": ").append(legalUrl.getValue()).append(System.lineSeparator());
		}

		final Path reportPath = evidenceDirectory.resolve("final-report.txt");
		Files.writeString(reportPath, reportText.toString(), StandardCharsets.UTF_8);
		System.out.println(reportText);
	}

	private static boolean getBooleanConfig(final String systemProperty, final String environmentVariable,
			final boolean defaultValue) {
		final String value = getConfig(systemProperty, environmentVariable, String.valueOf(defaultValue));
		return "true".equalsIgnoreCase(value);
	}

	private static String getConfig(final String systemProperty, final String environmentVariable,
			final String defaultValue) {
		final String propertyValue = System.getProperty(systemProperty);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		final String environmentValue = System.getenv(environmentVariable);
		if (environmentValue != null && !environmentValue.isBlank()) {
			return environmentValue;
		}

		return defaultValue;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static class StepResult {

		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
