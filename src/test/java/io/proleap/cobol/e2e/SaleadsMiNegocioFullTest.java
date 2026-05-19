package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

public class SaleadsMiNegocioFullTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informaci\u00F3n General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "T\u00E9rminos y Condiciones";
	private static final String STEP_POLITICA = "Pol\u00EDtica de Privacidad";

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String NUEVO_NEGOCIO_NOMBRE = "Negocio Prueba Automatizacion";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private Path evidenceDir;
	private String administrarNegociosUrl;
	private long timeoutMs;

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		Assume.assumeTrue(
				"Set RUN_SALEADS_E2E=true to execute SaleADS UI flow validation.",
				Boolean.parseBoolean(System.getenv().getOrDefault("RUN_SALEADS_E2E", "false")));

		timeoutMs = Long.parseLong(System.getenv().getOrDefault("SALEADS_TIMEOUT_MS", "15000"));
		initializeStepResults();
		initializeEvidenceDir();

		try {
			initializeBrowser();
			executeWorkflow();
		} finally {
			writeFinalReport();
			closeBrowserSafely();
		}

		assertTrue(buildFailureSummary(), allStepsPassed());
	}

	private void executeWorkflow() {
		runStep(STEP_LOGIN, this::stepLoginWithGoogle);
		runStep(STEP_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(STEP_AGREGAR_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(STEP_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		runStep(STEP_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(STEP_DETALLES_CUENTA, this::stepValidateDetallesCuenta);
		runStep(STEP_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(STEP_TERMINOS, () -> stepValidateLegalLink("T\u00E9rminos y Condiciones", "T\u00E9rminos y Condiciones",
				"08-terminos-y-condiciones"));
		runStep(STEP_POLITICA, () -> stepValidateLegalLink("Pol\u00EDtica de Privacidad", "Pol\u00EDtica de Privacidad",
				"09-politica-de-privacidad"));
	}

	private void stepLoginWithGoogle() {
		final List<String> loginTexts = Arrays.asList("Sign in with Google", "Iniciar sesi\u00F3n con Google",
				"Continuar con Google", "Google");

		final String loginButtonText = findFirstVisibleText(appPage, loginTexts);
		if (loginButtonText == null) {
			throw new AssertionError("No visible login button containing Google was found.");
		}

		Page popup = null;
		try {
			popup = appPage.waitForPopup(() -> clickVisibleTextNoWait(appPage, loginButtonText));
			waitForUiLoad(popup);
			selectGoogleAccountIfVisible(popup);
			waitForUiLoad(appPage);
		} catch (PlaywrightException noPopup) {
			clickVisibleText(appPage, loginButtonText);
			selectGoogleAccountIfVisible(appPage);
			waitForUiLoad(appPage);
		}

		assertSidebarVisible(appPage);
		assertVisibleText(appPage, "Negocio");
		takeScreenshot(appPage, "01-dashboard-loaded", false);
	}

	private void stepOpenMiNegocioMenu() {
		clickVisibleText(appPage, "Negocio");
		clickVisibleText(appPage, "Mi Negocio");

		assertVisibleText(appPage, "Agregar Negocio");
		assertVisibleText(appPage, "Administrar Negocios");
		takeScreenshot(appPage, "02-mi-negocio-menu-expanded", false);
	}

	private void stepValidateAgregarNegocioModal() {
		clickVisibleText(appPage, "Agregar Negocio");

		assertVisibleText(appPage, "Crear Nuevo Negocio");
		final Locator nombreInput = findInputByLabelOrPlaceholder(appPage, "Nombre del Negocio");
		assertTrue("Input field 'Nombre del Negocio' is not visible.",
				isLocatorVisible(nombreInput, timeoutMs));
		assertVisibleText(appPage, "Tienes 2 de 3 negocios");
		assertVisibleText(appPage, "Cancelar");
		assertVisibleText(appPage, "Crear Negocio");
		takeScreenshot(appPage, "03-agregar-negocio-modal", false);

		nombreInput.fill(NUEVO_NEGOCIO_NOMBRE);
		clickVisibleText(appPage, "Cancelar");
		assertTextNotVisible(appPage, "Crear Nuevo Negocio");
	}

	private void stepOpenAdministrarNegocios() {
		expandMiNegocioIfNeeded();
		clickVisibleText(appPage, "Administrar Negocios");

		assertVisibleText(appPage, "Informaci\u00F3n General");
		assertVisibleText(appPage, "Detalles de la Cuenta");
		assertVisibleText(appPage, "Tus Negocios");
		assertVisibleText(appPage, "Secci\u00F3n Legal");
		administrarNegociosUrl = appPage.url();
		takeScreenshot(appPage, "04-administrar-negocios-page", true);
	}

	private void stepValidateInformacionGeneral() {
		final String sectionText = sectionTextOrPageText("Informaci\u00F3n General");
		assertTrue("User email is not visible in Informacion General.",
				EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("User name is not visible in Informacion General.", containsLikelyUserName(sectionText));
		assertVisibleText(appPage, "BUSINESS PLAN");
		assertVisibleText(appPage, "Cambiar Plan");
	}

	private void stepValidateDetallesCuenta() {
		final String sectionText = sectionTextOrPageText("Detalles de la Cuenta");
		assertContainsNormalized(sectionText, "Cuenta creada");
		assertContainsNormalized(sectionText, "Estado activo");
		assertContainsNormalized(sectionText, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final Locator section = findSectionByHeading("Tus Negocios");
		assertTrue("Section 'Tus Negocios' is not visible.", isLocatorVisible(section, timeoutMs));

		final String sectionText = normalizeWhitespace(section.innerText());
		assertTrue("Business list is not visible in 'Tus Negocios'.", sectionText.length() > 40);
		assertContainsNormalized(sectionText, "Tienes 2 de 3 negocios");

		Locator addBusinessButton = section.getByText("Agregar Negocio", new Locator.GetByTextOptions().setExact(true));
		if (addBusinessButton.count() == 0) {
			addBusinessButton = appPage.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(true));
		}
		assertTrue("'Agregar Negocio' button is not visible in 'Tus Negocios'.",
				isLocatorVisible(addBusinessButton.first(), timeoutMs));
	}

	private void stepValidateLegalLink(final String linkText, final String headingText, final String screenshotName) {
		expandMiNegocioIfNeeded();

		final String applicationUrlBeforeClick = appPage.url();
		Page legalPage = null;
		boolean openedInNewTab = false;
		try {
			legalPage = context.waitForPage(() -> clickVisibleTextNoWait(appPage, linkText));
			openedInNewTab = true;
		} catch (PlaywrightException noNewTab) {
			clickVisibleText(appPage, linkText);
			legalPage = appPage;
		}

		waitForUiLoad(legalPage);
		assertVisibleText(legalPage, headingText);

		final String legalBody = normalizeWhitespace(legalPage.locator("body").innerText());
		assertTrue("Legal content text is not sufficiently visible for " + headingText + ".", legalBody.length() > 120);

		takeScreenshot(legalPage, screenshotName, true);
		legalUrls.put(headingText, legalPage.url());

		if (openedInNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else if (!applicationUrlBeforeClick.equals(appPage.url())) {
			if (administrarNegociosUrl != null && !administrarNegociosUrl.isEmpty()) {
				appPage.navigate(administrarNegociosUrl);
				waitForUiLoad(appPage);
			} else {
				appPage.navigate(applicationUrlBeforeClick);
				waitForUiLoad(appPage);
			}
		}
	}

	private void initializeBrowser() {
		playwright = Playwright.create();
		final String cdpUrl = System.getenv("SALEADS_CHROME_CDP_URL");
		if (cdpUrl != null && !cdpUrl.trim().isEmpty()) {
			browser = playwright.chromium().connectOverCDP(cdpUrl);
			if (!browser.contexts().isEmpty()) {
				context = browser.contexts().get(0);
			}
		}

		if (browser == null) {
			final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "false"));
			browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		}

		if (context == null) {
			context = browser.newContext();
		}
		context.setDefaultTimeout(timeoutMs);

		appPage = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
		appPage.setDefaultTimeout(timeoutMs);

		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.trim().isEmpty()) {
			appPage.navigate(loginUrl.trim());
			waitForUiLoad(appPage);
		}

		if (appPage.url() == null || appPage.url().isEmpty() || "about:blank".equals(appPage.url())) {
			throw new IllegalStateException(
					"Browser is not on SaleADS login page. Set SALEADS_LOGIN_URL or connect to an existing browser page through SALEADS_CHROME_CDP_URL.");
		}
	}

	private void initializeEvidenceDir() throws IOException {
		final String configuredBaseDir = System.getenv().getOrDefault("SALEADS_EVIDENCE_DIR", "target/saleads-evidence");
		final String runId = RUN_ID_FORMAT.format(LocalDateTime.now());
		evidenceDir = Paths.get(configuredBaseDir, "saleads-mi-negocio-" + runId);
		Files.createDirectories(evidenceDir);
	}

	private void runStep(final String stepName, final Runnable action) {
		try {
			action.run();
			stepResults.put(stepName, StepResult.pass());
		} catch (Throwable error) {
			stepResults.put(stepName, StepResult.fail(error.getMessage()));
		}
	}

	private void initializeStepResults() {
		stepResults.put(STEP_LOGIN, StepResult.fail("Not executed."));
		stepResults.put(STEP_MI_NEGOCIO_MENU, StepResult.fail("Not executed."));
		stepResults.put(STEP_AGREGAR_MODAL, StepResult.fail("Not executed."));
		stepResults.put(STEP_ADMIN_VIEW, StepResult.fail("Not executed."));
		stepResults.put(STEP_INFO_GENERAL, StepResult.fail("Not executed."));
		stepResults.put(STEP_DETALLES_CUENTA, StepResult.fail("Not executed."));
		stepResults.put(STEP_TUS_NEGOCIOS, StepResult.fail("Not executed."));
		stepResults.put(STEP_TERMINOS, StepResult.fail("Not executed."));
		stepResults.put(STEP_POLITICA, StepResult.fail("Not executed."));
	}

	private void takeScreenshot(final Page page, final String name, final boolean fullPage) {
		final Path output = evidenceDir.resolve(name + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(output).setFullPage(fullPage));
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		final String report = buildReport();
		Files.writeString(evidenceDir.resolve("final-report.txt"), report, StandardCharsets.UTF_8);
		System.out.println(report);
	}

	private String buildReport() {
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test\n");
		report.append("Evidence directory: ").append(evidenceDir).append('\n');
		report.append('\n');
		report.append("Validation results:\n");
		for (Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().statusLabel);
			if (!entry.getValue().detail.isEmpty()) {
				report.append(" (").append(entry.getValue().detail).append(')');
			}
			report.append('\n');
		}

		if (!legalUrls.isEmpty()) {
			report.append('\n');
			report.append("Legal final URLs:\n");
			for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
				report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}
		return report.toString();
	}

	private boolean allStepsPassed() {
		for (StepResult result : stepResults.values()) {
			if (!result.passed) {
				return false;
			}
		}
		return true;
	}

	private String buildFailureSummary() {
		final StringBuilder summary = new StringBuilder();
		summary.append("One or more validations failed. Review report at ");
		summary.append(evidenceDir.resolve("final-report.txt"));
		summary.append('\n');
		for (Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			if (!entry.getValue().passed) {
				summary.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().detail).append('\n');
			}
		}
		return summary.toString();
	}

	private void closeBrowserSafely() {
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

	private void selectGoogleAccountIfVisible(final Page page) {
		final Locator accountLocator = page.getByText(GOOGLE_ACCOUNT_EMAIL, new Page.GetByTextOptions().setExact(true));
		if (isLocatorVisible(accountLocator.first(), 5000)) {
			accountLocator.first().click();
			waitForUiLoad(page);
		}
	}

	private String findFirstVisibleText(final Page page, final List<String> texts) {
		for (String text : texts) {
			Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true));
			if (exact.count() > 0 && isLocatorVisible(exact.first(), 1500)) {
				return text;
			}

			Locator partial = page.getByText(text);
			if (partial.count() > 0 && isLocatorVisible(partial.first(), 1500)) {
				return text;
			}
		}
		return null;
	}

	private void expandMiNegocioIfNeeded() {
		final Locator adminOption = appPage.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true));
		if (adminOption.count() == 0 || !isLocatorVisible(adminOption.first(), 1000)) {
			if (isTextVisible(appPage, "Negocio")) {
				clickVisibleText(appPage, "Negocio");
			}
			clickVisibleText(appPage, "Mi Negocio");
		}
	}

	private Locator findInputByLabelOrPlaceholder(final Page page, final String fieldName) {
		Locator byLabel = page.getByLabel(fieldName);
		if (byLabel.count() > 0) {
			return byLabel.first();
		}

		Locator byPlaceholder = page.getByPlaceholder(fieldName);
		if (byPlaceholder.count() > 0) {
			return byPlaceholder.first();
		}

		throw new AssertionError("Input field '" + fieldName + "' was not found.");
	}

	private Locator findSectionByHeading(final String headingText) {
		Locator section = appPage.locator("section,div,article").filter(new Locator.FilterOptions().setHasText(headingText));
		if (section.count() == 0) {
			throw new AssertionError("Unable to find section containing heading: " + headingText);
		}
		return section.first();
	}

	private String sectionTextOrPageText(final String headingText) {
		try {
			Locator section = findSectionByHeading(headingText);
			return normalizeWhitespace(section.innerText());
		} catch (Throwable ignored) {
			return normalizeWhitespace(appPage.locator("body").innerText());
		}
	}

	private boolean containsLikelyUserName(final String content) {
		final String[] lines = content.split("\\R");
		for (String line : lines) {
			final String normalized = normalizeWhitespace(line);
			if (normalized.isEmpty()) {
				continue;
			}
			if (normalized.contains("@") || normalized.toUpperCase().contains("BUSINESS PLAN")
					|| normalized.toUpperCase().contains("CAMBIAR PLAN")) {
				continue;
			}
			final String[] words = normalized.split(" ");
			if (words.length >= 2 && words[0].matches("[A-Za-z].*") && words[1].matches("[A-Za-z].*")) {
				return true;
			}
		}
		return false;
	}

	private void clickVisibleText(final Page page, final String text) {
		Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(true));
		if (locator.count() == 0) {
			locator = page.getByText(text);
		}
		if (locator.count() == 0) {
			throw new AssertionError("No element found with visible text: " + text);
		}
		Locator first = locator.first();
		first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
		first.click();
		waitForUiLoad(page);
	}

	private void clickVisibleTextNoWait(final Page page, final String text) {
		Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(true));
		if (locator.count() == 0) {
			locator = page.getByText(text);
		}
		if (locator.count() == 0) {
			throw new AssertionError("No element found with visible text: " + text);
		}
		Locator first = locator.first();
		first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
		first.click();
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (PlaywrightException ignored) {
			// Some pages continuously poll the backend, so NETWORKIDLE might not occur.
		}
		page.waitForTimeout(300);
	}

	private void assertVisibleText(final Page page, final String text) {
		Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(true));
		if (locator.count() == 0) {
			locator = page.getByText(text);
		}
		if (locator.count() == 0 || !isLocatorVisible(locator.first(), timeoutMs)) {
			throw new AssertionError("Expected visible text not found: " + text);
		}
	}

	private void assertTextNotVisible(final Page page, final String text) {
		Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(true));
		if (locator.count() == 0) {
			return;
		}
		if (isLocatorVisible(locator.first(), 1000)) {
			throw new AssertionError("Text is still visible when it should not be: " + text);
		}
	}

	private void assertContainsNormalized(final String source, final String expectedFragment) {
		final String normalizedSource = normalizeWhitespace(source);
		final String normalizedFragment = normalizeWhitespace(expectedFragment);
		if (!normalizedSource.contains(normalizedFragment)) {
			throw new AssertionError("Expected text fragment not found: " + expectedFragment);
		}
	}

	private void assertSidebarVisible(final Page page) {
		Locator sidebar = page.locator("aside").first();
		if (sidebar.count() > 0 && isLocatorVisible(sidebar, 3000)) {
			return;
		}

		Locator nav = page.locator("nav").first();
		if (nav.count() > 0 && isLocatorVisible(nav, 3000)) {
			return;
		}

		throw new AssertionError("Left sidebar navigation is not visible.");
	}

	private boolean isTextVisible(final Page page, final String text) {
		Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(true));
		if (locator.count() == 0) {
			locator = page.getByText(text);
		}
		return locator.count() > 0 && isLocatorVisible(locator.first(), 1000);
	}

	private boolean isLocatorVisible(final Locator locator, final long timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs).setState(WaitForSelectorState.VISIBLE));
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private String normalizeWhitespace(final String input) {
		return input == null ? "" : input.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	private static final class StepResult {
		private final boolean passed;
		private final String statusLabel;
		private final String detail;

		private StepResult(final boolean passed, final String statusLabel, final String detail) {
			this.passed = passed;
			this.statusLabel = statusLabel;
			this.detail = detail == null ? "" : detail;
		}

		private static StepResult pass() {
			return new StepResult(true, "PASS", "");
		}

		private static StepResult fail(final String detail) {
			return new StepResult(false, "FAIL", detail == null ? "Validation failed." : detail);
		}
	}
}
