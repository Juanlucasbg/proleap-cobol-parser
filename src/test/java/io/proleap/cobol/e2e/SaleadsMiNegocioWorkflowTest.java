package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
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

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(25);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String SAMPLE_BUSINESS_NAME = "Negocio Prueba Automatizacion";
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private Path evidenceDirectory;
	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() throws IOException {
		evidenceDirectory = Files.createDirectories(Path.of("target", "saleads-e2e", TS_FORMAT.format(LocalDateTime.now())));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");

		final String headless = readValue("SALEADS_HEADLESS", "saleads.headless");

		if (headless == null || !"false".equalsIgnoreCase(headless.trim())) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String loginUrl = readValue("SALEADS_LOGIN_URL", "saleads.loginUrl");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL env var or -Dsaleads.loginUrl=<url> to execute SaleADS UI workflow.",
				loginUrl != null && !loginUrl.isBlank());

		driver.get(loginUrl);
		waitForUiLoad();

		runStep("Login", () -> {
			clickAnyText(Arrays.asList("Sign in with Google", "Iniciar sesion con Google", "Iniciar sesión con Google",
					"Login with Google", "Continuar con Google", "Ingresar con Google"), Duration.ofSeconds(20));
			waitForUiLoad();

			selectGoogleAccountIfPrompted();
			waitForUiLoad();

			assertTrue("Main application interface did not load.", isAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio",
					"Dashboard", "Panel"), Duration.ofSeconds(30)));
			assertTrue("Left sidebar is not visible.", isSidebarVisible(Duration.ofSeconds(30)));
			takeScreenshot("01-dashboard-loaded");
		});

		runStep("Mi Negocio menu", () -> {
			openMiNegocioMenu();
			assertTrue("'Agregar Negocio' is not visible.",
					isAnyTextVisible(Arrays.asList("Agregar Negocio"), Duration.ofSeconds(10)));
			assertTrue("'Administrar Negocios' is not visible.",
					isAnyTextVisible(Arrays.asList("Administrar Negocios"), Duration.ofSeconds(10)));
			takeScreenshot("02-mi-negocio-menu-expanded");
		});

		runStep("Agregar Negocio modal", () -> {
			clickAnyText(Arrays.asList("Agregar Negocio"), Duration.ofSeconds(10));
			waitForUiLoad();

			assertVisibleText("Crear Nuevo Negocio", Duration.ofSeconds(10));
			assertVisibleText("Nombre del Negocio", Duration.ofSeconds(10));
			assertVisibleText("Tienes 2 de 3 negocios", Duration.ofSeconds(10));
			assertVisibleText("Cancelar", Duration.ofSeconds(10));
			assertVisibleText("Crear Negocio", Duration.ofSeconds(10));
			takeScreenshot("03-agregar-negocio-modal");

			final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("(//input[@placeholder='Nombre del Negocio'] | //input[contains(@aria-label,'Nombre del Negocio')]"
							+ " | //input[contains(@name,'nombre')])[1]")));
			input.click();
			input.sendKeys(Keys.chord(Keys.CONTROL, "a"));
			input.sendKeys(SAMPLE_BUSINESS_NAME);
			waitForUiLoad();

			clickAnyText(Arrays.asList("Cancelar"), Duration.ofSeconds(10));
			waitForUiLoad();
		});

		runStep("Administrar Negocios view", () -> {
			openMiNegocioMenu();
			clickAnyText(Arrays.asList("Administrar Negocios"), Duration.ofSeconds(10));
			waitForUiLoad();

			assertTrue("Expected 'Información General' heading was not visible.",
					isAnyTextVisible(Arrays.asList("Informacion General", "Información General"), Duration.ofSeconds(15)));
			assertVisibleText("Detalles de la Cuenta", Duration.ofSeconds(15));
			assertVisibleText("Tus Negocios", Duration.ofSeconds(15));
			assertTrue("Expected legal section label was not visible.",
					isAnyTextVisible(Arrays.asList("Seccion Legal", "Sección Legal"), Duration.ofSeconds(15)));
			takeScreenshot("04-administrar-negocios-page");
		});

		runStep("Información General", () -> {
			assertTrue("User name was not visible.", hasVisibleElement(By.xpath(
					"//*[contains(@class,'info') or contains(@class,'profile') or contains(@class,'account')]//*[contains(text(),'@') or string-length(normalize-space(.)) > 3]"),
					Duration.ofSeconds(10)));
			assertTrue("User email was not visible.",
					hasVisibleElement(By.xpath("//*[contains(normalize-space(.), '@')]"), Duration.ofSeconds(10)));
			assertVisibleText("BUSINESS PLAN", Duration.ofSeconds(10));
			assertVisibleText("Cambiar Plan", Duration.ofSeconds(10));
		});

		runStep("Detalles de la Cuenta", () -> {
			assertVisibleText("Cuenta creada", Duration.ofSeconds(10));
			assertTrue("Expected active status text was not visible.",
					isAnyTextVisible(Arrays.asList("Estado activo", "Activo"), Duration.ofSeconds(10)));
			assertVisibleText("Idioma seleccionado", Duration.ofSeconds(10));
		});

		runStep("Tus Negocios", () -> {
			assertVisibleText("Tus Negocios", Duration.ofSeconds(10));
			assertVisibleText("Agregar Negocio", Duration.ofSeconds(10));
			assertVisibleText("Tienes 2 de 3 negocios", Duration.ofSeconds(10));
			assertTrue("Business list was not visible.",
					hasVisibleElement(By.xpath(
							"(//*[contains(@class,'business') or contains(@class,'negocio')] | //table | //ul)[1]"),
						Duration.ofSeconds(10)));
		});

		runStep("Términos y Condiciones", () -> {
			final LegalNavigationEvidence evidence = openLegalLink(
					Arrays.asList("Terminos y Condiciones", "Términos y Condiciones"),
					Arrays.asList("Terminos y Condiciones", "Términos y Condiciones"));
			writeTextFile("08-terminos-url.txt", evidence.finalUrl);
			takeScreenshot("08-terminos-y-condiciones");
			returnToApplication(evidence);
		});

		runStep("Política de Privacidad", () -> {
			final LegalNavigationEvidence evidence = openLegalLink(
					Arrays.asList("Politica de Privacidad", "Política de Privacidad"),
					Arrays.asList("Politica de Privacidad", "Política de Privacidad"));
			writeTextFile("09-politica-url.txt", evidence.finalUrl);
			takeScreenshot("09-politica-de-privacidad");
			returnToApplication(evidence);
		});

		writeFinalReport();
		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue().pass)
				.map(Map.Entry::getKey).collect(Collectors.toList());
		assertTrue("SaleADS Mi Negocio workflow failed in: " + failedSteps + ". Full report: "
				+ evidenceDirectory.resolve("final-report.txt"), failedSteps.isEmpty());
	}

	private void openMiNegocioMenu() {
		if (isAnyTextVisible(Arrays.asList("Negocio"), Duration.ofSeconds(5))) {
			clickAnyText(Arrays.asList("Negocio"), Duration.ofSeconds(5));
			waitForUiLoad();
		}

		clickAnyText(Arrays.asList("Mi Negocio"), Duration.ofSeconds(10));
		waitForUiLoad();
	}

	private void selectGoogleAccountIfPrompted() {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() > 1) {
			for (String handle : handles) {
				if (!handle.equals(appHandle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		if (isAnyTextVisible(Arrays.asList(GOOGLE_ACCOUNT_EMAIL), Duration.ofSeconds(8))) {
			clickAnyText(Arrays.asList(GOOGLE_ACCOUNT_EMAIL), Duration.ofSeconds(8));
			waitForUiLoad();
		}

		if (!driver.getWindowHandle().equals(appHandle) && driver.getWindowHandles().contains(appHandle)) {
			driver.switchTo().window(appHandle);
		}
	}

	private LegalNavigationEvidence openLegalLink(final List<String> linkLabelCandidates,
			final List<String> headingCandidates) {
		final LegalNavigationEvidence evidence = new LegalNavigationEvidence();
		evidence.applicationHandle = driver.getWindowHandle();
		evidence.applicationUrl = driver.getCurrentUrl();
		evidence.handlesBefore = driver.getWindowHandles();

		clickAnyText(linkLabelCandidates, Duration.ofSeconds(12));
		waitForUiLoad();

		final Set<String> handlesNow = driver.getWindowHandles();
		if (handlesNow.size() > evidence.handlesBefore.size()) {
			for (String handle : handlesNow) {
				if (!evidence.handlesBefore.contains(handle)) {
					evidence.legalHandle = handle;
					break;
				}
			}
			if (evidence.legalHandle != null) {
				driver.switchTo().window(evidence.legalHandle);
			}
		}

		assertTrue("Legal heading for " + linkLabelCandidates + " was not found.",
				isAnyTextVisible(headingCandidates, Duration.ofSeconds(20)));
		assertTrue("Legal content for " + linkLabelCandidates + " was not visible.",
				hasVisibleElement(By.xpath("//body//*[string-length(normalize-space(.)) > 80]"), Duration.ofSeconds(20)));
		evidence.finalUrl = driver.getCurrentUrl();

		return evidence;
	}

	private void returnToApplication(final LegalNavigationEvidence evidence) {
		if (evidence.legalHandle != null && driver.getWindowHandles().contains(evidence.legalHandle)) {
			driver.close();
			driver.switchTo().window(evidence.applicationHandle);
		} else if (!driver.getCurrentUrl().equals(evidence.applicationUrl)) {
			driver.navigate().back();
		}

		waitForUiLoad();
		assertTrue("Application tab was not restored after legal page validation.", isSidebarVisible(Duration.ofSeconds(20)));
	}

	private boolean isSidebarVisible(final Duration timeout) {
		return hasVisibleElement(By.xpath(
				"//aside | //nav[contains(@class,'sidebar')] | //div[contains(@class,'sidebar')] | //div[contains(@class,'sidenav')]"),
				timeout);
	}

	private void runStep(final String stepName, final StepAction action) {
		final StepResult stepResult = new StepResult();
		try {
			action.run();
			stepResult.pass = true;
			stepResult.details = "PASS";
		} catch (Throwable throwable) {
			stepResult.pass = false;
			stepResult.details = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getMessage();
			takeScreenshot("failed-" + sanitize(stepName));
		}
		report.put(stepName, stepResult);
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Full Test Report");
		lines.add("Evidence directory: " + evidenceDirectory.toAbsolutePath());
		lines.add("");
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			lines.add(entry.getKey() + ": " + (entry.getValue().pass ? "PASS" : "FAIL"));
			if (!entry.getValue().pass) {
				lines.add("  Reason: " + entry.getValue().details);
			}
		}
		Files.write(evidenceDirectory.resolve("final-report.txt"), lines, StandardCharsets.UTF_8);
	}

	private void clickAnyText(final List<String> texts, final Duration timeout) {
		Throwable lastError = null;
		for (String text : texts) {
			try {
				final WebElement element = new WebDriverWait(driver, timeout)
						.until(ExpectedConditions.elementToBeClickable(By.xpath(buildClickableTextXpath(text))));
				element.click();
				waitForUiLoad();
				return;
			} catch (Throwable throwable) {
				lastError = throwable;
			}
		}

		throw new AssertionError("Could not click any text variant: " + texts, lastError);
	}

	private boolean isAnyTextVisible(final List<String> texts, final Duration timeout) {
		for (String text : texts) {
			try {
				new WebDriverWait(driver, timeout)
						.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(buildVisibleTextXpath(text))));
				return true;
			} catch (TimeoutException ignored) {
			}
		}
		return false;
	}

	private void assertVisibleText(final String text, final Duration timeout) {
		assertTrue("Expected text not visible: " + text, isAnyTextVisible(Arrays.asList(text), timeout));
	}

	private boolean hasVisibleElement(final By by, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(by));
			return true;
		} catch (TimeoutException ex) {
			return false;
		}
	}

	private String takeScreenshot(final String filePrefix) {
		try {
			final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path destination = evidenceDirectory.resolve(sanitize(filePrefix) + ".png");
			Files.copy(screenshot.toPath(), destination);
			return destination.toString();
		} catch (Exception ex) {
			return "Screenshot failed: " + ex.getMessage();
		}
	}

	private void writeTextFile(final String fileName, final String content) {
		try {
			Files.writeString(evidenceDirectory.resolve(fileName), content == null ? "" : content, StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	private void waitForUiLoad() {
		try {
			wait.until(driver -> "complete".equals(
					((JavascriptExecutor) driver).executeScript("return document.readyState")));
		} catch (TimeoutException ignored) {
		}
		sleep(Duration.ofMillis(500));
	}

	private String buildVisibleTextXpath(final String text) {
		final String literal = toXpathLiteral(text);
		return "//*[contains(normalize-space(.), " + literal + ")]";
	}

	private String buildClickableTextXpath(final String text) {
		final String literal = toXpathLiteral(text);
		return "(" + "//button[contains(normalize-space(.), " + literal + ")]"
				+ " | //a[contains(normalize-space(.), " + literal + ")]"
				+ " | //*[@role='button' and contains(normalize-space(.), " + literal + ")]"
				+ " | //*[(self::div or self::span or self::li) and contains(normalize-space(.), " + literal + ")]"
				+ ")[1]";
	}

	private String toXpathLiteral(final String input) {
		if (!input.contains("'")) {
			return "'" + input + "'";
		}

		final String[] parts = input.split("'");
		final String joined = Arrays.stream(parts).map(part -> "'" + part + "'")
				.collect(Collectors.joining(",\"'\","));
		return "concat(" + joined + ")";
	}

	private String sanitize(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private void sleep(final Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String readValue(final String envKey, final String propertyKey) {
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		return null;
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepResult {
		private boolean pass;
		private String details;
	}

	private static class LegalNavigationEvidence {
		private String applicationHandle;
		private String legalHandle;
		private String applicationUrl;
		private String finalUrl;
		private Set<String> handlesBefore;
	}
}
