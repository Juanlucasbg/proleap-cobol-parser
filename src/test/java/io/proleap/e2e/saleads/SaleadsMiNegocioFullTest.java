package io.proleap.e2e.saleads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

	private static final long DEFAULT_TIMEOUT_MS = 20000;
	private static final Pattern EMAIL_PATTERN =
			Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Informaci\u00f3n General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"T\u00e9rminos y Condiciones",
			"Pol\u00edtica de Privacidad");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private Path evidenceDir;
	private int screenshotCount = 1;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String loginUrl = propertyOrEnv("saleads.loginUrl", "SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set -Dsaleads.loginUrl (or SALEADS_LOGIN_URL) to run this E2E test.",
				loginUrl != null && !loginUrl.trim().isEmpty());

		evidenceDir = createEvidenceDir();
		final boolean headless = Boolean.parseBoolean(propertyOrEnv("saleads.headless", "SALEADS_HEADLESS", "true"));

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			try (BrowserContext context = browser.newContext(
					new Browser.NewContextOptions().setViewportSize(1600, 1200).setIgnoreHTTPSErrors(true))) {
				context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
				context.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS);
				final Page page = context.newPage();
				page.navigate(loginUrl);
				waitForUiLoad(page);

				executeStep("Login", () -> loginWithGoogle(page, context));
				executeStep("Mi Negocio menu", () -> openMiNegocioMenu(page));
				executeStep("Agregar Negocio modal", () -> validateAgregarNegocioModal(page));
				executeStep("Administrar Negocios view", () -> openAdministrarNegocios(page));
				executeStep("Informaci\u00f3n General", () -> validateInformacionGeneral(page));
				executeStep("Detalles de la Cuenta", () -> validateDetallesCuenta(page));
				executeStep("Tus Negocios", () -> validateTusNegocios(page));
				executeStep("T\u00e9rminos y Condiciones", () ->
						validateLegalLink(page, context, "T\u00e9rminos y Condiciones", "T.rminos y Condiciones", "legal-terminos"));
				executeStep("Pol\u00edtica de Privacidad", () ->
						validateLegalLink(page, context, "Pol\u00edtica de Privacidad", "Pol.tica de Privacidad", "legal-privacidad"));
			} finally {
				browser.close();
			}
		} finally {
			writeFinalReport();
		}

		assertFalse("At least one step failed. See report in: " + evidenceDir.toAbsolutePath(), hasFailures());
	}

	private void loginWithGoogle(final Page page, final BrowserContext context) {
		final Locator loginButton = firstVisible(
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("google|sign in|iniciar sesi.n", Pattern.CASE_INSENSITIVE))),
				page.getByText(Pattern.compile("google|sign in|iniciar sesi.n", Pattern.CASE_INSENSITIVE)));
		assertVisible("Login button or 'Sign in with Google' should be visible", loginButton);

		final Page popup = clickAndCapturePopup(page, context, loginButton);
		if (popup != null) {
			handleGoogleAccountSelection(popup);
			waitForPopupCloseIfOpen(popup);
		} else {
			handleGoogleAccountSelection(page);
		}

		assertVisible("Left sidebar should be visible", firstVisible(
				page.getByText(Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE)),
				page.locator("aside"),
				page.getByRole(AriaRole.NAVIGATION)));
		takeScreenshot(page, "01-dashboard-loaded", false);
	}

	private void openMiNegocioMenu(final Page page) {
		final Locator negocioSection = firstVisible(
				page.getByText(Pattern.compile("^\\s*Negocio\\s*$", Pattern.CASE_INSENSITIVE)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE))));
		assertVisible("Negocio section should be visible in sidebar", negocioSection);

		final Locator miNegocioOption = firstVisible(
				page.getByText(Pattern.compile("Mi Negocio", Pattern.CASE_INSENSITIVE)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("Mi Negocio", Pattern.CASE_INSENSITIVE))));
		clickAndWait(page, miNegocioOption);

		assertVisible("Agregar Negocio should be visible", page.getByText(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE)));
		assertVisible("Administrar Negocios should be visible", page.getByText(Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE)));
		takeScreenshot(page, "02-mi-negocio-expanded", false);
	}

	private void validateAgregarNegocioModal(final Page page) {
		clickAndWait(page, page.getByText(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE)));

		assertVisible("Modal title should be visible",
				page.getByText(Pattern.compile("Crear Nuevo Negocio", Pattern.CASE_INSENSITIVE)));
		final Locator nombreInput = firstVisible(
				page.getByLabel(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
				page.getByPlaceholder(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
				page.locator("input[type='text']"));
		assertVisible("Input field 'Nombre del Negocio' should exist", nombreInput);
		assertVisible("Business quota text should be visible",
				page.getByText(Pattern.compile("Tienes\\s*2\\s*de\\s*3\\s*negocios", Pattern.CASE_INSENSITIVE)));
		assertVisible("Cancelar button should be present",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE))));
		assertVisible("Crear Negocio button should be present",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Crear Negocio", Pattern.CASE_INSENSITIVE))));
		takeScreenshot(page, "03-agregar-negocio-modal", false);

		nombreInput.fill("Negocio Prueba Automatizacion");
		clickAndWait(page, page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE))));
	}

	private void openAdministrarNegocios(final Page page) {
		final Locator administrarNegocios = page.getByText(Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE));
		if (!isVisible(administrarNegocios, 1500)) {
			clickAndWait(page, page.getByText(Pattern.compile("Mi Negocio", Pattern.CASE_INSENSITIVE)));
		}
		clickAndWait(page, administrarNegocios);

		assertVisible("Informacion General section should exist",
				page.getByText(Pattern.compile("Informaci.n General", Pattern.CASE_INSENSITIVE)));
		assertVisible("Detalles de la Cuenta section should exist",
				page.getByText(Pattern.compile("Detalles de la Cuenta", Pattern.CASE_INSENSITIVE)));
		assertVisible("Tus Negocios section should exist",
				page.getByText(Pattern.compile("Tus Negocios", Pattern.CASE_INSENSITIVE)));
		assertVisible("Legal section should exist",
				page.getByText(Pattern.compile("Secci.n Legal|Legal", Pattern.CASE_INSENSITIVE)));
		takeScreenshot(page, "04-administrar-negocios-view", true);
	}

	private void validateInformacionGeneral(final Page page) {
		assertVisible("User email should be visible", page.getByText(EMAIL_PATTERN));
		assertVisible("User name should be visible",
				firstVisible(
						page.getByText(Pattern.compile("Nombre", Pattern.CASE_INSENSITIVE)),
						page.getByText(Pattern.compile("Perfil", Pattern.CASE_INSENSITIVE))));
		assertVisible("'BUSINESS PLAN' should be visible",
				page.getByText(Pattern.compile("BUSINESS PLAN", Pattern.CASE_INSENSITIVE)));
		assertVisible("'Cambiar Plan' button should be visible",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Cambiar Plan", Pattern.CASE_INSENSITIVE))));
	}

	private void validateDetallesCuenta(final Page page) {
		assertVisible("'Cuenta creada' should be visible",
				page.getByText(Pattern.compile("Cuenta creada", Pattern.CASE_INSENSITIVE)));
		assertVisible("'Estado activo' should be visible",
				page.getByText(Pattern.compile("Estado activo", Pattern.CASE_INSENSITIVE)));
		assertVisible("'Idioma seleccionado' should be visible",
				page.getByText(Pattern.compile("Idioma seleccionado", Pattern.CASE_INSENSITIVE)));
	}

	private void validateTusNegocios(final Page page) {
		assertVisible("Business list should be visible", page.getByText(Pattern.compile("Tus Negocios", Pattern.CASE_INSENSITIVE)));
		assertVisible("'Agregar Negocio' button should exist",
				page.getByText(Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE)));
		assertVisible("'Tienes 2 de 3 negocios' should be visible",
				page.getByText(Pattern.compile("Tienes\\s*2\\s*de\\s*3\\s*negocios", Pattern.CASE_INSENSITIVE)));
	}

	private void validateLegalLink(
			final Page page,
			final BrowserContext context,
			final String legalName,
			final String linkRegex,
			final String screenshotName) {
		final Locator link = firstVisible(
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile(linkRegex, Pattern.CASE_INSENSITIVE))),
				page.getByText(Pattern.compile(linkRegex, Pattern.CASE_INSENSITIVE)));
		assertVisible("Legal link should be visible: " + linkRegex, link);

		final String originalUrl = page.url();
		final Page popup = clickAndCapturePopup(page, context, link);
		final Page legalPage = popup != null ? popup : page;
		waitForUiLoad(legalPage);

		assertVisible("Legal page heading should be visible for: " + linkRegex,
				legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile(linkRegex, Pattern.CASE_INSENSITIVE))));
		assertVisible("Legal content text should be visible for: " + linkRegex,
				firstVisible(legalPage.locator("p"), legalPage.locator("article"), legalPage.locator("main")));

		takeScreenshot(legalPage, screenshotName, true);
		legalUrls.put(legalName, legalPage.url());

		if (popup != null) {
			popup.close();
			page.bringToFront();
		} else if (!originalUrl.equals(page.url())) {
			page.goBack();
			waitForUiLoad(page);
		}
	}

	private void handleGoogleAccountSelection(final Page authPage) {
		waitForUiLoad(authPage);
		final Locator accountOption = authPage.getByText(
				Pattern.compile("juanlucasbarbiergarzon@gmail\\.com", Pattern.CASE_INSENSITIVE));
		if (isVisible(accountOption, 8000)) {
			clickAndWait(authPage, accountOption);
		}
	}

	private Page clickAndCapturePopup(final Page page, final BrowserContext context, final Locator locator) {
		try {
			return context.waitForPage(() -> clickAndWait(page, locator),
					new BrowserContext.WaitForPageOptions().setTimeout(5000));
		} catch (TimeoutError timeoutError) {
			return null;
		}
	}

	private void waitForPopupCloseIfOpen(final Page popup) {
		try {
			popup.waitForClose(new Page.WaitForCloseOptions().setTimeout(15000));
		} catch (TimeoutError ignored) {
			// Popup may remain open in some flows; continue with main app validation.
		}
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7000));
		} catch (TimeoutError ignored) {
			// Some applications keep open network requests; DOM loaded is enough.
		}
	}

	private Locator firstVisible(final Locator... locators) {
		for (final Locator locator : locators) {
			if (isVisible(locator, 1500)) {
				return locator.first();
			}
		}
		return locators[0].first();
	}

	private boolean isVisible(final Locator locator, final long timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout((double) timeoutMs));
			return true;
		} catch (RuntimeException ex) {
			return false;
		}
	}

	private void clickAndWait(final Page page, final Locator locator) {
		final Locator target = locator.first();
		target.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout((double) DEFAULT_TIMEOUT_MS));
		target.scrollIntoViewIfNeeded();
		target.click();
		waitForUiLoad(page);
	}

	private void assertVisible(final String message, final Locator locator) {
		assertTrue(message, isVisible(locator, DEFAULT_TIMEOUT_MS));
	}

	private void executeStep(final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, StepResult.pass());
		} catch (Throwable ex) {
			report.put(stepName, StepResult.fail(ex));
		}
	}

	private void takeScreenshot(final Page page, final String label, final boolean fullPage) {
		final String fileName = String.format("%02d-%s.png", screenshotCount++, label);
		final Path screenshotPath = evidenceDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private Path createEvidenceDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Paths.get("target", "e2e-evidence", "saleads_mi_negocio_full_test-" + timestamp);
		return Files.createDirectories(path);
	}

	private void writeFinalReport() throws IOException {
		for (final String field : REPORT_FIELDS) {
			report.putIfAbsent(field, StepResult.fail("Not executed"));
		}

		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator()).append(System.lineSeparator());
		sb.append("Step results").append(System.lineSeparator());
		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			sb.append("- ").append(field).append(": ").append(result.pass ? "PASS" : "FAIL");
			if (!result.message.isEmpty()) {
				sb.append(" (").append(result.message).append(")");
			}
			sb.append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			sb.append(System.lineSeparator()).append("Captured legal URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), sb.toString());
	}

	private boolean hasFailures() {
		for (final StepResult result : report.values()) {
			if (!result.pass) {
				return true;
			}
		}
		return false;
	}

	private String propertyOrEnv(final String propertyName, final String envName) {
		final String property = System.getProperty(propertyName);
		if (property != null && !property.trim().isEmpty()) {
			return property;
		}
		final String env = System.getenv(envName);
		if (env != null && !env.trim().isEmpty()) {
			return env;
		}
		return null;
	}

	private String propertyOrEnv(final String propertyName, final String envName, final String defaultValue) {
		final String value = propertyOrEnv(propertyName, envName);
		return value == null ? defaultValue : value;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean pass;
		private final String message;

		private StepResult(final boolean pass, final String message) {
			this.pass = pass;
			this.message = message;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final Exception ex) {
			return new StepResult(false, ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}

		private static StepResult fail(final Throwable ex) {
			return new StepResult(false, ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}

		private static StepResult fail(final String message) {
			return new StepResult(false, message);
		}
	}
}
