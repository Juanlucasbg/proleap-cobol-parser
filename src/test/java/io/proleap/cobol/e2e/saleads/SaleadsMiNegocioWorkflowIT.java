package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchWindowException;
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

public class SaleadsMiNegocioWorkflowIT {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> report = new LinkedHashMap<>();

	private final Path evidenceDir = Paths
			.get(getSetting("saleads.evidenceDir", "SALEADS_EVIDENCE_DIR", "target/saleads-evidence"));
	private final Duration timeout = Duration.ofSeconds(getLongSetting("saleads.timeoutSeconds",
			"SALEADS_TIMEOUT_SECONDS", 40));
	private final Duration postClickPause = Duration.ofMillis(getLongSetting("saleads.postClickPauseMs",
			"SALEADS_POST_CLICK_PAUSE_MS", 700));

	private WebDriver driver;
	private WebDriverWait wait;
	private String appWindowHandle;
	private String termsUrl;
	private String privacyUrl;

	@Before
	public void setUp() throws IOException {
		Files.createDirectories(evidenceDir);
		driver = createDriver();
		wait = new WebDriverWait(driver, timeout);

		driver.manage().window().setSize(new Dimension(1920, 1080));
		driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(120));

		final String loginUrl = getSetting("saleads.loginUrl", "SALEADS_LOGIN_URL", "");
		if (!loginUrl.isBlank()) {
			driver.navigate().to(loginUrl);
		}

		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();

