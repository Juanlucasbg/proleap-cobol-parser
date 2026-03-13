package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * End-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * <p>Enable with:
 * <ul>
 *   <li>env: SALEADS_E2E_ENABLED=true</li>
 *   <li>and one of: SALEADS_LOGIN_URL / SALEADS_URL, or JVM props saleads.login.url / saleads.url</li>
 * </ul>
 *
 * <p>Artifacts:
 * <ul>
 *   <li>target/saleads-evidence/&lt;timestamp&gt;/screenshots</li>
 *   <li>target/saleads-evidence/&lt;timestamp&gt;/final-report.md</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final long SHORT_WAIT_MS = 2500L;
	private static final long LONG_WAIT_MS = 20000L;
	private static final DateTimeFormatter RUN_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w._%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;

	private Path runDirectory;
	private Path screenshotsDirectory;

	private final Map<String, StepResult> results = new LinkedHashMap<String, StepResult>();

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("SaleADS E2E disabled. Set SALEADS_E2E_ENABLED=true or -Dsaleads.e2e.enabled=true.",
				isE2eEnabled());

		initResults();
		initEvidenceDirectories();
		initBrowser();
		openLoginPage();
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		closeQuietly();
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final boolean loginOk = runStep("Login", new CheckedRunnable() {
			@Override
			public void run() {
				stepLoginWithGoogle();
			}
		});

		final boolean menuOk;
		if (loginOk) {
			menuOk = runStep("Mi Negocio menu", new CheckedRunnable() {
				@Override
				public void run() {
					stepOpenMiNegocioMenu();
				}
			});
		} else {
			menuOk = false;
			failBlocked("Mi Negocio menu", "Blocked because login did not succeed.");
		}

		final boolean modalOk;
		if (menuOk) {
			modalOk = runStep("Agregar Negocio modal", new CheckedRunnable() {
				@Override
				public void run() {
					stepValidateAgregarNegocioModal();
				}
			});
		} else {
			modalOk = false;
			failBlocked("Agregar Negocio modal", "Blocked because 'Mi Negocio menu' step did not succeed.");
		}

		final boolean adminOk;
		if (menuOk) {
			adminOk = runStep("Administrar Negocios view", new CheckedRunnable() {
				@Override
				public void run() {
					stepOpenAdministrarNegocios();
				}
			});
		} else {
			adminOk = false;
			failBlocked("Administrar Negocios view", "Blocked because 'Mi Negocio menu' step did not succeed.");
		}

		if (adminOk) {
			runStep("Informaci\u00f3n General", new CheckedRunnable() {
				@Override
				public void run() {
					stepValidateInformacionGeneral();
				}
			});

			runStep("Detalles de la Cuenta", new CheckedRunnable() {
				@Override
				public void run() {
					stepValidateDetallesCuenta();
				}
			});

			runStep("Tus Negocios", new CheckedRunnable() {
				@Override
				public void run() {
					stepValidateTusNegocios();
				}
			});

			runStep("T\u00e9rminos y Condiciones", new CheckedRunnable() {
				@Override
				public void run() {
					stepValidateLegalLink("T\u00e9rminos y Condiciones", "T\u00e9rminos y Condiciones",
							"terms-and-conditions.png");
				}
			});

			runStep("Pol\u00edtica de Privacidad", new CheckedRunnable() {
				@Override
				public void run() {
					stepValidateLegalLink("Pol\u00edtica de Privacidad", "Pol\u00edtica de Privacidad",
							"privacy-policy.png");
				}
			});
		} else {
			failBlocked("Informaci\u00f3n General", "Blocked because account administration page did not load.");
			failBlocked("Detalles de la Cuenta", "Blocked because account administration page did not load.");
			failBlocked("Tus Negocios", "Blocked because account administration page did not load.");
			failBlocked("T\u00e9rminos y Condiciones", "Blocked because account administration page did not load.");
			failBlocked("Pol\u00edtica de Privacidad", "Blocked because account administration page did not load.");
		}

		assertAllPassed();
	}

	private void stepLoginWithGoogle() {
		final Locator loginButton = requireVisibleAnyText(appPage, "Sign in with Google", "Iniciar sesi\u00f3n con Google",
				"Continuar con Google", "Google", "Iniciar sesi\u00f3n");

		final int pagesBeforeClick = context.pages().size();
		clickAndWait(appPage, loginButton);
		sleepSilently(SHORT_WAIT_MS);

		final Page possibleGooglePage = detectNewPage(pagesBeforeClick);
		if (possibleGooglePage != null) {
			possibleGooglePage.bringToFront();
			waitForUiLoad(possibleGooglePage);
			selectGoogleAccountIfVisible(possibleGooglePage, GOOGLE_ACCOUNT_EMAIL);
			waitForUiLoad(possibleGooglePage);
			appPage.bringToFront();
		} else {
			selectGoogleAccountIfVisible(appPage, GOOGLE_ACCOUNT_EMAIL);
		}

		waitForUiLoad(appPage);

		assertVisibleAnyText(appPage, "Negocio");
		assertTrue("Main application shell (sidebar/nav) is not visible.",
				isVisible(appPage, "xpath=//aside") || isVisible(appPage, "xpath=//nav"));

		takeScreenshot(appPage, "01-dashboard-loaded.png", true);
	}

	private void stepOpenMiNegocioMenu() {
		assertTrue("Sidebar navigation not visible.", isVisible(appPage, "xpath=//aside") || isVisible(appPage, "xpath=//nav"));
		assertVisibleAnyText(appPage, "Negocio");

		clickByVisibleText(appPage, "Mi Negocio");
		waitForUiLoad(appPage);

		assertVisibleAnyText(appPage, "Agregar Negocio");
		assertVisibleAnyText(appPage, "Administrar Negocios");

		takeScreenshot(appPage, "02-mi-negocio-menu-expanded.png", true);
	}

	private void stepValidateAgregarNegocioModal() {
		clickByVisibleText(appPage, "Agregar Negocio");
		waitForUiLoad(appPage);

		assertVisibleAnyText(appPage, "Crear Nuevo Negocio");
		assertVisibleAnyText(appPage, "Nombre del Negocio");
		assertVisibleAnyText(appPage, "Tienes 2 de 3 negocios");
		assertVisibleAnyText(appPage, "Cancelar");
		assertVisibleAnyText(appPage, "Crear Negocio");

		takeScreenshot(appPage, "03-crear-negocio-modal.png", true);

		final Locator nameInput = firstVisibleLocator(appPage, "xpath=//input[@placeholder='Nombre del Negocio']",
				"xpath=//input[@name='nombre' or @name='nombreNegocio' or @name='businessName']",
				"xpath=//label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]");
		if (nameInput != null) {
			nameInput.click();
			nameInput.fill("Negocio Prueba Automatizacion");
		}

		clickByVisibleText(appPage, "Cancelar");
		waitForUiLoad(appPage);
	}

	private void stepOpenAdministrarNegocios() {
		ensureVisibleByText(appPage, "Administrar Negocios", "Mi Negocio");
		clickByVisibleText(appPage, "Administrar Negocios");
		waitForUiLoad(appPage);

		assertVisibleAnyText(appPage, "Informaci\u00f3n General");
		assertVisibleAnyText(appPage, "Detalles de la Cuenta");
		assertVisibleAnyText(appPage, "Tus Negocios");
		assertVisibleAnyText(appPage, "Secci\u00f3n Legal");

		takeScreenshot(appPage, "04-administrar-negocios-view.png", true);
	}

	private void stepValidateInformacionGeneral() {
		final String bodyText = safeBodyText(appPage);

		assertTrue("User email is not visible.", EMAIL_PATTERN.matcher(bodyText).find());
		assertTrue("User name is not visible.",
				hasVisibleText(appPage, "Nombre") || hasLikelyDisplayNameNearEmail(bodyText));
		assertVisibleAnyText(appPage, "BUSINESS PLAN");
		assertVisibleAnyText(appPage, "Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleAnyText(appPage, "Cuenta creada");
		assertVisibleAnyText(appPage, "Estado activo");
		assertVisibleAnyText(appPage, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleAnyText(appPage, "Tus Negocios");
		assertVisibleAnyText(appPage, "Agregar Negocio");
		assertVisibleAnyText(appPage, "Tienes 2 de 3 negocios");

		// At least one visible row/card/item under business section is expected.
		final boolean businessListVisible = isVisible(appPage,
				"xpath=//*[contains(normalize-space(),'Tus Negocios')]/following::*[self::table or self::ul][1]")
				|| isVisible(appPage,
						"xpath=//*[contains(normalize-space(),'Tus Negocios')]/following::*[contains(@class,'card') or contains(@class,'item')][1]");
		assertTrue("Business list is not visible.", businessListVisible);
	}

	private void stepValidateLegalLink(final String linkText, final String headingText, final String screenshotName) {
		final String originalUrl = appPage.url();
		final int pagesBefore = context.pages().size();

		clickByVisibleText(appPage, linkText);
		waitForUiLoad(appPage);
		sleepSilently(SHORT_WAIT_MS);

		Page legalPage = appPage;
		final Page newPage = detectNewPage(pagesBefore);
		if (newPage != null) {
			legalPage = newPage;
			legalPage.bringToFront();
			waitForUiLoad(legalPage);
		}

		assertVisibleAnyText(legalPage, headingText);
		assertTrue("Legal content text is not visible.",
				safeBodyText(legalPage).replaceAll("\\s+", " ").trim().length() > 180);

		takeScreenshot(legalPage, screenshotName, true);

		final String reportField = "T\u00e9rminos y Condiciones".equals(linkText) ? "T\u00e9rminos y Condiciones"
				: "Pol\u00edtica de Privacidad";
		addDetail(reportField, "Final URL: " + legalPage.url());

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
			return;
		}

		// Same-tab navigation fallback.
		if (!originalUrl.equals(appPage.url())) {
			appPage.goBack();
			waitForUiLoad(appPage);
		}
	}

	private void initResults() {
		results.clear();
		results.put("Login", new StepResult("Login"));
		results.put("Mi Negocio menu", new StepResult("Mi Negocio menu"));
		results.put("Agregar Negocio modal", new StepResult("Agregar Negocio modal"));
		results.put("Administrar Negocios view", new StepResult("Administrar Negocios view"));
		results.put("Informaci\u00f3n General", new StepResult("Informaci\u00f3n General"));
		results.put("Detalles de la Cuenta", new StepResult("Detalles de la Cuenta"));
		results.put("Tus Negocios", new StepResult("Tus Negocios"));
		results.put("T\u00e9rminos y Condiciones", new StepResult("T\u00e9rminos y Condiciones"));
		results.put("Pol\u00edtica de Privacidad", new StepResult("Pol\u00edtica de Privacidad"));
	}

	private void initEvidenceDirectories() throws IOException {
		final String runTs = RUN_TS_FORMAT.format(LocalDateTime.now());
		runDirectory = Paths.get("target", "saleads-evidence", runTs);
		screenshotsDirectory = runDirectory.resolve("screenshots");
		Files.createDirectories(screenshotsDirectory);
	}

	private void initBrowser() {
		playwright = Playwright.create();

		final boolean headless = !Boolean.parseBoolean(
				firstNonBlank(System.getenv("SALEADS_HEADED"), System.getProperty("saleads.headed"), "false"));
		final double slowMo = parseDoubleOrDefault(
				firstNonBlank(System.getenv("SALEADS_SLOWMO_MS"), System.getProperty("saleads.slowmo.ms"), "250"), 250D);

		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(slowMo));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
		appPage = context.newPage();
	}

	private void openLoginPage() {
		final String targetUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getenv("SALEADS_URL"),
				System.getProperty("saleads.login.url"), System.getProperty("saleads.url"));

		assertTrue(
				"No login URL provided. Set SALEADS_LOGIN_URL (or SALEADS_URL / -Dsaleads.login.url / -Dsaleads.url).",
				targetUrl != null && !targetUrl.trim().isEmpty());

		appPage.navigate(targetUrl.trim());
		waitForUiLoad(appPage);
	}

	private boolean runStep(final String stepName, final CheckedRunnable action) {
		try {
			action.run();
			pass(stepName);
			return true;
		} catch (final Throwable throwable) {
			fail(stepName, throwable);
			takeScreenshot(appPage, "failure-" + safeFileName(stepName) + ".png", true);
			return false;
		}
	}

	private void pass(final String stepName) {
		final StepResult result = results.get(stepName);
		result.passed = true;
		if (result.details.isEmpty()) {
			result.details.add("PASS");
		}
	}

	private void fail(final String stepName, final Throwable throwable) {
		final StepResult result = results.get(stepName);
		result.passed = false;
		result.details.add("FAIL: " + throwable.getMessage());
	}

	private void failBlocked(final String stepName, final String reason) {
		final StepResult result = results.get(stepName);
		result.passed = false;
		result.details.add("FAIL: " + reason);
	}

	private void addDetail(final String stepName, final String detail) {
		final StepResult result = results.get(stepName);
		result.details.add(detail);
	}

	private void assertAllPassed() {
		final List<String> failedSteps = new ArrayList<String>();

		for (final StepResult result : results.values()) {
			if (!result.passed) {
				failedSteps.add(result.name);
			}
		}

		assertTrue("One or more workflow validations failed: " + failedSteps, failedSteps.isEmpty());
	}

	private void writeFinalReport() throws IOException {
		if (runDirectory == null) {
			return;
		}

		final StringBuilder report = new StringBuilder();
		report.append("# SaleADS Mi Negocio Workflow Report\n\n");
		report.append("Run timestamp: ").append(LocalDateTime.now()).append("\n\n");
		report.append("## Step Status\n\n");

		for (final StepResult result : results.values()) {
			report.append("- ").append(result.name).append(": ").append(result.passed ? "PASS" : "FAIL").append("\n");
			for (final String detail : result.details) {
				report.append("  - ").append(detail).append("\n");
			}
		}

		report.append("\n## Evidence\n\n");
		report.append("- Screenshots: ").append(screenshotsDirectory.toString()).append("\n");

		Files.writeString(runDirectory.resolve("final-report.md"), report.toString());
	}

	private void closeQuietly() {
		try {
			if (context != null) {
				context.close();
			}
		} catch (final Exception ignored) {
			// no-op
		}

		try {
			if (browser != null) {
				browser.close();
			}
		} catch (final Exception ignored) {
			// no-op
		}

		try {
			if (playwright != null) {
				playwright.close();
			}
		} catch (final Exception ignored) {
			// no-op
		}
	}

	private static boolean isE2eEnabled() {
		return Boolean.parseBoolean(firstNonBlank(System.getenv("SALEADS_E2E_ENABLED"),
				System.getProperty("saleads.e2e.enabled"), "false"));
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value;
			}
		}
		return null;
	}

	private static double parseDoubleOrDefault(final String rawValue, final double defaultValue) {
		try {
			return Double.parseDouble(rawValue);
		} catch (final Exception ignored) {
			return defaultValue;
		}
	}

	private static String safeFileName(final String raw) {
		return raw.toLowerCase().replaceAll("[^a-z0-9]+", "-");
	}

	private static void sleepSilently(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState();
		} catch (final Exception ignored) {
			// no-op
		}

		long waited = 0L;
		while (waited < LONG_WAIT_MS) {
			try {
				page.waitForTimeout(350);
				waited += 350L;
				if (!isVisible(page, "xpath=//*[contains(@class, 'loading')]")
						&& !isVisible(page, "xpath=//*[contains(@class, 'spinner')]")
						&& !isVisible(page, "xpath=//*[contains(@class, 'skeleton')]")) {
					return;
				}
			} catch (final Exception ignored) {
				return;
			}
		}
	}

	private void selectGoogleAccountIfVisible(final Page page, final String accountEmail) {
		final Locator emailLocator = firstVisibleLocator(page, xpathByExactText(accountEmail),
				"xpath=//*[contains(normalize-space(),'" + accountEmail + "')]");
		if (emailLocator != null) {
			clickAndWait(page, emailLocator);
			waitForUiLoad(page);
		}
	}

	private void ensureVisibleByText(final Page page, final String expectedText, final String clickToExposeText) {
		if (hasVisibleText(page, expectedText)) {
			return;
		}

		clickByVisibleText(page, clickToExposeText);
		waitForUiLoad(page);
		assertVisibleAnyText(page, expectedText);
	}

	private void clickByVisibleText(final Page page, final String text) {
		final Locator locator = requireVisibleAnyText(page, text);
		clickAndWait(page, locator);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.scrollIntoViewIfNeeded();
		locator.click();
		waitForUiLoad(page);
	}

	private void assertVisibleAnyText(final Page page, final String... textOptions) {
		final Locator locator = firstVisibleByText(page, textOptions);
		assertTrue("Expected visible text was not found: " + String.join(", ", textOptions), locator != null);
	}

	private Locator requireVisibleAnyText(final Page page, final String... textOptions) {
		final Locator locator = firstVisibleByText(page, textOptions);
		assertTrue("Expected visible text was not found: " + String.join(", ", textOptions), locator != null);
		return locator;
	}

	private Locator firstVisibleByText(final Page page, final String... textOptions) {
		final List<String> selectors = new ArrayList<String>();

		for (final String text : textOptions) {
			selectors.add(xpathByExactText(text));
			selectors.add("xpath=//*[contains(normalize-space()," + xpathLiteral(text) + ")]");
		}

		return firstVisibleLocator(page, selectors.toArray(new String[0]));
	}

	private Locator firstVisibleLocator(final Page page, final String... selectors) {
		for (final String selector : selectors) {
			final Locator locator = page.locator(selector);
			final int count = locator.count();
			for (int i = 0; i < count; i++) {
				final Locator candidate = locator.nth(i);
				if (candidate.isVisible()) {
					return candidate;
				}
			}
		}
		return null;
	}

	private static String xpathByExactText(final String text) {
		return "xpath=//*[normalize-space()=" + xpathLiteral(text) + "]";
	}

	private static String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}
		return "concat('" + text.replace("'", "',\"'\",'") + "')";
	}

	private boolean hasVisibleText(final Page page, final String text) {
		return firstVisibleByText(page, text) != null;
	}

	private boolean isVisible(final Page page, final String selector) {
		try {
			final Locator locator = page.locator(selector).first();
			return locator != null && locator.isVisible();
		} catch (final Exception ignored) {
			return false;
		}
	}

	private String safeBodyText(final Page page) {
		try {
			return page.locator("body").innerText();
		} catch (final Exception ignored) {
			return "";
		}
	}

	private boolean hasLikelyDisplayNameNearEmail(final String bodyText) {
		final Matcher matcher = EMAIL_PATTERN.matcher(bodyText);
		if (!matcher.find()) {
			return false;
		}

		final String prefix = bodyText.substring(0, matcher.start()).trim();
		if (prefix.isEmpty()) {
			return false;
		}

		final String[] lines = prefix.split("\\R");
		for (int i = lines.length - 1; i >= 0; i--) {
			final String candidate = lines[i].trim();
			if (candidate.isEmpty()) {
				continue;
			}
			if (candidate.length() >= 3 && !candidate.contains("@") && !candidate.toLowerCase().contains("informaci")) {
				return true;
			}
		}
		return false;
	}

	private Page detectNewPage(final int pagesBeforeClick) {
		long waited = 0L;
		while (waited < LONG_WAIT_MS) {
			if (context.pages().size() > pagesBeforeClick) {
				return context.pages().get(context.pages().size() - 1);
			}
			sleepSilently(250L);
			waited += 250L;
		}
		return null;
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) {
		if (page == null || screenshotsDirectory == null) {
			return;
		}

		try {
			page.screenshot(new Page.ScreenshotOptions().setPath(screenshotsDirectory.resolve(fileName))
					.setFullPage(fullPage));
		} catch (final Exception ignored) {
			// no-op
		}
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final String name;
		private boolean passed;
		private final List<String> details = new ArrayList<String>();

		private StepResult(final String name) {
			this.name = name;
			this.passed = false;
		}
	}
}
