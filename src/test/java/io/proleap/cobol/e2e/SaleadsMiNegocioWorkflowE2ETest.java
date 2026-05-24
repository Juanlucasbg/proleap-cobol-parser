package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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

public class SaleadsMiNegocioWorkflowE2ETest {

	private static final String ENV_LOGIN_URL = "SALEADS_LOGIN_URL";
	private static final String ENV_GOOGLE_ACCOUNT_EMAIL = "SALEADS_GOOGLE_ACCOUNT_EMAIL";
	private static final String ENV_HEADLESS = "SALEADS_HEADLESS";

	private static final int SHORT_TIMEOUT_MS = 5_000;
	private static final int DEFAULT_TIMEOUT_MS = 30_000;

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> results = new LinkedHashMap<>();
	private final Map<String, String> evidence = new LinkedHashMap<>();

	private Path artifactDirectory;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = Optional.ofNullable(System.getenv(ENV_LOGIN_URL)).map(String::trim).orElse("");
		Assume.assumeTrue("Set " + ENV_LOGIN_URL + " to execute the SaleADS workflow test.", !loginUrl.isEmpty());

		final String googleAccountEmail = Optional.ofNullable(System.getenv(ENV_GOOGLE_ACCOUNT_EMAIL))
				.filter(value -> !value.isBlank()).orElse("juanlucasbarbiergarzon@gmail.com");
		final boolean headless = Boolean.parseBoolean(Optional.ofNullable(System.getenv(ENV_HEADLESS)).orElse("true"));

