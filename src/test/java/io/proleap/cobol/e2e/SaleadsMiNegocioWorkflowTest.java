package io.proleap.cobol.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.WaitForPopupOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * The test is environment-agnostic:
 * - It does not hardcode SaleADS domains.
 * - It can reuse an already opened browser tab via CDP (recommended when login page is already open):
 *   -Dsaleads.cdp.url=ws://...
 * - Or it can navigate to a provided login page:
 *   -Dsaleads.login.url=https://<current-environment>/login
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
	private static final int DEFAULT_TIMEOUT_MS = 20_000;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final SaleadsRunConfig config = SaleadsRunConfig.fromSystem();
		Files.createDirectories(config.screenshotsDir);

		final Map<String, Boolean> report = new LinkedHashMap<>();
		final AtomicReference<String> termsUrl = new AtomicReference<>("N/A");
		final AtomicReference<String> privacyUrl = new AtomicReference<>("N/A");

		try (Playwright playwright = Playwright.create()) {
			final Session session = startSession(playwright, config);
			final Page page = session.page;

			boolean loginOk = executeStep(report, "Login", () -> {
				stepLoginWithGoogle(page, config.googleAccountEmail);
				takeScreenshot(page, config.screenshotsDir, "01-dashboard-loaded");
			});

			boolean miNegocioMenuOk = loginOk && executeStep(report, "Mi Negocio menu", () -> {
				openMiNegocioMenu(page);
				assertVisibleText(page, "Agregar Negocio");
				assertVisibleText(page, "Administrar Negocios");
				takeScreenshot(page, config.screenshotsDir, "02-mi-negocio-menu-expanded");
			});

			boolean agregarNegocioModalOk = miNegocioMenuOk && executeStep(report, "Agregar Negocio modal", () -> {
				clickByVisibleText(page, "Agregar Negocio");
				assertVisibleText(page, "Crear Nuevo Negocio");
				assertVisibleLabelOrPlaceholder(page, "Nombre del Negocio");
				assertVisibleText(page, "Tienes 2 de 3 negocios");
				assertButtonVisibleAny(page, "Cancelar");
				assertButtonVisibleAny(page, "Crear Negocio");
				takeScreenshot(page, config.screenshotsDir, "03-agregar-negocio-modal");

				fillIfVisible(page, "Nombre del Negocio", "Negocio Prueba Automatizacion");
				clickByVisibleText(page, "Cancelar");
				waitForUi(page);
			});

			boolean administrarNegociosOk = agregarNegocioModalOk && executeStep(report, "Administrar Negocios view", () -> {
				openMiNegocioMenu(page);
				clickByVisibleText(page, "Administrar Negocios");
				waitForUi(page);
				assertVisibleTextAny(page, "Informaci\u00f3n General", "Informacion General");
				assertVisibleText(page, "Detalles de la Cuenta");
				assertVisibleText(page, "Tus Negocios");
				assertVisibleTextAny(page, "Secci\u00f3n Legal", "Seccion Legal");
				takeFullScreenshot(page, config.screenshotsDir, "04-administrar-negocios");
			});

			boolean informacionGeneralOk = administrarNegociosOk && executeStep(report, "Informaci\u00f3n General", () -> {
				assertHasAnyVisibleText(page, "BUSINESS PLAN");
				assertButtonVisibleAny(page, "Cambiar Plan");
				assertContainsEmail(page);
			});

			boolean detallesCuentaOk = informacionGeneralOk && executeStep(report, "Detalles de la Cuenta", () -> {
				assertVisibleText(page, "Cuenta creada");
				assertVisibleText(page, "Estado activo");
				assertVisibleText(page, "Idioma seleccionado");
			});

			boolean tusNegociosOk = detallesCuentaOk && executeStep(report, "Tus Negocios", () -> {
				assertVisibleText(page, "Tus Negocios");
				assertVisibleText(page, "Agregar Negocio");
				assertVisibleText(page, "Tienes 2 de 3 negocios");
			});

			boolean termsOk = tusNegociosOk && executeStep(report, "T\u00e9rminos y Condiciones", () -> {
				Page legalPage = clickLegalLink(page, "T\u00e9rminos y Condiciones", "Terminos y Condiciones");
				assertVisibleTextAny(legalPage, "T\u00e9rminos y Condiciones", "Terminos y Condiciones");
				assertLegalContentVisible(legalPage);
				takeScreenshot(legalPage, config.screenshotsDir, "05-terminos-y-condiciones");
				termsUrl.set(legalPage.url());
				returnToApplicationTab(page, legalPage);
			});

			boolean privacyOk = termsOk && executeStep(report, "Pol\u00edtica de Privacidad", () -> {
				Page legalPage = clickLegalLink(page, "Pol\u00edtica de Privacidad", "Politica de Privacidad");
				assertVisibleTextAny(legalPage, "Pol\u00edtica de Privacidad", "Politica de Privacidad");
				assertLegalContentVisible(legalPage);
				takeScreenshot(legalPage, config.screenshotsDir, "06-politica-de-privacidad");
				privacyUrl.set(legalPage.url());
				returnToApplicationTab(page, legalPage);
			});

			if (!loginOk) {
				setFailed(report, "Mi Negocio menu", "Agregar Negocio modal", "Administrar Negocios view",
						"Informaci\u00f3n General", "Detalles de la Cuenta", "Tus Negocios", "T\u00e9rminos y Condiciones",
						"Pol\u00edtica de Privacidad");
			} else if (!miNegocioMenuOk) {
				setFailed(report, "Agregar Negocio modal", "Administrar Negocios view", "Informaci\u00f3n General",
						"Detalles de la Cuenta", "Tus Negocios", "T\u00e9rminos y Condiciones", "Pol\u00edtica de Privacidad");
			} else if (!agregarNegocioModalOk) {
				setFailed(report, "Administrar Negocios view", "Informaci\u00f3n General", "Detalles de la Cuenta",
						"Tus Negocios", "T\u00e9rminos y Condiciones", "Pol\u00edtica de Privacidad");
			} else if (!administrarNegociosOk) {
				setFailed(report, "Informaci\u00f3n General", "Detalles de la Cuenta", "Tus Negocios",
						"T\u00e9rminos y Condiciones", "Pol\u00edtica de Privacidad");
			} else if (!informacionGeneralOk) {
				setFailed(report, "Detalles de la Cuenta", "Tus Negocios", "T\u00e9rminos y Condiciones",
						"Pol\u00edtica de Privacidad");
			} else if (!detallesCuentaOk) {
				setFailed(report, "Tus Negocios", "T\u00e9rminos y Condiciones", "Pol\u00edtica de Privacidad");
			} else if (!tusNegociosOk) {
				setFailed(report, "T\u00e9rminos y Condiciones", "Pol\u00edtica de Privacidad");
			} else if (!termsOk) {
				setFailed(report, "Pol\u00edtica de Privacidad");
			}

			printFinalReport(report, termsUrl.get(), privacyUrl.get());
			Assert.assertTrue("One or more SaleADS workflow validations failed. See final report output.", allPassed(report));
		}
	}

	private static Session startSession(final Playwright playwright, final SaleadsRunConfig config) {
		if (config.cdpUrl != null && !config.cdpUrl.isBlank()) {
			final Browser browser = playwright.chromium().connectOverCDP(config.cdpUrl);
			BrowserContext context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
			Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
			page.bringToFront();
			waitForUi(page);
			return new Session(browser, context, page);
		}

		if (config.loginUrl == null || config.loginUrl.isBlank()) {
			throw new AssertionError(
					"No SaleADS page source was provided. Set -Dsaleads.login.url (or SALEADS_LOGIN_URL), "
							+ "or provide -Dsaleads.cdp.url to attach to an already-opened login page.");
		}

		final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(config.headless)
				.setSlowMo(config.slowMoMs);
		final Browser browser = playwright.chromium().launch(launchOptions);
		final BrowserContext context = browser.newContext();
		final Page page = context.newPage();
		page.navigate(config.loginUrl);
		waitForUi(page);
		return new Session(browser, context, page);
	}

	private static void stepLoginWithGoogle(final Page page, final String accountEmail) {
		Locator googleLoginButton = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName("Sign in with Google"));
		if (googleLoginButton.count() == 0) {
			googleLoginButton = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
					new Page.GetByRoleOptions().setName("Iniciar sesi\u00f3n con Google"));
		}
		if (googleLoginButton.count() == 0) {
			googleLoginButton = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
					new Page.GetByRoleOptions().setName("Iniciar sesion con Google"));
		}
		if (googleLoginButton.count() == 0) {
			googleLoginButton = page.locator("button:has-text(\"Google\"), [role='button']:has-text(\"Google\")").first();
		}

		assertThat(googleLoginButton).isVisible();

		Page popup = null;
		try {
			popup = page.waitForPopup(new WaitForPopupOptions().setTimeout(7_000), () -> {
				googleLoginButton.click();
			});
		} catch (TimeoutError ignored) {
			googleLoginButton.click();
		}

		if (popup != null) {
			waitForUi(popup);
			clickGoogleAccountIfVisible(popup, accountEmail);
		} else {
			waitForUi(page);
			clickGoogleAccountIfVisible(page, accountEmail);
		}

		waitForUi(page);
		assertSidebarVisible(page);
		assertVisibleText(page, "Negocio");
	}

	private static void clickGoogleAccountIfVisible(final Page page, final String accountEmail) {
		Locator accountLocator = page.getByText(accountEmail);
		if (accountLocator.count() > 0 && accountLocator.first().isVisible()) {
			accountLocator.first().click();
			waitForUi(page);
		}
	}

	private static void openMiNegocioMenu(final Page page) {
		assertSidebarVisible(page);
		clickByVisibleText(page, "Negocio");
		waitForUi(page);
		clickByVisibleText(page, "Mi Negocio");
		waitForUi(page);
	}

	private static void assertSidebarVisible(final Page page) {
		Locator sidebar = page.locator("aside, nav").first();
		assertThat(sidebar).isVisible();
	}

	private static void assertButtonVisibleAny(final Page page, final String... labels) {
		AssertionError lastError = null;
		for (String label : labels) {
			try {
				Locator button = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(label));
				assertThat(button.first()).isVisible();
				return;
			} catch (AssertionError error) {
				lastError = error;
			}
		}
		if (lastError != null) {
			throw lastError;
		}
		throw new AssertionError("No visible button matched the expected labels.");
	}

	private static void assertVisibleLabelOrPlaceholder(final Page page, final String labelOrPlaceholder) {
		Locator byLabel = page.getByLabel(labelOrPlaceholder);
		if (byLabel.count() > 0) {
			assertThat(byLabel.first()).isVisible();
			return;
		}
		Locator byPlaceholder = page.getByPlaceholder(labelOrPlaceholder);
		assertThat(byPlaceholder.first()).isVisible();
	}

	private static void fillIfVisible(final Page page, final String labelOrPlaceholder, final String value) {
		Locator field = page.getByLabel(labelOrPlaceholder);
		if (field.count() == 0) {
			field = page.getByPlaceholder(labelOrPlaceholder);
		}
		if (field.count() > 0 && field.first().isVisible()) {
			field.first().fill(value);
			waitForUi(page);
		}
	}

	private static void clickByVisibleText(final Page page, final String visibleText) {
		Locator locator = page.getByText(visibleText);
		assertThat(locator.first()).isVisible();
		locator.first().click();
		waitForUi(page);
	}

	private static Page clickLegalLink(final Page appPage, final String... linkTexts) {
		Locator link = null;
		for (String linkText : linkTexts) {
			Locator candidate = appPage.getByText(linkText);
			if (candidate.count() > 0 && candidate.first().isVisible()) {
				link = candidate.first();
				break;
			}
		}
		if (link == null) {
			throw new AssertionError("Could not find any legal link: " + String.join(", ", linkTexts));
		}
		final Locator selectedLink = link;

		try {
			Page popup = appPage.waitForPopup(new WaitForPopupOptions().setTimeout(7_000), () -> {
				selectedLink.click();
			});
			waitForUi(popup);
			return popup;
		} catch (TimeoutError ignored) {
			selectedLink.click();
			waitForUi(appPage);
			return appPage;
		}
	}

	private static void returnToApplicationTab(final Page appPage, final Page legalPage) {
		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}
	}

	private static void assertLegalContentVisible(final Page page) {
		Locator content = page.locator("main, article, section").first();
		assertThat(content).isVisible();
	}

	private static void assertContainsEmail(final Page page) {
		Locator emailPattern = page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/");
		assertThat(emailPattern.first()).isVisible();
	}

	private static void assertHasAnyVisibleText(final Page page, final String text) {
		Locator locator = page.getByText(text);
		assertThat(locator.first()).isVisible();
	}

	private static void assertVisibleText(final Page page, final String text) {
		assertHasAnyVisibleText(page, text);
	}

	private static void assertVisibleTextAny(final Page page, final String... texts) {
		AssertionError lastError = null;
		for (String text : texts) {
			try {
				assertVisibleText(page, text);
				return;
			} catch (AssertionError error) {
				lastError = error;
			}
		}
		if (lastError != null) {
			throw lastError;
		}
		throw new AssertionError("No visible text matched any provided value.");
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (TimeoutError ignored) {
			// Some SPA transitions do not trigger a full load; keep going with short stabilization wait.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(3_000));
		} catch (TimeoutError ignored) {
			// Background network calls are common in SPA apps.
		}
		page.waitForTimeout(500);
	}

	private static boolean executeStep(final Map<String, Boolean> report, final String key, final CheckedRunnable action) {
		try {
			action.run();
			report.put(key, true);
			return true;
		} catch (Throwable throwable) {
			report.put(key, false);
			System.err.println("[SALEADS][FAIL] " + key + " -> " + throwable.getMessage());
			return false;
		}
	}

	private static void setFailed(final Map<String, Boolean> report, final String... keys) {
		for (String key : keys) {
			report.putIfAbsent(key, false);
		}
	}

	private static boolean allPassed(final Map<String, Boolean> report) {
		return report.values().stream().allMatch(Boolean.TRUE::equals);
	}

	private static void takeScreenshot(final Page page, final Path screenshotsDir, final String name) {
		final String fileName = fileName(name);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotsDir.resolve(fileName)).setFullPage(false));
	}

	private static void takeFullScreenshot(final Page page, final Path screenshotsDir, final String name) {
		final String fileName = fileName(name);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotsDir.resolve(fileName)).setFullPage(true));
	}

	private static String fileName(final String name) {
		return LocalDateTime.now().format(STAMP) + "-" + name + ".png";
	}

	private static void printFinalReport(final Map<String, Boolean> report, final String termsUrl, final String privacyUrl) {
		System.out.println("========== SaleADS Mi Negocio Final Report ==========");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		System.out.println("Terminos y Condiciones final URL: " + termsUrl);
		System.out.println("Politica de Privacidad final URL: " + privacyUrl);
		System.out.println("=====================================================");
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class Session {
		@SuppressWarnings("unused")
		private final Browser browser;
		@SuppressWarnings("unused")
		private final BrowserContext context;
		private final Page page;

		private Session(final Browser browser, final BrowserContext context, final Page page) {
			this.browser = browser;
			this.context = context;
			this.page = page;
		}
	}

	private static final class SaleadsRunConfig {
		private final String loginUrl;
		private final String cdpUrl;
		private final String googleAccountEmail;
		private final boolean headless;
		private final int slowMoMs;
		private final Path screenshotsDir;

		private SaleadsRunConfig(final String loginUrl, final String cdpUrl, final String googleAccountEmail, final boolean headless,
				final int slowMoMs, final Path screenshotsDir) {
			this.loginUrl = loginUrl;
			this.cdpUrl = cdpUrl;
			this.googleAccountEmail = googleAccountEmail;
			this.headless = headless;
			this.slowMoMs = slowMoMs;
			this.screenshotsDir = screenshotsDir;
		}

		private static SaleadsRunConfig fromSystem() {
			final String loginUrl = propertyOrEnv("saleads.login.url", "SALEADS_LOGIN_URL");
			final String cdpUrl = propertyOrEnv("saleads.cdp.url", "SALEADS_CDP_URL");
			final String googleEmail = propertyOrEnv("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT");
			final String headlessRaw = propertyOrEnv("saleads.headless", "SALEADS_HEADLESS");
			final String slowMoRaw = propertyOrEnv("saleads.slow.mo.ms", "SALEADS_SLOW_MO_MS");
			final String screenshotsDirRaw = propertyOrEnv("saleads.screenshots.dir", "SALEADS_SCREENSHOTS_DIR");

			final boolean headless = headlessRaw == null ? true : Boolean.parseBoolean(headlessRaw);
			final int slowMoMs = slowMoRaw == null ? 0 : Integer.parseInt(slowMoRaw);
			final Path screenshotsDir = Paths.get(
					screenshotsDirRaw == null ? "target/saleads-minegocio-screenshots" : screenshotsDirRaw);

			return new SaleadsRunConfig(loginUrl, cdpUrl,
					googleEmail == null || googleEmail.isBlank() ? DEFAULT_GOOGLE_ACCOUNT : googleEmail, headless, slowMoMs,
					screenshotsDir);
		}

		private static String propertyOrEnv(final String propertyName, final String envName) {
			final String propertyValue = System.getProperty(propertyName);
			if (propertyValue != null && !propertyValue.isBlank()) {
				return propertyValue;
			}
			final String envValue = System.getenv(envName);
			if (envValue != null && !envValue.isBlank()) {
				return envValue;
			}
			return null;
		}
	}
}
