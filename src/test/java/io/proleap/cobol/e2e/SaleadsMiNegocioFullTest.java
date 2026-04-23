package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";

	private final Map<String, String> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private Path reportPath;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue(
				"Set -Dsaleads.e2e.enabled=true to run this browser-based workflow test.",
				enabled);

		initializeReport();

		final String runId = LocalDateTime.now().format(TS_FORMAT);
		screenshotDir = Paths.get("target", "saleads-mi-negocio-evidence", runId, "screenshots");
		reportPath = Paths.get("target", "saleads-mi-negocio-evidence", runId, "final-report.txt");
		Files.createDirectories(screenshotDir);
		Files.createDirectories(reportPath.getParent());

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(30));

		final String loginUrl = System.getProperty("saleads.login.url", "").trim();
		assertTrue(
				"Provide -Dsaleads.login.url with the environment login page URL. "
						+ "This test never hardcodes a domain.",
				!loginUrl.isEmpty());
		driver.get(loginUrl);
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
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		boolean continueWorkflow = runStep("Login", this::stepLoginWithGoogle);

		if (continueWorkflow) {
			continueWorkflow = runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		} else {
			markBlocked("Mi Negocio menu");
		}

		if (continueWorkflow) {
			continueWorkflow = runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		} else {
			markBlocked("Agregar Negocio modal");
		}

		if (continueWorkflow) {
			continueWorkflow = runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		} else {
			markBlocked("Administrar Negocios view");
		}

		if (continueWorkflow) {
			continueWorkflow = runStep("Información General", this::stepValidateInformacionGeneral);
		} else {
			markBlocked("Información General");
		}

		if (continueWorkflow) {
			continueWorkflow = runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		} else {
			markBlocked("Detalles de la Cuenta");
		}

		if (continueWorkflow) {
			continueWorkflow = runStep("Tus Negocios", this::stepValidateTusNegocios);
		} else {
			markBlocked("Tus Negocios");
		}

		if (continueWorkflow) {
			continueWorkflow = runStep("Términos y Condiciones", this::stepValidateTerminos);
		} else {
			markBlocked("Términos y Condiciones");
		}

		if (continueWorkflow) {
			runStep("Política de Privacidad", this::stepValidatePrivacidad);
		} else {
			markBlocked("Política de Privacidad");
		}

		assertAllRequiredStepsPassed();
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByFirstVisibleText(
				"Sign in with Google",
				"Iniciar sesion con Google",
				"Iniciar sesi\u00f3n con Google",
				"Continuar con Google",
				"Login with Google");

		waitForUiToLoad();
		selectGoogleAccountIfVisible();

		assertAnyVisible(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(translate(@class, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'sidebar')]"));

		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		assertAnyVisible(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(translate(@class, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'sidebar')]"));

		clickByFirstVisibleText("Negocio");
		clickByFirstVisibleText("Mi Negocio");

		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded-menu");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByFirstVisibleText("Agregar Negocio");

		assertAnyVisibleText("Crear Nuevo Negocio");
		assertAnyVisibleText("Nombre del Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
		assertAnyVisibleText("Cancelar");
		assertAnyVisibleText("Crear Negocio");

		captureScreenshot("03-agregar-negocio-modal");

		final WebElement nameInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]"
						+ " | //input[contains(translate(@placeholder, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'nombre')]")));
		nameInput.click();
		nameInput.clear();
		nameInput.sendKeys(TEST_BUSINESS_NAME);
		waitForUiToLoad();

		clickByFirstVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(
				"//*[contains(normalize-space(), 'Crear Nuevo Negocio')]")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isAnyVisibleTextPresent("Administrar Negocios")) {
			clickByFirstVisibleText("Mi Negocio");
		}

		clickByFirstVisibleText("Administrar Negocios");

		assertAnyVisibleText("Informacion General", "Informaci\u00f3n General");
		assertAnyVisibleText("Detalles de la Cuenta");
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Seccion Legal", "Secci\u00f3n Legal");

		captureFullPageScreenshot("04-administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		assertAnyVisibleText("Informacion General", "Informaci\u00f3n General");

		final String bodyText = normalizedBodyText();
		assertTrue("User email was not visible.", bodyText.contains("@"));
		assertTrue(
				"User name indicator was not visible.",
				hasAnyVisibleEmailOrLikelyUserName());
		assertAnyVisibleText("BUSINESS PLAN");
		assertAnyVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() throws Exception {
		assertAnyVisibleText("Detalles de la Cuenta");
		assertAnyVisibleText("Cuenta creada");
		assertAnyVisibleText("Estado activo");
		assertAnyVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() throws Exception {
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");

		final WebElement section = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//*[contains(normalize-space(), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]")));
		final List<WebElement> possibleItems = section.findElements(By.xpath(
				".//*[self::li or self::tr or contains(translate(@class, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'card') or contains(translate(@class, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'business')]"));
		assertTrue(
				"Business list was not visible.",
				!possibleItems.isEmpty() || section.getText().trim().length() > "Tus Negocios".length() + 20);
	}

	private void stepValidateTerminos() throws Exception {
		termsUrl = validateLegalLink(
				"Términos y Condiciones",
				List.of("Terminos y Condiciones", "T\u00e9rminos y Condiciones"),
				"05-terminos-y-condiciones");
	}

	private void stepValidatePrivacidad() throws Exception {
		privacyUrl = validateLegalLink(
				"Política de Privacidad",
				List.of("Politica de Privacidad", "Pol\u00edtica de Privacidad"),
				"06-politica-de-privacidad");
	}

	private String validateLegalLink(
			final String primaryText,
			final List<String> headingOptions,
			final String screenshotName) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();
		final String beforeUrl = driver.getCurrentUrl();

		if ("T\u00e9rminos y Condiciones".equals(primaryText)) {
			clickByFirstVisibleText("T\u00e9rminos y Condiciones", "Terminos y Condiciones");
		} else {
			clickByFirstVisibleText("Pol\u00edtica de Privacidad", "Politica de Privacidad");
		}

		waitForUiToLoad();

		try {
			wait.until(d -> d.getWindowHandles().size() > beforeHandles.size()
					|| !Objects.equals(beforeUrl, d.getCurrentUrl()));
		} catch (final TimeoutException ignored) {
			// Continue with whichever page is currently active.
		}

		final Set<String> afterHandles = driver.getWindowHandles();
		final List<String> newHandles = new ArrayList<>();
		for (final String handle : afterHandles) {
			if (!beforeHandles.contains(handle)) {
				newHandles.add(handle);
			}
		}

		boolean switchedToNewTab = false;
		if (!newHandles.isEmpty()) {
			driver.switchTo().window(newHandles.get(0));
			switchedToNewTab = true;
			waitForUiToLoad();
		}

		assertAnyVisibleText(headingOptions.toArray(new String[0]));

		final String legalBody = normalizedBodyText();
		assertTrue(
				"Legal content text was not visible.",
				legalBody.length() > 200
						|| legalBody.contains("terminos")
						|| legalBody.contains("condiciones")
						|| legalBody.contains("privacidad"));

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void selectGoogleAccountIfVisible() {
		final By accountLocator = By.xpath(
				"//*[contains(normalize-space(), " + escapeXpathLiteral(GOOGLE_ACCOUNT) + ")]");

		final List<WebElement> elements = driver.findElements(accountLocator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				clickAndWait(element);
				return;
			}
		}
	}

	private boolean hasAnyVisibleEmailOrLikelyUserName() {
		final List<WebElement> textNodes = driver.findElements(By.xpath("//*[normalize-space()]"));
		for (final WebElement textNode : textNodes) {
			if (!textNode.isDisplayed()) {
				continue;
			}
			final String text = normalize(textNode.getText());
			if (text.isEmpty()) {
				continue;
			}
			if (text.contains("@")) {
				return true;
			}
			if (text.matches("^[\\p{L} .'-]{3,}$")
					&& !text.contains("informacion general")
					&& !text.contains("detalles de la cuenta")
					&& !text.contains("tus negocios")
					&& !text.contains("seccion legal")
					&& !text.contains("business plan")
					&& !text.contains("cambiar plan")
					&& !text.contains("cuenta creada")
					&& !text.contains("estado activo")
					&& !text.contains("idioma seleccionado")) {
				return true;
			}
		}
		return false;
	}

	private boolean runStep(final String stepName, final Step step) throws Exception {
		try {
			step.execute();
			report.put(stepName, "PASS");
			return true;
		} catch (final Exception | AssertionError ex) {
			report.put(stepName, "FAIL - " + ex.getMessage());
			captureScreenshot("failure-" + stepName.toLowerCase(Locale.ROOT).replace(" ", "-"));
			return false;
		}
	}

	private void markBlocked(final String stepName) {
		if (report.containsKey(stepName) && report.get(stepName).startsWith("FAIL")) {
			return;
		}
		report.put(stepName, "FAIL - Blocked by a previous failed step");
	}

	private void initializeReport() {
		report.put("Login", "FAIL - Not executed");
		report.put("Mi Negocio menu", "FAIL - Not executed");
		report.put("Agregar Negocio modal", "FAIL - Not executed");
		report.put("Administrar Negocios view", "FAIL - Not executed");
		report.put("Información General", "FAIL - Not executed");
		report.put("Detalles de la Cuenta", "FAIL - Not executed");
		report.put("Tus Negocios", "FAIL - Not executed");
		report.put("Términos y Condiciones", "FAIL - Not executed");
		report.put("Política de Privacidad", "FAIL - Not executed");
	}

	private void assertAllRequiredStepsPassed() {
		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			if (!entry.getValue().startsWith("PASS")) {
				failed.add(entry.getKey() + " => " + entry.getValue());
			}
		}

		assertTrue("Workflow validation failed:\n" + String.join("\n", failed), failed.isEmpty());
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		sb.append("Screenshots: ").append(screenshotDir.toAbsolutePath()).append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("Final PASS/FAIL report").append(System.lineSeparator());
		sb.append("- Login: ").append(report.get("Login")).append(System.lineSeparator());
		sb.append("- Mi Negocio menu: ").append(report.get("Mi Negocio menu")).append(System.lineSeparator());
		sb.append("- Agregar Negocio modal: ").append(report.get("Agregar Negocio modal")).append(System.lineSeparator());
		sb.append("- Administrar Negocios view: ").append(report.get("Administrar Negocios view")).append(System.lineSeparator());
		sb.append("- Información General: ").append(report.get("Información General")).append(System.lineSeparator());
		sb.append("- Detalles de la Cuenta: ").append(report.get("Detalles de la Cuenta")).append(System.lineSeparator());
		sb.append("- Tus Negocios: ").append(report.get("Tus Negocios")).append(System.lineSeparator());
		sb.append("- Términos y Condiciones: ").append(report.get("Términos y Condiciones")).append(System.lineSeparator());
		sb.append("- Política de Privacidad: ").append(report.get("Política de Privacidad")).append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("Final URLs").append(System.lineSeparator());
		sb.append("- Terminos y Condiciones URL: ").append(termsUrl).append(System.lineSeparator());
		sb.append("- Politica de Privacidad URL: ").append(privacyUrl).append(System.lineSeparator());

		Files.writeString(reportPath, sb.toString());
		System.out.println(sb);
		System.out.println("Final report file: " + reportPath.toAbsolutePath());
	}

	private void clickByFirstVisibleText(final String... texts) {
		Exception lastError = null;
		for (final String text : texts) {
			try {
				wait.until(ExpectedConditions.presenceOfElementLocated(byExactVisibleText(text)));
				final List<WebElement> candidates = driver.findElements(byExactVisibleText(text));
				for (final WebElement candidate : candidates) {
					if (candidate.isDisplayed() && candidate.isEnabled()) {
						clickAndWait(candidate);
						return;
					}
				}
			} catch (final Exception ex) {
				lastError = ex;
			}
		}
		throw new AssertionError(
				"Could not click any element for visible texts: " + String.join(", ", texts),
				lastError);
	}

	private void assertAnyVisibleText(final String... texts) {
		for (final String text : texts) {
			if (isAnyVisibleTextPresent(text)) {
				return;
			}
		}
		throw new AssertionError("Expected visible text not found: " + String.join(" | ", texts));
	}

	private void assertBodyContainsAll(final String... words) {
		final String body = normalizedBodyText();
		for (final String word : words) {
			assertTrue("Expected body to contain text: " + word, body.contains(normalize(word)));
		}
	}

	private boolean isAnyVisibleTextPresent(final String text) {
		final List<WebElement> elements = driver.findElements(byAnyVisibleText(text));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void assertAnyVisible(final By... locators) {
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}
		throw new AssertionError("Expected at least one visible element for the provided locators.");
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.visibilityOf(element));
		new Actions(driver).moveToElement(element).pause(java.time.Duration.ofMillis(100)).perform();
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(
					String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"))));
		} catch (final Exception ignored) {
			// Some SPAs do not expose complete in every internal transition.
		}

		try {
			Thread.sleep(600);
		} catch (final InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String name) throws IOException {
		final Path target = screenshotDir.resolve(name + ".png");
		final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(target, bytes);
	}

	private void captureFullPageScreenshot(final String name) throws IOException {
		final Path target = screenshotDir.resolve(name + ".png");

		try {
			if (driver instanceof ChromeDriver) {
				final Map<String, Object> result = ((ChromeDriver) driver).executeCdpCommand(
						"Page.captureScreenshot",
						Map.of("format", "png", "captureBeyondViewport", true, "fromSurface", true));
				final String data = String.valueOf(result.get("data"));
				Files.write(target, Base64.getDecoder().decode(data));
				return;
			}
		} catch (final Exception ignored) {
			// Fall back to regular screenshot if CDP full-page capture is unavailable.
		}

		captureScreenshot(name);
	}

	private String normalizedBodyText() {
		final String body = driver.findElement(By.tagName("body")).getText();
		return normalize(body);
	}

	private String normalize(final String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT)
				replaceAll("\\s+", " ")
				.trim();
	}

	private By byExactVisibleText(final String text) {
		final String literal = escapeXpathLiteral(text);
		return By.xpath(
				"//button[normalize-space()=" + literal + "]"
						+ " | //a[normalize-space()=" + literal + "]"
						+ " | //*[@role='button' and normalize-space()=" + literal + "]"
						+ " | //span[normalize-space()=" + literal + "]/ancestor::*[self::button or self::a or @role='button'][1]"
						+ " | //*[normalize-space()=" + literal + "]");
	}

	private By byAnyVisibleText(final String text) {
		final String normalizedLiteral = escapeXpathLiteral(text);
		return By.xpath(
				"//*[contains(normalize-space(), " + normalizedLiteral + ")]"
						+ " | //*[@aria-label and contains(normalize-space(@aria-label), " + normalizedLiteral + ")]");
	}

	private String escapeXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(", \"'\", ");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	@FunctionalInterface
	private interface Step {
		void execute() throws Exception;
	}
}
