package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final DateTimeFormatter EVIDENCE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final LinkedHashMap<String, Boolean> stepResults = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> legalFinalUrls = new LinkedHashMap<>();
	private final Duration defaultWait = Duration.ofSeconds(readLongProperty("saleads.timeout.seconds", 30L));

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (readBooleanProperty("saleads.headless", true)) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, defaultWait);

		evidenceDir = Path.of("target", "saleads-evidence", EVIDENCE_TS.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		if (loginUrl != null) {
			driver.get(loginUrl);
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() {
		final String report = buildFinalReport();
		System.out.println(report);

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalPage("Términos y Condiciones", "08-terminos-y-condiciones.png"));
		runStep("Política de Privacidad", () -> stepValidateLegalPage("Política de Privacidad", "09-politica-de-privacidad.png"));

		assertTrue(buildFinalReport(), allStepsPassed());
	}

	private boolean stepLoginWithGoogle() throws Exception {
		if (!isTextVisible("Negocio", Duration.ofSeconds(8))) {
			clickAnyText(List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"), true);
			clickIfVisible(GOOGLE_ACCOUNT_EMAIL, Duration.ofSeconds(10));
		}

		final boolean mainInterfaceVisible = waitForAnyText(List.of("Negocio", "Mi Negocio"), Duration.ofSeconds(35));
		final boolean sidebarVisible = isSidebarVisible();
		captureScreenshot("01-dashboard-loaded.png");
		return mainInterfaceVisible && sidebarVisible;
	}

	private boolean stepOpenMiNegocioMenu() throws Exception {
		waitForText("Negocio", Duration.ofSeconds(20));
		clickAnyText(List.of("Mi Negocio"), true);

		final boolean submenuExpanded = isTextVisible("Agregar Negocio", Duration.ofSeconds(12))
				&& isTextVisible("Administrar Negocios", Duration.ofSeconds(12));
		captureScreenshot("02-mi-negocio-expanded-menu.png");
		return submenuExpanded;
	}

	private boolean stepValidateAgregarNegocioModal() throws Exception {
		clickAnyText(List.of("Agregar Negocio"), true);
		final boolean titleVisible = isTextVisible("Crear Nuevo Negocio", Duration.ofSeconds(15));
		final boolean nameInputVisible = isAnyLocatorVisible(List.of(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]")), Duration.ofSeconds(12));
		final boolean quotaVisible = isTextVisible("Tienes 2 de 3 negocios", Duration.ofSeconds(12));
		final boolean cancelButtonVisible = isTextVisible("Cancelar", Duration.ofSeconds(8));
		final boolean createButtonVisible = isTextVisible("Crear Negocio", Duration.ofSeconds(8));

		typeIfVisible("Nombre del Negocio", "Negocio Prueba Automatización");
		clickIfVisible("Cancelar", Duration.ofSeconds(5));

		captureScreenshot("03-crear-nuevo-negocio-modal.png");
		return titleVisible && nameInputVisible && quotaVisible && cancelButtonVisible && createButtonVisible;
	}

	private boolean stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(5))) {
			clickAnyText(List.of("Mi Negocio"), true);
		}

		clickAnyText(List.of("Administrar Negocios"), true);
		final boolean infoGeneralVisible = isTextVisible("Información General", Duration.ofSeconds(20));
		final boolean detallesVisible = isTextVisible("Detalles de la Cuenta", Duration.ofSeconds(15));
		final boolean tusNegociosVisible = isTextVisible("Tus Negocios", Duration.ofSeconds(15));
		final boolean legalVisible = isTextVisible("Sección Legal", Duration.ofSeconds(15));

		captureScreenshot("04-administrar-negocios-page-full.png");
		return infoGeneralVisible && detallesVisible && tusNegociosVisible && legalVisible;
	}

	private boolean stepValidateInformacionGeneral() {
		final String infoText = sectionTextOrPage("Información General");
		final boolean userEmailVisible = EMAIL_PATTERN.matcher(infoText).find();
		final boolean userNameVisible = hasLikelyUserName(infoText);
		final boolean businessPlanVisible = isTextVisible("BUSINESS PLAN", Duration.ofSeconds(8));
		final boolean changePlanVisible = isTextVisible("Cambiar Plan", Duration.ofSeconds(8));
		return userNameVisible && userEmailVisible && businessPlanVisible && changePlanVisible;
	}

	private boolean stepValidateDetallesDeLaCuenta() {
		final boolean createdVisible = isTextVisible("Cuenta creada", Duration.ofSeconds(10));
		final boolean activeVisible = isTextVisible("Estado activo", Duration.ofSeconds(10));
		final boolean languageVisible = isTextVisible("Idioma seleccionado", Duration.ofSeconds(10));
		return createdVisible && activeVisible && languageVisible;
	}

	private boolean stepValidateTusNegocios() {
		final boolean businessSectionVisible = isTextVisible("Tus Negocios", Duration.ofSeconds(10));
		final boolean addBusinessVisible = isTextVisible("Agregar Negocio", Duration.ofSeconds(10));
		final boolean quotaVisible = isTextVisible("Tienes 2 de 3 negocios", Duration.ofSeconds(10));

		final WebElement section = firstVisible(By.xpath(
				"//*[contains(normalize-space(), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]"), Duration.ofSeconds(5));
		final boolean listVisible = section != null && !section.getText().trim().isEmpty();
		return businessSectionVisible && addBusinessVisible && quotaVisible && listVisible;
	}

	private boolean stepValidateLegalPage(final String legalLinkText, final String screenshotName) throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickLegalLink(legalLinkText);

		final String newHandle = waitForNewTab(handlesBefore, Duration.ofSeconds(10));
		final boolean openedNewTab = newHandle != null;
		if (openedNewTab) {
			driver.switchTo().window(newHandle);
		}

		waitForUiLoad();
		final boolean headingVisible = isTextVisible(legalLinkText, Duration.ofSeconds(20));
		final boolean legalContentVisible = legalContentLooksPresent();
		legalFinalUrls.put(legalLinkText, driver.getCurrentUrl());
		captureScreenshot(screenshotName);

		// Return to the application context after legal-page validation.
		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		return headingVisible && legalContentVisible;
	}

	private void clickLegalLink(final String legalLinkText) {
		final WebElement legalSection = firstVisible(
				By.xpath("//*[contains(normalize-space(), 'Sección Legal')]/ancestor::*[self::section or self::div][1]"),
				Duration.ofSeconds(8));

		if (legalSection != null) {
			final String linkXpath = ".//*[self::a or self::button or @role='button'][contains(normalize-space(), "
					+ xpathLiteral(legalLinkText) + ")]";
			final List<WebElement> legalLinks = legalSection.findElements(By.xpath(linkXpath));
			if (!legalLinks.isEmpty()) {
				clickElement(legalLinks.get(0), true);
				return;
			}
		}

		clickAnyText(List.of(legalLinkText), true);
	}

	private void runStep(final String stepName, final StepAction stepAction) {
		boolean passed = false;
		try {
			passed = stepAction.run();
		} catch (Exception exception) {
			System.err.println("Step failed: " + stepName + " -> " + exception.getMessage());
		}
		stepResults.put(stepName, passed);
	}

	private void clickAnyText(final List<String> texts, final boolean waitAfterClick) {
		Exception lastException = null;

		for (final String text : texts) {
			try {
				final WebElement element = firstVisible(By.xpath(
						"//*[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), " + xpathLiteral(text)
								+ ")]"),
						Duration.ofSeconds(8));
				if (element != null) {
					clickElement(element, waitAfterClick);
					return;
				}
			} catch (Exception exception) {
				lastException = exception;
			}
		}

		throw new NoSuchElementException("Unable to locate clickable element with any text in " + texts, lastException);
	}

	private boolean clickIfVisible(final String text, final Duration timeout) {
		final WebElement element = firstVisible(By.xpath(
				"//*[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), " + xpathLiteral(text) + ")]"),
				timeout);
		if (element == null) {
			return false;
		}

		clickElement(element, true);
		return true;
	}

	private void clickElement(final WebElement element, final boolean waitAfterClick) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (TimeoutException | StaleElementReferenceException exception) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		if (waitAfterClick) {
			waitForUiLoad();
		}
	}

	private void typeIfVisible(final String fieldLabelText, final String value) {
		final List<By> fieldLocators = List.of(
				By.xpath("//label[contains(normalize-space(), " + xpathLiteral(fieldLabelText) + ")]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, " + xpathLiteral(fieldLabelText) + ")]"),
				By.xpath("//input[contains(@aria-label, " + xpathLiteral(fieldLabelText) + ")]"));

		final WebElement input = firstVisibleFromLocators(fieldLocators, Duration.ofSeconds(6));
		if (input != null) {
			input.clear();
			input.sendKeys(value);
		}
	}

	private boolean isSidebarVisible() {
		return firstVisible(
				By.xpath("//aside | //nav[contains(@class, 'sidebar')] | //*[@role='navigation'][contains(@class, 'sidebar')]"),
				Duration.ofSeconds(15)) != null;
	}

	private boolean waitForAnyText(final List<String> texts, final Duration timeout) {
		for (final String text : texts) {
			if (isTextVisible(text, timeout)) {
				return true;
			}
		}
		return false;
	}

	private void waitForText(final String text, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		localWait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//*[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), " + xpathLiteral(text) + ")]")));
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		return firstVisible(By.xpath(
				"//*[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space(), " + xpathLiteral(text) + ")]"),
				timeout) != null;
	}

	private boolean isAnyLocatorVisible(final List<By> locators, final Duration timeout) {
		return firstVisibleFromLocators(locators, timeout) != null;
	}

	private WebElement firstVisibleFromLocators(final List<By> locators, final Duration timeout) {
		for (final By locator : locators) {
			final WebElement element = firstVisible(locator, timeout);
			if (element != null) {
				return element;
			}
		}
		return null;
	}

	private WebElement firstVisible(final By locator, final Duration timeout) {
		try {
			final WebDriverWait localWait = new WebDriverWait(driver, timeout);
			return localWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (TimeoutException timeoutException) {
			return null;
		}
	}

	private String waitForNewTab(final Set<String> handlesBefore, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		try {
			return localWait.until(driver -> {
				final Set<String> currentHandles = driver.getWindowHandles();
				for (final String handle : currentHandles) {
					if (!handlesBefore.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (TimeoutException timeoutException) {
			return null;
		}
	}

	private String sectionTextOrPage(final String sectionTitle) {
		final WebElement section = firstVisible(
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral(sectionTitle)
						+ ")]/ancestor::*[self::section or self::div][1]"),
				Duration.ofSeconds(5));
		if (section != null && !section.getText().trim().isEmpty()) {
			return section.getText();
		}

		return driver.findElement(By.tagName("body")).getText();
	}

	private boolean hasLikelyUserName(final String text) {
		final String[] ignored = { "información general", "business plan", "cambiar plan", "detalles de la cuenta",
				"cuenta creada", "estado activo", "idioma seleccionado", "tus negocios", "sección legal" };
		final List<String> ignoredList = List.of(ignored);

		for (final String line : text.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.length() < 3) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(trimmed).find()) {
				continue;
			}

			final String lowered = trimmed.toLowerCase();
			boolean ignoredLine = false;
			for (final String ignoredWord : ignoredList) {
				if (lowered.contains(ignoredWord)) {
					ignoredLine = true;
					break;
				}
			}
			if (!ignoredLine) {
				return true;
			}
		}
		return false;
	}

	private boolean legalContentLooksPresent() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final String normalized = bodyText == null ? "" : bodyText.trim();
		if (normalized.length() < 120) {
			return false;
		}

		return normalized.toLowerCase().contains("términos") || normalized.toLowerCase().contains("privacidad")
				|| normalized.toLowerCase().contains("condiciones");
	}

	private void waitForUiLoad() {
		wait.until((ExpectedCondition<Boolean>) wd -> ((JavascriptExecutor) wd).executeScript("return document.readyState")
				.toString().equals("complete"));

		final List<String> loadingSelectors = List.of(".loading", ".loader", ".spinner", "[aria-busy='true']");
		for (final String selector : loadingSelectors) {
			try {
				final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(2));
				shortWait.until(ExpectedConditions.numberOfElementsToBe(By.cssSelector(selector), 0));
			} catch (TimeoutException timeoutException) {
				// Best effort: different environments may use different loading indicators.
			}
		}
	}

	private void captureScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDir.resolve(name);
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private boolean allStepsPassed() {
		for (final Boolean passed : stepResults.values()) {
			if (!Boolean.TRUE.equals(passed)) {
				return false;
			}
		}
		return !stepResults.isEmpty();
	}

	private String buildFinalReport() {
		final StringBuilder report = new StringBuilder();
		report.append("\nSaleADS Mi Negocio Workflow Report\n");
		report.append("Evidence directory: ").append(evidenceDir).append('\n');
		report.append("----------------------------------------\n");

		for (final Map.Entry<String, Boolean> entry : stepResults.entrySet()) {
			report.append(entry.getKey()).append(": ").append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL")
					.append('\n');
		}

		if (!legalFinalUrls.isEmpty()) {
			report.append("----------------------------------------\n");
			for (final Map.Entry<String, String> entry : legalFinalUrls.entrySet()) {
				report.append(entry.getKey()).append(" URL: ").append(entry.getValue()).append('\n');
			}
		}
		return report.toString();
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private static boolean readBooleanProperty(final String key, final boolean defaultValue) {
		final String raw = System.getProperty(key);
		if (raw == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(raw.trim());
	}

	private static long readLongProperty(final String key, final long defaultValue) {
		final String raw = System.getProperty(key);
		if (raw == null || raw.trim().isEmpty()) {
			return defaultValue;
		}
		try {
			return Long.parseLong(raw.trim());
		} catch (NumberFormatException exception) {
			return defaultValue;
		}
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final List<String> parts = new ArrayList<>();
		final String[] split = value.split("'");
		for (int i = 0; i < split.length; i++) {
			parts.add("'" + split[i] + "'");
			if (i < split.length - 1) {
				parts.add("\"'\"");
			}
		}

		return "concat(" + String.join(",", parts) + ")";
	}

	@FunctionalInterface
	private interface StepAction {
		boolean run() throws Exception;
	}
}
