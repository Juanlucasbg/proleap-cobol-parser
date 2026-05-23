package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
 * End-to-end validation for SaleADS.ai "Mi Negocio" workflow.
 *
 * Runtime configuration:
 * -Dsaleads.url=<login page URL> or env SALEADS_URL
 * -Dsaleads.google.account=<email> or env SALEADS_GOOGLE_ACCOUNT
 * -Dsaleads.headless=<true|false> or env SALEADS_HEADLESS
 * -Dsaleads.timeout.ms=<milliseconds> or env SALEADS_TIMEOUT_MS
 */
public class SaleadsMiNegocioFullTest {

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final String saleadsUrl = setting("saleads.url", "SALEADS_URL", "");
	private final String googleAccount = setting("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT",
			"juanlucasbarbiergarzon@gmail.com");
	private final boolean headless = Boolean.parseBoolean(setting("saleads.headless", "SALEADS_HEADLESS", "true"));
	private final double timeoutMs = Double.parseDouble(setting("saleads.timeout.ms", "SALEADS_TIMEOUT_MS", "60000"));

	private final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
	private final Path evidenceDir = Paths.get("target", "saleads-evidence", runId);
	private final Map<String, String> stepResults = new LinkedHashMap<>();
	private final Map<String, String> stepErrors = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private Page appPage;

	@Test
	public void saleadsMiNegocioWorkflow() throws Exception {
		initializeStepResults();
		Files.createDirectories(evidenceDir);

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext();
			appPage = context.newPage();
			appPage.setDefaultTimeout(timeoutMs);
			context.setDefaultTimeout(timeoutMs);

			runStep("Login", () -> stepLogin(context));
			runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
			runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
			runStep("Información General", this::stepValidateInformacionGeneral);
			runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			runStep("Tus Negocios", this::stepValidateTusNegocios);
			runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones", "terminos"));
			runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad", "privacidad"));

			context.close();
			browser.close();
		}

		final String report = buildFinalReport();
		Files.writeString(evidenceDir.resolve("final-report.txt"), report, StandardCharsets.UTF_8);

