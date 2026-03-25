package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Informacion General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "Terminos y Condiciones";
	private static final String PRIVACIDAD = "Politica de Privacidad";
	private static final List<String> REPORT_FIELDS = List.of(LOGIN, MI_NEGOCIO_MENU, AGREGAR_NEGOCIO_MODAL,
			ADMINISTRAR_NEGOCIOS_VIEW, INFORMACION_GENERAL, DETALLES_CUENTA, TUS_NEGOCIOS, TERMINOS, PRIVACIDAD);

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor js;
	private Path screenshotDir;
	private final Map<String, StepResult> report = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final boolean enabled = boolFromEnvOrSystem("SALEADS_E2E_ENABLED", "saleads.e2e.enabled", false);
		Assume.assumeTrue(
				"Skipping SaleADS E2E. Enable with env SALEADS_E2E_ENABLED=true or -Dsaleads.e2e.enabled=true.",
				enabled);

		final String startUrl = firstNonBlank(System.getenv("SALEADS_START_URL"), System.getProperty("saleads.start.url"));
		Assume.assumeTrue(
				"Set SALEADS_START_URL (or -Dsaleads.start.url) to the login page of your current SaleADS environment.",
				startUrl != null);

		final ChromeOptions chromeOptions = new ChromeOptions();
		final boolean headless = boolFromEnvOrSystem("SALEADS_HEADLESS", "saleads.headless", true);
		if (headless) {
			chromeOptions.addArguments("--headless=new");
		}
		chromeOptions.addArguments("--window-size=1920,1080");
		chromeOptions.addArguments("--disable-dev-shm-usage");
		chromeOptions.addArguments("--no-sandbox");

		driver = new ChromeDriver(chromeOptions);
		wait = new WebDriverWait(driver, Duration.ofSeconds(25));
		js = (JavascriptExecutor) driver;

		screenshotDir = Paths.get("target", "surefire-reports", "screenshots", "saleads-mi-negocio");
		Files.createDirectories(screenshotDir);

		driver.get(startUrl);
		waitForPageLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		initializeReport();

		setResult(LOGIN, stepLoginWithGoogle());
		setResult(MI_NEGOCIO_MENU, stepOpenMiNegocioMenu());
		setResult(AGREGAR_NEGOCIO_MODAL, stepValidateAgregarNegocioModal());
		setResult(ADMINISTRAR_NEGOCIOS_VIEW, stepOpenAdministrarNegocios());
		setResult(INFORMACION_GENERAL, stepValidateInformacionGeneral());
		setResult(DETALLES_CUENTA, stepValidateDetallesCuenta());
		setResult(TUS_NEGOCIOS, stepValidateTusNegocios());
		setResult(TERMINOS,
				stepValidateLegalLink(new String[] { "Terminos y Condiciones", "T\u00E9rminos y Condiciones" },
						new String[] { "Terminos y Condiciones", "T\u00E9rminos y Condiciones" }, "08-terminos"));
		setResult(PRIVACIDAD,
				stepValidateLegalLink(new String[] { "Politica de Privacidad", "Pol\u00EDtica de Privacidad" },
						new String[] { "Politica de Privacidad", "Pol\u00EDtica de Privacidad" }, "09-privacidad"));

		printFinalReport();
		assertAllStepsPassed();
	}

	private StepResult stepLoginWithGoogle() {
		try {
			if (!isSidebarVisible()) {
				clickFirstVisibleText(new String[] { "Sign in with Google", "Iniciar sesi\u00F3n con Google",
						"Continuar con Google", "Login with Google" });
				waitForPageLoad();
				selectGoogleAccountIfPresent(ACCOUNT_EMAIL);
			}

			final boolean mainUiVisible = waitUntil(this::isMainInterfaceVisible, 120);
			final boolean sidebarVisible = isSidebarVisible();

			if (mainUiVisible && sidebarVisible) {
				takeScreenshot("01-dashboard-loaded");
				return StepResult.pass("Main interface and left sidebar are visible.");
			}

			takeScreenshot("01-dashboard-not-loaded");
			return StepResult.fail("Dashboard or sidebar not visible after Google login.");
		} catch (final Exception e) {
			takeScreenshot("01-login-error");
			return StepResult.fail("Login flow error: " + e.getMessage());
		}
	}

	private StepResult stepOpenMiNegocioMenu() {
		try {
			expandMiNegocioMenu();
			final boolean agregarVisible = isAnyTextVisible(new String[] { "Agregar Negocio" });
			final boolean administrarVisible = isAnyTextVisible(new String[] { "Administrar Negocios" });

			takeScreenshot("02-mi-negocio-menu");

			if (agregarVisible && administrarVisible) {
				return StepResult.pass("Submenu expanded and expected options are visible.");
			}

			return StepResult.fail("Missing submenu options. Agregar Negocio=" + agregarVisible + ", Administrar Negocios="
					+ administrarVisible);
		} catch (final Exception e) {
			takeScreenshot("02-mi-negocio-error");
			return StepResult.fail("Could not open Mi Negocio menu: " + e.getMessage());
		}
	}

	private StepResult stepValidateAgregarNegocioModal() {
		try {
			clickFirstVisibleText(new String[] { "Agregar Negocio" });
			final boolean modalTitle = waitUntilAnyTextVisible(
					new String[] { "Crear Nuevo Negocio", "Crear nuevo negocio" }, 20);
			final boolean nombreInput = isElementDisplayed(By.xpath(
					"//input[contains(@placeholder, 'Nombre del Negocio')] | //label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
			final boolean cuota = isAnyTextVisible(new String[] { "Tienes 2 de 3 negocios" });
			final boolean cancelar = isAnyTextVisible(new String[] { "Cancelar" });
			final boolean crearNegocio = isAnyTextVisible(new String[] { "Crear Negocio" });

			takeScreenshot("03-agregar-negocio-modal");

			// Optional interaction requested in the workflow.
			if (nombreInput) {
				final List<WebElement> fields = driver.findElements(By.xpath(
						"//input[contains(@placeholder, 'Nombre del Negocio')] | //label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
				if (!fields.isEmpty()) {
					fields.get(0).click();
					fields.get(0).clear();
					fields.get(0).sendKeys("Negocio Prueba Automatizacion");
				}
			}

			if (cancelar) {
				clickFirstVisibleText(new String[] { "Cancelar" });
				waitUntilModalCloses();
			}

			if (modalTitle && nombreInput && cuota && cancelar && crearNegocio) {
				return StepResult.pass("Agregar Negocio modal validated successfully.");
			}

			return StepResult.fail("Modal validation failed. title=" + modalTitle + ", nombreInput=" + nombreInput
					+ ", cuota=" + cuota + ", cancelar=" + cancelar + ", crearNegocio=" + crearNegocio);
		} catch (final Exception e) {
			takeScreenshot("03-agregar-negocio-error");
			return StepResult.fail("Could not validate Agregar Negocio modal: " + e.getMessage());
		}
	}

	private StepResult stepOpenAdministrarNegocios() {
		try {
			if (!isAnyTextVisible(new String[] { "Administrar Negocios" })) {
				expandMiNegocioMenu();
			}

			clickFirstVisibleText(new String[] { "Administrar Negocios" });
			waitForPageLoad();

			final boolean infoGeneral = isAnyTextVisible(new String[] { "Informacion General", "Informaci\u00F3n General" });
			final boolean detallesCuenta = isAnyTextVisible(new String[] { "Detalles de la Cuenta" });
			final boolean tusNegocios = isAnyTextVisible(new String[] { "Tus Negocios" });
			final boolean seccionLegal = isAnyTextVisible(new String[] { "Seccion Legal", "Secci\u00F3n Legal" });

			takeScreenshot("04-administrar-negocios");

			if (infoGeneral && detallesCuenta && tusNegocios && seccionLegal) {
				return StepResult.pass("Administrar Negocios view with all expected sections is visible.");
			}

			return StepResult.fail("Missing sections. infoGeneral=" + infoGeneral + ", detallesCuenta=" + detallesCuenta
					+ ", tusNegocios=" + tusNegocios + ", seccionLegal=" + seccionLegal);
		} catch (final Exception e) {
			takeScreenshot("04-administrar-negocios-error");
			return StepResult.fail("Could not open Administrar Negocios: " + e.getMessage());
		}
	}

	private StepResult stepValidateInformacionGeneral() {
		try {
			final String infoSectionText = sectionText(new String[] { "Informacion General", "Informaci\u00F3n General" });
			final boolean userEmailVisible = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
					.matcher(infoSectionText).find();
			final boolean userNameVisible = inferNameVisible(infoSectionText);
			final boolean businessPlanVisible = isAnyTextVisible(new String[] { "BUSINESS PLAN" });
			final boolean cambiarPlanVisible = isAnyTextVisible(new String[] { "Cambiar Plan" });

			if (userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible) {
				return StepResult.pass("Informacion General is valid.");
			}

			return StepResult.fail("Informacion General validation failed. userName=" + userNameVisible + ", userEmail="
					+ userEmailVisible + ", businessPlan=" + businessPlanVisible + ", cambiarPlan=" + cambiarPlanVisible);
		} catch (final Exception e) {
			return StepResult.fail("Could not validate Informacion General: " + e.getMessage());
		}
	}

	private StepResult stepValidateDetallesCuenta() {
		try {
			final boolean cuentaCreada = isAnyTextVisible(new String[] { "Cuenta creada" });
			final boolean estadoActivo = isAnyTextVisible(new String[] { "Estado activo" });
			final boolean idiomaSeleccionado = isAnyTextVisible(new String[] { "Idioma seleccionado" });

			if (cuentaCreada && estadoActivo && idiomaSeleccionado) {
				return StepResult.pass("Detalles de la Cuenta is valid.");
			}

			return StepResult.fail("Detalles de la Cuenta validation failed. cuentaCreada=" + cuentaCreada
					+ ", estadoActivo=" + estadoActivo + ", idiomaSeleccionado=" + idiomaSeleccionado);
		} catch (final Exception e) {
			return StepResult.fail("Could not validate Detalles de la Cuenta: " + e.getMessage());
		}
	}

	private StepResult stepValidateTusNegocios() {
		try {
			final String tusNegociosText = sectionText(new String[] { "Tus Negocios" });
			final boolean sectionVisible = tusNegociosText != null && !tusNegociosText.isBlank();
			final boolean buttonVisible = isAnyTextVisible(new String[] { "Agregar Negocio" });
			final boolean quotaVisible = isAnyTextVisible(new String[] { "Tienes 2 de 3 negocios" });
			final boolean businessListVisible = inferBusinessListVisible(tusNegociosText);

			if (sectionVisible && buttonVisible && quotaVisible && businessListVisible) {
				return StepResult.pass("Tus Negocios is valid.");
			}

			return StepResult.fail("Tus Negocios validation failed. sectionVisible=" + sectionVisible + ", buttonVisible="
					+ buttonVisible + ", quotaVisible=" + quotaVisible + ", businessListVisible=" + businessListVisible);
		} catch (final Exception e) {
			return StepResult.fail("Could not validate Tus Negocios: " + e.getMessage());
		}
	}

	private StepResult stepValidateLegalLink(final String[] linkTexts, final String[] headingTexts,
			final String screenshotName) {
		try {
			final String originalHandle = driver.getWindowHandle();
			final String originalUrl = driver.getCurrentUrl();
			final Set<String> handlesBefore = driver.getWindowHandles();

			clickFirstVisibleText(linkTexts);

			final String newHandle = waitForNewWindowHandle(handlesBefore, 10);
			boolean inNewTab = false;
			if (newHandle != null) {
				driver.switchTo().window(newHandle);
				inNewTab = true;
			}

			waitForPageLoad();

			final boolean headingVisible = waitUntilAnyTextVisible(headingTexts, 25);
			final String pageText = textOfBody();
			final boolean legalContentVisible = pageText != null && pageText.trim().length() > 120;
			final String finalUrl = driver.getCurrentUrl();

			takeScreenshot(screenshotName);

			if (inNewTab) {
				driver.close();
				driver.switchTo().window(originalHandle);
				waitForPageLoad();
			} else {
				if (!safeEquals(originalUrl, finalUrl)) {
					driver.navigate().back();
					waitForPageLoad();
				}
			}

			if (headingVisible && legalContentVisible) {
				return StepResult.pass("Legal page validated. URL=" + finalUrl);
			}

			return StepResult.fail(
					"Legal page validation failed. headingVisible=" + headingVisible + ", legalContentVisible="
							+ legalContentVisible + ", URL=" + finalUrl);
		} catch (final Exception e) {
			takeScreenshot(screenshotName + "-error");
			return StepResult.fail("Could not validate legal page: " + e.getMessage());
		}
	}

	private void expandMiNegocioMenu() {
		if (isAnyTextVisible(new String[] { "Negocio" })) {
			clickFirstVisibleText(new String[] { "Negocio" });
		}
		if (isAnyTextVisible(new String[] { "Mi Negocio" })) {
			clickFirstVisibleText(new String[] { "Mi Negocio" });
		}
		waitUntilAnyTextVisible(new String[] { "Agregar Negocio", "Administrar Negocios" }, 20);
	}

	private boolean isMainInterfaceVisible() {
		return isSidebarVisible() || isAnyTextVisible(new String[] { "Mi Negocio", "Negocio" });
	}

	private boolean isSidebarVisible() {
		final List<WebElement> candidates = driver
				.findElements(By.xpath("//aside | //nav | //div[contains(@class, 'sidebar')]"));
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void selectGoogleAccountIfPresent(final String email) {
		final long end = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
		final String originalHandle = driver.getWindowHandle();

		while (System.currentTimeMillis() < end) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				final List<WebElement> emailOptions = driver.findElements(
						By.xpath("//*[normalize-space(.)=" + xpathLiteral(email) + "] | //*[contains(normalize-space(.), "
								+ xpathLiteral(email) + ")]"));
				for (final WebElement option : emailOptions) {
					if (option.isDisplayed()) {
						option.click();
						waitForPageLoad();
						driver.switchTo().window(originalHandle);
						waitForPageLoad();
						return;
					}
				}
			}

			driver.switchTo().window(originalHandle);
			if (isSidebarVisible()) {
				return;
			}

			sleep(1000);
		}
	}

	private void clickFirstVisibleText(final String[] texts) {
		Exception lastError = null;
		for (final String text : texts) {
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By
						.xpath("//button[normalize-space(.)=" + xpathLiteral(text) + "] | //a[normalize-space(.)="
								+ xpathLiteral(text) + "] | //*[@role='button' and normalize-space(.)="
								+ xpathLiteral(text) + "] | //*[normalize-space(.)=" + xpathLiteral(text) + "]")));
				element.click();
				waitForPageLoad();
				sleep(500);
				return;
			} catch (final Exception e) {
				lastError = e;
			}
		}

		throw new IllegalStateException("Could not click any of texts " + String.join(", ", texts), lastError);
	}

	private boolean isAnyTextVisible(final String[] texts) {
		for (final String text : texts) {
			final List<WebElement> elements = driver
					.findElements(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]"));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isElementDisplayed(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean waitUntilAnyTextVisible(final String[] texts, final int timeoutSeconds) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		try {
			return shortWait.until(webDriver -> isAnyTextVisible(texts));
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private void waitUntilModalCloses() {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));
			shortWait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(
					"//*[contains(normalize-space(.), 'Crear Nuevo Negocio') or contains(normalize-space(.), 'Crear nuevo negocio')]")));
		} catch (final Exception ignored) {
			// Ignore close race conditions in dynamic modals.
		}
	}

	private String sectionText(final String[] headings) {
		for (final String heading : headings) {
			final List<WebElement> headingElements = driver.findElements(By.xpath(
					"//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::div or self::span][contains(normalize-space(.), "
							+ xpathLiteral(heading) + ")]"));
			for (final WebElement headingElement : headingElements) {
				if (!headingElement.isDisplayed()) {
					continue;
				}

				try {
					final WebElement container = headingElement
							.findElement(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
					return container.getText();
				} catch (final Exception ignored) {
					return headingElement.getText();
				}
			}
		}
		return "";
	}

	private boolean inferNameVisible(final String sectionText) {
		if (sectionText == null || sectionText.isBlank()) {
			return false;
		}

		final String[] lines = sectionText.split("\\R");
		for (final String line : lines) {
			final String value = line.trim();
			if (value.isEmpty()) {
				continue;
			}
			if (value.contains("@") || value.equalsIgnoreCase("BUSINESS PLAN") || value.equalsIgnoreCase("Cambiar Plan")
					|| value.toLowerCase().contains("informacion general")
					|| value.toLowerCase().contains("informaci\u00F3n general")) {
				continue;
			}

			final Matcher m = Pattern.compile("[A-Za-z\\u00C0-\\u017F]{2,}(\\s+[A-Za-z\\u00C0-\\u017F]{2,})+").matcher(value);
			if (m.find()) {
				return true;
			}
		}

		return false;
	}

	private boolean inferBusinessListVisible(final String sectionText) {
		if (sectionText == null || sectionText.isBlank()) {
			return false;
		}

		final String[] lines = sectionText.split("\\R");
		final List<String> informativeLines = new ArrayList<>();
		for (final String line : lines) {
			final String value = line.trim();
			if (value.isEmpty()) {
				continue;
			}
			if (value.equalsIgnoreCase("Tus Negocios") || value.equalsIgnoreCase("Agregar Negocio")
					|| value.equalsIgnoreCase("Tienes 2 de 3 negocios")) {
				continue;
			}
			informativeLines.add(value);
		}
		return !informativeLines.isEmpty();
	}

	private String waitForNewWindowHandle(final Set<String> previousHandles, final int timeoutSeconds) {
		final long end = System.currentTimeMillis() + Duration.ofSeconds(timeoutSeconds).toMillis();
		while (System.currentTimeMillis() < end) {
			final Set<String> current = driver.getWindowHandles();
			if (current.size() > previousHandles.size()) {
				for (final String handle : current) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}
			}
			sleep(250);
		}
		return null;
	}

	private void waitForPageLoad() {
		wait.until(webDriver -> "complete".equals(js.executeScript("return document.readyState")));
	}

	private void takeScreenshot(final String name) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		try {
			final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path target = screenshotDir.resolve(timestamp + "-" + name + ".png");
			Files.copy(source.toPath(), target);
		} catch (final Exception ignored) {
			// Non-blocking evidence capture.
		}
	}

	private String textOfBody() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception e) {
			return "";
		}
	}

	private boolean waitUntil(final BooleanSupplier supplier, final int timeoutSeconds) {
		final long end = System.currentTimeMillis() + Duration.ofSeconds(timeoutSeconds).toMillis();
		while (System.currentTimeMillis() < end) {
			try {
				if (supplier.getAsBoolean()) {
					return true;
				}
			} catch (final Exception ignored) {
				// Keep polling.
			}
			sleep(500);
		}
		return false;
	}

	private void initializeReport() {
		for (final String field : REPORT_FIELDS) {
			report.put(field, StepResult.fail("Not executed"));
		}
	}

	private void setResult(final String field, final StepResult result) {
		report.put(field, result);
	}

	private void printFinalReport() {
		System.out.println();
		System.out.println("=== SaleADS Mi Negocio Full Validation Report ===");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final String status = entry.getValue().passed ? "PASS" : "FAIL";
			System.out.println("- " + entry.getKey() + ": " + status + " -> " + entry.getValue().details);
		}
		System.out.println("===============================================");
		System.out.println();
	}

	private void assertAllStepsPassed() {
		final StringBuilder errors = new StringBuilder();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				errors.append(entry.getKey()).append(": ").append(entry.getValue().details).append('\n');
			}
		}

		if (errors.length() > 0) {
			Assert.fail("One or more workflow validations failed:\n" + errors);
		}
	}

	private static boolean boolFromEnvOrSystem(final String envName, final String propertyName, final boolean fallback) {
		final String fromEnv = System.getenv(envName);
		if (fromEnv != null) {
			return Boolean.parseBoolean(fromEnv);
		}

		final String fromProp = System.getProperty(propertyName);
		if (fromProp != null) {
			return Boolean.parseBoolean(fromProp);
		}

		return fallback;
	}

	private static String firstNonBlank(final String a, final String b) {
		if (a != null && !a.isBlank()) {
			return a;
		}
		if (b != null && !b.isBlank()) {
			return b;
		}
		return null;
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			if (chars[i] == '\'') {
				sb.append("\"'\"");
			} else {
				sb.append('\'').append(chars[i]).append('\'');
			}
		}
		sb.append(')');
		return sb.toString();
	}

	private static boolean safeEquals(final String a, final String b) {
		if (a == null) {
			return b == null;
		}
		return a.equals(b);
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private interface BooleanSupplier {
		boolean getAsBoolean();
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
