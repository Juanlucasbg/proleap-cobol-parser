package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informacion General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Terminos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Politica de Privacidad";

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue("Set saleads.e2e.enabled=true (or SALEADS_E2E_ENABLED=true) to run this flow.",
				readBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false));

		initializeStepStatus();
		evidenceDir = createEvidenceDirectory();
		driver = createWebDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);
		openLoginPageIfConfigured();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		executeStep(STEP_LOGIN, this::loginWithGoogleAndValidateDashboard);
		executeStep(STEP_MI_NEGOCIO_MENU, this::openMiNegocioMenuAndValidateOptions);
		executeStep(STEP_AGREGAR_NEGOCIO_MODAL, this::validateAgregarNegocioModal);
		executeStep(STEP_ADMINISTRAR_VIEW, this::openAdministrarNegociosAndValidateSections);
		executeStep(STEP_INFO_GENERAL, this::validateInformacionGeneralSection);
		executeStep(STEP_DETALLES_CUENTA, this::validateDetallesDeLaCuentaSection);
		executeStep(STEP_TUS_NEGOCIOS, this::validateTusNegociosSection);
		executeStep(STEP_TERMINOS,
				() -> validateLegalLink(new String[] { "T\u00E9rminos y Condiciones", "Terminos y Condiciones" },
						new String[] { "T\u00E9rminos y Condiciones", "Terminos y Condiciones" }, "terminos-url",
				"08-terminos-y-condiciones"));
		executeStep(STEP_PRIVACIDAD,
				() -> validateLegalLink(new String[] { "Pol\u00EDtica de Privacidad", "Politica de Privacidad" },
						new String[] { "Pol\u00EDtica de Privacidad", "Politica de Privacidad" }, "privacidad-url",
						"09-politica-de-privacidad"));

		final String report = buildFinalReport();
		System.out.println(report);
		assertTrue("One or more validation steps failed.\n" + report,
				stepStatus.values().stream().allMatch("PASS"::equals));
	}

	private void initializeStepStatus() {
		final List<String> orderedSteps = Arrays.asList(STEP_LOGIN, STEP_MI_NEGOCIO_MENU, STEP_AGREGAR_NEGOCIO_MODAL,
				STEP_ADMINISTRAR_VIEW, STEP_INFO_GENERAL, STEP_DETALLES_CUENTA, STEP_TUS_NEGOCIOS, STEP_TERMINOS,
				STEP_PRIVACIDAD);

		for (final String step : orderedSteps) {
			stepStatus.put(step, "FAIL");
			stepDetails.put(step, "Step was not executed.");
		}
	}

	private void executeStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepStatus.put(stepName, "PASS");
			stepDetails.put(stepName, "Validated successfully.");
		} catch (final Throwable t) {
			stepStatus.put(stepName, "FAIL");
			final String detail = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
			stepDetails.put(stepName, detail);
			captureScreenshotSafe("failure-" + slugify(stepName));
		}
	}

	private void loginWithGoogleAndValidateDashboard() throws Exception {
		clickByAnyText("Sign in with Google", "Iniciar sesion con Google", "Inicia sesion con Google",
				"Continuar con Google", "Login with Google");
		selectGoogleAccountIfVisible();
		waitForUiToSettle();

		assertVisibleByAnyText("Sidebar with Negocio should be visible after login.", "Negocio");
		assertSidebarVisible();
		captureScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenuAndValidateOptions() throws Exception {
		clickByAnyText("Negocio");
		clickByAnyText("Mi Negocio");
		waitForUiToSettle();

		assertVisibleByAnyText("Agregar Negocio option should be visible.", "Agregar Negocio");
		assertVisibleByAnyText("Administrar Negocios option should be visible.", "Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickByAnyText("Agregar Negocio");
		waitForUiToSettle();

		assertVisibleByAnyText("Modal title Crear Nuevo Negocio should be visible.", "Crear Nuevo Negocio");
		assertVisibleByAnyText("Nombre del Negocio input label should be visible.", "Nombre del Negocio");
		assertVisibleByAnyText("Expected quota text should be visible.", "Tienes 2 de 3 negocios");
		assertVisibleByAnyText("Cancelar button should be present.", "Cancelar");
		assertVisibleByAnyText("Crear Negocio button should be present.", "Crear Negocio");

		final WebElement nameInput = findVisibleElement(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio') or contains(@name,'nombre') or contains(@id,'nombre')]"),
				SHORT_TIMEOUT);
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatizacion");
		captureScreenshot("03-agregar-negocio-modal");

		clickByAnyText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byAnyVisibleText("Crear Nuevo Negocio")));
		waitForUiToSettle();
	}

	private void openAdministrarNegociosAndValidateSections() throws Exception {
		ensureMiNegocioExpanded();
		clickByAnyText("Administrar Negocios");
		waitForUiToSettle();

		assertVisibleByAnyText("Informacion General section should be visible.", "Informaci\u00F3n General",
				"Informacion General");
		assertVisibleByAnyText("Detalles de la Cuenta section should be visible.", "Detalles de la Cuenta");
		assertVisibleByAnyText("Tus Negocios section should be visible.", "Tus Negocios");
		assertVisibleByAnyText("Seccion Legal section should be visible.", "Secci\u00F3n Legal", "Seccion Legal");
		captureFullPageScreenshot("04-administrar-negocios-view");
	}

	private void validateInformacionGeneralSection() throws Exception {
		assertVisibleByAnyText("User email should be visible in Informacion General.", GOOGLE_ACCOUNT_EMAIL);
		assertVisibleByAnyText("BUSINESS PLAN text should be visible.", "BUSINESS PLAN");
		assertVisibleByAnyText("Cambiar Plan button should be visible.", "Cambiar Plan");

		final WebElement infoSection = findContainerByHeading("Informaci\u00F3n General", "Informacion General");
		final String sectionText = normalizeSpace(infoSection.getText());
		assertTrue("User name should be visible in Informacion General.",
				containsLikelyPersonName(sectionText, GOOGLE_ACCOUNT_EMAIL));
	}

	private void validateDetallesDeLaCuentaSection() {
		assertVisibleByAnyText("Cuenta creada should be visible.", "Cuenta creada");
		assertVisibleByAnyText("Estado activo should be visible.", "Estado activo");
		assertVisibleByAnyText("Idioma seleccionado should be visible.", "Idioma seleccionado");
	}

	private void validateTusNegociosSection() throws Exception {
		final WebElement section = findContainerByHeading("Tus Negocios");
		assertTrue("Business list should be visible in Tus Negocios section.", hasBusinessList(section));
		assertVisibleInside(section, "Agregar Negocio", "Agregar Negocio button should exist in Tus Negocios.");
		assertVisibleInside(section, "Tienes 2 de 3 negocios", "Quota text should be visible in Tus Negocios.");
	}

	private void validateLegalLink(final String[] linkTexts, final String[] expectedHeadings, final String urlKey,
			final String screenshotName) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String urlBeforeClick = driver.getCurrentUrl();

		clickByAnyText(linkTexts);
		wait.until(d -> d.getWindowHandles().size() > handlesBefore.size()
				|| !Objects.equals(urlBeforeClick, d.getCurrentUrl()));

		final boolean openedNewTab = driver.getWindowHandles().size() > handlesBefore.size();
		if (openedNewTab) {
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		waitForUiToSettle();
		assertVisibleByAnyText("Legal heading should be visible.", expectedHeadings);

		final String bodyText = normalizeSpace(driver.findElement(By.tagName("body")).getText());
		assertTrue("Legal content should contain meaningful text.", bodyText.length() > 200);

		captureScreenshot(screenshotName);
		capturedUrls.put(urlKey, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToSettle();
		} else {
			driver.navigate().back();
			waitForUiToSettle();
		}
	}

	private void ensureMiNegocioExpanded() throws Exception {
		if (!isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
			if (isTextVisible("Mi Negocio", SHORT_TIMEOUT)) {
				clickByAnyText("Mi Negocio");
			} else {
				clickByAnyText("Negocio");
				clickByAnyText("Mi Negocio");
			}
		}
		waitForUiToSettle();
	}

	private void selectGoogleAccountIfVisible() throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesAfterLoginClick = driver.getWindowHandles();
		if (handlesAfterLoginClick.size() > 1) {
			for (final String handle : handlesAfterLoginClick) {
				if (!handle.equals(appWindow)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		if (isAnyTextVisible(Duration.ofSeconds(10), GOOGLE_ACCOUNT_EMAIL)) {
			clickByAnyText(GOOGLE_ACCOUNT_EMAIL);
		}

		if (handlesAfterLoginClick.size() > 1) {
			driver.switchTo().window(appWindow);
		}
		waitForUiToSettle();
	}

	private void assertSidebarVisible() {
		final List<By> sidebarLocators = Arrays.asList(By.xpath("//aside"),
				By.xpath("//nav[.//*[contains(normalize-space(), 'Negocio')]]"),
				By.xpath("//*[@role='navigation']"));

		boolean found = false;
		for (final By locator : sidebarLocators) {
			if (!driver.findElements(locator).isEmpty() && driver.findElement(locator).isDisplayed()) {
				found = true;
				break;
			}
		}

		assertTrue("Left sidebar navigation should be visible.", found);
	}

	private void clickByAnyText(final String... texts) throws Exception {
		for (final String text : texts) {
			final List<By> clickLocators = Arrays.asList(
					By.xpath("//button[normalize-space()=" + toXPathLiteral(text) + " or contains(normalize-space(), "
							+ toXPathLiteral(text) + ")]"),
					By.xpath("//a[normalize-space()=" + toXPathLiteral(text) + " or contains(normalize-space(), "
							+ toXPathLiteral(text) + ")]"),
					By.xpath("//*[@role='button'][normalize-space()=" + toXPathLiteral(text)
							+ " or contains(normalize-space(), " + toXPathLiteral(text) + ")]"),
					By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + " or contains(normalize-space(), "
							+ toXPathLiteral(text) + ")]"));

			for (final By locator : clickLocators) {
				final WebElement element = findVisibleElement(locator, Duration.ofSeconds(2));
				if (element != null) {
					clickAndWait(element);
					return;
				}
			}
		}

		throw new AssertionError("Could not find clickable element using visible text candidates: "
				+ Arrays.toString(texts));
	}

	private void clickAndWait(final WebElement element) throws Exception {
		wait.until(ExpectedConditions.visibilityOf(element));
		scrollIntoView(element);

		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiToSettle();
	}

	private void assertVisibleByAnyText(final String message, final String... texts) {
		assertTrue(message, isAnyTextVisible(DEFAULT_TIMEOUT, texts));
	}

	private void assertVisibleInside(final WebElement container, final String text, final String message) {
		final List<WebElement> elements = container.findElements(By.xpath(
				".//*[normalize-space()=" + toXPathLiteral(text) + " or contains(normalize-space(), " + toXPathLiteral(text)
						+ ")]"));
		assertTrue(message, elements.stream().anyMatch(WebElement::isDisplayed));
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(byAnyVisibleText(text)));
			return true;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private boolean isAnyTextVisible(final Duration timeout, final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text, timeout)) {
				return true;
			}
		}

		return false;
	}

	private By byAnyVisibleText(final String... texts) {
		final List<String> clauses = new ArrayList<>();
		for (final String text : texts) {
			for (final String candidate : textVariants(text)) {
				clauses.add("normalize-space()=" + toXPathLiteral(candidate));
				clauses.add("contains(normalize-space(), " + toXPathLiteral(candidate) + ")");
			}
		}

		return By.xpath("//*[" + String.join(" or ", clauses) + "]");
	}

	private WebElement findVisibleElement(final By locator, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout)
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
		} catch (final Exception ignored) {
			return null;
		}
	}

	private WebElement findContainerByHeading(final String... headingTextCandidates) {
		final WebElement heading = wait
				.until(ExpectedConditions.visibilityOfElementLocated(byAnyVisibleText(headingTextCandidates)));
		try {
			return heading.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		} catch (final Exception ignored) {
			return driver.findElement(By.tagName("body"));
		}
	}

	private boolean hasBusinessList(final WebElement section) {
		final List<WebElement> listItems = section.findElements(By.xpath(".//li[normalize-space()]"));
		if (!listItems.isEmpty()) {
			return listItems.stream().anyMatch(WebElement::isDisplayed);
		}

		final List<WebElement> rows = section.findElements(By.xpath(".//tr[normalize-space()]"));
		if (!rows.isEmpty()) {
			return rows.stream().anyMatch(WebElement::isDisplayed);
		}

		final List<WebElement> cards = section.findElements(
				By.xpath(".//*[contains(@class,'business') or contains(@class,'negocio')][normalize-space()]"));
		return cards.stream().anyMatch(WebElement::isDisplayed);
	}

	private void waitForUiToSettle() throws Exception {
		wait.until(driver -> {
			final Object state = ((JavascriptExecutor) driver).executeScript("return document.readyState");
			return "complete".equals(state);
		});

		new WebDriverWait(driver, Duration.ofSeconds(10))
				.until(ExpectedConditions.invisibilityOfElementLocated(
						By.xpath("//*[contains(@class,'loading') or contains(@class,'spinner')]")));

		Thread.sleep(500);
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void openLoginPageIfConfigured() throws Exception {
		final String loginUrl = readTextConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl.trim());
			waitForUiToSettle();
			return;
		}

		final String currentUrl = driver.getCurrentUrl();
		if (currentUrl == null || currentUrl.isBlank() || currentUrl.startsWith("about:blank")) {
			throw new IllegalStateException(
					"Browser did not start on SaleADS login page. Set saleads.login.url/SALEADS_LOGIN_URL.");
		}
	}

	private WebDriver createWebDriver() throws Exception {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (readBooleanConfig("saleads.e2e.headless", "SALEADS_E2E_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = readTextConfig("saleads.selenium.remote.url", "SELENIUM_REMOTE_URL");
		if (remoteUrl != null && !remoteUrl.isBlank()) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}

		WebDriverManager.chromedriver().setup();
		return new ChromeDriver(options);
	}

	private Path createEvidenceDirectory() throws Exception {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT));
		final Path dir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private void captureScreenshot(final String checkpointName) throws Exception {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDir.resolve(checkpointName + ".png");
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureFullPageScreenshot(final String checkpointName) throws Exception {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final Number scrollHeightValue = (Number) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			final Number scrollWidthValue = (Number) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);");

			final int fullHeight = Math.max(originalSize.getHeight(), Math.min(12000, scrollHeightValue.intValue() + 200));
			final int fullWidth = Math.max(originalSize.getWidth(), scrollWidthValue.intValue());
			driver.manage().window().setSize(new Dimension(fullWidth, fullHeight));
			Thread.sleep(300);
			captureScreenshot(checkpointName);
		} finally {
			driver.manage().window().setSize(originalSize);
		}
	}

	private void captureScreenshotSafe(final String checkpointName) {
		try {
			if (driver != null) {
				captureScreenshot(checkpointName);
			}
		} catch (final Exception ignored) {
			// Best effort screenshot capture.
		}
	}

	private String buildFinalReport() {
		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio workflow report");
		lines.add("----------------------------------");
		lines.add("Login: " + stepStatus.get(STEP_LOGIN));
		lines.add("Mi Negocio menu: " + stepStatus.get(STEP_MI_NEGOCIO_MENU));
		lines.add("Agregar Negocio modal: " + stepStatus.get(STEP_AGREGAR_NEGOCIO_MODAL));
		lines.add("Administrar Negocios view: " + stepStatus.get(STEP_ADMINISTRAR_VIEW));
		lines.add("Informaci\u00F3n General: " + stepStatus.get(STEP_INFO_GENERAL));
		lines.add("Detalles de la Cuenta: " + stepStatus.get(STEP_DETALLES_CUENTA));
		lines.add("Tus Negocios: " + stepStatus.get(STEP_TUS_NEGOCIOS));
		lines.add("T\u00E9rminos y Condiciones: " + stepStatus.get(STEP_TERMINOS));
		lines.add("Pol\u00EDtica de Privacidad: " + stepStatus.get(STEP_PRIVACIDAD));

		lines.add("");
		lines.add("Step details:");
		for (final Map.Entry<String, String> detail : stepDetails.entrySet()) {
			lines.add("- " + detail.getKey() + ": " + detail.getValue());
		}

		lines.add("");
		lines.add("Evidence directory: " + evidenceDir.toAbsolutePath());
		if (!capturedUrls.isEmpty()) {
			lines.add("Captured legal URLs:");
			for (final Map.Entry<String, String> url : capturedUrls.entrySet()) {
				lines.add("- " + url.getKey() + ": " + url.getValue());
			}
		}

		return String.join(System.lineSeparator(), lines);
	}

	private String normalizeSpace(final String value) {
		if (value == null) {
			return "";
		}

		return value.replaceAll("\\s+", " ").trim();
	}

	private List<String> textVariants(final String text) {
		final LinkedHashSet<String> variants = new LinkedHashSet<>();
		variants.add(text);
		final String normalized = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		variants.add(normalized);
		return new ArrayList<>(variants);
	}

	private boolean containsLikelyPersonName(final String text, final String email) {
		if (text == null || text.isBlank()) {
			return false;
		}

		final String[] lines = text.split("\\R");
		for (final String rawLine : lines) {
			final String line = normalizeSpace(rawLine);
			if (line.isBlank()) {
				continue;
			}

			final String lowered = line.toLowerCase(Locale.ROOT);
			if (lowered.contains(email.toLowerCase(Locale.ROOT)) || lowered.contains("informacion general")
					|| lowered.contains("business plan") || lowered.contains("cambiar plan")) {
				continue;
			}

			if (line.matches(".*[A-Za-z].*") && line.split(" ").length >= 2 && line.length() >= 5) {
				return true;
			}
		}

		return false;
	}

	private String readTextConfig(final String propertyName, final String environmentVariable) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		final String environmentValue = System.getenv(environmentVariable);
		if (environmentValue != null && !environmentValue.isBlank()) {
			return environmentValue;
		}

		return null;
	}

	private boolean readBooleanConfig(final String propertyName, final String environmentVariable,
			final boolean defaultValue) {
		final String configuredValue = readTextConfig(propertyName, environmentVariable);
		if (configuredValue == null) {
			return defaultValue;
		}

		return Boolean.parseBoolean(configuredValue.trim());
	}

	private String slugify(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] characters = value.toCharArray();
		for (int i = 0; i < characters.length; i++) {
			final char character = characters[i];
			if (character == '\'') {
				builder.append("\"'\"");
			} else if (character == '\"') {
				builder.append("'\"'");
			} else {
				builder.append("'").append(character).append("'");
			}
			if (i < characters.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
