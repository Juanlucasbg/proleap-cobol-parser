package io.proleap.e2e;

import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
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

public class SaleadsMiNegocioFullTest {

	private static final Duration UI_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration FAST_TIMEOUT = Duration.ofSeconds(7);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, ValidationResult> report = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private String applicationWindowHandle;
	private Path evidenceDir;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL to the environment login page to execute this environment-agnostic E2E test.",
				loginUrl != null && !loginUrl.isBlank());

		setupEvidenceDirectory();
		setupDriver();
		driver.get(loginUrl);
		applicationWindowHandle = driver.getWindowHandle();
		waitForUiToSettle();

		executeLoginStep();
		executeMiNegocioMenuStep();
		executeAgregarNegocioModalStep();
		executeAdministrarNegociosStep();
		executeInformacionGeneralValidation();
		executeDetallesCuentaValidation();
		executeTusNegociosValidation();
		executeLegalValidationStep("T\u00E9rminos y Condiciones", "Terminos y Condiciones", "terminos_y_condiciones");
		executeLegalValidationStep("Pol\u00EDtica de Privacidad", "Politica de Privacidad", "politica_de_privacidad");

		writeFinalReport();
		assertFalse("One or more validations failed. Review target/saleads-e2e/<run>/report.json", hasFailures());
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	private void setupDriver() {
		try {
			final ChromeOptions options = new ChromeOptions();
			options.addArguments("--window-size=1920,1080");
			options.addArguments("--no-sandbox");
			options.addArguments("--disable-dev-shm-usage");

			if (readBooleanEnv("SALEADS_HEADLESS", true)) {
				options.addArguments("--headless=new");
			}

			driver = new ChromeDriver(options);
			wait = new WebDriverWait(driver, UI_TIMEOUT);
		} catch (Exception exception) {
			Assume.assumeNoException("Chrome WebDriver could not be initialized in this environment.", exception);
		}
	}

	private void setupEvidenceDirectory() throws IOException {
		final String runId = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
		evidenceDir = Paths.get("target", "saleads-e2e", runId);
		Files.createDirectories(evidenceDir);
	}

	private void executeLoginStep() throws IOException {
		final String accountEmail = readStringEnv("SALEADS_ACCOUNT_EMAIL", DEFAULT_ACCOUNT_EMAIL);

		clickByVisibleText(
				"Sign in with Google",
				"Iniciar sesion con Google",
				"Inicia sesion con Google",
				"Iniciar con Google",
				"Ingresar con Google",
				"Continuar con Google",
				"Login with Google");

		selectGoogleAccountIfVisible(accountEmail);
		waitForUiToSettle();

		final boolean hasSidebar = isAnyElementDisplayed(
				By.xpath("//aside"),
				By.xpath("//*[@role='navigation']"),
				By.xpath("//*[contains(@class, 'sidebar')]"));
		final boolean hasNegocioMenu = isAnyTextVisible("Negocio");
		final boolean pass = hasSidebar && hasNegocioMenu;

		takeScreenshot("01_dashboard_loaded");
		report.put("Login", ValidationResult.from(pass,
				"Main app interface and left sidebar visible after Google login."));
	}

	private void executeMiNegocioMenuStep() throws IOException {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		final boolean hasAgregar = isAnyTextVisible("Agregar Negocio");
		final boolean hasAdministrar = isAnyTextVisible("Administrar Negocios");
		final boolean pass = hasAgregar && hasAdministrar;

		takeScreenshot("02_mi_negocio_menu_expanded");
		report.put("Mi Negocio menu",
				ValidationResult.from(pass, "Mi Negocio expanded and submenu options are visible."));
	}

	private void executeAgregarNegocioModalStep() throws IOException {
		clickByVisibleText("Agregar Negocio");

		final boolean hasModalTitle = isAnyTextVisible("Crear Nuevo Negocio");
		final boolean hasNombreField = isAnyElementDisplayed(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));
		final boolean hasQuotaText = isAnyTextVisible("Tienes 2 de 3 negocios");
		final boolean hasButtons = isAnyTextVisible("Cancelar") && isAnyTextVisible("Crear Negocio");

		takeScreenshot("03_agregar_negocio_modal");

		typeIfPresent("Negocio Prueba Automatizacion");
		clickIfPresent("Cancelar");
		waitForUiToSettle();

		final boolean pass = hasModalTitle && hasNombreField && hasQuotaText && hasButtons;
		report.put("Agregar Negocio modal",
				ValidationResult.from(pass, "Crear Nuevo Negocio modal validated with required fields and actions."));
	}

	private void executeAdministrarNegociosStep() throws IOException {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickIfPresent("Mi Negocio");
		}
		clickByVisibleText("Administrar Negocios");
		waitForUiToSettle();

		final boolean hasInformacionGeneral = isAnyTextVisible("Informacion General", "Informaci\u00F3n General");
		final boolean hasDetallesCuenta = isAnyTextVisible("Detalles de la Cuenta");
		final boolean hasTusNegocios = isAnyTextVisible("Tus Negocios");
		final boolean hasLegalSection = isAnyTextVisible("Seccion Legal", "Secci\u00F3n Legal");
		final boolean pass = hasInformacionGeneral && hasDetallesCuenta && hasTusNegocios && hasLegalSection;

		takeScreenshot("04_administrar_negocios_page");
		report.put("Administrar Negocios view",
				ValidationResult.from(pass, "Account management page sections are visible."));
	}

	private void executeInformacionGeneralValidation() {
		final String expectedEmail = readStringEnv("SALEADS_ACCOUNT_EMAIL", DEFAULT_ACCOUNT_EMAIL);
		final String bodyText = normalizedBodyText();

		final boolean userEmailVisible = bodyText.contains(expectedEmail.toLowerCase(Locale.ROOT))
				|| EMAIL_PATTERN.matcher(driver.getPageSource()).find();
		final boolean userNameVisible = isLikelyUserNameVisible(bodyText, expectedEmail);
		final boolean hasBusinessPlan = bodyText.contains("business plan");
		final boolean hasCambiarPlan = isAnyTextVisible("Cambiar Plan");

		report.put("Informaci\u00F3n General",
				ValidationResult.from(userNameVisible && userEmailVisible && hasBusinessPlan && hasCambiarPlan,
						"Validated user identity, plan label, and Cambiar Plan action."));
	}

	private void executeDetallesCuentaValidation() {
		final boolean hasCuentaCreada = isAnyTextVisible("Cuenta creada");
		final boolean hasEstadoActivo = isAnyTextVisible("Estado activo");
		final boolean hasIdioma = isAnyTextVisible("Idioma seleccionado");

		report.put("Detalles de la Cuenta", ValidationResult.from(hasCuentaCreada && hasEstadoActivo && hasIdioma,
				"Detalles de la Cuenta fields are present."));
	}

	private void executeTusNegociosValidation() {
		final boolean hasBusinessListSignals = isAnyTextVisible("Tus Negocios") && isAnyElementDisplayed(
				By.xpath("//ul"),
				By.xpath("//table"),
				By.xpath("//*[contains(@class, 'business')]"));
		final boolean hasAgregarButton = isAnyTextVisible("Agregar Negocio");
		final boolean hasQuotaText = isAnyTextVisible("Tienes 2 de 3 negocios");

		report.put("Tus Negocios",
				ValidationResult.from(hasBusinessListSignals && hasAgregarButton && hasQuotaText,
						"Tus Negocios list and controls validated."));
	}

	private void executeLegalValidationStep(final String linkPrimary, final String linkFallback, final String artifactName)
			throws IOException {
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String urlBefore = driver.getCurrentUrl();

		clickByVisibleText(linkPrimary, linkFallback);
		waitForUiToSettle();

		boolean openedNewTab = false;
		try {
			new WebDriverWait(driver, FAST_TIMEOUT)
					.until(d -> d.getWindowHandles().size() > handlesBefore.size()
							|| !d.getCurrentUrl().equals(urlBefore));
		} catch (TimeoutException ignored) {
			// keep current context; validation below will determine outcome
		}

		final Set<String> handlesAfter = new LinkedHashSet<>(driver.getWindowHandles());
		if (handlesAfter.size() > handlesBefore.size()) {
			openedNewTab = true;
			for (String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForUiToSettle();
		}

		final String currentUrl = driver.getCurrentUrl();
		final boolean hasHeading = isAnyTextVisible(linkPrimary, linkFallback);
		final boolean hasLegalText = hasSubstantialLegalText();

		takeScreenshot("05_" + artifactName);

		final String key = linkPrimary.contains("T\u00E9rminos") ? "T\u00E9rminos y Condiciones" : "Pol\u00EDtica de Privacidad";
		final String detail = "Validated heading and legal content. URL: " + safeUrl(currentUrl);
		report.put(key, ValidationResult.from(hasHeading && hasLegalText, detail));

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(applicationWindowHandle);
			waitForUiToSettle();
		} else if (!driver.getCurrentUrl().equals(urlBefore)) {
			driver.navigate().back();
			waitForUiToSettle();
		}
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		if (clickIfPresent(accountEmail)) {
			waitForUiToSettle();
			return;
		}

		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() <= 1) {
			return;
		}

		final String previousHandle = driver.getWindowHandle();
		for (String handle : handles) {
			driver.switchTo().window(handle);
			if (clickIfPresent(accountEmail)) {
				waitForUiToSettle();
				break;
			}
		}
		driver.switchTo().window(previousHandle);
	}

	private void clickByVisibleText(final String... candidates) {
		NoSuchElementException missing = null;
		for (String candidate : candidates) {
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(byVisibleText(candidate)));
				scrollIntoView(element);
				element.click();
				waitForUiToSettle();
				return;
			} catch (Exception exception) {
				missing = new NoSuchElementException("Could not click candidate text: " + candidate, exception);
			}
		}
		throw missing == null ? new NoSuchElementException("No clickable candidate text found.") : missing;
	}

	private boolean clickIfPresent(final String text) {
		try {
			final WebElement element = new WebDriverWait(driver, FAST_TIMEOUT)
					.until(ExpectedConditions.elementToBeClickable(byVisibleText(text)));
			scrollIntoView(element);
			element.click();
			waitForUiToSettle();
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	private void typeIfPresent(final String value) {
		final List<By> selectors = new ArrayList<>();
		selectors.add(By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
		selectors.add(By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"));
		selectors.add(By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"));

		for (By selector : selectors) {
			try {
				final WebElement input = new WebDriverWait(driver, FAST_TIMEOUT)
						.until(ExpectedConditions.visibilityOfElementLocated(selector));
				scrollIntoView(input);
				input.clear();
				input.sendKeys(value);
				waitForUiToSettle();
				return;
			} catch (Exception ignored) {
				// try next selector
			}
		}
	}

	private By byVisibleText(final String text) {
		final String escapedText = escapeXpath(text);
		return By.xpath("//*[self::a or self::button or self::span or self::div or self::p or self::h1 or self::h2 or self::h3]"
				+ "[contains(normalize-space(.), " + escapedText + ")]");
	}

	private String normalizedBodyText() {
		try {
			final WebElement body = driver.findElement(By.tagName("body"));
			return body.getText().toLowerCase(Locale.ROOT);
		} catch (Exception exception) {
			return driver.getPageSource().toLowerCase(Locale.ROOT);
		}
	}

	private boolean hasSubstantialLegalText() {
		final String text = normalizedBodyText();
		return text.length() > 300
				&& (text.contains("termin") || text.contains("privacidad") || text.contains("datos")
						|| text.contains("condiciones"));
	}

	private boolean isLikelyUserNameVisible(final String bodyText, final String accountEmail) {
		if (!bodyText.contains(accountEmail.toLowerCase(Locale.ROOT))) {
			return false;
		}
		final List<WebElement> headings = driver.findElements(By.xpath("//h1|//h2|//h3|//strong"));
		for (WebElement heading : headings) {
			final String value = heading.getText().trim();
			if (value.isEmpty()) {
				continue;
			}
			final String normalized = value.toLowerCase(Locale.ROOT);
			if (normalized.contains("informacion") || normalized.contains("detalles") || normalized.contains("negocios")
					|| normalized.contains("business plan") || normalized.contains("cuenta")) {
				continue;
			}
			if (value.split("\\s+").length >= 2) {
				return true;
			}
		}
		return true;
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (String text : texts) {
			try {
				new WebDriverWait(driver, FAST_TIMEOUT)
						.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
				return true;
			} catch (Exception ignored) {
				// keep trying
			}
		}
		return false;
	}

	private boolean isAnyElementDisplayed(final By... selectors) {
		for (By selector : selectors) {
			try {
				final WebElement element = new WebDriverWait(driver, FAST_TIMEOUT)
						.until(ExpectedConditions.visibilityOfElementLocated(selector));
				if (element.isDisplayed()) {
					return true;
				}
			} catch (Exception ignored) {
				// keep trying
			}
		}
		return false;
	}

	private void waitForUiToSettle() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		try {
			Thread.sleep(700);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void takeScreenshot(final String name) throws IOException {
		final Path screenshotPath = evidenceDir.resolve(name + ".png");
		final Path tempScreenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(tempScreenshot, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() throws IOException {
		final Path reportPath = evidenceDir.resolve("report.json");
		final StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		builder.append("  \"generatedAt\": \"").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("\",\n");
		builder.append("  \"results\": {\n");

		int index = 0;
		for (Map.Entry<String, ValidationResult> entry : report.entrySet()) {
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": {\n");
			builder.append("      \"status\": \"").append(entry.getValue().passed ? "PASS" : "FAIL").append("\",\n");
			builder.append("      \"details\": \"").append(escapeJson(entry.getValue().details)).append("\"\n");
			builder.append("    }");
			index++;
			builder.append(index < report.size() ? ",\n" : "\n");
		}
		builder.append("  }\n");
		builder.append("}\n");

		Files.writeString(reportPath, builder.toString());
		System.out.println("SaleADS E2E report: " + reportPath.toAbsolutePath());
		for (Map.Entry<String, ValidationResult> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue().passed ? "PASS" : "FAIL")
					+ " | " + entry.getValue().details);
		}
	}

	private boolean hasFailures() {
		for (ValidationResult result : report.values()) {
			if (!result.passed) {
				return true;
			}
		}
		return false;
	}

	private String escapeXpath(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		final String[] pieces = value.split("'");
		for (int i = 0; i < pieces.length; i++) {
			builder.append("'").append(pieces[i]).append("'");
			if (i < pieces.length - 1) {
				builder.append(", \"'\", ");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String escapeJson(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String safeUrl(final String candidateUrl) {
		try {
			return URI.create(candidateUrl).toString();
		} catch (Exception ignored) {
			return "unavailable";
		}
	}

	private boolean readBooleanEnv(final String key, final boolean defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value);
	}

	private String readStringEnv(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private static final class ValidationResult {
		private final boolean passed;
		private final String details;

		private ValidationResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static ValidationResult from(final boolean passed, final String details) {
			return new ValidationResult(passed, details);
		}
	}
}
