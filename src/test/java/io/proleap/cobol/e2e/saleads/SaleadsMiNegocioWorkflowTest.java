package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.File;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio".
 *
 * This test intentionally does not hardcode environment URLs. Provide the
 * login URL dynamically:
 *
 * mvn -Dtest=SaleadsMiNegocioWorkflowTest
 * -Dsaleads.loginUrl=https://<current-environment>/login test
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepNotes = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path reportDir;
	private Path reportFile;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = firstNonBlank(System.getProperty("saleads.loginUrl"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"Set -Dsaleads.loginUrl (or SALEADS_LOGIN_URL) to run this environment-agnostic SaleADS workflow test.",
				loginUrl != null && !loginUrl.trim().isEmpty());

		final boolean headless = Boolean
				.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "true"));
		final long timeoutSeconds = Long.parseLong(firstNonBlank(System.getProperty("saleads.timeoutSeconds"), "30"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		reportDir = Path.of("target", "saleads-reports", "mi-negocio-" + LocalDateTime.now().format(TS_FORMAT));
		Files.createDirectories(reportDir);
		reportFile = reportDir.resolve("final-report.txt");

		driver.get(loginUrl);
		waitForUiToSettle();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void validatesMiNegocioModuleWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Informacion General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Terminos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones", "08_terminos"));
		runStep("Politica de Privacidad", () -> stepValidateLegalLink("Política de Privacidad", "09_politica"));

		final boolean allPass = stepStatus.values().stream().allMatch("PASS"::equals);
		assertTrue("At least one SaleADS workflow validation failed. See report: " + reportFile, allPass);
	}

	private void stepLoginWithGoogle() throws IOException {
		if (!isSidebarVisible()) {
			clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Login with Google", "Google");
			waitForUiToSettle();

			if (isTextVisible("juanlucasbarbiergarzon@gmail.com")) {
				clickByVisibleText("juanlucasbarbiergarzon@gmail.com");
				waitForUiToSettle();
			}
		}

		waitUntil(() -> isSidebarVisible() || isTextVisible("Mi Negocio") || isTextVisible("Negocio"),
				"Expected main interface and sidebar after login.");
		assertTrue("Main application interface was not visible after login.", isSidebarVisible() || isTextVisible("Negocio"));
		captureScreenshot("01_dashboard_loaded");
		appWindowHandle = driver.getWindowHandle();
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		ensureSidebarContext();
		if (!isTextVisible("Mi Negocio")) {
			clickByVisibleText("Negocio");
		}

		clickByVisibleText("Mi Negocio");
		waitForUiToSettle();

		assertTrue("'Agregar Negocio' was not visible after opening Mi Negocio.", isTextVisible("Agregar Negocio"));
		assertTrue("'Administrar Negocios' was not visible after opening Mi Negocio.",
				isTextVisible("Administrar Negocios"));
		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitUntil(() -> isTextVisible("Crear Nuevo Negocio"), "Expected 'Crear Nuevo Negocio' modal.");

		assertTrue("Modal title 'Crear Nuevo Negocio' is missing.", isTextVisible("Crear Nuevo Negocio"));
		assertTrue("Input 'Nombre del Negocio' is missing.",
				isElementVisible(By.xpath("//input[contains(@placeholder,'Nombre del Negocio')] | " +
						"//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]")));
		assertTrue("Expected business quota text is missing.",
				isTextVisible("Tienes 2 de 3 negocios") || isTextVisible("2 de 3 negocios"));
		assertTrue("'Cancelar' button is missing.", isTextVisible("Cancelar"));
		assertTrue("'Crear Negocio' button is missing.", isTextVisible("Crear Negocio"));

		typeIfVisible("Nombre del Negocio", "Negocio Prueba Automatización");
		captureScreenshot("03_agregar_negocio_modal");
		clickByVisibleText("Cancelar");
		waitForUiToSettle();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureSidebarContext();
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}
		clickByVisibleText("Administrar Negocios");
		waitForUiToSettle();

		assertTrue("Section 'Información General' is missing.", isTextVisible("Información General"));
		assertTrue("Section 'Detalles de la Cuenta' is missing.", isTextVisible("Detalles de la Cuenta"));
		assertTrue("Section 'Tus Negocios' is missing.", isTextVisible("Tus Negocios"));
		assertTrue("Section 'Sección Legal' is missing.", isTextVisible("Sección Legal"));
		captureScreenshot("04_administrar_negocios_page");
	}

	private void stepValidateInformacionGeneral() {
		assertTrue("'Información General' section is not visible.", isTextVisible("Información General"));

		final String bodyText = readBodyText();
		final boolean hasExpectedEmail = bodyText.contains("juanlucasbarbiergarzon@gmail.com")
				|| EMAIL_PATTERN.matcher(bodyText).find();
		final boolean hasUserNameHint = isTextVisible("Nombre") || isTextVisible("Usuario") || containsIgnoreCase(bodyText, "juan");

		assertTrue("User name is not visible in 'Información General'.", hasUserNameHint);
		assertTrue("User email is not visible in 'Información General'.", hasExpectedEmail);
		assertTrue("'BUSINESS PLAN' text is not visible.", isTextVisible("BUSINESS PLAN"));
		assertTrue("'Cambiar Plan' button is not visible.", isTextVisible("Cambiar Plan"));
	}

	private void stepValidateDetallesCuenta() {
		assertTrue("'Cuenta creada' is not visible.", isTextVisible("Cuenta creada"));
		assertTrue("'Estado activo' is not visible.", isTextVisible("Estado activo"));
		assertTrue("'Idioma seleccionado' is not visible.", isTextVisible("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		assertTrue("'Tus Negocios' section is missing.", isTextVisible("Tus Negocios"));
		assertTrue("'Agregar Negocio' button is missing in 'Tus Negocios'.", isTextVisible("Agregar Negocio"));
		assertTrue("Expected quota text is missing in 'Tus Negocios'.",
				isTextVisible("Tienes 2 de 3 negocios") || isTextVisible("2 de 3 negocios"));

		final boolean hasBusinessList = isElementVisible(By.xpath(
				"//*[contains(normalize-space(), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]//li | " +
						"//*[contains(normalize-space(), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]//tr"));
		assertTrue("Business list is not visible in 'Tus Negocios'.", hasBusinessList);
	}

	private void stepValidateLegalLink(final String linkText, final String evidencePrefix) throws IOException {
		final String originalWindow = appWindowHandle != null ? appWindowHandle : driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> oldHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);

		waitUntil(() -> driver.getWindowHandles().size() > oldHandles.size() || !driver.getCurrentUrl().equals(originalUrl),
				"Expected new tab or navigation after clicking '" + linkText + "'.");

		final Set<String> newHandles = new LinkedHashSet<>(driver.getWindowHandles());
		newHandles.removeAll(oldHandles);
		final boolean openedNewTab = !newHandles.isEmpty();

		if (openedNewTab) {
			driver.switchTo().window(newHandles.iterator().next());
		}

		waitForUiToSettle();
		assertTrue("Expected heading '" + linkText + "' was not visible.", isTextVisible(linkText));
		assertTrue("Legal content text was not visible for '" + linkText + "'.", hasEnoughLegalBodyText());

		final String finalUrl = driver.getCurrentUrl();
		stepNotes.put(linkText + " URL", finalUrl);
		captureScreenshot(evidencePrefix + "_page");

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiToSettle();
	}

	private void runStep(final String stepName, final StepBlock block) {
		try {
			block.execute();
			stepStatus.put(stepName, "PASS");
		} catch (final Throwable e) {
			stepStatus.put(stepName, "FAIL");
			stepNotes.put(stepName, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
			try {
				captureScreenshot("failure_" + stepName);
			} catch (final IOException ioException) {
				stepNotes.put(stepName + " screenshot", ioException.getMessage());
			}
		}
	}

	private void ensureSidebarContext() {
		waitUntil(this::isSidebarVisible, "Expected left sidebar navigation to be visible.");
	}

	private boolean isSidebarVisible() {
		return isElementVisible(By.xpath("//aside")) || isElementVisible(By.xpath("//nav[contains(@class,'sidebar')]"));
	}

	private void typeIfVisible(final String fieldText, final String value) {
		final List<By> locators = Arrays.asList(
				By.xpath("//input[contains(@placeholder," + xpathLiteral(fieldText) + ")]"),
				By.xpath("//label[contains(normalize-space(), " + xpathLiteral(fieldText) + ")]/following::input[1]"));

		for (final By locator : locators) {
			final Optional<WebElement> input = findVisibleElement(locator, Duration.ofSeconds(3));
			if (input.isPresent()) {
				input.get().clear();
				input.get().sendKeys(value);
				waitForUiToSettle();
				return;
			}
		}
	}

	private void clickByVisibleText(final String... textOptions) {
		final List<String> tried = new ArrayList<>();
		for (final String text : textOptions) {
			tried.add(text);
			final List<By> locators = Arrays.asList(
					By.xpath("//button[contains(normalize-space(), " + xpathLiteral(text) + ")]"),
					By.xpath("//a[contains(normalize-space(), " + xpathLiteral(text) + ")]"),
					By.xpath("//*[@role='button' and contains(normalize-space(), " + xpathLiteral(text) + ")]"),
					By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text)
							+ ") and (self::div or self::span or self::p)]"));

			for (final By locator : locators) {
				final Optional<WebElement> candidate = findVisibleElement(locator, Duration.ofSeconds(4));
				if (candidate.isPresent()) {
					clickWithWait(candidate.get());
					return;
				}
			}
		}
		throw new IllegalStateException("Could not find visible clickable element for texts: " + tried);
	}

	private boolean isTextVisible(final String text) {
		final List<By> locators = Arrays.asList(
				By.xpath("//*[normalize-space() = " + xpathLiteral(text) + "]"),
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]"));

		for (final By locator : locators) {
			if (findVisibleElement(locator, Duration.ofSeconds(2)).isPresent()) {
				return true;
			}
		}
		return false;
	}

	private boolean isElementVisible(final By locator) {
		return findVisibleElement(locator, Duration.ofSeconds(2)).isPresent();
	}

	private Optional<WebElement> findVisibleElement(final By locator, final Duration timeout) {
		try {
			final WebElement element = new WebDriverWait(driver, timeout).until(d -> {
				for (final WebElement candidate : d.findElements(locator)) {
					if (candidate.isDisplayed()) {
						return candidate;
					}
				}
				return null;
			});
			return Optional.ofNullable(element);
		} catch (final TimeoutException e) {
			return Optional.empty();
		}
	}

	private void clickWithWait(final WebElement element) {
		scrollIntoView(element);
		try {
			element.click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToSettle();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private void waitForUiToSettle() {
		try {
			wait.until((ExpectedCondition<Boolean>) wd -> "complete".equals(
					((JavascriptExecutor) wd).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// SPA screens can finish transitions without changing readyState.
		}

		try {
			Thread.sleep(700L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void waitUntil(final BooleanSupplier condition, final String failureMessage) {
		try {
			wait.until(driver -> condition.getAsBoolean());
		} catch (final TimeoutException e) {
			throw new IllegalStateException(failureMessage, e);
		}
	}

	private String readBodyText() {
		final List<WebElement> bodies = driver.findElements(By.tagName("body"));
		if (bodies.isEmpty()) {
			return "";
		}
		return bodies.get(0).getText();
	}

	private boolean containsIgnoreCase(final String source, final String token) {
		return source.toLowerCase().contains(token.toLowerCase());
	}

	private boolean hasEnoughLegalBodyText() {
		final String bodyText = readBodyText().trim();
		return bodyText.length() > 250;
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = reportDir.resolve(sanitizeFileName(checkpointName) + ".png");
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() throws IOException {
		if (reportFile == null) {
			return;
		}

		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test final report");
		lines.add("Generated at: " + LocalDateTime.now());
		lines.add("");
		lines.add("Validation summary:");
		lines.add("Login: " + stepStatus.getOrDefault("Login", "NOT_EXECUTED"));
		lines.add("Mi Negocio menu: " + stepStatus.getOrDefault("Mi Negocio menu", "NOT_EXECUTED"));
		lines.add("Agregar Negocio modal: " + stepStatus.getOrDefault("Agregar Negocio modal", "NOT_EXECUTED"));
		lines.add("Administrar Negocios view: " + stepStatus.getOrDefault("Administrar Negocios view", "NOT_EXECUTED"));
		lines.add("Información General: " + stepStatus.getOrDefault("Informacion General", "NOT_EXECUTED"));
		lines.add("Detalles de la Cuenta: " + stepStatus.getOrDefault("Detalles de la Cuenta", "NOT_EXECUTED"));
		lines.add("Tus Negocios: " + stepStatus.getOrDefault("Tus Negocios", "NOT_EXECUTED"));
		lines.add("Términos y Condiciones: " + stepStatus.getOrDefault("Terminos y Condiciones", "NOT_EXECUTED"));
		lines.add("Política de Privacidad: " + stepStatus.getOrDefault("Politica de Privacidad", "NOT_EXECUTED"));

		if (!stepNotes.isEmpty()) {
			lines.add("");
			lines.add("Notes:");
			for (final Map.Entry<String, String> entry : stepNotes.entrySet()) {
				lines.add("- " + entry.getKey() + ": " + entry.getValue());
			}
		}

		Files.write(reportFile, lines);
	}

	private String sanitizeFileName(final String input) {
		return input.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value;
			}
		}
		return null;
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
			if (i > 0) {
				builder.append(",");
			}
			if (chars[i] == '\'') {
				builder.append("\"'\"");
			} else {
				builder.append("'").append(chars[i]).append("'");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepBlock {
		void execute() throws Exception;
	}

	@FunctionalInterface
	private interface BooleanSupplier {
		boolean getAsBoolean();
	}
}
