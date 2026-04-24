package io.proleap.cobol.e2e.saleads;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
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

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String REPORT_INFORMACION_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_POLITICA = "Política de Privacidad";
	private static final String ACCENTED_AND_CASED_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÑÜabcdefghijklmnopqrstuvwxyzáéíóúñü";
	private static final String NORMALIZED_LOWERCASE = "abcdefghijklmnopqrstuvwxyzaeiounuabcdefghijklmnopqrstuvwxyzaeiounu";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> urlEvidence = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String enabledRaw = firstNonBlank(
				System.getProperty("saleads.e2e.enabled"),
				System.getenv("SALEADS_E2E_ENABLED"));
		final boolean enabled = Boolean.parseBoolean(enabledRaw == null ? "false" : enabledRaw);
		Assume.assumeTrue("SaleADS E2E test is disabled. Set -Dsaleads.e2e.enabled=true to run it.", enabled);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--window-size=1920,1080");

		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(getTimeoutSeconds()));

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		screenshotDir = Path.of("target", "saleads-evidence", runId);
		Files.createDirectories(screenshotDir);

		final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		if (loginUrl == null) {
			throw new IllegalStateException(
					"Missing login URL. Set -Dsaleads.login.url=<url> or SALEADS_LOGIN_URL in the environment.");
		}

		driver.get(loginUrl);
		waitForUiToSettle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		executeStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		executeStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		executeStep(REPORT_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		executeStep(REPORT_ADMINISTRAR_NEGOCIOS, this::stepOpenAdministrarNegociosAndValidatePage);
		executeStep(REPORT_INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		executeStep(REPORT_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		executeStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		executeStep(REPORT_TERMINOS, this::stepValidateTerminosCondiciones);
		executeStep(REPORT_POLITICA, this::stepValidatePoliticaPrivacidad);

		final String finalReport = buildFinalReport();
		System.out.println(finalReport);

		if (!failures.isEmpty()) {
			Assert.fail(finalReport);
		}
	}

	private void stepLoginWithGoogle() {
		clickByText(
				"Sign in with Google",
				"Iniciar sesion con Google",
				"Iniciar sesión con Google",
				"Continuar con Google",
				"Acceder con Google",
				"Google");

		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");

		waitForAnyVisible(
				byText("Negocio"),
				byText("Mi Negocio"),
				By.cssSelector("aside"),
				By.cssSelector("nav"));

		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		waitForAnyVisible(byText("Negocio"), By.cssSelector("aside"));
		clickByText("Negocio");
		clickByText("Mi Negocio");

		waitForAnyVisible(byText("Agregar Negocio"), byText("Administrar Negocios"));
		assertVisible(byText("Agregar Negocio"), "Agregar Negocio should be visible after opening Mi Negocio.");
		assertVisible(byText("Administrar Negocios"),
				"Administrar Negocios should be visible after opening Mi Negocio.");

		takeScreenshot("02-mi-negocio-expanded-menu");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByText("Agregar Negocio");

		final WebElement title = waitForVisible(byText("Crear Nuevo Negocio"));
		assertVisible(title, "Modal title 'Crear Nuevo Negocio' is not visible.");
		assertVisible(byText("Nombre del Negocio"), "Field label 'Nombre del Negocio' was not found.");
		assertVisible(byText("Tienes 2 de 3 negocios"), "Business quota text was not found.");
		assertVisible(byText("Cancelar"), "Cancelar button is missing.");
		assertVisible(byText("Crear Negocio"), "Crear Negocio button is missing.");

		takeScreenshot("03-agregar-negocio-modal");

		final Optional<WebElement> nombreInput = findOptional(
				By.xpath("//input[contains(@placeholder,'Nombre') or contains(@aria-label,'Nombre')]"));
		nombreInput.ifPresent(input -> {
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatizacion");
			waitForUiToSettle();
		});

		clickByText("Cancelar");
		waitForInvisibility(byText("Crear Nuevo Negocio"));
	}

	private void stepOpenAdministrarNegociosAndValidatePage() {
		expandMiNegocioIfCollapsed();
		clickByText("Administrar Negocios");

		assertVisible(byText("Información General"), "Section 'Información General' is not visible.");
		assertVisible(byText("Detalles de la Cuenta"), "Section 'Detalles de la Cuenta' is not visible.");
		assertVisible(byText("Tus Negocios"), "Section 'Tus Negocios' is not visible.");
		assertVisible(byText("Sección Legal"), "Section 'Sección Legal' is not visible.");

		takeFullPageScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = sectionByHeader("Información General");
		final String text = section.getText();

		assertContains(text, "BUSINESS PLAN", "Text 'BUSINESS PLAN' was not found in Información General.");
		assertContains(text, "Cambiar Plan", "Button text 'Cambiar Plan' was not found in Información General.");
		assertEmailPresent(text, "User email is not visible in Información General.");

		final String expectedName = firstNonBlank(System.getProperty("saleads.user.name"), System.getenv("SALEADS_USER_NAME"));
		if (expectedName != null) {
			assertContains(text, expectedName, "Expected user name was not found in Información General.");
		} else {
			assertPotentialNamePresent(text, "No probable user name found in Información General section text.");
		}
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = sectionByHeader("Detalles de la Cuenta");
		final String text = section.getText();

		assertContains(text, "Cuenta creada", "'Cuenta creada' is missing.");
		assertContains(text, "Estado activo", "'Estado activo' is missing.");
		assertContains(text, "Idioma seleccionado", "'Idioma seleccionado' is missing.");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = sectionByHeader("Tus Negocios");
		final String text = section.getText();

		assertFalse(text.trim().isEmpty(), "Business list section appears empty.");
		assertContains(text, "Agregar Negocio", "Button 'Agregar Negocio' is missing in Tus Negocios.");
		assertContains(text, "Tienes 2 de 3 negocios", "Business quota text is missing in Tus Negocios.");
	}

	private void stepValidateTerminosCondiciones() {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByText("Terminos y Condiciones", "Términos y Condiciones");
		waitForUiToSettle();

		final boolean switchedToNewTab = switchToNewTabIfOpened(handlesBefore);
		waitForAnyVisible(byText("Terminos y Condiciones"), byText("Términos y Condiciones"), By.tagName("h1"),
				By.tagName("main"));

		assertVisible(byText("Términos y Condiciones"), "Heading 'Términos y Condiciones' is missing.");
		assertLegalContentPresent("Legal content text was not visible for Terminos y Condiciones.");

		takeScreenshot("05-terminos-y-condiciones");
		urlEvidence.put(REPORT_TERMINOS, driver.getCurrentUrl());

		returnToAppTab(appHandle, switchedToNewTab);
	}

	private void stepValidatePoliticaPrivacidad() {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByText("Politica de Privacidad", "Política de Privacidad");
		waitForUiToSettle();

		final boolean switchedToNewTab = switchToNewTabIfOpened(handlesBefore);
		waitForAnyVisible(byText("Politica de Privacidad"), byText("Política de Privacidad"), By.tagName("h1"),
				By.tagName("main"));

		assertVisible(byText("Política de Privacidad"), "Heading 'Política de Privacidad' is missing.");
		assertLegalContentPresent("Legal content text was not visible for Politica de Privacidad.");

		takeScreenshot("06-politica-de-privacidad");
		urlEvidence.put(REPORT_POLITICA, driver.getCurrentUrl());

		returnToAppTab(appHandle, switchedToNewTab);
	}

	private void executeStep(final String stepName, final Runnable stepAction) {
		try {
			stepAction.run();
			report.put(stepName, true);
		} catch (final Throwable throwable) {
			report.put(stepName, false);
			failures.add(stepName + " -> " + throwable.getMessage());
			takeScreenshot("FAILED-" + sanitize(stepName));
		}
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		final String appHandle = driver.getWindowHandle();
		waitBriefly(Duration.ofSeconds(2));

		boolean switched = false;
		for (final String handle : driver.getWindowHandles()) {
			if (!handle.equals(appHandle)) {
				driver.switchTo().window(handle);
				switched = true;
				break;
			}
		}

		final Optional<WebElement> account = findOptional(byText(accountEmail));
		account.ifPresent(this::clickAndWait);

		if (switched) {
			try {
				wait.until(ExpectedConditions.numberOfWindowsToBe(1));
				driver.switchTo().window(appHandle);
			} catch (final TimeoutException ignored) {
				driver.switchTo().window(appHandle);
			}
		}
	}

	private void returnToAppTab(final String appHandle, final boolean switchedToNewTab) {
		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToSettle();
			return;
		}

		try {
			driver.navigate().back();
			waitForUiToSettle();
		} catch (final Exception ignored) {
			driver.switchTo().window(appHandle);
		}
	}

	private void expandMiNegocioIfCollapsed() {
		if (!isElementVisible(byText("Administrar Negocios"), Duration.ofSeconds(3))) {
			clickByText("Negocio");
			clickByText("Mi Negocio");
			waitForAnyVisible(byText("Administrar Negocios"));
		}
	}

	private boolean switchToNewTabIfOpened(final Set<String> handlesBefore) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(6))
					.until(webDriver -> webDriver.getWindowHandles().size() > handlesBefore.size());
		} catch (final TimeoutException ignored) {
			return false;
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToSettle();
				return true;
			}
		}

		return false;
	}

	private void clickByText(final String... texts) {
		TimeoutException lastTimeout = null;
		for (final String text : texts) {
			try {
				final WebElement clickable = wait.until(ExpectedConditions.elementToBeClickable(byClickableText(text)));
				clickAndWait(clickable);
				return;
			} catch (final TimeoutException timeout) {
				lastTimeout = timeout;
			} catch (final StaleElementReferenceException stale) {
				final WebElement clickable = wait.until(ExpectedConditions.elementToBeClickable(byClickableText(text)));
				clickAndWait(clickable);
				return;
			}
		}
		throw new NoSuchElementException("Could not click any element with texts: " + String.join(", ", texts), lastTimeout);
	}

	private void clickAndWait(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToSettle();
	}

	private void waitForUiToSettle() {
		wait.until((ExpectedCondition<Boolean>) webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		wait.until(driver -> {
			final Object activeRequests = ((JavascriptExecutor) driver)
					.executeScript("return (window.jQuery && window.jQuery.active) ? window.jQuery.active : 0;");
			return String.valueOf(activeRequests).equals("0");
		});
	}

	private void waitForInvisibility(final By locator) {
		wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
	}

	private void waitForAnyVisible(final By... locators) {
		wait.until(driver -> {
			for (final By locator : locators) {
				if (firstVisibleElement(locator).isPresent()) {
					return true;
				}
			}
			return false;
		});
	}

	private WebElement waitForVisible(final By locator) {
		wait.until(driver -> firstVisibleElement(locator).isPresent());
		return firstVisibleElement(locator)
				.orElseThrow(() -> new NoSuchElementException("No visible element found for locator " + locator));
	}

	private Optional<WebElement> findOptional(final By locator) {
		try {
			return firstVisibleElement(locator);
		} catch (final Exception ignored) {
			return Optional.empty();
		}
	}

	private WebElement sectionByHeader(final String headerText) {
		final By headingLocator = byText(headerText);
		final WebElement heading = waitForVisible(headingLocator);

		final Object section = ((JavascriptExecutor) driver).executeScript(
				"const node = arguments[0];"
						+ "return node.closest('section,article,div,main') || node.parentElement;",
				heading);
		if (section instanceof WebElement) {
			return (WebElement) section;
		}

		return heading;
	}

	private void takeScreenshot(final String fileName) {
		try {
			final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path target = screenshotDir.resolve(sanitize(fileName) + ".png");
			Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
			System.out.println("Screenshot saved: " + target.toAbsolutePath());
		} catch (final Exception screenshotError) {
			failures.add("Failed to capture screenshot " + fileName + ": " + screenshotError.getMessage());
		}
	}

	private void takeFullPageScreenshot(final String fileName) {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			driver.manage().window().setSize(new Dimension(1920, 3000));
			waitForUiToSettle();
			takeScreenshot(fileName);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToSettle();
		}
	}

	private boolean isElementVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(driver -> firstVisibleElement(locator).isPresent());
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void assertVisible(final By locator, final String messageIfMissing) {
		if (!isElementVisible(locator, Duration.ofSeconds(getTimeoutSeconds()))) {
			throw new AssertionError(messageIfMissing);
		}
	}

	private void assertVisible(final WebElement element, final String messageIfMissing) {
		if (element == null || !element.isDisplayed()) {
			throw new AssertionError(messageIfMissing);
		}
	}

	private void assertContains(final String source, final String expected, final String messageIfMissing) {
		if (source == null || !normalize(source).contains(normalize(expected))) {
			throw new AssertionError(messageIfMissing);
		}
	}

	private void assertEmailPresent(final String text, final String messageIfMissing) {
		final Matcher matcher = EMAIL_PATTERN.matcher(text);
		if (!matcher.find()) {
			throw new AssertionError(messageIfMissing);
		}
	}

	private void assertPotentialNamePresent(final String text, final String messageIfMissing) {
		final String normalized = normalize(text);
		if (normalized.contains("informacion general") && normalized.replace("informacion general", "").trim().length() > 10) {
			return;
		}
		throw new AssertionError(messageIfMissing);
	}

	private void assertLegalContentPresent(final String messageIfMissing) {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		if (bodyText == null || bodyText.trim().length() < 80) {
			throw new AssertionError(messageIfMissing);
		}
	}

	private void assertFalse(final boolean condition, final String messageIfTrue) {
		if (condition) {
			throw new AssertionError(messageIfTrue);
		}
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append(System.lineSeparator());
		builder.append("=== SaleADS Mi Negocio Full Test Report ===").append(System.lineSeparator());
		for (final String key : List.of(
				REPORT_LOGIN,
				REPORT_MI_NEGOCIO_MENU,
				REPORT_AGREGAR_NEGOCIO_MODAL,
				REPORT_ADMINISTRAR_NEGOCIOS,
				REPORT_INFORMACION_GENERAL,
				REPORT_DETALLES_CUENTA,
				REPORT_TUS_NEGOCIOS,
				REPORT_TERMINOS,
				REPORT_POLITICA)) {
			final String status = Boolean.TRUE.equals(report.get(key)) ? "PASS" : "FAIL";
			builder.append("- ").append(key).append(": ").append(status).append(System.lineSeparator());
		}

		if (!urlEvidence.isEmpty()) {
			builder.append(System.lineSeparator()).append("URL Evidence").append(System.lineSeparator());
			urlEvidence.forEach((key, value) -> builder.append("- ").append(key).append(": ").append(value)
					.append(System.lineSeparator()));
		}

		builder.append(System.lineSeparator())
				.append("Screenshots directory: ")
				.append(screenshotDir.toAbsolutePath())
				.append(System.lineSeparator());

		if (!failures.isEmpty()) {
			builder.append(System.lineSeparator()).append("Failures").append(System.lineSeparator());
			for (final String failure : failures) {
				builder.append("- ").append(failure).append(System.lineSeparator());
			}
		}
		return builder.toString();
	}

	private By byText(final String text) {
		return By.xpath("//*[contains(" + normalizeXpathNode(".") + ", " + xpathText(normalize(text)) + ")]");
	}

	private By byClickableText(final String text) {
		final String normalized = xpathText(normalize(text));
		return By.xpath(
				"(//button[contains(" + normalizeXpathNode(".") + ", " + normalized + ")]"
						+ " | //a[contains(" + normalizeXpathNode(".") + ", " + normalized + ")]"
						+ " | //*[@role='button' and contains(" + normalizeXpathNode(".") + ", "
						+ normalized + ")]"
						+ " | //*[contains(@class,'btn') and contains(" + normalizeXpathNode(".") + ", "
						+ normalized + ")])[1]");
	}

	private String xpathText(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final String[] parts = text.split("'");
		final StringBuilder expression = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				expression.append(", \"'\", ");
			}
			expression.append("'").append(parts[i]).append("'");
		}
		expression.append(")");
		return expression.toString();
	}

	private String normalizeXpathNode(final String nodeExpression) {
		return "normalize-space(translate(" + nodeExpression + ", '" + ACCENTED_AND_CASED_ALPHABET + "', '" + NORMALIZED_LOWERCASE
				+ "'))";
	}

	private String normalize(final String value) {
		return value == null ? "" : value
				.replace("Á", "A")
				.replace("É", "E")
				.replace("Í", "I")
				.replace("Ó", "O")
				.replace("Ú", "U")
				.replace("Ñ", "N")
				.replace("Ü", "U")
				.replace("á", "a")
				.replace("é", "e")
				.replace("í", "i")
				.replace("ó", "o")
				.replace("ú", "u")
				.replace("ñ", "n")
				.replace("ü", "u")
				.toLowerCase();
	}

	private String sanitize(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9._-]+", "-");
	}

	private int getTimeoutSeconds() {
		return Integer.parseInt(System.getProperty("saleads.timeout.seconds", "25"));
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

	private void waitBriefly(final Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private Optional<WebElement> firstVisibleElement(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			try {
				if (element.isDisplayed()) {
					return Optional.of(element);
				}
			} catch (final StaleElementReferenceException ignored) {
				// DOM can update while polling; keep searching.
			}
		}
		return Optional.empty();
	}
}
