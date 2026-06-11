package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

public class SaleadsMiNegocioFullWorkflowTest {

	private static final int DEFAULT_TIMEOUT_MS = 15000;
	private static final Pattern BUSINESS_LIMIT_PATTERN = Pattern.compile("Tienes\\s+\\d+\\s+de\\s+\\d+\\s+negocios",
			Pattern.CASE_INSENSITIVE);
	private static final List<String> REPORT_FIELDS = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to the current environment login URL before running this test.",
				loginUrl != null && !loginUrl.isBlank());

		final Path evidenceDir = createEvidenceDirectory();
		final Map<String, Boolean> report = new LinkedHashMap<>();
		String termsUrl = "N/A";
		String privacyUrl = "N/A";

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(
					new BrowserType.LaunchOptions().setHeadless(parseBooleanEnv("HEADLESS", true)).setSlowMo(200));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page appPage = context.newPage();

			appPage.navigate(loginUrl);
			waitForUiLoad(appPage);

			report.put("Login", runStep(() -> stepLoginWithGoogle(appPage, context, evidenceDir), "Login"));
			report.put("Mi Negocio menu", runStep(() -> stepOpenMiNegocioMenu(appPage, evidenceDir), "Mi Negocio menu"));
			report.put("Agregar Negocio modal",
					runStep(() -> stepValidateAgregarNegocioModal(appPage, evidenceDir), "Agregar Negocio modal"));
			report.put("Administrar Negocios view",
					runStep(() -> stepOpenAdministrarNegocios(appPage, evidenceDir), "Administrar Negocios view"));
			report.put("Información General",
					runStep(() -> stepValidateInformacionGeneral(appPage), "Información General"));
			report.put("Detalles de la Cuenta",
					runStep(() -> stepValidateDetallesCuenta(appPage), "Detalles de la Cuenta"));
			report.put("Tus Negocios", runStep(() -> stepValidateTusNegocios(appPage), "Tus Negocios"));

			final LegalStepResult termsResult = runLegalStep(appPage, context, "Términos y Condiciones",
					Pattern.compile("Términos\\s+y\\s+Condiciones", Pattern.CASE_INSENSITIVE), "08-terminos.png",
					evidenceDir);
			report.put("Términos y Condiciones", termsResult.passed());
			termsUrl = termsResult.finalUrl();

			final LegalStepResult privacyResult = runLegalStep(appPage, context, "Política de Privacidad",
					Pattern.compile("Política\\s+de\\s+Privacidad", Pattern.CASE_INSENSITIVE), "09-privacidad.png",
					evidenceDir);
			report.put("Política de Privacidad", privacyResult.passed());
			privacyUrl = privacyResult.finalUrl();
		}

		printFinalReport(report, termsUrl, privacyUrl, evidenceDir);
		assertAllPassed(report);
	}

	private void stepLoginWithGoogle(final Page appPage, final BrowserContext context, final Path evidenceDir) {
		clickByText(appPage, "Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");

		selectGoogleAccountIfVisible(context);
		waitForUiLoad(appPage);

		assertVisible(appPage, Pattern.compile("(dashboard|panel|inicio|home)", Pattern.CASE_INSENSITIVE),
				"Main application interface was not detected after login.");
		assertTrue("Left sidebar navigation should be visible after login.", isSidebarVisible(appPage));
		captureScreenshot(appPage, evidenceDir.resolve("01-dashboard.png"), true);
	}

	private void stepOpenMiNegocioMenu(final Page appPage, final Path evidenceDir) {
		clickByText(appPage, "Mi Negocio");
		assertVisible(appPage, "Agregar Negocio", "Expected 'Agregar Negocio' menu entry to be visible.");
		assertVisible(appPage, "Administrar Negocios", "Expected 'Administrar Negocios' menu entry to be visible.");
		captureScreenshot(appPage, evidenceDir.resolve("02-mi-negocio-menu.png"), false);
	}

	private void stepValidateAgregarNegocioModal(final Page appPage, final Path evidenceDir) {
		clickByText(appPage, "Agregar Negocio");

		assertVisible(appPage, "Crear Nuevo Negocio", "Modal title 'Crear Nuevo Negocio' is missing.");
		assertVisible(appPage, "Nombre del Negocio", "Input label 'Nombre del Negocio' is missing.");
		assertVisible(appPage, BUSINESS_LIMIT_PATTERN, "Business limit text is missing.");
		assertVisible(appPage, "Cancelar", "Button 'Cancelar' is missing.");
		assertVisible(appPage, "Crear Negocio", "Button 'Crear Negocio' is missing.");

		final Locator input = appPage.getByLabel("Nombre del Negocio").first();
		if (input.count() > 0) {
			input.click();
			input.fill("Negocio Prueba Automatización");
			waitForUiLoad(appPage);
		}
		clickByText(appPage, "Cancelar");
		captureScreenshot(appPage, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);
	}

	private void stepOpenAdministrarNegocios(final Page appPage, final Path evidenceDir) {
		ensureTextVisible(appPage, "Administrar Negocios", "Mi Negocio");
		clickByText(appPage, "Administrar Negocios");

		assertVisible(appPage, "Información General", "Section 'Información General' is missing.");
		assertVisible(appPage, "Detalles de la Cuenta", "Section 'Detalles de la Cuenta' is missing.");
		assertVisible(appPage, "Tus Negocios", "Section 'Tus Negocios' is missing.");
		assertVisible(appPage, "Sección Legal", "Section 'Sección Legal' is missing.");
		captureScreenshot(appPage, evidenceDir.resolve("04-administrar-negocios.png"), true);
	}

	private void stepValidateInformacionGeneral(final Page appPage) {
		assertVisible(appPage, Pattern.compile(".+@.+\\..+"), "User email is not visible.");
		assertVisible(appPage, Pattern.compile("BUSINESS\\s+PLAN", Pattern.CASE_INSENSITIVE),
				"'BUSINESS PLAN' text is not visible.");
		assertVisible(appPage, "Cambiar Plan", "Button 'Cambiar Plan' is not visible.");
	}

	private void stepValidateDetallesCuenta(final Page appPage) {
		assertVisible(appPage, "Cuenta creada", "'Cuenta creada' is not visible.");
		assertVisible(appPage, "Estado activo", "'Estado activo' is not visible.");
		assertVisible(appPage, "Idioma seleccionado", "'Idioma seleccionado' is not visible.");
	}

	private void stepValidateTusNegocios(final Page appPage) {
		assertVisible(appPage, "Tus Negocios", "Business list section is not visible.");
		assertVisible(appPage, "Agregar Negocio", "Button 'Agregar Negocio' in businesses section is not visible.");
		assertVisible(appPage, BUSINESS_LIMIT_PATTERN, "Business limit text is not visible in 'Tus Negocios'.");
	}

	private LegalStepResult runLegalStep(final Page appPage, final BrowserContext context, final String linkText,
			final Pattern headingPattern, final String screenshotName, final Path evidenceDir) {
		return runStepWithResult(() -> {
			ensureTextVisible(appPage, linkText, "Sección Legal");
			final Locator link = findVisibleText(appPage, linkText)
					.orElseThrow(() -> new AssertionError("Could not find legal link: " + linkText));

			Page legalPage = null;
			try {
				legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(7000),
						() -> clickAndWait(appPage, link));
			} catch (final PlaywrightException ignored) {
				clickAndWait(appPage, link);
			}

			final Page target = legalPage != null ? legalPage : appPage;
			waitForUiLoad(target);
			assertVisible(target, headingPattern, "Legal heading is missing for " + linkText + ".");
			final String legalText = target.locator("body").first().innerText();
			assertTrue("Legal content text should be visible for " + linkText + ".", legalText != null && legalText.length() > 150);
			captureScreenshot(target, evidenceDir.resolve(screenshotName), true);

			final String finalUrl = target.url();

			if (legalPage != null) {
				legalPage.close();
				appPage.bringToFront();
			} else {
				appPage.goBack();
				waitForUiLoad(appPage);
			}

			return new LegalStepResult(true, finalUrl);
		}, linkText);
	}

	private boolean runStep(final Runnable action, final String stepName) {
		try {
			action.run();
			return true;
		} catch (final Throwable throwable) {
			System.err.println("Step failed [" + stepName + "]: " + throwable.getMessage());
			return false;
		}
	}

	private LegalStepResult runStepWithResult(final StepWithResult action, final String stepName) {
		try {
			return action.run();
		} catch (final Throwable throwable) {
			System.err.println("Step failed [" + stepName + "]: " + throwable.getMessage());
			return new LegalStepResult(false, "N/A");
		}
	}

	private void selectGoogleAccountIfVisible(final BrowserContext context) {
		final Page activeGooglePage = context.pages().stream().filter(this::isGoogleSigninPage).findFirst().orElse(null);
		if (activeGooglePage == null) {
			return;
		}

		final Locator accountOption = activeGooglePage.getByText("juanlucasbarbiergarzon@gmail.com").first();
		if (accountOption.count() > 0) {
			clickAndWait(activeGooglePage, accountOption);
		}
	}

	private boolean isGoogleSigninPage(final Page page) {
		final String url = page.url();
		return url != null && (url.contains("accounts.google.com") || url.contains("google.com/signin"));
	}

	private void clickByText(final Page page, final String... candidates) {
		for (final String candidate : candidates) {
			final java.util.Optional<Locator> locator = findVisibleText(page, candidate);
			if (locator.isPresent()) {
				clickAndWait(page, locator.get());
				return;
			}
		}
		throw new AssertionError("Could not click any visible element with text options: " + String.join(", ", candidates));
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUiLoad(page);
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			// some pages keep long-polling connections open
		}
		page.waitForTimeout(500);
	}

	private void ensureTextVisible(final Page page, final String requiredText, final String fallbackClickText) {
		if (findVisibleText(page, requiredText).isPresent()) {
			return;
		}
		clickByText(page, fallbackClickText);
		assertVisible(page, requiredText, "Expected text '" + requiredText + "' after clicking '" + fallbackClickText + "'.");
	}

	private java.util.Optional<Locator> findVisibleText(final Page page, final String text) {
		final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
		if (exact.count() > 0 && exact.isVisible()) {
			return java.util.Optional.of(exact);
		}

		final Pattern containsPattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
		final Locator contains = page.getByText(containsPattern).first();
		if (contains.count() > 0 && contains.isVisible()) {
			return java.util.Optional.of(contains);
		}
		return java.util.Optional.empty();
	}

	private void assertVisible(final Page page, final String text, final String message) {
		final java.util.Optional<Locator> locator = findVisibleText(page, text);
		if (locator.isPresent()) {
			return;
		}
		throw new AssertionError(message);
	}

	private void assertVisible(final Page page, final Pattern pattern, final String message) {
		final Locator locator = page.getByText(pattern).first();
		if (locator.count() > 0 && locator.isVisible()) {
			return;
		}
		throw new AssertionError(message);
	}

	private boolean isSidebarVisible(final Page page) {
		final Locator sidebar = page.locator("aside, nav, [role='navigation']").first();
		return sidebar.count() > 0 && sidebar.isVisible();
	}

	private void captureScreenshot(final Page page, final Path path, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path evidenceDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private boolean parseBooleanEnv(final String name, final boolean defaultValue) {
		final String value = System.getenv(name);
		return value == null ? defaultValue : Boolean.parseBoolean(value);
	}

	private void printFinalReport(final Map<String, Boolean> report, final String termsUrl, final String privacyUrl,
			final Path evidenceDir) {
		System.out.println("=== saleads_mi_negocio_full_test FINAL REPORT ===");
		for (final String field : REPORT_FIELDS) {
			final boolean passed = report.getOrDefault(field, false);
			System.out.println(field + ": " + (passed ? "PASS" : "FAIL"));
		}
		System.out.println("Términos y Condiciones URL: " + termsUrl);
		System.out.println("Política de Privacidad URL: " + privacyUrl);
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private void assertAllPassed(final Map<String, Boolean> report) {
		final List<String> failedSteps = REPORT_FIELDS.stream().filter(field -> !report.getOrDefault(field, false))
				.collect(Collectors.toList());
		assertTrue("Some validation steps failed: " + failedSteps, failedSteps.isEmpty());
	}

	@FunctionalInterface
	private interface StepWithResult {
		LegalStepResult run();
	}

	private record LegalStepResult(boolean passed, String finalUrl) {
	}
}
