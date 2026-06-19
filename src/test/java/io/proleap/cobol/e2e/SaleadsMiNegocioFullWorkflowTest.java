package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
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
import java.util.Set;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.openqa.selenium.By;
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

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(25);
	private static final Duration SHORT_WAIT = Duration.ofSeconds(8);
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final String loginUrl = configuredValue("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set -Dsaleads.login.url or SALEADS_LOGIN_URL to run this E2E flow.", loginUrl != null);

		final String googleAccount = configuredValue("saleads.google.account.email", "SALEADS_GOOGLE_ACCOUNT_EMAIL",
				DEFAULT_GOOGLE_ACCOUNT);
		final Path evidenceDir = evidenceDirectory();

		final Map<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", Boolean.FALSE);
		report.put("Mi Negocio menu", Boolean.FALSE);
		report.put("Agregar Negocio modal", Boolean.FALSE);
		report.put("Administrar Negocios view", Boolean.FALSE);
		report.put("Informacion General", Boolean.FALSE);
		report.put("Detalles de la Cuenta", Boolean.FALSE);
		report.put("Tus Negocios", Boolean.FALSE);
		report.put("Terminos y Condiciones", Boolean.FALSE);
		report.put("Politica de Privacidad", Boolean.FALSE);

		final List<String> failures = new ArrayList<>();

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--start-maximized");
		options.addArguments("--disable-notifications");
		if (Boolean.parseBoolean(configuredValue("saleads.headless", "SALEADS_HEADLESS", "false"))) {
			options.addArguments("--headless=new");
			options.addArguments("--window-size=1920,1080");
		}

		WebDriver driver = new ChromeDriver(options);
		try {
			driver.get(loginUrl);
			waitForUiLoad(driver);

			boolean loginPassed = stepLoginWithGoogle(driver, googleAccount, evidenceDir, failures);
			report.put("Login", loginPassed);

			boolean menuPassed = stepOpenMiNegocioMenu(driver, evidenceDir, failures);
			report.put("Mi Negocio menu", menuPassed);

			boolean modalPassed = stepValidateAgregarNegocioModal(driver, evidenceDir, failures);
			report.put("Agregar Negocio modal", modalPassed);

			boolean administrarPassed = stepOpenAdministrarNegocios(driver, evidenceDir, failures);
			report.put("Administrar Negocios view", administrarPassed);

			boolean infoPassed = stepValidateInformacionGeneral(driver, failures);
			report.put("Informacion General", infoPassed);

			boolean cuentaPassed = stepValidateDetallesCuenta(driver, failures);
			report.put("Detalles de la Cuenta", cuentaPassed);

			boolean negociosPassed = stepValidateTusNegocios(driver, failures);
			report.put("Tus Negocios", negociosPassed);

			boolean tycPassed = stepValidateLegalLink(driver, "Terminos y Condiciones", "Términos y Condiciones",
					"terms-and-conditions", evidenceDir, failures);
			report.put("Terminos y Condiciones", tycPassed);

			boolean privacyPassed = stepValidateLegalLink(driver, "Politica de Privacidad", "Política de Privacidad",
					"privacy-policy", evidenceDir, failures);
			report.put("Politica de Privacidad", privacyPassed);
		} finally {
			printFinalReport(report);
			driver.quit();
		}

		List<String> failedSections = new ArrayList<>();
		for (Map.Entry<String, Boolean> section : report.entrySet()) {
			if (!section.getValue()) {
				failedSections.add(section.getKey());
			}
		}

		if (!failedSections.isEmpty()) {
			failures.add("Failed report sections: " + String.join(", ", failedSections));
			Assert.fail(String.join(System.lineSeparator(), failures));
		}
	}

	private boolean stepLoginWithGoogle(final WebDriver driver, final String account, final Path evidenceDir,
			final List<String> failures) throws IOException {
		try {
			clickByVisibleText(driver, "Sign in with Google", "Iniciar sesión con Google", "Continuar con Google");
			waitForUiLoad(driver);

			clickIfPresentByVisibleText(driver, SHORT_WAIT, account);
			waitForUiLoad(driver);

			boolean mainUiVisible = isVisible(driver, By.xpath("//aside | //nav"), DEFAULT_WAIT);
			boolean sidebarVisible = isVisibleText(driver, DEFAULT_WAIT, "Negocio");
			captureScreenshot(driver, evidenceDir, "01-dashboard-loaded");

			if (!mainUiVisible || !sidebarVisible) {
				failures.add("Login validation failed: main interface or left sidebar was not visible.");
				return false;
			}

			return true;
		} catch (Exception ex) {
			failures.add("Login step failed: " + ex.getMessage());
			return false;
		}
	}

	private boolean stepOpenMiNegocioMenu(final WebDriver driver, final Path evidenceDir, final List<String> failures)
			throws IOException {
		try {
			clickByVisibleText(driver, "Negocio");
			waitForUiLoad(driver);
			clickByVisibleText(driver, "Mi Negocio");
			waitForUiLoad(driver);

			boolean agregarVisible = isVisibleText(driver, DEFAULT_WAIT, "Agregar Negocio");
			boolean administrarVisible = isVisibleText(driver, DEFAULT_WAIT, "Administrar Negocios");
			captureScreenshot(driver, evidenceDir, "02-mi-negocio-expanded");

			if (!agregarVisible || !administrarVisible) {
				failures.add("Mi Negocio menu validation failed: expected submenu options are not visible.");
				return false;
			}

			return true;
		} catch (Exception ex) {
			failures.add("Mi Negocio menu step failed: " + ex.getMessage());
			return false;
		}
	}

	private boolean stepValidateAgregarNegocioModal(final WebDriver driver, final Path evidenceDir,
			final List<String> failures) throws IOException {
		try {
			clickByVisibleText(driver, "Agregar Negocio");
			waitForUiLoad(driver);

			boolean titleVisible = isVisibleText(driver, DEFAULT_WAIT, "Crear Nuevo Negocio");
			WebElement negocioInput = firstVisible(driver, DEFAULT_WAIT,
					By.xpath("//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio']"
							+ " | //label[normalize-space()='Nombre del Negocio']/following::input[1]"));
			boolean quotaVisible = isVisibleText(driver, DEFAULT_WAIT, "Tienes 2 de 3 negocios");
			boolean cancelarVisible = isVisibleText(driver, DEFAULT_WAIT, "Cancelar");
			boolean crearNegocioVisible = isVisibleText(driver, DEFAULT_WAIT, "Crear Negocio");

			captureScreenshot(driver, evidenceDir, "03-agregar-negocio-modal");

			if (negocioInput != null) {
				negocioInput.click();
				negocioInput.clear();
				negocioInput.sendKeys(TEST_BUSINESS_NAME);
			}

			clickIfPresentByVisibleText(driver, SHORT_WAIT, "Cancelar");
			waitForUiLoad(driver);

			boolean passed = titleVisible && negocioInput != null && quotaVisible && cancelarVisible && crearNegocioVisible;
			if (!passed) {
				failures.add("Agregar Negocio modal validation failed.");
			}

			return passed;
		} catch (Exception ex) {
			failures.add("Agregar Negocio modal step failed: " + ex.getMessage());
			return false;
		}
	}

	private boolean stepOpenAdministrarNegocios(final WebDriver driver, final Path evidenceDir, final List<String> failures)
			throws IOException {
		try {
			expandMiNegocioIfCollapsed(driver);
			clickByVisibleText(driver, "Administrar Negocios");
			waitForUiLoad(driver);

			boolean infoGeneral = isVisibleText(driver, DEFAULT_WAIT, "Información General");
			boolean detallesCuenta = isVisibleText(driver, DEFAULT_WAIT, "Detalles de la Cuenta");
			boolean tusNegocios = isVisibleText(driver, DEFAULT_WAIT, "Tus Negocios");
			boolean seccionLegal = isVisibleText(driver, DEFAULT_WAIT, "Sección Legal");

			captureScreenshot(driver, evidenceDir, "04-administrar-negocios");

			boolean passed = infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
			if (!passed) {
				failures.add("Administrar Negocios page validation failed.");
			}

			return passed;
		} catch (Exception ex) {
			failures.add("Administrar Negocios step failed: " + ex.getMessage());
			return false;
		}
	}

	private boolean stepValidateInformacionGeneral(final WebDriver driver, final List<String> failures) {
		try {
			boolean userNameVisible = hasVisibleElement(driver, By.xpath(
					"//section[.//*[normalize-space()='Información General']]//*[contains(@class,'name') or self::h1 or self::h2]"));
			boolean userEmailVisible = hasVisibleElement(driver, By.xpath(
					"//section[.//*[normalize-space()='Información General']]//*[contains(text(),'@')]"));
			boolean businessPlanVisible = isVisibleText(driver, DEFAULT_WAIT, "BUSINESS PLAN");
			boolean cambiarPlanVisible = isVisibleText(driver, DEFAULT_WAIT, "Cambiar Plan");

			boolean passed = userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
			if (!passed) {
				failures.add("Informacion General validation failed.");
			}
			return passed;
		} catch (Exception ex) {
			failures.add("Informacion General step failed: " + ex.getMessage());
			return false;
		}
	}

	private boolean stepValidateDetallesCuenta(final WebDriver driver, final List<String> failures) {
		try {
			boolean cuentaCreada = isVisibleText(driver, DEFAULT_WAIT, "Cuenta creada");
			boolean estadoActivo = isVisibleText(driver, DEFAULT_WAIT, "Estado activo");
			boolean idiomaSeleccionado = isVisibleText(driver, DEFAULT_WAIT, "Idioma seleccionado");

			boolean passed = cuentaCreada && estadoActivo && idiomaSeleccionado;
			if (!passed) {
				failures.add("Detalles de la Cuenta validation failed.");
			}
			return passed;
		} catch (Exception ex) {
			failures.add("Detalles de la Cuenta step failed: " + ex.getMessage());
			return false;
		}
	}

	private boolean stepValidateTusNegocios(final WebDriver driver, final List<String> failures) {
		try {
			boolean businessListVisible = hasVisibleElement(driver,
					By.xpath("//section[.//*[normalize-space()='Tus Negocios']]//ul | "
							+ "//section[.//*[normalize-space()='Tus Negocios']]//*[contains(@class,'list')]"));
			boolean agregarNegocioButtonVisible = isVisibleText(driver, DEFAULT_WAIT, "Agregar Negocio");
			boolean quotaVisible = isVisibleText(driver, DEFAULT_WAIT, "Tienes 2 de 3 negocios");

			boolean passed = businessListVisible && agregarNegocioButtonVisible && quotaVisible;
			if (!passed) {
				failures.add("Tus Negocios validation failed.");
			}
			return passed;
		} catch (Exception ex) {
			failures.add("Tus Negocios step failed: " + ex.getMessage());
			return false;
		}
	}

	private boolean stepValidateLegalLink(final WebDriver driver, final String reportName, final String linkText,
			final String screenshotName, final Path evidenceDir, final List<String> failures) throws IOException {
		String appTab = driver.getWindowHandle();
		Set<String> beforeHandles = driver.getWindowHandles();

		try {
			clickByVisibleText(driver, linkText);
			waitForUiLoad(driver);

			WebDriverWait wait = new WebDriverWait(driver, DEFAULT_WAIT);
			wait.until((ExpectedCondition<Boolean>) d -> d != null && d.getWindowHandles().size() >= beforeHandles.size());

			Set<String> afterHandles = driver.getWindowHandles();
			String activeHandle = appTab;
			if (afterHandles.size() > beforeHandles.size()) {
				for (String handle : afterHandles) {
					if (!beforeHandles.contains(handle)) {
						activeHandle = handle;
						break;
					}
				}
				driver.switchTo().window(activeHandle);
				waitForUiLoad(driver);
			}

			boolean headingVisible = isVisibleText(driver, DEFAULT_WAIT, linkText);
			boolean legalContentVisible = hasVisibleElement(driver, By.xpath("//main//*[string-length(normalize-space()) > 40] | "
					+ "//article//*[string-length(normalize-space()) > 40]"));

			captureScreenshot(driver, evidenceDir, screenshotName);
			System.out.println(reportName + " final URL: " + driver.getCurrentUrl());

			if (!headingVisible || !legalContentVisible) {
				failures.add(reportName + " validation failed.");
			}

			if (!activeHandle.equals(appTab)) {
				driver.close();
				driver.switchTo().window(appTab);
			} else {
				driver.navigate().back();
			}
			waitForUiLoad(driver);

			return headingVisible && legalContentVisible;
		} catch (Exception ex) {
			failures.add(reportName + " step failed: " + ex.getMessage());
			try {
				driver.switchTo().window(appTab);
				waitForUiLoad(driver);
			} catch (Exception ignored) {
				// Best effort to restore the application tab.
			}
			return false;
		}
	}

	private void expandMiNegocioIfCollapsed(final WebDriver driver) {
		if (!isVisibleText(driver, SHORT_WAIT, "Administrar Negocios")) {
			clickIfPresentByVisibleText(driver, SHORT_WAIT, "Mi Negocio");
			waitForUiLoad(driver);
		}
	}

	private void clickByVisibleText(final WebDriver driver, final String... visibleTexts) {
		Exception lastError = null;

		for (String text : visibleTexts) {
			try {
				WebElement candidate = firstClickableByText(driver, DEFAULT_WAIT, text);
				candidate.click();
				waitForUiLoad(driver);
				return;
			} catch (Exception ex) {
				lastError = ex;
			}
		}

		throw new TimeoutException("Could not click any visible text in candidate set.", lastError);
	}

	private void clickIfPresentByVisibleText(final WebDriver driver, final Duration timeout, final String text) {
		try {
			WebElement candidate = firstClickableByText(driver, timeout, text);
			candidate.click();
			waitForUiLoad(driver);
		} catch (Exception ignored) {
			// Optional click.
		}
	}

	private WebElement firstClickableByText(final WebDriver driver, final Duration timeout, final String text) {
		By xpath = By.xpath("//*[normalize-space()='" + text
				+ "']/self::* | //*[normalize-space()='" + text + "']/ancestor::*[self::button or self::a][1]");
		WebDriverWait wait = new WebDriverWait(driver, timeout);
		return wait.until(ExpectedConditions.elementToBeClickable(xpath));
	}

	private WebElement firstVisible(final WebDriver driver, final Duration timeout, final By by) {
		try {
			WebDriverWait wait = new WebDriverWait(driver, timeout);
			return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
		} catch (TimeoutException ex) {
			return null;
		}
	}

	private boolean hasVisibleElement(final WebDriver driver, final By by) {
		try {
			WebElement element = firstVisible(driver, SHORT_WAIT, by);
			return element != null;
		} catch (StaleElementReferenceException ex) {
			return false;
		}
	}

	private boolean isVisible(final WebDriver driver, final By by, final Duration timeout) {
		return firstVisible(driver, timeout, by) != null;
	}

	private boolean isVisibleText(final WebDriver driver, final Duration timeout, final String text) {
		By by = By.xpath("//*[normalize-space()='" + text + "']");
		return isVisible(driver, by, timeout);
	}

	private void waitForUiLoad(final WebDriver driver) {
		WebDriverWait wait = new WebDriverWait(driver, DEFAULT_WAIT);
		wait.until(d -> ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete"));
	}

	private Path evidenceDirectory() throws IOException {
		String overrideDir = configuredValue("saleads.evidence.dir", "SALEADS_EVIDENCE_DIR");
		Path base = overrideDir != null ? Path.of(overrideDir) : Path.of("target", "saleads-e2e-evidence");
		Path dir = base.resolve(LocalDateTime.now().format(TS_FORMAT));
		Files.createDirectories(dir);
		return dir;
	}

	private void captureScreenshot(final WebDriver driver, final Path evidenceDir, final String checkpoint)
			throws IOException {
		File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Path destination = evidenceDir.resolve(checkpoint + ".png");
		Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Saved screenshot: " + destination.toAbsolutePath());
	}

	private void printFinalReport(final Map<String, Boolean> report) {
		System.out.println("==== SaleADS Mi Negocio Validation Report ====");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		System.out.println("==============================================");
	}

	private String configuredValue(final String propertyName, final String envName) {
		String property = System.getProperty(propertyName);
		if (property != null && !property.isBlank()) {
			return property.trim();
		}

		String env = System.getenv(envName);
		if (env != null && !env.isBlank()) {
			return env.trim();
		}

		return null;
	}

	private String configuredValue(final String propertyName, final String envName, final String defaultValue) {
		String configured = configuredValue(propertyName, envName);
		return configured != null ? configured : defaultValue;
	}
}
