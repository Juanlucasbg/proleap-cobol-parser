package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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

/**
 * Environment-agnostic E2E workflow for the SaleADS "Mi Negocio" module.
 *
 * Required runtime config:
 * -Dsaleads.e2e.enabled=true
 * -Dsaleads.login.url=https://<env>/login (must be supplied per environment)
 *
 * Optional runtime config:
 * -Dsaleads.google.account.email=juanlucasbarbiergarzon@gmail.com
 * -Dsaleads.e2e.headless=true
 * -Dsaleads.e2e.timeout.seconds=25
 */
public class SaleAdsMiNegocioFullWorkflowE2ETest {

	private static final String STATUS_PASS = "PASS";
	private static final String STATUS_FAIL = "FAIL";
	private static final String STATUS_SKIPPED = "SKIPPED";

	private static final DateTimeFormatter SHOT_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private final Map<String, String> report = new LinkedHashMap<>();
	private final Map<String, String> evidence = new LinkedHashMap<>();
	private final Path screenshotDir = Path.of("target", "saleads-e2e-screenshots");
	private String applicationWindowHandle;

	@Before
	public void setUp() throws IOException {
		boolean enabled = Boolean.parseBoolean(config("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Skipping SaleADS E2E test. Set -Dsaleads.e2e.enabled=true to run.", enabled);

		String loginUrl = config("saleads.login.url", "SALEADS_LOGIN_URL", "");
		Assume.assumeTrue("Missing SaleADS login URL. Set -Dsaleads.login.url or SALEADS_LOGIN_URL.",
				!loginUrl.isBlank());

		boolean headless = Boolean.parseBoolean(config("saleads.e2e.headless", "SALEADS_E2E_HEADLESS", "true"));
		int timeoutSeconds = Integer.parseInt(config("saleads.e2e.timeout.seconds", "SALEADS_E2E_TIMEOUT_SECONDS", "25"));

		Files.createDirectories(screenshotDir);

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);
		driver.get(loginUrl);
		applicationWindowHandle = driver.getWindowHandle();
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaDePrivacidad);

