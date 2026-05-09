package io.proleap.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_POLITICA = "Política de Privacidad";

	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-evidence");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	@Test
	public void saleads_mi_negocio_full_test() throws Exception {
		Files.createDirectories(EVIDENCE_DIR);

		final LinkedHashMap<String, StepResult> report = new LinkedHashMap<>();
		initializeReport(report);

		Throwable fatalError = null;

		try (Playwright playwright = Playwright.create(); BrowserSession session = BrowserSession.open(playwright)) {
			final Page page = session.page;

			executeStep(report, REPORT_LOGIN, () -> executeLoginStep(page));
			executeStep(report, REPORT_MI_NEGOCIO_MENU, () -> executeMiNegocioMenuStep(page));
			executeStep(report, REPORT_AGREGAR_MODAL, () -> executeAgregarNegocioModalStep(page));
			executeStep(report, REPORT_ADMINISTRAR_VIEW, () -> executeAdministrarNegociosStep(page));
			executeStep(report, REPORT_INFO_GENERAL, () -> executeInformacionGeneralStep(page));
			executeStep(report, REPORT_DETALLES_CUENTA, () -> executeDetallesCuentaStep(page));
			executeStep(report, REPORT_TUS_NEGOCIOS, () -> executeTusNegociosStep(page));
			executeStep(report, REPORT_TERMINOS, () -> executeLegalLinkStep(page, "Términos y Condiciones",
					Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"), "08-terminos-y-condiciones"));
			executeStep(report, REPORT_POLITICA, () -> executeLegalLinkStep(page, "Política de Privacidad",
					Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"), "09-politica-de-privacidad"));
		} catch (Throwable throwable) {
			fatalError = throwable;
			markUnexecutedStepsAsFailed(report,
					"Execution stopped due to fatal error: " + safeMessage(throwable));
		}

		printFinalReport(report);
		assertAllStepsPassed(report, fatalError);
	}

	private String executeLoginStep(final Page page) {
		final Locator loginButton = waitForAnyVisibleText(page,
				Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
						"Continuar con Google", "Acceder con Google"),
				30_000, "Login button was not visible");

		Page popup = null;
		try {
			popup = page.context().waitForPage(loginButton::click, new BrowserContext.WaitForPageOptions().setTimeout(8_000));
		} catch (PlaywrightException ignored) {
			loginButton.click();
		}
		waitForUi(page);

		final Page authSurface = popup != null ? popup : page;
		waitForUi(authSurface);
		selectGoogleAccountIfVisible(authSurface);

		if (popup != null) {
			try {
				popup.waitForClose(new Page.WaitForCloseOptions().setTimeout(25_000));
			} catch (PlaywrightException ignored) {
				// The popup may stay open with some account configurations.
			}
		}

		waitForUi(page);
		waitForAnyVisibleText(page, Arrays.asList("Negocio", "Mi Negocio"), 60_000,
				"Main app interface did not load");
		assertSidebarVisible(page);

		final Path screenshot = takeScreenshot(page, "01-dashboard-loaded", true);
		return "Dashboard loaded and sidebar visible. Screenshot: " + screenshot;
	}

	private String executeMiNegocioMenuStep(final Page page) {
		clickVisibleText(page, "Negocio");
		clickVisibleText(page, "Mi Negocio");

		waitForVisibleText(page, "Agregar Negocio", 20_000, "'Agregar Negocio' was not visible");
		waitForVisibleText(page, "Administrar Negocios", 20_000, "'Administrar Negocios' was not visible");

		final Path screenshot = takeScreenshot(page, "02-mi-negocio-menu-expanded", false);
		return "Mi Negocio menu expanded and submenu options are visible. Screenshot: " + screenshot;
	}

	private String executeAgregarNegocioModalStep(final Page page) {
		clickVisibleText(page, "Agregar Negocio");

		waitForVisibleText(page, "Crear Nuevo Negocio", 20_000, "Modal title was not visible");
		waitForVisibleText(page, "Nombre del Negocio", 10_000, "'Nombre del Negocio' field was not visible");
		waitForVisibleText(page, "Tienes 2 de 3 negocios", 10_000, "Business quota text was not visible");
		waitForVisibleText(page, "Cancelar", 10_000, "'Cancelar' button was not visible");
		waitForVisibleText(page, "Crear Negocio", 10_000, "'Crear Negocio' button was not visible");

		final Path screenshot = takeScreenshot(page, "03-agregar-negocio-modal", false);

		// Optional interaction requested by workflow.
		final Locator input = resolveBusinessNameInput(page);
		input.click();
		input.fill("Negocio Prueba Automatizacion");
		clickVisibleText(page, "Cancelar");
		waitForUi(page);

		return "Agregar Negocio modal validated. Screenshot: " + screenshot;
	}

	private String executeAdministrarNegociosStep(final Page page) {
		if (!isTextVisible(page, "Administrar Negocios")) {
			clickVisibleText(page, "Mi Negocio");
		}
		clickVisibleText(page, "Administrar Negocios");

		waitForVisibleText(page, "Información General", 30_000, "Información General section was not visible");
		waitForVisibleText(page, "Detalles de la Cuenta", 20_000, "Detalles de la Cuenta section was not visible");
		waitForVisibleText(page, "Tus Negocios", 20_000, "Tus Negocios section was not visible");
		waitForVisibleText(page, "Sección Legal", 20_000, "Sección Legal section was not visible");

		final Path screenshot = takeScreenshot(page, "04-administrar-negocios", true);
		return "Administrar Negocios account view loaded. Screenshot: " + screenshot;
	}

	private String executeInformacionGeneralStep(final Page page) {
		final Locator section = findSectionByHeading(page, "Información General");
		waitForVisibleRegex(section, EMAIL_PATTERN, 12_000, "User email is not visible");
		waitForVisibleText(section, "BUSINESS PLAN", 12_000, "'BUSINESS PLAN' is not visible");
		waitForVisibleText(section, "Cambiar Plan", 12_000, "'Cambiar Plan' button is not visible");

		final Boolean hasLikelyName = (Boolean) section.evaluate("root => {" +
				"  const blocked = ['información general', 'business plan', 'cambiar plan'];" +
				"  const values = [...root.querySelectorAll('*')]" +
				"    .map(el => (el.textContent || '').trim())" +
				"    .filter(Boolean);" +
				"  return values.some(text => {" +
				"    const lower = text.toLowerCase();" +
				"    if (blocked.includes(lower)) return false;" +
				"    if (text.includes('@')) return false;" +
				"    if (text.length < 3) return false;" +
				"    return /[a-záéíóúñ]/i.test(text);" +
				"  });" +
				"}");
		Assert.assertTrue("User name was not visible in 'Información General'", Boolean.TRUE.equals(hasLikelyName));

		return "Información General section validated.";
	}

	private String executeDetallesCuentaStep(final Page page) {
		waitForVisibleText(page, "Cuenta creada", 15_000, "'Cuenta creada' is not visible");
		waitForVisibleText(page, "Estado activo", 15_000, "'Estado activo' is not visible");
		waitForVisibleText(page, "Idioma seleccionado", 15_000, "'Idioma seleccionado' is not visible");
		return "Detalles de la Cuenta section validated.";
	}

	private String executeTusNegociosStep(final Page page) {
		final Locator section = findSectionByHeading(page, "Tus Negocios");
		waitForVisibleText(section, "Agregar Negocio", 12_000, "'Agregar Negocio' button is not visible");
		waitForVisibleText(section, "Tienes 2 de 3 negocios", 12_000, "Business quota text is not visible");

		final Boolean listVisible = (Boolean) section.evaluate("root => {" +
				"  const rows = root.querySelectorAll('li, [role=\"row\"], table tbody tr, .business-card, .business-item');" +
				"  if (rows.length > 0) return true;" +
				"  return (root.innerText || '').trim().length > 40;" +
				"}");
		Assert.assertTrue("Business list is not visible", Boolean.TRUE.equals(listVisible));

		return "Tus Negocios section validated.";
	}

	private String executeLegalLinkStep(final Page appPage, final String linkText, final Pattern headingPattern,
			final String screenshotKey) {
		final String appUrlBefore = appPage.url();
		final BrowserContext context = appPage.context();
		final Locator link = waitForVisibleText(appPage, linkText, 15_000,
				"Legal link was not visible: " + linkText);

		Page targetPage = appPage;
		boolean openedInNewTab = false;

		try {
			targetPage = context.waitForPage(link::click, new BrowserContext.WaitForPageOptions().setTimeout(8_000));
			openedInNewTab = true;
		} catch (PlaywrightException ignored) {
			link.click();
		}

		waitForUi(appPage);
		waitForUi(targetPage);

		waitForVisibleRegex(targetPage, headingPattern, 20_000, "Legal heading was not visible for: " + linkText);

		final Number legalTextLength = (Number) targetPage.evaluate(
				"() => document.body && document.body.innerText ? document.body.innerText.trim().length : 0");
		Assert.assertTrue("Legal content text was not visible for: " + linkText, legalTextLength.intValue() > 200);

		final String finalUrl = targetPage.url();
		final Path screenshot = takeScreenshot(targetPage, screenshotKey, true);

		if (openedInNewTab) {
			targetPage.close();
			appPage.bringToFront();
		} else {
			try {
				appPage.goBack(new Page.GoBackOptions().setTimeout(12_000));
			} catch (PlaywrightException ignored) {
				appPage.navigate(appUrlBefore);
			}
			waitForUi(appPage);
		}

		return "Validated '" + linkText + "' URL: " + finalUrl + ". Screenshot: " + screenshot;
	}

	private void clickVisibleText(final Page page, final String text) {
		final Locator locator = waitForVisibleText(page, text, 20_000, "Could not find visible text: " + text);
		locator.click();
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(8_000));
		} catch (PlaywrightException ignored) {
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(6_000));
		} catch (PlaywrightException ignored) {
		}
		page.waitForTimeout(400);
	}

	private Locator resolveBusinessNameInput(final Page page) {
		final long deadline = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < deadline) {
			final Locator byLabel = page.getByLabel("Nombre del Negocio").first();
			if (isVisible(byLabel)) {
				return byLabel;
			}

			final Locator byPlaceholder = page.getByPlaceholder("Nombre del Negocio").first();
			if (isVisible(byPlaceholder)) {
				return byPlaceholder;
			}
			page.waitForTimeout(200);
		}

		Assert.fail("'Nombre del Negocio' input field was not found");
		return page.locator("input[name='__never__']");
	}

	private void selectGoogleAccountIfVisible(final Page authSurface) {
		final long deadline = System.currentTimeMillis() + 20_000;
		while (System.currentTimeMillis() < deadline) {
			final Locator account = authSurface.getByText(GOOGLE_ACCOUNT_EMAIL).first();
			if (isVisible(account)) {
				account.click();
				waitForUi(authSurface);
				return;
			}
			authSurface.waitForTimeout(350);
		}
	}

	private Locator findSectionByHeading(final Page page, final String headingText) {
		final Locator heading = waitForVisibleText(page, headingText, 20_000, "Section heading not found: " + headingText);
		Locator section = heading.locator("xpath=ancestor::section[1]").first();
		if (section.count() == 0) {
			section = heading.locator("xpath=ancestor::div[1]").first();
		}
		return section;
	}

	private void assertSidebarVisible(final Page page) {
		final Locator sidebar = page.locator("aside").first();
		if (isVisible(sidebar)) {
			return;
		}

		if (!isTextVisible(page, "Negocio") && !isTextVisible(page, "Mi Negocio")) {
			Assert.fail("Left sidebar navigation is not visible");
		}
	}

	private boolean isTextVisible(final Page page, final String text) {
		return isVisible(page.getByText(text).first());
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.count() > 0 && locator.first().isVisible();
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private Locator waitForVisibleText(final Page page, final String text, final long timeoutMs, final String errorMessage) {
		return waitForAnyVisibleText(page, Arrays.asList(text), timeoutMs, errorMessage);
	}

	private Locator waitForAnyVisibleText(final Page page, final List<String> texts, final long timeoutMs,
			final String errorMessage) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		final List<String> attempted = new ArrayList<>(texts);

		while (System.currentTimeMillis() < deadline) {
			for (final String text : texts) {
				final Locator locator = page.getByText(text).first();
				if (isVisible(locator)) {
					return locator;
				}
			}
			page.waitForTimeout(250);
		}

		Assert.fail(errorMessage + " (searched texts: " + attempted + ")");
		return page.locator("text=__never__"); // Unreachable but keeps compiler satisfied.
	}

	private void waitForVisibleText(final Locator parent, final String text, final long timeoutMs,
			final String errorMessage) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			final Locator candidate = parent.getByText(text).first();
			if (isVisible(candidate)) {
				return;
			}
			parent.page().waitForTimeout(250);
		}

		Assert.fail(errorMessage);
	}

	private void waitForVisibleRegex(final Page page, final Pattern pattern, final long timeoutMs,
			final String errorMessage) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			final Locator locator = page.getByText(pattern).first();
			if (isVisible(locator)) {
				return;
			}
			page.waitForTimeout(250);
		}
		Assert.fail(errorMessage);
	}

	private void waitForVisibleRegex(final Locator parent, final Pattern pattern, final long timeoutMs,
			final String errorMessage) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			final Locator locator = parent.getByText(pattern).first();
			if (isVisible(locator)) {
				return;
			}
			parent.page().waitForTimeout(250);
		}
		Assert.fail(errorMessage);
	}

	private Path takeScreenshot(final Page page, final String label, final boolean fullPage) {
		final String filename = Instant.now().toEpochMilli() + "-" + label + ".png";
		final Path target = EVIDENCE_DIR.resolve(filename);
		page.screenshot(new Page.ScreenshotOptions().setPath(target).setFullPage(fullPage));
		return target;
	}

	private void executeStep(final Map<String, StepResult> report, final String reportField, final StepAction action) {
		try {
			final String detail = action.run();
			report.put(reportField, new StepResult(true, detail == null ? "PASS" : detail));
		} catch (Throwable throwable) {
			report.put(reportField, new StepResult(false, safeMessage(throwable)));
		}
	}

	private void initializeReport(final Map<String, StepResult> report) {
		report.put(REPORT_LOGIN, new StepResult(false, "Not executed"));
		report.put(REPORT_MI_NEGOCIO_MENU, new StepResult(false, "Not executed"));
		report.put(REPORT_AGREGAR_MODAL, new StepResult(false, "Not executed"));
		report.put(REPORT_ADMINISTRAR_VIEW, new StepResult(false, "Not executed"));
		report.put(REPORT_INFO_GENERAL, new StepResult(false, "Not executed"));
		report.put(REPORT_DETALLES_CUENTA, new StepResult(false, "Not executed"));
		report.put(REPORT_TUS_NEGOCIOS, new StepResult(false, "Not executed"));
		report.put(REPORT_TERMINOS, new StepResult(false, "Not executed"));
		report.put(REPORT_POLITICA, new StepResult(false, "Not executed"));
	}

	private void markUnexecutedStepsAsFailed(final Map<String, StepResult> report, final String reason) {
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			if ("Not executed".equals(entry.getValue().detail)) {
				entry.setValue(new StepResult(false, reason));
			}
		}
	}

	private void printFinalReport(final Map<String, StepResult> report) {
		System.out.println();
		System.out.println("===== SaleADS Mi Negocio Final Report =====");
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			final String status = entry.getValue().passed ? "PASS" : "FAIL";
			System.out.println("- " + entry.getKey() + ": " + status + " -> " + entry.getValue().detail);
		}
		System.out.println("Evidence directory: " + EVIDENCE_DIR.toAbsolutePath());
		System.out.println("===========================================");
		System.out.println();
	}

	private void assertAllStepsPassed(final Map<String, StepResult> report, final Throwable fatalError) {
		final List<String> failed = new ArrayList<>();
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				failed.add(entry.getKey() + " -> " + entry.getValue().detail);
			}
		}

		if (fatalError != null) {
			failed.add("Fatal execution error -> " + safeMessage(fatalError));
		}

		Assert.assertTrue("Workflow validation failed:\n" + String.join("\n", failed), failed.isEmpty());
	}

	private String safeMessage(final Throwable throwable) {
		if (throwable == null) {
			return "Unknown error";
		}
		final String message = throwable.getMessage();
		return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
	}

	private static String config(final String propertyName, final String envName) {
		String value = System.getProperty(propertyName);
		if (value == null || value.isBlank()) {
			value = System.getenv(envName);
		}
		return value == null ? null : value.trim();
	}

	private static final class BrowserSession implements AutoCloseable {
		private final Browser browser;
		private final BrowserContext context;
		private final Page page;
		private final boolean connectedToExistingBrowser;

		private BrowserSession(final Browser browser, final BrowserContext context, final Page page,
				final boolean connectedToExistingBrowser) {
			this.browser = browser;
			this.context = context;
			this.page = page;
			this.connectedToExistingBrowser = connectedToExistingBrowser;
		}

		private static BrowserSession open(final Playwright playwright) {
			final String cdpUrl = config("saleads.cdpUrl", "SALEADS_CDP_URL");

			if (cdpUrl != null && !cdpUrl.isBlank()) {
				final Browser attachedBrowser = playwright.chromium().connectOverCDP(cdpUrl);
				final BrowserContext attachedContext = attachedBrowser.contexts().isEmpty()
						? attachedBrowser.newContext()
						: attachedBrowser.contexts().get(0);
				final Page attachedPage = attachedContext.pages().isEmpty()
						? attachedContext.newPage()
						: attachedContext.pages().get(0);
				return new BrowserSession(attachedBrowser, attachedContext, attachedPage, true);
			}

			final boolean headless = !"false".equalsIgnoreCase(config("saleads.headless", "SALEADS_HEADLESS"));
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser
					.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page page = context.newPage();

			final String startUrl = config("saleads.startUrl", "SALEADS_START_URL");
			if (startUrl != null && !startUrl.isBlank()) {
				page.navigate(startUrl);
			}

			if ("about:blank".equals(page.url())) {
				throw new IllegalStateException(
						"No SaleADS page was available. Set SALEADS_START_URL/saleads.startUrl or use SALEADS_CDP_URL/saleads.cdpUrl.");
			}

			return new BrowserSession(browser, context, page, false);
		}

		@Override
		public void close() {
			if (!connectedToExistingBrowser) {
				context.close();
				browser.close();
			}
		}
	}

	private interface StepAction {
		String run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}
	}
}
