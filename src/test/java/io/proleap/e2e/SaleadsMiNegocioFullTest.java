package io.proleap.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFORMACION_GENERAL = "Informacion General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Terminos y Condiciones";
	private static final String REPORT_POLITICA = "Politica de Privacidad";

	private final Map<String, Boolean> stepReport = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String terminosFinalUrl = "N/A";
	private String politicaFinalUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this UI workflow test.",
				Boolean.parseBoolean(readEnv("SALEADS_E2E_ENABLED", "false")));

		final boolean headless = Boolean.parseBoolean(readEnv("SALEADS_HEADLESS", "true"));
		final int timeoutSeconds = Integer.parseInt(readEnv("SALEADS_TIMEOUT_SECONDS", "30"));
		final String startUrl = System.getenv("SALEADS_URL");

		evidenceDir = Paths.get("evidence", "saleads_mi_negocio_full_test", TIMESTAMP_FORMAT.format(Instant.now()));
		Files.createDirectories(evidenceDir);

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

		if (startUrl != null && !startUrl.isBlank()) {
			driver.get(startUrl);
			waitForUiLoad();
		} else {
			final String currentUrl = driver.getCurrentUrl();
			if ("about:blank".equalsIgnoreCase(currentUrl)) {
				throw new IllegalStateException(
						"Browser started on about:blank. Set SALEADS_URL to the SaleADS login page for your environment.");
			}
		}
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		executeStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		executeStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		executeStep(REPORT_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		executeStep(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		executeStep(REPORT_INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		executeStep(REPORT_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		executeStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		executeStep(REPORT_TERMINOS, this::stepValidateTerminosCondiciones);
		executeStep(REPORT_POLITICA, this::stepValidatePoliticaPrivacidad);

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : stepReport.entrySet()) {
			if (!entry.getValue()) {
				failedSteps.add(entry.getKey());
			}
		}

		if (!failedSteps.isEmpty()) {
			Assert.fail("FAIL steps: " + failedSteps + ". See evidence at: " + evidenceDir.toAbsolutePath());
		}
	}

	private void stepLoginWithGoogle() {
		final Set<String> beforeClickHandles = driver.getWindowHandles();
		clickByVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesion con Google", "Continuar con Google", "Google"),
				"Google login button");

		switchToNewWindowIfPresent(beforeClickHandles);
		selectGoogleAccountIfVisible(readEnv("SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com"));

		switchToMainAppWindow(beforeClickHandles);
		waitForAnyVisibleText(Arrays.asList("Mi Negocio", "Negocio"));

		final WebElement sidebar = findFirstVisibleElement(Arrays.asList(By.xpath("//aside"), By.xpath("//nav"),
				By.xpath("//*[contains(translate(@class,'SIDEBAR','sidebar'),'sidebar')]")));
		require(sidebar != null, "Left sidebar navigation is not visible after login.");

		takeScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() {
		waitForAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio"));
		clickByVisibleText(Arrays.asList("Mi Negocio"), "Mi Negocio option");

		waitForAnyVisibleText(Arrays.asList("Agregar Negocio", "Administrar Negocios"));
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");

		takeScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText(Arrays.asList("Agregar Negocio"), "Agregar Negocio button");

		waitForAnyVisibleText(Arrays.asList("Crear Nuevo Negocio"));
		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		final WebElement nombreInput = findFirstVisibleElement(Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), " + xpathLiteral("Nombre del Negocio")
						+ ")]/following::*[self::input or self::textarea][1]"),
				By.xpath("//input[contains(@placeholder, " + xpathLiteral("Nombre del Negocio") + ")]"),
				By.xpath("//input[@name='nombreNegocio' or @id='nombreNegocio']")));
		require(nombreInput != null, "Input field 'Nombre del Negocio' was not found.");

		nombreInput.click();
		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatizacion");

		takeScreenshot("03-crear-nuevo-negocio-modal.png");

		clickByVisibleText(Arrays.asList("Cancelar"), "Cancelar button");
		waitForTextToDisappear("Crear Nuevo Negocio");
	}

	private void stepOpenAdministrarNegocios() {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText(Arrays.asList("Mi Negocio"), "Mi Negocio option re-expand");
		}

		clickByVisibleText(Arrays.asList("Administrar Negocios"), "Administrar Negocios option");

		waitForAnyVisibleText(Arrays.asList("Informacion General", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Seccion Legal"));
		assertAnyTextVisible(Arrays.asList("Informacion General", "Información General"));
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertAnyTextVisible(Arrays.asList("Seccion Legal", "Sección Legal"));

		takeFullPageScreenshot("04-administrar-negocios-cuenta.png");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading(Arrays.asList("Informacion General", "Información General"));
		final String sectionText = section.getText();

		final Matcher emailMatcher = EMAIL_PATTERN.matcher(sectionText);
		require(emailMatcher.find(), "User email is not visible in 'Informacion General'.");

		final String email = emailMatcher.group();
		final String normalized = sectionText.replace(email, "");
		boolean hasUserName = false;
		for (final String line : normalized.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.equalsIgnoreCase("Informacion General") || trimmed.equalsIgnoreCase("Información General")) {
				continue;
			}
			if (trimmed.toUpperCase().contains("BUSINESS PLAN")) {
				continue;
			}
			if (trimmed.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}
			hasUserName = true;
			break;
		}

		require(hasUserName, "User name is not visible in 'Informacion General'.");
		assertTextVisible("BUSINESS PLAN");
		clickableElementByText("Cambiar Plan", false);
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = findSectionByHeading(Arrays.asList("Detalles de la Cuenta"));
		final String text = section.getText();

		require(text.contains("Cuenta creada"), "Missing 'Cuenta creada' in account details.");
		require(text.contains("Estado activo"), "Missing 'Estado activo' in account details.");
		require(text.contains("Idioma seleccionado"), "Missing 'Idioma seleccionado' in account details.");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading(Arrays.asList("Tus Negocios"));
		final String text = section.getText();

		require(text.contains("Tienes 2 de 3 negocios"), "Missing 'Tienes 2 de 3 negocios' in business section.");
		require(findVisibleElements(By.xpath(".//*[contains(normalize-space(.), " + xpathLiteral("Agregar Negocio") + ")]"), section)
				.size() > 0, "Button 'Agregar Negocio' is not visible in business section.");

		final List<WebElement> businessRows = findVisibleElements(
				By.xpath(".//*[self::li or self::tr or contains(@class,'business') or contains(@class,'negocio')]"), section);
		final List<WebElement> directChildren = findVisibleElements(By.xpath("./*"), section);
		final boolean hasBusinessList = !businessRows.isEmpty() || directChildren.size() > 2;
		require(hasBusinessList, "Business list is not visible in 'Tus Negocios'.");
	}

	private void stepValidateTerminosCondiciones() {
		terminosFinalUrl = openLegalLinkAndValidate(Arrays.asList("Terminos y Condiciones", "Términos y Condiciones"),
				Arrays.asList("Terminos y Condiciones", "Términos y Condiciones"), "05-terminos-y-condiciones.png");
	}

	private void stepValidatePoliticaPrivacidad() {
		politicaFinalUrl = openLegalLinkAndValidate(Arrays.asList("Politica de Privacidad", "Política de Privacidad"),
				Arrays.asList("Politica de Privacidad", "Política de Privacidad"), "06-politica-de-privacidad.png");
	}

	private String openLegalLinkAndValidate(final List<String> linkTexts, final List<String> headingTexts,
			final String screenshotName) {
		waitForAnyVisibleText(Arrays.asList("Seccion Legal", "Sección Legal"));

		final String appHandle = driver.getWindowHandle();
		final String appUrlBefore = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkTexts, "Legal link: " + linkTexts.get(0));
		switchToNewWindowIfPresent(handlesBeforeClick);

		waitUntilNavigationOrHeadingVisible(appUrlBefore, headingTexts);
		waitForAnyVisibleText(headingTexts);

		final String legalText = safeBodyText();
		require(legalText.trim().length() > 120, "Legal content text is not visible.");

		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (!Objects.equals(driver.getWindowHandle(), appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiLoad();
		} else if (!Objects.equals(appUrlBefore, finalUrl)) {
			driver.navigate().back();
			waitForUiLoad();
		}

		return finalUrl;
	}

	private void executeStep(final String reportField, final StepAction action) {
		try {
			action.run();
			stepReport.put(reportField, true);
			stepDetails.put(reportField, "PASS");
		} catch (final Exception ex) {
			stepReport.put(reportField, false);
			stepDetails.put(reportField, "FAIL - " + sanitize(ex.getMessage()));
			takeScreenshot("failed-" + slug(reportField) + ".png");
		}
	}

	private void waitForUiLoad() {
		wait.until(driver -> {
			final Object readyState = ((JavascriptExecutor) driver).executeScript("return document.readyState");
			return "complete".equals(readyState);
		});
		sleep(400);
	}

	private void waitUntilNavigationOrHeadingVisible(final String previousUrl, final List<String> headingTexts) {
		try {
			wait.until(driver -> {
				if (!Objects.equals(previousUrl, driver.getCurrentUrl())) {
					return true;
				}
				for (final String heading : headingTexts) {
					if (isTextVisible(heading)) {
						return true;
					}
				}
				return false;
			});
		} catch (final TimeoutException ignored) {
			// The following explicit heading check will produce a descriptive failure.
		}
		waitForUiLoad();
	}

	private void switchToNewWindowIfPresent(final Set<String> handlesBeforeClick) {
		try {
			wait.until(driver -> driver.getWindowHandles().size() > handlesBeforeClick.size());
		} catch (final TimeoutException ignored) {
			return;
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBeforeClick.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				return;
			}
		}
	}

	private void switchToMainAppWindow(final Set<String> handlesBeforeClick) {
		if (driver.getWindowHandles().size() == 1) {
			return;
		}

		for (final String handle : handlesBeforeClick) {
			if (driver.getWindowHandles().contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiLoad();
				return;
			}
		}
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final List<WebElement> candidateEmailNodes = findVisibleElements(
				By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(email) + ")]"), driver);
		if (!candidateEmailNodes.isEmpty()) {
			try {
				candidateEmailNodes.get(0).click();
				waitForUiLoad();
			} catch (final Exception ignored) {
				// If click fails, login may have auto-resumed; continue with app assertions.
			}
		}
	}

	private void clickByVisibleText(final List<String> texts, final String controlDescription) {
		WebElement element = null;
		for (final String text : texts) {
			element = clickableElementByText(text, true);
			if (element != null) {
				break;
			}
		}
		require(element != null, "Could not find clickable element for " + controlDescription + ".");
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiLoad();
	}

	private WebElement clickableElementByText(final String text, final boolean returnNullIfMissing) {
		final String textLiteral = xpathLiteral(text);
		final List<By> candidates = Arrays.asList(
				By.xpath("//button[contains(normalize-space(.), " + textLiteral + ")]"),
				By.xpath("//a[contains(normalize-space(.), " + textLiteral + ")]"),
				By.xpath("//*[@role='button' and contains(normalize-space(.), " + textLiteral + ")]"),
				By.xpath("//*[contains(normalize-space(.), " + textLiteral + ") and (self::div or self::span)]"));

		for (final By candidate : candidates) {
			final List<WebElement> elements = findVisibleElements(candidate, driver);
			for (final WebElement element : elements) {
				if (element.isEnabled()) {
					return element;
				}
			}
		}

		if (returnNullIfMissing) {
			return null;
		}
		throw new IllegalStateException("Element with text '" + text + "' is not visible.");
	}

	private void assertTextVisible(final String text) {
		require(isTextVisible(text), "Expected visible text not found: '" + text + "'");
	}

	private void assertAnyTextVisible(final List<String> texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return;
			}
		}
		throw new IllegalStateException("None of expected texts were visible: " + texts);
	}

	private void waitForAnyVisibleText(final List<String> texts) {
		wait.until(driver -> {
			for (final String text : texts) {
				if (isTextVisible(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isTextVisible(final String text) {
		final By locator = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
		return !findVisibleElements(locator, driver).isEmpty();
	}

	private void waitForTextToDisappear(final String text) {
		final By locator = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
	}

	private WebElement findSectionByHeading(final List<String> headingCandidates) {
		for (final String heading : headingCandidates) {
			final String headingLiteral = xpathLiteral(heading);
			final List<By> sectionLocators = Arrays.asList(
					By.xpath("//section[.//*[contains(normalize-space(.), " + headingLiteral + ")]]"),
					By.xpath("//div[.//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(.), "
							+ headingLiteral + ")]]"),
					By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(.), "
							+ headingLiteral + ")]/ancestor::*[self::section or self::div][1]"));

			for (final By locator : sectionLocators) {
				final List<WebElement> sections = findVisibleElements(locator, driver);
				if (!sections.isEmpty()) {
					return sections.get(0);
				}
			}
		}
		throw new IllegalStateException("Could not find section for headings: " + headingCandidates);
	}

	private List<WebElement> findVisibleElements(final By locator, final Object scope) {
		final List<WebElement> all;
		if (scope instanceof WebDriver) {
			all = ((WebDriver) scope).findElements(locator);
		} else {
			all = ((WebElement) scope).findElements(locator);
		}

		final List<WebElement> visible = new ArrayList<>();
		for (final WebElement element : all) {
			try {
				if (element.isDisplayed()) {
					visible.add(element);
				}
			} catch (final Exception ignored) {
				// Ignore stale/non-interactable candidates.
			}
		}
		return visible;
	}

	private WebElement findFirstVisibleElement(final List<By> locators) {
		for (final By locator : locators) {
			final List<WebElement> elements = findVisibleElements(locator, driver);
			if (!elements.isEmpty()) {
				return elements.get(0);
			}
		}
		return null;
	}

	private String safeBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception ignored) {
			return "";
		}
	}

	private void takeScreenshot(final String fileName) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		try {
			final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(evidenceDir.resolve(fileName), screenshot);
		} catch (final Exception ignored) {
			// Evidence capture should not abort workflow execution.
		}
	}

	private void takeFullPageScreenshot(final String fileName) {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final Number pageHeight = (Number) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			driver.manage().window().setSize(new Dimension(1920, Math.max(pageHeight.intValue(), originalSize.getHeight())));
			waitForUiLoad();
			takeScreenshot(fileName);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiLoad();
		}
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final List<String> orderedFields = Arrays.asList(REPORT_LOGIN, REPORT_MI_NEGOCIO_MENU, REPORT_AGREGAR_NEGOCIO_MODAL,
				REPORT_ADMINISTRAR_NEGOCIOS_VIEW, REPORT_INFORMACION_GENERAL, REPORT_DETALLES_CUENTA, REPORT_TUS_NEGOCIOS,
				REPORT_TERMINOS, REPORT_POLITICA);

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		reportBuilder.append("timestamp_utc=").append(Instant.now().toString()).append(System.lineSeparator());
		reportBuilder.append(System.lineSeparator());

		for (final String field : orderedFields) {
			final boolean pass = stepReport.getOrDefault(field, false);
			final String detail = stepDetails.getOrDefault(field, "NOT_EXECUTED");
			reportBuilder.append(field).append(": ").append(pass ? "PASS" : "FAIL").append(" | ").append(detail)
					.append(System.lineSeparator());
		}

		reportBuilder.append(System.lineSeparator());
		reportBuilder.append("Terminos y Condiciones URL: ").append(terminosFinalUrl).append(System.lineSeparator());
		reportBuilder.append("Politica de Privacidad URL: ").append(politicaFinalUrl).append(System.lineSeparator());

		Files.writeString(evidenceDir.resolve("final-report.txt"), reportBuilder.toString(), StandardCharsets.UTF_8);
	}

	private String readEnv(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return value;
	}

	private void require(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private String sanitize(final String message) {
		if (message == null || message.isBlank()) {
			return "No details";
		}
		return message.replace('\n', ' ').replace('\r', ' ').trim();
	}

	private String slug(final String value) {
		final String lower = value.toLowerCase();
		final StringBuilder out = new StringBuilder();
		for (int i = 0; i < lower.length(); i++) {
			final char c = lower.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				out.append(c);
			} else if (c == ' ' || c == '_' || c == '-') {
				out.append('-');
			}
		}
		return out.toString().replaceAll("-{2,}", "-");
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder concat = new StringBuilder("concat(");
		final String[] parts = text.split("'");
		for (int i = 0; i < parts.length; i++) {
			concat.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				concat.append(",\"'\",");
			}
		}
		concat.append(")");
		return concat.toString();
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
