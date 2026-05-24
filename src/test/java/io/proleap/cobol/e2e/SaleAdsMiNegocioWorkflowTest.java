package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
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
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" module workflow.
 *
 * <p>
 * Runtime configuration:
 * <ul>
 * <li>SALEADS_LOGIN_URL: optional login URL for the active environment (dev/staging/prod).</li>
 * <li>SALEADS_GOOGLE_ACCOUNT: optional Google account to pick in the SSO chooser.</li>
 * <li>SALEADS_HEADLESS: optional, defaults to true.</li>
 * <li>SALEADS_STORAGE_STATE: optional Playwright storage state file path.</li>
 * </ul>
 * </p>
 */
public class SaleAdsMiNegocioWorkflowTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MENU = "Mi Negocio menu";
	private static final String REPORT_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN = "Administrar Negocios view";
	private static final String REPORT_INFO = "Informaci\u00f3n General";
	private static final String REPORT_DETAILS = "Detalles de la Cuenta";
	private static final String REPORT_BUSINESSES = "Tus Negocios";
	private static final String REPORT_TERMS = "T\u00e9rminos y Condiciones";
	private static final String REPORT_PRIVACY = "Pol\u00edtica de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> stepReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private Path evidenceDirectory;

	@Before
	public void setUp() throws IOException {
		playwright = Playwright.create();
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));

		final Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();
		final String storageStatePath = env("SALEADS_STORAGE_STATE");
		if (storageStatePath != null) {
			contextOptions.setStorageStatePath(Paths.get(storageStatePath));
		}

		context = browser.newContext(contextOptions);
		page = context.newPage();

		evidenceDirectory = Paths.get("target", "saleads-evidence",
				DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()));
		Files.createDirectories(evidenceDirectory);

		final String loginUrl = env("SALEADS_LOGIN_URL");
		if (loginUrl != null) {
			page.navigate(loginUrl);
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
		initializeReport();

		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMIN, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFO, this::stepValidateInformacionGeneral);
		runStep(REPORT_DETAILS, this::stepValidateDetallesCuenta);
		runStep(REPORT_BUSINESSES, this::stepValidateTusNegocios);
		runStep(REPORT_TERMS, () -> stepValidateLegalLink("T\u00e9rminos y Condiciones"));
		runStep(REPORT_PRIVACY, () -> stepValidateLegalLink("Pol\u00edtica de Privacidad"));

		printFinalReport();
		assertTrue("One or more SaleADS workflow validations failed. Check console report and screenshots in "
				+ evidenceDirectory, allStepsPassed());
	}

	private void stepLoginWithGoogle() {
		assertTrue(
				"The browser is still on about:blank. Provide SALEADS_LOGIN_URL or initialize the page externally before running this test.",
				!"about:blank".equals(page.url()));

		final Locator loginButton = firstVisible(page.locator("text=Sign in with Google"),
				page.locator("text=Login with Google"), page.locator("text=Iniciar sesi\u00f3n con Google"),
				page.locator("button:has-text(\"Google\")"), page.locator("[role='button']:has-text(\"Google\")"));

		final AtomicBoolean clickExecuted = new AtomicBoolean(false);
		Page popup = null;
		try {
			popup = page.waitForPopup(() -> {
				clickExecuted.set(true);
				loginButton.click();
			}, new Page.WaitForPopupOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			if (!clickExecuted.get()) {
				loginButton.click();
			}
		}

		waitForUiLoad(page);

		final String googleAccount = System.getenv().getOrDefault("SALEADS_GOOGLE_ACCOUNT",
				"juanlucasbarbiergarzon@gmail.com");
		selectGoogleAccountIfVisible(popup != null ? popup : page, googleAccount);

		if (popup != null) {
			try {
				waitForUiLoad(popup);
			} catch (final PlaywrightException ignored) {
				// Popup may close automatically after account selection.
			}
			if (!popup.isClosed()) {
				popup.close();
			}
			page.bringToFront();
		}

		waitForUiLoad(page);
		assertVisible("Main application interface", firstVisible(page.locator("main"), page.locator("text=Dashboard"),
				page.locator("text=Negocio")));
		assertVisible("Left sidebar navigation",
				firstVisible(page.locator("aside"), page.locator("nav"), page.locator("text=Negocio")));
		captureScreenshot(page, "step-01-dashboard.png", true);
	}

	private void stepOpenMiNegocioMenu() {
		assertVisible("Sidebar navigation", firstVisible(page.locator("aside"), page.locator("nav")));
		clickIfVisible(page.locator("text=Negocio"));
		clickByText("Mi Negocio");

		assertVisible("Expanded submenu entry 'Agregar Negocio'", page.locator("text=Agregar Negocio").first());
		assertVisible("Expanded submenu entry 'Administrar Negocios'",
				page.locator("text=Administrar Negocios").first());
		captureScreenshot(page, "step-02-mi-negocio-menu-expanded.png", true);
	}

	private void stepValidateAgregarNegocioModal() {
		clickByText("Agregar Negocio");
		assertVisible("Modal title 'Crear Nuevo Negocio'", page.locator("text=Crear Nuevo Negocio").first());

		final Locator businessNameField = firstVisible(page.getByLabel("Nombre del Negocio"),
				page.locator("input[placeholder*='Nombre del Negocio']"), page.locator("input[name*='negocio' i]"));
		assertVisible("Input field 'Nombre del Negocio'", businessNameField);
		assertVisible("Business plan slot text 'Tienes 2 de 3 negocios'",
				page.locator("text=Tienes 2 de 3 negocios").first());
		assertVisible("Button 'Cancelar'", page.locator("text=Cancelar").first());
		assertVisible("Button 'Crear Negocio'", page.locator("text=Crear Negocio").first());

		businessNameField.click();
		businessNameField.fill("Negocio Prueba Automatizacion");
		captureScreenshot(page, "step-03-agregar-negocio-modal.png", true);
		clickByText("Cancelar");
	}

	private void stepOpenAdministrarNegocios() {
		if (!isVisible(page.locator("text=Administrar Negocios").first(), 1000)) {
			clickByText("Mi Negocio");
		}
		clickByText("Administrar Negocios");

		assertVisible("Section 'Informaci\u00f3n General'", page.locator("text=Informaci\u00f3n General").first());
		assertVisible("Section 'Detalles de la Cuenta'", page.locator("text=Detalles de la Cuenta").first());
		assertVisible("Section 'Tus Negocios'", page.locator("text=Tus Negocios").first());
		assertVisible("Section 'Secci\u00f3n Legal'", page.locator("text=Secci\u00f3n Legal").first());
		captureScreenshot(page, "step-04-administrar-negocios-full.png", true);
	}

	private void stepValidateInformacionGeneral() {
		final Locator section = firstVisible(page.locator("section:has-text(\"Informaci\u00f3n General\")"),
				page.locator("div:has-text(\"Informaci\u00f3n General\")"));
		final String sectionText = section.innerText();

		final Matcher emailMatcher = EMAIL_PATTERN.matcher(sectionText);
		assertTrue("User email is not visible in Informacion General section.", emailMatcher.find());

		String normalizedText = sectionText.replaceAll(EMAIL_PATTERN.pattern(), " ");
		normalizedText = normalizedText.replace("Informaci\u00f3n General", " ").replace("BUSINESS PLAN", " ")
				.replace("Cambiar Plan", " ");
		assertTrue("User name is not visible in Informacion General section.", containsLikelyName(normalizedText));

		assertVisible("Text 'BUSINESS PLAN'", page.locator("text=BUSINESS PLAN").first());
		assertVisible("Button 'Cambiar Plan'", page.locator("text=Cambiar Plan").first());
	}

	private void stepValidateDetallesCuenta() {
		assertVisible("Label 'Cuenta creada'", page.locator("text=Cuenta creada").first());
		assertVisible("Status text containing 'activo'", firstVisible(page.locator("text=Estado activo"),
				page.locator("text=Estado"), page.locator("text=activo")));
		assertVisible("Label 'Idioma seleccionado'", page.locator("text=Idioma seleccionado").first());
	}

	private void stepValidateTusNegocios() {
		final Locator section = firstVisible(page.locator("section:has-text(\"Tus Negocios\")"),
				page.locator("div:has-text(\"Tus Negocios\")"));
		assertVisible("Business list section", section);
		assertVisible("Button 'Agregar Negocio' in business section", page.locator("text=Agregar Negocio").first());
		assertVisible("Text 'Tienes 2 de 3 negocios' in business section",
				page.locator("text=Tienes 2 de 3 negocios").first());

		final String text = section.innerText();
		assertTrue("Business list appears empty.", text != null && text.trim().length() > 20);
	}

	private void stepValidateLegalLink(final String linkText) {
		final String applicationUrl = page.url();

		final AtomicBoolean clickExecuted = new AtomicBoolean(false);
		Page legalPage = null;
		try {
			legalPage = page.waitForPopup(() -> {
				clickExecuted.set(true);
				clickByTextWithoutWaiting(linkText);
			}, new Page.WaitForPopupOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			if (!clickExecuted.get()) {
				clickByTextWithoutWaiting(linkText);
			}
		}

		final Page destinationPage = legalPage != null ? legalPage : page;
		waitForUiLoad(destinationPage);

		assertVisible("Legal heading '" + linkText + "'", destinationPage.locator("text=" + linkText).first());
		final String bodyText = destinationPage.locator("body").innerText();
		assertTrue("Legal content text is not visible for " + linkText + ".", bodyText != null && bodyText.trim().length() > 120);

		final String fileName = linkText.equals("T\u00e9rminos y Condiciones") ? "step-08-terminos-condiciones.png"
				: "step-09-politica-privacidad.png";
		captureScreenshot(destinationPage, fileName, true);
		legalUrls.put(linkText, destinationPage.url());

		if (destinationPage != page) {
			destinationPage.close();
			page.bringToFront();
			waitForUiLoad(page);
			return;
		}

		if (!applicationUrl.equals(page.url())) {
			page.goBack();
			waitForUiLoad(page);
		}
	}

	private void clickByText(final String text) {
		final Locator target = firstVisible(page.locator("text=" + text));
		target.click();
		waitForUiLoad(page);
	}

	private void clickByTextWithoutWaiting(final String text) {
		final Locator target = firstVisible(page.locator("text=" + text));
		target.click();
	}

	private void clickIfVisible(final Locator locator) {
		if (isVisible(locator.first(), 1000)) {
			locator.first().click();
			waitForUiLoad(page);
		}
	}

	private void selectGoogleAccountIfVisible(final Page authPage, final String accountEmail) {
		try {
			final Locator account = authPage.locator("text=" + accountEmail).first();
			if (isVisible(account, 5000)) {
				account.click();
				waitForUiLoad(authPage);
			}
		} catch (final PlaywrightException ignored) {
			// Account chooser is optional depending on active session state.
		}
	}

	private void waitForUiLoad(final Page activePage) {
		activePage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			activePage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7000));
		} catch (final PlaywrightException ignored) {
			// Some SPA routes keep network connections active; DOMContentLoaded is sufficient in this case.
		}
		activePage.waitForTimeout(400);
	}

	private Locator firstVisible(final Locator... candidates) {
		for (final Locator candidate : candidates) {
			final Locator target = candidate.first();
			if (isVisible(target, 4000)) {
				return target;
			}
		}

		throw new AssertionError("No visible locator found for provided candidates.");
	}

	private boolean isVisible(final Locator locator, final double timeoutMillis) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMillis));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void assertVisible(final String label, final Locator locator) {
		assertTrue(label + " is not visible.", isVisible(locator, 8000));
	}

	private void captureScreenshot(final Page targetPage, final String fileName, final boolean fullPage) {
		targetPage.screenshot(new Page.ScreenshotOptions().setPath(evidenceDirectory.resolve(fileName)).setFullPage(fullPage));
	}

	private void runStep(final String key, final StepAction action) {
		try {
			action.execute();
			stepReport.put(key, Boolean.TRUE);
		} catch (final Throwable throwable) {
			stepReport.put(key, Boolean.FALSE);
			System.err.println("Step failed [" + key + "]: " + throwable.getMessage());
			captureFailureScreenshot(key);
		}
	}

	private void initializeReport() {
		stepReport.put(REPORT_LOGIN, Boolean.FALSE);
		stepReport.put(REPORT_MENU, Boolean.FALSE);
		stepReport.put(REPORT_MODAL, Boolean.FALSE);
		stepReport.put(REPORT_ADMIN, Boolean.FALSE);
		stepReport.put(REPORT_INFO, Boolean.FALSE);
		stepReport.put(REPORT_DETAILS, Boolean.FALSE);
		stepReport.put(REPORT_BUSINESSES, Boolean.FALSE);
		stepReport.put(REPORT_TERMS, Boolean.FALSE);
		stepReport.put(REPORT_PRIVACY, Boolean.FALSE);
	}

	private void printFinalReport() {
		System.out.println("========== SaleADS Mi Negocio Workflow Report ==========");
		for (final Map.Entry<String, Boolean> entry : stepReport.entrySet()) {
			System.out.printf("%s: %s%n", entry.getKey(), entry.getValue() ? "PASS" : "FAIL");
		}

		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			System.out.printf("%s URL: %s%n", entry.getKey(), entry.getValue());
		}

		System.out.println("Evidence directory: " + evidenceDirectory);
		System.out.println("========================================================");
	}

	private boolean allStepsPassed() {
		for (final Boolean value : stepReport.values()) {
			if (!Boolean.TRUE.equals(value)) {
				return false;
			}
		}
		return true;
	}

	private void captureFailureScreenshot(final String stepName) {
		try {
			captureScreenshot(page, "failure-" + slugify(stepName) + ".png", true);
		} catch (final Throwable ignored) {
			// Best-effort evidence capture on step failure.
		}
	}

	private boolean containsLikelyName(final String sourceText) {
		for (final String line : sourceText.split("\\R")) {
			final String normalized = line.trim();
			if (normalized.length() < 3 || normalized.length() > 60) {
				continue;
			}

			if (!normalized.matches(".*[A-Za-z].*")) {
				continue;
			}

			final String lower = normalized.toLowerCase(Locale.ROOT);
			if (lower.contains("plan") || lower.contains("cuenta") || lower.contains("idioma") || lower.contains("negocio")) {
				continue;
			}

			return true;
		}

		return false;
	}

	private String env(final String key) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? null : value;
	}

	private String slugify(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	@FunctionalInterface
	private interface StepAction {
		void execute();
	}
}
