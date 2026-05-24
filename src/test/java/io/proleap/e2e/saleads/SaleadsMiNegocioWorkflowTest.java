package io.proleap.e2e.saleads;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.regex.Matcher;
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

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * Required environment variables:
 * </p>
 * <ul>
 * <li>SALEADS_LOGIN_URL: Login page URL for the current environment.</li>
 * </ul>
 *
 * <p>
 * Optional environment variables:
 * </p>
 * <ul>
 * <li>SALEADS_GOOGLE_ACCOUNT_EMAIL (default: juanlucasbarbiergarzon@gmail.com)</li>
 * <li>SALEADS_EXPECTED_USER_NAME</li>
 * <li>HEADLESS (default: true)</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final long SHORT_WAIT_MS = 10_000L;
	private static final long MEDIUM_WAIT_MS = 20_000L;
	private static final long LONG_WAIT_MS = 90_000L;

	private static final String INFO_GENERAL = "Informaci\u00F3n General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String SECCION_LEGAL = "Secci\u00F3n Legal";
	private static final String TERMINOS = "T\u00E9rminos y Condiciones";
	private static final String POLITICA = "Pol\u00EDtica de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern NAME_NEAR_EMAIL_PATTERN = Pattern
			.compile("([A-Za-z\\u00C0-\\u00FF]{2,}\\s+[A-Za-z\\u00C0-\\u00FF]{2,})");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String loginUrl = valueOrEmpty(System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue("SALEADS_LOGIN_URL must be provided to run this E2E flow.", !loginUrl.isBlank());

		final String googleAccount = valueOrDefault(System.getenv("SALEADS_GOOGLE_ACCOUNT_EMAIL"),
				"juanlucasbarbiergarzon@gmail.com");
		final String expectedUserName = valueOrEmpty(System.getenv("SALEADS_EXPECTED_USER_NAME"));
		final boolean headless = Boolean.parseBoolean(valueOrDefault(System.getenv("HEADLESS"), "true"));

		final Path evidenceDir = createEvidenceDir();
		final Map<String, StepResult> report = initReport();

		String terminosUrl = "N/A";
		String politicaUrl = "N/A";

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			Page appPage = context.newPage();

			appPage.navigate(loginUrl);
			waitForUi(appPage);

			// Step 1: Login with Google
			try {
				appPage = loginWithGoogle(appPage, context, googleAccount, evidenceDir);
				markPass(report, "Login", "Main application and left sidebar are visible.");
			} catch (Exception ex) {
				markFail(report, "Login", ex);
			}

			// Step 2: Open Mi Negocio menu
			try {
				requireAppPage(appPage, report, "Login");
				openMiNegocioMenu(appPage, evidenceDir);
				markPass(report, "Mi Negocio menu",
						"Mi Negocio expanded and both submenu options are visible.");
			} catch (Exception ex) {
				markFail(report, "Mi Negocio menu", ex);
			}

			// Step 3: Validate Agregar Negocio modal
			try {
				requirePassed(report, "Mi Negocio menu");
				validateAgregarNegocioModal(appPage, evidenceDir);
				markPass(report, "Agregar Negocio modal",
						"Modal fields, text, and action buttons validated successfully.");
			} catch (Exception ex) {
				markFail(report, "Agregar Negocio modal", ex);
			}

			// Step 4: Open Administrar Negocios
			try {
				requirePassed(report, "Mi Negocio menu");
				openAdministrarNegocios(appPage, evidenceDir);
				markPass(report, "Administrar Negocios view",
						"All required account sections are visible.");
			} catch (Exception ex) {
				markFail(report, "Administrar Negocios view", ex);
			}

			// Step 5: Validate Informacion General
			try {
				requirePassed(report, "Administrar Negocios view");
				validateInformacionGeneral(appPage, expectedUserName);
				markPass(report, "Informaci\u00F3n General",
						"User name/email plus plan information validated.");
			} catch (Exception ex) {
				markFail(report, "Informaci\u00F3n General", ex);
			}

			// Step 6: Validate Detalles de la Cuenta
			try {
				requirePassed(report, "Administrar Negocios view");
				validateDetallesCuenta(appPage);
				markPass(report, "Detalles de la Cuenta", "All account detail labels are visible.");
			} catch (Exception ex) {
				markFail(report, "Detalles de la Cuenta", ex);
			}

			// Step 7: Validate Tus Negocios
			try {
				requirePassed(report, "Administrar Negocios view");
				validateTusNegocios(appPage);
				markPass(report, "Tus Negocios", "Business list section and quota text are visible.");
			} catch (Exception ex) {
				markFail(report, "Tus Negocios", ex);
			}

			// Step 8: Validate Terminos y Condiciones
			try {
				requirePassed(report, "Administrar Negocios view");
				terminosUrl = openAndValidateLegalLink(appPage, context, evidenceDir, TERMINOS,
						"08-terminos-y-condiciones.png");
				markPass(report, "T\u00E9rminos y Condiciones", "Legal page validated. URL: " + terminosUrl);
			} catch (Exception ex) {
				markFail(report, "T\u00E9rminos y Condiciones", ex);
			}

			// Step 9: Validate Politica de Privacidad
			try {
				requirePassed(report, "Administrar Negocios view");
				politicaUrl = openAndValidateLegalLink(appPage, context, evidenceDir, POLITICA,
						"09-politica-de-privacidad.png");
				markPass(report, "Pol\u00EDtica de Privacidad", "Legal page validated. URL: " + politicaUrl);
			} catch (Exception ex) {
				markFail(report, "Pol\u00EDtica de Privacidad", ex);
			}

			final String finalReport = buildFinalReport(report, evidenceDir, terminosUrl, politicaUrl);
			final Path reportFile = evidenceDir.resolve("10-final-report.txt");
			Files.writeString(reportFile, finalReport, StandardCharsets.UTF_8);
			System.out.println(finalReport);

			final List<String> failedSteps = failedSteps(report);
			Assert.assertTrue(
					"One or more SaleADS validations failed. Review " + reportFile + ". Failed steps: " + failedSteps,
					failedSteps.isEmpty());
		}
	}

	private static Page loginWithGoogle(final Page loginPage, final BrowserContext context, final String googleAccount,
			final Path evidenceDir) {
		final Locator loginButton = findVisible(loginPage, MEDIUM_WAIT_MS,
				"button:has-text(\"Sign in with Google\")",
				"button:has-text(\"Iniciar sesi\u00F3n con Google\")",
				"button:has-text(\"Continuar con Google\")",
				"text=/Sign in with Google|Iniciar sesi[o\u00F3]n con Google|Continuar con Google|Google/i");

		final int beforeClickPages = context.pages().size();
		clickAndWait(loginPage, loginButton);
		final Page popup = waitForNewPage(context, beforeClickPages, SHORT_WAIT_MS);

		if (popup != null) {
			waitForUi(popup);
			chooseGoogleAccountIfPresented(popup, googleAccount);
		}

		chooseGoogleAccountIfPresented(loginPage, googleAccount);

		final Page appPage = waitForMainApplicationPage(context, loginPage, LONG_WAIT_MS);
		takeScreenshot(appPage, evidenceDir.resolve("01-dashboard-loaded.png"), false);
		return appPage;
	}

	private static void openMiNegocioMenu(final Page appPage, final Path evidenceDir) {
		final Locator negocioSection = findVisible(appPage, MEDIUM_WAIT_MS, "text=/^Negocio$/i", "text=/Negocio/i");
		clickAndWait(appPage, negocioSection);

		final Locator miNegocioOption = findVisible(appPage, MEDIUM_WAIT_MS, "text=/^Mi Negocio$/i", "text=/Mi Negocio/i");
		clickAndWait(appPage, miNegocioOption);

		findVisible(appPage, MEDIUM_WAIT_MS, "text=/^Agregar Negocio$/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/^Administrar Negocios$/i");
		takeScreenshot(appPage, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
	}

	private static void validateAgregarNegocioModal(final Page appPage, final Path evidenceDir) {
		final Locator agregarNegocio = findVisible(appPage, MEDIUM_WAIT_MS, "text=/^Agregar Negocio$/i");
		clickAndWait(appPage, agregarNegocio);

		findVisible(appPage, MEDIUM_WAIT_MS, "text=/^Crear Nuevo Negocio$/i");
		findVisible(appPage, MEDIUM_WAIT_MS,
				"label:has-text(\"Nombre del Negocio\")",
				"input[placeholder*=\"Nombre del Negocio\"]",
				"text=/Nombre del Negocio/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "button:has-text(\"Cancelar\")", "text=/^Cancelar$/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "button:has-text(\"Crear Negocio\")", "text=/^Crear Negocio$/i");

		takeScreenshot(appPage, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

		// Optional modal interaction requested by the workflow.
		final Locator nombreInput = appPage
				.locator("input[placeholder*=\"Nombre del Negocio\"], input[name*=\"nombre\" i], input[id*=\"nombre\" i]")
				.first();
		if (isVisible(nombreInput)) {
			nombreInput.click();
			nombreInput.fill("Negocio Prueba Automatizacion");
			waitForUi(appPage);
		}

		final Locator cancelar = findVisible(appPage, MEDIUM_WAIT_MS, "button:has-text(\"Cancelar\")", "text=/^Cancelar$/i");
		clickAndWait(appPage, cancelar);
		waitUntilHidden(appPage, "text=/^Crear Nuevo Negocio$/i", SHORT_WAIT_MS);
	}

	private static void openAdministrarNegocios(final Page appPage, final Path evidenceDir) {
		if (!isAnyVisible(appPage, "text=/^Administrar Negocios$/i")) {
			final Locator miNegocio = findVisible(appPage, MEDIUM_WAIT_MS, "text=/^Mi Negocio$/i", "text=/Mi Negocio/i");
			clickAndWait(appPage, miNegocio);
		}

		final Locator administrarNegocios = findVisible(appPage, MEDIUM_WAIT_MS, "text=/^Administrar Negocios$/i");
		clickAndWait(appPage, administrarNegocios);

		findVisible(appPage, MEDIUM_WAIT_MS, "text=/" + INFO_GENERAL + "/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/" + DETALLES_CUENTA + "/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/" + TUS_NEGOCIOS + "/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/" + SECCION_LEGAL + "/i", "text=/Seccion Legal/i");
		takeScreenshot(appPage, evidenceDir.resolve("04-administrar-negocios-page.png"), true);
	}

	private static void validateInformacionGeneral(final Page appPage, final String expectedUserName) {
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/" + INFO_GENERAL + "/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/BUSINESS PLAN/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "button:has-text(\"Cambiar Plan\")", "text=/Cambiar Plan/i");

		final String pageText = bodyText(appPage);
		final Matcher emailMatcher = EMAIL_PATTERN.matcher(pageText);
		if (!emailMatcher.find()) {
			throw new IllegalStateException("No user email was detected in the account view.");
		}

		if (!expectedUserName.isBlank()) {
			if (!containsIgnoreCase(pageText, expectedUserName)) {
				throw new IllegalStateException(
						"Expected user name '" + expectedUserName + "' was not visible in Informacion General.");
			}
		} else {
			final int start = Math.max(0, emailMatcher.start() - 160);
			final String nearEmail = pageText.substring(start, emailMatcher.start());
			if (!NAME_NEAR_EMAIL_PATTERN.matcher(nearEmail).find()) {
				throw new IllegalStateException(
						"A user name-like text was not detected near the email address in Informacion General.");
			}
		}
	}

	private static void validateDetallesCuenta(final Page appPage) {
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/" + DETALLES_CUENTA + "/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/Cuenta creada/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/Estado activo/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/Idioma seleccionado/i");
	}

	private static void validateTusNegocios(final Page appPage) {
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/" + TUS_NEGOCIOS + "/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/^Agregar Negocio$/i");
		findVisible(appPage, MEDIUM_WAIT_MS, "text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i");
	}

	private static String openAndValidateLegalLink(final Page appPage, final BrowserContext context, final Path evidenceDir,
			final String linkText, final String screenshotFileName) {
		final Locator legalLink = findVisible(appPage, MEDIUM_WAIT_MS, "text=/" + linkText + "/i",
				"text=/" + withoutAccents(linkText) + "/i");

		final String appUrlBefore = appPage.url();
		final int beforeClickPages = context.pages().size();
		clickAndWait(appPage, legalLink);

		Page targetPage = waitForNewPage(context, beforeClickPages, SHORT_WAIT_MS);
		if (targetPage == null) {
			targetPage = appPage;
		}

		waitForUi(targetPage);
		findVisible(targetPage, MEDIUM_WAIT_MS, "text=/" + linkText + "/i", "text=/" + withoutAccents(linkText) + "/i");

		final String legalText = bodyText(targetPage).trim();
		if (legalText.length() < 120) {
			throw new IllegalStateException("Legal page content appears too short to be considered valid.");
		}

		takeScreenshot(targetPage, evidenceDir.resolve(screenshotFileName), true);
		final String finalUrl = targetPage.url();

		// Cleanup: always return to the application tab/page.
		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!appUrlBefore.equals(appPage.url())) {
			try {
				appPage.goBack();
				waitForUi(appPage);
			} catch (PlaywrightException ignored) {
				// If browser history is unavailable, keep current page and continue.
			}
		}

		return finalUrl;
	}

	private static Page waitForMainApplicationPage(final BrowserContext context, final Page fallbackPage, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;

		while (System.currentTimeMillis() < deadline) {
			final List<Page> pages = new ArrayList<>(context.pages());
			for (final Page candidate : pages) {
				if (!candidate.isClosed() && isAnyVisible(candidate, "text=/Mi Negocio/i", "text=/\\bNegocio\\b/i", "aside")) {
					waitForUi(candidate);
					return candidate;
				}
			}
			fallbackPage.waitForTimeout(300);
		}

		throw new IllegalStateException(
				"Main application interface was not detected after login. Sidebar/menu did not become visible.");
	}

	private static void chooseGoogleAccountIfPresented(final Page page, final String googleAccount) {
		final Locator accountOption = page.locator("text=" + googleAccount).first();
		if (isVisible(accountOption)) {
			clickAndWait(page, accountOption);
			waitForUi(page);
		}
	}

	private static Locator findVisible(final Page page, final long timeoutMs, final String... selectors) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final String selector : selectors) {
				final Locator locator = page.locator(selector).first();
				if (isVisible(locator)) {
					return locator;
				}
			}
			page.waitForTimeout(250);
		}
		throw new IllegalStateException("Timed out waiting for visible element. Selectors: " + Arrays.toString(selectors));
	}

	private static boolean isAnyVisible(final Page page, final String... selectors) {
		for (final String selector : selectors) {
			if (isVisible(page.locator(selector).first())) {
				return true;
			}
		}
		return false;
	}

	private static boolean isVisible(final Locator locator) {
		try {
			return locator.isVisible();
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private static void clickAndWait(final Page page, final Locator locator) {
		locator.click();
		waitForUi(page);
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState();
		} catch (PlaywrightException ignored) {
			// Some SPA interactions do not trigger a full load state transition.
		}
		page.waitForTimeout(700);
	}

	private static void waitUntilHidden(final Page page, final String selector, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (!isVisible(page.locator(selector).first())) {
				return;
			}
			page.waitForTimeout(200);
		}
		throw new IllegalStateException("Element remained visible longer than expected: " + selector);
	}

	private static Page waitForNewPage(final BrowserContext context, final int beforeSize, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			final List<Page> pages = context.pages();
			if (pages.size() > beforeSize) {
				return pages.get(pages.size() - 1);
			}
			try {
				Thread.sleep(200);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	private static String bodyText(final Page page) {
		final String text = page.locator("body").innerText();
		return text == null ? "" : text;
	}

	private static void takeScreenshot(final Page page, final Path path, final boolean fullPage) {
		ensureParent(path);
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private static void ensureParent(final Path path) {
		try {
			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}
		} catch (IOException ex) {
			throw new IllegalStateException("Could not create screenshot directory for: " + path, ex);
		}
	}

	private static Path createEvidenceDir() {
		try {
			final String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
			final Path dir = Paths.get("target", "saleads-evidence", stamp);
			Files.createDirectories(dir);
			return dir;
		} catch (IOException ex) {
			throw new IllegalStateException("Could not create evidence directory.", ex);
		}
	}

	private static void requireAppPage(final Page page, final Map<String, StepResult> report, final String dependencyStep) {
		requirePassed(report, dependencyStep);
		if (page == null || page.isClosed()) {
			throw new IllegalStateException("Application page is not available for this step.");
		}
	}

	private static void requirePassed(final Map<String, StepResult> report, final String stepName) {
		final StepResult result = report.get(stepName);
		if (result == null || result.status != StepStatus.PASS) {
			throw new IllegalStateException("Blocked because prerequisite step failed: " + stepName);
		}
	}

	private static Map<String, StepResult> initReport() {
		final Map<String, StepResult> report = new LinkedHashMap<>();
		report.put("Login", StepResult.pending());
		report.put("Mi Negocio menu", StepResult.pending());
		report.put("Agregar Negocio modal", StepResult.pending());
		report.put("Administrar Negocios view", StepResult.pending());
		report.put("Informaci\u00F3n General", StepResult.pending());
		report.put("Detalles de la Cuenta", StepResult.pending());
		report.put("Tus Negocios", StepResult.pending());
		report.put("T\u00E9rminos y Condiciones", StepResult.pending());
		report.put("Pol\u00EDtica de Privacidad", StepResult.pending());
		return report;
	}

	private static void markPass(final Map<String, StepResult> report, final String step, final String details) {
		report.put(step, StepResult.pass(details));
	}

	private static void markFail(final Map<String, StepResult> report, final String step, final Exception ex) {
		report.put(step, StepResult.fail(ex.getClass().getSimpleName() + ": " + safeMessage(ex)));
	}

	private static String buildFinalReport(final Map<String, StepResult> report, final Path evidenceDir, final String terminosUrl,
			final String politicaUrl) {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Workflow - Final Report").append('\n');
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		builder.append('\n');

		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().status.name()).append('\n');
			builder.append("  Details: ").append(entry.getValue().details).append('\n');
		}

		builder.append('\n');
		builder.append("Captured legal URLs:").append('\n');
		builder.append("- ").append(TERMINOS).append(": ").append(terminosUrl).append('\n');
		builder.append("- ").append(POLITICA).append(": ").append(politicaUrl).append('\n');

		return builder.toString();
	}

	private static List<String> failedSteps(final Map<String, StepResult> report) {
		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (entry.getValue().status == StepStatus.FAIL) {
				failed.add(entry.getKey());
			}
		}
		return failed;
	}

	private static String withoutAccents(final String value) {
		return value
				.replace("\u00E1", "a").replace("\u00E9", "e").replace("\u00ED", "i").replace("\u00F3", "o").replace("\u00FA", "u")
				.replace("\u00C1", "A").replace("\u00C9", "E").replace("\u00CD", "I").replace("\u00D3", "O").replace("\u00DA", "U");
	}

	private static boolean containsIgnoreCase(final String text, final String token) {
		return text.toLowerCase().contains(token.toLowerCase());
	}

	private static String safeMessage(final Exception ex) {
		return ex.getMessage() == null ? "(no error message)" : ex.getMessage();
	}

	private static String valueOrDefault(final String value, final String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String valueOrEmpty(final String value) {
		return value == null ? "" : value.trim();
	}

	private enum StepStatus {
		PASS,
		FAIL,
		PENDING
	}

	private static final class StepResult {
		private final StepStatus status;
		private final String details;

		private StepResult(final StepStatus status, final String details) {
			this.status = status;
			this.details = details;
		}

		private static StepResult pending() {
			return new StepResult(StepStatus.PENDING, "Not executed.");
		}

		private static StepResult pass(final String details) {
			return new StepResult(StepStatus.PASS, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(StepStatus.FAIL, details);
		}
	}
}
