package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String RUN_FLAG = "runSaleadsE2E";
	private static final String LOGIN_URL_PROPERTY = "saleads.login.url";
	private static final String HEADLESS_PROPERTY = "saleads.e2e.headless";
	private static final String SCREENSHOT_DIR_PROPERTY = "saleads.e2e.screenshotsDir";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Información General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS_CONDICIONES = "Términos y Condiciones";
	private static final String POLITICA_PRIVACIDAD = "Política de Privacidad";

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private int screenshotCounter = 1;

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue("Enable this E2E using -D" + RUN_FLAG + "=true",
				Boolean.parseBoolean(System.getProperty(RUN_FLAG, "false")));

		final String loginUrl = System.getProperty(LOGIN_URL_PROPERTY, "").trim();
		Assume.assumeTrue(
				"Provide the current environment login URL with -D" + LOGIN_URL_PROPERTY + "=https://<environment>/login",
				!loginUrl.isEmpty());

		initializeReport();

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(System.getProperty(HEADLESS_PROPERTY, "false"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1));

		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final String baseScreenshotPath = System.getProperty(SCREENSHOT_DIR_PROPERTY, "target/saleads-evidence");
		screenshotDir = Paths.get(baseScreenshotPath).resolve(timestamp);
		Files.createDirectories(screenshotDir);

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final boolean loginOk = runStep(LOGIN, this::stepLoginWithGoogle);
		final boolean menuOk = runStep(MI_NEGOCIO_MENU, () -> {
			require(loginOk, "Login step must pass before opening Mi Negocio.");
			return stepOpenMiNegocioMenu();
		});
		runStep(AGREGAR_NEGOCIO_MODAL, () -> {
			require(menuOk, "Mi Negocio menu step must pass before opening modal.");
			return stepValidateAgregarNegocioModal();
		});
		final boolean administrarViewOk = runStep(ADMINISTRAR_NEGOCIOS_VIEW, () -> {
			require(menuOk, "Mi Negocio menu step must pass before opening account view.");
			return stepOpenAdministrarNegocios();
		});

		runStep(INFORMACION_GENERAL, () -> {
			require(administrarViewOk, "Administrar Negocios view must be visible.");
			return stepValidateInformacionGeneral();
		});
		runStep(DETALLES_CUENTA, () -> {
			require(administrarViewOk, "Administrar Negocios view must be visible.");
			return stepValidateDetallesCuenta();
		});
		runStep(TUS_NEGOCIOS, () -> {
			require(administrarViewOk, "Administrar Negocios view must be visible.");
			return stepValidateTusNegocios();
		});
		final boolean termsOk = runStep(TERMINOS_CONDICIONES, () -> {
			require(administrarViewOk, "Administrar Negocios view must be visible.");
			return stepValidateLegalLink("Términos y Condiciones", "08-terminos-condiciones");
		});
		runStep(POLITICA_PRIVACIDAD, () -> {
			require(administrarViewOk, "Administrar Negocios view must be visible.");
			require(termsOk, "Términos y Condiciones step must pass before validating Política de Privacidad.");
			return stepValidateLegalLink("Política de Privacidad", "09-politica-privacidad");
		});

		final String finalReport = buildReport();
		System.out.println(finalReport);

		assertTrue("One or more steps failed.\n" + finalReport,
				report.values().stream().allMatch(stepResult -> "PASS".equals(stepResult.status)));
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	private String stepLoginWithGoogle() throws Exception {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickAndWait(clickableByText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"));
		switchToNewWindowIfPresent(handlesBefore);
		tryChooseGoogleAccount();
		returnToAppWindowIfNeeded(appWindow);
		waitForMainApplication();

		final String screenshot = captureScreenshot("01-dashboard-loaded");
		return "Dashboard and sidebar visible. Screenshot: " + screenshot;
	}

	private String stepOpenMiNegocioMenu() throws Exception {
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Negocio')]"));
		clickAndWait(clickableByText("Mi Negocio"));

		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Agregar Negocio')]"));
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Administrar Negocios')]"));

		final String screenshot = captureScreenshot("02-mi-negocio-menu-expanded");
		return "Mi Negocio expanded with submenu options. Screenshot: " + screenshot;
	}

	private String stepValidateAgregarNegocioModal() throws Exception {
		clickAndWait(clickableByText("Agregar Negocio"));
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Crear Nuevo Negocio')]"));
		waitForVisible(By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]"
				+ "|//input[contains(@placeholder,'Nombre del Negocio')]"));
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Tienes 2 de 3 negocios')]"));
		waitForVisible(clickableByText("Cancelar"));
		waitForVisible(clickableByText("Crear Negocio"));

		final String screenshot = captureScreenshot("03-agregar-negocio-modal");

		final WebElement nombreInput = firstVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio')] | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));
		nombreInput.click();
		nombreInput.sendKeys(Keys.chord(Keys.CONTROL, "a"), "Negocio Prueba Automatización");
		clickAndWait(clickableByText("Cancelar"));
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.),'Crear Nuevo Negocio')]")));

		return "Agregar Negocio modal validated and closed. Screenshot: " + screenshot;
	}

	private String stepOpenAdministrarNegocios() throws Exception {
		if (!isVisible(By.xpath("//*[contains(normalize-space(.),'Administrar Negocios')]"))) {
			clickAndWait(clickableByText("Mi Negocio"));
		}

		clickAndWait(clickableByText("Administrar Negocios"));
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Información General')]"));
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Detalles de la Cuenta')]"));
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Tus Negocios')]"));
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Sección Legal')]"));

		final String screenshot = captureScreenshot("04-administrar-negocios-page");
		return "Account page sections are visible. Screenshot: " + screenshot;
	}

	private String stepValidateInformacionGeneral() {
		final WebElement section = sectionFromHeading("Información General");
		final String sectionText = section.getText();

		require(sectionText.contains("@"), "User email is not visible in Información General.");
		require(sectionText.contains("BUSINESS PLAN"), "BUSINESS PLAN text is not visible.");
		require(findVisibleInside(section, clickableByText("Cambiar Plan")) != null, "Cambiar Plan button is not visible.");
		require(hasVisibleUserName(sectionText), "User name is not visible in Información General.");

		return "Información General validated.";
	}

	private String stepValidateDetallesCuenta() {
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Cuenta creada')]"));
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Estado activo')]"));
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Idioma seleccionado')]"));

		return "Detalles de la Cuenta validated.";
	}

	private String stepValidateTusNegocios() {
		final WebElement section = sectionFromHeading("Tus Negocios");
		final String sectionText = section.getText();

		require(sectionText.contains("Tienes 2 de 3 negocios"), "Business usage text is missing.");
		require(findVisibleInside(section, clickableByText("Agregar Negocio")) != null,
				"Agregar Negocio button is not visible in Tus Negocios.");
		require(hasBusinessList(section), "Business list is not visible.");

		return "Tus Negocios validated.";
	}

	private String stepValidateLegalLink(final String linkText, final String screenshotLabel) throws Exception {
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Sección Legal')]"));
		final String appWindow = driver.getWindowHandle();
		final String appUrlBefore = driver.getCurrentUrl();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickAndWait(clickableByText(linkText));
		wait.until(d -> d.getWindowHandles().size() > handlesBefore.size() || !driver.getCurrentUrl().equals(appUrlBefore));

		String navigationType = "same-tab";
		final Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > handlesBefore.size()) {
			navigationType = "new-tab";
			for (final String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForUiToLoad();
		}
		require(!driver.getCurrentUrl().equals(appUrlBefore) || "new-tab".equals(navigationType),
				"Legal page did not navigate to a new tab or URL.");

		waitForVisible(By.xpath("//h1[contains(normalize-space(.),'" + linkText + "')]"
				+ "|//h2[contains(normalize-space(.),'" + linkText + "')]"
				+ "|//h3[contains(normalize-space(.),'" + linkText + "')]"));
		final String legalText = driver.findElement(By.tagName("body")).getText();
		require(legalText.length() > 120, "Legal page content looks incomplete.");

		final String finalUrl = driver.getCurrentUrl();
		final String screenshot = captureScreenshot(screenshotLabel);

		if ("new-tab".equals(navigationType)) {
			driver.close();
			driver.switchTo().window(appWindow);
		} else if (!finalUrl.equals(appUrlBefore)) {
			driver.navigate().back();
		}
		waitForUiToLoad();
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Sección Legal')]"));

		return linkText + " validated. URL: " + finalUrl + ". Screenshot: " + screenshot;
	}

	private void tryChooseGoogleAccount() {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
			final WebElement accountOption = shortWait.until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[contains(normalize-space(.),'" + GOOGLE_ACCOUNT_EMAIL + "')]"));
			clickElement(accountOption);
			waitForUiToLoad();
		} catch (final TimeoutException ignored) {
			// No account selector appeared, which can happen with active sessions.
		}
	}

	private void switchToNewWindowIfPresent(final Set<String> handlesBefore) {
		final Set<String> handlesAfterClick = driver.getWindowHandles();
		if (handlesAfterClick.size() <= handlesBefore.size()) {
			return;
		}

		for (final String handle : handlesAfterClick) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void returnToAppWindowIfNeeded(final String appWindow) {
		if (driver.getWindowHandle().equals(appWindow)) {
			return;
		}

		wait.until(d -> d.getWindowHandles().contains(appWindow));
		driver.switchTo().window(appWindow);
		waitForUiToLoad();
	}

	private void waitForMainApplication() {
		waitForVisible(By.xpath("//aside|//nav"));
		waitForVisible(By.xpath("//*[contains(normalize-space(.),'Negocio') or contains(normalize-space(.),'Mi Negocio')]"));
	}

	private void clickAndWait(final By locator) {
		final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
		clickElement(element);
		waitForUiToLoad();
	}

	private void clickElement(final WebElement element) {
		try {
			scrollIntoView(element);
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final RuntimeException runtimeException) {
			try {
				new Actions(driver).moveToElement(element).click().perform();
			} catch (final RuntimeException actionException) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			}
		}
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(400);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private By clickableByText(final String... texts) {
		final String joinedConditions = Arrays.stream(texts).map(text -> "contains(normalize-space(.),'" + text + "')")
				.collect(Collectors.joining(" or "));
		final String xpath = "(//button[" + joinedConditions + "]"
				+ "|//a[" + joinedConditions + "]"
				+ "|//*[@role='button' and (" + joinedConditions + ")]"
				+ "|//span[" + joinedConditions + "]/ancestor::button[1]"
				+ "|//span[" + joinedConditions + "]/ancestor::a[1]"
				+ "|//*[self::div or self::li][" + joinedConditions + " and (@role='button' or @tabindex='0')])[1]";
		return By.xpath(xpath);
	}

	private WebElement sectionFromHeading(final String headingText) {
		final WebElement heading = waitForVisible(By.xpath("//*[contains(normalize-space(.),'" + headingText + "')]"));
		return heading.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private WebElement firstVisible(final By locator) {
		return wait.until(d -> {
			final List<WebElement> matches = d.findElements(locator);
			for (final WebElement match : matches) {
				try {
					if (match.isDisplayed()) {
						return match;
					}
				} catch (final StaleElementReferenceException ignored) {
					// Retry loop handles stale references transparently.
				}
			}
			return null;
		});
	}

	private WebElement findVisibleInside(final WebElement container, final By locator) {
		try {
			final List<WebElement> matches = container.findElements(locator);
			for (final WebElement match : matches) {
				if (match.isDisplayed()) {
					return match;
				}
			}
			return null;
		} catch (final NoSuchElementException ignored) {
			return null;
		}
	}

	private boolean isVisible(final By locator) {
		try {
			return !driver.findElements(locator).isEmpty() && driver.findElements(locator).stream().anyMatch(WebElement::isDisplayed);
		} catch (final RuntimeException exception) {
			return false;
		}
	}

	private boolean hasVisibleUserName(final String sectionText) {
		return Arrays.stream(sectionText.split("\\R")).map(String::trim).filter(value -> !value.isEmpty())
				.filter(value -> !value.contains("@")).filter(value -> !"Información General".equalsIgnoreCase(value))
				.filter(value -> !"BUSINESS PLAN".equalsIgnoreCase(value)).filter(value -> !"Cambiar Plan".equalsIgnoreCase(value))
				.filter(value -> !value.toLowerCase().contains("plan")).anyMatch(value -> value.length() >= 3);
	}

	private boolean hasBusinessList(final WebElement tusNegociosSection) {
		final List<WebElement> listCandidates = tusNegociosSection
				.findElements(By.xpath(".//li | .//table//tr | .//*[@data-testid='business-item'] | .//*[contains(@class,'business-item')]"));
		if (!listCandidates.isEmpty()) {
			return listCandidates.stream().anyMatch(WebElement::isDisplayed);
		}

		final String sectionText = tusNegociosSection.getText();
		return Arrays.stream(sectionText.split("\\R")).map(String::trim).filter(value -> !value.isEmpty())
				.filter(value -> !"Tus Negocios".equalsIgnoreCase(value)).filter(value -> !"Agregar Negocio".equalsIgnoreCase(value))
				.filter(value -> !"Tienes 2 de 3 negocios".equalsIgnoreCase(value)).count() >= 1;
	}

	private String captureScreenshot(final String label) throws IOException {
		final String normalizedName = label.toLowerCase().replaceAll("[^a-z0-9\\-]+", "-").replaceAll("\\-+", "-");
		final String fileName = String.format("%02d-%s.png", screenshotCounter++, normalizedName);
		final Path destination = screenshotDir.resolve(fileName);
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		return destination.toString();
	}

	private boolean runStep(final String reportKey, final StepAction action) {
		try {
			final String details = action.run();
			report.put(reportKey, StepResult.pass(details));
			return true;
		} catch (final Exception exception) {
			final String screenshot;
			try {
				screenshot = captureScreenshot("failure-" + reportKey.toLowerCase().replaceAll("[^a-z0-9]+", "-"));
			} catch (final Exception screenshotException) {
				report.put(reportKey, StepResult.fail(exception.getMessage()));
				return false;
			}
			final String details = exception.getMessage() + " | Screenshot: " + screenshot;
			report.put(reportKey, StepResult.fail(details));
			return false;
		}
	}

	private void require(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private void initializeReport() {
		report.put(LOGIN, StepResult.pending());
		report.put(MI_NEGOCIO_MENU, StepResult.pending());
		report.put(AGREGAR_NEGOCIO_MODAL, StepResult.pending());
		report.put(ADMINISTRAR_NEGOCIOS_VIEW, StepResult.pending());
		report.put(INFORMACION_GENERAL, StepResult.pending());
		report.put(DETALLES_CUENTA, StepResult.pending());
		report.put(TUS_NEGOCIOS, StepResult.pending());
		report.put(TERMINOS_CONDICIONES, StepResult.pending());
		report.put(POLITICA_PRIVACIDAD, StepResult.pending());
	}

	private String buildReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio full workflow report").append(System.lineSeparator());
		builder.append("Evidence directory: ").append(screenshotDir).append(System.lineSeparator());
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().status);
			if (entry.getValue().details != null && !entry.getValue().details.isBlank()) {
				builder.append(" | ").append(entry.getValue().details);
			}
			builder.append(System.lineSeparator());
		}
		return builder.toString();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private interface StepAction {
		String run() throws Exception;
	}

	private static final class StepResult {
		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult("PASS", details);
		}

		private static StepResult fail(final String details) {
			return new StepResult("FAIL", details);
		}

		private static StepResult pending() {
			return new StepResult("FAIL", "Not executed.");
		}
	}
}
