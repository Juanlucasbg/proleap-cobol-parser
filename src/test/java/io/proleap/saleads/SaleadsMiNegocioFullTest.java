package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

	private static final String LOGIN_STEP = "Login";
	private static final String MENU_STEP = "Mi Negocio menu";
	private static final String MODAL_STEP = "Agregar Negocio modal";
	private static final String ADMIN_STEP = "Administrar Negocios view";
	private static final String INFO_STEP = "Información General";
	private static final String DETAILS_STEP = "Detalles de la Cuenta";
	private static final String BUSINESSES_STEP = "Tus Negocios";
	private static final String TERMS_STEP = "Términos y Condiciones";
	private static final String PRIVACY_STEP = "Política de Privacidad";
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern LOGIN_PATTERN = Pattern
			.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)mi\\s*negocio");
	private static final Pattern NEGOCIO_SECTION_PATTERN = Pattern.compile("(?i)negocio");
	private static final Pattern ADD_BUSINESS_PATTERN = Pattern.compile("(?i)agregar\\s+negocio");
	private static final Pattern MANAGE_BUSINESS_PATTERN = Pattern.compile("(?i)administrar\\s+negocios");
	private static final Pattern TERMS_PATTERN = Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones");
	private static final Pattern PRIVACY_PATTERN = Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final Map<String, StepResult> results = createEmptyResults();
		final Path evidenceDir = createEvidenceDirectory();
		BrowserSession session = null;

		try (Playwright playwright = Playwright.create()) {
			session = startSession(playwright);
			final Page appPage = session.page;
			final BrowserContext context = session.context;

			results.put(LOGIN_STEP, runLogin(appPage, context, evidenceDir));
			results.put(MENU_STEP, runMiNegocioMenuValidation(appPage, evidenceDir));
			results.put(MODAL_STEP, runAgregarNegocioModalValidation(appPage, evidenceDir));
			results.put(ADMIN_STEP, runAdministrarNegociosValidation(appPage, evidenceDir));
			results.put(INFO_STEP, runInformacionGeneralValidation(appPage));
			results.put(DETAILS_STEP, runDetallesCuentaValidation(appPage));
			results.put(BUSINESSES_STEP, runTusNegociosValidation(appPage));
			results.put(TERMS_STEP, runLegalPageValidation(appPage, context, evidenceDir, "Términos y Condiciones",
					TERMS_PATTERN, "05-terminos-y-condiciones.png"));
			results.put(PRIVACY_STEP, runLegalPageValidation(appPage, context, evidenceDir, "Política de Privacidad",
					PRIVACY_PATTERN, "06-politica-de-privacidad.png"));
		} finally {
			if (session != null) {
				session.close();
			}

			writeReport(evidenceDir, results);
		}

		assertTrue(buildFailureSummary(results), allPassed(results));
	}

	private BrowserSession startSession(final Playwright playwright) {
		final String cdpEndpoint = readSetting("saleads.cdpEndpoint", "SALEADS_CDP_ENDPOINT");
		final String loginUrl = readSetting("saleads.loginUrl", "SALEADS_LOGIN_URL");

		if (cdpEndpoint != null && !cdpEndpoint.isEmpty()) {
			final Browser browser = playwright.chromium().connectOverCDP(cdpEndpoint);
			final BrowserContext context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
			final Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
			page.bringToFront();

			if (("about:blank".equals(page.url()) || page.url() == null || page.url().isEmpty()) && loginUrl != null
					&& !loginUrl.isEmpty()) {
				page.navigate(loginUrl);
			}

			waitForUi(page);
			return new BrowserSession(browser, context, page, false);
		}

		if (loginUrl == null || loginUrl.isEmpty()) {
			throw new IllegalStateException(
					"Missing login page source. Provide -Dsaleads.loginUrl or SALEADS_LOGIN_URL, "
							+ "or reuse an existing browser with -Dsaleads.cdpEndpoint/SALEADS_CDP_ENDPOINT.");
		}

		final boolean headless = !"false".equalsIgnoreCase(readSetting("saleads.headless", "SALEADS_HEADLESS"));
		final Browser browser = playwright.chromium()
				.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(200));
		final BrowserContext context = browser.newContext();
		final Page page = context.newPage();
		page.navigate(loginUrl);
		waitForUi(page);

		return new BrowserSession(browser, context, page, true);
	}

	private StepResult runLogin(final Page appPage, final BrowserContext context, final Path evidenceDir) {
		try {
			final Locator loginButton = firstVisible("Google login button",
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(LOGIN_PATTERN)),
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(LOGIN_PATTERN)),
					appPage.getByText(LOGIN_PATTERN));

			Page popup = null;
			try {
				popup = context.waitForPage(() -> clickAndWaitForUi(appPage, loginButton),
						new BrowserContext.WaitForPageOptions().setTimeout(timeoutMs()));
			} catch (final PlaywrightException noPopupExpected) {
				clickAndWaitForUi(appPage, loginButton);
			}

			if (popup != null) {
				waitForUi(popup);
				selectGoogleAccountIfVisible(popup);
				try {
					popup.waitForClose(new Page.WaitForCloseOptions().setTimeout(timeoutMs() * 2L));
				} catch (final PlaywrightException ignored) {
					// Continue if popup stays open but app already authenticated.
				}
			} else {
				selectGoogleAccountIfVisible(appPage);
			}

			waitForUi(appPage);
			assertVisible("main application interface",
					firstVisible("main app shell", appPage.locator("main"), appPage.locator("#root"),
							appPage.locator("body")));
			assertVisible("left sidebar navigation",
					firstVisible("left sidebar", appPage.locator("aside"), appPage.locator("nav"),
							appPage.getByText(NEGOCIO_SECTION_PATTERN)));

			final Path screenshot = takeScreenshot(appPage, evidenceDir, "01-dashboard.png", true);
			return StepResult.pass("Dashboard loaded and sidebar is visible.", screenshot.toString(), null);
		} catch (final RuntimeException ex) {
			return StepResult.fail(rootMessage(ex), null, null);
		}
	}

	private StepResult runMiNegocioMenuValidation(final Page appPage, final Path evidenceDir) {
		try {
			final Locator negocioSection = firstVisible("Negocio section", appPage.getByText(NEGOCIO_SECTION_PATTERN),
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEGOCIO_SECTION_PATTERN)),
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(NEGOCIO_SECTION_PATTERN)));
			assertVisible("Negocio section", negocioSection);

			final Locator miNegocioOption = firstVisible("Mi Negocio option",
					appPage.getByText(MI_NEGOCIO_PATTERN),
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)));
			clickAndWaitForUi(appPage, miNegocioOption);

			assertVisible("Agregar Negocio submenu option", firstVisible("Agregar Negocio option",
					appPage.getByText(ADD_BUSINESS_PATTERN),
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADD_BUSINESS_PATTERN)),
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADD_BUSINESS_PATTERN))));
			assertVisible("Administrar Negocios submenu option", firstVisible("Administrar Negocios option",
					appPage.getByText(MANAGE_BUSINESS_PATTERN),
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MANAGE_BUSINESS_PATTERN)),
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MANAGE_BUSINESS_PATTERN))));

			final Path screenshot = takeScreenshot(appPage, evidenceDir, "02-mi-negocio-menu-expanded.png", false);
			return StepResult.pass("Mi Negocio menu expanded correctly.", screenshot.toString(), null);
		} catch (final RuntimeException ex) {
			return StepResult.fail(rootMessage(ex), null, null);
		}
	}

	private StepResult runAgregarNegocioModalValidation(final Page appPage, final Path evidenceDir) {
		try {
			final Locator addBusiness = firstVisible("Agregar Negocio button",
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADD_BUSINESS_PATTERN)),
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADD_BUSINESS_PATTERN)),
					appPage.getByText(ADD_BUSINESS_PATTERN));
			clickAndWaitForUi(appPage, addBusiness);

			assertVisibleText(appPage, "Crear Nuevo Negocio");
			assertVisible("Nombre del Negocio input",
					firstVisible("Nombre del Negocio input", appPage.getByLabel("Nombre del Negocio"),
							appPage.getByPlaceholder("Nombre del Negocio"),
							appPage.locator("input[name='nombreNegocio']")));
			assertVisibleText(appPage, "Tienes 2 de 3 negocios");
			assertVisible("Cancelar button",
					firstVisible("Cancelar button",
							appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
							appPage.getByText("Cancelar", new Page.GetByTextOptions().setExact(true))));
			assertVisible("Crear Negocio button",
					firstVisible("Crear Negocio button", appPage.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s+negocio"))),
							appPage.getByText(Pattern.compile("(?i)crear\\s+negocio"))));

			final Path screenshot = takeScreenshot(appPage, evidenceDir, "03-agregar-negocio-modal.png", false);

			final Locator businessNameInput = firstVisible("Nombre del Negocio input for typing",
					appPage.getByLabel("Nombre del Negocio"), appPage.getByPlaceholder("Nombre del Negocio"));
			businessNameInput.fill("Negocio Prueba Automatización");
			clickAndWaitForUi(appPage, firstVisible("Cancelar close modal button",
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
					appPage.getByText("Cancelar", new Page.GetByTextOptions().setExact(true))));
			waitForUi(appPage);

			return StepResult.pass("Agregar Negocio modal validated and closed with Cancelar.", screenshot.toString(), null);
		} catch (final RuntimeException ex) {
			return StepResult.fail(rootMessage(ex), null, null);
		}
	}

	private StepResult runAdministrarNegociosValidation(final Page appPage, final Path evidenceDir) {
		try {
			expandMiNegocioIfNeeded(appPage);

			final Locator administrarNegocios = firstVisible("Administrar Negocios navigation",
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MANAGE_BUSINESS_PATTERN)),
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MANAGE_BUSINESS_PATTERN)),
					appPage.getByText(MANAGE_BUSINESS_PATTERN));
			clickAndWaitForUi(appPage, administrarNegocios);

			assertVisibleText(appPage, "Información General");
			assertVisibleText(appPage, "Detalles de la Cuenta");
			assertVisibleText(appPage, "Tus Negocios");
			assertVisibleText(appPage, "Sección Legal");

			final Path screenshot = takeScreenshot(appPage, evidenceDir, "04-administrar-negocios-page.png", true);
			return StepResult.pass("Administrar Negocios page loaded with all required sections.", screenshot.toString(),
					null);
		} catch (final RuntimeException ex) {
			return StepResult.fail(rootMessage(ex), null, null);
		}
	}

	private StepResult runInformacionGeneralValidation(final Page appPage) {
		try {
			assertVisibleText(appPage, "Información General");
			assertVisible("User email", firstVisible("User email", appPage.getByText(EMAIL_PATTERN)));
			assertVisible("User name",
					firstVisible("User name", appPage.getByText(Pattern.compile("(?i)nombre")),
							appPage.getByText(Pattern.compile("(?i)usuario")),
							appPage.getByText(Pattern.compile("(?i)perfil"))));
			assertVisibleText(appPage, "BUSINESS PLAN");
			assertVisible("Cambiar Plan button",
					firstVisible("Cambiar Plan button",
							appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")),
							appPage.getByText("Cambiar Plan", new Page.GetByTextOptions().setExact(true))));

			return StepResult.pass("Información General section validated.", null, null);
		} catch (final RuntimeException ex) {
			return StepResult.fail(rootMessage(ex), null, null);
		}
	}

	private StepResult runDetallesCuentaValidation(final Page appPage) {
		try {
			assertVisibleText(appPage, "Cuenta creada");
			assertVisibleText(appPage, "Estado activo");
			assertVisibleText(appPage, "Idioma seleccionado");
			return StepResult.pass("Detalles de la Cuenta section validated.", null, null);
		} catch (final RuntimeException ex) {
			return StepResult.fail(rootMessage(ex), null, null);
		}
	}

	private StepResult runTusNegociosValidation(final Page appPage) {
		try {
			assertVisibleText(appPage, "Tus Negocios");
			assertVisible("Business list area",
					firstVisible("Business list", appPage.locator("section:has-text('Tus Negocios')"),
							appPage.locator("div:has-text('Tus Negocios')")));
			assertVisible("Agregar Negocio button",
					firstVisible("Agregar Negocio button in businesses section",
							appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADD_BUSINESS_PATTERN)),
							appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADD_BUSINESS_PATTERN)),
							appPage.getByText(ADD_BUSINESS_PATTERN)));
			assertVisibleText(appPage, "Tienes 2 de 3 negocios");
			return StepResult.pass("Tus Negocios section validated.", null, null);
		} catch (final RuntimeException ex) {
			return StepResult.fail(rootMessage(ex), null, null);
		}
	}

	private StepResult runLegalPageValidation(final Page appPage, final BrowserContext context, final Path evidenceDir,
			final String linkText, final Pattern headingPattern, final String screenshotName) {
		try {
			final String appUrlBeforeClick = appPage.url();
			final Locator legalLink = firstVisible(linkText + " link",
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + linkText))),
					appPage.getByText(Pattern.compile("(?i)" + linkText)));

			Page legalPage;
			try {
				legalPage = context.waitForPage(() -> clickAndWaitForUi(appPage, legalLink),
						new BrowserContext.WaitForPageOptions().setTimeout(timeoutMs()));
			} catch (final PlaywrightException noNewTab) {
				clickAndWaitForUi(appPage, legalLink);
				legalPage = appPage;
			}

			waitForUi(legalPage);
			assertVisible(linkText + " heading", firstVisible(linkText + " heading", legalPage.getByText(headingPattern),
					legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern))));

			final String content = legalPage.textContent("body");
			if (content == null || content.trim().length() < 120) {
				throw new IllegalStateException("Legal content text is too short or missing.");
			}

			final Path screenshot = takeScreenshot(legalPage, evidenceDir, screenshotName, true);
			final String finalUrl = legalPage.url();

			if (legalPage == appPage) {
				if (appPage.goBack() == null && appUrlBeforeClick != null && !appUrlBeforeClick.isEmpty()) {
					appPage.navigate(appUrlBeforeClick);
				}
				waitForUi(appPage);
			} else {
				legalPage.close();
				appPage.bringToFront();
				waitForUi(appPage);
			}

			return StepResult.pass(linkText + " page validated.", screenshot.toString(), finalUrl);
		} catch (final RuntimeException ex) {
			return StepResult.fail(rootMessage(ex), null, null);
		}
	}

	private void expandMiNegocioIfNeeded(final Page appPage) {
		if (!isVisible(appPage.getByText(ADD_BUSINESS_PATTERN), 1500)) {
			final Locator miNegocio = firstVisible("Mi Negocio option to expand",
					appPage.getByText(MI_NEGOCIO_PATTERN),
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)));
			clickAndWaitForUi(appPage, miNegocio);
		}
	}

	private void selectGoogleAccountIfVisible(final Page googlePage) {
		final String configuredEmail = readSetting("saleads.googleEmail", "SALEADS_GOOGLE_EMAIL");
		final String targetEmail = configuredEmail == null ? DEFAULT_GOOGLE_ACCOUNT : configuredEmail;
		final Locator account = googlePage.getByText(targetEmail, new Page.GetByTextOptions().setExact(true));
		if (isVisible(account, 5000)) {
			clickAndWaitForUi(googlePage, account);
		}
	}

	private Locator firstVisible(final String elementDescription, final Locator... options) {
		for (final Locator option : options) {
			try {
				final Locator first = option.first();
				first.waitFor(new Locator.WaitForOptions().setTimeout(3500).setState(WaitForSelectorState.VISIBLE));
				return first;
			} catch (final PlaywrightException ignored) {
				// Try next option.
			}
		}

		throw new IllegalStateException("Unable to find visible element: " + elementDescription);
	}

	private void clickAndWaitForUi(final Page page, final Locator locator) {
		locator.scrollIntoViewIfNeeded();
		locator.click(new Locator.ClickOptions().setTimeout(timeoutMs()));
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs()));
		} catch (final PlaywrightException ignored) {
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
		}
		page.waitForTimeout(350);
	}

	private void assertVisibleText(final Page page, final String text) {
		assertVisible(text, firstVisible(text, page.getByText(text, new Page.GetByTextOptions().setExact(false))));
	}

	private void assertVisible(final String description, final Locator locator) {
		if (!locator.isVisible()) {
			throw new IllegalStateException(description + " is not visible.");
		}
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs).setState(WaitForSelectorState.VISIBLE));
			return true;
		} catch (final PlaywrightException ex) {
			return false;
		}
	}

	private Path takeScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		final Path screenshotPath = evidenceDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		return screenshotPath;
	}

	private Map<String, StepResult> createEmptyResults() {
		final Map<String, StepResult> results = new LinkedHashMap<>();
		results.put(LOGIN_STEP, StepResult.pending());
		results.put(MENU_STEP, StepResult.pending());
		results.put(MODAL_STEP, StepResult.pending());
		results.put(ADMIN_STEP, StepResult.pending());
		results.put(INFO_STEP, StepResult.pending());
		results.put(DETAILS_STEP, StepResult.pending());
		results.put(BUSINESSES_STEP, StepResult.pending());
		results.put(TERMS_STEP, StepResult.pending());
		results.put(PRIVACY_STEP, StepResult.pending());
		return results;
	}

	private Path createEvidenceDirectory() {
		final String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path evidenceDir = Paths.get(readSetting("saleads.evidenceDir", "SALEADS_EVIDENCE_DIR") == null
				? "target/saleads-evidence/" + stamp
				: readSetting("saleads.evidenceDir", "SALEADS_EVIDENCE_DIR"));
		try {
			Files.createDirectories(evidenceDir);
		} catch (final IOException ex) {
			throw new IllegalStateException("Failed to create evidence directory " + evidenceDir, ex);
		}
		return evidenceDir;
	}

	private void writeReport(final Path evidenceDir, final Map<String, StepResult> results) throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("evidence_directory: " + evidenceDir.toAbsolutePath());
		lines.add("");

		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			final String stepName = entry.getKey();
			final StepResult result = entry.getValue();
			lines.add(stepName + ": " + (result.passed ? "PASS" : "FAIL"));
			if (result.detail != null && !result.detail.isEmpty()) {
				lines.add("  detail: " + result.detail);
			}
			if (result.screenshot != null && !result.screenshot.isEmpty()) {
				lines.add("  screenshot: " + result.screenshot);
			}
			if (result.finalUrl != null && !result.finalUrl.isEmpty()) {
				lines.add("  final_url: " + result.finalUrl);
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), String.join(System.lineSeparator(), lines),
				StandardCharsets.UTF_8);
	}

	private boolean allPassed(final Map<String, StepResult> results) {
		for (final StepResult result : results.values()) {
			if (!result.passed) {
				return false;
			}
		}
		return true;
	}

	private String buildFailureSummary(final Map<String, StepResult> results) {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			if (!entry.getValue().passed) {
				failedSteps.add(entry.getKey() + " -> " + entry.getValue().detail);
			}
		}
		return failedSteps.isEmpty() ? "All workflow validations passed."
				: "One or more validations failed: " + String.join("; ", failedSteps);
	}

	private String readSetting(final String systemProperty, final String envName) {
		final String fromProperty = System.getProperty(systemProperty);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}

		final String fromEnv = System.getenv(envName);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv.trim();
		}

		return null;
	}

	private int timeoutMs() {
		final String timeout = readSetting("saleads.timeoutMs", "SALEADS_TIMEOUT_MS");
		if (timeout == null) {
			return 15000;
		}

		try {
			return Integer.parseInt(timeout);
		} catch (final NumberFormatException ex) {
			return 15000;
		}
	}

	private String rootMessage(final Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.toString() : current.getMessage();
	}

	private static class BrowserSession {
		private final Browser browser;
		private final BrowserContext context;
		private final Page page;
		private final boolean ownsBrowser;

		private BrowserSession(final Browser browser, final BrowserContext context, final Page page,
				final boolean ownsBrowser) {
			this.browser = browser;
			this.context = context;
			this.page = page;
			this.ownsBrowser = ownsBrowser;
		}

		private void close() {
			if (ownsBrowser && browser != null) {
				browser.close();
			}
		}
	}

	private static class StepResult {
		private final boolean passed;
		private final String detail;
		private final String screenshot;
		private final String finalUrl;

		private StepResult(final boolean passed, final String detail, final String screenshot, final String finalUrl) {
			this.passed = passed;
			this.detail = detail;
			this.screenshot = screenshot;
			this.finalUrl = finalUrl;
		}

		private static StepResult pending() {
			return new StepResult(false, "Step was not executed.", null, null);
		}

		private static StepResult pass(final String detail, final String screenshot, final String finalUrl) {
			return new StepResult(true, detail, screenshot, finalUrl);
		}

		private static StepResult fail(final String detail, final String screenshot, final String finalUrl) {
			return new StepResult(false, detail, screenshot, finalUrl);
		}
	}
}
