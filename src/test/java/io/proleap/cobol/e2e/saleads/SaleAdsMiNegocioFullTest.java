package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * Full workflow test for SaleADS "Mi Negocio" module.
 *
 * <p>
 * Notes:
 * <ul>
 * <li>Environment-agnostic: URL is read from SALEADS_LOGIN_URL or -Dsaleads.login.url.</li>
 * <li>Selectors are mostly text-based to stay resilient across environments.</li>
 * <li>A final PASS/FAIL report is always generated under target/saleads-mi-negocio/&lt;timestamp&gt;.</li>
 * </ul>
 */
public class SaleAdsMiNegocioFullTest {

	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String LOGIN_URL_PROPERTY = "saleads.login.url";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String HEADLESS_PROPERTY = "saleads.headless";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final int SHORT_WAIT_MS = 10000;
	private static final int MEDIUM_WAIT_MS = 20000;
	private static final int LONG_WAIT_MS = 90000;

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
			.withZone(ZoneOffset.UTC);

	private enum Status {
		PASS, FAIL
	}

	private static class StepResult {
		private final String label;
		private Status status = Status.FAIL;
		private String details = "Not executed.";
		private final List<String> evidence = new ArrayList<>();
		private final Map<String, String> metadata = new LinkedHashMap<>();

		private StepResult(final String label) {
			this.label = label;
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() throws IOException {
		final String runId = TIMESTAMP_FORMAT.format(Instant.now());
		final Path runDir = Files.createDirectories(Path.of("target", "saleads-mi-negocio", runId));
		final Path screenshotsDir = Files.createDirectories(runDir.resolve("screenshots"));
		final Path markdownReportPath = runDir.resolve("report.md");
		final Path jsonReportPath = runDir.resolve("report.json");

		final Map<String, StepResult> report = initializeReport();
		final String loginUrl = getLoginUrl();
		report.get("Login").metadata.put("configuredLoginUrl", loginUrl == null ? "<missing>" : loginUrl);

		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
					.setHeadless(resolveHeadless());
			final Browser browser = playwright.chromium().launch(launchOptions);
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			Page appPage = context.newPage();

			// STEP 1: Login with Google
			final boolean loginOk = performLoginStep(report.get("Login"), context, appPage, loginUrl, screenshotsDir);
			if (!loginOk) {
				markAsPrerequisiteFailed(report, Arrays.asList("Mi Negocio menu", "Agregar Negocio modal",
						"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
						"Términos y Condiciones", "Política de Privacidad"), "Login");
				return;
			}

			appPage = findPageWithAnyVisibleText(context,
					Arrays.asList("Negocio", "Mi\\s+Negocio", "Administrar\\s+Negocios", "Dashboard"), appPage);

			// STEP 2: Open Mi Negocio menu
			final boolean miNegocioMenuOk = performMiNegocioMenuStep(report.get("Mi Negocio menu"), appPage,
					screenshotsDir);
			if (!miNegocioMenuOk) {
				markAsPrerequisiteFailed(report,
						Arrays.asList("Agregar Negocio modal", "Administrar Negocios view", "Información General",
								"Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones",
								"Política de Privacidad"),
						"Mi Negocio menu");
				return;
			}

			// STEP 3: Validate Agregar Negocio modal
			final boolean agregarModalOk = performAgregarNegocioModalStep(report.get("Agregar Negocio modal"), appPage,
					screenshotsDir);
			if (!agregarModalOk) {
				markAsPrerequisiteFailed(report, Arrays.asList("Administrar Negocios view", "Información General",
						"Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones", "Política de Privacidad"),
						"Agregar Negocio modal");
				return;
			}

			// STEP 4: Open Administrar Negocios
			final boolean administrarOk = performAdministrarNegociosStep(report.get("Administrar Negocios view"), appPage,
					screenshotsDir);
			if (!administrarOk) {
				markAsPrerequisiteFailed(report, Arrays.asList("Información General", "Detalles de la Cuenta",
						"Tus Negocios", "Términos y Condiciones", "Política de Privacidad"), "Administrar Negocios view");
				return;
			}

			// STEP 5: Validate Información General
			performInformacionGeneralStep(report.get("Información General"), appPage);

			// STEP 6: Validate Detalles de la Cuenta
			performDetallesCuentaStep(report.get("Detalles de la Cuenta"), appPage);

			// STEP 7: Validate Tus Negocios
			performTusNegociosStep(report.get("Tus Negocios"), appPage);

			// STEP 8: Validate Términos y Condiciones
			performLegalPageStep(report.get("Términos y Condiciones"), context, appPage, screenshotsDir,
					"T[eé]rminos\\s+y\\s+Condiciones", "step8_terminos_y_condiciones");

			// STEP 9: Validate Política de Privacidad
			performLegalPageStep(report.get("Política de Privacidad"), context, appPage, screenshotsDir,
					"Pol[ií]tica\\s+de\\s+Privacidad", "step9_politica_de_privacidad");
		} catch (final Exception ex) {
			recordUnhandledFailure(report, ex);
		} finally {
			writeMarkdownReport(markdownReportPath, runId, report);
			writeJsonReport(jsonReportPath, runId, report);
		}

		assertTrue(buildFailureSummary(report), allPass(report));
	}

