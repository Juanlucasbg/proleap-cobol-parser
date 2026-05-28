package io.proleap.qa;

import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.junit.BeforeClass;
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

/**
 * End-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * <p>Run with:
 *
 * <pre>
 * mvn -Dtest=SaleadsMiNegocioWorkflowTest \
 *     -Dsaleads.e2e.enabled=true \
 *     -Dsaleads.startUrl=https://<current-saleads-env>/login \
 *     test
 * </pre>
 *
 * <p>No domain is hardcoded. All navigation starts from the provided URL.
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final long SHORT_WAIT_SECONDS = 8L;

	private final Map<String, StepOutcome> stepOutcomes = new LinkedHashMap<>();
	private final List<String> failureMessages = new ArrayList<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Path screenshotsDir;
	private int screenshotCounter;
	private String configuredAccountEmail;

	@BeforeClass
	public static void ensureEnabled() {
		Assume.assumeTrue("SaleADS E2E disabled. Use -Dsaleads.e2e.enabled=true to run this test.",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));
	}

	@Before
	public void setUp() throws IOException {
		ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(System.getProperty("saleads.headless", "true"))) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		WebDriverManager.chromedriver().setup();
		driver = new ChromeDriver(options);

		long waitSeconds = Long.parseLong(System.getProperty("saleads.waitSeconds", "25"));
		wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
		configuredAccountEmail = System.getProperty("saleads.googleAccountEmail", DEFAULT_ACCOUNT_EMAIL);
		screenshotCounter = 0;

		String evidencePath = System.getProperty("saleads.evidenceDir", "target/saleads-evidence");
		evidenceDir = Paths.get(evidencePath);
		screenshotsDir = evidenceDir.resolve("screenshots");
		Files.createDirectories(screenshotsDir);

		String startUrl = System.getProperty("saleads.startUrl", "").trim();
		Assert.assertFalse(
				"Missing -Dsaleads.startUrl. Provide the login URL for the current SaleADS environment (no hardcoded domain in test).",
				startUrl.isEmpty());
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
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegociosView);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones",
				"08_terminos_y_condiciones"));
		runStep("Política de Privacidad",
				() -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09_politica_privacidad"));

		Assert.assertTrue("SaleADS workflow had failures:\n" + String.join("\n", failureMessages), failureMessages.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		if (!isSidebarVisible()) {
			Set<String> handlesBeforeLogin = new LinkedHashSet<>(driver.getWindowHandles());
			clickByAnyVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
					"Continuar con Google");
			waitForUiToLoad();
			maybeSwitchToNewWindow(handlesBeforeLogin);
			selectGoogleAccountIfShown(configuredAccountEmail);
			switchToWindowWithSidebar();
		}

		Assert.assertTrue("Main application interface did not appear after login.", isSidebarVisible());
		assertTextVisible("Negocio");
		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByAnyVisibleText("Mi Negocio", "Negocio");
		waitForUiToLoad();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByAnyVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		WebElement nombreNegocioInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"(//input[@placeholder='Nombre del Negocio'] | //input[@name='nombreNegocio'] | //input[contains(@aria-label,'Nombre del Negocio')])[1]")));
		nombreNegocioInput.click();
		nombreNegocioInput.sendKeys("Negocio Prueba Automatización");
		waitForUiToLoad();

		takeScreenshot("03_agregar_negocio_modal");
		clickByAnyVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegociosView() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickByAnyVisibleText("Mi Negocio", "Negocio");
			waitForUiToLoad();
		}

		clickByAnyVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		takeScreenshot("04_administrar_negocios_full_page");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("Información General");
		String pageText = visibleTextSnapshot();
		Assert.assertTrue("A user email was expected in Información General.", EMAIL_PATTERN.matcher(pageText).find());
		Assert.assertTrue("Expected user name-like content in Información General.",
				pageText.contains(" ") || pageText.length() > 30);
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
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

		List<WebElement> businessEntries = driver.findElements(By.xpath(
				"//section[.//*[contains(normalize-space(),'Tus Negocios')]]//*[self::li or self::article or self::div[contains(@class,'card')]]"));
		Assert.assertFalse("Business list area under 'Tus Negocios' appears empty.", businessEntries.isEmpty());
	}

	private void stepValidateLegalLink(String linkText, String headingText, String screenshotBaseName) throws IOException {
		String applicationHandle = driver.getWindowHandle();
		String beforeClickUrl = driver.getCurrentUrl();
		Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByAnyVisibleText(linkText);
		waitForUiToLoad();

		WebDriverWait legalWait = new WebDriverWait(driver, Duration.ofSeconds(30));
		legalWait.until(d -> d.getWindowHandles().size() > handlesBefore.size()
				|| !Objects.equals(beforeClickUrl, d.getCurrentUrl()) || isTextVisibleNoThrow(headingText));

		boolean openedNewTab = maybeSwitchToNewWindow(handlesBefore);
		waitForUiToLoad();
		if (!openedNewTab) {
			Assert.assertNotEquals("Legal link did not navigate to a different page: " + linkText, beforeClickUrl,
					driver.getCurrentUrl());
		}

		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"(//h1[contains(normalize-space()," + xpathLiteral(headingText) + ")] | //h2[contains(normalize-space(),"
						+ xpathLiteral(headingText) + ")] | //h3[contains(normalize-space()," + xpathLiteral(headingText)
						+ ")])[1]")));
		Assert.assertTrue("Expected legal content text for: " + headingText, visibleTextSnapshot().length() > 200);

		takeScreenshot(screenshotBaseName);
		String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(applicationHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		stepOutcomes.put(linkText, new StepOutcome(true, "Final URL: " + finalUrl));
	}

	private void runStep(String label, StepAction action) {
		try {
			action.run();
			stepOutcomes.putIfAbsent(label, new StepOutcome(true, "PASS"));
		} catch (Throwable error) {
			String message = label + " FAILED: " + error.getMessage();
			failureMessages.add(message);
			stepOutcomes.put(label, new StepOutcome(false, message));
			try {
				takeScreenshot("failure_" + label.toLowerCase(Locale.ROOT).replace(' ', '_'));
			} catch (IOException ignored) {
				// Keep the original failure as the primary signal.
			}
		}
	}

	private void clickByAnyVisibleText(String... options) {
		Throwable lastError = null;
		for (String option : options) {
			try {
				WebElement element = wait.until(
						ExpectedConditions.visibilityOfElementLocated(By.xpath(clickableXpathByExactText(option))));
				safeClick(element);
				waitForUiToLoad();
				return;
			} catch (Throwable error) {
				lastError = error;
			}
		}

		throw new IllegalStateException("Unable to click any expected text option: " + String.join(", ", options), lastError);
	}

	private boolean maybeSwitchToNewWindow(Set<String> oldHandles) {
		Set<String> currentHandles = driver.getWindowHandles();
		for (String handle : currentHandles) {
			if (!oldHandles.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return true;
			}
		}

		return false;
	}

	private void selectGoogleAccountIfShown(String email) {
		List<By> accountLocators = List.of(
				By.xpath("//*[normalize-space()=" + xpathLiteral(email) + "]"),
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral(email) + ")]"));

		for (By accountLocator : accountLocators) {
			try {
				WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(SHORT_WAIT_SECONDS));
				WebElement account = shortWait.until(ExpectedConditions.visibilityOfElementLocated(accountLocator));
				safeClick(account);
				waitForUiToLoad();
				return;
			} catch (TimeoutException ignored) {
				// Account picker is optional and might not show for already-authenticated sessions.
			}
		}
	}

	private void switchToWindowWithSidebar() {
		WebDriverWait loginWait = new WebDriverWait(driver, Duration.ofSeconds(90));
		loginWait.until(d -> {
			for (String handle : d.getWindowHandles()) {
				d.switchTo().window(handle);
				if (isSidebarVisible()) {
					return true;
				}
			}
			return false;
		});
		waitForUiToLoad();
	}

	private boolean isSidebarVisible() {
		return isVisible(By.xpath("//aside | //nav[.//*[contains(normalize-space(),'Negocio')]]"));
	}

	private boolean isVisible(By locator) {
		List<WebElement> elements = driver.findElements(locator);
		for (WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
	}

	private void assertTextVisible(String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(),"
				+ xpathLiteral(text) + ") and not(self::script) and not(self::style)]")));
	}

	private boolean isTextVisible(String text) {
		return isVisible(By.xpath("//*[contains(normalize-space()," + xpathLiteral(text)
				+ ") and not(self::script) and not(self::style)]"));
	}

	private boolean isTextVisibleNoThrow(String text) {
		try {
			return isVisible(By.xpath("//*[contains(normalize-space()," + xpathLiteral(text)
					+ ") and not(self::script) and not(self::style)]"));
		} catch (Throwable ignored) {
			return false;
		}
	}

	private String visibleTextSnapshot() {
		WebElement body = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
		return body.getText().replace('\n', ' ').trim();
	}

	private void safeClick(WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (Throwable ignored) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void takeScreenshot(String name) throws IOException {
		File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		String fileName = String.format("%02d_%s.png", ++screenshotCounter, sanitizeName(name));
		Path destination = screenshotsDir.resolve(fileName);
		Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio Workflow Report").append(System.lineSeparator());
		report.append("Generated at: ").append(OffsetDateTime.now()).append(System.lineSeparator());
		report.append(System.lineSeparator());

		List<String> orderedFields = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
				"Información General", "Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones", "Política de Privacidad");

		for (String field : orderedFields) {
			StepOutcome outcome = stepOutcomes.get(field);
			if (outcome == null) {
				report.append(field).append(": FAIL (Not executed)").append(System.lineSeparator());
			} else {
				report.append(field).append(": ").append(outcome.passed ? "PASS" : "FAIL");
				if (outcome.details != null && !outcome.details.isBlank()) {
					report.append(" - ").append(outcome.details);
				}
				report.append(System.lineSeparator());
			}
		}

		Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.createDirectories(evidenceDir);
		Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
	}

	private String clickableXpathByExactText(String text) {
		String literal = xpathLiteral(text);
		return "(//button[normalize-space()=" + literal + "]"
				+ " | //a[normalize-space()=" + literal + "]"
				+ " | //*[@role='button' and normalize-space()=" + literal + "]"
				+ " | //*[@role='menuitem' and normalize-space()=" + literal + "]"
				+ " | //*[normalize-space()=" + literal + "]/ancestor::button[1]"
				+ " | //*[normalize-space()=" + literal + "]/ancestor::a[1]"
				+ " | //*[normalize-space()=" + literal + "])[1]";
	}

	private String sanitizeName(String input) {
		return input.replaceAll("[^a-zA-Z0-9._-]+", "_");
	}

	private String xpathLiteral(String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		StringBuilder result = new StringBuilder("concat(");
		char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			String part = String.valueOf(chars[i]);
			if ("'".equals(part)) {
				result.append("\"'\"");
			} else {
				result.append("'").append(part).append("'");
			}
			if (i < chars.length - 1) {
				result.append(",");
			}
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepOutcome {
		private final boolean passed;
		private final String details;

		private StepOutcome(boolean passed, String details) {
			this.passed = passed;
			this.details = details;
		}
	}
}
