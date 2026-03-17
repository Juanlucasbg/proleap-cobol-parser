package io.proleap.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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

/**
 * End-to-end workflow validation for the SaleADS "Mi Negocio" module.
 *
 * <p>
 * This test is intentionally environment-agnostic. It never hardcodes a SaleADS
 * domain and relies on environment variables instead.
 * </p>
 *
 * <ul>
 * <li>SALEADS_E2E_ENABLED=true (required to run)</li>
 * <li>SALEADS_LOGIN_URL=https://current-environment/login (required)</li>
 * <li>SALEADS_HEADLESS=true|false (optional, default true)</li>
 * </ul>
 */
public class SaleAdsMiNegocioWorkflowE2ETest {

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Informacion General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "Terminos y Condiciones";
	private static final String PRIVACIDAD = "Politica de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private Path reportDir;

	private final Map<String, Boolean> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String enabled = System.getenv("SALEADS_E2E_ENABLED");
		assumeTrue(
				"Skipping SaleADS E2E test. Set SALEADS_E2E_ENABLED=true to execute.",
				"true".equalsIgnoreCase(enabled));

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		assumeTrue(
				"Skipping SaleADS E2E test. Set SALEADS_LOGIN_URL to current environment login page.",
				loginUrl != null && !loginUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(20));

