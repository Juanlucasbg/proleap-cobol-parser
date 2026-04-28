package io.proleap.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String ENV_ENTRY_URL = "SALEADS_ENTRY_URL";
	private static final String ENV_BASE_URL = "SALEADS_BASE_URL";
	private static final String ENV_HEADLESS = "SALEADS_HEADLESS";
	private static final String ENV_EXPECTED_USER_NAME = "SALEADS_EXPECTED_USER_NAME";
	private static final long UI_TIMEOUT_MS = 15000L;
	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-evidence");

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String PRIVACIDAD = "Pol\u00edtica de Privacidad";

	@Test
	public void saleadsMiNegocioFullWorkflowTest() throws Exception {
		final Map<String, StepResult> results = initializeResults();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		Files.createDirectories(EVIDENCE_DIR);

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(readBooleanEnv(ENV_HEADLESS, false)));

			try (BrowserContext context = browser.newContext()) {
				final Page page = context.newPage();
				openEntryUrlIfConfigured(page);

				final boolean loginOk = runStep(LOGIN, results, () -> {
					performGoogleLogin(page, context);
					expectAnyVisibleText(page, "Negocio", "Mi Negocio", "Dashboard", "Inicio");
					expectSidebar(page);
					screenshot(page, "01-dashboard-loaded.png", false);
				});

				if (!loginOk) {
					writeFinalReport(results, legalUrls);
					failIfAnyStepFailed(results);
					return;
				}

				runStep(MI_NEGOCIO_MENU, results, () -> {
					openMiNegocioMenu(page);
					expectAnyVisibleText(page, "Agregar Negocio");
					expectAnyVisibleText(page, "Administrar Negocios");
					screenshot(page, "02-mi-negocio-menu-expanded.png", false);
				});

				runStep(AGREGAR_NEGOCIO_MODAL, results, () -> {
					clickText(page, "Agregar Negocio");
					expectAnyVisibleText(page, "Crear Nuevo Negocio");
					expectAnyVisibleText(page, "Nombre del Negocio");
					expectAnyVisibleText(page, "Tienes 2 de 3 negocios");
					expectAnyVisibleText(page, "Cancelar");
					expectAnyVisibleText(page, "Crear Negocio");

					final Locator nameField = firstVisible(
							page.getByLabel(Pattern.compile("(?i)nombre del negocio")),
							page.locator("input[placeholder*='Nombre del Negocio']"),
							page.getByPlaceholder("Nombre del Negocio"),
							page.locator("input[name*='nombre' i]"));
					nameField.click();
					nameField.fill("Negocio Prueba Automatizacion");
					screenshot(page, "03-agregar-negocio-modal.png", false);
					clickText(page, "Cancelar");
				});

				runStep(ADMINISTRAR_NEGOCIOS_VIEW, results, () -> {
					ensureAdminMenuVisible(page);
					clickText(page, "Administrar Negocios");
					expectAnyVisiblePattern(page, Pattern.compile("(?iu)informaci[o\u00f3]n\\s+general"));
					expectAnyVisibleText(page, "Detalles de la Cuenta");
					expectAnyVisibleText(page, "Tus Negocios");
					expectAnyVisiblePattern(page, Pattern.compile("(?iu)secci[o\u00f3]n\\s+legal"));
					screenshot(page, "04-administrar-negocios.png", true);
				});

				runStep(INFORMACION_GENERAL, results, () -> {
					expectAnyVisibleText(page, "BUSINESS PLAN");
					expectAnyVisibleText(page, "Cambiar Plan");
					expectVisibleEmail(page);
					expectNameSignal(page);
				});

				runStep(DETALLES_CUENTA, results, () -> {
					expectAnyVisibleText(page, "Cuenta creada");
					expectAnyVisibleText(page, "Estado activo", "Estado Activo");
					expectAnyVisibleText(page, "Idioma seleccionado");
				});

				runStep(TUS_NEGOCIOS, results, () -> {
					expectAnyVisibleText(page, "Tus Negocios");
					expectAnyVisibleText(page, "Agregar Negocio");
					expectAnyVisibleText(page, "Tienes 2 de 3 negocios");
				});

				runStep(TERMINOS, results, () -> {
					validateLegalLink(page, context, "Terminos y Condiciones", "T\u00e9rminos y Condiciones",
							"08-terminos-condiciones.png", TERMINOS, legalUrls);
				});

				runStep(PRIVACIDAD, results, () -> {
					validateLegalLink(page, context, "Politica de Privacidad", "Pol\u00edtica de Privacidad",
							"09-politica-privacidad.png", PRIVACIDAD, legalUrls);
				});

				writeFinalReport(results, legalUrls);
			}
		}

		failIfAnyStepFailed(results);
	}

	private Map<String, StepResult> initializeResults() {
		final Map<String, StepResult> results = new LinkedHashMap<>();
		results.put(LOGIN, StepResult.pending());
		results.put(MI_NEGOCIO_MENU, StepResult.pending());
		results.put(AGREGAR_NEGOCIO_MODAL, StepResult.pending());
		results.put(ADMINISTRAR_NEGOCIOS_VIEW, StepResult.pending());
		results.put(INFORMACION_GENERAL, StepResult.pending());
		results.put(DETALLES_CUENTA, StepResult.pending());
		results.put(TUS_NEGOCIOS, StepResult.pending());
		results.put(TERMINOS, StepResult.pending());
		results.put(PRIVACIDAD, StepResult.pending());
		return results;
	}

	private boolean runStep(final String stepName, final Map<String, StepResult> results, final CheckedRunnable runnable) {
		try {
			runnable.run();
			results.put(stepName, StepResult.passed());
			return true;
		} catch (final Exception e) {
			final String detail = e.getMessage() == null ? e.getClass().getSimpleName()
					: e.getClass().getSimpleName() + ": " + e.getMessage();
			results.put(stepName, StepResult.failed(detail));
			return false;
		}
	}

	private void openEntryUrlIfConfigured(final Page page) {
		final String entryUrl = firstNonBlank(readEnv(ENV_ENTRY_URL), readEnv(ENV_BASE_URL));
		if (entryUrl != null) {
			page.navigate(entryUrl.trim());
			waitForUiLoad(page);
			return;
		}

		if ("about:blank".equals(page.url())) {
			throw new IllegalStateException("Define SALEADS_ENTRY_URL or SALEADS_BASE_URL for environment-agnostic execution.");
		}
	}

	private void performGoogleLogin(final Page page, final BrowserContext context) {
		final Locator loginButton = firstVisible(page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(sign in|iniciar|continuar).*(google)"))),
				page.getByText(Pattern.compile("(?i)(sign in|iniciar|continuar).*(google)")));
		Page popup = null;
		try {
			popup = context.waitForPage(() -> loginButton.first().click(),
					new BrowserContext.WaitForPageOptions().setTimeout(5000));
		} catch (final TimeoutError ignored) {
			loginButton.first().click();
		}
		waitForUiLoad(page);

		final Page possibleGooglePage = popup != null ? popup : detectActiveGooglePage(page, context);
		if (possibleGooglePage != null) {
			selectGoogleAccountIfVisible(possibleGooglePage);
		}

		page.bringToFront();
		waitForUiLoad(page);
	}

	private Page detectActiveGooglePage(final Page currentPage, final BrowserContext context) {
		waitForUiLoad(currentPage);
		for (final Page candidate : context.pages()) {
			if (!candidate.isClosed() && candidate.url().contains("accounts.google.com")) {
				candidate.bringToFront();
				waitForUiLoad(candidate);
				return candidate;
			}
		}
		if (currentPage.url().contains("accounts.google.com")) {
			return currentPage;
		}
		return null;
	}

	private void selectGoogleAccountIfVisible(final Page googlePage) {
		final Locator accountLocator = googlePage.getByText(GOOGLE_ACCOUNT, new Page.GetByTextOptions().setExact(true));
		if (isVisible(accountLocator, UI_TIMEOUT_MS)) {
			accountLocator.click();
			waitForUiLoad(googlePage);
		}
	}

	private void openMiNegocioMenu(final Page page) {
		if (isAnyTextVisible(page, "Mi Negocio")) {
			clickAnyText(page, "Mi Negocio");
			return;
		}

		final Locator negocio = firstVisible(page.getByText(Pattern.compile("(?i)^negocio$")),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^negocio$"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^negocio$"))));
		clickAndWait(page, negocio);

		final Locator miNegocio = firstVisible(page.getByText(Pattern.compile("(?i)^mi\\s+negocio$")),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Mi Negocio")),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Mi Negocio")));
		clickAndWait(page, miNegocio);
	}

	private void ensureAdminMenuVisible(final Page page) {
		if (!isAnyTextVisible(page, "Administrar Negocios")) {
			openMiNegocioMenu(page);
		}
	}

	private void validateLegalLink(final Page appPage, final BrowserContext context, final String plainLinkText,
			final String accentedLinkText, final String screenshotName, final String urlKey, final Map<String, String> legalUrls) {
		final String originalUrl = appPage.url();
		final int pageCountBefore = context.pages().size();

		clickAnyText(appPage, plainLinkText, accentedLinkText);

		Page legalPage = appPage;
		waitForUiLoad(appPage);
		final List<Page> allPages = context.pages();
		if (allPages.size() > pageCountBefore) {
			legalPage = allPages.get(allPages.size() - 1);
			legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			waitForUiLoad(legalPage);
		}

		expectAnyVisibleText(legalPage, plainLinkText, accentedLinkText);
		expectLegalBodyText(legalPage);
		screenshot(legalPage, screenshotName, true);
		legalUrls.put(urlKey, legalPage.url());

		if (!Objects.equals(legalPage, appPage)) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else if (!Objects.equals(appPage.url(), originalUrl)) {
			appPage.navigate(originalUrl);
			waitForUiLoad(appPage);
		}
	}

	private void expectNameSignal(final Page page) {
		final String expectedName = readEnv(ENV_EXPECTED_USER_NAME);
		if (expectedName != null && !expectedName.isBlank()) {
			expectAnyVisibleText(page, expectedName.trim());
			return;
		}

		final boolean hasProfileLabel = isAnyTextVisible(page, "Nombre", "Usuario", "Perfil");
		final boolean hasPersonLikeName = isVisible(page.getByText(Pattern.compile("(?iu)\\b[\\p{L}]{2,}\\s+[\\p{L}]{2,}\\b")),
				2000L);
		if (!hasProfileLabel && !hasPersonLikeName) {
			throw new IllegalStateException("No se encontro una senal visible del nombre de usuario.");
		}
	}

	private void expectVisibleEmail(final Page page) {
		final Locator emailLocator = page.getByText(
				Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", Pattern.CASE_INSENSITIVE));
		if (!isVisible(emailLocator, UI_TIMEOUT_MS)) {
			throw new IllegalStateException("No se encontro un correo electronico visible.");
		}
	}

	private void expectSidebar(final Page page) {
		final Locator sidebar = firstVisible(page.locator("aside"), page.locator("nav"),
				page.getByRole(AriaRole.NAVIGATION));
		if (!isVisible(sidebar, UI_TIMEOUT_MS)) {
			throw new IllegalStateException("No se detecto la barra lateral de navegacion.");
		}
	}

	private void expectLegalBodyText(final Page page) {
		final String bodyText = page.locator("body").innerText();
		if (bodyText == null || bodyText.trim().length() < 120) {
			throw new IllegalStateException("No se detecto contenido legal en la pagina.");
		}
	}

	private void clickText(final Page page, final String text) {
		clickAnyText(page, text);
	}

	private void clickAnyText(final Page page, final String... textOptions) {
		final List<Locator> candidates = new ArrayList<>();
		for (final String text : textOptions) {
			candidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)));
			candidates.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)));
			candidates.add(page.getByText(text, new Page.GetByTextOptions().setExact(true)));
			candidates.add(page.getByText(text));
		}
		final Locator target = firstVisible(candidates.toArray(new Locator[0]));
		clickAndWait(page, target);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click();
		waitForUiLoad(page);
	}

	private void expectAnyVisibleText(final Page page, final String... texts) {
		if (!isAnyTextVisible(page, texts)) {
			throw new IllegalStateException("No se encontro texto esperado: " + String.join(" | ", texts));
		}
	}

	private void expectAnyVisiblePattern(final Page page, final Pattern... patterns) {
		for (final Pattern pattern : patterns) {
			if (isVisible(page.getByText(pattern), UI_TIMEOUT_MS)) {
				return;
			}
		}
		throw new IllegalStateException("No se encontro texto esperado por patron.");
	}

	private boolean isAnyTextVisible(final Page page, final String... texts) {
		for (final String text : texts) {
			if (isVisible(page.getByText(text, new Page.GetByTextOptions().setExact(true)), UI_TIMEOUT_MS)) {
				return true;
			}
			if (isVisible(page.getByText(text), 1500L)) {
				return true;
			}
		}
		return false;
	}

	private Locator firstVisible(final Locator... locators) {
		for (final Locator locator : locators) {
			if (isVisible(locator, UI_TIMEOUT_MS)) {
				return locator.first();
			}
		}
		throw new IllegalStateException("No se encontro un elemento visible para la accion solicitada.");
	}

	private boolean isVisible(final Locator locator, final long timeoutMs) {
		try {
			locator.first()
					.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final TimeoutError e) {
			return false;
		}
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(UI_TIMEOUT_MS));
		} catch (final Exception ignored) {
			// Not every page reaches network idle consistently (e.g. websocket apps).
		}
		page.waitForTimeout(600);
	}

	private void screenshot(final Page page, final String filename, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(EVIDENCE_DIR.resolve(filename)).setFullPage(fullPage));
	}

	private void writeFinalReport(final Map<String, StepResult> results, final Map<String, String> legalUrls) throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		report.append("generated_at=").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
				.append(System.lineSeparator()).append(System.lineSeparator());

		for (final Map.Entry<String, StepResult> result : results.entrySet()) {
			report.append(result.getKey()).append(": ").append(result.getValue().passed ? "PASS" : "FAIL");
			if (result.getValue().detail != null && !result.getValue().detail.isBlank()) {
				report.append(" - ").append(result.getValue().detail);
			}
			report.append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			report.append(System.lineSeparator()).append("Final URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				report.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		final Path reportPath = EVIDENCE_DIR.resolve("final-report.txt");
		Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
		System.out.println(report);
	}

	private void failIfAnyStepFailed(final Map<String, StepResult> results) {
		final List<String> failed = results.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(entry -> entry.getKey() + " (" + entry.getValue().detail + ")").collect(Collectors.toList());
		assertTrue("SaleADS Mi Negocio workflow failures: " + String.join(", ", failed), failed.isEmpty());
	}

	private String readEnv(final String key) {
		return System.getenv(key);
	}

	private boolean readBooleanEnv(final String key, final boolean defaultValue) {
		final String raw = readEnv(key);
		if (raw == null || raw.isBlank()) {
			return defaultValue;
		}
		return Boolean.parseBoolean(raw);
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}

		private static StepResult pending() {
			return new StepResult(false, "Not executed");
		}

		private static StepResult passed() {
			return new StepResult(true, "");
		}

		private static StepResult failed(final String detail) {
			return new StepResult(false, detail == null ? "Unknown error" : detail);
		}
	}
}
