package io.proleap.cobol.e2e.saleads;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
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

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		Assume.assumeTrue("Enable with -Dsaleads.e2e.enabled=true",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

		final Path runDir = Paths.get("target", TEST_NAME, RUN_ID_FORMATTER.format(Instant.now()));
		Files.createDirectories(runDir);

		final Map<String, Boolean> report = initializeReport();
		final Map<String, String> details = new LinkedHashMap<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(buildLaunchOptionsFromProperties());
			final BrowserContext context = browser.newContext(defaultContextOptions());
			final Page page = context.newPage();

			navigateToLoginIfConfigured(page);

			report.put("Login", runStep("Login", details, () -> performLoginAndValidateDashboard(page, runDir)));
			report.put("Mi Negocio menu", runStep("Mi Negocio menu", details, () -> openMiNegocioMenu(page, runDir)));
			report.put("Agregar Negocio modal",
					runStep("Agregar Negocio modal", details, () -> validateAgregarNegocioModal(page, runDir)));
			report.put("Administrar Negocios view",
					runStep("Administrar Negocios view", details, () -> openAdministrarNegocios(page, runDir)));
			report.put("Información General",
					runStep("Información General", details, () -> validateInformacionGeneral(page)));
			report.put("Detalles de la Cuenta",
					runStep("Detalles de la Cuenta", details, () -> validateDetallesCuenta(page)));
			report.put("Tus Negocios", runStep("Tus Negocios", details, () -> validateTusNegocios(page)));
			report.put("Términos y Condiciones", runStep("Términos y Condiciones", details,
					() -> validateLegalDocument(page, context, runDir, legalUrls,
							"Términos y Condiciones",
							Pattern.compile("T[eé]rminos\\s+y\\s+Condiciones", Pattern.CASE_INSENSITIVE),
							Pattern.compile("T[eé]rminos\\s+y\\s+Condiciones", Pattern.CASE_INSENSITIVE),
							"08_terminos_y_condiciones.png")));
			report.put("Política de Privacidad", runStep("Política de Privacidad", details,
					() -> validateLegalDocument(page, context, runDir, legalUrls,
							"Política de Privacidad",
							Pattern.compile("Pol[ií]tica\\s+de\\s+Privacidad", Pattern.CASE_INSENSITIVE),
							Pattern.compile("Pol[ií]tica\\s+de\\s+Privacidad", Pattern.CASE_INSENSITIVE),
							"09_politica_de_privacidad.png")));

			browser.close();
		} finally {
			writeReport(runDir, report, details, legalUrls);
		}

		printSummary(report, details);
		if (report.containsValue(Boolean.FALSE)) {
			Assert.fail("One or more validations failed. Check report at: "
					+ runDir.resolve("final-report.json").toAbsolutePath());
		}
	}

	private boolean runStep(final String stepName, final Map<String, String> details, final StepAction action) {
		try {
			action.run();
			details.put(stepName, "PASS");
			return true;
		} catch (final Throwable t) {
			details.put(stepName, "FAIL: " + safeMessage(t));
			return false;
		}
	}

	private void performLoginAndValidateDashboard(final Page page, final Path runDir) {
		waitForUi(page);

		if (!isAnyVisibleWithTimeout(page, 3000, page.locator("aside"), page.locator("nav"),
				page.getByText(Pattern.compile("Mi\\s+Negocio", Pattern.CASE_INSENSITIVE)))) {
			final Locator loginButton = firstVisibleWithTimeout(15000,
					page.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("Sign\\s*in\\s*with\\s*Google",
									Pattern.CASE_INSENSITIVE))),
					page.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions()
									.setName(Pattern.compile("Iniciar\\s+sesi[oó]n\\s+con\\s+Google",
											Pattern.CASE_INSENSITIVE))),
					page.getByText(Pattern.compile("Google", Pattern.CASE_INSENSITIVE)));

			requireVisible(loginButton, "Google login button was not found");
			clickAndWait(page, loginButton, "Click Google login");

			final Locator accountOption = page
					.getByText("juanlucasbarbiergarzon@gmail.com", new Page.GetByTextOptions().setExact(true));
			if (isVisibleWithTimeout(accountOption, 8000)) {
				clickAndWait(page, accountOption, "Select Google account");
			}
		}

		require(
				isAnyVisibleWithTimeout(page, 30000, page.locator("main"), page.locator("aside"), page.locator("nav"),
						page.getByText(Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE))),
				"Main application interface did not appear after login");
		require(isAnyVisibleWithTimeout(page, 30000, page.locator("aside"), page.locator("nav")),
				"Left sidebar navigation is not visible");

		takeScreenshot(page, runDir.resolve("01_dashboard_loaded.png"), false);
	}

	private void openMiNegocioMenu(final Page page, final Path runDir) {
		final Locator negocioSection = firstVisibleWithTimeout(12000,
				page.getByText(Pattern.compile("^\\s*Negocio\\s*$", Pattern.CASE_INSENSITIVE)),
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("^\\s*Negocio\\s*$", Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("^\\s*Negocio\\s*$", Pattern.CASE_INSENSITIVE))));
		if (negocioSection != null) {
			clickAndWait(page, negocioSection, "Open Negocio section");
		}

		final Locator miNegocioOption = firstVisibleWithTimeout(15000,
				page.getByText(Pattern.compile("^\\s*Mi\\s+Negocio\\s*$", Pattern.CASE_INSENSITIVE)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
						.setName(Pattern.compile("^\\s*Mi\\s+Negocio\\s*$", Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("^\\s*Mi\\s+Negocio\\s*$", Pattern.CASE_INSENSITIVE))));
		requireVisible(miNegocioOption, "'Mi Negocio' option is not visible");
		clickAndWait(page, miNegocioOption, "Expand Mi Negocio");

		require(isAnyVisibleWithTimeout(page, 12000,
				page.getByText(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE)),
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE)))),
				"'Agregar Negocio' is not visible in submenu");
		require(isAnyVisibleWithTimeout(page, 12000,
				page.getByText(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
						.setName(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE)))),
				"'Administrar Negocios' is not visible in submenu");

		takeScreenshot(page, runDir.resolve("02_mi_negocio_menu_expanded.png"), false);
	}

	private void validateAgregarNegocioModal(final Page page, final Path runDir) {
		final Locator agregarNegocio = firstVisibleWithTimeout(12000,
				page.getByText(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE)),
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE))));
		requireVisible(agregarNegocio, "'Agregar Negocio' entry was not found");
		clickAndWait(page, agregarNegocio, "Open Agregar Negocio modal");

		final Locator modalTitle = page
				.getByText(Pattern.compile("Crear\\s+Nuevo\\s+Negocio", Pattern.CASE_INSENSITIVE));
		require(isVisibleWithTimeout(modalTitle, 12000), "Modal title 'Crear Nuevo Negocio' is not visible");

		require(isAnyVisibleWithTimeout(page, 10000,
				page.getByText(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE))),
				"Label text 'Nombre del Negocio' is not visible");
		final Locator nombreField = firstVisibleWithTimeout(10000, page.locator("input[placeholder*='Nombre']"),
				page.locator("input[name*='nombre' i]"), page.locator("input[id*='nombre' i]"),
				page.locator("form input"));
		requireVisible(nombreField, "Input 'Nombre del Negocio' was not found");
		require(
				isAnyVisibleWithTimeout(page, 10000,
						page.getByText(Pattern.compile("Tienes\\s+2\\s+de\\s+3\\s+negocios", Pattern.CASE_INSENSITIVE))),
				"Text 'Tienes 2 de 3 negocios' is not visible");
		require(isAnyVisibleWithTimeout(page, 10000,
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE)))),
				"Button 'Cancelar' is not visible");
		require(isAnyVisibleWithTimeout(page, 10000,
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("Crear\\s+Negocio", Pattern.CASE_INSENSITIVE)))),
				"Button 'Crear Negocio' is not visible");

		takeScreenshot(page, runDir.resolve("03_crear_nuevo_negocio_modal.png"), false);

		nombreField.first().fill("Negocio Prueba Automatización");
		waitForUi(page);
		clickAndWait(page,
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE))),
				"Close Agregar Negocio modal");
	}

	private void openAdministrarNegocios(final Page page, final Path runDir) {
		final Locator miNegocioOption = firstVisibleWithTimeout(12000,
				page.getByText(Pattern.compile("^\\s*Mi\\s+Negocio\\s*$", Pattern.CASE_INSENSITIVE)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
						.setName(Pattern.compile("^\\s*Mi\\s+Negocio\\s*$", Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("^\\s*Mi\\s+Negocio\\s*$", Pattern.CASE_INSENSITIVE))));
		if (miNegocioOption != null) {
			clickAndWait(page, miNegocioOption, "Ensure Mi Negocio submenu expanded");
		}

		final Locator administrarNegocios = firstVisibleWithTimeout(12000,
				page.getByText(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
						.setName(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE))));
		requireVisible(administrarNegocios, "'Administrar Negocios' option was not found");
		clickAndWait(page, administrarNegocios, "Open Administrar Negocios");

		require(isAnyVisibleWithTimeout(page, 20000,
				page.getByText(Pattern.compile("Informaci[oó]n\\s+General", Pattern.CASE_INSENSITIVE))),
				"'Información General' section is missing");
		require(isAnyVisibleWithTimeout(page, 20000,
				page.getByText(Pattern.compile("Detalles\\s+de\\s+la\\s+Cuenta", Pattern.CASE_INSENSITIVE))),
				"'Detalles de la Cuenta' section is missing");
		require(isAnyVisibleWithTimeout(page, 20000,
				page.getByText(Pattern.compile("Tus\\s+Negocios", Pattern.CASE_INSENSITIVE))),
				"'Tus Negocios' section is missing");
		require(isAnyVisibleWithTimeout(page, 20000,
				page.getByText(Pattern.compile("Secci[oó]n\\s+Legal", Pattern.CASE_INSENSITIVE))),
				"'Sección Legal' section is missing");

		takeScreenshot(page, runDir.resolve("04_administrar_negocios_view_full.png"), true);
	}

	private void validateInformacionGeneral(final Page page) {
		require(isAnyVisibleWithTimeout(page, 10000,
				page.getByText(Pattern.compile("BUSINESS\\s+PLAN", Pattern.CASE_INSENSITIVE))),
				"'BUSINESS PLAN' is not visible");
		require(isAnyVisibleWithTimeout(page, 10000,
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("Cambiar\\s+Plan", Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("Cambiar\\s+Plan", Pattern.CASE_INSENSITIVE)))),
				"'Cambiar Plan' button is not visible");

		final String pageText = page.locator("main, body").first().innerText();
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(pageText);
		require(emailMatcher.find(), "User email was not found");
		final boolean hasNameLikeText = Pattern.compile("\\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\\b")
				.matcher(pageText).find()
				|| Pattern.compile("\\bNombre\\b", Pattern.CASE_INSENSITIVE).matcher(pageText).find();
		require(hasNameLikeText, "User name was not found");
	}

	private void validateDetallesCuenta(final Page page) {
		require(isAnyVisibleWithTimeout(page, 10000,
				page.getByText(Pattern.compile("Cuenta\\s+creada", Pattern.CASE_INSENSITIVE))),
				"'Cuenta creada' is not visible");
		require(isAnyVisibleWithTimeout(page, 10000,
				page.getByText(Pattern.compile("Estado\\s+activo", Pattern.CASE_INSENSITIVE))),
				"'Estado activo' is not visible");
		require(isAnyVisibleWithTimeout(page, 10000,
				page.getByText(Pattern.compile("Idioma\\s+seleccionado", Pattern.CASE_INSENSITIVE))),
				"'Idioma seleccionado' is not visible");
	}

	private void validateTusNegocios(final Page page) {
		require(isAnyVisibleWithTimeout(page, 10000,
				page.getByText(Pattern.compile("Tus\\s+Negocios", Pattern.CASE_INSENSITIVE))),
				"Business list section 'Tus Negocios' is not visible");
		require(isAnyVisibleWithTimeout(page, 10000,
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE)))),
				"'Agregar Negocio' button in business section is not visible");
		require(
				isAnyVisibleWithTimeout(page, 10000,
						page.getByText(Pattern.compile("Tienes\\s+2\\s+de\\s+3\\s+negocios", Pattern.CASE_INSENSITIVE))),
				"'Tienes 2 de 3 negocios' is not visible in business section");
		require(page.locator("main li, main tr, main [role='listitem']").count() > 0,
				"Business list entries were not detected");
	}

	private void validateLegalDocument(final Page appPage, final BrowserContext context, final Path runDir,
			final Map<String, String> legalUrls, final String reportLabel, final Pattern linkPattern,
			final Pattern headingPattern, final String screenshotName) {
		final Locator legalLink = firstVisibleWithTimeout(10000, appPage.getByText(linkPattern),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(linkPattern)));
		requireVisible(legalLink, "Legal link was not found for pattern: " + linkPattern.pattern());

		Page legalPage;
		boolean openedInNewTab = false;
		try {
			legalPage = appPage.waitForPopup(() -> legalLink.first().click(new Locator.ClickOptions().setTimeout(15000)),
					new Page.WaitForPopupOptions().setTimeout(7000));
			openedInNewTab = true;
		} catch (final PlaywrightException ignored) {
			clickAndWait(appPage, legalLink, "Open legal page in same tab: " + linkPattern.pattern());
			legalPage = appPage;
		}

		waitForUi(legalPage);
		require(isAnyVisibleWithTimeout(legalPage, 15000, legalPage.getByRole(AriaRole.HEADING,
				new Page.GetByRoleOptions().setName(headingPattern)), legalPage.getByText(headingPattern)),
				"Expected legal heading was not found: " + headingPattern.pattern());
		require(legalPage.locator("main p, article p, section p, div p").count() > 0,
				"Legal content text is not visible");

		takeScreenshot(legalPage, runDir.resolve(screenshotName), true);
		legalUrls.put(reportLabel, legalPage.url());

		if (openedInNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			legalPage.goBack();
			waitForUi(legalPage);
		}

		// Keep context reference used to avoid accidental cleanup by static analysis.
		if (context.pages().isEmpty()) {
			throw new AssertionError("All browser tabs were unexpectedly closed");
		}
	}

	private static void navigateToLoginIfConfigured(final Page page) {
		final String configuredUrl = firstNonEmpty(System.getProperty("saleads.login.url"),
				System.getenv("SALEADS_LOGIN_URL"), System.getenv("BASE_URL"));
		if (configuredUrl != null) {
			page.navigate(configuredUrl);
			waitForUi(page);
		}
	}

	private static Browser.NewContextOptions defaultContextOptions() {
		return new Browser.NewContextOptions().setViewportSize(1600, 1200);
	}

	private static BrowserType.LaunchOptions buildLaunchOptionsFromProperties() {
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		final double slowMo = Double.parseDouble(System.getProperty("saleads.slowmo.ms", "0"));
		final BrowserType.LaunchOptions options = new BrowserType.LaunchOptions();
		options.setHeadless(headless);
		if (slowMo > 0) {
			options.setSlowMo(slowMo);
		}
		return options;
	}

	private static Map<String, Boolean> initializeReport() {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
		return report;
	}

	private static Locator firstVisibleWithTimeout(final int timeoutMs, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate != null && isVisibleWithTimeout(candidate, timeoutMs)) {
				return candidate.first();
			}
		}
		return null;
	}

	private static boolean isAnyVisibleWithTimeout(final Page page, final int timeoutMs, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate != null && isVisibleWithTimeout(candidate, timeoutMs)) {
				return true;
			}
		}
		waitForUi(page);
		return false;
	}

	private static boolean isVisibleWithTimeout(final Locator locator, final int timeoutMs) {
		try {
			locator.first().waitFor(
					new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout((double) timeoutMs));
			return locator.first().isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static void clickAndWait(final Page page, final Locator locator, final String actionName) {
		requireVisible(locator, "Cannot click. Element is not visible for action: " + actionName);
		locator.first().scrollIntoViewIfNeeded();
		locator.first().click(new Locator.ClickOptions().setTimeout(15000));
		waitForUi(page);
	}

	private static void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000));
		} catch (final PlaywrightException ignored) {
			// Some SPA screens keep polling; DOM readiness is sufficient in that case.
		}
		page.waitForTimeout(400);
	}

	private static void require(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void requireVisible(final Locator locator, final String message) {
		require(locator != null && isVisibleWithTimeout(locator, 5000), message);
	}

	private static void takeScreenshot(final Page page, final Path path, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private static void printSummary(final Map<String, Boolean> report, final Map<String, String> details) {
		System.out.println("=== " + TEST_NAME + " Final Report ===");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			final String status = entry.getValue() ? "PASS" : "FAIL";
			System.out.println(entry.getKey() + ": " + status + " (" + details.getOrDefault(entry.getKey(), "n/a") + ")");
		}
	}

	private static void writeReport(final Path runDir, final Map<String, Boolean> report, final Map<String, String> details,
			final Map<String, String> legalUrls) throws IOException {
		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"name\": \"").append(TEST_NAME).append("\",\n");
		json.append("  \"generatedAtUtc\": \"").append(Instant.now().toString()).append("\",\n");
		json.append("  \"evidenceDirectory\": \"").append(escapeJson(runDir.toAbsolutePath().toString())).append("\",\n");
		json.append("  \"results\": {\n");
		int index = 0;
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			json.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(entry.getValue() ? "PASS" : "FAIL").append("\"");
			if (++index < report.size()) {
				json.append(",");
			}
			json.append("\n");
		}
		json.append("  },\n");
		json.append("  \"details\": {\n");
		index = 0;
		for (final Map.Entry<String, String> entry : details.entrySet()) {
			json.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(escapeJson(entry.getValue())).append("\"");
			if (++index < details.size()) {
				json.append(",");
			}
			json.append("\n");
		}
		json.append("  },\n");
		json.append("  \"legalUrls\": {\n");
		index = 0;
		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			json.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
					.append(escapeJson(entry.getValue())).append("\"");
			if (++index < legalUrls.size()) {
				json.append(",");
			}
			json.append("\n");
		}
		json.append("  }\n");
		json.append("}\n");

		Files.writeString(runDir.resolve("final-report.json"), json.toString());
	}

	private static String safeMessage(final Throwable throwable) {
		if (throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()) {
			return throwable == null ? "Unknown error" : throwable.getClass().getSimpleName();
		}
		return throwable.getMessage().replace('\n', ' ').trim();
	}

	private static String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String firstNonEmpty(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

}
