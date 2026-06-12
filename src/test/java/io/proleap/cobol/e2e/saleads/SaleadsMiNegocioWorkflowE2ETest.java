package io.proleap.cobol.e2e.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Full E2E flow for SaleADS "Mi Negocio" module.
 *
 * <p>This test intentionally does not hardcode any SaleADS domain.
 * Configure the target environment with:
 * <ul>
 *   <li>-Dsaleads.e2e.enabled=true (required to run)</li>
 *   <li>-Dsaleads.login.url=https://your-env/login (optional but practical for isolated runs)</li>
 *   <li>-Dsaleads.e2e.headless=false (optional for debugging)</li>
 * </ul>
 *
 * <p>If saleads.login.url is not provided, the test assumes the browser has already
 * navigated to a SaleADS login page.
 */
public class SaleadsMiNegocioWorkflowE2ETest {

	private static final long SHORT_TIMEOUT_MS = 4_000;
	private static final long DEFAULT_TIMEOUT_MS = 15_000;
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern LOGIN_WITH_GOOGLE_PATTERN = Pattern
			.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)");
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[A-Za-z]{2,}");
	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final boolean enabled = booleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", false);
		Assume.assumeTrue(
				"Set -Dsaleads.e2e.enabled=true (or SALEADS_E2E_ENABLED=true) to run this environment-dependent UI test.",
				enabled);

		final Path evidenceDir = createEvidenceDirectory();
		final Map<String, StepResult> report = initializeReport();
		final StringBuilder metadata = new StringBuilder();
		Session session = null;

		try {
			session = Session.start();
			final Page page = session.page;
			final String loginUrl = stringConfig("saleads.login.url", "SALEADS_LOGIN_URL");

			if (hasText(loginUrl)) {
				page.navigate(loginUrl);
			}

			waitForUi(page);

			runStep(report, "Login", () -> {
				loginWithGoogle(page);
				handleGoogleAccountSelectorIfVisible(page);
				assertMainInterfaceVisible(page);
				assertSidebarVisible(page);
				captureScreenshot(page, evidenceDir, "01-dashboard-loaded.png", true);
			});

			runStep(report, "Mi Negocio menu", () -> {
				openMiNegocioMenu(page);
				assertVisible(page.getByText(Pattern.compile("^Agregar Negocio$", Pattern.CASE_INSENSITIVE)).first(),
						"'Agregar Negocio' should be visible");
				assertVisible(page.getByText(Pattern.compile("^Administrar Negocios$", Pattern.CASE_INSENSITIVE)).first(),
						"'Administrar Negocios' should be visible");
				captureScreenshot(page, evidenceDir, "02-mi-negocio-menu-expanded.png", true);
			});

			runStep(report, "Agregar Negocio modal", () -> {
				final Locator agregarNegocio = byVisibleText(page, "Agregar Negocio");
				clickAndWait(page, agregarNegocio);

				assertVisible(byVisibleText(page, "Crear Nuevo Negocio"),
						"Modal title 'Crear Nuevo Negocio' must be visible");
				final Locator nombreInput = firstVisible(
						"input 'Nombre del Negocio'",
						page.getByLabel(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
						page.locator("input[placeholder*='Nombre del Negocio']"),
						page.locator("input[name*='negocio']"));
				assertVisible(nombreInput, "'Nombre del Negocio' input must exist");
				assertVisible(byVisibleText(page, "Tienes 2 de 3 negocios"),
						"'Tienes 2 de 3 negocios' must be visible");
				assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first(),
						"'Cancelar' button must be visible");
				assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")).first(),
						"'Crear Negocio' button must be visible");
				captureScreenshot(page, evidenceDir, "03-agregar-negocio-modal.png", true);

				nombreInput.fill("Negocio Prueba Automatización");
				clickAndWait(page, page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first());
				assertHidden(byVisibleText(page, "Crear Nuevo Negocio"), "Modal should close after 'Cancelar'");
			});

			runStep(report, "Administrar Negocios view", () -> {
				openMiNegocioMenu(page);
				clickAndWait(page, byVisibleText(page, "Administrar Negocios"));

				assertVisible(byVisibleText(page, "Información General"),
						"'Información General' section should exist");
				assertVisible(byVisibleText(page, "Detalles de la Cuenta"),
						"'Detalles de la Cuenta' section should exist");
				assertVisible(byVisibleText(page, "Tus Negocios"),
						"'Tus Negocios' section should exist");
				assertVisible(
						firstVisible("legal section",
								byVisibleText(page, "Sección Legal"),
								byVisibleText(page, "Términos y Condiciones")),
						"'Sección Legal' should exist");
				captureScreenshot(page, evidenceDir, "04-administrar-negocios-full.png", true);
			});

			runStep(report, "Información General", () -> {
				final String infoText = sectionText(page, "Información General");
				assertContainsRegex(infoText, EMAIL_PATTERN, "User email should be visible");
				Assert.assertTrue("User name should be visible in 'Información General'",
						containsLikelyUserName(infoText));
				assertVisible(byVisibleText(page, "BUSINESS PLAN"), "'BUSINESS PLAN' text should be visible");
				assertVisible(byVisibleText(page, "Cambiar Plan"), "'Cambiar Plan' button should be visible");
			});

			runStep(report, "Detalles de la Cuenta", () -> {
				final String detailsText = sectionText(page, "Detalles de la Cuenta");
				assertContainsIgnoreCase(detailsText, "Cuenta creada", "'Cuenta creada' should be visible");
				assertContainsIgnoreCase(detailsText, "Estado activo", "'Estado activo' should be visible");
				assertContainsIgnoreCase(detailsText, "Idioma seleccionado", "'Idioma seleccionado' should be visible");
			});

			runStep(report, "Tus Negocios", () -> {
				final String negociosText = sectionText(page, "Tus Negocios");
				Assert.assertFalse("Business list should be visible in 'Tus Negocios' section",
						negociosText.trim().isEmpty());
				assertVisible(
						firstVisible("'Agregar Negocio' button",
								page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
								page.getByText(Pattern.compile("^Agregar Negocio$", Pattern.CASE_INSENSITIVE))),
						"'Agregar Negocio' button should exist");
				assertContainsIgnoreCase(negociosText, "Tienes 2 de 3 negocios",
						"'Tienes 2 de 3 negocios' should be visible");
			});

			runStep(report, "Términos y Condiciones", () -> {
				final String termsUrl = validateLegalDocument(page, "Términos y Condiciones", "Términos y Condiciones",
						evidenceDir, "05-terminos-y-condiciones.png");
				metadata.append("Términos y Condiciones URL: ").append(termsUrl).append(System.lineSeparator());
			});

			runStep(report, "Política de Privacidad", () -> {
				final String privacyUrl = validateLegalDocument(page, "Política de Privacidad", "Política de Privacidad",
						evidenceDir, "06-politica-de-privacidad.png");
				metadata.append("Política de Privacidad URL: ").append(privacyUrl).append(System.lineSeparator());
			});
		} finally {
			if (session != null) {
				session.close();
			}
			writeFinalReport(evidenceDir, report, metadata.toString());
		}

		final List<String> failures = report.entrySet().stream()
				.filter(entry -> !entry.getValue().pass)
				.map(entry -> entry.getKey() + " => FAIL (" + entry.getValue().detail + ")")
				.collect(Collectors.toList());
		Assert.assertTrue(
				"SaleADS Mi Negocio workflow failed validations:\n" + String.join("\n", failures),
				failures.isEmpty());
	}

	private static void loginWithGoogle(final Page page) {
		final Locator loginButton = firstVisible(
				"login button or 'Sign in with Google'",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(LOGIN_WITH_GOOGLE_PATTERN)),
				page.getByText(LOGIN_WITH_GOOGLE_PATTERN));
		clickAndWait(page, loginButton);
	}

	private static void handleGoogleAccountSelectorIfVisible(final Page appPage) {
		final long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
		while (System.currentTimeMillis() < deadline) {
			for (final Page candidatePage : appPage.context().pages()) {
				final Locator accountOption = candidatePage.getByText(GOOGLE_ACCOUNT).first();
				if (isVisible(accountOption, 600)) {
					clickAndWait(candidatePage, accountOption);
					return;
				}
			}
			appPage.waitForTimeout(400);
		}
	}

	private static void assertMainInterfaceVisible(final Page page) {
		assertVisible(
				firstVisible("main app interface",
						page.locator("main"),
						page.locator("aside"),
						page.locator("[role='main']"),
						page.locator("[role='navigation']")),
				"Main application interface should be visible");
	}

	private static void assertSidebarVisible(final Page page) {
		assertVisible(
				firstVisible("left sidebar navigation",
						page.locator("aside"),
						page.locator("[role='navigation']"),
						page.locator("nav")),
				"Left sidebar navigation should be visible");
	}

	private static void openMiNegocioMenu(final Page page) {
		final Locator negocioSection = page.getByText(Pattern.compile("^Negocio$", Pattern.CASE_INSENSITIVE)).first();
		if (isVisible(negocioSection, SHORT_TIMEOUT_MS)) {
			clickAndWait(page, negocioSection);
		}

		final Locator administrar = page.getByText(Pattern.compile("^Administrar Negocios$", Pattern.CASE_INSENSITIVE))
				.first();
		if (!isVisible(administrar, SHORT_TIMEOUT_MS)) {
			clickAndWait(page, byVisibleText(page, "Mi Negocio"));
		}
	}

	private static String validateLegalDocument(
			final Page appPage,
			final String linkText,
			final String expectedHeading,
			final Path evidenceDir,
			final String screenshotName) {
		openMiNegocioMenu(appPage);
		final Locator legalLink = firstVisible(
				"legal link '" + linkText + "'",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkText)),
				appPage.getByText(linkText));

		Page legalPage = appPage;
		boolean openedPopup = false;

		try {
			legalPage = appPage.waitForPopup(
					() -> legalLink.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS)),
					new Page.WaitForPopupOptions().setTimeout(6_000));
			openedPopup = true;
			waitForUi(legalPage);
		} catch (final PlaywrightException ignored) {
			waitForUi(appPage);
		}

		assertVisible(
				firstVisible("heading '" + expectedHeading + "'",
						legalPage.getByRole(AriaRole.HEADING,
								new Page.GetByRoleOptions().setName(Pattern.compile(expectedHeading, Pattern.CASE_INSENSITIVE))),
						legalPage.getByText(Pattern.compile(expectedHeading, Pattern.CASE_INSENSITIVE))),
				"Legal heading '" + expectedHeading + "' should be visible");

		final String bodyText = legalPage.locator("body").innerText();
		Assert.assertTrue("Legal content text should be visible for '" + expectedHeading + "'",
				bodyText != null && bodyText.trim().length() > 120);

		captureScreenshot(legalPage, evidenceDir, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (openedPopup) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private static String sectionText(final Page page, final String sectionHeading) {
		final Locator heading = byVisibleText(page, sectionHeading);
		assertVisible(heading, "Section heading '" + sectionHeading + "' should be visible");

		final Locator container = page.locator(
				"xpath=//*[normalize-space()='" + sectionHeading + "']/ancestor::*[self::section or self::article or self::div][1]")
				.first();
		if (isVisible(container, SHORT_TIMEOUT_MS)) {
			return container.innerText();
		}

		return page.locator("body").innerText();
	}

	private static boolean containsLikelyUserName(final String input) {
		final String[] lines = input.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}

			final String lower = line.toLowerCase(Locale.ROOT);
			if (lower.contains("información general")
					|| lower.contains("business plan")
					|| lower.contains("cambiar plan")
					|| lower.contains("cuenta")
					|| lower.contains("idioma")
					|| lower.contains("@")) {
				continue;
			}

			if (line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private static void runStep(
			final Map<String, StepResult> report,
			final String reportField,
			final CheckedRunnable stepAction) {
		try {
			stepAction.run();
			report.put(reportField, StepResult.pass("Validated"));
		} catch (final Throwable throwable) {
			report.put(reportField, StepResult.fail(safeDetail(throwable)));
		}
	}

	private static String safeDetail(final Throwable throwable) {
		final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
		return message.replace('\n', ' ').trim();
	}

	private static Map<String, StepResult> initializeReport() {
		final Map<String, StepResult> report = new LinkedHashMap<>();
		for (final String field : REPORT_FIELDS) {
			report.put(field, StepResult.fail("Not executed"));
		}
		return report;
	}

	private static void writeFinalReport(
			final Path evidenceDir,
			final Map<String, StepResult> report,
			final String metadata) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Workflow Report").append(System.lineSeparator());
		builder.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		builder.append(System.lineSeparator());
		for (final String field : REPORT_FIELDS) {
			final StepResult result = report.get(field);
			builder.append("- ").append(field).append(": ")
					.append(result.pass ? "PASS" : "FAIL");
			if (hasText(result.detail)) {
				builder.append(" (").append(result.detail).append(")");
			}
			builder.append(System.lineSeparator());
		}
		if (hasText(metadata)) {
			builder.append(System.lineSeparator()).append(metadata);
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), builder.toString());
	}

	private static Path createEvidenceDirectory() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path outputDir = Paths.get("target", "saleads-e2e", timestamp);
		Files.createDirectories(outputDir);
		return outputDir;
	}

	private static void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUi(page);
	}

	private static void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// Some environments keep polling APIs; a DOM + short settle is enough for this workflow.
		}
		page.waitForTimeout(300);
	}

	private static void captureScreenshot(
			final Page page,
			final Path evidenceDir,
			final String screenshotFileName,
			final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(evidenceDir.resolve(screenshotFileName))
				.setFullPage(fullPage));
	}

	private static Locator byVisibleText(final Page page, final String text) {
		return firstVisible(
				"text '" + text + "'",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)),
				page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(text)),
				page.getByText(Pattern.compile("^" + Pattern.quote(text) + "$", Pattern.CASE_INSENSITIVE)),
				page.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)));
	}

	private static Locator firstVisible(final String description, final Locator... locators) {
		for (final Locator locator : locators) {
			if (locator == null) {
				continue;
			}
			try {
				final Locator candidate = locator.first();
				candidate.waitFor(new Locator.WaitForOptions()
						.setState(WaitForSelectorState.VISIBLE)
						.setTimeout(SHORT_TIMEOUT_MS));
				return candidate;
			} catch (final PlaywrightException ignored) {
				// Try next locator strategy.
			}
		}
		throw new AssertionError("Could not find visible element for: " + description);
	}

	private static boolean isVisible(final Locator locator, final long timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout(timeoutMs));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static void assertVisible(final Locator locator, final String message) {
		Assert.assertTrue(message, isVisible(locator, DEFAULT_TIMEOUT_MS));
	}

	private static void assertHidden(final Locator locator, final String message) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.HIDDEN)
					.setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (final PlaywrightException e) {
			Assert.fail(message + " (" + safeDetail(e) + ")");
		}
	}

	private static void assertContainsIgnoreCase(
			final String input,
			final String expected,
			final String message) {
		Assert.assertTrue(message, input.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT)));
	}

	private static void assertContainsRegex(
			final String input,
			final Pattern pattern,
			final String message) {
		final Matcher matcher = pattern.matcher(input);
		Assert.assertTrue(message, matcher.find());
	}

	private static boolean booleanConfig(
			final String systemProperty,
			final String envVariable,
			final boolean defaultValue) {
		final String raw = stringConfig(systemProperty, envVariable);
		if (!hasText(raw)) {
			return defaultValue;
		}
		return Boolean.parseBoolean(raw.trim());
	}

	private static String stringConfig(final String systemProperty, final String envVariable) {
		final String propertyValue = System.getProperty(systemProperty);
		if (hasText(propertyValue)) {
			return propertyValue;
		}
		return System.getenv(envVariable);
	}

	private static boolean hasText(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static class StepResult {
		final boolean pass;
		final String detail;

		private StepResult(final boolean pass, final String detail) {
			this.pass = pass;
			this.detail = detail;
		}

		static StepResult pass(final String detail) {
			return new StepResult(true, detail);
		}

		static StepResult fail(final String detail) {
			return new StepResult(false, detail);
		}
	}

	private static class Session implements AutoCloseable {
		final Playwright playwright;
		final Browser browser;
		final BrowserContext context;
		final Page page;

		private Session(
				final Playwright playwright,
				final Browser browser,
				final BrowserContext context,
				final Page page) {
			this.playwright = playwright;
			this.browser = browser;
			this.context = context;
			this.page = page;
		}

		static Session start() {
			final Playwright playwright = Playwright.create();
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
					.setHeadless(booleanConfig("saleads.e2e.headless", "SALEADS_E2E_HEADLESS", true)));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
			final Page page = context.newPage();
			return new Session(playwright, browser, context, page);
		}

		@Override
		public void close() {
			context.close();
			browser.close();
			playwright.close();
		}
	}
}
