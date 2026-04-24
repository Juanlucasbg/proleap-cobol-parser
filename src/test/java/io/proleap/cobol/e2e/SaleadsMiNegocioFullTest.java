package io.proleap.cobol.e2e;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration SHORT_WAIT = Duration.ofSeconds(5);
	private static final Duration MEDIUM_WAIT = Duration.ofSeconds(20);
	private static final Duration LONG_WAIT = Duration.ofSeconds(45);
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final List<String> evidenceNotes = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		for (final String field : REPORT_FIELDS) {
			stepStatus.put(field, "FAIL (not executed)");
		}

		final String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
		evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);

		driver = createDriver();
		wait = new WebDriverWait(driver, MEDIUM_WAIT);

		final String loginUrl = envOrProperty("SALEADS_LOGIN_URL", "saleads.login.url");
		if (!isBlank(loginUrl)) {
			driver.navigate().to(loginUrl.trim());
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() {
		writeFinalReportFile();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		final boolean loginPass = runStep("Login", this::stepLoginWithGoogle);

		if (loginPass) {
			appWindowHandle = driver.getWindowHandle();
			runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			runStep("Información General", this::stepValidateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			runStep("Tus Negocios", this::stepValidateTusNegocios);
			runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones", "terminos_y_condiciones"));
			runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad", "politica_privacidad"));
		} else {
			markRemainingBlockedByLogin();
		}

		final String report = buildFinalReport();
		System.out.println(report);

		Assert.assertTrue("One or more validations failed.\n" + report, allStepsPassed());
	}

	private boolean stepLoginWithGoogle() {
		if (isMainInterfaceVisible()) {
			takeScreenshot("01_dashboard_loaded");
			return true;
		}

		final boolean clickedLogin = clickFirstMatchingText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google",
				"Inicia sesión con Google", "Continuar con Google", "Acceder con Google", "Login with Google", "Google"));
		if (!clickedLogin) {
			return false;
		}

		waitForUiLoad();

		clickIfTextVisible("juanlucasbarbiergarzon@gmail.com");

		final boolean interfaceVisible = waitUntil(this::isMainInterfaceVisible, LONG_WAIT);
		if (interfaceVisible) {
			takeScreenshot("01_dashboard_loaded");
		}
		return interfaceVisible;
	}

	private boolean stepOpenMiNegocioMenu() {
		final boolean sidebarVisible = isSidebarVisible();
		final boolean negocioVisible = isTextVisible("Negocio", SHORT_WAIT);

		if (isTextVisible("Mi Negocio", SHORT_WAIT)) {
			clickText("Mi Negocio");
		} else if (negocioVisible) {
			clickText("Negocio");
			clickText("Mi Negocio");
		} else {
			return false;
		}

		final boolean expanded = isTextVisible("Agregar Negocio", MEDIUM_WAIT)
				&& isTextVisible("Administrar Negocios", MEDIUM_WAIT);
		if (expanded) {
			takeScreenshot("02_menu_mi_negocio_expandido");
		}
		return sidebarVisible && negocioVisible && expanded;
	}

	private boolean stepValidateAgregarNegocioModal() {
		if (!isTextVisible("Agregar Negocio", SHORT_WAIT)) {
			ensureMiNegocioExpanded();
		}

		clickText("Agregar Negocio");

		final boolean titleVisible = isAnyTextVisible(Arrays.asList("Crear Nuevo Negocio"), MEDIUM_WAIT);
		final boolean inputVisible = isBusinessNameInputVisible();
		final boolean quotaVisible = isAnyTextVisible(Arrays.asList("Tienes 2 de 3 negocios"), MEDIUM_WAIT);
		final boolean buttonsVisible = isAnyTextVisible(Arrays.asList("Cancelar"), MEDIUM_WAIT)
				&& isAnyTextVisible(Arrays.asList("Crear Negocio"), MEDIUM_WAIT);

		if (titleVisible) {
			takeScreenshot("03_modal_agregar_negocio");
		}

		if (inputVisible) {
			typeIntoBusinessName("Negocio Prueba Automatización");
		}

		clickIfTextVisible("Cancelar");
		waitForUiLoad();

		return titleVisible && inputVisible && quotaVisible && buttonsVisible;
	}

	private boolean stepOpenAdministrarNegocios() {
		ensureMiNegocioExpanded();
		clickText("Administrar Negocios");

		final boolean infoGeneral = isAnyTextVisible(Arrays.asList("Información General"), LONG_WAIT);
		final boolean detallesCuenta = isAnyTextVisible(Arrays.asList("Detalles de la Cuenta"), LONG_WAIT);
		final boolean tusNegocios = isAnyTextVisible(Arrays.asList("Tus Negocios"), LONG_WAIT);
		final boolean seccionLegal = isAnyTextVisible(Arrays.asList("Sección Legal", "Seccion Legal"), LONG_WAIT);

		if (infoGeneral || detallesCuenta || tusNegocios || seccionLegal) {
			takeScreenshot("04_administrar_negocios");
		}

		return infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
	}

	private boolean stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		if (section == null) {
			return false;
		}

		final boolean userEmailVisible = hasDescendant(section, ".//*[contains(normalize-space(.), '@')]");
		final boolean businessPlanVisible = hasDescendant(section, ".//*[contains(normalize-space(.), 'BUSINESS PLAN')]");
		final boolean cambiarPlanVisible = hasDescendant(section, ".//*[normalize-space(.)='Cambiar Plan' or contains(normalize-space(.), 'Cambiar Plan')]");
		final boolean userNameVisible = hasDescendant(section,
				".//*[normalize-space(.)!='' and not(contains(normalize-space(.), '@')) and not(contains(normalize-space(.), 'BUSINESS PLAN')) and not(contains(normalize-space(.), 'Cambiar Plan'))]");

		return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean stepValidateDetallesCuenta() {
		return isAnyTextVisible(Arrays.asList("Cuenta creada"), MEDIUM_WAIT)
				&& isAnyTextVisible(Arrays.asList("Estado activo"), MEDIUM_WAIT)
				&& isAnyTextVisible(Arrays.asList("Idioma seleccionado"), MEDIUM_WAIT);
	}

	private boolean stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		if (section == null) {
			return false;
		}

		final boolean addButtonVisible = hasDescendant(section,
				".//*[normalize-space(.)='Agregar Negocio' or contains(normalize-space(.), 'Agregar Negocio')]");
		final boolean quotaVisible = hasDescendant(section, ".//*[contains(normalize-space(.), 'Tienes 2 de 3 negocios')]");
		final boolean listVisible = hasDescendant(section,
				".//table | .//ul | .//ol | .//*[contains(@class,'list')] | .//*[contains(@class,'card')] | .//*[contains(normalize-space(.), 'Negocio') and not(contains(normalize-space(.), 'Agregar Negocio')) and not(contains(normalize-space(.), 'Tus Negocios'))]");

		return listVisible && addButtonVisible && quotaVisible;
	}

	private boolean stepValidateLegalLink(final String linkText, final String evidenceKey) {
		final String originHandle = appWindowHandle != null ? appWindowHandle : driver.getWindowHandle();
		final String originUrl = driver.getCurrentUrl();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickText(linkText);

		final String targetHandle = resolveLegalTargetWindow(originHandle, beforeHandles);
		final boolean openedInNewTab = !originHandle.equals(targetHandle);

		if (openedInNewTab) {
			driver.switchTo().window(targetHandle);
		}

		waitForUiLoad();

		final boolean headingVisible = isAnyTextVisible(Arrays.asList(linkText), LONG_WAIT);
		final boolean legalContentVisible = isLegalTextVisible();
		final String finalUrl = driver.getCurrentUrl();
		evidenceNotes.add(linkText + " URL: " + finalUrl);
		takeScreenshot("05_" + evidenceKey);

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(originHandle);
			waitForUiLoad();
		} else {
			tryReturnToApplication(originUrl);
		}

		return headingVisible && legalContentVisible;
	}

	private boolean runStep(final String fieldName, final StepAction stepAction) {
		try {
			final boolean passed = stepAction.execute();
			stepStatus.put(fieldName, passed ? "PASS" : "FAIL");
			return passed;
		} catch (final Exception ex) {
			stepStatus.put(fieldName, "FAIL (" + ex.getClass().getSimpleName() + ")");
			evidenceNotes.add(fieldName + " error: " + ex.getMessage());
			return false;
		}
	}

	private void markRemainingBlockedByLogin() {
		for (final String field : REPORT_FIELDS) {
			if ("FAIL (not executed)".equals(stepStatus.get(field))) {
				stepStatus.put(field, "FAIL (blocked by login)");
			}
		}
	}

	private boolean allStepsPassed() {
		for (final String value : stepStatus.values()) {
			if (!"PASS".equals(value)) {
				return false;
			}
		}
		return true;
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio workflow report\n");
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		builder.append('\n');
		for (final String field : REPORT_FIELDS) {
			builder.append("- ").append(field).append(": ").append(stepStatus.get(field)).append('\n');
		}
		if (!evidenceNotes.isEmpty()) {
			builder.append('\n').append("Notes\n");
			for (final String note : evidenceNotes) {
				builder.append("- ").append(note).append('\n');
			}
		}
		return builder.toString();
	}

	private void writeFinalReportFile() {
		if (evidenceDir == null) {
			return;
		}

		final Path reportFile = evidenceDir.resolve("final-report.txt");
		try {
			Files.writeString(reportFile, buildFinalReport());
		} catch (final IOException ex) {
			System.err.println("Failed to write final report file: " + ex.getMessage());
		}
	}

	private void waitForUiLoad() {
		final ExpectedCondition<Boolean> pageReady = drv -> {
			if (!(drv instanceof JavascriptExecutor)) {
				return Boolean.TRUE;
			}
			final Object readyState = ((JavascriptExecutor) drv).executeScript("return document.readyState");
			return "complete".equals(readyState) || "interactive".equals(readyState);
		};

		try {
			new WebDriverWait(driver, SHORT_WAIT).until(pageReady);
		} catch (final TimeoutException ignored) {
			// Some SPA transitions never reach expected ready state.
		}

		try {
			Thread.sleep(500);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean waitUntil(final BooleanSupplier check, final Duration timeout) {
		final long maxMillis = timeout.toMillis();
		final long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < maxMillis) {
			if (check.getAsBoolean()) {
				return true;
			}
			try {
				Thread.sleep(300);
			} catch (final InterruptedException ex) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	private boolean isMainInterfaceVisible() {
		return isSidebarVisible() && isAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio"), SHORT_WAIT);
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

	private void ensureMiNegocioExpanded() {
		if (isTextVisible("Agregar Negocio", SHORT_WAIT) && isTextVisible("Administrar Negocios", SHORT_WAIT)) {
			return;
		}
		if (isTextVisible("Mi Negocio", SHORT_WAIT)) {
			clickText("Mi Negocio");
			return;
		}
		if (isTextVisible("Negocio", SHORT_WAIT)) {
			clickText("Negocio");
		}
		if (isTextVisible("Mi Negocio", SHORT_WAIT)) {
			clickText("Mi Negocio");
		}
	}

	private boolean clickFirstMatchingText(final List<String> candidates) {
		for (final String text : candidates) {
			if (clickIfTextVisible(text)) {
				return true;
			}
		}
		return false;
	}

	private boolean clickIfTextVisible(final String text) {
		try {
			final WebElement element = findVisibleElementByText(text, SHORT_WAIT);
			if (element == null) {
				return false;
			}
			clickElement(element);
			return true;
		} catch (final RuntimeException ex) {
			return false;
		}
	}

	private void clickText(final String text) {
		final WebElement element = findVisibleElementByText(text, MEDIUM_WAIT);
		if (element == null) {
			throw new NoSuchElementException("Element with text not found: " + text);
		}
		clickElement(element);
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final RuntimeException ex) {
			if (driver instanceof JavascriptExecutor) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			} else {
				throw ex;
			}
		}
		waitForUiLoad();
	}

	private WebElement findVisibleElementByText(final String text, final Duration timeout) {
		final By by = By.xpath(visibleTextXPath(text));
		try {
			return new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
		} catch (final TimeoutException ex) {
			return null;
		}
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		return findVisibleElementByText(text, timeout) != null;
	}

	private boolean isAnyTextVisible(final List<String> texts, final Duration timeout) {
		for (final String text : texts) {
			if (isTextVisible(text, timeout)) {
				return true;
			}
		}
		return false;
	}

	private String visibleTextXPath(final String text) {
		final String escaped = escapeXPath(text);
		return "//*[self::a or self::button or @role='button' or self::span or self::p or self::h1 or self::h2 or self::h3 or self::h4 or self::div or self::li]"
				+ "[contains(normalize-space(.), " + escaped + ") or normalize-space(.)=" + escaped + "]";
	}

	private String escapeXPath(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private boolean isBusinessNameInputVisible() {
		final List<By> candidates = Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"));

		for (final By by : candidates) {
			try {
				final WebElement input = new WebDriverWait(driver, SHORT_WAIT)
						.until(ExpectedConditions.visibilityOfElementLocated(by));
				if (input != null && input.isDisplayed()) {
					return true;
				}
			} catch (final TimeoutException ignored) {
				// try next
			}
		}
		return false;
	}

	private void typeIntoBusinessName(final String value) {
		final List<By> candidates = Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"));

		for (final By by : candidates) {
			try {
				final WebElement input = new WebDriverWait(driver, SHORT_WAIT)
						.until(ExpectedConditions.visibilityOfElementLocated(by));
				input.clear();
				input.sendKeys(value);
				waitForUiLoad();
				return;
			} catch (final TimeoutException ignored) {
				// try next
			}
		}
	}

	private WebElement findSectionByHeading(final String heading) {
		final String escapedHeading = escapeXPath(heading);
		final List<By> locators = Arrays.asList(
				By.xpath("//*[self::section or self::div][.//*[normalize-space(.)=" + escapedHeading + "]]"),
				By.xpath("//*[self::section or self::div][.//*[contains(normalize-space(.), " + escapedHeading + ")]]"));

		for (final By locator : locators) {
			try {
				final List<WebElement> candidates = driver.findElements(locator);
				for (final WebElement candidate : candidates) {
					if (candidate.isDisplayed()) {
						return candidate;
					}
				}
			} catch (final RuntimeException ignored) {
				// try next
			}
		}
		return null;
	}

	private boolean hasDescendant(final WebElement root, final String xpath) {
		try {
			final List<WebElement> descendants = root.findElements(By.xpath(xpath));
			for (final WebElement descendant : descendants) {
				if (descendant.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final RuntimeException ex) {
			return false;
		}
	}

	private String resolveLegalTargetWindow(final String originHandle, final Set<String> beforeHandles) {
		final boolean switched = waitUntil(() -> driver.getWindowHandles().size() > beforeHandles.size(), MEDIUM_WAIT);
		if (!switched) {
			return originHandle;
		}

		for (final String handle : driver.getWindowHandles()) {
			if (!beforeHandles.contains(handle)) {
				return handle;
			}
		}
		return originHandle;
	}

	private boolean isLegalTextVisible() {
		final List<String> legalKeywords = Arrays.asList("uso", "datos", "privacidad", "términos", "terminos",
				"información", "informacion", "condiciones");
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p | //li | //div"));
		for (final WebElement element : paragraphs) {
			if (!element.isDisplayed()) {
				continue;
			}
			final String text = element.getText();
			if (isBlank(text)) {
				continue;
			}
			final String normalized = text.toLowerCase(Locale.ROOT);
			for (final String keyword : legalKeywords) {
				if (normalized.contains(keyword)) {
					return true;
				}
			}
		}
		return false;
	}

	private void tryReturnToApplication(final String originUrl) {
		try {
			driver.navigate().back();
			waitForUiLoad();
		} catch (final RuntimeException ex) {
			if (!isBlank(originUrl)) {
				driver.navigate().to(originUrl);
				waitForUiLoad();
			}
		}
	}

	private void takeScreenshot(final String checkpointName) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		try {
			final Path screenshotPath = evidenceDir.resolve(checkpointName + ".png");
			final java.io.File tmp = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			Files.copy(tmp.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
			evidenceNotes.add("Screenshot: " + screenshotPath.toAbsolutePath());
		} catch (final IOException ex) {
			evidenceNotes.add("Screenshot error at " + checkpointName + ": " + ex.getMessage());
		}
	}

	private WebDriver createDriver() {
		final String remoteUrl = envOrProperty("SALEADS_SELENIUM_REMOTE_URL", "saleads.selenium.remote.url");
		final boolean headless = parseBoolean(envOrProperty("SALEADS_HEADLESS", "saleads.headless"), true);
		final String requestedBrowser = envOrProperty("SALEADS_BROWSER", "saleads.browser");
		final String browser = isBlank(requestedBrowser) ? "chrome" : requestedBrowser.trim().toLowerCase(Locale.ROOT);

		if (!isBlank(remoteUrl)) {
			try {
				if ("firefox".equals(browser)) {
					final FirefoxOptions options = createFirefoxOptions(headless);
					return new RemoteWebDriver(java.net.URI.create(remoteUrl).toURL(), options);
				}
				final ChromeOptions options = createChromeOptions(headless);
				return new RemoteWebDriver(java.net.URI.create(remoteUrl).toURL(), options);
			} catch (final Exception ex) {
				throw new IllegalStateException("Unable to initialize remote WebDriver", ex);
			}
		}

		if ("firefox".equals(browser)) {
			try {
				return new FirefoxDriver(createFirefoxOptions(headless));
			} catch (final RuntimeException ex) {
				return new ChromeDriver(createChromeOptions(headless));
			}
		}

		try {
			return new ChromeDriver(createChromeOptions(headless));
		} catch (final RuntimeException ex) {
			return new FirefoxDriver(createFirefoxOptions(headless));
		}
	}

	private ChromeOptions createChromeOptions(final boolean headless) {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}
		return options;
	}

	private FirefoxOptions createFirefoxOptions(final boolean headless) {
		final FirefoxOptions options = new FirefoxOptions();
		if (headless) {
			options.addArguments("-headless");
		}
		return options;
	}

	private String envOrProperty(final String envKey, final String propertyKey) {
		final String fromProperty = System.getProperty(propertyKey);
		if (!isBlank(fromProperty)) {
			return fromProperty;
		}
		return System.getenv(envKey);
	}

	private boolean parseBoolean(final String value, final boolean defaultValue) {
		if (isBlank(value)) {
			return defaultValue;
		}
		return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()) || "yes".equalsIgnoreCase(value.trim());
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface StepAction {
		boolean execute() throws Exception;
	}

	@FunctionalInterface
	private interface BooleanSupplier {
		boolean getAsBoolean();
	}
}
