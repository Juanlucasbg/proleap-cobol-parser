package io.proleap.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * SaleADS Mi Negocio end-to-end workflow:
 * - Login with Google
 * - Validate Mi Negocio navigation and modal
 * - Validate account sections and legal links
 *
 * Enable with RUN_SALEADS_E2E=true and provide SALEADS_LOGIN_URL.
 */
public class SaleadsMiNegocioFullTest {

	private static final String E2E_FLAG = "RUN_SALEADS_E2E";
	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String SCREENSHOT_DIR_ENV = "SALEADS_SCREENSHOT_DIR";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;

	@Before
	public void setUp() {
		final boolean enabled = Boolean.parseBoolean(System.getenv().getOrDefault(E2E_FLAG, "false"));
		Assume.assumeTrue("Skipping SaleADS E2E test. Set RUN_SALEADS_E2E=true to execute.", enabled);

		final String loginUrl = System.getenv(LOGIN_URL_ENV);
		Assume.assumeTrue("Skipping SaleADS E2E test. SALEADS_LOGIN_URL must be provided.",
				loginUrl != null && !loginUrl.trim().isEmpty());

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(System.getenv().getOrDefault(HEADLESS_ENV, "false"))) {
			options.addArguments("--headless=new");
			options.addArguments("--window-size=1920,1080");
		}
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		driver.manage().window().maximize();
		driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
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
	public void saleadsMiNegocioFullWorkflow() {
		final Map<String, Boolean> report = new LinkedHashMap<String, Boolean>();
		final Map<String, String> evidenceUrls = new LinkedHashMap<String, String>();
		final Map<String, String> errors = new LinkedHashMap<String, String>();

		final boolean loginOk = runStep("Login", errors, new StepAction() {
			@Override
			public void run() {
				stepLoginWithGoogle();
			}
		});
		report.put("Login", Boolean.valueOf(loginOk));

		final boolean menuOk = runStep("Mi Negocio menu", errors, new StepAction() {
			@Override
			public void run() {
				stepOpenMiNegocioMenu();
			}
		});
		report.put("Mi Negocio menu", Boolean.valueOf(menuOk));

		final boolean modalOk = runStep("Agregar Negocio modal", errors, new StepAction() {
			@Override
			public void run() {
				stepValidateAgregarNegocioModal();
			}
		});
		report.put("Agregar Negocio modal", Boolean.valueOf(modalOk));

		final boolean adminOk = runStep("Administrar Negocios view", errors, new StepAction() {
			@Override
			public void run() {
				stepOpenAdministrarNegocios();
			}
		});
		report.put("Administrar Negocios view", Boolean.valueOf(adminOk));

		final boolean infoOk = runStep("Información General", errors, new StepAction() {
			@Override
			public void run() {
				stepValidateInformacionGeneral();
			}
		});
		report.put("Información General", Boolean.valueOf(infoOk));

		final boolean cuentaOk = runStep("Detalles de la Cuenta", errors, new StepAction() {
			@Override
			public void run() {
				stepValidateDetallesCuenta();
			}
		});
		report.put("Detalles de la Cuenta", Boolean.valueOf(cuentaOk));

		final boolean negociosOk = runStep("Tus Negocios", errors, new StepAction() {
			@Override
			public void run() {
				stepValidateTusNegocios();
			}
		});
		report.put("Tus Negocios", Boolean.valueOf(negociosOk));

		final boolean terminosOk = runStep("Términos y Condiciones", errors, new StepAction() {
			@Override
			public void run() {
				final String finalUrl = stepOpenLegalLink("Términos y Condiciones");
				evidenceUrls.put("Términos y Condiciones URL", finalUrl);
			}
		});
		report.put("Términos y Condiciones", Boolean.valueOf(terminosOk));

		final boolean privacidadOk = runStep("Política de Privacidad", errors, new StepAction() {
			@Override
			public void run() {
				final String finalUrl = stepOpenLegalLink("Política de Privacidad");
				evidenceUrls.put("Política de Privacidad URL", finalUrl);
			}
		});
		report.put("Política de Privacidad", Boolean.valueOf(privacidadOk));

		final String summary = buildFinalReport(report, errors, evidenceUrls);
		System.out.println(summary);

		final List<String> failedSteps = new ArrayList<String>();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue().booleanValue()) {
				failedSteps.add(entry.getKey());
			}
		}

		Assert.assertTrue("SaleADS Mi Negocio workflow failed.\n" + summary, failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() {
		clickByAnyVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiToLoad();
		handleGoogleAccountSelectionIfPresent();

		assertAnyVisibleText("Negocio");
		assertSidebarVisible();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		assertSidebarVisible();

		if (!isAnyVisibleText("Agregar Negocio", "Administrar Negocios")) {
			clickByAnyVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByAnyVisibleText("Agregar Negocio");
		waitForUiToLoad();

		assertAnyVisibleText("Crear Nuevo Negocio");
		assertAnyVisibleText("Nombre del Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");
		assertAnyVisibleText("Cancelar");
		assertAnyVisibleText("Crear Negocio");

		takeScreenshot("03-agregar-negocio-modal");

		final WebElement nombreInput = findVisibleElement(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio' or contains(@name,'negocio')]"));
		if (nombreInput != null) {
			nombreInput.clear();
			nombreInput.sendKeys("Negocio Prueba Automatización");
		}

		clickByAnyVisibleText("Cancelar");
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegocios() {
		if (!isAnyVisibleText("Administrar Negocios")) {
			clickByAnyVisibleText("Mi Negocio");
			waitForUiToLoad();
		}

		clickByAnyVisibleText("Administrar Negocios");
		waitForUiToLoad();

		assertAnyVisibleText("Información General", "Informacion General");
		assertAnyVisibleText("Detalles de la Cuenta", "Detalles de la cuenta");
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Sección Legal", "Seccion Legal");
		takeScreenshot("04-administrar-negocios-page");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisibleText("Información General", "Informacion General");
		assertAnyVisibleEmail();
		assertAnyVisibleText("BUSINESS PLAN");
		assertAnyVisibleText("Cambiar Plan");

		// Heuristic: profile section should expose at least one non-trivial display name text.
		final boolean hasLikelyDisplayName = !driver.findElements(By.xpath(
				"//*[normalize-space(text())!='' and not(contains(text(),'@')) and string-length(normalize-space(text())) > 4 and not(contains(.,'BUSINESS PLAN')) and not(contains(.,'Cambiar Plan'))]"))
				.isEmpty();
		Assert.assertTrue("Expected a visible user name in Información General.", hasLikelyDisplayName);
	}

	private void stepValidateDetallesCuenta() {
		assertAnyVisibleText("Detalles de la Cuenta", "Detalles de la cuenta");
		assertAnyVisibleText("Cuenta creada");
		assertAnyVisibleText("Estado activo");
		assertAnyVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertAnyVisibleText("Tus Negocios");
		assertAnyVisibleText("Agregar Negocio");
		assertAnyVisibleText("Tienes 2 de 3 negocios");

		final boolean hasBusinessList = !driver.findElements(By.xpath(
				"//section//*[self::ul or self::table or @role='list' or contains(@class,'list') or contains(@class,'card')][.//text()[normalize-space()!='']]"))
				.isEmpty();
		Assert.assertTrue("Expected business list to be visible in Tus Negocios.", hasBusinessList);
	}

	private String stepOpenLegalLink(final String legalText) {
		final String appWindow = driver.getWindowHandle();
		final Set<String> before = driver.getWindowHandles();

		clickByAnyVisibleText(legalText);
		waitForUiToLoad();

		final String destinationHandle = waitForNewWindowHandle(before, Duration.ofSeconds(8));
		final boolean openedNewTab = destinationHandle != null;

		if (openedNewTab) {
			driver.switchTo().window(destinationHandle);
			waitForUiToLoad();
		}

		assertAnyVisibleText(legalText);
		final boolean hasLegalContent = !driver
				.findElements(By.xpath("//p[string-length(normalize-space()) > 40] | //article | //section"))
				.isEmpty();
		Assert.assertTrue("Expected legal content text to be visible for " + legalText + ".", hasLegalContent);

		takeScreenshot("legal-" + slug(legalText));
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private void handleGoogleAccountSelectionIfPresent() {
		final Set<String> handles = driver.getWindowHandles();
		if (handles.size() > 1) {
			final String current = driver.getWindowHandle();
			for (final String handle : handles) {
				if (!handle.equals(current)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		try {
			clickByAnyVisibleText(GOOGLE_ACCOUNT_EMAIL);
			waitForUiToLoad();
		} catch (final AssertionError ignored) {
			// If account chooser is not shown we continue, as session may already be authenticated.
		}

		// Try to return to app window if multiple windows remain.
		final Set<String> after = driver.getWindowHandles();
		if (after.size() > 1) {
			for (final String handle : after) {
				driver.switchTo().window(handle);
				if (isAnyVisibleText("Negocio", "Mi Negocio", "Dashboard")) {
					return;
				}
			}
		}
	}

	private void assertSidebarVisible() {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//aside | //nav[contains(@class,'sidebar')] | //*[@role='navigation']")));
		} catch (final TimeoutException e) {
			throw new AssertionError("Left sidebar navigation is not visible.", e);
		}
	}

	private void assertAnyVisibleEmail() {
		final boolean hasEmail = !driver.findElements(By.xpath(
				"//*[contains(normalize-space(text()),'@') and contains(normalize-space(text()),'.')]")).isEmpty();
		Assert.assertTrue("Expected user email to be visible.", hasEmail);
	}

	private void assertAnyVisibleText(final String... texts) {
		for (final String text : texts) {
			final String literal = xpathLiteral(text);
			final List<By> candidates = new ArrayList<By>();
			candidates.add(By.xpath("//*[normalize-space()=" + literal + "]"));
			candidates.add(By.xpath("//*[contains(normalize-space(), " + literal + ")]"));

			for (final By candidate : candidates) {
				try {
					wait.until(ExpectedConditions.visibilityOfElementLocated(candidate));
					return;
				} catch (final TimeoutException ignored) {
				}
			}
		}

		throw new AssertionError("Expected visible text not found: " + String.join(" | ", texts));
	}

	private boolean isAnyVisibleText(final String... texts) {
		for (final String text : texts) {
			final String literal = xpathLiteral(text);
			final List<WebElement> exact = driver.findElements(By.xpath("//*[normalize-space()=" + literal + "]"));
			for (final WebElement element : exact) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void clickByAnyVisibleText(final String... texts) {
		for (final String text : texts) {
			final WebElement element = findClickableByText(text);
			if (element != null) {
				clickAndWait(element);
				return;
			}
		}

		throw new AssertionError("Could not click any visible element with text: " + String.join(" | ", texts));
	}

	private WebElement findClickableByText(final String text) {
		final String literal = xpathLiteral(text);

		final List<By> locators = new ArrayList<By>();
		locators.add(By.xpath("//button[normalize-space()=" + literal + "]"));
		locators.add(By.xpath("//a[normalize-space()=" + literal + "]"));
		locators.add(By.xpath("//*[@role='button' and normalize-space()=" + literal + "]"));
		locators.add(By.xpath(
				"//*[self::button or self::a or @role='button'][contains(normalize-space(), " + literal + ")]"));
		locators.add(By.xpath("//*[normalize-space()=" + literal + "]"));
		locators.add(By.xpath("//*[contains(normalize-space(), " + literal + ")]"));

		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}

		return null;
	}

	private WebElement findVisibleElement(final By by) {
		try {
			return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
		} catch (final TimeoutException e) {
			return null;
		}
	}

	private void clickAndWait(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(new ExpectedCondition<Boolean>() {
			@Override
			public Boolean apply(final WebDriver d) {
				final Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(state);
			}
		});

		try {
			Thread.sleep(400L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String waitForNewWindowHandle(final Set<String> previousHandles, final Duration timeout) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			final Set<String> currentHandles = driver.getWindowHandles();
			for (final String handle : currentHandles) {
				if (!previousHandles.contains(handle)) {
					return handle;
				}
			}
			try {
				Thread.sleep(200L);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	private void takeScreenshot(final String checkpointName) {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final String outputDir = System.getenv().getOrDefault(SCREENSHOT_DIR_ENV,
				"target/surefire-reports/saleads-screenshots");
		final Path dir = Path.of(outputDir);
		final Path destination = dir.resolve(timestamp + "-" + checkpointName + ".png");
		try {
			Files.createDirectories(dir);
			Files.copy(screenshot.toPath(), destination);
		} catch (final IOException e) {
			throw new RuntimeException("Failed to write screenshot to " + destination, e);
		}
	}

	private boolean runStep(final String stepName, final Map<String, String> errors, final StepAction action) {
		try {
			action.run();
			return true;
		} catch (final Throwable t) {
			errors.put(stepName, t.getMessage() == null ? t.getClass().getName() : t.getMessage());
			return false;
		}
	}

	private String buildFinalReport(final Map<String, Boolean> report, final Map<String, String> errors,
			final Map<String, String> evidenceUrls) {
		final StringBuilder builder = new StringBuilder();
		builder.append("\n=== SaleADS Mi Negocio Workflow Report ===\n");

		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ")
					.append(entry.getValue().booleanValue() ? "PASS" : "FAIL").append("\n");
			if (!entry.getValue().booleanValue() && errors.containsKey(entry.getKey())) {
				builder.append("  reason: ").append(errors.get(entry.getKey())).append("\n");
			}
		}

		if (!evidenceUrls.isEmpty()) {
			builder.append("\nEvidence URLs:\n");
			for (final Map.Entry<String, String> entry : evidenceUrls.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
			}
		}

		builder.append("=========================================\n");
		return builder.toString();
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}
		final String[] parts = text.split("'");
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

	private String slug(final String text) {
		return text.toLowerCase().replace(" ", "-").replace("á", "a").replace("é", "e").replace("í", "i")
				.replace("ó", "o").replace("ú", "u").replace("ñ", "n").replaceAll("[^a-z0-9-]", "");
	}

	private interface StepAction {
		void run();
	}
}
