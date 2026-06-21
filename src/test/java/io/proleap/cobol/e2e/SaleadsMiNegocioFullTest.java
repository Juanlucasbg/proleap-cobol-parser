package io.proleap.cobol.e2e;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

/**
 * Full E2E workflow for SaleADS "Mi Negocio" module.
 *
 * Runtime configuration:
 * - SALEADS_LOGIN_URL: login page URL for any target environment (required).
 * - SALEADS_HEADLESS: "true" or "false" (optional, default true).
 *
 * This test deliberately avoids hardcoded domains and uses visible-text selectors whenever possible.
 */
public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEST_NAME = "saleads_mi_negocio_full_test";

	@Test
	public void saleadsMiNegocioWorkflow() throws IOException {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to run this environment-agnostic E2E test.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		final Map<String, Boolean> finalReport = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();
		final List<String> evidenceNotes = new ArrayList<>();
		final Path evidenceDir = buildEvidenceDir();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(200));
			final BrowserContext context = browser.newContext();
			final Page appPage = context.newPage();

			appPage.navigate(loginUrl);
			waitForUiLoad(appPage);

			// Step 1: Login with Google
			final boolean loginClicked = clickVisibleText(appPage, "Sign in with Google", "Iniciar sesión con Google",
					"Continuar con Google", "Google");
			if (loginClicked) {
				waitForUiLoad(appPage);
				pickGoogleAccountIfVisible(context, GOOGLE_ACCOUNT_EMAIL);
				waitForUiLoad(appPage);
			}

			final boolean mainInterfaceVisible = isAnyTextVisible(appPage, "Negocio", "Mi Negocio", "Dashboard", "Inicio");
			final boolean leftSidebarVisible = isLeftSidebarVisible(appPage);
			captureScreenshot(appPage, evidenceDir.resolve("01-dashboard-loaded.png"), true);
			recordStep(finalReport, failures, "Login", mainInterfaceVisible && leftSidebarVisible,
					"Main interface or left sidebar not visible after Google login.");

			// Step 2: Open Mi Negocio menu
			clickVisibleText(appPage, "Negocio");
			clickVisibleText(appPage, "Mi Negocio");
			final boolean agregarNegocioVisibleInMenu = isAnyTextVisible(appPage, "Agregar Negocio");
			final boolean administrarNegociosVisibleInMenu = isAnyTextVisible(appPage, "Administrar Negocios");
			captureScreenshot(appPage, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), true);
			recordStep(finalReport, failures, "Mi Negocio menu",
					agregarNegocioVisibleInMenu && administrarNegociosVisibleInMenu,
					"Mi Negocio menu did not show expected submenu options.");

			// Step 3: Validate Agregar Negocio modal
			clickVisibleText(appPage, "Agregar Negocio");
			final boolean modalTitleVisible = waitForAnyTextVisible(appPage, 8000, "Crear Nuevo Negocio");
			final boolean nombreDelNegocioInputVisible = isAnyTextVisible(appPage, "Nombre del Negocio");
			final boolean businessQuotaVisible = isAnyTextVisible(appPage, "Tienes 2 de 3 negocios");
			final boolean modalButtonsVisible = isAnyTextVisible(appPage, "Cancelar")
					&& isAnyTextVisible(appPage, "Crear Negocio");
			captureScreenshot(appPage, evidenceDir.resolve("03-agregar-negocio-modal.png"), true);

			clickVisibleText(appPage, "Nombre del Negocio");
			fillIfInputVisible(appPage, "Negocio Prueba Automatización");
			clickVisibleText(appPage, "Cancelar");
			waitForUiLoad(appPage);

			recordStep(finalReport, failures, "Agregar Negocio modal",
					modalTitleVisible && nombreDelNegocioInputVisible && businessQuotaVisible && modalButtonsVisible,
					"Agregar Negocio modal did not match expected title/fields/buttons.");

			// Step 4: Open Administrar Negocios view
			clickVisibleText(appPage, "Mi Negocio");
			clickVisibleText(appPage, "Administrar Negocios");
			waitForUiLoad(appPage);
			final boolean infoGeneralSection = isAnyTextVisible(appPage, "Información General");
			final boolean detallesCuentaSection = isAnyTextVisible(appPage, "Detalles de la Cuenta");
			final boolean tusNegociosSection = isAnyTextVisible(appPage, "Tus Negocios");
			final boolean seccionLegalSection = isAnyTextVisible(appPage, "Sección Legal");
			captureScreenshot(appPage, evidenceDir.resolve("04-administrar-negocios-full-page.png"), true);
			recordStep(finalReport, failures, "Administrar Negocios view",
					infoGeneralSection && detallesCuentaSection && tusNegociosSection && seccionLegalSection,
					"Administrar Negocios sections are incomplete.");

			// Step 5: Validate Información General
			final boolean userNameVisible = isUserNameVisible(appPage);
			final boolean userEmailVisible = isUserEmailVisible(appPage);
			final boolean businessPlanVisible = isAnyTextVisible(appPage, "BUSINESS PLAN");
			final boolean cambiarPlanVisible = isAnyTextVisible(appPage, "Cambiar Plan");
			recordStep(finalReport, failures, "Información General",
					userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible,
					"Información General did not include name/email/plan/actions.");

			// Step 6: Validate Detalles de la Cuenta
			final boolean cuentaCreadaVisible = isAnyTextVisible(appPage, "Cuenta creada");
			final boolean estadoActivoVisible = isAnyTextVisible(appPage, "Estado activo");
			final boolean idiomaSeleccionadoVisible = isAnyTextVisible(appPage, "Idioma seleccionado");
			recordStep(finalReport, failures, "Detalles de la Cuenta",
					cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible,
					"Detalles de la Cuenta missing expected fields.");

			// Step 7: Validate Tus Negocios
			final boolean businessListVisible = isAnyTextVisible(appPage, "Tus Negocios");
			final boolean addBusinessButtonVisible = isAnyTextVisible(appPage, "Agregar Negocio");
			final boolean quotaVisibleInBusinessSection = isAnyTextVisible(appPage, "Tienes 2 de 3 negocios");
			recordStep(finalReport, failures, "Tus Negocios",
					businessListVisible && addBusinessButtonVisible && quotaVisibleInBusinessSection,
					"Tus Negocios section missing list/button/quota.");

			// Step 8: Validate Términos y Condiciones
			final LegalValidationResult termsResult = validateLegalLink(context, appPage, evidenceDir,
					"05-terminos-y-condiciones.png", "Términos y Condiciones", "Términos y Condiciones");
			evidenceNotes.add("Términos y Condiciones URL: " + termsResult.url);
			recordStep(finalReport, failures, "Términos y Condiciones", termsResult.passed,
					"Términos y Condiciones page heading/content validation failed.");

			// Step 9: Validate Política de Privacidad
			final LegalValidationResult privacyResult = validateLegalLink(context, appPage, evidenceDir,
					"06-politica-de-privacidad.png", "Política de Privacidad", "Política de Privacidad");
			evidenceNotes.add("Política de Privacidad URL: " + privacyResult.url);
			recordStep(finalReport, failures, "Política de Privacidad", privacyResult.passed,
					"Política de Privacidad page heading/content validation failed.");
		}

		writeFinalReport(evidenceDir, finalReport, evidenceNotes);

		if (!failures.isEmpty()) {
			fail("SaleADS Mi Negocio full workflow failures:\n - " + String.join("\n - ", failures));
		}
	}

	private static LegalValidationResult validateLegalLink(final BrowserContext context, final Page appPage,
			final Path evidenceDir, final String screenshotName, final String linkText, final String headingText) {
		final int previousPageCount = context.pages().size();
		final boolean clicked = clickVisibleText(appPage, linkText);
		if (!clicked) {
			return new LegalValidationResult(false, "N/A");
		}

		final Page legalPage = waitForPotentialNewTab(context, appPage, previousPageCount);
		waitForUiLoad(legalPage);

		final boolean headingVisible = isAnyTextVisible(legalPage, headingText);
		final boolean legalContentVisible = hasNonTrivialContent(legalPage);
		captureScreenshot(legalPage, evidenceDir.resolve(screenshotName), true);

		final String finalUrl = legalPage.url();
		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		}

		return new LegalValidationResult(headingVisible && legalContentVisible, finalUrl);
	}

	private static Page waitForPotentialNewTab(final BrowserContext context, final Page appPage,
			final int previousPageCount) {
		final long deadlineMs = System.currentTimeMillis() + 6000;
		while (System.currentTimeMillis() < deadlineMs) {
			if (context.pages().size() > previousPageCount) {
				return context.pages().get(context.pages().size() - 1);
			}
			appPage.waitForTimeout(250);
		}
		return appPage;
	}

	private static void pickGoogleAccountIfVisible(final BrowserContext context, final String email) {
		final long deadlineMs = System.currentTimeMillis() + 20000;
		while (System.currentTimeMillis() < deadlineMs) {
			for (final Page page : context.pages()) {
				final Locator emailOption = page.getByText(email, new Page.GetByTextOptions().setExact(false)).first();
				if (isVisible(emailOption, 1000)) {
					emailOption.click();
					waitForUiLoad(page);
					return;
				}
			}

			for (final Page page : context.pages()) {
				if (isAnyTextVisible(page, "Choose an account", "Elige una cuenta")) {
					page.waitForTimeout(300);
				}
			}
			context.pages().get(0).waitForTimeout(300);
		}
	}

	private static boolean isUserNameVisible(final Page page) {
		final Locator textNodes = page.locator("h1,h2,h3,p,span,div");
		try {
			final int sample = Math.min(textNodes.count(), 40);
			for (int i = 0; i < sample; i++) {
				final String text = textNodes.nth(i).innerText().trim();
				if (!text.isEmpty() && !text.contains("@") && text.split("\\s+").length >= 2
						&& text.length() >= 5 && !text.equalsIgnoreCase("Información General")) {
					return true;
				}
			}
		} catch (final PlaywrightException ignored) {
			return false;
		}
		return false;
	}

	private static boolean isUserEmailVisible(final Page page) {
		return hasVisibleRegexLikeText(page, "@", ".");
	}

	private static boolean hasVisibleRegexLikeText(final Page page, final String mandatoryPart,
			final String secondaryPart) {
		final Locator textNodes = page.locator("p,span,div,a");
		try {
			final int sample = Math.min(textNodes.count(), 120);
			for (int i = 0; i < sample; i++) {
				final String text = textNodes.nth(i).innerText().trim();
				if (!text.isEmpty() && text.contains(mandatoryPart) && text.contains(secondaryPart)) {
					return true;
				}
			}
		} catch (final PlaywrightException ignored) {
			return false;
		}
		return false;
	}

	private static boolean hasNonTrivialContent(final Page page) {
		try {
			final String text = page.locator("body").innerText();
			return text != null && text.trim().length() >= 120;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static boolean isLeftSidebarVisible(final Page page) {
		try {
			final Locator sidebar = page.locator("aside, nav").first();
			return isVisible(sidebar, 6000);
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static void fillIfInputVisible(final Page page, final String value) {
		final Locator input = page.locator("input").first();
		if (isVisible(input, 2500)) {
			input.fill(value);
		}
	}

	private static boolean clickVisibleText(final Page page, final String... labels) {
		for (final String label : labels) {
			final Locator candidate = page.getByText(label, new Page.GetByTextOptions().setExact(false)).first();
			if (isVisible(candidate, 2500)) {
				candidate.click();
				waitForUiLoad(page);
				return true;
			}
		}
		return false;
	}

	private static boolean waitForAnyTextVisible(final Page page, final int timeoutMs, final String... labels) {
		final long deadlineMs = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadlineMs) {
			if (isAnyTextVisible(page, labels)) {
				return true;
			}
			page.waitForTimeout(200);
		}
		return false;
	}

	private static boolean isAnyTextVisible(final Page page, final String... labels) {
		for (final String label : labels) {
			final Locator candidate = page.getByText(label, new Page.GetByTextOptions().setExact(false)).first();
			if (isVisible(candidate, 1800)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return locator.isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// ignore transient load-state issues and continue with explicit waits by visible text
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (final PlaywrightException ignored) {
			// some pages continuously poll; continue after a bounded delay
		}

		page.waitForTimeout(700);
	}

	private static void captureScreenshot(final Page page, final Path path, final boolean fullPage) {
		try {
			page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
		} catch (final PlaywrightException ignored) {
			// best effort evidence capture
		}
	}

	private static Path buildEvidenceDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		final Path path = Path.of("target", "evidence", TEST_NAME, timestamp);
		Files.createDirectories(path);
		return path;
	}

	private static void writeFinalReport(final Path evidenceDir, final Map<String, Boolean> finalReport,
			final List<String> evidenceNotes) throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("Test: ").append(TEST_NAME).append('\n');
		sb.append("Generated at: ").append(LocalDateTime.now()).append('\n');
		sb.append('\n');
		sb.append("Final Report (PASS/FAIL):").append('\n');
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append('\n');
		}

		if (!evidenceNotes.isEmpty()) {
			sb.append('\n').append("Evidence Notes:").append('\n');
			for (final String note : evidenceNotes) {
				sb.append("- ").append(note).append('\n');
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), sb.toString());
		System.out.println(sb);
	}

	private static void recordStep(final Map<String, Boolean> finalReport, final List<String> failures, final String key,
			final boolean passed, final String failureMessage) {
		finalReport.put(key, passed);
		if (!passed) {
			failures.add(key + ": " + failureMessage);
		}
	}

	private static final class LegalValidationResult {
		private final boolean passed;
		private final String url;

		private LegalValidationResult(final boolean passed, final String url) {
			this.passed = passed;
			this.url = url;
		}
	}
}
