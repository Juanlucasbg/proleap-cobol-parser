package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> errors = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(readConfig("SALEADS_E2E_ENABLED", "saleads.e2e.enabled", "false"));
		Assume.assumeTrue(
				"Set SALEADS_E2E_ENABLED=true (or -Dsaleads.e2e.enabled=true) to run this external UI E2E test.",
				enabled);

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		artifactsDir = Path.of("target", "saleads-e2e-artifacts", runId);
		Files.createDirectories(artifactsDir);

		final boolean headless = Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "saleads.headless", "true"));
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String startUrl = readConfig("SALEADS_START_URL", "saleads.start.url", "").trim();
		if (!startUrl.isEmpty()) {
			driver.get(startUrl);
		}
		waitForUiLoad();
		assertTrue(
				"Browser is not on a valid login page. Provide SALEADS_START_URL for your current environment or preload the browser session.",
				isNotBlankPage(driver.getCurrentUrl()));
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalDocument(
				Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
				Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
				"08_terminos_y_condiciones",
				"Términos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalDocument(
				Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
				Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
				"09_politica_de_privacidad",
				"Política de Privacidad"));

		assertTrue(buildFailureSummary(), errors.isEmpty());
	}

	private void stepLoginWithGoogle() {
		clickByVisibleText(Arrays.asList(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Iniciar sesion con Google",
				"Continuar con Google",
				"Google"));

		selectGoogleAccountIfVisible();
		waitForUiLoad();

		assertAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Inicio", "Home"),
				"No se detectó la interfaz principal luego del login.");
		assertSidebarVisible();
		captureScreenshot("01_dashboard_cargado");
	}

	private void stepOpenMiNegocioMenu() {
		assertSidebarVisible();

		if (isAnyVisibleText(Arrays.asList("Negocio"))) {
			clickByVisibleText(Arrays.asList("Negocio"));
		}
		clickByVisibleText(Arrays.asList("Mi Negocio"));

		assertAnyVisibleText(Arrays.asList("Agregar Negocio"), "No se encontró la opción 'Agregar Negocio'.");
		assertAnyVisibleText(Arrays.asList("Administrar Negocios"), "No se encontró la opción 'Administrar Negocios'.");
		captureScreenshot("02_menu_mi_negocio_expandido");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText(Arrays.asList("Agregar Negocio"));
		assertAnyVisibleText(Arrays.asList("Crear Nuevo Negocio"), "No se encontró el título del modal.");

		final WebElement nombreInput = findElementByAny(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"));
		assertTrue("No se encontró el input 'Nombre del Negocio'.", nombreInput != null);

		assertAnyVisibleText(Arrays.asList("Tienes 2 de 3 negocios"), "No se encontró el texto de límites de negocios.");
		assertAnyVisibleText(Arrays.asList("Cancelar"), "No se encontró el botón 'Cancelar'.");
		assertAnyVisibleText(Arrays.asList("Crear Negocio"), "No se encontró el botón 'Crear Negocio'.");
		captureScreenshot("03_modal_crear_nuevo_negocio");

		nombreInput.click();
		nombreInput.sendKeys("Negocio Prueba Automatización");
		clickByVisibleText(Arrays.asList("Cancelar"));
	}

	private void stepOpenAdministrarNegocios() {
		if (!isAnyVisibleText(Arrays.asList("Administrar Negocios"))) {
			clickByVisibleText(Arrays.asList("Mi Negocio"));
		}

		clickByVisibleText(Arrays.asList("Administrar Negocios"));
		assertAnyVisibleText(Arrays.asList("Información General"), "No se encontró la sección 'Información General'.");
		assertAnyVisibleText(Arrays.asList("Detalles de la Cuenta"), "No se encontró la sección 'Detalles de la Cuenta'.");
		assertAnyVisibleText(Arrays.asList("Tus Negocios"), "No se encontró la sección 'Tus Negocios'.");
		assertAnyVisibleText(Arrays.asList("Sección Legal", "Seccion Legal"), "No se encontró la sección legal.");
		captureScreenshot("04_administrar_negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisibleText(Arrays.asList("Información General"), "No se encontró 'Información General'.");
		assertTrue("No se detectó un email visible para el usuario.", containsEmail(driver.findElement(By.tagName("body")).getText()));
		assertAnyVisibleText(Arrays.asList("BUSINESS PLAN"), "No se encontró el texto 'BUSINESS PLAN'.");
		assertAnyVisibleText(Arrays.asList("Cambiar Plan"), "No se encontró el botón 'Cambiar Plan'.");
		assertTrue("No se detectó un nombre de usuario visible en la sección general.",
				isAnyVisibleText(Arrays.asList("Nombre", "Usuario", "Perfil", "Account")));
	}

	private void stepValidateDetallesCuenta() {
		assertAnyVisibleText(Arrays.asList("Cuenta creada"), "No se encontró 'Cuenta creada'.");
		assertAnyVisibleText(Arrays.asList("Estado activo"), "No se encontró 'Estado activo'.");
		assertAnyVisibleText(Arrays.asList("Idioma seleccionado"), "No se encontró 'Idioma seleccionado'.");
	}

	private void stepValidateTusNegocios() {
		assertAnyVisibleText(Arrays.asList("Tus Negocios"), "No se encontró la sección 'Tus Negocios'.");
		assertAnyVisibleText(Arrays.asList("Agregar Negocio"), "No se encontró el botón 'Agregar Negocio'.");
		assertAnyVisibleText(Arrays.asList("Tienes 2 de 3 negocios"), "No se encontró el texto 'Tienes 2 de 3 negocios'.");
		assertTrue("No se detectó una lista de negocios visible.", isBusinessListVisible());
	}

	private void stepValidateLegalDocument(
			final List<String> linkTexts,
			final List<String> headingTexts,
			final String screenshotName,
			final String reportKey) {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickByVisibleText(linkTexts);

		final String newWindow = waitForNewWindow(beforeHandles, SHORT_TIMEOUT);
		if (newWindow != null) {
			driver.switchTo().window(newWindow);
		}
		waitForUiLoad();

		assertAnyVisibleText(headingTexts, "No se encontró el heading legal esperado para " + reportKey + ".");
		assertTrue("No se detectó contenido legal suficiente para " + reportKey + ".",
				normalizeText(driver.findElement(By.tagName("body")).getText()).length() > 120);

		legalUrls.put(reportKey, driver.getCurrentUrl());
		captureScreenshot(screenshotName);

		if (newWindow != null) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String stepName, final StepAction stepAction) {
		try {
			stepAction.run();
			report.put(stepName, true);
		} catch (final Throwable throwable) {
			report.put(stepName, false);
			final String message = throwable.getMessage() == null ? throwable.toString() : throwable.getMessage();
			errors.put(stepName, message);
		}
	}

	private void clickByVisibleText(final List<String> texts) {
		Throwable lastError = null;
		for (final String text : texts) {
			final By locator = By.xpath("//*[contains(normalize-space(.), " + xPathLiteral(text) + ")]");
			try {
				final List<WebElement> candidates = new WebDriverWait(driver, SHORT_TIMEOUT)
						.until(d -> d.findElements(locator));
				for (final WebElement candidate : candidates) {
					if (!candidate.isDisplayed()) {
						continue;
					}
					try {
						scrollIntoView(candidate);
						candidate.click();
						waitForUiLoad();
						return;
					} catch (final Exception clickException) {
						try {
							scrollIntoView(candidate);
							((JavascriptExecutor) driver).executeScript("arguments[0].click();", candidate);
							waitForUiLoad();
							return;
						} catch (final Exception jsClickException) {
							lastError = jsClickException;
						}
					}
				}
			} catch (final Exception e) {
				lastError = e;
			}
		}
		throw new AssertionError("No se pudo hacer click sobre ninguno de los textos: " + texts, lastError);
	}

	private void selectGoogleAccountIfVisible() {
		final String currentHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String newWindow = waitForNewWindow(handlesBefore, Duration.ofSeconds(6));
		if (newWindow != null) {
			driver.switchTo().window(newWindow);
			waitForUiLoad();
		}

		if (isAnyVisibleText(Arrays.asList(GOOGLE_ACCOUNT))) {
			clickByVisibleText(Arrays.asList(GOOGLE_ACCOUNT));
		}

		waitForUiLoad();
		if (!driver.getWindowHandle().equals(currentHandle) && driver.getWindowHandles().contains(currentHandle)) {
			driver.switchTo().window(currentHandle);
			waitForUiLoad();
		}
	}

	private void assertSidebarVisible() {
		final List<By> locators = Arrays.asList(
				By.tagName("aside"),
				By.xpath("//nav"),
				By.xpath("//*[@role='navigation']"));
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}
		throw new AssertionError("No se detectó barra lateral visible.");
	}

	private void assertAnyVisibleText(final List<String> texts, final String errorMessage) {
		assertTrue(errorMessage + " Textos buscados: " + texts, isAnyVisibleText(texts));
	}

	private boolean isAnyVisibleText(final List<String> texts) {
		for (final String text : texts) {
			final By locator = By.xpath("//*[contains(normalize-space(.), " + xPathLiteral(text) + ")]");
			try {
				final List<WebElement> elements = new WebDriverWait(driver, SHORT_TIMEOUT)
						.until(d -> d.findElements(locator));
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			} catch (final TimeoutException ignored) {
				// Try next text.
			}
		}
		return false;
	}

	private boolean isBusinessListVisible() {
		final List<By> listLocators = Arrays.asList(
				By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]/following::*[self::li or self::tr][1]"),
				By.xpath("//*[contains(@class,'business') and not(self::script)]"),
				By.xpath("//*[@role='listitem']"));
		for (final By locator : listLocators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private WebElement findElementByAny(final By... locators) {
		for (final By locator : locators) {
			try {
				final List<WebElement> elements = new WebDriverWait(driver, SHORT_TIMEOUT)
						.until(d -> d.findElements(locator));
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			} catch (final TimeoutException ignored) {
				// Try next locator.
			}
		}
		return null;
	}

	private void waitForUiLoad() {
		final ExpectedCondition<Boolean> jsLoad = wd -> {
			final Object readyState = ((JavascriptExecutor) wd).executeScript("return document.readyState");
			return readyState != null && "complete".equals(readyState.toString());
		};
		wait.until(jsLoad);
	}

	private String waitForNewWindow(final Set<String> previousHandles, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(d -> d.getWindowHandles().size() > previousHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!previousHandles.contains(handle)) {
					return handle;
				}
			}
			return null;
		} catch (final TimeoutException ignored) {
			return null;
		}
	}

	private void captureScreenshot(final String name) {
		try {
			final byte[] image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(artifactsDir.resolve(name + ".png"), image);
		} catch (final IOException e) {
			errors.put("screenshot:" + name, "No se pudo guardar screenshot: " + e.getMessage());
		}
	}

	private void writeFinalReport() throws IOException {
		if (artifactsDir == null) {
			return;
		}

		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		builder.append("Artifacts directory: ").append(artifactsDir.toAbsolutePath()).append(System.lineSeparator());
		builder.append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			final boolean passed = Boolean.TRUE.equals(report.get(field));
			builder.append(field).append(": ").append(passed ? "PASS" : "FAIL").append(System.lineSeparator());
			if (!passed && errors.containsKey(field)) {
				builder.append("  reason: ").append(errors.get(field)).append(System.lineSeparator());
			}
		}

		builder.append(System.lineSeparator());
		if (!legalUrls.isEmpty()) {
			builder.append("Captured legal URLs:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		if (!errors.isEmpty()) {
			builder.append(System.lineSeparator()).append("Other captured errors:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : errors.entrySet()) {
				if (!REPORT_FIELDS.contains(entry.getKey())) {
					builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
				}
			}
		}

		final Path reportFile = artifactsDir.resolve("final-report.txt");
		Files.writeString(reportFile, builder.toString(), StandardCharsets.UTF_8);
		System.out.println(builder);
	}

	private String buildFailureSummary() {
		if (errors.isEmpty()) {
			return "All workflow validations passed.";
		}

		final List<String> failed = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			if (Boolean.FALSE.equals(report.get(field))) {
				final String reason = errors.getOrDefault(field, "Unknown validation failure.");
				failed.add(field + " -> " + reason);
			}
		}
		return "Failed workflow validations: " + failed;
	}

	private String readConfig(final String envKey, final String propertyKey, final String defaultValue) {
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		return defaultValue;
	}

	private boolean isNotBlankPage(final String currentUrl) {
		if (currentUrl == null) {
			return false;
		}
		final String normalized = currentUrl.trim().toLowerCase(Locale.ROOT);
		return !(normalized.isEmpty()
				|| normalized.equals("about:blank")
				|| normalized.equals("data:,"));
	}

	private boolean containsEmail(final String text) {
		return Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
				.matcher(text == null ? "" : text)
				.find();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private String normalizeText(final String value) {
		if (value == null) {
			return "";
		}
		return Normalizer.normalize(value, Normalizer.Form.NFKC).replace('\u00A0', ' ').trim();
	}

	private String xPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			result.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				result.append(",\"'\",");
			}
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
