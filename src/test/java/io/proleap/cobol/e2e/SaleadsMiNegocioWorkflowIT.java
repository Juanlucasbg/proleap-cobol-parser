package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Full E2E workflow for SaleADS "Mi Negocio", without hardcoded domain.
 *
 * Execute with:
 * mvn -Dtest=SaleadsMiNegocioWorkflowIT -Dsaleads.url=https://your-env/login test
 */
public class SaleadsMiNegocioWorkflowIT {

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Información General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "Términos y Condiciones";
	private static final String PRIVACIDAD = "Política de Privacidad";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;
	private String googleAccountEmail;

	@Before
	public void setUp() throws IOException {
		final String saleadsUrl = firstNonBlank(System.getProperty("saleads.url"), System.getenv("SALEADS_URL"));
		if (saleadsUrl == null) {
			throw new IllegalStateException(
					"Missing SaleADS URL. Provide -Dsaleads.url=<login-url> or SALEADS_URL environment variable.");
		}

		googleAccountEmail = firstNonBlank(System.getProperty("saleads.google.email"),
				System.getenv("SALEADS_GOOGLE_EMAIL"), "juanlucasbarbiergarzon@gmail.com");
		evidenceDir = Paths.get(firstNonBlank(System.getProperty("saleads.evidence.dir"),
				System.getenv("SALEADS_EVIDENCE_DIR"), "target/saleads-evidence"));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox");
		if (Boolean.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"), "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(25));
		driver.get(saleadsUrl);
		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final boolean loginOk = runStep(LOGIN, this::stepLoginWithGoogle);
		final boolean menuOk = runStepWithPrerequisite(MI_NEGOCIO_MENU, loginOk, this::stepOpenMiNegocioMenu);
		runStepWithPrerequisite(AGREGAR_NEGOCIO_MODAL, menuOk, this::stepAgregarNegocioModal);
		final boolean administrarOk = runStepWithPrerequisite(ADMINISTRAR_NEGOCIOS_VIEW, menuOk,
				this::stepOpenAdministrarNegocios);

		runStepWithPrerequisite(INFORMACION_GENERAL, administrarOk, this::stepValidateInformacionGeneral);
		runStepWithPrerequisite(DETALLES_CUENTA, administrarOk, this::stepValidateDetallesCuenta);
		runStepWithPrerequisite(TUS_NEGOCIOS, administrarOk, this::stepValidateTusNegocios);
		runStepWithPrerequisite(TERMINOS, administrarOk,
				() -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos.png"));
		runStepWithPrerequisite(PRIVACIDAD, administrarOk,
				() -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09-politica.png"));

		printFinalReport();
		assertTrue("One or more SaleADS Mi Negocio validations failed:\n" + String.join("\n", failures),
				failures.isEmpty());
	}

	private boolean stepLoginWithGoogle() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		handleGoogleAccountSelection();
		waitForUiLoad();

		final boolean appInterfaceVisible = isVisible(By.xpath("//aside | //nav[contains(@class,'sidebar')]"));
		final boolean sidebarVisible = isVisible(By.xpath("//*[normalize-space()='Negocio' or contains(normalize-space(.),'Negocio')]"));

		takeScreenshot("01-dashboard-loaded.png");
		return appInterfaceVisible && sidebarVisible;
	}

	private boolean stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		final boolean agregarVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Agregar Negocio')]"));
		final boolean administrarVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Administrar Negocios')]"));

		takeScreenshot("02-mi-negocio-menu.png");
		return agregarVisible && administrarVisible;
	}

