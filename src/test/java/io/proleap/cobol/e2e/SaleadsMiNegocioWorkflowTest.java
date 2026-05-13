package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, String> stepReport = new LinkedHashMap<>();
	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		initializeReport();

		final boolean enabled = Boolean.parseBoolean(readConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Skipping SaleADS E2E. Set saleads.e2e.enabled=true (or SALEADS_E2E_ENABLED=true).", enabled);

		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", null);
		Assert.assertNotNull("Missing login URL. Set saleads.login.url or SALEADS_LOGIN_URL.", loginUrl);
		Assert.assertFalse("Empty login URL. Set saleads.login.url or SALEADS_LOGIN_URL.", loginUrl.trim().isEmpty());

		evidenceDir = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(TS_FORMATTER));
		Files.createDirectories(evidenceDir);

		playwright = Playwright.create();
		final boolean headless = Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"));
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 900));
		page = context.newPage();

		page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
		page.waitForLoadState(LoadState.NETWORKIDLE);
	}

	@After
	public void tearDown() throws IOException {
		writeReport();

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
	public void saleadsMiNegocioWorkflow() {
		final boolean loginOk = validateStep("Login", this::stepLoginWithGoogle);
		final boolean menuOk = loginOk && validateStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		final boolean modalOk = menuOk && validateStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		final boolean adminOk = modalOk && validateStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		final boolean infoOk = adminOk && validateStep("Información General", this::stepValidateInformacionGeneral);
		final boolean detailsOk = infoOk && validateStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		final boolean businessOk = detailsOk && validateStep("Tus Negocios", this::stepValidateTusNegocios);
		final boolean termsOk = businessOk && validateStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones", "terminos-y-condiciones.png"));
		final boolean privacyOk = termsOk && validateStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad", "politica-de-privacidad.png"));

		final String reportOutput = renderReport();
		System.out.println(reportOutput);

		Assert.assertTrue("Workflow did not complete successfully.\n" + reportOutput, loginOk && menuOk && modalOk && adminOk && infoOk && detailsOk && businessOk && termsOk && privacyOk);
	}

	private void stepLoginWithGoogle() {
		final Locator loginButton = firstVisible(
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(sign in with google|continuar con google|iniciar sesion con google|google)"))),
				page.getByText(Pattern.compile("(?i)(sign in with google|continuar con google|iniciar sesion con google)")));
		clickAndSettle(loginButton, page);

		selectGoogleAccountIfPrompted();
		waitForMainInterface();
		capture("dashboard-loaded.png", false, page);
	}

	private void stepOpenMiNegocioMenu() {
		clickAndSettle(firstVisible(page.getByText(Pattern.compile("(?i)^negocio$")), page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Negocio"))), page);
		clickAndSettle(firstVisible(page.getByText(Pattern.compile("(?i)^mi negocio$")), page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Mi Negocio"))), page);

		assertVisible(page.getByText(Pattern.compile("(?i)^agregar negocio$")), "Agregar Negocio should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)^administrar negocios$")), "Administrar Negocios should be visible.");
		capture("mi-negocio-menu-expanded.png", false, page);
	}

	private void stepValidateAgregarNegocioModal() {
		clickAndSettle(firstVisible(page.getByText(Pattern.compile("(?i)^agregar negocio$"))), page);

		assertVisible(page.getByText(Pattern.compile("(?i)^crear nuevo negocio$")), "Modal title 'Crear Nuevo Negocio' should be visible.");
		assertVisible(firstVisible(
				page.getByLabel(Pattern.compile("(?i)nombre del negocio")),
				page.getByPlaceholder(Pattern.compile("(?i)nombre del negocio"))),
				"Input 'Nombre del Negocio' should exist.");
		assertVisible(page.getByText(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios")), "Business quota text should be visible.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^cancelar$"))), "Cancelar button should be visible.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^crear negocio$"))), "Crear Negocio button should be visible.");

		final Locator businessNameInput = firstVisible(
				page.getByLabel(Pattern.compile("(?i)nombre del negocio")),
				page.getByPlaceholder(Pattern.compile("(?i)nombre del negocio")));
		businessNameInput.fill("Negocio Prueba Automatizacion");
		clickAndSettle(firstVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^cancelar$")))), page);

		capture("agregar-negocio-modal.png", false, page);
	}

	private void stepOpenAdministrarNegocios() {
		ensureMiNegocioExpanded();
		clickAndSettle(firstVisible(page.getByText(Pattern.compile("(?i)^administrar negocios$"))), page);

		assertVisible(page.getByText(Pattern.compile("(?i)^informacion general$")), "Section 'Información General' should exist.");
		assertVisible(page.getByText(Pattern.compile("(?i)^detalles de la cuenta$")), "Section 'Detalles de la Cuenta' should exist.");
		assertVisible(page.getByText(Pattern.compile("(?i)^tus negocios$")), "Section 'Tus Negocios' should exist.");
		assertVisible(page.getByText(Pattern.compile("(?i)^seccion legal$")), "Section 'Sección Legal' should exist.");
		capture("administrar-negocios-full-page.png", true, page);
	}

	private void stepValidateInformacionGeneral() {
		assertVisible(page.getByText(Pattern.compile("(?i)^informacion general$")), "Información General header should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)business plan")), "BUSINESS PLAN text should be visible.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^cambiar plan$"))), "Cambiar Plan button should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")), "A user email should be visible.");
		assertVisible(firstVisible(
				page.getByText(Pattern.compile("(?i)(nombre|usuario|user name)")),
				page.getByText(Pattern.compile("(?i)hola\\s+"))), "User name information should be visible.");
	}

	private void stepValidateDetallesCuenta() {
		assertVisible(page.getByText(Pattern.compile("(?i)^cuenta creada$")), "'Cuenta creada' should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)^estado activo$")), "'Estado activo' should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)^idioma seleccionado$")), "'Idioma seleccionado' should be visible.");
	}

	private void stepValidateTusNegocios() {
		assertVisible(page.getByText(Pattern.compile("(?i)^tus negocios$")), "Business list section should be visible.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^agregar negocio$"))), "Agregar Negocio button should exist.");
		assertVisible(page.getByText(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios")), "Business quota text should be visible.");
	}

	private void stepValidateLegalLink(final String linkText, final String screenshotFileName) {
		final Page appPage = page;
		final Set<Page> knownPages = Set.copyOf(context.pages());

		clickAndSettle(firstVisible(appPage.getByText(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$"))), appPage);
		appPage.waitForTimeout(1500);

		Page legalPage = appPage;
		for (final Page candidate : context.pages()) {
			if (!knownPages.contains(candidate)) {
				legalPage = candidate;
				break;
			}
		}

		legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		legalPage.waitForLoadState(LoadState.NETWORKIDLE);

		assertVisible(legalPage.getByText(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$")), "Legal heading should be visible: " + linkText);
		assertVisible(legalPage.locator("p, li, article"), "Legal content text should be visible for: " + linkText);
		capture(screenshotFileName, true, legalPage);

		stepReport.put(linkText, "PASS (" + legalPage.url() + ")");

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} else {
			appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			appPage.waitForLoadState(LoadState.NETWORKIDLE);
		}
	}

	private void selectGoogleAccountIfPrompted() {
		for (final Page candidate : context.pages()) {
			try {
				final Locator accountOption = candidate.getByText(GOOGLE_ACCOUNT_EMAIL, new Page.GetByTextOptions().setExact(true));
				if (accountOption.count() > 0 && accountOption.first().isVisible()) {
					accountOption.first().click();
					candidate.waitForLoadState(LoadState.DOMCONTENTLOADED);
					return;
				}
			} catch (final Exception ignored) {
				// ignore transient page state while popup loads/closes
			}
		}
	}

	private void waitForMainInterface() {
		assertVisible(firstVisible(
				page.getByRole(AriaRole.NAVIGATION),
				page.locator("aside"),
				page.getByText(Pattern.compile("(?i)negocio"))),
				"Sidebar navigation should be visible.");
	}

	private void ensureMiNegocioExpanded() {
		if (page.getByText(Pattern.compile("(?i)^administrar negocios$")).count() > 0 && page.getByText(Pattern.compile("(?i)^administrar negocios$")).first().isVisible()) {
			return;
		}

		clickAndSettle(firstVisible(page.getByText(Pattern.compile("(?i)^mi negocio$"))), page);
	}

	private boolean validateStep(final String reportField, final StepAction action) {
		try {
			action.run();
			if (!stepReport.containsKey(reportField) || !stepReport.get(reportField).startsWith("PASS")) {
				stepReport.put(reportField, "PASS");
			}
			return true;
		} catch (final Throwable throwable) {
			stepReport.put(reportField, "FAIL (" + throwable.getMessage() + ")");
			return false;
		}
	}

	private void clickAndSettle(final Locator locator, final Page targetPage) {
		locator.first().click();
		targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		targetPage.waitForTimeout(500);
	}

	private Locator firstVisible(final Locator... locators) {
		for (final Locator locator : locators) {
			try {
				if (locator.count() > 0 && locator.first().isVisible()) {
					return locator.first();
				}
			} catch (final Exception ignored) {
				// keep trying fallback locators
			}
		}

		throw new AssertionError("No visible locator matched.");
	}

	private void assertVisible(final Locator locator, final String message) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
			Assert.assertTrue(message, locator.first().isVisible());
		} catch (final RuntimeException ex) {
			throw new AssertionError(message + " Details: " + ex.getMessage(), ex);
		}
	}

	private void capture(final String fileName, final boolean fullPage, final Page targetPage) {
		targetPage.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private String readConfig(final String systemProperty, final String envName, final String defaultValue) {
		final String fromProperty = System.getProperty(systemProperty);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}

		final String fromEnv = System.getenv(envName);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv.trim();
		}

		return defaultValue;
	}

	private void initializeReport() {
		stepReport.clear();
		stepReport.put("Login", "SKIPPED");
		stepReport.put("Mi Negocio menu", "SKIPPED");
		stepReport.put("Agregar Negocio modal", "SKIPPED");
		stepReport.put("Administrar Negocios view", "SKIPPED");
		stepReport.put("Información General", "SKIPPED");
		stepReport.put("Detalles de la Cuenta", "SKIPPED");
		stepReport.put("Tus Negocios", "SKIPPED");
		stepReport.put("Términos y Condiciones", "SKIPPED");
		stepReport.put("Política de Privacidad", "SKIPPED");
	}

	private String renderReport() {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio Workflow - Final Report").append(System.lineSeparator());
		for (final Map.Entry<String, String> entry : stepReport.entrySet()) {
			reportBuilder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}
		if (evidenceDir != null) {
			reportBuilder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		}
		return reportBuilder.toString();
	}

	private void writeReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), renderReport());
	}

	@FunctionalInterface
	private interface StepAction {
		void run();
	}
}
