package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow validation for SaleADS "Mi Negocio".
 *
 * This test is intentionally opt-in to avoid affecting the default COBOL parser
 * build. Enable with:
 *
 * SALEADS_RUN_E2E=true SALEADS_LOGIN_URL=https://your-env/login mvn -Dtest=SaleadsMiNegocioWorkflowE2eTest test
 */
public class SaleadsMiNegocioWorkflowE2eTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepStatus> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String appWindowHandle;
	private int screenshotIndex = 1;

	@Before
	public void setUp() throws IOException {
		final boolean runE2E = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_RUN_E2E", "false"));
		Assume.assumeTrue("Skipping SaleADS E2E. Set SALEADS_RUN_E2E=true to run it.", runE2E);

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL with the current environment login page.",
				loginUrl != null && !loginUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		driver.get(loginUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();

		screenshotDir = Path.of("target", "screenshots", "saleads-mi-negocio");
		Files.createDirectories(screenshotDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones"));
		executeStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad"));

		final String finalReport = renderFinalReport();
		System.out.println(finalReport);

		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(Map.Entry::getKey).collect(Collectors.toList());
		assertTrue("Some validations failed: " + failedSteps + System.lineSeparator() + finalReport,
				failedSteps.isEmpty());
	}

	private String stepLoginWithGoogle() throws IOException {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
				"Continuar con Google", "Google");
		waitForUiToLoad();

		selectGoogleAccountIfVisible();

		waitForAnyVisibleText("Negocio");
		waitForVisible(By.xpath(
				"//aside//*[contains(normalize-space(.), 'Negocio')] | //nav//*[contains(normalize-space(.), 'Negocio')] | //*[@role='navigation']//*[contains(normalize-space(.), 'Negocio')]"));

		final String screenshot = captureScreenshot("dashboard-loaded");
		return "Dashboard loaded. Screenshot: " + screenshot;
	}

	private String stepOpenMiNegocioMenu() throws IOException {
		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		waitForAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Administrar Negocios");

		final String screenshot = captureScreenshot("mi-negocio-menu-expanded");
		return "Submenu expanded. Screenshot: " + screenshot;
	}

	private String stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForUiToLoad();

		final WebElement modalTitle = waitForAnyVisibleText("Crear Nuevo Negocio");
		waitForVisible(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')] | //input[@name='nombreNegocio']"));
		waitForAnyVisibleText("Tienes 2 de 3 negocios");
		waitForAnyVisibleText("Cancelar");
		waitForAnyVisibleText("Crear Negocio");

		final String screenshot = captureScreenshot("agregar-negocio-modal");

		// Optional interaction requested by the workflow.
		final WebElement input = findFirstVisible(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')] | //input[@name='nombreNegocio']"));
		if (input != null) {
			input.click();
			waitForUiToLoad();
			input.clear();
			input.sendKeys("Negocio Prueba Automatizacion");
		}

		final WebElement modal = findClosestDialog(modalTitle);
		clickByVisibleTextInside(modal, "Cancelar");
		waitForUiToLoad();
		waitUntilNotVisible(By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]"));

		return "Modal validated and closed. Screenshot: " + screenshot;
	}

	private String stepOpenAdministrarNegocios() throws IOException {
		if (!isVisible(By.xpath("//*[contains(normalize-space(.), 'Administrar Negocios')]"), 2)) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		waitForAnyVisibleText("Información General", "Informacion General");
		waitForAnyVisibleText("Detalles de la Cuenta", "Detalles de la cuenta");
		waitForAnyVisibleText("Tus Negocios");
		waitForAnyVisibleText("Sección Legal", "Seccion Legal");

		final String screenshot = captureScreenshot("administrar-negocios-page");
		return "Account page loaded. Screenshot: " + screenshot;
	}

	private String stepValidateInformacionGeneral() {
		final String sectionText = readSectionText("Información General", "Informacion General");
		assertTrue("Expected user email to be visible in Información General.",
				EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("Expected user name to be visible in Información General.", hasLikelyUserName(sectionText));
		assertContains(sectionText, "BUSINESS PLAN");
		assertContains(sectionText, "Cambiar Plan");
		return "Información General validated.";
	}

	private String stepValidateDetallesCuenta() {
		final String sectionText = readSectionText("Detalles de la Cuenta", "Detalles de la cuenta");
		assertContains(sectionText, "Cuenta creada");
		assertContains(sectionText, "Estado activo");
		assertContains(sectionText, "Idioma seleccionado");
		return "Detalles de la Cuenta validated.";
	}

	private String stepValidateTusNegocios() {
		final String sectionText = readSectionText("Tus Negocios");
		assertContains(sectionText, "Agregar Negocio");
		assertContains(sectionText, "Tienes 2 de 3 negocios");
		assertTrue("Expected business list content to be visible in Tus Negocios.",
				sectionText.replaceAll("\\s+", " ").trim().length() > 70);
		return "Tus Negocios validated.";
	}

	private String stepValidateLegalLink(final String legalLinkText) throws IOException {
		final String originalWindow = driver.getWindowHandle();
		final String appUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(legalLinkText);
		waitForUiToLoad();

		String legalWindow = originalWindow;
		boolean newTabOpened = false;

		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(webDriver -> webDriver.getWindowHandles().size() > handlesBefore.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					legalWindow = handle;
					newTabOpened = true;
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			// Link may navigate in the same tab.
		}

		driver.switchTo().window(legalWindow);
		waitForUiToLoad();

		if (legalLinkText.contains("Términos")) {
			waitForAnyVisibleText("Términos y Condiciones", "Terminos y Condiciones");
		} else {
			waitForAnyVisibleText("Política de Privacidad", "Politica de Privacidad");
		}

		final String bodyText = driver.findElement(By.tagName("body")).getText().replaceAll("\\s+", " ").trim();
		assertTrue("Expected legal content to be visible.", bodyText.length() > 150);

		final String screenshot = captureScreenshot("legal-" + sanitize(legalLinkText));
		final String legalUrl = driver.getCurrentUrl();

		if (newTabOpened) {
			driver.close();
			driver.switchTo().window(originalWindow);
		} else {
			driver.navigate().to(appUrl);
			waitForUiToLoad();
		}

		waitForAnyVisibleText("Sección Legal", "Seccion Legal");
		return "URL: " + legalUrl + " | Screenshot: " + screenshot;
	}

	private void selectGoogleAccountIfVisible() {
		final Set<String> handles = driver.getWindowHandles();
		for (final String handle : handles) {
			driver.switchTo().window(handle);
			if (isVisible(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]"), 8)) {
				clickByVisibleText(GOOGLE_ACCOUNT_EMAIL);
				waitForUiToLoad();
				break;
			}
		}

		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private void executeStep(final String stepName, final CheckedStep step) {
		try {
			final String detail = step.run();
			report.put(stepName, StepStatus.pass(detail));
		} catch (final Throwable throwable) {
			report.put(stepName, StepStatus.fail(rootMessage(throwable)));
		}
	}

	private String renderFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append(System.lineSeparator());
		builder.append("SaleADS Mi Negocio final report").append(System.lineSeparator());
		builder.append("================================").append(System.lineSeparator());
		for (final Map.Entry<String, StepStatus> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().passed ? "PASS" : "FAIL");
			if (entry.getValue().detail != null && !entry.getValue().detail.isBlank()) {
				builder.append(" (").append(entry.getValue().detail).append(")");
			}
			builder.append(System.lineSeparator());
		}
		return builder.toString();
	}

	private void clickByVisibleText(final String... texts) {
		clickByVisibleTextInside(null, texts);
	}

	private void clickByVisibleTextInside(final WebElement root, final String... texts) {
		for (final String text : texts) {
			final List<WebElement> candidates = findClickableCandidates(root, text);
			for (final WebElement candidate : candidates) {
				if (!candidate.isDisplayed() || !candidate.isEnabled()) {
					continue;
				}
				clickElement(candidate);
				waitForUiToLoad();
				return;
			}
		}
		throw new AssertionError("Unable to find clickable element by visible text: " + Arrays.toString(texts));
	}

	private List<WebElement> findClickableCandidates(final WebElement root, final String text) {
		final String locator = "//*[contains(normalize-space(.), " + xpathLiteral(text)
				+ ") and (self::button or self::a or @role='button' or self::span or self::div)]";
		final List<WebElement> rawCandidates = root == null ? driver.findElements(By.xpath(locator))
				: root.findElements(By.xpath("." + locator));
		final List<WebElement> clickables = new ArrayList<>();

		for (final WebElement candidate : rawCandidates) {
			try {
				if ("button".equalsIgnoreCase(candidate.getTagName()) || "a".equalsIgnoreCase(candidate.getTagName())
						|| "button".equalsIgnoreCase(candidate.getAttribute("role"))) {
					clickables.add(candidate);
					continue;
				}
				final WebElement ancestor = candidate
						.findElement(By.xpath("./ancestor-or-self::*[self::button or self::a or @role='button'][1]"));
				clickables.add(ancestor);
			} catch (final NoSuchElementException ignored) {
				// Candidate itself might still be clickable via JavaScript fallback.
				clickables.add(candidate);
			}
		}

		return clickables;
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private WebElement waitForAnyVisibleText(final String... texts) {
		for (final String text : texts) {
			final By locator = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
			if (isVisible(locator, 6)) {
				return waitForVisible(locator);
			}
		}
		throw new AssertionError("Could not find expected text in page: " + Arrays.toString(texts));
	}

	private boolean isVisible(final By locator, final int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void waitUntilNotVisible(final By locator) {
		wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private String readSectionText(final String... headings) {
		final WebElement heading = waitForAnyVisibleText(headings);
		WebElement section = heading;

		try {
			section = heading.findElement(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
		} catch (final NoSuchElementException ignored) {
			// Fallback to the heading itself if no semantic container exists.
		}

		return section.getText();
	}

	private boolean hasLikelyUserName(final String sectionText) {
		final String[] lines = sectionText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.length() < 3 || EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}

			final String normalized = line.toLowerCase(Locale.ROOT);
			if (normalized.contains("informacion general") || normalized.contains("información general")
					|| normalized.contains("business plan") || normalized.contains("cambiar plan")) {
				continue;
			}

			if (line.matches(".*[A-Za-z].*")) {
				return true;
			}
		}
		return false;
	}

	private WebElement findClosestDialog(final WebElement child) {
		try {
			return child.findElement(By.xpath("./ancestor::*[@role='dialog' or contains(@class, 'modal')][1]"));
		} catch (final NoSuchElementException ignored) {
			return null;
		}
	}

	private WebElement findFirstVisible(final By locator) {
		final List<WebElement> matches = driver.findElements(locator);
		for (final WebElement match : matches) {
			if (match.isDisplayed()) {
				return match;
			}
		}
		return null;
	}

	private String captureScreenshot(final String checkpointName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String fileName = String.format("%02d-%s-%s.png", screenshotIndex++, sanitize(checkpointName),
				DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-"));
		final Path destination = screenshotDir.resolve(fileName);
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		return destination.toString();
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		try {
			Thread.sleep(400);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void assertContains(final String text, final String expectedFragment) {
		assertTrue("Expected to find text [" + expectedFragment + "] in section: " + text,
				text != null && text.contains(expectedFragment));
	}

	private String sanitize(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String rootMessage(final Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getClass().getSimpleName() + ": " + current.getMessage();
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder concat = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				concat.append(", \"'\", ");
			}
			concat.append("'").append(parts[i]).append("'");
		}
		concat.append(")");
		return concat.toString();
	}

	@FunctionalInterface
	private interface CheckedStep {
		String run() throws Exception;
	}

	private static class StepStatus {
		private final boolean passed;
		private final String detail;

		private StepStatus(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}

		private static StepStatus pass(final String detail) {
			return new StepStatus(true, detail);
		}

		private static StepStatus fail(final String detail) {
			return new StepStatus(false, detail);
		}
	}
}
