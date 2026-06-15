package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Duration DEFAULT_TIMEOUT = Duration
			.ofSeconds(Long.parseLong(System.getProperty("saleads.timeout.seconds", "30")));

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> stepErrors = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private int screenshotCounter = 1;

	@Before
	public void setUp() throws IOException {
		final String baseUrl = firstNonBlank(System.getProperty("saleads.baseUrl"), System.getenv("SALEADS_BASE_URL"));
		Assume.assumeTrue(
				"Set -Dsaleads.baseUrl or SALEADS_BASE_URL to run this UI workflow against any SaleADS environment.",
				baseUrl != null && !baseUrl.trim().isEmpty());

		final boolean headless = Boolean.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"),
				System.getenv("SALEADS_HEADLESS"), "true"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--window-size=1440,1024");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.manage().window().setSize(new Dimension(1440, 1024));

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDirectory = Files.createDirectories(Paths.get("target", "saleads-evidence", timestamp));

		driver.get(baseUrl.trim());
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones",
				() -> validateLegalDocument("Términos y Condiciones", "Términos y Condiciones", "terminos-y-condiciones"));
		runStep("Política de Privacidad",
				() -> validateLegalDocument("Política de Privacidad", "Política de Privacidad", "politica-de-privacidad"));

		final List<String> failingSteps = finalReport.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		assertTrue("Failing SaleADS workflow steps: " + failingSteps, failingSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Ingresar con Google");
		handleGoogleAccountSelector("juanlucasbarbiergarzon@gmail.com");
		wait.until(d -> hasVisibleElement(sidebarLocator()));
		wait.until(d -> hasVisibleText("Negocio") || hasVisibleText("Mi Negocio"));
		takeScreenshot("dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		expandMiNegocioMenu();
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		takeScreenshot("mi-negocio-expanded-menu");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		expandMiNegocioMenu();
		clickByVisibleText("Agregar Negocio");

		waitForVisibleText("Crear Nuevo Negocio");
		final WebElement businessNameInput = waitForBusinessNameInput();
		assertTrue("'Nombre del Negocio' input must exist.", businessNameInput.isDisplayed());
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");

		takeScreenshot("agregar-negocio-modal");

		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(exactTextLocator("Crear Nuevo Negocio")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		expandMiNegocioMenu();
		clickByVisibleText("Administrar Negocios");

		waitForVisibleText("Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal");
		takeFullPageScreenshot("administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		waitForVisibleText("Información General");
		waitForVisibleEmail();
		waitForVisibleText("BUSINESS PLAN");
		waitForVisibleText("Cambiar Plan");
		assertTrue("A user name should be visible in account page.", hasLikelyUserNameVisible());
	}

	private void stepValidateDetallesCuenta() {
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
		assertTrue("Business list should be visible.", hasBusinessListVisible());
	}

	private void validateLegalDocument(final String linkText, final String headingText, final String screenshotName)
			throws Exception {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> previousHandles = new LinkedHashSet<>(driver.getWindowHandles());

		clickLegalSectionLink(linkText);
		final boolean openedNewTab = waitForNewWindow(previousHandles);
		if (openedNewTab) {
			switchToNewWindow(previousHandles);
		}

		waitForVisibleText(headingText);
		assertTrue("Expected legal content to be visible for " + linkText, hasLegalContentVisible());
		takeScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		waitForVisibleText("Sección Legal");
	}

	private void runStep(final String reportField, final ThrowingRunnable action) {
		try {
			action.run();
			finalReport.put(reportField, true);
		} catch (final Throwable throwable) {
			finalReport.put(reportField, false);
			stepErrors.add(reportField + ": " + throwable.getMessage());
			takeScreenshot("failure-" + slugify(reportField));
		}
	}

	private void expandMiNegocioMenu() throws Exception {
		if (hasVisibleText("Agregar Negocio") && hasVisibleText("Administrar Negocios")) {
			return;
		}

		clickByVisibleText("Negocio");

		if (!hasVisibleText("Agregar Negocio") || !hasVisibleText("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
	}

	private void handleGoogleAccountSelector(final String email) {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> originalHandles = new LinkedHashSet<>(driver.getWindowHandles());

		if (waitForNewWindow(originalHandles)) {
			switchToNewWindow(originalHandles);
		}

		final By accountByEmail = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(email) + ")]");
		if (hasVisibleElement(accountByEmail)) {
			clickElement(waitForVisibleElement(accountByEmail));
		} else {
			final List<WebElement> emailInputs = driver.findElements(By.cssSelector("input[type='email']"));
			if (!emailInputs.isEmpty() && emailInputs.get(0).isDisplayed()) {
				final WebElement input = emailInputs.get(0);
				input.clear();
				input.sendKeys(email);
				input.submit();
				waitForUiToLoad();
			}
		}

		if (!driver.getWindowHandle().equals(originalWindow) && driver.getWindowHandles().contains(originalWindow)) {
			try {
				new WebDriverWait(driver, Duration.ofSeconds(20))
						.until(d -> !d.getWindowHandles().contains(driver.getWindowHandle()));
			} catch (final TimeoutException ignored) {
				// If popup did not auto-close, switch back to app tab and continue.
			}
			driver.switchTo().window(originalWindow);
			waitForUiToLoad();
		}
	}

	private void clickLegalSectionLink(final String text) throws Exception {
		final WebElement legalSectionHeading = waitForVisibleElement(exactTextLocator("Sección Legal"));
		final WebElement legalContainer = legalSectionHeading.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
		final List<WebElement> inSection = legalContainer
				.findElements(By.xpath(".//*[self::a or self::button][contains(normalize-space(.), " + xpathLiteral(text) + ")]"));

		if (!inSection.isEmpty()) {
			clickElement(inSection.get(0));
			waitForUiToLoad();
			return;
		}

		clickByVisibleText(text);
	}

	private void clickByVisibleText(final String... texts) throws Exception {
		WebElement target = null;
		for (final String text : texts) {
			target = findClickableByText(text);
			if (target != null) {
				break;
			}
		}

		if (target == null) {
			throw new NoSuchElementException("Unable to find clickable element by text: " + String.join(", ", texts));
		}

		clickElement(target);
		waitForUiToLoad();
	}

	private WebElement findClickableByText(final String text) {
		final By[] candidates = new By[] {
				By.xpath("//*[self::button or self::a or @role='button'][normalize-space(.)=" + xpathLiteral(text) + "]"),
				By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.), " + xpathLiteral(text) + ")]"),
				By.xpath("//*[normalize-space(.)=" + xpathLiteral(text)
						+ "]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"),
				By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text)
						+ ")]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"),
				By.xpath("//*[normalize-space(.)=" + xpathLiteral(text) + "]") };

		for (final By locator : candidates) {
			final List<WebElement> found = driver.findElements(locator);
			for (final WebElement element : found) {
				try {
					if (element.isDisplayed()) {
						return element;
					}
				} catch (final StaleElementReferenceException ignored) {
					// Try next candidate.
				}
			}
		}

		return null;
	}

	private WebElement waitForVisibleElement(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private void waitForVisibleText(final String text) {
		waitForVisibleElement(containsTextLocator(text));
	}

	private void waitForVisibleEmail() {
		wait.until(d -> {
			final List<WebElement> candidates = d.findElements(By.xpath("//*[contains(text(), '@')]"));
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed() && EMAIL_PATTERN.matcher(candidate.getText()).find()) {
					return true;
				}
			}
			return false;
		});
	}

	private WebElement waitForBusinessNameInput() {
		final By businessNameBy = By.xpath(
				"//label[normalize-space(.)='Nombre del Negocio']/following::input[1]"
						+ " | //input[@placeholder='Nombre del Negocio']"
						+ " | //input[@aria-label='Nombre del Negocio']");
		return waitForVisibleElement(businessNameBy);
	}

	private boolean hasBusinessListVisible() {
		final By businessItems = By.xpath(
				"//*[contains(translate(normalize-space(.), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'negocio')]");
		return hasVisibleElement(businessItems);
	}

	private boolean hasLikelyUserNameVisible() {
		final By likelyNameBy = By.xpath(
				"//*[contains(normalize-space(.), 'Información General')]/ancestor::*[self::section or self::div][1]"
						+ "//*[self::p or self::span or self::h4 or self::h5][string-length(normalize-space()) > 2]"
						+ "[not(contains(normalize-space(.), '@'))]"
						+ "[not(contains(normalize-space(.), 'BUSINESS PLAN'))]"
						+ "[not(contains(normalize-space(.), 'Cambiar Plan'))]");
		return hasVisibleElement(likelyNameBy);
	}

	private boolean hasLegalContentVisible() {
		final By legalContentBy = By.xpath("//*[self::p or self::li or self::div][string-length(normalize-space()) > 40]");
		return hasVisibleElement(legalContentBy);
	}

	private boolean hasVisibleText(final String text) {
		return hasVisibleElement(containsTextLocator(text));
	}

	private boolean hasVisibleElement(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			try {
				if (element.isDisplayed()) {
					return true;
				}
			} catch (final StaleElementReferenceException ignored) {
				// Try next element.
			}
		}
		return false;
	}

	private boolean waitForNewWindow(final Set<String> previousHandles) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12))
					.until(d -> d.getWindowHandles().size() > previousHandles.size());
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void switchToNewWindow(final Set<String> previousHandles) {
		for (final String handle : driver.getWindowHandles()) {
			if (!previousHandles.contains(handle)) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				return;
			}
		}
	}

	private void clickElement(final WebElement element) {
		for (int attempt = 0; attempt < 3; attempt++) {
			try {
				scrollIntoView(element);
				wait.until(ExpectedConditions.elementToBeClickable(element)).click();
				return;
			} catch (final StaleElementReferenceException staleElementReferenceException) {
				if (attempt == 2) {
					throw staleElementReferenceException;
				}
			}
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center',inline:'nearest'});", element);
	}

	private void waitForUiToLoad() {
		wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
	}

	private void takeScreenshot(final String label) {
		if (driver == null || evidenceDirectory == null) {
			return;
		}

		try {
			final Path outputPath = evidenceDirectory.resolve(String.format("%02d-%s.png", screenshotCounter++, slugify(label)));
			final Path screenshotPath = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(screenshotPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException ignored) {
			// Do not hide test results because of an evidence I/O issue.
		}
	}

	private void takeFullPageScreenshot(final String label) {
		if (driver == null) {
			return;
		}

		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final Long pageWidth = (Long) ((JavascriptExecutor) driver).executeScript(
					"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, window.innerWidth);");
			final Long pageHeight = (Long) ((JavascriptExecutor) driver).executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, window.innerHeight);");
			driver.manage().window().setSize(new Dimension(Math.toIntExact(Math.min(pageWidth, 1900L)),
					Math.toIntExact(Math.min(pageHeight, 5000L))));
			waitForUiToLoad();
			takeScreenshot(label);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}
	}

	private void writeFinalReport() throws IOException {
		final Path targetDir = evidenceDirectory != null ? evidenceDirectory : Files.createDirectories(Paths.get("target", "saleads-evidence"));
		final Path reportFile = targetDir.resolve("final-report.json");

		final StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"results\": {\n");

		int index = 0;
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(entry.getValue() ? "PASS" : "FAIL").append("\"");
			builder.append(index++ < finalReport.size() - 1 ? ",\n" : "\n");
		}
		builder.append("  },\n");

		builder.append("  \"legalUrls\": {\n");
		int urlIndex = 0;
		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(escapeJson(entry.getValue())).append("\"");
			builder.append(urlIndex++ < legalUrls.size() - 1 ? ",\n" : "\n");
		}
		builder.append("  },\n");

		builder.append("  \"errors\": [\n");
		for (int i = 0; i < stepErrors.size(); i++) {
			builder.append("    \"").append(escapeJson(stepErrors.get(i))).append("\"");
			builder.append(i < stepErrors.size() - 1 ? ",\n" : "\n");
		}
		builder.append("  ]\n");
		builder.append("}\n");

		Files.write(reportFile, builder.toString().getBytes(StandardCharsets.UTF_8));
	}

	private By sidebarLocator() {
		return By.xpath("//aside | //nav[contains(translate(@class, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'sidebar')]");
	}

	private By containsTextLocator(final String text) {
		return By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
	}

	private By exactTextLocator(final String text) {
		return By.xpath("//*[normalize-space(.)=" + xpathLiteral(text) + "]");
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private String escapeJson(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String slugify(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int index = 0; index < chars.length; index++) {
			final char current = chars[index];
			if (current == '\'') {
				builder.append("\"'\"");
			} else if (current == '"') {
				builder.append("'\"'");
			} else {
				builder.append("'").append(current).append("'");
			}
			if (index < chars.length - 1) {
				builder.append(',');
			}
		}
		builder.append(')');
		return builder.toString();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
