package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullTest {

	private static final int DEFAULT_TIMEOUT_MS = 30_000;
	private static final int SHORT_TIMEOUT_MS = 4_000;
	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	@Test
	public void runSaleadsMiNegocioWorkflow() throws IOException {
		final boolean runEnabled = Boolean
				.parseBoolean(System.getenv().getOrDefault("RUN_SALEADS_MI_NEGOCIO_TEST", "false"));
		Assume.assumeTrue("Set RUN_SALEADS_MI_NEGOCIO_TEST=true to execute this E2E workflow.", runEnabled);

		final String startUrl = firstNonBlank(System.getenv("SALEADS_START_URL"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue("Set SALEADS_START_URL (or SALEADS_LOGIN_URL) to the SaleADS login page.", !isBlank(startUrl));

		final Path screenshotDir = Paths.get("target", "saleads-e2e-screenshots", TEST_NAME + "_" + TIMESTAMP.format(LocalDateTime.now()));
		Files.createDirectories(screenshotDir);

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

		final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
					.setHeadless(Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true")))
					.setSlowMo(Double.valueOf(System.getenv().getOrDefault("SALEADS_SLOWMO_MS", "200")));

			try (Browser browser = playwright.chromium().launch(launchOptions);
					BrowserContext context = browser.newContext(
							new Browser.NewContextOptions().setLocale("es-ES").setViewportSize(1440, 900))) {

				final Page page = context.newPage();
				page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
				page.navigate(startUrl);
				waitForUiLoad(page);

				final boolean loginPassed = stepLoginWithGoogle(context, page, screenshotDir);
				report.put("Login", loginPassed);

				if (loginPassed) {
					final Page appPage = findApplicationPage(context, page, DEFAULT_TIMEOUT_MS);

					final boolean miNegocioMenuPassed = stepOpenMiNegocioMenu(appPage, screenshotDir);
					report.put("Mi Negocio menu", miNegocioMenuPassed);

					final boolean agregarModalPassed = miNegocioMenuPassed && stepValidateAgregarNegocioModal(appPage, screenshotDir);
					report.put("Agregar Negocio modal", agregarModalPassed);

					final boolean administrarViewPassed = stepOpenAdministrarNegocios(appPage, screenshotDir);
					report.put("Administrar Negocios view", administrarViewPassed);

					final boolean infoGeneralPassed = administrarViewPassed && stepValidateInformacionGeneral(appPage);
					report.put("Información General", infoGeneralPassed);

					final boolean detallesCuentaPassed = administrarViewPassed && stepValidateDetallesCuenta(appPage);
					report.put("Detalles de la Cuenta", detallesCuentaPassed);

					final boolean tusNegociosPassed = administrarViewPassed && stepValidateTusNegocios(appPage);
					report.put("Tus Negocios", tusNegociosPassed);

					final LegalValidationResult termsResult = administrarViewPassed
							? stepValidateLegalLink(context, appPage, "Términos y Condiciones",
									new String[] { "text=/T[ée]rminos\\s+y\\s+Condiciones/i",
											"role=heading[name=/T[ée]rminos\\s+y\\s+Condiciones/i]" },
									screenshotDir, "terminos_y_condiciones")
							: LegalValidationResult.failed();
					report.put("Términos y Condiciones", termsResult.passed);
					legalUrls.put("Términos y Condiciones", termsResult.url);

					final LegalValidationResult privacyResult = administrarViewPassed
							? stepValidateLegalLink(context, appPage, "Política de Privacidad",
									new String[] { "text=/Pol[íi]tica\\s+de\\s+Privacidad/i",
											"role=heading[name=/Pol[íi]tica\\s+de\\s+Privacidad/i]" },
									screenshotDir, "politica_de_privacidad")
							: LegalValidationResult.failed();
					report.put("Política de Privacidad", privacyResult.passed);
					legalUrls.put("Política de Privacidad", privacyResult.url);
				}
			}
		}

		printFinalReport(report, legalUrls, screenshotDir);
		assertTrue("At least one required validation failed. Check the final report in test output.",
				report.values().stream().allMatch(Boolean::booleanValue));
	}

	private boolean stepLoginWithGoogle(final BrowserContext context, final Page page, final Path screenshotDir) {
		final Locator loginButton = firstVisible(page, "button:has-text('Sign in with Google')",
				"button:has-text('Iniciar sesión con Google')", "text=/Sign\\s*in\\s*with\\s*Google/i",
				"text=/Iniciar\\s+sesi[oó]n\\s+con\\s+Google/i", "button:has-text('Google')", "text=/Google/i");
		if (loginButton == null) {
			return false;
		}

		final int pagesBefore = context.pages().size();
		clickAndWait(page, loginButton);
		final Page potentialAuthPage = waitForNewPage(context, pagesBefore, 12_000);
		final Page authPage = potentialAuthPage == null ? page : potentialAuthPage;
		waitForUiLoad(authPage);

		final Locator accountOption = firstVisible(authPage, "text=" + ACCOUNT_EMAIL, "role=button[name='" + ACCOUNT_EMAIL + "']");
		if (accountOption != null) {
			clickAndWait(authPage, accountOption);
		}

		final Page appPage = findApplicationPage(context, page, DEFAULT_TIMEOUT_MS);
		final boolean mainInterfaceVisible = isVisible(appPage, "body");
		final boolean leftSidebarVisible = isVisible(appPage, "text=/\\bNegocio\\b/i", "text=/Mi\\s+Negocio/i");

		if (mainInterfaceVisible && leftSidebarVisible) {
			takeScreenshot(appPage, screenshotDir, "01_dashboard_loaded", true);
			return true;
		}

		return false;
	}

	private boolean stepOpenMiNegocioMenu(final Page page, final Path screenshotDir) {
		clickIfVisible(page, "text=/\\bNegocio\\b/i", "role=link[name=/\\bNegocio\\b/i]");
		waitForUiLoad(page);

		final boolean clickedMiNegocio = clickIfVisible(page, "text=/Mi\\s+Negocio/i", "role=link[name=/Mi\\s+Negocio/i]",
				"role=button[name=/Mi\\s+Negocio/i]");
		waitForUiLoad(page);
		if (!clickedMiNegocio) {
			return false;
		}

		final boolean hasAgregar = isVisible(page, "text=/Agregar\\s+Negocio/i");
		final boolean hasAdministrar = isVisible(page, "text=/Administrar\\s+Negocios/i");

		if (hasAgregar && hasAdministrar) {
			takeScreenshot(page, screenshotDir, "02_mi_negocio_menu_expanded", true);
			return true;
		}

		return false;
	}

	private boolean stepValidateAgregarNegocioModal(final Page page, final Path screenshotDir) {
		final boolean opened = clickIfVisible(page, "text=/Agregar\\s+Negocio/i", "role=link[name=/Agregar\\s+Negocio/i]",
				"role=button[name=/Agregar\\s+Negocio/i]");
		waitForUiLoad(page);
		if (!opened) {
			return false;
		}

		final boolean titleVisible = isVisible(page, "text=/Crear\\s+Nuevo\\s+Negocio/i");
		final boolean inputVisible = isVisible(page, "text=/Nombre\\s+del\\s+Negocio/i",
				"input[placeholder*='Nombre del Negocio']", "input[name*='business']");
		final boolean quotaVisible = isVisible(page, "text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i");
		final boolean cancelVisible = isVisible(page, "role=button[name=/Cancelar/i]", "text=/\\bCancelar\\b/i");
		final boolean createVisible = isVisible(page, "role=button[name=/Crear\\s+Negocio/i]", "text=/Crear\\s+Negocio/i");

		if (titleVisible && inputVisible && quotaVisible && cancelVisible && createVisible) {
			takeScreenshot(page, screenshotDir, "03_agregar_negocio_modal", true);
		}

		final Locator input = firstVisible(page, "input[placeholder*='Nombre del Negocio']", "input[name*='business']",
				"input[type='text']");
		if (input != null) {
			input.click();
			input.fill("Negocio Prueba Automatización");
		}
		clickIfVisible(page, "role=button[name=/Cancelar/i]", "text=/\\bCancelar\\b/i");
		waitForUiLoad(page);

		return titleVisible && inputVisible && quotaVisible && cancelVisible && createVisible;
	}

	private boolean stepOpenAdministrarNegocios(final Page page, final Path screenshotDir) {
		if (!isVisible(page, "text=/Administrar\\s+Negocios/i")) {
			clickIfVisible(page, "text=/Mi\\s+Negocio/i");
			waitForUiLoad(page);
		}

		final boolean clicked = clickIfVisible(page, "text=/Administrar\\s+Negocios/i",
				"role=link[name=/Administrar\\s+Negocios/i]", "role=button[name=/Administrar\\s+Negocios/i]");
		waitForUiLoad(page);
		if (!clicked) {
			return false;
		}

		final boolean infoGeneral = isVisible(page, "text=/Informaci[oó]n\\s+General/i");
		final boolean detallesCuenta = isVisible(page, "text=/Detalles\\s+de\\s+la\\s+Cuenta/i");
		final boolean tusNegocios = isVisible(page, "text=/Tus\\s+Negocios/i");
		final boolean legalSection = isVisible(page, "text=/Secci[oó]n\\s+Legal/i");

		if (infoGeneral && detallesCuenta && tusNegocios && legalSection) {
			takeScreenshot(page, screenshotDir, "04_administrar_negocios_view", true);
			return true;
		}

		return false;
	}

	private boolean stepValidateInformacionGeneral(final Page page) {
		final boolean emailVisible = isVisible(page, "text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/");
		final boolean usernameVisible = isVisible(page, "text=/\\bUsuario\\b/i", "text=/\\bNombre\\b/i",
				"text=/Perfil/i");
		final boolean businessPlanVisible = isVisible(page, "text=/BUSINESS\\s+PLAN/i");
		final boolean cambiarPlanVisible = isVisible(page, "role=button[name=/Cambiar\\s+Plan/i]",
				"text=/Cambiar\\s+Plan/i");

		return emailVisible && usernameVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean stepValidateDetallesCuenta(final Page page) {
		final boolean cuentaCreada = isVisible(page, "text=/Cuenta\\s+creada/i");
		final boolean estadoActivo = isVisible(page, "text=/Estado\\s+activo/i");
		final boolean idiomaSeleccionado = isVisible(page, "text=/Idioma\\s+seleccionado/i");

		return cuentaCreada && estadoActivo && idiomaSeleccionado;
	}

	private boolean stepValidateTusNegocios(final Page page) {
		final boolean businessListVisible = isVisible(page, "text=/Tus\\s+Negocios/i");
		final boolean addButtonVisible = isVisible(page, "role=button[name=/Agregar\\s+Negocio/i]",
				"text=/Agregar\\s+Negocio/i");
		final boolean quotaVisible = isVisible(page, "text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i");

		return businessListVisible && addButtonVisible && quotaVisible;
	}

	private LegalValidationResult stepValidateLegalLink(final BrowserContext context, final Page appPage, final String linkText,
			final String[] headingSelectors, final Path screenshotDir, final String screenshotName) {
		final int pagesBefore = context.pages().size();
		final boolean clicked = clickIfVisible(appPage, "text=" + linkText, "role=link[name=/" + linkText + "/i]");
		waitForUiLoad(appPage);
		if (!clicked) {
			return LegalValidationResult.failed();
		}

		Page legalPage = waitForNewPage(context, pagesBefore, 10_000);
		if (legalPage == null) {
			legalPage = appPage;
		}

		waitForUiLoad(legalPage);
		final boolean headingVisible = isVisible(legalPage, headingSelectors);
		final boolean legalContentVisible = hasVisibleTextContent(legalPage);
		final String finalUrl = legalPage.url();
		takeScreenshot(legalPage, screenshotDir, "05_" + screenshotName, true);

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else {
			try {
				appPage.goBack();
				waitForUiLoad(appPage);
			} catch (final Exception ignored) {
				// Best effort cleanup when legal content opened in same tab.
			}
		}

		return new LegalValidationResult(headingVisible && legalContentVisible, finalUrl);
	}

	private Page findApplicationPage(final BrowserContext context, final Page fallbackPage, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			final List<Page> pages = context.pages();
			for (final Page candidate : pages) {
				if (candidate.isClosed()) {
					continue;
				}
				waitForUiLoad(candidate);
				if (isVisible(candidate, "text=/Mi\\s+Negocio/i", "text=/\\bNegocio\\b/i")) {
					return candidate;
				}
			}
			sleep(300);
		}
		return fallbackPage;
	}

	private Page waitForNewPage(final BrowserContext context, final int pageCountBefore, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			final List<Page> pages = context.pages();
			if (pages.size() > pageCountBefore) {
				final Page newest = pages.get(pages.size() - 1);
				if (!newest.isClosed()) {
					return newest;
				}
			}
			sleep(250);
		}
		return null;
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUiLoad(page);
	}

	private boolean clickIfVisible(final Page page, final String... selectors) {
		final Locator locator = firstVisible(page, selectors);
		if (locator == null) {
			return false;
		}
		clickAndWait(page, locator);
		return true;
	}

	private Locator firstVisible(final Page page, final String... selectors) {
		for (final String selector : selectors) {
			final Locator locator = page.locator(selector).first();
			try {
				locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(SHORT_TIMEOUT_MS));
				return locator;
			} catch (final TimeoutError ignored) {
				// Continue trying fallback selectors.
			}
		}
		return null;
	}

	private boolean isVisible(final Page page, final String... selectors) {
		return firstVisible(page, selectors) != null;
	}

	private boolean hasVisibleTextContent(final Page page) {
		try {
			final String text = page.locator("body").innerText();
			return text != null && text.trim().length() > 120;
		} catch (final RuntimeException ex) {
			return false;
		}
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final RuntimeException ignored) {
			// Keep progressing even if this state is already reached.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8_000));
		} catch (final RuntimeException ignored) {
			// Some pages keep network requests alive; this is best-effort.
		}
		page.waitForTimeout(500);
	}

	private void takeScreenshot(final Page page, final Path screenshotDir, final String name, final boolean fullPage) {
		final String safeName = name.replaceAll("[^A-Za-z0-9_\\-]", "_");
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotDir.resolve(safeName + ".png")).setFullPage(fullPage));
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (!isBlank(value)) {
				return value.trim();
			}
		}
		return null;
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private void printFinalReport(final LinkedHashMap<String, Boolean> report, final Map<String, String> legalUrls,
			final Path screenshotDir) {
		System.out.println();
		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			final String status = entry.getValue() ? "PASS" : "FAIL";
			System.out.println(entry.getKey() + ": " + status);
		}
		System.out.println("Términos y Condiciones URL: " + valueOrNA(legalUrls.get("Términos y Condiciones")));
		System.out.println("Política de Privacidad URL: " + valueOrNA(legalUrls.get("Política de Privacidad")));
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
		System.out.println("=======================================");
		System.out.println();
	}

	private String valueOrNA(final String value) {
		return isBlank(value) ? "N/A" : value;
	}

	private static final class LegalValidationResult {
		private final boolean passed;
		private final String url;

		private LegalValidationResult(final boolean passed, final String url) {
			this.passed = passed;
			this.url = url;
		}

		private static LegalValidationResult failed() {
			return new LegalValidationResult(false, null);
		}
	}
}
