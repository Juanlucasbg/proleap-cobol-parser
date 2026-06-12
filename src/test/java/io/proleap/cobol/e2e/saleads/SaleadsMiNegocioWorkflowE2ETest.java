package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

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

/**
 * End-to-end workflow validation for SaleADS "Mi Negocio" module.
 *
 * This test is environment-agnostic:
 * - It reads the start URL from environment variables (no hardcoded domain).
 * - It relies primarily on visible text selectors.
 * - It handles both same-tab and new-tab legal links.
 */
public class SaleadsMiNegocioWorkflowE2ETest {

	private static final double STEP_TIMEOUT_MS = 20000;
	private static final double POPUP_TIMEOUT_MS = 8000;

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Informacion General";
	private static final String DETALLES_DE_LA_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS_Y_CONDICIONES = "Terminos y Condiciones";
	private static final String POLITICA_DE_PRIVACIDAD = "Politica de Privacidad";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue("Set RUN_SALEADS_E2E=true to execute this browser workflow.",
				Boolean.parseBoolean(getEnv("RUN_SALEADS_E2E", "false")));

		final String startUrl = getRequiredEnv("SALEADS_START_URL");
		final String googleEmail = getEnv("SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com");
		final String expectedUserName = getEnv("SALEADS_EXPECTED_USER_NAME", "").trim();
		final boolean headless = Boolean.parseBoolean(getEnv("SALEADS_HEADLESS", "true"));

