package io.proleap.e2e.saleads;

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
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_DETALLES = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Política de Privacidad";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final WorkflowReport report = new WorkflowReport();
		final Path evidenceDir = createEvidenceDirectory();
		final String loginUrl = getRequiredEnv("SALEADS_START_URL");
		final boolean headless = !"false".equalsIgnoreCase(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(200));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page page = context.newPage();

			page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(page);

			boolean criticalChainPassed = true;
			criticalChainPassed = executeStep(report, STEP_LOGIN, true, () -> {
				loginWithGoogle(context, page);
				waitForApplicationShell(page);
				screenshot(page, evidenceDir, "01_dashboard_loaded", false);
			});

			if (criticalChainPassed) {
				criticalChainPassed = executeStep(report, STEP_MI_NEGOCIO_MENU, true, () -> {
					clickByVisibleText(page, "Negocio");
					clickByVisibleText(page, "Mi Negocio");
					assertTextVisible(page, "Agregar Negocio");
					assertTextVisible(page, "Administrar Negocios");
					screenshot(page, evidenceDir, "02_mi_negocio_menu_expanded", false);
				});
			}

			if (criticalChainPassed) {
				criticalChainPassed = executeStep(report, STEP_AGREGAR_MODAL, true, () -> {
					clickByVisibleText(page, "Agregar Negocio");
					assertTextVisible(page, "Crear Nuevo Negocio");
					assertTextVisible(page, "Nombre del Negocio");
					assertTextVisible(page, "Tienes 2 de 3 negocios");
					assertTextVisible(page, "Cancelar");
					assertTextVisible(page, "Crear Negocio");

					final Locator nameInput = findBusinessNameInput(page);
					if (isVisible(nameInput, 2000)) {
						nameInput.fill("Negocio Prueba Automatización");
					}

					screenshot(page, evidenceDir, "03_agregar_negocio_modal", false);
					clickByVisibleText(page, "Cancelar");
				});
			}

			if (criticalChainPassed) {
				criticalChainPassed = executeStep(report, STEP_ADMIN_VIEW, true, () -> {
					if (!isTextVisible(page, "Administrar Negocios", 1500)) {
						clickByVisibleText(page, "Mi Negocio");
					}
					clickByVisibleText(page, "Administrar Negocios");
					assertTextVisible(page, "Información General");
					assertTextVisible(page, "Detalles de la Cuenta");
					assertTextVisible(page, "Tus Negocios");
					assertTextVisible(page, "Sección Legal");
					screenshot(page, evidenceDir, "04_administrar_negocios", true);
				});
			}

			if (!criticalChainPassed) {
				report.markNotExecuted(List.of(STEP_AGREGAR_MODAL, STEP_ADMIN_VIEW, STEP_INFO_GENERAL, STEP_DETALLES,
						STEP_TUS_NEGOCIOS, STEP_TERMINOS, STEP_PRIVACIDAD),
						"Skipped because a previous critical step failed.");
			}

			if (criticalChainPassed) {
				executeStep(report, STEP_INFO_GENERAL, false, () -> {
					assertAnyTextVisible(page, List.of("BUSINESS PLAN", "Business Plan"));
					assertTextVisible(page, "Cambiar Plan");
					assertEmailVisible(page);
					assertUserNameVisible(page);
				});

				executeStep(report, STEP_DETALLES, false, () -> {
					assertTextVisible(page, "Cuenta creada");
					assertAnyTextVisible(page, List.of("Estado activo", "Estado Activo"));
					assertAnyTextVisible(page, List.of("Idioma seleccionado", "Idioma Seleccionado"));
				});

				executeStep(report, STEP_TUS_NEGOCIOS, false, () -> {
					assertTextVisible(page, "Tus Negocios");
					assertTextVisible(page, "Agregar Negocio");
					assertTextVisible(page, "Tienes 2 de 3 negocios");
				});

				executeStep(report, STEP_TERMINOS, false, () -> {
					final String termsUrl = validateLegalLink(context, page, evidenceDir, "Términos y Condiciones",
							"Términos y Condiciones", "05_terminos_y_condiciones");
					report.legalUrls.put("Términos y Condiciones", termsUrl);
				});

				executeStep(report, STEP_PRIVACIDAD, false, () -> {
					final String privacyUrl = validateLegalLink(context, page, evidenceDir, "Política de Privacidad",
							"Política de Privacidad", "06_politica_de_privacidad");
					report.legalUrls.put("Política de Privacidad", privacyUrl);
				});
			}

			writeFinalReport(evidenceDir, report);
			System.out.println(report.toConsoleString());
			assertTrue(report.toConsoleString(), report.allPassed());
		}
	}

	private void loginWithGoogle(final BrowserContext context, final Page appPage) {
		Page popup = null;
		try {
			popup = context.waitForPage(() -> clickByVisibleTextWithoutUiWait(appPage, "Google"),
					new BrowserContext.WaitForPageOptions().setTimeout(7000));
		} catch (final RuntimeException ignored) {
			clickByVisibleText(appPage, "Google");
		}

		if (popup != null) {
			waitForUi(popup);
			selectGoogleAccountIfVisible(popup, "juanlucasbarbiergarzon@gmail.com");
		}
		waitForUi(appPage);
	}

	private void waitForApplicationShell(final Page page) {
		final Locator sidebar = page.locator("aside, nav").first();
		waitUntilVisible(sidebar, 15000, "Main sidebar navigation is not visible.");
		assertAnyTextVisible(page, List.of("Negocio", "Mi Negocio"));
	}

	private void selectGoogleAccountIfVisible(final Page popup, final String accountEmail) {
		if (isTextVisible(popup, accountEmail, 5000)) {
			clickByVisibleText(popup, accountEmail);
		}
		popup.waitForTimeout(1000);
	}

	private String validateLegalLink(final BrowserContext context, final Page appPage, final Path evidenceDir,
			final String linkText, final String expectedHeading, final String screenshotName) {
		Page legalPage = null;
		boolean openedNewTab = false;

		try {
			legalPage = context.waitForPage(() -> clickByVisibleTextWithoutUiWait(appPage, linkText),
					new BrowserContext.WaitForPageOptions().setTimeout(5000));
			openedNewTab = true;
		} catch (final RuntimeException ignored) {
			clickByVisibleText(appPage, linkText);
			legalPage = appPage;
		}

		waitForUi(legalPage);
		assertTextVisible(legalPage, expectedHeading);

		final String legalContent = legalPage.locator("body").innerText().trim();
		if (legalContent.length() < 120) {
			throw new IllegalStateException("Legal page content looks too short for " + linkText + ".");
		}

		screenshot(legalPage, evidenceDir, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			legalPage.goBack();
			waitForUi(legalPage);
		}

		return finalUrl;
	}

	private static Path createEvidenceDirectory() throws Exception {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
		final Path evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private static String getRequiredEnv(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalStateException(
					"Missing environment variable '" + key + "'. Set it to the current SaleADS login URL.");
		}
		return value.trim();
	}

	private boolean executeStep(final WorkflowReport report, final String stepName, final boolean critical,
			final StepAction action) {
		try {
			action.run();
			report.pass(stepName);
			return true;
		} catch (final Exception e) {
			report.fail(stepName, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
			return !critical;
		}
	}

	private void screenshot(final Page page, final Path evidenceDir, final String name, final boolean fullPage) {
		final String safeName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(safeName + ".png")).setFullPage(fullPage));
	}

	private void clickByVisibleText(final Page page, final String text) {
		final Locator locator = findVisibleByText(page, text, 4000);
		locator.click();
		waitForUi(page);
	}

	private void clickByVisibleTextWithoutUiWait(final Page page, final String text) {
		final Locator locator = findVisibleByText(page, text, 4000);
		locator.click();
	}

	private void assertTextVisible(final Page page, final String text) {
		findVisibleByText(page, text, 10000);
	}

	private void assertAnyTextVisible(final Page page, final List<String> textOptions) {
		for (final String text : textOptions) {
			if (isTextVisible(page, text, 2500)) {
				return;
			}
		}
		throw new IllegalStateException("None of the expected texts are visible: " + textOptions);
	}

	private void assertEmailVisible(final Page page) {
		final Locator emailLocator = page
				.getByText(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")).first();
		waitUntilVisible(emailLocator, 8000, "No visible user email found in Información General.");
	}

	private void assertUserNameVisible(final Page page) {
		final Locator headingCandidate = page.locator("section:has-text('Información General') h1, section:has-text('Información General') h2, section:has-text('Información General') h3")
				.first();
		if (isVisible(headingCandidate, 3000)) {
			final String text = headingCandidate.innerText().trim();
			if (!text.isEmpty()) {
				return;
			}
		}

		final Locator fallback = page.locator("main h1, main h2").first();
		waitUntilVisible(fallback, 5000, "No visible user name/heading found.");
	}

	private Locator findBusinessNameInput(final Page page) {
		final List<Locator> candidates = List.of(
				page.locator("input[placeholder*='Nombre del Negocio']"),
				page.locator("input[name*='nombre']"),
				page.locator("input[id*='nombre']"));

		for (final Locator candidate : candidates) {
			final Locator first = candidate.first();
			if (isVisible(first, 1500)) {
				return first;
			}
		}
		return page.locator("input").first();
	}

	private Locator findVisibleByText(final Page page, final String text, final int timeoutMs) {
		final Pattern fuzzyText = Pattern.compile("(?i).*" + Pattern.quote(text) + ".*");
		final List<Locator> candidates = new ArrayList<>();
		candidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(fuzzyText)).first());
		candidates.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(fuzzyText)).first());
		candidates.add(page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(fuzzyText)).first());
		candidates.add(page.getByText(fuzzyText).first());
		candidates.add(page.locator("text=" + text).first());

		for (final Locator candidate : candidates) {
			if (isVisible(candidate, timeoutMs)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Unable to find visible element with text: " + text);
	}

	private boolean isTextVisible(final Page page, final String text, final int timeoutMs) {
		try {
			findVisibleByText(page, text, timeoutMs);
			return true;
		} catch (final RuntimeException ignored) {
			return false;
		}
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout((double) timeoutMs));
			return true;
		} catch (final TimeoutError e) {
			return false;
		} catch (final RuntimeException e) {
			return false;
		}
	}

	private void waitUntilVisible(final Locator locator, final int timeoutMs, final String errorMessage) {
		if (!isVisible(locator, timeoutMs)) {
			throw new IllegalStateException(errorMessage);
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(8000));
		} catch (final RuntimeException ignored) {
			// Some single-page transitions do not trigger a document lifecycle event.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(2500));
		} catch (final RuntimeException ignored) {
			// Keep the test resilient to pages with ongoing network requests.
		}
		page.waitForTimeout(600);
	}

	private void writeFinalReport(final Path evidenceDir, final WorkflowReport report) throws Exception {
		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, report.toConsoleString());
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private static final class WorkflowReport {
		private final Map<String, String> stepStatus = new LinkedHashMap<>();
		private final Map<String, String> stepFailureReason = new LinkedHashMap<>();
		private final Map<String, String> legalUrls = new LinkedHashMap<>();

		private void pass(final String stepName) {
			stepStatus.put(stepName, "PASS");
			stepFailureReason.remove(stepName);
		}

		private void fail(final String stepName, final String reason) {
			stepStatus.put(stepName, "FAIL");
			stepFailureReason.put(stepName, reason == null ? "Unknown failure" : reason);
		}

		private void markNotExecuted(final List<String> steps, final String reason) {
			for (final String step : steps) {
				if (!stepStatus.containsKey(step)) {
					fail(step, reason);
				}
			}
		}

		private boolean allPassed() {
			return stepStatus.values().stream().allMatch("PASS"::equals);
		}

		private String toConsoleString() {
			final List<String> orderedSteps = List.of(STEP_LOGIN, STEP_MI_NEGOCIO_MENU, STEP_AGREGAR_MODAL, STEP_ADMIN_VIEW,
					STEP_INFO_GENERAL, STEP_DETALLES, STEP_TUS_NEGOCIOS, STEP_TERMINOS, STEP_PRIVACIDAD);
			final StringBuilder sb = new StringBuilder();
			sb.append("SaleADS Mi Negocio workflow final report").append(System.lineSeparator());
			sb.append("=====================================").append(System.lineSeparator());
			for (final String step : orderedSteps) {
				final String status = stepStatus.getOrDefault(step, "FAIL");
				sb.append("- ").append(step).append(": ").append(status);
				if ("FAIL".equals(status) && stepFailureReason.containsKey(step)) {
					sb.append(" (").append(stepFailureReason.get(step)).append(")");
				}
				sb.append(System.lineSeparator());
			}
			if (!legalUrls.isEmpty()) {
				sb.append(System.lineSeparator()).append("Captured legal URLs").append(System.lineSeparator());
				legalUrls.forEach((name, url) -> sb.append("- ").append(name).append(": ").append(url)
						.append(System.lineSeparator()));
			}
			return sb.toString();
		}
	}
}
