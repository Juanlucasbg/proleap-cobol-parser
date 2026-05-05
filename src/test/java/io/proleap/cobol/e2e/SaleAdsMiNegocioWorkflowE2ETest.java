/*
 * Copyright (C) 2026
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioWorkflowE2ETest {

	private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, StepResult> results = new LinkedHashMap<>();
	private String appWindowHandle;
	private String appUrlBeforeLegalNavigation;

	@Before
	public void setUp() throws Exception {
		final boolean enabled = Boolean.parseBoolean(value("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue(
				"Skipping SaleADS E2E test. Enable with -Dsaleads.e2e.enabled=true (or SALEADS_E2E_ENABLED=true).",
				enabled);

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(value("saleads.e2e.headless", "SALEADS_E2E_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		evidenceDir = Files.createDirectories(Path.of("target", "saleads-e2e-evidence", FILE_TS.format(LocalDateTime.now())));

		final String loginUrl = value("saleads.e2e.url", "SALEADS_E2E_URL", "");
		Assume.assumeTrue(
				"Provide login page URL using -Dsaleads.e2e.url=<env-login-url> or SALEADS_E2E_URL env var.",
				!loginUrl.isBlank());
		driver.get(loginUrl);
		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepAdministrarNegociosView);
		runStep("Información General", this::stepInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepDetallesCuenta);
		runStep("Tus Negocios", this::stepTusNegocios);
		runStep("Términos y Condiciones", this::stepTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepPoliticaPrivacidad);

		final String report = buildFinalReport();
		final Path reportFile = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportFile, report);
		System.out.println(report);
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());

		final List<String> failures = new ArrayList<>();
		for (Map.Entry<String, StepResult> entry : results.entrySet()) {
			if (!entry.getValue().pass) {
				failures.add(entry.getKey() + " => " + entry.getValue().details);
			}
		}
		if (!failures.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow validation failed:\n" + String.join("\n", failures) + "\n\n" + report);
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiLoad();

		handleGoogleAccountSelectorIfPresent();
		waitForUiLoad();

		requireVisibleAnyText("Negocio", "Mi Negocio");
		requireSidebarVisible();
		appWindowHandle = driver.getWindowHandle();
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		requireSidebarVisible();
		expandMiNegocioMenu();
		requireVisibleAnyText("Agregar Negocio");
		requireVisibleAnyText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		requireVisibleAnyText("Crear Nuevo Negocio");
		requireVisibleAnyText("Nombre del Negocio");
		requireVisibleAnyText("Tienes 2 de 3 negocios");
		requireVisibleAnyText("Cancelar");
		requireVisibleAnyText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final Optional<WebElement> nombreInput = findVisibleElements(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @name='Nombre del Negocio' or @aria-label='Nombre del Negocio']"))
				.stream().findFirst();
		if (nombreInput.isPresent()) {
			nombreInput.get().click();
			nombreInput.get().clear();
			nombreInput.get().sendKeys("Negocio Prueba Automatizacion");
		}
		clickByVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepAdministrarNegociosView() throws Exception {
		expandMiNegocioMenu();
		clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		requireVisibleAnyText("Información General");
		requireVisibleAnyText("Detalles de la Cuenta");
		requireVisibleAnyText("Tus Negocios");
		requireVisibleAnyText("Sección Legal");
		captureFullPageScreenshot("04-administrar-negocios-view");
	}

	private void stepInformacionGeneral() throws Exception {
		requireVisibleAnyText("Información General");
		final WebElement emailElement = findFirstVisibleByRegex("(?i).*@.*\\..*");
		Assert.assertNotNull("User email is not visible.", emailElement);
		requireVisibleAnyText("BUSINESS PLAN");
		requireVisibleAnyText("Cambiar Plan");

		final WebElement candidateName = findFirstVisibleByRegex("^[A-Za-zÀ-ÿ'`-]+\\s+[A-Za-zÀ-ÿ'`-].*");
		Assert.assertNotNull("User name is not visible.", candidateName);
	}

	private void stepDetallesCuenta() {
		requireVisibleAnyText("Detalles de la Cuenta");
		requireVisibleAnyText("Cuenta creada");
		requireVisibleAnyText("Estado activo");
		requireVisibleAnyText("Idioma seleccionado");
	}

	private void stepTusNegocios() {
		requireVisibleAnyText("Tus Negocios");
		requireVisibleAnyText("Agregar Negocio");
		requireVisibleAnyText("Tienes 2 de 3 negocios");
		final WebElement section = findFirstVisibleByText("Tus Negocios");
		Assert.assertNotNull("Tus Negocios section is not visible.", section);
	}

	private void stepTerminosYCondiciones() throws Exception {
		scrollToVisibleText("Sección Legal");
		appUrlBeforeLegalNavigation = driver.getCurrentUrl();
		openLegalLinkAndValidate("Términos y Condiciones", "08-terminos-y-condiciones");
		returnToApplicationTab();
	}

	private void stepPoliticaPrivacidad() throws Exception {
		scrollToVisibleText("Sección Legal");
		appUrlBeforeLegalNavigation = driver.getCurrentUrl();
		openLegalLinkAndValidate("Política de Privacidad", "09-politica-privacidad");
		returnToApplicationTab();
	}

	private void openLegalLinkAndValidate(final String linkText, final String screenshotName) throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		clickByVisibleText(linkText);

		boolean openedNewTab = false;
		try {
			wait.until(d -> d.getWindowHandles().size() > handlesBefore.size()
					|| !d.getCurrentUrl().equals(appUrlBeforeLegalNavigation));
		} catch (TimeoutException ignored) {
			// validation below will fail with explicit messages if navigation did not occur.
		}

		final Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > handlesBefore.size()) {
			for (String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					openedNewTab = true;
					break;
				}
			}
		}

		waitForUiLoad();
		requireVisibleAnyText(linkText);
		final String pageText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Legal content text is not visible for " + linkText + ".", pageText != null && pageText.trim().length() > 120);

		captureScreenshot(screenshotName);
		results.get(linkText).extra = "Final URL: " + driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiLoad();
		}
	}

	private void returnToApplicationTab() {
		if (appWindowHandle != null && !driver.getWindowHandle().equals(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
		if (appUrlBeforeLegalNavigation != null && !driver.getCurrentUrl().equals(appUrlBeforeLegalNavigation)) {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void handleGoogleAccountSelectorIfPresent() {
		final Set<String> handles = driver.getWindowHandles();
		for (String handle : handles) {
			driver.switchTo().window(handle);
			final Optional<WebElement> account = findVisibleElements(By.xpath(
					"//*[normalize-space(.)='" + GOOGLE_ACCOUNT_EMAIL + "']"))
					.stream().findFirst();
			if (account.isPresent()) {
				clickElementAndWait(account.get());
				return;
			}
		}

		// Ensure we return to app/main window if selector is not present.
		if (appWindowHandle != null && handles.contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		}
	}

	private void expandMiNegocioMenu() {
		final Optional<WebElement> administrar = findVisibleElements(By.xpath("//*[normalize-space(.)='Administrar Negocios']"))
				.stream().findFirst();
		if (administrar.isPresent()) {
			return;
		}

		final Optional<WebElement> miNegocio = findVisibleElements(By.xpath("//*[normalize-space(.)='Mi Negocio']"))
				.stream().findFirst();
		if (miNegocio.isPresent()) {
			clickElementAndWait(miNegocio.get());
			return;
		}

		clickByVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");
	}

	private void clickByVisibleText(final String... candidates) {
		for (String text : candidates) {
			final List<WebElement> elements = findVisibleElements(By.xpath("//*[normalize-space(.)='" + text + "']"));
			if (!elements.isEmpty()) {
				clickElementAndWait(elements.get(0));
				return;
			}
		}
		Assert.fail("Could not find clickable visible text from candidates: " + String.join(", ", candidates));
	}

	private void clickElementAndWait(final WebElement element) {
		scrollIntoView(element);
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiLoad();
	}

	private void requireVisibleAnyText(final String... candidates) {
		for (String text : candidates) {
			if (!findVisibleElements(By.xpath("//*[normalize-space(.)='" + text + "']")).isEmpty()) {
				return;
			}
		}
		Assert.fail("None of the required texts are visible: " + String.join(", ", candidates));
	}

	private void requireSidebarVisible() {
		final List<WebElement> sidebars = findVisibleElements(By.xpath("//aside | //nav[contains(@class, 'sidebar')]"));
		if (!sidebars.isEmpty()) {
			return;
		}
		requireVisibleAnyText("Negocio", "Mi Negocio");
	}

	private void scrollToVisibleText(final String text) {
		final WebElement element = findFirstVisibleByText(text);
		Assert.assertNotNull("Expected text not visible for scrolling: " + text, element);
		scrollIntoView(element);
		waitForUiLoad();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({behavior: 'instant', block: 'center', inline: 'nearest'});", element);
	}

	private WebElement findFirstVisibleByText(final String text) {
		final List<WebElement> matches = findVisibleElements(By.xpath("//*[normalize-space(.)='" + text + "']"));
		return matches.isEmpty() ? null : matches.get(0);
	}

	private WebElement findFirstVisibleByRegex(final String regex) {
		final Pattern pattern = Pattern.compile(regex);
		final List<WebElement> elements = findVisibleElements(By.xpath("//*[normalize-space(text()) != '']"));
		for (WebElement element : elements) {
			final String text = element.getText();
			if (text != null && pattern.matcher(text.trim()).matches()) {
				return element;
			}
		}
		return null;
	}

	private List<WebElement> findVisibleElements(final By by) {
		try {
			final List<WebElement> elements = driver.findElements(by);
			final List<WebElement> visible = new ArrayList<>();
			for (WebElement element : elements) {
				if (element != null && element.isDisplayed()) {
					visible.add(element);
				}
			}
			return visible;
		} catch (NoSuchElementException e) {
			return new ArrayList<>();
		}
	}

	private void captureScreenshot(final String name) throws IOException {
		final Path destination = evidenceDir.resolve(name + ".png");
		final Path source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureFullPageScreenshot(final String name) throws IOException {
		final JavascriptExecutor js = (JavascriptExecutor) driver;
		final Long fullHeight = (Long) js.executeScript("return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
		final Dimension original = driver.manage().window().getSize();

		if (fullHeight != null) {
			driver.manage().window().setSize(new Dimension(original.getWidth(), Math.min(fullHeight.intValue(), 5000)));
		}
		waitForUiLoad();
		captureScreenshot(name);
		driver.manage().window().setSize(original);
		waitForUiLoad();
	}

	private void waitForUiLoad() {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(350);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private void runStep(final String stepName, final CheckedRunnable stepAction) throws Exception {
		final StepResult result = new StepResult();
		results.put(stepName, result);
		try {
			stepAction.run();
			result.pass = true;
			result.details = "PASS";
		} catch (Throwable t) {
			result.pass = false;
			result.details = safeMessage(t);
			try {
				captureScreenshot("FAIL-" + sanitize(stepName));
			} catch (IOException ignored) {
				// Best effort evidence on failure.
			}
		}
	}

	private String buildFinalReport() {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Workflow - Final Report\n");
		sb.append("=========================================\n");

		appendReportLine(sb, "Login");
		appendReportLine(sb, "Mi Negocio menu");
		appendReportLine(sb, "Agregar Negocio modal");
		appendReportLine(sb, "Administrar Negocios view");
		appendReportLine(sb, "Información General");
		appendReportLine(sb, "Detalles de la Cuenta");
		appendReportLine(sb, "Tus Negocios");
		appendReportLine(sb, "Términos y Condiciones");
		appendReportLine(sb, "Política de Privacidad");
		return sb.toString();
	}

	private void appendReportLine(final StringBuilder sb, final String stepName) {
		final StepResult result = results.get(stepName);
		if (result == null) {
			sb.append("- ").append(stepName).append(": FAIL (not executed)\n");
			return;
		}

		sb.append("- ").append(stepName).append(": ").append(result.pass ? "PASS" : "FAIL");
		if (result.details != null && !result.details.isBlank()) {
			sb.append(" - ").append(result.details);
		}
		if (result.extra != null && !result.extra.isBlank()) {
			sb.append(" (").append(result.extra).append(")");
		}
		sb.append("\n");
	}

	private String value(final String systemProperty, final String envVar, final String defaultValue) {
		final String fromProperty = System.getProperty(systemProperty);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}

		final String fromEnv = System.getenv(envVar);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		return defaultValue;
	}

	private String sanitize(final String text) {
		return text.replaceAll("[^a-zA-Z0-9_-]", "_");
	}

	private String safeMessage(final Throwable t) {
		if (t == null) {
			return "Unknown error";
		}
		final String message = t.getMessage();
		if (message == null || message.isBlank()) {
			return t.getClass().getSimpleName();
		}
		return t.getClass().getSimpleName() + ": " + message.replace('\n', ' ');
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static class StepResult {
		private boolean pass;
		private String details;
		private String extra;
	}
}
