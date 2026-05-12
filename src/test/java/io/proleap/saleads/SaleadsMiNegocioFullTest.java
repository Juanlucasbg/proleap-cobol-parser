package io.proleap.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
	private static final Pattern FULL_NAME_PATTERN = Pattern.compile("[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]{2,}\\s+[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]{2,}");

	private final Map<String, String> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set -Dsaleads.e2e.enabled=true to run this workflow.", Boolean.getBoolean("saleads.e2e.enabled"));

		final String loginUrl = resolveLoginUrl();
		Assume.assumeTrue("Provide -Dsaleads.login.url=<login-page> or SALEADS_LOGIN_URL.", loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--disable-dev-shm-usage", "--no-sandbox", "--window-size=1600,1100");

		driver = new ChromeDriver(options);
		driver.manage().window().setSize(new Dimension(1600, 1100));
		wait = new WebDriverWait(driver, Duration.ofSeconds(Long.parseLong(System.getProperty("saleads.timeout.seconds", "25"))));
		screenshotDir = Files.createDirectories(Paths.get("target", "saleads-e2e-screenshots",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));

		REPORT_FIELDS.forEach(field -> report.put(field, "NOT RUN"));

		driver.get(loginUrl);
		waitForUiLoad();
	}

	@After
	public void tearDown() {
		printFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::validateLoginWithGoogle);
		runStep("Mi Negocio menu", this::validateMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegocios);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", () -> validateLegalPage("Términos y Condiciones", "05-terminos-y-condiciones"));
		runStep("Política de Privacidad", () -> validateLegalPage("Política de Privacidad", "06-politica-de-privacidad"));

		assertTrue("Workflow failures:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private void validateLoginWithGoogle() throws Exception {
		clickAnyVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Login con Google");
		selectGoogleAccountIfPresent("juanlucasbarbiergarzon@gmail.com");

		assertTextVisible("Negocio");
		assertSidebarVisible();
		takeScreenshot("01-dashboard-loaded");
	}

	private void validateMiNegocioMenu() throws Exception {
		optionalClick("Negocio");
		clickAnyVisibleText("Mi Negocio");

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickAnyVisibleText("Agregar Negocio");

		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final Optional<WebElement> nameInput = findDisplayedElement(By.xpath(
				"//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder,'Nombre del Negocio')]"));
		if (nameInput.isPresent()) {
			nameInput.get().click();
			nameInput.get().clear();
			nameInput.get().sendKeys("Negocio Prueba Automatización");
		}

		clickAnyVisibleText("Cancelar");
	}

	private void openAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(3))) {
			clickAnyVisibleText("Mi Negocio");
		}
		clickAnyVisibleText("Administrar Negocios");

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		final String sectionText = section.getText();

		assertTrue("Expected a user email in Información General.", EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("Expected a visible user name in Información General.", FULL_NAME_PATTERN.matcher(sectionText).find());
		assertTrue("Expected BUSINESS PLAN text in Información General.", sectionText.contains("BUSINESS PLAN"));
		assertTrue("Expected Cambiar Plan button in Información General.", sectionText.contains("Cambiar Plan"));
	}

	private void validateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTrue("Expected business capacity text in Tus Negocios.", section.getText().contains("Tienes 2 de 3 negocios"));

		final int listLikeElements = section.findElements(By.cssSelector("li, [role='listitem'], tr, article, .card")).size();
		assertTrue("Expected visible business list/content in Tus Negocios.", listLikeElements > 0 || section.getText().length() > 50);
	}

	private void validateLegalPage(final String linkText, final String screenshotName) throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBeforeClick = driver.getWindowHandles();
		final String appUrlBeforeClick = driver.getCurrentUrl();

		clickAnyVisibleText(linkText);

		String activeWindow = appWindow;
		try {
			wait.until(d -> d.getWindowHandles().size() > windowsBeforeClick.size()
					|| !Objects.equals(appUrlBeforeClick, d.getCurrentUrl()));
		} catch (final TimeoutException e) {
			fail("No navigation happened after clicking " + linkText + ".");
		}

		final Set<String> windowsAfterClick = driver.getWindowHandles();
		if (windowsAfterClick.size() > windowsBeforeClick.size()) {
			for (final String handle : windowsAfterClick) {
				if (!windowsBeforeClick.contains(handle)) {
					activeWindow = handle;
					break;
				}
			}
			driver.switchTo().window(activeWindow);
			waitForUiLoad();
		}

		assertTextVisible(linkText);
		assertLegalContentVisible();
		legalUrls.put(linkText, driver.getCurrentUrl());
		takeScreenshot(screenshotName);

		if (!activeWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad();
		} else if (!Objects.equals(appUrlBeforeClick, driver.getCurrentUrl())) {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void assertLegalContentVisible() {
		final Optional<WebElement> content = findDisplayedElement(
				By.xpath("//article//*[string-length(normalize-space()) > 60] | //main//*[string-length(normalize-space()) > 60] | //p[string-length(normalize-space()) > 80]"));
		assertTrue("Expected visible legal content text.", content.isPresent());
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			report.put(reportField, "PASS");
		} catch (final Throwable throwable) {
			report.put(reportField, "FAIL");
			failures.add(reportField + ": " + throwable.getMessage());
			try {
				takeScreenshot("fail-" + sanitizeFileName(reportField));
			} catch (final Exception ignored) {
				// ignore screenshot errors to preserve original failure
			}
		}
	}

	private void assertTextVisible(final String text) {
		final String literal = xpathLiteral(text);
		final By locator = By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void clickAnyVisibleText(final String... candidateTexts) {
		for (final String candidate : candidateTexts) {
			final Optional<WebElement> element = findClickableElementByText(candidate, Duration.ofSeconds(8));
			if (element.isPresent()) {
				clickElement(element.get());
				waitForUiLoad();
				return;
			}
		}
		fail("Could not find clickable element for any of: " + String.join(", ", candidateTexts));
	}

	private Optional<WebElement> findClickableElementByText(final String text, final Duration timeout) {
		final String literal = xpathLiteral(text);
		final By locator = By.xpath(
				"//button[normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")]"
						+ " | //a[normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")]"
						+ " | //*[@role='button' and (normalize-space()=" + literal + " or contains(normalize-space()," + literal + "))]"
						+ " | //*[normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")]/ancestor::*[self::button or self::a or @role='button'][1]");

		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.presenceOfElementLocated(locator));
			final List<WebElement> candidates = driver.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					return Optional.of(candidate);
				}
			}
		} catch (final TimeoutException ignored) {
			// optional lookup
		}
		return Optional.empty();
	}

	private Optional<WebElement> findDisplayedElement(final By by) {
		final List<WebElement> elements = driver.findElements(by);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return Optional.of(element);
			}
		}
		return Optional.empty();
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void selectGoogleAccountIfPresent(final String accountEmail) {
		final String currentWindow = driver.getWindowHandle();
		final Set<String> windows = driver.getWindowHandles();

		for (final String window : windows) {
			driver.switchTo().window(window);
			final Optional<WebElement> accountOption = findClickableElementByText(accountEmail, Duration.ofSeconds(6));
			if (accountOption.isPresent()) {
				clickElement(accountOption.get());
				waitForUiLoad();
				break;
			}
		}

		if (driver.getWindowHandles().contains(currentWindow)) {
			driver.switchTo().window(currentWindow);
		}
		waitForUiLoad();
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			final String literal = xpathLiteral(text);
			final By locator = By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")]");
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private WebElement findSectionByHeading(final String heading) {
		final String literal = xpathLiteral(heading);
		final By locator = By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][normalize-space()=" + literal + "]"
				+ "/ancestor::*[self::section or self::article or self::div][1]");
		wait.until(ExpectedConditions.presenceOfElementLocated(locator));
		final Optional<WebElement> section = findDisplayedElement(locator);
		if (section.isPresent()) {
			return section.get();
		}
		throw new IllegalStateException("Could not locate section heading: " + heading);
	}

	private void assertSidebarVisible() {
		final List<WebElement> sidebars = driver.findElements(By.cssSelector("aside, nav"));
		for (final WebElement sidebar : sidebars) {
			if (sidebar.isDisplayed()) {
				return;
			}
		}
		fail("Expected visible sidebar navigation.");
	}

	private void optionalClick(final String text) {
		final Optional<WebElement> element = findClickableElementByText(text, Duration.ofSeconds(4));
		element.ifPresent(this::clickElement);
		waitForUiLoad();
	}

	private void waitForUiLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// keep trying spinner-based wait below
		}

		try {
			wait.until(d -> d.findElements(
					By.cssSelector("[aria-busy='true'], .loading, .spinner, .ant-spin-spinning, [data-testid='loading']"))
					.isEmpty());
		} catch (final Exception ignored) {
			// some pages do not expose loading indicators
		}
	}

	private void takeScreenshot(final String name) throws IOException {
		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = screenshotDir.resolve(sanitizeFileName(name) + ".png");
		Files.copy(screenshotFile.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private String resolveLoginUrl() {
		final String fromProperty = System.getProperty("saleads.login.url");
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}

		final String fromEnv = System.getenv("SALEADS_LOGIN_URL");
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		return null;
	}

	private String sanitizeFileName(final String name) {
		return name.toLowerCase().replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
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
			if (i > 0) {
				builder.append(", ");
			}
			if (chars[i] == '\'') {
				builder.append("\"'\"");
			} else {
				builder.append("'").append(chars[i]).append("'");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private void printFinalReport() {
		System.out.println("========== SaleADS Mi Negocio Final Report ==========");
		for (final String field : REPORT_FIELDS) {
			System.out.println(field + ": " + report.getOrDefault(field, "NOT RUN"));
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("--- Legal URLs ---");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(entry.getKey() + ": " + entry.getValue());
			}
		}
		if (screenshotDir != null) {
			System.out.println("Screenshots: " + screenshotDir.toAbsolutePath());
		}
		System.out.println("=====================================================");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
