package io.proleap.cobol.e2e.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final String LOGIN_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final int DEFAULT_TIMEOUT_MS = 20000;
	private static final DateTimeFormatter RUN_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, String> finalReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private Path screenshotsDir;

	@Before
	public void setUp() throws Exception {
		String runId = LocalDateTime.now().format(RUN_FORMAT);
		screenshotsDir = Paths.get("target", "saleads-mi-negocio-evidence", runId);
		Files.createDirectories(screenshotsDir);

		playwright = Playwright.create();
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
				.setHeadless(resolveHeadlessFlag())
				.setSlowMo(resolveSlowMoMs()));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
		page = context.newPage();
		page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

		initReport();
	}

	@After
	public void tearDown() {
		printFinalReport();
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
		executeStep("Login", this::loginWithGoogleAndValidateDashboard);
		executeStep("Mi Negocio menu", this::openMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::openAdministrarNegociosAndValidateSections);
		executeStep("Información General", this::validateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		executeStep("Tus Negocios", this::validateTusNegocios);
		executeStep("Términos y Condiciones", this::validateTerminosYCondiciones);
		executeStep("Política de Privacidad", this::validatePoliticaPrivacidad);

		List<String> failedSteps = new ArrayList<>();
		for (Map.Entry<String, String> entry : finalReport.entrySet()) {
			if (!"PASS".equals(entry.getValue())) {
				failedSteps.add(entry.getKey() + " -> " + entry.getValue());
			}
		}

		Assert.assertTrue(
				"One or more SaleADS workflow validations failed:\n" + String.join("\n", failedSteps),
				failedSteps.isEmpty());
	}

	private void loginWithGoogleAndValidateDashboard() throws Exception {
		String targetUrl = resolveBaseUrl();
		page.navigate(targetUrl);
		waitForUi();

		Locator loginButton = waitForAnyVisible("Google login button",
				page.getByText(Pattern.compile("(?iu)(Sign in with Google|Continue with Google|Iniciar sesi[oó]n con Google|Continuar con Google)")),
				page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*Google.*"))),
				page.getByText(Pattern.compile("(?iu)Google")));

		Page popup = clickAndCapturePotentialNewPage(loginButton);
		selectGoogleAccountIfVisible(popup);

		page.bringToFront();
		waitForUi();

		waitForAnyVisible("main application interface",
				page.locator("aside"),
				page.locator("nav"),
				page.getByText(Pattern.compile("(?iu)Negocio")));
		assertTextVisible("left sidebar navigation", Pattern.compile("(?iu)Negocio"));

		screenshot(page, "01-dashboard-loaded.png", true);
	}

	private void openMiNegocioMenu() throws Exception {
		Locator negocioSection = waitForAnyVisible("Negocio section", sidebarText("(?iu)\\bNegocio\\b"),
				page.getByText(Pattern.compile("(?iu)\\bNegocio\\b")));
		clickAndWait(negocioSection);

		Locator miNegocio = waitForAnyVisible("Mi Negocio option", sidebarText("(?iu)Mi\\s+Negocio"),
				page.getByText(Pattern.compile("(?iu)Mi\\s+Negocio")));
		clickAndWait(miNegocio);

		assertTextVisible("Agregar Negocio menu option", Pattern.compile("(?iu)Agregar\\s+Negocio"));
		assertTextVisible("Administrar Negocios menu option", Pattern.compile("(?iu)Administrar\\s+Negocios"));

		screenshot(page, "02-mi-negocio-menu-expanded.png", false);
	}

	private void validateAgregarNegocioModal() throws Exception {
		expandMiNegocioIfCollapsed();
		Locator agregarNegocio = waitForAnyVisible("Agregar Negocio", sidebarText("(?iu)^\\s*Agregar\\s+Negocio\\s*$"),
				page.getByText(Pattern.compile("(?iu)^\\s*Agregar\\s+Negocio\\s*$")));
		clickAndWait(agregarNegocio);

		assertTextVisible("Crear Nuevo Negocio modal title", Pattern.compile("(?iu)Crear\\s+Nuevo\\s+Negocio"));
		assertTextVisible("Nombre del Negocio field label", Pattern.compile("(?iu)Nombre\\s+del\\s+Negocio"));
		assertTextVisible("Business quota text", Pattern.compile("(?iu)Tienes\\s*2\\s*de\\s*3\\s*negocios"));

		Locator cancelarButton = waitForAnyVisible("Cancelar button",
				page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
				page.getByText(Pattern.compile("(?iu)^\\s*Cancelar\\s*$")));
		waitForAnyVisible("Crear Negocio button",
				page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")),
				page.getByText(Pattern.compile("(?iu)Crear\\s+Negocio")));

		Locator negocioInput = waitForAnyVisible("Nombre del Negocio input",
				page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Nombre\\s+del\\s+Negocio"))),
				page.locator("input[placeholder*='Negocio' i]"),
				page.locator("input[name*='negocio' i]"));
		negocioInput.fill("Negocio Prueba Automatización");
		waitForUi();

		screenshot(page, "03-agregar-negocio-modal.png", false);
		clickAndWait(cancelarButton);
	}

	private void openAdministrarNegociosAndValidateSections() throws Exception {
		expandMiNegocioIfCollapsed();
		Locator administrarNegocios = waitForAnyVisible("Administrar Negocios",
				sidebarText("(?iu)^\\s*Administrar\\s+Negocios\\s*$"),
				page.getByText(Pattern.compile("(?iu)^\\s*Administrar\\s+Negocios\\s*$")));
		clickAndWait(administrarNegocios);

		assertTextVisible("Información General section", Pattern.compile("(?iu)Informaci[oó]n\\s+General"));
		assertTextVisible("Detalles de la Cuenta section", Pattern.compile("(?iu)Detalles\\s+de\\s+la\\s+Cuenta"));
		assertTextVisible("Tus Negocios section", Pattern.compile("(?iu)Tus\\s+Negocios"));
		assertTextVisible("Sección Legal section", Pattern.compile("(?iu)Secci[oó]n\\s+Legal"));

		screenshot(page, "04-administrar-negocios-page-full.png", true);
	}

	private void validateInformacionGeneral() {
		assertTextVisible("User email", Pattern.compile(Pattern.quote(LOGIN_ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE));
		assertTextVisible("BUSINESS PLAN", Pattern.compile("(?iu)BUSINESS\\s+PLAN"));
		assertTextVisible("Cambiar Plan button", Pattern.compile("(?iu)Cambiar\\s+Plan"));

		String bodyText = page.locator("body").innerText();
		Assert.assertTrue("User name should be visible in Información General.",
				bodyText.matches("(?s).*(Nombre|Usuario|Perfil|Juan).*"));
	}

	private void validateDetallesCuenta() {
		assertTextVisible("Cuenta creada", Pattern.compile("(?iu)Cuenta\\s+creada"));
		assertTextVisible("Estado activo", Pattern.compile("(?iu)Estado\\s+activo"));
		assertTextVisible("Idioma seleccionado", Pattern.compile("(?iu)Idioma\\s+seleccionado"));
	}

	private void validateTusNegocios() {
		assertTextVisible("Tus Negocios heading", Pattern.compile("(?iu)Tus\\s+Negocios"));
		assertTextVisible("Agregar Negocio button", Pattern.compile("(?iu)Agregar\\s+Negocio"));
		assertTextVisible("Business quota", Pattern.compile("(?iu)Tienes\\s*2\\s*de\\s*3\\s*negocios"));

		Locator businessListCandidates = page.locator("table tbody tr, ul li, [class*='business'], [data-testid*='business']");
		Assert.assertTrue("Expected business list to be visible.", businessListCandidates.count() > 0);
	}

	private void validateTerminosYCondiciones() throws Exception {
		validateLegalDocument("Términos y Condiciones", Pattern.compile("(?iu)T[eé]rminos\\s+y\\s+Condiciones"),
				"05-terminos-y-condiciones.png");
	}

	private void validatePoliticaPrivacidad() throws Exception {
		validateLegalDocument("Política de Privacidad", Pattern.compile("(?iu)Pol[ií]tica\\s+de\\s+Privacidad"),
				"06-politica-de-privacidad.png");
	}

	private void validateLegalDocument(String linkText, Pattern headingPattern, String screenshotName) throws Exception {
		Locator legalLink = waitForAnyVisible(linkText + " link", page.getByText(headingPattern),
				sidebarText(headingPattern.pattern()));
		legalLink.scrollIntoViewIfNeeded();

		String previousAppUrl = page.url();
		Page legalPage = clickAndCapturePotentialNewPage(legalLink);
		legalPage.bringToFront();
		legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		waitForUi(legalPage);

		waitForAnyVisibleOnPage(legalPage, linkText + " heading", legalPage.getByText(headingPattern),
				legalPage.getByRole(com.microsoft.playwright.options.AriaRole.HEADING,
						new Page.GetByRoleOptions().setName(headingPattern)));
		String legalBodyText = legalPage.locator("body").innerText();
		Assert.assertTrue("Expected legal content text for " + linkText + " to be visible.",
				legalBodyText != null && legalBodyText.trim().length() > 120);

		screenshot(legalPage, screenshotName, true);
		legalUrls.put(linkText, legalPage.url());

		if (legalPage != page) {
			legalPage.close();
			page.bringToFront();
			waitForUi();
		} else if (!previousAppUrl.equals(page.url())) {
			page.goBack();
			waitForUi();
		}
	}

	private void expandMiNegocioIfCollapsed() {
		Locator administrarVisible = sidebarText("(?iu)^\\s*Administrar\\s+Negocios\\s*$");
		if (!isVisible(administrarVisible)) {
			Locator miNegocio = waitForAnyVisible("Mi Negocio option", sidebarText("(?iu)Mi\\s+Negocio"),
					page.getByText(Pattern.compile("(?iu)Mi\\s+Negocio")));
			clickAndWait(miNegocio);
		}
	}

	private Page clickAndCapturePotentialNewPage(Locator clickTarget) {
		Page popup = null;
		try {
			popup = context.waitForPage(() -> clickTarget.first().click(),
					new BrowserContext.WaitForPageOptions().setTimeout(6000));
		} catch (PlaywrightException ignored) {
			// Link might open in the same tab. The click already happened in callback.
		}

		waitForUi();
		if (popup != null) {
			try {
				popup.waitForLoadState(LoadState.DOMCONTENTLOADED);
			} catch (PlaywrightException ignored) {
				// Continue and validate with best effort.
			}
			return popup;
		}

		return page;
	}

	private void selectGoogleAccountIfVisible(Page authPage) {
		Page candidatePage = authPage != null ? authPage : page;
		candidatePage.bringToFront();
		waitForUi(candidatePage);

		Locator accountEmailOption = candidatePage.getByText(Pattern.compile(Pattern.quote(LOGIN_ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE));
		if (isVisible(accountEmailOption)) {
			accountEmailOption.first().click();
			waitForUi(candidatePage);
		}

		Locator continueButton = candidatePage.getByText(Pattern.compile("(?iu)^\\s*(Continue|Continuar|Next|Siguiente)\\s*$"));
		if (isVisible(continueButton)) {
			continueButton.first().click();
			waitForUi(candidatePage);
		}
	}

	private void executeStep(String reportField, StepAction stepAction) {
		try {
			stepAction.run();
			finalReport.put(reportField, "PASS");
		} catch (Throwable throwable) {
			finalReport.put(reportField, "FAIL - " + throwable.getMessage());
		}
	}

	private void initReport() {
		finalReport.put("Login", "NOT_EXECUTED");
		finalReport.put("Mi Negocio menu", "NOT_EXECUTED");
		finalReport.put("Agregar Negocio modal", "NOT_EXECUTED");
		finalReport.put("Administrar Negocios view", "NOT_EXECUTED");
		finalReport.put("Información General", "NOT_EXECUTED");
		finalReport.put("Detalles de la Cuenta", "NOT_EXECUTED");
		finalReport.put("Tus Negocios", "NOT_EXECUTED");
		finalReport.put("Términos y Condiciones", "NOT_EXECUTED");
		finalReport.put("Política de Privacidad", "NOT_EXECUTED");
	}

	private void printFinalReport() {
		System.out.println("===== SaleADS Mi Negocio Final Report =====");
		for (Map.Entry<String, String> entry : finalReport.entrySet()) {
			System.out.printf("%s: %s%n", entry.getKey(), entry.getValue());
		}

		if (!legalUrls.isEmpty()) {
			System.out.println("----- Legal URLs -----");
			for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.printf("%s URL: %s%n", entry.getKey(), entry.getValue());
			}
		}

		System.out.println("Evidence directory: " + screenshotsDir.toAbsolutePath());
		System.out.println("===========================================");
	}

	private String resolveBaseUrl() {
		String systemProperty = System.getProperty("saleads.url");
		if (systemProperty != null && !systemProperty.isBlank()) {
			return systemProperty;
		}

		String envVar = System.getenv("SALEADS_URL");
		if (envVar != null && !envVar.isBlank()) {
			return envVar;
		}

		throw new IllegalStateException(
				"No SaleADS URL provided. Set -Dsaleads.url=<login-page-url> or SALEADS_URL to run this test in any environment.");
	}

	private boolean resolveHeadlessFlag() {
		String value = System.getProperty("headless", System.getenv().getOrDefault("HEADLESS", "true"));
		return !"false".equalsIgnoreCase(value);
	}

	private double resolveSlowMoMs() {
		String value = System.getProperty("slowmo.ms", System.getenv().getOrDefault("PLAYWRIGHT_SLOWMO_MS", "250"));
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return 250;
		}
	}

	private void waitForUi() {
		waitForUi(page);
	}

	private void waitForUi(Page targetPage) {
		targetPage.waitForTimeout(800);
		try {
			targetPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
			// Single page updates may not reach network idle reliably.
		}
		targetPage.waitForTimeout(500);
	}

	private void clickAndWait(Locator locator) {
		locator.first().scrollIntoViewIfNeeded();
		locator.first().click();
		waitForUi();
	}

	private Locator waitForAnyVisible(String description, Locator... candidates) {
		return waitForAnyVisibleOnPage(page, description, candidates);
	}

	private Locator waitForAnyVisibleOnPage(Page targetPage, String description, Locator... candidates) {
		for (Locator candidate : candidates) {
			try {
				candidate.first().waitFor(new Locator.WaitForOptions()
						.setState(WaitForSelectorState.VISIBLE)
						.setTimeout((double) DEFAULT_TIMEOUT_MS));
				return candidate.first();
			} catch (PlaywrightException ignored) {
				// Try the next locator candidate.
			}
		}
		throw new AssertionError("Unable to find visible element for: " + description + " on URL: " + targetPage.url());
	}

	private void assertTextVisible(String description, Pattern pattern) {
		waitForAnyVisible(description, page.getByText(pattern));
	}

	private boolean isVisible(Locator locator) {
		try {
			return locator.first().isVisible();
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private Locator sidebarText(String regexPattern) {
		return page.locator("aside").getByText(Pattern.compile(regexPattern));
	}

	private void screenshot(Page targetPage, String name, boolean fullPage) throws Exception {
		Path screenshotPath = screenshotsDir.resolve(name);
		targetPage.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
