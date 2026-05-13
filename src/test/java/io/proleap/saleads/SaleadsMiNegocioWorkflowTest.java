package io.proleap.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

	private WebDriver driver;
	private WebDriverWait wait;
	private JavascriptExecutor js;
	private Path evidenceDir;

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> reportDetails = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String loginUrl = readConfig("SALEADS_LOGIN_URL", "saleads.login.url", null);
		if (loginUrl == null || loginUrl.isBlank()) {
			fail("Missing SALEADS_LOGIN_URL (or -Dsaleads.login.url). Use the login URL for the active environment.");
		}

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean
				.parseBoolean(readConfig("SALEADS_HEADLESS", "saleads.headless", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		js = (JavascriptExecutor) driver;

		evidenceDir = Path.of("target", "saleads-evidence",
				DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		driver.get(loginUrl);
		waitForUiIdle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminos);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final String finalReport = buildFinalReport();
		System.out.println(finalReport);

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue()) {
				failedSteps.add(entry.getKey());
			}
		}
		assertTrue("One or more workflow steps failed:\n" + String.join(", ", failedSteps) + "\n" + finalReport,
				failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		final Set<String> initialHandles = driver.getWindowHandles();
		final WebElement loginButton = waitForAnyVisible(Duration.ofSeconds(20),
				By.xpath("//button[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'google')]"),
				By.xpath("//a[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'google')]"),
				By.xpath("//*[@role='button' and contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'google')]"));
		clickAndWait(loginButton);

		handleGoogleAccountSelection(initialHandles, GOOGLE_ACCOUNT_EMAIL);
		waitForMainInterface();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickIfPresent("Negocio");
		clickByText("Mi Negocio");

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		final WebElement modal = waitForVisible(By.xpath(
				"//*[self::div or self::section][.//*[normalize-space()='Crear Nuevo Negocio']]"), Duration.ofSeconds(10));
		final List<WebElement> nameInputs = modal
				.findElements(By.xpath(".//input[@placeholder='Nombre del Negocio' or @name='businessName' or @id='businessName'] | .//input"));
		assertTrue("Input field 'Nombre del Negocio' is required.", !nameInputs.isEmpty());

		takeScreenshot("03-agregar-negocio-modal");

		nameInputs.get(0).click();
		nameInputs.get(0).clear();
		nameInputs.get(0).sendKeys("Negocio Prueba Automatizacion");
		clickInsideContainer(modal, "Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']")));
		waitForUiIdle();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(3))) {
			clickByText("Mi Negocio");
		}
		clickByText("Administrar Negocios");

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("Información General");
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");

		final String bodyText = visibleBodyText();
		assertTrue("User email should be visible in Información General.", EMAIL_PATTERN.matcher(bodyText).find());

		final boolean hasExpectedName = isTextVisible(readConfig("SALEADS_EXPECTED_USER_NAME", "saleads.expected.user.name", ""),
				Duration.ofSeconds(2));
		final boolean hasNameLabel = isTextVisible("Nombre", Duration.ofSeconds(2))
				|| isTextVisible("Usuario", Duration.ofSeconds(2))
				|| isTextVisible("Perfil", Duration.ofSeconds(2));
		assertTrue("User name should be visible in Información General.", hasExpectedName || hasNameLabel);
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminos() throws IOException {
		validateLegalPage("Términos y Condiciones", "Términos y Condiciones", "05-terminos-condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		validateLegalPage("Política de Privacidad", "Política de Privacidad", "06-politica-privacidad");
	}

	private void validateLegalPage(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		clickByText(linkText);

		final String targetHandle = waitForNewWindowOrCurrent(handlesBefore, appHandle);
		driver.switchTo().window(targetHandle);
		waitForUiIdle();

		waitForAnyVisible(Duration.ofSeconds(20),
				By.xpath("//*[self::h1 or self::h2 or self::h3][contains(normalize-space(), " + toXPathLiteral(headingText)
						+ ")]"),
				By.xpath("//*[contains(normalize-space(), " + toXPathLiteral(headingText) + ")]"));

		final String legalContent = visibleBodyText().replaceAll("\\s+", " ").trim();
		assertTrue("Legal content text should be visible for: " + headingText, legalContent.length() > 120);

		takeScreenshot(screenshotName);
		System.out.println(linkText + " final URL: " + driver.getCurrentUrl());

		if (!targetHandle.equals(appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiIdle();
		assertTextVisible("Sección Legal");
	}

	private void handleGoogleAccountSelection(final Set<String> initialHandles, final String email) {
		final long timeoutMillis = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
		final By emailSelector = By.xpath("//*[normalize-space()=" + toXPathLiteral(email) + "]");

		while (System.currentTimeMillis() < timeoutMillis) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				clickIfPresent(emailSelector, Duration.ofSeconds(1));
				if (isMainInterfaceVisible(Duration.ofSeconds(2))) {
					return;
				}
			}

			if (driver.getWindowHandles().size() > initialHandles.size()) {
				try {
					Thread.sleep(500);
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			} else if (isMainInterfaceVisible(Duration.ofSeconds(2))) {
				return;
			}
		}
	}

	private void waitForMainInterface() {
		wait.until(d -> {
			for (final String handle : d.getWindowHandles()) {
				d.switchTo().window(handle);
				if (isMainInterfaceVisible(Duration.ofSeconds(2))) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isMainInterfaceVisible(final Duration timeout) {
		return isPresent(By.xpath("//aside"), timeout)
				|| isPresent(By.xpath("//nav[.//*[contains(normalize-space(), 'Negocio')]]"), timeout)
				|| isTextVisible("Negocio", timeout);
	}

	private String waitForNewWindowOrCurrent(final Set<String> handlesBefore, final String currentHandle) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12))
					.until(ExpectedConditions.numberOfWindowsToBe(handlesBefore.size() + 1));
		} catch (final TimeoutException ignored) {
			// Legal pages can open in the same tab depending on the environment.
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBefore.contains(handle)) {
				return handle;
			}
		}
		return currentHandle;
	}

	private void clickByText(final String visibleText) {
		final String textLiteral = toXPathLiteral(visibleText);
		final By locator = By.xpath(
				"(//button[normalize-space()=" + textLiteral + " or .//*[normalize-space()=" + textLiteral + "]]"
						+ " | //a[normalize-space()=" + textLiteral + " or .//*[normalize-space()=" + textLiteral + "]]"
						+ " | //*[@role='button' and normalize-space()=" + textLiteral + "]"
						+ " | //li[normalize-space()=" + textLiteral + "]"
						+ " | //span[normalize-space()=" + textLiteral + "])[1]");
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
		clickAndWait(element);
	}

	private void clickIfPresent(final String visibleText) {
		final String textLiteral = toXPathLiteral(visibleText);
		final By locator = By.xpath(
				"(//button[normalize-space()=" + textLiteral + "] | //a[normalize-space()=" + textLiteral
						+ "] | //*[@role='button' and normalize-space()=" + textLiteral + "] | //span[normalize-space()="
						+ textLiteral + "])[1]");
		clickIfPresent(locator, Duration.ofSeconds(3));
	}

	private boolean clickIfPresent(final By locator, final Duration timeout) {
		try {
			final WebElement element = waitForVisible(locator, timeout);
			clickAndWait(element);
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void clickInsideContainer(final WebElement container, final String text) {
		final String textLiteral = toXPathLiteral(text);
		final WebElement target = container.findElement(By.xpath(".//button[normalize-space()=" + textLiteral + "]"
				+ " | .//a[normalize-space()=" + textLiteral + "]"
				+ " | .//*[@role='button' and normalize-space()=" + textLiteral + "]"));
		clickAndWait(target);
	}

	private void assertTextVisible(final String text) {
		assertTrue("Expected visible text: " + text, isTextVisible(text, Duration.ofSeconds(20)));
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		if (text == null || text.isBlank()) {
			return false;
		}
		final String textLiteral = toXPathLiteral(text);
		return isPresent(By.xpath("//*[normalize-space()=" + textLiteral + "]"), timeout)
				|| isPresent(By.xpath("//*[contains(normalize-space(), " + textLiteral + ")]"), timeout);
	}

	private boolean isPresent(final By by, final Duration timeout) {
		try {
			waitForVisible(by, timeout);
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private WebElement waitForVisible(final By by, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private WebElement waitForAnyVisible(final Duration timeout, final By... candidates) {
		final long endTime = System.currentTimeMillis() + timeout.toMillis();
		NoSuchElementException lastException = null;

		while (System.currentTimeMillis() < endTime) {
			for (final By by : candidates) {
				try {
					final WebElement element = driver.findElement(by);
					if (element.isDisplayed()) {
						return element;
					}
				} catch (final NoSuchElementException e) {
					lastException = e;
				}
			}
			try {
				Thread.sleep(250);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		throw new TimeoutException("None of the candidate elements became visible.", lastException);
	}

	private void clickAndWait(final WebElement element) {
		try {
			js.executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		} catch (final Exception ignored) {
			// Best effort only.
		}

		try {
			element.click();
		} catch (final Exception ignored) {
			js.executeScript("arguments[0].click();", element);
		}
		waitForUiIdle();
	}

	private void waitForUiIdle() {
		try {
			wait.until(d -> {
				final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(state) || "interactive".equals(state);
			});
		} catch (final Exception ignored) {
			// SPAs sometimes keep loading states for long periods.
		}

		try {
			Thread.sleep(350);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String visibleBodyText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String fileName = checkpointName.replaceAll("[^a-zA-Z0-9._-]", "_") + ".png";
		Files.copy(screenshot.toPath(), evidenceDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.execute();
			report.put(stepName, true);
			reportDetails.put(stepName, "PASS");
		} catch (final Throwable t) {
			report.put(stepName, false);
			reportDetails.put(stepName, "FAIL - " + t.getMessage());
		}
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("\n=== SaleADS Mi Negocio Workflow Report ===\n");
		for (final Map.Entry<String, String> entry : reportDetails.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		return builder.toString();
	}

	private String readConfig(final String envKey, final String propertyKey, final String defaultValue) {
		final String prop = System.getProperty(propertyKey);
		if (prop != null && !prop.isBlank()) {
			return prop;
		}
		final String env = System.getenv(envKey);
		if (env != null && !env.isBlank()) {
			return env;
		}
		return defaultValue;
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char c = chars[i];
			if (c == '\'') {
				result.append("\"'\"");
			} else if (c == '\"') {
				result.append("'\"'");
			} else {
				result.append('\'').append(c).append('\'');
			}
			if (i < chars.length - 1) {
				result.append(',');
			}
		}
		result.append(')');
		return result.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void execute() throws Exception;
	}
}
