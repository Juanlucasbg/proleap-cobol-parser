package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

/**
 * E2E test for SaleADS.ai "Mi Negocio" workflow.
 *
 * <p>
 * This test is disabled by default to avoid affecting the regular parser test
 * suite. Enable it by setting -Dsaleads.run.e2e=true and providing
 * -Dsaleads.login.url (or SALEADS_LOGIN_URL).
 * </p>
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final long ACTION_TIMEOUT_MS = longProperty("saleads.timeout.ms", "SALEADS_TIMEOUT_MS", 30000L);
	private static final boolean HEADLESS = boolProperty("saleads.headless", "SALEADS_HEADLESS", true);
	private static final String RUN_FOLDER_PREFIX = "saleads-mi-negocio-";

	private static final String FIELD_LOGIN = "Login";
	private static final String FIELD_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String FIELD_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String FIELD_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String FIELD_INFORMACION_GENERAL = "Información General";
	private static final String FIELD_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String FIELD_TUS_NEGOCIOS = "Tus Negocios";
	private static final String FIELD_TERMINOS = "Términos y Condiciones";
	private static final String FIELD_PRIVACIDAD = "Política de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		Assume.assumeTrue("Enable with -Dsaleads.run.e2e=true or SALEADS_RUN_E2E=true",
				boolProperty("saleads.run.e2e", "SALEADS_RUN_E2E", false));

		final String loginUrl = stringProperty("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue("Provide login URL with -Dsaleads.login.url or SALEADS_LOGIN_URL",
				loginUrl != null && !loginUrl.isBlank());

		final Path runDir = createRunDir();
		final Path reportPath = runDir.resolve("final-report.txt");
		final Map<String, StepResult> results = initResultsMap();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new Browser.LaunchOptions().setHeadless(HEADLESS));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
			final Page page = context.newPage();
			page.setDefaultTimeout(ACTION_TIMEOUT_MS);

			page.navigate(loginUrl);
			waitForUiLoad(page);

			runStep(FIELD_LOGIN, results, () -> {
				executeLogin(page);
				assertMainInterfaceVisible(page);
				capture(page, runDir, "01-dashboard-loaded", true);
				return "Dashboard loaded and sidebar visible.";
			});

			runStep(FIELD_MI_NEGOCIO_MENU, results, () -> {
				expandMiNegocioMenu(page);
				assertTextVisible(page, "Agregar Negocio", ACTION_TIMEOUT_MS);
				assertTextVisible(page, "Administrar Negocios", ACTION_TIMEOUT_MS);
				capture(page, runDir, "02-mi-negocio-expanded", false);
				return "Mi Negocio expanded with submenu options visible.";
			});

			runStep(FIELD_AGREGAR_NEGOCIO_MODAL, results, () -> {
				clickByText(page, "Agregar Negocio");
				assertTextVisible(page, "Crear Nuevo Negocio", ACTION_TIMEOUT_MS);
				assertBusinessNameInputVisible(page);
				assertTextVisible(page, "Tienes 2 de 3 negocios", ACTION_TIMEOUT_MS);
				assertTextVisible(page, "Cancelar", ACTION_TIMEOUT_MS);
				assertTextVisible(page, "Crear Negocio", ACTION_TIMEOUT_MS);
				capture(page, runDir, "03-agregar-negocio-modal", false);

				final Locator nameField = findBusinessNameInput(page);
				if (nameField != null) {
					nameField.click();
					nameField.fill("Negocio Prueba Automatizacion");
				}
				clickByText(page, "Cancelar");
				waitForUiLoad(page);
				return "Agregar Negocio modal validated and closed.";
			});

			runStep(FIELD_ADMINISTRAR_NEGOCIOS, results, () -> {
				expandMiNegocioMenu(page);
				clickByText(page, "Administrar Negocios");
				waitForUiLoad(page);

				assertTextVisible(page, "Información General", ACTION_TIMEOUT_MS);
				assertTextVisible(page, "Detalles de la Cuenta", ACTION_TIMEOUT_MS);
				assertTextVisible(page, "Tus Negocios", ACTION_TIMEOUT_MS);
				assertTextVisible(page, "Sección Legal", ACTION_TIMEOUT_MS);
				capture(page, runDir, "04-administrar-negocios-page", true);
				return "Administrar Negocios sections are visible.";
			});

			runStep(FIELD_INFORMACION_GENERAL, results, () -> {
				assertEmailVisible(page);
				assertTextVisible(page, "BUSINESS PLAN", ACTION_TIMEOUT_MS);
				assertTextVisible(page, "Cambiar Plan", ACTION_TIMEOUT_MS);
				assertUserNameLikelyVisible(page);
				return "Información General validated (name, email, plan and action button).";
			});

			runStep(FIELD_DETALLES_CUENTA, results, () -> {
				assertTextVisible(page, "Cuenta creada", ACTION_TIMEOUT_MS);
				assertTextVisible(page, "Estado activo", ACTION_TIMEOUT_MS);
				assertTextVisible(page, "Idioma seleccionado", ACTION_TIMEOUT_MS);
				return "Detalles de la Cuenta validated.";
			});

			runStep(FIELD_TUS_NEGOCIOS, results, () -> {
				assertTextVisible(page, "Tus Negocios", ACTION_TIMEOUT_MS);
				assertTextVisible(page, "Agregar Negocio", ACTION_TIMEOUT_MS);
				assertTextVisible(page, "Tienes 2 de 3 negocios", ACTION_TIMEOUT_MS);
				return "Tus Negocios validated.";
			});

			runStep(FIELD_TERMINOS, results, () -> validateLegalLink(page, runDir, "Términos y Condiciones",
					Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"), "05-terminos-y-condiciones"));

			runStep(FIELD_PRIVACIDAD, results, () -> validateLegalLink(page, runDir, "Política de Privacidad",
					Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"), "06-politica-de-privacidad"));
		} finally {
			writeReport(reportPath, results);
		}

		assertTrue("SaleADS Mi Negocio workflow failed.\n" + renderReport(results), allPassed(results));
	}

	private void executeLogin(final Page appPage) {
		Page activePage = appPage;
		try {
			activePage = appPage.context().waitForPage(() -> clickGoogleSignIn(appPage),
					new BrowserContext.WaitForPageOptions().setTimeout(6000));
			waitForUiLoad(activePage);
		} catch (final PlaywrightException popupNotOpened) {
			clickGoogleSignIn(appPage);
			activePage = appPage;
		}

		selectGoogleAccountIfVisible(activePage);

		if (activePage != appPage) {
			try {
				activePage.waitForClose(new Page.WaitForCloseOptions().setTimeout(60000));
			} catch (final PlaywrightException ignored) {
				// Some environments keep auth tab open. Continue with app tab checks.
			}
			appPage.bringToFront();
		}
		waitForUiLoad(appPage);
	}

	private void clickGoogleSignIn(final Page page) {
		final Pattern signInPattern = Pattern.compile("(?i)(sign\\s*in|iniciar\\s*sesi[oó]n|continuar).*google");
		final List<Locator> candidates = new ArrayList<>();
		candidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(signInPattern)));
		candidates.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(signInPattern)));
		candidates.add(page.getByText(Pattern.compile("(?i)sign\\s*in\\s*with\\s*google")));
		candidates.add(page.getByText(Pattern.compile("(?i)iniciar\\s*sesi[oó]n\\s*con\\s*google")));
		candidates.add(page.getByText(Pattern.compile("(?i)continuar\\s*con\\s*google")));
		candidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)google"))));

		final Locator loginButton = waitForVisible(page, candidates, ACTION_TIMEOUT_MS,
				"Google login button");
		loginButton.click();
		waitForUiLoad(page);
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		final Pattern accountPattern = Pattern.compile("(?i)^\\s*" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + "\\s*$");
		final List<Locator> candidates = new ArrayList<>();
		candidates.add(page.getByText(accountPattern));
		candidates.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(accountPattern)));
		candidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(accountPattern)));

		final Locator account = findVisible(page, candidates, 8000);
		if (account != null) {
			account.click();
			waitForUiLoad(page);
		}
	}

	private void assertMainInterfaceVisible(final Page page) {
		final List<Locator> sidebarCandidates = new ArrayList<>();
		sidebarCandidates.add(page.locator("aside"));
		sidebarCandidates.add(page.locator("nav"));
		sidebarCandidates.add(page.getByText(Pattern.compile("(?i)^\\s*Negocio\\s*$")));
		sidebarCandidates.add(page.getByText(Pattern.compile("(?i)^\\s*Mi Negocio\\s*$")));

		final Locator sidebar = waitForVisible(page, sidebarCandidates, ACTION_TIMEOUT_MS,
				"main application sidebar");
		assertTrue("Sidebar is not visible after login.", sidebar.isVisible());
	}

	private void expandMiNegocioMenu(final Page page) {
		if (isTextVisible(page, "Agregar Negocio", 2000) && isTextVisible(page, "Administrar Negocios", 2000)) {
			return;
		}

		clickIfVisible(page, "Negocio", 2500);
		clickByText(page, "Mi Negocio");
		waitForUiLoad(page);
	}

	private String validateLegalLink(final Page appPage, final Path runDir, final String linkLabel, final Pattern heading,
			final String screenshotName) throws IOException {
		final String previousUrl = appPage.url();
		Page targetPage = appPage;
		boolean openedNewTab = false;

		try {
			targetPage = appPage.context().waitForPage(() -> clickByText(appPage, linkLabel),
					new BrowserContext.WaitForPageOptions().setTimeout(7000));
			openedNewTab = true;
		} catch (final PlaywrightException noNewTab) {
			clickByText(appPage, linkLabel);
			targetPage = appPage;
		}

		waitForUiLoad(targetPage);
		assertPatternVisible(targetPage, heading, ACTION_TIMEOUT_MS, "Legal heading: " + heading.pattern());
		assertLegalTextVisible(targetPage);

		capture(targetPage, runDir, screenshotName, true);
		final String finalUrl = targetPage.url();

		if (openedNewTab) {
			targetPage.close();
			appPage.bringToFront();
		} else if (!previousUrl.equals(appPage.url())) {
			appPage.goBack();
			waitForUiLoad(appPage);
		}

		return "PASS - final URL: " + finalUrl;
	}

	private void assertLegalTextVisible(final Page page) {
		final String bodyText = page.locator("body").innerText();
		if (bodyText == null || bodyText.trim().length() < 120) {
			throw new AssertionError("Legal content text is not visible.");
		}
	}

	private void assertEmailVisible(final Page page) {
		final String bodyText = page.locator("body").innerText();
		final Matcher matcher = EMAIL_PATTERN.matcher(bodyText);
		assertTrue("No user email found in account page.", matcher.find());
	}

	private void assertUserNameLikelyVisible(final Page page) {
		final String bodyText = page.locator("body").innerText();
		final boolean hasNameLabel = Pattern.compile("(?i)nombre").matcher(bodyText).find();
		final boolean hasAccountEmail = bodyText.contains(GOOGLE_ACCOUNT_EMAIL);

		assertTrue("No clear user name signal found in Informacion General section.", hasNameLabel || hasAccountEmail);
	}

	private void assertBusinessNameInputVisible(final Page page) {
		final Locator field = findBusinessNameInput(page);
		if (field == null) {
			throw new AssertionError("Field 'Nombre del Negocio' was not found.");
		}
	}

	private Locator findBusinessNameInput(final Page page) {
		final Pattern namePattern = Pattern.compile("(?i)nombre\\s+del\\s+negocio");
		final List<Locator> candidates = new ArrayList<>();
		candidates.add(page.getByLabel(namePattern));
		candidates.add(page.getByPlaceholder(namePattern));
		candidates.add(page.locator("input[placeholder*='Nombre'][placeholder*='Negocio']"));
		candidates.add(page.locator("input[name*='negocio' i]"));
		return findVisible(page, candidates, ACTION_TIMEOUT_MS);
	}

	private void assertTextVisible(final Page page, final String text, final long timeoutMs) {
		assertTextVisible(page, text, timeoutMs, text);
	}

	private void assertTextVisible(final Page page, final String text, final long timeoutMs, final String description) {
		final Locator locator = waitForVisible(page, candidatesByVisibleText(page, text), timeoutMs, description);
		assertTrue("Expected visible text: " + description, locator.isVisible());
	}

	private void assertPatternVisible(final Page page, final Pattern pattern, final long timeoutMs,
			final String description) {
		final List<Locator> candidates = new ArrayList<>();
		candidates.add(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(pattern)));
		candidates.add(page.getByText(pattern));
		final Locator locator = waitForVisible(page, candidates, timeoutMs, description);
		assertTrue("Expected visible pattern: " + description, locator.isVisible());
	}

	private boolean isTextVisible(final Page page, final String text, final long timeoutMs) {
		return findVisible(page, candidatesByVisibleText(page, text), timeoutMs) != null;
	}

	private boolean clickIfVisible(final Page page, final String text, final long timeoutMs) {
		final Locator locator = findVisible(page, candidatesByVisibleText(page, text), timeoutMs);
		if (locator == null) {
			return false;
		}
		locator.click();
		waitForUiLoad(page);
		return true;
	}

	private void clickByText(final Page page, final String text) {
		final Locator locator = waitForVisible(page, candidatesByVisibleText(page, text), ACTION_TIMEOUT_MS,
				"clickable text: " + text);
		locator.click();
		waitForUiLoad(page);
	}

	private List<Locator> candidatesByVisibleText(final Page page, final String text) {
		final Pattern exact = Pattern.compile("(?i)^\\s*" + Pattern.quote(text) + "\\s*$");
		final Pattern contains = Pattern.compile("(?i)" + Pattern.quote(text));

		final List<Locator> candidates = new ArrayList<>();
		candidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(exact)));
		candidates.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(exact)));
		candidates.add(page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(exact)));
		candidates.add(page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(exact)));
		candidates.add(page.getByText(exact));
		candidates.add(page.getByText(contains));
		return candidates;
	}

	private Locator waitForVisible(final Page page, final List<Locator> candidates, final long timeoutMs,
			final String description) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final Locator candidate : candidates) {
				try {
					if (candidate.count() > 0 && candidate.first().isVisible()) {
						return candidate.first();
					}
				} catch (final PlaywrightException ignored) {
					// Keep polling until timeout.
				}
			}
			page.waitForTimeout(250);
		}
		throw new AssertionError("Could not find visible element for: " + description);
	}

	private Locator findVisible(final Page page, final List<Locator> candidates, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final Locator candidate : candidates) {
				try {
					if (candidate.count() > 0 && candidate.first().isVisible()) {
						return candidate.first();
					}
				} catch (final PlaywrightException ignored) {
					// Keep polling until timeout.
				}
			}
			page.waitForTimeout(200);
		}
		return null;
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000));
		} catch (final PlaywrightException ignored) {
			// Some pages keep open connections; DOM content loaded is enough as fallback.
		}
		page.waitForTimeout(700);
	}

	private void capture(final Page page, final Path runDir, final String name, final boolean fullPage) {
		final Path path = runDir.resolve(name + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private void runStep(final String field, final Map<String, StepResult> results, final StepAction action) {
		try {
			final String details = action.run();
			results.put(field, StepResult.pass(details == null ? "PASS" : details));
		} catch (final Throwable error) {
			results.put(field, StepResult.fail(compactError(error)));
		}
	}

	private static String compactError(final Throwable error) {
		final String message = error.getMessage();
		if (message == null || message.isBlank()) {
			return error.getClass().getSimpleName();
		}
		return message.length() <= 280 ? message : message.substring(0, 280) + "...";
	}

	private static Map<String, StepResult> initResultsMap() {
		final Map<String, StepResult> map = new LinkedHashMap<>();
		map.put(FIELD_LOGIN, StepResult.notExecuted());
		map.put(FIELD_MI_NEGOCIO_MENU, StepResult.notExecuted());
		map.put(FIELD_AGREGAR_NEGOCIO_MODAL, StepResult.notExecuted());
		map.put(FIELD_ADMINISTRAR_NEGOCIOS, StepResult.notExecuted());
		map.put(FIELD_INFORMACION_GENERAL, StepResult.notExecuted());
		map.put(FIELD_DETALLES_CUENTA, StepResult.notExecuted());
		map.put(FIELD_TUS_NEGOCIOS, StepResult.notExecuted());
		map.put(FIELD_TERMINOS, StepResult.notExecuted());
		map.put(FIELD_PRIVACIDAD, StepResult.notExecuted());
		return map;
	}

	private static Path createRunDir() throws IOException {
		final String baseDir = stringProperty("saleads.screenshots.dir", "SALEADS_SCREENSHOTS_DIR");
		final Path root = baseDir == null || baseDir.isBlank() ? Paths.get("target", "saleads-e2e-evidence")
				: Paths.get(baseDir);
		final String timestamp = TS_FORMAT.format(ZonedDateTime.now(ZoneOffset.UTC));
		final Path runDir = root.resolve(RUN_FOLDER_PREFIX + timestamp);
		Files.createDirectories(runDir);
		return runDir;
	}

	private static void writeReport(final Path path, final Map<String, StepResult> results) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, renderReport(results), StandardCharsets.UTF_8);
	}

	private static String renderReport(final Map<String, StepResult> results) {
		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test\n");
		sb.append("================================\n");
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().status);
			if (entry.getValue().details != null && !entry.getValue().details.isBlank()) {
				sb.append(" | ").append(entry.getValue().details);
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	private static boolean allPassed(final Map<String, StepResult> results) {
		for (final StepResult result : results.values()) {
			if (!"PASS".equals(result.status)) {
				return false;
			}
		}
		return true;
	}

	private static long longProperty(final String key, final String env, final long defaultValue) {
		final String raw = stringProperty(key, env);
		if (raw == null || raw.isBlank()) {
			return defaultValue;
		}
		return Long.parseLong(raw.trim());
	}

	private static boolean boolProperty(final String key, final String env, final boolean defaultValue) {
		final String raw = stringProperty(key, env);
		if (raw == null || raw.isBlank()) {
			return defaultValue;
		}
		return Boolean.parseBoolean(raw.trim());
	}

	private static String stringProperty(final String key, final String env) {
		final String property = System.getProperty(key);
		if (property != null && !property.isBlank()) {
			return property;
		}
		final String value = System.getenv(env);
		if (value != null && !value.isBlank()) {
			return value;
		}
		return null;
	}

	@FunctionalInterface
	private interface StepAction {
		String run() throws Exception;
	}

	private static final class StepResult {
		private final String status;
		private final String details;

		private StepResult(final String status, final String details) {
			this.status = status;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult("PASS", details);
		}

		private static StepResult fail(final String details) {
			return new StepResult("FAIL", details);
		}

		private static StepResult notExecuted() {
			return new StepResult("FAIL", "Not executed");
		}
	}
}
