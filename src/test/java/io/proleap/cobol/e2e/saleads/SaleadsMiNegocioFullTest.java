package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import com.microsoft.playwright.options.LoadState;

public class SaleadsMiNegocioFullTest {

	private static final String LOGIN_FIELD = "Login";
	private static final String MI_NEGOCIO_MENU_FIELD = "Mi Negocio menu";
	private static final String AGREGAR_MODAL_FIELD = "Agregar Negocio modal";
	private static final String ADMINISTRAR_VIEW_FIELD = "Administrar Negocios view";
	private static final String INFO_GENERAL_FIELD = "Información General";
	private static final String DETALLES_CUENTA_FIELD = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS_FIELD = "Tus Negocios";
	private static final String TERMINOS_FIELD = "Términos y Condiciones";
	private static final String POLITICA_FIELD = "Política de Privacidad";

	private static final List<String> REPORT_FIELDS = Arrays.asList(LOGIN_FIELD, MI_NEGOCIO_MENU_FIELD, AGREGAR_MODAL_FIELD,
			ADMINISTRAR_VIEW_FIELD, INFO_GENERAL_FIELD, DETALLES_CUENTA_FIELD, TUS_NEGOCIOS_FIELD, TERMINOS_FIELD,
			POLITICA_FIELD);

	private static final Pattern ANY_EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern GOOGLE_ACCOUNT_PATTERN = Pattern.compile("juanlucasbarbiergarzon@gmail\\.com",
			Pattern.CASE_INSENSITIVE);

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final boolean enabled = Boolean.parseBoolean(readConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Set -Dsaleads.e2e.enabled=true (or SALEADS_E2E_ENABLED=true) to run this E2E workflow test.",
				enabled);

		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", null);
		final Path outputDir = createOutputDirectory();
		final Map<String, StepResult> results = initResults();

		Page appPage = null;

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions()
							.setHeadless(Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"))));
			final BrowserContext context = browser.newContext();
			final Page initialPage = context.newPage();

			if (isBlank(loginUrl)) {
				failStep(results, LOGIN_FIELD, "Missing login URL. Provide -Dsaleads.login.url or SALEADS_LOGIN_URL.");
				failDownstreamFrom(results, MI_NEGOCIO_MENU_FIELD,
						"Prerequisite failed: login URL not provided and login cannot be executed.");
			} else {
				initialPage.navigate(loginUrl);
				waitForUi(initialPage);
				appPage = runLoginStep(initialPage, context, outputDir, results);
			}

			if (isPass(results, LOGIN_FIELD)) {
				runMiNegocioMenuStep(appPage, outputDir, results);
			}

			if (isPass(results, MI_NEGOCIO_MENU_FIELD)) {
				runAgregarNegocioModalStep(appPage, outputDir, results);
				runAdministrarNegociosStep(appPage, outputDir, results);
			} else {
				failDownstreamFrom(results, AGREGAR_MODAL_FIELD, "Prerequisite failed: Mi Negocio menu step did not pass.");
			}

			if (isPass(results, ADMINISTRAR_VIEW_FIELD)) {
				runInformacionGeneralStep(appPage, results);
				runDetallesCuentaStep(appPage, results);
				runTusNegociosStep(appPage, results);
				runTerminosStep(appPage, context, outputDir, results);
				runPoliticaStep(appPage, context, outputDir, results);
			} else {
				failDownstreamFrom(results, INFO_GENERAL_FIELD,
						"Prerequisite failed: Administrar Negocios page was not validated.");
			}

			browser.close();
		} catch (Exception e) {
			failUnfinishedSteps(results, "Execution aborted: " + e.getMessage());
		}

		final Path jsonReport = outputDir.resolve("report.json");
		final Path markdownReport = outputDir.resolve("report.md");
		writeJsonReport(jsonReport, results);
		writeMarkdownReport(markdownReport, results);

