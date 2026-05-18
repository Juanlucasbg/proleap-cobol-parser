package io.proleap.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

	private static final int TIMEOUT_MS = 15000;
	private static final int SHORT_TIMEOUT_MS = 5000;

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Informacion General";
	private static final String REPORT_DETALLES = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Terminos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Politica de Privacidad";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String loginUrl = env("SALEADS_LOGIN_URL", "");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the environment login page before running this test.",
				!loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));
		final String googleEmail = env("SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com");
		final String expectedUserName = env("SALEADS_EXPECTED_USER_NAME", googleEmail.split("@")[0]);
		final String expectedUserEmail = env("SALEADS_EXPECTED_USER_EMAIL", googleEmail);
		final Path screenshotDir = createScreenshotDir();

		final Map<String, StepOutcome> report = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();
		final String[] accountPageUrl = new String[] { "" };
		final String[] termsUrl = new String[] { "" };
		final String[] privacyUrl = new String[] { "" };

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(
					new Browser.NewContextOptions().setViewportSize(1920, 1080));
			final Page appPage = context.newPage();
			appPage.navigate(loginUrl);
			waitForUiToSettle(appPage);

			runStep(REPORT_LOGIN, report, failures, () -> {
				loginWithGoogle(context, appPage, googleEmail);
				assertAnyVisible("Main application interface",
						candidatesByText(appPage, "Mi Negocio", "Negocio", "Dashboard", "Inicio"));
				assertAnyVisible("Left sidebar navigation",
						Arrays.asList(appPage.locator("aside"), appPage.locator("nav")));
				capture(appPage, screenshotDir.resolve("01-dashboard-loaded.png"), true);
			});

			runStep(REPORT_MI_NEGOCIO, report, failures, () -> {
				expandMiNegocioMenu(appPage);
				assertAnyVisible("'Agregar Negocio' option", candidatesByText(appPage, "Agregar Negocio"));
				assertAnyVisible("'Administrar Negocios' option", candidatesByText(appPage, "Administrar Negocios"));
				capture(appPage, screenshotDir.resolve("02-mi-negocio-expanded.png"), false);
			});

			runStep(REPORT_AGREGAR_MODAL, report, failures, () -> {
				clickVisible(appPage, "Agregar Negocio");
				assertAnyVisible("Modal title 'Crear Nuevo Negocio'",
						candidatesByText(appPage, "Crear Nuevo Negocio"));
				assertAnyVisible("Input 'Nombre del Negocio'",
						Arrays.asList(appPage.getByLabel("Nombre del Negocio"),
								appPage.locator("input[placeholder*='Nombre del Negocio']")));
				assertAnyVisible("Business quota text",
						candidatesByText(appPage, "Tienes 2 de 3 negocios"));
				assertAnyVisible("'Cancelar' button", candidatesByRoleAndText(appPage, AriaRole.BUTTON, "Cancelar"));
				assertAnyVisible("'Crear Negocio' button",
						candidatesByRoleAndText(appPage, AriaRole.BUTTON, "Crear Negocio"));
				capture(appPage, screenshotDir.resolve("03-agregar-negocio-modal.png"), false);

				final Locator nombreNegocio = firstVisibleLocator(
						Arrays.asList(appPage.getByLabel("Nombre del Negocio"),
								appPage.locator("input[placeholder*='Nombre del Negocio']")),
						SHORT_TIMEOUT_MS);
				nombreNegocio.fill("Negocio Prueba Automatizacion");
				clickVisible(appPage, "Cancelar");
			});

			runStep(REPORT_ADMIN_VIEW, report, failures, () -> {
				expandMiNegocioMenu(appPage);
				clickVisible(appPage, "Administrar Negocios");
				waitForUiToSettle(appPage);
				assertAnyVisible("'Informacion General' section", candidatesByText(appPage, "Informacion General"));
				assertAnyVisible("'Detalles de la Cuenta' section", candidatesByText(appPage, "Detalles de la Cuenta"));
				assertAnyVisible("'Tus Negocios' section", candidatesByText(appPage, "Tus Negocios"));
				assertAnyVisible("'Seccion Legal' section", candidatesByText(appPage, "Seccion Legal"));
				accountPageUrl[0] = appPage.url();
				capture(appPage, screenshotDir.resolve("04-administrar-negocios-page.png"), true);
			});

			runStep(REPORT_INFO_GENERAL, report, failures, () -> {
				assertAnyVisible("User name", candidatesByText(appPage, expectedUserName, googleEmail.split("@")[0]));
				assertAnyVisible("User email", candidatesByText(appPage, expectedUserEmail, googleEmail));
				assertAnyVisible("'BUSINESS PLAN' text", candidatesByText(appPage, "BUSINESS PLAN"));
				assertAnyVisible("'Cambiar Plan' button", candidatesByText(appPage, "Cambiar Plan"));
			});

			runStep(REPORT_DETALLES, report, failures, () -> {
				assertAnyVisible("'Cuenta creada' text", candidatesByText(appPage, "Cuenta creada"));
				assertAnyVisible("'Estado activo' text", candidatesByText(appPage, "Estado activo"));
				assertAnyVisible("'Idioma seleccionado' text", candidatesByText(appPage, "Idioma seleccionado"));
			});

			runStep(REPORT_TUS_NEGOCIOS, report, failures, () -> {
				assertAnyVisible("Business list",
						Arrays.asList(appPage.locator("table tbody tr"), appPage.locator("ul li"),
								appPage.locator("[class*='business']"), appPage.locator("[data-testid*='business']")));
				assertAnyVisible("'Agregar Negocio' button", candidatesByText(appPage, "Agregar Negocio"));
				assertAnyVisible("Business quota text",
						candidatesByText(appPage, "Tienes 2 de 3 negocios"));
			});

			runStep(REPORT_TERMINOS, report, failures, () -> {
				termsUrl[0] = validateLegalPageAndReturnUrl(context, appPage, accountPageUrl[0],
						Arrays.asList("Terminos y Condiciones", "Términos y Condiciones"),
						"Términos y Condiciones", screenshotDir.resolve("05-terminos-y-condiciones.png"));
			});

			runStep(REPORT_PRIVACIDAD, report, failures, () -> {
				privacyUrl[0] = validateLegalPageAndReturnUrl(context, appPage, accountPageUrl[0],
						Arrays.asList("Politica de Privacidad", "Política de Privacidad"),
						"Política de Privacidad", screenshotDir.resolve("06-politica-de-privacidad.png"));
			});
		}

		if (!termsUrl[0].isBlank()) {
			report.computeIfPresent(REPORT_TERMINOS,
					(key, outcome) -> outcome.withDetail("Final URL: " + termsUrl[0]));
		}
		if (!privacyUrl[0].isBlank()) {
			report.computeIfPresent(REPORT_PRIVACIDAD,
					(key, outcome) -> outcome.withDetail("Final URL: " + privacyUrl[0]));
		}

		printReport(report, screenshotDir);

		if (!failures.isEmpty()) {
			throw new AssertionError("Workflow validations failed:\n - " + String.join("\n - ", failures));
		}
	}

	private void loginWithGoogle(final BrowserContext context, final Page appPage, final String googleEmail) {
		final Locator loginButton = firstVisibleLocator(
				Arrays.asList(
						appPage.getByRole(AriaRole.BUTTON,
								new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*"))),
						appPage.getByText(Pattern.compile("(?i).*google.*")),
						appPage.locator("button:has-text('Google')"),
						appPage.locator("a:has-text('Google')")),
				TIMEOUT_MS);

		final int initialPageCount = context.pages().size();
		loginButton.click();
		waitForUiToSettle(appPage);

		final Page authPage = waitForAuthPage(context, appPage, initialPageCount);
		if (isAnyVisible(candidatesByText(authPage, googleEmail), SHORT_TIMEOUT_MS)) {
			clickVisible(authPage, googleEmail);
		}

		waitForText(appPage, Arrays.asList("Mi Negocio", "Negocio", "Dashboard", "Inicio"), TIMEOUT_MS);
	}

	private Page waitForAuthPage(final BrowserContext context, final Page defaultPage, final int previousPageCount) {
		final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
		while (System.currentTimeMillis() < deadline) {
			final List<Page> pages = context.pages();
			if (pages.size() > previousPageCount) {
				return pages.get(pages.size() - 1);
			}
			if (defaultPage.url().contains("accounts.google.com")) {
				return defaultPage;
			}
			defaultPage.waitForTimeout(250);
		}
		return defaultPage;
	}

	private void expandMiNegocioMenu(final Page appPage) {
		if (isAnyVisible(candidatesByText(appPage, "Agregar Negocio", "Administrar Negocios"), SHORT_TIMEOUT_MS)) {
			return;
		}

		clickIfVisible(appPage, "Negocio");
		clickIfVisible(appPage, "Mi Negocio");
		waitForText(appPage, Arrays.asList("Agregar Negocio", "Administrar Negocios"), TIMEOUT_MS);
	}

	private String validateLegalPageAndReturnUrl(final BrowserContext context, final Page appPage,
			final String accountPageUrl, final List<String> legalLinkTexts, final String expectedHeading,
			final Path screenshotPath) {
		final int before = context.pages().size();
		final Locator legalLink = firstVisibleLocator(candidatesByText(appPage, legalLinkTexts.toArray(new String[0])),
				TIMEOUT_MS);
		legalLink.click();
		waitForUiToSettle(appPage);

		Page targetPage = appPage;
		boolean newTabOpened = false;
		if (context.pages().size() > before) {
			targetPage = context.pages().get(context.pages().size() - 1);
			targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			newTabOpened = true;
		}

		waitForText(targetPage, Arrays.asList(expectedHeading), TIMEOUT_MS);
		assertAnyVisible(expectedHeading + " heading",
				Arrays.asList(targetPage.getByRole(AriaRole.HEADING,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*" + expectedHeading + ".*"))),
						targetPage.getByText(expectedHeading)));
		assertLegalContentVisible(targetPage);
		capture(targetPage, screenshotPath, true);

		final String finalUrl = targetPage.url();
		if (newTabOpened) {
			targetPage.close();
			appPage.bringToFront();
		} else if (!accountPageUrl.isBlank()) {
			appPage.navigate(accountPageUrl);
		}
		waitForUiToSettle(appPage);
		return finalUrl;
	}

	private void assertLegalContentVisible(final Page page) {
		final Locator paragraphs = page.locator("p, li, article");
		final int count = paragraphs.count();
		for (int i = 0; i < Math.min(count, 20); i++) {
			final String text = paragraphs.nth(i).innerText().trim();
			if (text.length() > 40) {
				return;
			}
		}
		throw new AssertionError("Legal content text is not visible.");
	}

	private void clickVisible(final Page page, final String text) {
		final Locator locator = firstVisibleLocator(candidatesByText(page, text), TIMEOUT_MS);
		locator.click();
		waitForUiToSettle(page);
	}

	private void clickIfVisible(final Page page, final String text) {
		final List<Locator> candidates = candidatesByText(page, text);
		if (isAnyVisible(candidates, SHORT_TIMEOUT_MS)) {
			firstVisibleLocator(candidates, SHORT_TIMEOUT_MS).click();
			waitForUiToSettle(page);
		}
	}

	private List<Locator> candidatesByText(final Page page, final String... texts) {
		final List<Locator> result = new ArrayList<>();
		for (final String text : texts) {
			result.add(page.getByText(text, new Page.GetByTextOptions().setExact(true)));
			result.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)));
			result.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)));
		}
		return result;
	}

	private List<Locator> candidatesByRoleAndText(final Page page, final AriaRole role, final String text) {
		return Arrays.asList(page.getByRole(role, new Page.GetByRoleOptions().setName(text)),
				page.getByText(text, new Page.GetByTextOptions().setExact(true)));
	}

	private Locator firstVisibleLocator(final List<Locator> candidates, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final Locator candidate : candidates) {
				try {
					final Locator first = candidate.first();
					if (first.isVisible()) {
						return first;
					}
				} catch (PlaywrightException ignored) {
				}
			}
			if (!candidates.isEmpty()) {
				candidates.get(0).page().waitForTimeout(200);
			}
		}
		throw new AssertionError("No visible element found for the requested text-based selector.");
	}

	private boolean isAnyVisible(final List<Locator> candidates, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final Locator candidate : candidates) {
				try {
					if (candidate.first().isVisible()) {
						return true;
					}
				} catch (PlaywrightException ignored) {
				}
			}
			if (!candidates.isEmpty()) {
				candidates.get(0).page().waitForTimeout(200);
			}
		}
		return false;
	}

	private void assertAnyVisible(final String description, final List<Locator> candidates) {
		if (!isAnyVisible(candidates, TIMEOUT_MS)) {
			throw new AssertionError(description + " is not visible.");
		}
	}

	private void waitForText(final Page page, final List<String> texts, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (isAnyVisible(candidatesByText(page, texts.toArray(new String[0])), SHORT_TIMEOUT_MS)) {
				return;
			}
			page.waitForTimeout(250);
		}
		throw new AssertionError("Expected text was not visible: " + texts);
	}

	private void waitForUiToSettle(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(3000));
		} catch (PlaywrightException ignored) {
			page.waitForTimeout(500);
		}
	}

	private void capture(final Page page, final Path screenshotPath, final boolean fullPage) throws Exception {
		Files.createDirectories(screenshotPath.getParent());
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private void runStep(final String stepName, final Map<String, StepOutcome> report, final List<String> failures,
			final CheckedRunnable step) {
		try {
			step.run();
			report.put(stepName, StepOutcome.pass());
		} catch (final Throwable throwable) {
			final String detail = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getMessage();
			report.put(stepName, StepOutcome.fail(detail));
			failures.add(stepName + ": " + detail);
		}
	}

	private void printReport(final Map<String, StepOutcome> report, final Path screenshotDir) {
		System.out.println("==== SaleADS Mi Negocio Workflow Report ====");
		for (final Map.Entry<String, StepOutcome> entry : report.entrySet()) {
			System.out.printf(Locale.ROOT, "- %s: %s", entry.getKey(), entry.getValue().status);
			if (!entry.getValue().detail.isBlank()) {
				System.out.print(" (" + entry.getValue().detail + ")");
			}
			System.out.println();
		}
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
		System.out.println("===========================================");
	}

	private Path createScreenshotDir() throws Exception {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
				.withZone(ZoneOffset.UTC)
				.format(Instant.now());
		final Path path = Paths.get("target", "saleads-mi-negocio-screenshots", timestamp);
		Files.createDirectories(path);
		return path;
	}

	private String env(final String key, final String fallback) {
		final String value = System.getenv(key);
		return value == null ? fallback : value.trim();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepOutcome {
		private final String status;
		private final String detail;

		private StepOutcome(final String status, final String detail) {
			this.status = status;
			this.detail = detail;
		}

		static StepOutcome pass() {
			return new StepOutcome("PASS", "");
		}

		static StepOutcome fail(final String detail) {
			return new StepOutcome("FAIL", detail);
		}

		StepOutcome withDetail(final String detail) {
			return new StepOutcome(status, detail);
		}
	}
}
