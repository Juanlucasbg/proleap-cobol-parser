package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * <p>This test never hardcodes a SaleADS domain. Provide the login page dynamically with:
 *
 * <ul>
 *   <li>JVM property: -Dsaleads.e2e.loginUrl=https://your-env/login</li>
 *   <li>or environment variable: SALEADS_LOGIN_URL=https://your-env/login</li>
 * </ul>
 */
public class SaleAdsMiNegocioFullWorkflowTest {

	private static final String DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(6);
	private static final DateTimeFormatter EVIDENCE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, String> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private String appWindowHandle;
	private Path evidenceDirectory;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws Exception {
		final boolean enabled = Boolean.parseBoolean(
				readSetting("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Enable with -Dsaleads.e2e.enabled=true to run SaleADS E2E.", enabled);

		final boolean headless = Boolean.parseBoolean(
				readSetting("saleads.e2e.headless", "SALEADS_E2E_HEADLESS", "true"));
		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		initializeReport();

		final String loginUrl = readSetting("saleads.e2e.loginUrl", "SALEADS_LOGIN_URL", "");
		if (!loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}

		appWindowHandle = driver.getWindowHandle();
		evidenceDirectory = createEvidenceDirectory();
	}

	@After
	public void tearDown() {
		printFinalReport();

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullTest() {
		runStep("Login", this::validateLoginWithGoogle);
		runStep("Mi Negocio menu", this::validateMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::validateAdministrarNegociosView);
		runStep("Información General", this::validateInformacionGeneralSection);
		runStep("Detalles de la Cuenta", this::validateDetallesCuentaSection);
		runStep("Tus Negocios", this::validateTusNegociosSection);
		runStep("Términos y Condiciones", this::validateTerminosYCondiciones);
		runStep("Política de Privacidad", this::validatePoliticaPrivacidad);

		assertTrue("Failing validations:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private void validateLoginWithGoogle() throws IOException {
		if (!isSidebarVisible()) {
			clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Iniciar con Google", "Google");
			handleGoogleAccountChooserIfPresent(DEFAULT_ACCOUNT_EMAIL);
		}

		waitForSidebar();
		assertAnyTextVisible("Negocio", "Mi Negocio");
		captureScreenshot("01-dashboard-loaded");
	}

	private void validateMiNegocioMenu() throws IOException {
		waitForSidebar();
		assertAnyTextVisible("Negocio");

		ensureMiNegocioExpanded();
		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		ensureMiNegocioExpanded();
		clickByVisibleText("Agregar Negocio");
		assertAnyTextVisible("Crear Nuevo Negocio");
		assertAnyTextVisible("Nombre del Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");
		assertAnyTextVisible("Cancelar");
		assertAnyTextVisible("Crear Negocio");

		captureScreenshot("03-agregar-negocio-modal");

		typeInBusinessNameField("Negocio Prueba Automatizacion");
		clickByVisibleText("Cancelar");
		waitUntilTextHidden("Crear Nuevo Negocio");
	}

	private void validateAdministrarNegociosView() throws IOException {
		ensureMiNegocioExpanded();
		clickByVisibleText("Administrar Negocios");

		assertAnyTextVisible("Información General");
		assertAnyTextVisible("Detalles de la Cuenta");
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Sección Legal");
		captureFullPageScreenshot("04-administrar-negocios-page");
	}

	private void validateInformacionGeneralSection() {
		final WebElement section = sectionContainer("Información General");
		final String expectedEmail = readSetting("saleads.e2e.accountEmail", "SALEADS_ACCOUNT_EMAIL", DEFAULT_ACCOUNT_EMAIL);

		assertSectionContainsText(section, expectedEmail, "User email is visible");
		assertSectionContainsText(section, "BUSINESS PLAN", "BUSINESS PLAN label is visible");
		assertSectionContainsText(section, "Cambiar Plan", "Cambiar Plan button is visible");

		final String expectedName = readSetting("saleads.e2e.accountName", "SALEADS_ACCOUNT_NAME", "");
		if (!expectedName.isBlank()) {
			assertSectionContainsText(section, expectedName, "Configured user name is visible");
		} else {
			assertTrue("Could not detect a likely user name in Información General section.",
					containsLikelyHumanName(section.getText()));
		}
	}

	private void validateDetallesCuentaSection() {
		final WebElement section = sectionContainer("Detalles de la Cuenta");
		assertSectionContainsText(section, "Cuenta creada", "Cuenta creada label is visible");
		assertSectionContainsText(section, "Estado activo", "Estado activo label is visible");
		assertSectionContainsText(section, "Idioma seleccionado", "Idioma seleccionado label is visible");
	}

	private void validateTusNegociosSection() {
		final WebElement section = sectionContainer("Tus Negocios");
		assertSectionContainsText(section, "Agregar Negocio", "Agregar Negocio button exists");
		assertSectionContainsText(section, "Tienes 2 de 3 negocios", "Business quota text is visible");

		final boolean hasListLikeItem = !section
				.findElements(By.xpath(".//li | .//tr[td] | .//*[contains(@class,'business') or contains(@class,'negocio')]"))
				isEmpty();
		assertTrue("Business list is not visible in Tus Negocios section.",
				hasListLikeItem || section.getText().length() > "Tus Negocios".length() + 20);
	}

	private void validateTerminosYCondiciones() throws IOException {
		termsUrl = openLegalLinkAndValidate(new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
				new String[] { "Términos y Condiciones", "Terminos y Condiciones" }, "05-terminos-y-condiciones");
	}

	private void validatePoliticaPrivacidad() throws IOException {
		privacyUrl = openLegalLinkAndValidate(new String[] { "Política de Privacidad", "Politica de Privacidad" },
				new String[] { "Política de Privacidad", "Politica de Privacidad" }, "06-politica-de-privacidad");
	}

	private String openLegalLinkAndValidate(final String[] linkTextCandidates, final String[] headingCandidates,
			final String screenshotName) throws IOException {
		final String sourceTab = driver.getWindowHandle();
		final Set<String> handlesBefore = new HashSet<>(driver.getWindowHandles());

		clickTextInsideSection("Sección Legal", linkTextCandidates);
		waitForUiToLoad();

		final String openedHandle = waitForNewHandle(handlesBefore);
		final boolean openedInNewTab = openedHandle != null;
		if (openedInNewTab) {
			driver.switchTo().window(openedHandle);
			waitForUiToLoad();
		}

		assertAnyTextVisible(headingCandidates);
		final String legalBody = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body"))).getText();
		assertTrue("Legal content text should be visible.", legalBody.trim().length() > 80);

		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(sourceTab);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();
		assertAnyTextVisible("Información General", "Sección Legal");

		return finalUrl;
	}

	private void handleGoogleAccountChooserIfPresent(final String accountEmail) {
		final String currentTab = driver.getWindowHandle();
		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() > 1) {
			handles.stream().filter(handle -> !handle.equals(currentTab)).findFirst().ifPresent(handle -> {
				driver.switchTo().window(handle);
				waitForUiToLoad();
			});
		}

		if (isAnyTextVisible(SHORT_TIMEOUT, accountEmail)) {
			clickByVisibleText(accountEmail);
		}

		if (!driver.getWindowHandle().equals(appWindowHandle) && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
		waitForUiToLoad();
	}

	private void ensureMiNegocioExpanded() {
		waitForSidebar();
		if (!isAnyTextVisible(SHORT_TIMEOUT, "Agregar Negocio", "Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}
		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Administrar Negocios");
	}

	private void typeInBusinessNameField(final String value) {
		final List<By> locators = Arrays.asList(
				By.xpath("//input[contains(@placeholder," + xpathLiteral("Nombre del Negocio") + ")]"),
				By.xpath("//label[contains(normalize-space()," + xpathLiteral("Nombre del Negocio")
						+ ")]/following::input[1]"),
				By.xpath("//input[@name='businessName' or @id='businessName']"));

		for (final By locator : locators) {
			final List<WebElement> inputs = driver.findElements(locator);
			for (final WebElement input : inputs) {
				if (input.isDisplayed()) {
					input.clear();
					input.sendKeys(value);
					waitForUiToLoad();
					return;
				}
			}
		}

		throw new AssertionError("Could not find the 'Nombre del Negocio' input field.");
	}

	private void clickTextInsideSection(final String sectionHeading, final String... textCandidates) {
		final WebElement section = sectionContainer(sectionHeading);
		for (final String candidate : textCandidates) {
			final List<By> locators = clickLocatorsForText(candidate).stream()
					.map(by -> relative(by))
					.collect(Collectors.toList());
			for (final By locator : locators) {
				final List<WebElement> elements = section.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						clickElement(element);
						return;
					}
				}
			}
		}

		throw new AssertionError("Could not click legal link in section '" + sectionHeading + "'.");
	}

	private By relative(final By by) {
		final String locator = by.toString();
		if (locator.startsWith("By.xpath: ")) {
			final String xpath = locator.replace("By.xpath: ", "");
			return By.xpath("." + xpath.substring(1));
		}
		return by;
	}

	private void runStep(final String stepName, final CheckedAction action) {
		try {
			action.run();
			report.put(stepName, "PASS");
		} catch (final Throwable throwable) {
			report.put(stepName, "FAIL");
			failures.add(stepName + " -> " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
			try {
				captureScreenshot("failure-" + toFileSlug(stepName));
			} catch (final Exception ignored) {
				// Ignore secondary failure during evidence capture.
			}
		}
	}

	private void waitForSidebar() {
		wait.until(driverInstance -> {
			final List<WebElement> sidebars = driverInstance.findElements(By.xpath("//aside | //nav"));
			for (final WebElement sidebar : sidebars) {
				if (sidebar.isDisplayed()) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isSidebarVisible() {
		try {
			return driver.findElements(By.xpath("//aside | //nav")).stream().anyMatch(WebElement::isDisplayed);
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void clickByVisibleText(final String... textCandidates) {
		final WebElement element = waitForVisibleText(DEFAULT_TIMEOUT, true, textCandidates);
		clickElement(element);
	}

	private void clickElement(final WebElement element) {
		scrollIntoView(element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void assertAnyTextVisible(final String... textCandidates) {
		waitForVisibleText(DEFAULT_TIMEOUT, false, textCandidates);
	}

	private boolean isAnyTextVisible(final Duration timeout, final String... textCandidates) {
		try {
			waitForVisibleText(timeout, false, textCandidates);
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private WebElement waitForVisibleText(final Duration timeout, final boolean clickable, final String... textCandidates) {
		final Wait<WebDriver> fluentWait = new FluentWait<>(driver)
				.withTimeout(timeout)
				.pollingEvery(Duration.ofMillis(250))
				.ignoring(NoSuchElementException.class)
				.ignoring(StaleElementReferenceException.class);

		return fluentWait.until(driverInstance -> {
			for (final String candidate : textCandidates) {
				final List<By> locators = clickable ? clickLocatorsForText(candidate) : displayLocatorsForText(candidate);
				for (final By locator : locators) {
					for (final WebElement element : driverInstance.findElements(locator)) {
						if (element.isDisplayed()) {
							return element;
						}
					}
				}
			}
			return null;
		});
	}

	private List<By> clickLocatorsForText(final String text) {
		final String literal = xpathLiteral(text);
		return Arrays.asList(
				By.xpath("//button[normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")]"),
				By.xpath("//a[normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")]"),
				By.xpath("//*[@role='button' and (normalize-space()=" + literal + " or contains(normalize-space(),"
						+ literal + ")]"),
				By.xpath("//*[self::span or self::div][normalize-space()=" + literal + " or contains(normalize-space(),"
						+ literal + ")]"));
	}

	private List<By> displayLocatorsForText(final String text) {
		final String literal = xpathLiteral(text);
		return Arrays.asList(
				By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space()," + literal + ")]"));
	}

	private void waitUntilTextHidden(final String text) {
		wait.until(driverInstance -> driverInstance.findElements(By.xpath(
				"//*[normalize-space()=" + xpathLiteral(text) + " or contains(normalize-space()," + xpathLiteral(text) + ")]"))
				.stream()
				.noneMatch(WebElement::isDisplayed));
	}

	private WebElement sectionContainer(final String sectionHeading) {
		final WebElement heading = waitForVisibleText(DEFAULT_TIMEOUT, false, sectionHeading);
		return heading.findElement(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
	}

	private void assertSectionContainsText(final WebElement section, final String text, final String message) {
		assertTrue(message, normalized(section.getText()).contains(normalized(text)));
	}

	private boolean containsLikelyHumanName(final String sectionText) {
		final List<String> ignoredTokens = Arrays.asList("informacion general", "business plan", "cambiar plan", "cuenta",
				"estado", "idioma", "negocios", "seccion legal", "plan");

		for (final String line : sectionText.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.length() < 3 || trimmed.contains("@")) {
				continue;
			}

			final String normalized = normalized(trimmed);
			if (ignoredTokens.stream().anyMatch(normalized::contains)) {
				continue;
			}

			if (trimmed.matches(".*[A-Za-z].*")) {
				return true;
			}
		}
		return false;
	}

	private String waitForNewHandle(final Set<String> handlesBefore) {
		try {
			return wait.until(driverInstance -> {
				final Set<String> handlesAfter = driverInstance.getWindowHandles();
				if (handlesAfter.size() > handlesBefore.size()) {
					for (final String handle : handlesAfter) {
						if (!handlesBefore.contains(handle)) {
							return handle;
						}
					}
				}
				return null;
			});
		} catch (final TimeoutException timeoutException) {
			return null;
		}
	}

	private void waitForUiToLoad() {
		wait.until(driverInstance -> "complete".equals(
				((JavascriptExecutor) driverInstance).executeScript("return document.readyState")));
		try {
			Thread.sleep(300L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final byte[] image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(evidenceDirectory.resolve(checkpointName + ".png"), image);
	}

	private void captureFullPageScreenshot(final String checkpointName) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final Long fullHeight = (Long) ((JavascriptExecutor) driver).executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight,"
							+ "document.body.offsetHeight, document.documentElement.offsetHeight,"
							+ "document.body.clientHeight, document.documentElement.clientHeight);");
			if (fullHeight != null && fullHeight > originalSize.getHeight()) {
				final int resizedHeight = (int) Math.min(fullHeight + 120, 4500L);
				driver.manage().window().setSize(new Dimension(Math.max(originalSize.getWidth(), 1600), resizedHeight));
				waitForUiToLoad();
			}
			captureScreenshot(checkpointName);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(EVIDENCE_TIMESTAMP);
		final Path directory = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(directory);
		return directory;
	}

	private void initializeReport() {
		for (final String field : REPORT_FIELDS) {
			report.put(field, "NOT_RUN");
		}
	}

	private void printFinalReport() {
		System.out.println();
		System.out.println("========== SaleADS Mi Negocio Full Test Report ==========");
		for (final String field : REPORT_FIELDS) {
			System.out.println(field + ": " + report.getOrDefault(field, "NOT_RUN"));
		}
		System.out.println("Términos y Condiciones URL: " + termsUrl);
		System.out.println("Política de Privacidad URL: " + privacyUrl);
		System.out.println("Evidence directory: "
				+ (evidenceDirectory == null ? "N/A" : evidenceDirectory.toAbsolutePath()));
		System.out.println("=========================================================");
		System.out.println();
	}

	private String normalized(final String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT);
	}

	private String readSetting(final String propertyName, final String envName, final String defaultValue) {
		final String property = System.getProperty(propertyName);
		if (property != null && !property.isBlank()) {
			return property.trim();
		}

		final String env = System.getenv(envName);
		if (env != null && !env.isBlank()) {
			return env.trim();
		}

		return defaultValue;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String literal = chars[i] == '\'' ? "\"'\"" : "'" + chars[i] + "'";
			builder.append(literal);
			if (i < chars.length - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	private String toFileSlug(final String value) {
		return normalized(value).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	@FunctionalInterface
	private interface CheckedAction {
		void run() throws Exception;
	}
}
