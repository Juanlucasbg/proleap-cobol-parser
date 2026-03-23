package io.proleap.e2e.saleads;

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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Información General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS_CONDICIONES = "Términos y Condiciones";
	private static final String POLITICA_PRIVACIDAD = "Política de Privacidad";

	private static final List<String> REPORT_FIELDS = Arrays.asList(LOGIN, MI_NEGOCIO_MENU, AGREGAR_NEGOCIO_MODAL,
			ADMINISTRAR_NEGOCIOS_VIEW, INFORMACION_GENERAL, DETALLES_CUENTA, TUS_NEGOCIOS, TERMINOS_CONDICIONES,
			POLITICA_PRIVACIDAD);

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final DateTimeFormatter EVIDENCE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss",
			Locale.ROOT);

	private final Map<String, StepOutcome> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String termsAndConditionsUrl = "N/A";
	private String privacyPolicyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		if (Boolean.parseBoolean(getEnvOrDefault("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		final String userDataDir = System.getenv("SALEADS_CHROME_USER_DATA_DIR");
		if (userDataDir != null && !userDataDir.isBlank()) {
			options.addArguments("--user-data-dir=" + userDataDir);
		}

		final String profile = System.getenv("SALEADS_CHROME_PROFILE");
		if (profile != null && !profile.isBlank()) {
			options.addArguments("--profile-directory=" + profile);
		}

		driver = new ChromeDriver(options);
		driver.manage().window().setSize(new Dimension(1920, 3000));
		wait = new WebDriverWait(driver, Duration.ofSeconds(parseIntEnv("SALEADS_WAIT_SECONDS", 30)));
		wait.pollingEvery(Duration.ofMillis(300));

		evidenceDir = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(EVIDENCE_TS_FORMAT));
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		for (final String field : REPORT_FIELDS) {
			report.put(field, StepOutcome.pending());
		}

		executeStep(LOGIN, this::stepLoginWithGoogle);
		executeStep(MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		executeStep(AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		executeStep(ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		executeStep(INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		executeStep(DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		executeStep(TUS_NEGOCIOS, this::stepValidateTusNegocios);
		executeStep(TERMINOS_CONDICIONES,
				() -> stepValidateLegalDocument(new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
						"terms-and-conditions", true));
		executeStep(POLITICA_PRIVACIDAD,
				() -> stepValidateLegalDocument(new String[] { "Política de Privacidad", "Politica de Privacidad" },
						"privacy-policy", false));

		final String summary = buildFinalSummary();
		System.out.println(summary);

		final boolean hasFailures = report.values().stream().anyMatch(result -> !result.passed);
		if (hasFailures) {
			fail(summary);
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiToSettle();
		} else if ("about:blank".equalsIgnoreCase(driver.getCurrentUrl())) {
			throw new IllegalStateException(
					"Browser started on about:blank. Set SALEADS_LOGIN_URL or provide a pre-opened login page session.");
		}

		final List<String> googleButtons = Arrays.asList("Sign in with Google", "Iniciar sesión con Google",
				"Inicia sesión con Google", "Continuar con Google", "Acceder con Google", "Login with Google");
		final List<String> genericLoginButtons = Arrays.asList("Iniciar sesión", "Ingresar", "Login", "Sign in");
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBeforeGoogleClick = new LinkedHashSet<>(driver.getWindowHandles());

		if (!clickFirstClickableText(googleButtons, Duration.ofSeconds(8))) {
			clickFirstClickableTextOrFail(genericLoginButtons, Duration.ofSeconds(8));
			clickFirstClickableTextOrFail(googleButtons, Duration.ofSeconds(8));
		}

		if (driver.getWindowHandles().size() > windowsBeforeGoogleClick.size()) {
			driver.switchTo().window(findNewWindowHandle(windowsBeforeGoogleClick));
		}

		clickIfVisibleText("juanlucasbarbiergarzon@gmail.com", Duration.ofSeconds(8));

		if (hasVisibleText("juanlucasbarbiergarzon@gmail.com") && driver.getWindowHandles().size() > 1
				&& !driver.getWindowHandle().equals(appWindow)) {
			waitUntilGoogleWindowClosesOrRedirects(appWindow);
		}

		if (driver.getWindowHandles().contains(appWindow)) {
			driver.switchTo().window(appWindow);
		}

		waitForAnyVisibleText(Duration.ofSeconds(90), "Negocio", "Mi Negocio");
		assertTrue("Left sidebar navigation should be visible after login", hasVisibleElement(By.xpath(
				"//aside | //nav[contains(@class,'sidebar') or .//*[contains(normalize-space(.), 'Negocio')] ]")));
		takeScreenshot("dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickIfVisibleText("Negocio", Duration.ofSeconds(6));
		clickFirstClickableTextOrFail(List.of("Mi Negocio"), Duration.ofSeconds(10));

		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Agregar Negocio");
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Administrar Negocios");
		takeScreenshot("mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickFirstClickableTextOrFail(List.of("Agregar Negocio"), Duration.ofSeconds(10));

		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Crear Nuevo Negocio");
		assertTrue("Input field 'Nombre del Negocio' should exist", hasVisibleElement(By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio')] | //label[normalize-space(.)='Nombre del Negocio']/following::input[1]")));
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Tienes 2 de 3 negocios");
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Cancelar");
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Crear Negocio");

		takeScreenshot("agregar-negocio-modal");

		clickIfVisible(By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio')] | //label[normalize-space(.)='Nombre del Negocio']/following::input[1]"),
				Duration.ofSeconds(5));
		typeIfVisible(By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio')] | //label[normalize-space(.)='Nombre del Negocio']/following::input[1]"),
				"Negocio Prueba Automatización");

		clickFirstClickableTextOrFail(List.of("Cancelar"), Duration.ofSeconds(10));
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]")));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!hasVisibleText("Administrar Negocios")) {
			clickFirstClickableTextOrFail(List.of("Mi Negocio"), Duration.ofSeconds(10));
		}

		clickFirstClickableTextOrFail(List.of("Administrar Negocios"), Duration.ofSeconds(10));

		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Información General");
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Detalles de la Cuenta");
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Tus Negocios");
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Sección Legal", "Seccion Legal");
		takeScreenshot("administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() {
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Información General");
		final WebElement infoSection = findSectionByHeading("Información General");

		assertTrue("User email should be visible in 'Información General'",
				hasVisibleElementInside(infoSection, By.xpath(".//*[contains(normalize-space(.), '@')]")));
		assertTrue("User name should be visible in 'Información General'", hasLikelyUserName(infoSection));
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "BUSINESS PLAN");
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Detalles de la Cuenta");
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Cuenta creada");
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Estado activo");
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Tus Negocios");
		final WebElement businessesSection = findSectionByHeading("Tus Negocios");

		assertTrue("Business list should be visible in 'Tus Negocios'", hasVisibleElementInside(businessesSection,
				By.xpath(".//table//tr | .//ul/li | .//*[contains(@class, 'business')] | .//div[contains(@class, 'card')]")));
		assertTrue("Button 'Agregar Negocio' should exist in 'Tus Negocios'",
				hasVisibleElementInside(businessesSection, By.xpath(
						".//button[normalize-space(.)='Agregar Negocio'] | .//a[normalize-space(.)='Agregar Negocio'] | .//*[@role='button' and normalize-space(.)='Agregar Negocio']")));
		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalDocument(final String[] titleCandidates, final String screenshotPrefix,
			final boolean termsDocument) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String previousUrl = driver.getCurrentUrl();

		clickFirstClickableTextOrFail(Arrays.asList(titleCandidates), Duration.ofSeconds(10));

		waitUntilNewWindowOrUrlChange(beforeHandles, previousUrl);
		final boolean newTabOpened = driver.getWindowHandles().size() > beforeHandles.size();

		if (newTabOpened) {
			driver.switchTo().window(findNewWindowHandle(beforeHandles));
		}

		waitForAnyVisibleText(Duration.ofSeconds(45), titleCandidates);
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal content should be visible", bodyText != null && bodyText.trim().length() > 200);

		takeScreenshot(screenshotPrefix);
		final String legalUrl = driver.getCurrentUrl();
		if (termsDocument) {
			termsAndConditionsUrl = legalUrl;
		} else {
			privacyPolicyUrl = legalUrl;
		}

		if (newTabOpened) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}

		waitForAnyVisibleText(DEFAULT_TIMEOUT, "Sección Legal", "Seccion Legal");
	}

	private void executeStep(final String stepName, final CheckedAction action) {
		try {
			action.run();
			report.put(stepName, StepOutcome.passed());
		} catch (final Throwable throwable) {
			final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getMessage();
			report.put(stepName, StepOutcome.failed(message));
			try {
				takeScreenshot("failed-" + toSafeFilename(stepName));
			} catch (final Exception screenshotError) {
				System.err.println("Could not capture failure screenshot for step '" + stepName + "': "
						+ screenshotError.getMessage());
			}
		}
	}

	private WebElement findSectionByHeading(final String heading) {
		waitForAnyVisibleText(DEFAULT_TIMEOUT, heading);
		final String headingLiteral = xpathLiteral(heading);
		final List<By> sectionLocators = List.of(
				By.xpath("//*[self::section or self::article or self::div][.//*[self::h1 or self::h2 or self::h3 or self::h4][normalize-space(.)="
						+ headingLiteral + "]]"),
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][normalize-space(.)=" + headingLiteral
						+ "]/ancestor::*[self::section or self::article or self::div][1]"));

		for (final By locator : sectionLocators) {
			final Optional<WebElement> element = findVisible(locator, Duration.ofSeconds(2));
			if (element.isPresent()) {
				return element.get();
			}
		}

		return driver.findElement(By.tagName("body"));
	}

	private boolean hasLikelyUserName(final WebElement section) {
		final List<WebElement> candidates = section
				.findElements(By.xpath(".//*[self::h1 or self::h2 or self::h3 or self::h4 or self::p or self::span]"));
		for (final WebElement candidate : candidates) {
			final String text = normalize(candidate.getText());
			if (text.isEmpty()) {
				continue;
			}
			if (text.contains("@")) {
				continue;
			}
			if ("información general".equals(text) || "business plan".equals(text) || "cambiar plan".equals(text)
					|| "informacion general".equals(text)) {
				continue;
			}
			if (text.length() >= 4) {
				return true;
			}
		}
		return false;
	}

	private void waitForAnyVisibleText(final Duration timeout, final String... texts) {
		new WebDriverWait(driver, timeout).until(d -> {
			for (final String text : texts) {
				if (hasVisibleText(text)) {
					return Boolean.TRUE;
				}
			}
			return Boolean.FALSE;
		});
	}

	private boolean clickFirstClickableText(final List<String> texts, final Duration timeout) {
		for (final String text : texts) {
			final Optional<WebElement> maybeElement = findVisible(clickableTextLocator(text), timeout);
			if (maybeElement.isPresent()) {
				maybeElement.get().click();
				waitForUiToSettle();
				return true;
			}
		}
		return false;
	}

	private void clickFirstClickableTextOrFail(final List<String> texts, final Duration timeout) {
		assertTrue("Could not click expected text: " + texts, clickFirstClickableText(texts, timeout));
	}

	private void clickIfVisibleText(final String text, final Duration timeout) {
		clickFirstClickableText(List.of(text), timeout);
	}

	private void clickIfVisible(final By locator, final Duration timeout) {
		findVisible(locator, timeout).ifPresent(element -> {
			element.click();
			waitForUiToSettle();
		});
	}

	private void typeIfVisible(final By locator, final String value) {
		findVisible(locator, Duration.ofSeconds(4)).ifPresent(element -> {
			element.clear();
			element.sendKeys(value);
		});
	}

	private boolean hasVisibleText(final String text) {
		return hasVisibleElement(visibleTextLocator(text));
	}

	private boolean hasVisibleElement(final By locator) {
		for (final WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean hasVisibleElementInside(final WebElement container, final By locator) {
		final List<WebElement> elements = container.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private Optional<WebElement> findVisible(final By locator, final Duration timeout) {
		try {
			final WebElement element = new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return Optional.ofNullable(element);
		} catch (final TimeoutException timeoutException) {
			return Optional.empty();
		}
	}

	private void waitUntilNewWindowOrUrlChange(final Set<String> previousHandles, final String previousUrl) {
		final ExpectedCondition<Boolean> condition = d -> {
			final boolean newWindow = d.getWindowHandles().size() > previousHandles.size();
			final boolean urlChanged = !Objects.equals(previousUrl, d.getCurrentUrl());
			return newWindow || urlChanged;
		};
		new WebDriverWait(driver, Duration.ofSeconds(30)).until(condition);
		waitForUiToSettle();
	}

	private void waitUntilGoogleWindowClosesOrRedirects(final String appWindow) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(40)).until(d -> {
				try {
					final Set<String> handles = d.getWindowHandles();
					if (handles.size() == 1 && handles.contains(appWindow)) {
						return true;
					}
					final String currentHandle = d.getWindowHandle();
					if (!handles.contains(currentHandle)) {
						return true;
					}
					final String url = d.getCurrentUrl();
					return url != null && !url.contains("accounts.google.");
				} catch (final Exception ignored) {
					return true;
				}
			});
		} catch (final TimeoutException timeoutException) {
			// Continue with best-effort window recovery.
		}
	}

	private String findNewWindowHandle(final Set<String> previousHandles) {
		for (final String handle : driver.getWindowHandles()) {
			if (!previousHandles.contains(handle)) {
				return handle;
			}
		}
		throw new IllegalStateException("New browser tab/window was expected but not found.");
	}

	private void waitForUiToSettle() {
		try {
			wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Some pages may block script execution while redirecting. Continue with a short fallback pause.
		}
		pause(Duration.ofMillis(400));
	}

	private void takeScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDir.resolve(toSafeFilename(name) + ".png");
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private String buildFinalSummary() {
		final StringBuilder builder = new StringBuilder();
		builder.append(System.lineSeparator()).append("SaleADS Mi Negocio workflow report").append(System.lineSeparator());
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		for (final String field : REPORT_FIELDS) {
			final StepOutcome stepOutcome = report.getOrDefault(field, StepOutcome.failed("Not executed"));
			builder.append("- ").append(field).append(": ").append(stepOutcome.passed ? "PASS" : "FAIL");
			if (!stepOutcome.passed && stepOutcome.message != null && !stepOutcome.message.isBlank()) {
				builder.append(" (").append(stepOutcome.message).append(")");
			}
			builder.append(System.lineSeparator());
		}
		builder.append("- Términos y Condiciones URL: ").append(termsAndConditionsUrl).append(System.lineSeparator());
		builder.append("- Política de Privacidad URL: ").append(privacyPolicyUrl).append(System.lineSeparator());
		return builder.toString();
	}

	private By clickableTextLocator(final String text) {
		final String textLiteral = xpathLiteral(text);
		return By.xpath("//button[normalize-space(.)=" + textLiteral + "] | //a[normalize-space(.)=" + textLiteral
				+ "] | //*[@role='button' and normalize-space(.)=" + textLiteral
				+ "] | //button[contains(normalize-space(.), " + textLiteral + ")] | //a[contains(normalize-space(.), "
				+ textLiteral + ")] | //*[@role='button' and contains(normalize-space(.), " + textLiteral
				+ ")] | //*[normalize-space(text())=" + textLiteral
				+ "]/ancestor::*[self::button or self::a or @role='button'][1]");
	}

	private By visibleTextLocator(final String text) {
		final String textLiteral = xpathLiteral(text);
		return By.xpath("//*[normalize-space(.)=" + textLiteral + " or contains(normalize-space(.), " + textLiteral + ")]");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'", -1);
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

	private String toSafeFilename(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String normalize(final String text) {
		return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
	}

	private String getEnvOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private int parseIntEnv(final String key, final int defaultValue) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}

		try {
			return Integer.parseInt(value);
		} catch (final NumberFormatException numberFormatException) {
			return defaultValue;
		}
	}

	private void pause(final Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface CheckedAction {
		void run() throws Exception;
	}

	private static final class StepOutcome {
		private final boolean passed;
		private final String message;

		private StepOutcome(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message;
		}

		private static StepOutcome passed() {
			return new StepOutcome(true, "");
		}

		private static StepOutcome failed(final String message) {
			return new StepOutcome(false, message);
		}

		private static StepOutcome pending() {
			return new StepOutcome(false, "Pending");
		}
	}
}
