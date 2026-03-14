package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * End-to-end test for SaleADS "Mi Negocio" workflow.
 *
 * <p>Execution notes:
 * <ul>
 *   <li>Set RUN_SALEADS_E2E=true to run this test. Otherwise it is skipped.</li>
 *   <li>If SALEADS_WS_ENDPOINT is provided, the test connects to that browser instance and reuses
 *       the active page (helpful when the login page is already open).</li>
 *   <li>If running with a local browser, provide SALEADS_LOGIN_URL to avoid URL hardcoding.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final double UI_TIMEOUT_MS = 30000;
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private final Map<String, String> stepResults = new LinkedHashMap<>();
	private Path evidenceDir;
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		Assume.assumeTrue("Set RUN_SALEADS_E2E=true to execute this test.",
				isTruthy(System.getenv("RUN_SALEADS_E2E")));

		evidenceDir = createEvidenceDirectory();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = createBrowser(playwright);

			try {
				final BrowserContext context = getOrCreateContext(browser);
				context.setDefaultTimeout(UI_TIMEOUT_MS);
				final Page page = getOrCreatePage(context);
				page.setDefaultTimeout(UI_TIMEOUT_MS);

				prepareStartingPage(page);

				runStep(page, "Login", () -> stepLoginWithGoogle(page));
				runStep(page, "Mi Negocio menu", () -> stepOpenMiNegocioMenu(page));
				runStep(page, "Agregar Negocio modal", () -> stepValidateAgregarNegocioModal(page));
				runStep(page, "Administrar Negocios view", () -> stepOpenAdministrarNegocios(page));
				runStep(page, "Informacion General", () -> stepValidateInformacionGeneral(page));
				runStep(page, "Detalles de la Cuenta", () -> stepValidateDetallesCuenta(page));
				runStep(page, "Tus Negocios", () -> stepValidateTusNegocios(page));
				runStep(page, "Terminos y Condiciones",
						() -> termsUrl = stepValidateLegalPage(page, "T\u00e9rminos y Condiciones",
								Pattern.compile("(?i)t[e\u00e9]rminos\\s+y\\s+condiciones"), "terminos"));
				runStep(page, "Politica de Privacidad",
						() -> privacyUrl = stepValidateLegalPage(page, "Pol\u00edtica de Privacidad",
								Pattern.compile("(?i)pol[i\u00ed]tica\\s+de\\s+privacidad"), "politica_privacidad"));
			} finally {
				browser.close();
			}
		}

		writeFinalReport();
		assertAllStepsPassed();
	}

	private void prepareStartingPage(final Page page) {
		final String loginUrl = trimToNull(System.getenv("SALEADS_LOGIN_URL"));
		if (loginUrl != null) {
			page.navigate(loginUrl);
			waitForUi(page);
			return;
		}

		if ("about:blank".equalsIgnoreCase(page.url())) {
			throw new AssertionError(
					"No login page is open. Provide SALEADS_LOGIN_URL or connect with SALEADS_WS_ENDPOINT.");
		}
		waitForUi(page);
	}

	private Browser createBrowser(final Playwright playwright) {
		final String wsEndpoint = trimToNull(System.getenv("SALEADS_WS_ENDPOINT"));
		if (wsEndpoint != null) {
			return playwright.chromium().connect(wsEndpoint);
		}

		final boolean headless = !isFalsy(System.getenv("SALEADS_HEADFUL"));
		return playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
	}

	private BrowserContext getOrCreateContext(final Browser browser) {
		if (!browser.contexts().isEmpty()) {
			return browser.contexts().get(0);
		}
		return browser.newContext();
	}

	private Page getOrCreatePage(final BrowserContext context) {
		if (!context.pages().isEmpty()) {
			return context.pages().get(0);
		}
		return context.newPage();
	}

	private void stepLoginWithGoogle(final Page page) {
		clickAnyVisibleText(page, Arrays.asList(
				"Sign in with Google",
				"Sign in Google",
				"Continuar con Google",
				"Iniciar sesi\u00f3n con Google",
				"Iniciar sesion con Google",
				"Google"));

		final Locator accountOption = page.getByText("juanlucasbarbiergarzon@gmail.com",
				new Page.GetByTextOptions().setExact(false)).first();
		if (isVisible(accountOption, 8000)) {
			accountOption.click();
			waitForUi(page);
		}

		// Dashboard/main app checks.
		assertVisibleText(page, "Negocio");
		assertAnyVisible(page, "Left sidebar should be visible after login.",
				page.locator("aside").first(),
				page.getByRole(AriaRole.NAVIGATION).first(),
				page.locator("nav").first());

		takeScreenshot(page, "01_dashboard_loaded.png", false);
	}

	private void stepOpenMiNegocioMenu(final Page page) {
		assertVisibleText(page, "Negocio");
		clickAnyVisibleText(page, Arrays.asList("Mi Negocio"));
		assertVisibleText(page, "Agregar Negocio");
		assertVisibleText(page, "Administrar Negocios");
		takeScreenshot(page, "02_mi_negocio_menu_expanded.png", false);
	}

	private void stepValidateAgregarNegocioModal(final Page page) {
		clickAnyVisibleText(page, Arrays.asList("Agregar Negocio"));
		assertVisibleText(page, "Crear Nuevo Negocio");
		assertVisibleText(page, "Tienes 2 de 3 negocios");

		final Locator nombreField = firstVisibleLocator(
				page.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)).first(),
				page.getByPlaceholder("Nombre del Negocio", new Page.GetByPlaceholderOptions().setExact(false)).first(),
				page.locator("input[name*='negocio'], input[id*='negocio']").first());
		assertTrue("Input field 'Nombre del Negocio' must exist.", isVisible(nombreField, UI_TIMEOUT_MS));

		assertAnyVisible(page, "Buttons 'Cancelar' and 'Crear Negocio' must be visible.",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first());
		assertAnyVisible(page, "Buttons 'Cancelar' and 'Crear Negocio' must be visible.",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")).first());

		takeScreenshot(page, "03_agregar_negocio_modal.png", false);
		nombreField.fill("Negocio Prueba Automatizacion");
		clickAnyVisibleText(page, Arrays.asList("Cancelar"));
	}

	private void stepOpenAdministrarNegocios(final Page page) {
		if (!isVisible(page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)).first(), 3000)) {
			clickAnyVisibleText(page, Arrays.asList("Mi Negocio"));
		}

		clickAnyVisibleText(page, Arrays.asList("Administrar Negocios"));
		assertVisibleText(page, "Informacion General");
		assertVisibleText(page, "Detalles de la Cuenta");
		assertVisibleText(page, "Tus Negocios");
		assertVisibleText(page, "Seccion Legal");

		takeScreenshot(page, "04_administrar_negocios_view.png", true);
	}

	private void stepValidateInformacionGeneral(final Page page) {
		assertVisibleText(page, "Informacion General");
		assertVisibleText(page, "BUSINESS PLAN");
		assertVisibleText(page, "Cambiar Plan");

		final Locator section = findSectionByHeading(page, "Informacion General");
		final String sectionText = safeText(section);
		assertTrue("User email should be visible in 'Informacion General'.", containsEmail(sectionText));
		assertTrue("User name should be visible in 'Informacion General'.", containsProbableName(sectionText));
	}

	private void stepValidateDetallesCuenta(final Page page) {
		assertVisibleText(page, "Detalles de la Cuenta");
		assertVisibleText(page, "Cuenta creada");
		assertVisibleText(page, "Estado activo");
		assertVisibleText(page, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios(final Page page) {
		assertVisibleText(page, "Tus Negocios");
		assertVisibleText(page, "Agregar Negocio");
		assertVisibleText(page, "Tienes 2 de 3 negocios");

		final Locator section = findSectionByHeading(page, "Tus Negocios");
		final String sectionText = safeText(section);
		assertTrue("Business list should be visible in 'Tus Negocios'.", sectionText.trim().length() > 40);
	}

	private String stepValidateLegalPage(final Page appPage, final String linkText, final Pattern headingPattern,
			final String screenshotPrefix) {
		final String currentAppUrl = appPage.url();
		Page target = appPage;
		boolean openedNewTab = false;

		try {
			target = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(7000),
					() -> clickAnyVisibleTextNoWait(appPage, Arrays.asList(linkText)));
			openedNewTab = true;
		} catch (final PlaywrightException ignored) {
			clickAnyVisibleTextNoWait(appPage, Arrays.asList(linkText));
		}

		waitForUi(target);

		assertAnyVisible(target, "Expected legal heading is not visible.",
				target.getByText(headingPattern).first(),
				target.getByRole(AriaRole.HEADING,
						new Page.GetByRoleOptions().setName(headingPattern)).first());
		final String bodyText = safeText(target.locator("body").first());
		assertTrue("Legal content text should be visible.", bodyText.trim().length() > 200);

		final String finalUrl = target.url();
		takeScreenshot(target, String.format("05_%s_page.png", screenshotPrefix), true);

		if (openedNewTab) {
			target.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!currentAppUrl.equals(finalUrl)) {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void runStep(final Page page, final String stepName, final StepAction action) {
		try {
			action.run();
			stepResults.put(stepName, "PASS");
		} catch (final Throwable throwable) {
			stepResults.put(stepName, "FAIL: " + oneLine(throwable.getMessage()));
			takeScreenshot(page, "fail_" + slug(stepName) + ".png", true);
		}
	}

	private void assertVisibleText(final Page page, final String textOrAsciiFallback) {
		final List<String> variants = textVariants(textOrAsciiFallback);
		for (final String variant : variants) {
			final Locator candidate = page.getByText(variant, new Page.GetByTextOptions().setExact(false)).first();
			if (isVisible(candidate, 5000)) {
				return;
			}
		}
		throw new AssertionError("Expected text not visible: " + textOrAsciiFallback);
	}

	private void clickAnyVisibleText(final Page page, final List<String> texts) {
		clickAnyVisibleTextNoWait(page, texts);
		waitForUi(page);
	}

	private void clickAnyVisibleTextNoWait(final Page page, final List<String> texts) {
		for (final String text : texts) {
			for (final String variant : textVariants(text)) {
				final Locator button = page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(variant)).first();
				if (isVisible(button, 3000)) {
					button.click();
					return;
				}

				final Locator link = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(variant)).first();
				if (isVisible(link, 3000)) {
					link.click();
					return;
				}

				final Locator generic = page.getByText(variant, new Page.GetByTextOptions().setExact(false)).first();
				if (isVisible(generic, 3000)) {
					generic.click();
					return;
				}
			}
		}

		final Locator googleFallback = page
				.locator("button:has-text('Google'), a:has-text('Google'), [role='button']:has-text('Google')").first();
		if (isVisible(googleFallback, 3000)) {
			googleFallback.click();
			return;
		}

		throw new AssertionError("Could not find clickable element by visible text: " + texts);
	}

	private void assertAnyVisible(final Page page, final String message, final Locator... candidates) {
		for (final Locator locator : candidates) {
			if (isVisible(locator, 3000)) {
				return;
			}
		}
		throw new AssertionError(message);
	}

	private Locator firstVisibleLocator(final Locator... candidates) {
		for (final Locator locator : candidates) {
			if (isVisible(locator, 3000)) {
				return locator;
			}
		}
		return candidates[0];
	}

	private Locator findSectionByHeading(final Page page, final String headingText) {
		for (final String variant : textVariants(headingText)) {
			final Locator heading = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(variant)).first();
			if (isVisible(heading, 2000)) {
				return heading.locator("xpath=ancestor::*[self::section or self::div][1]");
			}
		}
		return page.locator("body");
	}

	private String safeText(final Locator locator) {
		try {
			return locator.innerText();
		} catch (final Throwable ignored) {
			return "";
		}
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final Throwable ignored) {
			return false;
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (final Throwable ignored) {
			// NETWORKIDLE can timeout in highly dynamic pages; use a short settle delay.
		}
		page.waitForTimeout(750);
	}

	private void takeScreenshot(final Page page, final String name, final boolean fullPage) {
		try {
			final Path output = evidenceDir.resolve(name);
			page.screenshot(new Page.ScreenshotOptions().setPath(output).setFullPage(fullPage));
		} catch (final Throwable ignored) {
			// Best-effort evidence capture.
		}
	}

	private void writeFinalReport() throws IOException {
		final List<String> orderedFields = Arrays.asList(
				"Login",
				"Mi Negocio menu",
				"Agregar Negocio modal",
				"Administrar Negocios view",
				"Informacion General",
				"Detalles de la Cuenta",
				"Tus Negocios",
				"Terminos y Condiciones",
				"Politica de Privacidad");

		final StringBuilder report = new StringBuilder();
		report.append("Test Name: ").append(TEST_NAME).append('\n');
		report.append("Generated: ").append(LocalDateTime.now()).append('\n');
		report.append('\n');
		report.append("Step Results:\n");

		for (final String field : orderedFields) {
			final String result = stepResults.getOrDefault(field, "NOT RUN");
			report.append("- ").append(field).append(": ").append(result).append('\n');
		}

		report.append('\n');
		report.append("Evidence:\n");
		report.append("- Terminos y Condiciones URL: ").append(termsUrl).append('\n');
		report.append("- Politica de Privacidad URL: ").append(privacyUrl).append('\n');
		report.append("- Screenshot directory: ").append(evidenceDir.toAbsolutePath()).append('\n');

		final Path reportFile = evidenceDir.resolve("final_report.txt");
		Files.write(reportFile, report.toString().getBytes(StandardCharsets.UTF_8));
	}

	private void assertAllStepsPassed() {
		final StringBuilder failures = new StringBuilder();
		for (final Map.Entry<String, String> entry : stepResults.entrySet()) {
			if (!entry.getValue().startsWith("PASS")) {
				failures.append(entry.getKey()).append(" -> ").append(entry.getValue()).append('\n');
			}
		}

		if (failures.length() > 0) {
			fail("Workflow failed:\n" + failures);
		}
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(TS_FORMAT);
		final Path path = Paths.get("target", "saleads-evidence", TEST_NAME, timestamp);
		Files.createDirectories(path);
		return path;
	}

	private List<String> textVariants(final String value) {
		final String lower = value.toLowerCase(Locale.ROOT);
		switch (lower) {
		case "informacion general":
			return Arrays.asList("Informacion General", "Informaci\u00f3n General");
		case "seccion legal":
			return Arrays.asList("Seccion Legal", "Secci\u00f3n Legal");
		case "iniciar sesion con google":
			return Arrays.asList("Iniciar sesion con Google", "Iniciar sesi\u00f3n con Google");
		case "terminos y condiciones":
			return Arrays.asList("Terminos y Condiciones", "T\u00e9rminos y Condiciones");
		case "politica de privacidad":
			return Arrays.asList("Politica de Privacidad", "Pol\u00edtica de Privacidad");
		default:
			return Arrays.asList(value);
		}
	}

	private boolean containsEmail(final String text) {
		return Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(text).find();
	}

	private boolean containsProbableName(final String text) {
		for (final String line : text.split("\\R")) {
			final String candidate = line.trim();
			if (candidate.isEmpty()) {
				continue;
			}

			final String lowered = candidate.toLowerCase(Locale.ROOT);
			if (candidate.contains("@")
					|| lowered.contains("informacion")
					|| lowered.contains("business plan")
					|| lowered.contains("cambiar plan")) {
				continue;
			}

			if (candidate.length() >= 4 && Pattern.compile(".*[A-Za-z].*").matcher(candidate).matches()) {
				return true;
			}
		}
		return false;
	}

	private String slug(final String raw) {
		return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("_+", "_");
	}

	private String oneLine(final String input) {
		if (input == null || input.trim().isEmpty()) {
			return "unknown error";
		}
		return input.replaceAll("\\s+", " ").trim();
	}

	private String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private boolean isTruthy(final String value) {
		if (value == null) {
			return false;
		}
		final String normalized = value.trim().toLowerCase(Locale.ROOT);
		return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "y".equals(normalized);
	}

	private boolean isFalsy(final String value) {
		if (value == null) {
			return false;
		}
		final String normalized = value.trim().toLowerCase(Locale.ROOT);
		return "0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized) || "n".equals(normalized);
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
