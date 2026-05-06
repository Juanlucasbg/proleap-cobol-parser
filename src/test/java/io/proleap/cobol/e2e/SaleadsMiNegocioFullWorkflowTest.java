package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(35);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepNotes = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private String appWindowHandle;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = readRequiredEnvironment("SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Skipping SaleADS workflow: set SALEADS_LOGIN_URL to run against any environment.",
				loginUrl != null);

		setupDriver(loginUrl);
		try {
			runStep("Login", this::loginWithGoogleAndValidateDashboard);
			runStep("Mi Negocio menu", this::openMiNegocioMenuAndValidate);
			runStep("Agregar Negocio modal", this::openAgregarNegocioModalAndValidate);
			runStep("Administrar Negocios view", this::openAdministrarNegociosAndValidate);
			runStep("Información General", this::validateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
			runStep("Tus Negocios", this::validateTusNegocios);
			runStep("Términos y Condiciones", this::openAndValidateTerminos);
			runStep("Política de Privacidad", this::openAndValidatePrivacidad);
		} finally {
			writeFinalReport();
			if (driver != null) {
				driver.quit();
			}
		}

		final List<String> failed = new ArrayList<>();
		for (Map.Entry<String, Boolean> entry : stepResults.entrySet()) {
			if (!entry.getValue().booleanValue()) {
				failed.add(entry.getKey() + " -> " + stepNotes.getOrDefault(entry.getKey(), "No details."));
			}
		}
		assertTrue("SaleADS workflow has failures:\n" + String.join("\n", failed), failed.isEmpty());
	}

	private void setupDriver(final String loginUrl) throws IOException {
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);

		evidenceDirectory = Files.createDirectories(Path.of(
				"target",
				"saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));

		driver.get(loginUrl);
		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	private void loginWithGoogleAndValidateDashboard() throws IOException {
		// Some environments may keep prior sessions active; treat visible sidebar as already logged in.
		if (!isAnyTextVisible(Duration.ofSeconds(3), "Sign in with Google", "Iniciar sesión con Google", "Login with Google")
				&& isAnyTextVisible(Duration.ofSeconds(5), "Negocio", "Mi Negocio")) {
			captureScreenshot("01-dashboard-loaded.png");
			return;
		}

		clickByVisibleText(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Iniciar sesion con Google",
				"Continuar con Google",
				"Login with Google");

		waitForUiLoad();
		resolveGoogleAccountSelector();
		waitForUiLoad();

		waitForAnyVisibleText(
				Duration.ofSeconds(40),
				"Negocio",
				"Mi Negocio",
				"Administrar Negocios");
		if (!isAnyTextVisible(Duration.ofSeconds(10), "Negocio", "Mi Negocio")) {
			throw new IllegalStateException("Left sidebar navigation is not visible.");
		}
		captureScreenshot("01-dashboard-loaded.png");
	}

	private void openMiNegocioMenuAndValidate() throws IOException {
		expandMiNegocioMenu();
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void openAgregarNegocioModalAndValidate() throws IOException {
		expandMiNegocioMenu();
		clickByVisibleText("Agregar Negocio");

		assertAnyVisibleText("Crear Nuevo Negocio");
		assertFieldPresent("Nombre del Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
		assertAnyVisibleText("Cancelar");
		assertAnyVisibleText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal.png");

		typeIntoField("Nombre del Negocio", "Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
	}

	private void openAdministrarNegociosAndValidate() throws IOException {
		expandMiNegocioMenu();
		clickByVisibleText("Administrar Negocios");

		assertAnyVisibleText("Información General", "Informacion General");
		assertAnyVisibleText("Detalles de la Cuenta", "Detalles de la Cuenta");
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Sección Legal", "Seccion Legal");
		captureFullPageScreenshot("04-administrar-negocios.png");
	}

	private void validateInformacionGeneral() {
		assertAnyVisibleText("Información General", "Informacion General");
		assertAnyVisibleText("BUSINESS PLAN");
		assertAnyVisibleText("Cambiar Plan");
		assertEmailVisible();
		assertLikelyUserNameVisible();
	}

	private void validateDetallesDeLaCuenta() {
		assertAnyVisibleText("Cuenta creada");
		assertAnyVisibleText("Estado activo", "Estado Activo");
		assertAnyVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");

		final List<WebElement> businessRows = driver.findElements(By.xpath(
				"//*[contains(@class,'business') or contains(@class,'negocio') or @role='row' or self::li]"));
		if (businessRows.isEmpty() && !getBodyText().contains("Negocio")) {
			throw new IllegalStateException("Business list is not visible.");
		}
	}

	private void openAndValidateTerminos() throws IOException {
		final String url = openLegalLinkAndValidate(
				"Términos y Condiciones",
				"Terminos y Condiciones",
				"05-terminos-y-condiciones.png",
				Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"));
		legalUrls.put("Términos y Condiciones", url);
	}

	private void openAndValidatePrivacidad() throws IOException {
		final String url = openLegalLinkAndValidate(
				"Política de Privacidad",
				"Politica de Privacidad",
				"06-politica-de-privacidad.png",
				Arrays.asList("Política de Privacidad", "Politica de Privacidad"));
		legalUrls.put("Política de Privacidad", url);
	}

	private String openLegalLinkAndValidate(
			final String primaryLinkText,
			final String fallbackLinkText,
			final String screenshotName,
			final List<String> expectedHeadingCandidates) throws IOException {

		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String currentUrl = driver.getCurrentUrl();

		clickByVisibleText(primaryLinkText, fallbackLinkText);

		String targetHandle = appWindowHandle;
		final Set<String> handlesAfter = waitForHandlesChange(handlesBefore, Duration.ofSeconds(15));
		if (handlesAfter.size() > handlesBefore.size()) {
			for (String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					targetHandle = handle;
					break;
				}
			}
		}

		driver.switchTo().window(targetHandle);
		waitForUiLoad();
		waitForAnyVisibleText(Duration.ofSeconds(20), expectedHeadingCandidates.toArray(new String[0]));

		final String bodyText = getBodyText();
		assertTrue("Legal content text is not visible.", bodyText != null && bodyText.trim().length() > 120);
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (!targetHandle.equals(appWindowHandle)) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else if (!currentUrl.equals(finalUrl)) {
			driver.navigate().back();
		}
		waitForUiLoad();
		return finalUrl;
	}

	private void resolveGoogleAccountSelector() {
		final Set<String> initialHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final Set<String> possibleHandles = waitForHandlesChange(initialHandles, Duration.ofSeconds(10));
		if (possibleHandles.size() > initialHandles.size()) {
			for (String handle : possibleHandles) {
				if (!initialHandles.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		if (isAnyTextVisible(Duration.ofSeconds(8), "Choose an account", "Elige una cuenta", GOOGLE_ACCOUNT_EMAIL)) {
			clickByVisibleText(GOOGLE_ACCOUNT_EMAIL);
			waitForUiLoad();
		}

		try {
			if (!driver.getWindowHandle().equals(appWindowHandle) && driver.getWindowHandles().contains(appWindowHandle)) {
				driver.switchTo().window(appWindowHandle);
			}
		} catch (NoSuchWindowException ignored) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private void expandMiNegocioMenu() {
		if (isAnyTextVisible(Duration.ofSeconds(3), "Agregar Negocio", "Administrar Negocios")) {
			return;
		}

		if (isAnyTextVisible(Duration.ofSeconds(5), "Negocio")) {
			clickByVisibleText("Negocio");
		}
		clickByVisibleText("Mi Negocio");
		waitForUiLoad();
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, Boolean.TRUE);
			stepNotes.put(stepName, "PASS");
		} catch (Throwable ex) {
			stepResults.put(stepName, Boolean.FALSE);
			stepNotes.put(stepName, ex.getMessage());
			try {
				captureScreenshot("failure-" + sanitize(stepName) + ".png");
			} catch (Exception ignored) {
				// ignore screenshot failures while already failing the step
			}
		}
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDirectory == null) {
			return;
		}

		final StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
		report.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator()).append(System.lineSeparator());

		final List<String> fields = Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad");

		for (String field : fields) {
			final boolean pass = stepResults.getOrDefault(field, Boolean.FALSE);
			report.append(field).append(": ").append(pass ? "PASS" : "FAIL");
			final String note = stepNotes.get(field);
			if (note != null && !"PASS".equals(note)) {
				report.append(" - ").append(note);
			}
			report.append(System.lineSeparator());
		}

		report.append(System.lineSeparator());
		report.append("Captured URLs:").append(System.lineSeparator());
		for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
			report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}

		Files.writeString(evidenceDirectory.resolve("final-report.txt"), report.toString());
	}

	private String readRequiredEnvironment(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return value.trim();
	}

	private void clickByVisibleText(final String... textCandidates) {
		Exception lastFailure = null;
		for (String text : textCandidates) {
			try {
				final WebElement element = wait.until(driver -> findFirstVisibleElementByText(driver, text));
				scrollIntoView(element);
				element.click();
				waitForUiLoad();
				return;
			} catch (Exception ex) {
				lastFailure = ex;
			}
		}
		throw new IllegalStateException("Could not click any text: " + Arrays.toString(textCandidates), lastFailure);
	}

	private void assertAnyVisibleText(final String... candidates) {
		if (!isAnyTextVisible(Duration.ofSeconds(10), candidates)) {
			throw new IllegalStateException("Expected text not visible: " + Arrays.toString(candidates));
		}
	}

	private boolean isAnyTextVisible(final Duration timeout, final String... textCandidates) {
		try {
			waitForAnyVisibleText(timeout, textCandidates);
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	private void waitForAnyVisibleText(final Duration timeout, final String... textCandidates) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		localWait.until((ExpectedCondition<Boolean>) d -> {
			for (String text : textCandidates) {
				if (findFirstVisibleElementByText(d, text) != null) {
					return true;
				}
			}
			return false;
		});
	}

	private WebElement findFirstVisibleElementByText(final WebDriver webDriver, final String textCandidate) {
		final String escaped = escapeXPathLiteral(textCandidate);
		final List<WebElement> elements = webDriver.findElements(By.xpath(
				"//*[normalize-space() = " + escaped + " or contains(normalize-space(), " + escaped + ")]"));
		for (WebElement element : elements) {
			if (element.isDisplayed()) {
				return element;
			}
		}

		final String normalizedCandidate = normalizeForMatching(textCandidate);
		final List<WebElement> all = webDriver.findElements(By.xpath("//*"));
		for (WebElement element : all) {
			if (!element.isDisplayed()) {
				continue;
			}
			final String text = normalizeForMatching(element.getText());
			if (!text.isEmpty() && text.contains(normalizedCandidate)) {
				return element;
			}
		}
		return null;
	}

	private void typeIntoField(final String label, final String value) {
		final String escaped = escapeXPathLiteral(label);
		final List<WebElement> candidates = new ArrayList<>();
		candidates.addAll(driver.findElements(By.xpath("//label[normalize-space() = " + escaped + "]/following::input[1]")));
		candidates.addAll(driver.findElements(By.xpath("//input[@placeholder = " + escaped + "]")));
		candidates.addAll(driver.findElements(By.xpath("//input[contains(@aria-label, " + escaped + ")]")));

		for (WebElement input : candidates) {
			if (input.isDisplayed()) {
				scrollIntoView(input);
				input.clear();
				input.sendKeys(value);
				waitForUiLoad();
				return;
			}
		}

		throw new IllegalStateException("Input field not found for label: " + label);
	}

	private void assertFieldPresent(final String label) {
		final String escaped = escapeXPathLiteral(label);
		final List<WebElement> fields = new ArrayList<>();
		fields.addAll(driver.findElements(By.xpath("//label[normalize-space() = " + escaped + "]")));
		fields.addAll(driver.findElements(By.xpath("//input[@placeholder = " + escaped + "]")));
		fields.addAll(driver.findElements(By.xpath("//input[contains(@aria-label, " + escaped + ")]")));

		for (WebElement field : fields) {
			if (field.isDisplayed()) {
				return;
			}
		}
		throw new IllegalStateException("Field not found for label: " + label);
	}

	private void assertEmailVisible() {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[contains(normalize-space(), '@')]"));
		for (WebElement element : elements) {
			if (element.isDisplayed() && EMAIL_PATTERN.matcher(element.getText()).find()) {
				return;
			}
		}
		throw new IllegalStateException("User email is not visible.");
	}

	private void assertLikelyUserNameVisible() {
		final String bodyText = getBodyText();
		for (String line : bodyText.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			final String normalized = normalizeForMatching(trimmed);
			if (normalized.contains("informacion general")
					|| normalized.contains("business plan")
					|| normalized.contains("cambiar plan")
					|| normalized.contains("cuenta creada")
					|| normalized.contains("estado activo")
					|| normalized.contains("idioma seleccionado")
					|| EMAIL_PATTERN.matcher(trimmed).find()) {
				continue;
			}
			if (trimmed.split("\\s+").length >= 2) {
				return;
			}
		}
		throw new IllegalStateException("Could not identify a visible user name.");
	}

	private Set<String> waitForHandlesChange(final Set<String> before, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		try {
			return localWait.until(d -> {
				final Set<String> now = d.getWindowHandles();
				return !now.equals(before) ? now : null;
			});
		} catch (TimeoutException timeoutException) {
			return before;
		}
	}

	private void waitForUiLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		try {
			Thread.sleep(500);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), evidenceDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureFullPageScreenshot(final String fileName) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			final Number width = (Number) js.executeScript(
					"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, 1280);");
			final Number height = (Number) js.executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, 1080);");
			driver.manage().window().setSize(new Dimension(width.intValue(), Math.min(height.intValue(), 5000)));
			waitForUiLoad();
			captureScreenshot(fileName);
		} finally {
			driver.manage().window().setSize(originalSize);
		}
	}

	private String getBodyText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private String escapeXPathLiteral(final String input) {
		if (!input.contains("'")) {
			return "'" + input + "'";
		}
		if (!input.contains("\"")) {
			return "\"" + input + "\"";
		}
		final StringBuilder concat = new StringBuilder("concat(");
		final char[] chars = input.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				concat.append(", ");
			}
			if (chars[i] == '\'') {
				concat.append("\"'\"");
			} else {
				concat.append("'").append(chars[i]).append("'");
			}
		}
		concat.append(")");
		return concat.toString();
	}

	private String normalizeForMatching(final String text) {
		final String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT)
				.trim();
		return normalized;
	}

	private String sanitize(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
