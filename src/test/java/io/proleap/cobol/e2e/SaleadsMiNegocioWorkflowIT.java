package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.Assert;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * This test is environment-agnostic. Configure at runtime:
 * -Dsaleads.login.url=https://{current-env-login-page}
 * -Dsaleads.google.account=juanlucasbarbiergarzon@gmail.com
 * -Dsaleads.headless=false
 * -Dselenium.remote.url=http://{grid-host}:4444/wd/hub (optional)
 *
 * Evidence and final report are written to: target/saleads-evidence
 */
public class SaleadsMiNegocioWorkflowIT {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String STEP_INFORMACION_GENERAL = "Información General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Política de Privacidad";

	private final Map<String, String> stepResults = new LinkedHashMap<>();
	private final Map<String, String> metadata = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String applicationHandle;

	@Test
	public void validateMiNegocioWorkflow() throws Exception {
		initializeReport();

		try {
			setUpDriver();

			boolean loginOk = runStepLogin();
			boolean miNegocioMenuOk = loginOk && runStepMiNegocioMenu();
			boolean agregarModalOk = miNegocioMenuOk && runStepAgregarNegocioModal();
			boolean administrarOk = (agregarModalOk || miNegocioMenuOk) && runStepAdministrarNegocios();
			boolean infoGeneralOk = administrarOk && runStepInformacionGeneral();
			boolean detallesOk = administrarOk && runStepDetallesCuenta();
			boolean negociosOk = administrarOk && runStepTusNegocios();
			boolean terminosOk = administrarOk && runStepTerminosYCondiciones();
			boolean privacidadOk = administrarOk && runStepPoliticaPrivacidad();

			metadata.put("Execution summary",
					"login=" + loginOk + ", menu=" + miNegocioMenuOk + ", modal=" + agregarModalOk + ", administrar="
							+ administrarOk + ", infoGeneral=" + infoGeneralOk + ", detalles=" + detallesOk + ", negocios="
							+ negociosOk + ", terminos=" + terminosOk + ", privacidad=" + privacidadOk);
		} finally {
			writeFinalReport();

			if (driver != null) {
				driver.quit();
			}
		}

		assertAllStepsPassed();
	}

	private void initializeReport() {
		stepResults.put(STEP_LOGIN, "FAIL");
		stepResults.put(STEP_MI_NEGOCIO_MENU, "FAIL");
		stepResults.put(STEP_AGREGAR_NEGOCIO_MODAL, "FAIL");
		stepResults.put(STEP_ADMINISTRAR_NEGOCIOS, "FAIL");
		stepResults.put(STEP_INFORMACION_GENERAL, "FAIL");
		stepResults.put(STEP_DETALLES_CUENTA, "FAIL");
		stepResults.put(STEP_TUS_NEGOCIOS, "FAIL");
		stepResults.put(STEP_TERMINOS, "FAIL");
		stepResults.put(STEP_PRIVACIDAD, "FAIL");
	}

	private void setUpDriver() throws Exception {
		evidenceDir = Paths.get("target", "saleads-evidence");
		Files.createDirectories(evidenceDir);

		String loginUrl = getConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		String remoteUrl = getConfig("selenium.remote.url", "SELENIUM_REMOTE_URL");
		boolean headless = Boolean.parseBoolean(Optional.ofNullable(System.getProperty("saleads.headless"))
				.orElseGet(() -> Optional.ofNullable(System.getenv("SALEADS_HEADLESS")).orElse("false")));

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		if (headless) {
			options.addArguments("--headless=new");
		}

		if (!isBlank(remoteUrl)) {
			driver = new RemoteWebDriver(new java.net.URI(remoteUrl).toURL(), options);
		} else {
			driver = new ChromeDriver(options);
		}

		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		if (!isBlank(loginUrl)) {
			driver.get(loginUrl);
			waitForUiToSettle();
		}

		applicationHandle = driver.getWindowHandle();
	}