		final Path evidenceRoot = Paths.get(getEnv("SALEADS_EVIDENCE_DIR", "target/saleads-e2e"));
		final Path evidenceDir = evidenceRoot.resolve(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		final Map<String, String> report = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext();
			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(STEP_TIMEOUT_MS);

			appPage.navigate(startUrl);
			waitForUi(appPage);

			final boolean loginOk = runStep(report, LOGIN, () -> {
				loginWithGoogle(appPage, googleEmail);
				waitForAnyVisible(appPage, "main application interface", "text=/^\\s*Negocio\\s*$/i",
						"text=/Mi\\s+Negocio/i");
				waitForAnyVisible(appPage, "left sidebar navigation", "aside", "[role='navigation']", "nav");
				screenshot(appPage, evidenceDir.resolve("01-dashboard-loaded.png"), false);
				return "Dashboard loaded, sidebar visible.";
			});

			final boolean menuOk = runStepIfPrerequisite(report, loginOk, MI_NEGOCIO_MENU, LOGIN, () -> {
				openMiNegocioMenu(appPage);
				waitForAnyVisible(appPage, "Agregar Negocio item", "text=/^\\s*Agregar\\s+Negocio\\s*$/i");
				waitForAnyVisible(appPage, "Administrar Negocios item", "text=/^\\s*Administrar\\s+Negocios\\s*$/i");
				screenshot(appPage, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
				return "Submenu expanded with expected entries.";
			});

			runStepIfPrerequisite(report, menuOk, AGREGAR_NEGOCIO_MODAL, MI_NEGOCIO_MENU, () -> {
				openAgregarNegocioModalAndClose(appPage, evidenceDir);
				return "Modal validated and closed with Cancelar.";
			});

			final boolean administrarOk = runStepIfPrerequisite(report, menuOk, ADMINISTRAR_NEGOCIOS_VIEW,
					MI_NEGOCIO_MENU, () -> {
						openAdministrarNegocios(appPage);
						waitForAnyVisible(appPage, "Informacion General section",
								"text=/Informaci[oó]n\\s+General/i");
						waitForAnyVisible(appPage, "Detalles de la Cuenta section",
								"text=/Detalles\\s+de\\s+la\\s+Cuenta/i");
						waitForAnyVisible(appPage, "Tus Negocios section", "text=/Tus\\s+Negocios/i");
						waitForAnyVisible(appPage, "Seccion Legal section", "text=/Secci[oó]n\\s+Legal/i");
						screenshot(appPage, evidenceDir.resolve("04-administrar-negocios-account-page.png"), true);
						return "Account page loaded with required sections.";
					});

			runStepIfPrerequisite(report, administrarOk, INFORMACION_GENERAL, ADMINISTRAR_NEGOCIOS_VIEW, () -> {
				waitForAnyVisible(appPage, "Informacion General header", "text=/Informaci[oó]n\\s+General/i");

				if (!expectedUserName.isBlank()) {
					waitForAnyVisible(appPage, "user name", "text=/" + Pattern.quote(expectedUserName) + "/i");
				} else {
					waitForAnyVisible(appPage, "user name label", "text=/Nombre/i", "text=/Usuario/i");
				}

				waitForAnyVisible(appPage, "user email",
						"text=/" + Pattern.quote(googleEmail) + "/i",
						"text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i");
				waitForAnyVisible(appPage, "BUSINESS PLAN text", "text=/BUSINESS\\s+PLAN/i");
				waitForAnyVisible(appPage, "Cambiar Plan button", "text=/Cambiar\\s+Plan/i");
				return "Informacion General validated.";
			});

			runStepIfPrerequisite(report, administrarOk, DETALLES_DE_LA_CUENTA, ADMINISTRAR_NEGOCIOS_VIEW, () -> {
				waitForAnyVisible(appPage, "Cuenta creada", "text=/Cuenta\\s+creada/i");
				waitForAnyVisible(appPage, "Estado activo", "text=/Estado\\s+activo/i");
				waitForAnyVisible(appPage, "Idioma seleccionado", "text=/Idioma\\s+seleccionado/i");
				return "Detalles de la Cuenta validated.";
			});

			runStepIfPrerequisite(report, administrarOk, TUS_NEGOCIOS, ADMINISTRAR_NEGOCIOS_VIEW, () -> {
				waitForAnyVisible(appPage, "Tus Negocios header", "text=/Tus\\s+Negocios/i");
				waitForAnyVisible(appPage, "Agregar Negocio button", "text=/^\\s*Agregar\\s+Negocio\\s*$/i");
				waitForAnyVisible(appPage, "business quota text", "text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i");
				waitForAnyVisible(appPage, "business list content", "text=/negocio/i", "table", "ul");
				return "Tus Negocios content validated.";
			});

			runStepIfPrerequisite(report, administrarOk, TERMINOS_Y_CONDICIONES, ADMINISTRAR_NEGOCIOS_VIEW, () -> {
				final String termsUrl = openAndValidateLegalDocument(appPage, context,
						"text=/T[eé]rminos\\s+y\\s+Condiciones/i",
						"text=/T[eé]rminos\\s+y\\s+Condiciones/i",
						evidenceDir.resolve("08-terminos-y-condiciones.png"));
				return "Validated legal page. URL: " + termsUrl;
			});

			runStepIfPrerequisite(report, administrarOk, POLITICA_DE_PRIVACIDAD, ADMINISTRAR_NEGOCIOS_VIEW, () -> {
				final String privacyUrl = openAndValidateLegalDocument(appPage, context,
						"text=/Pol[ií]tica\\s+de\\s+Privacidad/i",
						"text=/Pol[ií]tica\\s+de\\s+Privacidad/i",
						evidenceDir.resolve("09-politica-de-privacidad.png"));
				return "Validated legal page. URL: " + privacyUrl;
			});
		}

		final String finalReport = buildFinalReport(report, evidenceDir);
		System.out.println(finalReport);

		assertTrue("SaleADS Mi Negocio workflow has failed validations.\n" + finalReport, allStepsPassed(report));
	}

