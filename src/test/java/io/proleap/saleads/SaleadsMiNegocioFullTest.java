package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.ScreenshotOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullTest {

	private static final long SHORT_WAIT_MS = 750L;
	private static final long DEFAULT_TIMEOUT_MS = 20_000L;
	private static final long LOGIN_TIMEOUT_MS = 90_000L;

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informacion General";
	private static final String STEP_DETALLES = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Terminos y Condiciones";
	private static final String STEP_POLITICA = "Politica de Privacidad";

	private static final List<String> REPORT_FIELDS = Arrays.asList(
			STEP_LOGIN,
			STEP_MI_NEGOCIO_MENU,
			STEP_AGREGAR_MODAL,
			STEP_ADMIN_VIEW,
			STEP_INFO_GENERAL,
			STEP_DETALLES,
			STEP_TUS_NEGOCIOS,
			STEP_TERMINOS,
			STEP_POLITICA);

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final Path evidenceDir = createEvidenceDirectory();
		final Map<String, StepResult> report = initializeReport();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		final String startUrl = requiredStartUrl();
		final String googleAccount = readStringConfig("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT",
				"juanlucasbarbiergarzon@gmail.com");
		final boolean headless = readBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true);

		boolean overallSuccess = true;

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext();
			Page page = context.newPage();

			page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
			page.setDefaultNavigationTimeout(LOGIN_TIMEOUT_MS);
			page.navigate(startUrl);
			waitForUi(page);

			final boolean loginPass = runStep(STEP_LOGIN, report, page, evidenceDir,
					() -> executeLoginStep(context, page, googleAccount));
			if (loginPass) {
				captureScreenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), false);
			}

			if (!loginPass) {
				markRemainingAsBlocked(report, "Blocked because login failed.");
			} else {
				final boolean menuPass = runStep(STEP_MI_NEGOCIO_MENU, report, page, evidenceDir,
						() -> openMiNegocioMenu(page));
				if (menuPass) {
					captureScreenshot(page, evidenceDir.resolve("02-mi-negocio-expanded.png"), false);
				}

				final boolean modalPass = runStep(STEP_AGREGAR_MODAL, report, page, evidenceDir,
						() -> validateAgregarNegocioModal(page));
				if (modalPass) {
					captureScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);
				}

				final boolean adminPass = runStep(STEP_ADMIN_VIEW, report, page, evidenceDir,
						() -> openAdministrarNegocios(page));
				if (adminPass) {
					captureScreenshot(page, evidenceDir.resolve("04-administrar-negocios.png"), true);
				}

				runStep(STEP_INFO_GENERAL, report, page, evidenceDir, () -> validateInformacionGeneral(page, googleAccount));
				runStep(STEP_DETALLES, report, page, evidenceDir, () -> validateDetallesCuenta(page));
				runStep(STEP_TUS_NEGOCIOS, report, page, evidenceDir, () -> validateTusNegocios(page));

				final boolean terminosPass = runStep(STEP_TERMINOS, report, page, evidenceDir,
						() -> legalUrls.put(STEP_TERMINOS,
								validateLegalLinkAndReturnUrl(context, page, "T.rminos y Condiciones",
										"T.rminos y Condiciones", evidenceDir.resolve("05-terminos-condiciones.png"))));
				if (!terminosPass) {
					legalUrls.put(STEP_TERMINOS, "N/A");
				}

				final boolean politicaPass = runStep(STEP_POLITICA, report, page, evidenceDir,
						() -> legalUrls.put(STEP_POLITICA,
								validateLegalLinkAndReturnUrl(context, page, "Pol.tica de Privacidad",
										"Pol.tica de Privacidad", evidenceDir.resolve("06-politica-privacidad.png"))));
				if (!politicaPass) {
					legalUrls.put(STEP_POLITICA, "N/A");
				}
			}

			for (final String field : REPORT_FIELDS) {
				if (!report.get(field).passed) {
					overallSuccess = false;
					break;
				}
			}
		} finally {
			writeReport(evidenceDir, report, legalUrls);
		}

		assertTrue("At least one Mi Negocio validation failed. Review evidence in target/saleads-evidence.",
				overallSuccess);
	}

	private void executeLoginStep(final BrowserContext context, final Page page, final String googleAccount) {
		if (isMainApplicationVisible(page)) {
			return;
		}

		Locator loginButton = firstVisible(page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile(
						"(?i)(sign in with google|iniciar sesi.n con google|continuar con google|google)"))));
		if (loginButton == null) {
			loginButton = requireVisibleText(page,
					"(?i)(sign in with google|iniciar sesi.n con google|continuar con google|google)",
					"Google login button");
		}

		final int pagesBefore = context.pages().size();
		loginButton.click();
		waitForUi(page);

		Page authPage = page;
		final List<Page> pagesAfter = context.pages();
		if (pagesAfter.size() > pagesBefore) {
			authPage = pagesAfter.get(pagesAfter.size() - 1);
			authPage.bringToFront();
			waitForUi(authPage);
		}

		selectGoogleAccountIfVisible(authPage, googleAccount);

		if (authPage != page && !authPage.isClosed()) {
			for (int i = 0; i < 20 && !authPage.isClosed(); i++) {
				authPage.waitForTimeout(500);
			}
		}

		page.bringToFront();
		waitForCondition(page, () -> isMainApplicationVisible(page), LOGIN_TIMEOUT_MS,
				"Main application interface did not appear after Google login.");
		assertTrue("Left sidebar navigation must be visible after login.", isSidebarVisible(page));
	}

	private void openMiNegocioMenu(final Page page) {
		if (isVisibleText(page, "(?i)Negocio")) {
			clickTextAndWait(page, "(?i)Negocio", "Negocio section");
		}
		clickTextAndWait(page, "(?i)Mi Negocio", "Mi Negocio");

		assertVisible(page, "(?i)Agregar Negocio", "Agregar Negocio submenu option");
		assertVisible(page, "(?i)Administrar Negocios", "Administrar Negocios submenu option");
	}

	private void validateAgregarNegocioModal(final Page page) {
		clickTextAndWait(page, "(?i)Agregar Negocio", "Agregar Negocio");

		assertVisible(page, "(?i)Crear Nuevo Negocio", "Crear Nuevo Negocio modal title");
		assertVisible(page, "(?i)Nombre del Negocio", "Nombre del Negocio field label");
		assertVisible(page, "(?i)Tienes 2 de 3 negocios", "business quota text");
		assertVisible(page, "(?i)Cancelar", "Cancelar button");
		assertVisible(page, "(?i)Crear Negocio", "Crear Negocio button");

		final Locator nameField = firstVisible(page.locator("input"));
		if (nameField != null) {
			nameField.fill("Negocio Prueba Automatizacion");
			waitForUi(page);
		}

		clickTextAndWait(page, "(?i)Cancelar", "Cancelar modal");
	}

	private void openAdministrarNegocios(final Page page) {
		if (!isVisibleText(page, "(?i)Administrar Negocios")) {
			clickTextAndWait(page, "(?i)Mi Negocio", "Mi Negocio");
		}

		clickTextAndWait(page, "(?i)Administrar Negocios", "Administrar Negocios");

		assertVisible(page, "(?i)Informaci.n General", "Informacion General section");
		assertVisible(page, "(?i)Detalles de la Cuenta", "Detalles de la Cuenta section");
		assertVisible(page, "(?i)Tus Negocios", "Tus Negocios section");
		assertVisible(page, "(?i)Secci.n Legal", "Seccion Legal section");
	}

	private void validateInformacionGeneral(final Page page, final String googleAccount) {
		assertVisible(page, "(?i)Informaci.n General", "Informacion General heading");
		assertVisible(page, "(?i)BUSINESS PLAN", "BUSINESS PLAN text");
		assertVisible(page, "(?i)Cambiar Plan", "Cambiar Plan button");

		final boolean emailVisible = isVisibleText(page, Pattern.quote(googleAccount))
				|| isVisibleText(page, "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
		assertTrue("Expected user email to be visible in Informacion General.", emailVisible);

		final Locator possibleName = firstVisible(page.locator("h1, h2, h3, p, span, div")
				.filter(new Locator.FilterOptions().setHasText(Pattern.compile("^[A-Za-z][A-Za-z\\s]{2,}$"))));
		assertTrue("Expected user name-like text to be visible.", possibleName != null && possibleName.isVisible());
	}

	private void validateDetallesCuenta(final Page page) {
		assertVisible(page, "(?i)Cuenta creada", "Cuenta creada label");
		assertVisible(page, "(?i)Estado activo", "Estado activo label");
		assertVisible(page, "(?i)Idioma seleccionado", "Idioma seleccionado label");
	}

	private void validateTusNegocios(final Page page) {
		assertVisible(page, "(?i)Tus Negocios", "Tus Negocios heading");
		assertVisible(page, "(?i)Agregar Negocio", "Agregar Negocio button");
		assertVisible(page, "(?i)Tienes 2 de 3 negocios", "business quota text");
	}

	private String validateLegalLinkAndReturnUrl(final BrowserContext context, final Page appPage,
			final String linkRegex, final String headingRegex, final Path screenshotPath) throws IOException {
		final int pagesBefore = context.pages().size();
		clickTextAndWait(appPage, "(?i)" + linkRegex, linkRegex);

		Page legalPage = appPage;
		final List<Page> pagesAfter = context.pages();
		if (pagesAfter.size() > pagesBefore) {
			legalPage = pagesAfter.get(pagesAfter.size() - 1);
			legalPage.bringToFront();
			waitForUi(legalPage);
		}

		assertVisible(legalPage, "(?i)" + headingRegex, "Legal page heading");
		final String bodyText = legalPage.locator("body").innerText();
		assertTrue("Expected legal content text to be visible.",
				bodyText != null && bodyText.trim().replaceAll("\\s+", " ").length() > 120);

		captureScreenshot(legalPage, screenshotPath, true);
		final String finalUrl = legalPage.url();

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private boolean runStep(final String stepName, final Map<String, StepResult> report, final Page page,
			final Path evidenceDir, final CheckedRunnable action) {
		try {
			action.run();
			report.put(stepName, StepResult.pass());
			return true;
		} catch (final Exception ex) {
			report.put(stepName, StepResult.fail(ex.getMessage()));
			captureScreenshotSilently(page, evidenceDir.resolve("fail-" + toSlug(stepName) + ".png"));
			return false;
		}
	}

	private Map<String, StepResult> initializeReport() {
		final Map<String, StepResult> report = new LinkedHashMap<>();
		for (final String field : REPORT_FIELDS) {
			report.put(field, StepResult.fail("Not executed."));
		}
		return report;
	}

	private void markRemainingAsBlocked(final Map<String, StepResult> report, final String detail) {
		for (final String field : REPORT_FIELDS) {
			if ("Not executed.".equals(report.get(field).detail)) {
				report.put(field, StepResult.fail(detail));
			}
		}
	}

	private void writeReport(final Path evidenceDir, final Map<String, StepResult> report,
			final Map<String, String> legalUrls) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		builder.append("evidence_dir=").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		builder.append(System.lineSeparator());
		builder.append("Final Report").append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			builder.append("- ").append(field).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (result.detail != null && !result.detail.isEmpty()) {
				builder.append(" (").append(result.detail).append(")");
			}
			if (STEP_TERMINOS.equals(field) && legalUrls.containsKey(STEP_TERMINOS)) {
				builder.append(" [url=").append(legalUrls.get(STEP_TERMINOS)).append("]");
			}
			if (STEP_POLITICA.equals(field) && legalUrls.containsKey(STEP_POLITICA)) {
				builder.append(" [url=").append(legalUrls.get(STEP_POLITICA)).append("]");
			}
			builder.append(System.lineSeparator());
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, builder.toString(), StandardCharsets.UTF_8);
		System.out.println(builder);
	}

	private void clickTextAndWait(final Page page, final String regex, final String name) {
		final Locator target = requireVisibleText(page, regex, name);
		target.click();
		waitForUi(page);
	}

	private void assertVisible(final Page page, final String regex, final String description) {
		final Locator target = requireVisibleText(page, regex, description);
		assertTrue("Expected visible: " + description, target.isVisible());
	}

	private Locator requireVisibleText(final Page page, final String regex, final String description) {
		final Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		final long end = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;

		while (System.currentTimeMillis() < end) {
			final Locator candidate = firstVisible(page.getByText(pattern));
			if (candidate != null) {
				try {
					candidate.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(1500));
					return candidate;
				} catch (final PlaywrightException ignored) {
					// Continue polling while the UI is rendering.
				}
			}
			page.waitForTimeout(250);
		}

		throw new AssertionError("Could not find visible text for: " + description + " using regex: " + regex);
	}

	private boolean isVisibleText(final Page page, final String regex) {
		try {
			final Locator candidate = firstVisible(page.getByText(Pattern.compile(regex, Pattern.CASE_INSENSITIVE)));
			return candidate != null && candidate.isVisible();
		} catch (final PlaywrightException ex) {
			return false;
		}
	}

	private Locator firstVisible(final Locator locator) {
		try {
			final int count = locator.count();
			for (int i = 0; i < count; i++) {
				final Locator candidate = locator.nth(i);
				if (candidate.isVisible()) {
					return candidate;
				}
			}
			return count > 0 ? locator.first() : null;
		} catch (final PlaywrightException ex) {
			return null;
		}
	}

	private boolean isSidebarVisible(final Page page) {
		final Locator sidebar = firstVisible(page.locator("aside, nav"));
		return sidebar != null && sidebar.isVisible();
	}

	private boolean isMainApplicationVisible(final Page page) {
		return isSidebarVisible(page) && (isVisibleText(page, "(?i)Mi Negocio") || isVisibleText(page, "(?i)Negocio"));
	}

	private void selectGoogleAccountIfVisible(final Page page, final String email) {
		if (isVisibleText(page, Pattern.quote(email))) {
			clickTextAndWait(page, Pattern.quote(email), "Google account " + email);
			return;
		}

		final Locator emailInput = firstVisible(page.locator("input[type='email']"));
		if (emailInput != null && emailInput.isVisible()) {
			emailInput.fill(email);
			waitForUi(page);

			if (isVisibleText(page, "(?i)^Next$|Siguiente")) {
				clickTextAndWait(page, "(?i)^Next$|Siguiente", "Google next button");
			}
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			// Some pages keep active network traffic; continue with a deterministic fixed wait.
		}
		page.waitForTimeout(SHORT_WAIT_MS);
	}

	private void waitForCondition(final Page page, final Condition condition, final long timeoutMs, final String message) {
		final long end = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < end) {
			if (condition.evaluate()) {
				return;
			}
			page.waitForTimeout(400);
		}
		throw new AssertionError(message);
	}

	private void captureScreenshot(final Page page, final Path path, final boolean fullPage) throws IOException {
		Files.createDirectories(path.getParent());
		page.screenshot(new ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private void captureScreenshotSilently(final Page page, final Path path) {
		try {
			captureScreenshot(page, path, true);
		} catch (final Exception ignored) {
			// Best effort screenshot capture on failures.
		}
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path result = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(result);
		return result;
	}

	private String requiredStartUrl() {
		final String configured = readStringConfig("saleads.url", "SALEADS_URL", null);
		if (configured == null || configured.trim().isEmpty()) {
			throw new AssertionError("Missing start URL. Provide -Dsaleads.url=<login-page-url> or SALEADS_URL.");
		}
		return configured;
	}

	private String readStringConfig(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private boolean readBooleanConfig(final String propertyName, final String envName, final boolean defaultValue) {
		final String value = readStringConfig(propertyName, envName, String.valueOf(defaultValue));
		return Boolean.parseBoolean(value);
	}

	private String toSlug(final String text) {
		return text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	@FunctionalInterface
	private interface Condition {
		boolean evaluate();
	}

	private static class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String detail) {
			return new StepResult(false, detail == null ? "Validation failed." : detail);
		}
	}
}
