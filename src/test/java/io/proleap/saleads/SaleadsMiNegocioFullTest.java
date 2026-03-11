package io.proleap.saleads;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
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

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(25);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, StepResult> results = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		final boolean headless = Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-popup-blocking");
		options.addArguments("--disable-notifications");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		evidenceDir = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String saleadsUrl = readConfig("saleads.url", "SALEADS_URL", null);
		Assert.assertTrue(
				"Missing URL. Set system property 'saleads.url' or environment variable 'SALEADS_URL'.",
				saleadsUrl != null && !saleadsUrl.isBlank());

		driver.get(saleadsUrl);
		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();

		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		writeFinalReport();
		failIfAnyStepFailed();
	}

	private void stepLoginWithGoogle() throws Exception {
		clickAnyText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		selectGoogleAccountIfSelectorAppears();

		waitForAnyTextVisible(Arrays.asList("Negocio", "Dashboard", "Inicio"), DEFAULT_WAIT);
		assertSidebarVisible();
		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		waitForAnyTextVisible(Arrays.asList("Negocio"), DEFAULT_WAIT);
		clickAnyText("Negocio");
		clickAnyText("Mi Negocio");

		waitForAnyTextVisible(Arrays.asList("Agregar Negocio"), DEFAULT_WAIT);
		waitForAnyTextVisible(Arrays.asList("Administrar Negocios"), DEFAULT_WAIT);
		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickAnyText("Agregar Negocio");
		waitForAnyTextVisible(Arrays.asList("Crear Nuevo Negocio"), DEFAULT_WAIT);
		waitForAnyTextVisible(Arrays.asList("Nombre del Negocio"), DEFAULT_WAIT);
		waitForAnyTextVisible(Arrays.asList("Tienes 2 de 3 negocios"), DEFAULT_WAIT);
		waitForAnyTextVisible(Arrays.asList("Cancelar"), DEFAULT_WAIT);
		waitForAnyTextVisible(Arrays.asList("Crear Negocio"), DEFAULT_WAIT);

		final WebElement businessInput = waitForVisibleInputNearText("Nombre del Negocio");
		businessInput.click();
		businessInput.clear();
		businessInput.sendKeys("Negocio Prueba Automatización");

		takeScreenshot("03_agregar_negocio_modal");
		clickAnyText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(2))) {
			clickAnyText("Mi Negocio");
		}

		clickAnyText("Administrar Negocios");
		waitForAnyTextVisible(Arrays.asList("Información General"), DEFAULT_WAIT);
		waitForAnyTextVisible(Arrays.asList("Detalles de la Cuenta"), DEFAULT_WAIT);
		waitForAnyTextVisible(Arrays.asList("Tus Negocios"), DEFAULT_WAIT);
		waitForAnyTextVisible(Arrays.asList("Sección Legal"), DEFAULT_WAIT);

		takeFullPageScreenshot("04_administrar_negocios_account_page_full");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		waitForAnyTextVisible(Arrays.asList("BUSINESS PLAN"), DEFAULT_WAIT);
		waitForAnyTextVisible(Arrays.asList("Cambiar Plan"), DEFAULT_WAIT);
		assertSectionContainsEmail(section);
		assertSectionContainsPotentialName(section);
	}

	private void stepValidateDetallesCuenta() {
		waitForAnyTextVisible(Arrays.asList("Cuenta creada"), DEFAULT_WAIT);
		waitForAnyTextVisible(Arrays.asList("Estado activo"), DEFAULT_WAIT);
		waitForAnyTextVisible(Arrays.asList("Idioma seleccionado"), DEFAULT_WAIT);
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		waitForAnyTextVisible(Arrays.asList("Agregar Negocio"), DEFAULT_WAIT);
		waitForAnyTextVisible(Arrays.asList("Tienes 2 de 3 negocios"), DEFAULT_WAIT);

		final String visibleText = section.getText().trim();
		Assert.assertTrue("Business list is not visible inside 'Tus Negocios' section.",
				visibleText.split("\\R").length >= 3);
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		handleLegalLink(
				Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
				"Términos y Condiciones",
				"08_terminos_y_condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		handleLegalLink(
				Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
				"Política de Privacidad",
				"09_politica_de_privacidad");
	}

	private void handleLegalLink(final List<String> linkTexts, final String reportKey, final String screenshotPrefix)
			throws Exception {
		final String currentHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		clickAnyText(linkTexts.toArray(new String[0]));
		waitForUiLoad();

		Set<String> handlesAfterClick = new LinkedHashSet<>(handlesBefore);
		try {
			new WebDriverWait(driver, Duration.ofSeconds(6))
					.until((ExpectedCondition<Boolean>) drv -> drv.getWindowHandles().size() > handlesBefore.size());
			handlesAfterClick = driver.getWindowHandles();
		} catch (final TimeoutException ignored) {
			handlesAfterClick = driver.getWindowHandles();
		}

		String legalHandle = currentHandle;
		if (handlesAfterClick.size() > handlesBefore.size()) {
			legalHandle = handlesAfterClick.stream().filter(h -> !handlesBefore.contains(h)).findFirst().orElse(currentHandle);
			driver.switchTo().window(legalHandle);
			waitForUiLoad();
		}

		waitForAnyTextVisible(linkTexts, DEFAULT_WAIT);
		assertLegalContentVisible();
		takeScreenshot(screenshotPrefix);
		legalUrls.put(reportKey, driver.getCurrentUrl());

		if (!legalHandle.equals(currentHandle)) {
			driver.close();
			driver.switchTo().window(currentHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String name, final CheckedRunnable action) {
		final StepResult result = new StepResult();
		result.name = name;

		try {
			action.run();
			result.pass = true;
			result.details = "PASS";
		} catch (final Exception ex) {
			result.pass = false;
			result.details = ex.getMessage();
			safeFailureScreenshot(name);
		}

		results.put(name, result);
	}

	private void failIfAnyStepFailed() {
		final List<StepResult> failures = results.values().stream().filter(r -> !r.pass).collect(Collectors.toList());
		if (!failures.isEmpty()) {
			final String summary = failures.stream().map(f -> f.name + ": " + f.details).collect(Collectors.joining("\n - "));
			Assert.fail("One or more validations failed:\n - " + summary);
		}
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("Generated at: " + LocalDateTime.now());
		lines.add("");
		lines.add("RESULTS:");
		lines.add("Login: " + passFail("Login"));
		lines.add("Mi Negocio menu: " + passFail("Mi Negocio menu"));
		lines.add("Agregar Negocio modal: " + passFail("Agregar Negocio modal"));
		lines.add("Administrar Negocios view: " + passFail("Administrar Negocios view"));
		lines.add("Información General: " + passFail("Información General"));
		lines.add("Detalles de la Cuenta: " + passFail("Detalles de la Cuenta"));
		lines.add("Tus Negocios: " + passFail("Tus Negocios"));
		lines.add("Términos y Condiciones: " + passFail("Términos y Condiciones"));
		lines.add("Política de Privacidad: " + passFail("Política de Privacidad"));
		lines.add("");
		lines.add("LEGAL URLS:");
		lines.add("Términos y Condiciones URL: " + legalUrls.getOrDefault("Términos y Condiciones", "N/A"));
		lines.add("Política de Privacidad URL: " + legalUrls.getOrDefault("Política de Privacidad", "N/A"));
		lines.add("");
		lines.add("EVIDENCE DIR: " + evidenceDir.toAbsolutePath());

		final Path report = evidenceDir.resolve("final-report.txt");
		Files.write(report, lines);
	}

	private String passFail(final String step) {
		final StepResult result = results.get(step);
		return result != null && result.pass ? "PASS" : "FAIL";
	}

	private void selectGoogleAccountIfSelectorAppears() throws Exception {
		final Set<String> handles = driver.getWindowHandles();
		String googleHandle = null;

		for (final String handle : handles) {
			driver.switchTo().window(handle);
			final String currentUrl = driver.getCurrentUrl().toLowerCase(Locale.ROOT);
			if (currentUrl.contains("accounts.google.com")) {
				googleHandle = handle;
				break;
			}
		}

		if (googleHandle == null) {
			driver.switchTo().window(appWindowHandle);
			return;
		}

		if (isTextVisible(GOOGLE_ACCOUNT_EMAIL, Duration.ofSeconds(8))) {
			clickAnyText(GOOGLE_ACCOUNT_EMAIL);
		}

		driver.switchTo().window(appWindowHandle);
		waitForUiLoad();
	}

	private void assertSidebarVisible() {
		final List<WebElement> sidebars = driver.findElements(By.cssSelector("aside, nav"));
		final boolean anyVisibleSidebar = sidebars.stream().anyMatch(WebElement::isDisplayed);
		Assert.assertTrue("Left sidebar navigation is not visible.", anyVisibleSidebar);
	}

	private WebElement waitForVisibleInputNearText(final String labelText) {
		final String labelXpath = "//*[contains(normalize-space(.)," + xpathLiteral(labelText) + ")]";
		final String inputXpath = "(" + labelXpath + "/following::input[not(@type='hidden')])[1]";
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(inputXpath)));
	}

	private WebElement findSectionByHeading(final String headingText) {
		final String xpath = "(//*[self::h1 or self::h2 or self::h3 or self::h4 or self::h5 or self::p or self::span]"
				+ "[contains(normalize-space(.)," + xpathLiteral(headingText) + ")])[1]";
		final WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
		final List<WebElement> candidates = heading
				.findElements(By.xpath("ancestor::*[self::section or self::article or self::div][position()<=4]"));
		return candidates.isEmpty() ? heading : candidates.get(0);
	}

	private void assertSectionContainsEmail(final WebElement section) {
		final List<String> texts = section.findElements(By.xpath(".//*[self::p or self::span or self::div or self::h4]")).stream()
				.filter(WebElement::isDisplayed)
				.map(WebElement::getText)
				.map(String::trim)
				.filter(t -> !t.isEmpty())
				.collect(Collectors.toList());
		final boolean foundEmail = texts.stream().anyMatch(t -> EMAIL_PATTERN.matcher(t).matches());
		Assert.assertTrue("User email is not visible in 'Información General'.", foundEmail);
	}

	private void assertSectionContainsPotentialName(final WebElement section) {
		final List<String> exclusions = Arrays.asList("Información General", "BUSINESS PLAN", "Cambiar Plan");
		final List<String> texts = section.findElements(By.xpath(".//*[self::p or self::span or self::div or self::h4]")).stream()
				.filter(WebElement::isDisplayed)
				.map(WebElement::getText)
				.map(String::trim)
				.filter(t -> !t.isEmpty())
				.collect(Collectors.toList());
		final boolean foundName = texts.stream()
				.anyMatch(t -> !t.contains("@") && t.length() >= 3 && exclusions.stream().noneMatch(t::equalsIgnoreCase));
		Assert.assertTrue("User name is not visible in 'Información General'.", foundName);
	}

	private void assertLegalContentVisible() {
		final List<WebElement> visibleParagraphs = driver
				.findElements(By.xpath("//p[string-length(normalize-space(.)) > 30] | //div[string-length(normalize-space(.)) > 120]"))
				.stream()
				.filter(WebElement::isDisplayed)
				.collect(Collectors.toList());
		Assert.assertTrue("Legal content text is not visible.", !visibleParagraphs.isEmpty());
	}

	private void clickAnyText(final String... visibleTexts) {
		Exception lastError = null;
		for (final String text : visibleTexts) {
			try {
				clickByVisibleText(text);
				return;
			} catch (final Exception ex) {
				lastError = ex;
			}
		}
		throw new RuntimeException("Could not click any of these texts: " + Arrays.toString(visibleTexts), lastError);
	}

	private void clickByVisibleText(final String text) throws Exception {
		final String clickableXpath = "("
				+ "//button[contains(normalize-space(.)," + xpathLiteral(text) + ")]"
				+ " | //a[contains(normalize-space(.)," + xpathLiteral(text) + ")]"
				+ " | //*[@role='button' and contains(normalize-space(.)," + xpathLiteral(text) + ")]"
				+ " | //*[contains(normalize-space(.)," + xpathLiteral(text) + ")]/ancestor::*[self::button or self::a or @role='button'][1]"
				+ " | //*[contains(normalize-space(.)," + xpathLiteral(text) + ")]" + ")[1]";

		final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(clickableXpath)));
		wait.until(ExpectedConditions.elementToBeClickable(element));
		scrollIntoView(element);
		try {
			element.click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiLoad();
	}

	private void waitForAnyTextVisible(final List<String> texts, final Duration timeout) {
		Exception lastException = null;
		for (final String text : texts) {
			try {
				new WebDriverWait(driver, timeout)
						.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.),"
								+ xpathLiteral(text) + ")]")));
				return;
			} catch (final Exception ex) {
				lastException = ex;
			}
		}
		throw new RuntimeException("None of the expected texts became visible: " + texts, lastException);
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout)
					.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.),"
							+ xpathLiteral(text) + ")]")));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void takeScreenshot(final String name) {
		final Path screenshot = evidenceDir.resolve(sanitizeFileName(name) + ".png");
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		try {
			Files.copy(source.toPath(), screenshot, StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException ex) {
			throw new RuntimeException("Failed to save screenshot: " + screenshot, ex);
		}
	}

	private void takeFullPageScreenshot(final String name) {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final Long scrollHeight = (Long) ((JavascriptExecutor) driver)
					.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			final int fullHeight = Math.max(originalSize.getHeight(), Math.toIntExact(Math.min(scrollHeight + 200L, 4000L)));
			driver.manage().window().setSize(new Dimension(originalSize.getWidth(), fullHeight));
			waitForUiLoad();
			takeScreenshot(name);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiLoad();
		}
	}

	private void safeFailureScreenshot(final String stepName) {
		try {
			takeScreenshot("failure_" + sanitizeFileName(stepName));
		} catch (final Exception ignored) {
			// best-effort evidence capture
		}
	}

	private void waitForUiLoad() {
		wait.until(drv -> "complete".equals(((JavascriptExecutor) drv).executeScript("return document.readyState")));
		try {
			Thread.sleep(500L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
	}

	private String readConfig(final String systemProperty, final String envVar, final String defaultValue) {
		final String fromSystemProperty = System.getProperty(systemProperty);
		if (fromSystemProperty != null && !fromSystemProperty.isBlank()) {
			return fromSystemProperty;
		}
		final String fromEnv = System.getenv(envVar);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}
		return defaultValue;
	}

	private String sanitizeFileName(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder concat = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			concat.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				concat.append(",\"'\",");
			}
		}
		concat.append(")");
		return concat.toString();
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static class StepResult {
		private String name;
		private boolean pass;
		private String details;
	}
}
