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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioWorkflowTest {

	private static final Duration STEP_TIMEOUT = Duration.ofSeconds(30);

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, StepResult> report = new LinkedHashMap<>();

	private String terminosUrl;
	private String privacidadUrl;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"Set saleads.login.url (system property) or SALEADS_LOGIN_URL (env var) to the SaleADS login page URL.",
				loginUrl != null);

		evidenceDir = createEvidenceDirectory();
		driver = new ChromeDriver(buildChromeOptions());
		wait = new WebDriverWait(driver, STEP_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1));
		driver.get(loginUrl);
		waitForPageLoad();
	}

	@After
	public void tearDown() throws IOException {
		if (driver != null) {
			driver.quit();
		}
		if (evidenceDir != null) {
			writeFinalReport();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		runStep("Login", this::loginWithGoogle);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegociosView);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesDeCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", this::validateTerminosYCondiciones);
		runStep("Política de Privacidad", this::validatePoliticaDePrivacidad);

		assertAllStepsPassed();
	}

	private boolean loginWithGoogle() throws Exception {
		clickByFirstMatchingText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
				"Continuar con Google", "Google"));
		waitForPageLoad();
		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");

		final boolean mainInterfaceVisible = isTextVisible("Mi Negocio", Duration.ofSeconds(20))
				|| isTextVisible("Negocio", Duration.ofSeconds(20));
		final boolean sidebarVisible = isElementVisible(By.xpath("//aside|//nav|//*[contains(@class,'sidebar')]"),
				Duration.ofSeconds(20));

		saveScreenshot("01-dashboard-loaded");
		return mainInterfaceVisible && sidebarVisible;
	}

	private boolean openMiNegocioMenu() throws Exception {
		clickIfPresent("Negocio");
		clickByFirstMatchingText(Arrays.asList("Mi Negocio"));

		final boolean expanded = isTextVisible("Agregar Negocio", Duration.ofSeconds(20))
				&& isTextVisible("Administrar Negocios", Duration.ofSeconds(20));
		saveScreenshot("02-mi-negocio-menu-expanded");
		return expanded;
	}

	private boolean validateAgregarNegocioModal() throws Exception {
		clickByFirstMatchingText(Arrays.asList("Agregar Negocio"));
		final boolean titleOk = isTextVisible("Crear Nuevo Negocio", Duration.ofSeconds(20));
		final boolean quotaOk = isTextVisible("Tienes 2 de 3 negocios", Duration.ofSeconds(20));
		final boolean nombreInputOk = isElementVisible(By.xpath(
				"(//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]|//input[contains(@placeholder, 'Nombre del Negocio')])[1]"),
				Duration.ofSeconds(20));
		final boolean cancelarOk = isTextVisible("Cancelar", Duration.ofSeconds(20));
		final boolean crearOk = isTextVisible("Crear Negocio", Duration.ofSeconds(20));

		typeIfPresent("Nombre del Negocio", "Negocio Prueba Automatización");
		saveScreenshot("03-agregar-negocio-modal");
		clickIfPresent("Cancelar");

		return titleOk && quotaOk && nombreInputOk && cancelarOk && crearOk;
	}

	private boolean openAdministrarNegociosView() throws Exception {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(4))) {
			clickByFirstMatchingText(Arrays.asList("Mi Negocio"));
		}

		clickByFirstMatchingText(Arrays.asList("Administrar Negocios"));

		final boolean informacionGeneralOk = isTextVisible("Información General", Duration.ofSeconds(30));
		final boolean detallesOk = isTextVisible("Detalles de la Cuenta", Duration.ofSeconds(30));
		final boolean negociosOk = isTextVisible("Tus Negocios", Duration.ofSeconds(30));
		final boolean legalOk = isTextVisible("Sección Legal", Duration.ofSeconds(30));

		saveScreenshot("04-administrar-negocios-page");
		return informacionGeneralOk && detallesOk && negociosOk && legalOk;
	}

	private boolean validateInformacionGeneral() {
		final boolean userNameOk = isElementVisible(By.xpath(
				"//*[contains(@class,'user') or contains(@class,'name') or contains(@class,'profile')]//*[string-length(normalize-space(text())) > 2]"),
				Duration.ofSeconds(10));
		final boolean userEmailOk = isElementVisible(By.xpath("//*[contains(text(), '@')]"), Duration.ofSeconds(10));
		final boolean planOk = isTextVisible("BUSINESS PLAN", Duration.ofSeconds(10));
		final boolean cambiarPlanOk = isTextVisible("Cambiar Plan", Duration.ofSeconds(10));

		return userNameOk && userEmailOk && planOk && cambiarPlanOk;
	}

	private boolean validateDetallesDeCuenta() {
		final boolean cuentaCreadaOk = isTextVisible("Cuenta creada", Duration.ofSeconds(10));
		final boolean estadoActivoOk = isTextVisible("Estado activo", Duration.ofSeconds(10));
		final boolean idiomaOk = isTextVisible("Idioma seleccionado", Duration.ofSeconds(10));

		return cuentaCreadaOk && estadoActivoOk && idiomaOk;
	}

	private boolean validateTusNegocios() {
		final boolean listOk = isElementVisible(
				By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]/following::*[self::table or self::ul or self::div][1]"),
				Duration.ofSeconds(10));
		final boolean agregarOk = isTextVisible("Agregar Negocio", Duration.ofSeconds(10));
		final boolean quotaOk = isTextVisible("Tienes 2 de 3 negocios", Duration.ofSeconds(10));

		return listOk && agregarOk && quotaOk;
	}

	private boolean validateTerminosYCondiciones() throws Exception {
		final LegalValidationResult result = validateLegalLink("Términos y Condiciones", "Términos y Condiciones",
				"08-terminos-y-condiciones");
		terminosUrl = result.url;
		return result.valid;
	}

	private boolean validatePoliticaDePrivacidad() throws Exception {
		final LegalValidationResult result = validateLegalLink("Política de Privacidad", "Política de Privacidad",
				"09-politica-de-privacidad");
		privacidadUrl = result.url;
		return result.valid;
	}

	private LegalValidationResult validateLegalLink(final String linkText, final String headingText,
			final String screenshotName) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> windowHandlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickByFirstMatchingText(Arrays.asList(linkText));

		String legalHandle = appHandle;
		boolean openedInNewTab = false;
		try {
			wait.until(drv -> drv.getWindowHandles().size() > windowHandlesBeforeClick.size());
			final Set<String> handlesAfterClick = new LinkedHashSet<>(driver.getWindowHandles());
			handlesAfterClick.removeAll(windowHandlesBeforeClick);
			if (!handlesAfterClick.isEmpty()) {
				legalHandle = handlesAfterClick.iterator().next();
				openedInNewTab = true;
			}
		} catch (final TimeoutException ignored) {
			openedInNewTab = false;
		}

		driver.switchTo().window(legalHandle);
		waitForPageLoad();

		final boolean headingOk = isTextVisible(headingText, Duration.ofSeconds(25));
		final boolean contentOk = hasLegalContent();
		saveScreenshot(screenshotName);

		final String url = driver.getCurrentUrl();

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForPageLoad();
		} else {
			driver.navigate().back();
			waitForPageLoad();
		}

		final boolean returnedToApp = isTextVisible("Sección Legal", Duration.ofSeconds(20))
				|| isTextVisible("Tus Negocios", Duration.ofSeconds(20));

		return new LegalValidationResult(headingOk && contentOk && returnedToApp, url);
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final List<By> selectors = Arrays.asList(
				By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(email) + ")]"),
				By.xpath("//div[@data-identifier and contains(normalize-space(.), " + xpathLiteral(email) + ")]"));

		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(drv -> hasAnyVisibleElement(selectors) || drv.getWindowHandles().size() > 1);
		} catch (final TimeoutException ignored) {
			// Account picker did not appear. This can happen when session is already authenticated.
		}

		final String currentHandle = driver.getWindowHandle();
		boolean accountClicked = false;
		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			for (final By selector : selectors) {
				if (clickIfVisible(selector)) {
					waitForPageLoad();
					accountClicked = true;
					break;
				}
			}
			if (accountClicked) {
				break;
			}
		}
		driver.switchTo().window(currentHandle);
		waitForPageLoad();
	}

	private void runStep(final String name, final StepAction action) throws IOException {
		boolean pass = false;
		String message = "PASS";

		try {
			pass = action.run();
			if (!pass) {
				message = "Validation returned false";
			}
		} catch (final Exception exception) {
			pass = false;
			message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
		}

		report.put(name, new StepResult(pass, message));
		if (!pass) {
			saveScreenshot("failure-" + normalizeForFileName(name));
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failed = REPORT_FIELDS.stream().filter(name -> !report.containsKey(name) || !report.get(name).passed)
				.collect(Collectors.toList());

		assertTrue("Failed validations: " + failed + ". Report file: " + evidenceDir.resolve("final-report.txt"),
				failed.isEmpty());
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("generated_at=" + LocalDateTime.now().toString());
		lines.add("evidence_dir=" + evidenceDir.toAbsolutePath());
		lines.add("");

		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			if (result == null) {
				lines.add(field + ": FAIL - NOT EXECUTED");
			} else {
				lines.add(field + ": " + (result.passed ? "PASS" : "FAIL") + " - " + result.message);
			}
		}

		lines.add("");
		lines.add("Términos y Condiciones URL: " + (terminosUrl == null ? "N/A" : terminosUrl));
		lines.add("Política de Privacidad URL: " + (privacidadUrl == null ? "N/A" : privacidadUrl));

		Files.write(evidenceDir.resolve("final-report.txt"), lines, StandardCharsets.UTF_8);
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path directory = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(directory);
		return directory;
	}

	private ChromeOptions buildChromeOptions() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		final String headlessValue = firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"));
		if (!"false".equalsIgnoreCase(headlessValue)) {
			options.addArguments("--headless=new");
		}
		return options;
	}

	private void waitForPageLoad() {
		wait.until(driver -> {
			final Object state = ((JavascriptExecutor) driver).executeScript("return document.readyState");
			return "complete".equals(state);
		});
	}

	private void clickByFirstMatchingText(final List<String> texts) throws Exception {
		for (final String text : texts) {
			final By selector = byClickableText(text);
			if (clickIfVisible(selector)) {
				waitForPageLoad();
				return;
			}
		}
		throw new NoSuchElementException("Could not find clickable element for any text: " + texts);
	}

	private void clickIfPresent(final String text) throws Exception {
		clickIfVisible(byClickableText(text));
		waitForPageLoad();
	}

	private boolean clickIfVisible(final By selector) {
		try {
			final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
			try {
				element.click();
			} catch (final Exception clickException) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			}
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private By byClickableText(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath(
				"(//button[contains(normalize-space(.), " + literal + ")]" +
				"|//a[contains(normalize-space(.), " + literal + ")]" +
				"|//*[@role='button' and contains(normalize-space(.), " + literal + ")]" +
				"|//li[contains(normalize-space(.), " + literal + ")]" +
				"|//span[contains(normalize-space(.), " + literal + ")]" +
				"|//div[contains(normalize-space(.), " + literal + ")])[1]");
	}

	private void typeIfPresent(final String label, final String value) {
		try {
			final String literal = xpathLiteral(label);
			final By inputSelector = By.xpath(
					"(//label[contains(normalize-space(.), " + literal + ")]/following::input[1]|//input[contains(@placeholder, "
							+ literal + ")])[1]");
			final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(inputSelector));
			input.clear();
			input.sendKeys(value);
		} catch (final TimeoutException ignored) {
			// Optional action only.
		}
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		final String literal = xpathLiteral(text);
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions
					.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.), " + literal + ")]")));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private boolean isElementVisible(final By selector, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(selector));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private boolean hasLegalContent() {
		try {
			final WebElement body = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
			return body.getText() != null && body.getText().trim().length() > 200;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private boolean hasAnyVisibleElement(final List<By> selectors) {
		for (final By selector : selectors) {
			if (isElementVisible(selector, Duration.ofSeconds(1))) {
				return true;
			}
		}
		return false;
	}

	private void saveScreenshot(final String fileName) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final Path screenshotFile = evidenceDir.resolve(normalizeForFileName(fileName) + ".png");
		Files.write(screenshotFile, bytes);
	}

	private String normalizeForFileName(final String text) {
		return text.toLowerCase().replaceAll("[^a-z0-9\\-]+", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(",\"'\",");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private String firstNonBlank(final String first, final String second) {
		if (first != null && !first.trim().isEmpty()) {
			return first.trim();
		}
		if (second != null && !second.trim().isEmpty()) {
			return second.trim();
		}
		return null;
	}

	private interface StepAction {
		boolean run() throws Exception;
	}

	private static class StepResult {
		private final boolean passed;
		private final String message;

		private StepResult(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message;
		}
	}

	private static class LegalValidationResult {
		private final boolean valid;
		private final String url;

		private LegalValidationResult(final boolean valid, final String url) {
			this.valid = valid;
			this.url = url;
		}
	}
}
