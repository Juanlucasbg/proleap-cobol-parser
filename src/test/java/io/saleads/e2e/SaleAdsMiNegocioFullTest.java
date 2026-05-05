package io.saleads.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class SaleAdsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final int DEFAULT_TIMEOUT_MS = 15000;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = readEnv("SALEADS_LOGIN_URL");
		final String wsEndpoint = readEnv("PLAYWRIGHT_WS_ENDPOINT");
		final String googleAccount = readEnvOrDefault("SALEADS_GOOGLE_ACCOUNT", "juanlucasbarbiergarzon@gmail.com");
		final String expectedUserName = readEnv("SALEADS_USER_NAME");
		final boolean headless = Boolean.parseBoolean(readEnvOrDefault("SALEADS_HEADLESS", "true"));

		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL (any environment login page) or PLAYWRIGHT_WS_ENDPOINT (pre-opened browser).",
				!isBlank(loginUrl) || !isBlank(wsEndpoint));

		final Path evidenceDir = createEvidenceDirectory();
		final Map<String, StepResult> report = createInitialReport();

		String termsAndConditionsUrl = "N/A";
		String privacyPolicyUrl = "N/A";

		try (Playwright playwright = Playwright.create()) {
			final BrowserSession session = createSession(playwright, wsEndpoint, loginUrl, headless);
			final Browser browser = session.browser;
			final BrowserContext context = session.context;
			Page appPage = session.page;

			try {
				try {
					appPage = loginWithGoogleAndValidateDashboard(context, appPage, googleAccount, evidenceDir);
					report.put("Login", StepResult.pass("Dashboard and left sidebar were visible."));
				} catch (Throwable t) {
					report.put("Login", StepResult.fail(t));
				}

				try {
					openMiNegocioAndValidateMenu(appPage, evidenceDir);
					report.put("Mi Negocio menu", StepResult.pass("Mi Negocio expanded with both expected options."));
				} catch (Throwable t) {
					report.put("Mi Negocio menu", StepResult.fail(t));
				}

				try {
					validateAgregarNegocioModal(appPage, evidenceDir);
					report.put("Agregar Negocio modal", StepResult.pass("Modal fields and actions validated."));
				} catch (Throwable t) {
					report.put("Agregar Negocio modal", StepResult.fail(t));
				}

				try {
					openAdministrarNegociosAndValidateView(appPage, evidenceDir);
					report.put("Administrar Negocios view",
							StepResult.pass("Account page sections are visible and loaded."));
				} catch (Throwable t) {
					report.put("Administrar Negocios view", StepResult.fail(t));
				}

				try {
					validateInformacionGeneral(appPage, googleAccount, expectedUserName);
					report.put("Información General", StepResult.pass("Name/email/plan information validated."));
				} catch (Throwable t) {
					report.put("Información General", StepResult.fail(t));
				}

				try {
					validateDetallesDeLaCuenta(appPage);
					report.put("Detalles de la Cuenta", StepResult.pass("Account details labels are visible."));
				} catch (Throwable t) {
					report.put("Detalles de la Cuenta", StepResult.fail(t));
				}

				try {
					validateTusNegocios(appPage);
					report.put("Tus Negocios", StepResult.pass("Business section and capacity text validated."));
				} catch (Throwable t) {
					report.put("Tus Negocios", StepResult.fail(t));
				}

				try {
					termsAndConditionsUrl = validateLegalLink(
							context,
							appPage,
							evidenceDir,
							"08-terminos-y-condiciones.png",
							Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
							Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"));
					report.put("Términos y Condiciones",
							StepResult.pass("Legal page opened and validated. URL: " + termsAndConditionsUrl));
				} catch (Throwable t) {
					report.put("Términos y Condiciones", StepResult.fail(t));
				}

				try {
					privacyPolicyUrl = validateLegalLink(
							context,
							appPage,
							evidenceDir,
							"09-politica-de-privacidad.png",
							Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
							Arrays.asList("Política de Privacidad", "Politica de Privacidad"));
					report.put("Política de Privacidad",
							StepResult.pass("Legal page opened and validated. URL: " + privacyPolicyUrl));
				} catch (Throwable t) {
					report.put("Política de Privacidad", StepResult.fail(t));
				}
			} finally {
				writeFinalReport(evidenceDir, report, termsAndConditionsUrl, privacyPolicyUrl);
				if (browser != null && !browser.isConnected()) {
					// no-op: browser already closed
				}
			}

			assertNoFailures(report);
		}
	}

	private BrowserSession createSession(final Playwright playwright, final String wsEndpoint, final String loginUrl,
			final boolean headless) {
		if (!isBlank(wsEndpoint)) {
			final Browser browser = playwright.chromium().connect(wsEndpoint);
			final BrowserContext context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
			final Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
			if (!isBlank(loginUrl) && isBlank(page.url())) {
				page.navigate(loginUrl);
			}
			waitForUi(page);
			return new BrowserSession(browser, context, page);
		}

		final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
		final Page page = context.newPage();
		page.navigate(loginUrl, new Page.NavigateOptions().setTimeout(60000));
		waitForUi(page);
		return new BrowserSession(browser, context, page);
	}

	private Page loginWithGoogleAndValidateDashboard(final BrowserContext context, final Page page, final String googleEmail,
			final Path evidenceDir) {
		final Locator loginButton = findVisibleActionable(page,
				Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
						"Continuar con Google", "Acceder con Google", "Google"),
				DEFAULT_TIMEOUT_MS);

		final Page popup = clickAndCapturePopup(context, page, loginButton, 7000);
		final Page authPage = popup != null ? popup : page;
		waitForUi(authPage);

		final Locator accountSelector = findVisibleActionableOrNull(authPage,
				Arrays.asList(googleEmail, "juanlucasbarbiergarzon@gmail.com"), 7000);
		if (accountSelector != null) {
			accountSelector.first().click();
			waitForUi(authPage);
		}

		final Page appPage = waitForApplicationPage(context, page, popup, 60000);
		assertSidebarVisible(appPage);
		captureScreenshot(appPage, evidenceDir, "01-dashboard-loaded.png", false);
		appPage.bringToFront();
		return appPage;
	}

	private void openMiNegocioAndValidateMenu(final Page appPage, final Path evidenceDir) {
		clickIfVisible(appPage, Arrays.asList("Negocio"));
		clickByVisibleText(appPage, Arrays.asList("Mi Negocio"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Agregar Negocio"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Administrar Negocios"));
		captureScreenshot(appPage, evidenceDir, "02-mi-negocio-menu-expandido.png", false);
	}

	private void validateAgregarNegocioModal(final Page appPage, final Path evidenceDir) {
		clickByVisibleText(appPage, Arrays.asList("Agregar Negocio"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Crear Nuevo Negocio"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Nombre del Negocio"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Tienes 2 de 3 negocios"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Cancelar"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Crear Negocio"));

		final Locator businessNameInput = findVisibleLocatorOrNull(Arrays.asList(
				appPage.getByLabel("Nombre del Negocio"),
				appPage.getByPlaceholder("Nombre del Negocio"),
				appPage.locator("input[placeholder*='Nombre'], input[name*='nombre']")), 4000);
		if (businessNameInput != null) {
			businessNameInput.fill("Negocio Prueba Automatizacion");
			waitForUi(appPage);
		}

		captureScreenshot(appPage, evidenceDir, "03-modal-crear-nuevo-negocio.png", false);
		clickByVisibleText(appPage, Arrays.asList("Cancelar"));
	}

	private void openAdministrarNegociosAndValidateView(final Page appPage, final Path evidenceDir) {
		if (!hasAnyVisibleText(appPage, Arrays.asList("Administrar Negocios"), 1000)) {
			clickByVisibleText(appPage, Arrays.asList("Mi Negocio"));
		}
		clickByVisibleText(appPage, Arrays.asList("Administrar Negocios"));

		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Información General", "Informacion General"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Detalles de la Cuenta", "Detalles de la cuenta"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Tus Negocios"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Sección Legal", "Seccion Legal"));
		captureScreenshot(appPage, evidenceDir, "04-administrar-negocios-cuenta.png", true);
	}

	private void validateInformacionGeneral(final Page appPage, final String googleEmail, final String expectedUserName) {
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("BUSINESS PLAN"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Cambiar Plan"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList(googleEmail));

		if (!isBlank(expectedUserName)) {
			waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList(expectedUserName));
		} else {
			final String bodyText = safeBodyText(appPage).toLowerCase(Locale.ROOT);
			final List<String> blacklist = Arrays.asList("información general", "informacion general", "detalles de la cuenta",
					"tus negocios", "sección legal", "seccion legal", "business plan", "cambiar plan");

			boolean hasLikelyName = false;
			for (String line : safeBodyText(appPage).split("\\R")) {
				final String normalized = line.trim().toLowerCase(Locale.ROOT);
				if (normalized.isEmpty() || normalized.contains("@")) {
					continue;
				}
				if (blacklist.contains(normalized)) {
					continue;
				}
				if (normalized.matches("[\\p{L}]{3,}(\\s+[\\p{L}]{3,})+")) {
					hasLikelyName = true;
					break;
				}
			}
			Assert.assertTrue(
					"A user name-like value should be visible. Set SALEADS_USER_NAME for strict validation if needed.",
					hasLikelyName || bodyText.contains("juan"));
		}
	}

	private void validateDetallesDeLaCuenta(final Page appPage) {
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Cuenta creada"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Estado activo"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Idioma seleccionado"));
	}

	private void validateTusNegocios(final Page appPage) {
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Tus Negocios"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Agregar Negocio"));
		waitForAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, Arrays.asList("Tienes 2 de 3 negocios"));

		final Locator section = findSectionContainer(appPage, Arrays.asList("Tus Negocios"));
		boolean businessListVisible = false;
		if (section != null) {
			businessListVisible = section.locator("li, [role='listitem'], tr, [role='row'], article, .card").count() > 0;
		}
		if (!businessListVisible) {
			businessListVisible = appPage.locator("li, [role='listitem'], tr, [role='row']").count() > 0;
		}
		Assert.assertTrue("Business list should be visible in 'Tus Negocios'.", businessListVisible);
	}

	private String validateLegalLink(final BrowserContext context, final Page appPage, final Path evidenceDir,
			final String screenshotName, final List<String> linkTexts, final List<String> expectedHeadingTexts) {
		appPage.bringToFront();
		final String applicationUrlBeforeClick = appPage.url();
		final Locator legalLink = findVisibleActionable(appPage, linkTexts, DEFAULT_TIMEOUT_MS);
		final Page popup = clickAndCapturePopup(context, appPage, legalLink, 6000);
		final Page legalPage = popup != null ? popup : appPage;
		legalPage.bringToFront();
		waitForUi(legalPage);

		waitForAnyVisibleText(legalPage, DEFAULT_TIMEOUT_MS, expectedHeadingTexts);
		final String legalContent = safeBodyText(legalPage).trim();
		Assert.assertTrue("Expected legal content text to be visible.", legalContent.length() > 120);

		final String finalUrl = legalPage.url();
		captureScreenshot(legalPage, evidenceDir, screenshotName, true);

		if (popup != null) {
			popup.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				final Response backResponse = appPage.goBack(new Page.GoBackOptions().setTimeout(10000));
				if (backResponse == null && !isBlank(applicationUrlBeforeClick)) {
					appPage.navigate(applicationUrlBeforeClick, new Page.NavigateOptions().setTimeout(20000));
				}
			} catch (PlaywrightException ignored) {
				if (!isBlank(applicationUrlBeforeClick)) {
					appPage.navigate(applicationUrlBeforeClick, new Page.NavigateOptions().setTimeout(20000));
				}
			}
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private Page waitForApplicationPage(final BrowserContext context, final Page defaultPage, final Page popup,
			final int timeoutMs) {
		final long end = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < end) {
			for (Page candidate : context.pages()) {
				if (candidate.isClosed()) {
					continue;
				}
				if (isSidebarVisible(candidate) || hasAnyVisibleText(candidate, Arrays.asList("Mi Negocio", "Negocio"), 200)) {
					return candidate;
				}
			}
			waitForUi(defaultPage);
			if (popup != null && !popup.isClosed()) {
				waitForUi(popup);
			}
			defaultPage.waitForTimeout(250);
		}
		return defaultPage;
	}

	private boolean isSidebarVisible(final Page page) {
		final Locator nav = page.getByRole(AriaRole.NAVIGATION);
		if (isVisible(nav, 300)) {
			return true;
		}
		return hasAnyVisibleText(page, Arrays.asList("Mi Negocio", "Negocio"), 300);
	}

	private void assertSidebarVisible(final Page page) {
		Assert.assertTrue("Expected left sidebar navigation to be visible after login.", isSidebarVisible(page));
	}

	private void clickIfVisible(final Page page, final List<String> texts) {
		final Locator locator = findVisibleActionableOrNull(page, texts, 2000);
		if (locator != null) {
			locator.first().click();
			waitForUi(page);
		}
	}

	private void clickByVisibleText(final Page page, final List<String> texts) {
		final Locator locator = findVisibleActionable(page, texts, DEFAULT_TIMEOUT_MS);
		locator.first().click();
		waitForUi(page);
	}

	private Page clickAndCapturePopup(final BrowserContext context, final Page page, final Locator locator,
			final int popupTimeoutMs) {
		try {
			return context.waitForPage(() -> {
				locator.first().click();
				waitForUi(page);
			}, new BrowserContext.WaitForPageOptions().setTimeout(popupTimeoutMs));
		} catch (PlaywrightException ignored) {
			locator.first().click();
			waitForUi(page);
			return null;
		}
	}

	private Locator findVisibleActionable(final Page page, final List<String> texts, final int timeoutMs) {
		final Locator locator = findVisibleActionableOrNull(page, texts, timeoutMs);
		if (locator == null) {
			throw new AssertionError("Could not find visible clickable element for text(s): " + texts);
		}
		return locator;
	}

	private Locator findVisibleActionableOrNull(final Page page, final List<String> texts, final int timeoutMs) {
		final long end = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < end) {
			for (String text : texts) {
				final List<Locator> candidates = Arrays.asList(
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)),
						page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(text)),
						page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(text)),
						page.getByText(text, new Page.GetByTextOptions().setExact(true)),
						page.getByText(text));

				for (Locator candidate : candidates) {
					if (isVisible(candidate, 200)) {
						return candidate.first();
					}
				}
			}
			page.waitForTimeout(200);
		}
		return null;
	}

	private Locator findVisibleLocatorOrNull(final List<Locator> locators, final int timeoutMs) {
		final long end = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < end) {
			for (Locator locator : locators) {
				if (isVisible(locator, 200)) {
					return locator.first();
				}
			}
		}
		return null;
	}

	private Locator findSectionContainer(final Page page, final List<String> headingTexts) {
		for (String heading : headingTexts) {
			final Locator headingLocator = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(heading));
			if (isVisible(headingLocator, 300)) {
				return headingLocator.first().locator("xpath=ancestor::*[self::section or self::div][1]");
			}

			final Locator textLocator = page.getByText(heading, new Page.GetByTextOptions().setExact(true));
			if (isVisible(textLocator, 300)) {
				return textLocator.first().locator("xpath=ancestor::*[self::section or self::div][1]");
			}
		}
		return null;
	}

	private boolean hasAnyVisibleText(final Page page, final List<String> texts, final int timeoutMs) {
		try {
			waitForAnyVisibleText(page, timeoutMs, texts);
			return true;
		} catch (AssertionError ignored) {
			return false;
		}
	}

	private void waitForAnyVisibleText(final Page page, final int timeoutMs, final List<String> texts) {
		final long end = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < end) {
			for (String text : texts) {
				if (isVisible(page.getByText(text, new Page.GetByTextOptions().setExact(true)), 200)
						|| isVisible(page.getByText(text), 200)
						|| isVisible(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(text)), 200)
						|| isVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)), 200)
						|| isVisible(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)), 200)) {
					return;
				}
			}
			page.waitForTimeout(200);
		}
		throw new AssertionError("Expected at least one visible text from " + texts);
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			return locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout((double) timeoutMs));
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
			// Some SPA transitions never go fully idle. Continue with a short buffer.
		}
		page.waitForTimeout(300);
	}

	private void captureScreenshot(final Page page, final Path evidenceDir, final String filename, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(filename)).setFullPage(fullPage));
	}

	private String safeBodyText(final Page page) {
		try {
			return page.locator("body").innerText();
		} catch (PlaywrightException ignored) {
			return "";
		}
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = Instant.now().toString().replace(":", "-");
		final Path evidenceDir = Paths.get("target", "saleads-evidence", TEST_NAME, timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private Map<String, StepResult> createInitialReport() {
		final Map<String, StepResult> report = new LinkedHashMap<>();
		report.put("Login", StepResult.notRun());
		report.put("Mi Negocio menu", StepResult.notRun());
		report.put("Agregar Negocio modal", StepResult.notRun());
		report.put("Administrar Negocios view", StepResult.notRun());
		report.put("Información General", StepResult.notRun());
		report.put("Detalles de la Cuenta", StepResult.notRun());
		report.put("Tus Negocios", StepResult.notRun());
		report.put("Términos y Condiciones", StepResult.notRun());
		report.put("Política de Privacidad", StepResult.notRun());
		return report;
	}

	private void writeFinalReport(final Path evidenceDir, final Map<String, StepResult> report, final String termsUrl,
			final String privacyUrl) throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("test_name: " + TEST_NAME);
		lines.add("generated_at: " + Instant.now());
		lines.add("");
		lines.add("result_matrix:");
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			lines.add("- " + entry.getKey() + ": " + entry.getValue().status);
			lines.add("  details: " + entry.getValue().details);
		}
		lines.add("");
		lines.add("final_urls:");
		lines.add("- Terminos y Condiciones: " + termsUrl);
		lines.add("- Politica de Privacidad: " + privacyUrl);

		Files.write(evidenceDir.resolve("final-report.txt"), lines, StandardCharsets.UTF_8);
	}

	private void assertNoFailures(final Map<String, StepResult> report) {
		final List<Map.Entry<String, StepResult>> failures = report.entrySet().stream()
				.filter(entry -> entry.getValue().status == StepStatus.FAIL).collect(Collectors.toList());
		if (failures.isEmpty()) {
			return;
		}

		final String failureSummary = failures.stream()
				.map(entry -> entry.getKey() + " -> " + entry.getValue().details)
				.collect(Collectors.joining(System.lineSeparator()));
		Assert.fail("One or more workflow validations failed:" + System.lineSeparator() + failureSummary);
	}

	private String readEnv(final String key) {
		return System.getenv(key);
	}

	private String readEnvOrDefault(final String key, final String defaultValue) {
		final String value = readEnv(key);
		return isBlank(value) ? defaultValue : value;
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private static final class BrowserSession {
		private final Browser browser;
		private final BrowserContext context;
		private final Page page;

		private BrowserSession(final Browser browser, final BrowserContext context, final Page page) {
			this.browser = browser;
			this.context = context;
			this.page = page;
		}
	}

	private enum StepStatus {
		PASS, FAIL, NOT_RUN
	}

	private static final class StepResult {
		private final StepStatus status;
		private final String details;

		private StepResult(final StepStatus status, final String details) {
			this.status = status;
			this.details = details;
		}

		private static StepResult pass(final String detail) {
			return new StepResult(StepStatus.PASS, detail);
		}

		private static StepResult fail(final Throwable error) {
			return new StepResult(StepStatus.FAIL,
					error.getClass().getSimpleName() + ": " + (error.getMessage() == null ? "" : error.getMessage()));
		}

		private static StepResult notRun() {
			return new StepResult(StepStatus.NOT_RUN, "Step not executed yet.");
		}
	}
}
