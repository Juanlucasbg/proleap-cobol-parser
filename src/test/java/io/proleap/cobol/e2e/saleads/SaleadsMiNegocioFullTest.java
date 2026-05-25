package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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

public class SaleadsMiNegocioFullTest {

	private static final String ENABLED_PROPERTY = "saleads.e2e.enabled";
	private static final String START_URL_PROPERTY = "saleads.start.url";
	private static final String START_URL_ENV = "SALEADS_START_URL";
	private static final String START_URL_ENV_ALT = "SALEADS_URL";
	private static final String HEADLESS_PROPERTY = "saleads.e2e.headless";
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final double SHORT_TIMEOUT_MS = 2000;
	private static final double MEDIUM_TIMEOUT_MS = 6000;
	private static final double DEFAULT_TIMEOUT_MS = 20000;

	@Test
	public void saleadsMiNegocioWorkflow() throws IOException {
		Assume.assumeTrue("Enable with -D" + ENABLED_PROPERTY + "=true", Boolean.getBoolean(ENABLED_PROPERTY));

		final Path screenshotDir = createScreenshotDir();
		final Map<String, Boolean> report = createReportTemplate();
		final List<String> failures = new ArrayList<String>();
		final Map<String, String> evidenceUrls = new LinkedHashMap<String, String>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = launchBrowser(playwright);

			try {
				final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
				final Page page = context.newPage();
				page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
				page.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS);

				openLoginPage(page);

				runStep(report, failures, "Login", new CheckedRunnable() {
					@Override
					public void run() throws Exception {
						stepLoginWithGoogle(context, page);
						takeScreenshot(page, screenshotDir, "01-dashboard-loaded", false);
					}
				});

				runStep(report, failures, "Mi Negocio menu", new CheckedRunnable() {
					@Override
					public void run() throws Exception {
						stepOpenMiNegocioMenu(page);
						takeScreenshot(page, screenshotDir, "02-mi-negocio-menu-expanded", false);
					}
				});

				runStep(report, failures, "Agregar Negocio modal", new CheckedRunnable() {
					@Override
					public void run() throws Exception {
						stepValidateAgregarNegocioModal(page);
						takeScreenshot(page, screenshotDir, "03-agregar-negocio-modal", false);
					}
				});

				runStep(report, failures, "Administrar Negocios view", new CheckedRunnable() {
					@Override
					public void run() throws Exception {
						stepOpenAdministrarNegocios(page);
						takeScreenshot(page, screenshotDir, "04-administrar-negocios-view", true);
					}
				});

				runStep(report, failures, "Información General", new CheckedRunnable() {
					@Override
					public void run() throws Exception {
						stepValidateInformacionGeneral(page);
					}
				});

				runStep(report, failures, "Detalles de la Cuenta", new CheckedRunnable() {
					@Override
					public void run() throws Exception {
						stepValidateDetallesCuenta(page);
					}
				});

				runStep(report, failures, "Tus Negocios", new CheckedRunnable() {
					@Override
					public void run() throws Exception {
						stepValidateTusNegocios(page);
					}
				});

				runStep(report, failures, "Términos y Condiciones", new CheckedRunnable() {
					@Override
					public void run() throws Exception {
						final String url = stepValidateLegalLink(context, page, "Términos y Condiciones", "Términos y Condiciones",
								"08-terminos-y-condiciones", screenshotDir);
						evidenceUrls.put("Términos y Condiciones URL", url);
					}
				});

				runStep(report, failures, "Política de Privacidad", new CheckedRunnable() {
					@Override
					public void run() throws Exception {
						final String url = stepValidateLegalLink(context, page, "Política de Privacidad", "Política de Privacidad",
								"09-politica-de-privacidad", screenshotDir);
						evidenceUrls.put("Política de Privacidad URL", url);
					}
				});
			} finally {
				browser.close();
			}
		}

		printReport(report, failures, evidenceUrls, screenshotDir);
		assertTrue("SaleADS Mi Negocio workflow failed: " + failures, failures.isEmpty());
	}

	private Browser launchBrowser(final Playwright playwright) {
		final boolean headless = Boolean.parseBoolean(System.getProperty(HEADLESS_PROPERTY, "true"));
		return playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
	}

	private void openLoginPage(final Page page) {
		final String startUrl = firstNonBlank(System.getProperty(START_URL_PROPERTY), System.getenv(START_URL_ENV),
				System.getenv(START_URL_ENV_ALT));

		if (startUrl == null) {
			throw new IllegalStateException("Missing SaleADS login page URL. Provide -D" + START_URL_PROPERTY + "=... or "
					+ START_URL_ENV + "/" + START_URL_ENV_ALT + " environment variable.");
		}

		page.navigate(startUrl);
		waitForUiLoad(page);
	}

	private void stepLoginWithGoogle(final BrowserContext context, final Page appPage) {
		final Locator loginButton = requireAnyVisibleLocator("Login with Google button",
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(
								Pattern.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google)"))),
				appPage.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(
								Pattern.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google)"))),
				appPage.getByText("Sign in with Google", new Page.GetByTextOptions().setExact(false)),
				appPage.getByText("Iniciar sesión con Google", new Page.GetByTextOptions().setExact(false)),
				appPage.getByText("Continuar con Google", new Page.GetByTextOptions().setExact(false)));

		final Page possibleGooglePage = clickAndMaybeGetNewPage(context, appPage, loginButton);
		selectGoogleAccountIfPresent(possibleGooglePage != null ? possibleGooglePage : appPage);
		waitForUiLoad(appPage);

		requireAnyVisibleLocator("Main application interface",
				appPage.locator("main"),
				appPage.getByText("Negocio", new Page.GetByTextOptions().setExact(false)),
				appPage.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)));

		requireAnyVisibleLocator("Left sidebar navigation", appPage.locator("aside"), appPage.getByRole(AriaRole.NAVIGATION),
				appPage.locator("[class*='sidebar']"));
	}

	private void stepOpenMiNegocioMenu(final Page page) {
		Locator miNegocio = firstVisibleLocator(page.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)));

		if (miNegocio == null) {
			final Locator negocio = requireAnyVisibleLocator("Negocio section",
					page.getByText("Negocio", new Page.GetByTextOptions().setExact(false)),
					page.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^negocio$"))));
			clickAndWait(page, negocio);
			miNegocio = requireAnyVisibleLocator("Mi Negocio option",
					page.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)));
		}

		clickAndWait(page, miNegocio);

		requireAnyVisibleLocator("'Agregar Negocio' visible",
				page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)));
		requireAnyVisibleLocator("'Administrar Negocios' visible",
				page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)));
	}

	private void stepValidateAgregarNegocioModal(final Page page) {
		final Locator agregarNegocio = requireAnyVisibleLocator("'Agregar Negocio' menu option",
				page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)));
		clickAndWait(page, agregarNegocio);

		requireAnyVisibleLocator("Modal title 'Crear Nuevo Negocio'",
				page.getByText("Crear Nuevo Negocio", new Page.GetByTextOptions().setExact(false)));

		final Locator nombreInput = firstVisibleLocator(
				page.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)),
				page.getByPlaceholder("Nombre del Negocio", new Page.GetByPlaceholderOptions().setExact(false)),
				page.locator("input[name*='negocio']"),
				page.locator("input[placeholder*='Negocio']"));
		if (nombreInput == null) {
			throw new AssertionError("Input 'Nombre del Negocio' was not found.");
		}

		requireAnyVisibleLocator("Business quota text",
				page.getByText("Tienes 2 de 3 negocios", new Page.GetByTextOptions().setExact(false)));
		requireAnyVisibleLocator("'Cancelar' button",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^cancelar$"))));
		requireAnyVisibleLocator("'Crear Negocio' button", page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^crear negocio$"))));

		nombreInput.click();
		nombreInput.fill("Negocio Prueba Automatización");

		final Locator cancelar = requireAnyVisibleLocator("Modal cancel button",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^cancelar$"))));
		clickAndWait(page, cancelar);
	}

	private void stepOpenAdministrarNegocios(final Page page) {
		Locator administrar = firstVisibleLocator(
				page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)));
		if (administrar == null) {
			final Locator miNegocio = requireAnyVisibleLocator("Mi Negocio option",
					page.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)));
			clickAndWait(page, miNegocio);
			administrar = requireAnyVisibleLocator("Administrar Negocios option",
					page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)));
		}

		clickAndWait(page, administrar);

		requireAnyVisibleLocator("'Información General' section",
				page.getByText("Información General", new Page.GetByTextOptions().setExact(false)));
		requireAnyVisibleLocator("'Detalles de la Cuenta' section",
				page.getByText("Detalles de la Cuenta", new Page.GetByTextOptions().setExact(false)));
		requireAnyVisibleLocator("'Tus Negocios' section", page.getByText("Tus Negocios", new Page.GetByTextOptions().setExact(false)));
		requireAnyVisibleLocator("'Sección Legal' section",
				page.getByText("Sección Legal", new Page.GetByTextOptions().setExact(false)));
	}

	private void stepValidateInformacionGeneral(final Page page) {
		final Locator section = requireSection(page, "Información General");

		final boolean hasEmail = isVisible(
				section.getByText(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")).first(), MEDIUM_TIMEOUT_MS)
				|| isVisible(page.getByText(GOOGLE_ACCOUNT, new Page.GetByTextOptions().setExact(false)).first(), MEDIUM_TIMEOUT_MS);
		if (!hasEmail) {
			throw new AssertionError("User email is not visible in 'Información General'.");
		}

		final String sectionText = normalizeMultiline(section.innerText());
		if (!containsLikelyUserName(sectionText)) {
			throw new AssertionError("User name is not visible in 'Información General'.");
		}

		requireAnyVisibleLocator("'BUSINESS PLAN' text",
				section.getByText("BUSINESS PLAN", new Locator.GetByTextOptions().setExact(false)));
		requireAnyVisibleLocator("'Cambiar Plan' button",
				section.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar plan"))),
				section.getByText("Cambiar Plan", new Locator.GetByTextOptions().setExact(false)));
	}

	private void stepValidateDetallesCuenta(final Page page) {
		final Locator section = requireSection(page, "Detalles de la Cuenta");
		requireAnyVisibleLocator("'Cuenta creada' visible",
				section.getByText("Cuenta creada", new Locator.GetByTextOptions().setExact(false)));
		requireAnyVisibleLocator("'Estado activo' visible",
				section.getByText("Estado activo", new Locator.GetByTextOptions().setExact(false)));
		requireAnyVisibleLocator("'Idioma seleccionado' visible",
				section.getByText("Idioma seleccionado", new Locator.GetByTextOptions().setExact(false)));
	}

	private void stepValidateTusNegocios(final Page page) {
		final Locator section = requireSection(page, "Tus Negocios");
		requireAnyVisibleLocator("'Agregar Negocio' button in business section",
				section.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile("(?i)agregar negocio"))),
				section.getByText("Agregar Negocio", new Locator.GetByTextOptions().setExact(false)));
		requireAnyVisibleLocator("Business quota text in 'Tus Negocios'",
				section.getByText("Tienes 2 de 3 negocios", new Locator.GetByTextOptions().setExact(false)));

		final int listElements = section.locator("li, [role='row'], [class*='business'], [class*='negocio']").count();
		final String text = normalizeMultiline(section.innerText());
		final boolean hasListText = text.length() > 30 && !text.equalsIgnoreCase("Tus Negocios");
		if (listElements <= 0 && !hasListText) {
			throw new AssertionError("Business list is not visible in 'Tus Negocios'.");
		}
	}

	private String stepValidateLegalLink(final BrowserContext context, final Page appPage, final String linkText,
			final String expectedHeading, final String screenshotLabel, final Path screenshotDir) throws IOException {
		final Locator link = requireAnyVisibleLocator("'" + linkText + "' link",
				appPage.getByText(linkText, new Page.GetByTextOptions().setExact(false)),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))));
		final String originalAppUrl = appPage.url();
		final Page newPage = clickAndMaybeGetNewPage(context, appPage, link);
		final Page legalPage = newPage != null ? newPage : appPage;

		waitForUiLoad(legalPage);
		requireAnyVisibleLocator("Legal page heading '" + expectedHeading + "'",
				legalPage.getByText(expectedHeading, new Page.GetByTextOptions().setExact(false)));

		final String bodyText = normalizeMultiline(legalPage.locator("body").innerText());
		if (bodyText.length() < 120) {
			throw new AssertionError("Legal content text is too short for '" + expectedHeading + "'.");
		}

		takeScreenshot(legalPage, screenshotDir, screenshotLabel, true);
		final String legalUrl = legalPage.url();

		if (newPage != null) {
			newPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else if (!appPage.url().equals(originalAppUrl)) {
			try {
				appPage.goBack();
				waitForUiLoad(appPage);
			} catch (final PlaywrightException error) {
				appPage.navigate(originalAppUrl);
				waitForUiLoad(appPage);
			}
		}

		return legalUrl;
	}

	private Path createScreenshotDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Paths.get("target", "saleads-screenshots", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private void takeScreenshot(final Page page, final Path directory, final String filename, final boolean fullPage)
			throws IOException {
		Files.createDirectories(directory);
		page.screenshot(new Page.ScreenshotOptions().setPath(directory.resolve(filename + ".png")).setFullPage(fullPage));
	}

	private Locator requireSection(final Page page, final String sectionTitle) {
		final Locator heading = requireAnyVisibleLocator("Section heading '" + sectionTitle + "'",
				page.getByText(sectionTitle, new Page.GetByTextOptions().setExact(false)));

		final Locator container = heading.locator("xpath=ancestor::*[self::section or self::div][1]");
		if (isVisible(container.first(), SHORT_TIMEOUT_MS)) {
			return container.first();
		}

		return page.locator("body");
	}

	private Page clickAndMaybeGetNewPage(final BrowserContext context, final Page page, final Locator locator) {
		Page newPage = null;
		try {
			newPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(5000), new Runnable() {
				@Override
				public void run() {
					safeClick(locator);
				}
			});
		} catch (final PlaywrightException timeoutOrNoTab) {
			// Same-tab navigation is valid here.
		}

		if (newPage == null) {
			waitForUiLoad(page);
		} else {
			waitForUiLoad(newPage);
		}

		return newPage;
	}

	private void clickAndWait(final Page page, final Locator locator) {
		safeClick(locator);
		waitForUiLoad(page);
	}

	private void safeClick(final Locator locator) {
		locator.first().scrollIntoViewIfNeeded();
		locator.first().click();
	}

	private void selectGoogleAccountIfPresent(final Page page) {
		if (page == null) {
			return;
		}

		final Locator account = firstVisibleLocator(
				page.getByText(GOOGLE_ACCOUNT, new Page.GetByTextOptions().setExact(false)),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT)))));

		if (account != null) {
			clickAndWait(page, account);
		}
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// Keep flow resilient across environments with async content.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			// Some apps keep long-lived network calls open.
		}

		page.waitForTimeout(600);
	}

	private Locator requireAnyVisibleLocator(final String description, final Locator... candidates) {
		final Locator visible = firstVisibleLocator(candidates);
		if (visible == null) {
			throw new AssertionError(description + " was not visible.");
		}
		return visible;
	}

	private Locator firstVisibleLocator(final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate == null) {
				continue;
			}

			final Locator first = candidate.first();
			if (isVisible(first, SHORT_TIMEOUT_MS) || isVisible(first, MEDIUM_TIMEOUT_MS)) {
				return first;
			}
		}
		return null;
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
		} catch (final PlaywrightException error) {
			return false;
		}
	}

	private boolean containsLikelyUserName(final String sectionText) {
		for (final String rawLine : sectionText.split("\n")) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			final String lower = line.toLowerCase(Locale.ROOT);

			if (line.contains("@")
					|| lower.contains("información general")
					|| lower.contains("informacion general")
					|| lower.contains("business plan")
					|| lower.contains("cambiar plan")) {
				continue;
			}

			if (line.length() >= 3) {
				return true;
			}
		}
		return false;
	}

	private String normalizeMultiline(final String text) {
		return text == null ? "" : text.replace("\r", "").trim();
	}

	private Map<String, Boolean> createReportTemplate() {
		final Map<String, Boolean> report = new LinkedHashMap<String, Boolean>();
		for (final String key : Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
				"Información General", "Detalles de la Cuenta", "Tus Negocios", "Términos y Condiciones", "Política de Privacidad")) {
			report.put(key, Boolean.FALSE);
		}
		return report;
	}

	private void runStep(final Map<String, Boolean> report, final List<String> failures, final String reportKey,
			final CheckedRunnable action) {
		try {
			action.run();
			report.put(reportKey, Boolean.TRUE);
		} catch (final Throwable error) {
			report.put(reportKey, Boolean.FALSE);
			failures.add(reportKey + " -> " + error.getMessage());
		}
	}

	private void printReport(final Map<String, Boolean> report, final List<String> failures, final Map<String, String> evidenceUrls,
			final Path screenshotDir) {
		System.out.println("=== SaleADS Mi Negocio Full Test Report ===");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue().booleanValue() ? "PASS" : "FAIL"));
		}

		if (!evidenceUrls.isEmpty()) {
			for (final Map.Entry<String, String> entry : evidenceUrls.entrySet()) {
				System.out.println(entry.getKey() + ": " + entry.getValue());
			}
		}

		System.out.println("Screenshot path: " + screenshotDir.toAbsolutePath());
		if (!failures.isEmpty()) {
			System.out.println("Failures: " + failures);
		}
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
