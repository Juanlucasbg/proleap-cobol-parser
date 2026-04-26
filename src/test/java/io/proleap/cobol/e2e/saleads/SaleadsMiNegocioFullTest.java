package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Test;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * End-to-end test for the SaleADS "Mi Negocio" workflow.
 *
 * <p>Environment variables / system properties:
 * <ul>
 *   <li>SALEADS_URL or saleads.url: login page URL for the current environment.</li>
 *   <li>SALEADS_HEADLESS or saleads.headless: browser headless mode (default true).</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final double SHORT_TIMEOUT_MS = 4000;
	private static final double DEFAULT_TIMEOUT_MS = 30000;
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final Map<String, String> report = initializeReport();
		final List<String> failures = new ArrayList<String>();
		final Map<String, String> legalUrls = new LinkedHashMap<String, String>();
		final Path evidenceDir = createEvidenceDirectory();

		final String startUrl = readConfig("SALEADS_URL", "saleads.url");
		if (startUrl == null || startUrl.trim().isEmpty()) {
			fail("Missing SALEADS_URL/saleads.url. Provide the login URL for the target SaleADS environment.");
		}

		Page page = null;
		Page appPage = null;

		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
					.setHeadless(Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "saleads.headless", "true")));
			final Browser browser = playwright.chromium().launch(launchOptions);
			final BrowserContext context = browser.newContext();
			page = context.newPage();

			page.navigate(startUrl);
			waitForUi(page);

			appPage = page;
			final Page[] appPageHolder = new Page[] {appPage};

			runStep("Login", report, failures, new StepAction() {
				@Override
				public void run() throws Exception {
					appPageHolder[0] = loginWithGoogle(context, page, evidenceDir);
				}
			});
			appPage = appPageHolder[0];

			if (appPage != null) {
				final Page finalAppPage = appPage;

				runStep("Mi Negocio menu", report, failures, new StepAction() {
					@Override
					public void run() throws Exception {
						expandMiNegocioMenu(finalAppPage, evidenceDir);
					}
				});

				runStep("Agregar Negocio modal", report, failures, new StepAction() {
					@Override
					public void run() throws Exception {
						validateAgregarNegocioModal(finalAppPage, evidenceDir);
					}
				});

				runStep("Administrar Negocios view", report, failures, new StepAction() {
					@Override
					public void run() throws Exception {
						openAdministrarNegocios(finalAppPage, evidenceDir);
					}
				});

				runStep("Informacion General", report, failures, new StepAction() {
					@Override
					public void run() throws Exception {
						validateInformacionGeneral(finalAppPage);
					}
				});

				runStep("Detalles de la Cuenta", report, failures, new StepAction() {
					@Override
					public void run() throws Exception {
						validateDetallesCuenta(finalAppPage);
					}
				});

				runStep("Tus Negocios", report, failures, new StepAction() {
					@Override
					public void run() throws Exception {
						validateTusNegocios(finalAppPage);
					}
				});

				runStep("Terminos y Condiciones", report, failures, new StepAction() {
					@Override
					public void run() throws Exception {
						final String url = validateLegalLink(context, finalAppPage, "Terminos y Condiciones",
								"terminos-condiciones", evidenceDir);
						legalUrls.put("Terminos y Condiciones", url);
					}
				});

				runStep("Politica de Privacidad", report, failures, new StepAction() {
					@Override
					public void run() throws Exception {
						final String url = validateLegalLink(context, finalAppPage, "Politica de Privacidad", "politica-privacidad",
								evidenceDir);
						legalUrls.put("Politica de Privacidad", url);
					}
				});
			}
		}

		printFinalReport(report, legalUrls, evidenceDir);

		if (!failures.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failed:\n - " + String.join("\n - ", failures));
		}
	}

	private static Page loginWithGoogle(final BrowserContext context, final Page page, final Path evidenceDir) throws Exception {
		final Locator loginButton = requireVisible(page, "Login with Google button",
				"button:has-text(\"Google\")",
				"[role='button']:has-text(\"Google\")",
				"a:has-text(\"Google\")",
				"text=/Sign in with Google/i",
				"text=/Iniciar sesion con Google/i",
				"text=/Iniciar sesi[oó]n con Google/i");

		final int pageCountBeforeClick = context.pages().size();
		clickAndWait(page, loginButton);

		Page googlePage = detectNewPage(context, pageCountBeforeClick, DEFAULT_TIMEOUT_MS);
		if (googlePage == null) {
			googlePage = page;
		}

		waitForUi(googlePage);
		selectGoogleAccountIfPresent(googlePage);

		waitForSidebar(page);
		takeScreenshot(page, evidenceDir, "01-dashboard-loaded", true);

		return page;
	}

	private static void selectGoogleAccountIfPresent(final Page googlePage) throws Exception {
		final Locator accountOption = googlePage.locator("text=" + ACCOUNT_EMAIL).first();
		if (isVisible(accountOption, SHORT_TIMEOUT_MS)) {
			clickAndWait(googlePage, accountOption);
			return;
		}

		final Locator chooseAccountHeader = googlePage.locator(
				"text=/Elige una cuenta|Choose an account|Selecciona una cuenta/i").first();
		if (!isVisible(chooseAccountHeader, SHORT_TIMEOUT_MS)) {
			return;
		}

		// If account chooser is open but the account is not listed, the session likely needs manual auth.
		throw new AssertionError("Google account chooser appeared, but account " + ACCOUNT_EMAIL + " was not available.");
	}

	private static void expandMiNegocioMenu(final Page page, final Path evidenceDir) throws Exception {
		waitForSidebar(page);

		final Locator negocioSection = requireVisible(page, "Negocio section",
				"text=/^Negocio$/i",
				"nav >> text=/^Negocio$/i",
				"aside >> text=/^Negocio$/i");
		clickAndWait(page, negocioSection);

		final Locator miNegocioOption = requireVisible(page, "Mi Negocio option",
				"text=/^Mi Negocio$/i",
				"nav >> text=/^Mi Negocio$/i",
				"aside >> text=/^Mi Negocio$/i");
		clickAndWait(page, miNegocioOption);

		requireVisible(page, "Agregar Negocio option",
				"nav >> text=/Agregar Negocio/i",
				"aside >> text=/Agregar Negocio/i",
				"text=/Agregar Negocio/i");
		requireVisible(page, "Administrar Negocios option",
				"nav >> text=/Administrar Negocios/i",
				"aside >> text=/Administrar Negocios/i",
				"text=/Administrar Negocios/i");

		takeScreenshot(page, evidenceDir, "02-mi-negocio-menu-expanded", false);
	}

	private static void validateAgregarNegocioModal(final Page page, final Path evidenceDir) throws Exception {
		ensureMiNegocioSubmenuVisible(page);
		final Locator agregarNegocio = requireVisible(page, "Agregar Negocio action",
				"nav >> text=/Agregar Negocio/i",
				"aside >> text=/Agregar Negocio/i",
				"text=/Agregar Negocio/i");
		clickAndWait(page, agregarNegocio);

		final Locator modalTitle = requireVisible(page, "Crear Nuevo Negocio modal title", "text=/Crear Nuevo Negocio/i");
		requireVisible(page, "Nombre del Negocio field",
				"input[placeholder*='Nombre del Negocio']",
				"label:has-text(\"Nombre del Negocio\")",
				"text=/Nombre del Negocio/i");
		requireVisible(page, "Business quota text", "text=/Tienes\\s*2\\s*de\\s*3\\s*negocios/i");
		requireVisible(page, "Cancelar button", "button:has-text(\"Cancelar\")", "text=/^Cancelar$/i");
		requireVisible(page, "Crear Negocio button", "button:has-text(\"Crear Negocio\")", "text=/Crear Negocio/i");

		final Locator businessNameInput = page.locator("input[placeholder*='Nombre del Negocio'], input[name*='negocio'], input[id*='negocio']").first();
		if (isVisible(businessNameInput, SHORT_TIMEOUT_MS)) {
			businessNameInput.click();
			businessNameInput.fill(TEST_BUSINESS_NAME);
			waitForUi(page);
		}

		takeScreenshot(page, evidenceDir, "03-agregar-negocio-modal", false);

		final Locator cancelButton = requireVisible(page, "Cancelar button",
				"button:has-text(\"Cancelar\")", "text=/^Cancelar$/i");
		clickAndWait(page, cancelButton);

		modalTitle.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(DEFAULT_TIMEOUT_MS));
	}

	private static void openAdministrarNegocios(final Page page, final Path evidenceDir) throws Exception {
		ensureMiNegocioSubmenuVisible(page);

		final Locator administrarNegocios = requireVisible(page, "Administrar Negocios option",
				"nav >> text=/Administrar Negocios/i",
				"aside >> text=/Administrar Negocios/i",
				"text=/Administrar Negocios/i");
		clickAndWait(page, administrarNegocios);

		requireVisible(page, "Informacion General section", "text=/Informaci[oó]n General/i");
		requireVisible(page, "Detalles de la Cuenta section", "text=/Detalles de la Cuenta/i");
		requireVisible(page, "Tus Negocios section", "text=/Tus Negocios/i");
		requireVisible(page, "Seccion Legal section", "text=/Secci[oó]n Legal/i");

		takeScreenshot(page, evidenceDir, "04-administrar-negocios-page", true);
	}

	private static void validateInformacionGeneral(final Page page) throws Exception {
		final String bodyText = page.locator("body").innerText();
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(bodyText);
		assertTrue("User email is not visible in Informacion General.", emailMatcher.find());

		final boolean knownNameVisible = bodyText.toLowerCase(Locale.ROOT).contains("juan")
				|| bodyText.toLowerCase(Locale.ROOT).contains("lucas");
		final boolean nameLabelVisible = page.locator("text=/^Nombre$/i").count() > 0 || page.locator("text=/Nombre completo/i").count() > 0;
		assertTrue("User name is not visible in Informacion General.", knownNameVisible || nameLabelVisible);

		requireVisible(page, "BUSINESS PLAN text", "text=/BUSINESS PLAN/i");
		requireVisible(page, "Cambiar Plan button", "button:has-text(\"Cambiar Plan\")", "text=/Cambiar Plan/i");
	}

	private static void validateDetallesCuenta(final Page page) throws Exception {
		requireVisible(page, "Cuenta creada", "text=/Cuenta creada/i");
		requireVisible(page, "Estado activo", "text=/Estado activo/i");
		requireVisible(page, "Idioma seleccionado", "text=/Idioma seleccionado/i");
	}

	private static void validateTusNegocios(final Page page) throws Exception {
		final Locator tusNegociosSection = requireVisible(page, "Tus Negocios section", "text=/Tus Negocios/i");
		requireVisible(page, "Agregar Negocio button", "button:has-text(\"Agregar Negocio\")", "text=/Agregar Negocio/i");
		requireVisible(page, "Business quota text", "text=/Tienes\\s*2\\s*de\\s*3\\s*negocios/i");

		final String sectionText = tusNegociosSection.innerText();
		assertTrue("Business list does not appear visible in Tus Negocios.",
				sectionText != null && sectionText.replaceAll("\\s+", " ").trim().length() > 10);
	}

	private static String validateLegalLink(final BrowserContext context, final Page appPage, final String linkType,
			final String screenshotName, final Path evidenceDir) throws Exception {
		final String originalUrl = appPage.url();
		final int pageCountBeforeClick = context.pages().size();

		final Locator legalLink = requireVisible(appPage, linkType + " link",
				"text=/" + legalLinkPattern(linkType) + "/i",
				"a:has-text(\"" + linkType + "\")",
				"button:has-text(\"" + linkType + "\")");
		clickAndWait(appPage, legalLink);

		Page targetPage = detectNewPage(context, pageCountBeforeClick, 10000);
		if (targetPage == null) {
			targetPage = appPage;
		}

		waitForUi(targetPage);
		requireVisible(targetPage, linkType + " heading", "text=/" + legalLinkPattern(linkType) + "/i");

		final String legalBodyText = targetPage.locator("body").innerText();
		assertTrue(linkType + " content text is not visible.", legalBodyText != null && legalBodyText.trim().length() > 120);

		takeScreenshot(targetPage, evidenceDir, screenshotName, true);
		final String finalUrl = targetPage.url();

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!originalUrl.equals(finalUrl)) {
			try {
				appPage.goBack(new Page.GoBackOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			} catch (final Exception exception) {
				appPage.navigate(originalUrl);
			}
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private static String legalLinkPattern(final String linkType) {
		if ("Terminos y Condiciones".equals(linkType)) {
			return "T[eé]rminos y Condiciones";
		}

		return "Pol[ií]tica de Privacidad";
	}

	private static void ensureMiNegocioSubmenuVisible(final Page page) throws Exception {
		if (isVisible(page.locator("text=/Administrar Negocios/i").first(), SHORT_TIMEOUT_MS)
				&& isVisible(page.locator("text=/Agregar Negocio/i").first(), SHORT_TIMEOUT_MS)) {
			return;
		}

		final Locator miNegocio = requireVisible(page, "Mi Negocio option", "text=/^Mi Negocio$/i", "text=/Mi Negocio/i");
		clickAndWait(page, miNegocio);

		requireVisible(page, "Agregar Negocio option", "text=/Agregar Negocio/i");
		requireVisible(page, "Administrar Negocios option", "text=/Administrar Negocios/i");
	}

	private static void waitForSidebar(final Page page) throws Exception {
		requireVisible(page, "Left sidebar navigation", "aside", "nav", "text=/Negocio/i", "text=/Mi Negocio/i");
	}

	private static Locator requireVisible(final Page page, final String description, final String... selectors) throws Exception {
		for (final String selector : selectors) {
			final Locator locator = page.locator(selector).first();
			if (isVisible(locator, SHORT_TIMEOUT_MS)) {
				return locator;
			}
		}

		throw new AssertionError(description + " was not visible. Selectors: " + String.join(" | ", selectors));
	}

	private static boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs).setState(WaitForSelectorState.VISIBLE));
			return true;
		} catch (final TimeoutError timeoutError) {
			return false;
		}
	}

	private static void clickAndWait(final Page page, final Locator locator) throws Exception {
		locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUi(page);
	}

	private static void waitForUi(final Page page) throws Exception {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final TimeoutError timeoutError) {
			// Single-page apps can keep long-polling requests open. DOM + a short settle is enough.
		}
		page.waitForTimeout(500);
	}

	private static Page detectNewPage(final BrowserContext context, final int pageCountBeforeClick, final double timeoutMs)
			throws Exception {
		final long deadline = System.currentTimeMillis() + (long) timeoutMs;
		while (System.currentTimeMillis() <= deadline) {
			final List<Page> pages = context.pages();
			if (pages.size() > pageCountBeforeClick) {
				final Page newestPage = pages.get(pages.size() - 1);
				waitForUi(newestPage);
				return newestPage;
			}

			Thread.sleep(200);
		}

		return null;
	}

	private static Path createEvidenceDirectory() throws Exception {
		final Path evidenceDir = Paths.get("target", "saleads-evidence", TIMESTAMP_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private static void takeScreenshot(final Page page, final Path evidenceDir, final String name, final boolean fullPage)
			throws Exception {
		final Path screenshotPath = evidenceDir.resolve(name + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private static String readConfig(final String envName, final String propName) {
		final String propertyValue = System.getProperty(propName);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue;
		}

		return System.getenv(envName);
	}

	private static String readConfig(final String envName, final String propName, final String defaultValue) {
		final String value = readConfig(envName, propName);
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}

		return value;
	}

	private static Map<String, String> initializeReport() {
		final Map<String, String> report = new LinkedHashMap<String, String>();
		report.put("Login", "FAIL");
		report.put("Mi Negocio menu", "FAIL");
		report.put("Agregar Negocio modal", "FAIL");
		report.put("Administrar Negocios view", "FAIL");
		report.put("Informacion General", "FAIL");
		report.put("Detalles de la Cuenta", "FAIL");
		report.put("Tus Negocios", "FAIL");
		report.put("Terminos y Condiciones", "FAIL");
		report.put("Politica de Privacidad", "FAIL");
		return report;
	}

	private static void runStep(final String reportKey, final Map<String, String> report, final List<String> failures,
			final StepAction action) {
		try {
			action.run();
			report.put(reportKey, "PASS");
		} catch (final Throwable throwable) {
			report.put(reportKey, "FAIL");
			failures.add(reportKey + " -> " + throwable.getMessage());
		}
	}

	private static void printFinalReport(final Map<String, String> report, final Map<String, String> legalUrls,
			final Path evidenceDir) {
		final StringBuilder builder = new StringBuilder();
		builder.append("\nSaleADS Mi Negocio final report\n");
		builder.append("--------------------------------\n");
		builder.append("Login: ").append(report.get("Login")).append('\n');
		builder.append("Mi Negocio menu: ").append(report.get("Mi Negocio menu")).append('\n');
		builder.append("Agregar Negocio modal: ").append(report.get("Agregar Negocio modal")).append('\n');
		builder.append("Administrar Negocios view: ").append(report.get("Administrar Negocios view")).append('\n');
		builder.append("Informacion General: ").append(report.get("Informacion General")).append('\n');
		builder.append("Detalles de la Cuenta: ").append(report.get("Detalles de la Cuenta")).append('\n');
		builder.append("Tus Negocios: ").append(report.get("Tus Negocios")).append('\n');
		builder.append("Terminos y Condiciones: ").append(report.get("Terminos y Condiciones")).append('\n');
		builder.append("Politica de Privacidad: ").append(report.get("Politica de Privacidad")).append('\n');
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');

		if (!legalUrls.isEmpty()) {
			builder.append("Legal URLs:\n");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				builder.append(" - ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		System.out.println(builder);
	}

	private interface StepAction {
		void run() throws Exception;
	}
}
