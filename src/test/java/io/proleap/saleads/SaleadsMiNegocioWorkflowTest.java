package io.proleap.saleads;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioWorkflowTest {

	private static final int SHORT_TIMEOUT_MS = 4000;
	private static final int DEFAULT_TIMEOUT_MS = 15000;
	private static final int LOGIN_TIMEOUT_MS = 90000;

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Informaci\u00f3n General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "T\u00e9rminos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Pol\u00edtica de Privacidad";

	private static final DateTimeFormatter EVIDENCE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String saleadsUrl = System.getenv("SALEADS_URL");
		final boolean runE2E = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run SaleADS E2E workflow.", runE2E);
		Assume.assumeTrue("Set SALEADS_URL to the environment login URL.", isNotBlank(saleadsUrl));

		final String googleAccountEmail = System.getenv().getOrDefault(
				"SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com");

		final LinkedHashMap<String, Boolean> statusByStep = initStatusMap();
		final LinkedHashMap<String, String> detailsByStep = new LinkedHashMap<>();
		final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();
		final Path evidenceDir = createEvidenceDirectory();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(
					new BrowserType.LaunchOptions().setHeadless(isHeadless()));
			final BrowserContext context = browser.newContext(
					new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page page = context.newPage();

			page.navigate(saleadsUrl);
			waitForUi(page);

			runStep(STEP_LOGIN, statusByStep, detailsByStep, () -> {
				loginWithGoogle(page, context, googleAccountEmail);
				assertMainInterfaceVisible(page);
				takeScreenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), true);
			});

			runStep(STEP_MI_NEGOCIO_MENU, statusByStep, detailsByStep, () -> {
				expandMiNegocioMenu(page);
				assertVisibleByText(page, Pattern.compile("(?i)^Agregar\\s+Negocio$"), DEFAULT_TIMEOUT_MS);
				assertVisibleByText(page, Pattern.compile("(?i)^Administrar\\s+Negocios$"), DEFAULT_TIMEOUT_MS);
				takeScreenshot(page, evidenceDir.resolve("02-mi-negocio-expanded.png"), true);
			});

			runStep(STEP_AGREGAR_NEGOCIO_MODAL, statusByStep, detailsByStep, () -> {
				clickByText(page, Pattern.compile("(?i)^Agregar\\s+Negocio$"));
				waitForUi(page);

				final Locator modalTitle = assertVisibleByText(page,
						Pattern.compile("(?i)^Crear\\s+Nuevo\\s+Negocio$"), DEFAULT_TIMEOUT_MS);
				final Locator businessNameInput = firstVisible(
						page.getByLabel(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")).first(),
						page.getByPlaceholder(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")).first(),
						page.locator("input[name*='negocio' i]").first(),
						page.locator("input").first());

				Assert.assertTrue("Input 'Nombre del Negocio' should be visible.",
						isVisible(businessNameInput, DEFAULT_TIMEOUT_MS));
				assertVisibleByText(page, Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios"), DEFAULT_TIMEOUT_MS);
				assertVisibleByRoleName(page, AriaRole.BUTTON, Pattern.compile("(?i)^Cancelar$"), DEFAULT_TIMEOUT_MS);
				assertVisibleByRoleName(page, AriaRole.BUTTON, Pattern.compile("(?i)^Crear\\s+Negocio$"), DEFAULT_TIMEOUT_MS);
				takeScreenshot(page, evidenceDir.resolve("03-crear-negocio-modal.png"), false);

				businessNameInput.fill("Negocio Prueba Automatizacion");
				clickByRoleName(page, AriaRole.BUTTON, Pattern.compile("(?i)^Cancelar$"));
				modalTitle.waitFor(new Locator.WaitForOptions()
						.setState(WaitForSelectorState.HIDDEN)
						.setTimeout(DEFAULT_TIMEOUT_MS));
			});

			runStep(STEP_ADMINISTRAR_NEGOCIOS, statusByStep, detailsByStep, () -> {
				ensureMiNegocioMenuExpanded(page);
				clickByText(page, Pattern.compile("(?i)^Administrar\\s+Negocios$"));
				waitForUi(page);

				assertVisibleByText(page, Pattern.compile("(?i)Informaci[o\u00f3]n\\s+General"), DEFAULT_TIMEOUT_MS);
				assertVisibleByText(page, Pattern.compile("(?i)Detalles\\s+de\\s+la\\s+Cuenta"), DEFAULT_TIMEOUT_MS);
				assertVisibleByText(page, Pattern.compile("(?i)Tus\\s+Negocios"), DEFAULT_TIMEOUT_MS);
				assertVisibleByText(page, Pattern.compile("(?i)Secci[o\u00f3]n\\s+Legal"), DEFAULT_TIMEOUT_MS);
				takeScreenshot(page, evidenceDir.resolve("04-administrar-negocios.png"), true);
			});

			runStep(STEP_INFO_GENERAL, statusByStep, detailsByStep, () -> {
				assertVisibleByText(page, Pattern.compile(Pattern.quote(googleAccountEmail), Pattern.CASE_INSENSITIVE),
						DEFAULT_TIMEOUT_MS);
				assertVisibleByText(page, Pattern.compile("(?i)BUSINESS\\s+PLAN"), DEFAULT_TIMEOUT_MS);
				assertVisibleByRoleName(page, AriaRole.BUTTON, Pattern.compile("(?i)Cambiar\\s+Plan"), DEFAULT_TIMEOUT_MS);

				final String expectedUserName = System.getenv("SALEADS_EXPECTED_USER_NAME");
				if (isNotBlank(expectedUserName)) {
					assertVisibleByText(page, Pattern.compile(Pattern.quote(expectedUserName), Pattern.CASE_INSENSITIVE),
							DEFAULT_TIMEOUT_MS);
				} else {
					final String bodyText = page.locator("body").innerText();
					Assert.assertTrue("A user name-like value should be visible in Informacion General.",
							Pattern.compile("(?m)^[A-Z][\\p{L}'-]+(?:\\s+[A-Z][\\p{L}'-]+)+$").matcher(bodyText).find());
				}
			});

			runStep(STEP_DETALLES_CUENTA, statusByStep, detailsByStep, () -> {
				assertVisibleByText(page, Pattern.compile("(?i)Cuenta\\s+creada"), DEFAULT_TIMEOUT_MS);
				assertVisibleByText(page, Pattern.compile("(?i)Estado\\s+activo"), DEFAULT_TIMEOUT_MS);
				assertVisibleByText(page, Pattern.compile("(?i)Idioma\\s+seleccionado"), DEFAULT_TIMEOUT_MS);
			});

			runStep(STEP_TUS_NEGOCIOS, statusByStep, detailsByStep, () -> {
				assertVisibleByText(page, Pattern.compile("(?i)Tus\\s+Negocios"), DEFAULT_TIMEOUT_MS);
				assertVisibleByRoleName(page, AriaRole.BUTTON, Pattern.compile("(?i)Agregar\\s+Negocio"), DEFAULT_TIMEOUT_MS);
				assertVisibleByText(page, Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios"), DEFAULT_TIMEOUT_MS);
			});

			runStep(STEP_TERMINOS, statusByStep, detailsByStep, () -> {
				final String legalUrl = validateLegalLink(
						page,
						context,
						Pattern.compile("(?i)T[e\u00e9]rminos\\s+y\\s+Condiciones"),
						Pattern.compile("(?i)T[e\u00e9]rminos\\s+y\\s+Condiciones"),
						evidenceDir.resolve("05-terminos-y-condiciones.png"));
				legalUrls.put(STEP_TERMINOS, legalUrl);
			});

			runStep(STEP_PRIVACIDAD, statusByStep, detailsByStep, () -> {
				final String legalUrl = validateLegalLink(
						page,
						context,
						Pattern.compile("(?i)Pol[i\u00ed]tica\\s+de\\s+Privacidad"),
						Pattern.compile("(?i)Pol[i\u00ed]tica\\s+de\\s+Privacidad"),
						evidenceDir.resolve("06-politica-de-privacidad.png"));
				legalUrls.put(STEP_PRIVACIDAD, legalUrl);
			});
		}

		final String finalReport = buildFinalReport(statusByStep, detailsByStep, legalUrls, evidenceDir);
		System.out.println(finalReport);
		Assert.assertTrue(finalReport, allStepsPassed(statusByStep));
	}

	private void loginWithGoogle(final Page page, final BrowserContext context, final String googleAccountEmail) {
		if (isVisible(page.getByText(Pattern.compile("(?i)^Mi\\s+Negocio$")).first(), SHORT_TIMEOUT_MS)
				|| isVisible(page.getByText(Pattern.compile("(?i)^Negocio$")).first(), SHORT_TIMEOUT_MS)) {
			return;
		}

		final Locator googleLoginButton = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s+sesi[o\u00f3]n\\s+con\\s+google|google)")))
						.first(),
				page.getByText(Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s+sesi[o\u00f3]n\\s+con\\s+google)")).first(),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s+sesi[o\u00f3]n\\s+con\\s+google|google)")))
						.first());

		Page googlePage = null;
		try {
			googlePage = context.waitForPage(googleLoginButton::click,
					new BrowserContext.WaitForPageOptions().setTimeout(8000));
		} catch (PlaywrightException popupTimeout) {
			waitForUi(page);
		}

		if (googlePage != null) {
			waitForUi(googlePage);
			selectGoogleAccountIfVisible(googlePage, googleAccountEmail);
			try {
				googlePage.waitForClose(new Page.WaitForCloseOptions().setTimeout(LOGIN_TIMEOUT_MS));
			} catch (PlaywrightException ignored) {
				page.waitForTimeout(500);
			}
			page.bringToFront();
		} else {
			selectGoogleAccountIfVisible(page, googleAccountEmail);
		}

		assertMainInterfaceVisible(page);
	}

	private void selectGoogleAccountIfVisible(final Page page, final String googleAccountEmail) {
		final Locator accountOption = page.getByText(Pattern.compile(Pattern.quote(googleAccountEmail), Pattern.CASE_INSENSITIVE)).first();
		if (isVisible(accountOption, DEFAULT_TIMEOUT_MS)) {
			accountOption.click();
			waitForUi(page);
		}
	}

	private void expandMiNegocioMenu(final Page page) {
		clickByText(page, Pattern.compile("(?i)^Negocio$"));
		clickByText(page, Pattern.compile("(?i)^Mi\\s+Negocio$"));
	}

	private void ensureMiNegocioMenuExpanded(final Page page) {
		final Locator administrarNegocios = page.getByText(Pattern.compile("(?i)^Administrar\\s+Negocios$")).first();
		if (!isVisible(administrarNegocios, SHORT_TIMEOUT_MS)) {
			expandMiNegocioMenu(page);
		}
	}

	private String validateLegalLink(
			final Page appPage,
			final BrowserContext context,
			final Pattern linkPattern,
			final Pattern headingPattern,
			final Path screenshotPath) {
		final Locator legalLink = firstVisible(
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)).first(),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(linkPattern)).first(),
				appPage.getByText(linkPattern).first());

		Page legalPage = null;
		try {
			legalPage = context.waitForPage(legalLink::click, new BrowserContext.WaitForPageOptions().setTimeout(8000));
		} catch (PlaywrightException noNewPage) {
			waitForUi(appPage);
		}

		final Page targetPage = legalPage != null ? legalPage : appPage;
		waitForUi(targetPage);
		assertVisibleByText(targetPage, headingPattern, DEFAULT_TIMEOUT_MS);

		final String legalBodyText = targetPage.locator("body").innerText();
		Assert.assertTrue("Expected visible legal content text.",
				legalBodyText != null && legalBodyText.trim().length() > 120);
		takeScreenshot(targetPage, screenshotPath, true);

		final String finalUrl = targetPage.url();
		if (legalPage != null) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void assertMainInterfaceVisible(final Page page) {
		final boolean sidebarVisible = isVisible(page.getByText(Pattern.compile("(?i)^Negocio$")).first(), LOGIN_TIMEOUT_MS)
				|| isVisible(page.getByText(Pattern.compile("(?i)^Mi\\s+Negocio$")).first(), LOGIN_TIMEOUT_MS);
		Assert.assertTrue("Main app interface and left sidebar should be visible after login.", sidebarVisible);
	}

	private void clickByText(final Page page, final Pattern pattern) {
		final Locator locator = firstVisible(page.getByText(pattern).first());
		locator.click();
		waitForUi(page);
	}

	private void clickByRoleName(final Page page, final AriaRole role, final Pattern pattern) {
		final Locator locator = firstVisible(page.getByRole(role, new Page.GetByRoleOptions().setName(pattern)).first());
		locator.click();
		waitForUi(page);
	}

	private Locator assertVisibleByText(final Page page, final Pattern pattern, final int timeoutMs) {
		final Locator locator = page.getByText(pattern).first();
		Assert.assertTrue("Expected visible text matching pattern: " + pattern.pattern(), isVisible(locator, timeoutMs));
		return locator;
	}

	private Locator assertVisibleByRoleName(final Page page, final AriaRole role, final Pattern pattern, final int timeoutMs) {
		final Locator locator = page.getByRole(role, new Page.GetByRoleOptions().setName(pattern)).first();
		Assert.assertTrue("Expected visible element by role " + role + " and pattern: " + pattern.pattern(),
				isVisible(locator, timeoutMs));
		return locator;
	}

	private Locator firstVisible(final Locator... locators) {
		for (final Locator locator : locators) {
			if (locator != null && isVisible(locator, DEFAULT_TIMEOUT_MS)) {
				return locator;
			}
		}

		throw new AssertionError("No visible element found among provided candidates.");
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// Some views do not reach network idle due to polling; DOM ready is sufficient.
		}
		page.waitForTimeout(350);
	}

	private void takeScreenshot(final Page page, final Path path, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private Path createEvidenceDirectory() throws IOException {
		final String runId = LocalDateTime.now().format(EVIDENCE_FORMAT);
		final Path dir = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(dir);
		return dir;
	}

	private LinkedHashMap<String, Boolean> initStatusMap() {
		final LinkedHashMap<String, Boolean> statusByStep = new LinkedHashMap<>();
		statusByStep.put(STEP_LOGIN, false);
		statusByStep.put(STEP_MI_NEGOCIO_MENU, false);
		statusByStep.put(STEP_AGREGAR_NEGOCIO_MODAL, false);
		statusByStep.put(STEP_ADMINISTRAR_NEGOCIOS, false);
		statusByStep.put(STEP_INFO_GENERAL, false);
		statusByStep.put(STEP_DETALLES_CUENTA, false);
		statusByStep.put(STEP_TUS_NEGOCIOS, false);
		statusByStep.put(STEP_TERMINOS, false);
		statusByStep.put(STEP_PRIVACIDAD, false);
		return statusByStep;
	}

	private boolean allStepsPassed(final Map<String, Boolean> statusByStep) {
		for (final Boolean value : statusByStep.values()) {
			if (!Boolean.TRUE.equals(value)) {
				return false;
			}
		}
		return true;
	}

	private String buildFinalReport(
			final Map<String, Boolean> statusByStep,
			final Map<String, String> detailsByStep,
			final Map<String, String> legalUrls,
			final Path evidenceDir) {
		final StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio Workflow Final Report\n");
		for (final Map.Entry<String, Boolean> stepResult : statusByStep.entrySet()) {
			report.append("- ")
					.append(stepResult.getKey())
					.append(": ")
					.append(Boolean.TRUE.equals(stepResult.getValue()) ? "PASS" : "FAIL");
			final String detail = detailsByStep.get(stepResult.getKey());
			if (isNotBlank(detail) && !"OK".equals(detail)) {
				report.append(" (").append(detail).append(")");
			}
			report.append('\n');
		}

		if (!legalUrls.isEmpty()) {
			report.append("Final URLs\n");
			for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
				report.append("- ").append(legalUrl.getKey()).append(": ").append(legalUrl.getValue()).append('\n');
			}
		}

		report.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		return report.toString();
	}

	private void runStep(
			final String stepName,
			final Map<String, Boolean> statusByStep,
			final Map<String, String> detailsByStep,
			final StepAction stepAction) {
		try {
			stepAction.run();
			statusByStep.put(stepName, true);
			detailsByStep.put(stepName, "OK");
		} catch (Throwable throwable) {
			statusByStep.put(stepName, false);
			detailsByStep.put(stepName, collapseMessage(throwable));
		}
	}

	private String collapseMessage(final Throwable throwable) {
		final String rawMessage = throwable.getMessage();
		if (!isNotBlank(rawMessage)) {
			return throwable.getClass().getSimpleName();
		}

		final String singleLine = rawMessage.replace('\n', ' ').replace('\r', ' ').trim();
		if (singleLine.length() <= 220) {
			return singleLine;
		}
		return singleLine.substring(0, 220) + "...";
	}

	private boolean isHeadless() {
		return !"false".equalsIgnoreCase(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
	}

	private boolean isNotBlank(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
