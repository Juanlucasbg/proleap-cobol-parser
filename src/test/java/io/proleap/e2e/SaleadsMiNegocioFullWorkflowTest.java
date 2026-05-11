package io.proleap.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Pattern GOOGLE_LOGIN_TEXT = Pattern
			.compile("(?i)(sign\\s*in|login|iniciar\\s*sesi[oó]n|continuar).*google");
	private static final Pattern TERMS_TEXT = Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones");
	private static final Pattern PRIVACY_TEXT = Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad");
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"(?i)\\b[\\w.%+-]+@[\\w.-]+\\.[a-z]{2,}\\b");
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();
	private Path evidenceDir;

	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		initializeReport();

		final String cdpUrl = envOrProperty("SALEADS_CDP_URL", "saleads.cdp.url");
		final String loginUrl = envOrProperty("SALEADS_LOGIN_URL", "saleads.login.url");

		Assume.assumeTrue(
				"Set SALEADS_CDP_URL (attach to browser already open on login) or SALEADS_LOGIN_URL (environment URL).",
				hasValue(cdpUrl) || hasValue(loginUrl));

		evidenceDir = buildEvidenceDir();

		try (Playwright playwright = Playwright.create()) {
			bootstrapBrowser(playwright, cdpUrl, loginUrl);

			final boolean login = executeStep("Login", this::loginWithGoogleAndValidateShell);
			final boolean menu = login ? executeStep("Mi Negocio menu", this::openMiNegocioMenu)
					: skipStep("Mi Negocio menu", "Skipped because Login failed.");
			final boolean modal = menu ? executeStep("Agregar Negocio modal", this::validateAgregarNegocioModal)
					: skipStep("Agregar Negocio modal", "Skipped because Mi Negocio menu failed.");
			final boolean adminView = menu ? executeStep("Administrar Negocios view", this::openAdministrarNegocios)
					: skipStep("Administrar Negocios view", "Skipped because Mi Negocio menu failed.");
			final boolean infoGeneral = adminView ? executeStep("Información General", this::validateInformacionGeneral)
					: skipStep("Información General", "Skipped because Administrar Negocios view failed.");
			final boolean detallesCuenta = adminView ? executeStep("Detalles de la Cuenta", this::validateDetallesCuenta)
					: skipStep("Detalles de la Cuenta", "Skipped because Administrar Negocios view failed.");
			final boolean tusNegocios = adminView ? executeStep("Tus Negocios", this::validateTusNegocios)
					: skipStep("Tus Negocios", "Skipped because Administrar Negocios view failed.");
			final boolean terms = adminView
					? executeStep("Términos y Condiciones",
							() -> openLegalDocument(TERMS_TEXT, "Términos y Condiciones",
									"06-terminos-y-condiciones.png", true))
					: skipStep("Términos y Condiciones", "Skipped because Administrar Negocios view failed.");
			final boolean privacy = adminView
					? executeStep("Política de Privacidad",
							() -> openLegalDocument(PRIVACY_TEXT, "Política de Privacidad",
									"07-politica-de-privacidad.png", false))
					: skipStep("Política de Privacidad", "Skipped because Administrar Negocios view failed.");

			writeFinalReport(login, menu, modal, adminView, infoGeneral, detallesCuenta, tusNegocios, terms, privacy);
		} finally {
			closeBrowserSafely();
		}

		Assert.assertTrue("Workflow validation failed:\n- " + String.join("\n- ", failures), failures.isEmpty());
	}

	private void bootstrapBrowser(final Playwright playwright, final String cdpUrl, final String loginUrl) {
		if (hasValue(cdpUrl)) {
			browser = playwright.chromium().connectOverCDP(cdpUrl);
			context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
			appPage = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
			appPage.bringToFront();
			waitForUi(appPage);
			return;
		}

		final boolean headless = Boolean.parseBoolean(
				envOrProperty("SALEADS_HEADLESS", "saleads.headless", "true"));
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
		appPage = context.newPage();
		appPage.navigate(loginUrl);
		waitForUi(appPage);
	}

	private void loginWithGoogleAndValidateShell() {
		final Locator loginButton = firstVisible(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_TEXT)),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_TEXT)),
				appPage.getByText("Sign in with Google", new Page.GetByTextOptions().setExact(false)),
				appPage.getByText("Iniciar sesión con Google", new Page.GetByTextOptions().setExact(false)),
				appPage.getByText("Continuar con Google", new Page.GetByTextOptions().setExact(false)));

		if (loginButton == null) {
			throw new IllegalStateException("Google login button was not found.");
		}

		final int pageCountBeforeClick = context.pages().size();
		clickAndWait(loginButton, appPage);

		Page googleSurface = appPage;
		if (context.pages().size() > pageCountBeforeClick) {
			googleSurface = context.pages().get(context.pages().size() - 1);
			googleSurface.bringToFront();
			waitForUi(googleSurface);
		}

		final String googleEmail = envOrProperty("SALEADS_GOOGLE_ACCOUNT", "saleads.google.account",
				DEFAULT_GOOGLE_ACCOUNT);
		final Locator accountSelector = firstVisible(
				googleSurface.getByText(googleEmail, new Page.GetByTextOptions().setExact(false)),
				googleSurface.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)"
						+ Pattern.quote(googleEmail)))));

		if (accountSelector != null) {
			clickAndWait(accountSelector, googleSurface);
		}

		waitForUi(appPage);
		final boolean mainInterface = waitForAnyVisible(45_000, appPage.locator("main"),
				appPage.getByText("Dashboard", new Page.GetByTextOptions().setExact(false)),
				appPage.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)));
		final boolean sidebar = waitForAnyVisible(45_000,
				appPage.locator("aside").getByText("Negocio", new Locator.GetByTextOptions().setExact(false)),
				appPage.locator("nav").getByText("Negocio", new Locator.GetByTextOptions().setExact(false)),
				appPage.getByText("Negocio", new Page.GetByTextOptions().setExact(false)));

		if (!mainInterface || !sidebar) {
			throw new IllegalStateException("Main application shell did not appear after Google login.");
		}

		screenshot(appPage, "01-dashboard-loaded.png", true);
	}

	private void openMiNegocioMenu() {
		final Locator miNegocio = firstVisible(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Mi Negocio")),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Mi Negocio")),
				appPage.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)));

		if (miNegocio == null) {
			throw new IllegalStateException("'Mi Negocio' menu item was not found.");
		}

		clickAndWait(miNegocio, appPage);

		final boolean agregarVisible = waitForAnyVisible(15_000,
				appPage.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Agregar Negocio")));
		final boolean administrarVisible = waitForAnyVisible(15_000,
				appPage.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Administrar Negocios")),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Administrar Negocios")));

		if (!agregarVisible || !administrarVisible) {
			throw new IllegalStateException("'Mi Negocio' submenu did not expand as expected.");
		}

		screenshot(appPage, "02-mi-negocio-menu-expanded.png", true);
	}

	private void validateAgregarNegocioModal() {
		final Locator agregarNegocio = firstVisible(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Agregar Negocio")),
				appPage.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)));

		if (agregarNegocio == null) {
			throw new IllegalStateException("'Agregar Negocio' entry was not found.");
		}

		clickAndWait(agregarNegocio, appPage);

		final boolean titleVisible = waitForAnyVisible(10_000,
				appPage.getByText("Crear Nuevo Negocio", new Page.GetByTextOptions().setExact(false)));
		final Locator businessNameField = firstVisible(
				appPage.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)),
				appPage.getByPlaceholder("Nombre del Negocio", new Page.GetByPlaceholderOptions().setExact(false)),
				appPage.locator("input[name*='negocio']"));
		final boolean businessQuotaVisible = waitForAnyVisible(10_000,
				appPage.getByText("Tienes 2 de 3 negocios", new Page.GetByTextOptions().setExact(false)));
		final Locator cancelButton = firstVisible(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
				appPage.getByText("Cancelar", new Page.GetByTextOptions().setExact(false)));
		final boolean createButtonVisible = waitForAnyVisible(10_000,
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")),
				appPage.getByText("Crear Negocio", new Page.GetByTextOptions().setExact(false)));

		if (!titleVisible || businessNameField == null || !businessQuotaVisible || cancelButton == null
				|| !createButtonVisible) {
			throw new IllegalStateException("The 'Crear Nuevo Negocio' modal is missing expected controls.");
		}

		businessNameField.first().click();
		businessNameField.first().fill("Negocio Prueba Automatización");
		screenshot(appPage, "03-agregar-negocio-modal.png", true);
		clickAndWait(cancelButton, appPage);
	}

	private void openAdministrarNegocios() {
		final boolean administrarVisible = isVisible(
				appPage.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)), 3_000);
		if (!administrarVisible) {
			final Locator miNegocio = firstVisible(
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Mi Negocio")),
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Mi Negocio")),
					appPage.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)));
			if (miNegocio != null) {
				clickAndWait(miNegocio, appPage);
			}
		}

		final Locator administrar = firstVisible(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Administrar Negocios")),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Administrar Negocios")),
				appPage.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)));
		if (administrar == null) {
			throw new IllegalStateException("'Administrar Negocios' option was not found.");
		}

		clickAndWait(administrar, appPage);
		waitForUi(appPage);

		final boolean infoGeneral = waitForAnyVisible(15_000,
				appPage.getByText("Información General", new Page.GetByTextOptions().setExact(false)));
		final boolean detallesCuenta = waitForAnyVisible(15_000,
				appPage.getByText("Detalles de la Cuenta", new Page.GetByTextOptions().setExact(false)));
		final boolean tusNegocios = waitForAnyVisible(15_000,
				appPage.getByText("Tus Negocios", new Page.GetByTextOptions().setExact(false)));
		final boolean legal = waitForAnyVisible(15_000,
				appPage.getByText("Sección Legal", new Page.GetByTextOptions().setExact(false)));

		if (!infoGeneral || !detallesCuenta || !tusNegocios || !legal) {
			throw new IllegalStateException("'Administrar Negocios' page is missing expected sections.");
		}

		screenshot(appPage, "04-administrar-negocios-view.png", true);
	}

	private void validateInformacionGeneral() {
		final String pageText = safePageText();
		final String configuredExpectedName = envOrProperty("SALEADS_EXPECTED_USER_NAME", "saleads.expected.user.name");
		final String configuredExpectedEmail = envOrProperty("SALEADS_EXPECTED_USER_EMAIL", "saleads.expected.user.email",
				DEFAULT_GOOGLE_ACCOUNT);

		final boolean userNameVisible = hasValue(configuredExpectedName)
				? textContainsIgnoreCase(pageText, configuredExpectedName)
				: containsAnyToken(pageText, "Nombre", "Usuario", "User");
		final boolean userEmailVisible = hasValue(configuredExpectedEmail)
				? textContainsIgnoreCase(pageText, configuredExpectedEmail)
				: EMAIL_PATTERN.matcher(pageText).find();
		final boolean businessPlanVisible = waitForAnyVisible(8_000,
				appPage.getByText("BUSINESS PLAN", new Page.GetByTextOptions().setExact(false)));
		final boolean cambiarPlanVisible = waitForAnyVisible(8_000,
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")),
				appPage.getByText("Cambiar Plan", new Page.GetByTextOptions().setExact(false)));

		if (!userNameVisible || !userEmailVisible || !businessPlanVisible || !cambiarPlanVisible) {
			throw new IllegalStateException(
					"'Información General' validation failed for username/email/plan/plan-change button.");
		}
	}

	private void validateDetallesCuenta() {
		final boolean cuentaCreada = waitForAnyVisible(8_000,
				appPage.getByText("Cuenta creada", new Page.GetByTextOptions().setExact(false)));
		final boolean estadoActivo = waitForAnyVisible(8_000,
				appPage.getByText("Estado activo", new Page.GetByTextOptions().setExact(false)));
		final boolean idiomaSeleccionado = waitForAnyVisible(8_000,
				appPage.getByText("Idioma seleccionado", new Page.GetByTextOptions().setExact(false)));

		if (!cuentaCreada || !estadoActivo || !idiomaSeleccionado) {
			throw new IllegalStateException("'Detalles de la Cuenta' is missing expected labels.");
		}
	}

	private void validateTusNegocios() {
		final boolean tusNegociosHeading = waitForAnyVisible(8_000,
				appPage.getByText("Tus Negocios", new Page.GetByTextOptions().setExact(false)));
		final boolean agregarNegocioButton = waitForAnyVisible(8_000,
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
				appPage.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)));
		final boolean quotaText = waitForAnyVisible(8_000,
				appPage.getByText("Tienes 2 de 3 negocios", new Page.GetByTextOptions().setExact(false)));
		boolean businessListVisible = appPage.locator("ul li, [role='listitem'], table tbody tr").count() > 0;
		if (!businessListVisible) {
			final String sectionText = safePageText();
			businessListVisible = textContainsIgnoreCase(sectionText, "Negocio");
		}

		if (!tusNegociosHeading || !agregarNegocioButton || !quotaText || !businessListVisible) {
			throw new IllegalStateException("'Tus Negocios' validation failed.");
		}
	}

	private void openLegalDocument(final Pattern linkPattern, final String headingText, final String screenshotName,
			final boolean isTerms) {
		final Locator link = firstVisible(
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)),
				appPage.getByText(headingText, new Page.GetByTextOptions().setExact(false)));
		if (link == null) {
			throw new IllegalStateException("Legal link was not found: " + headingText);
		}

		final int pagesBeforeClick = context.pages().size();
		link.first().click();
		sleep(1_200);

		Page legalPage = appPage;
		boolean openedNewTab = false;
		if (context.pages().size() > pagesBeforeClick) {
			legalPage = context.pages().get(context.pages().size() - 1);
			openedNewTab = true;
		}

		legalPage.bringToFront();
		waitForUi(legalPage);

		final boolean headingVisible = waitForAnyVisible(10_000,
				legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(linkPattern)),
				legalPage.getByText(headingText, new Page.GetByTextOptions().setExact(false)));
		final String legalText = safeText(legalPage.locator("body"));
		final boolean legalContentVisible = legalText != null && legalText.trim().length() > 200;

		if (!headingVisible || !legalContentVisible) {
			throw new IllegalStateException("Legal document validation failed: " + headingText);
		}

		screenshot(legalPage, screenshotName, true);

		if (isTerms) {
			termsUrl = legalPage.url();
		} else {
			privacyUrl = legalPage.url();
		}

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}
	}

	private boolean executeStep(final String reportKey, final CheckedRunnable runnable) {
		try {
			runnable.run();
			finalReport.put(reportKey, true);
			return true;
		} catch (Exception exception) {
			finalReport.put(reportKey, false);
			failures.add(reportKey + " -> " + exception.getMessage());
			return false;
		}
	}

	private boolean skipStep(final String reportKey, final String reason) {
		finalReport.put(reportKey, false);
		failures.add(reportKey + " -> " + reason);
		return false;
	}

	private void writeFinalReport(final boolean login, final boolean menu, final boolean modal, final boolean adminView,
			final boolean infoGeneral, final boolean detallesCuenta, final boolean tusNegocios, final boolean terms,
			final boolean privacy) throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio - Full Workflow Report\n\n");
		report.append("Login: ").append(passFail(login)).append('\n');
		report.append("Mi Negocio menu: ").append(passFail(menu)).append('\n');
		report.append("Agregar Negocio modal: ").append(passFail(modal)).append('\n');
		report.append("Administrar Negocios view: ").append(passFail(adminView)).append('\n');
		report.append("Información General: ").append(passFail(infoGeneral)).append('\n');
		report.append("Detalles de la Cuenta: ").append(passFail(detallesCuenta)).append('\n');
		report.append("Tus Negocios: ").append(passFail(tusNegocios)).append('\n');
		report.append("Términos y Condiciones: ").append(passFail(terms)).append('\n');
		report.append("Política de Privacidad: ").append(passFail(privacy)).append('\n');
		report.append('\n');
		report.append("Final URL - Términos y Condiciones: ").append(termsUrl).append('\n');
		report.append("Final URL - Política de Privacidad: ").append(privacyUrl).append('\n');
		report.append('\n');

		if (!failures.isEmpty()) {
			report.append("Failures:\n");
			for (final String failure : failures) {
				report.append("- ").append(failure).append('\n');
			}
		}

		final Path reportFile = evidenceDir.resolve("08-final-report.txt");
		Files.writeString(reportFile, report.toString(), StandardCharsets.UTF_8);
		System.out.println(report);
	}

	private void initializeReport() {
		finalReport.put("Login", false);
		finalReport.put("Mi Negocio menu", false);
		finalReport.put("Agregar Negocio modal", false);
		finalReport.put("Administrar Negocios view", false);
		finalReport.put("Información General", false);
		finalReport.put("Detalles de la Cuenta", false);
		finalReport.put("Tus Negocios", false);
		finalReport.put("Términos y Condiciones", false);
		finalReport.put("Política de Privacidad", false);
	}

	private Path buildEvidenceDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private void screenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15_000));
		} catch (PlaywrightException ignored) {
			// Some UI transitions do not trigger navigation events.
		}
		sleep(900);
	}

	private void clickAndWait(final Locator locator, final Page currentPage) {
		locator.first().click();
		waitForUi(currentPage);
	}

	private boolean waitForAnyVisible(final long timeoutMs, final Locator... locators) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final Locator locator : locators) {
				if (isVisible(locator, 1_000)) {
					return true;
				}
			}
			sleep(250);
		}
		return false;
	}

	private Locator firstVisible(final Locator... locators) {
		for (final Locator locator : locators) {
			if (isVisible(locator, 2_500)) {
				return locator;
			}
		}
		return null;
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			return locator != null && locator.first()
					.isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
		} catch (PlaywrightException exception) {
			return false;
		}
	}

	private String safePageText() {
		return safeText(appPage.locator("body"));
	}

	private String safeText(final Locator locator) {
		try {
			final String text = locator.first().textContent();
			return text == null ? "" : text;
		} catch (PlaywrightException exception) {
			return "";
		}
	}

	private void closeBrowserSafely() {
		if (browser == null) {
			return;
		}
		try {
			browser.close();
		} catch (PlaywrightException ignored) {
			// Ignore cleanup errors.
		}
	}

	private static String passFail(final boolean value) {
		return value ? "PASS" : "FAIL";
	}

	private static void sleep(final long milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private static boolean textContainsIgnoreCase(final String source, final String token) {
		return source.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
	}

	private static boolean containsAnyToken(final String source, final String... tokens) {
		for (final String token : tokens) {
			if (textContainsIgnoreCase(source, token)) {
				return true;
			}
		}
		return false;
	}

	private static String envOrProperty(final String envName, final String propertyName) {
		return envOrProperty(envName, propertyName, "");
	}

	private static String envOrProperty(final String envName, final String propertyName, final String defaultValue) {
		final String envValue = System.getenv(envName);
		if (hasValue(envValue)) {
			return envValue.trim();
		}

		final String propertyValue = System.getProperty(propertyName);
		if (hasValue(propertyValue)) {
			return propertyValue.trim();
		}

		return defaultValue;
	}

	private static boolean hasValue(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
