package io.proleap.saleads.e2e;

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
import org.openqa.selenium.Dimension;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SaleadsMiNegocioFullTest {

	private static final DateTimeFormatter EVIDENCE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_DETALLES = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Política de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Enable with -Dsaleads.e2e.enabled=true",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1200");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--disable-gpu");
		options.addArguments("--lang=es-ES");

		if (Boolean.parseBoolean(System.getProperty("saleads.headless", "true"))) {
			options.addArguments("--headless=new");
		}

		final String userDataDir = firstNonBlank(System.getProperty("saleads.chrome.userDataDir"),
				System.getenv("SALEADS_CHROME_USER_DATA_DIR"));
		if (userDataDir != null) {
			options.addArguments("--user-data-dir=" + userDataDir);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(readTimeoutSeconds()));

		evidenceDir = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(EVIDENCE_TIME_FORMAT));
		Files.createDirectories(evidenceDir);

		final String loginUrl = resolveLoginUrl();
		if (loginUrl != null) {
			driver.get(loginUrl);
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep(STEP_LOGIN, this::stepLoginWithGoogle);
		runStep(STEP_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(STEP_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(STEP_ADMINISTRAR_NEGOCIOS, this::stepOpenAdministrarNegocios);
		runStep(STEP_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(STEP_DETALLES, this::stepValidateDetallesCuenta);
		runStep(STEP_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(STEP_TERMINOS, this::stepValidateTerminosYCondiciones);
		runStep(STEP_PRIVACIDAD, this::stepValidatePoliticaDePrivacidad);

		printFinalReport();

		final List<String> failedSteps = stepResults.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(Map.Entry::getKey).collect(Collectors.toList());
		Assert.assertTrue("Workflow validation failed for steps: " + failedSteps, failedSteps.isEmpty());
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	private void stepLoginWithGoogle() {
		waitForPageReady();
		final WebElement loginButton = findFirstVisibleClickable(Arrays.asList("Sign in with Google", "Iniciar sesión con Google",
				"Iniciar sesion con Google", "Continuar con Google", "Login con Google", "Google"));
		if (loginButton != null) {
			clickAndWait(loginButton);
			selectGoogleAccountIfVisible(DEFAULT_GOOGLE_ACCOUNT);
		}

		Assert.assertTrue("Main application interface was not detected.", isAnyVisible(
				By.xpath("//*[contains(@class,'app') or contains(@class,'dashboard') or contains(@class,'layout') or @role='main']")));
		Assert.assertTrue("Left sidebar navigation is not visible.", isAnyVisible(By.xpath(
				"//aside | //nav[contains(@class,'sidebar')] | //nav[.//*[contains(normalize-space(.), 'Negocio') or contains(normalize-space(.), 'Mi Negocio')]]")));

		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		final WebElement negocioMenu = findFirstVisibleClickable(Arrays.asList("Negocio", "Mi Negocio"));
		Assert.assertNotNull("Menu 'Negocio' or 'Mi Negocio' was not found in sidebar.", negocioMenu);
		clickAndWait(negocioMenu);

		final WebElement miNegocioOption = findFirstVisibleClickable(Arrays.asList("Mi Negocio"));
		if (miNegocioOption != null) {
			clickAndWait(miNegocioOption);
		}

		Assert.assertTrue("Option 'Agregar Negocio' is not visible.", waitForVisibleText("Agregar Negocio"));
		Assert.assertTrue("Option 'Administrar Negocios' is not visible.", waitForVisibleText("Administrar Negocios"));
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		final WebElement addBusiness = findFirstVisibleClickable(Arrays.asList("Agregar Negocio"));
		Assert.assertNotNull("Button/menu 'Agregar Negocio' was not found.", addBusiness);
		clickAndWait(addBusiness);

		Assert.assertTrue("Modal title 'Crear Nuevo Negocio' is not visible.", waitForVisibleText("Crear Nuevo Negocio"));
		Assert.assertNotNull("Input 'Nombre del Negocio' was not found.", findBusinessNameInput());
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' is not visible.", waitForVisibleText("Tienes 2 de 3 negocios"));
		Assert.assertNotNull("Button 'Cancelar' is not visible.", findFirstVisibleClickable(Arrays.asList("Cancelar")));
		Assert.assertNotNull("Button 'Crear Negocio' is not visible.", findFirstVisibleClickable(Arrays.asList("Crear Negocio")));

		takeScreenshot("03-agregar-negocio-modal");

		final WebElement businessNameInput = findBusinessNameInput();
		if (businessNameInput != null) {
			businessNameInput.click();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		}

		final WebElement cancelButton = findFirstVisibleClickable(Arrays.asList("Cancelar"));
		if (cancelButton != null) {
			clickAndWait(cancelButton);
		}
	}

	private void stepOpenAdministrarNegocios() {
		ensureMiNegocioExpanded();
		final WebElement manageBusinesses = findFirstVisibleClickable(Arrays.asList("Administrar Negocios"));
		Assert.assertNotNull("Option 'Administrar Negocios' was not found.", manageBusinesses);
		clickAndWait(manageBusinesses);

		Assert.assertTrue("Section 'Información General' was not found.", waitForVisibleText("Información General"));
		Assert.assertTrue("Section 'Detalles de la Cuenta' was not found.", waitForVisibleText("Detalles de la Cuenta"));
		Assert.assertTrue("Section 'Tus Negocios' was not found.", waitForVisibleText("Tus Negocios"));
		Assert.assertTrue("Section 'Sección Legal' was not found.", waitForVisibleText("Sección Legal"));

		takeFullPageScreenshot("04-administrar-negocios-full");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement infoSection = waitForSection("Información General");
		Assert.assertNotNull("Section 'Información General' was not visible.", infoSection);

		Assert.assertTrue("User name is not visible in 'Información General'.",
				labelHasValue(infoSection, Arrays.asList("Nombre", "Nombre de usuario", "Usuario", "User name", "Name"))
						|| hasPotentialNameValue(infoSection));

		Assert.assertTrue("User email is not visible in 'Información General'.",
				containsText(infoSection, DEFAULT_GOOGLE_ACCOUNT) || hasEmailValue(infoSection));
		Assert.assertTrue("Text 'BUSINESS PLAN' is not visible in 'Información General'.", containsText(infoSection, "BUSINESS PLAN"));
		Assert.assertNotNull("Button 'Cambiar Plan' is not visible.",
				findFirstVisibleClickableInside(infoSection, Arrays.asList("Cambiar Plan")));
	}

	private void stepValidateDetallesCuenta() {
		final WebElement detailsSection = waitForSection("Detalles de la Cuenta");
		Assert.assertNotNull("Section 'Detalles de la Cuenta' was not visible.", detailsSection);
		Assert.assertTrue("'Cuenta creada' is not visible.", containsText(detailsSection, "Cuenta creada"));
		Assert.assertTrue("'Estado activo' is not visible.", containsText(detailsSection, "Estado activo"));
		Assert.assertTrue("'Idioma seleccionado' is not visible.", containsText(detailsSection, "Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement businessesSection = waitForSection("Tus Negocios");
		Assert.assertNotNull("Section 'Tus Negocios' was not visible.", businessesSection);
		Assert.assertTrue("Business list is not visible.", hasBusinessList(businessesSection));
		Assert.assertNotNull("Button 'Agregar Negocio' was not found in 'Tus Negocios'.",
				findFirstVisibleClickableInside(businessesSection, Arrays.asList("Agregar Negocio")));
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' was not found in 'Tus Negocios'.",
				containsText(businessesSection, "Tienes 2 de 3 negocios"));
	}

	private void stepValidateTerminosYCondiciones() {
		termsUrl = openAndValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "05-terminos-y-condiciones");
	}

	private void stepValidatePoliticaDePrivacidad() {
		privacyUrl = openAndValidateLegalLink("Política de Privacidad", "Política de Privacidad", "06-politica-de-privacidad");
	}

	private String openAndValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName) {
		final String appWindow = driver.getWindowHandle();
		final Set<String> windowsBefore = driver.getWindowHandles();

		WebElement legalLink = findFirstVisibleClickable(Arrays.asList(linkText));
		if (legalLink == null) {
			final WebElement legalSection = waitForSection("Sección Legal");
			Assert.assertNotNull("Section 'Sección Legal' was not visible.", legalSection);
			legalLink = findFirstVisibleClickableInside(legalSection, Arrays.asList(linkText));
		}
		Assert.assertNotNull("Legal link '" + linkText + "' was not found.", legalLink);
		clickAndWait(legalLink);

		final String legalWindow = waitForLegalWindowOrNavigation(windowsBefore, appWindow);
		final boolean switchedToNewTab = legalWindow != null && !legalWindow.equals(appWindow);
		if (switchedToNewTab) {
			driver.switchTo().window(legalWindow);
		}

		Assert.assertTrue("Heading '" + expectedHeading + "' is not visible.", waitForVisibleText(expectedHeading));
		Assert.assertTrue("Legal content text is not visible.",
				isAnyVisible(By.xpath("//p[string-length(normalize-space()) > 40] | //article//*[string-length(normalize-space()) > 40]")));
		takeScreenshot(screenshotName);

		final String currentLegalUrl = driver.getCurrentUrl();

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForPageReady();
		} else {
			driver.navigate().back();
			waitForPageReady();
		}

		return currentLegalUrl;
	}

	private void runStep(final String stepName, final CheckedAction action) {
		try {
			action.run();
			stepResults.put(stepName, StepResult.pass());
		} catch (final Throwable error) {
			final String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
			stepResults.put(stepName, StepResult.fail(message));
			takeScreenshot("failed-" + normalizeFileName(stepName));
		}
	}

	private void ensureMiNegocioExpanded() {
		if (isAnyVisible(By.xpath("//*[normalize-space()='Administrar Negocios']"))) {
			return;
		}
		final WebElement miNegocio = findFirstVisibleClickable(Arrays.asList("Mi Negocio", "Negocio"));
		if (miNegocio != null) {
			clickAndWait(miNegocio);
		}
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		waitForPageReady();

		final Set<String> windows = driver.getWindowHandles();
		if (!windows.isEmpty()) {
			final String currentWindow = driver.getWindowHandle();
			for (final String window : windows) {
				driver.switchTo().window(window);
				if (isAnyVisible(By.xpath("//*[normalize-space()='" + accountEmail + "']"))) {
					final WebElement account = wait.until(
							ExpectedConditions.elementToBeClickable(By.xpath("//*[normalize-space()='" + accountEmail + "']")));
					clickAndWait(account);
					driver.switchTo().window(currentWindow);
					return;
				}
			}
			driver.switchTo().window(currentWindow);
		}
	}

	private long readTimeoutSeconds() {
		final String timeoutText = System.getProperty("saleads.timeout.seconds", "30");
		return Long.parseLong(timeoutText);
	}

	private String resolveLoginUrl() {
		return firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"),
				System.getenv("SALEADS_BASE_URL"), System.getenv("BASE_URL"), System.getenv("APP_URL"));
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private void waitForPageReady() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForPageReady();
		waitForUiSettled();
	}

	private void waitForUiSettled() {
		try {
			Thread.sleep(500);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean waitForVisibleText(final String text) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[normalize-space()='" + text + "' or contains(normalize-space(),'" + text + "')]")));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private WebElement findFirstVisibleClickable(final List<String> texts) {
		for (final String text : texts) {
			final WebElement insideButton = findVisible(By.xpath(
					"(//button[normalize-space()='" + text + "' or contains(normalize-space(),'" + text + "')]"
							+ " | //a[normalize-space()='" + text + "' or contains(normalize-space(),'" + text + "')]"
							+ " | //*[@role='button' and (normalize-space()='" + text + "' or contains(normalize-space(),'" + text + "'))]"
							+ " | //*[self::span or self::div][normalize-space()='" + text + "' or contains(normalize-space(),'" + text
							+ "')]/ancestor::*[self::button or self::a or @role='button'][1])[1]"));
			if (insideButton != null) {
				return insideButton;
			}
		}
		return null;
	}

	private WebElement findFirstVisibleClickableInside(final WebElement root, final List<String> texts) {
		for (final String text : texts) {
			final List<By> locators = Arrays.asList(
					By.xpath(".//button[normalize-space()='" + text + "' or contains(normalize-space(),'" + text + "')]"),
					By.xpath(".//a[normalize-space()='" + text + "' or contains(normalize-space(),'" + text + "')]"),
					By.xpath(".//*[@role='button' and (normalize-space()='" + text + "' or contains(normalize-space(),'" + text + "'))]"));
			for (final By locator : locators) {
				for (final WebElement candidate : root.findElements(locator)) {
					if (candidate.isDisplayed()) {
						return candidate;
					}
				}
			}
		}
		return null;
	}

	private WebElement findVisible(final By locator) {
		try {
			final List<WebElement> elements = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(locator));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private boolean isAnyVisible(final By locator) {
		return findVisible(locator) != null;
	}

	private WebElement findBusinessNameInput() {
		final List<By> locators = Arrays.asList(
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"));
		for (final By locator : locators) {
			final WebElement input = findVisible(locator);
			if (input != null) {
				return input;
			}
		}
		return null;
	}

	private WebElement waitForSection(final String title) {
		final List<By> locators = Arrays.asList(
				By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][normalize-space()='" + title
						+ "']/ancestor::*[self::section or self::div][1]"),
				By.xpath("//*[normalize-space()='" + title + "']/ancestor::*[self::section or self::div][1]"));
		for (final By locator : locators) {
			final WebElement section = findVisible(locator);
			if (section != null) {
				return section;
			}
		}
		return null;
	}

	private boolean containsText(final WebElement section, final String text) {
		for (final WebElement element : section.findElements(By.xpath(".//*[contains(normalize-space(),'" + text + "')]"))) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean labelHasValue(final WebElement section, final List<String> labels) {
		for (final String label : labels) {
			final List<WebElement> labelElements = section.findElements(
					By.xpath(".//*[normalize-space()='" + label + "' or contains(normalize-space(),'" + label + "')]"));
			for (final WebElement labelElement : labelElements) {
				if (!labelElement.isDisplayed()) {
					continue;
				}
				final List<WebElement> candidateValues = new ArrayList<>();
				candidateValues.addAll(labelElement.findElements(By.xpath("./following-sibling::*[normalize-space()]")));
				candidateValues.addAll(labelElement.findElements(By.xpath("./parent::*/*[normalize-space()]")));

				for (final WebElement candidate : candidateValues) {
					final String candidateText = candidate.getText().trim();
					if (!candidate.isDisplayed() || candidateText.isEmpty()) {
						continue;
					}
					final String normalizedCandidate = candidateText.toLowerCase(Locale.ROOT);
					final String normalizedLabel = label.toLowerCase(Locale.ROOT);
					if (!normalizedCandidate.equals(normalizedLabel) && !normalizedCandidate.contains(normalizedLabel + ":")) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean hasPotentialNameValue(final WebElement section) {
		for (final WebElement candidate : section.findElements(By.xpath(".//*[normalize-space()]"))) {
			if (!candidate.isDisplayed()) {
				continue;
			}
			final String text = candidate.getText().trim();
			if (text.matches("[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}(\\s+[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}){1,3}")) {
				return true;
			}
		}
		return false;
	}

	private boolean hasEmailValue(final WebElement section) {
		for (final WebElement candidate : section.findElements(By.xpath(".//*[contains(text(),'@')]"))) {
			if (!candidate.isDisplayed()) {
				continue;
			}
			final String text = candidate.getText().trim();
			if (text.matches(".*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*")) {
				return true;
			}
		}
		return false;
	}

	private boolean hasBusinessList(final WebElement section) {
		final List<By> locators = Arrays.asList(
				By.xpath(".//ul/li[normalize-space()]"),
				By.xpath(".//table//tr[.//td]"),
				By.xpath(".//*[contains(@class,'card') and normalize-space()]"),
				By.xpath(".//*[contains(@class,'business') and normalize-space()]"));
		for (final By locator : locators) {
			for (final WebElement element : section.findElements(locator)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private String waitForLegalWindowOrNavigation(final Set<String> windowsBefore, final String appWindow) {
		final ExpectedCondition<Boolean> newWindowOpened = driver -> driver.getWindowHandles().size() > windowsBefore.size();
		try {
			wait.until(newWindowOpened);
			for (final String window : driver.getWindowHandles()) {
				if (!windowsBefore.contains(window)) {
					return window;
				}
			}
		} catch (final TimeoutException timeoutException) {
			// Navigation happened in same tab.
		}
		return appWindow;
	}

	private void takeScreenshot(final String fileNamePrefix) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		try {
			final Path targetFile = evidenceDir.resolve(fileNamePrefix + ".png");
			final Path sourceFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException ioException) {
			System.err.println("Could not write screenshot '" + fileNamePrefix + "': " + ioException.getMessage());
		}
	}

	private void takeFullPageScreenshot(final String fileNamePrefix) {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final Long fullHeight = (Long) ((JavascriptExecutor) driver).executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight,"
							+ "document.body.offsetHeight, document.documentElement.offsetHeight,"
							+ "document.body.clientHeight, document.documentElement.clientHeight);");
			if (fullHeight != null && fullHeight > 0) {
				final int boundedHeight = (int) Math.min(fullHeight + 100, 12000);
				driver.manage().window().setSize(new Dimension(originalSize.getWidth(), boundedHeight));
				waitForUiSettled();
			}
			takeScreenshot(fileNamePrefix);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiSettled();
		}
	}

	private String normalizeFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replace(' ', '-').replace("ó", "o").replace("í", "i").replace("é", "e")
				.replace("á", "a").replace("ú", "u").replace("ñ", "n");
	}

	private void printFinalReport() {
		System.out.println("----- SaleADS Mi Negocio Full Test Report -----");
		for (final Map.Entry<String, StepResult> step : stepResults.entrySet()) {
			final String status = step.getValue().passed ? "PASS" : "FAIL";
			final String details = step.getValue().details == null ? "" : " - " + step.getValue().details;
			System.out.println(step.getKey() + ": " + status + details);
		}
		System.out.println("Términos y Condiciones URL: " + termsUrl);
		System.out.println("Política de Privacidad URL: " + privacyUrl);
		System.out.println("Evidence path: " + evidenceDir.toAbsolutePath());
		System.out.println("------------------------------------------------");
	}

	@FunctionalInterface
	private interface CheckedAction {
		void run();
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, null);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
