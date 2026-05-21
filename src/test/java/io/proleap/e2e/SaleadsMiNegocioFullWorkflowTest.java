package io.proleap.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Política de Privacidad";

	private static final Pattern GOOGLE_SIGN_IN_PATTERN = Pattern
			.compile("(?i)(sign\\s*in.*google|iniciar\\s*sesi[oó]n.*google|continuar.*google|google)");
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final long SHORT_TIMEOUT_MS = 5_000;
	private static final long DEFAULT_TIMEOUT_MS = 15_000;
	private static final long LOGIN_TIMEOUT_MS = 120_000;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		Assume.assumeTrue(
				"Set -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true to run this workflow test.",
				readBooleanSetting("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false));

		final String loginUrl = readStringSetting("saleads.login.url", "SALEADS_LOGIN_URL", null);
		Assume.assumeTrue(
				"Set -Dsaleads.login.url (or SALEADS_LOGIN_URL) to the login page of the current SaleADS environment.",
				loginUrl != null && !loginUrl.isBlank());

		final String expectedGoogleAccount = readStringSetting("saleads.google.email", "SALEADS_GOOGLE_EMAIL",
				"juanlucasbarbiergarzon@gmail.com");
		final boolean headless = readBooleanSetting("saleads.headless", "SALEADS_HEADLESS", true);
		final String screenshotRoot = readStringSetting("saleads.screenshot.dir", "SALEADS_SCREENSHOT_DIR",
				"target/saleads-evidence");

		final Map<String, StepResult> results = initializeResultMap();
		final Path screenshotDir = createScreenshotDirectory(screenshotRoot);
		final String[] legalUrls = new String[2];

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(200));
			try (BrowserContext context = browser.newContext(
					new Browser.NewContextOptions().setViewportSize(1440, 900).setIgnoreHTTPSErrors(true))) {

				Page appPage = null;

				try {
					appPage = loginWithGoogle(context, loginUrl, expectedGoogleAccount);
					captureScreenshot(appPage, screenshotDir, "step-01-dashboard-loaded", true);
					markPass(results, STEP_LOGIN, "Main interface and sidebar detected after Google login.");
				} catch (RuntimeException exception) {
					captureFailureScreenshot(context, screenshotDir, "step-01-login-failure");
					markFail(results, STEP_LOGIN, exception.getMessage());
				}

				try {
					ensurePrerequisite(appPage, STEP_MI_NEGOCIO_MENU, STEP_LOGIN);
					openMiNegocioMenu(appPage);
					captureScreenshot(appPage, screenshotDir, "step-02-mi-negocio-expanded", true);
					markPass(results, STEP_MI_NEGOCIO_MENU, "Mi Negocio expanded with submenu options visible.");
				} catch (RuntimeException exception) {
					captureFailureScreenshot(context, screenshotDir, "step-02-mi-negocio-menu-failure");
					markFail(results, STEP_MI_NEGOCIO_MENU, exception.getMessage());
				}

				try {
					ensurePrerequisite(appPage, STEP_AGREGAR_MODAL, STEP_MI_NEGOCIO_MENU);
					validateAgregarNegocioModal(appPage);
					captureScreenshot(appPage, screenshotDir, "step-03-crear-negocio-modal", true);
					closeCrearNegocioModal(appPage);
					markPass(results, STEP_AGREGAR_MODAL,
							"Crear Nuevo Negocio modal validated with required fields and buttons.");
				} catch (RuntimeException exception) {
					captureFailureScreenshot(context, screenshotDir, "step-03-agregar-negocio-modal-failure");
					markFail(results, STEP_AGREGAR_MODAL, exception.getMessage());
				}

				try {
					ensurePrerequisite(appPage, STEP_ADMINISTRAR_VIEW, STEP_MI_NEGOCIO_MENU);
					openAdministrarNegocios(appPage);
					captureScreenshot(appPage, screenshotDir, "step-04-administrar-negocios", true);
					markPass(results, STEP_ADMINISTRAR_VIEW, "Administrar Negocios page loaded with all major sections.");
				} catch (RuntimeException exception) {
					captureFailureScreenshot(context, screenshotDir, "step-04-administrar-negocios-failure");
					markFail(results, STEP_ADMINISTRAR_VIEW, exception.getMessage());
				}

				try {
					ensurePrerequisite(appPage, STEP_INFO_GENERAL, STEP_ADMINISTRAR_VIEW);
					validateInformacionGeneral(appPage, expectedGoogleAccount);
					markPass(results, STEP_INFO_GENERAL, "Información General section contains user and plan information.");
				} catch (RuntimeException exception) {
					captureFailureScreenshot(context, screenshotDir, "step-05-informacion-general-failure");
					markFail(results, STEP_INFO_GENERAL, exception.getMessage());
				}

				try {
					ensurePrerequisite(appPage, STEP_DETALLES_CUENTA, STEP_ADMINISTRAR_VIEW);
					validateDetallesDeLaCuenta(appPage);
					markPass(results, STEP_DETALLES_CUENTA, "Detalles de la Cuenta section fields are visible.");
				} catch (RuntimeException exception) {
					captureFailureScreenshot(context, screenshotDir, "step-06-detalles-cuenta-failure");
					markFail(results, STEP_DETALLES_CUENTA, exception.getMessage());
				}

				try {
					ensurePrerequisite(appPage, STEP_TUS_NEGOCIOS, STEP_ADMINISTRAR_VIEW);
					validateTusNegocios(appPage);
					markPass(results, STEP_TUS_NEGOCIOS, "Tus Negocios list, quota text, and action button validated.");
				} catch (RuntimeException exception) {
					captureFailureScreenshot(context, screenshotDir, "step-07-tus-negocios-failure");
					markFail(results, STEP_TUS_NEGOCIOS, exception.getMessage());
				}

				try {
					ensurePrerequisite(appPage, STEP_TERMINOS, STEP_ADMINISTRAR_VIEW);
					legalUrls[0] = openAndValidateLegalDocument(context, appPage, "Términos y Condiciones",
							Pattern.compile("(?i)términos\\s*y\\s*condiciones"), screenshotDir, "step-08-terminos");
					markPass(results, STEP_TERMINOS, "Legal content validated. Final URL: " + legalUrls[0]);
				} catch (RuntimeException exception) {
					captureFailureScreenshot(context, screenshotDir, "step-08-terminos-failure");
					markFail(results, STEP_TERMINOS, exception.getMessage());
				}

				try {
					ensurePrerequisite(appPage, STEP_PRIVACIDAD, STEP_ADMINISTRAR_VIEW);
					legalUrls[1] = openAndValidateLegalDocument(context, appPage, "Política de Privacidad",
							Pattern.compile("(?i)pol[ií]tica\\s*de\\s*privacidad"), screenshotDir, "step-09-privacidad");
					markPass(results, STEP_PRIVACIDAD, "Legal content validated. Final URL: " + legalUrls[1]);
				} catch (RuntimeException exception) {
					captureFailureScreenshot(context, screenshotDir, "step-09-privacidad-failure");
					markFail(results, STEP_PRIVACIDAD, exception.getMessage());
				}
			}
		}

		final String report = buildFinalReport(results);
		System.out.println(report);
		assertTrue("One or more SaleADS Mi Negocio validations failed.\n" + report, allStepsPassed(results));
	}

	private Page loginWithGoogle(final BrowserContext context, final String loginUrl, final String expectedGoogleAccount) {
		final Page loginPage = context.newPage();
		loginPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
		waitForUi(loginPage);

		final Locator signInLocator = firstVisibleLocator(
				loginPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_SIGN_IN_PATTERN)),
				loginPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_SIGN_IN_PATTERN)),
				loginPage.getByText(GOOGLE_SIGN_IN_PATTERN).first());
		assertVisible(signInLocator, "Could not find a Google login trigger.");

		final Page postClickPage = clickAndCapturePossibleNewPage(context, loginPage, signInLocator);
		selectGoogleAccountIfPrompted(postClickPage, expectedGoogleAccount);

		final Page applicationPage = waitForMainApplicationPage(context, LOGIN_TIMEOUT_MS);
		assertVisible(applicationPage.locator("aside").first(), "Left sidebar is not visible after login.");
		return applicationPage;
	}

	private void openMiNegocioMenu(final Page page) {
		assertVisible(page.locator("aside").first(), "Left sidebar is not visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)^negocio$")).first(), "Negocio section is not visible.");

		final Locator miNegocio = firstVisibleLocator(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi\\s*negocio"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi\\s*negocio"))),
				page.getByText(Pattern.compile("(?i)mi\\s*negocio")).first());
		assertVisible(miNegocio, "Mi Negocio option is not visible in the sidebar.");
		clickAndWait(page, miNegocio);

		assertVisible(page.getByText(Pattern.compile("(?i)agregar\\s*negocio")).first(),
				"Agregar Negocio is not visible after expanding Mi Negocio.");
		assertVisible(page.getByText(Pattern.compile("(?i)administrar\\s*negocios")).first(),
				"Administrar Negocios is not visible after expanding Mi Negocio.");
	}

	private void validateAgregarNegocioModal(final Page page) {
		clickAndWait(page, page.getByText(Pattern.compile("(?i)^agregar\\s*negocio$")).first());

		assertVisible(page.getByText(Pattern.compile("(?i)crear\\s*nuevo\\s*negocio")).first(),
				"Modal title 'Crear Nuevo Negocio' is not visible.");
		assertVisible(findNombreNegocioInput(page), "Input field 'Nombre del Negocio' is not visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")).first(),
				"Quota text 'Tienes 2 de 3 negocios' is not visible in the modal.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))),
				"Button 'Cancelar' is not visible.");
		assertVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s*negocio"))),
				"Button 'Crear Negocio' is not visible.");

		final Locator nombreInput = findNombreNegocioInput(page);
		clickAndWait(page, nombreInput);
		nombreInput.fill("Negocio Prueba Automatización");
	}

	private void closeCrearNegocioModal(final Page page) {
		final Locator cancelar = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar")));
		clickAndWait(page, cancelar);
		page.getByText(Pattern.compile("(?i)crear\\s*nuevo\\s*negocio")).first()
				.waitFor(new Locator.WaitForOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN)
						.setTimeout(DEFAULT_TIMEOUT_MS));
	}

	private void openAdministrarNegocios(final Page page) {
		final Locator administrar = page.getByText(Pattern.compile("(?i)administrar\\s*negocios")).first();
		if (!isVisible(administrar)) {
			openMiNegocioMenu(page);
		}

		clickAndWait(page, page.getByText(Pattern.compile("(?i)administrar\\s*negocios")).first());

		assertVisible(page.getByText(Pattern.compile("(?i)informaci[oó]n\\s*general")).first(),
				"Section 'Información General' is missing.");
		assertVisible(page.getByText(Pattern.compile("(?i)detalles\\s*de\\s*la\\s*cuenta")).first(),
				"Section 'Detalles de la Cuenta' is missing.");
		assertVisible(page.getByText(Pattern.compile("(?i)tus\\s*negocios")).first(),
				"Section 'Tus Negocios' is missing.");
		assertVisible(page.getByText(Pattern.compile("(?i)secci[oó]n\\s*legal")).first(),
				"Section 'Sección Legal' is missing.");
	}

	private void validateInformacionGeneral(final Page page, final String expectedGoogleAccount) {
		assertVisible(page.getByText(Pattern.compile("(?i)informaci[oó]n\\s*general")).first(),
				"'Información General' heading is not visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)business\\s*plan")).first(),
				"'BUSINESS PLAN' text is not visible.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar\\s*plan"))),
				"'Cambiar Plan' button is not visible.");

		final Locator emailLocator = firstVisibleLocator(page.getByText(EMAIL_PATTERN).first(),
				page.getByText(Pattern.compile(Pattern.quote(expectedGoogleAccount), Pattern.CASE_INSENSITIVE)).first());
		assertVisible(emailLocator, "User email is not visible.");
		assertTrue("User name does not appear to be visible.", hasLikelyUserDisplayName(page));
	}

	private void validateDetallesDeLaCuenta(final Page page) {
		assertVisible(page.getByText(Pattern.compile("(?i)cuenta\\s*creada")).first(), "'Cuenta creada' is not visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)estado\\s*activo")).first(), "'Estado activo' is not visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)idioma\\s*seleccionado")).first(),
				"'Idioma seleccionado' is not visible.");
	}

	private void validateTusNegocios(final Page page) {
		assertVisible(page.getByText(Pattern.compile("(?i)tus\\s*negocios")).first(), "'Tus Negocios' title is not visible.");
		assertVisible(page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")).first(),
				"'Tienes 2 de 3 negocios' text is not visible.");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s*negocio"))),
				"'Agregar Negocio' button is not visible in 'Tus Negocios'.");
		assertTrue("Business list is not visible.", hasBusinessListSignals(page));
	}

	private String openAndValidateLegalDocument(final BrowserContext context, final Page appPage, final String linkText,
			final Pattern headingPattern, final Path screenshotDir, final String screenshotPrefix) {
		final Locator legalLink = firstVisibleLocator(
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))),
				appPage.getByText(Pattern.compile("(?i)" + Pattern.quote(linkText))).first());
		assertVisible(legalLink, "Could not find legal link: " + linkText);

		final Page legalPage = clickAndCapturePossibleNewPage(context, appPage, legalLink);
		waitForUi(legalPage);

		assertVisible(legalPage.getByText(headingPattern).first(), "Heading '" + linkText + "' is not visible.");
		assertTrue("Legal content text is not visible for '" + linkText + "'.", hasLegalContent(legalPage));

		captureScreenshot(legalPage, screenshotDir, screenshotPrefix, true);
		final String finalUrl = legalPage.url();

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private Locator findNombreNegocioInput(final Page page) {
		return firstVisibleLocator(
				page.getByLabel(Pattern.compile("(?i)nombre\\s*del\\s*negocio")).first(),
				page.getByPlaceholder(Pattern.compile("(?i)nombre\\s*del\\s*negocio")).first(),
				page.locator("input[name*='negocio' i], input[id*='negocio' i], input[placeholder*='negocio' i]").first());
	}

	private Page clickAndCapturePossibleNewPage(final BrowserContext context, final Page sourcePage, final Locator clickable) {
		try {
			return context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS), () -> {
				clickable.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			});
		} catch (PlaywrightException exception) {
			waitForUi(sourcePage);
			return sourcePage;
		}
	}

	private void selectGoogleAccountIfPrompted(final Page candidatePage, final String expectedGoogleAccount) {
		waitForUi(candidatePage);

		final boolean onGoogleFlow = candidatePage.url().contains("accounts.google.com");
		final Locator accountOption = firstVisibleLocator(
				candidatePage.getByText(Pattern.compile(Pattern.quote(expectedGoogleAccount), Pattern.CASE_INSENSITIVE)).first(),
				candidatePage.locator("div[role='link']").filter(new Locator.FilterOptions()
						.setHasText(Pattern.compile(Pattern.quote(expectedGoogleAccount), Pattern.CASE_INSENSITIVE))).first());

		if (onGoogleFlow || isVisible(accountOption)) {
			assertVisible(accountOption, "Google account selector appeared, but the expected account was not visible.");
			clickAndWait(candidatePage, accountOption);
		}
	}

	private Page waitForMainApplicationPage(final BrowserContext context, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;

		while (System.currentTimeMillis() < deadline) {
			for (final Page page : context.pages()) {
				if (page.isClosed()) {
					continue;
				}

				try {
					page.waitForLoadState(LoadState.DOMCONTENTLOADED,
							new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
				} catch (PlaywrightException ignored) {
					// Continue polling other pages until the main app layout is available.
				}

				if (isVisible(page.locator("aside").first())
						&& (isVisible(page.getByText(Pattern.compile("(?i)negocio")).first())
								|| isVisible(page.getByText(Pattern.compile("(?i)mi\\s*negocio")).first()))) {
					page.bringToFront();
					waitForUi(page);
					return page;
				}
			}

			sleep(1_000);
		}

		throw new IllegalStateException("Main application interface did not load within login timeout.");
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException exception) {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED,
					new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		}
		page.waitForTimeout(500);
	}

	private Locator firstVisibleLocator(final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate != null && isVisible(candidate)) {
				return candidate;
			}
		}

		return candidates.length == 0 ? null : candidates[0];
	}

	private boolean isVisible(final Locator locator) {
		if (locator == null) {
			return false;
		}

		try {
			return locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (PlaywrightException exception) {
			return false;
		}
	}

	private boolean hasLikelyUserDisplayName(final Page page) {
		final Object evaluation = page.evaluate(
				"() => {"
						+ "  const reserved = ['información general', 'detalles de la cuenta', 'tus negocios', 'sección legal', 'business plan', 'cambiar plan'];"
						+ "  const elements = Array.from(document.querySelectorAll('h1,h2,h3,h4,p,span,strong,div'));"
						+ "  const matchesName = (value) => /^[A-Za-zÀ-ÿ]{2,}(\\s+[A-Za-zÀ-ÿ]{2,})+$/.test(value);"
						+ "  return elements"
						+ "    .map((el) => (el.textContent || '').trim())"
						+ "    .filter((text) => text.length > 3 && text.length < 80)"
						+ "    .some((text) => {"
						+ "      const lower = text.toLowerCase();"
						+ "      if (reserved.some((word) => lower.includes(word))) return false;"
						+ "      return matchesName(text);"
						+ "    });"
						+ "}");
		return Boolean.TRUE.equals(evaluation);
	}

	private boolean hasBusinessListSignals(final Page page) {
		final Locator directListSignals = page.locator(
				"section:has-text('Tus Negocios') li, section:has-text('Tus Negocios') [role='row'], section:has-text('Tus Negocios') article");
		if (directListSignals.count() > 0) {
			return true;
		}

		final Object evaluation = page.evaluate("() => {"
				+ "  const sections = Array.from(document.querySelectorAll('section,div,article'));"
				+ "  const target = sections.find((node) => /tus\\s*negocios/i.test((node.textContent || '').trim()));"
				+ "  if (!target) return false;"
				+ "  const text = (target.textContent || '').replace(/\\s+/g, ' ').trim();"
				+ "  return text.length > 80;"
				+ "}");
		return Boolean.TRUE.equals(evaluation);
	}

	private boolean hasLegalContent(final Page page) {
		final Object evaluation = page.evaluate("() => {"
				+ "  const blocks = Array.from(document.querySelectorAll('p,li,article,section,div'));"
				+ "  return blocks"
				+ "    .map((node) => (node.textContent || '').replace(/\\s+/g, ' ').trim())"
				+ "    .some((text) => text.length >= 120);"
				+ "}");
		return Boolean.TRUE.equals(evaluation);
	}

	private void ensurePrerequisite(final Page appPage, final String targetStep, final String requiredStep) {
		if (appPage == null) {
			throw new IllegalStateException("Skipped '" + targetStep + "' because '" + requiredStep + "' failed.");
		}
	}

	private Path createScreenshotDirectory(final String screenshotRoot) throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Path.of(screenshotRoot, "saleads-mi-negocio-" + timestamp);
		Files.createDirectories(path);
		return path;
	}

	private void captureScreenshot(final Page page, final Path screenshotDir, final String screenshotName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotDir.resolve(screenshotName + ".png")).setFullPage(fullPage));
	}

	private void captureFailureScreenshot(final BrowserContext context, final Path screenshotDir, final String screenshotName) {
		for (final Page page : context.pages()) {
			if (!page.isClosed()) {
				captureScreenshot(page, screenshotDir, screenshotName, true);
				return;
			}
		}
	}

	private void assertVisible(final Locator locator, final String errorMessage) {
		if (!isVisible(locator)) {
			throw new IllegalStateException(errorMessage);
		}
	}

	private void markPass(final Map<String, StepResult> results, final String stepName, final String detail) {
		results.put(stepName, new StepResult(true, detail));
	}

	private void markFail(final Map<String, StepResult> results, final String stepName, final String detail) {
		results.put(stepName, new StepResult(false, detail));
	}

	private Map<String, StepResult> initializeResultMap() {
		final Map<String, StepResult> results = new LinkedHashMap<>();
		results.put(STEP_LOGIN, StepResult.pending());
		results.put(STEP_MI_NEGOCIO_MENU, StepResult.pending());
		results.put(STEP_AGREGAR_MODAL, StepResult.pending());
		results.put(STEP_ADMINISTRAR_VIEW, StepResult.pending());
		results.put(STEP_INFO_GENERAL, StepResult.pending());
		results.put(STEP_DETALLES_CUENTA, StepResult.pending());
		results.put(STEP_TUS_NEGOCIOS, StepResult.pending());
		results.put(STEP_TERMINOS, StepResult.pending());
		results.put(STEP_PRIVACIDAD, StepResult.pending());
		return results;
	}

	private boolean allStepsPassed(final Map<String, StepResult> results) {
		for (final StepResult result : results.values()) {
			if (!result.pass) {
				return false;
			}
		}

		return true;
	}

	private String buildFinalReport(final Map<String, StepResult> results) {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio workflow result summary:\n");

		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().pass ? "PASS" : "FAIL");
			if (entry.getValue().detail != null && !entry.getValue().detail.isBlank()) {
				builder.append(" - ").append(entry.getValue().detail);
			}
			builder.append('\n');
		}

		return builder.toString();
	}

	private String readStringSetting(final String propertyName, final String envName, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return defaultValue;
	}

	private boolean readBooleanSetting(final String propertyName, final String envName, final boolean defaultValue) {
		final String configured = readStringSetting(propertyName, envName, null);
		return configured == null ? defaultValue : Boolean.parseBoolean(configured);
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for application state.", exception);
		}
	}

	private static final class StepResult {
		private final boolean pass;
		private final String detail;

		private StepResult(final boolean pass, final String detail) {
			this.pass = pass;
			this.detail = detail;
		}

		private static StepResult pending() {
			return new StepResult(false, "Not executed.");
		}
	}
}
