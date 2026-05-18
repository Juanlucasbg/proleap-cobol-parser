package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end test for the SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * Runtime properties:
 * </p>
 * <ul>
 * <li>saleads.login.url (required): Environment login URL (dev/staging/prod).</li>
 * <li>saleads.headless (optional, default true): true/false.</li>
 * <li>saleads.timeout.seconds (optional, default 30): explicit wait timeout.</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowIT {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.%-]+@[\\w.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		final long timeoutSeconds = Long.parseLong(System.getProperty("saleads.timeout.seconds", "30"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-gpu");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		evidenceDir = createEvidenceDirectory();
	}

	@After
	public void tearDown() {
		writeFinalReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String loginUrl = getRequiredProperty("saleads.login.url");
		driver.get(loginUrl);
		waitForUiToLoad();

		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", () -> stepValidateLegalDocument("Términos y Condiciones", "Términos y Condiciones",
				"08-terminos-y-condiciones.png"));
		executeStep("Política de Privacidad", () -> stepValidateLegalDocument("Política de Privacidad", "Política de Privacidad",
				"09-politica-de-privacidad.png"));

		final boolean allPassed = report.values().stream().allMatch(StepResult::passed);
		assertTrue(buildAssertionMessage(), allPassed);
	}

	private void stepLoginWithGoogle() throws IOException {
		final String mainHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByAnyVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"));
		waitForUiToLoad();

		switchToNewWindowIfAny(handlesBeforeClick);
		if (isTextVisible(GOOGLE_ACCOUNT_EMAIL, Duration.ofSeconds(10))) {
			clickByVisibleText(GOOGLE_ACCOUNT_EMAIL);
			waitForUiToLoad();
		}

		if (driver.getWindowHandles().contains(mainHandle)) {
			driver.switchTo().window(mainHandle);
		}

		waitForAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio", "Dashboard"), Duration.ofSeconds(40));
		assertTrue("Left sidebar is not visible after login.", isSidebarVisible());
		captureScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		assertTrue("Left sidebar is not visible.", isSidebarVisible());
		if (isTextVisible("Negocio", Duration.ofSeconds(5))) {
			clickByVisibleText("Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertTextVisible("Crear Nuevo Negocio");
		findInputForNombreDelNegocio();
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal.png");

		final WebElement nameInput = findInputForNombreDelNegocio();
		nameInput.click();
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(3))) {
			if (isTextVisible("Mi Negocio", Duration.ofSeconds(3))) {
				clickByVisibleText("Mi Negocio");
				waitForUiToLoad();
			}
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		captureScreenshot("04-administrar-negocios-view.png");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Información General");
		final String sectionText = normalizeWhitespace(section.getText());

		assertTrue("User email not found in Información General section.", EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("BUSINESS PLAN text is missing in Información General section.", sectionText.contains("BUSINESS PLAN"));
		assertTrue("'Cambiar Plan' button is missing.", isTextVisibleInside(section, "Cambiar Plan"));
		assertTrue("No likely user name found in Información General section.", hasLikelyUserName(sectionText));
	}

	private void stepValidateDetallesDeLaCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		assertTrue("'Cuenta creada' not visible.", isTextVisibleInside(section, "Cuenta creada"));
		assertTrue("'Estado activo' not visible.", isTextVisibleInside(section, "Estado activo"));
		assertTrue("'Idioma seleccionado' not visible.", isTextVisibleInside(section, "Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		assertTrue("Business list area seems empty.", hasBusinessListContent(section));
		assertTrue("'Agregar Negocio' button missing in Tus Negocios.", isTextVisibleInside(section, "Agregar Negocio"));
		assertTrue("'Tienes 2 de 3 negocios' is not visible in Tus Negocios.", isTextVisibleInside(section, "Tienes 2 de 3 negocios"));
	}

	private void stepValidateLegalDocument(final String linkText, final String headingText, final String screenshotName) throws IOException {
		final String appHandle = driver.getWindowHandle();
		final String appUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		final boolean openedNewTab = switchToNewWindowIfAny(handlesBeforeClick);
		if (!openedNewTab) {
			wait.until((ExpectedCondition<Boolean>) d -> d != null && !d.getCurrentUrl().equals(appUrl));
		}

		waitForVisibleText(headingText, Duration.ofSeconds(30));
		final String bodyText = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		assertTrue("Legal content text is too short for " + headingText + ".", bodyText.length() > 120);

		final String finalUrl = driver.getCurrentUrl();
		captureScreenshot(screenshotName);
		appendDetailToStep(linkText, "Final URL: " + finalUrl);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		assertTextVisible("Sección Legal");
	}

	private void executeStep(final String stepName, final ThrowingRunnable runnable) {
		try {
			runnable.run();
			final String detail = stepDetails.remove(stepName);
			report.put(stepName, new StepResult(true, detail == null ? "PASS" : "PASS | " + detail));
		} catch (final Exception ex) {
			try {
				captureScreenshot("error-" + sanitizeFileName(stepName) + ".png");
			} catch (final IOException ignored) {
			}
			final String detail = stepDetails.remove(stepName);
			final String message = "FAIL: " + rootMessage(ex);
			report.put(stepName, new StepResult(false, detail == null ? message : message + " | " + detail));
		}
	}

	private void appendDetailToStep(final String stepName, final String detail) {
		stepDetails.merge(stepName, detail, (a, b) -> a + " | " + b);
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"))));
		} catch (final Exception ignored) {
		}

		try {
			new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> d.findElements(
					By.cssSelector("[aria-busy='true'], .loading, .spinner, .ant-spin-spinning, [data-loading='true']")).isEmpty());
		} catch (final TimeoutException ignored) {
		}
	}

	private boolean isSidebarVisible() {
		final List<WebElement> sidebars = new ArrayList<>();
		sidebars.addAll(driver.findElements(By.cssSelector("aside")));
		sidebars.addAll(driver.findElements(By.cssSelector("nav")));

		for (final WebElement sidebar : sidebars) {
			if (sidebar.isDisplayed()) {
				final String text = normalizeWhitespace(sidebar.getText());
				if (text.contains("Negocio") || text.contains("Mi Negocio")) {
					return true;
				}
			}
		}

		return isTextVisible("Mi Negocio", Duration.ofSeconds(2));
	}

	private void clickByAnyVisibleText(final List<String> texts) {
		Exception last = null;
		for (final String text : texts) {
			try {
				clickByVisibleText(text);
				return;
			} catch (final Exception ex) {
				last = ex;
			}
		}

		throw new NoSuchElementException("Unable to click any of texts: " + texts + ". Last error: "
				+ (last == null ? "unknown" : last.getMessage()));
	}

	private void clickByVisibleText(final String text) {
		final String literal = toXpathLiteral(text);
		final By clickableBy = By.xpath(
				"//*[normalize-space(.)=" + literal + "]/ancestor-or-self::*[self::button or self::a or @role='button' or self::input[@type='button' or @type='submit']][1]");

		WebElement target = null;
		try {
			target = waitForElement(clickableBy, Duration.ofSeconds(12));
		} catch (final TimeoutException ignored) {
			target = waitForVisibleText(text, Duration.ofSeconds(12));
		}

		scrollIntoView(target);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(target)).click();
		} catch (final Exception ignored) {
			try {
				target.click();
			} catch (final Exception clickError) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", target);
			}
		}

		waitForUiToLoad();
	}

	private WebElement waitForVisibleText(final String text, final Duration timeout) {
		final String literal = toXpathLiteral(text);
		final By exactText = By.xpath("//*[normalize-space(.)=" + literal + "]");
		return waitForElement(exactText, timeout);
	}

	private WebElement waitForAnyVisibleText(final List<String> texts, final Duration timeout) {
		final long timeoutMillis = timeout.toMillis();
		final long pollMillis = 400L;
		final long start = System.currentTimeMillis();
		Exception last = null;

		while (System.currentTimeMillis() - start <= timeoutMillis) {
			for (final String text : texts) {
				try {
					return waitForVisibleText(text, Duration.ofMillis(pollMillis));
				} catch (final Exception ex) {
					last = ex;
				}
			}
		}

		throw new NoSuchElementException(
				"Unable to find any text from " + texts + ". Last error: " + (last == null ? "unknown" : last.getMessage()));
	}

	private WebElement waitForElement(final By by, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(d -> {
			final List<WebElement> elements = d.findElements(by);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			waitForVisibleText(text, timeout);
			return true;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void assertTextVisible(final String text) {
		waitForVisibleText(text, Duration.ofSeconds(20));
	}

	private WebElement findInputForNombreDelNegocio() {
		final String label = toXpathLiteral("Nombre del Negocio");
		final List<By> selectors = Arrays.asList(
				By.xpath("//input[contains(@placeholder, " + label + ")]"),
				By.xpath("//input[contains(@aria-label, " + label + ")]"),
				By.xpath("//label[contains(normalize-space(.), " + label + ")]/following::input[1]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"));

		for (final By selector : selectors) {
			try {
				return waitForElement(selector, Duration.ofSeconds(8));
			} catch (final Exception ignored) {
			}
		}

		throw new NoSuchElementException("Input field 'Nombre del Negocio' was not found.");
	}

	private WebElement findSectionByHeading(final String headingText) {
		final WebElement heading = waitForVisibleText(headingText, Duration.ofSeconds(20));
		try {
			return heading.findElement(By.xpath("./ancestor::*[self::section or self::article][1]"));
		} catch (final NoSuchElementException ignored) {
			return heading.findElement(By.xpath("./ancestor::div[1]"));
		}
	}

	private boolean isTextVisibleInside(final WebElement container, final String text) {
		final String literal = toXpathLiteral(text);
		return !container.findElements(By.xpath(".//*[contains(normalize-space(.), " + literal + ")]")).isEmpty();
	}

	private boolean hasLikelyUserName(final String sectionText) {
		final String[] lines = sectionText.split("\\R");
		for (final String raw : lines) {
			final String line = raw.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.contains("@")) {
				continue;
			}
			if (line.equalsIgnoreCase("Información General")) {
				continue;
			}
			if (line.toUpperCase().contains("BUSINESS PLAN")) {
				continue;
			}
			if (line.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}

			if (line.matches("[\\p{L}]{2,}(\\s+[\\p{L}]{2,})+")) {
				return true;
			}
		}

		return false;
	}

	private boolean hasBusinessListContent(final WebElement section) {
		final List<WebElement> candidates = new ArrayList<>();
		candidates.addAll(section.findElements(By.xpath(".//li")));
		candidates.addAll(section.findElements(By.xpath(".//tr")));
		candidates.addAll(section.findElements(By.xpath(".//article")));
		candidates.addAll(section.findElements(By.xpath(".//div[contains(@class,'card') or contains(@class,'business')]")));

		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed() && !normalizeWhitespace(candidate.getText()).isEmpty()) {
				return true;
			}
		}

		final String text = normalizeWhitespace(section.getText());
		return text.length() > 80;
	}

	private boolean switchToNewWindowIfAny(final Set<String> handlesBeforeClick) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(d -> d != null && d.getWindowHandles().size() > handlesBeforeClick.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					waitForUiToLoad();
					return true;
				}
			}
		} catch (final TimeoutException ignored) {
		}

		return false;
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(path);
		return path;
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), evidenceDir.resolve(fileName));
	}

	private String getRequiredProperty(final String key) {
		final String value = System.getProperty(key, "").trim();
		if (!value.isEmpty()) {
			return value;
		}

		throw new IllegalStateException("Missing required property '" + key
				+ "'. Provide the environment-specific login URL at runtime, e.g. -Dsaleads.login.url=https://<your-env>/login");
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center',inline:'center'});", element);
		try {
			new Actions(driver).moveToElement(element).perform();
		} catch (final Exception ignored) {
		}
	}

	private void writeFinalReport() {
		try {
			if (evidenceDir == null) {
				return;
			}
			final StringBuilder sb = new StringBuilder();
			sb.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
			sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator()).append(System.lineSeparator());
			for (final String key : Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
					"Información General", "Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones", "Política de Privacidad")) {
				final StepResult result = report.get(key);
				if (result == null) {
					sb.append("- ").append(key).append(": FAIL | Not executed").append(System.lineSeparator());
				} else {
					sb.append("- ").append(key).append(": ").append(result.passed() ? "PASS" : "FAIL").append(" | ")
							.append(result.detail()).append(System.lineSeparator());
				}
			}

			final Path reportFile = evidenceDir.resolve("final-report.txt");
			Files.write(reportFile, sb.toString().getBytes(StandardCharsets.UTF_8));
			System.out.println(sb.toString());
			System.out.println("Final report saved at: " + reportFile.toAbsolutePath());
		} catch (final Exception ex) {
			System.err.println("Failed to write final report: " + ex.getMessage());
		}
	}

	private String buildAssertionMessage() {
		final StringBuilder sb = new StringBuilder("One or more SaleADS workflow validations failed.");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			sb.append(System.lineSeparator()).append("- ").append(entry.getKey()).append(": ").append(entry.getValue().detail());
		}
		return sb.toString();
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < value.length(); i++) {
			final String c = String.valueOf(value.charAt(i));
			if ("'".equals(c)) {
				sb.append("\"'\"");
			} else if ("\"".equals(c)) {
				sb.append("'\"'");
			} else {
				sb.append("'").append(c).append("'");
			}
			if (i < value.length() - 1) {
				sb.append(",");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	private String normalizeWhitespace(final String text) {
		return text == null ? "" : text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
	}

	private String sanitizeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-");
	}

	private String rootMessage(final Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		final String message = current.getMessage();
		return message == null || message.isEmpty() ? current.getClass().getSimpleName() : message;
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private record StepResult(boolean passed, String detail) {
	}
}
