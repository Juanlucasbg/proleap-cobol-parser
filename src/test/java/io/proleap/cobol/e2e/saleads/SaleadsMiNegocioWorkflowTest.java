package io.proleap.cobol.e2e.saleads;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
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
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end flow for SaleADS "Mi Negocio" module.
 *
 * Run explicitly with:
 * mvn -Dtest=SaleadsMiNegocioWorkflowTest -DrunSaleadsE2E=true -Dsaleads.url=https://<env>/login test
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String SCREENSHOT_ROOT = "target/surefire-reports/saleads-screenshots";
	private static final String REPORT_PATH = "target/surefire-reports/saleads-mi-negocio-report.txt";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final AtomicInteger screenshotCounter = new AtomicInteger(1);

	private Path screenshotDir;
	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue("Skipped: run with -DrunSaleadsE2E=true", Boolean.getBoolean("runSaleadsE2E"));

		final String saleadsUrl = firstNonBlank(System.getProperty("saleads.url"), System.getenv("SALEADS_URL"));
		Assume.assumeTrue("Skipped: provide -Dsaleads.url or SALEADS_URL", saleadsUrl != null && !saleadsUrl.isBlank());

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-notifications");
		options.addArguments("--window-size=1600,1200");

		if (Boolean.parseBoolean(System.getProperty("saleads.headless", "false"))) {
			options.addArguments("--headless=new");
		}

		final String chromeProfileDir = firstNonBlank(System.getProperty("saleads.chromeUserDataDir"),
				System.getenv("SALEADS_CHROME_USER_DATA_DIR"));
		if (chromeProfileDir != null && !chromeProfileDir.isBlank()) {
			options.addArguments("--user-data-dir=" + chromeProfileDir);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(Long.getLong("saleads.waitSeconds", 30L)));
		screenshotDir = Path.of(SCREENSHOT_ROOT, String.valueOf(Instant.now().toEpochMilli()));
		Files.createDirectories(screenshotDir);

		driver.get(saleadsUrl);
		waitForUiLoad();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		registerReportFields();

		report.put("Login", loginWithGoogle());
		if (!report.get("Login")) {
			Assert.fail(renderReportSummary());
		}

		report.put("Mi Negocio menu", openMiNegocioMenu());
		report.put("Agregar Negocio modal", validateAgregarNegocioModal());
		report.put("Administrar Negocios view", openAdministrarNegociosView());
		report.put("Información General", validateInformacionGeneralSection());
		report.put("Detalles de la Cuenta", validateDetallesCuentaSection());
		report.put("Tus Negocios", validateTusNegociosSection());
		report.put("Términos y Condiciones", openAndValidateLegalLink("T\u00e9rminos y Condiciones",
				"T\u00e9rminos y Condiciones", "terminos_y_condiciones"));
		report.put("Política de Privacidad", openAndValidateLegalLink("Pol\u00edtica de Privacidad",
				"Pol\u00edtica de Privacidad", "politica_de_privacidad"));

		Assert.assertTrue(renderReportSummary(), report.values().stream().allMatch(Boolean.TRUE::equals));
	}

	private boolean loginWithGoogle() {
		try {
			clickByAnyVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google",
					"Entrar con Google", "Google"));
			trySelectGoogleAccount();

			final boolean appLoaded = waitForAnyVisibleText(
					Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Panel", "Administrar Negocios"), Duration.ofSeconds(60));
			final boolean sidebarVisible = isAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio"), Duration.ofSeconds(30));
			takeScreenshot("dashboard-loaded");
			return appLoaded && sidebarVisible;
		} catch (Exception ex) {
			safeScreenshot("login-failure");
			return false;
		}
	}

	private boolean openMiNegocioMenu() {
		try {
			clickByAnyVisibleText(Arrays.asList("Negocio"));
			waitForUiLoad();
			clickByAnyVisibleText(Arrays.asList("Mi Negocio"));
			waitForUiLoad();

			final boolean agregarVisible = isTextVisible("Agregar Negocio", Duration.ofSeconds(15));
			final boolean administrarVisible = isTextVisible("Administrar Negocios", Duration.ofSeconds(15));
			takeScreenshot("mi-negocio-menu-expanded");
			return agregarVisible && administrarVisible;
		} catch (Exception ex) {
			safeScreenshot("mi-negocio-menu-failure");
			return false;
		}
	}

	private boolean validateAgregarNegocioModal() {
		try {
			clickByAnyVisibleText(Arrays.asList("Agregar Negocio"));
			waitForUiLoad();

			final boolean titleVisible = isTextVisible("Crear Nuevo Negocio", Duration.ofSeconds(15));
			final boolean nombreInputVisible = isNombreNegocioInputVisible();
			final boolean quotaVisible = isTextVisible("Tienes 2 de 3 negocios", Duration.ofSeconds(10));
			final boolean cancelarVisible = isTextVisible("Cancelar", Duration.ofSeconds(10));
			final boolean crearVisible = isTextVisible("Crear Negocio", Duration.ofSeconds(10));
			takeScreenshot("agregar-negocio-modal");

			typeInNombreNegocioIfPossible("Negocio Prueba Automatizacion");
			clickByAnyVisibleText(Arrays.asList("Cancelar"));
			waitForUiLoad();

			return titleVisible && nombreInputVisible && quotaVisible && cancelarVisible && crearVisible;
		} catch (Exception ex) {
			safeScreenshot("agregar-negocio-modal-failure");
			return false;
		}
	}

	private boolean openAdministrarNegociosView() {
		try {
			if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(5))) {
				clickByAnyVisibleText(Arrays.asList("Mi Negocio"));
				waitForUiLoad();
			}

			clickByAnyVisibleText(Arrays.asList("Administrar Negocios"));
			waitForUiLoad();

			final boolean infoGeneral = isTextVisible("Informaci\u00f3n General", Duration.ofSeconds(20));
			final boolean detallesCuenta = isTextVisible("Detalles de la Cuenta", Duration.ofSeconds(20));
			final boolean tusNegocios = isTextVisible("Tus Negocios", Duration.ofSeconds(20));
			final boolean legal = isAnyTextVisible(Arrays.asList("Secci\u00f3n Legal", "Legal"), Duration.ofSeconds(20));
			takeScreenshot("administrar-negocios-view");
			return infoGeneral && detallesCuenta && tusNegocios && legal;
		} catch (Exception ex) {
			safeScreenshot("administrar-negocios-view-failure");
			return false;
		}
	}

	private boolean validateInformacionGeneralSection() {
		try {
			final boolean hasName = isLikelyUserNameVisible(Duration.ofSeconds(10));
			final boolean hasEmail = isLikelyUserEmailVisible(Duration.ofSeconds(10));
			final boolean businessPlan = isTextVisible("BUSINESS PLAN", Duration.ofSeconds(10));
			final boolean cambiarPlan = isTextVisible("Cambiar Plan", Duration.ofSeconds(10));
			return hasName && hasEmail && businessPlan && cambiarPlan;
		} catch (Exception ex) {
			return false;
		}
	}

	private boolean validateDetallesCuentaSection() {
		try {
			final boolean cuentaCreada = isTextVisible("Cuenta creada", Duration.ofSeconds(10));
			final boolean estadoActivo = isAnyTextVisible(Arrays.asList("Estado activo", "Activo"), Duration.ofSeconds(10));
			final boolean idiomaSeleccionado = isTextVisible("Idioma seleccionado", Duration.ofSeconds(10));
			return cuentaCreada && estadoActivo && idiomaSeleccionado;
		} catch (Exception ex) {
			return false;
		}
	}

	private boolean validateTusNegociosSection() {
		try {
			final boolean sectionTitle = isTextVisible("Tus Negocios", Duration.ofSeconds(10));
			final boolean addButton = isTextVisible("Agregar Negocio", Duration.ofSeconds(10));
			final boolean quota = isTextVisible("Tienes 2 de 3 negocios", Duration.ofSeconds(10));
			return sectionTitle && addButton && quota;
		} catch (Exception ex) {
			return false;
		}
	}

	private boolean openAndValidateLegalLink(final String linkText, final String heading, final String slug) {
		String appHandle = null;
		try {
			appHandle = driver.getWindowHandle();
			final Set<String> existingHandles = driver.getWindowHandles();

			clickByAnyVisibleText(Arrays.asList(linkText));
			waitForUiLoad();

			final String targetHandle = waitForNewTabHandle(existingHandles, Duration.ofSeconds(12));
			final boolean openedInNewTab = targetHandle != null;
			if (openedInNewTab) {
				driver.switchTo().window(targetHandle);
				waitForUiLoad();
			}

			final boolean headingVisible = isTextVisible(heading, Duration.ofSeconds(20));
			final boolean legalContentVisible = isAnyTextVisible(
					Arrays.asList("t\u00e9rminos", "condiciones", "privacidad", "informaci\u00f3n", "datos"), Duration.ofSeconds(20));

			takeScreenshot(slug + "-page");
			legalUrls.put(linkText, driver.getCurrentUrl());

			if (openedInNewTab) {
				driver.close();
				driver.switchTo().window(appHandle);
			} else {
				driver.navigate().back();
			}
			waitForUiLoad();
			return headingVisible && legalContentVisible;
		} catch (Exception ex) {
			safeScreenshot(slug + "-failure");
			try {
				if (appHandle != null) {
					driver.switchTo().window(appHandle);
				}
			} catch (Exception ignored) {
				// no-op
			}
			return false;
		}
	}

	private void registerReportFields() {
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
	}

	private void clickByAnyVisibleText(final List<String> texts) {
		Exception lastError = null;
		for (final String text : texts) {
			try {
				final WebElement element = wait.until(driver -> findFirstVisibleElementByText(text));
				scrollIntoView(element);
				element.click();
				waitForUiLoad();
				return;
			} catch (Exception ex) {
				lastError = ex;
			}
		}
		throw new NoSuchElementException("No clickable element found for texts: " + texts, lastError);
	}

	private WebElement findFirstVisibleElementByText(final String text) {
		final String escapedText = escapeXpath(text);
		final String xpath = "//*[self::button or self::a or @role='button' or self::div or self::span]"
				+ "[contains(normalize-space(.), " + escapedText + ")]";
		final List<WebElement> candidates = driver.findElements(By.xpath(xpath));
		return candidates.stream().filter(WebElement::isDisplayed).findFirst().orElse(null);
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		return isAnyTextVisible(List.of(text), timeout);
	}

	private boolean isAnyTextVisible(final List<String> texts, final Duration timeout) {
		try {
			return waitForAnyVisibleText(texts, timeout);
		} catch (Exception ex) {
			return false;
		}
	}

	private boolean waitForAnyVisibleText(final List<String> texts, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			return shortWait.until(driver -> texts.stream().anyMatch(this::isTextCurrentlyVisible));
		} catch (TimeoutException ex) {
			return false;
		}
	}

	private boolean isTextCurrentlyVisible(final String text) {
		final String escapedText = escapeXpath(text);
		final String xpath = "//*[contains(translate(normalize-space(.),"
				+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZ\u00c1\u00c9\u00cd\u00d3\u00da',"
				+ "'abcdefghijklmnopqrstuvwxyz\u00e1\u00e9\u00ed\u00f3\u00fa'),"
				+ "translate(" + escapedText + ","
				+ "'ABCDEFGHIJKLMNOPQRSTUVWXYZ\u00c1\u00c9\u00cd\u00d3\u00da',"
				+ "'abcdefghijklmnopqrstuvwxyz\u00e1\u00e9\u00ed\u00f3\u00fa'))]";
		return driver.findElements(By.xpath(xpath)).stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean isNombreNegocioInputVisible() {
		final List<String> xpaths = List.of(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]",
				"//input[contains(@placeholder, 'Nombre del Negocio')]",
				"//input[contains(@name, 'nombre') or contains(@id, 'nombre')]");
		for (final String xpath : xpaths) {
			final List<WebElement> elements = driver.findElements(By.xpath(xpath));
			final Optional<WebElement> visibleElement = elements.stream().filter(WebElement::isDisplayed).findFirst();
			if (visibleElement.isPresent()) {
				return true;
			}
		}
		return false;
	}

	private void typeInNombreNegocioIfPossible(final String negocioName) {
		final List<String> xpaths = List.of(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]",
				"//input[contains(@placeholder, 'Nombre del Negocio')]",
				"//input[contains(@name, 'nombre') or contains(@id, 'nombre')]");
		for (final String xpath : xpaths) {
			final List<WebElement> elements = driver.findElements(By.xpath(xpath));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					element.clear();
					element.sendKeys(negocioName);
					return;
				}
			}
		}
	}

	private void trySelectGoogleAccount() {
		try {
			final Set<String> originalHandles = driver.getWindowHandles();
			final String newTabHandle = waitForNewTabHandle(originalHandles, Duration.ofSeconds(8));
			if (newTabHandle != null) {
				driver.switchTo().window(newTabHandle);
			}

			if (isTextVisible(ACCOUNT_EMAIL, Duration.ofSeconds(12))) {
				clickByAnyVisibleText(Arrays.asList(ACCOUNT_EMAIL));
			}

			if (newTabHandle != null) {
				wait.until((ExpectedCondition<Boolean>) wd -> wd != null && wd.getWindowHandles().size() >= 1);
				driver.switchTo().window(driver.getWindowHandles().iterator().next());
			}
		} catch (Exception ignored) {
			// Account chooser may not appear if user already authenticated.
		}
	}

	private String waitForNewTabHandle(final Set<String> existingHandles, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			return shortWait.until(drv -> {
				final Set<String> currentHandles = drv.getWindowHandles();
				if (currentHandles.size() <= existingHandles.size()) {
					return null;
				}
				for (final String handle : currentHandles) {
					if (!existingHandles.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
		} catch (TimeoutException ex) {
			return null;
		}
	}

	private boolean isLikelyUserNameVisible(final Duration timeout) {
		final List<String> nameHints = Arrays.asList("Nombre", "Usuario", "Perfil");
		return isAnyTextVisible(nameHints, timeout);
	}

	private boolean isLikelyUserEmailVisible(final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			return shortWait.until(drv -> drv.findElements(By.xpath("//*[contains(text(),'@')]")).stream()
					.anyMatch(WebElement::isDisplayed));
		} catch (TimeoutException ex) {
			return false;
		}
	}

	private void waitForUiLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final Path screenshotPath = screenshotDir.resolve(String.format("%02d-%s.png", screenshotCounter.getAndIncrement(), checkpointName));
		final Path sourcePath = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(sourcePath, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void safeScreenshot(final String checkpointName) {
		try {
			takeScreenshot(checkpointName);
		} catch (Exception ignored) {
			// no-op
		}
	}

	private void writeFinalReport() throws IOException {
		if (report.isEmpty()) {
			return;
		}

		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Workflow Report\n");
		sb.append("=================================\n");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			sb.append(entry.getKey()).append(": ").append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL").append('\n');
		}

		if (!legalUrls.isEmpty()) {
			sb.append("\nLegal URLs:\n");
			for (final Map.Entry<String, String> legalEntry : legalUrls.entrySet()) {
				sb.append("- ").append(legalEntry.getKey()).append(": ").append(legalEntry.getValue()).append('\n');
			}
		}

		sb.append("\nScreenshots directory: ").append(screenshotDir).append('\n');
		Files.createDirectories(Path.of("target/surefire-reports"));
		Files.writeString(Path.of(REPORT_PATH), sb.toString());
	}

	private String renderReportSummary() {
		final StringBuilder sb = new StringBuilder("Final Report:\n");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ")
					.append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL").append('\n');
		}
		return sb.toString();
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private static String escapeXpath(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		final String[] parts = text.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(", \"'\", ");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}
}
