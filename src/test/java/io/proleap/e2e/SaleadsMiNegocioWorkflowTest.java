package io.proleap.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

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

/**
 * Full E2E workflow test for SaleADS "Mi Negocio" module.
 *
 * Required env vars:
 * - SALEADS_RUN_E2E=true
 * - SALEADS_LOGIN_URL=https://<current-env>/... (no hardcoded domain in test)
 *
 * Optional env vars:
 * - SALEADS_HEADLESS=true|false (default: true)
 * - SALEADS_SLOW_MO_MS=<int> (default: 0)
 * - SALEADS_SCREENSHOT_DIR=<path> (default: target/saleads-evidence/<run-id>)
 */
public class SaleadsMiNegocioWorkflowTest {
	private static final int DEFAULT_TIMEOUT_MS = 20_000;
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final boolean runE2E = Boolean.parseBoolean(env("SALEADS_RUN_E2E", "false"));
		Assume.assumeTrue("Skipping E2E test. Set SALEADS_RUN_E2E=true to run.", runE2E);

		final String loginUrl = env("SALEADS_LOGIN_URL", "").trim();
		Assume.assumeTrue("Skipping E2E test. Set SALEADS_LOGIN_URL to the current SaleADS login URL.", !loginUrl.isEmpty());

		final String runId = LocalDateTime.now().format(RUN_ID_FORMATTER);
		final Path evidenceDir = resolveEvidenceDirectory(runId);
		Files.createDirectories(evidenceDir);

