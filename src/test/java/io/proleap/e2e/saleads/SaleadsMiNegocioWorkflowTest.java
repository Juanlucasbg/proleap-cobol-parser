package io.proleap.e2e.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioWorkflowTest {

	private static final long ELEMENT_TIMEOUT_MS = 15_000;
	private static final long PAGE_TIMEOUT_MS = 20_000;
	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final List<String> screenshotPaths = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private final Path outputDir = Paths.get("target", "saleads-mi-negocio");
	private final Path screenshotDir = outputDir.resolve("screenshots");

	private Page appPage;

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final boolean enabled = Boolean.parseBoolean(readConfig("SALEADS_E2E_ENABLED", "saleads.e2e.enabled", "false"));
		Assume.assumeTrue("Skipping SaleADS E2E workflow. Set SALEADS_E2E_ENABLED=true to run it.", enabled);

		Files.createDirectories(screenshotDir);

		try (Playwright playwright = Playwright.create()) {
			final Session session = createSession(playwright);
			appPage = session.page;

			try {
				executeStep("Login", () -> loginWithGoogle(session.context));
				executeStep("Mi Negocio menu", this::openMiNegocioMenu);
				executeStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
				executeStep("Administrar Negocios view", this::openAdministrarNegocios);
				executeStep("Información General", this::validateInformacionGeneral);
				executeStep("Detalles de la Cuenta", this::validateDetallesCuenta);
				executeStep("Tus Negocios", this::validateTusNegocios);
				executeStep("Términos y Condiciones", () -> validateLegalLink("Términos y Condiciones",
						"terminos-condiciones", "(?i)t[eé]rminos\\s+y\\s+condiciones"));
				executeStep("Política de Privacidad", () -> validateLegalLink("Política de Privacidad", "politica-privacidad",
						"(?i)pol[ií]tica\\s+de\\s+privacidad"));
			} finally {
				session.browser.close();
			}
		} finally {
			finalizeAndWriteReport();
		}

		final List<String> failures = new ArrayList<>();
		for (String reportField : REPORT_FIELDS) {
			final StepResult result = stepResults.get(reportField);
			if (result == null || !result.passed) {
				failures.add(reportField + (result == null ? " (not executed)" : " (" + result.details + ")"));
			}
		}
		Assert.assertTrue("SaleADS workflow validation failures: " + failures, failures.isEmpty());
	}

	private Session createSession(final Playwright playwright) {
		final String cdpUrl = readConfig("SALEADS_CDP_URL", "saleads.cdp.url", "");
		final String loginUrl = readConfig("SALEADS_LOGIN_URL", "saleads.login.url", "");
		final boolean headless = Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "saleads.headless", "true"));

		if (!cdpUrl.isBlank()) {
			final Browser browser = playwright.chromium().connectOverCDP(cdpUrl);
			final BrowserContext context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
			final Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);

			if ("about:blank".equals(page.url()) && loginUrl.isBlank()) {
				Assert.fail(
						"CDP session page is blank. Open the SaleADS login page first, or provide SALEADS_LOGIN_URL/saleads.login.url.");
			}
			if ("about:blank".equals(page.url()) && !loginUrl.isBlank()) {
				page.navigate(loginUrl);
				waitForUi(page);
			}
			return new Session(browser, context, page);
		}

		if (loginUrl.isBlank()) {
			Assert.fail(
					"No start page configured. Set SALEADS_LOGIN_URL/saleads.login.url, or use SALEADS_CDP_URL with an already open SaleADS login page.");
		}

		final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
		final Page page = context.newPage();
		page.navigate(loginUrl);
		waitForUi(page);
		return new Session(browser, context, page);
	}

	private void loginWithGoogle(final BrowserContext context) {
		final Locator loginButton = findVisible(appPage, "Google login button", "button:has-text('Google')",
				"a:has-text('Google')", "[role='button']:has-text('Google')", "text='Sign in with Google'",
				"text='Iniciar sesión con Google'", "text='Iniciar sesion con Google'");

		final int pageCountBefore = context.pages().size();
		clickAndWait(appPage, loginButton);

		final Page authPage = resolveTargetPage(context, pageCountBefore);
		selectGoogleAccountIfVisible(authPage);
		waitForApplicationInterface(context);

		Assert.assertTrue("Left sidebar navigation was not visible after login.", isSidebarVisible(appPage));
		captureScreenshot(appPage, "01-dashboard-loaded", false);
	}

	private void selectGoogleAccountIfVisible(final Page authPage) {
		final Locator accountOption = authPage.locator("text='" + ACCOUNT_EMAIL + "'").first();
		if (isVisible(accountOption, 8_000)) {
			clickAndWait(authPage, accountOption);
		}
	}

	private void waitForApplicationInterface(final BrowserContext context) {
		final long deadline = System.currentTimeMillis() + 60_000;
		while (System.currentTimeMillis() < deadline) {
			for (Page page : context.pages()) {
				if (isSidebarVisible(page)) {
					appPage = page;
					page.bringToFront();
					waitForUi(page);
					return;
				}
			}
			appPage.waitForTimeout(1_000);
		}
		Assert.fail("Main application interface did not appear after Google login.");
	}

	private void openMiNegocioMenu() {
		final Locator negocio = findVisible(appPage, "Negocio section", "[role='navigation'] :text('Negocio')",
				"aside :text('Negocio')", "nav :text('Negocio')", "text='Negocio'");
		clickAndWait(appPage, negocio);

		final Locator miNegocio = findVisible(appPage, "Mi Negocio option", "a:has-text('Mi Negocio')",
				"button:has-text('Mi Negocio')", "[role='menuitem']:has-text('Mi Negocio')", "text='Mi Negocio'");
		clickAndWait(appPage, miNegocio);

		Assert.assertTrue("Expected 'Agregar Negocio' to be visible.",
				anyVisible(appPage, "a:has-text('Agregar Negocio')", "button:has-text('Agregar Negocio')",
						"text='Agregar Negocio'"));
		Assert.assertTrue("Expected 'Administrar Negocios' to be visible.",
				anyVisible(appPage, "a:has-text('Administrar Negocios')", "button:has-text('Administrar Negocios')",
						"text='Administrar Negocios'"));

		captureScreenshot(appPage, "02-mi-negocio-menu-expanded", false);
	}

	private void validateAgregarNegocioModal() {
		final Locator agregarNegocio = findVisible(appPage, "Agregar Negocio action", "a:has-text('Agregar Negocio')",
				"button:has-text('Agregar Negocio')", "text='Agregar Negocio'");
		clickAndWait(appPage, agregarNegocio);

		Assert.assertTrue("Modal title 'Crear Nuevo Negocio' was not visible.", anyVisible(appPage,
				"[role='dialog'] :text('Crear Nuevo Negocio')", "text='Crear Nuevo Negocio'"));
		final Locator nombreNegocioInput = findVisible(appPage, "Nombre del Negocio input",
				"[role='dialog'] input[placeholder*='Nombre del Negocio']", "[role='dialog'] input[name*='nombre']",
				"[role='dialog'] input");

		Assert.assertTrue("Expected text 'Tienes 2 de 3 negocios' in modal.",
				anyVisible(appPage, "[role='dialog'] :text('Tienes 2 de 3 negocios')", "text='Tienes 2 de 3 negocios'"));
		Assert.assertTrue("Expected 'Cancelar' button in modal.",
				anyVisible(appPage, "[role='dialog'] button:has-text('Cancelar')", "button:has-text('Cancelar')"));
		Assert.assertTrue("Expected 'Crear Negocio' button in modal.",
				anyVisible(appPage, "[role='dialog'] button:has-text('Crear Negocio')", "button:has-text('Crear Negocio')"));

		nombreNegocioInput.fill("Negocio Prueba Automatización");
		final Locator cancelar = findVisible(appPage, "Cancelar button", "[role='dialog'] button:has-text('Cancelar')",
				"button:has-text('Cancelar')");
		captureScreenshot(appPage, "03-agregar-negocio-modal", false);
		clickAndWait(appPage, cancelar);
	}

	private void openAdministrarNegocios() {
		if (!anyVisible(appPage, "a:has-text('Administrar Negocios')", "button:has-text('Administrar Negocios')",
				"text='Administrar Negocios'")) {
			final Locator miNegocio = findVisible(appPage, "Mi Negocio option", "a:has-text('Mi Negocio')",
					"button:has-text('Mi Negocio')", "text='Mi Negocio'");
			clickAndWait(appPage, miNegocio);
		}

		final Locator administrar = findVisible(appPage, "Administrar Negocios option", "a:has-text('Administrar Negocios')",
				"button:has-text('Administrar Negocios')", "text='Administrar Negocios'");
		clickAndWait(appPage, administrar);

		assertTextVisible(appPage, "Información General");
		assertTextVisible(appPage, "Detalles de la Cuenta");
		assertTextVisible(appPage, "Tus Negocios");
		assertTextVisible(appPage, "Sección Legal");

		captureScreenshot(appPage, "04-administrar-negocios-vista", true);
	}

	private void validateInformacionGeneral() {
		assertTextVisible(appPage, "Información General");
		final String pageText = normalizedPageText(appPage);

		Assert.assertTrue("User name is not visible in account view.", Pattern.compile("(?i)juan").matcher(pageText).find());
		Assert.assertTrue("User email is not visible in account view.", pageText.contains(ACCOUNT_EMAIL));
		assertTextVisible(appPage, "BUSINESS PLAN");
		Assert.assertTrue("Button 'Cambiar Plan' was not visible.",
				anyVisible(appPage, "button:has-text('Cambiar Plan')", "a:has-text('Cambiar Plan')", "text='Cambiar Plan'"));
	}

	private void validateDetallesCuenta() {
		assertTextVisible(appPage, "Cuenta creada");
		assertTextVisible(appPage, "Estado activo");
		assertTextVisible(appPage, "Idioma seleccionado");
	}

	private void validateTusNegocios() {
		assertTextVisible(appPage, "Tus Negocios");
		Assert.assertTrue("Button 'Agregar Negocio' was not visible in 'Tus Negocios'.",
				anyVisible(appPage, "section:has-text('Tus Negocios') button:has-text('Agregar Negocio')",
						"section:has-text('Tus Negocios') a:has-text('Agregar Negocio')", "button:has-text('Agregar Negocio')"));
		assertTextVisible(appPage, "Tienes 2 de 3 negocios");

		final boolean hasBusinessList = anyVisible(appPage, "section:has-text('Tus Negocios') li",
				"section:has-text('Tus Negocios') tr", "section:has-text('Tus Negocios') [role='listitem']");
		Assert.assertTrue("Business list is not visible in 'Tus Negocios'.", hasBusinessList);
	}

	private void validateLegalLink(final String linkText, final String screenshotSlug, final String headingRegex) {
		final int pageCountBefore = appPage.context().pages().size();
		final String appUrlBefore = appPage.url();

		final Locator legalLink = findVisible(appPage, linkText + " link", "a:has-text('" + linkText + "')",
				"button:has-text('" + linkText + "')", "text='" + linkText + "'");
		clickAndWait(appPage, legalLink);

		final Page targetPage = resolveTargetPage(appPage.context(), pageCountBefore);
		waitForUi(targetPage);

		assertRegexVisible(targetPage, headingRegex);
		final String legalBodyText = normalizedPageText(targetPage);
		Assert.assertTrue("Legal content text was not visible for " + linkText + ".",
				legalBodyText != null && legalBodyText.length() > 120);

		legalUrls.put(linkText, targetPage.url());
		captureScreenshot(targetPage, "05-" + screenshotSlug, true);

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!appPage.url().equals(appUrlBefore)) {
			appPage.goBack();
			waitForUi(appPage);
		}
	}

	private Page resolveTargetPage(final BrowserContext context, final int pageCountBefore) {
		if (context.pages().size() > pageCountBefore) {
			final Page newPage = context.pages().get(context.pages().size() - 1);
			newPage.bringToFront();
			return newPage;
		}
		return appPage;
	}

	private void executeStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, StepResult.pass());
		} catch (Throwable throwable) {
			stepResults.put(stepName, StepResult.fail(firstLine(throwable.getMessage())));
		}
	}

	private void finalizeAndWriteReport() throws IOException {
		for (String reportField : REPORT_FIELDS) {
			if (!stepResults.containsKey(reportField)) {
				stepResults.put(reportField, StepResult.fail("Step was not executed."));
			}
		}

		Files.createDirectories(outputDir);
		final String report = buildReportMarkdown();
		final Path reportPath = outputDir.resolve("report.md");
		Files.writeString(reportPath, report, StandardCharsets.UTF_8);
		System.out.println(report);
		System.out.println("SaleADS report path: " + reportPath.toAbsolutePath());
	}

	private String buildReportMarkdown() {
		final StringBuilder builder = new StringBuilder();
		builder.append("# SaleADS Mi Negocio - Final Report\n\n");
		builder.append("| Validation | Status | Details |\n");
		builder.append("|---|---|---|\n");
		for (String reportField : REPORT_FIELDS) {
			final StepResult result = stepResults.get(reportField);
			builder.append("| ").append(reportField).append(" | ").append(result.passed ? "PASS" : "FAIL").append(" | ")
					.append(result.details).append(" |\n");
		}

		builder.append("\n## Evidence\n");
		if (screenshotPaths.isEmpty()) {
			builder.append("- No screenshots were captured.\n");
		} else {
			for (String screenshotPath : screenshotPaths) {
				builder.append("- ").append(screenshotPath).append("\n");
			}
		}

		builder.append("\n## Final URLs\n");
		if (legalUrls.isEmpty()) {
			builder.append("- Legal URLs were not captured.\n");
		} else {
			for (Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
				builder.append("- ").append(legalUrl.getKey()).append(": ").append(legalUrl.getValue()).append("\n");
			}
		}

		return builder.toString();
	}

	private void clickAndWait(final Page page, final Locator locator) {
		try {
			locator.scrollIntoViewIfNeeded();
		} catch (PlaywrightException ignored) {
			// best effort scrolling
		}
		locator.click();
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(PAGE_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// not all clicks trigger navigation
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8_000));
		} catch (PlaywrightException ignored) {
			// network may remain active in SPA pages
		}
		page.waitForTimeout(400);
	}

	private Locator findVisible(final Page page, final String description, final String... selectors) {
		for (String selector : selectors) {
			try {
				final Locator locator = page.locator(selector).first();
				if (locator.count() == 0) {
					continue;
				}
				if (isVisible(locator, ELEMENT_TIMEOUT_MS)) {
					return locator;
				}
			} catch (PlaywrightException ignored) {
				// try next selector
			}
		}
		Assert.fail("Could not find visible element for: " + description);
		return null;
	}

	private boolean anyVisible(final Page page, final String... selectors) {
		for (String selector : selectors) {
			try {
				final Locator locator = page.locator(selector).first();
				if (locator.count() > 0 && isVisible(locator, 2_000)) {
					return true;
				}
			} catch (PlaywrightException ignored) {
				// try next selector
			}
		}
		return false;
	}

	private boolean isVisible(final Locator locator, final long timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private boolean isSidebarVisible(final Page page) {
		return anyVisible(page, "aside", "nav:has-text('Negocio')", "text='Negocio'");
	}

	private void assertTextVisible(final Page page, final String text) {
		final Locator textLocator = page.locator("text='" + text + "'").first();
		Assert.assertTrue("Expected text not visible: " + text, isVisible(textLocator, ELEMENT_TIMEOUT_MS));
	}

	private void assertRegexVisible(final Page page, final String regex) {
		final Pattern pattern = Pattern.compile(regex);
		final String bodyText = normalizedPageText(page);
		Assert.assertTrue("Expected text matching regex was not visible: " + regex, pattern.matcher(bodyText).find());
	}

	private String normalizedPageText(final Page page) {
		final String text = page.locator("body").innerText();
		return text == null ? "" : text.replaceAll("\\s+", " ").trim();
	}

	private void captureScreenshot(final Page page, final String checkpointName, final boolean fullPage) {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path screenshotPath = screenshotDir.resolve(timestamp + "-" + checkpointName + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		screenshotPaths.add(screenshotPath.toString());
	}

	private static String readConfig(final String envKey, final String propertyKey, final String defaultValue) {
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		return defaultValue;
	}

	private static String firstLine(final String input) {
		if (input == null || input.isBlank()) {
			return "Validation failed.";
		}
		final int newLineIndex = input.indexOf('\n');
		return newLineIndex < 0 ? input.trim() : input.substring(0, newLineIndex).trim();
	}

	private interface StepAction {
		void run();
	}

	private static final class Session {
		private final Browser browser;
		private final BrowserContext context;
		private final Page page;

		private Session(final Browser browser, final BrowserContext context, final Page page) {
			this.browser = browser;
			this.context = context;
			this.page = page;
		}
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, "All validations passed.");
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details == null || details.isBlank() ? "Validation failed." : details);
		}
	}
}
