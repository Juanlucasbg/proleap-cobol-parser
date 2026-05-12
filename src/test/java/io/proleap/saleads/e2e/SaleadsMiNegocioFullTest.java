package io.proleap.saleads.e2e;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

/**
 * End-to-end workflow validation for the SaleADS "Mi Negocio" module.
 *
 * <p>
 * Configuration:
 * </p>
 * <ul>
 * <li>SALEADS_URL (or -Dsaleads.url): URL for any SaleADS environment login
 * page.</li>
 * <li>SALEADS_CDP_URL (or -Dsaleads.cdp.url): optional CDP endpoint to reuse an
 * existing browser session.</li>
 * <li>SALEADS_HEADLESS (or -Dsaleads.headless): defaults to true when launching
 * a new browser.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private Path evidenceDir;
	private boolean closeContextOnTeardown = true;
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String runStamp = LocalDateTime.now().format(STAMP_FORMAT);
		evidenceDir = Paths.get("target", "saleads-evidence", runStamp);
		Files.createDirectories(evidenceDir);

		playwright = Playwright.create();
		final String cdpUrl = getConfig("saleads.cdp.url", "SALEADS_CDP_URL");

		if (cdpUrl != null && !cdpUrl.isBlank()) {
			browser = playwright.chromium().connectOverCDP(cdpUrl);
			if (browser.contexts().isEmpty()) {
				context = browser.newContext();
				closeContextOnTeardown = true;
			} else {
				context = browser.contexts().get(0);
				closeContextOnTeardown = false;
			}
			page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
		} else {
			final boolean headless = Boolean.parseBoolean(getConfigOrDefault("saleads.headless", "SALEADS_HEADLESS", "true"));
			browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			context = browser.newContext();
			page = context.newPage();
		}

		final String startUrl = getConfig("saleads.url", "SALEADS_URL");
		if (startUrl != null && !startUrl.isBlank()) {
			page.navigate(startUrl);
		}
		waitForUiLoad(page);
	}

	@After
	public void tearDown() {
		if (context != null && closeContextOnTeardown) {
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
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final Map<String, StepResult> report = new LinkedHashMap<>();

		report.put("Login", runStep(this::validateLoginWithGoogle));
		report.put("Mi Negocio menu", runStep(this::validateMiNegocioMenu));
		report.put("Agregar Negocio modal", runStep(this::validateAgregarNegocioModal));
		report.put("Administrar Negocios view", runStep(this::validateAdministrarNegociosView));
		report.put("Información General", runStep(this::validateInformacionGeneral));
		report.put("Detalles de la Cuenta", runStep(this::validateDetallesCuenta));
		report.put("Tus Negocios", runStep(this::validateTusNegocios));
		report.put("Términos y Condiciones", runStep(() -> validateLegalDocument("Términos y Condiciones",
				Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"), "terminos-y-condiciones")));
		report.put("Política de Privacidad", runStep(() -> validateLegalDocument("Política de Privacidad",
				Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"), "politica-de-privacidad")));

		final Path reportFile = writeReport(report);
		final StringBuilder failures = new StringBuilder();
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				failures.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().detail).append(System.lineSeparator());
			}
		}

		if (failures.length() > 0) {
			fail("SaleADS Mi Negocio workflow failed." + System.lineSeparator() + failures + "Report: " + reportFile);
		}
	}

	private void validateLoginWithGoogle() {
		if ("about:blank".equals(page.url())) {
			throw new AssertionError("Page is blank. Provide SALEADS_URL or reuse an existing logged-out session via SALEADS_CDP_URL.");
		}

		final Locator signInButton = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)google"))),
				page.getByText("Sign in with Google", new Page.GetByTextOptions().setExact(false)),
				page.getByText("Iniciar sesión con Google", new Page.GetByTextOptions().setExact(false)),
				page.getByText("Continuar con Google", new Page.GetByTextOptions().setExact(false)));

		final Page googlePage = clickExpectingOptionalPopup(signInButton);
		if (googlePage != null) {
			selectGoogleAccountIfVisible(googlePage);
			waitForUiLoad(googlePage);
			try {
				if (!googlePage.isClosed()) {
					googlePage.close();
				}
			} catch (RuntimeException ignored) {
				// Popup may already close after selecting account.
			}
			page.bringToFront();
		} else {
			selectGoogleAccountIfVisible(page);
		}

		waitForUiLoad(page);
		assertVisible("Main application interface should be visible",
				page.getByText(Pattern.compile("(?i)(negocio|dashboard|administrar negocios)")));
		assertVisible("Left sidebar navigation should be visible", page.locator("aside"));
		takeScreenshot("01-dashboard-loaded", false);
	}

	private void validateMiNegocioMenu() {
		final Locator negocioSection = firstVisible(page.getByText("Negocio", new Page.GetByTextOptions().setExact(false)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)negocio"))));
		clickAndWait(negocioSection);

		final Locator miNegocioOption = firstVisible(
				page.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi negocio"))));
		clickAndWait(miNegocioOption);

		assertVisible("Submenu should show Agregar Negocio",
				page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)));
		assertVisible("Submenu should show Administrar Negocios",
				page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)));
		takeScreenshot("02-mi-negocio-menu-expanded", false);
	}

	private void validateAgregarNegocioModal() {
		final Locator agregarNegocio = firstVisible(
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^agregar negocio$"))),
				page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(true)));
		clickAndWait(agregarNegocio);

		assertVisible("Modal title should be visible",
				page.getByText("Crear Nuevo Negocio", new Page.GetByTextOptions().setExact(false)));
		assertVisible("Nombre del Negocio field should exist",
				page.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)));
		assertVisible("Business limit text should be visible",
				page.getByText("Tienes 2 de 3 negocios", new Page.GetByTextOptions().setExact(false)));
		assertVisible("Cancelar button should be present",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))));
		assertVisible("Crear Negocio button should be present",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear negocio"))));
		takeScreenshot("03-agregar-negocio-modal", false);

		final Locator negocioNameInput = firstVisible(
				page.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)),
				page.getByPlaceholder("Nombre del Negocio", new Page.GetByPlaceholderOptions().setExact(false)));
		negocioNameInput.fill("Negocio Prueba Automatización");
		clickAndWait(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))));
	}

	private void validateAdministrarNegociosView() {
		if (!isVisible(page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)))) {
			clickAndWait(firstVisible(page.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false))));
		}

		clickAndWait(firstVisible(page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar negocios")))));

		assertVisible("Información General section should exist",
				page.getByText("Información General", new Page.GetByTextOptions().setExact(false)));
		assertVisible("Detalles de la Cuenta section should exist",
				page.getByText("Detalles de la Cuenta", new Page.GetByTextOptions().setExact(false)));
		assertVisible("Tus Negocios section should exist",
				page.getByText("Tus Negocios", new Page.GetByTextOptions().setExact(false)));
		assertVisible("Sección Legal should exist",
				page.getByText("Sección Legal", new Page.GetByTextOptions().setExact(false)));
		takeScreenshot("04-administrar-negocios-account-page", true);
	}

	private void validateInformacionGeneral() {
		assertVisible("User name should be visible", page.locator("main").getByText(Pattern.compile(".+")));
		assertVisible("User email should be visible",
				page.getByText(Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")));
		assertVisible("BUSINESS PLAN text should be visible",
				page.getByText("BUSINESS PLAN", new Page.GetByTextOptions().setExact(false)));
		assertVisible("Cambiar Plan button should be visible",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar plan"))));
	}

	private void validateDetallesCuenta() {
		assertVisible("'Cuenta creada' should be visible",
				page.getByText("Cuenta creada", new Page.GetByTextOptions().setExact(false)));
		assertVisible("'Estado activo' should be visible",
				page.getByText(Pattern.compile("(?i)estado\\s+activo")));
		assertVisible("'Idioma seleccionado' should be visible",
				page.getByText(Pattern.compile("(?i)idioma\\s+seleccionado")));
	}

	private void validateTusNegocios() {
		assertVisible("Business list should be visible",
				page.getByText("Tus Negocios", new Page.GetByTextOptions().setExact(false)));
		assertVisible("Agregar Negocio button should exist",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar negocio"))));
		assertVisible("'Tienes 2 de 3 negocios' should be visible",
				page.getByText("Tienes 2 de 3 negocios", new Page.GetByTextOptions().setExact(false)));
	}

	private void validateLegalDocument(final String linkText, final Pattern headingPattern, final String screenshotPrefix) {
		final String appUrlBeforeClick = page.url();
		final Locator link = firstVisible(page.getByText(linkText, new Page.GetByTextOptions().setExact(false)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(headingPattern)));

		Page legalPage = null;
		try {
			legalPage = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(5000), link::click);
		} catch (TimeoutError ignored) {
			// No popup means navigation happened in the current tab.
		}

		if (legalPage != null) {
			waitForUiLoad(legalPage);
			assertVisible("Legal heading should be visible", legalPage.getByText(headingPattern));
			assertLegalContentVisible(legalPage);
			takeScreenshot(legalPage, screenshotPrefix + "-page", false);
			legalUrls.put(linkText, legalPage.url());
			System.out.println(linkText + " URL: " + legalPage.url());
			try {
				legalPage.close();
			} catch (RuntimeException ignored) {
				// already closed by the browser.
			}
			page.bringToFront();
		} else {
			waitForUiLoad(page);
			assertVisible("Legal heading should be visible", page.getByText(headingPattern));
			assertLegalContentVisible(page);
			takeScreenshot(page, screenshotPrefix + "-page", false);
			legalUrls.put(linkText, page.url());
			System.out.println(linkText + " URL: " + page.url());

			// Cleanup: return to app tab/page when navigation happened in-place.
			if (!page.url().equals(appUrlBeforeClick)) {
				page.goBack();
				waitForUiLoad(page);
			}
		}
	}

	private void assertLegalContentVisible(final Page legalPage) {
		final String text = legalPage.locator("body").innerText();
		if (text == null || text.trim().length() < 120) {
			throw new AssertionError("Legal content text is not sufficiently visible.");
		}
	}

	private Page clickExpectingOptionalPopup(final Locator target) {
		try {
			return page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(5000), () -> {
				target.first().click();
				waitForUiLoad(page);
			});
		} catch (TimeoutError ignored) {
			return null;
		}
	}

	private void selectGoogleAccountIfVisible(final Page googlePage) {
		final Locator account = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, new Page.GetByTextOptions().setExact(false));
		if (isVisible(account)) {
			account.first().click();
			waitForUiLoad(googlePage);
		}
	}

	private void clickAndWait(final Locator locator) {
		locator.first().click();
		waitForUiLoad(page);
	}

	private void waitForUiLoad(final Page targetPage) {
		try {
			targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000));
		} catch (RuntimeException ignored) {
			// Some SPAs stay in loading states with open connections. Continue.
		}
		try {
			targetPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000));
		} catch (RuntimeException ignored) {
			// NETWORKIDLE may not happen in apps that continuously poll.
		}
		targetPage.waitForTimeout(500);
	}

	private Locator firstVisible(final Locator... candidates) {
		for (Locator candidate : candidates) {
			if (isVisible(candidate)) {
				return candidate.first();
			}
		}
		throw new AssertionError("No visible locator matched the expected element.");
	}

	private boolean isVisible(final Locator locator) {
		final int count = Math.min(locator.count(), 5);
		for (int i = 0; i < count; i++) {
			try {
				if (locator.nth(i).isVisible()) {
					return true;
				}
			} catch (RuntimeException ignored) {
				// ignore detached elements while scanning alternatives
			}
		}
		return false;
	}

	private void assertVisible(final String message, final Locator locator) {
		if (!isVisible(locator)) {
			throw new AssertionError(message);
		}
	}

	private void takeScreenshot(final String name, final boolean fullPage) {
		takeScreenshot(page, name, fullPage);
	}

	private void takeScreenshot(final Page targetPage, final String name, final boolean fullPage) {
		final Path file = evidenceDir.resolve(sanitizeFileName(name) + ".png");
		targetPage.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(fullPage));
	}

	private Path writeReport(final Map<String, StepResult> report) throws IOException {
		final Path reportFile = evidenceDir.resolve("final-report.txt");
		final StringBuilder content = new StringBuilder();
		content.append("SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
		content.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator()).append(System.lineSeparator());
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			content.append(entry.getKey()).append(": ").append(entry.getValue().passed ? "PASS" : "FAIL");
			if (entry.getValue().detail != null && !entry.getValue().detail.isBlank()) {
				content.append(" - ").append(entry.getValue().detail);
			}
			content.append(System.lineSeparator());
		}
		if (!legalUrls.isEmpty()) {
			content.append(System.lineSeparator()).append("Captured legal URLs").append(System.lineSeparator());
			for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
				content.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}
		Files.writeString(reportFile, content.toString(), StandardCharsets.UTF_8);
		return reportFile;
	}

	private StepResult runStep(final CheckedStep step) {
		try {
			step.run();
			return StepResult.pass();
		} catch (Throwable throwable) {
			return StepResult.fail(throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage());
		}
	}

	private String getConfig(final String systemPropertyKey, final String envKey) {
		final String systemValue = System.getProperty(systemPropertyKey);
		if (systemValue != null && !systemValue.isBlank()) {
			return systemValue;
		}
		return System.getenv(envKey);
	}

	private String getConfigOrDefault(final String systemPropertyKey, final String envKey, final String defaultValue) {
		final String value = getConfig(systemPropertyKey, envKey);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private String sanitizeFileName(final String value) {
		final String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return normalized.replaceAll("[^a-zA-Z0-9._-]+", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
	}

	private interface CheckedStep {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String detail) {
			return new StepResult(false, detail);
		}
	}
}
