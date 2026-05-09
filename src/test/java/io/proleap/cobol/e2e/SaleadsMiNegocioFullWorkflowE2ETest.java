package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowE2ETest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[^\\s@]+@[^\\s@]+\\.[^\\s@]+\\b");

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue(
				"Enable this test with -Dsaleads.e2e.enabled=true",
				Boolean.parseBoolean(readConfig("saleads.e2e.enabled", "false")));

		initializeDriver();

		try {
			openLoginPage();

			runStep("Login", this::stepLoginWithGoogle);
			runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			runStep("Información General", this::stepValidateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			runStep("Tus Negocios", this::stepValidateTusNegocios);
			runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
			runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

			printFinalReport();

			final boolean allStepsPassed = finalReport.values().stream().allMatch(Boolean::booleanValue);
			Assert.assertTrue("One or more SaleADS validations failed. Check the final report logs.", allStepsPassed);
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	private void initializeDriver() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");

		if (Boolean.parseBoolean(readConfig("saleads.headless", "true"))) {
			options.addArguments("--headless=new");
		}

		final String remoteUrl = readConfig("saleads.remote.url", System.getenv("SELENIUM_REMOTE_URL"));
		if (isBlank(remoteUrl)) {
			driver = new ChromeDriver(options);
		} else {
			driver = new RemoteWebDriver(new URL(remoteUrl), options);
		}

		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDir = Files.createDirectories(Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));
	}

	private void openLoginPage() {
		if (Boolean.parseBoolean(readConfig("saleads.assume.on.login.page", "false"))) {
			waitForUiToLoad();
			return;
		}

		final String configuredLoginUrl = readConfig("saleads.login.url", System.getenv("SALEADS_LOGIN_URL"));
		Assert.assertFalse(
				"Provide login URL with -Dsaleads.login.url=<url> or SALEADS_LOGIN_URL env var. "
						+ "The test intentionally avoids hardcoding SaleADS domains.",
				isBlank(configuredLoginUrl));

		driver.get(configuredLoginUrl);
		waitForUiToLoad();
	}

	private boolean stepLoginWithGoogle() throws IOException {
		boolean passed = true;

		final WebElement loginWithGoogleButton = findFirstVisibleInteractiveText(
				List.of("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google", "Continuar con Google"),
				DEFAULT_TIMEOUT);
		passed &= check("Login button / 'Sign in with Google' is visible", loginWithGoogleButton != null);

		if (loginWithGoogleButton == null) {
			return false;
		}

		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		clickAndWait(loginWithGoogleButton);
		handleGoogleAccountSelectorIfPresent(handlesBeforeClick);
		waitForUiToLoad();

		passed &= check("Main application interface appears",
				isVisible(By.xpath("//main | //aside | //nav"), DEFAULT_TIMEOUT));
		passed &= check("Left sidebar navigation is visible",
				isVisible(By.xpath("//aside | //nav[.//*[contains(normalize-space(), 'Negocio')]] "
						+ "| //*[contains(normalize-space(), 'Mi Negocio')]"), DEFAULT_TIMEOUT));

		captureScreenshot("01-dashboard-loaded");
		return passed;
	}

	private boolean stepOpenMiNegocioMenu() throws IOException {
		boolean passed = true;

		passed &= check("Left sidebar navigation exists", isVisible(By.xpath("//aside | //nav"), DEFAULT_TIMEOUT));
		clickIfVisible("Negocio");

		final WebElement miNegocio = findFirstVisibleInteractiveText(List.of("Mi Negocio"), DEFAULT_TIMEOUT);
		passed &= check("'Mi Negocio' option is visible", miNegocio != null);

		if (miNegocio == null) {
			return false;
		}

		clickAndWait(miNegocio);

		final boolean agregarVisible = isVisibleText("Agregar Negocio", DEFAULT_TIMEOUT);
		final boolean administrarVisible = isVisibleText("Administrar Negocios", DEFAULT_TIMEOUT);

		passed &= check("Mi Negocio submenu expands", agregarVisible || administrarVisible);
		passed &= check("'Agregar Negocio' is visible", agregarVisible);
		passed &= check("'Administrar Negocios' is visible", administrarVisible);

		captureScreenshot("02-mi-negocio-expanded-menu");
		return passed;
	}

	private boolean stepValidateAgregarNegocioModal() throws IOException {
		boolean passed = true;

		final WebElement agregarNegocio = findFirstVisibleInteractiveText(List.of("Agregar Negocio"), DEFAULT_TIMEOUT);
		passed &= check("'Agregar Negocio' button is clickable", agregarNegocio != null);

		if (agregarNegocio == null) {
			return false;
		}

		clickAndWait(agregarNegocio);

		passed &= check("Modal title 'Crear Nuevo Negocio' is visible",
				isVisibleText("Crear Nuevo Negocio", DEFAULT_TIMEOUT));
		passed &= check("Input field 'Nombre del Negocio' exists",
				findFirstVisible(By.xpath("//input[@placeholder='Nombre del Negocio' "
						+ "or @aria-label='Nombre del Negocio' "
						+ "or @name='nombreNegocio' or @id='nombreNegocio' "
						+ "or @name='businessName' or @id='businessName']"), DEFAULT_TIMEOUT) != null);
		passed &= check("Text 'Tienes 2 de 3 negocios' is visible",
				isVisibleText("Tienes 2 de 3 negocios", DEFAULT_TIMEOUT));
		passed &= check("Button 'Cancelar' is present", isVisibleText("Cancelar", DEFAULT_TIMEOUT));
		passed &= check("Button 'Crear Negocio' is present", isVisibleText("Crear Negocio", DEFAULT_TIMEOUT));

		final WebElement nombreNegocioInput = findFirstVisible(By.xpath("//input[@placeholder='Nombre del Negocio' "
				+ "or @aria-label='Nombre del Negocio' or @name='nombreNegocio' or @id='nombreNegocio' "
				+ "or @name='businessName' or @id='businessName']"), SHORT_TIMEOUT);
		if (nombreNegocioInput != null) {
			nombreNegocioInput.click();
			nombreNegocioInput.clear();
			nombreNegocioInput.sendKeys("Negocio Prueba Automatización");
		}

		captureScreenshot("03-agregar-negocio-modal");
		clickIfVisible("Cancelar");
		waitForUiToLoad();

		return passed;
	}

	private boolean stepOpenAdministrarNegocios() throws IOException {
		boolean passed = true;

		if (!isVisibleText("Administrar Negocios", SHORT_TIMEOUT)) {
			clickIfVisible("Mi Negocio");
		}

		final WebElement administrarNegocios = findFirstVisibleInteractiveText(List.of("Administrar Negocios"),
				DEFAULT_TIMEOUT);
		passed &= check("'Administrar Negocios' is visible", administrarNegocios != null);

		if (administrarNegocios == null) {
			return false;
		}

		clickAndWait(administrarNegocios);

		passed &= check("Section 'Información General' exists", isVisibleText("Información General", DEFAULT_TIMEOUT));
		passed &= check("Section 'Detalles de la Cuenta' exists",
				isVisibleText("Detalles de la Cuenta", DEFAULT_TIMEOUT));
		passed &= check("Section 'Tus Negocios' exists", isVisibleText("Tus Negocios", DEFAULT_TIMEOUT));
		passed &= check("Section 'Sección Legal' exists", isVisibleText("Sección Legal", DEFAULT_TIMEOUT));

		captureScreenshot("04-administrar-negocios-page");
		return passed;
	}

	private boolean stepValidateInformacionGeneral() {
		boolean passed = true;

		final WebElement section = findSectionByTitle("Información General");
		final String sectionText = section == null ? "" : normalizeWhitespace(section.getText());
		final String expectedUserName = readConfig("saleads.expected.user.name", "");

		passed &= check("User name is visible", hasUserName(sectionText, expectedUserName));
		passed &= check("User email is visible",
				EMAIL_PATTERN.matcher(sectionText).find() || isVisible(By.xpath("//*[contains(normalize-space(), '@')]"),
						SHORT_TIMEOUT));
		passed &= check("Text 'BUSINESS PLAN' is visible",
				containsIgnoreCase(sectionText, "BUSINESS PLAN") || isVisibleText("BUSINESS PLAN", SHORT_TIMEOUT));
		passed &= check("Button 'Cambiar Plan' is visible", isVisibleText("Cambiar Plan", SHORT_TIMEOUT));

		return passed;
	}

	private boolean stepValidateDetallesCuenta() {
		boolean passed = true;

		passed &= check("'Cuenta creada' is visible", isVisibleText("Cuenta creada", DEFAULT_TIMEOUT));
		passed &= check("'Estado activo' is visible", isVisibleText("Estado activo", DEFAULT_TIMEOUT));
		passed &= check("'Idioma seleccionado' is visible", isVisibleText("Idioma seleccionado", DEFAULT_TIMEOUT));

		return passed;
	}

	private boolean stepValidateTusNegocios() {
		boolean passed = true;

		final WebElement section = findSectionByTitle("Tus Negocios");
		passed &= check("Business list is visible", businessListIsVisible(section));
		passed &= check("Button 'Agregar Negocio' exists", isVisibleText("Agregar Negocio", SHORT_TIMEOUT));
		passed &= check("Text 'Tienes 2 de 3 negocios' is visible",
				isVisibleText("Tienes 2 de 3 negocios", SHORT_TIMEOUT));

		return passed;
	}

	private boolean stepValidateTerminosYCondiciones() throws IOException {
		return validateLegalLink(
				"Términos y Condiciones",
				"Términos y Condiciones",
				"05-terminos-y-condiciones",
				"Términos y Condiciones");
	}

	private boolean stepValidatePoliticaPrivacidad() throws IOException {
		return validateLegalLink(
				"Política de Privacidad",
				"Política de Privacidad",
				"06-politica-privacidad",
				"Política de Privacidad");
	}

	private boolean validateLegalLink(
			final String linkText,
			final String expectedHeading,
			final String screenshotName,
			final String reportKey) throws IOException {
		boolean passed = true;

		final String applicationHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

		final WebElement link = findFirstVisibleInteractiveText(List.of(linkText), DEFAULT_TIMEOUT);
		passed &= check("Legal link '" + linkText + "' is visible", link != null);

		if (link == null) {
			return false;
		}

		clickAndWait(link);

		switchToNewTabIfOpened(handlesBeforeClick);
		waitForUiToLoad();

		passed &= check(
				"Page contains heading '" + expectedHeading + "'",
				isVisible(By.xpath("//*[self::h1 or self::h2 or self::h3][contains(normalize-space(), "
						+ toXPathLiteral(expectedHeading) + ")] "
						+ "| //*[contains(normalize-space(), " + toXPathLiteral(expectedHeading) + ")]"), DEFAULT_TIMEOUT));

		final String legalBody = normalizeWhitespace(getText(By.tagName("body")));
		passed &= check("Legal content text is visible", legalBody.length() > 120);

		captureScreenshot(screenshotName);
		legalUrls.put(reportKey, driver.getCurrentUrl());

		restoreApplicationContext(applicationHandle);
		return passed;
	}

	private void restoreApplicationContext(final String applicationHandle) {
		if (!driver.getWindowHandles().contains(applicationHandle)) {
			return;
		}

		if (!applicationHandle.equals(driver.getWindowHandle())) {
			driver.close();
			driver.switchTo().window(applicationHandle);
			waitForUiToLoad();
			return;
		}

		driver.navigate().back();
		waitForUiToLoad();
	}

	private void handleGoogleAccountSelectorIfPresent(final Set<String> handlesBeforeClick) {
		final String applicationHandle = driver.getWindowHandle();

		switchToNewTabIfOpened(handlesBeforeClick);
		if (applicationHandle.equals(driver.getWindowHandle()) && driver.getWindowHandles().size() > 1) {
			for (final String candidateHandle : driver.getWindowHandles()) {
				if (!candidateHandle.equals(applicationHandle)) {
					driver.switchTo().window(candidateHandle);
					break;
				}
			}
		}

		final WebElement accountOption = findFirstVisible(
				By.xpath("//*[normalize-space() = " + toXPathLiteral(GOOGLE_ACCOUNT_EMAIL) + "]"),
				Duration.ofSeconds(20));
		if (accountOption != null) {
			clickAndWait(accountOption);
		}

		if (driver.getWindowHandles().contains(applicationHandle) && !applicationHandle.equals(driver.getWindowHandle())) {
			driver.switchTo().window(applicationHandle);
			waitForUiToLoad();
		}
	}

	private void runStep(final String reportKey, final StepAction action) {
		try {
			final boolean passed = action.execute();
			finalReport.put(reportKey, passed);
		} catch (final Exception exception) {
			System.err.println("[FAIL] Step '" + reportKey + "' failed with exception: " + exception.getMessage());
			finalReport.put(reportKey, false);
		}
	}

	private void printFinalReport() {
		System.out.println("===== SaleADS Mi Negocio Workflow Final Report =====");
		for (final Map.Entry<String, Boolean> reportEntry : finalReport.entrySet()) {
			System.out.println(reportEntry.getKey() + ": " + (reportEntry.getValue() ? "PASS" : "FAIL"));
		}
		for (final Map.Entry<String, String> legalUrlEntry : legalUrls.entrySet()) {
			System.out.println(legalUrlEntry.getKey() + " URL: " + legalUrlEntry.getValue());
		}
		System.out.println("Evidence folder: " + evidenceDir.toAbsolutePath());
	}

	private void switchToNewTabIfOpened(final Set<String> handlesBeforeClick) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(currentDriver -> currentDriver.getWindowHandles().size() > handlesBeforeClick.size());
		} catch (final TimeoutException ignored) {
			return;
		}

		for (final String currentHandle : driver.getWindowHandles()) {
			if (!handlesBeforeClick.contains(currentHandle)) {
				driver.switchTo().window(currentHandle);
				return;
			}
		}
	}

	private void clickIfVisible(final String text) {
		final WebElement element = findFirstVisibleInteractiveText(List.of(text), SHORT_TIMEOUT);
		if (element != null) {
			clickAndWait(element);
		}
	}

	private void clickAndWait(final WebElement element) {
		wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		waitForUiToLoad();
		sleep(500);
	}

	private void waitForUiToLoad() {
		try {
			wait.until(currentDriver -> "complete".equals(
					((org.openqa.selenium.JavascriptExecutor) currentDriver).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// For dynamic screens without full navigation, best-effort wait is enough.
		}
	}

	private boolean isVisibleText(final String text, final Duration timeout) {
		final String literal = toXPathLiteral(text);
		return isVisible(By.xpath("//*[normalize-space() = " + literal + " or contains(normalize-space(), " + literal + ")]"),
				timeout);
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		return findFirstVisible(locator, timeout) != null;
	}

	private WebElement findFirstVisibleInteractiveText(final List<String> texts, final Duration timeout) {
		final long timeoutMillis = timeout.toMillis();
		final long timeoutPerTextMillis = Math.max(1500L, timeoutMillis / Math.max(texts.size(), 1));
		final Duration timeoutPerText = Duration.ofMillis(timeoutPerTextMillis);

		for (final String text : texts) {
			final String literal = toXPathLiteral(text);
			final WebElement element = findFirstVisible(By.xpath(
					"//button[normalize-space() = " + literal + " or contains(normalize-space(), " + literal + ")]"
							+ " | //a[normalize-space() = " + literal + " or contains(normalize-space(), " + literal + ")]"
							+ " | //*[@role='button' and (normalize-space() = " + literal
							+ " or contains(normalize-space(), " + literal + "))]"
							+ " | //*[self::span or self::div][normalize-space() = " + literal
							+ " or contains(normalize-space(), " + literal + ")]"),
					timeoutPerText);
			if (element != null) {
				return element;
			}
		}

		return null;
	}

	private WebElement findSectionByTitle(final String title) {
		final String titleLiteral = toXPathLiteral(title);
		return findFirstVisible(By.xpath(
				"//*[self::section or self::div][.//*[normalize-space() = " + titleLiteral
						+ " or contains(normalize-space(), " + titleLiteral + ")]]"),
				DEFAULT_TIMEOUT);
	}

	private WebElement findFirstVisible(final By locator, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(currentDriver -> {
				final List<WebElement> elements = currentDriver.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
				return null;
			});
		} catch (final TimeoutException ignored) {
			return null;
		}
	}

	private String getText(final By locator) {
		final WebElement element = findFirstVisible(locator, SHORT_TIMEOUT);
		return element == null ? "" : element.getText();
	}

	private boolean businessListIsVisible(final WebElement section) {
		if (section == null || !section.isDisplayed()) {
			return false;
		}

		final List<By> potentialListLocators = new ArrayList<>();
		potentialListLocators.add(By.xpath(".//li"));
		potentialListLocators.add(By.xpath(".//tr"));
		potentialListLocators.add(By.xpath(".//article"));
		potentialListLocators.add(By.xpath(".//*[contains(@class, 'business')]"));

		for (final By locator : potentialListLocators) {
			if (!section.findElements(locator).isEmpty()) {
				return true;
			}
		}

		final String sectionText = normalizeWhitespace(section.getText());
		return sectionText.length() > "Tus Negocios".length() + 12;
	}

	private boolean hasUserName(final String sectionText, final String expectedUserName) {
		if (!isBlank(expectedUserName)) {
			return containsIgnoreCase(sectionText, expectedUserName);
		}

		final String[] lines = sectionText.split("\\R");
		for (final String lineRaw : lines) {
			final String line = normalizeWhitespace(lineRaw);
			if (line.length() < 3 || line.length() > 80) {
				continue;
			}
			if (containsIgnoreCase(line, "Información General")
					|| containsIgnoreCase(line, "BUSINESS PLAN")
					|| containsIgnoreCase(line, "Cambiar Plan")
					|| containsIgnoreCase(line, "Plan")
					|| line.contains("@")
					|| !line.chars().anyMatch(Character::isLetter)) {
				continue;
			}
			return true;
		}
		return false;
	}

	private boolean check(final String validationDescription, final boolean passed) {
		System.out.println((passed ? "[PASS] " : "[FAIL] ") + validationDescription);
		return passed;
	}

	private void captureScreenshot(final String fileNamePrefix) throws IOException {
		final File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String fileName = fileNamePrefix + ".png";
		Files.copy(screenshotFile.toPath(), evidenceDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private String readConfig(final String propertyName, final String defaultValue) {
		return System.getProperty(propertyName, defaultValue);
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean containsIgnoreCase(final String value, final String expectedFragment) {
		return value != null
				&& expectedFragment != null
				&& value.toLowerCase(Locale.ROOT).contains(expectedFragment.toLowerCase(Locale.ROOT));
	}

	private String normalizeWhitespace(final String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("\\s+", " ").trim();
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'");
		final StringBuilder literalBuilder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			literalBuilder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				literalBuilder.append(", \"'\", ");
			}
		}
		literalBuilder.append(")");
		return literalBuilder.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		boolean execute() throws Exception;
	}
}
