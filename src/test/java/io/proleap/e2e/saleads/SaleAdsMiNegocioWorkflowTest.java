package io.proleap.e2e.saleads;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Set RUN_SALEADS_E2E=true (or -Dsaleads.runE2E=true) to execute this workflow.", isRunEnabled());

		evidenceDir = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(TIMESTAMP_FORMAT));
		Files.createDirectories(evidenceDir);

		ChromeOptions options = new ChromeOptions();
		if (isHeadlessEnabled()) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		String debuggerAddress = readSetting("saleads.debuggerAddress", "SALEADS_DEBUGGER_ADDRESS", "");
		if (!debuggerAddress.isBlank()) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(readTimeoutSeconds()));

		String startUrl = readSetting("saleads.url", "SALEADS_URL", "");
		if (!startUrl.isBlank()) {
			driver.get(startUrl);
		}
		waitForUiLoad();
		if (startUrl.isBlank() && driver.getCurrentUrl().startsWith("about:")) {
			throw new IllegalStateException(
					"No SaleADS page is open. Provide SALEADS_URL (or -Dsaleads.url) or attach to an existing browser using SALEADS_DEBUGGER_ADDRESS.");
		}

		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null && !keepBrowserOpen()) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", () -> stepValidateLegalDocument("Términos y Condiciones", "Términos y Condiciones", "08-terminos"));
		executeStep("Política de Privacidad", () -> stepValidateLegalDocument("Política de Privacidad", "Política de Privacidad", "09-politica"));

		writeFinalReport();

		List<String> failed = new ArrayList<>();
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!Boolean.TRUE.equals(entry.getValue())) {
				failed.add(entry.getKey());
			}
		}

		Assert.assertTrue("Some workflow validations failed.\n" + renderReport(), failed.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		if (!isSidebarNavigationVisible()) {
			clickVisibleText(
					"Sign in with Google",
					"Iniciar sesión con Google",
					"Continuar con Google",
					"Login with Google");
			waitForUiLoad();
			clickVisibleTextIfPresent(Duration.ofSeconds(8), "juanlucasbarbiergarzon@gmail.com");
			waitForUiLoad();
		}

		wait.until(new ExpectedCondition<Boolean>() {
			@Override
			public Boolean apply(final WebDriver webDriver) {
				return isSidebarNavigationVisible();
			}
		});
		Assert.assertTrue("Main application interface (sidebar) is not visible after login.", isSidebarNavigationVisible());
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickVisibleTextIfPresent(Duration.ofSeconds(5), "Negocio");
		clickVisibleText("Mi Negocio");
		Assert.assertTrue("'Agregar Negocio' should be visible.", isTextVisible("Agregar Negocio"));
		Assert.assertTrue("'Administrar Negocios' should be visible.", isTextVisible("Administrar Negocios"));
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickVisibleText("Agregar Negocio");
		requireVisibleText("Crear Nuevo Negocio");
		Assert.assertTrue("'Nombre del Negocio' input should exist.", findNombreDelNegocioInput().isPresent());
		Assert.assertTrue("'Tienes 2 de 3 negocios' should be visible.", isTextVisible("Tienes 2 de 3 negocios"));
		Assert.assertTrue("'Cancelar' button should be present.", isTextVisible("Cancelar"));
		Assert.assertTrue("'Crear Negocio' button should be present.", isTextVisible("Crear Negocio"));
		takeScreenshot("03-agregar-negocio-modal");

		Optional<WebElement> input = findNombreDelNegocioInput();
		if (input.isPresent()) {
			input.get().click();
			input.get().clear();
			input.get().sendKeys("Negocio Prueba Automatización");
		}
		clickVisibleTextIfPresent(Duration.ofSeconds(5), "Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickVisibleText("Mi Negocio");
		}
		clickVisibleText("Administrar Negocios");
		requireVisibleText("Información General");
		requireVisibleText("Detalles de la Cuenta");
		requireVisibleText("Tus Negocios");
		requireVisibleText("Sección Legal");
		takeScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		WebElement section = requireSectionByHeading("Información General");
		List<String> lines = extractVisibleLines(section.getText());

		boolean hasEmail = lines.stream().anyMatch(this::looksLikeEmail);
		boolean hasUserName = lines.stream().anyMatch(this::looksLikeUserName);

		Assert.assertTrue("User email should be visible in 'Información General'.", hasEmail);
		Assert.assertTrue("User name should be visible in 'Información General'.", hasUserName);
		Assert.assertTrue("'BUSINESS PLAN' should be visible.", isTextVisible("BUSINESS PLAN"));
		Assert.assertTrue("'Cambiar Plan' button should be visible.", isTextVisible("Cambiar Plan"));
	}

	private void stepValidateDetallesCuenta() {
		requireSectionByHeading("Detalles de la Cuenta");
		requireVisibleText("Cuenta creada");
		requireVisibleText("Estado activo");
		requireVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		WebElement section = requireSectionByHeading("Tus Negocios");
		Assert.assertTrue("Business list should be visible.", hasBusinessList(section));
		Assert.assertTrue("'Agregar Negocio' should exist.", isTextVisible("Agregar Negocio"));
		Assert.assertTrue("'Tienes 2 de 3 negocios' should be visible.", isTextVisible("Tienes 2 de 3 negocios"));
	}

	private void stepValidateLegalDocument(final String linkText, final String headingText, final String screenshotPrefix) throws IOException {
		String currentHandle = driver.getWindowHandle();
		Set<String> handlesBefore = driver.getWindowHandles();

		clickVisibleText(linkText);
		waitForUiLoad();

		try {
			Thread.sleep(1200L);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
		Set<String> handlesAfterClick = driver.getWindowHandles();

		boolean openedInNewTab = handlesAfterClick.size() > handlesBefore.size();
		if (openedInNewTab) {
			String newTabHandle = handlesAfterClick.stream()
					.filter(handle -> !handlesBefore.contains(handle))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("A new tab was expected but no new handle was found."));
			driver.switchTo().window(newTabHandle);
		}

		waitForUiLoad();
		requireVisibleText(headingText);

		String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Legal content text should be visible for " + headingText + ".", bodyText != null && bodyText.trim().length() > 120);

		String finalUrl = driver.getCurrentUrl();
		legalUrls.put(headingText, finalUrl);
		takeScreenshot(screenshotPrefix);

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
		} else {
			driver.navigate().back();
			waitForUiLoad();
			if (!driver.getWindowHandle().equals(currentHandle) && driver.getWindowHandles().contains(currentHandle)) {
				driver.switchTo().window(currentHandle);
			}
		}
		waitForUiLoad();
	}

	private void executeStep(final String reportField, final StepAction action) {
		try {
			action.run();
			report.put(reportField, Boolean.TRUE);
		} catch (Throwable throwable) {
			report.put(reportField, Boolean.FALSE);
			System.err.println("Step failed: " + reportField);
			throwable.printStackTrace(System.err);
		}
	}

	private WebElement requireVisibleText(final String... options) {
		return wait.until(new ExpectedCondition<WebElement>() {
			@Override
			public WebElement apply(final WebDriver webDriver) {
				for (String option : options) {
					Optional<WebElement> found = findVisibleElementByText(option);
					if (found.isPresent()) {
						return found.get();
					}
				}
				return null;
			}
		});
	}

	private void clickVisibleText(final String... options) {
		WebElement element = requireVisibleText(options);
		scrollIntoView(element);
		wait.until(d -> element.isDisplayed() && element.isEnabled());
		try {
			element.click();
		} catch (RuntimeException runtimeException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiLoad();
	}

	private boolean clickVisibleTextIfPresent(final Duration timeout, final String... options) {
		WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		try {
			WebElement element = shortWait.until(new ExpectedCondition<WebElement>() {
				@Override
				public WebElement apply(final WebDriver webDriver) {
					for (String option : options) {
						Optional<WebElement> found = findVisibleElementByText(option);
						if (found.isPresent()) {
							return found.get();
						}
					}
					return null;
				}
			});
			scrollIntoView(element);
			element.click();
			waitForUiLoad();
			return true;
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private Optional<WebElement> findVisibleElementByText(final String text) {
		String literal = xpathLiteral(text);
		By by = By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]");
		return driver.findElements(by).stream().filter(this::isVisible).findFirst();
	}

	private Optional<WebElement> findNombreDelNegocioInput() {
		List<By> candidates = Arrays.asList(
				By.xpath("//input[@name='nombre' or @name='businessName' or @id='nombreNegocio']"),
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"));

		for (By candidate : candidates) {
			Optional<WebElement> element = driver.findElements(candidate).stream().filter(this::isVisible).findFirst();
			if (element.isPresent()) {
				return element;
			}
		}
		return Optional.empty();
	}

	private WebElement requireSectionByHeading(final String heading) {
		WebElement header = requireVisibleText(heading);
		return header.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
	}

	private boolean hasBusinessList(final WebElement section) {
		boolean hasCommonListPattern = !section.findElements(By.xpath(".//li[normalize-space()] | .//tr[.//td] | .//*[contains(@class,'business') and normalize-space()]")).isEmpty();
		if (hasCommonListPattern) {
			return true;
		}
		List<String> lines = extractVisibleLines(section.getText());
		Set<String> knownLabels = new HashSet<>(Arrays.asList("Tus Negocios", "Agregar Negocio", "Tienes 2 de 3 negocios"));
		long possibleBusinessRows = lines.stream()
				.filter(line -> !knownLabels.contains(line))
				.filter(line -> line.length() >= 3)
				.count();
		return possibleBusinessRows >= 1;
	}

	private boolean looksLikeUserName(final String text) {
		if (text == null) {
			return false;
		}
		String trimmed = text.trim();
		if (trimmed.length() < 3 || looksLikeEmail(trimmed)) {
			return false;
		}
		Set<String> excluded = new HashSet<>(Arrays.asList(
				"Información General",
				"BUSINESS PLAN",
				"Cambiar Plan",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Sección Legal"));
		if (excluded.contains(trimmed)) {
			return false;
		}
		return trimmed.chars().anyMatch(Character::isLetter);
	}

	private boolean looksLikeEmail(final String text) {
		return text != null && EMAIL_PATTERN.matcher(text.trim()).matches();
	}

	private List<String> extractVisibleLines(final String textBlock) {
		List<String> lines = new ArrayList<>();
		if (textBlock == null || textBlock.isBlank()) {
			return lines;
		}
		for (String line : textBlock.split("\\R")) {
			String trimmed = line.trim();
			if (!trimmed.isEmpty()) {
				lines.add(trimmed);
			}
		}
		return lines;
	}

	private boolean isSidebarNavigationVisible() {
		List<By> sidebarLocators = Arrays.asList(
				By.xpath("//aside"),
				By.xpath("//*[@role='navigation']"),
				By.xpath("//*[contains(@class,'sidebar')]"));

		for (By locator : sidebarLocators) {
			Optional<WebElement> visible = driver.findElements(locator).stream().filter(this::isVisible).findFirst();
			if (visible.isPresent()) {
				return true;
			}
		}
		return isTextVisible("Mi Negocio") || isTextVisible("Negocio");
	}

	private boolean isTextVisible(final String text) {
		return findVisibleElementByText(text).isPresent();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private boolean isVisible(final WebElement element) {
		try {
			return element.isDisplayed();
		} catch (RuntimeException runtimeException) {
			return false;
		}
	}

	private void waitForUiLoad() {
		wait.until(new ExpectedCondition<Boolean>() {
			@Override
			public Boolean apply(final WebDriver webDriver) {
				Object state = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
				return "complete".equals(state);
			}
		});
		try {
			Thread.sleep(600L);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String name) throws IOException {
		File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Path destination = evidenceDir.resolve(sanitizeFilename(name) + ".png");
		Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Workflow Final Report");
		lines.add("=======================================");
		lines.add("");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			lines.add(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		lines.add("");
		lines.add("Captured legal URLs:");
		if (legalUrls.isEmpty()) {
			lines.add("- N/A");
		} else {
			for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
				lines.add("- " + entry.getKey() + ": " + entry.getValue());
			}
		}

		Path reportFile = evidenceDir.resolve("10-final-report.txt");
		Files.write(reportFile, lines, StandardCharsets.UTF_8);
	}

	private String renderReport() {
		StringBuilder builder = new StringBuilder();
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ")
					.append(entry.getValue() ? "PASS" : "FAIL")
					.append('\n');
		}
		for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
			builder.append("  URL ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}
		return builder.toString();
	}

	private String sanitizeFilename(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
	}

	private int readTimeoutSeconds() {
		return Integer.parseInt(readSetting("saleads.timeoutSeconds", "SALEADS_TIMEOUT_SECONDS", "30"));
	}

	private boolean isRunEnabled() {
		return Boolean.parseBoolean(readSetting("saleads.runE2E", "RUN_SALEADS_E2E", "false"));
	}

	private boolean keepBrowserOpen() {
		return Boolean.parseBoolean(readSetting("saleads.keepBrowserOpen", "SALEADS_KEEP_BROWSER_OPEN", "false"));
	}

	private boolean isHeadlessEnabled() {
		return Boolean.parseBoolean(readSetting("saleads.headless", "SALEADS_HEADLESS", "false"));
	}

	private String readSetting(final String systemPropertyKey, final String envKey, final String defaultValue) {
		String value = System.getProperty(systemPropertyKey);
		if (value != null && !value.isBlank()) {
			return value;
		}
		String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return defaultValue;
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		String[] parts = text.split("'");
		StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			builder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				builder.append(",\"'\",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
