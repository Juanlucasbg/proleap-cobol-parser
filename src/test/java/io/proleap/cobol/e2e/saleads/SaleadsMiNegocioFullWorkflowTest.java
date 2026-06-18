package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.fail;

import java.io.IOException;
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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
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

/**
 * Opt-in E2E workflow test for SaleADS "Mi Negocio".
 *
 * <p>
 * This test is URL-agnostic and only runs when {@code saleads.start.url} system
 * property or {@code SALEADS_START_URL}/{@code BASE_URL} environment variable is
 * provided.
 * </p>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;
	private String applicationWindowHandle;

	@Before
	public void setUp() throws IOException {
		final String startUrl = firstNonBlank(System.getProperty("saleads.start.url"), System.getenv("SALEADS_START_URL"),
				System.getenv("BASE_URL"));
		Assume.assumeTrue(
				"Set -Dsaleads.start.url=<login-url> or SALEADS_START_URL/BASE_URL to run SaleADS E2E workflow test.",
				startUrl != null && !startUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"),
				System.getenv("SALEADS_HEADLESS"), "true"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage",
				"--lang=es-ES");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		artifactsDir = Paths.get("target", "saleads-e2e",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(artifactsDir);

		driver.get(startUrl);
		waitForUiLoad();
		applicationWindowHandle = driver.getWindowHandle();

		initializeReport();
	}

	@After
	public void tearDown() throws IOException {
		writeReportFile();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() throws IOException {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalPage("Términos y Condiciones", "08-terminos"));
		runStep("Política de Privacidad", () -> stepValidateLegalPage("Política de Privacidad", "09-politica-privacidad"));

		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue().pass)
				.map(Map.Entry::getKey).collect(Collectors.toList());
		if (!failedSteps.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failed on: " + failedSteps + ". See report: " + artifactsDir.resolve("report.txt"));
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		final Set<String> initialHandles = driver.getWindowHandles();

		clickByFirstAvailableText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Acceder con Google", "Google"));
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(d -> d.getWindowHandles().size() > initialHandles.size());
		} catch (final TimeoutException ignored) {
			// Login can stay in the same tab depending on environment and browser policy.
		}
		switchToNewWindowIfOpened(initialHandles);

		clickIfVisibleText(GOOGLE_ACCOUNT_EMAIL);

		if (!driver.getWindowHandle().equals(applicationWindowHandle) && isWindowStillOpen(applicationWindowHandle)) {
			switchToWindow(applicationWindowHandle);
		}

		final boolean appLoaded = waitUntilAnyTextVisible(
				Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Inicio", "Administrar Negocios"));
		final boolean sidebarVisible = isElementVisible(By.tagName("aside"))
				|| isAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio"));

		assertStepCondition(appLoaded, "Main application interface was not detected after login.");
		assertStepCondition(sidebarVisible, "Sidebar navigation is not visible after login.");

		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickIfVisibleText("Negocio");
		clickByFirstAvailableText(Arrays.asList("Mi Negocio", "Mi negocio"));

		final boolean agregarVisible = isAnyTextVisible(Arrays.asList("Agregar Negocio", "Agregar negocio"));
		final boolean administrarVisible = isAnyTextVisible(Arrays.asList("Administrar Negocios", "Administrar negocios"));

		assertStepCondition(agregarVisible, "Submenu item 'Agregar Negocio' not visible.");
		assertStepCondition(administrarVisible, "Submenu item 'Administrar Negocios' not visible.");

		captureScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByFirstAvailableText(Arrays.asList("Agregar Negocio", "Agregar negocio"));

		assertStepCondition(waitUntilAnyTextVisible(Arrays.asList("Crear Nuevo Negocio", "Crear nuevo negocio")),
				"Modal title 'Crear Nuevo Negocio' is not visible.");
		assertStepCondition(isAnyTextVisible(Arrays.asList("Nombre del Negocio", "Nombre del negocio")),
				"Input label 'Nombre del Negocio' is not visible.");
		assertStepCondition(isAnyTextVisible(Arrays.asList("Tienes 2 de 3 negocios")),
				"Text 'Tienes 2 de 3 negocios' is not visible.");
		assertStepCondition(isAnyTextVisible(Arrays.asList("Cancelar")), "Button 'Cancelar' is not visible.");
		assertStepCondition(isAnyTextVisible(Arrays.asList("Crear Negocio", "Crear negocio")),
				"Button 'Crear Negocio' is not visible.");

		captureScreenshot("03-agregar-negocio-modal");

		fillInputIfVisible("Nombre del Negocio", "Negocio Prueba Automatización");
		clickIfVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		clickIfVisibleText("Mi Negocio");
		clickByFirstAvailableText(Arrays.asList("Administrar Negocios", "Administrar negocios"));

		assertStepCondition(waitUntilAnyTextVisible(Arrays.asList("Información General")), "Section 'Información General' missing.");
		assertStepCondition(isAnyTextVisible(Arrays.asList("Detalles de la Cuenta", "Detalles de la cuenta")),
				"Section 'Detalles de la Cuenta' missing.");
		assertStepCondition(isAnyTextVisible(Arrays.asList("Tus Negocios", "Tus negocios")), "Section 'Tus Negocios' missing.");
		assertStepCondition(isAnyTextVisible(Arrays.asList("Sección Legal", "Sección legal")), "Section 'Sección Legal' missing.");

		captureScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertStepCondition(isAnyTextVisible(Arrays.asList("Información General")), "'Información General' section missing.");

		final String bodyText = visibleBodyText();
		final boolean expectedEmailVisible = bodyText.contains(GOOGLE_ACCOUNT_EMAIL) || EMAIL_PATTERN.matcher(bodyText).find();
		final boolean businessPlanVisible = bodyText.contains("BUSINESS PLAN");
		final boolean cambiarPlanVisible = isAnyTextVisible(Arrays.asList("Cambiar Plan", "Cambiar plan"));
		final boolean likelyUserNameVisible = hasLikelyUserName(bodyText);

		assertStepCondition(likelyUserNameVisible, "User name is not clearly visible in account view.");
		assertStepCondition(expectedEmailVisible, "User email is not visible in account view.");
		assertStepCondition(businessPlanVisible, "Text 'BUSINESS PLAN' is not visible.");
		assertStepCondition(cambiarPlanVisible, "Button 'Cambiar Plan' is not visible.");
	}

	private void stepValidateDetallesCuenta() {
		assertStepCondition(isAnyTextVisible(Arrays.asList("Cuenta creada")), "'Cuenta creada' is not visible.");
		assertStepCondition(isAnyTextVisible(Arrays.asList("Estado activo", "Estado Activo")), "'Estado activo' is not visible.");
		assertStepCondition(isAnyTextVisible(Arrays.asList("Idioma seleccionado", "Idioma Seleccionado")),
				"'Idioma seleccionado' is not visible.");
	}

	private void stepValidateTusNegocios() {
		assertStepCondition(isAnyTextVisible(Arrays.asList("Tus Negocios", "Tus negocios")), "'Tus Negocios' section missing.");
		assertStepCondition(isAnyTextVisible(Arrays.asList("Agregar Negocio", "Agregar negocio")),
				"Button 'Agregar Negocio' is not visible in business section.");
		assertStepCondition(isAnyTextVisible(Arrays.asList("Tienes 2 de 3 negocios")),
				"Text 'Tienes 2 de 3 negocios' is not visible in business section.");

		final String sectionText = sectionTextByHeading("Tus Negocios");
		assertStepCondition(sectionText != null && sectionText.replace("Tus Negocios", "").trim().length() > 0,
				"Business list appears empty or could not be detected.");
	}

	private void stepValidateLegalPage(final String linkText, final String screenshotName) throws IOException {
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickByFirstAvailableText(Arrays.asList(linkText));

		boolean openedInNewTab = false;
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(ExpectedConditions.numberOfWindowsToBe(handlesBeforeClick.size() + 1));
			openedInNewTab = true;
		} catch (final TimeoutException ignored) {
			// Same tab navigation is also valid.
		}

		if (openedInNewTab) {
			switchToNewWindowIfOpened(handlesBeforeClick);
		}

		assertStepCondition(waitUntilAnyTextVisible(Arrays.asList(linkText)), "Heading '" + linkText + "' is not visible.");
		assertStepCondition(visibleBodyText().trim().length() > 120, "Legal content seems too short or not visible.");

		captureScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedInNewTab) {
			driver.close();
			switchToWindow(applicationWindowHandle);
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String stepName, final StepAction action) throws IOException {
		try {
			action.run();
			report.put(stepName, StepResult.pass("PASS"));
		} catch (final Exception e) {
			captureScreenshot("failed-" + slug(stepName));
			report.put(stepName, StepResult.fail("FAIL: " + e.getMessage()));
		}
	}

	private void initializeReport() {
		report.put("Login", StepResult.fail("NOT_EXECUTED"));
		report.put("Mi Negocio menu", StepResult.fail("NOT_EXECUTED"));
		report.put("Agregar Negocio modal", StepResult.fail("NOT_EXECUTED"));
		report.put("Administrar Negocios view", StepResult.fail("NOT_EXECUTED"));
		report.put("Información General", StepResult.fail("NOT_EXECUTED"));
		report.put("Detalles de la Cuenta", StepResult.fail("NOT_EXECUTED"));
		report.put("Tus Negocios", StepResult.fail("NOT_EXECUTED"));
		report.put("Términos y Condiciones", StepResult.fail("NOT_EXECUTED"));
		report.put("Política de Privacidad", StepResult.fail("NOT_EXECUTED"));
	}

	private void writeReportFile() throws IOException {
		if (artifactsDir == null) {
			return;
		}

		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Workflow Report");
		lines.add("================================");
		lines.add("Artifacts directory: " + artifactsDir.toAbsolutePath());
		lines.add("");
		lines.add("Final PASS/FAIL by validation step:");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			lines.add("- " + entry.getKey() + ": " + (entry.getValue().pass ? "PASS" : "FAIL") + " | " + entry.getValue().message);
		}
		lines.add("");
		lines.add("Captured legal URLs:");
		lines.add("- Términos y Condiciones: " + legalUrls.getOrDefault("Términos y Condiciones", "N/A"));
		lines.add("- Política de Privacidad: " + legalUrls.getOrDefault("Política de Privacidad", "N/A"));

		Files.write(artifactsDir.resolve("report.txt"), lines);
		for (final String line : lines) {
			System.out.println(line);
		}
	}

	private void clickByFirstAvailableText(final List<String> texts) {
		WebElement clickable = null;
		for (final String text : texts) {
			final By locator = By.xpath(
					"(//button[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), " + xpathLiteral(text)
							+ ")] | //a[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), "
							+ xpathLiteral(text) + ")] | //*[@role='button' and (normalize-space()=" + xpathLiteral(text)
							+ " or contains(normalize-space(), " + xpathLiteral(text)
							+ "))] | //*[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), "
							+ xpathLiteral(text) + ")]/ancestor-or-self::button[1] | //*[normalize-space()=" + xpathLiteral(text)
							+ " or contains(normalize-space(), " + xpathLiteral(text) + ")]/ancestor-or-self::a[1])[1]");

			try {
				clickable = wait.until(ExpectedConditions.elementToBeClickable(locator));
				break;
			} catch (final TimeoutException ignored) {
				// Try the next visible text fallback.
			}
		}

		if (clickable == null) {
			throw new IllegalStateException("Could not find clickable element by visible text: " + texts);
		}

		try {
			clickable.click();
		} catch (final RuntimeException clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
		}
		waitForUiLoad();
	}

	private void clickIfVisibleText(final String text) {
		final By locator = By.xpath("//*[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), "
				+ xpathLiteral(text) + ")]");
		final List<WebElement> matches = driver.findElements(locator);
		for (final WebElement element : matches) {
			if (element.isDisplayed()) {
				try {
					element.click();
				} catch (final RuntimeException clickException) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				}
				waitForUiLoad();
				return;
			}
		}
	}

	private void fillInputIfVisible(final String labelText, final String value) {
		final String literal = xpathLiteral(labelText);
		final List<By> candidates = Arrays.asList(
				By.xpath("//label[contains(normalize-space(), " + literal + ")]/following::input[1]"),
				By.xpath("//input[@placeholder=" + literal + " or @aria-label=" + literal + "]"));

		for (final By candidate : candidates) {
			final List<WebElement> elements = driver.findElements(candidate);
			if (!elements.isEmpty()) {
				final WebElement input = elements.get(0);
				if (input.isDisplayed()) {
					input.clear();
					input.sendKeys(value);
					return;
				}
			}
		}
	}

	private boolean waitUntilAnyTextVisible(final List<String> texts) {
		try {
			new WebDriverWait(driver, DEFAULT_TIMEOUT).until(d -> isAnyTextVisible(texts));
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private boolean isAnyTextVisible(final List<String> texts) {
		for (final String text : texts) {
			final By locator = By.xpath("//*[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), "
					+ xpathLiteral(text) + ")]");
			if (isElementVisible(locator)) {
				return true;
			}
		}
		return false;
	}

	private boolean isElementVisible(final By locator) {
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private String sectionTextByHeading(final String heading) {
		final String literal = xpathLiteral(heading);
		final List<By> locators = Arrays.asList(
				By.xpath("(//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(), " + literal + ")]"
						+ "/ancestor::*[self::section or self::div][1])[1]"),
				By.xpath("(//*[contains(normalize-space(), " + literal + ")]/ancestor::*[self::section or self::div][1])[1]"));
		for (final By locator : locators) {
			final List<WebElement> matches = driver.findElements(locator);
			if (!matches.isEmpty()) {
				final String text = matches.get(0).getText();
				if (text != null && !text.isBlank()) {
					return text;
				}
			}
		}
		return null;
	}

	private boolean hasLikelyUserName(final String bodyText) {
		if (bodyText == null || bodyText.isBlank()) {
			return false;
		}
		final List<String> lines = Arrays.stream(bodyText.split("\\R")).map(String::trim).filter(line -> !line.isBlank())
				.filter(line -> !line.equalsIgnoreCase("información general") && !line.equalsIgnoreCase("business plan")
						&& !line.equalsIgnoreCase("cambiar plan"))
				.collect(Collectors.toList());
		for (final String line : lines) {
			final boolean isEmail = EMAIL_PATTERN.matcher(line).find();
			final boolean hasLetters = line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*");
			final boolean notTooLong = line.length() <= 60;
			if (!isEmail && hasLetters && notTooLong && line.split("\\s+").length >= 2) {
				return true;
			}
		}
		return false;
	}

	private String visibleBodyText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private void captureScreenshot(final String name) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(artifactsDir.resolve(name + ".png"), screenshot);
	}

	private void waitForUiLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(500);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void switchToNewWindowIfOpened(final Set<String> handlesBeforeClick) {
		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBeforeClick.contains(handle)) {
				switchToWindow(handle);
				return;
			}
		}
	}

	private void switchToWindow(final String windowHandle) {
		driver.switchTo().window(windowHandle);
		waitForUiLoad();
	}

	private boolean isWindowStillOpen(final String handle) {
		return driver.getWindowHandles().contains(handle);
	}

	private void assertStepCondition(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private String slug(final String text) {
		return text.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9\\-]", "");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			sb.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				sb.append(",\"'\",");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws IOException;
	}

	private static class StepResult {
		private final boolean pass;
		private final String message;

		private StepResult(final boolean pass, final String message) {
			this.pass = pass;
			this.message = message;
		}

		private static StepResult pass(final String message) {
			return new StepResult(true, message);
		}

		private static StepResult fail(final String message) {
			return new StepResult(false, message);
		}
	}
}
