package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * End-to-end test for SaleADS.ai Mi Negocio workflow.
 *
 * <p>This test is environment-agnostic: pass the login page URL through
 * SALEADS_START_URL and it will run against dev, staging, or production.</p>
 */
public class SaleAdsMiNegocioWorkflowE2ETest {

	private static final int DEFAULT_TIMEOUT_MS = 30_000;
	private static final int SHORT_TIMEOUT_MS = 5_000;
	private static final DateTimeFormatter RUN_FOLDER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String startUrl = System.getenv("SALEADS_START_URL");
		Assume.assumeTrue("Set SALEADS_START_URL to the current environment login URL.",
				startUrl != null && !startUrl.isBlank());

		final String googleEmail = System.getenv().getOrDefault("SALEADS_GOOGLE_EMAIL",
				"juanlucasbarbiergarzon@gmail.com");
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		final Path artifactsDir = createArtifactsDir();

		final Map<String, StepResult> report = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new Browser.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext();
			final Page appPage = context.newPage();

			appPage.navigate(startUrl);
			waitForUiToSettle(appPage);

			runStep(report, "Login", () -> {
				loginWithGoogle(appPage, googleEmail);
				assertAnyVisible(appPage, Arrays.asList("aside", "nav", "text=Negocio", "text=Mi Negocio"),
						"Expected main interface/sidebar after login.");
				takeScreenshot(appPage, artifactsDir.resolve("01-dashboard-loaded.png"), false);
			});

			runStep(report, "Mi Negocio menu", () -> {
				openMiNegocioMenu(appPage);
				assertAnyVisible(appPage, Arrays.asList("text=Agregar Negocio"), "'Agregar Negocio' is not visible.");
				assertAnyVisible(appPage, Arrays.asList("text=Administrar Negocios"),
						"'Administrar Negocios' is not visible.");
				takeScreenshot(appPage, artifactsDir.resolve("02-mi-negocio-expanded.png"), false);
			});

			runStep(report, "Agregar Negocio modal", () -> {
				clickByVisibleText(appPage, Arrays.asList("Agregar Negocio"));
				waitForUiToSettle(appPage);

				assertAnyVisible(appPage, Arrays.asList("text=Crear Nuevo Negocio"),
						"Modal title 'Crear Nuevo Negocio' is not visible.");
				assertAnyVisible(appPage,
						Arrays.asList("input[placeholder*='Nombre del Negocio']", "label:has-text('Nombre del Negocio')"),
						"Input 'Nombre del Negocio' is not visible.");
				assertAnyVisible(appPage, Arrays.asList("text=Tienes 2 de 3 negocios"),
						"'Tienes 2 de 3 negocios' is not visible.");
				assertAnyVisible(appPage, Arrays.asList("button:has-text('Cancelar')"),
						"Button 'Cancelar' is not visible.");
				assertAnyVisible(appPage, Arrays.asList("button:has-text('Crear Negocio')"),
						"Button 'Crear Negocio' is not visible.");

				takeScreenshot(appPage, artifactsDir.resolve("03-agregar-negocio-modal.png"), false);

				fillInputIfVisible(appPage, Arrays.asList("input[placeholder*='Nombre del Negocio']", "input[type='text']"),
						"Negocio Prueba Automatizacion");
				clickByVisibleText(appPage, Arrays.asList("Cancelar"));
				waitForUiToSettle(appPage);
			});

			runStep(report, "Administrar Negocios view", () -> {
				openMiNegocioMenu(appPage);
				clickByVisibleText(appPage, Arrays.asList("Administrar Negocios"));
				waitForUiToSettle(appPage);

				assertAnyVisible(appPage, Arrays.asList("text=Informacion General", "text=Información General"),
						"'Información General' section is not visible.");
				assertAnyVisible(appPage, Arrays.asList("text=Detalles de la Cuenta"), "'Detalles de la Cuenta' is not visible.");
				assertAnyVisible(appPage, Arrays.asList("text=Tus Negocios"), "'Tus Negocios' section is not visible.");
				assertAnyVisible(appPage, Arrays.asList("text=Seccion Legal", "text=Sección Legal"),
						"'Sección Legal' section is not visible.");

				takeScreenshot(appPage, artifactsDir.resolve("04-administrar-negocios-full.png"), true);
			});

			runStep(report, "Información General", () -> {
				assertAnyVisible(appPage, Arrays.asList("text=@", "text=juan"), "User name/email not visible.");
				assertAnyVisible(appPage, Arrays.asList("text=BUSINESS PLAN"), "'BUSINESS PLAN' is not visible.");
				assertAnyVisible(appPage, Arrays.asList("button:has-text('Cambiar Plan')"),
						"Button 'Cambiar Plan' is not visible.");
			});

