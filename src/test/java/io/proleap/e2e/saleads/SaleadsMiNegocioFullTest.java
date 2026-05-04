package io.proleap.e2e.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SaleadsMiNegocioFullTest {

	private static final String FIELD_LOGIN = "Login";
	private static final String FIELD_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String FIELD_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String FIELD_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String FIELD_INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String FIELD_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String FIELD_TUS_NEGOCIOS = "Tus Negocios";
	private static final String FIELD_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String FIELD_PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-e2e-evidence");
	private static final Path SCREENSHOT_DIR = EVIDENCE_DIR.resolve("screenshots");
	private static final Path REPORT_FILE = EVIDENCE_DIR.resolve("mi-negocio-final-report.json");

	private static final long DEFAULT_TIMEOUT_MS = 20_000L;
	private static final String DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue(
				"Enable this test with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true",
				boolConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false));

		Files.createDirectories(SCREENSHOT_DIR);
		final String loginUrl = requiredConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		final String googleAccount = stringConfig("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT", DEFAULT_ACCOUNT_EMAIL);
		final String expectedEmail = stringConfig("saleads.expected.email", "SALEADS_EXPECTED_EMAIL", googleAccount);
		final String expectedName = stringConfig("saleads.expected.name", "SALEADS_EXPECTED_NAME", "");
		final boolean headless = boolConfig("saleads.headless", "SALEADS_HEADLESS", true);

		final LinkedHashMap<String, StepResult> report = createEmptyReport();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final String[] accountPageUrl = new String[] { "" };

		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium()
						.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(150));
				BrowserContext context = browser.newContext(
						new Browser.NewContextOptions().setViewportSize(1600, 1200))) {
			final Page appPage = context.newPage();
			appPage.navigate(loginUrl);
			waitForUiLoad(appPage);

			report.put(FIELD_LOGIN, executeStep(() -> {
				Page loggedInPage = loginWithGoogle(appPage, context, googleAccount);
				assertMainInterfaceVisible(loggedInPage);
				captureScreenshot(loggedInPage, "01-dashboard-loaded", false);
			}));

			report.put(FIELD_MI_NEGOCIO_MENU, executeStep(() -> {
				expandMiNegocioMenu(appPage);
				assertVisible(appPage,
						Pattern.compile("^Agregar Negocio$", Pattern.CASE_INSENSITIVE),
						"Expected submenu option 'Agregar Negocio'.");
				assertVisible(appPage,
						Pattern.compile("^Administrar Negocios$", Pattern.CASE_INSENSITIVE),
						"Expected submenu option 'Administrar Negocios'.");
				captureScreenshot(appPage, "02-mi-negocio-expanded-menu", false);
			}));

			report.put(FIELD_AGREGAR_NEGOCIO_MODAL, executeStep(() -> {
				clickInSidebar(appPage, Pattern.compile("^Agregar Negocio$", Pattern.CASE_INSENSITIVE));
				waitForUiLoad(appPage);
				assertVisible(appPage,
						Pattern.compile("Crear Nuevo Negocio", Pattern.CASE_INSENSITIVE),
						"Expected modal title 'Crear Nuevo Negocio'.");
				assertAnyVisible("Expected 'Nombre del Negocio' input field.",
						appPage.getByLabel(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
						appPage.getByPlaceholder(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
						appPage.locator("input[name*='negocio'], input[id*='negocio'], input[placeholder*='Negocio']"));
				assertVisible(appPage,
						Pattern.compile("Tienes\\s*2\\s*de\\s*3\\s*negocios", Pattern.CASE_INSENSITIVE),
						"Expected business quota text.");
				assertVisible(appPage, Pattern.compile("^Cancelar$", Pattern.CASE_INSENSITIVE),
						"Expected button 'Cancelar'.");
				assertVisible(appPage, Pattern.compile("Crear Negocio", Pattern.CASE_INSENSITIVE),
						"Expected button 'Crear Negocio'.");
				captureScreenshot(appPage, "03-agregar-negocio-modal", false);

				Locator nombreInput = firstVisible(
						appPage.getByLabel(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
						appPage.getByPlaceholder(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
						appPage.locator("input[name*='negocio'], input[id*='negocio'], input[placeholder*='Negocio']"));
				nombreInput.click();
				nombreInput.fill("Negocio Prueba Automatizacion");
				clickAndWait(appPage, firstVisible(
						appPage.getByRole(AriaRole.BUTTON,
								new Page.GetByRoleOptions().setName(Pattern.compile("^Cancelar$", Pattern.CASE_INSENSITIVE))),
						appPage.getByText(Pattern.compile("^Cancelar$", Pattern.CASE_INSENSITIVE))));
			}));

			report.put(FIELD_ADMINISTRAR_NEGOCIOS_VIEW, executeStep(() -> {
				if (!isVisible(appPage.getByText(Pattern.compile("^Administrar Negocios$", Pattern.CASE_INSENSITIVE)).first())) {
					expandMiNegocioMenu(appPage);
				}
				clickInSidebar(appPage, Pattern.compile("^Administrar Negocios$", Pattern.CASE_INSENSITIVE));
				waitForUiLoad(appPage);

				assertVisible(appPage,
						Pattern.compile("Informaci[o\u00f3]n General", Pattern.CASE_INSENSITIVE),
						"Expected section 'Informacion General'.");
				assertVisible(appPage,
						Pattern.compile("Detalles de la Cuenta", Pattern.CASE_INSENSITIVE),
						"Expected section 'Detalles de la Cuenta'.");
				assertVisible(appPage,
						Pattern.compile("Tus Negocios", Pattern.CASE_INSENSITIVE),
						"Expected section 'Tus Negocios'.");
				assertVisible(appPage,
						Pattern.compile("Secci[o\u00f3]n Legal", Pattern.CASE_INSENSITIVE),
						"Expected section 'Seccion Legal'.");
				accountPageUrl[0] = appPage.url();
				captureScreenshot(appPage, "04-administrar-negocios-page", true);
			}));

			report.put(FIELD_INFORMACION_GENERAL, executeStep(() -> {
				assertVisible(appPage,
						Pattern.compile("BUSINESS\\s*PLAN", Pattern.CASE_INSENSITIVE),
						"Expected text 'BUSINESS PLAN'.");
				assertVisible(appPage,
						Pattern.compile("Cambiar Plan", Pattern.CASE_INSENSITIVE),
						"Expected button 'Cambiar Plan'.");

				if (!expectedName.isBlank()) {
					assertVisible(appPage, Pattern.compile(Pattern.quote(expectedName), Pattern.CASE_INSENSITIVE),
							"Expected configured user name to be visible.");
				} else {
					assertAnyVisible("Expected a visible user name or profile identifier.",
							appPage.locator("[data-testid*='name'], [class*='name'], [id*='name']"),
							appPage.locator("text=/[A-Za-z]{2,}\\s+[A-Za-z]{2,}/"));
				}
				assertVisible(appPage, Pattern.compile(Pattern.quote(expectedEmail), Pattern.CASE_INSENSITIVE),
						"Expected user email to be visible.");
			}));

			report.put(FIELD_DETALLES_CUENTA, executeStep(() -> {
				assertVisible(appPage, Pattern.compile("Cuenta creada", Pattern.CASE_INSENSITIVE),
						"Expected 'Cuenta creada'.");
				assertVisible(appPage, Pattern.compile("Estado activo", Pattern.CASE_INSENSITIVE),
						"Expected 'Estado activo'.");
				assertVisible(appPage, Pattern.compile("Idioma seleccionado", Pattern.CASE_INSENSITIVE),
						"Expected 'Idioma seleccionado'.");
			}));

			report.put(FIELD_TUS_NEGOCIOS, executeStep(() -> {
				assertVisible(appPage, Pattern.compile("Tus Negocios", Pattern.CASE_INSENSITIVE),
						"Expected section title 'Tus Negocios'.");
				assertVisible(appPage, Pattern.compile("^Agregar Negocio$", Pattern.CASE_INSENSITIVE),
						"Expected button 'Agregar Negocio'.");
				assertVisible(appPage,
						Pattern.compile("Tienes\\s*2\\s*de\\s*3\\s*negocios", Pattern.CASE_INSENSITIVE),
						"Expected business quota text.");
				assertAnyVisible("Expected visible business list content.",
						appPage.locator("[data-testid*='business'], [class*='business-item'], [class*='business-list']"),
						appPage.locator("li"),
						appPage.locator("[role='row']"));
			}));

			report.put(FIELD_TERMINOS, executeStep(() -> {
				String finalUrl = validateLegalPage(appPage, context,
						Pattern.compile("T[e\u00e9]rminos y Condiciones", Pattern.CASE_INSENSITIVE),
						Pattern.compile("T[e\u00e9]rminos y Condiciones", Pattern.CASE_INSENSITIVE),
						"05-terminos-y-condiciones",
						accountPageUrl[0]);
				legalUrls.put(FIELD_TERMINOS, finalUrl);
			}));

			report.put(FIELD_PRIVACIDAD, executeStep(() -> {
				String finalUrl = validateLegalPage(appPage, context,
						Pattern.compile("Pol[i\u00ed]tica de Privacidad", Pattern.CASE_INSENSITIVE),
						Pattern.compile("Pol[i\u00ed]tica de Privacidad", Pattern.CASE_INSENSITIVE),
						"06-politica-de-privacidad",
						accountPageUrl[0]);
				legalUrls.put(FIELD_PRIVACIDAD, finalUrl);
			}));
		} finally {
			writeReport(report, legalUrls);
		}

		final List<String> failedFields = report.entrySet().stream()
				.filter(entry -> !entry.getValue().pass)
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());
		Assert.assertTrue(
				"Final report contains failing validations: " + failedFields + ". Check " + REPORT_FILE.toAbsolutePath(),
				failedFields.isEmpty());
	}

	private Page loginWithGoogle(final Page appPage, final BrowserContext context, final String googleAccount) {
		final Pattern loginPattern = Pattern.compile("(Google|Iniciar sesi[o\u00f3]n|Login)", Pattern.CASE_INSENSITIVE);
		Locator loginButton = firstVisible(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(loginPattern)),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(loginPattern)),
				appPage.getByText(loginPattern));
		int beforePages = context.pages().size();
		loginButton.click();
		waitForUiLoad(appPage);
		Page googlePage = waitForNewPage(context, beforePages);
		if (googlePage != null) {
			waitForUiLoad(googlePage);
		}

		Page activePage = findGooglePage(context);
		if (activePage == null) {
			activePage = appPage;
		}

		Locator accountOption = activePage.getByText(Pattern.compile("^" + Pattern.quote(googleAccount) + "$",
				Pattern.CASE_INSENSITIVE));
		if (isVisible(accountOption.first())) {
			clickAndWait(activePage, accountOption.first());
		}
		waitForUiLoad(activePage);
		return appPage;
	}

	private void expandMiNegocioMenu(final Page page) {
		assertMainInterfaceVisible(page);
		Locator negocioTrigger = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("^Negocio$|^Mi Negocio$", Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
						.setName(Pattern.compile("^Negocio$|^Mi Negocio$", Pattern.CASE_INSENSITIVE))),
				page.getByText(Pattern.compile("^Negocio$|^Mi Negocio$", Pattern.CASE_INSENSITIVE)));
		clickAndWait(page, negocioTrigger);

		Locator miNegocio = firstVisible(
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("^Mi Negocio$", Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("^Mi Negocio$", Pattern.CASE_INSENSITIVE))),
				page.getByText(Pattern.compile("^Mi Negocio$", Pattern.CASE_INSENSITIVE)));
		clickAndWait(page, miNegocio);
	}

	private void clickInSidebar(final Page page, final Pattern optionText) {
		Locator sidebar = firstVisible(
				page.getByRole(AriaRole.NAVIGATION),
				page.locator("aside"),
				page.locator("[class*='sidebar'], [data-testid*='sidebar']"));
		Locator option = firstVisible(
				sidebar.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(optionText)),
				sidebar.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(optionText)),
				sidebar.getByText(optionText),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(optionText)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(optionText)),
				page.getByText(optionText));
		clickAndWait(page, option);
	}

	private String validateLegalPage(final Page appPage, final BrowserContext context, final Pattern linkPattern,
			final Pattern headingPattern, final String screenshotName, final String accountPageUrl) throws IOException {
		Locator link = firstVisible(
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)),
				appPage.getByText(linkPattern));

		int pageCountBefore = context.pages().size();
		link.click();
		Page destinationPage = waitForNewPage(context, pageCountBefore);
		if (destinationPage == null) {
			destinationPage = appPage;
		}
		waitForUiLoad(destinationPage);
		assertVisible(destinationPage, headingPattern, "Expected legal heading to be visible.");

		String bodyText = destinationPage.textContent("body");
		Assert.assertTrue("Expected legal content text to be visible.",
				bodyText != null && bodyText.replaceAll("\\s+", " ").trim().length() > 120);
		captureScreenshot(destinationPage, screenshotName, true);

		final String finalUrl = destinationPage.url();
		if (destinationPage != appPage) {
			destinationPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else {
			boolean returned = false;
			try {
				returned = appPage.goBack(new Page.GoBackOptions().setTimeout(DEFAULT_TIMEOUT_MS)) != null;
			} catch (Exception ignored) {
				returned = false;
			}
			if (!returned && accountPageUrl != null && !accountPageUrl.isBlank()) {
				appPage.navigate(accountPageUrl);
			}
			waitForUiLoad(appPage);
		}
		return finalUrl;
	}

	private void assertMainInterfaceVisible(final Page page) {
		assertAnyVisible("Expected main application interface and left sidebar to be visible.",
				page.getByRole(AriaRole.NAVIGATION),
				page.locator("aside"),
				page.locator("[class*='sidebar'], [data-testid*='sidebar']"));
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (Exception ignored) {
			// Some SPA interactions do not trigger a new document load.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7_000));
		} catch (Exception ignored) {
			// Network idle is best effort only.
		}
		page.waitForTimeout(500);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		locator.click();
		waitForUiLoad(page);
	}

	private void assertVisible(final Page page, final Pattern textPattern, final String failureMessage) {
		Locator locator = page.getByText(textPattern);
		locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		Assert.assertTrue(failureMessage, isVisible(locator.first()));
	}

	private void assertAnyVisible(final String failureMessage, final Locator... locators) {
		Locator visible = firstVisible(locators);
		Assert.assertTrue(failureMessage, isVisible(visible));
	}

	private Locator firstVisible(final Locator... locators) {
		for (int attempt = 0; attempt < 40; attempt++) {
			for (Locator locator : locators) {
				if (locator == null) {
					continue;
				}
				Locator first = locator.first();
				if (isVisible(first)) {
					return first;
				}
			}
			try {
				Thread.sleep(250);
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(interruptedException);
			}
		}
		throw new AssertionError("No visible element found among provided locators.");
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.isVisible();
		} catch (Exception ignored) {
			return false;
		}
	}

	private Page waitForNewPage(final BrowserContext context, final int previousCount) {
		for (int attempt = 0; attempt < 20; attempt++) {
			if (context.pages().size() > previousCount) {
				return context.pages().get(context.pages().size() - 1);
			}
			try {
				Thread.sleep(250);
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	private Page findGooglePage(final BrowserContext context) {
		for (Page page : context.pages()) {
			if (page.url() != null && page.url().contains("accounts.google.com")) {
				return page;
			}
		}
		return null;
	}

	private void captureScreenshot(final Page page, final String fileName, final boolean fullPage) throws IOException {
		final Path path = SCREENSHOT_DIR.resolve(fileName + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private boolean boolConfig(final String propertyKey, final String envKey, final boolean defaultValue) {
		String value = System.getProperty(propertyKey);
		if (value == null || value.isBlank()) {
			value = System.getenv(envKey);
		}
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value);
	}

	private String requiredConfig(final String propertyKey, final String envKey) {
		String value = stringConfig(propertyKey, envKey, "");
		if (value.isBlank()) {
			throw new IllegalArgumentException(
					"Missing configuration. Provide -" + propertyKey + "=... or " + envKey + " environment variable.");
		}
		return value;
	}

	private String stringConfig(final String propertyKey, final String envKey, final String defaultValue) {
		String value = System.getProperty(propertyKey);
		if (value == null || value.isBlank()) {
			value = System.getenv(envKey);
		}
		if (value == null) {
			return defaultValue;
		}
		return value.trim().isEmpty() ? defaultValue : value.trim();
	}

	private LinkedHashMap<String, StepResult> createEmptyReport() {
		LinkedHashMap<String, StepResult> report = new LinkedHashMap<>();
		report.put(FIELD_LOGIN, StepResult.pending());
		report.put(FIELD_MI_NEGOCIO_MENU, StepResult.pending());
		report.put(FIELD_AGREGAR_NEGOCIO_MODAL, StepResult.pending());
		report.put(FIELD_ADMINISTRAR_NEGOCIOS_VIEW, StepResult.pending());
		report.put(FIELD_INFORMACION_GENERAL, StepResult.pending());
		report.put(FIELD_DETALLES_CUENTA, StepResult.pending());
		report.put(FIELD_TUS_NEGOCIOS, StepResult.pending());
		report.put(FIELD_TERMINOS, StepResult.pending());
		report.put(FIELD_PRIVACIDAD, StepResult.pending());
		return report;
	}

	private StepResult executeStep(final CheckedRunnable action) {
		try {
			action.run();
			return StepResult.pass();
		} catch (Throwable throwable) {
			return StepResult.fail(throwable.getMessage() == null ? throwable.toString() : throwable.getMessage());
		}
	}

	private void writeReport(final LinkedHashMap<String, StepResult> report, final Map<String, String> legalUrls)
			throws IOException {
		StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"generatedAt\": \"").append(escapeJson(Instant.now().toString())).append("\",\n");
		json.append("  \"results\": {\n");
		int index = 0;
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			json.append("    \"").append(escapeJson(entry.getKey())).append("\": {\n");
			json.append("      \"status\": \"").append(entry.getValue().pass ? "PASS" : "FAIL").append("\",\n");
			json.append("      \"details\": \"").append(escapeJson(entry.getValue().details)).append("\"\n");
			json.append("    }");
			if (index < report.size() - 1) {
				json.append(",");
			}
			json.append("\n");
			index++;
		}
		json.append("  },\n");
		json.append("  \"evidence\": {\n");
		json.append("    \"screenshotsDir\": \"").append(escapeJson(SCREENSHOT_DIR.toString())).append("\",\n");
		json.append("    \"reportFile\": \"").append(escapeJson(REPORT_FILE.toString())).append("\"\n");
		json.append("  },\n");
		json.append("  \"legalFinalUrls\": {\n");
		json.append("    \"").append(escapeJson(FIELD_TERMINOS)).append("\": \"")
				.append(escapeJson(legalUrls.getOrDefault(FIELD_TERMINOS, ""))).append("\",\n");
		json.append("    \"").append(escapeJson(FIELD_PRIVACIDAD)).append("\": \"")
				.append(escapeJson(legalUrls.getOrDefault(FIELD_PRIVACIDAD, ""))).append("\"\n");
		json.append("  }\n");
		json.append("}\n");
		Files.createDirectories(EVIDENCE_DIR);
		Files.writeString(REPORT_FILE, json.toString(), StandardCharsets.UTF_8);
	}

	private String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean pass;
		private final String details;

		private StepResult(final boolean pass, final String details) {
			this.pass = pass;
			this.details = details;
		}

		private static StepResult pending() {
			return new StepResult(false, "Not executed.");
		}

		private static StepResult pass() {
			return new StepResult(true, "All validations passed.");
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details == null || details.isBlank() ? "Step failed." : details);
		}
	}
}
