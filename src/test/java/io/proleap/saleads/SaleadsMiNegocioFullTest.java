package io.proleap.saleads;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final Duration SHORT_WAIT = Duration.ofSeconds(10);
	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final LinkedHashMap<String, StepResult> report = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setup() throws Exception {
		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		final String suiteTimestamp = LocalDateTime.now().format(TS_FORMATTER);
		evidenceDir = Paths.get("target", "saleads-evidence", suiteTimestamp);
		Files.createDirectories(evidenceDir);
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());

		final String baseUrl = readConfig("saleads.baseUrl", "SALEADS_BASE_URL");
		if (baseUrl != null && !baseUrl.isBlank()) {
			driver.get(baseUrl);
			waitForUiToLoad();
		} else {
			System.out.println("No base URL configured. " +
					"Set -Dsaleads.baseUrl or SALEADS_BASE_URL to start from the environment login page.");
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		printFinalReport();

		final boolean allPassed = report.values().stream().allMatch(result -> result.status == StepStatus.PASS);
		Assert.assertTrue("At least one validation step failed. See report output above.", allPassed);
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
				"Continuar con Google", "Login con Google", "Google");
		waitForUiToLoad();

		selectGoogleAccountIfPresent(readConfig("saleads.googleEmail", "SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL));

		assertVisibleText("Negocio");
		assertSidebarVisible();
		takeScreenshot("01-dashboard");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		waitForUiToLoad();
		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertVisibleText("Crear Nuevo Negocio");
		final WebElement businessNameInput = findBusinessNameInput();
		Assert.assertNotNull("Input 'Nombre del Negocio' does not exist.", businessNameInput);
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal");

		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isVisibleTextPresent(SHORT_WAIT, "Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertVisibleText("Información General", "Informacion General");
		assertVisibleText("Detalles de la Cuenta", "Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal", "Seccion Legal");
		takeScreenshot("04-administrar-negocios-view-full");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
		assertVisibleEmailPresent();
		assertVisibleUserNamePresent();
	}

	private void stepValidateDetallesCuenta() throws Exception {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo", "Estado Activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() throws Exception {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		assertVisibleText("Sección Legal", "Seccion Legal");
		final String finalUrl = validateLegalLink(
				"08-terminos-y-condiciones",
				new String[]{"Términos y Condiciones", "Terminos y Condiciones"},
				new String[]{"Términos y Condiciones", "Terminos y Condiciones"});
		legalUrls.put("Términos y Condiciones", finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		assertVisibleText("Sección Legal", "Seccion Legal");
		final String finalUrl = validateLegalLink(
				"09-politica-de-privacidad",
				new String[]{"Política de Privacidad", "Politica de Privacidad"},
				new String[]{"Política de Privacidad", "Politica de Privacidad"});
		legalUrls.put("Política de Privacidad", finalUrl);
	}

	private String validateLegalLink(final String screenshotName,
									 final String[] linkTexts,
									 final String[] headingTexts) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeWindows = driver.getWindowHandles();

		clickByVisibleText(linkTexts);
		waitForUiToLoad();

		final boolean switchedToNewTab = switchToNewTabIfOpened(beforeWindows);

		assertVisibleText(headingTexts);
		Assert.assertTrue("Legal content text is not visible.", isLegalContentVisible());
		final String finalUrl = driver.getCurrentUrl();
		takeScreenshot(screenshotName);
		System.out.println("Final URL [" + headingTexts[0] + "]: " + finalUrl);

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private boolean switchToNewTabIfOpened(final Set<String> beforeWindows) {
		final long timeoutMillis = SHORT_WAIT.toMillis();
		final long started = System.currentTimeMillis();

		while (System.currentTimeMillis() - started < timeoutMillis) {
			final Set<String> current = driver.getWindowHandles();
			if (current.size() > beforeWindows.size()) {
				for (final String handle : current) {
					if (!beforeWindows.contains(handle)) {
						driver.switchTo().window(handle);
						waitForUiToLoad();
						return true;
					}
				}
			}
			sleep(300);
		}
		return false;
	}

	private void selectGoogleAccountIfPresent(final String email) {
		if (email == null || email.isBlank()) {
			return;
		}

		if (isVisibleTextPresent(Duration.ofSeconds(8), email)) {
			clickByVisibleText(email);
			waitForUiToLoad();
		}
	}

	private void runStep(final String name, final StepAction stepAction) {
		try {
			stepAction.run();
			report.put(name, new StepResult(StepStatus.PASS, "Validated successfully."));
		} catch (final Throwable t) {
			final String message = Objects.toString(t.getMessage(), t.getClass().getSimpleName());
			report.put(name, new StepResult(StepStatus.FAIL, message));
			try {
				takeScreenshot("failed-" + sanitizeFilename(name));
			} catch (final Exception ignored) {
				// best-effort evidence capture for failure diagnostics
			}
		}
	}

	private void printFinalReport() {
		System.out.println("======= SALEADS MI NEGOCIO WORKFLOW REPORT =======");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue().status +
					" | " + entry.getValue().details);
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("------- Legal URLs -------");
			legalUrls.forEach((key, value) -> System.out.println(key + ": " + value));
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("==================================================");
	}

	private WebDriver createDriver() throws MalformedURLException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		final String chromeBinary = readConfig("saleads.chromeBinary", "CHROME_BINARY");
		if (chromeBinary != null && !chromeBinary.isBlank()) {
			options.setBinary(chromeBinary);
		}

		final String userDataDir = readConfig("saleads.chromeUserDataDir", "SALEADS_CHROME_USER_DATA_DIR");
		if (userDataDir != null && !userDataDir.isBlank()) {
			options.addArguments("--user-data-dir=" + userDataDir);
		}

		final String remoteUrl = readConfig("selenium.remoteUrl", "SELENIUM_REMOTE_URL");
		if (remoteUrl != null && !remoteUrl.isBlank()) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}

		WebDriverManager.chromedriver().setup();
		return new ChromeDriver(options);
	}

	private void clickByVisibleText(final String... labels) {
		final WebElement element = waitForVisibleTextElement(DEFAULT_WAIT, labels);
		scrollIntoView(element);
		element.click();
		waitForUiToLoad();
	}

	private void assertVisibleText(final String... labels) {
		if (!isVisibleTextPresent(DEFAULT_WAIT, labels)) {
			throw new AssertionError("Expected text not visible: " + String.join(" | ", labels));
		}
	}

	private boolean isVisibleTextPresent(final Duration timeout, final String... labels) {
		try {
			return waitForVisibleTextElement(timeout, labels) != null;
		} catch (final RuntimeException e) {
			return false;
		}
	}

	private WebElement waitForVisibleTextElement(final Duration timeout, final String... labels) {
		final long timeoutMillis = timeout.toMillis();
		final long started = System.currentTimeMillis();
		Throwable lastError = null;

		while (System.currentTimeMillis() - started < timeoutMillis) {
			for (final String label : labels) {
				try {
					final List<WebElement> elements = new ArrayList<>(driver.findElements(
							By.xpath("//*[contains(normalize-space(.), " + quote(label) + ")]")));
					for (final WebElement element : elements) {
						if (element.isDisplayed()) {
							return element;
						}
					}
				} catch (final Throwable t) {
					lastError = t;
				}
			}

			final WebElement relaxedMatch = findNormalizedTextMatch(labels);
			if (relaxedMatch != null) {
				return relaxedMatch;
			}
			sleep(250);
		}

		throw new RuntimeException("Could not find visible text: " + String.join(" | ", labels), lastError);
	}

	private WebElement findNormalizedTextMatch(final String... labels) {
		final String[] normalizedNeedles = new String[labels.length];
		for (int i = 0; i < labels.length; i++) {
			normalizedNeedles[i] = normalize(labels[i]);
		}

		final List<WebElement> scanTargets = driver.findElements(By.xpath("//a|//button|//span|//div|//li|//p|//h1|//h2|//h3|//h4|//label"));
		for (final WebElement element : scanTargets) {
			try {
				if (!element.isDisplayed()) {
					continue;
				}
				final String text = normalize(element.getText());
				for (final String needle : normalizedNeedles) {
					if (!needle.isBlank() && text.contains(needle)) {
						return element;
					}
				}
			} catch (final StaleElementReferenceException ignored) {
				// keep scanning while DOM settles
			}
		}
		return null;
	}

	private WebElement findBusinessNameInput() {
		final List<By> selectors = List.of(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"));

		for (final By selector : selectors) {
			try {
				final List<WebElement> matches = driver.findElements(selector);
				for (final WebElement match : matches) {
					if (match.isDisplayed()) {
						return match;
					}
				}
			} catch (final NoSuchElementException ignored) {
				// continue scanning alternatives
			}
		}
		return null;
	}

	private void assertSidebarVisible() {
		final boolean sidebarVisible = isDisplayed(By.tagName("aside"))
				|| isDisplayed(By.xpath("//nav"))
				|| isDisplayed(By.xpath("//*[contains(@class, 'sidebar') or contains(@id, 'sidebar')]"));
		Assert.assertTrue("Left sidebar navigation is not visible.", sidebarVisible);
	}

	private void assertVisibleEmailPresent() {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[contains(text(), '@')]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed() && element.getText().contains("@")) {
				return;
			}
		}
		throw new AssertionError("No visible user email was found in 'Información General'.");
	}

	private void assertVisibleUserNamePresent() {
		final String configuredName = readConfig("saleads.expectedUserName", "SALEADS_EXPECTED_USER_NAME");
		if (configuredName != null && !configuredName.isBlank()) {
			assertVisibleText(configuredName);
			return;
		}

		final List<WebElement> candidates = driver.findElements(By.xpath("//h1|//h2|//h3|//span|//p"));
		for (final WebElement candidate : candidates) {
			final String text = candidate.getText();
			if (candidate.isDisplayed() && text != null && text.trim().length() >= 4 && !text.contains("@")
					&& !normalize(text).contains("business plan")) {
				return;
			}
		}

		throw new AssertionError("No visible user name was found in 'Información General'.");
	}

	private boolean isLegalContentVisible() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p|//article|//section|//div"));
		for (final WebElement paragraph : paragraphs) {
			try {
				final String text = paragraph.getText();
				if (paragraph.isDisplayed() && text != null && normalize(text).length() > 120) {
					return true;
				}
			} catch (final StaleElementReferenceException ignored) {
				// keep looking while page settles
			}
		}
		return false;
	}

	private boolean isDisplayed(final By selector) {
		try {
			final List<WebElement> matches = driver.findElements(selector);
			for (final WebElement match : matches) {
				if (match.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final RuntimeException e) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> {
				final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(Objects.toString(readyState, ""));
			});
		} catch (final RuntimeException ignored) {
			// SPA routes may not always impact readyState; continue with a short settle delay
		}
		sleep(500);
	}

	private void takeScreenshot(final String checkpoint) throws Exception {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(checkpoint + ".png");
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Screenshot captured: " + target.toAbsolutePath());
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({behavior:'instant', block:'center', inline:'center'});", element);
	}

	private String quote(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	private String normalize(final String input) {
		if (input == null) {
			return "";
		}
		final String withoutDiacritics = Normalizer.normalize(input, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		return withoutDiacritics.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
	}

	private String sanitizeFilename(final String raw) {
		return normalize(raw).replace(' ', '-').replaceAll("[^a-z0-9-]", "");
	}

	private String readConfig(final String propertyName, final String envName) {
		return readConfig(propertyName, envName, null);
	}

	private String readConfig(final String propertyName, final String envName, final String fallback) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		return fallback;
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private enum StepStatus {
		PASS,
		FAIL
	}

	private static final class StepResult {
		private final StepStatus status;
		private final String details;

		private StepResult(final StepStatus status, final String details) {
			this.status = status;
			this.details = details;
		}
	}
}
