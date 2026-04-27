package io.saleads.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
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

/**
 * End-to-end test for the SaleADS "Mi Negocio" workflow.
 *
 * Run with:
 * mvn -Dtest=SaleadsMiNegocioFullTest
 *     -Dsaleads.run.e2e=true
 *     -Dsaleads.url=<login_page_url>
 *     test
 */
public class SaleadsMiNegocioFullTest {

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private static final List<String> REPORT_ORDER = Arrays.asList(
			LOGIN,
			MI_NEGOCIO_MENU,
			AGREGAR_NEGOCIO_MODAL,
			ADMINISTRAR_NEGOCIOS_VIEW,
			INFORMACION_GENERAL,
			DETALLES_CUENTA,
			TUS_NEGOCIOS,
			TERMINOS,
			PRIVACIDAD
	);

	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
	);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotsDir;
	private final Map<String, Boolean> results = new LinkedHashMap<>();
	private final Map<String, String> details = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue(
				"Skipping E2E test. Enable with -Dsaleads.run.e2e=true",
				Boolean.parseBoolean(getConfig("saleads.run.e2e", "SALEADS_RUN_E2E", "false"))
		);

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(getConfig("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		screenshotsDir = Path.of("target", "saleads-e2e-screenshots", timestamp);
		Files.createDirectories(screenshotsDir);

		final String configuredUrl = getConfig("saleads.url", "SALEADS_URL", "");
		if (!configuredUrl.isEmpty()) {
			driver.get(configuredUrl);
		}
	}

	@After
	public void tearDown() {
		try {
			if (screenshotsDir != null || !results.isEmpty()) {
				printFinalReport();
			}
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runSection(LOGIN, this::stepLoginWithGoogle);
		runSection(MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runSection(AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runSection(ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios);
		runSection(INFORMACION_GENERAL, this::stepValidateInformacionGeneral);
		runSection(DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runSection(TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runSection(TERMINOS, () -> stepValidateLegalLink(
				"T\u00e9rminos y Condiciones",
				"T\u00e9rminos y Condiciones",
				"05-terminos-y-condiciones"
		));
		runSection(PRIVACIDAD, () -> stepValidateLegalLink(
				"Pol\u00edtica de Privacidad",
				"Pol\u00edtica de Privacidad",
				"06-politica-de-privacidad"
		));

		final List<String> failed = new ArrayList<>();
		for (final String name : REPORT_ORDER) {
			if (!Boolean.TRUE.equals(results.get(name))) {
				failed.add(name + " -> " + details.getOrDefault(name, "no details"));
			}
		}
		Assert.assertTrue("Failed sections:\n - " + String.join("\n - ", failed), failed.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		waitForPageVisible();
		clickByAnyVisibleText("Sign in with Google", "Continuar con Google", "Iniciar sesi\u00f3n con Google");
		waitForUiSettled();
		selectGoogleAccountIfShown("juanlucasbarbiergarzon@gmail.com");

		ensureLeftSidebarVisible();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		ensureLeftSidebarVisible();
		clickByAnyVisibleText("Mi Negocio");
		waitForUiSettled();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByAnyVisibleText("Agregar Negocio");
		waitForUiSettled();

		assertVisibleText("Crear Nuevo Negocio");
		assertFieldByLabelExists("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal");

		final WebElement nameInput = findInputByLabel("Nombre del Negocio");
		nameInput.click();
		waitForUiSettled();
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatizacion");
		clickByAnyVisibleText("Cancelar");
		waitForUiSettled();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioIfNeeded();
		clickByAnyVisibleText("Administrar Negocios");
		waitForUiSettled();

		assertVisibleText("Informaci\u00f3n General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Secci\u00f3n Legal");
		takeFullPageScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement pageRoot = pageRoot();
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
		assertAnyEmailVisible(pageRoot);
		assertUserNameLooksVisible(pageRoot);
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(
			final String linkText,
			final String expectedHeading,
			final String screenshotName
	) throws IOException {
		final String appTab = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickByAnyVisibleText(linkText);
		waitForUiSettled();

		String targetHandle = appTab;
		try {
			wait.until(driver -> driver.getWindowHandles().size() > beforeHandles.size());
			final Set<String> afterHandles = new LinkedHashSet<>(driver.getWindowHandles());
			afterHandles.removeAll(beforeHandles);
			if (!afterHandles.isEmpty()) {
				targetHandle = afterHandles.iterator().next();
				driver.switchTo().window(targetHandle);
				waitForUiSettled();
			}
		} catch (final TimeoutException ignored) {
			// Same-tab navigation is acceptable.
		}

		assertVisibleText(expectedHeading);
		assertAnyLegalContentVisible();
		takeScreenshot(screenshotName);
		details.put(screenshotName + ".url", driver.getCurrentUrl());

		if (!targetHandle.equals(appTab)) {
			driver.close();
			driver.switchTo().window(appTab);
			waitForUiSettled();
		} else {
			driver.navigate().back();
			waitForUiSettled();
		}
	}

	private void runSection(final String sectionName, final CheckedRunnable action) {
		try {
			action.run();
			results.put(sectionName, true);
			details.put(sectionName, "PASS");
		} catch (final Throwable t) {
			results.put(sectionName, false);
			details.put(sectionName, t.getClass().getSimpleName() + ": " + safeMessage(t));
			try {
				takeScreenshot("failure-" + sanitize(sectionName));
			} catch (final IOException ignored) {
				// Best effort in failures.
			}
		}
	}

	private void waitForPageVisible() {
		wait.until(driver -> ((JavascriptExecutor) driver)
				.executeScript("return document.readyState").equals("complete"));
	}

	private void waitForUiSettled() {
		waitForPageVisible();
		try {
			Thread.sleep(600L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void clickByAnyVisibleText(final String... texts) {
		Throwable last = null;
		for (final String text : texts) {
			try {
				clickByAnyVisibleText(text);
				return;
			} catch (final Throwable t) {
				last = t;
			}
		}
		throw new AssertionError("Could not click by visible text: " + Arrays.toString(texts), last);
	}

	private void clickByAnyVisibleText(final String text) {
		final String textLiteral = xpathLiteral(text);
		final List<By> locators = Arrays.asList(
				By.xpath("//button[normalize-space(.)=" + textLiteral + "]"),
				By.xpath("//a[normalize-space(.)=" + textLiteral + "]"),
				By.xpath("//*[@role='button' and normalize-space(.)=" + textLiteral + "]"),
				By.xpath("//*[normalize-space(.)=" + textLiteral + "]"),
				By.xpath("//*[contains(normalize-space(.)," + textLiteral + ")]")
		);

		WebElement target = null;
		Throwable last = null;
		for (final By locator : locators) {
			try {
				target = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				if (target.isDisplayed()) {
					break;
				}
			} catch (final Throwable t) {
				last = t;
			}
		}

		if (target == null) {
			throw new AssertionError("Visible element not found for text '" + text + "'", last);
		}

		try {
			wait.until(ExpectedConditions.elementToBeClickable(target)).click();
		} catch (final Throwable clickFailure) {
			try {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", target);
			} catch (final Throwable jsClickFailure) {
				throw new AssertionError("Failed to click element with text '" + text + "'", jsClickFailure);
			}
		}

		waitForUiSettled();
	}

	private void selectGoogleAccountIfShown(final String email) {
		try {
			final By emailItem = By.xpath(
					"//*[contains(normalize-space(),'" + email + "')]"
			);
			final WebElement account = new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(ExpectedConditions.visibilityOfElementLocated(emailItem));
			if (account.isDisplayed()) {
				account.click();
				waitForUiSettled();
			}
		} catch (final TimeoutException ignored) {
			// Account picker did not appear (already authenticated flow).
		}
	}

	private void ensureLeftSidebarVisible() {
		final List<By> sidebarLocators = Arrays.asList(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar')]")
		);
		for (final By locator : sidebarLocators) {
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return;
			} catch (final TimeoutException ignored) {
				// Try next candidate locator.
			}
		}
		throw new AssertionError("Left sidebar navigation is not visible");
	}

	private void expandMiNegocioIfNeeded() {
		if (isTextVisible("Administrar Negocios")) {
			return;
		}
		clickByAnyVisibleText("Mi Negocio");
		waitForUiSettled();
	}

	private void assertVisibleText(final String text) {
		Assert.assertTrue("Expected visible text: " + text, isTextVisible(text));
	}

	private boolean isTextVisible(final String text) {
		try {
			final String textLiteral = xpathLiteral(text);
			final List<WebElement> elements = new ArrayList<>();
			elements.addAll(driver.findElements(By.xpath("//*[normalize-space(.)=" + textLiteral + "]")));
			elements.addAll(driver.findElements(By.xpath("//*[contains(normalize-space(.)," + textLiteral + ")]")));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final Throwable ignored) {
			return false;
		}
	}

	private void assertFieldByLabelExists(final String labelText) {
		final WebElement input = findInputByLabel(labelText);
		Assert.assertNotNull("Input for label '" + labelText + "' not found", input);
	}

	private WebElement findInputByLabel(final String labelText) {
		final String labelLiteral = xpathLiteral(labelText);
		final List<By> candidateLocators = Arrays.asList(
				By.xpath("//label[normalize-space(.)=" + labelLiteral + "]/following::input[1]"),
				By.xpath("//*[normalize-space(.)=" + labelLiteral + "]/following::input[1]"),
				By.xpath("//input[@placeholder=" + labelLiteral + "]")
		);
		for (final By locator : candidateLocators) {
			try {
				final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				if (input.isDisplayed()) {
					return input;
				}
			} catch (final TimeoutException ignored) {
				// Try next locator.
			}
		}
		throw new AssertionError("No visible input found for label: " + labelText);
	}

	private void assertAnyEmailVisible(final WebElement root) {
		final List<WebElement> nodes = root.findElements(By.xpath(".//*[contains(normalize-space(),'@')]"));
		for (final WebElement node : nodes) {
			final String text = node.getText().trim();
			if (node.isDisplayed() && EMAIL_PATTERN.matcher(text).matches()) {
				return;
			}
		}
		throw new AssertionError("No visible email was detected in Informacion General");
	}

	private void assertUserNameLooksVisible(final WebElement root) {
		final List<WebElement> nodes = root.findElements(By.xpath(".//*"));
		for (final WebElement node : nodes) {
			if (!node.isDisplayed()) {
				continue;
			}
			final String text = node.getText().trim();
			if (text.length() < 3 || text.length() > 80) {
				continue;
			}
			final String lowered = text.toLowerCase(Locale.ROOT);
			if (lowered.contains("@")
					|| lowered.contains("informaci")
					|| lowered.contains("business plan")
					|| lowered.contains("cambiar plan")
					|| lowered.contains("detalles de la cuenta")
					|| lowered.contains("tus negocios")
					|| lowered.contains("seccion legal")) {
				continue;
			}
			if (text.chars().anyMatch(Character::isLetter)) {
				return;
			}
		}
		throw new AssertionError("No candidate user name text was found");
	}

	private void assertAnyLegalContentVisible() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p|//article|//section|//main//div"));
		for (final WebElement paragraph : paragraphs) {
			final String text = paragraph.getText().trim();
			if (paragraph.isDisplayed() && text.length() > 120) {
				return;
			}
		}
		throw new AssertionError("Legal content body text was not detected");
	}

	private WebElement pageRoot() {
		try {
			return driver.findElement(By.tagName("main"));
		} catch (final Throwable ignored) {
			return driver.findElement(By.tagName("body"));
		}
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final Path target = screenshotsDir.resolve(sanitize(checkpointName) + ".png");
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		details.put("screenshot." + checkpointName, target.toAbsolutePath().toString());
	}

	private void takeFullPageScreenshot(final String checkpointName) throws IOException {
		final Object originalHeight = ((JavascriptExecutor) driver).executeScript(
				"return document.body ? document.body.style.height : '';"
		);
		try {
			((JavascriptExecutor) driver).executeScript(
					"if (document.body) { document.body.style.height = 'auto'; }"
			);
			waitForUiSettled();
			takeScreenshot(checkpointName);
		} finally {
			((JavascriptExecutor) driver).executeScript(
					"if (document.body) { document.body.style.height = arguments[0] || ''; }",
					originalHeight
			);
		}
	}

	private void printFinalReport() {
		System.out.println();
		System.out.println("========== SaleADS Mi Negocio Final Report ==========");
		for (final String name : REPORT_ORDER) {
			final boolean pass = Boolean.TRUE.equals(results.get(name));
			System.out.println(String.format("[%s] %s", pass ? "PASS" : "FAIL", name));
			if (details.containsKey(name)) {
				System.out.println("  detail: " + details.get(name));
			}
		}
		if (details.containsKey("05-terminos-y-condiciones.url")) {
			System.out.println("Terminos URL: " + details.get("05-terminos-y-condiciones.url"));
		}
		if (details.containsKey("06-politica-de-privacidad.url")) {
			System.out.println("Politica URL: " + details.get("06-politica-de-privacidad.url"));
		}
		if (screenshotsDir != null) {
			System.out.println("Screenshots directory: " + screenshotsDir.toAbsolutePath());
		}
		System.out.println("=====================================================");
		System.out.println();
	}

	private String sanitize(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
	}

	private String safeMessage(final Throwable throwable) {
		return throwable.getMessage() == null ? "(no message)" : throwable.getMessage();
	}

	private String getConfig(final String property, final String env, final String fallback) {
		final String systemValue = System.getProperty(property);
		if (systemValue != null && !systemValue.trim().isEmpty()) {
			return systemValue.trim();
		}
		final String envValue = System.getenv(env);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue.trim();
		}
		return fallback;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'", -1);
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			if (parts[i].isEmpty()) {
				builder.append("\"\"");
			} else {
				builder.append("'").append(parts[i]).append("'");
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
