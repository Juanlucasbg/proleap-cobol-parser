package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

public class SaleadsMiNegocioFullTest {

	private static final int DEFAULT_TIMEOUT_MS = 20000;
	private static final int SHORT_TIMEOUT_MS = 5000;
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleads_mi_negocio_full_test() throws Exception {
		final Map<String, StepOutcome> outcomes = new LinkedHashMap<>();
		final Path evidenceDir = createEvidenceDirectory();

		final String loginUrl = getRequiredConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		final String googleAccount = getConfig("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT_EMAIL",
				DEFAULT_GOOGLE_ACCOUNT);
		final boolean headless = Boolean.parseBoolean(getConfig("saleads.headless", "SALEADS_HEADLESS", "true"));

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(250));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1680, 1050));
			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

			recordStep(outcomes, "Login", () -> {
				appPage.navigate(loginUrl);
				waitForUi(appPage);
				loginWithGoogle(appPage, context, googleAccount);

				assertVisible(appPage.locator("aside, nav").first(), "left sidebar navigation");
				saveScreenshot(appPage, evidenceDir, "01-dashboard-loaded.png", false);
			});

			recordStep(outcomes, "Mi Negocio menu", () -> {
				openMiNegocioMenu(appPage);
				assertVisibleText(appPage, "^\\s*Agregar Negocio\\s*$");
				assertVisibleText(appPage, "^\\s*Administrar Negocios\\s*$");
				saveScreenshot(appPage, evidenceDir, "02-mi-negocio-expanded.png", false);
			});

			recordStep(outcomes, "Agregar Negocio modal", () -> {
				clickByVisibleText(appPage, "^\\s*Agregar Negocio\\s*$");
				waitForUi(appPage);

				assertVisibleText(appPage, "^\\s*Crear Nuevo Negocio\\s*$");
				assertVisibleText(appPage, "^\\s*Nombre del Negocio\\s*$");
				assertVisibleText(appPage, "Tienes\\s*2\\s*de\\s*3\\s*negocios");
				assertVisibleText(appPage, "^\\s*Cancelar\\s*$");
				assertVisibleText(appPage, "^\\s*Crear Negocio\\s*$");

				fillIfVisible(appPage, "input[placeholder*='Nombre del Negocio'], input[name*='negocio']");
				saveScreenshot(appPage, evidenceDir, "03-agregar-negocio-modal.png", false);

				clickByVisibleText(appPage, "^\\s*Cancelar\\s*$");
				waitForUi(appPage);
			});

			recordStep(outcomes, "Administrar Negocios view", () -> {
				openMiNegocioMenu(appPage);
				clickByVisibleText(appPage, "^\\s*Administrar Negocios\\s*$");
				waitForUi(appPage);

				assertVisibleText(appPage, "^\\s*Informaci[oó]n General\\s*$");
				assertVisibleText(appPage, "^\\s*Detalles de la Cuenta\\s*$");
				assertVisibleText(appPage, "^\\s*Tus Negocios\\s*$");
				assertVisibleText(appPage, "^\\s*Secci[oó]n Legal\\s*$");

				saveScreenshot(appPage, evidenceDir, "04-administrar-negocios.png", true);
			});

			recordStep(outcomes, "Información General", () -> {
				assertVisibleText(appPage, "Nombre");
				assertVisibleText(appPage, Pattern.quote(googleAccount));
				assertVisibleText(appPage, "@");
				assertVisibleText(appPage, "BUSINESS\\s*PLAN");
				assertVisibleText(appPage, "^\\s*Cambiar Plan\\s*$");
			});

			recordStep(outcomes, "Detalles de la Cuenta", () -> {
				assertVisibleText(appPage, "Cuenta\\s+creada");
				assertVisibleText(appPage, "Estado\\s+activo");
				assertVisibleText(appPage, "Idioma\\s+seleccionado");
			});

			recordStep(outcomes, "Tus Negocios", () -> {
				assertVisibleText(appPage, "^\\s*Tus Negocios\\s*$");
				assertVisibleText(appPage, "^\\s*Agregar Negocio\\s*$");
				assertVisibleText(appPage, "Tienes\\s*2\\s*de\\s*3\\s*negocios");
			});

			recordStep(outcomes, "Términos y Condiciones", () -> {
				final LegalValidationResult result = validateLegalLink(appPage, context, evidenceDir,
						"T[ée]rminos y Condiciones", "T[ée]rminos y Condiciones", "05-terminos-y-condiciones.png");
				outcomes.get("Términos y Condiciones").details = "URL final: " + result.finalUrl;
			});

			recordStep(outcomes, "Política de Privacidad", () -> {
				final LegalValidationResult result = validateLegalLink(appPage, context, evidenceDir,
						"Pol[ií]tica de Privacidad", "Pol[ií]tica de Privacidad", "06-politica-de-privacidad.png");
				outcomes.get("Política de Privacidad").details = "URL final: " + result.finalUrl;
			});

			context.close();
			browser.close();
		}

		writeFinalReport(outcomes, evidenceDir);
		assertAllStepsPassed(outcomes);
	}

	private void loginWithGoogle(final Page appPage, final BrowserContext context, final String googleAccount) {
		final Locator googleLoginButton = firstVisibleOrThrow(appPage, List.of(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Google", Pattern.CASE_INSENSITIVE))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("Google", Pattern.CASE_INSENSITIVE))),
				appPage.getByText(Pattern.compile("Google", Pattern.CASE_INSENSITIVE))));

		Page authPage = null;
		try {
			authPage = context.waitForPage(new BrowserContext.WaitForPageOptions()
					.setTimeout(SHORT_TIMEOUT_MS), () -> googleLoginButton.click());
		} catch (final TimeoutError ignored) {
			waitForUi(appPage);
		}

		final Page pageToHandle = authPage != null ? authPage : appPage;
		selectGoogleAccountIfShown(pageToHandle, googleAccount);

		if (authPage != null) {
			waitForUi(authPage);
			final long closeWaitDeadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
			while (!authPage.isClosed() && System.currentTimeMillis() < closeWaitDeadline) {
				authPage.waitForTimeout(250);
			}
			appPage.bringToFront();
		}

		waitForUi(appPage);
		assertVisible(appPage.locator("aside, nav").first(), "main app sidebar");
	}

	private void openMiNegocioMenu(final Page appPage) {
		assertVisible(appPage.locator("aside, nav").first(), "left sidebar");

		clickIfVisible(appPage, "^\\s*Negocio\\s*$");
		waitForUi(appPage);
		clickIfVisible(appPage, "^\\s*Mi Negocio\\s*$");
		waitForUi(appPage);
	}

	private LegalValidationResult validateLegalLink(final Page appPage, final BrowserContext context, final Path evidenceDir,
			final String linkTextRegex, final String headingTextRegex, final String screenshotName) {
		final Locator link = appPage.getByText(pattern(linkTextRegex)).first();
		assertVisible(link, "legal link: " + linkTextRegex);

		Page legalPage = null;
		boolean openedNewTab = false;
		try {
			legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions()
					.setTimeout(SHORT_TIMEOUT_MS), () -> link.click());
			openedNewTab = true;
		} catch (final TimeoutError ignored) {
			waitForUi(appPage);
			legalPage = appPage;
		}

		waitForUi(legalPage);
		assertVisibleText(legalPage, headingTextRegex);
		final String bodyText = legalPage.locator("body").innerText().trim();
		assertTrue("Expected legal content text to be visible.", bodyText.length() > 100);
		saveScreenshot(legalPage, evidenceDir, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (openedNewTab && legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		}

		return new LegalValidationResult(finalUrl);
	}

	private void selectGoogleAccountIfShown(final Page page, final String email) {
		final Locator accountChoice = page.getByText(pattern("^\\s*" + Pattern.quote(email) + "\\s*$")).first();
		if (isVisible(accountChoice, SHORT_TIMEOUT_MS)) {
			accountChoice.click();
			waitForUi(page);
			return;
		}

		final Locator directEmailInput = page.locator("input[type='email']").first();
		if (isVisible(directEmailInput, SHORT_TIMEOUT_MS)) {
			directEmailInput.fill(email);
			directEmailInput.press("Enter");
			waitForUi(page);
			return;
		}

		final Locator useAnotherAccount = page.getByText(pattern("Use another account|Usar otra cuenta")).first();
		if (isVisible(useAnotherAccount, SHORT_TIMEOUT_MS)) {
			useAnotherAccount.click();
			waitForUi(page);
			final Locator emailInput = page.locator("input[type='email']").first();
			assertVisible(emailInput, "Google email input");
			emailInput.fill(email);
			emailInput.press("Enter");
			waitForUi(page);
		}
	}

	private void fillIfVisible(final Page page, final String selector) {
		final Locator input = page.locator(selector).first();
		if (isVisible(input, SHORT_TIMEOUT_MS)) {
			input.click();
			input.fill("Negocio Prueba Automatizacion");
			waitForUi(page);
		}
	}

	private void clickByVisibleText(final Page page, final String textRegex) {
		final Locator target = page.getByText(pattern(textRegex)).first();
		assertVisible(target, "click target text: " + textRegex);
		target.click();
		waitForUi(page);
	}

	private void clickIfVisible(final Page page, final String textRegex) {
		final Locator target = page.getByText(pattern(textRegex)).first();
		if (isVisible(target, SHORT_TIMEOUT_MS)) {
			target.click();
		}
	}

	private void assertVisibleText(final Page page, final String textRegex) {
		final Locator locator = page.getByText(pattern(textRegex)).first();
		assertVisible(locator, "visible text matching: " + textRegex);
	}

	private void assertVisible(final Locator locator, final String description) {
		locator.waitFor(new Locator.WaitForOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		assertTrue("Expected to find visible " + description, locator.isVisible());
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
			return locator.isVisible();
		} catch (final RuntimeException ignored) {
			return false;
		}
	}

	private Locator firstVisibleOrThrow(final Page page, final List<Locator> candidates) {
		for (final Locator candidate : candidates) {
			if (isVisible(candidate.first(), SHORT_TIMEOUT_MS)) {
				return candidate.first();
			}
		}

		throw new AssertionError("Expected at least one visible locator candidate on page: " + page.url());
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final TimeoutError ignored) {
			try {
				page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
			} catch (final TimeoutError ignoredAgain) {
				// Some SPA updates do not trigger full document load-state transitions.
			}
		}
		page.waitForTimeout(500);
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now(ZoneOffset.UTC));
		final Path evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private void saveScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(evidenceDir.resolve(fileName))
				.setFullPage(fullPage));
	}

	private void writeFinalReport(final Map<String, StepOutcome> outcomes, final Path evidenceDir) throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test\n");
		report.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append("\n\n");

		for (final Map.Entry<String, StepOutcome> entry : outcomes.entrySet()) {
			report.append(entry.getKey())
					.append(": ")
					.append(entry.getValue().passed ? "PASS" : "FAIL");
			if (entry.getValue().details != null && !entry.getValue().details.isBlank()) {
				report.append(" (").append(entry.getValue().details).append(")");
			}
			report.append("\n");
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), report.toString(), StandardCharsets.UTF_8);
	}

	private void assertAllStepsPassed(final Map<String, StepOutcome> outcomes) {
		final StringBuilder failures = new StringBuilder();

		for (final Map.Entry<String, StepOutcome> entry : outcomes.entrySet()) {
			if (!entry.getValue().passed) {
				failures.append("- ")
						.append(entry.getKey())
						.append(": ")
						.append(entry.getValue().details == null ? "No details." : entry.getValue().details)
						.append("\n");
			}
		}

		assertTrue("One or more SaleADS workflow validations failed:\n" + failures, failures.length() == 0);
	}

	private void recordStep(final Map<String, StepOutcome> outcomes, final String stepName, final CheckedAction action) {
		final StepOutcome outcome = new StepOutcome();
		outcomes.put(stepName, outcome);

		try {
			action.run();
			outcome.passed = true;
			if (outcome.details == null) {
				outcome.details = "Validation completed.";
			}
		} catch (final Throwable ex) {
			outcome.passed = false;
			outcome.details = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
		}
	}

	private String getRequiredConfig(final String propertyName, final String envName) {
		final String value = getConfig(propertyName, envName, null);
		assertTrue("Missing required configuration: -D" + propertyName + " or env " + envName,
				value != null && !value.isBlank());
		return value;
	}

	private String getConfig(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private Pattern pattern(final String regex) {
		return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	private interface CheckedAction {
		void run() throws Exception;
	}

	private static class StepOutcome {
		private boolean passed;
		private String details;
	}

	private static class LegalValidationResult {
		private final String finalUrl;

		private LegalValidationResult(final String finalUrl) {
			this.finalUrl = finalUrl;
		}
	}
}
