package io.proleap.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String EMAIL_TO_SELECT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String termsAndConditionsUrl = "N/A";
	private String privacyPolicyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final String startUrl = requiredEnv("SALEADS_LOGIN_URL");
		final int waitSeconds = Integer.parseInt(envOrDefault("SALEADS_WAIT_SECONDS", "30"));

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		if (!"false".equalsIgnoreCase(envOrDefault("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-gpu");
		options.addArguments("--lang=es-ES");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		screenshotDir = Paths.get("target", "saleads-mi-negocio-screenshots", timestamp);
		Files.createDirectories(screenshotDir);

		driver.get(startUrl);
		waitForUiLoad();
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
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminos);
		runStep("Política de Privacidad", this::stepValidatePrivacidad);

		final String finalReport = buildFinalReport();
		System.out.println(finalReport);
		Assert.assertFalse(finalReport, hasFailedStep());
	}

	private void stepLoginWithGoogle() throws Exception {
		clickAnyVisibleText("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
				"Continuar con Google", "Google");
		waitForUiLoad();
		handleGoogleAccountSelectionIfPrompted();

		// Dashboard and left sidebar validation.
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside | //nav")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//aside//*[contains(normalize-space(.), 'Negocio')] | //nav//*[contains(normalize-space(.), 'Negocio')]")));
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		ensureMiNegocioSubmenuVisible();
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')] | //input[contains(@placeholder, 'Nombre del Negocio')]")));
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final WebElement businessNameInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')]")));
		businessNameInput.click();
		waitForUiLoad();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]")));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioSubmenuVisible();
		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertAnyVisibleText("Información General", "Informacion General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertAnyVisibleText("Sección Legal", "Seccion Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisibleText("Información General", "Informacion General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final String expectedName = System.getenv("SALEADS_EXPECTED_USER_NAME");
		if (expectedName != null && !expectedName.isBlank()) {
			Assert.assertTrue("Expected user name not visible: " + expectedName, bodyText.contains(expectedName));
		} else {
			final List<String> ignored = Arrays.asList("Información General", "Informacion General",
					"BUSINESS PLAN", "Cambiar Plan");
			final boolean hasNameLikeText = Arrays.stream(bodyText.split("\\R"))
					.map(String::trim)
					.filter(line -> !line.isEmpty())
					.filter(line -> !ignored.contains(line))
					.anyMatch(line -> line.matches(".*[A-Za-z]{3,}.*") && !line.contains("@"));
			Assert.assertTrue("No user-name-like text found in account view.", hasNameLikeText);
		}

		final String expectedEmail = System.getenv("SALEADS_EXPECTED_USER_EMAIL");
		if (expectedEmail != null && !expectedEmail.isBlank()) {
			Assert.assertTrue("Expected email not visible: " + expectedEmail, bodyText.contains(expectedEmail));
		} else {
			Assert.assertTrue("No visible email found on page.", EMAIL_PATTERN.matcher(bodyText).find());
		}
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		final WebElement negociosSection = findSectionByHeading("Tus Negocios");
		final List<WebElement> listCandidates = negociosSection.findElements(By.xpath(".//li | .//tr"));
		final boolean hasListLikeContent = !listCandidates.isEmpty() || negociosSection.getText().split("\\R").length >= 4;
		Assert.assertTrue("Business list content is not visible in 'Tus Negocios'.", hasListLikeContent);
	}

	private void stepValidateTerminos() throws Exception {
		termsAndConditionsUrl = openLegalLinkAndValidate(
				new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
				new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
				"05-terminos-condiciones");
	}

	private void stepValidatePrivacidad() throws Exception {
		privacyPolicyUrl = openLegalLinkAndValidate(
				new String[] { "Política de Privacidad", "Politica de Privacidad" },
				new String[] { "Política de Privacidad", "Politica de Privacidad" },
				"06-politica-privacidad");
	}

	private String openLegalLinkAndValidate(final String[] linkTextOptions, final String[] headingTextOptions,
			final String screenshotName) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickAnyVisibleText(linkTextOptions);
		waitForUiLoad();

		boolean switchedToNewTab = false;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					switchedToNewTab = true;
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			// Same-tab navigation is acceptable by requirement.
		}

		waitForUiLoad();
		assertAnyVisibleText(headingTextOptions);

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Expected legal content to be visible for " + Arrays.toString(headingTextOptions),
				bodyText.length() > 120);
		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		return finalUrl;
	}

	private void ensureMiNegocioSubmenuVisible() throws Exception {
		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			clickIfVisible("Negocio");
			clickIfVisible("Mi Negocio");
			waitForUiLoad();
		}
	}

	private void handleGoogleAccountSelectionIfPrompted() throws Exception {
		String originalHandle = driver.getWindowHandle();
		try {
			new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(d -> d.getWindowHandles().size() > 1 || isTextVisible(EMAIL_TO_SELECT));
		} catch (final TimeoutException ignored) {
			return;
		}

		final Set<String> handles = driver.getWindowHandles();
		for (final String handle : handles) {
			if (!handle.equals(originalHandle)) {
				try {
					driver.switchTo().window(handle);
					break;
				} catch (final NoSuchWindowException ignored) {
					// Window closed while switching; continue gracefully.
				}
			}
		}

		if (isTextVisible(EMAIL_TO_SELECT)) {
			clickByVisibleText(EMAIL_TO_SELECT);
		}

		try {
			driver.switchTo().window(originalHandle);
		} catch (final NoSuchWindowException ignored) {
			originalHandle = driver.getWindowHandles().iterator().next();
			driver.switchTo().window(originalHandle);
		}
		waitForUiLoad();
	}

	private WebElement findSectionByHeading(final String headingText) {
		final String literal = xpathLiteral(headingText);
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"(//section[.//*[contains(normalize-space(.), " + literal + ")]] | //div[.//*[self::h1 or self::h2 or self::h3][contains(normalize-space(.), "
						+ literal + ")]])[1]")));
	}

	private void clickIfVisible(final String text) throws Exception {
		if (isTextVisible(text)) {
			clickByVisibleText(text);
		}
	}

	private void clickAnyVisibleText(final String... candidates) throws Exception {
		for (final String candidate : candidates) {
			if (isTextVisible(candidate)) {
				clickByVisibleText(candidate);
				return;
			}
		}
		throw new AssertionError("None of these texts were visible/clickable: " + Arrays.toString(candidates));
	}

	private void clickByVisibleText(final String text) throws Exception {
		final WebElement target = wait.until(ExpectedConditions.elementToBeClickable(visibleTextLocator(text)));
		target.click();
		waitForUiLoad();
	}

	private By visibleTextLocator(final String text) {
		final String literal = xpathLiteral(text);
		final String xpath = "("
				+ "//button[normalize-space() = " + literal + "]"
				+ " | //a[normalize-space() = " + literal + "]"
				+ " | //*[@role='button' and normalize-space() = " + literal + "]"
				+ " | //*[self::span or self::div][normalize-space() = " + literal
				+ "]/ancestor::*[self::button or self::a or @role='button'][1]"
				+ " | //*[self::button or self::a or @role='button'][contains(normalize-space(.), " + literal + ")]"
				+ " | //*[(self::span or self::div) and contains(normalize-space(.), " + literal
				+ ")]/ancestor::*[self::button or self::a or @role='button'][1]"
				+ ")[1]";
		return By.xpath(xpath);
	}

	private boolean isTextVisible(final String text) {
		try {
			final List<WebElement> elements = driver.findElements(visibleTextLocator(text));
			return elements.stream().anyMatch(WebElement::isDisplayed);
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void assertVisibleText(final String text) {
		final String literal = xpathLiteral(text);
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(.), " + literal + ")]")));
	}

	private void assertAnyVisibleText(final String... options) {
		for (final String option : options) {
			try {
				assertVisibleText(option);
				return;
			} catch (final Exception ignored) {
				// Try next option.
			}
		}
		throw new AssertionError("None of these texts were visible: " + Arrays.toString(options));
	}

	private void waitForUiLoad() throws Exception {
		wait.until(driver -> "complete".equals(
				((JavascriptExecutor) driver).executeScript("return document.readyState")));
		Thread.sleep(500);
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = screenshotDir.resolve(checkpointName + ".png");
		Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, new StepResult(true, "PASS"));
		} catch (final Throwable throwable) {
			try {
				takeScreenshot("FAIL-" + stepName.replaceAll("[^A-Za-z0-9_-]", "_"));
			} catch (final Exception ignored) {
				// Ignore screenshot errors for already-failed steps.
			}
			report.put(stepName, new StepResult(false, "FAIL: " + throwable.getMessage()));
		}
	}

	private boolean hasFailedStep() {
		return report.values().stream().anyMatch(step -> !step.passed);
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("\n================ SALEADS MI NEGOCIO FULL TEST REPORT ================\n");
		builder.append("Screenshots directory: ").append(screenshotDir.toAbsolutePath()).append('\n');
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().details).append('\n');
		}
		builder.append("- Términos y Condiciones URL: ").append(termsAndConditionsUrl).append('\n');
		builder.append("- Política de Privacidad URL: ").append(privacyPolicyUrl).append('\n');
		builder.append("=====================================================================\n");
		return builder.toString();
	}

	private String envOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private String requiredEnv(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(
					"Missing required environment variable '" + key + "'. " +
							"Set it to the SaleADS login page URL for the current environment.");
		}
		return value;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder result = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				result.append(", \"'\", ");
			}
			result.append("'").append(parts[i]).append("'");
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}
	}
}
