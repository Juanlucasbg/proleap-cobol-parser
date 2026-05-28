package io.proleap.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * End-to-end SaleADS Mi Negocio workflow automation.
 *
 * <p>Execution notes:
 * <ul>
 *   <li>Set SALEADS_LOGIN_URL to the current environment login URL (dev/staging/prod).</li>
 *   <li>Set SALEADS_GOOGLE_ACCOUNT_EMAIL if a different Google account should be selected.</li>
 *   <li>Run manually with: mvn -Dtest=SaleadsMiNegocioWorkflowIT test</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowIT {

	private static final DateTimeFormatter TS_FORMATTER =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);

	private static final Pattern LOGIN_BUTTON_PATTERN =
			Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|google)");
	private static final Pattern BUSINESS_PLAN_PATTERN = Pattern.compile("(?i)BUSINESS\\s+PLAN");
	private static final Pattern CREATED_ACCOUNT_PATTERN = Pattern.compile("(?i)Cuenta\\s+creada");
	private static final Pattern ACTIVE_STATUS_PATTERN = Pattern.compile("(?i)Estado\\s+activo");
	private static final Pattern SELECTED_LANGUAGE_PATTERN = Pattern.compile("(?i)Idioma\\s+seleccionado");
	private static final Pattern EMAIL_PATTERN =
			Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern NAME_LABEL_PATTERN =
			Pattern.compile("(?i)(nombre|usuario|user\\s*name|name)");
	private static final Pattern TERMS_HEADING_PATTERN =
			Pattern.compile("(?i)T[eé]rminos\\s+y\\s+Condiciones");
	private static final Pattern PRIVACY_HEADING_PATTERN =
			Pattern.compile("(?i)Pol[ií]tica\\s+de\\s+Privacidad");

	private final Map<String, StepResult> results = new LinkedHashMap<>();
	private final Map<String, String> capturedUrls = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private Path artifactsDir;

	@Before
	public void setUp() throws IOException {
		playwright = Playwright.create();
		final boolean headed = getBooleanConfig("SALEADS_HEADED", false);
		final double slowMoMs = getDoubleConfig("SALEADS_SLOW_MO_MS", 120);

		browser = playwright.chromium().launch(new BrowserTypeLaunchOptionsBuilder()
				.setHeadless(!headed)
				.setSlowMo(slowMoMs)
				.build());
		context = browser.newContext();
		page = context.newPage();

		artifactsDir = Paths.get("target", "saleads-e2e", TS_FORMATTER.format(Instant.now()));
		Files.createDirectories(artifactsDir);

		final String loginUrl = getRequiredConfig("SALEADS_LOGIN_URL");
		page.navigate(loginUrl);
		waitForUiLoad(page);
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();

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
		final String googleAccountEmail = getConfig("SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com");

		executeStep("Login", () -> loginWithGoogle(googleAccountEmail));
		executeStep("Mi Negocio menu", this::openMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::openAdministrarNegocios);
		executeStep("Informacion General", this::validateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
		executeStep("Tus Negocios", this::validateTusNegocios);
		executeStep("Terminos y Condiciones", () -> validateLegalLink("Términos y Condiciones", TERMS_HEADING_PATTERN, "08-terminos-y-condiciones.png"));
		executeStep("Politica de Privacidad", () -> validateLegalLink("Política de Privacidad", PRIVACY_HEADING_PATTERN, "09-politica-de-privacidad.png"));

		final List<String> failed = results.entrySet().stream()
				.filter(entry -> !entry.getValue().pass)
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

		Assert.assertTrue("Workflow has failing steps: " + failed, failed.isEmpty());
	}

	private void loginWithGoogle(final String googleAccountEmail) {
		final Locator loginButton = findByVisibleText(page, LOGIN_BUTTON_PATTERN);
		loginButton.click();
		waitForUiLoad(page);

		selectGoogleAccountIfVisible(googleAccountEmail);
		waitForUiLoad(page);

		assertVisibleText(page, "Negocio");
		screenshot("01-dashboard-loaded.png", false);
	}

	private void openMiNegocioMenu() {
		clickByVisibleText(page, "Negocio");
		waitForUiLoad(page);

		clickByVisibleText(page, "Mi Negocio");
		waitForUiLoad(page);

		assertVisibleText(page, "Agregar Negocio");
		assertVisibleText(page, "Administrar Negocios");
		screenshot("02-mi-negocio-menu-expanded.png", false);
	}

	private void validateAgregarNegocioModal() {
		clickByVisibleText(page, "Agregar Negocio");
		waitForUiLoad(page);

		assertVisibleRegex(page, Pattern.compile("(?i)Crear\\s+Nuevo\\s+Negocio"));
		assertVisibleRegex(page, Pattern.compile("(?i)Nombre\\s+del\\s+Negocio"));
		assertVisibleRegex(page, Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios"));
		assertVisibleText(page, "Cancelar");
		assertVisibleRegex(page, Pattern.compile("(?i)Crear\\s+Negocio"));

		final Locator businessNameInput = firstVisible(page.locator("input,textarea"));
		if (businessNameInput != null) {
			businessNameInput.click();
			businessNameInput.fill("Negocio Prueba Automatización");
		}

		screenshot("03-agregar-negocio-modal.png", false);
		clickByVisibleText(page, "Cancelar");
		waitForUiLoad(page);
	}

	private void openAdministrarNegocios() {
		clickByVisibleText(page, "Mi Negocio");
		waitForUiLoad(page);
		clickByVisibleText(page, "Administrar Negocios");
		waitForUiLoad(page);

		assertVisibleRegex(page, Pattern.compile("(?i)Informaci[oó]n\\s+General"));
		assertVisibleRegex(page, Pattern.compile("(?i)Detalles\\s+de\\s+la\\s+Cuenta"));
		assertVisibleRegex(page, Pattern.compile("(?i)Tus\\s+Negocios"));
		assertVisibleRegex(page, Pattern.compile("(?i)Secci[oó]n\\s+Legal"));
		screenshot("04-administrar-negocios-full-page.png", true);
	}

	private void validateInformacionGeneral() {
		assertVisibleRegex(page, NAME_LABEL_PATTERN);
		assertVisibleRegex(page, EMAIL_PATTERN);
		assertVisibleRegex(page, BUSINESS_PLAN_PATTERN);
		assertVisibleRegex(page, Pattern.compile("(?i)Cambiar\\s+Plan"));
	}

	private void validateDetallesDeLaCuenta() {
		assertVisibleRegex(page, CREATED_ACCOUNT_PATTERN);
		assertVisibleRegex(page, ACTIVE_STATUS_PATTERN);
		assertVisibleRegex(page, SELECTED_LANGUAGE_PATTERN);
	}

	private void validateTusNegocios() {
		assertVisibleRegex(page, Pattern.compile("(?i)Tus\\s+Negocios"));
		assertVisibleRegex(page, Pattern.compile("(?i)Agregar\\s+Negocio"));
		assertVisibleRegex(page, Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios"));
	}

	private void validateLegalLink(final String linkText, final Pattern headingPattern, final String screenshotName) {
		final String originalUrl = page.url();
		Page legalPage = page;
		boolean openedNewTab = false;

		try {
			legalPage = context.waitForPage(() -> clickByVisibleText(page, linkText), new BrowserContext.WaitForPageOptions().setTimeout(5000));
			openedNewTab = true;
		} catch (PlaywrightException ignored) {
			clickByVisibleText(page, linkText);
		}

		waitForUiLoad(legalPage);
		assertVisibleRegex(legalPage, headingPattern);

		final String bodyText = Objects.toString(legalPage.locator("body").innerText(), "");
		Assert.assertTrue("Expected legal content text to be visible for " + linkText, bodyText.trim().length() > 150);

		screenshot(legalPage, screenshotName, true);
		capturedUrls.put(linkText, legalPage.url());

		if (openedNewTab) {
			legalPage.close();
			page.bringToFront();
		} else {
			page.navigate(originalUrl);
			waitForUiLoad(page);
		}
	}

	private void executeStep(final String stepName, final StepAction action) {
		try {
			action.run();
			results.put(stepName, StepResult.pass("PASS"));
		} catch (Throwable throwable) {
			results.put(stepName, StepResult.fail(throwable.getMessage()));
			try {
				screenshot("error-" + normalizeFileName(stepName) + ".png", true);
			} catch (Throwable ignored) {
				// Ignore screenshot errors to preserve original failure.
			}
		}
	}

	private Locator findByVisibleText(final Page targetPage, final Pattern textPattern) {
		Locator locator = targetPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)).first();
		if (safeVisible(locator)) {
			return locator;
		}

		locator = targetPage.getByText(textPattern).first();
		if (safeVisible(locator)) {
			return locator;
		}

		throw new AssertionError("Could not find visible element by text pattern: " + textPattern.pattern());
	}

	private void clickByVisibleText(final Page targetPage, final String text) {
		final Pattern textPattern = Pattern.compile("(?i)" + Pattern.quote(text));
		final Locator byRole = targetPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern)).first();
		if (safeVisible(byRole)) {
			byRole.click();
			waitForUiLoad(targetPage);
			return;
		}

		final Locator byButtonRole = targetPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)).first();
		if (safeVisible(byButtonRole)) {
			byButtonRole.click();
			waitForUiLoad(targetPage);
			return;
		}

		final Locator byText = targetPage.getByText(textPattern).first();
		if (safeVisible(byText)) {
			byText.click();
			waitForUiLoad(targetPage);
			return;
		}

		throw new AssertionError("Could not click visible element with text: " + text);
	}

	private void assertVisibleText(final Page targetPage, final String text) {
		final Pattern pattern = Pattern.compile("(?i)" + Pattern.quote(text));
		assertVisibleRegex(targetPage, pattern);
	}

	private void assertVisibleRegex(final Page targetPage, final Pattern textPattern) {
		final Locator locator = targetPage.getByText(textPattern).first();
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(12000));
		} catch (TimeoutError timeoutError) {
			throw new AssertionError("Expected visible text matching pattern: " + textPattern.pattern(), timeoutError);
		}
	}

	private void selectGoogleAccountIfVisible(final String accountEmail) {
		final List<Page> allPages = new ArrayList<>(context.pages());
		for (Page candidate : allPages) {
			if (isGoogleAccountSelection(candidate)) {
				final Pattern accountPattern = Pattern.compile("(?i)" + Pattern.quote(accountEmail));
				final Locator account = candidate.getByText(accountPattern).first();
				if (safeVisible(account)) {
					account.click();
					waitForUiLoad(candidate);
				}
				return;
			}
		}
	}

	private boolean isGoogleAccountSelection(final Page candidate) {
		final String url = candidate.url() == null ? "" : candidate.url();
		if (url.contains("accounts.google.com")) {
			return true;
		}

		final Locator chooseAccountText = candidate.getByText(Pattern.compile("(?i)(choose\\s+an\\s+account|elige\\s+una\\s+cuenta)")).first();
		return safeVisible(chooseAccountText);
	}

	private void waitForUiLoad(final Page targetPage) {
		targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			targetPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(6000));
		} catch (PlaywrightException ignored) {
			// Some SPA views never reach network idle; DOM ready state is enough as baseline.
		}
		targetPage.waitForTimeout(500);
	}

	private void screenshot(final String fileName, final boolean fullPage) {
		screenshot(page, fileName, fullPage);
	}

	private void screenshot(final Page targetPage, final String fileName, final boolean fullPage) {
		targetPage.screenshot(new Page.ScreenshotOptions()
				.setPath(artifactsDir.resolve(fileName))
				.setFullPage(fullPage));
	}

	private Locator firstVisible(final Locator locator) {
		final int count = locator.count();
		for (int i = 0; i < count; i++) {
			final Locator nth = locator.nth(i);
			if (safeVisible(nth)) {
				return nth;
			}
		}
		return null;
	}

	private boolean safeVisible(final Locator locator) {
		try {
			return locator != null && locator.isVisible();
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void writeFinalReport() throws IOException {
		if (artifactsDir == null) {
			return;
		}

		final List<String> lines = new ArrayList<>();
		lines.add("# SaleADS Mi Negocio workflow report");
		lines.add("");
		lines.add("Generated at (UTC): " + Instant.now());
		lines.add("");
		lines.add("## Step results");
		lines.add("");

		appendStepResult(lines, "Login");
		appendStepResult(lines, "Mi Negocio menu");
		appendStepResult(lines, "Agregar Negocio modal");
		appendStepResult(lines, "Administrar Negocios view");
		appendStepResult(lines, "Informacion General");
		appendStepResult(lines, "Detalles de la Cuenta");
		appendStepResult(lines, "Tus Negocios");
		appendStepResult(lines, "Terminos y Condiciones");
		appendStepResult(lines, "Politica de Privacidad");

		lines.add("");
		lines.add("## Captured legal URLs");
		lines.add("");
		if (capturedUrls.isEmpty()) {
			lines.add("- (none captured)");
		} else {
			for (Map.Entry<String, String> entry : capturedUrls.entrySet()) {
				lines.add("- " + entry.getKey() + ": " + entry.getValue());
			}
		}

		lines.add("");
		lines.add("## Artifacts directory");
		lines.add("");
		lines.add("- " + artifactsDir.toAbsolutePath());

		Files.write(artifactsDir.resolve("report.md"), lines);
	}

	private void appendStepResult(final List<String> lines, final String stepKey) {
		final StepResult stepResult = results.get(stepKey);
		if (stepResult == null) {
			lines.add("- " + stepKey + ": NOT_EXECUTED");
			return;
		}

		lines.add("- " + stepKey + ": " + (stepResult.pass ? "PASS" : "FAIL") + detailSuffix(stepResult.detail));
	}

	private String detailSuffix(final String detail) {
		if (detail == null || detail.trim().isEmpty()) {
			return "";
		}
		return " (" + detail + ")";
	}

	private String getRequiredConfig(final String key) {
		final String value = getConfig(key, null);
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalStateException("Required config missing: " + key);
		}
		return value;
	}

	private String getConfig(final String key, final String defaultValue) {
		final String sysValue = System.getProperty(key);
		if (sysValue != null && !sysValue.trim().isEmpty()) {
			return sysValue.trim();
		}
		final String envValue = System.getenv(key);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue.trim();
		}
		return defaultValue;
	}

	private boolean getBooleanConfig(final String key, final boolean defaultValue) {
		final String value = getConfig(key, null);
		return value == null ? defaultValue : Boolean.parseBoolean(value);
	}

	private double getDoubleConfig(final String key, final double defaultValue) {
		final String value = getConfig(key, null);
		if (value == null) {
			return defaultValue;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException ignored) {
			return defaultValue;
		}
	}

	private String normalizeFileName(final String input) {
		return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
	}

	private static class BrowserTypeLaunchOptionsBuilder {
		private final com.microsoft.playwright.BrowserType.LaunchOptions options =
				new com.microsoft.playwright.BrowserType.LaunchOptions();

		private BrowserTypeLaunchOptionsBuilder setHeadless(final boolean value) {
			options.setHeadless(value);
			return this;
		}

		private BrowserTypeLaunchOptionsBuilder setSlowMo(final double value) {
			options.setSlowMo(value);
			return this;
		}

		private com.microsoft.playwright.BrowserType.LaunchOptions build() {
			return options;
		}
	}

	private interface StepAction {
		void run();
	}

	private static class StepResult {
		private final boolean pass;
		private final String detail;

		private StepResult(final boolean pass, final String detail) {
			this.pass = pass;
			this.detail = detail;
		}

		private static StepResult pass(final String detail) {
			return new StepResult(true, detail);
		}

		private static StepResult fail(final String detail) {
			return new StepResult(false, detail);
		}
	}
}
