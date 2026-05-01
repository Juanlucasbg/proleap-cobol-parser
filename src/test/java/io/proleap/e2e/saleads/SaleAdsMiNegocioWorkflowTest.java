package io.proleap.e2e.saleads;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end UI workflow for SaleADS "Mi Negocio" module.
 *
 * This test is environment agnostic by design:
 * - It does not hardcode any SaleADS domain.
 * - It relies on visible text selectors whenever possible.
 * - It accepts runtime configuration through environment variables.
 *
 * Required environment variables:
 * - SALEADS_E2E_ENABLED=true
 * - SALEADS_LOGIN_URL=<current environment login page>
 *
 * Optional environment variables:
 * - SALEADS_BROWSER=chrome|firefox (default: chrome)
 * - SALEADS_HEADLESS=true|false (default: false)
 * - SALEADS_E2E_TIMEOUT_SECONDS=<int> (default: 45)
 */
public class SaleAdsMiNegocioWorkflowTest {

	private static final String LOGIN_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
	private static final DateTimeFormatter RUN_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> evidenceUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private String appWindowHandle;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run SaleADS workflow test.",
				Boolean.parseBoolean(env("SALEADS_E2E_ENABLED", "false")));

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the current environment login page URL.",
				loginUrl != null && !loginUrl.isBlank());

		final int timeoutSeconds = parseIntEnv("SALEADS_E2E_TIMEOUT_SECONDS", 45);
		wait = null;
		driver = createDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);
		driver.manage().window().setSize(new Dimension(1600, 1000));

		evidenceDir = Paths.get("target", "saleads-evidence", RUN_TS_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		driver.get(loginUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
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
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		printFinalReport();

		final List<String> failed = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		Assert.assertTrue("Workflow contains failed validations: " + failed, failed.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByTextCandidates(List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Iniciar con Google", "Google"));
		selectGoogleAccountIfVisible(LOGIN_ACCOUNT_EMAIL);
		ensureApplicationLoaded();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			clickByTextCandidates(List.of("Mi Negocio"));
		}

		assertVisibleText("Negocio");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByTextCandidates(List.of("Agregar Negocio"));

		final WebElement modal = waitForDialogWithTitle("Crear Nuevo Negocio");
		Assert.assertNotNull("Modal 'Crear Nuevo Negocio' must be visible.", modal);

		assertVisibleTextInside(modal, "Nombre del Negocio");
		Assert.assertTrue("The business name input must exist.",
				hasVisibleElementInside(modal, By.xpath(".//input[not(@type='hidden')]")));
		assertVisibleTextInside(modal, "Tienes 2 de 3 negocios");
		assertClickableTextInside(modal, "Cancelar");
		assertClickableTextInside(modal, "Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal");

		final WebElement input = firstVisibleInside(modal, By.xpath(".//input[not(@type='hidden')]"));
		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatización");

		final WebElement cancelar = firstClickableInside(modal, "Cancelar");
		clickAndWait(cancelar);
		wait.until(ExpectedConditions.invisibilityOf(modal));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios")) {
			clickByTextCandidates(List.of("Mi Negocio"));
		}

		clickByTextCandidates(List.of("Administrar Negocios"));
		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		takeFullPageScreenshot("04-administrar-negocios-view-full");
	}

	private void stepValidateInformacionGeneral() throws Exception {
		assertVisibleText("Información General");

		final Optional<String> visibleEmail = findVisibleEmailText();
		Assert.assertTrue("A user email must be visible in Información General.", visibleEmail.isPresent());

		final boolean userNameVisible = isLikelyNameVisibleNearEmail(visibleEmail.get()) || isTextVisible("Nombre");
		Assert.assertTrue("A user name must be visible in Información General.", userNameVisible);

		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() throws Exception {
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() throws Exception {
		final WebElement section = findSectionByHeading("Tus Negocios");
		Assert.assertNotNull("Section 'Tus Negocios' must be visible.", section);
		Assert.assertTrue("Business list content must be visible.",
				visibleTextLength(section) > "Tus Negocios".length() + "Tienes 2 de 3 negocios".length());
		assertClickableTextInside(section, "Agregar Negocio");
		assertVisibleTextInside(section, "Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminosCondiciones() throws Exception {
		validateLegalDocument("Términos y Condiciones", "Términos y Condiciones", "05-terminos-y-condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		validateLegalDocument("Política de Privacidad", "Política de Privacidad", "06-politica-de-privacidad");
	}

	private void validateLegalDocument(final String linkText, final String headingText, final String screenshotName)
			throws Exception {
		final String appUrlBefore = driver.getCurrentUrl();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByTextCandidates(List.of(linkText));

		final String destinationHandle = waitForNewWindowOrNavigation(handlesBefore, appUrlBefore);
		final boolean openedNewTab = destinationHandle != null;
		if (openedNewTab) {
			driver.switchTo().window(destinationHandle);
		}

		waitForUiToLoad();
		assertVisibleText(headingText);
		Assert.assertTrue("Legal content text should be visible for " + headingText,
				hasVisibleElement(By.xpath("//*[self::p or self::article or self::div][string-length(normalize-space())>80]")));

		final String finalUrl = driver.getCurrentUrl();
		evidenceUrls.put(headingText, finalUrl);
		takeScreenshot(screenshotName);

		if (openedNewTab) {
			driver.close();
			switchToApplicationWindow();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
			switchToApplicationWindow();
		}
	}

	private void runStep(final String stepName, final StepAction action) {
		boolean success = false;
		try {
			action.run();
			success = true;
		} catch (Exception error) {
			System.err.println("[FAIL] " + stepName + " -> " + error.getMessage());
			try {
				takeScreenshot("failure-" + slug(stepName));
			} catch (Exception screenshotError) {
				System.err.println("Could not capture failure screenshot for " + stepName + ": "
						+ screenshotError.getMessage());
			}
		}

		report.put(stepName, success);
	}

	private void printFinalReport() {
		System.out.println();
		System.out.println("===== SaleADS Mi Negocio Workflow Final Report =====");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println("- " + entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}

		if (!evidenceUrls.isEmpty()) {
			System.out.println("Legal URLs:");
			for (Map.Entry<String, String> entry : evidenceUrls.entrySet()) {
				System.out.println("- " + entry.getKey() + ": " + entry.getValue());
			}
		}

		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("====================================================");
		System.out.println();
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final long deadlineMillis = System.currentTimeMillis() + 20000L;
		while (System.currentTimeMillis() < deadlineMillis) {
			for (String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (isTextVisible(email)) {
					final WebElement account = firstVisible(By.xpath("//*[contains(normalize-space()," + xpathLiteral(email) + ")]"));
					clickAndWait(account);
					return;
				}
			}

			if (isApplicationShellVisible()) {
				switchToApplicationWindow();
				return;
			}

			sleep(400);
		}

		switchToApplicationWindow();
	}

	private void ensureApplicationLoaded() {
		final WebDriverWait loginWait = new WebDriverWait(driver, Duration.ofSeconds(60));
		loginWait.until((ExpectedCondition<Boolean>) d -> {
			switchToApplicationWindow();
			return isApplicationShellVisible();
		});
		Assert.assertTrue("Main application interface must be visible after login.", isApplicationShellVisible());
	}

	private boolean isApplicationShellVisible() {
		return hasVisibleElement(By.xpath("//aside|//nav|//*[contains(translate(@class,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'sidebar')]"))
				&& (isTextVisible("Negocio") || isTextVisible("Mi Negocio") || isTextVisible("Administrar Negocios"));
	}

	private void switchToApplicationWindow() {
		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			if (isApplicationShellVisible() || !driver.getCurrentUrl().contains("accounts.google.com")) {
				return;
			}
		}

		for (String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (!driver.getCurrentUrl().contains("accounts.google.com")) {
				appWindowHandle = handle;
				return;
			}
		}
	}

	private String waitForNewWindowOrNavigation(final Set<String> handlesBefore, final String urlBefore) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(15));
		try {
			shortWait.until((ExpectedCondition<Boolean>) d -> {
				if (d.getWindowHandles().size() > handlesBefore.size()) {
					return true;
				}
				return !d.getCurrentUrl().equals(urlBefore);
			});
		} catch (TimeoutException ignored) {
			// Keep current context for assertions below.
		}

		final Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > handlesBefore.size()) {
			for (String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					return handle;
				}
			}
		}

		return null;
	}

	private WebElement waitForDialogWithTitle(final String title) {
		final String titleLiteral = xpathLiteral(title);
		return wait.until(d -> {
			final List<WebElement> dialogs = d
					.findElements(By.xpath("//div[@role='dialog']|//*[@aria-modal='true']|//*[contains(translate(@class,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'modal')]"));
			for (WebElement dialog : dialogs) {
				if (dialog.isDisplayed()) {
					final List<WebElement> titleElements = dialog.findElements(By.xpath(".//*[normalize-space()=" + titleLiteral
							+ " or contains(normalize-space()," + titleLiteral + ")]"));
					if (!titleElements.isEmpty()) {
						return dialog;
					}
				}
			}
			return null;
		});
	}

	private WebElement findSectionByHeading(final String headingText) {
		final String literal = xpathLiteral(headingText);
		final List<WebElement> headings = driver.findElements(By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::div or self::span][normalize-space()="
				+ literal + " or contains(normalize-space()," + literal + ")]"));
		for (WebElement heading : headings) {
			if (!heading.isDisplayed()) {
				continue;
			}
			final List<WebElement> candidates = heading.findElements(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
			if (!candidates.isEmpty()) {
				return candidates.get(0);
			}
		}
		return null;
	}

	private Optional<String> findVisibleEmailText() {
		final List<WebElement> candidates = driver.findElements(By.xpath("//*[contains(normalize-space(),'@')]"));
		for (WebElement candidate : candidates) {
			if (!candidate.isDisplayed()) {
				continue;
			}

			final String text = normalizedText(candidate);
			if (!text.isBlank() && text.contains("@")) {
				final String[] tokens = text.split("\\s+");
				for (String token : tokens) {
					if (EMAIL_PATTERN.matcher(token.trim()).matches()) {
						return Optional.of(token.trim());
					}
				}
			}
		}
		return Optional.empty();
	}

	private boolean isLikelyNameVisibleNearEmail(final String email) {
		final String literal = xpathLiteral(email);
		final List<WebElement> emailElements = driver
				.findElements(By.xpath("//*[contains(normalize-space()," + literal + ")]"));
		for (WebElement emailElement : emailElements) {
			if (!emailElement.isDisplayed()) {
				continue;
			}

			final List<WebElement> nearby = emailElement.findElements(By.xpath(
					"./ancestor::*[position()<=4]//*[self::h1 or self::h2 or self::h3 or self::span or self::div or self::p]"));
			for (WebElement candidate : nearby) {
				if (!candidate.isDisplayed()) {
					continue;
				}
				final String text = normalizedText(candidate);
				if (text.isBlank() || text.contains("@")) {
					continue;
				}
				if (!isKnownStaticLabel(text) && text.length() >= 3) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isKnownStaticLabel(final String text) {
		final String normalized = text.toLowerCase(Locale.ROOT);
		return normalized.contains("información general") || normalized.contains("detalles de la cuenta")
				|| normalized.contains("tus negocios") || normalized.contains("sección legal")
				|| normalized.contains("business plan") || normalized.contains("cambiar plan")
				|| normalized.contains("cuenta creada") || normalized.contains("estado activo")
				|| normalized.contains("idioma seleccionado") || normalized.contains("tienes 2 de 3 negocios")
				|| normalized.contains("agregar negocio") || normalized.contains("administrar negocios")
				|| normalized.contains("términos y condiciones") || normalized.contains("política de privacidad");
	}

	private void clickByTextCandidates(final List<String> candidates) {
		Exception lastFailure = null;
		for (String text : candidates) {
			try {
				final WebElement element = firstClickable(byTextMatcher(text));
				clickAndWait(element);
				return;
			} catch (Exception error) {
				lastFailure = error;
			}
		}
		throw new AssertionError("Could not click any text candidate: " + candidates,
				lastFailure == null ? null : lastFailure);
	}

	private void clickAndWait(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (Exception clickError) {
			try {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			} catch (Exception jsError) {
				new Actions(driver).moveToElement(element).click().perform();
			}
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(d -> {
			try {
				final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(state) || "interactive".equals(state);
			} catch (Exception ignore) {
				return true;
			}
		});
		sleep(300);
	}

	private void assertVisibleText(final String text) {
		final By by = byTextMatcher(text);
		wait.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private void assertVisibleTextInside(final WebElement container, final String text) {
		final List<WebElement> matches = container.findElements(byTextMatcher(text));
		Assert.assertTrue("Expected visible text inside container: " + text,
				matches.stream().anyMatch(WebElement::isDisplayed));
	}

	private void assertClickableTextInside(final WebElement container, final String text) {
		Assert.assertNotNull("Expected clickable text inside container: " + text, firstClickableInside(container, text));
	}

	private WebElement firstClickableInside(final WebElement container, final String text) {
		final String literal = xpathLiteral(text);
		final By by = By.xpath(".//*[self::button or self::a or @role='button' or self::span or self::div][normalize-space()="
				+ literal + " or contains(normalize-space()," + literal + ")]");
		for (WebElement element : container.findElements(by)) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		return null;
	}

	private WebElement firstVisibleInside(final WebElement container, final By by) {
		for (WebElement element : container.findElements(by)) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		throw new AssertionError("Expected visible element inside container for locator: " + by);
	}

	private boolean hasVisibleElementInside(final WebElement container, final By by) {
		return container.findElements(by).stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean hasVisibleElement(final By by) {
		return driver.findElements(by).stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean isTextVisible(final String text) {
		return hasVisibleElement(byTextMatcher(text));
	}

	private WebElement firstVisible(final By by) {
		return driver.findElements(by).stream().filter(WebElement::isDisplayed).findFirst()
				.orElseThrow(() -> new AssertionError("Expected visible element for locator: " + by));
	}

	private WebElement firstClickable(final By by) {
		final List<WebElement> elements = driver.findElements(by);
		for (WebElement element : elements) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		throw new AssertionError("Expected clickable/visible element for locator: " + by);
	}

	private int visibleTextLength(final WebElement element) {
		return normalizedText(element).replaceAll("\\s+", " ").trim().length();
	}

	private String normalizedText(final WebElement element) {
		return element.getText() == null ? "" : element.getText().trim();
	}

	private By byTextMatcher(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")]");
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
		for (int index = 0; index < chars.length; index++) {
			final String token = String.valueOf(chars[index]);
			if (index > 0) {
				builder.append(",");
			}
			if ("'".equals(token)) {
				builder.append("\"'\"");
			} else {
				builder.append("'").append(token).append("'");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private void takeScreenshot(final String name) throws IOException {
		final String filename = slug(name) + ".png";
		final Path target = evidenceDir.resolve(filename);
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Screenshot saved: " + target.toAbsolutePath());
	}

	private void takeFullPageScreenshot(final String name) throws IOException {
		final Dimension original = driver.manage().window().getSize();
		try {
			final Long width = (Long) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth);");
			final Long height = (Long) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			final int targetWidth = Math.min(width == null ? original.width : width.intValue() + 80, 2200);
			final int targetHeight = Math.min(height == null ? original.height : height.intValue() + 120, 5000);
			driver.manage().window().setSize(new Dimension(targetWidth, targetHeight));
			waitForUiToLoad();
			takeScreenshot(name);
		} finally {
			driver.manage().window().setSize(original);
			waitForUiToLoad();
		}
	}

	private String slug(final String value) {
		final String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
	}

	private WebDriver createDriver() {
		final String browser = env("SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "false"));

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			return new FirefoxDriver(firefoxOptions);
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.addArguments("--disable-dev-shm-usage", "--no-sandbox");
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			return new ChromeDriver(chromeOptions);
		}
	}

	private static String env(final String key, final String defaultValue) {
		return System.getenv().getOrDefault(key, defaultValue);
	}

	private static int parseIntEnv(final String key, final int defaultValue) {
		try {
			return Integer.parseInt(env(key, String.valueOf(defaultValue)));
		} catch (NumberFormatException error) {
			return defaultValue;
		}
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
