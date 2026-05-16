package io.proleap.cobol.e2e;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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

public class SaleadsMiNegocioFullTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> detailReport = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;
	private String terminosFinalUrl;
	private String privacidadFinalUrl;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Provide saleads.login.url (or SALEADS_LOGIN_URL) for the current environment login page.", loginUrl != null);

		evidenceDir = Path.of("target", "saleads-evidence", TIMESTAMP_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-notifications");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--window-size=1920,1080");
		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "false"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(readWaitSeconds()));
		driver.get(loginUrl);
		waitForUiToLoad();
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
	public void saleadsMiNegocioFullWorkflowTest() {
		runStep("Login", this::loginWithGoogle);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegocios);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", () -> validateLegalLink("Términos y Condiciones", "Términos y Condiciones", true));
		runStep("Política de Privacidad", () -> validateLegalLink("Política de Privacidad", "Política de Privacidad", false));

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue()) {
				failedSteps.add(entry.getKey());
			}
		}

		Assert.assertTrue("Failed workflow steps: " + failedSteps, failedSteps.isEmpty());
	}

	private void loginWithGoogle() throws IOException {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Login with Google",
				"Google");

		chooseGoogleAccountIfPresent(GOOGLE_ACCOUNT_EMAIL);
		waitForMainInterface();
		appWindowHandle = driver.getWindowHandle();
		captureScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws IOException {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded-menu");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");

		assertAnyTextVisible("Crear Nuevo Negocio");
		waitForAnyVisible("Nombre del Negocio input",
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@name,'nombre') or contains(@id,'nombre')]"));
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertAnyTextVisible("Cancelar");
		assertAnyTextVisible("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final Optional<WebElement> nombreInput = findFirstVisible(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[contains(@name,'nombre') or contains(@id,'nombre')]"));
		if (nombreInput.isPresent()) {
			nombreInput.get().clear();
			nombreInput.get().sendKeys("Negocio Prueba Automatizacion");
		}

		clickByVisibleText("Cancelar");
	}

	private void openAdministrarNegocios() throws IOException {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}
		clickByVisibleText("Administrar Negocios");

		assertAnyTextVisible("Información General", "Informacion General");
		assertAnyTextVisible("Detalles de la Cuenta", "Detalles de la cuenta");
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Sección Legal", "Seccion Legal");
		captureScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		final String pageText = visibleBodyText();
		Assert.assertTrue("Expected a visible user email in Información General section.", EMAIL_PATTERN.matcher(pageText).find());

		assertAnyTextVisible("BUSINESS PLAN");
		assertAnyTextVisible("Cambiar Plan");

		final boolean hasNameLabel = isAnyTextVisible("Nombre", "Usuario", "Perfil");
		final boolean hasDisplayNameLine = pageText.lines().map(String::trim).filter(line -> line.length() >= 4)
				.anyMatch(line -> !line.contains("@") && !line.equalsIgnoreCase("Información General")
						&& !line.equalsIgnoreCase("Informacion General") && !line.equalsIgnoreCase("BUSINESS PLAN")
						&& !line.equalsIgnoreCase("Cambiar Plan"));
		Assert.assertTrue("Expected a visible user name in Información General.", hasNameLabel || hasDisplayNameLine);
	}

	private void validateDetallesDeLaCuenta() {
		assertAnyTextVisible("Cuenta creada");
		assertAnyTextVisible("Estado activo", "Activo");
		assertAnyTextVisible("Idioma seleccionado", "Idioma");
	}

	private void validateTusNegocios() {
		waitForAnyVisible("Business list in Tus Negocios",
				By.xpath("//*[contains(normalize-space(),'Tus Negocios')]/following::*[self::ul or self::table][1]"),
				By.xpath("//*[contains(normalize-space(),'Tus Negocios')]/following::*[contains(normalize-space(),'Negocio')][1]"),
				By.xpath("//*[contains(normalize-space(),'Tienes 2 de 3 negocios')]"));

		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");
	}

	private void validateLegalLink(final String linkText, final String headingText, final boolean isTerminos)
			throws IOException {
		final String sourceHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);

		final Optional<String> maybeNewHandle = waitForNewWindow(handlesBeforeClick, Duration.ofSeconds(8));
		final boolean openedNewTab = maybeNewHandle.isPresent();
		if (openedNewTab) {
			driver.switchTo().window(maybeNewHandle.get());
		}

		assertAnyTextVisible(headingText);
		final String legalPageText = visibleBodyText();
		Assert.assertTrue("Expected legal content text to be visible for " + linkText, legalPageText.length() > 120);

		final String evidenceName = isTerminos ? "05-terminos-y-condiciones" : "06-politica-de-privacidad";
		captureScreenshot(evidenceName);

		final String finalUrl = driver.getCurrentUrl();
		if (isTerminos) {
			terminosFinalUrl = finalUrl;
		} else {
			privacidadFinalUrl = finalUrl;
		}

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(sourceHandle);
		} else {
			driver.navigate().back();
		}

		if (appWindowHandle != null) {
			driver.switchTo().window(appWindowHandle);
		}
		waitForUiToLoad();
		assertAnyTextVisible("Información General", "Informacion General");
	}

	private void waitForMainInterface() {
		waitForAnyVisible("main application interface",
				By.tagName("aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar') or contains(@class,'Sidebar')]"));
	}

	private void chooseGoogleAccountIfPresent(final String accountEmail) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));
			final Optional<WebElement> accountTile = findFirstVisible(
					By.xpath("//*[contains(normalize-space()," + xpathLiteral(accountEmail) + ")]"));
			if (accountTile.isPresent()) {
				clickElement(accountTile.get());
				return;
			}

			final WebElement useAnotherAccount = shortWait.until(
					ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(),'Use another account')]")));
			clickElement(useAnotherAccount);
		} catch (final TimeoutException ignored) {
			// Account picker is not always shown when user session is already authenticated.
		}
		waitForUiToLoad();
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, true);
		} catch (final Throwable exception) {
			report.put(stepName, false);
			detailReport.put(stepName, exception.getMessage());
		}
	}

	private void clickByVisibleText(final String... texts) {
		final List<String> candidates = Arrays.asList(texts);
		Exception lastException = null;

		for (final String text : candidates) {
			final List<By> locators = List.of(
					By.xpath("//button[contains(normalize-space(), " + xpathLiteral(text) + ")]"),
					By.xpath("//a[contains(normalize-space(), " + xpathLiteral(text) + ")]"),
					By.xpath("//*[@role='button' and contains(normalize-space(), " + xpathLiteral(text) + ")]"),
					By.xpath("//*[self::span or self::div or self::p][contains(normalize-space(), " + xpathLiteral(text) + ")]"));

			for (final By locator : locators) {
				final Optional<WebElement> maybeElement = findFirstVisible(locator);
				if (maybeElement.isPresent()) {
					try {
						clickElement(maybeElement.get());
						waitForUiToLoad();
						return;
					} catch (final Exception clickError) {
						lastException = clickError;
					}
				}
			}
		}

		if (lastException != null) {
			throw new IllegalStateException("Could not click visible text options: " + candidates, lastException);
		}
		throw new IllegalStateException("Could not find visible text options: " + candidates);
	}

	private Optional<WebElement> findFirstVisible(final By... locators) {
		for (final By locator : locators) {
			final List<WebElement> candidates = driver.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					return Optional.of(candidate);
				}
			}
		}
		return Optional.empty();
	}

	private void clickElement(final WebElement element) {
		final JavascriptExecutor javascriptExecutor = (JavascriptExecutor) driver;
		javascriptExecutor.executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception standardClickFailure) {
			javascriptExecutor.executeScript("arguments[0].click();", element);
		}
	}

	private void waitForAnyVisible(final String description, final By... locators) {
		try {
			wait.until(driverRef -> findFirstVisible(locators).isPresent());
		} catch (final TimeoutException timeoutException) {
			throw new AssertionError("Expected visible element for: " + description, timeoutException);
		}
	}

	private void assertAnyTextVisible(final String... texts) {
		try {
			wait.until(driverRef -> isAnyTextVisible(texts));
		} catch (final TimeoutException timeoutException) {
			throw new AssertionError("Expected one of the texts to be visible: " + Arrays.toString(texts), timeoutException);
		}
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			final By locator = By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]");
			if (findFirstVisible(locator).isPresent()) {
				return true;
			}
		}
		return false;
	}

	private String visibleBodyText() {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
	}

	private Optional<String> waitForNewWindow(final Set<String> handlesBeforeClick, final Duration timeout) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			return Optional.ofNullable(shortWait.until(newWindowHandle(handlesBeforeClick)));
		} catch (final TimeoutException timeoutException) {
			return Optional.empty();
		}
	}

	private ExpectedCondition<String> newWindowHandle(final Set<String> oldHandles) {
		return driverRef -> {
			for (final String handle : driverRef.getWindowHandles()) {
				if (!oldHandles.contains(handle)) {
					return handle;
				}
			}
			return null;
		};
	}

	private void waitForUiToLoad() {
		try {
			wait.until(driverRef -> "complete".equals(
					((JavascriptExecutor) driverRef).executeScript("return document.readyState")));
		} catch (final TimeoutException ignored) {
			// SPA navigation may keep readyState as complete during asynchronous transitions.
		}

		try {
			Thread.sleep(500);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String name) throws IOException {
		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path targetFile = evidenceDir.resolve(name + ".png");
		Files.copy(screenshotFile.toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING);
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String readConfig(final String propertyName, final String envName) {
		return readConfig(propertyName, envName, null);
	}

	private String readConfig(final String propertyName, final String envName, final String fallbackValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		return fallbackValue;
	}

	private long readWaitSeconds() {
		final String waitSeconds = readConfig("saleads.wait.seconds", "SALEADS_WAIT_SECONDS", "25");
		try {
			return Long.parseLong(waitSeconds);
		} catch (final NumberFormatException numberFormatException) {
			return 25L;
		}
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Workflow Report ===");
		for (final String step : List.of("Login", "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
				"Información General", "Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones",
				"Política de Privacidad")) {
			final String status;
			if (!report.containsKey(step)) {
				status = "NOT RUN";
			} else {
				status = report.get(step) ? "PASS" : "FAIL";
			}
			final String details = detailReport.get(step);
			System.out.println(step + ": " + status + (details == null ? "" : " (" + details + ")"));
		}
		if (terminosFinalUrl != null) {
			System.out.println("Términos y Condiciones URL: " + terminosFinalUrl);
		}
		if (privacidadFinalUrl != null) {
			System.out.println("Política de Privacidad URL: " + privacidadFinalUrl);
		}
		if (evidenceDir != null) {
			System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Throwable;
	}
}
