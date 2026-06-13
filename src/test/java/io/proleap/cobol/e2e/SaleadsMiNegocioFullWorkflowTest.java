package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
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
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Environment-agnostic E2E workflow for SaleADS "Mi Negocio".
 *
 * <p>This test is opt-in by design and is skipped unless
 * {@code SALEADS_E2E_ENABLED=true}. It also expects a runtime login URL via
 * {@code SALEADS_LOGIN_URL} so the same test can run against any environment.
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String ENABLED_ENV = "SALEADS_E2E_ENABLED";
	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final Pattern GOOGLE_BUTTON_PATTERN = Pattern.compile("google", Pattern.CASE_INSENSITIVE);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Path OUTPUT_DIR = Path.of("target", "saleads-e2e-evidence");

	private final Map<String, String> finalReport = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private String terminosUrl = "N/A";
	private String privacidadUrl = "N/A";
	private String administrarNegociosUrl = null;

	private Page appPage;
	private BrowserContext context;

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final String enabled = env(ENABLED_ENV);
		Assume.assumeTrue(
				"Skipping SaleADS E2E workflow. Set " + ENABLED_ENV + "=true to enable.",
				"true".equalsIgnoreCase(enabled));

		final String loginUrl = env(LOGIN_URL_ENV);
		Assume.assumeTrue(
				"Skipping SaleADS E2E workflow. Set " + LOGIN_URL_ENV + " to the login page of the target environment.",
				loginUrl != null && !loginUrl.isBlank());

		Files.createDirectories(OUTPUT_DIR);

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless()));
			try (BrowserContext testContext = browser.newContext()) {
				this.context = testContext;
				this.appPage = context.newPage();
				this.appPage.navigate(loginUrl);
				waitForUi(this.appPage);

				runStep("Login", this::stepLogin);
				runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
				runStep("Agregar Negocio modal", this::stepAgregarNegocioModal);
				runStep("Administrar Negocios view", this::stepAdministrarNegociosView);
				runStep("Información General", this::stepInformacionGeneral);
				runStep("Detalles de la Cuenta", this::stepDetallesCuenta);
				runStep("Tus Negocios", this::stepTusNegocios);
				runStep("Términos y Condiciones", this::stepTerminosYCondiciones);
				runStep("Política de Privacidad", this::stepPoliticaPrivacidad);
			}
		} finally {
			writeFinalReport();
		}

		if (!failures.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failures:\n - " + String.join("\n - ", failures));
		}
	}

	private void stepLogin() {
		final Locator googleButton = firstVisibleLocator("Google login button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_BUTTON_PATTERN)).first(),
				appPage.getByText("Sign in with Google").first(),
				appPage.getByText("Iniciar sesión con Google").first(),
				appPage.getByText("Continuar con Google").first());

		Page popup = null;
		try {
			popup = appPage.waitForPopup(() -> googleButton.click(), new Page.WaitForPopupOptions().setTimeout(7000));
		} catch (PlaywrightException popupNotOpened) {
			googleButton.click();
			waitForUi(appPage);
		}

		if (popup != null) {
			waitForUi(popup);
			selectGoogleAccountIfVisible(popup);
			waitForUi(appPage);
		} else {
			selectGoogleAccountIfVisible(appPage);
			waitForUi(appPage);
		}

		assertVisible(firstVisibleLocator("left sidebar navigation", appPage.locator("aside").first(),
				appPage.locator("nav").first()), "Left sidebar should be visible after login.");

		// Dashboard/main-shell checkpoints are intentionally generic to keep this test portable.
		assertTrue("Main application interface should contain rendered text after login.",
				appPage.locator("body").innerText().trim().length() > 0);

		takeScreenshot(appPage, "01-dashboard-loaded.png", true);
	}

	private void stepOpenMiNegocioMenu() {
		clickTextAndWait(appPage, "Negocio");
		clickTextAndWait(appPage, "Mi Negocio");

		assertVisible(appPage.getByText("Agregar Negocio").first(), "Agregar Negocio should be visible.");
		assertVisible(appPage.getByText("Administrar Negocios").first(), "Administrar Negocios should be visible.");

		takeScreenshot(appPage, "02-mi-negocio-menu-expanded.png", false);
	}

	private void stepAgregarNegocioModal() {
		clickTextAndWait(appPage, "Agregar Negocio");

		assertVisible(appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Crear Nuevo Negocio")).first(),
				"Modal title 'Crear Nuevo Negocio' should be visible.");
		assertVisible(firstVisibleLocator("Nombre del Negocio input", appPage.getByLabel("Nombre del Negocio").first(),
				appPage.getByPlaceholder("Nombre del Negocio").first(), appPage.locator("input[type='text']").first()),
				"Input field 'Nombre del Negocio' should be visible.");
		assertVisible(appPage.getByText("Tienes 2 de 3 negocios").first(), "Expected business quota text should be visible.");
		assertVisible(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first(),
				"Cancelar button should be visible.");
		assertVisible(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")).first(),
				"Crear Negocio button should be visible.");

		takeScreenshot(appPage, "03-agregar-negocio-modal.png", false);

		// Optional action requested in the workflow specification.
		final Locator businessName = firstVisibleLocator("Nombre del Negocio input",
				appPage.getByLabel("Nombre del Negocio").first(), appPage.getByPlaceholder("Nombre del Negocio").first(),
				appPage.locator("input[type='text']").first());
		businessName.click();
		waitForUi(appPage);
		businessName.fill("Negocio Prueba Automatización");
		waitForUi(appPage);
		clickTextAndWait(appPage, "Cancelar");

		assertHidden(appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Crear Nuevo Negocio")).first(),
				"Modal should be closed after clicking Cancelar.");
	}

	private void stepAdministrarNegociosView() {
		ensureMiNegocioExpanded();
		clickTextAndWait(appPage, "Administrar Negocios");
		waitForUi(appPage);
		administrarNegociosUrl = appPage.url();

		assertVisible(appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Información General")).first(),
				"Información General section should exist.");
		assertVisible(appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Detalles de la Cuenta")).first(),
				"Detalles de la Cuenta section should exist.");
		assertVisible(appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Tus Negocios")).first(),
				"Tus Negocios section should exist.");
		assertVisible(firstVisibleLocator("legal section heading",
				appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Sección Legal")).first(),
				appPage.getByText("Sección Legal").first()), "Sección Legal section should exist.");

		takeScreenshot(appPage, "04-administrar-negocios-full-page.png", true);
	}

	private void stepInformacionGeneral() {
		final String pageText = appPage.locator("body").innerText();
		assertTrue("A user email should be visible in Información General.", EMAIL_PATTERN.matcher(pageText).find());
		assertTrue("A likely user name should be visible in Información General.", containsLikelyUserName(pageText));
		assertVisible(appPage.getByText("BUSINESS PLAN").first(), "BUSINESS PLAN text should be visible.");
		assertVisible(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")).first(),
				"Cambiar Plan button should be visible.");
	}

	private void stepDetallesCuenta() {
		assertVisible(appPage.getByText("Cuenta creada").first(), "'Cuenta creada' should be visible.");
		assertVisible(appPage.getByText("Estado activo").first(), "'Estado activo' should be visible.");
		assertVisible(appPage.getByText("Idioma seleccionado").first(), "'Idioma seleccionado' should be visible.");
	}

	private void stepTusNegocios() {
		assertVisible(appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Tus Negocios")).first(),
				"'Tus Negocios' heading should be visible.");
		assertVisible(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")).first(),
				"'Agregar Negocio' button should exist.");
		assertVisible(appPage.getByText("Tienes 2 de 3 negocios").first(), "'Tienes 2 de 3 negocios' text should be visible.");
		assertTrue("Business list should contain visible content.", appPage.locator("body").innerText().contains("Negocio"));
	}

	private void stepTerminosYCondiciones() {
		final LegalResult legalResult = openLegalDocument("Términos y Condiciones", "Términos y Condiciones",
				"05-terminos-y-condiciones.png");
		terminosUrl = legalResult.url;
	}

	private void stepPoliticaPrivacidad() {
		final LegalResult legalResult = openLegalDocument("Política de Privacidad", "Política de Privacidad",
				"06-politica-de-privacidad.png");
		privacidadUrl = legalResult.url;
	}

	private LegalResult openLegalDocument(final String linkText, final String headingText, final String screenshotName) {
		final Locator link = firstVisibleLocator(linkText + " link", appPage.getByText(linkText).first(),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkText)).first());

		Page legalPage = appPage;
		boolean openedPopup = false;
		try {
			legalPage = appPage.waitForPopup(() -> link.click(), new Page.WaitForPopupOptions().setTimeout(7000));
			openedPopup = true;
			waitForUi(legalPage);
		} catch (PlaywrightException popupNotOpened) {
			link.click();
			waitForUi(appPage);
			legalPage = appPage;
		}

		assertVisible(legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingText)).first(),
				headingText + " heading should be visible.");

		final String bodyText = legalPage.locator("body").innerText().trim();
		assertTrue(headingText + " page should contain legal content text.", bodyText.length() > 200);
		takeScreenshot(legalPage, screenshotName, true);

		final String finalUrl = legalPage.url();

		if (openedPopup) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (administrarNegociosUrl != null && !administrarNegociosUrl.isBlank()) {
			appPage.navigate(administrarNegociosUrl);
			waitForUi(appPage);
		}

		return new LegalResult(finalUrl);
	}

	private void ensureMiNegocioExpanded() {
		if (!isVisible(appPage.getByText("Administrar Negocios").first(), 2000)) {
			clickTextAndWait(appPage, "Mi Negocio");
		}
	}

	private void runStep(final String reportField, final StepRunnable stepRunnable) {
		try {
			stepRunnable.run();
			finalReport.put(reportField, "PASS");
		} catch (Throwable throwable) {
			finalReport.put(reportField, "FAIL");
			failures.add(reportField + " -> " + throwable.getMessage());
			try {
				if (appPage != null) {
					takeScreenshot(appPage, "failure-" + sanitize(reportField) + ".png", true);
				}
			} catch (Throwable ignored) {
				// Best effort screenshot on failure.
			}
		}
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		final Locator accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL).first();
		if (isVisible(accountOption, 10000)) {
			accountOption.click();
			waitForUi(page);
			waitForUi(appPage);
		}
	}

	private void clickTextAndWait(final Page page, final String text) {
		final Locator target = firstVisibleLocator("text '" + text + "'", page.getByText(text).first(),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)).first(),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)).first());
		target.click();
		waitForUi(page);
	}

	private Locator firstVisibleLocator(final String description, final Locator... locators) {
		for (final Locator locator : locators) {
			if (isVisible(locator, 4000)) {
				return locator;
			}
		}
		throw new AssertionError("Could not find visible element for " + description + ".");
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout((double) timeoutMs));
			return locator.isVisible();
		} catch (PlaywrightException hiddenOrMissing) {
			return false;
		}
	}

	private void assertVisible(final Locator locator, final String message) {
		assertTrue(message, isVisible(locator, 10000));
	}

	private void assertHidden(final Locator locator, final String message) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(10000));
			assertTrue(message, true);
		} catch (PlaywrightException notHidden) {
			fail(message);
		}
	}

	private boolean containsLikelyUserName(final String text) {
		final String[] lines = text.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.length() < 3 || line.length() > 60) {
				continue;
			}
			final String lower = line.toLowerCase();
			if (line.contains("@")
					|| lower.contains("información general")
					|| lower.contains("detalles de la cuenta")
					|| lower.contains("business plan")
					|| lower.contains("cambiar plan")) {
				continue;
			}
			if (Pattern.compile("[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,} [A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}").matcher(line).find()) {
				return true;
			}
		}
		return false;
	}

	private void takeScreenshot(final Page page, final String filename, final boolean fullPage) {
		Files.createDirectories(OUTPUT_DIR);
		page.screenshot(new Page.ScreenshotOptions().setPath(OUTPUT_DIR.resolve(filename)).setFullPage(fullPage));
	}

	private void writeFinalReport() throws IOException {
		final List<String> reportFields = List.of(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Información General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Términos y Condiciones",
				"Política de Privacidad");

		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Test Report").append(System.lineSeparator());
		builder.append("Generated at: ").append(Instant.now()).append(System.lineSeparator());
		builder.append(System.lineSeparator());

		for (final String field : reportFields) {
			builder.append(field)
					.append(": ")
					.append(finalReport.getOrDefault(field, "NOT_EXECUTED"))
					.append(System.lineSeparator());
		}

		builder.append(System.lineSeparator());
		builder.append("Términos y Condiciones final URL: ").append(terminosUrl).append(System.lineSeparator());
		builder.append("Política de Privacidad final URL: ").append(privacidadUrl).append(System.lineSeparator());
		builder.append("Evidence directory: ").append(OUTPUT_DIR.toAbsolutePath()).append(System.lineSeparator());

		final Path reportPath = OUTPUT_DIR.resolve("final-report.txt");
		Files.writeString(reportPath, builder.toString(),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

		System.out.println(builder);
	}

	private boolean headless() {
		final String headless = env(HEADLESS_ENV);
		if (headless == null || headless.isBlank()) {
			return true;
		}
		return Boolean.parseBoolean(headless);
	}

	private String env(final String key) {
		return System.getenv(key);
	}

	private String sanitize(final String input) {
		return input.toLowerCase().replaceAll("[^a-z0-9]+", "-");
	}

	@FunctionalInterface
	private interface StepRunnable {
		void run() throws Exception;
	}

	private static final class LegalResult {
		private final String url;

		private LegalResult(final String url) {
			this.url = url;
		}
	}
}