	private Map<String, StepResult> initializeReport() {
		final Map<String, StepResult> report = new LinkedHashMap<>();
		report.put("Login", new StepResult("Login"));
		report.put("Mi Negocio menu", new StepResult("Mi Negocio menu"));
		report.put("Agregar Negocio modal", new StepResult("Agregar Negocio modal"));
		report.put("Administrar Negocios view", new StepResult("Administrar Negocios view"));
		report.put("Información General", new StepResult("Información General"));
		report.put("Detalles de la Cuenta", new StepResult("Detalles de la Cuenta"));
		report.put("Tus Negocios", new StepResult("Tus Negocios"));
		report.put("Términos y Condiciones", new StepResult("Términos y Condiciones"));
		report.put("Política de Privacidad", new StepResult("Política de Privacidad"));
		return report;
	}

	private boolean performLoginStep(final StepResult step, final BrowserContext context, final Page initialPage,
			final String loginUrl, final Path screenshotsDir) {
		if (loginUrl == null || loginUrl.isBlank()) {
			step.status = Status.FAIL;
			step.details = "Missing login URL. Configure SALEADS_LOGIN_URL or -Dsaleads.login.url.";
			return false;
		}

		Page activePage = initialPage;

		try {
			activePage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUiLoad(activePage);
			step.evidence.add(captureScreenshot(activePage, screenshotsDir, "step0_initial_login_page.png", true));
		} catch (final Exception ex) {
			step.status = Status.FAIL;
			step.details = "Could not navigate to login URL: " + ex.getMessage();
			return false;
		}

		final boolean clickedGoogleDirectly = clickAnyVisibleText(activePage,
				Arrays.asList("Sign\\s*in\\s*with\\s*Google", "Inicia\\s*sesi[oó]n\\s*con\\s*Google", "^GOOGLE$"));

		if (!clickedGoogleDirectly) {
			clickAnyVisibleText(activePage, Arrays.asList("Sign\\s*in", "Inicia\\s*sesi[oó]n", "Login", "Acceder"));
			waitForUiLoad(activePage);
			final boolean clickedGoogleAfterLogin = clickAnyVisibleText(activePage,
					Arrays.asList("Sign\\s*in\\s*with\\s*Google", "Inicia\\s*sesi[oó]n\\s*con\\s*Google", "^GOOGLE$"));
			if (!clickedGoogleAfterLogin) {
				step.status = Status.FAIL;
				step.details = "Could not find or click Google sign-in control.";
				step.evidence
						.add(captureScreenshot(activePage, screenshotsDir, "step1_google_button_not_found.png", true));
				return false;
			}
		}

		waitForUiLoad(activePage);

		final Page googlePage = detectGooglePage(context);
		if (googlePage != null) {
			activePage = googlePage;
			clickAnyVisibleText(activePage, Arrays.asList(Pattern.quote(GOOGLE_ACCOUNT_EMAIL)));
			waitForUiLoad(activePage);
		}

		final boolean sidebarVisible = waitForSidebarVisible(context, activePage, LONG_WAIT_MS);
		final Page appPage = findPageWithAnyVisibleText(context,
				Arrays.asList("Negocio", "Mi\\s+Negocio", "Administrar\\s+Negocios"), activePage);

		if (!sidebarVisible || appPage == null) {
			step.status = Status.FAIL;
			step.details = "Login did not reach the main application sidebar (likely blocked in OAuth).";
			step.evidence.add(captureScreenshot(activePage, screenshotsDir, "step1_after_login_attempt.png", true));
			step.metadata.put("finalUrl", activePage.url());
			return false;
		}

		step.status = Status.PASS;
		step.details = "Main application interface and left sidebar are visible.";
		step.metadata.put("finalUrl", appPage.url());
		step.evidence.add(captureScreenshot(appPage, screenshotsDir, "step1_dashboard_loaded.png", true));
		return true;
	}

