package io.proleap.cobol.e2e.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
			.withZone(ZoneOffset.UTC);
	private static final int UI_TIMEOUT_MS = 15000;

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	@Test
	public void saleadsMiNegocioWorkflow() throws Exception {
		Assume.assumeTrue(
				"Set SALEADS_E2E_ENABLED=true (or -Dsaleads.e2e.enabled=true) to run this external browser workflow.",
				isTruthy(firstNonBlank(System.getenv("SALEADS_E2E_ENABLED"), System.getProperty("saleads.e2e.enabled"))));

		final LinkedHashMap<String, StepResult> results = initResults();
		final Path runDir = createRunDirectory();
		final Path screenshotsDir = runDir.resolve("screenshots");
		Files.createDirectories(screenshotsDir);

		final String loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getProperty("saleads.login.url"));
		boolean allPassed = false;

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(isTruthy(
							firstNonBlank(System.getenv("SALEADS_HEADLESS"), System.getProperty("saleads.headless"), "true"))));
			try (BrowserContext context = browser.newContext()) {
				final Page appPage = context.newPage();

				if (isBlank(loginUrl)) {
					failStep(results.get("Login"),
							"Missing SALEADS_LOGIN_URL (or -Dsaleads.login.url). This test is environment-agnostic and does not hardcode domains.");
					markPrerequisiteFailures(results, "Mi Negocio menu", "Login did not complete.");
					captureScreenshot(appPage, screenshotsDir, "step1_login_missing_url", false, results.get("Login"));
					throw new AssertionError("Missing SaleADS login URL input.");
				}

				appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
				waitForUiLoad(appPage);
				captureScreenshot(appPage, screenshotsDir, "step0_login_page", true, null);

				final boolean loginOk = runLoginStep(context, appPage, screenshotsDir, results.get("Login"));
				if (!loginOk) {
					markPrerequisiteFailures(results, "Mi Negocio menu", "Login failed.");
					throw new AssertionError("Login step failed.");
				}

				final boolean menuOk = runMiNegocioMenuStep(appPage, screenshotsDir, results.get("Mi Negocio menu"));
				if (!menuOk) {
					markPrerequisiteFailures(results, "Agregar Negocio modal", "Mi Negocio menu validation failed.");
					throw new AssertionError("Mi Negocio menu step failed.");
				}

				final boolean agregarModalOk = runAgregarNegocioModalStep(appPage, screenshotsDir,
						results.get("Agregar Negocio modal"));
				if (!agregarModalOk) {
					markPrerequisiteFailures(results, "Administrar Negocios view", "Agregar Negocio modal validation failed.");
					throw new AssertionError("Agregar Negocio modal step failed.");
				}

				final boolean administrarOk = runAdministrarNegociosStep(appPage, screenshotsDir,
						results.get("Administrar Negocios view"));
				if (!administrarOk) {
					markPrerequisiteFailures(results, "Información General", "Administrar Negocios view validation failed.");
					throw new AssertionError("Administrar Negocios view step failed.");
				}

				final boolean infoOk = runInformacionGeneralStep(appPage, results.get("Información General"));
				if (!infoOk) {
					markPrerequisiteFailures(results, "Detalles de la Cuenta", "Información General validation failed.");
					throw new AssertionError("Información General step failed.");
				}

				final boolean detallesOk = runDetallesCuentaStep(appPage, results.get("Detalles de la Cuenta"));
				if (!detallesOk) {
					markPrerequisiteFailures(results, "Tus Negocios", "Detalles de la Cuenta validation failed.");
					throw new AssertionError("Detalles de la Cuenta step failed.");
				}

				final boolean negociosOk = runTusNegociosStep(appPage, results.get("Tus Negocios"));
				if (!negociosOk) {
					markPrerequisiteFailures(results, "Términos y Condiciones", "Tus Negocios validation failed.");
					throw new AssertionError("Tus Negocios step failed.");
				}

				final boolean terminosOk = runLegalLinkStep(context, appPage, screenshotsDir, "Términos y Condiciones",
						"(?iu)T[eé]rminos\\s+y\\s+Condiciones", results.get("Términos y Condiciones"));
				if (!terminosOk) {
					markPrerequisiteFailures(results, "Política de Privacidad",
							"Términos y Condiciones validation failed.");
					throw new AssertionError("Términos y Condiciones step failed.");
				}

				runLegalLinkStep(context, appPage, screenshotsDir, "Política de Privacidad",
						"(?iu)Pol[ií]tica\\s+de\\s+Privacidad", results.get("Política de Privacidad"));
			}
		} finally {
			allPassed = allStepsPassed(results);
			writeReports(runDir, results);
		}

		Assert.assertTrue("One or more Mi Negocio validations failed. See report artifacts for details.", allPassed);
	}

	private LinkedHashMap<String, StepResult> initResults() {
		final LinkedHashMap<String, StepResult> results = new LinkedHashMap<>();
		for (final String field : REPORT_FIELDS) {
			results.put(field, new StepResult(field));
		}
		return results;
	}

	private boolean runLoginStep(final BrowserContext context, final Page page, final Path screenshotsDir,
			final StepResult result) {
		try {
			if (hasVisibleText(page, "(?i)^\\s*GOOGLE\\s*$")) {
				clickFirstVisibleText(page, "(?i)^\\s*GOOGLE\\s*$");
			} else {
				clickFirstVisibleText(page, "(?iu)Sign\\s*in\\s*with\\s*Google",
						"(?iu)Inicia\\s+sesi[oó]n\\s+con\\s+Google", "(?iu)^\\s*Google\\s*$", "(?iu)Sign\\s*in",
						"(?iu)Iniciar\\s*sesi[oó]n", "(?iu)Inicia\\s+sesi[oó]n", "(?iu)Login|Log\\s*in",
						"(?iu)Acceder|Entrar");
				if (hasVisibleText(page, "(?i)^\\s*GOOGLE\\s*$")) {
					clickFirstVisibleText(page, "(?i)^\\s*GOOGLE\\s*$");
				}
			}

			// Optional Google account picker interaction.
			for (final Page anyPage : context.pages()) {
				if (hasVisibleText(anyPage, Pattern.quote(GOOGLE_ACCOUNT))) {
					clickFirstVisibleText(anyPage, Pattern.quote(GOOGLE_ACCOUNT));
					break;
				}
			}

			waitForUiLoad(page);

			final boolean appInterfaceVisible = !containsAny(page.url(), "accounts.google.com", "keycloak")
					&& (hasVisibleText(page, "(?iu)Mi\\s+Negocio|Negocio|Dashboard|Panel")
							|| isVisible(page.locator("aside")) || isVisible(page.locator("nav")));
			final boolean sidebarVisible = isVisible(page.locator("aside"))
					|| (isVisible(page.locator("nav")) && hasVisibleText(page, "(?iu)Negocio|Mi\\s+Negocio"));

			captureScreenshot(page, screenshotsDir, "step1_dashboard", true, result);

			if (!appInterfaceVisible || !sidebarVisible) {
				failStep(result, "Main application interface and left sidebar were not both detected after login.");
				return false;
			}

			passStep(result, "Main application interface and left sidebar are visible.");
			return true;
		} catch (final Exception e) {
			captureScreenshot(page, screenshotsDir, "step1_login_failed", false, result);
			failStep(result, "Login with Google failed: " + safeMessage(e));
			return false;
		}
	}

	private boolean runMiNegocioMenuStep(final Page page, final Path screenshotsDir, final StepResult result) {
		try {
			clickIfVisibleText(page, "(?iu)^\\s*Negocio\\s*$");
			clickFirstVisibleText(page, "(?iu)Mi\\s+Negocio");

			final boolean agregarVisible = hasVisibleText(page, "(?iu)Agregar\\s+Negocio");
			final boolean administrarVisible = hasVisibleText(page, "(?iu)Administrar\\s+Negocios");

			captureScreenshot(page, screenshotsDir, "step2_mi_negocio_menu", true, result);

			if (!agregarVisible || !administrarVisible) {
				failStep(result, "Mi Negocio submenu did not show both 'Agregar Negocio' and 'Administrar Negocios'.");
				return false;
			}

			passStep(result, "Mi Negocio submenu expanded and both submenu options are visible.");
			return true;
		} catch (final Exception e) {
			captureScreenshot(page, screenshotsDir, "step2_mi_negocio_menu_failed", false, result);
			failStep(result, "Mi Negocio menu validation failed: " + safeMessage(e));
			return false;
		}
	}

	private boolean runAgregarNegocioModalStep(final Page page, final Path screenshotsDir, final StepResult result) {
		try {
			clickFirstVisibleText(page, "(?iu)Agregar\\s+Negocio");

			final boolean titleVisible = hasVisibleText(page, "(?iu)Crear\\s+Nuevo\\s+Negocio");
			final boolean inputVisible = isVisible(page.getByLabel(regex("(?iu)Nombre\\s+del\\s+Negocio")));
			final boolean quotaTextVisible = hasVisibleText(page, "(?iu)Tienes\\s+2\\s+de\\s+3\\s+negocios");
			final boolean cancelarVisible = hasVisibleText(page, "(?iu)Cancelar");
			final boolean crearVisible = hasVisibleText(page, "(?iu)Crear\\s+Negocio");

			if (inputVisible) {
				page.getByLabel(regex("(?iu)Nombre\\s+del\\s+Negocio")).fill("Negocio Prueba Automatización");
				waitForUiLoad(page);
			}
			clickIfVisibleText(page, "(?iu)Cancelar");

			captureScreenshot(page, screenshotsDir, "step3_agregar_negocio_modal", true, result);

			if (!(titleVisible && inputVisible && quotaTextVisible && cancelarVisible && crearVisible)) {
				failStep(result,
						"Agregar Negocio modal did not expose all required controls/texts (title, name field, quota, buttons).");
				return false;
			}

			passStep(result, "Agregar Negocio modal contains all required UI elements.");
			return true;
		} catch (final Exception e) {
			captureScreenshot(page, screenshotsDir, "step3_agregar_negocio_modal_failed", false, result);
			failStep(result, "Agregar Negocio modal validation failed: " + safeMessage(e));
			return false;
		}
	}

	private boolean runAdministrarNegociosStep(final Page page, final Path screenshotsDir, final StepResult result) {
		try {
			clickIfVisibleText(page, "(?iu)Mi\\s+Negocio");
			clickFirstVisibleText(page, "(?iu)Administrar\\s+Negocios");

			final boolean infoGeneral = hasVisibleText(page, "(?iu)Informaci[oó]n\\s+General");
			final boolean detallesCuenta = hasVisibleText(page, "(?iu)Detalles\\s+de\\s+la\\s+Cuenta");
			final boolean tusNegocios = hasVisibleText(page, "(?iu)Tus\\s+Negocios");
			final boolean legalSection = hasVisibleText(page, "(?iu)Secci[oó]n\\s+Legal");

			captureScreenshot(page, screenshotsDir, "step4_administrar_negocios", true, result);

			if (!(infoGeneral && detallesCuenta && tusNegocios && legalSection)) {
				failStep(result, "Administrar Negocios page is missing one or more required sections.");
				return false;
			}

			passStep(result, "Administrar Negocios page contains all expected sections.");
			return true;
		} catch (final Exception e) {
			captureScreenshot(page, screenshotsDir, "step4_administrar_negocios_failed", true, result);
			failStep(result, "Administrar Negocios view validation failed: " + safeMessage(e));
			return false;
		}
	}

	private boolean runInformacionGeneralStep(final Page page, final StepResult result) {
		try {
			final boolean userNameVisible = hasAnyVisibleText(page, "(?iu)Nombre\\s*:|Usuario|Perfil");
			final boolean userEmailVisible = hasVisibleText(page, "(?iu)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
			final boolean businessPlanVisible = hasVisibleText(page, "(?iu)BUSINESS\\s+PLAN");
			final boolean cambiarPlanVisible = hasVisibleText(page, "(?iu)Cambiar\\s+Plan");

			if (!(userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible)) {
				failStep(result, "Información General section missing one or more required fields.");
				return false;
			}

			passStep(result, "Información General section validated successfully.");
			return true;
		} catch (final Exception e) {
			failStep(result, "Información General validation failed: " + safeMessage(e));
			return false;
		}
	}

	private boolean runDetallesCuentaStep(final Page page, final StepResult result) {
		try {
			final boolean cuentaCreada = hasVisibleText(page, "(?iu)Cuenta\\s+creada");
			final boolean estadoActivo = hasVisibleText(page, "(?iu)Estado\\s+activo");
			final boolean idiomaSeleccionado = hasVisibleText(page, "(?iu)Idioma\\s+seleccionado");

			if (!(cuentaCreada && estadoActivo && idiomaSeleccionado)) {
				failStep(result, "Detalles de la Cuenta section missing one or more required labels.");
				return false;
			}

			passStep(result, "Detalles de la Cuenta section validated successfully.");
			return true;
		} catch (final Exception e) {
			failStep(result, "Detalles de la Cuenta validation failed: " + safeMessage(e));
			return false;
		}
	}

	private boolean runTusNegociosStep(final Page page, final StepResult result) {
		try {
			final boolean businessListVisible = hasVisibleText(page, "(?iu)Tus\\s+Negocios");
			final boolean agregarNegocioButton = hasVisibleText(page, "(?iu)Agregar\\s+Negocio");
			final boolean quotaTextVisible = hasVisibleText(page, "(?iu)Tienes\\s+2\\s+de\\s+3\\s+negocios");

			if (!(businessListVisible && agregarNegocioButton && quotaTextVisible)) {
				failStep(result, "Tus Negocios section missing one or more required elements.");
				return false;
			}

			passStep(result, "Tus Negocios section validated successfully.");
			return true;
		} catch (final Exception e) {
			failStep(result, "Tus Negocios validation failed: " + safeMessage(e));
			return false;
		}
	}

	private boolean runLegalLinkStep(final BrowserContext context, final Page appPage, final Path screenshotsDir,
			final String stepLabel, final String headingRegex, final StepResult result) {
		final String urlBeforeClick = appPage.url();
		Page legalPage = null;
		boolean openedNewTab = false;

		try {
			try {
				legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(5000),
						() -> clickFirstVisibleText(appPage, "(?iu)" + Pattern.quote(stepLabel)));
				openedNewTab = true;
			} catch (final PlaywrightException popupException) {
				// Same-tab navigation is valid per workflow rules.
				legalPage = appPage;
				waitForUiLoad(legalPage);
			}

			waitForUiLoad(legalPage);

			final boolean headingVisible = hasVisibleText(legalPage, headingRegex);
			final String bodyText = legalPage.locator("body").innerText();
			final boolean legalContentVisible = bodyText != null && bodyText.trim().length() > 120;

			captureScreenshot(legalPage, screenshotsDir,
					(stepLabel.equals("Términos y Condiciones") ? "step8_terminos" : "step9_politica"), true, result);
			result.finalUrl = legalPage.url();

			if (!(headingVisible && legalContentVisible)) {
				failStep(result, stepLabel + " page did not show required heading/content.");
				return false;
			}

			passStep(result, stepLabel + " page validated with visible heading and legal text.");
			return true;
		} catch (final Exception e) {
			captureScreenshot(appPage, screenshotsDir,
					(stepLabel.equals("Términos y Condiciones") ? "step8_terminos_failed" : "step9_politica_failed"),
					true, result);
			failStep(result, stepLabel + " validation failed: " + safeMessage(e));
			return false;
		} finally {
			try {
				if (openedNewTab && legalPage != null && legalPage != appPage) {
					legalPage.close();
					appPage.bringToFront();
				} else if (!sameUrlIgnoringHash(appPage.url(), urlBeforeClick)) {
					appPage.navigate(urlBeforeClick, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
					waitForUiLoad(appPage);
				}
			} catch (final Exception ignored) {
				// Cleanup should not hide the validation result.
			}
		}
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(UI_TIMEOUT_MS));
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(UI_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// Some pages continuously poll; DOM content loaded is enough fallback.
		}
	}

	private void clickFirstVisibleText(final Page page, final String... regexes) {
		for (final String regex : regexes) {
			final Locator locator = page.getByText(regex(regex));
			if (isVisible(locator)) {
				locator.first().click();
				waitForUiLoad(page);
				return;
			}
		}
		throw new IllegalStateException("No clickable element found for text patterns: " + Arrays.toString(regexes));
	}

	private void clickIfVisibleText(final Page page, final String regex) {
		final Locator locator = page.getByText(regex(regex));
		if (isVisible(locator)) {
			locator.first().click();
			waitForUiLoad(page);
		}
	}

	private boolean hasVisibleText(final Page page, final String regexValue) {
		return isVisible(page.getByText(regex(regexValue)));
	}

	private boolean hasAnyVisibleText(final Page page, final String... regexes) {
		for (final String regexValue : regexes) {
			if (hasVisibleText(page, regexValue)) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.count() > 0 && locator.first().isVisible();
		} catch (final Exception e) {
			return false;
		}
	}

	private Path captureScreenshot(final Page page, final Path screenshotsDir, final String name, final boolean fullPage,
			final StepResult stepResult) {
		try {
			final Path screenshotPath = screenshotsDir.resolve(name + ".png");
			page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
			if (stepResult != null) {
				stepResult.screenshot = screenshotPath.toString();
			}
			return screenshotPath;
		} catch (final Exception ignored) {
			return null;
		}
	}

	private void markPrerequisiteFailures(final LinkedHashMap<String, StepResult> results, final String firstStepToMark,
			final String reason) {
		boolean mark = false;
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			if (entry.getKey().equals(firstStepToMark)) {
				mark = true;
			}
			if (mark && "FAIL".equals(entry.getValue().status) && "Not executed.".equals(entry.getValue().details)) {
				failStep(entry.getValue(), "Prerequisite failed: " + reason);
			}
		}
	}

	private boolean allStepsPassed(final LinkedHashMap<String, StepResult> results) {
		for (final StepResult result : results.values()) {
			if (!"PASS".equals(result.status)) {
				return false;
			}
		}
		return true;
	}

	private Path createRunDirectory() throws IOException {
		final String baseOutputPath = firstNonBlank(System.getenv("SALEADS_OUTPUT_DIR"),
				System.getProperty("saleads.output.dir"), "/tmp");
		final String runId = RUN_ID_FORMATTER.format(Instant.now());
		final Path runDir = Paths.get(baseOutputPath).resolve("saleads-mi-negocio-" + runId);
		Files.createDirectories(runDir);
		return runDir;
	}

	private void writeReports(final Path runDir, final LinkedHashMap<String, StepResult> results) throws IOException {
		final Instant now = Instant.now();
		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"name\": \"").append(TEST_NAME).append("\",\n");
		json.append("  \"generatedAt\": \"").append(now).append("\",\n");
		json.append("  \"overall\": \"").append(allStepsPassed(results) ? "PASS" : "FAIL").append("\",\n");
		json.append("  \"results\": [\n");

		int idx = 0;
		for (final StepResult result : results.values()) {
			json.append("    {\n");
			json.append("      \"field\": \"").append(escapeJson(result.field)).append("\",\n");
			json.append("      \"status\": \"").append(result.status).append("\",\n");
			json.append("      \"details\": \"").append(escapeJson(result.details)).append("\",\n");
			json.append("      \"screenshot\": \"").append(escapeJson(result.screenshot)).append("\",\n");
			json.append("      \"finalUrl\": \"").append(escapeJson(result.finalUrl)).append("\"\n");
			json.append("    }");
			if (idx < results.size() - 1) {
				json.append(",");
			}
			json.append("\n");
			idx++;
		}
		json.append("  ]\n");
		json.append("}\n");

		Files.writeString(runDir.resolve("report.json"), json.toString(), StandardCharsets.UTF_8);

		final StringBuilder md = new StringBuilder();
		md.append("# SaleADS Mi Negocio Workflow Report\n\n");
		md.append("- Test: `").append(TEST_NAME).append("`\n");
		md.append("- Generated at (UTC): `").append(now).append("`\n");
		md.append("- Overall: **").append(allStepsPassed(results) ? "PASS" : "FAIL").append("**\n\n");
		md.append("| Field | Status | Details | Final URL |\n");
		md.append("|---|---|---|---|\n");
		for (final StepResult result : results.values()) {
			md.append("| ").append(result.field).append(" | ").append(result.status).append(" | ")
					.append(escapeMarkdown(result.details)).append(" | ").append(escapeMarkdown(result.finalUrl))
					.append(" |\n");
		}

		Files.writeString(runDir.resolve("report.md"), md.toString(), StandardCharsets.UTF_8);
	}

	private String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	private String escapeMarkdown(final String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return value.replace("|", "\\|").replace("\n", "<br/>");
	}

	private static Pattern regex(final String regexValue) {
		final String normalized = regexValue.replace("(?iu)", "(?i)").replace("(?ui)", "(?i)");
		return Pattern.compile(normalized, Pattern.CASE_INSENSITIVE);
	}

	private void passStep(final StepResult result, final String details) {
		result.status = "PASS";
		result.details = details;
	}

	private void failStep(final StepResult result, final String details) {
		result.status = "FAIL";
		result.details = details;
	}

	private static boolean isTruthy(final String value) {
		if (value == null) {
			return false;
		}
		final String normalized = value.trim().toLowerCase(Locale.ROOT);
		return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (!isBlank(value)) {
				return value;
			}
		}
		return null;
	}

	private static boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private static String safeMessage(final Exception e) {
		return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
	}

	private static boolean containsAny(final String input, final String... needles) {
		if (input == null) {
			return false;
		}
		for (final String needle : needles) {
			if (input.contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private static boolean sameUrlIgnoringHash(final String left, final String right) {
		if (left == null || right == null) {
			return false;
		}
		return left.split("#")[0].equals(right.split("#")[0]);
	}

	private static final class StepResult {
		private final String field;
		private String status = "FAIL";
		private String details = "Not executed.";
		private String screenshot = "";
		private String finalUrl = "";

		private StepResult(final String field) {
			this.field = field;
		}
	}
}
