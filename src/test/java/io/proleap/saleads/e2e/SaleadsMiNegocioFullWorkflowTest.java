package io.proleap.saleads.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * Full UI workflow for SaleADS Mi Negocio module.
 *
 * <p>
 * This test is disabled by default to avoid breaking normal parser CI runs.
 * Enable with: -Dsaleads.e2e.enabled=true and provide
 * -Dsaleads.login.url=https://your-environment/login
 * </p>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String ENABLED_PROPERTY = "saleads.e2e.enabled";
	private static final String LOGIN_URL_PROPERTY = "saleads.login.url";
	private static final String HEADLESS_PROPERTY = "saleads.headless";
	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(25);
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;
	private String terminosFinalUrl = "N/A";
	private String privacidadFinalUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
		Assume.assumeTrue(
				"This E2E test is opt-in. Enable with -D" + ENABLED_PROPERTY + "=true and provide -D"
						+ LOGIN_URL_PROPERTY + "=https://<environment>/login.",
				enabled);

		final String loginUrl = System.getProperty(LOGIN_URL_PROPERTY, "").trim();
		assertFalse("Missing required property: -" + LOGIN_URL_PROPERTY, loginUrl.isEmpty());

		final String runTimestamp = TS_FORMAT.format(Instant.now());
		evidenceDir = Path.of("target", "saleads-artifacts", runTimestamp);
		Files.createDirectories(evidenceDir);

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getProperty(HEADLESS_PROPERTY, "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);

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
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		executeStep("Login", this::step1LoginWithGoogle);
		executeStep("Mi Negocio menu", this::step2OpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::step3ValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::step4OpenAdministrarNegocios);
		executeStep("Información General", this::step5ValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::step6ValidateDetallesCuenta);
		executeStep("Tus Negocios", this::step7ValidateTusNegocios);
		executeStep("Términos y Condiciones", this::step8ValidateTerminosYCondiciones);
		executeStep("Política de Privacidad", this::step9ValidatePoliticaPrivacidad);

		assertNoFailedSteps();
	}

	private void step1LoginWithGoogle() throws IOException {
		final String initialWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeLogin = driver.getWindowHandles();

		final WebElement loginButton = waitForAnyDisplayedClickable(
				byTextButtonOrLink("Sign in with Google"),
				byTextButtonOrLink("Iniciar con Google"),
				byTextButtonOrLink("Continuar con Google"),
				byTextButtonOrLink("Google"));

		clickAndWait(loginButton);
		waitForUiToLoad();

		final String googleWindow = resolveActiveWindow(handlesBeforeLogin, driver.getWindowHandles());
		if (!googleWindow.equals(initialWindow)) {
			driver.switchTo().window(googleWindow);
			waitForUiToLoad();
			selectGoogleAccountIfVisible();

			driver.switchTo().window(initialWindow);
			waitForUiToLoad();
		} else {
			selectGoogleAccountIfVisible();
		}

		try {
			wait.until(d -> d.getWindowHandles().size() == 1);
		} catch (final Exception ignored) {
			if (driver.getWindowHandles().contains(initialWindow)) {
				driver.switchTo().window(initialWindow);
			}
		}

		// Validate main app shell and sidebar.
		waitForAnyDisplayed(By.tagName("aside"), By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar')]"));
		waitForAnyDisplayed(By.tagName("main"), By.xpath("//*[contains(@class,'dashboard')]"));
		takeScreenshot("01-dashboard-loaded");
	}

	private void step2OpenMiNegocioMenu() throws IOException {
		final WebElement negocioSection = waitForAnyDisplayedClickable(
				byTextButtonOrLink("Negocio"),
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Negocio") + ")]"));
		clickAndWait(negocioSection);

		final WebElement miNegocio = waitForAnyDisplayedClickable(
				byTextButtonOrLink("Mi Negocio"),
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Mi Negocio") + ")]"));
		clickAndWait(miNegocio);

		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Agregar Negocio") + ")]"));
		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Administrar Negocios") + ")]"));
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void step3ValidateAgregarNegocioModal() throws IOException {
		final WebElement agregarNegocio = waitForAnyDisplayedClickable(
				byTextButtonOrLink("Agregar Negocio"),
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Agregar Negocio") + ")]"));
		clickAndWait(agregarNegocio);

		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Crear Nuevo Negocio") + ")]"));
		waitForAnyDisplayed(
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Nombre del Negocio") + ")]"));
		waitForAnyDisplayed(
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Tienes 2 de 3 negocios") + ")]"));
		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Cancelar") + ")]"));
		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Crear Negocio") + ")]"));

		// Optional data entry exercise before cancelling modal.
		final WebElement businessNameInput = waitForAnyDisplayed(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label," + xpathLiteral("Nombre del Negocio") + ")]"),
				By.xpath("//input[contains(@name,'negocio') or contains(@id,'negocio')]"));
		clickAndWait(businessNameInput);
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");

		takeScreenshot("03-agregar-negocio-modal");

		final WebElement cancelar = waitForAnyDisplayedClickable(
				byTextButtonOrLink("Cancelar"),
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Cancelar") + ")]"));
		clickAndWait(cancelar);
	}

	private void step4OpenAdministrarNegocios() throws IOException {
		ensureMiNegocioExpanded();

		final WebElement administrarNegocios = waitForAnyDisplayedClickable(
				byTextButtonOrLink("Administrar Negocios"),
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Administrar Negocios") + ")]"));
		clickAndWait(administrarNegocios);

		waitForAnyDisplayed(
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Informacion General") + ")]"),
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Información General") + ")]"));
		waitForAnyDisplayed(
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Detalles de la Cuenta") + ")]"));
		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Tus Negocios") + ")]"));
		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Seccion Legal") + ")]"),
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Sección Legal") + ")]"));

		takeFullPageScreenshot("04-administrar-negocios-full");
	}

	private void step5ValidateInformacionGeneral() {
		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("@") + ")]"));
		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("BUSINESS PLAN") + ")]"));
		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Cambiar Plan") + ")]"));
	}

	private void step6ValidateDetallesCuenta() {
		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Cuenta creada") + ")]"));
		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Estado activo") + ")]"));
		waitForAnyDisplayed(
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Idioma seleccionado") + ")]"));
	}

	private void step7ValidateTusNegocios() {
		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Tus Negocios") + ")]"));
		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Agregar Negocio") + ")]"));
		waitForAnyDisplayed(
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Tienes 2 de 3 negocios") + ")]"));
	}

	private void step8ValidateTerminosYCondiciones() throws IOException {
		appWindowHandle = driver.getWindowHandle();

		terminosFinalUrl = openAndValidateLegalDocument(
				"Terminos y Condiciones",
				"Términos y Condiciones",
				"08-terminos-y-condiciones");
	}

	private void step9ValidatePoliticaPrivacidad() throws IOException {
		appWindowHandle = driver.getWindowHandle();

		privacidadFinalUrl = openAndValidateLegalDocument(
				"Politica de Privacidad",
				"Política de Privacidad",
				"09-politica-de-privacidad");
	}

	private String openAndValidateLegalDocument(final String unaccentedLabel, final String accentedLabel,
			final String screenshotName) throws IOException {
		final Set<String> handlesBefore = driver.getWindowHandles();

		final WebElement legalLink = waitForAnyDisplayedClickable(
				byTextButtonOrLink(accentedLabel),
				byTextButtonOrLink(unaccentedLabel),
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(accentedLabel) + ")]"),
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(unaccentedLabel) + ")]"));
		clickAndWait(legalLink);

		waitForUiToLoad();
		final Set<String> handlesAfter = driver.getWindowHandles();
		final String activeWindow = resolveActiveWindow(handlesBefore, handlesAfter);
		driver.switchTo().window(activeWindow);
		waitForUiToLoad();

		waitForAnyDisplayed(
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(accentedLabel) + ")]"),
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(unaccentedLabel) + ")]"));

		// Generic legal-text validation: any visible paragraph-sized text block.
		waitForAnyDisplayed(By.xpath("//p[string-length(normalize-space(.)) > 30]"),
				By.xpath("//div[string-length(normalize-space(.)) > 60]"));

		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();
		assertNotNull("Legal page URL should not be null", finalUrl);

		if (!activeWindow.equals(appWindowHandle)) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();

		return finalUrl;
	}

	private void ensureMiNegocioExpanded() {
		final List<WebElement> administrar = driver.findElements(
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Administrar Negocios") + ")]"));
		for (final WebElement element : administrar) {
			if (element.isDisplayed()) {
				return;
			}
		}

		final WebElement miNegocio = waitForAnyDisplayedClickable(
				byTextButtonOrLink("Mi Negocio"),
				By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Mi Negocio") + ")]"));
		clickAndWait(miNegocio);
		waitForAnyDisplayed(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral("Administrar Negocios") + ")]"));
	}

	private void executeStep(final String stepName, final CheckedRunnable stepAction) {
		try {
			stepAction.run();
			report.put(stepName, StepResult.pass());
		} catch (final Exception e) {
			report.put(stepName, StepResult.fail(e.getMessage()));
		}
	}

	private WebElement waitForAnyDisplayedClickable(final By... candidates) {
		return wait.until(driverInstance -> {
			for (final By candidate : candidates) {
				for (final WebElement element : driverInstance.findElements(candidate)) {
					if (element.isDisplayed() && element.isEnabled()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private WebElement waitForAnyDisplayed(final By... candidates) {
		return wait.until(driverInstance -> {
			for (final By candidate : candidates) {
				for (final WebElement element : driverInstance.findElements(candidate)) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private By byTextButtonOrLink(final String text) {
		final String literal = xpathLiteral(text);
		final String lowerLiteral = xpathLiteral(text.toLowerCase(Locale.ROOT));
		return By.xpath(
				"//*[self::button or self::a or @role='button'][contains(translate(normalize-space(.),"
						+ xpathLiteral("ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ")
						+ ","
						+ xpathLiteral("abcdefghijklmnopqrstuvwxyzáéíóúüñ")
						+ "),"
						+ lowerLiteral + ") or contains(normalize-space(.)," + literal + ")]");
	}

	private void clickAndWait(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		element.click();
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until((ExpectedCondition<Boolean>) wd -> {
			if (wd == null) {
				return false;
			}
			final Object state = ((JavascriptExecutor) wd).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(state));
		});

		wait.until(wd -> {
			if (wd == null) {
				return false;
			}
			final Object activeRequests = ((JavascriptExecutor) wd).executeScript(
					"return (window.jQuery ? jQuery.active : 0) +"
							+ "(window.__pendingRequests || 0);");
			return activeRequests instanceof Number && ((Number) activeRequests).intValue() == 0;
		});
	}

	private void takeScreenshot(final String name) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(name + ".png");
		Files.createDirectories(target.getParent());
		Files.copy(source.toPath(), target);
	}

	private void takeFullPageScreenshot(final String name) throws IOException {
		final JavascriptExecutor js = (JavascriptExecutor) driver;
		final long originalWidth = ((Number) js.executeScript("return window.innerWidth")).longValue();
		final long originalHeight = ((Number) js.executeScript("return window.innerHeight")).longValue();
		final long fullHeight = ((Number) js.executeScript(
				"return Math.max(document.body.scrollHeight,document.documentElement.scrollHeight);")).longValue();

		driver.manage().window().setSize(new Dimension((int) originalWidth, (int) fullHeight + 150));
		waitForUiToLoad();
		takeScreenshot(name);
		driver.manage().window().setSize(new Dimension((int) originalWidth, (int) originalHeight));
		waitForUiToLoad();
	}

	private String resolveActiveWindow(final Set<String> before, final Set<String> after) {
		for (final String handle : after) {
			if (!before.contains(handle)) {
				return handle;
			}
		}
		return driver.getWindowHandle();
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final List<String> missing = new ArrayList<>();
		addMissingField(missing, "Login");
		addMissingField(missing, "Mi Negocio menu");
		addMissingField(missing, "Agregar Negocio modal");
		addMissingField(missing, "Administrar Negocios view");
		addMissingField(missing, "Información General");
		addMissingField(missing, "Detalles de la Cuenta");
		addMissingField(missing, "Tus Negocios");
		addMissingField(missing, "Términos y Condiciones");
		addMissingField(missing, "Política de Privacidad");

		for (final String missingField : missing) {
			report.put(missingField, StepResult.fail("Step did not execute"));
		}

		final StringBuilder content = new StringBuilder();
		content.append("# SaleADS Mi Negocio Workflow Report\n\n");
		content.append("| Step | Status | Details |\n");
		content.append("| --- | --- | --- |\n");

		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final StepResult result = entry.getValue();
			content.append("| ").append(entry.getKey()).append(" | ")
					.append(result.pass ? "PASS" : "FAIL").append(" | ")
					.append(result.details.replace("|", "\\|")).append(" |\n");
		}

		content.append("\n## Legal URLs\n");
		content.append("- Terminos y Condiciones: ").append(terminosFinalUrl).append("\n");
		content.append("- Politica de Privacidad: ").append(privacidadFinalUrl).append("\n");

		final Path reportPath = evidenceDir.resolve("final-report.md");
		Files.writeString(reportPath, content.toString(), StandardCharsets.UTF_8);
	}

	private void addMissingField(final List<String> missing, final String field) {
		if (!report.containsKey(field)) {
			missing.add(field);
		}
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
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

	private void selectGoogleAccountIfVisible() {
		final WebElement accountCandidate = tryFindDisplayedClickable(
				By.xpath("//*[contains(normalize-space(.),"
						+ xpathLiteral("juanlucasbarbiergarzon@gmail.com") + ")]"),
				By.xpath("//*[@data-email=" + xpathLiteral("juanlucasbarbiergarzon@gmail.com") + "]"),
				By.xpath("//*[@data-identifier=" + xpathLiteral("juanlucasbarbiergarzon@gmail.com") + "]"));

		if (accountCandidate != null) {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", accountCandidate);
			accountCandidate.click();
		}
	}

	private WebElement tryFindDisplayedClickable(final By... candidates) {
		for (final By candidate : candidates) {
			for (final WebElement element : driver.findElements(candidate)) {
				if (element.isDisplayed() && element.isEnabled()) {
					return element;
				}
			}
		}
		return null;
	}

	private void assertNoFailedSteps() {
		final StringBuilder failures = new StringBuilder();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().pass) {
				if (failures.length() > 0) {
					failures.append("; ");
				}
				failures.append(entry.getKey()).append(": ").append(entry.getValue().details);
			}
		}
		assertFalse("Workflow validation failures: " + failures, failures.length() > 0);
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static class StepResult {
		private final boolean pass;
		private final String details;

		private StepResult(final boolean pass, final String details) {
			this.pass = pass;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, "Validated");
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details == null ? "Unknown failure" : details);
		}
	}
}
