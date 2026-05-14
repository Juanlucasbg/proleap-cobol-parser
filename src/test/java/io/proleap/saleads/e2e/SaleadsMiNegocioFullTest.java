package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration UI_SETTLE_DELAY = Duration.ofMillis(700);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String REPORT_INFORMACION_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Path reportFile;
	private final Map<String, String> report = new LinkedHashMap<>();
	private String terminosUrl = "N/A";
	private String privacidadUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-gpu", "--window-size=1920,1080");
		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);

		evidenceDir = Path.of("target", "saleads-evidence", TEST_NAME + "-" + DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-"));
		Files.createDirectories(evidenceDir);
		reportFile = evidenceDir.resolve("final-report.txt");
		initializeReport();

		final String startUrl = readConfig("saleads.url", "SALEADS_URL", null);
		if (startUrl != null && !startUrl.isBlank()) {
			driver.get(startUrl);
			waitForUiToLoad();
		}
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
		final boolean loginOk = safeStep(REPORT_LOGIN, this::stepLoginAndValidate);
		final boolean menuOk = safeStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		final boolean agregarModalOk = safeStep(REPORT_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		final boolean administrarOk = safeStep(REPORT_ADMINISTRAR_NEGOCIOS, this::stepOpenAdministrarNegocios);
		final boolean infoGeneralOk = safeStep(REPORT_INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		final boolean detallesOk = safeStep(REPORT_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		final boolean tusNegociosOk = safeStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		final boolean terminosOk = safeStep(REPORT_TERMINOS, this::stepValidateTerminosYCondiciones);
		final boolean privacidadOk = safeStep(REPORT_PRIVACIDAD, this::stepValidatePoliticaPrivacidad);

		writeFinalReport();

		final boolean allPassed = loginOk && menuOk && agregarModalOk && administrarOk && infoGeneralOk && detallesOk
				&& tusNegociosOk && terminosOk && privacidadOk;

		assertTrue("Workflow validation failed. Review evidence in: " + evidenceDir.toAbsolutePath(), allPassed);
	}

	private boolean stepLoginAndValidate() throws IOException {
		if (driver.getCurrentUrl() == null || driver.getCurrentUrl().startsWith("data:")) {
			throw new AssertionError("Browser did not start on the SaleADS login page. Set SALEADS_URL or -Dsaleads.url.");
		}

		final Optional<WebElement> loginButton = findVisibleElementByText(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Iniciar sesion con Google",
				"Continuar con Google",
				"Login with Google");
		if (loginButton.isEmpty()) {
			throw new AssertionError("Could not locate a Google login button on the current page.");
		}

		clickAndWait(loginButton.get());
		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");

		final boolean dashboardVisible = isAnyTextVisible("Dashboard", "Inicio", "Negocio", "Mi Negocio");
		final boolean sidebarVisible = isSidebarVisible();
		captureScreenshot("01-dashboard-loaded");
		assertTrue("Expected the main application interface to be visible after login.", dashboardVisible);
		assertTrue("Expected the left sidebar navigation to be visible.", sidebarVisible);
		return true;
	}

	private boolean stepOpenMiNegocioMenu() throws IOException {
		ensureSidebarLoaded();

		clickTextIfVisible("Negocio");
		clickTextIfVisible("Mi Negocio");

		wait.until(driver -> isTextVisible("Agregar Negocio") && isTextVisible("Administrar Negocios"));
		captureScreenshot("02-mi-negocio-expanded");

		assertTrue("Expected 'Agregar Negocio' to be visible.", isTextVisible("Agregar Negocio"));
		assertTrue("Expected 'Administrar Negocios' to be visible.", isTextVisible("Administrar Negocios"));
		return true;
	}

	private boolean stepValidateAgregarNegocioModal() throws IOException {
		clickText("Agregar Negocio");
		waitForUiToLoad();

		assertTrue("Expected modal title 'Crear Nuevo Negocio'.", isTextVisible("Crear Nuevo Negocio"));
		assertTrue("Expected text 'Tienes 2 de 3 negocios'.", isTextVisible("Tienes 2 de 3 negocios"));
		assertTrue("Expected button 'Cancelar'.", isTextVisible("Cancelar"));
		assertTrue("Expected button 'Crear Negocio'.", isTextVisible("Crear Negocio"));

		final WebElement businessNameInput = findBusinessNameInput()
				.orElseThrow(() -> new AssertionError("Expected input field 'Nombre del Negocio'."));
		businessNameInput.click();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		captureScreenshot("03-crear-nuevo-negocio-modal");
		clickText("Cancelar");
		waitForUiToLoad();
		return true;
	}

	private boolean stepOpenAdministrarNegocios() throws IOException {
		ensureMiNegocioExpanded();
		clickText("Administrar Negocios");
		waitForUiToLoad();

		assertTrue("Expected section 'Informacion General'.", isAnyTextVisible("Información General", "Informacion General"));
		assertTrue("Expected section 'Detalles de la Cuenta'.", isTextVisible("Detalles de la Cuenta"));
		assertTrue("Expected section 'Tus Negocios'.", isTextVisible("Tus Negocios"));
		assertTrue("Expected section 'Seccion Legal'.", isAnyTextVisible("Sección Legal", "Seccion Legal"));
		captureScreenshot("04-administrar-negocios-view");
		return true;
	}

	private boolean stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General", "Informacion General")
				.orElseThrow(() -> new AssertionError("Could not locate 'Informacion General' section."));

		final List<String> sectionLines = normalizeLines(section.getText());
		final boolean hasEmail = sectionLines.stream().anyMatch(line -> EMAIL_PATTERN.matcher(line).matches());
		final boolean hasUserName = sectionLines.stream().anyMatch(line -> !line.equalsIgnoreCase("Informacion General")
				&& !line.equalsIgnoreCase("BUSINESS PLAN")
				&& !line.equalsIgnoreCase("Cambiar Plan")
				&& !EMAIL_PATTERN.matcher(line).matches()
				&& line.length() >= 3);

		assertTrue("Expected user name to be visible in 'Informacion General'.", hasUserName);
		assertTrue("Expected user email to be visible in 'Informacion General'.", hasEmail);
		assertTrue("Expected text 'BUSINESS PLAN'.", containsTextInSection(section, "BUSINESS PLAN"));
		assertTrue("Expected button 'Cambiar Plan'.", containsTextInSection(section, "Cambiar Plan"));
		return true;
	}

	private boolean stepValidateDetallesCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta")
				.orElseThrow(() -> new AssertionError("Could not locate 'Detalles de la Cuenta' section."));

		assertTrue("Expected 'Cuenta creada' in account details.", containsTextInSection(section, "Cuenta creada"));
		assertTrue("Expected 'Estado activo' in account details.", containsTextInSection(section, "Estado activo"));
		assertTrue("Expected 'Idioma seleccionado' in account details.", containsTextInSection(section, "Idioma seleccionado"));
		return true;
	}

	private boolean stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios")
				.orElseThrow(() -> new AssertionError("Could not locate 'Tus Negocios' section."));

		final boolean businessRowsVisible = !section.findElements(By.xpath(".//*[self::li or self::tr or self::div][normalize-space()!='']")).isEmpty();
		assertTrue("Expected business list content in 'Tus Negocios'.", businessRowsVisible);
		assertTrue("Expected 'Agregar Negocio' button in 'Tus Negocios'.", containsTextInSection(section, "Agregar Negocio"));
		assertTrue("Expected text 'Tienes 2 de 3 negocios' in 'Tus Negocios'.", containsTextInSection(section, "Tienes 2 de 3 negocios"));
		return true;
	}

	private boolean stepValidateTerminosYCondiciones() throws IOException {
		terminosUrl = openLegalLinkAndValidate("Términos y Condiciones", "Terminos y Condiciones", "08-terminos-y-condiciones");
		return true;
	}

	private boolean stepValidatePoliticaPrivacidad() throws IOException {
		privacidadUrl = openLegalLinkAndValidate("Política de Privacidad", "Politica de Privacidad", "09-politica-privacidad");
		return true;
	}

	private String openLegalLinkAndValidate(final String linkText, final String headingAscii, final String screenshotName)
			throws IOException {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickAnyText(linkText, headingAscii);

		waitForUiToLoad();
		final Set<String> handlesAfterClick = driver.getWindowHandles();

		if (handlesAfterClick.size() > handlesBeforeClick.size()) {
			for (final String handle : handlesAfterClick) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForUiToLoad();
		}

		final boolean headingVisible = isAnyTextVisible(linkText, headingAscii);
		assertTrue("Expected heading for legal page: " + linkText, headingVisible);

		final String bodyText = Optional.ofNullable(driver.findElement(By.tagName("body")).getText()).orElse("");
		assertTrue("Expected legal content text to be visible for " + linkText + ".", bodyText.trim().length() > 80);
		captureScreenshot(screenshotName);
		final String currentUrl = driver.getCurrentUrl();

		if (driver.getWindowHandles().size() > 1) {
			driver.close();
			driver.switchTo().window(originalWindow);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		return currentUrl;
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
			final By emailCell = By.xpath("//*[normalize-space()='" + accountEmail + "']");
			final WebElement accountOption = shortWait.until(ExpectedConditions.visibilityOfElementLocated(emailCell));
			clickAndWait(accountOption);
		} catch (final TimeoutException ex) {
			// If account chooser is not shown, flow may already be authenticated.
		}
	}

	private Optional<WebElement> findBusinessNameInput() {
		final List<By> candidates = List.of(
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@name,'negocio') or contains(@id,'negocio')]"));
		for (final By by : candidates) {
			try {
				final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(by));
				return Optional.of(element);
			} catch (final TimeoutException ex) {
				// continue searching
			}
		}
		return Optional.empty();
	}

	private Optional<WebElement> findSectionByHeading(final String... headings) {
		for (final String heading : headings) {
			final String xpath = "//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::p]"
					+ "[normalize-space()=" + toXPathLiteral(heading) + "]/ancestor::*[self::section or self::div][1]";
			final List<WebElement> candidates = driver.findElements(By.xpath(xpath));
			final Optional<WebElement> visible = candidates.stream().filter(WebElement::isDisplayed).findFirst();
			if (visible.isPresent()) {
				return visible;
			}
		}
		return Optional.empty();
	}

	private boolean containsTextInSection(final WebElement section, final String text) {
		return section.getText().toLowerCase().contains(text.toLowerCase());
	}

	private void ensureSidebarLoaded() {
		wait.until((ExpectedCondition<Boolean>) driver -> isSidebarVisible());
	}

	private void ensureMiNegocioExpanded() {
		ensureSidebarLoaded();
		if (!isTextVisible("Administrar Negocios")) {
			clickTextIfVisible("Negocio");
			clickTextIfVisible("Mi Negocio");
		}
		wait.until(driver -> isTextVisible("Administrar Negocios"));
	}

	private boolean isSidebarVisible() {
		final List<By> candidates = List.of(
				By.xpath("//aside"),
				By.xpath("//*[contains(@class,'sidebar')]"),
				By.xpath("//*[contains(@class,'navigation')]"));
		for (final By by : candidates) {
			final List<WebElement> elements = driver.findElements(by);
			if (elements.stream().anyMatch(WebElement::isDisplayed)) {
				return true;
			}
		}
		return false;
	}

	private boolean isTextVisible(final String text) {
		return findVisibleElementByText(text).isPresent();
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return true;
			}
		}
		return false;
	}

	private Optional<WebElement> findVisibleElementByText(final String... texts) {
		for (final String text : texts) {
			final String escaped = toXPathLiteral(text);
			final String xpath = "//*[normalize-space()=" + escaped + "]"
					+ " | //*[@role='button' or @role='menuitem'][normalize-space()=" + escaped + "]"
					+ " | //*[normalize-space()=" + escaped + "]/ancestor::*[self::button or self::a or @role='button' or @role='menuitem'][1]";
			final List<WebElement> elements = driver.findElements(By.xpath(xpath));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return Optional.of(element);
				}
			}
		}
		return Optional.empty();
	}

	private void clickTextIfVisible(final String text) {
		findVisibleElementByText(text).ifPresent(this::clickAndWait);
	}

	private void clickText(final String text) {
		final WebElement element = findVisibleElementByText(text)
				.orElseThrow(() -> new AssertionError("Could not find visible element with text: " + text));
		clickAndWait(element);
	}

	private void clickAnyText(final String... texts) {
		final WebElement element = findVisibleElementByText(texts)
				.orElseThrow(() -> new AssertionError("Could not find visible element with expected text options."));
		clickAndWait(element);
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(driver -> {
			if (!(driver instanceof JavascriptExecutor)) {
				return true;
			}
			final Object readyState = ((JavascriptExecutor) driver).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(readyState)) || "interactive".equals(String.valueOf(readyState));
		});
		try {
			Thread.sleep(UI_SETTLE_DELAY.toMillis());
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String name) throws IOException {
		final Path screenshot = evidenceDir.resolve(name + ".png");
		final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(screenshot, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private boolean safeStep(final String reportKey, final Step step) {
		try {
			final boolean ok = step.run();
			report.put(reportKey, ok ? "PASS" : "FAIL");
			return ok;
		} catch (final Throwable throwable) {
			report.put(reportKey, "FAIL - " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
			try {
				captureScreenshot("error-" + normalizeFileName(reportKey));
			} catch (final IOException ignored) {
				// ignore capture errors on failure path
			}
			return false;
		}
	}

	private void initializeReport() {
		report.put(REPORT_LOGIN, "NOT_RUN");
		report.put(REPORT_MI_NEGOCIO_MENU, "NOT_RUN");
		report.put(REPORT_AGREGAR_NEGOCIO_MODAL, "NOT_RUN");
		report.put(REPORT_ADMINISTRAR_NEGOCIOS, "NOT_RUN");
		report.put(REPORT_INFORMACION_GENERAL, "NOT_RUN");
		report.put(REPORT_DETALLES_CUENTA, "NOT_RUN");
		report.put(REPORT_TUS_NEGOCIOS, "NOT_RUN");
		report.put(REPORT_TERMINOS, "NOT_RUN");
		report.put(REPORT_PRIVACIDAD, "NOT_RUN");
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("Test: " + TEST_NAME);
		lines.add("Executed at: " + DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
		lines.add("");
		lines.add("Validation summary:");
		report.forEach((key, value) -> lines.add("- " + key + ": " + value));
		lines.add("");
		lines.add("Final URL - Terminos y Condiciones: " + terminosUrl);
		lines.add("Final URL - Politica de Privacidad: " + privacidadUrl);

		Files.write(reportFile, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private String readConfig(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		return defaultValue;
	}

	private String normalizeFileName(final String input) {
		return input.toLowerCase().replaceAll("[^a-z0-9]+", "-");
	}

	private List<String> normalizeLines(final String text) {
		final List<String> lines = new ArrayList<>();
		for (final String line : text.split("\\R")) {
			final String normalized = line.trim();
			if (!normalized.isEmpty()) {
				lines.add(normalized);
			}
		}
		return lines;
	}

	private String toXPathLiteral(final String input) {
		if (!input.contains("'")) {
			return "'" + input + "'";
		}
		if (!input.contains("\"")) {
			return "\"" + input + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = input.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part = String.valueOf(chars[i]);
			if (i > 0) {
				result.append(",");
			}
			if ("'".equals(part)) {
				result.append("\"").append(part).append("\"");
			} else {
				result.append("'").append(part).append("'");
			}
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface Step {
		boolean run() throws Exception;
	}
}
