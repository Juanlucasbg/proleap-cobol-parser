package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
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
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullTest {

	private static final Pattern LOGIN_WITH_GOOGLE_PATTERN = Pattern.compile(
			"(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|continuar\\s*con\\s*google|google)");
	private static final Pattern NEGOCIO_PATTERN = Pattern.compile("(?i)negocio");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)mi\\s*negocio");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)agregar\\s*negocio");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)administrar\\s*negocios");
	private static final Pattern CREAR_NUEVO_NEGOCIO_PATTERN = Pattern.compile("(?i)crear\\s*nuevo\\s*negocio");
	private static final Pattern NOMBRE_DEL_NEGOCIO_PATTERN = Pattern.compile("(?i)nombre\\s*del\\s*negocio");
	private static final Pattern TIENES_2_DE_3_PATTERN = Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios");
	private static final Pattern CANCELAR_PATTERN = Pattern.compile("(?i)^cancelar$");
	private static final Pattern CREAR_NEGOCIO_PATTERN = Pattern.compile("(?i)crear\\s*negocio");
	private static final Pattern INFORMACION_GENERAL_PATTERN = Pattern.compile("(?i)informaci[oó]n\\s*general");
	private static final Pattern DETALLES_CUENTA_PATTERN = Pattern.compile("(?i)detalles\\s*de\\s*la\\s*cuenta");
	private static final Pattern TUS_NEGOCIOS_PATTERN = Pattern.compile("(?i)tus\\s*negocios");
	private static final Pattern SECCION_LEGAL_PATTERN = Pattern.compile("(?i)secci[oó]n\\s*legal");
	private static final Pattern BUSINESS_PLAN_PATTERN = Pattern.compile("(?i)business\\s*plan");
	private static final Pattern CAMBIAR_PLAN_PATTERN = Pattern.compile("(?i)cambiar\\s*plan");
	private static final Pattern CUENTA_CREADA_PATTERN = Pattern.compile("(?i)cuenta\\s*creada");
	private static final Pattern ESTADO_ACTIVO_PATTERN = Pattern.compile("(?i)estado\\s*activo");
	private static final Pattern IDIOMA_SELECCIONADO_PATTERN = Pattern.compile("(?i)idioma\\s*seleccionado");
	private static final Pattern TERMINOS_LINK_PATTERN = Pattern.compile("(?i)t[eé]rminos\\s*y\\s*condiciones");
	private static final Pattern POLITICA_LINK_PATTERN = Pattern.compile("(?i)pol[ií]tica\\s*de\\s*privacidad");
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
	private static final Pattern ACCOUNT_EMAIL_PATTERN = Pattern
			.compile("(?i)juanlucasbarbiergarzon@gmail\\.com");
	private static final Pattern ACCOUNT_NAME_PATTERN = Pattern.compile("(?i)juan\\s*lucas|juanlucasbarbiergarzon");

	private static final String RUN_FLAG = "RUN_SALEADS_E2E";
	private static final String BASE_URL_VAR = "SALEADS_BASE_URL";
	private static final String WS_ENDPOINT_VAR = "PLAYWRIGHT_WS_ENDPOINT";

	private Path evidenceDirectory;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		Assume.assumeTrue("Set RUN_SALEADS_E2E=true to execute this UI flow test.", isEnabledEnv(RUN_FLAG));

		final LinkedHashMap<String, StepResult> report = initializeReport();
		Session session = null;

		try (Playwright playwright = Playwright.create()) {
			this.evidenceDirectory = createEvidenceDirectory();
			session = createSession(playwright);

			runStep(report, "Login", () -> {
				final Page appPage = session.appPage;
				final Locator loginButton = findVisibleByText(appPage, LOGIN_WITH_GOOGLE_PATTERN, 8_000);
				final int pageCountBeforeClick = session.context.pages().size();

				clickAndWait(loginButton, appPage);

				final Page popup = waitForNewPage(session.context, pageCountBeforeClick, 8_000);
				if (popup != null) {
					waitForUi(popup);
					selectGoogleAccountIfVisible(popup);
				}

				selectGoogleAccountIfVisible(appPage);
				waitUntilNotGoogleAuth(appPage, 20_000);
				assertSidebarVisible(appPage);
				capture(appPage, "01_dashboard_loaded", true);
				return "Dashboard loaded and sidebar visible.";
			});

			runStep(report, "Mi Negocio menu", () -> {
				final Page appPage = session.appPage;
				findVisibleByText(appPage, NEGOCIO_PATTERN, 8_000);
				clickAndWait(findVisibleByText(appPage, MI_NEGOCIO_PATTERN, 8_000), appPage);
				findVisibleByText(appPage, AGREGAR_NEGOCIO_PATTERN, 8_000);
				findVisibleByText(appPage, ADMINISTRAR_NEGOCIOS_PATTERN, 8_000);
				capture(appPage, "02_mi_negocio_expanded_menu", true);
				return "Mi Negocio submenu expanded.";
			});

			runStep(report, "Agregar Negocio modal", () -> {
				final Page appPage = session.appPage;
				clickAndWait(findVisibleByText(appPage, AGREGAR_NEGOCIO_PATTERN, 8_000), appPage);
				findVisibleByText(appPage, CREAR_NUEVO_NEGOCIO_PATTERN, 8_000);

				final Locator nombreNegocioInput = findNombreNegocioInput(appPage);
				findVisibleByText(appPage, TIENES_2_DE_3_PATTERN, 8_000);
				findVisibleByText(appPage, CANCELAR_PATTERN, 8_000);
				findVisibleByText(appPage, CREAR_NEGOCIO_PATTERN, 8_000);
				capture(appPage, "03_agregar_negocio_modal", true);

				// Optional interaction requested in the workflow.
				nombreNegocioInput.click();
				nombreNegocioInput.fill("Negocio Prueba Automatizacion");
				clickAndWait(findVisibleByText(appPage, CANCELAR_PATTERN, 8_000), appPage);
				return "Agregar Negocio modal validated and closed.";
			});

			runStep(report, "Administrar Negocios view", () -> {
				final Page appPage = session.appPage;
				if (!isVisibleByText(appPage, ADMINISTRAR_NEGOCIOS_PATTERN, 2_000)) {
					clickAndWait(findVisibleByText(appPage, MI_NEGOCIO_PATTERN, 8_000), appPage);
				}

				clickAndWait(findVisibleByText(appPage, ADMINISTRAR_NEGOCIOS_PATTERN, 8_000), appPage);
				findVisibleByText(appPage, INFORMACION_GENERAL_PATTERN, 8_000);
				findVisibleByText(appPage, DETALLES_CUENTA_PATTERN, 8_000);
				findVisibleByText(appPage, TUS_NEGOCIOS_PATTERN, 8_000);
				findVisibleByText(appPage, SECCION_LEGAL_PATTERN, 8_000);
				capture(appPage, "04_administrar_negocios_view", true);
				return "Administrar Negocios sections are visible.";
			});

			runStep(report, "Información General", () -> {
				final Page appPage = session.appPage;
				findVisibleByText(appPage, INFORMACION_GENERAL_PATTERN, 8_000);
				assertTrue("Expected user email to be visible.",
						isVisibleByText(appPage, ACCOUNT_EMAIL_PATTERN, 3_000) || isVisibleByText(appPage, EMAIL_PATTERN, 3_000));
				assertTrue("Expected user name to be visible.",
						isVisibleByText(appPage, ACCOUNT_NAME_PATTERN, 3_000));
				findVisibleByText(appPage, BUSINESS_PLAN_PATTERN, 8_000);
				findVisibleByText(appPage, CAMBIAR_PLAN_PATTERN, 8_000);
				return "Informacion General validated.";
			});

			runStep(report, "Detalles de la Cuenta", () -> {
				final Page appPage = session.appPage;
				findVisibleByText(appPage, DETALLES_CUENTA_PATTERN, 8_000);
				findVisibleByText(appPage, CUENTA_CREADA_PATTERN, 8_000);
				findVisibleByText(appPage, ESTADO_ACTIVO_PATTERN, 8_000);
				findVisibleByText(appPage, IDIOMA_SELECCIONADO_PATTERN, 8_000);
				return "Detalles de la Cuenta validated.";
			});

			runStep(report, "Tus Negocios", () -> {
				final Page appPage = session.appPage;
				findVisibleByText(appPage, TUS_NEGOCIOS_PATTERN, 8_000);
				findVisibleByText(appPage, AGREGAR_NEGOCIO_PATTERN, 8_000);
				findVisibleByText(appPage, TIENES_2_DE_3_PATTERN, 8_000);
				assertTrue("Expected business list to be visible.", isBusinessListVisible(appPage));
				return "Tus Negocios validated.";
			});

			runStep(report, "Términos y Condiciones", () -> validateLegalDocument(
					session, TERMINOS_LINK_PATTERN, TERMINOS_LINK_PATTERN, "08_terminos_y_condiciones", true));

			runStep(report, "Política de Privacidad", () -> validateLegalDocument(
					session, POLITICA_LINK_PATTERN, POLITICA_LINK_PATTERN, "09_politica_de_privacidad", true));
		} finally {
			if (session != null) {
				closeSession(session);
			}
			printReport(report);
			assertNoFailedSteps(report);
		}
	}

	private Session createSession(final Playwright playwright) {
		final Session session = new Session();
		final String wsEndpoint = env(WS_ENDPOINT_VAR);
		final boolean headless = !"false".equalsIgnoreCase(env("SALEADS_HEADLESS"));

		if (wsEndpoint != null && !wsEndpoint.isBlank()) {
			session.browser = playwright.chromium().connect(wsEndpoint);
			session.manageBrowserLifecycle = false;
			if (session.browser.contexts().isEmpty()) {
				session.context = session.browser.newContext();
			} else {
				session.context = session.browser.contexts().get(0);
			}
			if (session.context.pages().isEmpty()) {
				session.appPage = session.context.newPage();
			} else {
				session.appPage = session.context.pages().get(0);
			}
		} else {
			final String baseUrl = env(BASE_URL_VAR);
			Assume.assumeTrue(
					"Set SALEADS_BASE_URL to the login page URL, or set PLAYWRIGHT_WS_ENDPOINT to attach to an existing browser/page.",
					baseUrl != null && !baseUrl.isBlank());
			session.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			session.manageBrowserLifecycle = true;
			session.context = session.browser.newContext();
			session.appPage = session.context.newPage();
			session.appPage.navigate(baseUrl);
		}

		waitForUi(session.appPage);
		return session;
	}

	private void closeSession(final Session session) {
		try {
			if (session.context != null) {
				session.context.close();
			}
		} catch (final Exception ignored) {
		}

		try {
			if (session.manageBrowserLifecycle && session.browser != null) {
				session.browser.close();
			}
		} catch (final Exception ignored) {
		}
	}

	private String validateLegalDocument(final Session session, final Pattern linkPattern, final Pattern headingPattern,
			final String screenshotName, final boolean returnToApplication) {
		final Page appPage = session.appPage;
		final String applicationUrlBefore = appPage.url();
		final int pageCountBeforeClick = session.context.pages().size();

		clickAndWait(findVisibleByText(appPage, linkPattern, 8_000), appPage);

		Page legalPage = waitForNewPage(session.context, pageCountBeforeClick, 8_000);
		boolean openedNewTab = true;
		if (legalPage == null) {
			legalPage = appPage;
			openedNewTab = false;
		}

		waitForUi(legalPage);
		findVisibleByText(legalPage, headingPattern, 8_000);
		assertFalse("Expected legal content text to be visible.", legalPage.content().trim().isBlank());
		capture(legalPage, screenshotName, true);
		final String legalUrl = legalPage.url();

		if (returnToApplication) {
			if (openedNewTab) {
				legalPage.close();
				appPage.bringToFront();
				waitForUi(appPage);
			} else if (!applicationUrlBefore.equals(appPage.url())) {
				appPage.goBack();
				waitForUi(appPage);
			}
		}

		return legalUrl;
	}

	private LinkedHashMap<String, StepResult> initializeReport() {
		final LinkedHashMap<String, StepResult> report = new LinkedHashMap<>();
		report.put("Login", StepResult.pending());
		report.put("Mi Negocio menu", StepResult.pending());
		report.put("Agregar Negocio modal", StepResult.pending());
		report.put("Administrar Negocios view", StepResult.pending());
		report.put("Información General", StepResult.pending());
		report.put("Detalles de la Cuenta", StepResult.pending());
		report.put("Tus Negocios", StepResult.pending());
		report.put("Términos y Condiciones", StepResult.pending());
		report.put("Política de Privacidad", StepResult.pending());
		return report;
	}

	private void runStep(final LinkedHashMap<String, StepResult> report, final String stepName, final StepAction action) {
		try {
			final String details = action.run();
			report.put(stepName, StepResult.passed(details));
		} catch (final Throwable throwable) {
			report.put(stepName, StepResult.failed(throwable.getMessage()));
		}
	}

	private void printReport(final LinkedHashMap<String, StepResult> report) {
		System.out.println();
		System.out.println("======== SaleADS Mi Negocio Final Report ========");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final StepResult result = entry.getValue();
			final String detailsPart = result.details == null || result.details.isBlank() ? "" : " | " + result.details;
			System.out.println(entry.getKey() + ": " + result.status + detailsPart);
		}
		System.out.println("Evidence folder: " + (evidenceDirectory == null ? "not-created" : evidenceDirectory.toAbsolutePath()));
		System.out.println("=================================================");
		System.out.println();
	}

	private void assertNoFailedSteps(final LinkedHashMap<String, StepResult> report) {
		final List<String> failures = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!"PASS".equals(entry.getValue().status)) {
				failures.add(entry.getKey() + " -> " + entry.getValue().details);
			}
		}

		if (!failures.isEmpty()) {
			Assert.fail("One or more SaleADS validations failed:\n" + String.join("\n", failures));
		}
	}

	private Locator findVisibleByText(final Page page, final Pattern pattern, final int timeoutMs) {
		final List<Locator> candidates = List.of(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)),
				page.getByText(pattern));

		for (final Locator candidate : candidates) {
			final Locator first = candidate.first();
			try {
				first.waitFor(new Locator.WaitForOptions().setTimeout((double) timeoutMs)
						.setState(WaitForSelectorState.VISIBLE));
				return first;
			} catch (final PlaywrightException ignored) {
			}
		}

		throw new AssertionError("Visible element not found for pattern: " + pattern.pattern());
	}

	private boolean isVisibleByText(final Page page, final Pattern pattern, final int timeoutMs) {
		try {
			findVisibleByText(page, pattern, timeoutMs);
			return true;
		} catch (final Throwable ignored) {
			return false;
		}
	}

	private Locator findNombreNegocioInput(final Page page) {
		final List<Locator> candidates = List.of(
				page.getByLabel(NOMBRE_DEL_NEGOCIO_PATTERN),
				page.getByPlaceholder(NOMBRE_DEL_NEGOCIO_PATTERN),
				page.locator("input[name*='negocio' i], input[id*='negocio' i], input[placeholder*='Negocio' i]"));

		for (final Locator candidate : candidates) {
			final Locator first = candidate.first();
			try {
				first.waitFor(new Locator.WaitForOptions().setTimeout(8_000d).setState(WaitForSelectorState.VISIBLE));
				return first;
			} catch (final PlaywrightException ignored) {
			}
		}

		throw new AssertionError("Input field 'Nombre del Negocio' was not found.");
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		try {
			final Locator account = findVisibleByText(page, ACCOUNT_EMAIL_PATTERN, 3_000);
			clickAndWait(account, page);
		} catch (final Throwable ignored) {
			// Account selector is optional and only appears in some auth states.
		}
	}

	private void assertSidebarVisible(final Page page) {
		final List<Locator> sidebarCandidates = List.of(
				page.locator("aside"),
				page.locator("nav"),
				page.locator("[class*='sidebar' i]"),
				page.locator("[class*='sidenav' i]"));

		for (final Locator candidate : sidebarCandidates) {
			try {
				candidate.first()
						.waitFor(new Locator.WaitForOptions().setTimeout(8_000d).setState(WaitForSelectorState.VISIBLE));
				return;
			} catch (final PlaywrightException ignored) {
			}
		}

		throw new AssertionError("Left sidebar navigation is not visible.");
	}

	private boolean isBusinessListVisible(final Page page) {
		final List<Locator> candidates = List.of(
				page.locator("table tbody tr"),
				page.locator("ul li"),
				page.locator("[role='row']"),
				page.locator("[class*='business' i], [class*='negocio' i]"));

		for (final Locator candidate : candidates) {
			try {
				if (candidate.count() > 0) {
					candidate.first()
							.waitFor(new Locator.WaitForOptions().setTimeout(3_000d).setState(WaitForSelectorState.VISIBLE));
					return true;
				}
			} catch (final PlaywrightException ignored) {
			}
		}

		return false;
	}

	private void clickAndWait(final Locator locator, final Page page) {
		locator.click();
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(700);
	}

	private Page waitForNewPage(final BrowserContext context, final int initialPageCount, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (context.pages().size() > initialPageCount) {
				final List<Page> pages = context.pages();
				return pages.get(pages.size() - 1);
			}
			safeSleep(200);
		}
		return null;
	}

	private void waitUntilNotGoogleAuth(final Page page, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			final String url = page.url();
			if (url == null || !url.contains("accounts.google.com")) {
				return;
			}
			safeSleep(300);
		}
	}

	private Path createEvidenceDirectory() throws Exception {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		final Path outputPath = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(outputPath);
		return outputPath;
	}

	private void capture(final Page page, final String fileName, final boolean fullPage) {
		if (evidenceDirectory == null) {
			return;
		}
		final Path screenshotPath = evidenceDirectory.resolve(fileName + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private void safeSleep(final int millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private boolean isEnabledEnv(final String envVarName) {
		final String envValue = env(envVarName);
		return "true".equalsIgnoreCase(envValue) || "1".equals(envValue) || "yes".equalsIgnoreCase(envValue);
	}

	private String env(final String name) {
		return System.getenv(name);
	}

	@FunctionalInterface
	private interface StepAction {
		String run() throws Exception;
	}

	private static class Session {
		private Browser browser;
		private BrowserContext context;
		private Page appPage;
		private boolean manageBrowserLifecycle;
	}

	private static class StepResult {
		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details;
		}

		private static StepResult pending() {
			return new StepResult("PENDING", "Step did not execute.");
		}

		private static StepResult passed(final String details) {
			return new StepResult("PASS", details);
		}

		private static StepResult failed(final String details) {
			return new StepResult("FAIL", details == null ? "Unknown failure." : details);
		}
	}
}
