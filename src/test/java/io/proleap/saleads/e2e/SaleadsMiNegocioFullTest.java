package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

/**
 * Full E2E test for SaleADS "Mi Negocio" workflow.
 *
 * Runtime configuration:
 * - RUN_SALEADS_E2E=true (required to execute)
 * - SALEADS_START_URL=https://<env-login-url> (recommended)
 * - SALEADS_HEADLESS=true|false (optional, default true)
 * - SALEADS_EMAIL=<email> (optional, default juanlucasbarbiergarzon@gmail.com)
 * - SALEADS_E2E_ARTIFACTS_DIR=<path> (optional, default target/saleads-artifacts)
 */
public class SaleadsMiNegocioFullTest {

	private static final int WAIT_MS = 15000;
	private static final int SHORT_WAIT_MS = 5000;
	private static final String DEFAULT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private Path artifactsDir;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		Assume.assumeTrue("Set RUN_SALEADS_E2E=true to execute this environment-dependent test.",
				Boolean.parseBoolean(System.getenv().getOrDefault("RUN_SALEADS_E2E", "false")));

		final String email = System.getenv().getOrDefault("SALEADS_EMAIL", DEFAULT_EMAIL).trim();
		artifactsDir = initArtifactsDir();

		try (Playwright playwright = Playwright.create()) {
			final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
			try (Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
					BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080))) {

				Page appPage = context.newPage();
				navigateToLoginIfProvided(appPage);

				runStep("Login", () -> loginWithGoogleAndValidateShell(appPage, email));
				runStep("Mi Negocio menu", () -> openMiNegocioMenuAndValidate(appPage));
				runStep("Agregar Negocio modal", () -> validateAgregarNegocioModal(appPage));
				runStep("Administrar Negocios view", () -> openAdministrarNegociosAndValidateSections(appPage));
				runStep("Información General", () -> validateInformacionGeneral(appPage, email));
				runStep("Detalles de la Cuenta", () -> validateDetallesCuenta(appPage));
				runStep("Tus Negocios", () -> validateTusNegocios(appPage));
				runStep("Términos y Condiciones",
						() -> validateLegalLink(appPage, context, "Términos y Condiciones", "Términos y Condiciones", "08-terminos"));
				runStep("Política de Privacidad",
						() -> validateLegalLink(appPage, context, "Política de Privacidad", "Política de Privacidad", "09-politica"));

				writeFinalReport();
			}
		}

		assertTrue("One or more SaleADS workflow validations failed. Check target/saleads-artifacts/final-report.txt",
				allStepsPassed());
	}

	private void navigateToLoginIfProvided(final Page page) {
		final String startUrl = System.getenv("SALEADS_START_URL");
		if (startUrl != null && !startUrl.isBlank()) {
			page.navigate(startUrl.trim());
			waitForUi(page);
		}
	}

	private StepResult loginWithGoogleAndValidateShell(final Page page, final String email) {
		final List<String> failures = new ArrayList<>();
		if ("about:blank".equals(page.url()) && (System.getenv("SALEADS_START_URL") == null
				|| System.getenv("SALEADS_START_URL").isBlank())) {
			failures.add(
					"No login page available. Set SALEADS_START_URL or preload the browser on a SaleADS login page.");
			return StepResult.fromFailures(failures);
		}

		clickFirstVisible(page, "login with Google", List.of(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?i)(sign in|iniciar sesi[oó]n|continuar).*google"))),
				page.getByText(Pattern.compile("(?i)(sign in|iniciar sesi[oó]n|continuar).*google")),
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*")))), failures);

		maybeClickGoogleAccount(page, email);
		waitForUi(page);

		final boolean mainUi = isAnyVisible(List.of(page.locator("main"), page.getByRole(AriaRole.MAIN),
				page.getByText(Pattern.compile("(?i)(dashboard|panel|inicio)"))), SHORT_WAIT_MS);
		if (!mainUi) {
			failures.add("Main application interface did not appear.");
		}

		final boolean sidebarVisible = isAnyVisible(List.of(page.locator("aside"), page.getByRole(AriaRole.NAVIGATION),
				page.getByText(Pattern.compile("(?i)negocio"))), SHORT_WAIT_MS);
		if (!sidebarVisible) {
			failures.add("Left sidebar navigation is not visible.");
		}

		takeScreenshot(page, "01-dashboard-loaded", true);
		return StepResult.fromFailures(failures);
	}

	private StepResult openMiNegocioMenuAndValidate(final Page page) {
		final List<String> failures = new ArrayList<>();
		clickFirstVisible(page, "Negocio section",
				List.of(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Negocio")),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Negocio")),
						page.getByText("Negocio")),
				failures);

		clickFirstVisible(page, "Mi Negocio option",
				List.of(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Mi Negocio")),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Mi Negocio")),
						page.getByText("Mi Negocio")),
				failures);

		final boolean submenuExpanded = isAnyVisible(List.of(page.getByText("Agregar Negocio"), page.getByText("Administrar Negocios")),
				SHORT_WAIT_MS);
		if (!submenuExpanded) {
			failures.add("Mi Negocio submenu did not expand.");
		}
		if (!isVisible(page.getByText("Agregar Negocio"), SHORT_WAIT_MS)) {
			failures.add("'Agregar Negocio' is not visible.");
		}
		if (!isVisible(page.getByText("Administrar Negocios"), SHORT_WAIT_MS)) {
			failures.add("'Administrar Negocios' is not visible.");
		}

		takeScreenshot(page, "02-mi-negocio-menu-expanded", true);
		return StepResult.fromFailures(failures);
	}

	private StepResult validateAgregarNegocioModal(final Page page) {
		final List<String> failures = new ArrayList<>();
		clickFirstVisible(page, "Agregar Negocio action", List.of(page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName("Agregar Negocio")), page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName("Agregar Negocio")), page.getByText("Agregar Negocio")), failures);

		waitForUi(page);

		if (!isVisible(page.getByText("Crear Nuevo Negocio"), SHORT_WAIT_MS)) {
			failures.add("Modal title 'Crear Nuevo Negocio' is not visible.");
		}
		if (!isAnyVisible(List.of(page.getByLabel(Pattern.compile("(?i)nombre del negocio")),
				page.getByPlaceholder(Pattern.compile("(?i)nombre del negocio")),
				page.getByText("Nombre del Negocio")), SHORT_WAIT_MS)) {
			failures.add("Input field 'Nombre del Negocio' does not exist.");
		}
		if (!isVisible(page.getByText("Tienes 2 de 3 negocios"), SHORT_WAIT_MS)) {
			failures.add("Text 'Tienes 2 de 3 negocios' is not visible.");
		}
		if (!isVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")), SHORT_WAIT_MS)) {
			failures.add("Button 'Cancelar' is not present.");
		}
		if (!isVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")), SHORT_WAIT_MS)) {
			failures.add("Button 'Crear Negocio' is not present.");
		}

		takeScreenshot(page, "03-agregar-negocio-modal", true);

		fillFirstVisible(List.of(page.getByLabel(Pattern.compile("(?i)nombre del negocio")),
				page.getByPlaceholder(Pattern.compile("(?i)nombre del negocio")),
				page.locator("input").first()), "Negocio Prueba Automatización");
		clickFirstVisible(page, "Cancelar button",
				List.of(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
						page.getByText("Cancelar")),
				failures);
		return StepResult.fromFailures(failures);
	}

	private StepResult openAdministrarNegociosAndValidateSections(final Page page) {
		final List<String> failures = new ArrayList<>();
		clickIfVisible(page, List.of(page.getByText("Mi Negocio"), page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName("Mi Negocio")), page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName("Mi Negocio"))));

		clickFirstVisible(page, "Administrar Negocios option",
				List.of(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Administrar Negocios")),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Administrar Negocios")),
						page.getByText("Administrar Negocios")),
				failures);

		waitForUi(page);

		if (!isVisible(page.getByText("Información General"), SHORT_WAIT_MS)) {
			failures.add("Section 'Información General' does not exist.");
		}
		if (!isVisible(page.getByText("Detalles de la Cuenta"), SHORT_WAIT_MS)) {
			failures.add("Section 'Detalles de la Cuenta' does not exist.");
		}
		if (!isVisible(page.getByText("Tus Negocios"), SHORT_WAIT_MS)) {
			failures.add("Section 'Tus Negocios' does not exist.");
		}
		if (!isAnyVisible(List.of(page.getByText("Sección Legal"), page.getByText("Legal")), SHORT_WAIT_MS)) {
			failures.add("Section 'Sección Legal' does not exist.");
		}

		takeScreenshot(page, "04-administrar-negocios-account-page", true);
		return StepResult.fromFailures(failures);
	}

	private StepResult validateInformacionGeneral(final Page page, final String email) {
		final List<String> failures = new ArrayList<>();
		if (!isVisible(page.getByText("Información General"), SHORT_WAIT_MS)) {
			failures.add("'Información General' section heading is missing.");
		}
		if (!isVisible(page.getByText(Pattern.compile(".+@.+\\..+")), SHORT_WAIT_MS)
				&& !isVisible(page.getByText(email), SHORT_WAIT_MS)) {
			failures.add("User email is not visible.");
		}
		if (!isAnyVisible(List.of(page.getByText("BUSINESS PLAN"), page.getByText(Pattern.compile("(?i)business plan"))),
				SHORT_WAIT_MS)) {
			failures.add("Text 'BUSINESS PLAN' is not visible.");
		}
		if (!isVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")), SHORT_WAIT_MS)) {
			failures.add("Button 'Cambiar Plan' is not visible.");
		}

		// User name is dynamic; validate there is at least one non-empty profile-like title text.
		if (!isAnyVisible(List.of(page.locator("h1"), page.locator("h2"), page.locator("[data-testid*='name']")),
				SHORT_WAIT_MS)) {
			failures.add("User name is not visible.");
		}

		return StepResult.fromFailures(failures);
	}

	private StepResult validateDetallesCuenta(final Page page) {
		final List<String> failures = new ArrayList<>();
		if (!isVisible(page.getByText("Cuenta creada"), SHORT_WAIT_MS)) {
			failures.add("'Cuenta creada' is not visible.");
		}
		if (!isVisible(page.getByText("Estado activo"), SHORT_WAIT_MS)) {
			failures.add("'Estado activo' is not visible.");
		}
		if (!isVisible(page.getByText("Idioma seleccionado"), SHORT_WAIT_MS)) {
			failures.add("'Idioma seleccionado' is not visible.");
		}
		return StepResult.fromFailures(failures);
	}

	private StepResult validateTusNegocios(final Page page) {
		final List<String> failures = new ArrayList<>();
		if (!isVisible(page.getByText("Tus Negocios"), SHORT_WAIT_MS)) {
			failures.add("'Tus Negocios' section title is not visible.");
		}
		if (!isAnyVisible(List.of(page.locator("table"), page.locator("ul li"), page.locator("[data-testid*='business']")),
				SHORT_WAIT_MS)) {
			failures.add("Business list is not visible.");
		}
		if (!isVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")), SHORT_WAIT_MS)) {
			failures.add("Button 'Agregar Negocio' does not exist.");
		}
		if (!isVisible(page.getByText("Tienes 2 de 3 negocios"), SHORT_WAIT_MS)) {
			failures.add("Text 'Tienes 2 de 3 negocios' is not visible.");
		}
		return StepResult.fromFailures(failures);
	}

	private StepResult validateLegalLink(final Page appPage, final BrowserContext context, final String linkText,
			final String expectedHeading, final String screenshotName) {
		final List<String> failures = new ArrayList<>();
		Page targetPage = appPage;
		final int pagesBefore = context.pages().size();

		final Locator link = firstVisible(List.of(
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkText)),
				appPage.getByText(linkText)));
		if (link == null) {
			failures.add("Link '" + linkText + "' is not visible.");
			return StepResult.fromFailures(failures);
		}

		link.click(new Locator.ClickOptions().setTimeout(WAIT_MS));
		waitForUi(appPage);

		if (context.pages().size() > pagesBefore) {
			targetPage = context.pages().get(context.pages().size() - 1);
			waitForUi(targetPage);
		}

		if (!isVisible(targetPage.getByText(expectedHeading), SHORT_WAIT_MS)) {
			failures.add("Heading '" + expectedHeading + "' is not visible.");
		}
		if (!isAnyVisible(List.of(targetPage.locator("article"), targetPage.locator("main p"), targetPage.locator("p")),
				SHORT_WAIT_MS)) {
			failures.add("Legal content text is not visible.");
		}

		takeScreenshot(targetPage, screenshotName, true);
		failures.addAll(writeLegalUrlNote(expectedHeading, targetPage.url()));

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		}

		return StepResult.fromFailures(failures);
	}

	private List<String> writeLegalUrlNote(final String heading, final String url) {
		final List<String> messages = new ArrayList<>();
		try {
			final String line = heading + " URL: " + url + System.lineSeparator();
			Files.writeString(artifactsDir.resolve("legal-urls.txt"), line, StandardCharsets.UTF_8,
					java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		} catch (IOException e) {
			messages.add("Could not write URL evidence for '" + heading + "': " + e.getMessage());
		}
		return messages;
	}

	private void runStep(final String name, final StepSupplier supplier) {
		try {
			report.put(name, supplier.run());
		} catch (Exception ex) {
			report.put(name, StepResult.fail("Unexpected error: " + ex.getMessage()));
		}
	}

	private void clickFirstVisible(final Page page, final String action, final List<Locator> candidates,
			final List<String> failures) {
		final Locator locator = firstVisible(candidates);
		if (locator == null) {
			failures.add("Could not find element for action: " + action + ".");
			return;
		}

		locator.click(new Locator.ClickOptions().setTimeout(WAIT_MS));
		waitForUi(page);
	}

	private void fillFirstVisible(final List<Locator> candidates, final String value) {
		final Locator locator = firstVisible(candidates);
		if (locator != null) {
			locator.fill(value, new Locator.FillOptions().setTimeout(WAIT_MS));
		}
	}

	private void clickIfVisible(final Page page, final List<Locator> candidates) {
		final Locator locator = firstVisible(candidates);
		if (locator != null) {
			locator.click(new Locator.ClickOptions().setTimeout(SHORT_WAIT_MS));
			waitForUi(page);
		}
	}

	private void maybeClickGoogleAccount(final Page page, final String email) {
		final Locator emailOption = firstVisible(List.of(page.getByText(email), page.getByRole(AriaRole.LINK,
				new Page.GetByRoleOptions().setName(email)), page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(email))));
		if (emailOption != null) {
			emailOption.click(new Locator.ClickOptions().setTimeout(SHORT_WAIT_MS));
			waitForUi(page);
		}
	}

	private Locator firstVisible(final List<Locator> candidates) {
		for (Locator candidate : candidates) {
			final Locator first = candidate.first();
			if (isVisible(first, SHORT_WAIT_MS)) {
				return first;
			}
		}
		return null;
	}

	private boolean isAnyVisible(final List<Locator> candidates, final int timeoutMs) {
		for (Locator candidate : candidates) {
			if (isVisible(candidate.first(), timeoutMs)) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			return locator.isVisible(new Locator.IsVisibleOptions().setTimeout((double) timeoutMs));
		} catch (Exception ignored) {
			return false;
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout((double) WAIT_MS));
		} catch (Exception ignored) {
			// Some interactions are client-side only; continue with fallback wait.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout((double) WAIT_MS));
		} catch (Exception ignored) {
			// NETWORKIDLE may not be reached in apps with long polling.
		}
		page.waitForTimeout(500);
	}

	private void takeScreenshot(final Page page, final String name, final boolean fullPage) {
		try {
			final Path screenshotPath = artifactsDir.resolve(name + ".png");
			page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		} catch (Exception ignored) {
			// Keep workflow running even if screenshot capture fails.
		}
	}

	private Path initArtifactsDir() throws IOException {
		final String configuredDir = System.getenv("SALEADS_E2E_ARTIFACTS_DIR");
		final Path base = configuredDir == null || configuredDir.isBlank() ? Paths.get("target", "saleads-artifacts")
				: Paths.get(configuredDir);
		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path runDir = base.resolve(runId);
		Files.createDirectories(runDir);
		return runDir;
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder content = new StringBuilder();
		content.append("SaleADS Mi Negocio Full Test Report").append(System.lineSeparator());
		content.append("Generated: ").append(LocalDateTime.now()).append(System.lineSeparator());
		content.append("Artifacts: ").append(artifactsDir.toAbsolutePath()).append(System.lineSeparator())
				.append(System.lineSeparator());

		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			content.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().passed ? "PASS" : "FAIL");
			if (entry.getValue().details != null && !entry.getValue().details.isBlank()) {
				content.append(" -> ").append(entry.getValue().details);
			}
			content.append(System.lineSeparator());
		}

		final Path reportPath = artifactsDir.resolve("final-report.txt");
		Files.writeString(reportPath, content.toString(), StandardCharsets.UTF_8);
		System.out.println(content);
	}

	private boolean allStepsPassed() {
		for (StepResult result : report.values()) {
			if (!result.passed) {
				return false;
			}
		}
		return true;
	}

	@FunctionalInterface
	private interface StepSupplier {
		StepResult run();
	}

	private static class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult fromFailures(final List<String> failures) {
			return failures.isEmpty() ? new StepResult(true, "All validations passed.")
					: new StepResult(false, String.join(" ", failures));
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
