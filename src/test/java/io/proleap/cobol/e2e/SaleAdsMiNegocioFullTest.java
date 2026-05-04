package io.proleap.cobol.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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

public class SaleAdsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(45);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final List<String> notes = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setup() throws Exception {
		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		if (isHeadlessEnabled()) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDir = createEvidenceDirectory();

		final String startUrl = firstNonBlank(
				System.getProperty("saleads.startUrl"),
				System.getenv("SALEADS_START_URL"),
				System.getenv("BASE_URL"));

		if (startUrl != null) {
			driver.get(startUrl);
			waitForUiToLoad();
		} else {
			notes.add("No start URL provided (saleads.startUrl / SALEADS_START_URL / BASE_URL).");
			notes.add("The browser must already be on a SaleADS login page for this run context.");
		}
	}

	@After
	public void teardown() {
		if (driver != null) {
			driver.quit();
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
		runStep("Términos y Condiciones", () -> stepValidateLegalDocument("Términos y Condiciones", "terminos_condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalDocument("Política de Privacidad", "politica_privacidad"));

		printReportAndAssert();
	}

	private void stepLoginWithGoogle() throws Exception {
		if (!isSidebarVisible()) {
			clickByTextCandidates("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
			waitForUiToLoad();
			selectGoogleAccountIfPrompted();
		}

		waitUntilVisibleByText("Negocio");
		Assert.assertTrue("Left sidebar should be visible after login.", isSidebarVisible());
		captureScreenshot("step1_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		waitUntilVisibleByText("Negocio");
		clickIfPresent("Negocio");
		clickByTextCandidates("Mi Negocio");

		waitUntilVisibleByText("Agregar Negocio");
		waitUntilVisibleByText("Administrar Negocios");
		captureScreenshot("step2_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByTextCandidates("Agregar Negocio");
		waitUntilVisibleByText("Crear Nuevo Negocio");

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		final Optional<WebElement> businessNameInput = findFirstVisible(Duration.ofSeconds(5),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[@name='businessName']"));

		if (businessNameInput.isPresent()) {
			businessNameInput.get().click();
			businessNameInput.get().clear();
			businessNameInput.get().sendKeys("Negocio Prueba Automatizacion");
		}

		captureScreenshot("step3_crear_nuevo_negocio_modal");
		clickByTextCandidates("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios")) {
			clickByTextCandidates("Mi Negocio");
			waitForUiToLoad();
		}

		clickByTextCandidates("Administrar Negocios");
		waitUntilVisibleByText("Información General");
		waitUntilVisibleByText("Detalles de la Cuenta");
		waitUntilVisibleByText("Tus Negocios");
		waitUntilVisibleByText("Sección Legal");

		captureScreenshot("step4_administrar_negocios_view");
	}

	private void stepValidateInformacionGeneral() {
		waitUntilVisibleByText("Información General");
		assertAnyVisibleText("BUSINESS PLAN", "Business Plan");
		assertVisibleText("Cambiar Plan");

		final String pageText = normalizeText(driver.findElement(By.tagName("body")).getText());
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(pageText);
		Assert.assertTrue("User email should be visible in Información General.", emailMatcher.find());

		final boolean hasNameLabel = containsTextIgnoreCase(pageText, "nombre") || containsTextIgnoreCase(pageText, "name");
		Assert.assertTrue("User name should be visible in Información General.", hasNameLabel);
	}

	private void stepValidateDetallesCuenta() {
		waitUntilVisibleByText("Detalles de la Cuenta");
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitUntilVisibleByText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		final Optional<WebElement> section = findFirstVisible(Duration.ofSeconds(10),
				By.xpath("//*[self::section or self::div][.//*[contains(normalize-space(.), 'Tus Negocios')]]"));

		Assert.assertTrue("Tus Negocios section should be visible.", section.isPresent());
		final List<WebElement> candidates = section.get().findElements(
				By.xpath(".//*[@role='listitem' or self::li or self::tr or contains(@class, 'card')]"));
		Assert.assertTrue("Business list should be visible in Tus Negocios.", !candidates.isEmpty() || section.get().getText().trim().length() > 40);
	}

	private void stepValidateLegalDocument(final String linkText, final String screenshotName) throws Exception {
		final String applicationTab = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String urlBeforeClick = driver.getCurrentUrl();

		clickByTextCandidates(linkText);
		waitForUiToLoad();

		wait.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size() || !d.getCurrentUrl().equals(urlBeforeClick));

		boolean openedNewTab = false;
		Set<String> currentHandles = driver.getWindowHandles();
		if (currentHandles.size() > handlesBeforeClick.size()) {
			openedNewTab = true;
			for (final String handle : currentHandles) {
				if (!handlesBeforeClick.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		waitForUiToLoad();
		assertVisibleText(linkText);
		final String pageText = normalizeText(driver.findElement(By.tagName("body")).getText());
		Assert.assertTrue("Legal content text should be visible for " + linkText + ".", pageText.length() > 120);

		captureScreenshot("step_" + screenshotName);
		notes.add(linkText + " URL: " + driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(applicationTab);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
			waitUntilVisibleByText("Sección Legal");
		}
	}

	private void selectGoogleAccountIfPrompted() throws Exception {
		final Set<String> handles = new LinkedHashSet<>(driver.getWindowHandles());
		for (final String handle : handles) {
			driver.switchTo().window(handle);
			final Optional<WebElement> accountOption = findFirstVisible(Duration.ofSeconds(4),
					By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(GOOGLE_ACCOUNT_EMAIL) + ")]"));
			if (accountOption.isPresent()) {
				clickElement(accountOption.get());
				waitForUiToLoad();
				break;
			}
		}
	}

	private void runStep(final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			report.put(stepName, true);
		} catch (final Exception ex) {
			report.put(stepName, false);
			notes.add(stepName + " failed: " + ex.getMessage());
			try {
				captureScreenshot("failed_" + sanitizeName(stepName));
			} catch (final Exception ignored) {
				// Failure evidence is best-effort.
			}
		}
	}

	private void printReportAndAssert() {
		System.out.println("=== SaleADS Mi Negocio Full Test Report ===");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		if (!notes.isEmpty()) {
			System.out.println("--- Notes ---");
			for (final String note : notes) {
				System.out.println(note);
			}
		}

		final List<String> failed = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).collect(Collectors.toList());
		Assert.assertTrue("Some validation steps failed: " + failed, failed.isEmpty());
	}

	private Path createEvidenceDirectory() throws IOException {
		final String runStamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
		final Path baseDir = Path.of("target", "saleads-evidence", runStamp);
		return Files.createDirectories(baseDir);
	}

	private void captureScreenshot(final String screenshotName) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final Path target = evidenceDir.resolve(sanitizeName(screenshotName) + ".png");
		final java.io.File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		notes.add("Screenshot: " + target.toString());
	}

	private void clickByTextCandidates(final String... textCandidates) throws Exception {
		Exception lastError = null;
		for (final String candidate : textCandidates) {
			try {
				final Optional<WebElement> element = findFirstVisible(Duration.ofSeconds(10),
						By.xpath("//button[contains(normalize-space(.), " + toXPathLiteral(candidate) + ")]"),
						By.xpath("//a[contains(normalize-space(.), " + toXPathLiteral(candidate) + ")]"),
						By.xpath("//*[@role='button' and contains(normalize-space(.), " + toXPathLiteral(candidate) + ")]"),
						By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(candidate) + ")]"));
				if (element.isPresent()) {
					clickElement(element.get());
					return;
				}
			} catch (final Exception ex) {
				lastError = ex;
			}
		}
		throw new IllegalStateException("Could not find clickable element using text candidates: " + String.join(", ", textCandidates), lastError);
	}

	private void clickIfPresent(final String text) throws Exception {
		final Optional<WebElement> element = findFirstVisible(Duration.ofSeconds(4),
				By.xpath("//button[contains(normalize-space(.), " + toXPathLiteral(text) + ")]"),
				By.xpath("//a[contains(normalize-space(.), " + toXPathLiteral(text) + ")]"),
				By.xpath("//*[@role='button' and contains(normalize-space(.), " + toXPathLiteral(text) + ")]"),
				By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]"));
		if (element.isPresent()) {
			clickElement(element.get());
		}
	}

	private void clickElement(final WebElement element) throws Exception {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiToLoad();
	}

	private void waitUntilVisibleByText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]")));
	}

	private void assertVisibleText(final String text) {
		Assert.assertTrue("Expected text to be visible: " + text, isTextVisible(text));
	}

	private void assertAnyVisibleText(final String... textCandidates) {
		for (final String candidate : textCandidates) {
			if (isTextVisible(candidate)) {
				return;
			}
		}
		Assert.fail("None of the expected texts are visible: " + String.join(", ", textCandidates));
	}

	private boolean isTextVisible(final String text) {
		return findFirstVisible(Duration.ofSeconds(5),
				By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]")).isPresent();
	}

	private Optional<WebElement> findFirstVisible(final Duration timeout, final By... locators) {
		for (final By locator : locators) {
			try {
				final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
				final WebElement element = shortWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return Optional.of(element);
			} catch (final TimeoutException ignored) {
				// Try next locator.
			}
		}
		return Optional.empty();
	}

	private boolean isSidebarVisible() {
		return findFirstVisible(Duration.ofSeconds(5),
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[self::aside or self::nav][.//*[contains(normalize-space(.), 'Negocio')]]")).isPresent();
	}

	private void waitForUiToLoad() throws Exception {
		wait.until(driverInstance -> {
			try {
				return "complete".equals(((JavascriptExecutor) driverInstance).executeScript("return document.readyState"));
			} catch (final Exception ex) {
				return true;
			}
		});
		Thread.sleep(500);
	}

	private boolean isHeadlessEnabled() {
		final String value = firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"));
		return value == null || Boolean.parseBoolean(value);
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private String sanitizeName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	private String normalizeText(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace('\u00A0', ' ').trim();
	}

	private boolean containsTextIgnoreCase(final String haystack, final String needle) {
		return haystack.toLowerCase().contains(needle.toLowerCase());
	}

	private String toXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		final String[] parts = text.split("'");
		final StringBuilder result = new StringBuilder("concat(");
		for (int index = 0; index < parts.length; index++) {
			if (index > 0) {
				result.append(", \"'\", ");
			}
			result.append("'").append(parts[index]).append("'");
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
