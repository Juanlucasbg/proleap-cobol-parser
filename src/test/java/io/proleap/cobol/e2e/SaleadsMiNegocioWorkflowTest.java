package io.proleap.cobol.e2e;

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
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * End-to-end UI validation for SaleADS "Mi Negocio" workflow.
 *
 * Runtime configuration:
 * - SALEADS_LOGIN_URL: required; environment-specific login URL.
 * - SALEADS_HEADLESS: optional; defaults to true.
 * - SALEADS_GOOGLE_ACCOUNT: optional; defaults to juanlucasbarbiergarzon@gmail.com.
 * - SALEADS_ARTIFACTS_DIR: optional; defaults to target/saleads-evidence.
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private static final List<String> REPORT_FIELDS = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String loginUrl = trimToNull(readConfig("SALEADS_LOGIN_URL", "saleads.login.url"));
		Assume.assumeTrue(
				"SALEADS_LOGIN_URL (or -Dsaleads.login.url) is required for this environment-agnostic UI test.",
				loginUrl != null);

		final String googleAccount = readConfig("SALEADS_GOOGLE_ACCOUNT", "saleads.google.account",
				DEFAULT_GOOGLE_ACCOUNT);
		final boolean headless = Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "saleads.headless", "true"));
		final Path artifactsDir = initArtifactsDirectory();

		final Map<String, String> statuses = new LinkedHashMap<>();
		final Map<String, String> details = new LinkedHashMap<>();
		initializeReport(statuses, details);

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 900));

			final Page[] appPageRef = new Page[] { context.newPage() };
			appPageRef[0].navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(appPageRef[0]);

			final boolean loginOk = runStep(statuses, details, "Login", () -> {
				Page resolvedPage = loginWithGoogleAndValidateApp(context, appPageRef[0], googleAccount, artifactsDir, details);
				if (resolvedPage != appPageRef[0]) {
					appPageRef[0].close();
					appPageRef[0] = resolvedPage;
				}
			});

			final boolean menuOk = loginOk ? runStep(statuses, details, "Mi Negocio menu", () -> {
				openMiNegocioMenu(appPageRef[0]);
				Path screenshot = captureScreenshot(appPageRef[0], artifactsDir, "02-mi-negocio-menu-expandido", false);
				appendDetail(details, "Mi Negocio menu", "Menu screenshot: " + screenshot);
			}) : blockStep(statuses, details, "Mi Negocio menu", "Blocked because login did not pass.");

			final boolean modalOk = menuOk ? runStep(statuses, details, "Agregar Negocio modal", () -> {
				validateAgregarNegocioModal(appPageRef[0], artifactsDir, details);
			}) : blockStep(statuses, details, "Agregar Negocio modal",
					"Blocked because Mi Negocio menu did not pass.");

			final boolean administrarOk = modalOk ? runStep(statuses, details, "Administrar Negocios view", () -> {
				openAdministrarNegocios(appPageRef[0], artifactsDir, details);
			}) : blockStep(statuses, details, "Administrar Negocios view",
					"Blocked because Agregar Negocio modal did not pass.");

			final boolean infoOk = administrarOk ? runStep(statuses, details, "Información General", () -> {
				validateInformacionGeneral(appPageRef[0]);
			}) : blockStep(statuses, details, "Información General",
					"Blocked because Administrar Negocios view did not pass.");

			final boolean cuentaOk = infoOk ? runStep(statuses, details, "Detalles de la Cuenta", () -> {
				assertAnyTextVisible(appPageRef[0], List.of("Cuenta creada"), 15000);
				assertAnyTextVisible(appPageRef[0], List.of("Estado activo", "Estado Activo"), 15000);
				assertAnyTextVisible(appPageRef[0], List.of("Idioma seleccionado", "Idioma Seleccionado"), 15000);
			}) : blockStep(statuses, details, "Detalles de la Cuenta",
					"Blocked because Información General did not pass.");

			final boolean negociosOk = cuentaOk ? runStep(statuses, details, "Tus Negocios", () -> {
				assertAnyTextVisible(appPageRef[0], List.of("Tus Negocios"), 15000);
				assertAnyTextVisible(appPageRef[0], List.of("Agregar Negocio"), 15000);
				assertAnyTextVisible(appPageRef[0], List.of("Tienes 2 de 3 negocios"), 15000);
			}) : blockStep(statuses, details, "Tus Negocios",
					"Blocked because Detalles de la Cuenta did not pass.");

			final boolean terminosOk = negociosOk ? runStep(statuses, details, "Términos y Condiciones", () -> {
				validateLegalLink(appPageRef[0], context, "Términos y Condiciones", "Términos y Condiciones", artifactsDir,
						details, "Términos y Condiciones");
			}) : blockStep(statuses, details, "Términos y Condiciones",
					"Blocked because Tus Negocios did not pass.");

			final boolean politicasOk = terminosOk ? runStep(statuses, details, "Política de Privacidad", () -> {
				validateLegalLink(appPageRef[0], context, "Política de Privacidad", "Política de Privacidad", artifactsDir,
						details, "Política de Privacidad");
			}) : blockStep(statuses, details, "Política de Privacidad",
					"Blocked because Términos y Condiciones did not pass.");

			final Path reportPath = writeFinalReport(artifactsDir, statuses, details, loginUrl);
			final boolean allPassed = loginOk && menuOk && modalOk && administrarOk && infoOk && cuentaOk && negociosOk
					&& terminosOk && politicasOk;

			assertTrue("One or more validations failed. Final report: " + reportPath, allPassed);
		}
	}

	private Page loginWithGoogleAndValidateApp(final BrowserContext context, final Page appPage, final String accountEmail,
			final Path artifactsDir, final Map<String, String> details) throws IOException {
		if (!isAnyTextVisible(appPage, List.of("Negocio", "Mi Negocio"), 2500)) {
			Page popup = null;
			try {
				popup = context.waitForPage(() -> clickByVisibleText(appPage,
						List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"), 20000),
						new BrowserContext.WaitForPageOptions().setTimeout(7000));
			} catch (PlaywrightException ignored) {
				// Login could remain in the same tab; ignore timeout on popup detection.
			}

			if (popup != null) {
				waitForUi(popup);
				maybePickGoogleAccount(popup, accountEmail);
			} else {
				maybePickGoogleAccount(appPage, accountEmail);
			}
		}

		Page resolvedPage = resolveAppPage(context, appPage);
		waitForUi(resolvedPage);

		assertAnyTextVisible(resolvedPage, List.of("Mi Negocio", "Negocio"), 120000);
		assertTrue("Expected left sidebar navigation to be visible.",
				isAnySelectorVisible(resolvedPage, List.of("aside:has-text(\"Negocio\")", "nav:has-text(\"Negocio\")"), 15000));

		Path screenshot = captureScreenshot(resolvedPage, artifactsDir, "01-dashboard", false);
		appendDetail(details, "Login", "Dashboard screenshot: " + screenshot);
		return resolvedPage;
	}

	private void openMiNegocioMenu(final Page appPage) {
		if (!isAnyTextVisible(appPage, List.of("Mi Negocio"), 3000)) {
			clickByVisibleText(appPage, List.of("Negocio"), 15000);
		}

		clickByVisibleText(appPage, List.of("Mi Negocio"), 15000);
		if (!(isAnyTextVisible(appPage, List.of("Agregar Negocio"), 4000)
				&& isAnyTextVisible(appPage, List.of("Administrar Negocios"), 4000))) {
			clickByVisibleText(appPage, List.of("Mi Negocio"), 15000);
		}

		assertAnyTextVisible(appPage, List.of("Agregar Negocio"), 15000);
		assertAnyTextVisible(appPage, List.of("Administrar Negocios"), 15000);
	}

	private void validateAgregarNegocioModal(final Page appPage, final Path artifactsDir, final Map<String, String> details)
			throws IOException {
		ensureMiNegocioExpanded(appPage);
		clickByVisibleText(appPage, List.of("Agregar Negocio"), 15000);

		assertAnyTextVisible(appPage, List.of("Crear Nuevo Negocio"), 15000);
		assertAnyTextVisible(appPage, List.of("Nombre del Negocio"), 15000);
		assertAnyTextVisible(appPage, List.of("Tienes 2 de 3 negocios"), 15000);
		assertAnyTextVisible(appPage, List.of("Cancelar"), 15000);
		assertAnyTextVisible(appPage, List.of("Crear Negocio"), 15000);

		Path screenshot = captureScreenshot(appPage, artifactsDir, "03-agregar-negocio-modal", false);
		appendDetail(details, "Agregar Negocio modal", "Modal screenshot: " + screenshot);

		fillNombreDelNegocioIfPresent(appPage, "Negocio Prueba Automatización");
		clickByVisibleText(appPage, List.of("Cancelar"), 10000);
		waitForUi(appPage);
	}

	private void openAdministrarNegocios(final Page appPage, final Path artifactsDir, final Map<String, String> details)
			throws IOException {
		ensureMiNegocioExpanded(appPage);
		clickByVisibleText(appPage, List.of("Administrar Negocios"), 20000);

		assertAnyTextVisible(appPage, List.of("Información General", "Informacion General"), 25000);
		assertAnyTextVisible(appPage, List.of("Detalles de la Cuenta", "Detalles de la cuenta"), 25000);
		assertAnyTextVisible(appPage, List.of("Tus Negocios"), 25000);
		assertAnyTextVisible(appPage, List.of("Sección Legal", "Seccion Legal"), 25000);

		Path screenshot = captureScreenshot(appPage, artifactsDir, "04-administrar-negocios", true);
		appendDetail(details, "Administrar Negocios view", "Account page screenshot: " + screenshot);
	}

	private void validateInformacionGeneral(final Page appPage) {
		assertAnyTextVisible(appPage, List.of("Información General", "Informacion General"), 15000);
		assertAnyTextVisible(appPage, List.of("BUSINESS PLAN"), 15000);
		assertAnyTextVisible(appPage, List.of("Cambiar Plan"), 15000);

		final String bodyText = safeInnerText(appPage.locator("body"));
		assertTrue("Expected a user email to be visible in Información General section.",
				Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(bodyText).find());
		assertTrue("Expected a user name-like text to be visible in Información General section.",
				Pattern.compile("\\b[\\p{L}]{2,}(?:\\s+[\\p{L}]{2,})+\\b").matcher(bodyText).find());
	}

	private void validateLegalLink(final Page appPage, final BrowserContext context, final String linkText,
			final String headingText, final Path artifactsDir, final Map<String, String> details, final String reportField)
			throws IOException {
		Page legalPage = appPage;
		boolean openedNewTab = false;

		try {
			legalPage = context.waitForPage(() -> clickByVisibleText(appPage, List.of(linkText), 20000),
					new BrowserContext.WaitForPageOptions().setTimeout(7000));
			openedNewTab = true;
			legalPage.bringToFront();
		} catch (PlaywrightException ignored) {
			// Navigation can happen in the same tab; keep current app page reference.
		}

		waitForUi(legalPage);
		assertAnyTextVisible(legalPage, List.of(headingText), 20000);

		final String legalBody = safeInnerText(legalPage.locator("body"));
		assertTrue("Expected legal content text to be visible for " + reportField + ".", legalBody.length() > 250);

		final Path screenshot = captureScreenshot(legalPage, artifactsDir, "legal-" + slugify(reportField), true);
		appendDetail(details, reportField, "Legal page screenshot: " + screenshot);
		appendDetail(details, reportField, "Final URL: " + legalPage.url());

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(appPage);
		}
	}

	private void maybePickGoogleAccount(final Page page, final String accountEmail) {
		if (!isAnyTextVisible(page, List.of(accountEmail), 8000)) {
			return;
		}

		clickByVisibleText(page, List.of(accountEmail), 10000);
		waitForUi(page);
	}

	private void ensureMiNegocioExpanded(final Page appPage) {
		if (isAnyTextVisible(appPage, List.of("Agregar Negocio"), 1500)
				&& isAnyTextVisible(appPage, List.of("Administrar Negocios"), 1500)) {
			return;
		}
		clickByVisibleText(appPage, List.of("Mi Negocio"), 15000);
		waitForUi(appPage);
	}

	private void fillNombreDelNegocioIfPresent(final Page page, final String value) {
		Locator input = page.locator("input[placeholder*='Nombre del Negocio']").first();
		if (!safeVisible(input)) {
			input = page.locator("input[name*='nombre'], input[id*='nombre']").first();
		}

		if (safeVisible(input)) {
			input.fill(value);
			waitForUi(page);
		}
	}

	private void clickByVisibleText(final Page page, final List<String> textCandidates, final int timeoutMs) {
		Locator target = waitForFirstVisibleLocator(() -> buildTextCandidateLocators(page, textCandidates), timeoutMs);
		target.scrollIntoViewIfNeeded();
		target.click(new Locator.ClickOptions().setTimeout(timeoutMs));
		waitForUi(page);
	}

	private void assertAnyTextVisible(final Page page, final List<String> textCandidates, final int timeoutMs) {
		waitForFirstVisibleLocator(() -> buildTextCandidateLocators(page, textCandidates), timeoutMs);
	}

	private boolean isAnyTextVisible(final Page page, final List<String> textCandidates, final int timeoutMs) {
		try {
			waitForFirstVisibleLocator(() -> buildTextCandidateLocators(page, textCandidates), timeoutMs);
			return true;
		} catch (AssertionError ignored) {
			return false;
		}
	}

	private Locator waitForFirstVisibleLocator(final Supplier<List<Locator>> locatorSupplier, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (Locator locator : locatorSupplier.get()) {
				if (safeVisible(locator)) {
					return locator;
				}
			}
			try {
				Thread.sleep(250L);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		throw new AssertionError("Did not find a visible locator within " + timeoutMs + "ms.");
	}

	private List<Locator> buildTextCandidateLocators(final Page page, final List<String> texts) {
		final List<Locator> locators = new ArrayList<>();
		for (String text : texts) {
			final String escaped = escapeCssText(text);
			locators.add(page.locator("button:has-text(\"" + escaped + "\")").first());
			locators.add(page.locator("a:has-text(\"" + escaped + "\")").first());
			locators.add(page.locator("[role='button']:has-text(\"" + escaped + "\")").first());
			locators.add(page.locator("[role='menuitem']:has-text(\"" + escaped + "\")").first());
			locators.add(page.locator("[role='link']:has-text(\"" + escaped + "\")").first());
			locators.add(page.locator("text=\"" + escaped + "\"").first());
			locators.add(page.locator("text=" + escaped).first());
		}
		return locators;
	}

	private boolean isAnySelectorVisible(final Page page, final List<String> selectors, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (String selector : selectors) {
				if (safeVisible(page.locator(selector).first())) {
					return true;
				}
			}
			page.waitForTimeout(200);
		}
		return false;
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (PlaywrightException ignored) {
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000));
		} catch (PlaywrightException ignored) {
		}
		page.waitForTimeout(250);
	}

	private Page resolveAppPage(final BrowserContext context, final Page fallbackPage) {
		for (Page page : context.pages()) {
			if (page.isClosed()) {
				continue;
			}
			waitForUi(page);
			if (isAnyTextVisible(page, List.of("Mi Negocio", "Negocio"), 2000)) {
				return page;
			}
		}
		return fallbackPage;
	}

	private boolean runStep(final Map<String, String> statuses, final Map<String, String> details, final String field,
			final StepAction action) {
		try {
			action.run();
			statuses.put(field, "PASS");
			if ("Not executed.".equals(details.get(field))) {
				details.put(field, "Validated.");
			}
			return true;
		} catch (Throwable error) {
			statuses.put(field, "FAIL");
			appendDetail(details, field, "Error: " + cleanMessage(error));
			return false;
		}
	}

	private boolean blockStep(final Map<String, String> statuses, final Map<String, String> details, final String field,
			final String reason) {
		statuses.put(field, "FAIL");
		appendDetail(details, field, reason);
		return false;
	}

	private void initializeReport(final Map<String, String> statuses, final Map<String, String> details) {
		for (String field : REPORT_FIELDS) {
			statuses.put(field, "FAIL");
			details.put(field, "Not executed.");
		}
	}

	private Path writeFinalReport(final Path artifactsDir, final Map<String, String> statuses, final Map<String, String> details,
			final String loginUrl) throws IOException {
		final Path reportPath = artifactsDir.resolve("final-report.md");
		final StringBuilder report = new StringBuilder();
		report.append("# SaleADS Mi Negocio Workflow Report\n\n");
		report.append("- Timestamp: ").append(LocalDateTime.now()).append('\n');
		report.append("- Login URL: ").append(loginUrl).append('\n');
		report.append("- Rule: Environment agnostic URL (provided at runtime)\n\n");
		report.append("| Step | Result | Details |\n");
		report.append("|---|---|---|\n");

		for (String field : REPORT_FIELDS) {
			report.append("| ").append(field).append(" | ").append(statuses.get(field)).append(" | ")
					.append(details.get(field).replace('\n', ' ')).append(" |\n");
		}

		Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
		return reportPath;
	}

	private Path captureScreenshot(final Page page, final Path artifactsDir, final String checkpointName, final boolean fullPage)
			throws IOException {
		final Path path = artifactsDir.resolve(checkpointName + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
		return path;
	}

	private Path initArtifactsDirectory() throws IOException {
		final String configuredDir = trimToNull(readConfig("SALEADS_ARTIFACTS_DIR", "saleads.artifacts.dir"));
		final String baseDir = configuredDir == null ? "target/saleads-evidence" : configuredDir;
		final Path dir = Paths.get(baseDir).resolve("run-" + LocalDateTime.now().format(TS_FORMAT));
		Files.createDirectories(dir);
		return dir;
	}

	private static String readConfig(final String envName, final String propertyName) {
		String value = System.getenv(envName);
		if (value == null || value.isBlank()) {
			value = System.getProperty(propertyName);
		}
		return value;
	}

	private static String readConfig(final String envName, final String propertyName, final String defaultValue) {
		final String value = readConfig(envName, propertyName);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private static String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static boolean safeVisible(final Locator locator) {
		try {
			if (locator.count() == 0) {
				return false;
			}
			return locator.first().isVisible();
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private static String safeInnerText(final Locator locator) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
			return locator.first().innerText();
		} catch (PlaywrightException ignored) {
			return "";
		}
	}

	private static String escapeCssText(final String text) {
		return text.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static void appendDetail(final Map<String, String> details, final String field, final String detail) {
		final String previous = details.getOrDefault(field, "");
		if (previous == null || previous.isBlank() || "Not executed.".equals(previous)) {
			details.put(field, detail);
		} else {
			details.put(field, previous + " | " + detail);
		}
	}

	private static String cleanMessage(final Throwable error) {
		final String message = error.getMessage();
		return message == null || message.isBlank() ? error.getClass().getSimpleName() : message.replace('\n', ' ');
	}

	private static String slugify(final String value) {
		return value.toLowerCase().replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o")
				.replace("ú", "u").replace("ñ", "n").replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private interface StepAction {
		void run() throws Exception;
	}
}
