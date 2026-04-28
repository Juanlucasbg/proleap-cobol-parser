package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

import org.junit.Assume;
import org.junit.Test;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio".
 *
 * <p>
 * Required environment variable:
 * </p>
 * <ul>
 * <li>SALEADS_LOGIN_URL: login URL for the current environment.</li>
 * </ul>
 *
 * <p>
 * Optional environment variable:
 * </p>
 * <ul>
 * <li>SALEADS_HEADLESS: true/false (defaults to true).</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Política de Privacidad";

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"SALEADS_LOGIN_URL is required to run this environment-agnostic E2E test.",
				loginUrl != null && !loginUrl.trim().isEmpty());

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDir = Path.of("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);

		final Map<String, Boolean> report = new LinkedHashMap<>();
		final Map<String, String> errors = new LinkedHashMap<>();
		final List<String> legalUrls = new ArrayList<>();

		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(
					new Browser.NewContextOptions().setViewportSize(1920, 1080));
			final Page appPage = context.newPage();

			appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(LoadState.DOMCONTENTLOADED));
			waitForUi(appPage);

			recordStep(report, errors, STEP_LOGIN, () -> {
				stepLoginWithGoogle(appPage, context);
				takeScreenshot(appPage, evidenceDir.resolve("01-dashboard-loaded.png"), true);
			});

			recordStep(report, errors, STEP_MI_NEGOCIO_MENU, () -> {
				stepOpenMiNegocioMenu(appPage);
				takeScreenshot(appPage, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
			});

			recordStep(report, errors, STEP_AGREGAR_MODAL, () -> {
				stepValidateAgregarNegocioModal(appPage);
				takeScreenshot(appPage, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);
			});

			recordStep(report, errors, STEP_ADMIN_VIEW, () -> {
				stepOpenAdministrarNegocios(appPage);
				takeScreenshot(appPage, evidenceDir.resolve("04-administrar-negocios-view.png"), true);
			});

			recordStep(report, errors, STEP_INFO_GENERAL, () -> stepValidateInformacionGeneral(appPage));
			recordStep(report, errors, STEP_DETALLES_CUENTA, () -> stepValidateDetallesCuenta(appPage));
			recordStep(report, errors, STEP_TUS_NEGOCIOS, () -> stepValidateTusNegocios(appPage));

			recordStep(report, errors, STEP_TERMINOS, () -> {
				final String url = stepValidateLegalDocument(appPage, context, "Términos y Condiciones",
						Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"),
						evidenceDir.resolve("05-terminos-y-condiciones.png"));
				legalUrls.add("Términos y Condiciones: " + url);
			});

			recordStep(report, errors, STEP_PRIVACIDAD, () -> {
				final String url = stepValidateLegalDocument(appPage, context, "Política de Privacidad",
						Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"),
						evidenceDir.resolve("06-politica-de-privacidad.png"));
				legalUrls.add("Política de Privacidad: " + url);
			});
		}

		final String finalReport = buildFinalReport(report, errors, legalUrls, evidenceDir);
		System.out.println(finalReport);

		final List<String> failed = new ArrayList<>();
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue()) {
				failed.add(entry.getKey() + " => " + errors.getOrDefault(entry.getKey(), "Failed"));
			}
		}
		if (!failed.isEmpty()) {
			fail("SaleADS Mi Negocio workflow validation failed:\n" + String.join("\n", failed)
					+ "\nEvidence directory: " + evidenceDir);
		}
	}

	private void stepLoginWithGoogle(final Page appPage, final BrowserContext context) {
		final Locator loginButton = findFirstVisible(appPage,
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern
								.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s+sesi[oó]n\\s+con\\s+google|continuar\\s+con\\s+google|google)"))),
				appPage.getByText(Pattern
						.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s+sesi[oó]n\\s+con\\s+google|continuar\\s+con\\s+google)")),
				appPage.locator("button:has-text('Google')"));

		Page popupPage = null;
		try {
			popupPage = context.waitForPage(() -> clickAndWait(appPage, loginButton),
					new BrowserContext.WaitForPageOptions().setTimeout(8000));
		} catch (PlaywrightException ex) {
			clickAndWait(appPage, loginButton);
		}

		if (popupPage != null) {
			chooseGoogleAccountIfShown(popupPage);
		}
		chooseGoogleAccountIfShown(appPage);

		waitForTextVisible(appPage,
				Pattern.compile("(?i)(mi\\s+negocio|negocio|administrar\\s+negocios|informaci[oó]n\\s+general)"),
				60000);
		waitForSidebarVisible(appPage);
	}

	private void stepOpenMiNegocioMenu(final Page page) {
		expandMiNegocioIfNeeded(page);
		waitForTextVisible(page, Pattern.compile("(?i)agregar\\s+negocio"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)administrar\\s+negocios"), 15000);
	}

	private void stepValidateAgregarNegocioModal(final Page page) {
		clickText(page, Pattern.compile("(?i)^agregar\\s+negocio$"));
		waitForTextVisible(page, Pattern.compile("(?i)crear\\s+nuevo\\s+negocio"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)nombre\\s+del\\s+negocio"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)^cancelar$"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)^crear\\s+negocio$"), 15000);

		final Locator nombreNegocioInput = findFirstVisible(page,
				page.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
				page.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
				page.locator("input[name*='nombre' i], input[id*='nombre' i]"));
		nombreNegocioInput.fill("Negocio Prueba Automatización");

		clickText(page, Pattern.compile("(?i)^cancelar$"));
	}

	private void stepOpenAdministrarNegocios(final Page page) {
		expandMiNegocioIfNeeded(page);
		clickText(page, Pattern.compile("(?i)^administrar\\s+negocios$"));
		waitForTextVisible(page, Pattern.compile("(?i)informaci[oó]n\\s+general"), 20000);
		waitForTextVisible(page, Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta"), 20000);
		waitForTextVisible(page, Pattern.compile("(?i)tus\\s+negocios"), 20000);
		waitForTextVisible(page, Pattern.compile("(?i)secci[oó]n\\s+legal"), 20000);
	}

	private void stepValidateInformacionGeneral(final Page page) {
		waitForTextVisible(page, Pattern.compile("(?i)informaci[oó]n\\s+general"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)(nombre|usuario|name)"), 15000);
		waitForTextVisible(page, Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)business\\s+plan"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)cambiar\\s+plan"), 15000);
	}

	private void stepValidateDetallesCuenta(final Page page) {
		waitForTextVisible(page, Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)cuenta\\s+creada"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)estado\\s+activo"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)idioma\\s+seleccionado"), 15000);
	}

	private void stepValidateTusNegocios(final Page page) {
		waitForTextVisible(page, Pattern.compile("(?i)tus\\s+negocios"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)^agregar\\s+negocio$"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"), 15000);
	}

	private String stepValidateLegalDocument(final Page appPage, final BrowserContext context, final String linkText,
			final Pattern headingPattern, final Path screenshotPath) {
		waitForTextVisible(appPage, Pattern.compile("(?i)secci[oó]n\\s+legal"), 15000);
		final Locator link = findFirstVisible(appPage,
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))),
				appPage.getByText(Pattern.compile("(?i)" + Pattern.quote(linkText))));
		link.scrollIntoViewIfNeeded();
		waitForUi(appPage);

		Page legalPage = null;
		final String currentUrl = appPage.url();

		try {
			legalPage = context.waitForPage(() -> clickAndWait(appPage, link),
					new BrowserContext.WaitForPageOptions().setTimeout(8000));
		} catch (PlaywrightException ex) {
			clickAndWait(appPage, link);
		}

		final Page targetPage = legalPage != null ? legalPage : appPage;
		targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		waitForUi(targetPage);

		waitForTextVisible(targetPage, headingPattern, 20000);
		waitForTextVisible(targetPage,
				Pattern.compile("(?i)(t[eé]rminos|condiciones|privacidad|datos\\s+personales|uso|legal)"),
				20000);

		takeScreenshot(targetPage, screenshotPath, true);
		final String finalUrl = targetPage.url();

		if (legalPage != null) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!currentUrl.equals(appPage.url())) {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void expandMiNegocioIfNeeded(final Page page) {
		final Locator miNegocio = findFirstVisible(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^mi\\s+negocio$"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^mi\\s+negocio$"))),
				page.getByText(Pattern.compile("(?i)^mi\\s+negocio$")));

		clickAndWait(page, miNegocio);
		waitForTextVisible(page, Pattern.compile("(?i)agregar\\s+negocio"), 15000);
		waitForTextVisible(page, Pattern.compile("(?i)administrar\\s+negocios"), 15000);
	}

	private void chooseGoogleAccountIfShown(final Page page) {
		try {
			final Locator account = page.getByText(GOOGLE_ACCOUNT_EMAIL, new Page.GetByTextOptions().setExact(true));
			if (hasVisible(account, 5000)) {
				clickAndWait(page, account.first());
			}
		} catch (PlaywrightException ex) {
			// If Google account chooser is not shown, continue.
		}
	}

	private void clickText(final Page page, final Pattern pattern) {
		final Locator target = findFirstVisible(page, page.getByText(pattern),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)));
		clickAndWait(page, target);
	}

	private Locator findFirstVisible(final Page page, final Locator... candidates) {
		final long end = System.currentTimeMillis() + 15000;
		while (System.currentTimeMillis() < end) {
			for (Locator candidate : candidates) {
				final int count = (int) candidate.count();
				for (int i = 0; i < count; i++) {
					final Locator nth = candidate.nth(i);
					if (nth.isVisible()) {
						return nth;
					}
				}
			}
			page.waitForTimeout(250);
		}
		throw new AssertionError("Could not find a visible element for the requested selector set.");
	}

	private boolean hasVisible(final Locator locator, final long timeoutMs) {
		final long end = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < end) {
			final int count = (int) locator.count();
			for (int i = 0; i < count; i++) {
				if (locator.nth(i).isVisible()) {
					return true;
				}
			}
			locator.page().waitForTimeout(200);
		}
		return false;
	}

	private void waitForTextVisible(final Page page, final Pattern pattern, final double timeoutMs) {
		final Locator text = page.getByText(pattern);
		assertTrue("Expected visible text pattern: " + pattern.pattern(),
				text.first().isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs)));
	}

	private void waitForSidebarVisible(final Page page) {
		assertTrue("Left sidebar/navigation is expected to be visible after login.",
				findFirstVisible(page, page.locator("aside"), page.locator("nav"),
						page.getByText(Pattern.compile("(?i)negocio")))
						.isVisible());
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click();
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (PlaywrightException ex) {
			// Some apps maintain long-polling requests; DOM ready is enough.
		}
		page.waitForTimeout(600);
	}

	private void takeScreenshot(final Page page, final Path destination, final boolean fullPage) {
		page.screenshot(
				new Page.ScreenshotOptions().setPath(destination).setFullPage(fullPage));
	}

	private void recordStep(final Map<String, Boolean> report, final Map<String, String> errors, final String stepName,
			final CheckedRunnable runnable) {
		try {
			runnable.run();
			report.put(stepName, true);
		} catch (Throwable t) {
			report.put(stepName, false);
			errors.put(stepName, t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
		}
	}

	private String buildFinalReport(final Map<String, Boolean> report, final Map<String, String> errors,
			final List<String> legalUrls, final Path evidenceDir) {
		final StringBuilder sb = new StringBuilder();
		sb.append("\n=== SaleADS Mi Negocio Final Report ===\n");
		appendStepLine(sb, report, errors, STEP_LOGIN);
		appendStepLine(sb, report, errors, STEP_MI_NEGOCIO_MENU);
		appendStepLine(sb, report, errors, STEP_AGREGAR_MODAL);
		appendStepLine(sb, report, errors, STEP_ADMIN_VIEW);
		appendStepLine(sb, report, errors, STEP_INFO_GENERAL);
		appendStepLine(sb, report, errors, STEP_DETALLES_CUENTA);
		appendStepLine(sb, report, errors, STEP_TUS_NEGOCIOS);
		appendStepLine(sb, report, errors, STEP_TERMINOS);
		appendStepLine(sb, report, errors, STEP_PRIVACIDAD);
		sb.append("Evidence directory: ").append(evidenceDir).append('\n');
		if (!legalUrls.isEmpty()) {
			sb.append("Legal URLs:\n");
			for (String url : legalUrls) {
				sb.append("- ").append(url).append('\n');
			}
		}
		return sb.toString();
	}

	private void appendStepLine(final StringBuilder sb, final Map<String, Boolean> report, final Map<String, String> errors,
			final String stepName) {
		final boolean pass = report.getOrDefault(stepName, false);
		sb.append("- ").append(stepName).append(": ").append(pass ? "PASS" : "FAIL");
		if (!pass) {
			sb.append(" (").append(errors.getOrDefault(stepName, "No error details")).append(")");
		}
		sb.append('\n');
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws IOException;
	}
}