		final Map<String, Boolean> report = new LinkedHashMap<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));
			final int slowMoMs = Integer.parseInt(env("SALEADS_SLOW_MO_MS", "0"));

			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
				.setHeadless(headless)
				.setSlowMo((double) slowMoMs));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page page = context.newPage();
			page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

			// Step 1: Login with Google
			executeStep("Login", report, failures, page, evidenceDir, () -> {
				page.navigate(loginUrl);
				waitForUi(page);

				// Click login/sign-in with Google button/link and wait for navigation or popup.
				Page authPage = clickAndResolvePopupOrSamePage(page, () -> clickGoogleLoginButton(page));

				// If account selector appears, choose requested account.
				clickIfVisible(authPage, locatorByTextRegex(authPage, "(?i)" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL)), true);
				waitForUi(authPage);

				// If auth happened in popup, return focus to app page.
				page.bringToFront();
				waitForUi(page);

				assertAnyVisible(page,
					"Main app interface is not visible after login.",
					locatorByTextRegex(page, "(?i)mi\\s+negocio"),
					page.locator("aside"),
					page.locator("nav"));

				assertAnyVisible(page,
					"Left sidebar is not visible after login.",
					page.locator("aside"),
					page.locator("nav:has-text(\"Negocio\")"),
					locatorByTextRegex(page, "(?i)negocio"));

				screenshot(page, evidenceDir, "01-dashboard-loaded.png", false);
			});

			// Step 2: Open Mi Negocio menu
			executeStep("Mi Negocio menu", report, failures, page, evidenceDir, () -> {
				clickByVisibleText(page, "Negocio");
				clickByVisibleText(page, "Mi Negocio");
				waitForUi(page);

				assertVisible(page, "Agregar Negocio option is not visible.", "Agregar Negocio");
				assertVisible(page, "Administrar Negocios option is not visible.", "Administrar Negocios");
				screenshot(page, evidenceDir, "02-mi-negocio-menu-expanded.png", false);
			});

			// Step 3: Validate Agregar Negocio modal
			executeStep("Agregar Negocio modal", report, failures, page, evidenceDir, () -> {
				clickByVisibleText(page, "Agregar Negocio");
				waitForUi(page);

				assertVisibleRegex(page, "Modal title 'Crear Nuevo Negocio' is not visible.", "(?i)crear\\s+nuevo\\s+negocio");
				assertVisibleRegex(page, "Input field 'Nombre del Negocio' is not visible.", "(?i)nombre\\s+del\\s+negocio");
				assertVisibleRegex(page, "Text 'Tienes 2 de 3 negocios' is not visible.", "(?i)tienes\\s+2\\s+de\\s+3\\s+negocios");
				assertVisible(page, "Button 'Cancelar' is not visible.", "Cancelar");
				assertVisibleRegex(page, "Button 'Crear Negocio' is not visible.", "(?i)crear\\s+negocio");

				screenshot(page, evidenceDir, "03-agregar-negocio-modal.png", false);

				Locator nombreNegocioInput = page.getByLabel("Nombre del Negocio");
				if (!nombreNegocioInput.first().isVisible()) {
					nombreNegocioInput = page.getByPlaceholder("Nombre del Negocio");
				}
				if (nombreNegocioInput.first().isVisible()) {
					nombreNegocioInput.first().click();
					nombreNegocioInput.first().fill("Negocio Prueba Automatizacion");
					waitForUi(page);
				}
				clickByVisibleText(page, "Cancelar");
				waitForUi(page);
			});

			// Step 4: Open Administrar Negocios
			executeStep("Administrar Negocios view", report, failures, page, evidenceDir, () -> {
				ensureMiNegocioExpanded(page);
				clickByVisibleText(page, "Administrar Negocios");
				waitForUi(page);

				assertVisibleRegex(page, "Section 'Informacion General' is not visible.", "(?i)informaci[oó]n\\s+general");
				assertVisibleRegex(page, "Section 'Detalles de la Cuenta' is not visible.", "(?i)detalles\\s+de\\s+la\\s+cuenta");
				assertVisibleRegex(page, "Section 'Tus Negocios' is not visible.", "(?i)tus\\s+negocios");
				assertVisibleRegex(page, "Section 'Seccion Legal' is not visible.", "(?i)secci[oó]n\\s+legal");
				screenshot(page, evidenceDir, "04-administrar-negocios-full-page.png", true);
			});

			// Step 5: Validate Informacion General
			executeStep("Información General", report, failures, page, evidenceDir, () -> {
				assertVisibleRegex(page, "Text 'BUSINESS PLAN' is not visible.", "(?i)business\\s+plan");
				assertVisibleRegex(page, "Button 'Cambiar Plan' is not visible.", "(?i)cambiar\\s+plan");

				assertAnyVisible(page,
					"User email is not visible in account page.",
					page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/"));

				assertAnyVisible(page,
					"User name is not visibly rendered in account page.",
					page.locator("h1, h2, h3, h4"),
					page.locator("text=/\\b[A-Z][a-z]+\\s+[A-Z][a-z]+\\b/"));
			});

			// Step 6: Validate Detalles de la Cuenta
			executeStep("Detalles de la Cuenta", report, failures, page, evidenceDir, () -> {
				assertVisibleRegex(page, "'Cuenta creada' is not visible.", "(?i)cuenta\\s+creada");
				assertVisibleRegex(page, "'Estado activo' is not visible.", "(?i)estado\\s+activo");
				assertVisibleRegex(page, "'Idioma seleccionado' is not visible.", "(?i)idioma\\s+seleccionado");
			});

			// Step 7: Validate Tus Negocios
			executeStep("Tus Negocios", report, failures, page, evidenceDir, () -> {
				assertVisibleRegex(page, "'Tus Negocios' section is not visible.", "(?i)tus\\s+negocios");
				assertVisibleRegex(page, "Button 'Agregar Negocio' is not visible in account page.", "(?i)agregar\\s+negocio");
				assertVisibleRegex(page, "Text 'Tienes 2 de 3 negocios' is not visible.", "(?i)tienes\\s+2\\s+de\\s+3\\s+negocios");
			});

			// Step 8: Validate Terminos y Condiciones
			executeStep("Términos y Condiciones", report, failures, page, evidenceDir, () -> {
				String termsUrl = openLegalLinkAndValidate(
					page,
					evidenceDir,
					"Términos y Condiciones",
					"(?i)t[eé]rminos\\s+y\\s+condiciones",
					"05-terminos-y-condiciones.png");
				legalUrls.put("Términos y Condiciones", termsUrl);
			});

			// Step 9: Validate Politica de Privacidad
			executeStep("Política de Privacidad", report, failures, page, evidenceDir, () -> {
				String privacyUrl = openLegalLinkAndValidate(
					page,
					evidenceDir,
					"Política de Privacidad",
					"(?i)pol[ií]tica\\s+de\\s+privacidad",
					"06-politica-de-privacidad.png");
				legalUrls.put("Política de Privacidad", privacyUrl);
			});

			writeFinalReport(evidenceDir, report, legalUrls, failures);
			printFinalReport(report, legalUrls, evidenceDir);
		}

		if (!failures.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow failed:\n- " + String.join("\n- ", failures));
		}
	}

	private void executeStep(String stepName, Map<String, Boolean> report, List<String> failures, Page page, Path evidenceDir, StepBody stepBody) {
		try {
			stepBody.run();
			report.put(stepName, true);
		} catch (Throwable t) {
			report.put(stepName, false);
			failures.add(stepName + ": " + safeMessage(t));
			try {
				screenshot(page, evidenceDir, "failure-" + sanitizeFileName(stepName) + ".png", true);
			} catch (Throwable ignored) {
				// Keep original error as primary signal.
			}
		}
	}

	private String openLegalLinkAndValidate(Page appPage, Path evidenceDir, String linkText, String headingRegex, String screenshotName) {
		Page legalPage = null;
		boolean openedPopup = false;
		try {
			legalPage = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(5_000), () -> clickByVisibleTextNoWait(appPage, linkText));
			openedPopup = true;
		} catch (PlaywrightException popupNotOpened) {
			clickByVisibleTextNoWait(appPage, linkText);
			waitForUi(appPage);
			legalPage = appPage;
		}

		waitForUi(legalPage);
		assertVisibleRegex(legalPage, "Legal heading not visible for " + linkText + ".", headingRegex);
		assertAnyVisible(legalPage,
			"Legal content text is not visible for " + linkText + ".",
			legalPage.locator("main p"),
			legalPage.locator("article p"),
			legalPage.locator("p"));
		final String finalUrl = legalPage.url();
		screenshot(legalPage, evidenceDir, screenshotName, true);

		if (openedPopup) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private Page clickAndResolvePopupOrSamePage(Page appPage, Runnable clickAction) {
		try {
			Page popup = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(5_000), clickAction::run);
			waitForUi(popup);
			return popup;
		} catch (PlaywrightException popupNotOpened) {
			clickAction.run();
			waitForUi(appPage);
			return appPage;
		}
	}

	private void clickGoogleLoginButton(Page page) {
		if (clickIfVisible(page, locatorByTextRegex(page, "(?i)sign\\s*in\\s*with\\s*google"), true)) return;
		if (clickIfVisible(page, locatorByTextRegex(page, "(?i)continuar\\s*con\\s*google"), true)) return;
		if (clickIfVisible(page, locatorByTextRegex(page, "(?i)ingresar\\s*con\\s*google"), true)) return;
		if (clickIfVisible(page, locatorByTextRegex(page, "(?i)google"), true)) return;

		throw new AssertionError("Unable to locate 'Sign in with Google' control.");
	}

	private void ensureMiNegocioExpanded(Page page) {
		if (isVisible(locatorByTextRegex(page, "(?i)administrar\\s+negocios"), 2_000)) {
			return;
		}
		clickIfVisible(page, locatorByTextRegex(page, "(?i)mi\\s+negocio"), true);
		waitForUi(page);
	}

	private void clickByVisibleText(Page page, String text) {
		clickByVisibleTextNoWait(page, text);
		waitForUi(page);
	}

	private void clickByVisibleTextNoWait(Page page, String text) {
		if (clickIfVisible(page, page.getByText(text, new Page.GetByTextOptions().setExact(true)), false)) return;
		if (clickIfVisible(page, page.getByText(text), false)) return;
		if (clickIfVisible(page, locatorByTextRegex(page, "(?i)" + Pattern.quote(text)), false)) return;
		throw new AssertionError("Unable to click visible element by text: " + text);
	}

	private boolean clickIfVisible(Page page, Locator locator, boolean waitAfterClick) {
		if (isVisible(locator, 2_500)) {
			locator.first().click();
			if (waitAfterClick) {
				waitForUi(page);
			}
			return true;
		}
		return false;
	}

	private void assertVisible(Page page, String errorMessage, String exactText) {
		assertAnyVisible(page, errorMessage,
			page.getByText(exactText, new Page.GetByTextOptions().setExact(true)),
			page.getByText(exactText));
	}

	private void assertVisibleRegex(Page page, String errorMessage, String regex) {
		assertAnyVisible(page, errorMessage, locatorByTextRegex(page, regex));
	}

	private Locator locatorByTextRegex(Page page, String regex) {
		return page.locator("text=/" + regex + "/");
	}

	private void assertAnyVisible(Page page, String errorMessage, Locator... locators) {
		for (Locator locator : locators) {
			if (isVisible(locator, 5_000)) {
				return;
			}
		}
		throw new AssertionError(errorMessage);
	}

	private boolean isVisible(Locator locator, int timeoutMs) {
		try {
			return locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout((double) timeoutMs));
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private void waitForUi(Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (PlaywrightException ignored) {
			// Some transitions do not trigger document lifecycle changes.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
		} catch (PlaywrightException ignored) {
			// Dynamic apps can keep polling; continue after a stable timeout.
		}
		page.waitForTimeout(500);
	}

	private void screenshot(Page page, Path evidenceDir, String fileName, boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
			.setPath(evidenceDir.resolve(fileName))
			.setFullPage(fullPage));
	}

	private Path resolveEvidenceDirectory(String runId) {
		final String configuredDir = env("SALEADS_SCREENSHOT_DIR", "").trim();
		if (!configuredDir.isEmpty()) {
			return Paths.get(configuredDir);
		}
		return Paths.get("target", "saleads-evidence", runId);
	}

	private void writeFinalReport(Path evidenceDir, Map<String, Boolean> report, Map<String, String> legalUrls, List<String> failures) throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Workflow - Final Report");
		lines.add("");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			lines.add(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		lines.add("");
		lines.add("Final URLs:");
		lines.add("Términos y Condiciones: " + legalUrls.getOrDefault("Términos y Condiciones", "N/A"));
		lines.add("Política de Privacidad: " + legalUrls.getOrDefault("Política de Privacidad", "N/A"));
		if (!failures.isEmpty()) {
			lines.add("");
			lines.add("Failures:");
			lines.addAll(failures);
		}
		Files.write(evidenceDir.resolve("final-report.txt"), lines);
	}

	private void printFinalReport(Map<String, Boolean> report, Map<String, String> legalUrls, Path evidenceDir) {
		System.out.println("=== SaleADS Mi Negocio Workflow Final Report ===");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		System.out.println("Términos y Condiciones URL: " + legalUrls.getOrDefault("Términos y Condiciones", "N/A"));
		System.out.println("Política de Privacidad URL: " + legalUrls.getOrDefault("Política de Privacidad", "N/A"));
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private String sanitizeFileName(String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String safeMessage(Throwable t) {
		if (t.getMessage() == null || t.getMessage().isBlank()) {
			return t.getClass().getSimpleName();
		}
		return t.getMessage();
	}

	private String env(String key, String defaultValue) {
		String value = System.getenv(key);
		return value == null ? defaultValue : value;
	}

	@FunctionalInterface
	private interface StepBody {
		void run() throws Exception;
	}
}
