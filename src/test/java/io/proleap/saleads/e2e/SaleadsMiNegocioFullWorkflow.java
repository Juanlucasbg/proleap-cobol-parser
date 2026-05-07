package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflow {

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Información General";
	private static final String DETALLES_DE_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "Términos y Condiciones";
	private static final String POLITICA = "Política de Privacidad";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
			Pattern.CASE_INSENSITIVE);

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final AtomicInteger screenshotCounter = new AtomicInteger(1);

	private WebDriver driver;
	private WebDriverWait wait;
	private Duration timeout;
	private Path evidenceDir;

	@Before
	public void setUp() throws Exception {
		timeout = Duration.ofSeconds(readTimeoutSeconds());
		evidenceDir = Paths.get(readSetting("SALEADS_EVIDENCE_DIR",
				"target/saleads-evidence/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");
		if (Boolean.parseBoolean(readSetting("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		final String remoteWebDriverUrl = readSetting("SALEADS_REMOTE_WEBDRIVER_URL", "");
		if (!remoteWebDriverUrl.isBlank()) {
			driver = new RemoteWebDriver(new URL(remoteWebDriverUrl), options);
		} else {
			driver = new ChromeDriver(options);
		}

		wait = new WebDriverWait(driver, timeout);
		final String saleadsLoginUrl = readSetting("SALEADS_LOGIN_URL", "");
		if (!saleadsLoginUrl.isBlank()) {
			driver.get(saleadsLoginUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		recordStepResult(LOGIN, runStep(LOGIN, this::loginWithGoogleAndValidateDashboard));
		recordStepResult(MI_NEGOCIO_MENU, runStep(MI_NEGOCIO_MENU, this::openMiNegocioMenu));
		recordStepResult(AGREGAR_NEGOCIO_MODAL, runStep(AGREGAR_NEGOCIO_MODAL, this::validateAgregarNegocioModal));
		recordStepResult(ADMINISTRAR_NEGOCIOS_VIEW, runStep(ADMINISTRAR_NEGOCIOS_VIEW, this::openAdministrarNegocios));
		recordStepResult(INFORMACION_GENERAL, runStep(INFORMACION_GENERAL, this::validateInformacionGeneral));
		recordStepResult(DETALLES_DE_CUENTA, runStep(DETALLES_DE_CUENTA, this::validateDetallesDeLaCuenta));
		recordStepResult(TUS_NEGOCIOS, runStep(TUS_NEGOCIOS, this::validateTusNegocios));
		recordStepResult(TERMINOS, runStep(TERMINOS, this::validateTerminosYCondiciones));
		recordStepResult(POLITICA, runStep(POLITICA, this::validatePoliticaDePrivacidad));

		writeFinalReport();
		final List<String> failedValidations = finalReport.entrySet().stream().filter(entry -> !entry.getValue())
				.map(Map.Entry::getKey).collect(Collectors.toList());

		assertTrue("SaleADS workflow validation failed for: " + failedValidations + ". Evidence folder: "
				+ evidenceDir.toAbsolutePath(), failedValidations.isEmpty());
	}

	private boolean loginWithGoogleAndValidateDashboard() throws Exception {
		final boolean clickedLogin = clickByVisibleText("Sign in with Google", "Iniciar sesión con Google",
				"Iniciar sesión con Google", "Continuar con Google", "Google");
		if (!clickedLogin) {
			return false;
		}

		selectGoogleAccountIfPresent("juanlucasbarbiergarzon@gmail.com");
		final boolean mainInterfaceVisible = hasVisibleText(Duration.ofSeconds(20), "Negocio", "Mi Negocio");
		final boolean sidebarVisible = isVisible(By.xpath("//aside | //nav"), Duration.ofSeconds(20));
		takeScreenshot("dashboard-loaded");
		return mainInterfaceVisible && sidebarVisible;
	}

	private boolean openMiNegocioMenu() throws Exception {
		if (!isVisible(By.xpath("//aside | //nav"), Duration.ofSeconds(10))) {
			return false;
		}

		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		final boolean agregarNegocioVisible = hasVisibleText(Duration.ofSeconds(10), "Agregar Negocio");
		final boolean administrarNegociosVisible = hasVisibleText(Duration.ofSeconds(10), "Administrar Negocios");
		takeScreenshot("mi-negocio-menu-expanded");
		return agregarNegocioVisible && administrarNegociosVisible;
	}

	private boolean validateAgregarNegocioModal() throws Exception {
		if (!clickByVisibleText("Agregar Negocio")) {
			return false;
		}

		final boolean modalTitleVisible = hasVisibleText(Duration.ofSeconds(10), "Crear Nuevo Negocio");
		final boolean nombreDelNegocioInputVisible = isVisible(By.xpath(
				"//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"),
				Duration.ofSeconds(10));
		final boolean businessCounterVisible = hasVisibleText(Duration.ofSeconds(10), "Tienes 2 de 3 negocios");
		final boolean cancelarVisible = hasVisibleText(Duration.ofSeconds(10), "Cancelar");
		final boolean crearNegocioVisible = hasVisibleText(Duration.ofSeconds(10), "Crear Negocio");

		takeScreenshot("agregar-negocio-modal");
		typeInNombreDelNegocioFieldIfPresent("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		return modalTitleVisible && nombreDelNegocioInputVisible && businessCounterVisible && cancelarVisible
				&& crearNegocioVisible;
	}

	private boolean openAdministrarNegocios() throws Exception {
		if (!hasVisibleText(Duration.ofSeconds(3), "Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		if (!clickByVisibleText("Administrar Negocios")) {
			return false;
		}

		final boolean informacionGeneralVisible = hasVisibleText(Duration.ofSeconds(15), "Información General");
		final boolean detallesCuentaVisible = hasVisibleText(Duration.ofSeconds(15), "Detalles de la Cuenta");
		final boolean tusNegociosVisible = hasVisibleText(Duration.ofSeconds(15), "Tus Negocios");
		final boolean seccionLegalVisible = hasVisibleText(Duration.ofSeconds(15), "Sección Legal");
		takeScreenshot("administrar-negocios-view");
		return informacionGeneralVisible && detallesCuentaVisible && tusNegociosVisible && seccionLegalVisible;
	}

	private boolean validateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		final String sectionText = section != null ? section.getText() : safeBodyText();
		final boolean userNameVisible = hasLikelyUserName(sectionText);
		final boolean userEmailVisible = EMAIL_PATTERN.matcher(sectionText).find()
				|| EMAIL_PATTERN.matcher(safeBodyText()).find();
		final boolean businessPlanVisible = hasVisibleText(Duration.ofSeconds(8), "BUSINESS PLAN");
		final boolean cambiarPlanVisible = hasVisibleText(Duration.ofSeconds(8), "Cambiar Plan");
		return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean validateDetallesDeLaCuenta() {
		return hasVisibleText(Duration.ofSeconds(8), "Cuenta creada")
				&& hasVisibleText(Duration.ofSeconds(8), "Estado activo")
				&& hasVisibleText(Duration.ofSeconds(8), "Idioma seleccionado");
	}

	private boolean validateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		final boolean businessListVisible = isBusinessListVisible(section);
		final boolean addButtonVisible = hasVisibleText(Duration.ofSeconds(8), "Agregar Negocio");
		final boolean businessCounterVisible = hasVisibleText(Duration.ofSeconds(8), "Tienes 2 de 3 negocios");
		return businessListVisible && addButtonVisible && businessCounterVisible;
	}

	private boolean validateTerminosYCondiciones() throws Exception {
		return validateLegalPage(Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
				Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"), TERMINOS);
	}

	private boolean validatePoliticaDePrivacidad() throws Exception {
		return validateLegalPage(Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
				Arrays.asList("Política de Privacidad", "Politica de Privacidad"), POLITICA);
	}

	private boolean validateLegalPage(final List<String> linkCandidates, final List<String> headingCandidates,
			final String reportKey) throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		if (!clickByVisibleText(linkCandidates.toArray(new String[0]))) {
			return false;
		}

		final String destinationHandle = waitForPotentialNewTab(handlesBeforeClick, originalHandle);
		final boolean openedInNewTab = !Objects.equals(destinationHandle, originalHandle);
		if (openedInNewTab) {
			driver.switchTo().window(destinationHandle);
			waitForUiToLoad();
		}

		final boolean headingVisible = hasVisibleText(Duration.ofSeconds(15), headingCandidates.toArray(new String[0]));
		final boolean legalContentVisible = safeBodyText().trim().length() > 200;
		legalUrls.put(reportKey, driver.getCurrentUrl());
		takeScreenshot(slugify(reportKey) + "-page");

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else if (!Objects.equals(originalUrl, driver.getCurrentUrl())) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return headingVisible && legalContentVisible;
	}

	private void recordStepResult(final String step, final boolean result) {
		finalReport.put(step, result);
		System.out.println(step + ": " + (result ? "PASS" : "FAIL"));
	}

	private boolean runStep(final String stepName, final StepAction action) {
		try {
			final boolean stepResult = action.run();
			if (!stepResult) {
				takeScreenshot("failed-" + slugify(stepName));
			}
			return stepResult;
		} catch (final Exception exception) {
			System.out.println("Step '" + stepName + "' failed with error: " + exception.getMessage());
			try {
				takeScreenshot("error-" + slugify(stepName));
			} catch (final IOException ioException) {
				System.out.println("Could not capture error screenshot: " + ioException.getMessage());
			}
			return false;
		}
	}

	private boolean clickByVisibleText(final String... visibleTexts) {
		for (final String visibleText : visibleTexts) {
			final WebElement targetElement = findVisibleTextElement(visibleText);
			if (targetElement != null) {
				try {
					((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});",
							targetElement);
					wait.until(ExpectedConditions.elementToBeClickable(targetElement)).click();
				} catch (final Exception exception) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", targetElement);
				}
				waitForUiToLoad();
				return true;
			}
		}
		return false;
	}

	private WebElement findVisibleTextElement(final String visibleText) {
		final List<By> selectors = Arrays.asList(
				By.xpath("//button[normalize-space()=" + asXpathLiteral(visibleText) + "]"),
				By.xpath("//a[normalize-space()=" + asXpathLiteral(visibleText) + "]"),
				By.xpath("//*[normalize-space()=" + asXpathLiteral(visibleText) + "]"),
				By.xpath("//*[contains(normalize-space(), " + asXpathLiteral(visibleText) + ")]"));

		for (final By selector : selectors) {
			for (final WebElement candidate : driver.findElements(selector)) {
				if (candidate.isDisplayed()) {
					return candidate;
				}
			}
		}
		return null;
	}

	private boolean hasVisibleText(final Duration visibilityTimeout, final String... visibleTexts) {
		final WebDriverWait visibilityWait = new WebDriverWait(driver, visibilityTimeout);
		for (final String visibleText : visibleTexts) {
			try {
				visibilityWait.until(webDriver -> findVisibleTextElement(visibleText) != null);
				return true;
			} catch (final TimeoutException exception) {
				// try next candidate
			}
		}
		return false;
	}

	private void selectGoogleAccountIfPresent(final String emailAddress) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				final WebElement accountOption = findVisibleTextElement(emailAddress);
				if (accountOption != null) {
					clickByVisibleText(emailAddress);
					return;
				}
			}
			sleep(300);
		}
	}

	private void typeInNombreDelNegocioFieldIfPresent(final String businessName) {
		final WebElement input = firstVisibleElement(By.xpath(
				"//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"));
		if (input != null) {
			input.click();
			input.clear();
			input.sendKeys(businessName);
		}
	}

	private boolean isVisible(final By selector, final Duration visibilityTimeout) {
		try {
			new WebDriverWait(driver, visibilityTimeout).until(ExpectedConditions.visibilityOfElementLocated(selector));
			return true;
		} catch (final TimeoutException exception) {
			return false;
		}
	}

	private WebElement findSectionByHeading(final String headingText) {
		final WebElement heading = firstVisibleElement(By.xpath("//*[contains(normalize-space(), "
				+ asXpathLiteral(headingText) + ") and (self::h1 or self::h2 or self::h3 or self::h4 or self::span)]"));
		if (heading == null) {
			return null;
		}

		try {
			return heading.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		} catch (final Exception exception) {
			return null;
		}
	}

	private boolean isBusinessListVisible(final WebElement businessesSection) {
		if (businessesSection == null) {
			return false;
		}

		final List<WebElement> listCandidates = businessesSection.findElements(By
				.xpath(".//li | .//tr | .//article | .//div[contains(@class, 'business') or contains(@class, 'negocio')]"));
		final boolean hasVisibleListItem = listCandidates.stream().anyMatch(WebElement::isDisplayed);
		if (hasVisibleListItem) {
			return true;
		}

		final long nonEmptyLines = Arrays.stream(businessesSection.getText().split("\\R")).map(String::trim)
				.filter(line -> !line.isEmpty()).count();
		return nonEmptyLines >= 3;
	}

	private boolean hasLikelyUserName(final String sectionText) {
		final Set<String> excludedValues = new LinkedHashSet<>(
				Arrays.asList("INFORMACIÓN GENERAL", "BUSINESS PLAN", "CAMBIAR PLAN", "DETALLES DE LA CUENTA",
						"TUS NEGOCIOS", "SECCIÓN LEGAL", "CUENTA CREADA", "ESTADO ACTIVO", "IDIOMA SELECCIONADO"));

		return Arrays.stream(sectionText.split("\\R")).map(String::trim).filter(line -> !line.isEmpty())
				.filter(line -> !line.contains("@")).filter(line -> !line.matches(".*\\d.*"))
				.filter(line -> !excludedValues.contains(line.toUpperCase())).anyMatch(line -> line.matches("(?U).*[\\p{L}]{2,}.*"));
	}

	private WebElement firstVisibleElement(final By selector) {
		for (final WebElement element : driver.findElements(selector)) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		return null;
	}

	private String waitForPotentialNewTab(final Set<String> handlesBeforeClick, final String currentHandle) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
		while (System.currentTimeMillis() < deadline) {
			final Set<String> handlesNow = driver.getWindowHandles();
			if (handlesNow.size() > handlesBeforeClick.size()) {
				for (final String handle : handlesNow) {
					if (!handlesBeforeClick.contains(handle)) {
						return handle;
					}
				}
			}
			sleep(200);
		}
		return currentHandle;
	}

	private void waitForUiToLoad() {
		final ExpectedCondition<Boolean> domReady = webDriver -> {
			try {
				return "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState"));
			} catch (final Exception exception) {
				return true;
			}
		};

		wait.until(domReady);
		sleep(400);
	}

	private Path takeScreenshot(final String checkpoint) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path screenshotPath = evidenceDir.resolve(
				String.format("%02d-%s.png", screenshotCounter.getAndIncrement(), slugify(checkpoint)));
		Files.copy(screenshot.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
		return screenshotPath;
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		reportBuilder.append(System.lineSeparator());
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			reportBuilder.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL")
					.append(System.lineSeparator());
		}

		reportBuilder.append(System.lineSeparator()).append("Legal URLs:").append(System.lineSeparator());
		reportBuilder.append("Términos y Condiciones URL: ")
				.append(legalUrls.getOrDefault(TERMINOS, "NOT CAPTURED")).append(System.lineSeparator());
		reportBuilder.append("Política de Privacidad URL: ").append(legalUrls.getOrDefault(POLITICA, "NOT CAPTURED"))
				.append(System.lineSeparator());

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, reportBuilder.toString(), StandardCharsets.UTF_8);
		System.out.println(reportBuilder);
		System.out.println("Evidence folder: " + evidenceDir.toAbsolutePath());
	}

	private String readSetting(final String envKey, final String defaultValue) {
		final String propertyKey = envKey.toLowerCase().replace('_', '.');
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String environmentValue = System.getenv(envKey);
		if (environmentValue != null && !environmentValue.isBlank()) {
			return environmentValue.trim();
		}

		return defaultValue;
	}

	private long readTimeoutSeconds() {
		final String value = readSetting("SALEADS_TIMEOUT_SECONDS", "25");
		try {
			return Long.parseLong(value);
		} catch (final NumberFormatException exception) {
			return 25L;
		}
	}

	private String safeBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception exception) {
			return "";
		}
	}

	private String slugify(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String asXpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final String[] parts = text.split("'");
		for (int i = 0; i < parts.length; i++) {
			result.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				result.append(", \"'\", ");
			}
		}
		result.append(")");
		return result.toString();
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		boolean run() throws Exception;
	}
}
