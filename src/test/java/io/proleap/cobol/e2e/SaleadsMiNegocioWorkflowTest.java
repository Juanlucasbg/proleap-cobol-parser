package io.proleap.cobol.e2e;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * End-to-end workflow test for SaleADS Mi Negocio module.
 *
 * <p>
 * Configuration:
 * <ul>
 * <li>-Dsaleads.login.url or env SALEADS_LOGIN_URL (required)</li>
 * <li>-Dsaleads.headless=true|false (optional, default true)</li>
 * <li>-Dsaleads.timeout.ms=20000 (optional, default 20000)</li>
 * <li>-Dsaleads.screenshot.dir=target/saleads-evidence (optional)</li>
 * </ul>
 * </p>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final int DEFAULT_TIMEOUT_MS = Integer.getInteger("saleads.timeout.ms", 20_000);

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Información General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "Términos y Condiciones";
	private static final String POLITICA = "Política de Privacidad";

	private static final List<String> REPORT_FIELDS = Arrays.asList(LOGIN, MI_NEGOCIO_MENU, AGREGAR_NEGOCIO_MODAL,
			ADMINISTRAR_NEGOCIOS_VIEW, INFORMACION_GENERAL, DETALLES_CUENTA, TUS_NEGOCIOS, TERMINOS, POLITICA);

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String loginUrl = firstDefined(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		assertNotNull("Set -Dsaleads.login.url or SALEADS_LOGIN_URL to the current environment login page URL.", loginUrl);

		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		final Path evidenceDir = Paths
				.get(System.getProperty("saleads.screenshot.dir", "target/saleads-evidence/saleads_mi_negocio_full_test"));
		Files.createDirectories(evidenceDir);

		final Map<String, Boolean> report = new LinkedHashMap<>();
		for (final String field : REPORT_FIELDS) {
			report.put(field, Boolean.TRUE);
		}
		final List<String> failures = new ArrayList<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			try (BrowserContext context = browser.newContext()) {
				final Page page = context.newPage();
				page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
				page.navigate(loginUrl);
				waitForUiToSettle(page);

				// Step 1 - Login with Google
				runStep(LOGIN, report, failures, () -> {
					clickGoogleLogin(context, page);
					selectGoogleAccountIfPresented(context, page, GOOGLE_ACCOUNT_EMAIL);
					waitForAnyVisibleText(page, Arrays.asList("Negocio", "Mi Negocio"), "main app interface");
					assertVisibleText(page, "Negocio");
					screenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), false);
				});

				// Step 2 - Open Mi Negocio menu
				runStep(MI_NEGOCIO_MENU, report, failures, () -> {
					ensureMiNegocioExpanded(page);
					assertVisibleText(page, "Agregar Negocio");
					assertVisibleText(page, "Administrar Negocios");
					screenshot(page, evidenceDir.resolve("02-mi-negocio-expanded-menu.png"), false);
				});

				// Step 3 - Validate Agregar Negocio modal
				runStep(AGREGAR_NEGOCIO_MODAL, report, failures, () -> {
					clickByVisibleText(page, "Agregar Negocio");
					assertVisibleText(page, "Crear Nuevo Negocio");
					assertAnyVisibleText(page, Arrays.asList("Nombre del Negocio", "Nombre negocio"), "Nombre field");
					assertVisibleText(page, "Tienes 2 de 3 negocios");
					assertVisibleText(page, "Cancelar");
					assertVisibleText(page, "Crear Negocio");
					screenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

					// Optional actions requested by workflow.
					fillInputIfVisible(page, Arrays.asList("Nombre del Negocio", "Nombre negocio"),
							"Negocio Prueba Automatización");
					clickByVisibleText(page, "Cancelar");
					waitForUiToSettle(page);
				});

				// Step 4 - Open Administrar Negocios
				runStep(ADMINISTRAR_NEGOCIOS_VIEW, report, failures, () -> {
					ensureMiNegocioExpanded(page);
					clickByVisibleText(page, "Administrar Negocios");
					waitForUiToSettle(page);
					assertVisibleText(page, "Información General");
					assertVisibleText(page, "Detalles de la Cuenta");
					assertVisibleText(page, "Tus Negocios");
					assertAnyVisibleText(page, Arrays.asList("Sección Legal", "Seccion Legal"), "Sección Legal");
					screenshot(page, evidenceDir.resolve("04-administrar-negocios-full.png"), true);
				});

				// Step 5 - Validate Información General
				runStep(INFORMACION_GENERAL, report, failures, () -> {
					assertLikelyUserNameVisible(page);
					assertAnyVisibleText(page, Arrays.asList("@", ".com"), "user email");
					assertAnyVisibleText(page, Arrays.asList("BUSINESS PLAN"), "BUSINESS PLAN");
					assertVisibleText(page, "Cambiar Plan");
				});

				// Step 6 - Validate Detalles de la Cuenta
				runStep(DETALLES_CUENTA, report, failures, () -> {
					assertAnyVisibleText(page, Arrays.asList("Cuenta creada"), "Cuenta creada");
					assertAnyVisibleText(page, Arrays.asList("Estado activo", "Estado Activo"), "Estado activo");
					assertAnyVisibleText(page, Arrays.asList("Idioma seleccionado"), "Idioma seleccionado");
				});

				// Step 7 - Validate Tus Negocios
				runStep(TUS_NEGOCIOS, report, failures, () -> {
					assertVisibleText(page, "Tus Negocios");
					assertVisibleText(page, "Agregar Negocio");
					assertVisibleText(page, "Tienes 2 de 3 negocios");
				});

				// Step 8 - Validate Términos y Condiciones
				runStep(TERMINOS, report, failures,
						() -> validateLegalLink(context, page, "Términos y Condiciones",
								Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
								evidenceDir.resolve("05-terminos-y-condiciones.png"), legalUrls, TERMINOS));

				// Step 9 - Validate Política de Privacidad
				runStep(POLITICA, report, failures,
						() -> validateLegalLink(context, page, "Política de Privacidad",
								Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
								evidenceDir.resolve("06-politica-de-privacidad.png"), legalUrls, POLITICA));
			}
		}

		printReport(report, legalUrls, evidenceDir);
		assertTrue("Failed workflow validations:\n- " + String.join("\n- ", failures), failures.isEmpty());
	}

	private void runStep(final String stepName, final Map<String, Boolean> report, final List<String> failures,
			final StepAction action) {
		try {
			action.run();
		} catch (final Throwable error) {
			report.put(stepName, Boolean.FALSE);
			failures.add(stepName + " -> " + normalizeMessage(error));
		}
	}

	private void clickGoogleLogin(final BrowserContext context, final Page page) {
		final int pagesBeforeClick = context.pages().size();
		final Locator googleButton = firstVisibleLocator(Arrays.asList(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*google.*"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*google.*"))),
				page.getByText("Sign in with Google", new Page.GetByTextOptions().setExact(false)),
				page.getByText("Iniciar sesión con Google", new Page.GetByTextOptions().setExact(false)),
				page.getByText("Continuar con Google", new Page.GetByTextOptions().setExact(false))));

		googleButton.click();
		waitForUiToSettle(page);

		// If Google opens in a new tab, bring it to front to continue account selection.
		final Page maybePopup = waitForNewPage(context, pagesBeforeClick, 10_000);
		if (maybePopup != null) {
			maybePopup.bringToFront();
			waitForUiToSettle(maybePopup);
		}
	}

	private void selectGoogleAccountIfPresented(final BrowserContext context, final Page appPage, final String email) {
		final Page activePage = context.pages().get(context.pages().size() - 1);

		final Locator accountLocator = activePage.getByText(email, new Page.GetByTextOptions().setExact(true)).first();
		if (isVisible(accountLocator, 8_000)) {
			accountLocator.click();
			waitForUiToSettle(activePage);
		}

		// Return to app tab if Google page is still active or opened as popup.
		if (activePage != appPage) {
			if (activePage.url() != null && activePage.url().contains("accounts.google.com")) {
				activePage.close();
			}
			appPage.bringToFront();
			waitForUiToSettle(appPage);
		}
	}

	private void ensureMiNegocioExpanded(final Page page) {
		if (!isVisible(page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)).first(), 2_000)) {
			if (isVisible(page.getByText("Negocio", new Page.GetByTextOptions().setExact(false)).first(), 3_000)) {
				clickByVisibleText(page, "Negocio");
			}
			clickByVisibleText(page, "Mi Negocio");
			waitForUiToSettle(page);
		}
	}

	private void validateLegalLink(final BrowserContext context, final Page appPage, final String linkText,
			final List<String> headingCandidates, final Path screenshotPath, final Map<String, String> legalUrls,
			final String reportKey) {
		appPage.bringToFront();
		final String appUrlBefore = appPage.url();
		final int pagesBeforeClick = context.pages().size();

		clickByVisibleText(appPage, linkText);
		waitForUiToSettle(appPage);

		Page targetPage = waitForNewPage(context, pagesBeforeClick, 8_000);
		boolean openedNewTab = true;
		if (targetPage == null) {
			targetPage = appPage;
			openedNewTab = false;
		}

		targetPage.bringToFront();
		waitForUiToSettle(targetPage);

		boolean headingFound = false;
		for (final String headingCandidate : headingCandidates) {
			if (isVisible(targetPage.getByText(headingCandidate, new Page.GetByTextOptions().setExact(false)).first(), 3_000)) {
				headingFound = true;
				break;
			}
		}
		assertTrue("Expected legal heading not found for: " + linkText, headingFound);

		final String bodyText = targetPage.locator("body").innerText();
		assertTrue("Expected legal body content for: " + linkText, bodyText != null && bodyText.trim().length() > 120);

		screenshot(targetPage, screenshotPath, true);
		legalUrls.put(reportKey, targetPage.url());

		// Cleanup: return to app tab after legal validation.
		if (openedNewTab && targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUiToSettle(appPage);
			return;
		}

		if (!appPage.url().equals(appUrlBefore)) {
			appPage.goBack();
			waitForUiToSettle(appPage);
		}
	}

	private void clickByVisibleText(final Page page, final String text) {
		final Pattern textPattern = Pattern.compile("(?iu).*" + Pattern.quote(text) + ".*");
		final Locator target = firstVisibleLocator(Arrays.asList(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern)),
				page.getByText(text, new Page.GetByTextOptions().setExact(false))));
		target.click();
		waitForUiToSettle(page);
	}

	private void waitForAnyVisibleText(final Page page, final List<String> textCandidates, final String targetName) {
		for (final String candidate : textCandidates) {
			final Locator locator = page.getByText(candidate, new Page.GetByTextOptions().setExact(false)).first();
			if (isVisible(locator, 4_000)) {
				return;
			}
		}
		throw new AssertionError("Could not find visible text for " + targetName + " from candidates: " + textCandidates);
	}

	private void assertVisibleText(final Page page, final String text) {
		final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
		assertTrue("Expected visible text: " + text, isVisible(locator, DEFAULT_TIMEOUT_MS));
	}

	private void assertAnyVisibleText(final Page page, final List<String> textCandidates, final String targetName) {
		for (final String text : textCandidates) {
			final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
			if (isVisible(locator, 4_000)) {
				return;
			}
		}
		throw new AssertionError("Expected at least one visible text for " + targetName + ": " + textCandidates);
	}

	private void assertLikelyUserNameVisible(final Page page) {
		final String bodyText = page.locator("body").innerText();
		final List<String> ignored = Arrays.asList("Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Sección Legal", "BUSINESS PLAN", "Cambiar Plan", "Cuenta creada", "Estado activo", "Idioma seleccionado");

		for (final String line : bodyText.split("\\R")) {
			final String value = line.trim();
			if (value.length() < 3 || value.length() > 60 || value.contains("@")) {
				continue;
			}
			if (ignored.contains(value)) {
				continue;
			}
			if (value.chars().allMatch(ch -> !Character.isLetter(ch))) {
				continue;
			}
			return;
		}

		throw new AssertionError("Expected visible user name in Información General.");
	}

	private void fillInputIfVisible(final Page page, final List<String> labelCandidates, final String value) {
		for (final String label : labelCandidates) {
			final Locator byLabel = page.getByLabel(label, new Page.GetByLabelOptions().setExact(false)).first();
			if (isVisible(byLabel, 2_000)) {
				byLabel.fill(value);
				return;
			}
		}

		final Locator fallback = page.locator("input[name*='nombre'], input[placeholder*='Nombre']").first();
		if (isVisible(fallback, 2_000)) {
			fallback.fill(value);
		}
	}

	private Locator firstVisibleLocator(final List<Locator> candidates) {
		for (final Locator candidate : candidates) {
			final Locator first = candidate.first();
			if (isVisible(first, 5_000)) {
				return first;
			}
		}
		throw new AssertionError("No visible locator candidate matched.");
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout((double) timeoutMs));
			return true;
		} catch (final PlaywrightException e) {
			return false;
		}
	}

	private void waitForUiToSettle(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// Some SPA transitions may not trigger a document load event.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (final PlaywrightException ignored) {
			// Not all pages become fully network idle.
		}
	}

	private Page waitForNewPage(final BrowserContext context, final int pagesBeforeClick, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			final List<Page> pages = context.pages();
			if (pages.size() > pagesBeforeClick) {
				return pages.get(pages.size() - 1);
			}
			sleepSilently(250);
		}
		return null;
	}

	private void screenshot(final Page page, final Path file, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(fullPage));
	}

	private void printReport(final Map<String, Boolean> report, final Map<String, String> legalUrls, final Path evidenceDir) {
		System.out.println("=== saleads_mi_negocio_full_test report ===");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.printf("%s: %s%n", entry.getKey(), entry.getValue() ? "PASS" : "FAIL");
		}
		for (final Map.Entry<String, String> legal : legalUrls.entrySet()) {
			System.out.printf("%s URL: %s%n", legal.getKey(), legal.getValue());
		}
		System.out.println("Screenshots directory: " + evidenceDir.toAbsolutePath());
	}

	private String firstDefined(final String first, final String second) {
		if (first != null && !first.trim().isEmpty()) {
			return first.trim();
		}
		if (second != null && !second.trim().isEmpty()) {
			return second.trim();
		}
		return null;
	}

	private String normalizeMessage(final Throwable error) {
		final String message = error.getMessage();
		if (message == null || message.trim().isEmpty()) {
			return error.getClass().getSimpleName();
		}
		return message.trim();
	}

	private void sleepSilently(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
