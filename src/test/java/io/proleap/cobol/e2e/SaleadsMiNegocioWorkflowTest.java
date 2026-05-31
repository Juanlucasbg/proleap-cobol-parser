package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Assume;
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

/**
 * Environment-agnostic SaleADS workflow validation for "Mi Negocio".
 *
 * This test intentionally avoids a hardcoded domain. Set SALEADS_START_URL to
 * point to whichever environment is under validation.
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withLocale(Locale.ROOT).withZone(ZoneOffset.UTC);

	private final Map<String, StepOutcome> outcomes = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String expectedGoogleEmail;
	private String expectedUserName;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String startUrl = env("SALEADS_START_URL", "").trim();
		Assume.assumeTrue("Set SALEADS_START_URL to run SaleADS UI automation.", !startUrl.isEmpty());

		expectedGoogleEmail = env("SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com").trim();
		expectedUserName = env("SALEADS_EXPECTED_USER_NAME", "").trim();
		evidenceDir = initEvidenceDir();

		driver = buildDriver();
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		try {
			runStep("Login", () -> stepLoginWithGoogle(startUrl));
			runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			runStep("Información General", this::stepValidateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			runStep("Tus Negocios", this::stepValidateTusNegocios);
			runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones"));
			runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad"));
		} finally {
			writeFinalReport();

			if (driver != null) {
				driver.quit();
			}
		}

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepOutcome> entry : outcomes.entrySet()) {
			if (!entry.getValue().pass) {
				failedSteps.add(entry.getKey() + ": " + entry.getValue().detail);
			}
		}

		if (!failedSteps.isEmpty()) {
			fail("SaleADS workflow validation failed:\n - " + String.join("\n - ", failedSteps));
		}
	}

	private WebDriver buildDriver() {
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1440,1000");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--lang=es-ES");

		final ChromeDriver chrome = new ChromeDriver(options);
		chrome.manage().window().setSize(new Dimension(1440, 1000));
		return chrome;
	}

	private void stepLoginWithGoogle(final String startUrl) throws Exception {
		driver.get(startUrl);
		waitForUiLoad();

		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiLoad();

		optionalClickByVisibleText(expectedGoogleEmail);

		assertVisibleTextAnyOf("Negocio", "Mi Negocio", "Dashboard", "Inicio");
		waitForAnyVisible(By.xpath("//aside | //nav"), Duration.ofSeconds(30));

		final Path screenshot = captureScreenshot("01-dashboard-load");
		setStepDetail("Dashboard loaded. Screenshot: " + screenshot.getFileName());
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		waitForUiLoad();
		clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");

		final Path screenshot = captureScreenshot("02-mi-negocio-expanded-menu");
		setStepDetail("Mi Negocio menu expanded. Screenshot: " + screenshot.getFileName());
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForUiLoad();

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		optionalTypeInField("Nombre del Negocio", "Negocio Prueba Automatización");

		final Path screenshot = captureScreenshot("03-agregar-negocio-modal");
		clickByVisibleText("Cancelar");
		waitForUiLoad();

		setStepDetail("Agregar Negocio modal validated. Screenshot: " + screenshot.getFileName());
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");

		final Path screenshot = captureScreenshot("04-administrar-negocios-view");
		setStepDetail("Administrar Negocios open. Screenshot: " + screenshot.getFileName());
	}

	private void stepValidateInformacionGeneral() {
		final String infoText = containerTextByHeading("Información General");

		assertTrue("User name is not visible.", isUserNameVisible(infoText));
		assertTrue("User email is not visible.", EMAIL_PATTERN.matcher(infoText).find());
		assertTrue("'BUSINESS PLAN' is not visible.", infoText.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN"));
		assertTrue("'Cambiar Plan' button text is not visible.", infoText.contains("Cambiar Plan"));

		setStepDetail("Información General validated.");
	}

	private void stepValidateDetallesCuenta() {
		final String detailsText = containerTextByHeading("Detalles de la Cuenta");

		assertTrue("'Cuenta creada' is not visible.", containsIgnoreCase(detailsText, "Cuenta creada"));
		assertTrue("'Estado activo' is not visible.",
				containsIgnoreCase(detailsText, "Estado activo") || containsIgnoreCase(detailsText, "Estado: activo"));
		assertTrue("'Idioma seleccionado' is not visible.", containsIgnoreCase(detailsText, "Idioma seleccionado"));

		setStepDetail("Detalles de la Cuenta validated.");
	}

	private void stepValidateTusNegocios() {
		final String businessText = containerTextByHeading("Tus Negocios");

		assertTrue("Business list section content is not visible.", businessText.length() > 30);
		assertTrue("'Agregar Negocio' is not visible.", containsIgnoreCase(businessText, "Agregar Negocio"));
		assertTrue("'Tienes 2 de 3 negocios' is not visible.", containsIgnoreCase(businessText, "Tienes 2 de 3 negocios"));

		setStepDetail("Tus Negocios validated.");
	}

	private void stepValidateLegalLink(final String legalText) throws Exception {
		assertVisibleText("Sección Legal");

		final String originalWindow = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickByVisibleText(legalText);
		waitForUiLoad();

		boolean switchedToNewTab = false;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(15)).until(d -> {
				final Set<String> handles = d.getWindowHandles();
				return handles.size() > beforeHandles.size() || !d.getCurrentUrl().equals(originalUrl);
			});
		} catch (final TimeoutException e) {
			// No hard failure here: in some environments legal links can still load in-place.
		}

		final Set<String> afterHandles = driver.getWindowHandles();
		if (afterHandles.size() > beforeHandles.size()) {
			switchedToNewTab = true;
			for (final String handle : afterHandles) {
				if (!beforeHandles.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForUiLoad();
		}

		assertVisibleText(legalText);
		final String bodyText = visibleBodyText();
		assertTrue("Legal content text is not visible for " + legalText + ".", bodyText.replaceAll("\\s+", " ").length() > 200);

		final Path screenshot = captureScreenshot(legalText.equals("Términos y Condiciones")
				? "08-terminos-condiciones"
				: "09-politica-privacidad");
		final String finalUrl = driver.getCurrentUrl();

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		assertVisibleText("Sección Legal");
		setStepDetail("Validated at URL: " + finalUrl + ". Screenshot: " + screenshot.getFileName());
	}

	private void runStep(final String name, final ThrowingRunnable runnable) {
		outcomes.put(name, StepOutcome.pass("PASS"));
		try {
			runnable.run();
			final StepOutcome current = outcomes.get(name);
			if (current != null && (current.detail == null || current.detail.isBlank())) {
				outcomes.put(name, StepOutcome.pass("PASS"));
			}
		} catch (final Throwable t) {
			outcomes.put(name, StepOutcome.fail(t.getMessage() == null ? t.toString() : t.getMessage()));
		}
	}

	private void setStepDetail(final String detail) {
		final Optional<Map.Entry<String, StepOutcome>> latest = outcomes.entrySet().stream().reduce((first, second) -> second);
		if (latest.isPresent()) {
			outcomes.put(latest.get().getKey(), StepOutcome.pass(detail));
		}
	}

	private void clickByVisibleText(final String... texts) {
		Throwable lastError = null;
		for (final String text : texts) {
			final String literal = xPathLiteral(text);
			final List<By> locators = List.of(
					By.xpath("//button[contains(normalize-space(.), " + literal + ")]"),
					By.xpath("//a[contains(normalize-space(.), " + literal + ")]"),
					By.xpath("//*[@role='button' and contains(normalize-space(.), " + literal + ")]"),
					By.xpath("//*[contains(normalize-space(.), " + literal
							+ ")]/ancestor::*[self::button or self::a or @role='button'][1]"));

			for (final By locator : locators) {
				try {
					final WebElement element = waitForAnyVisible(locator, Duration.ofSeconds(6));
					safeClick(element);
					waitForUiLoad();
					return;
				} catch (final Throwable t) {
					lastError = t;
				}
			}
		}

		throw new AssertionError("Could not click any visible element with text options: " + String.join(", ", texts),
				lastError);
	}

	private void optionalClickByVisibleText(final String text) {
		try {
			clickByVisibleText(text);
		} catch (final Throwable ignored) {
			// Google account selector may not appear if session is already authenticated.
		}
	}

	private void optionalTypeInField(final String labelText, final String value) {
		final String literal = xPathLiteral(labelText);
		final List<By> locators = List.of(
				By.xpath("//label[contains(normalize-space(.), " + literal + ")]/following::input[1]"),
				By.xpath("//input[@placeholder=" + literal + "]"),
				By.xpath("//input[contains(@aria-label, " + literal + ")]"));

		for (final By locator : locators) {
			final List<WebElement> found = driver.findElements(locator);
			for (final WebElement input : found) {
				if (input.isDisplayed() && input.isEnabled()) {
					input.clear();
					input.sendKeys(value);
					return;
				}
			}
		}
	}

	private void assertVisibleText(final String text) {
		assertTrue("Expected text is not visible: " + text, isTextVisible(text));
	}

	private void assertVisibleTextAnyOf(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return;
			}
		}
		fail("None of the expected texts are visible: " + String.join(", ", texts));
	}

	private boolean isTextVisible(final String text) {
		final String literal = xPathLiteral(text);
		final By locator = By.xpath("//*[contains(normalize-space(.), " + literal + ")]");
		return !driver.findElements(locator).stream().filter(WebElement::isDisplayed).toList().isEmpty();
	}

	private String containerTextByHeading(final String headingText) {
		assertVisibleText(headingText);
		final String literal = xPathLiteral(headingText);
		final By container = By.xpath("(//*[contains(normalize-space(.), " + literal
				+ ")]/ancestor::*[self::section or self::article or self::div][1])[1]");
		final WebElement element = waitForAnyVisible(container, Duration.ofSeconds(10));
		return element.getText();
	}

	private boolean isUserNameVisible(final String infoText) {
		if (!expectedUserName.isEmpty()) {
			return containsIgnoreCase(infoText, expectedUserName);
		}

		final String normalized = infoText.replace("\r", "");
		for (final String rawLine : normalized.split("\n")) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.length() < 3 || line.matches(".*\\d.*") || line.contains("@")) {
				continue;
			}

			final String lower = line.toLowerCase(Locale.ROOT);
			if (lower.contains("información general") || lower.contains("business plan") || lower.contains("cambiar plan")
					|| lower.contains("cuenta") || lower.contains("idioma") || lower.contains("negocio")) {
				continue;
			}
			return true;
		}

		return false;
	}

	private String visibleBodyText() {
		final WebElement body = waitForAnyVisible(By.tagName("body"), Duration.ofSeconds(10));
		return body.getText();
	}

	private WebElement waitForAnyVisible(final By by, final Duration timeout) {
		return new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private void safeClick(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
		} catch (final Throwable clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void waitForUiLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(500);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private Path captureScreenshot(final String prefix) throws IOException {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String filename = TS.format(Instant.now()) + "-" + sanitize(prefix) + ".png";
		final Path target = evidenceDir.resolve(filename);
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		return target;
	}

	private Path initEvidenceDir() throws IOException {
		final String defaultDir = "target/saleads-evidence/" + TS.format(Instant.now());
		final Path dir = Paths.get(env("SALEADS_EVIDENCE_DIR", defaultDir));
		Files.createDirectories(dir);
		return dir;
	}

	private void writeFinalReport() {
		try {
			final StringBuilder report = new StringBuilder();
			report.append("saleads_mi_negocio_full_test\n");
			report.append("GeneratedAt(UTC): ").append(Instant.now()).append('\n');
			report.append("EvidenceDir: ").append(evidenceDir.toAbsolutePath()).append("\n\n");
			report.append("Final Report\n");
			report.append("============\n");

			for (final Map.Entry<String, StepOutcome> entry : outcomes.entrySet()) {
				report.append("- ").append(entry.getKey()).append(": ")
						.append(entry.getValue().pass ? "PASS" : "FAIL");
				if (entry.getValue().detail != null && !entry.getValue().detail.isBlank()) {
					report.append(" (").append(entry.getValue().detail).append(')');
				}
				report.append('\n');
			}

			final Path reportPath = evidenceDir.resolve("final-report.txt");
			Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
		} catch (final Exception ignored) {
			// Report writing should not hide validation outcomes.
		}
	}

	private static String sanitize(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value;
	}

	private static boolean containsIgnoreCase(final String value, final String expected) {
		return value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
	}

	private static String xPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				result.append(", ");
			}
			if (chars[i] == '\'') {
				result.append("\"'\"");
			} else {
				result.append("'").append(chars[i]).append("'");
			}
		}
		result.append(")");
		return result.toString();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static class StepOutcome {
		private final boolean pass;
		private final String detail;

		private StepOutcome(final boolean pass, final String detail) {
			this.pass = pass;
			this.detail = detail;
		}

		private static StepOutcome pass(final String detail) {
			return new StepOutcome(true, detail);
		}

		private static StepOutcome fail(final String detail) {
			return new StepOutcome(false, detail);
		}
	}
}
