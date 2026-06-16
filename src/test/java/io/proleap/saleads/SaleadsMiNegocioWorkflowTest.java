package io.proleap.saleads;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * End-to-end workflow for SaleADS "Mi Negocio".
 *
 * Runtime configuration:
 * - SALEADS_START_URL (or -Dsaleads.startUrl): Login URL for any environment.
 * - SALEADS_CDP_URL (or -Dsaleads.cdpUrl): Optional CDP endpoint to reuse an existing browser page.
 * - SALEADS_HEADLESS (or -Dsaleads.headless): true/false, defaults to true.
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final double UI_TIMEOUT_MS = 20_000;
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);

		final Path evidenceDir = createEvidenceDirectory();
		final String[] legalUrls = new String[] { "N/A", "N/A" };

		try (Playwright playwright = Playwright.create()) {
			final BrowserSession browserSession = createBrowserSession(playwright);
			final Page appPage = browserSession.page;

			// 1) Login with Google.
			report.put("Login", runStep("Login", () -> {
				clickAnyText(appPage, Arrays.asList(
						"Sign in with Google",
						"Iniciar sesión con Google",
						"Iniciar sesion con Google",
						"Continuar con Google",
						"Acceder con Google",
						"Login with Google"
				));

				maybeSelectGoogleAccount(appPage, GOOGLE_ACCOUNT_EMAIL);
				waitForUi(appPage);

				assertAnyVisible(appPage, Arrays.asList("Negocio", "Mi Negocio"), UI_TIMEOUT_MS,
						"Main application interface did not appear after login.");
				assertSidebarVisible(appPage);

				takeScreenshot(appPage, evidenceDir, "01-dashboard-loaded", false);
			}));

			// 2) Open Mi Negocio menu.
			report.put("Mi Negocio menu", runStep("Mi Negocio menu", () -> {
				assertSidebarVisible(appPage);
				clickAnyText(appPage, Arrays.asList("Negocio"));
				clickAnyText(appPage, Arrays.asList("Mi Negocio"));

				assertAnyVisible(appPage, Arrays.asList("Agregar Negocio"), UI_TIMEOUT_MS,
						"'Agregar Negocio' was not visible after expanding Mi Negocio.");
				assertAnyVisible(appPage, Arrays.asList("Administrar Negocios"), UI_TIMEOUT_MS,
						"'Administrar Negocios' was not visible after expanding Mi Negocio.");

				takeScreenshot(appPage, evidenceDir, "02-mi-negocio-menu-expanded", false);
			}));

			// 3) Validate Agregar Negocio modal.
			report.put("Agregar Negocio modal", runStep("Agregar Negocio modal", () -> {
				clickAnyText(appPage, Arrays.asList("Agregar Negocio"));

				assertAnyVisible(appPage, Arrays.asList("Crear Nuevo Negocio"), UI_TIMEOUT_MS,
						"Modal title 'Crear Nuevo Negocio' is not visible.");
				assertAnyVisible(appPage, Arrays.asList("Nombre del Negocio"), UI_TIMEOUT_MS,
						"'Nombre del Negocio' input label/placeholder is not visible.");
				assertAnyVisible(appPage, Arrays.asList("Tienes 2 de 3 negocios"), UI_TIMEOUT_MS,
						"Business quota text was not visible in modal.");
				assertAnyVisible(appPage, Arrays.asList("Cancelar"), UI_TIMEOUT_MS,
						"'Cancelar' button was not visible in modal.");
				assertAnyVisible(appPage, Arrays.asList("Crear Negocio"), UI_TIMEOUT_MS,
						"'Crear Negocio' button was not visible in modal.");

				fillBusinessNameIfPresent(appPage, "Negocio Prueba Automatización");
				takeScreenshot(appPage, evidenceDir, "03-agregar-negocio-modal", false);

				clickAnyText(appPage, Arrays.asList("Cancelar"));
			}));

			// 4) Open Administrar Negocios view.
			report.put("Administrar Negocios view", runStep("Administrar Negocios view", () -> {
				ensureMiNegocioMenuExpanded(appPage);
				clickAnyText(appPage, Arrays.asList("Administrar Negocios"));
				waitForUi(appPage);

				assertAnyVisible(appPage, Arrays.asList("Información General"), UI_TIMEOUT_MS,
						"'Información General' section is not visible.");
				assertAnyVisible(appPage, Arrays.asList("Detalles de la Cuenta"), UI_TIMEOUT_MS,
						"'Detalles de la Cuenta' section is not visible.");
				assertAnyVisible(appPage, Arrays.asList("Tus Negocios"), UI_TIMEOUT_MS,
						"'Tus Negocios' section is not visible.");
				assertAnyVisible(appPage, Arrays.asList("Sección Legal", "Seccion Legal"), UI_TIMEOUT_MS,
						"'Sección Legal' section is not visible.");

				takeScreenshot(appPage, evidenceDir, "04-administrar-negocios-view", true);
			}));

			// 5) Validate Información General.
			report.put("Información General", runStep("Información General", () -> {
				assertAnyVisible(appPage, Arrays.asList("Información General"), UI_TIMEOUT_MS,
						"'Información General' heading is not visible.");
				assertAnyVisible(appPage, Arrays.asList("BUSINESS PLAN"), UI_TIMEOUT_MS,
						"'BUSINESS PLAN' text is not visible.");
				assertAnyVisible(appPage, Arrays.asList("Cambiar Plan"), UI_TIMEOUT_MS,
						"'Cambiar Plan' button is not visible.");

				final String pageText = appPage.locator("body").innerText();
				Assert.assertTrue("User email is not visible in Información General area.",
						Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE)
								.matcher(pageText)
								.find());
				Assert.assertTrue("User name is not visible in Información General area.",
						Pattern.compile("\\b\\p{L}{3,}(?:\\s+\\p{L}{2,})?\\b", Pattern.UNICODE_CHARACTER_CLASS)
								.matcher(pageText)
								.find());
			}));

			// 6) Validate Detalles de la Cuenta.
			report.put("Detalles de la Cuenta", runStep("Detalles de la Cuenta", () -> {
				assertAnyVisible(appPage, Arrays.asList("Cuenta creada"), UI_TIMEOUT_MS,
						"'Cuenta creada' is not visible.");
				assertAnyVisible(appPage, Arrays.asList("Estado activo"), UI_TIMEOUT_MS,
						"'Estado activo' is not visible.");
				assertAnyVisible(appPage, Arrays.asList("Idioma seleccionado"), UI_TIMEOUT_MS,
						"'Idioma seleccionado' is not visible.");
			}));

			// 7) Validate Tus Negocios.
			report.put("Tus Negocios", runStep("Tus Negocios", () -> {
				assertAnyVisible(appPage, Arrays.asList("Tus Negocios"), UI_TIMEOUT_MS,
						"'Tus Negocios' heading is not visible.");
				assertAnyVisible(appPage, Arrays.asList("Agregar Negocio"), UI_TIMEOUT_MS,
						"'Agregar Negocio' button is not visible in business list.");
				assertAnyVisible(appPage, Arrays.asList("Tienes 2 de 3 negocios"), UI_TIMEOUT_MS,
						"Business quota text is not visible in Tus Negocios.");
			}));

			// 8) Validate Términos y Condiciones.
			report.put("Términos y Condiciones", runStep("Términos y Condiciones", () -> {
				final LegalNavigationResult result = openLegalDocument(appPage, evidenceDir,
						"Términos y Condiciones",
						"08-terminos-y-condiciones",
						Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"));
				legalUrls[0] = result.finalUrl;
			}));

			// 9) Validate Política de Privacidad.
			report.put("Política de Privacidad", runStep("Política de Privacidad", () -> {
				final LegalNavigationResult result = openLegalDocument(appPage, evidenceDir,
						"Política de Privacidad",
						"09-politica-de-privacidad",
						Arrays.asList("Política de Privacidad", "Politica de Privacidad"));
				legalUrls[1] = result.finalUrl;
			}));

			if (browserSession.closeOnExit) {
				browserSession.browser.close();
			}
		}

		final String reportText = buildFinalReport(report, legalUrls[0], legalUrls[1], evidenceDir);
		System.out.println(reportText);

		Assert.assertTrue("One or more validations failed.\n" + reportText, report.values().stream().allMatch(Boolean::booleanValue));
	}

	private BrowserSession createBrowserSession(final Playwright playwright) {
		final String cdpUrl = getValue("saleads.cdpUrl", "SALEADS_CDP_URL");
		final boolean closeOnExit;
		final Browser browser;
		final BrowserContext context;
		final Page page;

		if (cdpUrl != null && !cdpUrl.isBlank()) {
			browser = playwright.chromium().connectOverCDP(cdpUrl);
			context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
			page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
			closeOnExit = false;
		} else {
			final String startUrl = getValue("saleads.startUrl", "SALEADS_START_URL");
			Assume.assumeTrue(
					"Set SALEADS_START_URL (or -Dsaleads.startUrl) for the target environment, or provide SALEADS_CDP_URL for an existing login page.",
					startUrl != null && !startUrl.isBlank());

			final boolean headless = Boolean.parseBoolean(getValueOrDefault("saleads.headless", "SALEADS_HEADLESS", "true"));
			browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
			page = context.newPage();
			page.navigate(startUrl);
			waitForUi(page);
			closeOnExit = true;
		}

		return new BrowserSession(browser, page, closeOnExit);
	}

	private void maybeSelectGoogleAccount(final Page page, final String email) {
		final Locator accountLocator = page.getByText(email, new Page.GetByTextOptions().setExact(true));
		try {
			if (accountLocator.count() > 0) {
				accountLocator.first().click();
				waitForUi(page);
			}
		} catch (PlaywrightException ignored) {
			// Account selector may not appear when already authenticated.
		}
	}

	private void ensureMiNegocioMenuExpanded(final Page page) {
		if (isAnyVisible(page, Arrays.asList("Administrar Negocios"), 1_500)) {
			return;
		}

		if (isAnyVisible(page, Arrays.asList("Negocio"), 2_000)) {
			clickAnyText(page, Arrays.asList("Negocio"));
		}

		if (!isAnyVisible(page, Arrays.asList("Administrar Negocios"), 2_000)) {
			clickAnyText(page, Arrays.asList("Mi Negocio"));
		}
	}

	private LegalNavigationResult openLegalDocument(final Page appPage,
			final Path evidenceDir,
			final String expectedHeading,
			final String screenshotName,
			final List<String> linkTexts) {
		Page targetPage = null;
		boolean openedInNewTab = false;

		try {
			targetPage = appPage.waitForPopup(() -> clickAnyTextNoWait(appPage, linkTexts),
					new Page.WaitForPopupOptions().setTimeout(8_000));
			openedInNewTab = true;
			waitForUi(targetPage);
		} catch (PlaywrightException popupNotOpened) {
			clickAnyText(appPage, linkTexts);
			targetPage = appPage;
		}

		assertAnyVisible(targetPage, Arrays.asList(expectedHeading), UI_TIMEOUT_MS,
				"Legal heading '" + expectedHeading + "' was not visible.");
		final String legalText = targetPage.locator("body").innerText().trim();
		Assert.assertTrue("Legal content text is too short for '" + expectedHeading + "'.", legalText.length() > 120);

		takeScreenshot(targetPage, evidenceDir, screenshotName, true);
		final String finalUrl = targetPage.url();

		if (openedInNewTab) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				appPage.goBack(new Page.GoBackOptions().setTimeout(8_000));
				waitForUi(appPage);
			} catch (PlaywrightException ignored) {
				// In-page legal render or non-history navigation.
			}
		}

		return new LegalNavigationResult(finalUrl);
	}

	private void clickAnyText(final Page page, final List<String> textCandidates) {
		clickAnyTextNoWait(page, textCandidates);
		waitForUi(page);
	}

	private void clickAnyTextNoWait(final Page page, final List<String> textCandidates) {
		for (final String text : textCandidates) {
			final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true));
			if (clickIfVisible(exact)) {
				return;
			}

			final Locator partial = page.getByText(text);
			if (clickIfVisible(partial)) {
				return;
			}
		}

		throw new AssertionError("Unable to find clickable element with texts: " + textCandidates);
	}

	private boolean clickIfVisible(final Locator locator) {
		try {
			if (locator.count() < 1) {
				return false;
			}
			final Locator first = locator.first();
			first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(2_000));
			first.click();
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void fillBusinessNameIfPresent(final Page page, final String businessName) {
		try {
			Locator input = page.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false));
			if (input.count() < 1) {
				input = page.locator("input[placeholder*='Nombre'][placeholder*='Negocio']");
			}
			if (input.count() < 1) {
				return;
			}
			input.first().click();
			input.first().fill(businessName);
		} catch (PlaywrightException ignored) {
			// Optional interaction; keep the test focused on modal validation.
		}
	}

	private void assertSidebarVisible(final Page page) {
		try {
			page.locator("aside, nav").first().waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout(UI_TIMEOUT_MS));
		} catch (PlaywrightException ex) {
			Assert.fail("Left sidebar navigation is not visible.");
		}
	}

	private boolean isAnyVisible(final Page page, final List<String> textCandidates, final double timeoutMs) {
		for (final String text : textCandidates) {
			final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true));
			if (isVisible(exact, timeoutMs)) {
				return true;
			}

			final Locator partial = page.getByText(text);
			if (isVisible(partial, timeoutMs)) {
				return true;
			}
		}

		return false;
	}

	private void assertAnyVisible(final Page page,
			final List<String> textCandidates,
			final double timeoutMs,
			final String failureMessage) {
		if (!isAnyVisible(page, textCandidates, timeoutMs)) {
			Assert.fail(failureMessage + " Tried texts: " + textCandidates);
		}
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			if (locator.count() < 1) {
				return false;
			}
			locator.first().waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout(timeoutMs));
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
		} catch (PlaywrightException ignored) {
			// Some SPAs keep network active via websockets.
		}
		page.waitForTimeout(500);
	}

	private void takeScreenshot(final Page page, final Path evidenceDir, final String name, final boolean fullPage) {
		final Path output = evidenceDir.resolve(name + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(output).setFullPage(fullPage));
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		final Path evidenceDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private String buildFinalReport(final Map<String, Boolean> report,
			final String termsUrl,
			final String privacyUrl,
			final Path evidenceDir) {
		final StringBuilder sb = new StringBuilder();
		sb.append('\n').append("SaleADS Mi Negocio Workflow - Final Report").append('\n');
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		sb.append("Final URL (Términos y Condiciones): ").append(termsUrl).append('\n');
		sb.append("Final URL (Política de Privacidad): ").append(privacyUrl).append('\n');
		sb.append("Step status:").append('\n');

		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			sb.append(" - ")
					.append(entry.getKey())
					.append(": ")
					.append(entry.getValue() ? "PASS" : "FAIL")
					.append('\n');
		}
		return sb.toString();
	}

	private boolean runStep(final String stepName, final CheckedRunnable action) {
		try {
			action.run();
			return true;
		} catch (Throwable ex) {
			System.out.println("Step failed [" + stepName + "]: " + ex.getMessage());
			return false;
		}
	}

	private String getValue(final String propertyName, final String environmentName) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		final String environmentValue = System.getenv(environmentName);
		return (environmentValue == null || environmentValue.isBlank()) ? null : environmentValue;
	}

	private String getValueOrDefault(final String propertyName, final String environmentName, final String defaultValue) {
		final String value = getValue(propertyName, environmentName);
		return value == null ? defaultValue : value;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class LegalNavigationResult {
		private final String finalUrl;

		private LegalNavigationResult(final String finalUrl) {
			this.finalUrl = finalUrl;
		}
	}

	private static final class BrowserSession {
		private final Browser browser;
		private final Page page;
		private final boolean closeOnExit;

		private BrowserSession(final Browser browser, final Page page, final boolean closeOnExit) {
			this.browser = browser;
			this.page = page;
			this.closeOnExit = closeOnExit;
		}
	}
}
