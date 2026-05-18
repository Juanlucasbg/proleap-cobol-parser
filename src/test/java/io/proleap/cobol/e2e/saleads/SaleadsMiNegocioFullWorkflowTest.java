package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * End-to-end SaleADS "Mi Negocio" flow with screenshots and final PASS/FAIL report.
 *
 * Required environment variable:
 * - SALEADS_LOGIN_URL: Login URL for the current environment (dev/staging/prod).
 *
 * Optional environment variable:
 * - SALEADS_EXPECTED_USER_NAME: expected display name to validate in "Información General".
 *
 * Optional JVM properties:
 * - saleads.headless=true|false
 * - saleads.slowMoMs=<milliseconds>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final long DEFAULT_TIMEOUT_MS = 20000;
	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-evidence");
	private static final Path REPORT_FILE = EVIDENCE_DIR.resolve("saleads-mi-negocio-final-report.txt");

	private final Map<String, String> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepNotes = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		Files.createDirectories(EVIDENCE_DIR);

		final String loginUrl = readRequiredEnv("SALEADS_LOGIN_URL");
		final boolean headless = Boolean.parseBoolean(readConfig("saleads.headless", "true"));
		final float slowMoMs = Float.parseFloat(readConfig("saleads.slowMoMs", "0"));
		final String expectedUserName = readOptionalEnv("SALEADS_EXPECTED_USER_NAME");

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless)
					.setSlowMo(slowMoMs));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page page = context.newPage();

			page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(LoadState.DOMCONTENTLOADED));
			waitForUi(page);

			runStep("Login", () -> {
				final Locator loginButton = firstVisible(page, 8000, "button:has-text(\"Sign in with Google\")",
						"button:has-text(\"Iniciar sesión con Google\")", "button:has-text(\"Continuar con Google\")",
						"[role=\"button\"]:has-text(\"Google\")");

				if (loginButton != null) {
					clickAndWait(page, loginButton);
					selectGoogleAccountIfPresent(page);

					final List<Page> pagesSnapshot = new ArrayList<>(context.pages());
					for (final Page candidate : pagesSnapshot) {
						if (candidate != page) {
							selectGoogleAccountIfPresent(candidate);
						}
					}
				}

				page.bringToFront();
				waitForUi(page);

				assertVisible(page, "main application interface",
						"text=Negocio", "text=Mi Negocio", "main", "aside", "nav");
				assertVisible(page, "left sidebar navigation",
						"aside:has-text(\"Negocio\")", "nav:has-text(\"Negocio\")", "text=Negocio");
				captureScreenshot(page, "01-dashboard-loaded.png", false);
			});

			runStep("Mi Negocio menu", () -> {
				clickIfVisibleByText(page, "Negocio");
				clickByText(page, "Mi Negocio");
				assertVisible(page, "'Agregar Negocio' option", "text=Agregar Negocio");
				assertVisible(page, "'Administrar Negocios' option", "text=Administrar Negocios");
				captureScreenshot(page, "02-mi-negocio-menu-expanded.png", false);
			});

			runStep("Agregar Negocio modal", () -> {
				clickByText(page, "Agregar Negocio");
				assertVisible(page, "modal title 'Crear Nuevo Negocio'",
						"text=Crear Nuevo Negocio", "h2:has-text(\"Crear Nuevo Negocio\")", "h3:has-text(\"Crear Nuevo Negocio\")");
				assertVisible(page, "input field 'Nombre del Negocio'",
						"input[placeholder*=\"Nombre del Negocio\"]",
						"input[name*=\"negocio\"]",
						"label:has-text(\"Nombre del Negocio\") + input",
						"text=Nombre del Negocio");
				assertVisible(page, "text 'Tienes 2 de 3 negocios'", "text=Tienes 2 de 3 negocios");
				assertVisible(page, "button 'Cancelar'", "button:has-text(\"Cancelar\")", "[role=\"button\"]:has-text(\"Cancelar\")");
				assertVisible(page, "button 'Crear Negocio'",
						"button:has-text(\"Crear Negocio\")", "[role=\"button\"]:has-text(\"Crear Negocio\")");

				captureScreenshot(page, "03-agregar-negocio-modal.png", false);

				final Locator businessNameInput = firstVisible(page, 4000, "input[placeholder*=\"Nombre del Negocio\"]",
						"input[name*=\"negocio\"]", "label:has-text(\"Nombre del Negocio\") + input");
				if (businessNameInput != null) {
					businessNameInput.fill("Negocio Prueba Automatizacion");
					waitForUi(page);
				}

				clickByText(page, "Cancelar");
			});

			runStep("Administrar Negocios view", () -> {
				if (!isTextVisible(page, "Administrar Negocios", 3000)) {
					clickByText(page, "Mi Negocio");
				}

				clickByText(page, "Administrar Negocios");
				assertVisible(page, "section 'Información General'", "text=Información General");
				assertVisible(page, "section 'Detalles de la Cuenta'", "text=Detalles de la Cuenta");
				assertVisible(page, "section 'Tus Negocios'", "text=Tus Negocios");
				assertVisible(page, "section 'Sección Legal'", "text=Sección Legal");
				captureScreenshot(page, "04-administrar-negocios-full.png", true);
			});

			runStep("Información General", () -> {
				if (expectedUserName != null && !expectedUserName.isBlank()) {
					assertVisible(page, "user name", "text=" + expectedUserName);
				} else {
					assertVisible(page, "user name label", "text=Nombre", "text=Usuario", "text=Perfil");
				}

				assertVisible(page, "user email", "text=" + GOOGLE_ACCOUNT_EMAIL,
						"text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/");
				assertVisible(page, "text 'BUSINESS PLAN'", "text=BUSINESS PLAN");
				assertVisible(page, "button 'Cambiar Plan'",
						"button:has-text(\"Cambiar Plan\")", "[role=\"button\"]:has-text(\"Cambiar Plan\")");
			});

			runStep("Detalles de la Cuenta", () -> {
				assertVisible(page, "'Cuenta creada' text", "text=Cuenta creada");
				assertVisible(page, "'Estado activo' text", "text=Estado activo");
				assertVisible(page, "'Idioma seleccionado' text", "text=Idioma seleccionado");
			});

			runStep("Tus Negocios", () -> {
				assertVisible(page, "business list", "text=Tus Negocios", "[data-testid*=\"business\"]", "section:has-text(\"Tus Negocios\")");
				assertVisible(page, "button 'Agregar Negocio'",
						"button:has-text(\"Agregar Negocio\")", "[role=\"button\"]:has-text(\"Agregar Negocio\")");
				assertVisible(page, "text 'Tienes 2 de 3 negocios'", "text=Tienes 2 de 3 negocios");
			});

			runStep("Términos y Condiciones", () -> {
				final String finalUrl = validateLegalLink(context, page, "Términos y Condiciones",
						"Términos y Condiciones", "08-terminos-y-condiciones.png");
				capturedUrls.put("Términos y Condiciones URL", finalUrl);
			});

			runStep("Política de Privacidad", () -> {
				final String finalUrl = validateLegalLink(context, page, "Política de Privacidad",
						"Política de Privacidad", "09-politica-de-privacidad.png");
				capturedUrls.put("Política de Privacidad URL", finalUrl);
			});

			writeFinalReport();

			final List<String> failedSteps = new ArrayList<>();
			for (final Map.Entry<String, String> entry : stepStatus.entrySet()) {
				if ("FAIL".equals(entry.getValue())) {
					failedSteps.add(entry.getKey());
				}
			}

			assertTrue("Some validations failed: " + failedSteps + ". See report: " + REPORT_FILE.toAbsolutePath(),
					failedSteps.isEmpty());
		}
	}

	private String validateLegalLink(final BrowserContext context, final Page appPage, final String linkText,
			final String expectedHeading, final String screenshotFileName) {
		final List<Page> beforePages = new ArrayList<>(context.pages());
		final String appUrlBefore = appPage.url();

		clickByTextNoWait(appPage, linkText);
		waitForUi(appPage);

		Page targetPage = appPage;
		for (final Page pageCandidate : context.pages()) {
			if (!beforePages.contains(pageCandidate)) {
				targetPage = pageCandidate;
				break;
			}
		}

		targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUi(targetPage);

		assertVisible(targetPage, expectedHeading + " heading", "text=" + expectedHeading, "h1:has-text(\"" + expectedHeading
				+ "\")", "h2:has-text(\"" + expectedHeading + "\")");
		assertVisible(targetPage, "legal content text", "article", "main p", "section p", "p");
		captureScreenshot(targetPage, screenshotFileName, true);

		final String finalUrl = targetPage.url();

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!appUrlBefore.equals(appPage.url())) {
			appPage.goBack(new Page.GoBackOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void runStep(final String stepName, final CheckedRunnable checkedRunnable) {
		try {
			checkedRunnable.run();
			stepStatus.put(stepName, "PASS");
		} catch (final Throwable throwable) {
			stepStatus.put(stepName, "FAIL");
			stepNotes.put(stepName, throwable.getMessage() == null ? throwable.toString() : throwable.getMessage());
		}
	}

	private void clickByText(final Page page, final String text) {
		final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();

		if (waitVisible(exact, 8000)) {
			clickAndWait(page, exact);
			return;
		}

		final Locator fuzzy = page.getByText(text).first();
		if (waitVisible(fuzzy, DEFAULT_TIMEOUT_MS)) {
			clickAndWait(page, fuzzy);
			return;
		}

		throw new AssertionError("Unable to find clickable text: " + text);
	}

	private void clickByTextNoWait(final Page page, final String text) {
		final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();

		if (waitVisible(exact, 5000)) {
			exact.click();
			return;
		}

		final Locator fuzzy = page.getByText(text).first();
		if (waitVisible(fuzzy, DEFAULT_TIMEOUT_MS)) {
			fuzzy.click();
			return;
		}

		throw new AssertionError("Unable to find clickable text: " + text);
	}

	private void clickIfVisibleByText(final Page page, final String text) {
		final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
		if (waitVisible(exact, 2500)) {
			clickAndWait(page, exact);
		}
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click();
		waitForUi(page);
	}

	private void assertVisible(final Page page, final String description, final String... selectors) {
		final Locator locator = firstVisible(page, DEFAULT_TIMEOUT_MS, selectors);
		if (locator == null) {
			throw new AssertionError("Expected visible element was not found: " + description);
		}
	}

	private Locator firstVisible(final Page page, final long timeoutMs, final String... selectors) {
		for (final String selector : selectors) {
			final Locator locator = page.locator(selector).first();
			if (waitVisible(locator, timeoutMs)) {
				return locator;
			}
		}

		return null;
	}

	private boolean waitVisible(final Locator locator, final long timeoutMs) {
		try {
			locator.waitFor(
					new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final PlaywrightException playwrightException) {
			return false;
		}
	}

	private boolean isTextVisible(final Page page, final String text, final long timeoutMs) {
		return waitVisible(page.getByText(text).first(), timeoutMs);
	}

	private void selectGoogleAccountIfPresent(final Page page) {
		try {
			final Locator accountOption = firstVisible(page, 8000,
					"div[role=\"button\"]:has-text(\"" + GOOGLE_ACCOUNT_EMAIL + "\")",
					"li:has-text(\"" + GOOGLE_ACCOUNT_EMAIL + "\")",
					"div[data-email=\"" + GOOGLE_ACCOUNT_EMAIL + "\"]",
					"text=" + GOOGLE_ACCOUNT_EMAIL);

			if (accountOption != null) {
				accountOption.click();
				waitForUi(page);
			}
		} catch (final PlaywrightException ignored) {
			// Google flow varies by session state; this is intentionally best-effort.
		}
	}

	private void waitForUi(final Page page) {
		page.waitForTimeout(800);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (final PlaywrightException ignored) {
			// SPAs can keep network open; continue with the next assertion.
		}
		page.waitForTimeout(500);
	}

	private void captureScreenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(EVIDENCE_DIR.resolve(fileName)).setFullPage(fullPage));
	}

	private String readRequiredEnv(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Required environment variable is missing: " + key);
		}
		return value;
	}

	private String readOptionalEnv(final String key) {
		return System.getenv(key);
	}

	private String readConfig(final String propertyName, final String defaultValue) {
		final String fromProperty = System.getProperty(propertyName);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}
		return defaultValue;
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Workflow Report").append('\n');
		builder.append("Generated at: ").append(Instant.now()).append('\n');
		builder.append('\n');

		appendStepLine(builder, "Login");
		appendStepLine(builder, "Mi Negocio menu");
		appendStepLine(builder, "Agregar Negocio modal");
		appendStepLine(builder, "Administrar Negocios view");
		appendStepLine(builder, "Información General");
		appendStepLine(builder, "Detalles de la Cuenta");
		appendStepLine(builder, "Tus Negocios");
		appendStepLine(builder, "Términos y Condiciones");
		appendStepLine(builder, "Política de Privacidad");

		builder.append('\n');
		builder.append("Evidence directory: ").append(EVIDENCE_DIR.toAbsolutePath()).append('\n');

		if (!capturedUrls.isEmpty()) {
			builder.append('\n');
			for (final Map.Entry<String, String> entry : capturedUrls.entrySet()) {
				builder.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		if (!stepNotes.isEmpty()) {
			builder.append('\n').append("Failure details").append('\n');
			for (final Map.Entry<String, String> entry : stepNotes.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		Files.writeString(REPORT_FILE, builder.toString(), StandardCharsets.UTF_8);
	}

	private void appendStepLine(final StringBuilder builder, final String stepName) {
		final String status = stepStatus.getOrDefault(stepName, "FAIL");
		builder.append(stepName).append(": ").append(status).append('\n');
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
