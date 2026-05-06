package io.proleap.saleads.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String ENABLED_ENV = "SALEADS_E2E_ENABLED";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String BUSINESS_QUOTA_TEXT = "Tienes 2 de 3 negocios";

	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	private final AtomicInteger screenshotCounter = new AtomicInteger(1);
	private final Map<String, String> results = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		for (final String field : REPORT_FIELDS) {
			results.put(field, "NOT RUN");
		}

		final boolean runE2e = Boolean.parseBoolean(System.getenv().getOrDefault(ENABLED_ENV, "false"));
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to execute this live SaleADS workflow.", runE2e);

		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		artifactsDir = Path.of("target", "saleads-mi-negocio", timestamp);
		Files.createDirectories(artifactsDir);

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(System.getenv().getOrDefault(HEADLESS_ENV, "true"))) {
			options.addArguments("--headless=new");
		}

		options.addArguments(
				"--window-size=1920,1080",
				"--disable-gpu",
				"--no-sandbox",
				"--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String loginUrl = System.getenv(LOGIN_URL_ENV);
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() throws IOException {
		writeReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		boolean continueFlow = true;
		continueFlow = executeStep("Login", continueFlow, this::stepLoginWithGoogle);
		continueFlow = executeStep("Mi Negocio menu", continueFlow, this::stepOpenMiNegocioMenu);
		continueFlow = executeStep("Agregar Negocio modal", continueFlow, this::stepValidateAgregarNegocioModal);
		continueFlow = executeStep("Administrar Negocios view", continueFlow, this::stepOpenAdministrarNegocios);
		continueFlow = executeStep("Información General", continueFlow, this::stepValidateInformacionGeneral);
		continueFlow = executeStep("Detalles de la Cuenta", continueFlow, this::stepValidateDetallesCuenta);
		continueFlow = executeStep("Tus Negocios", continueFlow, this::stepValidateTusNegocios);
		continueFlow = executeStep("Términos y Condiciones", continueFlow, () -> stepOpenLegalPage(
				Arrays.asList("T\u00e9rminos y Condiciones", "Terminos y Condiciones"),
				Arrays.asList("T\u00e9rminos y Condiciones", "Terminos y Condiciones"),
				"Términos y Condiciones",
				"08-terminos-y-condiciones"));
		continueFlow = executeStep("Política de Privacidad", continueFlow, () -> stepOpenLegalPage(
				Arrays.asList("Pol\u00edtica de Privacidad", "Politica de Privacidad"),
				Arrays.asList("Pol\u00edtica de Privacidad", "Politica de Privacidad"),
				"Política de Privacidad",
				"09-politica-de-privacidad"));

		final List<String> failingSteps = new ArrayList<>();
		for (final String reportField : REPORT_FIELDS) {
			final String status = results.getOrDefault(reportField, "NOT RUN");
			if (!status.startsWith("PASS")) {
				failingSteps.add(reportField + " -> " + status);
			}
		}

		if (!failingSteps.isEmpty()) {
			fail("One or more Mi Negocio workflow validations failed:\n" + String.join("\n", failingSteps));
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		final String currentUrl = driver.getCurrentUrl();
		final boolean loginPageAvailable = currentUrl != null && !currentUrl.isBlank()
				&& !"about:blank".equalsIgnoreCase(currentUrl);
		assertTrue(
				"Browser is not on a login page. Provide " + LOGIN_URL_ENV + " or pre-open the SaleADS login page.",
				loginPageAvailable);

		clickByVisibleText(Arrays.asList(
				"Sign in with Google",
				"Iniciar sesion con Google",
				"Iniciar sesi\u00f3n con Google",
				"Continuar con Google",
				"Login with Google"));

		selectGoogleAccountIfVisible();

		waitForAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio"), DEFAULT_TIMEOUT);
		assertTrue("The left sidebar navigation is not visible.", isAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio")));

		appWindowHandle = driver.getWindowHandle();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleTextIfVisible(Arrays.asList("Negocio"), SHORT_TIMEOUT);
		clickByVisibleText(Arrays.asList("Mi Negocio", "Mi Negocio"));

		waitForAnyVisibleText(Arrays.asList("Agregar Negocio"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Administrar Negocios"), DEFAULT_TIMEOUT);
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText(Arrays.asList("Agregar Negocio"));
		waitForAnyVisibleText(Arrays.asList("Crear Nuevo Negocio"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Nombre del Negocio"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList(BUSINESS_QUOTA_TEXT), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Cancelar"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Crear Negocio"), DEFAULT_TIMEOUT);

		final WebElement businessNameInput = findFirstVisibleElement(By.xpath(
				"//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1] | "
						+ "//input[contains(@placeholder, 'Nombre del Negocio')]"),
				DEFAULT_TIMEOUT);
		assertNotNull("Input field 'Nombre del Negocio' was not found.", businessNameInput);

		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		captureScreenshot("03-agregar-negocio-modal");

		clickByVisibleText(Arrays.asList("Cancelar"));
		wait.until((ExpectedCondition<Boolean>) d -> !isAnyTextVisible(Arrays.asList("Crear Nuevo Negocio")));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isAnyTextVisible(Arrays.asList("Administrar Negocios"))) {
			clickByVisibleText(Arrays.asList("Mi Negocio"));
		}

		clickByVisibleText(Arrays.asList("Administrar Negocios"));

		waitForAnyVisibleText(Arrays.asList("Informaci\u00f3n General", "Informacion General"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Detalles de la Cuenta"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Tus Negocios"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Secci\u00f3n Legal", "Seccion Legal"), DEFAULT_TIMEOUT);

		appWindowHandle = driver.getWindowHandle();
		captureScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		waitForAnyVisibleText(Arrays.asList("Informaci\u00f3n General", "Informacion General"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("BUSINESS PLAN"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Cambiar Plan"), DEFAULT_TIMEOUT);

		assertTrue("Expected a visible email address in Informacion General.",
				hasVisibleElementWithTextPattern(".+@.+\\..+"));
		assertTrue("Expected a visible user name label in Informacion General.",
				isAnyTextVisible(Arrays.asList("Nombre", "Name")));
	}

	private void stepValidateDetallesCuenta() {
		waitForAnyVisibleText(Arrays.asList("Cuenta creada"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Estado activo"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Idioma seleccionado"), DEFAULT_TIMEOUT);
	}

	private void stepValidateTusNegocios() {
		waitForAnyVisibleText(Arrays.asList("Tus Negocios"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Agregar Negocio"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList(BUSINESS_QUOTA_TEXT), DEFAULT_TIMEOUT);

		final List<WebElement> listLikeElements = driver.findElements(By.xpath(
				"//*[contains(normalize-space(), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]"
						+ "//*[self::li or self::tr or @role='row' or @role='listitem']"));
		assertFalse("Expected at least one visible business in the list.", filterVisible(listLikeElements).isEmpty());
	}

	private void stepOpenLegalPage(
			final List<String> linkTexts,
			final List<String> headingTexts,
			final String reportKey,
			final String screenshotName) throws Exception {
		ensureOnApplicationWindow();
		waitForAnyVisibleText(Arrays.asList("Secci\u00f3n Legal", "Seccion Legal"), DEFAULT_TIMEOUT);

		final String startingHandle = driver.getWindowHandle();
		final String startingUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkTexts);

		final String legalHandle = waitForLegalWindowOrNavigation(handlesBeforeClick, startingUrl, headingTexts);
		if (legalHandle != null && !legalHandle.equals(driver.getWindowHandle())) {
			driver.switchTo().window(legalHandle);
		}

		waitForAnyVisibleText(headingTexts, DEFAULT_TIMEOUT);
		assertTrue("Expected visible legal content text.", hasLongVisibleText(80));

		captureScreenshot(screenshotName);
		legalUrls.put(reportKey, driver.getCurrentUrl());

		if (!driver.getWindowHandle().equals(startingHandle)) {
			driver.close();
			driver.switchTo().window(startingHandle);
			waitForUiToLoad();
		} else if (!startingUrl.equals(driver.getCurrentUrl())) {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void ensureOnApplicationWindow() {
		if (appWindowHandle != null && !appWindowHandle.equals(driver.getWindowHandle())) {
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		}
	}

	private void selectGoogleAccountIfVisible() throws InterruptedException {
		for (int i = 0; i < 25; i++) {
			for (final String windowHandle : driver.getWindowHandles()) {
				driver.switchTo().window(windowHandle);
				final WebElement account = findVisibleElementNoWait(By.xpath(
						"//*[normalize-space()='" + GOOGLE_ACCOUNT + "' or contains(normalize-space(), '" + GOOGLE_ACCOUNT + "')]"));
				if (account != null) {
					clickElement(account);
					waitForUiToLoad();
					return;
				}
			}

			if (isAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio"))) {
				return;
			}

			Thread.sleep(1000L);
		}
	}

	private String waitForLegalWindowOrNavigation(
			final Set<String> handlesBeforeClick,
			final String startingUrl,
			final List<String> headingTexts) throws InterruptedException {
		for (int i = 0; i < 30; i++) {
			final Set<String> handlesAfterClick = new LinkedHashSet<>(driver.getWindowHandles());
			handlesAfterClick.removeAll(handlesBeforeClick);
			if (!handlesAfterClick.isEmpty()) {
				return handlesAfterClick.iterator().next();
			}

			if (!startingUrl.equals(driver.getCurrentUrl())) {
				return driver.getWindowHandle();
			}

			if (isAnyTextVisible(headingTexts)) {
				return driver.getWindowHandle();
			}

			Thread.sleep(500L);
		}

		return driver.getWindowHandle();
	}

	private boolean executeStep(final String reportField, final boolean canRun, final ThrowingRunnable action) {
		if (!canRun) {
			results.put(reportField, "FAIL - blocked by previous step failure");
			return false;
		}

		try {
			action.run();
			results.put(reportField, "PASS");
			return true;
		} catch (final AssertionError e) {
			results.put(reportField, "FAIL - " + safeMessage(e));
			return false;
		} catch (final Exception e) {
			results.put(reportField, "FAIL - " + safeMessage(e));
			return false;
		}
	}

	private String safeMessage(final Throwable error) {
		final String message = error.getMessage();
		if (message == null || message.isBlank()) {
			return error.getClass().getSimpleName();
		}

		final String singleLine = message.replace('\n', ' ').replace('\r', ' ').trim();
		if (singleLine.length() > 240) {
			return singleLine.substring(0, 240) + "...";
		}

		return singleLine;
	}

	private void clickByVisibleText(final List<String> candidateTexts) {
		WebElement target = null;

		for (final String text : candidateTexts) {
			target = findClickableElementByText(text, SHORT_TIMEOUT);
			if (target != null) {
				break;
			}
		}

		assertNotNull("Could not find a clickable element with text: " + candidateTexts, target);
		clickElement(target);
	}

	private void clickByVisibleTextIfVisible(final List<String> candidateTexts, final Duration timeout) {
		for (final String text : candidateTexts) {
			final WebElement target = findClickableElementByText(text, timeout);
			if (target != null) {
				clickElement(target);
				return;
			}
		}
	}

	private WebElement findClickableElementByText(final String text, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		try {
			return localWait.until(d -> {
				final String xpath = "//*[normalize-space()='" + text + "' or contains(normalize-space(), '" + text
						+ "')]";
				final List<WebElement> candidates = d.findElements(By.xpath(xpath));
				for (final WebElement candidate : candidates) {
					if (!candidate.isDisplayed()) {
						continue;
					}

					final WebElement clickableAncestor = findClickableAncestor(candidate);
					if (clickableAncestor != null && clickableAncestor.isDisplayed() && clickableAncestor.isEnabled()) {
						return clickableAncestor;
					}

					if (candidate.isEnabled()) {
						return candidate;
					}
				}
				return null;
			});
		} catch (final Exception ignored) {
			return null;
		}
	}

	private WebElement findClickableAncestor(final WebElement element) {
		final List<WebElement> clickables = element.findElements(By.xpath(
				"./ancestor-or-self::*[self::button or self::a or @role='button' or @role='menuitem'][1]"));
		return clickables.isEmpty() ? null : clickables.get(0);
	}

	private WebElement findFirstVisibleElement(final By by, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		return localWait.until(d -> {
			final List<WebElement> elements = d.findElements(by);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}

			return null;
		});
	}

	private WebElement findVisibleElementNoWait(final By by) {
		final List<WebElement> elements = driver.findElements(by);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return element;
			}
		}

		return null;
	}

	private boolean isAnyTextVisible(final List<String> candidateTexts) {
		for (final String text : candidateTexts) {
			final String xpath = "//*[normalize-space()='" + text + "' or contains(normalize-space(), '" + text + "')]";
			if (findVisibleElementNoWait(By.xpath(xpath)) != null) {
				return true;
			}
		}

		return false;
	}

	private void waitForAnyVisibleText(final List<String> candidateTexts, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		localWait.until(d -> isAnyTextVisible(candidateTexts));
	}

	private void clickElement(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		waitForUiToLoad();
		element.click();
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Some transitions do not return document.readyState in a stable way.
		}

		try {
			Thread.sleep(400L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean hasVisibleElementWithTextPattern(final String regex) {
		final List<WebElement> allElements = driver.findElements(By.xpath("//*"));
		for (final WebElement element : allElements) {
			if (!element.isDisplayed()) {
				continue;
			}

			final String text = element.getText();
			if (text != null && text.trim().matches(regex)) {
				return true;
			}
		}

		return false;
	}

	private boolean hasLongVisibleText(final int minLength) {
		final List<WebElement> textElements = driver.findElements(
				By.xpath("//p | //li | //article//*[self::p or self::li] | //main//*[self::p or self::li]"));
		for (final WebElement element : textElements) {
			if (!element.isDisplayed()) {
				continue;
			}

			final String text = element.getText();
			if (text != null && text.trim().length() >= minLength) {
				return true;
			}
		}

		return false;
	}

	private List<WebElement> filterVisible(final List<WebElement> elements) {
		final List<WebElement> visible = new ArrayList<>();
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				visible.add(element);
			}
		}
		return visible;
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final int index = screenshotCounter.getAndIncrement();
		final Path targetPath = artifactsDir.resolve(String.format("%02d-%s.png", index, checkpointName));
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeReport() throws IOException {
		if (artifactsDir == null) {
			return;
		}

		final StringBuilder content = new StringBuilder();
		content.append("saleads_mi_negocio_full_test\n");
		content.append("Artifacts directory: ").append(artifactsDir.toAbsolutePath()).append("\n\n");
		content.append("Final Report\n");
		content.append("============\n");

		for (final String field : REPORT_FIELDS) {
			content.append(field).append(": ").append(results.getOrDefault(field, "NOT RUN")).append("\n");
		}

		content.append("\nLegal URLs\n");
		content.append("==========\n");
		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			content.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
		}

		final Path reportPath = artifactsDir.resolve("final-report.txt");
		Files.writeString(reportPath, content.toString());
		System.out.println(content);
		System.out.println("Final report saved at: " + reportPath.toAbsolutePath());
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
