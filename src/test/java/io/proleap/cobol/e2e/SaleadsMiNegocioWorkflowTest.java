package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SaleadsMiNegocioWorkflowTest {

	private static final String RUN_FLAG_ENV = "SALEADS_RUN_UI_TEST";
	private static final String START_URL_ENV = "SALEADS_START_URL";
	private static final String CDP_URL_ENV = "SALEADS_CDP_URL";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String GOOGLE_EMAIL_ENV = "SALEADS_GOOGLE_EMAIL";

	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final int DEFAULT_TIMEOUT_MS = 15000;
	private static final int SHORT_TIMEOUT_MS = 3000;

	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-evidence");
	private static final Path REPORT_PATH = EVIDENCE_DIR.resolve("final-report.json");

	private final LinkedHashMap<String, StepStatus> reportByStep = createReportSkeleton();
	private final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		Assume.assumeTrue(
				"This test is disabled by default. Set SALEADS_RUN_UI_TEST=true to enable it.",
				Boolean.parseBoolean(envOrDefault(RUN_FLAG_ENV, "false")));

		Files.createDirectories(EVIDENCE_DIR);

		final String startUrl = envOrDefault(START_URL_ENV, "").trim();
		final String cdpUrl = envOrDefault(CDP_URL_ENV, "").trim();
		final String googleEmail = envOrDefault(GOOGLE_EMAIL_ENV, DEFAULT_GOOGLE_EMAIL).trim();
		final boolean headless = Boolean.parseBoolean(envOrDefault(HEADLESS_ENV, "true"));

		Page appPage = null;

		try (Playwright playwright = Playwright.create()) {
			final Browser browser;
			final BrowserContext context;

			if (!cdpUrl.isEmpty()) {
				browser = playwright.chromium().connectOverCDP(cdpUrl);
				context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
				appPage = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
			} else {
				Assert.assertFalse("SALEADS_START_URL must be set when SALEADS_CDP_URL is not provided.", startUrl.isEmpty());

				browser = playwright.chromium()
						.launch(new BrowserType.LaunchOptions().setHeadless(headless));
				context = browser.newContext();
				appPage = context.newPage();
				appPage.navigate(startUrl);
			}

			appPage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
			waitForUiLoad(appPage);

			runLoginStep(appPage, googleEmail);
			runMiNegocioMenuStep(appPage);
			runAgregarNegocioModalStep(appPage);
			runAdministrarNegociosStep(appPage);
			runInformacionGeneralStep(appPage);
			runDetallesCuentaStep(appPage);
			runTusNegociosStep(appPage);
			runLegalStep(
					appPage,
					"Términos y Condiciones",
					List.of("Términos y Condiciones", "Terminos y Condiciones"),
					List.of("Términos y Condiciones", "Terminos y Condiciones"),
					"05-terminos-y-condiciones.png");
			runLegalStep(
					appPage,
					"Política de Privacidad",
					List.of("Política de Privacidad", "Politica de Privacidad"),
					List.of("Política de Privacidad", "Politica de Privacidad"),
					"06-politica-de-privacidad.png");
		} finally {
			writeFinalReport();
		}

		assertAllReportStepsPassed();
	}

	private void runLoginStep(final Page appPage, final String googleEmail) {
		executeStep("Login", () -> {
			PopupInteraction popup = clickWithPopupSupport(
					appPage,
					List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"));
			Page activePage = popup.page;
			activePage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
			waitForUiLoad(activePage);

			boolean accountChooserVisible = isAnyTextVisible(activePage, List.of("Choose an account", "Elige una cuenta"), SHORT_TIMEOUT_MS);
			if (accountChooserVisible) {
				clickByAnyText(activePage, List.of(googleEmail), false);
				waitForUiLoad(activePage);
			}

			if (popup.openedNewTab) {
				for (int attempt = 0; attempt < 30 && !activePage.isClosed(); attempt++) {
					activePage.waitForTimeout(500);
				}
				appPage.bringToFront();
			}

			waitForUiLoad(appPage);
			assertAnyVisibleText(appPage, List.of("Negocio", "Mi Negocio"));
			captureScreenshot(appPage, "01-dashboard-loaded.png", false);
		});
	}

	private void runMiNegocioMenuStep(final Page appPage) {
		executeStep("Mi Negocio menu", () -> {
			assertAnyVisibleText(appPage, List.of("Negocio", "Mi Negocio"));
			clickByAnyText(appPage, List.of("Mi Negocio"), true);
			assertVisibleText(appPage, "Agregar Negocio");
			assertVisibleText(appPage, "Administrar Negocios");
			captureScreenshot(appPage, "02-mi-negocio-menu-expanded.png", false);
		});
	}

	private void runAgregarNegocioModalStep(final Page appPage) {
		executeStep("Agregar Negocio modal", () -> {
			clickByAnyText(appPage, List.of("Agregar Negocio"), true);
			assertVisibleText(appPage, "Crear Nuevo Negocio");
			assertVisibleText(appPage, "Nombre del Negocio");
			assertVisibleText(appPage, "Tienes 2 de 3 negocios");
			assertVisibleText(appPage, "Cancelar");
			assertVisibleText(appPage, "Crear Negocio");
			captureScreenshot(appPage, "03-agregar-negocio-modal.png", false);

			Locator businessNameInput = findBusinessNameInput(appPage);
			businessNameInput.click();
			businessNameInput.fill("Negocio Prueba Automatización");

			clickByAnyText(appPage, List.of("Cancelar"), true);
		});
	}

	private void runAdministrarNegociosStep(final Page appPage) {
		executeStep("Administrar Negocios view", () -> {
			if (!isTextVisible(appPage, "Administrar Negocios", SHORT_TIMEOUT_MS)) {
				clickByAnyText(appPage, List.of("Mi Negocio"), true);
			}

			clickByAnyText(appPage, List.of("Administrar Negocios"), true);

			assertVisibleText(appPage, "Información General");
			assertVisibleText(appPage, "Detalles de la Cuenta");
			assertVisibleText(appPage, "Tus Negocios");
			assertAnyVisibleText(appPage, List.of("Sección Legal", "Seccion Legal"));

			captureScreenshot(appPage, "04-administrar-negocios-full.png", true);
		});
	}

	private void runInformacionGeneralStep(final Page appPage) {
		executeStep("Información General", () -> {
			assertVisibleText(appPage, "BUSINESS PLAN");
			assertVisibleText(appPage, "Cambiar Plan");
			assertUserAndEmailVisible(appPage);
		});
	}

	private void runDetallesCuentaStep(final Page appPage) {
		executeStep("Detalles de la Cuenta", () -> {
			assertVisibleText(appPage, "Cuenta creada");
			assertAnyVisibleText(appPage, List.of("Estado activo", "Activo"));
			assertAnyVisibleText(appPage, List.of("Idioma seleccionado", "Idioma"));
		});
	}

	private void runTusNegociosStep(final Page appPage) {
		executeStep("Tus Negocios", () -> {
			assertVisibleText(appPage, "Tus Negocios");
			assertVisibleText(appPage, "Agregar Negocio");
			assertVisibleText(appPage, "Tienes 2 de 3 negocios");
		});
	}

	private void runLegalStep(
			final Page appPage,
			final String reportField,
			final List<String> linkTexts,
			final List<String> headingTexts,
			final String screenshotName) {
		executeStep(reportField, () -> {
			String appUrlBeforeClick = appPage.url();
			PopupInteraction popup = clickWithPopupSupport(appPage, linkTexts);
			Page legalPage = popup.page;

			waitForUiLoad(legalPage);
			assertAnyVisibleText(legalPage, headingTexts);
			assertLegalBodyTextVisible(legalPage);
			captureScreenshot(legalPage, screenshotName, false);
			legalUrls.put(reportField, legalPage.url());

			if (popup.openedNewTab) {
				if (!legalPage.isClosed()) {
					legalPage.close();
				}
				appPage.bringToFront();
				waitForUiLoad(appPage);
			} else {
				try {
					appPage.goBack();
					waitForUiLoad(appPage);
				} catch (PlaywrightException ignored) {
					appPage.navigate(appUrlBeforeClick);
					waitForUiLoad(appPage);
				}
			}
		});
	}

	private PopupInteraction clickWithPopupSupport(final Page page, final List<String> linkTexts) {
		BrowserContext context = page.context();
		int pagesBeforeClick = context.pages().size();

		clickByAnyText(page, linkTexts, false);

		for (int attempt = 0; attempt < 12; attempt++) {
			if (context.pages().size() > pagesBeforeClick) {
				Page popup = context.pages().get(context.pages().size() - 1);
				popup.bringToFront();
				waitForUiLoad(popup);
				return new PopupInteraction(popup, true);
			}
			page.waitForTimeout(250);
		}

		waitForUiLoad(page);
		return new PopupInteraction(page, false);
	}

	private void clickByAnyText(final Page page, final List<String> textCandidates, final boolean waitAfterClick) {
		PlaywrightException lastError = null;

		for (String text : textCandidates) {
			for (boolean exact : List.of(true, false)) {
				try {
					Locator locator = locateVisibleText(page, text, exact, SHORT_TIMEOUT_MS);
					locator.click();
					if (waitAfterClick) {
						waitForUiLoad(page);
					}
					return;
				} catch (PlaywrightException e) {
					lastError = e;
				}
			}
		}

		throw new AssertionError("Unable to click any of the following text candidates: " + textCandidates, lastError);
	}

	private Locator locateVisibleText(final Page page, final String text, final boolean exact, final int timeoutMs) {
		Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(exact)).first();
		locator.waitFor(new Locator.WaitForOptions()
				.setState(WaitForSelectorState.VISIBLE)
				.setTimeout((double) timeoutMs));
		return locator;
	}

	private boolean isTextVisible(final Page page, final String text, final int timeoutMs) {
		try {
			locateVisibleText(page, text, false, timeoutMs);
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private boolean isAnyTextVisible(final Page page, final List<String> texts, final int timeoutMs) {
		for (String text : texts) {
			if (isTextVisible(page, text, timeoutMs)) {
				return true;
			}
		}
		return false;
	}

	private void assertVisibleText(final Page page, final String text) {
		locateVisibleText(page, text, false, DEFAULT_TIMEOUT_MS);
	}

	private void assertAnyVisibleText(final Page page, final List<String> texts) {
		for (String text : texts) {
			if (isTextVisible(page, text, SHORT_TIMEOUT_MS)) {
				return;
			}
		}
		Assert.fail("None of the expected text fragments was visible: " + texts);
	}

	private Locator findBusinessNameInput(final Page page) {
		Locator byLabel = page.getByLabel("Nombre del Negocio").first();
		if (isLocatorVisible(byLabel, SHORT_TIMEOUT_MS)) {
			return byLabel;
		}

		Locator byPlaceholder = page.getByPlaceholder("Nombre del Negocio").first();
		if (isLocatorVisible(byPlaceholder, SHORT_TIMEOUT_MS)) {
			return byPlaceholder;
		}

		throw new AssertionError("Could not find the 'Nombre del Negocio' input field.");
	}

	private boolean isLocatorVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout((double) timeoutMs));
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void assertUserAndEmailVisible(final Page page) {
		Locator email = page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first();
		email.waitFor(new Locator.WaitForOptions()
				.setState(WaitForSelectorState.VISIBLE)
				.setTimeout((double) DEFAULT_TIMEOUT_MS));

		Locator userName = page.locator(
				"text=/[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}(\\s+[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,})+/").first();
		userName.waitFor(new Locator.WaitForOptions()
				.setState(WaitForSelectorState.VISIBLE)
				.setTimeout((double) DEFAULT_TIMEOUT_MS));
	}

	private void assertLegalBodyTextVisible(final Page legalPage) {
		String bodyText = legalPage.locator("body").innerText();
		Assert.assertNotNull("Legal page body text is not available.", bodyText);
		Assert.assertTrue(
				"Legal page body text appears too short.",
				bodyText.replaceAll("\\s+", " ").trim().length() > 120);
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (PlaywrightException ignored) {
			// Some single page app transitions do not trigger full navigation events.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (PlaywrightException ignored) {
			// Some pages keep long polling requests open.
		}

		page.waitForTimeout(600);
	}

	private void captureScreenshot(final Page page, final String fileName, final boolean fullPage) throws IOException {
		Files.createDirectories(EVIDENCE_DIR);
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(EVIDENCE_DIR.resolve(fileName))
				.setFullPage(fullPage));
	}

	private void executeStep(final String reportField, final ThrowingRunnable runnable) {
		StepStatus status = reportByStep.get(reportField);
		if (status == null) {
			throw new IllegalArgumentException("Unknown report field: " + reportField);
		}

		try {
			runnable.run();
			status.pass = true;
			status.notes.add("Step validations completed successfully.");
		} catch (AssertionError | PlaywrightException | IOException e) {
			status.pass = false;
			status.notes.add("Failure: " + e.getMessage());
		}
	}

	private void writeFinalReport() throws IOException {
		Files.createDirectories(EVIDENCE_DIR);

		StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"generatedAt\": \"").append(escapeJson(Instant.now().toString())).append("\",\n");
		json.append("  \"results\": {\n");

		int index = 0;
		for (Map.Entry<String, StepStatus> entry : reportByStep.entrySet()) {
			json.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(entry.getValue().pass ? "PASS" : "FAIL").append("\"");
			if (++index < reportByStep.size()) {
				json.append(",");
			}
			json.append("\n");
		}
		json.append("  },\n");
		json.append("  \"details\": {\n");

		int detailIndex = 0;
		for (Map.Entry<String, StepStatus> entry : reportByStep.entrySet()) {
			json.append("    \"").append(escapeJson(entry.getKey())).append("\": [");
			for (int i = 0; i < entry.getValue().notes.size(); i++) {
				if (i > 0) {
					json.append(", ");
				}
				json.append("\"").append(escapeJson(entry.getValue().notes.get(i))).append("\"");
			}
			json.append("]");

			if (++detailIndex < reportByStep.size()) {
				json.append(",");
			}
			json.append("\n");
		}
		json.append("  },\n");
		json.append("  \"evidence\": {\n");
		json.append("    \"screenshotsDirectory\": \"").append(escapeJson(EVIDENCE_DIR.toString())).append("\",\n");
		json.append("    \"reportPath\": \"").append(escapeJson(REPORT_PATH.toString())).append("\",\n");
		json.append("    \"terminosYCondicionesUrl\": \"")
				.append(escapeJson(legalUrls.getOrDefault("Términos y Condiciones", ""))).append("\",\n");
		json.append("    \"politicaDePrivacidadUrl\": \"")
				.append(escapeJson(legalUrls.getOrDefault("Política de Privacidad", ""))).append("\"\n");
		json.append("  }\n");
		json.append("}\n");

		Files.writeString(REPORT_PATH, json.toString(), StandardCharsets.UTF_8);
	}

	private void assertAllReportStepsPassed() {
		List<String> failures = new ArrayList<>();
		for (Map.Entry<String, StepStatus> step : reportByStep.entrySet()) {
			if (!step.getValue().pass) {
				failures.add(step.getKey() + " => " + step.getValue().notes);
			}
		}

		if (!failures.isEmpty()) {
			Assert.fail(
					"SaleADS Mi Negocio workflow failed for one or more report fields: "
							+ failures + ". Check evidence report at: " + REPORT_PATH);
		}
	}

	private static LinkedHashMap<String, StepStatus> createReportSkeleton() {
		LinkedHashMap<String, StepStatus> steps = new LinkedHashMap<>();
		steps.put("Login", new StepStatus());
		steps.put("Mi Negocio menu", new StepStatus());
		steps.put("Agregar Negocio modal", new StepStatus());
		steps.put("Administrar Negocios view", new StepStatus());
		steps.put("Información General", new StepStatus());
		steps.put("Detalles de la Cuenta", new StepStatus());
		steps.put("Tus Negocios", new StepStatus());
		steps.put("Términos y Condiciones", new StepStatus());
		steps.put("Política de Privacidad", new StepStatus());
		return steps;
	}

	private static String envOrDefault(final String key, final String fallback) {
		String value = System.getenv(key);
		return value == null ? fallback : value;
	}

	private static String escapeJson(final String input) {
		return input
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r");
	}

	private static final class PopupInteraction {
		private final Page page;
		private final boolean openedNewTab;

		private PopupInteraction(final Page page, final boolean openedNewTab) {
			this.page = page;
			this.openedNewTab = openedNewTab;
		}
	}

	private static final class StepStatus {
		private boolean pass = false;
		private final List<String> notes = new ArrayList<>();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws IOException;
	}
}
