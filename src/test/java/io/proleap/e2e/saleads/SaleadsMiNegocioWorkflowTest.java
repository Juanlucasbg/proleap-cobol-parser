package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;

/**
 * End-to-end workflow validation for SaleADS "Mi Negocio".
 *
 * <p>
 * This test intentionally avoids hardcoded domains and expects the login page URL from an environment variable.
 * It records screenshots and a structured PASS/FAIL report at:
 * target/saleads-mi-negocio/&lt;timestamp&gt;
 * </p>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR = "Agregar Negocio modal";
	private static final String STEP_ADMIN = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_DETALLES = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Política de Privacidad";

	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private static final long UI_WAIT_MS = 1200L;
	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withLocale(Locale.ROOT).withZone(ZoneOffset.UTC);

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Instant startedAt = Instant.now();

	private Playwright playwright;
	private Browser browser;
	private Page appPage;
	private Path artifactDir;
	private String startUrl;

	@Before
	public void setUp() throws IOException {
		startUrl = getenv("SALEADS_START_URL");
		Assume.assumeTrue("SALEADS_START_URL must be set to the login page of the current SaleADS environment.",
				startUrl != null && !startUrl.isBlank());

		final String timestamp = TS_FORMATTER.format(startedAt);
		artifactDir = Paths.get("target", "saleads-mi-negocio", timestamp);
		Files.createDirectories(artifactDir);
		initializeReport();

		playwright = Playwright.create();
		final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
				.setHeadless(Boolean.parseBoolean(getenv("SALEADS_HEADLESS", "true")));

		browser = playwright.chromium().launch(launchOptions);
		appPage = browser.newContext().newPage();
		appPage.navigate(startUrl);
		waitForUi(appPage);
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeReports();
		} finally {
			if (browser != null) {
				browser.close();
			}
			if (playwright != null) {
				playwright.close();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		runStep(STEP_LOGIN, this::loginWithGoogleAndValidateSidebar);
		runStep(STEP_MENU, this::openMiNegocioMenu);
		runStep(STEP_AGREGAR, this::validateAgregarNegocioModal);
		runStep(STEP_ADMIN, this::openAdministrarNegocios);
		runStep(STEP_INFO_GENERAL, this::validateInformacionGeneralSection);
		runStep(STEP_DETALLES, this::validateDetallesCuentaSection);
		runStep(STEP_TUS_NEGOCIOS, this::validateTusNegociosSection);
		runStep(STEP_TERMINOS, () -> validateLegalDocument("Términos y Condiciones",
				Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"), "terminos-y-condiciones"));
		runStep(STEP_PRIVACIDAD, () -> validateLegalDocument("Política de Privacidad",
				Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"), "politica-de-privacidad"));

		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			if ("FAIL".equals(entry.getValue().status)) {
				failedSteps.add(entry.getKey());
			}
		}

		assertTrue("Workflow failed on steps: " + failedSteps + ". Check artifacts in: " + artifactDir, failedSteps.isEmpty());
	}

	private void loginWithGoogleAndValidateSidebar() {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google", "Google");
		waitForUi(appPage);
		trySelectGoogleAccount(GOOGLE_ACCOUNT);
		waitForUi(appPage);

		final boolean hasMainContainer = isVisible(appPage.locator("main"));
		final boolean hasSidebar = isVisible(appPage.getByRole(AriaRole.NAVIGATION)) || isTextVisible(Pattern.compile("(?i)negocio"));

		assertTrue("Main application interface did not appear after login.", hasMainContainer || hasSidebar);
		assertTrue("Left sidebar navigation is not visible after login.", hasSidebar);

		recordCheckpointScreenshot(STEP_LOGIN, appPage, false, "dashboard-loaded");
	}

	private void openMiNegocioMenu() {
		clickByVisibleText("Negocio");
		waitForUi(appPage);
		clickByVisibleText("Mi Negocio");
		waitForUi(appPage);

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		recordCheckpointScreenshot(STEP_MENU, appPage, false, "mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");
		waitForUi(appPage);

		assertTextMatchesVisible(Pattern.compile("(?i)crear\\s+nuevo\\s+negocio"));
		assertAnyLocatorVisible("Nombre del Negocio input", appPage.getByLabel("Nombre del Negocio"),
				appPage.getByPlaceholder("Nombre del Negocio"),
				appPage.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)nombre\\s+del\\s+negocio"))));
		assertTextMatchesVisible(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"));
		assertAnyLocatorVisible("Cancelar button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))));
		assertAnyLocatorVisible("Crear Negocio button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s+negocio"))));

		recordCheckpointScreenshot(STEP_AGREGAR, appPage, false, "agregar-negocio-modal");

		fillIfVisible("Nombre del Negocio", "Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
		waitForUi(appPage);
	}

	private void openAdministrarNegocios() {
		if (!isTextVisible(Pattern.compile("(?i)administrar\\s+negocios"))) {
			clickByVisibleText("Mi Negocio");
			waitForUi(appPage);
		}

		clickByVisibleText("Administrar Negocios");
		waitForUi(appPage);

		assertTextMatchesVisible(Pattern.compile("(?i)informaci[oó]n\\s+general"));
		assertTextMatchesVisible(Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta"));
		assertTextMatchesVisible(Pattern.compile("(?i)tus\\s+negocios"));
		assertTextMatchesVisible(Pattern.compile("(?i)secci[oó]n\\s+legal"));

		recordCheckpointScreenshot(STEP_ADMIN, appPage, true, "administrar-negocios-view");
	}

	private void validateInformacionGeneralSection() {
		assertTextMatchesVisible(Pattern.compile("(?i)informaci[oó]n\\s+general"));
		assertTextMatchesVisible(Pattern.compile("(?i)business\\s+plan"));
		assertAnyLocatorVisible("Cambiar Plan button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar\\s+plan"))));

		final String expectedName = getenv("SALEADS_EXPECTED_USER_NAME");
		if (expectedName != null && !expectedName.isBlank()) {
			assertTextVisible(expectedName);
		} else {
			assertTrue("User name is not visible in Información General.",
					isTextVisible(Pattern.compile("(?i)nombre")) || isTextVisible(Pattern.compile("(?i)bienvenido")));
		}

		final String expectedEmail = getenv("SALEADS_EXPECTED_USER_EMAIL", GOOGLE_ACCOUNT);
		assertTrue("User email is not visible in Información General.",
				isTextVisible(Pattern.compile(Pattern.quote(expectedEmail), Pattern.CASE_INSENSITIVE))
						|| isTextVisible(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")));
	}

	private void validateDetallesCuentaSection() {
		assertTextMatchesVisible(Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta"));
		assertTextMatchesVisible(Pattern.compile("(?i)cuenta\\s+creada"));
		assertTextMatchesVisible(Pattern.compile("(?i)estado\\s+activo"));
		assertTextMatchesVisible(Pattern.compile("(?i)idioma\\s+seleccionado"));
	}

	private void validateTusNegociosSection() {
		assertTextMatchesVisible(Pattern.compile("(?i)tus\\s+negocios"));
		assertAnyLocatorVisible("Agregar Negocio button in Tus Negocios",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s+negocio"))));
		assertTextMatchesVisible(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"));

		final boolean hasBusinessList = isVisible(appPage.locator("ul")) || isVisible(appPage.locator("table"))
				|| isTextVisible(Pattern.compile("(?i)negocio"));
		assertTrue("Business list is not visible in Tus Negocios.", hasBusinessList);
	}

	private void validateLegalDocument(final String linkText, final Pattern headingPattern, final String screenshotSlug) {
		final List<Page> previousPages = new ArrayList<>(appPage.context().pages());
		clickByVisibleText(linkText);
		waitForUi(appPage);

		Page legalPage = appPage;
		for (int i = 0; i < 8; i++) {
			final List<Page> currentPages = appPage.context().pages();
			for (final Page candidate : currentPages) {
				if (!previousPages.contains(candidate)) {
					legalPage = candidate;
					break;
				}
			}
			if (legalPage != appPage) {
				break;
			}
			appPage.waitForTimeout(400);
		}

		legalPage.bringToFront();
		waitForUi(legalPage);
		assertTextMatchesVisible(legalPage, headingPattern, "Legal heading is not visible for: " + linkText);

		final String legalBodyText = safeBodyText(legalPage);
		assertTrue("Legal content text is not visible for: " + linkText,
				legalBodyText != null && legalBodyText.trim().length() > 120);

		final String screenshot = screenshotName(screenshotSlug);
		takeScreenshot(legalPage, screenshot, true);

		final StepResult result = report.get(linkText);
		result.screenshot = screenshot;
		result.finalUrl = legalPage.url();

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}
	}

	private void runStep(final String stepName, final ThrowingRunnable action) {
		final StepResult result = report.get(stepName);
		try {
			action.run();
			result.status = "PASS";
			result.details = "All validations passed.";
		} catch (final Throwable t) {
			result.status = "FAIL";
			result.details = t.getClass().getSimpleName() + ": " + safeMessage(t);
			result.screenshot = screenshotName(slug(stepName) + "-failure");
			takeScreenshot(appPage, result.screenshot, true);
		}
	}

	private void initializeReport() {
		report.put(STEP_LOGIN, new StepResult());
		report.put(STEP_MENU, new StepResult());
		report.put(STEP_AGREGAR, new StepResult());
		report.put(STEP_ADMIN, new StepResult());
		report.put(STEP_INFO_GENERAL, new StepResult());
		report.put(STEP_DETALLES, new StepResult());
		report.put(STEP_TUS_NEGOCIOS, new StepResult());
		report.put(STEP_TERMINOS, new StepResult());
		report.put(STEP_PRIVACIDAD, new StepResult());
	}

	private void writeReports() throws IOException {
		if (artifactDir == null) {
			return;
		}

		final String json = buildJsonReport();
		Files.writeString(artifactDir.resolve("saleads-mi-negocio-report.json"), json, StandardCharsets.UTF_8);

		final String markdown = buildMarkdownSummary();
		Files.writeString(artifactDir.resolve("saleads-mi-negocio-report.md"), markdown, StandardCharsets.UTF_8);
	}

	private String buildJsonReport() {
		final StringBuilder out = new StringBuilder();
		out.append("{\n");
		out.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		out.append("  \"generatedAt\": \"").append(escapeJson(Instant.now().toString())).append("\",\n");
		out.append("  \"startUrl\": \"").append(escapeJson(startUrl)).append("\",\n");
		out.append("  \"artifactDirectory\": \"").append(escapeJson(artifactDir.toString())).append("\",\n");
		out.append("  \"results\": [\n");

		int index = 0;
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final StepResult value = entry.getValue();
			out.append("    {\n");
			out.append("      \"step\": \"").append(escapeJson(entry.getKey())).append("\",\n");
			out.append("      \"status\": \"").append(escapeJson(defaultString(value.status, "FAIL"))).append("\",\n");
			out.append("      \"details\": \"").append(escapeJson(defaultString(value.details, ""))).append("\",\n");
			out.append("      \"screenshot\": \"").append(escapeJson(defaultString(value.screenshot, ""))).append("\",\n");
			out.append("      \"finalUrl\": \"").append(escapeJson(defaultString(value.finalUrl, ""))).append("\"\n");
			out.append("    }");
			index++;
			if (index < report.size()) {
				out.append(",");
			}
			out.append("\n");
		}

		out.append("  ]\n");
		out.append("}\n");
		return out.toString();
	}

	private String buildMarkdownSummary() {
		final StringBuilder out = new StringBuilder();
		out.append("# SaleADS Mi Negocio Workflow Report\n\n");
		out.append("- Generated at: `").append(Instant.now()).append("`\n");
		out.append("- Start URL: `").append(startUrl).append("`\n");
		out.append("- Artifact directory: `").append(artifactDir).append("`\n\n");
		out.append("| Step | Status | Screenshot | URL |\n");
		out.append("| --- | --- | --- | --- |\n");

		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			final StepResult value = entry.getValue();
			out.append("| ").append(entry.getKey()).append(" | ")
					.append(defaultString(value.status, "FAIL")).append(" | ")
					.append(defaultString(value.screenshot, "-")).append(" | ")
					.append(defaultString(value.finalUrl, "-")).append(" |\n");
			if (value.details != null && !value.details.isBlank()) {
				out.append("\n  - ").append(entry.getKey()).append(": ").append(value.details).append("\n\n");
			}
		}
		return out.toString();
	}

	private void recordCheckpointScreenshot(final String stepName, final Page page, final boolean fullPage, final String label) {
		final StepResult result = report.get(stepName);
		final String screenshot = screenshotName(label);
		takeScreenshot(page, screenshot, fullPage);
		result.screenshot = screenshot;
	}

	private String screenshotName(final String label) {
		return TS_FORMATTER.format(Instant.now()) + "-" + slug(label) + ".png";
	}

	private String slug(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) {
		try {
			page.screenshot(new Page.ScreenshotOptions().setPath(artifactDir.resolve(fileName)).setFullPage(fullPage));
		} catch (final PlaywrightException ignored) {
			// best effort evidence only
		}
	}

	private void trySelectGoogleAccount(final String accountEmail) {
		final Pattern accountPattern = Pattern.compile("(?i)" + Pattern.quote(accountEmail));

		for (int attempt = 0; attempt < 5; attempt++) {
			for (final Page page : appPage.context().pages()) {
				if (page.isClosed()) {
					continue;
				}
				final Locator account = page.getByText(accountPattern);
				if (hasVisible(account)) {
					account.first().click();
					waitForUi(page);
				}
			}
			appPage.waitForTimeout(800);
		}

		appPage.bringToFront();
		waitForUi(appPage);
	}

	private void clickByVisibleText(final String... labels) {
		final Locator locator = firstVisibleLocatorForLabels(labels);
		locator.scrollIntoViewIfNeeded();
		locator.click();
		waitForUi(appPage);
	}

	private Locator firstVisibleLocatorForLabels(final String... labels) {
		final List<Locator> candidates = new ArrayList<>();
		for (final String label : labels) {
			final Pattern exactText = Pattern.compile("(?i)^\\s*" + Pattern.quote(label) + "\\s*$");
			final Pattern looseText = Pattern.compile("(?i)" + Pattern.quote(label));

			candidates.add(appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(exactText)));
			candidates.add(appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(exactText)));
			candidates.add(appPage.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(exactText)));
			candidates.add(appPage.getByText(exactText));
			candidates.add(appPage.getByText(looseText));
		}

		Locator fallback = null;
		for (final Locator candidate : candidates) {
			if (candidate.count() == 0) {
				continue;
			}

			final Locator first = candidate.first();
			if (fallback == null) {
				fallback = first;
			}
			if (isVisible(first)) {
				return first;
			}
		}

		if (fallback != null) {
			return fallback;
		}
		throw new AssertionError("Could not find an element for labels: " + String.join(", ", labels));
	}

	private void fillIfVisible(final String fieldLabel, final String value) {
		final Pattern fieldPattern = Pattern.compile("(?i)" + Pattern.quote(fieldLabel));
		final List<Locator> candidates = List.of(appPage.getByLabel(fieldLabel), appPage.getByPlaceholder(fieldLabel),
				appPage.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(fieldPattern)));
		for (final Locator candidate : candidates) {
			if (candidate.count() == 0) {
				continue;
			}
			final Locator first = candidate.first();
			if (isVisible(first)) {
				first.fill(value);
				waitForUi(appPage);
				return;
			}
		}
	}

	private void assertTextVisible(final String text) {
		assertTextMatchesVisible(Pattern.compile("(?i)" + Pattern.quote(text)));
	}

	private void assertTextMatchesVisible(final Pattern textPattern) {
		assertTextMatchesVisible(appPage, textPattern, "Expected text not visible: " + textPattern.pattern());
	}

	private void assertTextMatchesVisible(final Page page, final Pattern textPattern, final String message) {
		assertTrue(message, isTextVisible(page, textPattern));
	}

	private void assertAnyLocatorVisible(final String label, final Locator... locators) {
		for (final Locator locator : locators) {
			if (hasVisible(locator)) {
				return;
			}
		}
		throw new AssertionError("Expected visible element not found: " + label);
	}

	private boolean isTextVisible(final Pattern textPattern) {
		return isTextVisible(appPage, textPattern);
	}

	private boolean isTextVisible(final Page page, final Pattern textPattern) {
		final Locator locator = page.getByText(textPattern);
		if (locator.count() == 0) {
			return false;
		}

		final int checkCount = Math.min(locator.count(), 5);
		for (int i = 0; i < checkCount; i++) {
			if (isVisible(locator.nth(i))) {
				return true;
			}
		}
		return false;
	}

	private boolean hasVisible(final Locator locator) {
		if (locator.count() == 0) {
			return false;
		}

		final int checkCount = Math.min(locator.count(), 5);
		for (int i = 0; i < checkCount; i++) {
			if (isVisible(locator.nth(i))) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState();
		} catch (final PlaywrightException ignored) {
			// some UI clicks do not trigger navigation
		}

		try {
			page.waitForTimeout(UI_WAIT_MS);
		} catch (final PlaywrightException ignored) {
			// best-effort stabilization
		}
	}

	private String safeBodyText(final Page page) {
		try {
			return page.locator("body").innerText();
		} catch (final PlaywrightException ex) {
			return null;
		}
	}

	private String safeMessage(final Throwable throwable) {
		final String message = throwable.getMessage();
		return message == null ? "<no message>" : message;
	}

	private String defaultString(final String value, final String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	private String getenv(final String key) {
		return System.getenv(key);
	}

	private String getenv(final String key, final String fallback) {
		final String value = System.getenv(key);
		return value == null ? fallback : value;
	}

	private static class StepResult {
		private String status = "FAIL";
		private String details = "Step did not complete.";
		private String screenshot;
		private String finalUrl;
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
