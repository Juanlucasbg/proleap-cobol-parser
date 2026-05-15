package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowE2ETest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private Path evidenceDir;
	private boolean environmentReady;
	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws IOException {
		final String configuredEvidenceDir = env("SALEADS_EVIDENCE_DIR");
		final String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
		evidenceDir = configuredEvidenceDir.isEmpty() ? Path.of("target", "saleads-evidence", timestamp)
				: Path.of(configuredEvidenceDir);
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (!"false".equalsIgnoreCase(env("SALEADS_HEADLESS"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		final String loginUrl = nonBlank(env("SALEADS_LOGIN_URL"), env("SALEADS_BASE_URL"));
		if (!loginUrl.isEmpty()) {
			driver.navigate().to(loginUrl);
			waitForUiReady();
			environmentReady = true;
		} else {
			environmentReady = false;
			failures.add(
					"No SALEADS_LOGIN_URL/SALEADS_BASE_URL configured. Selenium cannot attach to a pre-opened browser tab.");
			captureScreenshot("00-missing-login-url");
		}
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLogin);
		runStep("Mi Negocio menu", this::stepMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepAdministrarNegociosView);
		runStep("Información General", this::stepInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepDetallesCuenta);
		runStep("Tus Negocios", this::stepTusNegocios);
		runStep("Términos y Condiciones", this::stepTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepPoliticaPrivacidad);

		final boolean allPassed = report.values().stream().allMatch(Boolean::booleanValue);
		assertTrue("SaleADS Mi Negocio workflow failed. Review " + evidenceDir.resolve("final-report.md"), allPassed);
	}

	private boolean stepLogin() {
		if (!isSidebarVisible()) {
			if (!clickFirstVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
					"Continuar con Google", "Login with Google", "Google")) {
				failures.add("Login button with Google text was not found.");
				captureScreenshot("01-login-button-not-found");
				return false;
			}
			waitForUiReady();
			selectGoogleAccountIfPrompted();
		}

		final boolean mainUiVisible = isAnyVisible(
				By.xpath("//main"),
				By.xpath("//div[contains(@class,'dashboard') or contains(@class,'layout') or contains(@class,'app')]"));
		final boolean sidebarVisible = isSidebarVisible();
		captureScreenshot("01-dashboard-loaded");
		return mainUiVisible && sidebarVisible;
	}

	private boolean stepMiNegocioMenu() {
		clickFirstVisibleText("Negocio");
		clickFirstVisibleText("Mi Negocio");
		waitForUiReady();

		final boolean agregarVisible = isTextVisible("Agregar Negocio");
		final boolean administrarVisible = isTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
		return agregarVisible && administrarVisible;
	}

	private boolean stepAgregarNegocioModal() {
		if (!isTextVisible("Agregar Negocio")) {
			clickFirstVisibleText("Mi Negocio");
			waitForUiReady();
		}

		if (!clickFirstVisibleText("Agregar Negocio")) {
			failures.add("Could not click 'Agregar Negocio'.");
			captureScreenshot("03-agregar-negocio-click-failed");
			return false;
		}
		waitForUiReady();

		final boolean titleVisible = isAnyTextVisible("Crear Nuevo Negocio");
		final boolean inputVisible = isAnyVisible(
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]"),
				By.xpath("//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio']"),
				By.xpath("//input[contains(@name,'nombre') or contains(@id,'nombre')]"));
		final boolean limitVisible = isAnyTextVisible("Tienes 2 de 3 negocios");
		final boolean cancelVisible = isAnyTextVisible("Cancelar");
		final boolean createVisible = isAnyTextVisible("Crear Negocio");

		captureScreenshot("03-agregar-negocio-modal");

		findFirstVisible(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or contains(@name,'nombre') or contains(@id,'nombre')]"))
						.ifPresent(input -> {
							input.click();
							input.clear();
							input.sendKeys("Negocio Prueba Automatizacion");
						});

		clickFirstVisibleText("Cancelar");
		waitForUiReady();

		return titleVisible && inputVisible && limitVisible && cancelVisible && createVisible;
	}

	private boolean stepAdministrarNegociosView() {
		if (!isTextVisible("Administrar Negocios")) {
			clickFirstVisibleText("Mi Negocio");
			waitForUiReady();
		}

		if (!clickFirstVisibleText("Administrar Negocios")) {
			failures.add("Could not click 'Administrar Negocios'.");
			captureScreenshot("04-administrar-negocios-click-failed");
			return false;
		}
		waitForUiReady();

		final boolean infoGeneral = isAnyTextVisible("Información General", "Informacion General");
		final boolean detallesCuenta = isAnyTextVisible("Detalles de la Cuenta");
		final boolean tusNegocios = isAnyTextVisible("Tus Negocios");
		final boolean seccionLegal = isAnyTextVisible("Sección Legal", "Seccion Legal");

		captureScreenshot("04-administrar-negocios-view");
		return infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
	}

	private boolean stepInformacionGeneral() {
		final Optional<WebElement> section = findSection("Información General", "Informacion General");
		if (section.isEmpty()) {
			failures.add("'Información General' section was not found.");
			return false;
		}

		final String sectionText = section.get().getText();
		final boolean hasEmail = sectionText.contains("@");
		final boolean hasUserName = sectionText.lines().map(String::trim)
				.anyMatch(line -> !line.isEmpty() && !line.contains("@") && line.length() > 2
						&& !"INFORMACIÓN GENERAL".equalsIgnoreCase(line) && !"INFORMACION GENERAL".equalsIgnoreCase(line)
						&& !"BUSINESS PLAN".equalsIgnoreCase(line));
		final boolean planVisible = sectionText.contains("BUSINESS PLAN");
		final boolean changePlanVisible = isAnyVisible(By.xpath("//button[normalize-space()='Cambiar Plan']"),
				By.xpath("//a[normalize-space()='Cambiar Plan']"));

		return hasUserName && hasEmail && planVisible && changePlanVisible;
	}

	private boolean stepDetallesCuenta() {
		final Optional<WebElement> section = findSection("Detalles de la Cuenta");
		if (section.isEmpty()) {
			failures.add("'Detalles de la Cuenta' section was not found.");
			return false;
		}

		final String sectionText = section.get().getText();
		final boolean cuentaCreada = containsIgnoringCase(sectionText, "Cuenta creada");
		final boolean estadoActivo = containsIgnoringCase(sectionText, "Estado activo");
		final boolean idioma = containsIgnoringCase(sectionText, "Idioma seleccionado");
		return cuentaCreada && estadoActivo && idioma;
	}

	private boolean stepTusNegocios() {
		final Optional<WebElement> section = findSection("Tus Negocios");
		if (section.isEmpty()) {
			failures.add("'Tus Negocios' section was not found.");
			return false;
		}

		final boolean addButtonVisible = isAnyVisible(By.xpath("//button[normalize-space()='Agregar Negocio']"),
				By.xpath("//a[normalize-space()='Agregar Negocio']"));
		final boolean limitVisible = section.get().getText().contains("Tienes 2 de 3 negocios");
		final boolean businessListVisible = !section.get()
				.findElements(By.xpath(".//*[self::li or self::tr or contains(@class,'card') or contains(@class,'item')]"))
				.isEmpty();
		return businessListVisible && addButtonVisible && limitVisible;
	}

	private boolean stepTerminosYCondiciones() {
		return validateLegalLink("Términos y Condiciones", "Terminos y Condiciones", "08-terminos-y-condiciones");
	}

	private boolean stepPoliticaPrivacidad() {
		return validateLegalLink("Política de Privacidad", "Politica de Privacidad", "09-politica-privacidad");
	}

	private boolean validateLegalLink(final String titleWithAccent, final String titleWithoutAccent,
			final String screenshotName) {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		if (!clickFirstVisibleText(titleWithAccent, titleWithoutAccent)) {
			failures.add("Could not click legal link: " + titleWithAccent);
			return false;
		}

		waitForUiReady();

		boolean openedNewTab = false;
		try {
			final String newHandle = new WebDriverWait(driver, SHORT_TIMEOUT).until(d -> {
				for (final String handle : d.getWindowHandles()) {
					if (!handlesBefore.contains(handle)) {
						return handle;
					}
				}
				return null;
			});
			if (newHandle != null) {
				driver.switchTo().window(newHandle);
				openedNewTab = true;
			}
		} catch (final TimeoutException ignored) {
			// same-tab navigation is valid as well
		}

		waitForUiReady();

		final boolean headingVisible = isAnyTextVisible(titleWithAccent, titleWithoutAccent);
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final boolean legalContentVisible = bodyText != null && bodyText.trim().length() > 120;
		legalUrls.put(titleWithAccent, driver.getCurrentUrl());
		captureScreenshot(screenshotName);

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiReady();
		} else {
			driver.navigate().back();
			waitForUiReady();
		}

		return headingVisible && legalContentVisible;
	}

	private void runStep(final String fieldName, final CheckedStep step) {
		if (!environmentReady) {
			report.put(fieldName, false);
			failures.add(fieldName + " validation failed because environment is not initialized.");
			return;
		}

		try {
			final boolean passed = step.run();
			report.put(fieldName, passed);
			if (!passed) {
				failures.add(fieldName + " validation failed.");
			}
		} catch (final Exception ex) {
			report.put(fieldName, false);
			failures.add(fieldName + " failed with exception: " + ex.getMessage());
			captureScreenshot("error-" + sanitizeFileName(fieldName));
		}
	}

	private void selectGoogleAccountIfPrompted() {
		final String account = nonBlank(env("SALEADS_GOOGLE_ACCOUNT"), DEFAULT_GOOGLE_ACCOUNT);
		clickFirstVisibleText(account);
		waitForUiReady();
	}

	private Optional<WebElement> findSection(final String... headings) {
		for (final String heading : headings) {
			final Optional<WebElement> headingElement = findFirstVisible(
					By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::div][normalize-space()='"
							+ escapeXpathLiteral(heading) + "']"));
			if (headingElement.isPresent()) {
				final WebElement section = resolveSectionContainer(headingElement.get());
				return Optional.ofNullable(section);
			}
		}
		return Optional.empty();
	}

	private WebElement resolveSectionContainer(final WebElement headingElement) {
		try {
			return headingElement.findElement(By.xpath("./ancestor-or-self::*[self::section or self::article or self::div][1]"));
		} catch (final Exception ex) {
			return headingElement;
		}
	}

	private boolean isSidebarVisible() {
		return isAnyVisible(By.xpath("//aside"), By.xpath("//nav[.//*[contains(normalize-space(),'Negocio')]]"));
	}

	private boolean isTextVisible(final String text) {
		return isAnyTextVisible(text);
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			if (findFirstVisible(By.xpath("//*[normalize-space()='" + escapeXpathLiteral(text) + "']")).isPresent()) {
				return true;
			}
		}
		return false;
	}

	private boolean isAnyVisible(final By... locators) {
		for (final By locator : locators) {
			if (findFirstVisible(locator).isPresent()) {
				return true;
			}
		}
		return false;
	}

	private Optional<WebElement> findFirstVisible(final By locator) {
		try {
			final WebElement visible = new WebDriverWait(driver, SHORT_TIMEOUT).until(d -> d.findElements(locator).stream()
					.filter(WebElement::isDisplayed).findFirst().orElse(null));
			return Optional.ofNullable(visible);
		} catch (final TimeoutException ex) {
			return Optional.empty();
		}
	}

	private boolean clickFirstVisibleText(final String... texts) {
		for (final String text : texts) {
			final Optional<WebElement> candidate = findFirstVisible(By.xpath("//*[normalize-space()='"
					+ escapeXpathLiteral(text) + "']/ancestor-or-self::*[self::button or self::a or @role='button' or self::li or self::span or self::div][1]"));
			if (candidate.isPresent()) {
				try {
					candidate.get().click();
				} catch (final Exception clickError) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", candidate.get());
				}
				waitForUiReady();
				return true;
			}
		}
		return false;
	}

	private void waitForUiReady() {
		try {
			wait.until((ExpectedCondition<Boolean>) d -> {
				if (d == null) {
					return false;
				}
				final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(state) || "interactive".equals(state);
			});
		} catch (final Exception ignored) {
			// ignore and continue with best-effort waits
		}

		try {
			Thread.sleep(700);
		} catch (final InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String name) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		try {
			final Path screenshot = evidenceDir.resolve(sanitizeFileName(name) + ".png");
			final java.io.File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			Files.copy(source.toPath(), screenshot, StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception ex) {
			failures.add("Could not capture screenshot '" + name + "': " + ex.getMessage());
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("# SaleADS Mi Negocio Workflow Report\n\n");
		reportBuilder.append("Evidence directory: `").append(evidenceDir.toAbsolutePath()).append("`\n\n");
		reportBuilder.append("| Validation | Result |\n");
		reportBuilder.append("|---|---|\n");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			reportBuilder.append("| ").append(entry.getKey()).append(" | ")
					.append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL").append(" |\n");
		}

		if (!legalUrls.isEmpty()) {
			reportBuilder.append("\n## Legal URLs\n");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				reportBuilder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		if (!failures.isEmpty()) {
			reportBuilder.append("\n## Notes\n");
			for (final String failure : failures) {
				reportBuilder.append("- ").append(failure).append('\n');
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.md"), reportBuilder.toString());
	}

	private static String env(final String key) {
		final String value = System.getenv(key);
		return value == null ? "" : value.trim();
	}

	private static String nonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return "";
	}

	private static String sanitizeFileName(final String name) {
		return name.toLowerCase().replaceAll("[^a-z0-9\\-]+", "-").replaceAll("\\-+", "-").replaceAll("(^\\-|\\-$)", "");
	}

	private static String escapeXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return value;
		}

		final String[] tokens = value.split("'");
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < tokens.length; i++) {
			builder.append("'").append(tokens[i]).append("'");
			if (i < tokens.length - 1) {
				builder.append(",\"'\",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private static boolean containsIgnoringCase(final String text, final String expected) {
		return text.toLowerCase().contains(expected.toLowerCase());
	}

	@FunctionalInterface
	private interface CheckedStep {
		boolean run();
	}
}
