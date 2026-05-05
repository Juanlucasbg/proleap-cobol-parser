package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final Pattern TEXT_TERMINOS = Pattern.compile("T[ée]rminos y Condiciones", Pattern.CASE_INSENSITIVE);
	private static final Pattern TEXT_POLITICA = Pattern.compile("Pol[íi]tica de Privacidad", Pattern.CASE_INSENSITIVE);

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final boolean enabled = Boolean.parseBoolean(getEnv("SALEADS_E2E_ENABLED").orElse("false"));
		Assume.assumeTrue(
			"Skipping SaleADS Mi Negocio E2E test. Set SALEADS_E2E_ENABLED=true to execute this test.",
			enabled
		);

		final Path artifactDir = createArtifactDirectory();
		final LinkedHashMap<String, Boolean> report = createEmptyReport();
		final LinkedHashMap<String, String> details = new LinkedHashMap<>();
		final AtomicReference<String> termsUrl = new AtomicReference<>("N/A");
		final AtomicReference<String> privacyUrl = new AtomicReference<>("N/A");
		Exception fatalError = null;

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
				.setHeadless(Boolean.parseBoolean(getEnv("SALEADS_HEADLESS").orElse("true")))
			);
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page page = context.newPage();

			openLoginPageIfProvided(page);

			report.put("Login", runStep(() -> {
				clickLoginWithGoogle(page);
				selectGoogleAccountIfVisible(page);
				assertMainInterfaceLoaded(page);
				screenshot(page, artifactDir, "01-dashboard-loaded.png", false);
			}, details, "Login"));

			report.put("Mi Negocio menu", runStep(() -> {
				openMiNegocioMenu(page);
				assertVisibleText(page, "Agregar Negocio");
				assertVisibleText(page, "Administrar Negocios");
				screenshot(page, artifactDir, "02-mi-negocio-menu-expanded.png", false);
			}, details, "Mi Negocio menu"));

			report.put("Agregar Negocio modal", runStep(() -> {
				clickByVisibleText(page, "Agregar Negocio");
				waitForUiAfterClick(page);
				assertVisibleText(page, "Crear Nuevo Negocio");
				assertVisibleText(page, "Nombre del Negocio");
				assertVisibleText(page, "Tienes 2 de 3 negocios");
				assertVisibleText(page, "Cancelar");
				assertVisibleText(page, "Crear Negocio");

				final Locator businessNameInput = firstVisible(
					page.getByLabel("Nombre del Negocio"),
					page.locator("input[placeholder*='Nombre del Negocio' i]"),
					page.locator("input[name*='negocio' i]")
				);
				businessNameInput.click();
				waitForUiAfterClick(page);
				businessNameInput.fill("Negocio Prueba Automatización");

				screenshot(page, artifactDir, "03-agregar-negocio-modal.png", false);

				clickByVisibleText(page, "Cancelar");
				waitForUiAfterClick(page);
			}, details, "Agregar Negocio modal"));

			report.put("Administrar Negocios view", runStep(() -> {
				openMiNegocioMenu(page);
				clickByVisibleText(page, "Administrar Negocios");
				waitForUiAfterClick(page);
				assertVisibleText(page, "Información General");
				assertVisibleText(page, "Detalles de la Cuenta");
				assertVisibleText(page, "Tus Negocios");
				assertVisibleText(page, "Sección Legal");
				screenshot(page, artifactDir, "04-administrar-negocios-page.png", true);
			}, details, "Administrar Negocios view"));

			report.put("Información General", runStep(() -> {
				assertAnyVisible(page,
					"BUSINESS PLAN",
					"Business Plan"
				);
				assertVisibleText(page, "Cambiar Plan");
				assertAnyVisibleByPattern(page,
					Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
				);
				assertAnyVisibleByPattern(page,
					Pattern.compile("[A-Za-z]{2,}\\s+[A-Za-z]{2,}")
				);
			}, details, "Información General"));

			report.put("Detalles de la Cuenta", runStep(() -> {
				assertVisibleText(page, "Cuenta creada");
				assertAnyVisible(page, "Estado activo", "Estado Activo");
				assertVisibleText(page, "Idioma seleccionado");
			}, details, "Detalles de la Cuenta"));

			report.put("Tus Negocios", runStep(() -> {
				assertVisibleText(page, "Tus Negocios");
				assertVisibleText(page, "Agregar Negocio");
				assertVisibleText(page, "Tienes 2 de 3 negocios");
				assertAnyVisibleByPattern(page,
					Pattern.compile("negocio", Pattern.CASE_INSENSITIVE)
				);
			}, details, "Tus Negocios"));

			report.put("Términos y Condiciones", runStep(() -> {
				final Page legalPage = openPossiblyNewTab(page, () -> clickByPattern(page, TEXT_TERMINOS));
				waitForUiAfterClick(legalPage);
				assertAnyVisibleByPattern(legalPage, TEXT_TERMINOS);
				assertAnyVisibleByPattern(legalPage, Pattern.compile(".{30,}"));
				termsUrl.set(legalPage.url());
				screenshot(legalPage, artifactDir, "08-terminos-y-condiciones.png", true);
				returnToApplicationTab(page, legalPage);
			}, details, "Términos y Condiciones"));

			report.put("Política de Privacidad", runStep(() -> {
				final Page legalPage = openPossiblyNewTab(page, () -> clickByPattern(page, TEXT_POLITICA));
				waitForUiAfterClick(legalPage);
				assertAnyVisibleByPattern(legalPage, TEXT_POLITICA);
				assertAnyVisibleByPattern(legalPage, Pattern.compile(".{30,}"));
				privacyUrl.set(legalPage.url());
				screenshot(legalPage, artifactDir, "09-politica-de-privacidad.png", true);
				returnToApplicationTab(page, legalPage);
			}, details, "Política de Privacidad"));

			context.close();
			browser.close();
		} catch (Exception ex) {
			fatalError = ex;
			details.put("Fatal error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}

		try {
			writeFinalReport(report, details, termsUrl.get(), privacyUrl.get(), artifactDir);
		} catch (IOException reportError) {
			if (fatalError != null) {
				fatalError.addSuppressed(reportError);
			} else {
				throw reportError;
			}
		}

		if (fatalError != null) {
			throw fatalError;
		}
		final boolean allStepsPassed = report.values().stream().allMatch(Boolean::booleanValue);
		if (!allStepsPassed) {
			throw new AssertionError("One or more Mi Negocio workflow validations failed. "
				+ "Review artifacts at: " + artifactDir.toAbsolutePath());
		}
	}

	private static void openLoginPageIfProvided(final Page page) {
		final Optional<String> loginUrl = getEnv("SALEADS_LOGIN_URL");
		final Optional<String> baseUrl = getEnv("SALEADS_BASE_URL");
		if (loginUrl.isPresent() && !loginUrl.get().isBlank()) {
			page.navigate(loginUrl.get());
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
			return;
		}
		if (baseUrl.isPresent() && !baseUrl.get().isBlank()) {
			page.navigate(baseUrl.get());
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
			return;
		}
		throw new IllegalStateException(
			"No URL provided. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL to run in any SaleADS environment."
		);
	}

	private static void clickLoginWithGoogle(final Page page) {
		final Locator loginButton = firstVisible(
			page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Google|Iniciar sesi[óo]n|Sign in", Pattern.CASE_INSENSITIVE))),
			page.getByText(Pattern.compile("Sign in with Google|Continuar con Google|Google", Pattern.CASE_INSENSITIVE)),
			page.locator("button:has-text('Google')"),
			page.locator("[role='button']:has-text('Google')")
		);
		loginButton.click();
		waitForUiAfterClick(page);
	}

	private static void selectGoogleAccountIfVisible(final Page appPage) {
		final Pattern accountPattern = Pattern.compile("juanlucasbarbiergarzon@gmail.com", Pattern.CASE_INSENSITIVE);
		Page googlePage = appPage;

		try {
			final Locator accountOnAppPage = googlePage.getByText(accountPattern).first();
			if (accountOnAppPage.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
				accountOnAppPage.click();
				waitForUiAfterClick(googlePage);
				return;
			}
		} catch (PlaywrightException ignored) {
			// If account selector is not present, flow might be direct login.
		}

		appPage.waitForTimeout(1500);
		for (final Page candidatePage : appPage.context().pages()) {
			final String currentUrl = candidatePage.url();
			if (currentUrl != null && currentUrl.contains("accounts.google.com")) {
				googlePage = candidatePage;
				break;
			}
		}

		final Locator accountOption = googlePage.getByText(accountPattern).first();
		if (accountOption.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
			accountOption.click();
			waitForUiAfterClick(googlePage);
		}
	}

	private static void assertMainInterfaceLoaded(final Page page) {
		waitForUiAfterClick(page);
		assertAnyVisible(page, "Negocio", "Mi Negocio");
		final Locator sidebar = firstVisible(
			page.locator("aside"),
			page.locator("nav"),
			page.getByRole(AriaRole.NAVIGATION)
		);
		if (!sidebar.isVisible(new Locator.IsVisibleOptions().setTimeout(10000))) {
			throw new AssertionError("Left sidebar navigation is not visible after login.");
		}
	}

	private static void openMiNegocioMenu(final Page page) {
		assertAnyVisible(page, "Negocio", "Mi Negocio");
		if (isTextVisible(page, "Agregar Negocio", 1500) && isTextVisible(page, "Administrar Negocios", 1500)) {
			return;
		}

		final Locator menuButton = firstVisible(
			page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("Mi Negocio", Pattern.CASE_INSENSITIVE))),
			page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Mi Negocio", Pattern.CASE_INSENSITIVE))),
			page.getByText(Pattern.compile("Mi Negocio", Pattern.CASE_INSENSITIVE))
		);
		menuButton.click();
		waitForUiAfterClick(page);
	}

	private static Page openPossiblyNewTab(final Page appPage, final Runnable clickAction) {
		try {
			return appPage.context().waitForPage(
				new BrowserContext.WaitForPageOptions().setTimeout(Duration.ofSeconds(5).toMillis()),
				() -> {
					clickAction.run();
					waitForUiAfterClick(appPage);
				}
			);
		} catch (PlaywrightException ignored) {
			// Link might open in the same tab.
			clickAction.run();
			waitForUiAfterClick(appPage);
			return appPage;
		}
	}

	private static void returnToApplicationTab(final Page appPage, final Page legalPage) {
		if (legalPage != appPage && !legalPage.isClosed()) {
			legalPage.close();
		}
		appPage.bringToFront();
		waitForUiAfterClick(appPage);
	}

	private static void clickByVisibleText(final Page page, final String text) {
		final Locator target = firstVisible(
			page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))),
			page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))),
			page.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))
		);
		target.click();
		waitForUiAfterClick(page);
	}

	private static boolean isTextVisible(final Page page, final String text, final double timeoutMs) {
		try {
			return page.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))
				.first()
				.isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private static void clickByPattern(final Page page, final Pattern pattern) {
		final Locator target = firstVisible(
			page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)),
			page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)),
			page.getByText(pattern)
		);
		target.click();
		waitForUiAfterClick(page);
	}

	private static void assertVisibleText(final Page page, final String text) {
		final Locator match = page.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)).first();
		if (!match.isVisible(new Locator.IsVisibleOptions().setTimeout(15000))) {
			throw new AssertionError("Expected visible text not found: " + text);
		}
	}

	private static void assertAnyVisible(final Page page, final String... options) {
		for (final String option : options) {
			final Locator candidate = page.getByText(Pattern.compile(Pattern.quote(option), Pattern.CASE_INSENSITIVE)).first();
			if (candidate.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
				return;
			}
		}
		throw new AssertionError("None of the expected texts are visible: " + String.join(", ", options));
	}

	private static void assertAnyVisibleByPattern(final Page page, final Pattern pattern) {
		final Locator candidate = page.getByText(pattern).first();
		if (!candidate.isVisible(new Locator.IsVisibleOptions().setTimeout(15000))) {
			throw new AssertionError("Expected content pattern not found: " + pattern.pattern());
		}
	}

	private static Locator firstVisible(final Locator... locators) {
		for (final Locator locator : locators) {
			try {
				final Locator candidate = locator.first();
				if (candidate.isVisible(new Locator.IsVisibleOptions().setTimeout(2500))) {
					return candidate;
				}
			} catch (PlaywrightException ignored) {
				// Continue with next candidate locator.
			}
		}
		throw new AssertionError("Could not find a visible locator among provided options.");
	}

	private static void waitForUiAfterClick(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
			// Some pages keep websocket/network activity alive permanently.
		}
	}

	private static void screenshot(final Page page, final Path artifactDir, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
			.setPath(artifactDir.resolve(fileName))
			.setFullPage(fullPage)
		);
	}

	private static boolean runStep(
		final StepAction action,
		final Map<String, String> details,
		final String reportField
	) {
		try {
			action.run();
			details.put(reportField, "PASS");
			return true;
		} catch (Throwable ex) {
			details.put(reportField, "FAIL - " + ex.getMessage());
			return false;
		}
	}

	private static LinkedHashMap<String, Boolean> createEmptyReport() {
		final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
		return report;
	}

	private static void writeFinalReport(
		final Map<String, Boolean> report,
		final Map<String, String> details,
		final String termsUrl,
		final String privacyUrl,
		final Path artifactDir
	) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("# SaleADS Mi Negocio Full Workflow Report\n\n");
		builder.append("Generated: ")
			.append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
			.append("\n\n");
		builder.append("| Step | Result |\n");
		builder.append("|---|---|\n");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			final String step = entry.getKey();
			final String result = entry.getValue() ? "PASS" : "FAIL";
			builder.append("| ").append(step).append(" | ").append(result).append(" |\n");
		}
		builder.append("\n## Details\n\n");
		for (Map.Entry<String, String> entry : details.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
		}
		builder.append("\n## Captured URLs\n\n");
		builder.append("- Términos y Condiciones: ").append(termsUrl).append("\n");
		builder.append("- Política de Privacidad: ").append(privacyUrl).append("\n");

		Files.writeString(
			artifactDir.resolve("final-report.md"),
			builder.toString(),
			StandardCharsets.UTF_8
		);
	}

	private static Path createArtifactDirectory() throws IOException {
		final String timestamp = LocalDateTime.now()
			.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT));
		final Path artifactDir = Paths.get("target", "saleads-e2e", timestamp);
		Files.createDirectories(artifactDir);
		return artifactDir;
	}

	private static Optional<String> getEnv(final String key) {
		return Optional.ofNullable(System.getenv(key));
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Throwable;
	}
}
