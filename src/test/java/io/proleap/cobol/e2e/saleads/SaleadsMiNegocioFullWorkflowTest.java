package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assume;
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

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final Pattern GOOGLE_BUTTON_PATTERN = Pattern.compile(
			"Google|Sign in with Google|Iniciar sesi.n con Google|Continuar con Google", Pattern.CASE_INSENSITIVE);
	private static final Pattern GOOGLE_ACCOUNT_PATTERN = Pattern.compile(
			"^\\s*juanlucasbarbiergarzon@gmail\\.com\\s*$", Pattern.CASE_INSENSITIVE);

	@Test
	public void runWorkflow() throws Exception {
		final String e2eEnabled = System.getenv("SALEADS_E2E_ENABLED");
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");

		Assume.assumeTrue("Enable this test with SALEADS_E2E_ENABLED=true.",
				"true".equalsIgnoreCase(e2eEnabled));
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the SaleADS login page for the current environment.",
				loginUrl != null && !loginUrl.isBlank());

		final Path screenshotDir = createScreenshotDir();
		final Map<String, StepResult> report = createEmptyReport();
		final String[] termsUrl = new String[1];
		final String[] privacyUrl = new String[1];
		final String[] appAccountUrl = new String[1];

		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
					.setHeadless(!"false".equalsIgnoreCase(System.getenv("SALEADS_HEADFUL")));
			final Browser browser = playwright.chromium().launch(launchOptions);
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page page = context.newPage();

			page.navigate(loginUrl);
			waitUi(page);

			executeStep(report, "Login", () -> {
				loginWithGoogle(page, context);
				assertTrue("Main application interface should appear after login.",
						isVisible(page.getByText(Pattern.compile("Mi Negocio|Negocio", Pattern.CASE_INSENSITIVE)), 20000));
				assertTrue("Left sidebar should be visible after login.",
						isVisible(page.getByText(Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE)), 20000));
				screenshot(page, screenshotDir.resolve("01-dashboard-loaded.png"), false);
			});

			executeStep(report, "Mi Negocio menu", () -> {
				clickByVisibleText(page, "Negocio");
				clickByVisibleText(page, "Mi Negocio");
				assertTrue("'Agregar Negocio' should be visible in expanded submenu.",
						isVisible(page.getByText(exactText("Agregar Negocio")), 10000));
				assertTrue("'Administrar Negocios' should be visible in expanded submenu.",
						isVisible(page.getByText(exactText("Administrar Negocios")), 10000));
				screenshot(page, screenshotDir.resolve("02-mi-negocio-menu-expanded.png"), false);
			});

			executeStep(report, "Agregar Negocio modal", () -> {
				clickByVisibleText(page, "Agregar Negocio");

				assertTrue("Modal title 'Crear Nuevo Negocio' should be visible.",
						isVisible(page.getByText(exactText("Crear Nuevo Negocio")), 10000));
				assertTrue("Input field label 'Nombre del Negocio' should be visible.",
						isVisible(page.getByText(exactText("Nombre del Negocio")), 10000));
				assertTrue("Business limit text should be visible.",
						isVisible(page.getByText(Pattern.compile("Tienes\\s*2\\s*de\\s*3\\s*negocios", Pattern.CASE_INSENSITIVE)),
								10000));
				assertTrue("'Cancelar' button should be present.",
						isVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(exactText("Cancelar"))),
								10000));
				assertTrue("'Crear Negocio' button should be present.",
						isVisible(
								page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(exactText("Crear Negocio"))),
								10000));

				screenshot(page, screenshotDir.resolve("03-agregar-negocio-modal.png"), false);

				// Optional action requested in the test definition.
				final Locator firstTextbox = firstVisible(5000,
						page.getByLabel(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
						page.getByRole(AriaRole.TEXTBOX));
				firstTextbox.fill("Negocio Prueba Automatizacion");
				clickByVisibleText(page, "Cancelar");
			});

			executeStep(report, "Administrar Negocios view", () -> {
				if (!isVisible(page.getByText(exactText("Administrar Negocios")), 3000)) {
					clickByVisibleText(page, "Mi Negocio");
				}
				clickByVisibleText(page, "Administrar Negocios");
				assertTrue("'Informacion General' section should exist.",
						isVisible(page.getByText(Pattern.compile("Informaci.n General", Pattern.CASE_INSENSITIVE)), 15000));
				assertTrue("'Detalles de la Cuenta' section should exist.",
						isVisible(page.getByText(Pattern.compile("Detalles de la Cuenta", Pattern.CASE_INSENSITIVE)), 15000));
				assertTrue("'Tus Negocios' section should exist.",
						isVisible(page.getByText(Pattern.compile("Tus Negocios", Pattern.CASE_INSENSITIVE)), 15000));
				assertTrue("'Seccion Legal' section should exist.",
						isVisible(page.getByText(Pattern.compile("Secci.n Legal", Pattern.CASE_INSENSITIVE)), 15000));
				appAccountUrl[0] = page.url();
				screenshot(page, screenshotDir.resolve("04-administrar-negocios-page.png"), true);
			});

			executeStep(report, "Informacion General", () -> {
				assertTrue("User name info should be visible.",
						isVisible(page.getByText(Pattern.compile("Nombre|Usuario", Pattern.CASE_INSENSITIVE)), 10000));
				assertTrue("User email should be visible.",
						isVisible(page.getByText(Pattern.compile("juanlucasbarbiergarzon@gmail\\.com", Pattern.CASE_INSENSITIVE)),
								10000));
				assertTrue("'BUSINESS PLAN' should be visible.",
						isVisible(page.getByText(Pattern.compile("BUSINESS PLAN", Pattern.CASE_INSENSITIVE)), 10000));
				assertTrue("'Cambiar Plan' button should be visible.",
						isVisible(page.getByText(Pattern.compile("Cambiar Plan", Pattern.CASE_INSENSITIVE)), 10000));
			});

			executeStep(report, "Detalles de la Cuenta", () -> {
				assertTrue("'Cuenta creada' should be visible.",
						isVisible(page.getByText(Pattern.compile("Cuenta creada", Pattern.CASE_INSENSITIVE)), 10000));
				assertTrue("'Estado activo' should be visible.",
						isVisible(page.getByText(Pattern.compile("Estado activo", Pattern.CASE_INSENSITIVE)), 10000));
				assertTrue("'Idioma seleccionado' should be visible.",
						isVisible(page.getByText(Pattern.compile("Idioma seleccionado", Pattern.CASE_INSENSITIVE)), 10000));
			});

			executeStep(report, "Tus Negocios", () -> {
				assertTrue("'Tus Negocios' section should be visible.",
						isVisible(page.getByText(Pattern.compile("Tus Negocios", Pattern.CASE_INSENSITIVE)), 10000));
				assertTrue("'Agregar Negocio' button should exist in business section.",
						isVisible(page.getByText(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE)), 10000));
				assertTrue("'Tienes 2 de 3 negocios' should be visible in business section.", isVisible(
						page.getByText(Pattern.compile("Tienes\\s*2\\s*de\\s*3\\s*negocios", Pattern.CASE_INSENSITIVE)), 10000));
			});

			executeStep(report, "Terminos y Condiciones", () -> {
				termsUrl[0] = validateLegalPageAndReturn(page, context, appAccountUrl[0],
						Pattern.compile("T.rminos y Condiciones", Pattern.CASE_INSENSITIVE),
						screenshotDir.resolve("05-terminos-y-condiciones.png"));
			});

			executeStep(report, "Politica de Privacidad", () -> {
				privacyUrl[0] = validateLegalPageAndReturn(page, context, appAccountUrl[0],
						Pattern.compile("Pol.tica de Privacidad", Pattern.CASE_INSENSITIVE),
						screenshotDir.resolve("06-politica-de-privacidad.png"));
			});

			printFinalReport(report, termsUrl[0], privacyUrl[0], screenshotDir);
			assertAllStepsPassed(report);

			browser.close();
		}
	}

	private Map<String, StepResult> createEmptyReport() {
		final Map<String, StepResult> report = new LinkedHashMap<>();
		report.put("Login", StepResult.notExecuted());
		report.put("Mi Negocio menu", StepResult.notExecuted());
		report.put("Agregar Negocio modal", StepResult.notExecuted());
		report.put("Administrar Negocios view", StepResult.notExecuted());
		report.put("Informacion General", StepResult.notExecuted());
		report.put("Detalles de la Cuenta", StepResult.notExecuted());
		report.put("Tus Negocios", StepResult.notExecuted());
		report.put("Terminos y Condiciones", StepResult.notExecuted());
		report.put("Politica de Privacidad", StepResult.notExecuted());
		return report;
	}

	private void executeStep(final Map<String, StepResult> report, final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, StepResult.pass());
		} catch (final Throwable error) {
			report.put(stepName, StepResult.fail(error.getMessage()));
		}
	}

	private void loginWithGoogle(final Page page, final BrowserContext context) {
		if (isVisible(page.getByText(Pattern.compile("Mi Negocio|Negocio", Pattern.CASE_INSENSITIVE)), 5000)) {
			return;
		}

		final Locator googleSignIn = firstVisible(10000,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_BUTTON_PATTERN)),
				page.getByText(GOOGLE_BUTTON_PATTERN));

		Page popupPage = null;
		try {
			popupPage = context.waitForPage(() -> clickAndWait(page, googleSignIn),
					new BrowserContext.WaitForPageOptions().setTimeout(8000));
		} catch (final TimeoutError timeout) {
			clickAndWait(page, googleSignIn);
		}

		if (popupPage != null) {
			waitUi(popupPage);
			selectGoogleAccountIfVisible(popupPage);
			waitUi(page);
		} else {
			selectGoogleAccountIfVisible(page);

			for (final Page openPage : context.pages()) {
				if (openPage != page && !openPage.isClosed()) {
					selectGoogleAccountIfVisible(openPage);
				}
			}
		}
	}

	private String validateLegalPageAndReturn(final Page appPage, final BrowserContext context, final String returnUrl,
			final Pattern legalLinkPattern, final Path screenshotPath) {
		final Locator legalLink = firstVisible(10000,
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(legalLinkPattern)),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(legalLinkPattern)),
				appPage.getByText(legalLinkPattern));

		Page legalPage = null;
		try {
			legalPage = context.waitForPage(() -> clickAndWait(appPage, legalLink),
					new BrowserContext.WaitForPageOptions().setTimeout(8000));
		} catch (final TimeoutError timeout) {
			clickAndWait(appPage, legalLink);
		}

		final Page pageToValidate = legalPage != null ? legalPage : appPage;
		waitUi(pageToValidate);
		assertTrue("Expected legal heading was not found.",
				isVisible(pageToValidate.getByText(legalLinkPattern), 15000));
		final String bodyText = pageToValidate.locator("body").innerText();
		assertTrue("Legal content text should be visible.", bodyText != null && bodyText.trim().length() > 80);
		screenshot(pageToValidate, screenshotPath, true);

		final String finalUrl = pageToValidate.url();

		if (legalPage != null) {
			legalPage.close();
			appPage.bringToFront();
			waitUi(appPage);
		} else {
			if (returnUrl != null && !returnUrl.isBlank()) {
				appPage.navigate(returnUrl);
				waitUi(appPage);
			}
		}

		return finalUrl;
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		final Locator accountOption = page.getByText(GOOGLE_ACCOUNT_PATTERN);
		if (isVisible(accountOption, 8000)) {
			clickAndWait(page, accountOption.first());
		}
	}

	private void clickByVisibleText(final Page page, final String text) {
		final Pattern exactPattern = exactText(text);
		final Locator element = firstVisible(10000,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(exactPattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(exactPattern)),
				page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(exactPattern)),
				page.getByText(exactPattern));
		clickAndWait(page, element);
	}

	private Locator firstVisible(final int timeoutMs, final Locator... locators) {
		for (final Locator candidate : locators) {
			try {
				final Locator first = candidate.first();
				first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
				return first;
			} catch (final RuntimeException ignored) {
				// Try the next candidate locator.
			}
		}

		throw new AssertionError("No visible element found for any provided locator.");
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final RuntimeException ignored) {
			return false;
		}
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click();
		waitUi(page);
	}

	private void waitUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final RuntimeException ignored) {
			// Keep moving if this is a SPA state transition.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (final RuntimeException ignored) {
			// NETWORKIDLE may not be reached on pages with long-lived requests.
		}
		page.waitForTimeout(500);
	}

	private Path createScreenshotDir() throws Exception {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Paths.get("target", "e2e-screenshots", TEST_NAME + "-" + timestamp);
		Files.createDirectories(path);
		return path;
	}

	private void screenshot(final Page page, final Path path, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private Pattern exactText(final String text) {
		return Pattern.compile("^\\s*" + Pattern.quote(text) + "\\s*$", Pattern.CASE_INSENSITIVE);
	}

	private void assertAllStepsPassed(final Map<String, StepResult> report) {
		boolean hasFailures = false;
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				hasFailures = true;
				break;
			}
		}
		if (hasFailures) {
			fail("One or more SaleADS workflow validations failed. Check the final report in test output.");
		}
	}

	private void printFinalReport(final Map<String, StepResult> report, final String termsUrl, final String privacyUrl,
			final Path screenshotDir) {
		System.out.println();
		System.out.println("======== FINAL REPORT: " + TEST_NAME + " ========");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final String status = entry.getValue().passed ? "PASS" : "FAIL";
			final String detail = entry.getValue().detail == null ? "" : " - " + entry.getValue().detail;
			System.out.println(entry.getKey() + ": " + status + detail);
		}
		System.out.println("Terminos y Condiciones URL: " + (termsUrl == null ? "N/A" : termsUrl));
		System.out.println("Politica de Privacidad URL: " + (privacyUrl == null ? "N/A" : privacyUrl));
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
		System.out.println("===============================================");
		System.out.println();
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}

		private static StepResult pass() {
			return new StepResult(true, null);
		}

		private static StepResult fail(final String detail) {
			return new StepResult(false, detail == null ? "Unknown error." : detail);
		}

		private static StepResult notExecuted() {
			return new StepResult(false, "Not executed.");
		}
	}
}
