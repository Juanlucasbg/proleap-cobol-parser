package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaleadsMiNegocioWorkflowTest {

	private static final Logger LOG = LoggerFactory.getLogger(SaleadsMiNegocioWorkflowTest.class);
	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

	private final Map<String, Boolean> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		if (isHeadlessEnabled()) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(resolveTimeoutSeconds()));
		evidenceDir = Paths.get("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		final String startUrl = firstNonBlank(System.getProperty("saleads.startUrl"), System.getenv("SALEADS_START_URL"));
		if (startUrl != null) {
			driver.get(startUrl);
		}

		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::step1LoginWithGoogle);
		runStep("Mi Negocio menu", this::step2OpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::step3ValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::step4OpenAdministrarNegocios);
		runStep("Información General", this::step5ValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::step6ValidateDetallesCuenta);
		runStep("Tus Negocios", this::step7ValidateTusNegocios);
		runStep("Términos y Condiciones", () -> step8ValidateLegalPage("Términos y Condiciones", "terminos-y-condiciones"));
		runStep("Política de Privacidad", () -> step8ValidateLegalPage("Política de Privacidad", "politica-de-privacidad"));

		LOG.info("Final report: {}", report);
		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		assertTrue("Workflow failed in steps: " + failedSteps + ". Evidence: " + evidenceDir.toAbsolutePath(),
				failedSteps.isEmpty());
	}

	private void step1LoginWithGoogle() throws IOException {
		clickByVisibleTextAny("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google");
		waitForUiToLoad();

		selectGoogleAccountIfVisible(ACCOUNT_EMAIL);

		assertVisibleTextAny("Negocio", "Mi Negocio");
		assertTrue("Left sidebar navigation should be visible.",
				isAnyLocatorVisible(By.xpath("//aside"), By.xpath("//nav[.//*[contains(normalize-space(), 'Negocio')]]")));
		captureScreenshot("step1-dashboard-loaded");
	}

	private void step2OpenMiNegocioMenu() throws IOException {
		if (!isTextVisible("Mi Negocio")) {
			clickByVisibleTextAny("Negocio");
		}
		clickByVisibleTextAny("Mi Negocio");
		assertVisibleTextAny("Agregar Negocio");
		assertVisibleTextAny("Administrar Negocios");
		captureScreenshot("step2-mi-negocio-expanded");
	}

	private void step3ValidateAgregarNegocioModal() throws IOException {
		clickByVisibleTextAny("Agregar Negocio");
		assertVisibleTextAny("Crear Nuevo Negocio");
		assertTrue("Nombre del Negocio input must exist.",
				isAnyLocatorVisible(By.xpath("//input[@placeholder='Nombre del Negocio']"),
						By.xpath("//input[contains(@name,'Negocio')]"),
						By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]")));
		assertVisibleTextAny("Tienes 2 de 3 negocios");
		assertVisibleTextAny("Cancelar");
		assertVisibleTextAny("Crear Negocio");
		captureScreenshot("step3-agregar-negocio-modal");

		typeIfVisible("Nombre del Negocio", "Negocio Prueba Automatización");
		clickByVisibleTextAny("Cancelar");
	}

	private void step4OpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleTextAny("Mi Negocio");
		}
		clickByVisibleTextAny("Administrar Negocios");
		assertVisibleTextAny("Información General");
		assertVisibleTextAny("Detalles de la Cuenta");
		assertVisibleTextAny("Tus Negocios");
		assertVisibleTextAny("Sección Legal");
		captureScreenshot("step4-administrar-negocios");
	}

	private void step5ValidateInformacionGeneral() {
		final WebElement sectionHeading = waitForVisible(By.xpath("//*[contains(normalize-space(),'Información General')]"));
		final WebElement section = resolveSectionContainer(sectionHeading);
		assertTrue("User email should be visible in Información General.",
				getVisibleTexts(section).stream().anyMatch(text -> EMAIL_PATTERN.matcher(text).matches()));
		assertTrue("Potential user name should be visible in Información General.",
				getVisibleTexts(section).stream().anyMatch(text -> !text.contains("@") && text.length() > 2
						&& !"BUSINESS PLAN".equalsIgnoreCase(text) && !"Cambiar Plan".equalsIgnoreCase(text)));
		assertVisibleTextAny("BUSINESS PLAN");
		assertVisibleTextAny("Cambiar Plan");
	}

	private void step6ValidateDetallesCuenta() {
		assertVisibleTextAny("Cuenta creada");
		assertVisibleTextAny("Estado activo");
		assertVisibleTextAny("Idioma seleccionado");
	}

	private void step7ValidateTusNegocios() {
		assertVisibleTextAny("Tus Negocios");
		assertVisibleTextAny("Agregar Negocio");
		assertVisibleTextAny("Tienes 2 de 3 negocios");
	}

	private void step8ValidateLegalPage(final String linkText, final String checkpointName) throws IOException {
		final String sourceHandle = driver.getWindowHandle();
		final String sourceUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleTextAny(linkText);

		boolean openedNewTab = false;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size() || !sourceUrl.equals(d.getCurrentUrl()));
			openedNewTab = driver.getWindowHandles().size() > handlesBeforeClick.size();
		} catch (final TimeoutException e) {
			// Continue with same tab validation if no navigation signal was detected in time.
		}

		if (openedNewTab) {
			switchToNewestWindow(handlesBeforeClick);
		}

		assertVisibleTextAny(linkText);
		assertTrue("Legal content should be visible on page " + linkText, hasVisibleParagraphLikeContent());
		captureScreenshot("step-legal-" + checkpointName);
		LOG.info("{} URL: {}", linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(sourceHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void runStep(final String stepName, final StepExecutable stepExecutable) {
		try {
			stepExecutable.execute();
			report.put(stepName, Boolean.TRUE);
			LOG.info("STEP PASS - {}", stepName);
		} catch (final Exception e) {
			report.put(stepName, Boolean.FALSE);
			LOG.error("STEP FAIL - {}", stepName, e);
		}
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		final long timeoutMillis = Duration.ofSeconds(20).toMillis();
		final long startTime = System.currentTimeMillis();

		while (System.currentTimeMillis() - startTime < timeoutMillis) {
			final Set<String> handles = driver.getWindowHandles();
			for (final String handle : handles) {
				driver.switchTo().window(handle);
				if (isTextVisible(accountEmail)) {
					clickByVisibleTextAny(accountEmail);
					waitForUiToLoad();
					if (handles.contains(appWindowHandle)) {
						driver.switchTo().window(appWindowHandle);
					}
					return;
				}
			}

			sleepMillis(500);
		}

		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private void clickByVisibleTextAny(final String... texts) {
		Exception lastFailure = null;
		for (final String text : texts) {
			try {
				final WebElement element = findVisibleElementByText(text);
				scrollIntoView(element);
				wait.until(ExpectedConditions.elementToBeClickable(element)).click();
				waitForUiToLoad();
				return;
			} catch (final Exception e) {
				lastFailure = e;
			}
		}

		throw new IllegalStateException("Could not click any of: " + String.join(", ", texts), lastFailure);
	}

	private WebElement findVisibleElementByText(final String text) {
		final String literal = xpathLiteral(text);
		final By exact = By.xpath("//*[normalize-space()=" + literal + "]");
		final By partial = By.xpath("//*[contains(normalize-space(), " + literal + ")]");

		final Optional<WebElement> exactMatch = firstDisplayed(exact);
		if (exactMatch.isPresent()) {
			return exactMatch.get();
		}

		return firstDisplayed(partial)
				.orElseThrow(() -> new IllegalStateException("Visible element with text not found: " + text));
	}

	private void assertVisibleTextAny(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return;
			}
		}
		throw new IllegalStateException("None of the expected visible texts were found: " + String.join(", ", texts));
	}

	private boolean isTextVisible(final String text) {
		try {
			wait.until(ExpectedConditions
					.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]")));
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private boolean isAnyLocatorVisible(final By... locators) {
		for (final By locator : locators) {
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return true;
			} catch (final TimeoutException e) {
				// Try next locator.
			}
		}
		return false;
	}

	private Optional<WebElement> firstDisplayed(final By locator) {
		for (final WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				return Optional.of(element);
			}
		}
		return Optional.empty();
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void typeIfVisible(final String inputHint, final String value) {
		final List<By> inputLocators = List.of(
				By.xpath("//input[@placeholder=" + xpathLiteral(inputHint) + "]"),
				By.xpath("//label[contains(normalize-space(), " + xpathLiteral(inputHint) + ")]/following::input[1]"),
				By.xpath("//input[contains(@name, " + xpathLiteral("Negocio") + ")]"));
		for (final By locator : inputLocators) {
			try {
				final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				input.clear();
				input.sendKeys(value);
				return;
			} catch (final TimeoutException e) {
				// Try next locator.
			}
		}
	}

	private boolean hasVisibleParagraphLikeContent() {
		final List<WebElement> contentNodes = driver
				.findElements(By.xpath("//p[string-length(normalize-space()) > 40] | //div[string-length(normalize-space()) > 80]"));
		return contentNodes.stream().anyMatch(WebElement::isDisplayed);
	}

	private List<String> getVisibleTexts(final WebElement root) {
		final List<String> texts = new ArrayList<>();
		for (final WebElement element : root.findElements(By.xpath(".//*[string-length(normalize-space()) > 0]"))) {
			if (element.isDisplayed()) {
				final String text = element.getText().trim();
				if (!text.isEmpty()) {
					texts.add(text);
				}
			}
		}
		return texts;
	}

	private WebElement resolveSectionContainer(final WebElement heading) {
		try {
			return heading.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		} catch (final Exception e) {
			return driver.findElement(By.tagName("body"));
		}
	}

	private void switchToNewestWindow(final Set<String> handlesBeforeClick) {
		final Set<String> currentHandles = driver.getWindowHandles();
		for (final String handle : currentHandles) {
			if (!handlesBeforeClick.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), evidenceDir.resolve(checkpointName + ".png"));
	}

	private void waitForUiToLoad() {
		try {
			final ExpectedCondition<Boolean> pageLoaded = webDriver -> "complete"
					.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState"));
			new WebDriverWait(driver, Duration.ofSeconds(20)).until(pageLoaded);
		} catch (final TimeoutException e) {
			LOG.debug("Timed out waiting for document.readyState=complete. Continuing.");
		}
		sleepMillis(400);
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
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

	private boolean isHeadlessEnabled() {
		return Boolean.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "true"));
	}

	private long resolveTimeoutSeconds() {
		return Long.parseLong(firstNonBlank(System.getProperty("saleads.timeout.seconds"), System.getenv("SALEADS_TIMEOUT_SECONDS"), "30"));
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private void sleepMillis(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepExecutable {
		void execute() throws Exception;
	}
}
