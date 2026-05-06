package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertNotNull;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow for "Mi Negocio" in SaleADS.ai.
 *
 * <p>This test is environment-agnostic and does not hardcode any SaleADS URL. It expects one of:
 *
 * <ul>
 *   <li>SALEADS_LOGIN_URL (preferred), or
 *   <li>SALEADS_BASE_URL (the test will append "/login")
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private String loginUrl;
	private String googleAccountEmail;
	private Path evidenceDir;
	private Path reportPath;

	private final Map<String, Boolean> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final Map<String, String> screenshots = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		loginUrl = resolveLoginUrl();
		Assume.assumeTrue("Set SALEADS_LOGIN_URL or SALEADS_BASE_URL to run SaleADS E2E.", loginUrl != null);

		googleAccountEmail = envOrDefault("SALEADS_GOOGLE_ACCOUNT", "juanlucasbarbiergarzon@gmail.com");

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);
		reportPath = evidenceDir.resolve("final-report.txt");

		final boolean headless = Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true"));
		final int waitSeconds = Integer.parseInt(envOrDefault("SALEADS_WAIT_SECONDS", "30"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		driver.manage().window().setSize(new Dimension(1920, 1080));
		wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final boolean loginOk = runStep("Login", this::stepLoginWithGoogle);
		final boolean menuOk = runStep("Mi Negocio menu", () -> requirePrecondition(loginOk, "Login") && stepOpenMiNegocioMenu());
		final boolean modalOk = runStep("Agregar Negocio modal",
				() -> requirePrecondition(menuOk, "Mi Negocio menu") && stepValidateAgregarNegocioModal());
		final boolean adminViewOk = runStep("Administrar Negocios view",
				() -> requirePrecondition(menuOk, "Mi Negocio menu") && stepOpenAdministrarNegocios());
		final boolean infoOk = runStep("Información General",
				() -> requirePrecondition(adminViewOk, "Administrar Negocios view") && stepValidateInformacionGeneral());
		final boolean detailsOk = runStep("Detalles de la Cuenta",
				() -> requirePrecondition(adminViewOk, "Administrar Negocios view") && stepValidateDetallesCuenta());
		final boolean businessesOk = runStep("Tus Negocios",
				() -> requirePrecondition(adminViewOk, "Administrar Negocios view") && stepValidateTusNegocios());
		runStep("Términos y Condiciones",
				() -> requirePrecondition(adminViewOk, "Administrar Negocios view")
						&& stepValidateLegalDocument("Términos y Condiciones", "Términos y Condiciones", "terminos.png"));
		runStep("Política de Privacidad",
				() -> requirePrecondition(adminViewOk, "Administrar Negocios view")
						&& stepValidateLegalDocument("Política de Privacidad", "Política de Privacidad", "privacidad.png"));
	}

	private boolean stepLoginWithGoogle() throws IOException {
		driver.get(loginUrl);
		waitForUiLoad();

		clickAnyVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Google"));

		// If Google account chooser appears, pick the requested account.
		if (isVisibleText(googleAccountEmail, Duration.ofSeconds(8))) {
			clickByVisibleText(googleAccountEmail);
		}

		waitForUiLoad();

		final boolean appLoaded = isAnyVisibleText(Arrays.asList("Dashboard", "Inicio", "Negocio"), Duration.ofSeconds(20));
		final boolean sidebarVisible = isSidebarVisible();
		require(appLoaded, "Main application interface did not appear after login.");
		require(sidebarVisible, "Left sidebar navigation is not visible after login.");

		captureScreenshot("dashboard_loaded", "dashboard-loaded.png");
		return true;
	}

	private boolean stepOpenMiNegocioMenu() throws IOException {
		clickIfVisibleText("Negocio", Duration.ofSeconds(5));
		clickByVisibleText("Mi Negocio");

		require(isVisibleText("Agregar Negocio", Duration.ofSeconds(10)), "'Agregar Negocio' is not visible.");
		require(isVisibleText("Administrar Negocios", Duration.ofSeconds(10)), "'Administrar Negocios' is not visible.");

		captureScreenshot("mi_negocio_menu_expanded", "mi-negocio-menu-expanded.png");
		return true;
	}

	private boolean stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");

		require(isVisibleText("Crear Nuevo Negocio", Duration.ofSeconds(10)),
				"Modal title 'Crear Nuevo Negocio' is not visible.");
		require(isVisibleText("Nombre del Negocio", Duration.ofSeconds(10)),
				"'Nombre del Negocio' field label is not visible.");
		require(isVisibleText("Tienes 2 de 3 negocios", Duration.ofSeconds(10)),
				"'Tienes 2 de 3 negocios' is not visible.");
		require(isVisibleText("Cancelar", Duration.ofSeconds(10)), "'Cancelar' button is not visible.");
		require(isVisibleText("Crear Negocio", Duration.ofSeconds(10)), "'Crear Negocio' button is not visible.");

		captureScreenshot("agregar_negocio_modal", "agregar-negocio-modal.png");

		final WebElement businessNameInput = findBusinessNameInput();
		assertNotNull("Could not find 'Nombre del Negocio' input.", businessNameInput);
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']")));

		return true;
	}

	private boolean stepOpenAdministrarNegocios() throws IOException {
		if (!isVisibleText("Administrar Negocios", Duration.ofSeconds(5))) {
			clickByVisibleText("Mi Negocio");
		}
		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		require(isVisibleText("Información General", Duration.ofSeconds(15)), "Missing section 'Información General'.");
		require(isVisibleText("Detalles de la Cuenta", Duration.ofSeconds(15)), "Missing section 'Detalles de la Cuenta'.");
		require(isVisibleText("Tus Negocios", Duration.ofSeconds(15)), "Missing section 'Tus Negocios'.");
		require(isVisibleText("Sección Legal", Duration.ofSeconds(15)), "Missing section 'Sección Legal'.");

		captureScreenshot("administrar_negocios_page", "administrar-negocios-page.png");
		return true;
	}

	private boolean stepValidateInformacionGeneral() {
		final WebElement section = findSectionContainer("Información General");
		final String text = normalizedText(section.getText());

		require(hasUserName(text), "A user name was not detected in 'Información General'.");
		require(hasEmail(text), "A user email was not detected in 'Información General'.");
		require(isVisibleText("BUSINESS PLAN", Duration.ofSeconds(10)), "Text 'BUSINESS PLAN' is not visible.");
		require(isVisibleText("Cambiar Plan", Duration.ofSeconds(10)), "Button 'Cambiar Plan' is not visible.");
		return true;
	}

	private boolean stepValidateDetallesCuenta() {
		final WebElement section = findSectionContainer("Detalles de la Cuenta");
		final String text = normalizedText(section.getText());

		require(text.toLowerCase(Locale.ROOT).contains("cuenta creada"), "'Cuenta creada' is not visible.");
		require(text.toLowerCase(Locale.ROOT).contains("estado activo"), "'Estado activo' is not visible.");
		require(text.toLowerCase(Locale.ROOT).contains("idioma seleccionado"), "'Idioma seleccionado' is not visible.");
		return true;
	}

	private boolean stepValidateTusNegocios() {
		final WebElement section = findSectionContainer("Tus Negocios");
		final String text = normalizedText(section.getText());

		require(text.contains("Tienes 2 de 3 negocios"), "'Tienes 2 de 3 negocios' is not visible.");
		require(hasVisibleElement(section, By.xpath(".//*[normalize-space()='Agregar Negocio']")),
				"'Agregar Negocio' button is missing in 'Tus Negocios'.");
		require(isBusinessListVisible(section), "Business list is not visible in 'Tus Negocios'.");
		return true;
	}

	private boolean stepValidateLegalDocument(final String linkText, final String expectedHeading, final String screenshotFile)
			throws IOException {
		final WebElement legalSection = findSectionContainer("Sección Legal");
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String urlBefore = driver.getCurrentUrl();

		clickVisibleTextInsideOrFallback(legalSection, linkText);

		wait.until(drv -> {
			final boolean openedNewTab = drv.getWindowHandles().size() > handlesBefore.size();
			final boolean navigatedCurrentTab = !drv.getCurrentUrl().equals(urlBefore);
			final boolean headingNowVisible = isVisibleTextUnsafe(expectedHeading);
			return openedNewTab || navigatedCurrentTab || headingNowVisible;
		});

		final Optional<String> newHandle = driver.getWindowHandles().stream().filter(handle -> !handlesBefore.contains(handle))
				.findFirst();
		if (newHandle.isPresent()) {
			driver.switchTo().window(newHandle.get());
		}

		waitForUiLoad();
		require(isVisibleText(expectedHeading, Duration.ofSeconds(20)),
				"Heading '" + expectedHeading + "' is not visible on legal page.");
		require(hasLegalContent(), "Legal content text is not visible on legal page.");

		captureScreenshot("legal_" + sanitize(linkText), screenshotFile);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (newHandle.isPresent()) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		return true;
	}

	private boolean runStep(final String reportField, final StepAction step) {
		try {
			final boolean result = step.execute();
			stepStatus.put(reportField, result);
			if (!result) {
				stepDetails.put(reportField, "Step returned false.");
			}
			return result;
		} catch (final Throwable error) {
			stepStatus.put(reportField, false);
			stepDetails.put(reportField, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
			return false;
		}
	}

	private boolean requirePrecondition(final boolean precondition, final String failedStepName) {
		require(precondition, "Prerequisite step failed: " + failedStepName);
		return true;
	}

	private void captureScreenshot(final String label, final String fileName) throws IOException {
		final Path target = evidenceDir.resolve(fileName);
		final byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(target, screenshotBytes);
		screenshots.put(label, target.toString());
	}

	private void clickByVisibleText(final String text) {
		final WebElement element = waitForVisibleText(text, Duration.ofSeconds(20));
		clickElement(element);
		waitForUiLoad();
	}

	private void clickIfVisibleText(final String text, final Duration timeout) {
		if (isVisibleText(text, timeout)) {
			clickByVisibleText(text);
		}
	}

	private void clickAnyVisibleText(final List<String> candidates) {
		for (final String candidate : candidates) {
			if (isVisibleText(candidate, Duration.ofSeconds(5))) {
				clickByVisibleText(candidate);
				return;
			}
		}
		throw new IllegalStateException("None of the candidate texts were visible/clickable: " + candidates);
	}

	private void clickVisibleTextInsideOrFallback(final WebElement container, final String text) {
		final String escapedText = asXPathLiteral(text);
		final List<WebElement> scopedElements = container
				.findElements(By.xpath(".//*[normalize-space()=" + escapedText + "] | .//*[contains(normalize-space(),"
						+ escapedText + ")]"));

		for (final WebElement element : scopedElements) {
			if (element.isDisplayed()) {
				clickElement(element);
				waitForUiLoad();
				return;
			}
		}

		clickByVisibleText(text);
	}

	private void clickElement(final WebElement element) {
		scrollIntoView(element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception ignored) {
			try {
				new Actions(driver).moveToElement(element).click().perform();
			} catch (final Exception ignoredAgain) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			}
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private WebElement findSectionContainer(final String sectionHeadingText) {
		final WebElement heading = waitForVisibleText(sectionHeadingText, Duration.ofSeconds(20));
		final List<WebElement> containers = heading.findElements(By.xpath("./ancestor::*[self::section or self::div]"));

		for (final WebElement container : containers) {
			if (container.isDisplayed()) {
				return container;
			}
		}

		return heading;
	}

	private WebElement findBusinessNameInput() {
		final List<By> locators = Arrays.asList(
				By.xpath("//*[normalize-space()='Nombre del Negocio']/following::input[1]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"), By.xpath("//input[@name='nombreDelNegocio']"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"), By.xpath("//div[@role='dialog']//input"));

		for (final By locator : locators) {
			try {
				final WebElement candidate = new WebDriverWait(driver, Duration.ofSeconds(3))
						.until(ExpectedConditions.visibilityOfElementLocated(locator));
				if (candidate != null && candidate.isDisplayed()) {
					return candidate;
				}
			} catch (final TimeoutException ignored) {
				// Try next locator.
			}
		}

		return null;
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

	private boolean hasLegalContent() {
		final String bodyText = normalizedText(driver.findElement(By.tagName("body")).getText());
		return bodyText.length() > 120;
	}

	private boolean isBusinessListVisible(final WebElement section) {
		final List<WebElement> listCandidates = section.findElements(By.xpath(
				".//li | .//tr | .//article | .//div[contains(@class,'business')] | .//div[contains(@class,'negocio')]"));
		for (final WebElement element : listCandidates) {
			if (element.isDisplayed()) {
				return true;
			}
		}

		final String text = normalizedText(section.getText());
		final long textLines = Arrays.stream(text.split("\\R")).map(String::trim).filter(line -> !line.isEmpty()).count();
		return textLines >= 3;
	}

	private boolean hasVisibleElement(final WebElement container, final By locator) {
		final List<WebElement> elements = container.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean hasUserName(final String sectionText) {
		final List<String> ignored = Arrays.asList("Información General", "BUSINESS PLAN", "Cambiar Plan");
		final String[] lines = sectionText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.contains("@")) {
				continue;
			}
			if (ignored.stream().anyMatch(line::equalsIgnoreCase)) {
				continue;
			}
			if (line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*") && line.length() >= 3) {
				return true;
			}
		}
		return false;
	}

	private boolean hasEmail(final String text) {
		final Matcher matcher = EMAIL_PATTERN.matcher(text);
		return matcher.find();
	}

	private boolean isAnyVisibleText(final List<String> texts, final Duration timeout) {
		for (final String text : texts) {
			if (isVisibleText(text, timeout)) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisibleText(final String text, final Duration timeout) {
		try {
			waitForVisibleText(text, timeout);
			return true;
		} catch (final TimeoutException error) {
			return false;
		}
	}

	private boolean isVisibleTextUnsafe(final String text) {
		final String escapedText = asXPathLiteral(text);
		final List<WebElement> elements = driver
				.findElements(By.xpath("//*[normalize-space()=" + escapedText + "] | //*[contains(normalize-space(),"
						+ escapedText + ")]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private WebElement waitForVisibleText(final String text, final Duration timeout) {
		final String escapedText = asXPathLiteral(text);
		final By locator = By.xpath("//*[normalize-space()=" + escapedText + "] | //*[contains(normalize-space(),"
				+ escapedText + ")]");
		return new WebDriverWait(driver, timeout).until(drv -> {
			final List<WebElement> elements = drv.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private void waitForUiLoad() {
		final ExpectedCondition<Boolean> pageLoaded = drv -> {
			final Object state = ((JavascriptExecutor) drv).executeScript("return document.readyState");
			return state != null && "complete".equals(state.toString());
		};

		wait.until(pageLoaded);
		waitForCommonLoadersToDisappear();
	}

	private void waitForCommonLoadersToDisappear() {
		final List<By> loaders = Arrays.asList(By.cssSelector(".spinner"), By.cssSelector(".loading"),
				By.cssSelector("[data-testid='loading']"), By.cssSelector("[aria-busy='true']"));
		for (final By loader : loaders) {
			try {
				new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.invisibilityOfElementLocated(loader));
			} catch (final TimeoutException ignored) {
				// Some apps keep non-blocking busy indicators around; ignore timeout.
			}
		}
	}

	private String resolveLoginUrl() {
		final String explicitLoginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (explicitLoginUrl != null && !explicitLoginUrl.trim().isEmpty()) {
			return explicitLoginUrl.trim();
		}

		final String baseUrl = System.getenv("SALEADS_BASE_URL");
		if (baseUrl == null || baseUrl.trim().isEmpty()) {
			return null;
		}

		final String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		return normalizedBase + "/login";
	}

	private String envOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
	}

	private String normalizedText(final String text) {
		return text == null ? "" : text.replace('\u00A0', ' ').trim();
	}

	private String sanitize(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	private String asXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private void require(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("Evidence directory: " + evidenceDir.toAbsolutePath());
		lines.add("");
		lines.add("Final Report:");

		for (final String field : REPORT_FIELDS) {
			final boolean pass = stepStatus.getOrDefault(field, false);
			final String detail = stepDetails.get(field);
			lines.add("- " + field + ": " + (pass ? "PASS" : "FAIL") + (detail == null ? "" : " (" + detail + ")"));
		}

		lines.add("");
		lines.add("Captured URLs:");
		lines.add("- Términos y Condiciones: " + legalUrls.getOrDefault("Términos y Condiciones", "N/A"));
		lines.add("- Política de Privacidad: " + legalUrls.getOrDefault("Política de Privacidad", "N/A"));

		lines.add("");
		lines.add("Screenshots:");
		if (screenshots.isEmpty()) {
			lines.add("- None");
		} else {
			screenshots.forEach((label, path) -> lines.add("- " + label + ": " + path));
		}

		Files.write(reportPath, lines, StandardCharsets.UTF_8);
		System.out.println("SaleADS E2E final report: " + reportPath.toAbsolutePath());
	}

	@FunctionalInterface
	private interface StepAction {
		boolean execute() throws Exception;
	}
}
