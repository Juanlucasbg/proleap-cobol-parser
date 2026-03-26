package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioWorkflowE2ETest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFORMACION_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_POLITICA = "Política de Privacidad";

	private static final List<String> REPORT_FIELDS = List.of(REPORT_LOGIN, REPORT_MI_NEGOCIO_MENU,
			REPORT_AGREGAR_NEGOCIO_MODAL, REPORT_ADMINISTRAR_NEGOCIOS_VIEW, REPORT_INFORMACION_GENERAL,
			REPORT_DETALLES_CUENTA, REPORT_TUS_NEGOCIOS, REPORT_TERMINOS, REPORT_POLITICA);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDir;
	private String appWindowHandle;

	private final Map<String, Boolean> reportStatus = new LinkedHashMap<>();
	private final Map<String, String> reportDetails = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		final String headlessValue = firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"));
		if (!"false".equalsIgnoreCase(headlessValue)) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		driver.manage().window().setSize(new Dimension(1920, 1080));
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));

		final String folderName = "run-" + Instant.now().toString().replace(":", "-");
		screenshotsDir = Paths.get("target", "saleads-evidence", folderName);
		Files.createDirectories(screenshotsDir);

		final String loginUrl = firstNonBlank(System.getProperty("saleads.loginUrl"), System.getenv("SALEADS_LOGIN_URL"));
		if (loginUrl != null) {
			driver.get(loginUrl);
			waitForUiLoad();
		} else {
			System.out.println(
					"No login URL provided. Assuming browser session is already on SaleADS login page (saleads.loginUrl / SALEADS_LOGIN_URL).");
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		runStep(REPORT_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(REPORT_TERMINOS, () -> stepValidateLegalLink("Términos y Condiciones", "terminos-y-condiciones"));
		runStep(REPORT_POLITICA, () -> stepValidateLegalLink("Política de Privacidad", "politica-de-privacidad"));

		final String report = buildFinalReport();
		System.out.println(report);
		final boolean allPassed = REPORT_FIELDS.stream().allMatch(field -> Boolean.TRUE.equals(reportStatus.get(field)));
		Assert.assertTrue("One or more workflow validations failed.\n" + report, allPassed);
	}

	private void stepLoginWithGoogle() throws IOException {
		if (driver.getCurrentUrl().startsWith("about:blank")) {
			throw new IllegalStateException(
					"Browser started on about:blank. Provide saleads.loginUrl or SALEADS_LOGIN_URL with current environment login page.");
		}

		appWindowHandle = driver.getWindowHandle();
		waitForUiLoad();

		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickByAnyText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");

		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com", handlesBeforeClick);

		assertTextVisibleAny("Negocio", "Mi Negocio", "Dashboard", "Panel");
		waitForSidebar();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		waitForSidebar();
		expandMiNegocioIfNeeded();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByAnyText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");

		waitForVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio')] | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));
		assertTextVisible("Tienes 2 de 3 negocios");
		clickableByText("Cancelar");
		clickableByText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		// Optional interaction from task instructions.
		final WebElement businessNameInput = waitForVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio')] | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		clickByAnyText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.),'Crear Nuevo Negocio')]")));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioIfNeeded();
		clickByAnyText("Administrar Negocios");

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		captureScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("Información General");
		assertTextVisible("BUSINESS PLAN");
		clickableByText("Cambiar Plan");

		// Validates visible email and a likely user-name text in this view.
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'@')]"));

		final WebElement generalSection = waitForVisible(By.xpath(
				"//*[self::section or self::div][contains(normalize-space(.),'Información General')]"));
		final List<WebElement> candidates = generalSection
				.findElements(By.xpath(".//*[self::h1 or self::h2 or self::h3 or self::h4 or self::p or self::span]"));

		boolean hasLikelyName = false;
		for (final WebElement candidate : candidates) {
			final String text = safeText(candidate);
			if (text.isEmpty()) {
				continue;
			}
			if (text.contains("@")) {
				continue;
			}
			if (text.contains("Información General") || text.contains("BUSINESS PLAN") || text.contains("Cambiar Plan")) {
				continue;
			}
			if (text.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				hasLikelyName = true;
				break;
			}
		}
		Assert.assertTrue("User name is not visible in 'Información General'.", hasLikelyName);
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(final String linkText, final String screenshotSlug) throws IOException {
		assertTextVisible("Sección Legal");
		final String originalHandle = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByAnyText(linkText);
		waitForNewTabOrUrlChange(handlesBefore, originalUrl);

		final Set<String> handlesAfter = driver.getWindowHandles();
		String activeHandle = driver.getWindowHandle();
		if (handlesAfter.size() > handlesBefore.size()) {
			for (final String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					activeHandle = handle;
					break;
				}
			}
			driver.switchTo().window(activeHandle);
			waitForUiLoad();
		}

		assertTextVisible(linkText);
		assertLegalContent();
		captureScreenshot("05-" + screenshotSlug);
		capturedUrls.put(linkText, driver.getCurrentUrl());

		if (!originalHandle.equals(driver.getWindowHandle())) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void selectGoogleAccountIfVisible(final String accountEmail, final Set<String> handlesBeforeClick) {
		waitForUiLoad();
		Set<String> currentHandles = driver.getWindowHandles();
		if (currentHandles.size() > handlesBeforeClick.size()) {
			for (final String handle : currentHandles) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForUiLoad();
		}

		try {
			clickByAnyText(accountEmail);
		} catch (final RuntimeException ignored) {
			// Continue even if account picker was skipped due existing Google session.
		}

		currentHandles = driver.getWindowHandles();
		if (currentHandles.contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
		waitForUiLoad();
	}

	private void expandMiNegocioIfNeeded() {
		waitForSidebar();
		if (!isTextVisible("Agregar Negocio", 2) || !isTextVisible("Administrar Negocios", 2)) {
			if (isTextVisible("Negocio", 3)) {
				clickByAnyText("Negocio");
			}
			clickByAnyText("Mi Negocio");
		}
	}

	private void waitForSidebar() {
		wait.until(driver -> {
			final List<WebElement> candidates = driver.findElements(
					By.xpath("//aside | //nav | //div[contains(@class,'sidebar')] | //div[contains(@class,'menu')]"));
			for (final WebElement candidate : candidates) {
				try {
					if (candidate.isDisplayed()) {
						return true;
					}
				} catch (final StaleElementReferenceException ignored) {
				}
			}
			return false;
		});
	}

	private void assertLegalContent() {
		wait.until(driver -> {
			final List<WebElement> paragraphs = driver
					.findElements(By.xpath("//p[string-length(normalize-space(.)) > 40] | //li[string-length(normalize-space(.)) > 40]"));
			for (final WebElement paragraph : paragraphs) {
				try {
					if (paragraph.isDisplayed()) {
						return true;
					}
				} catch (final StaleElementReferenceException ignored) {
				}
			}
			return false;
		});
	}

	private void waitForNewTabOrUrlChange(final Set<String> handlesBefore, final String originalUrl) {
		wait.until(driver -> driver.getWindowHandles().size() > handlesBefore.size()
				|| !safeString(driver.getCurrentUrl()).equals(safeString(originalUrl)));
	}

	private void waitForUiLoad() {
		wait.until((ExpectedCondition<Boolean>) driver -> "complete"
				.equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
	}

	private void clickByAnyText(final String... texts) {
		RuntimeException lastError = null;
		for (final String text : texts) {
			try {
				final WebElement target = clickableByText(text);
				scrollIntoView(target);
				target.click();
				waitForUiLoad();
				return;
			} catch (final RuntimeException ex) {
				lastError = ex;
			}
		}
		if (lastError == null) {
			throw new IllegalStateException("No texts were provided to clickByAnyText.");
		}
		throw new RuntimeException("Unable to click any expected text: " + String.join(", ", texts), lastError);
	}

	private WebElement clickableByText(final String text) {
		final String xText = xpathLiteral(text);
		final By locator = By.xpath("//button[contains(normalize-space(.)," + xText + ")]"
				+ " | //a[contains(normalize-space(.)," + xText + ")]"
				+ " | //*[@role='button' and contains(normalize-space(.)," + xText + ")]"
				+ " | //*[(self::span or self::div) and contains(normalize-space(.)," + xText + ")]");

		return wait.until(driver -> firstVisibleAndEnabled(driver.findElements(locator)));
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void assertTextVisible(final String text) {
		final String xText = xpathLiteral(text);
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.)," + xText + ")]")));
	}

	private void assertTextVisibleAny(final String... texts) {
		RuntimeException last = null;
		for (final String text : texts) {
			try {
				assertTextVisible(text);
				return;
			} catch (final RuntimeException ex) {
				last = ex;
			}
		}
		throw new RuntimeException("None of expected texts were visible: " + String.join(", ", texts), last);
	}

	private boolean isTextVisible(final String text, final int timeoutSeconds) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		final String xText = xpathLiteral(text);
		try {
			shortWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.)," + xText + ")]")));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private WebElement firstVisibleAndEnabled(final List<WebElement> elements) {
		for (final WebElement element : elements) {
			try {
				if (element.isDisplayed() && element.isEnabled()) {
					return element;
				}
			} catch (final StaleElementReferenceException ignored) {
			}
		}
		return null;
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final String safeName = checkpointName.toLowerCase().replaceAll("[^a-z0-9\\-]+", "-");
		final Path targetPath = screenshotsDir.resolve(safeName + ".png");
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Saved screenshot: " + targetPath.toAbsolutePath());
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({behavior:'instant', block:'center', inline:'center'});", element);
	}

	private String buildFinalReport() {
		final List<String> lines = new ArrayList<>();
		lines.add("=== SaleADS Mi Negocio Workflow Report ===");
		for (final String field : REPORT_FIELDS) {
			final boolean pass = Boolean.TRUE.equals(reportStatus.get(field));
			final StringBuilder line = new StringBuilder("- ").append(field).append(": ").append(pass ? "PASS" : "FAIL");
			if (!pass) {
				final String details = reportDetails.get(field);
				if (details != null && !details.isEmpty()) {
					line.append(" | ").append(details);
				}
			}
			lines.add(line.toString());
		}
		if (!capturedUrls.isEmpty()) {
			lines.add("--- Captured URLs ---");
			for (final Map.Entry<String, String> entry : capturedUrls.entrySet()) {
				lines.add("* " + entry.getKey() + ": " + entry.getValue());
			}
		}
		lines.add("Screenshot directory: " + screenshotsDir.toAbsolutePath());
		return String.join(System.lineSeparator(), lines);
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			reportStatus.put(reportField, true);
		} catch (final Throwable throwable) {
			reportStatus.put(reportField, false);
			reportDetails.put(reportField, throwable.getClass().getSimpleName() + ": " + safeString(throwable.getMessage()));
			System.err.println("Step failed - " + reportField + ": " + throwable.getMessage());
		}
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i != parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String safeText(final WebElement element) {
		try {
			final String text = element.getText();
			return text == null ? "" : text.trim();
		} catch (final Exception ex) {
			return "";
		}
	}

	private String safeString(final String value) {
		return value == null ? "" : value;
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

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
