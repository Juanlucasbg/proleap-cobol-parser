package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

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

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final List<String> REPORT_FIELDS = List.of(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	private final Map<String, Boolean> stepReport = new LinkedHashMap<>();
	private final Map<String, String> failureDetails = new LinkedHashMap<>();
	private String termsUrl = "N/A";
	private String privacyUrl = "N/A";

	private Path evidenceDir;
	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;

	@Before
	public void setUp() throws IOException {
		evidenceDir = Paths.get("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		final boolean enabled = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run SaleADS UI E2E tests.", enabled);

		playwright = Playwright.create();
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
		context.setDefaultTimeout(20_000);
		context.setDefaultNavigationTimeout(45_000);
		appPage = context.newPage();

		final String baseUrl = System.getenv("SALEADS_BASE_URL");
		if (baseUrl != null && !baseUrl.trim().isEmpty()) {
			appPage.navigate(baseUrl.trim());
			waitForUi(appPage);
		}

		Assume.assumeTrue(
				"SALEADS_BASE_URL must be provided when browser is not preloaded on the login page.",
				!"about:blank".equals(appPage.url()));
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReportFile();
		} finally {
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
	}

	@Test
	public void saleads_mi_negocio_full_test() throws IOException {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminos);
		runStep("Política de Privacidad", this::stepValidatePrivacidad);

		printFinalReport();

		final List<String> failedSteps = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			if (!stepReport.getOrDefault(field, false)) {
				failedSteps.add(field);
			}
		}

		Assert.assertTrue(
				"Workflow finished with failed validations: " + String.join(", ", failedSteps)
						+ ". Inspect evidence at " + evidenceDir.toAbsolutePath(),
				failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() throws IOException {
		if (!isMainInterfaceVisible(appPage)) {
			final Locator loginButton = firstVisible(
					appPage.getByText("Sign in with Google"),
					appPage.getByText("Iniciar sesión con Google"),
					appPage.getByText("Ingresar con Google"),
					appPage.locator("button:has-text(\"Google\"), [role='button']:has-text(\"Google\")"));
			assertCondition(loginButton != null, "Login button or Google sign-in trigger was not found.");
			clickAndWait(loginButton, "Login with Google");
			selectGoogleAccountIfVisible();
			waitForMainInterface();
		}

		assertCondition(isMainInterfaceVisible(appPage), "Main application interface did not appear after login.");
		assertCondition(isSidebarVisible(appPage), "Left sidebar navigation is not visible.");
		takeScreenshot(appPage, "01-dashboard-loaded", false);
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		assertCondition(isSidebarVisible(appPage), "Sidebar was not available before opening Mi Negocio.");
		clickIfVisible(appPage.getByText("Negocio"), "Negocio section");
		clickAndWait(appPage.getByText("Mi Negocio"), "Mi Negocio");

		assertTextVisible(appPage, "Agregar Negocio", "Expected 'Agregar Negocio' in expanded submenu.");
		assertTextVisible(appPage, "Administrar Negocios", "Expected 'Administrar Negocios' in expanded submenu.");
		takeScreenshot(appPage, "02-mi-negocio-expanded", false);
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		ensureMiNegocioExpanded();
		clickAndWait(appPage.getByText("Agregar Negocio"), "Agregar Negocio");

		assertTextVisible(appPage, "Crear Nuevo Negocio", "Modal title 'Crear Nuevo Negocio' was not visible.");
		assertTextVisible(appPage, "Nombre del Negocio", "Field 'Nombre del Negocio' was not visible.");
		assertTextVisible(appPage, "Tienes 2 de 3 negocios", "Quota text was not visible.");
		assertTextVisible(appPage, "Cancelar", "Button 'Cancelar' was not visible.");
		assertTextVisible(appPage, "Crear Negocio", "Button 'Crear Negocio' was not visible.");
		takeScreenshot(appPage, "03-agregar-negocio-modal", false);

		final Locator businessNameInput = firstVisible(
				appPage.getByLabel("Nombre del Negocio"),
				appPage.getByPlaceholder("Nombre del Negocio"),
				appPage.locator("input"));
		assertCondition(businessNameInput != null, "Could not locate input for 'Nombre del Negocio'.");
		businessNameInput.first().fill("Negocio Prueba Automatización");
		clickAndWait(appPage.getByText("Cancelar"), "Cancelar");
		assertCondition(!isVisible(appPage.getByText("Crear Nuevo Negocio")), "Modal did not close after clicking Cancelar.");
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureMiNegocioExpanded();
		clickAndWait(appPage.getByText("Administrar Negocios"), "Administrar Negocios");

		assertTextVisible(appPage, "Información General", "Section 'Información General' is missing.");
		assertTextVisible(appPage, "Detalles de la Cuenta", "Section 'Detalles de la Cuenta' is missing.");
		assertTextVisible(appPage, "Tus Negocios", "Section 'Tus Negocios' is missing.");
		assertTextVisible(appPage, "Sección Legal", "Section 'Sección Legal' is missing.");
		takeScreenshot(appPage, "04-administrar-negocios", true);
	}

	private void stepValidateInformacionGeneral() {
		final String bodyText = appPage.locator("body").innerText();
		assertCondition(EMAIL_PATTERN.matcher(bodyText).find(), "User email is not visible.");
		assertCondition(hasLikelyUserName(bodyText), "User name is not visible.");
		assertTextVisible(appPage, "BUSINESS PLAN", "Text 'BUSINESS PLAN' is missing.");
		assertTextVisible(appPage, "Cambiar Plan", "Button 'Cambiar Plan' is missing.");
	}

	private void stepValidateDetallesCuenta() {
		assertTextVisible(appPage, "Cuenta creada", "Text 'Cuenta creada' is missing.");
		assertTextVisible(appPage, "Estado activo", "Text 'Estado activo' is missing.");
		assertTextVisible(appPage, "Idioma seleccionado", "Text 'Idioma seleccionado' is missing.");
	}

	private void stepValidateTusNegocios() {
		assertTextVisible(appPage, "Tus Negocios", "Section 'Tus Negocios' is missing.");
		assertTextVisible(appPage, "Agregar Negocio", "Button 'Agregar Negocio' is missing in 'Tus Negocios'.");
		assertTextVisible(appPage, "Tienes 2 de 3 negocios", "Quota text is missing in 'Tus Negocios'.");

		final Locator section = sectionFromHeading("Tus Negocios");
		assertCondition(section != null, "Unable to scope 'Tus Negocios' section.");
		final int listItems = section.locator("li, [role='listitem'], tbody tr").count();
		final int textBlocks = section.locator("p, h3, h4, span, div").count();
		assertCondition(listItems > 0 || textBlocks > 10, "Business list is not visible.");
	}

	private void stepValidateTerminos() throws IOException {
		final LegalResult legalResult = openLegalPage("Términos y Condiciones", "Términos y Condiciones", "05-terminos");
		termsUrl = legalResult.url;
	}

	private void stepValidatePrivacidad() throws IOException {
		final LegalResult legalResult = openLegalPage("Política de Privacidad", "Política de Privacidad", "06-privacidad");
		privacyUrl = legalResult.url;
	}

	private LegalResult openLegalPage(final String linkText, final String headingText, final String screenshotName)
			throws IOException {
		assertTextVisible(appPage, "Sección Legal", "Could not find legal section before opening " + linkText + ".");
		final Locator legalLink = appPage.getByText(linkText);
		assertCondition(isVisible(legalLink), "Could not find link: " + linkText);
		legalLink.first().scrollIntoViewIfNeeded();

		final int beforePageCount = context.pages().size();
		final String previousUrl = appPage.url();
		clickAndWait(legalLink, linkText);

		Page legalPage = appPage;
		final long deadline = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < deadline) {
			if (context.pages().size() > beforePageCount) {
				legalPage = context.pages().get(context.pages().size() - 1);
				break;
			}
			if (!previousUrl.equals(appPage.url())) {
				legalPage = appPage;
				break;
			}
			appPage.waitForTimeout(200);
		}

		waitForUi(legalPage);
		assertTextVisible(legalPage, headingText, "Heading '" + headingText + "' was not found.");
		final String bodyText = legalPage.locator("body").innerText();
		assertCondition(bodyText != null && bodyText.trim().length() > 120, "Legal content text is not visible.");
		takeScreenshot(legalPage, screenshotName, true);

		final String finalUrl = legalPage.url();
		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!"about:blank".equals(previousUrl)) {
			try {
				appPage.goBack();
				waitForUi(appPage);
			} catch (final TimeoutError ignored) {
				// Keep current page if browser history is not available.
			}
		}
		return new LegalResult(finalUrl);
	}

	private void runStep(final String reportField, final StepAction action) throws IOException {
		try {
			action.run();
			stepReport.put(reportField, true);
		} catch (final Throwable throwable) {
			stepReport.put(reportField, false);
			failureDetails.put(reportField, safeMessage(throwable));
			takeScreenshot(appPage, "failure-" + sanitize(reportField), true);
		}
	}

	private void waitForMainInterface() {
		final long deadline = System.currentTimeMillis() + 120_000;
		while (System.currentTimeMillis() < deadline) {
			for (final Page page : context.pages()) {
				if (isMainInterfaceVisible(page)) {
					appPage = page;
					page.bringToFront();
					waitForUi(page);
					return;
				}
			}
			appPage.waitForTimeout(500);
		}
		throw new AssertionError("Main interface was not detected after Google login.");
	}

	private void selectGoogleAccountIfVisible() {
		final long deadline = System.currentTimeMillis() + 30_000;
		while (System.currentTimeMillis() < deadline) {
			for (final Page page : context.pages()) {
				final Locator accountOption = page.getByText(GOOGLE_ACCOUNT);
				if (isVisible(accountOption)) {
					accountOption.first().click();
					waitForUi(page);
					return;
				}
			}
			appPage.waitForTimeout(400);
		}
	}

	private void ensureMiNegocioExpanded() {
		if (!isVisible(appPage.getByText("Administrar Negocios")) || !isVisible(appPage.getByText("Agregar Negocio"))) {
			clickIfVisible(appPage.getByText("Negocio"), "Negocio section");
			clickIfVisible(appPage.getByText("Mi Negocio"), "Mi Negocio");
		}
		assertCondition(isVisible(appPage.getByText("Administrar Negocios")),
				"'Mi Negocio' submenu did not expand correctly.");
	}

	private void clickAndWait(final Locator locator, final String actionName) {
		assertCondition(locator != null, "Locator for action '" + actionName + "' was null.");
		waitVisible(locator, 20_000, "Element for action '" + actionName + "' was not visible.");
		locator.first().click();
		waitForUi(appPage);
	}

	private void clickIfVisible(final Locator locator, final String actionName) {
		if (isVisible(locator)) {
			clickAndWait(locator, actionName);
		}
	}

	private void waitVisible(final Locator locator, final int timeoutMs, final String failureMessage) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout((double) timeoutMs));
		} catch (final RuntimeException exception) {
			throw new AssertionError(failureMessage, exception);
		}
	}

	private void assertTextVisible(final Page page, final String text, final String failureMessage) {
		waitVisible(page.getByText(text), 20_000, failureMessage);
	}

	private void assertCondition(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator != null
					&& locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout(1_000));
		} catch (final RuntimeException ignored) {
			return false;
		}
	}

	private boolean isMainInterfaceVisible(final Page page) {
		return isVisible(page.getByText("Negocio")) || isVisible(page.getByText("Mi Negocio"));
	}

	private boolean isSidebarVisible(final Page page) {
		return isVisible(page.locator("aside")) || isVisible(page.locator("nav")) || isVisible(page.getByText("Negocio"));
	}

	private Locator firstVisible(final Locator... candidates) {
		for (final Locator locator : candidates) {
			if (isVisible(locator)) {
				return locator;
			}
		}
		return null;
	}

	private Locator sectionFromHeading(final String heading) {
		final Locator header = appPage.getByText(heading);
		if (!isVisible(header)) {
			return null;
		}
		return header.first().locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
	}

	private boolean hasLikelyUserName(final String bodyText) {
		final String[] lines = bodyText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.length() < 4 || line.length() > 80) {
				continue;
			}
			if (line.contains("@")) {
				continue;
			}
			final String normalized = line.toLowerCase();
			if (normalized.contains("información general")
					|| normalized.contains("business plan")
					|| normalized.contains("cambiar plan")
					|| normalized.contains("detalles de la cuenta")
					|| normalized.contains("tus negocios")
					|| normalized.contains("sección legal")) {
				continue;
			}
			if (line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15_000));
		} catch (final RuntimeException ignored) {
			// Keep moving if DOMCONTENTLOADED was already reached.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7_000));
		} catch (final RuntimeException ignored) {
			// Some SPAs keep long-lived requests; best-effort wait only.
		}
		page.waitForTimeout(400);
	}

	private void takeScreenshot(final Page page, final String name, final boolean fullPage) throws IOException {
		if (page == null || page.isClosed()) {
			return;
		}
		final Path destination = evidenceDir.resolve(sanitize(name) + ".png");
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(destination)
				.setFullPage(fullPage));
	}

	private void writeFinalReportFile() throws IOException {
		if (evidenceDir == null) {
			return;
		}
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio Full Workflow Report\n");
		reportBuilder.append("======================================\n\n");
		for (final String field : REPORT_FIELDS) {
			final boolean pass = stepReport.getOrDefault(field, false);
			reportBuilder.append(field).append(": ").append(pass ? "PASS" : "FAIL");
			final String detail = failureDetails.get(field);
			if (detail != null) {
				reportBuilder.append(" - ").append(detail);
			}
			reportBuilder.append('\n');
		}
		reportBuilder.append('\n');
		reportBuilder.append("Terminos URL: ").append(termsUrl).append('\n');
		reportBuilder.append("Privacidad URL: ").append(privacyUrl).append('\n');
		reportBuilder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		Files.write(evidenceDir.resolve("final-report.txt"),
				reportBuilder.toString().getBytes(StandardCharsets.UTF_8));
	}

	private void printFinalReport() {
		final StringBuilder output = new StringBuilder("\nFinal validation report:\n");
		for (final String field : REPORT_FIELDS) {
			final boolean pass = stepReport.getOrDefault(field, false);
			output.append(" - ").append(field).append(": ").append(pass ? "PASS" : "FAIL");
			if (!pass && failureDetails.containsKey(field)) {
				output.append(" (").append(failureDetails.get(field)).append(')');
			}
			output.append('\n');
		}
		output.append(" - Términos URL: ").append(termsUrl).append('\n');
		output.append(" - Privacidad URL: ").append(privacyUrl).append('\n');
		output.append(" - Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		System.out.println(output);
	}

	private String sanitize(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String safeMessage(final Throwable throwable) {
		final String message = throwable.getMessage();
		if (message == null || message.trim().isEmpty()) {
			return throwable.getClass().getSimpleName();
		}
		return message.replace('\n', ' ').trim();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private static class LegalResult {
		private final String url;

		private LegalResult(final String url) {
			this.url = url;
		}
	}
}
