package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
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
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String AUTOMATION_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final double UI_TIMEOUT_MS = 30_000d;
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> results = new LinkedHashMap<>();
	private int screenshotCounter = 0;

	@Test
	public void testFullMiNegocioWorkflow() throws IOException {
		final String loginUrl = config("SALEADS_LOGIN_URL", "saleads.login.url");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL (or -Dsaleads.login.url) with the environment login page URL.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(config("SALEADS_HEADLESS", "saleads.headless", "true"));
		final Path runArtifactsDir = createRunArtifactsDirectory();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			try (BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000))) {
				final Page appPage = context.newPage();
				appPage.navigate(loginUrl);
				waitForUi(appPage);

				final boolean loginOk = runStep("Login", () -> loginWithGoogleAndValidateShell(appPage, context, runArtifactsDir));
				final boolean menuOk = loginOk ? runStep("Mi Negocio menu", () -> openMiNegocioMenu(appPage, runArtifactsDir))
						: markAsBlocked("Mi Negocio menu", "Login");
				final boolean modalOk = menuOk
						? runStep("Agregar Negocio modal", () -> validateAgregarNegocioModal(appPage, runArtifactsDir))
						: markAsBlocked("Agregar Negocio modal", "Mi Negocio menu");
				final boolean administrarOk = modalOk
						? runStep("Administrar Negocios view",
								() -> openAdministrarNegociosAndValidateSections(appPage, runArtifactsDir))
						: markAsBlocked("Administrar Negocios view", "Agregar Negocio modal");
				final boolean infoOk = administrarOk ? runStep("Información General", () -> validateInformacionGeneral(appPage))
						: markAsBlocked("Información General", "Administrar Negocios view");
				final boolean detailsOk = infoOk ? runStep("Detalles de la Cuenta", () -> validateDetallesDeLaCuenta(appPage))
						: markAsBlocked("Detalles de la Cuenta", "Información General");
				final boolean businessesOk = detailsOk ? runStep("Tus Negocios", () -> validateTusNegocios(appPage))
						: markAsBlocked("Tus Negocios", "Detalles de la Cuenta");
				final boolean termsOk = businessesOk
						? runStep("Términos y Condiciones",
								() -> validateLegalLink(appPage, context, "Términos y Condiciones",
										Pattern.compile("(?i)T[ée]rminos\\s+y\\s+Condiciones"), runArtifactsDir, true))
						: markAsBlocked("Términos y Condiciones", "Tus Negocios");
				if (termsOk) {
					runStep("Política de Privacidad",
							() -> validateLegalLink(appPage, context, "Política de Privacidad",
									Pattern.compile("(?i)Pol[íi]tica\\s+de\\s+Privacidad"), runArtifactsDir, false));
				} else {
					markAsBlocked("Política de Privacidad", "Términos y Condiciones");
				}
			}
		} finally {
			printFinalReport(runArtifactsDir);
		}

		assertTrue("One or more steps failed. See the Final Report in test output.", allStepsPassed());
	}

	private String loginWithGoogleAndValidateShell(final Page appPage, final BrowserContext context, final Path artifactsDir) {
		final Locator googleButton = firstVisibleOrFail(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?i)(sign\\s*in|iniciar\\s+sesi[oó]n|continuar).*google|google"))).first(),
				appPage.getByText(Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s+sesi[oó]n\\s+con\\s+google|google)"))
						.first());

		Page googlePage = null;
		try {
			googlePage = context.waitForPage(() -> clickAndWait(appPage, googleButton),
					new BrowserContext.WaitForPageOptions().setTimeout(8_000));
			waitForUi(googlePage);
		} catch (final TimeoutError timeout) {
			// Some environments keep auth in the same tab.
			clickAndWait(appPage, googleButton);
		}

		if (googlePage != null && !googlePage.isClosed()) {
			selectGoogleAccountIfShown(googlePage);
			if (!googlePage.isClosed()) {
				googlePage.bringToFront();
			}
			appPage.bringToFront();
		} else {
			selectGoogleAccountIfShown(appPage);
		}

		waitForUi(appPage);
		assertVisible(appPage.getByRole(AriaRole.NAVIGATION).first(),
				"Main application interface should expose a visible sidebar navigation.");
		assertVisible(appPage.getByText(Pattern.compile("(?i)negocio")).first(), "Sidebar should show the Negocio area.");
		final Path screenshot = screenshot(appPage, artifactsDir, "dashboard_loaded", false);
		return "Dashboard loaded. screenshot=" + screenshot;
	}

	private void selectGoogleAccountIfShown(final Page page) {
		final Locator account = page.getByText(AUTOMATION_EMAIL).first();
		if (isVisible(account)) {
			clickAndWait(page, account);
		}
	}

	private String openMiNegocioMenu(final Page page, final Path artifactsDir) {
		final Locator negocio = page.getByText(Pattern.compile("(?i)^\\s*Negocio\\s*$")).first();
		if (isVisible(negocio)) {
			clickAndWait(page, negocio);
		}

		final Locator miNegocio = firstVisibleOrFail(page.getByText(Pattern.compile("(?i)^\\s*Mi\\s+Negocio\\s*$")).first(),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Mi\\s+Negocio")))
						.first());
		clickAndWait(page, miNegocio);

		assertVisible(page.getByText(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$")).first(),
				"The submenu should contain 'Agregar Negocio'.");
		assertVisible(page.getByText(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$")).first(),
				"The submenu should contain 'Administrar Negocios'.");
		final Path screenshot = screenshot(page, artifactsDir, "mi_negocio_expanded_menu", false);
		return "Mi Negocio expanded. screenshot=" + screenshot;
	}

	private String validateAgregarNegocioModal(final Page page, final Path artifactsDir) {
		clickAndWait(page, firstVisibleOrFail(page.getByText(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$")).first(),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Agregar\\s+Negocio")))
						.first()));

		assertVisible(page.getByText(Pattern.compile("(?i)Crear\\s+Nuevo\\s+Negocio")).first(),
				"Modal title 'Crear Nuevo Negocio' should be visible.");

		Locator businessName = page.getByLabel(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")).first();
		if (!isVisible(businessName)) {
			businessName = page.getByPlaceholder(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")).first();
		}
		assertVisible(businessName, "Input field 'Nombre del Negocio' must be visible.");

		assertVisible(page.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")).first(),
				"Business limit text should be visible.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cancelar"))).first(),
				"Button 'Cancelar' must be visible.");
		assertVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Crear\\s+Negocio"))).first(),
				"Button 'Crear Negocio' must be visible.");

		final Path screenshot = screenshot(page, artifactsDir, "agregar_negocio_modal", false);
		businessName.fill("Negocio Prueba Automatización");
		clickAndWait(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cancelar"))).first());
		return "Agregar Negocio modal validated. screenshot=" + screenshot;
	}

	private String openAdministrarNegociosAndValidateSections(final Page page, final Path artifactsDir) {
		final Locator administrar = page.getByText(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$")).first();
		if (!isVisible(administrar)) {
			clickAndWait(page, page.getByText(Pattern.compile("(?i)^\\s*Mi\\s+Negocio\\s*$")).first());
		}
		clickAndWait(page, firstVisibleOrFail(administrar,
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Administrar\\s+Negocios"))).first()));

		assertVisible(page.getByText(Pattern.compile("(?i)Informaci[oó]n\\s+General")).first(),
				"Section 'Información General' should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)Detalles\\s+de\\s+la\\s+Cuenta")).first(),
				"Section 'Detalles de la Cuenta' should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)Tus\\s+Negocios")).first(),
				"Section 'Tus Negocios' should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)Secci[oó]n\\s+Legal")).first(),
				"Section 'Sección Legal' should be visible.");
		final Path screenshot = screenshot(page, artifactsDir, "administrar_negocios_account_page", true);
		return "Administrar Negocios loaded. screenshot=" + screenshot;
	}

	private String validateInformacionGeneral(final Page page) {
		assertVisible(page.getByText(Pattern.compile("(?i)BUSINESS\\s+PLAN")).first(),
				"'BUSINESS PLAN' text should be visible in account info.");
		assertVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cambiar\\s+Plan"))).first(),
				"Button 'Cambiar Plan' should be visible.");

		final Locator emailLocator = page.getByText(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")).first();
		assertVisible(emailLocator, "A user email should be visible in account information.");
		final String emailText = emailLocator.innerText().trim();

		final Locator nearestContainer = emailLocator.locator("xpath=ancestor::*[self::div or self::section][1]").first();
		final String containerText = nearestContainer.innerText();
		final boolean hasUserName = Arrays.stream(containerText.split("\\R")).map(String::trim)
				.anyMatch(line -> !line.isBlank() && !line.equalsIgnoreCase(emailText) && !line.contains("@")
						&& !line.equalsIgnoreCase("Información General") && !line.equalsIgnoreCase("BUSINESS PLAN")
						&& !line.equalsIgnoreCase("Cambiar Plan"));
		assertTrue("A user name should be visible in 'Información General'.", hasUserName);
		return "Información General validated.";
	}

	private String validateDetallesDeLaCuenta(final Page page) {
		assertVisible(page.getByText(Pattern.compile("(?i)Cuenta\\s+creada")).first(),
				"'Cuenta creada' should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)Estado\\s+activo")).first(), "'Estado activo' should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)Idioma\\s+seleccionado")).first(),
				"'Idioma seleccionado' should be visible.");
		return "Detalles de la Cuenta validated.";
	}

	private String validateTusNegocios(final Page page) {
		assertVisible(page.getByText(Pattern.compile("(?i)Tus\\s+Negocios")).first(), "Business section should be visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")).first(),
				"Business limit text should be visible.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Agregar\\s+Negocio"))).first(),
				"'Agregar Negocio' button should be visible in business section.");

		final String bodyText = page.locator("body").innerText();
		final boolean hasAnyBusinessItem = bodyText.contains("Tus Negocios")
				&& Pattern.compile("(?i)(Negocio\\s+\\d+|Negocio\\s+Prueba|Administrar\\s+Negocios)").matcher(bodyText).find();
		assertTrue("Business list should contain at least one visible item.", hasAnyBusinessItem);
		return "Tus Negocios validated.";
	}

	private String validateLegalLink(final Page appPage, final BrowserContext context, final String fieldName,
			final Pattern headingPattern, final Path artifactsDir, final boolean terms) {
		final Locator legalLink = firstVisibleOrFail(appPage.getByText(headingPattern).first(),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(headingPattern)).first());

		Page legalPage;
		try {
			legalPage = context.waitForPage(() -> clickAndWait(appPage, legalLink),
					new BrowserContext.WaitForPageOptions().setTimeout(8_000));
			waitForUi(legalPage);
		} catch (final TimeoutError timeout) {
			clickAndWait(appPage, legalLink);
			legalPage = appPage;
		}

		assertVisible(legalPage.getByText(headingPattern).first(), fieldName + " heading should be visible.");
		final String legalBody = legalPage.locator("body").innerText().replaceAll("\\s+", " ").trim();
		assertTrue(fieldName + " should show legal content text.", legalBody.length() > 200);

		final Path screenshot = screenshot(legalPage, artifactsDir, terms ? "terminos_condiciones" : "politica_privacidad", true);
		final String finalUrl = legalPage.url();

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return fieldName + " validated. url=" + finalUrl + " screenshot=" + screenshot;
	}

	private boolean runStep(final String field, final StepAction action) {
		try {
			final String details = action.run();
			results.put(field, StepResult.pass(details));
			return true;
		} catch (final Exception error) {
			results.put(field, StepResult.fail(error.getMessage()));
			return false;
		}
	}

	private boolean markAsBlocked(final String field, final String blockedBy) {
		results.put(field, StepResult.fail("Not executed because '" + blockedBy + "' failed."));
		return false;
	}

	private Locator firstVisibleOrFail(final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			if (isVisible(candidate)) {
				return candidate;
			}
		}
		throw new IllegalStateException("Could not find any visible locator for the requested action.");
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.isVisible();
		} catch (final Exception ignored) {
			return false;
		}
	}

	private void assertVisible(final Locator locator, final String message) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(UI_TIMEOUT_MS));
		} catch (final Exception error) {
			throw new IllegalStateException(message, error);
		}
	}

	private void clickAndWait(final Page page, final Locator locator) {
		assertVisible(locator, "Element should be visible before clicking.");
		locator.click();
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(4_000));
		} catch (final TimeoutError ignored) {
			// Ignore on apps with persistent network activity (e.g. websockets).
		}
		page.waitForTimeout(500);
	}

	private Path screenshot(final Page page, final Path artifactsDir, final String label, final boolean fullPage) {
		try {
			screenshotCounter++;
			final String fileName = String.format("%02d_%s.png", Integer.valueOf(screenshotCounter), label);
			final Path screenshotPath = artifactsDir.resolve(fileName);
			page.screenshot(
					new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage).setTimeout(UI_TIMEOUT_MS));
			return screenshotPath;
		} catch (final Exception error) {
			throw new IllegalStateException("Failed to take screenshot for " + label, error);
		}
	}

	private Path createRunArtifactsDirectory() throws IOException {
		final Path baseDir = Path.of("target", "saleads-artifacts");
		Files.createDirectories(baseDir);
		final Path runDir = baseDir.resolve(DATE_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(runDir);
		return runDir;
	}

	private String config(final String envName, final String propName) {
		return config(envName, propName, null);
	}

	private String config(final String envName, final String propName, final String fallback) {
		final String valueFromProperty = System.getProperty(propName);
		if (valueFromProperty != null && !valueFromProperty.isBlank()) {
			return valueFromProperty;
		}
		final String valueFromEnv = System.getenv(envName);
		if (valueFromEnv != null && !valueFromEnv.isBlank()) {
			return valueFromEnv;
		}
		return fallback;
	}

	private void printFinalReport(final Path artifactsDir) {
		System.out.println("\n=== Final Report (" + TEST_NAME + ") ===");
		System.out.println("artifacts_dir=" + artifactsDir.toAbsolutePath());
		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.getOrDefault(field, StepResult.fail("Step did not run."));
			System.out.println(field + ": " + result.status + " - " + result.details);
		}
	}

	private boolean allStepsPassed() {
		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.get(field);
			if (result == null || !"PASS".equals(result.status)) {
				return false;
			}
		}
		return true;
	}

	@FunctionalInterface
	private interface StepAction {
		String run() throws Exception;
	}

	private static class StepResult {

		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult("PASS", details);
		}

		private static StepResult fail(final String details) {
			return new StepResult("FAIL", details);
		}
	}
}
