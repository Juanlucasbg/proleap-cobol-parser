package io.proleap.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final int UI_TIMEOUT_MS = 30000;
	private static final int SHORT_TIMEOUT_MS = 5000;

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private Path evidenceDir;

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue(
				"This test is opt-in. Set RUN_SALEADS_E2E=true to execute.",
				"true".equalsIgnoreCase(System.getenv("RUN_SALEADS_E2E")));

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL to the active environment login page.",
				loginUrl != null && !loginUrl.isBlank());

		evidenceDir = Paths.get("target", "saleads-evidence",
				DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-"));
		Files.createDirectories(evidenceDir);

		playwright = Playwright.create();
		final boolean headless = !"false".equalsIgnoreCase(System.getenv("SALEADS_HEADLESS"));
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
		appPage = context.newPage();
		appPage.navigate(loginUrl, new Page.NavigateOptions().setTimeout(UI_TIMEOUT_MS));
		waitForUiSettle(appPage);
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
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();
		final Instant started = Instant.now();

		// 1) Login with Google
		try {
			loginWithGoogle();
			final boolean dashboardVisible = isAnyVisible(appPage, "Dashboard", "Inicio", "Panel");
			final boolean sidebarVisible = isSidebarVisible(appPage);
			recordResult(report, failures, "Login", dashboardVisible && sidebarVisible,
					"Dashboard and sidebar were not both visible after Google login.");
			takeScreenshot(appPage, "01-dashboard-loaded.png", false);
		} catch (Exception e) {
			recordResult(report, failures, "Login", false, "Login step failed: " + e.getMessage());
		}

		// 2) Open Mi Negocio menu
		try {
			expandMiNegocioMenu();
			final boolean hasAgregar = isAnyVisible(appPage, "Agregar Negocio");
			final boolean hasAdministrar = isAnyVisible(appPage, "Administrar Negocios");
			recordResult(report, failures, "Mi Negocio menu", hasAgregar && hasAdministrar,
					"Mi Negocio submenu did not show both options.");
			takeScreenshot(appPage, "02-mi-negocio-menu-expanded.png", false);
		} catch (Exception e) {
			recordResult(report, failures, "Mi Negocio menu", false, "Menu step failed: " + e.getMessage());
		}

		// 3) Validate Agregar Negocio modal
		try {
			clickByText(appPage, "Agregar Negocio");
			waitForUiSettle(appPage);
			waitForVisibleText(appPage, "Crear Nuevo Negocio", UI_TIMEOUT_MS);

			final boolean title = isAnyVisible(appPage, "Crear Nuevo Negocio");
			final boolean nombreField = isAnyVisible(appPage, "Nombre del Negocio");
			final boolean limitText = isAnyVisible(appPage, "Tienes 2 de 3 negocios");
			final boolean cancelar = isAnyVisible(appPage, "Cancelar");
			final boolean crear = isAnyVisible(appPage, "Crear Negocio");
			final boolean modalValid = title && nombreField && limitText && cancelar && crear;

			recordResult(report, failures, "Agregar Negocio modal", modalValid,
					"Agregar Negocio modal fields/buttons were incomplete.");
			takeScreenshot(appPage, "03-crear-negocio-modal.png", false);

			fillFirstVisibleInput("Nombre del Negocio", "Negocio Prueba Automatizacion");
			clickByText(appPage, "Cancelar");
			waitForUiSettle(appPage);
		} catch (Exception e) {
			recordResult(report, failures, "Agregar Negocio modal", false, "Modal step failed: " + e.getMessage());
		}

		// 4) Open Administrar Negocios
		try {
			expandMiNegocioMenu();
			clickByText(appPage, "Administrar Negocios");
			waitForUiSettle(appPage);
			waitForVisibleText(appPage, "Informacion General", UI_TIMEOUT_MS);

			final boolean info = isAnyVisibleNormalized(appPage, "Informacion General");
			final boolean detalles = isAnyVisibleNormalized(appPage, "Detalles de la Cuenta");
			final boolean negocios = isAnyVisibleNormalized(appPage, "Tus Negocios");
			final boolean legal = isAnyVisibleNormalized(appPage, "Seccion Legal");
			recordResult(report, failures, "Administrar Negocios view", info && detalles && negocios && legal,
					"Administrar Negocios page sections were not fully visible.");
			takeScreenshot(appPage, "04-administrar-negocios-full.png", true);
		} catch (Exception e) {
			recordResult(report, failures, "Administrar Negocios view", false,
					"Administrar Negocios step failed: " + e.getMessage());
		}

		// 5) Validate Informacion General
		try {
			final Locator infoSection = sectionByHeading("Informacion General");
			final boolean infoVisible = infoSection != null;
			final boolean emailVisible = isEmailVisible(infoSection == null ? appPage.locator("body") : infoSection);
			final boolean businessPlan = isAnyVisibleNormalized(appPage, "BUSINESS PLAN");
			final boolean cambiarPlan = isAnyVisibleNormalized(appPage, "Cambiar Plan");
			final boolean userNameLikelyPresent = infoSection != null && infoSection.innerText().replaceAll("\\s+", " ").trim().length() > 20;

			recordResult(report, failures, "Informacion General",
					infoVisible && userNameLikelyPresent && emailVisible && businessPlan && cambiarPlan,
					"Informacion General validation failed.");
		} catch (Exception e) {
			recordResult(report, failures, "Informacion General", false,
					"Informacion General step failed: " + e.getMessage());
		}

		// 6) Validate Detalles de la Cuenta
		try {
			final boolean cuentaCreada = isAnyVisibleNormalized(appPage, "Cuenta creada");
			final boolean estadoActivo = isAnyVisibleNormalized(appPage, "Estado activo");
			final boolean idioma = isAnyVisibleNormalized(appPage, "Idioma seleccionado");
			recordResult(report, failures, "Detalles de la Cuenta", cuentaCreada && estadoActivo && idioma,
					"Detalles de la Cuenta labels were not fully visible.");
		} catch (Exception e) {
			recordResult(report, failures, "Detalles de la Cuenta", false,
					"Detalles de la Cuenta step failed: " + e.getMessage());
		}

		// 7) Validate Tus Negocios
		try {
			final Locator negociosSection = sectionByHeading("Tus Negocios");
			final boolean sectionVisible = negociosSection != null;
			final boolean addButton = isAnyVisibleNormalized(appPage, "Agregar Negocio");
			final boolean limitText = isAnyVisibleNormalized(appPage, "Tienes 2 de 3 negocios");
			final boolean businessList = negociosSection != null && negociosSection.locator("li, table tbody tr, [role='row']").count() > 0;
			recordResult(report, failures, "Tus Negocios", sectionVisible && addButton && limitText && businessList,
					"Tus Negocios section validation failed.");
		} catch (Exception e) {
			recordResult(report, failures, "Tus Negocios", false, "Tus Negocios step failed: " + e.getMessage());
		}

		// 8) Validate Terminos y Condiciones
		try {
			final String termsUrl = validateLegalLink(
					"Terminos y Condiciones",
					"Terminos y Condiciones",
					"08-terminos-y-condiciones.png");
			legalUrls.put("Terminos y Condiciones", termsUrl);
			recordResult(report, failures, "Terminos y Condiciones", true, "");
		} catch (Exception e) {
			recordResult(report, failures, "Terminos y Condiciones", false,
					"Terminos y Condiciones step failed: " + e.getMessage());
		}

		// 9) Validate Politica de Privacidad
		try {
			final String privacyUrl = validateLegalLink(
					"Politica de Privacidad",
					"Politica de Privacidad",
					"09-politica-de-privacidad.png");
			legalUrls.put("Politica de Privacidad", privacyUrl);
			recordResult(report, failures, "Politica de Privacidad", true, "");
		} catch (Exception e) {
			recordResult(report, failures, "Politica de Privacidad", false,
					"Politica de Privacidad step failed: " + e.getMessage());
		}

		// 10) Final report
		final Path reportFile = evidenceDir.resolve("10-final-report.txt");
		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Full Workflow - Final Report");
		lines.add("Duration: " + Duration.between(started, Instant.now()));
		lines.add("");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			lines.add(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		lines.add("");
		lines.add("Captured Legal URLs:");
		for (Map.Entry<String, String> legal : legalUrls.entrySet()) {
			lines.add("- " + legal.getKey() + ": " + legal.getValue());
		}
		if (!failures.isEmpty()) {
			lines.add("");
			lines.add("Failure Details:");
			for (String failure : failures) {
				lines.add("- " + failure);
			}
		}
		Files.write(reportFile, lines);

		final boolean allPassed = report.values().stream().allMatch(Boolean::booleanValue);
		Assert.assertTrue("One or more workflow validations failed. See " + reportFile, allPassed);
	}

	private void loginWithGoogle() {
		Page googlePage = null;
		try {
			googlePage = appPage.waitForPopup(
					() -> clickByText(appPage, "Sign in with Google"),
					new Page.WaitForPopupOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (RuntimeException ex) {
			clickByAnyText(appPage, "Sign in with Google", "Iniciar sesion con Google", "Continuar con Google", "Google");
		}

		final Page authPage = googlePage == null ? appPage : googlePage;
		waitForUiSettle(authPage);
		clickIfVisible(authPage, ACCOUNT_EMAIL);
		waitForUiSettle(authPage);

		if (googlePage != null) {
			try {
				googlePage.waitForClose(new Page.WaitForCloseOptions().setTimeout(UI_TIMEOUT_MS));
			} catch (RuntimeException ignored) {
				// Some environments keep OAuth page open; continue with the main app tab.
			}
		}
		appPage.bringToFront();
		waitForUiSettle(appPage);
		waitForSidebar(appPage);
	}

	private void expandMiNegocioMenu() {
		waitForSidebar(appPage);
		if (isAnyVisible(appPage, "Agregar Negocio") && isAnyVisible(appPage, "Administrar Negocios")) {
			return;
		}

		clickIfVisible(appPage, "Negocio");
		waitForUiSettle(appPage);
		clickIfVisible(appPage, "Mi Negocio");
		waitForUiSettle(appPage);

		if (!isAnyVisible(appPage, "Agregar Negocio")) {
			clickByText(appPage, "Mi Negocio");
			waitForUiSettle(appPage);
		}
	}

	private String validateLegalLink(final String linkText, final String headingText, final String screenshotName) {
		final String beforeUrl = appPage.url();
		Page legalPage = null;
		try {
			legalPage = appPage.waitForPopup(
					() -> clickByText(appPage, linkText),
					new Page.WaitForPopupOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (TimeoutError e) {
			clickByText(appPage, linkText);
		}

		final Page targetPage = legalPage != null ? legalPage : appPage;
		waitForUiSettle(targetPage);
		waitForVisibleText(targetPage, headingText, UI_TIMEOUT_MS);

		final boolean headingVisible = isAnyVisibleNormalized(targetPage, headingText);
		final boolean legalContentVisible = targetPage.locator("p, article, main").first().innerText().trim().length() > 30;
		Assert.assertTrue("Legal heading/content not visible for " + linkText, headingVisible && legalContentVisible);

		takeScreenshot(targetPage, screenshotName, true);
		final String finalUrl = targetPage.url();

		if (legalPage != null) {
			legalPage.close();
			appPage.bringToFront();
		} else if (!beforeUrl.equals(appPage.url())) {
			appPage.goBack();
		}
		waitForUiSettle(appPage);
		return finalUrl;
	}

	private Locator sectionByHeading(final String heading) {
		final String headingRegex = "(?i)" + Pattern.quote(heading);
		Locator headingLocator = appPage.locator("h1, h2, h3, h4").filter(new Locator.FilterOptions().setHasText(Pattern.compile(headingRegex))).first();
		if (headingLocator.count() == 0) {
			headingLocator = appPage.getByText(Pattern.compile(headingRegex)).first();
		}
		if (headingLocator.count() == 0) {
			return null;
		}
		return headingLocator.locator("xpath=ancestor::section[1] | ancestor::div[1]");
	}

	private void fillFirstVisibleInput(final String labelOrPlaceholder, final String value) {
		final Pattern textPattern = Pattern.compile("(?i)" + Pattern.quote(labelOrPlaceholder));
		final Locator byLabel = appPage.getByLabel(textPattern).first();
		if (byLabel.count() > 0 && byLabel.isVisible()) {
			byLabel.fill(value);
			return;
		}
		final Locator byPlaceholder = appPage.getByPlaceholder(textPattern).first();
		if (byPlaceholder.count() > 0 && byPlaceholder.isVisible()) {
			byPlaceholder.fill(value);
			return;
		}
		final Locator inputNearLabel = appPage.getByText(textPattern).first().locator("xpath=ancestor::*[1]//input[1]").first();
		if (inputNearLabel.count() > 0 && inputNearLabel.isVisible()) {
			inputNearLabel.fill(value);
		}
	}

	private void waitForSidebar(final Page page) {
		final Locator sidebar = page.locator("aside, nav").first();
		sidebar.waitFor(new Locator.WaitForOptions().setTimeout(UI_TIMEOUT_MS));
	}

	private boolean isSidebarVisible(final Page page) {
		try {
			final Locator sidebar = page.locator("aside, nav").first();
			return sidebar.isVisible();
		} catch (RuntimeException e) {
			return false;
		}
	}

	private void clickByText(final Page page, final String text) {
		final Pattern pattern = Pattern.compile("(?i)" + Pattern.quote(text));

		final Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)).first();
		if (button.count() > 0 && button.isVisible()) {
			button.click(new Locator.ClickOptions().setTimeout(UI_TIMEOUT_MS));
			waitForUiSettle(page);
			return;
		}

		final Locator link = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)).first();
		if (link.count() > 0 && link.isVisible()) {
			link.click(new Locator.ClickOptions().setTimeout(UI_TIMEOUT_MS));
			waitForUiSettle(page);
			return;
		}

		final Locator textLocator = page.getByText(pattern).first();
		if (textLocator.count() > 0 && textLocator.isVisible()) {
			textLocator.click(new Locator.ClickOptions().setTimeout(UI_TIMEOUT_MS));
			waitForUiSettle(page);
			return;
		}

		throw new AssertionError("Could not click element by text: " + text);
	}

	private void clickByAnyText(final Page page, final String... texts) {
		RuntimeException lastException = null;
		for (String text : texts) {
			try {
				clickByText(page, text);
				return;
			} catch (RuntimeException e) {
				lastException = e;
			}
		}
		if (lastException != null) {
			throw lastException;
		}
	}

	private void clickIfVisible(final Page page, final String text) {
		final Pattern pattern = Pattern.compile("(?i)" + Pattern.quote(text));
		final Locator candidate = page.getByText(pattern).first();
		if (candidate.count() > 0 && candidate.isVisible()) {
			candidate.click();
			waitForUiSettle(page);
		}
	}

	private void waitForVisibleText(final Page page, final String text, final int timeoutMs) {
		final Pattern textPattern = Pattern.compile("(?i)" + Pattern.quote(text));
		page.getByText(textPattern).first().waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
	}

	private boolean isEmailVisible(final Locator scope) {
		try {
			final Locator email = scope.getByText(EMAIL_PATTERN).first();
			return email.count() > 0 && email.isVisible();
		} catch (RuntimeException e) {
			return false;
		}
	}

	private boolean isAnyVisible(final Page page, final String... texts) {
		for (String text : texts) {
			final Pattern pattern = Pattern.compile("(?i)" + Pattern.quote(text));
			try {
				final Locator locator = page.getByText(pattern).first();
				if (locator.count() > 0 && locator.isVisible()) {
					return true;
				}
			} catch (RuntimeException ignored) {
				// Continue trying other candidates.
			}
		}
		return false;
	}

	private boolean isAnyVisibleNormalized(final Page page, final String text) {
		return isAnyVisible(page, text, normalizeSpanish(text));
	}

	private String normalizeSpanish(final String text) {
		return text
				.replace("á", "a")
				.replace("Á", "A")
				.replace("é", "e")
				.replace("É", "E")
				.replace("í", "i")
				.replace("Í", "I")
				.replace("ó", "o")
				.replace("Ó", "O")
				.replace("ú", "u")
				.replace("Ú", "U")
				.replace("ñ", "n")
				.replace("Ñ", "N");
	}

	private void waitForUiSettle(final Page page) {
		try {
			page.waitForLoadState(Page.LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(UI_TIMEOUT_MS));
		} catch (RuntimeException ignored) {
			// Some SPA transitions do not trigger load states.
		}
		try {
			page.waitForLoadState(Page.LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (RuntimeException ignored) {
			// NETWORKIDLE is best-effort for dynamic frontends.
		}
		page.waitForTimeout(600);
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(evidenceDir.resolve(fileName))
				.setFullPage(fullPage));
	}

	private void recordResult(
			final Map<String, Boolean> report,
			final List<String> failures,
			final String key,
			final boolean passed,
			final String failureMessage) {
		report.put(key, passed);
		if (!passed && failureMessage != null && !failureMessage.isBlank()) {
			failures.add(key + " -> " + failureMessage);
		}
	}
}
