package io.proleap.saleads;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

public class SaleadsMiNegocioFullTest {

	private static final String ENV_BASE_URL = "SALEADS_BASE_URL";
	private static final String ENV_GOOGLE_ACCOUNT = "SALEADS_GOOGLE_ACCOUNT_EMAIL";
	private static final String ENV_HEADLESS = "SALEADS_HEADLESS";
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-evidence");

	private static final String LOGIN_FIELD = "Login";
	private static final String MENU_FIELD = "Mi Negocio menu";
	private static final String MODAL_FIELD = "Agregar Negocio modal";
	private static final String ADMIN_FIELD = "Administrar Negocios view";
	private static final String GENERAL_FIELD = "Informaci\u00f3n General";
	private static final String DETAILS_FIELD = "Detalles de la Cuenta";
	private static final String BUSINESS_FIELD = "Tus Negocios";
	private static final String TERMS_FIELD = "T\u00e9rminos y Condiciones";
	private static final String PRIVACY_FIELD = "Pol\u00edtica de Privacidad";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String baseUrl = System.getenv(ENV_BASE_URL);
		Assume.assumeTrue("Set SALEADS_BASE_URL to the current SaleADS login page URL.",
				baseUrl != null && !baseUrl.isBlank());

		Files.createDirectories(EVIDENCE_DIR);

