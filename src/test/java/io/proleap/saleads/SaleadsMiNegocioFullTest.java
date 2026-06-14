package io.proleap.saleads;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
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
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(20);
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, String> finalReport = new LinkedHashMap<>();
	private final DateTimeFormatter runFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String termsFinalUrl = "N/A";
	private String privacyFinalUrl = "N/A";
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		final String startUrl = readConfig("saleads.start.url", "SALEADS_START_URL");
		final String debuggerAddress = readConfig("saleads.debugger.address", "SALEADS_DEBUGGER_ADDRESS");
		Assume.assumeTrue(
				"Set saleads.start.url / SALEADS_START_URL or attach to an already-open browser via saleads.debugger.address / SALEADS_DEBUGGER_ADDRESS.",
				!isBlank(startUrl) || !isBlank(debuggerAddress));

		evidenceDir = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(runFormatter));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		if (!isBlank(debuggerAddress)) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress.trim());
		} else if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "false"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, TIMEOUT);

		if (isBlank(debuggerAddress)) {
			driver.get(startUrl.trim());
			waitForUiToLoad();
		}

		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String googleAccount = readConfig("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);

		final boolean loginOk = runStep("Login", () -> {
			loginWithGoogle(googleAccount);
			assertAnyTextVisible("Negocio");
			takeScreenshot("01-dashboard-loaded");
		});

		final boolean miNegocioMenuOk = runStep("Mi Negocio menu", loginOk, () -> {
			openMiNegocioMenu();
			assertAnyTextVisible("Agregar Negocio");
			assertAnyTextVisible("Administrar Negocios");
			takeScreenshot("02-mi-negocio-menu-expanded");
		});

		final boolean agregarModalOk = runStep("Agregar Negocio modal", miNegocioMenuOk, () -> {
			clickByTextAndWait("Agregar Negocio");
			assertAnyTextVisible("Crear Nuevo Negocio");
			assertAnyTextVisible("Nombre del Negocio");
			assertAnyTextVisible("Tienes 2 de 3 negocios");
			assertAnyTextVisible("Cancelar");
			assertAnyTextVisible("Crear Negocio");
			takeScreenshot("03-agregar-negocio-modal");
			typeInFieldIfPresent("Nombre del Negocio", "Negocio Prueba Automatización");
			clickByTextAndWait("Cancelar");
		});

		final boolean administrarNegociosOk = runStep("Administrar Negocios view", miNegocioMenuOk, () -> {
			openMiNegocioMenu();
			clickByTextAndWait("Administrar Negocios");
			assertAnyTextVisible("Información General", "Informacion General");
			assertAnyTextVisible("Detalles de la Cuenta", "Detalles de la cuenta");
			assertAnyTextVisible("Tus Negocios");
			assertAnyTextVisible("Sección Legal", "Seccion Legal");
			takeScreenshot("04-administrar-negocios");
		});

		final boolean informacionGeneralOk = runStep("Información General", administrarNegociosOk, () -> {
			assertAnyTextVisible("BUSINESS PLAN");
			assertAnyTextVisible("Cambiar Plan");
			assertVisibleUserIdentity();
		});

		final boolean detallesCuentaOk = runStep("Detalles de la Cuenta", administrarNegociosOk, () -> {
			assertAnyTextVisible("Cuenta creada");
			assertAnyTextVisible("Estado activo", "Estado Activo");
			assertAnyTextVisible("Idioma seleccionado");
		});

		final boolean tusNegociosOk = runStep("Tus Negocios", administrarNegociosOk, () -> {
			assertAnyTextVisible("Tus Negocios");
			assertAnyTextVisible("Agregar Negocio");
			assertAnyTextVisible("Tienes 2 de 3 negocios");
		});

		final boolean terminosOk = runStep("Términos y Condiciones", administrarNegociosOk, () -> {
			termsFinalUrl = openLegalLinkAndValidate(new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
					new String[] { "Términos y Condiciones", "Terminos y Condiciones" }, "05-terminos");
		});

		final boolean privacidadOk = runStep("Política de Privacidad", administrarNegociosOk, () -> {
			privacyFinalUrl = openLegalLinkAndValidate(new String[] { "Política de Privacidad", "Politica de Privacidad" },
					new String[] { "Política de Privacidad", "Politica de Privacidad" }, "06-politica-privacidad");
		});

		final String report = buildFinalReport();
		System.out.println(report);

		final boolean allPassed = loginOk && miNegocioMenuOk && agregarModalOk && administrarNegociosOk && informacionGeneralOk
				&& detallesCuentaOk && tusNegociosOk && terminosOk && privacidadOk;

		Assert.assertTrue(report, allPassed);
	}

	private void loginWithGoogle(final String googleAccount) {
		final boolean hasLoginButton = hasVisibleText("Sign in with Google", "Iniciar sesión con Google",
				"Iniciar sesion con Google", "Continuar con Google", "Google");

		if (hasLoginButton) {
			clickByTextAndWait("Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
					"Continuar con Google", "Google");
			selectGoogleAccountIfVisible(googleAccount);
		}

		assertMainInterfaceLoaded();
	}

	private void assertMainInterfaceLoaded() {
		assertAnyTextVisible("Negocio");
	}

	private void openMiNegocioMenu() {
		clickByTextAndWait("Negocio");
		clickByTextAndWait("Mi Negocio");
	}

	private void typeInFieldIfPresent(final String label, final String value) {
		try {
			final WebElement field = findInputByLabelText(label);
			field.click();
			field.clear();
			field.sendKeys(value);
			waitForUiToLoad();
		} catch (final NoSuchElementException ignored) {
			// Optional action only.
		}
	}

	private WebElement findInputByLabelText(final String labelText) {
		final List<WebElement> labels = driver
				.findElements(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(labelText) + ")]"));

		for (final WebElement label : labels) {
			if (!isDisplayed(label)) {
				continue;
			}

			final String forAttribute = label.getAttribute("for");
			if (!isBlank(forAttribute)) {
				final List<WebElement> inputsByFor = driver.findElements(By.id(forAttribute));
				for (final WebElement input : inputsByFor) {
					if (isDisplayed(input)) {
						return input;
					}
				}
			}

			final List<WebElement> inputsInside = label.findElements(By.xpath(".//input | .//textarea"));
			for (final WebElement input : inputsInside) {
				if (isDisplayed(input)) {
					return input;
				}
			}
		}

		final List<WebElement> placeholders = driver
				.findElements(By.xpath("//input[contains(@placeholder, " + xpathLiteral(labelText) + ")]"));
		for (final WebElement input : placeholders) {
			if (isDisplayed(input)) {
				return input;
			}
		}

		throw new NoSuchElementException("Input field not found for label: " + labelText);
	}

	private void selectGoogleAccountIfVisible(final String googleAccount) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
			shortWait.until(d -> hasVisibleText(googleAccount));
			clickByTextAndWait(googleAccount);
		} catch (final TimeoutException ignored) {
			// Account chooser did not appear; the app may have reused an authenticated session.
		}
	}

	private String openLegalLinkAndValidate(final String[] linkTexts, final String[] headingTexts, final String screenshotName) {
		final Set<String> beforeHandles = driver.getWindowHandles();
		final String originalHandle = driver.getWindowHandle();

		clickByTextAndWait(linkTexts);

		final String targetHandle = wait.until(d -> {
			final Set<String> handles = d.getWindowHandles();
			if (handles.size() > beforeHandles.size()) {
				for (final String handle : handles) {
					if (!beforeHandles.contains(handle)) {
						return handle;
					}
				}
			}
			return originalHandle;
		});

		driver.switchTo().window(targetHandle);
		waitForUiToLoad();

		assertAnyTextVisible(headingTexts);
		assertLegalContentVisible();
		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (!targetHandle.equals(originalHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else {
			driver.navigate().back();
		}

		waitForUiToLoad();
		if (!appWindowHandle.equals(driver.getWindowHandle())) {
			driver.switchTo().window(appWindowHandle);
		}

		return finalUrl;
	}

	private void assertLegalContentVisible() {
		final WebElement body = wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));
		final String text = body.getText() == null ? "" : body.getText().trim();
		Assert.assertTrue("Expected visible legal text content.", text.length() > 200);
	}

	private void assertVisibleUserIdentity() {
		final String configuredEmail = readConfig("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);
		Assert.assertTrue("Expected user email to be visible.", hasVisibleText(configuredEmail));
	}

	private void assertAnyTextVisible(final String... texts) {
		wait.until(d -> hasVisibleText(texts));
	}

	private boolean hasVisibleText(final String... texts) {
		for (final String text : texts) {
			final List<WebElement> elements = driver
					.findElements(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]"));
			for (final WebElement element : elements) {
				if (isDisplayed(element)) {
					return true;
				}
			}
		}
		return false;
	}

	private void clickByTextAndWait(final String... texts) {
		wait.until(d -> findClickableElementByText(texts) != null);
		final WebElement element = findClickableElementByText(texts);
		Assert.assertNotNull("Clickable element not found for texts: " + String.join(", ", texts), element);

		try {
			element.click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiToLoad();
	}

	private WebElement findClickableElementByText(final String... texts) {
		final List<WebElement> candidates = new ArrayList<>();

		for (final String text : texts) {
			final String locator = "//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]";
			candidates.addAll(driver.findElements(By.xpath(locator)));
		}

		for (final WebElement candidate : candidates) {
			if (!isDisplayed(candidate)) {
				continue;
			}

			final WebElement clickable = toClickableCandidate(candidate);
			if (clickable != null && isDisplayed(clickable) && clickable.isEnabled()) {
				return clickable;
			}
		}

		return null;
	}

	private WebElement toClickableCandidate(final WebElement element) {
		final String tagName = element.getTagName();
		if ("button".equalsIgnoreCase(tagName) || "a".equalsIgnoreCase(tagName) || "input".equalsIgnoreCase(tagName)
				|| "summary".equalsIgnoreCase(tagName)) {
			return element;
		}

		final String role = element.getAttribute("role");
		if ("button".equalsIgnoreCase(role) || "link".equalsIgnoreCase(role)) {
			return element;
		}

		try {
			return element.findElement(By.xpath(
					"./ancestor-or-self::*[self::button or self::a or self::summary or @role='button' or @role='link' or @onclick][1]"));
		} catch (final NoSuchElementException ignored) {
			return null;
		}
	}

	private void waitForUiToLoad() {
		waitForDocumentReady();
		// Short stabilization delay after each click to reduce flakiness while dynamic UI finishes rendering.
		try {
			Thread.sleep(700);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void waitForDocumentReady() {
		try {
			wait.until(d -> {
				final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState;");
				return "complete".equals(state);
			});
		} catch (final TimeoutException ignored) {
			// Some SPA routes do not transition readyState; keep the test progressing.
		}
	}

	private boolean runStep(final String reportField, final ThrowingRunnable action) {
		try {
			action.run();
			finalReport.put(reportField, "PASS");
			return true;
		} catch (final Exception e) {
			finalReport.put(reportField, "FAIL - " + safeMessage(e));
			takeScreenshot(reportField.toLowerCase().replace(" ", "-") + "-failure");
			return false;
		}
	}

	private boolean runStep(final String reportField, final boolean prerequisiteOk, final ThrowingRunnable action) {
		if (!prerequisiteOk) {
			finalReport.put(reportField, "FAIL - prerequisite step failed.");
			return false;
		}

		return runStep(reportField, action);
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("\n=== SaleADS Mi Negocio Workflow Report ===\n");
		for (final Map.Entry<String, String> entry : finalReport.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}
		builder.append("- Términos y Condiciones URL: ").append(termsFinalUrl).append('\n');
		builder.append("- Política de Privacidad URL: ").append(privacyFinalUrl).append('\n');
		builder.append("- Evidencia (screenshots): ").append(evidenceDir.toAbsolutePath()).append('\n');
		return builder.toString();
	}

	private void takeScreenshot(final String name) {
		if (driver == null || evidenceDir == null) {
			return;
		}

		try {
			final String safeName = name.toLowerCase().replaceAll("[^a-z0-9\\-]+", "-");
			final Path target = evidenceDir.resolve(safeName + ".png");
			final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception ignored) {
			// Screenshot capture should not hide the root failure.
		}
	}

	private String readConfig(final String systemProperty, final String envVar) {
		return readConfig(systemProperty, envVar, "");
	}

	private String readConfig(final String systemProperty, final String envVar, final String defaultValue) {
		final String property = System.getProperty(systemProperty);
		if (!isBlank(property)) {
			return property;
		}

		final String env = System.getenv(envVar);
		if (!isBlank(env)) {
			return env;
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
		return "concat('" + value.replace("'", "',\"'\",'") + "')";
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private boolean isDisplayed(final WebElement element) {
		try {
			return element.isDisplayed();
		} catch (final StaleElementReferenceException ignored) {
			return false;
		}
	}

	private String safeMessage(final Exception exception) {
		final String message = exception.getMessage();
		if (isBlank(message)) {
			return exception.getClass().getSimpleName();
		}

		return message.lines().limit(1).collect(Collectors.joining(" "));
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
