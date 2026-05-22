package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter DIR_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final List<String> LOGIN_BUTTON_TEXTS = Arrays.asList("Sign in with Google", "Iniciar sesión con Google",
			"Inicia sesión con Google", "Continuar con Google", "Acceder con Google", "Login with Google");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, String> report = new LinkedHashMap<>();
	private String termsUrl = "";
	private String privacyUrl = "";

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue(
				"Set SALEADS_E2E_ENABLED=true to run this environment-driven test.",
				"true".equalsIgnoreCase(env("SALEADS_E2E_ENABLED").orElse("false")));

		evidenceDir = Path.of("target", "saleads-evidence", DIR_TS.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		if (!"false".equalsIgnoreCase(env("SALEADS_HEADLESS").orElse("true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(20));

		env("SALEADS_LOGIN_URL").ifPresent(driver::get);
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
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

		final List<String> failed = report.entrySet().stream().filter(entry -> entry.getValue().startsWith("FAIL"))
				.map(entry -> entry.getKey() + ": " + entry.getValue()).collect(Collectors.toList());

		assertTrue("SaleADS Mi Negocio workflow failed.\n" + formatReport() + "\nTerms URL: " + termsUrl
				+ "\nPrivacy URL: " + privacyUrl, failed.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		ensureLoginPageIsOpen();

		final WebElement loginButton = firstVisibleElementByExactOrContainsText(LOGIN_BUTTON_TEXTS, Duration.ofSeconds(15))
				.orElseThrow(() -> new AssertionError("Google login button was not found by visible text."));
		clickAndWait(loginButton);

		waitForGoogleFlowAndAppLoad();
		assertMainInterface();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		assertMainInterface();
		clickByAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio"), Duration.ofSeconds(12));
		waitForUiLoad();
		ensureVisibleText("Agregar Negocio", Duration.ofSeconds(10));
		ensureVisibleText("Administrar Negocios", Duration.ofSeconds(10));
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByAnyVisibleText(Arrays.asList("Agregar Negocio"), Duration.ofSeconds(12));
		ensureVisibleText("Crear Nuevo Negocio", Duration.ofSeconds(12));
		ensureVisibleText("Nombre del Negocio", Duration.ofSeconds(10));
		ensureVisibleText("Tienes 2 de 3 negocios", Duration.ofSeconds(10));
		ensureVisibleText("Cancelar", Duration.ofSeconds(10));
		ensureVisibleText("Crear Negocio", Duration.ofSeconds(10));

		// Optional interaction requested in the workflow.
		typeIntoFieldNearLabel("Nombre del Negocio", "Negocio Prueba Automatización");
		captureScreenshot("03-agregar-negocio-modal");
		clickByAnyVisibleText(Arrays.asList("Cancelar"), Duration.ofSeconds(8));
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios")) {
			clickByAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio"), Duration.ofSeconds(10));
			waitForUiLoad();
		}

		clickByAnyVisibleText(Arrays.asList("Administrar Negocios"), Duration.ofSeconds(12));
		ensureVisibleText("Información General", Duration.ofSeconds(20));
		ensureVisibleText("Detalles de la Cuenta", Duration.ofSeconds(20));
		ensureVisibleText("Tus Negocios", Duration.ofSeconds(20));
		ensureAnyVisibleText(Arrays.asList("Sección Legal", "Seccion Legal"), Duration.ofSeconds(20));
		captureScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		ensureVisibleText("Información General", Duration.ofSeconds(10));
		assertTrue("Expected a visible user email in Información General.",
				hasVisibleElement(By.xpath("//*[contains(normalize-space(), '@')]")));
		ensureAnyVisibleText(Arrays.asList("BUSINESS PLAN"), Duration.ofSeconds(10));
		ensureVisibleText("Cambiar Plan", Duration.ofSeconds(10));
	}

	private void stepValidateDetallesCuenta() {
		ensureVisibleText("Detalles de la Cuenta", Duration.ofSeconds(10));
		ensureVisibleText("Cuenta creada", Duration.ofSeconds(10));
		ensureAnyVisibleText(Arrays.asList("Estado activo", "Estado Activo"), Duration.ofSeconds(10));
		ensureAnyVisibleText(Arrays.asList("Idioma seleccionado", "Idioma Seleccionado"), Duration.ofSeconds(10));
	}

	private void stepValidateTusNegocios() {
		ensureVisibleText("Tus Negocios", Duration.ofSeconds(10));
		ensureVisibleText("Agregar Negocio", Duration.ofSeconds(10));
		ensureVisibleText("Tienes 2 de 3 negocios", Duration.ofSeconds(10));
		assertTrue("Expected at least one business card or list item in Tus Negocios.",
				hasVisibleElement(By.xpath(
						"//*[self::ul or self::ol or contains(@class,'list') or contains(@class,'card')][.//*[contains(normalize-space(),'Negocio') or contains(normalize-space(),'negocio')]]")));
	}

	private void stepValidateTerminos() throws Exception {
		termsUrl = validateLegalLink("Términos y Condiciones", Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
				"08-terminos");
	}

	private void stepValidatePrivacidad() throws Exception {
		privacyUrl = validateLegalLink("Política de Privacidad", Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
				"09-privacidad");
	}

	private String validateLegalLink(final String linkText, final List<String> expectedHeadings, final String screenshotPrefix)
			throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickByAnyVisibleText(Arrays.asList(linkText), Duration.ofSeconds(12));

		final String destinationHandle = waitForNavigationOrNewTab(beforeHandles, appWindow);
		driver.switchTo().window(destinationHandle);
		waitForUiLoad();

		firstVisibleElementByExactOrContainsText(expectedHeadings, Duration.ofSeconds(20))
				.orElseThrow(() -> new AssertionError("Expected legal heading was not visible for: " + linkText));
		assertTrue("Expected legal content text on page for " + linkText + ".",
				hasVisibleElement(By.xpath("//p[string-length(normalize-space()) > 40]")));

		final String finalUrl = driver.getCurrentUrl();
		captureScreenshot(screenshotPrefix + "-legal-page");

		if (!destinationHandle.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
		return finalUrl;
	}

	private String waitForNavigationOrNewTab(final Set<String> beforeHandles, final String fallbackHandle) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(15));
		try {
			shortWait.until(d -> d.getWindowHandles().size() > beforeHandles.size() || !d.getCurrentUrl().equals("about:blank"));
		} catch (final TimeoutException ignored) {
			// Continue with fallback behavior; this method still validates content afterwards.
		}

		final Set<String> afterHandles = new LinkedHashSet<>(driver.getWindowHandles());
		afterHandles.removeAll(beforeHandles);
		if (!afterHandles.isEmpty()) {
			return afterHandles.iterator().next();
		}
		return fallbackHandle;
	}

	private void waitForGoogleFlowAndAppLoad() {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(90).toMillis();
		String lastState = "Login not completed yet.";

		while (System.currentTimeMillis() < deadline) {
			final List<String> handles = new ArrayList<>(driver.getWindowHandles());
			for (final String handle : handles) {
				driver.switchTo().window(handle);
				waitForUiLoad();

				if (isTextVisible(GOOGLE_ACCOUNT_EMAIL)) {
					lastState = "Google account chooser found.";
					clickByAnyVisibleText(Arrays.asList(GOOGLE_ACCOUNT_EMAIL), Duration.ofSeconds(6));
					waitForUiLoad();
				}

				if (isMainInterfaceVisible()) {
					return;
				}
			}

			try {
				Thread.sleep(1000);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Interrupted while waiting for Google login flow.");
			}
		}

		throw new AssertionError("Main application interface did not load after Google login. Last state: " + lastState);
	}

	private void ensureLoginPageIsOpen() {
		final String currentUrl = driver.getCurrentUrl();
		if (!"about:blank".equals(currentUrl)) {
			return;
		}

		final String loginUrl = env("SALEADS_LOGIN_URL")
				.orElseThrow(() -> new AssertionError(
						"Browser is on about:blank. Provide SALEADS_LOGIN_URL or pre-open the SaleADS login page."));
		driver.get(loginUrl);
		waitForUiLoad();
	}

	private void assertMainInterface() {
		assertTrue("Main application interface was not visible after login.", isMainInterfaceVisible());
		assertTrue("Left sidebar navigation was not visible.",
				hasVisibleElement(By.xpath("//aside | //nav | //*[@role='navigation']")));
	}

	private boolean isMainInterfaceVisible() {
		return hasVisibleAnyText(Arrays.asList("Negocio", "Mi Negocio"), Duration.ofSeconds(2));
	}

	private void clickByAnyVisibleText(final List<String> texts, final Duration timeout) {
		final WebElement element = firstVisibleElementByExactOrContainsText(texts, timeout)
				.orElseThrow(() -> new AssertionError("Could not find clickable element with texts: " + texts));
		clickAndWait(element);
	}

	private Optional<WebElement> firstVisibleElementByExactOrContainsText(final List<String> texts, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		for (final String text : texts) {
			final List<By> candidates = Arrays.asList(
					By.xpath("//*[normalize-space()='" + text + "']"),
					By.xpath("//*[contains(normalize-space(),'" + text + "')]"));
			for (final By candidate : candidates) {
				try {
					final WebElement element = localWait.until(ExpectedConditions.visibilityOfElementLocated(candidate));
					return Optional.of(element);
				} catch (final TimeoutException ignored) {
					// Try next candidate.
				}
			}
		}
		return Optional.empty();
	}

	private void clickAndWait(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiLoad();
	}

	private void waitForUiLoad() {
		final ExpectedCondition<Boolean> readyStateComplete = webDriver -> "complete".equals(
				((JavascriptExecutor) webDriver).executeScript("return document.readyState"));
		wait.until(readyStateComplete);
		try {
			Thread.sleep(400);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean hasVisibleElement(final By by) {
		try {
			return !driver.findElements(by).stream().filter(WebElement::isDisplayed).collect(Collectors.toList()).isEmpty();
		} catch (final NoSuchElementException ex) {
			return false;
		}
	}

	private boolean hasVisibleAnyText(final List<String> texts, final Duration timeout) {
		return firstVisibleElementByExactOrContainsText(texts, timeout).isPresent();
	}

	private boolean isTextVisible(final String text) {
		return hasVisibleAnyText(Arrays.asList(text), Duration.ofSeconds(2));
	}

	private void ensureVisibleText(final String text, final Duration timeout) {
		if (!hasVisibleAnyText(Arrays.asList(text), timeout)) {
			throw new AssertionError("Expected visible text was not found: " + text);
		}
	}

	private void ensureAnyVisibleText(final List<String> texts, final Duration timeout) {
		if (!hasVisibleAnyText(texts, timeout)) {
			throw new AssertionError("Expected one of the texts to be visible: " + texts);
		}
	}

	private void typeIntoFieldNearLabel(final String labelText, final String value) {
		final List<By> fieldCandidates = Arrays.asList(
				By.xpath("//label[contains(normalize-space(),'" + labelText + "')]/following::input[1]"),
				By.xpath("//input[@placeholder='" + labelText + "']"),
				By.xpath("//input[contains(@placeholder,'" + labelText + "')]"),
				By.xpath("//*[contains(normalize-space(),'" + labelText + "')]/following::input[1]"));

		for (final By candidate : fieldCandidates) {
			final List<WebElement> fields = driver.findElements(candidate);
			if (!fields.isEmpty() && fields.get(0).isDisplayed()) {
				final WebElement field = fields.get(0);
				field.clear();
				field.sendKeys(value);
				waitForUiLoad();
				return;
			}
		}
		throw new AssertionError("Could not find input field for label: " + labelText);
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final Path destination = evidenceDir.resolve(checkpointName + ".png");
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void runStep(final String reportKey, final ThrowingRunnable step) {
		try {
			step.run();
			report.put(reportKey, "PASS");
		} catch (final Throwable throwable) {
			report.put(reportKey, "FAIL - " + throwable.getMessage());
		}
	}

	private String formatReport() {
		return report.entrySet().stream().map(entry -> entry.getKey() + ": " + entry.getValue())
				.collect(Collectors.joining("\n"));
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder content = new StringBuilder();
		content.append("saleads_mi_negocio_full_test\n\n");
		content.append(formatReport()).append('\n');
		if (!termsUrl.isEmpty()) {
			content.append("Términos y Condiciones URL: ").append(termsUrl).append('\n');
		}
		if (!privacyUrl.isEmpty()) {
			content.append("Política de Privacidad URL: ").append(privacyUrl).append('\n');
		}
		Files.writeString(evidenceDir.resolve("final-report.txt"), content.toString());
	}

	private Optional<String> env(final String key) {
		return Optional.ofNullable(System.getenv(key)).map(String::trim).filter(value -> !value.isEmpty());
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
