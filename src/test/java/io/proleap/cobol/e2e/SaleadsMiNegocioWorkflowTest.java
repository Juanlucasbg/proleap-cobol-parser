package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
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

public class SaleadsMiNegocioWorkflowTest {

	private static final double DEFAULT_TIMEOUT_MS = 20_000;
	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String loginUrl = valueOrEmpty(System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"SALEADS_LOGIN_URL is required to run this environment-agnostic E2E workflow.",
				!loginUrl.isBlank());

		final Path evidenceDir = createEvidenceDir();
		final LinkedHashMap<String, String> report = initReport();
		final String[] termsUrl = new String[] { "N/A" };
		final String[] privacyUrl = new String[] { "N/A" };

		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
					.setHeadless(isHeadless())
					.setChannel(valueOrNull(System.getenv("SALEADS_BROWSER_CHANNEL")));
			final Browser browser = playwright.chromium().launch(launchOptions);

			final Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
					.setViewportSize(1600, 900);
			final String storageStatePath = valueOrEmpty(System.getenv("SALEADS_STORAGE_STATE_PATH"));
			if (!storageStatePath.isBlank()) {
				contextOptions.setStorageStatePath(Paths.get(storageStatePath));
			}

			final BrowserContext context = browser.newContext(contextOptions);
			context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

			Page appPage = context.newPage();
			appPage.navigate(loginUrl);
			waitForUi(appPage);

			final Page[] pageHolder = new Page[] { appPage };

			executeStep(report, "Login", () -> {
				pageHolder[0] = loginWithGoogle(context, pageHolder[0], evidenceDir);
				assertSidebarVisible(pageHolder[0]);
			});

			executeStep(report, "Mi Negocio menu", () -> openMiNegocioMenu(pageHolder[0], evidenceDir));
			executeStep(report, "Agregar Negocio modal", () -> validateAgregarNegocioModal(pageHolder[0], evidenceDir));
			executeStep(report, "Administrar Negocios view", () -> openAdministrarNegocios(pageHolder[0], evidenceDir));
			executeStep(report, "Informaci\u00F3n General", () -> validateInformacionGeneral(pageHolder[0]));
			executeStep(report, "Detalles de la Cuenta", () -> validateDetallesDeLaCuenta(pageHolder[0]));
			executeStep(report, "Tus Negocios", () -> validateTusNegocios(pageHolder[0]));
			executeStep(report, "T\u00E9rminos y Condiciones", () -> {
				termsUrl[0] = openAndValidateLegalPage(context, pageHolder[0], evidenceDir,
						"T(?:e|\\u00E9)rminos y Condiciones", "T(?:e|\\u00E9)rminos y Condiciones",
						"08-terminos-y-condiciones.png");
			});
			executeStep(report, "Pol\u00EDtica de Privacidad", () -> {
				privacyUrl[0] = openAndValidateLegalPage(context, pageHolder[0], evidenceDir,
						"Pol(?:i|\\u00ED)tica de Privacidad", "Pol(?:i|\\u00ED)tica de Privacidad",
						"09-politica-de-privacidad.png");
			});

