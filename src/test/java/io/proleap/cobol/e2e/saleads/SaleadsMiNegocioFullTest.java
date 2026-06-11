package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
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
import java.util.Map;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDir;

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> details = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set SALEADS_RUN_E2E=true to execute this external environment test.",
				Boolean.parseBoolean(envOrDefault("SALEADS_RUN_E2E", "false")));

		final String loginUrl = envOrDefault("SALEADS_LOGIN_URL", "");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the login page URL of the target SaleADS environment.",
				!loginUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		screenshotsDir = Paths.get("target", "saleads-screenshots", LocalDateTime.now().format(TIMESTAMP));
		Files.createDirectories(screenshotsDir);

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
		System.out.println(buildReportSummary());
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep("Login", this::validateLoginWithGoogle);
		runStep("Mi Negocio menu", this::validateMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::validateAdministrarNegociosView);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", this::validateTerminosYCondiciones);
		runStep("Política de Privacidad", this::validatePoliticaPrivacidad);

		final List<String> failed = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());

		assertTrue("Workflow validation failures:\n" + buildReportSummary(), failed.isEmpty());
	}

	private void validateLoginWithGoogle() {
		clickFirstVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google"));
		waitForUiToLoad();

		selectGoogleAccountIfVisible();
		waitForUiToLoad();

		boolean sidebarVisible = anyElementVisible(
				Arrays.asList(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar')]")));
		boolean negocioVisible = textVisible("Negocio");
		assertStep(sidebarVisible && negocioVisible,
				"Main interface was not detected after login (sidebar/Negocio missing).");

		takeScreenshot("01-dashboard-loaded");
	}

	private void validateMiNegocioMenu() {
		ensureSidebarSectionExpanded("Negocio");
		clickVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertStep(textVisible("Agregar Negocio"), "'Agregar Negocio' is not visible in expanded menu.");
		assertStep(textVisible("Administrar Negocios"), "'Administrar Negocios' is not visible in expanded menu.");

		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() {
		clickVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertStep(textVisible("Crear Nuevo Negocio"), "Modal title 'Crear Nuevo Negocio' is not visible.");
		assertStep(inputForNombreNegocioVisible(), "Input field for 'Nombre del Negocio' was not found.");
		assertStep(textVisible("Tienes 2 de 3 negocios"), "Expected limit text is not visible in modal.");
		assertStep(textVisible("Cancelar"), "Button 'Cancelar' is missing.");
		assertStep(textVisible("Crear Negocio"), "Button 'Crear Negocio' is missing.");

		fillNombreNegocioIfPresent("Negocio Prueba Automatización");
		clickVisibleText("Cancelar");
		waitForUiToLoad();

		takeScreenshot("03-agregar-negocio-modal");
	}

	private void validateAdministrarNegociosView() {
		ensureSidebarSectionExpanded("Mi Negocio");
		clickVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertStep(textVisible("Información General"), "Section 'Información General' was not found.");
		assertStep(textVisible("Detalles de la Cuenta"), "Section 'Detalles de la Cuenta' was not found.");
		assertStep(textVisible("Tus Negocios"), "Section 'Tus Negocios' was not found.");
		assertStep(textVisible("Sección Legal"), "Section 'Sección Legal' was not found.");

		takeScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		final String infoText = sectionText("Información General");
		assertStep(!extractPossibleNameLines(infoText).isEmpty(), "User name was not detected in 'Información General'.");
		assertStep(EMAIL_PATTERN.matcher(infoText).find(), "User email was not detected in 'Información General'.");
		assertStep(textVisible("BUSINESS PLAN"), "Text 'BUSINESS PLAN' is not visible.");
		assertStep(textVisible("Cambiar Plan"), "Button 'Cambiar Plan' is not visible.");
	}

	private void validateDetallesCuenta() {
		assertStep(textVisible("Cuenta creada"), "'Cuenta creada' is not visible.");
		assertStep(textVisible("Estado activo"), "'Estado activo' is not visible.");
		assertStep(textVisible("Idioma seleccionado"), "'Idioma seleccionado' is not visible.");
	}

	private void validateTusNegocios() {
		assertStep(sectionExists("Tus Negocios"), "'Tus Negocios' section is not visible.");
		assertStep(textVisible("Agregar Negocio"), "Button 'Agregar Negocio' is missing in 'Tus Negocios'.");
		assertStep(textVisible("Tienes 2 de 3 negocios"), "Text 'Tienes 2 de 3 negocios' is missing in 'Tus Negocios'.");
	}

	private void validateTerminosYCondiciones() {
		validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "05-terminos-y-condiciones");
	}

	private void validatePoliticaPrivacidad() {
		validateLegalLink("Política de Privacidad", "Política de Privacidad", "06-politica-de-privacidad");
	}

	private void validateLegalLink(final String linkText, final String expectedHeading, final String screenshotName) {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickVisibleText(linkText);
		waitForUiToLoad();

		String legalWindow = appWindow;
		if (driver.getWindowHandles().size() > windowsBefore.size()) {
			final Set<String> windowsAfter = new LinkedHashSet<>(driver.getWindowHandles());
			windowsAfter.removeAll(windowsBefore);
			if (!windowsAfter.isEmpty()) {
				legalWindow = windowsAfter.iterator().next();
				driver.switchTo().window(legalWindow);
				waitForUiToLoad();
			}
		}

		assertStep(textVisible(expectedHeading), "Expected legal heading '" + expectedHeading + "' is not visible.");
		assertStep(!driver.findElements(By.xpath("//p[string-length(normalize-space()) > 40]")).isEmpty(),
				"Legal content text is not visible for '" + expectedHeading + "'.");

		takeScreenshot(screenshotName);
		details.put(expectedHeading + " URL", driver.getCurrentUrl());

		if (!legalWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();
	}

	private void ensureSidebarSectionExpanded(final String sectionLabel) {
		if (!textVisible(sectionLabel)) {
			return;
		}

		if (!textVisible("Agregar Negocio") && !textVisible("Administrar Negocios")) {
			clickVisibleText(sectionLabel);
			waitForUiToLoad();
		}
	}

	private void selectGoogleAccountIfVisible() {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
			final WebElement accountOption = shortWait
					.until(ExpectedConditions.visibilityOfElementLocated(byExactText(GOOGLE_ACCOUNT_EMAIL)));
			wait.until(ExpectedConditions.elementToBeClickable(accountOption)).click();
		} catch (final TimeoutException ignored) {
			// Account chooser can be bypassed in already-authenticated sessions.
		}
	}

	private boolean inputForNombreNegocioVisible() {
		final List<By> selectors = Arrays.asList(
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[@name='nombreNegocio']"));

		for (final By selector : selectors) {
			if (!driver.findElements(selector).isEmpty() && driver.findElement(selector).isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void fillNombreNegocioIfPresent(final String value) {
		final List<By> selectors = Arrays.asList(
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//input[@name='nombreNegocio']"));

		for (final By selector : selectors) {
			final List<WebElement> elements = driver.findElements(selector);
			if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
				final WebElement input = elements.get(0);
				input.clear();
				input.sendKeys(value);
				return;
			}
		}
	}

	private String sectionText(final String sectionTitle) {
		final WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(byExactText(sectionTitle)));
		final WebElement section = nearestSectionContainer(heading);
		return section.getText();
	}

	private boolean sectionExists(final String sectionTitle) {
		return !driver.findElements(byExactText(sectionTitle)).isEmpty();
	}

	private List<String> extractPossibleNameLines(final String block) {
		final List<String> lines = Arrays.stream(block.split("\\R")).map(String::trim).filter(line -> !line.isBlank())
				.collect(Collectors.toList());

		final List<String> excluded = Arrays.asList("Información General", "BUSINESS PLAN", "Cambiar Plan");
		final List<String> candidates = new ArrayList<>();
		for (final String line : lines) {
			if (excluded.stream().noneMatch(line::equalsIgnoreCase) && !EMAIL_PATTERN.matcher(line).find() && line.length() >= 3) {
				candidates.add(line);
			}
		}
		return candidates;
	}

	private WebElement nearestSectionContainer(final WebElement heading) {
		final List<By> candidates = Arrays.asList(By.xpath("./ancestor::section[1]"), By.xpath("./ancestor::div[1]"));
		for (final By candidate : candidates) {
			final List<WebElement> matches = heading.findElements(candidate);
			if (!matches.isEmpty()) {
				return matches.get(0);
			}
		}
		return heading;
	}

	private void clickVisibleText(final String text) {
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(byExactText(text)));
		element.click();
		waitForUiToLoad();
	}

	private void clickFirstVisibleText(final List<String> texts) {
		for (final String text : texts) {
			final List<WebElement> matches = driver.findElements(byExactText(text));
			if (!matches.isEmpty() && matches.get(0).isDisplayed()) {
				wait.until(ExpectedConditions.elementToBeClickable(matches.get(0))).click();
				waitForUiToLoad();
				return;
			}
		}
		throw new IllegalStateException("No clickable element found for texts: " + texts);
	}

	private boolean textVisible(final String text) {
		final List<WebElement> matches = driver.findElements(byExactText(text));
		return matches.stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean anyElementVisible(final List<By> selectors) {
		for (final By selector : selectors) {
			if (driver.findElements(selector).stream().anyMatch(WebElement::isDisplayed)) {
				return true;
			}
		}
		return false;
	}

	private By byExactText(final String text) {
		return By.xpath("//*[normalize-space()='" + text + "']");
	}

	private void waitForUiToLoad() {
		wait.until(documentReady());
		try {
			Thread.sleep(400L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private ExpectedCondition<Boolean> documentReady() {
		return webDriver -> "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState"));
	}

	private void takeScreenshot(final String checkpointName) {
		try {
			final byte[] image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			final Path output = screenshotsDir.resolve(checkpointName + ".png");
			Files.write(output, image);
		} catch (final IOException e) {
			throw new RuntimeException("Could not write screenshot for checkpoint '" + checkpointName + "'.", e);
		}
	}

	private void runStep(final String label, final Runnable validation) {
		try {
			validation.run();
			report.put(label, true);
		} catch (final Exception e) {
			report.put(label, false);
			details.put(label, e.getMessage());
		}
	}

	private void assertStep(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private String buildReportSummary() {
		final StringBuilder sb = new StringBuilder();
		sb.append(System.lineSeparator()).append("SaleADS Mi Negocio Full Test Report").append(System.lineSeparator());
		report.forEach((key, value) -> sb.append("- ").append(key).append(": ").append(value ? "PASS" : "FAIL")
				.append(System.lineSeparator()));

		if (!details.isEmpty()) {
			sb.append("Details:").append(System.lineSeparator());
			details.forEach((key, value) -> sb.append("  * ").append(key).append(": ").append(value)
					.append(System.lineSeparator()));
		}
		sb.append("Screenshots: ").append(screenshotsDir == null ? "N/A (test skipped before setup)" : screenshotsDir)
				.append(System.lineSeparator());
		return sb.toString();
	}

	private String envOrDefault(final String key, final String fallback) {
		return System.getenv().getOrDefault(key, fallback);
	}
}
