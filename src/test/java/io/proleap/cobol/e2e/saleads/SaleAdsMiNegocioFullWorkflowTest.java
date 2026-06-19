package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
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
import org.junit.Assume;
import org.junit.Test;

public class SaleAdsMiNegocioFullWorkflowTest {

	private static final double UI_WAIT_TIMEOUT_MS = 15000;
	private static final double VISIBILITY_TIMEOUT_MS = 15000;
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final List<String> REPORT_FIELDS = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final String loginUrl = env("SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL to the SaleADS login page of the target environment. URL is intentionally not hardcoded.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true"));
		final int slowMoMs = Integer.parseInt(envOrDefault("SALEADS_SLOWMO_MS", "150"));
		final Path screenshotDir = createScreenshotDirectory();
		final Map<String, StepResult> results = new LinkedHashMap<>();
		final Map<String, String> evidenceUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new Browser.LaunchOptions().setHeadless(headless).setSlowMo((double) slowMoMs));
			final BrowserContext context = browser.newContext(
					new Browser.NewContextOptions().setViewportSize(1440, 900).setIgnoreHTTPSErrors(true));
			final Page page = context.newPage();

			results.put("Login", runStep("Login", () -> {
				page.navigate(loginUrl);
				waitForUi(page);

				clickSignInWithGoogle(page, context);
				selectGoogleAccountIfShown(context, page, GOOGLE_ACCOUNT_EMAIL);

				ensureAnyVisible(page, List.of(
						page.getByRole(AriaRole.NAVIGATION),
						page.locator("aside"),
						page.getByText(Pattern.compile("(?i).*Negocio.*"))),
						"main application interface with left sidebar navigation");
				takeScreenshot(page, screenshotDir, "01_dashboard_loaded.png", true);
			}));

			results.put("Mi Negocio menu", runStep("Mi Negocio menu", () -> {
				clickByVisibleText(page, "Negocio");
				clickByVisibleText(page, "Mi Negocio");

				ensureTextVisible(page, "Agregar Negocio");
				ensureTextVisible(page, "Administrar Negocios");
				takeScreenshot(page, screenshotDir, "02_mi_negocio_menu_expanded.png", true);
			}));

			results.put("Agregar Negocio modal", runStep("Agregar Negocio modal", () -> {
				clickByVisibleText(page, "Agregar Negocio");
				ensureTextVisible(page, "Crear Nuevo Negocio");
				ensureTextVisible(page, "Nombre del Negocio");
				ensureTextVisible(page, "Tienes 2 de 3 negocios");
				ensureTextVisible(page, "Cancelar");
				ensureTextVisible(page, "Crear Negocio");

				final Locator nameInput = firstVisible(page, List.of(
						page.getByLabel(Pattern.compile("(?i).*Nombre del Negocio.*")),
						page.getByPlaceholder(Pattern.compile("(?i).*Nombre del Negocio.*")),
						page.locator("input[type='text']"),
						page.locator("input")));
				if (nameInput != null) {
					nameInput.fill("Negocio Prueba Automatizacion");
				}
				clickByVisibleText(page, "Cancelar");
				takeScreenshot(page, screenshotDir, "03_agregar_negocio_modal.png", true);
			}));

			results.put("Administrar Negocios view", runStep("Administrar Negocios view", () -> {
				if (!isTextVisible(page, "Administrar Negocios", 3000)) {
					clickByVisibleText(page, "Mi Negocio");
				}
				clickByVisibleText(page, "Administrar Negocios");

				ensureTextVisible(page, "Información General");
				ensureTextVisible(page, "Detalles de la Cuenta");
				ensureTextVisible(page, "Tus Negocios");
				ensureTextVisible(page, "Sección Legal");
				takeScreenshot(page, screenshotDir, "04_administrar_negocios_view.png", true);
			}));

			results.put("Información General", runStep("Información General", () -> {
				ensureTextVisible(page, "Información General");
				final Locator emailLocator = page.getByText(EMAIL_PATTERN).first();
				assertTrue("User email should be visible.", isVisible(emailLocator, VISIBILITY_TIMEOUT_MS));
				ensureTextVisible(page, "BUSINESS PLAN");
				ensureTextVisible(page, "Cambiar Plan");

				final String bodyText = safeInnerText(page.locator("body"));
				final boolean hasLikelyName = bodyText != null && bodyText.lines().map(String::trim)
						.anyMatch(line -> !line.isBlank() && !EMAIL_PATTERN.matcher(line).find()
								&& !line.equalsIgnoreCase("BUSINESS PLAN") && !line.equalsIgnoreCase("Cambiar Plan")
								&& !line.equalsIgnoreCase("Información General") && line.length() >= 3);
				assertTrue("User name should be visible.", hasLikelyName);
			}));

			results.put("Detalles de la Cuenta", runStep("Detalles de la Cuenta", () -> {
				ensureTextVisible(page, "Cuenta creada");
				ensureTextVisible(page, "Estado activo");
				ensureTextVisible(page, "Idioma seleccionado");
			}));

			results.put("Tus Negocios", runStep("Tus Negocios", () -> {
				ensureTextVisible(page, "Tus Negocios");
				ensureTextVisible(page, "Agregar Negocio");
				ensureTextVisible(page, "Tienes 2 de 3 negocios");
			}));

			results.put("Términos y Condiciones", runStep("Términos y Condiciones", () -> {
				final String url = openLegalDocumentAndReturn(page, context, "Términos y Condiciones",
						"Términos y Condiciones", screenshotDir, "05_terminos_y_condiciones.png");
				evidenceUrls.put("Términos y Condiciones", url);
			}));

			results.put("Política de Privacidad", runStep("Política de Privacidad", () -> {
				final String url = openLegalDocumentAndReturn(page, context, "Política de Privacidad",
						"Política de Privacidad", screenshotDir, "06_politica_de_privacidad.png");
				evidenceUrls.put("Política de Privacidad", url);
			}));
		}

		printFinalReport(results, evidenceUrls, screenshotDir);
		assertAllStepsPassed(results);
	}

	private static String openLegalDocumentAndReturn(final Page appPage, final BrowserContext context,
			final String linkText, final String expectedHeading, final Path screenshotDir, final String screenshotName) {
		final int pagesBefore = context.pages().size();
		clickByVisibleText(appPage, linkText);

		final Page targetPage = awaitNewPage(context, pagesBefore, appPage);
		waitForUi(targetPage);

		ensureAnyVisible(targetPage, List.of(
				targetPage.getByRole(AriaRole.HEADING,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*" + Pattern.quote(expectedHeading) + ".*"))),
				targetPage.getByText(Pattern.compile("(?i).*" + Pattern.quote(expectedHeading) + ".*"))),
				"legal heading: " + expectedHeading);

		final String legalContent = safeInnerText(targetPage.locator("body"));
		assertTrue("Legal content should be visible for " + expectedHeading,
				legalContent != null && legalContent.trim().length() > 100);
		takeScreenshot(targetPage, screenshotDir, screenshotName, true);

		final String finalUrl = targetPage.url();

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
		} else {
			try {
				appPage.goBack(new Page.GoBackOptions().setTimeout(UI_WAIT_TIMEOUT_MS));
			} catch (final PlaywrightException ignored) {
				// Stay on the same page if there is no browser history entry.
			}
		}
		waitForUi(appPage);
		return finalUrl;
	}

	private static void clickSignInWithGoogle(final Page page, final BrowserContext context) {
		final Locator button = firstVisible(page, List.of(
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*(google|sign in|iniciar).*"))),
				page.getByText(Pattern.compile("(?i).*(Sign in with Google|Iniciar sesión con Google|Google).*"))));
		if (button == null) {
			fail("Could not find Google sign-in button.");
		}

		final int pagesBefore = context.pages().size();
		clickAndWait(page, button);
		awaitNewPage(context, pagesBefore, page);
	}

	private static void selectGoogleAccountIfShown(final BrowserContext context, final Page appPage, final String email) {
		Page candidatePage = appPage;
		for (final Page page : context.pages()) {
			if (page != appPage) {
				candidatePage = page;
				break;
			}
		}

		try {
			final Locator accountLocator = candidatePage.getByText(email, new Page.GetByTextOptions().setExact(true)).first();
			if (isVisible(accountLocator, 5000)) {
				clickAndWait(candidatePage, accountLocator);
			}
		} catch (final PlaywrightException ignored) {
			// Account chooser may not appear if the session is already authenticated.
		}

		waitForUi(appPage);
	}

	private static Page awaitNewPage(final BrowserContext context, final int pagesBefore, final Page fallbackPage) {
		final long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < 5000) {
			final List<Page> currentPages = context.pages();
			if (currentPages.size() > pagesBefore) {
				return currentPages.get(currentPages.size() - 1);
			}
			fallbackPage.waitForTimeout(100);
		}
		return fallbackPage;
	}

	private static StepResult runStep(final String stepName, final Runnable action) {
		try {
			action.run();
			return StepResult.passed();
		} catch (final Throwable t) {
			return StepResult.failed(stepName + " failed: " + t.getMessage());
		}
	}

	private static void assertAllStepsPassed(final Map<String, StepResult> results) {
		final List<String> failed = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.get(field);
			if (result == null || !result.passed) {
				final String reason = result == null ? "step not executed" : result.message;
				failed.add(field + " -> " + reason);
			}
		}
		if (!failed.isEmpty()) {
			fail("One or more workflow steps failed:\n" + String.join("\n", failed));
		}
	}

	private static void printFinalReport(final Map<String, StepResult> results, final Map<String, String> evidenceUrls,
			final Path screenshotDir) {
		System.out.println("=== SaleADS Mi Negocio Workflow Final Report ===");
		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.get(field);
			final String status = result != null && result.passed ? "PASS" : "FAIL";
			System.out.println(field + ": " + status);
			if (result != null && !result.passed && result.message != null) {
				System.out.println("  reason: " + result.message);
			}
		}

		if (!evidenceUrls.isEmpty()) {
			System.out.println("Final URLs:");
			for (final Map.Entry<String, String> entry : evidenceUrls.entrySet()) {
				System.out.println("  " + entry.getKey() + ": " + entry.getValue());
			}
		}
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
	}

	private static void ensureTextVisible(final Page page, final String text) {
		final Locator locator = firstVisible(page, List.of(
				page.getByText(text, new Page.GetByTextOptions().setExact(true)),
				page.getByText(Pattern.compile("(?i).*" + Pattern.quote(text) + ".*"))));
		if (locator == null) {
			fail("Expected text is not visible: " + text);
		}
	}

	private static void clickByVisibleText(final Page page, final String text) {
		final Locator locator = firstVisible(page, List.of(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*" + Pattern.quote(text) + ".*"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*" + Pattern.quote(text) + ".*"))),
				page.getByText(text, new Page.GetByTextOptions().setExact(true)),
				page.getByText(Pattern.compile("(?i).*" + Pattern.quote(text) + ".*"))));
		if (locator == null) {
			fail("Could not find clickable text: " + text);
		}
		clickAndWait(page, locator);
	}

	private static void ensureAnyVisible(final Page page, final List<Locator> locators, final String description) {
		if (firstVisible(page, locators) == null) {
			fail("Expected to find visible element: " + description);
		}
	}

	private static boolean isTextVisible(final Page page, final String text, final double timeoutMs) {
		final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
		if (isVisible(exact, timeoutMs)) {
			return true;
		}
		final Locator fuzzy = page.getByText(Pattern.compile("(?i).*" + Pattern.quote(text) + ".*")).first();
		return isVisible(fuzzy, timeoutMs);
	}

	private static Locator firstVisible(final Page page, final List<Locator> locators) {
		for (final Locator locator : locators) {
			try {
				if (locator.count() == 0) {
					continue;
				}
				final Locator candidate = locator.first();
				if (isVisible(candidate, 3000)) {
					return candidate;
				}
			} catch (final PlaywrightException ignored) {
				// Try next locator.
			}
		}
		return null;
	}

	private static boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final PlaywrightException e) {
			return false;
		}
	}

	private static void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout(VISIBILITY_TIMEOUT_MS));
		waitForUi(page);
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(UI_WAIT_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// Some SPA interactions do not trigger network idle state.
		}
		page.waitForTimeout(700);
	}

	private static void takeScreenshot(final Page page, final Path directory, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(directory.resolve(fileName)).setFullPage(fullPage));
	}

	private static Path createScreenshotDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		final Path dir = Paths.get("target", "saleads-minegocio-evidence", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private static String safeInnerText(final Locator locator) {
		try {
			return locator.first().innerText();
		} catch (final PlaywrightException e) {
			return null;
		}
	}

	private static String env(final String name) {
		return System.getenv(name);
	}

	private static String envOrDefault(final String name, final String defaultValue) {
		final String value = env(name);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String message;

		private StepResult(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message;
		}

		private static StepResult passed() {
			return new StepResult(true, null);
		}

		private static StepResult failed(final String message) {
			return new StepResult(false, message);
		}

		@Override
		public String toString() {
			return passed ? "PASS" : "FAIL: " + message;
		}
	}
}
