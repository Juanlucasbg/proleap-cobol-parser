package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(7);
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, Boolean> reportStatus = new LinkedHashMap<>();
	private final Map<String, String> reportDetails = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		final String headlessEnv = System.getenv().getOrDefault("SALEADS_HEADLESS", "false");
		if (Boolean.parseBoolean(headlessEnv)) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() {
		reportStatus.put("Login", runStep("Login", this::stepLoginWithGoogle));
		reportStatus.put("Mi Negocio menu", runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu));
		reportStatus.put("Agregar Negocio modal", runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal));
		reportStatus.put("Administrar Negocios view", runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios));
		reportStatus.put("Información General", runStep("Información General", this::stepValidateInformacionGeneral));
		reportStatus.put("Detalles de la Cuenta", runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta));
		reportStatus.put("Tus Negocios", runStep("Tus Negocios", this::stepValidateTusNegocios));
		reportStatus.put("Términos y Condiciones", runStep("Términos y Condiciones", this::stepValidateTerminos));
		reportStatus.put("Política de Privacidad", runStep("Política de Privacidad", this::stepValidatePrivacidad));

		printFinalReport();

		final boolean allPassed = reportStatus.values().stream().allMatch(Boolean::booleanValue);
		assertTrue("One or more workflow checks failed. Review logs and screenshots at: " + evidenceDir.toAbsolutePath(),
				allPassed);
	}

	private boolean stepLoginWithGoogle() throws Exception {
		final String loginUrl = requiredEnv("SALEADS_LOGIN_URL");
		driver.get(loginUrl);
		waitForUiToLoad();

		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		final boolean clickedLogin = clickFirstVisibleText("Sign in with Google", "Iniciar sesión con Google",
				"Inicia sesión con Google", "Ingresar con Google", "Continúa con Google", "Continuar con Google",
				"Google");

		if (!clickedLogin) {
			throw new IllegalStateException("Could not find a Google login button by visible text.");
		}

		waitForGoogleOrDashboard(handlesBefore);
		selectGoogleAccountIfPresent("juanlucasbarbiergarzon@gmail.com");
		switchBackToAppWindow(appHandle);
		waitForDashboard();

		final boolean sidebarVisible = isElementVisible(
				By.xpath("//aside[.//a or .//button] | //nav[.//a or .//button]"), DEFAULT_TIMEOUT);
		final boolean appVisible = sidebarVisible && (isTextVisible("Negocio", DEFAULT_TIMEOUT)
				|| isTextVisible("Mi Negocio", DEFAULT_TIMEOUT));

		captureScreenshot("01-dashboard-loaded");
		return appVisible;
	}

	private boolean stepOpenMiNegocioMenu() throws Exception {
		final boolean menuExpanded = ensureMiNegocioExpanded();

		final boolean agregarVisible = isTextVisible("Agregar Negocio", DEFAULT_TIMEOUT);
		final boolean administrarVisible = isTextVisible("Administrar Negocios", DEFAULT_TIMEOUT);

		captureScreenshot("02-mi-negocio-menu-expanded");
		return menuExpanded && agregarVisible && administrarVisible;
	}

	private boolean stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		final boolean titleVisible = isTextVisible("Crear Nuevo Negocio", DEFAULT_TIMEOUT);
		final boolean inputVisible = isElementVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or @aria-label='Nombre del Negocio' or @name='nombreNegocio']"
						+ " | //label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				DEFAULT_TIMEOUT);
		final boolean limitVisible = isTextVisible("Tienes 2 de 3 negocios", DEFAULT_TIMEOUT);
		final boolean cancelarVisible = isTextVisible("Cancelar", DEFAULT_TIMEOUT);
		final boolean crearVisible = isTextVisible("Crear Negocio", DEFAULT_TIMEOUT);

		if (inputVisible) {
			typeInBusinessNameInput("Negocio Prueba Automatización");
		}

		captureScreenshot("03-agregar-negocio-modal");
		if (cancelarVisible) {
			clickByVisibleText("Cancelar");
		}

		return titleVisible && inputVisible && limitVisible && cancelarVisible && crearVisible;
	}

	private boolean stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioExpanded();
		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		final boolean infoGeneral = isTextVisible("Información General", DEFAULT_TIMEOUT);
		final boolean detalles = isTextVisible("Detalles de la Cuenta", DEFAULT_TIMEOUT);
		final boolean negocios = isTextVisible("Tus Negocios", DEFAULT_TIMEOUT);
		final boolean legal = isTextVisible("Sección Legal", DEFAULT_TIMEOUT);

		captureScreenshot("04-administrar-negocios-view");
		return infoGeneral && detalles && negocios && legal;
	}

	private boolean stepValidateInformacionGeneral() {
		final String sectionText = sectionText("Información General");
		final Matcher matcher = EMAIL_PATTERN.matcher(sectionText);
		final boolean emailVisible = matcher.find();
		final boolean nameVisible = sectionText != null && !sectionText.isBlank() && sectionText.split("\\R").length >= 2;
		final boolean planVisible = isTextVisible("BUSINESS PLAN", DEFAULT_TIMEOUT);
		final boolean cambiarPlanVisible = isTextVisible("Cambiar Plan", DEFAULT_TIMEOUT);
		return nameVisible && emailVisible && planVisible && cambiarPlanVisible;
	}

	private boolean stepValidateDetallesCuenta() {
		final boolean cuentaCreada = isTextVisible("Cuenta creada", DEFAULT_TIMEOUT);
		final boolean estadoActivo = isTextVisible("Estado activo", DEFAULT_TIMEOUT);
		final boolean idiomaSeleccionado = isTextVisible("Idioma seleccionado", DEFAULT_TIMEOUT);
		return cuentaCreada && estadoActivo && idiomaSeleccionado;
	}

	private boolean stepValidateTusNegocios() {
		final String sectionText = sectionText("Tus Negocios");
		final boolean listVisible = sectionText != null && sectionText.strip().length() > "Tus Negocios".length();
		final boolean agregarVisible = isTextVisible("Agregar Negocio", DEFAULT_TIMEOUT);
		final boolean limitVisible = isTextVisible("Tienes 2 de 3 negocios", DEFAULT_TIMEOUT);
		return listVisible && agregarVisible && limitVisible;
	}

	private boolean stepValidateTerminos() throws Exception {
		final String finalUrl = validateLegalPage("Términos y Condiciones", "Términos y Condiciones", "08-terminos");
		legalUrls.put("Términos y Condiciones", finalUrl);
		return true;
	}

	private boolean stepValidatePrivacidad() throws Exception {
		final String finalUrl = validateLegalPage("Política de Privacidad", "Política de Privacidad", "09-privacidad");
		legalUrls.put("Política de Privacidad", finalUrl);
		return true;
	}

	private String validateLegalPage(final String linkText, final String headingText, final String screenshotName)
			throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String previousUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);

		try {
			new WebDriverWait(driver, DEFAULT_TIMEOUT).until(d -> d.getWindowHandles().size() > handlesBefore.size()
					|| !d.getCurrentUrl().equals(previousUrl));
		} catch (final TimeoutException ignored) {
			// Some apps change content in the same URL. We still validate heading/content below.
		}

		boolean openedNewTab = false;
		if (driver.getWindowHandles().size() > handlesBefore.size()) {
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					openedNewTab = true;
					break;
				}
			}
		}

		waitForUiToLoad();
		final boolean headingVisible = isTextVisible(headingText, DEFAULT_TIMEOUT);
		final String bodyText = getPageBodyText();
		final boolean legalContentVisible = bodyText != null && bodyText.strip().length() > 120;

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		if (!headingVisible || !legalContentVisible) {
			throw new AssertionError("Legal page validation failed for: " + linkText);
		}
		return finalUrl;
	}

	private boolean runStep(final String stepName, final StepAction action) {
		try {
			final boolean passed = action.run();
			reportDetails.put(stepName, passed ? "PASS" : "FAIL");
			return passed;
		} catch (final Exception ex) {
			reportDetails.put(stepName, "FAIL - " + ex.getMessage());
			try {
				captureScreenshot("failed-" + slug(stepName));
			} catch (final Exception ignored) {
				// Best effort evidence capture.
			}
			return false;
		}
	}

	private void printFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("\n==== SaleADS Mi Negocio Final Report ====\n");
		for (final Map.Entry<String, Boolean> entry : reportStatus.entrySet()) {
			final String details = reportDetails.getOrDefault(entry.getKey(), "");
			builder.append(String.format("- %-28s : %s (%s)%n", entry.getKey(), entry.getValue() ? "PASS" : "FAIL",
					details));
		}

		builder.append("- Términos y Condiciones URL     : ")
				.append(legalUrls.getOrDefault("Términos y Condiciones", "N/A")).append('\n');
		builder.append("- Política de Privacidad URL     : ")
				.append(legalUrls.getOrDefault("Política de Privacidad", "N/A")).append('\n');
		builder.append("- Evidence folder                : ").append(evidenceDir.toAbsolutePath()).append('\n');
		builder.append("=========================================\n");

		System.out.println(builder.toString());
	}

	private boolean ensureMiNegocioExpanded() {
		if (isTextVisible("Agregar Negocio", SHORT_TIMEOUT) && isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			return true;
		}

		clickIfVisible("Negocio", SHORT_TIMEOUT);
		clickIfVisible("Mi Negocio", SHORT_TIMEOUT);
		waitForUiToLoad();

		if (isTextVisible("Agregar Negocio", SHORT_TIMEOUT) && isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			return true;
		}

		clickIfVisible("Mi Negocio", SHORT_TIMEOUT);
		waitForUiToLoad();
		return isTextVisible("Agregar Negocio", SHORT_TIMEOUT) && isTextVisible("Administrar Negocios", SHORT_TIMEOUT);
	}

	private void typeInBusinessNameInput(final String value) {
		final List<By> inputLocators = Arrays.asList(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio') or @aria-label='Nombre del Negocio']"),
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[@name='nombreNegocio']"));

		for (final By locator : inputLocators) {
			for (final WebElement input : driver.findElements(locator)) {
				if (input.isDisplayed()) {
					input.clear();
					input.sendKeys(value);
					return;
				}
			}
		}
	}

	private String sectionText(final String heading) {
		final String headingLiteral = toXPathLiteral(heading);
		final By sectionLocator = By.xpath("//*[self::section or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4"
				+ " or self::span or self::p][contains(normalize-space(), " + headingLiteral + ")]][1]");
		try {
			final WebElement section = new WebDriverWait(driver, SHORT_TIMEOUT)
					.until(ExpectedConditions.visibilityOfElementLocated(sectionLocator));
			return section.getText();
		} catch (final TimeoutException ex) {
			return "";
		}
	}

	private boolean clickByVisibleText(final String text) {
		final WebElement element = waitForVisibleTextElement(text, DEFAULT_TIMEOUT);
		safeClick(element);
		waitForUiToLoad();
		return true;
	}

	private boolean clickIfVisible(final String text, final Duration timeout) {
		try {
			final WebElement element = waitForVisibleTextElement(text, timeout);
			safeClick(element);
			waitForUiToLoad();
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private boolean clickFirstVisibleText(final String... candidates) {
		for (final String candidate : candidates) {
			if (clickIfVisible(candidate, SHORT_TIMEOUT)) {
				return true;
			}
		}
		return false;
	}

	private WebElement waitForVisibleTextElement(final String text, final Duration timeout) {
		final String literal = toXPathLiteral(text);
		final List<By> locators = Arrays.asList(
				By.xpath("//*[self::button or self::a or @role='button'][normalize-space()=" + literal
						+ " or contains(normalize-space(), " + literal + ")]"),
				By.xpath("//*[normalize-space()=" + literal + "]"),
				By.xpath("//*[contains(normalize-space(), " + literal + ")]"));

		return new WebDriverWait(driver, timeout).until(d -> {
			for (final By locator : locators) {
				for (final WebElement candidate : d.findElements(locator)) {
					try {
						if (candidate.isDisplayed()) {
							return candidate;
						}
					} catch (final StaleElementReferenceException ignored) {
						// Retry on next poll.
					}
				}
			}
			return null;
		});
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			waitForVisibleTextElement(text, timeout);
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private boolean isElementVisible(final By by, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void waitForDashboard() {
		wait.until(d -> (isTextVisible("Mi Negocio", SHORT_TIMEOUT) || isTextVisible("Negocio", SHORT_TIMEOUT))
				&& isElementVisible(By.xpath("//aside[.//a or .//button] | //nav[.//a or .//button]"), SHORT_TIMEOUT));
	}

	private void waitForGoogleOrDashboard(final Set<String> handlesBefore) {
		try {
			wait.until(d -> d.getWindowHandles().size() > handlesBefore.size() || d.getCurrentUrl().contains("accounts.google")
					|| isTextVisible("Mi Negocio", SHORT_TIMEOUT) || isTextVisible("Negocio", SHORT_TIMEOUT));
		} catch (final TimeoutException ex) {
			// If not immediately visible, the next step attempts explicit account selection.
		}
	}

	private void selectGoogleAccountIfPresent(final String email) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(45).toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				final String url = driver.getCurrentUrl();
				if (url != null && url.contains("accounts.google.com")) {
					if (clickIfVisible(email, SHORT_TIMEOUT)) {
						waitForUiToLoad();
						return;
					}
				}
			}
			if (isTextVisible("Mi Negocio", SHORT_TIMEOUT) || isTextVisible("Negocio", SHORT_TIMEOUT)) {
				return;
			}
			sleep(1000L);
		}
	}

	private void switchBackToAppWindow(final String preferredHandle) {
		final Set<String> handles = driver.getWindowHandles();
		if (handles.contains(preferredHandle)) {
			driver.switchTo().window(preferredHandle);
			return;
		}

		for (final String handle : handles) {
			driver.switchTo().window(handle);
			final String url = driver.getCurrentUrl();
			if (url == null || !url.contains("accounts.google.com")) {
				return;
			}
		}
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final TimeoutException ignored) {
			// Some apps keep network activity alive. We still proceed with explicit element waits.
		}
		sleep(500L);
	}

	private void safeClick(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
			new Actions(driver).moveToElement(element).pause(Duration.ofMillis(120)).perform();
			element.click();
		} catch (final Exception ex) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private String requiredEnv(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(
					"Missing environment variable " + key + ". Set it to the current SaleADS login page URL.");
		}
		return value;
	}

	private String captureScreenshot(final String name) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(name + ".png");
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		return target.toAbsolutePath().toString();
	}

	private String getPageBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception ex) {
			return "";
		}
	}

	private String slug(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String toXPathLiteral(final String input) {
		if (!input.contains("'")) {
			return "'" + input + "'";
		}
		if (!input.contains("\"")) {
			return "\"" + input + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = input.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char current = chars[i];
			if (current == '\'') {
				builder.append("\"'\"");
			} else if (current == '"') {
				builder.append("'\"'");
			} else {
				builder.append("'").append(current).append("'");
			}
			if (i < chars.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		boolean run() throws Exception;
	}
}
