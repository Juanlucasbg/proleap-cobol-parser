package io.proleap.qa.saleads;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜ";
	private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyzáéíóúü";
	private static final String TEST_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String LOGIN_FIELD = "Login";
	private static final String MENU_FIELD = "Mi Negocio menu";
	private static final String MODAL_FIELD = "Agregar Negocio modal";
	private static final String ADMIN_VIEW_FIELD = "Administrar Negocios view";
	private static final String INFO_FIELD = "Información General";
	private static final String DETAILS_FIELD = "Detalles de la Cuenta";
	private static final String BUSINESSES_FIELD = "Tus Negocios";
	private static final String TERMS_FIELD = "Términos y Condiciones";
	private static final String PRIVACY_FIELD = "Política de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;
	private String appWindowHandle;
	private final Map<String, StepResult> results = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		artifactsDir = createArtifactsDir();

		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", null);
		if (loginUrl == null || loginUrl.isBlank()) {
			throw new IllegalStateException(
					"Missing SaleADS login URL. Provide -Dsaleads.login.url or SALEADS_LOGIN_URL.");
		}

		driver.get(loginUrl);
		appWindowHandle = driver.getWindowHandle();
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
		runStep(LOGIN_FIELD, this::stepLoginWithGoogle);
		runStep(MENU_FIELD, this::stepOpenMiNegocioMenu);
		runStep(MODAL_FIELD, this::stepValidateAgregarNegocioModal);
		runStep(ADMIN_VIEW_FIELD, this::stepOpenAdministrarNegocios);
		runStep(INFO_FIELD, this::stepValidateInformacionGeneral);
		runStep(DETAILS_FIELD, this::stepValidateDetallesCuenta);
		runStep(BUSINESSES_FIELD, this::stepValidateTusNegocios);
		runStep(TERMS_FIELD, () -> stepValidateLegalDocument("Términos y Condiciones", "Términos y Condiciones",
				"05-terminos-y-condiciones.png"));
		runStep(PRIVACY_FIELD,
				() -> stepValidateLegalDocument("Política de Privacidad", "Política de Privacidad",
						"06-politica-de-privacidad.png"));

		printFinalReport();

		final List<String> failedSteps = results.entrySet().stream()
				.filter(entry -> !entry.getValue().passed)
				.map(entry -> entry.getKey() + ": " + entry.getValue().detail)
				.collect(Collectors.toList());

		if (!failedSteps.isEmpty()) {
			Assert.fail("One or more validations failed:\n" + String.join("\n", failedSteps));
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickByTextContains("google");
		switchToNewWindowIfOpened(handlesBeforeClick);
		selectGoogleAccountIfPrompted();

		assertVisibleText("Negocio");
		assertVisibleElement(By.xpath("//aside | //nav"), "Left sidebar navigation should be visible");
		takeScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByTextContains("Negocio");
		clickByTextContains("Mi Negocio");

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByTextContains("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal.png");

		final WebElement businessNameInput = findVisibleElement(By.xpath(
				"//input[contains(translate(@placeholder,'" + UPPERCASE + "','" + LOWERCASE + "'),'nombre del negocio')]"
						+ " | //label[contains(translate(normalize-space(.),'" + UPPERCASE + "','" + LOWERCASE
						+ "'),'nombre del negocio')]/following::input[1]"));
		if (businessNameInput != null) {
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatización");
			waitForUiToLoad();
		}

		clickByTextContains("Cancelar");
		waitForTextToDisappear("Crear Nuevo Negocio");
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (findVisibleElement(byTextContains("Administrar Negocios")) == null) {
			clickByTextContains("Mi Negocio");
		}

		clickByTextContains("Administrar Negocios");
		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		takeFullPageScreenshot("04-administrar-negocios-page.png");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		Assert.assertNotNull("Section 'Información General' should exist.", section);

		final List<String> lines = visibleNonEmptyLines(section.getText());
		final boolean hasEmail = lines.stream().anyMatch(line -> line.contains("@"));
		final boolean hasBusinessPlan = containsLine(lines, "BUSINESS PLAN");
		final boolean hasCambiarPlan = containsLine(lines, "Cambiar Plan");
		final boolean hasUserName = lines.stream().anyMatch(this::looksLikeUserNameLine);

		Assert.assertTrue("User email should be visible in Información General.", hasEmail);
		Assert.assertTrue("BUSINESS PLAN should be visible.", hasBusinessPlan);
		Assert.assertTrue("Cambiar Plan button should be visible.", hasCambiarPlan);
		Assert.assertTrue("User name should be visible in Información General.", hasUserName);
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		Assert.assertNotNull("Section 'Detalles de la Cuenta' should exist.", section);

		final String sectionText = normalize(section.getText());
		Assert.assertTrue("'Cuenta creada' should be visible.", sectionText.contains(normalize("Cuenta creada")));
		Assert.assertTrue("'Estado activo' should be visible.", sectionText.contains(normalize("Estado activo")));
		Assert.assertTrue("'Idioma seleccionado' should be visible.",
				sectionText.contains(normalize("Idioma seleccionado")));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		Assert.assertNotNull("Section 'Tus Negocios' should exist.", section);

		final String sectionText = normalize(section.getText());
		Assert.assertTrue("'Agregar Negocio' should exist in Tus Negocios.",
				sectionText.contains(normalize("Agregar Negocio")));
		Assert.assertTrue("'Tienes 2 de 3 negocios' should be visible.",
				sectionText.contains(normalize("Tienes 2 de 3 negocios")));

		final List<WebElement> listCandidates = section.findElements(By.xpath(".//li[normalize-space()]"
				+ " | .//table//tr[td] | .//div[contains(@class,'business') and normalize-space()]"));
		final List<String> lines = visibleNonEmptyLines(section.getText());
		final boolean hasList = !listCandidates.isEmpty() || lines.size() >= 4;
		Assert.assertTrue("Business list should be visible in Tus Negocios.", hasList);
	}

	private void stepValidateLegalDocument(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final String applicationHandle = appWindowHandle;
		final String previousUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByTextContains(linkText);
		final Optional<String> legalTabHandle = getNewlyOpenedHandle(handlesBeforeClick);
		if (legalTabHandle.isPresent()) {
			driver.switchTo().window(legalTabHandle.get());
			waitForUiToLoad();
		}

		assertVisibleText(headingText);
		assertLegalContentVisible();
		takeScreenshot(screenshotName);
		legalUrls.put(headingText, driver.getCurrentUrl());

		if (legalTabHandle.isPresent()) {
			driver.close();
			driver.switchTo().window(applicationHandle);
			waitForUiToLoad();
		} else if (!driver.getCurrentUrl().equals(previousUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			results.put(reportField, StepResult.pass());
		} catch (final Throwable throwable) {
			results.put(reportField, StepResult.fail(throwable.getMessage()));
		}
	}

	private void clickByTextContains(final String text) {
		final WebElement element = waitForVisibleElement(byTextContains(text));
		Assert.assertNotNull("Could not find clickable element containing text: " + text, element);
		scrollIntoView(element);

		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiToLoad();
	}

	private WebElement findSectionByHeading(final String headingText) {
		return findVisibleElement(By.xpath("//section[.//*[contains(normalize-space(.)," + xpathLiteral(headingText)
				+ ")]] | //div[.//*[contains(normalize-space(.)," + xpathLiteral(headingText) + ")] and descendant::*]"));
	}

	private void assertVisibleText(final String text) {
		final WebElement element = waitForVisibleElement(byTextContains(text));
		Assert.assertNotNull("Expected to find visible text: " + text, element);
	}

	private void assertVisibleElement(final By by, final String message) {
		final WebElement element = waitForVisibleElement(by);
		Assert.assertNotNull(message, element);
	}

	private void assertLegalContentVisible() {
		final WebElement body = waitForVisibleElement(By.tagName("body"));
		Assert.assertNotNull("Legal page body should be visible.", body);

		final String text = body.getText().trim();
		Assert.assertTrue("Legal content text should be visible.", text.length() > 120);
	}

	private void switchToNewWindowIfOpened(final Set<String> handlesBeforeClick) {
		final Optional<String> newHandle = getNewlyOpenedHandle(handlesBeforeClick);
		if (newHandle.isPresent()) {
			driver.switchTo().window(newHandle.get());
			waitForUiToLoad();
		}
	}

	private Optional<String> getNewlyOpenedHandle(final Set<String> handlesBeforeClick) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size());
		} catch (final TimeoutException ignored) {
			return Optional.empty();
		}

		return driver.getWindowHandles().stream()
				.filter(handle -> !handlesBeforeClick.contains(handle))
				.findFirst();
	}

	private void selectGoogleAccountIfPrompted() {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
			final WebElement accountElement = shortWait
					.until(d -> findVisibleElement(By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(TEST_ACCOUNT_EMAIL)
							+ ")]")));
			if (accountElement != null) {
				scrollIntoView(accountElement);
				accountElement.click();
				waitForUiToLoad();
			}
		} catch (final TimeoutException ignored) {
			// Account chooser did not appear. This is acceptable when the session is already authenticated.
		}

		if (!driver.getWindowHandle().equals(appWindowHandle) && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		}
	}

	private WebElement waitForVisibleElement(final By by) {
		try {
			return wait.until(d -> findVisibleElement(by));
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private WebElement findVisibleElement(final By by) {
		final List<WebElement> elements = driver.findElements(by);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		return null;
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center',inline:'nearest'});",
				element);
	}

	private By byTextContains(final String text) {
		final String normalizedText = normalize(text);
		return By.xpath("//*[contains(translate(normalize-space(.),'" + UPPERCASE + "','" + LOWERCASE + "'),"
				+ xpathLiteral(normalizedText) + ")]");
	}

	private void waitForTextToDisappear(final String text) {
		wait.until(d -> findVisibleElement(byTextContains(text)) == null);
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(
				((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(700L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String filename) throws IOException {
		final Path target = artifactsDir.resolve(filename);
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
	}

	private void takeFullPageScreenshot(final String filename) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			final Long fullWidth = (Long) js.executeScript(
					"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, window.innerWidth);");
			final Long fullHeight = (Long) js.executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, window.innerHeight);");
			driver.manage().window().setSize(new Dimension(fullWidth.intValue(), Math.min(fullHeight.intValue(), 4000)));
			waitForUiToLoad();
			takeScreenshot(filename);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Full Workflow Report ===");
		for (final String field : List.of(LOGIN_FIELD, MENU_FIELD, MODAL_FIELD, ADMIN_VIEW_FIELD, INFO_FIELD,
				DETAILS_FIELD, BUSINESSES_FIELD, TERMS_FIELD, PRIVACY_FIELD)) {
			final StepResult stepResult = results.getOrDefault(field, StepResult.fail("Step was not executed."));
			final String status = stepResult.passed ? "PASS" : "FAIL";
			final String detailSuffix = stepResult.detail == null ? "" : " (" + stepResult.detail + ")";
			System.out.println(field + ": " + status + detailSuffix);
		}

		if (!legalUrls.isEmpty()) {
			System.out.println("--- Legal URLs ---");
			legalUrls.forEach((name, url) -> System.out.println(name + ": " + url));
		}

		System.out.println("Screenshots saved in: " + artifactsDir.toAbsolutePath());
	}

	private static String readConfig(final String systemProperty, final String envVariable, final String defaultValue) {
		final String propertyValue = System.getProperty(systemProperty);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		final String envValue = System.getenv(envVariable);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		return defaultValue;
	}

	private static Path createArtifactsDir() throws IOException {
		final String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Path.of("target", "saleads-artifacts", suffix);
		Files.createDirectories(dir);
		return dir;
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	private static String normalize(final String input) {
		return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
	}

	private static List<String> visibleNonEmptyLines(final String text) {
		return text.lines()
				.map(String::trim)
				.filter(line -> !line.isBlank())
				.collect(Collectors.toCollection(ArrayList::new));
	}

	private static boolean containsLine(final List<String> lines, final String expected) {
		final String normalizedExpected = normalize(expected);
		return lines.stream().map(SaleadsMiNegocioFullWorkflowTest::normalize)
				.anyMatch(line -> line.contains(normalizedExpected));
	}

	private boolean looksLikeUserNameLine(final String line) {
		final String normalized = normalize(line);
		if (normalized.isBlank()) {
			return false;
		}
		if (normalized.contains("@")) {
			return false;
		}
		if (normalized.contains("información general") || normalized.contains("business plan")
				|| normalized.contains("cambiar plan") || normalized.contains("plan")) {
			return false;
		}
		return normalized.split("\\s+").length >= 2;
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}

		private static StepResult pass() {
			return new StepResult(true, null);
		}

		private static StepResult fail(final String detail) {
			return new StepResult(false, detail == null ? "No detail provided." : detail);
		}
	}
}
