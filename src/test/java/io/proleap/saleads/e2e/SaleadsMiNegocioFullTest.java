package io.proleap.saleads.e2e;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final long DEFAULT_WAIT_TIMEOUT_MS = 30_000L;
	private static final long POLLING_INTERVAL_MS = 500L;

	@Test
	public void saleadsMiNegocioFullWorkflowTest() throws IOException {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		final Map<String, String> urls = new LinkedHashMap<>();
		final Path screenshotsDir = createScreenshotsDirectory();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
					.setHeadless(isHeadless()));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
			final Page page = context.newPage();

			openLoginPageIfConfigured(page);
			waitForUi(page);

			// Step 1: Login with Google and validate dashboard/sidebar.
			executeGoogleLogin(page);
			final boolean loginMainInterfaceVisible = waitForVisibleText(page, 60_000L, "Mi Negocio", "Negocio",
					"Administrar Negocios");
			final boolean sidebarVisible = isSidebarVisible(page);
			report.put("Login", loginMainInterfaceVisible && sidebarVisible);
			takeScreenshot(page, screenshotsDir.resolve("01-dashboard-loaded.png"), false);

			// Step 2: Open Mi Negocio menu and validate entries.
			expandMiNegocioMenu(page);
			final boolean agregarNegocioVisible = isTextVisible(page, "Agregar Negocio");
			final boolean administrarNegociosVisible = isTextVisible(page, "Administrar Negocios");
			report.put("Mi Negocio menu", agregarNegocioVisible && administrarNegociosVisible);
			takeScreenshot(page, screenshotsDir.resolve("02-mi-negocio-expanded.png"), false);

			// Step 3: Validate Agregar Negocio modal.
			clickByVisibleText(page, "Agregar Negocio");
			final boolean modalTitleVisible = waitForVisibleText(page, DEFAULT_WAIT_TIMEOUT_MS, "Crear Nuevo Negocio");
			final boolean nombreNegocioVisible = isTextVisible(page, "Nombre del Negocio")
					|| page.locator("input[placeholder*='Nombre']").count() > 0;
			final boolean negociosQuotaVisible = isTextVisible(page, "Tienes 2 de 3 negocios");
			final boolean cancelarVisible = isTextVisible(page, "Cancelar");
			final boolean crearNegocioVisible = isTextVisible(page, "Crear Negocio");
			report.put("Agregar Negocio modal", modalTitleVisible && nombreNegocioVisible && negociosQuotaVisible
					&& cancelarVisible && crearNegocioVisible);
			takeScreenshot(page, screenshotsDir.resolve("03-agregar-negocio-modal.png"), false);

			// Optional actions requested in the workflow.
			fillBusinessNameAndCancel(page);

			// Step 4: Open Administrar Negocios and validate account sections.
			expandMiNegocioMenu(page);
			clickByVisibleText(page, "Administrar Negocios");
			final boolean informacionGeneralSectionVisible = waitForVisibleText(page, DEFAULT_WAIT_TIMEOUT_MS,
					"Información General", "Informacion General");
			final boolean detallesCuentaSectionVisible = isTextVisible(page, "Detalles de la Cuenta");
			final boolean tusNegociosSectionVisible = isTextVisible(page, "Tus Negocios");
			final boolean legalSectionVisible = isTextVisible(page, "Sección Legal") || isTextVisible(page, "Seccion Legal");
			report.put("Administrar Negocios view", informacionGeneralSectionVisible && detallesCuentaSectionVisible
					&& tusNegociosSectionVisible && legalSectionVisible);
			takeScreenshot(page, screenshotsDir.resolve("04-administrar-negocios-view.png"), true);

			// Step 5: Validate Información General block.
			final boolean userNameVisible = hasLikelyUserName(page);
			final boolean userEmailVisible = hasEmailVisible(page);
			final boolean businessPlanVisible = isTextVisible(page, "BUSINESS PLAN");
			final boolean cambiarPlanVisible = isTextVisible(page, "Cambiar Plan");
			report.put("Información General", userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible);

			// Step 6: Validate Detalles de la Cuenta block.
			final boolean cuentaCreadaVisible = isTextVisible(page, "Cuenta creada");
			final boolean estadoActivoVisible = isTextVisible(page, "Estado activo");
			final boolean idiomaSeleccionadoVisible = isTextVisible(page, "Idioma seleccionado");
			report.put("Detalles de la Cuenta", cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible);

			// Step 7: Validate Tus Negocios block.
			final boolean businessListVisible = page.locator("section:has-text('Tus Negocios') li").count() > 0
					|| page.locator("section:has-text('Tus Negocios') [role='listitem']").count() > 0
					|| page.locator("section:has-text('Tus Negocios') div").count() > 0;
			final boolean agregarButtonVisible = isTextVisible(page, "Agregar Negocio");
			final boolean quotaVisible = isTextVisible(page, "Tienes 2 de 3 negocios");
			report.put("Tus Negocios", businessListVisible && agregarButtonVisible && quotaVisible);

			// Step 8: Validate Términos y Condiciones (same-tab or new-tab).
			final LegalPageResult terminosResult = validateLegalPageAndReturn(page, "Términos y Condiciones",
					"Términos y Condiciones", screenshotsDir.resolve("08-terminos-y-condiciones.png"));
			report.put("Términos y Condiciones", terminosResult.passed);
			urls.put("Términos y Condiciones", terminosResult.finalUrl);

			// Step 9: Validate Política de Privacidad (same-tab or new-tab).
			final LegalPageResult privacidadResult = validateLegalPageAndReturn(page, "Política de Privacidad",
					"Política de Privacidad", screenshotsDir.resolve("09-politica-de-privacidad.png"));
			report.put("Política de Privacidad", privacidadResult.passed);
			urls.put("Política de Privacidad", privacidadResult.finalUrl);
		}

		printFinalReport(report, urls, screenshotsDir);
		assertAllStepsPassed(report, urls);
	}

	private void executeGoogleLogin(final Page page) {
		Page googlePage = null;
		try {
			googlePage = page.waitForPopup(
					() -> clickByVisibleTextNoWait(page, "Sign in with Google", "Iniciar sesión con Google",
							"Iniciar sesion con Google", "Continuar con Google", "Acceder con Google"),
					new Page.WaitForPopupOptions().setTimeout(8_000));
		} catch (final PlaywrightException popupNotOpened) {
			clickByVisibleText(page, "Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
					"Continuar con Google", "Acceder con Google");
		}

		if (googlePage != null) {
			waitForUi(googlePage);
			selectGoogleAccountIfVisible(googlePage);
			try {
				googlePage.waitForClose(new Page.WaitForCloseOptions().setTimeout(60_000));
			} catch (final PlaywrightException ignored) {
				// In some environments Google stays on same popup lifecycle; continue waiting on app page.
			}
		} else {
			selectGoogleAccountIfVisible(page);
		}

		waitForUi(page);
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		try {
			if (waitForVisibleText(page, 15_000L, GOOGLE_ACCOUNT_EMAIL)) {
				clickByVisibleText(page, GOOGLE_ACCOUNT_EMAIL);
			}
		} catch (final PlaywrightException ignored) {
			// If the Google account chooser closes quickly, continue with application validation.
		}
	}

	private void expandMiNegocioMenu(final Page page) {
		if (isTextVisible(page, "Agregar Negocio") && isTextVisible(page, "Administrar Negocios")) {
			return;
		}

		clickByVisibleText(page, "Mi Negocio", "Negocio");
		assertTrue("Mi Negocio submenu did not expand after click.",
				waitForVisibleText(page, DEFAULT_WAIT_TIMEOUT_MS, "Agregar Negocio", "Administrar Negocios"));
	}

	private void fillBusinessNameAndCancel(final Page page) {
		if (page.locator("input[placeholder*='Nombre']").count() > 0) {
			page.locator("input[placeholder*='Nombre']").first().fill("Negocio Prueba Automatizacion");
		} else if (page.locator("input[name*='nombre']").count() > 0) {
			page.locator("input[name*='nombre']").first().fill("Negocio Prueba Automatizacion");
		} else if (page.locator("input").count() > 0) {
			page.locator("input").first().fill("Negocio Prueba Automatizacion");
		}

		clickByVisibleText(page, "Cancelar");
		assertTrue("Add business modal did not close after Cancelar.",
				!isTextVisible(page, "Crear Nuevo Negocio") || isTextVisible(page, "Administrar Negocios"));
	}

	private LegalPageResult validateLegalPageAndReturn(final Page appPage, final String linkText, final String headingText,
			final Path screenshotPath) {
		Page legalPage = null;
		boolean openedNewTab = false;

		try {
			legalPage = appPage.waitForPopup(() -> clickByVisibleTextNoWait(appPage, linkText),
					new Page.WaitForPopupOptions().setTimeout(6_000));
			openedNewTab = true;
		} catch (final PlaywrightException popupNotOpened) {
			clickByVisibleText(appPage, linkText);
			legalPage = appPage;
		}

		waitForUi(legalPage);
		final boolean headingVisible = waitForVisibleText(legalPage, DEFAULT_WAIT_TIMEOUT_MS, headingText);
		final boolean legalContentVisible = getBodyTextLength(legalPage) > 250;
		takeScreenshot(legalPage, screenshotPath, true);
		final String finalUrl = legalPage.url();

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				appPage.goBack();
			} catch (final PlaywrightException ignored) {
				// Some legal pages can replace history. Keep current page and continue.
			}
			waitForUi(appPage);
		}

		return new LegalPageResult(headingVisible && legalContentVisible, finalUrl);
	}

	private void clickByVisibleText(final Page page, final String... textCandidates) {
		clickByVisibleTextNoWait(page, textCandidates);
		waitForUi(page);
	}

	private void clickByVisibleTextNoWait(final Page page, final String... textCandidates) {
		final Locator clickable = findVisibleTextLocator(page, textCandidates);
		assertNotNull("Could not find clickable element by visible text: " + Arrays.toString(textCandidates), clickable);
		clickable.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_WAIT_TIMEOUT_MS));
	}

	private Locator findVisibleTextLocator(final Page page, final String... textCandidates) {
		for (final String candidate : textCandidates) {
			final Locator matches = page.getByText(candidate, new Page.GetByTextOptions().setExact(false));
			final int count = matches.count();
			for (int i = 0; i < count; i++) {
				final Locator option = matches.nth(i);
				if (safeIsVisible(option)) {
					return option;
				}
			}
		}
		return null;
	}

	private boolean isTextVisible(final Page page, final String text) {
		return findVisibleTextLocator(page, text) != null;
	}

	private boolean waitForVisibleText(final Page page, final long timeoutMs, final String... textCandidates) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() <= deadline) {
			if (findVisibleTextLocator(page, textCandidates) != null) {
				return true;
			}
			page.waitForTimeout(POLLING_INTERVAL_MS);
		}
		return false;
	}

	private boolean isSidebarVisible(final Page page) {
		if (page.locator("aside").count() > 0 && safeIsVisible(page.locator("aside").first())) {
			return true;
		}
		return isTextVisible(page, "Mi Negocio") || isTextVisible(page, "Negocio");
	}

	private boolean hasLikelyUserName(final Page page) {
		if (page.locator("section:has-text('Información General') strong").count() > 0) {
			return true;
		}
		if (page.locator("section:has-text('Informacion General') strong").count() > 0) {
			return true;
		}
		return page.locator("section:has-text('Información General') h1").count() > 0
				|| page.locator("section:has-text('Información General') h2").count() > 0
				|| page.locator("section:has-text('Informacion General') h1").count() > 0
				|| page.locator("section:has-text('Informacion General') h2").count() > 0;
	}

	private boolean hasEmailVisible(final Page page) {
		final String body = getBodyText(page);
		return body.matches("(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*");
	}

	private int getBodyTextLength(final Page page) {
		final String text = getBodyText(page);
		return text.trim().length();
	}

	private String getBodyText(final Page page) {
		try {
			final String text = page.locator("body").innerText();
			return text == null ? "" : text;
		} catch (final PlaywrightException ignored) {
			return "";
		}
	}

	private void waitForUi(final Page page) {
		if (page.isClosed()) {
			return;
		}
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7_500));
		} catch (final PlaywrightException ignored) {
			// Some pages keep long-lived connections; DOMCONTENTLOADED plus short delay is enough.
		}
		page.waitForTimeout(800);
	}

	private void openLoginPageIfConfigured(final Page page) {
		final String urlFromProperty = System.getProperty("saleads.login.url");
		final String urlFromEnv = System.getenv("SALEADS_LOGIN_URL");
		final String baseUrlFromEnv = System.getenv("BASE_URL");

		final String loginUrl = firstNonBlank(urlFromProperty, urlFromEnv, baseUrlFromEnv);
		if (loginUrl != null) {
			page.navigate(loginUrl);
		}
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private boolean safeIsVisible(final Locator locator) {
		try {
			return locator.isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private boolean isHeadless() {
		final String value = firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"));
		return value == null || Boolean.parseBoolean(value);
	}

	private Path createScreenshotsDirectory() throws IOException {
		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path screenshotsDir = Paths.get("target", "saleads-mi-negocio-evidence", runId);
		Files.createDirectories(screenshotsDir);
		return screenshotsDir;
	}

	private void takeScreenshot(final Page page, final Path targetFile, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(targetFile).setFullPage(fullPage));
	}

	private void printFinalReport(final Map<String, Boolean> report, final Map<String, String> urls, final Path screenshotsDir) {
		System.out.println("=== saleads_mi_negocio_full_test ===");
		report.forEach((step, passed) -> System.out.println(step + ": " + (passed ? "PASS" : "FAIL")));
		urls.forEach((name, value) -> System.out.println(name + " URL: " + value));
		System.out.println("Screenshots directory: " + screenshotsDir.toAbsolutePath());
	}

	private void assertAllStepsPassed(final Map<String, Boolean> report, final Map<String, String> urls) {
		final StringBuilder details = new StringBuilder("One or more SaleADS validations failed.\n");
		report.forEach((step, passed) -> details.append("- ").append(step).append(": ")
				.append(passed ? "PASS" : "FAIL").append('\n'));
		urls.forEach((name, value) -> details.append("- ").append(name).append(" URL: ").append(value).append('\n'));
		assertTrue(details.toString(), report.values().stream().allMatch(Boolean::booleanValue));
	}

	private static class LegalPageResult {
		private final boolean passed;
		private final String finalUrl;

		private LegalPageResult(final boolean passed, final String finalUrl) {
			this.passed = passed;
			this.finalUrl = finalUrl;
		}
	}
}