	private boolean performMiNegocioMenuStep(final StepResult step, final Page page, final Path screenshotsDir) {
		final boolean sidebarVisible = isAnyVisibleText(page,
				Arrays.asList("Negocio", "Mi\\s+Negocio", "Administrar\\s+Negocios", "Dashboard"));
		if (!sidebarVisible) {
			step.status = Status.FAIL;
			step.details = "Left sidebar is not visible.";
			return false;
		}

		final boolean clickedMiNegocio = clickAnyVisibleText(page, Arrays.asList("Mi\\s+Negocio", "Negocio"));
		waitForUiLoad(page);

		final boolean agregarVisible = isAnyVisibleText(page, Arrays.asList("Agregar\\s+Negocio"));
		final boolean administrarVisible = isAnyVisibleText(page, Arrays.asList("Administrar\\s+Negocios"));

		if (!clickedMiNegocio || !agregarVisible || !administrarVisible) {
			step.status = Status.FAIL;
			step.details = "Mi Negocio submenu did not expand with expected options.";
			step.evidence.add(captureScreenshot(page, screenshotsDir, "step2_menu_validation_failed.png", true));
			return false;
		}

		step.status = Status.PASS;
		step.details = "Mi Negocio submenu expanded with Agregar Negocio and Administrar Negocios.";
		step.evidence.add(captureScreenshot(page, screenshotsDir, "step2_mi_negocio_menu_expanded.png", true));
		return true;
	}

	private boolean performAgregarNegocioModalStep(final StepResult step, final Page page, final Path screenshotsDir) {
		if (!clickAnyVisibleText(page, Arrays.asList("Agregar\\s+Negocio"))) {
			step.status = Status.FAIL;
			step.details = "Could not click Agregar Negocio.";
			return false;
		}
		waitForUiLoad(page);

		final boolean titleVisible = waitForVisibleText(page, "Crear\\s+Nuevo\\s+Negocio", MEDIUM_WAIT_MS);
		final boolean inputVisible = hasBusinessNameInput(page);
		final boolean quotaVisible = isAnyVisibleText(page, Arrays.asList("Tienes\\s+2\\s+de\\s+3\\s+negocios"));
		final boolean cancelVisible = isAnyVisibleText(page, Arrays.asList("Cancelar"));
		final boolean createVisible = isAnyVisibleText(page, Arrays.asList("Crear\\s+Negocio"));

		if (!(titleVisible && inputVisible && quotaVisible && cancelVisible && createVisible)) {
			step.status = Status.FAIL;
			step.details = "Agregar Negocio modal is missing one or more expected elements.";
			step.evidence.add(captureScreenshot(page, screenshotsDir, "step3_modal_validation_failed.png", true));
			return false;
		}

		fillBusinessNameAndCancel(page);
		waitForUiLoad(page);

		step.status = Status.PASS;
		step.details = "Crear Nuevo Negocio modal validated successfully.";
		step.evidence.add(captureScreenshot(page, screenshotsDir, "step3_agregar_negocio_modal.png", true));
		return true;
	}

