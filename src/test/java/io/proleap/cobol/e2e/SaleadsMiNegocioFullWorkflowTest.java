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
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private Path evidenceDir;
	private Path reportFile;

	@Before
	public void setUp() throws IOException {
		playwright = Playwright.create();

		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));

		final Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setViewportSize(1600, 1200);
		final String storageStatePath = System.getenv("SALEADS_STORAGE_STATE");
		if (storageStatePath != null && !storageStatePath.isBlank()) {
			final Path storageState = Paths.get(storageStatePath);
			if (Files.exists(storageState)) {
				contextOptions.setStorageStatePath(storageState);
			}
		}

		context = browser.newContext(contextOptions);
		page = context.newPage();
		page.setDefaultTimeout(Long.parseLong(System.getenv().getOrDefault("SALEADS_TIMEOUT_MS", "20000")));

		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		evidenceDir = Paths.get("target", "saleads-artifacts", timestamp);
		Files.createDirectories(evidenceDir);
		reportFile = evidenceDir.resolve("final-report.txt");
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
	public void saleadsMiNegocioFullTest() throws IOException {
		final Map<String, String> report = new LinkedHashMap<>();
		final Map<String, String> evidenceUrls = new LinkedHashMap<>();

		report.put("Login", executeStep(() -> {
			navigateToLogin();
			clickGoogleSignInIfVisible();
			selectGoogleAccountIfVisible();
			waitForMainApp();
			captureScreenshot(page, "01-dashboard-loaded", false);
		}));

		report.put("Mi Negocio menu", executeStep(() -> {
			expandMiNegocioMenu();
			assertVisibleText(page, "Agregar Negocio");
			assertVisibleText(page, "Administrar Negocios");
			captureScreenshot(page, "02-mi-negocio-menu-expanded", false);
		}));

		report.put("Agregar Negocio modal", executeStep(() -> {
			clickByText(page, "Agregar Negocio");
			waitForUi(page);
			assertVisibleText(page, "Crear Nuevo Negocio");
			assertVisibleText(page, "Nombre del Negocio");
			assertVisibleText(page, "Tienes 2 de 3 negocios");
			assertVisibleText(page, "Cancelar");
			assertVisibleText(page, "Crear Negocio");
			captureScreenshot(page, "03-agregar-negocio-modal", false);

			final Locator nombreInput = page.getByLabel(Pattern.compile("(?i)Nombre del Negocio")).first();
			if (isVisible(nombreInput, 3000)) {
				nombreInput.click();
				nombreInput.fill("Negocio Prueba Automatización");
			}

			clickByText(page, "Cancelar");
			waitForUi(page);
		}));

		report.put("Administrar Negocios view", executeStep(() -> {
			expandMiNegocioMenu();
			clickByText(page, "Administrar Negocios");
			waitForUi(page);
			assertVisibleText(page, "Información General");
			assertVisibleText(page, "Detalles de la Cuenta");
			assertVisibleText(page, "Tus Negocios");
			assertVisibleText(page, "Sección Legal");
			captureScreenshot(page, "04-administrar-negocios-view", true);
		}));

		report.put("Información General", executeStep(() -> {
			assertUserIdentityVisible();
			assertVisibleText(page, "BUSINESS PLAN");
			assertVisibleText(page, "Cambiar Plan");
		}));

		report.put("Detalles de la Cuenta", executeStep(() -> {
			assertVisibleText(page, "Cuenta creada");
			assertVisibleText(page, "Estado activo");
			assertVisibleText(page, "Idioma seleccionado");
		}));

		report.put("Tus Negocios", executeStep(() -> {
			assertVisibleText(page, "Tus Negocios");
			assertVisibleText(page, "Agregar Negocio");
			assertVisibleText(page, "Tienes 2 de 3 negocios");
		}));

		report.put("Términos y Condiciones", executeStep(() -> {
			final String url = validateLegalLink("Términos y Condiciones", "08-terminos-y-condiciones");
			evidenceUrls.put("Términos y Condiciones URL", url);
		}));

		report.put("Política de Privacidad", executeStep(() -> {
			final String url = validateLegalLink("Política de Privacidad", "09-politica-de-privacidad");
			evidenceUrls.put("Política de Privacidad URL", url);
		}));

		writeFinalReport(report, evidenceUrls);
		assertNoFailures(report);
	}

	private void navigateToLogin() {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assert.assertTrue(
				"Environment variable SALEADS_LOGIN_URL is required (do not hardcode environment domain in test code).",
				loginUrl != null && !loginUrl.isBlank());
		page.navigate(loginUrl);
		waitForUi(page);
	}

	private void clickGoogleSignInIfVisible() {
		final Locator buttonByRole = page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*"))).first();
		final Locator linkByRole = page.getByRole(AriaRole.LINK,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*"))).first();
		final Locator buttonByText = page.getByText(Pattern.compile("(?i)(sign in|iniciar|continuar).{0,20}google")).first();

		if (isVisible(buttonByRole, 3000)) {
			clickWithOptionalPopup(buttonByRole);
			return;
		}
		if (isVisible(linkByRole, 3000)) {
			clickWithOptionalPopup(linkByRole);
			return;
		}
		if (isVisible(buttonByText, 3000)) {
			clickWithOptionalPopup(buttonByText);
		}
	}

	private void clickWithOptionalPopup(final Locator clickable) {
		try {
			final Page popup = context.waitForPage(clickable::click,
					new BrowserContext.WaitForPageOptions().setTimeout(5000));
			waitForUi(popup);
		} catch (final PlaywrightException ignored) {
			clickable.click();
		}
		waitForUi(page);
	}

	private void selectGoogleAccountIfVisible() {
		Page accountPage = page;
		for (final Page openPage : context.pages()) {
			if (openPage != page) {
				accountPage = openPage;
			}
		}

		final Locator accountOption = accountPage.getByText(GOOGLE_ACCOUNT_EMAIL).first();
		if (isVisible(accountOption, 8000)) {
			accountOption.click();
			waitForUi(accountPage);
			waitForUi(page);
		}
	}

	private void waitForMainApp() {
		// Wait for the dashboard/main app container to be visible.
		final Locator sidebar = page.locator("aside, nav").first();
		if (isVisible(sidebar, 20000)) {
			return;
		}

		assertVisibleText(page, "Negocio");
	}

	private void expandMiNegocioMenu() {
		if (isVisible(page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(true)).first(), 1500)
				&& isVisible(page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true)).first(), 1500)) {
			return;
		}

		final Locator negocio = page.getByText(Pattern.compile("(?i)^Negocio$")).first();
		if (isVisible(negocio, 3000)) {
			negocio.click();
			waitForUi(page);
		}

		final Locator miNegocio = page.getByText(Pattern.compile("(?i)^Mi\\s*Negocio$")).first();
		if (isVisible(miNegocio, 3000)) {
			miNegocio.click();
			waitForUi(page);
		}
	}

	private String validateLegalLink(final String linkText, final String screenshotName) throws IOException {
		final Page appPage = page;
		final Locator link = page.getByText(linkText, new Page.GetByTextOptions().setExact(true)).first();
		Assert.assertTrue("Legal link not visible: " + linkText, isVisible(link, 10000));

		Page destinationPage = appPage;
		try {
			destinationPage = context.waitForPage(link::click,
					new BrowserContext.WaitForPageOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			link.click();
		}

		waitForUi(destinationPage);
		assertVisibleText(destinationPage, linkText);
		final String bodyText = destinationPage.locator("body").textContent();
		Assert.assertTrue("Legal content text should be visible for: " + linkText,
				bodyText != null && bodyText.replaceAll("\\s+", " ").trim().length() > 120);

		captureScreenshot(destinationPage, screenshotName, true);
		final String finalUrl = destinationPage.url();

		if (destinationPage != appPage) {
			destinationPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private String executeStep(final StepAction action) {
		try {
			action.run();
			return "PASS";
		} catch (final Throwable error) {
			try {
				captureScreenshot(page, "failure-" + System.currentTimeMillis(), true);
			} catch (final Exception ignored) {
				// Best effort evidence capture.
			}
			return "FAIL - " + error.getMessage();
		}
	}

	private void clickByText(final Page targetPage, final String text) {
		final Locator exact = targetPage.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
		if (isVisible(exact, 4000)) {
			exact.click();
			waitForUi(targetPage);
			return;
		}

		final Locator loose = targetPage.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)).first();
		Assert.assertTrue("Could not find clickable text: " + text, isVisible(loose, 6000));
		loose.click();
		waitForUi(targetPage);
	}

	private void assertVisibleText(final Page targetPage, final String text) {
		final Locator locator = targetPage.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
		if (isVisible(locator, 4000)) {
			return;
		}

		final Locator fallback = targetPage.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)).first();
		Assert.assertTrue("Expected visible text: " + text, isVisible(fallback, 10000));
	}

	private void assertVisibleAny(final Page targetPage, final Pattern... patterns) {
		for (final Pattern pattern : patterns) {
			if (isVisible(targetPage.getByText(pattern).first(), 4000)) {
				return;
			}
		}
		Assert.fail("None of expected patterns were visible.");
	}

	private void assertUserIdentityVisible() {
		final String expectedName = System.getenv("SALEADS_EXPECTED_USER_NAME");
		final String expectedEmail = System.getenv("SALEADS_EXPECTED_USER_EMAIL");
		if (expectedName != null && !expectedName.isBlank()) {
			assertVisibleText(page, expectedName);
		}

		if (expectedEmail != null && !expectedEmail.isBlank()) {
			assertVisibleText(page, expectedEmail);
			return;
		}

		// Fallback for environment-agnostic checks when the exact email is not configured.
		assertVisibleAny(page, Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+"));
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout((double) timeoutMs));
			return locator.isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void waitForUi(final Page targetPage) {
		targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			targetPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(6000));
		} catch (final PlaywrightException ignored) {
			// Some pages keep long-polling connections. Continue after DOM is ready.
		}
		targetPage.waitForTimeout(600);
	}

	private void captureScreenshot(final Page targetPage, final String name, final boolean fullPage) throws IOException {
		targetPage.screenshot(new Page.ScreenshotOptions()
				.setPath(evidenceDir.resolve(name + ".png"))
				.setFullPage(fullPage));
	}

	private void writeFinalReport(final Map<String, String> report, final Map<String, String> evidenceUrls) throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio - Final Report\n");
		sb.append("================================\n");
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}
		if (!evidenceUrls.isEmpty()) {
			sb.append('\n').append("Final URLs\n");
			sb.append("----------\n");
			for (final Map.Entry<String, String> entry : evidenceUrls.entrySet()) {
				sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}
		sb.append('\n').append("Evidence folder: ").append(evidenceDir.toAbsolutePath()).append('\n');
		Files.writeString(reportFile, sb.toString());
	}

	private void assertNoFailures(final Map<String, String> report) {
		final StringBuilder failed = new StringBuilder();
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			if (!entry.getValue().startsWith("PASS")) {
				failed.append('\n').append(entry.getKey()).append(" -> ").append(entry.getValue());
			}
		}

		if (failed.length() > 0) {
			Assert.fail("One or more validations failed. See report: " + reportFile.toAbsolutePath() + failed);
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
