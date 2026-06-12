package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Assume;
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
import com.microsoft.playwright.options.WaitUntilState;

/**
 * End-to-end workflow for SaleADS "Mi Negocio" module.
 *
 * Required environment variables:
 * - SALEADS_E2E_ENABLED=true
 * - SALEADS_LOGIN_URL=<current environment login URL>
 *
 * Optional environment variables:
 * - SALEADS_GOOGLE_ACCOUNT (default: juanlucasbarbiergarzon@gmail.com)
 * - SALEADS_EXPECTED_USER_NAME
 * - SALEADS_HEADLESS (default: true)
 */
public class SaleAdsMiNegocioFullTest {

	private static final long SHORT_TIMEOUT_MS = 4_000;
	private static final long DEFAULT_TIMEOUT_MS = 15_000;
	private static final long LONG_TIMEOUT_MS = 90_000;
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this test.",
				"true".equalsIgnoreCase(readEnv("SALEADS_E2E_ENABLED")));

		final String loginUrl = readEnv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL with the current environment login page URL.",
				loginUrl != null && !loginUrl.isBlank());

		final String googleAccount = readEnvOrDefault("SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);
		final String expectedUserName = readEnv("SALEADS_EXPECTED_USER_NAME");
		final boolean headless = Boolean.parseBoolean(readEnvOrDefault("SALEADS_HEADLESS", "true"));

		final Path evidenceDir = Paths.get("target", "saleads-evidence", TIMESTAMP_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		final Map<String, Boolean> results = createReportTemplate();
		final Map<String, String> failures = new LinkedHashMap<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(150));
			final BrowserContext context = browser.newContext();
			final Page page = context.newPage();

			runStep(REPORT_LOGIN, results, failures, () -> {
				page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
				waitForUi(page);

				loginWithGoogle(page, context, googleAccount);
				waitForAnyVisibleText(page, List.of("Negocio", "Mi Negocio"), LONG_TIMEOUT_MS);
				assertTrue("Left sidebar navigation should be visible.",
						isVisibleByVisibleText(page, "Negocio", DEFAULT_TIMEOUT_MS)
								|| isVisibleByCss(page, "aside", DEFAULT_TIMEOUT_MS));

				screenshot(page, evidenceDir, "01_dashboard_loaded.png", true);
			});

			runStep(REPORT_MI_NEGOCIO_MENU, results, failures, () -> {
				ensureMiNegocioMenuExpanded(page);
				assertVisibleText(page, "Agregar Negocio");
				assertVisibleText(page, "Administrar Negocios");

				screenshot(page, evidenceDir, "02_mi_negocio_menu_expanded.png", true);
			});

			runStep(REPORT_AGREGAR_NEGOCIO_MODAL, results, failures, () -> {
				clickFirstVisibleText(page, List.of("Agregar Negocio"));
				waitForAnyVisibleText(page, List.of("Crear Nuevo Negocio"), DEFAULT_TIMEOUT_MS);

				assertVisibleText(page, "Crear Nuevo Negocio");
				assertTrue("Input 'Nombre del Negocio' must exist.",
						isVisibleByPlaceholder(page, "Nombre del Negocio", DEFAULT_TIMEOUT_MS)
								|| isVisibleByVisibleText(page, "Nombre del Negocio", DEFAULT_TIMEOUT_MS));
				assertVisibleText(page, "Tienes 2 de 3 negocios");
				assertVisibleText(page, "Cancelar");
				assertVisibleText(page, "Crear Negocio");

				screenshot(page, evidenceDir, "03_agregar_negocio_modal.png", true);

				fillByPlaceholder(page, "Nombre del Negocio", "Negocio Prueba Automatizacion");
				clickFirstVisibleText(page, List.of("Cancelar"));
				waitForUi(page);
			});

			runStep(REPORT_ADMINISTRAR_NEGOCIOS, results, failures, () -> {
				ensureMiNegocioMenuExpanded(page);
				clickFirstVisibleText(page, List.of("Administrar Negocios"));

				waitForAnyVisibleText(page,
						List.of("Información General", "Detalles de la Cuenta", "Tus Negocios", "Sección Legal"),
						DEFAULT_TIMEOUT_MS);
				assertVisibleText(page, "Información General");
				assertVisibleText(page, "Detalles de la Cuenta");
				assertVisibleText(page, "Tus Negocios");
				assertVisibleText(page, "Sección Legal");

				screenshot(page, evidenceDir, "04_administrar_negocios_view.png", true);
			});

			runStep(REPORT_INFO_GENERAL, results, failures, () -> {
				assertVisibleText(page, "Información General");
				assertTrue("User email should be visible.",
						isVisibleByRegex(page, "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", DEFAULT_TIMEOUT_MS));
				assertTrue("User name should be visible (or provide SALEADS_EXPECTED_USER_NAME).",
						(expectedUserName != null && !expectedUserName.isBlank()
								&& isVisibleByVisibleText(page, expectedUserName, DEFAULT_TIMEOUT_MS))
								|| isVisibleByAnyText(page, List.of("Nombre", "Usuario", "Perfil"), DEFAULT_TIMEOUT_MS));
				assertVisibleText(page, "BUSINESS PLAN");
				assertVisibleText(page, "Cambiar Plan");
			});

			runStep(REPORT_DETALLES_CUENTA, results, failures, () -> {
				assertVisibleText(page, "Cuenta creada");
				assertVisibleText(page, "Estado activo");
				assertVisibleText(page, "Idioma seleccionado");
			});

			runStep(REPORT_TUS_NEGOCIOS, results, failures, () -> {
				assertVisibleText(page, "Tus Negocios");
				assertVisibleText(page, "Agregar Negocio");
				assertVisibleText(page, "Tienes 2 de 3 negocios");
				assertTrue("Business list should be visible.", isBusinessListVisible(page));
			});

			runStep(REPORT_TERMINOS, results, failures, () -> {
				final String finalUrl = validateLegalLink(context, page, "Términos y Condiciones",
						"terminos_y_condiciones.png", evidenceDir);
				legalUrls.put("Términos y Condiciones URL", finalUrl);
			});

			runStep(REPORT_PRIVACIDAD, results, failures, () -> {
				final String finalUrl = validateLegalLink(context, page, "Política de Privacidad", "politica_privacidad.png",
						evidenceDir);
				legalUrls.put("Política de Privacidad URL", finalUrl);
			});
		}

		final String report = buildReport(results, failures, legalUrls, evidenceDir);
		System.out.println(report);

		final boolean allPassed = results.values().stream().allMatch(Boolean::booleanValue);
		if (!allPassed) {
			fail(report);
		}
	}

	private Map<String, Boolean> createReportTemplate() {
		final Map<String, Boolean> results = new LinkedHashMap<>();
		results.put(REPORT_LOGIN, false);
		results.put(REPORT_MI_NEGOCIO_MENU, false);
		results.put(REPORT_AGREGAR_NEGOCIO_MODAL, false);
		results.put(REPORT_ADMINISTRAR_NEGOCIOS, false);
		results.put(REPORT_INFO_GENERAL, false);
		results.put(REPORT_DETALLES_CUENTA, false);
		results.put(REPORT_TUS_NEGOCIOS, false);
		results.put(REPORT_TERMINOS, false);
		results.put(REPORT_PRIVACIDAD, false);
		return results;
	}

	private void runStep(final String reportField, final Map<String, Boolean> results, final Map<String, String> failures,
			final CheckedRunnable step) {
		try {
			step.run();
			results.put(reportField, true);
		} catch (final Throwable throwable) {
			results.put(reportField, false);
			failures.put(reportField, throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
		}
	}

	private void loginWithGoogle(final Page page, final BrowserContext context, final String googleAccount) {
		Page popup = null;
		try {
			popup = context.waitForPage(() -> clickFirstVisibleTextWithoutWaiting(page,
					List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Login with Google")),
					new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final PlaywrightException noPopupOpened) {
			clickFirstVisibleText(page,
					List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Login with Google"));
		}

		if (popup != null) {
			waitForUi(popup);
			selectGoogleAccountIfVisible(popup, googleAccount);
			try {
				popup.waitForClose(new Page.WaitForCloseOptions().setTimeout(45_000));
			} catch (final PlaywrightException ignored) {
				// If popup does not close automatically, main app might still be logged in.
			}
		} else {
			selectGoogleAccountIfVisible(page, googleAccount);
		}

		waitForUi(page);
	}

	private void selectGoogleAccountIfVisible(final Page page, final String googleAccount) {
		if (isVisibleByVisibleText(page, googleAccount, 8_000)) {
			clickByVisibleText(page, googleAccount, true);
			return;
		}

		final String currentUrl = page.url().toLowerCase(Locale.ROOT);
		final boolean googleChooserVisible = currentUrl.contains("accounts.google.")
				|| isVisibleByAnyText(page, List.of("Choose an account", "Elige una cuenta"), SHORT_TIMEOUT_MS);
		if (googleChooserVisible) {
			throw new AssertionError("Google account selector appeared but account was not found: " + googleAccount);
		}
	}

	private void ensureMiNegocioMenuExpanded(final Page page) {
		if (isVisibleByVisibleText(page, "Agregar Negocio", SHORT_TIMEOUT_MS)
				&& isVisibleByVisibleText(page, "Administrar Negocios", SHORT_TIMEOUT_MS)) {
			return;
		}

		if (isVisibleByVisibleText(page, "Mi Negocio", SHORT_TIMEOUT_MS)) {
			clickByVisibleText(page, "Mi Negocio", true);
		} else {
			clickByVisibleText(page, "Negocio", true);
			clickByVisibleText(page, "Mi Negocio", true);
		}

		waitForAnyVisibleText(page, List.of("Agregar Negocio", "Administrar Negocios"), DEFAULT_TIMEOUT_MS);
	}

	private String validateLegalLink(final BrowserContext context, final Page applicationPage, final String linkText,
			final String screenshotName, final Path evidenceDir) {
		Page legalPage = null;
		boolean openedInNewTab = false;

		try {
			legalPage = context.waitForPage(() -> clickByVisibleText(applicationPage, linkText, false),
					new BrowserContext.WaitForPageOptions().setTimeout(6_000));
			openedInNewTab = true;
		} catch (final PlaywrightException noPopupOpened) {
			clickByVisibleText(applicationPage, linkText, true);
			legalPage = applicationPage;
		}

		waitForUi(legalPage);
		assertVisibleText(legalPage, linkText);

		final String bodyText = legalPage.locator("body").innerText();
		assertTrue("Legal content text should be visible for: " + linkText, bodyText != null && bodyText.length() > 200);

		screenshot(legalPage, evidenceDir, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (openedInNewTab) {
			legalPage.close();
			applicationPage.bringToFront();
			waitForUi(applicationPage);
		} else {
			legalPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(applicationPage);
		}

		return finalUrl;
	}

	private void clickFirstVisibleText(final Page page, final List<String> texts) {
		for (final String text : texts) {
			try {
				clickByVisibleText(page, text, true);
				return;
			} catch (final AssertionError ignored) {
				// Try the next candidate text.
			}
		}
		throw new AssertionError("Could not click any of these texts: " + texts);
	}

	private void clickFirstVisibleTextWithoutWaiting(final Page page, final List<String> texts) {
		for (final String text : texts) {
			try {
				clickByVisibleText(page, text, false);
				return;
			} catch (final AssertionError ignored) {
				// Try the next candidate text.
			}
		}
		throw new AssertionError("Could not click any of these texts: " + texts);
	}

	private void clickByVisibleText(final Page page, final String text, final boolean waitForLoad) {
		for (final Locator locator : textLocators(page, text)) {
			try {
				locator.first().click(new Locator.ClickOptions().setTimeout(SHORT_TIMEOUT_MS));
				if (waitForLoad) {
					waitForUi(page);
				}
				return;
			} catch (final PlaywrightException ignored) {
				// Try the next strategy.
			}
		}
		throw new AssertionError("Could not click visible text: " + text);
	}

	private void fillByPlaceholder(final Page page, final String placeholder, final String value) {
		final Locator field = page.getByPlaceholder(placeholder).first();
		field.fill(value, new Locator.FillOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUi(page);
	}

	private boolean isVisibleByPlaceholder(final Page page, final String placeholder, final long timeoutMs) {
		try {
			return page.getByPlaceholder(placeholder).first().isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private boolean isVisibleByCss(final Page page, final String cssSelector, final long timeoutMs) {
		try {
			return page.locator(cssSelector).first().isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void assertVisibleText(final Page page, final String text) {
		waitForAnyVisibleText(page, List.of(text), DEFAULT_TIMEOUT_MS);
		assertTrue("Expected text to be visible: " + text, isVisibleByVisibleText(page, text, DEFAULT_TIMEOUT_MS));
	}

	private boolean isVisibleByAnyText(final Page page, final List<String> textOptions, final long timeoutMs) {
		for (final String text : textOptions) {
			if (isVisibleByVisibleText(page, text, timeoutMs)) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisibleByVisibleText(final Page page, final String text, final long timeoutMs) {
		for (final Locator locator : textLocators(page, text)) {
			try {
				if (locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs))) {
					return true;
				}
			} catch (final PlaywrightException ignored) {
				// Try the next strategy.
			}
		}
		return false;
	}

	private boolean isVisibleByRegex(final Page page, final String regex, final long timeoutMs) {
		try {
			return page.locator("text=/" + regex + "/").first().isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private List<Locator> textLocators(final Page page, final String text) {
		final List<Locator> locators = new ArrayList<>();
		locators.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text).setExact(true)));
		locators.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text).setExact(true)));
		locators.add(page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(text).setExact(true)));
		locators.add(page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(text).setExact(true)));
		locators.add(page.getByText(text, new Page.GetByTextOptions().setExact(true)));
		locators.add(page.getByText(text));
		return locators;
	}

	private void waitForAnyVisibleText(final Page page, final List<String> textOptions, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final String text : textOptions) {
				if (isVisibleByVisibleText(page, text, 500)) {
					return;
				}
			}
			page.waitForTimeout(250);
		}
		throw new AssertionError("None of the expected texts became visible: " + textOptions);
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// Some screens keep background requests alive; DOM readiness is enough here.
		}
		page.waitForTimeout(300);
	}

	private void screenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		page.screenshot(
				new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage).setTimeout(30_000));
	}

	private boolean isBusinessListVisible(final Page page) {
		return hasVisibleElement(page, "[data-testid*='business']", 10)
				|| hasVisibleElement(page, "[class*='business']", 10)
				|| hasVisibleElement(page, "table tbody tr", 10)
				|| hasVisibleElement(page, "[role='row']", 10)
				|| hasVisibleElement(page, "[role='listitem']", 10);
	}

	private boolean hasVisibleElement(final Page page, final String selector, final int maxChecks) {
		final Locator locator = page.locator(selector);
		final int count = Math.min(locator.count(), maxChecks);
		for (int i = 0; i < count; i++) {
			try {
				if (locator.nth(i).isVisible()) {
					return true;
				}
			} catch (final PlaywrightException ignored) {
				// Skip detached or stale element and continue checking.
			}
		}
		return false;
	}

	private String buildReport(final Map<String, Boolean> results, final Map<String, String> failures,
			final Map<String, String> legalUrls, final Path evidenceDir) {
		final StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio Full Test Report").append(System.lineSeparator());
		report.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		report.append(System.lineSeparator());
		report.append("Validation Results:").append(System.lineSeparator());

		for (final Map.Entry<String, Boolean> entry : results.entrySet()) {
			report.append("- ").append(entry.getKey()).append(": ").append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL")
					.append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			report.append(System.lineSeparator());
			report.append("Captured URLs:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		if (!failures.isEmpty()) {
			report.append(System.lineSeparator());
			report.append("Failure Details:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : failures.entrySet()) {
				report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		return report.toString();
	}

	private String readEnv(final String key) {
		return System.getenv(key);
	}

	private String readEnvOrDefault(final String key, final String fallback) {
		final String value = readEnv(key);
		return value == null || value.isBlank() ? fallback : value;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