	private boolean performAdministrarNegociosStep(final StepResult step, final Page page, final Path screenshotsDir) {
		if (!isAnyVisibleText(page, Arrays.asList("Administrar\\s+Negocios"))) {
			clickAnyVisibleText(page, Arrays.asList("Mi\\s+Negocio", "Negocio"));
			waitForUiLoad(page);
		}

		if (!clickAnyVisibleText(page, Arrays.asList("Administrar\\s+Negocios"))) {
			step.status = Status.FAIL;
			step.details = "Could not open Administrar Negocios.";
			return false;
		}
		waitForUiLoad(page);

		final boolean infoGeneral = waitForVisibleText(page, "Informaci[oó]n\\s+General", MEDIUM_WAIT_MS);
		final boolean detallesCuenta = isAnyVisibleText(page, Arrays.asList("Detalles\\s+de\\s+la\\s+Cuenta"));
		final boolean tusNegocios = isAnyVisibleText(page, Arrays.asList("Tus\\s+Negocios"));
		final boolean legalSection = isAnyVisibleText(page, Arrays.asList("Secci[oó]n\\s+Legal"));

		if (!(infoGeneral && detallesCuenta && tusNegocios && legalSection)) {
			step.status = Status.FAIL;
			step.details = "Administrar Negocios page is missing one or more main sections.";
			step.evidence
					.add(captureScreenshot(page, screenshotsDir, "step4_administrar_negocios_validation_failed.png", true));
			return false;
		}

		step.status = Status.PASS;
		step.details = "Administrar Negocios page loaded with all expected sections.";
		step.evidence.add(captureScreenshot(page, screenshotsDir, "step4_administrar_negocios_full_page.png", true));
		return true;
	}

