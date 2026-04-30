package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
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
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>SALEADS_LOGIN_URL (optional): login URL of the target environment.</li>
 *   <li>SALEADS_EXPECTED_GOOGLE_EMAIL (optional): email to select in Google account picker.</li>
 *   <li>SALEADS_EXPECTED_USER_NAME (optional): expected user name in "Informacion General".</li>
 *   <li>SALEADS_BROWSER (optional): chrome (default) or firefox.</li>
 *   <li>SALEADS_HEADLESS (optional): true (default) or false.</li>
 *   <li>SELENIUM_REMOTE_URL (optional): use remote Selenium grid instead of local browser.</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String expectedGoogleEmail;
	private String expectedUserName;

	@Before
	public void setUp() throws Exception {
		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().window().setSize(new Dimension(1920, 1080));

		expectedGoogleEmail = readEnv("SALEADS_EXPECTED_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL);
		expectedUserName = System.getenv("SALEADS_EXPECTED_USER_NAME");

		final String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		screenshotDir = Path.of("target", "surefire-reports", "saleads-screenshots", stamp);
		Files.createDirectories(screenshotDir);

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
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
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStepLogin();
		runStepMiNegocioMenu();
		runStepAgregarNegocioModal();
		runStepAdministrarNegocios();
		runStepInformacionGeneral();
		runStepDetallesCuenta();
		runStepTusNegocios();
		runStepTerminosYCondiciones();
		runStepPoliticaPrivacidad();
		printFinalReport();
		assertAllChecksPassed();
	}

	private void runStepLogin() {
		boolean passed = true;
		try {
			final List<String> loginButtons = Arrays.asList(
					"Sign in with Google",
					"Login with Google",
					"Iniciar con Google",
					"Iniciar sesion con Google",
					"Continuar con Google");

			if (isAnyTextVisible(loginButtons)) {
				clickByVisibleTextAndWait(loginButtons);
				selectGoogleAccountIfPrompted(expectedGoogleEmail);
			}

			final boolean appShellVisible = isSidebarVisible() || isAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio"));
			final boolean sidebarVisible = isSidebarVisible();

			passed = appShellVisible && sidebarVisible;
			captureScreenshot("01-dashboard-loaded", false);
		} catch (Exception ex) {
			passed = false;
			captureFailureEvidence("01-login-error");
		}
		report.put("Login", passed);
	}

	private void runStepMiNegocioMenu() {
		boolean passed = true;
		try {
			clickByVisibleTextAndWait(Arrays.asList("Negocio"));
			clickByVisibleTextAndWait(Arrays.asList("Mi Negocio"));

			final boolean agregarVisible = waitForTextVisible("Agregar Negocio", SHORT_TIMEOUT);
			final boolean administrarVisible = waitForTextVisible("Administrar Negocios", SHORT_TIMEOUT);

			passed = agregarVisible && administrarVisible;
			captureScreenshot("02-mi-negocio-menu-expanded", false);
		} catch (Exception ex) {
			passed = false;
			captureFailureEvidence("02-mi-negocio-menu-error");
		}
		report.put("Mi Negocio menu", passed);
	}

	private void runStepAgregarNegocioModal() {
		boolean passed = true;
		try {
			clickByVisibleTextAndWait(Arrays.asList("Agregar Negocio"));

			final boolean titleVisible = waitForTextVisible("Crear Nuevo Negocio", DEFAULT_TIMEOUT);
			final boolean inputVisible = isElementVisible(By.xpath(
					"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio') or @name='businessName']"
							+ " | //label[contains(normalize-space(),'Nombre del Negocio')]"));
			final boolean quotaVisible = waitForTextVisible("Tienes 2 de 3 negocios", SHORT_TIMEOUT);
			final boolean cancelarVisible = waitForTextVisible("Cancelar", SHORT_TIMEOUT);
			final boolean crearVisible = waitForTextVisible("Crear Negocio", SHORT_TIMEOUT);

			passed = titleVisible && inputVisible && quotaVisible && cancelarVisible && crearVisible;
			captureScreenshot("03-agregar-negocio-modal", false);

			// Optional action requested in the workflow: type then close modal.
			typeBusinessNameIfFieldExists("Negocio Prueba Automatizacion");
			clickByVisibleTextAndWait(Arrays.asList("Cancelar"));
		} catch (Exception ex) {
			passed = false;
			captureFailureEvidence("03-agregar-negocio-modal-error");
		}
		report.put("Agregar Negocio modal", passed);
	}

	private void runStepAdministrarNegocios() {
		boolean passed = true;
		try {
			ensureMiNegocioExpanded();
			clickByVisibleTextAndWait(Arrays.asList("Administrar Negocios"));

			final boolean infoGeneral = waitForTextVisible("Informacion General", DEFAULT_TIMEOUT)
					|| waitForTextVisible("Información General", SHORT_TIMEOUT);
			final boolean detalles = waitForTextVisible("Detalles de la Cuenta", SHORT_TIMEOUT);
			final boolean tusNegocios = waitForTextVisible("Tus Negocios", SHORT_TIMEOUT);
			final boolean legal = waitForTextVisible("Seccion Legal", SHORT_TIMEOUT)
					|| waitForTextVisible("Sección Legal", SHORT_TIMEOUT);

			passed = infoGeneral && detalles && tusNegocios && legal;
			captureScreenshot("04-administrar-negocios", true);
		} catch (Exception ex) {
			passed = false;
			captureFailureEvidence("04-administrar-negocios-error");
		}
		report.put("Administrar Negocios view", passed);
	}

	private void runStepInformacionGeneral() {
		boolean passed = true;
		try {
			final boolean emailVisible = waitForTextVisible(expectedGoogleEmail, SHORT_TIMEOUT) || hasVisibleEmailText();
			final boolean businessPlanVisible = waitForTextVisible("BUSINESS PLAN", SHORT_TIMEOUT);
			final boolean cambiarPlanVisible = waitForTextVisible("Cambiar Plan", SHORT_TIMEOUT);
			final boolean userNameVisible = isUserNameVisible();
			passed = userNameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible;
		} catch (Exception ex) {
			passed = false;
		}
		report.put("Información General", passed);
	}

	private void runStepDetallesCuenta() {
		boolean passed = true;
		try {
			final boolean cuentaCreada = waitForTextVisible("Cuenta creada", SHORT_TIMEOUT);
			final boolean estadoActivo = waitForTextVisible("Estado activo", SHORT_TIMEOUT);
			final boolean idioma = waitForTextVisible("Idioma seleccionado", SHORT_TIMEOUT);
			passed = cuentaCreada && estadoActivo && idioma;
		} catch (Exception ex) {
			passed = false;
		}
		report.put("Detalles de la Cuenta", passed);
	}

	private void runStepTusNegocios() {
		boolean passed = true;
		try {
			final boolean heading = waitForTextVisible("Tus Negocios", SHORT_TIMEOUT);
			final boolean addButton = waitForTextVisible("Agregar Negocio", SHORT_TIMEOUT);
			final boolean quota = waitForTextVisible("Tienes 2 de 3 negocios", SHORT_TIMEOUT);
			final boolean businessListVisible = hasBusinessListSignal();
			passed = heading && addButton && quota && businessListVisible;
		} catch (Exception ex) {
			passed = false;
		}
		report.put("Tus Negocios", passed);
	}

	private void runStepTerminosYCondiciones() {
		final boolean passed = validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos");
		report.put("Términos y Condiciones", passed);
	}

	private void runStepPoliticaPrivacidad() {
		final boolean passed = validateLegalLink("Política de Privacidad", "Política de Privacidad", "09-politica-privacidad");
		report.put("Política de Privacidad", passed);
	}

	private boolean validateLegalLink(final String linkText, final String headingText, final String screenshotName) {
		boolean passed = true;
		String mainWindow = null;
		try {
			mainWindow = driver.getWindowHandle();
			final Set<String> beforeHandles = driver.getWindowHandles();

			clickByVisibleTextAndWait(Arrays.asList(linkText));
			switchToNewTabIfOpened(beforeHandles);

			final boolean headingVisible = waitForTextVisible(headingText, DEFAULT_TIMEOUT)
					|| waitForTextVisible(stripAccents(headingText), SHORT_TIMEOUT);
			final boolean legalContentVisible = hasLegalContentText();
			final String finalUrl = driver.getCurrentUrl();
			legalUrls.put(headingText, finalUrl);
			captureScreenshot(screenshotName, true);

			passed = headingVisible && legalContentVisible;
		} catch (Exception ex) {
			passed = false;
			captureFailureEvidence(screenshotName + "-error");
		} finally {
			returnToApplicationTab(mainWindow);
		}
		return passed;
	}

	private WebDriver createDriver() throws MalformedURLException {
		final String browser = readEnv("SALEADS_BROWSER", "chrome").toLowerCase();
		final boolean headless = Boolean.parseBoolean(readEnv("SALEADS_HEADLESS", "true"));
		final String remoteUrl = System.getenv("SELENIUM_REMOTE_URL");

		if ("firefox".equals(browser)) {
			final FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			return createLocalOrRemoteDriver(options, remoteUrl);
		}

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (headless) {
			options.addArguments("--headless=new");
		}
		return createLocalOrRemoteDriver(options, remoteUrl);
	}

	private WebDriver createLocalOrRemoteDriver(final MutableCapabilities options, final String remoteUrl)
			throws MalformedURLException {
		if (remoteUrl != null && !remoteUrl.isBlank()) {
			return new RemoteWebDriver(new URL(remoteUrl), options);
		}
		if (options instanceof ChromeOptions) {
			return new ChromeDriver((ChromeOptions) options);
		}
		if (options instanceof FirefoxOptions) {
			return new FirefoxDriver((FirefoxOptions) options);
		}
		throw new IllegalArgumentException("Unsupported capabilities for local browser startup.");
	}

	private void ensureMiNegocioExpanded() {
		if (!isTextVisible("Administrar Negocios")) {
			if (isTextVisible("Mi Negocio")) {
				clickByVisibleTextAndWait(Arrays.asList("Mi Negocio"));
			} else {
				clickByVisibleTextAndWait(Arrays.asList("Negocio", "Mi Negocio"));
			}
		}
	}

	private void selectGoogleAccountIfPrompted(final String accountEmail) {
		try {
			if (waitForTextVisible(accountEmail, Duration.ofSeconds(10))) {
				clickByVisibleTextAndWait(Arrays.asList(accountEmail));
			}
		} catch (Exception ignored) {
			// Account picker is optional; proceed when session is already authenticated.
		}
	}

	private void clickByVisibleTextAndWait(final List<String> candidateTexts) {
		WebElement element = null;
		for (final String text : candidateTexts) {
			final Optional<WebElement> found = findDisplayedElementByText(text);
			if (found.isPresent()) {
				element = found.get();
				break;
			}
		}

		if (element == null) {
			throw new NoSuchElementException("None of the texts were found/clickable: " + candidateTexts);
		}

		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (Exception clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiLoad();
	}

	private Optional<WebElement> findDisplayedElementByText(final String text) {
		final String literal = toXPathLiteral(text);
		final List<By> locators = Arrays.asList(
				By.xpath("//*[self::button or self::a or @role='button'][normalize-space(.)=" + literal + "]"),
				By.xpath("//*[normalize-space(text())=" + literal + "]"),
				By.xpath("//*[contains(normalize-space(.)," + literal + ")]"));

		for (final By locator : locators) {
			final List<WebElement> candidates = driver.findElements(locator);
			for (final WebElement candidate : candidates) {
				try {
					if (candidate.isDisplayed()) {
						return Optional.of(candidate);
					}
				} catch (Exception ignored) {
					// Ignore stale references while probing list.
				}
			}
		}
		return Optional.empty();
	}

	private void waitForUiLoad() {
		try {
			wait.until((ExpectedCondition<Boolean>) wd -> {
				if (wd == null) {
					return false;
				}
				final Object result = ((JavascriptExecutor) wd).executeScript("return document.readyState");
				return "complete".equals(result);
			});
		} catch (Exception ignored) {
			// SPA transitions may not always toggle document readiness.
		}

		try {
			Thread.sleep(600);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean waitForTextVisible(final String text, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout)
					.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.),"
							+ toXPathLiteral(text) + ")]")));
			return true;
		} catch (TimeoutException timeoutException) {
			return false;
		}
	}

	private boolean isTextVisible(final String text) {
		return !driver.findElements(By.xpath("//*[contains(normalize-space(.)," + toXPathLiteral(text) + ")]")).stream()
				.filter(WebElement::isDisplayed).collect(Collectors.toList()).isEmpty();
	}

	private boolean isAnyTextVisible(final List<String> texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return true;
			}
		}
		return false;
	}

	private boolean isSidebarVisible() {
		final List<By> probes = Arrays.asList(
				By.xpath("//aside"),
				By.xpath("//*[@role='navigation']"),
				By.xpath("//*[contains(@class,'sidebar') or contains(@class,'SideBar')]"));
		for (final By probe : probes) {
			if (isElementVisible(probe)) {
				return true;
			}
		}
		return false;
	}

	private boolean isElementVisible(final By by) {
		for (final WebElement element : driver.findElements(by)) {
			try {
				if (element.isDisplayed()) {
					return true;
				}
			} catch (Exception ignored) {
				// Skip stale/invalid element references.
			}
		}
		return false;
	}

	private void typeBusinessNameIfFieldExists(final String value) {
		final List<By> fieldLocators = Arrays.asList(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));

		for (final By locator : fieldLocators) {
			final List<WebElement> inputs = driver.findElements(locator);
			for (final WebElement input : inputs) {
				if (input.isDisplayed()) {
					input.click();
					input.clear();
					input.sendKeys(value);
					waitForUiLoad();
					return;
				}
			}
		}
	}

	private void switchToNewTabIfOpened(final Set<String> beforeHandles) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(drv -> drv.getWindowHandles().size() > beforeHandles.size());
		} catch (TimeoutException ignored) {
			// Same-tab navigation is also valid.
		}

		final Set<String> afterHandles = driver.getWindowHandles();
		if (afterHandles.size() > beforeHandles.size()) {
			final Optional<String> newestHandle = afterHandles.stream()
					.filter(handle -> !beforeHandles.contains(handle)).findFirst();
			newestHandle.ifPresent(handle -> driver.switchTo().window(handle));
			waitForUiLoad();
		}
	}

	private void returnToApplicationTab(final String mainWindow) {
		try {
			if (mainWindow == null) {
				return;
			}
			if (!driver.getWindowHandle().equals(mainWindow)) {
				driver.close();
				driver.switchTo().window(mainWindow);
			} else {
				driver.navigate().back();
			}
			waitForUiLoad();
		} catch (Exception ignored) {
			// Best-effort cleanup to continue workflow.
		}
	}

	private boolean hasVisibleEmailText() {
		return driver.findElements(By.xpath("//*[contains(text(),'@')]")).stream().filter(WebElement::isDisplayed)
				.map(WebElement::getText).map(String::trim).anyMatch(text -> text.matches(".*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*"));
	}

	private boolean isUserNameVisible() {
		if (expectedUserName != null && !expectedUserName.isBlank()) {
			return isTextVisible(expectedUserName);
		}

		final List<String> blockedTerms = Arrays.asList(
				"informacion general",
				"información general",
				"business plan",
				"cambiar plan",
				"detalles de la cuenta",
				"tus negocios",
				"seccion legal",
				"sección legal");

		final List<WebElement> textNodes = driver.findElements(By.xpath("//h1|//h2|//h3|//h4|//p|//span|//div"));
		for (final WebElement node : textNodes) {
			if (!node.isDisplayed()) {
				continue;
			}
			final String text = node.getText().trim();
			if (text.isBlank() || text.length() < 3 || text.contains("@")) {
				continue;
			}
			final String normalized = stripAccents(text).toLowerCase();
			if (blockedTerms.stream().noneMatch(normalized::contains) && text.matches("[A-Za-zÀ-ÿ\\s'.-]{3,}")) {
				return true;
			}
		}

		return false;
	}

	private boolean hasBusinessListSignal() {
		if (isElementVisible(By.xpath("//table//tr[position()>1] | //*[@role='row'][position()>1]"))) {
			return true;
		}
		if (isElementVisible(By.xpath("//ul/li | //*[@role='listitem']"))) {
			return true;
		}
		return isTextVisible("Negocio");
	}

	private boolean hasLegalContentText() {
		final String bodyText = driver.findElement(By.tagName("body")).getText().trim();
		return bodyText.length() >= 80;
	}

	private void printFinalReport() {
		System.out.println("==== SaleADS Mi Negocio - Final Report ====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.printf("- %s: %s%n", entry.getKey(), entry.getValue() ? "PASS" : "FAIL");
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("---- Captured legal URLs ----");
			for (final Map.Entry<String, String> legal : legalUrls.entrySet()) {
				System.out.printf("- %s URL: %s%n", legal.getKey(), legal.getValue());
			}
		}
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
	}

	private void assertAllChecksPassed() {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> step : report.entrySet()) {
			if (!step.getValue()) {
				failedSteps.add(step.getKey());
			}
		}

		assertTrue(
				"Failed validation steps: " + failedSteps + ". Review screenshots in: " + screenshotDir.toAbsolutePath(),
				failedSteps.isEmpty());
	}

	private void captureScreenshot(final String fileName, final boolean fullPage) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final Dimension original = driver.manage().window().getSize();
		try {
			if (fullPage) {
				resizeWindowToFullPage();
			}
			final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path target = screenshotDir.resolve(fileName + ".png");
			Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception ignored) {
			// Screenshot is best effort evidence; do not interrupt workflow on capture issues.
		} finally {
			try {
				driver.manage().window().setSize(original);
			} catch (Exception ignored) {
				// Ignore resize failures.
			}
		}
	}

	private void captureFailureEvidence(final String prefix) {
		captureScreenshot(prefix, false);
	}

	private void resizeWindowToFullPage() {
		try {
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			final Long width = asLong(js.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);"));
			final Long height = asLong(js.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);"));
			if (width != null && height != null) {
				final int targetWidth = Math.min(Math.toIntExact(width), 1920);
				final int targetHeight = Math.min(Math.toIntExact(height), 5000);
				driver.manage().window().setSize(new Dimension(targetWidth, targetHeight));
				waitForUiLoad();
			}
		} catch (Exception ignored) {
			// Fall back to viewport capture.
		}
	}

	private Long asLong(final Object value) {
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		return null;
	}

	private String toXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}
		final String[] parts = text.split("'");
		return "concat('" + String.join("',\"'\",'", parts) + "')";
	}

	private String readEnv(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private String stripAccents(final String value) {
		return value
				.replace('á', 'a').replace('Á', 'A')
				.replace('é', 'e').replace('É', 'E')
				.replace('í', 'i').replace('Í', 'I')
				.replace('ó', 'o').replace('Ó', 'O')
				.replace('ú', 'u').replace('Ú', 'U')
				.replace('ñ', 'n').replace('Ñ', 'N');
	}
}
