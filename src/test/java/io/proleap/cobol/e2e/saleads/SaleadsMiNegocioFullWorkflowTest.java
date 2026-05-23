package io.proleap.cobol.e2e.saleads;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
	private static final Pattern PERSON_NAME_PATTERN = Pattern
			.compile("\\b[\\p{L}]{2,}(?:\\s+[\\p{L}]{2,}){1,3}\\b");

	private final Map<String, Boolean> sectionStatus = new LinkedHashMap<>();
	private final Map<String, String> sectionDetails = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;
	private String loginUrl;
	private String googleAccountEmail;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Enable this workflow with -Dsaleads.run.e2e=true or SALEADS_RUN_E2E=true.",
				readBoolean("saleads.run.e2e", "SALEADS_RUN_E2E", false));

		loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", null);
		googleAccountEmail = readConfig("saleads.google.email", "SALEADS_GOOGLE_EMAIL",
				"juanlucasbarbiergarzon@gmail.com");

		if (loginUrl == null || loginUrl.isBlank()) {
			throw new IllegalArgumentException(
					"Missing SaleADS login URL. Set -Dsaleads.login.url or SALEADS_LOGIN_URL.");
		}

		driver = new ChromeDriver(buildChromeOptions());
		wait = new WebDriverWait(driver, Duration.ofSeconds(readInt("saleads.timeout.seconds", "SALEADS_TIMEOUT_SECONDS", 30)));
		evidenceDirectory = createEvidenceDirectory();

		driver.manage().window().setSize(new Dimension(1600, 1000));
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
	public void saleadsMiNegocioFullTest() throws IOException {
		runSection("Login", this::validateLoginWithGoogle);
		runSection("Mi Negocio menu", this::validateMiNegocioMenu);
		runSection("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runSection("Administrar Negocios view", this::validateAdministrarNegociosView);
		runSection("Información General", this::validateInformacionGeneral);
		runSection("Detalles de la Cuenta", this::validateDetallesCuenta);
		runSection("Tus Negocios", this::validateTusNegocios);
		runSection("Términos y Condiciones",
				() -> validateLegalDocument("Términos y Condiciones", "Términos y Condiciones", "08-terminos"));
		runSection("Política de Privacidad",
				() -> validateLegalDocument("Política de Privacidad", "Política de Privacidad", "09-politica"));

		writeFinalReport();
		assertNoFailures();
	}

	private void validateLoginWithGoogle() throws IOException {
		clickFirstByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google", "Continuar con Google");
		selectGoogleAccountIfVisible(googleAccountEmail);
		waitForSidebar();
		waitForVisibleText("Negocio");
		captureScreenshot("01-dashboard-loaded");
	}

	private void validateMiNegocioMenu() throws IOException {
		expandMiNegocioMenu();
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickFirstByVisibleText("Agregar Negocio");
		waitForVisibleText("Crear Nuevo Negocio");
		findBusinessNameInput();
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		WebElement businessNameInput = findBusinessNameInput();
		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");

		clickFirstByVisibleText("Cancelar");
		waitForTextToDisappear("Crear Nuevo Negocio");
	}

	private void validateAdministrarNegociosView() throws IOException {
		expandMiNegocioMenu();
		clickFirstByVisibleText("Administrar Negocios");
		waitForVisibleText("Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal");
		captureScreenshot("04-administrar-negocios");
	}

	private void validateInformacionGeneral() {
		waitForVisibleText("Información General");
		waitForVisibleText("BUSINESS PLAN");
		waitForVisibleText("Cambiar Plan");

		String bodyText = driver.findElement(By.tagName("body")).getText();
		if (!EMAIL_PATTERN.matcher(bodyText).find()) {
			throw new AssertionError("User email was not visible in Información General.");
		}

		boolean userNameVisible = PERSON_NAME_PATTERN.matcher(bodyText).find() || bodyText.contains("Nombre");
		if (!userNameVisible) {
			throw new AssertionError("User name was not visibly detected in Información General.");
		}
	}

	private void validateDetallesCuenta() {
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void validateTusNegocios() {
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
	}

	private void validateLegalDocument(final String sectionName, final String linkText, final String screenshotName)
			throws IOException {
		String appHandle = driver.getWindowHandle();
		String appUrlBeforeClick = driver.getCurrentUrl();
		Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		clickFirstByVisibleText(linkText);
		boolean openedNewTab = switchToNewTabIfOpened(handlesBeforeClick);

		waitForVisibleText(sectionName);
		assertLegalContentVisible();
		captureScreenshot(screenshotName);
		legalUrls.put(sectionName, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToLoad();
			return;
		}

		driver.navigate().back();
		waitForUiToLoad();

		if (!driver.getCurrentUrl().equals(appUrlBeforeClick)) {
			driver.get(appUrlBeforeClick);
			waitForUiToLoad();
		}
	}

	private void runSection(final String sectionName, final StepAction action) {
		try {
			action.run();
			sectionStatus.put(sectionName, Boolean.TRUE);
			sectionDetails.put(sectionName, "PASS");
		} catch (final Throwable throwable) {
			sectionStatus.put(sectionName, Boolean.FALSE);
			sectionDetails.put(sectionName, "FAIL - " + throwable.getMessage());
			try {
				captureScreenshot("failure-" + sanitizeFileName(sectionName.toLowerCase(Locale.ROOT)));
			} catch (final IOException screenshotException) {
				sectionDetails.put(sectionName, sectionDetails.get(sectionName)
						+ " (additional screenshot capture failed: " + screenshotException.getMessage() + ")");
			}
		}
	}

	private void assertNoFailures() {
		List<String> failedSections = new ArrayList<>();
		for (String field : REPORT_FIELDS) {
			if (!Boolean.TRUE.equals(sectionStatus.get(field))) {
				failedSections.add(field + " -> " + sectionDetails.getOrDefault(field, "Not executed"));
			}
		}

		Assert.assertTrue("One or more SaleADS workflow sections failed:\n" + String.join("\n", failedSections),
				failedSections.isEmpty());
	}

	private void writeFinalReport() throws IOException {
		StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio full workflow report").append(System.lineSeparator());
		reportBuilder.append("Generated at: ").append(Instant.now()).append(System.lineSeparator());
		reportBuilder.append(System.lineSeparator());

		for (String field : REPORT_FIELDS) {
			boolean pass = Boolean.TRUE.equals(sectionStatus.get(field));
			reportBuilder.append(field).append(": ").append(pass ? "PASS" : "FAIL");
			String detail = sectionDetails.get(field);
			if (detail != null && !detail.isBlank()) {
				reportBuilder.append(" (").append(detail).append(")");
			}

			String legalUrl = legalUrls.get(field);
			if (legalUrl != null) {
				reportBuilder.append(" [URL: ").append(legalUrl).append("]");
			}
			reportBuilder.append(System.lineSeparator());
		}

		Path reportFile = evidenceDirectory.resolve("final-report.txt");
		Files.writeString(reportFile, reportBuilder.toString(), StandardCharsets.UTF_8);
		System.out.println(reportBuilder);
		System.out.println("Evidence stored in: " + evidenceDirectory.toAbsolutePath());
	}

	private void waitForSidebar() {
		wait.until(driverRef -> {
			List<WebElement> navigations = driverRef.findElements(By.xpath("//aside | //nav"));
			for (WebElement navigation : navigations) {
				if (navigation.isDisplayed()) {
					return Boolean.TRUE;
				}
			}
			return Boolean.FALSE;
		});
	}

	private void waitForVisibleText(final String text) {
		wait.until(driverRef -> !visibleElementsByText(text).isEmpty());
	}

	private void waitForTextToDisappear(final String text) {
		new WebDriverWait(driver, Duration.ofSeconds(10)).until(driverRef -> visibleElementsByText(text).isEmpty());
	}

	private void waitForUiToLoad() {
		wait.until(driverRef -> {
			Object state = ((JavascriptExecutor) driverRef).executeScript("return document.readyState");
			return "complete".equals(state);
		});

		try {
			Thread.sleep(450L);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void clickFirstByVisibleText(final String... labels) {
		Exception lastError = null;
		for (String label : labels) {
			try {
				clickByVisibleText(label);
				return;
			} catch (Exception exception) {
				lastError = exception;
			}
		}

		String attemptedLabels = String.join(", ", labels);
		throw new NoSuchElementException("Unable to click any element with visible text: " + attemptedLabels, lastError);
	}

	private void clickByVisibleText(final String text) {
		List<WebElement> displayedElements = visibleElementsByText(text);
		if (displayedElements.isEmpty()) {
			throw new NoSuchElementException("No visible element found with text: " + text);
		}

		WebElement target = displayedElements.get(0);
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", target);

		try {
			target.click();
		} catch (Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", target);
		}

		waitForUiToLoad();
	}

	private List<WebElement> visibleElementsByText(final String text) {
		String literal = toXpathLiteral(text);
		By locator = By.xpath("//*[contains(normalize-space(), " + literal + ")]");
		List<WebElement> matchingElements = driver.findElements(locator);
		List<WebElement> displayedElements = new ArrayList<>();
		for (WebElement element : matchingElements) {
			if (element.isDisplayed()) {
				displayedElements.add(element);
			}
		}
		return displayedElements;
	}

	private void expandMiNegocioMenu() {
		if (isVisibleTextPresent("Agregar Negocio", 2) && isVisibleTextPresent("Administrar Negocios", 2)) {
			return;
		}

		clickFirstByVisibleText("Mi Negocio");

		if (!isVisibleTextPresent("Agregar Negocio", 3)) {
			clickFirstByVisibleText("Negocio");
			clickFirstByVisibleText("Mi Negocio");
		}

		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
	}

	private boolean isVisibleTextPresent(final String text, final int timeoutSeconds) {
		WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		try {
			shortWait.until(driverRef -> !visibleElementsByText(text).isEmpty());
			return true;
		} catch (TimeoutException timeoutException) {
			return false;
		}
	}

	private WebElement findBusinessNameInput() {
		String labelLiteral = toXpathLiteral("Nombre del Negocio");
		By inputByLabel = By.xpath(
				"//label[contains(normalize-space(), " + labelLiteral + ")]/following::input[1] | //input[contains(@placeholder, "
						+ labelLiteral + ") or contains(@aria-label, " + labelLiteral + ")]");
		List<WebElement> candidates = driver.findElements(inputByLabel);
		for (WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				return candidate;
			}
		}

		throw new NoSuchElementException("Input field 'Nombre del Negocio' is not visible.");
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		try {
			String currentWindow = driver.getWindowHandle();
			String googleWindow = waitForGoogleWindow(Duration.ofSeconds(15));

			if (googleWindow == null) {
				driver.switchTo().window(currentWindow);
				return;
			}

			driver.switchTo().window(googleWindow);
			if (isVisibleTextPresent(accountEmail, 5)) {
				clickByVisibleText(accountEmail);
			}

			driver.switchTo().window(currentWindow);
		} catch (Exception ignored) {
			// Keep flow resilient when account chooser is skipped by Google SSO.
		}
	}

	private String waitForGoogleWindow(final Duration timeout) {
		Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			Set<String> handles = driver.getWindowHandles();
			for (String handle : handles) {
				driver.switchTo().window(handle);
				String currentUrl = driver.getCurrentUrl();
				if (currentUrl != null && currentUrl.contains("accounts.google")) {
					return handle;
				}
			}

			try {
				Thread.sleep(300L);
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	private boolean switchToNewTabIfOpened(final Set<String> handlesBeforeClick) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (Instant.now().isBefore(deadline)) {
			Set<String> handlesAfterClick = driver.getWindowHandles();
			if (handlesAfterClick.size() > handlesBeforeClick.size()) {
				for (String handle : handlesAfterClick) {
					if (!handlesBeforeClick.contains(handle)) {
						driver.switchTo().window(handle);
						waitForUiToLoad();
						return true;
					}
				}
			}

			try {
				Thread.sleep(250L);
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				return false;
			}
		}

		return false;
	}

	private void assertLegalContentVisible() {
		wait.until(driverRef -> {
			List<WebElement> paragraphs = driverRef.findElements(By.xpath("//p | //li | //article | //section"));
			for (WebElement paragraph : paragraphs) {
				String text = paragraph.getText();
				if (paragraph.isDisplayed() && text != null && text.trim().length() > 80) {
					return Boolean.TRUE;
				}
			}
			return Boolean.FALSE;
		});
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		String fileName = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC).format(Instant.now())
				+ "-" + sanitizeFileName(checkpointName) + ".png";
		Files.copy(source.toPath(), evidenceDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private Path createEvidenceDirectory() throws IOException {
		String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
		Path targetDirectory = Path.of("target", "saleads-mi-negocio-evidence", timestamp);
		Files.createDirectories(targetDirectory);
		return targetDirectory;
	}

	private ChromeOptions buildChromeOptions() {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1000");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (readBoolean("saleads.headless", "SALEADS_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}

		return options;
	}

	private static String readConfig(final String systemProperty, final String envVar, final String defaultValue) {
		String propertyValue = System.getProperty(systemProperty);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		String envValue = System.getenv(envVar);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		return defaultValue;
	}

	private static int readInt(final String systemProperty, final String envVar, final int defaultValue) {
		String rawValue = readConfig(systemProperty, envVar, Integer.toString(defaultValue));
		try {
			return Integer.parseInt(rawValue);
		} catch (NumberFormatException numberFormatException) {
			return defaultValue;
		}
	}

	private static boolean readBoolean(final String systemProperty, final String envVar, final boolean defaultValue) {
		String rawValue = readConfig(systemProperty, envVar, Boolean.toString(defaultValue));
		return Boolean.parseBoolean(rawValue);
	}

	private static String sanitizeFileName(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("-{2,}", "-")
				.replaceAll("^-|-$", "");
	}

	private static String toXpathLiteral(final String input) {
		if (!input.contains("'")) {
			return "'" + input + "'";
		}
		if (!input.contains("\"")) {
			return "\"" + input + "\"";
		}

		String[] parts = input.split("'");
		StringBuilder builder = new StringBuilder("concat(");
		for (int index = 0; index < parts.length; index++) {
			builder.append("'").append(parts[index]).append("'");
			if (index < parts.length - 1) {
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