		if ("about:blank".equals(driver.getCurrentUrl())) {
			fail("Browser is on about:blank. Pass SALEADS_LOGIN_URL/saleads.loginUrl at runtime so the test can open the active environment login page.");
		}
	}

	@After
	public void tearDown() throws IOException {
		writeReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final List<String> failures = new ArrayList<>();

		runStep("Login", failures, this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", failures, this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", failures, this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", failures, this::stepOpenAdministrarNegocios);
		runStep("Información General", failures, this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", failures, this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", failures, this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", failures, this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", failures, this::stepValidatePoliticaDePrivacidad);

		writeReport();

		if (!failures.isEmpty()) {
			fail("SaleADS Mi Negocio workflow validation failed:\n - " + String.join("\n - ", failures));
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesion con Google", "Iniciar sesión con Google",
				"Continuar con Google", "Google");

		final Optional<WebElement> accountOption = findVisibleElementByTexts(Duration.ofSeconds(15),
				GOOGLE_ACCOUNT_EMAIL);
		if (accountOption.isPresent()) {
			clickAndWait(accountOption.get());
		}

		waitForAnyVisibleText("Negocio", "Mi Negocio");
		assertSidebarVisible();

		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		waitForAnyVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");

		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");

		waitForVisibleText("Crear Nuevo Negocio");
		waitForVisibleText("Nombre del Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");

		captureScreenshot("03-agregar-negocio-modal");

		final WebElement nombreNegocioInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio') or contains(@name,'nombre')]")));
		nombreNegocioInput.click();
		nombreNegocioInput.clear();
		nombreNegocioInput.sendKeys("Negocio Prueba Automatizacion");

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(
				"//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioSubmenuVisible();
		clickByVisibleText("Administrar Negocios");

		waitForVisibleText("Informacion General", "Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Seccion Legal", "Sección Legal");

		captureScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement infoSection = waitForSection("Informacion General", "Información General");

		final String sectionText = normalize(infoSection.getText());
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(sectionText);
		assertTrue("User email is not visible in Informacion General.", emailMatcher.find());

		final List<String> lines = Arrays.stream(sectionText.split("\n")).map(String::trim).filter(line -> !line.isEmpty())
				.toList();
		final boolean hasLikelyName = lines.stream().anyMatch(line -> !line.equalsIgnoreCase("Informacion General")
				&& !line.equalsIgnoreCase("Información General") && !EMAIL_PATTERN.matcher(line).matches()
				&& !line.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN")
				&& !line.equalsIgnoreCase("Cambiar Plan"));
		assertTrue("User name is not visible in Informacion General.", hasLikelyName);

		waitForVisibleText("BUSINESS PLAN");
		clickableByVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		waitForSection("Detalles de la Cuenta");
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement businessesSection = waitForSection("Tus Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");

		final List<WebElement> possibleBusinessEntries = businessesSection.findElements(By.xpath(
				".//li|.//tr|.//article|.//div[contains(@class,'business')]|.//div[contains(@class,'card')]"));
		assertTrue("Business list is not visible in Tus Negocios.", !possibleBusinessEntries.isEmpty());
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		ensureLegalSectionVisible();
		final NavigationResult result = clickLegalLinkAndHandleTab("Terminos y Condiciones", "Términos y Condiciones");
		termsUrl = driver.getCurrentUrl();
		waitForAnyVisibleText("Terminos y Condiciones", "Términos y Condiciones");
		assertLegalContentVisible();
		captureScreenshot("05-terminos-y-condiciones");
		returnToApplication(result);
	}

	private void stepValidatePoliticaDePrivacidad() throws Exception {
		ensureLegalSectionVisible();
		final NavigationResult result = clickLegalLinkAndHandleTab("Politica de Privacidad", "Política de Privacidad");
		privacyUrl = driver.getCurrentUrl();
		waitForAnyVisibleText("Politica de Privacidad", "Política de Privacidad");
		assertLegalContentVisible();
		captureScreenshot("06-politica-de-privacidad");
		returnToApplication(result);
	}

	private NavigationResult clickLegalLinkAndHandleTab(final String... texts) {
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String urlBeforeClick = driver.getCurrentUrl();

		clickByVisibleText(texts);

		wait.until(d -> {
			final boolean openedNewTab = d.getWindowHandles().size() > handlesBeforeClick.size();
			final boolean sameTabNavigated = !d.getCurrentUrl().equals(urlBeforeClick);
			return openedNewTab || sameTabNavigated;
		});

		final Set<String> handlesAfterClick = new LinkedHashSet<>(driver.getWindowHandles());
		handlesAfterClick.removeAll(handlesBeforeClick);

		if (!handlesAfterClick.isEmpty()) {
			final String newHandle = handlesAfterClick.iterator().next();
			driver.switchTo().window(newHandle);
			waitForUiToLoad();
			return new NavigationResult(true, urlBeforeClick);
		}

		waitForUiToLoad();
		return new NavigationResult(false, urlBeforeClick);
	}

	private void returnToApplication(final NavigationResult navigationResult) {
		if (navigationResult.newTabOpened) {
			driver.close();
			try {
				driver.switchTo().window(appWindowHandle);
			} catch (final NoSuchWindowException ignored) {
				final String fallback = driver.getWindowHandles().iterator().next();
				driver.switchTo().window(fallback);
			}
			waitForUiToLoad();
			return;
		}

		driver.navigate().back();
		waitForUiToLoad();
	}

	private void assertLegalContentVisible() {
		final String text = normalize(driver.findElement(By.tagName("body")).getText());
		assertTrue("Legal page content should be visible.", text.length() > 200);
	}

	private void ensureMiNegocioSubmenuVisible() {
		if (!isAnyElementVisibleByText("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}
		waitForVisibleText("Administrar Negocios");
	}

	private void ensureLegalSectionVisible() {
		waitForSection("Seccion Legal", "Sección Legal");
	}

	private void runStep(final String stepName, final List<String> failures, final ThrowingRunnable action) {
		try {
			action.run();
			report.put(stepName, Boolean.TRUE);
		} catch (final Exception | AssertionError e) {
			report.put(stepName, Boolean.FALSE);
			failures.add(stepName + " -> " + e.getMessage());
		}
	}

	private void clickByVisibleText(final String... texts) {
		final WebElement element = clickableByVisibleText(texts);
		clickAndWait(element);
	}

	private WebElement clickableByVisibleText(final String... texts) {
		final Optional<WebElement> optional = findClickableElementByTexts(timeout, texts);
		if (optional.isPresent()) {
			return optional.get();
		}

		throw new TimeoutException("Could not find clickable element by visible text: " + Arrays.toString(texts));
	}

	private Optional<WebElement> findClickableElementByTexts(final Duration waitDuration, final String... texts) {
		final WebDriverWait shortWait = new WebDriverWait(driver, waitDuration);
		for (final String text : texts) {
			final By by = By.xpath("//*[self::button or self::a or @role='button' or self::div or self::span]"
					+ "[contains(normalize-space(.), " + toXpathLiteral(text) + ")]");
			try {
				return Optional.of(shortWait.until(ExpectedConditions.elementToBeClickable(by)));
			} catch (final TimeoutException ignored) {
				// Try next text variant.
			}
		}
		return Optional.empty();
	}

	private Optional<WebElement> findVisibleElementByTexts(final Duration waitDuration, final String... texts) {
		final WebDriverWait shortWait = new WebDriverWait(driver, waitDuration);
		for (final String text : texts) {
			final By by = By.xpath("//*[contains(normalize-space(.), " + toXpathLiteral(text) + ")]");
			try {
				return Optional.of(shortWait.until(ExpectedConditions.visibilityOfElementLocated(by)));
			} catch (final TimeoutException ignored) {
				// Try next text variant.
			}
		}
		return Optional.empty();
	}

	private void waitForVisibleText(final String... texts) {
		waitForAnyVisibleText(texts);
	}

	private void waitForAnyVisibleText(final String... texts) {
		final Optional<WebElement> optional = findVisibleElementByTexts(timeout, texts);
		if (optional.isEmpty()) {
			throw new TimeoutException("Expected visible text not found: " + Arrays.toString(texts));
		}
	}

	private boolean isAnyElementVisibleByText(final String... texts) {
		return findVisibleElementByTexts(Duration.ofSeconds(2), texts).isPresent();
	}

	private WebElement waitForSection(final String... headings) {
		for (final String heading : headings) {
			final By sectionByHeading = By.xpath(
					"//section[.//*[contains(normalize-space(.), " + toXpathLiteral(heading) + ")]]"
							+ "|//div[.//*[contains(normalize-space(.), " + toXpathLiteral(heading)
							+ ")] and (.//h1 or .//h2 or .//h3)]");
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(sectionByHeading));
			} catch (final TimeoutException ignored) {
				// Try next heading variant.
			}
		}

		throw new TimeoutException("Could not find section with heading(s): " + Arrays.toString(headings));
	}

	private void assertSidebarVisible() {
		final By sidebar = By.xpath("//aside | //nav[contains(@class, 'sidebar')] | //nav");
		wait.until(ExpectedConditions.visibilityOfElementLocated(sidebar));
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(driver -> {
			final Object readyState = ((JavascriptExecutor) driver).executeScript("return document.readyState");
			return "complete".equals(readyState) || "interactive".equals(readyState);
		});

		try {
			Thread.sleep(postClickPause.toMillis());
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private Path captureScreenshot(final String checkpointName) throws IOException {
		final String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
		final Path destination = evidenceDir.resolve(checkpointName + "-" + timestamp + ".png");
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		return destination;
	}

	private void writeReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("# SaleADS Mi Negocio Workflow Report");
		lines.add("");
		lines.add("| Validation | Status |");
		lines.add("| --- | --- |");
		lines.add(formatStatusLine("Login"));
		lines.add(formatStatusLine("Mi Negocio menu"));
		lines.add(formatStatusLine("Agregar Negocio modal"));
		lines.add(formatStatusLine("Administrar Negocios view"));
		lines.add(formatStatusLine("Información General"));
		lines.add(formatStatusLine("Detalles de la Cuenta"));
		lines.add(formatStatusLine("Tus Negocios"));
		lines.add(formatStatusLine("Términos y Condiciones"));
		lines.add(formatStatusLine("Política de Privacidad"));
		lines.add("");
		lines.add("## URLs");
		lines.add("- Terminos y Condiciones: " + safeValue(termsUrl));
		lines.add("- Politica de Privacidad: " + safeValue(privacyUrl));

		Files.write(evidenceDir.resolve("saleads-mi-negocio-report.md"), lines);
	}

	private String formatStatusLine(final String stepName) {
		final boolean status = report.getOrDefault(stepName, false);
		return "| " + stepName + " | " + (status ? "PASS" : "FAIL") + " |";
	}

	private String safeValue(final String value) {
		return value == null || value.isBlank() ? "NOT_CAPTURED" : value;
	}

	private WebDriver createDriver() {
		final String browser = getSetting("saleads.browser", "SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(getSetting("saleads.headless", "SALEADS_HEADLESS", "true"));

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
			chromeOptions.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu",
					"--window-size=1920,1080");
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			return new ChromeDriver(chromeOptions);
		}
	}

	private String getSetting(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		return defaultValue;
	}

	private long getLongSetting(final String propertyName, final String envName, final long defaultValue) {
		final String value = getSetting(propertyName, envName, String.valueOf(defaultValue));
		try {
			return Long.parseLong(value.trim());
		} catch (final NumberFormatException e) {
			return defaultValue;
		}
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder literalBuilder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			literalBuilder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				literalBuilder.append(", \"'\", ");
			}
		}
		literalBuilder.append(")");
		return literalBuilder.toString();
	}

	private String normalize(final String value) {
		return value == null ? "" : value.replace('\u00A0', ' ').trim();
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static class NavigationResult {
		private final boolean newTabOpened;
		@SuppressWarnings("unused")
		private final String previousUrl;

		private NavigationResult(final boolean newTabOpened, final String previousUrl) {
			this.newTabOpened = newTabOpened;
			this.previousUrl = previousUrl;
		}
	}
}
