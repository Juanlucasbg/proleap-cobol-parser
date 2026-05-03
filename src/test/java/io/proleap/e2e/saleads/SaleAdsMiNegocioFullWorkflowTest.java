package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio" module.
 *
 * <p>Configuration:
 * <ul>
 *   <li>-Dsaleads.login.url=https://your-environment/login (optional; if omitted, test expects to start on login page)</li>
 *   <li>-Dsaleads.user.email=juanlucasbarbiergarzon@gmail.com (optional override)</li>
 *   <li>-Dsaleads.user.name=Juan Lucas (optional; improves user-name validation)</li>
 *   <li>-Dsaleads.headless=true|false (default true)</li>
 *   <li>-Dwebdriver.chrome.driver=/path/to/chromedriver (optional)</li>
 * </ul>
 */
public class SaleAdsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final List<String> legalUrls = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String appWindowHandle;
	private String expectedEmail;
	private String expectedUserName;

	@Before
	public void setUp() throws IOException {
		expectedEmail = readConfig("saleads.user.email", "SALEADS_USER_EMAIL", "juanlucasbarbiergarzon@gmail.com");
		expectedUserName = readConfig("saleads.user.name", "SALEADS_USER_NAME", "");

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-notifications");
		options.addArguments("--disable-popup-blocking");
		options.addArguments("--lang=es-ES");

		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		final String chromeBinary = readConfig("saleads.chrome.binary", "SALEADS_CHROME_BINARY", "");
		if (!chromeBinary.isBlank()) {
			options.setBinary(chromeBinary);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));

		final String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
		screenshotDir = Path.of("target", "surefire-reports", "saleads-mi-negocio-" + timestamp);
		Files.createDirectories(screenshotDir);

		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", "");
		if (!loginUrl.isBlank()) {
			driver.get(loginUrl);
		}
		waitForUiToSettle();
	}

	@After
	public void tearDown() {
		printFinalReport();
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
		runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos"));
		runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09-politica-privacidad"));

		if (!failures.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow failed: " + String.join(" | ", failures));
		}
	}

	private void stepLoginWithGoogle() {
		if (isCurrentPageBlank()) {
			throw new IllegalStateException(
					"Browser is not on the SaleADS login page. Provide -Dsaleads.login.url or preload login page.");
		}

		final Set<String> windowsBefore = driver.getWindowHandles();
		clickByVisibleTextAny("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
				"Continuar con Google", "Acceder con Google", "Login con Google");
		waitForUiToSettle();

		final Optional<String> newGoogleWindow = waitForNewWindow(windowsBefore, Duration.ofSeconds(12));
		if (newGoogleWindow.isPresent()) {
			driver.switchTo().window(newGoogleWindow.get());
			waitForUiToSettle();
		}

		selectGoogleAccountIfVisible(expectedEmail);
		waitForUiToSettle();

		switchToWindowContainingAnyText(Duration.ofSeconds(60), "Mi Negocio", "Negocio", "Dashboard", "Inicio");
		appWindowHandle = driver.getWindowHandle();

		assertAnyVisibleText("Mi Negocio", "Negocio");
		assertSidebarVisible();
		takeViewportScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		assertSidebarVisible();
		ensureTextVisible("Negocio");
		clickByVisibleTextAny("Mi Negocio");
		waitForUiToSettle();

		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Administrar Negocios");
		takeViewportScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleTextAny("Agregar Negocio");
		waitForUiToSettle();

		assertAnyVisibleText("Crear Nuevo Negocio");
		assertInputForLabel("Nombre del Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
		assertAnyVisibleText("Cancelar");
		assertAnyVisibleText("Crear Negocio");
		takeViewportScreenshot("03-agregar-negocio-modal");

		final Optional<WebElement> input = findLabeledInput("Nombre del Negocio");
		if (input.isPresent()) {
			WebElement inputField = input.get();
			inputField.click();
			waitForUiToSettle();
			inputField.sendKeys(Keys.chord(Keys.CONTROL, "a"));
			inputField.sendKeys("Negocio Prueba Automatizacion");
			waitForUiToSettle();
		}
		clickByVisibleTextAny("Cancelar");
		waitForUiToSettle();
		assertTextNotVisible("Crear Nuevo Negocio");
	}

	private void stepOpenAdministrarNegocios() {
		if (!isAnyVisible("Administrar Negocios")) {
			clickByVisibleTextAny("Mi Negocio");
			waitForUiToSettle();
		}
		clickByVisibleTextAny("Administrar Negocios");
		waitForUiToSettle();

		assertAnyVisibleText("Información General");
		assertAnyVisibleText("Detalles de la Cuenta");
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Sección Legal");
		takeFullPageScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisibleText("BUSINESS PLAN");
		assertAnyVisibleText("Cambiar Plan");
		assertEmailVisible(expectedEmail);
		assertUserNameVisible();
	}

	private void stepValidateDetallesCuenta() {
		assertAnyVisibleText("Cuenta creada");
		assertAnyVisibleText("Estado activo");
		assertAnyVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");

		final List<WebElement> visibleRows = findVisibleElements(By.xpath(
				"//section[contains(., 'Tus Negocios')]//*[self::li or self::tr or self::article or self::div][normalize-space()]"));
		assertTrue("Business list is not visible in 'Tus Negocios' section.", visibleRows.size() > 0);
	}

	private void stepValidateLegalLink(final String linkText, final String headingText, final String screenshotPrefix) {
		switchToAppWindow();

		final Set<String> before = driver.getWindowHandles();
		clickByVisibleTextAny(linkText);
		waitForUiToSettle();

		final Optional<String> maybeNewTab = waitForNewWindow(before, Duration.ofSeconds(10));
		final boolean openedNewTab = maybeNewTab.isPresent();
		if (openedNewTab) {
			driver.switchTo().window(maybeNewTab.get());
			waitForUiToSettle();
		}

		assertAnyVisibleText(headingText);
		assertLegalContentVisible();
		takeViewportScreenshot(screenshotPrefix);

		final String finalUrl = driver.getCurrentUrl();
		legalUrls.add(linkText + " -> " + finalUrl);

		if (openedNewTab) {
			driver.close();
			switchToAppWindow();
		} else {
			driver.navigate().back();
			waitForUiToSettle();
			switchToAppWindow();
		}
	}

	private void runStep(final String stepName, final Runnable step) {
		try {
			step.run();
			report.put(stepName, true);
		} catch (final Throwable throwable) {
			report.put(stepName, false);
			failures.add(stepName + ": " + throwable.getMessage());
			safeCheckpointOnFailure(stepName);
		}
	}

	private void printFinalReport() {
		if (report.isEmpty()) {
			return;
		}

		System.out.println("==== SaleADS Mi Negocio Final Report ====");
		final List<String> order = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Términos y Condiciones", "Política de Privacidad");
		for (final String step : order) {
			final boolean passed = report.getOrDefault(step, false);
			System.out.println(step + ": " + (passed ? "PASS" : "FAIL"));
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("Legal URLs:");
			for (final String legalUrl : legalUrls) {
				System.out.println(" - " + legalUrl);
			}
		}
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
		System.out.println("=========================================");
	}

	private void safeCheckpointOnFailure(final String stepName) {
		try {
			takeViewportScreenshot("failure-" + sanitizeFileName(stepName));
		} catch (final RuntimeException ignored) {
			// best-effort failure evidence only
		}
	}

	private void switchToAppWindow() {
		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForUiToSettle();
			return;
		}
		switchToWindowContainingAnyText(Duration.ofSeconds(15), "Sección Legal", "Administrar Negocios", "Mi Negocio");
		appWindowHandle = driver.getWindowHandle();
	}

	private void assertSidebarVisible() {
		final List<WebElement> candidates = findVisibleElements(By.xpath("//aside | //nav"));
		assertTrue("Sidebar navigation is not visible.", !candidates.isEmpty());
	}

	private void ensureTextVisible(final String text) {
		wait.until(driver -> isAnyVisible(text));
	}

	private boolean isAnyVisible(final String text) {
		return !findVisibleElements(byContainsText(text)).isEmpty();
	}

	private void assertAnyVisibleText(final String... texts) {
		final List<String> tried = new ArrayList<>();
		for (final String text : texts) {
			tried.add(text);
			if (waitForVisibleText(text, Duration.ofSeconds(15)).isPresent()) {
				return;
			}
		}
		throw new AssertionError("None of the expected texts is visible: " + tried);
	}

	private void assertTextNotVisible(final String text) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
		final boolean gone = shortWait.until(d -> findVisibleElements(byContainsText(text)).isEmpty());
		assertTrue("Text should not be visible: " + text, gone);
	}

	private void clickByVisibleTextAny(final String... texts) {
		Throwable lastFailure = null;
		for (final String text : texts) {
			try {
				clickByVisibleText(text);
				return;
			} catch (final Throwable throwable) {
				lastFailure = throwable;
			}
		}
		throw new AssertionError("Could not click any candidate text: " + Arrays.toString(texts), lastFailure);
	}

	private void clickByVisibleText(final String text) {
		final String lit = toXPathLiteral(text);
		final List<By> locators = Arrays.asList(
				By.xpath("//button[contains(normalize-space(.), " + lit + ")]"),
				By.xpath("//a[contains(normalize-space(.), " + lit + ")]"),
				By.xpath("//*[@role='button'][contains(normalize-space(.), " + lit + ")]"),
				By.xpath("//*[contains(normalize-space(.), " + lit + ")]"));

		for (final By locator : locators) {
			final List<WebElement> elements = findVisibleElements(locator);
			for (final WebElement element : elements) {
				try {
					scrollIntoView(element);
					element.click();
					waitForUiToSettle();
					return;
				} catch (final Exception ignored) {
					// try another visible candidate
				}
			}
		}
		throw new NoSuchElementException("Visible clickable element not found for text: " + text);
	}

	private Optional<WebElement> waitForVisibleText(final String text, final Duration timeout) {
		final WebDriverWait customWait = new WebDriverWait(driver, timeout);
		try {
			return Optional.ofNullable(customWait.until(d -> {
				final List<WebElement> elements = findVisibleElements(byContainsText(text));
				return elements.isEmpty() ? null : elements.get(0);
			}));
		} catch (final TimeoutException timeoutException) {
			return Optional.empty();
		}
	}

	private void assertInputForLabel(final String labelText) {
		final Optional<WebElement> input = findLabeledInput(labelText);
		assertTrue("Input for label '" + labelText + "' was not found.", input.isPresent());
	}

	private Optional<WebElement> findLabeledInput(final String labelText) {
		final String lit = toXPathLiteral(labelText);
		final By byLabelFor = By.xpath("//label[contains(normalize-space(.), " + lit + ")][@for]");
		final List<WebElement> labels = findVisibleElements(byLabelFor);
		for (final WebElement label : labels) {
			final String forAttribute = label.getAttribute("for");
			if (forAttribute != null && !forAttribute.isBlank()) {
				final List<WebElement> byId = findVisibleElements(By.id(forAttribute));
				if (!byId.isEmpty()) {
					return Optional.of(byId.get(0));
				}
			}
		}

		final List<By> fallbacks = Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), " + lit + ")]/following::input[1]"),
				By.xpath("//div[contains(., " + lit + ")]//input[1]"),
				By.xpath("//input[contains(@placeholder, " + lit + ")]"));
		for (final By fallback : fallbacks) {
			final List<WebElement> inputs = findVisibleElements(fallback);
			if (!inputs.isEmpty()) {
				return Optional.of(inputs.get(0));
			}
		}
		return Optional.empty();
	}

	private void assertEmailVisible(final String requiredEmail) {
		final List<WebElement> visible = findVisibleElements(By.xpath("//*[contains(normalize-space(.), '@')]"));
		for (final WebElement element : visible) {
			if (element.getText().contains(requiredEmail)) {
				return;
			}
		}
		throw new AssertionError("Expected user email is not visible: " + requiredEmail);
	}

	private void assertUserNameVisible() {
		if (!expectedUserName.isBlank()) {
			assertAnyVisibleText(expectedUserName);
			return;
		}

		final List<WebElement> textCandidates = findVisibleElements(
				By.xpath("//section[contains(., 'Información General')]//*[normalize-space(text())]"));
		for (final WebElement candidate : textCandidates) {
			final String text = normalize(candidate.getText());
			if (text.isBlank()) {
				continue;
			}
			if (text.contains("@") || text.equalsIgnoreCase("BUSINESS PLAN") || text.equalsIgnoreCase("Cambiar Plan")
					|| text.equalsIgnoreCase("Información General")) {
				continue;
			}
			if (text.length() >= 4) {
				return;
			}
		}
		throw new AssertionError(
				"Could not confidently detect a user name. Set -Dsaleads.user.name for strict name validation.");
	}

	private void assertLegalContentVisible() {
		final List<WebElement> paragraphs = findVisibleElements(By.xpath("//p[normalize-space()] | //article//*[normalize-space()]"));
		for (final WebElement paragraph : paragraphs) {
			final String text = normalize(paragraph.getText());
			if (text.length() >= 40) {
				return;
			}
		}
		throw new AssertionError("Legal content text is not visible.");
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final Optional<WebElement> emailOption = waitForVisibleText(email, Duration.ofSeconds(12));
		if (emailOption.isPresent()) {
			scrollIntoView(emailOption.get());
			emailOption.get().click();
			waitForUiToSettle();
		}
	}

	private Optional<String> waitForNewWindow(final Set<String> previousHandles, final Duration timeout) {
		final WebDriverWait customWait = new WebDriverWait(driver, timeout);
		try {
			return Optional.ofNullable(customWait.until(d -> {
				final Set<String> current = new LinkedHashSet<>(driver.getWindowHandles());
				current.removeAll(previousHandles);
				return current.isEmpty() ? null : current.iterator().next();
			}));
		} catch (final TimeoutException timeoutException) {
			return Optional.empty();
		}
	}

	private void switchToWindowContainingAnyText(final Duration timeout, final String... texts) {
		final WebDriverWait customWait = new WebDriverWait(driver, timeout);
		final boolean found = customWait.until((ExpectedCondition<Boolean>) d -> {
			final Set<String> handles = driver.getWindowHandles();
			for (final String handle : handles) {
				driver.switchTo().window(handle);
				for (final String text : texts) {
					if (isAnyVisible(text)) {
						return true;
					}
				}
			}
			return false;
		});
		if (!found) {
			throw new AssertionError("Could not find any window containing expected texts: " + Arrays.toString(texts));
		}
	}

	private List<WebElement> findVisibleElements(final By locator) {
		final List<WebElement> all = driver.findElements(locator);
		final List<WebElement> visible = new ArrayList<>();
		for (final WebElement element : all) {
			try {
				if (element.isDisplayed()) {
					visible.add(element);
				}
			} catch (final Exception ignored) {
				// stale or detached element
			}
		}
		return visible;
	}

	private void waitForUiToSettle() {
		wait.until(driver -> {
			try {
				final Object state = ((JavascriptExecutor) driver).executeScript("return document.readyState");
				return "complete".equals(state) || "interactive".equals(state);
			} catch (final Exception exception) {
				return true;
			}
		});

		try {
			Thread.sleep(400L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void takeViewportScreenshot(final String name) {
		saveScreenshot(name);
	}

	private void takeFullPageScreenshot(final String name) {
		final Dimension original = driver.manage().window().getSize();
		try {
			final long pageHeight = Math.max(1200L, ((Number) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);"))
					.longValue());
			driver.manage().window().setSize(new Dimension(1920, (int) Math.min(pageHeight, 6000L)));
			waitForUiToSettle();
			saveScreenshot(name);
		} finally {
			driver.manage().window().setSize(original);
		}
	}

	private void saveScreenshot(final String name) {
		try {
			final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path output = screenshotDir.resolve(sanitizeFileName(name) + ".png");
			Files.copy(screenshot.toPath(), output, StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException exception) {
			throw new RuntimeException("Unable to save screenshot for " + name, exception);
		}
	}

	private By byContainsText(final String text) {
		return By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char c = chars[i];
			if (i > 0) {
				sb.append(", ");
			}
			if (c == '\'') {
				sb.append("\"'\"");
			} else if (c == '"') {
				sb.append("'\"'");
			} else {
				sb.append('\'').append(c).append('\'');
			}
		}
		sb.append(')');
		return sb.toString();
	}

	private String readConfig(final String systemProperty, final String environmentVariable, final String fallbackValue) {
		final String fromProperty = System.getProperty(systemProperty);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}
		final String fromEnv = System.getenv(environmentVariable);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv.trim();
		}
		return fallbackValue;
	}

	private String normalize(final String text) {
		return text == null ? "" : text.replace('\u00a0', ' ').trim();
	}

	private String sanitizeFileName(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]+", "-").replaceAll("-+", "-")
				.replaceAll("^-|-$", "");
	}

	private boolean isCurrentPageBlank() {
		final String currentUrl = driver.getCurrentUrl();
		return currentUrl == null || "about:blank".equals(currentUrl);
	}
}
