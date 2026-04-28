package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.LoadState;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * End-to-end validation of the SaleADS Mi Negocio module workflow.
 * <p>
 * The test does not hardcode any SaleADS domain. Use one of these to provide
 * the current environment login URL when needed:
 * <ul>
 * <li>Environment variable: SALEADS_URL</li>
 * <li>JVM property: -Dsaleads.url=...</li>
 * </ul>
 * If omitted, this test expects a pre-opened login page context from the
 * runtime environment.
 */
public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final int DEFAULT_TIMEOUT_MS = 45_000;
	private static final int QUICK_TIMEOUT_MS = 5_000;
	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
		.withZone(ZoneOffset.UTC);
	private static final Pattern EMAIL_PATTERN = Pattern
		.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern LOGIN_BUTTON_PATTERN = Pattern.compile(
		"(iniciar sesi[oó]n con google|sign in with google|continuar con google|google)",
		Pattern.CASE_INSENSITIVE);

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String runId = TS_FORMATTER.format(Instant.now());
		final Path outputDir = Paths.get("target", "saleads-mi-negocio", runId);
		final Path screenshotsDir = outputDir.resolve("screenshots");
		Files.createDirectories(screenshotsDir);

		final Map<String, StepResult> report = new LinkedHashMap<>();
		report.put("Login", StepResult.pending());
		report.put("Mi Negocio menu", StepResult.pending());
		report.put("Agregar Negocio modal", StepResult.pending());
		report.put("Administrar Negocios view", StepResult.pending());
		report.put("Información General", StepResult.pending());
		report.put("Detalles de la Cuenta", StepResult.pending());
		report.put("Tus Negocios", StepResult.pending());
		report.put("Términos y Condiciones", StepResult.pending());
		report.put("Política de Privacidad", StepResult.pending());

		final AtomicReference<String> termsUrl = new AtomicReference<>("");
		final AtomicReference<String> privacyUrl = new AtomicReference<>("");
		final AtomicReference<String> administrarNegociosUrl = new AtomicReference<>("");

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
				.setHeadless(resolveHeadless())
				.setTimeout((double) DEFAULT_TIMEOUT_MS));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page page = context.newPage();
			page.setDefaultTimeout((double) DEFAULT_TIMEOUT_MS);

			try {
				final String configuredUrl = resolveUrl();
				Assume.assumeTrue("Set SALEADS_URL env var or -Dsaleads.url to run this E2E workflow test.",
					configuredUrl != null && !configuredUrl.isBlank());
				page.navigate(configuredUrl);
				waitForUi(page);

				runStep("Login", report, () -> {
					final Locator loginButton = firstVisible(page,
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN)).first(),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN)).first(),
						page.getByText(LOGIN_BUTTON_PATTERN).first());

					clickAndWait(page, loginButton);
					selectGoogleAccountIfVisible(context, page, GOOGLE_ACCOUNT_EMAIL);

					final Locator sidebar = firstVisible(page, page.locator("aside").first(),
						page.getByRole(AriaRole.NAVIGATION).first(),
						page.locator("nav").filter(new Locator.FilterOptions().setHasText(Pattern.compile("Negocio",
							Pattern.CASE_INSENSITIVE))).first());
					mustBeVisible(sidebar, "Left sidebar navigation");
					screenshot(page, screenshotsDir.resolve("01-dashboard-loaded.png"), false);
				});

				runStep("Mi Negocio menu", report, () -> {
					openMiNegocioMenu(page);

					mustBeVisible(page.getByText(Pattern.compile("^Agregar\\s+Negocio$", Pattern.CASE_INSENSITIVE)).first(),
						"'Agregar Negocio' option");
					mustBeVisible(
						page.getByText(Pattern.compile("^Administrar\\s+Negocios$", Pattern.CASE_INSENSITIVE)).first(),
						"'Administrar Negocios' option");

					screenshot(page, screenshotsDir.resolve("02-mi-negocio-menu-expanded.png"), false);
				});

				runStep("Agregar Negocio modal", report, () -> {
					final Locator agregarNegocio = firstVisible(page,
						page.getByRole(AriaRole.MENUITEM,
							new Page.GetByRoleOptions().setName(Pattern.compile("^Agregar\\s+Negocio$",
								Pattern.CASE_INSENSITIVE))).first(),
						page.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("^Agregar\\s+Negocio$",
								Pattern.CASE_INSENSITIVE))).first(),
						page.getByText(Pattern.compile("^Agregar\\s+Negocio$", Pattern.CASE_INSENSITIVE)).first());
					clickAndWait(page, agregarNegocio);

					final Locator modal = page.getByRole(AriaRole.DIALOG).first();
					mustBeVisible(modal, "Crear Nuevo Negocio modal");
					mustBeVisible(modal.getByText(Pattern.compile("Crear\\s+Nuevo\\s+Negocio", Pattern.CASE_INSENSITIVE)).first(),
						"Modal title 'Crear Nuevo Negocio'");
					final Locator nombreNegocioInput = firstVisible(page,
						modal.getByLabel(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE)).first(),
						modal.getByPlaceholder(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE)).first(),
						modal.locator("input").first());
					mustBeVisible(nombreNegocioInput, "'Nombre del Negocio' input");
					mustBeVisible(modal.getByText(Pattern.compile("Tienes\\s+2\\s+de\\s+3\\s+negocios", Pattern.CASE_INSENSITIVE))
						.first(), "Usage text 'Tienes 2 de 3 negocios'");
					mustBeVisible(modal.getByRole(AriaRole.BUTTON,
						new Locator.GetByRoleOptions().setName(Pattern.compile("^Cancelar$", Pattern.CASE_INSENSITIVE))).first(),
						"Cancelar button");
					mustBeVisible(modal.getByRole(AriaRole.BUTTON,
						new Locator.GetByRoleOptions()
							.setName(Pattern.compile("^Crear\\s+Negocio$", Pattern.CASE_INSENSITIVE))).first(),
						"Crear Negocio button");

					screenshot(page, screenshotsDir.resolve("03-agregar-negocio-modal.png"), false);

					// Optional interaction requested by workflow.
					nombreNegocioInput.click();
					nombreNegocioInput.fill("Negocio Prueba Automatización");
					clickAndWait(page, modal.getByRole(AriaRole.BUTTON,
						new Locator.GetByRoleOptions().setName(Pattern.compile("^Cancelar$", Pattern.CASE_INSENSITIVE))).first());
				});

				runStep("Administrar Negocios view", report, () -> {
					ensureMenuItemVisible(page, Pattern.compile("^Administrar\\s+Negocios$", Pattern.CASE_INSENSITIVE));
					final Locator administrarNegocios = firstVisible(page,
						page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions()
							.setName(Pattern.compile("^Administrar\\s+Negocios$", Pattern.CASE_INSENSITIVE))).first(),
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
							.setName(Pattern.compile("^Administrar\\s+Negocios$", Pattern.CASE_INSENSITIVE))).first(),
						page.getByText(Pattern.compile("^Administrar\\s+Negocios$", Pattern.CASE_INSENSITIVE)).first());
					clickAndWait(page, administrarNegocios);

					mustBeVisible(page.getByText(Pattern.compile("Informaci[oó]n\\s+General", Pattern.CASE_INSENSITIVE)).first(),
						"Sección 'Información General'");
					mustBeVisible(page.getByText(Pattern.compile("Detalles\\s+de\\s+la\\s+Cuenta", Pattern.CASE_INSENSITIVE)).first(),
						"Sección 'Detalles de la Cuenta'");
					mustBeVisible(page.getByText(Pattern.compile("Tus\\s+Negocios", Pattern.CASE_INSENSITIVE)).first(),
						"Sección 'Tus Negocios'");
					mustBeVisible(page.getByText(Pattern.compile("Secci[oó]n\\s+Legal", Pattern.CASE_INSENSITIVE)).first(),
						"Sección 'Sección Legal'");

					administrarNegociosUrl.set(page.url());
					screenshot(page, screenshotsDir.resolve("04-administrar-negocios.png"), true);
				});

				runStep("Información General", report, () -> {
					final Locator section = sectionFor(page, "Informaci[oó]n\\s+General");
					final String content = section.innerText();

					assertTrue("Expected BUSINESS PLAN text to be visible",
						Pattern.compile("BUSINESS\\s+PLAN", Pattern.CASE_INSENSITIVE).matcher(content).find());
					assertTrue("Expected Cambiar Plan button to be visible",
						Pattern.compile("Cambiar\\s+Plan", Pattern.CASE_INSENSITIVE).matcher(content).find());

					final Matcher emailMatcher = EMAIL_PATTERN.matcher(content);
					assertTrue("Expected user email to be visible in Información General", emailMatcher.find());
					assertHasLikelyPersonName(content);
				});

				runStep("Detalles de la Cuenta", report, () -> {
					final Locator section = sectionFor(page, "Detalles\\s+de\\s+la\\s+Cuenta");
					final String content = section.innerText();
					assertTrue("Expected 'Cuenta creada' in Detalles de la Cuenta",
						Pattern.compile("Cuenta\\s+creada", Pattern.CASE_INSENSITIVE).matcher(content).find());
					assertTrue("Expected 'Estado activo' in Detalles de la Cuenta",
						Pattern.compile("Estado\\s+activo", Pattern.CASE_INSENSITIVE).matcher(content).find());
					assertTrue("Expected 'Idioma seleccionado' in Detalles de la Cuenta",
						Pattern.compile("Idioma\\s+seleccionado", Pattern.CASE_INSENSITIVE).matcher(content).find());
				});

				runStep("Tus Negocios", report, () -> {
					final Locator section = sectionFor(page, "Tus\\s+Negocios");
					mustBeVisible(section, "'Tus Negocios' section");
					mustBeVisible(section.getByRole(AriaRole.BUTTON,
						new Locator.GetByRoleOptions().setName(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE)))
						.first(), "'Agregar Negocio' button in Tus Negocios");
					assertTrue("Expected 'Tienes 2 de 3 negocios' in Tus Negocios",
						Pattern.compile("Tienes\\s+2\\s+de\\s+3\\s+negocios", Pattern.CASE_INSENSITIVE)
							.matcher(section.innerText()).find());
				});

				runStep("Términos y Condiciones", report, () -> {
					final LegalValidationResult result = openAndValidateLegalDocument(page, context,
						Pattern.compile("T[eé]rminos\\s+y\\s+Condiciones", Pattern.CASE_INSENSITIVE),
						Pattern.compile("T[eé]rminos\\s+y\\s+Condiciones", Pattern.CASE_INSENSITIVE),
						screenshotsDir.resolve("05-terminos-y-condiciones.png"), administrarNegociosUrl.get());
					termsUrl.set(result.finalUrl);
				});

				runStep("Política de Privacidad", report, () -> {
					final LegalValidationResult result = openAndValidateLegalDocument(page, context,
						Pattern.compile("Pol[ií]tica\\s+de\\s+Privacidad", Pattern.CASE_INSENSITIVE),
						Pattern.compile("Pol[ií]tica\\s+de\\s+Privacidad", Pattern.CASE_INSENSITIVE),
						screenshotsDir.resolve("06-politica-de-privacidad.png"), administrarNegociosUrl.get());
					privacyUrl.set(result.finalUrl);
				});
			} finally {
				context.close();
				browser.close();
			}
		}

		final Path reportPath = outputDir.resolve("final-report.md");
		writeReport(reportPath, report, termsUrl.get(), privacyUrl.get(), screenshotsDir);

		final boolean allPassed = report.values().stream().allMatch(StepResult::isPassed);
		assertTrue("One or more SaleADS Mi Negocio validations failed. See report: " + reportPath, allPassed);
	}

	private static void openMiNegocioMenu(final Page page) {
		final Locator negocio = firstVisible(page,
			page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^Negocio$",
				Pattern.CASE_INSENSITIVE))).first(),
			page.getByText(Pattern.compile("^Negocio$", Pattern.CASE_INSENSITIVE)).first());
		clickAndWait(page, negocio);

		final Locator miNegocio = firstVisible(page,
			page.getByRole(AriaRole.MENUITEM,
				new Page.GetByRoleOptions().setName(Pattern.compile("^Mi\\s+Negocio$", Pattern.CASE_INSENSITIVE))).first(),
			page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("^Mi\\s+Negocio$", Pattern.CASE_INSENSITIVE))).first(),
			page.getByText(Pattern.compile("^Mi\\s+Negocio$", Pattern.CASE_INSENSITIVE)).first());
		clickAndWait(page, miNegocio);
	}

	private static void ensureMenuItemVisible(final Page page, final Pattern itemPattern) {
		if (page.getByText(itemPattern).first().isVisible()) {
			return;
		}
		openMiNegocioMenu(page);
	}

	private static void selectGoogleAccountIfVisible(final BrowserContext context, final Page appPage, final String email) {
		Page googlePage = null;
		if (isGoogleUrl(appPage.url())) {
			googlePage = appPage;
		}

		if (googlePage == null) {
			for (int i = 0; i < 20; i++) {
				for (final Page candidate : context.pages()) {
					if (isGoogleUrl(candidate.url())) {
						googlePage = candidate;
						break;
					}
				}
				if (googlePage != null) {
					break;
				}
				appPage.waitForTimeout(500);
			}
		}

		if (googlePage == null) {
			waitForUi(appPage);
			return;
		}

		googlePage.bringToFront();
		waitForUi(googlePage);

		final Locator accountChoice = googlePage.getByText(Pattern.compile(Pattern.quote(email), Pattern.CASE_INSENSITIVE))
			.first();
		if (accountChoice.isVisible()) {
			clickAndWait(googlePage, accountChoice);
		}

		for (int i = 0; i < 60; i++) {
			if (!isGoogleUrl(appPage.url())) {
				break;
			}
			appPage.waitForTimeout(1_000);
		}

		appPage.bringToFront();
		waitForUi(appPage);
	}

	private static LegalValidationResult openAndValidateLegalDocument(final Page appPage, final BrowserContext context,
		final Pattern linkPattern, final Pattern headingPattern, final Path screenshotPath, final String fallbackAppUrl) {
		final Locator legalLink = firstVisible(appPage,
			appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)).first(),
			appPage.getByText(linkPattern).first());

		Page targetPage = appPage;
		boolean openedInPopup = false;

		try {
			final Page popup = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout((double) QUICK_TIMEOUT_MS),
				() -> legalLink.click());
			targetPage = popup;
			openedInPopup = true;
		} catch (final PlaywrightException popupTimeout) {
			targetPage = appPage;
		}

		waitForUi(targetPage);
		mustBeVisible(targetPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)).first(),
			"Expected legal page heading");

		final String content = targetPage.locator("body").innerText();
		assertTrue("Expected legal content text to be visible",
			content != null && content.replaceAll("\\s+", " ").trim().length() >= 120);

		screenshot(targetPage, screenshotPath, true);
		final String finalUrl = targetPage.url();

		if (openedInPopup) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				appPage.goBack(new Page.GoBackOptions().setTimeout((double) DEFAULT_TIMEOUT_MS));
				waitForUi(appPage);
			} catch (final PlaywrightException ignored) {
				if (fallbackAppUrl != null && !fallbackAppUrl.isBlank()) {
					appPage.navigate(fallbackAppUrl);
					waitForUi(appPage);
				}
			}
		}

		mustBeVisible(appPage.getByText(Pattern.compile("Secci[oó]n\\s+Legal", Pattern.CASE_INSENSITIVE)).first(),
			"Sección Legal after returning to app");
		return new LegalValidationResult(finalUrl);
	}

	private static Locator sectionFor(final Page page, final String headingRegex) {
		final Locator heading = page.getByText(Pattern.compile(headingRegex, Pattern.CASE_INSENSITIVE)).first();
		mustBeVisible(heading, "Expected heading " + headingRegex);

		final Locator section = heading.locator("xpath=ancestor::*[self::section or self::div][1]");
		mustBeVisible(section, "Section container for heading " + headingRegex);
		return section;
	}

	private static void assertHasLikelyPersonName(final String content) {
		final String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
		final String[] tokens = normalized.split(" ");
		int candidateTokens = 0;
		for (final String token : tokens) {
			if (token.matches("[\\p{L}][\\p{L}'-]{2,}") && !token.equalsIgnoreCase("BUSINESS")
				&& !token.equalsIgnoreCase("PLAN") && !token.equalsIgnoreCase("Cambiar")
				&& !token.equalsIgnoreCase("Información") && !token.equalsIgnoreCase("General")) {
				candidateTokens++;
			}
		}
		assertTrue("Expected a likely user name to be visible in Información General", candidateTokens >= 2);
	}

	private static boolean isGoogleUrl(final String url) {
		return url != null && url.contains("accounts.google.com");
	}

	private static boolean resolveHeadless() {
		final String env = System.getenv("PLAYWRIGHT_HEADLESS");
		if (env == null || env.isBlank()) {
			return true;
		}
		return Boolean.parseBoolean(env);
	}

	private static String resolveUrl() {
		final String envUrl = System.getenv("SALEADS_URL");
		if (envUrl != null && !envUrl.isBlank()) {
			return envUrl.trim();
		}

		final String propUrl = System.getProperty("saleads.url");
		if (propUrl != null && !propUrl.isBlank()) {
			return propUrl.trim();
		}

		return null;
	}

	private static void runStep(final String stepName, final Map<String, StepResult> report, final CheckedRunnable action) {
		try {
			action.run();
			report.put(stepName, StepResult.pass());
		} catch (final Throwable t) {
			report.put(stepName, StepResult.fail(t.getMessage()));
		}
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout((double) QUICK_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// Network idle can be noisy for SPA applications. Short delay still enforces post-click settle.
		}
		page.waitForTimeout(600);
	}

	private static void clickAndWait(final Page page, final Locator locator) {
		mustBeVisible(locator, "Clickable element");
		locator.click();
		waitForUi(page);
	}

	private static Locator firstVisible(final Page page, final Locator... candidates) {
		final long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
		while (System.currentTimeMillis() < deadline) {
			for (final Locator candidate : candidates) {
				try {
					if (candidate != null && candidate.isVisible()) {
						return candidate;
					}
				} catch (final PlaywrightException ignored) {
					// Keep polling for dynamic UI.
				}
			}
			page.waitForTimeout(250);
		}

		throw new AssertionError("Could not find a visible element among candidates within timeout.");
	}

	private static void mustBeVisible(final Locator locator, final String name) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE)
				.setTimeout((double) DEFAULT_TIMEOUT_MS));
		} catch (final PlaywrightException e) {
			throw new AssertionError("Expected visible element: " + name, e);
		}
	}

	private static void screenshot(final Page page, final Path path, final boolean fullPage) {
		try {
			Files.createDirectories(path.getParent());
			page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
		} catch (final IOException e) {
			throw new RuntimeException("Could not create screenshot directories for " + path, e);
		}
	}

	private static void writeReport(final Path reportPath, final Map<String, StepResult> report, final String termsUrl,
		final String privacyUrl, final Path screenshotsDir) throws IOException {
		final StringBuilder md = new StringBuilder();
		md.append("# SaleADS Mi Negocio Full Test Report\n\n");
		md.append("- Generated (UTC): ").append(Instant.now()).append('\n');
		md.append("- Screenshots directory: `").append(screenshotsDir).append("`\n\n");
		md.append("| Validation | Status | Notes |\n");
		md.append("|---|---|---|\n");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final StepResult result = entry.getValue();
			md.append("| ").append(entry.getKey()).append(" | ").append(result.passed ? "PASS" : "FAIL").append(" | ")
				.append(escapeTable(result.notes)).append(" |\n");
		}
		md.append("\n## Legal URLs\n\n");
		md.append("- Términos y Condiciones: ").append(termsUrl == null || termsUrl.isBlank() ? "N/A" : termsUrl).append('\n');
		md.append("- Política de Privacidad: ").append(privacyUrl == null || privacyUrl.isBlank() ? "N/A" : privacyUrl)
			.append('\n');

		Files.createDirectories(reportPath.getParent());
		Files.writeString(reportPath, md.toString(), StandardCharsets.UTF_8);
	}

	private static String escapeTable(final String value) {
		if (value == null || value.isBlank()) {
			return "-";
		}
		return value.replace("|", "\\|").replace("\n", " ").trim();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class LegalValidationResult {
		private final String finalUrl;

		private LegalValidationResult(final String finalUrl) {
			this.finalUrl = finalUrl;
		}
	}

	private static final class StepResult {
		private final boolean passed;
		private final String notes;

		private StepResult(final boolean passed, final String notes) {
			this.passed = passed;
			this.notes = notes;
		}

		private static StepResult pending() {
			return new StepResult(false, "Not executed");
		}

		private static StepResult pass() {
			return new StepResult(true, "OK");
		}

		private static StepResult fail(final String notes) {
			return new StepResult(false, notes == null ? "Unknown failure" : notes);
		}

		private boolean isPassed() {
			return passed;
		}
	}
}
