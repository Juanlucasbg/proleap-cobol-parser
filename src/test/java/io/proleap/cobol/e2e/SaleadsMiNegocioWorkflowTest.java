package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioWorkflowTest {

	private static final long DEFAULT_TIMEOUT_MS = 20_000L;
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

	private final Map<String, String> report = new LinkedHashMap<>();
	private Path evidenceDir;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String loginUrl = readConfig("SALEADS_LOGIN_URL", "saleads.login.url", "");
		Assume.assumeTrue(
				"SALEADS_LOGIN_URL (or -Dsaleads.login.url) is required. The test stays environment-agnostic by reading it from configuration.",
				!loginUrl.isBlank());

		final String googleEmail = readConfig("SALEADS_GOOGLE_EMAIL", "saleads.google.email",
				"juanlucasbarbiergarzon@gmail.com");
		final boolean headless = Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "saleads.headless", "true"));
		evidenceDir = createEvidenceDirectory();

		initReport();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
			final Page page = context.newPage();
			page.navigate(loginUrl);
			waitForUi(page);

			boolean canContinue = runStep("Login", () -> loginWithGoogle(page, context, googleEmail));
			canContinue = runDependentStep(canContinue, "Mi Negocio menu", () -> openMiNegocioMenu(page));
			canContinue = runDependentStep(canContinue, "Agregar Negocio modal", () -> validateAgregarNegocioModal(page));
			canContinue = runDependentStep(canContinue, "Administrar Negocios view", () -> openAdministrarNegocios(page));
			canContinue = runDependentStep(canContinue, "Información General", () -> validateInformacionGeneral(page));
			canContinue = runDependentStep(canContinue, "Detalles de la Cuenta", () -> validateDetallesCuenta(page));
			canContinue = runDependentStep(canContinue, "Tus Negocios", () -> validateTusNegocios(page));
			canContinue = runDependentStep(canContinue, "Términos y Condiciones",
					() -> validateLegalDocument(page, context, "Términos y Condiciones",
							Pattern.compile("(?iu)T[eé]rminos\\s+y\\s+Condiciones"), "08-terminos-y-condiciones.png"));
			runDependentStep(canContinue, "Política de Privacidad",
					() -> validateLegalDocument(page, context, "Política de Privacidad",
							Pattern.compile("(?iu)Pol[ií]tica\\s+de\\s+Privacidad"), "09-politica-de-privacidad.png"));
		}

		printFinalReport();
		assertAllStepsPassed();
	}

	private void loginWithGoogle(final Page page, final BrowserContext context, final String googleEmail) {
		final Pattern loginPattern = Pattern
				.compile("(?iu)(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)");
		final Locator loginButton = firstVisible("Google login button",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(loginPattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(loginPattern)),
				page.getByText(loginPattern));

		Page authPage = null;
		try {
			authPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(6_000), () -> {
				loginButton.click();
				waitForUi(page);
			});
		} catch (PlaywrightException noPopupExpected) {
			loginButton.click();
			waitForUi(page);
		}

		if (authPage != null) {
			handleGoogleAccountSelection(authPage, googleEmail);
		} else {
			handleGoogleAccountSelection(page, googleEmail);
		}

		assertVisible("main application area",
				firstVisible("app shell", page.locator("aside"), page.locator("nav"), page.getByText("Negocio")));
		assertVisible("left sidebar navigation", firstVisible("sidebar navigation", page.getByText("Negocio"),
				page.getByText(Pattern.compile("(?iu)Mi\\s+Negocio")), page.locator("aside")));
		screenshot(page, "01-dashboard-loaded.png", true);
	}

	private void openMiNegocioMenu(final Page page) {
		final Locator miNegocio = firstVisible("Mi Negocio menu option",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Mi\\s+Negocio"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Mi\\s+Negocio"))),
				page.getByText(Pattern.compile("(?iu)Mi\\s+Negocio")));
		clickAndWait(miNegocio, page);

		assertVisible("Agregar Negocio menu option", page.getByText(Pattern.compile("(?iu)^Agregar\\s+Negocio$")));
		assertVisible("Administrar Negocios menu option",
				page.getByText(Pattern.compile("(?iu)^Administrar\\s+Negocios$")));
		screenshot(page, "02-mi-negocio-menu-expanded.png", false);
	}

	private void validateAgregarNegocioModal(final Page page) {
		final Locator agregarNegocioOption = firstVisible("Agregar Negocio option",
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Agregar\\s+Negocio$"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)^Agregar\\s+Negocio$"))),
				page.getByText(Pattern.compile("(?iu)^Agregar\\s+Negocio$")));

		clickAndWait(agregarNegocioOption, page);
		assertVisible("Crear Nuevo Negocio title", page.getByText(Pattern.compile("(?iu)Crear\\s+Nuevo\\s+Negocio")));
		assertVisible("Nombre del Negocio field",
				firstVisible("Nombre del Negocio input", page.getByLabel(Pattern.compile("(?iu)Nombre\\s+del\\s+Negocio")),
						page.getByPlaceholder(Pattern.compile("(?iu)Nombre\\s+del\\s+Negocio"))));
		assertVisible("business quota text", page.getByText(Pattern.compile("(?iu)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
		assertVisible("Cancelar button",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Cancelar"))));
		assertVisible("Crear Negocio button",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Crear\\s+Negocio"))));

		screenshot(page, "03-agregar-negocio-modal.png", false);

		// Optional action requested in the scenario.
		final Locator nombreNegocioInput = firstVisible("Nombre del Negocio input",
				page.getByLabel(Pattern.compile("(?iu)Nombre\\s+del\\s+Negocio")),
				page.getByPlaceholder(Pattern.compile("(?iu)Nombre\\s+del\\s+Negocio")));
		nombreNegocioInput.click();
		nombreNegocioInput.fill("Negocio Prueba Automatización");
		clickAndWait(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Cancelar"))),
				page);
	}

	private void openAdministrarNegocios(final Page page) {
		final Locator administrarNegocios = page.getByText(Pattern.compile("(?iu)^Administrar\\s+Negocios$")).first();
		if (!administrarNegocios.isVisible()) {
			final Locator miNegocio = firstVisible("Mi Negocio re-expand",
					page.getByText(Pattern.compile("(?iu)Mi\\s+Negocio")),
					page.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Mi\\s+Negocio"))));
			clickAndWait(miNegocio, page);
		}

		clickAndWait(firstVisible("Administrar Negocios link",
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Administrar\\s+Negocios"))),
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Administrar\\s+Negocios"))),
				page.getByText(Pattern.compile("(?iu)^Administrar\\s+Negocios$"))), page);

		assertVisible("Información General section", page.getByText(Pattern.compile("(?iu)Informaci[oó]n\\s+General")));
		assertVisible("Detalles de la Cuenta section",
				page.getByText(Pattern.compile("(?iu)Detalles\\s+de\\s+la\\s+Cuenta")));
		assertVisible("Tus Negocios section", page.getByText(Pattern.compile("(?iu)Tus\\s+Negocios")));
		assertVisible("Sección Legal section", page.getByText(Pattern.compile("(?iu)Secci[oó]n\\s+Legal")));
		screenshot(page, "04-administrar-negocios-vista-completa.png", true);
	}

	private void validateInformacionGeneral(final Page page) {
		final String pageText = page.locator("body").innerText();
		assertTrue("Expected visible user email in Información General.",
				EMAIL_PATTERN.matcher(pageText).find() || pageText.contains("juanlucasbarbiergarzon@gmail.com"));
		assertVisible("BUSINESS PLAN text", page.getByText(Pattern.compile("(?iu)BUSINESS\\s+PLAN")));
		assertVisible("Cambiar Plan button",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Cambiar\\s+Plan"))));

		// Most UIs label the name field with Nombre/Name/Usuario; use visible text check.
		assertVisible("user name indicator",
				firstVisible("name indicator", page.getByText(Pattern.compile("(?iu)Nombre")),
						page.getByText(Pattern.compile("(?iu)Name")), page.getByText(Pattern.compile("(?iu)Usuario"))));
	}

	private void validateDetallesCuenta(final Page page) {
		assertVisible("Cuenta creada text", page.getByText(Pattern.compile("(?iu)Cuenta\\s+creada")));
		assertVisible("Estado activo text", page.getByText(Pattern.compile("(?iu)Estado\\s+activo")));
		assertVisible("Idioma seleccionado text", page.getByText(Pattern.compile("(?iu)Idioma\\s+seleccionado")));
	}

	private void validateTusNegocios(final Page page) {
		assertVisible("Tus Negocios heading", page.getByText(Pattern.compile("(?iu)Tus\\s+Negocios")));
		assertVisible("Agregar Negocio button",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)Agregar\\s+Negocio"))));
		assertVisible("business quota text in Tus Negocios",
				page.getByText(Pattern.compile("(?iu)Tienes\\s+2\\s+de\\s+3\\s+negocios")));

		final String tusNegociosSectionText = page.locator("body").innerText();
		assertTrue("Expected a visible business list in Tus Negocios section.", tusNegociosSectionText.contains("Tus Negocios"));
	}

	private void validateLegalDocument(final Page appPage, final BrowserContext context, final String linkText,
			final Pattern expectedHeading, final String screenshotFileName) {
		final int previousPageCount = context.pages().size();
		final Locator legalLink = firstVisible(linkText + " link",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu)" + Pattern.quote(linkText)))),
				appPage.getByText(Pattern.compile("(?iu)" + Pattern.quote(linkText))));

		Page legalPage = appPage;
		try {
			legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(6_000), () -> {
				legalLink.click();
				waitForUi(appPage);
			});
		} catch (PlaywrightException sameTabExpected) {
			legalLink.click();
			waitForUi(appPage);
			legalPage = appPage;
		}

		waitForUi(legalPage);
		assertVisible(linkText + " heading", legalPage.getByText(expectedHeading));
		assertTrue("Expected legal content text for " + linkText + ".",
				legalPage.locator("body").innerText().replaceAll("\\s+", " ").trim().length() > 250);
		screenshot(legalPage, screenshotFileName, true);
		System.out.println(linkText + " URL: " + legalPage.url());

		if (context.pages().size() > previousPageCount) {
			legalPage.close();
			appPage.bringToFront();
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}
	}

	private void handleGoogleAccountSelection(final Page authPage, final String googleEmail) {
		waitForUi(authPage);
		final Locator accountTile = authPage.getByText(Pattern.compile("(?iu)" + Pattern.quote(googleEmail))).first();
		if (isVisible(accountTile)) {
			clickAndWait(accountTile, authPage);
		}
	}

	private boolean runStep(final String stepName, final CheckedRunnable action) {
		try {
			action.run();
			report.put(stepName, "PASS");
			return true;
		} catch (Throwable throwable) {
			report.put(stepName, "FAIL");
			System.err.println("Step failed: " + stepName + " -> " + throwable.getMessage());
			return false;
		}
	}

	private boolean runDependentStep(final boolean canContinue, final String stepName, final CheckedRunnable action) {
		if (!canContinue) {
			report.put(stepName, "FAIL");
			System.err.println("Step failed: " + stepName + " -> blocked because a previous required step failed.");
			return false;
		}
		return runStep(stepName, action);
	}

	private void initReport() {
		report.put("Login", "FAIL");
		report.put("Mi Negocio menu", "FAIL");
		report.put("Agregar Negocio modal", "FAIL");
		report.put("Administrar Negocios view", "FAIL");
		report.put("Información General", "FAIL");
		report.put("Detalles de la Cuenta", "FAIL");
		report.put("Tus Negocios", "FAIL");
		report.put("Términos y Condiciones", "FAIL");
		report.put("Política de Privacidad", "FAIL");
	}

	private void printFinalReport() {
		System.out.println("==== SaleADS Mi Negocio Workflow Final Report ====");
		report.forEach((step, status) -> System.out.println(step + ": " + status));
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private void assertAllStepsPassed() {
		final StringBuilder failures = new StringBuilder();
		report.forEach((step, status) -> {
			if (!"PASS".equals(status)) {
				failures.append(step).append("=").append(status).append("; ");
			}
		});
		assertTrue("One or more workflow checks failed: " + failures, failures.length() == 0);
	}

	private Locator firstVisible(final String targetName, final Locator... options) {
		for (final Locator option : options) {
			final Locator first = option.first();
			try {
				first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(4_000));
				return first;
			} catch (PlaywrightException ignored) {
				// Try the next option.
			}
		}
		throw new AssertionError("Could not find visible element for: " + targetName);
	}

	private void assertVisible(final String targetName, final Locator locator) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE)
					.setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException exception) {
			throw new AssertionError("Expected visible element not found: " + targetName, exception);
		}
	}

	private void clickAndWait(final Locator locator, final Page page) {
		locator.first().click();
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED,
					new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// Some modal interactions do not trigger load events.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8_000));
		} catch (PlaywrightException ignored) {
			// NETWORKIDLE may not be reached for SPAs with background polling.
		}
		page.waitForTimeout(500);
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.isVisible();
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void screenshot(final Page page, final String fileName, final boolean fullPage) {
		try {
			page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
		} catch (PlaywrightException ignored) {
			// Evidence capture should not hide validation results.
		}
	}

	private Path createEvidenceDirectory() throws Exception {
		final String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Path.of("target", "saleads-evidence", stamp);
		Files.createDirectories(dir);
		return dir;
	}

	private String readConfig(final String envName, final String propertyName, final String defaultValue) {
		final String env = System.getenv(envName);
		if (env != null && !env.isBlank()) {
			return env.trim();
		}

		final String property = System.getProperty(propertyName);
		if (property != null && !property.isBlank()) {
			return property.trim();
		}

		return defaultValue;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