		final LinkedHashMap<String, StepOutcome> report = initializeReport();
		final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();
		final String googleAccount = readGoogleAccount();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(isHeadless()));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			Page appPage = context.newPage();
			appPage.navigate(baseUrl);
			waitForUi(appPage);

			try {
				appPage = runLoginStep(context, appPage, googleAccount);
				captureScreenshot(appPage, "01-dashboard-loaded.png", true);
				report.put(LOGIN_FIELD, StepOutcome.pass("Main interface and sidebar are visible."));
			} catch (Throwable e) {
				report.put(LOGIN_FIELD, StepOutcome.fail(buildErrorMessage(e)));
			}

			try {
				runMiNegocioMenuStep(appPage);
				captureScreenshot(appPage, "02-mi-negocio-menu-expanded.png", true);
				report.put(MENU_FIELD, StepOutcome.pass("Submenu expanded with both options visible."));
			} catch (Throwable e) {
				report.put(MENU_FIELD, StepOutcome.fail(buildErrorMessage(e)));
			}

			try {
				runAgregarNegocioModalStep(appPage);
				captureScreenshot(appPage, "03-agregar-negocio-modal.png", true);
				report.put(MODAL_FIELD, StepOutcome.pass("Modal fields and actions validated."));
			} catch (Throwable e) {
				report.put(MODAL_FIELD, StepOutcome.fail(buildErrorMessage(e)));
			}

			try {
				runAdministrarNegociosStep(appPage);
				captureScreenshot(appPage, "04-administrar-negocios-view.png", true);
				report.put(ADMIN_FIELD, StepOutcome.pass("Account page sections are visible."));
			} catch (Throwable e) {
				report.put(ADMIN_FIELD, StepOutcome.fail(buildErrorMessage(e)));
			}

			try {
				validateInformacionGeneral(appPage);
				report.put(GENERAL_FIELD, StepOutcome.pass("Profile, email, plan and action button are visible."));
			} catch (Throwable e) {
				report.put(GENERAL_FIELD, StepOutcome.fail(buildErrorMessage(e)));
			}

			try {
				validateDetallesCuenta(appPage);
				report.put(DETAILS_FIELD, StepOutcome.pass("Account metadata details are visible."));
			} catch (Throwable e) {
				report.put(DETAILS_FIELD, StepOutcome.fail(buildErrorMessage(e)));
			}

			try {
				validateTusNegocios(appPage);
				report.put(BUSINESS_FIELD, StepOutcome.pass("Business list, button and limit text are visible."));
			} catch (Throwable e) {
				report.put(BUSINESS_FIELD, StepOutcome.fail(buildErrorMessage(e)));
			}

			try {
				final String termsUrl = runLegalLinkValidation(context, appPage, "T\u00e9rminos y Condiciones",
						"T\u00e9rminos y Condiciones", "05-terminos-y-condiciones.png");
				legalUrls.put("T\u00e9rminos y Condiciones", termsUrl);
				report.put(TERMS_FIELD, StepOutcome.pass("Heading and legal content are visible."));
			} catch (Throwable e) {
				report.put(TERMS_FIELD, StepOutcome.fail(buildErrorMessage(e)));
			}

			try {
				final String privacyUrl = runLegalLinkValidation(context, appPage, "Pol\u00edtica de Privacidad",
						"Pol\u00edtica de Privacidad", "06-politica-de-privacidad.png");
				legalUrls.put("Pol\u00edtica de Privacidad", privacyUrl);
				report.put(PRIVACY_FIELD, StepOutcome.pass("Heading and legal content are visible."));
			} catch (Throwable e) {
				report.put(PRIVACY_FIELD, StepOutcome.fail(buildErrorMessage(e)));
			}
		}

		writeFinalReport(report, legalUrls);
		assertNoFailures(report);
	}

	private static Page runLoginStep(final BrowserContext context, final Page appPage, final String googleAccount) {
		final Locator loginControl = waitForAnyText(appPage, List.of("Sign in with Google", "Iniciar sesi\u00f3n con Google",
				"Continuar con Google", "Acceder con Google", "Login with Google", "Google"), 25000L);

		final Page popup = clickAndMaybeCaptureNewPage(context, () -> loginControl.click(), 8000d);
		if (popup != null) {
			waitForUi(popup);
			maybeSelectGoogleAccount(popup, googleAccount);
		} else if (appPage.url() != null && appPage.url().contains("accounts.google")) {
			maybeSelectGoogleAccount(appPage, googleAccount);
		}

		waitForUi(appPage);
		if (popup != null && !popup.isClosed()) {
			waitForUi(popup);
		}

		Page activePage = appPage;
		if (popup != null && !popup.isClosed() && isSidebarVisible(popup)) {
			activePage = popup;
		}
		if (!isSidebarVisible(activePage) && isSidebarVisible(appPage)) {
			activePage = appPage;
		}

		assertCondition(isSidebarVisible(activePage), "Main app sidebar was not visible after login.");
		return activePage;
	}

	private static void runMiNegocioMenuStep(final Page page) {
		waitForAnyText(page, List.of("Negocio", "Mi Negocio"), 15000L);
		clickIfVisible(page, "Negocio");
		clickByVisibleText(page, "Mi Negocio");
		waitForUi(page);

		assertTextVisible(page, "Agregar Negocio");
		assertTextVisible(page, "Administrar Negocios");
	}

	private static void runAgregarNegocioModalStep(final Page page) {
		clickByVisibleText(page, "Agregar Negocio");
		waitForUi(page);

		assertTextVisible(page, "Crear Nuevo Negocio");
		assertTextVisible(page, "Nombre del Negocio");
		assertTextVisible(page, "Tienes 2 de 3 negocios");
		assertTextVisible(page, "Cancelar");
		assertTextVisible(page, "Crear Negocio");

		final Locator input = findBusinessNameInput(page);
		input.click();
		input.fill("Negocio Prueba Automatizaci\u00f3n");
		clickByVisibleText(page, "Cancelar");
		waitForUi(page);
	}

	private static void runAdministrarNegociosStep(final Page page) {
		if (!isTextVisible(page, "Administrar Negocios", 2000L)) {
			clickIfVisible(page, "Mi Negocio");
		}

		clickByVisibleText(page, "Administrar Negocios");
		waitForUi(page);

		assertTextVisible(page, "Informaci\u00f3n General");
		assertTextVisible(page, "Detalles de la Cuenta");
		assertTextVisible(page, "Tus Negocios");
		assertTextVisible(page, "Secci\u00f3n Legal");
	}

	private static void validateInformacionGeneral(final Page page) {
		final String currentPageText = page.locator("body").innerText();
		assertCondition(currentPageText.contains("@"), "User email is not visible in Informaci\u00f3n General.");
		assertTextVisible(page, "BUSINESS PLAN");
		assertTextVisible(page, "Cambiar Plan");
	}

	private static void validateDetallesCuenta(final Page page) {
		assertTextVisible(page, "Cuenta creada");
		assertTextVisible(page, "Estado activo");
		assertTextVisible(page, "Idioma seleccionado");
	}

	private static void validateTusNegocios(final Page page) {
		assertTextVisible(page, "Tus Negocios");
		assertTextVisible(page, "Agregar Negocio");
		assertTextVisible(page, "Tienes 2 de 3 negocios");
	}

	private static String runLegalLinkValidation(final BrowserContext context, final Page appPage, final String linkLabel,
			final String heading, final String screenshotName) {
		final Locator link = waitForAnyText(appPage, List.of(linkLabel), 15000L);
		final Page popup = clickAndMaybeCaptureNewPage(context, () -> link.click(), 6000d);
		final Page legalPage = popup != null ? popup : appPage;

		waitForUi(legalPage);
		assertTextVisible(legalPage, heading);
		final String bodyText = legalPage.locator("body").innerText();
		assertCondition(bodyText != null && bodyText.trim().length() > 120, "Legal content is not visible for " + heading + ".");

		captureScreenshot(legalPage, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (popup != null) {
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private static void maybeSelectGoogleAccount(final Page googlePage, final String accountEmail) {
		waitForUi(googlePage);
		if (isTextVisible(googlePage, accountEmail, 6000L)) {
			clickByVisibleText(googlePage, accountEmail);
			waitForUi(googlePage);
		}
	}

	private static boolean isSidebarVisible(final Page page) {
		try {
			final Locator aside = page.locator("aside");
			if (isVisible(aside, 0)) {
				return true;
			}

			final Locator nav = page.getByRole(AriaRole.NAVIGATION);
			if (isVisible(nav, 0)) {
				return true;
			}

			return isTextVisible(page, "Mi Negocio", 1000L) || isTextVisible(page, "Negocio", 1000L);
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private static void clickByVisibleText(final Page page, final String text) {
		final Locator locator = waitForAnyText(page, List.of(text), 15000L);
		locator.click();
		waitForUi(page);
	}

	private static void clickIfVisible(final Page page, final String text) {
		final Locator locator = findVisibleByText(page, text);
		if (locator != null) {
			locator.click();
			waitForUi(page);
		}
	}

	private static Locator waitForAnyText(final Page page, final List<String> texts, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final String text : texts) {
				final Locator locator = findVisibleByText(page, text);
				if (locator != null) {
					return locator;
				}
			}
			page.waitForTimeout(250);
		}
		throw new AssertionError("Unable to find visible text: " + texts);
	}

	private static Locator findVisibleByText(final Page page, final String text) {
		final List<Locator> candidates = new ArrayList<>();
		candidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)));
		candidates.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)));
		candidates.add(page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(text)));
		candidates.add(page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(text)));
		candidates.add(page.getByText(text, new Page.GetByTextOptions().setExact(true)));
		candidates.add(page.getByText(text, new Page.GetByTextOptions().setExact(false)));

		for (final Locator candidate : candidates) {
			final int limit = (int) Math.min(candidate.count(), 3);
			for (int i = 0; i < limit; i++) {
				final Locator nth = candidate.nth(i);
				if (isVisible(nth, 0)) {
					return nth;
				}
			}
		}
		return null;
	}

	private static Locator findBusinessNameInput(final Page page) {
		final List<Locator> candidates = new ArrayList<>();
		candidates.add(page.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)));
		candidates.add(page.getByPlaceholder("Nombre del Negocio", new Page.GetByPlaceholderOptions().setExact(false)));
		candidates.add(page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Nombre del Negocio")));
		candidates.add(page.locator("input[placeholder*='Negocio']"));

		for (final Locator candidate : candidates) {
			final int limit = (int) Math.min(candidate.count(), 3);
			for (int i = 0; i < limit; i++) {
				final Locator nth = candidate.nth(i);
				if (isVisible(nth, 0)) {
					return nth;
				}
			}
		}
		throw new AssertionError("Input field 'Nombre del Negocio' was not found.");
	}

	private static void assertTextVisible(final Page page, final String text) {
		assertCondition(isTextVisible(page, text, 15000L), "Expected text is not visible: " + text);
	}

	private static boolean isTextVisible(final Page page, final String text, final long timeoutMs) {
		try {
			waitForAnyText(page, List.of(text), timeoutMs);
			return true;
		} catch (AssertionError e) {
			return false;
		}
	}

	private static boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			if (timeoutMs > 0d) {
				locator.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
			}
			return locator.isVisible();
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000d));
		} catch (PlaywrightException ignored) {
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000d));
		} catch (PlaywrightException ignored) {
		}
		page.waitForTimeout(350);
	}

	private static Page clickAndMaybeCaptureNewPage(final BrowserContext context, final Runnable clickAction,
			final double timeoutMs) {
		try {
			return context.waitForPage(clickAction, new BrowserContext.WaitForPageOptions().setTimeout(timeoutMs));
		} catch (PlaywrightException ignored) {
			return null;
		}
	}

	private static void captureScreenshot(final Page page, final String screenshotName, final boolean fullPage) {
		final Path path = EVIDENCE_DIR.resolve(screenshotName);
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private static void writeFinalReport(final Map<String, StepOutcome> report, final Map<String, String> legalUrls)
			throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test\n");
		sb.append("Generated: ").append(Instant.now()).append('\n');
		sb.append('\n');

		for (final Map.Entry<String, StepOutcome> entry : report.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().passed ? "PASS" : "FAIL");
			sb.append(" | ").append(entry.getValue().details).append('\n');
		}

		sb.append('\n');
		sb.append("Captured URLs:\n");
		sb.append("- T\u00e9rminos y Condiciones: ").append(legalUrls.getOrDefault("T\u00e9rminos y Condiciones", "N/A"))
				.append('\n');
		sb.append("- Pol\u00edtica de Privacidad: ").append(legalUrls.getOrDefault("Pol\u00edtica de Privacidad", "N/A"))
				.append('\n');

		Files.writeString(EVIDENCE_DIR.resolve("final-report.txt"), sb.toString(), StandardCharsets.UTF_8);
	}

	private static void assertNoFailures(final Map<String, StepOutcome> report) {
		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, StepOutcome> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				failed.add(entry.getKey() + " -> " + entry.getValue().details);
			}
		}

		Assert.assertTrue(
				"Some validations failed. Review target/saleads-evidence/final-report.txt for details.\n" + String.join("\n",
						failed),
				failed.isEmpty());
	}

	private static LinkedHashMap<String, StepOutcome> initializeReport() {
		final LinkedHashMap<String, StepOutcome> report = new LinkedHashMap<>();
		report.put(LOGIN_FIELD, StepOutcome.fail("Not executed"));
		report.put(MENU_FIELD, StepOutcome.fail("Not executed"));
		report.put(MODAL_FIELD, StepOutcome.fail("Not executed"));
		report.put(ADMIN_FIELD, StepOutcome.fail("Not executed"));
		report.put(GENERAL_FIELD, StepOutcome.fail("Not executed"));
		report.put(DETAILS_FIELD, StepOutcome.fail("Not executed"));
		report.put(BUSINESS_FIELD, StepOutcome.fail("Not executed"));
		report.put(TERMS_FIELD, StepOutcome.fail("Not executed"));
		report.put(PRIVACY_FIELD, StepOutcome.fail("Not executed"));
		return report;
	}

	private static String readGoogleAccount() {
		final String fromEnv = System.getenv(ENV_GOOGLE_ACCOUNT);
		return fromEnv == null || fromEnv.isBlank() ? DEFAULT_GOOGLE_ACCOUNT : fromEnv;
	}

	private static boolean isHeadless() {
		final String configured = System.getenv(ENV_HEADLESS);
		return configured == null || Boolean.parseBoolean(configured);
	}

	private static void assertCondition(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static String buildErrorMessage(final Throwable exception) {
		final String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}
		return message.replace('\n', ' ').replace('\r', ' ').trim();
	}

	private static final class StepOutcome {
		private final boolean passed;
		private final String details;

		private StepOutcome(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepOutcome pass(final String details) {
			return new StepOutcome(true, details);
		}

		private static StepOutcome fail(final String details) {
			return new StepOutcome(false, details);
		}
	}
}