	private void performInformacionGeneralStep(final StepResult step, final Page page) {
		final boolean hasName = hasAnyVisibleLocator(page,
				Arrays.asList("h1", "h2", "h3", "p", "span", "div", "[data-testid*=name]", "[class*=name]"), ".+");
		final boolean hasEmail = isAnyVisibleText(page, Arrays.asList("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));
		final boolean hasPlan = isAnyVisibleText(page, Arrays.asList("BUSINESS\\s+PLAN"));
		final boolean hasCambiarPlan = isAnyVisibleText(page, Arrays.asList("Cambiar\\s+Plan"));

		if (hasName && hasEmail && hasPlan && hasCambiarPlan) {
			step.status = Status.PASS;
			step.details = "Información General section contains name, email, plan, and Cambiar Plan button.";
		} else {
			step.status = Status.FAIL;
			step.details = "Información General section is missing required fields.";
		}
	}

	private void performDetallesCuentaStep(final StepResult step, final Page page) {
		final boolean cuentaCreada = isAnyVisibleText(page, Arrays.asList("Cuenta\\s+creada"));
		final boolean estadoActivo = isAnyVisibleText(page, Arrays.asList("Estado\\s+activo", "Activo"));
		final boolean idiomaSeleccionado = isAnyVisibleText(page, Arrays.asList("Idioma\\s+seleccionado", "Idioma"));

		if (cuentaCreada && estadoActivo && idiomaSeleccionado) {
			step.status = Status.PASS;
			step.details = "Detalles de la Cuenta section contains all expected labels.";
		} else {
			step.status = Status.FAIL;
			step.details = "Detalles de la Cuenta section is missing one or more expected labels.";
		}
	}

	private void performTusNegociosStep(final StepResult step, final Page page) {
		final boolean listVisible = isAnyVisibleText(page,
				Arrays.asList("Tus\\s+Negocios", "Negocio", "Negocios"));
		final boolean addButtonVisible = isAnyVisibleText(page, Arrays.asList("Agregar\\s+Negocio"));
		final boolean quotaVisible = isAnyVisibleText(page, Arrays.asList("Tienes\\s+2\\s+de\\s+3\\s+negocios"));

		if (listVisible && addButtonVisible && quotaVisible) {
			step.status = Status.PASS;
			step.details = "Tus Negocios section and quota are visible.";
		} else {
			step.status = Status.FAIL;
			step.details = "Tus Negocios section is missing expected list/button/quota elements.";
		}
	}

	private void performLegalPageStep(final StepResult step, final BrowserContext context, final Page appPage,
			final Path screenshotsDir, final String legalLinkRegex, final String screenshotBaseName) {
		final Page[] popupHolder = new Page[1];

		try {
			popupHolder[0] = context.waitForPage(() -> clickAnyVisibleText(appPage, Arrays.asList(legalLinkRegex)),
					new BrowserContext.WaitForPageOptions().setTimeout(SHORT_WAIT_MS));
		} catch (final TimeoutError timeout) {
			clickAnyVisibleText(appPage, Arrays.asList(legalLinkRegex));
		}

		waitForUiLoad(appPage);

		final Page legalPage = popupHolder[0] != null ? popupHolder[0] : appPage;
		waitForUiLoad(legalPage);

		final boolean headingVisible = waitForVisibleText(legalPage, legalLinkRegex, MEDIUM_WAIT_MS);
		final boolean hasLegalContent = hasAnyVisibleLocator(legalPage, Arrays.asList("article", "main", "section", "p"),
				".{50,}");

		step.metadata.put("finalUrl", legalPage.url());
		step.evidence.add(captureScreenshot(legalPage, screenshotsDir, screenshotBaseName + ".png", true));

		if (headingVisible && hasLegalContent) {
			step.status = Status.PASS;
			step.details = "Legal page loaded with heading and content.";
		} else {
			step.status = Status.FAIL;
			step.details = "Legal page validation failed (missing heading or visible legal content).";
		}

		if (popupHolder[0] != null) {
			popupHolder[0].close();
		} else if (legalPage == appPage) {
			try {
				appPage.goBack();
				waitForUiLoad(appPage);
			} catch (final Exception ignored) {
				// Ignore cleanup navigation failures.
			}
		}
	}

	private boolean hasBusinessNameInput(final Page page) {
		try {
			if (page.getByLabel(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
					.first().isVisible()) {
				return true;
			}
		} catch (final Exception ignored) {
			// Continue with other selectors.
		}

		try {
			return page
					.getByPlaceholder(
							Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
					.first().isVisible();
		} catch (final Exception ignored) {
			return isAnyVisibleText(page, Arrays.asList("Nombre\\s+del\\s+Negocio"));
		}
	}

	private void fillBusinessNameAndCancel(final Page page) {
		try {
			final Locator byLabel = page
					.getByLabel(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
					.first();
			if (byLabel.isVisible()) {
				byLabel.click();
				byLabel.fill("Negocio Prueba Automatizacion");
			}
		} catch (final Exception ignored) {
			try {
				final Locator byPlaceholder = page.getByPlaceholder(
						Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)).first();
				if (byPlaceholder.isVisible()) {
					byPlaceholder.click();
					byPlaceholder.fill("Negocio Prueba Automatizacion");
				}
			} catch (final Exception ignoredAgain) {
				// Optional action: no-op if field cannot be typed.
			}
		}

		clickAnyVisibleText(page, Arrays.asList("Cancelar"));
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(SHORT_WAIT_MS));
		} catch (final Exception ignored) {
			// UI might not trigger load state transitions on SPA clicks.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_WAIT_MS));
		} catch (final Exception ignored) {
			// Best effort only.
		}

		page.waitForTimeout(800);
	}

	private boolean waitForSidebarVisible(final BrowserContext context, final Page fallbackPage, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final Page page : context.pages()) {
				if (isAnyVisibleText(page,
						Arrays.asList("Negocio", "Mi\\s+Negocio", "Administrar\\s+Negocios", "Dashboard"))) {
					return true;
				}
			}

			if (isAnyVisibleText(fallbackPage,
					Arrays.asList("Negocio", "Mi\\s+Negocio", "Administrar\\s+Negocios", "Dashboard"))) {
				return true;
			}

			fallbackPage.waitForTimeout(1000);
		}
		return false;
	}

