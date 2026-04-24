package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final String GOOGLE_TEST_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue("Enable with -Dsaleads.e2e.enabled=true to run this live E2E test.", enabled);

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDir = createEvidenceDirectory();

		final String configuredUrl = firstNonBlank(System.getProperty("saleads.url"), System.getenv("SALEADS_URL"));
		if (configuredUrl != null) {
			driver.get(configuredUrl);
		}

		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		if (!stepStatus.isEmpty()) {
			printAndPersistFinalReport();
		}

		if (driver != null) {
			driver.quit();
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
		runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad"));

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, String> entry : stepStatus.entrySet()) {
			if ("FAIL".equals(entry.getValue())) {
				failedSteps.add(entry.getKey() + ": " + stepDetails.get(entry.getKey()));
			}
		}

		assertTrue("One or more validations failed.\n" + String.join("\n", failedSteps), failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		if (isBrowserOnBlankPage()) {
			throw new IllegalStateException(
					"Browser is not on SaleADS login page. Provide -Dsaleads.url=<login-page> or preload the page.");
		}

		clickByText(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Continuar con Google",
				"Ingresar con Google",
				"Login with Google");

		selectGoogleAccountIfVisible(GOOGLE_TEST_ACCOUNT);
		waitForUiToLoad();

		assertTrue("Main application interface should appear after login.", isAnyVisible(By.xpath("//main | //div[@id='root']")));
		assertTrue("Left sidebar navigation should be visible.", hasSidebarNavigation());
		captureScreenshot("dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByText("Negocio");
		waitForUiToLoad();
		clickByText("Mi Negocio");
		waitForUiToLoad();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByText("Agregar Negocio");
		waitForUiToLoad();

		assertVisibleText("Crear Nuevo Negocio");
		assertTrue("Input field 'Nombre del Negocio' should exist.", hasBusinessNameInput());
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		captureScreenshot("agregar_negocio_modal");

		fillBusinessNameField("Negocio Prueba Automatización");
		clickByText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioIfCollapsed();
		clickByText("Administrar Negocios");
		waitForUiToLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		captureScreenshot("administrar_negocios_account_page");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("Información General");
		assertTrue("User name should be visible.", hasLikelyUserName());
		assertTrue("User email should be visible.", hasVisibleEmail());
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertTrue("Business list should be visible.", hasLikelyBusinessList());
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(final String linkText) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByText(linkText);
		waitForUiToLoad();

		final String newWindowHandle = waitForNewTabIfAny(windowsBefore);
		final boolean switchedToNewTab = newWindowHandle != null;
		if (switchedToNewTab) {
			driver.switchTo().window(newWindowHandle);
			waitForUiToLoad();
		}

		assertVisibleText(linkText);
		assertTrue("Legal content text should be visible.", hasLegalContentText());

		captureScreenshot(slug(linkText) + "_page");
		capturedUrls.put(linkText, driver.getCurrentUrl());

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void runStep(final String stepName, final CheckedRunnable stepAction) {
		try {
			stepAction.run();
			stepStatus.put(stepName, "PASS");
			stepDetails.put(stepName, "Validated successfully.");
		} catch (final Throwable t) {
			stepStatus.put(stepName, "FAIL");
			stepDetails.put(stepName, nonEmpty(t.getMessage(), t.getClass().getSimpleName()));
		}
	}

	private WebDriver createDriver() {
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		final String remoteUrl = firstNonBlank(System.getProperty("selenium.remote.url"), System.getenv("SELENIUM_REMOTE_URL"));
		if (remoteUrl != null) {
			try {
				return new RemoteWebDriver(new URL(remoteUrl), options);
			} catch (final MalformedURLException e) {
				throw new IllegalArgumentException("Invalid Selenium remote URL: " + remoteUrl, e);
			}
		}

		return new ChromeDriver(options);
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
		final Path baseDir = Paths.get("target", "saleads-evidence", timestamp);
		return Files.createDirectories(baseDir);
	}

	private void printAndPersistFinalReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test - Final Report").append(System.lineSeparator());
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		sb.append(System.lineSeparator());

		for (final Map.Entry<String, String> entry : stepStatus.entrySet()) {
			sb.append(entry.getKey()).append(": ").append(entry.getValue());
			final String details = stepDetails.get(entry.getKey());
			if (details != null && !details.isBlank()) {
				sb.append(" - ").append(details);
			}
			sb.append(System.lineSeparator());
		}

		if (!capturedUrls.isEmpty()) {
			sb.append(System.lineSeparator()).append("Captured URLs:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : capturedUrls.entrySet()) {
				sb.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		final String report = sb.toString();
		System.out.println(report);
		Files.writeString(evidenceDir.resolve("final_report.txt"), report);
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final Set<String> allHandles = new LinkedHashSet<>(driver.getWindowHandles());
		for (final String handle : allHandles) {
			driver.switchTo().window(handle);
			if (clickIfVisible(By.xpath(
					"(//*[normalize-space()=" + toXPathLiteral(email) + " or contains(normalize-space(), " + toXPathLiteral(email)
							+ ")])[1]"))) {
				waitForUiToLoad();
				return;
			}
		}
	}

	private void expandMiNegocioIfCollapsed() {
		if (!isTextVisible("Administrar Negocios") || !isTextVisible("Agregar Negocio")) {
			if (isTextVisible("Negocio")) {
				clickByText("Negocio");
			}
			clickByText("Mi Negocio");
			waitForUiToLoad();
		}
	}

	private boolean hasSidebarNavigation() {
		if (!isAnyVisible(By.xpath("//aside | //nav"))) {
			return false;
		}
		return isTextVisible("Negocio") || isTextVisible("Mi Negocio");
	}

	private boolean hasBusinessNameInput() {
		return isAnyVisible(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio']"))
				|| isAnyVisible(By.xpath(
						"//*[contains(normalize-space(),'Nombre del Negocio')]/following::*[self::input or self::textarea][1]"));
	}

	private void fillBusinessNameField(final String value) {
		final List<By> locators = List.of(
				By.xpath("//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio']"),
				By.xpath("//*[contains(normalize-space(),'Nombre del Negocio')]/following::*[self::input or self::textarea][1]"));

		for (final By locator : locators) {
			try {
				final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				input.clear();
				input.sendKeys(value);
				return;
			} catch (final TimeoutException ignored) {
				// Try next fallback locator.
			}
		}

		throw new AssertionError("Could not locate 'Nombre del Negocio' input.");
	}

	private boolean hasLikelyUserName() {
		final List<By> locators = List.of(
				By.xpath("//*[contains(normalize-space(),'Nombre')]/following::*[normalize-space()!=''][1]"),
				By.xpath("//*[contains(normalize-space(),'Usuario')]/following::*[normalize-space()!=''][1]"),
				By.xpath("//h1[normalize-space()!=''] | //h2[normalize-space()!='']"));
		for (final By locator : locators) {
			if (isAnyVisible(locator)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasVisibleEmail() {
		if (!isAnyVisible(By.xpath("//*[contains(text(),'@')]"))) {
			return false;
		}

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		return Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(bodyText).find();
	}

	private boolean hasLikelyBusinessList() {
		final List<By> listLocators = List.of(
				By.xpath("//*[contains(normalize-space(),'Tus Negocios')]/following::*[self::ul or self::table or @role='list'][1]"),
				By.xpath("//ul/li[normalize-space()!='']"),
				By.xpath("//table//tr"));

		for (final By locator : listLocators) {
			if (isAnyVisible(locator)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasLegalContentText() {
		final List<By> contentLocators = List.of(
				By.xpath("//article//*[string-length(normalize-space()) > 80]"),
				By.xpath("//main//*[string-length(normalize-space()) > 80]"),
				By.xpath("//p[string-length(normalize-space()) > 80]"));
		for (final By locator : contentLocators) {
			if (isAnyVisible(locator)) {
				return true;
			}
		}
		return false;
	}

	private String waitForNewTabIfAny(final Set<String> windowsBefore) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20))
					.until(d -> d.getWindowHandles().size() > windowsBefore.size() || !d.getCurrentUrl().isBlank());
		} catch (final TimeoutException ignored) {
			// Continue and evaluate current handles.
		}

		final Set<String> windowsAfter = new LinkedHashSet<>(driver.getWindowHandles());
		windowsAfter.removeAll(windowsBefore);
		return windowsAfter.isEmpty() ? null : windowsAfter.iterator().next();
	}

	private void clickByText(final String... candidateTexts) {
		for (final String text : candidateTexts) {
			if (clickByTextCandidate(text)) {
				return;
			}
		}
		throw new AssertionError("Could not click any visible element with text: " + String.join(", ", candidateTexts));
	}

	private boolean clickByTextCandidate(final String text) {
		final List<By> clickLocators = List.of(
				By.xpath("(//*[self::button or self::a or @role='button'][normalize-space()=" + toXPathLiteral(text)
						+ " or contains(normalize-space(), " + toXPathLiteral(text) + ")])[1]"),
				By.xpath("(//*[normalize-space()=" + toXPathLiteral(text) + " or contains(normalize-space(), " + toXPathLiteral(text)
						+ ")]/ancestor-or-self::*[self::button or self::a or @role='button'][1])[1]"),
				By.xpath("(//*[normalize-space()=" + toXPathLiteral(text) + " or contains(normalize-space(), " + toXPathLiteral(text)
						+ ")])[1]"));

		for (final By locator : clickLocators) {
			try {
				final WebElement clickable = new WebDriverWait(driver, Duration.ofSeconds(8))
						.until(ExpectedConditions.elementToBeClickable(locator));
				safeClick(clickable);
				waitForUiToLoad();
				return true;
			} catch (final TimeoutException ignored) {
				// Try next locator.
			}
		}

		return false;
	}

	private void safeClick(final WebElement element) {
		try {
			scrollIntoView(element);
			element.click();
		} catch (final RuntimeException ex) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'center'});", element);
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(600);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void assertVisibleText(final String text) {
		final By locator = By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + " or contains(normalize-space(), "
				+ toXPathLiteral(text) + ")]");
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final TimeoutException ex) {
			throw new AssertionError("Expected visible text not found: " + text, ex);
		}
	}

	private boolean isTextVisible(final String text) {
		final By locator = By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + " or contains(normalize-space(), "
				+ toXPathLiteral(text) + ")]");
		return isAnyVisible(locator);
	}

	private boolean isAnyVisible(final By locator) {
		try {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				try {
					if (element.isDisplayed()) {
						return true;
					}
				} catch (final StaleElementReferenceException ignored) {
					// Retry with next element.
				}
			}
			return false;
		} catch (final RuntimeException e) {
			return false;
		}
	}

	private boolean clickIfVisible(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			try {
				if (element.isDisplayed()) {
					safeClick(element);
					return true;
				}
			} catch (final StaleElementReferenceException ignored) {
				// Try next candidate.
			}
		}
		return false;
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String timestamp = DateTimeFormatter.ofPattern("HHmmss_SSS").format(LocalDateTime.now());
		final String fileName = timestamp + "_" + slug(checkpointName) + ".png";
		Files.copy(screenshot.toPath(), evidenceDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private String firstNonBlank(final String first, final String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		if (second != null && !second.isBlank()) {
			return second;
		}
		return null;
	}

	private String nonEmpty(final String value, final String fallback) {
		return (value == null || value.isBlank()) ? fallback : value;
	}

	private String slug(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part = chars[i] == '\'' ? "\"'\"" : "'" + chars[i] + "'";
			if (i > 0) {
				sb.append(",");
			}
			sb.append(part);
		}
		sb.append(")");
		return sb.toString();
	}

	private boolean isBrowserOnBlankPage() {
		final String currentUrl = driver.getCurrentUrl();
		return currentUrl == null || currentUrl.isBlank() || "about:blank".equalsIgnoreCase(currentUrl)
				|| "data:,".equalsIgnoreCase(currentUrl);
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
