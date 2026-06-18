package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * This test does not hardcode any environment URL. Provide SALEADS_LOGIN_URL in
 * environment variables to point to the login page of the current environment.
 */
public class SaleadsMiNegocioFullTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN = "Administrar Negocios view";
	private static final String STEP_INFO = "Informaci\u00F3n General";
	private static final String STEP_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "T\u00E9rminos y Condiciones";
	private static final String STEP_PRIVACY = "Pol\u00EDtica de Privacidad";

	private static final long DEFAULT_TIMEOUT_MS = 20_000;
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = trimToNull(System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue("Set SALEADS_LOGIN_URL with the current SaleADS environment login page.", loginUrl != null);

		final String googleAccountEmail = trimToNull(System.getenv("SALEADS_GOOGLE_ACCOUNT_EMAIL")) == null
				? "juanlucasbarbiergarzon@gmail.com"
				: trimToNull(System.getenv("SALEADS_GOOGLE_ACCOUNT_EMAIL"));
		final String expectedUserName = trimToNull(System.getenv("SALEADS_EXPECTED_USER_NAME"));
		final boolean headed = Boolean.parseBoolean(defaultValue(System.getenv("SALEADS_HEADED"), "false"));
		final int slowMoMs = parseInt(defaultValue(System.getenv("SALEADS_SLOW_MO_MS"), "150"), 150);

		final Path evidenceDir = createEvidenceDirectory();
		final LinkedHashMap<String, StepResult> results = initializeResults();
		final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();
		final List<String> executionNotes = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(!headed).setSlowMo((double) slowMoMs));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
			final Page page = context.newPage();
			final Page[] appPage = new Page[] { page };

			page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(LoadState.DOMCONTENTLOADED));
			waitForUiLoad(page);

			runStep(results, STEP_LOGIN, () -> {
				clickByAnySelector(appPage[0],
						Arrays.asList("button:has-text(\"Google\")", "[role=\"button\"]:has-text(\"Google\")",
								"a:has-text(\"Google\")", "button:has-text(\"Iniciar\")"));
				selectGoogleAccountIfVisible(context, googleAccountEmail);
				appPage[0] = waitForApplicationInterface(context);
				assertVisible(appPage[0], "aside, nav", "Left sidebar navigation is not visible.");
				captureScreenshot(appPage[0], evidenceDir.resolve("step-1-dashboard-loaded.png"), true);
			}, executionNotes);

			runStep(results, STEP_MENU, () -> {
				assertVisibleText(appPage[0], "Negocio");
				clickTextIfPresent(appPage[0], "Negocio");
				clickByText(appPage[0], "Mi Negocio");
				assertVisibleText(appPage[0], "Agregar Negocio");
				assertVisibleText(appPage[0], "Administrar Negocios");
				captureScreenshot(appPage[0], evidenceDir.resolve("step-2-mi-negocio-menu-expanded.png"), true);
			}, executionNotes);

			runStep(results, STEP_MODAL, () -> {
				clickByText(appPage[0], "Agregar Negocio");
				assertVisibleText(appPage[0], "Crear Nuevo Negocio");
				assertVisibleText(appPage[0], "Nombre del Negocio");
				assertVisibleText(appPage[0], "Tienes 2 de 3 negocios");
				assertVisibleText(appPage[0], "Cancelar");
				assertVisibleText(appPage[0], "Crear Negocio");

				captureScreenshot(appPage[0], evidenceDir.resolve("step-3-agregar-negocio-modal.png"), true);

				fillByAnySelector(appPage[0],
						Arrays.asList("input[placeholder*=\"Nombre del Negocio\"]", "input[name*=\"negocio\"]",
								"input[id*=\"negocio\"]"),
						"Negocio Prueba Automatizacion");
				clickByText(appPage[0], "Cancelar");
				assertNotVisibleText(appPage[0], "Crear Nuevo Negocio");
			}, executionNotes);

			runStep(results, STEP_ADMIN, () -> {
				if (!textIsVisible(appPage[0], "Administrar Negocios")) {
					clickByText(appPage[0], "Mi Negocio");
				}
				clickByText(appPage[0], "Administrar Negocios");
				assertVisibleAnyText(appPage[0], "Informaci\u00F3n General", "Informacion General");
				assertVisibleText(appPage[0], "Detalles de la Cuenta");
				assertVisibleText(appPage[0], "Tus Negocios");
				assertVisibleAnyText(appPage[0], "Secci\u00F3n Legal", "Seccion Legal");
				captureScreenshot(appPage[0], evidenceDir.resolve("step-4-administrar-negocios-page.png"), true);
			}, executionNotes);

			runStep(results, STEP_INFO, () -> {
				final String infoText = sectionText(appPage[0], "Informaci\u00F3n General", "Informacion General");
				assertTrue("User email is not visible in Informaci\u00F3n General.", EMAIL_PATTERN.matcher(infoText).find());
				assertVisibleText(appPage[0], "BUSINESS PLAN");
				assertVisibleText(appPage[0], "Cambiar Plan");

				if (expectedUserName != null) {
					assertTrue("Expected user name is not visible in Informaci\u00F3n General.",
							normalize(infoText).contains(normalize(expectedUserName)));
				} else {
					assertTrue("A user-name-like line is not visible in Informaci\u00F3n General.", hasNameLikeLine(infoText));
				}
			}, executionNotes);

			runStep(results, STEP_DETAILS, () -> {
				assertVisibleText(appPage[0], "Cuenta creada");
				assertVisibleText(appPage[0], "Estado activo");
				assertVisibleText(appPage[0], "Idioma seleccionado");
			}, executionNotes);

			runStep(results, STEP_BUSINESSES, () -> {
				assertVisibleText(appPage[0], "Tus Negocios");
				assertVisibleText(appPage[0], "Agregar Negocio");
				assertVisibleText(appPage[0], "Tienes 2 de 3 negocios");
			}, executionNotes);

			runStep(results, STEP_TERMS, () -> {
				final String finalUrl = validateLegalLink(appPage[0], context,
						Arrays.asList("T\u00E9rminos y Condiciones", "Terminos y Condiciones"),
						Arrays.asList("T\u00E9rminos y Condiciones", "Terminos y Condiciones"),
						evidenceDir.resolve("step-8-terminos-y-condiciones.png"));
				legalUrls.put("T\u00E9rminos y Condiciones URL", finalUrl);
			}, executionNotes);

			runStep(results, STEP_PRIVACY, () -> {
				final String finalUrl = validateLegalLink(appPage[0], context,
						Arrays.asList("Pol\u00EDtica de Privacidad", "Politica de Privacidad"),
						Arrays.asList("Pol\u00EDtica de Privacidad", "Politica de Privacidad"),
						evidenceDir.resolve("step-9-politica-de-privacidad.png"));
				legalUrls.put("Pol\u00EDtica de Privacidad URL", finalUrl);
			}, executionNotes);
		} finally {
			writeReport(evidenceDir, results, legalUrls, executionNotes);
		}

		assertAllStepsPass(results);
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static void runStep(final LinkedHashMap<String, StepResult> results, final String stepName,
			final CheckedRunnable runnable, final List<String> executionNotes) {
		try {
			runnable.run();
			results.put(stepName, StepResult.pass());
		} catch (Throwable throwable) {
			results.put(stepName, StepResult.fail(throwable.getMessage()));
			executionNotes.add(stepName + ": " + throwable.getClass().getSimpleName() + " - " + throwable.getMessage());
		}
	}

	private static String validateLegalLink(final Page appPage, final BrowserContext context,
			final List<String> possibleLinkTexts, final List<String> possibleHeadingTexts, final Path screenshotPath) {
		final String appUrlBeforeClick = appPage.url();
		Page targetPage = appPage;
		final Locator linkLocator = findByText(appPage, possibleLinkTexts);

		try {
			targetPage = context.waitForPage(() -> clickAndWait(appPage, linkLocator),
					new BrowserContext.WaitForPageOptions().setTimeout(5_000));
		} catch (PlaywrightException ignored) {
			clickAndWait(appPage, linkLocator);
			targetPage = appPage;
		}

		waitForUiLoad(targetPage);
		assertVisibleAnyText(targetPage, possibleHeadingTexts.toArray(new String[0]));
		assertLegalContentVisible(targetPage);
		captureScreenshot(targetPage, screenshotPath, true);

		final String finalUrl = targetPage.url();
		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else if (!appUrlBeforeClick.equals(targetPage.url())) {
			try {
				targetPage.goBack(new Page.GoBackOptions().setWaitUntil(LoadState.DOMCONTENTLOADED).setTimeout(10_000));
				waitForUiLoad(targetPage);
			} catch (PlaywrightException ignored) {
				targetPage.navigate(appUrlBeforeClick, new Page.NavigateOptions().setWaitUntil(LoadState.DOMCONTENTLOADED));
				waitForUiLoad(targetPage);
			}
		}

		return finalUrl;
	}

	private static void assertLegalContentVisible(final Page targetPage) {
		final String bodyText = targetPage.locator("body").innerText();
		assertTrue("Legal content text is not visible.", bodyText != null && bodyText.trim().length() > 120);
	}

	private static Page waitForApplicationInterface(final BrowserContext context) {
		final long deadline = System.currentTimeMillis() + 60_000;
		while (System.currentTimeMillis() < deadline) {
			for (final Page openPage : context.pages()) {
				try {
					waitForUiLoad(openPage);
					if (openPage.locator("aside, nav").first().isVisible()
							&& openPage.locator("text=Negocio").count() > 0) {
						openPage.bringToFront();
						return openPage;
					}
				} catch (PlaywrightException ignored) {
					// Page can detach during auth transitions.
				}
			}
		}
		throw new AssertionError("Main application interface did not appear after Google login.");
	}

	private static void selectGoogleAccountIfVisible(final BrowserContext context, final String accountEmail) {
		final long deadline = System.currentTimeMillis() + 20_000;
		while (System.currentTimeMillis() < deadline) {
			for (final Page openPage : context.pages()) {
				try {
					final Locator emailLocator = openPage.getByText(accountEmail).first();
					if (emailLocator.isVisible()) {
						clickAndWait(openPage, emailLocator);
						return;
					}
				} catch (PlaywrightException ignored) {
					// Ignore short-lived states during auth flow.
				}
			}
		}
	}

	private static void clickByText(final Page page, final String text) {
		final Locator locator = findByAnySelector(page, Arrays.asList("button:has-text(\"" + text + "\")",
				"[role=\"button\"]:has-text(\"" + text + "\")", "a:has-text(\"" + text + "\")",
				"[role=\"link\"]:has-text(\"" + text + "\")", "text=" + text));
		clickAndWait(page, locator);
	}

	private static Locator findByText(final Page page, final List<String> texts) {
		AssertionError lastError = null;
		for (final String text : texts) {
			try {
				final Locator locator = findByAnySelector(page, Arrays.asList("button:has-text(\"" + text + "\")",
						"[role=\"button\"]:has-text(\"" + text + "\")", "a:has-text(\"" + text + "\")",
						"[role=\"link\"]:has-text(\"" + text + "\")", "text=" + text));
				return locator;
			} catch (AssertionError error) {
				lastError = error;
			}
		}
		throw new AssertionError("Could not locate visible text options: " + texts, lastError);
	}

	private static void clickTextIfPresent(final Page page, final String text) {
		if (textIsVisible(page, text)) {
			clickByText(page, text);
		}
	}

	private static void fillByAnySelector(final Page page, final List<String> selectors, final String value) {
		final Locator locator = findByAnySelector(page, selectors);
		locator.click();
		locator.fill(value);
		waitForUiLoad(page);
	}

	private static void clickByAnySelector(final Page page, final List<String> selectors) {
		final Locator locator = findByAnySelector(page, selectors);
		clickAndWait(page, locator);
	}

	private static Locator findByAnySelector(final Page page, final List<String> selectors) {
		PlaywrightException lastException = null;
		for (final String selector : selectors) {
			try {
				final Locator locator = page.locator(selector).first();
				locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(4_000));
				return locator;
			} catch (PlaywrightException exception) {
				lastException = exception;
			}
		}
		throw new AssertionError("Could not find a visible element for selectors: " + selectors, lastException);
	}

	private static boolean textIsVisible(final Page page, final String text) {
		try {
			return page.getByText(text).first().isVisible();
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private static void assertVisibleText(final Page page, final String text) {
		try {
			page.getByText(text).first()
					.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException exception) {
			throw new AssertionError("Expected visible text not found: " + text, exception);
		}
	}

	private static void assertVisibleAnyText(final Page page, final String... texts) {
		AssertionError lastError = null;
		for (final String text : texts) {
			try {
				assertVisibleText(page, text);
				return;
			} catch (AssertionError assertionError) {
				lastError = assertionError;
				// Continue trying variants with accents or locale differences.
			}
		}
		throw new AssertionError("None of the expected text variants are visible: " + Arrays.toString(texts), lastError);
	}

	private static void assertNotVisibleText(final Page page, final String text) {
		try {
			final Locator locator = page.getByText(text).first();
			if (locator.count() > 0) {
				assertTrue("Text should not be visible: " + text, !locator.isVisible());
			}
		} catch (PlaywrightException ignored) {
			// If detached or absent, modal is no longer visible.
		}
	}

	private static void assertVisible(final Page page, final String selector, final String message) {
		try {
			page.locator(selector).first()
					.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException exception) {
			throw new AssertionError(message, exception);
		}
	}

	private static String sectionText(final Page page, final String... sectionTitles) {
		for (final String sectionTitle : sectionTitles) {
			try {
				final Locator section = page.locator("section, article, div")
						.filter(new Locator.FilterOptions().setHasText(sectionTitle)).first();
				section.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE)
						.setTimeout(DEFAULT_TIMEOUT_MS));
				return defaultValue(section.innerText(), "");
			} catch (PlaywrightException ignored) {
				// Continue with next title variant.
			}
		}

		throw new AssertionError("Could not locate section by any title variant: " + Arrays.toString(sectionTitles));
	}

	private static boolean hasNameLikeLine(final String sectionText) {
		final String[] lines = sectionText.split("\\R");
		for (final String raw : lines) {
			final String line = raw.trim();
			if (line.length() >= 5 && line.length() <= 60 && !line.contains("@") && !line.matches(".*\\d.*")
					&& !normalize(line).contains("informacion general") && !normalize(line).contains("business plan")
					&& !normalize(line).contains("cambiar plan")) {
				return true;
			}
		}
		return false;
	}

	private static void clickAndWait(final Page page, final Locator locator) {
		locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUiLoad(page);
	}

	private static void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED,
					new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// Not every click triggers navigation.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
		} catch (PlaywrightException ignored) {
			// Dynamic pages may keep background network calls.
		}

		page.waitForTimeout(300);
	}

	private static void captureScreenshot(final Page page, final Path path, final boolean fullPage) {
		try {
			Files.createDirectories(path.getParent());
			page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
		} catch (IOException exception) {
			throw new RuntimeException("Could not write screenshot to " + path, exception);
		}
	}

	private static LinkedHashMap<String, StepResult> initializeResults() {
		final LinkedHashMap<String, StepResult> results = new LinkedHashMap<>();
		results.put(STEP_LOGIN, StepResult.notRun());
		results.put(STEP_MENU, StepResult.notRun());
		results.put(STEP_MODAL, StepResult.notRun());
		results.put(STEP_ADMIN, StepResult.notRun());
		results.put(STEP_INFO, StepResult.notRun());
		results.put(STEP_DETAILS, StepResult.notRun());
		results.put(STEP_BUSINESSES, StepResult.notRun());
		results.put(STEP_TERMS, StepResult.notRun());
		results.put(STEP_PRIVACY, StepResult.notRun());
		return results;
	}

	private static void assertAllStepsPass(final LinkedHashMap<String, StepResult> results) {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			if (!entry.getValue().passed) {
				failedSteps.add(entry.getKey() + " (" + entry.getValue().status + ")");
			}
		}

		if (!failedSteps.isEmpty()) {
			fail("One or more SaleADS workflow validations failed: " + failedSteps);
		}
	}

	private static Path createEvidenceDirectory() throws IOException {
		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDir = Path.of("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private static void writeReport(final Path evidenceDir, final LinkedHashMap<String, StepResult> results,
			final LinkedHashMap<String, String> legalUrls, final List<String> executionNotes) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("# SaleADS Mi Negocio Full Test Report\n\n");
		builder.append("| Step | Result | Details |\n");
		builder.append("|---|---|---|\n");
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			builder.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue().status).append(" | ")
					.append(escapePipe(defaultValue(entry.getValue().details, ""))).append(" |\n");
		}

		if (!legalUrls.isEmpty()) {
			builder.append("\n## Legal URLs\n");
			for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
				builder.append("- ").append(legalUrl.getKey()).append(": ").append(legalUrl.getValue()).append("\n");
			}
		}

		if (!executionNotes.isEmpty()) {
			builder.append("\n## Execution Notes\n");
			for (final String note : executionNotes) {
				builder.append("- ").append(note).append("\n");
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.md"), builder.toString(), StandardCharsets.UTF_8);
	}

	private static String escapePipe(final String value) {
		return value.replace("|", "\\|");
	}

	private static String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String defaultValue(final String value, final String fallback) {
		return value == null ? fallback : value;
	}

	private static int parseInt(final String value, final int fallback) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static String normalize(final String value) {
		if (value == null) {
			return "";
		}

		final String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return withoutAccents.toLowerCase().replaceAll("\\s+", " ").trim();
	}

	private static final class StepResult {
		private final String status;
		private final boolean passed;
		private final String details;

		private StepResult(final String status, final boolean passed, final String details) {
			this.status = status;
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult("PASS", true, "");
		}

		private static StepResult fail(final String details) {
			return new StepResult("FAIL", false, defaultValue(details, "Validation failed."));
		}

		private static StepResult notRun() {
			return new StepResult("FAIL", false, "Not executed.");
		}
	}
}
