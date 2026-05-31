package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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
import org.openqa.selenium.io.FileHandler;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * This test is intentionally opt-in so existing parser CI jobs are not affected:
 * set SALEADS_RUN_E2E=true to execute.
 * </p>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEXT_NEGOCIO = "Negocio";
	private static final String TEXT_MI_NEGOCIO = "Mi Negocio";
	private static final String TEXT_AGREGAR_NEGOCIO = "Agregar Negocio";
	private static final String TEXT_ADMINISTRAR_NEGOCIOS = "Administrar Negocios";
	private static final String TEXT_CREAR_NUEVO_NEGOCIO = "Crear Nuevo Negocio";
	private static final String TEXT_NOMBRE_DEL_NEGOCIO = "Nombre del Negocio";
	private static final String TEXT_CUPO_NEGOCIOS = "Tienes 2 de 3 negocios";
	private static final String TEXT_CANCELAR = "Cancelar";
	private static final String TEXT_CREAR_NEGOCIO = "Crear Negocio";
	private static final String TEXT_INFORMACION_GENERAL = "Informaci\u00F3n General";
	private static final String TEXT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TEXT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String TEXT_SECCION_LEGAL = "Secci\u00F3n Legal";
	private static final String TEXT_BUSINESS_PLAN = "BUSINESS PLAN";
	private static final String TEXT_CAMBIAR_PLAN = "Cambiar Plan";
	private static final String TEXT_CUENTA_CREADA = "Cuenta creada";
	private static final String TEXT_ESTADO_ACTIVO = "Estado activo";
	private static final String TEXT_IDIOMA_SELECCIONADO = "Idioma seleccionado";
	private static final String TEXT_TERMINOS = "T\u00E9rminos y Condiciones";
	private static final String TEXT_POLITICA = "Pol\u00EDtica de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
			Pattern.CASE_INSENSITIVE);

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Informaci\u00F3n General", "Detalles de la Cuenta", "Tus Negocios",
			"T\u00E9rminos y Condiciones", "Pol\u00EDtica de Privacidad");

	private static final List<String> LOGIN_BUTTON_TEXTS = Arrays.asList("Sign in with Google", "Iniciar sesi\u00F3n con Google",
			"Iniciar sesion con Google", "Continuar con Google", "Login with Google", "Ingresar con Google");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private int screenshotCounter = 1;
	private final Map<String, Boolean> report = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final boolean runE2E = Boolean.parseBoolean(env("SALEADS_RUN_E2E", "false"));
		Assume.assumeTrue("Set SALEADS_RUN_E2E=true to execute this workflow.", runE2E);

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the current environment login page.",
				loginUrl != null && !loginUrl.isBlank());

		final ChromeOptions chromeOptions = new ChromeOptions();
		if (Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"))) {
			chromeOptions.addArguments("--headless=new");
		}
		chromeOptions.addArguments("--window-size=1920,1080", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(chromeOptions);
		wait = new WebDriverWait(driver, Duration.ofSeconds(intEnv("SALEADS_WAIT_SECONDS", 30)));

		screenshotDir = Paths.get("target", "saleads-mi-negocio-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
		Files.createDirectories(screenshotDir);

		for (final String field : REPORT_FIELDS) {
			report.put(field, Boolean.FALSE);
		}

		driver.get(loginUrl);
		waitForUiToLoad();
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
		runStep("Informaci\u00F3n General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("T\u00E9rminos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Pol\u00EDtica de Privacidad", this::stepValidatePoliticaPrivacidad);

		printFinalReport();
		assertNoFailedSteps();
	}

	private void stepLoginWithGoogle() throws IOException {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByAnyText(LOGIN_BUTTON_TEXTS);
		waitForUiToLoad();

		final String googleHandle = waitForNewHandle(handlesBeforeClick, Duration.ofSeconds(15));
		if (googleHandle != null) {
			driver.switchTo().window(googleHandle);
			waitForUiToLoad();
		}

		if (isTextVisible(GOOGLE_ACCOUNT_EMAIL, 8)) {
			clickByAnyText(Collections.singletonList(GOOGLE_ACCOUNT_EMAIL));
			waitForUiToLoad();
		}

		if (googleHandle != null) {
			waitUntil(() -> driver.getWindowHandles().contains(appHandle), Duration.ofSeconds(30),
					"Main app window was not available after Google selection.");
			if (driver.getWindowHandles().contains(googleHandle)) {
				driver.close();
			}
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		}

		Assert.assertTrue("Main application interface must appear after login.", isAppShellVisible());
		Assert.assertTrue("Left sidebar navigation must be visible after login.", isSidebarVisible());

		captureScreenshot("dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		expandMiNegocioMenuIfNeeded();

		Assert.assertTrue("Mi Negocio submenu should contain 'Agregar Negocio'.",
				isTextVisible(TEXT_AGREGAR_NEGOCIO, 8));
		Assert.assertTrue("Mi Negocio submenu should contain 'Administrar Negocios'.",
				isTextVisible(TEXT_ADMINISTRAR_NEGOCIOS, 8));

		captureScreenshot("mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByAnyText(Collections.singletonList(TEXT_AGREGAR_NEGOCIO));
		waitForUiToLoad();

		Assert.assertTrue("Modal title should show 'Crear Nuevo Negocio'.", isTextVisible(TEXT_CREAR_NUEVO_NEGOCIO, 8));
		Assert.assertNotNull("Field 'Nombre del Negocio' should be present.", findInputByLabel(TEXT_NOMBRE_DEL_NEGOCIO));
		Assert.assertTrue("Business counter text should be visible.", isTextVisible(TEXT_CUPO_NEGOCIOS, 8));
		Assert.assertTrue("'Cancelar' button should be present.", isTextVisible(TEXT_CANCELAR, 8));
		Assert.assertTrue("'Crear Negocio' button should be present.", isTextVisible(TEXT_CREAR_NEGOCIO, 8));

		captureScreenshot("agregar_negocio_modal");

		final WebElement input = findInputByLabel(TEXT_NOMBRE_DEL_NEGOCIO);
		if (input != null) {
			input.click();
			waitForUiToLoad();
			input.clear();
			input.sendKeys("Negocio Prueba Automatizacion");
		}

		clickByAnyText(Collections.singletonList(TEXT_CANCELAR));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioMenuIfNeeded();
		clickByAnyText(Collections.singletonList(TEXT_ADMINISTRAR_NEGOCIOS));
		waitForUiToLoad();

		Assert.assertTrue("Section 'Informaci\u00F3n General' should be visible.", isTextVisible(TEXT_INFORMACION_GENERAL, 10));
		Assert.assertTrue("Section 'Detalles de la Cuenta' should be visible.", isTextVisible(TEXT_DETALLES_CUENTA, 10));
		Assert.assertTrue("Section 'Tus Negocios' should be visible.", isTextVisible(TEXT_TUS_NEGOCIOS, 10));
		Assert.assertTrue("Section 'Secci\u00F3n Legal' should be visible.", isTextVisible(TEXT_SECCION_LEGAL, 10));

		captureScreenshot("administrar_negocios_page");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionContainer(TEXT_INFORMACION_GENERAL);
		final String sectionText = normalized(section.getText());

		Assert.assertTrue("User email should be visible in 'Informaci\u00F3n General'.",
				EMAIL_PATTERN.matcher(section.getText()).find());
		Assert.assertTrue("User name should be visible in 'Informaci\u00F3n General'.", hasLikelyUserName(section.getText()));
		Assert.assertTrue("'BUSINESS PLAN' should be visible.", containsNormalized(sectionText, TEXT_BUSINESS_PLAN));
		Assert.assertTrue("'Cambiar Plan' button should be visible.", isTextVisible(TEXT_CAMBIAR_PLAN, 8));
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = findSectionContainer(TEXT_DETALLES_CUENTA);
		final String sectionText = normalized(section.getText());

		Assert.assertTrue("'Cuenta creada' should be visible.", containsNormalized(sectionText, TEXT_CUENTA_CREADA));
		Assert.assertTrue("'Estado activo' should be visible.", containsNormalized(sectionText, TEXT_ESTADO_ACTIVO));
		Assert.assertTrue("'Idioma seleccionado' should be visible.",
				containsNormalized(sectionText, TEXT_IDIOMA_SELECCIONADO));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionContainer(TEXT_TUS_NEGOCIOS);
		final String sectionText = normalized(section.getText());

		Assert.assertTrue("Business list/container should be visible.", hasBusinessList(section));
		Assert.assertTrue("'Agregar Negocio' button should be visible in 'Tus Negocios'.",
				isTextVisible(TEXT_AGREGAR_NEGOCIO, 8));
		Assert.assertTrue("'Tienes 2 de 3 negocios' should be visible.", containsNormalized(sectionText, TEXT_CUPO_NEGOCIOS));
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		final String finalUrl = clickLegalLinkAndValidate(TEXT_TERMINOS, "terminos_y_condiciones");
		System.out.println("Final URL (" + TEXT_TERMINOS + "): " + finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		final String finalUrl = clickLegalLinkAndValidate(TEXT_POLITICA, "politica_de_privacidad");
		System.out.println("Final URL (" + TEXT_POLITICA + "): " + finalUrl);
	}

	private String clickLegalLinkAndValidate(final String linkText, final String screenshotName) throws IOException {
		final String appHandle = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByAnyText(Collections.singletonList(linkText));
		waitForUiToLoad();

		final String legalHandle = waitForNewHandle(handlesBeforeClick, Duration.ofSeconds(8));
		if (legalHandle != null) {
			driver.switchTo().window(legalHandle);
			waitForUiToLoad();
		} else {
			waitUntil(() -> !Objects.equals(driver.getCurrentUrl(), originalUrl) || isTextVisible(linkText, 5),
					Duration.ofSeconds(15), "Legal page did not navigate after clicking '" + linkText + "'.");
		}

		Assert.assertTrue("Legal page heading '" + linkText + "' should be visible.", isTextVisible(linkText, 12));
		Assert.assertTrue("Legal content text should be visible.", hasLegalContentText());

		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (legalHandle != null) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
			if (!Objects.equals(driver.getWindowHandle(), appHandle)) {
				driver.switchTo().window(appHandle);
			}
		}

		return finalUrl;
	}

	private void expandMiNegocioMenuIfNeeded() {
		if (isTextVisible(TEXT_AGREGAR_NEGOCIO, 2) && isTextVisible(TEXT_ADMINISTRAR_NEGOCIOS, 2)) {
			return;
		}

		if (isTextVisible(TEXT_MI_NEGOCIO, 4)) {
			clickByAnyText(Collections.singletonList(TEXT_MI_NEGOCIO));
			waitForUiToLoad();
		}

		if (!isTextVisible(TEXT_AGREGAR_NEGOCIO, 2) || !isTextVisible(TEXT_ADMINISTRAR_NEGOCIOS, 2)) {
			clickByAnyText(Collections.singletonList(TEXT_NEGOCIO));
			waitForUiToLoad();
		}

		if (!isTextVisible(TEXT_AGREGAR_NEGOCIO, 2) || !isTextVisible(TEXT_ADMINISTRAR_NEGOCIOS, 2)) {
			clickByAnyText(Collections.singletonList(TEXT_MI_NEGOCIO));
			waitForUiToLoad();
		}
	}

	private void runStep(final String stepName, final StepExecutable stepExecutable) {
		try {
			stepExecutable.run();
			report.put(stepName, Boolean.TRUE);
		} catch (final Throwable error) {
			report.put(stepName, Boolean.FALSE);
			System.err.println("Step failed: " + stepName + " -> " + error.getMessage());
			try {
				captureScreenshot("failure_" + slug(stepName));
			} catch (final IOException ignored) {
				// Best effort evidence capture.
			}
		}
	}

	private void printFinalReport() {
		System.out.println("===== SaleADS Mi Negocio Final Report =====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		System.out.println("===========================================");
	}

	private void assertNoFailedSteps() {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue()) {
				failedSteps.add(entry.getKey());
			}
		}

		if (!failedSteps.isEmpty()) {
			Assert.fail("Failed workflow sections: " + String.join(", ", failedSteps));
		}
	}

	private void clickByAnyText(final List<String> texts) {
		RuntimeException lastError = null;
		for (final String text : texts) {
			try {
				clickByText(text);
				return;
			} catch (final RuntimeException runtimeException) {
				lastError = runtimeException;
			}
		}

		throw new AssertionError("Unable to click any of these labels: " + texts, lastError);
	}

	private void clickByText(final String text) {
		for (final By locator : clickableLocators(text)) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (!isDisplayedSafe(element)) {
					continue;
				}

				scrollIntoView(element);
				try {
					wait.until(ExpectedConditions.elementToBeClickable(element));
					element.click();
					waitForUiToLoad();
					return;
				} catch (final Exception clickError) {
					try {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
						waitForUiToLoad();
						return;
					} catch (final Exception jsError) {
						// Try next element candidate.
					}
				}
			}
		}

		throw new NoSuchElementException("No clickable element found for text: " + text);
	}

	private List<By> clickableLocators(final String text) {
		final String literal = toXPathLiteral(text);
		final String containsLower = normalizedContainsXPath(text);
		return Arrays.asList(
				By.xpath("//button[normalize-space()=" + literal + " or " + containsLower + "]"),
				By.xpath("//a[normalize-space()=" + literal + " or " + containsLower + "]"),
				By.xpath("//*[@role='button' and (normalize-space()=" + literal + " or " + containsLower + ")]"),
				By.xpath("//*[contains(@class,'btn') and (normalize-space()=" + literal + " or " + containsLower + ")]"),
				By.xpath(
						"//*[self::span or self::div or self::p][normalize-space()=" + literal + " or " + containsLower + "]/ancestor::*[self::button or self::a or @role='button'][1]"),
				By.xpath("//*[normalize-space()=" + literal + " or " + containsLower + "]"));
	}

	private boolean isTextVisible(final String text, final int timeoutSeconds) {
		final String literal = toXPathLiteral(text);
		final By textLocator = By.xpath("//*[normalize-space()=" + literal + " or " + normalizedContainsXPath(text) + "]");
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(d -> {
				for (final WebElement element : d.findElements(textLocator)) {
					if (isDisplayedSafe(element)) {
						return true;
					}
				}
				return false;
			});
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private String waitForNewHandle(final Set<String> existingHandles, final Duration timeout) {
		final long timeoutMillis = timeout.toMillis();
		final long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < timeoutMillis) {
			for (final String handle : driver.getWindowHandles()) {
				if (!existingHandles.contains(handle)) {
					return handle;
				}
			}
			sleep(250);
		}
		return null;
	}

	private boolean isAppShellVisible() {
		final List<WebElement> mainCandidates = driver.findElements(By.cssSelector("main, [role='main'], aside, nav"));
		for (final WebElement candidate : mainCandidates) {
			if (isDisplayedSafe(candidate)) {
				return true;
			}
		}
		return isTextVisible(TEXT_NEGOCIO, 5);
	}

	private boolean isSidebarVisible() {
		for (final WebElement sidebar : driver.findElements(By.xpath("//aside|//nav"))) {
			if (isDisplayedSafe(sidebar) && containsNormalized(sidebar.getText(), TEXT_NEGOCIO)) {
				return true;
			}
		}
		return isTextVisible(TEXT_NEGOCIO, 5);
	}

	private WebElement findInputByLabel(final String labelText) {
		final String labelLiteral = toXPathLiteral(labelText);
		final List<WebElement> labels = driver
				.findElements(By.xpath("//label[contains(normalize-space(), " + labelLiteral + ")]"));

		for (final WebElement label : labels) {
			if (!isDisplayedSafe(label)) {
				continue;
			}

			final String forAttribute = label.getAttribute("for");
			if (forAttribute != null && !forAttribute.isBlank()) {
				final List<WebElement> target = driver.findElements(By.id(forAttribute));
				if (!target.isEmpty() && isDisplayedSafe(target.get(0))) {
					return target.get(0);
				}
			}

			final List<WebElement> nestedInputs = label.findElements(By.xpath(".//input|.//textarea"));
			if (!nestedInputs.isEmpty() && isDisplayedSafe(nestedInputs.get(0))) {
				return nestedInputs.get(0);
			}

			final List<WebElement> siblingInputs = label.findElements(By.xpath("following::input[1] | following::textarea[1]"));
			if (!siblingInputs.isEmpty() && isDisplayedSafe(siblingInputs.get(0))) {
				return siblingInputs.get(0);
			}
		}

		final List<WebElement> byPlaceholder = driver.findElements(
				By.xpath("//input[contains(@placeholder," + labelLiteral + ")] | //textarea[contains(@placeholder," + labelLiteral + ")]"));
		if (!byPlaceholder.isEmpty() && isDisplayedSafe(byPlaceholder.get(0))) {
			return byPlaceholder.get(0);
		}

		return null;
	}

	private WebElement findSectionContainer(final String sectionHeading) {
		final String literal = toXPathLiteral(sectionHeading);
		final By headingLocator = By.xpath(
				"//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::div or self::span or self::p][contains(normalize-space(),"
						+ literal + ")]");

		final WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(headingLocator));
		for (final String ancestor : Arrays.asList("./ancestor::section[1]", "./ancestor::article[1]", "./ancestor::main[1]",
				"./ancestor::div[1]")) {
			final List<WebElement> candidates = heading.findElements(By.xpath(ancestor));
			if (!candidates.isEmpty() && isDisplayedSafe(candidates.get(0))) {
				return candidates.get(0);
			}
		}

		return heading;
	}

	private boolean hasLikelyUserName(final String text) {
		final String normalizedText = normalized(text);
		final List<String> lines = Arrays.asList(normalizedText.split("\\R"));
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}

			if (line.contains("@") || line.equals(normalized(TEXT_INFORMACION_GENERAL)) || line.equals(normalized(TEXT_BUSINESS_PLAN))
					|| line.equals(normalized(TEXT_CAMBIAR_PLAN))) {
				continue;
			}

			if (line.matches(".*[a-zA-Z].*") && line.length() > 2) {
				return true;
			}
		}

		return false;
	}

	private boolean hasBusinessList(final WebElement section) {
		final List<WebElement> explicitListElements = section
				.findElements(By.xpath(".//li | .//tr | .//*[contains(@class,'business') or contains(@class,'negocio')]"));
		if (!explicitListElements.isEmpty()) {
			for (final WebElement element : explicitListElements) {
				if (isDisplayedSafe(element)) {
					return true;
				}
			}
		}

		final String text = normalized(section.getText());
		final List<String> lines = Arrays.asList(text.split("\\R"));
		int meaningfulLines = 0;
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}

			if (trimmed.equals(normalized(TEXT_TUS_NEGOCIOS)) || trimmed.equals(normalized(TEXT_AGREGAR_NEGOCIO))
					|| trimmed.equals(normalized(TEXT_CUPO_NEGOCIOS))) {
				continue;
			}

			meaningfulLines++;
		}
		return meaningfulLines > 0;
	}

	private boolean hasLegalContentText() {
		final WebElement body = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
		final String legalText = body.getText() == null ? "" : body.getText().trim();
		return legalText.length() > 200;
	}

	private void captureScreenshot(final String name) throws IOException {
		final String filename = String.format("%02d_%s.png", screenshotCounter++, slug(name));
		final Path destination = screenshotDir.resolve(filename);
		FileHandler.copy(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE), destination.toFile());
		System.out.println("Screenshot saved: " + destination.toAbsolutePath());
	}

	private void scrollIntoView(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript(
					"arguments[0].scrollIntoView({behavior:'instant',block:'center',inline:'center'});", element);
		} catch (final Exception ignored) {
			// Non-blocking helper.
		}
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"))));
		} catch (final Exception ignored) {
			// Keep going to avoid overfitting to one environment.
		}
		sleep(350);
	}

	private void waitUntil(final BooleanSupplier condition, final Duration timeout, final String errorMessage) {
		final long start = System.currentTimeMillis();
		final long timeoutMillis = timeout.toMillis();
		while (System.currentTimeMillis() - start <= timeoutMillis) {
			if (condition.getAsBoolean()) {
				return;
			}
			sleep(250);
		}
		throw new AssertionError(errorMessage);
	}

	private String normalizedContainsXPath(final String text) {
		final String lowercaseText = normalized(text);
		final String loweredXPath = "translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ\u00C1\u00C9\u00CD\u00D3\u00DA\u00DC\u00D1','abcdefghijklmnopqrstuvwxyz\u00E1\u00E9\u00ED\u00F3\u00FA\u00FC\u00F1')";
		return "contains(" + loweredXPath + "," + toXPathLiteral(lowercaseText) + ")";
	}

	private boolean isDisplayedSafe(final WebElement element) {
		try {
			return element != null && element.isDisplayed();
		} catch (final Exception ignored) {
			return false;
		}
	}

	private String toXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder literal = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part = String.valueOf(chars[i]);
			if (i > 0) {
				literal.append(',');
			}
			if ("'".equals(part)) {
				literal.append("\"'\"");
			} else if ("\"".equals(part)) {
				literal.append("'\"'");
			} else {
				literal.append('\'').append(part).append('\'');
			}
		}
		literal.append(')');
		return literal.toString();
	}

	private String slug(final String value) {
		return normalized(value).replaceAll("[^a-z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
	}

	private String normalized(final String value) {
		if (value == null) {
			return "";
		}
		final String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return withoutAccents.toLowerCase(Locale.ROOT).trim();
	}

	private boolean containsNormalized(final String text, final String expectedFragment) {
		return normalized(text).contains(normalized(expectedFragment));
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String env(final String name, final String defaultValue) {
		final String value = System.getenv(name);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private int intEnv(final String name, final int defaultValue) {
		try {
			return Integer.parseInt(env(name, String.valueOf(defaultValue)));
		} catch (final NumberFormatException numberFormatException) {
			return defaultValue;
		}
	}

	@FunctionalInterface
	private interface StepExecutable {
		void run() throws Exception;
	}
}