		assertTrue(buildAssertionMessage(results, jsonReport, markdownReport), allPassed(results));
	}

	private Page runLoginStep(final Page initialPage, final BrowserContext context, final Path outputDir,
			final Map<String, StepResult> results) {
		try {
			final Locator loginButton = firstVisibleByText(initialPage, "sign\\s*in\\s*with\\s*google",
					"inicia\\s+sesi[oó]n\\s+con\\s+google", "^sign\\s*in$", "^inicia\\s+sesi[oó]n$", "google");
			if (loginButton == null) {
				failStep(results, LOGIN_FIELD, "Could not locate login trigger or 'Sign in with Google' button.");
				failDownstreamFrom(results, MI_NEGOCIO_MENU_FIELD, "Prerequisite failed: login step failed.");
				return null;
			}

			Page authPage = initialPage;
			try {
				authPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(7000),
						() -> clickAndWait(initialPage, loginButton));
				waitForUi(authPage);
			} catch (PlaywrightException popupTimeout) {
				waitForUi(initialPage);
				authPage = initialPage;
			}

			selectGoogleAccountIfPrompted(authPage);
			selectGoogleAccountIfPrompted(initialPage);

			final Page appPage = waitForMainApplicationPage(context);
			if (appPage == null) {
				failStep(results, LOGIN_FIELD,
						"Main application interface was not detected after Google login attempt (sidebar not visible).");
				failDownstreamFrom(results, MI_NEGOCIO_MENU_FIELD, "Prerequisite failed: login step failed.");
				return null;
			}

			final String dashboardShot = screenshot(appPage, outputDir, "step1_dashboard_loaded.png", false);
			passStep(results, LOGIN_FIELD, "Dashboard loaded and sidebar detected.", dashboardShot, null);
			return appPage;
		} catch (Exception e) {
			failStep(results, LOGIN_FIELD, "Login step failed: " + e.getMessage());
			failDownstreamFrom(results, MI_NEGOCIO_MENU_FIELD, "Prerequisite failed: login step failed.");
			return null;
		}
	}

	private void runMiNegocioMenuStep(final Page appPage, final Path outputDir, final Map<String, StepResult> results) {
		try {
			final Locator negocio = firstVisibleByText(appPage, "^Negocio$", "Negocio");
			if (negocio != null) {
				clickAndWait(appPage, negocio);
			}

			final Locator miNegocio = requireVisibleByText(appPage, "^Mi\\s+Negocio$", "Mi\\s+Negocio");
			clickAndWait(appPage, miNegocio);

			final boolean agregarVisible = isVisible(firstVisibleByText(appPage, "^Agregar\\s+Negocio$", "Agregar\\s+Negocio"));
			final boolean administrarVisible = isVisible(
					firstVisibleByText(appPage, "^Administrar\\s+Negocios$", "Administrar\\s+Negocios"));

			if (agregarVisible && administrarVisible) {
				final String screenshot = screenshot(appPage, outputDir, "step2_mi_negocio_menu_expanded.png", false);
				passStep(results, MI_NEGOCIO_MENU_FIELD, "Mi Negocio submenu expanded with required options visible.",
						screenshot, null);
			} else {
				failStep(results, MI_NEGOCIO_MENU_FIELD,
						"Mi Negocio submenu did not expose both 'Agregar Negocio' and 'Administrar Negocios'.");
			}
		} catch (Exception e) {
			failStep(results, MI_NEGOCIO_MENU_FIELD, "Mi Negocio menu step failed: " + e.getMessage());
		}
	}

	private void runAgregarNegocioModalStep(final Page appPage, final Path outputDir, final Map<String, StepResult> results) {
		try {
			final Locator agregarNegocio = requireVisibleByText(appPage, "^Agregar\\s+Negocio$", "Agregar\\s+Negocio");
			clickAndWait(appPage, agregarNegocio);

			final boolean titleVisible = isVisible(firstVisibleByText(appPage, "^Crear\\s+Nuevo\\s+Negocio$", "Crear\\s+Nuevo\\s+Negocio"));
			final Locator nombreInput = appPage.getByLabel(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE))
					.first();
			final boolean nombreInputVisible = isVisible(nombreInput)
					|| isVisible(appPage.getByPlaceholder(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE)).first());
			final boolean limitsTextVisible = isVisible(
					firstVisibleByText(appPage, "Tienes\\s+2\\s+de\\s+3\\s+negocios", "2\\s+de\\s+3\\s+negocios"));
			final boolean cancelVisible = isVisible(firstVisibleByText(appPage, "^Cancelar$"));
			final boolean createVisible = isVisible(firstVisibleByText(appPage, "^Crear\\s+Negocio$", "Crear\\s+Negocio"));

			final String screenshot = screenshot(appPage, outputDir, "step3_agregar_negocio_modal.png", false);

			if (titleVisible && nombreInputVisible && limitsTextVisible && cancelVisible && createVisible) {
				if (isVisible(nombreInput)) {
					nombreInput.click();
					nombreInput.fill("Negocio Prueba Automatización");
				}
				if (cancelVisible) {
					clickAndWait(appPage, firstVisibleByText(appPage, "^Cancelar$"));
				}
				passStep(results, AGREGAR_MODAL_FIELD, "Agregar Negocio modal validated successfully.", screenshot, null);
			} else {
				failStep(results, AGREGAR_MODAL_FIELD, "Agregar Negocio modal did not contain all required controls/text.");
			}
		} catch (Exception e) {
			failStep(results, AGREGAR_MODAL_FIELD, "Agregar Negocio modal step failed: " + e.getMessage());
		}
	}

	private void runAdministrarNegociosStep(final Page appPage, final Path outputDir, final Map<String, StepResult> results) {
		try {
			Locator administrar = firstVisibleByText(appPage, "^Administrar\\s+Negocios$", "Administrar\\s+Negocios");
			if (administrar == null) {
				final Locator miNegocio = firstVisibleByText(appPage, "^Mi\\s+Negocio$", "Mi\\s+Negocio");
				if (miNegocio != null) {
					clickAndWait(appPage, miNegocio);
				}
				administrar = firstVisibleByText(appPage, "^Administrar\\s+Negocios$", "Administrar\\s+Negocios");
			}

			if (administrar == null) {
				failStep(results, ADMINISTRAR_VIEW_FIELD, "Could not locate 'Administrar Negocios' option.");
				return;
			}

			clickAndWait(appPage, administrar);

			final boolean infoGeneral = isVisible(firstVisibleByText(appPage, "Informaci[oó]n\\s+General"));
			final boolean detallesCuenta = isVisible(firstVisibleByText(appPage, "Detalles\\s+de\\s+la\\s+Cuenta"));
			final boolean tusNegocios = isVisible(firstVisibleByText(appPage, "Tus\\s+Negocios"));
			final boolean seccionLegal = isVisible(firstVisibleByText(appPage, "Secci[oó]n\\s+Legal"));

			final String screenshot = screenshot(appPage, outputDir, "step4_administrar_negocios_full.png", true);

			if (infoGeneral && detallesCuenta && tusNegocios && seccionLegal) {
				passStep(results, ADMINISTRAR_VIEW_FIELD, "Administrar Negocios page loaded with required sections.",
						screenshot, null);
			} else {
				failStep(results, ADMINISTRAR_VIEW_FIELD,
						"Administrar Negocios page is missing one or more required sections.");
			}
		} catch (Exception e) {
			failStep(results, ADMINISTRAR_VIEW_FIELD, "Administrar Negocios step failed: " + e.getMessage());
		}
	}

	private void runInformacionGeneralStep(final Page appPage, final Map<String, StepResult> results) {
		try {
			final String bodyText = appPage.locator("body").innerText();
			final boolean hasAnyEmail = ANY_EMAIL_PATTERN.matcher(bodyText).find();
			final boolean hasBusinessPlan = isVisible(firstVisibleByText(appPage, "BUSINESS\\s+PLAN"));
			final boolean hasCambiarPlan = isVisible(firstVisibleByText(appPage, "Cambiar\\s+Plan"));
			final boolean hasUserNameSignal = isVisible(firstVisibleByText(appPage, "Nombre", "Usuario", "Perfil"))
					|| hasAnyEmail;

			if (hasUserNameSignal && hasAnyEmail && hasBusinessPlan && hasCambiarPlan) {
				passStep(results, INFO_GENERAL_FIELD, "Información General section validated.", null, null);
			} else {
				failStep(results, INFO_GENERAL_FIELD,
						"Información General is missing user identity, plan label, or 'Cambiar Plan' button.");
			}
		} catch (Exception e) {
			failStep(results, INFO_GENERAL_FIELD, "Información General validation failed: " + e.getMessage());
		}
	}

	private void runDetallesCuentaStep(final Page appPage, final Map<String, StepResult> results) {
		try {
			final boolean cuentaCreada = isVisible(firstVisibleByText(appPage, "Cuenta\\s+creada"));
			final boolean estadoActivo = isVisible(firstVisibleByText(appPage, "Estado\\s+activo"));
			final boolean idiomaSeleccionado = isVisible(firstVisibleByText(appPage, "Idioma\\s+seleccionado"));

			if (cuentaCreada && estadoActivo && idiomaSeleccionado) {
				passStep(results, DETALLES_CUENTA_FIELD, "Detalles de la Cuenta section validated.", null, null);
			} else {
				failStep(results, DETALLES_CUENTA_FIELD,
						"Detalles de la Cuenta is missing 'Cuenta creada', 'Estado activo', or 'Idioma seleccionado'.");
			}
		} catch (Exception e) {
			failStep(results, DETALLES_CUENTA_FIELD, "Detalles de la Cuenta validation failed: " + e.getMessage());
		}
	}

	private void runTusNegociosStep(final Page appPage, final Map<String, StepResult> results) {
		try {
			final boolean headingVisible = isVisible(firstVisibleByText(appPage, "Tus\\s+Negocios"));
			final boolean addButtonVisible = isVisible(firstVisibleByText(appPage, "^Agregar\\s+Negocio$", "Agregar\\s+Negocio"));
			final boolean limitsVisible = isVisible(
					firstVisibleByText(appPage, "Tienes\\s+2\\s+de\\s+3\\s+negocios", "2\\s+de\\s+3\\s+negocios"));

			if (headingVisible && addButtonVisible && limitsVisible) {
				passStep(results, TUS_NEGOCIOS_FIELD, "Tus Negocios section validated.", null, null);
			} else {
				failStep(results, TUS_NEGOCIOS_FIELD,
						"Tus Negocios section is missing the business list heading, add button, or limits text.");
			}
		} catch (Exception e) {
			failStep(results, TUS_NEGOCIOS_FIELD, "Tus Negocios validation failed: " + e.getMessage());
		}
	}

	private void runTerminosStep(final Page appPage, final BrowserContext context, final Path outputDir,
			final Map<String, StepResult> results) {
		try {
			final LegalCheckResult legalResult = validateLegalPage(appPage, context, outputDir, "T[ée]rminos\\s+y\\s+Condiciones",
					"T[ée]rminos\\s+y\\s+Condiciones", "step8_terminos_y_condiciones.png");

			if (legalResult.passed) {
				passStep(results, TERMINOS_FIELD, "Términos y Condiciones page validated.", legalResult.screenshotPath,
						legalResult.finalUrl);
			} else {
				failStep(results, TERMINOS_FIELD,
						"Términos y Condiciones validation failed. Final URL: " + legalResult.finalUrl);
			}
		} catch (Exception e) {
			failStep(results, TERMINOS_FIELD, "Términos y Condiciones step failed: " + e.getMessage());
		}
	}

	private void runPoliticaStep(final Page appPage, final BrowserContext context, final Path outputDir,
			final Map<String, StepResult> results) {
		try {
			final LegalCheckResult legalResult = validateLegalPage(appPage, context, outputDir, "Pol[ií]tica\\s+de\\s+Privacidad",
					"Pol[ií]tica\\s+de\\s+Privacidad", "step9_politica_de_privacidad.png");

			if (legalResult.passed) {
				passStep(results, POLITICA_FIELD, "Política de Privacidad page validated.", legalResult.screenshotPath,
						legalResult.finalUrl);
			} else {
				failStep(results, POLITICA_FIELD, "Política de Privacidad validation failed. Final URL: " + legalResult.finalUrl);
			}
		} catch (Exception e) {
			failStep(results, POLITICA_FIELD, "Política de Privacidad step failed: " + e.getMessage());
		}
	}

	private LegalCheckResult validateLegalPage(final Page appPage, final BrowserContext context, final Path outputDir,
			final String linkTextRegex, final String headingRegex, final String screenshotName) {
		Page legalPage = appPage;
		boolean openedNewTab = false;
		final String appUrlBeforeClick = appPage.url();
		String finalUrl = appUrlBeforeClick;

		try {
			final Locator link = requireVisibleByText(appPage, linkTextRegex);
			try {
				legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(7000),
						() -> clickAndWait(appPage, link));
				openedNewTab = true;
			} catch (PlaywrightException popupTimeout) {
				waitForUi(appPage);
				legalPage = appPage;
			}

			waitForUi(legalPage);
			final boolean headingVisible = isVisible(firstVisibleByText(legalPage, headingRegex));
			final String body = legalPage.locator("body").innerText();
			final boolean legalContentVisible = body != null && body.trim().length() > 200;

			final String screenshotPath = screenshot(legalPage, outputDir, screenshotName, true);
			finalUrl = legalPage.url();

			if (openedNewTab && legalPage != appPage) {
				legalPage.close();
				appPage.bringToFront();
			} else if (!safeEquals(appUrlBeforeClick, appPage.url())) {
				appPage.goBack();
				waitForUi(appPage);
			}

			return new LegalCheckResult(headingVisible && legalContentVisible, screenshotPath, finalUrl);
		} catch (Exception e) {
			return new LegalCheckResult(false, null, finalUrl + " (error: " + e.getMessage() + ")");
		}
	}

	private void selectGoogleAccountIfPrompted(final Page page) {
		try {
			waitForUi(page);
			final Locator accountOption = page.getByText(GOOGLE_ACCOUNT_PATTERN).first();
			if (isVisible(accountOption)) {
				clickAndWait(page, accountOption);
			}
		} catch (Exception ignored) {
			// Account selector is optional and depends on the current Google session state.
		}
	}

	private Page waitForMainApplicationPage(final BrowserContext context) {
		for (int attempt = 0; attempt < 30; attempt++) {
			for (final Page candidate : context.pages()) {
				try {
					waitForUi(candidate);
					final boolean sidebarVisible = isVisible(candidate.locator("aside").first())
							|| isVisible(candidate.locator("nav").first());
					final boolean negocioVisible = isVisible(firstVisibleByText(candidate, "Mi\\s+Negocio", "Negocio"));
					if (sidebarVisible && negocioVisible) {
						return candidate;
					}
				} catch (Exception ignored) {
					// Continue looking at remaining tabs/pages.
				}
			}

			try {
				context.pages().get(0).waitForTimeout(1000);
			} catch (Exception ignored) {
				// Ignore and continue polling.
			}
		}

		return null;
	}

	private Locator requireVisibleByText(final Page page, final String... regexes) {
		final Locator locator = firstVisibleByText(page, regexes);
		if (locator == null) {
			throw new IllegalStateException("Could not find visible element by text: " + Arrays.toString(regexes));
		}
		return locator;
	}

	private Locator firstVisibleByText(final Page page, final String... regexes) {
		for (final String regex : regexes) {
			final Locator candidate = page.getByText(Pattern.compile(regex, Pattern.CASE_INSENSITIVE)).first();
			if (isVisible(candidate)) {
				return candidate;
			}
		}

		return null;
	}

	private boolean isVisible(final Locator locator) {
		if (locator == null) {
			return false;
		}

		try {
			return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(1500));
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout(10000));
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
			// Some screens keep open connections, so network-idle may not happen.
		}
		page.waitForTimeout(500);
	}

	private String screenshot(final Page page, final Path outputDir, final String fileName, final boolean fullPage) {
		try {
			final Path shotPath = outputDir.resolve(fileName);
			page.screenshot(new Page.ScreenshotOptions().setPath(shotPath).setFullPage(fullPage));
			return shotPath.toAbsolutePath().toString();
		} catch (Exception e) {
			return null;
		}
	}

	private Path createOutputDirectory() throws IOException {
		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").format(OffsetDateTime.now(ZoneOffset.UTC));
		final Path outputDir = Paths.get("target", "saleads-mi-negocio", runId);
		Files.createDirectories(outputDir);
		return outputDir;
	}

	private Map<String, StepResult> initResults() {
		final Map<String, StepResult> results = new LinkedHashMap<>();
		for (final String field : REPORT_FIELDS) {
			results.put(field, new StepResult("FAIL", "Not executed."));
		}
		return results;
	}

	private void passStep(final Map<String, StepResult> results, final String field, final String details,
			final String screenshotPath, final String finalUrl) {
		final StepResult stepResult = results.get(field);
		stepResult.status = "PASS";
		stepResult.details = details;
		if (!isBlank(screenshotPath)) {
			stepResult.screenshots.add(screenshotPath);
		}
		if (!isBlank(finalUrl)) {
			stepResult.finalUrl = finalUrl;
		}
	}

	private void failStep(final Map<String, StepResult> results, final String field, final String details) {
		final StepResult stepResult = results.get(field);
		stepResult.status = "FAIL";
		stepResult.details = details;
	}

	private void failDownstreamFrom(final Map<String, StepResult> results, final String startField, final String reason) {
		final int startIndex = REPORT_FIELDS.indexOf(startField);
		if (startIndex < 0) {
			return;
		}

		for (int i = startIndex; i < REPORT_FIELDS.size(); i++) {
			final String field = REPORT_FIELDS.get(i);
			if (!isPass(results, field)) {
				failStep(results, field, reason);
			}
		}
	}

	private void failUnfinishedSteps(final Map<String, StepResult> results, final String reason) {
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			final StepResult value = entry.getValue();
			if ("Not executed.".equals(value.details)) {
				value.status = "FAIL";
				value.details = reason;
			}
		}
	}

	private boolean isPass(final Map<String, StepResult> results, final String field) {
		return "PASS".equals(results.get(field).status);
	}

	private boolean allPassed(final Map<String, StepResult> results) {
		for (final StepResult result : results.values()) {
			if (!"PASS".equals(result.status)) {
				return false;
			}
		}

		return true;
	}

	private String readConfig(final String systemProperty, final String environmentVariable, final String defaultValue) {
		final String propertyValue = System.getProperty(systemProperty);
		if (!isBlank(propertyValue)) {
			return propertyValue;
		}

		final String envValue = System.getenv(environmentVariable);
		if (!isBlank(envValue)) {
			return envValue;
		}

		return defaultValue;
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private boolean safeEquals(final String left, final String right) {
		if (left == null) {
			return right == null;
		}
		return left.equals(right);
	}

	private void writeJsonReport(final Path jsonReport, final Map<String, StepResult> results) throws IOException {
		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"workflow\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"generated_at_utc\": \"").append(escapeJson(OffsetDateTime.now(ZoneOffset.UTC).toString())).append("\",\n");
		json.append("  \"results\": {\n");

		for (int i = 0; i < REPORT_FIELDS.size(); i++) {
			final String field = REPORT_FIELDS.get(i);
			final StepResult result = results.get(field);
			json.append("    \"").append(escapeJson(field)).append("\": {\n");
			json.append("      \"status\": \"").append(escapeJson(result.status)).append("\",\n");
			json.append("      \"details\": \"").append(escapeJson(result.details)).append("\",\n");
			json.append("      \"screenshots\": [");
			for (int s = 0; s < result.screenshots.size(); s++) {
				if (s > 0) {
					json.append(", ");
				}
				json.append("\"").append(escapeJson(result.screenshots.get(s))).append("\"");
			}
			json.append("],\n");
			json.append("      \"final_url\": ");
			if (result.finalUrl == null) {
				json.append("null\n");
			} else {
				json.append("\"").append(escapeJson(result.finalUrl)).append("\"\n");
			}
			json.append("    }");
			if (i < REPORT_FIELDS.size() - 1) {
				json.append(",");
			}
			json.append("\n");
		}

		json.append("  }\n");
		json.append("}\n");
		Files.writeString(jsonReport, json.toString(), StandardCharsets.UTF_8);
	}

	private void writeMarkdownReport(final Path markdownReport, final Map<String, StepResult> results) throws IOException {
		final StringBuilder markdown = new StringBuilder();
		markdown.append("# SaleADS Mi Negocio Full Test Report\n\n");
		markdown.append("- Generated at (UTC): ").append(OffsetDateTime.now(ZoneOffset.UTC)).append("\n");
		markdown.append("- Test name: saleads_mi_negocio_full_test\n\n");
		markdown.append("| Field | Status | Details | Final URL |\n");
		markdown.append("| --- | --- | --- | --- |\n");

		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.get(field);
			markdown.append("| ").append(field).append(" | ").append(result.status).append(" | ")
					.append(result.details.replace("|", "\\|")).append(" | ")
					.append(result.finalUrl == null ? "-" : result.finalUrl.replace("|", "\\|")).append(" |\n");
		}

		markdown.append("\n## Screenshots\n\n");
		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.get(field);
			if (!result.screenshots.isEmpty()) {
				markdown.append("- **").append(field).append("**\n");
				for (final String screenshot : result.screenshots) {
					markdown.append("  - ").append(screenshot).append("\n");
				}
			}
		}

		Files.writeString(markdownReport, markdown.toString(), StandardCharsets.UTF_8);
	}

	private String buildAssertionMessage(final Map<String, StepResult> results, final Path jsonReport, final Path markdownReport) {
		final StringBuilder message = new StringBuilder();
		message.append("One or more workflow validations failed.\n");
		message.append("JSON report: ").append(jsonReport.toAbsolutePath()).append("\n");
		message.append("Markdown report: ").append(markdownReport.toAbsolutePath()).append("\n");
		message.append("Summary:\n");
		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.get(field);
			message.append("- ").append(field).append(": ").append(result.status).append(" (").append(result.details).append(")\n");
		}
		return message.toString();
	}

	private String escapeJson(final String value) {
		if (value == null) {
			return "";
		}

		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	private static final class StepResult {
		private String status;
		private String details;
		private final List<String> screenshots = new ArrayList<>();
		private String finalUrl;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details;
		}
	}

	private static final class LegalCheckResult {
		private final boolean passed;
		private final String screenshotPath;
		private final String finalUrl;

		private LegalCheckResult(final boolean passed, final String screenshotPath, final String finalUrl) {
			this.passed = passed;
			this.screenshotPath = screenshotPath;
			this.finalUrl = finalUrl;
		}
	}

}
