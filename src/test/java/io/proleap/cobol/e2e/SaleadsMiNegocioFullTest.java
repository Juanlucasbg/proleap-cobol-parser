package io.proleap.cobol.e2e;

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
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final Path ARTIFACTS_DIR = Paths.get("target", "saleads-mi-negocio-artifacts");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
	private static final Pattern GOOGLE_LOGIN_PATTERN = Pattern.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*mi negocio\\s*$");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*agregar negocio\\s*$");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)^\\s*administrar negocios\\s*$");

	private interface StepAction {
		void run() throws Exception;
	}

	@Test
	public void saleads_mi_negocio_full_test() throws Exception {
		Assume.assumeTrue("Set SALEADS_RUN_E2E=true to execute this external E2E flow.",
				Boolean.parseBoolean(env("SALEADS_RUN_E2E", "false")));

		final String loginUrl = requiredEnv("SALEADS_LOGIN_URL");
		final LinkedHashMap<String, String> stepResults = new LinkedHashMap<>();
		final LinkedHashMap<String, String> stepDetails = new LinkedHashMap<>();
		final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

		Files.createDirectories(ARTIFACTS_DIR);

		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
					.setHeadless(Boolean.parseBoolean(env("SALEADS_HEADLESS", "true")));
			final Browser browser = playwright.chromium().launch(launchOptions);
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
			final Page page = context.newPage();

			page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(page);

			runStep("Login", stepResults, stepDetails, () -> loginWithGoogle(page));
			runStep("Mi Negocio menu", stepResults, stepDetails, () -> openMiNegocioMenu(page));
			runStep("Agregar Negocio modal", stepResults, stepDetails, () -> validateAgregarNegocioModal(page));
			runStep("Administrar Negocios view", stepResults, stepDetails, () -> openAdministrarNegocios(page));
			runStep("Información General", stepResults, stepDetails, () -> validateInformacionGeneral(page));
			runStep("Detalles de la Cuenta", stepResults, stepDetails, () -> validateDetallesDeLaCuenta(page));
			runStep("Tus Negocios", stepResults, stepDetails, () -> validateTusNegocios(page));
			runStep("Términos y Condiciones", stepResults, stepDetails, () -> {
				final String finalUrl = validateLegalDocument(page, "Términos y Condiciones",
						Pattern.compile("(?i)t[eé]rminos y condiciones"), "step08_terminos_y_condiciones.png");
				legalUrls.put("Términos y Condiciones", finalUrl);
			});
			runStep("Política de Privacidad", stepResults, stepDetails, () -> {
				final String finalUrl = validateLegalDocument(page, "Política de Privacidad",
						Pattern.compile("(?i)pol[ií]tica de privacidad"), "step09_politica_de_privacidad.png");
				legalUrls.put("Política de Privacidad", finalUrl);
			});

			writeFinalReport(stepResults, stepDetails, legalUrls);
			assertNoFailures(stepResults, stepDetails);
		}
	}

	private void loginWithGoogle(final Page page) throws IOException {
		final Locator loginButton = firstVisible(page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_PATTERN)),
				page.getByText(GOOGLE_LOGIN_PATTERN));
		waitForVisible(loginButton, "Google login button");

		Page googlePage = null;
		try {
			googlePage = page.waitForPopup(() -> clickAndWait(page, loginButton),
					new Page.WaitForPopupOptions().setTimeout(7000));
		} catch (PlaywrightException ignored) {
			waitForUi(page);
		}

		if (googlePage != null) {
			waitForUi(googlePage);
			clickIfVisible(googlePage,
					googlePage.getByText("juanlucasbarbiergarzon@gmail.com", new Page.GetByTextOptions().setExact(true)));
		} else {
			clickIfVisible(page,
					page.getByText("juanlucasbarbiergarzon@gmail.com", new Page.GetByTextOptions().setExact(true)));
		}

		page.bringToFront();
		waitForUi(page);

		final Locator sidebar = firstVisible(page.locator("aside"), page.getByText(Pattern.compile("(?i)negocio")));
		waitForVisible(sidebar, "Main app sidebar");
		screenshot(page, "step01_dashboard_loaded.png", false);
	}

	private void openMiNegocioMenu(final Page page) throws IOException {
		final Locator sidebar = firstVisible(page.locator("aside"), page.locator("nav"));
		waitForVisible(sidebar, "Left sidebar");
		waitForVisible(page.getByText(Pattern.compile("(?i)^\\s*negocio\\s*$")), "Negocio section");

		final Locator miNegocio = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
				page.getByText(MI_NEGOCIO_PATTERN));
		clickAndWait(page, miNegocio);

		waitForVisible(page.getByText(AGREGAR_NEGOCIO_PATTERN), "Agregar Negocio menu option");
		waitForVisible(page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN), "Administrar Negocios menu option");
		screenshot(page, "step02_mi_negocio_menu_expanded.png", false);
	}

	private void validateAgregarNegocioModal(final Page page) throws IOException {
		clickAndWait(page, page.getByText(AGREGAR_NEGOCIO_PATTERN));

		final Locator modalTitle = page.getByText(Pattern.compile("(?i)^\\s*crear nuevo negocio\\s*$"));
		waitForVisible(modalTitle, "Crear Nuevo Negocio modal title");
		waitForVisible(firstVisible(
				page.getByLabel(Pattern.compile("(?i)nombre del negocio")),
				page.getByPlaceholder(Pattern.compile("(?i)nombre del negocio")),
				page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)nombre del negocio")))),
				"Nombre del Negocio input");
		waitForVisible(page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")),
				"Business quota text");
		waitForVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*cancelar\\s*$"))),
				"Cancelar button");
		waitForVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*crear negocio\\s*$"))),
				"Crear Negocio button");

		screenshot(page, "step03_agregar_negocio_modal.png", false);

		final Locator businessNameInput = firstVisible(
				page.getByLabel(Pattern.compile("(?i)nombre del negocio")),
				page.getByPlaceholder(Pattern.compile("(?i)nombre del negocio")),
				page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)nombre del negocio"))));
		businessNameInput.click();
		businessNameInput.fill("Negocio Prueba Automatización");
		clickAndWait(page, page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*cancelar\\s*$"))));
	}

	private void openAdministrarNegocios(final Page page) throws IOException {
		ensureMiNegocioExpanded(page);
		clickAndWait(page, page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN));

		waitForVisible(page.getByText(Pattern.compile("(?i)^\\s*informaci[oó]n general\\s*$")), "Información General section");
		waitForVisible(page.getByText(Pattern.compile("(?i)^\\s*detalles de la cuenta\\s*$")), "Detalles de la Cuenta section");
		waitForVisible(page.getByText(Pattern.compile("(?i)^\\s*tus negocios\\s*$")), "Tus Negocios section");
		waitForVisible(page.getByText(Pattern.compile("(?i)(secci[oó]n legal|t[eé]rminos y condiciones|pol[ií]tica de privacidad)")),
				"Sección Legal");

		screenshot(page, "step04_administrar_negocios_view.png", true);
	}

	private void validateInformacionGeneral(final Page page) {
		final Locator infoSectionTitle = page.getByText(Pattern.compile("(?i)^\\s*informaci[oó]n general\\s*$"));
		waitForVisible(infoSectionTitle, "Información General title");

		waitForVisible(page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first(), "User email");
		waitForVisible(page.getByText(Pattern.compile("(?i)business\\s*plan")), "BUSINESS PLAN text");
		waitForVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*cambiar plan\\s*$"))),
				"Cambiar Plan button");

		final String bodyText = page.locator("body").innerText();
		if (!EMAIL_PATTERN.matcher(bodyText).find()) {
			throw new AssertionError("No visible user email was found.");
		}

		final boolean hasNameSignal = containsAny(bodyText.toLowerCase(),
				"nombre", "usuario", "perfil", "cuenta");
		if (!hasNameSignal) {
			throw new AssertionError("No visible user name signal was found in Información General.");
		}
	}

	private void validateDetallesDeLaCuenta(final Page page) {
		waitForVisible(page.getByText(Pattern.compile("(?i)cuenta creada")), "'Cuenta creada' text");
		waitForVisible(page.getByText(Pattern.compile("(?i)estado activo")), "'Estado activo' text");
		waitForVisible(page.getByText(Pattern.compile("(?i)idioma seleccionado")), "'Idioma seleccionado' text");
	}

	private void validateTusNegocios(final Page page) {
		waitForVisible(page.getByText(Pattern.compile("(?i)^\\s*tus negocios\\s*$")), "Tus Negocios title");
		waitForVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
				"'Agregar Negocio' button");
		waitForVisible(page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")),
				"'Tienes 2 de 3 negocios' text");
	}

	private String validateLegalDocument(final Page appPage, final String linkText, final Pattern expectedHeading,
			final String screenshotName) throws IOException {
		Page legalPage = null;
		boolean openedInPopup = false;

		try {
			legalPage = appPage.waitForPopup(() -> clickAndWait(appPage, appPage.getByText(linkText)),
					new Page.WaitForPopupOptions().setTimeout(6000));
			openedInPopup = true;
		} catch (PlaywrightException ignored) {
			clickAndWait(appPage, appPage.getByText(linkText));
			legalPage = appPage;
		}

		waitForUi(legalPage);
		waitForVisible(firstVisible(
						legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(expectedHeading)),
						legalPage.getByText(expectedHeading)),
				linkText + " heading");

		final String legalText = legalPage.locator("body").innerText();
		if (legalText == null || legalText.trim().length() < 120) {
			throw new AssertionError("Legal content text is too short or not visible for " + linkText + ".");
		}

		screenshot(legalPage, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (openedInPopup) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				appPage.goBack();
			} catch (PlaywrightException ignored) {
				appPage.navigate(requiredEnv("SALEADS_LOGIN_URL"),
						new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			}
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void runStep(final String stepName, final LinkedHashMap<String, String> stepResults,
			final LinkedHashMap<String, String> stepDetails, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, "PASS");
			stepDetails.put(stepName, "Validated successfully.");
		} catch (Exception error) {
			stepResults.put(stepName, "FAIL");
			stepDetails.put(stepName, sanitize(error.getMessage()));
		}
	}

	private void writeFinalReport(final LinkedHashMap<String, String> stepResults,
			final LinkedHashMap<String, String> stepDetails, final LinkedHashMap<String, String> legalUrls) throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("{\n");
		report.append("  \"test\": \"saleads_mi_negocio_full_test\",\n");
		report.append("  \"executedAt\": \"").append(Instant.now()).append("\",\n");
		report.append("  \"results\": {\n");

		int index = 0;
		for (Map.Entry<String, String> entry : stepResults.entrySet()) {
			final String field = entry.getKey();
			final String status = entry.getValue();
			final String detail = stepDetails.get(field);

			report.append("    \"").append(escape(field)).append("\": {\n");
			report.append("      \"status\": \"").append(escape(status)).append("\",\n");
			report.append("      \"detail\": \"").append(escape(detail)).append("\"\n");
			report.append("    }");
			index++;
			report.append(index < stepResults.size() ? ",\n" : "\n");
		}

		report.append("  },\n");
		report.append("  \"legalUrls\": {\n");
		int legalIndex = 0;
		for (Map.Entry<String, String> legalEntry : legalUrls.entrySet()) {
			report.append("    \"").append(escape(legalEntry.getKey())).append("\": \"")
					.append(escape(legalEntry.getValue())).append("\"");
			legalIndex++;
			report.append(legalIndex < legalUrls.size() ? ",\n" : "\n");
		}
		report.append("  }\n");
		report.append("}\n");

		Files.writeString(ARTIFACTS_DIR.resolve("final-report.json"), report.toString(), StandardCharsets.UTF_8);
	}

	private void assertNoFailures(final LinkedHashMap<String, String> stepResults,
			final LinkedHashMap<String, String> stepDetails) {
		final StringBuilder failures = new StringBuilder();
		for (Map.Entry<String, String> entry : stepResults.entrySet()) {
			if ("FAIL".equals(entry.getValue())) {
				failures.append(entry.getKey())
						.append(": ")
						.append(stepDetails.getOrDefault(entry.getKey(), "unknown error"))
						.append(System.lineSeparator());
			}
		}

		if (failures.length() > 0) {
			Assert.fail("saleads_mi_negocio_full_test failures:\n" + failures);
		}
	}

	private void ensureMiNegocioExpanded(final Page page) {
		if (page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN).count() > 0 && page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN).first().isVisible()) {
			return;
		}

		final Locator miNegocio = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
				page.getByText(MI_NEGOCIO_PATTERN));
		clickAndWait(page, miNegocio);
		waitForVisible(page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN), "Administrar Negocios option after expanding");
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click();
		waitForUi(page);
	}

	private void clickIfVisible(final Page page, final Locator locator) {
		if (locator.count() > 0 && locator.first().isVisible()) {
			clickAndWait(page, locator.first());
		}
	}

	private Locator firstVisible(final Locator... candidates) {
		for (Locator candidate : candidates) {
			try {
				if (candidate.count() > 0 && candidate.first().isVisible()) {
					return candidate.first();
				}
			} catch (PlaywrightException ignored) {
			}
		}

		for (Locator candidate : candidates) {
			if (candidate.count() > 0) {
				return candidate.first();
			}
		}

		throw new AssertionError("None of the candidate locators matched.");
	}

	private void waitForVisible(final Locator locator, final String description) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
		} catch (PlaywrightException error) {
			throw new AssertionError("Expected visible element not found: " + description, error);
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000));
		} catch (PlaywrightException ignored) {
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
		}

		page.waitForTimeout(500);
	}

	private void screenshot(final Page page, final String filename, final boolean fullPage) throws IOException {
		Files.createDirectories(ARTIFACTS_DIR);
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(ARTIFACTS_DIR.resolve(filename))
				.setFullPage(fullPage));
	}

	private String requiredEnv(final String key) {
		final String value = env(key, "");
		if (value.isBlank()) {
			throw new IllegalArgumentException("Missing required environment variable: " + key);
		}
		return value;
	}

	private static String env(final String key, final String fallback) {
		final String value = System.getenv(key);
		return value == null ? fallback : value;
	}

	private static boolean containsAny(final String text, final String... terms) {
		for (String term : terms) {
			if (text.contains(term)) {
				return true;
			}
		}
		return false;
	}

	private static String sanitize(final String value) {
		if (value == null || value.isBlank()) {
			return "No details available";
		}
		return value.replace("\n", " ").replace("\r", " ").trim();
	}

	private static String escape(final String value) {
		final String safe = value == null ? "" : value;
		return safe
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r");
	}
}
