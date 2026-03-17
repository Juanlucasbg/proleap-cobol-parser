package io.saleads.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final DateTimeFormatter EVIDENCE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
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

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;
	private String termsFinalUrl = "N/A";
	private String privacyFinalUrl = "N/A";

	@Before
	public void setup() throws IOException {
		for (final String field : REPORT_FIELDS) {
			report.put(field, Boolean.FALSE);
		}

		final String loginUrl = getConfig("SALEADS_LOGIN_URL", "saleads.login.url");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL env var (or -Dsaleads.login.url=...) to run this test in the target SaleADS environment.",
				loginUrl != null && !loginUrl.isBlank());

		evidenceDir = Path.of("target", "saleads-evidence", EVIDENCE_TIMESTAMP.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		final String headless = getConfig("SALEADS_HEADLESS", "saleads.headless");
		if (headless == null || Boolean.parseBoolean(headless)) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1600,1200", "--no-sandbox", "--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		driver.manage().timeouts().implicitlyWait(Duration.ZERO);
		driver.manage().window().setSize(new Dimension(1600, 1200));
		driver.get(loginUrl);
		appWindowHandle = driver.getWindowHandle();
		waitForUiToLoad();
	}

	@After
	public void teardown() {
		try {
			printFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		executeStep("Política de Privacidad", this::stepValidatePoliticaDePrivacidad);

		final boolean allPassed = report.values().stream().allMatch(Boolean::booleanValue);
		Assert.assertTrue("Some SaleADS validations failed:\n" + formattedReport(), allPassed);
	}

	private void stepLoginWithGoogle() {
		final Set<String> handlesBeforeLoginClick = driver.getWindowHandles();
		clickByVisibleText("Sign in with Google", "Continuar con Google", "Iniciar sesión con Google", "Iniciar con Google");
		waitForUiToLoad();

		final Optional<String> maybeNewGoogleWindow = switchToNewWindowIfOpened(handlesBeforeLoginClick);
		if (isGooglePage()) {
			selectGoogleAccountIfSelectorAppears();
			waitForUiToLoad();
		}

		if (maybeNewGoogleWindow.isPresent() && !driver.getWindowHandle().equals(appWindowHandle)) {
			if (driver.getWindowHandles().contains(appWindowHandle)) {
				driver.switchTo().window(appWindowHandle);
			} else {
				driver.switchTo().window(driver.getWindowHandles().iterator().next());
			}
		}

		waitForAnyTextVisible("Negocio", "Mi Negocio");
		waitForUiToLoad();

		final boolean sidebarVisible = isElementVisible(By.xpath("//aside | //nav"));
		Assert.assertTrue("Main app interface or sidebar was not visible after login.", sidebarVisible);
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		if (!isTextVisible("Mi Negocio")) {
			clickByVisibleText("Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Mi Negocio");
		waitForUiToLoad();

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded-menu");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertTextVisible("Crear Nuevo Negocio");
		assertTextVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final Optional<WebElement> maybeInput = findFirstVisibleElement(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio') or @name='businessName']"));
		if (maybeInput.isPresent()) {
			final WebElement input = maybeInput.get();
			safeClick(input);
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
		}

		clickByVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		takeScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
		Assert.assertTrue("User email is not visible.", hasVisibleEmail());
		Assert.assertTrue("User name is not visible in Información General.", hasVisibleUserName());
	}

	private void stepValidateDetallesDeLaCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");

		final WebElement negociosSection = getSectionContainerByHeading("Tus Negocios");
		final List<WebElement> businessEntries = negociosSection.findElements(
				By.xpath(".//li | .//tr | .//article | .//div[contains(@class, 'business') or contains(@class, 'negocio')]"));
		Assert.assertTrue("Business list is not visible in 'Tus Negocios'.",
				!businessEntries.isEmpty() || negociosSection.getText().split("\\R").length >= 4);
	}

	private void stepValidateTerminosYCondiciones() {
		termsFinalUrl = openAndValidateLegalLink("Términos y Condiciones", "08-terminos-y-condiciones");
	}

	private void stepValidatePoliticaDePrivacidad() {
		privacyFinalUrl = openAndValidateLegalLink("Política de Privacidad", "09-politica-de-privacidad");
	}

	private String openAndValidateLegalLink(final String linkText, final String screenshotName) {
		final String originHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);
		waitForUiToLoad();

		final Optional<String> maybeNewHandle = switchToNewWindowIfOpened(handlesBeforeClick);
		final boolean openedInNewTab = maybeNewHandle.isPresent();
		if (openedInNewTab) {
			driver.switchTo().window(maybeNewHandle.get());
		}

		waitForUiToLoad();
		assertTextVisible(linkText);

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Expected legal content on page for '" + linkText + "'.", bodyText != null && bodyText.trim().length() > 120);
		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedInNewTab) {
			driver.close();
			driver.switchTo().window(originHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		return finalUrl;
	}

	private void selectGoogleAccountIfSelectorAppears() {
		if (isTextVisible("Choose an account") || isTextVisible("Elige una cuenta")) {
			clickByVisibleText(GOOGLE_ACCOUNT_EMAIL);
			waitForUiToLoad();
		}
	}

	private boolean isGooglePage() {
		final String url = driver.getCurrentUrl();
		return url.contains("accounts.google.") || isTextVisible("Choose an account") || isTextVisible("Elige una cuenta");
	}

	private Optional<String> switchToNewWindowIfOpened(final Set<String> handlesBefore) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(d -> d != null && d.getWindowHandles().size() > handlesBefore.size());
		} catch (final TimeoutException ignored) {
			return Optional.empty();
		}

		final Set<String> handlesAfter = driver.getWindowHandles();
		for (final String handle : handlesAfter) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				return Optional.of(handle);
			}
		}

		return Optional.empty();
	}

	private void executeStep(final String reportField, final Step step) {
		try {
			step.run();
			report.put(reportField, Boolean.TRUE);
		} catch (final Exception | AssertionError ex) {
			report.put(reportField, Boolean.FALSE);
			takeScreenshot("error-" + sanitizeFilename(reportField));
			System.err.println("Step failed [" + reportField + "]: " + ex.getMessage());
		}
	}

	private void waitForUiToLoad() {
		wait.until(d -> d != null && "complete"
				.equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		waitABit(600);
	}

	private void waitForAnyTextVisible(final String... texts) {
		wait.until(d -> {
			for (final String text : texts) {
				if (isTextVisible(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private void assertTextVisible(final String text) {
		if (!isTextVisible(text)) {
			throw new AssertionError("Expected visible text: " + text);
		}
	}

	private boolean isTextVisible(final String text) {
		final String literal = xpathLiteral(text);
		final List<WebElement> elements = driver.findElements(By.xpath(
				"//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"));
		return elements.stream().anyMatch(WebElement::isDisplayed);
	}

	private void clickByVisibleText(final String... texts) {
		Exception lastFailure = null;

		for (final String text : texts) {
			final String literal = xpathLiteral(text);
			final List<By> locators = Arrays.asList(
					By.xpath("//button[normalize-space()=" + literal + "]"),
					By.xpath("//a[normalize-space()=" + literal + "]"),
					By.xpath("//*[@role='button' and normalize-space()=" + literal + "]"),
					By.xpath("//*[normalize-space()=" + literal + "]"));

			for (final By locator : locators) {
				final Optional<WebElement> maybeElement = findFirstVisibleElement(locator);
				if (maybeElement.isPresent()) {
					try {
						safeClick(maybeElement.get());
						waitForUiToLoad();
						return;
					} catch (final Exception clickFailure) {
						lastFailure = clickFailure;
					}
				}
			}
		}

		throw new AssertionError("Unable to click any element with visible text: " + Arrays.toString(texts), lastFailure);
	}

	private Optional<WebElement> findFirstVisibleElement(final By locator) {
		final List<WebElement> candidates = driver.findElements(locator);
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}

	private boolean isElementVisible(final By locator) {
		return findFirstVisibleElement(locator).isPresent();
	}

	private void safeClick(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		wait.until(ExpectedConditions.elementToBeClickable(element));
		try {
			element.click();
		} catch (final Exception ignored) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private boolean hasVisibleEmail() {
		final List<WebElement> emailNodes = driver.findElements(By.xpath("//*[contains(normalize-space(),'@')]"));
		return emailNodes.stream()
				.filter(WebElement::isDisplayed)
				.map(WebElement::getText)
				.map(String::trim)
				.anyMatch(text -> EMAIL_PATTERN.matcher(text).find());
	}

	private boolean hasVisibleUserName() {
		final String expectedUserName = getConfig("SALEADS_EXPECTED_USER_NAME", "saleads.expected.user.name");
		if (expectedUserName != null && !expectedUserName.isBlank()) {
			return isTextVisible(expectedUserName);
		}

		final WebElement infoSection = getSectionContainerByHeading("Información General");
		final List<WebElement> candidates = infoSection.findElements(
				By.xpath(".//*[self::h1 or self::h2 or self::h3 or self::h4 or self::p or self::span][string-length(normalize-space()) > 2]"));

		for (final WebElement candidate : candidates) {
			final String text = candidate.getText().trim();
			if (!text.isEmpty()
					&& !text.equalsIgnoreCase("Información General")
					&& !text.equalsIgnoreCase("BUSINESS PLAN")
					&& !text.equalsIgnoreCase("Cambiar Plan")
					&& !EMAIL_PATTERN.matcher(text).find()) {
				return true;
			}
		}

		return false;
	}

	private WebElement getSectionContainerByHeading(final String headingText) {
		final String literal = xpathLiteral(headingText);
		final List<WebElement> headingMatches = driver.findElements(By.xpath("//*[normalize-space()=" + literal + "]"));
		for (final WebElement heading : headingMatches) {
			if (heading.isDisplayed()) {
				final List<WebElement> containers = heading.findElements(By.xpath("ancestor::*[self::section or self::div][1]"));
				if (!containers.isEmpty()) {
					return containers.get(0);
				}
			}
		}
		return driver.findElement(By.tagName("body"));
	}

	private void takeScreenshot(final String filename) {
		if (driver == null) {
			return;
		}

		try {
			final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path destination = evidenceDir.resolve(sanitizeFilename(filename) + ".png");
			Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception screenshotError) {
			System.err.println("Failed to capture screenshot '" + filename + "': " + screenshotError.getMessage());
		}
	}

	private String getConfig(final String envName, final String propName) {
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		final String propValue = System.getProperty(propName);
		if (propValue != null && !propValue.isBlank()) {
			return propValue.trim();
		}

		return null;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder concat = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final char current = chars[i];
			if (current == '\'') {
				concat.append("\"'\"");
			} else if (current == '"') {
				concat.append("'\"'");
			} else {
				concat.append("'").append(current).append("'");
			}

			if (i < chars.length - 1) {
				concat.append(",");
			}
		}
		concat.append(")");
		return concat.toString();
	}

	private String sanitizeFilename(final String text) {
		return text.toLowerCase().replaceAll("[^a-z0-9\\-]+", "-").replaceAll("(^-|-$)", "");
	}

	private void waitABit(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String formattedReport() {
		return report.entrySet().stream()
				.map(entry -> String.format("- %s: %s", entry.getKey(), entry.getValue() ? "PASS" : "FAIL"))
				.collect(Collectors.joining(System.lineSeparator()));
	}

	private void printFinalReport() {
		System.out.println("=== saleads_mi_negocio_full_test report ===");
		System.out.println(formattedReport());
		System.out.println("Términos y Condiciones URL: " + termsFinalUrl);
		System.out.println("Política de Privacidad URL: " + privacyFinalUrl);
		System.out.println("Evidence directory: " + (evidenceDir == null ? "N/A" : evidenceDir.toAbsolutePath()));
	}

	@FunctionalInterface
	private interface Step {
		void run();
	}
}
