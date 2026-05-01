package io.proleap.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Assert;
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

	private static final String FIELD_LOGIN = "Login";
	private static final String FIELD_MENU = "Mi Negocio menu";
	private static final String FIELD_MODAL = "Agregar Negocio modal";
	private static final String FIELD_ADMIN = "Administrar Negocios view";
	private static final String FIELD_INFO_GENERAL = "Información General";
	private static final String FIELD_DETALLES = "Detalles de la Cuenta";
	private static final String FIELD_TUS_NEGOCIOS = "Tus Negocios";
	private static final String FIELD_TERMINOS = "Términos y Condiciones";
	private static final String FIELD_POLITICA = "Política de Privacidad";

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepStatus> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String googleAccountEmail;
	private String currentAppWindow;

	@Before
	public void setUp() throws IOException {
		initializeReport();
		googleAccountEmail = readConfig("saleads.account.email", "SALEADS_ACCOUNT_EMAIL")
				.orElse("juanlucasbarbiergarzon@gmail.com");

		evidenceDir = Paths.get("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		final boolean headless = Boolean.parseBoolean(
				readConfig("saleads.headless", "SALEADS_HEADLESS").orElse("false"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		readConfig("saleads.chrome.debugger", "SALEADS_CHROME_DEBUGGER")
				.ifPresent(address -> options.setExperimentalOption("debuggerAddress", address));

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final Optional<String> loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		if (loginUrl.isPresent()) {
			driver.get(loginUrl.get());
		}

		waitForUiLoad();
	}

	@After
	public void tearDown() {
		try {
			printFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final boolean loginPass = validateLoginWithGoogle();
		mark(FIELD_LOGIN, loginPass, loginPass ? "Dashboard y sidebar visibles." : "No fue posible validar login.");

		final boolean menuPass = openMiNegocioMenu();
		mark(FIELD_MENU, menuPass, menuPass ? "Submenú visible con opciones clave." : "No se validó submenú Mi Negocio.");

		final boolean modalPass = validateAgregarNegocioModal();
		mark(FIELD_MODAL, modalPass, modalPass ? "Modal Crear Nuevo Negocio validado." : "Faltan elementos requeridos del modal.");

		final boolean adminPass = openAdministrarNegocios();
		mark(FIELD_ADMIN, adminPass,
				adminPass ? "Secciones principales de cuenta visibles." : "No se validaron todas las secciones de cuenta.");

		final boolean infoGeneralPass = validateInformacionGeneral();
		mark(FIELD_INFO_GENERAL, infoGeneralPass,
				infoGeneralPass ? "Nombre/email/plan visibles." : "Información General incompleta.");

		final boolean detallesPass = validateDetallesCuenta();
		mark(FIELD_DETALLES, detallesPass,
				detallesPass ? "Campos de estado de cuenta visibles." : "Detalles de cuenta incompletos.");

		final boolean tusNegociosPass = validateTusNegocios();
		mark(FIELD_TUS_NEGOCIOS, tusNegociosPass,
				tusNegociosPass ? "Listado y límites visibles." : "Validación de Tus Negocios incompleta.");

		final LegalValidationResult terminos = validateLegalDocument(
				Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
				Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
				"08-terminos-y-condiciones");
		mark(FIELD_TERMINOS, terminos.pass, terminos.pass
				? "Documento legal válido. URL: " + terminos.finalUrl
				: "No se validó correctamente Términos y Condiciones. URL: " + terminos.finalUrl);

		final LegalValidationResult politica = validateLegalDocument(
				Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
				Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
				"09-politica-de-privacidad");
		mark(FIELD_POLITICA, politica.pass, politica.pass
				? "Documento legal válido. URL: " + politica.finalUrl
				: "No se validó correctamente Política de Privacidad. URL: " + politica.finalUrl);

		final List<String> failedFields = report.entrySet().stream().filter(entry -> !entry.getValue().pass)
				.map(Map.Entry::getKey).toList();
		Assert.assertTrue("Validaciones fallidas: " + failedFields, failedFields.isEmpty());
	}

	private boolean validateLoginWithGoogle() throws IOException {
		if (isMainApplicationVisible()) {
			currentAppWindow = driver.getWindowHandle();
			takeScreenshot("01-dashboard-loaded");
			return true;
		}

		if (isBlankPageWithoutLoginUrl()) {
			return false;
		}

		final List<String> loginCtas = Arrays.asList("Sign in with Google", "Continue with Google", "Iniciar sesión con Google",
				"Iniciar sesion con Google", "Continuar con Google", "Acceder con Google");
		final WebElement loginButton = waitForFirstClickableByText(loginCtas, DEFAULT_TIMEOUT);
		if (loginButton == null) {
			return false;
		}

		final Set<String> windowsBeforeLoginClick = driver.getWindowHandles();
		clickAndWait(loginButton);
		trySelectGoogleAccountIfPrompted(windowsBeforeLoginClick);

		final boolean appLoaded = waitForMainApplication(DEFAULT_TIMEOUT.plusSeconds(30));
		if (appLoaded) {
			currentAppWindow = driver.getWindowHandle();
			takeScreenshot("01-dashboard-loaded");
		}
		return appLoaded;
	}

	private boolean openMiNegocioMenu() throws IOException {
		if (!isMainApplicationVisible()) {
			return false;
		}

		expandMiNegocioIfNeeded();
		final boolean agregarVisible = containsAnyVisibleText(Arrays.asList("Agregar Negocio"), DEFAULT_TIMEOUT);
		final boolean administrarVisible = containsAnyVisibleText(Arrays.asList("Administrar Negocios"), DEFAULT_TIMEOUT);
		takeScreenshot("02-mi-negocio-menu-expanded");
		return agregarVisible && administrarVisible;
	}

	private boolean validateAgregarNegocioModal() throws IOException {
		final WebElement agregarNegocio = waitForFirstClickableByText(Arrays.asList("Agregar Negocio"), DEFAULT_TIMEOUT);
		if (agregarNegocio == null) {
			return false;
		}

		clickAndWait(agregarNegocio);
		final boolean titleVisible = containsAnyVisibleText(Arrays.asList("Crear Nuevo Negocio"), DEFAULT_TIMEOUT);
		final boolean inputExists = existsElement(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio')]"
						+ " | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));
		final boolean limitTextVisible = containsAnyVisibleText(Arrays.asList("Tienes 2 de 3 negocios"), DEFAULT_TIMEOUT);
		final boolean cancelarVisible = containsAnyVisibleText(Arrays.asList("Cancelar"), SHORT_TIMEOUT);
		final boolean crearVisible = containsAnyVisibleText(Arrays.asList("Crear Negocio"), SHORT_TIMEOUT);

		takeScreenshot("03-agregar-negocio-modal");

		if (inputExists) {
			final WebElement input = waitForFirstVisible(By.xpath(
					"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio')]"
							+ " | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"),
					SHORT_TIMEOUT);
			if (input != null) {
				input.click();
				input.clear();
				input.sendKeys("Negocio Prueba Automatización");
			}
		}

		final WebElement cancelar = waitForFirstClickableByText(Arrays.asList("Cancelar"), SHORT_TIMEOUT);
		if (cancelar != null) {
			clickAndWait(cancelar);
		}

		return titleVisible && inputExists && limitTextVisible && cancelarVisible && crearVisible;
	}

	private boolean openAdministrarNegocios() throws IOException {
		expandMiNegocioIfNeeded();
		final WebElement administrar = waitForFirstClickableByText(Arrays.asList("Administrar Negocios"), DEFAULT_TIMEOUT);
		if (administrar == null) {
			return false;
		}

		clickAndWait(administrar);
		waitForUiLoad();
		currentAppWindow = driver.getWindowHandle();

		final boolean infoGeneral = containsAnyVisibleText(Arrays.asList("Información General", "Informacion General"), DEFAULT_TIMEOUT);
		final boolean detalles = containsAnyVisibleText(Arrays.asList("Detalles de la Cuenta"), DEFAULT_TIMEOUT);
		final boolean tusNegocios = containsAnyVisibleText(Arrays.asList("Tus Negocios"), DEFAULT_TIMEOUT);
		final boolean legal = containsAnyVisibleText(Arrays.asList("Sección Legal", "Seccion Legal"), DEFAULT_TIMEOUT);

		takeScreenshot("04-administrar-negocios");
		return infoGeneral && detalles && tusNegocios && legal;
	}

	private boolean validateInformacionGeneral() {
		final String bodyText = getBodyTextNormalized();
		final boolean hasAnyEmail = EMAIL_PATTERN.matcher(getBodyTextRaw()).find();
		final boolean accountEmailVisible = containsNormalized(bodyText, googleAccountEmail.toLowerCase(Locale.ROOT)) || hasAnyEmail;
		final boolean userNameVisible = containsAnyVisibleText(Arrays.asList("Nombre", "Usuario", "Perfil"), SHORT_TIMEOUT)
				|| containsNormalized(bodyText, "juan")
				|| containsNormalized(bodyText, "lucas");
		final boolean businessPlanVisible = containsAnyVisibleText(Arrays.asList("BUSINESS PLAN"), SHORT_TIMEOUT);
		final boolean cambiarPlanVisible = containsAnyVisibleText(Arrays.asList("Cambiar Plan"), SHORT_TIMEOUT);
		return userNameVisible && accountEmailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean validateDetallesCuenta() {
		final String bodyText = getBodyTextNormalized();
		final boolean cuentaCreada = containsNormalized(bodyText, "cuenta creada");
		final boolean estadoActivo = containsNormalized(bodyText, "estado activo");
		final boolean idiomaSeleccionado = containsNormalized(bodyText, "idioma seleccionado");
		return cuentaCreada && estadoActivo && idiomaSeleccionado;
	}

	private boolean validateTusNegocios() {
		final boolean tituloVisible = containsAnyVisibleText(Arrays.asList("Tus Negocios"), SHORT_TIMEOUT);
		final boolean agregarVisible = containsAnyVisibleText(Arrays.asList("Agregar Negocio"), SHORT_TIMEOUT);
		final boolean limitTextVisible = containsAnyVisibleText(Arrays.asList("Tienes 2 de 3 negocios"), SHORT_TIMEOUT);

		final WebElement section = waitForFirstVisible(By.xpath(
				"//*[contains(normalize-space(.),'Tus Negocios')]/ancestor::*[self::section or self::div][1]"), SHORT_TIMEOUT);
		final boolean hasListLikeContent;
		if (section == null) {
			hasListLikeContent = false;
		} else {
			final int listCandidateCount = section.findElements(By.xpath(".//li | .//tr | .//*[contains(@class,'card')]")).size();
			hasListLikeContent = listCandidateCount > 0 || section.getText().trim().length() > 40;
		}

		return tituloVisible && agregarVisible && limitTextVisible && hasListLikeContent;
	}

	private LegalValidationResult validateLegalDocument(final List<String> linkCandidates, final List<String> headingCandidates,
			final String screenshotName) throws IOException {
		final String originalUrl = driver.getCurrentUrl();
		final String originalWindow = currentAppWindow == null ? driver.getWindowHandle() : currentAppWindow;
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		final WebElement link = waitForFirstClickableByText(linkCandidates, DEFAULT_TIMEOUT);
		if (link == null) {
			return new LegalValidationResult(false, "");
		}

		clickAndWait(link);
		final String newWindow = waitForNewWindow(handlesBeforeClick, Duration.ofSeconds(10));
		final boolean openedNewTab = newWindow != null;

		if (openedNewTab) {
			driver.switchTo().window(newWindow);
			waitForUiLoad();
		}

		final boolean headingVisible = containsAnyVisibleText(headingCandidates, DEFAULT_TIMEOUT);
		final String legalBody = getBodyTextRaw();
		final boolean legalContentVisible = legalBody != null && legalBody.trim().length() > 200;
		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiLoad();
		} else if (!finalUrl.equals(originalUrl)) {
			driver.navigate().back();
			waitForUiLoad();
		}

		currentAppWindow = driver.getWindowHandle();
		return new LegalValidationResult(headingVisible && legalContentVisible, finalUrl);
	}

	private void expandMiNegocioIfNeeded() {
		if (containsAnyVisibleText(Arrays.asList("Agregar Negocio"), SHORT_TIMEOUT)
				&& containsAnyVisibleText(Arrays.asList("Administrar Negocios"), SHORT_TIMEOUT)) {
			return;
		}

		final List<String> menuCandidates = Arrays.asList("Mi Negocio", "Mi negocio", "Negocio");
		final WebElement menu = waitForFirstClickableByText(menuCandidates, DEFAULT_TIMEOUT);
		if (menu != null) {
			clickAndWait(menu);
		}
	}

	private void trySelectGoogleAccountIfPrompted(final Set<String> windowsBeforeLoginClick) {
		waitUntil(Duration.ofSeconds(20), ignored -> {
			final String newWindow = waitForNewWindow(windowsBeforeLoginClick, Duration.ofSeconds(1));
			if (newWindow != null) {
				driver.switchTo().window(newWindow);
				waitForUiLoad();
			}

			final WebElement accountOption = waitForFirstClickableByText(Arrays.asList(googleAccountEmail), Duration.ofSeconds(2));
			if (accountOption != null) {
				clickAndWait(accountOption);
				return true;
			}
			return false;
		});
	}

	private boolean waitForMainApplication(final Duration timeout) {
		return waitUntil(timeout, ignored -> {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				if (isMainApplicationVisible()) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isMainApplicationVisible() {
		final boolean sidebarVisible = existsVisibleElement(By.xpath("//aside | //nav"));
		final boolean negocioTextVisible = containsAnyVisibleText(Arrays.asList("Mi Negocio", "Negocio"), SHORT_TIMEOUT);
		return sidebarVisible && negocioTextVisible;
	}

	private boolean isBlankPageWithoutLoginUrl() {
		final Optional<String> configuredLoginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		return configuredLoginUrl.isEmpty() && "about:blank".equals(driver.getCurrentUrl());
	}

	private void clickAndWait(final WebElement element) {
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiLoad();
	}

	private void waitForUiLoad() {
		try {
			wait.until((ExpectedCondition<Boolean>) currentDriver -> {
				if (currentDriver == null) {
					return false;
				}
				final Object state = ((JavascriptExecutor) currentDriver).executeScript("return document.readyState");
				return "complete".equals(String.valueOf(state));
			});
		} catch (final Exception ignored) {
			// Some OAuth transitions may keep readyState in flux. Continue with explicit checks.
		}
		sleep(300);
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDir.resolve(checkpointName + ".png");
		Files.copy(screenshot.toPath(), destination);
	}

	private boolean containsAnyVisibleText(final List<String> candidates, final Duration timeout) {
		return waitUntil(timeout, ignored -> {
			final String bodyText = getBodyTextNormalized();
			if (bodyText.isEmpty()) {
				return false;
			}
			for (final String candidate : candidates) {
				if (containsNormalized(bodyText, candidate)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean existsElement(final By locator) {
		return !driver.findElements(locator).isEmpty();
	}

	private boolean existsVisibleElement(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private WebElement waitForFirstVisible(final By locator, final Duration timeout) {
		final List<WebElement> found = new ArrayList<>();
		final boolean visible = waitUntil(timeout, ignored -> {
			found.clear();
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					found.add(element);
				}
			}
			return !found.isEmpty();
		});
		return visible ? found.get(0) : null;
	}

	private WebElement waitForFirstClickableByText(final List<String> textCandidates, final Duration timeout) {
		final List<WebElement> found = new ArrayList<>();
		final boolean clickableFound = waitUntil(timeout, ignored -> {
			found.clear();
			for (final String text : textCandidates) {
				final String literal = asXpathLiteral(text);
				final String xpath = "//button[contains(normalize-space(.)," + literal + ")]"
						+ " | //a[contains(normalize-space(.)," + literal + ")]"
						+ " | //*[@role='button' and contains(normalize-space(.)," + literal + ")]"
						+ " | //*[(self::div or self::span) and contains(normalize-space(.)," + literal + ")]";
				final List<WebElement> elements = driver.findElements(By.xpath(xpath));
				for (final WebElement element : elements) {
					if (element.isDisplayed() && element.isEnabled()) {
						found.add(element);
						return true;
					}
				}
			}
			return false;
		});
		return clickableFound ? found.get(0) : null;
	}

	private String waitForNewWindow(final Set<String> previousHandles, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(currentDriver -> {
				if (currentDriver == null) {
					return null;
				}
				for (final String handle : currentDriver.getWindowHandles()) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private boolean waitUntil(final Duration timeout, final java.util.function.Function<WebDriver, Boolean> condition) {
		try {
			return new WebDriverWait(driver, timeout).until(condition::apply);
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private String getBodyTextRaw() {
		final WebElement body = waitForFirstVisible(By.tagName("body"), SHORT_TIMEOUT);
		return body == null ? "" : body.getText();
	}

	private String getBodyTextNormalized() {
		return normalizeText(getBodyTextRaw());
	}

	private String normalizeText(final String input) {
		final String decomposed = Normalizer.normalize(input == null ? "" : input, Normalizer.Form.NFD);
		return decomposed.replaceAll("\\p{M}", "").replace('\u00A0', ' ').toLowerCase(Locale.ROOT).replaceAll("\\s+", " ")
				.trim();
	}

	private boolean containsNormalized(final String normalizedContainer, final String candidate) {
		return normalizedContainer.contains(normalizeText(candidate));
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				builder.append(", ");
			}
			if (chars[i] == '\'') {
				builder.append("\"").append(chars[i]).append("\"");
			} else {
				builder.append("'").append(chars[i]).append("'");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private Optional<String> readConfig(final String propertyKey, final String envKey) {
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return Optional.of(propertyValue.trim());
		}
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return Optional.of(envValue.trim());
		}
		return Optional.empty();
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruption) {
			Thread.currentThread().interrupt();
		}
	}

	private void initializeReport() {
		report.put(FIELD_LOGIN, StepStatus.notExecuted());
		report.put(FIELD_MENU, StepStatus.notExecuted());
		report.put(FIELD_MODAL, StepStatus.notExecuted());
		report.put(FIELD_ADMIN, StepStatus.notExecuted());
		report.put(FIELD_INFO_GENERAL, StepStatus.notExecuted());
		report.put(FIELD_DETALLES, StepStatus.notExecuted());
		report.put(FIELD_TUS_NEGOCIOS, StepStatus.notExecuted());
		report.put(FIELD_TERMINOS, StepStatus.notExecuted());
		report.put(FIELD_POLITICA, StepStatus.notExecuted());
	}

	private void mark(final String field, final boolean pass, final String details) {
		report.put(field, new StepStatus(pass, details));
	}

	private void printFinalReport() {
		System.out.println();
		System.out.println("========== SaleADS Mi Negocio Full Test Report ==========");
		for (final Map.Entry<String, StepStatus> entry : report.entrySet()) {
			final String status = entry.getValue().pass ? "PASS" : "FAIL";
			System.out.println(entry.getKey() + ": " + status + " - " + entry.getValue().details);
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("==========================================================");
		System.out.println();
	}

	private static final class LegalValidationResult {
		private final boolean pass;
		private final String finalUrl;

		private LegalValidationResult(final boolean pass, final String finalUrl) {
			this.pass = pass;
			this.finalUrl = finalUrl;
		}
	}

	private static final class StepStatus {
		private final boolean pass;
		private final String details;

		private StepStatus(final boolean pass, final String details) {
			this.pass = pass;
			this.details = details;
		}

		private static StepStatus notExecuted() {
			return new StepStatus(false, "No ejecutado.");
		}
	}
}
