package io.proleap.cobol.e2e;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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

public class SaleAdsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final long DEFAULT_WAIT_MS = 15000;
	private static final long SHORT_WAIT_MS = 4000;

	private static final String FIELD_LOGIN = "Login";
	private static final String FIELD_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String FIELD_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String FIELD_ADMINISTRAR_VIEW = "Administrar Negocios view";
	private static final String FIELD_INFO_GENERAL = "Información General";
	private static final String FIELD_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String FIELD_TUS_NEGOCIOS = "Tus Negocios";
	private static final String FIELD_TERMINOS = "Términos y Condiciones";
	private static final String FIELD_POLITICA = "Política de Privacidad";

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalFinalUrls = new LinkedHashMap<>();
	private Path runArtifactsDir;

	@Test
	public void testSaleAdsMiNegocioWorkflow() throws Exception {
		initSteps();
		runArtifactsDir = createRunArtifactsDirectory();

		final String loginUrl = getConfig("SALEADS_LOGIN_URL");
		if (isBlank(loginUrl)) {
			final String message = "Missing SALEADS_LOGIN_URL configuration. "
					+ "Set the login URL of the target SaleADS.ai environment.";
			markRemainingAsFailed(message);
			writeFinalReport();
			fail(message);
			return;
		}

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(
					new BrowserType.LaunchOptions().setHeadless(isHeadless()));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions()
					.setViewportSize(1600, 1200));
			final Page page = context.newPage();

			page.navigate(loginUrl);
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);

			runStep(FIELD_LOGIN, () -> executeLoginStep(page));
			runStep(FIELD_MI_NEGOCIO_MENU, () -> executeOpenMiNegocioMenuStep(page));
			runStep(FIELD_AGREGAR_MODAL, () -> executeAgregarNegocioModalStep(page));
			runStep(FIELD_ADMINISTRAR_VIEW, () -> executeAdministrarNegociosViewStep(page));
			runStep(FIELD_INFO_GENERAL, () -> executeInformacionGeneralStep(page));
			runStep(FIELD_DETALLES_CUENTA, () -> executeDetallesCuentaStep(page));
			runStep(FIELD_TUS_NEGOCIOS, () -> executeTusNegociosStep(page));
			runStep(FIELD_TERMINOS, () -> executeLegalDocumentStep(context, page, "Términos y Condiciones",
					"terminos-y-condiciones.png"));
			runStep(FIELD_POLITICA, () -> executeLegalDocumentStep(context, page, "Política de Privacidad",
					"politica-de-privacidad.png"));
		} finally {
			writeFinalReport();
		}

		final List<String> failedSteps = getFailedSteps();
		if (!failedSteps.isEmpty()) {
			fail("Workflow validation failed on step(s): " + failedSteps + ". Report: "
					+ runArtifactsDir.resolve("final-report.txt"));
		}
	}

	private void executeLoginStep(final Page page) throws IOException {
		final Locator googleButton = firstVisibleByText(page, DEFAULT_WAIT_MS,
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Continuar con Google",
				"Acceder con Google",
				"Google");
		clickAndWaitForUi(page, googleButton);

		final Locator accountSelector = page.getByText(GOOGLE_ACCOUNT_EMAIL,
				new Page.GetByTextOptions().setExact(true)).first();
		if (isVisible(accountSelector, SHORT_WAIT_MS)) {
			clickAndWaitForUi(page, accountSelector);
		}

		firstVisibleByText(page, DEFAULT_WAIT_MS, "Negocio", "Mi Negocio");
		requireVisible(page.locator("aside").first(), DEFAULT_WAIT_MS,
				"Left sidebar navigation is not visible after login.");
		captureScreenshot(FIELD_LOGIN, page, "dashboard-loaded.png", true);
	}

	private void executeOpenMiNegocioMenuStep(final Page page) throws IOException {
		final Locator sidebar = page.locator("aside").first();
		requireVisible(sidebar, DEFAULT_WAIT_MS, "Sidebar is not visible.");

		clickTextIfVisible(page, "Negocio", SHORT_WAIT_MS);
		clickAndWaitForUi(page, firstVisibleByText(page, DEFAULT_WAIT_MS, "Mi Negocio"));

		firstVisibleByText(page, DEFAULT_WAIT_MS, "Agregar Negocio");
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Administrar Negocios");
		captureScreenshot(FIELD_MI_NEGOCIO_MENU, page, "mi-negocio-menu-expanded.png", false);
	}

	private void executeAgregarNegocioModalStep(final Page page) throws IOException {
		ensureMiNegocioExpanded(page);
		clickAndWaitForUi(page, firstVisibleByText(page, DEFAULT_WAIT_MS, "Agregar Negocio"));

		firstVisibleByText(page, DEFAULT_WAIT_MS, "Crear Nuevo Negocio");
		final Locator nombreInput = firstVisibleByLocator(page, DEFAULT_WAIT_MS,
				page.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)).first(),
				page.locator("input[placeholder*='Nombre']").first(),
				page.locator("input[name*='nombre']").first(),
				page.locator("input").first());
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Tienes 2 de 3 negocios");
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Cancelar");
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Crear Negocio");

		captureScreenshot(FIELD_AGREGAR_MODAL, page, "agregar-negocio-modal.png", false);

		nombreInput.click();
		nombreInput.fill("Negocio Prueba Automatización");
		clickAndWaitForUi(page, firstVisibleByText(page, DEFAULT_WAIT_MS, "Cancelar"));
		waitForHidden(page.getByText("Crear Nuevo Negocio", new Page.GetByTextOptions().setExact(true)).first(),
				DEFAULT_WAIT_MS);
	}

	private void executeAdministrarNegociosViewStep(final Page page) throws IOException {
		ensureMiNegocioExpanded(page);
		clickAndWaitForUi(page, firstVisibleByText(page, DEFAULT_WAIT_MS, "Administrar Negocios"));

		firstVisibleByText(page, DEFAULT_WAIT_MS, "Información General");
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Detalles de la Cuenta");
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Tus Negocios");
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Sección Legal");
		captureScreenshot(FIELD_ADMINISTRAR_VIEW, page, "administrar-negocios-view.png", true);
	}

	private void executeInformacionGeneralStep(final Page page) {
		final Locator infoHeading = firstVisibleByText(page, DEFAULT_WAIT_MS, "Información General");
		final Locator sectionContainer = closestContainer(infoHeading);
		final String sectionText = sectionContainer.innerText();

		requireCondition(containsEmail(sectionText), "User email is not visible in Información General.");
		requireCondition(containsLikelyUserName(sectionText),
				"User name is not clearly visible in Información General.");
		firstVisibleByText(page, DEFAULT_WAIT_MS, "BUSINESS PLAN");
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Cambiar Plan");
	}

	private void executeDetallesCuentaStep(final Page page) {
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Detalles de la Cuenta");
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Cuenta creada");
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Estado activo");
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Idioma seleccionado");
	}

	private void executeTusNegociosStep(final Page page) {
		final Locator heading = firstVisibleByText(page, DEFAULT_WAIT_MS, "Tus Negocios");
		final Locator sectionContainer = closestContainer(heading);
		final String sectionText = sectionContainer.innerText();

		firstVisibleByText(page, DEFAULT_WAIT_MS, "Agregar Negocio");
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Tienes 2 de 3 negocios");

		final List<String> lines = splitLines(sectionText);
		final long businessLikeLines = lines.stream()
				.filter(line -> !line.equalsIgnoreCase("Tus Negocios"))
				.filter(line -> !line.equalsIgnoreCase("Agregar Negocio"))
				.filter(line -> !line.contains("2 de 3 negocios"))
				.filter(line -> line.length() > 2)
				.count();
		requireCondition(businessLikeLines > 0, "Business list content is not visible in Tus Negocios section.");
	}

	private void executeLegalDocumentStep(final BrowserContext context, final Page appPage, final String linkText,
			final String screenshotFileName) throws IOException {
		firstVisibleByText(appPage, DEFAULT_WAIT_MS, "Sección Legal");
		final String appUrlBeforeClick = appPage.url();
		Page legalPage = appPage;

		try {
			legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(8000),
					() -> clickAndWaitForUi(appPage, firstVisibleByText(appPage, DEFAULT_WAIT_MS, linkText)));
			legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException timeoutOrSameTab) {
			clickAndWaitForUi(appPage, firstVisibleByText(appPage, DEFAULT_WAIT_MS, linkText));
			legalPage = appPage;
			legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		}

		firstVisibleByText(legalPage, DEFAULT_WAIT_MS, linkText);
		final String pageText = legalPage.locator("body").innerText();
		requireCondition(pageText != null && pageText.trim().length() > 120,
				"Legal content text is not sufficiently visible for: " + linkText);

		captureScreenshot(linkText, legalPage, screenshotFileName, true);
		legalFinalUrls.put(linkText, legalPage.url());

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
		} else if (!appUrlBeforeClick.equals(appPage.url())) {
			try {
				appPage.goBack();
				appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			} catch (final PlaywrightException unableToGoBack) {
				appPage.navigate(appUrlBeforeClick);
				appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			}
		}

		firstVisibleByText(appPage, DEFAULT_WAIT_MS, "Sección Legal");
	}

	private void ensureMiNegocioExpanded(final Page page) {
		if (!isTextVisible(page, "Administrar Negocios", SHORT_WAIT_MS)) {
			clickTextIfVisible(page, "Negocio", SHORT_WAIT_MS);
			clickAndWaitForUi(page, firstVisibleByText(page, DEFAULT_WAIT_MS, "Mi Negocio"));
		}
		firstVisibleByText(page, DEFAULT_WAIT_MS, "Administrar Negocios");
	}

	private void clickAndWaitForUi(final Page page, final Locator locator) {
		requireVisible(locator, DEFAULT_WAIT_MS, "Element is not visible before click.");
		locator.click();
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (final PlaywrightException ignored) {
			// Not all views become fully idle; DOMContentLoaded and a short wait are enough fallback.
		}
		page.waitForTimeout(700);
	}

	private void clickTextIfVisible(final Page page, final String text, final long timeoutMs) {
		final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
		if (isVisible(locator, timeoutMs)) {
			clickAndWaitForUi(page, locator);
		}
	}

	private Locator firstVisibleByText(final Page page, final long timeoutMs, final String... texts) {
		for (final String text : texts) {
			final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
			if (isVisible(exact, timeoutMs)) {
				return exact;
			}
			final Locator contains = page.getByText(text).first();
			if (isVisible(contains, timeoutMs)) {
				return contains;
			}
		}
		throw new AssertionError("Could not find a visible element by text among: " + String.join(", ", texts));
	}

	@SafeVarargs
	private final Locator firstVisibleByLocator(final Page page, final long timeoutMs, final Locator... locators) {
		for (final Locator locator : locators) {
			if (isVisible(locator, timeoutMs)) {
				return locator;
			}
		}
		throw new AssertionError("Could not find any visible input field candidate.");
	}

	private void requireVisible(final Locator locator, final long timeoutMs, final String errorMessage) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
		} catch (final PlaywrightException e) {
			throw new AssertionError(errorMessage, e);
		}
	}

	private void waitForHidden(final Locator locator, final long timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(timeoutMs));
		} catch (final PlaywrightException e) {
			throw new AssertionError("Element did not close/hide in time.", e);
		}
	}

	private boolean isVisible(final Locator locator, final long timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private boolean isTextVisible(final Page page, final String text, final long timeoutMs) {
		final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
		return isVisible(locator, timeoutMs);
	}

	private Locator closestContainer(final Locator heading) {
		Locator container = heading.locator("xpath=ancestor::section[1]");
		if (container.count() > 0) {
			return container.first();
		}
		container = heading.locator("xpath=ancestor::div[1]");
		if (container.count() > 0) {
			return container.first();
		}
		return heading;
	}

	private void captureScreenshot(final String stepName, final Page page, final String fileName, final boolean fullPage)
			throws IOException {
		final Path screenshotPath = runArtifactsDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		addEvidence(stepName, screenshotPath);
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.run();
			markPass(reportField, "PASS");
		} catch (final Exception | AssertionError e) {
			markFail(reportField, e.getMessage());
		}
	}

	private void requireCondition(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private boolean containsEmail(final String text) {
		if (isBlank(text)) {
			return false;
		}

		for (final String token : text.split("\\s+")) {
			if (token.contains("@") && token.contains(".") && token.length() >= 6) {
				return true;
			}
		}

		return false;
	}

	private boolean containsLikelyUserName(final String text) {
		for (final String line : splitLines(text)) {
			final String normalized = line.toLowerCase(Locale.ROOT);
			if (normalized.contains("@") || normalized.contains("información general")
					|| normalized.contains("business plan") || normalized.contains("cambiar plan")) {
				continue;
			}
			if (line.length() >= 4 && line.chars().anyMatch(Character::isLetter)) {
				return true;
			}
		}
		return false;
	}

	private List<String> splitLines(final String text) {
		return text == null ? List.of()
				: text.lines()
						.map(String::trim)
						.filter(line -> !line.isEmpty())
						.collect(Collectors.toList());
	}

	private void initSteps() {
		stepResults.put(FIELD_LOGIN, new StepResult());
		stepResults.put(FIELD_MI_NEGOCIO_MENU, new StepResult());
		stepResults.put(FIELD_AGREGAR_MODAL, new StepResult());
		stepResults.put(FIELD_ADMINISTRAR_VIEW, new StepResult());
		stepResults.put(FIELD_INFO_GENERAL, new StepResult());
		stepResults.put(FIELD_DETALLES_CUENTA, new StepResult());
		stepResults.put(FIELD_TUS_NEGOCIOS, new StepResult());
		stepResults.put(FIELD_TERMINOS, new StepResult());
		stepResults.put(FIELD_POLITICA, new StepResult());
	}

	private void markPass(final String reportField, final String message) {
		final StepResult result = stepResults.get(reportField);
		result.status = "PASS";
		result.message = message;
	}

	private void markFail(final String reportField, final String message) {
		final StepResult result = stepResults.get(reportField);
		result.status = "FAIL";
		result.message = isBlank(message) ? "Validation failed." : message;
	}

	private void markRemainingAsFailed(final String message) {
		for (final String field : stepResults.keySet()) {
			markFail(field, message);
		}
	}

	private void addEvidence(final String stepName, final Path evidencePath) {
		final String reportField = mapStepNameToReportField(stepName);
		stepResults.get(reportField).evidence.add(evidencePath.toString());
	}

	private String mapStepNameToReportField(final String stepName) {
		if ("Términos y Condiciones".equals(stepName)) {
			return FIELD_TERMINOS;
		}
		if ("Política de Privacidad".equals(stepName)) {
			return FIELD_POLITICA;
		}
		return stepName;
	}

	private List<String> getFailedSteps() {
		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			if (!"PASS".equals(entry.getValue().status)) {
				failed.add(entry.getKey());
			}
		}
		return failed;
	}

	private Path createRunArtifactsDirectory() throws IOException {
		final String customBaseDir = getConfig("SALEADS_E2E_ARTIFACTS_DIR");
		final Path basePath = isBlank(customBaseDir)
				? Paths.get("target", "saleads-mi-negocio-evidence")
				: Paths.get(customBaseDir);
		final String utcTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
				.format(LocalDateTime.now(ZoneOffset.UTC));
		final Path runDir = basePath.resolve(utcTimestamp);
		Files.createDirectories(runDir);
		return runDir;
	}

	private void writeFinalReport() throws IOException {
		final Path reportPath = runArtifactsDir.resolve("final-report.txt");
		final StringBuilder builder = new StringBuilder();
		builder.append("test_name: ").append(TEST_NAME).append('\n');
		builder.append("generated_at_utc: ")
				.append(DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now(ZoneOffset.UTC))).append('\n');
		builder.append("report_path: ").append(reportPath).append('\n');
		builder.append('\n');

		builder.append("final_report:\n");
		for (final Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().status).append('\n');
			builder.append("  detail: ").append(entry.getValue().message).append('\n');
			if (!entry.getValue().evidence.isEmpty()) {
				builder.append("  evidence:\n");
				for (final String evidence : entry.getValue().evidence) {
					builder.append("    - ").append(evidence).append('\n');
				}
			}
			if (FIELD_TERMINOS.equals(entry.getKey()) && legalFinalUrls.containsKey("Términos y Condiciones")) {
				builder.append("  final_url: ").append(legalFinalUrls.get("Términos y Condiciones")).append('\n');
			}
			if (FIELD_POLITICA.equals(entry.getKey()) && legalFinalUrls.containsKey("Política de Privacidad")) {
				builder.append("  final_url: ").append(legalFinalUrls.get("Política de Privacidad")).append('\n');
			}
		}

		Files.writeString(reportPath, builder.toString(), StandardCharsets.UTF_8);
		System.out.println(builder);
	}

	private boolean isHeadless() {
		final String configured = getConfig("SALEADS_HEADLESS");
		return isBlank(configured) || Boolean.parseBoolean(configured);
	}

	private String getConfig(final String key) {
		final String propertyValue = System.getProperty(key);
		if (!isBlank(propertyValue)) {
			return propertyValue;
		}
		return System.getenv(key);
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static final class StepResult {
		private String status = "FAIL";
		private String message = "Step was not executed.";
		private final List<String> evidence = new ArrayList<>();
	}
}
