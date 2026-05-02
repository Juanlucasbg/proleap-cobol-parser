package io.proleap.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
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

	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern SIGN_IN_WITH_GOOGLE_PATTERN = Pattern.compile(
			"(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[o\\u00F3]n\\s*con\\s*google)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern TERMINOS_PATTERN = Pattern.compile("T[\\u00E9e]rminos\\s+y\\s+Condiciones",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern POLITICA_PATTERN = Pattern.compile("Pol[\\u00EDi]tica\\s+de\\s+Privacidad",
			Pattern.CASE_INSENSITIVE);

	private final Map<String, StepResult> report = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private Path evidenceDir;

	private String loginUrl;
	private String googleEmail;
	private String expectedUserName;
	private long uiWaitMs;
	private boolean e2eEnabled;

	@Before
	public void setUp() throws IOException {
		e2eEnabled = Boolean.parseBoolean(readSetting("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		assumeTrue("SaleADS E2E disabled. Set -Dsaleads.e2e.enabled=true (or SALEADS_E2E_ENABLED=true) to run.",
				e2eEnabled);

		loginUrl = readSetting("saleads.loginUrl", "SALEADS_LOGIN_URL", "");
		googleEmail = readSetting("saleads.googleEmail", "SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL);
		expectedUserName = readSetting("saleads.userName", "SALEADS_USER_NAME", emailLocalPart(googleEmail));
		uiWaitMs = Long.parseLong(readSetting("saleads.uiWaitMs", "SALEADS_UI_WAIT_MS", "1200"));

		assumeTrue(
				"Missing login URL. Configure -Dsaleads.loginUrl (or SALEADS_LOGIN_URL) with the login page of the target environment.",
				!loginUrl.isBlank());

		Path baseEvidenceDir = Path.of("target", "saleads-evidence");
		Files.createDirectories(baseEvidenceDir);
		evidenceDir = baseEvidenceDir.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		playwright = Playwright.create();
		boolean headless = Boolean.parseBoolean(readSetting("saleads.headless", "SALEADS_HEADLESS", "true"));
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
		page = context.newPage();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();

		if (context != null) {
			context.close();
		}
		if (browser != null) {
			browser.close();
		}
		if (playwright != null) {
			playwright.close();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Informaci\u00F3n General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("T\u00E9rminos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Pol\u00EDtica de Privacidad", this::stepValidatePoliticaPrivacidad);

		StringBuilder failures = new StringBuilder();
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				if (!failures.isEmpty()) {
					failures.append(System.lineSeparator());
				}
				failures.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().details);
			}
		}

		assertTrue("SaleADS Mi Negocio workflow failures:" + System.lineSeparator() + failures, failures.isEmpty());
	}

	private void stepLoginWithGoogle() {
		page.navigate(loginUrl);
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);

		Page googlePage = null;
		try {
			googlePage = context.waitForPage(() -> clickSignInWithGoogleButton());
		} catch (PlaywrightException ignored) {
			// Some environments reuse the same tab for Google auth.
		}
		waitForUi();

		if (googlePage != null) {
			handleGoogleAccountSelection(googlePage);
		} else {
			handleGoogleAccountSelection(page);
		}

		waitForUi();
		assertVisible(page.locator("aside"), "Left sidebar navigation is not visible after login.");
		assertVisible(page.getByText(Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE)).first(),
				"Main application did not load after Google login.");

		screenshot("01-dashboard-loaded.png", false);
	}

	private void stepOpenMiNegocioMenu() {
		ensureMiNegocioExpanded();

		assertVisible(page.getByText(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE)).first(),
				"'Agregar Negocio' is not visible in Mi Negocio menu.");
		assertVisible(page.getByText(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE)).first(),
				"'Administrar Negocios' is not visible in Mi Negocio menu.");

		screenshot("02-mi-negocio-menu-expanded.png", false);
	}

	private void stepValidateAgregarNegocioModal() {
		clickAndWait(page.getByText(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE)).first());

		Locator modalTitle = page.getByText(Pattern.compile("Crear\\s+Nuevo\\s+Negocio", Pattern.CASE_INSENSITIVE)).first();
		assertVisible(modalTitle, "Modal title 'Crear Nuevo Negocio' was not found.");

		assertVisible(page.getByLabel(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE)).first(),
				"'Nombre del Negocio' input was not found.");
		assertVisible(page.getByText(Pattern.compile("Tienes\\s+2\\s+de\\s+3\\s+negocios", Pattern.CASE_INSENSITIVE)).first(),
				"'Tienes 2 de 3 negocios' text was not found in modal.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first(),
				"'Cancelar' button was not found in modal.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Crear\\s+Negocio")))
				.first(), "'Crear Negocio' button was not found in modal.");

		screenshot("03-agregar-negocio-modal.png", false);

		Locator nombreNegocioInput = page.getByLabel(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE))
				.first();
		nombreNegocioInput.click();
		nombreNegocioInput.fill("Negocio Prueba Automatizacion");
		clickAndWait(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first());
	}

	private void stepOpenAdministrarNegocios() {
		ensureMiNegocioExpanded();
		clickAndWait(page.getByText(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE)).first());

		assertVisible(page.getByText(Pattern.compile("Informaci[o\\u00F3]n\\s+General", Pattern.CASE_INSENSITIVE)).first(),
				"'Informacion General' section was not found.");
		assertVisible(page.getByText(Pattern.compile("Detalles\\s+de\\s+la\\s+Cuenta", Pattern.CASE_INSENSITIVE)).first(),
				"'Detalles de la Cuenta' section was not found.");
		assertVisible(page.getByText(Pattern.compile("Tus\\s+Negocios", Pattern.CASE_INSENSITIVE)).first(),
				"'Tus Negocios' section was not found.");
		assertVisible(page.getByText(Pattern.compile("Secci[o\\u00F3]n\\s+Legal", Pattern.CASE_INSENSITIVE)).first(),
				"'Seccion Legal' section was not found.");

		screenshot("04-administrar-negocios-view.png", true);
	}

	private void stepValidateInformacionGeneral() {
		Locator infoGeneral = page.getByText(Pattern.compile("Informaci[o\\u00F3]n\\s+General", Pattern.CASE_INSENSITIVE)).first();
		assertVisible(infoGeneral, "'Informacion General' heading missing.");

		assertVisible(page.getByText(Pattern.compile("BUSINESS\\s+PLAN", Pattern.CASE_INSENSITIVE)).first(),
				"'BUSINESS PLAN' text was not found.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Cambiar\\s+Plan")))
				.first(), "'Cambiar Plan' button was not found.");

		Locator emailLocator = page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first();
		assertVisible(emailLocator, "User email is not visible in Informacion General.");
		assertVisible(page.getByText(Pattern.compile(Pattern.quote(expectedUserName), Pattern.CASE_INSENSITIVE)).first(),
				"User name is not visible in Informacion General. Expected name pattern: " + expectedUserName);
	}

	private void stepValidateDetallesCuenta() {
		assertVisible(page.getByText(Pattern.compile("Cuenta\\s+creada", Pattern.CASE_INSENSITIVE)).first(),
				"'Cuenta creada' label was not found.");
		assertVisible(page.getByText(Pattern.compile("Estado\\s+activo", Pattern.CASE_INSENSITIVE)).first(),
				"'Estado activo' label was not found.");
		assertVisible(page.getByText(Pattern.compile("Idioma\\s+seleccionado", Pattern.CASE_INSENSITIVE)).first(),
				"'Idioma seleccionado' label was not found.");
	}

	private void stepValidateTusNegocios() {
		assertVisible(page.getByText(Pattern.compile("Tus\\s+Negocios", Pattern.CASE_INSENSITIVE)).first(),
				"'Tus Negocios' heading missing.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Agregar\\s+Negocio")))
				.first(), "'Agregar Negocio' button was not found in 'Tus Negocios'.");
		assertVisible(page.getByText(Pattern.compile("Tienes\\s+2\\s+de\\s+3\\s+negocios", Pattern.CASE_INSENSITIVE)).first(),
				"'Tienes 2 de 3 negocios' text was not found in 'Tus Negocios'.");
	}

	private void stepValidateTerminosYCondiciones() {
		String finalUrl = validateLegalLink("08-terminos-y-condiciones.png", TERMINOS_PATTERN);
		report.get("T\u00E9rminos y Condiciones").details = "PASS - URL: " + finalUrl;
	}

	private void stepValidatePoliticaPrivacidad() {
		String finalUrl = validateLegalLink("09-politica-privacidad.png", POLITICA_PATTERN);
		report.get("Pol\u00EDtica de Privacidad").details = "PASS - URL: " + finalUrl;
	}

	private String validateLegalLink(String screenshotName, Pattern linkPattern) {
		Page appPage = page;
		Page legalPage = null;
		boolean openedNewTab = false;

		try {
			legalPage = context.waitForPage(() -> clickAndWait(page.getByText(linkPattern).first()));
			openedNewTab = true;
		} catch (PlaywrightException ignored) {
			clickAndWait(page.getByText(linkPattern).first());
			legalPage = page;
		}

		legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		waitForUi(legalPage);

		assertVisible(legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(linkPattern)).first(),
				"Legal page heading missing for: " + linkPattern.pattern());
		assertVisible(legalPage.locator("main p, article p, p").first(),
				"Legal content text is not visible for: " + linkPattern.pattern());

		screenshot(legalPage, screenshotName, false);
		String finalUrl = legalPage.url();

		if (openedNewTab) {
			legalPage.close();
			page = appPage;
		} else {
			page.goBack();
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
			waitForUi();
		}

		return finalUrl;
	}

	private void ensureMiNegocioExpanded() {
		Locator agregarNegocio = page.getByText(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE)).first();
		Locator administrarNegocios = page.getByText(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE)).first();

		if (isVisible(agregarNegocio) && isVisible(administrarNegocios)) {
			return;
		}

		Locator negocio = page.getByText(Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE)).first();
		if (isVisible(negocio)) {
			clickAndWait(negocio);
		}

		Locator miNegocio = page.getByText(Pattern.compile("Mi\\s+Negocio", Pattern.CASE_INSENSITIVE)).first();
		clickAndWait(miNegocio);
	}

	private void clickSignInWithGoogleButton() {
		Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SIGN_IN_WITH_GOOGLE_PATTERN)).first();
		if (!isVisible(button)) {
			button = page.getByText(SIGN_IN_WITH_GOOGLE_PATTERN).first();
		}

		assertVisible(button, "Google login button was not found.");
		clickAndWait(button);
	}

	private void handleGoogleAccountSelection(Page authPage) {
		Locator accountEntry = authPage.getByText(Pattern.compile(Pattern.quote(googleEmail), Pattern.CASE_INSENSITIVE)).first();
		if (isVisible(accountEntry)) {
			clickAndWait(accountEntry);
			authPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		}
	}

	private void runStep(String stepName, Runnable action) {
		StepResult stepResult = new StepResult(false, "Not executed");
		report.put(stepName, stepResult);

		try {
			action.run();
			stepResult.passed = true;
			if (stepResult.details == null || stepResult.details.isBlank() || "Not executed".equals(stepResult.details)) {
				stepResult.details = "PASS";
			}
		} catch (Throwable throwable) {
			stepResult.passed = false;
			stepResult.details = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
			try {
				screenshot("FAIL-" + sanitizeFileName(stepName) + ".png", false);
			} catch (Throwable ignored) {
				// Ignore screenshot capture failures on failing steps.
			}
		}
	}

	private void assertVisible(Locator locator, String messageIfMissing) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
		} catch (PlaywrightException ex) {
			throw new AssertionError(messageIfMissing, ex);
		}
	}

	private boolean isVisible(Locator locator) {
		try {
			return locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout(1500));
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void clickAndWait(Locator locator) {
		assertVisible(locator, "Element to click is not visible.");
		locator.first().click();
		waitForUi();
	}

	private void waitForUi() {
		waitForUi(page);
	}

	private void waitForUi(Page targetPage) {
		try {
			targetPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
			// Some SPA pages keep network connections open and never become fully idle.
		}
		targetPage.waitForTimeout(uiWaitMs);
	}

	private void screenshot(String name, boolean fullPage) {
		screenshot(page, name, fullPage);
	}

	private void screenshot(Page targetPage, String name, boolean fullPage) {
		targetPage.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(name)).setFullPage(fullPage));
	}

	private String readSetting(String systemPropertyKey, String envVarKey, String defaultValue) {
		String systemValue = System.getProperty(systemPropertyKey);
		if (systemValue != null && !systemValue.isBlank()) {
			return systemValue.trim();
		}

		String envValue = System.getenv(envVarKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		StringBuilder summary = new StringBuilder();
		summary.append("SaleADS Mi Negocio workflow final report").append(System.lineSeparator());
		summary.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		summary.append(System.lineSeparator());

		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			summary.append(entry.getKey())
					.append(": ")
					.append(entry.getValue().passed ? "PASS" : "FAIL")
					.append(" - ")
					.append(entry.getValue().details == null ? "" : entry.getValue().details)
					.append(System.lineSeparator());
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), summary.toString(), StandardCharsets.UTF_8);
		Files.writeString(evidenceDir.resolve("final-report.json"), toJsonReport(), StandardCharsets.UTF_8);
	}

	private String toJsonReport() {
		StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"report\": [\n");

		int index = 0;
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			StepResult value = entry.getValue();
			json.append("    {\n");
			json.append("      \"step\": \"").append(escapeJson(entry.getKey())).append("\",\n");
			json.append("      \"status\": \"").append(value.passed ? "PASS" : "FAIL").append("\",\n");
			json.append("      \"details\": \"").append(escapeJson(value.details == null ? "" : value.details)).append("\"\n");
			json.append("    }");
			if (index < report.size() - 1) {
				json.append(",");
			}
			json.append("\n");
			index++;
		}

		json.append("  ]\n");
		json.append("}\n");
		return json.toString();
	}

	private String escapeJson(String input) {
		return input.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r");
	}

	private String sanitizeFileName(String input) {
		return input.toLowerCase().replaceAll("[^a-z0-9]+", "-");
	}

	private String emailLocalPart(String email) {
		int atIndex = email.indexOf('@');
		if (atIndex > 0) {
			return email.substring(0, atIndex);
		}
		return email;
	}

	private static final class StepResult {
		private boolean passed;
		private String details;

		private StepResult(boolean passed, String details) {
			this.passed = passed;
			this.details = details;
		}
	}
}
