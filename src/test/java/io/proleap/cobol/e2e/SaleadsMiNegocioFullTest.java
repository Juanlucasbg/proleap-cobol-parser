package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

/**
 * End-to-end workflow test for SaleADS Mi Negocio module.
 *
 * <p>
 * Runtime configuration:
 * </p>
 * <ul>
 * <li>-Dsaleads.loginUrl=https://&lt;environment-login-url&gt; (required)</li>
 * <li>-Dsaleads.headless=true|false (optional, default true)</li>
 * <li>-Dsaleads.timeoutMs=15000 (optional)</li>
 * <li>-Dsaleads.googleAccount=juanlucasbarbiergarzon@gmail.com (optional)</li>
 * <li>-Dsaleads.screenshotsDir=target/saleads-mi-negocio (optional)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String DEFAULT_SCREENSHOT_DIR = "target/saleads-mi-negocio";

	private final Map<String, StepResult> report = new LinkedHashMap<>();

	private String termsAndConditionsUrl = "N/A";
	private String privacyPolicyUrl = "N/A";
	private Path screenshotDir;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = firstNonBlank(System.getProperty("saleads.loginUrl"), System.getenv("SALEADS_LOGIN_URL"),
				System.getenv("SALEADS_BASE_URL"));
		Assume.assumeTrue(
				"Set -Dsaleads.loginUrl (or SALEADS_LOGIN_URL/SALEADS_BASE_URL) to run this environment-agnostic test.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean
				.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "true"));
		final double timeoutMs = Double.parseDouble(
				firstNonBlank(System.getProperty("saleads.timeoutMs"), System.getenv("SALEADS_TIMEOUT_MS"), "15000"));
		final String googleAccountEmail = firstNonBlank(System.getProperty("saleads.googleAccount"),
				System.getenv("SALEADS_GOOGLE_ACCOUNT"), DEFAULT_GOOGLE_ACCOUNT);

		screenshotDir = Paths
				.get(firstNonBlank(System.getProperty("saleads.screenshotsDir"), System.getenv("SALEADS_SCREENSHOTS_DIR"),
						DEFAULT_SCREENSHOT_DIR))
				.toAbsolutePath();
		Files.createDirectories(screenshotDir);

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(150));
			final BrowserContext context = browser.newContext(
					new Browser.NewContextOptions().setViewportSize(1600, 900));
			final Page page = context.newPage();
			page.setDefaultTimeout(timeoutMs);
			page.setDefaultNavigationTimeout(timeoutMs);

			page.navigate(loginUrl);
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);

			runStep("Login", () -> executeLoginStep(context, page, googleAccountEmail));
			runStep("Mi Negocio menu", () -> executeMiNegocioMenuStep(page));
			runStep("Agregar Negocio modal", () -> executeAgregarNegocioModalStep(page));
			runStep("Administrar Negocios view", () -> executeAdministrarNegociosStep(page));
			runStep("Información General", () -> executeInformacionGeneralStep(page));
			runStep("Detalles de la Cuenta", () -> executeDetallesCuentaStep(page));
			runStep("Tus Negocios", () -> executeTusNegociosStep(page));
			runStep("Términos y Condiciones", () -> executeTerminosStep(context, page));
			runStep("Política de Privacidad", () -> executePrivacidadStep(context, page));
		} finally {
			printFinalReport();
		}

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().pass) {
				failedSteps.add(entry.getKey() + " -> " + String.join(" | ", entry.getValue().errors));
			}
		}
		assertTrue("Failed validations: " + String.join(" || ", failedSteps), failedSteps.isEmpty());
	}

	private void executeLoginStep(final BrowserContext context, final Page appPage, final String accountEmail) {
		final Locator loginButton = firstVisible(appPage.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(google|iniciar|sign in|continuar)"))),
				appPage.locator("button:has-text(\"Google\"), a:has-text(\"Google\")"));
		clickAndWaitForUi(appPage, loginButton);

		selectGoogleAccountIfPrompted(context, appPage, accountEmail);

		requireVisible(appPage.locator("main, [role='main']").first(), "Main application interface appears");
		requireVisible(appPage.locator("aside, nav").first(), "Left sidebar navigation is visible");
		takeScreenshot(appPage, "01-dashboard-loaded.png", false);
	}

	private void executeMiNegocioMenuStep(final Page page) {
		requireVisible(page.locator("aside, nav").first(), "Left sidebar navigation is present");
		clickAndWaitForUi(page, firstVisible(page.getByText(Pattern.compile("(?i)^Negocio$"))));
		clickAndWaitForUi(page, firstVisible(page.getByText(Pattern.compile("(?i)^Mi Negocio$"))));

		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)^Agregar Negocio$"))),
				"Agregar Negocio is visible");
		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)^Administrar Negocios$"))),
				"Administrar Negocios is visible");
		takeScreenshot(page, "02-mi-negocio-expanded.png", false);
	}

	private void executeAgregarNegocioModalStep(final Page page) {
		clickAndWaitForUi(page, firstVisible(page.getByText(Pattern.compile("(?i)^Agregar Negocio$"))));
		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)^Crear Nuevo Negocio$"))),
				"Modal title 'Crear Nuevo Negocio' is visible");
		requireVisible(firstVisible(page.getByLabel(Pattern.compile("(?i)^Nombre del Negocio$")),
				page.getByPlaceholder(Pattern.compile("(?i)Nombre del Negocio"))), "Input field 'Nombre del Negocio' exists");
		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)Tienes\\s*2\\s*de\\s*3\\s*negocios"))),
				"Quota text 'Tienes 2 de 3 negocios' is visible");
		requireVisible(firstVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar"))),
				"Button 'Cancelar' is present");
		requireVisible(firstVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio"))),
				"Button 'Crear Negocio' is present");
		takeScreenshot(page, "03-agregar-negocio-modal.png", false);

		final Locator businessNameField = firstVisible(page.getByLabel(Pattern.compile("(?i)^Nombre del Negocio$")),
				page.getByPlaceholder(Pattern.compile("(?i)Nombre del Negocio")));
		businessNameField.click();
		page.waitForTimeout(500);
		businessNameField.fill("Negocio Prueba Automatizacion");
		page.waitForTimeout(500);
		clickAndWaitForUi(page, firstVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar"))));
	}

	private void executeAdministrarNegociosStep(final Page page) {
		if (!isVisible(page.getByText(Pattern.compile("(?i)^Administrar Negocios$")).first())) {
			clickAndWaitForUi(page, firstVisible(page.getByText(Pattern.compile("(?i)^Mi Negocio$"))));
		}
		clickAndWaitForUi(page, firstVisible(page.getByText(Pattern.compile("(?i)^Administrar Negocios$"))));

		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)^Informaci[oó]n General$"))),
				"Section 'Información General' exists");
		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)^Detalles de la Cuenta$"))),
				"Section 'Detalles de la Cuenta' exists");
		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)^Tus Negocios$"))),
				"Section 'Tus Negocios' exists");
		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)(Secci[oó]n Legal|T[eé]rminos y Condiciones|Pol[ií]tica de Privacidad)"))),
				"Section 'Sección Legal' exists");
		takeScreenshot(page, "04-administrar-negocios-full.png", true);
	}

	private void executeInformacionGeneralStep(final Page page) {
		final Locator infoSection = firstVisible(page.locator("section, div")
				.filter(new Locator.FilterOptions().setHasText(Pattern.compile("(?i)Informaci[oó]n General"))));
		requireVisible(infoSection, "Información General container is visible");
		requireVisible(firstVisible(infoSection.locator("p, span, h1, h2, h3, h4, strong")
				.filter(new Locator.FilterOptions().setHasNotText(Pattern.compile("(?i)Informaci[oó]n General|BUSINESS PLAN|Cambiar Plan|@")))),
				page.locator("header, [class*='profile'], [class*='user']").locator("h1, h2, h3, span, strong")
						.filter(new Locator.FilterOptions().setHasNotText(Pattern.compile("(?i)@")))),
				"User name is visible");
		requireVisible(firstVisible(page.locator("text=/@/")), "User email is visible");
		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)BUSINESS PLAN"))), "Text 'BUSINESS PLAN' is visible");
		requireVisible(firstVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cambiar Plan")))),
				"Button 'Cambiar Plan' is visible");
	}

	private void executeDetallesCuentaStep(final Page page) {
		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)Cuenta creada"))), "'Cuenta creada' is visible");
		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)Estado activo|Activo"))), "'Estado activo' is visible");
		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)Idioma seleccionado|Idioma"))),
				"'Idioma seleccionado' is visible");
	}

	private void executeTusNegociosStep(final Page page) {
		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)^Tus Negocios$"))), "Business list section is visible");
		requireVisible(firstVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Agregar Negocio$")))),
				"Button 'Agregar Negocio' exists");
		requireVisible(firstVisible(page.getByText(Pattern.compile("(?i)Tienes\\s*2\\s*de\\s*3\\s*negocios"))),
				"Text 'Tienes 2 de 3 negocios' is visible");
	}

	private void executeTerminosStep(final BrowserContext context, final Page appPage) {
		termsAndConditionsUrl = validateLegalLink(context, appPage, Pattern.compile("(?i)T[eé]rminos y Condiciones"),
				Pattern.compile("(?i)T[eé]rminos y Condiciones"), "05-terminos-y-condiciones.png");
	}

	private void executePrivacidadStep(final BrowserContext context, final Page appPage) {
		privacyPolicyUrl = validateLegalLink(context, appPage, Pattern.compile("(?i)Pol[ií]tica de Privacidad"),
				Pattern.compile("(?i)Pol[ií]tica de Privacidad"), "06-politica-de-privacidad.png");
	}

	private String validateLegalLink(final BrowserContext context, final Page appPage, final Pattern linkPattern,
			final Pattern headingPattern, final String screenshotName) {
		final Locator link = firstVisible(appPage.getByText(linkPattern),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)));
		final String appUrlBeforeClick = appPage.url();

		Page legalPage = null;
		boolean openedNewTab = false;
		try {
			legalPage = context.waitForPage(() -> link.click(), new BrowserContext.WaitForPageOptions().setTimeout(5000));
			openedNewTab = true;
			legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final TimeoutError timeout) {
			clickAndWaitForUi(appPage, link);
			legalPage = appPage;
		}

		requireVisible(firstVisible(legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
				legalPage.getByText(headingPattern)), "Legal page heading is visible: " + headingPattern.pattern());
		requireVisible(legalPage.locator("p, li").first(), "Legal content text is visible");
		takeScreenshot(legalPage, screenshotName, false);
		final String legalUrl = legalPage.url();

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} else if (!appPage.url().equals(appUrlBeforeClick)) {
			appPage.navigate(appUrlBeforeClick);
			appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		}

		return legalUrl;
	}

	private void selectGoogleAccountIfPrompted(final BrowserContext context, final Page appPage, final String accountEmail) {
		appPage.waitForTimeout(1500);
		Page googlePage = null;
		for (final Page candidate : context.pages()) {
			if (candidate != appPage && isLikelyGoogleAuthPage(candidate)) {
				googlePage = candidate;
				break;
			}
		}
		try {
			if (googlePage == null) {
				googlePage = context.waitForPage(() -> appPage.waitForTimeout(3000),
						new BrowserContext.WaitForPageOptions().setTimeout(4000));
			}
			handleGoogleAccountSelection(googlePage, accountEmail);
			googlePage.waitForClose(new Page.WaitForCloseOptions().setTimeout(20000));
		} catch (final TimeoutError ignored) {
			// Google sign-in may happen in same tab or auto-login. Continue with app page checks.
			handleGoogleAccountSelection(appPage, accountEmail);
		}
	}

	private void handleGoogleAccountSelection(final Page page, final String accountEmail) {
		final Locator accountOption = page.getByText(accountEmail);
		if (isVisible(accountOption.first())) {
			clickAndWaitForUi(page, accountOption.first());
		}
	}

	private boolean isLikelyGoogleAuthPage(final Page page) {
		final String url = page.url();
		return url != null && url.contains("accounts.google.com");
	}

	private void clickAndWaitForUi(final Page page, final Locator locator) {
		locator.waitFor(new Locator.WaitForOptions().setState(Locator.WaitForOptions.State.VISIBLE));
		locator.click();
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(750);
	}

	private void requireVisible(final Locator locator, final String validationMessage) {
		assertTrue(validationMessage, isVisible(locator));
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.isVisible();
		} catch (final RuntimeException e) {
			return false;
		}
	}

	private Locator firstVisible(final Locator... candidates) {
		for (final Locator candidate : candidates) {
			final int count = candidate.count() > 10 ? 10 : (int) candidate.count();
			for (int i = 0; i < count; i++) {
				final Locator current = candidate.nth(i);
				if (isVisible(current)) {
					return current;
				}
			}
		}
		return candidates[0].first();
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotDir.resolve(fileName)).setFullPage(fullPage));
	}

	private void runStep(final String stepName, final StepAction action) {
		final List<String> errors = new ArrayList<>();
		try {
			action.run();
		} catch (final AssertionError assertionError) {
			errors.add(assertionError.getMessage());
		} catch (final Exception exception) {
			errors.add("Unexpected error: " + exception.getMessage());
		}
		report.put(stepName, new StepResult(errors.isEmpty(), errors));
	}

	private void printFinalReport() {
		System.out.println();
		System.out.println("=== SaleADS Mi Negocio Workflow Report ===");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final String status = entry.getValue().pass ? "PASS" : "FAIL";
			final String details = entry.getValue().errors.isEmpty() ? "" : " -> " + String.join(" | ", entry.getValue().errors);
			System.out.println(entry.getKey() + ": " + status + details);
		}
		System.out.println("Términos y Condiciones URL: " + termsAndConditionsUrl);
		System.out.println("Política de Privacidad URL: " + privacyPolicyUrl);
		System.out.println("Screenshots directory: " + screenshotDir);
		System.out.println("=========================================");
		System.out.println();
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean pass;
		private final List<String> errors;

		private StepResult(final boolean pass, final List<String> errors) {
			this.pass = pass;
			this.errors = errors;
		}
	}
}
