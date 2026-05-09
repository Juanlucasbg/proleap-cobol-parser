package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio" module.
 *
 * <p>
 * This test is intentionally environment-agnostic and never hardcodes a domain.
 * Provide a login URL from the active environment via:
 * </p>
 *
 * <ul>
 * <li>System property: -Dsaleads.login.url=...</li>
 * <li>Environment variable: SALEADS_LOGIN_URL=...</li>
 * </ul>
 *
 * <p>
 * Execution is opt-in to keep default CI flows stable:
 * </p>
 * <ul>
 * <li>System property: -Dsaleads.e2e.enabled=true</li>
 * <li>Environment variable: SALEADS_E2E_ENABLED=true</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final int DEFAULT_TIMEOUT_MS = 10000;
	private static final int SHORT_TIMEOUT_MS = 2500;
	private static final int STEP_WAIT_MS = 800;
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		Assume.assumeTrue(
				"Enable with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true.",
				Boolean.parseBoolean(readConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false")));

		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", "");
		Assert.assertFalse(
				"Missing SaleADS login URL. Provide -Dsaleads.login.url or SALEADS_LOGIN_URL for the target environment.",
				loginUrl.isBlank());

		final boolean headless = Boolean
				.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"));
		final String expectedUserName = readConfig("saleads.expected.user.name", "SALEADS_EXPECTED_USER_NAME", "");
		final String expectedUserEmail = readConfig("saleads.expected.user.email", "SALEADS_EXPECTED_USER_EMAIL", "");
		final Path evidenceDir = createEvidenceDirectory();
		final LinkedHashMap<String, StepStatus> report = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser
					.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
			final Page appPage = context.newPage();
			appPage.navigate(loginUrl);
			waitForUi(appPage);

			// 1) Login with Google
			executeStep(report, "Login", () -> {
				final Locator loginButton = firstVisible(appPage, DEFAULT_TIMEOUT_MS, "text=/sign in with google/i",
						"text=/login with google/i", "text=/continuar con google/i", "text=/google/i");
				final Page possibleGooglePopup = clickAndDetectNewPage(appPage, loginButton);
				if (possibleGooglePopup != null) {
					waitForUi(possibleGooglePopup);
					selectGoogleAccountIfVisible(possibleGooglePopup);
					if (!possibleGooglePopup.isClosed()) {
						possibleGooglePopup.waitForTimeout(STEP_WAIT_MS);
					}
				} else {
					selectGoogleAccountIfVisible(appPage);
				}

				waitForUi(appPage);
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Negocio", "text=Mi Negocio", "text=Dashboard",
						"text=Inicio");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "aside", "nav", "text=Negocio");
				screenshot(appPage, evidenceDir.resolve("01-dashboard-loaded.png"), false);
			});

			// 2) Open Mi Negocio menu
			executeStep(report, "Mi Negocio menu", () -> {
				final Locator miNegocio = firstVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Mi Negocio");
				clickAndWait(appPage, miNegocio);

				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Agregar Negocio");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Administrar Negocios");
				screenshot(appPage, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
			});

			// 3) Validate Agregar Negocio modal
			executeStep(report, "Agregar Negocio modal", () -> {
				final Locator agregarNegocio = firstVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Agregar Negocio");
				clickAndWait(appPage, agregarNegocio);

				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Crear Nuevo Negocio");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Nombre del Negocio");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Tienes 2 de 3 negocios");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Cancelar");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Crear Negocio");
				screenshot(appPage, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

				// Optional modal interaction requested by workflow.
				final Locator nombreInput = firstVisible(appPage, DEFAULT_TIMEOUT_MS, "input[placeholder*='Nombre']",
						"input[name*='nombre' i]", "input[id*='nombre' i]", "input[type='text']");
				nombreInput.fill("Negocio Prueba Automatizacion");
				clickAndWait(appPage, firstVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Cancelar"));
			});

			// 4) Open Administrar Negocios
			executeStep(report, "Administrar Negocios view", () -> {
				expandMiNegocioIfCollapsed(appPage);
				clickAndWait(appPage, firstVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Administrar Negocios"));
				waitForUi(appPage);

				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Informacion General", "text=Información General");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Detalles de la Cuenta");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Tus Negocios");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Seccion Legal", "text=Sección Legal");
				screenshot(appPage, evidenceDir.resolve("04-administrar-negocios-full-page.png"), true);
			});

			// 5) Validate Información General
			executeStep(report, "Información General", () -> {
				if (!expectedUserName.isBlank()) {
					assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=" + expectedUserName);
				} else {
					assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Nombre", "text=Usuario", "text=Name");
				}

				if (!expectedUserEmail.isBlank()) {
					assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=" + expectedUserEmail);
				} else {
					assertTrue("User email was not detected on the page.",
							EMAIL_PATTERN.matcher(appPage.locator("body").innerText()).find());
				}

				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=BUSINESS PLAN");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Cambiar Plan");
			});

			// 6) Validate Detalles de la Cuenta
			executeStep(report, "Detalles de la Cuenta", () -> {
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Cuenta creada");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Estado activo");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Idioma seleccionado");
			});

			// 7) Validate Tus Negocios
			executeStep(report, "Tus Negocios", () -> {
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Tus Negocios");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Agregar Negocio");
				assertAnyVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Tienes 2 de 3 negocios");
			});

			// 8) Validate Términos y Condiciones
			executeStep(report, "Términos y Condiciones", () -> {
				final String finalUrl = validateLegalLink(appPage, "Términos y Condiciones", "Términos y Condiciones",
						evidenceDir.resolve("08-terminos-y-condiciones.png"));
				report.put("Términos y Condiciones", StepStatus.pass("URL: " + finalUrl));
			});

			// 9) Validate Política de Privacidad
			executeStep(report, "Política de Privacidad", () -> {
				final String finalUrl = validateLegalLink(appPage, "Política de Privacidad", "Política de Privacidad",
						evidenceDir.resolve("09-politica-de-privacidad.png"));
				report.put("Política de Privacidad", StepStatus.pass("URL: " + finalUrl));
			});
		}

		final String summary = formatSummary(report);
		System.out.println(summary);
		assertTrue("One or more workflow validations failed:\n" + summary,
				report.values().stream().allMatch(StepStatus::passed));
	}

	private void executeStep(final Map<String, StepStatus> report, final String stepName, final CheckedRunnable step) {
		try {
			step.run();
			report.putIfAbsent(stepName, StepStatus.pass("Validation passed"));
		} catch (final Throwable error) {
			report.put(stepName, StepStatus.fail(error.getMessage() == null ? error.toString() : error.getMessage()));
		}
	}

	private void expandMiNegocioIfCollapsed(final Page appPage) {
		if (!isVisible(appPage, "text=Administrar Negocios", SHORT_TIMEOUT_MS)) {
			clickAndWait(appPage, firstVisible(appPage, DEFAULT_TIMEOUT_MS, "text=Mi Negocio"));
		}
	}

	private String validateLegalLink(final Page appPage, final String linkText, final String headingText,
			final Path screenshotPath) {
		final String appUrlBeforeClick = appPage.url();
		final Locator link = firstVisible(appPage, DEFAULT_TIMEOUT_MS, "text=" + linkText);
		final Page newPage = clickAndDetectNewPage(appPage, link);
		final Page legalPage = newPage == null ? appPage : newPage;

		waitForUi(legalPage);
		assertAnyVisible(legalPage, DEFAULT_TIMEOUT_MS, "text=" + headingText);
		final String legalContent = legalPage.locator("body").innerText();
		assertTrue("Legal content text was not visible for '" + headingText + "'.", legalContent.length() > 120);
		screenshot(legalPage, screenshotPath, true);

		final String finalUrl = legalPage.url();
		if (newPage != null) {
			newPage.close();
			appPage.bringToFront();
		} else if (!appPage.url().equals(appUrlBeforeClick)) {
			appPage.navigate(appUrlBeforeClick);
			waitForUi(appPage);
		}
		return finalUrl;
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout((double) DEFAULT_TIMEOUT_MS));
		waitForUi(page);
	}

	private Page clickAndDetectNewPage(final Page sourcePage, final Locator clickTarget) {
		final BrowserContext context = sourcePage.context();
		final int pagesBefore = context.pages().size();
		clickAndWait(sourcePage, clickTarget);

		for (int i = 0; i < 10; i++) {
			if (context.pages().size() > pagesBefore) {
				return context.pages().get(context.pages().size() - 1);
			}
			sourcePage.waitForTimeout(300);
		}
		return null;
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		try {
			final Locator account = page.locator("text=" + GOOGLE_ACCOUNT_EMAIL).first();
			account.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE)
					.setTimeout((double) SHORT_TIMEOUT_MS));
			account.click(new Locator.ClickOptions().setTimeout((double) DEFAULT_TIMEOUT_MS));
			waitForUi(page);
		} catch (final PlaywrightException ignored) {
			// Continue when account picker is not shown.
		}
	}

	private void assertAnyVisible(final Page page, final int timeoutMs, final String... selectors) {
		for (final String selector : selectors) {
			if (isVisible(page, selector, timeoutMs)) {
				return;
			}
		}
		Assert.fail("None of the expected selectors were visible: " + String.join(", ", selectors));
	}

	private Locator firstVisible(final Page page, final int timeoutMs, final String... selectors) {
		for (final String selector : selectors) {
			final Locator locator = page.locator(selector).first();
			try {
				locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE)
						.setTimeout((double) timeoutMs));
				return locator;
			} catch (final PlaywrightException ignored) {
				// Try next selector.
			}
		}
		throw new AssertionError("No visible selector matched: " + String.join(", ", selectors));
	}

	private boolean isVisible(final Page page, final String selector, final int timeoutMs) {
		try {
			page.locator(selector).first()
					.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE)
							.setTimeout((double) timeoutMs));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(STEP_WAIT_MS);
	}

	private void screenshot(final Page page, final Path path, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private String readConfig(final String propertyKey, final String envKey, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private String formatSummary(final Map<String, StepStatus> report) {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio workflow report\n");
		builder.append("==================================\n");
		for (final Map.Entry<String, StepStatus> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ")
					.append(entry.getValue().passed() ? "PASS" : "FAIL");
			if (!entry.getValue().details().isBlank()) {
				builder.append(" (").append(entry.getValue().details()).append(")");
			}
			builder.append('\n');
		}
		return builder.toString();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepStatus {
		private final boolean passed;
		private final String details;

		private StepStatus(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details == null ? "" : details;
		}

		private static StepStatus pass(final String details) {
			return new StepStatus(true, details);
		}

		private static StepStatus fail(final String details) {
			return new StepStatus(false, details);
		}

		private boolean passed() {
			return passed;
		}

		private String details() {
			return details;
		}
	}
}
