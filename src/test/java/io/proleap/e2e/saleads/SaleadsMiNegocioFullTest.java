package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final int UI_TIMEOUT_MS = 30000;
	private static final double VISIBILITY_TIMEOUT_MS = 8000;
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Path SCREENSHOT_DIR = Paths.get("target", "screenshots", TEST_NAME);

	@Test
	public void runSaleadsMiNegocioWorkflow() throws Exception {
		Files.createDirectories(SCREENSHOT_DIR);

		final LinkedHashMap<String, String> report = initReport();
		final List<String> failures = new ArrayList<>();
		final LinkedHashMap<String, String> capturedUrls = new LinkedHashMap<>();

		boolean canContinue;
		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
					.setHeadless(Boolean.parseBoolean(System.getenv().getOrDefault("HEADLESS", "true")));

			try (Browser browser = playwright.chromium().launch(launchOptions); BrowserContext context = browser.newContext()) {
				final Page page = context.newPage();

				canContinue = executeStep(report, failures, true, "Login", () -> stepLoginWithGoogle(context, page));
				canContinue = executeStep(report, failures, canContinue, "Mi Negocio menu",
						() -> stepOpenMiNegocioMenu(page));
				canContinue = executeStep(report, failures, canContinue, "Agregar Negocio modal",
						() -> stepValidateAgregarNegocioModal(page));
				canContinue = executeStep(report, failures, canContinue, "Administrar Negocios view",
						() -> stepOpenAdministrarNegocios(page));
				canContinue = executeStep(report, failures, canContinue, "Información General",
						() -> stepValidateInformacionGeneral(page));
				canContinue = executeStep(report, failures, canContinue, "Detalles de la Cuenta",
						() -> stepValidateDetallesDeLaCuenta(page));
				canContinue = executeStep(report, failures, canContinue, "Tus Negocios",
						() -> stepValidateTusNegocios(page));
				canContinue = executeStep(report, failures, canContinue, "Términos y Condiciones", () -> {
					final String finalUrl = stepValidateLegalLink(context, page, "Términos y Condiciones",
							Pattern.compile("(?i)T[eé]rminos y Condiciones"), "05-terminos-y-condiciones.png");
					capturedUrls.put("Términos y Condiciones", finalUrl);
				});
				executeStep(report, failures, canContinue, "Política de Privacidad", () -> {
					final String finalUrl = stepValidateLegalLink(context, page, "Política de Privacidad",
							Pattern.compile("(?i)Pol[ií]tica de Privacidad"), "06-politica-de-privacidad.png");
					capturedUrls.put("Política de Privacidad", finalUrl);
				});
			}
		}

		printFinalReport(report, capturedUrls);
		if (!failures.isEmpty()) {
			fail(String.join(System.lineSeparator(), failures));
		}
	}

	private void stepLoginWithGoogle(final BrowserContext context, final Page page) throws IOException {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (loginUrl == null || loginUrl.isBlank()) {
			throw new IllegalStateException(
					"SALEADS_LOGIN_URL is required so the test can open the current environment login page.");
		}

		page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
		page.waitForLoadState(LoadState.NETWORKIDLE);

		if (!isSidebarVisible(page)) {
			final Pattern loginPattern = Pattern.compile(
					"(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)");
			final Locator loginButton = requireVisibleLocator("Google login button",
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(loginPattern)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(loginPattern)),
					page.getByText(loginPattern));

			final Page popup = clickAndCapturePopup(context, page, loginButton);
			final Page authPage = popup == null ? page : popup;

			selectGoogleAccountIfVisible(authPage);
		}

		waitForMainInterface(page);
		screenshot(page, "01-dashboard-loaded.png", true);
	}

	private void stepOpenMiNegocioMenu(final Page page) throws IOException {
		if (!isTextVisible(page, "Mi Negocio")) {
			clickByText(page, "Negocio");
		}

		clickByText(page, "Mi Negocio");
		if (!isTextVisible(page, "Agregar Negocio")) {
			// Some sidebars require one extra expand click when collapsed.
			clickByText(page, "Mi Negocio");
		}

		requireVisibleLocator("Agregar Negocio option", page.getByText(Pattern.compile("(?i)^\\s*Agregar Negocio\\s*$")));
		requireVisibleLocator("Administrar Negocios option",
				page.getByText(Pattern.compile("(?i)^\\s*Administrar Negocios\\s*$")));
		screenshot(page, "02-mi-negocio-menu-expanded.png", true);
	}

	private void stepValidateAgregarNegocioModal(final Page page) throws IOException {
		clickByText(page, "Agregar Negocio");

		requireVisibleLocator("Crear Nuevo Negocio title",
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Crear Nuevo Negocio"))),
				page.getByText(Pattern.compile("(?i)Crear Nuevo Negocio")));

		final Locator businessNameInput = requireVisibleLocator("Nombre del Negocio input",
				page.getByLabel(Pattern.compile("(?i)Nombre del Negocio")),
				page.getByPlaceholder(Pattern.compile("(?i)Nombre del Negocio")));

		requireVisibleLocator("Tienes 2 de 3 negocios text", page.getByText(Pattern.compile("(?i)Tienes 2 de 3 negocios")));
		requireVisibleLocator("Cancelar button",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cancelar"))));
		requireVisibleLocator("Crear Negocio button",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Crear Negocio"))));

		screenshot(page, "03-agregar-negocio-modal.png", true);

		businessNameInput.click(new Locator.ClickOptions().setTimeout((double) UI_TIMEOUT_MS));
		businessNameInput.fill("Negocio Prueba Automatizacion");
		clickByText(page, "Cancelar");
	}

	private void stepOpenAdministrarNegocios(final Page page) throws IOException {
		if (!isTextVisible(page, "Administrar Negocios")) {
			clickByText(page, "Mi Negocio");
		}

		clickByText(page, "Administrar Negocios");
		page.waitForLoadState(LoadState.NETWORKIDLE);

		requireVisibleLocator("Información General section",
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Informaci[oó]n General"))),
				page.getByText(Pattern.compile("(?i)Informaci[oó]n General")));
		requireVisibleLocator("Detalles de la Cuenta section",
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Detalles de la Cuenta"))),
				page.getByText(Pattern.compile("(?i)Detalles de la Cuenta")));
		requireVisibleLocator("Tus Negocios section",
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Tus Negocios"))),
				page.getByText(Pattern.compile("(?i)Tus Negocios")));
		requireVisibleLocator("Sección Legal section",
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Secci[oó]n Legal"))),
				page.getByText(Pattern.compile("(?i)Secci[oó]n Legal")));
		screenshot(page, "04-administrar-negocios-view.png", true);
	}

	private void stepValidateInformacionGeneral(final Page page) {
		final Locator infoSection = sectionByHeading(page, "Informaci[oó]n General");
		final String infoText = infoSection.innerText();

		assertTrue("Expected user email in Información General section.", EMAIL_PATTERN.matcher(infoText).find());
		assertTrue("Expected BUSINESS PLAN text in Información General section.",
				Pattern.compile("(?i)BUSINESS PLAN").matcher(infoText).find());
		requireVisibleLocator("Cambiar Plan button",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cambiar Plan"))));

		final String expectedUserName = System.getenv("SALEADS_EXPECTED_USER_NAME");
		if (expectedUserName != null && !expectedUserName.isBlank()) {
			requireVisibleLocator("Expected user name",
					page.getByText(Pattern.compile(Pattern.quote(expectedUserName), Pattern.CASE_INSENSITIVE)));
			return;
		}

		final Matcher emailMatcher = EMAIL_PATTERN.matcher(infoText);
		assertTrue("Expected user email in Información General section.", emailMatcher.find());
		final String beforeEmail = infoText.substring(0, emailMatcher.start()).replaceAll("(?i)Informaci[oó]n General", "").trim();
		assertTrue("Expected a visible user name before the user email in Información General section.",
				beforeEmail.length() >= 2);
	}

	private void stepValidateDetallesDeLaCuenta(final Page page) {
		final Locator detailsSection = sectionByHeading(page, "Detalles de la Cuenta");
		final String detailsText = detailsSection.innerText();

		assertTrue("Missing 'Cuenta creada' in Detalles de la Cuenta.", Pattern.compile("(?i)Cuenta creada").matcher(detailsText).find());
		assertTrue("Missing 'Estado activo' in Detalles de la Cuenta.", Pattern.compile("(?i)Estado activo").matcher(detailsText).find());
		assertTrue("Missing 'Idioma seleccionado' in Detalles de la Cuenta.",
				Pattern.compile("(?i)Idioma seleccionado").matcher(detailsText).find());
	}

	private void stepValidateTusNegocios(final Page page) {
		final Locator businessesSection = sectionByHeading(page, "Tus Negocios");
		final String sectionText = businessesSection.innerText();

		requireVisibleLocator("Agregar Negocio button in Tus Negocios",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Agregar Negocio"))),
				page.getByText(Pattern.compile("(?i)^\\s*Agregar Negocio\\s*$")));
		assertTrue("Missing 'Tienes 2 de 3 negocios' in Tus Negocios section.",
				Pattern.compile("(?i)Tienes 2 de 3 negocios").matcher(sectionText).find());

		final int rowLikeItems = businessesSection.locator("li, [role='row'], [data-testid*='business'], .card").count();
		assertTrue("Expected business list content to be visible in Tus Negocios section.",
				rowLikeItems > 0 || sectionText.replaceAll("(?i)Tus Negocios", "").trim().length() > 20);
	}

	private String stepValidateLegalLink(final BrowserContext context, final Page appPage, final String linkText,
			final Pattern headingPattern, final String screenshotName) throws IOException {
		final Locator link = requireVisibleLocator(linkText + " link",
				appPage.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*" + Pattern.quote(linkText) + "\\s*$"))),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*" + Pattern.quote(linkText) + "\\s*$"))),
				appPage.getByText(Pattern.compile("(?i)^\\s*" + Pattern.quote(linkText) + "\\s*$")));

		final String appUrlBefore = appPage.url();
		Page legalPage;
		boolean openedNewTab = false;
		try {
			legalPage = context.waitForPage(() -> link.click(new Locator.ClickOptions().setTimeout((double) UI_TIMEOUT_MS)),
					new BrowserContext.WaitForPageOptions().setTimeout(7000));
			openedNewTab = true;
			appPage.waitForTimeout(500);
		} catch (PlaywrightException timeoutOrNoNewTab) {
			clickAndWait(appPage, link);
			legalPage = appPage;
		}

		legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		legalPage.waitForTimeout(1000);

		requireVisibleLocator(linkText + " heading",
				legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
				legalPage.getByText(headingPattern));

		final String legalContent = legalPage.locator("body").innerText();
		assertTrue("Expected legal content text for " + linkText + ".", legalContent != null && legalContent.trim().length() > 120);

		screenshot(legalPage, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
		} else if (!appPage.url().equals(appUrlBefore)) {
			appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			appPage.waitForLoadState(LoadState.NETWORKIDLE);
		}

		return finalUrl;
	}

	private void waitForMainInterface(final Page page) {
		requireVisibleLocator("left sidebar navigation", page.locator("aside"), page.locator("nav"),
				page.getByText(Pattern.compile("(?i)Negocio")));
		page.waitForLoadState(LoadState.NETWORKIDLE);
	}

	private void selectGoogleAccountIfVisible(final Page authPage) {
		final Locator accountOption = authPage.getByText(Pattern.compile("^\\s*" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + "\\s*$"));
		try {
			accountOption.first().waitFor(
					new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
			clickAndWait(authPage, accountOption.first());
		} catch (PlaywrightException ignored) {
			// Account selector is optional when the current browser session is already authenticated.
		}
	}

	private Page clickAndCapturePopup(final BrowserContext context, final Page clickPage, final Locator locator) {
		try {
			final Page popup = context.waitForPage(
					() -> locator.click(new Locator.ClickOptions().setTimeout((double) UI_TIMEOUT_MS)),
					new BrowserContext.WaitForPageOptions().setTimeout(7000));
			clickPage.waitForTimeout(500);
			return popup;
		} catch (PlaywrightException noPopupOpened) {
			clickPage.waitForTimeout(500);
			return null;
		}
	}

	private void clickByText(final Page page, final String text) {
		final Pattern exactText = Pattern.compile("(?i)^\\s*" + Pattern.quote(text) + "\\s*$");
		final Locator clickable = requireVisibleLocator("clickable text '" + text + "'",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(exactText)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(exactText)),
				page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(exactText)),
				page.getByText(exactText));
		clickAndWait(page, clickable);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click(new Locator.ClickOptions().setTimeout((double) UI_TIMEOUT_MS));
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(600);
	}

	private Locator sectionByHeading(final Page page, final String headingRegex) {
		final Locator heading = requireVisibleLocator("section heading " + headingRegex,
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + headingRegex))),
				page.getByText(Pattern.compile("(?i)" + headingRegex)));
		return heading.locator("xpath=ancestor::*[self::section or self::div][1]");
	}

	private boolean isTextVisible(final Page page, final String text) {
		final Pattern exact = Pattern.compile("(?i)^\\s*" + Pattern.quote(text) + "\\s*$");
		final Locator locator = page.getByText(exact).first();
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(2000));
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private boolean isSidebarVisible(final Page page) {
		try {
			requireVisibleLocator("sidebar", page.locator("aside"), page.locator("nav"),
					page.getByText(Pattern.compile("(?i)Negocio")));
			return true;
		} catch (AssertionError noSidebarYet) {
			return false;
		}
	}

	private Locator requireVisibleLocator(final String description, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate == null) {
				continue;
			}

			final Locator first = candidate.first();
			try {
				first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(VISIBILITY_TIMEOUT_MS));
				return first;
			} catch (PlaywrightException ignored) {
				// Try next selector variant.
			}
		}

		throw new AssertionError("Expected visible element: " + description);
	}

	private void screenshot(final Page page, final String fileName, final boolean fullPage) throws IOException {
		page.screenshot(new Page.ScreenshotOptions().setPath(SCREENSHOT_DIR.resolve(fileName)).setFullPage(fullPage));
	}

	private boolean executeStep(final Map<String, String> report, final List<String> failures, final boolean canContinue,
			final String reportKey, final StepAction action) {
		if (!canContinue) {
			report.put(reportKey, "FAIL (blocked by previous step)");
			failures.add(reportKey + ": blocked by previous step.");
			return false;
		}

		try {
			action.run();
			report.put(reportKey, "PASS");
			return true;
		} catch (final Throwable error) {
			report.put(reportKey, "FAIL");
			final String message = error.getMessage() == null ? "No error message available." : error.getMessage();
			failures.add(reportKey + ": " + message);
			return false;
		}
	}

	private LinkedHashMap<String, String> initReport() {
		final LinkedHashMap<String, String> report = new LinkedHashMap<>();
		report.put("Login", "NOT RUN");
		report.put("Mi Negocio menu", "NOT RUN");
		report.put("Agregar Negocio modal", "NOT RUN");
		report.put("Administrar Negocios view", "NOT RUN");
		report.put("Información General", "NOT RUN");
		report.put("Detalles de la Cuenta", "NOT RUN");
		report.put("Tus Negocios", "NOT RUN");
		report.put("Términos y Condiciones", "NOT RUN");
		report.put("Política de Privacidad", "NOT RUN");
		return report;
	}

	private void printFinalReport(final Map<String, String> report, final Map<String, String> capturedUrls) {
		System.out.println("=== Final Report: " + TEST_NAME + " ===");
		for (final Map.Entry<String, String> stepResult : report.entrySet()) {
			System.out.println(stepResult.getKey() + ": " + stepResult.getValue());
		}

		if (!capturedUrls.isEmpty()) {
			System.out.println("=== Captured Legal URLs ===");
			for (final Map.Entry<String, String> entry : capturedUrls.entrySet()) {
				System.out.println(entry.getKey() + ": " + entry.getValue());
			}
		}

		System.out.println("Screenshots directory: " + SCREENSHOT_DIR.toAbsolutePath());
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
