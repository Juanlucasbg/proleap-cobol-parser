package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String BUSINESS_NAME = "Negocio Prueba Automatizacion";
	private static final DateTimeFormatter RUN_STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final int DEFAULT_TIMEOUT_MS = 15000;
	private static final int NEW_TAB_WAIT_ITERATIONS = 20;
	private static final int POLL_INTERVAL_MS = 300;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String loginUrl = valueOrNull(System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL to the SaleADS login page for the environment under test before running this test.",
				loginUrl != null);

		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		final Path evidenceDir = ensureEvidenceDirectory();

		final Map<String, Boolean> results = new LinkedHashMap<>();
		final Map<String, String> details = new LinkedHashMap<>();
		String termsUrl = "";
		String privacyUrl = "";

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext();
			context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

			final Page page = context.newPage();
			page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
			page.navigate(loginUrl);
			waitForUi(page);

			final boolean login = runStep("Login", details, () -> validateLogin(page, evidenceDir));
			results.put("Login", login);

			final boolean miNegocioMenu = runDependentStep("Mi Negocio menu", login, details,
					() -> validateMiNegocioMenu(page, evidenceDir));
			results.put("Mi Negocio menu", miNegocioMenu);

			final boolean agregarModal = runDependentStep("Agregar Negocio modal", miNegocioMenu, details,
					() -> validateAgregarNegocioModal(page, evidenceDir));
			results.put("Agregar Negocio modal", agregarModal);

			final boolean administrarView = runDependentStep("Administrar Negocios view", miNegocioMenu, details,
					() -> validateAdministrarNegociosView(page, evidenceDir));
			results.put("Administrar Negocios view", administrarView);

			final boolean infoGeneral = runDependentStep("Información General", administrarView, details,
					() -> validateInformacionGeneral(page));
			results.put("Información General", infoGeneral);

			final boolean accountDetails = runDependentStep("Detalles de la Cuenta", administrarView, details,
					() -> validateDetallesCuenta(page));
			results.put("Detalles de la Cuenta", accountDetails);

			final boolean tusNegocios = runDependentStep("Tus Negocios", administrarView, details,
					() -> validateTusNegocios(page));
			results.put("Tus Negocios", tusNegocios);

			final LegalValidationResult termsResult = runDependentLegalStep("Términos y Condiciones", administrarView,
					details, () -> validateLegalDocument(page, "Términos y Condiciones", evidenceDir,
							"05-terminos-y-condiciones.png"));
			results.put("Términos y Condiciones", termsResult.passed);
			termsUrl = termsResult.finalUrl;

			final LegalValidationResult privacyResult = runDependentLegalStep("Política de Privacidad", administrarView,
					details, () -> validateLegalDocument(page, "Política de Privacidad", evidenceDir,
							"06-politica-de-privacidad.png"));
			results.put("Política de Privacidad", privacyResult.passed);
			privacyUrl = privacyResult.finalUrl;
		}

		final String report = buildFinalReport(results, details, termsUrl, privacyUrl);
		System.out.println(report);

		final List<String> failedSteps = results.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		assertTrue("SaleADS Mi Negocio workflow failed in steps: " + failedSteps + System.lineSeparator() + report,
				failedSteps.isEmpty());
	}

	private boolean validateLogin(final Page page, final Path evidenceDir) {
		final int pagesBefore = page.context().pages().size();
		clickAndWait(page, "button:has-text(\"Sign in with Google\")", "button:has-text(\"Iniciar sesión con Google\")",
				"button:has-text(\"Continuar con Google\")", "button:has-text(\"Google\")", "text=Sign in with Google",
				"text=Iniciar sesión con Google", "text=Continuar con Google");

		Page activePage = waitForNewTab(page.context(), pagesBefore);
		if (activePage == null) {
			activePage = page;
		}

		if (isAnyVisible(activePage, "text=" + GOOGLE_ACCOUNT_EMAIL, "[data-email=\"" + GOOGLE_ACCOUNT_EMAIL + "\"]")) {
			clickAndWait(activePage, "text=" + GOOGLE_ACCOUNT_EMAIL, "[data-email=\"" + GOOGLE_ACCOUNT_EMAIL + "\"]");
		}

		if (activePage != page && !activePage.isClosed()) {
			waitForUi(page);
			page.bringToFront();
		}

		final boolean mainInterfaceVisible = waitUntilAnyVisible(page, NEW_TAB_WAIT_ITERATIONS * 2, "aside",
				"[role='navigation']", "text=Negocio");
		captureScreenshot(page, evidenceDir, "01-dashboard-loaded.png", true);

		return mainInterfaceVisible;
	}

	private boolean validateMiNegocioMenu(final Page page, final Path evidenceDir) {
		clickAndWait(page, "aside >> text=Mi Negocio", "nav >> text=Mi Negocio", "text=Mi Negocio");

		final boolean agregarVisible = waitUntilAnyVisible(page, NEW_TAB_WAIT_ITERATIONS, "aside >> text=Agregar Negocio",
				"text=Agregar Negocio");
		final boolean administrarVisible = waitUntilAnyVisible(page, NEW_TAB_WAIT_ITERATIONS,
				"aside >> text=Administrar Negocios", "text=Administrar Negocios");

		captureScreenshot(page, evidenceDir, "02-mi-negocio-expanded.png", false);
		return agregarVisible && administrarVisible;
	}

	private boolean validateAgregarNegocioModal(final Page page, final Path evidenceDir) {
		clickAndWait(page, "aside >> text=Agregar Negocio", "text=Agregar Negocio");
		final boolean modalTitleVisible = waitUntilAnyVisible(page, NEW_TAB_WAIT_ITERATIONS, "text=Crear Nuevo Negocio");

		final boolean inputVisible = isAnyVisible(page, "div[role='dialog'] input[placeholder*='Nombre']",
				"div[role='dialog'] input[name*='nombre']", "div[role='dialog'] input", "label:has-text(\"Nombre del Negocio\")");
		final boolean businessLimitVisible = isAnyVisible(page, "text=Tienes 2 de 3 negocios");
		final boolean cancelVisible = isAnyVisible(page, "div[role='dialog'] button:has-text(\"Cancelar\")",
				"button:has-text(\"Cancelar\")");
		final boolean createVisible = isAnyVisible(page, "div[role='dialog'] button:has-text(\"Crear Negocio\")",
				"button:has-text(\"Crear Negocio\")");

		captureScreenshot(page, evidenceDir, "03-agregar-negocio-modal.png", false);

		if (inputVisible) {
			final Locator input = firstVisible(page, "div[role='dialog'] input[placeholder*='Nombre']",
					"div[role='dialog'] input[name*='nombre']", "div[role='dialog'] input");
			input.fill(BUSINESS_NAME);
		}

		if (cancelVisible) {
			clickAndWait(page, "div[role='dialog'] button:has-text(\"Cancelar\")", "button:has-text(\"Cancelar\")");
		}

		return modalTitleVisible && inputVisible && businessLimitVisible && cancelVisible && createVisible;
	}

	private boolean validateAdministrarNegociosView(final Page page, final Path evidenceDir) {
		if (!isAnyVisible(page, "aside >> text=Administrar Negocios", "text=Administrar Negocios")) {
			clickAndWait(page, "aside >> text=Mi Negocio", "nav >> text=Mi Negocio", "text=Mi Negocio");
		}

		clickAndWait(page, "aside >> text=Administrar Negocios", "text=Administrar Negocios");
		final boolean infoGeneral = waitUntilAnyVisible(page, NEW_TAB_WAIT_ITERATIONS * 2, "text=Información General");
		final boolean detalles = isAnyVisible(page, "text=Detalles de la Cuenta");
		final boolean tusNegocios = isAnyVisible(page, "text=Tus Negocios");
		final boolean legalSection = isAnyVisible(page, "text=Sección Legal", "text=Seccion Legal");

		captureScreenshot(page, evidenceDir, "04-administrar-negocios-view.png", true);
		return infoGeneral && detalles && tusNegocios && legalSection;
	}

	private boolean validateInformacionGeneral(final Page page) {
		final boolean userNameVisible = isAnyVisible(page, "[data-testid='user-name']", "[data-testid='profile-name']",
				"text=Nombre", "text=Usuario");
		final boolean userEmailVisible = isAnyVisible(page, "text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/");
		final boolean businessPlanVisible = isAnyVisible(page, "text=BUSINESS PLAN");
		final boolean changePlanVisible = isAnyVisible(page, "button:has-text(\"Cambiar Plan\")", "text=Cambiar Plan");
		return userNameVisible && userEmailVisible && businessPlanVisible && changePlanVisible;
	}

	private boolean validateDetallesCuenta(final Page page) {
		final boolean cuentaCreada = isAnyVisible(page, "text=Cuenta creada");
		final boolean estadoActivo = isAnyVisible(page, "text=Estado activo");
		final boolean idiomaSeleccionado = isAnyVisible(page, "text=Idioma seleccionado");
		return cuentaCreada && estadoActivo && idiomaSeleccionado;
	}

	private boolean validateTusNegocios(final Page page) {
		final boolean businessListVisible = isAnyVisible(page, "text=Tus Negocios", "[data-testid='business-list']", "table");
		final boolean addButtonVisible = isAnyVisible(page, "button:has-text(\"Agregar Negocio\")", "text=Agregar Negocio");
		final boolean businessLimitVisible = isAnyVisible(page, "text=Tienes 2 de 3 negocios");
		return businessListVisible && addButtonVisible && businessLimitVisible;
	}

	private LegalValidationResult validateLegalDocument(final Page appPage, final String linkText, final Path evidenceDir,
			final String screenshotName) {
		final BrowserContext context = appPage.context();
		final int pagesBefore = context.pages().size();

		click(appPage, "text=" + linkText, "a:has-text(\"" + linkText + "\")", "button:has-text(\"" + linkText + "\")");

		Page legalPage = waitForNewTab(context, pagesBefore);
		if (legalPage == null) {
			waitForUi(appPage);
			legalPage = appPage;
		} else {
			waitForUi(legalPage);
		}

		final boolean headingVisible = waitUntilAnyVisible(legalPage, NEW_TAB_WAIT_ITERATIONS,
				"h1:has-text(\"" + linkText + "\")", "h2:has-text(\"" + linkText + "\")", "text=" + linkText);
		final boolean legalTextVisible = hasVisibleLegalContent(legalPage);
		final String finalUrl = legalPage.url();

		captureScreenshot(legalPage, evidenceDir, screenshotName, true);

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				appPage.goBack();
				waitForUi(appPage);
			} catch (final RuntimeException ignored) {
				// Some environments may keep legal content in place without history.
			}
		}

		return new LegalValidationResult(headingVisible && legalTextVisible, finalUrl);
	}

	private boolean hasVisibleLegalContent(final Page page) {
		return isAnyVisible(page, "main p", "article p", "section p", "p", "li");
	}

	private boolean runStep(final String stepName, final Map<String, String> details, final StepAction action) {
		try {
			final boolean passed = action.execute();
			if (!passed) {
				details.put(stepName, "Validation returned false.");
			}
			return passed;
		} catch (final RuntimeException runtimeException) {
			details.put(stepName, runtimeException.getMessage());
			return false;
		} catch (final Exception exception) {
			details.put(stepName, exception.getMessage());
			return false;
		}
	}

	private boolean runDependentStep(final String stepName, final boolean dependencyPassed, final Map<String, String> details,
			final StepAction action) {
		if (!dependencyPassed) {
			details.put(stepName, "Skipped because prerequisite step failed.");
			return false;
		}
		return runStep(stepName, details, action);
	}

	private LegalValidationResult runDependentLegalStep(final String stepName, final boolean dependencyPassed,
			final Map<String, String> details, final LegalStepAction action) {
		if (!dependencyPassed) {
			details.put(stepName, "Skipped because prerequisite step failed.");
			return new LegalValidationResult(false, "");
		}
		try {
			final LegalValidationResult result = action.execute();
			if (!result.passed) {
				details.put(stepName, "Validation returned false.");
			}
			return result;
		} catch (final RuntimeException runtimeException) {
			details.put(stepName, runtimeException.getMessage());
			return new LegalValidationResult(false, "");
		} catch (final Exception exception) {
			details.put(stepName, exception.getMessage());
			return new LegalValidationResult(false, "");
		}
	}

	private void clickAndWait(final Page page, final String... selectors) {
		click(page, selectors);
		waitForUi(page);
	}

	private void click(final Page page, final String... selectors) {
		final Locator target = firstVisible(page, selectors);
		target.click();
	}

	private Locator firstVisible(final Page page, final String... selectors) {
		for (final String selector : selectors) {
			try {
				final Locator locator = page.locator(selector);
				final int matches = locator.count();
				for (int index = 0; index < matches; index++) {
					final Locator candidate = locator.nth(index);
					if (candidate.isVisible()) {
						return candidate;
					}
				}
			} catch (final RuntimeException ignored) {
				// Invalid selector for this environment variant; continue with fallback selectors.
			}
		}
		throw new IllegalStateException("Could not find any visible element for selectors: " + Arrays.toString(selectors));
	}

	private boolean waitUntilAnyVisible(final Page page, final int iterations, final String... selectors) {
		for (int i = 0; i < iterations; i++) {
			if (isAnyVisible(page, selectors)) {
				return true;
			}
			sleep(POLL_INTERVAL_MS);
		}
		return false;
	}

	private boolean isAnyVisible(final Page page, final String... selectors) {
		for (final String selector : selectors) {
			try {
				final Locator locator = page.locator(selector);
				final int matches = locator.count();
				for (int index = 0; index < matches; index++) {
					if (locator.nth(index).isVisible()) {
						return true;
					}
				}
			} catch (final RuntimeException ignored) {
				// Invalid selector for this environment variant; continue with fallback selectors.
			}
		}
		return false;
	}

	private Page waitForNewTab(final BrowserContext context, final int previousPageCount) {
		for (int i = 0; i < NEW_TAB_WAIT_ITERATIONS; i++) {
			final List<Page> pages = context.pages();
			if (pages.size() > previousPageCount) {
				return pages.get(pages.size() - 1);
			}
			sleep(POLL_INTERVAL_MS);
		}
		return null;
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final RuntimeException ignored) {
			// Some clicks only update partial content and do not trigger a full load event.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (final RuntimeException ignored) {
			// Network idle may never happen when live polling is active.
		}
		sleep(POLL_INTERVAL_MS);
	}

	private void captureScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private Path ensureEvidenceDirectory() throws IOException {
		final String runFolderName = "saleads-mi-negocio-" + RUN_STAMP_FORMAT.format(LocalDateTime.now());
		final Path evidenceDir = Paths.get("target", "saleads-evidence", runFolderName);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private String buildFinalReport(final Map<String, Boolean> results, final Map<String, String> details, final String termsUrl,
			final String privacyUrl) {
		final StringBuilder report = new StringBuilder();
		report.append(System.lineSeparator());
		report.append("===== SaleADS Mi Negocio Workflow Report =====").append(System.lineSeparator());
		appendReportLine(report, results, details, "Login");
		appendReportLine(report, results, details, "Mi Negocio menu");
		appendReportLine(report, results, details, "Agregar Negocio modal");
		appendReportLine(report, results, details, "Administrar Negocios view");
		appendReportLine(report, results, details, "Información General");
		appendReportLine(report, results, details, "Detalles de la Cuenta");
		appendReportLine(report, results, details, "Tus Negocios");
		appendReportLine(report, results, details, "Términos y Condiciones");
		appendReportLine(report, results, details, "Política de Privacidad");
		report.append("Terminos y Condiciones URL: ").append(termsUrl).append(System.lineSeparator());
		report.append("Politica de Privacidad URL: ").append(privacyUrl).append(System.lineSeparator());
		report.append("==============================================").append(System.lineSeparator());
		return report.toString();
	}

	private void appendReportLine(final StringBuilder report, final Map<String, Boolean> results, final Map<String, String> details,
			final String step) {
		report.append(step).append(": ").append(results.getOrDefault(step, false) ? "PASS" : "FAIL");
		if (details.containsKey(step)) {
			report.append(" (").append(details.get(step)).append(")");
		}
		report.append(System.lineSeparator());
	}

	private String valueOrNull(final String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return value.trim();
	}

	private void sleep(final int millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		boolean execute() throws Exception;
	}

	@FunctionalInterface
	private interface LegalStepAction {
		LegalValidationResult execute() throws Exception;
	}

	private static class LegalValidationResult {
		private final boolean passed;
		private final String finalUrl;

		private LegalValidationResult(final boolean passed, final String finalUrl) {
			this.passed = passed;
			this.finalUrl = finalUrl;
		}
	}
}
