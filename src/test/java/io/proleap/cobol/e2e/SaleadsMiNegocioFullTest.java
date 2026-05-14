package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDirectory;

	@Test
	public void saleadsMiNegocioFullTest() {
		final String baseUrl = getConfig("saleads.login.url", "SALEADS_LOGIN_URL");

		if (baseUrl == null || baseUrl.isBlank()) {
			fail("Set SALEADS_LOGIN_URL (or saleads.login.url) to the environment login page URL.");
		}

		try {
			setupDriver();
			driver.get(baseUrl);
			waitForUiToLoad();

			final String googleAccount = getConfigOrDefault("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT",
					DEFAULT_GOOGLE_ACCOUNT);
			executeStep("Login", () -> stepLoginWithGoogle(googleAccount));
			executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			executeStep("Administrar Negocios view", this::stepOpenAdministrarNegociosView);
			executeStep("Información General", this::stepValidateInformacionGeneral);
			executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			executeStep("Tus Negocios", this::stepValidateTusNegocios);
			executeStep("Términos y Condiciones",
					() -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos"));
			executeStep("Política de Privacidad",
					() -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09-privacidad"));
		} finally {
			writeFinalReport();

			if (driver != null) {
				driver.quit();
			}
		}

		final List<String> failedSteps = stepResults.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(entry -> entry.getKey() + ": " + entry.getValue().message).collect(Collectors.toList());

		if (!failedSteps.isEmpty()) {
			fail("Workflow validation failed:\n- " + String.join("\n- ", failedSteps));
		}
	}

	private void executeStep(final String stepName, final StepExecutable executable) {
		try {
			executable.run();
			stepResults.put(stepName, StepResult.pass());
		} catch (final Throwable throwable) {
			stepResults.put(stepName, StepResult.fail(throwable.getMessage()));
		}
	}

	private void setupDriver() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");

		if (Boolean.parseBoolean(getConfigOrDefault("saleads.headless", "SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDirectory = Paths.get("target", "saleads-mi-negocio", Instant.now().toString().replace(":", "-"));

		try {
			Files.createDirectories(evidenceDirectory);
		} catch (final IOException e) {
			throw new UncheckedIOException("Could not create evidence directory " + evidenceDirectory, e);
		}
	}

	private void stepLoginWithGoogle(final String googleAccount) {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Google");

		selectGoogleAccountIfPresent(googleAccount);
		waitForUiToLoad();

		assertVisibleText("Negocio");

		final List<WebElement> sidebars = driver.findElements(By.xpath("//aside | //nav"));
		assertFalse("Left sidebar was not detected after login.", sidebars.isEmpty());

		takeScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() {
		assertVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		takeScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");
		assertVisibleText("Crear Nuevo Negocio");
		assertVisibleText("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		takeScreenshot("03-agregar-negocio-modal");

		try {
			final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(
					"//input[@placeholder='Nombre del Negocio' or @name='nombreNegocio' or @name='businessName' or @id='nombreNegocio' or @id='businessName'] | //label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]")));
			input.click();
			input.clear();
			input.sendKeys("Negocio Prueba Automatización");
		} catch (final TimeoutException ignored) {
			// Optional action: if input is present in a different implementation, validations above already passed.
		}

		clickByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(byAnyVisibleText("Crear Nuevo Negocio")));
		waitForUiToLoad();
	}

	private void stepOpenAdministrarNegociosView() {
		if (!isVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		takeScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionContainer("Información General");
		assertTrue("User email is not visible in Información General.",
				containsVisibleText(section, "@") || !driver.findElements(By.xpath("//*[contains(text(), '@')]")).isEmpty());
		assertVisibleTextInContainer(section, "BUSINESS PLAN");
		assertVisibleTextInContainer(section, "Cambiar Plan");

		final String expectedName = getConfig("saleads.user.name", "SALEADS_USER_NAME");

		if (expectedName != null && !expectedName.isBlank()) {
			assertVisibleTextInContainer(section, expectedName);
		} else {
			final boolean hasLikelyName = section.findElements(By.xpath(".//*[self::h1 or self::h2 or self::h3 or self::p or self::span]"))
					.stream().map(WebElement::getText).map(String::trim)
					.anyMatch(text -> !text.isEmpty() && !text.contains("@") && !"BUSINESS PLAN".equalsIgnoreCase(text)
							&& !"Cambiar Plan".equalsIgnoreCase(text) && !"Información General".equalsIgnoreCase(text));
			assertTrue("User name is not visible in Información General.", hasLikelyName);
		}
	}

	private void stepValidateDetallesCuenta() {
		final WebElement section = findSectionContainer("Detalles de la Cuenta");
		assertVisibleTextInContainer(section, "Cuenta creada");

		final String sectionText = section.getText().toLowerCase();
		assertTrue("'Estado activo' is not visible.", sectionText.contains("estado")
				&& (sectionText.contains("activo") || sectionText.contains("activa")));
		assertTrue("'Idioma seleccionado' is not visible.", sectionText.contains("idioma")
				&& (sectionText.contains("seleccionado") || sectionText.contains("seleccionada")));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionContainer("Tus Negocios");
		assertVisibleTextInContainer(section, "Agregar Negocio");
		assertVisibleTextInContainer(section, "Tienes 2 de 3 negocios");

		final List<WebElement> rows = section.findElements(By.xpath(
				".//li | .//tr | .//div[contains(@class,'item')] | .//div[contains(@class,'business')] | .//article"));
		assertFalse("Business list is not visible in Tus Negocios.", rows.isEmpty());
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName) {
		final String appHandle = driver.getWindowHandle();
		final String appUrl = driver.getCurrentUrl();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();

		clickByVisibleText(linkText);

		final String targetHandle = wait.until(newWindowOrCurrentWindowChanged(handlesBeforeClick, appUrl));

		if (!Objects.equals(targetHandle, driver.getWindowHandle())) {
			driver.switchTo().window(targetHandle);
		}

		waitForUiToLoad();
		assertVisibleText(expectedHeading);

		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p[string-length(normalize-space(.)) > 40]"));
		assertFalse("Legal content is not visible for " + expectedHeading + ".", paragraphs.isEmpty());

		legalUrls.put(expectedHeading, driver.getCurrentUrl());
		takeScreenshot(screenshotName);

		if (!Objects.equals(targetHandle, appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().to(appUrl);
			waitForUiToLoad();
		}
	}

	private ExpectedCondition<String> newWindowOrCurrentWindowChanged(final Set<String> handlesBeforeClick,
			final String appUrl) {
		return webDriver -> {
			final Set<String> handlesAfterClick = webDriver.getWindowHandles();

			if (handlesAfterClick.size() > handlesBeforeClick.size()) {
				final List<String> newHandles = new ArrayList<>(handlesAfterClick);
				newHandles.removeAll(handlesBeforeClick);
				if (!newHandles.isEmpty()) {
					return newHandles.get(0);
				}
			}

			if (!Objects.equals(webDriver.getCurrentUrl(), appUrl)) {
				return webDriver.getWindowHandle();
			}

			return null;
		};
	}

	private void selectGoogleAccountIfPresent(final String accountEmail) {
		final List<String> googleSignals = Arrays.asList("accounts.google.com", "choose an account", "elige una cuenta");
		final boolean googleContextDetected = googleSignals.stream().anyMatch(signal -> driver.getPageSource().toLowerCase().contains(signal)
				|| driver.getCurrentUrl().toLowerCase().contains(signal));

		if (!googleContextDetected) {
			final Set<String> existingHandles = driver.getWindowHandles();
			for (final String handle : existingHandles) {
				driver.switchTo().window(handle);
				if (driver.getCurrentUrl().toLowerCase().contains("accounts.google.com")) {
					break;
				}
			}
		}

		final List<WebElement> accountSelectors = driver.findElements(byAnyVisibleText(accountEmail));
		if (!accountSelectors.isEmpty() && accountSelectors.get(0).isDisplayed()) {
			clickElement(accountSelectors.get(0));
		}
	}

	private void clickByVisibleText(final String... texts) {
		final By clickableBy = byClickableVisibleText(texts);
		final List<WebElement> clickableElements = driver.findElements(clickableBy).stream().filter(WebElement::isDisplayed)
				.collect(Collectors.toList());
		final WebElement element = clickableElements.isEmpty()
				? wait.until(ExpectedConditions.visibilityOfElementLocated(byAnyVisibleText(texts)))
				: clickableElements.get(0);
		clickElement(element);
	}

	private void clickElement(final WebElement element) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception ignored) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}

		waitForUiToLoad();
	}

	private void assertVisibleText(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(byAnyVisibleText(text)));
	}

	private void assertVisibleTextInContainer(final WebElement container, final String text) {
		assertTrue("'" + text + "' is not visible.", containsVisibleText(container, text));
	}

	private boolean containsVisibleText(final WebElement container, final String text) {
		return container.findElements(byAnyVisibleText(text)).stream().anyMatch(WebElement::isDisplayed);
	}

	private boolean isVisible(final String text) {
		return driver.findElements(byAnyVisibleText(text)).stream().anyMatch(WebElement::isDisplayed);
	}

	private WebElement findSectionContainer(final String sectionHeading) {
		final WebElement heading = wait.until(ExpectedConditions.visibilityOfElementLocated(byAnyVisibleText(sectionHeading)));
		final List<WebElement> containers = heading
				.findElements(By.xpath("./ancestor::*[self::section or self::div or self::article]"));

		for (final WebElement container : containers) {
			final String text = container.getText();
			if (text != null && text.length() > sectionHeading.length() + 20) {
				return container;
			}
		}

		return heading;
	}

	private By byAnyVisibleText(final String... texts) {
		final String predicate = Arrays.stream(texts)
				.map(text -> "contains(normalize-space(.), " + quoteXpathLiteral(text) + ")").collect(Collectors.joining(" or "));
		return By.xpath("//*[(" + predicate + ") and not(self::script) and not(self::style)]");
	}

	private By byClickableVisibleText(final String... texts) {
		final String predicate = Arrays.stream(texts)
				.map(text -> "contains(normalize-space(.), " + quoteXpathLiteral(text) + ")").collect(Collectors.joining(" or "));
		return By.xpath("//*[self::button or self::a or @role='button' or @tabindex='0'][(" + predicate + ")]");
	}

	private String quoteXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] parts = value.split("'", -1);
		final StringBuilder xpathLiteral = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			xpathLiteral.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				xpathLiteral.append(",\"'\",");
			}
		}
		xpathLiteral.append(")");
		return xpathLiteral.toString();
	}

	private void takeScreenshot(final String name) {
		final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = evidenceDirectory.resolve(name + ".png");

		try {
			Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException e) {
			throw new UncheckedIOException("Could not save screenshot to " + destination, e);
		}
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));

		try {
			Thread.sleep(350L);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private String getConfig(final String systemPropertyName, final String environmentName) {
		final String bySystemProperty = System.getProperty(systemPropertyName);
		if (bySystemProperty != null && !bySystemProperty.isBlank()) {
			return bySystemProperty;
		}

		final String byEnvironment = System.getenv(environmentName);
		if (byEnvironment != null && !byEnvironment.isBlank()) {
			return byEnvironment;
		}

		return null;
	}

	private String getConfigOrDefault(final String systemPropertyName, final String environmentName,
			final String defaultValue) {
		final String configuredValue = getConfig(systemPropertyName, environmentName);
		return configuredValue == null ? defaultValue : configuredValue;
	}

	private void writeFinalReport() {
		if (evidenceDirectory == null) {
			return;
		}

		final StringBuilder report = new StringBuilder();
		report.append("# SaleADS Mi Negocio Full Workflow Report\n\n");
		report.append("- Generated at: ").append(Instant.now()).append('\n');
		report.append("- Evidence directory: ").append(evidenceDirectory).append("\n\n");
		report.append("## Step Results\n\n");
		report.append("| Step | Status | Details |\n");
		report.append("| --- | --- | --- |\n");

		for (final String field : REPORT_FIELDS) {
			final StepResult result = stepResults.getOrDefault(field, StepResult.fail("Not executed"));
			report.append("| ").append(field).append(" | ").append(result.passed ? "PASS" : "FAIL").append(" | ")
					.append(result.message.replace('\n', ' ')).append(" |\n");
		}

		report.append("\n## Captured Legal URLs\n\n");
		if (legalUrls.isEmpty()) {
			report.append("- None\n");
		} else {
			legalUrls.forEach((name, value) -> report.append("- ").append(name).append(": ").append(value).append('\n'));
		}

		final Path outputFile = evidenceDirectory.resolve("final-report.md");
		try {
			Files.write(outputFile, report.toString().getBytes(StandardCharsets.UTF_8));
		} catch (final IOException e) {
			throw new UncheckedIOException("Could not write final report file " + outputFile, e);
		}
	}

	@FunctionalInterface
	private interface StepExecutable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String message;

		private StepResult(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message;
		}

		static StepResult pass() {
			return new StepResult(true, "OK");
		}

		static StepResult fail(final String message) {
			return new StepResult(false, message == null ? "No error details" : message);
		}
	}
}