	private boolean runStepLogin() {
		try {
			clickByAnyText("Sign in with Google", "Continue with Google", "Iniciar sesión con Google",
					"Continuar con Google", "Login with Google", "Google");
			waitForUiToSettle();
			selectGoogleAccountIfVisible();

			assertAnyVisibleText("Negocio", "Mi Negocio");
			markStepPass(STEP_LOGIN);
			captureScreenshot("01-dashboard-loaded");
			return true;
		} catch (Throwable e) {
			recordStepFailure(STEP_LOGIN, e, "01-login-failure");
			return false;
		}
	}

	private boolean runStepMiNegocioMenu() {
		try {
			clickByAnyText("Negocio");
			clickByAnyText("Mi Negocio");

			assertAnyVisibleText("Agregar Negocio");
			assertAnyVisibleText("Administrar Negocios");

			markStepPass(STEP_MI_NEGOCIO_MENU);
			captureScreenshot("02-mi-negocio-menu-expanded");
			return true;
		} catch (Throwable e) {
			recordStepFailure(STEP_MI_NEGOCIO_MENU, e, "02-mi-negocio-menu-failure");
			return false;
		}
	}

	private boolean runStepAgregarNegocioModal() {
		try {
			clickByAnyText("Agregar Negocio");
			assertAnyVisibleText("Crear Nuevo Negocio");
			assertAnyVisibleText("Nombre del Negocio");
			assertAnyVisibleText("Tienes 2 de 3 negocios");
			assertAnyVisibleText("Cancelar");
			assertAnyVisibleText("Crear Negocio");

			tryTypeInField("Nombre del Negocio", "Negocio Prueba Automatización");
			clickByAnyText("Cancelar");
			waitForUiToSettle();

			markStepPass(STEP_AGREGAR_NEGOCIO_MODAL);
			captureScreenshot("03-agregar-negocio-modal");
			return true;
		} catch (Throwable e) {
			recordStepFailure(STEP_AGREGAR_NEGOCIO_MODAL, e, "03-agregar-negocio-modal-failure");
			return false;
		}
	}

	private boolean runStepAdministrarNegocios() {
		try {
			ensureMiNegocioSubmenuVisible();
			clickByAnyText("Administrar Negocios");

			assertAnyVisibleText("Información General");
			assertAnyVisibleText("Detalles de la Cuenta");
			assertAnyVisibleText("Tus Negocios");
			assertAnyVisibleText("Sección Legal");

			markStepPass(STEP_ADMINISTRAR_NEGOCIOS);
			captureFullPageScreenshot("04-administrar-negocios-page");
			return true;
		} catch (Throwable e) {
			recordStepFailure(STEP_ADMINISTRAR_NEGOCIOS, e, "04-administrar-negocios-failure");
			return false;
		}
	}

	private boolean runStepInformacionGeneral() {
		try {
			assertAnyVisibleText("Información General");
			assertPageContainsEmail();
			assertAnyVisibleText("BUSINESS PLAN");
			assertAnyVisibleText("Cambiar Plan");
			assertLikelyUserNameIsVisible();

			markStepPass(STEP_INFORMACION_GENERAL);
			return true;
		} catch (Throwable e) {
			recordStepFailure(STEP_INFORMACION_GENERAL, e, "05-informacion-general-failure");
			return false;
		}
	}

	private boolean runStepDetallesCuenta() {
		try {
			assertAnyVisibleText("Cuenta creada");
			assertAnyVisibleText("Estado activo");
			assertAnyVisibleText("Idioma seleccionado");

			markStepPass(STEP_DETALLES_CUENTA);
			return true;
		} catch (Throwable e) {
			recordStepFailure(STEP_DETALLES_CUENTA, e, "06-detalles-cuenta-failure");
			return false;
		}
	}

	private boolean runStepTusNegocios() {
		try {
			assertAnyVisibleText("Tus Negocios");
			assertAnyVisibleText("Agregar Negocio");
			assertAnyVisibleText("Tienes 2 de 3 negocios");
			assertBusinessListIsVisible();

			markStepPass(STEP_TUS_NEGOCIOS);
			return true;
		} catch (Throwable e) {
			recordStepFailure(STEP_TUS_NEGOCIOS, e, "07-tus-negocios-failure");
			return false;
		}
	}

