package io.proleap.saleads.e2e;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile(".+@.+\\..+");
	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_WAIT_TIMEOUT = Duration.ofSeconds(6);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> failureDetails = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		evidenceDir = Paths.get("target", "surefire-reports", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		driver = createDriver();
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
		final String baseUrl = property("saleads.url");
		if (!isBlank(baseUrl)) {
			driver.get(baseUrl);
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		printFinalReport();
		Assert.assertTrue("One or more validations failed. See report in test output.", allStepsPassed());
	}

	private void stepLoginWithGoogle() throws Exception {
		assertDriverContextReady();

		final Set<String> beforeClickHandles = driver.getWindowHandles();
		final WebElement loginButton = waitForVisibleTextAny(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Iniciar sesion con Google",
				"Continuar con Google",
				"Ingresar con Google",
				"Login with Google");
		clickAndWait(loginButton);
		selectGoogleAccountIfVisible(GOOGLE_EMAIL, beforeClickHandles);

		waitForVisibleTextAny("Negocio", "Mi Negocio", "Dashboard", "Inicio");
		waitForSidebar();
		captureScreenshot("01-dashboard-loaded");
	}

	private void selectGoogleAccountIfVisible(final String email, final Set<String> preLoginHandles) {
		final String startingHandle = driver.getWindowHandle();
		final Optional<String> potentialGoogleHandle = waitForPotentialNewWindow(preLoginHandles);
		if (potentialGoogleHandle.isPresent()) {
			driver.switchTo().window(potentialGoogleHandle.get());
			waitForUiLoad();
		}

		try {
			final Optional<WebElement> accountOption = findVisibleAny(
					By.xpath("//*[contains(normalize-space(.), " + xpathString(email) + ")]"),
					By.xpath("//div[@data-identifier=" + xpathString(email) + "]"));
			if (accountOption.isPresent()) {
				clickAndWait(accountOption.get());
			} else {
				// Fallback for the Google login form path when account chooser is not shown.
				final Optional<WebElement> emailInput = findVisibleAny(
						By.xpath("//input[@type='email' or @name='identifier']"));
				if (emailInput.isPresent()) {
					emailInput.get().click();
					emailInput.get().clear();
					emailInput.get().sendKeys(email);
					final Optional<WebElement> nextButton = findVisibleAny(
							By.xpath("//button//*[contains(normalize-space(.), 'Siguiente')]/ancestor::button[1]"),
							By.xpath("//button//*[contains(normalize-space(.), 'Next')]/ancestor::button[1]"),
							By.xpath("//div[@id='identifierNext']"));
					nextButton.ifPresent(this::clickAndWait);
				}
			}
		} catch (final Exception ignored) {
			// Account selector is optional, since active sessions can bypass it.
		}

		if (!driver.getWindowHandles().contains(startingHandle)) {
			return;
		}
		driver.switchTo().window(startingHandle);
		waitForUiLoad();
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		waitForSidebar();
		clickByVisibleTextAny("Negocio");
		clickByVisibleTextAny("Mi Negocio");
		waitForVisibleTextAny("Agregar Negocio");
		waitForVisibleTextAny("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleTextAny("Agregar Negocio");
		waitForVisibleTextAny("Crear Nuevo Negocio");
		waitForVisibleTextAny("Nombre del Negocio");
		waitForVisibleTextAny("Tienes 2 de 3 negocios");
		waitForVisibleTextAny("Cancelar");
		waitForVisibleTextAny("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final Optional<WebElement> nameInput = findVisibleAny(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio' or @name='businessName']"));
		if (nameInput.isPresent()) {
			nameInput.get().click();
			nameInput.get().clear();
			nameInput.get().sendKeys("Negocio Prueba Automatizacion");
		}

		clickByVisibleTextAny("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioMenuExpanded();
		clickByVisibleTextAny("Administrar Negocios");
		waitForUiLoad();

		waitForVisibleTextAny("Información General", "Informacion General");
		waitForVisibleTextAny("Detalles de la Cuenta", "Detalles de la cuenta");
		waitForVisibleTextAny("Tus Negocios");
		waitForVisibleTextAny("Sección Legal", "Seccion Legal");
		captureScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		waitForVisibleTextAny("Información General", "Informacion General");
		assertTextVisibleAny("BUSINESS PLAN");
		assertTextVisibleAny("Cambiar Plan");

		final String pageText = visibleBodyText();
		Assert.assertTrue("Expected visible user email.", EMAIL_PATTERN.matcher(pageText).find());
		Assert.assertTrue("Expected likely visible user name in account information section.", hasLikelyUserName(pageText));
	}

	private void stepValidateDetallesCuenta() throws Exception {
		assertTextVisibleAny("Cuenta creada");
		assertTextVisibleAny("Estado activo");
		assertTextVisibleAny("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() throws Exception {
		waitForVisibleTextAny("Tus Negocios");
		assertTextVisibleAny("Agregar Negocio");
		assertTextVisibleAny("Tienes 2 de 3 negocios");

		final String sectionText = sectionTextByHeading("Tus Negocios");
		Assert.assertTrue("Expected visible business list in 'Tus Negocios' section.", hasListLikeContent(sectionText));
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		validateLegalLink("Términos y Condiciones", "Terminos y Condiciones", "08-terminos-y-condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		validateLegalLink("Política de Privacidad", "Politica de Privacidad", "09-politica-de-privacidad");
	}

	private void validateLegalLink(final String expectedHeading, final String headingWithoutAccent, final String screenshotName)
			throws Exception {
		final Set<String> beforeHandles = driver.getWindowHandles();
		final String sourceHandle = driver.getWindowHandle();
		clickByVisibleTextAny(expectedHeading, headingWithoutAccent);

		final String targetHandle = waitForPotentialNewWindow(beforeHandles).orElse(sourceHandle);
		if (!sourceHandle.equals(targetHandle)) {
			driver.switchTo().window(targetHandle);
		}
		waitForUiLoad();

		assertTextVisibleAny(expectedHeading, headingWithoutAccent);
		Assert.assertTrue("Expected legal content text to be visible.", visibleBodyText().trim().length() > 120);

		captureScreenshot(screenshotName);
		legalUrls.put(expectedHeading, driver.getCurrentUrl());

		if (!sourceHandle.equals(targetHandle)) {
			driver.close();
			driver.switchTo().window(sourceHandle);
			waitForUiLoad();
			return;
		}

		driver.navigate().back();
		waitForUiLoad();
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, Boolean.TRUE);
		} catch (final Exception ex) {
			report.put(stepName, Boolean.FALSE);
			failureDetails.add(stepName + ": " + ex.getMessage());
			safeCaptureScreenshot("FAILED-" + sanitize(stepName));
		}
	}

	private boolean allStepsPassed() {
		for (final Boolean value : report.values()) {
			if (!Boolean.TRUE.equals(value)) {
				return false;
			}
		}
		return true;
	}

	private void printFinalReport() {
		System.out.println("==== saleads_mi_negocio_full_test report ====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.printf("%s: %s%n", entry.getKey(), entry.getValue() ? "PASS" : "FAIL");
		}
		for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
			System.out.printf("%s URL: %s%n", legalUrl.getKey(), legalUrl.getValue());
		}
		if (!failureDetails.isEmpty()) {
			System.out.println("---- Failure details ----");
			for (final String detail : failureDetails) {
				System.out.println(detail);
			}
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private void ensureMiNegocioMenuExpanded() throws Exception {
		if (isTextVisible("Agregar Negocio") && isTextVisible("Administrar Negocios")) {
			return;
		}
		clickByVisibleTextAny("Mi Negocio");
		waitForVisibleTextAny("Agregar Negocio");
		waitForVisibleTextAny("Administrar Negocios");
	}

	private void waitForSidebar() {
		wait.until(driver -> {
			final List<WebElement> sidebars = driver.findElements(By.xpath("//aside | //nav"));
			for (final WebElement sidebar : sidebars) {
				if (sidebar.isDisplayed() && sidebar.getText() != null && !sidebar.getText().trim().isEmpty()) {
					return true;
				}
			}
			return false;
		});
	}

	private void clickByVisibleTextAny(final String... texts) throws Exception {
		final WebElement element = waitForVisibleTextAny(texts);
		clickAndWait(element);
	}

	private WebElement waitForVisibleTextAny(final String... texts) {
		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_WAIT_TIMEOUT);
		for (final String text : texts) {
			try {
				return shortWait.until(driver -> firstDisplayedElement(text).orElse(null));
			} catch (final TimeoutException ignored) {
				// Try the next candidate text.
			}
		}
		throw new TimeoutException("Could not find visible element with any text: " + Arrays.toString(texts));
	}

	private Optional<WebElement> firstDisplayedElement(final String text) {
		final String xpath = "//*[self::button or self::a or self::span or self::div or self::p or self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::h6 or self::label or self::li]"
				+ "[contains(normalize-space(.), " + xpathString(text) + ")]";
		final List<WebElement> elements = driver.findElements(By.xpath(xpath));
		for (final WebElement element : elements) {
			if (isInteractable(element)) {
				return Optional.of(element);
			}
		}
		return Optional.empty();
	}

	private Optional<WebElement> findVisibleAny(final By... locators) {
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (isInteractable(element)) {
					return Optional.of(element);
				}
			}
		}
		return Optional.empty();
	}

	private boolean isTextVisible(final String text) {
		return firstDisplayedElement(text).isPresent();
	}

	private void assertTextVisibleAny(final String... texts) {
		waitForVisibleTextAny(texts);
	}

	private void clickAndWait(final WebElement element) {
		scrollIntoView(element);
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiLoad();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
	}

	private void waitForUiLoad() {
		wait.until((ExpectedCondition<Boolean>) wd -> {
			try {
				final Object state = ((JavascriptExecutor) wd).executeScript("return document.readyState");
				return state != null && "complete".equals(state.toString());
			} catch (final Exception ex) {
				return false;
			}
		});
	}

	private Optional<String> waitForPotentialNewWindow(final Set<String> existingHandles) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(d -> d.getWindowHandles().size() > existingHandles.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!existingHandles.contains(handle)) {
					return Optional.of(handle);
				}
			}
		} catch (final TimeoutException ignored) {
			// Link likely opened in the same tab.
		}
		return Optional.empty();
	}

	private String sectionTextByHeading(final String headingText) {
		final String xpath = "//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::h6 or self::p or self::span]"
				+ "[contains(normalize-space(.), " + xpathString(headingText) + ")]";
		final List<WebElement> headings = driver.findElements(By.xpath(xpath));
		for (final WebElement heading : headings) {
			if (!heading.isDisplayed()) {
				continue;
			}
			final List<WebElement> sectionAncestors = heading.findElements(By.xpath("./ancestor::section[1]"));
			if (!sectionAncestors.isEmpty() && sectionAncestors.get(0).isDisplayed()) {
				return sectionAncestors.get(0).getText();
			}
			final List<WebElement> divAncestors = heading.findElements(By.xpath("./ancestor::div[1]"));
			if (!divAncestors.isEmpty() && divAncestors.get(0).isDisplayed()) {
				return divAncestors.get(0).getText();
			}
		}
		return visibleBodyText();
	}

	private String visibleBodyText() {
		return wait.until(driver -> {
			final WebElement body = driver.findElement(By.tagName("body"));
			final String text = body.getText();
			return text == null ? "" : text;
		});
	}

	private boolean hasLikelyUserName(final String text) {
		final String normalized = text.replace('\r', '\n');
		final List<String> ignoreTokens = Arrays.asList(
				"información general",
				"informacion general",
				"business plan",
				"cambiar plan",
				"cuenta creada",
				"estado activo",
				"idioma seleccionado",
				"tus negocios",
				"sección legal",
				"seccion legal");

		for (final String rawLine : normalized.split("\n")) {
			final String line = rawLine.trim();
			if (line.length() < 4 || line.length() > 120) {
				continue;
			}
			if (line.contains("@") || line.matches(".*\\d{2,}.*")) {
				continue;
			}
			final String lower = line.toLowerCase(Locale.ROOT);
			boolean ignored = false;
			for (final String token : ignoreTokens) {
				if (lower.contains(token)) {
					ignored = true;
					break;
				}
			}
			if (!ignored && line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private boolean hasListLikeContent(final String sectionText) {
		final String[] lines = sectionText.split("\n");
		int nonEmpty = 0;
		for (final String line : lines) {
			if (!line.trim().isEmpty()) {
				nonEmpty++;
			}
		}
		return nonEmpty >= 3;
	}

	private void captureScreenshot(final String name) throws IOException {
		final Path destination = evidenceDir.resolve(name + ".png");
		final byte[] image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(destination, image);
	}

	private void safeCaptureScreenshot(final String name) {
		try {
			captureScreenshot(name);
		} catch (final Exception ignored) {
			// Suppress screenshot exceptions so we do not mask test failures.
		}
	}

	private WebDriver createDriver() throws MalformedURLException {
		final String remoteUrl = property("saleads.remoteUrl");
		final String attachDebuggerAddress = property("saleads.chromeDebuggerAddress");
		final boolean headless = Boolean.parseBoolean(propertyOrDefault("saleads.headless", "false"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (headless) {
			options.addArguments("--headless=new");
		}
		if (!isBlank(attachDebuggerAddress)) {
			options.setExperimentalOption("debuggerAddress", attachDebuggerAddress);
		}

		if (!isBlank(remoteUrl)) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}
		return new ChromeDriver(options);
	}

	private void assertDriverContextReady() {
		final String currentUrl = driver.getCurrentUrl();
		if (isBlank(property("saleads.url")) && (currentUrl == null || currentUrl.startsWith("data:") || "about:blank".equals(currentUrl))) {
			Assert.fail("Browser is not on the SaleADS login page. Set -Dsaleads.url=<current-env-login-url> "
					+ "or attach to an already-open browser using -Dsaleads.chromeDebuggerAddress=host:port.");
		}
	}

	private String property(final String key) {
		return System.getProperty(key);
	}

	private String propertyOrDefault(final String key, final String defaultValue) {
		final String value = System.getProperty(key);
		return value == null ? defaultValue : value;
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private boolean isInteractable(final WebElement element) {
		try {
			return element != null && element.isDisplayed() && element.getRect().height > 0 && element.getRect().width > 0;
		} catch (final Exception ex) {
			return false;
		}
	}

	private String sanitize(final String value) {
		return value.replaceAll("[^A-Za-z0-9._-]+", "_");
	}

	private String xpathString(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
