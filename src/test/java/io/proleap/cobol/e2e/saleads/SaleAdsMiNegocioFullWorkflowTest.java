package io.proleap.cobol.e2e.saleads;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class SaleAdsMiNegocioFullWorkflowTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String STEP_POLITICA = "Pol\u00edtica de Privacidad";

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = configurationValue("SALEADS_LOGIN_URL", "saleads.login.url");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL env var or saleads.login.url system property to run this test.",
				loginUrl != null && !loginUrl.trim().isEmpty()
		);

		final boolean headless = configurationFlag("SALEADS_HEADLESS", "saleads.headless", true);
		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		evidenceDir = Paths.get(
				"target",
				"saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT))
		).toAbsolutePath();
		Files.createDirectories(evidenceDir);

		driver.get(loginUrl.trim());
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		writeSummaryReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() throws IOException {
		runStep(STEP_LOGIN, this::validateLoginWithGoogle);
		runStep(STEP_MI_NEGOCIO_MENU, this::validateMiNegocioMenu);
		runStep(STEP_AGREGAR_NEGOCIO_MODAL, this::validateAgregarNegocioModal);
		runStep(STEP_ADMINISTRAR_NEGOCIOS_VIEW, this::validateAdministrarNegociosView);
		runStep(STEP_INFO_GENERAL, this::validateInformacionGeneral);
		runStep(STEP_DETALLES_CUENTA, this::validateDetallesCuenta);
		runStep(STEP_TUS_NEGOCIOS, this::validateTusNegocios);
		runStep(STEP_TERMINOS, this::validateTerminosYCondiciones);
		runStep(STEP_POLITICA, this::validatePoliticaDePrivacidad);

		final boolean allPassed = stepResults.values().stream().allMatch(Boolean::booleanValue);
		Assert.assertTrue("One or more SaleADS validations failed. See target/saleads-evidence report.", allPassed);
	}

	private void validateLoginWithGoogle() throws IOException {
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final boolean clickedLogin = clickFirstVisibleText(Arrays.asList(
				"Sign in with Google",
				"Iniciar sesion con Google",
				"Iniciar sesion con google",
				"Continuar con Google",
				"Login with Google",
				"Google"
		));
		Assert.assertTrue("Google login button was not found.", clickedLogin);

		handleGoogleAccountSelectorIfPresent(handlesBeforeClick);

		final boolean dashboardLoaded = waitForAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio"), Duration.ofSeconds(60));
		Assert.assertTrue("Main application interface did not appear.", dashboardLoaded);
		Assert.assertTrue("Left sidebar navigation is not visible.", isSidebarVisible());
		captureScreenshot("01-dashboard-loaded");
	}

	private void validateMiNegocioMenu() throws IOException {
		Assert.assertTrue("Sidebar section 'Negocio' is not visible.", waitForAnyVisibleText(Arrays.asList("Negocio"), DEFAULT_WAIT));

		final boolean clickedMiNegocio = clickFirstVisibleText(Arrays.asList("Mi Negocio"));
		Assert.assertTrue("'Mi Negocio' option was not clickable.", clickedMiNegocio);

		Assert.assertTrue("'Agregar Negocio' is not visible.", waitForAnyVisibleText(Arrays.asList("Agregar Negocio"), DEFAULT_WAIT));
		Assert.assertTrue("'Administrar Negocios' is not visible.", waitForAnyVisibleText(Arrays.asList("Administrar Negocios"), DEFAULT_WAIT));
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		final boolean clickedAgregar = clickFirstVisibleText(Arrays.asList("Agregar Negocio"));
		Assert.assertTrue("Could not click 'Agregar Negocio'.", clickedAgregar);

		Assert.assertTrue("Modal title 'Crear Nuevo Negocio' not visible.",
				waitForAnyVisibleText(Arrays.asList("Crear Nuevo Negocio"), DEFAULT_WAIT));
		Assert.assertNotNull("Input field 'Nombre del Negocio' not found.", findNombreDelNegocioInput());
		Assert.assertTrue("Expected quota text is missing.",
				waitForAnyVisibleText(Arrays.asList("Tienes 2 de 3 negocios"), DEFAULT_WAIT));
		Assert.assertTrue("'Cancelar' button missing.", waitForAnyVisibleText(Arrays.asList("Cancelar"), DEFAULT_WAIT));
		Assert.assertTrue("'Crear Negocio' button missing.", waitForAnyVisibleText(Arrays.asList("Crear Negocio"), DEFAULT_WAIT));
		captureScreenshot("03-agregar-negocio-modal");

		final WebElement nameInput = findNombreDelNegocioInput();
		if (nameInput != null) {
			nameInput.click();
			nameInput.clear();
			nameInput.sendKeys("Negocio Prueba Automatizacion");
			waitForUiToLoad();
		}

		clickFirstVisibleText(Arrays.asList("Cancelar"));
		waitForUiToLoad();
	}

	private void validateAdministrarNegociosView() throws IOException {
		if (!waitForAnyVisibleText(Arrays.asList("Administrar Negocios"), Duration.ofSeconds(5))) {
			clickFirstVisibleText(Arrays.asList("Mi Negocio"));
		}

		final boolean clickedAdministrar = clickFirstVisibleText(Arrays.asList("Administrar Negocios"));
		Assert.assertTrue("Could not click 'Administrar Negocios'.", clickedAdministrar);

		Assert.assertTrue("Section 'Informacion General' missing.",
				waitForAnyVisibleText(Arrays.asList("Informacion General", "Informaci\u00f3n General"), DEFAULT_WAIT));
		Assert.assertTrue("Section 'Detalles de la Cuenta' missing.",
				waitForAnyVisibleText(Arrays.asList("Detalles de la Cuenta"), DEFAULT_WAIT));
		Assert.assertTrue("Section 'Tus Negocios' missing.",
				waitForAnyVisibleText(Arrays.asList("Tus Negocios"), DEFAULT_WAIT));
		Assert.assertTrue("Section 'Seccion Legal' missing.",
				waitForAnyVisibleText(Arrays.asList("Seccion Legal", "Secci\u00f3n Legal"), DEFAULT_WAIT));
		captureScreenshot("04-administrar-negocios-page");
	}

	private void validateInformacionGeneral() {
		final WebElement section = findSectionByHeading(Arrays.asList("Informacion General", "Informaci\u00f3n General"));
		Assert.assertNotNull("Could not find 'Informacion General' section.", section);

		final String text = section.getText();
		final boolean hasEmail = EMAIL_PATTERN.matcher(text).find();
		final boolean hasLikelyName = hasLikelyUserName(text);

		Assert.assertTrue("User name is not visible in Informacion General.", hasLikelyName);
		Assert.assertTrue("User email is not visible in Informacion General.", hasEmail);
		Assert.assertTrue("'BUSINESS PLAN' text is missing in Informacion General.",
				text.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN"));
		Assert.assertTrue("'Cambiar Plan' button is not visible.",
				findVisibleElementByText(Arrays.asList("Cambiar Plan")).isPresent());
	}

	private void validateDetallesCuenta() {
		final WebElement section = findSectionByHeading(Arrays.asList("Detalles de la Cuenta"));
		Assert.assertNotNull("Could not find 'Detalles de la Cuenta' section.", section);
		final String text = section.getText();

		Assert.assertTrue("'Cuenta creada' is not visible.", containsIgnoreCase(text, "Cuenta creada"));
		Assert.assertTrue("'Estado activo' is not visible.", containsIgnoreCase(text, "Estado activo"));
		Assert.assertTrue("'Idioma seleccionado' is not visible.", containsIgnoreCase(text, "Idioma seleccionado"));
	}

	private void validateTusNegocios() {
		final WebElement section = findSectionByHeading(Arrays.asList("Tus Negocios"));
		Assert.assertNotNull("Could not find 'Tus Negocios' section.", section);
		final String text = section.getText();

		Assert.assertTrue("Business list block is not visible.", text.length() > "Tus Negocios".length() + 20);
		Assert.assertTrue("'Agregar Negocio' button is not visible in business section.",
				findVisibleElementByText(Arrays.asList("Agregar Negocio")).isPresent());
		Assert.assertTrue("Expected quota text is missing in business section.",
				containsIgnoreCase(text, "Tienes 2 de 3 negocios"));
	}

	private void validateTerminosYCondiciones() throws IOException {
		validateLegalLink(
				Arrays.asList("Terminos y Condiciones", "T\u00e9rminos y Condiciones"),
				Arrays.asList("Terminos y Condiciones", "T\u00e9rminos y Condiciones"),
				"08-terminos-y-condiciones",
				"terminos_url"
		);
	}

	private void validatePoliticaDePrivacidad() throws IOException {
		validateLegalLink(
				Arrays.asList("Politica de Privacidad", "Pol\u00edtica de Privacidad"),
				Arrays.asList("Politica de Privacidad", "Pol\u00edtica de Privacidad"),
				"09-politica-de-privacidad",
				"politica_url"
		);
	}

	private void validateLegalLink(
			final List<String> linkTexts,
			final List<String> headingTexts,
			final String screenshotName,
			final String urlKey
	) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final String appUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		final boolean clicked = clickFirstVisibleText(linkTexts);
		Assert.assertTrue("Could not click legal link: " + linkTexts, clicked);

		switchToNewTabIfOpened(handlesBeforeClick);
		Assert.assertTrue("Legal page heading is not visible for: " + linkTexts,
				waitForAnyVisibleText(headingTexts, DEFAULT_WAIT));

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Legal page content appears empty.", bodyText != null && bodyText.trim().length() > 80);

		captureScreenshot(screenshotName);
		legalUrls.put(urlKey, driver.getCurrentUrl());

		final String currentWindow = driver.getWindowHandle();
		if (!currentWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else if (!driver.getCurrentUrl().equals(appUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void switchToNewTabIfOpened(final Set<String> handlesBeforeClick) {
		final WebDriverWait tabWait = new WebDriverWait(driver, Duration.ofSeconds(10));
		try {
			tabWait.until((ExpectedCondition<Boolean>) d -> d != null && d.getWindowHandles().size() > handlesBeforeClick.size());
		} catch (TimeoutException ignored) {
			return;
		}

		final Set<String> handlesAfterClick = driver.getWindowHandles();
		for (final String handle : handlesAfterClick) {
			if (!handlesBeforeClick.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void handleGoogleAccountSelectorIfPresent(final Set<String> handlesBeforeClick) {
		switchToNewTabIfOpened(handlesBeforeClick);

		final boolean googleAccountChooser = driver.getCurrentUrl().contains("accounts.google.com")
				|| waitForAnyVisibleText(Arrays.asList("Choose an account", "Elige una cuenta"), Duration.ofSeconds(10));

		if (!googleAccountChooser) {
			return;
		}

		final Optional<WebElement> accountOption = findVisibleElementByText(Arrays.asList(GOOGLE_ACCOUNT_EMAIL));
		if (accountOption.isPresent()) {
			clickElement(accountOption.get());
		}

		final List<String> handles = new ArrayList<>(driver.getWindowHandles());
		for (final String handle : handles) {
			driver.switchTo().window(handle);
			waitForUiToLoad();
			if (!driver.getCurrentUrl().contains("accounts.google.com")) {
				return;
			}
		}
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, true);
		} catch (final Throwable throwable) {
			stepResults.put(stepName, false);
			stepErrors.put(stepName, throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage());
		}
	}

	private Optional<WebElement> findVisibleElementByText(final List<String> texts) {
		for (final String text : texts) {
			final By locator = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return Optional.of(element);
				}
			}
		}
		return Optional.empty();
	}

	private boolean clickFirstVisibleText(final List<String> texts) {
		for (final String text : texts) {
			final Optional<WebElement> clickable = findFirstClickableByText(text);
			if (clickable.isPresent()) {
				clickElement(clickable.get());
				return true;
			}
		}
		return false;
	}

	private Optional<WebElement> findFirstClickableByText(final String text) {
		final String literal = xpathLiteral(text);
		final By locator = By.xpath("("
				+ "//*[self::button or self::a or @role='button' or @role='menuitem'][normalize-space()=" + literal + "]"
				+ " | //*[self::button or self::a or @role='button' or @role='menuitem'][.//*[normalize-space()=" + literal + "]]"
				+ " | //*[self::button or self::a or @role='button' or @role='menuitem'][contains(normalize-space(), " + literal + ")]"
				+ ")");

		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return Optional.of(element);
			}
		}
		return Optional.empty();
	}

	private WebElement findSectionByHeading(final List<String> headingTexts) {
		for (final String heading : headingTexts) {
			final By headingLocator = By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::h6 or self::span or self::p][contains(normalize-space(), "
					+ xpathLiteral(heading) + ")]");
			final List<WebElement> headingElements = driver.findElements(headingLocator);
			for (final WebElement headingElement : headingElements) {
				if (!headingElement.isDisplayed()) {
					continue;
				}
				final List<WebElement> candidates = headingElement.findElements(By.xpath("./ancestor::*[self::section or self::article or self::div]"));
				if (!candidates.isEmpty()) {
					return candidates.get(0);
				}
				return headingElement;
			}
		}
		return null;
	}

	private WebElement findNombreDelNegocioInput() {
		final List<By> locators = Arrays.asList(
				By.xpath("//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[@name='nombreNegocio' or @name='businessName']")
		);
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		return null;
	}

	private boolean waitForAnyVisibleText(final List<String> texts, final Duration timeout) {
		try {
			final WebDriverWait customWait = new WebDriverWait(driver, timeout);
			customWait.until(d -> {
				if (d == null) {
					return false;
				}
				for (final String text : texts) {
					final List<WebElement> elements = d.findElements(By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]"));
					for (final WebElement element : elements) {
						if (element.isDisplayed()) {
							return true;
						}
					}
				}
				return false;
			});
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private boolean isSidebarVisible() {
		final List<WebElement> sidebars = driver.findElements(By.xpath("//aside | //nav"));
		for (final WebElement sidebar : sidebars) {
			if (sidebar.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final RuntimeException ignored) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		try {
			wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(
					"//*[contains(@class,'loading') or contains(@class,'spinner') or contains(@class,'skeleton')]"
			)));
		} catch (final TimeoutException ignored) {
			// Some pages do not expose loaders consistently.
		}
		try {
			Thread.sleep(400L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final Path outputPath = evidenceDir.resolve(fileName + ".png");
		final byte[] data = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(outputPath, data);
	}

	private boolean hasLikelyUserName(final String text) {
		if (text == null) {
			return false;
		}
		if (containsIgnoreCase(text, "Nombre") || containsIgnoreCase(text, "Usuario") || containsIgnoreCase(text, "Name")) {
			return true;
		}

		final String[] lines = text.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty() || line.length() < 4 || line.contains("@")) {
				continue;
			}
			if (line.matches(".*\\d.*")) {
				continue;
			}
			if (line.split("\\s+").length >= 2 && Character.isLetter(line.charAt(0))) {
				return true;
			}
		}
		return false;
	}

	private boolean containsIgnoreCase(final String text, final String part) {
		return text != null && part != null && text.toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT));
	}

	private String configurationValue(final String envKey, final String systemPropertyKey) {
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue.trim();
		}
		final String propertyValue = System.getProperty(systemPropertyKey);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue.trim();
		}
		return null;
	}

	private boolean configurationFlag(final String envKey, final String systemPropertyKey, final boolean defaultValue) {
		final String configured = configurationValue(envKey, systemPropertyKey);
		if (configured == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(configured);
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String character = String.valueOf(chars[i]);
			if (i > 0) {
				builder.append(", ");
			}
			if ("'".equals(character)) {
				builder.append("\"").append(character).append("\"");
			} else {
				builder.append("'").append(character).append("'");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private void writeSummaryReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("");
		lines.add("Final report (PASS/FAIL by validation step):");
		lines.add("- " + STEP_LOGIN + ": " + statusOf(STEP_LOGIN));
		lines.add("- " + STEP_MI_NEGOCIO_MENU + ": " + statusOf(STEP_MI_NEGOCIO_MENU));
		lines.add("- " + STEP_AGREGAR_NEGOCIO_MODAL + ": " + statusOf(STEP_AGREGAR_NEGOCIO_MODAL));
		lines.add("- " + STEP_ADMINISTRAR_NEGOCIOS_VIEW + ": " + statusOf(STEP_ADMINISTRAR_NEGOCIOS_VIEW));
		lines.add("- " + STEP_INFO_GENERAL + ": " + statusOf(STEP_INFO_GENERAL));
		lines.add("- " + STEP_DETALLES_CUENTA + ": " + statusOf(STEP_DETALLES_CUENTA));
		lines.add("- " + STEP_TUS_NEGOCIOS + ": " + statusOf(STEP_TUS_NEGOCIOS));
		lines.add("- " + STEP_TERMINOS + ": " + statusOf(STEP_TERMINOS));
		lines.add("- " + STEP_POLITICA + ": " + statusOf(STEP_POLITICA));
		lines.add("");

		if (!legalUrls.isEmpty()) {
			lines.add("Captured legal URLs:");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				lines.add("- " + entry.getKey() + ": " + entry.getValue());
			}
			lines.add("");
		}

		if (!stepErrors.isEmpty()) {
			lines.add("Step errors:");
			for (final Map.Entry<String, String> error : stepErrors.entrySet()) {
				lines.add("- " + error.getKey() + ": " + error.getValue());
			}
		}

		final Path reportFile = evidenceDir.resolve("report.txt");
		Files.write(reportFile, lines);
		System.out.println(String.join(System.lineSeparator(), lines));
	}

	private String statusOf(final String step) {
		if (!stepResults.containsKey(step)) {
			return "FAIL";
		}
		return stepResults.get(step) ? "PASS" : "FAIL";
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
