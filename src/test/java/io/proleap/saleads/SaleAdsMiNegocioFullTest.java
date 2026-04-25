package io.proleap.saleads;

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
import java.util.stream.Collectors;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleAdsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String DEFAULT_SCREENSHOT_ROOT = "target/saleads-mi-negocio-screenshots";

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"Set -Dsaleads.login.url or SALEADS_LOGIN_URL to the current environment login page.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(
				firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "true"));
		final int slowMoMs = Integer.parseInt(firstNonBlank(System.getProperty("saleads.slowmo.ms"),
				System.getenv("SALEADS_SLOW_MO_MS"), "0"));
		final Path screenshotDir = createScreenshotDir();

		final Map<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);

		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo((double) slowMoMs));
			final BrowserContext context = browser.newContext();
			final Page appPage = context.newPage();

			appPage.navigate(loginUrl);
			waitForUiToLoad(appPage);

			// Step 1: Login with Google and validate app shell appears.
			final Locator loginButton = firstVisibleOrThrow(appPage, 20000, "button:has-text('Google')",
					"[role='button']:has-text('Google')", "text=/sign\\s*in\\s*with\\s*google/i",
					"text=/iniciar\\s+sesi[oó]n\\s+con\\s+google/i", "text=/continuar\\s+con\\s+google/i");
			final int pagesBeforeLogin = context.pages().size();
			clickAndWaitUi(appPage, loginButton);
			final Page googlePopup = waitForNewPage(context, pagesBeforeLogin, 10000);
			trySelectGoogleAccount(context, appPage, googlePopup, GOOGLE_ACCOUNT_EMAIL);
			final boolean sidebarVisibleAfterLogin = waitForAnyVisible(appPage, 60000, "aside:has-text('Negocio')",
					"nav:has-text('Negocio')", "text=/^Negocio$/i", "text=/^Mi\\s+Negocio$/i");
			final boolean mainInterfaceVisible = waitForAnyVisible(appPage, 60000, "main", "aside", "nav");
			final boolean loginPassed = sidebarVisibleAfterLogin && mainInterfaceVisible;
			record(report, failures, "Login", loginPassed,
					"Expected app interface and left sidebar after Google login.");
			screenshot(appPage, screenshotDir, "01-dashboard-loaded", false);

			// Step 2: Open Mi Negocio and validate submenu items.
			final Locator negocio = firstVisibleOrThrow(appPage, 20000, "text=/^Negocio$/i", "aside text=/Negocio/i",
					"nav text=/Negocio/i");
			clickAndWaitUi(appPage, negocio);
			final Locator miNegocio = firstVisibleOrThrow(appPage, 20000, "text=/^Mi\\s+Negocio$/i",
					"aside text=/Mi\\s+Negocio/i", "nav text=/Mi\\s+Negocio/i");
			clickAndWaitUi(appPage, miNegocio);
			final boolean agregarNegocioVisible = isVisible(appPage.locator("text=/^Agregar\\s+Negocio$/i").first(),
					10000);
			final boolean administrarNegociosVisible = isVisible(
					appPage.locator("text=/^Administrar\\s+Negocios$/i").first(), 10000);
			record(report, failures, "Mi Negocio menu", agregarNegocioVisible && administrarNegociosVisible,
					"Expected expanded submenu with Agregar Negocio and Administrar Negocios.");
			screenshot(appPage, screenshotDir, "02-mi-negocio-menu-expanded", false);

			// Step 3: Validate Agregar Negocio modal.
			clickAndWaitUi(appPage.locator("text=/^Agregar\\s+Negocio$/i").first(), appPage);
			final Locator modalTitle = appPage.locator("text=/^Crear\\s+Nuevo\\s+Negocio$/i").first();
			final Locator nombreInput = appPage
					.locator("input[placeholder*='Nombre'], input[aria-label*='Nombre'], input[name*='nombre']")
					.first();
			final Locator quotaText = appPage.locator("text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i").first();
			final Locator cancelar = appPage.locator("button:has-text('Cancelar'), [role='button']:has-text('Cancelar')")
					.first();
			final Locator crear = appPage.locator(
					"button:has-text('Crear Negocio'), [role='button']:has-text('Crear Negocio')")
					.first();
			final boolean modalPassed = isVisible(modalTitle, 10000) && isVisible(nombreInput, 10000)
					&& isVisible(quotaText, 10000) && isVisible(cancelar, 10000) && isVisible(crear, 10000);
			record(report, failures, "Agregar Negocio modal", modalPassed, "Agregar Negocio modal content mismatch.");
			screenshot(appPage, screenshotDir, "03-agregar-negocio-modal", false);
			if (isVisible(nombreInput, 3000)) {
				nombreInput.click();
				nombreInput.fill("Negocio Prueba Automatización");
				waitForUiToLoad(appPage);
			}
			if (isVisible(cancelar, 3000)) {
				clickAndWaitUi(appPage, cancelar);
			}

			// Step 4: Open Administrar Negocios and validate sections.
			if (!isVisible(appPage.locator("text=/^Administrar\\s+Negocios$/i").first(), 3000)) {
				final Locator miNegocioAgain = firstVisibleOrThrow(appPage, 10000, "text=/^Mi\\s+Negocio$/i");
				clickAndWaitUi(appPage, miNegocioAgain);
			}
			final Locator administrar = firstVisibleOrThrow(appPage, 15000, "text=/^Administrar\\s+Negocios$/i");
			clickAndWaitUi(appPage, administrar);
			final boolean informacionGeneral = waitForAnyVisible(appPage, 20000, "text=/Informaci[oó]n\\s+General/i");
			final boolean detallesCuenta = waitForAnyVisible(appPage, 20000, "text=/Detalles\\s+de\\s+la\\s+Cuenta/i");
			final boolean tusNegocios = waitForAnyVisible(appPage, 20000, "text=/Tus\\s+Negocios/i");
			final boolean seccionLegal = waitForAnyVisible(appPage, 20000, "text=/Secci[oó]n\\s+Legal/i");
			record(report, failures, "Administrar Negocios view",
					informacionGeneral && detallesCuenta && tusNegocios && seccionLegal,
					"Expected all account sections in Administrar Negocios.");
			screenshot(appPage, screenshotDir, "04-administrar-negocios-account-page", true);

			// Step 5: Validate Información General.
			final boolean userNameVisible = waitForAnyVisible(appPage, 10000, "text=/Nombre\\s+de\\s+Usuario/i",
					"text=/Nombre/i", "text=/Usuario/i");
			final boolean userEmailVisible = waitForAnyVisible(appPage, 10000, "text=/" + GOOGLE_ACCOUNT_EMAIL + "/i",
					"text=/@/i");
			final boolean businessPlanVisible = waitForAnyVisible(appPage, 10000, "text=/BUSINESS\\s+PLAN/i");
			final boolean cambiarPlanVisible = waitForAnyVisible(appPage, 10000, "text=/Cambiar\\s+Plan/i");
			record(report, failures, "Información General",
					userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible,
					"Información General fields missing.");

			// Step 6: Validate Detalles de la Cuenta.
			final boolean cuentaCreadaVisible = waitForAnyVisible(appPage, 10000, "text=/Cuenta\\s+creada/i");
			final boolean estadoActivoVisible = waitForAnyVisible(appPage, 10000, "text=/Estado\\s+activo/i");
			final boolean idiomaSeleccionadoVisible = waitForAnyVisible(appPage, 10000,
					"text=/Idioma\\s+seleccionado/i");
			record(report, failures, "Detalles de la Cuenta",
					cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible,
					"Detalles de la Cuenta fields missing.");

			// Step 7: Validate Tus Negocios.
			final boolean businessListVisible = waitForAnyVisible(appPage, 10000, "text=/Tus\\s+Negocios/i",
					"text=/Negocios/i");
			final boolean agregarNegocioButtonVisible = waitForAnyVisible(appPage, 10000, "text=/Agregar\\s+Negocio/i");
			final boolean negociosQuotaVisible = waitForAnyVisible(appPage, 10000,
					"text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i");
			record(report, failures, "Tus Negocios",
					businessListVisible && agregarNegocioButtonVisible && negociosQuotaVisible,
					"Tus Negocios section elements missing.");

			// Step 8: Validate Términos y Condiciones and return to app tab.
			final LegalValidationResult termsResult = validateLegalLinkAndReturn(context, appPage, screenshotDir,
					"Términos y Condiciones", "Términos y Condiciones", "05-terminos-y-condiciones");
			record(report, failures, "Términos y Condiciones", termsResult.pass, termsResult.failureMessage);
			legalUrls.put("Términos y Condiciones URL", termsResult.finalUrl);

			// Step 9: Validate Política de Privacidad and return to app tab.
			final LegalValidationResult privacyResult = validateLegalLinkAndReturn(context, appPage, screenshotDir,
					"Política de Privacidad", "Política de Privacidad", "06-politica-de-privacidad");
			record(report, failures, "Política de Privacidad", privacyResult.pass, privacyResult.failureMessage);
			legalUrls.put("Política de Privacidad URL", privacyResult.finalUrl);

			printFinalReport(report, legalUrls, screenshotDir);

			final List<String> failedFields = report.entrySet().stream().filter(entry -> !entry.getValue())
					.map(Map.Entry::getKey).collect(Collectors.toList());
			assertTrue("Some report fields failed validations: " + failedFields + ". Details: " + failures,
					failedFields.isEmpty());
		}
	}

	private static LegalValidationResult validateLegalLinkAndReturn(final BrowserContext context, final Page appPage,
			final Path screenshotDir, final String linkText, final String expectedHeading, final String screenshotName) {
		final Locator legalLink = firstVisibleOrThrow(appPage, 15000, "text=/^" + linkText + "$/i",
				"text=/" + linkText + "/i");
		final int pagesBeforeClick = context.pages().size();
		clickAndWaitUi(appPage, legalLink);

		Page legalPage = waitForNewPage(context, pagesBeforeClick, 7000);
		boolean openedNewTab = legalPage != null;
		if (legalPage == null) {
			legalPage = appPage;
		} else {
			legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			waitForUiToLoad(legalPage);
		}

		final boolean headingVisible = waitForAnyVisible(legalPage, 15000,
				"text=/^" + expectedHeading + "$/i", "h1:has-text('" + expectedHeading + "')",
				"text=/" + expectedHeading + "/i");
		final boolean legalContentVisible = waitForAnyVisible(legalPage, 15000, "main p", "article p", "p", "li");
		screenshot(legalPage, screenshotDir, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiToLoad(appPage);
		} else {
			appPage.goBack();
			waitForUiToLoad(appPage);
		}

		final boolean pass = headingVisible && legalContentVisible;
		final String failureMessage = pass ? ""
				: "Failed legal page checks for [" + linkText + "]. headingVisible=" + headingVisible
						+ ", legalContentVisible=" + legalContentVisible + ", finalUrl=" + finalUrl;
		return new LegalValidationResult(pass, finalUrl, failureMessage);
	}

	private static void trySelectGoogleAccount(final BrowserContext context, final Page appPage, final Page popup,
			final String email) {
		final long deadline = System.currentTimeMillis() + 25000;
		while (System.currentTimeMillis() < deadline) {
			final List<Page> candidates = new ArrayList<>(context.pages());
			if (popup != null && !candidates.contains(popup)) {
				candidates.add(popup);
			}
			if (!candidates.contains(appPage)) {
				candidates.add(appPage);
			}

			for (final Page candidate : candidates) {
				try {
					final Locator account = candidate.locator("text=" + email).first();
					if (isVisible(account, 750)) {
						account.click();
						waitForUiToLoad(candidate);
						return;
					}
				} catch (final PlaywrightException ignored) {
					// Continue trying other pages and polling until timeout.
				}
			}

			appPage.waitForTimeout(500);
		}
	}

	private static Page waitForNewPage(final BrowserContext context, final int pagesBefore, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (context.pages().size() > pagesBefore) {
				return context.pages().get(context.pages().size() - 1);
			}
			try {
				context.pages().get(0).waitForTimeout(200);
			} catch (final PlaywrightException ignored) {
				// Ignore transient browser context events.
			}
		}
		return null;
	}

	private static void clickAndWaitUi(final Locator locator, final Page page) {
		locator.click();
		waitForUiToLoad(page);
	}

	private static void clickAndWaitUi(final Page page, final Locator locator) {
		locator.click();
		waitForUiToLoad(page);
	}

	private static void waitForUiToLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// DOMCONTENTLOADED may already have fired.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			// Some apps keep a long-polling connection open. We still keep an explicit wait.
		}
		page.waitForTimeout(700);
	}

	private static boolean waitForAnyVisible(final Page page, final int timeoutMs, final String... selectors) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final String selector : selectors) {
				final Locator locator = page.locator(selector).first();
				if (isVisible(locator, 250)) {
					return true;
				}
			}
			page.waitForTimeout(300);
		}
		return false;
	}

	private static Locator firstVisibleOrThrow(final Page page, final int timeoutMs, final String... selectors) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final String selector : selectors) {
				final Locator locator = page.locator(selector).first();
				if (isVisible(locator, 350)) {
					return locator;
				}
			}
			page.waitForTimeout(300);
		}
		throw new AssertionError("None of selectors became visible: " + String.join(", ", selectors));
	}

	private static boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout((double) timeoutMs));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static void record(final Map<String, Boolean> report, final List<String> failures, final String field,
			final boolean pass, final String failureMessage) {
		report.put(field, pass);
		if (!pass) {
			failures.add(field + ": " + failureMessage);
		}
	}

	private static void screenshot(final Page page, final Path screenshotDir, final String name, final boolean fullPage) {
		final Path targetPath = screenshotDir.resolve(name + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(targetPath).setFullPage(fullPage));
	}

	private static Path createScreenshotDir() throws Exception {
		final String root = firstNonBlank(System.getProperty("saleads.screenshot.dir"),
				System.getenv("SALEADS_SCREENSHOT_DIR"), DEFAULT_SCREENSHOT_ROOT);
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Paths.get(root, timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private static void printFinalReport(final Map<String, Boolean> report, final Map<String, String> legalUrls,
			final Path screenshotDir) {
		System.out.println("===== saleads_mi_negocio_full_test report =====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		for (final Map.Entry<String, String> urlEntry : legalUrls.entrySet()) {
			System.out.println(urlEntry.getKey() + ": " + urlEntry.getValue());
		}
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
	}

	private static final class LegalValidationResult {
		private final boolean pass;
		private final String finalUrl;
		private final String failureMessage;

		private LegalValidationResult(final boolean pass, final String finalUrl, final String failureMessage) {
			this.pass = pass;
			this.finalUrl = finalUrl;
			this.failureMessage = failureMessage;
		}
	}
}
