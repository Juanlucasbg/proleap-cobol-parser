package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * SaleADS.ai E2E workflow for Mi Negocio.
 *
 * <p>
 * To avoid hardcoded domains, this test requires the login page URL to be
 * provided externally:
 * </p>
 * <ul>
 * <li>JVM property: {@code -Dsaleads.login.url=https://...}</li>
 * <li>Env var: {@code SALEADS_LOGIN_URL=https://...}</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowE2ETest {

	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();
	private final Path evidenceDir = Paths.get(
			"target",
			"saleads-mi-negocio-evidence",
			LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));

	private Page appPage;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		initializeReport();

		final String loginUrl = resolveLoginUrl();
		Assume.assumeTrue(
				"Skipping SaleADS E2E: set -Dsaleads.login.url or SALEADS_LOGIN_URL.",
				loginUrl != null && !loginUrl.isBlank());

		Files.createDirectories(evidenceDir);

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
			final BrowserContext context = browser.newContext();
			appPage = context.newPage();

			appPage.navigate(loginUrl);
			waitForUi(appPage);

			executeStep("Login", this::stepLoginWithGoogle);
			executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			executeStep("Información General", this::stepValidateInformacionGeneral);
			executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			executeStep("Tus Negocios", this::stepValidateTusNegocios);
			executeStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
			executeStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

			final String finalReport = buildFinalReport();
			writeFinalReport(finalReport);
			System.out.println(finalReport);

			assertTrue("SaleADS Mi Negocio workflow has failing validations:\n" + finalReport, allStepsPassed());
		}
	}

	private void stepLoginWithGoogle() {
		final Page authPage = clickForOptionalNewTab(
				appPage,
				"Google login button",
				"text=/Sign in with Google|Iniciar sesión con Google|Continuar con Google|Google/i",
				"button:has-text('Google')",
				"[role='button']:has-text('Google')",
				"a:has-text('Google')");

		selectGoogleAccountIfVisible(authPage);
		waitForUi(authPage);
		waitForUi(appPage);

		assertAnyVisible(appPage, "Main app interface", "main", "[role='main']", "text=/Dashboard|Panel|Inicio/i");
		assertAnyVisible(appPage, "Left sidebar navigation", "aside", "nav", "text=/Mi Negocio|Negocio/i");

		takeScreenshot(appPage, "01-dashboard-loaded", true);
	}

	private void stepOpenMiNegocioMenu() {
		clickIfVisible(appPage, "Negocio section", "text=/^Negocio$/i", "text=/Negocio/i");
		clickFirstVisible(appPage, "Mi Negocio", "text=/^Mi Negocio$/i", "text=/Mi Negocio/i");

		assertAnyVisible(appPage, "Agregar Negocio option", "text=/^Agregar Negocio$/i", "text=/Agregar Negocio/i");
		assertAnyVisible(appPage, "Administrar Negocios option",
				"text=/^Administrar Negocios$/i",
				"text=/Administrar Negocios/i");
		takeScreenshot(appPage, "02-mi-negocio-menu-expanded", true);
	}

	private void stepValidateAgregarNegocioModal() {
		clickFirstVisible(appPage, "Agregar Negocio", "text=/^Agregar Negocio$/i", "text=/Agregar Negocio/i");
		assertAnyVisible(appPage, "Crear Nuevo Negocio modal", "text=/Crear Nuevo Negocio/i");
		assertAnyVisible(appPage, "Nombre del Negocio input",
				"input[placeholder*='Nombre del Negocio']",
				"input[name*='negocio' i]",
				"div[role='dialog'] input");
		assertAnyVisible(appPage, "Tienes 2 de 3 negocios text", "text=/Tienes\\s*2\\s*de\\s*3\\s*negocios/i");
		assertAnyVisible(appPage, "Cancelar button", "button:has-text('Cancelar')");
		assertAnyVisible(appPage, "Crear Negocio button", "button:has-text('Crear Negocio')");

		fillFirstVisible(appPage, "Negocio Prueba Automatización",
				"input[placeholder*='Nombre del Negocio']",
				"div[role='dialog'] input");
		takeScreenshot(appPage, "03-crear-nuevo-negocio-modal", true);
		clickFirstVisible(appPage, "Cancelar", "button:has-text('Cancelar')");
	}

	private void stepOpenAdministrarNegocios() {
		if (!isAnyVisible(appPage, 3000, "text=/Administrar Negocios/i")) {
			clickIfVisible(appPage, "Mi Negocio", "text=/Mi Negocio/i");
		}

		clickFirstVisible(appPage, "Administrar Negocios", "text=/^Administrar Negocios$/i", "text=/Administrar Negocios/i");
		assertAnyVisible(appPage, "Información General section", "text=/Información General/i");
		assertAnyVisible(appPage, "Detalles de la Cuenta section", "text=/Detalles de la Cuenta/i");
		assertAnyVisible(appPage, "Tus Negocios section", "text=/Tus Negocios/i");
		assertAnyVisible(appPage, "Sección Legal section", "text=/Sección Legal/i");
		takeScreenshot(appPage, "04-administrar-negocios", true);
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisible(appPage, "User name",
				"text=/Juan\\s+Lucas|Juan|Lucas|Barbier|Garzon/i",
				"[data-testid*='name' i]",
				"[class*='name' i]");
		assertAnyVisible(appPage, "User email",
				"text=/juanlucasbarbiergarzon@gmail\\.com/i",
				"text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/");
		assertAnyVisible(appPage, "BUSINESS PLAN text", "text=/BUSINESS PLAN/i");
		assertAnyVisible(appPage, "Cambiar Plan button", "button:has-text('Cambiar Plan')", "text=/Cambiar Plan/i");
	}

	private void stepValidateDetallesCuenta() {
		assertAnyVisible(appPage, "Cuenta creada", "text=/Cuenta creada/i");
		assertAnyVisible(appPage, "Estado activo", "text=/Estado activo/i");
		assertAnyVisible(appPage, "Idioma seleccionado", "text=/Idioma seleccionado/i");
	}

	private void stepValidateTusNegocios() {
		assertAnyVisible(appPage, "Business list",
				"section:has-text('Tus Negocios') li",
				"section:has-text('Tus Negocios') div:has-text('Negocio')",
				"text=/Tus Negocios/i");
		assertAnyVisible(appPage, "Agregar Negocio button", "button:has-text('Agregar Negocio')", "text=/Agregar Negocio/i");
		assertAnyVisible(appPage, "Tienes 2 de 3 negocios text", "text=/Tienes\\s*2\\s*de\\s*3\\s*negocios/i");
	}

	private void stepValidateTerminosYCondiciones() {
		final String url = validateLegalLink("Términos y Condiciones", "08-terminos-y-condiciones");
		setStepDetails("Términos y Condiciones", "URL: " + url);
	}

	private void stepValidatePoliticaPrivacidad() {
		final String url = validateLegalLink("Política de Privacidad", "09-politica-privacidad");
		setStepDetails("Política de Privacidad", "URL: " + url);
	}

	private String validateLegalLink(final String linkText, final String screenshotName) {
		final String linkSelector = textRegexSelector(linkText);
		final Page targetPage = clickForOptionalNewTab(
				appPage,
				linkText,
				linkSelector,
				"a:has-text('" + linkText + "')");

		waitForUi(targetPage);
		assertAnyVisible(targetPage,
				"Legal heading for " + linkText,
				"h1:has-text('" + linkText + "')",
				"h2:has-text('" + linkText + "')",
				linkSelector);
		assertAnyVisible(targetPage, "Legal content text", "p", "article", "main");

		takeScreenshot(targetPage, screenshotName, true);
		final String finalUrl = targetPage.url();

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack(new Page.GoBackOptions().setTimeout(20000));
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void executeStep(final String field, final StepAction action) {
		try {
			action.run();
			report.put(field, StepResult.pass(stepDetails.getOrDefault(field, "")));
		} catch (final Throwable throwable) {
			final String failureDetail = mergeDetails(stepDetails.get(field), shortError(throwable));
			report.put(field, StepResult.fail(failureDetail));
			takeFailureScreenshot(field);
		}
	}

	private void initializeReport() {
		report.clear();
		stepDetails.clear();
		for (final String field : REPORT_FIELDS) {
			report.put(field, StepResult.fail("Not executed."));
		}
	}

	private void setStepDetails(final String field, final String details) {
		if (details != null && !details.isBlank()) {
			stepDetails.put(field, details);
		}
	}

	private boolean allStepsPassed() {
		return report.values().stream().allMatch(result -> result.status.equals("PASS"));
	}

	private String buildFinalReport() {
		final String lines = report.entrySet().stream()
				.map(entry -> String.format(Locale.ROOT,
						"- %s: %s%s",
						entry.getKey(),
						entry.getValue().status,
						entry.getValue().details.isBlank() ? "" : " (" + entry.getValue().details + ")"))
				.collect(Collectors.joining(System.lineSeparator()));
		return "SaleADS Mi Negocio - Final Report" + System.lineSeparator() + lines;
	}

	private void writeFinalReport(final String reportContent) throws IOException {
		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, reportContent, StandardCharsets.UTF_8);
	}

	private void takeFailureScreenshot(final String stepName) {
		if (appPage != null) {
			takeScreenshot(appPage, "failure-" + sanitizeName(stepName), true);
		}
	}

	private void takeScreenshot(final Page page, final String name, final boolean fullPage) {
		try {
			Files.createDirectories(evidenceDir);
			final Path screenshot = evidenceDir.resolve(sanitizeName(name) + ".png");
			page.screenshot(new Page.ScreenshotOptions().setPath(screenshot).setFullPage(fullPage));
		} catch (final Exception ignored) {
			// Do not fail the test because of evidence capture issues.
		}
	}

	private String resolveLoginUrl() {
		final String propertyValue = System.getProperty("saleads.login.url");
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		final String envValue = System.getenv("SALEADS_LOGIN_URL");
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		return null;
	}

	private String textRegexSelector(final String text) {
		return "text=/" + Pattern.quote(text) + "/i";
	}

	private Page clickForOptionalNewTab(final Page sourcePage, final String description, final String... selectors) {
		final int beforeCount = sourcePage.context().pages().size();
		clickFirstVisibleWithoutWaiting(sourcePage, description, selectors);
		waitForUi(sourcePage);

		final long deadline = System.currentTimeMillis() + 7000;
		while (System.currentTimeMillis() < deadline) {
			final List<Page> pages = sourcePage.context().pages();
			if (pages.size() > beforeCount) {
				final Page newest = pages.get(pages.size() - 1);
				if (newest != sourcePage) {
					waitForUi(newest);
					return newest;
				}
			}
			sourcePage.waitForTimeout(250);
		}

		return sourcePage;
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		if (isAnyVisible(page, 7000,
				"text=/juanlucasbarbiergarzon@gmail\\.com/i",
				"div:has-text('juanlucasbarbiergarzon@gmail.com')")) {
			clickFirstVisible(page,
					"Google account selector",
					"text=/juanlucasbarbiergarzon@gmail\\.com/i",
					"div:has-text('juanlucasbarbiergarzon@gmail.com')");
		}
	}

	private void clickFirstVisible(final Page page, final String description, final String... selectors) {
		final Locator locator = firstVisibleLocator(page, 30000, selectors);
		try {
			locator.click();
		} catch (final PlaywrightException clickError) {
			throw new AssertionError("Could not click " + description + ". " + clickError.getMessage(), clickError);
		}
		waitForUi(page);
	}

	private void clickFirstVisibleWithoutWaiting(final Page page, final String description, final String... selectors) {
		final Locator locator = firstVisibleLocator(page, 12000, selectors);
		try {
			locator.click();
		} catch (final PlaywrightException clickError) {
			throw new AssertionError("Could not click " + description + ". " + clickError.getMessage(), clickError);
		}
	}

	private void clickIfVisible(final Page page, final String description, final String... selectors) {
		if (isAnyVisible(page, 5000, selectors)) {
			clickFirstVisible(page, description, selectors);
		}
	}

	private void fillFirstVisible(final Page page, final String value, final String... selectors) {
		final Locator locator = firstVisibleLocator(page, 15000, selectors);
		locator.fill(value);
		waitForUi(page);
	}

	private Locator firstVisibleLocator(final Page page, final long timeoutMillis, final String... selectors) {
		final long deadline = System.currentTimeMillis() + timeoutMillis;
		PlaywrightException lastError = null;

		while (System.currentTimeMillis() < deadline) {
			for (final String selector : selectors) {
				final Locator locator = page.locator(selector).first();
				try {
					locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(750));
					return locator;
				} catch (final PlaywrightException e) {
					lastError = e;
				}
			}
			page.waitForTimeout(250);
		}

		throw new AssertionError("No visible element found for selectors: " + String.join(" | ", selectors), lastError);
	}

	private void assertAnyVisible(final Page page, final String label, final String... selectors) {
		assertTrue(
				"Expected visible element for " + label + ". Selectors: " + String.join(" | ", selectors),
				isAnyVisible(page, 25000, selectors));
	}

	private boolean isAnyVisible(final Page page, final long timeoutMillis, final String... selectors) {
		final long deadline = System.currentTimeMillis() + timeoutMillis;
		while (System.currentTimeMillis() < deadline) {
			for (final String selector : selectors) {
				try {
					if (page.locator(selector).first().isVisible()) {
						return true;
					}
				} catch (final PlaywrightException ignored) {
					// Keep evaluating fallback selectors.
				}
			}
			page.waitForTimeout(250);
		}
		return false;
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (final PlaywrightException ignored) {
		}
		page.waitForTimeout(500);
	}

	private String shortError(final Throwable throwable) {
		final String message = throwable.getMessage();
		if (message == null || message.isBlank()) {
			return throwable.getClass().getSimpleName();
		}
		return message;
	}

	private String mergeDetails(final String left, final String right) {
		if (left == null || left.isBlank()) {
			return right == null ? "" : right;
		}
		if (right == null || right.isBlank()) {
			return left;
		}
		return left + " | " + right;
	}

	private String sanitizeName(final String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = Objects.requireNonNull(status, "status");
			this.details = details == null ? "" : details;
		}

		private static StepResult pass(final String details) {
			return new StepResult("PASS", details);
		}

		private static StepResult fail(final String details) {
			return new StepResult("FAIL", details);
		}
	}
}
