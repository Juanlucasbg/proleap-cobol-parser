package io.proleap.saleads;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
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
import java.nio.charset.StandardCharsets;
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
import java.util.Set;
import java.util.regex.Pattern;

public class SaleadsMiNegocioWorkflowTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, Boolean> statusByField = new LinkedHashMap<>();
	private final Map<String, String> detailByField = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private String termsUrl = "";
	private String privacyUrl = "";

	@Before
	public void setUp() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);

		WebDriverManager.chromedriver().setup();

		final ChromeOptions chromeOptions = new ChromeOptions();
		chromeOptions.addArguments("--window-size=1920,1080");
		chromeOptions.addArguments("--disable-dev-shm-usage");
		chromeOptions.addArguments("--no-sandbox");
		if (Boolean.parseBoolean(readValue("SALEADS_HEADLESS", "true"))) {
			chromeOptions.addArguments("--headless=new");
		}

		driver = new ChromeDriver(chromeOptions);
		wait = new WebDriverWait(driver, Duration.ofSeconds(Long.parseLong(readValue("SALEADS_WAIT_SECONDS", "25"))));
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		initializeReport();

		try {
			final String loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getenv("SALEADS_URL"),
					System.getProperty("saleads.url"));
			if (loginUrl == null || loginUrl.isBlank()) {
				markAllBlocked(
						"Missing SALEADS_LOGIN_URL (or SALEADS_URL/saleads.url). Configure target environment URL.");
			} else {
				driver.get(loginUrl);
				waitForUiToLoad();

				final boolean loginOk = executeStep("Login", this::loginWithGoogleAndValidate,
						"Dashboard and sidebar visible.");
				final boolean menuOk = loginOk ? executeStep("Mi Negocio menu", this::openMiNegocioMenuAndValidate,
						"Mi Negocio menu expanded with both submenu entries.")
						: markBlocked("Mi Negocio menu", "Login failed; cannot open left navigation.");

				final boolean agregarModalOk = menuOk ? executeStep("Agregar Negocio modal",
						this::validateAgregarNegocioModal, "Crear Nuevo Negocio modal validated.")
						: markBlocked("Agregar Negocio modal", "Mi Negocio menu validation failed.");

				final boolean administrarOk = (menuOk || agregarModalOk)
						? executeStep("Administrar Negocios view", this::openAdministrarNegociosAndValidate,
								"Account sections are visible.")
						: markBlocked("Administrar Negocios view", "Navigation prerequisites were not completed.");

				final boolean infoGeneralOk = administrarOk
						? executeStep("Información General", this::validateInformacionGeneral,
								"User data and plan controls are visible.")
						: markBlocked("Información General", "Administrar Negocios view not available.");

				final boolean detallesOk = administrarOk
						? executeStep("Detalles de la Cuenta", this::validateDetallesCuenta,
								"Cuenta creada, Estado activo, and Idioma seleccionado are visible.")
						: markBlocked("Detalles de la Cuenta", "Administrar Negocios view not available.");

				final boolean tusNegociosOk = administrarOk ? executeStep("Tus Negocios", this::validateTusNegocios,
						"Business list plus limits and add action are visible.")
						: markBlocked("Tus Negocios", "Administrar Negocios view not available.");

				if (administrarOk || infoGeneralOk || detallesOk || tusNegociosOk) {
					executeStep("Términos y Condiciones",
							() -> validateLegalLink("Términos y Condiciones", "08-terminos-y-condiciones.png", true),
							"Legal page validated.");
					executeStep("Política de Privacidad",
							() -> validateLegalLink("Política de Privacidad", "09-politica-de-privacidad.png", false),
							"Privacy page validated.");
				} else {
					markBlocked("Términos y Condiciones",
							"Legal section is unavailable because account page was not reached.");
					markBlocked("Política de Privacidad",
							"Legal section is unavailable because account page was not reached.");
				}
			}
		} finally {
			writeFinalReport();
		}

		if (!failures.isEmpty()) {
			Assert.fail(String.join(System.lineSeparator(), failures));
		}
	}

	private boolean loginWithGoogleAndValidate() throws Exception {
		clickByAnyVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Acceder con Google", "Google");
		selectGoogleAccountIfVisible(readValue("SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT));

		final boolean mainInterfaceVisible = waitForAnyVisibleText(60, "Negocio", "Dashboard", "Inicio");
		final boolean sidebarVisible = waitForVisible(By.xpath("//aside | //nav[.//*[contains(normalize-space(),'Negocio')]]"),
				30);

		takeScreenshot("01-dashboard-loaded.png");
		return mainInterfaceVisible && sidebarVisible;
	}

	private boolean openMiNegocioMenuAndValidate() throws Exception {
		clickByAnyVisibleText("Negocio");
		clickByAnyVisibleText("Mi Negocio");

		final boolean submenuExpanded = waitForAnyVisibleText(20, "Agregar Negocio", "Administrar Negocios");
		final boolean agregarVisible = isVisible(By.xpath("//*[normalize-space()='Agregar Negocio']"), 10);
		final boolean administrarVisible = isVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"), 10);

		takeScreenshot("02-mi-negocio-menu-expanded.png");
		return submenuExpanded && agregarVisible && administrarVisible;
	}

	private boolean validateAgregarNegocioModal() throws Exception {
		clickByAnyVisibleText("Agregar Negocio");

		final boolean titleVisible = waitForVisible(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']"), 20);
		final boolean inputVisible = isVisible(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio']"),
				10) || isVisible(By.xpath(
						"//*[normalize-space()='Nombre del Negocio']/following::input[1] | //label[normalize-space()='Nombre del Negocio']/following::input[1]"),
						10);
		final boolean quotaVisible = isVisible(By.xpath("//*[contains(normalize-space(),'Tienes 2 de 3 negocios')]"), 10);
		final boolean cancelVisible = isVisible(By.xpath("//*[normalize-space()='Cancelar']"), 10);
		final boolean createVisible = isVisible(By.xpath("//*[normalize-space()='Crear Negocio']"), 10);

		takeScreenshot("03-agregar-negocio-modal.png");

		// Optional workflow completion to avoid persistent form state.
		final List<WebElement> modalInputs = driver.findElements(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio' or @type='text']"));
		if (!modalInputs.isEmpty()) {
			modalInputs.get(0).click();
			modalInputs.get(0).clear();
			modalInputs.get(0).sendKeys("Negocio Prueba Automatizacion");
			waitForUiToLoad();
		}
		if (cancelVisible) {
			clickByAnyVisibleText("Cancelar");
		}

		return titleVisible && inputVisible && quotaVisible && cancelVisible && createVisible;
	}

	private boolean openAdministrarNegociosAndValidate() throws Exception {
		if (!isVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"), 3)) {
			clickByAnyVisibleText("Mi Negocio");
		}

		clickByAnyVisibleText("Administrar Negocios");

		final boolean informacionGeneralVisible = waitForVisible(By.xpath("//*[normalize-space()='Información General']"), 25);
		final boolean detallesVisible = waitForVisible(By.xpath("//*[normalize-space()='Detalles de la Cuenta']"), 25);
		final boolean negociosVisible = waitForVisible(By.xpath("//*[normalize-space()='Tus Negocios']"), 25);
		final boolean legalVisible = waitForVisible(By.xpath("//*[normalize-space()='Sección Legal']"), 25);

		takeScreenshot("04-administrar-negocios-page.png");
		return informacionGeneralVisible && detallesVisible && negociosVisible && legalVisible;
	}

	private boolean validateInformacionGeneral() {
		final WebElement section = findSectionRoot("Información General");
		final String sectionText = section.getText();

		final boolean hasEmail = section.findElements(By.xpath(".//*[contains(normalize-space(),'@')]")).stream()
				.anyMatch(element -> EMAIL_PATTERN.matcher(element.getText().trim()).find());
		final boolean hasName = sectionText.lines().map(String::trim)
				.anyMatch(line -> line.length() >= 3 && !line.contains("@") && !line.equalsIgnoreCase("Información General")
						&& !line.equalsIgnoreCase("BUSINESS PLAN") && !line.equalsIgnoreCase("Cambiar Plan"));
		final boolean hasBusinessPlan = sectionText.contains("BUSINESS PLAN");
		final boolean hasCambiarPlan = section.findElements(By.xpath(".//*[normalize-space()='Cambiar Plan']")).size() > 0;

		return hasName && hasEmail && hasBusinessPlan && hasCambiarPlan;
	}

	private boolean validateDetallesCuenta() {
		final WebElement section = findSectionRoot("Detalles de la Cuenta");
		final String sectionText = section.getText();

		return sectionText.contains("Cuenta creada") && sectionText.contains("Estado activo")
				&& sectionText.contains("Idioma seleccionado");
	}

	private boolean validateTusNegocios() {
		final WebElement section = findSectionRoot("Tus Negocios");
		final String sectionText = section.getText();

		final boolean businessListVisible = section.findElements(By.xpath(".//li | .//tbody/tr | .//*[contains(@class,'business')]"))
				.size() > 0;
		final boolean addBusinessButtonVisible = section.findElements(By.xpath(".//*[normalize-space()='Agregar Negocio']")).size() > 0;
		final boolean quotaTextVisible = sectionText.contains("Tienes 2 de 3 negocios");

		return businessListVisible && addBusinessButtonVisible && quotaTextVisible;
	}

	private boolean validateLegalLink(final String linkText, final String screenshotName, final boolean isTerms) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final String appUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickByAnyVisibleText(linkText);

		boolean openedNewTab = false;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20))
					.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size() || !d.getCurrentUrl().equals(appUrl));
			openedNewTab = driver.getWindowHandles().size() > handlesBeforeClick.size();
		} catch (TimeoutException ignored) {
			openedNewTab = false;
		}

		if (openedNewTab) {
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		waitForUiToLoad();
		final boolean headingVisible = waitForVisible(By.xpath("//*[normalize-space()=" + xPathLiteral(linkText) + "]"), 30);
		final boolean legalTextVisible = isVisible(
				By.xpath("//*[self::p or self::div][string-length(normalize-space()) > 120]"), 10);
		takeScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();
		if (isTerms) {
			termsUrl = finalUrl;
		} else {
			privacyUrl = finalUrl;
		}

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();

		return headingVisible && legalTextVisible;
	}

	private boolean executeStep(final String fieldName, final StepAction action, final String passDetail) {
		try {
			final boolean result = action.run();
			statusByField.put(fieldName, result);
			final String detail = result ? passDetail : "Validation failed.";
			detailByField.put(fieldName, detail);
			if (!result) {
				failures.add(fieldName + ": " + detail);
			}
			return result;
		} catch (Exception exception) {
			statusByField.put(fieldName, false);
			detailByField.put(fieldName, "Error: " + exception.getMessage());
			failures.add(fieldName + ": " + exception.getClass().getSimpleName() + " - " + exception.getMessage());
			return false;
		}
	}

	private boolean markBlocked(final String fieldName, final String reason) {
		statusByField.put(fieldName, false);
		detailByField.put(fieldName, "Blocked: " + reason);
		failures.add(fieldName + ": Blocked - " + reason);
		return false;
	}

	private void initializeReport() {
		for (final String field : REPORT_FIELDS) {
			statusByField.put(field, false);
			detailByField.put(field, "Not executed.");
		}
	}

	private void markAllBlocked(final String reason) {
		for (final String field : REPORT_FIELDS) {
			markBlocked(field, reason);
		}
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Full Workflow Report");
		lines.add("Generated at: " + LocalDateTime.now());
		lines.add("");
		for (final String field : REPORT_FIELDS) {
			final String status = statusByField.getOrDefault(field, false) ? "PASS" : "FAIL";
			lines.add(field + ": " + status + " - " + detailByField.getOrDefault(field, "No detail."));
		}
		lines.add("");
		lines.add("Términos y Condiciones URL: " + (termsUrl.isEmpty() ? "N/A" : termsUrl));
		lines.add("Política de Privacidad URL: " + (privacyUrl.isEmpty() ? "N/A" : privacyUrl));
		lines.add("Evidence directory: " + evidenceDir.toAbsolutePath());

		final Path reportFile = evidenceDir.resolve("final-report.txt");
		Files.write(reportFile, lines, StandardCharsets.UTF_8);
	}

	private void clickByAnyVisibleText(final String... textCandidates) {
		for (final String text : textCandidates) {
			final WebElement element = findClickableByText(text, 4);
			if (element != null) {
				safeClick(element);
				return;
			}
		}
		throw new IllegalStateException("None of the text candidates were clickable: " + Arrays.toString(textCandidates));
	}

	private WebElement findClickableByText(final String text, final long timeoutInSeconds) {
		final By exactLocator = By.xpath("//*[normalize-space()=" + xPathLiteral(text)
				+ "]/ancestor-or-self::*[self::button or self::a or @role='button' or self::div][1]");
		final By containsLocator = By.xpath("//*[contains(normalize-space(), " + xPathLiteral(text)
				+ ")]/ancestor-or-self::*[self::button or self::a or @role='button' or self::div][1]");

		try {
			return new WebDriverWait(driver, Duration.ofSeconds(timeoutInSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(exactLocator));
		} catch (TimeoutException ignored) {
			try {
				return new WebDriverWait(driver, Duration.ofSeconds(timeoutInSeconds))
						.until(ExpectedConditions.visibilityOfElementLocated(containsLocator));
			} catch (TimeoutException ignoredAgain) {
				return null;
			}
		}
	}

	private void safeClick(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		final long timeoutMs = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
		while (System.currentTimeMillis() < timeoutMs) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				final List<WebElement> accounts = driver
						.findElements(By.xpath("//*[normalize-space()=" + xPathLiteral(accountEmail) + "]"));
				if (!accounts.isEmpty()) {
					safeClick(accounts.get(0));
					return;
				}
			}
			sleep(500);
		}
	}

	private boolean waitForAnyVisibleText(final int timeoutSeconds, final String... texts) {
		final long timeoutMs = System.currentTimeMillis() + Duration.ofSeconds(timeoutSeconds).toMillis();
		while (System.currentTimeMillis() < timeoutMs) {
			for (final String text : texts) {
				if (isVisible(By.xpath("//*[contains(normalize-space(), " + xPathLiteral(text) + ")]"), 1)) {
					return true;
				}
			}
			sleep(250);
		}
		return false;
	}

	private boolean waitForVisible(final By locator, final int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (TimeoutException exception) {
			return false;
		}
	}

	private boolean isVisible(final By locator, final int timeoutSeconds) {
		return waitForVisible(locator, timeoutSeconds);
	}

	private WebElement findSectionRoot(final String headingText) {
		final By headingLocator = By.xpath("//*[normalize-space()=" + xPathLiteral(headingText) + "]");
		final WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(headingLocator));
		return heading.findElement(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.copy(new java.io.ByteArrayInputStream(screenshot), evidenceDir.resolve(fileName),
				StandardCopyOption.REPLACE_EXISTING);
	}

	private void waitForUiToLoad() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> {
				final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(state);
			});
		} catch (Exception ignored) {
			// Continue with a small delay if readyState probing is not available.
		}
		sleep(500);
	}

	private String readValue(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private String xPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder result = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				result.append(", \"'\", ");
			}
			result.append("'").append(parts[i]).append("'");
		}
		result.append(")");
		return result.toString();
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		boolean run() throws Exception;
	}
}
