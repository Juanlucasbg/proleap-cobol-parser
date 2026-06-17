package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
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
 * End-to-end validation for the SaleADS.ai "Mi Negocio" workflow.
 *
 * <p>
 * This test is opt-in and environment-agnostic:
 * <ul>
 *   <li>It does not hardcode any domain.</li>
 *   <li>It relies on visible text selectors first.</li>
 *   <li>It captures screenshots for requested checkpoints.</li>
 *   <li>It writes a final PASS/FAIL report to {@code target/saleads-evidence/.../final-report.txt}.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	private final Map<String, Boolean> results = new LinkedHashMap<>();
	private final Map<String, String> details = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final boolean enabled = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this end-to-end workflow test.", enabled);

		final String startUrl = System.getenv("SALEADS_START_URL");
		Assume.assumeTrue(
				"Set SALEADS_START_URL to the login page of the target environment.",
				startUrl != null && !startUrl.isBlank());

		final Path evidenceDir = createEvidenceDirectory();

		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
					.setHeadless(headless);
			try (Browser browser = playwright.chromium().launch(launchOptions)) {
				try (BrowserContext context = browser.newContext()) {
					final Page page = context.newPage();
					page.navigate(startUrl);
					waitForUiToLoad(page);

					final boolean loginOk = runStep("Login", () -> {
						performGoogleLogin(context, page);
						assertVisibleText(page, "Negocio");
						saveScreenshot(page, evidenceDir, "01-dashboard-loaded", false);
					});

					final boolean miNegocioMenuOk = runStep("Mi Negocio menu", () -> {
						openMiNegocioMenu(page);
						assertVisibleText(page, "Agregar Negocio");
						assertVisibleText(page, "Administrar Negocios");
						saveScreenshot(page, evidenceDir, "02-mi-negocio-menu-expanded", false);
					}, loginOk);

					final boolean agregarModalOk = runStep("Agregar Negocio modal", () -> {
						clickByVisibleText(page, "Agregar Negocio");
						assertVisibleText(page, "Crear Nuevo Negocio");
						assertVisibleText(page, "Nombre del Negocio");
						assertVisibleText(page, "Tienes 2 de 3 negocios");
						assertVisibleText(page, "Cancelar");
						assertVisibleText(page, "Crear Negocio");
						saveScreenshot(page, evidenceDir, "03-agregar-negocio-modal", false);

						final Locator nameInput = firstVisible(page, Arrays.asList(
								"input[placeholder*='Nombre del Negocio']",
								"input[name*='negocio']",
								"input:near(:text('Nombre del Negocio'))"));
						clickAndWait(page, nameInput);
						nameInput.fill("Negocio Prueba Automatización");
						clickByVisibleText(page, "Cancelar");
					}, miNegocioMenuOk);

					final boolean administrarOk = runStep("Administrar Negocios view", () -> {
						openMiNegocioMenu(page);
						clickByVisibleText(page, "Administrar Negocios");
						assertVisibleText(page, "Información General");
						assertVisibleText(page, "Detalles de la Cuenta");
						assertVisibleText(page, "Tus Negocios");
						assertVisibleText(page, "Sección Legal");
						saveScreenshot(page, evidenceDir, "04-administrar-negocios", true);
					}, miNegocioMenuOk || agregarModalOk);

					runStep("Información General", () -> {
						assertAnyVisible(page, Arrays.asList(
								"text=/@/",
								"text=/[A-Z][a-z]+\\s+[A-Z][a-z]+/",
								"text=/Perfil/i"));
						assertVisibleText(page, "BUSINESS PLAN");
						assertVisibleText(page, "Cambiar Plan");
					}, administrarOk);

					runStep("Detalles de la Cuenta", () -> {
						assertVisibleText(page, "Cuenta creada");
						assertVisibleText(page, "Estado activo");
						assertVisibleText(page, "Idioma seleccionado");
					}, administrarOk);

					runStep("Tus Negocios", () -> {
						assertVisibleText(page, "Tus Negocios");
						assertVisibleText(page, "Agregar Negocio");
						assertVisibleText(page, "Tienes 2 de 3 negocios");
					}, administrarOk);

					runStep("Términos y Condiciones", () -> {
						Page legalPage = openLegalLinkAndResolveTargetPage(context, page, "Términos y Condiciones");
						assertVisibleText(legalPage, "Términos y Condiciones");
						assertAnyVisible(legalPage, Arrays.asList("text=/t[eé]rmino/i", "text=/condiciones/i"));
						saveScreenshot(legalPage, evidenceDir, "05-terminos-y-condiciones", true);
						legalUrls.put("Términos y Condiciones", legalPage.url());
						returnToApplicationTab(page, legalPage);
					}, administrarOk);

					runStep("Política de Privacidad", () -> {
						Page legalPage = openLegalLinkAndResolveTargetPage(context, page, "Política de Privacidad");
						assertVisibleText(legalPage, "Política de Privacidad");
						assertAnyVisible(legalPage, Arrays.asList("text=/privacidad/i", "text=/datos personales/i"));
						saveScreenshot(legalPage, evidenceDir, "06-politica-de-privacidad", true);
						legalUrls.put("Política de Privacidad", legalPage.url());
						returnToApplicationTab(page, legalPage);
					}, administrarOk);
				}
			}
		} finally {
			writeFinalReport(evidenceDir);
		}

		final boolean allPassed = results.values().stream().allMatch(Boolean::booleanValue);
		assertTrue("One or more workflow validations failed. Check report in target/saleads-evidence.", allPassed);
	}

	private void performGoogleLogin(final BrowserContext context, final Page page) {
		final Locator loginButton = firstVisible(page, Arrays.asList(
				"text=/sign in with google/i",
				"text=/iniciar sesi[oó]n con google/i",
				"text=/continuar con google/i",
				"button:has-text('Google')",
				"[role='button']:has-text('Google')"));
		clickAndWait(page, loginButton);

		selectGoogleAccountIfVisible(context);
		waitForUiToLoad(page);
		assertVisibleText(page, "Negocio");
	}

	private void selectGoogleAccountIfVisible(final BrowserContext context) {
		final long deadline = System.currentTimeMillis() + 15_000L;
		while (System.currentTimeMillis() < deadline) {
			for (Page candidate : context.pages()) {
				Locator accountLocator = candidate.locator("text=" + GOOGLE_ACCOUNT_EMAIL).first();
				try {
					accountLocator.waitFor(new Locator.WaitForOptions()
							.setState(WaitForSelectorState.VISIBLE)
							.setTimeout(1_000));
					clickAndWait(candidate, accountLocator);
					return;
				} catch (PlaywrightException ignored) {
					// Continue polling until timeout.
				}
			}
			sleep(500L);
		}
	}

	private void openMiNegocioMenu(final Page page) {
		if (isVisible(page, "text=Agregar Negocio", 1_000) && isVisible(page, "text=Administrar Negocios", 1_000)) {
			return;
		}

		if (isVisible(page, "text=Negocio", 1_500)) {
			clickByVisibleText(page, "Negocio");
		}
		clickByVisibleText(page, "Mi Negocio");
	}

	private Page openLegalLinkAndResolveTargetPage(final BrowserContext context, final Page appPage, final String linkText) {
		final int beforePages = context.pages().size();
		clickByVisibleText(appPage, linkText);
		waitForUiToLoad(appPage);

		final long deadline = System.currentTimeMillis() + 8_000L;
		while (System.currentTimeMillis() < deadline) {
			if (context.pages().size() > beforePages) {
				Page target = context.pages().get(context.pages().size() - 1);
				waitForUiToLoad(target);
				return target;
			}
			sleep(250L);
		}

		return appPage;
	}

	private void returnToApplicationTab(final Page appPage, final Page maybeNewTab) {
		if (maybeNewTab != appPage) {
			maybeNewTab.close();
		}
		appPage.bringToFront();
		waitForUiToLoad(appPage);
	}

	private void clickByVisibleText(final Page page, final String visibleText) {
		final Locator locator = firstVisible(page, Arrays.asList(
				"text=" + visibleText,
				"[role='button']:has-text('" + visibleText + "')",
				"a:has-text('" + visibleText + "')"));
		clickAndWait(page, locator);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click();
		waitForUiToLoad(page);
	}

	private void waitForUiToLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (PlaywrightException ignored) {
			// Some pages keep background requests alive; DOM ready + brief wait is enough.
		}
		page.waitForTimeout(800);
	}

	private void assertVisibleText(final Page page, final String text) {
		final Locator locator = page.locator("text=" + text).first();
		locator.waitFor(new Locator.WaitForOptions()
				.setState(WaitForSelectorState.VISIBLE)
				.setTimeout(20_000));
	}

	private void assertAnyVisible(final Page page, final List<String> selectors) {
		for (String selector : selectors) {
			if (isVisible(page, selector, 2_500)) {
				return;
			}
		}
		throw new AssertionError("None of the expected selectors became visible: " + selectors);
	}

	private Locator firstVisible(final Page page, final List<String> selectors) {
		for (String selector : selectors) {
			Locator locator = page.locator(selector).first();
			try {
				locator.waitFor(new Locator.WaitForOptions()
						.setState(WaitForSelectorState.VISIBLE)
						.setTimeout(4_000));
				return locator;
			} catch (PlaywrightException ignored) {
				// Try next selector.
			}
		}
		throw new AssertionError("None of the selectors became visible: " + selectors);
	}

	private boolean isVisible(final Page page, final String selector, final double timeoutMs) {
		Locator locator = page.locator(selector).first();
		try {
			locator.waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout(timeoutMs));
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void saveScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(evidenceDir.resolve(fileName + ".png"))
				.setFullPage(fullPage));
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private void writeFinalReport(final Path evidenceDir) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Workflow - Final Report\n");
		builder.append("=========================================\n\n");

		for (String field : REPORT_FIELDS) {
			final boolean passed = results.getOrDefault(field, false);
			builder.append(field).append(": ").append(passed ? "PASS" : "FAIL").append('\n');
			final String detail = details.get(field);
			if (detail != null && !detail.isBlank()) {
				builder.append("  Detail: ").append(detail).append('\n');
			}
		}

		builder.append("\nCaptured URLs:\n");
		builder.append("-------------\n");
		builder.append("Términos y Condiciones URL: ")
				.append(legalUrls.getOrDefault("Términos y Condiciones", "N/A"))
				.append('\n');
		builder.append("Política de Privacidad URL: ")
				.append(legalUrls.getOrDefault("Política de Privacidad", "N/A"))
				.append('\n');

		Files.writeString(evidenceDir.resolve("final-report.txt"), builder.toString(), StandardCharsets.UTF_8);
	}

	private boolean runStep(final String field, final StepAction stepAction) {
		return runStep(field, stepAction, true);
	}

	private boolean runStep(final String field, final StepAction stepAction, final boolean dependencySatisfied) {
		if (!dependencySatisfied) {
			results.put(field, false);
			details.put(field, "Skipped because a required prior step failed.");
			return false;
		}

		try {
			stepAction.run();
			results.put(field, true);
			details.put(field, "Validation succeeded.");
			return true;
		} catch (Throwable throwable) {
			results.put(field, false);
			details.put(field, throwable.getMessage());
			return false;
		}
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
