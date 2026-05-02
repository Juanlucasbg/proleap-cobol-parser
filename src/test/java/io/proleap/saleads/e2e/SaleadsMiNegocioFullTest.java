package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

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

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private Path evidenceDir;
	private int timeoutMs;

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> failureReasons = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws Exception {
		timeoutMs = readIntEnv("SALEADS_TIMEOUT_MS", 15000);
		final boolean headless = readBooleanEnv("SALEADS_HEADLESS", true);
		final int slowMo = readIntEnv("SALEADS_SLOW_MO_MS", 0);

		playwright = Playwright.create();
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo((double) slowMo));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
		page = context.newPage();

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Paths.get("target", "saleads-evidence", "saleads_mi_negocio_full_test-" + timestamp);
		Files.createDirectories(evidenceDir);

		final String startUrl = readEnv("SALEADS_START_URL");
		if (startUrl != null && !startUrl.isBlank()) {
			page.navigate(startUrl);
			waitForUiLoad(page);
		}
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
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		printFinalReport();

		final List<String> failed = new ArrayList<>();
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue()) {
				final String reason = failureReasons.getOrDefault(entry.getKey(), "unknown");
				failed.add(entry.getKey() + " -> " + reason);
			}
		}

		assertTrue("One or more validations failed: " + String.join(" | ", failed), failed.isEmpty());
	}

	private void runStep(final String label, final Step step) {
		try {
			step.execute();
			report.put(label, Boolean.TRUE);
		} catch (final Throwable throwable) {
			report.put(label, Boolean.FALSE);
			failureReasons.put(label, throwable.getMessage());
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		waitForUiLoad(page);

		final Locator loginButton = firstVisible(2500,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern("google"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern("google"))),
				page.getByText(pattern("sign\\s*in\\s*with\\s*google|iniciar\\s*sesion\\s*con\\s*google|google")));

		if (loginButton == null) {
			throw new IllegalStateException(
					"No Google login CTA found. If the page was not pre-opened, set SALEADS_START_URL to the current environment login page.");
		}

		final Page popup = clickExpectingOptionalPopup(loginButton);
		if (popup != null) {
			handleGoogleAccountSelectionIfPresent(popup);
			tryClosePage(popup);
			page.bringToFront();
		} else {
			handleGoogleAccountSelectionIfPresent(page);
		}

		requireVisible("Main app interface", firstVisible(timeoutMs,
				page.getByRole(AriaRole.MAIN),
				page.locator("main"),
				page.locator("div").filter(new Locator.FilterOptions().setHasText(pattern("dashboard|panel|inicio")))));

		final Locator sidebar = firstVisible(timeoutMs,
				page.getByRole(AriaRole.NAVIGATION),
				page.locator("aside"),
				page.locator("[class*='sidebar']"));
		requireVisible("Left sidebar navigation", sidebar);
		captureScreenshot(page, "01-dashboard-loaded.png", true);
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		final Locator sidebar = firstVisible(timeoutMs,
				page.getByRole(AriaRole.NAVIGATION),
				page.locator("aside"),
				page.locator("[class*='sidebar']"));
		requireVisible("Sidebar before menu actions", sidebar);

		final Locator negocioSection = firstVisible(timeoutMs,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern("negocio"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern("negocio"))),
				page.getByText(pattern("^\\s*Negocio\\s*$")));
		requireVisible("Negocio section", negocioSection);
		clickAndWaitUi(page, negocioSection);

		final Locator miNegocio = firstVisible(timeoutMs,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern("mi\\s*negocio"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern("mi\\s*negocio"))),
				page.getByText(pattern("mi\\s*negocio")));
		requireVisible("Mi Negocio option", miNegocio);
		clickAndWaitUi(page, miNegocio);

		requireVisible("Agregar Negocio visible", firstVisible(timeoutMs,
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern("agregar\\s*negocio"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern("agregar\\s*negocio"))),
				page.getByText(pattern("agregar\\s*negocio"))));
		requireVisible("Administrar Negocios visible", firstVisible(timeoutMs,
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern("administrar\\s*negocios"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern("administrar\\s*negocios"))),
				page.getByText(pattern("administrar\\s*negocios"))));

		captureScreenshot(page, "02-mi-negocio-menu-expanded.png", true);
	}

	private void stepAgregarNegocioModal() throws Exception {
		ensureMiNegocioExpanded();

		final Locator agregarNegocio = firstVisible(timeoutMs,
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern("agregar\\s*negocio"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern("agregar\\s*negocio"))),
				page.getByText(pattern("agregar\\s*negocio")));
		requireVisible("Agregar Negocio option", agregarNegocio);
		clickAndWaitUi(page, agregarNegocio);

		final Locator modalTitle = firstVisible(timeoutMs,
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(pattern("crear\\s*nuevo\\s*negocio"))),
				page.getByText(pattern("crear\\s*nuevo\\s*negocio")));
		requireVisible("Modal title Crear Nuevo Negocio", modalTitle);
		requireVisible("Input Nombre del Negocio", firstVisible(timeoutMs,
				page.getByLabel(pattern("nombre\\s*del\\s*negocio")),
				page.getByPlaceholder(pattern("nombre\\s*del\\s*negocio")),
				page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(pattern("nombre\\s*del\\s*negocio")))));
		requireVisible("Tienes 2 de 3 negocios text", page.getByText(pattern("tienes\\s*2\\s*de\\s*3\\s*negocios")));
		requireVisible("Cancelar button", firstVisible(timeoutMs,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern("cancelar"))),
				page.getByText(pattern("^\\s*cancelar\\s*$"))));
		requireVisible("Crear Negocio button", firstVisible(timeoutMs,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern("crear\\s*negocio"))),
				page.getByText(pattern("crear\\s*negocio"))));

		captureScreenshot(page, "03-agregar-negocio-modal.png", true);

		final Locator nombreInput = firstVisible(timeoutMs,
				page.getByLabel(pattern("nombre\\s*del\\s*negocio")),
				page.getByPlaceholder(pattern("nombre\\s*del\\s*negocio")),
				page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(pattern("nombre\\s*del\\s*negocio"))));
		requireVisible("Nombre del Negocio input to type", nombreInput);
		nombreInput.fill("Negocio Prueba Automatizacion");

		final Locator cancelarButton = firstVisible(timeoutMs,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern("cancelar"))),
				page.getByText(pattern("^\\s*cancelar\\s*$")));
		requireVisible("Cancelar button to close modal", cancelarButton);
		clickAndWaitUi(page, cancelarButton);
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioExpanded();

		final Locator administrarNegocios = firstVisible(timeoutMs,
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern("administrar\\s*negocios"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern("administrar\\s*negocios"))),
				page.getByText(pattern("administrar\\s*negocios")));
		requireVisible("Administrar Negocios option", administrarNegocios);
		clickAndWaitUi(page, administrarNegocios);

		requireVisible("Informacion General section", page.getByText(pattern("informacion\\s*general")));
		requireVisible("Detalles de la Cuenta section", page.getByText(pattern("detalles\\s*de\\s*la\\s*cuenta")));
		requireVisible("Tus Negocios section", page.getByText(pattern("tus\\s*negocios")));
		requireVisible("Seccion Legal section", page.getByText(pattern("seccion\\s*legal")));
		captureScreenshot(page, "04-administrar-negocios-cuenta-full.png", true);
	}

	private void stepValidateInformacionGeneral() {
		final Locator section = locateSectionByHeading(pattern("informacion\\s*general"));
		requireVisible("Informacion General section container", section);

		final String sectionText = safeInnerText(section);
		if (!EMAIL_PATTERN.matcher(sectionText).find()) {
			throw new AssertionError("User email is not visible in Informacion General");
		}

		if (!containsPattern(sectionText, pattern("nombre|usuario|perfil"))) {
			throw new AssertionError("User name indicator is not visible in Informacion General");
		}

		requireVisible("BUSINESS PLAN text", page.getByText(pattern("business\\s*plan")));
		requireVisible("Cambiar Plan button", firstVisible(timeoutMs,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern("cambiar\\s*plan"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern("cambiar\\s*plan"))),
				page.getByText(pattern("cambiar\\s*plan"))));
	}

	private void stepValidateDetallesCuenta() {
		final Locator section = locateSectionByHeading(pattern("detalles\\s*de\\s*la\\s*cuenta"));
		requireVisible("Detalles de la Cuenta section container", section);

		requireVisible("Cuenta creada text", section.getByText(pattern("cuenta\\s*creada")));
		requireVisible("Estado activo text", section.getByText(pattern("estado\\s*activo")));
		requireVisible("Idioma seleccionado text", section.getByText(pattern("idioma\\s*seleccionado")));
	}

	private void stepValidateTusNegocios() {
		final Locator section = locateSectionByHeading(pattern("tus\\s*negocios"));
		requireVisible("Tus Negocios section container", section);

		final String sectionText = safeInnerText(section);
		if (sectionText.trim().length() < 40) {
			throw new AssertionError("Business list content in Tus Negocios looks empty");
		}

		requireVisible("Agregar Negocio in Tus Negocios", firstVisible(timeoutMs,
				section.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(pattern("agregar\\s*negocio"))),
				section.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(pattern("agregar\\s*negocio"))),
				section.getByText(pattern("agregar\\s*negocio"))));
		requireVisible("Tienes 2 de 3 negocios in Tus Negocios", section.getByText(pattern("tienes\\s*2\\s*de\\s*3\\s*negocios")));
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		validateLegalLink(
				pattern("terminos\\s*y\\s*condiciones"),
				pattern("terminos\\s*y\\s*condiciones"),
				"05-terminos-y-condiciones.png",
				"Términos y Condiciones");
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		validateLegalLink(
				pattern("politica\\s*de\\s*privacidad"),
				pattern("politica\\s*de\\s*privacidad"),
				"06-politica-de-privacidad.png",
				"Política de Privacidad");
	}

	private void validateLegalLink(final Pattern linkPattern, final Pattern headingPattern, final String screenshotName,
			final String reportKey) throws Exception {
		requireVisible("Seccion Legal heading", page.getByText(pattern("seccion\\s*legal")));

		final Locator legalLink = firstVisible(timeoutMs,
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(linkPattern)),
				page.getByText(linkPattern));
		requireVisible("Legal link " + reportKey, legalLink);

		final Page popup = clickExpectingOptionalPopup(legalLink);
		final Page legalPage = popup != null ? popup : page;
		waitForUiLoad(legalPage);

		requireVisible(reportKey + " heading", firstVisible(timeoutMs,
				legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
				legalPage.getByText(headingPattern)));

		final String legalContent = safeInnerText(legalPage.locator("body"));
		if (legalContent.trim().length() < 150) {
			throw new AssertionError(reportKey + " legal content is not visible or too short");
		}

		legalUrls.put(reportKey, legalPage.url());
		captureScreenshot(legalPage, screenshotName, true);

		if (popup != null) {
			tryClosePage(popup);
			page.bringToFront();
			waitForUiLoad(page);
			return;
		}

		page.goBack();
		waitForUiLoad(page);
	}

	private void ensureMiNegocioExpanded() {
		if (isVisibleNow(page.getByText(pattern("agregar\\s*negocio")))
				&& isVisibleNow(page.getByText(pattern("administrar\\s*negocios")))) {
			return;
		}

		final Locator negocioSection = firstVisible(timeoutMs,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern("negocio"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern("negocio"))),
				page.getByText(pattern("^\\s*Negocio\\s*$")));
		if (negocioSection != null) {
			clickAndWaitUi(page, negocioSection);
		}

		final Locator miNegocio = firstVisible(timeoutMs,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern("mi\\s*negocio"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern("mi\\s*negocio"))),
				page.getByText(pattern("mi\\s*negocio")));
		if (miNegocio != null) {
			clickAndWaitUi(page, miNegocio);
		}

		requireVisible("Agregar Negocio after expansion", page.getByText(pattern("agregar\\s*negocio")));
		requireVisible("Administrar Negocios after expansion", page.getByText(pattern("administrar\\s*negocios")));
	}

	private Locator locateSectionByHeading(final Pattern headingPattern) {
		final Locator heading = firstVisible(timeoutMs,
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
				page.getByText(headingPattern));
		requireVisible("Section heading " + headingPattern.pattern(), heading);

		final Locator sectionFromHeading = heading.first().locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
		if (isVisibleNow(sectionFromHeading)) {
			return sectionFromHeading;
		}
		return heading;
	}

	private void handleGoogleAccountSelectionIfPresent(final Page authPage) {
		waitForUiLoad(authPage);

		final Locator emailOption = firstVisible(3000,
				authPage.getByText(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE)),
				authPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE))),
				authPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
						.setName(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE))));
		if (emailOption != null) {
			clickAndWaitUi(authPage, emailOption);
		}
	}

	private Page clickExpectingOptionalPopup(final Locator locator) {
		locator.first().scrollIntoViewIfNeeded();
		Page popup = null;

		try {
			popup = page.waitForPopup(() -> locator.first().click(), new Page.WaitForPopupOptions().setTimeout(8000));
		} catch (final PlaywrightException ignored) {
			// same-tab navigation path: click already happened in callback
		}

		waitForUiLoad(page);
		if (popup != null) {
			waitForUiLoad(popup);
		}

		return popup;
	}

	private void clickAndWaitUi(final Page targetPage, final Locator locator) {
		locator.first().scrollIntoViewIfNeeded();
		locator.first().click();
		waitForUiLoad(targetPage);
	}

	private void waitForUiLoad(final Page targetPage) {
		try {
			targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
		} catch (final PlaywrightException ignored) {
			// DOMContentLoaded may already be complete.
		}
		try {
			targetPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			// Some apps continuously poll network; treat this as non-fatal.
		}
		targetPage.waitForTimeout(350);
	}

	private void captureScreenshot(final Page targetPage, final String fileName, final boolean fullPage) {
		targetPage.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private Locator firstVisible(final int perLocatorTimeoutMs, final Locator... locators) {
		for (final Locator locator : locators) {
			if (locator == null) {
				continue;
			}
			try {
				locator.first().waitFor(new Locator.WaitForOptions()
						.setState(WaitForSelectorState.VISIBLE)
						.setTimeout(perLocatorTimeoutMs));
				return locator.first();
			} catch (final PlaywrightException ignored) {
				// Try next candidate.
			}
		}
		return null;
	}

	private void requireVisible(final String description, final Locator locator) {
		if (locator == null) {
			throw new AssertionError(description + " is not visible (locator not found)");
		}

		try {
			locator.first().waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout(timeoutMs));
		} catch (final PlaywrightException exception) {
			throw new AssertionError(description + " is not visible", exception);
		}
	}

	private boolean isVisibleNow(final Locator locator) {
		if (locator == null) {
			return false;
		}

		try {
			return locator.first().isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private String safeInnerText(final Locator locator) {
		try {
			return locator.first().innerText();
		} catch (final PlaywrightException exception) {
			return "";
		}
	}

	private Pattern pattern(final String regex) {
		return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	private boolean containsPattern(final String text, final Pattern pattern) {
		return pattern.matcher(text).find();
	}

	private void tryClosePage(final Page targetPage) {
		try {
			targetPage.close();
		} catch (final PlaywrightException ignored) {
			// Ignore close failures.
		}
	}

	private void printFinalReport() {
		System.out.println("=== SALEADS MI NEGOCIO FINAL REPORT ===");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			System.out.println(entry.getKey() + " URL: " + entry.getValue());
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private String readEnv(final String key) {
		return System.getenv(key);
	}

	private int readIntEnv(final String key, final int defaultValue) {
		final String value = readEnv(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}

		try {
			return Integer.parseInt(value.trim());
		} catch (final NumberFormatException ignored) {
			return defaultValue;
		}
	}

	private boolean readBooleanEnv(final String key, final boolean defaultValue) {
		final String value = readEnv(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value.trim());
	}

	@FunctionalInterface
	private interface Step {
		void execute() throws Exception;
	}
}
