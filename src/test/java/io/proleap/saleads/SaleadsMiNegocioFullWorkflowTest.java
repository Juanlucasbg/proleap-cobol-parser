package io.proleap.saleads;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.Objects;
import java.util.Set;

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
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws Exception {
		final boolean enabled = Boolean.parseBoolean(resolveConfig("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this E2E workflow.", enabled);

		evidenceDir = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(TIMESTAMP_FORMAT));
		Files.createDirectories(evidenceDir);

		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
		driver.manage().window().maximize();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		initializeReport();
		final String loginUrl = resolveConfig("SALEADS_LOGIN_URL", "").trim();
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
		} else {
			final String currentUrl = driver.getCurrentUrl();
			Assert.assertFalse(
				"Browser is not on SaleADS login page. Set SALEADS_LOGIN_URL or pre-open the login page.",
				currentUrl == null || currentUrl.startsWith("about:blank") || currentUrl.startsWith("data:"));
		}

		// Step 1: Login with Google
		clickByVisibleTextAny(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Login with Google"));
		waitForUiLoad();
		selectGoogleAccountIfVisible("juanlucasbarbiergarzon@gmail.com");
		waitForAppShell();
		takeScreenshot("01_dashboard_loaded");
		report.put("Login", true);

		// Step 2: Open Mi Negocio menu
		openMiNegocioMenu();
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02_mi_negocio_expanded");
		report.put("Mi Negocio menu", true);

		// Step 3: Validate Agregar Negocio modal
		clickByVisibleTextAny(Arrays.asList("Agregar Negocio"));
		waitForUiLoad();
		assertVisibleText("Crear Nuevo Negocio");
		assertFieldByLabelOrPlaceholder("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		takeScreenshot("03_agregar_negocio_modal");
		optionalTypeAndCancelBusinessName();
		report.put("Agregar Negocio modal", true);

		// Step 4: Open Administrar Negocios
		openMiNegocioMenu();
		clickByVisibleTextAny(Arrays.asList("Administrar Negocios"));
		assertVisibleTextAny(Arrays.asList("Información General", "Informacion General"));
		assertVisibleTextAny(Arrays.asList("Detalles de la Cuenta", "Detalles de la Cuenta"));
		assertVisibleText("Tus Negocios");
		assertVisibleTextAny(Arrays.asList("Sección Legal", "Seccion Legal"));
		takeScreenshot("04_administrar_negocios_full");
		report.put("Administrar Negocios view", true);

		// Step 5: Validate Información General
		assertLikelyUserNameVisible();
		assertLikelyEmailVisible();
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
		report.put("Información General", true);

		// Step 6: Validate Detalles de la Cuenta
		assertVisibleTextAny(Arrays.asList("Cuenta creada", "Cuenta Creada"));
		assertVisibleText("Estado activo");
		assertVisibleTextAny(Arrays.asList("Idioma seleccionado", "Idioma Seleccionado"));
		report.put("Detalles de la Cuenta", true);

		// Step 7: Validate Tus Negocios
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		report.put("Tus Negocios", true);

		// Step 8: Validate Términos y Condiciones
		termsUrl = openLegalLinkAndValidate(
			"Términos y Condiciones",
			Arrays.asList("Términos y Condiciones", "Terminos y Condiciones", "Terms and Conditions"),
			"08_terminos_y_condiciones");
		report.put("Términos y Condiciones", true);

		// Step 9: Validate Política de Privacidad
		privacyUrl = openLegalLinkAndValidate(
			"Política de Privacidad",
			Arrays.asList("Política de Privacidad", "Politica de Privacidad", "Privacy Policy"),
			"09_politica_de_privacidad");
		report.put("Política de Privacidad", true);
	}

	private WebDriver createDriver() {
		final String browser = resolveConfig("SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(resolveConfig("SALEADS_HEADLESS", "true"));

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			return new FirefoxDriver(firefoxOptions);
		case "edge":
			final EdgeOptions edgeOptions = new EdgeOptions();
			if (headless) {
				edgeOptions.addArguments("--headless=new");
			}
			edgeOptions.addArguments("--window-size=1920,1080");
			return new EdgeDriver(edgeOptions);
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			chromeOptions.addArguments("--window-size=1920,1080");
			chromeOptions.addArguments("--disable-dev-shm-usage");
			chromeOptions.addArguments("--no-sandbox");
			return new ChromeDriver(chromeOptions);
		}
	}

	private void initializeReport() {
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
	}

	private void openMiNegocioMenu() {
		clickByVisibleTextAny(Arrays.asList("Negocio", "Mi Negocio"));
		waitForUiLoad();
		try {
			if (!isTextVisible("Agregar Negocio", Duration.ofSeconds(2))) {
				clickByVisibleTextAny(Arrays.asList("Mi Negocio", "Negocio"));
				waitForUiLoad();
			}
		} catch (TimeoutException ignored) {
			clickByVisibleTextAny(Arrays.asList("Mi Negocio", "Negocio"));
			waitForUiLoad();
		}
	}

	private void waitForAppShell() {
		wait.until((ExpectedCondition<Boolean>) ignored -> {
			final boolean bodyPresent = !driver.findElements(By.tagName("body")).isEmpty();
			final boolean sidebarPresent = !driver.findElements(By.xpath(
				"//*[self::aside or @role='navigation' or contains(@class,'sidebar') or contains(@class,'sidenav')]")).isEmpty();
			return bodyPresent && sidebarPresent;
		});
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		final Duration shortWait = Duration.ofSeconds(8);
		if (!isTextVisible("Choose an account", shortWait)
			&& !isTextVisible("Elige una cuenta", shortWait)
			&& !isTextVisible(accountEmail, shortWait)) {
			return;
		}

		final List<WebElement> options = visibleElementsByXpath("//*[contains(translate(normalize-space(.),"
			+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),"
			+ "'" + accountEmail.toLowerCase(Locale.ROOT) + "')]");
		if (!options.isEmpty()) {
			clickElement(options.get(0));
			waitForUiLoad();
		}
	}

	private void optionalTypeAndCancelBusinessName() {
		final List<WebElement> nameField = findFieldByLabelOrPlaceholder("Nombre del Negocio");
		if (!nameField.isEmpty()) {
			final WebElement input = nameField.get(0);
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
		}

		clickByVisibleTextAny(Arrays.asList("Cancelar"));
		waitForUiLoad();
	}

	private String openLegalLinkAndValidate(final String linkText,
		final List<String> expectedHeadings,
		final String screenshotName) {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = driver.getWindowHandles();
		final String preClickUrl = driver.getCurrentUrl();

		clickByVisibleTextAny(textVariants(linkText));

		String targetWindow = originalWindow;
		final long end = System.currentTimeMillis() + DEFAULT_TIMEOUT.toMillis();
		while (System.currentTimeMillis() < end) {
			final Set<String> now = driver.getWindowHandles();
			if (now.size() > windowsBefore.size()) {
				for (final String handle : now) {
					if (!windowsBefore.contains(handle)) {
						targetWindow = handle;
						break;
					}
				}
				if (!Objects.equals(targetWindow, originalWindow)) {
					break;
				}
			}
			sleep(200);
		}

		driver.switchTo().window(targetWindow);
		waitForUiLoad();
		assertAnyVisibleHeading(expectedHeadings);
		assertLegalContentVisible();
		takeScreenshot(screenshotName);
		final String capturedUrl = driver.getCurrentUrl();

		if (!Objects.equals(targetWindow, originalWindow)) {
			driver.close();
			driver.switchTo().window(originalWindow);
		} else if (!Objects.equals(preClickUrl, capturedUrl)) {
			driver.navigate().back();
		}

		waitForUiLoad();
		return capturedUrl;
	}

	private void clickByVisibleTextAny(final List<String> textOptions) {
		Throwable last = null;
		for (final String text : textOptions) {
			try {
				clickByVisibleText(text);
				return;
			} catch (final Throwable t) {
				last = t;
			}
		}

		if (last instanceof RuntimeException) {
			throw (RuntimeException) last;
		}
		throw new IllegalStateException("Could not click any target texts: " + textOptions, last);
	}

	private void clickByVisibleText(final String text) {
		final String lower = text.toLowerCase(Locale.ROOT);
		final String xpath = "//*[self::button or self::a or self::span or self::div or self::p or self::li]"
			+ "[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),"
			+ "'" + lower + "')]";
		final long end = System.currentTimeMillis() + DEFAULT_TIMEOUT.toMillis();
		while (System.currentTimeMillis() < end) {
			final List<WebElement> candidates = visibleElementsByXpath(xpath);
			if (!candidates.isEmpty()) {
				clickElement(candidates.get(0));
				return;
			}
			sleep(250);
		}
		throw new TimeoutException("No clickable element found for text: " + text);
	}

	private void clickElement(final WebElement element) {
		wait.until(ignored -> element.isDisplayed() && element.isEnabled());
		element.click();
		waitForUiLoad();
	}

	private void assertVisibleText(final String text) {
		Assert.assertTrue("Expected visible text: " + text, isTextVisible(text, DEFAULT_TIMEOUT));
	}

	private void assertVisibleTextAny(final List<String> textOptions) {
		for (final String text : textOptions) {
			if (isTextVisible(text, Duration.ofSeconds(6))) {
				return;
			}
		}
		Assert.fail("Expected one of visible texts: " + textOptions);
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		final String lower = text.toLowerCase(Locale.ROOT);
		while (System.currentTimeMillis() < end) {
			final List<WebElement> matches = visibleElementsByXpath("//*[contains(translate(normalize-space(.),"
				+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'" + lower + "')]");
			if (!matches.isEmpty()) {
				return true;
			}
			sleep(250);
		}
		return false;
	}

	private void assertFieldByLabelOrPlaceholder(final String labelText) {
		Assert.assertFalse("Expected field for: " + labelText, findFieldByLabelOrPlaceholder(labelText).isEmpty());
	}

	private List<WebElement> findFieldByLabelOrPlaceholder(final String labelText) {
		final String lower = labelText.toLowerCase(Locale.ROOT);
		final List<WebElement> fields = new ArrayList<>();
		fields.addAll(visibleElementsByXpath("//input[contains(translate(@placeholder,'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
			+ "'abcdefghijklmnopqrstuvwxyz'),'" + lower + "')]"));
		fields.addAll(visibleElementsByXpath("//textarea[contains(translate(@placeholder,'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
			+ "'abcdefghijklmnopqrstuvwxyz'),'" + lower + "')]"));
		fields.addAll(visibleElementsByXpath("//label[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
			+ "'abcdefghijklmnopqrstuvwxyz'),'" + lower + "')]/following::input[1]"));
		fields.addAll(visibleElementsByXpath("//label[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
			+ "'abcdefghijklmnopqrstuvwxyz'),'" + lower + "')]/following::textarea[1]"));
		return fields;
	}

	private void assertLikelyUserNameVisible() {
		final List<WebElement> candidates = visibleElementsByXpath("//*[contains(@class,'name') or contains(@class,'user')]"
			+ "[string-length(normalize-space(.)) > 3]");
		if (!candidates.isEmpty()) {
			return;
		}

		// Fallback: look for non-email profile-like text near "Información General"
		final List<WebElement> nearSection = visibleElementsByXpath(
			"//*[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'información general')"
				+ " or contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'informacion general')]"
				+ "/following::*[string-length(normalize-space(.)) > 3 and not(contains(normalize-space(.),'@'))]");
		Assert.assertFalse("Expected likely user name visible", nearSection.isEmpty());
	}

	private void assertLikelyEmailVisible() {
		final List<WebElement> emails = visibleElementsByXpath("//*[contains(normalize-space(.),'@') and "
			+ "(contains(normalize-space(.),'.com') or contains(normalize-space(.),'.net') or contains(normalize-space(.),'.io'))]");
		Assert.assertFalse("Expected user email visible", emails.isEmpty());
	}

	private void assertAnyVisibleHeading(final List<String> headings) {
		boolean found = false;
		for (final String heading : headings) {
			if (isTextVisible(heading, Duration.ofSeconds(8))) {
				found = true;
				break;
			}
		}
		Assert.assertTrue("Expected one legal heading from: " + headings, found);
	}

	private void assertLegalContentVisible() {
		final List<WebElement> paragraphs = visibleElementsByXpath("//p[string-length(normalize-space(.)) > 80]");
		if (!paragraphs.isEmpty()) {
			return;
		}
		final List<WebElement> textBlocks = visibleElementsByXpath("//*[self::div or self::section]"
			+ "[string-length(normalize-space(.)) > 150]");
		Assert.assertFalse("Expected legal content text visible", textBlocks.isEmpty());
	}

	private List<WebElement> visibleElementsByXpath(final String xpath) {
		final List<WebElement> elements = driver.findElements(By.xpath(xpath));
		final List<WebElement> visible = new ArrayList<>();
		for (final WebElement element : elements) {
			if (element != null && element.isDisplayed()) {
				visible.add(element);
			}
		}
		return visible;
	}

	private void waitForUiLoad() {
		try {
			wait.until(ignored -> "complete".equals(
				((JavascriptExecutor) driver).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Some auth pages intentionally keep loading scripts; continue with fallback wait.
		}
		sleep(800);
	}

	private void takeScreenshot(final String name) {
		try {
			final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			final Path target = evidenceDir.resolve(name + ".png");
			Files.write(target, bytes);
		} catch (final Exception e) {
			throw new IllegalStateException("Failed taking screenshot '" + name + "': " + e.getMessage(), e);
		}
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null || report.isEmpty()) {
			return;
		}

		final StringBuilder out = new StringBuilder();
		out.append("# SaleADS Mi Negocio Full Workflow Report\n\n");
		out.append("Generated: ").append(LocalDateTime.now()).append('\n').append('\n');
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			out.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append('\n');
		}

		out.append('\n');
		out.append("## Captured URLs\n");
		out.append("- Términos y Condiciones URL: ").append(termsUrl).append('\n');
		out.append("- Política de Privacidad URL: ").append(privacyUrl).append('\n');

		Files.write(evidenceDir.resolve("final-report.md"), out.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String resolveConfig(final String key, final String defaultValue) {
		final String sys = System.getProperty(key);
		if (sys != null && !sys.trim().isEmpty()) {
			return sys.trim();
		}
		final String env = System.getenv(key);
		if (env != null && !env.trim().isEmpty()) {
			return env.trim();
		}
		return defaultValue;
	}

	private List<String> textVariants(final String input) {
		final List<String> variants = new ArrayList<>();
		variants.add(input);
		final String accentFree = input
			.replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
			.replace('Á', 'A').replace('É', 'E').replace('Í', 'I').replace('Ó', 'O').replace('Ú', 'U')
			.replace('ñ', 'n').replace('Ñ', 'N');
		if (!accentFree.equals(input)) {
			variants.add(accentFree);
		}
		return variants;
	}

	private void sleep(final long ms) {
		try {
			Thread.sleep(ms);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}
}