			runStep(report, "Detalles de la Cuenta", () -> {
				assertAnyVisible(appPage, Arrays.asList("text=Cuenta creada"), "'Cuenta creada' is not visible.");
				assertAnyVisible(appPage, Arrays.asList("text=Estado activo"), "'Estado activo' is not visible.");
				assertAnyVisible(appPage, Arrays.asList("text=Idioma seleccionado"),
						"'Idioma seleccionado' is not visible.");
			});

			runStep(report, "Tus Negocios", () -> {
				assertAnyVisible(appPage, Arrays.asList("text=Tus Negocios"), "'Tus Negocios' title is not visible.");
				assertAnyVisible(appPage, Arrays.asList("button:has-text('Agregar Negocio')"),
						"'Agregar Negocio' button is not visible in businesses section.");
				assertAnyVisible(appPage, Arrays.asList("text=Tienes 2 de 3 negocios"),
						"'Tienes 2 de 3 negocios' is not visible.");
			});

			runStep(report, "Términos y Condiciones", () -> {
				final LegalPageEvidence termsEvidence = validateLegalLink(context, appPage, "Términos y Condiciones",
						"Terminos y Condiciones|Términos y Condiciones",
						artifactsDir.resolve("05-terminos-y-condiciones.png"));
				report.put("Términos y Condiciones URL", StepResult.pass(termsEvidence.url));
			});

