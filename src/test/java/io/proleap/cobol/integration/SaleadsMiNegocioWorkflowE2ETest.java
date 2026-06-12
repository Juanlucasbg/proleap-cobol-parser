package io.proleap.cobol.integration;

import static org.junit.Assert.assertTrue;

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
import com.microsoft.playwright.Page.LoadState;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;

public class SaleadsMiNegocioWorkflowE2ETest {

	private static final double DEFAULT_TIMEOUT_MS = 20_000;
	private static final Pattern GOOGLE_BUTTON_PATTERN = Pattern
			.compile("(?i)(google|sign in with google|iniciar sesi.n con google|continuar con google)");
	private static final Pattern LEGAL_TERMS_PATTERN = Pattern.compile("(?i)t.rminos y condiciones");
	private static final Pattern LEGAL_PRIVACY_PATTERN = Pattern.compile("(?i)pol.tica de privacidad");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue("Enable with -Dsaleads.e2e.enabled=true", enabled);

		final String startUrl = firstNonBlank(System.getProperty("saleads.startUrl"), System.getenv("SALEADS_START_URL"));
		final String accountEmail = firstNonBlank(System.getProperty("saleads.accountEmail"),
				System.getenv("SALEADS_ACCOUNT_EMAIL"), "juanlucasbarbiergarzon@gmail.com");
		final String browserName = firstNonBlank(System.getProperty("saleads.browser"), "chromium").toLowerCase();
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));

		final Path screenshotDir = buildScreenshotDirectory();
		final Map<String, String> report = new LinkedHashMap<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = launchBrowser(playwright, browserName, headless);
			try (BrowserContext context = browser.newContext()) {
				context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
				final Page page = context.newPage();

				if (startUrl != null) {
					page.navigate(startUrl);
					waitForUiToLoad(page);
				}

				runStep(report, failures, "Login", () -> {
					assertTrue("Browser must be on SaleADS login page or provide -Dsaleads.startUrl",
							!"about:blank".equals(page.url()));
					loginWithGoogle(page, context, accountEmail);
					assertVisible(page.getByRole(AriaRole.MAIN), "Main application interface");
					assertVisible(anyText(page, Pattern.compile("(?i)negocio")), "Left sidebar navigation");
					takeScreenshot(page, screenshotDir.resolve("01-dashboard-loaded.png"), false);
				});

				runStep(report, failures, "Mi Negocio menu", () -> {
					openMiNegocioMenu(page);
					assertVisible(exactText(page, "Agregar Negocio"), "Agregar Negocio option");
					assertVisible(exactText(page, "Administrar Negocios"), "Administrar Negocios option");
					takeScreenshot(page, screenshotDir.resolve("02-mi-negocio-menu-expanded.png"), false);
				});

				runStep(report, failures, "Agregar Negocio modal", () -> {
					clickAndWait(page, firstVisible(exactText(page, "Agregar Negocio")));
					assertVisible(exactText(page, "Crear Nuevo Negocio"), "Crear Nuevo Negocio modal title");
					assertVisible(page.getByLabel("Nombre del Negocio"), "Nombre del Negocio input");
					assertVisible(exactText(page, "Tienes 2 de 3 negocios"), "Business quota text");
					assertVisible(exactText(page, "Cancelar"), "Cancelar button");
					assertVisible(exactText(page, "Crear Negocio"), "Crear Negocio button");

					clickAndWait(page, page.getByLabel("Nombre del Negocio"));
					page.getByLabel("Nombre del Negocio").fill("Negocio Prueba Automatizacion");
					takeScreenshot(page, screenshotDir.resolve("03-agregar-negocio-modal.png"), false);
					clickAndWait(page, exactText(page, "Cancelar"));
				});

				runStep(report, failures, "Administrar Negocios view", () -> {
					openMiNegocioMenu(page);
					clickAndWait(page, exactText(page, "Administrar Negocios"));
					assertVisible(anyText(page, Pattern.compile("(?i)informaci.n general")), "Informacion General section");
					assertVisible(exactText(page, "Detalles de la Cuenta"), "Detalles de la Cuenta section");
					assertVisible(exactText(page, "Tus Negocios"), "Tus Negocios section");
					assertVisible(anyText(page, Pattern.compile("(?i)secci.n legal")), "Seccion Legal section");
					takeScreenshot(page, screenshotDir.resolve("04-administrar-negocios-page.png"), true);
				});

				runStep(report, failures, "Información General", () -> {
					assertVisible(anyText(page, Pattern.compile("(?i)(juan|nombre)")), "User name");
					assertVisible(anyText(page, Pattern.compile("(?i)[\\w._%+-]+@[\\w.-]+\\.[a-z]{2,}")), "User email");
					assertVisible(anyText(page, Pattern.compile("(?i)business\\s*plan")), "BUSINESS PLAN text");
					assertVisible(exactText(page, "Cambiar Plan"), "Cambiar Plan button");
				});

				runStep(report, failures, "Detalles de la Cuenta", () -> {
					assertVisible(exactText(page, "Cuenta creada"), "Cuenta creada text");
					assertVisible(exactText(page, "Estado activo"), "Estado activo text");
					assertVisible(exactText(page, "Idioma seleccionado"), "Idioma seleccionado text");
				});

				runStep(report, failures, "Tus Negocios", () -> {
					assertVisible(exactText(page, "Tus Negocios"), "Tus Negocios section");
					assertVisible(exactText(page, "Agregar Negocio"), "Agregar Negocio button");
					assertVisible(exactText(page, "Tienes 2 de 3 negocios"), "Business quota text");
					final Locator businessRows = page
							.locator("section:has-text(\"Tus Negocios\") li, section:has-text(\"Tus Negocios\") [role='row']");
					assertTrue("Business list should be visible", businessRows.count() > 0 && businessRows.first().isVisible());
				});

				runStep(report, failures, "Términos y Condiciones", () -> {
					final String termsUrl = validateLegalDocument(page, context, LEGAL_TERMS_PATTERN, LEGAL_TERMS_PATTERN,
							screenshotDir.resolve("05-terminos-y-condiciones.png"));
					legalUrls.put("Términos y Condiciones", termsUrl);
				});

				runStep(report, failures, "Política de Privacidad", () -> {
					final String privacyUrl = validateLegalDocument(page, context, LEGAL_PRIVACY_PATTERN, LEGAL_PRIVACY_PATTERN,
							screenshotDir.resolve("06-politica-de-privacidad.png"));
					legalUrls.put("Política de Privacidad", privacyUrl);
				});
			}
		}

		printReport(report, legalUrls, screenshotDir);
		assertTrue("One or more validations failed:\n - " + String.join("\n - ", failures), failures.isEmpty());
	}

	private static Browser launchBrowser(final Playwright playwright, final String browserName, final boolean headless) {
		final BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);
		return switch (browserName) {
		case "firefox" -> playwright.firefox().launch(options);
		case "webkit" -> playwright.webkit().launch(options);
		default -> playwright.chromium().launch(options);
		};
	}

	private static void loginWithGoogle(final Page page, final BrowserContext context, final String accountEmail) {
		final Locator googleButton = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_BUTTON_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_BUTTON_PATTERN)),
				anyText(page, GOOGLE_BUTTON_PATTERN));
		final int beforePages = context.pages().size();
		clickAndWait(page, googleButton);

		Page authPage = page;
		final Page popup = waitForPotentialPopup(context, page, beforePages);
		if (popup != null) {
			authPage = popup;
			popup.bringToFront();
			waitForUiToLoad(popup);
		}

		selectGoogleAccountIfPrompted(authPage, accountEmail);
		waitForUiToLoad(page);
	}

	private static void openMiNegocioMenu(final Page page) {
		final boolean expanded = isVisibleSafely(exactText(page, "Agregar Negocio"))
				&& isVisibleSafely(exactText(page, "Administrar Negocios"));
		if (expanded) {
			return;
		}

		final Locator miNegocio = firstVisible(exactText(page, "Mi Negocio"), anyText(page, Pattern.compile("(?i)mi negocio")));
		clickAndWait(page, miNegocio);
		assertVisible(exactText(page, "Agregar Negocio"), "Agregar Negocio submenu");
		assertVisible(exactText(page, "Administrar Negocios"), "Administrar Negocios submenu");
	}

	private static String validateLegalDocument(final Page page, final BrowserContext context, final Pattern linkPattern,
			final Pattern headingPattern, final Path screenshotPath) {
		final int beforePages = context.pages().size();
		final Locator link = firstVisible(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(linkPattern)), anyText(page, linkPattern));
		clickAndWait(page, link);

		final Page popup = waitForPotentialPopup(context, page, beforePages);
		final Page targetPage = popup == null ? page : popup;
		targetPage.bringToFront();
		waitForUiToLoad(targetPage);

		assertVisible(targetPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
				"Legal heading");
		final Object hasContent = targetPage.evaluate("() => (document.body?.innerText || '').trim().length > 300");
		assertTrue("Legal content text should be visible", Boolean.TRUE.equals(hasContent));
		takeScreenshot(targetPage, screenshotPath, true);
		final String finalUrl = targetPage.url();

		if (popup != null) {
			popup.close();
			page.bringToFront();
		} else {
			page.goBack();
			waitForUiToLoad(page);
		}

		return finalUrl;
	}

	private static void selectGoogleAccountIfPrompted(final Page page, final String accountEmail) {
		waitForUiToLoad(page);
		final boolean googleContext = page.url().contains("accounts.google.com")
				|| isVisibleSafely(anyText(page, Pattern.compile("(?i)(choose an account|elige una cuenta|selecciona una cuenta)")));
		if (!googleContext) {
			return;
		}

		final Locator account = firstVisible(page.getByText(accountEmail), anyText(page, Pattern.compile(Pattern.quote(accountEmail))));
		clickAndWait(page, account);
		waitForUiToLoad(page);
	}

	private static void clickAndWait(final Page page, final Locator locator) {
		final Locator target = firstVisible(locator);
		target.waitFor(new Locator.WaitForOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		target.click();
		waitForUiToLoad(page);
	}

	private static void waitForUiToLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (final RuntimeException ignored) {
			// No navigation happened. Continue with a short settle delay.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
		} catch (final RuntimeException ignored) {
			page.waitForTimeout(500);
		}
	}

	private static void assertVisible(final Locator locator, final String elementDescription) {
		locator.first().waitFor(new Locator.WaitForOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		assertTrue(elementDescription + " should be visible", locator.first().isVisible());
	}

	private static Locator exactText(final Page page, final String text) {
		return page.getByText(text, new Page.GetByTextOptions().setExact(true));
	}

	private static Locator anyText(final Page page, final Pattern pattern) {
		return page.getByText(pattern);
	}

	private static void takeScreenshot(final Page page, final Path file, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(fullPage));
	}

	private static void runStep(final Map<String, String> report, final List<String> failures, final String stepName,
			final CheckedRunnable step) {
		try {
			step.run();
			report.put(stepName, "PASS");
		} catch (final Throwable t) {
			report.put(stepName, "FAIL");
			failures.add(stepName + ": " + t.getMessage());
		}
	}

	private static Locator firstVisible(final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate == null || candidate.count() == 0) {
				continue;
			}
			try {
				if (isVisibleSafely(candidate.first())) {
					return candidate.first();
				}
			} catch (final RuntimeException ignored) {
				// Keep trying next candidate.
			}
		}

		for (final Locator candidate : candidates) {
			if (candidate != null && candidate.count() > 0) {
				return candidate.first();
			}
		}

		throw new AssertionError("No matching locator found for requested element.");
	}

	private static boolean isVisibleSafely(final Locator locator) {
		try {
			return locator != null && locator.count() > 0 && locator.first().isVisible();
		} catch (final RuntimeException ignored) {
			return false;
		}
	}

	private static Page waitForPotentialPopup(final BrowserContext context, final Page sourcePage, final int previousPageCount) {
		final long end = System.currentTimeMillis() + 6_000;
		while (System.currentTimeMillis() < end) {
			if (context.pages().size() > previousPageCount) {
				final List<Page> pages = context.pages();
				final Page newestPage = pages.get(pages.size() - 1);
				if (newestPage != sourcePage) {
					return newestPage;
				}
			}
			sourcePage.waitForTimeout(250);
		}

		return null;
	}

	private static Path buildScreenshotDirectory() throws Exception {
		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Paths.get("target", "saleads-mi-negocio-e2e", runId);
		Files.createDirectories(path);
		return path;
	}

	private static void printReport(final Map<String, String> report, final Map<String, String> legalUrls,
			final Path screenshotDir) {
		System.out.println("===== SaleADS Mi Negocio Full Workflow Report =====");
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			System.out.println(entry.getKey() + " URL: " + entry.getValue());
		}
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
	}

	private static String firstNonBlank(final String... candidates) {
		for (final String candidate : candidates) {
			if (candidate != null && !candidate.trim().isEmpty()) {
				return candidate.trim();
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
