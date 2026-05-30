package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(35);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter EVIDENCE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String baseUrl;
	private String appWindowHandle;

	private final Map<String, String> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		baseUrl = getConfig("saleads.baseUrl", "SALEADS_BASE_URL");
		Assume.assumeTrue(
				"Set -Dsaleads.baseUrl=<login-url> or SALEADS_BASE_URL to run this live SaleADS workflow.",
				baseUrl != null && !baseUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(getConfigOrDefault("saleads.headless", "SALEADS_HEADLESS", "true"));
		evidenceDir = Path.of("target", "saleads-mi-negocio-evidence",
				LocalDateTime.now().format(EVIDENCE_FORMATTER));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--window-size=1600,1000");

		driver = new ChromeDriver(options);
		driver.manage().window().setSize(new Dimension(1600, 1000));
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		driver.get(baseUrl);
		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep("Login", () -> {
			loginWithGoogle();
			assertMainAppVisible();
			captureScreenshot("01-dashboard-loaded");
		});

		runStep("Mi Negocio menu", () -> {
			expandMiNegocioMenu();
			assertVisibleText("Agregar Negocio");
			assertVisibleText("Administrar Negocios");
			captureScreenshot("02-mi-negocio-menu-expanded");
		});

		runStep("Agregar Negocio modal", () -> {
			clickByVisibleText("Agregar Negocio");
			assertVisibleText("Crear Nuevo Negocio");
			assertVisibleText("Nombre del Negocio");
			assertVisibleText("Tienes 2 de 3 negocios");
			assertVisibleText("Cancelar");
			assertVisibleText("Crear Negocio");
			fillBusinessNameAndCancel();
			captureScreenshot("03-agregar-negocio-modal");
		});

		runStep("Administrar Negocios view", () -> {
			expandMiNegocioMenu();
			clickByVisibleText("Administrar Negocios");
			assertVisibleText("Informacion General", "Información General");
			assertVisibleText("Detalles de la Cuenta");
			assertVisibleText("Tus Negocios");
			assertVisibleText("Seccion Legal", "Sección Legal");
			captureScreenshot("04-administrar-negocios");
		});

		runStep("Información General", () -> {
			final WebElement section = findSectionByHeading("Informacion General", "Información General");
			final String sectionText = section.getText();
			assertTrue("User name is visible in Información General section.", hasPotentialUserName(sectionText));
			assertTrue("User email is visible in Información General section.", EMAIL_PATTERN.matcher(sectionText).find());
			assertTrue("BUSINESS PLAN is visible.", sectionText.contains("BUSINESS PLAN"));
			assertInContainer(section, "Cambiar Plan");
		});

		runStep("Detalles de la Cuenta", () -> {
			final WebElement section = findSectionByHeading("Detalles de la Cuenta");
			assertInContainer(section, "Cuenta creada");
			assertInContainer(section, "Estado activo");
			assertInContainer(section, "Idioma seleccionado");
		});

		runStep("Tus Negocios", () -> {
			final WebElement section = findSectionByHeading("Tus Negocios");
			assertInContainer(section, "Agregar Negocio");
			assertInContainer(section, "Tienes 2 de 3 negocios");
			assertTrue("Business list is visible in Tus Negocios section.", sectionHasBusinessEntries(section));
		});

		runStep("Términos y Condiciones", () -> {
			final String finalUrl = openLegalDocumentAndReturn("Terminos y Condiciones", "Términos y Condiciones",
					"08-terminos-y-condiciones");
			legalUrls.put("Términos y Condiciones", finalUrl);
		});

		runStep("Política de Privacidad", () -> {
			final String finalUrl = openLegalDocumentAndReturn("Politica de Privacidad", "Política de Privacidad",
					"09-politica-de-privacidad");
			legalUrls.put("Política de Privacidad", finalUrl);
		});

		writeFinalReport();
		assertTrue("One or more Mi Negocio workflow validations failed.", allStepsPassed());
	}

	private void loginWithGoogle() {
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickFirstMatchingVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
				"Continuar con Google", "Acceder con Google", "Login with Google"));
		waitForUiLoad();

		if (driver.getWindowHandles().size() > handlesBeforeClick.size()) {
			driver.switchTo().window(newestHandle(driver.getWindowHandles(), handlesBeforeClick));
			waitForUiLoad();
		}

		optionalClick(By.xpath("//*[normalize-space()=\"juanlucasbarbiergarzon@gmail.com\"]"), Duration.ofSeconds(8));
		waitForUiLoad();

		if (driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForUiLoad();
		}
	}

	private void assertMainAppVisible() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(
				By.xpath("//aside | //nav | //div[@role='navigation']")));
		assertVisibleText("Negocio");
	}

	private void expandMiNegocioMenu() {
		waitForUiLoad();
		assertVisibleText("Negocio");
		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiLoad();
		}
		if (!isTextVisible("Agregar Negocio") || !isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Negocio");
			clickByVisibleText("Mi Negocio");
			waitForUiLoad();
		}
	}

	private void fillBusinessNameAndCancel() {
		final By inputLocator = By.xpath(
				"//label[normalize-space()='Nombre del Negocio']/following::input[1] | //input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio']");
		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(inputLocator));
		input.clear();
		input.sendKeys("Negocio Prueba Automatizacion");
		waitForUiLoad();
		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space()='Crear Nuevo Negocio']")));
		waitForUiLoad();
	}

	private String openLegalDocumentAndReturn(final String linkTextWithoutAccent, final String headingText, final String screenshotName)
			throws IOException {
		final Set<String> beforeHandles = driver.getWindowHandles();
		final String originalHandle = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();

		clickByVisibleText(linkTextWithoutAccent, headingText);

		wait.until(driver -> driver.getWindowHandles().size() > beforeHandles.size()
				|| !driver.getCurrentUrl().equals(originalUrl));

		boolean openedInNewTab = false;
		if (driver.getWindowHandles().size() > beforeHandles.size()) {
			openedInNewTab = true;
			driver.switchTo().window(newestHandle(driver.getWindowHandles(), beforeHandles));
		}

		waitForUiLoad();
		assertVisibleText(linkTextWithoutAccent, headingText);
		assertLegalContentVisible();
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiLoad();
		driver.switchTo().window(appWindowHandle);
		return finalUrl;
	}

	private void assertLegalContentVisible() {
		final WebElement body = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
		assertTrue("Legal page body text should be visible.", body.getText() != null && body.getText().trim().length() > 120);
	}

	private WebElement findSectionByHeading(final String... headings) {
		for (final String heading : headings) {
			final String xpath = "//*[normalize-space()='" + heading
					+ "']/ancestor::*[self::section or self::article or self::div][1]";
			try {
				return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(xpath)));
			} catch (final TimeoutException ignored) {
				// Try next heading alias.
			}
		}
		throw new TimeoutException("Could not locate section with headings: " + Arrays.toString(headings));
	}

	private boolean sectionHasBusinessEntries(final WebElement section) {
		final List<By> candidateLocators = Arrays.asList(
				By.xpath(".//li"),
				By.xpath(".//tr"),
				By.xpath(".//*[contains(@class,'business') or contains(@class,'negocio')]"),
				By.xpath(".//article"));

		for (final By locator : candidateLocators) {
			if (!section.findElements(locator).isEmpty()) {
				return true;
			}
		}

		final String text = section.getText();
		return text != null && text.trim().split("\\R").length >= 4;
	}

	private void assertInContainer(final WebElement container, final String expectedText) {
		final String xpath = ".//*[normalize-space()='" + expectedText + "']";
		assertTrue("Expected text '" + expectedText + "' not found in section.",
				!container.findElements(By.xpath(xpath)).isEmpty());
	}

	private void clickByVisibleText(final String... variants) {
		clickFirstMatchingVisibleText(Arrays.asList(variants));
	}

	private void clickFirstMatchingVisibleText(final List<String> variants) {
		final List<By> allLocators = new ArrayList<>();
		for (final String text : variants) {
			allLocators.add(By.xpath(
					"//button[normalize-space()='" + text + "'] | //a[normalize-space()='" + text
							+ "'] | //*[@role='button' and normalize-space()='" + text + "'] | //*[(self::span or self::div) and normalize-space()='"
							+ text + "']"));
		}

		for (final By locator : allLocators) {
			try {
				final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
				element.click();
				waitForUiLoad();
				return;
			} catch (final TimeoutException ignored) {
				// Try next variant.
			}
		}

		throw new NoSuchElementException("None of these visible texts were clickable: " + variants);
	}

	private void optionalClick(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.elementToBeClickable(locator)).click();
		} catch (final TimeoutException ignored) {
			// Optional interaction.
		}
	}

	private void assertVisibleText(final String... variants) {
		for (final String text : variants) {
			final By locator = By.xpath("//*[normalize-space()='" + text + "']");
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return;
			} catch (final TimeoutException ignored) {
				// Try next variant.
			}
		}
		throw new TimeoutException("Could not find visible text for variants: " + Arrays.toString(variants));
	}

	private boolean isTextVisible(final String text) {
		try {
			return wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[normalize-space()='" + text + "']")))
					.isDisplayed();
		} catch (final TimeoutException ignored) {
			return false;
		}
	}

	private void waitForUiLoad() {
		wait.until(pageLoadIsComplete());
		try {
			Thread.sleep(450L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for UI stabilization.", e);
		}
	}

	private ExpectedCondition<Boolean> pageLoadIsComplete() {
		return driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState"));
	}

	private String newestHandle(final Set<String> currentHandles, final Set<String> previousHandles) {
		final Set<String> difference = new LinkedHashSet<>(currentHandles);
		difference.removeAll(previousHandles);
		if (difference.isEmpty()) {
			throw new NoSuchElementException("Could not determine the newly opened tab/window handle.");
		}
		return difference.iterator().next();
	}

	private Path captureScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(name + ".png");
		Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		return target;
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.execute();
			stepResults.put(stepName, "PASS");
		} catch (final Throwable error) {
			stepResults.put(stepName, "FAIL");
			stepErrors.put(stepName, error.getMessage() == null ? error.getClass().getName() : error.getMessage());
			try {
				captureScreenshot("fail-" + normalizeFileName(stepName));
			} catch (final IOException ignored) {
				// Best effort evidence capture.
			}
		}
	}

	private boolean allStepsPassed() {
		return stepResults.values().stream().allMatch("PASS"::equals);
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Test - Final Report").append(System.lineSeparator());
		builder.append("Base URL: ").append(baseUrl).append(System.lineSeparator());
		builder.append(System.lineSeparator());

		builder.append("Validation Results").append(System.lineSeparator());
		final List<String> orderedFields = Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad");

		for (final String field : orderedFields) {
			builder.append("- ").append(field).append(": ").append(stepResults.getOrDefault(field, "FAIL"));
			if (stepErrors.containsKey(field)) {
				builder.append(" (").append(stepErrors.get(field)).append(")");
			}
			builder.append(System.lineSeparator());
		}

		builder.append(System.lineSeparator()).append("Captured Legal URLs").append(System.lineSeparator());
		if (legalUrls.isEmpty()) {
			builder.append("- None captured").append(System.lineSeparator());
		} else {
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, builder.toString());
		System.out.println(builder);
		System.out.println("Evidence folder: " + evidenceDir.toAbsolutePath());
	}

	private String normalizeFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private boolean hasPotentialUserName(final String sectionText) {
		if (sectionText == null || sectionText.isBlank()) {
			return false;
		}

		final String[] blacklistedLines = { "informacion general", "información general", "business plan", "cambiar plan",
				"cuenta creada", "estado activo", "idioma seleccionado" };

		for (final String rawLine : sectionText.split("\\R")) {
			final String line = rawLine.trim();
			if (line.isBlank()) {
				continue;
			}

			final String lowered = line.toLowerCase(Locale.ROOT);
			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (lowered.length() < 4) {
				continue;
			}
			boolean blacklisted = false;
			for (final String token : blacklistedLines) {
				if (lowered.contains(token)) {
					blacklisted = true;
					break;
				}
			}
			if (blacklisted) {
				continue;
			}
			if (line.chars().filter(Character::isLetter).count() >= 4) {
				return true;
			}
		}
		return false;
	}

	private String getConfig(final String propertyName, final String envName) {
		final String prop = System.getProperty(propertyName);
		if (prop != null && !prop.isBlank()) {
			return prop;
		}
		final String env = System.getenv(envName);
		if (env != null && !env.isBlank()) {
			return env;
		}
		return null;
	}

	private String getConfigOrDefault(final String propertyName, final String envName, final String defaultValue) {
		final String resolved = getConfig(propertyName, envName);
		return resolved == null ? defaultValue : resolved;
	}

	@FunctionalInterface
	private interface StepAction {
		void execute() throws Exception;
	}
}
