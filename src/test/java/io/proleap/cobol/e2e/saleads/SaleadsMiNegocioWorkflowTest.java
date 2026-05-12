package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");
	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-evidence");
	private static final Path FINAL_REPORT = EVIDENCE_DIR.resolve("final-report.txt");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String enabled = System.getenv().getOrDefault("SALEADS_E2E_ENABLED", "false");
		final String baseUrl = System.getenv("SALEADS_BASE_URL");
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));

		assumeTrue("Set SALEADS_E2E_ENABLED=true to run this E2E test.", "true".equalsIgnoreCase(enabled));
		assumeTrue("Set SALEADS_BASE_URL to the current SaleADS environment login page.",
				baseUrl != null && !baseUrl.isBlank());

		Files.createDirectories(EVIDENCE_DIR);

		final Map<String, Boolean> results = new LinkedHashMap<>();
		final Map<String, String> details = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(200));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(20_000);

			appPage.navigate(baseUrl);
			appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);

			runStep("Login", results, details, () -> {
				loginWithGoogle(appPage, context);
				screenshot(appPage, "01-dashboard-loaded.png", false);
				return "Dashboard loaded and left sidebar visible.";
			});

			runStep("Mi Negocio menu", results, details, () -> {
				openMiNegocioMenu(appPage);
				screenshot(appPage, "02-mi-negocio-expanded.png", false);
				return "Mi Negocio menu expanded with Agregar/Administrar options.";
			});

			runStep("Agregar Negocio modal", results, details, () -> {
				validateAgregarNegocioModal(appPage);
				screenshot(appPage, "03-agregar-negocio-modal.png", false);
				return "Crear Nuevo Negocio modal validated.";
			});

			runStep("Administrar Negocios view", results, details, () -> {
				openAdministrarNegocios(appPage);
				screenshot(appPage, "04-administrar-negocios-full.png", true);
				return "Administrar Negocios page sections validated.";
			});

			runStep("Información General", results, details, () -> {
				validateInformacionGeneral(appPage);
				return "User identity, email, plan and Cambiar Plan validated.";
			});

			runStep("Detalles de la Cuenta", results, details, () -> {
				validateDetallesCuenta(appPage);
				return "Cuenta creada, Estado activo and Idioma seleccionado validated.";
			});

			runStep("Tus Negocios", results, details, () -> {
				validateTusNegocios(appPage);
				return "Business list and quota validated.";
			});

			runStep("Términos y Condiciones", results, details, () -> {
				final String termsUrl = validateLegalLinkAndReturnUrl(appPage, context, "Términos y Condiciones",
						"Términos y Condiciones", "05-terminos-y-condiciones.png");
				return "Validated legal content. URL: " + termsUrl;
			});

			runStep("Política de Privacidad", results, details, () -> {
				final String policyUrl = validateLegalLinkAndReturnUrl(appPage, context, "Política de Privacidad",
						"Política de Privacidad", "06-politica-de-privacidad.png");
				return "Validated legal content. URL: " + policyUrl;
			});
		}

		writeFinalReport(results, details);
		assertAllStepsPassed(results, details);
	}

	private void loginWithGoogle(final Page appPage, final BrowserContext context) {
		final Locator loginButton = firstVisibleByText(appPage,
				Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
						"Continuar con Google", "Google"));

		Page popup = null;
		try {
			popup = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(7_000), loginButton::click);
		} catch (final TimeoutError ignored) {
			// No popup opened; login likely continued in the same tab.
		}

		if (popup != null) {
			popup.waitForLoadState(LoadState.DOMCONTENTLOADED);
			selectGoogleAccountIfVisible(popup, GOOGLE_ACCOUNT_EMAIL);
			popup.waitForTimeout(1_200);
		} else {
			selectGoogleAccountIfVisible(appPage, GOOGLE_ACCOUNT_EMAIL);
		}

		appPage.bringToFront();
		appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		appPage.waitForTimeout(800);

		requireVisibleByText(appPage, "Negocio");
		assertTrue("Left sidebar navigation is not visible.", appPage.locator("aside, nav").first().isVisible());
	}

	private void openMiNegocioMenu(final Page appPage) {
		clickIfVisible(appPage, "Negocio");
		clickAndWait(appPage, requireVisibleByText(appPage, "Mi Negocio"));

		requireVisibleByText(appPage, "Agregar Negocio");
		requireVisibleByText(appPage, "Administrar Negocios");
	}

	private void validateAgregarNegocioModal(final Page appPage) {
		clickAndWait(appPage, requireVisibleByText(appPage, "Agregar Negocio"));

		requireVisibleByText(appPage, "Crear Nuevo Negocio");
		requireVisibleByText(appPage, "Nombre del Negocio");
		requireVisibleByText(appPage, "Tienes 2 de 3 negocios");
		requireVisibleByText(appPage, "Cancelar");
		requireVisibleByText(appPage, "Crear Negocio");

		Locator nameField = appPage.getByLabel("Nombre del Negocio");
		if (!isVisible(nameField)) {
			nameField = appPage.getByPlaceholder("Nombre del Negocio");
		}
		assertTrue("Nombre del Negocio input field not found.", isVisible(nameField));
		nameField.first().fill("Negocio Prueba Automatización");
		clickAndWait(appPage, requireVisibleByText(appPage, "Cancelar"));
	}

	private void openAdministrarNegocios(final Page appPage) {
		if (!isVisible(appPage.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true)))) {
			clickAndWait(appPage, requireVisibleByText(appPage, "Mi Negocio"));
		}

		clickAndWait(appPage, requireVisibleByText(appPage, "Administrar Negocios"));
		requireVisibleByText(appPage, "Información General");
		requireVisibleByText(appPage, "Detalles de la Cuenta");
		requireVisibleByText(appPage, "Tus Negocios");
		requireVisibleByText(appPage, "Sección Legal");
	}

	private void validateInformacionGeneral(final Page appPage) {
		requireVisibleByText(appPage, "Información General");
		requireVisibleByText(appPage, "BUSINESS PLAN");
		requireVisibleByText(appPage, "Cambiar Plan");

		final String bodyText = appPage.locator("body").innerText();
		assertTrue("User email is not visible in Información General.",
				bodyText.contains(GOOGLE_ACCOUNT_EMAIL) || EMAIL_PATTERN.matcher(bodyText).find());
		assertTrue("No likely user name text was detected in the profile area.", containsLikelyUserName(bodyText));
	}

	private void validateDetallesCuenta(final Page appPage) {
		requireVisibleByText(appPage, "Detalles de la Cuenta");
		requireVisibleByText(appPage, "Cuenta creada");
		requireVisibleByText(appPage, "Estado activo");
		requireVisibleByText(appPage, "Idioma seleccionado");
	}

	private void validateTusNegocios(final Page appPage) {
		requireVisibleByText(appPage, "Tus Negocios");
		requireVisibleByText(appPage, "Agregar Negocio");
		requireVisibleByText(appPage, "Tienes 2 de 3 negocios");
		assertTrue("Business list appears to be empty or not visible.", appPage.locator("ul, table, [role='list']").count() > 0);
	}

	private String validateLegalLinkAndReturnUrl(final Page appPage, final BrowserContext context, final String linkText,
			final String expectedHeading, final String screenshotName) {
		requireVisibleByText(appPage, "Sección Legal");

		final Locator legalLink = requireVisibleByText(appPage, linkText);
		Page targetPage = null;

		try {
			targetPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(6_000), legalLink::click);
		} catch (final TimeoutError ignored) {
			// Legal page opened in same tab.
		}

		if (targetPage == null) {
			appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			targetPage = appPage;
		} else {
			targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		}

		requireVisibleByText(targetPage, expectedHeading);
		final String legalBodyText = targetPage.locator("body").innerText();
		assertTrue("Legal content text is not visible for " + expectedHeading + ".", legalBodyText.trim().length() > 120);

		screenshot(targetPage, screenshotName, true);
		final String finalUrl = targetPage.url();

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} else {
			try {
				appPage.goBack();
				appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			} catch (final PlaywrightException ignored) {
				// Some environments use same-page overlays without navigation history.
			}
		}

		appPage.waitForTimeout(600);
		return finalUrl;
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click();
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(400);
	}

	private void clickIfVisible(final Page page, final String text) {
		final Locator candidate = page.getByText(text, new Page.GetByTextOptions().setExact(true));
		if (isVisible(candidate)) {
			clickAndWait(page, candidate);
		}
	}

	private Locator requireVisibleByText(final Page page, final String text) {
		Locator candidate = page.getByText(text, new Page.GetByTextOptions().setExact(true));
		if (!isVisible(candidate)) {
			candidate = page.getByText(text);
		}
		candidate.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
		return candidate.first();
	}

	private Locator firstVisibleByText(final Page page, final List<String> candidates) {
		for (final String text : candidates) {
			Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(true));
			if (isVisible(locator)) {
				return locator.first();
			}
			locator = page.getByText(text);
			if (isVisible(locator)) {
				return locator.first();
			}
		}
		throw new AssertionError("Could not find any visible text selector from: " + candidates);
	}

	private void selectGoogleAccountIfVisible(final Page page, final String email) {
		try {
			final Locator account = page.getByText(email, new Page.GetByTextOptions().setExact(true));
			if (isVisible(account)) {
				account.first().click();
				page.waitForLoadState(LoadState.DOMCONTENTLOADED);
				page.waitForTimeout(800);
			}
		} catch (final PlaywrightException ignored) {
			// Google chooser may not appear when session is already authenticated.
		}
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.first().isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void screenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(EVIDENCE_DIR.resolve(fileName)).setFullPage(fullPage));
	}

	private void runStep(final String name, final Map<String, Boolean> results, final Map<String, String> details,
			final StepBody body) {
		try {
			final String detail = body.run();
			results.put(name, true);
			details.put(name, detail == null ? "PASS" : detail);
		} catch (final Throwable throwable) {
			results.put(name, false);
			details.put(name, throwable.getMessage() == null ? throwable.toString() : throwable.getMessage());
		}
	}

	private void writeFinalReport(final Map<String, Boolean> results, final Map<String, String> details) throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("generated_at_utc: " + Instant.now());
		lines.add("");

		for (final String field : REPORT_FIELDS) {
			final boolean passed = results.getOrDefault(field, false);
			final String detail = details.getOrDefault(field, "Not executed.");
			lines.add(field + ": " + (passed ? "PASS" : "FAIL"));
			lines.add("  detail: " + detail);
		}

		Files.write(FINAL_REPORT, lines, StandardCharsets.UTF_8);
	}

	private void assertAllStepsPassed(final Map<String, Boolean> results, final Map<String, String> details) {
		final List<String> failed = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			if (!results.getOrDefault(field, false)) {
				failed.add(field + " -> " + details.getOrDefault(field, "No details"));
			}
		}
		if (!failed.isEmpty()) {
			fail("One or more validations failed:\n" + String.join("\n", failed) + "\nFinal report: " + FINAL_REPORT);
		}
	}

	private boolean containsLikelyUserName(final String text) {
		final List<String> excluded = Arrays.asList("Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Sección Legal", "BUSINESS PLAN", "Cambiar Plan", "Cuenta creada", "Estado activo", "Idioma seleccionado");
		final String[] lines = text.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.length() < 3 || line.length() > 80) {
				continue;
			}
			if (excluded.contains(line)) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (line.matches(".*\\d{2,}.*")) {
				continue;
			}
			if (line.matches(".*\\p{L}+\\s+\\p{L}+.*")) {
				return true;
			}
		}
		return false;
	}

	@FunctionalInterface
	private interface StepBody {
		String run() throws Exception;
	}

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
}
