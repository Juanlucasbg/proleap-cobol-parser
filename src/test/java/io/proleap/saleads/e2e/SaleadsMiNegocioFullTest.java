package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> details = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws Exception {
		evidenceDir = Paths.get("target", "saleads-evidence", TEST_NAME, LocalDateTime.now().format(TIMESTAMP));
		Files.createDirectories(evidenceDir);

		driver = createDriver();
		wait = new WebDriverWait(driver, Duration.ofSeconds(readIntEnv("SALEADS_WAIT_SECONDS", 30)));
		driver.manage().window().setSize(new Dimension(1600, 1200));

		final String loginUrl = env("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
		}

		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() throws Exception {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", () -> stepValidateLegalLink("T\u00e9rminos y Condiciones",
				"T\u00e9rminos y Condiciones", "legal_terminos.png", "terminos_url"));
		runStep("Política de Privacidad", () -> stepValidateLegalLink("Pol\u00edtica de Privacidad",
				"Pol\u00edtica de Privacidad", "legal_politica_privacidad.png", "politica_url"));

		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		assertTrue("Failed validations: " + failedSteps + ". Details: " + details, failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		waitForUiLoad();
		if (!isSidebarVisible()) {
			clickByAnyVisibleText("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google");
			waitForUiLoad();
			selectGoogleAccountIfPrompted();
			waitForUiLoad();
		}

		assertTrue("Left sidebar navigation is not visible after login", isSidebarVisible());
		assertVisibleByAnyText("Negocio", "Mi Negocio");
		captureScreenshot("01_dashboard_loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		assertTrue("Left sidebar is not visible", isSidebarVisible());
		clickByAnyVisibleText("Mi Negocio");
		waitForUiLoad();
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02_mi_negocio_expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByAnyVisibleText("Agregar Negocio");
		waitForUiLoad();

		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleByAnyText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		captureScreenshot("03_agregar_negocio_modal.png");

		final WebElement input = findFirstVisible(Arrays.asList(
				By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]")));
		input.click();
		input.clear();
		input.sendKeys("Negocio Prueba Automatizacion");
		clickByAnyVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioExpanded();
		clickByAnyVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertVisibleText("Informaci\u00f3n General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleByAnyText("Secci\u00f3n Legal", "Legal");
		captureScreenshot("04_administrar_negocios.png");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("Informaci\u00f3n General");
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		final String sectionText = sectionText("Informaci\u00f3n General");
		assertTrue("User email is not visible in Informacion General section", EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("User name is not visible in Informacion General section", containsLikelyName(sectionText));
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		final String sectionText = sectionText("Tus Negocios");
		assertTrue("Business list does not look visible in Tus Negocios section", sectionText.length() > 40);
	}

	private void stepValidateLegalLink(final String linkText, final String headingText, final String screenshotName,
			final String urlDetailKey) throws Exception {
		waitForUiLoad();
		final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
		clickByAnyVisibleText(linkText);
		waitForUiLoad();

		final String targetHandle = wait.until(newWindowOrSameWindow(beforeHandles));
		driver.switchTo().window(targetHandle);
		waitForUiLoad();

		assertVisibleText(headingText);
		final String bodyText = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
		assertTrue("Legal content text is not visible for " + linkText, bodyText != null && bodyText.trim().length() > 100);
		captureScreenshot(screenshotName);
		details.put(urlDetailKey, driver.getCurrentUrl());

		cleanupLegalNavigation(targetHandle);
	}

	private void cleanupLegalNavigation(final String targetHandle) {
		if (!Objects.equals(targetHandle, appWindowHandle)) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiLoad();
	}

	private void runStep(final String stepName, final StepAction stepAction) throws Exception {
		try {
			stepAction.run();
			report.put(stepName, Boolean.TRUE);
			details.put(stepName, "PASS");
		} catch (final Exception | AssertionError ex) {
			report.put(stepName, Boolean.FALSE);
			details.put(stepName, "FAIL - " + ex.getMessage());
			captureScreenshot("failure_" + sanitize(stepName) + ".png");
		}
	}

	private ExpectedCondition<String> newWindowOrSameWindow(final Set<String> beforeHandles) {
		return webDriver -> {
			final Set<String> currentHandles = webDriver.getWindowHandles();
			if (currentHandles.size() > beforeHandles.size()) {
				for (final String handle : currentHandles) {
					if (!beforeHandles.contains(handle)) {
						return handle;
					}
				}
			}
			return webDriver.getWindowHandle();
		};
	}

	private void ensureMiNegocioExpanded() {
		if (!isTextVisible("Administrar Negocios")) {
			clickByAnyVisibleText("Mi Negocio");
			waitForUiLoad();
		}
	}

	private void selectGoogleAccountIfPrompted() {
		try {
			final String currentHandle = driver.getWindowHandle();
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (isTextVisible(ACCOUNT_EMAIL)) {
					clickByAnyVisibleText(ACCOUNT_EMAIL);
					waitForUiLoad();
					break;
				}
			}
			driver.switchTo().window(currentHandle);
		} catch (final Exception ignored) {
			// Ignore if account chooser is skipped or already authenticated.
		}
	}

	private boolean containsLikelyName(final String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		final String[] lines = text.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.length() < 3) {
				continue;
			}
			final String lower = line.toLowerCase();
			if (lower.contains("informaci") || lower.contains("plan") || lower.contains("cambiar")
					|| lower.contains("cuenta") || lower.contains("estado") || lower.contains("idioma")
					|| EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (line.chars().anyMatch(Character::isLetter)) {
				return true;
			}
		}
		return false;
	}

	private String sectionText(final String headingText) {
		final List<By> selectors = Arrays.asList(
				By.xpath("//*[normalize-space(.)='" + headingText + "']/ancestor::*[self::section or self::article][1]"),
				By.xpath("//*[normalize-space(.)='" + headingText + "']/ancestor::div[1]"));
		for (final By selector : selectors) {
			try {
				final WebElement section = wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
				final String text = section.getText();
				if (text != null && !text.isBlank()) {
					return text;
				}
			} catch (final TimeoutException ignored) {
				// Try next selector.
			}
		}
		return "";
	}

	private boolean isSidebarVisible() {
		final List<By> selectors = Arrays.asList(By.xpath("//aside"), By.xpath("//nav"), By.xpath("//*[contains(@class,'sidebar')]"));
		for (final By selector : selectors) {
			final List<WebElement> elements = driver.findElements(selector);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void assertVisibleByAnyText(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return;
			}
		}
		fail("None of the expected texts are visible: " + Arrays.toString(texts));
	}

	private void assertVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
	}

	private boolean isTextVisible(final String text) {
		try {
			return !driver.findElements(byVisibleText(text)).isEmpty();
		} catch (final Exception ex) {
			return false;
		}
	}

	private By byVisibleText(final String text) {
		return By.xpath("//*[normalize-space(.)='" + text + "' or contains(normalize-space(.), '" + text + "')]");
	}

	private void clickByAnyVisibleText(final String... texts) {
		final List<By> selectors = new ArrayList<>();
		for (final String text : texts) {
			selectors.add(By.xpath("//button[normalize-space(.)='" + text + "' or contains(normalize-space(.), '" + text + "')]"));
			selectors.add(By.xpath("//a[normalize-space(.)='" + text + "' or contains(normalize-space(.), '" + text + "')]"));
			selectors.add(By.xpath("//*[@role='button'][normalize-space(.)='" + text + "' or contains(normalize-space(.), '" + text + "')]"));
			selectors.add(By.xpath("//*[normalize-space(.)='" + text + "']"));
		}

		final WebElement target = findFirstClickable(selectors);
		target.click();
		waitForUiLoad();
	}

	private WebElement findFirstVisible(final List<By> selectors) {
		for (final By selector : selectors) {
			final List<WebElement> elements = driver.findElements(selector);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		throw new IllegalStateException("No visible element found for selectors: " + selectors);
	}

	private WebElement findFirstClickable(final List<By> selectors) {
		for (final By selector : selectors) {
			try {
				return wait.until(ExpectedConditions.elementToBeClickable(selector));
			} catch (final TimeoutException ignored) {
				// Try next selector.
			}
		}
		throw new IllegalStateException("No clickable element found for selectors: " + selectors);
	}

	private void waitForUiLoad() {
		try {
			wait.until(driverReadyStateComplete());
		} catch (final Exception ignored) {
			// Continue for pages where readyState is not available.
		}
		try {
			Thread.sleep(700L);
		} catch (final InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private ExpectedCondition<Boolean> driverReadyStateComplete() {
		return webDriver -> {
			if (!(webDriver instanceof JavascriptExecutor)) {
				return Boolean.TRUE;
			}
			final Object state = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
			return "complete".equals(state);
		};
	}

	private void captureScreenshot(final String fileName) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final Path target = evidenceDir.resolve(fileName);
		try {
			final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(target, screenshot);
		} catch (final IOException ignored) {
			// Keep test running even if screenshot write fails.
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder reportText = new StringBuilder();
		reportText.append("test: ").append(TEST_NAME).append('\n');
		reportText.append("generated_at: ").append(LocalDateTime.now()).append('\n');
		reportText.append('\n');
		for (final String key : List.of("Login", "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
				"Información General", "Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones",
				"Política de Privacidad")) {
			final Boolean passed = report.get(key);
			reportText.append(key).append(": ").append(Boolean.TRUE.equals(passed) ? "PASS" : "FAIL").append('\n');
		}
		if (!details.isEmpty()) {
			reportText.append('\n').append("details:").append('\n');
			for (final Map.Entry<String, String> detailEntry : details.entrySet()) {
				reportText.append("- ").append(detailEntry.getKey()).append(": ").append(detailEntry.getValue()).append('\n');
			}
		}
		Files.writeString(evidenceDir.resolve("final_report.txt"), reportText.toString(), StandardCharsets.UTF_8);
	}

	private WebDriver createDriver() throws Exception {
		final String remoteUrl = env("SELENIUM_REMOTE_URL");
		if (remoteUrl != null && !remoteUrl.isBlank()) {
			return new RemoteWebDriver(new java.net.URL(remoteUrl), buildChromeOptions());
		}

		WebDriverManager.chromedriver().setup();
		return new ChromeDriver(buildChromeOptions());
	}

	private ChromeOptions buildChromeOptions() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--window-size=1600,1200");
		if (!"false".equalsIgnoreCase(envOrDefault("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		return options;
	}

	private int readIntEnv(final String key, final int defaultValue) {
		final String value = env(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return Integer.parseInt(value);
	}

	private String env(final String key) {
		return System.getenv(key);
	}

	private String envOrDefault(final String key, final String defaultValue) {
		final String value = env(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private String sanitize(final String text) {
		return text.toLowerCase().replaceAll("[^a-z0-9]+", "_");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