		artifactDirectory = Files.createDirectories(Paths.get("target", "saleads-e2e-artifacts"));

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setTimeout(DEFAULT_TIMEOUT_MS));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page page = context.newPage();

			page.navigate(loginUrl, new Page.NavigateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			waitForUiLoad(page);

			runStep("Login", () -> loginWithGoogle(page, context, googleAccountEmail));
			runStep("Mi Negocio menu", () -> openMiNegocioMenu(page));
			runStep("Agregar Negocio modal", () -> validateAgregarNegocioModal(page));
			runStep("Administrar Negocios view", () -> openAdministrarNegocios(page));
			runStep("Información General", () -> validateInformacionGeneral(page));
			runStep("Detalles de la Cuenta", () -> validateDetallesCuenta(page));
			runStep("Tus Negocios", () -> validateTusNegocios(page));
			runStep("Términos y Condiciones",
					() -> validateLegalDocument(page, context, "Términos y Condiciones", "Términos y Condiciones", "08-terminos"));
			runStep("Política de Privacidad",
					() -> validateLegalDocument(page, context, "Política de Privacidad", "Política de Privacidad", "09-privacidad"));

			final String finalReport = buildFinalReport();
			Files.writeString(artifactDirectory.resolve("final-report.txt"), finalReport, StandardCharsets.UTF_8);
			System.out.println(finalReport);

			assertAllPassed(finalReport);

			context.close();
			browser.close();
		}
	}

	private void loginWithGoogle(final Page page, final BrowserContext context, final String googleAccountEmail) {
		final Locator loginButton = mustFindVisibleText(page, SHORT_TIMEOUT_MS, "Sign in with Google", "Iniciar sesión con Google",
				"Ingresar con Google", "Continuar con Google", "Login with Google", "Google");

		final Page maybePopup = clickAndCapturePopup(context, page, loginButton);
		if (maybePopup != null && !maybePopup.isClosed()) {
			selectGoogleAccountIfVisible(maybePopup, googleAccountEmail);
		} else {
			selectGoogleAccountIfVisible(page, googleAccountEmail);
		}

		waitForUiLoad(page);
		assertLeftSidebarVisible(page);
		screenshot(page, "01-dashboard-loaded", true);
	}

	private void openMiNegocioMenu(final Page page) {
		clickIfVisible(page, "Negocio");
		clickAndWait(page, mustFindVisibleText(page, SHORT_TIMEOUT_MS, "Mi Negocio"));
		assertVisibleText(page, "Agregar Negocio");
		assertVisibleText(page, "Administrar Negocios");
		screenshot(page, "02-mi-negocio-expanded", false);
	}

	private void validateAgregarNegocioModal(final Page page) {
		ensureMiNegocioExpanded(page);
		clickAndWait(page, mustFindVisibleText(page, SHORT_TIMEOUT_MS, "Agregar Negocio"));

		assertVisibleText(page, "Crear Nuevo Negocio");
		assertVisibleText(page, "Nombre del Negocio");
		assertVisibleText(page, "Tienes 2 de 3 negocios");
		assertVisibleText(page, "Cancelar");
		assertVisibleText(page, "Crear Negocio");
		screenshot(page, "03-agregar-negocio-modal", true);

		final Locator firstInput = page.locator("input").first();
		if (firstInput.isVisible(new Locator.IsVisibleOptions().setTimeout(SHORT_TIMEOUT_MS))) {
			firstInput.click();
			firstInput.fill("Negocio Prueba Automatización");
		}

		clickAndWait(page, mustFindVisibleText(page, SHORT_TIMEOUT_MS, "Cancelar"));
	}

	private void openAdministrarNegocios(final Page page) {
		ensureMiNegocioExpanded(page);
		clickAndWait(page, mustFindVisibleText(page, SHORT_TIMEOUT_MS, "Administrar Negocios"));

		assertVisibleText(page, "Información General");
		assertVisibleText(page, "Detalles de la Cuenta");
		assertVisibleText(page, "Tus Negocios");
		assertVisibleText(page, "Sección Legal");
		screenshot(page, "04-administrar-negocios", true);
	}

	private void validateInformacionGeneral(final Page page) {
		assertVisibleText(page, "Información General");
		assertVisibleText(page, "BUSINESS PLAN");
		assertVisibleText(page, "Cambiar Plan");
		assertEmailVisible(page);
		assertLikelyUserNameVisible(page);
	}

	private void validateDetallesCuenta(final Page page) {
		assertVisibleText(page, "Cuenta creada");
		assertVisibleText(page, "Estado activo");
		assertVisibleText(page, "Idioma seleccionado");
	}

	private void validateTusNegocios(final Page page) {
		assertVisibleText(page, "Tus Negocios");
		assertVisibleText(page, "Agregar Negocio");
		assertVisibleText(page, "Tienes 2 de 3 negocios");
	}

	private void validateLegalDocument(final Page appPage, final BrowserContext context, final String linkText,
			final String expectedHeading, final String screenshotNamePrefix) {
		final String appPageUrlBeforeClick = appPage.url();
		final Locator legalLink = mustFindVisibleText(appPage, SHORT_TIMEOUT_MS, linkText);
		final Page legalPage = clickAndCapturePopup(context, appPage, legalLink);
		final Page targetPage = legalPage == null ? appPage : legalPage;

		waitForUiLoad(targetPage);
		assertVisibleText(targetPage, expectedHeading);

		final String bodyText = Optional.ofNullable(targetPage.textContent("body")).orElse("").trim();
		assertTrue("Legal content text should be visible for " + expectedHeading, bodyText.length() > 200);

		screenshot(targetPage, screenshotNamePrefix + "-page", true);
		evidence.put(linkText, "URL: " + targetPage.url());

		if (legalPage != null && !legalPage.isClosed()) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else {
			try {
				appPage.goBack(new Page.GoBackOptions().setTimeout(DEFAULT_TIMEOUT_MS));
				waitForUiLoad(appPage);
			} catch (final PlaywrightException exception) {
				appPage.navigate(appPageUrlBeforeClick, new Page.NavigateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
				waitForUiLoad(appPage);
			}
		}
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			results.put(stepName, new StepResult(true, "PASS"));
		} catch (final Throwable throwable) {
			final String details = Optional.ofNullable(throwable.getMessage()).orElse(throwable.toString());
			results.put(stepName, new StepResult(false, details));
		}
	}

	private void assertAllPassed(final String finalReport) {
		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.get(field);
			if (result == null || !result.passed) {
				fail("One or more SaleADS workflow validations failed.\n" + finalReport);
			}
		}
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Workflow - Final Report\n");
		builder.append("Artifacts: ").append(artifactDirectory.toAbsolutePath()).append("\n\n");

		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.get(field);
			if (result == null) {
				builder.append("- ").append(field).append(": FAIL | No execution result found\n");
				continue;
			}

			builder.append("- ").append(field).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (evidence.containsKey(field)) {
				builder.append(" | ").append(evidence.get(field));
			}
			if (!result.passed) {
				builder.append(" | ").append(result.details);
			}
			builder.append("\n");
		}

		return builder.toString();
	}

	private void ensureMiNegocioExpanded(final Page page) {
		if (isAnyTextVisible(page, SHORT_TIMEOUT_MS, "Agregar Negocio", "Administrar Negocios")) {
			return;
		}

		clickAndWait(page, mustFindVisibleText(page, SHORT_TIMEOUT_MS, "Mi Negocio"));
	}

	private void assertLeftSidebarVisible(final Page page) {
		if (page.locator("aside").first().isVisible(new Locator.IsVisibleOptions().setTimeout(SHORT_TIMEOUT_MS))) {
			return;
		}

		assertVisibleText(page, "Mi Negocio");
	}

	private void selectGoogleAccountIfVisible(final Page page, final String googleAccountEmail) {
		if (page.isClosed()) {
			return;
		}

		waitForUiLoad(page);

		final Locator accountEmailLocator = firstVisibleText(page, SHORT_TIMEOUT_MS, googleAccountEmail);
		if (accountEmailLocator != null) {
			clickAndWait(page, accountEmailLocator);
		}
	}

	private Page clickAndCapturePopup(final BrowserContext context, final Page page, final Locator locator) {
		try {
			return context.waitForPage(() -> clickAndWait(page, locator),
					new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final PlaywrightException exception) {
			return null;
		}
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUiLoad(page);
	}

	private void clickIfVisible(final Page page, final String text) {
		final Locator locator = firstVisibleText(page, 2_000, text);
		if (locator != null) {
			clickAndWait(page, locator);
		}
	}

	private void assertVisibleText(final Page page, final String text) {
		if (!isAnyTextVisible(page, SHORT_TIMEOUT_MS, text)) {
			fail("Expected text is not visible: " + text);
		}
	}

	private void assertEmailVisible(final Page page) {
		final Locator emailCandidates = page
				.locator("xpath=//*[contains(normalize-space(.), '@') and contains(normalize-space(.), '.')]");
		final int candidateCount = emailCandidates.count();
		for (int i = 0; i < candidateCount; i++) {
			if (emailCandidates.nth(i).isVisible(new Locator.IsVisibleOptions().setTimeout(1_000))) {
				return;
			}
		}

		fail("User email is not visible.");
	}

	private void assertLikelyUserNameVisible(final Page page) {
		final String bodyText = Optional.ofNullable(page.textContent("body")).orElse("");
		for (final String line : bodyText.split("\\R")) {
			final String normalized = line.trim().toLowerCase(Locale.ROOT);
			if (normalized.length() < 5 || normalized.contains("@")) {
				continue;
			}
			if (normalized.contains("información general") || normalized.contains("business plan")
					|| normalized.contains("cambiar plan") || normalized.contains("detalles de la cuenta")
					|| normalized.contains("tus negocios")) {
				continue;
			}
			if (normalized.matches("[a-záéíóúñ]+\\s+[a-záéíóúñ]+.*")) {
				return;
			}
		}

		fail("User name is not visible.");
	}

	private Locator mustFindVisibleText(final Page page, final int timeoutMs, final String... texts) {
		final Locator locator = firstVisibleText(page, timeoutMs, texts);
		if (locator == null) {
			fail("Could not find a visible element with text options: " + String.join(", ", texts));
		}
		return locator;
	}

	private Locator firstVisibleText(final Page page, final int timeoutMs, final String... texts) {
		for (final String text : texts) {
			final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
			if (exact.isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs))) {
				return exact;
			}

			final Locator relaxed = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
			if (relaxed.isVisible(new Locator.IsVisibleOptions().setTimeout(1_000))) {
				return relaxed;
			}
		}

		return null;
	}

	private boolean isAnyTextVisible(final Page page, final int timeoutMs, final String... texts) {
		return firstVisibleText(page, timeoutMs, texts) != null;
	}

	private void waitForUiLoad(final Page page) {
		if (page.isClosed()) {
			return;
		}

		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (final PlaywrightException exception) {
			// no-op
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final PlaywrightException exception) {
			// no-op
		}

		if (!page.isClosed()) {
			page.waitForTimeout(500);
		}
	}

	private void screenshot(final Page page, final String fileNamePrefix, final boolean fullPage) {
		final Path output = artifactDirectory.resolve(fileNamePrefix + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(output).setFullPage(fullPage));
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}
	}
}
