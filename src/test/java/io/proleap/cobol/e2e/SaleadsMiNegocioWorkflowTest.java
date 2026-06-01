package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioWorkflowTest {

	private static final String ENV_ENABLED = "SALEADS_E2E_ENABLED";
	private static final String ENV_BASE_URL = "SALEADS_BASE_URL";
	private static final String ENV_HEADLESS = "SALEADS_HEADLESS";
	private static final String ENV_SCREENSHOT_DIR = "SALEADS_SCREENSHOT_DIR";
	private static final String GOOGLE_TEST_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this E2E workflow test.",
				Boolean.parseBoolean(envOrDefault(ENV_ENABLED, "false")));

		final Path evidenceDir = Files
				.createDirectories(Paths.get(envOrDefault(ENV_SCREENSHOT_DIR, "target/saleads-evidence")));
		final Map<String, StepResult> report = initializeReport();
		final Map<String, String> legalUrls = new LinkedHashMap<String, String>();

		final String baseUrl = envOrDefault(ENV_BASE_URL, "").trim();
		if (baseUrl.isEmpty()) {
			recordFailure(report, "Login",
					"SALEADS_BASE_URL is required to run this workflow from login to legal pages.");
			writeReport(evidenceDir, report, legalUrls);
			Assert.fail("SALEADS_BASE_URL is empty.");
		}

		final boolean headless = Boolean.parseBoolean(envOrDefault(ENV_HEADLESS, "true"));

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(30000);
			appPage.setDefaultNavigationTimeout(45000);

			appPage.navigate(baseUrl);
			waitForUiLoad(appPage);

			executeStep(report, "Login", () -> {
				runLoginWithGoogle(appPage, context, evidenceDir);
			});

			executeStep(report, "Mi Negocio menu", () -> {
				runOpenMiNegocioMenu(appPage, evidenceDir);
			});

			executeStep(report, "Agregar Negocio modal", () -> {
				runAgregarNegocioModalValidation(appPage, evidenceDir);
			});

			executeStep(report, "Administrar Negocios view", () -> {
				runAdministrarNegociosValidation(appPage, evidenceDir);
			});

			executeStep(report, "Información General", () -> {
				runInformacionGeneralValidation(appPage);
			});

			executeStep(report, "Detalles de la Cuenta", () -> {
				runDetallesCuentaValidation(appPage);
			});

			executeStep(report, "Tus Negocios", () -> {
				runTusNegociosValidation(appPage);
			});

			executeStep(report, "Términos y Condiciones", () -> {
				final String url = runLegalValidation(appPage, context, evidenceDir, "Términos y Condiciones",
						"step8_terminos_y_condiciones.png");
				legalUrls.put("Términos y Condiciones", url);
			});

			executeStep(report, "Política de Privacidad", () -> {
				final String url = runLegalValidation(appPage, context, evidenceDir, "Política de Privacidad",
						"step9_politica_de_privacidad.png");
				legalUrls.put("Política de Privacidad", url);
			});
		}

		writeReport(evidenceDir, report, legalUrls);
		assertNoFailures(report);
	}

	private void runLoginWithGoogle(final Page appPage, final BrowserContext context, final Path evidenceDir) {
		final Locator loginButton = firstVisible(appPage,
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*"))),
				appPage.getByText(Pattern.compile("(?i)sign in with google|iniciar sesi[oó]n con google|google")));

		if (loginButton == null) {
			throw new AssertionError("Login button with Google text was not found.");
		}

		Page authPage = appPage;
		boolean openedPopup = false;

		try {
			authPage = context.waitForPage(() -> {
				loginButton.click();
			}, new BrowserContext.WaitForPageOptions().setTimeout(7000));
			openedPopup = true;
		} catch (PlaywrightException ignored) {
			// No popup was opened. The click still happened on the app page.
		}

		waitForUiLoad(authPage);
		selectGoogleAccountIfVisible(authPage, GOOGLE_TEST_ACCOUNT);

		// Some environments redirect in the main tab only.
		waitForUiLoad(appPage);

		final Locator sidebar = firstVisible(appPage, appPage.locator("aside"),
				appPage.getByText(Pattern.compile("(?i)negocio|mi negocio|dashboard")));
		if (sidebar == null) {
			throw new AssertionError("Main app sidebar/navigation is not visible after login.");
		}

		takeScreenshot(appPage, evidenceDir.resolve("step1_dashboard_loaded.png"), true);

		if (openedPopup && !authPage.equals(appPage) && !authPage.isClosed()) {
			// The popup may remain open in some auth flows; close it to keep the app focused.
			authPage.close();
		}
	}

	private void runOpenMiNegocioMenu(final Page appPage, final Path evidenceDir) {
		final Locator sidebar = firstVisible(appPage, appPage.locator("aside"), appPage.locator("nav"));
		if (sidebar == null) {
			throw new AssertionError("Left sidebar navigation was not found.");
		}

		clickTextIfVisible(appPage, "Negocio");
		clickByVisibleText(appPage, "Mi Negocio");

		requireVisibleText(appPage, "Agregar Negocio");
		requireVisibleText(appPage, "Administrar Negocios");

		takeScreenshot(appPage, evidenceDir.resolve("step2_mi_negocio_expanded.png"), false);
	}

	private void runAgregarNegocioModalValidation(final Page appPage, final Path evidenceDir) {
		clickByVisibleText(appPage, "Agregar Negocio");
		requireVisibleText(appPage, "Crear Nuevo Negocio");

		final Locator nombreInput = firstVisible(appPage,
				appPage.getByLabel(Pattern.compile("(?i).*nombre del negocio.*")),
				appPage.getByPlaceholder(Pattern.compile("(?i).*nombre del negocio.*")),
				appPage.locator("input").filter(new Locator.FilterOptions().setHasText("")));
		if (nombreInput == null) {
			throw new AssertionError("Input 'Nombre del Negocio' was not found.");
		}

		requireVisibleText(appPage, "Tienes 2 de 3 negocios");
		requireVisibleText(appPage, "Cancelar");
		requireVisibleText(appPage, "Crear Negocio");

		takeScreenshot(appPage, evidenceDir.resolve("step3_agregar_negocio_modal.png"), false);

		nombreInput.click();
		nombreInput.fill("Negocio Prueba Automatización");
		clickByVisibleText(appPage, "Cancelar");
		waitForUiLoad(appPage);
	}

	private void runAdministrarNegociosValidation(final Page appPage, final Path evidenceDir) {
		if (!isTextVisible(appPage, "Administrar Negocios")) {
			clickByVisibleText(appPage, "Mi Negocio");
		}

		clickByVisibleText(appPage, "Administrar Negocios");
		requireVisibleText(appPage, "Información General");
		requireVisibleText(appPage, "Detalles de la Cuenta");
		requireVisibleText(appPage, "Tus Negocios");
		requireVisibleText(appPage, "Sección Legal");

		takeScreenshot(appPage, evidenceDir.resolve("step4_administrar_negocios_full.png"), true);
	}

	private void runInformacionGeneralValidation(final Page appPage) {
		final Locator infoSection = sectionWithHeading(appPage, "Información General");
		final String sectionText = infoSection.innerText();

		assertContainsPattern(sectionText, EMAIL_PATTERN, "User email is not visible in Información General.");
		requireVisibleText(appPage, "BUSINESS PLAN");
		requireVisibleText(appPage, "Cambiar Plan");

		final List<String> lines = Arrays.stream(sectionText.split("\\R")).map(String::trim).filter(line -> !line.isEmpty())
				.collect(Collectors.toList());
		final boolean hasLikelyUserName = lines.stream().anyMatch(line -> !EMAIL_PATTERN.matcher(line).find()
				&& !equalsIgnoreCaseAny(line, "Información General", "BUSINESS PLAN", "Cambiar Plan")
				&& !line.toLowerCase(Locale.ROOT).contains("plan") && line.length() >= 3);
		Assert.assertTrue("User name is not visible in Información General.", hasLikelyUserName);
	}

	private void runDetallesCuentaValidation(final Page appPage) {
		sectionWithHeading(appPage, "Detalles de la Cuenta");
		requireVisibleText(appPage, "Cuenta creada");
		requireVisibleText(appPage, "Estado activo");
		requireVisibleText(appPage, "Idioma seleccionado");
	}

	private void runTusNegociosValidation(final Page appPage) {
		final Locator section = sectionWithHeading(appPage, "Tus Negocios");
		requireVisibleText(appPage, "Agregar Negocio");
		requireVisibleText(appPage, "Tienes 2 de 3 negocios");

		final int structuredItems = section.locator("li, [role='listitem'], table tbody tr, .business-item, .business-card")
				.count();
		if (structuredItems > 0) {
			return;
		}

		final String sectionText = section.innerText();
		final long meaningfulLines = Arrays.stream(sectionText.split("\\R")).map(String::trim).filter(line -> !line.isEmpty())
				.filter(line -> !equalsIgnoreCaseAny(line, "Tus Negocios", "Agregar Negocio", "Tienes 2 de 3 negocios"))
				.count();
		Assert.assertTrue("Business list is not visible in Tus Negocios.", meaningfulLines > 0);
	}

	private String runLegalValidation(final Page appPage, final BrowserContext context, final Path evidenceDir,
			final String linkText, final String screenshotName) {
		Page legalPage = appPage;
		boolean openedNewTab = false;

		try {
			legalPage = context.waitForPage(() -> {
				clickByVisibleTextNoWait(appPage, linkText);
			}, new BrowserContext.WaitForPageOptions().setTimeout(7000));
			openedNewTab = true;
		} catch (PlaywrightException ignored) {
			// If no new tab appears, the click navigated in the same tab.
		}

		waitForUiLoad(legalPage);
		requireVisibleText(legalPage, linkText);

		final String legalText = safeBodyText(legalPage);
		Assert.assertTrue(linkText + " content is not visible.", legalText != null && legalText.trim().length() > 120);

		takeScreenshot(legalPage, evidenceDir.resolve(screenshotName), true);

		final String finalUrl = legalPage.url();

		if (openedNewTab && !legalPage.equals(appPage) && !legalPage.isClosed()) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else {
			try {
				appPage.goBack();
			} catch (PlaywrightException ignored) {
				// If back is not available, continue from current page state.
			}
			waitForUiLoad(appPage);
		}

		return finalUrl;
	}

	private String safeBodyText(final Page page) {
		try {
			return page.locator("body").innerText();
		} catch (PlaywrightException ex) {
			return "";
		}
	}

	private void clickByVisibleText(final Page page, final String text) {
		final Locator locator = byVisibleText(page, text);
		if (locator == null) {
			throw new AssertionError("Expected visible text for click: " + text);
		}
		locator.click();
		waitForUiLoad(page);
	}

	private void clickByVisibleTextNoWait(final Page page, final String text) {
		final Locator locator = byVisibleText(page, text);
		if (locator == null) {
			throw new AssertionError("Expected visible text for click: " + text);
		}
		locator.click();
	}

	private void clickTextIfVisible(final Page page, final String text) {
		final Locator locator = byVisibleText(page, text);
		if (locator != null) {
			locator.click();
			waitForUiLoad(page);
		}
	}

	private Locator byVisibleText(final Page page, final String text) {
		final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
		if (isVisible(exact, 2500)) {
			return exact;
		}

		final Locator contains = page.getByText(Pattern.compile("(?i).*" + Pattern.quote(text) + ".*")).first();
		if (isVisible(contains, 2500)) {
			return contains;
		}

		return null;
	}

	private boolean isTextVisible(final Page page, final String text) {
		return byVisibleText(page, text) != null;
	}

	private Locator sectionWithHeading(final Page page, final String heading) {
		requireVisibleText(page, heading);
		final Locator section = page.locator("section, article, div")
				.filter(new Locator.FilterOptions().setHasText(Pattern.compile("(?i).*" + Pattern.quote(heading) + ".*")))
				.first();
		Assert.assertTrue("Section for heading '" + heading + "' is not visible.", isVisible(section, 8000));
		return section;
	}

	private void requireVisibleText(final Page page, final String text) {
		final Locator locator = byVisibleText(page, text);
		Assert.assertNotNull("Expected visible text: " + text, locator);
	}

	@SafeVarargs
	private final Locator firstVisible(final Page page, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			final Locator first = candidate.first();
			if (isVisible(first, 5000)) {
				return first;
			}
		}
		return null;
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return locator.isVisible();
		} catch (PlaywrightException ex) {
			return false;
		}
	}

	private void selectGoogleAccountIfVisible(final Page authPage, final String accountEmail) {
		final Locator account = authPage.getByText(accountEmail, new Page.GetByTextOptions().setExact(true)).first();
		if (isVisible(account, 7000)) {
			account.click();
			waitForUiLoad(authPage);
		}
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000));
		} catch (PlaywrightException ignored) {
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (PlaywrightException ignored) {
		}

		page.waitForTimeout(800);
	}

	private void takeScreenshot(final Page page, final Path filePath, final boolean fullPage) {
		try {
			Files.createDirectories(filePath.getParent());
		} catch (final IOException ex) {
			throw new RuntimeException("Could not create screenshot directory: " + filePath.getParent(), ex);
		}
		page.screenshot(new Page.ScreenshotOptions().setPath(filePath).setFullPage(fullPage));
	}

	private void executeStep(final Map<String, StepResult> report, final String stepName, final ThrowingRunnable action) {
		try {
			action.run();
			recordSuccess(report, stepName, "Validation completed.");
		} catch (final Throwable ex) {
			recordFailure(report, stepName, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
		}
	}

	private void writeReport(final Path evidenceDir, final Map<String, StepResult> report, final Map<String, String> legalUrls)
			throws IOException {
		final List<String> lines = new ArrayList<String>();
		lines.add("{");
		lines.add("  \"generatedAt\": \"" + escapeJson(Instant.now().toString()) + "\",");
		lines.add("  \"results\": {");

		final List<Map.Entry<String, StepResult>> entries = new ArrayList<Map.Entry<String, StepResult>>(report.entrySet());
		for (int i = 0; i < entries.size(); i++) {
			final Map.Entry<String, StepResult> entry = entries.get(i);
			final StepResult result = entry.getValue();
			lines.add("    \"" + escapeJson(entry.getKey()) + "\": {");
			lines.add("      \"status\": \"" + (result.passed ? "PASS" : "FAIL") + "\",");
			lines.add("      \"details\": \"" + escapeJson(result.details) + "\"");
			lines.add("    }" + (i < entries.size() - 1 ? "," : ""));
		}

		lines.add("  },");
		lines.add("  \"legalUrls\": {");

		final List<Map.Entry<String, String>> urlEntries = new ArrayList<Map.Entry<String, String>>(legalUrls.entrySet());
		for (int i = 0; i < urlEntries.size(); i++) {
			final Map.Entry<String, String> entry = urlEntries.get(i);
			lines.add("    \"" + escapeJson(entry.getKey()) + "\": \"" + escapeJson(entry.getValue()) + "\""
					+ (i < urlEntries.size() - 1 ? "," : ""));
		}

		lines.add("  }");
		lines.add("}");

		Files.write(evidenceDir.resolve("final-report.json"), lines);
	}

	private void assertNoFailures(final Map<String, StepResult> report) {
		final List<String> failed = report.entrySet().stream().filter(entry -> !entry.getValue().passed)
				.map(Map.Entry::getKey).collect(Collectors.toList());
		if (!failed.isEmpty()) {
			Assert.fail("SaleADS workflow validation failed in: " + String.join(", ", failed));
		}
	}

	private Map<String, StepResult> initializeReport() {
		final Map<String, StepResult> report = new LinkedHashMap<String, StepResult>();
		report.put("Login", StepResult.pending());
		report.put("Mi Negocio menu", StepResult.pending());
		report.put("Agregar Negocio modal", StepResult.pending());
		report.put("Administrar Negocios view", StepResult.pending());
		report.put("Información General", StepResult.pending());
		report.put("Detalles de la Cuenta", StepResult.pending());
		report.put("Tus Negocios", StepResult.pending());
		report.put("Términos y Condiciones", StepResult.pending());
		report.put("Política de Privacidad", StepResult.pending());
		return report;
	}

	private void recordSuccess(final Map<String, StepResult> report, final String stepName, final String details) {
		report.put(stepName, new StepResult(true, details));
	}

	private void recordFailure(final Map<String, StepResult> report, final String stepName, final String details) {
		report.put(stepName, new StepResult(false, details == null ? "Unknown failure." : details));
	}

	private String envOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value;
	}

	private String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	private boolean equalsIgnoreCaseAny(final String value, final String... candidates) {
		for (final String candidate : candidates) {
			if (value.equalsIgnoreCase(candidate)) {
				return true;
			}
		}
		return false;
	}

	private void assertContainsPattern(final String value, final Pattern pattern, final String message) {
		Assert.assertTrue(message, value != null && pattern.matcher(value).find());
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pending() {
			return new StepResult(false, "Not executed.");
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
