package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio".
 *
 * <p>
 * Runtime configuration:
 * <ul>
 * <li>saleads.loginUrl or SALEADS_LOGIN_URL (required)</li>
 * <li>saleads.browser=chrome|firefox (optional, default: chrome)</li>
 * <li>saleads.headless=true|false (optional, default: false)</li>
 * <li>saleads.timeoutSeconds (optional, default: 30)</li>
 * </ul>
 */
public class SaleAdsMiNegocioFullTest {

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
			Pattern.CASE_INSENSITIVE);

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> failureDetails = new LinkedHashMap<>();
	private final List<Path> screenshots = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private int screenshotCounter = 0;
	private String termsUrl;
	private String privacyUrl;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = firstNonBlank(System.getProperty("saleads.loginUrl"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"saleads.loginUrl (or SALEADS_LOGIN_URL) must point to the current environment login page.",
				loginUrl != null && !loginUrl.isBlank());

		driver = buildDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(resolveTimeoutSeconds()));
		screenshotDirectory = Paths.get("target", "screenshots", "saleads-mi-negocio",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(screenshotDirectory);

		driver.get(loginUrl);
		waitForUiToLoad();
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
		boolean loginOk = runStep("Login", this::stepLoginWithGoogle);
		boolean menuOk = runStepIfAllowed("Mi Negocio menu", loginOk, this::stepOpenMiNegocioMenu);
		boolean agregarModalOk = runStepIfAllowed("Agregar Negocio modal", menuOk, this::stepValidateAgregarNegocioModal);
		boolean administrarOk = runStepIfAllowed("Administrar Negocios view", menuOk, this::stepOpenAdministrarNegocios);
		boolean infoGeneralOk = runStepIfAllowed("Información General", administrarOk, this::stepValidateInformacionGeneral);
		boolean detallesOk = runStepIfAllowed("Detalles de la Cuenta", administrarOk, this::stepValidateDetallesCuenta);
		boolean negociosOk = runStepIfAllowed("Tus Negocios", administrarOk, this::stepValidateTusNegocios);
		boolean terminosOk = runStepIfAllowed("Términos y Condiciones", administrarOk, this::stepValidateTerminos);
		boolean privacidadOk = runStepIfAllowed("Política de Privacidad", administrarOk, this::stepValidatePrivacidad);

		assertTrue("Some workflow validations failed. Check report output for details.",
				loginOk && menuOk && agregarModalOk && administrarOk && infoGeneralOk && detallesOk && negociosOk
						&& terminosOk && privacidadOk);
	}

	private void stepLoginWithGoogle() throws IOException {
		boolean clickedDirectGoogle = tryClickFirstVisibleText(
				Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google"));
		if (!clickedDirectGoogle) {
			clickFirstVisibleText(Arrays.asList("Iniciar sesión", "Iniciar Sesión", "Login", "Entrar", "Sign in"));
			waitForUiToLoad();
			clickFirstVisibleText(
					Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google"));
		}
		waitForUiToLoad();

		// Google account picker can appear in some environments.
		if (isAnyTextVisible(Arrays.asList("Choose an account", "Elige una cuenta"), Duration.ofSeconds(4))) {
			clickFirstVisibleText(Arrays.asList("juanlucasbarbiergarzon@gmail.com"));
			waitForUiToLoad();
		}

		assertTrue("Main app interface was not detected after login.",
				isAnyTextVisible(Arrays.asList("Dashboard", "Inicio", "Mi Negocio", "Negocio"), Duration.ofSeconds(25)));
		assertTrue("Left sidebar navigation is not visible.", isSidebarVisible());
		takeScreenshot("dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		WebElement sidebar = waitForSidebar();
		clickTextInContainer(sidebar, Arrays.asList("Negocio"));
		waitForUiToLoad();
		clickTextInContainer(sidebar, Arrays.asList("Mi Negocio"));
		waitForUiToLoad();

		assertTrue("Expected 'Agregar Negocio' was not visible.", isAnyTextVisible(Arrays.asList("Agregar Negocio")));
		assertTrue("Expected 'Administrar Negocios' was not visible.",
				isAnyTextVisible(Arrays.asList("Administrar Negocios")));
		takeScreenshot("mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickFirstVisibleTextInSidebarThenFallback(Arrays.asList("Agregar Negocio"));
		waitForUiToLoad();

		assertTrue("Modal title 'Crear Nuevo Negocio' is not visible.",
				isAnyTextVisible(Arrays.asList("Crear Nuevo Negocio")));
		WebElement nombreDelNegocioInput = findInputByNearbyText("Nombre del Negocio")
				.orElseThrow(() -> new AssertionError("Input field 'Nombre del Negocio' does not exist."));
		assertTrue("Text 'Tienes 2 de 3 negocios' is not visible.",
				isAnyTextVisible(Arrays.asList("Tienes 2 de 3 negocios", "Tienes 2 de 3 negocios.")));
		assertTrue("Button 'Cancelar' is not visible.", isAnyTextVisible(Arrays.asList("Cancelar")));
		assertTrue("Button 'Crear Negocio' is not visible.", isAnyTextVisible(Arrays.asList("Crear Negocio")));
		takeScreenshot("crear-nuevo-negocio-modal");

		nombreDelNegocioInput.click();
		nombreDelNegocioInput.clear();
		nombreDelNegocioInput.sendKeys("Negocio Prueba Automatizacion");
		clickFirstVisibleText(Arrays.asList("Cancelar"));
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(
				"//*[contains(normalize-space(.), " + xPathLiteral("Crear Nuevo Negocio") + ")]")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isAnyTextVisible(Arrays.asList("Administrar Negocios"), Duration.ofSeconds(2))) {
			clickFirstVisibleTextInSidebarThenFallback(Arrays.asList("Mi Negocio"));
			waitForUiToLoad();
		}

		clickFirstVisibleTextInSidebarThenFallback(Arrays.asList("Administrar Negocios"));
		waitForUiToLoad();

		assertTrue("Section 'Información General' does not exist.",
				isAnyTextVisible(Arrays.asList("Información General", "Informacion General")));
		assertTrue("Section 'Detalles de la Cuenta' does not exist.",
				isAnyTextVisible(Arrays.asList("Detalles de la Cuenta")));
		assertTrue("Section 'Tus Negocios' does not exist.", isAnyTextVisible(Arrays.asList("Tus Negocios")));
		assertTrue("Section 'Sección Legal' does not exist.",
				isAnyTextVisible(Arrays.asList("Sección Legal", "Seccion Legal")));
		takeScreenshot("administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() {
		assertTrue("User name is not visible in Información General.",
				isAnyTextVisible(Arrays.asList("Nombre", "Usuario", "Name", "User")));
		assertTrue("User email is not visible in Información General.", EMAIL_PATTERN.matcher(getBodyText()).find());
		assertTrue("Text 'BUSINESS PLAN' is not visible.", isAnyTextVisible(Arrays.asList("BUSINESS PLAN")));
		assertTrue("Button 'Cambiar Plan' is not visible.",
				isAnyTextVisible(Arrays.asList("Cambiar Plan", "Change Plan")));
	}

	private void stepValidateDetallesCuenta() {
		assertTrue("'Cuenta creada' is not visible.", isAnyTextVisible(Arrays.asList("Cuenta creada", "Cuenta Creada")));
		assertTrue("'Estado activo' is not visible.",
				isAnyTextVisible(Arrays.asList("Estado activo", "Estado Activo", "Activo")));
		assertTrue("'Idioma seleccionado' is not visible.",
				isAnyTextVisible(Arrays.asList("Idioma seleccionado", "Idioma Seleccionado")));
	}

	private void stepValidateTusNegocios() {
		assertTrue("Section 'Tus Negocios' is not visible.", isAnyTextVisible(Arrays.asList("Tus Negocios")));
		assertTrue("Button 'Agregar Negocio' does not exist.", isAnyTextVisible(Arrays.asList("Agregar Negocio")));
		assertTrue("Text 'Tienes 2 de 3 negocios' is not visible.",
				isAnyTextVisible(Arrays.asList("Tienes 2 de 3 negocios")));

		WebElement negociosSection = findContainerByHeading(Arrays.asList("Tus Negocios"))
				.orElseThrow(() -> new AssertionError("Could not locate 'Tus Negocios' section."));
		List<WebElement> rows = negociosSection
				.findElements(By.xpath(".//li | .//tr | .//*[contains(@class, 'card')] | .//*[contains(@class, 'item')]"));
		assertTrue("Business list is not visible in 'Tus Negocios'.", !rows.isEmpty());
	}

	private void stepValidateTerminos() throws IOException {
		termsUrl = openLegalLinkAndValidate(Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
				Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"), "terminos-y-condiciones");
	}

	private void stepValidatePrivacidad() throws IOException {
		privacyUrl = openLegalLinkAndValidate(Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
				Arrays.asList("Política de Privacidad", "Politica de Privacidad"), "politica-de-privacidad");
	}

	private String openLegalLinkAndValidate(final List<String> linkTexts, final List<String> headingTexts,
			final String screenshotName) throws IOException {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String urlBeforeClick = driver.getCurrentUrl();

		clickFirstVisibleText(linkTexts);

		wait.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size()
				|| !d.getCurrentUrl().equals(urlBeforeClick) || isAnyTextVisible(headingTexts, Duration.ofSeconds(1)));
		waitForUiToLoad();

		Set<String> handlesAfterClick = new LinkedHashSet<>(driver.getWindowHandles());
		boolean openedNewTab = handlesAfterClick.size() > handlesBeforeClick.size();
		if (openedNewTab) {
			handlesAfterClick.removeAll(handlesBeforeClick);
			String newHandle = handlesAfterClick.iterator().next();
			driver.switchTo().window(newHandle);
			waitForUiToLoad();
		}

		assertTrue("Expected legal heading was not found: " + headingTexts,
				isAnyTextVisible(headingTexts, Duration.ofSeconds(20)));
		assertTrue("Expected legal body content to be visible.", normalizeSpaces(getBodyText()).length() > 150);
		takeScreenshot(screenshotName);

		String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
			assertTrue("Failed to return to app after legal page validation.",
					isAnyTextVisible(Arrays.asList("Sección Legal", "Seccion Legal"), Duration.ofSeconds(20)));
		}

		return finalUrl;
	}

	private boolean runStepIfAllowed(final String stepName, final boolean prerequisite, final CheckedRunnable runnable) {
		if (!prerequisite) {
			report.put(stepName, Boolean.FALSE);
			failureDetails.put(stepName, "Not executed because a prerequisite step failed.");
			return false;
		}
		return runStep(stepName, runnable);
	}

	private boolean runStep(final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			report.put(stepName, Boolean.TRUE);
			return true;
		} catch (Throwable throwable) {
			report.put(stepName, Boolean.FALSE);
			failureDetails.put(stepName, throwable.getMessage() == null ? throwable.getClass().getName()
					: throwable.getMessage());
			try {
				takeScreenshot("failure-" + sanitizeForFileName(stepName));
			} catch (IOException ignored) {
				// Ignore screenshot failures; do not hide original validation failure.
			}
			return false;
		}
	}

	private WebDriver buildDriver() {
		final String browser = firstNonBlank(System.getProperty("saleads.browser"), "chrome").toLowerCase();
		final boolean headless = Boolean.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"), "false"));

		switch (browser) {
		case "firefox":
			FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (headless) {
				firefoxOptions.addArguments("-headless");
			}
			return new FirefoxDriver(firefoxOptions);
		case "chrome":
		default:
			ChromeOptions chromeOptions = new ChromeOptions();
			chromeOptions.addArguments("--disable-popup-blocking");
			if (headless) {
				chromeOptions.addArguments("--headless=new");
			}
			return new ChromeDriver(chromeOptions);
		}
	}

	private void clickFirstVisibleTextInSidebarThenFallback(final List<String> texts) {
		Optional<WebElement> sidebar = findSidebar();
		if (sidebar.isPresent()) {
			try {
				clickTextInContainer(sidebar.get(), texts);
				return;
			} catch (AssertionError ignored) {
				// Fallback to global search when a sidebar-scoped click is not possible.
			}
		}
		clickFirstVisibleText(texts);
	}

	private void clickFirstVisibleText(final List<String> texts) {
		if (!tryClickFirstVisibleText(texts)) {
			throw new AssertionError("No clickable element found for visible texts: " + texts);
		}
	}

	private boolean tryClickFirstVisibleText(final List<String> texts) {
		for (String text : texts) {
			Optional<WebElement> candidate = firstVisibleElement(
					By.xpath("//*[self::button or self::a or @role='button' or self::div or self::span]"
							+ "[contains(normalize-space(.), " + xPathLiteral(text) + ")]"));
			if (candidate.isPresent()) {
				click(candidate.get());
				waitForUiToLoad();
				return true;
			}
		}
		return false;
	}

	private void clickTextInContainer(final WebElement container, final List<String> texts) {
		for (String text : texts) {
			List<WebElement> candidates = container.findElements(By.xpath(
					".//*[self::button or self::a or @role='button' or self::div or self::span][contains(normalize-space(.), "
							+ xPathLiteral(text) + ")]"));
			for (WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					click(candidate);
					waitForUiToLoad();
					return;
				}
			}
		}
		throw new AssertionError("No clickable element found in container for texts: " + texts);
	}

	private Optional<WebElement> findInputByNearbyText(final String fieldText) {
		final String literal = xPathLiteral(fieldText);
		List<By> locators = Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), " + literal + ")]/following::input[1]"),
				By.xpath("//*[contains(normalize-space(.), " + literal + ")]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, " + literal + ")]"));

		for (By locator : locators) {
			Optional<WebElement> candidate = firstVisibleElement(locator);
			if (candidate.isPresent()) {
				return candidate;
			}
		}
		return Optional.empty();
	}

	private Optional<WebElement> findContainerByHeading(final List<String> headings) {
		for (String heading : headings) {
			String headingLiteral = xPathLiteral(heading);
			List<WebElement> headerMatches = driver.findElements(
					By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::p or self::span]"
							+ "[contains(normalize-space(.), " + headingLiteral + ")]"));
			for (WebElement headerMatch : headerMatches) {
				if (!headerMatch.isDisplayed()) {
					continue;
				}
				List<WebElement> containers = headerMatch
						.findElements(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
				if (!containers.isEmpty() && containers.get(0).isDisplayed()) {
					return Optional.of(containers.get(0));
				}
			}
		}
		return Optional.empty();
	}

	private boolean isAnyTextVisible(final List<String> texts) {
		return isAnyTextVisible(texts, Duration.ofSeconds(8));
	}

	private boolean isAnyTextVisible(final List<String> texts, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(d -> {
				for (String text : texts) {
					if (hasVisibleText(text)) {
						return true;
					}
				}
				return false;
			});
			return true;
		} catch (TimeoutException ex) {
			return false;
		}
	}

	private boolean hasVisibleText(final String text) {
		List<WebElement> matches = driver.findElements(
				By.xpath("//*[contains(normalize-space(.), " + xPathLiteral(text) + ")]"));
		return matches.stream().anyMatch(WebElement::isDisplayed);
	}

	private Optional<WebElement> firstVisibleElement(final By locator) {
		List<WebElement> elements = driver.findElements(locator);
		for (WebElement element : elements) {
			if (element.isDisplayed()) {
				return Optional.of(element);
			}
		}
		return Optional.empty();
	}

	private Optional<WebElement> findSidebar() {
		List<By> sidebars = Arrays.asList(By.cssSelector("aside"), By.xpath("//nav"), By.xpath("//*[@role='navigation']"));
		for (By sidebarLocator : sidebars) {
			Optional<WebElement> sidebar = firstVisibleElement(sidebarLocator);
			if (sidebar.isPresent()) {
				return sidebar;
			}
		}
		return Optional.empty();
	}

	private WebElement waitForSidebar() {
		wait.until(d -> isSidebarVisible());
		return findSidebar().orElseThrow(() -> new AssertionError("Sidebar navigation was not found."));
	}

	private boolean isSidebarVisible() {
		return findSidebar().isPresent();
	}

	private void click(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (Exception clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void waitForUiToLoad() {
		wait.until(d -> ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete"));
		try {
			Thread.sleep(500);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		String filename = String.format("%02d-%s.png", ++screenshotCounter, sanitizeForFileName(checkpointName));
		Path target = screenshotDirectory.resolve(filename);
		Files.copy(src.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		screenshots.add(target);
	}

	private void printFinalReport() {
		System.out.println("==== SaleADS Mi Negocio Final Report ====");
		for (String reportField : REPORT_FIELDS) {
			boolean stepResult = report.getOrDefault(reportField, Boolean.FALSE);
			System.out.printf("- %s: %s%n", reportField, stepResult ? "PASS" : "FAIL");
			if (!stepResult && failureDetails.containsKey(reportField)) {
				System.out.printf("  reason: %s%n", failureDetails.get(reportField));
			}
		}
		if (termsUrl != null) {
			System.out.printf("Términos y Condiciones URL: %s%n", termsUrl);
		}
		if (privacyUrl != null) {
			System.out.printf("Política de Privacidad URL: %s%n", privacyUrl);
		}
		System.out.printf("Screenshots directory: %s%n", screenshotDirectory);
		if (!screenshots.isEmpty()) {
			System.out.println("Captured screenshots:");
			for (Path screenshot : screenshots) {
				System.out.printf("  - %s%n", screenshot);
			}
		}
		System.out.println("=========================================");
	}

	private long resolveTimeoutSeconds() {
		try {
			return Long.parseLong(firstNonBlank(System.getProperty("saleads.timeoutSeconds"), "30"));
		} catch (NumberFormatException ex) {
			return 30L;
		}
	}

	private String getBodyText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private static String firstNonBlank(final String primary, final String fallback) {
		if (primary != null && !primary.isBlank()) {
			return primary;
		}
		return fallback;
	}

	private static String normalizeSpaces(final String value) {
		return value == null ? "" : value.replaceAll("\\s+", " ").trim();
	}

	private static String sanitizeForFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static String xPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		String[] chunks = value.split("'");
		return "concat(" + Arrays.stream(chunks).map(chunk -> "'" + chunk + "'").collect(Collectors.joining(",\"'\","))
				+ ")";
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
