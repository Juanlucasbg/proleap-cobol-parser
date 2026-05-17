package io.proleap.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String RUN_E2E_ENV = "RUN_SALEADS_E2E";
	private static final String START_URL_ENV = "SALEADS_START_URL";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String GOOGLE_ACCOUNT_ENV = "SALEADS_GOOGLE_ACCOUNT";
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final long DEFAULT_TIMEOUT_MS = 15000;
	private static final Pattern LOGIN_WITH_GOOGLE_PATTERN = Pattern
			.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|continuar\\s*con\\s*google|iniciar\\s*sesi[oó]n|login)");
	private static final Pattern NEGOCIO_PATTERN = Pattern.compile("(?i)negocio");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)mi\\s*negocio");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)agregar\\s*negocio");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)administrar\\s*negocios");
	private static final Pattern TERMS_PATTERN = Pattern.compile("(?i)t[ée]rminos\\s*y\\s*condiciones");
	private static final Pattern PRIVACY_PATTERN = Pattern.compile("(?i)pol[ií]tica\\s*de\\s*privacidad");

	private enum StepResult {
		PASS, FAIL, SKIPPED
	}

	private final Map<String, StepResult> results = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		initializeReport();

		final boolean runE2E = Boolean.parseBoolean(System.getenv().getOrDefault(RUN_E2E_ENV, "false"));
		assumeTrue("Set " + RUN_E2E_ENV + "=true to execute this workflow test.", runE2E);

		final String startUrl = System.getenv(START_URL_ENV);
		assumeTrue("Set " + START_URL_ENV + " to the current SaleADS environment login page.", startUrl != null
				&& !startUrl.isBlank());

		final Path evidenceDirectory = Path.of("target", "saleads-evidence");
		Files.createDirectories(evidenceDirectory);

		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault(HEADLESS_ENV, "true"));
		final String googleAccount = System.getenv().getOrDefault(GOOGLE_ACCOUNT_ENV, DEFAULT_GOOGLE_ACCOUNT);

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page[] appPageRef = new Page[] { context.newPage() };
			appPageRef[0].navigate(startUrl);
			waitForUi(appPageRef[0]);

			final boolean loginOk = runStep("Login", () -> {
				Page maybeGooglePage = clickAndCapturePopup(context, appPageRef[0], () -> clickByVisibleText(appPageRef[0],
						LOGIN_WITH_GOOGLE_PATTERN, "Sign in with Google"));
				if (maybeGooglePage != null) {
					waitForUi(maybeGooglePage);
					selectGoogleAccountIfVisible(maybeGooglePage, googleAccount);
				} else {
					selectGoogleAccountIfVisible(appPageRef[0], googleAccount);
				}

				appPageRef[0] = waitForApplicationPage(context);
				assertVisibleText(appPageRef[0], NEGOCIO_PATTERN, "Left sidebar navigation");
				assertVisibleText(appPageRef[0], MI_NEGOCIO_PATTERN, "Left navigation with Mi Negocio");
				saveScreenshot(appPageRef[0], evidenceDirectory, "01-dashboard-loaded.png", false);
			});

			final boolean menuOk = loginOk && runStep("Mi Negocio menu", () -> {
				assertVisibleText(appPageRef[0], NEGOCIO_PATTERN, "Negocio section");
				clickByVisibleText(appPageRef[0], MI_NEGOCIO_PATTERN, "Mi Negocio");
				waitForUi(appPageRef[0]);
				assertVisibleText(appPageRef[0], AGREGAR_NEGOCIO_PATTERN, "Agregar Negocio option");
				assertVisibleText(appPageRef[0], ADMINISTRAR_NEGOCIOS_PATTERN, "Administrar Negocios option");
				saveScreenshot(appPageRef[0], evidenceDirectory, "02-mi-negocio-expanded.png", false);
			});

			final boolean modalOk = menuOk && runStep("Agregar Negocio modal", () -> {
				clickByVisibleText(appPageRef[0], AGREGAR_NEGOCIO_PATTERN, "Agregar Negocio");
				waitForUi(appPageRef[0]);
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)crear\\s+nuevo\\s+negocio"), "Crear Nuevo Negocio title");
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)nombre\\s+del\\s+negocio"), "Nombre del Negocio field");
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"), "Business usage text");
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)cancelar"), "Cancelar button");
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)crear\\s+negocio"), "Crear Negocio button");

				fillIfVisible(appPageRef[0], Pattern.compile("(?i)nombre\\s+del\\s+negocio"), "Negocio Prueba Automatizacion");
				saveScreenshot(appPageRef[0], evidenceDirectory, "03-agregar-negocio-modal.png", false);
				clickByVisibleText(appPageRef[0], Pattern.compile("(?i)cancelar"), "Cancelar");
				waitForUi(appPageRef[0]);
			});

			final boolean administrarOk = modalOk && runStep("Administrar Negocios view", () -> {
				clickByVisibleText(appPageRef[0], MI_NEGOCIO_PATTERN, "Mi Negocio");
				waitForUi(appPageRef[0]);
				clickByVisibleText(appPageRef[0], ADMINISTRAR_NEGOCIOS_PATTERN, "Administrar Negocios");
				waitForUi(appPageRef[0]);
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)informaci[oó]n\\s+general"), "Informacion General section");
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta"), "Detalles de la Cuenta section");
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)tus\\s+negocios"), "Tus Negocios section");
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)secci[oó]n\\s+legal"), "Seccion Legal section");
				saveScreenshot(appPageRef[0], evidenceDirectory, "04-administrar-negocios-full.png", true);
			});

			final boolean informacionGeneralOk = administrarOk && runStep("Información General", () -> {
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)nombre"), "User name");
				assertVisibleText(appPageRef[0], Pattern.compile("@"), "User email");
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)business\\s+plan"), "BUSINESS PLAN text");
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)cambiar\\s+plan"), "Cambiar Plan button");
			});

			final boolean detallesCuentaOk = administrarOk && runStep("Detalles de la Cuenta", () -> {
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)cuenta\\s+creada"), "Cuenta creada");
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)estado\\s+activo"), "Estado activo");
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)idioma\\s+seleccionado"), "Idioma seleccionado");
			});

			final boolean tusNegociosOk = administrarOk && runStep("Tus Negocios", () -> {
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)tus\\s+negocios"), "Tus Negocios section");
				assertVisibleText(appPageRef[0], AGREGAR_NEGOCIO_PATTERN, "Agregar Negocio button in business section");
				assertVisibleText(appPageRef[0], Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"), "Business limit text");
			});

			final boolean termsOk = administrarOk && runStep("Términos y Condiciones", () -> {
				final LegalValidationResult termsResult = validateLegalPage(context, appPageRef[0], TERMS_PATTERN,
						Pattern.compile("(?i)t[ée]rminos\\s+y\\s+condiciones"), evidenceDirectory, "05-terms.png");
				termsUrl = termsResult.url();
				appPageRef[0] = termsResult.applicationPage();
			});

			final boolean privacyOk = administrarOk && runStep("Política de Privacidad", () -> {
				final LegalValidationResult privacyResult = validateLegalPage(context, appPageRef[0], PRIVACY_PATTERN,
						Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"), evidenceDirectory, "06-privacy.png");
				privacyUrl = privacyResult.url();
				appPageRef[0] = privacyResult.applicationPage();
			});

			writeFinalReport(evidenceDirectory, startUrl);
			assertNoFailures();
			browser.close();
		}
	}

	private void initializeReport() {
		results.put("Login", StepResult.SKIPPED);
		results.put("Mi Negocio menu", StepResult.SKIPPED);
		results.put("Agregar Negocio modal", StepResult.SKIPPED);
		results.put("Administrar Negocios view", StepResult.SKIPPED);
		results.put("Información General", StepResult.SKIPPED);
		results.put("Detalles de la Cuenta", StepResult.SKIPPED);
		results.put("Tus Negocios", StepResult.SKIPPED);
		results.put("Términos y Condiciones", StepResult.SKIPPED);
		results.put("Política de Privacidad", StepResult.SKIPPED);
	}

	private boolean runStep(final String reportKey, final StepAction action) {
		try {
			action.run();
			results.put(reportKey, StepResult.PASS);
			return true;
		} catch (final Throwable t) {
			results.put(reportKey, StepResult.FAIL);
			failures.add(reportKey + " => " + t.getMessage());
			return false;
		}
	}

	private Page clickAndCapturePopup(final BrowserContext context, final Page currentPage, final Runnable clickAction) {
		final List<Page> previousPages = new ArrayList<>(context.pages());
		clickAction.run();
		waitForUi(currentPage);

		final long deadline = System.currentTimeMillis() + 10000;
		while (System.currentTimeMillis() < deadline) {
			for (final Page candidate : context.pages()) {
				if (!previousPages.contains(candidate)) {
					waitForUi(candidate);
					return candidate;
				}
			}
			sleep(250);
		}
		return null;
	}

	private void selectGoogleAccountIfVisible(final Page page, final String googleAccount) {
		final Locator accountChoice = page.getByText(Pattern.compile(Pattern.quote(googleAccount), Pattern.CASE_INSENSITIVE));
		if (isVisible(accountChoice, 5000)) {
			accountChoice.first().click();
			waitForUi(page);
			return;
		}

		final Locator useAnotherAccount = page.getByText(Pattern.compile("(?i)usar\\s*otra\\s*cuenta|use\\s*another\\s*account"));
		if (isVisible(useAnotherAccount, 3000)) {
			useAnotherAccount.first().click();
			waitForUi(page);
		}
	}

	private Page waitForApplicationPage(final BrowserContext context) {
		final long deadline = System.currentTimeMillis() + 40000;
		while (System.currentTimeMillis() < deadline) {
			for (final Page page : context.pages()) {
				try {
					if (isVisible(page.getByText(MI_NEGOCIO_PATTERN), 1500)) {
						page.bringToFront();
						waitForUi(page);
						return page;
					}
				} catch (final RuntimeException ex) {
					// continue scanning available pages.
				}
			}
			sleep(500);
		}
		throw new IllegalStateException("Unable to detect the SaleADS application page after Google login.");
	}

	private void clickByVisibleText(final Page page, final Pattern textPattern, final String description) {
		Locator candidate = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern));
		if (isVisible(candidate, 4000)) {
			candidate.first().click();
			waitForUi(page);
			return;
		}

		candidate = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern));
		if (isVisible(candidate, 4000)) {
			candidate.first().click();
			waitForUi(page);
			return;
		}

		candidate = page.getByText(textPattern);
		if (isVisible(candidate, 4000)) {
			candidate.first().click();
			waitForUi(page);
			return;
		}

		throw new IllegalStateException("Could not click element by visible text: " + description);
	}

	private void assertVisibleText(final Page page, final Pattern textPattern, final String description) {
		final Locator candidate = page.getByText(textPattern);
		if (!isVisible(candidate, DEFAULT_TIMEOUT_MS)) {
			throw new IllegalStateException("Expected visible text not found: " + description);
		}
	}

	private boolean isVisible(final Locator locator, final long timeoutMs) {
		try {
			locator.first()
					.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final RuntimeException ex) {
			return false;
		}
	}

	private void fillIfVisible(final Page page, final Pattern labelPattern, final String value) {
		Locator input = page.getByLabel(labelPattern);
		if (!isVisible(input, 4000)) {
			input = page.getByPlaceholder(labelPattern);
		}
		if (isVisible(input, 4000)) {
			input.first().fill(value);
		}
	}

	private LegalValidationResult validateLegalPage(final BrowserContext context, final Page applicationPage,
			final Pattern linkPattern, final Pattern headingPattern, final Path evidenceDirectory, final String screenshotName) {
		final String originalUrl = applicationPage.url();
		final Page legalPage = clickAndCapturePopup(context, applicationPage,
				() -> clickByVisibleText(applicationPage, linkPattern, linkPattern.pattern()));

		final boolean openedNewTab = legalPage != null && legalPage != applicationPage;
		final Page activeLegalPage = openedNewTab ? legalPage : applicationPage;
		waitForUi(activeLegalPage);
		final String legalUrl = activeLegalPage.url();

		assertVisibleText(activeLegalPage, headingPattern, "Legal heading");
		final String bodyText = activeLegalPage.locator("body").innerText();
		assertTrue("Legal content must be visible.", bodyText != null && bodyText.trim().length() > 120);
		saveScreenshot(activeLegalPage, evidenceDirectory, screenshotName, true);

		if (openedNewTab) {
			applicationPage.bringToFront();
			waitForUi(applicationPage);
			return new LegalValidationResult(legalUrl, applicationPage);
		}

		try {
			final Response ignored = activeLegalPage.goBack();
			waitForUi(activeLegalPage);
		} catch (final RuntimeException ex) {
			activeLegalPage.navigate(originalUrl);
			waitForUi(activeLegalPage);
		}
		return new LegalValidationResult(legalUrl, activeLegalPage);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (final RuntimeException ex) {
			// Continue even if this state is already reached or not applicable.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (final RuntimeException ex) {
			// Some pages keep connections open permanently; continue test flow.
		}
	}

	private void saveScreenshot(final Page page, final Path evidenceDirectory, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDirectory.resolve(fileName)).setFullPage(fullPage));
	}

	private void writeFinalReport(final Path evidenceDirectory, final String startUrl) throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		report.append("generated_at=").append(Instant.now()).append(System.lineSeparator());
		report.append("start_url=").append(startUrl).append(System.lineSeparator());
		report.append(System.lineSeparator());
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			report.append(entry.getKey()).append(": ").append(entry.getValue().name()).append(System.lineSeparator());
		}
		report.append(System.lineSeparator());
		report.append("terminos_y_condiciones_url=").append(termsUrl).append(System.lineSeparator());
		report.append("politica_de_privacidad_url=").append(privacyUrl).append(System.lineSeparator());
		if (!failures.isEmpty()) {
			report.append(System.lineSeparator()).append("failures:").append(System.lineSeparator());
			for (final String failure : failures) {
				report.append("- ").append(failure).append(System.lineSeparator());
			}
		}
		Files.writeString(evidenceDirectory.resolve("final-report.txt"), report.toString());
	}

	private void assertNoFailures() {
		if (failures.isEmpty()) {
			return;
		}
		throw new AssertionError("Workflow validation failures: " + String.join(" | ", failures));
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private record LegalValidationResult(String url, Page applicationPage) {
	}
}
