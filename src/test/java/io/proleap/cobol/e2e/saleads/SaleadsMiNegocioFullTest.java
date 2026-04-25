package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> reportDetails = new ArrayList<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws Exception {
		final boolean enabled = Boolean.parseBoolean(getEnvOrDefault("RUN_SALEADS_MI_NEGOCIO_TEST", "false"));
		Assume.assumeTrue("Set RUN_SALEADS_MI_NEGOCIO_TEST=true to run SaleADS E2E workflow.", enabled);

		for (final String field : REPORT_FIELDS) {
			report.put(field, Boolean.FALSE);
		}

		evidenceDir = Files.createDirectories(Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));
		driver = createDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		driver.manage().window().setSize(new Dimension(1600, 1200));
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);

		final String loginUrl = getEnvOrDefault("SALEADS_LOGIN_URL", "").trim();
		if (!loginUrl.isEmpty()) {
			driver.get(loginUrl);
			waitForUiToSettle();
		}
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioModuleWorkflow() throws IOException {
		stepLoginWithGoogle();
		stepOpenMiNegocioMenu();
		stepValidateAgregarNegocioModal();
		stepOpenAdministrarNegocios();
		stepValidateInformacionGeneral();
		stepValidateDetallesCuenta();
		stepValidateTusNegocios();
		stepValidateTerminosCondiciones();
		stepValidatePoliticaPrivacidad();

		final boolean allPassed = report.values().stream().allMatch(Boolean::booleanValue);
		assertTrue("One or more validations failed. Check " + evidenceDir.resolve("final-report.txt"), allPassed);
	}

	private void stepLoginWithGoogle() throws IOException {
		boolean passed = false;
		try {
			final boolean loginClicked = clickByVisibleText("Sign in with Google", "Iniciar sesión con Google",
					"Iniciar sesion con Google", "Continuar con Google", "Login with Google");
			if (!loginClicked) {
				record("Login", false, "Google login button was not found.");
				captureScreenshot("01-login-button-missing");
				return;
			}

			waitForUiToSettle();
			chooseGoogleAccountIfPrompted(ACCOUNT_EMAIL);

			final boolean appVisible = waitAnyTextVisible(Duration.ofSeconds(45), "Mi Negocio", "Negocio", "Dashboard",
					"Panel", "Inicio");
			final boolean sidebarVisible = isSidebarVisible();

			passed = appVisible && sidebarVisible;
			captureScreenshot("01-dashboard-loaded");
			record("Login", passed,
					passed ? "Main interface and sidebar are visible."
							: "Main interface or sidebar could not be confirmed after login.");
		} catch (final Exception ex) {
			captureScreenshot("01-login-error");
			record("Login", false, "Unexpected error: " + ex.getMessage());
		}
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		try {
			clickByVisibleText("Negocio");
			waitForUiToSettle();
			clickByVisibleText("Mi Negocio");
			waitForUiToSettle();

			final boolean submenuExpanded = waitAnyTextVisible(Duration.ofSeconds(10), "Agregar Negocio",
					"Administrar Negocios");
			final boolean addBusinessVisible = isAnyTextVisible("Agregar Negocio");
			final boolean manageBusinessVisible = isAnyTextVisible("Administrar Negocios");
			final boolean passed = submenuExpanded && addBusinessVisible && manageBusinessVisible;

			captureScreenshot("02-mi-negocio-expanded");
			record("Mi Negocio menu", passed, passed ? "Submenu expanded and both options are visible."
					: "Could not validate submenu expansion or required options.");
		} catch (final Exception ex) {
			captureScreenshot("02-mi-negocio-error");
			record("Mi Negocio menu", false, "Unexpected error: " + ex.getMessage());
		}
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		try {
			if (!isAnyTextVisible("Agregar Negocio")) {
				clickByVisibleText("Mi Negocio");
				waitForUiToSettle();
			}

			final boolean clicked = clickByVisibleText("Agregar Negocio");
			if (!clicked) {
				record("Agregar Negocio modal", false, "Agregar Negocio option is not clickable.");
				captureScreenshot("03-agregar-negocio-not-clickable");
				return;
			}

			final boolean titleVisible = waitAnyTextVisible(Duration.ofSeconds(15), "Crear Nuevo Negocio");
			final boolean fieldVisible = isElementVisible(By.xpath(
					"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"));
			final boolean quotaVisible = waitAnyTextVisible(Duration.ofSeconds(10), "Tienes 2 de 3 negocios");
			final boolean cancelVisible = isAnyTextVisible("Cancelar");
			final boolean createVisible = isAnyTextVisible("Crear Negocio");

			final boolean passed = titleVisible && fieldVisible && quotaVisible && cancelVisible && createVisible;

			if (fieldVisible) {
				typeInBusinessName("Negocio Prueba Automatización");
			}
			clickByVisibleText("Cancelar");
			waitForUiToSettle();

			captureScreenshot("03-agregar-negocio-modal");
			record("Agregar Negocio modal", passed, passed
					? "Modal title, field, quota text, and action buttons were validated."
					: "One or more modal validations failed.");
		} catch (final Exception ex) {
			captureScreenshot("03-agregar-negocio-error");
			record("Agregar Negocio modal", false, "Unexpected error: " + ex.getMessage());
		}
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		try {
			if (!isAnyTextVisible("Administrar Negocios")) {
				clickByVisibleText("Mi Negocio");
				waitForUiToSettle();
			}

			final boolean clicked = clickByVisibleText("Administrar Negocios");
			if (!clicked) {
				record("Administrar Negocios view", false, "Administrar Negocios option is not clickable.");
				captureScreenshot("04-administrar-negocios-not-clickable");
				return;
			}

			waitForUiToSettle();
			final boolean general = waitAnyTextVisible(Duration.ofSeconds(20), "Información General");
			final boolean details = isAnyTextVisible("Detalles de la Cuenta");
			final boolean businesses = isAnyTextVisible("Tus Negocios");
			final boolean legal = waitAnyTextVisible(Duration.ofSeconds(10), "Sección Legal", "Seccion Legal");
			final boolean passed = general && details && businesses && legal;

			captureScreenshot("04-administrar-negocios-view");
			record("Administrar Negocios view", passed,
					passed ? "All account sections are visible." : "Account sections are incomplete.");
		} catch (final Exception ex) {
			captureScreenshot("04-administrar-negocios-error");
			record("Administrar Negocios view", false, "Unexpected error: " + ex.getMessage());
		}
	}

	private void stepValidateInformacionGeneral() throws IOException {
		try {
			final String bodyText = getPageText();
			final boolean emailVisible = EMAIL_PATTERN.matcher(bodyText).find();
			final boolean nameVisible = inferUserNameVisible(bodyText);
			final boolean planVisible = waitAnyTextVisible(Duration.ofSeconds(10), "BUSINESS PLAN");
			final boolean changePlanVisible = isAnyTextVisible("Cambiar Plan");
			final boolean passed = nameVisible && emailVisible && planVisible && changePlanVisible;

			captureScreenshot("05-informacion-general");
			record("Información General", passed, passed
					? "User identity, plan text, and action button are visible."
					: "One or more Información General elements are missing.");
		} catch (final Exception ex) {
			captureScreenshot("05-informacion-general-error");
			record("Información General", false, "Unexpected error: " + ex.getMessage());
		}
	}

	private void stepValidateDetallesCuenta() throws IOException {
		try {
			final boolean created = waitAnyTextVisible(Duration.ofSeconds(10), "Cuenta creada");
			final boolean active = waitAnyTextVisible(Duration.ofSeconds(10), "Estado activo");
			final boolean language = waitAnyTextVisible(Duration.ofSeconds(10), "Idioma seleccionado");
			final boolean passed = created && active && language;

			captureScreenshot("06-detalles-cuenta");
			record("Detalles de la Cuenta", passed,
					passed ? "Cuenta creada, Estado activo, and Idioma seleccionado are visible."
							: "Missing required Detalles de la Cuenta values.");
		} catch (final Exception ex) {
			captureScreenshot("06-detalles-cuenta-error");
			record("Detalles de la Cuenta", false, "Unexpected error: " + ex.getMessage());
		}
	}

	private void stepValidateTusNegocios() throws IOException {
		try {
			final boolean listVisible = waitAnyTextVisible(Duration.ofSeconds(10), "Tus Negocios");
			final boolean addButton = isAnyTextVisible("Agregar Negocio");
			final boolean quota = waitAnyTextVisible(Duration.ofSeconds(10), "Tienes 2 de 3 negocios");
			final boolean passed = listVisible && addButton && quota;

			captureScreenshot("07-tus-negocios");
			record("Tus Negocios", passed,
					passed ? "Business list section, Add button, and quota text are visible."
							: "Missing required Tus Negocios elements.");
		} catch (final Exception ex) {
			captureScreenshot("07-tus-negocios-error");
			record("Tus Negocios", false, "Unexpected error: " + ex.getMessage());
		}
	}

	private void stepValidateTerminosCondiciones() throws IOException {
		validateLegalDocument("Términos y Condiciones", Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
				"Términos y Condiciones", "08-terminos-condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		validateLegalDocument("Política de Privacidad", Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
				"Política de Privacidad", "09-politica-privacidad");
	}

	private void validateLegalDocument(final String linkText, final List<String> expectedHeadings, final String reportField,
			final String screenshotPrefix) throws IOException {
		try {
			final String appHandle = driver.getWindowHandle();
			final String appUrl = getCurrentUrl();
			final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());

			final boolean clicked = clickByVisibleText(linkText);
			if (!clicked) {
				record(reportField, false, "Could not click legal link: " + linkText);
				captureScreenshot(screenshotPrefix + "-click-error");
				return;
			}

			waitForLegalNavigation(beforeHandles, appUrl);
			final Optional<String> newHandle = getNewWindowHandle(beforeHandles, driver.getWindowHandles());
			final boolean openedNewTab = newHandle.isPresent();
			if (openedNewTab) {
				driver.switchTo().window(newHandle.get());
				waitForUiToSettle();
			}

			final boolean headingVisible = expectedHeadings.stream()
					.anyMatch(heading -> waitAnyTextVisible(Duration.ofSeconds(20), heading));
			final String legalText = getPageText();
			final boolean legalContentVisible = legalText != null && legalText.trim().length() > 120;
			final boolean passed = headingVisible && legalContentVisible;

			captureScreenshot(screenshotPrefix);
			capturedUrls.put(reportField, getCurrentUrl());
			record(reportField, passed, passed ? "Heading and legal content are visible."
					: "Heading or legal content could not be validated.");

			if (openedNewTab) {
				driver.close();
				driver.switchTo().window(appHandle);
				waitForUiToSettle();
			} else if (!getCurrentUrl().equals(appUrl)) {
				driver.navigate().back();
				waitForUiToSettle();
			}
		} catch (final Exception ex) {
			captureScreenshot(screenshotPrefix + "-error");
			record(reportField, false, "Unexpected error: " + ex.getMessage());
		}
	}

	private WebDriver createDriver() throws MalformedURLException {
		final String browser = getEnvOrDefault("SALEADS_BROWSER", "chrome").toLowerCase(Locale.ROOT);
		final boolean headless = Boolean.parseBoolean(getEnvOrDefault("SALEADS_HEADLESS", "false"));
		final String remoteUrl = getEnvOrDefault("SELENIUM_REMOTE_URL", "").trim();

		switch (browser) {
		case "firefox":
			final FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("--headless");
			}
			return remoteUrl.isEmpty() ? new FirefoxDriver(firefoxOptions)
					: new RemoteWebDriver(new URL(remoteUrl), firefoxOptions);
		case "chrome":
		default:
			final ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.addArguments("--disable-gpu", "--window-size=1600,1200");
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			return remoteUrl.isEmpty() ? new ChromeDriver(chromeOptions)
					: new RemoteWebDriver(new URL(remoteUrl), chromeOptions);
		}
	}

	private void chooseGoogleAccountIfPrompted(final String email) {
		try {
			final String initialHandle = driver.getWindowHandle();
			final Set<String> initialHandles = new LinkedHashSet<>(driver.getWindowHandles());

			try {
				new WebDriverWait(driver, Duration.ofSeconds(15)).until((ExpectedCondition<Boolean>) d -> {
					if (d.getWindowHandles().size() > initialHandles.size()) {
						return Boolean.TRUE;
					}
					return isAnyTextVisible(email);
				});
			} catch (final TimeoutException ignored) {
				return;
			}

			final Optional<String> newHandle = getNewWindowHandle(initialHandles, driver.getWindowHandles());
			if (newHandle.isPresent()) {
				driver.switchTo().window(newHandle.get());
				waitForUiToSettle();
			}

			if (isAnyTextVisible(email)) {
				clickByVisibleText(email);
				waitForUiToSettle();
			}

			if (driver.getWindowHandles().contains(initialHandle)) {
				driver.switchTo().window(initialHandle);
			} else if (!driver.getWindowHandles().isEmpty()) {
				driver.switchTo().window(driver.getWindowHandles().iterator().next());
			}
			waitForUiToSettle();
		} catch (final Exception ignored) {
			// Login can continue if account selector is skipped because session is already authenticated.
		}
	}

	private boolean clickByVisibleText(final String... texts) {
		for (final String text : texts) {
			try {
				final Optional<WebElement> candidate = waitForDisplayedElementByText(text, Duration.ofSeconds(6));
				if (candidate.isPresent()) {
					clickElement(candidate.get());
					return true;
				}
			} catch (final Exception ignored) {
				// Try next text.
			}
		}
		return false;
	}

	private void clickElement(final WebElement element) {
		try {
			scrollIntoView(element);
			element.click();
		} catch (final Exception clickError) {
			final WebElement clickable = (WebElement) ((JavascriptExecutor) driver).executeScript(
					"const el = arguments[0]; return el.closest('button,a,[role=\"button\"],[role=\"menuitem\"],li,[tabindex]') || el;",
					element);
			scrollIntoView(clickable);
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
		}
		waitForUiToSettle();
	}

	private Optional<WebElement> waitForDisplayedElementByText(final String text, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		try {
			return Optional.ofNullable(localWait.until(d -> {
				for (final WebElement element : findDisplayedElementsByText(text)) {
					if (element.isDisplayed()) {
						return element;
					}
				}
				return null;
			}));
		} catch (final TimeoutException ex) {
			return Optional.empty();
		}
	}

	private List<WebElement> findDisplayedElementsByText(final String text) {
		final String literal = xpathLiteral(text);
		final String xpath = "//button[normalize-space(.)=" + literal + " or contains(normalize-space(.), " + literal + ")]"
				+ " | //a[normalize-space(.)=" + literal + " or contains(normalize-space(.), " + literal + ")]"
				+ " | //*[@role='button' and (normalize-space(.)=" + literal + " or contains(normalize-space(.), " + literal
				+ "))]"
				+ " | //*[@role='menuitem' and (normalize-space(.)=" + literal + " or contains(normalize-space(.), " + literal
				+ "))]"
				+ " | //*[self::span or self::div or self::li or self::label or self::p or self::h1 or self::h2 or self::h3 or self::h4]"
				+ "[normalize-space(.)=" + literal + " or contains(normalize-space(.), " + literal + ")]";
		final List<WebElement> results = new ArrayList<>();
		for (final WebElement element : driver.findElements(By.xpath(xpath))) {
			if (element.isDisplayed()) {
				results.add(element);
			}
		}
		return results;
	}

	private boolean waitAnyTextVisible(final Duration timeout, final String... texts) {
		final long endNanos = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < endNanos) {
			for (final String text : texts) {
				if (isAnyTextVisible(text)) {
					return true;
				}
			}
			sleep(300);
		}
		return false;
	}

	private boolean isAnyTextVisible(final String text) {
		final List<WebElement> elements = findDisplayedElementsByText(text);
		return !elements.isEmpty();
	}

	private boolean isElementVisible(final By locator) {
		try {
			return new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(d -> d.findElements(locator).stream().anyMatch(WebElement::isDisplayed));
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private boolean isSidebarVisible() {
		final List<WebElement> sidebars = driver.findElements(By.xpath("//aside | //nav"));
		for (final WebElement sidebar : sidebars) {
			if (sidebar.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void typeInBusinessName(final String businessName) {
		final List<By> locators = Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));
		for (final By locator : locators) {
			final List<WebElement> fields = driver.findElements(locator);
			for (final WebElement field : fields) {
				if (field.isDisplayed()) {
					field.clear();
					field.sendKeys(businessName);
					waitForUiToSettle();
					return;
				}
			}
		}
	}

	private boolean inferUserNameVisible(final String bodyText) {
		if (waitAnyTextVisible(Duration.ofSeconds(3), "Nombre", "Usuario")) {
			return true;
		}

		final List<String> lines = new ArrayList<>();
		for (final String line : bodyText.split("\\R")) {
			final String trimmed = line.trim();
			if (!trimmed.isEmpty()) {
				lines.add(trimmed);
			}
		}

		for (int i = 0; i < lines.size(); i++) {
			if (EMAIL_PATTERN.matcher(lines.get(i)).find()) {
				for (int j = Math.max(0, i - 2); j <= Math.min(lines.size() - 1, i + 2); j++) {
					final String candidate = lines.get(j);
					if (!EMAIL_PATTERN.matcher(candidate).find() && containsLetters(candidate)
							&& !candidate.equalsIgnoreCase("Información General") && !candidate.equalsIgnoreCase("BUSINESS PLAN")
							&& !candidate.equalsIgnoreCase("Cambiar Plan")) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean containsLetters(final String value) {
		for (int i = 0; i < value.length(); i++) {
			if (Character.isLetter(value.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	private Optional<String> getNewWindowHandle(final Set<String> beforeHandles, final Set<String> afterHandles) {
		for (final String handle : afterHandles) {
			if (!beforeHandles.contains(handle)) {
				return Optional.of(handle);
			}
		}
		return Optional.empty();
	}

	private void waitForLegalNavigation(final Set<String> beforeHandles, final String previousUrl) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20)).until((ExpectedCondition<Boolean>) d -> {
				if (d.getWindowHandles().size() > beforeHandles.size()) {
					return Boolean.TRUE;
				}
				return !getCurrentUrl().equals(previousUrl);
			});
		} catch (final TimeoutException ignored) {
			// Some environments can load legal content in the same route while keeping the URL.
		}
		waitForUiToSettle();
	}

	private void waitForUiToSettle() {
		try {
			wait.until(d -> {
				final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(state);
			});
		} catch (final Exception ignored) {
			// Best effort only.
		}
		sleep(500);
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});",
				element);
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private void record(final String field, final boolean passed, final String detail) {
		report.put(field, passed);
		reportDetails.add((passed ? "PASS" : "FAIL") + " | " + field + " | " + detail);
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("evidence_dir=" + evidenceDir.toAbsolutePath());
		lines.add("");
		lines.add("Result by requested report field:");
		for (final String field : REPORT_FIELDS) {
			lines.add(String.format("- %s: %s", field, report.getOrDefault(field, Boolean.FALSE) ? "PASS" : "FAIL"));
		}

		if (!capturedUrls.isEmpty()) {
			lines.add("");
			lines.add("Captured legal URLs:");
			for (final Map.Entry<String, String> entry : capturedUrls.entrySet()) {
				lines.add("- " + entry.getKey() + ": " + entry.getValue());
			}
		}

		if (!reportDetails.isEmpty()) {
			lines.add("");
			lines.add("Execution details:");
			lines.addAll(reportDetails);
		}

		Files.write(evidenceDir.resolve("final-report.txt"), lines, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
	}

	private void captureScreenshot(final String name) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		final Path outputPath = evidenceDir.resolve(name + ".png");
		Files.write(outputPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE);
	}

	private String getCurrentUrl() {
		try {
			return Optional.ofNullable(driver.getCurrentUrl()).orElse("");
		} catch (final Exception ex) {
			return "";
		}
	}

	private String getPageText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception ex) {
			return "";
		}
	}

	private String getEnvOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(",\"'\",");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}
}
