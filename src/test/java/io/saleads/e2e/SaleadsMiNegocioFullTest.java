package io.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * Environment-agnostic behavior:
 * <ul>
 * <li>Does not hardcode a specific domain.</li>
 * <li>Reads login page URL from SALEADS_LOGIN_URL.</li>
 * <li>Selects UI elements by visible text whenever possible.</li>
 * </ul>
 *
 * <p>
 * Execution notes:
 * <ul>
 * <li>Set SALEADS_LOGIN_URL to the login page of your current environment.</li>
 * <li>Optional: set SALEADS_HEADLESS=false to watch browser actions.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String applicationWindowHandle;

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		evidenceDir = createEvidenceDir();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-gpu");

		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (loginUrl == null || loginUrl.isBlank()) {
			throw new IllegalStateException(
					"Missing SALEADS_LOGIN_URL. This test is environment-agnostic and requires an external login URL.");
		}

		driver.get(loginUrl);
		waitForUiToLoad();
		applicationWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		printFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		recordStep("Login", this::stepLoginWithGoogle);
		recordStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		recordStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		recordStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		recordStep("Información General", this::stepValidateInformacionGeneral);
		recordStep("Detalles de la Cuenta", this::stepValidateDetallesDeLaCuenta);
		recordStep("Tus Negocios", this::stepValidateTusNegocios);
		recordStep("Términos y Condiciones", () -> stepValidateLegalDocument("Términos y Condiciones", "08-terminos"));
		recordStep("Política de Privacidad", () -> stepValidateLegalDocument("Política de Privacidad", "09-privacidad"));

		assertTrue("One or more workflow validations failed. See report in logs.", report.values().stream().allMatch(Boolean::booleanValue));
	}

	private boolean stepLoginWithGoogle() {
		final CheckCollector checks = new CheckCollector();

		checks.expect(clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"),
				"Google login button is clickable.");
		selectGoogleAccountIfVisible();

		final boolean mainInterfaceVisible = waitForAnyVisibleText(Duration.ofSeconds(90), "Negocio", "Mi Negocio", "Dashboard", "Inicio");
		final boolean leftSidebarVisible = waitForAnyVisibleElement(Duration.ofSeconds(40),
				By.xpath("//aside"),
				By.xpath("//nav[contains(@class,'sidebar')]"),
				By.xpath("//nav[.//*[normalize-space()='Negocio' or normalize-space()='Mi Negocio']]"));

		checks.expect(mainInterfaceVisible, "Main application interface is visible after login.");
		checks.expect(leftSidebarVisible, "Left sidebar navigation is visible.");

		takeScreenshot("01-dashboard-loaded");
		return checks.passed();
	}

	private boolean stepOpenMiNegocioMenu() {
		final CheckCollector checks = new CheckCollector();

		if (!isAnyTextVisible("Mi Negocio")) {
			clickByVisibleText("Negocio");
		}

		checks.expect(clickByVisibleText("Mi Negocio"), "Clicked Mi Negocio menu.");
		checks.expect(waitForAnyVisibleText(Duration.ofSeconds(20), "Agregar Negocio"), "Submenu item Agregar Negocio is visible.");
		checks.expect(waitForAnyVisibleText(Duration.ofSeconds(20), "Administrar Negocios"),
				"Submenu item Administrar Negocios is visible.");

		takeScreenshot("02-mi-negocio-menu-expanded");
		return checks.passed();
	}

	private boolean stepValidateAgregarNegocioModal() {
		final CheckCollector checks = new CheckCollector();

		if (!isAnyTextVisible("Agregar Negocio")) {
			clickByVisibleText("Mi Negocio");
		}
		checks.expect(clickByVisibleText("Agregar Negocio"), "Clicked Agregar Negocio.");
		checks.expect(waitForAnyVisibleText(Duration.ofSeconds(20), "Crear Nuevo Negocio"),
				"Modal title Crear Nuevo Negocio is visible.");
		checks.expect(isAnyTextVisible("Nombre del Negocio"), "Input label Nombre del Negocio is visible.");
		checks.expect(isAnyTextVisible("Tienes 2 de 3 negocios"), "Business quota text is visible.");
		checks.expect(isAnyTextVisible("Cancelar"), "Cancelar button is visible.");
		checks.expect(isAnyTextVisible("Crear Negocio"), "Crear Negocio button is visible.");

		typeIfVisible("Nombre del Negocio", "Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");

		takeScreenshot("03-agregar-negocio-modal");
		return checks.passed();
	}

	private boolean stepOpenAdministrarNegocios() {
		final CheckCollector checks = new CheckCollector();

		if (!isAnyTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		checks.expect(clickByVisibleText("Administrar Negocios"), "Clicked Administrar Negocios.");
		checks.expect(waitForAnyVisibleText(Duration.ofSeconds(30), "Información General"), "Información General section exists.");
		checks.expect(waitForAnyVisibleText(Duration.ofSeconds(30), "Detalles de la Cuenta"), "Detalles de la Cuenta section exists.");
		checks.expect(waitForAnyVisibleText(Duration.ofSeconds(30), "Tus Negocios"), "Tus Negocios section exists.");
		checks.expect(waitForAnyVisibleText(Duration.ofSeconds(30), "Sección Legal", "Seccion Legal"), "Sección Legal exists.");

		takeScreenshot("04-administrar-negocios");
		return checks.passed();
	}

	private boolean stepValidateInformacionGeneral() {
		final CheckCollector checks = new CheckCollector();

		final String bodyText = safeBodyText();
		final boolean emailVisible = EMAIL_PATTERN.matcher(bodyText).find();
		final boolean possibleUserNameVisible = containsLikelyUserName(bodyText);

		checks.expect(possibleUserNameVisible, "A user name appears in the account page.");
		checks.expect(emailVisible, "A user email appears in the account page.");
		checks.expect(isAnyTextVisible("BUSINESS PLAN"), "BUSINESS PLAN text is visible.");
		checks.expect(isAnyTextVisible("Cambiar Plan"), "Cambiar Plan button is visible.");
		return checks.passed();
	}

	private boolean stepValidateDetallesDeLaCuenta() {
		final CheckCollector checks = new CheckCollector();
		checks.expect(isAnyTextVisible("Cuenta creada"), "Cuenta creada is visible.");
		checks.expect(isAnyTextVisible("Estado activo"), "Estado activo is visible.");
		checks.expect(isAnyTextVisible("Idioma seleccionado"), "Idioma seleccionado is visible.");
		return checks.passed();
	}

	private boolean stepValidateTusNegocios() {
		final CheckCollector checks = new CheckCollector();
		checks.expect(isAnyTextVisible("Tus Negocios"), "Tus Negocios section title is visible.");
		checks.expect(isAnyTextVisible("Agregar Negocio"), "Agregar Negocio button exists.");
		checks.expect(isAnyTextVisible("Tienes 2 de 3 negocios"), "Business quota text is visible.");
		checks.expect(hasBusinessListContent(), "Business list content is visible.");
		return checks.passed();
	}

	private boolean stepValidateLegalDocument(final String linkText, final String screenshotName) {
		final CheckCollector checks = new CheckCollector();

		final String originalWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String originalUrl = driver.getCurrentUrl();

		checks.expect(clickByVisibleText(linkText), "Clicked legal link: " + linkText);

		try {
			new WebDriverWait(driver, Duration.ofSeconds(30))
					.until(d -> d.getWindowHandles().size() > handlesBeforeClick.size() || !d.getCurrentUrl().equals(originalUrl));
		} catch (final TimeoutException ex) {
			System.err.println("[WARN] Timeout while waiting legal navigation for: " + linkText);
		}

		final Set<String> handlesAfterClick = new LinkedHashSet<>(driver.getWindowHandles());
		handlesAfterClick.removeAll(handlesBeforeClick);
		final boolean openedNewTab = !handlesAfterClick.isEmpty();

		if (openedNewTab) {
			driver.switchTo().window(handlesAfterClick.iterator().next());
		}

		waitForUiToLoad();
		checks.expect(waitForAnyVisibleText(Duration.ofSeconds(20), linkText), "Heading " + linkText + " is visible.");
		checks.expect(safeBodyText().trim().length() > 120, "Legal content text is visible.");

		takeScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		// Ensure the account page is still active for subsequent validations.
		waitForAnyVisibleText(Duration.ofSeconds(20), "Sección Legal", "Seccion Legal", "Tus Negocios");
		return checks.passed();
	}

	private void selectGoogleAccountIfVisible() {
		waitForUiToLoad();
		clickByVisibleText(GOOGLE_ACCOUNT_EMAIL);
	}

	private boolean clickByVisibleText(final String... texts) {
		for (final String text : texts) {
			final List<By> selectors = List.of(
					By.xpath("//button[normalize-space()=" + toXPathLiteral(text) + "]"),
					By.xpath("//a[normalize-space()=" + toXPathLiteral(text) + "]"),
					By.xpath("//*[@role='button' and normalize-space()=" + toXPathLiteral(text) + "]"),
					By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + "]"));

			for (final By selector : selectors) {
				final List<WebElement> matches = driver.findElements(selector);
				for (final WebElement match : matches) {
					if (!match.isDisplayed()) {
						continue;
					}
					final WebElement clickable = firstClickableAncestor(match);
					if (clickElement(clickable)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private WebElement firstClickableAncestor(final WebElement element) {
		try {
			return element.findElement(
					By.xpath("./ancestor-or-self::*[self::button or self::a or @role='button' or @tabindex='0'][1]"));
		} catch (final Exception ignored) {
			return element;
		}
	}

	private boolean clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element));
			element.click();
			waitForUiToLoad();
			return true;
		} catch (final Exception clickException) {
			try {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				waitForUiToLoad();
				return true;
			} catch (final Exception jsException) {
				return false;
			}
		}
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			if (isTextVisible(text)) {
				return true;
			}
		}
		return false;
	}

	private boolean isTextVisible(final String text) {
		final List<WebElement> exactMatches = driver.findElements(By.xpath("//*[normalize-space()=" + toXPathLiteral(text) + "]"));
		for (final WebElement match : exactMatches) {
			if (match.isDisplayed()) {
				return true;
			}
		}

		final List<WebElement> containsMatches = driver
				.findElements(By.xpath("//*[contains(normalize-space(), " + toXPathLiteral(text) + ")]"));
		for (final WebElement match : containsMatches) {
			if (match.isDisplayed()) {
				return true;
			}
		}

		return false;
	}

	private boolean waitForAnyVisibleText(final Duration timeout, final String... texts) {
		try {
			new WebDriverWait(driver, timeout).until(d -> isAnyTextVisible(texts));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private boolean waitForAnyVisibleElement(final Duration timeout, final By... selectors) {
		try {
			new WebDriverWait(driver, timeout).until(d -> {
				for (final By selector : selectors) {
					final List<WebElement> elements = d.findElements(selector);
					for (final WebElement element : elements) {
						if (element.isDisplayed()) {
							return true;
						}
					}
				}
				return false;
			});
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void typeIfVisible(final String fieldLabel, final String value) {
		final List<By> selectors = List.of(
				By.xpath("//input[@placeholder=" + toXPathLiteral(fieldLabel) + "]"),
				By.xpath("//label[normalize-space()=" + toXPathLiteral(fieldLabel) + "]/following::input[1]"),
				By.xpath("//input[contains(@aria-label, " + toXPathLiteral(fieldLabel) + ")]"));

		for (final By selector : selectors) {
			final List<WebElement> fields = driver.findElements(selector);
			for (final WebElement field : fields) {
				if (field.isDisplayed()) {
					field.clear();
					field.sendKeys(value);
					waitForUiToLoad();
					return;
				}
			}
		}
	}

	private boolean hasBusinessListContent() {
		final List<WebElement> cards = driver.findElements(By.xpath(
				"//section[.//*[contains(normalize-space(),'Tus Negocios')]]//*[self::li or self::tr or contains(@class,'card') or contains(@class,'business')]"));
		for (final WebElement card : cards) {
			if (card.isDisplayed()) {
				return true;
			}
		}

		final String bodyText = safeBodyText();
		return bodyText.contains("Tus Negocios") && bodyText.lines().count() > 15;
	}

	private boolean containsLikelyUserName(final String bodyText) {
		for (final String rawLine : bodyText.split("\\R")) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.length() < 4 || line.length() > 60) {
				continue;
			}
			if (line.contains("@") || line.matches(".*\\d.*")) {
				continue;
			}
			if (line.equalsIgnoreCase("Información General") || line.equalsIgnoreCase("BUSINESS PLAN")
					|| line.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}
			if (line.matches("[A-Za-zÁÉÍÓÚáéíóúÑñ]+(?: [A-Za-zÁÉÍÓÚáéíóúÑñ]+)+")) {
				return true;
			}
		}
		return false;
	}

	private void waitForUiToLoad() {
		try {
			wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Some redirects temporarily block script execution; this is best effort.
		}

		try {
			Thread.sleep(650L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String safeBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception ex) {
			return "";
		}
	}

	private void recordStep(final String label, final CheckedAction action) {
		boolean passed;
		try {
			passed = action.run();
		} catch (final Exception ex) {
			passed = false;
			System.err.println("[FAIL] " + label + ": " + ex.getMessage());
			takeScreenshot("failure-" + sanitize(label));
		}
		report.put(label, passed);
	}

	private Path createEvidenceDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path outputDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(outputDir);
		return outputDir;
	}

	private void takeScreenshot(final String name) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		try {
			final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path target = evidenceDir.resolve(sanitize(name) + ".png");
			Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception ex) {
			System.err.println("[WARN] Could not capture screenshot " + name + ": " + ex.getMessage());
		}
	}

	private void printFinalReport() {
		if (report.isEmpty()) {
			return;
		}

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("==== SaleADS Mi Negocio Workflow Final Report ====\n");
		report.forEach((step, status) -> reportBuilder.append("- ").append(step).append(": ")
				.append(status ? "PASS" : "FAIL").append('\n'));

		if (!legalUrls.isEmpty()) {
			reportBuilder.append("---- Captured legal URLs ----\n");
			legalUrls.forEach((title, url) -> reportBuilder.append("- ").append(title).append(": ").append(url).append('\n'));
		}

		reportBuilder.append("Evidence folder: ").append(evidenceDir.toAbsolutePath()).append('\n');
		reportBuilder.append("==================================================\n");

		System.out.print(reportBuilder);

		try {
			Files.writeString(evidenceDir.resolve("final-report.txt"), reportBuilder.toString());
		} catch (final IOException ex) {
			System.err.println("[WARN] Could not write final report file: " + ex.getMessage());
		}
	}

	private String sanitize(final String raw) {
		return raw.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				builder.append(", ");
			}
			if (chars[i] == '\'') {
				builder.append("\"'\"");
			} else {
				builder.append('\'').append(chars[i]).append('\'');
			}
		}
		builder.append(')');
		return builder.toString();
	}

	@FunctionalInterface
	private interface CheckedAction {
		boolean run() throws Exception;
	}

	private static final class CheckCollector {
		private boolean passed = true;

		void expect(final boolean condition, final String message) {
			if (!condition) {
				System.err.println("[FAIL] " + message);
				passed = false;
			}
		}

		boolean passed() {
			return passed;
		}
	}
}
