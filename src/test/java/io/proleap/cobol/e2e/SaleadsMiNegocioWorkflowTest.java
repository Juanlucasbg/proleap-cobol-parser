package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(7);
	private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private int screenshotCounter;

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String runId = LocalDateTime.now().format(RUN_ID_FORMAT);
		evidenceDirectory = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDirectory);

		final ChromeOptions options = new ChromeOptions();
		if (parseHeadlessFlag()) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().window().setSize(new Dimension(1920, 1080));

		final String loginUrl = env("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl.trim());
			waitForUiToLoad();
		} else {
			System.out.println(
					"SALEADS_LOGIN_URL is not set. Test expects login page in current browser context and may fail from about:blank.");
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		finalReport.put("Login", stepLoginWithGoogle());
		finalReport.put("Mi Negocio menu", stepOpenMiNegocioMenu());
		finalReport.put("Agregar Negocio modal", stepValidateAgregarNegocioModal());
		finalReport.put("Administrar Negocios view", stepOpenAdministrarNegocios());
		finalReport.put("Información General", stepValidateInformacionGeneral());
		finalReport.put("Detalles de la Cuenta", stepValidateDetallesCuenta());
		finalReport.put("Tus Negocios", stepValidateTusNegocios());
		finalReport.put("Términos y Condiciones",
				stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "terminos-y-condiciones"));
		finalReport.put("Política de Privacidad",
				stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "politica-de-privacidad"));

		writeFinalReportFile();

		final List<String> failedFields = finalReport.entrySet().stream().filter(entry -> !entry.getValue())
				.map(Map.Entry::getKey).collect(Collectors.toList());
		assertTrue("Mi Negocio workflow validations failed: " + failedFields + " | Details: " + failures,
				failedFields.isEmpty());
	}

	private boolean stepLoginWithGoogle() {
		boolean ok = true;

		ok &= clickFirstByText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Entrar con Google");
		if (!ok) {
			failures.add("Login: Google sign-in button was not found.");
			return false;
		}

		clickIfVisibleByText("juanlucasbarbiergarzon@gmail.com");

		final boolean sidebarVisible = isVisible(By.xpath("//aside | //nav"), DEFAULT_TIMEOUT);
		final boolean appReady = sidebarVisible
				|| isVisible(By.xpath("//*[contains(normalize-space(.), 'Dashboard') or contains(normalize-space(.), 'Inicio')]"),
						DEFAULT_TIMEOUT);

		if (!appReady) {
			failures.add("Login: Main application interface was not detected after Google sign-in.");
		}
		if (!sidebarVisible) {
			failures.add("Login: Left sidebar navigation was not visible.");
		}

		captureScreenshot("01-dashboard-loaded", false);
		return appReady && sidebarVisible;
	}

	private boolean stepOpenMiNegocioMenu() {
		boolean ok = true;
		final boolean negocioSectionVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Negocio')]"),
				DEFAULT_TIMEOUT);
		if (!negocioSectionVisible) {
			failures.add("Mi Negocio menu: 'Negocio' section is not visible.");
			ok = false;
		}

		if (!clickFirstByText("Mi Negocio")) {
			failures.add("Mi Negocio menu: Unable to click 'Mi Negocio'.");
			ok = false;
		}

		final boolean addBusinessVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Agregar Negocio')]"),
				DEFAULT_TIMEOUT);
		final boolean manageBusinessesVisible = isVisible(
				By.xpath("//*[contains(normalize-space(.), 'Administrar Negocios')]"), DEFAULT_TIMEOUT);

		if (!addBusinessVisible) {
			failures.add("Mi Negocio menu: 'Agregar Negocio' is not visible.");
		}
		if (!manageBusinessesVisible) {
			failures.add("Mi Negocio menu: 'Administrar Negocios' is not visible.");
		}

		captureScreenshot("02-mi-negocio-expanded", false);
		return ok && addBusinessVisible && manageBusinessesVisible;
	}

	private boolean stepValidateAgregarNegocioModal() {
		boolean ok = true;

		if (!clickFirstByText("Agregar Negocio")) {
			failures.add("Agregar Negocio modal: Could not open modal from 'Agregar Negocio'.");
			return false;
		}

		final boolean titleVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]"),
				DEFAULT_TIMEOUT);
		final boolean businessNameInputVisible = isVisible(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"),
				DEFAULT_TIMEOUT);
		final boolean planLimitVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Tienes 2 de 3 negocios')]"),
				DEFAULT_TIMEOUT);
		final boolean cancelVisible = isVisible(By.xpath("//*[self::button or self::a][contains(normalize-space(.), 'Cancelar')]"),
				DEFAULT_TIMEOUT);
		final boolean createVisible = isVisible(
				By.xpath("//*[self::button or self::a][contains(normalize-space(.), 'Crear Negocio')]"), DEFAULT_TIMEOUT);

		ok &= titleVisible;
		ok &= businessNameInputVisible;
		ok &= planLimitVisible;
		ok &= cancelVisible;
		ok &= createVisible;

		if (!titleVisible) {
			failures.add("Agregar Negocio modal: Missing title 'Crear Nuevo Negocio'.");
		}
		if (!businessNameInputVisible) {
			failures.add("Agregar Negocio modal: Missing field 'Nombre del Negocio'.");
		}
		if (!planLimitVisible) {
			failures.add("Agregar Negocio modal: Missing text 'Tienes 2 de 3 negocios'.");
		}
		if (!cancelVisible || !createVisible) {
			failures.add("Agregar Negocio modal: Missing expected action buttons.");
		}

		captureScreenshot("03-agregar-negocio-modal", false);

		typeIfVisible(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"),
				"Negocio Prueba Automatización");
		clickIfVisibleByText("Cancelar");

		return ok;
	}

	private boolean stepOpenAdministrarNegocios() {
		if (!isVisible(By.xpath("//*[contains(normalize-space(.), 'Administrar Negocios')]"), SHORT_TIMEOUT)) {
			clickIfVisibleByText("Mi Negocio");
		}

		boolean ok = clickFirstByText("Administrar Negocios");
		if (!ok) {
			failures.add("Administrar Negocios view: Could not click 'Administrar Negocios'.");
			return false;
		}

		waitForUiToLoad();

		final boolean infoGeneralVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Información General')]"),
				DEFAULT_TIMEOUT);
		final boolean detailsVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Detalles de la Cuenta')]"),
				DEFAULT_TIMEOUT);
		final boolean businessesVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]"),
				DEFAULT_TIMEOUT);
		final boolean legalVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Sección Legal')]"),
				DEFAULT_TIMEOUT);

		if (!infoGeneralVisible) {
			failures.add("Administrar Negocios view: Section 'Información General' not found.");
		}
		if (!detailsVisible) {
			failures.add("Administrar Negocios view: Section 'Detalles de la Cuenta' not found.");
		}
		if (!businessesVisible) {
			failures.add("Administrar Negocios view: Section 'Tus Negocios' not found.");
		}
		if (!legalVisible) {
			failures.add("Administrar Negocios view: Section 'Sección Legal' not found.");
		}

		captureScreenshot("04-administrar-negocios", true);
		return infoGeneralVisible && detailsVisible && businessesVisible && legalVisible;
	}

	private boolean stepValidateInformacionGeneral() {
		final boolean userNameVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Nombre') or contains(@class, 'name')]"),
				DEFAULT_TIMEOUT);
		final boolean userEmailVisible = isVisible(By.xpath("//*[contains(normalize-space(.), '@')]"), DEFAULT_TIMEOUT);
		final boolean businessPlanVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'BUSINESS PLAN')]"),
				DEFAULT_TIMEOUT);
		final boolean changePlanVisible = isVisible(By.xpath("//*[self::button or self::a][contains(normalize-space(.), 'Cambiar Plan')]"),
				DEFAULT_TIMEOUT);

		if (!userNameVisible) {
			failures.add("Información General: User name was not visible.");
		}
		if (!userEmailVisible) {
			failures.add("Información General: User email was not visible.");
		}
		if (!businessPlanVisible) {
			failures.add("Información General: 'BUSINESS PLAN' text was not visible.");
		}
		if (!changePlanVisible) {
			failures.add("Información General: 'Cambiar Plan' button was not visible.");
		}

		return userNameVisible && userEmailVisible && businessPlanVisible && changePlanVisible;
	}

	private boolean stepValidateDetallesCuenta() {
		final boolean createdVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Cuenta creada')]"),
				DEFAULT_TIMEOUT);
		final boolean statusVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Estado activo')]"),
				DEFAULT_TIMEOUT);
		final boolean languageVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Idioma seleccionado')]"),
				DEFAULT_TIMEOUT);

		if (!createdVisible) {
			failures.add("Detalles de la Cuenta: 'Cuenta creada' not visible.");
		}
		if (!statusVisible) {
			failures.add("Detalles de la Cuenta: 'Estado activo' not visible.");
		}
		if (!languageVisible) {
			failures.add("Detalles de la Cuenta: 'Idioma seleccionado' not visible.");
		}

		return createdVisible && statusVisible && languageVisible;
	}

	private boolean stepValidateTusNegocios() {
		final boolean businessListVisible = isVisible(
				By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]/following::*[self::ul or self::table or self::div][1]"),
				DEFAULT_TIMEOUT) || isVisible(By.xpath("//*[contains(normalize-space(.), 'Tus Negocios')]"), DEFAULT_TIMEOUT);
		final boolean addBusinessButtonVisible = isVisible(
				By.xpath("//*[self::button or self::a][contains(normalize-space(.), 'Agregar Negocio')]"), DEFAULT_TIMEOUT);
		final boolean planLimitVisible = isVisible(By.xpath("//*[contains(normalize-space(.), 'Tienes 2 de 3 negocios')]"),
				DEFAULT_TIMEOUT);

		if (!businessListVisible) {
			failures.add("Tus Negocios: Business list was not visible.");
		}
		if (!addBusinessButtonVisible) {
			failures.add("Tus Negocios: Button 'Agregar Negocio' was not visible.");
		}
		if (!planLimitVisible) {
			failures.add("Tus Negocios: Text 'Tienes 2 de 3 negocios' was not visible.");
		}

		return businessListVisible && addBusinessButtonVisible && planLimitVisible;
	}

	private boolean stepValidateLegalLink(final String linkText, final String headingText, final String screenshotName) {
		boolean ok = true;

		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String urlBeforeClick = driver.getCurrentUrl();

		if (!clickFirstByText(linkText)) {
			failures.add(linkText + ": Link was not clickable.");
			return false;
		}

		waitUntil(driver -> driver.getWindowHandles().size() > handlesBeforeClick.size()
				|| !driver.getCurrentUrl().equals(urlBeforeClick), DEFAULT_TIMEOUT);

		boolean openedNewTab = false;
		final Set<String> handlesAfterClick = driver.getWindowHandles();
		if (handlesAfterClick.size() > handlesBeforeClick.size()) {
			openedNewTab = true;
			for (final String handle : handlesAfterClick) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		waitForUiToLoad();

		final boolean headingVisible = isVisible(By.xpath("//*[self::h1 or self::h2 or self::h3][contains(normalize-space(.), "
				+ toXPathLiteral(headingText) + ")] | //*[contains(normalize-space(.), " + toXPathLiteral(headingText) + ")]"),
				DEFAULT_TIMEOUT);
		final boolean legalContentVisible = isVisible(By.xpath(
				"//main//*[string-length(normalize-space(.)) > 60] | //article//*[string-length(normalize-space(.)) > 60] | //p[string-length(normalize-space(.)) > 60]"),
				DEFAULT_TIMEOUT);

		if (!headingVisible) {
			failures.add(linkText + ": Heading '" + headingText + "' not visible.");
			ok = false;
		}
		if (!legalContentVisible) {
			failures.add(linkText + ": Legal content text was not visible.");
			ok = false;
		}

		captureScreenshot("05-" + screenshotName, false);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else if (!driver.getCurrentUrl().equals(urlBeforeClick)) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return ok;
	}

	private boolean clickFirstByText(final String... visibleTexts) {
		for (final String text : visibleTexts) {
			if (clickByText(text)) {
				return true;
			}
		}
		return false;
	}

	private boolean clickIfVisibleByText(final String text) {
		return clickByText(text, SHORT_TIMEOUT, true);
	}

	private boolean clickByText(final String text) {
		return clickByText(text, DEFAULT_TIMEOUT, false);
	}

	private boolean clickByText(final String text, final Duration timeout, final boolean optional) {
		final String exactText = toXPathLiteral(text);
		final List<By> locators = List.of(
				By.xpath("//button[normalize-space(.)=" + exactText + "]"),
				By.xpath("//a[normalize-space(.)=" + exactText + "]"),
				By.xpath("//*[@role='button' and normalize-space(.)=" + exactText + "]"),
				By.xpath(
						"//*[self::button or self::a or @role='button' or self::span or self::div][contains(normalize-space(.), "
								+ exactText + ")]"));

		for (final By locator : locators) {
			final Optional<WebElement> candidate = waitForDisplayed(locator, timeout);
			if (candidate.isPresent()) {
				try {
					scrollTo(candidate.get());
					candidate.get().click();
					waitForUiToLoad();
					return true;
				} catch (final RuntimeException ex) {
					// Try next locator.
				}
			}
		}

		if (!optional) {
			System.out.println("Unable to click element by visible text: " + text);
		}
		return false;
	}

	private void typeIfVisible(final By locator, final String value) {
		final Optional<WebElement> input = waitForDisplayed(locator, SHORT_TIMEOUT);
		if (input.isPresent()) {
			scrollTo(input.get());
			input.get().clear();
			input.get().sendKeys(value);
			waitForUiToLoad();
		}
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		return waitForDisplayed(locator, timeout).isPresent();
	}

	private Optional<WebElement> waitForDisplayed(final By locator, final Duration timeout) {
		try {
			final WebDriverWait localWait = new WebDriverWait(driver, timeout);
			return Optional.ofNullable(localWait.until(driver -> {
				final List<WebElement> matches = driver.findElements(locator);
				for (final WebElement candidate : matches) {
					try {
						if (candidate.isDisplayed()) {
							return candidate;
						}
					} catch (final RuntimeException ex) {
						// Skip stale elements.
					}
				}
				return null;
			}));
		} catch (final RuntimeException ex) {
			return Optional.empty();
		}
	}

	private void waitForUiToLoad() {
		waitUntil(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")),
				DEFAULT_TIMEOUT);
		sleepSilently(900);
	}

	private void waitUntil(final ExpectedCondition<Boolean> condition, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(condition);
		} catch (final RuntimeException ex) {
			// Continue to allow test to gather full report.
		}
	}

	private void scrollTo(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		sleepSilently(200);
	}

	private Path captureScreenshot(final String checkpointName, final boolean fullPage) {
		try {
			final String fileName = String.format("%02d-%s.png", ++screenshotCounter, checkpointName);
			final Path destination = evidenceDirectory.resolve(fileName);

			Dimension originalSize = null;
			if (fullPage) {
				originalSize = driver.manage().window().getSize();
				final Long contentHeight = (Long) ((JavascriptExecutor) driver).executeScript(
						"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
				final int targetHeight = contentHeight == null ? 1080 : Math.min(contentHeight.intValue() + 200, 5000);
				driver.manage().window().setSize(new Dimension(1920, targetHeight));
				sleepSilently(300);
			}

			final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(destination, screenshot);

			if (originalSize != null) {
				driver.manage().window().setSize(originalSize);
				sleepSilently(200);
			}

			System.out.println("Saved screenshot: " + destination.toAbsolutePath());
			return destination;
		} catch (final IOException ex) {
			failures.add("Unable to write screenshot '" + checkpointName + "': " + ex.getMessage());
			return evidenceDirectory.resolve(checkpointName + ".png");
		}
	}

	private void writeFinalReportFile() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
		report.append("Evidence directory: ").append(evidenceDirectory.toAbsolutePath()).append(System.lineSeparator());
		report.append(System.lineSeparator());

		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL")
					.append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			report.append(System.lineSeparator());
			report.append("Final URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
				report.append("- ").append(legalUrl.getKey()).append(": ").append(legalUrl.getValue())
						.append(System.lineSeparator());
			}
		}

		if (!failures.isEmpty()) {
			report.append(System.lineSeparator());
			report.append("Failure details").append(System.lineSeparator());
			for (final String failure : failures) {
				report.append("- ").append(failure).append(System.lineSeparator());
			}
		}

		final Path reportFile = evidenceDirectory.resolve("final-report.txt");
		Files.writeString(reportFile, report.toString(), StandardCharsets.UTF_8);
		System.out.println(report);
		System.out.println("Saved final report: " + reportFile.toAbsolutePath());
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder concat = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int index = 0; index < parts.length; index++) {
			concat.append("'").append(parts[index]).append("'");
			if (index < parts.length - 1) {
				concat.append(", \"'\", ");
			}
		}
		concat.append(")");
		return concat.toString();
	}

	private String env(final String key) {
		return System.getenv(key);
	}

	private boolean parseHeadlessFlag() {
		final String flag = env("SALEADS_HEADLESS");
		if (flag == null) {
			return true;
		}
		return !("false".equalsIgnoreCase(flag) || "0".equals(flag) || "no".equalsIgnoreCase(flag));
	}

	private void sleepSilently(final long milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}
}
