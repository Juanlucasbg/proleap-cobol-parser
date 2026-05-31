package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * E2E workflow for SaleADS.ai "Mi Negocio" module.
 *
 * Runtime configuration:
 * - saleads.login.url (or SALEADS_LOGIN_URL): login URL for current environment.
 * - saleads.headless (or SALEADS_HEADLESS): true/false, defaults to true.
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final long DEFAULT_TIMEOUT_MS = 20_000L;
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
			"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String LOGIN_FIELD = "Login";
	private static final String MI_NEGOCIO_MENU_FIELD = "Mi Negocio menu";
	private static final String AGREGAR_MODAL_FIELD = "Agregar Negocio modal";
	private static final String ADMINISTRAR_VIEW_FIELD = "Administrar Negocios view";
	private static final String INFO_GENERAL_FIELD = "Informacion General";
	private static final String DETALLES_CUENTA_FIELD = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS_FIELD = "Tus Negocios";
	private static final String TERMINOS_FIELD = "Terminos y Condiciones";
	private static final String PRIVACIDAD_FIELD = "Politica de Privacidad";

	private final Map<String, String> report = new LinkedHashMap<>();
	private final Map<String, String> reportErrors = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		initializeReport();

		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set saleads.login.url system property or SALEADS_LOGIN_URL environment variable to run this test.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"));

		evidenceDir = createEvidenceDirectory();

		playwright = Playwright.create();
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
		appPage = context.newPage();
		appPage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
		appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
		waitForUi(appPage);
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
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final boolean loginOk = runStep(LOGIN_FIELD, this::stepLoginWithGoogle);
		final boolean menuOk = loginOk && runStep(MI_NEGOCIO_MENU_FIELD, this::stepOpenMiNegocioMenu);
		final boolean modalOk = menuOk && runStep(AGREGAR_MODAL_FIELD, this::stepValidateAgregarNegocioModal);
		final boolean administrarOk = menuOk && runStep(ADMINISTRAR_VIEW_FIELD, this::stepOpenAdministrarNegocios);
		final boolean infoOk = administrarOk && runStep(INFO_GENERAL_FIELD, this::stepValidateInformacionGeneral);
		final boolean detallesOk = administrarOk && runStep(DETALLES_CUENTA_FIELD, this::stepValidateDetallesCuenta);
		final boolean negociosOk = administrarOk && runStep(TUS_NEGOCIOS_FIELD, this::stepValidateTusNegocios);
		final boolean terminosOk = administrarOk
				&& runStep(TERMINOS_FIELD, () -> stepValidateLegalLink("T\u00e9rminos y Condiciones"));
		final boolean privacidadOk = administrarOk
				&& runStep(PRIVACIDAD_FIELD, () -> stepValidateLegalLink("Pol\u00edtica de Privacidad"));

		if (!loginOk) {
			markBlocked(MI_NEGOCIO_MENU_FIELD, "Blocked by login failure.");
		}
		if (!menuOk) {
			markBlocked(AGREGAR_MODAL_FIELD, "Blocked by Mi Negocio menu failure.");
			markBlocked(ADMINISTRAR_VIEW_FIELD, "Blocked by Mi Negocio menu failure.");
		}
		if (!administrarOk) {
			markBlocked(INFO_GENERAL_FIELD, "Blocked by Administrar Negocios failure.");
			markBlocked(DETALLES_CUENTA_FIELD, "Blocked by Administrar Negocios failure.");
			markBlocked(TUS_NEGOCIOS_FIELD, "Blocked by Administrar Negocios failure.");
			markBlocked(TERMINOS_FIELD, "Blocked by Administrar Negocios failure.");
			markBlocked(PRIVACIDAD_FIELD, "Blocked by Administrar Negocios failure.");
		}

		final Path reportPath = writeFinalReport();
		final String renderedReport = renderReport(reportPath);

		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			if (!"PASS".equals(entry.getValue())) {
				failed.add(entry.getKey());
			}
		}

		if (!failed.isEmpty()) {
			fail("One or more validations failed: " + failed + System.lineSeparator() + renderedReport);
		}
	}

	private void stepLoginWithGoogle() throws IOException {
		Page authPage = appPage;
		try {
			authPage = context.waitForPage(() -> clickByVisibleText(appPage, "Sign in with Google", "Login with Google",
					"Iniciar sesion con Google", "Acceder con Google", "Google"));
			authPage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
		} catch (final PlaywrightException noPopup) {
			clickByVisibleText(appPage, "Sign in with Google", "Login with Google", "Iniciar sesion con Google",
					"Acceder con Google", "Google");
		}

		handleGoogleAccountSelection(authPage);
		appPage.bringToFront();
		waitForUi(appPage);

		final boolean mainInterfaceVisible = isAnyVisible(appPage.locator("main"), appPage.locator("[data-testid='main']"),
				appPage.getByText("Dashboard"), appPage.getByText("Inicio"), appPage.getByText("Mi Negocio"));
		assertTrue("Main application interface should be visible after login.", mainInterfaceVisible);

		final boolean leftSidebarVisible = isAnyVisible(appPage.locator("aside"), appPage.getByText("Negocio"),
				appPage.getByText("Mi Negocio"));
		assertTrue("Left sidebar should be visible after login.", leftSidebarVisible);

		takeScreenshot(appPage, "01-dashboard-loaded.png", true);
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		clickByVisibleText(appPage, "Negocio");
		clickByVisibleText(appPage, "Mi Negocio");

		assertTextVisible(appPage, "Agregar Negocio");
		assertTextVisible(appPage, "Administrar Negocios");
		takeScreenshot(appPage, "02-mi-negocio-menu-expanded.png", true);
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByVisibleText(appPage, "Agregar Negocio");

		assertTextVisible(appPage, "Crear Nuevo Negocio");
		assertTextVisible(appPage, "Nombre del Negocio");
		assertTextVisible(appPage, "Tienes 2 de 3 negocios");
		assertTextVisible(appPage, "Cancelar");
		assertTextVisible(appPage, "Crear Negocio");

		final Locator anyInput = appPage.locator("input");
		assertTrue("Expected at least one input field in the modal.", anyInput.count() > 0);

		takeScreenshot(appPage, "03-agregar-negocio-modal.png", true);

		final Locator firstInput = anyInput.first();
		if (isVisible(firstInput)) {
			firstInput.click();
			firstInput.fill("Negocio Prueba Automatizacion");
			waitForUi(appPage);
		}
		clickByVisibleText(appPage, "Cancelar");
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isVisibleAnyText(appPage, "Administrar Negocios")) {
			clickByVisibleText(appPage, "Mi Negocio");
		}
		clickByVisibleText(appPage, "Administrar Negocios");

		assertTextVisible(appPage, "Informaci\u00f3n General");
		assertTextVisible(appPage, "Detalles de la Cuenta");
		assertTextVisible(appPage, "Tus Negocios");
		assertTextVisible(appPage, "Secci\u00f3n Legal");
		takeScreenshot(appPage, "04-administrar-negocios.png", true);
	}

	private void stepValidateInformacionGeneral() {
		assertTextVisible(appPage, "Informaci\u00f3n General");
		assertTextVisible(appPage, "BUSINESS PLAN");
		assertTextVisible(appPage, "Cambiar Plan");

		final String bodyText = safeInnerText(appPage.locator("body"));
		assertTrue("User email should be visible in account page.", EMAIL_PATTERN.matcher(bodyText).find());
		assertTrue("Google account email should be visible in account page.", bodyText.contains(GOOGLE_ACCOUNT));
		assertTrue("Expected a user name/value to be visible in Informacion General section.", hasLikelyName(bodyText));
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible(appPage, "Detalles de la Cuenta");
		assertTextVisible(appPage, "Cuenta creada");
		assertTrue("Expected active account status text.", isVisibleAnyText(appPage, "Estado activo", "Estado Activo"));
		assertTextVisible(appPage, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible(appPage, "Tus Negocios");
		assertTextVisible(appPage, "Agregar Negocio");
		assertTextVisible(appPage, "Tienes 2 de 3 negocios");
	}

	private void stepValidateLegalLink(final String linkText) throws IOException {
		final Page originPage = appPage;
		Page legalPage = originPage;
		boolean openedNewTab = false;

		try {
			legalPage = context.waitForPage(() -> clickByVisibleText(originPage, linkText));
			openedNewTab = true;
			legalPage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
		} catch (final PlaywrightException noPopup) {
			clickByVisibleText(originPage, linkText);
		}

		waitForUi(legalPage);
		assertTextVisible(legalPage, linkText);

		final String legalText = safeInnerText(legalPage.locator("body"));
		assertTrue("Legal page should contain descriptive text.", legalText != null && legalText.trim().length() >= 120);

		final String fileName = linkText.contains("Privacidad") ? "06-politica-privacidad.png" : "05-terminos.png";
		takeScreenshot(legalPage, fileName, true);
		legalUrls.put(linkText, legalPage.url());

		if (openedNewTab && legalPage != originPage) {
			legalPage.close();
			originPage.bringToFront();
			waitForUi(originPage);
			return;
		}

		try {
			originPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(originPage);
		} catch (final PlaywrightException ignored) {
			originPage.bringToFront();
			waitForUi(originPage);
		}
	}

	private boolean runStep(final String field, final ThrowingRunnable stepAction) {
		try {
			stepAction.run();
			report.put(field, "PASS");
			return true;
		} catch (final Throwable failure) {
			report.put(field, "FAIL");
			reportErrors.put(field, oneLineMessage(failure));
			return false;
		}
	}

	private void markBlocked(final String field, final String reason) {
		if (!reportErrors.containsKey(field)) {
			reportErrors.put(field, reason);
		}
	}

	private void initializeReport() {
		report.put(LOGIN_FIELD, "FAIL");
		report.put(MI_NEGOCIO_MENU_FIELD, "FAIL");
		report.put(AGREGAR_MODAL_FIELD, "FAIL");
		report.put(ADMINISTRAR_VIEW_FIELD, "FAIL");
		report.put(INFO_GENERAL_FIELD, "FAIL");
		report.put(DETALLES_CUENTA_FIELD, "FAIL");
		report.put(TUS_NEGOCIOS_FIELD, "FAIL");
		report.put(TERMINOS_FIELD, "FAIL");
		report.put(PRIVACIDAD_FIELD, "FAIL");
	}

	private Path writeFinalReport() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio workflow report").append(System.lineSeparator());
		builder.append("=================================").append(System.lineSeparator());
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			builder.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			if (reportErrors.containsKey(entry.getKey())) {
				builder.append("  detail: ").append(reportErrors.get(entry.getKey())).append(System.lineSeparator());
			}
		}

		builder.append(System.lineSeparator()).append("Legal URLs").append(System.lineSeparator());
		builder.append("----------").append(System.lineSeparator());
		builder.append("Terminos y Condiciones URL: ")
				.append(legalUrls.getOrDefault("T\u00e9rminos y Condiciones", "N/A")).append(System.lineSeparator());
		builder.append("Politica de Privacidad URL: ")
				.append(legalUrls.getOrDefault("Pol\u00edtica de Privacidad", "N/A")).append(System.lineSeparator());

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, builder.toString(), StandardCharsets.UTF_8);
		return reportPath;
	}

	private String renderReport(final Path reportPath) throws IOException {
		return Files.readString(reportPath, StandardCharsets.UTF_8);
	}

	private Path createEvidenceDirectory() throws IOException {
		final String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path path = Paths.get("target", "saleads-evidence", stamp);
		Files.createDirectories(path);
		return path;
	}

	private void takeScreenshot(final Page page, final String name, final boolean fullPage) throws IOException {
		final Path screenshotPath = evidenceDir.resolve(name);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		final Path manifestPath = evidenceDir.resolve("screenshots.txt");
		Files.writeString(manifestPath, screenshotPath.toString() + System.lineSeparator(), StandardCharsets.UTF_8,
				Files.exists(manifestPath) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
	}

	private void handleGoogleAccountSelection(final Page page) {
		waitForUi(page);
		if (isVisibleAnyText(page, GOOGLE_ACCOUNT)) {
			clickByVisibleText(page, GOOGLE_ACCOUNT);
			waitForUi(page);
		}
	}

	private void clickByVisibleText(final Page page, final String... candidates) {
		Locator locator = null;
		String chosen = null;
		for (final String candidate : candidates) {
			locator = findVisibleByText(page, candidate, true);
			if (locator != null) {
				chosen = candidate;
				break;
			}
		}

		if (locator == null) {
			for (final String candidate : candidates) {
				locator = findVisibleByText(page, candidate, false);
				if (locator != null) {
					chosen = candidate;
					break;
				}
			}
		}

		if (locator == null) {
			throw new AssertionError("Could not find visible element by text in candidates: " + String.join(", ", candidates));
		}

		try {
			locator.click();
		} catch (final PlaywrightException clickError) {
			throw new AssertionError("Failed clicking element '" + chosen + "': " + clickError.getMessage(), clickError);
		}
		waitForUi(page);
	}

	private Locator findVisibleByText(final Page page, final String text, final boolean exact) {
		final Locator candidates = exact ? page.getByText(text, new Page.GetByTextOptions().setExact(true)) : page.getByText(text);
		final int total = Math.min(candidates.count(), 8);
		for (int i = 0; i < total; i++) {
			final Locator candidate = candidates.nth(i);
			if (isVisible(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private void assertTextVisible(final Page page, final String text) {
		assertTrue("Expected visible text: " + text, isVisibleAnyText(page, text));
	}

	private boolean isVisibleAnyText(final Page page, final String... texts) {
		for (final String text : texts) {
			if (findVisibleByText(page, text, true) != null || findVisibleByText(page, text, false) != null) {
				return true;
			}
		}
		return false;
	}

	private boolean isAnyVisible(final Locator... locators) {
		for (final Locator locator : locators) {
			if (isVisible(locator)) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator != null && locator.isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private String safeInnerText(final Locator locator) {
		try {
			return locator.innerText();
		} catch (final PlaywrightException ignored) {
			return "";
		}
	}

	private boolean hasLikelyName(final String text) {
		final String normalized = text == null ? "" : text;
		final String[] lines = normalized.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.length() < 3 || line.length() > 80) {
				continue;
			}
			if (line.contains("@") || line.matches(".*\\d.*")) {
				continue;
			}
			final String lower = line.toLowerCase();
			if (lower.contains("informacion") || lower.contains("business plan") || lower.contains("cambiar plan")
					|| lower.contains("detalles de la cuenta") || lower.contains("seccion legal")
					|| lower.contains("tus negocios")) {
				continue;
			}
			if (line.matches("[A-Za-z][A-Za-z ]+[A-Za-z]")) {
				return true;
			}
		}
		return false;
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// Not all clicks trigger navigation; a short settle time still helps.
		}
		page.waitForTimeout(900);
	}

	private String oneLineMessage(final Throwable throwable) {
		if (throwable == null || throwable.getMessage() == null) {
			return "No error message";
		}
		return throwable.getMessage().replaceAll("[\\r\\n]+", " ").trim();
	}

	private String readConfig(final String propertyName, final String envName) {
		return readConfig(propertyName, envName, null);
	}

	private String readConfig(final String propertyName, final String envName, final String fallback) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		return fallback;
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