	private boolean runStepTerminosYCondiciones() {
		try {
			validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos-y-condiciones",
					"terminos_url");
			markStepPass(STEP_TERMINOS);
			return true;
		} catch (Throwable e) {
			recordStepFailure(STEP_TERMINOS, e, "08-terminos-y-condiciones-failure");
			return false;
		}
	}

	private boolean runStepPoliticaPrivacidad() {
		try {
			validateLegalLink("Política de Privacidad", "Política de Privacidad", "09-politica-privacidad",
					"privacidad_url");
			markStepPass(STEP_PRIVACIDAD);
			return true;
		} catch (Throwable e) {
			recordStepFailure(STEP_PRIVACIDAD, e, "09-politica-privacidad-failure");
			return false;
		}
	}

	private void validateLegalLink(final String linkText, final String headingText, final String screenshotName,
			final String urlMetadataKey) throws IOException {
		final String originHandle = driver.getWindowHandle();
		final Set<String> previousHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String previousUrl = driver.getCurrentUrl();

		clickByAnyText(linkText);
		waitForUiToSettle();

		wait.until((ExpectedCondition<Boolean>) wd -> {
			Set<String> currentHandles = wd.getWindowHandles();
			if (currentHandles.size() > previousHandles.size()) {
				return true;
			}

			String currentUrl = wd.getCurrentUrl();
			return !currentUrl.equals(previousUrl);
		});

		boolean switchedToNewTab = false;
		for (String handle : driver.getWindowHandles()) {
			if (!previousHandles.contains(handle)) {
				driver.switchTo().window(handle);
				switchedToNewTab = true;
				break;
			}
		}

		waitForUiToSettle();
		assertAnyVisibleText(headingText);
		assertLegalContentVisible();
		captureScreenshot(screenshotName);
		metadata.put(urlMetadataKey, driver.getCurrentUrl());

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(originHandle);
		} else {
			driver.navigate().back();
		}

		driver.switchTo().window(applicationHandle);
		waitForUiToSettle();
		assertAnyVisibleText("Sección Legal");
	}

	private void ensureMiNegocioSubmenuVisible() {
		if (hasAnyVisibleText("Administrar Negocios")) {
			return;
		}

		clickByAnyText("Mi Negocio");
		assertAnyVisibleText("Administrar Negocios");
	}

	private void selectGoogleAccountIfVisible() {
		final String account = Optional.ofNullable(System.getProperty("saleads.google.account"))
				.orElseGet(() -> Optional.ofNullable(System.getenv("SALEADS_GOOGLE_ACCOUNT")).orElse(DEFAULT_GOOGLE_ACCOUNT));

		List<String> googleSelectors = Arrays.asList(
				"//*[contains(@data-identifier, " + toXpathText(account) + ")]",
				"//*[contains(normalize-space(), " + toXpathText(account) + ")]",
				"//div[@data-email and contains(@data-email, " + toXpathText(account) + ")]");

		String originalHandle = driver.getWindowHandle();
		Set<String> handlesAfterClick = new LinkedHashSet<>(driver.getWindowHandles());

		if (handlesAfterClick.size() > 1) {
			for (String handle : handlesAfterClick) {
				if (!handle.equals(applicationHandle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		for (String selector : googleSelectors) {
			try {
				WebElement accountElement = waitForVisible(By.xpath(selector), Duration.ofSeconds(5));
				clickElement(accountElement);
				waitForUiToSettle();
				break;
			} catch (Exception ignored) {
				// Keep trying alternate selectors because Google can render different account pickers.
			}
		}

		try {
			wait.until((ExpectedCondition<Boolean>) wd -> isVisible("Negocio") || isVisible("Mi Negocio"));
		} catch (TimeoutException ignored) {
			// Dashboard checks are validated by caller, so ignore here.
		}

		if (!driver.getWindowHandle().equals(originalHandle) && driver.getWindowHandles().contains(originalHandle)) {
			driver.switchTo().window(originalHandle);
		}
	}

	private void assertAnyVisibleText(final String... texts) {
		if (!hasAnyVisibleText(texts)) {
			throw new AssertionError("Expected visible text not found: " + Arrays.toString(texts));
		}
	}

	private boolean hasAnyVisibleText(final String... texts) {
		try {
			wait.until((ExpectedCondition<Boolean>) wd -> {
				for (String text : texts) {
					if (isVisible(text)) {
						return true;
					}
				}
				return false;
			});
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	private boolean isVisible(final String text) {
		By locator = By.xpath("//*[normalize-space()=" + toXpathText(text) + " or contains(normalize-space(), "
				+ toXpathText(text) + ")]");
		for (WebElement element : driver.findElements(locator)) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private void clickByAnyText(final String... texts) {
		Exception lastException = null;

		for (String text : texts) {
			List<WebElement> matches = driver
					.findElements(By.xpath("//*[normalize-space()=" + toXpathText(text) + " or contains(normalize-space(), "
							+ toXpathText(text) + ")]"));

			for (WebElement match : matches) {
				if (!match.isDisplayed()) {
					continue;
				}

				try {
					clickElement(resolveClickableElement(match));
					waitForUiToSettle();
					return;
				} catch (Exception e) {
					lastException = e;
				}
			}
		}

		throw new IllegalStateException("Unable to click any element with texts: " + Arrays.toString(texts), lastException);
	}

	private WebElement resolveClickableElement(final WebElement element) {
		String tag = element.getTagName().toLowerCase();
		if ("button".equals(tag) || "a".equals(tag) || "input".equals(tag)) {
			return element;
		}

		String role = Optional.ofNullable(element.getAttribute("role")).orElse("");
		if ("button".equalsIgnoreCase(role) || "link".equalsIgnoreCase(role)) {
			return element;
		}

		List<WebElement> ancestors = element.findElements(
				By.xpath("./ancestor-or-self::*[self::button or self::a or @role='button' or @role='link']"));
		if (!ancestors.isEmpty()) {
			return ancestors.get(0);
		}

		return element;
	}

	private void clickElement(final WebElement element) {
		try {
			element.click();
		} catch (Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void tryTypeInField(final String fieldLabelText, final String value) {
		List<By> candidates = Arrays.asList(
				By.xpath("//input[@placeholder=" + toXpathText(fieldLabelText) + "]"),
				By.xpath("//label[contains(normalize-space(), " + toXpathText(fieldLabelText)
						+ ")]/following::input[1]"),
				By.xpath("//input[@aria-label=" + toXpathText(fieldLabelText) + "]"),
				By.xpath("//input[contains(@name, 'negocio') or contains(@id, 'negocio')]"));

		for (By by : candidates) {
			List<WebElement> elements = driver.findElements(by);
			for (WebElement element : elements) {
				if (element.isDisplayed() && element.isEnabled()) {
					element.clear();
					element.sendKeys(value);
					waitForUiToSettle();
					return;
				}
			}
		}
	}

	private void assertBusinessListIsVisible() {
		List<By> candidates = Arrays.asList(
				By.xpath("//*[contains(normalize-space(), 'Tus Negocios')]/following::*[self::ul or self::table][1]"),
				By.xpath("//*[contains(normalize-space(), 'Tus Negocios')]/following::*[contains(@class, 'business')][1]"),
				By.xpath("//div[contains(@class, 'business') and .//*[contains(normalize-space(), 'Negocio')]]"));

		for (By by : candidates) {
			for (WebElement element : driver.findElements(by)) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}

		throw new AssertionError("Business list not visible in 'Tus Negocios' section.");
	}

	private void assertPageContainsEmail() {
		String expectedEmail = Optional.ofNullable(System.getProperty("saleads.google.account"))
				.orElseGet(() -> Optional.ofNullable(System.getenv("SALEADS_GOOGLE_ACCOUNT")).orElse(DEFAULT_GOOGLE_ACCOUNT));
		String bodyText = driver.findElement(By.tagName("body")).getText();

		if (bodyText.contains(expectedEmail)) {
			return;
		}

		// If account alias changes per environment, validate generic email shape.
		for (WebElement element : driver.findElements(By.xpath("//*[contains(normalize-space(), '@')]"))) {
			if (element.isDisplayed()) {
				return;
			}
		}

		throw new AssertionError("Expected user email is not visible.");
	}

	private void assertLikelyUserNameIsVisible() {
		Set<String> ignoredTokens = new LinkedHashSet<>(Arrays.asList("información general", "business plan", "cambiar plan",
				"detalles de la cuenta", "cuenta creada", "estado activo", "idioma seleccionado"));

		List<WebElement> candidates = driver.findElements(By.xpath(
				"//*[self::h1 or self::h2 or self::h3 or self::p or self::span][string-length(normalize-space()) > 2]"));
		for (WebElement element : candidates) {
			if (!element.isDisplayed()) {
				continue;
			}

			String text = element.getText().trim();
			String normalized = text.toLowerCase();
			if (text.contains("@")) {
				continue;
			}
			if (ignoredTokens.contains(normalized)) {
				continue;
			}
			if (text.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*") && text.length() <= 80) {
				return;
			}
		}

		throw new AssertionError("User name text not detected.");
	}

	private void assertLegalContentVisible() {
		String bodyText = driver.findElement(By.tagName("body")).getText();
		if (bodyText == null || bodyText.trim().length() < 120) {
			throw new AssertionError("Legal content appears to be empty.");
		}
	}

	private void waitForUiToSettle() {
		wait.until((ExpectedCondition<Boolean>) wd -> "complete"
				.equals(((JavascriptExecutor) wd).executeScript("return document.readyState")));
		try {
			Thread.sleep(500L);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private WebElement waitForVisible(final By by, final Duration timeout) {
		WebDriverWait localWait = new WebDriverWait(driver, timeout);
		return localWait.until(wd -> {
			List<WebElement> elements = wd.findElements(by);
			for (WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private void captureScreenshot(final String name) throws IOException {
		Path outputFile = evidenceDir.resolve(name + "-" + TIMESTAMP_FORMAT.format(LocalDateTime.now()) + ".png");
		Files.copy(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath(), outputFile,
				StandardCopyOption.REPLACE_EXISTING);
		metadata.put("screenshot_" + name, outputFile.toString());
	}

	private void captureFullPageScreenshot(final String name) throws IOException {
		Dimension originalSize = driver.manage().window().getSize();
		try {
			Long pageHeight = ((Number) ((JavascriptExecutor) driver).executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);")).longValue();
			int height = (int) Math.min(Math.max(pageHeight + 200L, 1080L), 8000L);
			driver.manage().window().setSize(new Dimension(Math.max(originalSize.getWidth(), 1920), height));
			waitForUiToSettle();
			captureScreenshot(name);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToSettle();
		}
	}

	private void markStepPass(final String step) {
		stepResults.put(step, "PASS");
	}

	private void recordStepFailure(final String step, final Throwable exception, final String screenshotName) {
		stepResults.put(step, "FAIL");
		metadata.put("error_" + step, exception.getClass().getSimpleName() + ": " + exception.getMessage());
		try {
			captureScreenshot(screenshotName);
		} catch (Exception ignored) {
			metadata.put("error_" + step + "_screenshot", "Unable to capture failure screenshot.");
		}
	}

	private void writeFinalReport() throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Workflow - Final Report");
		lines.add("Generated at: " + LocalDateTime.now());
		lines.add("");
		lines.add("Validation results:");
		for (Map.Entry<String, String> entry : stepResults.entrySet()) {
			lines.add("- " + entry.getKey() + ": " + entry.getValue());
		}
		lines.add("");
		lines.add("Evidence and metadata:");
		for (Map.Entry<String, String> entry : metadata.entrySet()) {
			lines.add("- " + entry.getKey() + ": " + entry.getValue());
		}

		Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.write(reportPath, lines, StandardCharsets.UTF_8);
	}

	private void assertAllStepsPassed() {
		List<String> failed = new ArrayList<>();
		for (Map.Entry<String, String> step : stepResults.entrySet()) {
			if (!"PASS".equals(step.getValue())) {
				failed.add(step.getKey());
			}
		}
		Assert.assertTrue("Some validations failed: " + failed + ". See target/saleads-evidence/final-report.txt",
				failed.isEmpty());
	}

	private String getConfig(final String systemPropertyName, final String envName) {
		String value = System.getProperty(systemPropertyName);
		if (isBlank(value)) {
			value = System.getenv(envName);
		}
		return value;
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private String toXpathText(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		String[] parts = value.split("'");
		StringBuilder expression = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				expression.append(",\"'\",");
			}
			expression.append("'").append(parts[i]).append("'");
		}
		expression.append(")");
		return expression.toString();
	}
}
