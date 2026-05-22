package io.proleap.cobol.e2e;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * The test does not hardcode an environment URL. Provide the login page with one of:
 * <ul>
 * <li>System property: {@code -Dsaleads.login.url=https://...}</li>
 * <li>Environment variable: {@code SALEADS_LOGIN_URL}</li>
 * </ul>
 * </p>
 */
public class SaleadsMiNegocioFullTest {

	private static final Pattern LOGIN_BUTTON_PATTERN = Pattern
			.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|continuar\\s*con\\s*google)");
	private static final Pattern NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*Negocio\\s*$");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)Mi\\s+Negocio");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)Agregar\\s+Negocio");
	private static final Pattern ADMIN_NEGOCIO_PATTERN = Pattern.compile("(?i)Administrar\\s+Negocios");
	private static final Pattern CREAR_NEGOCIO_PATTERN = Pattern.compile("(?i)Crear\\s+Nuevo\\s+Negocio");
	private static final Pattern NOMBRE_NEGOCIO_PATTERN = Pattern.compile("(?i)Nombre\\s+del\\s+Negocio");
	private static final Pattern TERMS_PATTERN = Pattern.compile("(?i)T[eé]rminos\\s+y\\s+Condiciones");
	private static final Pattern PRIVACY_PATTERN = Pattern.compile("(?i)Pol[ií]tica\\s+de\\s+Privacidad");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_GENERAL_INFO = "Información General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Términos y Condiciones";
	private static final String STEP_PRIVACY = "Política de Privacidad";

	private static final List<String> STEP_ORDER = Arrays.asList(STEP_LOGIN, STEP_MENU, STEP_MODAL, STEP_ADMIN_VIEW,
			STEP_GENERAL_INFO, STEP_ACCOUNT_DETAILS, STEP_BUSINESSES, STEP_TERMS, STEP_PRIVACY);

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String loginUrl = getSetting("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Missing SaleADS login URL. Set -Dsaleads.login.url or SALEADS_LOGIN_URL to the current environment login page.",
				loginUrl != null && !loginUrl.isBlank());

		final String googleAccount = getSetting("saleads.google.email", "SALEADS_GOOGLE_EMAIL",
				"juanlucasbarbiergarzon@gmail.com");
		final boolean headless = Boolean.parseBoolean(getSetting("saleads.headless", "SALEADS_HEADLESS", "true"));

		final Map<String, Boolean> results = initializeStepResults();
		final List<String> failures = new ArrayList<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final Path evidenceDir = createEvidenceDirectory();
		final Path reportPath = evidenceDir.resolve("saleads_mi_negocio_full_report.json");

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(150));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
			final Page appPage = context.newPage();

			appPage.navigate(loginUrl);
			waitForUiLoad(appPage);

			results.put(STEP_LOGIN, runStep(STEP_LOGIN, failures, () -> {
				doLoginWithGoogle(appPage, context, googleAccount);
				waitForSidebar(appPage, 60000);
				captureScreenshot(appPage, evidenceDir, "01-dashboard-loaded", false);
			}));

			results.put(STEP_MENU, runStep(STEP_MENU, failures, () -> {
				clickAndWait(appPage, findNegocioControl(appPage), "Negocio");
				clickAndWait(appPage, findMiNegocioControl(appPage), "Mi Negocio");
				waitForAnyVisible(appPage, "Agregar Negocio menu entry", 20000,
						appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
						appPage.getByText(AGREGAR_NEGOCIO_PATTERN));
				waitForAnyVisible(appPage, "Administrar Negocios menu entry", 20000,
						appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADMIN_NEGOCIO_PATTERN)),
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADMIN_NEGOCIO_PATTERN)),
						appPage.getByText(ADMIN_NEGOCIO_PATTERN));
				captureScreenshot(appPage, evidenceDir, "02-mi-negocio-expanded-menu", false);
			}));

			results.put(STEP_MODAL, runStep(STEP_MODAL, failures, () -> {
				clickAndWait(appPage, findAgregarNegocioControl(appPage), "Agregar Negocio");
				waitForAnyVisible(appPage, "Crear Nuevo Negocio modal title", 20000, appPage.getByText(CREAR_NEGOCIO_PATTERN),
						appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(CREAR_NEGOCIO_PATTERN)));
				waitForAnyVisible(appPage, "Nombre del Negocio input", 10000,
						appPage.getByLabel(NOMBRE_NEGOCIO_PATTERN),
						appPage.getByPlaceholder(NOMBRE_NEGOCIO_PATTERN),
						appPage.locator("input[name*='negocio' i], input[id*='negocio' i]"));
				waitForAnyVisible(appPage, "Tienes 2 de 3 negocios text", 10000,
						appPage.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
				waitForAnyVisible(appPage, "Cancelar button", 10000,
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Cancelar\\s*$"))));
				waitForAnyVisible(appPage, "Crear Negocio button", 10000,
						appPage.getByRole(AriaRole.BUTTON,
								new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Crear\\s+Negocio"))));

				final Locator nombreInput = waitForAnyVisible(appPage, "Nombre del Negocio input to type", 10000,
						appPage.getByLabel(NOMBRE_NEGOCIO_PATTERN),
						appPage.getByPlaceholder(NOMBRE_NEGOCIO_PATTERN),
						appPage.locator("input[name*='negocio' i], input[id*='negocio' i]"));
				nombreInput.click();
				nombreInput.fill("Negocio Prueba Automatizacion");
				waitForUiLoad(appPage);

				captureScreenshot(appPage, evidenceDir, "03-agregar-negocio-modal", false);
				clickAndWait(appPage, appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Cancelar\\s*$"))), "Cancelar");
			}));

			results.put(STEP_ADMIN_VIEW, runStep(STEP_ADMIN_VIEW, failures, () -> {
				ensureMiNegocioExpanded(appPage);
				clickAndWait(appPage, findAdministrarNegocioControl(appPage), "Administrar Negocios");
				waitForAnyVisible(appPage, "Informacion General section", 25000,
						appPage.getByText(Pattern.compile("(?i)Informaci[oó]n\\s+General")));
				waitForAnyVisible(appPage, "Detalles de la Cuenta section", 25000,
						appPage.getByText(Pattern.compile("(?i)Detalles\\s+de\\s+la\\s+Cuenta")));
				waitForAnyVisible(appPage, "Tus Negocios section", 25000,
						appPage.getByText(Pattern.compile("(?i)Tus\\s+Negocios")));
				waitForAnyVisible(appPage, "Seccion Legal section", 25000,
						appPage.getByText(Pattern.compile("(?i)Secci[oó]n\\s+Legal")));
				captureScreenshot(appPage, evidenceDir, "04-administrar-negocios-account-page", true);
			}));

			results.put(STEP_GENERAL_INFO, runStep(STEP_GENERAL_INFO, failures, () -> {
				waitForAnyVisible(appPage, "BUSINESS PLAN text", 10000,
						appPage.getByText(Pattern.compile("(?i)BUSINESS\\s+PLAN")));
				waitForAnyVisible(appPage, "Cambiar Plan button", 10000,
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cambiar\\s+Plan"))));

				final String pageText = textOrEmpty(appPage.locator("body"));
				assertTrue("User email is not visible.", EMAIL_PATTERN.matcher(pageText).find());
				assertTrue("User name is not visible.", containsLikelyPersonName(pageText));
			}));

			results.put(STEP_ACCOUNT_DETAILS, runStep(STEP_ACCOUNT_DETAILS, failures, () -> {
				waitForAnyVisible(appPage, "Cuenta creada", 10000,
						appPage.getByText(Pattern.compile("(?i)Cuenta\\s+creada")));
				waitForAnyVisible(appPage, "Estado activo", 10000,
						appPage.getByText(Pattern.compile("(?i)Estado\\s+activo")));
				waitForAnyVisible(appPage, "Idioma seleccionado", 10000,
						appPage.getByText(Pattern.compile("(?i)Idioma\\s+seleccionado")));
			}));

			results.put(STEP_BUSINESSES, runStep(STEP_BUSINESSES, failures, () -> {
				waitForAnyVisible(appPage, "Tus Negocios heading", 10000,
						appPage.getByText(Pattern.compile("(?i)Tus\\s+Negocios")));
				waitForAnyVisible(appPage, "Agregar Negocio button on account page", 10000,
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
						appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)));
				waitForAnyVisible(appPage, "Tienes 2 de 3 negocios in business section", 10000,
						appPage.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
				final Locator businessRows = appPage
						.locator("[data-testid*='business' i], [class*='business' i], li, table tbody tr, .card");
				assertTrue("Business list is not visible.", businessRows.count() > 0);
			}));

			results.put(STEP_TERMS, runStep(STEP_TERMS, failures, () -> {
				final Page legalPage = openLegalPage(appPage, context, TERMS_PATTERN);
				waitForAnyVisible(legalPage, "Terminos y Condiciones heading", 20000,
						legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TERMS_PATTERN)),
						legalPage.getByText(TERMS_PATTERN));
				assertTrue("Legal content is not visible for Terminos y Condiciones.",
						normalized(textOrEmpty(legalPage.locator("body"))).length() > 200);
				captureScreenshot(legalPage, evidenceDir, "05-terminos-y-condiciones", true);
				legalUrls.put("Términos y Condiciones", legalPage.url());
				returnFromLegalPage(appPage, legalPage);
			}));

			results.put(STEP_PRIVACY, runStep(STEP_PRIVACY, failures, () -> {
				final Page legalPage = openLegalPage(appPage, context, PRIVACY_PATTERN);
				waitForAnyVisible(legalPage, "Politica de Privacidad heading", 20000,
						legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(PRIVACY_PATTERN)),
						legalPage.getByText(PRIVACY_PATTERN));
				assertTrue("Legal content is not visible for Politica de Privacidad.",
						normalized(textOrEmpty(legalPage.locator("body"))).length() > 200);
				captureScreenshot(legalPage, evidenceDir, "06-politica-de-privacidad", true);
				legalUrls.put("Política de Privacidad", legalPage.url());
				returnFromLegalPage(appPage, legalPage);
			}));
		}

		writeReport(reportPath, results, failures, legalUrls, evidenceDir);
		assertNoStepFailures(results, failures, reportPath);
	}

	private void assertNoStepFailures(final Map<String, Boolean> results, final List<String> failures, final Path reportPath) {
		final List<String> failedSteps = new ArrayList<>();
		for (final String step : STEP_ORDER) {
			if (!Boolean.TRUE.equals(results.get(step))) {
				failedSteps.add(step);
			}
		}
		if (!failedSteps.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failed for steps: " + failedSteps + ". Details: " + failures
					+ ". Report: " + reportPath.toAbsolutePath());
		}
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		final Path evidenceDir = Path.of("target", "saleads-mi-negocio-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private Map<String, Boolean> initializeStepResults() {
		final Map<String, Boolean> results = new LinkedHashMap<>();
		for (final String step : STEP_ORDER) {
			results.put(step, Boolean.FALSE);
		}
		return results;
	}

	private void doLoginWithGoogle(final Page appPage, final BrowserContext context, final String googleAccountEmail) {
		if (isSidebarVisible(appPage)) {
			return;
		}

		final Locator loginControl = waitForAnyVisible(appPage, "Google login button", 20000,
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN)),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN)),
				appPage.getByText(LOGIN_BUTTON_PATTERN));

		Page authPage = null;
		try {
			authPage = context.waitForPage(() -> clickAndWait(appPage, loginControl, "Sign in with Google"));
		} catch (final PlaywrightException ex) {
			clickAndWait(appPage, loginControl, "Sign in with Google");
		}

		final Page activeAuthPage = authPage != null ? authPage : appPage;
		waitForUiLoad(activeAuthPage);

		final Locator emailChoice = activeAuthPage.getByText(Pattern.compile(Pattern.quote(googleAccountEmail)));
		if (isVisibleSoon(activeAuthPage, emailChoice, 7000)) {
			clickAndWait(activeAuthPage, emailChoice, "Google account selection");
		}

		if (authPage != null) {
			waitForUiLoad(appPage);
			appPage.bringToFront();
		}
	}

	private void ensureMiNegocioExpanded(final Page page) {
		if (!isVisibleSoon(page, page.getByText(ADMIN_NEGOCIO_PATTERN), 1500)) {
			clickAndWait(page, findMiNegocioControl(page), "Mi Negocio");
		}
	}

	private Locator findNegocioControl(final Page page) {
		return waitForAnyVisible(page, "Negocio control", 20000,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEGOCIO_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(NEGOCIO_PATTERN)),
				page.getByText(NEGOCIO_PATTERN));
	}

	private Locator findMiNegocioControl(final Page page) {
		return waitForAnyVisible(page, "Mi Negocio control", 20000,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
				page.getByText(MI_NEGOCIO_PATTERN));
	}

	private Locator findAgregarNegocioControl(final Page page) {
		return waitForAnyVisible(page, "Agregar Negocio control", 20000,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
				page.getByText(AGREGAR_NEGOCIO_PATTERN));
	}

	private Locator findAdministrarNegocioControl(final Page page) {
		return waitForAnyVisible(page, "Administrar Negocios control", 20000,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADMIN_NEGOCIO_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADMIN_NEGOCIO_PATTERN)),
				page.getByText(ADMIN_NEGOCIO_PATTERN));
	}

	private Page openLegalPage(final Page appPage, final BrowserContext context, final Pattern legalControlPattern) {
		final Locator legalControl = waitForAnyVisible(appPage, "Legal link " + legalControlPattern.pattern(), 20000,
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(legalControlPattern)),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(legalControlPattern)),
				appPage.getByText(legalControlPattern));

		Page legalPage = null;
		try {
			legalPage = context.waitForPage(() -> clickAndWait(appPage, legalControl, legalControlPattern.pattern()));
		} catch (final PlaywrightException ex) {
			clickAndWait(appPage, legalControl, legalControlPattern.pattern());
		}

		final Page resultPage = legalPage != null ? legalPage : appPage;
		waitForUiLoad(resultPage);
		return resultPage;
	}

	private void returnFromLegalPage(final Page appPage, final Page legalPage) {
		if (legalPage != appPage && !legalPage.isClosed()) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
			return;
		}

		// Same-tab navigation: return to the application page.
		appPage.goBack();
		waitForUiLoad(appPage);
	}

	private boolean runStep(final String stepName, final List<String> failures, final StepAction stepAction) {
		try {
			stepAction.run();
			return true;
		} catch (final Throwable throwable) {
			failures.add(stepName + ": " + throwable.getMessage());
			return false;
		}
	}

	private void clickAndWait(final Page page, final Locator locator, final String actionName) {
		try {
			locator.scrollIntoViewIfNeeded();
			locator.click(new Locator.ClickOptions().setTimeout(15000));
		} catch (final PlaywrightException firstClickError) {
			locator.click(new Locator.ClickOptions().setTimeout(15000).setForce(true));
		}
		waitForUiLoad(page);
	}

	private void waitForSidebar(final Page page, final long timeoutMs) {
		waitForAnyVisible(page, "left sidebar", timeoutMs, page.locator("aside"),
				page.getByRole(AriaRole.NAVIGATION), page.getByText(Pattern.compile("(?i)Negocio")));
	}

	private boolean isSidebarVisible(final Page page) {
		return isVisibleSoon(page, page.locator("aside"), 1000)
				|| isVisibleSoon(page, page.getByText(Pattern.compile("(?i)Negocio")), 1000);
	}

	private boolean isVisibleSoon(final Page page, final Locator locator, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			try {
				if (locator.first().isVisible()) {
					return true;
				}
			} catch (final PlaywrightException ignored) {
				// Keep polling until timeout.
			}
			page.waitForTimeout(200);
		}
		return false;
	}

	private Locator waitForAnyVisible(final Page page, final String description, final long timeoutMs,
			final Locator... candidates) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final Locator candidate : candidates) {
				try {
					final Locator first = candidate.first();
					if (first.isVisible()) {
						return first;
					}
				} catch (final PlaywrightException ignored) {
					// Keep trying other selectors while page settles.
				}
			}
			page.waitForTimeout(250);
		}
		throw new AssertionError("Timed out waiting for visible element: " + description);
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (final PlaywrightException ignored) {
			// Some apps keep ongoing requests; DOM loaded is enough in that case.
		}
		page.waitForTimeout(400);
	}

	private void captureScreenshot(final Page page, final Path evidenceDir, final String name, final boolean fullPage)
			throws IOException {
		final Path screenshotPath = evidenceDir.resolve(name + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		if (!Files.exists(screenshotPath)) {
			throw new IOException("Screenshot was not created: " + screenshotPath);
		}
	}

	private void writeReport(final Path reportPath, final Map<String, Boolean> results, final List<String> failures,
			final Map<String, String> legalUrls, final Path evidenceDir) throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("{\n");
		report.append("  \"test\": \"saleads_mi_negocio_full_test\",\n");
		report.append("  \"status\": \"").append(allStepsPassed(results) ? "PASS" : "FAIL").append("\",\n");
		report.append("  \"steps\": {\n");

		for (int i = 0; i < STEP_ORDER.size(); i++) {
			final String step = STEP_ORDER.get(i);
			report.append("    \"").append(escapeJson(step)).append("\": \"")
					.append(Boolean.TRUE.equals(results.get(step)) ? "PASS" : "FAIL").append("\"");
			report.append(i < STEP_ORDER.size() - 1 ? ",\n" : "\n");
		}
		report.append("  },\n");
		report.append("  \"final_urls\": {\n");
		report.append("    \"Términos y Condiciones\": \"")
				.append(escapeJson(legalUrls.getOrDefault("Términos y Condiciones", ""))).append("\",\n");
		report.append("    \"Política de Privacidad\": \"")
				.append(escapeJson(legalUrls.getOrDefault("Política de Privacidad", ""))).append("\"\n");
		report.append("  },\n");
		report.append("  \"evidence_dir\": \"").append(escapeJson(evidenceDir.toAbsolutePath().toString())).append("\",\n");
		report.append("  \"failures\": [\n");
		for (int i = 0; i < failures.size(); i++) {
			report.append("    \"").append(escapeJson(failures.get(i))).append("\"");
			report.append(i < failures.size() - 1 ? ",\n" : "\n");
		}
		report.append("  ]\n");
		report.append("}\n");

		Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
	}

	private boolean allStepsPassed(final Map<String, Boolean> results) {
		for (final String step : STEP_ORDER) {
			if (!Boolean.TRUE.equals(results.get(step))) {
				return false;
			}
		}
		return true;
	}

	private String escapeJson(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private void assertTrue(final String message, final boolean condition) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private String textOrEmpty(final Locator locator) {
		final String text = locator.textContent();
		return text == null ? "" : text;
	}

	private boolean containsLikelyPersonName(final String text) {
		final String normalizedText = normalized(text);
		final String[] lines = normalizedText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isBlank()) {
				continue;
			}
			if (line.contains("@") || line.contains("business plan") || line.contains("cambiar plan")
					|| line.contains("informacion general") || line.contains("detalles de la cuenta")
					|| line.contains("tus negocios") || line.contains("seccion legal")) {
				continue;
			}
			if (line.matches(".*[a-z]{3,}.*") && line.length() >= 4 && line.length() <= 80) {
				return true;
			}
		}
		return false;
	}

	private String normalized(final String text) {
		return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase();
	}

	private String getSetting(final String propertyKey, final String envKey) {
		return getSetting(propertyKey, envKey, null);
	}

	private String getSetting(final String propertyKey, final String envKey, final String defaultValue) {
		final String fromProperty = System.getProperty(propertyKey);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}
		final String fromEnv = System.getenv(envKey);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv.trim();
		}
		return defaultValue;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
