package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Environment-agnostic end-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * Required runtime config:
 * - saleads.login.url (or env SALEADS_LOGIN_URL)
 *
 * Optional runtime config:
 * - saleads.google.email (or env SALEADS_GOOGLE_EMAIL), defaults to
 *   juanlucasbarbiergarzon@gmail.com
 * - saleads.user.name (or env SALEADS_USER_NAME)
 * - saleads.headless (or env SALEADS_HEADLESS), defaults to true
 */
public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(35);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final String DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Set<String> NON_NAME_LABELS = Set.of(
			"INFORMACION GENERAL",
			"INFORMACIÓN GENERAL",
			"DETALLES DE LA CUENTA",
			"TUS NEGOCIOS",
			"SECCION LEGAL",
			"SECCIÓN LEGAL",
			"BUSINESS PLAN",
			"CAMBIAR PLAN",
			"CUENTA CREADA",
			"ESTADO ACTIVO",
			"IDIOMA SELECCIONADO");

	private final LinkedHashMap<String, Boolean> finalReport = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;

	@Before
	public void setUp() throws IOException {
		final boolean headless = Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		screenshotDir = Paths.get("target", "saleads-screenshots", timestamp);
		Files.createDirectories(screenshotDir);

		initializeReport();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() throws IOException {
		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", null);
		Assume.assumeTrue("Set saleads.login.url or SALEADS_LOGIN_URL to execute this test.",
				loginUrl != null && !loginUrl.isBlank());

		final String accountEmail = readConfig("saleads.google.email", "SALEADS_GOOGLE_EMAIL", DEFAULT_ACCOUNT_EMAIL);
		final String expectedUserName = readConfig("saleads.user.name", "SALEADS_USER_NAME", null);

		driver.get(loginUrl);
		waitForUiToLoad();

		final boolean loginOk = stepLoginWithGoogle(accountEmail);
		finalReport.put("Login", loginOk);

		if (loginOk) {
			final boolean miNegocioMenuOk = stepOpenMiNegocioMenu();
			finalReport.put("Mi Negocio menu", miNegocioMenuOk);

			final boolean agregarModalOk = miNegocioMenuOk && stepValidateAgregarNegocioModal();
			finalReport.put("Agregar Negocio modal", agregarModalOk);

			final boolean administrarViewOk = miNegocioMenuOk && stepOpenAdministrarNegocios();
			finalReport.put("Administrar Negocios view", administrarViewOk);

			final boolean infoGeneralOk = administrarViewOk && stepValidateInformacionGeneral(accountEmail, expectedUserName);
			finalReport.put("Información General", infoGeneralOk);

			final boolean detallesCuentaOk = administrarViewOk && stepValidateDetallesCuenta();
			finalReport.put("Detalles de la Cuenta", detallesCuentaOk);

			final boolean tusNegociosOk = administrarViewOk && stepValidateTusNegocios();
			finalReport.put("Tus Negocios", tusNegociosOk);

			final boolean terminosOk = administrarViewOk
					&& stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "05-terminos");
			finalReport.put("Términos y Condiciones", terminosOk);

			final boolean privacidadOk = administrarViewOk
					&& stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "06-privacidad");
			finalReport.put("Política de Privacidad", privacidadOk);
		}

		printFinalReport();
		assertAllStepsPassed();
	}

	private void initializeReport() {
		finalReport.put("Login", false);
		finalReport.put("Mi Negocio menu", false);
		finalReport.put("Agregar Negocio modal", false);
		finalReport.put("Administrar Negocios view", false);
		finalReport.put("Información General", false);
		finalReport.put("Detalles de la Cuenta", false);
		finalReport.put("Tus Negocios", false);
		finalReport.put("Términos y Condiciones", false);
		finalReport.put("Política de Privacidad", false);
	}

	private boolean stepLoginWithGoogle(final String accountEmail) throws IOException {
		final WebElement loginButton = waitForFirstVisible(DEFAULT_TIMEOUT,
				byExactText("Sign in with Google"),
				byExactText("Iniciar sesión con Google"),
				byExactText("Continuar con Google"),
				By.xpath("//button[contains(normalize-space(), 'Google')]"),
				By.xpath("//a[contains(normalize-space(), 'Google')]"));

		clickAndWaitForUi(loginButton);
		handleGoogleAccountSelector(accountEmail);

		final boolean mainInterfaceVisible = isAnyVisible(DEFAULT_TIMEOUT,
				By.tagName("main"),
				By.xpath("//aside"),
				By.xpath("//nav"),
				byContainsText("Negocio"));

		final boolean leftSidebarVisible = isAnyVisible(DEFAULT_TIMEOUT,
				By.xpath("//aside"),
				By.xpath("//nav[.//*[contains(normalize-space(), 'Negocio')]]"));

		takeScreenshot("01-dashboard");
		return mainInterfaceVisible && leftSidebarVisible;
	}

	private void handleGoogleAccountSelector(final String accountEmail) {
		final By accountByIdentifier = By.xpath("//div[@data-identifier=" + xPathLiteral(accountEmail) + "]");
		final By accountByText = byExactText(accountEmail);

		final boolean accountChooserVisible = isAnyVisible(SHORT_TIMEOUT, accountByIdentifier, accountByText);
		if (!accountChooserVisible) {
			return;
		}

		final WebElement accountOption = waitForFirstVisible(DEFAULT_TIMEOUT, accountByIdentifier, accountByText);
		clickAndWaitForUi(accountOption);

		if (isAnyVisible(SHORT_TIMEOUT, byExactText("Continuar"), byExactText("Continue"))) {
			final WebElement continueButton = waitForFirstVisible(SHORT_TIMEOUT,
					byExactText("Continuar"),
					byExactText("Continue"));
			clickAndWaitForUi(continueButton);
		}
	}

	private boolean stepOpenMiNegocioMenu() throws IOException {
		waitForFirstVisible(DEFAULT_TIMEOUT, By.xpath("//aside"), By.xpath("//nav"));

		final WebElement miNegocio = waitForFirstVisible(DEFAULT_TIMEOUT,
				byExactText("Mi Negocio"),
				byContainsText("Mi Negocio"));
		clickAndWaitForUi(miNegocio);

		final boolean agregarVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Agregar Negocio"), byContainsText("Agregar Negocio"));
		final boolean administrarVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Administrar Negocios"),
				byContainsText("Administrar Negocios"));

		takeScreenshot("02-mi-negocio-menu-expandido");
		return agregarVisible && administrarVisible;
	}

	private boolean stepValidateAgregarNegocioModal() throws IOException {
		final WebElement agregarNegocio = waitForFirstVisible(DEFAULT_TIMEOUT,
				byExactText("Agregar Negocio"),
				byContainsText("Agregar Negocio"));
		clickAndWaitForUi(agregarNegocio);

		final boolean titleVisible = isAnyVisible(DEFAULT_TIMEOUT,
				byExactText("Crear Nuevo Negocio"),
				byContainsText("Crear Nuevo Negocio"));
		final boolean inputVisible = isAnyVisible(DEFAULT_TIMEOUT,
				By.xpath("//label[normalize-space()=" + xPathLiteral("Nombre del Negocio") + "]"),
				By.xpath("//input[@placeholder=" + xPathLiteral("Nombre del Negocio") + "]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"));
		final boolean planTextVisible = isAnyVisible(DEFAULT_TIMEOUT,
				byExactText("Tienes 2 de 3 negocios"),
				byContainsText("Tienes 2 de 3 negocios"));
		final boolean cancelVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Cancelar"), byContainsText("Cancelar"));
		final boolean createVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Crear Negocio"), byContainsText("Crear Negocio"));

		takeScreenshot("03-agregar-negocio-modal");

		if (inputVisible) {
			final List<WebElement> inputs = driver.findElements(By.xpath(
					"//input[@placeholder=" + xPathLiteral("Nombre del Negocio") + "] | //input[contains(@name, 'negocio') or contains(@id, 'negocio')]"));
			for (final WebElement input : inputs) {
				if (!input.isDisplayed()) {
					continue;
				}
				input.click();
				input.clear();
				input.sendKeys("Negocio Prueba Automatización");
				break;
			}
		}

		if (cancelVisible) {
			final WebElement cancelButton = waitForFirstVisible(DEFAULT_TIMEOUT, byExactText("Cancelar"), byContainsText("Cancelar"));
			clickAndWaitForUi(cancelButton);
		}

		return titleVisible && inputVisible && planTextVisible && cancelVisible && createVisible;
	}

	private boolean stepOpenAdministrarNegocios() throws IOException {
		if (!isAnyVisible(SHORT_TIMEOUT, byExactText("Administrar Negocios"), byContainsText("Administrar Negocios"))) {
			final WebElement miNegocio = waitForFirstVisible(DEFAULT_TIMEOUT, byExactText("Mi Negocio"), byContainsText("Mi Negocio"));
			clickAndWaitForUi(miNegocio);
		}

		final WebElement administrarNegocios = waitForFirstVisible(DEFAULT_TIMEOUT,
				byExactText("Administrar Negocios"),
				byContainsText("Administrar Negocios"));
		clickAndWaitForUi(administrarNegocios);

		final boolean informacionGeneral = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Información General"), byContainsText("Información General"));
		final boolean detallesCuenta = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Detalles de la Cuenta"), byContainsText("Detalles de la Cuenta"));
		final boolean tusNegocios = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Tus Negocios"), byContainsText("Tus Negocios"));
		final boolean seccionLegal = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Sección Legal"), byContainsText("Sección Legal"));

		takeScreenshot("04-administrar-negocios");
		return informacionGeneral && detallesCuenta && tusNegocios && seccionLegal;
	}

	private boolean stepValidateInformacionGeneral(final String accountEmail, final String expectedUserName) {
		final boolean userNameVisible = isUserNameVisible(expectedUserName, accountEmail);
		final boolean emailVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText(accountEmail), byContainsText(accountEmail))
				|| isAnyVisible(DEFAULT_TIMEOUT, By.xpath("//*[contains(normalize-space(), '@')]"));
		final boolean businessPlanVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText("BUSINESS PLAN"), byContainsText("BUSINESS PLAN"));
		final boolean cambiarPlanVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Cambiar Plan"), byContainsText("Cambiar Plan"));

		return userNameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean stepValidateDetallesCuenta() {
		final boolean cuentaCreadaVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Cuenta creada"), byContainsText("Cuenta creada"));
		final boolean estadoActivoVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Estado activo"), byContainsText("Estado activo"));
		final boolean idiomaSeleccionadoVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Idioma seleccionado"), byContainsText("Idioma seleccionado"));

		return cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible;
	}

	private boolean stepValidateTusNegocios() {
		final boolean sectionVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Tus Negocios"), byContainsText("Tus Negocios"));
		final boolean addButtonVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Agregar Negocio"), byContainsText("Agregar Negocio"));
		final boolean quotaVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText("Tienes 2 de 3 negocios"), byContainsText("Tienes 2 de 3 negocios"));
		final boolean listVisible = isAnyVisible(DEFAULT_TIMEOUT,
				By.xpath("//*[normalize-space()=" + xPathLiteral("Tus Negocios")
						+ "]/following::*[(self::li or self::tr or contains(@class, 'business') or contains(@class, 'negocio')) and normalize-space(.)!=''][1]"),
				By.xpath("//ul[.//*[contains(normalize-space(), '@')]]"),
				By.xpath("//table"));

		return sectionVisible && addButtonVisible && quotaVisible && listVisible;
	}

	private boolean stepValidateLegalLink(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String appWindow = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final int handlesBefore = driver.getWindowHandles().size();

		final WebElement legalLink = waitForFirstVisible(DEFAULT_TIMEOUT, byExactText(linkText), byContainsText(linkText));
		clickAndWaitForUi(legalLink);

		wait.until(d -> d.getWindowHandles().size() > handlesBefore || !d.getCurrentUrl().equals(originalUrl));

		boolean switchedToNewTab = false;
		if (driver.getWindowHandles().size() > handlesBefore) {
			for (final String handle : driver.getWindowHandles()) {
				if (!handle.equals(appWindow)) {
					driver.switchTo().window(handle);
					switchedToNewTab = true;
					break;
				}
			}
		}

		waitForUiToLoad();

		final boolean headingVisible = isAnyVisible(DEFAULT_TIMEOUT, byExactText(headingText), byContainsText(headingText));
		final boolean contentVisible = hasLegalBodyContent();

		takeScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return headingVisible && contentVisible;
	}

	private boolean hasLegalBodyContent() {
		try {
			final WebElement body = waitForFirstVisible(DEFAULT_TIMEOUT, By.tagName("body"));
			final String bodyText = body.getText();
			return bodyText != null && bodyText.trim().length() >= 80;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private boolean isUserNameVisible(final String expectedUserName, final String accountEmail) {
		if (expectedUserName != null && !expectedUserName.isBlank()) {
			return isAnyVisible(DEFAULT_TIMEOUT, byExactText(expectedUserName), byContainsText(expectedUserName));
		}

		final List<WebElement> emailElements = driver.findElements(By.xpath("//*[contains(normalize-space(), '@')]"));
		for (final WebElement emailElement : emailElements) {
			if (!emailElement.isDisplayed()) {
				continue;
			}
			if (accountEmail != null && !accountEmail.isBlank() && !emailElement.getText().contains(accountEmail)) {
				continue;
			}

			try {
				final WebElement cardContainer = emailElement.findElement(By.xpath("./ancestor::*[self::div or self::section][1]"));
				final List<WebElement> textElements = cardContainer
						.findElements(By.xpath(".//*[self::h1 or self::h2 or self::h3 or self::h4 or self::p or self::span or self::div]"));
				for (final WebElement textElement : textElements) {
					if (!textElement.isDisplayed()) {
						continue;
					}

					final String text = normalizeText(textElement.getText());
					if (text.isBlank() || text.contains("@") || text.length() < 3) {
						continue;
					}

					if (!NON_NAME_LABELS.contains(text.toUpperCase(Locale.ROOT))) {
						return true;
					}
				}
			} catch (final NoSuchElementException ignored) {
				// Continue scanning other candidates.
			}
		}

		return false;
	}

	private void clickAndWaitForUi(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		try {
			element.click();
		} catch (final WebDriverException ex) {
			if (driver instanceof JavascriptExecutor) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			} else {
				throw ex;
			}
		}

		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> {
				try {
					final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
					return "complete".equals(readyState) || "interactive".equals(readyState);
				} catch (final WebDriverException ignored) {
					return true;
				}
			});
		} catch (final TimeoutException ignored) {
			// Some SPA transitions may not expose "complete"; continue with best effort.
		}

		try {
			Thread.sleep(500L);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private WebElement waitForFirstVisible(final Duration timeout, final By... locators) {
		final FluentWait<WebDriver> fluentWait = new FluentWait<>(driver)
				.withTimeout(timeout)
				.pollingEvery(Duration.ofMillis(250))
				.ignoring(NoSuchElementException.class)
				.ignoring(StaleElementReferenceException.class);

		return fluentWait.until(d -> {
			for (final By locator : locators) {
				final List<WebElement> candidates = d.findElements(locator);
				for (final WebElement candidate : candidates) {
					if (candidate.isDisplayed()) {
						return candidate;
					}
				}
			}
			return null;
		});
	}

	private boolean isAnyVisible(final Duration timeout, final By... locators) {
		try {
			waitForFirstVisible(timeout, locators);
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void takeScreenshot(final String fileName) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = screenshotDir.resolve(fileName + ".png");
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private void printFinalReport() {
		System.out.println("==== SaleADS Mi Negocio Final Report ====");
		for (final String stepName : finalReport.keySet()) {
			final String status = finalReport.get(stepName) ? "PASS" : "FAIL";
			System.out.println(stepName + ": " + status);
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("---- Captured legal URLs ----");
			legalUrls.forEach((label, url) -> System.out.println(label + ": " + url));
		}
		System.out.println("Screenshots path: " + screenshotDir.toAbsolutePath());
	}

	private void assertAllStepsPassed() {
		final List<String> failedSteps = new ArrayList<>();
		for (final String stepName : finalReport.keySet()) {
			if (!Boolean.TRUE.equals(finalReport.get(stepName))) {
				failedSteps.add(stepName);
			}
		}
		assertTrue("SaleADS Mi Negocio workflow failed validations: " + failedSteps, failedSteps.isEmpty());
	}

	private String readConfig(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private String normalizeText(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace('\n', ' ').replace('\r', ' ').trim().replaceAll("\\s+", " ");
	}

	private By byExactText(final String text) {
		return By.xpath("//*[normalize-space()=" + xPathLiteral(text) + "]");
	}

	private By byContainsText(final String text) {
		return By.xpath("//*[contains(normalize-space(), " + xPathLiteral(text) + ")]");
	}

	private String xPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}
}
