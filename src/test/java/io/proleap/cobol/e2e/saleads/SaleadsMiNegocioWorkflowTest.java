package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Informaci\u00f3n General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private static final double DEFAULT_TIMEOUT_MS = 30000;
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private boolean closeBrowserAtEnd;
	private boolean closeContextAtEnd;
	private Path evidenceDir;
	private final Map<String, String> stepDetails = new LinkedHashMap<>();

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String loginUrl = readSetting("SALEADS_LOGIN_URL");
		final String cdpUrl = readSetting("SALEADS_CDP_URL");

		Assume.assumeTrue("Set SALEADS_LOGIN_URL or SALEADS_CDP_URL to run this E2E test.",
				hasText(loginUrl) || hasText(cdpUrl));

		final LinkedHashMap<String, StepOutcome> report = initReport();

		try {
			setupSession(loginUrl, cdpUrl);
			evidenceDir = createEvidenceDirectory();

			final boolean loginPassed = runStep(report, LOGIN, this::stepLoginWithGoogle);
			final boolean menuPassed = loginPassed ? runStep(report, MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu)
					: markBlocked(report, MI_NEGOCIO_MENU, LOGIN);
			final boolean agregarModalPassed = menuPassed
					? runStep(report, AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal)
					: markBlocked(report, AGREGAR_NEGOCIO_MODAL, MI_NEGOCIO_MENU);
			final boolean administrarPassed = menuPassed
					? runStep(report, ADMINISTRAR_NEGOCIOS_VIEW, this::stepOpenAdministrarNegocios)
					: markBlocked(report, ADMINISTRAR_NEGOCIOS_VIEW, MI_NEGOCIO_MENU);
			final boolean infoPassed = administrarPassed
					? runStep(report, INFORMACION_GENERAL, this::stepValidateInformacionGeneral)
					: markBlocked(report, INFORMACION_GENERAL, ADMINISTRAR_NEGOCIOS_VIEW);
			final boolean detallesPassed = administrarPassed
					? runStep(report, DETALLES_CUENTA, this::stepValidateDetallesCuenta)
					: markBlocked(report, DETALLES_CUENTA, ADMINISTRAR_NEGOCIOS_VIEW);
			final boolean negociosPassed = administrarPassed
					? runStep(report, TUS_NEGOCIOS, this::stepValidateTusNegocios)
					: markBlocked(report, TUS_NEGOCIOS, ADMINISTRAR_NEGOCIOS_VIEW);
			final boolean terminosPassed = administrarPassed
					? runStep(report, TERMINOS, this::stepValidateTerminosCondiciones)
					: markBlocked(report, TERMINOS, ADMINISTRAR_NEGOCIOS_VIEW);
			final boolean privacidadPassed = administrarPassed
					? runStep(report, PRIVACIDAD, this::stepValidatePoliticaPrivacidad)
					: markBlocked(report, PRIVACIDAD, ADMINISTRAR_NEGOCIOS_VIEW);

			final boolean allLogicalStepsReached = loginPassed && menuPassed && agregarModalPassed && administrarPassed
					&& infoPassed && detallesPassed && negociosPassed && terminosPassed && privacidadPassed;
			writeFinalReport(report, allLogicalStepsReached);
			assertAllPassed(report);
		} finally {
			closeSession();
		}
	}

	private void stepLoginWithGoogle() throws Exception {
		final Locator googleButton = appPage
				.locator("button:has-text(\"Google\"), [role='button']:has-text(\"Google\"), a:has-text(\"Google\")")
				.first();
		assertVisible(appPage, googleButton, "Google login button was not found.");
		clickAndWaitForUi(appPage, googleButton);

		selectGoogleAccountIfPresent();
		appPage = waitForApplicationPage();

		assertVisible(appPage, appPage.locator("aside, nav").first(), "Main sidebar navigation is not visible.");
		assertVisible(appPage, textLocator(appPage, "Negocio"), "Expected 'Negocio' option in sidebar.");

		takeScreenshot(appPage, "01-dashboard-loaded.png", false);
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		expandMiNegocioMenu();
		assertVisible(appPage, textLocator(appPage, "Agregar Negocio"),
				"'Agregar Negocio' option is not visible.");
		assertVisible(appPage, textLocator(appPage, "Administrar Negocios"),
				"'Administrar Negocios' option is not visible.");
		takeScreenshot(appPage, "02-mi-negocio-menu-expanded.png", false);
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		expandMiNegocioMenu();
		clickAndWaitForUi(appPage, textLocator(appPage, "Agregar Negocio"));

		assertVisible(appPage, textLocator(appPage, "Crear Nuevo Negocio"),
				"Modal title 'Crear Nuevo Negocio' is missing.");

		final Locator nombreNegocioInput = appPage.getByLabel("Nombre del Negocio").first();
		Locator inputToUse = nombreNegocioInput;
		if (!isVisible(appPage, nombreNegocioInput, 5000)) {
			inputToUse = appPage.locator("input[placeholder*='Negocio'], input[name*='negocio'], input").first();
		}
		assertVisible(appPage, inputToUse, "Input 'Nombre del Negocio' is not visible.");
		assertVisible(appPage, textLocator(appPage, "Tienes 2 de 3 negocios"),
				"Expected business count text is not visible.");
		assertVisible(appPage, textLocator(appPage, "Cancelar"), "Button 'Cancelar' is not present.");
		assertVisible(appPage, textLocator(appPage, "Crear Negocio"), "Button 'Crear Negocio' is not present.");

		takeScreenshot(appPage, "03-agregar-negocio-modal.png", false);

		inputToUse.fill(TEST_BUSINESS_NAME);
		clickAndWaitForUi(appPage, textLocator(appPage, "Cancelar"));
		assertTrue("Agregar negocio modal should close after clicking 'Cancelar'.",
				!isVisible(appPage, textLocator(appPage, "Crear Nuevo Negocio"), 4000));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		expandMiNegocioMenu();
		clickAndWaitForUi(appPage, textLocator(appPage, "Administrar Negocios"));

		assertVisible(appPage, textLocator(appPage, "Informaci\u00f3n General", "Informacion General"),
				"Section 'Informacion General' is missing.");
		assertVisible(appPage, textLocator(appPage, "Detalles de la Cuenta"),
				"Section 'Detalles de la Cuenta' is missing.");
		assertVisible(appPage, textLocator(appPage, "Tus Negocios"), "Section 'Tus Negocios' is missing.");
		assertVisible(appPage, textLocator(appPage, "Secci\u00f3n Legal", "Seccion Legal"),
				"Section 'Seccion Legal' is missing.");

		takeScreenshot(appPage, "04-administrar-negocios-full-page.png", true);
	}

	private void stepValidateInformacionGeneral() throws Exception {
		final Locator section = sectionByHeading("Informaci\u00f3n General", "Informacion General");
		final String text = normalizedText(section.innerText());
		final List<String> lines = nonEmptyLines(text);

		final boolean hasEmail = EMAIL_PATTERN.matcher(text).find();
		final boolean hasBusinessPlan = text.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN");
		final boolean hasCambiarPlan = text.contains("Cambiar Plan");
		final boolean hasUserName = lines.stream().anyMatch(this::looksLikeUserName);

		assertTrue("Expected a visible user name inside 'Informacion General'.", hasUserName);
		assertTrue("Expected a visible user email inside 'Informacion General'.", hasEmail);
		assertTrue("Expected text 'BUSINESS PLAN' inside 'Informacion General'.", hasBusinessPlan);
		assertTrue("Expected button text 'Cambiar Plan' inside 'Informacion General'.", hasCambiarPlan);
	}

	private void stepValidateDetallesCuenta() throws Exception {
		final Locator section = sectionByHeading("Detalles de la Cuenta");
		final String text = normalizedText(section.innerText());

		assertContains(text, "Cuenta creada", "Expected 'Cuenta creada' in 'Detalles de la Cuenta'.");
		assertContains(text, "Estado activo", "Expected 'Estado activo' in 'Detalles de la Cuenta'.");
		assertContains(text, "Idioma seleccionado", "Expected 'Idioma seleccionado' in 'Detalles de la Cuenta'.");
	}

	private void stepValidateTusNegocios() throws Exception {
		final Locator section = sectionByHeading("Tus Negocios");
		final String text = normalizedText(section.innerText());

		final boolean hasBusinessList = section.locator("li, [role='row'], [class*='business'], [class*='card']")
				.count() > 0 || nonEmptyLines(text).size() >= 4;

		assertTrue("Expected visible business list in 'Tus Negocios'.", hasBusinessList);
		assertTrue("Expected 'Agregar Negocio' button in 'Tus Negocios'.",
				text.contains("Agregar Negocio") || isVisible(appPage, textLocator(appPage, "Agregar Negocio"), 4000));
		assertContains(text, "Tienes 2 de 3 negocios", "Expected business quota text in 'Tus Negocios'.");
	}

	private void stepValidateTerminosCondiciones() throws Exception {
		final String finalUrl = validateLegalDocument(new String[] { "T\u00e9rminos y Condiciones", "Terminos y Condiciones" },
				new String[] { "T\u00e9rminos y Condiciones", "Terminos y Condiciones" },
				"05-terminos-condiciones.png");
		appendStepDetails(TERMINOS, "finalUrl=" + finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		final String finalUrl = validateLegalDocument(new String[] { "Pol\u00edtica de Privacidad", "Politica de Privacidad" },
				new String[] { "Pol\u00edtica de Privacidad", "Politica de Privacidad" },
				"06-politica-privacidad.png");
		appendStepDetails(PRIVACIDAD, "finalUrl=" + finalUrl);
	}

	private String validateLegalDocument(final String[] linkTextOptions, final String[] headingOptions,
			final String screenshotName) throws IOException {
		assertVisible(appPage, textLocator(appPage, "Secci\u00f3n Legal", "Seccion Legal"), "Legal section is not visible.");

		final String appUrlBeforeClick = appPage.url();
		final int pageCountBeforeClick = context.pages().size();
		clickAndWaitForUi(appPage, textLocator(appPage, linkTextOptions));

		Page legalPage = waitForAdditionalPage(pageCountBeforeClick, 7000);
		if (legalPage == null) {
			legalPage = appPage;
		}

		legalPage.bringToFront();
		waitForUiToSettle(legalPage);

		assertVisible(legalPage, textLocator(legalPage, headingOptions),
				"Expected legal heading is not visible.");
		final String legalBody = normalizedText(legalPage.locator("body").innerText());
		assertTrue("Legal content text should be visible and non-trivial.", legalBody.length() > 120);

		takeScreenshot(legalPage, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
		} else if (!safeEquals(appUrlBeforeClick, appPage.url())) {
			appPage.goBack();
		}
		waitForUiToSettle(appPage);

		return finalUrl;
	}

	private void expandMiNegocioMenu() {
		if (isVisible(appPage, textLocator(appPage, "Agregar Negocio"), 2000)
				&& isVisible(appPage, textLocator(appPage, "Administrar Negocios"), 2000)) {
			return;
		}

		final Locator miNegocio = textLocator(appPage, "Mi Negocio");
		if (isVisible(appPage, miNegocio, 5000)) {
			clickAndWaitForUi(appPage, miNegocio);
		}

		if (!isVisible(appPage, textLocator(appPage, "Agregar Negocio"), 2000)) {
			final Locator negocioSection = textLocator(appPage, "Negocio");
			if (isVisible(appPage, negocioSection, 5000)) {
				clickAndWaitForUi(appPage, negocioSection);
			}
		}

		if (!isVisible(appPage, textLocator(appPage, "Agregar Negocio"), 2000) && isVisible(appPage, miNegocio, 3000)) {
			clickAndWaitForUi(appPage, miNegocio);
		}
	}

	private void selectGoogleAccountIfPresent() {
		final long timeoutAt = System.currentTimeMillis() + 20000;
		while (System.currentTimeMillis() < timeoutAt) {
			for (final Page page : context.pages()) {
				if (page.url().contains("accounts.google.com")) {
					final Locator account = page.getByText(GOOGLE_ACCOUNT_EMAIL).first();
					if (isVisible(page, account, 3000)) {
						clickAndWaitForUi(page, account);
					}
					return;
				}
			}
			appPage.waitForTimeout(500);
		}
	}

	private Page waitForApplicationPage() {
		final long timeoutAt = System.currentTimeMillis() + 60000;
		while (System.currentTimeMillis() < timeoutAt) {
			for (final Page page : context.pages()) {
				if (!page.url().contains("accounts.google.com") && !page.url().startsWith("chrome-error://")) {
					if (isVisible(page, textLocator(page, "Negocio"), 2000)
							|| isVisible(page, page.locator("aside, nav").first(), 2000)) {
						page.bringToFront();
						waitForUiToSettle(page);
						return page;
					}
				}
			}
			appPage.waitForTimeout(1000);
		}

		return appPage;
	}

	private void setupSession(final String loginUrl, final String cdpUrl) {
		playwright = Playwright.create();

		if (hasText(cdpUrl)) {
			browser = playwright.chromium().connectOverCDP(cdpUrl);
			closeBrowserAtEnd = false;
			if (browser.contexts().isEmpty()) {
				context = browser.newContext();
				closeContextAtEnd = true;
			} else {
				context = browser.contexts().get(0);
				closeContextAtEnd = false;
			}

			if (context.pages().isEmpty()) {
				appPage = context.newPage();
				closeContextAtEnd = true;
			} else {
				appPage = context.pages().get(0);
			}
		} else {
			final String headlessSetting = readSetting("SALEADS_HEADLESS");
			final boolean headless = !hasText(headlessSetting) || Boolean.parseBoolean(headlessSetting);
			browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			closeBrowserAtEnd = true;
			context = browser.newContext();
			closeContextAtEnd = true;
			appPage = context.newPage();
		}

		context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
		appPage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

		if (hasText(loginUrl) && ("about:blank".equals(appPage.url()) || appPage.url().isBlank())) {
			appPage.navigate(loginUrl);
			waitForUiToSettle(appPage);
		}
	}

	private void closeSession() {
		if (context != null && closeContextAtEnd) {
			try {
				context.close();
			} catch (final Exception ignored) {
			}
		}
		if (browser != null && closeBrowserAtEnd) {
			try {
				browser.close();
			} catch (final Exception ignored) {
			}
		}
		if (playwright != null) {
			try {
				playwright.close();
			} catch (final Exception ignored) {
			}
		}
	}

	private void waitForUiToSettle(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (final RuntimeException ignored) {
		}
		page.waitForTimeout(400);
	}

	private void clickAndWaitForUi(final Page page, final Locator locator) {
		assertVisible(page, locator, "Target element is not visible before click.");
		locator.click();
		waitForUiToSettle(page);
	}

	private void assertVisible(final Page page, final Locator locator, final String errorMessage) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (final RuntimeException ex) {
			throw new AssertionError(errorMessage, ex);
		}
	}

	private boolean isVisible(final Page page, final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final RuntimeException ex) {
			return false;
		}
	}

	private Locator exactText(final Page page, final String text) {
		return page.getByText(text, new Page.GetByTextOptions().setExact(true));
	}

	private Locator textLocator(final Page page, final String... texts) {
		for (final String text : texts) {
			final Locator candidate = exactText(page, text).first();
			if (isVisible(page, candidate, 1500)) {
				return candidate;
			}
		}
		return exactText(page, texts[0]).first();
	}

	private Locator sectionByHeading(final String... headings) {
		final Locator headingLocator = textLocator(appPage, headings);
		assertVisible(appPage, headingLocator, "Requested section heading was not found.");
		return headingLocator.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
	}

	private Page waitForAdditionalPage(final int initialPageCount, final long timeoutMs) {
		final long timeoutAt = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < timeoutAt) {
			final List<Page> pages = context.pages();
			if (pages.size() > initialPageCount) {
				return pages.get(pages.size() - 1);
			}
			appPage.waitForTimeout(250);
		}
		return null;
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path path = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(path);
		return path;
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private LinkedHashMap<String, StepOutcome> initReport() {
		final LinkedHashMap<String, StepOutcome> report = new LinkedHashMap<>();
		report.put(LOGIN, StepOutcome.pending());
		report.put(MI_NEGOCIO_MENU, StepOutcome.pending());
		report.put(AGREGAR_NEGOCIO_MODAL, StepOutcome.pending());
		report.put(ADMINISTRAR_NEGOCIOS_VIEW, StepOutcome.pending());
		report.put(INFORMACION_GENERAL, StepOutcome.pending());
		report.put(DETALLES_CUENTA, StepOutcome.pending());
		report.put(TUS_NEGOCIOS, StepOutcome.pending());
		report.put(TERMINOS, StepOutcome.pending());
		report.put(PRIVACIDAD, StepOutcome.pending());
		return report;
	}

	private boolean runStep(final Map<String, StepOutcome> report, final String stepName, final StepAction action) {
		try {
			action.run();
			final String detail = stepDetails.getOrDefault(stepName, "PASS");
			report.put(stepName, StepOutcome.pass(detail));
			return true;
		} catch (final Throwable throwable) {
			report.put(stepName, StepOutcome.fail("FAIL: " + conciseError(throwable)));
			return false;
		}
	}

	private boolean markBlocked(final Map<String, StepOutcome> report, final String stepName, final String blockedByStep) {
		report.put(stepName, StepOutcome.fail("FAIL: blocked by '" + blockedByStep + "'."));
		return false;
	}

	private void appendStepDetails(final String stepName, final String suffix) {
		stepDetails.put(stepName, "PASS (" + suffix + ")");
	}

	private void writeFinalReport(final LinkedHashMap<String, StepOutcome> report, final boolean allLogicalStepsReached)
			throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("overall=" + (allLogicalStepsReached ? "PASS" : "FAIL"));
		lines.add("");
		for (final Map.Entry<String, StepOutcome> entry : report.entrySet()) {
			lines.add(entry.getKey() + ": " + entry.getValue().status + " - " + entry.getValue().details);
		}
		lines.add("");
		lines.add("evidenceDir=" + evidenceDir.toAbsolutePath());
		Files.write(evidenceDir.resolve("final-report.txt"), lines, StandardCharsets.UTF_8);
	}

	private void assertAllPassed(final LinkedHashMap<String, StepOutcome> report) {
		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, StepOutcome> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				failed.add(entry.getKey() + " -> " + entry.getValue().details);
			}
		}
		assertTrue("One or more validations failed:\n" + String.join("\n", failed), failed.isEmpty());
	}

	private void assertContains(final String text, final String expected, final String errorMessage) {
		assertTrue(errorMessage, text.contains(expected));
	}

	private boolean looksLikeUserName(final String line) {
		final String normalized = normalizedText(line);
		if (normalized.isBlank() || normalized.contains("@")) {
			return false;
		}

		final String upper = normalized.toUpperCase(Locale.ROOT);
		if (upper.contains("INFORMACION") || upper.contains("INFORMACIÓN") || upper.contains("BUSINESS PLAN")
				|| upper.contains("CAMBIAR PLAN")
				|| upper.contains("DETALLES") || upper.contains("CUENTA")) {
			return false;
		}

		return normalized.matches("[\\p{L}][\\p{L} .'-]{2,}");
	}

	private List<String> nonEmptyLines(final String text) {
		final String[] chunks = text.split("\\r?\\n");
		final List<String> result = new ArrayList<>();
		for (final String chunk : chunks) {
			final String normalized = normalizedText(chunk);
			if (!normalized.isBlank()) {
				result.add(normalized);
			}
		}
		return result;
	}

	private String normalizedText(final String value) {
		return value == null ? "" : value.replace('\u00a0', ' ').trim();
	}

	private String conciseError(final Throwable throwable) {
		final String message = throwable.getMessage();
		if (message != null && !message.isBlank()) {
			return normalizedText(message);
		}
		return throwable.getClass().getSimpleName();
	}

	private boolean hasText(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	private boolean safeEquals(final String left, final String right) {
		if (left == null) {
			return right == null;
		}
		return left.equals(right);
	}

	private String readSetting(final String key) {
		final String systemProperty = System.getProperty(key);
		if (hasText(systemProperty)) {
			return systemProperty.trim();
		}
		final String envValue = System.getenv(key);
		return hasText(envValue) ? envValue.trim() : null;
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private static class StepOutcome {
		private final boolean passed;
		private final String status;
		private final String details;

		private StepOutcome(final boolean passed, final String status, final String details) {
			this.passed = passed;
			this.status = status;
			this.details = details;
		}

		private static StepOutcome pending() {
			return new StepOutcome(false, "PENDING", "Not executed.");
		}

		private static StepOutcome pass(final String details) {
			return new StepOutcome(true, "PASS", details);
		}

		private static StepOutcome fail(final String details) {
			return new StepOutcome(false, "FAIL", details);
		}
	}
}
