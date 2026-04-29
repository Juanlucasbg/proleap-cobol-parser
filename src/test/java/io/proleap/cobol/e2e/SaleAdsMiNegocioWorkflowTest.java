package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
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
import java.util.Map;
import java.util.regex.Pattern;

public class SaleAdsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final int STEP_TIMEOUT_MS = 15000;
	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
			.withZone(ZoneOffset.UTC);

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final boolean enabled = Boolean.parseBoolean(env("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run SaleADS E2E test.", enabled);

		final Path evidenceDir = createEvidenceDir();
		final Map<String, StepStatus> report = initializeReport();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final BrowserSession session = openBrowserSession(playwright);
			final Browser browser = session.browser;
			final BrowserContext context = session.context;
			Page appPage = session.page;

			try {
				boolean workflowBlocked = false;
				final Page initialPage = appPage;

				if (!executeStep("Login", report, initialPage, evidenceDir, () -> {
					runLoginStep(initialPage);
					takeScreenshot(initialPage, evidenceDir.resolve("step1_dashboard_loaded.png"), true);
				})) {
					workflowBlocked = true;
				}

				if (workflowBlocked) {
					blockRemainingSteps(report, "Login failed, workflow could not continue.");
					finalizeReport(report, legalUrls, evidenceDir);
					Assert.fail("Login failed. Check report at: " + evidenceDir.resolve("final_report.md"));
					return;
				}

				executeStep("Mi Negocio menu", report, initialPage, evidenceDir, () -> {
					runMiNegocioMenuStep(initialPage);
					takeScreenshot(initialPage, evidenceDir.resolve("step2_mi_negocio_menu_expanded.png"), false);
				});

				executeStep("Agregar Negocio modal", report, initialPage, evidenceDir, () -> {
					runAgregarNegocioModalStep(initialPage);
					takeScreenshot(initialPage, evidenceDir.resolve("step3_agregar_negocio_modal.png"), false);
					clickText(initialPage, "Cancelar");
				});

				executeStep("Administrar Negocios view", report, initialPage, evidenceDir, () -> {
					runAdministrarNegociosStep(initialPage);
					takeScreenshot(initialPage, evidenceDir.resolve("step4_administrar_negocios_view.png"), true);
				});

				executeStep("Información General", report, initialPage, evidenceDir,
						() -> runInformacionGeneralStep(initialPage));
				executeStep("Detalles de la Cuenta", report, initialPage, evidenceDir,
						() -> runDetallesCuentaStep(initialPage));
				executeStep("Tus Negocios", report, initialPage, evidenceDir, () -> runTusNegociosStep(initialPage));

				appPage = runLegalStep(context, appPage, "Términos y Condiciones", "Términos y Condiciones",
						"step8_terminos_condiciones.png", report, evidenceDir, legalUrls);

				appPage = runLegalStep(context, appPage, "Política de Privacidad", "Política de Privacidad",
						"step9_politica_privacidad.png", report, evidenceDir, legalUrls);

				finalizeReport(report, legalUrls, evidenceDir);

				if (hasFailures(report)) {
					Assert.fail("SaleADS Mi Negocio workflow has failed validations. Check report at: "
							+ evidenceDir.resolve("final_report.md"));
				}
			} finally {
				context.close();
				browser.close();
			}
		}
	}

	private BrowserSession openBrowserSession(final Playwright playwright) {
		final String cdpUrl = System.getenv("SALEADS_CDP_URL");
		final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));

		if (isNotBlank(cdpUrl)) {
			final Browser browser = playwright.chromium().connectOverCDP(cdpUrl);
			final BrowserContext context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
			final Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
			waitForUi(page);
			return new BrowserSession(browser, context, page);
		}

		final String startUrl = System.getenv("SALEADS_START_URL");
		Assume.assumeTrue(
				"Provide SALEADS_START_URL, or use SALEADS_CDP_URL pointing to an existing browser already on login page.",
				isNotBlank(startUrl));

		final Browser browser = playwright.chromium()
				.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(120));
		final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
		final Page page = context.newPage();
		page.navigate(startUrl);
		waitForUi(page);
		return new BrowserSession(browser, context, page);
	}

	private void runLoginStep(final Page page) {
		clickFirstVisibleText(page, "Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Google");
		waitForUi(page);

		if (isTextVisible(page, GOOGLE_ACCOUNT_EMAIL, 5000)) {
			clickText(page, GOOGLE_ACCOUNT_EMAIL);
		}

		assertVisibleText(page, "Negocio", STEP_TIMEOUT_MS, "Main app sidebar not visible after login.");
		assertVisibleText(page, "Mi Negocio", STEP_TIMEOUT_MS, "Main app interface did not appear after login.");
	}

	private void runMiNegocioMenuStep(final Page page) {
		assertVisibleText(page, "Negocio", STEP_TIMEOUT_MS, "Sidebar section 'Negocio' is not visible.");
		clickText(page, "Mi Negocio");

		assertVisibleText(page, "Agregar Negocio", STEP_TIMEOUT_MS, "'Agregar Negocio' not visible in submenu.");
		assertVisibleText(page, "Administrar Negocios", STEP_TIMEOUT_MS,
				"'Administrar Negocios' not visible in submenu.");
	}

	private void runAgregarNegocioModalStep(final Page page) {
		clickText(page, "Agregar Negocio");
		assertVisibleText(page, "Crear Nuevo Negocio", STEP_TIMEOUT_MS, "Modal title not visible.");
		assertVisibleText(page, "Nombre del Negocio", STEP_TIMEOUT_MS, "Business name input not visible.");
		assertVisibleText(page, "Tienes 2 de 3 negocios", STEP_TIMEOUT_MS, "Business quota text is missing.");
		assertVisibleText(page, "Cancelar", STEP_TIMEOUT_MS, "Button 'Cancelar' is missing.");
		assertVisibleText(page, "Crear Negocio", STEP_TIMEOUT_MS, "Button 'Crear Negocio' is missing.");

		final Locator businessNameInput = page.locator("input[placeholder*='Nombre'], input[name*='nombre'], input");
		businessNameInput.first()
				.fill("Negocio Prueba Automatizacion");
		waitForUi(page);
	}

	private void runAdministrarNegociosStep(final Page page) {
		if (!isTextVisible(page, "Administrar Negocios", 3000)) {
			clickText(page, "Mi Negocio");
		}

		clickText(page, "Administrar Negocios");
		assertVisibleText(page, "Información General", STEP_TIMEOUT_MS, "Section 'Información General' is missing.");
		assertVisibleText(page, "Detalles de la Cuenta", STEP_TIMEOUT_MS,
				"Section 'Detalles de la Cuenta' is missing.");
		assertVisibleText(page, "Tus Negocios", STEP_TIMEOUT_MS, "Section 'Tus Negocios' is missing.");
		assertVisibleText(page, "Sección Legal", STEP_TIMEOUT_MS, "Section 'Sección Legal' is missing.");
	}

	private void runInformacionGeneralStep(final Page page) {
		final String expectedName = System.getenv("SALEADS_EXPECTED_USER_NAME");
		final String expectedEmail = env("SALEADS_EXPECTED_USER_EMAIL", GOOGLE_ACCOUNT_EMAIL);

		if (isNotBlank(expectedName)) {
			assertVisibleText(page, expectedName, STEP_TIMEOUT_MS, "Expected user name is not visible.");
		} else {
			Assert.assertTrue("User name indicators are not visible.",
					isTextVisible(page, "Nombre", STEP_TIMEOUT_MS) || isTextVisible(page, "Usuario", STEP_TIMEOUT_MS));
		}

		assertVisibleText(page, expectedEmail, STEP_TIMEOUT_MS, "Expected user email is not visible.");
		assertVisibleText(page, "BUSINESS PLAN", STEP_TIMEOUT_MS, "'BUSINESS PLAN' text is not visible.");
		assertVisibleText(page, "Cambiar Plan", STEP_TIMEOUT_MS, "'Cambiar Plan' button is not visible.");
	}

	private void runDetallesCuentaStep(final Page page) {
		assertVisibleText(page, "Cuenta creada", STEP_TIMEOUT_MS, "'Cuenta creada' is not visible.");
		assertVisibleText(page, "Estado activo", STEP_TIMEOUT_MS, "'Estado activo' is not visible.");
		assertVisibleText(page, "Idioma seleccionado", STEP_TIMEOUT_MS, "'Idioma seleccionado' is not visible.");
	}

	private void runTusNegociosStep(final Page page) {
		assertVisibleText(page, "Tus Negocios", STEP_TIMEOUT_MS, "Business list section is not visible.");
		assertVisibleText(page, "Agregar Negocio", STEP_TIMEOUT_MS, "'Agregar Negocio' button is not visible.");
		assertVisibleText(page, "Tienes 2 de 3 negocios", STEP_TIMEOUT_MS, "Business quota text is missing.");
	}

	private Page runLegalStep(final BrowserContext context, final Page appPage, final String linkText,
			final String headingText, final String screenshotFileName, final Map<String, StepStatus> report,
			final Path evidenceDir, final Map<String, String> legalUrls) {
		final String reportKey = headingText;
		try {
			final String appUrlBeforeClick = appPage.url();
			Page legalPage;
			boolean openedNewTab = true;

			try {
				legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(7000),
						() -> clickText(appPage, linkText));
				waitForUi(legalPage);
			} catch (PlaywrightException ex) {
				openedNewTab = false;
				legalPage = appPage;
				waitForUi(legalPage);
			}

			assertVisibleText(legalPage, headingText, STEP_TIMEOUT_MS,
					"Legal page heading '" + headingText + "' is not visible.");

			final String bodyText = legalPage.locator("body").innerText();
			Assert.assertTrue("Legal content text is not visible for '" + headingText + "'.",
					bodyText != null && bodyText.replaceAll("\\s+", " ").trim().length() > 120);

			takeScreenshot(legalPage, evidenceDir.resolve(screenshotFileName), true);
			legalUrls.put(headingText, legalPage.url());

			if (openedNewTab) {
				legalPage.close();
				appPage.bringToFront();
				waitForUi(appPage);
			} else if (!appPage.url().equals(appUrlBeforeClick)) {
				appPage.goBack(new Page.GoBackOptions().setTimeout(STEP_TIMEOUT_MS));
				waitForUi(appPage);
			}

			report.put(reportKey, StepStatus.passed("Validated and captured screenshot."));
			return appPage;
		} catch (Exception ex) {
			report.put(reportKey, StepStatus.failed(ex.getMessage()));
			captureFailureScreenshot(appPage, evidenceDir, reportKey);
			return appPage;
		}
	}

	private boolean executeStep(final String stepName, final Map<String, StepStatus> report, final Page page,
			final Path evidenceDir, final CheckedRunnable action) {
		try {
			action.run();
			report.put(stepName, StepStatus.passed("All validations passed."));
			return true;
		} catch (Exception ex) {
			report.put(stepName, StepStatus.failed(ex.getMessage()));
			captureFailureScreenshot(page, evidenceDir, stepName);
			return false;
		}
	}

	private void blockRemainingSteps(final Map<String, StepStatus> report, final String reason) {
		for (Map.Entry<String, StepStatus> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				entry.setValue(StepStatus.failed("Blocked: " + reason));
			}
		}
	}

	private boolean hasFailures(final Map<String, StepStatus> report) {
		for (StepStatus status : report.values()) {
			if (!status.passed) {
				return true;
			}
		}

		return false;
	}

	private void finalizeReport(final Map<String, StepStatus> report, final Map<String, String> legalUrls,
			final Path evidenceDir) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("# SaleADS Mi Negocio Workflow Report\n\n");
		builder.append("- Generated (UTC): ").append(Instant.now()).append('\n');
		builder.append("- Evidence directory: ").append(evidenceDir).append("\n\n");
		builder.append("## Step Results\n\n");
		builder.append("| Validation | Status | Details |\n");
		builder.append("| --- | --- | --- |\n");

		for (Map.Entry<String, StepStatus> entry : report.entrySet()) {
			final StepStatus status = entry.getValue();
			builder.append("| ").append(entry.getKey()).append(" | ").append(status.passed ? "PASS" : "FAIL")
					.append(" | ").append(sanitize(status.details)).append(" |\n");
		}

		builder.append("\n## Captured URLs\n\n");
		if (legalUrls.isEmpty()) {
			builder.append("- No legal URLs captured.\n");
		} else {
			for (Map.Entry<String, String> urlEntry : legalUrls.entrySet()) {
				builder.append("- ").append(urlEntry.getKey()).append(": ").append(urlEntry.getValue()).append('\n');
			}
		}

		Files.writeString(evidenceDir.resolve("final_report.md"), builder.toString(), StandardCharsets.UTF_8);
	}

	private void takeScreenshot(final Page page, final Path path, final boolean fullPage) throws IOException {
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
		waitForUi(page);
	}

	private void captureFailureScreenshot(final Page page, final Path evidenceDir, final String label) {
		try {
			final String safeLabel = label.toLowerCase().replaceAll("[^a-z0-9]+", "_");
			page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve("failure_" + safeLabel + ".png"))
					.setFullPage(true));
		} catch (Exception ignored) {
			// Screenshot capture should never mask the original step error.
		}
	}

	private void clickText(final Page page, final String text) {
		final Locator target = waitForVisibleText(page, text, STEP_TIMEOUT_MS);
		target.click(new Locator.ClickOptions().setTimeout(STEP_TIMEOUT_MS));
		waitForUi(page);
	}

	private void clickFirstVisibleText(final Page page, final String... candidates) {
		for (String candidate : candidates) {
			if (isTextVisible(page, candidate, 3500)) {
				clickText(page, candidate);
				return;
			}
		}

		Assert.fail("None of the expected clickable texts were visible: " + Arrays.toString(candidates));
	}

	private void assertVisibleText(final Page page, final String text, final int timeoutMs, final String failureMessage) {
		try {
			waitForVisibleText(page, text, timeoutMs);
		} catch (PlaywrightException ex) {
			Assert.fail(failureMessage + " Missing text: " + text);
		}
	}

	private boolean isTextVisible(final Page page, final String text, final int timeoutMs) {
		try {
			waitForVisibleText(page, text, timeoutMs);
			return true;
		} catch (PlaywrightException ex) {
			return false;
		}
	}

	private Locator waitForVisibleText(final Page page, final String text, final int timeoutMs) {
		final Pattern flexiblePattern = Pattern.compile("(?i).*" + Pattern.quote(text) + ".*");
		final Locator locator = page.getByText(flexiblePattern).first();
		locator.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs).setState(WaitForSelectorState.VISIBLE));
		return locator;
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (PlaywrightException ignored) {
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
		}

		page.waitForTimeout(700);
	}

	private String env(final String name, final String defaultValue) {
		final String value = System.getenv(name);
		return value == null ? defaultValue : value;
	}

	private boolean isNotBlank(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	private String sanitize(final String value) {
		if (value == null) {
			return "";
		}

		return value.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
	}

	private Path createEvidenceDir() throws IOException {
		final Path baseDir = Paths.get("target", "saleads-evidence");
		final Path evidenceDir = baseDir.resolve(TS_FORMATTER.format(Instant.now()));
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private Map<String, StepStatus> initializeReport() {
		final Map<String, StepStatus> report = new LinkedHashMap<>();
		report.put("Login", StepStatus.failed("Not executed."));
		report.put("Mi Negocio menu", StepStatus.failed("Not executed."));
		report.put("Agregar Negocio modal", StepStatus.failed("Not executed."));
		report.put("Administrar Negocios view", StepStatus.failed("Not executed."));
		report.put("Información General", StepStatus.failed("Not executed."));
		report.put("Detalles de la Cuenta", StepStatus.failed("Not executed."));
		report.put("Tus Negocios", StepStatus.failed("Not executed."));
		report.put("Términos y Condiciones", StepStatus.failed("Not executed."));
		report.put("Política de Privacidad", StepStatus.failed("Not executed."));
		return report;
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class BrowserSession {
		private final Browser browser;
		private final BrowserContext context;
		private final Page page;

		private BrowserSession(final Browser browser, final BrowserContext context, final Page page) {
			this.browser = browser;
			this.context = context;
			this.page = page;
		}
	}

	private static final class StepStatus {
		private final boolean passed;
		private final String details;

		private StepStatus(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepStatus passed(final String details) {
			return new StepStatus(true, details);
		}

		private static StepStatus failed(final String details) {
			return new StepStatus(false, details);
		}
	}
}
