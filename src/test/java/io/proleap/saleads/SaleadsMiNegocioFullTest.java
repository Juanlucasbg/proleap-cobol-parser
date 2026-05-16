package io.proleap.saleads;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEXT_TERMS = "T\u00E9rminos y Condiciones";
	private static final String TEXT_PRIVACY = "Pol\u00EDtica de Privacidad";
	private static final String TEXT_INFO = "Informaci\u00F3n General";
	private static final String TEXT_DETAILS = "Detalles de la Cuenta";
	private static final String TEXT_BUSINESSES = "Tus Negocios";
	private static final String TEXT_LEGAL = "Secci\u00F3n Legal";
	private static final String TEXT_MENU = "Mi Negocio";
	private static final String TEXT_ADD_BUSINESS = "Agregar Negocio";
	private static final String TEXT_MANAGE_BUSINESSES = "Administrar Negocios";
	private static final String TEXT_BUSINESS_LIMIT = "Tienes 2 de 3 negocios";
	private static final String TEXT_CREATE_MODAL = "Crear Nuevo Negocio";

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--lang=es-ES");

		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver,
				Duration.ofSeconds(Long.parseLong(System.getenv().getOrDefault("SALEADS_TIMEOUT_SECONDS", "35"))));

		screenshotDir = Paths.get("target", "surefire-reports", "saleads-mi-negocio-screenshots");
		Files.createDirectories(screenshotDir);

		final String baseUrl = System.getenv("SALEADS_BASE_URL");
		if (baseUrl != null && !baseUrl.isBlank()) {
			driver.navigate().to(baseUrl.trim());
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::loginWithGoogle);
		runStep("Mi Negocio menu", this::openMiNegocioMenu);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegocios);
		runStep("Informaci\u00F3n General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("T\u00E9rminos y Condiciones", this::validateTermsAndConditions);
		runStep("Pol\u00EDtica de Privacidad", this::validatePrivacyPolicy);

		printFinalReport();
		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		Assert.assertTrue("Validation failed for steps: " + failedSteps, failedSteps.isEmpty());
	}

	private void loginWithGoogle() throws IOException {
		final WebElement loginButton = waitForAnyVisibleElementWithText("Sign in with Google", "Iniciar sesi\u00F3n con Google",
				"Iniciar sesion con Google", "Continuar con Google", "Login with Google");
		clickAndWait(loginButton);
		selectGoogleAccountIfPrompted(GOOGLE_ACCOUNT);

		final WebElement sidebar = waitForVisible(By.xpath("//aside | //nav[contains(@class,'sidebar')]"));
		Assert.assertTrue("Left sidebar is not visible after login.", sidebar.isDisplayed());
		waitForAnyVisibleElementWithText("Negocio", TEXT_MENU);
		captureScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws IOException {
		expandMiNegocioIfNeeded();
		waitForAnyVisibleElementWithText(TEXT_ADD_BUSINESS);
		waitForAnyVisibleElementWithText(TEXT_MANAGE_BUSINESSES);
		captureScreenshot("02-mi-negocio-expanded");
	}

	private void validateAgregarNegocioModal() throws IOException {
		clickAndWait(waitForAnyVisibleElementWithText(TEXT_ADD_BUSINESS));
		waitForAnyVisibleElementWithText(TEXT_CREATE_MODAL);

		final WebElement businessNameInput = waitForVisible(By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio')] | //label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"));
		Assert.assertTrue("'Nombre del Negocio' input must be visible.", businessNameInput.isDisplayed());
		waitForAnyVisibleElementWithText(TEXT_BUSINESS_LIMIT);
		waitForAnyVisibleElementWithText("Cancelar");
		waitForAnyVisibleElementWithText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		clickAndWait(waitForAnyVisibleElementWithText("Cancelar"));
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[normalize-space(.)=" + asXpathLiteral(TEXT_CREATE_MODAL) + "]")));
	}

	private void openAdministrarNegocios() throws IOException {
		expandMiNegocioIfNeeded();
		clickAndWait(waitForAnyVisibleElementWithText(TEXT_MANAGE_BUSINESSES));

		waitForAnyVisibleElementWithText(TEXT_INFO);
		waitForAnyVisibleElementWithText(TEXT_DETAILS);
		waitForAnyVisibleElementWithText(TEXT_BUSINESSES);
		waitForAnyVisibleElementWithText(TEXT_LEGAL);
		captureScreenshot("04-administrar-negocios-view");
	}

	private void validateInformacionGeneral() {
		final WebElement infoSection = findSectionByHeading(TEXT_INFO);
		final String infoText = infoSection.getText();

		Assert.assertTrue("Expected user email in Informacion General section.", infoText.contains("@"));
		Assert.assertTrue("Expected user name in Informacion General section.", containsLikelyName(infoText));
		Assert.assertTrue("Expected BUSINESS PLAN text in Informacion General section.",
				infoText.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN"));
		waitForAnyVisibleElementWithText("Cambiar Plan");
	}

	private void validateDetallesCuenta() {
		final WebElement detailsSection = findSectionByHeading(TEXT_DETAILS);
		final String detailsText = detailsSection.getText();

		assertContainsIgnoreCase(detailsText, "Cuenta creada");
		assertContainsIgnoreCase(detailsText, "Estado activo");
		assertContainsIgnoreCase(detailsText, "Idioma seleccionado");
	}

	private void validateTusNegocios() {
		final WebElement businessesSection = findSectionByHeading(TEXT_BUSINESSES);
		Assert.assertTrue("Business section should include at least one visible line.", businessesSection.getText().trim().length() > 0);
		waitForAnyVisibleElementWithText(TEXT_ADD_BUSINESS);
		waitForAnyVisibleElementWithText(TEXT_BUSINESS_LIMIT);
	}

	private void validateTermsAndConditions() throws IOException {
		termsUrl = validateLegalLink(TEXT_TERMS, "08-terminos-y-condiciones");
	}

	private void validatePrivacyPolicy() throws IOException {
		privacyUrl = validateLegalLink(TEXT_PRIVACY, "09-politica-de-privacidad");
	}

	private String validateLegalLink(final String linkText, final String screenshotName) throws IOException {
		final String sourceWindow = driver.getWindowHandle();
		final String sourceUrl = driver.getCurrentUrl();
		final Set<String> windowHandlesBefore = driver.getWindowHandles();
		clickAndWait(waitForAnyVisibleElementWithText(linkText));

		wait.until((ExpectedCondition<Boolean>) currentDriver -> hasNewWindow(windowHandlesBefore)
				|| !sourceWindow.equals(currentDriver.getWindowHandle()) || !sourceUrl.equals(currentDriver.getCurrentUrl()));

		final Set<String> windowHandlesAfter = driver.getWindowHandles();
		final boolean openedNewTab = windowHandlesAfter.size() > windowHandlesBefore.size();

		if (openedNewTab) {
			for (final String handle : windowHandlesAfter) {
				if (!windowHandlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForUiLoad();
		}

		waitForAnyVisibleElementWithText(linkText);
		assertLegalContentVisible();
		captureScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(sourceWindow);
		} else {
			driver.navigate().back();
		}
		waitForUiLoad();
		return finalUrl;
	}

	private void assertLegalContentVisible() {
		final String bodyText = waitForVisible(By.tagName("body")).getText().trim();
		Assert.assertTrue("Expected visible legal content.", bodyText.length() > 120);
	}

	private WebElement findSectionByHeading(final String heading) {
		final String headingLiteral = asXpathLiteral(heading);
		final By sectionLocator = By.xpath(
				"//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::p][normalize-space(.)=" + headingLiteral + "]/ancestor::*[self::section or self::article or self::div][1]");
		return waitForVisible(sectionLocator);
	}

	private void expandMiNegocioIfNeeded() {
		if (!isElementVisibleWithText(TEXT_ADD_BUSINESS) || !isElementVisibleWithText(TEXT_MANAGE_BUSINESSES)) {
			tryClickByText("Negocio");
			tryClickByText(TEXT_MENU);
			waitForUiLoad();
		}
	}

	private void selectGoogleAccountIfPrompted(final String email) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(15));
		try {
			shortWait.until(currentDriver -> currentDriver.getCurrentUrl().contains("accounts.google.com")
					|| isElementVisibleWithText(email));
			if (isElementVisibleWithText(email)) {
				clickAndWait(waitForAnyVisibleElementWithText(email));
			}
		} catch (final TimeoutException ignored) {
			// Account chooser is optional in already-authenticated sessions.
		}
	}

	private void runStep(final String stepName, final CheckedStep step) {
		try {
			step.run();
			report.put(stepName, Boolean.TRUE);
		} catch (final Exception exception) {
			report.put(stepName, Boolean.FALSE);
			System.err.println("Step failed: " + stepName + " -> " + exception.getMessage());
		}
	}

	private void clickAndWait(final WebElement element) {
		final WebElement clickable = findClickableAncestor(element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(clickable)).click();
		} catch (final Exception exception) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
		}
		waitForUiLoad();
	}

	private WebElement findClickableAncestor(final WebElement element) {
		try {
			return element.findElement(By.xpath("./ancestor-or-self::*[self::button or self::a or @role='button' or @role='menuitem'][1]"));
		} catch (final NoSuchElementException ignored) {
			return element;
		}
	}

	private WebElement waitForAnyVisibleElementWithText(final String... texts) {
		return wait.until(currentDriver -> {
			for (final String text : texts) {
				for (final By locator : locatorsForText(text)) {
					final WebElement element = firstDisplayed(currentDriver.findElements(locator));
					if (element != null) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private List<By> locatorsForText(final String text) {
		final String literal = asXpathLiteral(text);
		final String containsLiteral = asXpathLiteral(text.replace("  ", " "));
		final List<By> locators = new ArrayList<>();
		locators.add(By.xpath("//*[normalize-space(.)=" + literal + "]"));
		locators.add(By.xpath("//*[contains(normalize-space(.), " + containsLiteral + ")]"));
		return locators;
	}

	private boolean isElementVisibleWithText(final String text) {
		for (final By locator : locatorsForText(text)) {
			if (firstDisplayed(driver.findElements(locator)) != null) {
				return true;
			}
		}
		return false;
	}

	private void tryClickByText(final String text) {
		if (isElementVisibleWithText(text)) {
			clickAndWait(waitForAnyVisibleElementWithText(text));
		}
	}

	private WebElement waitForVisible(final By locator) {
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private WebElement firstDisplayed(final List<WebElement> candidates) {
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				return candidate;
			}
		}
		return null;
	}

	private void waitForUiLoad() {
		wait.until(currentDriver -> "complete"
				.equals(((JavascriptExecutor) currentDriver).executeScript("return document.readyState")));
		try {
			Thread.sleep(300L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void assertContainsIgnoreCase(final String source, final String expected) {
		Assert.assertTrue("Expected text '" + expected + "' was not found.",
				source.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT)));
	}

	private boolean containsLikelyName(final String infoText) {
		final String upper = infoText.toUpperCase(Locale.ROOT);
		for (final String line : upper.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.contains("@") || trimmed.contains("INFORMACION GENERAL")
					|| trimmed.contains("INFORMACI\u00D3N GENERAL") || trimmed.contains("BUSINESS PLAN")
					|| trimmed.contains("CAMBIAR PLAN")) {
				continue;
			}
			if (trimmed.length() >= 3) {
				return true;
			}
		}
		return false;
	}

	private void captureScreenshot(final String name) throws IOException {
		final File sourceFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path targetFile = screenshotDir.resolve(name + ".png");
		Files.copy(sourceFile.toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("Saved screenshot: " + targetFile.toAbsolutePath());
	}

	private boolean hasNewWindow(final Set<String> handlesBefore) {
		return driver.getWindowHandles().size() > handlesBefore.size();
	}

	private void printFinalReport() {
		System.out.println("========== SaleADS Mi Negocio Workflow Report ==========");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		System.out.println(TEXT_TERMS + " URL: " + termsUrl);
		System.out.println(TEXT_PRIVACY + " URL: " + privacyUrl);
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
		System.out.println("========================================================");
	}

	private String asXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder xpath = new StringBuilder("concat(");
		for (int index = 0; index < parts.length; index++) {
			if (index > 0) {
				xpath.append(", \"'\", ");
			}
			xpath.append("'").append(parts[index]).append("'");
		}
		xpath.append(")");
		return xpath.toString();
	}

	@FunctionalInterface
	private interface CheckedStep {
		void run() throws Exception;
	}
}