			runStep(report, "Política de Privacidad", () -> {
				final LegalPageEvidence privacyEvidence = validateLegalLink(context, appPage, "Política de Privacidad",
						"Politica de Privacidad|Política de Privacidad",
						artifactsDir.resolve("06-politica-de-privacidad.png"));
				report.put("Política de Privacidad URL", StepResult.pass(privacyEvidence.url));
			});
		}

		final String finalReport = buildReport(report, artifactsDir);
		System.out.println(finalReport);
		assertTrue(finalReport, allMandatoryStepsPass(report));
	}

	private void loginWithGoogle(final Page page, final String googleEmail) {
		final Locator loginButton = firstVisible(page, Arrays.asList(
				"button:has-text('Sign in with Google')",
				"button:has-text('Iniciar sesión con Google')",
				"button:has-text('Continuar con Google')",
				"text=Sign in with Google",
				"text=Iniciar sesión con Google"));
		if (loginButton == null) {
			throw new AssertionError("Login button for Google was not found.");
		}

		Page popup = null;
		try {
			popup = page.waitForPopup(
					() -> loginButton.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS)),
					new Page.WaitForPopupOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final PlaywrightException popupNotOpened) {
			waitForUiToSettle(page);
		}

		if (popup != null) {
			waitForUiToSettle(popup);
			clickGoogleAccountIfVisible(popup, googleEmail);
			waitForUiToSettle(page);
		} else {
			clickGoogleAccountIfVisible(page, googleEmail);
			waitForUiToSettle(page);
		}
	}

	private void clickGoogleAccountIfVisible(final Page page, final String googleEmail) {
		final Locator accountLocator = firstVisible(page, Arrays.asList(
				"text=" + googleEmail,
				"[data-identifier='" + googleEmail + "']",
				"div[role='link']:has-text('" + googleEmail + "')"));
		if (accountLocator != null) {
			accountLocator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		}
	}

	private void openMiNegocioMenu(final Page page) {
		final Locator negocio = firstVisible(page, Arrays.asList("aside :text('Negocio')", "nav :text('Negocio')", "text=Negocio"));
		if (negocio != null) {
			negocio.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			waitForUiToSettle(page);
		}

		final Locator miNegocio = firstVisible(page, Arrays.asList("aside :text('Mi Negocio')", "nav :text('Mi Negocio')", "text=Mi Negocio"));
		if (miNegocio == null) {
			throw new AssertionError("'Mi Negocio' menu option not found.");
		}

		miNegocio.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUiToSettle(page);
	}

	private LegalPageEvidence validateLegalLink(final BrowserContext context, final Page appPage, final String linkText,
			final String headingRegex, final Path screenshotPath) {
		final String appUrlBefore = appPage.url();
		final Locator link = firstVisible(appPage, Arrays.asList("text=" + linkText));
		if (link == null) {
			throw new AssertionError("Could not find legal link: " + linkText);
		}

		Page legalPage = null;
		boolean openedInNewTab = false;
		try {
			legalPage = context.waitForPage(() -> link.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS)),
					new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS));
			openedInNewTab = true;
		} catch (final PlaywrightException newTabNotOpened) {
			link.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			legalPage = appPage;
		}

		waitForUiToSettle(legalPage);
		assertAnyVisible(legalPage, Arrays.asList("text=/" + headingRegex + "/i"),
				"Heading for legal page not visible: " + linkText);

		final String pageText = legalPage.locator("body").innerText();
		assertTrue("Expected legal content text on: " + linkText, pageText != null && pageText.trim().length() > 120);
		takeScreenshot(legalPage, screenshotPath, true);

		final String legalUrl = legalPage.url();
		if (openedInNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiToSettle(appPage);
		} else if (!appPage.url().equals(appUrlBefore)) {
			appPage.navigate(appUrlBefore);
			waitForUiToSettle(appPage);
		}

		return new LegalPageEvidence(legalUrl);
	}

	private void fillInputIfVisible(final Page page, final List<String> selectors, final String text) {
		final Locator input = firstVisible(page, selectors);
		if (input != null) {
			input.fill(text, new Locator.FillOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		}
	}

	private void clickByVisibleText(final Page page, final List<String> texts) {
		final Locator target = firstVisible(page, toTextSelectors(texts));
		if (target == null) {
			throw new AssertionError("Clickable element not found by text: " + texts);
		}
		target.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUiToSettle(page);
	}

	private List<String> toTextSelectors(final List<String> texts) {
		return texts.stream().map(text -> text.startsWith("text=") ? text : "text=" + text).toList();
	}

	private Locator firstVisible(final Page page, final List<String> selectors) {
		for (final String selector : selectors) {
			try {
				page.waitForSelector(selector, new Page.WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE)
						.setTimeout(2_500));
				final Locator locator = page.locator(selector).first();
				if (locator != null && locator.isVisible()) {
					return locator;
				}
			} catch (final PlaywrightException ignored) {
			}
		}
		return null;
	}

	private void assertAnyVisible(final Page page, final List<String> selectors, final String message) {
		final Locator locator = firstVisible(page, selectors);
		if (locator == null) {
			throw new AssertionError(message + " Selectors tried: " + selectors);
		}
	}

	private void waitForUiToSettle(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// Some SPA screens keep network activity open; DOM loaded is sufficient fallback.
		}
		page.waitForTimeout(500);
	}

	private void takeScreenshot(final Page page, final Path path, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private Path createArtifactsDir() throws Exception {
		final String folderName = "saleads-mi-negocio-" + LocalDateTime.now().format(RUN_FOLDER_FORMAT);
		final Path targetDir = Path.of("target", "e2e-artifacts", folderName);
		return Files.createDirectories(targetDir);
	}

	private void runStep(final Map<String, StepResult> report, final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			report.put(stepName, StepResult.pass("OK"));
		} catch (final Throwable throwable) {
			report.put(stepName, StepResult.fail(throwable.getMessage()));
		}
	}

	private boolean allMandatoryStepsPass(final Map<String, StepResult> report) {
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (entry.getKey().endsWith("URL")) {
				continue;
			}
			if (!entry.getValue().passed) {
				return false;
			}
		}
		return true;
	}

	private String buildReport(final Map<String, StepResult> report, final Path artifactsDir) {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio workflow report").append(System.lineSeparator());
		sb.append("Artifacts: ").append(artifactsDir.toAbsolutePath()).append(System.lineSeparator());
		sb.append(System.lineSeparator());

		final List<String> orderedFields = Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad");

		for (final String field : orderedFields) {
			final StepResult stepResult = report.getOrDefault(field, StepResult.fail("Not executed"));
			sb.append("- ").append(field).append(": ").append(stepResult.passed ? "PASS" : "FAIL");
			if (stepResult.detail != null && !stepResult.detail.isBlank()) {
				sb.append(" (").append(stepResult.detail).append(")");
			}
			sb.append(System.lineSeparator());

			final String urlKey = field + " URL";
			if (report.containsKey(urlKey)) {
				sb.append("  URL: ").append(report.get(urlKey).detail).append(System.lineSeparator());
			}
		}
		return sb.toString();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class LegalPageEvidence {
		private final String url;

		private LegalPageEvidence(final String url) {
			this.url = url;
		}
	}

	private static final class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}

		private static StepResult pass(final String detail) {
			return new StepResult(true, detail);
		}

		private static StepResult fail(final String detail) {
			return new StepResult(false, detail);
		}
	}
}
