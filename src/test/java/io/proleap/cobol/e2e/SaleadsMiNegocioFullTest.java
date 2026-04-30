package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
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

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> errors = new ArrayList<>();

	private Path evidenceDir;
	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue(
				"E2E test disabled. Run with -Dsaleads.e2e.enabled=true when browser execution is intended.",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1440,1100");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-notifications");

		if (Boolean.parseBoolean(System.getProperty("saleads.headless", "false"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(Long.getLong("saleads.timeout.seconds", 30L)));

		final String timestamp = LocalDateTime.now().format(TS_FORMAT);
		evidenceDir = Path.of("target", "saleads-evidence", TEST_NAME + "_" + timestamp).toAbsolutePath();
		Files.createDirectories(evidenceDir);

		final String baseUrl = System.getProperty("saleads.baseUrl", "").trim();
		if (!baseUrl.isEmpty()) {
			driver.get(baseUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() throws IOException {
		writeReportFile();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() throws IOException {
		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_AGREGAR_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(REPORT_DETALLES, this::stepValidateDetallesCuenta);
		runStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(REPORT_TERMINOS, () -> stepValidateLegalLink("Términos y Condiciones", "terminos_y_condiciones"));
		runStep(REPORT_PRIVACIDAD, () -> stepValidateLegalLink("Política de Privacidad", "politica_de_privacidad"));

		final boolean allPassed = stepStatus.values().stream().allMatch(Boolean::booleanValue);
		assertTrue("At least one validation failed. Check report: " + evidenceDir.resolve("final_report.txt"), allPassed);
	}

	private void stepLoginWithGoogle() throws IOException {
		clickFirstVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Acceder con Google", "Google"));
		waitForUiToLoad();
		selectGoogleAccountIfPresent();

		waitForAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Inicio"), 60);
		assertTrue("Left sidebar navigation is not visible after login", isSidebarVisible());
		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		openMiNegocioMenu();
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		assertInputForLabelVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		final WebElement nombreInput = findNombreNegocioInput();
		nombreInput.click();
		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatización");
		takeScreenshot("03_agregar_negocio_modal");
		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		openMiNegocioMenu();
		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");

		driver.manage().window().setSize(new Dimension(1920, 2200));
		waitForUiToLoad();
		takeScreenshot("04_administrar_negocios_view");
		driver.manage().window().setSize(new Dimension(1440, 1100));
		waitForUiToLoad();
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("Información General");
		assertTrue("User email not visible", isEmailVisible());
		assertTrue("User name not visible", isLikelyUserNameVisible());
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTrue("Business list is not visible", isBusinessListVisible());
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(final String legalText, final String screenshotPrefix) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = driver.getWindowHandles();

		clickByVisibleText(legalText);

		String legalWindow = appWindow;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15))
					.until(d -> d.getWindowHandles().size() > windowsBefore.size() || !d.getWindowHandle().equals(appWindow));
		} catch (final TimeoutException ignored) {
			// Same tab navigation is accepted by this test flow.
		}

		final Set<String> windowsAfter = driver.getWindowHandles();
		for (final String window : windowsAfter) {
			if (!windowsBefore.contains(window)) {
				legalWindow = window;
				break;
			}
		}

		driver.switchTo().window(legalWindow);
		waitForUiToLoad();
		assertTextVisible(legalText);
		assertTrue("Legal page does not contain enough content for " + legalText, pageTextLength() > 200);
		legalUrls.put(legalText, driver.getCurrentUrl());
		takeScreenshot("0" + (REPORT_TERMINOS.equals(legalText) ? "8" : "9") + "_" + screenshotPrefix);

		if (!legalWindow.equals(appWindow)) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void openMiNegocioMenu() {
		clickIfVisible("Negocio");
		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();
	}

	private void selectGoogleAccountIfPresent() {
		final List<String> loginWindows = new ArrayList<>(driver.getWindowHandles());
		for (final String win : loginWindows) {
			driver.switchTo().window(win);
			if (containsTextCaseInsensitive(driver.getCurrentUrl(), "accounts.google.com")
					|| isElementPresent(By.xpath("//*[contains(@data-email,'" + GOOGLE_EMAIL + "')]"))
					|| isElementPresent(byVisibleTextContains(GOOGLE_EMAIL))) {
				clickIfVisible(GOOGLE_EMAIL);
				waitForUiToLoad();
				break;
			}
		}
	}

	private void clickByVisibleText(final String text) {
		final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(byVisibleTextExact(text)));
		scrollIntoView(element);
		element.click();
		waitForUiToLoad();
	}

	private void clickFirstVisibleText(final List<String> textCandidates) {
		for (final String text : textCandidates) {
			final List<WebElement> elements = driver.findElements(byVisibleTextContains(text));
			for (final WebElement element : elements) {
				if (element.isDisplayed() && element.isEnabled()) {
					scrollIntoView(element);
					element.click();
					waitForUiToLoad();
					return;
				}
			}
		}

		throw new NoSuchElementException("None of the candidate texts were clickable: " + textCandidates);
	}

	private void clickIfVisible(final String text) {
		final List<WebElement> elements = driver.findElements(byVisibleTextContains(text));
		for (final WebElement element : elements) {
			if (element.isDisplayed() && element.isEnabled()) {
				scrollIntoView(element);
				element.click();
				waitForUiToLoad();
				return;
			}
		}
	}

	private void assertTextVisible(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleTextContains(text)));
	}

	private void assertInputForLabelVisible(final String labelText) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//*[normalize-space()=" + xpathLiteral(labelText) + "]/following::input[1] | //input[@placeholder="
						+ xpathLiteral(labelText) + "] | //input[@aria-label=" + xpathLiteral(labelText) + "]")));
	}

	private WebElement findNombreNegocioInput() {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//*[normalize-space()='Nombre del Negocio']/following::input[1] | //input[@placeholder='Nombre del Negocio'] | //input[@aria-label='Nombre del Negocio']")));
	}

	private void waitForAnyVisibleText(final List<String> candidates, final int timeoutSeconds) {
		final WebDriverWait localWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		localWait.until(anyVisibleText(candidates));
	}

	private ExpectedCondition<Boolean> anyVisibleText(final List<String> candidates) {
		return d -> candidates.stream().anyMatch(candidate -> !d.findElements(byVisibleTextContains(candidate)).isEmpty());
	}

	private boolean isSidebarVisible() {
		final List<By> sidebarCandidates = Arrays.asList(By.tagName("aside"), By.cssSelector("nav"),
				By.cssSelector("[class*='sidebar']"), By.cssSelector("[id*='sidebar']"));
		for (final By by : sidebarCandidates) {
			final List<WebElement> elements = driver.findElements(by);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isEmailVisible() {
		final Pattern emailPattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
		final String pageText = driver.findElement(By.tagName("body")).getText();
		return emailPattern.matcher(pageText).find();
	}

	private boolean isLikelyUserNameVisible() {
		if (isElementPresent(byVisibleTextContains("juan"))) {
			return true;
		}
		if (isElementPresent(By.xpath(
				"//*[contains(translate(normalize-space(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚ', 'abcdefghijklmnopqrstuvwxyzáéíóú'), 'nombre')]/following::*[normalize-space()][1]"))) {
			return true;
		}
		return false;
	}

	private boolean isBusinessListVisible() {
		if (isElementPresent(By.xpath("//li[normalize-space()] | //tr[td]"))) {
			return true;
		}
		return isElementPresent(By.xpath(
				"//*[contains(@class,'business') and normalize-space()] | //*[@data-testid='business-item' and normalize-space()]"));
	}

	private int pageTextLength() {
		return driver.findElement(By.tagName("body")).getText().trim().length();
	}

	private boolean isElementPresent(final By by) {
		try {
			return !driver.findElements(by).isEmpty();
		} catch (final Exception e) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"))));
		} catch (final TimeoutException ignored) {
			// SPA pages may not transition full readyState after each interaction.
		}

		try {
			Thread.sleep(700);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void takeScreenshot(final String name) throws IOException {
		final Path screenshotPath = evidenceDir.resolve(name + ".png");
		final Path tempScreenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(tempScreenshot, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void runStep(final String stepName, final StepAction action) throws IOException {
		try {
			action.run();
			stepStatus.put(stepName, Boolean.TRUE);
		} catch (final Exception e) {
			stepStatus.put(stepName, Boolean.FALSE);
			errors.add(stepName + " -> " + e.getMessage());
			takeScreenshotSafe("failed_" + sanitize(stepName));
		}
	}

	private void takeScreenshotSafe(final String name) {
		try {
			takeScreenshot(name);
		} catch (final Exception ignored) {
			// Best effort evidence capture.
		}
	}

	private void writeReportFile() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("Test: ").append(TEST_NAME).append('\n');
		reportBuilder.append("Evidence directory: ").append(evidenceDir).append('\n').append('\n');
		reportBuilder.append("Final Report (PASS/FAIL)\n");
		reportBuilder.append("--------------------------------\n");

		for (final String field : Arrays.asList(REPORT_LOGIN, REPORT_MI_NEGOCIO_MENU, REPORT_AGREGAR_MODAL, REPORT_ADMIN_VIEW,
				REPORT_INFO_GENERAL, REPORT_DETALLES, REPORT_TUS_NEGOCIOS, REPORT_TERMINOS, REPORT_PRIVACIDAD)) {
			final boolean passed = Boolean.TRUE.equals(stepStatus.get(field));
			reportBuilder.append(field).append(": ").append(passed ? "PASS" : "FAIL").append('\n');
		}

		reportBuilder.append('\n').append("Legal URLs\n");
		reportBuilder.append("--------------------------------\n");
		reportBuilder.append("Términos y Condiciones URL: ")
				.append(legalUrls.getOrDefault("Términos y Condiciones", "N/A")).append('\n');
		reportBuilder.append("Política de Privacidad URL: ")
				.append(legalUrls.getOrDefault("Política de Privacidad", "N/A")).append('\n');

		if (!errors.isEmpty()) {
			reportBuilder.append('\n').append("Errors\n");
			reportBuilder.append("--------------------------------\n");
			for (final String error : errors) {
				reportBuilder.append("- ").append(error).append('\n');
			}
		}

		final Path reportFile = evidenceDir.resolve("final_report.txt");
		Files.writeString(reportFile, reportBuilder.toString());
	}

	private static By byVisibleTextExact(final String text) {
		return By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]");
	}

	private static By byVisibleTextContains(final String text) {
		return By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]");
	}

	private static String sanitize(final String text) {
		return text.toLowerCase().replace(' ', '_').replace('/', '_');
	}

	private static boolean containsTextCaseInsensitive(final String source, final String value) {
		return source != null && value != null && source.toLowerCase().contains(value.toLowerCase());
	}

	private static String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String chr = String.valueOf(chars[i]);
			if ("'".equals(chr)) {
				sb.append("\"'\"");
			} else {
				sb.append("'").append(chr).append("'");
			}
			if (i < chars.length - 1) {
				sb.append(",");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
