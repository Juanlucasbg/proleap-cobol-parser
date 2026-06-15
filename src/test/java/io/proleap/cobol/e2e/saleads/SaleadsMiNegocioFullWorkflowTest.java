package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * End-to-end test for the SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * Runtime configuration:
 * <ul>
 * <li>SALEADS_E2E_ENABLED=true (required to run)</li>
 * <li>SALEADS_START_URL=https://... (optional, environment-specific login page)</li>
 * <li>SALEADS_HEADLESS=true|false (optional, default false)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MENU = "Mi Negocio menu";
	private static final String REPORT_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
	private static final String REPORT_GENERAL_INFO = "Información General";
	private static final String REPORT_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String REPORT_BUSINESSES = "Tus Negocios";
	private static final String REPORT_TERMS = "Términos y Condiciones";
	private static final String REPORT_PRIVACY = "Política de Privacidad";

	private static final int SHORT_TIMEOUT_MS = 1500;
	private static final int POPUP_TIMEOUT_MS = 7000;
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final LinkedHashMap<String, Boolean> finalReport = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> metadataReport = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private Path evidenceDir;

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this external E2E workflow.",
				isTruthy(System.getenv("SALEADS_E2E_ENABLED")));

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);

		playwright = Playwright.create();
		final boolean headless = isTruthy(System.getenv().getOrDefault("SALEADS_HEADLESS", "false"));
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
		context.setDefaultTimeout(15000);
		page = context.newPage();

		final String startUrl = System.getenv("SALEADS_START_URL");
		if (startUrl != null && !startUrl.isBlank()) {
			page.navigate(startUrl);
			waitForUi(page);
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
		initializeFinalReport();

		runStep(REPORT_LOGIN, () -> {
			loginWithGoogleAndValidateLanding();
			screenshot("01-dashboard-loaded.png", true);
		});

		runStep(REPORT_MENU, () -> {
			openMiNegocioMenu();
			validateMiNegocioExpandedMenu();
			screenshot("02-mi-negocio-menu-expanded.png", false);
		});

		runStep(REPORT_MODAL, () -> {
			validateAgregarNegocioModal();
			screenshot("03-agregar-negocio-modal.png", false);
		});

		runStep(REPORT_ADMIN_VIEW, () -> {
			openAdministrarNegociosAndValidateSections();
			screenshot("04-administrar-negocios-view.png", true);
		});

		runStep(REPORT_GENERAL_INFO, this::validateInformacionGeneralSection);
		runStep(REPORT_ACCOUNT_DETAILS, this::validateDetallesCuentaSection);
		runStep(REPORT_BUSINESSES, this::validateTusNegociosSection);

		runStep(REPORT_TERMS, () -> {
			final String url = validateLegalLink("Términos y Condiciones",
					Pattern.compile("(?iu)T[eé]rminos\\s+y\\s+Condiciones"), "05-terminos-y-condiciones.png");
			metadataReport.put("Final URL - Términos y Condiciones", url);
		});

		runStep(REPORT_PRIVACY, () -> {
			final String url = validateLegalLink("Política de Privacidad", Pattern.compile("(?iu)Pol[ií]tica\\s+de\\s+Privacidad"),
					"06-politica-de-privacidad.png");
			metadataReport.put("Final URL - Política de Privacidad", url);
		});

		printFinalReport();
		assertFalse("SaleADS workflow has failing validations:\n" + String.join("\n", failures), finalReport.containsValue(false));
	}

	private void loginWithGoogleAndValidateLanding() {
		if ("about:blank".equals(page.url()) && isBlank(System.getenv("SALEADS_START_URL"))) {
			throw new AssertionError(
					"No login page available. Provide SALEADS_START_URL or navigate the page externally before running.");
		}

		final Locator googleLoginButton = findFirstVisible("Login button / Sign in with Google",
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)(Iniciar sesi[oó]n|Sign in).*Google"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*Google.*"))),
				page.getByText(Pattern.compile("(?iu)Sign in with Google|Iniciar sesi[oó]n con Google")));

		final Page popup = clickPossiblyOpeningNewTab(page, googleLoginButton);
		selectGoogleAccountIfPrompted(popup);
		page = resolveApplicationPage(page, popup);

		assertAnyVisible("Main application interface", page.getByRole(AriaRole.MAIN), page.locator("main"),
				page.getByText(Pattern.compile("(?iu)Dashboard|Panel|Inicio|Negocio|Mi Negocio")));
		assertAnyVisible("Left sidebar navigation", page.getByRole(AriaRole.NAVIGATION),
				page.getByText(Pattern.compile("(?iu)^Negocio$|Mi Negocio")));
	}

	private void openMiNegocioMenu() {
		final Locator negocioSection = findFirstVisible("Negocio section",
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Negocio$"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Negocio$"))),
				page.getByText(Pattern.compile("(?iu)^Negocio$")));
		clickAndWait(page, negocioSection);

		final Locator miNegocio = findFirstVisible("Mi Negocio option",
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Mi Negocio$"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Mi Negocio$"))),
				page.getByText(Pattern.compile("(?iu)^Mi Negocio$")));
		clickAndWait(page, miNegocio);
	}

	private void validateMiNegocioExpandedMenu() {
		assertAnyVisible("Mi Negocio submenu expanded",
				page.getByText(Pattern.compile("(?iu)^Agregar Negocio$")),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Agregar Negocio$"))));
		assertAnyVisible("'Agregar Negocio' visible", page.getByText(Pattern.compile("(?iu)^Agregar Negocio$")));
		assertAnyVisible("'Administrar Negocios' visible", page.getByText(Pattern.compile("(?iu)^Administrar Negocios$")));
	}

	private void validateAgregarNegocioModal() {
		final Locator agregarNegocio = findFirstVisible("Agregar Negocio menu option",
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Agregar Negocio$"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Agregar Negocio$"))),
				page.getByText(Pattern.compile("(?iu)^Agregar Negocio$")));
		clickAndWait(page, agregarNegocio);

		assertAnyVisible("Modal title 'Crear Nuevo Negocio'",
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Crear Nuevo Negocio"))),
				page.getByText(Pattern.compile("(?iu)Crear Nuevo Negocio")));
		final Locator businessNameInput = assertAnyVisible("Input 'Nombre del Negocio'",
				page.getByLabel(Pattern.compile("(?iu)Nombre del Negocio")),
				page.getByPlaceholder(Pattern.compile("(?iu)Nombre del Negocio")));
		assertAnyVisible("Text 'Tienes 2 de 3 negocios'", page.getByText(Pattern.compile("(?iu)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
		final Locator cancelButton = assertAnyVisible("Button 'Cancelar'",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Cancelar$"))),
				page.getByText(Pattern.compile("(?iu)^Cancelar$")));
		assertAnyVisible("Button 'Crear Negocio'",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Crear Negocio$"))),
				page.getByText(Pattern.compile("(?iu)^Crear Negocio$")));

		// Optional action from workflow: fill the input and close with "Cancelar".
		businessNameInput.click();
		businessNameInput.fill("Negocio Prueba Automatización");
		clickAndWait(page, cancelButton);
	}

	private void openAdministrarNegociosAndValidateSections() {
		expandMiNegocioIfCollapsed();

		final Locator administrarNegocios = findFirstVisible("Administrar Negocios option",
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Administrar Negocios$"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Administrar Negocios$"))),
				page.getByText(Pattern.compile("(?iu)^Administrar Negocios$")));
		clickAndWait(page, administrarNegocios);

		assertAnyVisible("Section 'Información General'",
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Informaci[oó]n General"))),
				page.getByText(Pattern.compile("(?iu)Informaci[oó]n General")));
		assertAnyVisible("Section 'Detalles de la Cuenta'",
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Detalles de la Cuenta"))),
				page.getByText(Pattern.compile("(?iu)Detalles de la Cuenta")));
		assertAnyVisible("Section 'Tus Negocios'",
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Tus Negocios"))),
				page.getByText(Pattern.compile("(?iu)Tus Negocios")));
		assertAnyVisible("Section 'Sección Legal'",
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Secci[oó]n Legal"))),
				page.getByText(Pattern.compile("(?iu)Secci[oó]n Legal")));
	}

	private void validateInformacionGeneralSection() {
		assertEmailVisibleOnPage();
		assertAnyVisible("Text 'BUSINESS PLAN'", page.getByText(Pattern.compile("(?iu)BUSINESS PLAN")));
		assertAnyVisible("Button 'Cambiar Plan'",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Cambiar Plan"))),
				page.getByText(Pattern.compile("(?iu)Cambiar Plan")));

		final String sectionText = getSectionText("Informaci[oó]n General");
		assertTrue("User name should be visible in Información General section.",
				Pattern.compile("(?iu)\\b[\\p{L}]{2,}\\s+[\\p{L}]{2,}\\b").matcher(sectionText).find());
	}

	private void validateDetallesCuentaSection() {
		assertAnyVisible("'Cuenta creada' is visible", page.getByText(Pattern.compile("(?iu)Cuenta creada")));
		assertAnyVisible("'Estado activo' is visible", page.getByText(Pattern.compile("(?iu)Estado activo")));
		assertAnyVisible("'Idioma seleccionado' is visible", page.getByText(Pattern.compile("(?iu)Idioma seleccionado")));
	}

	private void validateTusNegociosSection() {
		final String sectionText = getSectionText("Tus Negocios");
		assertTrue("Business list should be visible in 'Tus Negocios'.",
				sectionText != null && sectionText.replaceAll("\\s+", " ").trim().length() > 50);
		assertAnyVisible("Button 'Agregar Negocio'",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Agregar Negocio$"))),
				page.getByText(Pattern.compile("(?iu)^Agregar Negocio$")));
		assertAnyVisible("Text 'Tienes 2 de 3 negocios'",
				page.getByText(Pattern.compile("(?iu)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
	}

	private String validateLegalLink(final String linkText, final Pattern headingPattern, final String screenshotFileName) {
		final Locator link = findFirstVisible("Legal link: " + linkText,
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^" + Pattern.quote(linkText) + "$"))),
				page.getByText(Pattern.compile("(?iu)^" + Pattern.quote(linkText) + "$")));

		final Page popup = clickPossiblyOpeningNewTab(page, link);
		final Page targetPage = popup != null ? popup : page;
		waitForUi(targetPage);

		assertAnyVisible("Heading '" + linkText + "' in legal page", targetPage.getByRole(AriaRole.HEADING,
				new Page.GetByRoleOptions().setName(headingPattern)), targetPage.getByText(headingPattern));

		final String bodyText = targetPage.locator("body").innerText();
		assertTrue("Legal content text should be visible for '" + linkText + "'.",
				bodyText != null && bodyText.replaceAll("\\s+", " ").trim().length() > 150);

		screenshot(targetPage, screenshotFileName, true);

		final String finalUrl = targetPage.url();
		assertNotNull("Final URL should not be null for '" + linkText + "'.", finalUrl);
		assertFalse("Final URL should not be blank for '" + linkText + "'.", finalUrl.isBlank());

		if (popup != null && !popup.isClosed()) {
			popup.close();
			page.bringToFront();
		} else if (popup == null) {
			try {
				page.goBack();
				waitForUi(page);
			} catch (final PlaywrightException ignored) {
				// Some environments might replace content via SPA routing with no browser history.
			}
		}
		return finalUrl;
	}

	private void selectGoogleAccountIfPrompted(final Page popup) {
		Page googlePage = null;
		if (popup != null && !popup.isClosed() && popup.url().contains("accounts.google.com")) {
			googlePage = popup;
		} else if (page.url().contains("accounts.google.com")) {
			googlePage = page;
		} else {
			for (final Page candidate : context.pages()) {
				if (!candidate.isClosed() && candidate.url().contains("accounts.google.com")) {
					googlePage = candidate;
					break;
				}
			}
		}

		if (googlePage == null) {
			return;
		}

		googlePage.bringToFront();
		final Locator account = findFirstVisible("Google account selector entry",
				googlePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL)))),
				googlePage.getByText(Pattern.compile("(?iu)^" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + "$")));
		clickAndWait(googlePage, account);
	}

	private void expandMiNegocioIfCollapsed() {
		try {
			findFirstVisible("Administrar Negocios already visible",
					page.getByText(Pattern.compile("(?iu)^Administrar Negocios$")));
			return;
		} catch (final AssertionError ignored) {
			// Not visible yet, proceed with expansion clicks.
		}

		final Locator negocioSection = findFirstVisible("Negocio section to expand",
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Negocio$"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Negocio$"))),
				page.getByText(Pattern.compile("(?iu)^Negocio$")));
		clickAndWait(page, negocioSection);

		final Locator miNegocio = findFirstVisible("Mi Negocio entry to expand",
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Mi Negocio$"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Mi Negocio$"))),
				page.getByText(Pattern.compile("(?iu)^Mi Negocio$")));
		clickAndWait(page, miNegocio);
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			finalReport.put(reportField, Boolean.TRUE);
		} catch (final Throwable throwable) {
			finalReport.put(reportField, Boolean.FALSE);
			failures.add(reportField + ": " + throwable.getMessage());
			try {
				screenshot("failure-" + slugify(reportField) + ".png", true);
			} catch (final Throwable ignored) {
				// Continue gathering remaining step statuses even if failure screenshot cannot be written.
			}
		}
	}

	private Page clickPossiblyOpeningNewTab(final Page sourcePage, final Locator clickable) {
		try {
			final Page popup = sourcePage.context().waitForPage(() -> {
				clickable.scrollIntoViewIfNeeded();
				clickable.click();
			}, new BrowserContext.WaitForPageOptions().setTimeout(POPUP_TIMEOUT_MS));
			waitForUi(sourcePage);
			waitForUi(popup);
			return popup;
		} catch (final PlaywrightException noPopupOpened) {
			waitForUi(sourcePage);
			return null;
		}
	}

	private void clickAndWait(final Page activePage, final Locator clickable) {
		clickable.scrollIntoViewIfNeeded();
		clickable.click();
		waitForUi(activePage);
	}

	private void waitForUi(final Page activePage) {
		try {
			activePage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// Dynamic pages may not trigger a load event after in-page interactions.
		}
		try {
			activePage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			// Ignore when no network activity occurs or page keeps a long-lived connection open.
		}
		activePage.waitForTimeout(500);
	}

	private void assertEmailVisibleOnPage() {
		assertAnyVisible("User email is visible", page.getByText(Pattern.compile("(?iu)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")));
	}

	private String getSectionText(final String headingRegex) {
		final Locator heading = assertAnyVisible("Section heading " + headingRegex,
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)" + headingRegex))),
				page.getByText(Pattern.compile("(?iu)" + headingRegex)));
		final Locator sectionContainer = heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
		return sectionContainer.innerText();
	}

	private Locator assertAnyVisible(final String description, final Locator... candidates) {
		return findFirstVisible(description, candidates);
	}

	private Locator findFirstVisible(final String description, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			try {
				final Locator first = candidate.first();
				first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(SHORT_TIMEOUT_MS));
				return first;
			} catch (final PlaywrightException ignored) {
				// Try the next candidate.
			}
		}
		throw new AssertionError("Could not find visible element for: " + description);
	}

	private Page resolveApplicationPage(final Page initialPage, final Page popup) {
		if (popup != null && !popup.isClosed() && !popup.url().contains("accounts.google.com")) {
			return popup;
		}
		if (!initialPage.isClosed() && !initialPage.url().contains("accounts.google.com")) {
			return initialPage;
		}
		for (final Page candidate : context.pages()) {
			if (!candidate.isClosed() && !candidate.url().contains("accounts.google.com")) {
				return candidate;
			}
		}
		return initialPage;
	}

	private void screenshot(final String fileName, final boolean fullPage) {
		screenshot(page, fileName, fullPage);
	}

	private void screenshot(final Page targetPage, final String fileName, final boolean fullPage) {
		final Path output = evidenceDir.resolve(fileName);
		targetPage.screenshot(new Page.ScreenshotOptions().setPath(output).setFullPage(fullPage));
	}

	private void initializeFinalReport() {
		finalReport.put(REPORT_LOGIN, Boolean.FALSE);
		finalReport.put(REPORT_MENU, Boolean.FALSE);
		finalReport.put(REPORT_MODAL, Boolean.FALSE);
		finalReport.put(REPORT_ADMIN_VIEW, Boolean.FALSE);
		finalReport.put(REPORT_GENERAL_INFO, Boolean.FALSE);
		finalReport.put(REPORT_ACCOUNT_DETAILS, Boolean.FALSE);
		finalReport.put(REPORT_BUSINESSES, Boolean.FALSE);
		finalReport.put(REPORT_TERMS, Boolean.FALSE);
		finalReport.put(REPORT_PRIVACY, Boolean.FALSE);
	}

	private void printFinalReport() {
		System.out.println("==== SaleADS Mi Negocio Final Report ====");
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			System.out.println(entry.getKey() + ": " + (Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL"));
		}
		for (final Map.Entry<String, String> metadata : metadataReport.entrySet()) {
			System.out.println(metadata.getKey() + ": " + metadata.getValue());
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("=========================================");
	}

	private boolean isTruthy(final String value) {
		return value != null && ("1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value));
	}

	private String slugify(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private boolean isBlank(final String value) {
		return value == null || value.isBlank();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