	private Page findPageWithAnyVisibleText(final BrowserContext context, final List<String> regexes, final Page fallback) {
		for (final Page page : context.pages()) {
			if (isAnyVisibleText(page, regexes)) {
				return page;
			}
		}
		return isAnyVisibleText(fallback, regexes) ? fallback : null;
	}

	private Page detectGooglePage(final BrowserContext context) {
		for (final Page page : context.pages()) {
			final String url = page.url();
			if (url != null && url.contains("accounts.google.com")) {
				return page;
			}
		}
		return null;
	}

	private boolean clickAnyVisibleText(final Page page, final List<String> regexes) {
		for (final String regex : regexes) {
			if (clickInPageOrFrames(page, regex)) {
				return true;
			}
		}
		return false;
	}

	private boolean clickInPageOrFrames(final Page page, final String regex) {
		final String selector = "text=/" + regex + "/i";
		if (tryClick(page.locator(selector).first(), page)) {
			return true;
		}

		for (final Frame frame : page.frames()) {
			try {
				if (tryClick(frame.locator(selector).first(), page)) {
					return true;
				}
			} catch (final Exception ignored) {
				// Best effort across dynamic iframes.
			}
		}
		return false;
	}

	private boolean tryClick(final Locator locator, final Page waitingPage) {
		try {
			if (locator.count() > 0 && locator.isVisible()) {
				locator.click(new Locator.ClickOptions().setTimeout(SHORT_WAIT_MS));
				waitForUiLoad(waitingPage);
				return true;
			}
		} catch (final Exception ignored) {
			// Click attempt failed; continue trying next candidate.
		}
		return false;
	}

	private boolean waitForVisibleText(final Page page, final String regex, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (isAnyVisibleText(page, Arrays.asList(regex))) {
				return true;
			}
			page.waitForTimeout(500);
		}
		return false;
	}

	private boolean isAnyVisibleText(final Page page, final List<String> regexes) {
		for (final String regex : regexes) {
			final String selector = "text=/" + regex + "/i";
			try {
				if (page.locator(selector).first().isVisible()) {
					return true;
				}
			} catch (final Exception ignored) {
				// Continue checking frames.
			}

			for (final Frame frame : page.frames()) {
				try {
					if (frame.locator(selector).first().isVisible()) {
						return true;
					}
				} catch (final Exception ignored) {
					// Continue scanning.
				}
			}
		}
		return false;
	}

	private boolean hasAnyVisibleLocator(final Page page, final List<String> cssSelectors, final String textRegex) {
		for (final String css : cssSelectors) {
			try {
				final Locator locator = page.locator(css).filter(new Locator.FilterOptions()
						.setHasText(Pattern.compile(textRegex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)));
				if (locator.count() > 0 && locator.first().isVisible()) {
					return true;
				}
			} catch (final Exception ignored) {
				// Continue scanning.
			}
		}
		return false;
	}

	private String captureScreenshot(final Page page, final Path screenshotDir, final String fileName,
			final boolean fullPage) {
		try {
			final Path screenshotPath = screenshotDir.resolve(fileName);
			page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
			return screenshotPath.toString();
		} catch (final Exception ex) {
			return "Screenshot failed: " + fileName + " (" + ex.getMessage() + ")";
		}
	}

