package io.proleap.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
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

/**
 * End-to-end automation for SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * Runtime configuration:
 * </p>
 * <ul>
 * <li>System property or environment variable for entry URL:
 * <code>saleads.entry.url</code> / <code>SALEADS_ENTRY_URL</code></li>
 * <li>Optional Google account email: <code>saleads.google.account</code> /
 * <code>SALEADS_GOOGLE_ACCOUNT</code></li>
 * <li>Optional headless toggle: <code>saleads.headless</code> (default:
 * <code>true</code>)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Términos y Condiciones";
	private static final String STEP_PRIVACY = "Política de Privacidad";

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private Path evidenceDir;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String entryUrl = readSetting("saleads.entry.url", "SALEADS_ENTRY_URL");
		Assume.assumeTrue("Set saleads.entry.url or SALEADS_ENTRY_URL to the current SaleADS login page.", entryUrl != null);

		playwright = Playwright.create();
		final boolean headless = Boolean.parseBoolean(readSetting("saleads.headless", "SALEADS_HEADLESS", "true"));
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
		appPage = context.newPage();

		evidenceDir = Files.createDirectories(Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));

		appPage.navigate(entryUrl);
		waitForUi(appPage);
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
		runStep(STEP_LOGIN, this::executeLoginWithGoogle);
		runStep(STEP_MENU, this::expandMiNegocioMenu);
		runStep(STEP_MODAL, this::validateAgregarNegocioModal);
		runStep(STEP_ADMIN_VIEW, this::openAdministrarNegocios);
		runStep(STEP_INFO_GENERAL, this::validateInformacionGeneral);
		runStep(STEP_ACCOUNT_DETAILS, this::validateDetallesCuenta);
		runStep(STEP_BUSINESSES, this::validateTusNegocios);
		runStep(STEP_TERMS, this::validateTerminosYCondiciones);
		runStep(STEP_PRIVACY, this::validatePoliticaPrivacidad);

		final boolean allPassed = stepResults.values().stream().allMatch(step -> step.passed);
		assertTrue("One or more SaleADS workflow validations failed. Review the report in test output.", allPassed);
	}

	private void executeLoginWithGoogle() {
		final String googleAccount = readSetting("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);

		final Locator loginButton = findFirstVisible("Login button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern
						.compile("(?iu).*(sign in with google|continuar con google|iniciar sesi[oó]n con google).*"))),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*(google).*"))),
				appPage.getByText(Pattern.compile("(?iu).*(sign in with google|continuar con google).*")));

		clickAndWait(loginButton);
		chooseGoogleAccountIfPrompted(googleAccount);
		waitForMainApplication();

		assertVisible("Main application interface", appPage.locator("main"));
		assertVisible("Left sidebar navigation", findSidebarLocator());
		captureScreenshot("01-dashboard-loaded", true);
	}

	private void expandMiNegocioMenu() {
		final Locator negocioSection = findFirstVisible("Negocio section",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*negocio.*"))),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*negocio.*"))),
				appPage.getByText(Pattern.compile("(?iu).*negocio.*")));
		clickAndWait(negocioSection);

		final Locator miNegocioOption = findFirstVisible("Mi Negocio option",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*mi negocio.*"))),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*mi negocio.*"))),
				appPage.getByText(Pattern.compile("(?iu).*mi negocio.*")));
		clickAndWait(miNegocioOption);

		assertVisible("Agregar Negocio option", appPage.getByText(Pattern.compile("(?iu).*agregar negocio.*")));
		assertVisible("Administrar Negocios option",
				appPage.getByText(Pattern.compile("(?iu).*administrar negocios.*")));
		captureScreenshot("02-mi-negocio-menu-expanded", false);
	}

	private void validateAgregarNegocioModal() {
		clickAndWait(findFirstVisible("Agregar Negocio option",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*agregar negocio.*"))),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*agregar negocio.*"))),
				appPage.getByText(Pattern.compile("(?iu).*agregar negocio.*"))));

		final Locator modalTitle = appPage.getByText(Pattern.compile("(?iu).*crear nuevo negocio.*"));
		assertVisible("Modal title Crear Nuevo Negocio", modalTitle);
		assertVisible("Nombre del Negocio field",
				appPage.getByRole(AriaRole.TEXTBOX,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*nombre del negocio.*"))));
		assertVisible("Business limit text", appPage.getByText(Pattern.compile("(?iu).*tienes\\s*2\\s*de\\s*3\\s*negocios.*")));
		assertVisible("Cancelar button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*cancelar.*"))));
		assertVisible("Crear Negocio button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*crear negocio.*"))));
		captureScreenshot("03-agregar-negocio-modal", false);

		final Locator nombreNegocio = appPage.getByRole(AriaRole.TEXTBOX,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*nombre del negocio.*"))).first();
		nombreNegocio.click();
		nombreNegocio.fill("Negocio Prueba Automatización");
		clickAndWait(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*cancelar.*"))));
	}

	private void openAdministrarNegocios() {
		ensureMiNegocioMenuExpanded();
		clickAndWait(findFirstVisible("Administrar Negocios option",
				appPage.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*administrar negocios.*"))),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*administrar negocios.*"))),
				appPage.getByText(Pattern.compile("(?iu).*administrar negocios.*"))));

		assertVisible("Información General section", appPage.getByText(Pattern.compile("(?iu).*informaci[oó]n general.*")));
		assertVisible("Detalles de la Cuenta section",
				appPage.getByText(Pattern.compile("(?iu).*detalles de la cuenta.*")));
		assertVisible("Tus Negocios section", appPage.getByText(Pattern.compile("(?iu).*tus negocios.*")));
		assertVisible("Sección Legal section", appPage.getByText(Pattern.compile("(?iu).*(secci[oó]n legal|seccion legal).*")));
		captureScreenshot("04-administrar-negocios-page", true);
	}

	private void validateInformacionGeneral() {
		assertVisible("User email", appPage.getByText(Pattern.compile("(?iu).*[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}.*")));
		assertVisible("BUSINESS PLAN text", appPage.getByText(Pattern.compile("(?iu).*business plan.*")));
		assertVisible("Cambiar Plan button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*cambiar plan.*"))));

		final boolean userNameVisible = waitForVisible(
				appPage.getByText(Pattern.compile("(?iu).*(nombre|usuario|user name|perfil).*")), 5_000);
		assertTrue("Expected user name indicator to be visible in Información General section.", userNameVisible);
	}

	private void validateDetallesCuenta() {
		assertVisible("'Cuenta creada' label", appPage.getByText(Pattern.compile("(?iu).*cuenta creada.*")));
		assertVisible("'Estado activo' label", appPage.getByText(Pattern.compile("(?iu).*(estado activo|estado\\s*:?.*activo).*")));
		assertVisible("'Idioma seleccionado' label", appPage.getByText(Pattern.compile("(?iu).*idioma seleccionado.*")));
	}

	private void validateTusNegocios() {
		assertVisible("Tus Negocios section", appPage.getByText(Pattern.compile("(?iu).*tus negocios.*")));
		assertVisible("Agregar Negocio button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*agregar negocio.*"))));
		assertVisible("Business count text", appPage.getByText(Pattern.compile("(?iu).*tienes\\s*2\\s*de\\s*3\\s*negocios.*")));
	}

	private void validateTerminosYCondiciones() {
		termsUrl = openAndValidateLegalPage("Términos y Condiciones", "(?iu).*(t[eé]rminos y condiciones|terminos y condiciones).*",
				"05-terminos-y-condiciones");
	}

	private void validatePoliticaPrivacidad() {
		privacyUrl = openAndValidateLegalPage("Política de Privacidad", "(?iu).*(pol[ií]tica de privacidad|politica de privacidad).*",
				"06-politica-privacidad");
	}

	private String openAndValidateLegalPage(final String linkText, final String headingPattern, final String screenshotName) {
		final Locator link = findFirstVisible(linkText + " link",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile(headingPattern))),
				appPage.getByText(Pattern.compile(headingPattern)));

		final int pageCountBefore = context.pages().size();
		link.first().click();
		waitForUi(appPage);
		appPage.waitForTimeout(1_000);

		Page legalPage = appPage;
		if (context.pages().size() > pageCountBefore) {
			legalPage = context.pages().get(context.pages().size() - 1);
			waitForUi(legalPage);
		}

		assertVisible(linkText + " heading", legalPage.getByText(Pattern.compile(headingPattern)));
		assertTrue("Expected legal content text to be visible for " + linkText + ".",
				waitForVisible(legalPage.locator("main, article, body").first(), 10_000));
		captureScreenshot(legalPage, screenshotName, true);

		final String finalUrl = legalPage.url();
		System.out.println(linkText + " final URL: " + finalUrl);

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void chooseGoogleAccountIfPrompted(final String accountEmail) {
		Page googlePage = null;
		for (int i = 0; i < 15; i++) {
			for (final Page candidate : context.pages()) {
				if (candidate.url() != null && candidate.url().contains("accounts.google.com")) {
					googlePage = candidate;
					break;
				}
			}
			if (googlePage != null) {
				break;
			}
			appPage.waitForTimeout(500);
		}

		if (googlePage == null) {
			return;
		}

		waitForUi(googlePage);
		final Locator accountOption = findFirstVisible("Google account option",
				googlePage.getByText(Pattern.compile("(?iu).*" + Pattern.quote(accountEmail) + ".*")),
				googlePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*" + Pattern.quote(accountEmail) + ".*"))),
				googlePage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*" + Pattern.quote(accountEmail) + ".*"))));
		accountOption.first().click();
		waitForUi(googlePage);
	}

	private void waitForMainApplication() {
		for (int i = 0; i < 90; i++) {
			for (final Page candidate : context.pages()) {
				if (isMainApplication(candidate)) {
					appPage = candidate;
					waitForUi(appPage);
					return;
				}
			}
			appPage.waitForTimeout(1_000);
		}
		throw new AssertionError("Main application interface did not load after Google login.");
	}

	private boolean isMainApplication(final Page candidate) {
		if (candidate.isClosed()) {
			return false;
		}
		try {
			return waitForVisible(candidate.locator("aside, nav").first(), 1_000)
					&& waitForVisible(candidate.getByText(Pattern.compile("(?iu).*negocio.*")).first(), 1_000);
		} catch (final PlaywrightException ex) {
			return false;
		}
	}

	private Locator findSidebarLocator() {
		return findFirstVisible("Sidebar", appPage.locator("aside"), appPage.locator("[data-testid*=sidebar]"),
				appPage.getByRole(AriaRole.NAVIGATION));
	}

	private void ensureMiNegocioMenuExpanded() {
		if (waitForVisible(appPage.getByText(Pattern.compile("(?iu).*administrar negocios.*")), 1_000)) {
			return;
		}

		clickAndWait(findFirstVisible("Mi Negocio",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*mi negocio.*"))),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*mi negocio.*"))),
				appPage.getByText(Pattern.compile("(?iu).*mi negocio.*"))));
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, StepResult.pass());
		} catch (final Throwable error) {
			stepResults.put(stepName, StepResult.fail(error.getMessage()));
			captureFailureEvidence(stepName);
		}
	}

	private void captureFailureEvidence(final String stepName) {
		final String sanitized = stepName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
		try {
			captureScreenshot("fail-" + sanitized, true);
		} catch (final Throwable ignored) {
			// best effort evidence collection
		}
	}

	private void clickAndWait(final Locator locator) {
		locator.first().click();
		waitForUi(appPage);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// best effort wait
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
		} catch (final PlaywrightException ignored) {
			// some pages never reach network idle; pause briefly instead
		}
		page.waitForTimeout(700);
	}

	private void assertVisible(final String label, final Locator locator) {
		assertTrue("Expected to find: " + label, waitForVisible(locator, 15_000));
	}

	private boolean waitForVisible(final Locator locator, final int timeoutMs) {
		final long end = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < end) {
			try {
				if (locator.first().isVisible()) {
					return true;
				}
			} catch (final PlaywrightException ignored) {
				// retry until timeout
			}
			try {
				Thread.sleep(250);
			} catch (final InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	private Locator findFirstVisible(final String label, final Locator... candidates) {
		final long end = System.currentTimeMillis() + 15_000;
		while (System.currentTimeMillis() < end) {
			for (final Locator candidate : candidates) {
				try {
					if (candidate.first().isVisible()) {
						return candidate.first();
					}
				} catch (final PlaywrightException ignored) {
					// continue checking other candidates
				}
			}
			appPage.waitForTimeout(250);
		}
		throw new AssertionError("Could not find visible element for " + label + ".");
	}

	private void captureScreenshot(final String name, final boolean fullPage) {
		captureScreenshot(appPage, name, fullPage);
	}

	private void captureScreenshot(final Page targetPage, final String name, final boolean fullPage) {
		targetPage.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(name + ".png")).setFullPage(fullPage));
	}

	private void printFinalReport() {
		if (stepResults.isEmpty()) {
			return;
		}

		System.out.println("=== SaleADS Mi Negocio Full Workflow Report ===");
		printStepResult(STEP_LOGIN);
		printStepResult(STEP_MENU);
		printStepResult(STEP_MODAL);
		printStepResult(STEP_ADMIN_VIEW);
		printStepResult(STEP_INFO_GENERAL);
		printStepResult(STEP_ACCOUNT_DETAILS);
		printStepResult(STEP_BUSINESSES);
		printStepResult(STEP_TERMS);
		printStepResult(STEP_PRIVACY);
		System.out.println("Terms final URL: " + termsUrl);
		System.out.println("Privacy final URL: " + privacyUrl);
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private void printStepResult(final String stepName) {
		final StepResult result = stepResults.getOrDefault(stepName, StepResult.fail("Step not executed"));
		System.out.println(stepName + ": " + (result.passed ? "PASS" : "FAIL")
				+ (result.message == null ? "" : " - " + result.message));
	}

	private String readSetting(final String property, final String env) {
		return readSetting(property, env, null);
	}

	private String readSetting(final String property, final String env, final String defaultValue) {
		final String propertyValue = System.getProperty(property);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}

		final String envValue = System.getenv(env);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}

		return defaultValue;
	}

	@FunctionalInterface
	private interface StepAction {
		void run();
	}

	private static final class StepResult {
		private final boolean passed;
		private final String message;

		private StepResult(final boolean passed, final String message) {
			this.passed = passed;
			this.message = message;
		}

		private static StepResult pass() {
			return new StepResult(true, null);
		}

		private static StepResult fail(final String message) {
			return new StepResult(false, message == null ? "No error message available" : message);
		}
	}
}
