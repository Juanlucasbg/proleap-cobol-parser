package io.proleap.e2e.saleads;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final int DEFAULT_TIMEOUT_MS = 25_000;
	private static final int VISIBILITY_POLL_INTERVAL_MS = 250;
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private int screenshotIndex = 1;
	private Path evidenceDir;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String runE2E = env("RUN_SALEADS_E2E", "false");
		Assume.assumeTrue(
				"Skipping external SaleADS E2E workflow. Set RUN_SALEADS_E2E=true to run it.",
				Boolean.parseBoolean(runE2E));

		final String loginUrl = trimToNull(System.getenv("SALEADS_LOGIN_URL"));
		final String wsEndpoint = trimToNull(System.getenv("SALEADS_WS_ENDPOINT"));
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL or SALEADS_WS_ENDPOINT to run this workflow in the current environment.",
				loginUrl != null || wsEndpoint != null);

		evidenceDir = createEvidenceDirectory();
		final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));
		final double slowMo = Double.parseDouble(env("SALEADS_SLOW_MO_MS", "0"));

		try (Playwright playwright = Playwright.create()) {
			try (Browser browser = wsEndpoint == null
					? playwright.chromium()
							.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(slowMo))
					: playwright.chromium().connect(wsEndpoint)) {
				final BrowserContext context = resolveContext(browser);
				final Page appPage = resolvePage(context);
				appPage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

				if (loginUrl != null) {
					appPage.navigate(loginUrl);
					waitForUi(appPage);
				} else {
					Assert.assertFalse(
							"Connected browser page is blank. Provide SALEADS_LOGIN_URL or open login page first.",
							appPage.url() == null || appPage.url().startsWith("about:blank"));
					waitForUi(appPage);
				}

				executeStep("Login", () -> loginWithGoogle(appPage, context));
				executeStep("Mi Negocio menu", () -> openMiNegocioMenu(appPage));
				executeStep("Agregar Negocio modal", () -> validateAgregarNegocioModal(appPage));
				executeStep("Administrar Negocios view", () -> openAdministrarNegocios(appPage));
				executeStep("Información General", () -> validateInformacionGeneral(appPage));
				executeStep("Detalles de la Cuenta", () -> validateDetallesCuenta(appPage));
				executeStep("Tus Negocios", () -> validateTusNegocios(appPage));
				executeStep("Términos y Condiciones",
						() -> validateLegalLink(appPage, context, "Términos y Condiciones",
								Pattern.compile("T[eé]rminos y Condiciones", Pattern.CASE_INSENSITIVE),
								"terminos-condiciones"));
				executeStep("Política de Privacidad",
						() -> validateLegalLink(appPage, context, "Política de Privacidad",
								Pattern.compile("Pol[ií]tica de Privacidad", Pattern.CASE_INSENSITIVE),
								"politica-privacidad"));
			}
		}

		printAndPersistFinalReport();
		assertNoStepFailures();
	}

	private BrowserContext resolveContext(final Browser browser) {
		if (!browser.contexts().isEmpty()) {
			return browser.contexts().get(0);
		}

		return browser.newContext();
	}

	private Page resolvePage(final BrowserContext context) {
		if (!context.pages().isEmpty()) {
			return context.pages().get(0);
		}

		return context.newPage();
	}

	private void loginWithGoogle(final Page appPage, final BrowserContext context) throws IOException {
		final Locator googleLoginButton = firstVisible(appPage, List.of(
				appPage.getByText("Sign in with Google"),
				appPage.getByText("Iniciar sesión con Google"),
				appPage.getByText("Continuar con Google"),
				appPage.getByText("Google")));

		requireVisible(appPage, googleLoginButton, "Google login button");
		final Page authPage = clickAndCapturePotentialNewTab(appPage, context, googleLoginButton);

		if (authPage != null) {
			selectGoogleAccountIfShown(authPage);
		}

		requireVisible(appPage, appPage.locator("aside, nav").first(), "Left sidebar navigation");
		takeScreenshot(appPage, "dashboard-loaded", false);
	}

	private void openMiNegocioMenu(final Page appPage) throws IOException {
		requireVisible(appPage, appPage.locator("aside, nav").first(), "Left sidebar navigation");

		clickAndWait(appPage, firstVisible(appPage, List.of(appPage.getByText("Negocio"))), "Negocio");
		clickAndWait(appPage, firstVisible(appPage, List.of(appPage.getByText("Mi Negocio"))), "Mi Negocio");

		requireVisible(appPage, appPage.getByText("Agregar Negocio").first(), "Agregar Negocio");
		requireVisible(appPage, appPage.getByText("Administrar Negocios").first(), "Administrar Negocios");
		takeScreenshot(appPage, "mi-negocio-menu-expanded", false);
	}

	private void validateAgregarNegocioModal(final Page appPage) throws IOException {
		clickAndWait(appPage, appPage.getByText("Agregar Negocio").first(), "Agregar Negocio menu item");

		requireVisible(appPage, appPage.getByText("Crear Nuevo Negocio").first(), "Crear Nuevo Negocio title");
		requireVisible(appPage, appPage.getByText("Nombre del Negocio").first(), "Nombre del Negocio field label");
		requireVisible(appPage, appPage.getByText("Tienes 2 de 3 negocios").first(), "Business quota text");
		requireVisible(appPage, appPage.getByText("Cancelar").first(), "Cancelar button");
		requireVisible(appPage, appPage.getByText("Crear Negocio").first(), "Crear Negocio button");
		takeScreenshot(appPage, "crear-negocio-modal", false);

		final Locator businessNameField = firstVisible(appPage, List.of(
				appPage.getByLabel("Nombre del Negocio"),
				appPage.getByPlaceholder("Nombre del Negocio"),
				appPage.locator("input").first()));
		requireVisible(appPage, businessNameField, "Nombre del Negocio input");
		businessNameField.fill("Negocio Prueba Automatización");
		clickAndWait(appPage, appPage.getByText("Cancelar").first(), "Cancelar");
	}

	private void openAdministrarNegocios(final Page appPage) throws IOException {
		if (!waitUntilVisible(appPage, appPage.getByText("Administrar Negocios").first(), 4_000)) {
			clickAndWait(appPage, firstVisible(appPage, List.of(appPage.getByText("Mi Negocio"))), "Mi Negocio");
		}

		clickAndWait(appPage, appPage.getByText("Administrar Negocios").first(), "Administrar Negocios");

		requireVisible(appPage, appPage.getByText("Información General").first(), "Información General");
		requireVisible(appPage, appPage.getByText("Detalles de la Cuenta").first(), "Detalles de la Cuenta");
		requireVisible(appPage, appPage.getByText("Tus Negocios").first(), "Tus Negocios");
		requireVisible(appPage, appPage.getByText("Sección Legal").first(), "Sección Legal");

		takeScreenshot(appPage, "administrar-negocios-account-page", true);
	}

	private void validateInformacionGeneral(final Page appPage) {
		requireVisible(appPage, appPage.getByText("Información General").first(), "Información General section");
		requireVisible(appPage, appPage.getByText(EMAIL_PATTERN).first(), "User email");
		requireVisible(appPage, firstVisible(appPage, List.of(
				appPage.getByText("Nombre"),
				appPage.getByText("Usuario"),
				appPage.getByText("Name"))), "User name");
		requireVisible(appPage, appPage.getByText("BUSINESS PLAN").first(), "BUSINESS PLAN text");
		requireVisible(appPage, appPage.getByText("Cambiar Plan").first(), "Cambiar Plan button");
	}

	private void validateDetallesCuenta(final Page appPage) {
		requireVisible(appPage, appPage.getByText("Detalles de la Cuenta").first(), "Detalles de la Cuenta section");
		requireVisible(appPage, appPage.getByText("Cuenta creada").first(), "Cuenta creada text");
		requireVisible(appPage, appPage.getByText("Estado activo").first(), "Estado activo text");
		requireVisible(appPage, appPage.getByText("Idioma seleccionado").first(), "Idioma seleccionado text");
	}

	private void validateTusNegocios(final Page appPage) {
		requireVisible(appPage, appPage.getByText("Tus Negocios").first(), "Tus Negocios section");
		requireVisible(appPage, appPage.getByText("Agregar Negocio").first(), "Agregar Negocio button");
		requireVisible(appPage, appPage.getByText("Tienes 2 de 3 negocios").first(), "Business quota text");
		requireVisible(appPage, firstVisible(appPage, List.of(
				appPage.locator("table tbody tr").first(),
				appPage.locator("ul li").first(),
				appPage.locator("[role='row']").first())), "Business list");
	}

	private void validateLegalLink(final Page appPage, final BrowserContext context, final String linkText,
			final Pattern expectedHeading, final String screenshotKey) throws IOException {
		final Locator legalLink = firstVisible(appPage, List.of(
				appPage.getByText(linkText),
				appPage.getByText(Pattern.compile(Pattern.quote(linkText), Pattern.CASE_INSENSITIVE))));
		requireVisible(appPage, legalLink, linkText + " link");

		final String appUrlBefore = appPage.url();
		final Page legalPage = clickAndCapturePotentialNewTab(appPage, context, legalLink);
		final Page targetPage = legalPage == null ? appPage : legalPage;

		requireVisible(targetPage, targetPage.getByText(expectedHeading).first(), linkText + " heading");
		final String legalText = targetPage.locator("body").innerText();
		Assert.assertTrue(linkText + " content should contain legal text.",
				legalText != null && legalText.trim().length() > 120);

		takeScreenshot(targetPage, screenshotKey, true);
		legalUrls.put(linkText, targetPage.url());

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!appUrlBefore.equals(appPage.url())) {
			appPage.goBack();
			waitForUi(appPage);
		}
	}

	private void executeStep(final String name, final CheckedRunnable action) {
		try {
			action.run();
			report.put(name, StepResult.pass());
		} catch (final Throwable throwable) {
			report.put(name, StepResult.fail(throwable));
		}
	}

	private Page clickAndCapturePotentialNewTab(final Page sourcePage, final BrowserContext context,
			final Locator clickable) {
		final int tabCountBefore = context.pages().size();
		clickAndWait(sourcePage, clickable, "click target");

		final long timeoutAt = System.currentTimeMillis() + 8_000;
		while (System.currentTimeMillis() < timeoutAt) {
			final List<Page> pages = context.pages();
			if (pages.size() > tabCountBefore) {
				final Page lastPage = pages.get(pages.size() - 1);
				if (lastPage != sourcePage) {
					waitForUi(lastPage);
					return lastPage;
				}
			}
			sourcePage.waitForTimeout(VISIBILITY_POLL_INTERVAL_MS);
		}

		return sourcePage;
	}

	private void selectGoogleAccountIfShown(final Page authPage) {
		final Locator accountEntry = authPage.getByText(GOOGLE_ACCOUNT_EMAIL).first();
		if (waitUntilVisible(authPage, accountEntry, 10_000)) {
			clickAndWait(authPage, accountEntry, "Google account selector");
		}
	}

	private void clickAndWait(final Page page, final Locator locator, final String elementDescription) {
		requireVisible(page, locator, elementDescription);
		locator.first().scrollIntoViewIfNeeded();
		locator.first().click();
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// noop
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (final PlaywrightException ignored) {
			// noop
		}

		page.waitForTimeout(800);
	}

	private Locator firstVisible(final Page page, final List<Locator> candidates) {
		for (final Locator candidate : candidates) {
			if (waitUntilVisible(page, candidate, 2_000)) {
				return candidate.first();
			}
		}

		return candidates.get(0).first();
	}

	private void requireVisible(final Page page, final Locator locator, final String description) {
		Assert.assertTrue(description + " should be visible.",
				waitUntilVisible(page, locator.first(), DEFAULT_TIMEOUT_MS));
	}

	private boolean waitUntilVisible(final Page page, final Locator locator, final int timeoutMs) {
		final long timeoutAt = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < timeoutAt) {
			try {
				if (locator.count() > 0 && locator.first().isVisible()) {
					return true;
				}
			} catch (final PlaywrightException ignored) {
				// keep polling
			}

			page.waitForTimeout(VISIBILITY_POLL_INTERVAL_MS);
		}

		return false;
	}

	private void takeScreenshot(final Page page, final String label, final boolean fullPage) throws IOException {
		final String fileName = String.format("%02d-%s.png", screenshotIndex++, sanitizeFileName(label));
		final Path screenshotPath = evidenceDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path directory = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(directory);
		return directory;
	}

	private void printAndPersistFinalReport() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio - Final Report").append(System.lineSeparator());
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		builder.append(System.lineSeparator());

		for (final Map.Entry<String, StepResult> step : report.entrySet()) {
			builder.append(step.getKey()).append(": ").append(step.getValue().passed ? "PASS" : "FAIL");
			if (!step.getValue().passed && step.getValue().detail != null && !step.getValue().detail.isBlank()) {
				builder.append(" - ").append(step.getValue().detail);
			}
			builder.append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			builder.append(System.lineSeparator()).append("Captured legal URLs:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		final String reportText = builder.toString();
		System.out.println(reportText);
		Files.writeString(evidenceDir.resolve("final-report.txt"), reportText);
	}

	private void assertNoStepFailures() {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> step : report.entrySet()) {
			if (!step.getValue().passed) {
				failedSteps.add(step.getKey());
			}
		}

		Assert.assertTrue("Workflow failed on steps: " + failedSteps, failedSteps.isEmpty());
	}

	private String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private String trimToNull(final String value) {
		if (value == null) {
			return null;
		}

		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String sanitizeFileName(final String input) {
		return input.toLowerCase().replaceAll("[^a-z0-9-]+", "-").replaceAll("(^-|-$)", "");
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
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

		private static StepResult fail(final Throwable throwable) {
			final String message = throwable == null || throwable.getMessage() == null ? "Unknown error"
					: throwable.getMessage();
			return new StepResult(false, message);
		}
	}

}
