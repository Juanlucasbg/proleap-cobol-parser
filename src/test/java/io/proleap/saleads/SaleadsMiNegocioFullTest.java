package io.proleap.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";
	private final Map<String, StepResult> report = new LinkedHashMap<>();

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		assumeTrue("Enable this suite with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true.",
				resolveBoolean("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false));

		final Path screenshotDir = createScreenshotDirectory();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(resolveBoolean("saleads.e2e.headless",
							"SALEADS_E2E_HEADLESS", true)));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page appPage = context.newPage();

			final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
			if (loginUrl != null) {
				appPage.navigate(loginUrl);
				waitForUiLoad(appPage);
			}

			if ("about:blank".equals(appPage.url())) {
				throw new IllegalStateException(
						"No login URL was provided. Set saleads.login.url or SALEADS_LOGIN_URL for your current environment.");
			}

			final boolean loginOk = runStep("Login", () -> {
				performGoogleLogin(appPage);
				assertVisibleAny(appPage, "main application interface", 30_000, appPage.locator("main"),
						appPage.getByText(Pattern.compile("(?i)dashboard|panel|inicio")));
				assertVisibleAny(appPage, "left sidebar navigation", 20_000, appPage.locator("aside"),
						appPage.getByRole(AriaRole.NAVIGATION), appPage.getByText(Pattern.compile("(?i)negocio")));
				screenshot(appPage, screenshotDir, "01_dashboard_loaded", true);
			});

			final boolean menuOk = runStepIf("Mi Negocio menu", loginOk, () -> {
				clickByVisibleText(appPage, "Negocio");
				clickByVisibleText(appPage, "Mi Negocio");
				assertVisible(appPage, "submenu option Agregar Negocio", appPage.getByText("Agregar Negocio"), 15_000);
				assertVisible(appPage, "submenu option Administrar Negocios", appPage.getByText("Administrar Negocios"), 15_000);
				screenshot(appPage, screenshotDir, "02_mi_negocio_menu_expanded", false);
			});

			runStepIf("Agregar Negocio modal", menuOk, () -> {
				clickByVisibleText(appPage, "Agregar Negocio");
				assertVisible(appPage, "modal title Crear Nuevo Negocio", appPage.getByText("Crear Nuevo Negocio"), 15_000);

				assertVisibleAny(appPage, "Nombre del Negocio field", 10_000,
						appPage.getByLabel("Nombre del Negocio"),
						appPage.getByPlaceholder("Nombre del Negocio"),
						appPage.locator("input[placeholder*='Nombre del Negocio' i]"),
						appPage.locator("label:has-text('Nombre del Negocio') + input"),
						appPage.locator("input[name*='negocio' i]"));

				assertVisible(appPage, "business quota text", appPage.getByText("Tienes 2 de 3 negocios"), 10_000);
				assertVisible(appPage, "Cancelar button", appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
						10_000);
				assertVisible(appPage,
						"Crear Negocio button",
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")),
						10_000);

				final Locator nameInput = firstVisibleLocator(appPage,
						appPage.getByLabel("Nombre del Negocio"),
						appPage.getByPlaceholder("Nombre del Negocio"),
						appPage.locator("input[placeholder*='Nombre del Negocio' i]"),
						appPage.locator("label:has-text('Nombre del Negocio') + input"),
						appPage.locator("input[name*='negocio' i]"));
				nameInput.click();
				waitForUiLoad(appPage);
				nameInput.fill(TEST_BUSINESS_NAME);
				screenshot(appPage, screenshotDir, "03_agregar_negocio_modal", false);
				clickByVisibleText(appPage, "Cancelar");
			});

			final boolean administrarOk = runStepIf("Administrar Negocios view", menuOk, () -> {
				if (!isAnyVisible(2_000, appPage.getByText("Administrar Negocios"),
						appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Administrar Negocios")))) {
					clickByVisibleText(appPage, "Mi Negocio");
				}
				clickByVisibleText(appPage, "Administrar Negocios");
				assertVisible(appPage, "Información General section", appPage.getByText("Información General"), 20_000);
				assertVisible(appPage, "Detalles de la Cuenta section", appPage.getByText("Detalles de la Cuenta"), 20_000);
				assertVisible(appPage, "Tus Negocios section", appPage.getByText("Tus Negocios"), 20_000);
				assertVisibleAny(appPage, "legal section", 20_000, appPage.getByText("Sección Legal"),
						appPage.getByText(Pattern.compile("(?i)t[eé]rminos y condiciones")));
				screenshot(appPage, screenshotDir, "04_administrar_negocios_page", true);
			});

			runStepIf("Información General", administrarOk, () -> {
				assertVisibleAny(appPage, "user name in Información General", 10_000,
						appPage.locator("section:has-text('Información General') [class*='name']"),
						appPage.locator("section:has-text('Información General') strong"),
						appPage.locator("section:has-text('Información General') p"));
				assertVisibleAny(appPage, "user email in Información General", 10_000,
						appPage.getByText(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")));
				assertVisible(appPage, "BUSINESS PLAN text", appPage.getByText("BUSINESS PLAN"), 10_000);
				assertVisibleAny(appPage, "Cambiar Plan button", 10_000,
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")),
						appPage.getByText("Cambiar Plan"));
			});

			runStepIf("Detalles de la Cuenta", administrarOk, () -> {
				assertVisibleAny(appPage, "'Cuenta creada' label", 10_000, appPage.getByText("Cuenta creada"),
						appPage.getByText(Pattern.compile("(?i)cuenta creada")));
				assertVisibleAny(appPage, "'Estado activo' label", 10_000, appPage.getByText("Estado activo"),
						appPage.getByText(Pattern.compile("(?i)estado activo")));
				assertVisibleAny(appPage, "'Idioma seleccionado' label", 10_000, appPage.getByText("Idioma seleccionado"),
						appPage.getByText(Pattern.compile("(?i)idioma seleccionado")));
			});

			runStepIf("Tus Negocios", administrarOk, () -> {
				assertVisible(appPage, "Tus Negocios section", appPage.getByText("Tus Negocios"), 10_000);
				assertVisibleAny(appPage, "Agregar Negocio button in businesses section", 10_000,
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
						appPage.getByText("Agregar Negocio"));
				assertVisible(appPage, "business quota text in businesses section", appPage.getByText("Tienes 2 de 3 negocios"), 10_000);
			});

			runStepIf("Términos y Condiciones", administrarOk, () -> {
				final LegalValidationResult legalResult = validateLegalLink(appPage, screenshotDir, "Términos y Condiciones",
						"05_terminos_y_condiciones");
				updateStepDetails("Términos y Condiciones", "Final URL: " + legalResult.finalUrl);
			});

			runStepIf("Política de Privacidad", administrarOk, () -> {
				final LegalValidationResult legalResult = validateLegalLink(appPage, screenshotDir, "Política de Privacidad",
						"06_politica_de_privacidad");
				updateStepDetails("Política de Privacidad", "Final URL: " + legalResult.finalUrl);
			});

			printFinalReport();

			assertTrue("One or more validation checkpoints failed. Review the final report above.",
					report.values().stream().allMatch(step -> step.passed));
		}
	}

	private void performGoogleLogin(final Page appPage) {
		if (isAnyVisible(2_000, appPage.locator("aside"), appPage.getByText(Pattern.compile("(?i)mi negocio|negocio")))) {
			return;
		}

		Page popup = null;
		try {
			popup = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(8_000), () -> clickByVisiblePattern(appPage,
					Pattern.compile("(?i)sign in with google|iniciar sesi[oó]n con google|continuar con google")));
		} catch (PlaywrightException ignored) {
			// Login may happen in the same page.
		}

		waitForUiLoad(appPage);

		if (popup != null) {
			waitForUiLoad(popup);
			clickGoogleAccountIfVisible(popup);
			waitForUiLoad(appPage);
			return;
		}

		clickGoogleAccountIfVisible(appPage);
		waitForUiLoad(appPage);
	}

	private void clickGoogleAccountIfVisible(final Page page) {
		final Locator account = page.getByText(GOOGLE_ACCOUNT_EMAIL, new Page.GetByTextOptions().setExact(true));
		if (isVisible(account, 7_000)) {
			account.click();
			waitForUiLoad(page);
		}
	}

	private LegalValidationResult validateLegalLink(final Page appPage, final Path screenshotDir, final String linkText,
			final String screenshotName) {
		Page legalPage = null;
		boolean openedInNewTab = false;
		try {
			legalPage = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(6_000),
					() -> clickByVisibleText(appPage, linkText));
			openedInNewTab = true;
		} catch (PlaywrightException ignored) {
			// Link might navigate in the same tab.
		}

		if (legalPage == null) {
			legalPage = appPage;
		}

		waitForUiLoad(legalPage);
		assertVisible(legalPage, linkText + " heading", legalPage.getByText(linkText), 20_000);

		final String bodyText = legalPage.locator("body").innerText();
		assertTrue("Expected legal content text to be visible for " + linkText,
				bodyText != null && bodyText.trim().length() > 120);

		screenshot(legalPage, screenshotDir, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (openedInNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else {
			appPage.goBack();
			waitForUiLoad(appPage);
		}

		return new LegalValidationResult(finalUrl);
	}

	private void clickByVisibleText(final Page page, final String visibleText) {
		final Locator locator = firstVisibleLocator(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(visibleText)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(visibleText)),
				page.getByText(visibleText, new Page.GetByTextOptions().setExact(true)),
				page.getByText(Pattern.compile("(?i)" + Pattern.quote(visibleText))));
		locator.click();
		waitForUiLoad(page);
	}

	private void clickByVisiblePattern(final Page page, final Pattern pattern) {
		final Locator locator = firstVisibleLocator(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)),
				page.getByText(pattern));
		locator.click();
		waitForUiLoad(page);
	}

	private void assertVisibleAny(final Page page, final String description, final int timeoutMs, final Locator... candidates) {
		PlaywrightException lastError = null;
		for (final Locator candidate : candidates) {
			try {
				candidate.first().waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
				if (candidate.first().isVisible()) {
					return;
				}
			} catch (PlaywrightException e) {
				lastError = e;
			}
		}

		if (lastError != null) {
			throw new AssertionError("Expected visible element for: " + description, lastError);
		}

		throw new AssertionError("Expected visible element for: " + description);
	}

	private void assertVisible(final Page page, final String description, final Locator locator, final int timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
			assertTrue("Expected visible element for: " + description, locator.first().isVisible());
		} catch (PlaywrightException e) {
			throw new AssertionError("Expected visible element for: " + description, e);
		}
	}

	private Locator firstVisibleLocator(final Page page, final Locator... candidates) {
		PlaywrightException lastError = null;
		for (final Locator candidate : candidates) {
			try {
				candidate.first().waitFor(new Locator.WaitForOptions().setTimeout(5_000));
				if (candidate.first().isVisible()) {
					return candidate.first();
				}
			} catch (PlaywrightException e) {
				lastError = e;
			}
		}

		if (lastError != null) {
			throw new AssertionError("No visible candidate locator was found.", lastError);
		}

		throw new AssertionError("No visible candidate locator was found.");
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
			return locator.first().isVisible();
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private boolean isAnyVisible(final int timeoutMs, final Locator... locators) {
		for (final Locator locator : locators) {
			if (isVisible(locator, timeoutMs)) {
				return true;
			}
		}
		return false;
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10_000));
		} catch (PlaywrightException ignored) {
			// Not all pages reach network idle due to long polling.
		}
		page.waitForTimeout(500);
	}

	private Path createScreenshotDirectory() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
				.format(LocalDateTime.now(ZoneOffset.UTC));
		final Path screenshotDir = Paths.get("target", "saleads-e2e", "saleads_mi_negocio_full_test_" + timestamp);
		Files.createDirectories(screenshotDir);
		return screenshotDir;
	}

	private void screenshot(final Page page, final Path screenshotDir, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotDir.resolve(fileName + ".png")).setFullPage(fullPage));
	}

	private boolean runStep(final String stepName, final StepBlock stepBlock) {
		try {
			stepBlock.run();
			report.put(stepName, StepResult.pass());
			return true;
		} catch (Throwable t) {
			report.put(stepName, StepResult.fail(t.getMessage()));
			return false;
		}
	}

	private boolean runStepIf(final String stepName, final boolean dependency, final StepBlock stepBlock) {
		if (!dependency) {
			report.put(stepName, StepResult.fail("Blocked by previous failed step."));
			return false;
		}
		return runStep(stepName, stepBlock);
	}

	private void updateStepDetails(final String stepName, final String details) {
		final StepResult current = report.get(stepName);
		if (current != null) {
			current.details = details;
		}
	}

	private void printFinalReport() {
		System.out.println("=== saleads_mi_negocio_full_test Final Report ===");
		report.putIfAbsent("Login", StepResult.fail("Step did not run."));
		report.putIfAbsent("Mi Negocio menu", StepResult.fail("Step did not run."));
		report.putIfAbsent("Agregar Negocio modal", StepResult.fail("Step did not run."));
		report.putIfAbsent("Administrar Negocios view", StepResult.fail("Step did not run."));
		report.putIfAbsent("Información General", StepResult.fail("Step did not run."));
		report.putIfAbsent("Detalles de la Cuenta", StepResult.fail("Step did not run."));
		report.putIfAbsent("Tus Negocios", StepResult.fail("Step did not run."));
		report.putIfAbsent("Términos y Condiciones", StepResult.fail("Step did not run."));
		report.putIfAbsent("Política de Privacidad", StepResult.fail("Step did not run."));

		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			final StepResult value = entry.getValue();
			final String status = value.passed ? "PASS" : "FAIL";
			final String details = value.details == null || value.details.isBlank() ? "" : " | " + value.details;
			System.out.println(entry.getKey() + ": " + status + details);
		}
	}

	private boolean resolveBoolean(final String propertyKey, final String envKey, final boolean defaultValue) {
		final String raw = firstNonBlank(System.getProperty(propertyKey), System.getenv(envKey));
		return raw == null ? defaultValue : Boolean.parseBoolean(raw);
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private interface StepBlock {
		void run();
	}

	private static class LegalValidationResult {
		private final String finalUrl;

		private LegalValidationResult(final String finalUrl) {
			this.finalUrl = finalUrl;
		}
	}

	private static class StepResult {
		private final boolean passed;
		private String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, null);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
