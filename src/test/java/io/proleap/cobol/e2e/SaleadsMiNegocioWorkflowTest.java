package io.proleap.cobol.e2e;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.WaitForPopupOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioWorkflowTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final int DEFAULT_TIMEOUT_MS = 20_000;
	private static final int SHORT_TIMEOUT_MS = 4_000;

	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, Boolean> stepReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private Path screenshotDir;
	private Page appPage;
	private String googleAccountEmail;

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		initReport();
		screenshotDir = initScreenshotDir();
		googleAccountEmail = getConfig("saleads.google.email", "SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_ACCOUNT);

		try (Playwright playwright = Playwright.create()) {
			final BrowserSession browserSession = startBrowserSession(playwright);
			appPage = browserSession.page;
			appPage.setDefaultTimeout(getTimeoutMs());
			appPage.setDefaultNavigationTimeout(getTimeoutMs());

			runStep("Login", () -> {
				loginWithGoogle(appPage, browserSession.context);
				validateMainApplicationLoaded(appPage);
				captureScreenshot(appPage, "01-dashboard-loaded", false);
			});

			runStep("Mi Negocio menu", () -> {
				openMiNegocioMenu(appPage);
				captureScreenshot(appPage, "02-mi-negocio-menu-expanded", false);
			});

			runStep("Agregar Negocio modal", () -> {
				validateAgregarNegocioModal(appPage);
				captureScreenshot(appPage, "03-agregar-negocio-modal", false);
			});

			runStep("Administrar Negocios view", () -> {
				openAdministrarNegocios(appPage);
				validateAdministrarNegociosView(appPage);
				captureScreenshot(appPage, "04-administrar-negocios", true);
			});

			runStep("Información General", () -> validateInformacionGeneral(appPage));
			runStep("Detalles de la Cuenta", () -> validateDetallesCuenta(appPage));
			runStep("Tus Negocios", () -> validateTusNegocios(appPage));

			runStep("Términos y Condiciones", () -> {
				final String legalUrl = validateLegalDocument(appPage, browserSession.context, "Términos y Condiciones",
						Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"), "05-terminos-condiciones");
				legalUrls.put("Términos y Condiciones", legalUrl);
			});

			runStep("Política de Privacidad", () -> {
				final String legalUrl = validateLegalDocument(appPage, browserSession.context, "Política de Privacidad",
						Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"), "06-politica-privacidad");
				legalUrls.put("Política de Privacidad", legalUrl);
			});

			browserSession.context.close();
			browserSession.browser.close();
		}

		printFinalReport();

		if (!failures.isEmpty()) {
			fail(String.join(System.lineSeparator(), failures));
		}
	}

	private void initReport() {
		for (final String field : REPORT_FIELDS) {
			stepReport.put(field, Boolean.FALSE);
		}
	}

	private void runStep(final String fieldName, final CheckedStep step) {
		try {
			step.run();
			stepReport.put(fieldName, Boolean.TRUE);
		} catch (final Exception exception) {
			stepReport.put(fieldName, Boolean.FALSE);
			failures.add(fieldName + ": " + exception.getMessage());
			captureStepFailure(fieldName);
		}
	}

	private void captureStepFailure(final String fieldName) {
		if (appPage == null) {
			return;
		}

		try {
			captureScreenshot(appPage, "failure-" + slug(fieldName), true);
		} catch (final Exception ignored) {
			// Capture errors are non-blocking for assertions/reporting.
		}
	}

	private BrowserSession startBrowserSession(final Playwright playwright) {
		final String cdpUrl = getConfig("saleads.cdp.url", "SALEADS_CDP_URL", null);
		final String loginUrl = getConfig("saleads.login.url", "SALEADS_LOGIN_URL", null);
		final boolean headless = getBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true);
		final Browser browser;
		final BrowserContext context;
		final Page page;

		if (cdpUrl != null && !cdpUrl.isBlank()) {
			browser = playwright.chromium().connectOverCDP(cdpUrl);
			context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
			page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
			if (loginUrl != null && !loginUrl.isBlank()) {
				page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			}
		} else {
			browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			context = browser.newContext();
			page = context.newPage();
			if (loginUrl == null || loginUrl.isBlank()) {
				throw new IllegalStateException(
						"Missing login entry point. Set SALEADS_LOGIN_URL/saleads.login.url "
								+ "or connect to an existing browser via SALEADS_CDP_URL/saleads.cdp.url.");
			}
			page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
		}

		waitForUiToLoad(page);
		return new BrowserSession(browser, context, page);
	}

	private void loginWithGoogle(final Page page, final BrowserContext context) {
		final Locator loginButton = requireVisible("Google login button",
				Arrays.asList(
						page.getByRole(AriaRole.BUTTON,
								new Page.GetByRoleOptions()
										.setName(Pattern.compile("(?i).*(sign\\s*in|iniciar\\s*sesi[oó]n|continuar).*google.*"))),
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*"))),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*"))),
						page.getByText(Pattern.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|google)"))));

		Page authPage = null;
		try {
			authPage = page.waitForPopup(new WaitForPopupOptions().setTimeout(SHORT_TIMEOUT_MS),
					() -> clickAndWait(loginButton));
		} catch (final PlaywrightException popupNotOpened) {
			clickAndWait(loginButton);
		}

		if (authPage != null) {
			authPage.setDefaultTimeout(getTimeoutMs());
			waitForUiToLoad(authPage);
			selectGoogleAccountIfPresent(authPage);
			waitForUiToLoad(page);
		} else {
			selectGoogleAccountIfPresent(page);
		}

		for (final Page contextPage : context.pages()) {
			if (contextPage.url().contains("accounts.google.com")) {
				selectGoogleAccountIfPresent(contextPage);
			}
		}

		waitForMainInterface(page);
	}

	private void validateMainApplicationLoaded(final Page page) {
		final Locator sidebar = requireVisible("left sidebar navigation",
				Arrays.asList(page.locator("aside"), page.locator("nav"), page.locator("[class*='sidebar']"),
						page.getByText(Pattern.compile("(?i)negocio"))));

		assertNotNull("Main application interface was not rendered.", sidebar);
		assertTrue("The sidebar navigation should be visible after login.", sidebar.isVisible());
	}

	private void openMiNegocioMenu(final Page page) {
		final Locator menu = requireVisible("Mi Negocio option", Arrays.asList(
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi\\s+negocio"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi\\s+negocio"))),
				page.getByText(Pattern.compile("(?i)mi\\s+negocio"))));

		clickAndWait(menu);
		requireVisible("Negocio section label", Arrays.asList(page.getByText(Pattern.compile("(?i)negocio"))));
		requireVisible("Agregar Negocio menu item", Arrays.asList(page.getByText(Pattern.compile("(?i)agregar\\s+negocio"))));
		requireVisible("Administrar Negocios menu item",
				Arrays.asList(page.getByText(Pattern.compile("(?i)administrar\\s+negocios"))));
	}

	private void validateAgregarNegocioModal(final Page page) {
		final Locator addBusiness = requireVisible("Agregar Negocio action",
				Arrays.asList(
						page.getByRole(AriaRole.BUTTON,
								new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s+negocio"))),
						page.getByRole(AriaRole.LINK,
								new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s+negocio"))),
						page.getByText(Pattern.compile("(?i)agregar\\s+negocio"))));
		clickAndWait(addBusiness);

		requireVisible("Crear Nuevo Negocio modal title",
				Arrays.asList(page.getByText(Pattern.compile("(?i)crear\\s+nuevo\\s+negocio"))));
		final Locator businessNameInput = requireVisible("Nombre del Negocio input",
				Arrays.asList(page.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
						page.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
						page.locator("input[name*='nombre'], input[placeholder*='Negocio']")));

		requireVisible("business quota text",
				Arrays.asList(page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios"))));
		requireVisible("Cancelar button",
				Arrays.asList(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar")))));
		requireVisible("Crear Negocio button", Arrays.asList(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s+negocio")))));

		businessNameInput.click();
		waitForUiToLoad(page);
		businessNameInput.fill("Negocio Prueba Automatización");
		waitForUiToLoad(page);
		clickAndWait(requireVisible("Cancelar button",
				Arrays.asList(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))))));
	}

	private void openAdministrarNegocios(final Page page) {
		if (!isAnyVisible(Arrays.asList(page.getByText(Pattern.compile("(?i)administrar\\s+negocios"))), SHORT_TIMEOUT_MS)) {
			openMiNegocioMenu(page);
		}

		final Locator manageBusinesses = requireVisible("Administrar Negocios option",
				Arrays.asList(
						page.getByRole(AriaRole.LINK,
								new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar\\s+negocios"))),
						page.getByRole(AriaRole.BUTTON,
								new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar\\s+negocios"))),
						page.getByText(Pattern.compile("(?i)administrar\\s+negocios"))));

		clickAndWait(manageBusinesses);
		waitForUiToLoad(page);
	}

	private void validateAdministrarNegociosView(final Page page) {
		requireVisible("Información General section", Arrays.asList(page.getByText(Pattern.compile("(?i)informaci[oó]n\\s+general"))));
		requireVisible("Detalles de la Cuenta section", Arrays.asList(page.getByText(Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta"))));
		requireVisible("Tus Negocios section", Arrays.asList(page.getByText(Pattern.compile("(?i)tus\\s+negocios"))));
		requireVisible("Sección Legal section", Arrays.asList(page.getByText(Pattern.compile("(?i)secci[oó]n\\s+legal"))));
	}

	private void validateInformacionGeneral(final Page page) {
		final Locator infoHeading = requireVisible("Información General heading",
				Arrays.asList(page.getByText(Pattern.compile("(?i)informaci[oó]n\\s+general"))));
		final String sectionText = extractClosestSectionText(infoHeading);

		assertTrue("User email should be visible in Información General.",
				Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}").matcher(sectionText).find());
		assertTrue("Expected account email should be visible.",
				isAnyVisible(Arrays.asList(page.getByText(Pattern.compile("(?i)" + Pattern.quote(googleAccountEmail)))),
						SHORT_TIMEOUT_MS));
		assertTrue("User name should be visible in Información General.", hasLikelyUserName(sectionText));

		requireVisible("BUSINESS PLAN text", Arrays.asList(page.getByText(Pattern.compile("(?i)business\\s+plan"))));
		requireVisible("Cambiar Plan button", Arrays.asList(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar\\s+plan"))),
				page.getByText(Pattern.compile("(?i)cambiar\\s+plan"))));
	}

	private void validateDetallesCuenta(final Page page) {
		requireVisible("Cuenta creada text", Arrays.asList(page.getByText(Pattern.compile("(?i)cuenta\\s+creada"))));
		requireVisible("Estado activo text", Arrays.asList(page.getByText(Pattern.compile("(?i)estado\\s+activo"))));
		requireVisible("Idioma seleccionado text", Arrays.asList(page.getByText(Pattern.compile("(?i)idioma\\s+seleccionado"))));
	}

	private void validateTusNegocios(final Page page) {
		final Locator heading = requireVisible("Tus Negocios heading",
				Arrays.asList(page.getByText(Pattern.compile("(?i)tus\\s+negocios"))));
		final String sectionText = extractClosestSectionText(heading);

		requireVisible("Agregar Negocio button",
				Arrays.asList(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s+negocio"))),
						page.getByText(Pattern.compile("(?i)agregar\\s+negocio"))));
		requireVisible("business quota text",
				Arrays.asList(page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios"))));

		final Locator listItems = page
				.locator("section:has-text('Tus Negocios') li, section:has-text('Tus Negocios') [role='row'], "
						+ "div:has-text('Tus Negocios') li, div:has-text('Tus Negocios') [role='row']");
		final boolean hasBusinessRows = listItems.count() > 0;
		final boolean hasNamedBusiness = Pattern.compile("(?i)negocio").matcher(sectionText).find();
		assertTrue("Business list should be visible in Tus Negocios.", hasBusinessRows || hasNamedBusiness);
	}

	private String validateLegalDocument(final Page page, final BrowserContext context, final String linkText,
			final Pattern headingPattern, final String screenshotName) {
		final Locator legalLink = requireVisible(linkText + " link",
				Arrays.asList(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))),
						page.getByText(Pattern.compile("(?i)" + Pattern.quote(linkText)))));
		final String appUrlBeforeNavigation = page.url();
		Page legalPage = null;

		try {
			legalPage = page.waitForPopup(new WaitForPopupOptions().setTimeout(SHORT_TIMEOUT_MS), () -> clickAndWait(legalLink));
		} catch (final PlaywrightException popupNotOpened) {
			clickAndWait(legalLink);
		}

		if (legalPage == null) {
			legalPage = page;
		}

		waitForUiToLoad(legalPage);
		validateLegalPageContent(legalPage, headingPattern);
		captureScreenshot(legalPage, screenshotName, true);
		final String legalUrl = legalPage.url();

		if (legalPage != page) {
			legalPage.close();
			page.bringToFront();
			waitForUiToLoad(page);
		} else if (!appUrlBeforeNavigation.equals(page.url())) {
			try {
				page.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			} catch (final PlaywrightException ignored) {
				// Some environments may disable history changes for legal docs.
			}
			waitForUiToLoad(page);
			ensureReturnedToApplication(context);
		}

		return legalUrl;
	}

	private void validateLegalPageContent(final Page page, final Pattern headingPattern) {
		final boolean headingVisible = isAnyVisible(Arrays.asList(
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
				page.getByText(headingPattern)), getTimeoutMs());
		assertTrue("The legal document heading should be visible.", headingVisible);

		final String bodyText = safeInnerText(page.locator("body"));
		assertTrue("The legal document should include meaningful content text.", bodyText.trim().length() > 120);
	}

	private void selectGoogleAccountIfPresent(final Page page) {
		final Locator accountOption = page.getByText(Pattern.compile("(?i)^\\s*" + Pattern.quote(googleAccountEmail) + "\\s*$"))
				.first();
		if (isVisible(accountOption, SHORT_TIMEOUT_MS)) {
			clickAndWait(accountOption);
			return;
		}

		final Locator accountHeading = page.getByText(Pattern.compile("(?i)(elige una cuenta|choose an account)")).first();
		if (isVisible(accountHeading, SHORT_TIMEOUT_MS)) {
			throw new IllegalStateException(
					"Google account selector is visible but account " + googleAccountEmail + " was not found.");
		}
	}

	private void waitForMainInterface(final Page page) {
		final long timeoutAt = System.currentTimeMillis() + getTimeoutMs();
		while (System.currentTimeMillis() < timeoutAt) {
			final boolean sidebarVisible = isAnyVisible(
					Arrays.asList(page.locator("aside"), page.locator("nav"), page.locator("[class*='sidebar']"),
							page.getByText(Pattern.compile("(?i)negocio"))),
					SHORT_TIMEOUT_MS);
			if (sidebarVisible && !page.url().contains("accounts.google.com")) {
				return;
			}
			waitForUiToLoad(page);
		}
		throw new IllegalStateException("Main application interface did not appear after Google login.");
	}

	private void clickAndWait(final Locator locator) {
		locator.first().click();
		waitForUiToLoad(locator.page());
	}

	private void waitForUiToLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// DOMContentLoaded is best effort in SPAs.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// Network idle is best effort in SPAs.
		}
		page.waitForTimeout(500);
	}

	private Locator requireVisible(final String elementName, final List<Locator> candidates) {
		final Optional<Locator> match = candidates.stream().map(Locator::first).filter(locator -> isVisible(locator, SHORT_TIMEOUT_MS)).findFirst();

		if (match.isPresent()) {
			return match.get();
		}

		throw new IllegalStateException("Unable to locate visible element: " + elementName);
	}

	private boolean isAnyVisible(final List<Locator> locators, final int timeoutMs) {
		return locators.stream().map(Locator::first).anyMatch(locator -> isVisible(locator, timeoutMs));
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return locator.isVisible();
		} catch (final TimeoutError timeoutError) {
			return false;
		} catch (final PlaywrightException playwrightException) {
			return false;
		}
	}

	private String extractClosestSectionText(final Locator heading) {
		final Locator sectionContainer = heading.locator(
				"xpath=ancestor::*[self::section or self::article or self::div][1]");
		if (sectionContainer.count() > 0) {
			final String sectionText = safeInnerText(sectionContainer.first());
			if (!sectionText.isBlank()) {
				return sectionText;
			}
		}
		return safeInnerText(heading.page().locator("body"));
	}

	private boolean hasLikelyUserName(final String sectionText) {
		final String normalized = sectionText.replace('\u00A0', ' ');
		final String[] lines = normalized.split("\\R");

		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.length() < 3) {
				continue;
			}
			if (line.contains("@")) {
				continue;
			}

			final String upper = line.toUpperCase(Locale.ROOT);
			if (upper.contains("INFORMACIÓN GENERAL") || upper.contains("INFORMACION GENERAL")
					|| upper.contains("BUSINESS PLAN") || upper.contains("CAMBIAR PLAN")) {
				continue;
			}

			if (line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}

		return false;
	}

	private String safeInnerText(final Locator locator) {
		try {
			final String value = locator.first().innerText();
			return value == null ? "" : value;
		} catch (final PlaywrightException exception) {
			return "";
		}
	}

	private Path initScreenshotDir() throws IOException {
		final String configuredDir = getConfig("saleads.evidence.dir", "SALEADS_EVIDENCE_DIR",
				Paths.get("target", "saleads-mi-negocio-evidence").toString());
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path path = Paths.get(configuredDir, timestamp);
		Files.createDirectories(path);
		return path;
	}

	private void captureScreenshot(final Page page, final String checkpointName, final boolean fullPage) {
		final Path outputPath = screenshotDir.resolve(checkpointName + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(outputPath).setFullPage(fullPage));
	}

	private void ensureReturnedToApplication(final BrowserContext context) {
		for (final Page page : context.pages()) {
			if (isAnyVisible(Arrays.asList(page.getByText(Pattern.compile("(?i)secci[oó]n\\s+legal")),
					page.getByText(Pattern.compile("(?i)informaci[oó]n\\s+general"))), SHORT_TIMEOUT_MS)) {
				page.bringToFront();
				appPage = page;
				return;
			}
		}
	}

	private int getTimeoutMs() {
		final String timeout = getConfig("saleads.timeout.ms", "SALEADS_TIMEOUT_MS", String.valueOf(DEFAULT_TIMEOUT_MS));
		try {
			return Integer.parseInt(timeout);
		} catch (final NumberFormatException ignored) {
			return DEFAULT_TIMEOUT_MS;
		}
	}

	private String getConfig(final String propertyName, final String envName, final String defaultValue) {
		final String fromProperty = System.getProperty(propertyName);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}

		final String fromEnv = System.getenv(envName);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		return defaultValue;
	}

	private boolean getBooleanConfig(final String propertyName, final String envName, final boolean defaultValue) {
		final String configured = getConfig(propertyName, envName, String.valueOf(defaultValue));
		return Boolean.parseBoolean(configured);
	}

	private String slug(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private void printFinalReport() {
		System.out.println();
		System.out.println("=== " + TEST_NAME + " FINAL REPORT ===");
		for (final String field : REPORT_FIELDS) {
			final boolean passed = Boolean.TRUE.equals(stepReport.get(field));
			System.out.println(field + ": " + (passed ? "PASS" : "FAIL"));
			if (legalUrls.containsKey(field)) {
				System.out.println("  URL: " + legalUrls.get(field));
			}
		}
		System.out.println("Evidence directory: " + screenshotDir.toAbsolutePath());
		System.out.println("=== END REPORT ===");
		System.out.println();
	}

	private interface CheckedStep {
		void run() throws Exception;
	}

	private static class BrowserSession {
		private final Browser browser;
		private final BrowserContext context;
		private final Page page;

		private BrowserSession(final Browser browser, final BrowserContext context, final Page page) {
			this.browser = browser;
			this.context = context;
			this.page = page;
		}
	}
}
