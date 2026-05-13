package io.proleap.cobol.e2e;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioWorkflowTest {

	private static final Pattern LOGIN_BUTTON_PATTERN = Pattern.compile("(?i)(google|sign in|iniciar sesi[oó]n|login)");
	private static final Pattern TERMS_PATTERN = Pattern.compile("(?i)t[eé]rminos y condiciones");
	private static final Pattern PRIVACY_PATTERN = Pattern.compile("(?i)pol[ií]tica de privacidad");

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private Path evidenceDir;

	private final Map<String, String> stepReport = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL or -Dsaleads.login.url before running this E2E flow.", isNotBlank(loginUrl));

		evidenceDir = Paths.get("target", "saleads-evidence", timestamp());
		Files.createDirectories(evidenceDir);

		playwright = Playwright.create();
		final boolean headless = Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"));
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
		appPage = context.newPage();
		appPage.navigate(loginUrl);
		waitForUiLoad(appPage);
	}

	@After
	public void tearDown() {
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
		runStep(STEP_LOGIN, this::loginWithGoogle);
		runStep(STEP_MI_NEGOCIO_MENU, this::openMiNegocioMenu);
		runStep(STEP_AGREGAR_MODAL, this::validateAgregarNegocioModal);
		runStep(STEP_ADMINISTRAR_VIEW, this::openAdministrarNegocios);
		runStep(STEP_INFO_GENERAL, this::validateInformacionGeneral);
		runStep(STEP_DETALLES_CUENTA, this::validateDetallesCuenta);
		runStep(STEP_TUS_NEGOCIOS, this::validateTusNegocios);
		runStep(STEP_TERMINOS, this::validateTerminosYCondiciones);
		runStep(STEP_PRIVACIDAD, this::validatePoliticaPrivacidad);

		final String report = buildReport();
		System.out.println(report);

		if (stepReport.values().stream().anyMatch(value -> value.startsWith("FAIL"))) {
			fail(report);
		}
	}

	private void loginWithGoogle() {
		if (isMainAppVisible()) {
			captureScreenshot(appPage, "01-dashboard-already-logged.png", false);
			return;
		}

		final Locator loginButton = appPage.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN)).first();
		loginButton.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20_000));

		Page authPage = null;
		try {
			authPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(7_000), loginButton::click);
		} catch (TimeoutError ignored) {
			loginButton.click();
		}
		waitForUiLoad(appPage);

		final Page activeAuthPage = authPage != null ? authPage : appPage;
		selectGoogleAccountIfVisible(activeAuthPage, "juanlucasbarbiergarzon@gmail.com");

		waitForMainApp();
		captureScreenshot(appPage, "01-dashboard-loaded.png", false);
		requireVisible(appPage.getByText("Negocio", new Page.GetByTextOptions().setExact(false)).first(),
				"Left sidebar must be visible.");
	}

	private void openMiNegocioMenu() {
		clickText(appPage, "Negocio");
		clickText(appPage, "Mi Negocio");
		requireVisible(appPage.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)).first(),
				"'Agregar Negocio' should be visible.");
		requireVisible(appPage.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)).first(),
				"'Administrar Negocios' should be visible.");
		captureScreenshot(appPage, "02-mi-negocio-expanded.png", false);
	}

	private void validateAgregarNegocioModal() {
		clickText(appPage, "Agregar Negocio");

		final Locator modalTitle = appPage.getByText("Crear Nuevo Negocio", new Page.GetByTextOptions().setExact(false)).first();
		requireVisible(modalTitle, "Modal title 'Crear Nuevo Negocio' must be visible.");
		requireVisible(appPage.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)).first(),
				"'Nombre del Negocio' input should exist.");
		requireVisible(appPage.getByText("Tienes 2 de 3 negocios", new Page.GetByTextOptions().setExact(false)).first(),
				"'Tienes 2 de 3 negocios' must be visible.");
		requireVisible(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first(),
				"'Cancelar' button must be present.");
		requireVisible(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")).first(),
				"'Crear Negocio' button must be present.");

		captureScreenshot(appPage, "03-agregar-negocio-modal.png", false);

		final Locator nombreNegocio = appPage.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)).first();
		nombreNegocio.fill("Negocio Prueba Automatizaci\u00f3n");
		clickRoleButton(appPage, "Cancelar");
		waitForUiLoad(appPage);
	}

	private void openAdministrarNegocios() {
		if (!appPage.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)).first().isVisible()) {
			clickText(appPage, "Mi Negocio");
		}

		clickText(appPage, "Administrar Negocios");
		waitForUiLoad(appPage);

		requireVisible(appPage.getByText("Informaci\u00f3n General", new Page.GetByTextOptions().setExact(false)).first(),
				"'Informaci\u00f3n General' section should exist.");
		requireVisible(appPage.getByText("Detalles de la Cuenta", new Page.GetByTextOptions().setExact(false)).first(),
				"'Detalles de la Cuenta' section should exist.");
		requireVisible(appPage.getByText("Tus Negocios", new Page.GetByTextOptions().setExact(false)).first(),
				"'Tus Negocios' section should exist.");
		requireVisible(appPage.getByText("Secci\u00f3n Legal", new Page.GetByTextOptions().setExact(false)).first(),
				"'Secci\u00f3n Legal' section should exist.");
		captureScreenshot(appPage, "04-administrar-negocios-page.png", true);
	}

	private void validateInformacionGeneral() {
		requireVisible(appPage.getByText("@", new Page.GetByTextOptions().setExact(false)).first(),
				"User email should be visible in Informaci\u00f3n General.");
		requireVisible(appPage.getByText("BUSINESS PLAN", new Page.GetByTextOptions().setExact(false)).first(),
				"'BUSINESS PLAN' text must be visible.");
		requireVisible(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")).first(),
				"'Cambiar Plan' button must be visible.");

		final Locator infoSection = appPage.getByText("Informaci\u00f3n General", new Page.GetByTextOptions().setExact(false)).first();
		requireVisible(infoSection, "Informaci\u00f3n General section should remain visible.");
	}

	private void validateDetallesCuenta() {
		requireVisible(appPage.getByText("Cuenta creada", new Page.GetByTextOptions().setExact(false)).first(),
				"'Cuenta creada' must be visible.");
		requireVisible(appPage.getByText("Estado activo", new Page.GetByTextOptions().setExact(false)).first(),
				"'Estado activo' must be visible.");
		requireVisible(appPage.getByText("Idioma seleccionado", new Page.GetByTextOptions().setExact(false)).first(),
				"'Idioma seleccionado' must be visible.");
	}

	private void validateTusNegocios() {
		requireVisible(appPage.getByText("Tus Negocios", new Page.GetByTextOptions().setExact(false)).first(),
				"'Tus Negocios' heading must be visible.");
		requireVisible(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")).first(),
				"'Agregar Negocio' button must exist in business section.");
		requireVisible(appPage.getByText("Tienes 2 de 3 negocios", new Page.GetByTextOptions().setExact(false)).first(),
				"'Tienes 2 de 3 negocios' should be visible in Tus Negocios.");
	}

	private void validateTerminosYCondiciones() {
		final Locator link = appPage.getByText(TERMS_PATTERN, new Page.GetByTextOptions().setExact(false)).first();
		requireVisible(link, "T\u00e9rminos y Condiciones link must be visible.");

		final NavigationResult result = navigateAndCaptureLegal(link, "05-terminos-y-condiciones.png", TERMS_PATTERN,
				"T\u00e9rminos y Condiciones");
		System.out.println("T\u00e9rminos y Condiciones URL: " + result.url);
	}

	private void validatePoliticaPrivacidad() {
		final Locator link = appPage.getByText(PRIVACY_PATTERN, new Page.GetByTextOptions().setExact(false)).first();
		requireVisible(link, "Pol\u00edtica de Privacidad link must be visible.");

		final NavigationResult result = navigateAndCaptureLegal(link, "06-politica-de-privacidad.png", PRIVACY_PATTERN,
				"Pol\u00edtica de Privacidad");
		System.out.println("Pol\u00edtica de Privacidad URL: " + result.url);
	}

	private NavigationResult navigateAndCaptureLegal(final Locator link, final String screenshotName,
			final Pattern titlePattern, final String readableName) {
		Page legalPage = appPage;
		boolean openedNewTab = false;

		try {
			legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(7_000), link::click);
			openedNewTab = true;
		} catch (TimeoutError ignored) {
			link.click();
		}

		waitForUiLoad(legalPage);
		requireVisible(legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(titlePattern)).first(),
				readableName + " heading must be visible.");
		requireVisible(legalPage.getByText(" ", new Page.GetByTextOptions().setExact(false)).first(),
				"Legal content text must be visible for " + readableName + ".");

		captureScreenshot(legalPage, screenshotName, true);
		final String legalUrl = legalPage.url();

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
		} else {
			appPage.goBack();
			waitForUiLoad(appPage);
		}

		return new NavigationResult(legalUrl);
	}

	private void runStep(final String stepName, final ThrowingRunnable stepAction) {
		try {
			stepAction.run();
			stepReport.put(stepName, "PASS");
		} catch (Throwable error) {
			stepReport.put(stepName, "FAIL - " + error.getMessage());
		}
	}

	private String buildReport() {
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio Workflow Report\n");
		reportBuilder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');

		for (Map.Entry<String, String> entry : stepReport.entrySet()) {
			reportBuilder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}
		return reportBuilder.toString();
	}

	private void waitForMainApp() {
		appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		requireVisible(appPage.getByText("Negocio", new Page.GetByTextOptions().setExact(false)).first(),
				"Main application sidebar was not rendered after login.");
	}

	private boolean isMainAppVisible() {
		try {
			return appPage.getByText("Negocio", new Page.GetByTextOptions().setExact(false)).first().isVisible();
		} catch (Exception ignored) {
			return false;
		}
	}

	private void selectGoogleAccountIfVisible(final Page page, final String email) {
		try {
			final Locator account = page.getByText(email, new Page.GetByTextOptions().setExact(false)).first();
			account.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5_000));
			account.click();
			waitForUiLoad(page);
		} catch (Exception ignored) {
			// If account chooser is not shown, continue with current auth state.
		}
	}

	private void clickText(final Page page, final String text) {
		final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
		requireVisible(locator, "Could not find visible text: " + text);
		locator.click();
		waitForUiLoad(page);
	}

	private void clickRoleButton(final Page page, final String name) {
		final Locator locator = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name)).first();
		requireVisible(locator, "Could not find button: " + name);
		locator.click();
		waitForUiLoad(page);
	}

	private void requireVisible(final Locator locator, final String errorMessage) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20_000));
		} catch (Exception error) {
			throw new AssertionError(errorMessage, error);
		}
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(750);
	}

	private void captureScreenshot(final Page page, final String fileName, final boolean fullPage) {
		final Path screenshotPath = evidenceDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private String readConfig(final String systemProperty, final String envVar) {
		return readConfig(systemProperty, envVar, null);
	}

	private String readConfig(final String systemProperty, final String envVar, final String fallback) {
		final String fromProperty = System.getProperty(systemProperty);
		if (isNotBlank(fromProperty)) {
			return fromProperty;
		}
		final String fromEnv = System.getenv(envVar);
		if (isNotBlank(fromEnv)) {
			return fromEnv;
		}
		return fallback;
	}

	private boolean isNotBlank(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	private String timestamp() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run();
	}

	private static class NavigationResult {
		private final String url;

		private NavigationResult(final String url) {
			this.url = url;
		}
	}
}
