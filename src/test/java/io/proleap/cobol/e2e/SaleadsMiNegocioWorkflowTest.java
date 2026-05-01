package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;

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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assume;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_WAIT_TIMEOUT = Duration.ofSeconds(6);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";

	private final Map<String, String> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue(
				"Enable with -Dsaleads.e2e.enabled=true",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));

		if (headless) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--lang=es-ES");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
		evidenceDir = createEvidenceDirectory();

		final String loginUrl = firstNonBlank(
				System.getProperty("saleads.login.url"),
				System.getenv("SALEADS_LOGIN_URL"));

		if (loginUrl != null) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		initializeFinalReportFields();
		try {
			runStep("Login", this::stepLoginWithGoogle);
			runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			runStep("Información General", this::stepValidateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			runStep("Tus Negocios", this::stepValidateTusNegocios);
			runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
			runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);
		} finally {
			printFinalReport();
		}

		assertFalse("One or more Mi Negocio workflow validations failed.", stepResults.containsValue("FAIL"));
	}

	private void stepLoginWithGoogle() throws IOException {
		final Set<String> handlesBeforeLoginClick = driver.getWindowHandles();
		final WebElement loginButton = waitForAnyVisibleText(
				"Sign in with Google",
				"Iniciar con Google",
				"Continuar con Google",
				"Google");
		clickAndWait(loginButton);
		switchToNewWindowIfOpened(handlesBeforeLoginClick, SHORT_WAIT_TIMEOUT);

		selectGoogleAccountIfShown();
		switchToWindowContainingAnyText(WAIT_TIMEOUT, "Negocio", "Mi Negocio");
		waitForMainApplication();
		takeScreenshot("01_dashboard_loaded");

		appWindowHandle = driver.getWindowHandle();
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		waitForAnyVisibleText("Negocio");

		clickAndWait(waitForClickableText("Mi Negocio"));
		waitForAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Administrar Negocios");

		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickAndWait(waitForClickableText("Agregar Negocio"));

		waitForAnyVisibleText("Crear Nuevo Negocio");
		waitForAnyVisibleText("Nombre del Negocio");
		waitForAnyVisibleText("Tienes 2 de 3 negocios");
		waitForAnyVisibleText("Cancelar");
		waitForAnyVisibleText("Crear Negocio");

		takeScreenshot("03_agregar_negocio_modal");

		final WebElement negocioInput = waitForVisible(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='businessName' or @id='businessName' or contains(@aria-label,'Nombre del Negocio')]"));
		negocioInput.click();
		negocioInput.clear();
		negocioInput.sendKeys(TEST_BUSINESS_NAME);
		clickAndWait(waitForClickableText("Cancelar"));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureMiNegocioExpanded();
		clickAndWait(waitForClickableText("Administrar Negocios"));

		waitForAnyVisibleText("Informacion General", "Información General");
		waitForAnyVisibleText("Detalles de la Cuenta");
		waitForAnyVisibleText("Tus Negocios");
		waitForAnyVisibleText("Seccion Legal", "Sección Legal");

		takeFullPageScreenshot("04_administrar_negocios_page_full");
	}

	private void stepValidateInformacionGeneral() {
		waitForAnyVisibleText("BUSINESS PLAN");
		waitForAnyVisibleText("Cambiar Plan");

		final String pageText = normalizedPageText();
		ensureContainsAny(pageText, "business plan");
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		ensureLikelyEmailPresent(bodyText);
		ensureLikelyUserNamePresent(bodyText);
	}

	private void stepValidateDetallesCuenta() {
		waitForAnyVisibleText("Cuenta creada");
		waitForAnyVisibleText("Estado activo");
		waitForAnyVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitForAnyVisibleText("Tus Negocios");
		waitForAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08_terminos_y_condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		validateLegalLink("Política de Privacidad", "Política de Privacidad", "09_politica_de_privacidad");
	}

	private void validateLegalLink(final String resultKey, final String linkText, final String screenshotName)
			throws IOException {
		final String beforeHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String currentUrlBefore = driver.getCurrentUrl();

		clickAndWait(waitForClickableText(linkText));

		String activeHandle = beforeHandle;
		try {
			wait.until(d -> d.getWindowHandles().size() > handlesBefore.size() || !Objects.equals(d.getCurrentUrl(), currentUrlBefore));
		} catch (final TimeoutException e) {
			throw new AssertionError("Legal link did not navigate or open a new tab: " + linkText, e);
		}

		final Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > handlesBefore.size()) {
			for (final String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					activeHandle = handle;
					break;
				}
			}
			driver.switchTo().window(activeHandle);
			waitForUiToLoad();
		}

		waitForAnyVisibleText(linkText);
		final String legalText = normalizedPageText();
		ensureLegalContentPresent(legalText);

		takeScreenshot(screenshotName);
		legalUrls.put(resultKey, driver.getCurrentUrl());

		if (!beforeHandle.equals(activeHandle) && driver.getWindowHandles().contains(activeHandle)) {
			driver.close();
		}
		final String appHandle = appWindowHandle != null ? appWindowHandle : beforeHandle;
		if (driver.getWindowHandles().contains(appHandle)) {
			driver.switchTo().window(appHandle);
		} else {
			driver.switchTo().window(beforeHandle);
		}
		if (beforeHandle.equals(activeHandle)) {
			driver.navigate().back();
		}
		waitForUiToLoad();
	}

	private void runStep(final String stepName, final StepAction action) throws Exception {
		try {
			action.run();
			stepResults.put(stepName, "PASS");
		} catch (final Throwable t) {
			stepResults.put(stepName, "FAIL");
			safeFailureScreenshot(stepName);
			throw t;
		}
	}

	private void initializeFinalReportFields() {
		stepResults.put("Login", "FAIL");
		stepResults.put("Mi Negocio menu", "FAIL");
		stepResults.put("Agregar Negocio modal", "FAIL");
		stepResults.put("Administrar Negocios view", "FAIL");
		stepResults.put("Información General", "FAIL");
		stepResults.put("Detalles de la Cuenta", "FAIL");
		stepResults.put("Tus Negocios", "FAIL");
		stepResults.put("Términos y Condiciones", "FAIL");
		stepResults.put("Política de Privacidad", "FAIL");
	}

	private void ensureMiNegocioExpanded() {
		if (isTextVisibleNow("Administrar Negocios")) {
			return;
		}
		clickAndWait(waitForClickableText("Mi Negocio"));
		waitForAnyVisibleText("Administrar Negocios");
	}

	private void selectGoogleAccountIfShown() {
		final List<String> chooserIndicators = Arrays.asList("Selecciona una cuenta", "Choose an account");
		if (!isAnyTextVisible(chooserIndicators, SHORT_WAIT_TIMEOUT)) {
			return;
		}

		final By accountBy = By.xpath("//*[contains(normalize-space(.), " + xPathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]");
		final WebElement account = new WebDriverWait(driver, WAIT_TIMEOUT)
				.until(ExpectedConditions.elementToBeClickable(accountBy));
		account.click();
		waitForUiToLoad();
	}

	private void waitForMainApplication() {
		waitForAnyVisibleText("Negocio");
		ensureSidebarVisible();
		wait.until(d -> {
			final JavascriptExecutor js = (JavascriptExecutor) d;
			final Object width = js.executeScript("return window.innerWidth;");
			return width instanceof Number && ((Number) width).intValue() > 0;
		});
	}

	private void ensureSidebarVisible() {
		final List<WebElement> sidebarCandidates = driver.findElements(By.xpath("//aside | //nav"));
		for (final WebElement candidate : sidebarCandidates) {
			if (candidate.isDisplayed()) {
				return;
			}
		}
		throw new AssertionError("Expected left sidebar navigation to be visible after login.");
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiToLoad();
	}

	private WebElement waitForClickableText(final String text) {
		final By locator = By.xpath(
				"//*[self::a or self::button or @role='button' or self::span or self::div][contains(normalize-space(.), "
						+ xPathLiteral(text)
						+ ")]");
		return wait.until(ExpectedConditions.elementToBeClickable(locator));
	}

	private WebElement waitForAnyVisibleText(final String... texts) {
		for (final String text : texts) {
			try {
				return waitForVisible(By.xpath("//*[contains(normalize-space(.), " + xPathLiteral(text) + ")]"));
			} catch (final TimeoutException ignored) {
				// Try next text variant.
			}
		}

		throw new AssertionError("None of the expected texts were visible: " + Arrays.toString(texts));
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void waitForUiToLoad() {
		wait.until(d -> {
			try {
				return "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState"));
			} catch (final Exception e) {
				return true;
			}
		});
	}

	private boolean isTextVisibleNow(final String text) {
		try {
			final WebElement element = new WebDriverWait(driver, SHORT_WAIT_TIMEOUT)
					.until(ExpectedConditions.visibilityOfElementLocated(
							By.xpath("//*[contains(normalize-space(.), " + xPathLiteral(text) + ")]")));
			return element.isDisplayed();
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private boolean isAnyTextVisible(final List<String> texts, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(d -> {
				for (final String text : texts) {
					final List<WebElement> elements = d.findElements(
							By.xpath("//*[contains(normalize-space(.), " + xPathLiteral(text) + ")]"));
					for (final WebElement element : elements) {
						if (element.isDisplayed()) {
							return true;
						}
					}
				}
				return false;
			});
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private Path createEvidenceDirectory() throws IOException {
		final String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path directory = Path.of("target", "saleads-evidence", stamp);
		Files.createDirectories(directory);
		return directory;
	}

	private void switchToNewWindowIfOpened(final Set<String> handlesBefore, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(d -> d.getWindowHandles().size() > handlesBefore.size());
		} catch (final TimeoutException ignored) {
			return;
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void switchToWindowContainingAnyText(final Duration timeout, final String... texts) {
		final List<String> expectedTexts = Arrays.asList(texts);
		new WebDriverWait(driver, timeout).until(d -> {
			for (final String handle : d.getWindowHandles()) {
				d.switchTo().window(handle);
				if (isAnyTextVisible(expectedTexts, SHORT_WAIT_TIMEOUT)) {
					return true;
				}
			}
			return false;
		});
	}

	private void takeScreenshot(final String name) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(name + ".png");
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private void takeFullPageScreenshot(final String name) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final Long bodyScrollWidth = toLong(((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);"));
			final Long bodyScrollHeight = toLong(((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);"));

			final int targetWidth = Math.max(originalSize.getWidth(), bodyScrollWidth != null ? bodyScrollWidth.intValue() : originalSize.getWidth());
			final int targetHeight = Math.max(originalSize.getHeight(), bodyScrollHeight != null ? bodyScrollHeight.intValue() : originalSize.getHeight());

			driver.manage().window().setSize(new Dimension(targetWidth, targetHeight));
			waitForUiToLoad();
			takeScreenshot(name);
		} catch (final Exception e) {
			takeScreenshot(name);
		} finally {
			try {
				driver.manage().window().setSize(originalSize);
				waitForUiToLoad();
			} catch (final Exception ignored) {
				// Best effort to restore viewport.
			}
		}
	}

	private void safeFailureScreenshot(final String stepName) {
		try {
			takeScreenshot("failure_" + normalizeName(stepName));
		} catch (final Exception ignored) {
			// Best effort evidence capture.
		}
	}

	private String normalizedPageText() {
		return driver.findElement(By.tagName("body")).getText().toLowerCase(Locale.ROOT);
	}

	private void ensureContainsAny(final String haystack, final String... values) {
		for (final String value : values) {
			if (haystack.contains(value.toLowerCase(Locale.ROOT))) {
				return;
			}
		}
		throw new AssertionError("Expected any of " + Arrays.toString(values) + " in current page text.");
	}

	private void ensureLikelyEmailPresent(final String bodyText) {
		if (bodyText.contains("@")) {
			return;
		}
		throw new AssertionError("Expected user email to be visible in Información General.");
	}

	private void ensureLikelyUserNamePresent(final String bodyText) {
		final String[] lines = bodyText.split("\\R");
		int emailIndex = -1;
		for (int i = 0; i < lines.length; i++) {
			if (lines[i].contains("@")) {
				emailIndex = i;
				break;
			}
		}

		if (emailIndex <= 0) {
			throw new AssertionError("Could not infer user name because no email line was detected.");
		}

		for (int i = emailIndex - 1; i >= 0; i--) {
			final String line = lines[i].trim();
			final String normalized = line.toLowerCase(Locale.ROOT);
			if (line.isEmpty()) {
				continue;
			}
			if (normalized.contains("informacion general")
					|| normalized.contains("información general")
					|| normalized.contains("business plan")
					|| normalized.contains("cambiar plan")) {
				continue;
			}
			if (line.length() >= 3 && !line.contains("@")) {
				return;
			}
		}

		throw new AssertionError("Expected user name text to be visible above the user email.");
	}

	private void ensureLegalContentPresent(final String legalPageText) {
		final boolean hasMinimumContent = legalPageText.length() > 400;
		final boolean hasLegalMarkers = legalPageText.contains("privacidad")
				|| legalPageText.contains("terminos")
				|| legalPageText.contains("condiciones")
				|| legalPageText.contains("datos personales");
		if (!(hasMinimumContent && hasLegalMarkers)) {
			throw new AssertionError("Legal content did not look complete enough.");
		}
	}

	private void printFinalReport() {
		final StringBuilder sb = new StringBuilder();
		sb.append("\n======== SaleADS Mi Negocio Workflow Report ========\n");
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		for (final Map.Entry<String, String> entry : stepResults.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}
		if (!legalUrls.isEmpty()) {
			sb.append("Legal URLs:\n");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				sb.append("  * ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}
		sb.append("====================================================\n");
		System.out.println(sb);
	}

	private String normalizeName(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private Long toLong(final Object value) {
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		return null;
	}

	private String xPathLiteral(final String value) {
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
	private interface StepAction {
		void run() throws Exception;
	}
}
