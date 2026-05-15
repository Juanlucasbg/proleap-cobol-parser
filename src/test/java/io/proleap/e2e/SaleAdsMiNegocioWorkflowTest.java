package io.proleap.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> evidenceUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to execute this live workflow test.",
				Boolean.parseBoolean(readEnvOrDefault("SALEADS_E2E_ENABLED", "false")));

		driver = buildDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(readLongEnv("SALEADS_TIMEOUT_SECONDS", 30)));
		screenshotDir = createScreenshotDirectory();

		final String loginUrl = readEnv("SALEADS_LOGIN_URL");
		if (loginUrl != null) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() {
		try {
			printReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final boolean login = runSafely(this::stepLoginWithGoogle);
		report.put("Login", login);

		final boolean miNegocioMenu = login && runSafely(this::stepOpenMiNegocioMenu);
		report.put("Mi Negocio menu", miNegocioMenu);

		final boolean agregarNegocioModal = miNegocioMenu && runSafely(this::stepValidateAgregarNegocioModal);
		report.put("Agregar Negocio modal", agregarNegocioModal);

		final boolean administrarNegociosView = miNegocioMenu && runSafely(this::stepOpenAdministrarNegocios);
		report.put("Administrar Negocios view", administrarNegociosView);

		final boolean infoGeneral = administrarNegociosView && runSafely(this::stepValidateInformacionGeneral);
		report.put("Información General", infoGeneral);

		final boolean detallesCuenta = administrarNegociosView && runSafely(this::stepValidateDetallesCuenta);
		report.put("Detalles de la Cuenta", detallesCuenta);

		final boolean tusNegocios = administrarNegociosView && runSafely(this::stepValidateTusNegocios);
		report.put("Tus Negocios", tusNegocios);

		final boolean terminos = administrarNegociosView
				&& runSafely(() -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos"));
		report.put("Términos y Condiciones", terminos);

		final boolean privacidad = administrarNegociosView
				&& runSafely(() -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09-politica-privacidad"));
		report.put("Política de Privacidad", privacidad);

		Assert.assertTrue("Some workflow validations failed: " + failedSteps(), report.values().stream().allMatch(Boolean.TRUE::equals));
	}

	private boolean stepLoginWithGoogle() throws IOException {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google", "Continuar con Google", "Google");
		waitForUiToLoad();

		selectGoogleAccountIfPresent(GOOGLE_ACCOUNT_EMAIL);
		waitForUiToLoad();

		final boolean mainInterfaceVisible = isAnyElementVisible(
				By.xpath("//main | //div[contains(@class,'dashboard')] | //div[contains(@class,'app')]"));
		final boolean leftSidebarVisible = isAnyElementVisible(By.xpath("//aside | //nav[.//*[contains(normalize-space(), 'Negocio')]]"));

		captureScreenshot("01-dashboard-loaded");
		return mainInterfaceVisible && leftSidebarVisible;
	}

	private boolean stepOpenMiNegocioMenu() throws IOException {
		clickByVisibleText("Negocio");
		waitForUiToLoad();
		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		final boolean agregarVisible = waitForTextVisible("Agregar Negocio", Duration.ofSeconds(15));
		final boolean administrarVisible = waitForTextVisible("Administrar Negocios", Duration.ofSeconds(15));
		captureScreenshot("02-mi-negocio-menu-expanded");

		return agregarVisible && administrarVisible;
	}

	private boolean stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForUiToLoad();

		final boolean title = waitForTextVisible("Crear Nuevo Negocio", Duration.ofSeconds(15));
		final boolean input = isAnyElementVisible(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='nombreNegocio' or @aria-label='Nombre del Negocio' or @id='nombreNegocio']"));
		final boolean quota = waitForTextVisible("Tienes 2 de 3 negocios", Duration.ofSeconds(15));
		final boolean cancelar = waitForTextVisible("Cancelar", Duration.ofSeconds(15));
		final boolean crear = waitForTextVisible("Crear Negocio", Duration.ofSeconds(15));

		captureScreenshot("03-agregar-negocio-modal");

		typeInBusinessNameIfPresent("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		waitForUiToLoad();

		return title && input && quota && cancelar && crear;
	}

	private boolean stepOpenAdministrarNegocios() throws IOException {
		if (!isTextCurrentlyVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		final boolean infoGeneral = waitForTextVisible("Información General", Duration.ofSeconds(20));
		final boolean detallesCuenta = waitForTextVisible("Detalles de la Cuenta", Duration.ofSeconds(20));
		final boolean tusNegocios = waitForTextVisible("Tus Negocios", Duration.ofSeconds(20));
		final boolean seccionLegal = waitForTextVisible("Sección Legal", Duration.ofSeconds(20));

		captureScreenshot("04-administrar-negocios-view");
		return infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
	}

	private boolean stepValidateInformacionGeneral() {
		final boolean nameVisible = isAnyElementVisible(By.xpath(
				"//*[contains(@class,'user') and normalize-space()!=''] | //h1[normalize-space()!=''] | //h2[normalize-space()!='']"));
		final boolean emailVisible = isAnyElementVisible(By.xpath("//*[contains(text(), '@')]"));
		final boolean businessPlan = waitForTextVisible("BUSINESS PLAN", Duration.ofSeconds(10));
		final boolean cambiarPlan = waitForTextVisible("Cambiar Plan", Duration.ofSeconds(10));

		return nameVisible && emailVisible && businessPlan && cambiarPlan;
	}

	private boolean stepValidateDetallesCuenta() {
		final boolean cuentaCreada = waitForTextVisible("Cuenta creada", Duration.ofSeconds(10));
		final boolean estadoActivo = waitForTextVisible("Estado activo", Duration.ofSeconds(10));
		final boolean idiomaSeleccionado = waitForTextVisible("Idioma seleccionado", Duration.ofSeconds(10));
		return cuentaCreada && estadoActivo && idiomaSeleccionado;
	}

	private boolean stepValidateTusNegocios() {
		final boolean businessListVisible = isAnyElementVisible(
				By.xpath("//*[contains(@class,'business') and (self::ul or self::div or self::section)] | //table"));
		final boolean agregarNegocio = waitForTextVisible("Agregar Negocio", Duration.ofSeconds(10));
		final boolean quota = waitForTextVisible("Tienes 2 de 3 negocios", Duration.ofSeconds(10));
		return businessListVisible && agregarNegocio && quota;
	}

	private boolean stepValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		final String newHandle = waitForNewTab(handlesBefore, Duration.ofSeconds(10));
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiToLoad();
		}

		final boolean headingVisible = waitForTextVisible(expectedHeading, Duration.ofSeconds(15));
		final boolean legalBodyVisible = hasNonTrivialPageBodyText();
		final String finalUrl = driver.getCurrentUrl();
		evidenceUrls.put(linkText, finalUrl);
		captureScreenshot(screenshotName);

		if (newHandle != null) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return headingVisible && legalBodyVisible;
	}

	private WebDriver buildDriver() {
		final String browser = readEnvOrDefault("SALEADS_BROWSER", "chrome").toLowerCase();
		final boolean headless = Boolean.parseBoolean(readEnvOrDefault("SALEADS_HEADLESS", "true"));

		if ("firefox".equals(browser)) {
			final FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			return new FirefoxDriver(options);
		}

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		return new ChromeDriver(options);
	}

	private void clickByVisibleText(final String... preferredLabels) {
		Objects.requireNonNull(preferredLabels, "preferredLabels");
		for (final String label : preferredLabels) {
			final By locator = By.xpath(clickableLocatorForText(label));
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
				scrollIntoView(element);
				element.click();
				waitForUiToLoad();
				return;
			} catch (final TimeoutException ignored) {
				// continue to next candidate
			}
		}
		throw new NoSuchElementException("Could not click any element with labels: " + String.join(", ", preferredLabels));
	}

	private void selectGoogleAccountIfPresent(final String accountEmail) {
		if (!isTextCurrentlyVisible(accountEmail)) {
			return;
		}

		clickByVisibleText(accountEmail);
		waitForUiToLoad();
	}

	private void typeInBusinessNameIfPresent(final String businessName) {
		try {
			final By fieldBy = By.xpath(
					"//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1] | //input[@placeholder='Nombre del Negocio']");
			final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(fieldBy));
			input.clear();
			input.sendKeys(businessName);
		} catch (final TimeoutException ignored) {
			// Optional field interaction
		}
	}

	private boolean waitForTextVisible(final String text, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(By.xpath(textLocator(text))));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private String waitForNewTab(final Set<String> handlesBefore, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(d -> d.getWindowHandles().size() > handlesBefore.size());
			return driver.getWindowHandles().stream().filter(handle -> !handlesBefore.contains(handle)).findFirst().orElse(null);
		} catch (final TimeoutException ex) {
			return null;
		}
	}

	private boolean hasNonTrivialPageBodyText() {
		try {
			final WebElement body = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
			final String visibleText = body.getText();
			return visibleText != null && visibleText.trim().length() >= 120;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private boolean isAnyElementVisible(final By locator) {
		try {
			return !driver.findElements(locator).stream().filter(WebElement::isDisplayed).collect(Collectors.toList()).isEmpty();
		} catch (final Exception ex) {
			return false;
		}
	}

	private boolean isTextCurrentlyVisible(final String text) {
		return isAnyElementVisible(By.xpath(textLocator(text)));
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Some SPA states may not expose a stable readyState transition.
		}

		final By commonLoaders = By.cssSelector(
				"[aria-busy='true'], [data-testid*='loading'], [class*='loading'], [class*='spinner'], .loader, .spinner");
		try {
			new WebDriverWait(driver, Duration.ofSeconds(4)).until(ExpectedConditions.invisibilityOfElementLocated(commonLoaders));
		} catch (final Exception ignored) {
			// Best effort: not all pages expose deterministic loader selectors.
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center',inline:'nearest'});", element);
	}

	private void captureScreenshot(final String checkpoint) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String fileName = checkpoint + "-" + FILE_TS.format(LocalDateTime.now()) + ".png";
		final Path destination = screenshotDir.resolve(fileName);
		Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Screenshot captured: " + destination);
	}

	private Path createScreenshotDirectory() throws IOException {
		final String runStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Path.of("target", "saleads-e2e-screenshots", runStamp);
		Files.createDirectories(path);
		return path;
	}

	private boolean runSafely(final Step step) {
		try {
			return step.execute();
		} catch (final Exception ex) {
			System.out.println("Step failed with error: " + ex.getMessage());
			return false;
		}
	}

	private void printReport() {
		System.out.println("==== SaleADS Mi Negocio Final Report ====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL"));
		}
		if (!evidenceUrls.isEmpty()) {
			System.out.println("==== Captured Legal URLs ====");
			for (final Map.Entry<String, String> entry : evidenceUrls.entrySet()) {
				System.out.println(entry.getKey() + ": " + entry.getValue());
			}
		}
	}

	private String failedSteps() {
		return report.entrySet().stream().filter(entry -> !Boolean.TRUE.equals(entry.getValue())).map(Map.Entry::getKey)
				.collect(Collectors.joining(", "));
	}

	private String readEnv(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			return null;
		}
		return value;
	}

	private String readEnvOrDefault(final String key, final String defaultValue) {
		final String value = readEnv(key);
		return value != null ? value : defaultValue;
	}

	private long readLongEnv(final String key, final long defaultValue) {
		final String value = readEnv(key);
		if (value == null) {
			return defaultValue;
		}

		try {
			return Long.parseLong(value);
		} catch (final NumberFormatException ex) {
			return defaultValue;
		}
	}

	private String clickableLocatorForText(final String text) {
		final String literal = xpathLiteral(text);
		return String.join(" | ",
				"//button[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]",
				"//a[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]",
				"//*[@role='button' and (normalize-space()=" + literal + " or contains(normalize-space(), " + literal + "))]",
				"//*[self::span or self::div][normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]");
	}

	private String textLocator(final String text) {
		final String literal = xpathLiteral(text);
		return "//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]";
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder concat = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			concat.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				concat.append(", \"'\", ");
			}
		}
		concat.append(")");
		return concat.toString();
	}

	@FunctionalInterface
	private interface Step {
		boolean execute() throws Exception;
	}
}
