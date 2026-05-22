package io.proleap.saleads.e2e;

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
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String BASE_URL_ENV = "SALEADS_BASE_URL";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String EVIDENCE_DIR_ENV = "SALEADS_EVIDENCE_DIR";
	private static final String GOOGLE_ACCOUNT_ENV = "SALEADS_GOOGLE_ACCOUNT_EMAIL";
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private static final int SHORT_TIMEOUT_MS = 5_000;
	private static final int DEFAULT_TIMEOUT_MS = 15_000;

	private static final Pattern GOOGLE_TEXT_PATTERN = Pattern
			.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesion\\s*con\\s*google|continuar\\s*con\\s*google|google)");
	private static final Pattern NEGOCIO_TEXT_PATTERN = Pattern.compile("(?i)^negocio$");
	private static final Pattern MI_NEGOCIO_TEXT_PATTERN = Pattern.compile("(?i)^mi\\s*negocio$");

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final Map<String, String> report = createBlankReport();
		final StringBuilder notes = new StringBuilder();
		final Path evidenceDir = resolveEvidenceDir();
		Files.createDirectories(evidenceDir);

		final AtomicReference<String> termsUrl = new AtomicReference<>("");
		final AtomicReference<String> privacyUrl = new AtomicReference<>("");

		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(resolveHeadless());
			try (Browser browser = playwright.chromium().launch(launchOptions)) {
				final Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setViewportSize(1600, 1200);
				final BrowserContext context = browser.newContext(contextOptions);
				final Page page = context.newPage();
				page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

				final String loginUrl = firstNonBlank(System.getenv(LOGIN_URL_ENV), System.getenv(BASE_URL_ENV));
				Assert.assertTrue(
						"Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL) to the current SaleADS environment login URL.",
						loginUrl != null && !loginUrl.isBlank());

				page.navigate(loginUrl);
				waitForUiAfterAction(page);

				final boolean loginPassed = executeStep("Login", report, notes, () -> runLoginStep(page, context, evidenceDir));
				final boolean miNegocioMenuPassed = loginPassed
						? executeStep("Mi Negocio menu", report, notes, () -> runMiNegocioMenuStep(page, evidenceDir))
						: markDependencyFailure("Mi Negocio menu", "Login", report, notes);

				final boolean agregarNegocioModalPassed = miNegocioMenuPassed
						? executeStep("Agregar Negocio modal", report, notes, () -> runAgregarNegocioModalStep(page, evidenceDir))
						: markDependencyFailure("Agregar Negocio modal", "Mi Negocio menu", report, notes);

				final boolean administrarNegociosPassed = agregarNegocioModalPassed
						? executeStep("Administrar Negocios view", report, notes,
								() -> runAdministrarNegociosStep(page, evidenceDir))
						: markDependencyFailure("Administrar Negocios view", "Agregar Negocio modal", report, notes);

				final boolean informacionGeneralPassed = administrarNegociosPassed
						? executeStep("Información General", report, notes, () -> validateInformacionGeneral(page))
						: markDependencyFailure("Información General", "Administrar Negocios view", report, notes);

				final boolean detallesCuentaPassed = informacionGeneralPassed
						? executeStep("Detalles de la Cuenta", report, notes, () -> validateDetallesCuenta(page))
						: markDependencyFailure("Detalles de la Cuenta", "Información General", report, notes);

				final boolean tusNegociosPassed = detallesCuentaPassed
						? executeStep("Tus Negocios", report, notes, () -> validateTusNegocios(page))
						: markDependencyFailure("Tus Negocios", "Detalles de la Cuenta", report, notes);

				final boolean terminosPassed = tusNegociosPassed
						? executeStep("Términos y Condiciones", report, notes, () -> {
							termsUrl.set(validateLegalPageLink(page, "Términos y Condiciones",
									Pattern.compile("(?i)t[ée]rminos\\s+y\\s+condiciones"),
									evidenceDir.resolve("08-terminos-y-condiciones.png")));
						})
						: markDependencyFailure("Términos y Condiciones", "Tus Negocios", report, notes);

				if (!terminosPassed) {
					privacyUrl.set("");
				}

				final boolean privacidadPassed = terminosPassed
						? executeStep("Política de Privacidad", report, notes, () -> {
							privacyUrl.set(validateLegalPageLink(page, "Política de Privacidad",
									Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"),
									evidenceDir.resolve("09-politica-de-privacidad.png")));
						})
						: markDependencyFailure("Política de Privacidad", "Términos y Condiciones", report, notes);

				if (!privacidadPassed) {
					privacyUrl.set("");
				}

				printFinalReport(report, notes, evidenceDir, termsUrl.get(), privacyUrl.get());

				final boolean allStepsPassed = report.values().stream().allMatch("PASS"::equals);
				Assert.assertTrue("One or more workflow validations failed. See report above.", allStepsPassed);
			}
		}
	}

	private void runLoginStep(final Page page, final BrowserContext context, final Path evidenceDir) {
		final String googleAccountEmail = firstNonBlank(System.getenv(GOOGLE_ACCOUNT_ENV), DEFAULT_GOOGLE_ACCOUNT);
		final Locator googleLoginButton = findFirstCandidate(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_TEXT_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_TEXT_PATTERN)),
				page.getByText(GOOGLE_TEXT_PATTERN));
		clickAndWait(page, googleLoginButton);

		final Page authPage = detectGooglePage(context, page);
		if (authPage != null) {
			final Locator accountChoice = authPage.getByText(Pattern.compile("(?i)" + Pattern.quote(googleAccountEmail)));
			if (isVisible(accountChoice)) {
				clickAndWait(authPage, accountChoice.first());
			}
		}

		page.bringToFront();
		waitForUiAfterAction(page);

		final Locator sidebar = findFirstCandidate(page, page.locator("aside"),
				page.getByRole(AriaRole.NAVIGATION), page.getByText(NEGOCIO_TEXT_PATTERN));
		assertVisible(sidebar, "Left sidebar should be visible after login.");
		assertVisible(page.getByText(Pattern.compile("(?i)(dashboard|panel|inicio|negocio)")),
				"Main application interface should be visible after login.");

		captureScreenshot(page, evidenceDir.resolve("01-dashboard-after-login.png"), true);
	}

	private void runMiNegocioMenuStep(final Page page, final Path evidenceDir) {
		final Locator negocioSection = findFirstCandidate(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEGOCIO_TEXT_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(NEGOCIO_TEXT_PATTERN)),
				page.getByText(NEGOCIO_TEXT_PATTERN));
		if (isVisible(negocioSection)) {
			clickAndWait(page, negocioSection);
		}

		final Locator miNegocio = findFirstCandidate(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_TEXT_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MI_NEGOCIO_TEXT_PATTERN)),
				page.getByText(MI_NEGOCIO_TEXT_PATTERN));
		clickAndWait(page, miNegocio);

		assertVisible(page.getByText(Pattern.compile("(?i)^agregar\\s*negocio$")),
				"'Agregar Negocio' should be visible in expanded submenu.");
		assertVisible(page.getByText(Pattern.compile("(?i)^administrar\\s*negocios$")),
				"'Administrar Negocios' should be visible in expanded submenu.");

		captureScreenshot(page, evidenceDir.resolve("02-mi-negocio-expanded-menu.png"), false);
	}

	private void runAgregarNegocioModalStep(final Page page, final Path evidenceDir) {
		final Locator agregarNegocio = findFirstCandidate(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^agregar\\s*negocio$"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^agregar\\s*negocio$"))),
				page.getByText(Pattern.compile("(?i)^agregar\\s*negocio$")));
		clickAndWait(page, agregarNegocio);

		assertVisible(page.getByText(Pattern.compile("(?i)^crear\\s+nuevo\\s+negocio$")),
				"Modal title 'Crear Nuevo Negocio' should be visible.");

		final Locator nombreNegocioInput = findFirstCandidate(page,
				page.getByLabel(Pattern.compile("(?i)^nombre\\s+del\\s+negocio$")),
				page.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
				page.locator("input"));
		assertVisible(nombreNegocioInput, "Input field 'Nombre del Negocio' should exist.");

		assertVisible(page.getByText(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios")),
				"Expected limit text 'Tienes 2 de 3 negocios' was not found.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
				"Button 'Cancelar' should be present in modal.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")),
				"Button 'Crear Negocio' should be present in modal.");

		captureScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

		clickAndWait(page, nombreNegocioInput);
		nombreNegocioInput.fill("Negocio Prueba Automatizacion");
		clickAndWait(page, page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")));
	}

	private void runAdministrarNegociosStep(final Page page, final Path evidenceDir) {
		ensureMiNegocioSubmenuExpanded(page);

		final Locator administrarNegocios = findFirstCandidate(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^administrar\\s*negocios$"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^administrar\\s*negocios$"))),
				page.getByText(Pattern.compile("(?i)^administrar\\s*negocios$")));
		clickAndWait(page, administrarNegocios);

		assertVisible(page.getByText(Pattern.compile("(?i)^informaci[oó]n\\s+general$")),
				"Section 'Información General' should exist.");
		assertVisible(page.getByText(Pattern.compile("(?i)^detalles\\s+de\\s+la\\s+cuenta$")),
				"Section 'Detalles de la Cuenta' should exist.");
		assertVisible(page.getByText(Pattern.compile("(?i)^tus\\s+negocios$")),
				"Section 'Tus Negocios' should exist.");
		assertVisible(page.getByText(Pattern.compile("(?i)(secci[oó]n\\s+legal|legal)")),
				"Section 'Sección Legal' should exist.");

		captureScreenshot(page, evidenceDir.resolve("04-administrar-negocios-view.png"), true);
	}

	private void validateInformacionGeneral(final Page page) {
		assertVisible(page.getByText(Pattern.compile("(?i)^business\\s+plan$")), "Text 'BUSINESS PLAN' should be visible.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar\\s+plan"))),
				"Button 'Cambiar Plan' should be visible.");

		final Locator userName = page.getByText(Pattern.compile("(?i)(usuario|nombre|perfil)"));
		assertVisible(userName, "A user name or profile identifier should be visible.");
		assertVisible(page.getByText(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
				"User email should be visible.");
	}

	private void validateDetallesCuenta(final Page page) {
		assertVisible(page.getByText(Pattern.compile("(?i)cuenta\\s+creada")), "'Cuenta creada' should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)estado\\s+activo")), "'Estado activo' should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)idioma\\s+seleccionado")),
				"'Idioma seleccionado' should be visible.");
	}

	private void validateTusNegocios(final Page page) {
		assertVisible(page.getByText(Pattern.compile("(?i)^tus\\s+negocios$")), "Business list section should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)^agregar\\s*negocio$")), "Button 'Agregar Negocio' should exist.");
		assertVisible(page.getByText(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios")),
				"Text 'Tienes 2 de 3 negocios' should be visible.");
	}

	private String validateLegalPageLink(final Page appPage, final String linkText, final Pattern headingPattern,
			final Path screenshotPath) {
		final Locator legalLink = findFirstCandidate(appPage,
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))),
				appPage.getByText(Pattern.compile("(?i)" + Pattern.quote(linkText))));

		Page destinationPage = null;
		try {
			destinationPage = appPage.waitForPopup(() -> clickAndWait(appPage, legalLink),
					new Page.WaitForPopupOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (PlaywrightException popupDidNotOpen) {
			clickAndWait(appPage, legalLink);
			destinationPage = appPage;
		}

		waitForUiAfterAction(destinationPage);

		final Locator heading = findFirstCandidate(destinationPage,
				destinationPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
				destinationPage.getByText(headingPattern));
		assertVisible(heading, "Legal page heading should be visible for '" + linkText + "'.");

		final Locator legalContent = destinationPage.locator("main, article, body").first();
		assertVisible(legalContent, "Legal page content should be visible for '" + linkText + "'.");
		final String legalText = legalContent.innerText();
		Assert.assertTrue("Legal page text should contain substantial content for '" + linkText + "'.",
				legalText != null && legalText.trim().length() > 100);

		captureScreenshot(destinationPage, screenshotPath, true);
		final String finalUrl = destinationPage.url();

		if (destinationPage != appPage) {
			destinationPage.close();
			appPage.bringToFront();
			waitForUiAfterAction(appPage);
		} else {
			appPage.goBack();
			waitForUiAfterAction(appPage);
		}

		return finalUrl;
	}

	private void ensureMiNegocioSubmenuExpanded(final Page page) {
		final Locator administrarOption = page.getByText(Pattern.compile("(?i)^administrar\\s*negocios$"));
		if (isVisible(administrarOption)) {
			return;
		}

		final Locator miNegocio = findFirstCandidate(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_TEXT_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(MI_NEGOCIO_TEXT_PATTERN)),
				page.getByText(MI_NEGOCIO_TEXT_PATTERN));
		clickAndWait(page, miNegocio);
	}

	private boolean executeStep(final String stepName, final Map<String, String> report, final StringBuilder notes,
			final StepAction action) {
		try {
			action.run();
			report.put(stepName, "PASS");
			return true;
		} catch (Throwable t) {
			report.put(stepName, "FAIL");
			appendNote(notes, stepName + " failed: " + t.getMessage());
			return false;
		}
	}

	private boolean markDependencyFailure(final String stepName, final String dependencyStep, final Map<String, String> report,
			final StringBuilder notes) {
		report.put(stepName, "FAIL");
		appendNote(notes, stepName + " skipped because dependency failed: " + dependencyStep + ".");
		return false;
	}

	private Locator findFirstCandidate(final Page page, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate.count() > 0) {
				return candidate.first();
			}
		}

		if (candidates.length > 0) {
			return candidates[0];
		}

		return page.locator("body");
	}

	private Page detectGooglePage(final BrowserContext context, final Page appPage) {
		waitForUiAfterAction(appPage);

		if (appPage.url().contains("accounts.google.com")) {
			return appPage;
		}

		for (final Page candidate : context.pages()) {
			if (candidate.url().contains("accounts.google.com")) {
				return candidate;
			}
		}

		return null;
	}

	private void clickAndWait(final Page page, final Locator locator) {
		assertVisible(locator, "Unable to click element because it is not visible.");
		locator.first().click();
		waitForUiAfterAction(page);
	}

	private void waitForUiAfterAction(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (PlaywrightException ignored) {
			// UI might update without navigation.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// Keep moving if network idle is not reached.
		}

		page.waitForTimeout(500);
	}

	private void assertVisible(final Locator locator, final String message) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE)
					.setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException e) {
			throw new AssertionError(message, e);
		}
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.count() > 0 && locator.first().isVisible();
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private void captureScreenshot(final Page page, final Path outputFile, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(outputFile).setFullPage(fullPage));
	}

	private Map<String, String> createBlankReport() {
		final Map<String, String> report = new LinkedHashMap<>();
		report.put("Login", "FAIL");
		report.put("Mi Negocio menu", "FAIL");
		report.put("Agregar Negocio modal", "FAIL");
		report.put("Administrar Negocios view", "FAIL");
		report.put("Información General", "FAIL");
		report.put("Detalles de la Cuenta", "FAIL");
		report.put("Tus Negocios", "FAIL");
		report.put("Términos y Condiciones", "FAIL");
		report.put("Política de Privacidad", "FAIL");
		return report;
	}

	private void printFinalReport(final Map<String, String> report, final StringBuilder notes, final Path evidenceDir,
			final String termsUrl, final String privacyUrl) {
		System.out.println("==== SaleADS Mi Negocio Final Report ====");
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}

		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("Terminos y Condiciones URL: " + (termsUrl == null ? "" : termsUrl));
		System.out.println("Politica de Privacidad URL: " + (privacyUrl == null ? "" : privacyUrl));

		if (notes.length() > 0) {
			System.out.println("Notes:");
			System.out.println(notes);
		}
	}

	private Path resolveEvidenceDir() {
		final String customDir = System.getenv(EVIDENCE_DIR_ENV);
		if (customDir != null && !customDir.isBlank()) {
			return Paths.get(customDir);
		}

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		return Paths.get("target", "saleads-evidence", timestamp);
	}

	private boolean resolveHeadless() {
		final String headlessRaw = System.getenv(HEADLESS_ENV);
		return headlessRaw == null || Boolean.parseBoolean(headlessRaw);
	}

	private String firstNonBlank(final String first, final String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}

		if (second != null && !second.isBlank()) {
			return second;
		}

		return null;
	}

	private void appendNote(final StringBuilder notes, final String note) {
		if (notes.length() > 0) {
			notes.append(System.lineSeparator());
		}
		notes.append("- ").append(note);
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
