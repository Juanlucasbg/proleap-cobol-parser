package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Assume;
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
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioWorkflowTest {

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final Duration SHORT_WAIT = Duration.ofSeconds(8);

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String appWindowHandle;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		initializeReport();

		final String loginUrl = readConfig("saleads.login.url");
		final String debuggerAddress = readConfig("saleads.chrome.debuggerAddress");
		final boolean useHeadless = Boolean.parseBoolean(readConfig("saleads.headless", "false"));
		evidenceDir = Path.of(readConfig("saleads.evidence.dir", "target/saleads-evidence"));
		Files.createDirectories(evidenceDir);

		Assume.assumeTrue(
				"Set -Dsaleads.login.url=<url> or -Dsaleads.chrome.debuggerAddress=<host:port> before running this test.",
				hasText(loginUrl) || hasText(debuggerAddress));

		setupDriver(debuggerAddress, useHeadless);

		try {
			if (hasText(loginUrl) && !hasText(debuggerAddress)) {
				driver.get(loginUrl);
				waitForUiToLoad();
			}

			appWindowHandle = driver.getWindowHandle();

			report.put("Login", validateLoginFlow());
			report.put("Mi Negocio menu", report.get("Login") && validateMiNegocioMenu());
			report.put("Agregar Negocio modal", report.get("Mi Negocio menu") && validateAgregarNegocioModal());
			report.put("Administrar Negocios view",
					report.get("Mi Negocio menu") && validateAdministrarNegociosView());
			report.put("Información General",
					report.get("Administrar Negocios view") && validateInformacionGeneralSection());
			report.put("Detalles de la Cuenta",
					report.get("Administrar Negocios view") && validateDetallesDeCuentaSection());
			report.put("Tus Negocios", report.get("Administrar Negocios view") && validateTusNegociosSection());
			report.put("Términos y Condiciones",
					report.get("Administrar Negocios view") && validateTerminosYCondiciones());
			report.put("Política de Privacidad",
					report.get("Administrar Negocios view") && validatePoliticaDePrivacidad());
		} finally {
			writeFinalReport();
			if (driver != null) {
				driver.quit();
			}
		}

		final List<String> failures = report.entrySet().stream().filter(entry -> !Boolean.TRUE.equals(entry.getValue()))
				.map(Map.Entry::getKey).collect(Collectors.toList());
		Assert.assertTrue("Workflow validation failures: " + failures, failures.isEmpty());
	}

	private void initializeReport() {
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
	}

	private void setupDriver(final String debuggerAddress, final boolean useHeadless) {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		if (hasText(debuggerAddress)) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		} else if (useHeadless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
	}

	private boolean validateLoginFlow() {
		try {
			final boolean clickedLoginButton = clickFirstVisibleText(
					"Sign in with Google",
					"Iniciar sesion con Google",
					"Inicia sesion con Google",
					"Acceder con Google",
					"Continuar con Google",
					"Login with Google");
			if (!clickedLoginButton) {
				return false;
			}

			waitForUiToLoad();
			clickIfVisible(SHORT_WAIT, "juanlucasbarbiergarzon@gmail.com");
			waitForUiToLoad();

			final boolean mainInterfaceVisible = waitForAnyVisible(Duration.ofSeconds(120),
					"Mi Negocio",
					"Negocio",
					"Dashboard");
			final boolean leftSidebarVisible = waitForAnyVisible(Duration.ofSeconds(45), "Negocio");

			captureScreenshot("01-dashboard-loaded");
			return mainInterfaceVisible && leftSidebarVisible;
		} catch (final Exception exception) {
			return false;
		}
	}

	private boolean validateMiNegocioMenu() {
		try {
			clickIfVisible(SHORT_WAIT, "Negocio");
			final boolean miNegocioClicked = clickFirstVisibleText("Mi Negocio");
			if (!miNegocioClicked) {
				return false;
			}
			waitForUiToLoad();

			final boolean submenuExpanded = waitForAnyVisible(SHORT_WAIT, "Agregar Negocio")
					&& waitForAnyVisible(SHORT_WAIT, "Administrar Negocios");
			captureScreenshot("02-mi-negocio-menu-expanded");
			return submenuExpanded;
		} catch (final Exception exception) {
			return false;
		}
	}

	private boolean validateAgregarNegocioModal() {
		try {
			if (!waitForAnyVisible(SHORT_WAIT, "Agregar Negocio")) {
				clickFirstVisibleText("Mi Negocio");
				waitForUiToLoad();
			}
			final boolean clickedAgregar = clickFirstVisibleText("Agregar Negocio");
			if (!clickedAgregar) {
				return false;
			}
			waitForUiToLoad();

			final boolean hasModalTitle = waitForAnyVisible(SHORT_WAIT, "Crear Nuevo Negocio");
			final boolean hasNameInput = findVisibleElement(By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
					SHORT_WAIT).isPresent()
					|| findVisibleElement(By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]"), SHORT_WAIT)
							.isPresent();
			final boolean hasBusinessLimitText = waitForAnyVisible(SHORT_WAIT, "Tienes 2 de 3 negocios");
			final boolean hasCancelButton = waitForAnyVisible(SHORT_WAIT, "Cancelar");
			final boolean hasCreateButton = waitForAnyVisible(SHORT_WAIT, "Crear Negocio");

			captureScreenshot("03-agregar-negocio-modal");

			final Optional<WebElement> nameInput = findVisibleElement(
					By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
					SHORT_WAIT);
			if (nameInput.isPresent()) {
				nameInput.get().click();
				nameInput.get().clear();
				nameInput.get().sendKeys("Negocio Prueba Automatizacion");
			}

			clickIfVisible(SHORT_WAIT, "Cancelar");
			waitForUiToLoad();

			return hasModalTitle && hasNameInput && hasBusinessLimitText && hasCancelButton && hasCreateButton;
		} catch (final Exception exception) {
			return false;
		}
	}

	private boolean validateAdministrarNegociosView() {
		try {
			if (!waitForAnyVisible(SHORT_WAIT, "Administrar Negocios")) {
				clickFirstVisibleText("Mi Negocio");
				waitForUiToLoad();
			}
			final boolean clickedAdministrar = clickFirstVisibleText("Administrar Negocios");
			if (!clickedAdministrar) {
				return false;
			}
			waitForUiToLoad();

			final boolean hasInformacionGeneral = waitForAnyVisible(DEFAULT_WAIT, "Informacion General", "Información General");
			final boolean hasDetallesCuenta = waitForAnyVisible(DEFAULT_WAIT, "Detalles de la Cuenta");
			final boolean hasTusNegocios = waitForAnyVisible(DEFAULT_WAIT, "Tus Negocios");
			final boolean hasLegalSection = waitForAnyVisible(DEFAULT_WAIT, "Seccion Legal", "Sección Legal");

			captureScreenshot("04-administrar-negocios-view");
			return hasInformacionGeneral && hasDetallesCuenta && hasTusNegocios && hasLegalSection;
		} catch (final Exception exception) {
			return false;
		}
	}

	private boolean validateInformacionGeneralSection() {
		try {
			final Optional<WebElement> section = findSectionByHeading("Informacion General", "Información General");
			if (section.isEmpty()) {
				return false;
			}

			final boolean userEmailVisible = hasVisibleEmail(section.get());
			final boolean userNameVisible = hasLikelyUserName(section.get());
			final boolean hasBusinessPlan = hasTextInside(section.get(), "BUSINESS PLAN");
			final boolean hasCambiarPlan = hasTextInside(section.get(), "Cambiar Plan");

			return userNameVisible && userEmailVisible && hasBusinessPlan && hasCambiarPlan;
		} catch (final Exception exception) {
			return false;
		}
	}

	private boolean validateDetallesDeCuentaSection() {
		try {
			final Optional<WebElement> section = findSectionByHeading("Detalles de la Cuenta");
			if (section.isEmpty()) {
				return false;
			}

			final boolean cuentaCreadaVisible = hasTextInside(section.get(), "Cuenta creada");
			final boolean estadoActivoVisible = hasTextInside(section.get(), "Estado activo");
			final boolean idiomaSeleccionadoVisible = hasTextInside(section.get(), "Idioma seleccionado");
			return cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible;
		} catch (final Exception exception) {
			return false;
		}
	}

	private boolean validateTusNegociosSection() {
		try {
			final Optional<WebElement> section = findSectionByHeading("Tus Negocios");
			if (section.isEmpty()) {
				return false;
			}

			final boolean businessListVisible = !section.get().findElements(By.xpath(".//li[normalize-space(.)!='']")).isEmpty()
					|| !section.get().findElements(By.xpath(".//table//tr")).isEmpty()
					|| !section.get().findElements(By.xpath(".//div[normalize-space(.)!='']")).isEmpty();
			final boolean agregarNegocioButtonVisible = hasTextInside(section.get(), "Agregar Negocio");
			final boolean hasBusinessLimitText = hasTextInside(section.get(), "Tienes 2 de 3 negocios");
			return businessListVisible && agregarNegocioButtonVisible && hasBusinessLimitText;
		} catch (final Exception exception) {
			return false;
		}
	}

	private boolean validateTerminosYCondiciones() {
		return validateLegalNavigation("Terminos y Condiciones", "Términos y Condiciones", "08-terminos-condiciones");
	}

	private boolean validatePoliticaDePrivacidad() {
		return validateLegalNavigation("Politica de Privacidad", "Política de Privacidad", "09-politica-privacidad");
	}

	private boolean validateLegalNavigation(final String plainText, final String accentedText, final String screenshotName) {
		final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());
		final String currentHandle = driver.getWindowHandle();

		final boolean clicked = clickFirstVisibleText(plainText, accentedText);
		if (!clicked) {
			return false;
		}

		waitForUiToLoad();
		final String legalHandle = resolveLegalWindowHandle(handlesBeforeClick, currentHandle);

		if (!Objects.equals(currentHandle, legalHandle)) {
			driver.switchTo().window(legalHandle);
			waitForUiToLoad();
		}

		final boolean hasHeading = waitForAnyVisible(DEFAULT_WAIT, plainText, accentedText);
		final boolean hasLegalText = hasLongVisibleText();
		captureScreenshot(screenshotName);
		legalUrls.put(accentedText, driver.getCurrentUrl());

		if (!Objects.equals(appWindowHandle, driver.getWindowHandle())) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		return hasHeading && hasLegalText;
	}

	private String resolveLegalWindowHandle(final Set<String> handlesBeforeClick, final String fallbackHandle) {
		final long maxWaitMs = 10000;
		final long pollIntervalMs = 250;
		long elapsed = 0;

		while (elapsed <= maxWaitMs) {
			final Set<String> handlesAfter = driver.getWindowHandles();
			if (handlesAfter.size() > handlesBeforeClick.size()) {
				for (final String handle : handlesAfter) {
					if (!handlesBeforeClick.contains(handle)) {
						return handle;
					}
				}
			}
			sleep(pollIntervalMs);
			elapsed += pollIntervalMs;
		}
		return fallbackHandle;
	}

	private Optional<WebElement> findSectionByHeading(final String... headingTexts) {
		for (final String heading : headingTexts) {
			final String headingXPath = "//*[contains(normalize-space(.)," + xpathLiteral(heading) + ")]";
			final List<WebElement> headings = driver.findElements(By.xpath(headingXPath));
			for (final WebElement element : headings) {
				if (!isDisplayed(element)) {
					continue;
				}
				final List<WebElement> containers = element.findElements(By.xpath("./ancestor-or-self::*[self::section or self::div][1]"));
				if (!containers.isEmpty() && isDisplayed(containers.get(0))) {
					return Optional.of(containers.get(0));
				}
			}
		}
		return Optional.empty();
	}

	private boolean hasVisibleEmail(final WebElement section) {
		final List<WebElement> emailCandidates = section
				.findElements(By.xpath(".//*[contains(normalize-space(.),'@') and contains(normalize-space(.),'.')]"));
		for (final WebElement candidate : emailCandidates) {
			if (isDisplayed(candidate)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasLikelyUserName(final WebElement section) {
		final List<WebElement> candidates = section.findElements(By.xpath(".//*[normalize-space(.)!='']"));
		for (final WebElement candidate : candidates) {
			if (!isDisplayed(candidate)) {
				continue;
			}
			final String text = candidate.getText().trim();
			if (text.length() < 3 || text.contains("@")) {
				continue;
			}
			if (text.equalsIgnoreCase("Informacion General")
					|| text.equalsIgnoreCase("Información General")
					|| text.equalsIgnoreCase("BUSINESS PLAN")
					|| text.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}
			if (text.matches("^[A-Za-zÀ-ÿ'\\- ]{3,}$")) {
				return true;
			}
		}
		return false;
	}

	private boolean hasTextInside(final WebElement section, final String text) {
		final String locator = ".//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]";
		for (final WebElement element : section.findElements(By.xpath(locator))) {
			if (isDisplayed(element)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasLongVisibleText() {
		final List<WebElement> candidates = new ArrayList<>();
		candidates.addAll(driver.findElements(By.xpath("//p[normalize-space(.)!='']")));
		candidates.addAll(driver.findElements(By.xpath("//div[normalize-space(.)!='']")));
		for (final WebElement element : candidates) {
			if (isDisplayed(element) && element.getText() != null && element.getText().trim().length() > 80) {
				return true;
			}
		}
		return false;
	}

	private boolean clickFirstVisibleText(final String... texts) {
		for (final String text : texts) {
			final Optional<WebElement> candidate = findVisibleElement(text, SHORT_WAIT);
			if (candidate.isPresent()) {
				clickElement(candidate.get());
				waitForUiToLoad();
				return true;
			}
		}
		return false;
	}

	private boolean clickIfVisible(final Duration timeout, final String text) {
		final Optional<WebElement> candidate = findVisibleElement(text, timeout);
		if (candidate.isEmpty()) {
			return false;
		}
		clickElement(candidate.get());
		waitForUiToLoad();
		return true;
	}

	private Optional<WebElement> findVisibleElement(final String text, final Duration timeout) {
		final String locator = "//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]";
		return findVisibleElement(By.xpath(locator), timeout);
	}

	private Optional<WebElement> findVisibleElement(final By by, final Duration timeout) {
		try {
			final WebDriverWait localWait = new WebDriverWait(driver, timeout);
			return Optional.ofNullable(localWait.until(driverInstance -> {
				final List<WebElement> candidates = driverInstance.findElements(by);
				for (final WebElement candidate : candidates) {
					if (isDisplayed(candidate)) {
						return candidate;
					}
				}
				return null;
			}));
		} catch (final TimeoutException exception) {
			return Optional.empty();
		}
	}

	private boolean waitForAnyVisible(final Duration timeout, final String... texts) {
		final long maxMs = timeout.toMillis();
		long elapsed = 0;
		final long intervalMs = 250;
		while (elapsed <= maxMs) {
			for (final String text : texts) {
				if (findVisibleElement(text, Duration.ofMillis(intervalMs)).isPresent()) {
					return true;
				}
			}
			elapsed += intervalMs;
		}
		return false;
	}

	private void clickElement(final WebElement element) {
		try {
			new Actions(driver).moveToElement(element).pause(Duration.ofMillis(150)).perform();
			element.click();
		} catch (final Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private void waitForUiToLoad() {
		try {
			final ExpectedCondition<Boolean> documentReady = webDriver -> "complete".equals(
					((JavascriptExecutor) webDriver).executeScript("return document.readyState"));
			wait.until(documentReady);
		} catch (final Exception ignored) {
			// Some transitions are SPA updates without full readyState changes.
		}
		sleep(900);
	}

	private void captureScreenshot(final String name) {
		try {
			if (!(driver instanceof TakesScreenshot)) {
				return;
			}
			final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			final Path target = evidenceDir.resolve(name + ".png");
			Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception ignored) {
			// Screenshot capture must never break test execution.
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append("  \"workflow\": \"saleads_mi_negocio_full_test\",\n");
		builder.append("  \"results\": {\n");

		final List<Map.Entry<String, Boolean>> entries = new ArrayList<>(report.entrySet());
		for (int i = 0; i < entries.size(); i++) {
			final Map.Entry<String, Boolean> entry = entries.get(i);
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL").append("\"");
			builder.append(i < entries.size() - 1 ? ",\n" : "\n");
		}
		builder.append("  },\n");

		builder.append("  \"legal_urls\": {\n");
		final List<Map.Entry<String, String>> legalEntries = new ArrayList<>(legalUrls.entrySet());
		for (int i = 0; i < legalEntries.size(); i++) {
			final Map.Entry<String, String> entry = legalEntries.get(i);
			builder.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(escapeJson(entry.getValue())).append("\"");
			builder.append(i < legalEntries.size() - 1 ? ",\n" : "\n");
		}
		builder.append("  }\n");
		builder.append("}\n");

		Files.writeString(evidenceDir.resolve("final-report.json"), builder.toString(), StandardCharsets.UTF_8);
	}

	private String readConfig(final String key) {
		return readConfig(key, "");
	}

	private String readConfig(final String key, final String defaultValue) {
		final String propertyValue = System.getProperty(key);
		if (hasText(propertyValue)) {
			return propertyValue.trim();
		}
		final String envKey = key.toUpperCase().replace('.', '_');
		final String envValue = System.getenv(envKey);
		if (hasText(envValue)) {
			return envValue.trim();
		}
		return defaultValue;
	}

	private boolean hasText(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	private boolean isDisplayed(final WebElement element) {
		try {
			return element != null && element.isDisplayed();
		} catch (final Exception exception) {
			return false;
		}
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'");
		final StringBuilder concat = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			concat.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				concat.append(",\"'\",");
			}
		}
		concat.append(")");
		return concat.toString();
	}

	private String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