		screenshotDir = Paths.get("target", "saleads", "screenshots");
		reportDir = Paths.get("target", "saleads", "reports");
		Files.createDirectories(screenshotDir);
		Files.createDirectories(reportDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() throws Exception {
		final boolean loginOk = runStep(LOGIN, this::stepLoginWithGoogle);

		final boolean menuOk;
		if (loginOk) {
			menuOk = runStep(MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		} else {
			menuOk = markBlocked(MI_NEGOCIO_MENU, LOGIN);
		}

		final boolean agregarModalOk;
		if (menuOk) {
			agregarModalOk = runStep(AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		} else {
			agregarModalOk = markBlocked(AGREGAR_NEGOCIO_MODAL, MI_NEGOCIO_MENU);
		}

		final boolean administrarOk;
		if (menuOk) {
			administrarOk = runStep(ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		} else {
			administrarOk = markBlocked(ADMINISTRAR_NEGOCIOS_VIEW, MI_NEGOCIO_MENU);
		}

		final boolean infoGeneralOk;
		if (administrarOk) {
			infoGeneralOk = runStep(INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		} else {
			infoGeneralOk = markBlocked(INFORMACION_GENERAL, ADMINISTRAR_NEGOCIOS_VIEW);
		}

		final boolean detallesOk;
		if (administrarOk) {
			detallesOk = runStep(DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		} else {
			detallesOk = markBlocked(DETALLES_CUENTA, ADMINISTRAR_NEGOCIOS_VIEW);
		}

		final boolean tusNegociosOk;
		if (administrarOk) {
			tusNegociosOk = runStep(TUS_NEGOCIOS, this::stepValidateTusNegocios);
		} else {
			tusNegociosOk = markBlocked(TUS_NEGOCIOS, ADMINISTRAR_NEGOCIOS_VIEW);
		}

		final boolean terminosOk;
		if (administrarOk) {
			terminosOk = runStep(TERMINOS, () -> stepValidateLegalPage("Terminos y Condiciones"));
		} else {
			terminosOk = markBlocked(TERMINOS, ADMINISTRAR_NEGOCIOS_VIEW);
		}

		final boolean privacidadOk;
		if (administrarOk) {
			privacidadOk = runStep(PRIVACIDAD, () -> stepValidateLegalPage("Politica de Privacidad"));
		} else {
			privacidadOk = markBlocked(PRIVACIDAD, ADMINISTRAR_NEGOCIOS_VIEW);
		}

		writeFinalReport();

		assertTrue(LOGIN + " validation failed.", loginOk);
		assertTrue(MI_NEGOCIO_MENU + " validation failed.", menuOk);
		assertTrue(AGREGAR_NEGOCIO_MODAL + " validation failed.", agregarModalOk);
		assertTrue(ADMINISTRAR_NEGOCIOS_VIEW + " validation failed.", administrarOk);
		assertTrue(INFORMACION_GENERAL + " validation failed.", infoGeneralOk);
		assertTrue(DETALLES_CUENTA + " validation failed.", detallesOk);
		assertTrue(TUS_NEGOCIOS + " validation failed.", tusNegociosOk);
		assertTrue(TERMINOS + " validation failed.", terminosOk);
		assertTrue(PRIVACIDAD + " validation failed.", privacidadOk);
	}

	private boolean runStep(final String stepName, final CheckedRunnable action) {
		try {
			action.run();
			stepStatus.put(stepName, Boolean.TRUE);
			return true;
		} catch (final Exception ex) {
			stepStatus.put(stepName, Boolean.FALSE);
			stepErrors.put(stepName, ex.getMessage() == null ? ex.toString() : ex.getMessage());
			try {
				captureScreenshot("failed-" + slug(stepName));
			} catch (final Exception ignored) {
				// Best effort screenshot only.
			}
			return false;
		}
	}

	private boolean markBlocked(final String stepName, final String dependency) {
		stepStatus.put(stepName, Boolean.FALSE);
		stepErrors.put(stepName, "Blocked due to failed prerequisite: " + dependency);
		return false;
	}

	private void stepLoginWithGoogle() throws Exception {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		driver.get(loginUrl);
		waitForUiLoad();

		clickByTextAny("Sign in with Google", "Iniciar sesion con Google", "Continuar con Google", "Google");
		waitForUiLoad();

		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");
		waitForUiLoad();

		assertAnyVisibleText(
				"Dashboard",
				"Inicio",
				"Negocio",
				"Mi Negocio");
		assertAnyVisibleText("Negocio", "Mi Negocio");
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		ensureSidebarVisible();

		clickByTextAny("Negocio");
		waitForUiLoad();
		clickByTextAny("Mi Negocio");
		waitForUiLoad();

		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByTextAny("Agregar Negocio");
		waitForUiLoad();

		assertAnyVisibleText("Crear Nuevo Negocio");
		assertAnyVisibleText("Nombre del Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
		assertAnyVisibleText("Cancelar");
		assertAnyVisibleText("Crear Negocio");

		fillInputByLabel("Nombre del Negocio", "Negocio Prueba Automatizacion");
		clickByTextAny("Cancelar");
		waitForUiLoad();
		captureScreenshot("03-agregar-negocio-modal");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickByTextAny("Mi Negocio");
			waitForUiLoad();
		}

		clickByTextAny("Administrar Negocios");
		waitForUiLoad();

		assertAnyVisibleText("Informacion General", "Informacion general");
		assertAnyVisibleText("Detalles de la Cuenta", "Detalles de la cuenta");
		assertAnyVisibleText("Tus Negocios", "Tus negocios");
		assertAnyVisibleText("Seccion Legal", "Seccion legal");
		captureScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		assertAnyVisibleText("Informacion General", "Informacion general");
		assertAnyVisibleText("BUSINESS PLAN");
		assertAnyVisibleText("Cambiar Plan", "Cambiar plan");

		if (!isAnyTextVisible("juanlucasbarbiergarzon@gmail.com")) {
			assertAnyVisibleText("@");
		}

		assertSectionHasDynamicContent("Informacion General");
	}

	private void stepValidateDetallesCuenta() throws Exception {
		assertAnyVisibleText("Cuenta creada");
		assertAnyVisibleText("Estado activo", "Activo");
		assertAnyVisibleText("Idioma seleccionado", "Idioma");
	}

	private void stepValidateTusNegocios() throws Exception {
		assertAnyVisibleText("Tus Negocios", "Tus negocios");
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalPage(final String linkTitleWithoutAccent) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final String previousUrl = driver.getCurrentUrl();
		final Set<String> beforeClickHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickByTextAny(linkTitleWithoutAccent, toAccented(linkTitleWithoutAccent));

		boolean openedNewTab = false;
		try {
			wait.until(drv -> drv.getWindowHandles().size() > beforeClickHandles.size());
			openedNewTab = true;
		} catch (final TimeoutException ex) {
			wait.until(drv -> !drv.getCurrentUrl().equals(previousUrl));
		}
		waitForUiLoad();

		if (openedNewTab) {
			final Set<String> currentHandles = new LinkedHashSet<>(driver.getWindowHandles());
			currentHandles.removeAll(beforeClickHandles);
			final String newTabHandle = currentHandles.iterator().next();
			driver.switchTo().window(newTabHandle);
			waitForUiLoad();
		}

		assertAnyVisibleText(linkTitleWithoutAccent, toAccented(linkTitleWithoutAccent));
		assertAnyLegalContentVisible();
		captureScreenshot("legal-" + slug(linkTitleWithoutAccent));
		legalUrls.put(linkTitleWithoutAccent, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void clickByTextAny(final String... candidates) {
		final WebElement element = wait.until(driverInstance -> {
			for (final String candidate : candidates) {
				if (candidate == null || candidate.isBlank()) {
					continue;
				}

				final List<By> locators = List.of(
						By.xpath("//*[normalize-space()='" + escapeXpath(candidate) + "']"),
						By.xpath("//*[contains(normalize-space(),'" + escapeXpath(candidate) + "')]"));

				for (final By locator : locators) {
					for (final WebElement found : driver.findElements(locator)) {
						if (found.isDisplayed()) {
							return found;
						}
					}
				}
			}
			return null;
		});

		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiLoad();
	}

	private void assertAnyVisibleText(final String... candidates) {
		final boolean visible = wait.until(driverInstance -> isAnyTextVisible(candidates));
		assertTrue("Expected visible text not found: " + String.join(", ", candidates), visible);
	}

	private boolean isAnyTextVisible(final String... candidates) {
		for (final String candidate : candidates) {
			if (candidate == null || candidate.isBlank()) {
				continue;
			}

			final List<By> locators = List.of(
					By.xpath("//*[normalize-space()='" + escapeXpath(candidate) + "']"),
					By.xpath("//*[contains(normalize-space(),'" + escapeXpath(candidate) + "')]"));

			for (final By locator : locators) {
				for (final WebElement element : driver.findElements(locator)) {
					if (element.isDisplayed()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void fillInputByLabel(final String labelText, final String value) {
		final List<By> locators = List.of(
				By.xpath("//label[contains(normalize-space(),'" + escapeXpath(labelText) + "')]/following::input[1]"),
				By.xpath("//input[@placeholder='" + escapeXpath(labelText) + "']"),
				By.xpath("//input[contains(@aria-label,'" + escapeXpath(labelText) + "')]"));

		WebElement targetInput = null;
		for (final By locator : locators) {
			final List<WebElement> candidates = driver.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					targetInput = candidate;
					break;
				}
			}
			if (targetInput != null) {
				break;
			}
		}

		if (targetInput == null) {
			throw new IllegalStateException("Could not locate input for label: " + labelText);
		}

		wait.until(ExpectedConditions.visibilityOf(targetInput));
		targetInput.clear();
		targetInput.sendKeys(value);
		waitForUiLoad();
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(6));
			final WebElement account = shortWait.until(driverInstance -> {
				final List<WebElement> matches = driver.findElements(
						By.xpath("//*[contains(normalize-space(),'" + escapeXpath(accountEmail) + "')]"));
				for (final WebElement match : matches) {
					if (match.isDisplayed()) {
						return match;
					}
				}
				return null;
			});

			account.click();
			waitForUiLoad();
		} catch (final TimeoutException ignored) {
			// Account selector is optional depending on session state.
		}
	}

	private void assertAnyLegalContentVisible() {
		final List<String> legalContentHints = List.of(
				"ultimo",
				"actualizacion",
				"privacidad",
				"condiciones",
				"datos",
				"terminos",
				"informacion");

		final boolean found = wait.until(driverInstance -> {
			final String bodyText = driver.findElement(By.tagName("body")).getText().toLowerCase();
			for (final String hint : legalContentHints) {
				if (bodyText.contains(hint)) {
					return true;
				}
			}
			return false;
		});

		assertTrue("Expected legal content body text was not found.", found);
	}

	private void ensureSidebarVisible() {
		assertAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Inicio");
	}

	private void assertSectionHasDynamicContent(final String sectionTitle) {
		final List<WebElement> sectionHeaders = driver.findElements(
				By.xpath("//*[contains(normalize-space(),'" + escapeXpath(sectionTitle) + "')]"));

		for (final WebElement header : sectionHeaders) {
			if (!header.isDisplayed()) {
				continue;
			}

			final String scopedText = header.findElement(By.xpath("./ancestor-or-self::*[1]")).getText();
			if (scopedText != null && scopedText.trim().length() > sectionTitle.length() + 20) {
				return;
			}
		}

		final String pageText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected dynamic user details in section: " + sectionTitle, pageText.contains("@"));
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final Path destination = screenshotDir.resolve(
				DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(Instant.now()) + "-" + slug(checkpointName)
						+ ".png");
		final byte[] imageBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(destination, imageBytes);
	}

	private void waitForUiLoad() {
		wait.until(driverInstance -> "complete".equals(
				((JavascriptExecutor) driverInstance).executeScript("return document.readyState")));
		wait.until(driverInstance -> ((Long) ((JavascriptExecutor) driverInstance)
				.executeScript("return document.querySelectorAll('.loading,.spinner,[aria-busy=\"true\"]').length"))
				>= 0L);
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("{");
		lines.add("  \"generatedAt\": \"" + Instant.now().toString() + "\",");
		lines.add("  \"results\": {");
		final List<Map.Entry<String, Boolean>> entries = new ArrayList<>(stepStatus.entrySet());
		for (int i = 0; i < entries.size(); i++) {
			final Map.Entry<String, Boolean> entry = entries.get(i);
			final String comma = i == entries.size() - 1 ? "" : ",";
			lines.add("    \"" + escapeJson(entry.getKey()) + "\": \"" + (entry.getValue() ? "PASS" : "FAIL") + "\""
					+ comma);
		}
		lines.add("  },");
		lines.add("  \"legalUrls\": {");
		final List<Map.Entry<String, String>> legalEntries = new ArrayList<>(legalUrls.entrySet());
		for (int i = 0; i < legalEntries.size(); i++) {
			final Map.Entry<String, String> entry = legalEntries.get(i);
			final String comma = i == legalEntries.size() - 1 ? "" : ",";
			lines.add("    \"" + escapeJson(entry.getKey()) + "\": \"" + escapeJson(entry.getValue()) + "\"" + comma);
		}
		lines.add("  },");
		lines.add("  \"errors\": {");
		final List<Map.Entry<String, String>> errorEntries = new ArrayList<>(stepErrors.entrySet());
		for (int i = 0; i < errorEntries.size(); i++) {
			final Map.Entry<String, String> entry = errorEntries.get(i);
			final String comma = i == errorEntries.size() - 1 ? "" : ",";
			lines.add("    \"" + escapeJson(entry.getKey()) + "\": \"" + escapeJson(entry.getValue()) + "\"" + comma);
		}
		lines.add("  }");
		lines.add("}");

		Files.write(
				reportDir.resolve("saleads-mi-negocio-workflow-report.json"),
				String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
	}

	private String toAccented(final String text) {
		if ("Terminos y Condiciones".equals(text)) {
			return "Términos y Condiciones";
		}
		if ("Politica de Privacidad".equals(text)) {
			return "Política de Privacidad";
		}
		return text;
	}

	private String escapeJson(final String value) {
		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"");
	}

	private String escapeXpath(final String value) {
		if (!value.contains("'")) {
			return value;
		}
		return value.replace("'", "\\'");
	}

	private String slug(final String input) {
		return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
