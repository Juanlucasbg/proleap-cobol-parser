package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
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
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String DEFAULT_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(40);
	private static final Duration SMALL_WAIT_TIMEOUT = Duration.ofSeconds(8);
	private static final Duration ACCOUNT_SELECTION_TIMEOUT = Duration.ofSeconds(15);
	private static final Path SCREENSHOTS_DIR = Paths.get("target", "saleads-screenshots");
	private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

	private static final List<String> REPORT_ORDER = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() {
		initializeReport();

		final String loginUrl = setting("SALEADS_LOGIN_URL", "saleads.login.url", "");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL or -Dsaleads.login.url to the current environment login page.",
				!loginUrl.isBlank());

		final boolean headless = Boolean
				.parseBoolean(setting("SALEADS_HEADLESS", "saleads.headless", String.valueOf(true)));
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT_TIMEOUT);
		driver.get(loginUrl);
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
		final boolean login = executeStep("Login", true, this::stepLoginWithGoogle);
		final boolean menu = executeStep("Mi Negocio menu", login, this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", menu, this::stepValidateAgregarNegocioModal);
		final boolean administrar = executeStep("Administrar Negocios view", menu, this::stepOpenAdministrarNegocios);
		executeStep("Información General", administrar, this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", administrar, this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", administrar, this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", administrar,
				() -> stepValidateLegalLink("Términos y Condiciones", "terminos_condiciones"));
		executeStep("Política de Privacidad", administrar,
				() -> stepValidateLegalLink("Política de Privacidad", "politica_privacidad"));

		printFinalReport();

		assertAllStepsPassed();
	}

	private String stepLoginWithGoogle() throws Exception {
		final WebElement googleButton = findGoogleLoginButton();
		click(googleButton);

		final String accountEmail = setting("SALEADS_GOOGLE_ACCOUNT", "saleads.google.account", DEFAULT_ACCOUNT);
		trySelectGoogleAccount(accountEmail);

		waitForMainApplication();
		final String screenshot = captureScreenshot("dashboard_loaded");
		return "Dashboard loaded; sidebar visible; screenshot=" + screenshot;
	}

	private String stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, "Agregar Negocio");
		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, "Administrar Negocios");

		final String screenshot = captureScreenshot("mi_negocio_expanded_menu");
		return "Submenu expanded; Agregar/Administrar visible; screenshot=" + screenshot;
	}

	private String stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, "Crear Nuevo Negocio");
		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, "Nombre del Negocio");
		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, "Tienes 2 de 3 negocios");
		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, "Cancelar");
		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, "Crear Negocio");

		final WebElement businessNameInput = wait.until(d -> firstVisible(d.findElements(By.xpath(
				"//input[@placeholder=" + xpathLiteral("Nombre del Negocio")
						+ "] | //label[normalize-space()=" + xpathLiteral("Nombre del Negocio")
						+ "]/following::input[1] | //input[contains(@aria-label, " + xpathLiteral("Negocio") + ")]"))));
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");

		final String screenshot = captureScreenshot("crear_nuevo_negocio_modal");
		clickByVisibleText("Cancelar");
		wait.until((ExpectedCondition<Boolean>) d -> d.findElements(By.xpath("//*[normalize-space()="
				+ xpathLiteral("Crear Nuevo Negocio") + "]")).isEmpty());
		waitForUiToLoad();

		return "Modal validated and cancelled; screenshot=" + screenshot;
	}

	private String stepOpenAdministrarNegocios() throws Exception {
		if (!isVisibleByText("Administrar Negocios", SMALL_WAIT_TIMEOUT)) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");

		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, "Información General", "Informacion General");
		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, "Detalles de la Cuenta", "Detalles de Cuenta");
		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, "Tus Negocios");
		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, "Sección Legal", "Seccion Legal");

		final String screenshot = captureScreenshot("administrar_negocios_view");
		return "Account page loaded with main sections; screenshot=" + screenshot;
	}

	private String stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General", "Informacion General");
		final String sectionText = normalized(section.getText());

		assertContains(sectionText, "business plan", "BUSINESS PLAN text must be visible.");
		assertContains(sectionText, "cambiar plan", "Cambiar Plan button must be visible.");

		final Pattern emailPattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");
		assertTrue("Expected user email to be visible in Información General section.",
				emailPattern.matcher(sectionText).find());

		final boolean hasPotentialName = section.findElements(By.xpath(".//*[self::h1 or self::h2 or self::h3 or self::p or self::span or self::div]"))
				.stream().map(WebElement::getText).map(String::trim).filter(text -> !text.isBlank())
				.map(this::normalized)
				.anyMatch(text -> text.matches("^[\\p{L} .'-]{3,80}$") && !text.contains("business plan")
						&& !text.contains("cambiar plan") && !text.contains("@"));
		assertTrue("Expected user name to be visible in Información General section.", hasPotentialName);

		return "Información General validated (name, email, plan, cambiar plan).";
	}

	private String stepValidateDetallesCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta", "Detalles de Cuenta");
		final String sectionText = normalized(section.getText());

		assertContains(sectionText, "cuenta creada", "Cuenta creada must be visible.");
		assertContains(sectionText, "estado activo", "Estado activo must be visible.");
		assertContains(sectionText, "idioma seleccionado", "Idioma seleccionado must be visible.");

		return "Detalles de la Cuenta validated.";
	}

	private String stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final String sectionText = normalized(section.getText());

		assertFalse("Expected business list or rows to be visible in Tus Negocios section.",
				section.findElements(By.xpath(".//li | .//tr | .//article")).isEmpty());
		assertContains(sectionText, "agregar negocio", "Agregar Negocio button must exist in Tus Negocios.");
		assertContains(sectionText, "tienes 2 de 3 negocios", "Expected business quota text in Tus Negocios.");

		return "Tus Negocios validated.";
	}

	private String stepValidateLegalLink(final String linkText, final String screenshotTag) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();
		final String beforeUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);

		String activeWindow = appWindow;
		wait.until(d -> d.getWindowHandles().size() > beforeHandles.size() || !Objects.equals(beforeUrl, d.getCurrentUrl()));
		final Set<String> currentHandles = driver.getWindowHandles();
		if (currentHandles.size() > beforeHandles.size()) {
			activeWindow = currentHandles.stream().filter(handle -> !beforeHandles.contains(handle)).findFirst()
					.orElse(appWindow);
			driver.switchTo().window(activeWindow);
		}

		waitForUiToLoad();
		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, linkText, withoutAccents(linkText));
		assertFalse("Expected legal content to be visible for " + linkText + ".", driver.findElements(By.xpath(
				"//article//*[string-length(normalize-space()) > 20] | //main//*[string-length(normalize-space()) > 20] | //p[string-length(normalize-space()) > 20]"))
				.isEmpty());

		final String finalUrl = driver.getCurrentUrl();
		final String screenshot = captureScreenshot(screenshotTag);

		if (!activeWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, "Sección Legal", "Seccion Legal");
		return "Validated " + linkText + "; url=" + finalUrl + "; screenshot=" + screenshot;
	}

	private boolean executeStep(final String reportKey, final boolean prerequisite, final StepAction action) {
		if (!prerequisite) {
			report.put(reportKey, StepResult.fail("Skipped because prerequisite step failed."));
			return false;
		}

		try {
			final String detail = action.run();
			report.put(reportKey, StepResult.pass(detail));
			return true;
		} catch (final Throwable ex) {
			report.put(reportKey, StepResult.fail(ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage())));
			return false;
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failed = report.entrySet().stream().filter(entry -> !entry.getValue().pass)
				.map(entry -> entry.getKey() + " -> " + entry.getValue().detail).collect(Collectors.toList());
		if (!failed.isEmpty()) {
			fail("Workflow finished with failures:\n" + String.join("\n", failed));
		}
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Workflow Report ===");
		for (final String key : REPORT_ORDER) {
			final StepResult stepResult = report.get(key);
			final String status = stepResult.pass ? "PASS" : "FAIL";
			System.out.println(key + ": " + status + " - " + stepResult.detail);
		}
	}

	private void initializeReport() {
		report.clear();
		for (final String key : REPORT_ORDER) {
			report.put(key, StepResult.fail("Not executed yet."));
		}
	}

	private WebElement findGoogleLoginButton() {
		final String lower = "abcdefghijklmnopqrstuvwxyz";
		final String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		final By locator = By.xpath("//button[contains(translate(normalize-space(.),'" + upper + "','" + lower
				+ "'),'google')] | //a[contains(translate(normalize-space(.),'" + upper + "','" + lower
				+ "'),'google')] | //*[@role='button' and contains(translate(normalize-space(.),'" + upper + "','"
				+ lower + "'),'google')]");
		return wait.until(d -> firstVisible(d.findElements(locator)));
	}

	private void trySelectGoogleAccount(final String email) {
		final String accountXpath = "//*[normalize-space()=" + xpathLiteral(email) + "]";
		final String originalHandle = driver.getWindowHandle();

		try {
			final WebDriverWait accountWait = new WebDriverWait(driver, ACCOUNT_SELECTION_TIMEOUT);
			accountWait.until(d -> {
				for (final String handle : d.getWindowHandles()) {
					d.switchTo().window(handle);
					if (!d.findElements(By.xpath(accountXpath)).isEmpty()) {
						final WebElement accountEntry = firstVisible(d.findElements(By.xpath(accountXpath)));
						if (accountEntry != null) {
							click(accountEntry);
							return true;
						}
					}
				}
				return false;
			});
		} catch (final TimeoutException ignored) {
			// Google selector is optional when already authenticated.
		}

		try {
			driver.switchTo().window(originalHandle);
		} catch (final Exception ignored) {
			// If original window was closed, next step will fail with actionable detail.
		}
	}

	private void waitForMainApplication() {
		wait.until(d -> !d.findElements(By.xpath("//aside | //nav")).isEmpty());
		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, "Negocio", "Mi Negocio");
	}

	private WebElement findSectionByHeading(final String... headings) {
		waitForAnyVisibleText(DEFAULT_WAIT_TIMEOUT, headings);

		for (final String heading : headings) {
			final WebElement headingElement = firstVisible(
					driver.findElements(By.xpath("//*[normalize-space()=" + xpathLiteral(heading) + "]")));
			if (headingElement == null) {
				continue;
			}

			final List<WebElement> containers = headingElement
					.findElements(By.xpath("ancestor::section | ancestor::article | ancestor::main | ancestor::div"));
			for (final WebElement container : containers) {
				if (container.isDisplayed() && normalized(container.getText()).contains(normalized(heading))) {
					return container;
				}
			}
		}

		throw new NoSuchElementException("Could not resolve section container for headings: " + String.join(", ", headings));
	}

	private void clickByVisibleText(final String text) {
		final String literal = xpathLiteral(text);
		final By locator = By.xpath("//button[normalize-space()=" + literal + "] | //a[normalize-space()=" + literal
				+ "] | //*[@role='button' and normalize-space()=" + literal + "] | //*[normalize-space()=" + literal
				+ "]/ancestor-or-self::*[self::button or self::a or @role='button'][1]");

		final WebElement element = wait.until(d -> firstVisible(d.findElements(locator)));
		click(element);
	}

	private void click(final WebElement element) {
		scrollTo(element);
		try {
			wait.until(d -> element.isDisplayed() && element.isEnabled());
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiToLoad();
	}

	private void waitForAnyVisibleText(final Duration timeout, final String... texts) {
		final List<String> terms = new ArrayList<>();
		for (final String text : texts) {
			terms.add("//*[normalize-space()=" + xpathLiteral(text) + "]");
		}
		final String xpath = String.join(" | ", terms);
		new WebDriverWait(driver, timeout).until(d -> firstVisible(d.findElements(By.xpath(xpath))) != null);
	}

	private boolean isVisibleByText(final String text, final Duration timeout) {
		try {
			waitForAnyVisibleText(timeout, text);
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Some cross-domain contexts do not expose readyState during transitions.
		}
		try {
			Thread.sleep(350);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String captureScreenshot(final String checkpointName) throws IOException {
		Files.createDirectories(SCREENSHOTS_DIR);
		final String safeName = checkpointName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
		final String filename = FILE_TS.format(LocalDateTime.now()) + "_" + safeName + ".png";
		final Path target = SCREENSHOTS_DIR.resolve(filename);

		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

		return target.toAbsolutePath().toString();
	}

	private void scrollTo(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private static WebElement firstVisible(final List<WebElement> elements) {
		for (final WebElement element : elements) {
			try {
				if (element != null && element.isDisplayed()) {
					return element;
				}
			} catch (final Exception ignored) {
				// Element may become stale while iterating.
			}
		}
		return null;
	}

	private String setting(final String envKey, final String propertyKey, final String defaultValue) {
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		return defaultValue;
	}

	private String normalized(final String value) {
		if (value == null) {
			return "";
		}
		final String compact = value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		final String decomposed = Normalizer.normalize(compact, Normalizer.Form.NFD);
		return decomposed.replaceAll("\\p{M}+", "");
	}

	private String withoutAccents(final String value) {
		final String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
		return decomposed.replaceAll("\\p{M}+", "");
	}

	private void assertContains(final String text, final String expected, final String message) {
		assertTrue(message + " Actual text: " + text, text.contains(expected));
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String piece = chars[i] == '\'' ? "\"'\"" : "'" + chars[i] + "'";
			builder.append(piece);
			if (i < chars.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		String run() throws Exception;
	}

	private static final class StepResult {
		private final boolean pass;
		private final String detail;

		private StepResult(final boolean pass, final String detail) {
			this.pass = pass;
			this.detail = detail;
		}

		private static StepResult pass(final String detail) {
			return new StepResult(true, detail);
		}

		private static StepResult fail(final String detail) {
			return new StepResult(false, detail);
		}
	}
}
