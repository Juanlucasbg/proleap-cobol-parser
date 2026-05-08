package io.proleap.cobol.e2e.saleads;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.GetByRoleOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

public class SaleadsMiNegocioWorkflowE2ETest {

	private static final String LOGIN_RESULT = "Login";
	private static final String MENU_RESULT = "Mi Negocio menu";
	private static final String MODAL_RESULT = "Agregar Negocio modal";
	private static final String ADMIN_VIEW_RESULT = "Administrar Negocios view";
	private static final String INFO_GENERAL_RESULT = "Información General";
	private static final String DETALLES_CUENTA_RESULT = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS_RESULT = "Tus Negocios";
	private static final String TERMINOS_RESULT = "Términos y Condiciones";
	private static final String POLITICA_RESULT = "Política de Privacidad";

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String e2eEnabled = valueOrDefault(System.getenv("SALEADS_E2E_ENABLED"), System.getProperty("saleads.e2e.enabled"));
		Assume.assumeTrue("Enable this E2E with SALEADS_E2E_ENABLED=true or -Dsaleads.e2e.enabled=true.", isTruthy(e2eEnabled));

		final String loginUrl = firstNonBlank(
				System.getenv("SALEADS_LOGIN_URL"),
				System.getProperty("saleads.loginUrl"),
				System.getenv("SALEADS_BASE_URL"),
				System.getProperty("saleads.baseUrl"));
		Assume.assumeTrue("Provide SALEADS_LOGIN_URL (or SALEADS_BASE_URL) to run this test.", loginUrl != null && !loginUrl.isBlank());

		final Path screenshotDirectory = createScreenshotDirectory();
		final List<String> failures = new ArrayList<>();
		final Map<String, Boolean> report = initializeReport();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final boolean headless = !"false".equalsIgnoreCase(firstNonBlank(System.getenv("SALEADS_HEADLESS"), System.getProperty("saleads.headless"), "true"));
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext();
			final Page page = context.newPage();
			page.setDefaultTimeout(20000);

			page.navigate(loginUrl);
			waitForUi(page);

			runStep(LOGIN_RESULT, report, failures, () -> {
				final Locator googleLogin = requireVisible("Google login button/link",
						() -> page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName(Pattern.compile("(?i).*google.*"))),
						() -> page.getByRole(AriaRole.LINK, new GetByRoleOptions().setName(Pattern.compile("(?i).*google.*"))),
						() -> page.getByText(Pattern.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google)")));

				final Page popup = clickAndResolveDestination(page, googleLogin);
				if (popup != null && popup != page) {
					selectGoogleAccountIfVisible(popup);
					waitForUi(popup);
				}
				selectGoogleAccountIfVisible(page);

				waitForUi(page);
				assertVisibleText(page, Pattern.compile("(?i)(negocio|mi\\s+negocio)"), "Left sidebar navigation should be visible after login.");
				takeScreenshot(page, screenshotDirectory, "01-dashboard-loaded", false);
			});

			runStep(MENU_RESULT, report, failures, () -> {
				ensureMiNegocioExpanded(page);
				assertVisibleText(page, Pattern.compile("(?i)agregar\\s+negocio"), "'Agregar Negocio' should be visible in expanded menu.");
				assertVisibleText(page, Pattern.compile("(?i)administrar\\s+negocios"), "'Administrar Negocios' should be visible in expanded menu.");
				takeScreenshot(page, screenshotDirectory, "02-mi-negocio-expanded", false);
			});

