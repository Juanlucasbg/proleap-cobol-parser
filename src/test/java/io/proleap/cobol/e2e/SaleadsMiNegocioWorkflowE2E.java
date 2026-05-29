package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
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
 * Manual E2E workflow test for SaleADS "Mi Negocio".
 *
 * Run example:
 * mvn -Dtest=io.proleap.cobol.e2e.SaleadsMiNegocioWorkflowE2E
 * -Dsaleads.baseUrl=https://your-environment/login -Dsaleads.headless=false test
 */
public class SaleadsMiNegocioWorkflowE2E {

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final int STEP_TIMEOUT_MS = Integer.getInteger("saleads.stepTimeoutMs", 15000);
	private static final int UI_STABILIZE_MS = Integer.getInteger("saleads.uiStabilizeMs", 900);
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> results = new LinkedHashMap<>();
	private Path evidenceDir;
	private Path reportPath;
	private Page appPage;
	private String currentStepField;

	@Test
	public void runSaleadsMiNegocioWorkflow() throws Exception {
		evidenceDir = createEvidenceDir();
		reportPath = evidenceDir.resolve("final-report.txt");

		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		final String baseUrl = resolveBaseUrl();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			appPage = context.newPage();

			if (baseUrl != null) {
				appPage.navigate(baseUrl);
				waitForUi(appPage);
			} else {
				throw new IllegalStateException(
						"Set SALEADS_BASE_URL or -Dsaleads.baseUrl to the login page URL for the target environment.");
			}

			runStep("Login", () -> loginWithGoogle(context));
			runStep("Mi Negocio menu", this::openMiNegocioMenu);
			runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::openAdministrarNegocios);
			runStep("Información General", this::validateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
			runStep("Tus Negocios", this::validateTusNegocios);
			runStep("Términos y Condiciones",
					() -> validateLegalLink("Términos y Condiciones", "Términos y Condiciones", "terminos-y-condiciones"));
			runStep("Política de Privacidad",
					() -> validateLegalLink("Política de Privacidad", "Política de Privacidad", "politica-de-privacidad"));

			writeFinalReport();
			assertAllStepsPassed();
		}
	}

	private void loginWithGoogle(final BrowserContext context) {
		final Locator loginButton = findByAnySelector(appPage, "Google login button",
				"button:has-text(\"Sign in with Google\")", "button:has-text(\"Iniciar sesión con Google\")",
				"button:has-text(\"Continuar con Google\")", "text=/Sign in with Google|Iniciar sesión con Google|Continuar con Google/i");

		Page popupPage = null;
		try {
			popupPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(8000),
					() -> clickAndWaitForUi(appPage, loginButton));
		} catch (final PlaywrightException ignored) {
			clickAndWaitForUi(appPage, loginButton);
		}

		if (popupPage != null) {
			waitForUi(popupPage);
			selectGoogleAccountIfVisible(popupPage);
			try {
				popupPage.waitForClose(new Page.WaitForCloseOptions().setTimeout(30000));
			} catch (final PlaywrightException ignored) {
			}
			appPage.bringToFront();
		} else {
			selectGoogleAccountIfVisible(appPage);
		}

		findByAnySelector(appPage, "main app interface", "main", "[role='main']", "text=/Mi Negocio|Dashboard|Inicio/i");
		findByAnySelector(appPage, "left sidebar", "aside", "nav", "[role='navigation']");
		takeScreenshot(appPage, "01-dashboard-loaded.png", true);
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		final Locator accountLocator = findOptionalByAnySelector(page, 4000, "text=" + GOOGLE_EMAIL,
				"div:has-text(\"" + GOOGLE_EMAIL + "\")", "button:has-text(\"" + GOOGLE_EMAIL + "\")");
		if (accountLocator != null) {
			clickAndWaitForUi(page, accountLocator);
		}
	}

	private void openMiNegocioMenu() {
		findByAnySelector(appPage, "Negocio section", "text=/^Negocio$/i", "text=/Negocio/i");
		final Locator miNegocio = findByAnySelector(appPage, "Mi Negocio option", "button:has-text(\"Mi Negocio\")",
				"a:has-text(\"Mi Negocio\")", "text=/Mi Negocio/i");
		clickAndWaitForUi(appPage, miNegocio);

		findByAnySelector(appPage, "Agregar Negocio option", "button:has-text(\"Agregar Negocio\")",
				"a:has-text(\"Agregar Negocio\")", "text=/Agregar Negocio/i");
		findByAnySelector(appPage, "Administrar Negocios option", "button:has-text(\"Administrar Negocios\")",
				"a:has-text(\"Administrar Negocios\")", "text=/Administrar Negocios/i");
		takeScreenshot(appPage, "02-mi-negocio-expanded.png", false);
	}

	private void validateAgregarNegocioModal() {
		final Locator agregarNegocio = findByAnySelector(appPage, "Agregar Negocio button", "button:has-text(\"Agregar Negocio\")",
				"a:has-text(\"Agregar Negocio\")", "text=/Agregar Negocio/i");
		clickAndWaitForUi(appPage, agregarNegocio);

		findByAnySelector(appPage, "Crear Nuevo Negocio title", "text=/Crear Nuevo Negocio/i");
		findByAnySelector(appPage, "Nombre del Negocio field", "label:has-text(\"Nombre del Negocio\")",
				"input[placeholder*='Nombre del Negocio']", "text=/Nombre del Negocio/i");
		findByAnySelector(appPage, "Tienes 2 de 3 negocios text", "text=/Tienes\\s*2\\s*de\\s*3\\s*negocios/i");
		findByAnySelector(appPage, "Cancelar button", "button:has-text(\"Cancelar\")");
		final Locator crearNegocioButton = findByAnySelector(appPage, "Crear Negocio button",
				"button:has-text(\"Crear Negocio\")");
		Assert.assertTrue("Crear Negocio button is not visible.", crearNegocioButton.isVisible());
		takeScreenshot(appPage, "03-agregar-negocio-modal.png", false);

		final Locator nombreInput = findOptionalByAnySelector(appPage, 3000, "input[placeholder*='Nombre del Negocio']",
				"input[name*='negocio']", "input[name*='nombre']");
		if (nombreInput != null) {
			nombreInput.click();
			waitForUi(appPage);
			nombreInput.fill("Negocio Prueba Automatización");
		}

		final Locator cancelar = findByAnySelector(appPage, "Cancelar button", "button:has-text(\"Cancelar\")");
		clickAndWaitForUi(appPage, cancelar);
	}

	private void openAdministrarNegocios() {
		final Locator adminNegocios = findOptionalByAnySelector(appPage, 3000, "button:has-text(\"Administrar Negocios\")",
				"a:has-text(\"Administrar Negocios\")", "text=/Administrar Negocios/i");
		if (adminNegocios == null) {
			final Locator miNegocio = findByAnySelector(appPage, "Mi Negocio option", "button:has-text(\"Mi Negocio\")",
					"a:has-text(\"Mi Negocio\")", "text=/Mi Negocio/i");
			clickAndWaitForUi(appPage, miNegocio);
		}

		final Locator administrar = findByAnySelector(appPage, "Administrar Negocios option",
				"button:has-text(\"Administrar Negocios\")", "a:has-text(\"Administrar Negocios\")",
				"text=/Administrar Negocios/i");
		clickAndWaitForUi(appPage, administrar);

		findByAnySelector(appPage, "Información General section", "text=/Información General/i");
		findByAnySelector(appPage, "Detalles de la Cuenta section", "text=/Detalles de la Cuenta/i");
		findByAnySelector(appPage, "Tus Negocios section", "text=/Tus Negocios/i");
		findByAnySelector(appPage, "Sección Legal section", "text=/Sección Legal/i");
		takeScreenshot(appPage, "04-administrar-negocios.png", true);
	}

	private void validateInformacionGeneral() {
		findByAnySelector(appPage, "Información General section", "text=/Información General/i");
		final String pageText = appPage.locator("body").innerText();

		final Matcher emailMatcher = EMAIL_PATTERN.matcher(pageText);
		Assert.assertTrue("User email is not visible.", emailMatcher.find());

		final String email = emailMatcher.group();
		final String withoutEmail = pageText.replace(email, " ").replaceAll("\\s+", " ").trim();
		assertTrue("User name appears to be missing.", withoutEmail.length() > 30);

		findByAnySelector(appPage, "BUSINESS PLAN text", "text=/BUSINESS PLAN/i");
		findByAnySelector(appPage, "Cambiar Plan button", "button:has-text(\"Cambiar Plan\")",
				"a:has-text(\"Cambiar Plan\")", "text=/Cambiar Plan/i");
	}

	private void validateDetallesCuenta() {
		findByAnySelector(appPage, "Detalles de la Cuenta section", "text=/Detalles de la Cuenta/i");
		findByAnySelector(appPage, "Cuenta creada text", "text=/Cuenta creada/i");
		findByAnySelector(appPage, "Estado activo text", "text=/Estado activo/i");
		findByAnySelector(appPage, "Idioma seleccionado text", "text=/Idioma seleccionado/i");
	}

	private void validateTusNegocios() {
		final Locator tusNegociosSection = findByAnySelector(appPage, "Tus Negocios section",
				"section:has-text(\"Tus Negocios\")", "div:has-text(\"Tus Negocios\")", "text=/Tus Negocios/i");
		final int listItemCount = tusNegociosSection.locator("[role='listitem'], tr, li, [role='row'], .card, [data-testid*='business']")
				.count();
		Assert.assertTrue("Business list is not visible.", listItemCount > 0);
		findByAnySelector(appPage, "Agregar Negocio button", "button:has-text(\"Agregar Negocio\")",
				"a:has-text(\"Agregar Negocio\")", "text=/Agregar Negocio/i");
		findByAnySelector(appPage, "Tienes 2 de 3 negocios text", "text=/Tienes\\s*2\\s*de\\s*3\\s*negocios/i");
	}

	private void validateLegalLink(final String linkText, final String headingText, final String evidenceName) {
		final Locator link = findByAnySelector(appPage, linkText + " link", "a:has-text(\"" + linkText + "\")",
				"button:has-text(\"" + linkText + "\")", "text=/" + Pattern.quote(linkText) + "/i");

		Page legalPage = null;
		try {
			legalPage = appPage.context().waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(7000), () -> {
				link.click(new Locator.ClickOptions().setTimeout(STEP_TIMEOUT_MS));
				waitForUi(appPage);
			});
		} catch (final PlaywrightException ignored) {
			clickAndWaitForUi(appPage, link);
		}

		if (legalPage != null) {
			waitForUi(legalPage);
			findByAnySelector(legalPage, headingText + " heading", "h1:has-text(\"" + headingText + "\")",
					"h2:has-text(\"" + headingText + "\")", "text=/" + Pattern.quote(headingText) + "/i");
			assertLegalContent(legalPage);
			takeScreenshot(legalPage, "05-" + evidenceName + ".png", true);
			appendDetail(linkText, "Final URL: " + legalPage.url());
			legalPage.close();
			appPage.bringToFront();
		} else {
			findByAnySelector(appPage, headingText + " heading", "h1:has-text(\"" + headingText + "\")",
					"h2:has-text(\"" + headingText + "\")", "text=/" + Pattern.quote(headingText) + "/i");
			assertLegalContent(appPage);
			takeScreenshot(appPage, "05-" + evidenceName + ".png", true);
			appendDetail(linkText, "Final URL: " + appPage.url());
			appPage.goBack();
			waitForUi(appPage);
		}
	}

	private void assertLegalContent(final Page page) {
		final String content = page.locator("body").innerText();
		assertTrue("Legal content is not visible.", content != null && content.trim().length() > 120);
	}

	private void runStep(final String fieldName, final StepAction action) {
		currentStepField = fieldName;
		final StepResult stepResult = StepResult.pass();
		results.put(fieldName, stepResult);
		try {
			action.run();
		} catch (final Throwable error) {
			final String message = safeMessage(error);
			takeScreenshot(appPage, "error-" + slug(fieldName) + ".png", true);
			stepResult.markFailed(message);
		} finally {
			currentStepField = null;
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(STEP_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED,
					new Page.WaitForLoadStateOptions().setTimeout(STEP_TIMEOUT_MS));
		}
		page.waitForTimeout(UI_STABILIZE_MS);
	}

	private void clickAndWaitForUi(final Page page, final Locator locator) {
		locator.first().scrollIntoViewIfNeeded();
		locator.first().click(new Locator.ClickOptions().setTimeout(STEP_TIMEOUT_MS));
		waitForUi(page);
	}

	private Locator findByAnySelector(final Page page, final String description, final String... selectors) {
		final Locator locator = findOptionalByAnySelector(page, STEP_TIMEOUT_MS, selectors);
		if (locator == null) {
			throw new AssertionError("Could not find visible element: " + description);
		}
		return locator;
	}

	private Locator findOptionalByAnySelector(final Page page, final int timeoutMs, final String... selectors) {
		for (final String selector : selectors) {
			final Locator candidate = page.locator(selector).first();
			try {
				candidate.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
				return candidate;
			} catch (final PlaywrightException ignored) {
			}
		}
		return null;
	}

	private void appendDetail(final String fieldName, final String detail) {
		final String targetField = currentStepField == null ? fieldName : currentStepField;
		final StepResult existing = results.get(targetField);
		if (existing != null) {
			existing.details.add(detail);
		}
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) {
		if (page == null || page.isClosed()) {
			return;
		}

		final Path path = evidenceDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private Path createEvidenceDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(path);
		return path;
	}

	private String resolveBaseUrl() {
		final String systemProperty = System.getProperty("saleads.baseUrl");
		if (systemProperty != null && !systemProperty.isBlank()) {
			return systemProperty.trim();
		}
		final String env = System.getenv("SALEADS_BASE_URL");
		if (env != null && !env.isBlank()) {
			return env.trim();
		}
		return null;
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio workflow result");
		lines.add("Evidence directory: " + evidenceDir.toAbsolutePath());
		lines.add("");
		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.getOrDefault(field, StepResult.fail("Step did not run."));
			lines.add(field + ": " + (result.passed ? "PASS" : "FAIL"));
			if (!result.message.isBlank()) {
				lines.add("  Detail: " + result.message);
			}
			for (final String detail : result.details) {
				lines.add("  " + detail);
			}
		}
		Files.write(reportPath, lines, StandardCharsets.UTF_8);
		System.out.println(String.join(System.lineSeparator(), lines));
		System.out.println("Final report written to: " + reportPath.toAbsolutePath());
	}

	private void assertAllStepsPassed() {
		final List<String> failures = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.get(field);
			if (result == null || !result.passed) {
				failures.add(field + " => " + (result == null ? "not executed" : result.message));
			}
		}

		if (!failures.isEmpty()) {
			Assert.fail("One or more SaleADS workflow validations failed. Report: " + reportPath.toAbsolutePath() + " | "
					+ String.join(" || ", failures));
		}
	}

	private String safeMessage(final Throwable error) {
		final String message = error.getMessage();
		if (message == null || message.isBlank()) {
			return error.getClass().getSimpleName();
		}
		return message.replaceAll("\\s+", " ").trim();
	}

	private String slug(final String value) {
		final String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-|-$)", "");
		return normalized.isBlank() ? "step" : normalized;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private boolean passed;
		private String message;
		private final List<String> details;

		private StepResult(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message == null ? "" : message;
			this.details = new ArrayList<>();
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String message) {
			return new StepResult(false, message);
		}

		private void markFailed(final String message) {
			this.passed = false;
			this.message = message == null ? "" : message;
		}
	}
}
