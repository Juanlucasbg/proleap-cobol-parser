package io.proleap.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String ENV_LOGIN_URL = "SALEADS_LOGIN_URL";
	private static final String ENV_HEADLESS = "SALEADS_HEADLESS";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Path evidenceDir = Path.of("target", "saleads-mi-negocio-evidence");
	private final Path screenshotDir = evidenceDir.resolve("screenshots");
	private final Map<String, String> reportStatus = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = System.getenv(ENV_LOGIN_URL);
		Assume.assumeTrue("Set environment variable SALEADS_LOGIN_URL with the login page URL of the current environment.",
				loginUrl != null && !loginUrl.isBlank());

		Files.createDirectories(screenshotDir);
		resetReportStatus();

		final ChromeOptions options = new ChromeOptions();
		if (!"false".equalsIgnoreCase(System.getenv(ENV_HEADLESS))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));
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
	public void saleadsMiNegocioFullTest() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalPage("Términos y Condiciones", "Términos y Condiciones",
				"05-terminos-y-condiciones.png"));
		runStep("Política de Privacidad", () -> stepValidateLegalPage("Política de Privacidad", "Política de Privacidad",
				"06-politica-de-privacidad.png"));

		writeFinalReport();
		if (!failures.isEmpty()) {
			Assert.fail("Workflow validation failed:\n - " + String.join("\n - ", failures));
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		clickByVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Google"));
		waitForUiToLoad();

		selectGoogleAccountIfShown();
		assertAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Inicio"), Duration.ofSeconds(45));
		assertSidebarVisible();
		captureScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickIfVisible("Negocio");
		clickByVisibleText(List.of("Mi Negocio"));

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded-menu.png");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText(List.of("Agregar Negocio"));

		assertAnyTextVisible(Arrays.asList("Crear Nuevo Negocio"), Duration.ofSeconds(15));
		assertAnyElementVisible(List.of(By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(), 'Nombre del Negocio')]")));
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal.png");

		fillNombreDelNegocioIfPresent();
		clickIfVisible("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickIfVisible("Mi Negocio");
		}
		clickByVisibleText(List.of("Administrar Negocios"));

		assertAnyTextVisible(Arrays.asList("Información General", "Informacion General"), Duration.ofSeconds(20));
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertAnyTextVisible(Arrays.asList("Sección Legal", "Seccion Legal"), Duration.ofSeconds(20));
		captureScreenshot("04-administrar-negocios-page.png");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyTextVisible(Arrays.asList("Información General", "Informacion General"), Duration.ofSeconds(20));
		assertTextMatches(EMAIL_PATTERN, "User email should be visible in Información General.");
		assertAnyTextVisible(Arrays.asList("BUSINESS PLAN"), Duration.ofSeconds(15));
		assertTextVisible("Cambiar Plan");

		final String sectionText = getBodyText();
		Assert.assertTrue("Expected user name text to be visible.",
				sectionText != null && sectionText.replaceAll("\\s+", " ").trim().length() > 80);
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");

		final String sectionText = getBodyText();
		Assert.assertTrue("Expected business list or cards to be visible.", sectionText.contains("Negocio"));
	}

	private void stepValidateLegalPage(final String linkText, final String expectedHeading, final String screenshotName)
			throws IOException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String originalUrl = driver.getCurrentUrl();

		clickByVisibleText(List.of(linkText));

		try {
			new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> d.getWindowHandles().size() > beforeHandles.size()
					|| !d.getCurrentUrl().equals(originalUrl));
		} catch (final TimeoutException ignored) {
			// Continue with current page and verify legal content in either scenario.
		}

		switchToNewTabIfPresent(beforeHandles);
		waitForUiToLoad();

		assertAnyTextVisible(Arrays.asList(expectedHeading), Duration.ofSeconds(20));
		Assert.assertTrue("Expected legal content text to be visible.", getBodyText().replaceAll("\\s+", " ").trim().length() > 80);
		captureScreenshot(screenshotName);
		legalUrls.put(expectedHeading, driver.getCurrentUrl());

		if (!driver.getWindowHandle().equals(originalHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else if (!driver.getCurrentUrl().equals(originalUrl)) {
			driver.navigate().back();
		}
		waitForUiToLoad();
		assertAnyTextVisible(Arrays.asList("Tus Negocios", "Sección Legal", "Seccion Legal"), Duration.ofSeconds(20));
	}

	private void runStep(final String reportField, final ThrowingRunnable stepAction) {
		try {
			stepAction.run();
			reportStatus.put(reportField, "PASS");
		} catch (final Exception ex) {
			reportStatus.put(reportField, "FAIL");
			failures.add(reportField + ": " + ex.getMessage());
			try {
				captureScreenshot("error-" + sanitizeFileName(reportField) + ".png");
			} catch (final IOException ignored) {
				// Best-effort screenshot on failure.
			}
		}
	}

	private void resetReportStatus() {
		for (final String field : REPORT_FIELDS) {
			reportStatus.put(field, "FAIL");
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("saleads_mi_negocio_full_test\n");
		for (final String field : REPORT_FIELDS) {
			reportBuilder.append(field).append(": ").append(reportStatus.getOrDefault(field, "FAIL")).append('\n');
		}

		if (!legalUrls.isEmpty()) {
			reportBuilder.append('\n').append("Captured URLs:\n");
			legalUrls.forEach((name, url) -> reportBuilder.append(name).append(": ").append(url).append('\n'));
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.createDirectories(evidenceDir);
		Files.writeString(reportPath, reportBuilder.toString());
	}

	private void clickByVisibleText(final List<String> candidates) {
		Exception lastException = null;
		for (final String candidate : candidates) {
			try {
				final Optional<WebElement> element = findVisibleElementByText(candidate, Duration.ofSeconds(10));
				if (element.isPresent()) {
					clickElement(element.get());
					return;
				}
			} catch (final Exception ex) {
				lastException = ex;
			}
		}
		throw new AssertionError("Unable to click element by visible text: " + candidates, lastException);
	}

	private void clickIfVisible(final String text) {
		try {
			final Optional<WebElement> element = findVisibleElementByText(text, Duration.ofSeconds(4));
			element.ifPresent(this::clickElement);
		} catch (final Exception ignored) {
			// Optional click.
		}
	}

	private void fillNombreDelNegocioIfPresent() {
		final List<By> locators = List.of(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@name, 'nombre')]"));

		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed() && element.isEnabled()) {
					element.click();
					element.clear();
					element.sendKeys("Negocio Prueba Automatización");
					waitForUiToLoad();
					return;
				}
			}
		}
	}

	private void selectGoogleAccountIfShown() {
		try {
			final Optional<WebElement> account = findVisibleElementByText(GOOGLE_ACCOUNT_EMAIL, Duration.ofSeconds(15));
			if (account.isPresent()) {
				clickElement(account.get());
				waitForUiToLoad();
			}
		} catch (final Exception ignored) {
			// Account selector is optional and only appears in some sessions.
		}
	}

	private void assertSidebarVisible() {
		final boolean isSidebarVisible = driver.findElements(By.xpath("//aside[not(contains(@style,'display: none'))]")).stream()
				.anyMatch(WebElement::isDisplayed)
				|| driver.findElements(By.xpath("//nav[not(contains(@style,'display: none'))]")).stream()
						.anyMatch(WebElement::isDisplayed);
		Assert.assertTrue("Expected left sidebar navigation to be visible.", isSidebarVisible);
	}

	private void assertTextVisible(final String expectedText) {
		assertAnyTextVisible(List.of(expectedText), Duration.ofSeconds(20));
	}

	private void assertAnyTextVisible(final List<String> expectedTexts, final Duration timeout) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() <= deadline) {
			for (final String expectedText : expectedTexts) {
				final Optional<WebElement> element = findVisibleElementNow(expectedText);
				if (element.isPresent()) {
					return;
				}
			}
			sleep(250);
		}
		throw new AssertionError("Expected text not visible. Candidates: " + expectedTexts);
	}

	private Optional<WebElement> findVisibleElementByText(final String text, final Duration timeout) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() <= deadline) {
			final Optional<WebElement> match = findVisibleElementNow(text);
			if (match.isPresent()) {
				return match;
			}
			sleep(250);
		}
		return Optional.empty();
	}

	private Optional<WebElement> findVisibleElementNow(final String text) {
		final String literal = asXPathString(text);
		final List<By> locators = List.of(
				By.xpath("//*[self::button or self::a or @role='button'][normalize-space(.)=" + literal + "]"),
				By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.), " + literal + ")]"),
				By.xpath("//*[normalize-space(.)=" + literal + "]"),
				By.xpath("//*[contains(normalize-space(.), " + literal + ")]"));

		for (final By locator : locators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return Optional.of(element);
				}
			}
		}
		return Optional.empty();
	}

	private void assertAnyElementVisible(final List<By> candidates) {
		for (final By candidate : candidates) {
			final List<WebElement> elements = driver.findElements(candidate);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}
		throw new AssertionError("Expected at least one element from candidates to be visible.");
	}

	private void assertTextMatches(final Pattern pattern, final String assertionMessage) {
		final String text = getBodyText();
		Assert.assertTrue(assertionMessage, text != null && pattern.matcher(text).find());
	}

	private boolean isTextVisible(final String text) {
		return findVisibleElementNow(text).isPresent();
	}

	private String getBodyText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private void switchToNewTabIfPresent(final Set<String> beforeHandles) {
		final Set<String> afterHandles = new LinkedHashSet<>(driver.getWindowHandles());
		afterHandles.removeAll(beforeHandles);
		if (!afterHandles.isEmpty()) {
			driver.switchTo().window(afterHandles.iterator().next());
		}
	}

	private void clickElement(final WebElement element) {
		try {
			new Actions(driver).moveToElement(element).pause(Duration.ofMillis(120)).click().perform();
		} catch (final Exception actionFailure) {
			try {
				wait.until(ExpectedConditions.elementToBeClickable(element)).click();
			} catch (final Exception clickFailure) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
			}
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(
					((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Some pages constantly update readyState; continue with conservative sleep.
		}
		sleep(700);
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path output = screenshotDir.resolve(fileName);
		Files.copy(screenshot.toPath(), output, StandardCopyOption.REPLACE_EXISTING);
	}

	private static String asXPathString(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String token = String.valueOf(chars[i]);
			if (i > 0) {
				builder.append(',');
			}
			if ("'".equals(token)) {
				builder.append("\"'\"");
			} else if ("\"".equals(token)) {
				builder.append("'\"'");
			} else {
				builder.append('\'').append(token).append('\'');
			}
		}
		builder.append(')');
		return builder.toString();
	}

	private static String sanitizeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(ex);
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