	private void loginWithGoogle(final Page appPage, final String googleEmail) {
		final Locator loginButton = waitForAnyVisible(appPage, "Sign in with Google button",
				"button:has-text(\"Sign in with Google\")",
				"button:has-text(\"Iniciar sesion con Google\")",
				"button:has-text(\"Iniciar sesi\")",
				"text=/Sign\\s*in\\s*with\\s*Google/i",
				"text=/Google/i");

		Page popup = null;
		try {
			popup = appPage.waitForPopup(() -> loginButton.click(),
					new Page.WaitForPopupOptions().setTimeout(POPUP_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// If no popup opens, login likely continues in the same tab.
			waitForUi(appPage);
		}

		final Page authPage = popup != null ? popup : appPage;
		waitForUi(authPage);

		final Locator accountOption = authPage.locator("text=/" + Pattern.quote(googleEmail) + "/i").first();
		if (isVisible(accountOption)) {
			accountOption.click();
			waitForUi(authPage);
		}

		if (popup != null) {
			try {
				popup.waitForClose(new Page.WaitForCloseOptions().setTimeout(STEP_TIMEOUT_MS));
			} catch (final PlaywrightException ignored) {
				// Popup can remain open in some identity flows; bring app tab to front anyway.
			}
			appPage.bringToFront();
			waitForUi(appPage);
		}
	}

	private void openMiNegocioMenu(final Page page) {
		final Locator negocioSection = waitForAnyVisible(page, "Negocio section",
				"aside >> text=/^\\s*Negocio\\s*$/i",
				"text=/^\\s*Negocio\\s*$/i");
		clickAndWaitForUi(page, negocioSection);

		final Locator miNegocio = waitForAnyVisible(page, "Mi Negocio option",
				"aside >> text=/^\\s*Mi\\s+Negocio\\s*$/i",
				"text=/^\\s*Mi\\s+Negocio\\s*$/i");
		clickAndWaitForUi(page, miNegocio);
	}

	private void openAgregarNegocioModalAndClose(final Page page, final Path evidenceDir) {
		ensureMiNegocioExpanded(page);

		final Locator agregarNegocio = waitForAnyVisible(page, "Agregar Negocio option",
				"text=/^\\s*Agregar\\s+Negocio\\s*$/i");
		clickAndWaitForUi(page, agregarNegocio);

		waitForAnyVisible(page, "Crear Nuevo Negocio title", "text=/Crear\\s+Nuevo\\s+Negocio/i");
		final Locator nombreNegocioInput = waitForAnyVisible(page, "Nombre del Negocio input",
				"input[placeholder*=\"Nombre del Negocio\"]",
				"label:has-text(\"Nombre del Negocio\") >> xpath=following::input[1]",
				"input[name*=\"nombre\"]");
		waitForAnyVisible(page, "business quota text", "text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i");
		waitForAnyVisible(page, "Cancelar button", "text=/^\\s*Cancelar\\s*$/i");
		waitForAnyVisible(page, "Crear Negocio button", "text=/Crear\\s+Negocio/i");
		screenshot(page, evidenceDir.resolve("03-crear-nuevo-negocio-modal.png"), false);

		nombreNegocioInput.click();
		waitForUi(page);
		nombreNegocioInput.fill("Negocio Prueba Automatizacion");
		waitForUi(page);

		final Locator cancelar = waitForAnyVisible(page, "Cancelar button", "text=/^\\s*Cancelar\\s*$/i");
		clickAndWaitForUi(page, cancelar);
	}

	private void openAdministrarNegocios(final Page page) {
		ensureMiNegocioExpanded(page);

		final Locator administrar = waitForAnyVisible(page, "Administrar Negocios option",
				"text=/^\\s*Administrar\\s+Negocios\\s*$/i");
		clickAndWaitForUi(page, administrar);
	}

	private String openAndValidateLegalDocument(final Page appPage, final BrowserContext context,
			final String linkSelector, final String headingSelector, final Path screenshotPath) {
		ensureMiNegocioExpanded(appPage);

		final Locator legalLink = waitForAnyVisible(appPage, "legal link: " + linkSelector, linkSelector);
		final String appUrlBefore = appPage.url();

		Page targetPage = appPage;
		boolean openedInNewTab = false;

		try {
			targetPage = context.waitForPage(() -> legalLink.click(),
					new BrowserContext.WaitForPageOptions().setTimeout(POPUP_TIMEOUT_MS));
			openedInNewTab = true;
		} catch (final PlaywrightException ignored) {
			// If no new tab opens, we stay in the application tab.
			waitForUi(appPage);
		}

		waitForUi(targetPage);
		waitForAnyVisible(targetPage, "legal heading", headingSelector);

		final String bodyText = targetPage.textContent("body");
		if (bodyText == null || bodyText.trim().length() < 120) {
			throw new AssertionError("Legal content text is not sufficiently visible.");
		}

		screenshot(targetPage, screenshotPath, true);
		final String finalUrl = targetPage.url();

		if (openedInNewTab) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				appPage.goBack(new Page.GoBackOptions().setTimeout(STEP_TIMEOUT_MS));
			} catch (final PlaywrightException ignored) {
				appPage.navigate(appUrlBefore);
			}
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void ensureMiNegocioExpanded(final Page page) {
		if (!isAnyVisible(page, "text=/^\\s*Agregar\\s+Negocio\\s*$/i", "text=/^\\s*Administrar\\s+Negocios\\s*$/i")) {
			final Locator miNegocio = waitForAnyVisible(page, "Mi Negocio option",
					"aside >> text=/^\\s*Mi\\s+Negocio\\s*$/i",
					"text=/^\\s*Mi\\s+Negocio\\s*$/i");
			clickAndWaitForUi(page, miNegocio);
		}
	}

	private boolean runStep(final Map<String, String> report, final String stepName, final StepAction action) {
		try {
			final String details = action.run();
			report.put(stepName, details == null || details.isBlank() ? "PASS" : "PASS - " + details);
			return true;
		} catch (final Throwable t) {
			report.put(stepName, "FAIL - " + sanitizeFailureMessage(t));
			return false;
		}
	}

	private boolean runStepIfPrerequisite(final Map<String, String> report, final boolean prerequisiteOk,
			final String stepName, final String prerequisiteName, final StepAction action) {
		if (!prerequisiteOk) {
			report.put(stepName, "FAIL - Prerequisite step failed: " + prerequisiteName);
			return false;
		}
		return runStep(report, stepName, action);
	}

	private String buildFinalReport(final Map<String, String> report, final Path evidenceDir) {
		final StringBuilder sb = new StringBuilder();
		sb.append(System.lineSeparator());
		sb.append("SaleADS Mi Negocio Workflow Final Report").append(System.lineSeparator());
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		sb.append(System.lineSeparator());

		for (final String step : Arrays.asList(LOGIN, MI_NEGOCIO_MENU, AGREGAR_NEGOCIO_MODAL, ADMINISTRAR_NEGOCIOS_VIEW,
				INFORMACION_GENERAL, DETALLES_DE_LA_CUENTA, TUS_NEGOCIOS, TERMINOS_Y_CONDICIONES, POLITICA_DE_PRIVACIDAD)) {
			sb.append("- ").append(step).append(": ").append(report.getOrDefault(step, "FAIL - Not executed"))
					.append(System.lineSeparator());
		}

		return sb.toString();
	}

	private boolean allStepsPassed(final Map<String, String> report) {
		for (final String step : Arrays.asList(LOGIN, MI_NEGOCIO_MENU, AGREGAR_NEGOCIO_MODAL, ADMINISTRAR_NEGOCIOS_VIEW,
				INFORMACION_GENERAL, DETALLES_DE_LA_CUENTA, TUS_NEGOCIOS, TERMINOS_Y_CONDICIONES, POLITICA_DE_PRIVACIDAD)) {
			final String value = report.get(step);
			if (value == null || !value.startsWith("PASS")) {
				return false;
			}
		}
		return true;
	}

	private Locator waitForAnyVisible(final Page page, final String description, final String... selectors) {
		final long deadline = System.currentTimeMillis() + (long) STEP_TIMEOUT_MS;

		while (System.currentTimeMillis() <= deadline) {
			for (final String selector : selectors) {
				final Locator locator = page.locator(selector).first();
				if (isVisible(locator)) {
					return locator;
				}
			}

			page.waitForTimeout(200);
		}

		throw new AssertionError("Could not find visible element for '" + description + "' using selectors: "
				+ Arrays.toString(selectors));
	}

	private boolean isAnyVisible(final Page page, final String... selectors) {
		for (final String selector : selectors) {
			if (isVisible(page.locator(selector).first())) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.count() > 0 && locator.isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void clickAndWaitForUi(final Page page, final Locator locator) {
		locator.click(new Locator.ClickOptions().setTimeout(STEP_TIMEOUT_MS));
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// Some SPA transitions won't trigger a classic load event.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(STEP_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// Network can remain active due to telemetry or websocket traffic.
		}

		page.waitForTimeout(500);
	}

	private void screenshot(final Page page, final Path path, final boolean fullPage) {
		try {
			Files.createDirectories(path.getParent());
		} catch (final Exception e) {
			throw new RuntimeException("Could not create screenshot directory: " + path.getParent(), e);
		}

		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private String sanitizeFailureMessage(final Throwable throwable) {
		final String message = throwable.getMessage();
		if (message == null || message.isBlank()) {
			return throwable.getClass().getSimpleName();
		}

		final String singleLine = message.replaceAll("\\s+", " ").trim();
		return singleLine.length() <= 220 ? singleLine : singleLine.substring(0, 220) + "...";
	}

	private String getRequiredEnv(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Required environment variable is missing: " + key);
		}
		return value.trim();
	}

	private String getEnv(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value.trim();
	}

	@FunctionalInterface
	private interface StepAction {
		String run() throws Exception;
	}
}