		String finalReport = buildFinalReport();
		assertFalse("Final report contains FAIL/SKIPPED statuses.\n" + finalReport, hasNonPassingStep());
	}

	private void stepLoginWithGoogle() {
		clickFirstVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google"));
		waitForUiToLoad();

		String accountEmail = config("saleads.google.account.email", "SALEADS_GOOGLE_ACCOUNT_EMAIL",
				"juanlucasbarbiergarzon@gmail.com");
		clickIfVisible(By.xpath("//*[normalize-space()=" + xPathLiteral(accountEmail) + "]"), Duration.ofSeconds(10));
		waitForUiToLoad();

		waitForVisible(By.xpath("//*[normalize-space()='Negocio']"));
		waitForVisible(By.xpath("//aside | //nav"));
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		waitForVisible(By.xpath("//aside | //nav"));
		clickFirstVisibleText(Arrays.asList("Negocio"));
		clickFirstVisibleText(Arrays.asList("Mi Negocio"));
		waitForUiToLoad();

		waitForVisible(By.xpath("//*[normalize-space()='Agregar Negocio']"));
		waitForVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"));
		captureScreenshot("02-mi-negocio-expanded-menu");
	}

	private void stepValidateAgregarNegocioModal() {
		clickFirstVisibleText(Arrays.asList("Agregar Negocio"));
		waitForUiToLoad();

		waitForVisible(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']"));
		WebElement businessNameInput = firstVisibleElement(Arrays.asList(
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]")));
		assertNotNull("Expected field 'Nombre del Negocio' to exist.", businessNameInput);
		waitForVisible(By.xpath("//*[contains(normalize-space(),'Tienes 2 de 3 negocios')]"));
		waitForVisible(By.xpath("//*[normalize-space()='Cancelar']"));
		waitForVisible(By.xpath("//*[normalize-space()='Crear Negocio']"));

		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		captureScreenshot("03-crear-negocio-modal");

		clickFirstVisibleText(Arrays.asList("Cancelar"));
		waitForUiToLoad();
		waitForInvisible(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']"));
	}

	private void stepOpenAdministrarNegocios() {
		ensureMiNegocioMenuExpanded();
		clickFirstVisibleText(Arrays.asList("Administrar Negocios"));
		waitForUiToLoad();

		waitForVisible(By.xpath("//*[normalize-space()='Información General']"));
		waitForVisible(By.xpath("//*[normalize-space()='Detalles de la Cuenta']"));
		waitForVisible(By.xpath("//*[normalize-space()='Tus Negocios']"));
		waitForVisible(By.xpath("//*[normalize-space()='Sección Legal']"));
		captureScreenshot("04-administrar-negocios-full-page");
	}

	private void stepValidateInformacionGeneral() {
		WebElement section = getSectionContainer("Información General");
		assertTrue("Expected user email in 'Información General'.", hasVisibleTextMatching(section, EMAIL_PATTERN));
		assertTrue("Expected user name in 'Información General'.", hasLikelyUserName(section));
		assertTrue("Expected text 'BUSINESS PLAN' in 'Información General'.",
				hasVisibleDescendant(section, By.xpath(".//*[contains(normalize-space(),'BUSINESS PLAN')]")));
		assertTrue("Expected button 'Cambiar Plan' in 'Información General'.",
				hasVisibleDescendant(section, By.xpath(".//*[normalize-space()='Cambiar Plan']")));
	}

	private void stepValidateDetallesDeLaCuenta() {
		WebElement section = getSectionContainer("Detalles de la Cuenta");
		assertTrue("'Cuenta creada' is missing.", hasVisibleDescendant(section, By.xpath(".//*[contains(normalize-space(),'Cuenta creada')]")));
		assertTrue("'Estado activo' is missing.", hasVisibleDescendant(section, By.xpath(".//*[contains(normalize-space(),'Estado activo')]")));
		assertTrue("'Idioma seleccionado' is missing.",
				hasVisibleDescendant(section, By.xpath(".//*[contains(normalize-space(),'Idioma seleccionado')]")));
	}

	private void stepValidateTusNegocios() {
		WebElement section = getSectionContainer("Tus Negocios");
		assertTrue("Business list is missing.",
				hasVisibleDescendant(section, By.xpath(".//ul/li | .//table//tr | .//*[contains(@class,'business') or contains(@class,'negocio')]")));
		assertTrue("'Agregar Negocio' button is missing in 'Tus Negocios'.",
				hasVisibleDescendant(section, By.xpath(".//*[normalize-space()='Agregar Negocio']")));
		assertTrue("'Tienes 2 de 3 negocios' is missing in 'Tus Negocios'.",
				hasVisibleDescendant(section, By.xpath(".//*[contains(normalize-space(),'Tienes 2 de 3 negocios')]")));
	}

	private void stepValidateTerminosYCondiciones() {
		WebElement legalSection = getSectionContainer("Sección Legal");
		String currentHandle = driver.getWindowHandle();
		String finalUrl = clickLegalLinkAndValidate(legalSection, "Términos y Condiciones", "Términos y Condiciones",
				"08-terminos-y-condiciones");
		assertFalse("Expected non-empty terms URL.", finalUrl.isBlank());
		evidence.put("Términos y Condiciones URL", finalUrl);
		switchBackToApplicationWindow(currentHandle);
	}

	private void stepValidatePoliticaDePrivacidad() {
		WebElement legalSection = getSectionContainer("Sección Legal");
		String currentHandle = driver.getWindowHandle();
		String finalUrl = clickLegalLinkAndValidate(legalSection, "Política de Privacidad", "Política de Privacidad",
				"09-politica-de-privacidad");
		assertFalse("Expected non-empty privacy URL.", finalUrl.isBlank());
		evidence.put("Política de Privacidad URL", finalUrl);
		switchBackToApplicationWindow(currentHandle);
	}

	private String clickLegalLinkAndValidate(WebElement legalSection, String linkText, String heading, String shotPrefix) {
		Set<String> originalHandles = driver.getWindowHandles();

		WebElement link = legalSection.findElement(By.xpath(".//*[normalize-space()=" + xPathLiteral(linkText) + "]"));
		link.click();
		waitForUiToLoad();

		String activeHandle = waitForAnyNewWindowOrSameTab(originalHandles);
		driver.switchTo().window(activeHandle);
		waitForUiToLoad();

		waitForVisible(By.xpath("//*[normalize-space()=" + xPathLiteral(heading) + "]"));
		waitForVisible(By.xpath("//*[self::p or self::article or self::section][string-length(normalize-space()) > 40]"));
		String finalUrl = Objects.toString(driver.getCurrentUrl(), "");
		captureScreenshot(shotPrefix);
		return finalUrl;
	}

	private void ensureMiNegocioMenuExpanded() {
		if (!isVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"))) {
			clickFirstVisibleText(Arrays.asList("Negocio"));
			clickFirstVisibleText(Arrays.asList("Mi Negocio"));
			waitForUiToLoad();
		}
	}

	private WebElement getSectionContainer(String headingText) {
		waitForVisible(By.xpath("//*[normalize-space()=" + xPathLiteral(headingText) + "]"));
		List<By> candidates = Arrays.asList(
				By.xpath("//*[normalize-space()=" + xPathLiteral(headingText) + "]/ancestor::section[1]"),
				By.xpath("//*[normalize-space()=" + xPathLiteral(headingText) + "]/ancestor::div[1]"),
				By.xpath("//*[normalize-space()=" + xPathLiteral(headingText)
						+ "]/ancestor::*[self::main or self::article][1]"));
		WebElement section = firstVisibleElement(candidates);
		assertNotNull("Could not resolve section container for '" + headingText + "'.", section);
		return section;
	}

	private void runStep(String stepName, Runnable step) {
		if (hasFailedStep()) {
			report.put(stepName, STATUS_SKIPPED + " (previous step failed)");
			return;
		}

		try {
			step.run();
			report.put(stepName, STATUS_PASS);
		} catch (Exception | AssertionError ex) {
			report.put(stepName, STATUS_FAIL + " (" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ")");
		}
	}

	private boolean hasFailedStep() {
		return report.values().stream().anyMatch(status -> status.startsWith(STATUS_FAIL));
	}

	private boolean hasNonPassingStep() {
		return report.values().stream().anyMatch(status -> !status.startsWith(STATUS_PASS));
	}

	private String buildFinalReport() {
		StringBuilder sb = new StringBuilder("SaleADS Mi Negocio full workflow report");
		for (Map.Entry<String, String> entry : report.entrySet()) {
			sb.append(System.lineSeparator()).append("- ").append(entry.getKey()).append(": ").append(entry.getValue());
		}
		for (Map.Entry<String, String> entry : evidence.entrySet()) {
			sb.append(System.lineSeparator()).append("- ").append(entry.getKey()).append(": ").append(entry.getValue());
		}
		return sb.toString();
	}

	private void clickFirstVisibleText(List<String> texts) {
		List<By> locators = new ArrayList<>();
		for (String text : texts) {
			String escaped = xPathLiteral(text);
			locators.add(By.xpath("//*[self::button or self::a or @role='button'][normalize-space()=" + escaped + "]"));
			locators.add(By.xpath("//*[normalize-space()=" + escaped + "]"));
		}

		WebElement target = firstVisibleElement(locators);
		assertNotNull("Could not locate clickable element with text(s): " + texts, target);
		target.click();
		waitForUiToLoad();
	}

	private WebElement firstVisibleElement(List<By> locators) {
		for (By locator : locators) {
			try {
				WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				if (element != null && element.isDisplayed()) {
					return element;
				}
			} catch (TimeoutException ignored) {
				// Try next locator variant.
			}
		}
		return null;
	}

	private void clickIfVisible(By locator, Duration timeout) {
		try {
			WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			WebElement element = shortWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
			element.click();
		} catch (TimeoutException ignored) {
			// Account selector may not appear if user is already authenticated.
		}
	}

	private void waitForVisible(By locator) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void waitForInvisible(By locator) {
		wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
	}

	private boolean isVisible(By locator) {
		try {
			WebElement element = driver.findElement(locator);
			return element.isDisplayed();
		} catch (NoSuchElementException ex) {
			return false;
		}
	}

	private String waitForAnyNewWindowOrSameTab(Set<String> originalHandles) {
		try {
			wait.until((ExpectedCondition<Boolean>) d -> d != null && d.getWindowHandles().size() > originalHandles.size());
			for (String handle : driver.getWindowHandles()) {
				if (!originalHandles.contains(handle)) {
					return handle;
				}
			}
		} catch (TimeoutException ignored) {
			// Link likely reused the same tab.
		}
		return driver.getWindowHandle();
	}

	private void switchBackToApplicationWindow(String fallbackHandle) {
		if (applicationWindowHandle != null && driver.getWindowHandles().contains(applicationWindowHandle)) {
			driver.switchTo().window(applicationWindowHandle);
			waitForUiToLoad();
			return;
		}
		if (fallbackHandle != null && driver.getWindowHandles().contains(fallbackHandle)) {
			driver.switchTo().window(fallbackHandle);
			waitForUiToLoad();
		}
	}

	private void captureScreenshot(String prefix) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		try {
			String timestamp = LocalDateTime.now().format(SHOT_TS);
			Path target = screenshotDir.resolve(prefix + "-" + timestamp + ".png");
			Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			// Evidence capture should not prevent functional validation.
		}
	}

	private void waitForUiToLoad() {
		wait.until(d -> {
			if (d == null) {
				return false;
			}
			Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(state);
		});
	}

	private boolean hasVisibleDescendant(WebElement root, By locator) {
		List<WebElement> matches = root.findElements(locator);
		return matches.stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean hasVisibleTextMatching(WebElement root, Pattern pattern) {
		List<WebElement> descendants = root.findElements(By.xpath(".//*[normalize-space()]"));
		return descendants.stream()
				.filter(WebElement::isDisplayed)
				.map(WebElement::getText)
				.filter(Objects::nonNull)
				anyMatch(text -> pattern.matcher(text.trim()).find());
	}

	private boolean hasLikelyUserName(WebElement root) {
		List<WebElement> descendants = root.findElements(By.xpath(".//*[normalize-space()]"));
		return descendants.stream()
				.filter(WebElement::isDisplayed)
				.map(WebElement::getText)
				.filter(Objects::nonNull)
				.map(String::trim)
				.anyMatch(text -> !text.isBlank()
						&& !EMAIL_PATTERN.matcher(text).find()
						&& text.length() >= 3
						&& text.length() <= 80
						&& text.split("\\s+").length >= 2
						&& !text.toUpperCase().contains("BUSINESS PLAN")
						&& !text.equalsIgnoreCase("Cambiar Plan"));
	}

	private String xPathLiteral(String value) {
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
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private String config(String propertyName, String envName, String defaultValue) {
		String value = System.getProperty(propertyName);
		if (value != null && !value.isBlank()) {
			return value.trim();
		}
		value = System.getenv(envName);
		if (value != null && !value.isBlank()) {
			return value.trim();
		}
		return defaultValue;
	}
}
