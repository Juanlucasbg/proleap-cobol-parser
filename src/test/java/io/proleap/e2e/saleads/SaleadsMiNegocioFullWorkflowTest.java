package io.proleap.e2e.saleads;

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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
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

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private final Map<String, Boolean> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "saleads.headless", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		final String chromeBinary = readConfig("SALEADS_CHROME_BINARY", "saleads.chromeBinary", "");
		if (!chromeBinary.isBlank()) {
			options.setBinary(chromeBinary);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		screenshotDir = Paths.get("target", "saleads-e2e-screenshots", "saleads_mi_negocio_full_test_" + timestamp);
		Files.createDirectories(screenshotDir);

		for (final String reportField : REPORT_FIELDS) {
			stepStatus.put(reportField, false);
			stepDetails.put(reportField, "NOT_RUN");
		}

		final String loginUrl = readConfig("SALEADS_LOGIN_URL", "saleads.loginUrl", "");
		if (!loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiToLoad();
		} else {
			final String currentUrl = safeCurrentUrl();
			Assume.assumeTrue(
					"Set SALEADS_LOGIN_URL (or -Dsaleads.loginUrl) unless the driver session starts on the SaleADS login page.",
					currentUrl != null && !currentUrl.isBlank() && !"about:blank".equals(currentUrl)
							&& !"data:,".equals(currentUrl));
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final String finalReport = buildFinalReport();
		System.out.println(finalReport);
		Assert.assertTrue("One or more validations failed.\n" + finalReport, allStepsPassed());
	}

	private void stepLoginWithGoogle() {
		clickByVisibleTextCandidates(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Iniciar sesion con Google",
				"Continuar con Google",
				"Login with Google");

		// Account picker is optional if the browser session is already authenticated.
		clickIfVisibleTextCandidates(Duration.ofSeconds(8), "juanlucasbarbiergarzon@gmail.com");
		waitForUiToLoad();

		Assert.assertTrue(
				"Main application interface was not detected after login.",
				isAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Panel", "Administrar Negocios"));
		assertSidebarVisible();
		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() {
		assertSidebarVisible();
		clickByVisibleTextCandidates("Negocio", "Business");
		clickByVisibleTextCandidates("Mi Negocio", "My Business");
		Assert.assertTrue("Submenu option 'Agregar Negocio' is not visible.", isAnyVisibleText("Agregar Negocio"));
		Assert.assertTrue("Submenu option 'Administrar Negocios' is not visible.",
				isAnyVisibleText("Administrar Negocios"));
		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleTextCandidates("Agregar Negocio");
		assertVisibleByTextCandidates("Crear Nuevo Negocio");
		final WebElement nameInput = waitForVisible(By.xpath(
				"//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder,'Nombre del Negocio')]"),
				Duration.ofSeconds(15));
		Assert.assertNotNull("Input 'Nombre del Negocio' was not found.", nameInput);
		assertVisibleByTextCandidates("Tienes 2 de 3 negocios");
		assertVisibleByTextCandidates("Cancelar");
		assertVisibleByTextCandidates("Crear Negocio");
		captureScreenshot("03_crear_nuevo_negocio_modal");

		nameInput.click();
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatizacion");
		clickByVisibleTextCandidates("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() {
		if (!isAnyVisibleText("Administrar Negocios")) {
			clickByVisibleTextCandidates("Mi Negocio", "My Business");
		}
		clickByVisibleTextCandidates("Administrar Negocios");
		assertVisibleByTextCandidates("Información General", "Informacion General");
		assertVisibleByTextCandidates("Detalles de la Cuenta", "Detalles de la cuenta");
		assertVisibleByTextCandidates("Tus Negocios");
		assertVisibleByTextCandidates("Sección Legal", "Seccion Legal");
		captureScreenshot("04_administrar_negocios_account_page");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General", "Informacion General");
		Assert.assertNotNull("Section 'Información General' was not found.", section);

		final List<String> sectionTexts = collectVisibleTexts(section);
		final boolean hasEmail = sectionTexts.stream().anyMatch(text -> EMAIL_PATTERN.matcher(text).find());
		Assert.assertTrue("User email is not visible in 'Información General'.", hasEmail);

		final boolean hasLikelyUserName = sectionTexts.stream().anyMatch(this::isLikelyUserName);
		Assert.assertTrue("A likely user name was not visible in 'Información General'.", hasLikelyUserName);

		Assert.assertTrue("'BUSINESS PLAN' text is not visible.", isTextVisibleInSection(section, "BUSINESS PLAN"));
		Assert.assertTrue("'Cambiar Plan' button is not visible.", isAnyVisibleText("Cambiar Plan"));
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta", "Detalles de la cuenta");
		Assert.assertNotNull("Section 'Detalles de la Cuenta' was not found.", section);
		Assert.assertTrue("'Cuenta creada' is not visible.", isTextVisibleInSection(section, "Cuenta creada"));
		Assert.assertTrue("'Estado activo' is not visible.", isTextVisibleInSection(section, "Estado activo"));
		Assert.assertTrue("'Idioma seleccionado' is not visible.",
				isTextVisibleInSection(section, "Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		Assert.assertNotNull("Section 'Tus Negocios' was not found.", section);
		Assert.assertTrue("Button 'Agregar Negocio' is not visible in 'Tus Negocios'.",
				isTextVisibleInSection(section, "Agregar Negocio") || isAnyVisibleText("Agregar Negocio"));
		Assert.assertTrue("'Tienes 2 de 3 negocios' is not visible in 'Tus Negocios'.",
				isTextVisibleInSection(section, "Tienes 2 de 3 negocios"));
		Assert.assertTrue("Business list is not visible in 'Tus Negocios'.", isBusinessListVisible(section));
	}

	private void stepValidateTerminosYCondiciones() {
		openAndValidateLegalDocument(
				"Términos y Condiciones",
				new String[]{"Términos y Condiciones", "Terminos y Condiciones"},
				"05_terminos_y_condiciones");
	}

	private void stepValidatePoliticaPrivacidad() {
		openAndValidateLegalDocument(
				"Política de Privacidad",
				new String[]{"Política de Privacidad", "Politica de Privacidad"},
				"06_politica_de_privacidad");
	}

	private void openAndValidateLegalDocument(
			final String reportField,
			final String[] linkAndHeadingCandidates,
			final String screenshotName) {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String previousUrl = safeCurrentUrl();

		clickByVisibleTextCandidates(linkAndHeadingCandidates);

		final String newWindow = waitForNewWindow(handlesBefore, Duration.ofSeconds(10));
		final boolean openedNewTab = newWindow != null;

		if (openedNewTab) {
			driver.switchTo().window(newWindow);
			waitForUiToLoad();
		} else {
			wait.until(d -> !Objects.equals(previousUrl, safeCurrentUrl()) || isAnyVisibleText(linkAndHeadingCandidates));
			waitForUiToLoad();
		}

		assertVisibleByTextCandidates(linkAndHeadingCandidates);
		assertLegalContentVisible();
		captureScreenshot(screenshotName);
		stepDetails.put(reportField, "PASS - URL: " + safeCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void runStep(final String stepName, final StepRunnable stepRunnable) {
		try {
			stepRunnable.run();
			stepStatus.put(stepName, true);
			final String existingDetail = stepDetails.get(stepName);
			if (existingDetail == null || "NOT_RUN".equals(existingDetail)) {
				stepDetails.put(stepName, "PASS");
			}
		} catch (final Throwable throwable) {
			stepStatus.put(stepName, false);
			final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getMessage();
			stepDetails.put(stepName, message);
			captureScreenshot("FAILED_" + sanitizeName(stepName));
		}
	}

	private void clickByVisibleTextCandidates(final String... candidates) {
		Throwable lastError = null;
		for (final String candidate : candidates) {
			final List<WebElement> elements = findDisplayedElementsByText(candidate);
			for (final WebElement element : elements) {
				final WebElement clickable = findClickableAncestor(element);
				try {
					wait.until(ExpectedConditions.elementToBeClickable(clickable));
					clickable.click();
					waitForUiToLoad();
					return;
				} catch (final Throwable clickError) {
					lastError = clickError;
					try {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
						waitForUiToLoad();
						return;
					} catch (final Throwable jsError) {
						lastError = jsError;
					}
				}
			}
		}
		throw new AssertionError("Could not click any element with visible text candidates: " + Arrays.toString(candidates),
				lastError);
	}

	private boolean clickIfVisibleTextCandidates(final Duration timeout, final String... candidates) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		for (final String candidate : candidates) {
			try {
				final WebElement element = shortWait.until(d -> {
					final List<WebElement> options = findDisplayedElementsByText(candidate);
					return options.isEmpty() ? null : options.get(0);
				});
				if (element != null) {
					findClickableAncestor(element).click();
					waitForUiToLoad();
					return true;
				}
			} catch (final TimeoutException ignored) {
				// Optional step; ignore timeout and keep trying other candidates.
			}
		}
		return false;
	}

	private void assertVisibleByTextCandidates(final String... candidates) {
		Assert.assertTrue("None of the expected visible texts were found: " + Arrays.toString(candidates),
				isAnyVisibleText(candidates));
	}

	private boolean isAnyVisibleText(final String... candidates) {
		for (final String candidate : candidates) {
			if (!findDisplayedElementsByText(candidate).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private List<WebElement> findDisplayedElementsByText(final String text) {
		final String literal = toXPathLiteral(text);
		final List<WebElement> results = new ArrayList<>();
		final List<By> byOptions = Arrays.asList(
				By.xpath("//*[normalize-space()=" + literal + "]"),
				By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
		for (final By by : byOptions) {
			for (final WebElement element : driver.findElements(by)) {
				try {
					if (element.isDisplayed() && !results.contains(element)) {
						results.add(element);
					}
				} catch (final Throwable ignored) {
					// Ignore stale and detached elements while scanning candidates.
				}
			}
		}
		return results;
	}

	private WebElement findClickableAncestor(final WebElement element) {
		try {
			final WebElement clickable = element.findElement(By.xpath(
					"./ancestor-or-self::*[self::button or self::a or @role='button' or @tabindex='0'][1]"));
			return clickable == null ? element : clickable;
		} catch (final Throwable ignored) {
			return element;
		}
	}

	private WebElement waitForVisible(final By by, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(
				String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"))));
		try {
			Thread.sleep(300L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void assertSidebarVisible() {
		final List<WebElement> sidebarElements = new ArrayList<>();
		sidebarElements.addAll(driver.findElements(By.cssSelector("aside")));
		sidebarElements.addAll(driver.findElements(By.cssSelector("nav")));
		sidebarElements.addAll(driver.findElements(By.cssSelector("[class*='sidebar']")));
		final boolean sidebarVisible = sidebarElements.stream().anyMatch(element -> {
			try {
				return element.isDisplayed();
			} catch (final Throwable ignored) {
				return false;
			}
		});
		Assert.assertTrue("Left sidebar navigation is not visible.", sidebarVisible);
	}

	private WebElement findSectionByHeading(final String... headingCandidates) {
		for (final String headingCandidate : headingCandidates) {
			for (final WebElement headingElement : findDisplayedElementsByText(headingCandidate)) {
				try {
					final WebElement section = headingElement.findElement(
							By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
					if (section != null && section.isDisplayed()) {
						return section;
					}
				} catch (final Throwable ignored) {
					// Continue searching the next heading candidate.
				}
			}
		}
		return null;
	}

	private List<String> collectVisibleTexts(final WebElement root) {
		final List<String> values = new ArrayList<>();
		final List<WebElement> textElements = root.findElements(By.xpath(".//*[normalize-space()]"));
		for (final WebElement element : textElements) {
			try {
				if (!element.isDisplayed()) {
					continue;
				}
				final String text = element.getText();
				if (text != null) {
					final String normalized = text.trim().replaceAll("\\s+", " ");
					if (!normalized.isEmpty() && !values.contains(normalized)) {
						values.add(normalized);
					}
				}
			} catch (final Throwable ignored) {
				// Continue scanning when elements become stale.
			}
		}
		return values;
	}

	private boolean isLikelyUserName(final String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		final String lower = text.toLowerCase(Locale.ROOT);
		if (lower.contains("@") || lower.contains("business plan") || lower.contains("cambiar plan")
				|| lower.contains("información general") || lower.contains("informacion general")) {
			return false;
		}
		return text.length() >= 3 && text.length() <= 80 && text.chars().anyMatch(Character::isLetter);
	}

	private boolean isTextVisibleInSection(final WebElement section, final String text) {
		final String target = text.toLowerCase(Locale.ROOT);
		for (final String value : collectVisibleTexts(section)) {
			if (value.toLowerCase(Locale.ROOT).contains(target)) {
				return true;
			}
		}
		return false;
	}

	private boolean isBusinessListVisible(final WebElement section) {
		final List<WebElement> listCandidates = new ArrayList<>();
		listCandidates.addAll(section.findElements(By.cssSelector("ul li")));
		listCandidates.addAll(section.findElements(By.cssSelector("table tbody tr")));
		listCandidates.addAll(section.findElements(By.cssSelector("[class*='business']")));
		listCandidates.addAll(section.findElements(By.cssSelector("[class*='negocio']")));
		for (final WebElement candidate : listCandidates) {
			try {
				if (candidate.isDisplayed() && !candidate.getText().trim().isEmpty()) {
					return true;
				}
			} catch (final Throwable ignored) {
				// Ignore stale elements while probing candidate business list entries.
			}
		}
		return false;
	}

	private void assertLegalContentVisible() {
		final List<WebElement> contentBlocks = driver.findElements(
				By.xpath("//article//*[self::p or self::li][normalize-space()] | //main//*[self::p or self::li][normalize-space()]"));
		final boolean hasVisibleBlock = contentBlocks.stream().anyMatch(element -> {
			try {
				return element.isDisplayed() && element.getText() != null && element.getText().trim().length() > 20;
			} catch (final Throwable ignored) {
				return false;
			}
		});
		if (hasVisibleBlock) {
			return;
		}

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Legal content text is not visible.", bodyText != null && bodyText.trim().length() > 120);
	}

	private String waitForNewWindow(final Set<String> handlesBefore, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(d -> {
				final Set<String> currentHandles = d.getWindowHandles();
				if (currentHandles.size() <= handlesBefore.size()) {
					return null;
				}
				for (final String handle : currentHandles) {
					if (!handlesBefore.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private void captureScreenshot(final String name) {
		if (driver == null) {
			return;
		}
		try {
			final Path targetPath = screenshotDir.resolve(sanitizeName(name) + ".png");
			final Path screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(screenshot, targetPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (final Throwable ignored) {
			// Screenshot capture is best-effort and must not break test execution.
		}
	}

	private String buildFinalReport() {
		final StringBuilder report = new StringBuilder();
		report.append("\n=== SaleADS Mi Negocio Workflow Final Report ===\n");
		for (final String field : REPORT_FIELDS) {
			final boolean passed = Boolean.TRUE.equals(stepStatus.get(field));
			final String detail = stepDetails.getOrDefault(field, "NO_DETAIL");
			report.append("- ").append(field).append(": ").append(passed ? "PASS" : "FAIL");
			if (!"PASS".equals(detail)) {
				report.append(" | ").append(detail);
			}
			report.append('\n');
		}
		report.append("Screenshots directory: ").append(screenshotDir.toAbsolutePath()).append('\n');
		return report.toString();
	}

	private boolean allStepsPassed() {
		return REPORT_FIELDS.stream().allMatch(field -> Boolean.TRUE.equals(stepStatus.get(field)));
	}

	private String readConfig(final String envKey, final String propertyKey, final String fallbackValue) {
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		return fallbackValue;
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (final Throwable ignored) {
			return "";
		}
	}

	private String sanitizeName(final String rawName) {
		return rawName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+", "").replaceAll("_+$", "");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part;
			if (chars[i] == '\'') {
				part = "\"'\"";
			} else if (chars[i] == '"') {
				part = "'\"'";
			} else {
				final int start = i;
				while (i < chars.length && chars[i] != '\'' && chars[i] != '"') {
					i++;
				}
				part = "'" + value.substring(start, i) + "'";
				i--;
			}
			if (result.length() > "concat(".length()) {
				result.append(", ");
			}
			result.append(part);
		}
		result.append(')');
		return result.toString();
	}

	@FunctionalInterface
	private interface StepRunnable {
		void run();
	}
}
