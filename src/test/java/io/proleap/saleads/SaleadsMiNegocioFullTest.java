package io.proleap.saleads;

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final Duration SHORT_WAIT = Duration.ofSeconds(8);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, String> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String terminosUrl = "N/A";
	private String privacidadUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		evidenceDir = Files.createDirectories(Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));
		final String startUrl = readStartUrl();
		Assume.assumeTrue("Set SALEADS_START_URL (or saleads.start.url) to the login page URL for this environment.",
				startUrl != null && !startUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-notifications");
		options.addArguments("--lang=es-ES");

		if (isHeadlessEnabled()) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		driver.get(startUrl);
		waitForUiLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Informaci\u00f3n General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("T\u00e9rminos y Condiciones", this::stepValidateTerminos);
		runStep("Pol\u00edtica de Privacidad", this::stepValidatePolitica);

		final String finalReport = buildFinalReport();
		System.out.println(finalReport);
		assertTrue(finalReport, failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		clickByVisibleText("Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google", "Google");

		handlePotentialGoogleAccountSelection();
		waitForAnyVisibleText("Negocio", "Dashboard", "Inicio");
		waitForSidebar();

		takeScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		waitForSidebar();

		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		waitForAnyVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-expanded-menu.png");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Crear Nuevo Negocio");
		ensureAnyVisibleText("Nombre del Negocio");
		ensureAnyVisibleText("Tienes 2 de 3 negocios");
		ensureAnyVisibleText("Cancelar");
		ensureAnyVisibleText("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal.png");

		final Optional<WebElement> input = findVisibleElement(By.xpath("//input[@placeholder='Nombre del Negocio']"));
		if (input.isPresent()) {
			input.get().click();
			input.get().sendKeys("Negocio Prueba Automatizacion");
		}

		clickByVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForAnyVisibleText("Informaci\u00f3n General", "Informacion General");
		ensureAnyVisibleText("Detalles de la Cuenta");
		ensureAnyVisibleText("Tus Negocios");
		ensureAnyVisibleText("Secci\u00f3n Legal", "Seccion Legal");
		takeScreenshot("04-administrar-negocios-account-page.png");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByHeading("Informaci\u00f3n General", "Informacion General");
		final List<WebElement> sectionTexts = section.findElements(By.xpath(".//*[normalize-space()]"));
		boolean hasNameLikeText = false;
		boolean hasEmail = false;

		for (final WebElement textElement : sectionTexts) {
			final String text = textElement.getText().trim();
			if (text.contains("@")) {
				hasEmail = true;
			}
			if (!text.isBlank() && !text.contains("@") && !text.equalsIgnoreCase("Informaci\u00f3n General")
					&& !text.equalsIgnoreCase("Informacion General")) {
				hasNameLikeText = true;
			}
		}

		assertTrue("User name not visible in Informacion General section.", hasNameLikeText);
		assertTrue("User email not visible in Informacion General section.", hasEmail);
		ensureVisibleInsideSection(section, "BUSINESS PLAN");
		ensureVisibleInsideSection(section, "Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = findSectionByHeading("Detalles de la Cuenta");
		ensureVisibleInsideSection(section, "Cuenta creada");
		ensureVisibleInsideSection(section, "Estado activo", "Estado Activo");
		ensureVisibleInsideSection(section, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByHeading("Tus Negocios");
		ensureVisibleInsideSection(section, "Agregar Negocio");
		ensureVisibleInsideSection(section, "Tienes 2 de 3 negocios");

		final List<WebElement> businessRows = section.findElements(By.xpath(
				".//li[normalize-space()] | .//tr[normalize-space()] | .//div[contains(@class,'negocio') and normalize-space()]"));
		assertTrue("Business list is not visible in Tus Negocios section.", !businessRows.isEmpty());
	}

	private void stepValidateTerminos() throws IOException {
		terminosUrl = validateLegalLink("T\u00e9rminos y Condiciones", "Terminos y Condiciones",
				"05-terminos-y-condiciones.png");
	}

	private void stepValidatePolitica() throws IOException {
		privacidadUrl = validateLegalLink("Pol\u00edtica de Privacidad", "Politica de Privacidad",
				"06-politica-de-privacidad.png");
	}

	private String validateLegalLink(final String linkText, final String headingAsciiFallback, final String screenshotFile)
			throws IOException {
		final String appHandle = driver.getWindowHandle();
		final String oldUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText, headingAsciiFallback);
		final String newHandle = waitForNewWindow(handlesBefore);
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		} else {
			waitForUrlChange(oldUrl);
		}

		waitForAnyVisibleText(linkText, headingAsciiFallback);
		assertTrue("Legal content was not visible for " + linkText, hasLongVisibleParagraph());

		takeScreenshot(screenshotFile);
		final String finalUrl = driver.getCurrentUrl();

		if (newHandle != null) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
			waitForAnyVisibleText("Secci\u00f3n Legal", "Seccion Legal");
		}

		return finalUrl;
	}

	private void handlePotentialGoogleAccountSelection() {
		final String primaryHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());
		final String currentUrl = driver.getCurrentUrl();

		final String newHandle = waitForNewWindow(handlesBefore);
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			chooseGoogleAccountIfVisible();
			try {
				new WebDriverWait(driver, SHORT_WAIT).until(ExpectedConditions.numberOfWindowsToBe(handlesBefore.size()));
			} catch (final TimeoutException ignored) {
				// Google flow may stay in the same tab depending on account/session state.
			}
			if (driver.getWindowHandles().contains(primaryHandle)) {
				driver.switchTo().window(primaryHandle);
			}
		} else {
			chooseGoogleAccountIfVisible();
			waitForUrlChange(currentUrl);
		}

		waitForUiLoad();
	}

	private void chooseGoogleAccountIfVisible() {
		final Optional<WebElement> accountOption = findVisibleElement(
				By.xpath("//*[contains(normalize-space(),'" + GOOGLE_ACCOUNT_EMAIL + "')]"));
		if (accountOption.isPresent()) {
			accountOption.get().click();
			waitForUiLoad();
		}
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, "PASS");
		} catch (final Throwable ex) {
			final String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			report.put(stepName, "FAIL - " + detail);
			failures.add(stepName + " -> " + detail);
		}
	}

	private String buildFinalReport() {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio workflow final report\n");
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}
		sb.append("- Terminos y Condiciones URL: ").append(terminosUrl).append('\n');
		sb.append("- Politica de Privacidad URL: ").append(privacidadUrl).append('\n');
		sb.append("- Evidence directory: ").append(evidenceDir.toAbsolutePath());
		return sb.toString();
	}

	private void clickByVisibleText(final String... candidates) {
		WebElement element = null;
		for (final String candidate : candidates) {
			element = waitForClickableText(candidate);
			if (element != null) {
				break;
			}
		}

		if (element == null) {
			throw new TimeoutException("No clickable element found for texts: " + String.join(", ", candidates));
		}

		new Actions(driver).moveToElement(element).pause(Duration.ofMillis(200)).click().perform();
		waitForUiLoad();
	}

	private WebElement waitForClickableText(final String text) {
		try {
			return new WebDriverWait(driver, SHORT_WAIT)
					.until(ExpectedConditions.elementToBeClickable(By.xpath(buildClickableTextXpath(text))));
		} catch (final TimeoutException ignored) {
			return null;
		}
	}

	private void waitForSidebar() {
		wait.until(ExpectedConditions.or(ExpectedConditions.visibilityOfElementLocated(By.tagName("aside")),
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(@class,'sidebar')]")),
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(@aria-label,'sidebar')]"))));
	}

	private void waitForAnyVisibleText(final String... texts) {
		wait.until(driver -> {
			for (final String text : texts) {
				if (isTextVisible(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private void ensureAnyVisibleText(final String... texts) {
		waitForAnyVisibleText(texts);
	}

	private void ensureVisibleInsideSection(final WebElement section, final String... textCandidates) {
		for (final String text : textCandidates) {
			final List<WebElement> matches = section
					.findElements(By.xpath(".//*[contains(normalize-space(), " + toXpathLiteral(text) + ")]"));
			for (final WebElement match : matches) {
				if (match.isDisplayed()) {
					return;
				}
			}
		}
		throw new AssertionError(
				"Expected one of " + String.join(", ", textCandidates) + " inside section " + section.getText());
	}

	private WebElement findSectionByHeading(final String... headingCandidates) {
		for (final String heading : headingCandidates) {
			final Optional<WebElement> headingElement = findVisibleElement(
					By.xpath("//*[normalize-space()=" + toXpathLiteral(heading) + "]"));
			if (headingElement.isPresent()) {
				final Optional<WebElement> section = findVisibleElementFrom(
						headingElement.get(),
						By.xpath("./ancestor::*[self::section or self::article or self::div][.//*[normalize-space()="
								+ toXpathLiteral(heading) + "]][1]"));
				if (section.isPresent()) {
					return section.get();
				}
			}
		}

		throw new AssertionError("Section heading not found: " + String.join(", ", headingCandidates));
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

	private Optional<WebElement> findVisibleElementFrom(final WebElement root, final By locator) {
		final List<WebElement> elements = root.findElements(locator);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return Optional.of(element);
			}
		}
		return Optional.empty();
	}

	private boolean isTextVisible(final String text) {
		final List<WebElement> elements = driver.findElements(By.xpath("//*[contains(normalize-space(), "
				+ toXpathLiteral(text) + ") and not(self::script) and not(self::style)]"));
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private boolean hasLongVisibleParagraph() {
		final List<WebElement> contentCandidates = driver.findElements(By.xpath(
				"//p[string-length(normalize-space()) > 60] | //div[string-length(normalize-space()) > 120]"));
		for (final WebElement candidate : contentCandidates) {
			if (candidate.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void waitForUiLoad() {
		wait.until((ExpectedCondition<Boolean>) wd -> {
			if (!(wd instanceof JavascriptExecutor)) {
				return true;
			}
			final Object state = ((JavascriptExecutor) wd).executeScript("return document.readyState");
			return "complete".equals(state) || "interactive".equals(state);
		});
	}

	private void waitForUrlChange(final String oldUrl) {
		try {
			new WebDriverWait(driver, SHORT_WAIT).until(ExpectedConditions.not(ExpectedConditions.urlToBe(oldUrl)));
		} catch (final TimeoutException ignored) {
			// Some transitions happen within the same URL while app state changes.
		}
		waitForUiLoad();
	}

	private String waitForNewWindow(final Set<String> handlesBefore) {
		try {
			new WebDriverWait(driver, SHORT_WAIT)
					.until(ExpectedConditions.numberOfWindowsToBe(handlesBefore.size() + 1));
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBefore.contains(handle)) {
					return handle;
				}
			}
		} catch (final TimeoutException ignored) {
			return null;
		}
		return null;
	}

	private void takeScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), evidenceDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
	}

	private String readStartUrl() {
		final String systemProperty = System.getProperty("saleads.start.url");
		if (systemProperty != null && !systemProperty.isBlank()) {
			return systemProperty.trim();
		}
		final String env = System.getenv("SALEADS_START_URL");
		return env == null ? null : env.trim();
	}

	private boolean isHeadlessEnabled() {
		final String headlessProperty = System.getProperty("saleads.headless");
		if (headlessProperty != null) {
			return Boolean.parseBoolean(headlessProperty);
		}
		final String env = System.getenv("SALEADS_HEADLESS");
		return env == null || Boolean.parseBoolean(env);
	}

	private String buildClickableTextXpath(final String text) {
		final String literal = toXpathLiteral(text);
		return "(//*[self::button or self::a or @role='button' or @type='button']"
				+ "[contains(normalize-space(), " + literal + ")] | "
				+ "//*[contains(normalize-space(), " + literal
				+ ")]/ancestor-or-self::*[self::button or self::a or @role='button' or @type='button'][1] | "
				+ "//*[contains(normalize-space(), " + literal + ")])[1]";
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(", \"'\", ");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
