package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(25);
	private static final Duration SHORT_WAIT = Duration.ofSeconds(5);
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private int screenshotCounter;
	private String appWindowHandle;

	private final Map<String, Boolean> stepReport = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		initializeReport();
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		if (isHeadlessEnabled()) {
			options.addArguments("--headless=new");
		}

		final String chromeBinary = System.getenv("SALEADS_CHROME_BINARY");
		if (chromeBinary != null && !chromeBinary.isBlank()) {
			options.setBinary(chromeBinary);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		evidenceDir = Paths.get("target", "saleads-evidence", TIMESTAMP_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		final String startUrl = System.getenv("SALEADS_URL");
		if (startUrl != null && !startUrl.isBlank()) {
			driver.get(startUrl);
		}

		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		printFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad"));

		final String reportOutput = renderReport();
		Files.writeString(evidenceDir.resolve("final-report.txt"), reportOutput);
		assertFalse("At least one required validation failed.\n" + reportOutput, stepReport.containsValue(Boolean.FALSE));
	}

	private void stepLoginWithGoogle() throws IOException {
		clickAnyVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"));
		waitForUiToLoad();
		selectGoogleAccountIfVisible();

		assertAnyTextVisible(
				Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Inicio", "Administrar Negocios"),
				"Main application interface was not detected after Google login.");
		ensureSidebarIsVisible();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		ensureSidebarIsVisible();
		ensureMiNegocioExpanded();
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickVisibleText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		assertBusinessNameInputPresent();
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		findVisibleBusinessNameInput().ifPresent(input -> {
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
		});
		clickVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureMiNegocioExpanded();
		clickVisibleText("Administrar Negocios");
		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		takeScreenshot("04-administrar-negocios-account-page");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
		assertEmailVisible();
		assertUserNameVisible();
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(final String linkText) throws IOException {
		ensureTextOnPage(linkText);
		final String currentHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String urlBefore = driver.getCurrentUrl();

		clickVisibleText(linkText);
		final String openedHandle = waitForNewWindowOrNavigation(handlesBefore, urlBefore);

		boolean openedNewTab = false;
		if (openedHandle != null && !openedHandle.equals(currentHandle)) {
			driver.switchTo().window(openedHandle);
			openedNewTab = true;
		}

		waitForUiToLoad();
		assertTextVisible(linkText);
		assertLegalContentVisible(linkText);
		takeScreenshot("05-legal-" + slugify(linkText));
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.execute();
			stepReport.put(stepName, Boolean.TRUE);
			stepErrors.put(stepName, "");
		} catch (final Throwable throwable) {
			stepReport.put(stepName, Boolean.FALSE);
			stepErrors.put(stepName, throwable.getMessage() == null ? throwable.getClass().getName() : throwable.getMessage());
			try {
				takeScreenshot("error-" + slugify(stepName));
			} catch (final IOException ignored) {
				// Ignore evidence failure and keep executing remaining checks.
			}
			tryReturnToAppWindow();
		}
	}

	private void initializeReport() {
		stepReport.put("Login", Boolean.FALSE);
		stepReport.put("Mi Negocio menu", Boolean.FALSE);
		stepReport.put("Agregar Negocio modal", Boolean.FALSE);
		stepReport.put("Administrar Negocios view", Boolean.FALSE);
		stepReport.put("Información General", Boolean.FALSE);
		stepReport.put("Detalles de la Cuenta", Boolean.FALSE);
		stepReport.put("Tus Negocios", Boolean.FALSE);
		stepReport.put("Términos y Condiciones", Boolean.FALSE);
		stepReport.put("Política de Privacidad", Boolean.FALSE);
	}

	private void ensureSidebarIsVisible() {
		final List<By> candidates = new ArrayList<>();
		candidates.add(By.xpath("//aside"));
		candidates.add(By.xpath("//nav"));
		candidates.add(By.xpath("//div[contains(@class,'sidebar')]"));

		for (final By by : candidates) {
			final Optional<WebElement> element = findFirstVisible(by, SHORT_WAIT);
			if (element.isPresent()) {
				return;
			}
		}

		assertTextVisible("Negocio");
	}

	private void ensureMiNegocioExpanded() {
		if (isTextVisible("Agregar Negocio") && isTextVisible("Administrar Negocios")) {
			return;
		}
		clickVisibleText("Negocio");
		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			clickVisibleText("Mi Negocio");
		}
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
	}

	private void selectGoogleAccountIfVisible() {
		final Optional<WebElement> accountOption = findFirstVisible(
				By.xpath("//*[contains(normalize-space()," + xPathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]"), SHORT_WAIT);
		if (accountOption.isPresent()) {
			safeClick(accountOption.get());
			waitForUiToLoad();
		}
	}

	private void clickAnyVisibleText(final List<String> textCandidates) {
		for (final String candidate : textCandidates) {
			final Optional<WebElement> element = findVisibleByText(candidate, SHORT_WAIT);
			if (element.isPresent()) {
				safeClick(element.get());
				waitForUiToLoad();
				return;
			}
		}
		throw new AssertionError("Could not find a clickable login button by visible text candidates: " + textCandidates);
	}

	private void clickVisibleText(final String text) {
		final WebElement element = findVisibleByText(text, DEFAULT_WAIT)
				.orElseThrow(() -> new AssertionError("Element with visible text not found: " + text));
		safeClick(element);
		waitForUiToLoad();
	}

	private Optional<WebElement> findVisibleByText(final String text, final Duration timeout) {
		final By locator = By.xpath("//*[normalize-space()=" + xPathLiteral(text) + "]");
		return findFirstVisible(locator, timeout);
	}

	private Optional<WebElement> findFirstVisible(final By locator, final Duration timeout) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			return Optional.ofNullable(shortWait.until(d -> {
				for (final WebElement element : d.findElements(locator)) {
					try {
						if (element.isDisplayed()) {
							return element;
						}
					} catch (final StaleElementReferenceException ignored) {
						// Retry until stable.
					}
				}
				return null;
			}));
		} catch (final TimeoutException ex) {
			return Optional.empty();
		}
	}

	private void assertTextVisible(final String text) {
		assertTrue("Expected text was not visible: " + text, findVisibleByText(text, DEFAULT_WAIT).isPresent());
	}

	private void assertAnyTextVisible(final List<String> candidates, final String messageIfMissing) {
		for (final String candidate : candidates) {
			if (findVisibleByText(candidate, SHORT_WAIT).isPresent()) {
				return;
			}
		}
		throw new AssertionError(messageIfMissing);
	}

	private void assertBusinessNameInputPresent() {
		assertTrue("Field 'Nombre del Negocio' was not found.", findVisibleBusinessNameInput().isPresent());
	}

	private Optional<WebElement> findVisibleBusinessNameInput() {
		final String inputLocator = "//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio'"
				+ " or @name='businessName' or @id='businessName' or @name='nombreNegocio' or @id='nombreNegocio']"
				+ " | //label[normalize-space()='Nombre del Negocio']/following::input[1]";
		return findFirstVisible(By.xpath(inputLocator), DEFAULT_WAIT);
	}

	private void assertEmailVisible() {
		final String pageText = driver.findElement(By.tagName("body")).getText();
		assertTrue("User email was not visible on the page.", EMAIL_PATTERN.matcher(pageText).find());
	}

	private void assertUserNameVisible() {
		final Optional<WebElement> explicitNameValue = findFirstVisible(By.xpath(
				"//*[normalize-space()='Información General']/following::*[contains(translate(normalize-space(),"
						+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ',"
						+ "'abcdefghijklmnopqrstuvwxyzáéíóúüñ'),'nombre')][1]/following::*[normalize-space()][1]"),
				SHORT_WAIT);

		if (explicitNameValue.isPresent()) {
			final String candidate = explicitNameValue.get().getText().trim();
			if (!candidate.isEmpty()) {
				return;
			}
		}

		final String pageText = driver.findElement(By.tagName("body")).getText();
		final Pattern possibleFullNamePattern = Pattern.compile("(?m)^[\\p{L}]{2,}(?:\\s+[\\p{L}]{2,}){1,3}$");
		for (final String line : pageText.split("\\R")) {
			final String trimmed = line.trim();
			final String normalized = normalize(trimmed);
			if (trimmed.isEmpty()) {
				continue;
			}
			if (Arrays.asList("informacion general", "business plan", "cambiar plan", "detalles de la cuenta",
					"tus negocios", "seccion legal", "cuenta creada", "estado activo", "idioma seleccionado")
					.contains(normalized)) {
				continue;
			}
			if (possibleFullNamePattern.matcher(trimmed).matches() && !trimmed.contains("@")) {
				return;
			}
		}

		throw new AssertionError("User name was not detected in 'Información General'.");
	}

	private void assertLegalContentVisible(final String heading) {
		final String contentLocator = "//*[self::p or self::li or self::div][string-length(normalize-space()) > 60]";
		final Optional<WebElement> legalContent = findFirstVisible(By.xpath(contentLocator), DEFAULT_WAIT);
		assertTrue("Legal content text was not visible for: " + heading, legalContent.isPresent());
	}

	private String waitForNewWindowOrNavigation(final Set<String> handlesBefore, final String urlBefore) {
		final ExpectedCondition<Boolean> windowOrNavigationCondition = d -> {
			final boolean newWindowOpened = d.getWindowHandles().size() > handlesBefore.size();
			final boolean navigated = !d.getCurrentUrl().equals(urlBefore);
			return newWindowOpened || navigated;
		};
		wait.until(windowOrNavigationCondition);

		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBefore.contains(handle)) {
				return handle;
			}
		}
		return driver.getWindowHandle();
	}

	private void ensureTextOnPage(final String text) {
		assertTrue("Expected legal link text not found: " + text, findVisibleByText(text, DEFAULT_WAIT).isPresent());
	}

	private void safeClick(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
			element.click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private boolean isTextVisible(final String text) {
		return findVisibleByText(text, SHORT_WAIT).isPresent();
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final TimeoutException ignored) {
			// Some web apps remain in interactive state with long polling; continue and rely on element waits.
		}
		try {
			Thread.sleep(400L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final String fileName = String.format(Locale.ROOT, "%02d-%s.png", ++screenshotCounter, checkpointName);
		final Path outputPath = evidenceDir.resolve(fileName);
		final byte[] screenshotData = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(outputPath, screenshotData);
	}

	private void tryReturnToAppWindow() {
		try {
			if (driver != null && appWindowHandle != null) {
				driver.switchTo().window(appWindowHandle);
				waitForUiToLoad();
			}
		} catch (final NoSuchWindowException ignored) {
			// No-op.
		}
	}

	private boolean isHeadlessEnabled() {
		final String configured = System.getenv("SALEADS_HEADLESS");
		return configured == null || !configured.equalsIgnoreCase("false");
	}

	private String renderReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		builder.append(System.lineSeparator());
		builder.append("Step Results").append(System.lineSeparator());
		for (final Map.Entry<String, Boolean> entry : stepReport.entrySet()) {
			final String status = entry.getValue().booleanValue() ? "PASS" : "FAIL";
			builder.append("- ").append(entry.getKey()).append(": ").append(status);
			final String error = stepErrors.get(entry.getKey());
			if (error != null && !error.isBlank()) {
				builder.append(" | ").append(error);
			}
			builder.append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			builder.append(System.lineSeparator());
			builder.append("Final URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
				builder.append("- ").append(legalUrl.getKey()).append(": ").append(legalUrl.getValue())
						.append(System.lineSeparator());
			}
		}
		return builder.toString();
	}

	private void printFinalReport() {
		if (!stepReport.isEmpty()) {
			System.out.println(renderReport());
		}
	}

	private String slugify(final String input) {
		return normalize(input).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String normalize(final String input) {
		final String normalized = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return normalized.toLowerCase(Locale.ROOT).trim();
	}

	private String xPathLiteral(final String value) {
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
				builder.append(",\"'\",");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void execute() throws Exception;
	}
}
