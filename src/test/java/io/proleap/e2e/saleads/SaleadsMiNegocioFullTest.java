package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullTest {

	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_GENERAL = "Informacion General";
	private static final String STEP_ACCOUNT = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Terminos y Condiciones";
	private static final String STEP_PRIVACY = "Politica de Privacidad";

	private final Map<String, StepResult> report = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final var loginUrl = System.getenv(LOGIN_URL_ENV);
		Assume.assumeTrue("Set " + LOGIN_URL_ENV + " to run this E2E workflow.", loginUrl != null && !loginUrl.isBlank());

		evidenceDir = Path.of("target", "evidence", "saleads_mi_negocio_full_test");
		Files.createDirectories(evidenceDir);

		playwright = Playwright.create();
		browser = playwright.chromium()
				.launch(new BrowserType.LaunchOptions().setHeadless(readBooleanEnv("PLAYWRIGHT_HEADLESS", true)));
		context = browser.newContext();
		context.setDefaultTimeout(15_000);
		context.setDefaultNavigationTimeout(30_000);
		page = context.newPage();
		page.navigate(loginUrl);
		waitForUi(page);
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
		runStep(STEP_LOGIN, this::stepLoginWithGoogle);
		runStep(STEP_MENU, this::stepOpenMiNegocioMenu);
		runStep(STEP_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(STEP_ADMIN_VIEW, this::stepOpenAdministrarNegocios);
		runStep(STEP_GENERAL, this::stepValidateInformacionGeneral);
		runStep(STEP_ACCOUNT, this::stepValidateDetallesCuenta);
		runStep(STEP_BUSINESSES, this::stepValidateTusNegocios);
		runStep(STEP_TERMS, this::stepValidateTerminosYCondiciones);
		runStep(STEP_PRIVACY, this::stepValidatePoliticaPrivacidad);

		final var hasFailures = report.values().stream().anyMatch(stepResult -> !stepResult.passed);
		assertTrue("Some SaleADS validations failed. See target/evidence/saleads_mi_negocio_full_test/final-report.txt.",
				!hasFailures);
	}

	private String stepLoginWithGoogle() throws IOException {
		clickFirstVisibleText(page,
				List.of("Sign in with Google", "Iniciar sesion con Google", "Continuar con Google", "Google"));

		// Account selector is optional. Continue if already authenticated.
		clickIfVisible(page, List.of(GOOGLE_EMAIL), 5_000);

		assertAnyTextVisible(page, List.of("Negocio", "Mi Negocio", "Dashboard", "Panel"));
		assertSidebarVisible();
		takeScreenshot(page, "01-dashboard-loaded.png", true);
		return "Main interface and sidebar visible";
	}

	private String stepOpenMiNegocioMenu() throws IOException {
		assertSidebarVisible();
		clickIfVisible(page, List.of("Negocio"), 2_500);
		clickFirstVisibleText(page, List.of("Mi Negocio"));
		assertAnyTextVisible(page, List.of("Agregar Negocio"));
		assertAnyTextVisible(page, List.of("Administrar Negocios"));
		takeScreenshot(page, "02-mi-negocio-menu-expanded.png", false);
		return "Mi Negocio submenu expanded";
	}

	private String stepValidateAgregarNegocioModal() throws IOException {
		clickFirstVisibleText(page, List.of("Agregar Negocio"));
		assertAnyTextVisible(page, List.of("Crear Nuevo Negocio"));

		final var namedInputVisible = isLocatorVisible(
				page.locator("input[placeholder*='Nombre del Negocio'], input[name*='nombre'], input[id*='nombre']"),
				6_000);
		assertTrue("Input field 'Nombre del Negocio' was not found.", namedInputVisible);

		assertAnyTextVisible(page, List.of("Tienes 2 de 3 negocios"));
		assertAnyTextVisible(page, List.of("Cancelar"));
		assertAnyTextVisible(page, List.of("Crear Negocio"));

		takeScreenshot(page, "03-agregar-negocio-modal.png", false);

		clickIfVisible(page, List.of("Nombre del Negocio"), 2_000);
		fillFirstVisibleInput("Negocio Prueba Automatizacion");
		clickFirstVisibleText(page, List.of("Cancelar"));
		return "Agregar Negocio modal validated";
	}

	private String stepOpenAdministrarNegocios() throws IOException {
		clickIfVisible(page, List.of("Mi Negocio"), 2_500);
		clickFirstVisibleText(page, List.of("Administrar Negocios"));

		assertAnyTextVisible(page, List.of("Informacion General", "Informacion general"));
		assertAnyTextVisible(page, List.of("Detalles de la Cuenta"));
		assertAnyTextVisible(page, List.of("Tus Negocios"));
		assertAnyTextVisible(page, List.of("Seccion Legal", "Seccion legal"));

		takeScreenshot(page, "04-administrar-negocios-full-page.png", true);
		return "Administrar Negocios page loaded";
	}

	private String stepValidateInformacionGeneral() {
		final var hasEmail = isLocatorVisible(page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/"), 4_000);
		assertTrue("User email is not visible.", hasEmail);
		assertAnyTextVisible(page, List.of("BUSINESS PLAN"));
		assertAnyTextVisible(page, List.of("Cambiar Plan"));
		return "Informacion General validated";
	}

	private String stepValidateDetallesCuenta() {
		assertAnyTextVisible(page, List.of("Cuenta creada"));
		assertAnyTextVisible(page, List.of("Estado activo", "Activo"));
		assertAnyTextVisible(page, List.of("Idioma seleccionado", "Idioma"));
		return "Detalles de la Cuenta validated";
	}

	private String stepValidateTusNegocios() {
		assertAnyTextVisible(page, List.of("Tus Negocios"));
		assertAnyTextVisible(page, List.of("Agregar Negocio"));
		assertAnyTextVisible(page, List.of("Tienes 2 de 3 negocios"));
		return "Tus Negocios validated";
	}

	private String stepValidateTerminosYCondiciones() throws IOException {
		final var url = openAndValidateLegalLink(List.of("Terminos y Condiciones", "T\u00e9rminos y Condiciones"),
				List.of("Terminos y Condiciones", "T\u00e9rminos y Condiciones"), "08-terminos-y-condiciones.png");
		return "Validated URL: " + url;
	}

	private String stepValidatePoliticaPrivacidad() throws IOException {
		final var url = openAndValidateLegalLink(List.of("Politica de Privacidad", "Pol\u00edtica de Privacidad"),
				List.of("Politica de Privacidad", "Pol\u00edtica de Privacidad"), "09-politica-de-privacidad.png");
		return "Validated URL: " + url;
	}

	private String openAndValidateLegalLink(final List<String> clickTexts, final List<String> headingTexts,
			final String screenshotName) throws IOException {
		final var appTab = page;
		final var pageCountBefore = context.pages().size();
		clickFirstVisibleText(appTab, clickTexts);

		final var targetPage = waitForNewTabIfAny(pageCountBefore, 7_000).orElse(appTab);
		waitForUi(targetPage);
		assertAnyTextVisible(targetPage, headingTexts);
		assertHasLegalContent(targetPage);
		takeScreenshot(targetPage, screenshotName, true);
		final var finalUrl = targetPage.url();

		if (targetPage != appTab) {
			targetPage.close();
			appTab.bringToFront();
			waitForUi(appTab);
		} else {
			appTab.goBack();
			waitForUi(appTab);
		}

		return finalUrl;
	}

	private void assertSidebarVisible() {
		final var sidebarVisible = isLocatorVisible(page.locator("aside, nav"), 6_000);
		assertTrue("Left sidebar navigation is not visible.", sidebarVisible);
	}

	private void assertHasLegalContent(final Page targetPage) {
		final var likelyContent = targetPage.locator("article, main, section, p");
		assertTrue("Legal content text is not visible.", isLocatorVisible(likelyContent, 6_000));
	}

	private void fillFirstVisibleInput(final String value) {
		final var candidates = Arrays.asList(
				page.locator("input[placeholder*='Nombre del Negocio']"),
				page.locator("input[name*='nombre']"),
				page.locator("input[id*='nombre']"));
		for (final var candidate : candidates) {
			if (isLocatorVisible(candidate, 2_000)) {
				candidate.first().fill(value);
				waitForUi(page);
				return;
			}
		}
		throw new AssertionError("No visible input field was found to type the business name.");
	}

	private void clickFirstVisibleText(final Page targetPage, final List<String> texts) {
		for (final var text : texts) {
			final var locator = targetPage.getByText(Pattern.compile("(?iu).*" + Pattern.quote(text) + ".*"));
			if (isLocatorVisible(locator, 2_500)) {
				locator.first().click();
				waitForUi(targetPage);
				return;
			}
		}
		throw new AssertionError("None of the expected texts were visible/clickable: " + texts);
	}

	private void clickIfVisible(final Page targetPage, final List<String> texts, final double timeoutMs) {
		for (final var text : texts) {
			final var locator = targetPage.getByText(Pattern.compile("(?iu).*" + Pattern.quote(text) + ".*"));
			if (isLocatorVisible(locator, timeoutMs)) {
				locator.first().click();
				waitForUi(targetPage);
				return;
			}
		}
	}

	private void assertAnyTextVisible(final Page targetPage, final List<String> expectedTexts) {
		for (final var expectedText : expectedTexts) {
			final var locator = targetPage.getByText(Pattern.compile("(?iu).*" + Pattern.quote(expectedText) + ".*"));
			if (isLocatorVisible(locator, 6_000)) {
				return;
			}
		}
		throw new AssertionError("None of the expected texts were visible: " + expectedTexts);
	}

	private boolean isLocatorVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.first()
					.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final TimeoutError e) {
			return false;
		}
	}

	private java.util.Optional<Page> waitForNewTabIfAny(final int previousPageCount, final long timeoutMs) {
		final var startedAt = System.currentTimeMillis();
		while (System.currentTimeMillis() - startedAt < timeoutMs) {
			final var allPages = context.pages();
			if (allPages.size() > previousPageCount) {
				return java.util.Optional.of(allPages.get(allPages.size() - 1));
			}
			page.waitForTimeout(200);
		}
		return java.util.Optional.empty();
	}

	private void takeScreenshot(final Page targetPage, final String fileName, final boolean fullPage) throws IOException {
		Files.createDirectories(evidenceDir);
		targetPage.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private void waitForUi(final Page targetPage) {
		targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		targetPage.waitForTimeout(900);
	}

	private boolean readBooleanEnv(final String key, final boolean defaultValue) {
		final var raw = System.getenv(key);
		if (raw == null || raw.isBlank()) {
			return defaultValue;
		}
		return Boolean.parseBoolean(raw);
	}

	private void runStep(final String stepName, final StepExecutable executable) {
		try {
			final var details = executable.execute();
			report.put(stepName, StepResult.pass(details));
		} catch (final Throwable throwable) {
			report.put(stepName, StepResult.fail(throwable.getClass().getSimpleName() + ": " + throwable.getMessage()));
		}
	}

	private void writeFinalReport() throws IOException {
		if (evidenceDir == null) {
			return;
		}

		Files.createDirectories(evidenceDir);
		final var now = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(Instant.now());
		final var lines = new StringBuilder();
		lines.append("saleads_mi_negocio_full_test").append('\n');
		lines.append("generated_at=").append(now).append('\n');
		lines.append("login_url=").append(System.getenv(LOGIN_URL_ENV)).append('\n');
		lines.append('\n');
		for (final var stepName : orderedStepNames()) {
			final var result = report.getOrDefault(stepName, StepResult.fail("NOT_EXECUTED"));
			lines.append(stepName).append(": ").append(result.passed ? "PASS" : "FAIL");
			if (result.details != null && !result.details.isBlank()) {
				lines.append(" - ").append(result.details);
			}
			lines.append('\n');
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), lines.toString(), StandardCharsets.UTF_8);
	}

	private List<String> orderedStepNames() {
		return List.of(STEP_LOGIN, STEP_MENU, STEP_MODAL, STEP_ADMIN_VIEW, STEP_GENERAL, STEP_ACCOUNT, STEP_BUSINESSES,
				STEP_TERMS, STEP_PRIVACY);
	}

	@FunctionalInterface
	private interface StepExecutable {
		String execute() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
