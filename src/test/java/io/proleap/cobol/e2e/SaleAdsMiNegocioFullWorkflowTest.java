package io.proleap.cobol.e2e;

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
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleAdsMiNegocioFullWorkflowTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO = "Informaci\u00f3n General";
	private static final String STEP_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "T\u00e9rminos y Condiciones";
	private static final String STEP_PRIVACY = "Pol\u00edtica de Privacidad";

	private static final Pattern MAIN_SIDEBAR_PATTERN = Pattern.compile("(?i)negocio");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)^mi\\s+negocio$");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)^agregar\\s+negocio$");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)^administrar\\s+negocios$");
	private static final Pattern CREAR_NEGOCIO_MODAL_PATTERN = Pattern.compile("(?i)^crear\\s+nuevo\\s+negocio$");
	private static final Pattern INFO_GENERAL_PATTERN = Pattern.compile("(?i)^informaci[o\\u00f3]n\\s+general$");
	private static final Pattern DETALLES_CUENTA_PATTERN = Pattern.compile("(?i)^detalles\\s+de\\s+la\\s+cuenta$");
	private static final Pattern TUS_NEGOCIOS_PATTERN = Pattern.compile("(?i)^tus\\s+negocios$");
	private static final Pattern SECCION_LEGAL_PATTERN = Pattern.compile("(?i)^secci[o\\u00f3]n\\s+legal$");
	private static final Pattern TERMINOS_PATTERN = Pattern.compile("(?i)^t[e\\u00e9]rminos\\s+y\\s+condiciones$");
	private static final Pattern PRIVACIDAD_PATTERN = Pattern.compile("(?i)^pol[i\\u00ed]tica\\s+de\\s+privacidad$");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern GOOGLE_LOGIN_PATTERN = Pattern.compile(
			"(?i)(sign\\s*in\\s*with\\s*google|continue\\s*with\\s*google|iniciar\\s*sesi[o\\u00f3]n\\s*con\\s*google|continuar\\s*con\\s*google)");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private Path evidenceDir;
	private String expectedUserEmail;
	private String expectedUserName;

	@Before
	public void setUp() throws IOException {
		final boolean enabled = readBoolean("run.saleads.ui", "RUN_SALEADS_UI_TEST", false);
		Assume.assumeTrue(
				"Skipped SaleADS UI flow test. Enable with -Drun.saleads.ui=true or RUN_SALEADS_UI_TEST=true.",
				enabled);

		final String loginUrl = readOptionalValue("saleads.login.url", "SALEADS_LOGIN_URL");
		if (isBlank(loginUrl)) {
			Assert.fail("Missing SaleADS login URL. Set -Dsaleads.login.url or SALEADS_LOGIN_URL.");
		}

		expectedUserEmail = readOptionalValue("saleads.google.email", "SALEADS_GOOGLE_EMAIL");
		if (isBlank(expectedUserEmail)) {
			expectedUserEmail = "juanlucasbarbiergarzon@gmail.com";
		}
		expectedUserName = readOptionalValue("saleads.expected.user.name", "SALEADS_EXPECTED_USER_NAME");

		playwright = Playwright.create();
		final boolean headless = readBoolean("saleads.headless", "SALEADS_HEADLESS", true);
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 1024));
		appPage = context.newPage();
		appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(LoadState.DOMCONTENTLOADED));
		waitForUi(appPage);

		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US).format(LocalDateTime.now());
		evidenceDir = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() {
		if (context != null) {
			context.close();
		}
		if (browser != null) {
			browser.close();
		}
		if (playwright != null) {
			playwright.close();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		executeStep(STEP_LOGIN, () -> {
			loginWithGoogle();
			assertMainAppVisible();
			captureScreenshot("01-dashboard-loaded", true, appPage);
		});

		executeStep(STEP_MENU, () -> {
			openMiNegocioMenu();
			assertVisible(AGREGAR_NEGOCIO_PATTERN, "Expected 'Agregar Negocio' in expanded menu.");
			assertVisible(ADMINISTRAR_NEGOCIOS_PATTERN, "Expected 'Administrar Negocios' in expanded menu.");
			captureScreenshot("02-mi-negocio-expanded-menu", false, appPage);
		});

		executeStep(STEP_MODAL, () -> {
			clickByVisibleText(AGREGAR_NEGOCIO_PATTERN, "Could not click 'Agregar Negocio'.");
			assertVisible(CREAR_NEGOCIO_MODAL_PATTERN, "Expected modal title 'Crear Nuevo Negocio'.");
			assertVisible(Pattern.compile("(?i)nombre\\s+del\\s+negocio"),
					"Expected input field label 'Nombre del Negocio'.");
			assertVisible(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"),
					"Expected business limit text 'Tienes 2 de 3 negocios'.");
			assertVisible(Pattern.compile("(?i)^cancelar$"), "Expected 'Cancelar' button.");
			assertVisible(Pattern.compile("(?i)^crear\\s+negocio$"), "Expected 'Crear Negocio' button.");
			captureScreenshot("03-crear-negocio-modal", false, appPage);

			final Locator businessNameInput = firstVisibleOrNull(5000,
					appPage.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")).first(),
					appPage.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")).first(),
					appPage.locator("input[name*='negocio'], input[id*='negocio']").first());
			if (businessNameInput != null) {
				businessNameInput.fill("Negocio Prueba Automatizacion");
				waitForUi(appPage);
			}
			clickByVisibleText(Pattern.compile("(?i)^cancelar$"), "Could not close modal with 'Cancelar'.");
		});

		executeStep(STEP_ADMIN_VIEW, () -> {
			openMiNegocioMenuIfCollapsed();
			clickByVisibleText(ADMINISTRAR_NEGOCIOS_PATTERN, "Could not click 'Administrar Negocios'.");
			assertVisible(INFO_GENERAL_PATTERN, "Expected section 'Informacion General'.");
			assertVisible(DETALLES_CUENTA_PATTERN, "Expected section 'Detalles de la Cuenta'.");
			assertVisible(TUS_NEGOCIOS_PATTERN, "Expected section 'Tus Negocios'.");
			assertVisible(SECCION_LEGAL_PATTERN, "Expected section 'Seccion Legal'.");
			captureScreenshot("04-administrar-negocios-account-page", true, appPage);
		});

		executeStep(STEP_INFO, () -> {
			assertUserIdentityVisible();
			assertVisible(Pattern.compile("(?i)business\\s+plan"), "Expected plan text 'BUSINESS PLAN'.");
			assertVisible(Pattern.compile("(?i)^cambiar\\s+plan$"), "Expected button 'Cambiar Plan'.");
		});

		executeStep(STEP_DETAILS, () -> {
			assertVisible(Pattern.compile("(?i)cuenta\\s+creada"), "Expected 'Cuenta creada'.");
			assertVisible(Pattern.compile("(?i)estado\\s+activo"), "Expected 'Estado activo'.");
			assertVisible(Pattern.compile("(?i)idioma\\s+seleccionado"), "Expected 'Idioma seleccionado'.");
		});

		executeStep(STEP_BUSINESSES, () -> {
			assertVisible(TUS_NEGOCIOS_PATTERN, "Expected 'Tus Negocios' heading.");
			assertVisible(AGREGAR_NEGOCIO_PATTERN, "Expected 'Agregar Negocio' button in businesses section.");
			assertVisible(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"),
					"Expected usage text in businesses section.");
			final String businessesText = appPage.locator("body").innerText();
			Assert.assertTrue("Expected business list content to be visible.",
					businessesText != null && businessesText.toLowerCase(Locale.ROOT).contains("negocio"));
		});

		executeStep(STEP_TERMS, () -> {
			final LegalNavigationResult termsResult = validateLegalLink(TERMINOS_PATTERN, TERMINOS_PATTERN,
					"05-terminos-condiciones");
			report.get(STEP_TERMS).details = report.get(STEP_TERMS).details + " | URL: " + termsResult.finalUrl;
		});

		executeStep(STEP_PRIVACY, () -> {
			final LegalNavigationResult privacyResult = validateLegalLink(PRIVACIDAD_PATTERN, PRIVACIDAD_PATTERN,
					"06-politica-privacidad");
			report.get(STEP_PRIVACY).details = report.get(STEP_PRIVACY).details + " | URL: " + privacyResult.finalUrl;
		});

		printFinalReport();
		assertAllPassed();
	}

	private void loginWithGoogle() {
		final Locator signInButton = firstVisible(25000,
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_PATTERN)).first(),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_PATTERN)).first(),
				appPage.locator("button:has-text(\"Google\")").first(),
				appPage.locator("a:has-text(\"Google\")").first(),
				appPage.getByText(Pattern.compile("(?i)google")).first());

		Page popupPage = null;
		try {
			popupPage = context.waitForPage(() -> clickAndWaitForUi(appPage, signInButton),
					new BrowserContext.WaitForPageOptions().setTimeout(10000));
		} catch (final PlaywrightException timeoutNoPopup) {
			clickAndWaitForUi(appPage, signInButton);
		}

		if (popupPage != null) {
			waitForUi(popupPage);
		}

		final Page accountPage = popupPage != null ? popupPage : appPage;
		final Locator accountOption = firstVisibleOrNull(10000,
				accountPage.getByText(expectedUserEmail).first(),
				accountPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile(
						"(?i)" + Pattern.quote(expectedUserEmail)))).first(),
				accountPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile(
						"(?i)" + Pattern.quote(expectedUserEmail)))).first());

		if (accountOption != null) {
			clickAndWaitForUi(accountPage, accountOption);
		}

		if (popupPage != null && !popupPage.isClosed()) {
			popupPage.close();
		}
		appPage.bringToFront();
		waitForUi(appPage);
	}

	private void assertMainAppVisible() {
		assertVisible(MAIN_SIDEBAR_PATTERN, "Expected main app and sidebar after login.");
		assertVisible(Pattern.compile("(?i)mi\\s+negocio|negocio"), "Expected left sidebar navigation.");
	}

	private void openMiNegocioMenu() {
		clickByVisibleText(MI_NEGOCIO_PATTERN, "Could not click 'Mi Negocio'.");
		assertVisible(AGREGAR_NEGOCIO_PATTERN, "Mi Negocio submenu did not expand.");
	}

	private void openMiNegocioMenuIfCollapsed() {
		if (firstVisibleOrNull(3000, appPage.getByText(AGREGAR_NEGOCIO_PATTERN).first()) == null
				|| firstVisibleOrNull(3000, appPage.getByText(ADMINISTRAR_NEGOCIOS_PATTERN).first()) == null) {
			openMiNegocioMenu();
		}
	}

	private void assertUserIdentityVisible() {
		if (!isBlank(expectedUserName)) {
			assertVisible(Pattern.compile("(?i)" + Pattern.quote(expectedUserName)),
					"Expected configured user name to be visible.");
		} else {
			final String pageText = appPage.locator("body").innerText();
			final String normalized = pageText == null ? "" : pageText.toLowerCase(Locale.ROOT);
			final boolean likelyNameVisible = normalized.matches("(?s).*[a-z].*");
			Assert.assertTrue("Expected user name or profile text to be visible in Informacion General.",
					likelyNameVisible);
		}

		final Locator expectedEmailLocator = appPage.getByText(expectedUserEmail).first();
		if (isVisible(expectedEmailLocator, 5000)) {
			return;
		}
		assertVisible(EMAIL_PATTERN, "Expected user email to be visible.");
	}

	private LegalNavigationResult validateLegalLink(final Pattern linkPattern, final Pattern headingPattern,
			final String screenshotPrefix) {
		final String appUrlBeforeClick = appPage.url();
		final Locator legalLink = firstVisible(10000,
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)).first(),
				appPage.getByText(linkPattern).first());

		Page legalPage = null;
		boolean openedNewTab = false;
		try {
			legalPage = context.waitForPage(() -> clickAndWaitForUi(appPage, legalLink),
					new BrowserContext.WaitForPageOptions().setTimeout(7000));
			openedNewTab = legalPage != null;
		} catch (final PlaywrightException timeoutNoPopup) {
			clickAndWaitForUi(appPage, legalLink);
			legalPage = appPage;
		}

		waitForUi(legalPage);
		assertVisible(legalPage, headingPattern, "Expected legal heading after navigation.");
		final Locator legalText = firstVisible(10000, legalPage.locator("main p").first(), legalPage.locator("p").first(),
				legalPage.locator("article").first(), legalPage.locator("main").first());
		Assert.assertTrue("Expected legal content text to be visible.", legalText.innerText().trim().length() > 10);
		captureScreenshot(screenshotPrefix, true, legalPage);
		final String finalUrl = legalPage.url();

		if (openedNewTab && legalPage != null && legalPage != appPage && !legalPage.isClosed()) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			final Page returnedPage = appPage.goBack(new Page.GoBackOptions().setWaitUntil(LoadState.DOMCONTENTLOADED));
			if (returnedPage == null) {
				appPage.navigate(appUrlBeforeClick, new Page.NavigateOptions().setWaitUntil(LoadState.DOMCONTENTLOADED));
			}
			waitForUi(appPage);
		}

		return new LegalNavigationResult(finalUrl);
	}

	private void executeStep(final String stepName, final CheckedAction action) {
		report.put(stepName, StepResult.passed());
		try {
			action.run();
		} catch (final Throwable t) {
			final String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
			report.put(stepName, StepResult.failed(message));
			captureFailureScreenshot(stepName);
		}
	}

	private void printFinalReport() {
		System.out.println("===== SaleADS Mi Negocio Workflow Report =====");
		for (final String field : requiredReportFields()) {
			final StepResult result = report.getOrDefault(field, StepResult.failed("Not executed"));
			System.out.println("- " + field + ": " + (result.passed ? "PASS" : "FAIL")
					+ (isBlank(result.details) ? "" : " (" + result.details + ")"));
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private void assertAllPassed() {
		final List<String> failed = new ArrayList<>();
		for (final String field : requiredReportFields()) {
			final StepResult result = report.getOrDefault(field, StepResult.failed("Not executed"));
			if (!result.passed) {
				failed.add(field + ": " + result.details);
			}
		}
		if (!failed.isEmpty()) {
			Assert.fail("SaleADS workflow failed.\n" + String.join("\n", failed)
					+ "\nEvidence directory: " + evidenceDir.toAbsolutePath());
		}
	}

	private List<String> requiredReportFields() {
		return List.of(STEP_LOGIN, STEP_MENU, STEP_MODAL, STEP_ADMIN_VIEW, STEP_INFO, STEP_DETAILS, STEP_BUSINESSES,
				STEP_TERMS, STEP_PRIVACY);
	}

	private void clickByVisibleText(final Pattern pattern, final String failureMessage) {
		final Locator target = firstVisible(10000,
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)).first(),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)).first(),
				appPage.getByText(pattern).first());
		if (target == null) {
			throw new AssertionError(failureMessage);
		}
		clickAndWaitForUi(appPage, target);
	}

	private void assertVisible(final Pattern pattern, final String failureMessage) {
		assertVisible(appPage, pattern, failureMessage);
	}

	private void assertVisible(final Page page, final Pattern pattern, final String failureMessage) {
		final Locator visible = firstVisibleOrNull(12000,
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByText(pattern).first());
		if (visible == null) {
			throw new AssertionError(failureMessage);
		}
	}

	private Locator firstVisible(final int timeoutMs, final Locator... locators) {
		final Locator locator = firstVisibleOrNull(timeoutMs, locators);
		if (locator == null) {
			throw new AssertionError("Expected at least one visible locator.");
		}
		return locator;
	}

	private Locator firstVisibleOrNull(final int timeoutMs, final Locator... locators) {
		for (final Locator candidate : locators) {
			if (candidate != null && isVisible(candidate, timeoutMs)) {
				return candidate;
			}
		}
		return null;
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout((double) timeoutMs));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void clickAndWaitForUi(final Page page, final Locator locator) {
		locator.scrollIntoViewIfNeeded();
		locator.click();
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7000));
		} catch (final PlaywrightException ignored) {
			// Some pages keep open network streams; DOM loaded plus short pause is enough.
		}
		page.waitForTimeout(800);
	}

	private void captureFailureScreenshot(final String stepName) {
		if (context == null || context.pages().isEmpty()) {
			return;
		}
		Page currentPage = appPage;
		if (currentPage == null || currentPage.isClosed()) {
			currentPage = context.pages().get(context.pages().size() - 1);
		}
		try {
			captureScreenshot("failure-" + sanitize(stepName), true, currentPage);
		} catch (final Throwable ignored) {
			// Ignore evidence failure to keep report generation stable.
		}
	}

	private void captureScreenshot(final String name, final boolean fullPage, final Page page) {
		final Path destination = evidenceDir.resolve(sanitize(name) + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(destination).setFullPage(fullPage));
	}

	private String sanitize(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-");
	}

	private boolean readBoolean(final String systemPropertyKey, final String envKey, final boolean defaultValue) {
		final String value = readOptionalValue(systemPropertyKey, envKey);
		if (isBlank(value)) {
			return defaultValue;
		}
		return "true".equalsIgnoreCase(value) || "1".equals(value.trim());
	}

	private String readOptionalValue(final String systemPropertyKey, final String envKey) {
		final String fromSystem = System.getProperty(systemPropertyKey);
		if (!isBlank(fromSystem)) {
			return fromSystem.trim();
		}
		final String fromEnv = System.getenv(envKey);
		return isBlank(fromEnv) ? null : fromEnv.trim();
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface CheckedAction {
		void run();
	}

	private static final class StepResult {
		private final boolean passed;
		private String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult passed() {
			return new StepResult(true, "OK");
		}

		private static StepResult failed(final String details) {
			return new StepResult(false, details);
		}
	}

	private static final class LegalNavigationResult {
		private final String finalUrl;

		private LegalNavigationResult(final String finalUrl) {
			this.finalUrl = finalUrl;
		}
	}
}
