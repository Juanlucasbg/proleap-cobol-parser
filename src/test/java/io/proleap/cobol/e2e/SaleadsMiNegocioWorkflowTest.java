package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
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
 * Full SaleADS "Mi Negocio" workflow validation with screenshots and step report.
 *
 * Required environment:
 * - SALEADS_BASE_URL (login page URL) or SALEADS_CDP_URL (Chrome DevTools endpoint).
 * Optional:
 * - SALEADS_HEADLESS (default: true)
 * - SALEADS_GOOGLE_ACCOUNT (default: juanlucasbarbiergarzon@gmail.com)
 * - SALEADS_SCREENSHOT_DIR (default: target/saleads-evidence)
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS_Y_CONDICIONES = "T\u00e9rminos y Condiciones";
	private static final String POLITICA_DE_PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private Path evidenceDir;
	private String terminosFinalUrl = "N/A";
	private String privacidadFinalUrl = "N/A";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String baseUrl = clean(System.getenv("SALEADS_BASE_URL"));
		final String cdpUrl = clean(System.getenv("SALEADS_CDP_URL"));

		Assume.assumeTrue(
				"Set SALEADS_BASE_URL (login page URL) or SALEADS_CDP_URL (existing browser session) to run this workflow.",
				hasText(baseUrl) || hasText(cdpUrl));

		final String googleAccount = cleanOrDefault(System.getenv("SALEADS_GOOGLE_ACCOUNT"),
				"juanlucasbarbiergarzon@gmail.com");
		final boolean headless = parseBoolean(System.getenv("SALEADS_HEADLESS"), true);

		evidenceDir = createEvidenceDir();
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());

		try (Playwright playwright = Playwright.create()) {
			final BrowserSession browserSession = createBrowserSession(playwright, cdpUrl, headless);
			final Browser browser = browserSession.browser;
			final BrowserContext context = browserSession.context;

			try (browser) {
				final Page appPage = resolveStartPage(context, baseUrl);
				configureTimeouts(appPage);

				runStep(LOGIN, () -> loginWithGoogle(appPage, googleAccount));
				runStep(MI_NEGOCIO_MENU, () -> openMiNegocioMenu(appPage));
				runStep(AGREGAR_NEGOCIO_MODAL, () -> validateAgregarNegocioModal(appPage));
				runStep(ADMINISTRAR_NEGOCIOS_VIEW, () -> openAdministrarNegocios(appPage));
				runStep(INFORMACION_GENERAL, () -> validateInformacionGeneral(appPage));
				runStep(DETALLES_CUENTA, () -> validateDetallesCuenta(appPage));
				runStep(TUS_NEGOCIOS, () -> validateTusNegocios(appPage));
				runStep(TERMINOS_Y_CONDICIONES,
						() -> validateLegalLink(appPage, context, TERMINOS_Y_CONDICIONES, "08-terminos-y-condiciones"));
				runStep(POLITICA_DE_PRIVACIDAD,
						() -> validateLegalLink(appPage, context, POLITICA_DE_PRIVACIDAD, "09-politica-de-privacidad"));
			}
		}

		writeFinalReport();
		Assert.assertTrue(buildFailureSummary(), allStepsPassed());
	}

	private BrowserSession createBrowserSession(final Playwright playwright, final String cdpUrl, final boolean headless) {
		if (hasText(cdpUrl)) {
			final Browser browser = playwright.chromium()
					.connectOverCDP(cdpUrl, new BrowserType.ConnectOverCDPOptions().setTimeout(30_000));
			final BrowserContext context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
			return new BrowserSession(browser, context);
		}

		final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		final BrowserContext context = browser.newContext();
		return new BrowserSession(browser, context);
	}

	private Page resolveStartPage(final BrowserContext context, final String baseUrl) {
		final Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
		if (hasText(baseUrl)) {
			page.navigate(baseUrl);
			waitForUi(page);
		}
		return page;
	}

	private void loginWithGoogle(final Page appPage, final String googleAccount) {
		final Locator loginButton = firstVisible(appPage,
				byRole(appPage, AriaRole.BUTTON, "(?i)(sign\\s*in\\s*with\\s*google|continuar\\s*con\\s*google|google|iniciar\\s*sesi[o\u00f3]n|ingresar|login)"),
				byRole(appPage, AriaRole.LINK, "(?i)(sign\\s*in\\s*with\\s*google|continuar\\s*con\\s*google|google|iniciar\\s*sesi[o\u00f3]n|ingresar|login)"),
				byText(appPage, "(?i)(sign\\s*in\\s*with\\s*google|continuar\\s*con\\s*google|iniciar\\s*sesi[o\u00f3]n\\s*con\\s*google)"));
		Assert.assertNotNull("Login button was not found.", loginButton);

		Page authPage = appPage;
		try {
			authPage = appPage.waitForPopup(() -> loginButton.click(),
					new Page.WaitForPopupOptions().setTimeout(8_000));
		} catch (final PlaywrightException popupNotOpened) {
			loginButton.click();
		}

		waitForUi(authPage);
		selectGoogleAccountIfVisible(authPage, googleAccount);
		waitForUi(appPage);

		Assert.assertTrue("Main application interface did not appear after login.", isAnyVisible(appPage,
				byText(appPage, "(?i)(dashboard|panel|inicio|home)"),
				byRole(appPage, AriaRole.NAVIGATION, ".*"),
				byCss(appPage, "aside")));
		Assert.assertTrue("Left sidebar navigation is not visible after login.",
				isAnyVisible(appPage, byCss(appPage, "aside"), byRole(appPage, AriaRole.NAVIGATION, ".*")));
		screenshot(appPage, "01-dashboard-loaded.png", true);
	}

	private void selectGoogleAccountIfVisible(final Page authPage, final String googleAccount) {
		final Locator accountOption = firstVisible(authPage,
				byText(authPage, Pattern.quote(googleAccount)),
				byRole(authPage, AriaRole.BUTTON, Pattern.quote(googleAccount)),
				byRole(authPage, AriaRole.LINK, Pattern.quote(googleAccount)));

		if (accountOption != null) {
			accountOption.click();
			waitForUi(authPage);
		}
	}

	private void openMiNegocioMenu(final Page appPage) {
		clickIfVisible(appPage, "Negocio");
		clickAndWaitByText(appPage, "Mi Negocio");

		Assert.assertTrue("Mi Negocio submenu did not expand.", isAnyVisible(appPage,
				byText(appPage, "(?i)Agregar\\s+Negocio"),
				byText(appPage, "(?i)Administrar\\s+Negocios")));
		Assert.assertTrue("\"Agregar Negocio\" is not visible.", isVisible(byText(appPage, "(?i)Agregar\\s+Negocio")));
		Assert.assertTrue("\"Administrar Negocios\" is not visible.",
				isVisible(byText(appPage, "(?i)Administrar\\s+Negocios")));
		screenshot(appPage, "02-mi-negocio-menu-expanded.png", false);
	}

	private void validateAgregarNegocioModal(final Page appPage) {
		clickAndWaitByText(appPage, "Agregar Negocio");
		Assert.assertTrue("\"Crear Nuevo Negocio\" modal title is not visible.",
				isVisible(byText(appPage, "(?i)Crear\\s+Nuevo\\s+Negocio")));
		Assert.assertTrue("\"Nombre del Negocio\" field is not visible.",
				isAnyVisible(appPage, byLabel(appPage, "(?i)Nombre\\s+del\\s+Negocio"),
						byPlaceholder(appPage, "(?i)Nombre\\s+del\\s+Negocio")));
		Assert.assertTrue("\"Tienes 2 de 3 negocios\" is not visible.",
				isVisible(byText(appPage, "(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
		Assert.assertTrue("\"Cancelar\" button is not visible.", isVisible(byRole(appPage, AriaRole.BUTTON, "(?i)Cancelar")));
		Assert.assertTrue("\"Crear Negocio\" button is not visible.",
				isVisible(byRole(appPage, AriaRole.BUTTON, "(?i)Crear\\s+Negocio")));
		screenshot(appPage, "03-crear-nuevo-negocio-modal.png", false);

		final Locator nombreInput = firstVisible(appPage, byLabel(appPage, "(?i)Nombre\\s+del\\s+Negocio"),
				byPlaceholder(appPage, "(?i)Nombre\\s+del\\s+Negocio"));
		if (nombreInput != null) {
			nombreInput.click();
			nombreInput.fill("Negocio Prueba Automatizacion");
			waitForUi(appPage);
		}

		clickAndWaitByText(appPage, "Cancelar");
		Assert.assertFalse("Modal did not close after clicking \"Cancelar\".",
				isVisible(byText(appPage, "(?i)Crear\\s+Nuevo\\s+Negocio")));
	}

	private void openAdministrarNegocios(final Page appPage) {
		if (!isVisible(byText(appPage, "(?i)Administrar\\s+Negocios"))) {
			clickIfVisible(appPage, "Mi Negocio");
			waitForUi(appPage);
		}
		clickAndWaitByText(appPage, "Administrar Negocios");

		Assert.assertTrue("\"Informaci\u00f3n General\" section is not visible.",
				isVisible(byText(appPage, "(?i)Informaci[o\u00f3]n\\s+General")));
		Assert.assertTrue("\"Detalles de la Cuenta\" section is not visible.",
				isVisible(byText(appPage, "(?i)Detalles\\s+de\\s+la\\s+Cuenta")));
		Assert.assertTrue("\"Tus Negocios\" section is not visible.", isVisible(byText(appPage, "(?i)Tus\\s+Negocios")));
		Assert.assertTrue("\"Secci\u00f3n Legal\" section is not visible.",
				isVisible(byText(appPage, "(?i)Secci[o\u00f3]n\\s+Legal")));
		screenshot(appPage, "04-administrar-negocios-page.png", true);
	}

	private void validateInformacionGeneral(final Page appPage) {
		final String pageText = safeInnerText(appPage.locator("body"));

		final Matcher emailMatcher = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
				.matcher(pageText);
		Assert.assertTrue("User email is not visible in Informaci\u00f3n General section.", emailMatcher.find());

		final String firstEmail = emailMatcher.group();
		final String normalizedText = pageText.replace(firstEmail, "").replaceAll("(?i)BUSINESS\\s+PLAN", "")
				.replaceAll("(?i)Cambiar\\s+Plan", "").replaceAll("\\s+", " ").trim();
		Assert.assertTrue("User name is not clearly visible in Informaci\u00f3n General section.",
				Pattern.compile("[A-Za-z\\p{L}]{3,}(\\s+[A-Za-z\\p{L}]{2,})?").matcher(normalizedText).find());
		Assert.assertTrue("\"BUSINESS PLAN\" is not visible.", isVisible(byText(appPage, "(?i)BUSINESS\\s+PLAN")));
		Assert.assertTrue("\"Cambiar Plan\" button is not visible.",
				isAnyVisible(appPage, byRole(appPage, AriaRole.BUTTON, "(?i)Cambiar\\s+Plan"),
						byText(appPage, "(?i)Cambiar\\s+Plan")));
	}

	private void validateDetallesCuenta(final Page appPage) {
		Assert.assertTrue("\"Cuenta creada\" is not visible.", isVisible(byText(appPage, "(?i)Cuenta\\s+creada")));
		Assert.assertTrue("\"Estado activo\" is not visible.", isVisible(byText(appPage, "(?i)Estado\\s+activo")));
		Assert.assertTrue("\"Idioma seleccionado\" is not visible.",
				isVisible(byText(appPage, "(?i)Idioma\\s+seleccionado")));
	}

	private void validateTusNegocios(final Page appPage) {
		Assert.assertTrue("Business list is not visible.",
				isAnyVisible(appPage, byText(appPage, "(?i)Tus\\s+Negocios"), byCss(appPage, "table"), byCss(appPage, "ul")));
		Assert.assertTrue("\"Agregar Negocio\" button is missing.",
				isAnyVisible(appPage, byRole(appPage, AriaRole.BUTTON, "(?i)Agregar\\s+Negocio"),
						byText(appPage, "(?i)Agregar\\s+Negocio")));
		Assert.assertTrue("\"Tienes 2 de 3 negocios\" is not visible.",
				isVisible(byText(appPage, "(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
	}

	private void validateLegalLink(final Page appPage, final BrowserContext context, final String linkText,
			final String screenshotPrefix) {
		Page legalPage = appPage;
		boolean openedNewTab = false;

		try {
			legalPage = context.waitForPage(() -> clickByText(appPage, linkText),
					new BrowserContext.WaitForPageOptions().setTimeout(8_000));
			openedNewTab = true;
		} catch (final PlaywrightException noNewTab) {
			clickByText(appPage, linkText);
		}

		waitForUi(legalPage);
		Assert.assertTrue("Expected legal heading is not visible for " + linkText + ".",
				isAnyVisible(legalPage, byRole(legalPage, AriaRole.HEADING, "(?i)" + Pattern.quote(linkText)),
						byText(legalPage, "(?i)" + Pattern.quote(linkText))));

		final String legalText = safeInnerText(legalPage.locator("body")).replaceAll("\\s+", " ").trim();
		Assert.assertTrue("Legal content is not visible for " + linkText + ".", legalText.length() > 160);
		screenshot(legalPage, screenshotPrefix + ".png", true);

		final String finalUrl = legalPage.url();
		if (TERMINOS_Y_CONDICIONES.equals(linkText)) {
			terminosFinalUrl = finalUrl;
		} else if (POLITICA_DE_PRIVACIDAD.equals(linkText)) {
			privacidadFinalUrl = finalUrl;
		}

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			legalPage.goBack();
			waitForUi(appPage);
		}
	}

	private void runStep(final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			stepResults.put(stepName, StepResult.pass());
		} catch (final Throwable throwable) {
			stepResults.put(stepName, StepResult.fail(throwable.getMessage()));
			System.err.println("[FAIL] " + stepName + " -> " + throwable.getMessage());
		}
	}

	private void clickAndWaitByText(final Page page, final String text) {
		clickByText(page, text);
		waitForUi(page);
	}

	private void clickIfVisible(final Page page, final String text) {
		final Locator locator = firstVisible(page, byText(page, "(?i)" + Pattern.quote(text)),
				byRole(page, AriaRole.BUTTON, "(?i)" + Pattern.quote(text)),
				byRole(page, AriaRole.LINK, "(?i)" + Pattern.quote(text)));
		if (locator != null) {
			locator.click();
			waitForUi(page);
		}
	}

	private void clickByText(final Page page, final String text) {
		final Locator locator = firstVisible(page, byText(page, "(?i)" + Pattern.quote(text)),
				byRole(page, AriaRole.BUTTON, "(?i)" + Pattern.quote(text)),
				byRole(page, AriaRole.LINK, "(?i)" + Pattern.quote(text)),
				byRole(page, AriaRole.MENUITEM, "(?i)" + Pattern.quote(text)));
		Assert.assertNotNull("Unable to find clickable element with text: " + text, locator);
		locator.click();
	}

	private boolean isAnyVisible(final Page page, final Locator... locators) {
		for (final Locator locator : locators) {
			if (isVisible(locator)) {
				return true;
			}
		}
		return false;
	}

	private Locator firstVisible(final Page page, final Locator... locators) {
		for (final Locator locator : locators) {
			if (isVisible(locator)) {
				return locator.first();
			}
		}
		return null;
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.count() > 0 && locator.first().isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private Locator byRole(final Page page, final AriaRole role, final String regex) {
		return page.getByRole(role, new Page.GetByRoleOptions().setName(Pattern.compile(regex)));
	}

	private Locator byText(final Page page, final String regex) {
		return page.getByText(Pattern.compile(regex));
	}

	private Locator byLabel(final Page page, final String regex) {
		return page.getByLabel(Pattern.compile(regex));
	}

	private Locator byPlaceholder(final Page page, final String regex) {
		return page.getByPlaceholder(Pattern.compile(regex));
	}

	private Locator byCss(final Page page, final String cssSelector) {
		return page.locator(cssSelector);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15_000));
		} catch (final PlaywrightException ignored) {
			// Some transitions do not reach network idle; keep a short fixed wait.
		}
		page.waitForTimeout(1_000);
	}

	private void configureTimeouts(final Page page) {
		page.setDefaultTimeout(20_000);
		page.setDefaultNavigationTimeout(30_000);
	}

	private Path createEvidenceDir() throws IOException {
		final String root = cleanOrDefault(System.getenv("SALEADS_SCREENSHOT_DIR"), "target/saleads-evidence");
		final Path rootPath = Paths.get(root);
		Files.createDirectories(rootPath);
		final String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path runDir = rootPath.resolve("saleads-mi-negocio-" + ts);
		Files.createDirectories(runDir);
		return runDir;
	}

	private void screenshot(final Page page, final String fileName, final boolean fullPage) {
		try {
			final Path screenshotPath = evidenceDir.resolve(fileName);
			page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		} catch (final PlaywrightException screenshotError) {
			System.err.println("Could not capture screenshot " + fileName + ": " + screenshotError.getMessage());
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio workflow final report").append(System.lineSeparator());
		sb.append("=====================================").append(System.lineSeparator()).append(System.lineSeparator());

		appendStatus(sb, LOGIN);
		appendStatus(sb, MI_NEGOCIO_MENU);
		appendStatus(sb, AGREGAR_NEGOCIO_MODAL);
		appendStatus(sb, ADMINISTRAR_NEGOCIOS_VIEW);
		appendStatus(sb, INFORMACION_GENERAL);
		appendStatus(sb, DETALLES_CUENTA);
		appendStatus(sb, TUS_NEGOCIOS);
		appendStatus(sb, TERMINOS_Y_CONDICIONES);
		appendStatus(sb, POLITICA_DE_PRIVACIDAD);

		sb.append(System.lineSeparator());
		sb.append("T\u00e9rminos y Condiciones final URL: ").append(terminosFinalUrl).append(System.lineSeparator());
		sb.append("Pol\u00edtica de Privacidad final URL: ").append(privacidadFinalUrl).append(System.lineSeparator());
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, sb.toString());
		System.out.println(sb);
	}

	private void appendStatus(final StringBuilder sb, final String stepName) {
		final StepResult result = stepResults.getOrDefault(stepName, StepResult.fail("Not executed"));
		sb.append(stepName).append(": ").append(result.passed ? "PASS" : "FAIL");
		if (!result.passed && hasText(result.details)) {
			sb.append(" (").append(result.details).append(")");
		}
		sb.append(System.lineSeparator());
	}

	private boolean allStepsPassed() {
		return stepResults.size() == 9 && stepResults.values().stream().allMatch(result -> result.passed);
	}

	private String buildFailureSummary() {
		final StringBuilder sb = new StringBuilder("One or more workflow validations failed.");
		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			if (!entry.getValue().passed) {
				sb.append(System.lineSeparator()).append("- ").append(entry.getKey()).append(": ")
						.append(entry.getValue().details);
			}
		}
		return sb.toString();
	}

	private String safeInnerText(final Locator locator) {
		try {
			return Objects.toString(locator.first().innerText(), "");
		} catch (final PlaywrightException ignored) {
			return "";
		}
	}

	private String clean(final String value) {
		return value == null ? null : value.trim();
	}

	private String cleanOrDefault(final String value, final String defaultValue) {
		return hasText(value) ? value.trim() : defaultValue;
	}

	private boolean hasText(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	private boolean parseBoolean(final String value, final boolean defaultValue) {
		if (!hasText(value)) {
			return defaultValue;
		}
		return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()) || "yes".equalsIgnoreCase(value.trim());
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class BrowserSession {
		private final Browser browser;
		private final BrowserContext context;

		private BrowserSession(final Browser browser, final BrowserContext context) {
			this.browser = browser;
			this.context = context;
		}
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, Objects.toString(details, "unknown error"));
		}
	}
}
