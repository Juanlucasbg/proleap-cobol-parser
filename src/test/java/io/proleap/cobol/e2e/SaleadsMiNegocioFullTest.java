package io.proleap.cobol.e2e;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio" module.
 *
 * <p>
 * Run with:
 * </p>
 *
 * <pre>
 * mvn -Dtest=SaleadsMiNegocioFullTest test \
 *   -Dsaleads.e2e.enabled=true \
 *   -Dsaleads.startUrl=https://YOUR_ENV_LOGIN_URL \
 *   -Dsaleads.headless=true
 * </pre>
 */
public class SaleadsMiNegocioFullTest {

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;
	private String appWindowHandle;
	private Path evidenceDirectory;

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue(
				"SaleADS E2E test disabled by default. Set -Dsaleads.e2e.enabled=true to execute this workflow test.",
				enabled);

		final String startUrl = System.getProperty("saleads.startUrl", "").trim();
		Assume.assumeTrue(
				"Missing login URL. Set -Dsaleads.startUrl to the current environment login page (dev/staging/production).",
				!startUrl.isEmpty());

		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		final int timeoutSeconds = Integer.parseInt(System.getProperty("saleads.timeout.seconds", "30"));

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT));
		evidenceDirectory = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDirectory);

		driver.get(startUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleads_mi_negocio_full_test() throws Exception {
		runStep("Login", () -> {
			clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
					"Continuar con Google", "Google");
			maybeSelectGoogleAccount();

			waitForAnyVisibleText("Negocio", "Mi Negocio");
			assertVisible("Mi Negocio sidebar option", "Mi Negocio");
			takeScreenshot("step-1-dashboard-loaded");
		});

		runStep("Mi Negocio menu", () -> {
			clickByVisibleText("Negocio");
			clickByVisibleText("Mi Negocio");

			assertVisible("Agregar Negocio submenu option", "Agregar Negocio");
			assertVisible("Administrar Negocios submenu option", "Administrar Negocios");
			takeScreenshot("step-2-mi-negocio-expanded");
		});

		runStep("Agregar Negocio modal", () -> {
			clickByVisibleText("Agregar Negocio");

			assertVisible("Modal title", "Crear Nuevo Negocio");
			final WebElement businessNameInput = waitForBusinessNameInput();
			assertTrue("Input field 'Nombre del Negocio' was not found.", businessNameInput.isDisplayed());
			assertVisible("Business slot text", "Tienes 2 de 3 negocios");
			assertVisible("Cancelar button", "Cancelar");
			assertVisible("Crear Negocio button", "Crear Negocio");
			takeScreenshot("step-3-agregar-negocio-modal");

			clickElement(businessNameInput);
			businessNameInput.sendKeys("Negocio Prueba Automatización");
			clickByVisibleText("Cancelar");
		});

		runStep("Administrar Negocios view", () -> {
			if (!isTextVisible("Administrar Negocios")) {
				clickByVisibleText("Mi Negocio");
			}

			clickByVisibleText("Administrar Negocios");

			assertVisible("Información General section", "Información General");
			assertVisible("Detalles de la Cuenta section", "Detalles de la Cuenta");
			assertVisible("Tus Negocios section", "Tus Negocios");
			assertVisible("Sección Legal section", "Sección Legal");
			takeFullPageScreenshot("step-4-administrar-negocios-full");
		});

		runStep("Información General", () -> {
			assertVisible("Información General heading", "Información General");
			assertTrue("User name was not detected in information section.", isTextVisible("Nombre")
					|| isTextVisible("Usuario") || isTextVisible("Name") || hasLikelyUserName());
			assertTrue("User email is not visible on the page.", hasEmailInBody());
			assertVisible("BUSINESS PLAN text", "BUSINESS PLAN");
			assertVisible("Cambiar Plan button", "Cambiar Plan");
		});

		runStep("Detalles de la Cuenta", () -> {
			assertVisible("Cuenta creada field", "Cuenta creada");
			assertVisible("Estado activo field", "Estado activo");
			assertVisible("Idioma seleccionado field", "Idioma seleccionado");
		});

		runStep("Tus Negocios", () -> {
			assertVisible("Tus Negocios heading", "Tus Negocios");
			assertVisible("Agregar Negocio button", "Agregar Negocio");
			assertVisible("Business capacity text", "Tienes 2 de 3 negocios");
		});

		runStep("Términos y Condiciones", () -> {
			final String finalUrl = validateLegalLink("Términos y Condiciones", "Terminos y Condiciones",
					"step-8-terminos");
			stepDetails.put("Términos y Condiciones", "URL: " + finalUrl);
		});

		runStep("Política de Privacidad", () -> {
			final String finalUrl = validateLegalLink("Política de Privacidad", "Politica de Privacidad",
					"step-9-politica-privacidad");
			stepDetails.put("Política de Privacidad", "URL: " + finalUrl);
		});

		final String report = writeFinalReport();
		System.out.println(report);

		if (hasAnyFailures()) {
			fail("One or more SaleADS workflow validations failed.\n\n" + report);
		}
	}

	private void runStep(final String name, final StepExecutable executable) {
		try {
			executable.run();
			stepStatus.put(name, "PASS");
		} catch (final Throwable throwable) {
			stepStatus.put(name, "FAIL");
			stepDetails.putIfAbsent(name, safeMessage(throwable));
			try {
				takeScreenshot("failure-" + sanitizeFileName(name));
			} catch (final Exception ignored) {
				// Preserve original failure context.
			}
		}
	}

	private String validateLegalLink(final String primaryText, final String fallbackText, final String screenshotName)
			throws IOException {
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String urlBeforeClick = driver.getCurrentUrl();
		final String appHandleBeforeClick = appWindowHandle;

		clickByVisibleText(primaryText, fallbackText);

		wait.until(driver -> driver.getWindowHandles().size() > handlesBeforeClick.size()
				|| !driver.getCurrentUrl().equals(urlBeforeClick));

		String legalHandle = null;
		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBeforeClick.contains(handle)) {
				legalHandle = handle;
				break;
			}
		}

		try {
			if (legalHandle != null) {
				driver.switchTo().window(legalHandle);
			}

			waitForUiToLoad();
			waitForAnyVisibleText(primaryText, fallbackText);

			final String legalBody = getBodyText();
			assertTrue("Legal content text is not visible or too short.", legalBody.length() > 120);
			takeScreenshot(screenshotName);

			return driver.getCurrentUrl();
		} finally {
			if (legalHandle != null) {
				driver.close();
				driver.switchTo().window(appHandleBeforeClick);
				waitForUiToLoad();
			} else {
				driver.navigate().back();
				waitForUiToLoad();
			}
		}
	}

	private void maybeSelectGoogleAccount() {
		final Set<String> initialHandles = new LinkedHashSet<>(driver.getWindowHandles());
		waitForUiToLoad();

		// Some environments open Google's account picker in a new tab.
		if (initialHandles.size() > 1) {
			switchToNewestTab(initialHandles);
		}

		if (isTextVisible(GOOGLE_ACCOUNT_EMAIL)) {
			clickByVisibleText(GOOGLE_ACCOUNT_EMAIL);
		}

		waitForUiToLoad();

		// Wait until we return to the application context and sidebar appears.
		try {
			wait.until((ExpectedCondition<Boolean>) d -> isTextVisible("Negocio") || isTextVisible("Mi Negocio"));
		} catch (final TimeoutException ignored) {
			// Leave assertion responsibility to the step validation.
		}

		// Keep app handle updated in case auth switched tabs.
		appWindowHandle = driver.getWindowHandle();
	}

	private void clickByVisibleText(final String... texts) {
		final WebElement element = waitForAnyVisibleText(texts);
		clickElement(element);
	}

	private void clickElement(final WebElement element) {
		final WebElement clickable = resolveClickable(element);
		try {
			clickable.click();
		} catch (final Exception primaryClickFailure) {
			try {
				new Actions(driver).moveToElement(clickable).click().perform();
			} catch (final Exception actionClickFailure) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
			}
		}
		waitForUiToLoad();
	}

	private WebElement resolveClickable(final WebElement element) {
		try {
			return element.findElement(By.xpath("./ancestor-or-self::*[self::button or self::a or @role='button'][1]"));
		} catch (final NoSuchElementException ignored) {
			return element;
		}
	}

	private WebElement waitForAnyVisibleText(final String... texts) {
		return wait.until(driver -> {
			for (final String text : texts) {
				final WebElement exact = firstDisplayed(
						By.xpath("//*[normalize-space()=" + toXpathLiteral(text) + " and not(self::script)]"));
				if (exact != null) {
					return exact;
				}

				final WebElement partial = firstDisplayed(
						By.xpath("//*[contains(normalize-space(), " + toXpathLiteral(text) + ") and not(self::script)]"));
				if (partial != null) {
					return partial;
				}
			}
			return null;
		});
	}

	private WebElement waitForBusinessNameInput() {
		try {
			return wait.until(driver -> firstDisplayed(By.xpath(
					"//input[@name='nombreDelNegocio' or @id='nombreDelNegocio' or @aria-label='Nombre del Negocio' or contains(@placeholder, 'Nombre del Negocio')]")));
		} catch (final TimeoutException ignored) {
			final WebElement label = waitForAnyVisibleText("Nombre del Negocio");
			final String forAttribute = label.getAttribute("for");
			if (forAttribute != null && !forAttribute.isBlank()) {
				return driver.findElement(By.id(forAttribute));
			}
			return label.findElement(By.xpath("./following::input[1]"));
		}
	}

	private void assertVisible(final String label, final String... texts) {
		try {
			waitForAnyVisibleText(texts);
		} catch (final TimeoutException timeoutException) {
			throw new AssertionError(label + " not visible for texts: " + String.join(", ", texts), timeoutException);
		}
	}

	private boolean isTextVisible(final String text) {
		try {
			return firstDisplayed(By.xpath("//*[normalize-space()=" + toXpathLiteral(text) + " or contains(normalize-space(), "
					+ toXpathLiteral(text) + ")]")) != null;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private WebElement firstDisplayed(final By locator) {
		final List<WebElement> elements = new ArrayList<>(driver.findElements(locator));
		for (final WebElement element : elements) {
			try {
				if (element.isDisplayed()) {
					return element;
				}
			} catch (final StaleElementReferenceException ignored) {
				// Retry on next element.
			}
		}
		return null;
	}

	private void switchToNewestTab(final Set<String> handles) {
		String latest = null;
		for (final String handle : handles) {
			latest = handle;
		}
		if (latest != null) {
			driver.switchTo().window(latest);
		}
	}

	private void waitForUiToLoad() {
		wait.until(driver -> "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
		try {
			Thread.sleep(350L);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for UI load.", interruptedException);
		}
	}

	private boolean hasLikelyUserName() {
		final String body = getBodyText();
		final Pattern twoWordNamePattern = Pattern.compile("\\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\\b");
		final Matcher matcher = twoWordNamePattern.matcher(body);
		return matcher.find();
	}

	private boolean hasEmailInBody() {
		final String body = getBodyText();
		final Pattern emailPattern = Pattern
				.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
		final Matcher matcher = emailPattern.matcher(body);
		return matcher.find();
	}

	private String getBodyText() {
		return driver.findElement(By.tagName("body")).getText().trim();
	}

	private void takeScreenshot(final String name) throws IOException {
		final Path destination = evidenceDirectory.resolve(sanitizeFileName(name) + ".png");
		final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.write(destination, screenshot);
	}

	private void takeFullPageScreenshot(final String name) throws IOException {
		final int currentWidth = driver.manage().window().getSize().getWidth();
		final int currentHeight = driver.manage().window().getSize().getHeight();

		final long pageHeight = ((Number) ((JavascriptExecutor) driver)
				.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);"))
				.longValue();
		final int clampedHeight = (int) Math.min(Math.max(pageHeight + 120L, 1080L), 8000L);

		try {
			driver.manage().window().setSize(new org.openqa.selenium.Dimension(currentWidth, clampedHeight));
			waitForUiToLoad();
			takeScreenshot(name);
		} finally {
			driver.manage().window().setSize(new org.openqa.selenium.Dimension(currentWidth, currentHeight));
			waitForUiToLoad();
		}
	}

	private String writeFinalReport() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
		builder.append("Evidence directory: ").append(evidenceDirectory.toAbsolutePath()).append(System.lineSeparator());
		builder.append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			builder.append(field).append(": ").append(stepStatus.getOrDefault(field, "FAIL"));
			final String details = stepDetails.get(field);
			if (details != null && !details.isBlank()) {
				builder.append(" (").append(details).append(")");
			}
			builder.append(System.lineSeparator());
		}

		final String report = builder.toString();
		Files.writeString(evidenceDirectory.resolve("final-report.txt"), report, StandardCharsets.UTF_8);
		return report;
	}

	private boolean hasAnyFailures() {
		for (final String field : REPORT_FIELDS) {
			if (!"PASS".equalsIgnoreCase(stepStatus.getOrDefault(field, "FAIL"))) {
				return true;
			}
		}
		return false;
	}

	private String safeMessage(final Throwable throwable) {
		final String message = throwable.getMessage();
		return message == null ? throwable.getClass().getSimpleName() : message;
	}

	private String sanitizeFileName(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	@FunctionalInterface
	private interface StepExecutable {
		void run() throws Exception;
	}
}
