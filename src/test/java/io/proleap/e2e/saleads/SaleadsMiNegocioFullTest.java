package io.proleap.e2e.saleads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
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

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String QUOTA_TEXT = "Tienes 2 de 3 negocios";
	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern.compile(".*@.*\\..*");

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor js;
	private Path evidenceDir;
	private Path screenshotsDir;

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();

	private String appWindowHandle;
	private String accountPageUrl;
	private String termsUrl = "";
	private String privacyUrl = "";

	@Before
	public void setUp() throws IOException {
		final boolean runE2E = getBooleanEnv("SALEADS_RUN_E2E", false);
		Assume.assumeTrue("Set SALEADS_RUN_E2E=true to run SaleADS E2E workflow test.", runE2E);

		evidenceDir = Paths.get("target", "saleads-evidence");
		screenshotsDir = evidenceDir.resolve("screenshots");
		Files.createDirectories(screenshotsDir);

		driver = createDriver();
		js = (JavascriptExecutor) driver;
		wait = new WebDriverWait(driver, Duration.ofSeconds(getIntEnv("SALEADS_TIMEOUT_SECONDS", 30)));

		driver.manage().window().setSize(new Dimension(1440, 900));
		driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90));

		final String startUrl = trimToNull(System.getenv("SALEADS_START_URL"));
		if (startUrl != null) {
			driver.get(startUrl);
			waitForPageLoad();
		}

		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep(STEP_LOGIN, this::stepLoginWithGoogle);
		runStep(STEP_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(STEP_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(STEP_ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		runStep(STEP_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(STEP_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(STEP_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(STEP_TERMINOS, () -> stepValidateLegalLink(STEP_TERMINOS, "step-08-terminos"));
		runStep(STEP_PRIVACIDAD, () -> stepValidateLegalLink(STEP_PRIVACIDAD, "step-09-privacidad"));

		final List<String> failures = new ArrayList<>();
		for (final Map.Entry<String, String> entry : stepStatus.entrySet()) {
			if (!"PASS".equals(entry.getValue())) {
				final String detail = stepDetails.getOrDefault(entry.getKey(), "(no detail)");
				failures.add(entry.getKey() + " -> " + entry.getValue() + ": " + detail);
			}
		}

		assertTrue("Validation failures:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private WebDriver createDriver() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--lang=es-419");
		options.addArguments("--window-size=1440,900");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (getBooleanEnv("SALEADS_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = trimToNull(System.getenv("SALEADS_SELENIUM_REMOTE_URL"));
		if (remoteUrl != null) {
			try {
				return new RemoteWebDriver(new URL(remoteUrl), options);
			} catch (final MalformedURLException e) {
				throw new IllegalArgumentException("SALEADS_SELENIUM_REMOTE_URL is not a valid URL: " + remoteUrl, e);
			}
		}

		return new ChromeDriver(options);
	}

	private void runStep(final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			stepStatus.put(stepName, "PASS");
		} catch (final Throwable t) {
			stepStatus.put(stepName, "FAIL");
			stepDetails.put(stepName, Objects.toString(t.getMessage(), t.getClass().getSimpleName()));
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		if (!isAnyTextVisible("Negocio", "Mi Negocio")) {
			clickFirstAvailableText("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google",
					"Ingresar con Google", "Google");

			final Set<String> handlesBeforeGoogleSelection = new LinkedHashSet<>(driver.getWindowHandles());
			waitForPageLoad();
			waitSmall();

			selectGoogleAccountIfVisible(DEFAULT_GOOGLE_ACCOUNT, handlesBeforeGoogleSelection);
			switchBackToAppWindow();
		}

		assertTrue("Main application interface was not detected after login.",
				isAnyTextVisible("Mi Negocio", "Negocio", "Administrar Negocios", "Agregar Negocio"));
		assertTrue("Left sidebar navigation was not detected.", isSidebarVisible());

		takeScreenshot("step-01-dashboard");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		assertTrue("Sidebar must be visible before opening menu.", isSidebarVisible());

		if (isAnyTextVisible("Negocio")) {
			clickByVisibleText("Negocio");
		}
		clickByVisibleText("Mi Negocio");

		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Administrar Negocios");

		takeScreenshot("step-02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");

		assertAnyVisibleText("Crear Nuevo Negocio");
		assertInputFieldPresent("Nombre del Negocio");
		assertAnyVisibleText(QUOTA_TEXT);
		assertAnyVisibleText("Cancelar");
		assertAnyVisibleText("Crear Negocio");

		takeScreenshot("step-03-agregar-negocio-modal");

		fillInputByLabel("Nombre del Negocio", "Negocio Prueba Automatizaci\u00f3n");
		clickByVisibleText("Cancelar");
		waitUntilTextNotVisible("Crear Nuevo Negocio");
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForPageLoad();

		assertAnyVisibleText("Informaci\u00f3n General");
		assertAnyVisibleText("Detalles de la Cuenta");
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Secci\u00f3n Legal");

		accountPageUrl = driver.getCurrentUrl();
		takeFullPageScreenshot("step-04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisibleText("Informaci\u00f3n General");
		assertAnyVisibleText("BUSINESS PLAN");
		assertAnyVisibleText("Cambiar Plan");

		final WebElement infoSection = locateSectionByHeading("Informaci\u00f3n General");
		final String sectionText = infoSection.getText();
		final String[] lines = sectionText.split("\\R");
		boolean hasEmail = false;
		boolean hasNameLikeText = false;

		for (final String line : lines) {
			final String normalized = line == null ? "" : line.trim();
			if (normalized.isEmpty()) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(normalized).matches()) {
				hasEmail = true;
				continue;
			}
			if (!containsAny(normalized, "Informaci\u00f3n General", "BUSINESS PLAN", "Cambiar Plan")
					&& normalized.matches(".*[A-Za-z].*")) {
				hasNameLikeText = true;
			}
		}

		assertTrue("Expected user email in Informaci\u00f3n General section.", hasEmail);
		assertTrue("Expected user name-like text in Informaci\u00f3n General section.", hasNameLikeText);
	}

	private void stepValidateDetallesCuenta() {
		assertAnyVisibleText("Detalles de la Cuenta");
		assertAnyVisibleText("Cuenta creada");
		assertAnyVisibleText("Estado activo");
		assertAnyVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText(QUOTA_TEXT);

		final WebElement section = locateSectionByHeading("Tus Negocios");
		final boolean hasListLikeContent = !section.findElements(By.xpath(
				".//li | .//table//tr | .//*[contains(translate(@class,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'business')]"
						+ " | .//*[contains(translate(@class,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'negocio')]"))
				.isEmpty();

		assertTrue("Expected visible business list/content in Tus Negocios section.", hasListLikeContent
				|| section.getText().matches("(?s).*Negocio.*"));
	}

	private void stepValidateLegalLink(final String label, final String screenshotName) throws IOException {
		final String originWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		clickByVisibleText(label);

		String legalWindow = originWindow;
		try {
			wait.until(d -> d.getWindowHandles().size() > handlesBefore.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					legalWindow = handle;
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			// navigation can happen in the same tab; this is expected in some environments.
		}

		if (!originWindow.equals(legalWindow)) {
			driver.switchTo().window(legalWindow);
			waitForPageLoad();
		}

		assertAnyVisibleText(label);
		assertTrue("Expected legal content text to be visible for " + label + ".",
				driver.findElement(By.tagName("body")).getText().trim().length() > 120);

		takeScreenshot(screenshotName);

		final String currentUrl = driver.getCurrentUrl();
		if (STEP_TERMINOS.equals(label)) {
			termsUrl = currentUrl;
		} else {
			privacyUrl = currentUrl;
		}

		if (!originWindow.equals(legalWindow)) {
			driver.close();
			driver.switchTo().window(originWindow);
			waitForPageLoad();
		} else if (accountPageUrl != null && !accountPageUrl.equals(currentUrl)) {
			driver.navigate().back();
			waitForPageLoad();
		}
	}

	private WebElement locateSectionByHeading(final String heading) {
		final String headingLiteral = toXPathLiteral(heading);
		final By sectionLocator = By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(),"
				+ headingLiteral
				+ ")]/ancestor::*[self::section or self::article or self::div][1]");
		return wait.until(driver -> {
			final List<WebElement> sections = driver.findElements(sectionLocator);
			for (final WebElement section : sections) {
				if (section.isDisplayed()) {
					return section;
				}
			}
			return null;
		});
	}

	private boolean isSidebarVisible() {
		final List<By> locators = Arrays.asList(By.tagName("aside"), By.xpath("//nav"),
				By.xpath("//*[contains(translate(@class,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'sidebar')]"));
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void clickFirstAvailableText(final String... candidateTexts) {
		AssertionError lastError = null;
		for (final String text : candidateTexts) {
			try {
				clickByVisibleText(text);
				return;
			} catch (final AssertionError e) {
				lastError = e;
			}
		}

		if (lastError == null) {
			throw new AssertionError("Unable to click any candidate text.");
		}
		throw lastError;
	}

	private void clickByVisibleText(final String text) {
		final WebElement element = wait.until(driver -> firstVisibleElementByText(text));
		assertTrue("Element with text '" + text + "' is not clickable.", element.isDisplayed());

		scrollIntoView(element);
		try {
			element.click();
		} catch (final Exception clickFailure) {
			js.executeScript("arguments[0].click();", element);
		}
		waitForPageLoad();
		waitSmall();
	}

	private WebElement firstVisibleElementByText(final String text) {
		final String textLiteral = toXPathLiteral(text);
		final By locator = By.xpath("//*[normalize-space()=" + textLiteral + " or contains(normalize-space()," + textLiteral
				+ ")]");
		final List<WebElement> elements = driver.findElements(locator);

		for (final WebElement element : elements) {
			if (!element.isDisplayed()) {
				continue;
			}
			final String tag = element.getTagName().toLowerCase();
			if ("button".equals(tag) || "a".equals(tag)
					|| "button".equalsIgnoreCase(element.getAttribute("role"))) {
				return element;
			}
			final List<WebElement> clickables = element
					.findElements(By.xpath("./ancestor-or-self::*[self::button or self::a or @role='button'][1]"));
			if (!clickables.isEmpty() && clickables.get(0).isDisplayed()) {
				return clickables.get(0);
			}
			return element;
		}

		return null;
	}

	private void selectGoogleAccountIfVisible(final String accountEmail, final Set<String> handlesBeforeGoogleSelection) {
		try {
			wait.until(d -> d.getWindowHandles().size() > handlesBeforeGoogleSelection.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBeforeGoogleSelection.contains(handle)) {
					driver.switchTo().window(handle);
					waitForPageLoad();
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			// Google flow can continue in the same tab. Keep current context.
		}

		if (isAnyTextVisible(accountEmail)) {
			clickByVisibleText(accountEmail);
			waitForPageLoad();
			return;
		}

		final List<WebElement> emailInputs = driver.findElements(By.id("identifierId"));
		if (!emailInputs.isEmpty() && emailInputs.get(0).isDisplayed()) {
			final WebElement emailInput = emailInputs.get(0);
			emailInput.clear();
			emailInput.sendKeys(accountEmail);
			emailInput.sendKeys(Keys.ENTER);
			waitForPageLoad();
		}
	}

	private void switchBackToAppWindow() {
		final Set<String> handles = driver.getWindowHandles();
		if (handles.contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForPageLoad();
			return;
		}

		for (final String handle : handles) {
			driver.switchTo().window(handle);
			final String url = Objects.toString(driver.getCurrentUrl(), "");
			if (!url.contains("accounts.google.com")) {
				appWindowHandle = handle;
				waitForPageLoad();
				return;
			}
		}
	}

	private void assertAnyVisibleText(final String text) {
		assertTrue("Expected visible text not found: " + text, isAnyTextVisible(text));
	}

	private boolean isAnyTextVisible(final String... candidateTexts) {
		for (final String text : candidateTexts) {
			final String textLiteral = toXPathLiteral(text);
			final By locator = By.xpath("//*[normalize-space()=" + textLiteral + " or contains(normalize-space()," + textLiteral
					+ ")]");
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void assertInputFieldPresent(final String labelText) {
		final String labelLiteral = toXPathLiteral(labelText);
		final By inputLocator = By.xpath("//label[contains(normalize-space()," + labelLiteral
				+ ")]/following::input[1] | //input[@placeholder=" + labelLiteral + "] | //input[@name='businessName']");
		assertFalse("Expected input field not found for label: " + labelText, driver.findElements(inputLocator).isEmpty());
	}

	private void fillInputByLabel(final String labelText, final String value) {
		final String labelLiteral = toXPathLiteral(labelText);
		final By inputLocator = By.xpath("//label[contains(normalize-space()," + labelLiteral
				+ ")]/following::input[1] | //input[@placeholder=" + labelLiteral + "] | //input[@name='businessName']");
		final List<WebElement> inputs = driver.findElements(inputLocator);
		if (inputs.isEmpty() || !inputs.get(0).isDisplayed()) {
			return;
		}

		final WebElement input = inputs.get(0);
		scrollIntoView(input);
		input.click();
		input.clear();
		input.sendKeys(value);
		waitSmall();
	}

	private void waitUntilTextNotVisible(final String text) {
		final String literal = toXPathLiteral(text);
		final By locator = By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")]");
		wait.until(d -> {
			final List<WebElement> elements = d.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return false;
				}
			}
			return true;
		});
	}

	private void waitForPageLoad() {
		final ExpectedCondition<Boolean> documentReady = d -> "complete"
				.equals(((JavascriptExecutor) d).executeScript("return document.readyState"));
		wait.until(documentReady);
	}

	private void waitSmall() {
		try {
			Thread.sleep(450);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void scrollIntoView(final WebElement element) {
		js.executeScript("arguments[0].scrollIntoView({block:'center', inline:'center'});", element);
	}

	private void takeScreenshot(final String baseName) throws IOException {
		final Path destination = screenshotsDir.resolve(baseName + ".png");
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(destination, screenshot);
	}

	private void takeFullPageScreenshot(final String baseName) throws IOException {
		final Dimension original = driver.manage().window().getSize();
		try {
			final Number fullHeight = (Number) js
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			final int targetHeight = Math.min(Math.max(fullHeight.intValue() + 120, original.getHeight()), 10000);
			final int targetWidth = Math.max(original.getWidth(), 1440);
			driver.manage().window().setSize(new Dimension(targetWidth, targetHeight));
			waitSmall();
			takeScreenshot(baseName);
		} finally {
			driver.manage().window().setSize(original);
			waitSmall();
		}
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final LinkedHashMap<String, String> finalStatus = new LinkedHashMap<>();
		finalStatus.put(STEP_LOGIN, stepStatus.getOrDefault(STEP_LOGIN, "NOT_RUN"));
		finalStatus.put(STEP_MI_NEGOCIO_MENU, stepStatus.getOrDefault(STEP_MI_NEGOCIO_MENU, "NOT_RUN"));
		finalStatus.put(STEP_AGREGAR_NEGOCIO_MODAL, stepStatus.getOrDefault(STEP_AGREGAR_NEGOCIO_MODAL, "NOT_RUN"));
		finalStatus.put(STEP_ADMINISTRAR_NEGOCIOS_VIEW, stepStatus.getOrDefault(STEP_ADMINISTRAR_NEGOCIOS_VIEW, "NOT_RUN"));
		finalStatus.put(STEP_INFO_GENERAL, stepStatus.getOrDefault(STEP_INFO_GENERAL, "NOT_RUN"));
		finalStatus.put(STEP_DETALLES_CUENTA, stepStatus.getOrDefault(STEP_DETALLES_CUENTA, "NOT_RUN"));
		finalStatus.put(STEP_TUS_NEGOCIOS, stepStatus.getOrDefault(STEP_TUS_NEGOCIOS, "NOT_RUN"));
		finalStatus.put(STEP_TERMINOS, stepStatus.getOrDefault(STEP_TERMINOS, "NOT_RUN"));
		finalStatus.put(STEP_PRIVACIDAD, stepStatus.getOrDefault(STEP_PRIVACIDAD, "NOT_RUN"));

		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"status\": {\n");
		int index = 0;
		for (final Map.Entry<String, String> entry : finalStatus.entrySet()) {
			json.append("    \"").append(jsonEscape(entry.getKey())).append("\": \"")
					.append(jsonEscape(entry.getValue())).append("\"");
			index++;
			json.append(index < finalStatus.size() ? ",\n" : "\n");
		}
		json.append("  },\n");
		json.append("  \"evidence\": {\n");
		json.append("    \"terms_url\": \"").append(jsonEscape(termsUrl)).append("\",\n");
		json.append("    \"privacy_url\": \"").append(jsonEscape(privacyUrl)).append("\",\n");
		json.append("    \"screenshots_dir\": \"").append(jsonEscape(screenshotsDir.toString())).append("\"\n");
		json.append("  },\n");
		json.append("  \"details\": {\n");
		int detailsIndex = 0;
		for (final Map.Entry<String, String> entry : stepDetails.entrySet()) {
			json.append("    \"").append(jsonEscape(entry.getKey())).append("\": \"")
					.append(jsonEscape(entry.getValue())).append("\"");
			detailsIndex++;
			json.append(detailsIndex < stepDetails.size() ? ",\n" : "\n");
		}
		json.append("  }\n");
		json.append("}\n");

		Files.write(evidenceDir.resolve("final-report.json"), json.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static String jsonEscape(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static int getIntEnv(final String envKey, final int defaultValue) {
		final String raw = trimToNull(System.getenv(envKey));
		if (raw == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(raw);
		} catch (final NumberFormatException ignored) {
			return defaultValue;
		}
	}

	private static boolean getBooleanEnv(final String envKey, final boolean defaultValue) {
		final String raw = trimToNull(System.getenv(envKey));
		return raw == null ? defaultValue : Boolean.parseBoolean(raw);
	}

	private static boolean containsAny(final String text, final String... values) {
		for (final String value : values) {
			if (text.contains(value)) {
				return true;
			}
		}
		return false;
	}

	private static String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part = String.valueOf(chars[i]);
			if (i > 0) {
				result.append(",");
			}
			if ("'".equals(part)) {
				result.append("\"").append(part).append("\"");
			} else {
				result.append("'").append(part).append("'");
			}
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