	private String getLoginUrl() {
		final String fromProperty = System.getProperty(LOGIN_URL_PROPERTY);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}
		final String fromEnv = System.getenv(LOGIN_URL_ENV);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv.trim();
		}
		return null;
	}

	private boolean resolveHeadless() {
		final String fromProperty = System.getProperty(HEADLESS_PROPERTY);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return Boolean.parseBoolean(fromProperty);
		}
		final String fromEnv = System.getenv(HEADLESS_ENV);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return Boolean.parseBoolean(fromEnv);
		}
		return true;
	}

	private void markAsPrerequisiteFailed(final Map<String, StepResult> report, final List<String> stepLabels,
			final String failedPrerequisite) {
		for (final String label : stepLabels) {
			final StepResult step = report.get(label);
			if (step.status != Status.PASS) {
				step.status = Status.FAIL;
				step.details = "Prerequisite failed: " + failedPrerequisite + ".";
			}
		}
	}

	private void recordUnhandledFailure(final Map<String, StepResult> report, final Exception ex) {
		for (final StepResult step : report.values()) {
			if (step.status != Status.PASS && "Not executed.".equals(step.details)) {
				step.status = Status.FAIL;
				step.details = "Unhandled test failure: " + ex.getMessage();
			}
		}
	}

	private void writeMarkdownReport(final Path reportPath, final String runId, final Map<String, StepResult> report)
			throws IOException {
		final StringBuilder markdown = new StringBuilder();
		markdown.append("# SaleADS Mi Negocio Workflow Report\n\n");
		markdown.append("- Run ID: ").append(runId).append('\n');
		markdown.append("- Generated: ").append(Instant.now()).append('\n');
		markdown.append('\n');
		markdown.append("| Field | Status | Details |\n");
		markdown.append("|---|---|---|\n");
		for (final StepResult step : report.values()) {
			markdown.append("| ").append(step.label).append(" | ").append(step.status).append(" | ")
					.append(step.details.replace('\n', ' ')).append(" |\n");
		}
		markdown.append('\n');

		for (final StepResult step : report.values()) {
			markdown.append("## ").append(step.label).append('\n');
			if (!step.metadata.isEmpty()) {
				markdown.append("- Metadata:\n");
				for (final Map.Entry<String, String> entry : step.metadata.entrySet()) {
					markdown.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
				}
			}
			if (!step.evidence.isEmpty()) {
				markdown.append("- Evidence:\n");
				for (final String evidence : step.evidence) {
					markdown.append("  - ").append(evidence).append('\n');
				}
			}
			markdown.append('\n');
		}

		Files.writeString(reportPath, markdown.toString(), StandardCharsets.UTF_8);
	}

	private void writeJsonReport(final Path reportPath, final String runId, final Map<String, StepResult> report)
			throws IOException {
		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"runId\": \"").append(escapeJson(runId)).append("\",\n");
		json.append("  \"generatedAt\": \"").append(escapeJson(Instant.now().toString())).append("\",\n");
		json.append("  \"steps\": [\n");
		int index = 0;
		for (final StepResult step : report.values()) {
			if (index++ > 0) {
				json.append(",\n");
			}
			json.append("    {\n");
			json.append("      \"label\": \"").append(escapeJson(step.label)).append("\",\n");
			json.append("      \"status\": \"").append(step.status).append("\",\n");
			json.append("      \"details\": \"").append(escapeJson(step.details)).append("\",\n");

			json.append("      \"metadata\": {\n");
			int metadataIndex = 0;
			for (final Map.Entry<String, String> metadataEntry : step.metadata.entrySet()) {
				if (metadataIndex++ > 0) {
					json.append(",\n");
				}
				json.append("        \"").append(escapeJson(metadataEntry.getKey())).append("\": \"")
						.append(escapeJson(metadataEntry.getValue())).append("\"");
			}
			json.append("\n      },\n");

			json.append("      \"evidence\": [");
			for (int i = 0; i < step.evidence.size(); i++) {
				if (i > 0) {
					json.append(", ");
				}
				json.append("\"").append(escapeJson(step.evidence.get(i))).append("\"");
			}
			json.append("]\n");
			json.append("    }");
		}
		json.append("\n  ]\n");
		json.append("}\n");

		Files.writeString(reportPath, json.toString(), StandardCharsets.UTF_8);
	}

	private String buildFailureSummary(final Map<String, StepResult> report) {
		final StringBuilder failureSummary = new StringBuilder("One or more SaleADS workflow validations failed:\n");
		for (final StepResult step : report.values()) {
			if (step.status == Status.FAIL) {
				failureSummary.append("- ").append(step.label).append(": ").append(step.details).append('\n');
			}
		}
		return failureSummary.toString();
	}

	private boolean allPass(final Map<String, StepResult> report) {
		for (final StepResult step : report.values()) {
			if (step.status != Status.PASS) {
				return false;
			}
		}
		return true;
	}

	private String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}
}
