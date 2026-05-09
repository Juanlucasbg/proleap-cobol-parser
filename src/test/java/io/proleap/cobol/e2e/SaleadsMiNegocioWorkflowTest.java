package io.proleap.cobol.e2e;

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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioWorkflowTest {

	private static final String DEFAULT_LOGIN_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter RUN_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Map<String, String> reportStatus;
	private List<String> failures;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		reportStatus = new LinkedHashMap<>();
		failures = new ArrayList<>();
		evidenceDir = Path.of("target", "saleads-e2e", LocalDateTime.now().format(RUN_FORMAT));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--start-maximized");

		if (Boolean.parseBoolean(readSetting("SALEADS_HEADLESS", "saleads.headless", "false"))) {
			options.addArguments("--headless=new", "--window-size=1920,1080");
		}

		final String debuggerAddress = readSetting("SALEADS_CHROME_DEBUGGER", "saleads.chromeDebugger", "");
		if (!debuggerAddress.isBlank()) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver,
				Duration.ofSeconds(Long.parseLong(readSetting("SALEADS_TIMEOUT_SECONDS", "saleads.timeoutSeconds", "30"))));

		final String loginUrl = readSetting("SALEADS_LOGIN_URL", "saleads.loginUrl", "");
		if (!loginUrl.isBlank()) {
			driver.navigate().to(loginUrl);
			waitForUiToSettle();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminos);
		runStep("Política de Privacidad", this::stepValidatePrivacidad);

		writeFinalReport();
		assertTrue("Workflow validation failures:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		if ("about:blank".equalsIgnoreCase(driver.getCurrentUrl())) {
			throw new IllegalStateException(
					"The browser is on about:blank. Provide SALEADS_LOGIN_URL (or saleads.loginUrl) for this environment.");
		}

		clickFirstVisible(
				"Sign in with Google",
				"Iniciar sesion con Google",
				"Iniciar sesión con Google",
				"Continuar con Google",
				"Google");

		selectGoogleAccountIfPrompted(readSetting("SALEADS_GOOGLE_ACCOUNT", "saleads.googleAccount", DEFAULT_LOGIN_ACCOUNT));
		waitForSidebar();
		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		waitForSidebar();
		clickIfVisible("Negocio");
		clickFirstVisible("Mi Negocio");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickFirstVisible("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		assertLabeledInputVisible("Nombre del Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		final Optional<WebElement> nombreNegocioInput = findVisible(By.xpath(
				"//input[contains(@placeholder,'Nombre del Negocio')] | //label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));
		if (nombreNegocioInput.isPresent()) {
			nombreNegocioInput.get().click();
			nombreNegocioInput.get().clear();
			nombreNegocioInput.get().sendKeys("Negocio Prueba Automatizacion");
		}

		clickFirstVisible("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byTextContains("Crear Nuevo Negocio")));
		waitForUiToSettle();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (findVisible(byInteractiveText("Administrar Negocios")).isEmpty()) {
			clickFirstVisible("Mi Negocio");
		}

		clickFirstVisible("Administrar Negocios");
		assertTextVisible("Informacion General", "Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Seccion Legal", "Sección Legal");
		takeFullPageScreenshot("04-administrar-negocios");
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible("Informacion General", "Información General");
		assertProbableUserNameVisible();
		assertPageContainsRegex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
		assertTextVisible("BUSINESS PLAN");
		assertTextVisible("Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible("Cuenta creada");
		assertTextVisible("Estado activo");
		assertTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible("Tus Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminos() throws IOException {
		termsUrl = openLegalLink(
				new String[] { "Terminos y Condiciones", "Términos y Condiciones" },
				new String[] { "Terminos y Condiciones", "Términos y Condiciones" },
				"05-terminos-y-condiciones");
	}

	private void stepValidatePrivacidad() throws IOException {
		privacyUrl = openLegalLink(
				new String[] { "Politica de Privacidad", "Política de Privacidad" },
				new String[] { "Politica de Privacidad", "Política de Privacidad" },
				"06-politica-de-privacidad");
	}

	private String openLegalLink(final String[] linkLabels, final String[] headingLabels, final String screenshotName)
			throws IOException {
		final String appHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String startUrl = driver.getCurrentUrl();

		clickFirstVisible(linkLabels);
		final Optional<String> newWindowHandle = waitForNewWindowHandle(handlesBeforeClick, Duration.ofSeconds(8));
		newWindowHandle.ifPresent(handle -> driver.switchTo().window(handle));

		assertTextVisible(headingLabels);
		assertLegalContentVisible();
		final String finalUrl = driver.getCurrentUrl();
		takeScreenshot(screenshotName);

		if (newWindowHandle.isPresent()) {
			driver.close();
			driver.switchTo().window(appHandle);
			waitForUiToSettle();
		} else if (!startUrl.equals(finalUrl)) {
			driver.navigate().back();
			waitForUiToSettle();
		}

		assertTextVisible("Seccion Legal", "Sección Legal");
		return finalUrl;
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			reportStatus.put(stepName, "PASS");
		} catch (final Throwable throwable) {
			reportStatus.put(stepName, "FAIL");
			failures.add(stepName + " -> " + rootMessage(throwable));
			try {
				takeScreenshot("failure-" + sanitize(stepName));
			} catch (final IOException ignored) {
				failures.add(stepName + " -> failed to capture failure screenshot: " + ignored.getMessage());
			}
		}
	}

	private void writeFinalReport() throws IOException {
		final Path reportPath = evidenceDir.resolve("final-report.md");
		final StringBuilder report = new StringBuilder();
		report.append("# SaleADS Mi Negocio - Final Report\n\n");
		report.append("| Checkpoint | Result |\n");
		report.append("|---|---|\n");

		final List<String> expectedFields = Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad");

		for (final String field : expectedFields) {
			report.append("| ")
					.append(field)
					.append(" | ")
					.append(reportStatus.getOrDefault(field, "NOT_RUN"))
					.append(" |\n");
		}

		report.append("\n## Legal URLs\n");
		report.append("- Términos y Condiciones: ").append(termsUrl).append('\n');
		report.append("- Política de Privacidad: ").append(privacyUrl).append('\n');
		report.append("\n## Evidence directory\n");
		report.append("- ").append(evidenceDir.toAbsolutePath()).append('\n');

		Files.writeString(reportPath, report);
	}

	private void waitForSidebar() {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside | //nav")));
		waitForUiToSettle();
	}

	private void selectGoogleAccountIfPrompted(final String account) {
		final String currentHandle = driver.getWindowHandle();
		final Set<String> handlesBeforeSwitch = driver.getWindowHandles();

		waitForUiToSettle();
		if (driver.getWindowHandles().size() > 1) {
			for (final String handle : driver.getWindowHandles()) {
				if (!handle.equals(currentHandle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		} else {
			waitForNewWindowHandle(handlesBeforeSwitch, Duration.ofSeconds(5))
					.ifPresent(handle -> driver.switchTo().window(handle));
		}

		if (findVisible(byTextContains(account)).isPresent()) {
			clickFirstVisible(account);
		}

		wait.until(driverRef -> driverRef.getWindowHandles().size() >= 1);
		final Set<String> handles = driver.getWindowHandles();
		if (handles.contains(currentHandle)) {
			driver.switchTo().window(currentHandle);
		} else {
			driver.switchTo().window(handles.iterator().next());
		}
		waitForUiToSettle();
	}

	private void clickFirstVisible(final String... labels) {
		for (final String label : labels) {
			final Optional<WebElement> candidate = findVisible(byInteractiveText(label));
			if (candidate.isPresent()) {
				wait.until(ExpectedConditions.elementToBeClickable(candidate.get())).click();
				waitForUiToSettle();
				return;
			}
		}

		throw new IllegalStateException("Could not find clickable element with labels: " + Arrays.toString(labels));
	}

	private void clickIfVisible(final String label) {
		findVisible(byInteractiveText(label)).ifPresent(element -> {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
			waitForUiToSettle();
		});
	}

	private void assertTextVisible(final String... labels) {
		for (final String label : labels) {
			if (findVisible(byTextContains(label)).isPresent()) {
				return;
			}
		}

		throw new IllegalStateException("None of the expected text labels are visible: " + Arrays.toString(labels));
	}

	private void assertLabeledInputVisible(final String label) {
		final Optional<WebElement> input = findVisible(By.xpath(
				"//input[contains(@placeholder,'" + label + "')] | //label[contains(normalize-space(.),'" + label
						+ "')]/following::input[1]"));

		if (input.isEmpty()) {
			throw new IllegalStateException("Input field not visible for label: " + label);
		}
	}

	private void assertPageContainsRegex(final String regex) {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		if (!bodyText.matches("(?s).*" + regex + ".*")) {
			throw new IllegalStateException("Page text does not contain pattern: " + regex);
		}
	}

	private void assertProbableUserNameVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		for (final String line : bodyText.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.length() < 3 || trimmed.length() > 80) {
				continue;
			}

			final String upper = trimmed.toUpperCase(Locale.ROOT);
			if (trimmed.contains("@")
					|| upper.contains("INFORMACION GENERAL")
					|| upper.contains("INFORMACIÓN GENERAL")
					|| upper.contains("BUSINESS PLAN")
					|| upper.contains("CAMBIAR PLAN")) {
				continue;
			}

			if (trimmed.matches("[\\p{L}][\\p{L} .'-]{2,}")) {
				return;
			}
		}

		throw new IllegalStateException("Could not identify a probable user name in the account view");
	}

	private void assertLegalContentVisible() {
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		if (bodyText.length() < 200) {
			throw new IllegalStateException("Legal content appears too short");
		}
	}

	private Optional<String> waitForNewWindowHandle(final Set<String> existingHandles, final Duration timeout) {
		final long timeoutMillis = timeout.toMillis();
		final long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < timeoutMillis) {
			final Set<String> currentHandles = driver.getWindowHandles();
			if (currentHandles.size() > existingHandles.size()) {
				for (final String handle : currentHandles) {
					if (!existingHandles.contains(handle)) {
						return Optional.of(handle);
					}
				}
			}

			try {
				Thread.sleep(250);
			} catch (final InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				return Optional.empty();
			}
		}

		return Optional.empty();
	}

	private Optional<WebElement> findVisible(final By locator) {
		try {
			return Optional.of(wait.until(ExpectedConditions.visibilityOfElementLocated(locator)));
		} catch (final TimeoutException ignored) {
			return Optional.empty();
		}
	}

	private By byTextContains(final String text) {
		return By.xpath("//*[contains(normalize-space(.)," + toXpathLiteral(text) + ")]");
	}

	private By byInteractiveText(final String text) {
		final String literal = toXpathLiteral(text);
		return By.xpath(
				"(//*[self::button or self::a or @role='button'][contains(normalize-space(.)," + literal + ")]"
						+ " | //*[contains(normalize-space(.)," + literal
						+ ")]/ancestor::*[self::button or self::a or @role='button'][1])[1]");
	}

	private String toXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder builder = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part = chars[i] == '\'' ? "\"'\"" : "'" + chars[i] + "'";
			builder.append(part);
			if (i < chars.length - 1) {
				builder.append(',');
			}
		}

		builder.append(')');
		return builder.toString();
	}

	private void waitForUiToSettle() {
		wait.until(driverRef -> "complete".equals(
				((JavascriptExecutor) driverRef).executeScript("return document.readyState")));

		try {
			Thread.sleep(400);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), evidenceDir.resolve(sanitize(name) + ".png"), StandardCopyOption.REPLACE_EXISTING);
	}

	private void takeFullPageScreenshot(final String name) throws IOException {
		final Long fullHeight = ((Number) ((JavascriptExecutor) driver).executeScript(
				"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);")).longValue();
		driver.manage().window().setSize(new Dimension(1920, Math.max(1080, fullHeight.intValue())));
		waitForUiToSettle();
		takeScreenshot(name);
	}

	private String sanitize(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String rootMessage(final Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}

		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}

	private String readSetting(final String envKey, final String propertyKey, final String defaultValue) {
		final String fromEnv = System.getenv(envKey);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		final String fromProperty = System.getProperty(propertyKey);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}

		return defaultValue;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
