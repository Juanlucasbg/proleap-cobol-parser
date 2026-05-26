package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final DateTimeFormatter RUN_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final DateTimeFormatter EVIDENCE_FORMATTER = DateTimeFormatter.ofPattern("HHmmss-SSS");

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"Set -Dsaleads.login.url or SALEADS_LOGIN_URL. The test is environment-agnostic and does not hardcode domain.",
				loginUrl != null);

		final boolean headless = Boolean.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"),
				System.getenv("SALEADS_HEADLESS"), "true"));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(25));

		evidenceDir = Paths.get("target", "saleads-evidence", RUN_FORMATTER.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() throws IOException {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalDocument("Términos y Condiciones"));
		runStep("Política de Privacidad", () -> stepValidateLegalDocument("Política de Privacidad"));

		final List<String> failedSteps = stepResults.entrySet().stream().filter(e -> !e.getValue().passed)
				.map(e -> e.getKey() + " -> " + e.getValue().details).collect(Collectors.toList());
		assertFalse("Some validations failed: " + failedSteps, !failedSteps.isEmpty());
	}

	private void runStep(final String stepName, final CheckedRunnable stepImplementation) {
		try {
			stepImplementation.run();
			stepResults.put(stepName, StepResult.pass());
		} catch (final Throwable ex) {
			stepResults.put(stepName, StepResult.fail(ex.getMessage()));
			takeScreenshot("failure-" + slug(stepName));
		}
	}

	private void stepLoginWithGoogle() {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Iniciar con Google", "Continuar con Google",
				"Google", "GOOGLE");
		handleGoogleAccountSelectorIfPresent();
		assertNotStillInLoginScreen();
		assertAnyTextVisible("Mi Negocio", "Negocio");
		assertSidebarIsVisible();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		clickByVisibleText("Negocio", "Mi Negocio");
		clickByVisibleText("Mi Negocio");
		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");
		assertAnyTextVisible("Crear Nuevo Negocio");
		assertAnyTextVisible("Nombre del Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertAnyTextVisible("Cancelar");
		assertAnyTextVisible("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final WebElement businessNameInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')] | //input[contains(@aria-label, 'Nombre del Negocio')]")));
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.), 'Crear Nuevo Negocio')]")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(3))) {
			clickByVisibleText("Mi Negocio");
		}
		clickByVisibleText("Administrar Negocios");
		assertAnyTextVisible("Información General");
		assertAnyTextVisible("Detalles de la Cuenta");
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Sección Legal");
		takeScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyTextVisible("Información General");
		assertAnyTextVisible("BUSINESS PLAN");
		assertAnyTextVisible("Cambiar Plan");
		assertAnyTextVisible("Correo", "Correo Electrónico", "Email", "E-mail", ACCOUNT_EMAIL);
		assertHasEmailInPage();
		assertHasPossibleUserName();
	}

	private void stepValidateDetallesCuenta() {
		assertAnyTextVisible("Cuenta creada");
		assertAnyTextVisible("Estado activo");
		assertAnyTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertBusinessListIsVisible();
	}

	private void stepValidateLegalDocument(final String linkText) {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String urlBeforeClick = driver.getCurrentUrl();

		clickByVisibleText(linkText);

		boolean openedNewTab = false;
		try {
			wait.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size());
			openedNewTab = true;
		} catch (final TimeoutException timeout) {
			wait.until(d -> !d.getCurrentUrl().equals(urlBeforeClick));
		}

		if (openedNewTab) {
			final Set<String> handlesAfterClick = new LinkedHashSet<>(driver.getWindowHandles());
			handlesAfterClick.removeAll(handlesBeforeClick);
			final String newHandle = handlesAfterClick.iterator().next();
			driver.switchTo().window(newHandle);
		}

		waitForUiToLoad();
		assertAnyTextVisible(linkText);
		assertLegalContentVisible();
		takeScreenshot("legal-" + slug(linkText));
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void handleGoogleAccountSelectorIfPresent() {
		final Set<String> handlesAfterLoginClick = new LinkedHashSet<>(driver.getWindowHandles());
		if (handlesAfterLoginClick.size() > 1) {
			final String originalHandle = driver.getWindowHandle();
			for (final String handle : handlesAfterLoginClick) {
				if (!handle.equals(originalHandle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));
			final WebElement account = shortWait.until(ExpectedConditions.elementToBeClickable(
					By.xpath("//*[contains(normalize-space(.), " + asXpathLiteral(ACCOUNT_EMAIL) + ")]")));
			account.click();
			waitForUiToLoad();
		} catch (final TimeoutException ignored) {
			// Account picker is optional when the session is already authenticated.
		}

		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if (!driver.getCurrentUrl().toLowerCase(Locale.ROOT).contains("accounts.google")) {
				break;
			}
		}

		waitForUiToLoad();
	}

	private void clickByVisibleText(final String... candidateTexts) {
		Exception lastException = null;
		for (final String text : candidateTexts) {
			try {
				final WebElement element = wait.until(
						ExpectedConditions.elementToBeClickable(By.xpath(clickableElementXpathContains(text))));
				element.click();
				waitForUiToLoad();
				return;
			} catch (final Exception ex) {
				lastException = ex;
			}
		}
		throw new IllegalStateException("Could not click any of the expected texts: " + Arrays.toString(candidateTexts),
				lastException);
	}

	private void assertAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text, Duration.ofSeconds(8))) {
				return;
			}
		}
		throw new IllegalStateException("None of the expected texts are visible: " + Arrays.toString(texts));
	}

	private void assertSidebarIsVisible() {
		final List<By> sidebarLocators = Arrays.asList(By.tagName("aside"), By.xpath("//nav"),
				By.xpath("//*[contains(@class, 'sidebar')]"));
		for (final By locator : sidebarLocators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}
		throw new IllegalStateException("Left sidebar navigation is not visible.");
	}

	private void assertHasEmailInPage() {
		final List<WebElement> emails = driver.findElements(By.xpath(
				"//*[contains(normalize-space(.), '@') and contains(normalize-space(.), '.') and string-length(normalize-space(.)) < 120]"));
		assertFalse("No user email was detected in the account view.", emails.isEmpty());
	}

	private void assertHasPossibleUserName() {
		final List<WebElement> nameCandidates = driver.findElements(By.xpath(
				"//*[contains(normalize-space(.), 'Nombre') or contains(normalize-space(.), 'Usuario') or contains(normalize-space(.), 'Perfil')]"));
		assertFalse("No visible user name indicator was found.", nameCandidates.isEmpty());
	}

	private void assertBusinessListIsVisible() {
		final List<WebElement> businessRows = driver.findElements(By.xpath(
				"//*[contains(normalize-space(.), 'Tus Negocios')]/ancestor::*[self::section or self::div][1]//*[self::li or self::tr or contains(@class, 'business') or contains(@class, 'negocio')]"));
		assertFalse("Business list is not visible in 'Tus Negocios'.", businessRows.isEmpty());
	}

	private void assertLegalContentVisible() {
		final List<WebElement> paragraphs = driver
				.findElements(By.xpath("//p[string-length(normalize-space(.)) > 80] | //article//*[string-length(normalize-space(.)) > 80]"));
		assertFalse("Legal content text is not visible.", paragraphs.isEmpty());
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout)
					.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*["
							+ caseInsensitiveContainsExpression("normalize-space(.)", text)
							+ " and not(self::script) and not(self::style)]")));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void assertNotStillInLoginScreen() {
		final boolean loginTitleVisible = isTextVisible("Sign in to your account", Duration.ofSeconds(3));
		final boolean googleButtonVisible = isTextVisible("GOOGLE", Duration.ofSeconds(3))
				|| isTextVisible("Google", Duration.ofSeconds(3));
		if (loginTitleVisible && googleButtonVisible) {
			throw new IllegalStateException(
					"Google authentication did not complete. Login screen is still visible, likely due missing/blocked Google session.");
		}
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete"
				.equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(350L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for UI to settle.", interruptedException);
		}
	}

	private void takeScreenshot(final String checkpointName) {
		if (driver == null) {
			return;
		}
		try {
			final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			final String filename = EVIDENCE_FORMATTER.format(LocalDateTime.now()) + "-" + slug(checkpointName) + ".png";
			Files.write(evidenceDir.resolve(filename), screenshot);
		} catch (final Exception ignored) {
			// Screenshot capture is best effort to keep test flow running.
		}
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}
		for (final String field : REPORT_FIELDS) {
			stepResults.putIfAbsent(field, StepResult.fail("Step was not executed."));
		}

		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"results\": {\n");
		final List<String> entries = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			final StepResult result = stepResults.get(field);
			entries.add("    " + quote(field) + ": " + quote(result.passed ? "PASS" : "FAIL"));
		}
		json.append(String.join(",\n", entries));
		json.append("\n  },\n");

		json.append("  \"details\": {\n");
		final List<String> detailEntries = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			detailEntries.add("    " + quote(field) + ": " + quote(stepResults.get(field).details));
		}
		json.append(String.join(",\n", detailEntries));
		json.append("\n  },\n");

		json.append("  \"legal_urls\": {\n");
		final List<String> legalEntries = new ArrayList<>();
		legalEntries.add("    " + quote("Términos y Condiciones") + ": "
				+ quote(legalUrls.getOrDefault("Términos y Condiciones", "N/A")));
		legalEntries.add(
				"    " + quote("Política de Privacidad") + ": " + quote(legalUrls.getOrDefault("Política de Privacidad", "N/A")));
		json.append(String.join(",\n", legalEntries));
		json.append("\n  }\n");
		json.append("}\n");

		final Path reportPath = evidenceDir.resolve("final-report.json");
		Files.writeString(reportPath, json.toString(), StandardCharsets.UTF_8);
		System.out.println("SaleADS Mi Negocio evidence: " + evidenceDir.toAbsolutePath());
		System.out.println("SaleADS Mi Negocio final report: " + reportPath.toAbsolutePath());
	}

	private String clickableElementXpathContains(final String text) {
		return "(//*[self::button or self::a or self::li or self::div or self::span or @role='button' or @role='menuitem']["
				+ caseInsensitiveContainsExpression("normalize-space(.)", text) + "])[1]";
	}

	private String caseInsensitiveContainsExpression(final String sourceExpression, final String expectedText) {
		final String uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		final String lowercase = "abcdefghijklmnopqrstuvwxyz";
		final String expectedLower = expectedText.toLowerCase(Locale.ROOT);
		return "contains(translate(" + sourceExpression + ", " + asXpathLiteral(uppercase) + ", "
				+ asXpathLiteral(lowercase) + "), " + asXpathLiteral(expectedLower) + ")";
	}

	private String quote(final String value) {
		final String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
				.replace("\r", "\\r").replace("\t", "\\t");
		return "\"" + escaped + "\"";
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final String joined = Arrays.stream(parts).map(part -> "'" + part + "'").collect(Collectors.joining(", \"'\", "));
		return "concat(" + joined + ")";
	}

	private String slug(final String raw) {
		return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "").replaceAll("-+$", "");
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, "Validation passed.");
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, firstNonBlankStatic(details, "Validation failed."));
		}

		private static String firstNonBlankStatic(final String... values) {
			for (final String value : values) {
				if (value != null && !value.isBlank()) {
					return value;
				}
			}
			return "Validation failed.";
		}
	}
}