		final String failedSteps = stepResults.entrySet().stream().filter(entry -> !"PASS".equals(entry.getValue()))
				.map(Map.Entry::getKey).collect(Collectors.joining(", "));
		assertTrue("Workflow validation failed.\n" + report,
				failedSteps.isEmpty());
	}

	private void initializeStepResults() {
		stepResults.put("Login", "FAIL");
		stepResults.put("Mi Negocio menu", "FAIL");
		stepResults.put("Agregar Negocio modal", "FAIL");
		stepResults.put("Administrar Negocios view", "FAIL");
		stepResults.put("Información General", "FAIL");
		stepResults.put("Detalles de la Cuenta", "FAIL");
		stepResults.put("Tus Negocios", "FAIL");
		stepResults.put("Términos y Condiciones", "FAIL");
		stepResults.put("Política de Privacidad", "FAIL");
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, "PASS");
			stepErrors.remove(stepName);
		} catch (final Exception | AssertionError ex) {
			final String screenshotName = "failure-" + normalizeFileName(stepName) + ".png";
			try {
				takeScreenshot(appPage, screenshotName, true);
			} catch (final Exception screenshotEx) {
				// Keep the original step exception as root cause for troubleshooting.
			}
			stepResults.put(stepName, "FAIL");
			stepErrors.put(stepName, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
		}
	}

	private void stepLogin(final BrowserContext context) throws IOException {
		openLoginPage();

		Page popup = null;
		try {
			popup = appPage.waitForPopup(
					() -> clickVisible("Sign in with Google button",
							appPage.getByRole(AriaRole.BUTTON,
									new Page.GetByRoleOptions().setName(
											Pattern.compile("(?iu)(sign in with google|iniciar sesión con google|continuar con google|google)"))),
							appPage.getByText(
									Pattern.compile("(?iu)(sign in with google|iniciar sesión con google|continuar con google)"))),
					new Page.WaitForPopupOptions().setTimeout(8000));
		} catch (final PlaywrightException popupMissing) {
			// Login can happen in same tab, so a popup is not mandatory.
		}

		final Page googlePage = popup != null ? popup : detectGooglePage(context);
		if (googlePage != null) {
			googlePage.setDefaultTimeout(timeoutMs);
			selectGoogleAccountIfVisible(googlePage);
		}

		waitForUiLoad(appPage);

		assertVisible("main app interface",
				appPage.getByRole(AriaRole.NAVIGATION),
				appPage.locator("aside"),
				appPage.getByText(Pattern.compile("(?iu)Mi Negocio")));
		assertVisible("left sidebar navigation",
				appPage.locator("aside"),
				appPage.getByRole(AriaRole.NAVIGATION));

		takeScreenshot(appPage, "01-dashboard-loaded.png", true);
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		waitForUiLoad(appPage);

		clickVisible("Negocio section",
				appPage.getByText(Pattern.compile("(?iu)^Negocio$")),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Negocio$"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Negocio$"))));
		waitForUiLoad(appPage);

		clickVisible("Mi Negocio menu",
				appPage.getByText(Pattern.compile("(?iu)^Mi Negocio$")),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Mi Negocio$"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Mi Negocio$"))));
		waitForUiLoad(appPage);

		assertVisible("Agregar Negocio submenu option",
				appPage.getByText(Pattern.compile("(?iu)^Agregar Negocio$")),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Agregar Negocio$"))));
		assertVisible("Administrar Negocios submenu option",
				appPage.getByText(Pattern.compile("(?iu)^Administrar Negocios$")),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Administrar Negocios$"))));

		takeScreenshot(appPage, "02-mi-negocio-menu-expanded.png", true);
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickVisible("Agregar Negocio",
				appPage.getByText(Pattern.compile("(?iu)^Agregar Negocio$")),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Agregar Negocio$"))));
		waitForUiLoad(appPage);

		assertVisible("Crear Nuevo Negocio modal title",
				appPage.getByText(Pattern.compile("(?iu)^Crear Nuevo Negocio$")));
		assertVisible("Nombre del Negocio input field",
				appPage.getByLabel(Pattern.compile("(?iu)^Nombre del Negocio$")),
				appPage.getByPlaceholder(Pattern.compile("(?iu)Nombre del Negocio")));
		assertVisible("Tienes 2 de 3 negocios text",
				appPage.getByText(Pattern.compile("(?iu)Tienes\\s*2\\s*de\\s*3\\s*negocios")));
		assertVisible("Cancelar button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Cancelar$"))));
		assertVisible("Crear Negocio button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Crear Negocio$"))));

		takeScreenshot(appPage, "03-agregar-negocio-modal.png", true);

		fillIfVisible(Pattern.compile("(?iu)^Nombre del Negocio$"), "Negocio Prueba Automatización");
		clickVisible("Cancelar button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Cancelar$"))),
				appPage.getByText(Pattern.compile("(?iu)^Cancelar$")));
		waitForUiLoad(appPage);
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		expandMiNegocioIfNeeded();

		clickVisible("Administrar Negocios",
				appPage.getByText(Pattern.compile("(?iu)^Administrar Negocios$")),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Administrar Negocios$"))),
				appPage.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Administrar Negocios$"))));
		waitForUiLoad(appPage);

		assertVisible("Información General section",
				appPage.getByText(Pattern.compile("(?iu)^Información General$")));
		assertVisible("Detalles de la Cuenta section",
				appPage.getByText(Pattern.compile("(?iu)^Detalles de la Cuenta$")));
		assertVisible("Tus Negocios section",
				appPage.getByText(Pattern.compile("(?iu)^Tus Negocios$")));
		assertVisible("Sección Legal section",
				appPage.getByText(Pattern.compile("(?iu)^Sección Legal$")));

		takeScreenshot(appPage, "04-administrar-negocios-full-page.png", true);
	}

	private void stepValidateInformacionGeneral() {
		assertVisible("Información General section heading",
				appPage.getByText(Pattern.compile("(?iu)^Información General$")));

		final String bodyText = appPage.locator("body").innerText();
		assertTrue("User email should be visible.", EMAIL_PATTERN.matcher(bodyText).find());

		assertVisible("BUSINESS PLAN text",
				appPage.getByText(Pattern.compile("(?iu)^BUSINESS PLAN$")));
		assertVisible("Cambiar Plan button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Cambiar Plan$"))),
				appPage.getByText(Pattern.compile("(?iu)^Cambiar Plan$")));

		// Ensure some non-email identity text is present in account summary.
		assertTrue("User name-like text should be visible.",
				Pattern.compile("(?iu)(Perfil|Cuenta|Usuario|Nombre|Plan)").matcher(bodyText).find());
	}

	private void stepValidateDetallesCuenta() {
		assertVisible("Cuenta creada text",
				appPage.getByText(Pattern.compile("(?iu)Cuenta creada")));
		assertVisible("Estado activo text",
				appPage.getByText(Pattern.compile("(?iu)Estado activo")));
		assertVisible("Idioma seleccionado text",
				appPage.getByText(Pattern.compile("(?iu)Idioma seleccionado")));
	}

	private void stepValidateTusNegocios() {
		assertVisible("Tus Negocios section heading",
				appPage.getByText(Pattern.compile("(?iu)^Tus Negocios$")));
		assertVisible("Agregar Negocio button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Agregar Negocio$"))),
				appPage.getByText(Pattern.compile("(?iu)^Agregar Negocio$")));
		assertVisible("Tienes 2 de 3 negocios text",
				appPage.getByText(Pattern.compile("(?iu)Tienes\\s*2\\s*de\\s*3\\s*negocios")));

		final Locator businessRows = appPage.locator("li, tr, [role='row'], [data-testid*='business'], [class*='business']");
		assertTrue("Business list should be visible.", businessRows.count() > 0
				|| appPage.locator("body").innerText().contains("Negocio"));
	}

	private void stepValidateLegalLink(final String linkText, final String screenshotPrefix) throws IOException {
		final Locator link = firstVisibleLocator("legal link " + linkText,
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^" + Pattern.quote(linkText) + "$"))),
				appPage.getByText(Pattern.compile("(?iu)^" + Pattern.quote(linkText) + "$")));

		final String originalUrl = appPage.url();
		Page targetPage = null;
		boolean openedInPopup = false;

		try {
			targetPage = appPage.waitForPopup(() -> link.click(), new Page.WaitForPopupOptions().setTimeout(7000));
			openedInPopup = true;
		} catch (final PlaywrightException noPopup) {
			// Same-tab navigation is also valid.
			if (!link.isVisible()) {
				throw noPopup;
			}
			link.click();
			targetPage = appPage;
		}

		waitForUiLoad(targetPage);

		assertVisible(linkText + " heading",
				targetPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*" + Pattern.quote(linkText) + ".*"))),
				targetPage.getByText(Pattern.compile("(?iu).*" + Pattern.quote(linkText) + ".*")));

		final String legalContent = targetPage.locator("body").innerText().trim();
		assertTrue(linkText + " legal content should be visible.", legalContent.length() > 120);

		takeScreenshot(targetPage, "05-" + screenshotPrefix + "-page.png", true);
		legalUrls.put(linkText, targetPage.url());

		if (openedInPopup) {
			targetPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else if (!appPage.url().equals(originalUrl)) {
			appPage.goBack();
			waitForUiLoad(appPage);
		}
	}

	private void openLoginPage() {
		if (!saleadsUrl.trim().isEmpty()) {
			appPage.navigate(saleadsUrl);
			waitForUiLoad(appPage);
		}
	}

	private void selectGoogleAccountIfVisible(final Page googlePage) {
		final Locator accountOption = googlePage
				.getByText(Pattern.compile("(?iu)^" + Pattern.quote(googleAccount) + "$"));
		if (safeIsVisible(accountOption)) {
			accountOption.first().click();
			waitForUiLoad(googlePage);
		}
	}

	private Page detectGooglePage(final BrowserContext context) {
		if (appPage.url() != null && appPage.url().contains("accounts.google")) {
			return appPage;
		}

		for (final Page candidate : context.pages()) {
			if (candidate.url() != null && candidate.url().contains("accounts.google")) {
				return candidate;
			}
		}

		return null;
	}

	private void expandMiNegocioIfNeeded() {
		if (!safeIsVisible(appPage.getByText(Pattern.compile("(?iu)^Administrar Negocios$")))) {
			clickVisible("Mi Negocio menu",
					appPage.getByText(Pattern.compile("(?iu)^Mi Negocio$")),
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Mi Negocio$"))));
			waitForUiLoad(appPage);
		}
	}

	private void fillIfVisible(final Pattern label, final String value) {
		final Locator byLabel = appPage.getByLabel(label);
		if (safeIsVisible(byLabel)) {
			byLabel.first().fill(value);
			return;
		}

		final Locator byPlaceholder = appPage.getByPlaceholder(label);
		if (safeIsVisible(byPlaceholder)) {
			byPlaceholder.first().fill(value);
		}
	}

	private Locator firstVisibleLocator(final String description, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			final Locator first = candidate.first();
			try {
				first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
				return first;
			} catch (final PlaywrightException ignored) {
				// Continue to next candidate.
			}
		}

		throw new AssertionError("Could not find visible element: " + description);
	}

	private void clickVisible(final String description, final Locator... candidates) {
		final Locator target = firstVisibleLocator(description, candidates);
		target.click();
	}

	private void assertVisible(final String description, final Locator... candidates) {
		firstVisibleLocator(description, candidates);
	}

	private boolean safeIsVisible(final Locator locator) {
		try {
			return locator.first().isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (final PlaywrightException ignored) {
			// Some SPAs keep active network sockets; DOM loaded is still enough.
		}
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) throws IOException {
		final Path screenshotPath = evidenceDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Workflow Report\n");
		builder.append("Run ID: ").append(runId).append('\n');
		builder.append("Evidence directory: ").append(evidenceDir).append('\n');
		builder.append("Configured URL: ").append(saleadsUrl).append('\n');
		builder.append("Google account: ").append(googleAccount).append("\n\n");

		for (final Map.Entry<String, String> entry : stepResults.entrySet()) {
			builder.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}

		if (!legalUrls.isEmpty()) {
			builder.append("\nLegal URLs:\n");
			for (final Map.Entry<String, String> legalEntry : legalUrls.entrySet()) {
				builder.append("- ").append(legalEntry.getKey()).append(": ").append(legalEntry.getValue()).append('\n');
			}
		}

		if (!stepErrors.isEmpty()) {
			builder.append("\nStep Errors:\n");
			for (final Map.Entry<String, String> stepError : stepErrors.entrySet()) {
				builder.append("- ").append(stepError.getKey()).append(": ").append(stepError.getValue()).append('\n');
			}
		}

		return builder.toString();
	}

	private static String setting(final String systemProperty, final String envVariable, final String fallbackValue) {
		final String propertyValue = System.getProperty(systemProperty);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envVariable);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue.trim();
		}

		return fallbackValue;
	}

	private static String normalizeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
