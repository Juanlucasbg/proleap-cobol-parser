package io.proleap.e2e;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private static final List<String> REPORT_FIELDS = Arrays.asList(
			REPORT_LOGIN,
			REPORT_MI_NEGOCIO_MENU,
			REPORT_AGREGAR_NEGOCIO_MODAL,
			REPORT_ADMINISTRAR_NEGOCIOS_VIEW,
			REPORT_INFO_GENERAL,
			REPORT_DETALLES_CUENTA,
			REPORT_TUS_NEGOCIOS,
			REPORT_TERMINOS,
			REPORT_PRIVACIDAD);

	private final Map<String, String> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;

	private String baseUrl;
	private String accountEmail;
	private int timeoutSeconds;

	@Before
	public void setUp() throws IOException {
		final boolean runE2E = Boolean.parseBoolean(config("SALEADS_RUN_E2E", "saleads.runE2E", "false"));
		Assume.assumeTrue(
				"Skipping SaleADS E2E. Set SALEADS_RUN_E2E=true (or -Dsaleads.runE2E=true) to execute this test.",
				runE2E);

		baseUrl = config("SALEADS_BASE_URL", "saleads.baseUrl", "").trim();
		Assume.assumeTrue(
				"Skipping SaleADS E2E. Provide SALEADS_BASE_URL (or -Dsaleads.baseUrl) with the login page URL.",
				!baseUrl.isEmpty());

		accountEmail = config("SALEADS_GOOGLE_ACCOUNT", "saleads.googleAccount", DEFAULT_ACCOUNT_EMAIL).trim();
		timeoutSeconds = Integer.parseInt(config("SALEADS_TIMEOUT_SECONDS", "saleads.timeoutSeconds", "30"));
		final boolean headless = Boolean.parseBoolean(config("SALEADS_HEADLESS", "saleads.headless", "true"));

		initStepResults();
		screenshotDir = createScreenshotDirectory();
		driver = buildWebDriver(headless);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		driver.manage().window().setSize(new Dimension(1600, 1200));
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
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		driver.get(baseUrl);
		waitForUiAfterClick();

		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(REPORT_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(REPORT_TERMINOS, () -> stepValidateLegalLink(
				"Términos y Condiciones",
				"Términos y Condiciones",
				"08-terminos-y-condiciones.png",
				REPORT_TERMINOS));
		runStep(REPORT_PRIVACIDAD, () -> stepValidateLegalLink(
				"Política de Privacidad",
				"Política de Privacidad",
				"09-politica-de-privacidad.png",
				REPORT_PRIVACIDAD));

		assertAllStepsPass();
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
				"Continuar con Google", "Google");
		waitForUiAfterClick();
		selectGoogleAccountIfPresent();

		waitForAnyVisibleText(20, "Negocio", "Mi Negocio", "Dashboard", "Inicio", "Panel");
		assertAnyVisibleElement(20,
				By.cssSelector("aside"),
				By.cssSelector("nav"),
				By.xpath("//*[contains(@class,'sidebar') or contains(@class,'Sidebar')]"));
		takeScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickIfPresent("Negocio");
		clickByVisibleText("Mi Negocio");
		waitForUiAfterClick();

		waitForAnyVisibleText(20, "Agregar Negocio");
		waitForAnyVisibleText(20, "Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForUiAfterClick();

		waitForAnyVisibleText(20, "Crear Nuevo Negocio");
		waitForAnyVisibleText(20, "Nombre del Negocio");
		waitForAnyVisibleText(20, "Tienes 2 de 3 negocios");
		waitForAnyVisibleText(20, "Cancelar");
		waitForAnyVisibleText(20, "Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal.png");

		final WebElement businessNameInput = findAnyVisibleElement(
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[contains(@name,'negocio') or contains(@id,'negocio')]"),
				By.xpath("//div[contains(@role,'dialog')]//input[1]"));

		if (businessNameInput != null) {
			businessNameInput.click();
			businessNameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "Negocio Prueba Automatización");
		}

		clickByVisibleText("Cancelar");
		waitForUiAfterClick();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios", 3)) {
			clickIfPresent("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiAfterClick();

		waitForAnyVisibleText(20, "Información General");
		waitForAnyVisibleText(20, "Detalles de la Cuenta");
		waitForAnyVisibleText(20, "Tus Negocios");
		waitForAnyVisibleText(20, "Sección Legal");
		takeScreenshot("04-administrar-negocios-view.png");
	}

	private void stepValidateInformacionGeneral() {
		waitForAnyVisibleText(20, "Información General");
		final WebElement infoSection = findSectionContainer("Información General");
		Assert.assertNotNull("Could not locate 'Información General' section container.", infoSection);

		final String visibleEmail = detectEmailInSection(infoSection);
		Assert.assertTrue("User email is not visible in 'Información General'.", !visibleEmail.isEmpty());
		Assert.assertTrue("Expected account email not visible. Expected: " + accountEmail,
				visibleEmail.equalsIgnoreCase(accountEmail) || isTextVisible(accountEmail, 5));

		final String probableUserName = detectLikelyUserNameInSection(infoSection);
		Assert.assertTrue("User name is not visible in 'Información General'.", !probableUserName.isEmpty());

		waitForAnyVisibleText(20, "BUSINESS PLAN");
		waitForAnyVisibleText(20, "Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		waitForAnyVisibleText(20, "Detalles de la Cuenta");
		waitForAnyVisibleText(20, "Cuenta creada");
		waitForAnyVisibleText(20, "Estado activo");
		waitForAnyVisibleText(20, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitForAnyVisibleText(20, "Tus Negocios");
		waitForAnyVisibleText(20, "Agregar Negocio");
		waitForAnyVisibleText(20, "Tienes 2 de 3 negocios");

		final WebElement negociosSection = findSectionContainer("Tus Negocios");
		Assert.assertNotNull("Could not locate 'Tus Negocios' section container.", negociosSection);
		Assert.assertTrue("Business list is not visible in 'Tus Negocios'.", hasBusinessListContent(negociosSection));
	}

	private void stepValidateLegalLink(
			final String linkText,
			final String expectedHeading,
			final String screenshotName,
			final String reportField) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String appUrlBeforeClick = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		waitForUiAfterClick();

		final String activeHandle = switchToNewTabIfOpened(beforeHandles, appHandle);
		final boolean openedNewTab = !activeHandle.equals(appHandle);

		waitForAnyVisibleText(20, expectedHeading);
		assertLegalContentVisible();
		takeScreenshot(screenshotName);
		legalUrls.put(reportField, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiAfterClick();
		} else if (!driver.getCurrentUrl().equals(appUrlBeforeClick)) {
			driver.navigate().back();
			waitForUiAfterClick();
		}

		waitForAnyVisibleText(20, "Sección Legal", "Información General", "Tus Negocios");
	}

	private void runStep(final String reportField, final StepAction action) throws Exception {
		try {
			action.run();
			stepResults.put(reportField, "PASS");
		} catch (final Exception ex) {
			stepResults.put(reportField, "FAIL - " + sanitizeError(ex.getMessage()));
			throw ex;
		}
	}

	private void assertAllStepsPass() {
		final List<String> failures = stepResults.entrySet().stream()
				.filter(entry -> !entry.getValue().startsWith("PASS"))
				.map(entry -> entry.getKey() + " -> " + entry.getValue())
				.collect(Collectors.toList());

		if (!failures.isEmpty()) {
			Assert.fail("One or more validations failed:\n" + String.join("\n", failures));
		}
	}

	private void initStepResults() {
		for (final String field : REPORT_FIELDS) {
			stepResults.put(field, "FAIL - Not executed");
		}
	}

	private void printFinalReport() {
		System.out.println("\n=== SaleADS Mi Negocio - Final Report ===");
		for (final String field : REPORT_FIELDS) {
			System.out.println(field + ": " + stepResults.get(field));
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("--- Final URLs ---");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(entry.getKey() + " URL: " + entry.getValue());
			}
		}
		if (screenshotDir != null) {
			System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
		}
	}

	private WebDriver buildWebDriver(final boolean headless) {
		try {
			final ChromeOptions chromeOptions = new ChromeOptions();
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			chromeOptions.addArguments("--window-size=1600,1200");
			chromeOptions.addArguments("--disable-gpu");
			chromeOptions.addArguments("--no-sandbox");
			chromeOptions.addArguments("--disable-dev-shm-usage");
			return new ChromeDriver(chromeOptions);
		} catch (final RuntimeException chromeError) {
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			return new FirefoxDriver(firefoxOptions);
		}
	}

	private void selectGoogleAccountIfPresent() {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));
			final WebElement accountOption = shortWait.until(d -> {
				for (final By by : textLocators(accountEmail)) {
					final WebElement found = firstDisplayed(by);
					if (found != null) {
						return found;
					}
				}
				return null;
			});
			safeClick(accountOption);
			waitForUiAfterClick();
		} catch (final TimeoutException ignored) {
			// Login may continue directly when the Google account was already selected.
		}
	}

	private void clickByVisibleText(final String... textCandidates) {
		final WebElement element = waitForAnyVisibleText(timeoutSeconds, textCandidates);
		Assert.assertNotNull("No clickable element found for texts: " + Arrays.toString(textCandidates), element);
		safeClick(element);
		waitForUiAfterClick();
	}

	private void clickIfPresent(final String text) {
		final WebElement maybeElement = findVisibleByText(4, text);
		if (maybeElement != null) {
			safeClick(maybeElement);
			waitForUiAfterClick();
		}
	}

	private WebElement waitForAnyVisibleText(final int seconds, final String... textCandidates) {
		final WebDriverWait localWait = new WebDriverWait(driver, Duration.ofSeconds(seconds));
		return localWait.until(d -> {
			for (final String text : textCandidates) {
				for (final By by : textLocators(text)) {
					final WebElement found = firstDisplayed(by);
					if (found != null) {
						return found;
					}
				}
			}
			return null;
		});
	}

	private WebElement findVisibleByText(final int seconds, final String textCandidate) {
		try {
			return waitForAnyVisibleText(seconds, textCandidate);
		} catch (final TimeoutException ex) {
			return null;
		}
	}

	private WebElement assertAnyVisibleElement(final int seconds, final By... locators) {
		final WebDriverWait localWait = new WebDriverWait(driver, Duration.ofSeconds(seconds));
		return localWait.until(d -> {
			for (final By locator : locators) {
				final WebElement visible = firstDisplayed(locator);
				if (visible != null) {
					return visible;
				}
			}
			return null;
		});
	}

	private WebElement findAnyVisibleElement(final By... locators) {
		for (final By locator : locators) {
			final WebElement visible = firstDisplayed(locator);
			if (visible != null) {
				return visible;
			}
		}
		return null;
	}

	private WebElement firstDisplayed(final By locator) {
		try {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		} catch (final NoSuchElementException | StaleElementReferenceException ignored) {
			return null;
		}
		return null;
	}

	private void safeClick(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final Exception clickError) {
			try {
				new Actions(driver).moveToElement(element).click().perform();
			} catch (final Exception actionsError) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			}
		}
	}

	private void waitForUiAfterClick() {
		waitForDocumentReady();
		try {
			Thread.sleep(500L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void waitForDocumentReady() {
		if (!(driver instanceof JavascriptExecutor)) {
			return;
		}
		wait.until(d -> {
			final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(state) || "interactive".equals(state);
		});
	}

	private boolean isTextVisible(final String text, final int seconds) {
		try {
			waitForAnyVisibleText(seconds, text);
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void assertLegalContentVisible() {
		final List<WebElement> contentCandidates = driver.findElements(By.xpath(
				"//p[string-length(normalize-space(.)) > 80] | //div[string-length(normalize-space(.)) > 120]"));
		for (final WebElement candidate : contentCandidates) {
			if (candidate.isDisplayed()) {
				return;
			}
		}
		Assert.fail("Legal content text is not visible.");
	}

	private String switchToNewTabIfOpened(final Set<String> handlesBeforeClick, final String fallbackHandle) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12)).until(d -> d.getWindowHandles().size() > handlesBeforeClick.size());
			final Set<String> currentHandles = driver.getWindowHandles();
			for (final String handle : currentHandles) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					waitForUiAfterClick();
					return handle;
				}
			}
		} catch (final TimeoutException ignored) {
			// Link may have opened in the same tab.
		}
		return fallbackHandle;
	}

	private WebElement findSectionContainer(final String sectionHeading) {
		final String headingLiteral = xpathLiteral(sectionHeading);
		final List<By> sectionLocators = Arrays.asList(
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(.),"
						+ headingLiteral + ")]/ancestor::*[self::section or self::div][1]"),
				By.xpath("//*[contains(normalize-space(.)," + headingLiteral + ")]/ancestor::*[self::section or self::div][1]"));

		for (final By sectionLocator : sectionLocators) {
			final WebElement section = firstDisplayed(sectionLocator);
			if (section != null) {
				return section;
			}
		}
		return null;
	}

	private String detectEmailInSection(final WebElement section) {
		final Pattern emailPattern = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
		final List<WebElement> textNodes = section.findElements(By.xpath(".//*[self::span or self::p or self::div or self::h1 or self::h2 or self::h3 or self::h4]"));
		for (final WebElement textNode : textNodes) {
			if (!textNode.isDisplayed()) {
				continue;
			}
			final String text = normalized(textNode.getText());
			if (emailPattern.matcher(text).matches()) {
				return text;
			}
			if (text.contains("@") && text.contains(".")) {
				return text;
			}
		}
		return "";
	}

	private String detectLikelyUserNameInSection(final WebElement section) {
		final List<String> blockedTexts = Arrays.asList(
				"información general",
				"business plan",
				"cambiar plan",
				"cuenta creada",
				"estado activo",
				"idioma seleccionado",
				"tienes 2 de 3 negocios");
		final List<WebElement> textNodes = section.findElements(By.xpath(".//*[self::span or self::p or self::div or self::h1 or self::h2 or self::h3 or self::h4]"));
		for (final WebElement textNode : textNodes) {
			if (!textNode.isDisplayed()) {
				continue;
			}
			final String text = normalized(textNode.getText());
			final String lowerText = text.toLowerCase(Locale.ROOT);
			if (text.isEmpty() || text.length() < 3 || text.contains("@")) {
				continue;
			}
			if (blockedTexts.stream().anyMatch(lowerText::contains)) {
				continue;
			}
			if (text.matches(".*\\d.*")) {
				continue;
			}
			return text;
		}
		return "";
	}

	private boolean hasBusinessListContent(final WebElement section) {
		final List<WebElement> potentialEntries = section.findElements(By.xpath(".//*[self::li or self::tr or self::article or self::div]"));
		final List<String> ignored = Arrays.asList("tus negocios", "agregar negocio", "tienes 2 de 3 negocios");
		int contentRows = 0;
		for (final WebElement entry : potentialEntries) {
			if (!entry.isDisplayed()) {
				continue;
			}
			final String text = normalized(entry.getText()).toLowerCase(Locale.ROOT);
			if (text.isEmpty()) {
				continue;
			}
			if (ignored.stream().anyMatch(text::equals)) {
				continue;
			}
			contentRows++;
			if (contentRows >= 1) {
				return true;
			}
		}
		return false;
	}

	private Path createScreenshotDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path outputDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(outputDir);
		return outputDir;
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final Path destination = screenshotDir.resolve(fileName);
		final java.io.File image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(image.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private String config(final String envName, final String systemPropertyName, final String defaultValue) {
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue.trim();
		}
		final String propertyValue = System.getProperty(systemPropertyName);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue.trim();
		}
		return defaultValue;
	}

	private String sanitizeError(final String message) {
		if (message == null || message.trim().isEmpty()) {
			return "No additional error details.";
		}
		return message.replace('\n', ' ').trim();
	}

	private List<By> textLocators(final String text) {
		final String literal = xpathLiteral(text);
		final List<By> locators = new ArrayList<>();
		locators.add(By.xpath("//button[normalize-space(.)=" + literal + "]"));
		locators.add(By.xpath("//a[normalize-space(.)=" + literal + "]"));
		locators.add(By.xpath("//*[@role='button' and normalize-space(.)=" + literal + "]"));
		locators.add(By.xpath("//*[normalize-space(text())=" + literal + "]"));
		locators.add(By.xpath("//*[contains(normalize-space(.)," + literal + ")]"));
		return locators;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			sb.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				sb.append(", \"'\", ");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	private String normalized(final String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
