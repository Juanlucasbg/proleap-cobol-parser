package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
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
import java.util.Locale;
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
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * End-to-end SaleADS test for Google login + Mi Negocio workflow.
 * <p>
 * The environment-specific login URL must be provided via
 * SALEADS_LOGIN_URL or -Dsaleads.login.url.
 */
public class SaleAdsMiNegocioFullTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_POLITICA = "Política de Privacidad";

	private static final List<String> REPORT_FIELDS = Arrays.asList(REPORT_LOGIN, REPORT_MI_NEGOCIO_MENU, REPORT_AGREGAR_MODAL,
			REPORT_ADMINISTRAR_VIEW, REPORT_INFO_GENERAL, REPORT_DETALLES_CUENTA, REPORT_TUS_NEGOCIOS, REPORT_TERMINOS,
			REPORT_POLITICA);

	private static final List<String> LOGIN_BUTTON_TEXTS = Arrays.asList("Sign in with Google", "Inicia sesión con Google",
			"Continuar con Google", "Iniciar sesión con Google", "Sign in", "Inicia sesión", "Iniciar sesión", "Acceder");
	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final double DEFAULT_WAIT_MS = 12000;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final Path artifactsDir = createArtifactsDir();
		final WorkflowReport report = new WorkflowReport(artifactsDir);

		final String loginUrl = resolveLoginUrl();
		if (loginUrl == null || loginUrl.isBlank()) {
			report.fail(REPORT_LOGIN, "Missing login URL. Set SALEADS_LOGIN_URL or -Dsaleads.login.url.");
			report.markRemainingAsPrerequisiteFailures(REPORT_LOGIN);
			report.write();
			assertTrue(report.summary(), report.allPassed());
			return;
		}

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(resolveHeadless()));
			try (BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 1000))) {
				final Page page = context.newPage();
				page.setDefaultTimeout(DEFAULT_WAIT_MS);
				page.navigate(loginUrl);
				waitForUiAfterAction(page);

				executeLoginStep(page, context, report, artifactsDir);
				executeMiNegocioMenuStep(page, report, artifactsDir);
				executeAgregarNegocioModalStep(page, report, artifactsDir);
				executeAdministrarNegociosStep(page, report, artifactsDir);
				executeInformacionGeneralStep(page, report);
				executeDetallesCuentaStep(page, report);
				executeTusNegociosStep(page, report);
				executeLegalPageStep(page, context, report, artifactsDir, REPORT_TERMINOS, "Términos y Condiciones",
						"step8_terminos_y_condiciones.png");
				executeLegalPageStep(page, context, report, artifactsDir, REPORT_POLITICA, "Política de Privacidad",
						"step9_politica_de_privacidad.png");

				final Path finalShot = artifactsDir.resolve("final_state.png");
				takeScreenshot(page, finalShot, true);
			}
		} catch (final Exception e) {
			report.failUnfinishedSteps("Unexpected execution error: " + e.getMessage());
			throw e;
		} finally {
			report.write();
		}

		assertTrue(report.summary(), report.allPassed());
	}

	private void executeLoginStep(final Page page, final BrowserContext context, final WorkflowReport report,
			final Path artifactsDir) {
		final List<String> evidence = new ArrayList<>();
		try {
			clickVisibleText(page, LOGIN_BUTTON_TEXTS, "Login button / Sign in with Google");
			clickVisibleTextIfPresent(page, Arrays.asList("GOOGLE", "Google"));
			clickGoogleAccountIfVisible(context);

			requireVisibleText(page, Arrays.asList("Negocio", "Mi Negocio"), "Left sidebar (Negocio / Mi Negocio)");
			final Path screenshot = artifactsDir.resolve("step1_dashboard_loaded.png");
			takeScreenshot(page, screenshot, true);
			evidence.add(screenshot.toString());
			report.pass(REPORT_LOGIN, "Main interface and sidebar are visible after login flow.", evidence);
		} catch (final Exception e) {
			final Path screenshot = artifactsDir.resolve("step1_login_failure.png");
			takeScreenshotSafely(page, screenshot, false);
			evidence.add(screenshot.toString());
			report.fail(REPORT_LOGIN, "Unable to complete login flow: " + e.getMessage(), evidence);
		}
	}

	private void executeMiNegocioMenuStep(final Page page, final WorkflowReport report, final Path artifactsDir) {
		final List<String> evidence = new ArrayList<>();
		try {
			clickVisibleTextIfPresent(page, Arrays.asList("Negocio"));
			clickVisibleText(page, Arrays.asList("Mi Negocio"), "Mi Negocio option");
			requireVisibleText(page, Arrays.asList("Agregar Negocio"), "Agregar Negocio");
			requireVisibleText(page, Arrays.asList("Administrar Negocios"), "Administrar Negocios");

			final Path screenshot = artifactsDir.resolve("step2_mi_negocio_expanded.png");
			takeScreenshot(page, screenshot, false);
			evidence.add(screenshot.toString());
			report.pass(REPORT_MI_NEGOCIO_MENU, "Mi Negocio menu expanded and submenu options are visible.", evidence);
		} catch (final Exception e) {
			final Path screenshot = artifactsDir.resolve("step2_mi_negocio_failure.png");
			takeScreenshotSafely(page, screenshot, false);
			evidence.add(screenshot.toString());
			report.fail(REPORT_MI_NEGOCIO_MENU, "Could not validate Mi Negocio menu: " + e.getMessage(), evidence);
		}
	}

	private void executeAgregarNegocioModalStep(final Page page, final WorkflowReport report, final Path artifactsDir) {
		final List<String> evidence = new ArrayList<>();
		try {
			clickVisibleText(page, Arrays.asList("Agregar Negocio"), "Agregar Negocio");
			requireVisibleText(page, Arrays.asList("Crear Nuevo Negocio"), "Modal title Crear Nuevo Negocio");
			requireInputByLabelOrPlaceholder(page, "Nombre del Negocio");
			requireVisibleText(page, Arrays.asList("Tienes 2 de 3 negocios"), "Quota text");
			requireVisibleText(page, Arrays.asList("Cancelar"), "Cancelar button");
			requireVisibleText(page, Arrays.asList("Crear Negocio"), "Crear Negocio button");

			fillInputByLabelOrPlaceholder(page, "Nombre del Negocio", "Negocio Prueba Automatización");
			clickVisibleTextIfPresent(page, Arrays.asList("Cancelar"));

			final Path screenshot = artifactsDir.resolve("step3_agregar_negocio_modal.png");
			takeScreenshot(page, screenshot, false);
			evidence.add(screenshot.toString());
			report.pass(REPORT_AGREGAR_MODAL, "Agregar Negocio modal validated successfully.", evidence);
		} catch (final Exception e) {
			final Path screenshot = artifactsDir.resolve("step3_agregar_modal_failure.png");
			takeScreenshotSafely(page, screenshot, false);
			evidence.add(screenshot.toString());
			report.fail(REPORT_AGREGAR_MODAL, "Could not validate Agregar Negocio modal: " + e.getMessage(), evidence);
		}
	}

	private void executeAdministrarNegociosStep(final Page page, final WorkflowReport report, final Path artifactsDir) {
		final List<String> evidence = new ArrayList<>();
		try {
			clickVisibleTextIfPresent(page, Arrays.asList("Mi Negocio"));
			clickVisibleText(page, Arrays.asList("Administrar Negocios"), "Administrar Negocios");
			requireVisibleText(page, Arrays.asList("Información General"), "Información General section");
			requireVisibleText(page, Arrays.asList("Detalles de la Cuenta"), "Detalles de la Cuenta section");
			requireVisibleText(page, Arrays.asList("Tus Negocios"), "Tus Negocios section");
			requireVisibleText(page, Arrays.asList("Sección Legal"), "Sección Legal section");

			final Path screenshot = artifactsDir.resolve("step4_administrar_negocios.png");
			takeScreenshot(page, screenshot, true);
			evidence.add(screenshot.toString());
			report.pass(REPORT_ADMINISTRAR_VIEW, "Administrar Negocios view loaded with all expected sections.", evidence);
		} catch (final Exception e) {
			final Path screenshot = artifactsDir.resolve("step4_administrar_negocios_failure.png");
			takeScreenshotSafely(page, screenshot, true);
			evidence.add(screenshot.toString());
			report.fail(REPORT_ADMINISTRAR_VIEW, "Could not validate Administrar Negocios view: " + e.getMessage(), evidence);
		}
	}

	private void executeInformacionGeneralStep(final Page page, final WorkflowReport report) {
		try {
			requireLikelyUserNameVisible(page);
			requireVisibleText(page, Arrays.asList("BUSINESS PLAN"), "BUSINESS PLAN");
			requireVisibleText(page, Arrays.asList("Cambiar Plan"), "Cambiar Plan");
			requireTextPattern(page, Pattern.compile("^[^\\s].+@.+\\..+$", Pattern.MULTILINE), "User email");
			report.pass(REPORT_INFO_GENERAL, "Información General shows name/email, BUSINESS PLAN and Cambiar Plan.");
		} catch (final Exception e) {
			report.fail(REPORT_INFO_GENERAL, "Could not validate Información General section: " + e.getMessage());
		}
	}

	private void executeDetallesCuentaStep(final Page page, final WorkflowReport report) {
		try {
			requireVisibleText(page, Arrays.asList("Cuenta creada"), "Cuenta creada");
			requireVisibleText(page, Arrays.asList("Estado activo"), "Estado activo");
			requireVisibleText(page, Arrays.asList("Idioma seleccionado"), "Idioma seleccionado");
			report.pass(REPORT_DETALLES_CUENTA, "Detalles de la Cuenta fields are visible.");
		} catch (final Exception e) {
			report.fail(REPORT_DETALLES_CUENTA, "Could not validate Detalles de la Cuenta: " + e.getMessage());
		}
	}

	private void executeTusNegociosStep(final Page page, final WorkflowReport report) {
		try {
			requireVisibleText(page, Arrays.asList("Tus Negocios"), "Tus Negocios");
			requireVisibleText(page, Arrays.asList("Agregar Negocio"), "Agregar Negocio button");
			requireVisibleText(page, Arrays.asList("Tienes 2 de 3 negocios"), "Tienes 2 de 3 negocios");
			report.pass(REPORT_TUS_NEGOCIOS, "Tus Negocios section and quota are visible.");
		} catch (final Exception e) {
			report.fail(REPORT_TUS_NEGOCIOS, "Could not validate Tus Negocios section: " + e.getMessage());
		}
	}

	private void executeLegalPageStep(final Page appPage, final BrowserContext context, final WorkflowReport report,
			final Path artifactsDir, final String reportField, final String linkText, final String screenshotName) {
		final List<String> evidence = new ArrayList<>();
		Page legalPage = appPage;
		final int pageCountBeforeClick = context.pages().size();

		try {
			clickVisibleText(appPage, Arrays.asList(linkText), linkText);
			waitForUiAfterAction(appPage);

			if (context.pages().size() > pageCountBeforeClick) {
				legalPage = context.pages().get(context.pages().size() - 1);
				legalPage.bringToFront();
				waitForUiAfterAction(legalPage);
			} else {
				legalPage = appPage;
			}

			requireVisibleText(legalPage, Arrays.asList(linkText), linkText + " heading");
			requireLegalContentText(legalPage);

			final Path screenshot = artifactsDir.resolve(screenshotName);
			takeScreenshot(legalPage, screenshot, true);
			evidence.add(screenshot.toString());
			final String finalUrl = legalPage.url();
			report.pass(reportField, "Legal page validated successfully.", evidence);
			report.setFinalUrl(reportField, finalUrl);
		} catch (final Exception e) {
			final Path screenshot = artifactsDir.resolve(screenshotName.replace(".png", "_failure.png"));
			takeScreenshotSafely(legalPage, screenshot, true);
			evidence.add(screenshot.toString());
			report.fail(reportField, "Could not validate legal page '" + linkText + "': " + e.getMessage(), evidence);
			try {
				report.setFinalUrl(reportField, legalPage.url());
			} catch (final Exception ignored) {
				// Keep final URL empty if page is unavailable.
			}
		} finally {
			returnToApplicationTab(appPage, legalPage);
		}
	}

	private static void returnToApplicationTab(final Page appPage, final Page currentPage) {
		try {
			if (currentPage != null && currentPage != appPage && !currentPage.isClosed()) {
				currentPage.close();
				appPage.bringToFront();
				waitForUiAfterAction(appPage);
			} else if (appPage != null && !appPage.isClosed()) {
				appPage.goBack(new Page.GoBackOptions().setTimeout(4000));
				waitForUiAfterAction(appPage);
			}
		} catch (final PlaywrightException ignored) {
			// Returning to the app can be best effort when navigation history is absent.
		}
	}

	private static void clickGoogleAccountIfVisible(final BrowserContext context) {
		for (final Page contextPage : context.pages()) {
			try {
				final boolean selected = clickVisibleTextIfPresent(contextPage, Arrays.asList(ACCOUNT_EMAIL));
				if (selected) {
					waitForUiAfterAction(contextPage);
					return;
				}
			} catch (final Exception ignored) {
				// Keep checking other pages.
			}
		}
	}

	private static void requireTextPattern(final Page page, final Pattern pattern, final String description) {
		try {
			final String bodyText = page.locator("body").innerText();
			if (!pattern.matcher(bodyText).find()) {
				throw new IllegalStateException("Pattern not found for " + description + ".");
			}
		} catch (final PlaywrightException e) {
			throw new IllegalStateException("Unable to evaluate text for " + description + ": " + e.getMessage(), e);
		}
	}

	private static void requireLikelyUserNameVisible(final Page page) {
		try {
			final String bodyText = page.locator("body").innerText();
			final List<String> ignoredLines = Arrays.asList("información general", "business plan", "cambiar plan",
					"detalles de la cuenta", "cuenta creada", "estado activo", "idioma seleccionado", "tus negocios",
					"agregar negocio", "administrar negocios", "sección legal", "términos y condiciones",
					"política de privacidad");
			for (final String lineRaw : bodyText.split("\\R")) {
				final String line = lineRaw.trim();
				if (line.isEmpty() || line.contains("@") || line.length() < 5) {
					continue;
				}
				final String lowered = line.toLowerCase(Locale.ROOT);
				if (ignoredLines.contains(lowered)) {
					continue;
				}
				if (line.matches(".*\\d.*")) {
					continue;
				}
				if (line.matches("^[\\p{L}'\\-]{2,}(\\s+[\\p{L}'\\-]{2,}){1,3}$")) {
					return;
				}
			}
			throw new IllegalStateException("Could not find a likely user name in visible account text.");
		} catch (final PlaywrightException e) {
			throw new IllegalStateException("Unable to evaluate account text for user name: " + e.getMessage(), e);
		}
	}

	private static void requireLegalContentText(final Page page) {
		try {
			final String bodyText = page.locator("body").innerText();
			if (bodyText == null || bodyText.trim().length() < 120) {
				throw new IllegalStateException("Legal content text is too short or unavailable.");
			}
		} catch (final PlaywrightException e) {
			throw new IllegalStateException("Unable to read legal content: " + e.getMessage(), e);
		}
	}

	private static void requireInputByLabelOrPlaceholder(final Page page, final String text) {
		final Pattern textPattern = patternFor(text);
		final Locator byLabel = page.getByLabel(textPattern).first();
		final Locator byPlaceholder = page.getByPlaceholder(textPattern).first();
		if (isVisible(byLabel) || isVisible(byPlaceholder)) {
			return;
		}
		throw new IllegalStateException("Input field '" + text + "' is not visible.");
	}

	private static void fillInputByLabelOrPlaceholder(final Page page, final String text, final String value) {
		final Pattern textPattern = patternFor(text);
		final Locator byLabel = page.getByLabel(textPattern).first();
		if (isVisible(byLabel)) {
			byLabel.fill(value);
			waitForUiAfterAction(page);
			return;
		}
		final Locator byPlaceholder = page.getByPlaceholder(textPattern).first();
		if (isVisible(byPlaceholder)) {
			byPlaceholder.fill(value);
			waitForUiAfterAction(page);
			return;
		}
		throw new IllegalStateException("Input field '" + text + "' is not visible.");
	}

	private static void requireVisibleText(final Page page, final List<String> texts, final String description) {
		if (hasAnyVisibleText(page, texts)) {
			return;
		}
		throw new IllegalStateException("Expected text not visible for " + description + ": " + texts);
	}

	private static void clickVisibleText(final Page page, final List<String> texts, final String description) {
		if (clickVisibleTextIfPresent(page, texts)) {
			return;
		}
		throw new IllegalStateException("Could not click expected text for " + description + ": " + texts);
	}

	private static boolean clickVisibleTextIfPresent(final Page page, final List<String> texts) {
		for (final String text : texts) {
			final Pattern pattern = patternFor(text);
			final Locator locator = page.getByText(pattern).first();
			if (clickIfVisible(locator, page)) {
				return true;
			}
			for (final Frame frame : page.frames()) {
				if (frame == page.mainFrame()) {
					continue;
				}
				if (clickIfVisible(frame.getByText(pattern).first(), page)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasAnyVisibleText(final Page page, final List<String> texts) {
		for (final String text : texts) {
			final Pattern pattern = patternFor(text);
			if (isVisible(page.getByText(pattern).first())) {
				return true;
			}
			for (final Frame frame : page.frames()) {
				if (frame == page.mainFrame()) {
					continue;
				}
				if (isVisible(frame.getByText(pattern).first())) {
					return true;
				}
			}
		}
		return false;
	}

	private static Pattern patternFor(final String text) {
		return Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	private static boolean clickIfVisible(final Locator locator, final Page page) {
		if (!isVisible(locator)) {
			return false;
		}
		try {
			locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_WAIT_MS));
			waitForUiAfterAction(page);
			return true;
		} catch (final PlaywrightException e) {
			return false;
		}
	}

	private static boolean isVisible(final Locator locator) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(1200));
			return true;
		} catch (final PlaywrightException e) {
			return false;
		}
	}

	private static void waitForUiAfterAction(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(6000));
		} catch (final PlaywrightException e) {
			try {
				page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(4000));
			} catch (final PlaywrightException ignored) {
				// Some UI actions do not trigger new page loads.
			}
		}
		page.waitForTimeout(350);
	}

	private static void takeScreenshot(final Page page, final Path path, final boolean fullPage) {
		try {
			Files.createDirectories(path.getParent());
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private static void takeScreenshotSafely(final Page page, final Path path, final boolean fullPage) {
		try {
			takeScreenshot(page, path, fullPage);
		} catch (final Exception ignored) {
			// Failure screenshots are best effort.
		}
	}

	private static Path createArtifactsDir() {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
				.withZone(ZoneOffset.UTC)
				.format(Instant.now());
		final Path path = Path.of("target", "saleads-mi-negocio", timestamp);
		try {
			Files.createDirectories(path);
			return path;
		} catch (final IOException e) {
			throw new UncheckedIOException("Could not create artifacts directory: " + path, e);
		}
	}

	private static String resolveLoginUrl() {
		final String[] candidates = { System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"),
				System.getenv("SALEADS_LOGIN_PAGE_URL"), System.getenv("SALEADS_BASE_URL") };
		for (final String candidate : candidates) {
			if (candidate != null && !candidate.isBlank()) {
				return candidate.trim();
			}
		}
		return null;
	}

	private static boolean resolveHeadless() {
		final String value = firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"));
		if (value == null) {
			return true;
		}
		return Boolean.parseBoolean(value);
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private static final class WorkflowReport {
		private final Map<String, StepResult> steps = new LinkedHashMap<>();
		private final Path artifactsDir;

		private WorkflowReport(final Path artifactsDir) {
			this.artifactsDir = artifactsDir;
			for (final String field : REPORT_FIELDS) {
				steps.put(field, StepResult.notExecuted());
			}
		}

		private void pass(final String field, final String details) {
			pass(field, details, List.of());
		}

		private void pass(final String field, final String details, final List<String> evidence) {
			steps.put(field, StepResult.passed(details, evidence));
		}

		private void fail(final String field, final String details) {
			fail(field, details, List.of());
		}

		private void fail(final String field, final String details, final List<String> evidence) {
			steps.put(field, StepResult.failed(details, evidence));
		}

		private void failUnfinishedSteps(final String details) {
			for (final Map.Entry<String, StepResult> entry : steps.entrySet()) {
				if ("NOT_EXECUTED".equals(entry.getValue().status)) {
					entry.setValue(StepResult.failed(details, List.of()));
				}
			}
		}

		private void markRemainingAsPrerequisiteFailures(final String prerequisite) {
			for (final Map.Entry<String, StepResult> entry : steps.entrySet()) {
				if (REPORT_LOGIN.equals(entry.getKey())) {
					continue;
				}
				entry.setValue(StepResult.failed("Prerequisite failed: " + prerequisite + ".", List.of()));
			}
		}

		private void setFinalUrl(final String field, final String url) {
			final StepResult current = steps.get(field);
			if (current == null) {
				return;
			}
			steps.put(field, current.withFinalUrl(url));
		}

		private boolean allPassed() {
			for (final StepResult result : steps.values()) {
				if (!"PASS".equals(result.status)) {
					return false;
				}
			}
			return true;
		}

		private String summary() {
			final StringBuilder builder = new StringBuilder();
			builder.append("SaleADS Mi Negocio workflow report. ");
			for (final Map.Entry<String, StepResult> entry : steps.entrySet()) {
				builder.append(entry.getKey()).append(": ").append(entry.getValue().status).append(". ");
			}
			builder.append("Artifacts: ").append(artifactsDir.toAbsolutePath());
			return builder.toString();
		}

		private void write() {
			writeMarkdown();
			writeJson();
		}

		private void writeMarkdown() {
			final StringBuilder markdown = new StringBuilder();
			markdown.append("# SaleADS Mi Negocio Full Test Report\n\n");
			markdown.append("- Generated UTC: ").append(Instant.now()).append("\n");
			markdown.append("- Artifacts: ").append(artifactsDir.toAbsolutePath()).append("\n\n");
			markdown.append("| Step | Status | Details | Final URL | Evidence |\n");
			markdown.append("|---|---|---|---|---|\n");
			for (final Map.Entry<String, StepResult> entry : steps.entrySet()) {
				final StepResult result = entry.getValue();
				markdown.append("| ").append(escapePipe(entry.getKey())).append(" | ")
						.append(escapePipe(result.status)).append(" | ")
						.append(escapePipe(result.details)).append(" | ")
						.append(escapePipe(result.finalUrl == null ? "" : result.finalUrl)).append(" | ")
						.append(escapePipe(String.join(", ", result.evidence))).append(" |\n");
			}
			writeFile(artifactsDir.resolve("report.md"), markdown.toString());
		}

		private void writeJson() {
			final StringBuilder json = new StringBuilder();
			json.append("{\n");
			json.append("  \"generatedAtUtc\": \"").append(escapeJson(Instant.now().toString())).append("\",\n");
			json.append("  \"artifactsDir\": \"").append(escapeJson(artifactsDir.toAbsolutePath().toString())).append("\",\n");
			json.append("  \"allPassed\": ").append(allPassed()).append(",\n");
			json.append("  \"results\": {\n");
			int index = 0;
			for (final Map.Entry<String, StepResult> entry : steps.entrySet()) {
				final StepResult result = entry.getValue();
				json.append("    \"").append(escapeJson(entry.getKey())).append("\": {\n");
				json.append("      \"status\": \"").append(escapeJson(result.status)).append("\",\n");
				json.append("      \"details\": \"").append(escapeJson(result.details)).append("\",\n");
				json.append("      \"finalUrl\": \"").append(escapeJson(result.finalUrl == null ? "" : result.finalUrl))
						.append("\",\n");
				json.append("      \"evidence\": [");
				for (int i = 0; i < result.evidence.size(); i++) {
					if (i > 0) {
						json.append(", ");
					}
					json.append("\"").append(escapeJson(result.evidence.get(i))).append("\"");
				}
				json.append("]\n");
				json.append("    }");
				index++;
				if (index < steps.size()) {
					json.append(",");
				}
				json.append("\n");
			}
			json.append("  }\n");
			json.append("}\n");
			writeFile(artifactsDir.resolve("report.json"), json.toString());
		}

		private void writeFile(final Path path, final String content) {
			try {
				Files.writeString(path, content, StandardCharsets.UTF_8);
			} catch (final IOException e) {
				throw new UncheckedIOException("Could not write report file: " + path, e);
			}
		}

		private static String escapePipe(final String value) {
			return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
		}

		private static String escapeJson(final String value) {
			return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
		}
	}

	private static final class StepResult {
		private final String status;
		private final String details;
		private final List<String> evidence;
		private final String finalUrl;

		private StepResult(final String status, final String details, final List<String> evidence, final String finalUrl) {
			this.status = status;
			this.details = details;
			this.evidence = List.copyOf(evidence);
			this.finalUrl = finalUrl;
		}

		private static StepResult notExecuted() {
			return new StepResult("NOT_EXECUTED", "Not executed.", List.of(), "");
		}

		private static StepResult passed(final String details, final List<String> evidence) {
			return new StepResult("PASS", details, evidence, "");
		}

		private static StepResult failed(final String details, final List<String> evidence) {
			return new StepResult("FAIL", details, evidence, "");
		}

		private StepResult withFinalUrl(final String url) {
			return new StepResult(status, details, evidence, url == null ? "" : url);
		}
	}
}
