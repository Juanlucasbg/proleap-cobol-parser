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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

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
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, StepResult> results = new LinkedHashMap<>();
	private String applicationWindowHandle;

	@Before
	public void setUp() throws IOException {
		driver = buildDriver();
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDir = Path.of("target", "saleads-evidence", LocalDateTime.now().format(TS));
		Files.createDirectories(evidenceDir);

		String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl.trim());
			waitForUiSettled();
		}
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
		boolean canContinue = runStep("Login", this::stepLoginWithGoogle);
		canContinue = canContinue && runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		canContinue = canContinue && runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		canContinue = canContinue && runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		canContinue = canContinue && runStep("Informacion General", this::stepValidateInformacionGeneral);
		canContinue = canContinue && runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		canContinue = canContinue && runStep("Tus Negocios", this::stepValidateTusNegocios);
		canContinue = canContinue && runStep("Terminos y Condiciones", this::stepValidateTerminosYCondiciones);
		canContinue = canContinue && runStep("Politica de Privacidad", this::stepValidatePoliticaDePrivacidad);

		if (!canContinue) {
			Assert.fail("One or more workflow steps failed. Check target/saleads-evidence report and screenshots.");
		}
	}

	private WebDriver buildDriver() {
		String browser = System.getenv().getOrDefault("SALEADS_BROWSER", "chrome").trim().toLowerCase();
		boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "false"));

		if ("firefox".equals(browser)) {
			FirefoxOptions options = new FirefoxOptions();
			if (headless) {
				options.addArguments("-headless");
			}
			return new FirefoxDriver(options);
		}

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-notifications");
		options.addArguments("--disable-popup-blocking");
		if (headless) {
			options.addArguments("--headless=new");
		}
		return new ChromeDriver(options);
	}

	private String stepLoginWithGoogle() {
		if ("about:blank".equalsIgnoreCase(driver.getCurrentUrl())) {
			Assert.fail(
					"The browser opened on about:blank. Set SALEADS_LOGIN_URL for the current SaleADS environment login page.");
		}

		Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickByAnyText("Sign in with Google", "Iniciar sesion con Google", "Iniciar sesión con Google",
				"Continuar con Google", "Google");
		waitForUiSettled();

		switchToGoogleWindowIfNeeded(handlesBeforeClick);
		chooseGoogleAccountIfSelectorVisible();
		switchBackToApplicationWindow();

		waitForAnyVisibleText(List.of("Negocio", "Dashboard", "Inicio"), Duration.ofSeconds(45));
		WebElement sidebar = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside|//nav")));
		assertTrue("Left sidebar navigation should be visible", sidebar.isDisplayed());
		takeScreenshot("01-dashboard-loaded");
		return "Dashboard loaded and sidebar visible";
	}

	private String stepOpenMiNegocioMenu() {
		clickIfPresentByAnyText("Negocio");
		clickByAnyText("Mi Negocio");
		waitForUiSettled();

		assertTrue("Agregar Negocio should be visible", isVisibleText("Agregar Negocio"));
		assertTrue("Administrar Negocios should be visible", isVisibleText("Administrar Negocios"));
		takeScreenshot("02-mi-negocio-menu-expanded");
		return "Mi Negocio expanded with expected options";
	}

	private String stepValidateAgregarNegocioModal() {
		clickByAnyText("Agregar Negocio");
		waitForUiSettled();

		assertTrue("Modal title should be visible", waitForVisibleText("Crear Nuevo Negocio", Duration.ofSeconds(20)));
		assertTrue("Input field 'Nombre del Negocio' should exist", isVisible(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')] | //input[contains(@placeholder, 'Nombre del Negocio')]")));
		assertTrue("Usage text should be visible", isVisibleText("Tienes 2 de 3 negocios"));
		assertTrue("Cancelar button should be visible", isVisibleText("Cancelar"));
		assertTrue("Crear Negocio button should be visible", isVisibleText("Crear Negocio"));

		WebElement businessNameInput = firstVisible(By.xpath(
				"//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1] | //input[contains(@placeholder, 'Nombre del Negocio')]"));
		if (businessNameInput != null) {
			businessNameInput.click();
			businessNameInput.clear();
			businessNameInput.sendKeys("Negocio Prueba Automatizacion");
		}
		takeScreenshot("03-agregar-negocio-modal");

		clickByAnyText("Cancelar");
		waitForUiSettled();
		return "Agregar Negocio modal validated and closed";
	}

	private String stepOpenAdministrarNegocios() {
		if (!isVisibleText("Administrar Negocios")) {
			clickIfPresentByAnyText("Negocio");
			clickByAnyText("Mi Negocio");
			waitForUiSettled();
		}

		clickByAnyText("Administrar Negocios");
		waitForUiSettled();

		assertTrue("Informacion General section should exist", waitForAnyVisibleText(
				List.of("Informacion General", "Información General"), Duration.ofSeconds(30)));
		assertTrue("Detalles de la Cuenta section should exist", waitForVisibleText("Detalles de la Cuenta",
				Duration.ofSeconds(30)));
		assertTrue("Tus Negocios section should exist", waitForVisibleText("Tus Negocios", Duration.ofSeconds(30)));
		assertTrue("Seccion Legal section should exist",
				waitForAnyVisibleText(List.of("Seccion Legal", "Sección Legal"), Duration.ofSeconds(30)));

		takeScreenshot("04-administrar-negocios-view");
		return "Administrar Negocios page loaded";
	}

	private String stepValidateInformacionGeneral() {
		assertTrue("User name should be visible", hasVisibleNonEmptyTextNear("Nombre"));
		assertTrue("User email should be visible", hasEmailLikeText());
		assertTrue("BUSINESS PLAN should be visible", isVisibleText("BUSINESS PLAN"));
		assertTrue("Cambiar Plan button should be visible", isVisibleText("Cambiar Plan"));
		return "Informacion General validated";
	}

	private String stepValidateDetallesCuenta() {
		assertTrue("'Cuenta creada' should be visible", waitForVisibleText("Cuenta creada", Duration.ofSeconds(15)));
		assertTrue("'Estado activo' should be visible", waitForVisibleText("Estado activo", Duration.ofSeconds(15)));
		assertTrue("'Idioma seleccionado' should be visible",
				waitForVisibleText("Idioma seleccionado", Duration.ofSeconds(15)));
		return "Detalles de la Cuenta validated";
	}

	private String stepValidateTusNegocios() {
		assertTrue("Business list should be visible", isVisible(By.xpath(
				"//*[contains(normalize-space(.), 'Tus Negocios')]/following::*[self::ul or self::table or self::div][1]")));
		assertTrue("Agregar Negocio button should exist", isVisibleText("Agregar Negocio"));
		assertTrue("Tienes 2 de 3 negocios should be visible", isVisibleText("Tienes 2 de 3 negocios"));
		return "Tus Negocios validated";
	}

	private String stepValidateTerminosYCondiciones() {
		String url = openLegalLinkAndValidate("Términos y Condiciones", List.of("Términos y Condiciones"),
				"08-terminos-condiciones");
		return "Final URL: " + url;
	}

	private String stepValidatePoliticaDePrivacidad() {
		String url = openLegalLinkAndValidate("Política de Privacidad", List.of("Política de Privacidad"),
				"09-politica-privacidad");
		return "Final URL: " + url;
	}

	private String openLegalLinkAndValidate(String linkText, List<String> headingCandidates, String screenshotName) {
		String previousWindow = driver.getWindowHandle();
		Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByAnyText(linkText);
		waitForUiSettled();

		String legalWindow = waitForNewWindowHandle(handlesBeforeClick, Duration.ofSeconds(15));
		if (legalWindow != null) {
			driver.switchTo().window(legalWindow);
			waitForUiSettled();
		}

		boolean headingVisible = waitForAnyVisibleText(headingCandidates, Duration.ofSeconds(20));
		assertTrue(linkText + " heading should be visible", headingVisible);
		assertTrue("Legal content text should be visible", hasParagraphText());

		String finalUrl = safeCurrentUrl();
		takeScreenshot(screenshotName);

		if (legalWindow != null) {
			driver.close();
			driver.switchTo().window(previousWindow);
			waitForUiSettled();
		} else {
			driver.navigate().back();
			waitForUiSettled();
		}
		return finalUrl;
	}

	private void switchToGoogleWindowIfNeeded(Set<String> handlesBeforeClick) {
		String newHandle = waitForNewWindowHandle(handlesBeforeClick, Duration.ofSeconds(20));
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiSettled();
			return;
		}

		if (safeCurrentUrl().contains("accounts.google.com")) {
			waitForUiSettled();
		}
	}

	private void chooseGoogleAccountIfSelectorVisible() {
		if (!safeCurrentUrl().contains("google.")) {
			return;
		}

		By accountBy = By.xpath("//*[contains(normalize-space(.), '" + GOOGLE_ACCOUNT_EMAIL + "')]");
		try {
			WebElement accountItem = new WebDriverWait(driver, Duration.ofSeconds(20))
					.until(ExpectedConditions.elementToBeClickable(accountBy));
			accountItem.click();
			waitForUiSettled();
		} catch (TimeoutException ignored) {
			// Account picker may not appear if a session is already active.
		}
	}

	private void switchBackToApplicationWindow() {
		wait.until(driver -> {
			for (String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				if (!safeCurrentUrl().contains("google.")) {
					applicationWindowHandle = handle;
					return true;
				}
			}
			return false;
		});
		driver.switchTo().window(applicationWindowHandle);
		waitForUiSettled();
	}

	private boolean runStep(String stepName, CheckedStep step) {
		StepResult result = new StepResult();
		result.name = stepName;
		try {
			result.details = step.run();
			result.passed = true;
		} catch (AssertionError | Exception ex) {
			result.passed = false;
			result.details = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			try {
				result.screenshot = takeScreenshot("failed-" + sanitize(stepName));
			} catch (Exception ignored) {
				// Best-effort screenshot for failures.
			}
		}
		results.put(stepName, result);
		return result.passed;
	}

	private void writeFinalReport() throws IOException {
		if (results.isEmpty()) {
			return;
		}

		StringBuilder content = new StringBuilder();
		content.append("SaleADS Mi Negocio Full Workflow Report\n");
		content.append("Generated at: ").append(LocalDateTime.now()).append('\n');
		content.append("Evidence dir: ").append(evidenceDir.toAbsolutePath()).append("\n\n");
		for (Map.Entry<String, StepResult> entry : results.entrySet()) {
			StepResult result = entry.getValue();
			content.append("- ").append(result.name).append(": ").append(result.passed ? "PASS" : "FAIL").append('\n');
			if (result.details != null) {
				content.append("  Details: ").append(result.details).append('\n');
			}
			if (result.screenshot != null) {
				content.append("  Screenshot: ").append(result.screenshot).append('\n');
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), content.toString());
	}

	private String takeScreenshot(String name) {
		File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Path target = evidenceDir.resolve(sanitize(name) + ".png");
		try {
			Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			throw new IllegalStateException("Could not save screenshot: " + target, ex);
		}
		return target.toString();
	}

	private void clickByAnyText(String... texts) {
		for (String text : texts) {
			WebElement candidate = firstClickableByText(text);
			if (candidate != null) {
				candidate.click();
				waitForUiSettled();
				return;
			}
		}
		Assert.fail("Could not find clickable element with texts: " + String.join(", ", texts));
	}

	private boolean clickIfPresentByAnyText(String... texts) {
		for (String text : texts) {
			WebElement candidate = firstClickableByText(text);
			if (candidate != null) {
				candidate.click();
				waitForUiSettled();
				return true;
			}
		}
		return false;
	}

	private WebElement firstClickableByText(String text) {
		String xpath = "//*[self::button or self::a or self::span or self::div or self::li]"
				+ "[contains(normalize-space(.),\"" + text + "\")]";
		List<WebElement> matches = driver.findElements(By.xpath(xpath));
		for (WebElement match : matches) {
			try {
				if (match.isDisplayed() && match.isEnabled()) {
					return match;
				}
			} catch (NoSuchElementException ignored) {
				// Ignore stale elements from dynamic rendering.
			}
		}
		return null;
	}

	private boolean waitForVisibleText(String text, Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[contains(normalize-space(.),\"" + text + "\")]")));
			return true;
		} catch (TimeoutException ex) {
			return false;
		}
	}

	private boolean waitForAnyVisibleText(List<String> candidates, Duration timeout) {
		long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			for (String candidate : candidates) {
				if (isVisibleText(candidate)) {
					return true;
				}
			}
			sleep(500);
		}
		return false;
	}

	private boolean isVisibleText(String text) {
		return isVisible(By.xpath("//*[contains(normalize-space(.),\"" + text + "\")]"));
	}

	private boolean isVisible(By by) {
		try {
			WebElement e = firstVisible(by);
			return e != null && e.isDisplayed();
		} catch (Exception ex) {
			return false;
		}
	}

	private WebElement firstVisible(By by) {
		List<WebElement> elements = driver.findElements(by);
		for (WebElement element : elements) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		return null;
	}

	private boolean hasVisibleNonEmptyTextNear(String anchor) {
		By locator = By.xpath("//*[contains(normalize-space(.), '" + anchor + "')]/following::*[1]");
		WebElement node = firstVisible(locator);
		return node != null && !node.getText().isBlank();
	}

	private boolean hasEmailLikeText() {
		return isVisible(By.xpath("//*[contains(normalize-space(.), '@')]"));
	}

	private boolean hasParagraphText() {
		List<WebElement> paragraphs = driver.findElements(By.xpath("//p[string-length(normalize-space(.)) > 40]"));
		for (WebElement paragraph : paragraphs) {
			if (paragraph.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private String waitForNewWindowHandle(Set<String> originalHandles, Duration timeout) {
		long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			Set<String> handles = driver.getWindowHandles();
			if (handles.size() > originalHandles.size()) {
				for (String handle : handles) {
					if (!originalHandles.contains(handle)) {
						return handle;
					}
				}
			}
			sleep(300);
		}
		return null;
	}

	private void waitForUiSettled() {
		waitUntil("document.readyState is complete", () -> {
			Object state = ((JavascriptExecutor) driver).executeScript("return document.readyState");
			return "complete".equals(state);
		}, Duration.ofSeconds(20));

		waitUntil("loading overlays disappear", () -> {
			List<WebElement> overlays = driver.findElements(By.xpath(
					"//*[contains(@class,'loading') or contains(@class,'spinner') or contains(@class,'overlay') or contains(@aria-label, 'loading')]"));
			for (WebElement overlay : overlays) {
				if (overlay.isDisplayed()) {
					return false;
				}
			}
			return true;
		}, Duration.ofSeconds(8));
	}

	private void waitUntil(String description, BooleanSupplier condition, Duration timeout) {
		WebDriverWait localWait = new WebDriverWait(driver, timeout);
		localWait.until((ExpectedCondition<Boolean>) ignored -> condition.getAsBoolean());
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (Exception ex) {
			return "";
		}
	}

	private String sanitize(String name) {
		return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface CheckedStep {
		String run() throws Exception;
	}

	private static class StepResult {
		private String name;
		private boolean passed;
		private String details;
		private String screenshot;
	}
}
