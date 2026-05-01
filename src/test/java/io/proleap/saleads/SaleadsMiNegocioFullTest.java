package io.proleap.saleads;

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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration UI_WAIT = Duration.ofSeconds(30);
	private static final Duration CLICK_WAIT = Duration.ofSeconds(8);
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Path screenshotsDir;
	private Path reportPath;
	private final Map<String, String> stepResults = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		evidenceDir = Paths.get("target", "saleads", "mi-negocio");
		screenshotsDir = evidenceDir.resolve("screenshots");
		reportPath = evidenceDir.resolve("report.txt");
		Files.createDirectories(screenshotsDir);

		driver = createDriver();
		wait = new WebDriverWait(driver, UI_WAIT);
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioModuleWorkflow() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminos);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		writeReport();
		assertTrue("One or more required validations failed. See target/saleads/mi-negocio/report.txt\n"
				+ String.join("\n", failures), failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		final String targetUrl = readConfig("saleads.url", "SALEADS_URL");
		if (targetUrl == null || targetUrl.isBlank()) {
			throw new IllegalStateException("Missing SaleADS URL. Set -Dsaleads.url or SALEADS_URL.");
		}

		driver.get(targetUrl);
		waitForUiLoad();

		if (!isSidebarVisible()) {
			clickAnyVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google",
					"Iniciar sesion con Google", "Continuar con Google", "Acceder con Google", "Google"));
			waitForUiLoad();

			clickIfVisibleText("juanlucasbarbiergarzon@gmail.com");
			waitForUiLoad();
		}

		assertTrue("Main application interface did not load after login.", isMainInterfaceVisible());
		assertTrue("Left sidebar navigation is not visible after login.", isSidebarVisible());
		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		assertTrue("Left sidebar navigation is not visible.", isSidebarVisible());

		clickIfVisibleText("Negocio");
		clickAnyVisibleText(Arrays.asList("Mi Negocio", "Mi negocio"));
		waitForUiLoad();

		assertTrue("'Agregar Negocio' is not visible under Mi Negocio.",
				isAnyVisibleText(Arrays.asList("Agregar Negocio")));
		assertTrue("'Administrar Negocios' is not visible under Mi Negocio.",
				isAnyVisibleText(Arrays.asList("Administrar Negocios")));
		takeScreenshot("02_mi_negocio_expanded_menu");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickAnyVisibleText(Arrays.asList("Agregar Negocio"));
		waitForUiLoad();

		assertTrue("Modal title 'Crear Nuevo Negocio' is not visible.",
				isAnyVisibleText(Arrays.asList("Crear Nuevo Negocio")));
		assertTrue("Input field label 'Nombre del Negocio' is not visible.",
				isAnyVisibleText(Arrays.asList("Nombre del Negocio")));
		assertTrue("Text 'Tienes 2 de 3 negocios' is not visible.",
				isAnyVisibleText(Arrays.asList("Tienes 2 de 3 negocios")));
		assertTrue("Button 'Cancelar' is not visible.",
				isAnyVisibleText(Arrays.asList("Cancelar")));
		assertTrue("Button 'Crear Negocio' is not visible.",
				isAnyVisibleText(Arrays.asList("Crear Negocio")));

		takeScreenshot("03_agregar_negocio_modal");

		typeIfInputVisibleNearLabel("Nombre del Negocio", "Negocio Prueba Automatización");
		clickIfVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		clickIfVisibleText("Mi Negocio");
		clickIfVisibleText("Mi negocio");
		clickAnyVisibleText(Arrays.asList("Administrar Negocios"));
		waitForUiLoad();

		assertTrue("Section 'Información General' was not found.",
				isAnyVisibleText(Arrays.asList("Información General", "Informacion General")));
		assertTrue("Section 'Detalles de la Cuenta' was not found.",
				isAnyVisibleText(Arrays.asList("Detalles de la Cuenta", "Detalles de la cuenta")));
		assertTrue("Section 'Tus Negocios' was not found.",
				isAnyVisibleText(Arrays.asList("Tus Negocios")));
		assertTrue("Section 'Sección Legal' was not found.",
				isAnyVisibleText(Arrays.asList("Sección Legal", "Seccion Legal")));
		takeScreenshot("04_administrar_negocios_page");
	}

	private void stepValidateInformacionGeneral() {
		final String sectionText = sectionTextByTitle(Arrays.asList("Información General", "Informacion General"));
		assertTrue("Could not detect user name in 'Información General'.",
				containsAnyIgnoreCase(sectionText, Arrays.asList("nombre", "usuario", "name")));
		assertTrue("Could not detect user email in 'Información General'.",
				EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("Text 'BUSINESS PLAN' is not visible in 'Información General'.",
				containsAnyIgnoreCase(sectionText, Arrays.asList("BUSINESS PLAN")));
		assertTrue("Button/text 'Cambiar Plan' is not visible in 'Información General'.",
				containsAnyIgnoreCase(sectionText, Arrays.asList("Cambiar Plan")));
	}

	private void stepValidateDetallesCuenta() {
		final String sectionText = sectionTextByTitle(
				Arrays.asList("Detalles de la Cuenta", "Detalles de la cuenta"));
		assertTrue("'Cuenta creada' is not visible in account details.",
				containsAnyIgnoreCase(sectionText, Arrays.asList("Cuenta creada")));
		assertTrue("'Estado activo' is not visible in account details.",
				containsAnyIgnoreCase(sectionText,
						Arrays.asList("Estado activo", "Estado: activo", "Estado : activo")));
		assertTrue("'Idioma seleccionado' is not visible in account details.",
				containsAnyIgnoreCase(sectionText, Arrays.asList("Idioma seleccionado")));
	}

	private void stepValidateTusNegocios() {
		final String sectionText = sectionTextByTitle(Arrays.asList("Tus Negocios"));
		assertTrue("Business list area under 'Tus Negocios' is not visible.",
				sectionText != null && !sectionText.isBlank() && sectionText.split("\\R").length >= 2);
		assertTrue("Button 'Agregar Negocio' is not visible in 'Tus Negocios'.",
				containsAnyIgnoreCase(sectionText, Arrays.asList("Agregar Negocio")));
		assertTrue("Text 'Tienes 2 de 3 negocios' is not visible in 'Tus Negocios'.",
				containsAnyIgnoreCase(sectionText, Arrays.asList("Tienes 2 de 3 negocios")));
	}

	private void stepValidateTerminos() throws Exception {
		final String url = validateLegalLink("Términos y Condiciones", "Terminos y Condiciones",
				Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"), "08_terminos");
		legalUrls.put("Términos y Condiciones URL", url);
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		final String url = validateLegalLink("Política de Privacidad", "Politica de Privacidad",
				Arrays.asList("Política de Privacidad", "Politica de Privacidad"), "09_politica_privacidad");
		legalUrls.put("Política de Privacidad URL", url);
	}

	private String validateLegalLink(final String preferredText, final String fallbackText,
			final List<String> headingTexts, final String screenshotPrefix) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());

		if (!clickIfVisibleText(preferredText)) {
			clickAnyVisibleText(Arrays.asList(fallbackText));
		}
		waitForUiLoad();

		String activeWindow = appWindow;
		final Set<String> afterHandles = wait.until(newWindowOrSameWindowNavigation(beforeHandles, appWindow));
		if (afterHandles.size() > beforeHandles.size()) {
			for (String handle : afterHandles) {
				if (!beforeHandles.contains(handle)) {
					activeWindow = handle;
					break;
				}
			}
			driver.switchTo().window(activeWindow);
			waitForUiLoad();
		}

		assertTrue("Legal heading was not found after opening link: " + preferredText, isAnyVisibleText(headingTexts));

		final String legalPageText = normalizedPageText();
		assertTrue("Legal content text appears missing or too short for link: " + preferredText,
				legalPageText.length() > 200);

		takeScreenshot(screenshotPrefix);
		final String url = driver.getCurrentUrl();

		if (!activeWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		return url;
	}

	private void runStep(final String stepName, final CheckedRunnable step) {
		try {
			step.run();
			stepResults.put(stepName, "PASS");
		} catch (Throwable throwable) {
			stepResults.put(stepName, "FAIL");
			failures.add(stepName + ": " + throwable.getMessage());
			try {
				takeScreenshot("fail_" + slugify(stepName));
			} catch (Exception ignored) {
				// best-effort evidence capture
			}
		}
	}

	private WebDriver createDriver() {
		final String remoteUrl = readConfig("selenium.remote.url", "SELENIUM_REMOTE_URL");
		final boolean headless = Boolean.parseBoolean(readConfigOrDefault("saleads.headless", "SALEADS_HEADLESS",
				"true"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		if (remoteUrl != null && !remoteUrl.isBlank()) {
			try {
				return new RemoteWebDriver(java.net.URI.create(remoteUrl).toURL(), options);
			} catch (Exception exception) {
				throw new IllegalArgumentException("Invalid selenium remote URL: " + remoteUrl, exception);
			}
		}

		WebDriverManager.chromedriver().setup();
		return new ChromeDriver(options);
	}

	private void clickAnyVisibleText(final List<String> candidateTexts) {
		for (String text : candidateTexts) {
			if (clickIfVisibleText(text)) {
				return;
			}
		}
		throw new NoSuchElementException("None of the visible text candidates were clickable: " + candidateTexts);
	}

	private boolean clickIfVisibleText(final String text) {
		if (text == null || text.isBlank()) {
			return false;
		}

		for (By locator : candidateClickLocators(text)) {
			final Optional<WebElement> element = waitVisible(locator, CLICK_WAIT);
			if (element.isPresent()) {
				scrollTo(element.get());
				try {
					element.get().click();
				} catch (Exception clickException) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", element.get());
				}
				waitForUiLoad();
				return true;
			}
		}
		return false;
	}

	private List<By> candidateClickLocators(final String text) {
		final String exact = xpathLiteral(text);
		return Arrays.asList(
				By.xpath("//button[normalize-space()=" + exact + "]"),
				By.xpath("//a[normalize-space()=" + exact + "]"),
				By.xpath("//*[(@role='button' or @role='menuitem' or @role='link') and normalize-space()=" + exact + "]"),
				By.xpath("//*[normalize-space()=" + exact + "]"),
				By.xpath("//button[contains(normalize-space(), " + exact + ")]"),
				By.xpath("//a[contains(normalize-space(), " + exact + ")]"),
				By.xpath("//*[contains(normalize-space(), " + exact + ")]"));
	}

	private boolean isAnyVisibleText(final List<String> candidateTexts) {
		for (String text : candidateTexts) {
			if (isVisibleText(text)) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisibleText(final String text) {
		for (By locator : Arrays.asList(By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]"),
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]"))) {
			if (waitVisible(locator, Duration.ofSeconds(3)).isPresent()) {
				return true;
			}
		}
		return false;
	}

	private Optional<WebElement> waitVisible(final By locator, final Duration timeout) {
		try {
			final WebDriverWait localWait = new WebDriverWait(driver, timeout);
			return Optional.of(localWait.until(d -> {
				for (WebElement element : d.findElements(locator)) {
					if (element.isDisplayed()) {
						return element;
					}
				}
				return null;
			}));
		} catch (TimeoutException timeoutException) {
			return Optional.empty();
		}
	}

	private void typeIfInputVisibleNearLabel(final String labelText, final String value) {
		final String labelXpath = "//*[normalize-space()=" + xpathLiteral(labelText) + "]";
		final List<By> inputLocators = Arrays.asList(
				By.xpath(labelXpath + "/following::input[1]"),
				By.xpath(labelXpath + "/ancestor::*[1]//input[1]"),
				By.xpath("//input[@placeholder=" + xpathLiteral(labelText) + "]"));

		for (By locator : inputLocators) {
			Optional<WebElement> input = waitVisible(locator, Duration.ofSeconds(3));
			if (input.isPresent()) {
				input.get().clear();
				input.get().sendKeys(value);
				waitForUiLoad();
				return;
			}
		}
	}

	private String sectionTextByTitle(final List<String> titles) {
		for (String title : titles) {
			final String titleLiteral = xpathLiteral(title);
			final List<By> candidateLocators = Arrays.asList(
					By.xpath("//*[normalize-space()=" + titleLiteral + "]/ancestor::section[1]"),
					By.xpath("//*[normalize-space()=" + titleLiteral + "]/ancestor::div[1]"),
					By.xpath("//*[normalize-space()=" + titleLiteral + "]"));

			for (By locator : candidateLocators) {
				Optional<WebElement> section = waitVisible(locator, Duration.ofSeconds(4));
				if (section.isPresent()) {
					return normalizeText(section.get().getText());
				}
			}
		}
		return normalizeText(normalizedPageText());
	}

	private boolean isSidebarVisible() {
		final List<By> sidebarSignals = Arrays.asList(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class, 'sidebar')]"),
				By.xpath("//*[contains(@class, 'menu')]"));
		for (By by : sidebarSignals) {
			if (waitVisible(by, Duration.ofSeconds(3)).isPresent()) {
				return true;
			}
		}
		return isAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio", "Mi negocio"));
	}

	private boolean isMainInterfaceVisible() {
		return isSidebarVisible() || isAnyVisibleText(Arrays.asList("Dashboard", "Inicio", "Negocio", "Mi Negocio"));
	}

	private void waitForUiLoad() {
		wait.until((ExpectedCondition<Boolean>) drv -> {
			Object readyState = ((JavascriptExecutor) drv).executeScript("return document.readyState");
			return "complete".equals(String.valueOf(readyState)) || "interactive".equals(String.valueOf(readyState));
		});
		try {
			Thread.sleep(600);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private ExpectedCondition<Set<String>> newWindowOrSameWindowNavigation(final Set<String> beforeHandles,
			final String currentWindow) {
		final String urlBefore = driver.getCurrentUrl();
		return drv -> {
			Set<String> currentHandles = drv.getWindowHandles();
			if (currentHandles.size() > beforeHandles.size()) {
				return currentHandles;
			}
			drv.switchTo().window(currentWindow);
			if (!drv.getCurrentUrl().equals(urlBefore)) {
				return currentHandles;
			}
			return null;
		};
	}

	private void takeScreenshot(final String checkpoint) throws IOException {
		final String fileName = timestamp() + "_" + slugify(checkpoint) + ".png";
		final Path target = screenshotsDir.resolve(fileName);
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeReport() throws IOException {
		Files.createDirectories(evidenceDir);
		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("generated_at=" + LocalDateTime.now());
		lines.add("");
		lines.add("Validation Results:");
		lines.add("- Login: " + stepResults.getOrDefault("Login", "FAIL"));
		lines.add("- Mi Negocio menu: " + stepResults.getOrDefault("Mi Negocio menu", "FAIL"));
		lines.add("- Agregar Negocio modal: " + stepResults.getOrDefault("Agregar Negocio modal", "FAIL"));
		lines.add("- Administrar Negocios view: " + stepResults.getOrDefault("Administrar Negocios view", "FAIL"));
		lines.add("- Información General: " + stepResults.getOrDefault("Información General", "FAIL"));
		lines.add("- Detalles de la Cuenta: " + stepResults.getOrDefault("Detalles de la Cuenta", "FAIL"));
		lines.add("- Tus Negocios: " + stepResults.getOrDefault("Tus Negocios", "FAIL"));
		lines.add("- Términos y Condiciones: " + stepResults.getOrDefault("Términos y Condiciones", "FAIL"));
		lines.add("- Política de Privacidad: " + stepResults.getOrDefault("Política de Privacidad", "FAIL"));
		lines.add("");
		for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
			lines.add(entry.getKey() + "=" + entry.getValue());
		}
		lines.add("");
		lines.add("screenshots_dir=" + screenshotsDir.toAbsolutePath());
		if (!failures.isEmpty()) {
			lines.add("");
			lines.add("Failures:");
			lines.addAll(failures);
		}
		Files.write(reportPath, lines);
	}

	private String normalizedPageText() {
		return normalizeText(driver.findElement(By.tagName("body")).getText());
	}

	private boolean containsAnyIgnoreCase(final String haystack, final List<String> needles) {
		if (haystack == null) {
			return false;
		}
		final String normalizedHaystack = haystack.toLowerCase(Locale.ROOT);
		for (String needle : needles) {
			if (normalizedHaystack.contains(needle.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private void scrollTo(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private String readConfig(final String propertyName, final String envName) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		final String envValue = System.getenv(envName);
		return envValue == null || envValue.isBlank() ? null : envValue;
	}

	private String readConfigOrDefault(final String propertyName, final String envName, final String defaultValue) {
		final String configuredValue = readConfig(propertyName, envName);
		return configuredValue == null ? defaultValue : configuredValue;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder xpath = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				xpath.append(", \"'\", ");
			}
			xpath.append("'").append(parts[i]).append("'");
		}
		xpath.append(")");
		return xpath.toString();
	}

	private String normalizeText(final String input) {
		return input == null ? "" : input.replace('\u00A0', ' ').trim();
	}

	private String timestamp() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
	}

	private String slugify(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