			runStep(MODAL_RESULT, report, failures, () -> {
				final Locator agregarNegocio = requireVisible("Agregar Negocio menu option",
						() -> page.getByRole(AriaRole.LINK, new GetByRoleOptions().setName(Pattern.compile("(?i)^agregar\\s+negocio$"))),
						() -> page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName(Pattern.compile("(?i)^agregar\\s+negocio$"))),
						() -> page.getByText(Pattern.compile("(?i)^agregar\\s+negocio$")));
				clickAndWait(agregarNegocio, page);

				assertVisibleText(page, Pattern.compile("(?i)crear\\s+nuevo\\s+negocio"), "Modal title 'Crear Nuevo Negocio' should be visible.");
				assertAnyVisible("Nombre del Negocio input",
						() -> page.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
						() -> page.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
						() -> page.locator("input[type='text']"));
				assertVisibleText(page, Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"), "Business limit text should be visible.");
				requireVisible("Cancelar button",
						() -> page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName(Pattern.compile("(?i)^cancelar$"))));
				requireVisible("Crear Negocio button",
						() -> page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName(Pattern.compile("(?i)^crear\\s+negocio$"))));

				takeScreenshot(page, screenshotDirectory, "03-agregar-negocio-modal", false);

				final Locator nombreNegocio = requireVisible("Nombre del Negocio input field",
						() -> page.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
						() -> page.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
						() -> page.locator("input[type='text']"));
				nombreNegocio.click();
				waitForUi(page);
				nombreNegocio.fill("Negocio Prueba Automatizacion");
				waitForUi(page);

