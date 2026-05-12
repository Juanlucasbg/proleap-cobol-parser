package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioFullTest {

	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> details = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private String googleEmail;
	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final String loginUrl = getPropertyOrEnv("saleads.login.url", "SALEADS_LOGIN_URL");
		final String debuggerAddress = getPropertyOrEnv("saleads.chrome.debugger.address",
				"SALEADS_CHROME_DEBUGGER_ADDRESS");
		googleEmail = getPropertyOrEnv("saleads.google.email", "SALEADS_GOOGLE_EMAIL");
		if (isBlank(googleEmail)) {
			googleEmail = DEFAULT_GOOGLE_EMAIL;
		}

		if (isBlank(loginUrl) && isBlank(debuggerAddress)) {
			Assume.assumeTrue(
					"Set SALEADS_LOGIN_URL (or saleads.login.url) or SALEADS_CHROME_DEBUGGER_ADDRESS to run this workflow.",
					false);
		}

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");
		if (isBlank(debuggerAddress)) {
			final boolean disableHeadless = isTruthy(getPropertyOrEnv("saleads.disable.headless", "SALEADS_DISABLE_HEADLESS"));
			if (!disableHeadless) {
				options.addArguments("--headless=new");
			}
		} else {
			options.setExperimentalOption("debuggerAddress", debuggerAddress.trim());
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDir = Files.createDirectories(Paths.get("target", "saleads-mi-negocio-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));

		if (!isBlank(loginUrl)) {
			driver.get(loginUrl.trim());
		}
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
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
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones",
				() -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos"));
		runStep("Política de Privacidad",
				() -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09-politica"));

		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		assertTrue("Failed validations: " + failedSteps + ". Review artifacts under " + evidenceDir.toAbsolutePath(),
				failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google");
		selectGoogleAccountIfVisible();
		waitForVisibleText("Negocio", "Mi Negocio");
		waitForSidebarVisible();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForVisibleText("Crear Nuevo Negocio");
		waitForAnyElement(Arrays.asList(
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio')]"),
				By.xpath("//*[contains(normalize-space(.), " + toXpathLiteral("Nombre del Negocio") + ")]")));
		waitForVisibleText("Tienes 2 de 3 negocios");
		waitForVisibleText("Cancelar");
		waitForVisibleText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final WebElement nameInput = tryFindVisible(By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio') or contains(@name, 'nombre')]"),
				Duration.ofSeconds(4));
		if (nameInput != null) {
			nameInput.click();
			nameInput.clear();
			nameInput.sendKeys("Negocio Prueba Automatizacion");
		}
		clickByVisibleText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isTextVisible("Administrar Negocios", Duration.ofSeconds(3))) {
			clickByVisibleText("Negocio");
			clickByVisibleText("Mi Negocio");
		}
		clickByVisibleText("Administrar Negocios");
		waitForVisibleText("Información General");
		waitForVisibleText("Detalles de la Cuenta");
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Sección Legal");
		takeScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		waitForVisibleText("Información General");
		waitForVisibleText("BUSINESS PLAN");
		waitForVisibleText("Cambiar Plan");
		waitForVisibleText(googleEmail);

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected a visible email in Información General.", EMAIL_PATTERN.matcher(bodyText).find());
		final boolean hasNameHint = bodyText.contains("Nombre") || bodyText.contains("Usuario") || bodyText.contains("Perfil");
		assertTrue("Expected a visible user name indicator in Información General.", hasNameHint);
	}

	private void stepValidateDetallesCuenta() {
		waitForVisibleText("Cuenta creada");
		waitForVisibleText("Estado activo");
		waitForVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		waitForVisibleText("Tus Negocios");
		waitForVisibleText("Agregar Negocio");
		waitForVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(final String linkText, final String headingText, final String screenshotBase)
			throws Exception {
		final String applicationWindow = driver.getWindowHandle();
		final Set<String> windowsBeforeClick = driver.getWindowHandles();
		final String urlBeforeClick = driver.getCurrentUrl();

		clickByVisibleText(linkText);
		final String newWindowHandle = waitForNewWindow(windowsBeforeClick, Duration.ofSeconds(10));
		final boolean openedNewTab = newWindowHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(newWindowHandle);
			waitForUiToLoad();
		} else {
			wait.until(d -> !d.getCurrentUrl().equals(urlBeforeClick));
			waitForUiToLoad();
		}

		waitForVisibleText(headingText);
		final String legalPageText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Expected legal content text on " + headingText + ".", legalPageText != null && legalPageText.trim().length() > 120);
		legalUrls.put(headingText, driver.getCurrentUrl());
		takeScreenshot(screenshotBase + "-page");

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(applicationWindow);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, true);
			details.put(stepName, "PASS");
		} catch (final Throwable throwable) {
			report.put(stepName, false);
			details.put(stepName, summarizeThrowable(throwable));
			try {
				takeScreenshot("error-" + normalizeFilePart(stepName));
			} catch (final Exception screenshotException) {
				details.put(stepName, details.get(stepName) + " | Screenshot failed: " + summarizeThrowable(screenshotException));
			}
		}
	}

	private void clickByVisibleText(final String... candidateTexts) {
		Throwable lastFailure = null;
		for (final String text : candidateTexts) {
			try {
				final WebElement element = findVisibleElementByText(text, Duration.ofSeconds(8));
				clickElement(element);
				waitForUiToLoad();
				return;
			} catch (final Throwable throwable) {
				lastFailure = throwable;
			}
		}

		final String joinedCandidates = String.join(", ", candidateTexts);
		throw new IllegalStateException("Could not click any element with visible text: " + joinedCandidates, lastFailure);
	}

	private void clickElement(final WebElement element) {
		final JavascriptExecutor javascript = (JavascriptExecutor) driver;
		javascript.executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception e) {
			javascript.executeScript("arguments[0].click();", element);
		}
	}

	private void selectGoogleAccountIfVisible() {
		try {
			final WebElement account = findVisibleElementByText(googleEmail, Duration.ofSeconds(8));
			clickElement(account);
			waitForUiToLoad();
		} catch (final Exception ignored) {
			// No account picker is displayed in this environment/session.
		}
	}

	private void waitForSidebarVisible() {
		wait.until(driver -> {
			final List<WebElement> sidebars = driver.findElements(By.xpath("//aside|//nav"));
			for (final WebElement sidebar : sidebars) {
				if (sidebar.isDisplayed()) {
					return true;
				}
			}
			return false;
		});
	}

	private void waitForVisibleText(final String... candidateTexts) {
		Throwable lastFailure = null;
		for (final String text : candidateTexts) {
			try {
				findVisibleElementByText(text, Duration.ofSeconds(10));
				return;
			} catch (final Throwable throwable) {
				lastFailure = throwable;
			}
		}

		throw new IllegalStateException("Expected visible text not found: " + String.join(" | ", candidateTexts), lastFailure);
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		try {
			findVisibleElementByText(text, timeout);
			return true;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private WebElement tryFindVisible(final By locator, final Duration timeout) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			return shortWait.until(d -> {
				final List<WebElement> candidates = d.findElements(locator);
				for (final WebElement candidate : candidates) {
					if (candidate.isDisplayed()) {
						return candidate;
					}
				}
				return null;
			});
		} catch (final TimeoutException ignored) {
			return null;
		}
	}

	private void waitForAnyElement(final List<By> locators) {
		wait.until(d -> {
			for (final By locator : locators) {
				final List<WebElement> matches = d.findElements(locator);
				for (final WebElement match : matches) {
					if (match.isDisplayed()) {
						return true;
					}
				}
			}
			return false;
		});
	}

	private WebElement findVisibleElementByText(final String text, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		final By byText = By.xpath("//*[contains(normalize-space(.), " + toXpathLiteral(text) + ")]");
		return shortWait.until(d -> {
			final List<WebElement> elements = d.findElements(byText);
			for (final WebElement element : elements) {
				if (!element.isDisplayed()) {
					continue;
				}
				final WebElement clickable = resolveClickableElement(element);
				if (clickable != null && clickable.isDisplayed()) {
					return clickable;
				}
				return element;
			}
			return null;
		});
	}

	private WebElement resolveClickableElement(final WebElement baseElement) {
		try {
			final Object resolved = ((JavascriptExecutor) driver).executeScript(
					"const el = arguments[0]; return el.closest('button,a,[role=\"button\"]') || el;", baseElement);
			if (resolved instanceof WebElement) {
				return (WebElement) resolved;
			}
		} catch (final Exception ignored) {
			// Fallback to the base element if the JS resolution fails.
		}
		return baseElement;
	}

	private String waitForNewWindow(final Set<String> windowsBeforeClick, final Duration timeout) throws InterruptedException {
		final long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			final Set<String> windowsAfterClick = driver.getWindowHandles();
			if (windowsAfterClick.size() > windowsBeforeClick.size()) {
				for (final String handle : windowsAfterClick) {
					if (!windowsBeforeClick.contains(handle)) {
						return handle;
					}
				}
			}
			Thread.sleep(200);
		}
		return null;
	}

	private void waitForUiToLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
	}

	private void takeScreenshot(final String checkpointName) throws IOException {
		final Path screenshotPath = evidenceDir.resolve(checkpointName + ".png");
		final Path tempFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(tempFile, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		reportBuilder.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator()).append(System.lineSeparator());
		reportBuilder.append("Final Report").append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			final Boolean stepPassed = report.get(field);
			final String status = stepPassed == null ? "NOT_EXECUTED" : stepPassed ? "PASS" : "FAIL";
			reportBuilder.append("- ").append(field).append(": ").append(status);

			if (legalUrls.containsKey(field)) {
				reportBuilder.append(" | URL: ").append(legalUrls.get(field));
			}
			if (details.containsKey(field) && !"PASS".equals(details.get(field))) {
				reportBuilder.append(" | Detail: ").append(details.get(field));
			}
			reportBuilder.append(System.lineSeparator());
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, reportBuilder.toString());
		System.out.println(reportBuilder);
	}

	private String summarizeThrowable(final Throwable throwable) {
		final String message = throwable.getMessage();
		return throwable.getClass().getSimpleName() + (message == null ? "" : ": " + message.replaceAll("\\s+", " ").trim());
	}

	private String normalizeFilePart(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String getPropertyOrEnv(final String propertyName, final String envName) {
		final String fromProperty = System.getProperty(propertyName);
		if (!isBlank(fromProperty)) {
			return fromProperty;
		}
		return System.getenv(envName);
	}

	private boolean isTruthy(final String value) {
		if (isBlank(value)) {
			return false;
		}
		final String normalized = value.trim().toLowerCase();
		return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final List<String> parts = new ArrayList<>();
		for (final String part : value.split("'")) {
			if (!part.isEmpty()) {
				parts.add("'" + part + "'");
			}
			parts.add("\"'\"");
		}
		parts.remove(parts.size() - 1);
		return "concat(" + String.join(",", parts) + ")";
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
