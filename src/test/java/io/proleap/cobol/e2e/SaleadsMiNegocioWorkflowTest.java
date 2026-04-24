package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final List<String> REPORT_FIELDS = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, String> stepReport = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private String applicationWindowHandle;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set -Dsaleads.e2e.enabled=true to run this E2E test.",
				Boolean.parseBoolean(readConfig("saleads.e2e.enabled", "false")));

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(readConfig("saleads.headless", "false"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--lang=es-ES");
		options.addArguments("--disable-notifications");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(30));

		final String runId = LocalDateTime.now().format(TIMESTAMP_FORMAT);
		evidenceDir = Path.of("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);

		final String startUrl = readConfig("saleads.startUrl", "");
		if (!startUrl.isBlank()) {
			driver.get(startUrl);
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() {
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
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		assertTrue("Some SaleADS Mi Negocio validations failed. See target/saleads-evidence for details.",
				stepReport.values().stream().allMatch("PASS"::equals));
	}

	private void stepLoginWithGoogle() throws IOException {
		clickFirstVisibleByText("Sign in with Google", "Iniciar sesion con Google", "Continuar con Google",
				"Acceder con Google");
		selectGoogleAccountIfShown();

		waitForAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Inicio");
		assertTrue("Expected main application interface after login.", isSidebarVisible());
		applicationWindowHandle = driver.getWindowHandle();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickFirstVisibleByText("Mi Negocio", "Negocio");
		waitForAnyVisibleText("Agregar Negocio", "Administrar Negocios");
		assertTrue("Expected 'Agregar Negocio' option.", isTextVisible("Agregar Negocio"));
		assertTrue("Expected 'Administrar Negocios' option.", isTextVisible("Administrar Negocios"));
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickFirstVisibleByText("Agregar Negocio");
		waitForAnyVisibleText("Crear Nuevo Negocio");

		assertTrue("Expected modal title 'Crear Nuevo Negocio'.", isTextVisible("Crear Nuevo Negocio"));
		assertTrue("Expected input label 'Nombre del Negocio'.", isTextVisible("Nombre del Negocio"));
		assertTrue("Expected business quota text.", isTextVisible("Tienes 2 de 3 negocios"));
		assertTrue("Expected 'Cancelar' button.", isTextVisible("Cancelar"));
		assertTrue("Expected 'Crear Negocio' button.", isTextVisible("Crear Negocio"));

		final WebElement businessNameInput = firstVisible(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='businessName' or @id='businessName' or @type='text']"));
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");

		takeScreenshot("03-agregar-negocio-modal");
		clickFirstVisibleByText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickFirstVisibleByText("Mi Negocio", "Negocio");
			waitForUiLoad();
		}
		clickFirstVisibleByText("Administrar Negocios");
		waitForAnyVisibleText("Informacion General", "Información General");

		assertTrue("Expected section 'Informacion General'.", isTextVisibleEither("Informacion General", "Información General"));
		assertTrue("Expected section 'Detalles de la Cuenta'.", isTextVisible("Detalles de la Cuenta"));
		assertTrue("Expected section 'Tus Negocios'.", isTextVisible("Tus Negocios"));
		assertTrue("Expected section 'Seccion Legal'.", isTextVisibleEither("Seccion Legal", "Sección Legal"));

		takeFullPageScreenshot("04-administrar-negocios-full");
	}

	private void stepValidateInformacionGeneral() {
		assertTrue("Expected an email to be visible in account summary.", hasVisibleEmail());
		assertTrue("Expected a likely user name string.", hasLikelyUserName());
		assertTrue("Expected text 'BUSINESS PLAN'.", isTextVisible("BUSINESS PLAN"));
		assertTrue("Expected button 'Cambiar Plan'.", isTextVisible("Cambiar Plan"));
	}

	private void stepValidateDetallesCuenta() {
		assertTrue("Expected 'Cuenta creada'.", isTextVisible("Cuenta creada"));
		assertTrue("Expected 'Estado activo'.", isTextVisible("Estado activo"));
		assertTrue("Expected 'Idioma seleccionado'.", isTextVisible("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		assertTrue("Expected section title 'Tus Negocios'.", isTextVisible("Tus Negocios"));
		assertTrue("Expected button 'Agregar Negocio'.", isTextVisible("Agregar Negocio"));
		assertTrue("Expected business quota text.", isTextVisible("Tienes 2 de 3 negocios"));

		final List<WebElement> visibleRows = visibleElements(By.xpath(
				"//*[contains(@class,'business') or contains(@class,'negocio') or self::li or self::tr][string-length(normalize-space()) > 3]"));
		assertTrue("Expected a visible business list.", !visibleRows.isEmpty());
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		final String legalUrl = clickLegalLinkValidateAndReturn("05-terminos-y-condiciones",
				new String[] { "Términos y Condiciones", "Terminos y Condiciones" });
		Files.writeString(evidenceDir.resolve("05-terminos-url.txt"), legalUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		final String legalUrl = clickLegalLinkValidateAndReturn("06-politica-privacidad",
				new String[] { "Política de Privacidad", "Politica de Privacidad" });
		Files.writeString(evidenceDir.resolve("06-politica-url.txt"), legalUrl);
	}

	private String clickLegalLinkValidateAndReturn(final String screenshotName, final String[] textVariants)
			throws IOException {
		ensureLegalSectionVisible();
		final Set<String> handlesBefore = driver.getWindowHandles();
		clickFirstVisibleByText(textVariants);
		waitForUiLoad();

		String openedHandle = null;
		try {
			openedHandle = new WebDriverWait(driver, java.time.Duration.ofSeconds(8)).until(drv -> {
				final Set<String> now = drv.getWindowHandles();
				if (now.size() > handlesBefore.size()) {
					for (final String handle : now) {
						if (!handlesBefore.contains(handle)) {
							return handle;
						}
					}
				}
				return null;
			});
		} catch (final TimeoutException ignored) {
			// Same-tab navigation is also valid.
		}

		if (openedHandle != null) {
			driver.switchTo().window(openedHandle);
			waitForUiLoad();
		}

		waitForAnyVisibleText("Términos y Condiciones", "Terminos y Condiciones", "Política de Privacidad",
				"Politica de Privacidad");
		assertTrue("Expected legal heading text.", hasAnyVisibleText("Términos y Condiciones", "Terminos y Condiciones",
				"Política de Privacidad", "Politica de Privacidad"));
		assertTrue("Expected legal content body text.", hasVisibleLegalBodyText());
		takeScreenshot(screenshotName);
		final String url = driver.getCurrentUrl();

		if (openedHandle != null) {
			driver.close();
			driver.switchTo().window(applicationWindowHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		return url;
	}

	private void ensureLegalSectionVisible() {
		if (!hasAnyVisibleText("Sección Legal", "Seccion Legal")) {
			clickFirstVisibleByText("Administrar Negocios");
			waitForUiLoad();
		}
		assertTrue("Expected legal section to be visible.", hasAnyVisibleText("Sección Legal", "Seccion Legal"));
	}

	private void selectGoogleAccountIfShown() {
		try {
			final WebDriverWait accountWait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));
			final WebElement accountElement = accountWait.until(drv -> {
				final List<WebElement> candidates = drv.findElements(By.xpath(
						"//*[contains(normalize-space(.), '" + GOOGLE_ACCOUNT_EMAIL + "') and not(self::script)]"));
				return candidates.stream().filter(WebElement::isDisplayed).findFirst().orElse(null);
			});
			clickElement(accountElement);
			waitForUiLoad();
		} catch (final TimeoutException ignored) {
			// Account chooser can be skipped if session is already authenticated.
		}
	}

	private void runStep(final String stepName, final CheckedRunnable stepAction) {
		try {
			stepAction.run();
			stepReport.put(stepName, "PASS");
		} catch (final Throwable ex) {
			stepReport.put(stepName, "FAIL");
			stepErrors.put(stepName, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
			try {
				takeScreenshot("fail-" + sanitizeFileSegment(stepName));
			} catch (final Exception ignored) {
				// Best effort evidence capture only.
			}
		}
	}

	private void writeFinalReport() {
		try {
			for (final String field : REPORT_FIELDS) {
				stepReport.putIfAbsent(field, "FAIL");
				stepErrors.putIfAbsent(field, "");
			}

			final List<String> lines = new ArrayList<>();
			lines.add("SaleADS Mi Negocio workflow validation report");
			lines.add("Evidence directory: " + evidenceDir.toAbsolutePath());
			lines.add("");
			for (final String field : REPORT_FIELDS) {
				lines.add(field + ": " + stepReport.get(field)
						+ (stepErrors.get(field).isBlank() ? "" : " | Error: " + stepErrors.get(field)));
			}
			Files.write(evidenceDir.resolve("final-report.txt"), lines);
		} catch (final Exception ignored) {
			// Report writing should not mask original test result.
		}
	}

	private void clickFirstVisibleByText(final String... textVariants) {
		for (final String text : textVariants) {
			final Optional<WebElement> element = findVisibleByText(text);
			if (element.isPresent()) {
				clickElement(element.get());
				waitForUiLoad();
				return;
			}
		}
		throw new IllegalStateException("No visible element found for texts: " + String.join(", ", textVariants));
	}

	private Optional<WebElement> findVisibleByText(final String text) {
		final String literal = xpathLiteral(text);
		final String normalizedMatch = "translate(normalize-space(.), 'ÁÉÍÓÚáéíóú', 'AEIOUaeiou')";
		final String normalizedText = normalizeForXPath(text);
		final String normalizedTextLiteral = xpathLiteral(normalizedText);
		final String xpath = "//*[self::a or self::button or @role='button' or self::span or self::div or self::li]"
				+ "[" + normalizedMatch + "=" + normalizedTextLiteral + " or contains(" + normalizedMatch + ", "
				+ normalizedTextLiteral + ") or normalize-space(.)=" + literal + " or contains(normalize-space(.), "
				+ literal + ")]";

		return driver.findElements(By.xpath(xpath)).stream().filter(WebElement::isDisplayed).findFirst();
	}

	private void clickElement(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
			element.click();
		} catch (final Exception clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private boolean isSidebarVisible() {
		return !visibleElements(By.xpath("//aside | //nav")).isEmpty()
				&& hasAnyVisibleText("Mi Negocio", "Negocio", "Dashboard", "Inicio");
	}

	private boolean isTextVisible(final String text) {
		return findVisibleByText(text).isPresent();
	}

	private boolean isTextVisibleEither(final String optionA, final String optionB) {
		return isTextVisible(optionA) || isTextVisible(optionB);
	}

	private boolean hasAnyVisibleText(final String... textVariants) {
		for (final String text : textVariants) {
			if (isTextVisible(text)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasVisibleEmail() {
		return visibleElements(By.xpath("//*[contains(normalize-space(.), '@')]")).stream()
				.map(WebElement::getText)
				.anyMatch(text -> text.matches("(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*"));
	}

	private boolean hasLikelyUserName() {
		return visibleElements(By.xpath("//*[self::h1 or self::h2 or self::h3 or self::p or self::span]")).stream()
				.map(WebElement::getText)
				.map(String::trim)
				.filter(text -> text.length() >= 5 && text.length() <= 80)
				.filter(text -> !text.contains("@"))
				.filter(text -> text.matches(".*[A-Za-z].*"))
				.findFirst()
				.isPresent();
	}

	private boolean hasVisibleLegalBodyText() {
		return visibleElements(By.xpath("//p[string-length(normalize-space()) > 40] | //li[string-length(normalize-space()) > 40]"))
				.size() > 0;
	}

	private void waitForAnyVisibleText(final String... textVariants) {
		wait.until(anyTextVisible(textVariants));
	}

	private ExpectedCondition<Boolean> anyTextVisible(final String... texts) {
		return drv -> {
			for (final String text : texts) {
				if (findVisibleByText(text).isPresent()) {
					return true;
				}
			}
			return false;
		};
	}

	private List<WebElement> visibleElements(final By by) {
		return driver.findElements(by).stream().filter(WebElement::isDisplayed).collect(Collectors.toList());
	}

	private WebElement firstVisible(final By by) {
		return visibleElements(by).stream().findFirst()
				.orElseThrow(() -> new IllegalStateException("No visible element found for locator: " + by));
	}

	private void waitForUiLoad() {
		try {
			wait.until(drv -> "complete".equals(((JavascriptExecutor) drv).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Some transitions may be SPA-based and never return complete quickly.
		}
		try {
			Thread.sleep(400);
		} catch (final InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String fileName = sanitizeFileSegment(name) + ".png";
		Files.copy(screenshot.toPath(), evidenceDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private void takeFullPageScreenshot(final String name) throws IOException {
		final JavascriptExecutor js = (JavascriptExecutor) driver;
		final Long fullWidth = (Long) js.executeScript(
				"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth,"
						+ "document.body.offsetWidth, document.documentElement.offsetWidth,"
						+ "document.documentElement.clientWidth);");
		final Long fullHeight = (Long) js.executeScript(
				"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight,"
						+ "document.body.offsetHeight, document.documentElement.offsetHeight,"
						+ "document.documentElement.clientHeight);");

		final Long targetWidth = fullWidth == null ? 1920L : Math.max(1200L, Math.min(2500L, fullWidth));
		final Long targetHeight = fullHeight == null ? 1800L : Math.max(1080L, Math.min(6000L, fullHeight));

		js.executeScript("window.resizeTo(arguments[0], arguments[1]);", targetWidth, targetHeight);
		waitForUiLoad();
		takeScreenshot(name);
	}

	private String readConfig(final String key, final String defaultValue) {
		final String systemValue = System.getProperty(key);
		if (systemValue != null && !systemValue.isBlank()) {
			return systemValue;
		}
		final String envValue = System.getenv(key.toUpperCase().replace('.', '_'));
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return defaultValue;
	}

	private String sanitizeFileSegment(final String raw) {
		return raw.toLowerCase().replaceAll("[^a-z0-9\\-]+", "-").replaceAll("-+", "-").replaceAll("(^-|-$)", "");
	}

	private String normalizeForXPath(final String text) {
		return text.replace('Á', 'A').replace('É', 'E').replace('Í', 'I').replace('Ó', 'O').replace('Ú', 'U')
				.replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').trim();
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
