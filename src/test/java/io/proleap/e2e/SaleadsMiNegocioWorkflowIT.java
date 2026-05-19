package io.proleap.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowIT {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");
	private static final Duration LEGAL_WAIT_TIMEOUT = Duration.ofSeconds(20);

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final Map<String, String> reportDetails = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;
	private String termsAndConditionsUrl = "N/A";
	private String privacyPolicyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Enable this test with SALEADS_E2E_ENABLED=true.",
				Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_E2E_ENABLED", "false")));

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"SALEADS_LOGIN_URL must point to the current environment login page. No domain is hardcoded in test code.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		final int timeoutSeconds = Integer.parseInt(System.getenv().getOrDefault("SALEADS_TIMEOUT_SECONDS", "30"));

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		evidenceDir = Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		driver.get(loginUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStep("Login", () -> {
			clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
			waitForUiToLoad();
			maybeSelectGoogleAccount("juanlucasbarbiergarzon@gmail.com");

			waitForVisibleText("Negocio");
			assertVisible(By.xpath("//aside | //nav"), "Left sidebar navigation is not visible.");
			takeScreenshot("01-dashboard-loaded.png");
		});

		runStep("Mi Negocio menu", () -> {
			clickByVisibleText("Negocio");
			waitForUiToLoad();
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();

			waitForVisibleText("Agregar Negocio");
			waitForVisibleText("Administrar Negocios");
			takeScreenshot("02-mi-negocio-expanded-menu.png");
		});

		runStep("Agregar Negocio modal", () -> {
			clickByVisibleText("Agregar Negocio");
			waitForUiToLoad();

			waitForVisibleText("Crear Nuevo Negocio");
			assertTrue("Input field 'Nombre del Negocio' is missing.", isBusinessNameFieldVisible());
			waitForVisibleText("Tienes 2 de 3 negocios");
			waitForVisibleText("Cancelar");
			waitForVisibleText("Crear Negocio");
			takeScreenshot("03-agregar-negocio-modal.png");

			final Optional<WebElement> businessNameField = findVisibleElement(
					By.xpath("//input[contains(@placeholder,'Nombre del Negocio')] | //input[@aria-label='Nombre del Negocio']"
							+ " | //label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"));
			if (businessNameField.isPresent()) {
				businessNameField.get().click();
				businessNameField.get().clear();
				businessNameField.get().sendKeys("Negocio Prueba Automatización");
			}

			clickByVisibleText("Cancelar");
			waitForUiToLoad();
		});

		runStep("Administrar Negocios view", () -> {
			if (!hasVisibleText("Administrar Negocios")) {
				clickByVisibleText("Mi Negocio");
				waitForUiToLoad();
			}
			clickByVisibleText("Administrar Negocios");
			waitForUiToLoad();

			waitForVisibleText("Información General");
			waitForVisibleText("Detalles de la Cuenta");
			waitForVisibleText("Tus Negocios");
			waitForVisibleText("Sección Legal");
			takeScreenshot("04-administrar-negocios-full-page.png");
		});

		runStep("Información General", () -> {
			waitForVisibleText("Información General");
			waitForVisibleText("BUSINESS PLAN");
			waitForVisibleText("Cambiar Plan");

			assertTrue("A user email is not visible.", hasVisibleEmail());
			assertTrue("A user name is not visible.", hasLikelyUserNameInGeneralInfo());
		});

		runStep("Detalles de la Cuenta", () -> {
			waitForVisibleText("Cuenta creada");
			waitForVisibleText("Estado activo");
			waitForVisibleText("Idioma seleccionado");
		});

		runStep("Tus Negocios", () -> {
			waitForVisibleText("Tus Negocios");
			waitForVisibleText("Agregar Negocio");
			waitForVisibleText("Tienes 2 de 3 negocios");
			assertTrue("Business list appears to be empty.", hasBusinessListContent());
		});

		runStep("Términos y Condiciones", () -> {
			termsAndConditionsUrl = openLegalPageAndReturn("Términos y Condiciones", "Términos y Condiciones",
					"08-terminos-y-condiciones.png");
		});

		runStep("Política de Privacidad", () -> {
			privacyPolicyUrl = openLegalPageAndReturn("Política de Privacidad", "Política de Privacidad",
					"09-politica-de-privacidad.png");
		});

		final String report = buildFinalReport();
		Files.writeString(evidenceDir.resolve("10-final-report.txt"), report + System.lineSeparator(),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		System.out.println(report);

		assertTrue("One or more SaleADS workflow validations failed. Check report: "
				+ evidenceDir.resolve("10-final-report.txt").toAbsolutePath(),
				finalReport.values().stream().allMatch(Boolean::booleanValue));
	}

	private void runStep(final String stepName, final StepRunnable action) {
		try {
			action.run();
			finalReport.put(stepName, true);
			reportDetails.put(stepName, "PASS");
		} catch (final Exception ex) {
			finalReport.put(stepName, false);
			reportDetails.put(stepName, "FAIL - " + ex.getMessage());
			try {
				takeScreenshot("error-" + sanitize(stepName) + ".png");
			} catch (final Exception ignored) {
				// If screenshot capture fails we still keep the original failure details.
			}
		}
	}

	private void clickByVisibleText(final String... texts) {
		final List<By> locators = new ArrayList<>();
		for (final String text : texts) {
			final String literal = xpathLiteral(text);
			locators.add(By.xpath(
					"//*[self::button or self::a or @role='button' or self::span or self::div]"
							+ "[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"));
			locators.add(By.xpath("//*[self::button or self::a or @role='button']//*[normalize-space()=" + literal
					+ " or contains(normalize-space(), " + literal + ")]/ancestor::*[self::button or self::a][1]"));
		}

		for (final By locator : locators) {
			final Optional<WebElement> candidate = findVisibleElement(locator);
			if (candidate.isPresent()) {
				wait.until(ExpectedConditions.elementToBeClickable(candidate.get())).click();
				waitForUiToLoad();
				return;
			}
		}

		throw new IllegalStateException("Could not find a clickable element by visible text: " + String.join(", ", texts));
	}

	private void maybeSelectGoogleAccount(final String email) throws InterruptedException {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(35).toMillis();
		final String emailLiteral = xpathLiteral(email);

		while (System.currentTimeMillis() < deadline) {
			final Set<String> handles = driver.getWindowHandles();
			for (final String handle : handles) {
				driver.switchTo().window(handle);

				final List<WebElement> accountMatches = driver.findElements(
						By.xpath("//*[contains(normalize-space(), " + emailLiteral + ")]"));
				if (!accountMatches.isEmpty() && accountMatches.get(0).isDisplayed()) {
					accountMatches.get(0).click();
					waitForUiToLoad();
				}
			}

			if (hasVisibleText("Negocio") && !driver.getCurrentUrl().contains("accounts.google.com")) {
				switchBackToAppWindow();
				return;
			}

			Thread.sleep(500);
		}

		switchBackToAppWindow();
	}

	private void switchBackToAppWindow() {
		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private String openLegalPageAndReturn(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		final Set<String> beforeHandles = driver.getWindowHandles();
		final int beforeSize = beforeHandles.size();
		final String currentHandle = driver.getWindowHandle();
		final String currentUrl = driver.getCurrentUrl();

		clickByVisibleText(linkText);

		final WebDriverWait legalWait = new WebDriverWait(driver, LEGAL_WAIT_TIMEOUT);
		legalWait.until(webDriver -> webDriver.getWindowHandles().size() > beforeSize
				|| !Objects.equals(webDriver.getCurrentUrl(), currentUrl));

		String legalHandle = currentHandle;
		final Set<String> afterHandles = driver.getWindowHandles();
		if (afterHandles.size() > beforeSize) {
			for (final String handle : afterHandles) {
				if (!beforeHandles.contains(handle)) {
					legalHandle = handle;
					break;
				}
			}
		}

		driver.switchTo().window(legalHandle);
		waitForUiToLoad();

		waitForVisibleText(headingText);
		assertTrue("Legal content text is not visible.", hasLegalContent());
		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (!Objects.equals(legalHandle, currentHandle)) {
			driver.close();
			driver.switchTo().window(currentHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();
		switchBackToAppWindow();

		return finalUrl;
	}

	private boolean hasLegalContent() {
		final Optional<WebElement> legalContainer = findVisibleElement(
				By.xpath("//main | //article | //section | //body"));
		return legalContainer.map(webElement -> webElement.getText().trim().length() > 100).orElse(false);
	}

	private boolean isBusinessNameFieldVisible() {
		return findVisibleElement(By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"
				+ " | //input[@aria-label='Nombre del Negocio']"
				+ " | //label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]")).isPresent();
	}

	private boolean hasVisibleEmail() {
		final List<WebElement> textNodes = driver.findElements(By.xpath("//*[self::span or self::p or self::div or self::li]"));
		for (final WebElement node : textNodes) {
			if (!node.isDisplayed()) {
				continue;
			}

			final String text = node.getText();
			if (text != null && EMAIL_PATTERN.matcher(text).find()) {
				return true;
			}
		}
		return false;
	}

	private boolean hasLikelyUserNameInGeneralInfo() {
		final Optional<WebElement> section = findVisibleElement(
				By.xpath("//*[contains(normalize-space(),'Información General')]/ancestor::*[self::section or self::div][1]"));
		if (section.isEmpty()) {
			return false;
		}

		final String sectionText = section.get().getText();
		if (sectionText == null || sectionText.isBlank()) {
			return false;
		}

		final String[] lines = sectionText.split("\\R");
		for (final String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.length() > 2 && !trimmed.contains("@") && !trimmed.equalsIgnoreCase("Información General")
					&& !trimmed.equalsIgnoreCase("BUSINESS PLAN") && !trimmed.equalsIgnoreCase("Cambiar Plan")) {
				return true;
			}
		}

		return false;
	}

	private boolean hasBusinessListContent() {
		final Optional<WebElement> section = findVisibleElement(
				By.xpath("//*[contains(normalize-space(),'Tus Negocios')]/ancestor::*[self::section or self::div][1]"));
		return section.map(element -> element.getText().trim().length() > 25).orElse(false);
	}

	private void waitForVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]")));
	}

	private boolean hasVisibleText(final String text) {
		return findVisibleElement(
				By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]")).isPresent();
	}

	private Optional<WebElement> findVisibleElement(final By locator) {
		final List<WebElement> elements = driver.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return Optional.of(element);
			}
		}
		return Optional.empty();
	}

	private void assertVisible(final By locator, final String errorMessage) {
		assertTrue(errorMessage, findVisibleElement(locator).isPresent());
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete"
				.equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final Path outputFile = evidenceDir.resolve(fileName);
		final Path screenshotPath = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(screenshotPath, outputFile, StandardCopyOption.REPLACE_EXISTING);
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		builder.append(System.lineSeparator());

		appendReportLine(builder, "Login");
		appendReportLine(builder, "Mi Negocio menu");
		appendReportLine(builder, "Agregar Negocio modal");
		appendReportLine(builder, "Administrar Negocios view");
		appendReportLine(builder, "Información General");
		appendReportLine(builder, "Detalles de la Cuenta");
		appendReportLine(builder, "Tus Negocios");
		appendReportLine(builder, "Términos y Condiciones");
		appendReportLine(builder, "Política de Privacidad");

		builder.append(System.lineSeparator());
		builder.append("Final URL - Términos y Condiciones: ").append(termsAndConditionsUrl).append(System.lineSeparator());
		builder.append("Final URL - Política de Privacidad: ").append(privacyPolicyUrl).append(System.lineSeparator());

		return builder.toString();
	}

	private void appendReportLine(final StringBuilder builder, final String field) {
		final String detail = reportDetails.getOrDefault(field, "NOT EXECUTED");
		builder.append(field).append(": ").append(detail).append(System.lineSeparator());
	}

	private String sanitize(final String value) {
		return value.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9\\-]", "");
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder value = new StringBuilder("concat(");
		final char[] characters = text.toCharArray();
		for (int i = 0; i < characters.length; i++) {
			final String part = String.valueOf(characters[i]);
			if ("'".equals(part)) {
				value.append("\"").append(part).append("\"");
			} else {
				value.append("'").append(part).append("'");
			}
			if (i < characters.length - 1) {
				value.append(",");
			}
		}
		value.append(")");
		return value.toString();
	}

	@FunctionalInterface
	private interface StepRunnable {
		void run() throws Exception;
	}
}
