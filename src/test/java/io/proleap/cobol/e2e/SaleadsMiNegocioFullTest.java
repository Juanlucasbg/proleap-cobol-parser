package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.Assume;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
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
 * End-to-end validation for SaleADS Mi Negocio workflow.
 *
 * Execution notes:
 * - Enable with -Dsaleads.e2e.enabled=true
 * - Provide login URL with -Dsaleads.login.url=<current-environment-login-url>
 * - Optional account email override: -Dsaleads.google.account=<email>
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(25);
	private static final Duration FAST_WAIT = Duration.ofSeconds(8);
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> notes = new ArrayList<>();
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue("Enable this E2E with -Dsaleads.e2e.enabled=true",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

		final String loginUrl = firstNonBlank(
				System.getProperty("saleads.login.url"),
				System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"Provide a login URL with -Dsaleads.login.url or SALEADS_LOGIN_URL.",
				loginUrl != null && !loginUrl.isBlank());

		setupDriverAndEvidence();
		try {
			driver.get(loginUrl);
			waitForUiToLoad();

			report.put("Login", executeStep(this::stepLoginWithGoogle));
			report.put("Mi Negocio menu", executeStep(this::stepOpenMiNegocioMenu));
			report.put("Agregar Negocio modal", executeStep(this::stepValidateAgregarNegocioModal));
			report.put("Administrar Negocios view", executeStep(this::stepOpenAdministrarNegocios));
			report.put("Información General", executeStep(this::stepValidateInformacionGeneral));
			report.put("Detalles de la Cuenta", executeStep(this::stepValidateDetallesCuenta));
			report.put("Tus Negocios", executeStep(this::stepValidateTusNegocios));
			report.put("Términos y Condiciones", executeStep(this::stepValidateTerminosCondiciones));
			report.put("Política de Privacidad", executeStep(this::stepValidatePoliticaPrivacidad));
		} finally {
			printFinalReport();
			if (driver != null) {
				driver.quit();
			}
		}

		assertTrue("One or more workflow validations failed. Review the report output.", allStepsPassed());
	}

	private boolean stepLoginWithGoogle() throws Exception {
		clickFirstVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiToLoad();

		handleGoogleAccountSelector();
		waitForUiToLoad();

		final boolean appLoaded = isVisibleText("Negocio", DEFAULT_WAIT) || isVisible(By.tagName("aside"), DEFAULT_WAIT);
		final boolean sidebarVisible = isVisible(By.tagName("aside"), DEFAULT_WAIT) || isVisibleText("Mi Negocio", DEFAULT_WAIT);
		takeScreenshot("01_dashboard_loaded");

		if (!appLoaded) {
			notes.add("Login validation: main application interface was not detected.");
		}
		if (!sidebarVisible) {
			notes.add("Login validation: left sidebar was not detected.");
		}
		return appLoaded && sidebarVisible;
	}

	private boolean stepOpenMiNegocioMenu() throws Exception {
		clickIfVisibleText("Negocio");
		clickFirstVisibleText("Mi Negocio");
		waitForUiToLoad();

		final boolean agregarVisible = isVisibleText("Agregar Negocio", DEFAULT_WAIT);
		final boolean administrarVisible = isVisibleText("Administrar Negocios", DEFAULT_WAIT);
		takeScreenshot("02_mi_negocio_menu_expanded");

		if (!agregarVisible || !administrarVisible) {
			notes.add("Mi Negocio menu validation: submenu options are not fully visible.");
		}
		return agregarVisible && administrarVisible;
	}

	private boolean stepValidateAgregarNegocioModal() throws Exception {
		clickFirstVisibleText("Agregar Negocio");
		waitForUiToLoad();

		final boolean titleVisible = isVisibleText("Crear Nuevo Negocio", DEFAULT_WAIT);
		final boolean nombreInputVisible = isVisible(By.xpath("//input[contains(@placeholder,'Nombre del Negocio') "
				+ "or @aria-label='Nombre del Negocio' or @name='nombreNegocio']"), FAST_WAIT)
				|| isVisibleText("Nombre del Negocio", FAST_WAIT);
		final boolean quotaVisible = isVisibleText("Tienes 2 de 3 negocios", FAST_WAIT);
		final boolean cancelarVisible = isVisibleText("Cancelar", FAST_WAIT);
		final boolean crearVisible = isVisibleText("Crear Negocio", FAST_WAIT);
		takeScreenshot("03_agregar_negocio_modal");

		if (nombreInputVisible) {
			typeIfVisible(
					By.xpath("//input[contains(@placeholder,'Nombre del Negocio') or @aria-label='Nombre del Negocio' or @name='nombreNegocio']"),
					"Negocio Prueba Automatizacion");
		}
		clickIfVisibleText("Cancelar");
		waitForUiToLoad();

		final boolean ok = titleVisible && nombreInputVisible && quotaVisible && cancelarVisible && crearVisible;
		if (!ok) {
			notes.add("Agregar Negocio modal validation: one or more required fields/texts are missing.");
		}
		return ok;
	}

	private boolean stepOpenAdministrarNegocios() throws Exception {
		expandMiNegocioMenuIfCollapsed();
		clickFirstVisibleText("Administrar Negocios");
		waitForUiToLoad();

		final boolean infoGeneral = isVisibleText("Informacion General", FAST_WAIT) || isVisibleText("Información General", FAST_WAIT);
		final boolean detallesCuenta = isVisibleText("Detalles de la Cuenta", FAST_WAIT);
		final boolean tusNegocios = isVisibleText("Tus Negocios", FAST_WAIT);
		final boolean seccionLegal = isVisibleText("Seccion Legal", FAST_WAIT) || isVisibleText("Sección Legal", FAST_WAIT);
		takeFullPageScreenshot("04_administrar_negocios_page_full");

		final boolean ok = infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
		if (!ok) {
			notes.add("Administrar Negocios validation: required sections were not all found.");
		}
		return ok;
	}

	private boolean stepValidateInformacionGeneral() {
		final boolean userNameVisible = isVisible(By.xpath("//*[contains(@class,'user') or contains(@class,'name')]"), FAST_WAIT)
				|| isVisibleText("Nombre", FAST_WAIT);
		final boolean userEmailVisible = isVisible(By.xpath("//*[contains(text(),'@')]"), FAST_WAIT);
		final boolean planVisible = isVisibleText("BUSINESS PLAN", FAST_WAIT);
		final boolean cambiarPlanVisible = isVisibleText("Cambiar Plan", FAST_WAIT);

		final boolean ok = userNameVisible && userEmailVisible && planVisible && cambiarPlanVisible;
		if (!ok) {
			notes.add("Informacion General validation: expected user or plan data missing.");
		}
		return ok;
	}

	private boolean stepValidateDetallesCuenta() {
		final boolean cuentaCreada = isVisibleText("Cuenta creada", FAST_WAIT);
		final boolean estadoActivo = isVisibleText("Estado activo", FAST_WAIT);
		final boolean idiomaSeleccionado = isVisibleText("Idioma seleccionado", FAST_WAIT);

		final boolean ok = cuentaCreada && estadoActivo && idiomaSeleccionado;
		if (!ok) {
			notes.add("Detalles de la Cuenta validation: one or more fields are missing.");
		}
		return ok;
	}

	private boolean stepValidateTusNegocios() {
		final boolean listVisible = isVisibleText("Tus Negocios", FAST_WAIT)
				&& (isVisible(By.xpath("//table"), FAST_WAIT) || isVisible(By.xpath("//ul"), FAST_WAIT) || isVisible(By.xpath("//div[contains(@class,'list')]"), FAST_WAIT));
		final boolean addBusinessVisible = isVisibleText("Agregar Negocio", FAST_WAIT);
		final boolean quotaVisible = isVisibleText("Tienes 2 de 3 negocios", FAST_WAIT);

		final boolean ok = listVisible && addBusinessVisible && quotaVisible;
		if (!ok) {
			notes.add("Tus Negocios validation: business list or controls not fully visible.");
		}
		return ok;
	}

	private boolean stepValidateTerminosCondiciones() throws Exception {
		final String finalUrl = openAndValidateLegalLink("Términos y Condiciones", "Terminos y Condiciones", "08_terminos_condiciones");
		final boolean validUrl = finalUrl != null && !finalUrl.isBlank();
		if (!validUrl) {
			notes.add("Términos y Condiciones validation: final URL could not be captured.");
		}
		return validUrl;
	}

	private boolean stepValidatePoliticaPrivacidad() throws Exception {
		final String finalUrl = openAndValidateLegalLink("Política de Privacidad", "Politica de Privacidad", "09_politica_privacidad");
		final boolean validUrl = finalUrl != null && !finalUrl.isBlank();
		if (!validUrl) {
			notes.add("Política de Privacidad validation: final URL could not be captured.");
		}
		return validUrl;
	}

	private String openAndValidateLegalLink(final String preferredText, final String fallbackText, final String screenshotName)
			throws Exception {
		final String currentHandle = driver.getWindowHandle();
		final Set<String> beforeHandles = driver.getWindowHandles();
		final String beforeUrl = driver.getCurrentUrl();

		if (!clickIfVisibleText(preferredText)) {
			clickFirstVisibleText(fallbackText);
		}
		waitForUiToLoad();

		boolean switchedToNewTab = false;
		try {
			new WebDriverWait(driver, DEFAULT_WAIT).until(d -> d.getWindowHandles().size() > beforeHandles.size());
			final Set<String> afterHandles = driver.getWindowHandles();
			final Optional<String> newHandle = afterHandles.stream()
					.filter(handle -> !beforeHandles.contains(handle))
					.findFirst();
			if (newHandle.isPresent()) {
				driver.switchTo().window(newHandle.get());
				switchedToNewTab = true;
			}
		} catch (final TimeoutException ignored) {
			new WebDriverWait(driver, DEFAULT_WAIT)
					.until((ExpectedCondition<Boolean>) d -> d != null && !d.getCurrentUrl().equals(beforeUrl));
		}

		waitForUiToLoad();
		final boolean headingVisible = isVisibleText(preferredText, FAST_WAIT) || isVisibleText(fallbackText, FAST_WAIT);
		final boolean legalContentVisible = isVisible(By.xpath("//p | //section | //article"), FAST_WAIT);
		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();
		notes.add(preferredText + " URL: " + finalUrl);

		if (!headingVisible || !legalContentVisible) {
			notes.add(preferredText + " validation: legal heading/content was not fully detected.");
		}

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(currentHandle);
			waitForUiToLoad();
		} else if (!driver.getCurrentUrl().equals(beforeUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return headingVisible && legalContentVisible ? finalUrl : null;
	}

	private boolean executeStep(final Step step) {
		try {
			return step.run();
		} catch (final Exception exception) {
			notes.add("Step error: " + exception.getMessage());
			try {
				takeScreenshot("error_" + LocalDateTime.now().format(formatter));
			} catch (final Exception ignored) {
				// Ignore evidence failures inside exception handling.
			}
			return false;
		}
	}

	private void setupDriverAndEvidence() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "false"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		evidenceDirectory = Path.of("target", "saleads-evidence", LocalDateTime.now().format(formatter));
		Files.createDirectories(evidenceDirectory);
	}

	private void handleGoogleAccountSelector() {
		final String desiredAccount = firstNonBlank(
				System.getProperty("saleads.google.account"),
				System.getenv("SALEADS_GOOGLE_ACCOUNT"),
				DEFAULT_GOOGLE_ACCOUNT);

		final String currentHandle = driver.getWindowHandle();
		try {
			new WebDriverWait(driver, FAST_WAIT).until(d -> d.getWindowHandles().size() > 1);
			final String popupHandle = driver.getWindowHandles().stream()
					.filter(handle -> !handle.equals(currentHandle))
					.findFirst()
					.orElse(currentHandle);
			driver.switchTo().window(popupHandle);
		} catch (final TimeoutException ignored) {
			// Google chooser may open in same tab or may be skipped if already authenticated.
		}

		final List<By> accountLocators = List.of(
				By.xpath("//*[contains(text(),'" + desiredAccount + "')]"),
				By.xpath("//*[@data-identifier='" + desiredAccount + "']"));

		for (final By locator : accountLocators) {
			try {
				final WebElement accountElement = new WebDriverWait(driver, FAST_WAIT)
						.until(ExpectedConditions.elementToBeClickable(locator));
				accountElement.click();
				break;
			} catch (final TimeoutException ignored) {
				// Try next selector.
			}
		}

		try {
			driver.switchTo().window(currentHandle);
		} catch (final Exception ignored) {
			// Keep current context if original handle was replaced due to same-tab navigation.
		}
	}

	private void expandMiNegocioMenuIfCollapsed() {
		if (!isVisibleText("Administrar Negocios", Duration.ofSeconds(3))) {
			clickIfVisibleText("Mi Negocio");
			waitForUiToLoad();
		}
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> {
				final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(readyState) || "interactive".equals(readyState);
			});
		} catch (final Exception ignored) {
			// Some transitions do not update readyState in SPA navigation.
		}
		try {
			Thread.sleep(700L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void clickFirstVisibleText(final String... texts) {
		for (final String text : texts) {
			if (clickIfVisibleText(text)) {
				return;
			}
		}
		throw new IllegalStateException("None of the expected clickable texts were found: " + String.join(", ", texts));
	}

	private boolean clickIfVisibleText(final String text) {
		final List<By> locators = List.of(
				By.xpath("//button[normalize-space()='" + text + "']"),
				By.xpath("//a[normalize-space()='" + text + "']"),
				By.xpath("//*[@role='button' and normalize-space()='" + text + "']"),
				By.xpath("//*[self::span or self::div or self::p][normalize-space()='" + text + "']"));

		for (final By locator : locators) {
			try {
				final WebElement element = new WebDriverWait(driver, FAST_WAIT)
						.until(ExpectedConditions.elementToBeClickable(locator));
				element.click();
				waitForUiToLoad();
				return true;
			} catch (final TimeoutException ignored) {
				// Try next locator.
			}
		}
		return false;
	}

	private boolean isVisibleText(final String text, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[contains(normalize-space(),'" + text + "')]")));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void typeIfVisible(final By locator, final String text) {
		try {
			final WebElement input = new WebDriverWait(driver, FAST_WAIT)
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			input.clear();
			input.sendKeys(text);
		} catch (final TimeoutException ignored) {
			notes.add("Optional action skipped: could not type in 'Nombre del Negocio' input.");
		}
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String timestamp = LocalDateTime.now().format(formatter);
		final Path target = evidenceDirectory.resolve(timestamp + "_" + checkpointName + ".png");
		Files.copy(screenshot.toPath(), target);
		notes.add("Screenshot: " + target);
	}

	private void takeFullPageScreenshot(final String checkpointName) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final Long fullWidth = (Long) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);");
			final Long fullHeight = (Long) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			if (fullWidth != null && fullHeight != null && fullWidth > 0L && fullHeight > 0L) {
				final int targetWidth = (int) Math.min(fullWidth + 100L, 2400L);
				final int targetHeight = (int) Math.min(fullHeight + 100L, 6000L);
				driver.manage().window().setSize(new Dimension(targetWidth, targetHeight));
				waitForUiToLoad();
			}
			takeScreenshot(checkpointName);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}
	}

	private void printFinalReport() {
		System.out.println("==== SaleADS Mi Negocio Workflow Report ====");
		report.forEach((key, value) -> System.out.println(key + ": " + (value ? "PASS" : "FAIL")));
		System.out.println("Evidence directory: " + evidenceDirectory);
		if (!notes.isEmpty()) {
			System.out.println("Notes:");
			notes.forEach(note -> System.out.println("- " + note));
		}
	}

	private boolean allStepsPassed() {
		return report.values().stream().allMatch(Boolean::booleanValue);
	}

	private String firstNonBlank(final String... values) {
		if (values == null) {
			return null;
		}
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface Step {
		boolean run() throws Exception;
	}
}
