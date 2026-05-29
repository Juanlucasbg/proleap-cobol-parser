package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioFullTest {

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> failures = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String accountEmail;
	private String expectedUserName;
	private String baseUrl;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue("SaleADS E2E test is disabled. Run with -Dsaleads.e2e.enabled=true", enabled);

		accountEmail = System.getProperty("saleads.account.email", "juanlucasbarbiergarzon@gmail.com");
		expectedUserName = System.getProperty("saleads.expected.user.name", "").trim();
		baseUrl = System.getProperty("saleads.base.url", "").trim();

		evidenceDir = Path.of(System.getProperty("saleads.evidence.dir", "target/saleads-evidence"));
		Files.createDirectories(evidenceDir);

		driver = buildDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(longProperty("saleads.timeout.seconds", 30L)));

		if (!baseUrl.isBlank() && isBlankPage()) {
			driver.get(baseUrl);
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() throws IOException {
		writeReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminos);
		runStep("Política de Privacidad", this::stepValidatePrivacidad);

		final String summary = buildSummary();
		assertTrue("One or more SaleADS validations failed:\n" + summary, failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		if (!isMainApplicationVisible()) {
			clickFirstVisibleByText(List.of("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google"));

			if (isTextVisible(accountEmail)) {
				clickByText(accountEmail);
			}
		}

		wait.until(driver -> isMainApplicationVisible());
		assertTrue("Main application interface should be visible after login.", isMainApplicationVisible());
		assertTrue("Left sidebar navigation should be visible.", isSidebarVisible());
		captureScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		expandMiNegocioMenuIfNeeded();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		assertTrue("Field 'Nombre del Negocio' should be visible.", isNombreNegocioFieldVisible());
		assertContainsVisibleText("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		captureScreenshot("03_agregar_negocio_modal");

		final WebElement nombreInput = firstVisibleElement(
				List.of(By.xpath("//input[@placeholder=" + xpathLiteral("Nombre del Negocio") + "]"),
						By.xpath("//input[@name='businessName']"), By.xpath("//input[@type='text']")));
		nombreInput.clear();
		nombreInput.sendKeys("Negocio Prueba Automatización");
		clickByText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byExactText("Crear Nuevo Negocio")));
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioMenuIfNeeded();
		clickByText("Administrar Negocios");

		assertContainsVisibleText("Información General");
		assertContainsVisibleText("Detalles de la Cuenta");
		assertContainsVisibleText("Tus Negocios");
		assertContainsVisibleText("Sección Legal");
		captureScreenshot("04_administrar_negocios_account_page");
	}

	private void stepValidateInformacionGeneral() {
		assertContainsVisibleText("Información General");
		assertTrue("User name should be visible.",
				(!expectedUserName.isBlank() && (isTextVisible(expectedUserName) || isContainsTextVisible(expectedUserName)))
						|| isAnyVisibleText("Nombre", "Usuario", "Perfil"));
		assertTrue("User email should be visible.", isEmailVisible());
		assertContainsVisibleText("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertContainsVisibleText("Cuenta creada");
		assertContainsVisibleText("Estado activo");
		assertContainsVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertContainsVisibleText("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertContainsVisibleText("Tienes 2 de 3 negocios");
		assertTrue("Business list should be visible.", isBusinessListVisible());
	}

	private void stepValidateTerminos() throws IOException {
		validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08_terminos_y_condiciones");
	}

	private void stepValidatePrivacidad() throws IOException {
		validateLegalLink("Política de Privacidad", "Política de Privacidad", "09_politica_de_privacidad");
	}

	private void validateLegalLink(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String currentUrl = driver.getCurrentUrl();

		clickByText(linkText);

		wait.until(driver -> driver.getWindowHandles().size() > handlesBeforeClick.size()
				|| !driver.getCurrentUrl().equals(currentUrl));
		waitForUiLoad();

		String legalHandle = driver.getWindowHandle();
		final Set<String> handlesAfterClick = driver.getWindowHandles();
		if (handlesAfterClick.size() > handlesBeforeClick.size()) {
			final Set<String> newHandles = handlesAfterClick.stream().filter(handle -> !handlesBeforeClick.contains(handle))
					.collect(Collectors.toSet());
			legalHandle = newHandles.iterator().next();
			driver.switchTo().window(legalHandle);
			waitForUiLoad();
		}

		assertContainsVisibleText(headingText);
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal content text should be visible.", bodyText != null && bodyText.trim().length() > 120);
		captureScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (!legalHandle.equals(appHandle) && driver.getWindowHandles().contains(legalHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String reportField, final CheckedRunnable runnable) {
		try {
			runnable.run();
			report.put(reportField, true);
		} catch (final Throwable throwable) {
			report.put(reportField, false);
			failures.put(reportField, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
		}
	}

	private void expandMiNegocioMenuIfNeeded() {
		if (!isTextVisible("Mi Negocio")) {
			clickByText("Negocio");
		}

		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			clickByText("Mi Negocio");
			waitForUiLoad();
		}
	}

	private void clickFirstVisibleByText(final List<String> textOptions) {
		for (final String option : textOptions) {
			if (isTextVisible(option) || isContainsTextVisible(option)) {
				clickByText(option);
				return;
			}
		}

		throw new AssertionError("None of the expected text options were visible: " + textOptions);
	}

	private void clickByText(final String text) {
		final WebElement element = firstVisibleElement(clickableTextLocators(text));
		scrollIntoView(element);

		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception exception) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiLoad();
	}

	private void assertTextVisible(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(byExactText(text)));
	}

	private void assertContainsVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(byContainsText(text)));
	}

	private boolean isTextVisible(final String text) {
		return isVisible(byExactText(text));
	}

	private boolean isContainsTextVisible(final String text) {
		return isVisible(byContainsText(text));
	}

	private boolean isVisible(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			try {
				if (element.isDisplayed()) {
					return true;
				}
			} catch (final Exception exception) {
				// ignore stale/intermittent element checks
			}
		}

		return false;
	}

	private boolean isAnyVisibleText(final String... values) {
		for (final String value : values) {
			if (isTextVisible(value) || isContainsTextVisible(value)) {
				return true;
			}
		}

		return false;
	}

	private boolean isNombreNegocioFieldVisible() {
		return isVisible(By.xpath("//label[normalize-space()=" + xpathLiteral("Nombre del Negocio") + "]"))
				|| isVisible(By.xpath("//input[@placeholder=" + xpathLiteral("Nombre del Negocio") + "]"))
				|| isVisible(By.xpath("//input[@name='businessName']"));
	}

	private boolean isEmailVisible() {
		if (!accountEmail.isBlank() && (isTextVisible(accountEmail) || isContainsTextVisible(accountEmail))) {
			return true;
		}

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		return EMAIL_PATTERN.matcher(bodyText).find();
	}

	private boolean isBusinessListVisible() {
		return isVisible(By.xpath(
				"//*[contains(normalize-space(), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]//*[self::li or self::tr or self::article or @role='row']"))
				|| isVisible(By.xpath("//table")) || isVisible(By.xpath("//ul/li")) || isVisible(By.xpath("//ol/li"));
	}

	private boolean isMainApplicationVisible() {
		return isSidebarVisible() || isTextVisible("Dashboard") || isTextVisible("Mi Negocio");
	}

	private boolean isSidebarVisible() {
		return isVisible(By.xpath("//aside")) || isVisible(By.xpath("//nav")) || isTextVisible("Negocio");
	}

	private boolean isBlankPage() {
		try {
			final String currentUrl = driver.getCurrentUrl();
			return currentUrl == null || currentUrl.isBlank() || "data:,".equals(currentUrl);
		} catch (final Exception exception) {
			return true;
		}
	}

	private void waitForUiLoad() {
		wait.until(driver -> {
			final Object readyState = ((JavascriptExecutor) driver).executeScript("return document.readyState");
			return "complete".equals(readyState) || "interactive".equals(readyState);
		});

		try {
			Thread.sleep(longProperty("saleads.ui.settle.ms", 500L));
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private WebElement firstVisibleElement(final List<By> locators) {
		for (final By locator : locators) {
			try {
				new WebDriverWait(driver, Duration.ofSeconds(3))
						.until(ExpectedConditions.visibilityOfElementLocated(locator));
				final List<WebElement> elements = driver.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			} catch (final TimeoutException timeoutException) {
				// try next locator
			}
		}

		throw new AssertionError("Could not find a visible element with locators: " + locators);
	}

	private List<By> clickableTextLocators(final String text) {
		final String literal = xpathLiteral(text);
		final List<By> locators = new ArrayList<>();
		locators.add(By.xpath("//button[normalize-space()=" + literal + "]"));
		locators.add(By.xpath("//a[normalize-space()=" + literal + "]"));
		locators.add(By.xpath("//*[@role='button' and normalize-space()=" + literal + "]"));
		locators.add(By.xpath("//*[normalize-space()=" + literal
				+ "]/ancestor::*[self::button or self::a or @role='button'][1]"));
		locators.add(By.xpath("//*[normalize-space()=" + literal + "]"));
		locators.add(By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
		return locators;
	}

	private By byExactText(final String text) {
		return By.xpath("//*[normalize-space()=" + xpathLiteral(text) + "]");
	}

	private By byContainsText(final String text) {
		return By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]");
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String fileName = checkpointName + "_" + Instant.now().toEpochMilli() + ".png";
		final Path targetPath = evidenceDir.resolve(fileName);
		Files.copy(screenshotFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final Path reportPath = evidenceDir.resolve("saleads_mi_negocio_full_test_report.txt");
		Files.writeString(reportPath, buildSummary(), StandardCharsets.UTF_8);
	}

	private String buildSummary() {
		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		builder.append("Generated at: ").append(Instant.now()).append(System.lineSeparator()).append(System.lineSeparator());
		builder.append("Validation status:").append(System.lineSeparator());

		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL")
					.append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			builder.append(System.lineSeparator()).append("Captured legal URLs:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue())
						.append(System.lineSeparator());
			}
		}

		if (!failures.isEmpty()) {
			builder.append(System.lineSeparator()).append("Failures:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : failures.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue())
						.append(System.lineSeparator());
			}
		}

		return builder.toString();
	}

	private WebDriver buildDriver() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-notifications");

		if (Boolean.parseBoolean(System.getProperty("saleads.headless", "false"))) {
			options.addArguments("--headless=new");
			options.addArguments("--window-size=1920,1080");
		} else {
			options.addArguments("--start-maximized");
		}

		final String debuggerAddress = System.getProperty("saleads.chrome.debugger.address", "").trim();
		if (!debuggerAddress.isBlank()) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
			return new ChromeDriver(options);
		}

		WebDriverManager.chromedriver().setup();
		return new ChromeDriver(options);
	}

	private long longProperty(final String key, final long defaultValue) {
		final String value = System.getProperty(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}

		try {
			return Long.parseLong(value);
		} catch (final NumberFormatException numberFormatException) {
			return defaultValue;
		}
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(",\"'\",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
