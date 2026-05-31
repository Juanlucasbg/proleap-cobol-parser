package io.proleap.e2e.saleads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(25);
	private static final String PASS = "PASS";
	private static final String FAIL = "FAIL";

	private static final List<String> REPORT_FIELDS = List.of(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Informacion General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Terminos y Condiciones",
			"Politica de Privacidad");

	private final Map<String, String> report = new LinkedHashMap<>();
	private final Map<String, String> details = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		for (final String field : REPORT_FIELDS) {
			report.put(field, FAIL);
		}

		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
		evidenceDir = Path.of("target", "saleads-evidence", TEST_NAME, runId);
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		if (!Boolean.parseBoolean(envOrProperty("SALEADS_HEADFUL", "saleads.headful", "false"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		final String loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getProperty("saleads.login.url"));
		if (loginUrl != null) {
			driver.get(loginUrl);
			waitForUiToLoad();
			return;
		}

		if ("about:blank".equals(driver.getCurrentUrl())) {
			throw new IllegalStateException(
					"Set SALEADS_LOGIN_URL (or -Dsaleads.login.url) to the current environment login page.");
		}
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
	public void saleadsMiNegocioWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Informacion General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Terminos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Politica de Privacidad", this::stepValidatePoliticaPrivacidad);

		final List<String> failures = new ArrayList<>();
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			if (FAIL.equals(entry.getValue())) {
				failures.add(entry.getKey() + " -> " + details.getOrDefault(entry.getKey(), "No details"));
			}
		}

		assertTrue("Workflow has failures:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		clickByAnyVisibleText(
				"Sign in with Google",
				"Iniciar sesion con Google",
				"Iniciar sesion",
				"Continuar con Google",
				"Acceder con Google");
		selectGoogleAccountIfPresent();

		assertAnyTextVisible("Negocio", "Mi Negocio", "Dashboard", "Inicio");
		assertSidebarVisible();
		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		expandMiNegocioMenuIfNeeded();
		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Administrar Negocios");
		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		expandMiNegocioMenuIfNeeded();
		clickByAnyVisibleText("Agregar Negocio");
		assertAnyTextVisible("Crear Nuevo Negocio");
		findBusinessNameInput();
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertButtonVisible("Cancelar");
		assertButtonVisible("Crear Negocio");
		captureScreenshot("03_agregar_negocio_modal");

		final WebElement input = findBusinessNameInput();
		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatizacion");
		waitForUiToLoad();
		clickByAnyVisibleText("Cancelar");
		waitUntilTextInvisible("Crear Nuevo Negocio");
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioMenuIfNeeded();
		clickByAnyVisibleText("Administrar Negocios");
		assertAnyTextVisible("Informacion General", "Informaci\u00f3n General");
		assertAnyTextVisible("Detalles de la Cuenta");
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Seccion Legal", "Secci\u00f3n Legal");
		captureScreenshot("04_administrar_negocios");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Informacion General", "Informaci\u00f3n General");
		assertSectionContainsEmail(section);
		assertSectionContainsName(section);
		assertAnyTextVisible("BUSINESS PLAN");
		assertAnyTextVisible("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertAnyTextVisible("Cuenta creada");
		assertAnyTextVisible("Estado activo");
		assertAnyTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		assertSectionHasBusinessList(section);
		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		final String finalUrl = validateLegalLink(
				"Terminos y Condiciones",
				"T\u00e9rminos y Condiciones",
				"T\u00e9rminos y Condiciones",
				"05_terminos_y_condiciones");
		legalUrls.put("Terminos y Condiciones", finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		final String finalUrl = validateLegalLink(
				"Politica de Privacidad",
				"Pol\u00edtica de Privacidad",
				"Pol\u00edtica de Privacidad",
				"06_politica_de_privacidad");
		legalUrls.put("Politica de Privacidad", finalUrl);
	}

	private String validateLegalLink(
			final String plainLinkText,
			final String accentedLinkText,
			final String expectedHeading,
			final String screenshotName) throws IOException {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = driver.getWindowHandles();
		final String currentUrlBefore = driver.getCurrentUrl();

		clickByAnyVisibleText(plainLinkText, accentedLinkText);

		wait.until(d -> d.getWindowHandles().size() > windowsBefore.size()
				|| !Objects.equals(d.getCurrentUrl(), currentUrlBefore));

		final Set<String> windowsAfter = driver.getWindowHandles();
		String legalWindow = originalWindow;
		for (final String handle : windowsAfter) {
			if (!windowsBefore.contains(handle)) {
				legalWindow = handle;
				break;
			}
		}

		final boolean openedNewTab = !legalWindow.equals(originalWindow);
		if (openedNewTab) {
			driver.switchTo().window(legalWindow);
			waitForUiToLoad();
		}

		assertAnyTextVisible(expectedHeading, plainLinkText);
		assertLegalContentVisible();
		captureScreenshot(screenshotName);
		final String legalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return legalUrl;
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, PASS);
		} catch (final Throwable t) {
			report.put(stepName, FAIL);
			details.put(stepName, t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
		}
	}

	private void expandMiNegocioMenuIfNeeded() {
		final boolean addVisible = isTextVisible("Agregar Negocio");
		final boolean adminVisible = isTextVisible("Administrar Negocios");
		if (addVisible && adminVisible) {
			return;
		}

		clickIfPresent("Negocio");
		clickIfPresent("Mi Negocio");
		waitForUiToLoad();
	}

	private void selectGoogleAccountIfPresent() {
		if (isTextVisible(GOOGLE_ACCOUNT_EMAIL)) {
			clickByAnyVisibleText(GOOGLE_ACCOUNT_EMAIL);
		}
		waitForUiToLoad();
	}

	private void assertSidebarVisible() {
		final List<By> sideBarLocators = List.of(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar')]"));
		for (final By locator : sideBarLocators) {
			final List<WebElement> elements = driver.findElements(locator);
			if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
				return;
			}
		}

		assertAnyTextVisible("Negocio", "Mi Negocio");
	}

	private void assertButtonVisible(final String text) {
		final By locator = By.xpath("//button[contains(normalize-space(.), " + toXPathLiteral(text) + ")]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private WebElement findBusinessNameInput() {
		final List<By> locators = List.of(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"));

		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
				return elements.get(0);
			}
		}

		throw new IllegalStateException("Could not find 'Nombre del Negocio' input.");
	}

	private WebElement findSectionByHeading(final String... headings) {
		for (final String heading : headings) {
			final By locator = By.xpath(
					"//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::p][contains(normalize-space(.), "
							+ toXPathLiteral(heading)
							+ ")]/ancestor::*[self::section or self::div][1]");
			final List<WebElement> sections = driver.findElements(locator);
			if (!sections.isEmpty() && sections.get(0).isDisplayed()) {
				return sections.get(0);
			}
		}

		throw new IllegalStateException("Could not find section for headings: " + String.join(", ", headings));
	}

	private void assertSectionContainsEmail(final WebElement section) {
		final List<WebElement> emailMatches = section
				.findElements(By.xpath(".//*[contains(normalize-space(.), '@') and string-length(normalize-space(.)) > 5]"));
		assertFalse("Expected an email inside Informacion General section.", emailMatches.isEmpty());
	}

	private void assertSectionContainsName(final WebElement section) {
		final List<WebElement> textNodes = section.findElements(
				By.xpath(".//*[self::h1 or self::h2 or self::h3 or self::h4 or self::p or self::span or self::div][normalize-space(.)]"));

		for (final WebElement node : textNodes) {
			final String text = node.getText().trim();
			if (text.isEmpty()) {
				continue;
			}

			final String lower = text.toLowerCase();
			if (lower.contains("@")) {
				continue;
			}
			if (lower.contains("informacion general")
					|| lower.contains("información general")
					|| lower.contains("business plan")
					|| lower.contains("cambiar plan")) {
				continue;
			}

			return;
		}

		throw new IllegalStateException("Expected a user name value inside Informacion General section.");
	}

	private void assertSectionHasBusinessList(final WebElement section) {
		final List<WebElement> listCandidates = section.findElements(
				By.xpath(".//li[normalize-space()] | .//tr[normalize-space()] | .//*[contains(@class,'business') and normalize-space()]"));
		assertFalse("Expected at least one business item in 'Tus Negocios'.", listCandidates.isEmpty());
	}

	private void assertLegalContentVisible() {
		final List<WebElement> contentBlocks = driver.findElements(
				By.xpath("//p[string-length(normalize-space(.)) > 40] | //article//*[string-length(normalize-space(.)) > 40]"));
		assertFalse("Expected visible legal content text.", contentBlocks.isEmpty());
	}

	private void clickByAnyVisibleText(final String... texts) {
		final WebElement element = findByAnyVisibleText(texts);
		scrollIntoView(element);
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiToLoad();
	}

	private void clickIfPresent(final String text) {
		try {
			final WebElement element = findByAnyVisibleText(text);
			scrollIntoView(element);
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
			waitForUiToLoad();
		} catch (final Throwable t) {
			// Best-effort expansion.
		}
	}

	private WebElement findByAnyVisibleText(final String... texts) {
		for (final String text : texts) {
			final List<By> locators = List.of(
					By.xpath("//button[normalize-space(.) = " + toXPathLiteral(text) + "]"),
					By.xpath("//a[normalize-space(.) = " + toXPathLiteral(text) + "]"),
					By.xpath("//*[@role='button' and normalize-space(.) = " + toXPathLiteral(text) + "]"),
					By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text)
							+ ") and (self::button or self::a or @role='button' or self::div or self::span)]"));

			for (final By locator : locators) {
				final List<WebElement> elements = driver.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
		}

		throw new IllegalStateException("Could not find visible element with text(s): " + String.join(", ", texts));
	}

	private void assertAnyTextVisible(final String... texts) {
		wait.until(d -> {
			for (final String text : texts) {
				if (isTextVisible(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isTextVisible(final String text) {
		final By locator = By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]");
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void waitUntilTextInvisible(final String text) {
		final By locator = By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]");
		try {
			wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
		} catch (final TimeoutException e) {
			throw new IllegalStateException("Text remained visible: " + text, e);
		}
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(500L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
	}

	private void captureScreenshot(final String screenshotName) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDir.resolve(screenshotName + ".png");
		Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("# ").append(TEST_NAME).append('\n').append('\n');
		reportBuilder.append("| Checkpoint | Result |\n");
		reportBuilder.append("| --- | --- |\n");
		for (final String field : REPORT_FIELDS) {
			reportBuilder.append("| ")
					.append(field)
					.append(" | ")
					.append(report.getOrDefault(field, FAIL))
					.append(" |\n");
		}

		reportBuilder.append('\n').append("## Legal URLs").append('\n');
		if (legalUrls.isEmpty()) {
			reportBuilder.append("- Not captured.\n");
		} else {
			for (final Map.Entry<String, String> urlEntry : legalUrls.entrySet()) {
				reportBuilder.append("- ")
						.append(urlEntry.getKey())
						.append(": ")
						.append(urlEntry.getValue())
						.append('\n');
			}
		}

		reportBuilder.append('\n').append("## Failure Details").append('\n');
		if (details.isEmpty()) {
			reportBuilder.append("- None.\n");
		} else {
			for (final Map.Entry<String, String> detail : details.entrySet()) {
				reportBuilder.append("- ")
						.append(detail.getKey())
						.append(": ")
						.append(detail.getValue())
						.append('\n');
			}
		}

		final Path reportPath = evidenceDir.resolve("final-report.md");
		Files.writeString(reportPath, reportBuilder.toString(), StandardCharsets.UTF_8);
		System.out.println("SaleADS workflow evidence generated at: " + evidenceDir.toAbsolutePath());
	}

	private String toXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder literal = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				literal.append(", ");
			}
			final char c = chars[i];
			if (c == '\'') {
				literal.append("\"'\"");
			} else if (c == '"') {
				literal.append("'\"'");
			} else {
				literal.append('\'').append(c).append('\'');
			}
		}
		literal.append(')');
		return literal.toString();
	}

	private String firstNonBlank(final String first, final String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		if (second != null && !second.isBlank()) {
			return second;
		}
		return null;
	}

	private String envOrProperty(final String envName, final String propertyName, final String defaultValue) {
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		return defaultValue;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
