package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * End-to-end workflow for SaleADS "Mi Negocio" module.
 *
 * Required runtime input:
 * -Dsaleads.login.url=https://<env>/login
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final long DEFAULT_TIMEOUT_MS = 20_000;
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private Path outputDir;
	private Path screenshotsDir;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = resolveLoginUrl();
		Assume.assumeTrue("Missing SaleADS login URL. Set -Dsaleads.login.url or SALEADS_LOGIN_URL.",
				loginUrl != null && !loginUrl.isBlank());

		initializeOutputDirectories();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(resolveHeadless()));
			final BrowserContext context = browser.newContext();
			final Page[] appPageRef = new Page[] { context.newPage() };

			appPageRef[0].navigate(loginUrl);
			waitForUi(appPageRef[0]);

			final boolean loginOk = runStep("Login", () -> {
				clickGoogleLogin(appPageRef[0]);
				waitForGoogleAccountSelectionIfPresent(context);

				appPageRef[0] = findApplicationPage(context);
				assertVisible(appPageRef[0], "text=Negocio", "left sidebar navigation");
				captureScreenshot(appPageRef[0], "01-dashboard-loaded.png", false);
			});

			if (loginOk) {
				runStep("Mi Negocio menu", () -> {
					openMiNegocioMenu(appPageRef[0]);
					assertVisible(appPageRef[0], "text=Agregar Negocio", "Agregar Negocio submenu item");
					assertVisible(appPageRef[0], "text=Administrar Negocios", "Administrar Negocios submenu item");
					captureScreenshot(appPageRef[0], "02-mi-negocio-menu-expanded.png", false);
				});

				runStep("Agregar Negocio modal", () -> {
					clickByText(appPageRef[0], "Agregar Negocio");
					assertVisible(appPageRef[0], "text=Crear Nuevo Negocio", "Crear Nuevo Negocio modal title");
					assertVisible(appPageRef[0], "text=Nombre del Negocio", "Nombre del Negocio field label");
					assertVisible(appPageRef[0], "text=Tienes 2 de 3 negocios", "business limit text");
					assertVisible(appPageRef[0], "text=Cancelar", "Cancelar button");
					assertVisible(appPageRef[0], "text=Crear Negocio", "Crear Negocio button");
					captureScreenshot(appPageRef[0], "03-agregar-negocio-modal.png", false);

					fillIfVisible(appPageRef[0],
							"input[placeholder*='Nombre del Negocio'], input[name*='nombre'], input[id*='nombre']",
							"Negocio Prueba Automatización");
					clickByText(appPageRef[0], "Cancelar");
				});

				runStep("Administrar Negocios view", () -> {
					openMiNegocioMenu(appPageRef[0]);
					clickByText(appPageRef[0], "Administrar Negocios");
					assertVisible(appPageRef[0], "text=Información General", "Información General section");
					assertVisible(appPageRef[0], "text=Detalles de la Cuenta", "Detalles de la Cuenta section");
					assertVisible(appPageRef[0], "text=Tus Negocios", "Tus Negocios section");
					assertVisible(appPageRef[0], "text=Sección Legal", "Sección Legal section");
					captureScreenshot(appPageRef[0], "04-administrar-negocios-full.png", true);
				});

				runStep("Información General", () -> {
					assertAnyVisible(appPageRef[0], new String[] { "text=Nombre", "text=Usuario", "text=Perfil" },
							"user name label in Información General");
					assertAnyVisible(appPageRef[0],
							new String[] { "text=@", "xpath=//*[contains(text(),'@') and not(self::script)]" },
							"user email in Información General");
					assertVisible(appPageRef[0], "text=BUSINESS PLAN", "BUSINESS PLAN text");
					assertVisible(appPageRef[0], "text=Cambiar Plan", "Cambiar Plan button");
				});

				runStep("Detalles de la Cuenta", () -> {
					assertVisible(appPageRef[0], "text=Cuenta creada", "Cuenta creada label");
					assertVisible(appPageRef[0], "text=Estado activo", "Estado activo label");
					assertVisible(appPageRef[0], "text=Idioma seleccionado", "Idioma seleccionado label");
				});

				runStep("Tus Negocios", () -> {
					assertVisible(appPageRef[0], "text=Tus Negocios", "Tus Negocios section title");
					assertVisible(appPageRef[0], "text=Agregar Negocio", "Agregar Negocio button in account page");
					assertVisible(appPageRef[0], "text=Tienes 2 de 3 negocios", "business limit text");
					assertAnyVisible(appPageRef[0],
							new String[] { "[role='listitem']", "li", "[class*='business']", "text=Negocio" },
							"business list content");
				});

				runStep("Términos y Condiciones", () -> {
					final LegalNavigationResult result = openAndValidateLegalPage(context, appPageRef[0],
							"Términos y Condiciones",
							"Términos y Condiciones", "08-terminos-y-condiciones.png");
					report.put("Términos y Condiciones", StepResult.passed("URL final: " + result.url));
				});

				runStep("Política de Privacidad", () -> {
					final LegalNavigationResult result = openAndValidateLegalPage(context, appPageRef[0],
							"Política de Privacidad",
							"Política de Privacidad", "09-politica-de-privacidad.png");
					report.put("Política de Privacidad", StepResult.passed("URL final: " + result.url));
				});
			} else {
				markRemainingAsFailed("Login failed; dependent workflow steps were not executed.");
			}

			writeFinalReport();
			assertTrue("Workflow validation failed. See report at " + outputDir.resolve("final-report.md"), allStepsPassed());
		}
	}

	private void clickGoogleLogin(final Page page) {
		clickFirstVisible(page, "button:has-text('Sign in with Google')", "button:has-text('Iniciar sesión con Google')",
				"text=Sign in with Google", "text=Iniciar sesión con Google", "text=Google");
	}

	private void waitForGoogleAccountSelectionIfPresent(final BrowserContext context) {
		for (int attempt = 0; attempt < 20; attempt++) {
			for (final Page candidate : context.pages()) {
				final Locator account = candidate.locator("text=" + GOOGLE_ACCOUNT_EMAIL).first();
				if (isVisible(account)) {
					clickAndWait(candidate, account);
					return;
				}
			}
			sleep(500);
		}
	}

	private Page findApplicationPage(final BrowserContext context) {
		for (int attempt = 0; attempt < 40; attempt++) {
			for (final Page candidate : context.pages()) {
				if (isVisible(candidate.locator("text=Negocio").first())) {
					candidate.bringToFront();
					waitForUi(candidate);
					return candidate;
				}
			}
			sleep(500);
		}

		throw new AssertionError("Could not locate SaleADS main application page with left sidebar.");
	}

	private void openMiNegocioMenu(final Page page) {
		clickByText(page, "Negocio");
		clickByText(page, "Mi Negocio");
		assertVisible(page, "text=Agregar Negocio", "expanded Mi Negocio menu");
	}

	private LegalNavigationResult openAndValidateLegalPage(final BrowserContext context, final Page appPage,
			final String linkText, final String headingText, final String screenshotName) throws IOException {
		final int beforePages = context.pages().size();
		clickByText(appPage, linkText);

		Page targetPage = appPage;
		for (int attempt = 0; attempt < 16; attempt++) {
			if (context.pages().size() > beforePages) {
				targetPage = context.pages().get(context.pages().size() - 1);
				break;
			}
			sleep(250);
		}

		targetPage.bringToFront();
		waitForUi(targetPage);
		assertVisible(targetPage, "text=" + headingText, headingText + " heading");
		assertAnyVisible(targetPage, new String[] { "p", "article", "main", "text=última", "text=actualización" },
				headingText + " legal content");
		captureScreenshot(targetPage, screenshotName, true);
		final String finalUrl = targetPage.url();

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				targetPage.goBack();
			} catch (final Exception ignored) {
				// Some environments can open legal pages in same tab without back history.
			}
			appPage.bringToFront();
			waitForUi(appPage);
		}

		return new LegalNavigationResult(finalUrl);
	}

	private boolean runStep(final String reportKey, final CheckedRunnable action) {
		try {
			action.run();
			report.putIfAbsent(reportKey, StepResult.passed("Validated successfully."));
			return true;
		} catch (final Throwable throwable) {
			report.put(reportKey, StepResult.failed(safeMessage(throwable)));
			return false;
		}
	}

	private void markRemainingAsFailed(final String reason) {
		for (final String field : REPORT_FIELDS) {
			report.putIfAbsent(field, StepResult.failed(reason));
		}
	}

	private boolean allStepsPassed() {
		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			if (result == null || !result.passed) {
				return false;
			}
		}
		return true;
	}

	private void writeFinalReport() throws IOException {
		markMissingEntriesAsFailed();

		final StringBuilder markdown = new StringBuilder();
		markdown.append("# SaleADS Mi Negocio Full Test Report\n\n");
		markdown.append("Generated at: ").append(LocalDateTime.now()).append('\n');
		markdown.append("Screenshots directory: ").append(screenshotsDir.toAbsolutePath()).append("\n\n");
		markdown.append("| Step | Status | Details |\n");
		markdown.append("|---|---|---|\n");

		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			final String status = result.passed ? "PASS" : "FAIL";
			markdown.append("| ").append(field).append(" | ").append(status).append(" | ")
					.append(escapePipe(result.details)).append(" |\n");
		}

		Files.writeString(outputDir.resolve("final-report.md"), markdown.toString(), StandardCharsets.UTF_8);

		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"results\": [\n");
		for (int i = 0; i < REPORT_FIELDS.size(); i++) {
			final String field = REPORT_FIELDS.get(i);
			final StepResult result = report.get(field);
			json.append("    {\n");
			json.append("      \"step\": \"").append(escapeJson(field)).append("\",\n");
			json.append("      \"status\": \"").append(result.passed ? "PASS" : "FAIL").append("\",\n");
			json.append("      \"details\": \"").append(escapeJson(result.details)).append("\"\n");
			json.append("    }");
			if (i < REPORT_FIELDS.size() - 1) {
				json.append(",");
			}
			json.append("\n");
		}
		json.append("  ]\n");
		json.append("}\n");

		Files.writeString(outputDir.resolve("final-report.json"), json.toString(), StandardCharsets.UTF_8);
	}

	private void markMissingEntriesAsFailed() {
		for (final String field : REPORT_FIELDS) {
			report.putIfAbsent(field, StepResult.failed("Step did not execute."));
		}
	}

	private void initializeOutputDirectories() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		outputDir = Paths.get("target", "saleads-mi-negocio", timestamp);
		screenshotsDir = outputDir.resolve("screenshots");
		Files.createDirectories(screenshotsDir);
	}

	private void captureScreenshot(final Page page, final String fileName, final boolean fullPage) throws IOException {
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotsDir.resolve(fileName)).setFullPage(fullPage));
	}

	private void clickByText(final Page page, final String text) {
		clickFirstVisible(page, "button:has-text('" + text + "')", "a:has-text('" + text + "')",
				"[role='button']:has-text('" + text + "')", "text=" + text);
	}

	private void clickFirstVisible(final Page page, final String... selectors) {
		for (final String selector : selectors) {
			final Locator locator = page.locator(selector).first();
			if (isVisible(locator)) {
				clickAndWait(page, locator);
				return;
			}
		}
		throw new AssertionError("Could not find any visible element for selectors: " + String.join(", ", selectors));
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.scrollIntoViewIfNeeded();
		locator.click();
		waitForUi(page);
	}

	private void assertVisible(final Page page, final String selector, final String description) {
		try {
			page.locator(selector).first()
					.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (final TimeoutError timeoutError) {
			throw new AssertionError("Element not visible: " + description + " (selector: " + selector + ")");
		}
	}

	private void assertAnyVisible(final Page page, final String[] selectors, final String description) {
		for (final String selector : selectors) {
			if (isVisible(page.locator(selector).first())) {
				return;
			}
		}
		throw new AssertionError("None of expected elements are visible for " + description);
	}

	private void fillIfVisible(final Page page, final String selector, final String value) {
		final Locator locator = page.locator(selector).first();
		if (isVisible(locator)) {
			locator.fill(value);
			waitForUi(page);
		}
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.isVisible();
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final Exception ignored) {
			// Not all UI actions trigger full page load events.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7_000));
		} catch (final Exception ignored) {
			// Some pages keep network activity alive; continue after timeout.
		}

		page.waitForTimeout(500);
	}

	private static String resolveLoginUrl() {
		final String systemProperty = System.getProperty("saleads.login.url");
		if (systemProperty != null && !systemProperty.isBlank()) {
			return systemProperty.trim();
		}

		final String env = System.getenv("SALEADS_LOGIN_URL");
		if (env != null && !env.isBlank()) {
			return env.trim();
		}

		return null;
	}

	private static boolean resolveHeadless() {
		final String systemProperty = System.getProperty("saleads.headless");
		if (systemProperty != null && !systemProperty.isBlank()) {
			return Boolean.parseBoolean(systemProperty);
		}

		final String env = System.getenv("SALEADS_HEADLESS");
		if (env != null && !env.isBlank()) {
			return Boolean.parseBoolean(env);
		}

		return true;
	}

	private static String safeMessage(final Throwable throwable) {
		final String message = throwable.getMessage();
		if (message == null || message.isBlank()) {
			return throwable.getClass().getSimpleName();
		}

		return message.replaceAll("[\\r\\n]+", " ").trim();
	}

	private static String escapePipe(final String input) {
		return input.replace("|", "\\|");
	}

	private static String escapeJson(final String input) {
		return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult passed(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult failed(final String details) {
			return new StepResult(false, details);
		}
	}

	private static final class LegalNavigationResult {
		private final String url;

		private LegalNavigationResult(final String url) {
			this.url = url;
		}
	}
}
