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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final double DEFAULT_TIMEOUT_MS = 30000;
	private static final double SHORT_TIMEOUT_MS = 4000;

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String STEP_INFORMACION_GENERAL = "Información General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Política de Privacidad";

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		Assume.assumeTrue(
				"Enable with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true",
				readBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false));

		final Path evidenceDir = Paths.get("target", "saleads-evidence", "saleads_mi_negocio_full_test");
		Files.createDirectories(evidenceDir);

		final Map<String, String> results = new LinkedHashMap<>();
		results.put(STEP_LOGIN, "NOT_RUN");
		results.put(STEP_MI_NEGOCIO_MENU, "NOT_RUN");
		results.put(STEP_AGREGAR_NEGOCIO_MODAL, "NOT_RUN");
		results.put(STEP_ADMINISTRAR_NEGOCIOS, "NOT_RUN");
		results.put(STEP_INFORMACION_GENERAL, "NOT_RUN");
		results.put(STEP_DETALLES_CUENTA, "NOT_RUN");
		results.put(STEP_TUS_NEGOCIOS, "NOT_RUN");
		results.put(STEP_TERMINOS, "NOT_RUN");
		results.put(STEP_PRIVACIDAD, "NOT_RUN");

		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final String[] activePage = new String[1];

		try (Playwright playwright = Playwright.create()) {
			final boolean headless = readBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true);
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions()
					.setViewportSize(1440, 1024));
			final Page[] appPage = new Page[] { context.newPage() };

			navigateIfConfigured(appPage[0]);

			executeStep(results, STEP_LOGIN, () -> {
				appPage[0] = performLoginAndValidateDashboard(appPage[0], context);
				captureScreenshot(appPage[0], evidenceDir, "01-dashboard-loaded", true);
				activePage[0] = appPage[0].url();
			});

			executeStep(results, STEP_MI_NEGOCIO_MENU, () -> {
				openMiNegocioMenu(appPage[0]);
				assertTextVisible(appPage[0], "Agregar Negocio");
				assertTextVisible(appPage[0], "Administrar Negocios");
				captureScreenshot(appPage[0], evidenceDir, "02-mi-negocio-menu-expanded", false);
			});

			executeStep(results, STEP_AGREGAR_NEGOCIO_MODAL, () -> {
				clickVisibleText(appPage[0], "Agregar Negocio");
				assertAnyTextVisible(appPage[0], "Crear Nuevo Negocio");
				assertAnyTextVisible(appPage[0], "Nombre del Negocio");
				assertAnyTextVisible(appPage[0], "Tienes 2 de 3 negocios");
				assertAnyTextVisible(appPage[0], "Cancelar");
				assertAnyTextVisible(appPage[0], "Crear Negocio");
				captureScreenshot(appPage[0], evidenceDir, "03-agregar-negocio-modal", false);

				final Locator input = waitForFirstVisible(appPage[0], DEFAULT_TIMEOUT_MS,
						"input[placeholder*='Nombre del Negocio']",
						"input[name*='nombre']",
						"input[aria-label*='Nombre del Negocio']",
						"xpath=//input[contains(@placeholder,'Nombre del Negocio')]");
				if (input != null) {
					input.fill("Negocio Prueba Automatización");
				}

				clickVisibleText(appPage[0], "Cancelar");
			});

			executeStep(results, STEP_ADMINISTRAR_NEGOCIOS, () -> {
				ensureMiNegocioSubmenuExpanded(appPage[0]);
				clickVisibleText(appPage[0], "Administrar Negocios");
				assertAnyTextVisible(appPage[0], "Información General");
				assertAnyTextVisible(appPage[0], "Detalles de la Cuenta");
				assertAnyTextVisible(appPage[0], "Tus Negocios");
				assertAnyTextVisible(appPage[0], "Sección Legal");
				captureScreenshot(appPage[0], evidenceDir, "04-administrar-negocios-view", true);
			});

			executeStep(results, STEP_INFORMACION_GENERAL, () -> {
				assertAnyTextVisible(appPage[0], "BUSINESS PLAN");
				assertAnyTextVisible(appPage[0], "Cambiar Plan");
				assertVisibleBySelectors(appPage[0],
						"xpath=//*[contains(@class,'user') and string-length(normalize-space()) > 1]",
						"xpath=//*[contains(normalize-space(.), '@')]");
			});

			executeStep(results, STEP_DETALLES_CUENTA, () -> {
				assertAnyTextVisible(appPage[0], "Cuenta creada");
				assertAnyTextVisible(appPage[0], "Estado activo");
				assertAnyTextVisible(appPage[0], "Idioma seleccionado");
			});

			executeStep(results, STEP_TUS_NEGOCIOS, () -> {
				assertAnyTextVisible(appPage[0], "Tus Negocios");
				assertAnyTextVisible(appPage[0], "Agregar Negocio");
				assertAnyTextVisible(appPage[0], "Tienes 2 de 3 negocios");
				assertVisibleBySelectors(appPage[0],
						"xpath=//ul[.//li]",
						"xpath=//table",
						"xpath=//*[contains(@class,'business')]");
			});

			executeStep(results, STEP_TERMINOS, () -> {
				final NavigationOutcome outcome = openLinkAndCaptureTargetPage(appPage[0], context, "Términos y Condiciones");
				final Page legalPage = outcome.targetPage;
				assertAnyTextVisible(legalPage, "Términos y Condiciones", "Terminos y Condiciones");
				assertLegalContentVisible(legalPage);
				legalUrls.put("Términos y Condiciones URL", legalPage.url());
				captureScreenshot(legalPage, evidenceDir, "05-terminos-y-condiciones", true);
				restoreApplicationPage(appPage[0], outcome);
			});

			executeStep(results, STEP_PRIVACIDAD, () -> {
				final NavigationOutcome outcome = openLinkAndCaptureTargetPage(appPage[0], context, "Política de Privacidad");
				final Page legalPage = outcome.targetPage;
				assertAnyTextVisible(legalPage, "Política de Privacidad", "Politica de Privacidad");
				assertLegalContentVisible(legalPage);
				legalUrls.put("Política de Privacidad URL", legalPage.url());
				captureScreenshot(legalPage, evidenceDir, "06-politica-de-privacidad", true);
				restoreApplicationPage(appPage[0], outcome);
			});
		} finally {
			writeFinalReport(evidenceDir, results, legalUrls, activePage[0]);
		}

		final String failures = results.entrySet().stream()
				.filter(entry -> !"PASS".equals(entry.getValue()))
				.map(entry -> entry.getKey() + ": " + entry.getValue())
				.collect(Collectors.joining("\n"));

		Assert.assertTrue("SaleADS Mi Negocio workflow validation failed:\n" + failures, failures.isEmpty());
	}

	private Page performLoginAndValidateDashboard(final Page initialPage, final BrowserContext context) {
		Page page = initialPage;
		waitForUiToSettle(page);

		Locator loginButton = waitForFirstVisible(page, SHORT_TIMEOUT_MS,
				"text=/Sign in with Google|Iniciar sesión con Google|Iniciar sesion con Google|Continuar con Google|Google/i",
				"text=Sign in with Google",
				"text=Iniciar sesión con Google",
				"text=Continuar con Google");

		if (loginButton != null) {
			final NavigationOutcome loginOutcome = clickAndTrackNewTab(page, context, loginButton);
			final Page googleOrAppPage = loginOutcome.targetPage;
			handleGoogleAccountSelectionIfPresent(googleOrAppPage);
		}

		page = resolveApplicationPage(context, page);
		assertVisibleBySelectors(page, "xpath=//aside", "xpath=//nav");
		assertAnyTextVisible(page, "Negocio", "Mi Negocio");

		return page;
	}

	private void openMiNegocioMenu(final Page page) {
		clickVisibleText(page, "Negocio");
		ensureMiNegocioSubmenuExpanded(page);
	}

	private void ensureMiNegocioSubmenuExpanded(final Page page) {
		if (!isTextVisible(page, "Agregar Negocio", SHORT_TIMEOUT_MS)
				|| !isTextVisible(page, "Administrar Negocios", SHORT_TIMEOUT_MS)) {
			clickVisibleText(page, "Mi Negocio");
		}
		waitForUiToSettle(page);
	}

	private void handleGoogleAccountSelectionIfPresent(final Page page) {
		if (page.isClosed()) {
			return;
		}

		waitForUiToSettle(page);
		final Locator accountOption = waitForFirstVisible(page, SHORT_TIMEOUT_MS,
				"text=" + GOOGLE_ACCOUNT_EMAIL,
				"xpath=//*[contains(normalize-space(.),'" + GOOGLE_ACCOUNT_EMAIL + "')]");
		if (accountOption != null) {
			accountOption.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			waitForUiToSettle(page);
		}
	}

	private NavigationOutcome openLinkAndCaptureTargetPage(final Page appPage, final BrowserContext context,
			final String linkText) {
		final Locator link = waitForFirstVisible(appPage, DEFAULT_TIMEOUT_MS,
				"text=" + linkText,
				"xpath=//a[contains(normalize-space(.),'" + linkText + "')]",
				"xpath=//*[self::a or self::button][contains(normalize-space(.),'" + linkText + "')]");
		if (link == null) {
			throw new AssertionError("Could not find link: " + linkText);
		}
		return clickAndTrackNewTab(appPage, context, link);
	}

	private NavigationOutcome clickAndTrackNewTab(final Page currentPage, final BrowserContext context,
			final Locator locator) {
		try {
			final Page popup = context.waitForPage(
					new BrowserContext.WaitForPageOptions().setTimeout(6000),
					() -> locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS)));
			waitForUiToSettle(popup);
			return new NavigationOutcome(popup, true);
		} catch (PlaywrightException noPopup) {
			waitForUiToSettle(currentPage);
			return new NavigationOutcome(currentPage, false);
		}
	}

	private Page resolveApplicationPage(final BrowserContext context, final Page preferredPage) {
		if (looksLikeApplicationPage(preferredPage, SHORT_TIMEOUT_MS)) {
			preferredPage.bringToFront();
			return preferredPage;
		}

		for (final Page page : context.pages()) {
			if (looksLikeApplicationPage(page, SHORT_TIMEOUT_MS)) {
				page.bringToFront();
				return page;
			}
		}

		throw new AssertionError("Could not find the SaleADS application page after login.");
	}

	private boolean looksLikeApplicationPage(final Page page, final double timeoutMs) {
		if (page == null || page.isClosed()) {
			return false;
		}
		return isVisibleBySelectors(page, timeoutMs, "xpath=//aside", "xpath=//nav")
				&& (isTextVisible(page, "Negocio", timeoutMs) || isTextVisible(page, "Mi Negocio", timeoutMs));
	}

	private void restoreApplicationPage(final Page appPage, final NavigationOutcome outcome) {
		if (outcome.openedNewTab && !outcome.targetPage.equals(appPage) && !outcome.targetPage.isClosed()) {
			outcome.targetPage.close();
			appPage.bringToFront();
			waitForUiToSettle(appPage);
			return;
		}

		if (outcome.targetPage.equals(appPage)) {
			appPage.goBack(new Page.GoBackOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			waitForUiToSettle(appPage);
		}
	}

	private void assertLegalContentVisible(final Page page) {
		assertVisibleBySelectors(page,
				"xpath=//p[string-length(normalize-space()) > 40]",
				"xpath=//li[string-length(normalize-space()) > 40]",
				"xpath=//div[string-length(normalize-space()) > 80]");
	}

	private void clickVisibleText(final Page page, final String text) {
		final Locator locator = waitForFirstVisible(page, DEFAULT_TIMEOUT_MS,
				"text=" + text,
				"xpath=//*[self::a or self::button or self::div or self::span][contains(normalize-space(.),'" + text + "')]");
		if (locator == null) {
			throw new AssertionError("Could not find visible clickable text: " + text);
		}
		locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUiToSettle(page);
	}

	private void assertAnyTextVisible(final Page page, final String... candidateTexts) {
		for (final String text : candidateTexts) {
			if (isTextVisible(page, text, SHORT_TIMEOUT_MS)) {
				return;
			}
		}
		throw new AssertionError("None of the expected texts were visible: " + String.join(", ", candidateTexts));
	}

	private void assertTextVisible(final Page page, final String text) {
		if (!isTextVisible(page, text, DEFAULT_TIMEOUT_MS)) {
			throw new AssertionError("Expected text not visible: " + text);
		}
	}

	private boolean isTextVisible(final Page page, final String text, final double timeoutMs) {
		return isVisibleBySelectors(page, timeoutMs,
				"text=" + text,
				"xpath=//*[contains(normalize-space(.),'" + text + "')]");
	}

	private void assertVisibleBySelectors(final Page page, final String... selectors) {
		if (!isVisibleBySelectors(page, DEFAULT_TIMEOUT_MS, selectors)) {
			throw new AssertionError("Expected visible selector(s) not found: " + String.join(" | ", selectors));
		}
	}

	private boolean isVisibleBySelectors(final Page page, final double timeoutMs, final String... selectors) {
		return waitForFirstVisible(page, timeoutMs, selectors) != null;
	}

	private Locator waitForFirstVisible(final Page page, final double timeoutMs, final String... selectors) {
		for (final String selector : selectors) {
			try {
				final Locator locator = page.locator(selector).first();
				locator.waitFor(new Locator.WaitForOptions()
						.setState(WaitForSelectorState.VISIBLE)
						.setTimeout(timeoutMs));
				return locator;
			} catch (PlaywrightException ignored) {
				// try next selector
			}
		}
		return null;
	}

	private void waitForUiToSettle(final Page page) {
		if (page == null || page.isClosed()) {
			return;
		}
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// no-op
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000));
		} catch (PlaywrightException ignored) {
			// no-op
		}
		page.waitForTimeout(500);
	}

	private void captureScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(evidenceDir.resolve(fileName + ".png"))
				.setFullPage(fullPage));
	}

	private void navigateIfConfigured(final Page page) {
		final String configuredUrl = readStringConfig("saleads.baseUrl", "SALEADS_BASE_URL");
		if (configuredUrl != null && !configuredUrl.isBlank()) {
			page.navigate(configuredUrl);
			waitForUiToSettle(page);
			return;
		}

		if ("about:blank".equals(page.url())) {
			throw new AssertionError(
					"No URL configured. Set -Dsaleads.baseUrl or SALEADS_BASE_URL to the SaleADS login page.");
		}
	}

	private boolean readBooleanConfig(final String propertyName, final String envName, final boolean defaultValue) {
		final String property = System.getProperty(propertyName);
		if (property != null) {
			return Boolean.parseBoolean(property.trim());
		}
		final String env = System.getenv(envName);
		if (env != null) {
			return Boolean.parseBoolean(env.trim());
		}
		return defaultValue;
	}

	private String readStringConfig(final String propertyName, final String envName) {
		final String property = System.getProperty(propertyName);
		if (property != null && !property.isBlank()) {
			return property.trim();
		}
		final String env = System.getenv(envName);
		if (env != null && !env.isBlank()) {
			return env.trim();
		}
		return null;
	}

	private void executeStep(final Map<String, String> results, final String key, final StepAction action) {
		try {
			action.run();
			results.put(key, "PASS");
		} catch (Throwable t) {
			results.put(key, "FAIL - " + t.getMessage());
		}
	}

	private void writeFinalReport(final Path evidenceDir, final Map<String, String> results,
			final Map<String, String> legalUrls, final String finalAppUrl) throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test").append("\n");
		report.append("Generated at: ").append(Instant.now()).append("\n\n");
		report.append("Final status by validation step:\n");
		for (final Map.Entry<String, String> entry : results.entrySet()) {
			report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
		}
		report.append("\nLegal URLs:\n");
		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
		}
		if (finalAppUrl != null) {
			report.append("- Application URL: ").append(finalAppUrl).append("\n");
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), report.toString(), StandardCharsets.UTF_8);
	}

	private static final class NavigationOutcome {
		private final Page targetPage;
		private final boolean openedNewTab;

		private NavigationOutcome(final Page targetPage, final boolean openedNewTab) {
			this.targetPage = targetPage;
			this.openedNewTab = openedNewTab;
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
