package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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

public class SaleadsMiNegocioWorkflowTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String REPORT_DETALLES = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String REPORT_POLITICA = "Pol\u00edtica de Privacidad";

	private static final int DEFAULT_TIMEOUT_MS = 15000;
	private static final int SHORT_TIMEOUT_MS = 5000;
	private static final int CLICK_SETTLE_WAIT_MS = 700;
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private static final Pattern LOGIN_BUTTON_PATTERN = Pattern
			.compile("(?i)(sign in with google|iniciar sesi[o\\u00f3]n con google|continuar con google)");
	private static final Pattern MENU_MI_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*mi negocio\\s*$");
	private static final Pattern MENU_NEGOCIO_PATTERN = Pattern.compile("(?i)negocio");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*agregar negocio\\s*$");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)^\\s*administrar negocios\\s*$");
	private static final Pattern CREAR_NEGOCIO_TITLE_PATTERN = Pattern.compile("(?i)crear nuevo negocio");
	private static final Pattern NOMBRE_NEGOCIO_PATTERN = Pattern.compile("(?i)nombre del negocio");
	private static final Pattern CUPO_NEGOCIO_PATTERN = Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios");
	private static final Pattern INFORMACION_GENERAL_PATTERN = Pattern.compile("(?i)informaci[o\\u00f3]n general");
	private static final Pattern DETALLES_CUENTA_PATTERN = Pattern.compile("(?i)detalles de la cuenta");
	private static final Pattern TUS_NEGOCIOS_PATTERN = Pattern.compile("(?i)tus negocios");
	private static final Pattern SECCION_LEGAL_PATTERN = Pattern.compile("(?i)secci[o\\u00f3]n legal");
	private static final Pattern TERMS_HEADING_PATTERN = Pattern.compile("(?i)t[e\\u00e9]rminos y condiciones");
	private static final Pattern PRIVACY_HEADING_PATTERN = Pattern.compile("(?i)pol[i\\u00ed]tica de privacidad");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private Path evidenceDir;

	private final Map<String, String> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws Exception {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);

		playwright = Playwright.create();
		final boolean headless = Boolean.parseBoolean(readSetting("SALEADS_HEADLESS", "saleads.headless", "true"));

		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
		appPage = context.newPage();

		final String loginUrl = readSetting("SALEADS_LOGIN_URL", "saleads.login.url", null);
		assertTrue(
				"Set SALEADS_LOGIN_URL (or -Dsaleads.login.url) to the SaleADS login page for the target environment.",
				loginUrl != null && !loginUrl.isBlank());
		appPage.navigate(loginUrl);
		waitForUiIdle(appPage);
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
		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MI_NEGOCIO, this::stepOpenMiNegocioMenu);
		runStep(REPORT_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMINISTRAR_NEGOCIOS, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(REPORT_DETALLES, this::stepValidateDetallesDeCuenta);
		runStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(REPORT_TERMINOS, () -> stepValidateLegalDocument("T\u00e9rminos y Condiciones", TERMS_HEADING_PATTERN, "08-terminos.png"));
		runStep(REPORT_POLITICA, () -> stepValidateLegalDocument("Pol\u00edtica de Privacidad", PRIVACY_HEADING_PATTERN, "09-politica-privacidad.png"));

		printFinalReport();
		assertTrue("Validation failures:\n - " + String.join("\n - ", failures), failures.isEmpty());
	}

	private void stepLoginWithGoogle() {
		final Locator loginButton = firstVisible(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN)),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN)),
				appPage.getByText(LOGIN_BUTTON_PATTERN));
		assertVisible("Google login button", loginButton);

		final int pageCountBefore = context.pages().size();
		clickAndWaitUi(appPage, loginButton);
		final Page popup = waitForNewTab(pageCountBefore, SHORT_TIMEOUT_MS);

		if (popup != null) {
			waitForUiIdle(popup);
			chooseGoogleAccountIfShown(popup);
			waitForUiIdle(appPage);
		} else {
			chooseGoogleAccountIfShown(appPage);
		}

		waitForAnyVisible("Main application interface",
				appPage.locator("aside"),
				appPage.getByRole(AriaRole.NAVIGATION),
				appPage.getByText(MENU_NEGOCIO_PATTERN),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MENU_MI_NEGOCIO_PATTERN)));

		assertVisible("Left sidebar navigation", firstVisible(appPage.locator("aside"), appPage.getByRole(AriaRole.NAVIGATION)));
		takeScreenshot(appPage, "01-dashboard-loaded.png", true);
	}

	private void stepOpenMiNegocioMenu() {
		assertVisible("Sidebar 'Negocio' section", appPage.getByText(MENU_NEGOCIO_PATTERN));
		expandMiNegocioMenuIfNeeded();

		assertVisible("'Agregar Negocio' submenu", appPage.getByText(AGREGAR_NEGOCIO_PATTERN));
		assertVisible("'Administrar Negocios' submenu", appPage.getByText(ADMINISTRAR_NEGOCIOS_PATTERN));
		takeScreenshot(appPage, "02-mi-negocio-expanded.png", true);
	}

	private void stepValidateAgregarNegocioModal() {
		final Locator agregarNegocio = firstVisible(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
				appPage.getByText(AGREGAR_NEGOCIO_PATTERN));
		assertVisible("Agregar Negocio action", agregarNegocio);
		clickAndWaitUi(appPage, agregarNegocio);

		assertVisible("Modal title 'Crear Nuevo Negocio'", appPage.getByText(CREAR_NEGOCIO_TITLE_PATTERN));
		assertVisible("Input 'Nombre del Negocio'",
				firstVisible(appPage.getByLabel(NOMBRE_NEGOCIO_PATTERN), appPage.getByPlaceholder(NOMBRE_NEGOCIO_PATTERN),
						appPage.locator("input")));
		assertVisible("Text 'Tienes 2 de 3 negocios'", appPage.getByText(CUPO_NEGOCIO_PATTERN));
		assertVisible("Button 'Cancelar'",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*cancelar\\s*$"))));
		assertVisible("Button 'Crear Negocio'",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*crear negocio\\s*$"))));

		takeScreenshot(appPage, "03-agregar-negocio-modal.png", true);

		final Locator nombreNegocioInput = firstVisible(appPage.getByLabel(NOMBRE_NEGOCIO_PATTERN),
				appPage.getByPlaceholder(NOMBRE_NEGOCIO_PATTERN), appPage.locator("input"));
		assertVisible("Nombre del Negocio input field", nombreNegocioInput);
		clickAndWaitUi(appPage, nombreNegocioInput);
		nombreNegocioInput.fill("Negocio Prueba Automatizacion");

		final Locator cancelarButton = appPage.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*cancelar\\s*$")));
		clickAndWaitUi(appPage, cancelarButton);
		assertHidden("Agregar negocio modal", appPage.getByText(CREAR_NEGOCIO_TITLE_PATTERN));
	}

	private void stepOpenAdministrarNegocios() {
		expandMiNegocioMenuIfNeeded();

		final Locator administrarNegocios = firstVisible(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
				appPage.getByText(ADMINISTRAR_NEGOCIOS_PATTERN));
		assertVisible("Administrar Negocios action", administrarNegocios);
		clickAndWaitUi(appPage, administrarNegocios);

		assertVisible("Section Informacion General", appPage.getByText(INFORMACION_GENERAL_PATTERN));
		assertVisible("Section Detalles de la Cuenta", appPage.getByText(DETALLES_CUENTA_PATTERN));
		assertVisible("Section Tus Negocios", appPage.getByText(TUS_NEGOCIOS_PATTERN));
		assertVisible("Section Seccion Legal", appPage.getByText(SECCION_LEGAL_PATTERN));
		takeScreenshot(appPage, "04-administrar-negocios-page.png", true);
	}

	private void stepValidateInformacionGeneral() {
		assertVisible("Informacion General section", appPage.getByText(INFORMACION_GENERAL_PATTERN));
		final String expectedEmail = readSetting("SALEADS_EXPECTED_USER_EMAIL", "saleads.expected.user.email", GOOGLE_ACCOUNT);
		assertVisible("User email", firstVisible(appPage.getByText(Pattern.compile(Pattern.quote(expectedEmail), Pattern.CASE_INSENSITIVE)),
				appPage.locator("text=/[^\\s@]+@[^\\s@]+\\.[^\\s@]+/")));

		final String expectedName = readSetting("SALEADS_EXPECTED_USER_NAME", "saleads.expected.user.name", null);
		if (expectedName != null && !expectedName.isBlank()) {
			assertVisible("Expected user name", appPage.getByText(Pattern.compile(Pattern.quote(expectedName), Pattern.CASE_INSENSITIVE)));
		} else {
			assertVisible("User name label or value", firstVisible(
					appPage.getByText(Pattern.compile("(?i)nombre")),
					appPage.getByText(Pattern.compile("(?i)usuario")),
					appPage.getByText(Pattern.compile("(?i)name"))));
		}

		assertVisible("BUSINESS PLAN text", appPage.getByText(Pattern.compile("(?i)business plan")));
		assertVisible("'Cambiar Plan' button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar plan"))));
	}

	private void stepValidateDetallesDeCuenta() {
		assertVisible("Section Detalles de la Cuenta", appPage.getByText(DETALLES_CUENTA_PATTERN));
		assertVisible("'Cuenta creada' text", appPage.getByText(Pattern.compile("(?i)cuenta creada")));
		assertVisible("'Estado activo' text", appPage.getByText(Pattern.compile("(?i)estado activo")));
		assertVisible("'Idioma seleccionado' text", appPage.getByText(Pattern.compile("(?i)idioma seleccionado")));
	}

	private void stepValidateTusNegocios() {
		assertVisible("Section Tus Negocios", appPage.getByText(TUS_NEGOCIOS_PATTERN));
		assertVisible("'Agregar Negocio' button in section",
				firstVisible(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
						appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN))));
		assertVisible("'Tienes 2 de 3 negocios' text", appPage.getByText(CUPO_NEGOCIO_PATTERN));

		assertTrue("Business list should be visible",
				isVisible(appPage.locator("ul li"), SHORT_TIMEOUT_MS) || isVisible(appPage.locator("table tbody tr"), SHORT_TIMEOUT_MS)
						|| isVisible(appPage.getByText(Pattern.compile("(?i)negocio")), SHORT_TIMEOUT_MS));
	}

	private void stepValidateLegalDocument(final String linkText, final Pattern headingPattern, final String screenshotName) {
		final Locator legalLink = firstVisible(appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))),
				appPage.getByText(Pattern.compile("(?i)" + Pattern.quote(linkText))));
		assertVisible(linkText + " link", legalLink);

		final int pageCountBefore = context.pages().size();
		final String appUrlBefore = appPage.url();
		clickAndWaitUi(appPage, legalLink);

		final Page legalPage = waitForNewTab(pageCountBefore, SHORT_TIMEOUT_MS);
		if (legalPage != null) {
			waitForUiIdle(legalPage);
			validateLegalPage(legalPage, headingPattern, screenshotName);
			legalUrls.put(linkText, legalPage.url());
			legalPage.close();
			appPage.bringToFront();
			waitForUiIdle(appPage);
			return;
		}

		validateLegalPage(appPage, headingPattern, screenshotName);
		legalUrls.put(linkText, appPage.url());

		if (!appPage.url().equals(appUrlBefore)) {
			appPage.goBack(new Page.GoBackOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			waitForUiIdle(appPage);
		}
	}

	private void validateLegalPage(final Page page, final Pattern headingPattern, final String screenshotName) {
		waitForAnyVisible("Legal heading",
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
				page.getByText(headingPattern));
		final String bodyText = page.locator("body").innerText();
		assertTrue("Legal content text should be visible", bodyText != null && bodyText.replaceAll("\\s+", " ").trim().length() > 120);
		takeScreenshot(page, screenshotName, true);
	}

	private void chooseGoogleAccountIfShown(final Page page) {
		final boolean isGooglePage = page.url().contains("accounts.google.com")
				|| isVisible(page.getByText(Pattern.compile("(?i)(choose an account|elige una cuenta)")), SHORT_TIMEOUT_MS);
		if (!isGooglePage) {
			return;
		}

		final Locator accountChoice = page.getByText(Pattern.compile("(?i)" + Pattern.quote(GOOGLE_ACCOUNT)));
		if (isVisible(accountChoice, SHORT_TIMEOUT_MS)) {
			clickAndWaitUi(page, accountChoice);
		}
	}

	private void expandMiNegocioMenuIfNeeded() {
		if (isVisible(appPage.getByText(AGREGAR_NEGOCIO_PATTERN), SHORT_TIMEOUT_MS)
				&& isVisible(appPage.getByText(ADMINISTRAR_NEGOCIOS_PATTERN), SHORT_TIMEOUT_MS)) {
			return;
		}

		final Locator miNegocio = firstVisible(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MENU_MI_NEGOCIO_PATTERN)),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MENU_MI_NEGOCIO_PATTERN)),
				appPage.getByText(MENU_MI_NEGOCIO_PATTERN));
		assertVisible("'Mi Negocio' menu item", miNegocio);
		clickAndWaitUi(appPage, miNegocio);
	}

	private void runStep(final String reportField, final Runnable action) {
		try {
			action.run();
			report.put(reportField, "PASS");
		} catch (final Throwable error) {
			report.put(reportField, "FAIL");
			final String detail = error.getMessage() == null ? error.toString() : error.getMessage();
			failures.add(reportField + " -> " + detail);
			takeScreenshot(appPage, "failure-" + sanitize(reportField) + ".png", true);
		}
	}

	private void waitForUiIdle(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// NETWORKIDLE is best-effort only.
		}
		page.waitForTimeout(CLICK_SETTLE_WAIT_MS);
	}

	private void clickAndWaitUi(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUiIdle(page);
	}

	private void assertVisible(final String description, final Locator locator) {
		assertTrue(description + " should be visible", isVisible(locator, DEFAULT_TIMEOUT_MS));
	}

	private void assertHidden(final String description, final Locator locator) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (final PlaywrightException error) {
			throw new AssertionError(description + " should be hidden", error);
		}
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final PlaywrightException error) {
			return false;
		}
	}

	private void waitForAnyVisible(final String description, final Locator... locators) {
		assertTrue(description + " should be visible", Arrays.stream(locators).anyMatch(locator -> isVisible(locator, DEFAULT_TIMEOUT_MS)));
	}

	private Locator firstVisible(final Locator... locators) {
		for (final Locator locator : locators) {
			if (isVisible(locator, SHORT_TIMEOUT_MS)) {
				return locator.first();
			}
		}
		return locators[0].first();
	}

	private Page waitForNewTab(final int pageCountBefore, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (context.pages().size() > pageCountBefore) {
				return context.pages().get(context.pages().size() - 1);
			}
			appPage.waitForTimeout(200);
		}
		return null;
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private String sanitize(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String readSetting(final String envKey, final String propertyKey, final String defaultValue) {
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		return defaultValue;
	}

	private void printFinalReport() {
		System.out.println();
		System.out.println("Final report (Mi Negocio workflow):");
		System.out.println(" - Login: " + report.getOrDefault(REPORT_LOGIN, "NOT RUN"));
		System.out.println(" - Mi Negocio menu: " + report.getOrDefault(REPORT_MI_NEGOCIO, "NOT RUN"));
		System.out.println(" - Agregar Negocio modal: " + report.getOrDefault(REPORT_AGREGAR_NEGOCIO_MODAL, "NOT RUN"));
		System.out.println(" - Administrar Negocios view: " + report.getOrDefault(REPORT_ADMINISTRAR_NEGOCIOS, "NOT RUN"));
		System.out.println(" - Informacion General: " + report.getOrDefault(REPORT_INFO_GENERAL, "NOT RUN"));
		System.out.println(" - Detalles de la Cuenta: " + report.getOrDefault(REPORT_DETALLES, "NOT RUN"));
		System.out.println(" - Tus Negocios: " + report.getOrDefault(REPORT_TUS_NEGOCIOS, "NOT RUN"));
		System.out.println(" - Terminos y Condiciones: " + report.getOrDefault(REPORT_TERMINOS, "NOT RUN"));
		System.out.println(" - Politica de Privacidad: " + report.getOrDefault(REPORT_POLITICA, "NOT RUN"));

		legalUrls.forEach((label, url) -> System.out.println(" - URL " + label + ": " + url));
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}
}