				final Locator cancelar = requireVisible("Cancelar modal button",
						() -> page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName(Pattern.compile("(?i)^cancelar$"))));
				clickAndWait(cancelar, page);
			});

			runStep(ADMIN_VIEW_RESULT, report, failures, () -> {
				ensureMiNegocioExpanded(page);
				final Locator administrarNegocios = requireVisible("Administrar Negocios menu option",
						() -> page.getByRole(AriaRole.LINK, new GetByRoleOptions().setName(Pattern.compile("(?i)^administrar\\s+negocios$"))),
						() -> page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName(Pattern.compile("(?i)^administrar\\s+negocios$"))),
						() -> page.getByText(Pattern.compile("(?i)^administrar\\s+negocios$")));
				clickAndWait(administrarNegocios, page);

				assertVisibleText(page, Pattern.compile("(?i)informaci[oó]n\\s+general"), "'Informacion General' section should be visible.");
				assertVisibleText(page, Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta"), "'Detalles de la Cuenta' section should be visible.");
				assertVisibleText(page, Pattern.compile("(?i)tus\\s+negocios"), "'Tus Negocios' section should be visible.");
				assertVisibleText(page, Pattern.compile("(?i)secci[oó]n\\s+legal"), "'Seccion Legal' section should be visible.");
				takeScreenshot(page, screenshotDirectory, "04-administrar-negocios-view", true);
			});

			runStep(INFO_GENERAL_RESULT, report, failures, () -> {
				final Locator emailLocator = assertVisibleText(page, Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+"), "User email should be visible.");
				final String accountText = safeInnerText(emailLocator);
				Assert.assertTrue("User email text should not be empty.", accountText != null && !accountText.trim().isEmpty());

				final String bodyText = safeInnerText(page.locator("body"));
				Assert.assertTrue("User name should be visible in account area.",
						Pattern.compile("(?i)(juan|lucas|barbier|garzon)").matcher(bodyText).find()
								|| Pattern.compile("(?m)^[A-Z][a-z]+\\s+[A-Z][a-z]+$").matcher(bodyText).find());
				assertVisibleText(page, Pattern.compile("(?i)business\\s+plan"), "Text 'BUSINESS PLAN' should be visible.");
				requireVisible("Cambiar Plan button",
						() -> page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName(Pattern.compile("(?i)cambiar\\s+plan"))));
			});

			runStep(DETALLES_CUENTA_RESULT, report, failures, () -> {
				assertVisibleText(page, Pattern.compile("(?i)cuenta\\s+creada"), "'Cuenta creada' should be visible.");
				assertVisibleText(page, Pattern.compile("(?i)estado\\s+activo"), "'Estado activo' should be visible.");
				assertVisibleText(page, Pattern.compile("(?i)idioma\\s+seleccionado"), "'Idioma seleccionado' should be visible.");
			});

			runStep(TUS_NEGOCIOS_RESULT, report, failures, () -> {
				assertVisibleText(page, Pattern.compile("(?i)tus\\s+negocios"), "Business list section title should be visible.");
				requireVisible("Agregar Negocio button in business list",
						() -> page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName(Pattern.compile("(?i)^agregar\\s+negocio$"))),
						() -> page.getByRole(AriaRole.LINK, new GetByRoleOptions().setName(Pattern.compile("(?i)^agregar\\s+negocio$"))));
				assertVisibleText(page, Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"), "Business usage text should be visible in 'Tus Negocios'.");
			});

			runStep(TERMINOS_RESULT, report, failures, () -> {
				final String appUrlBefore = page.url();
				final Locator terminosLink = requireVisible("Términos y Condiciones link",
						() -> page.getByRole(AriaRole.LINK, new GetByRoleOptions().setName(Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"))),
						() -> page.getByText(Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones")));
				final Page terminosPage = clickAndResolveDestination(page, terminosLink);
				waitForUi(terminosPage);

				assertVisibleText(terminosPage, Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"), "Terms page heading should be visible.");
				assertLegalContentVisible(terminosPage, "Terms legal content should be visible.");
				takeScreenshot(terminosPage, screenshotDirectory, "05-terminos-condiciones", true);
				legalUrls.put(TERMINOS_RESULT, terminosPage.url());

				restoreApplicationTab(page, terminosPage, appUrlBefore);
			});

			runStep(POLITICA_RESULT, report, failures, () -> {
				final String appUrlBefore = page.url();
				final Locator politicaLink = requireVisible("Politica de Privacidad link",
						() -> page.getByRole(AriaRole.LINK, new GetByRoleOptions().setName(Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"))),
						() -> page.getByText(Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad")));
				final Page politicaPage = clickAndResolveDestination(page, politicaLink);
				waitForUi(politicaPage);

				assertVisibleText(politicaPage, Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"), "Privacy page heading should be visible.");
				assertLegalContentVisible(politicaPage, "Privacy legal content should be visible.");
				takeScreenshot(politicaPage, screenshotDirectory, "06-politica-privacidad", true);
				legalUrls.put(POLITICA_RESULT, politicaPage.url());

				restoreApplicationTab(page, politicaPage, appUrlBefore);
			});

			browser.close();
		} finally {
			printFinalReport(report, legalUrls, screenshotDirectory);
		}

		if (!failures.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow failed:\n- " + String.join("\n- ", failures));
		}
	}

	private void runStep(final String stepName, final Map<String, Boolean> report, final List<String> failures, final Step step) {
		try {
			step.run();
			report.put(stepName, true);
		} catch (final Throwable throwable) {
			report.put(stepName, false);
			failures.add(stepName + ": " + throwable.getMessage());
		}
	}

	private void ensureMiNegocioExpanded(final Page page) {
		if (!isTextVisible(page, Pattern.compile("(?i)agregar\\s+negocio"), 1500)
				|| !isTextVisible(page, Pattern.compile("(?i)administrar\\s+negocios"), 1500)) {
			final Locator miNegocio = requireVisible("Mi Negocio menu",
					() -> page.getByRole(AriaRole.LINK, new GetByRoleOptions().setName(Pattern.compile("(?i)^mi\\s+negocio$"))),
					() -> page.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName(Pattern.compile("(?i)^mi\\s+negocio$"))),
					() -> page.getByText(Pattern.compile("(?i)^mi\\s+negocio$")));
			clickAndWait(miNegocio, page);
		}
	}

	private void clickAndWait(final Locator locator, final Page page) {
		locator.click();
		waitForUi(page);
	}

	private Page clickAndResolveDestination(final Page appPage, final Locator clickable) {
		final BrowserContext context = appPage.context();
		final AtomicBoolean clicked = new AtomicBoolean(false);

		try {
			final Page popupPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(5000), () -> {
				clickable.click();
				clicked.set(true);
			});
			waitForUi(popupPage);
			return popupPage;
		} catch (final PlaywrightException popupNotOpened) {
			if (!clicked.get()) {
				clickable.click();
			}
			waitForUi(appPage);
			return appPage;
		}
	}

	private void restoreApplicationTab(final Page appPage, final Page legalPage, final String appUrlBefore) {
		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
			return;
		}

		if (!appPage.url().equals(appUrlBefore)) {
			appPage.goBack();
			waitForUi(appPage);
		}
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		final Locator accountByEmail = page.getByText(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE)).first();
		if (isVisible(accountByEmail, 4000)) {
			accountByEmail.click();
			waitForUi(page);
		}
	}

	private Locator requireVisible(final String description, final Supplier<Locator>... candidates) {
		for (final Supplier<Locator> candidate : candidates) {
			final Locator locator = candidate.get().first();
			if (isVisible(locator, 5000)) {
				return locator;
			}
		}

		throw new AssertionError("Could not find visible element: " + description);
	}

	private void assertAnyVisible(final String description, final Supplier<Locator>... candidates) {
		requireVisible(description, candidates);
	}

	private Locator assertVisibleText(final Page page, final Pattern pattern, final String message) {
		final Locator locator = page.getByText(pattern).first();
		if (!isVisible(locator, 15000)) {
			throw new AssertionError(message + " Pattern: " + pattern.pattern());
		}
		return locator;
	}

	private boolean isTextVisible(final Page page, final Pattern pattern, final int timeoutMillis) {
		return isVisible(page.getByText(pattern).first(), timeoutMillis);
	}

	private boolean isVisible(final Locator locator, final int timeoutMillis) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMillis));
			return locator.isVisible();
		} catch (final PlaywrightException exception) {
			return false;
		}
	}

	private void assertLegalContentVisible(final Page page, final String failureMessage) {
		final String bodyText = safeInnerText(page.locator("body"));
		Assert.assertTrue(failureMessage, bodyText != null && bodyText.trim().length() > 120);
	}

	private String safeInnerText(final Locator locator) {
		try {
			return locator.innerText();
		} catch (final PlaywrightException ignored) {
			return "";
		}
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(800);
	}

	private Path createScreenshotDirectory() throws Exception {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path directory = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(directory);
		return directory;
	}

	private void takeScreenshot(final Page page, final Path directory, final String name, final boolean fullPage) {
		final Path output = directory.resolve(name + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(output).setFullPage(fullPage));
	}

	private Map<String, Boolean> initializeReport() {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		for (final String key : Arrays.asList(
				LOGIN_RESULT,
				MENU_RESULT,
				MODAL_RESULT,
				ADMIN_VIEW_RESULT,
				INFO_GENERAL_RESULT,
				DETALLES_CUENTA_RESULT,
				TUS_NEGOCIOS_RESULT,
				TERMINOS_RESULT,
				POLITICA_RESULT)) {
			report.put(key, false);
		}
		return report;
	}

	private void printFinalReport(final Map<String, Boolean> report, final Map<String, String> legalUrls, final Path screenshotDirectory) {
		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
			System.out.println(legalUrl.getKey() + " URL: " + legalUrl.getValue());
		}
		System.out.println("Evidence screenshots: " + screenshotDirectory.toAbsolutePath());
	}

	private String firstNonBlank(final String... candidates) {
		for (final String candidate : candidates) {
			if (candidate != null && !candidate.isBlank()) {
				return candidate;
			}
		}
		return null;
	}

	private String valueOrDefault(final String primary, final String secondary) {
		if (primary != null && !primary.isBlank()) {
			return primary;
		}
		return secondary;
	}

	private boolean isTruthy(final String value) {
		return value != null && ("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()) || "yes".equalsIgnoreCase(value.trim()));
	}

	@FunctionalInterface
	private interface Step {
		void run() throws Exception;
	}
}
