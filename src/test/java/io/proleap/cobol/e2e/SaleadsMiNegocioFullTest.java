package io.proleap.cobol.e2e;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

public class SaleadsMiNegocioFullTest {

	private static final double DEFAULT_TIMEOUT_MS = 15000d;
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", Pattern.UNICODE_CASE);
	private static final List<String> REPORT_FIELDS = List.of("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final Path evidenceDir = prepareEvidenceDirectory();
		final Path reportPath = evidenceDir.resolve("final-report.md");
		final Map<String, Boolean> report = initializeReport();
		final List<String> failures = new ArrayList<>();
		final String[] legalUrls = new String[] { "N/A", "N/A" };

		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium()
						.launch(new BrowserType.LaunchOptions().setHeadless(resolveHeadless()));
				BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
				Page page = context.newPage()) {

			final String loginUrl = resolveLoginUrl();
			if (loginUrl != null) {
				page.navigate(loginUrl);
				waitForUiLoad(page);
			}

			runStep("Login", page, evidenceDir, report, failures, () -> {
				assertLoginPageAvailable(page, loginUrl);
				loginWithGoogle(page, context);
				assertMainInterfaceVisible(page);
				captureScreenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), false);
			});

			runStep("Mi Negocio menu", page, evidenceDir, report, failures, () -> {
				openMiNegocioMenu(page);
				assertTextVisible(page, "Agregar Negocio");
				assertTextVisible(page, "Administrar Negocios");
				captureScreenshot(page, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
			});

			runStep("Agregar Negocio modal", page, evidenceDir, report, failures, () -> {
				clickByVisibleText(page, "Agregar Negocio", true);
				assertTextVisible(page, "Crear Nuevo Negocio");
				assertBusinessNameInputExists(page);
				assertTextVisible(page, "Tienes 2 de 3 negocios");
				assertTextVisible(page, "Cancelar");
				assertTextVisible(page, "Crear Negocio");

				fillBusinessNameIfVisible(page, "Negocio Prueba Automatización");
				captureScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);
				clickByVisibleText(page, "Cancelar", true);
			});

			runStep("Administrar Negocios view", page, evidenceDir, report, failures, () -> {
				if (!isTextVisible(page, "Administrar Negocios", 2500d)) {
					openMiNegocioMenu(page);
				}

				clickByVisibleText(page, "Administrar Negocios", true);
				assertTextVisible(page, "Información General");
				assertTextVisible(page, "Detalles de la Cuenta");
				assertTextVisible(page, "Tus Negocios");
				assertTextVisible(page, "Sección Legal");
				captureScreenshot(page, evidenceDir.resolve("04-administrar-negocios.png"), true);
			});

			runStep("Información General", page, evidenceDir, report, failures, () -> {
				assertAnyEmailVisible(page);
				assertTrue("Expected user name to be visible in account summary.",
						isAnyTextVisible(page, List.of("Nombre", "Usuario", "Perfil"), 5000d) || isAnyHeadingVisible(page));
				assertTextVisible(page, "BUSINESS PLAN");
				assertTextVisible(page, "Cambiar Plan");
			});

			runStep("Detalles de la Cuenta", page, evidenceDir, report, failures, () -> {
				assertTextVisible(page, "Cuenta creada");
				assertTextVisible(page, "Estado activo");
				assertTextVisible(page, "Idioma seleccionado");
			});

			runStep("Tus Negocios", page, evidenceDir, report, failures, () -> {
				assertTextVisible(page, "Tus Negocios");
				assertTextVisible(page, "Agregar Negocio");
				assertTextVisible(page, "Tienes 2 de 3 negocios");
			});

			runStep("Términos y Condiciones", page, evidenceDir, report, failures, () -> {
				legalUrls[0] = openLegalLinkAndReturn(page, context, "Términos y Condiciones", "Términos y Condiciones",
						evidenceDir.resolve("08-terminos-y-condiciones.png"));
			});

			runStep("Política de Privacidad", page, evidenceDir, report, failures, () -> {
				legalUrls[1] = openLegalLinkAndReturn(page, context, "Política de Privacidad", "Política de Privacidad",
						evidenceDir.resolve("09-politica-de-privacidad.png"));
			});
		} finally {
			writeFinalReport(reportPath, report, legalUrls[0], legalUrls[1], failures);
		}

		assertTrue("SaleADS Mi Negocio workflow has failing validations. Review report: " + reportPath,
				failures.isEmpty());
	}

	private void loginWithGoogle(final Page page, final BrowserContext context) {
		Page activePage = page;

		try {
			activePage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(6000d),
					() -> clickGoogleSignInButton(page));
		} catch (final PlaywrightException popupNotOpened) {
			clickGoogleSignInButton(page);
		}

		waitForUiLoad(activePage);
		selectGoogleAccountIfVisible(activePage, "juanlucasbarbiergarzon@gmail.com");

		if (activePage != page) {
			waitForUiLoad(page);
		}
	}

	private void clickGoogleSignInButton(final Page page) {
		if (clickByAnyVisibleText(page, List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Google"), true)) {
			return;
		}

		throw new AssertionError("Could not find a Google login button.");
	}

	private void selectGoogleAccountIfVisible(final Page page, final String email) {
		clickByAnyVisibleText(page, List.of(email), true);
	}

	private void assertMainInterfaceVisible(final Page page) {
		assertTrue("Expected main application interface after login.",
				isAnyTextVisible(page, List.of("Dashboard", "Panel", "Negocio", "Mi Negocio"), DEFAULT_TIMEOUT_MS));

		final Locator sidebar = page.locator("aside").first();
		final boolean sidebarVisible = isLocatorVisible(sidebar, 5000d)
				|| isAnyTextVisible(page, List.of("Negocio", "Mi Negocio"), DEFAULT_TIMEOUT_MS);
		assertTrue("Expected left sidebar navigation to be visible.", sidebarVisible);
	}

	private void openMiNegocioMenu(final Page page) {
		if (!isTextVisible(page, "Mi Negocio", 2500d)) {
			clickByVisibleText(page, "Negocio", true);
		}

		clickByVisibleText(page, "Mi Negocio", true);
	}

	private String openLegalLinkAndReturn(final Page appPage, final BrowserContext context, final String linkText,
			final String expectedHeading, final Path screenshotPath) throws IOException {
		Page legalPage = appPage;
		boolean openedNewTab = false;

		try {
			legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(6000d),
					() -> clickByVisibleText(appPage, linkText, false));
			openedNewTab = true;
		} catch (final PlaywrightException noPopupDetected) {
			clickByVisibleText(appPage, linkText, true);
		}

		waitForUiLoad(legalPage);
		assertTextVisible(legalPage, expectedHeading);
		assertLegalContentVisible(legalPage);
		captureScreenshot(legalPage, screenshotPath, false);

		final String finalUrl = legalPage.url();
		assertNotNull("Expected legal page final URL.", finalUrl);

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else {
			appPage.goBack(new Page.GoBackOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			waitForUiLoad(appPage);
		}

		return finalUrl;
	}

	private void assertLegalContentVisible(final Page page) {
		final String bodyText = page.textContent("body");
		assertNotNull("Expected legal page to include content.", bodyText);
		assertTrue("Expected visible legal content text.", bodyText.trim().length() > 120);
	}

	private void fillBusinessNameIfVisible(final Page page, final String businessName) {
		final Pattern labelPattern = caseInsensitivePattern("Nombre del Negocio");
		Locator input = page.getByLabel(labelPattern);

		if (!isLocatorVisible(input, 2500d)) {
			input = page.getByPlaceholder("Nombre del Negocio");
		}

		if (isLocatorVisible(input, 2500d)) {
			input.first().fill(businessName);
		}
	}

	private void assertBusinessNameInputExists(final Page page) {
		final Pattern labelPattern = caseInsensitivePattern("Nombre del Negocio");
		final boolean visible = isLocatorVisible(page.getByLabel(labelPattern), 3000d)
				|| isLocatorVisible(page.getByPlaceholder("Nombre del Negocio"), 3000d);
		assertTrue("Expected input field 'Nombre del Negocio'.", visible);
	}

	private void assertAnyEmailVisible(final Page page) {
		final String bodyText = page.textContent("body");
		assertNotNull("Expected account page body text.", bodyText);
		assertTrue("Expected user email to be visible.", EMAIL_PATTERN.matcher(bodyText).find());
	}

	private boolean isAnyHeadingVisible(final Page page) {
		return isLocatorVisible(page.locator("h1, h2, h3, h4, h5, h6").first(), 2500d);
	}

	private void clickByVisibleText(final Page page, final String text, final boolean waitAfterClick) {
		final boolean clicked = clickByAnyVisibleText(page, List.of(text), waitAfterClick);
		assertTrue("Could not find clickable visible text: " + text, clicked);
	}

	private boolean clickByAnyVisibleText(final Page page, final List<String> labels, final boolean waitAfterClick) {
		for (final String label : labels) {
			final Pattern namePattern = caseInsensitivePattern(label);
			final List<Locator> candidates = List.of(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(namePattern)).first(),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(namePattern)).first(),
					page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(namePattern)).first(),
					page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(namePattern)).first(),
					page.getByText(namePattern).first());

			for (final Locator candidate : candidates) {
				if (isLocatorVisible(candidate, 2000d)) {
					candidate.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));

					if (waitAfterClick) {
						waitForUiLoad(page);
					}

					return true;
				}
			}
		}

		return false;
	}

	private void assertTextVisible(final Page page, final String text) {
		assertTrue("Expected visible text: " + text, isTextVisible(page, text, DEFAULT_TIMEOUT_MS));
	}

	private boolean isTextVisible(final Page page, final String text, final double timeoutMs) {
		return isAnyTextVisible(page, List.of(text), timeoutMs);
	}

	private boolean isAnyTextVisible(final Page page, final List<String> texts, final double timeoutMs) {
		for (final String text : texts) {
			final Pattern namePattern = caseInsensitivePattern(text);
			final List<Locator> candidates = List.of(
					page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(namePattern)).first(),
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(namePattern)).first(),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(namePattern)).first(),
					page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(namePattern)).first(),
					page.getByText(namePattern).first());

			for (final Locator candidate : candidates) {
				if (isLocatorVisible(candidate, timeoutMs)) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean isLocatorVisible(final Locator locator, final double timeoutMs) {
		try {
			return locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void captureScreenshot(final Page page, final Path screenshotPath, final boolean fullPage) throws IOException {
		Files.createDirectories(screenshotPath.getParent());
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000d));
		} catch (final PlaywrightException ignored) {
			// DOM content may already be stable.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000d));
		} catch (final PlaywrightException ignored) {
			// Network idle is best-effort for SPA traffic.
		}
	}

	private Path prepareEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDir = Path.of("target", "saleads-mi-negocio-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private Map<String, Boolean> initializeReport() {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		for (final String field : REPORT_FIELDS) {
			report.put(field, Boolean.FALSE);
		}
		return report;
	}

	private void writeFinalReport(final Path reportPath, final Map<String, Boolean> report, final String termsUrl,
			final String privacyUrl, final List<String> failures) throws IOException {
		final StringBuilder content = new StringBuilder();
		content.append("# SaleADS Mi Negocio Full Test Report\n\n");
		content.append("## Step Results\n\n");
		content.append("| Step | Result |\n");
		content.append("| --- | --- |\n");

		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			content.append("| ").append(entry.getKey()).append(" | ").append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL")
					.append(" |\n");
		}

		content.append("\n## Legal URLs\n\n");
		content.append("- Términos y Condiciones: ").append(termsUrl).append('\n');
		content.append("- Política de Privacidad: ").append(privacyUrl).append('\n');

		if (!failures.isEmpty()) {
			content.append("\n## Failures\n\n");
			for (final String failure : failures) {
				content.append("- ").append(failure).append('\n');
			}
		}

		Files.writeString(reportPath, content.toString());
	}

	private boolean resolveHeadless() {
		return Boolean.parseBoolean(System.getProperty("saleads.headless",
				System.getenv().getOrDefault("SALEADS_HEADLESS", "true")));
	}

	private void assertLoginPageAvailable(final Page page, final String loginUrl) {
		if (loginUrl != null) {
			return;
		}

		final String currentUrl = page.url();
		if (currentUrl == null || currentUrl.isBlank() || currentUrl.startsWith("about:blank")) {
			throw new AssertionError(
					"Missing SaleADS login URL/session. Set -Dsaleads.login.url or SALEADS_LOGIN_URL.");
		}
	}

	private String resolveLoginUrl() {
		final String fromProperty = System.getProperty("saleads.login.url");
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty.trim();
		}

		final String fromEnvironment = System.getenv("SALEADS_LOGIN_URL");
		if (fromEnvironment != null && !fromEnvironment.isBlank()) {
			return fromEnvironment.trim();
		}

		return null;
	}

	private Pattern caseInsensitivePattern(final String literalText) {
		return Pattern.compile(Pattern.quote(literalText), Pattern.CASE_INSENSITIVE);
	}

	private void runStep(final String stepName, final Page page, final Path evidenceDir, final Map<String, Boolean> report,
			final List<String> failures, final StepAction action) {
		try {
			action.run();
			report.put(stepName, Boolean.TRUE);
		} catch (final Throwable stepError) {
			report.put(stepName, Boolean.FALSE);
			try {
				captureScreenshot(page, evidenceDir.resolve("failure-" + slugify(stepName) + ".png"), true);
			} catch (final IOException ignored) {
				// Failure evidence screenshot is best effort.
			}
			final String message = stepError.getMessage() == null ? stepError.getClass().getSimpleName()
					: stepError.getMessage();
			failures.add(stepName + ": " + message);
		}
	}

	private String slugify(final String text) {
		return text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
