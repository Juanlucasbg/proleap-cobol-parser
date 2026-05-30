package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
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
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_SWITCH = "SALEADS_E2E";
	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final long UI_TIMEOUT_MS = 25000;
	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-mi-negocio-evidence");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue("Set SALEADS_E2E=true to run this E2E test.", Boolean.parseBoolean(env(TEST_SWITCH, "false")));
		final String loginUrl = env(LOGIN_URL_ENV, "").trim();
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to a SaleADS login URL for the target environment.", !loginUrl.isEmpty());

		Files.createDirectories(EVIDENCE_DIR);

		final LinkedHashMap<String, StepResult> results = initResults();
		final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();
		final boolean headless = Boolean.parseBoolean(env(HEADLESS_ENV, "true"));

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext();
			final Page page = context.newPage();

			page.navigate(loginUrl);
			waitForUi(page);

			final boolean loginOk = runLoginStep(page, context, results.get("Login"));
			if (loginOk) {
				runMiNegocioMenuStep(page, results.get("Mi Negocio menu"));
				runAgregarNegocioModalStep(page, results.get("Agregar Negocio modal"));
				runAdministrarNegociosStep(page, results.get("Administrar Negocios view"));
				runInformacionGeneralStep(page, results.get("Información General"));
				runDetallesCuentaStep(page, results.get("Detalles de la Cuenta"));
				runTusNegociosStep(page, results.get("Tus Negocios"));
				runTerminosStep(page, context, results.get("Términos y Condiciones"), legalUrls);
				runPoliticaStep(page, context, results.get("Política de Privacidad"), legalUrls);
			} else {
				markBlocked(results, "Login failed. Remaining validations could not run.");
			}
		} catch (final Exception e) {
			final StepResult login = results.get("Login");
			if (login.isPassed()) {
				login.fail("Unexpected test-level failure: " + e.getMessage());
			}
			throw e;
		} finally {
			writeFinalReport(results, legalUrls);
		}

		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			assertTrue(entry.getKey() + " -> " + entry.getValue().summary(), entry.getValue().isPassed());
		}
	}

	private boolean runLoginStep(final Page appPage, final BrowserContext context, final StepResult step) {
		try {
			final Locator googleLogin = findGoogleLoginEntryPoint(appPage);
			final int tabsBefore = context.pages().size();
			clickAndWait(appPage, googleLogin);

			Page currentPage = appPage;
			if (context.pages().size() > tabsBefore) {
				currentPage = context.pages().get(context.pages().size() - 1);
				waitForUi(currentPage);
			}

			final Locator accountLocator = currentPage.getByText(GOOGLE_ACCOUNT_EMAIL).first();
			if (accountLocator.count() > 0) {
				accountLocator.click();
				waitForUi(currentPage);
			}

			requireVisible(appPage, "Negocio");
			requireVisible(appPage, "Mi Negocio");

			final boolean sidebarVisible = appPage.locator("aside").count() > 0 || appPage.locator("nav").count() > 0;
			if (!sidebarVisible) {
				throw new PlaywrightException("Left sidebar navigation was not detected.");
			}

			screenshot(appPage, "01-dashboard-loaded", true);
			step.pass("Dashboard and sidebar are visible after Google login.");
			return true;
		} catch (final Exception e) {
			safeErrorScreenshot(appPage, "01-login-failed");
			step.fail("Login validation failed: " + e.getMessage());
			return false;
		}
	}

	private void runMiNegocioMenuStep(final Page page, final StepResult step) {
		try {
			requireVisible(page, "Negocio");
			clickAndWait(page, page.getByText("Mi Negocio").first());
			requireVisible(page, "Agregar Negocio");
			requireVisible(page, "Administrar Negocios");
			screenshot(page, "02-mi-negocio-menu-expanded", true);
			step.pass("Mi Negocio submenu expanded with expected options.");
		} catch (final Exception e) {
			safeErrorScreenshot(page, "02-mi-negocio-menu-failed");
			step.fail("Mi Negocio menu validation failed: " + e.getMessage());
		}
	}

	private void runAgregarNegocioModalStep(final Page page, final StepResult step) {
		try {
			clickAndWait(page, page.getByText("Agregar Negocio").first());
			requireVisible(page, "Crear Nuevo Negocio");
			requireVisible(page, "Nombre del Negocio");
			requireVisible(page, "Tienes 2 de 3 negocios");
			requireVisible(page, "Cancelar");
			requireVisible(page, "Crear Negocio");

			final Locator nameInput = page.getByLabel("Nombre del Negocio").first();
			if (nameInput.count() > 0) {
				clickAndWait(page, nameInput);
			} else {
				clickAndWait(page, page.getByText("Nombre del Negocio").first());
			}
			page.keyboard().type("Negocio Prueba Automatizacion");
			screenshot(page, "03-agregar-negocio-modal", true);
			clickAndWait(page, page.getByText("Cancelar").first());
			step.pass("Agregar Negocio modal validations succeeded.");
		} catch (final Exception e) {
			safeErrorScreenshot(page, "03-agregar-negocio-modal-failed");
			step.fail("Agregar Negocio modal validation failed: " + e.getMessage());
		}
	}

	private void runAdministrarNegociosStep(final Page page, final StepResult step) {
		try {
			if (page.getByText("Administrar Negocios").first().count() == 0) {
				clickAndWait(page, page.getByText("Mi Negocio").first());
			}

			clickAndWait(page, page.getByText("Administrar Negocios").first());
			requireVisible(page, "Información General");
			requireVisible(page, "Detalles de la Cuenta");
			requireVisible(page, "Tus Negocios");
			requireVisible(page, "Sección Legal");
			screenshot(page, "04-administrar-negocios", true);
			step.pass("Administrar Negocios view loaded with all required sections.");
		} catch (final Exception e) {
			safeErrorScreenshot(page, "04-administrar-negocios-failed");
			step.fail("Administrar Negocios validation failed: " + e.getMessage());
		}
	}

	private void runInformacionGeneralStep(final Page page, final StepResult step) {
		try {
			requireVisible(page, "BUSINESS PLAN");
			requireVisible(page, "Cambiar Plan");
			requireTextPattern(page, Pattern.compile(".+@.+\\..+"), "user email");
			requireTextPattern(page, Pattern.compile("(?i)juan"), "user name");
			step.pass("Información General contains user, plan and action controls.");
		} catch (final Exception e) {
			safeErrorScreenshot(page, "05-informacion-general-failed");
			step.fail("Información General validation failed: " + e.getMessage());
		}
	}

	private void runDetallesCuentaStep(final Page page, final StepResult step) {
		try {
			requireVisible(page, "Cuenta creada");
			requireVisible(page, "Estado activo");
			requireVisible(page, "Idioma seleccionado");
			step.pass("Detalles de la Cuenta data points are visible.");
		} catch (final Exception e) {
			safeErrorScreenshot(page, "06-detalles-cuenta-failed");
			step.fail("Detalles de la Cuenta validation failed: " + e.getMessage());
		}
	}

	private void runTusNegociosStep(final Page page, final StepResult step) {
		try {
			requireVisible(page, "Tus Negocios");
			requireVisible(page, "Agregar Negocio");
			requireVisible(page, "Tienes 2 de 3 negocios");
			step.pass("Tus Negocios section and quota indicators are visible.");
		} catch (final Exception e) {
			safeErrorScreenshot(page, "07-tus-negocios-failed");
			step.fail("Tus Negocios validation failed: " + e.getMessage());
		}
	}

	private void runTerminosStep(final Page appPage, final BrowserContext context, final StepResult step,
			final Map<String, String> legalUrls) {
		validateLegalLink(appPage, context, "Términos y Condiciones", "Términos y Condiciones", "08-terminos", step,
				legalUrls);
	}

	private void runPoliticaStep(final Page appPage, final BrowserContext context, final StepResult step,
			final Map<String, String> legalUrls) {
		validateLegalLink(appPage, context, "Política de Privacidad", "Política de Privacidad", "09-politica", step,
				legalUrls);
	}

	private void validateLegalLink(final Page appPage, final BrowserContext context, final String linkText,
			final String expectedHeading, final String screenshotName, final StepResult step,
			final Map<String, String> legalUrls) {
		Page targetPage = appPage;
		final int tabsBefore = context.pages().size();

		try {
			clickAndWait(appPage, appPage.getByText(linkText).first());

			if (context.pages().size() > tabsBefore) {
				targetPage = context.pages().get(context.pages().size() - 1);
				waitForUi(targetPage);
			}

			requireVisible(targetPage, expectedHeading);
			final String bodyText = targetPage.textContent("body");
			final boolean hasLegalContent = bodyText != null && bodyText.replaceAll("\\s+", " ").trim().length() > 200;
			if (!hasLegalContent) {
				throw new PlaywrightException("Legal content text is too short or missing.");
			}

			screenshot(targetPage, screenshotName, true);
			legalUrls.put(linkText, targetPage.url());
			step.pass("Validated legal page and captured URL: " + targetPage.url());
		} catch (final Exception e) {
			safeErrorScreenshot(targetPage, screenshotName + "-failed");
			step.fail(linkText + " validation failed: " + e.getMessage());
		} finally {
			cleanupLegalNavigation(appPage, targetPage);
		}
	}

	private void cleanupLegalNavigation(final Page appPage, final Page targetPage) {
		try {
			if (targetPage != appPage && !targetPage.isClosed()) {
				targetPage.close();
				appPage.bringToFront();
				waitForUi(appPage);
				return;
			}

			appPage.goBack();
			waitForUi(appPage);
		} catch (final Exception e) {
			// best effort cleanup only
		}
	}

	private Locator findGoogleLoginEntryPoint(final Page page) {
		final Locator roleButton = page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*")));
		if (roleButton.count() > 0) {
			return roleButton.first();
		}

		final Locator roleLink = page.getByRole(AriaRole.LINK,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*")));
		if (roleLink.count() > 0) {
			return roleLink.first();
		}

		final Locator textMatch = page.getByText(Pattern.compile("(?i)(google|sign in with google|inicia sesion)"));
		if (textMatch.count() == 0) {
			throw new PlaywrightException("Google login entry point not found.");
		}

		return textMatch.first();
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click(new Locator.ClickOptions().setTimeout(UI_TIMEOUT_MS));
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException e) {
			// Some apps keep connections open permanently; DOM ready is enough.
		}
		page.waitForTimeout(600);
	}

	private void requireVisible(final Page page, final String visibleText) {
		page.waitForSelector("text=\"" + visibleText + "\"", new Page.WaitForSelectorOptions().setTimeout(UI_TIMEOUT_MS));
	}

	private void requireTextPattern(final Page page, final Pattern pattern, final String description) {
		final Locator match = page.getByText(pattern).first();
		if (match.count() == 0) {
			throw new PlaywrightException("Could not find expected " + description + " using pattern: " + pattern);
		}
	}

	private void screenshot(final Page page, final String name, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(EVIDENCE_DIR.resolve(name + ".png"))
				.setFullPage(fullPage));
	}

	private void safeErrorScreenshot(final Page page, final String name) {
		try {
			screenshot(page, name + "-" + Instant.now().toEpochMilli(), true);
		} catch (final Exception e) {
			// best effort evidence only
		}
	}

	private String env(final String key, final String fallback) {
		final String value = System.getenv(key);
		return value == null ? fallback : value;
	}

	private LinkedHashMap<String, StepResult> initResults() {
		final LinkedHashMap<String, StepResult> results = new LinkedHashMap<>();
		results.put("Login", new StepResult());
		results.put("Mi Negocio menu", new StepResult());
		results.put("Agregar Negocio modal", new StepResult());
		results.put("Administrar Negocios view", new StepResult());
		results.put("Información General", new StepResult());
		results.put("Detalles de la Cuenta", new StepResult());
		results.put("Tus Negocios", new StepResult());
		results.put("Términos y Condiciones", new StepResult());
		results.put("Política de Privacidad", new StepResult());
		return results;
	}

	private void markBlocked(final Map<String, StepResult> results, final String reason) {
		for (final StepResult stepResult : results.values()) {
			if (!stepResult.hasStatus()) {
				stepResult.fail(reason);
			}
		}
	}

	private void writeFinalReport(final Map<String, StepResult> results, final Map<String, String> legalUrls)
			throws Exception {
		final StringBuilder report = new StringBuilder();
		report.append("# SaleADS Mi Negocio Workflow Report\n\n");
		report.append("| Check | Status | Details |\n");
		report.append("|---|---|---|\n");

		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			report.append("| ").append(entry.getKey()).append(" | ")
					.append(entry.getValue().isPassed() ? "PASS" : "FAIL").append(" | ")
					.append(entry.getValue().summary().replace("|", "\\|")).append(" |\n");
		}

		if (!legalUrls.isEmpty()) {
			report.append("\n## Captured legal URLs\n");
			for (final Map.Entry<String, String> legalEntry : legalUrls.entrySet()) {
				report.append("- ").append(legalEntry.getKey()).append(": ").append(legalEntry.getValue()).append("\n");
			}
		}

		Files.writeString(EVIDENCE_DIR.resolve("final-report.md"), report.toString());
	}

	private static class StepResult {
		private Boolean passed;
		private String detail;

		void pass(final String message) {
			passed = true;
			detail = message;
		}

		void fail(final String message) {
			passed = false;
			detail = message;
		}

		boolean hasStatus() {
			return passed != null;
		}

		boolean isPassed() {
			return Boolean.TRUE.equals(passed);
		}

		String summary() {
			return detail == null ? "No details available." : detail;
		}
	}
}
