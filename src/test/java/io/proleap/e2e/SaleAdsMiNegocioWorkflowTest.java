package io.proleap.e2e;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.junit.Assert;
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

/**
 * End-to-end coverage for the SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * This test intentionally avoids hard-coding any environment-specific domain.
 * Provide the login page at runtime via SALEADS_LOGIN_URL.
 * </p>
 */
public class SaleAdsMiNegocioWorkflowTest {

	private static final String KEY_LOGIN = "Login";
	private static final String KEY_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String KEY_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String KEY_ADMIN_VIEW = "Administrar Negocios view";
	private static final String KEY_INFO_GENERAL = "Informacion General";
	private static final String KEY_DETALLES = "Detalles de la Cuenta";
	private static final String KEY_TUS_NEGOCIOS = "Tus Negocios";
	private static final String KEY_TERMINOS = "Terminos y Condiciones";
	private static final String KEY_POLITICA = "Politica de Privacidad";

	private final long timeoutMs = Long.parseLong(env("SALEADS_TIMEOUT_MS", "45000"));
	private final Path artifactsDir = Paths.get(env("SALEADS_E2E_ARTIFACTS_DIR", "target/saleads-e2e"));
	private final Path screenshotsDir = artifactsDir.resolve("screenshots");
	private final Path reportFile = artifactsDir.resolve("report.txt");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		Assume.assumeTrue(
				"Set SALEADS_E2E_ENABLED=true to run this test.",
				Boolean.parseBoolean(env("SALEADS_E2E_ENABLED", "false")));

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL to the login page for the environment under test.",
				loginUrl != null && !loginUrl.isBlank());

		Files.createDirectories(screenshotsDir);

		final Map<String, StepResult> results = initResults();
		final List<String> failures = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
					.setHeadless(Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"))));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page appPage = context.newPage();

			appPage.navigate(loginUrl);
			waitForUi(appPage);

			runStep(KEY_LOGIN, results, failures, () -> {
				clickFirstVisible(
						appPage,
						"Login / Sign in with Google button",
						Pattern.compile("(?i)sign\\s*in\\s*with\\s*google"),
						Pattern.compile("(?i)iniciar\\s+sesion\\s+con\\s+google"),
						Pattern.compile("(?i)continuar\\s+con\\s+google"),
						Pattern.compile("(?i)google"));

				chooseGoogleAccountIfShown(appPage);

				waitForVisibleText(
						appPage,
						"Sidebar anchor text",
						Pattern.compile("(?i)negocio|dashboard|inicio"));
				waitForVisible(appPage.locator("aside, nav").first(), "left sidebar");
				takeScreenshot(appPage, "01-dashboard-loaded.png", true);
			});

			runStep(KEY_MI_NEGOCIO_MENU, results, failures, () -> {
				clickIfVisible(
						appPage,
						"Negocio section",
						Pattern.compile("(?i)^negocio$"));
				clickFirstVisible(
						appPage,
						"Mi Negocio option",
						Pattern.compile("(?i)^mi\\s+negocio$"));

				waitForVisibleText(appPage, "Agregar Negocio option", Pattern.compile("(?i)^agregar\\s+negocio$"));
				waitForVisibleText(appPage, "Administrar Negocios option", Pattern.compile("(?i)^administrar\\s+negocios$"));
				takeScreenshot(appPage, "02-mi-negocio-menu-expanded.png", true);
			});

			runStep(KEY_AGREGAR_MODAL, results, failures, () -> {
				clickFirstVisible(
						appPage,
						"Agregar Negocio action",
						Pattern.compile("(?i)^agregar\\s+negocio$"));
				waitForVisibleText(appPage, "Crear Nuevo Negocio title", Pattern.compile("(?i)^crear\\s+nuevo\\s+negocio$"));
				waitForVisibleText(appPage, "Nombre del Negocio input label", Pattern.compile("(?i)^nombre\\s+del\\s+negocio$"));
				waitForVisibleText(appPage, "2 de 3 negocios text", Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"));
				waitForVisibleText(appPage, "Cancelar button", Pattern.compile("(?i)^cancelar$"));
				waitForVisibleText(appPage, "Crear Negocio button", Pattern.compile("(?i)^crear\\s+negocio$"));

				takeScreenshot(appPage, "03-agregar-negocio-modal.png", true);

				typeIfVisible(
						appPage,
						Pattern.compile("(?i)nombre\\s+del\\s+negocio"),
						"Negocio Prueba Automatizacion");
				clickFirstVisible(appPage, "Cancelar modal", Pattern.compile("(?i)^cancelar$"));
				waitForUi(appPage);
			});

			runStep(KEY_ADMIN_VIEW, results, failures, () -> {
				expandMiNegocioIfNeeded(appPage);
				clickFirstVisible(
						appPage,
						"Administrar Negocios option",
						Pattern.compile("(?i)^administrar\\s+negocios$"));
				waitForVisibleText(appPage, "Informacion General section", Pattern.compile("(?i)^informacion\\s+general$"));
				waitForVisibleText(appPage, "Detalles de la Cuenta section", Pattern.compile("(?i)^detalles\\s+de\\s+la\\s+cuenta$"));
				waitForVisibleText(appPage, "Tus Negocios section", Pattern.compile("(?i)^tus\\s+negocios$"));
				waitForVisibleText(appPage, "Seccion Legal section", Pattern.compile("(?i)^seccion\\s+legal$"));
				takeScreenshot(appPage, "04-administrar-negocios.png", true);
			});

			runStep(KEY_INFO_GENERAL, results, failures, () -> {
				waitForVisibleText(appPage, "BUSINESS PLAN label", Pattern.compile("(?i)business\\s+plan"));
				waitForVisibleText(appPage, "Cambiar Plan button", Pattern.compile("(?i)^cambiar\\s+plan$"));
				waitForVisibleText(
						appPage,
						"User email",
						Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"));
				waitForVisible(
						appPage.locator("h1, h2, h3, p, span, div").filter(new Locator.FilterOptions()
								.setHasText(Pattern.compile("(?i)usuario|nombre|perfil|cuenta"))).first(),
						"user name block");
			});

			runStep(KEY_DETALLES, results, failures, () -> {
				waitForVisibleText(appPage, "Cuenta creada", Pattern.compile("(?i)cuenta\\s+creada"));
				waitForVisibleText(appPage, "Estado activo", Pattern.compile("(?i)estado\\s+activo"));
				waitForVisibleText(appPage, "Idioma seleccionado", Pattern.compile("(?i)idioma\\s+seleccionado"));
			});

			runStep(KEY_TUS_NEGOCIOS, results, failures, () -> {
				waitForVisibleText(appPage, "Tus Negocios section", Pattern.compile("(?i)^tus\\s+negocios$"));
				waitForVisibleText(appPage, "Agregar Negocio button", Pattern.compile("(?i)^agregar\\s+negocio$"));
				waitForVisibleText(appPage, "2 de 3 negocios text", Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"));
				waitForVisible(appPage.locator("ul, table, [role='list'], [role='table']").first(), "business list/table");
			});

			runStep(KEY_TERMINOS, results, failures, () -> {
				final LegalNavigation legal = openLegalPage(context, appPage, Pattern.compile("(?i)terminos\\s+y\\s+condiciones"));
				waitForVisibleText(legal.page, "Terminos y Condiciones heading", Pattern.compile("(?i)terminos\\s+y\\s+condiciones"));
				waitForLegalBody(legal.page);
				takeScreenshot(legal.page, "05-terminos-y-condiciones.png", true);
				results.get(KEY_TERMINOS).details = "URL: " + legal.page.url();
				returnFromLegalPage(legal, appPage);
			});

			runStep(KEY_POLITICA, results, failures, () -> {
				final LegalNavigation legal = openLegalPage(context, appPage, Pattern.compile("(?i)politica\\s+de\\s+privacidad"));
				waitForVisibleText(legal.page, "Politica de Privacidad heading", Pattern.compile("(?i)politica\\s+de\\s+privacidad"));
				waitForLegalBody(legal.page);
				takeScreenshot(legal.page, "06-politica-de-privacidad.png", true);
				results.get(KEY_POLITICA).details = "URL: " + legal.page.url();
				returnFromLegalPage(legal, appPage);
			});
		} finally {
			writeReport(results);
		}

		Assert.assertTrue("Workflow failed. See " + reportFile.toAbsolutePath(), failures.isEmpty());
	}

	private void runStep(final String key, final Map<String, StepResult> results, final List<String> failures, final ThrowingRunnable action) {
		try {
			action.run();
			results.get(key).status = "PASS";
			if (results.get(key).details == null) {
				results.get(key).details = "Validation completed";
			}
		} catch (final Throwable t) {
			results.get(key).status = "FAIL";
			results.get(key).details = t.getMessage();
			failures.add(key + ": " + t.getMessage());
		}
	}

	private void chooseGoogleAccountIfShown(final Page page) {
		final Locator account = page.getByText(Pattern.compile("(?i)^juanlucasbarbiergarzon@gmail\\.com$"));
		if (account.count() > 0) {
			try {
				if (account.first().isVisible()) {
					account.first().click();
					waitForUi(page);
				}
			} catch (final TimeoutError ignored) {
				// If Google account chooser is not shown in this environment, continue.
			}
		}
	}

	private void expandMiNegocioIfNeeded(final Page page) {
		final Locator administrar = page.getByText(Pattern.compile("(?i)^administrar\\s+negocios$"));
		if (administrar.count() == 0 || !administrar.first().isVisible()) {
			clickIfVisible(page, "Mi Negocio option", Pattern.compile("(?i)^mi\\s+negocio$"));
			waitForUi(page);
		}
	}

	private LegalNavigation openLegalPage(final BrowserContext context, final Page appPage, final Pattern linkPattern) {
		final String previousUrl = appPage.url();
		Page legalPage = null;
		boolean newTab = false;
		try {
			legalPage = context.waitForPage(() -> clickFirstVisible(appPage, "Legal link", linkPattern),
					new BrowserContext.WaitForPageOptions().setTimeout(timeoutMs));
			newTab = true;
		} catch (final TimeoutError timeoutError) {
			clickFirstVisible(appPage, "Legal link", linkPattern);
			waitForUi(appPage);
			legalPage = appPage;
		}

		if (Objects.equals(previousUrl, legalPage.url())) {
			waitForUi(legalPage);
		}
		return new LegalNavigation(legalPage, newTab);
	}

	private void returnFromLegalPage(final LegalNavigation legal, final Page appPage) {
		if (legal.openedInNewTab) {
			legal.page.close();
			appPage.bringToFront();
			waitForUi(appPage);
			return;
		}

		appPage.goBack();
		waitForUi(appPage);
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(screenshotsDir.resolve(fileName))
				.setFullPage(fullPage));
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
		page.waitForTimeout(600);
	}

	private void clickFirstVisible(final Page page, final String description, final Pattern... textPatterns) {
		for (final Pattern textPattern : textPatterns) {
			final Locator locator = page.getByText(textPattern);
			if (locator.count() > 0) {
				try {
					waitForVisible(locator.first(), description + " [" + textPattern.pattern() + "]");
					locator.first().click();
					waitForUi(page);
					return;
				} catch (final RuntimeException ignored) {
					// Try next candidate pattern.
				}
			}
		}

		throw new IllegalStateException("Unable to find/click element: " + description);
	}

	private void clickIfVisible(final Page page, final String description, final Pattern textPattern) {
		final Locator locator = page.getByText(textPattern);
		if (locator.count() > 0 && locator.first().isVisible()) {
			locator.first().click();
			waitForUi(page);
		}
	}

	private void waitForVisibleText(final Page page, final String description, final Pattern textPattern) {
		final Locator locator = page.getByText(textPattern);
		waitForVisible(locator.first(), description + " [" + textPattern.pattern() + "]");
	}

	private void waitForLegalBody(final Page page) {
		waitForVisible(page.locator("main, article, section, body").first(), "legal content container");
		final String bodyText = page.locator("body").innerText();
		if (bodyText == null || bodyText.trim().length() < 80) {
			throw new IllegalStateException("Legal content text is too short to be considered valid.");
		}
	}

	private void typeIfVisible(final Page page, final Pattern labelPattern, final String text) {
		final Locator input = page.locator("input, textarea")
				.filter(new Locator.FilterOptions().setHas(
						page.locator("xpath=ancestor-or-self::*[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '" + labelPattern.pattern().toLowerCase() + "')]")));

		if (input.count() > 0 && input.first().isVisible()) {
			input.first().click();
			input.first().fill(text);
			waitForUi(page);
			return;
		}

		final Locator fallback = page.getByLabel(labelPattern);
		if (fallback.count() > 0 && fallback.first().isVisible()) {
			fallback.first().fill(text);
			waitForUi(page);
		}
	}

	private void waitForVisible(final Locator locator, final String description) {
		locator.waitFor(new Locator.WaitForOptions()
				.setState(Locator.WaitForOptions.State.VISIBLE)
				.setTimeout(timeoutMs));
	}

	private Map<String, StepResult> initResults() {
		final Map<String, StepResult> results = new LinkedHashMap<>();
		results.put(KEY_LOGIN, StepResult.pending());
		results.put(KEY_MI_NEGOCIO_MENU, StepResult.pending());
		results.put(KEY_AGREGAR_MODAL, StepResult.pending());
		results.put(KEY_ADMIN_VIEW, StepResult.pending());
		results.put(KEY_INFO_GENERAL, StepResult.pending());
		results.put(KEY_DETALLES, StepResult.pending());
		results.put(KEY_TUS_NEGOCIOS, StepResult.pending());
		results.put(KEY_TERMINOS, StepResult.pending());
		results.put(KEY_POLITICA, StepResult.pending());
		return results;
	}

	private void writeReport(final Map<String, StepResult> results) throws IOException {
		Files.createDirectories(artifactsDir);

		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Workflow Report");
		lines.add("----------------------------------");
		lines.add("Artifacts directory: " + artifactsDir.toAbsolutePath());
		lines.add("");

		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			final StepResult value = entry.getValue();
			lines.add(entry.getKey() + ": " + value.status);
			if (value.details != null && !value.details.isBlank()) {
				lines.add("  - " + value.details);
			}
		}

		Files.write(reportFile, lines, UTF_8);
	}

	private String env(final String key, final String fallback) {
		return Supplier.<String>of(() -> System.getenv(key)).get() == null ? fallback : System.getenv(key);
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static final class LegalNavigation {
		private final Page page;
		private final boolean openedInNewTab;

		private LegalNavigation(final Page page, final boolean openedInNewTab) {
			this.page = page;
			this.openedInNewTab = openedInNewTab;
		}
	}

	private static final class StepResult {
		private String status;
		private String details;

		private static StepResult pending() {
			final StepResult value = new StepResult();
			value.status = "PENDING";
			value.details = "Not executed";
			return value;
		}
	}
}
