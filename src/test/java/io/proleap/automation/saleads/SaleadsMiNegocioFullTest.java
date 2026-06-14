package io.proleap.automation.saleads;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String STEP_DETALLES = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private static final String TEXT_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String TEXT_PRIVACIDAD = "Pol\u00edtica de Privacidad";
	private static final String EMAIL_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private static final List<String> REPORT_FIELDS = List.of(
			STEP_LOGIN,
			STEP_MI_NEGOCIO_MENU,
			STEP_AGREGAR_MODAL,
			STEP_ADMIN_VIEW,
			STEP_INFO_GENERAL,
			STEP_DETALLES,
			STEP_TUS_NEGOCIOS,
			STEP_TERMINOS,
			STEP_PRIVACIDAD);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, StepResult> report = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String loginUrl = config("saleads.login.url", "SALEADS_LOGIN_URL", "");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL or -Dsaleads.login.url to run this external UI test.",
				!loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(config("saleads.headless", "SALEADS_HEADLESS", "true"));
		final ChromeOptions options = new ChromeOptions();

		if (headless) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		evidenceDir = Files.createDirectories(Paths.get("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final boolean loginPassed = executeStep(STEP_LOGIN, this::runLoginStep);
		final boolean menuPassed = loginPassed && executeStep(STEP_MI_NEGOCIO_MENU, this::runMiNegocioMenuStep);
		final boolean modalPassed = menuPassed && executeStep(STEP_AGREGAR_MODAL, this::runAgregarNegocioModalStep);
		final boolean administrarPassed = modalPassed && executeStep(STEP_ADMIN_VIEW, this::runAdministrarNegociosStep);
		final boolean infoPassed = administrarPassed && executeStep(STEP_INFO_GENERAL, this::runInformacionGeneralStep);
		final boolean detallesPassed = administrarPassed && executeStep(STEP_DETALLES, this::runDetallesCuentaStep);
		final boolean negociosPassed = administrarPassed && executeStep(STEP_TUS_NEGOCIOS, this::runTusNegociosStep);
		final boolean terminosPassed = administrarPassed && executeStep(STEP_TERMINOS, this::runTerminosStep);
		final boolean privacidadPassed = administrarPassed && executeStep(STEP_PRIVACIDAD, this::runPrivacidadStep);

		markBlockedIfMissing(menuPassed, STEP_MI_NEGOCIO_MENU, "Blocked because Login failed.");
		markBlockedIfMissing(modalPassed, STEP_AGREGAR_MODAL, "Blocked because Mi Negocio menu validation failed.");
		markBlockedIfMissing(administrarPassed, STEP_ADMIN_VIEW, "Blocked because Agregar Negocio modal validation failed.");
		markBlockedIfMissing(infoPassed, STEP_INFO_GENERAL, "Blocked because Administrar Negocios view failed.");
		markBlockedIfMissing(detallesPassed, STEP_DETALLES, "Blocked because Administrar Negocios view failed.");
		markBlockedIfMissing(negociosPassed, STEP_TUS_NEGOCIOS, "Blocked because Administrar Negocios view failed.");
		markBlockedIfMissing(terminosPassed, STEP_TERMINOS, "Blocked because Administrar Negocios view failed.");
		markBlockedIfMissing(privacidadPassed, STEP_PRIVACIDAD, "Blocked because Administrar Negocios view failed.");

		final String summary = buildReportSummary();
		System.out.println(summary);
		Assert.assertTrue("SaleADS Mi Negocio workflow failed.\n" + summary, allStepsPassed());
	}

	private String runLoginStep() throws IOException {
		final String appWindow = driver.getWindowHandle();
		clickByText("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google", "Google");
		waitForUiToLoad();
		selectGoogleAccountIfShown(appWindow);

		waitForSidebarVisible();
		final Path screenshot = takeScreenshot("01-dashboard-loaded");
		return "Dashboard and sidebar visible. Screenshot: " + screenshot;
	}

	private String runMiNegocioMenuStep() throws IOException {
		waitForSidebarVisible();
		clickIfVisibleText("Negocio");
		clickByText("Mi Negocio");

		requireTextVisible("Agregar Negocio");
		requireTextVisible("Administrar Negocios");

		final Path screenshot = takeScreenshot("02-mi-negocio-expanded-menu");
		return "Mi Negocio submenu expanded correctly. Screenshot: " + screenshot;
	}

	private String runAgregarNegocioModalStep() throws IOException {
		clickByText("Agregar Negocio");
		requireTextVisible("Crear Nuevo Negocio");
		requireTextVisible("Tienes 2 de 3 negocios");
		requireTextVisible("Cancelar");
		requireTextVisible("Crear Negocio");
		requireNegocioNameInput();

		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//label[normalize-space(.)='Nombre del Negocio']/following::input[1] | //input[@placeholder='Nombre del Negocio']")));
		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatizaci\u00f3n");
		clickByText("Cancelar");
		waitForUiToLoad();

		final Path screenshot = takeScreenshot("03-agregar-negocio-modal");
		return "Agregar Negocio modal validated and cancelled. Screenshot: " + screenshot;
	}

	private String runAdministrarNegociosStep() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickByText("Mi Negocio");
		}

		clickByText("Administrar Negocios");
		waitForUiToLoad();

		requireTextVisible("Informaci\u00f3n General");
		requireTextVisible("Detalles de la Cuenta");
		requireTextVisible("Tus Negocios");
		requireTextVisible("Secci\u00f3n Legal");

		final Path screenshot = takeScreenshot("04-administrar-negocios-view");
		return "Account page loaded with expected sections. Screenshot: " + screenshot;
	}

	private String runInformacionGeneralStep() {
		requireTextVisible("BUSINESS PLAN");
		requireTextVisible("Cambiar Plan");
		requireEmailVisible();
		requireUserNameVisible();
		return "Informaci\u00f3n General checks passed.";
	}

	private String runDetallesCuentaStep() {
		requireTextVisible("Cuenta creada");
		requireTextVisible("Estado activo");
		requireTextVisible("Idioma seleccionado");
		return "Detalles de la Cuenta checks passed.";
	}

	private String runTusNegociosStep() {
		requireTextVisible("Tus Negocios");
		requireTextVisible("Agregar Negocio");
		requireTextVisible("Tienes 2 de 3 negocios");
		return "Tus Negocios checks passed.";
	}

	private String runTerminosStep() throws IOException {
		return validateLegalLink(TEXT_TERMINOS, "05-terminos-condiciones");
	}

	private String runPrivacidadStep() throws IOException {
		return validateLegalLink(TEXT_PRIVACIDAD, "06-politica-privacidad");
	}

	private String validateLegalLink(final String linkText, final String screenshotName) throws IOException {
		final String appWindow = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> windowsBefore = driver.getWindowHandles();

		clickByText(linkText);
		waitForUiToLoad();

		wait.until(driver -> driver.getWindowHandles().size() > windowsBefore.size()
				|| !driver.getCurrentUrl().equals(originalUrl) || isTextVisible(linkText));

		boolean openedNewTab = false;
		if (driver.getWindowHandles().size() > windowsBefore.size()) {
			for (final String handle : driver.getWindowHandles()) {
				if (!windowsBefore.contains(handle)) {
					driver.switchTo().window(handle);
					openedNewTab = true;
					break;
				}
			}
		}

		waitForUiToLoad();
		requireTextVisible(linkText);
		requireLegalContentVisible();

		final String finalUrl = driver.getCurrentUrl();
		final Path screenshot = takeScreenshot(screenshotName);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
			wait.until(ExpectedConditions.or(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside")),
					ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(@class,'sidebar')]"))));
		}

		return "Validated '" + linkText + "'. URL: " + finalUrl + ". Screenshot: " + screenshot;
	}

	private void selectGoogleAccountIfShown(final String appWindow) {
		try {
			wait.until(driver -> driver.getWindowHandles().size() > 1 || isTextVisible(EMAIL_ACCOUNT)
					|| isTextVisible("Negocio") || isTextVisible("Mi Negocio"));

			if (driver.getWindowHandles().size() > 1) {
				for (final String handle : driver.getWindowHandles()) {
					if (!handle.equals(appWindow)) {
						driver.switchTo().window(handle);
						break;
					}
				}
			}

			if (isTextVisible(EMAIL_ACCOUNT)) {
				clickByText(EMAIL_ACCOUNT);
				waitForUiToLoad();
			}

			if (driver.getWindowHandles().contains(appWindow)) {
				driver.switchTo().window(appWindow);
			}
		} catch (final TimeoutException ignored) {
			// Some environments sign in automatically without showing the Google account picker.
		}
	}

	private void requireNegocioNameInput() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//label[normalize-space(.)='Nombre del Negocio']/following::input[1] | //input[@placeholder='Nombre del Negocio']")));
	}

	private void requireLegalContentVisible() {
		wait.until(driver -> {
			final List<WebElement> paragraphs = driver
					.findElements(By.xpath("//p[string-length(normalize-space(.)) > 30] | //article//*[self::p or self::li]"));
			for (final WebElement paragraph : paragraphs) {
				if (paragraph.isDisplayed()) {
					return true;
				}
			}
			return false;
		});
	}

	private void waitForSidebarVisible() {
		wait.until(ExpectedConditions.or(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside")),
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(@class,'sidebar')]")),
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//nav"))));
	}

	private void requireUserNameVisible() {
		wait.until(driver -> {
			final List<WebElement> candidates = driver.findElements(By.xpath(
					"//*[contains(@class,'user') or contains(@class,'name') or contains(@class,'profile')][string-length(normalize-space(.)) > 2]"));
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					return true;
				}
			}
			return false;
		});
	}

	private void requireEmailVisible() {
		wait.until(driver -> {
			final List<WebElement> emails = driver.findElements(By.xpath(
					"//*[contains(normalize-space(.), '@') and (contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '.com') or contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '.ai'))]"));
			for (final WebElement email : emails) {
				if (email.isDisplayed()) {
					return true;
				}
			}
			return false;
		});
	}

	private void requireTextVisible(final String text) {
		wait.until(driver -> {
			final List<WebElement> elements = driver
					.findElements(By.xpath("//*[normalize-space(.)=" + xpathLiteral(text) + " or contains(normalize-space(.), "
							+ xpathLiteral(text) + ")]"));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isTextVisible(final String text) {
		try {
			final List<WebElement> elements = driver
					.findElements(By.xpath("//*[normalize-space(.)=" + xpathLiteral(text) + " or contains(normalize-space(.), "
							+ xpathLiteral(text) + ")]"));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final NoSuchElementException ignored) {
			return false;
		}
	}

	private void clickByText(final String... visibleTexts) {
		final List<String> errors = new ArrayList<>();

		for (final String visibleText : visibleTexts) {
			try {
				final WebElement element = wait
						.until(ExpectedConditions.elementToBeClickable(By.xpath(clickableXpathByText(visibleText))));
				scrollIntoView(element);
				element.click();
				waitForUiToLoad();
				return;
			} catch (final Exception error) {
				errors.add(visibleText + ": " + error.getMessage());
			}
		}

		throw new AssertionError("Unable to click any target text: " + String.join(" | ", errors));
	}

	private void clickIfVisibleText(final String visibleText) {
		try {
			final List<WebElement> elements = driver.findElements(By.xpath(clickableXpathByText(visibleText)));
			for (final WebElement element : elements) {
				if (element.isDisplayed() && element.isEnabled()) {
					scrollIntoView(element);
					element.click();
					waitForUiToLoad();
					return;
				}
			}
		} catch (final Exception ignored) {
			// Optional click used only to open collapsible menus.
		}
	}

	private void waitForUiToLoad() {
		wait.until((ExpectedCondition<Boolean>) webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private Path takeScreenshot(final String checkpointName) throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
		final String safeName = checkpointName.toLowerCase().replaceAll("[^a-z0-9\\-]+", "-");
		final Path output = evidenceDir.resolve(timestamp + "-" + safeName + ".png");
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), output, StandardCopyOption.REPLACE_EXISTING);
		return output;
	}

	private String clickableXpathByText(final String text) {
		final String literal = xpathLiteral(text);
		return "(//*[self::button or self::a or @role='button'][normalize-space(.)=" + literal + "]"
				+ " | //*[normalize-space(.)=" + literal
				+ "]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"
				+ " | //*[self::span or self::div][normalize-space(.)=" + literal
				+ "]/ancestor::*[self::button or self::a or @role='button'][1])[1]";
	}

	private String config(final String propertyName, final String envName, final String fallback) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return fallback;
	}

	private boolean executeStep(final String stepName, final CheckedSupplier<String> stepBody) {
		try {
			final String details = stepBody.get();
			report.put(stepName, StepResult.pass(details));
			return true;
		} catch (final Throwable throwable) {
			report.put(stepName, StepResult.fail(throwable.getMessage()));
			return false;
		}
	}

	private void markBlockedIfMissing(final boolean alreadyCompleted, final String stepName, final String reason) {
		if (!alreadyCompleted && !report.containsKey(stepName)) {
			report.put(stepName, StepResult.fail(reason));
		}
	}

	private String buildReportSummary() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Final Report\n");
		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.getOrDefault(field, StepResult.fail("No result captured."));
			builder.append("- ").append(field).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (result.details != null && !result.details.isBlank()) {
				builder.append(" (").append(result.details).append(")");
			}
			builder.append('\n');
		}
		return builder.toString();
	}

	private boolean allStepsPassed() {
		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			if (result == null || !result.passed) {
				return false;
			}
		}
		return true;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'", -1);
		final StringJoiner joiner = new StringJoiner(", \"'\", ");
		for (final String part : parts) {
			joiner.add("'" + part + "'");
		}

		return "concat(" + joiner + ")";
	}

	private interface CheckedSupplier<T> {
		T get() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
