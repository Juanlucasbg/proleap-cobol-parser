package io.proleap.cobol.e2e;

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

import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String GOOGLE_EMAIL_ENV = "SALEADS_GOOGLE_EMAIL";
	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final int DEFAULT_TIMEOUT_MS = 30_000;
	private static final Pattern LEGAL_CONTENT_PATTERN = Pattern.compile("(?is).{80,}");

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final List<String> reportOrder = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Términos y Condiciones", "Política de Privacidad");
		final Map<String, String> stepReport = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		for (final String step : reportOrder) {
			stepReport.put(step, "FAIL");
		}

		final String googleEmail = envOrDefault(GOOGLE_EMAIL_ENV, DEFAULT_GOOGLE_EMAIL);
		final Path evidenceDir = createEvidenceDirectory();

		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
					.setHeadless(Boolean.parseBoolean(envOrDefault(HEADLESS_ENV, "true")));
			final Browser browser = playwright.chromium().launch(launchOptions);
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

			final String loginUrl = System.getenv(LOGIN_URL_ENV);
			if (loginUrl != null && !loginUrl.isBlank()) {
				appPage.navigate(loginUrl);
			}

			if ("about:blank".equals(appPage.url())) {
				throw new AssertionError("No SaleADS page is open. Set " + LOGIN_URL_ENV
						+ " to the current environment login URL before running this test.");
			}

			waitForUiToLoad(appPage);

			try {
				// Step 1: Login with Google
				final Locator googleLoginButton = findFirstVisible(appPage,
						pageCandidatesForTexts(appPage, List.of("Sign in with Google", "Iniciar sesión con Google",
								"Continuar con Google", "Ingresar con Google", "Google")));
				final Page popupOrNull = clickAndWaitForOptionalPopup(context, googleLoginButton);

				if (popupOrNull != null) {
					waitForUiToLoad(popupOrNull);
					selectGoogleAccountIfVisible(popupOrNull, googleEmail);
				} else {
					selectGoogleAccountIfVisible(appPage, googleEmail);
				}

				final Page authenticatedPage = waitForPageWithSidebar(context, appPage);
				assertSidebarVisible(authenticatedPage);
				waitForUiToLoad(authenticatedPage);
				takeScreenshot(authenticatedPage, evidenceDir, "01-dashboard-loaded.png", false);
				stepReport.put("Login", "PASS");

				// Step 2: Open Mi Negocio menu
				ensureMiNegocioExpanded(authenticatedPage);
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Agregar Negocio"))),
						"'Agregar Negocio' should be visible in submenu.");
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Administrar Negocios"))),
						"'Administrar Negocios' should be visible in submenu.");
				takeScreenshot(authenticatedPage, evidenceDir, "02-mi-negocio-menu-expanded.png", false);
				stepReport.put("Mi Negocio menu", "PASS");

				// Step 3: Validate Agregar Negocio modal
				clickAndWait(authenticatedPage,
						findFirstVisible(authenticatedPage, pageCandidatesForTexts(authenticatedPage, List.of("Agregar Negocio"))));
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Crear Nuevo Negocio"))),
						"Modal title 'Crear Nuevo Negocio' should be visible.");
				assertVisible(findInputByLabelOrPlaceholder(authenticatedPage, "Nombre del Negocio"),
						"Input 'Nombre del Negocio' should exist.");
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Tienes 2 de 3 negocios"))),
						"'Tienes 2 de 3 negocios' should be visible in modal.");
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Cancelar"))),
						"'Cancelar' button should be present.");
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Crear Negocio"))),
						"'Crear Negocio' button should be present.");
				takeScreenshot(authenticatedPage, evidenceDir, "03-agregar-negocio-modal.png", false);

				final Locator nombreNegocioInput = findInputByLabelOrPlaceholder(authenticatedPage, "Nombre del Negocio");
				nombreNegocioInput.click();
				nombreNegocioInput.fill("Negocio Prueba Automatización");
				clickAndWait(authenticatedPage,
						findFirstVisible(authenticatedPage, pageCandidatesForTexts(authenticatedPage, List.of("Cancelar"))));
				stepReport.put("Agregar Negocio modal", "PASS");

				// Step 4: Open Administrar Negocios
				ensureMiNegocioExpanded(authenticatedPage);
				clickAndWait(authenticatedPage, findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Administrar Negocios"))));
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Información General"))),
						"'Información General' section should exist.");
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Detalles de la Cuenta"))),
						"'Detalles de la Cuenta' section should exist.");
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Tus Negocios"))),
						"'Tus Negocios' section should exist.");
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Sección Legal"))),
						"'Sección Legal' section should exist.");
				takeScreenshot(authenticatedPage, evidenceDir, "04-administrar-negocios.png", true);
				stepReport.put("Administrar Negocios view", "PASS");

				// Step 5: Validate Información General
				assertVisible(authenticatedPage.locator("text=/@/").first(), "User email should be visible.");
				assertVisible(findFirstVisible(authenticatedPage, pageCandidatesForTexts(authenticatedPage, List.of("BUSINESS PLAN"))),
						"'BUSINESS PLAN' text should be visible.");
				assertVisible(findFirstVisible(authenticatedPage, pageCandidatesForTexts(authenticatedPage, List.of("Cambiar Plan"))),
						"'Cambiar Plan' button should be visible.");
				final Locator possibleUserName = authenticatedPage
						.locator("section:has-text('Información General') h1, section:has-text('Información General') h2, section:has-text('Información General') h3");
				assertVisible(possibleUserName, "User name should be visible in Información General.");
				stepReport.put("Información General", "PASS");

				// Step 6: Validate Detalles de la Cuenta
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Cuenta creada"))),
						"'Cuenta creada' should be visible.");
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Estado activo"))),
						"'Estado activo' should be visible.");
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Idioma seleccionado"))),
						"'Idioma seleccionado' should be visible.");
				stepReport.put("Detalles de la Cuenta", "PASS");

				// Step 7: Validate Tus Negocios
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Tus Negocios"))),
						"'Tus Negocios' title should be visible.");
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Agregar Negocio"))),
						"'Agregar Negocio' button should exist in business list.");
				assertVisible(findFirstVisible(authenticatedPage,
						pageCandidatesForTexts(authenticatedPage, List.of("Tienes 2 de 3 negocios"))),
						"'Tienes 2 de 3 negocios' should be visible in business list.");
				stepReport.put("Tus Negocios", "PASS");

				// Step 8: Validate Términos y Condiciones
				final String termsUrl = validateLegalLinkAndReturnToApp(context, authenticatedPage, evidenceDir,
						"Términos y Condiciones", "08-terminos-y-condiciones.png");
				legalUrls.put("Términos y Condiciones", termsUrl);
				stepReport.put("Términos y Condiciones", "PASS");

				// Step 9: Validate Política de Privacidad
				final String privacyUrl = validateLegalLinkAndReturnToApp(context, authenticatedPage, evidenceDir,
						"Política de Privacidad", "09-politica-de-privacidad.png");
				legalUrls.put("Política de Privacidad", privacyUrl);
				stepReport.put("Política de Privacidad", "PASS");
			} catch (final Throwable error) {
				failures.add(error.getMessage() == null ? error.toString() : error.getMessage());
			} finally {
				printFinalReport(stepReport, legalUrls, evidenceDir);
				browser.close();
			}
		}

		assertTrue("At least one workflow validation failed. Inspect report output and screenshots in target/saleads-evidence.",
				failures.isEmpty() && stepReport.values().stream().allMatch("PASS"::equals));
	}

	private static String validateLegalLinkAndReturnToApp(final BrowserContext context, final Page appPage,
			final Path evidenceDir, final String linkText, final String screenshotName) {
		final Locator legalLink = findFirstVisible(appPage, pageCandidatesForTexts(appPage, List.of(linkText)));
		final String appUrlBeforeClick = appPage.url();
		Page legalPage = appPage;

		try {
			legalPage = context.waitForPage(() -> legalLink.click(), new BrowserContext.WaitForPageOptions().setTimeout(5_000));
		} catch (final TimeoutError timeout) {
			clickAndWait(appPage, legalLink);
		}

		waitForUiToLoad(legalPage);
		assertVisible(findFirstVisible(legalPage, pageCandidatesForTexts(legalPage, List.of(linkText))),
				"Legal page heading should contain '" + linkText + "'.");
		assertVisible(legalPage.locator("body").filter(new Locator.FilterOptions().setHasText(LEGAL_CONTENT_PATTERN)),
				"Legal content should be visible for '" + linkText + "'.");
		takeScreenshot(legalPage, evidenceDir, screenshotName, true);
		final String legalUrl = legalPage.url();

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiToLoad(appPage);
		} else {
			try {
				appPage.goBack(new Page.GoBackOptions().setTimeout(15_000));
			} catch (final Exception ignored) {
				appPage.navigate(appUrlBeforeClick);
			}
			waitForUiToLoad(appPage);
		}

		return legalUrl;
	}

	private static List<Locator> pageCandidatesForTexts(final Page page, final List<String> texts) {
		final List<Locator> candidates = new ArrayList<>();
		for (final String text : texts) {
			final Pattern exactCaseInsensitive = Pattern.compile("(?i)^\\s*" + Pattern.quote(text) + "\\s*$");
			candidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(exactCaseInsensitive)));
			candidates.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(exactCaseInsensitive)));
			candidates.add(page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(exactCaseInsensitive)));
			candidates.add(page.getByText(text, new Page.GetByTextOptions().setExact(true)));
			candidates.add(page.getByText(Pattern.compile("(?i)" + Pattern.quote(text))));
		}
		return candidates;
	}

	private static Locator findInputByLabelOrPlaceholder(final Page page, final String labelOrPlaceholder) {
		final List<Locator> candidates = new ArrayList<>();
		candidates.add(page.getByLabel(labelOrPlaceholder, new Page.GetByLabelOptions().setExact(true)));
		candidates.add(page.getByPlaceholder(labelOrPlaceholder));
		candidates.add(page.locator("input").filter(new Locator.FilterOptions().setHasText(Pattern.compile("(?i)"
				+ Pattern.quote(labelOrPlaceholder)))));
		return findFirstVisible(page, candidates);
	}

	private static Page clickAndWaitForOptionalPopup(final BrowserContext context, final Locator clickable) {
		try {
			return context.waitForPage(() -> clickable.click(), new BrowserContext.WaitForPageOptions().setTimeout(7_000));
		} catch (final TimeoutError timeoutError) {
			return null;
		}
	}

	private static void selectGoogleAccountIfVisible(final Page page, final String email) {
		final List<Locator> accountCandidates = new ArrayList<>();
		accountCandidates.add(page.getByText(email, new Page.GetByTextOptions().setExact(true)));
		accountCandidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(email)));
		accountCandidates.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(email)));

		for (final Locator locator : accountCandidates) {
			if (isVisible(locator, 8_000)) {
				clickAndWait(page, locator);
				return;
			}
		}
	}

	private static void ensureMiNegocioExpanded(final Page page) {
		final Locator agregar = findFirstVisible(page, pageCandidatesForTexts(page, List.of("Agregar Negocio")));
		final Locator administrar = findFirstVisible(page, pageCandidatesForTexts(page, List.of("Administrar Negocios")));
		if (isVisible(agregar, 1_000) && isVisible(administrar, 1_000)) {
			return;
		}

		clickAndWait(page, findFirstVisible(page, pageCandidatesForTexts(page, List.of("Mi Negocio"))));
		waitForUiToLoad(page);
	}

	private static void assertSidebarVisible(final Page page) {
		final List<Locator> sidebarCandidates = List.of(page.locator("aside"), page.getByRole(AriaRole.NAVIGATION),
				page.getByText(Pattern.compile("(?i)negocio")));
		for (final Locator candidate : sidebarCandidates) {
			if (isVisible(candidate, 10_000)) {
				return;
			}
		}
		throw new AssertionError("Expected left sidebar navigation to be visible after login.");
	}

	private static Page waitForPageWithSidebar(final BrowserContext context, final Page preferredPage) {
		final long deadline = System.currentTimeMillis() + 90_000L;
		while (System.currentTimeMillis() < deadline) {
			for (final Page candidate : context.pages()) {
				try {
					assertSidebarVisible(candidate);
					return candidate;
				} catch (final AssertionError ignored) {
					// Keep polling all open pages until one shows the application sidebar.
				}
			}
			waitForUiToLoad(preferredPage);
		}
		throw new AssertionError("Could not detect the authenticated application interface after Google login.");
	}

	private static void clickAndWait(final Page page, final Locator locator) {
		locator.first().click();
		waitForUiToLoad(page);
	}

	private static void waitForUiToLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15_000));
		} catch (final Exception ignored) {
			// Dynamic SPAs may not trigger all load states on every action.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15_000));
		} catch (final Exception ignored) {
			// Long-polling endpoints can keep the network active indefinitely.
		}

		page.waitForTimeout(600);
	}

	private static Locator findFirstVisible(final Page page, final List<Locator> candidates) {
		for (final Locator candidate : candidates) {
			if (isVisible(candidate, 2_000)) {
				return candidate.first();
			}
		}
		throw new AssertionError("No visible element found for expected text-based selector on page: " + page.url());
	}

	private static boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private static void assertVisible(final Locator locator, final String message) {
		if (!isVisible(locator, DEFAULT_TIMEOUT_MS)) {
			throw new AssertionError(message);
		}
	}

	private static void takeScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private static Path createEvidenceDirectory() throws Exception {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path baseDir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(baseDir);
		return baseDir;
	}

	private static void printFinalReport(final Map<String, String> report, final Map<String, String> legalUrls,
			final Path evidenceDir) {
		System.out.println("=== SaleADS Mi Negocio Full Workflow Report ===");
		for (final Map.Entry<String, String> step : report.entrySet()) {
			System.out.println(step.getKey() + ": " + step.getValue());
		}
		for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
			System.out.println(legalUrl.getKey() + " URL: " + legalUrl.getValue());
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private static String envOrDefault(final String key, final String defaultValue) {
		final String env = System.getenv(key);
		return env == null || env.isBlank() ? defaultValue : env;
	}
}