	private boolean stepAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		final boolean titleVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]"));
		final boolean inputVisible = isVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio')] | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));
		final boolean quotaVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Tienes 2 de 3 negocios')]"));
		final boolean cancelarVisible = isVisible(By.xpath("//button[contains(normalize-space(.), 'Cancelar')]"));
		final boolean crearVisible = isVisible(By.xpath("//button[contains(normalize-space(.), 'Crear Negocio')]"));

		takeScreenshot("03-agregar-negocio-modal.png");

		if (inputVisible) {
			final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
					"//input[contains(@placeholder,'Nombre del Negocio')] | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]")));
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
			clickByVisibleText("Cancelar");
		}

		return titleVisible && inputVisible && quotaVisible && cancelarVisible && crearVisible;
	}

	private boolean stepOpenAdministrarNegocios() throws Exception {
		if (!isVisible(By.xpath("//*[contains(normalize-space(.), 'Administrar Negocios')]"), Duration.ofSeconds(3))) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		final boolean informacion = isVisible(By.xpath("//*[contains(normalize-space(.), 'Información General')]"));
		final boolean detalles = isVisible(By.xpath("//*[contains(normalize-space(.), 'Detalles de la Cuenta')]"));
		final boolean negocios = isVisible(By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]"));
		final boolean legal = isVisible(By.xpath("//*[contains(normalize-space(.), 'Sección Legal')]"));

		takeScreenshot("04-administrar-negocios.png");
		return informacion && detalles && negocios && legal;
	}

	private boolean stepValidateInformacionGeneral() {
		final boolean userEmail = isVisible(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(googleAccountEmail) + ")]"));
		final boolean userName = isVisible(By.xpath(
				"//*[contains(normalize-space(.), 'Información General')]/following::*[self::h1 or self::h2 or self::h3 or self::p or self::span][normalize-space()!=''][1]"));
		final boolean plan = isVisible(By.xpath("//*[contains(normalize-space(.), 'BUSINESS PLAN')]"));
		final boolean cambiarPlan = isVisible(By.xpath("//button[contains(normalize-space(.), 'Cambiar Plan')]"));
		return userName && userEmail && plan && cambiarPlan;
	}

	private boolean stepValidateDetallesCuenta() {
		final boolean cuentaCreada = isVisible(By.xpath("//*[contains(normalize-space(.), 'Cuenta creada')]"));
		final boolean estadoActivo = isVisible(By.xpath("//*[contains(normalize-space(.), 'Estado activo')]"));
		final boolean idiomaSeleccionado = isVisible(By.xpath("//*[contains(normalize-space(.), 'Idioma seleccionado')]"));
		return cuentaCreada && estadoActivo && idiomaSeleccionado;
	}

	private boolean stepValidateTusNegocios() {
		final boolean listSection = isVisible(By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]"));
		final boolean agregarNegocioButton = isVisible(By.xpath("//button[contains(normalize-space(.), 'Agregar Negocio')]"));
		final boolean quota = isVisible(By.xpath("//*[contains(normalize-space(.), 'Tienes 2 de 3 negocios')]"));
		return listSection && agregarNegocioButton && quota;
	}

	private boolean stepValidateLegalLink(final String linkText, final String heading, final String screenshotName)
			throws Exception {
		final Set<String> handlesBefore = driver.getWindowHandles();
		clickByVisibleText(linkText);

		final Optional<String> newWindow = switchToNewWindow(handlesBefore, Duration.ofSeconds(12));
		if (newWindow.isPresent()) {
			driver.switchTo().window(newWindow.get());
			waitForUiLoad();
		}

		final boolean headingVisible = isVisible(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(heading) + ")]"));
		final boolean legalTextVisible = hasLegalBodyText();

		legalUrls.put(linkText, driver.getCurrentUrl());
		takeScreenshot(screenshotName);

		returnToApplicationWindow(newWindow);
		return headingVisible && legalTextVisible;
	}

	private void returnToApplicationWindow(final Optional<String> newWindow) {
		if (newWindow.isPresent()) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
			waitForUiLoad();
		} else if (driver.getWindowHandle().equals(appWindowHandle)) {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private boolean hasLegalBodyText() {
		try {
			final WebElement body = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
			return body.getText() != null && body.getText().trim().length() > 150;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private void handleGoogleAccountSelection() {
		final Optional<String> googleWindow = switchToNewWindow(Set.of(appWindowHandle), Duration.ofSeconds(8));
		googleWindow.ifPresent(handle -> {
			driver.switchTo().window(handle);
			waitForUiLoad();
		});

		final By accountLocator = By.xpath(
				"//*[@data-identifier=" + xpathLiteral(googleAccountEmail) + "] | //*[contains(normalize-space(.), "
						+ xpathLiteral(googleAccountEmail) + ")]");
		if (isVisible(accountLocator, Duration.ofSeconds(8))) {
			final WebElement account = wait.until(ExpectedConditions.elementToBeClickable(accountLocator));
			account.click();
			waitForUiLoad();
		}

		for (final String handle : driver.getWindowHandles()) {
			if (handle.equals(appWindowHandle)) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				return;
			}
		}
	}

	private Optional<String> switchToNewWindow(final Set<String> previousHandles, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout)
					.until(webDriver -> webDriver.getWindowHandles().size() > previousHandles.size());
			final Set<String> currentHandles = driver.getWindowHandles();
			for (final String handle : currentHandles) {
				if (!previousHandles.contains(handle)) {
					return Optional.of(handle);
				}
			}
		} catch (final TimeoutException e) {
			return Optional.empty();
		}
		return Optional.empty();
	}

	private void clickByVisibleText(final String... candidateTexts) {
		for (final String text : candidateTexts) {
			final By locator = By.xpath("//button[contains(normalize-space(.), " + xpathLiteral(text) + ")]"
					+ " | //a[contains(normalize-space(.), " + xpathLiteral(text) + ")]"
					+ " | //*[@role='button' and contains(normalize-space(.), " + xpathLiteral(text) + ")]"
					+ " | //span[contains(normalize-space(.), " + xpathLiteral(text)
					+ ")]/ancestor::*[self::button or self::a or @role='button'][1]");
			if (isVisible(locator, Duration.ofSeconds(3))) {
				final WebElement clickable = wait.until(ExpectedConditions.elementToBeClickable(locator));
				clickable.click();
				waitForUiLoad();
				return;
			}
		}

		throw new TimeoutException("Could not find clickable element for texts: " + String.join(", ", candidateTexts));
	}

	private boolean isVisible(final By locator) {
		return isVisible(locator, Duration.ofSeconds(25));
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private void waitForUiLoad() {
		final ExpectedCondition<Boolean> pageLoaded = webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState"));
		wait.until(pageLoaded);

		try {
			Thread.sleep(600);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String filename) throws IOException {
		final Path outputFile = evidenceDir.resolve(filename);
		final Path screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(screenshot, outputFile, StandardCopyOption.REPLACE_EXISTING);
	}

	private boolean runStep(final String reportField, final StepRunner stepRunner) {
		try {
			final boolean result = stepRunner.run();
			report.put(reportField, result);
			if (!result) {
				failures.add(reportField + ": one or more validations failed.");
			}
			return result;
		} catch (final Exception e) {
			report.put(reportField, false);
			failures.add(reportField + ": " + e.getMessage());
			return false;
		}
	}

	private boolean runStepWithPrerequisite(final String reportField, final boolean prerequisite,
			final StepRunner stepRunner) {
		if (!prerequisite) {
			report.put(reportField, false);
			failures.add(reportField + ": skipped because a prerequisite step failed.");
			return false;
		}
		return runStep(reportField, stepRunner);
	}

	private void printFinalReport() {
		System.out.println("=== SALEADS MI NEGOCIO FINAL REPORT ===");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
			System.out.println(legalUrl.getKey() + " URL: " + legalUrl.getValue());
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private String xpathLiteral(final String value) {
		if (value.indexOf('\'') == -1) {
			return "'" + value + "'";
		}
		if (value.indexOf('"') == -1) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder concatBuilder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				concatBuilder.append(", \"'\", ");
			}
			concatBuilder.append("'").append(parts[i]).append("'");
		}
		concatBuilder.append(")");
		return concatBuilder.toString();
	}

	@FunctionalInterface
	private interface StepRunner {
		boolean run() throws Exception;
	}
}