			writeFinalReport(evidenceDir, report, termsUrl[0], privacyUrl[0]);
		}

		final String formattedReport = formatReport(report, termsUrl[0], privacyUrl[0]);
		assertTrue("One or more validations failed.\n" + formattedReport, allStepsPassed(report));
	}

	private Page loginWithGoogle(final BrowserContext context, final Page page, final Path evidenceDir) {
		if (isLikelyInsideApp(page)) {
			waitForUi(page);
			assertSidebarVisible(page);
			takeScreenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), false);
			return page;
		}

		final Locator loginButton = findVisibleAction(page,
				"Sign in with Google|Iniciar sesi(?:o|\\u00F3)n con Google|Continuar con Google|Google");

		final int pagesBeforeClick = context.pages().size();
		loginButton.click();
		waitForUi(page);

		final Page potentialGooglePage = waitForNewTab(context, pagesBeforeClick);
		final Page authPage = potentialGooglePage != null ? potentialGooglePage : page;

		selectGoogleAccountIfVisible(authPage);
		waitForUi(authPage);

		final Page appPage;
		if (potentialGooglePage != null) {
			waitForPopupToCloseOrForceClose(potentialGooglePage);
			appPage = page;
			appPage.bringToFront();
		} else {
			appPage = authPage;
		}

		waitForUi(appPage);
		assertVisibleText(appPage, "Negocio|Mi Negocio|Dashboard|Panel");
		assertSidebarVisible(appPage);
		takeScreenshot(appPage, evidenceDir.resolve("01-dashboard-loaded.png"), false);
		return appPage;
	}

	private boolean isLikelyInsideApp(final Page page) {
		return isTextVisible(page, "Mi Negocio|Administrar Negocios|Dashboard|Panel", 3_000)
				&& isVisible(page.locator("body"), 1_500);
	}

	private void openMiNegocioMenu(final Page page, final Path evidenceDir) {
		assertSidebarVisible(page);
		clickVisibleAction(page, "Mi Negocio|Negocio");
		assertVisibleText(page, "Agregar Negocio");
		assertVisibleText(page, "Administrar Negocios");
		takeScreenshot(page, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
	}

	private void validateAgregarNegocioModal(final Page page, final Path evidenceDir) {
		clickVisibleAction(page, "Agregar Negocio");
		assertVisibleText(page, "Crear Nuevo Negocio");
		assertVisibleText(page, "Nombre del Negocio");
		assertVisibleText(page, "Tienes\\s+2\\s+de\\s+3\\s+negocios");
		assertVisibleText(page, "Cancelar");
		assertVisibleText(page, "Crear Negocio");
		takeScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

		final Locator nombreInput = findVisibleInput(page, "Nombre del Negocio");
		nombreInput.fill("Negocio Prueba Automatizacion");
		clickVisibleAction(page, "Cancelar");
	}

	private void openAdministrarNegocios(final Page page, final Path evidenceDir) {
		if (!isTextVisible(page, "Administrar Negocios", 1_500)) {
			clickVisibleAction(page, "Mi Negocio|Negocio");
		}
		clickVisibleAction(page, "Administrar Negocios");
		waitForUi(page);

		assertVisibleText(page, "Informaci(?:o|\\u00F3)n General");
		assertVisibleText(page, "Detalles de la Cuenta");
		assertVisibleText(page, "Tus Negocios");
		assertVisibleText(page, "Secci(?:o|\\u00F3)n Legal");
		takeScreenshot(page, evidenceDir.resolve("04-administrar-negocios-page.png"), true);
	}

	private void validateInformacionGeneral(final Page page) {
		assertVisibleText(page, "Informaci(?:o|\\u00F3)n General");
		assertVisibleText(page, ACCOUNT_EMAIL);
		assertVisibleText(page, "BUSINESS PLAN");
		assertVisibleText(page, "Cambiar Plan");

		final Locator infoSection = page.locator("section,div,article").filter(
				new Locator.FilterOptions().setHasText(Pattern.compile("Informaci(?:o|\\u00F3)n General", Pattern.CASE_INSENSITIVE)))
				.first();
		final String sectionText = infoSection.innerText();
		final boolean userNameFound = Arrays.stream(sectionText.split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isBlank())
				.anyMatch(line -> line.matches(".*[A-Za-z].*")
						&& !line.contains("@")
						&& !line.equalsIgnoreCase("Informacion General")
						&& !line.equalsIgnoreCase("Información General")
						&& !line.equalsIgnoreCase("BUSINESS PLAN")
						&& !line.equalsIgnoreCase("Cambiar Plan"));
		assertTrue("User name should be visible in Informacion General.", userNameFound);
	}

	private void validateDetallesDeLaCuenta(final Page page) {
		assertVisibleText(page, "Cuenta creada");
		assertVisibleText(page, "Estado activo|Activo");
		assertVisibleText(page, "Idioma seleccionado|Idioma");
	}

	private void validateTusNegocios(final Page page) {
		assertVisibleText(page, "Tus Negocios");
		assertVisibleText(page, "Agregar Negocio");
		assertVisibleText(page, "Tienes\\s+2\\s+de\\s+3\\s+negocios");

		final Locator businessListContainer = page.locator("section,div,article").filter(
				new Locator.FilterOptions().setHasText(Pattern.compile("Tus Negocios", Pattern.CASE_INSENSITIVE))).first();
		assertTrue("Business list should be visible.", businessListContainer.innerText().trim().length() > 30);
	}

	private String openAndValidateLegalPage(final BrowserContext context, final Page appPage, final Path evidenceDir,
			final String linkPattern, final String headingPattern, final String screenshotName) {
		assertVisibleText(appPage, "Secci(?:o|\\u00F3)n Legal");

		final Locator link = findVisibleAction(appPage, linkPattern);
		final int pagesBeforeClick = context.pages().size();
		link.click();
		waitForUi(appPage);

		final Page legalPage = waitForNewTab(context, pagesBeforeClick);
		final String finalUrl;

		if (legalPage != null) {
			waitForUi(legalPage);
			assertVisibleText(legalPage, headingPattern);
			assertLegalContent(legalPage);
			takeScreenshot(legalPage, evidenceDir.resolve(screenshotName), true);
			finalUrl = legalPage.url();
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			assertVisibleText(appPage, headingPattern);
			assertLegalContent(appPage);
			takeScreenshot(appPage, evidenceDir.resolve(screenshotName), true);
			finalUrl = appPage.url();
			try {
				appPage.goBack();
				waitForUi(appPage);
			} catch (PlaywrightException ignored) {
				// no-op
			}
		}

		return finalUrl;
	}

	private void assertLegalContent(final Page page) {
		final String legalText = page.locator("body").innerText();
		assertTrue("Legal content text is expected to be visible.", legalText != null && legalText.trim().length() > 120);
	}

	private void selectGoogleAccountIfVisible(final Page authPage) {
		waitForUi(authPage);
		if (isTextVisible(authPage, ACCOUNT_EMAIL, 6_000)) {
			clickVisibleAction(authPage, Pattern.quote(ACCOUNT_EMAIL));
			waitForUi(authPage);
		}
	}

	private void waitForPopupToCloseOrForceClose(final Page popupPage) {
		final int maxAttempts = 40;
		for (int i = 0; i < maxAttempts; i++) {
			if (popupPage.isClosed()) {
				return;
			}
			popupPage.waitForTimeout(500);
		}
		try {
			popupPage.close();
		} catch (PlaywrightException ignored) {
			// no-op
		}
	}

	private Locator findVisibleAction(final Page page, final String regexPattern) {
		final Pattern pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		final Locator[] candidates = new Locator[] {
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByText(pattern).first() };

		return Arrays.stream(candidates).filter(locator -> isVisible(locator, 3_500)).findFirst()
				.orElseThrow(() -> new AssertionError("No visible clickable element found for pattern: " + regexPattern));
	}

	private Locator findVisibleInput(final Page page, final String labelPattern) {
		final Pattern pattern = Pattern.compile(labelPattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		final Locator[] candidates = new Locator[] {
				page.getByLabel(pattern).first(),
				page.getByPlaceholder(pattern).first(),
				page.locator("input").filter(new Locator.FilterOptions().setHasText(pattern)).first(),
				page.locator("input").first() };

		return Arrays.stream(candidates).filter(locator -> isVisible(locator, 3_500)).findFirst()
				.orElseThrow(() -> new AssertionError("No visible input found for label pattern: " + labelPattern));
	}

	private void clickVisibleAction(final Page page, final String regexPattern) {
		final Locator action = findVisibleAction(page, regexPattern);
		action.click();
		waitForUi(page);
	}

	private void assertVisibleText(final Page page, final String regexPattern) {
		final Pattern pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		final Locator matches = page.getByText(pattern);
		final int count = (int) Math.min(matches.count(), 20);
		for (int i = 0; i < count; i++) {
			if (isVisible(matches.nth(i), 1_200)) {
				return;
			}
		}
		throw new AssertionError("Expected visible text not found for pattern: " + regexPattern);
	}

	private boolean isTextVisible(final Page page, final String regexPattern, final double timeoutMs) {
		final Pattern pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		return isVisible(page.getByText(pattern).first(), timeoutMs);
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private void assertSidebarVisible(final Page page) {
		final Locator sidebar = page.getByRole(AriaRole.NAVIGATION).first();
		if (isVisible(sidebar, 5_000)) {
			return;
		}
		assertVisibleText(page, "Mi Negocio|Negocio");
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (PlaywrightException ignored) {
			// no-op
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7_000));
		} catch (PlaywrightException ignored) {
			// Some views keep background requests running indefinitely.
		}

		page.waitForTimeout(600);
	}

	private Page waitForNewTab(final BrowserContext context, final int pageCountBeforeClick) {
		final int maxAttempts = 24;
		for (int i = 0; i < maxAttempts; i++) {
			if (context.pages().size() > pageCountBeforeClick) {
				return context.pages().get(context.pages().size() - 1);
			}
			context.pages().get(0).waitForTimeout(250);
		}
		return null;
	}

	private void takeScreenshot(final Page page, final Path filePath, final boolean fullPage) {
		try {
			Files.createDirectories(filePath.getParent());
			page.screenshot(new Page.ScreenshotOptions().setPath(filePath).setFullPage(fullPage));
		} catch (IOException e) {
			throw new RuntimeException("Unable to create screenshot directory: " + filePath, e);
		}
	}

	private LinkedHashMap<String, String> initReport() {
		final LinkedHashMap<String, String> report = new LinkedHashMap<>();
		report.put("Login", "NOT RUN");
		report.put("Mi Negocio menu", "NOT RUN");
		report.put("Agregar Negocio modal", "NOT RUN");
		report.put("Administrar Negocios view", "NOT RUN");
		report.put("Informaci\u00F3n General", "NOT RUN");
		report.put("Detalles de la Cuenta", "NOT RUN");
		report.put("Tus Negocios", "NOT RUN");
		report.put("T\u00E9rminos y Condiciones", "NOT RUN");
		report.put("Pol\u00EDtica de Privacidad", "NOT RUN");
		return report;
	}

	private void executeStep(final Map<String, String> report, final String stepName, final StepExecutor executor) {
		try {
			executor.run();
			report.put(stepName, "PASS");
		} catch (Throwable t) {
			report.put(stepName, "FAIL: " + compactMessage(t));
		}
	}

	private String compactMessage(final Throwable t) {
		final String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
		return message.replaceAll("\\s+", " ").trim();
	}

	private boolean allStepsPassed(final Map<String, String> report) {
		return report.values().stream().allMatch(status -> status.startsWith("PASS"));
	}

	private String formatReport(final Map<String, String> report, final String termsUrl, final String privacyUrl) {
		final StringBuilder sb = new StringBuilder();
		sb.append("Final validation report").append('\n');
		for (Map.Entry<String, String> entry : report.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}
		sb.append("- Final URL (T\u00E9rminos y Condiciones): ").append(termsUrl).append('\n');
		sb.append("- Final URL (Pol\u00EDtica de Privacidad): ").append(privacyUrl).append('\n');
		return sb.toString();
	}

	private void writeFinalReport(final Path evidenceDir, final Map<String, String> report, final String termsUrl,
			final String privacyUrl) throws IOException {
		final String body = formatReport(report, termsUrl, privacyUrl);
		final Path reportPath = evidenceDir.resolve("10-final-report.txt");
		Files.writeString(reportPath, body, StandardCharsets.UTF_8);
	}

	private Path createEvidenceDir() {
		final String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
		return Paths.get("target", "e2e-artifacts", "saleads-mi-negocio", timestamp);
	}

	private boolean isHeadless() {
		final String value = valueOrEmpty(System.getenv("SALEADS_HEADLESS"));
		return !"false".equalsIgnoreCase(value);
	}

	private String valueOrEmpty(final String input) {
		return input == null ? "" : input.trim();
	}

	private String valueOrNull(final String input) {
		final String value = valueOrEmpty(input);
		return value.isBlank() ? null : value;
	}

	@FunctionalInterface
	private interface StepExecutor {
		void run() throws Exception;
	}
}
