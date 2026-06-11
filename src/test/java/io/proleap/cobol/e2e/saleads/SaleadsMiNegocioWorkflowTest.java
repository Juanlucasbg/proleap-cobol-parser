package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioWorkflowTest {

	private static final String LOGIN = "Login";
	private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL = "Información General";
	private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS = "Tus Negocios";
	private static final String TERMINOS = "Términos y Condiciones";
	private static final String POLITICA = "Política de Privacidad";

	private static final Pattern LOGIN_BUTTON_PATTERN = Pattern.compile(
			"(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|continuar\\s*con\\s*google|google)");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*negocio\\s*$");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*mi\\s*negocio\\s*$");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*agregar\\s*negocio\\s*$");
	private static final Pattern ADMIN_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*administrar\\s*negocios\\s*$");
	private static final Pattern INFO_GENERAL_PATTERN = Pattern.compile("(?i)informaci[oó]n\\s*general");
	private static final Pattern DETALLES_CUENTA_PATTERN = Pattern.compile("(?i)detalles\\s*de\\s*la\\s*cuenta");
	private static final Pattern TUS_NEGOCIOS_PATTERN = Pattern.compile("(?i)tus\\s*negocios");
	private static final Pattern SECCION_LEGAL_PATTERN = Pattern.compile("(?i)secci[oó]n\\s*legal");
	private static final Pattern TERMINOS_PATTERN = Pattern.compile("(?i)t[eé]rminos\\s*y\\s*condiciones");
	private static final Pattern POLITICA_PATTERN = Pattern.compile("(?i)pol[ií]tica\\s*de\\s*privacidad");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue("Enable this test with -Dsaleads.e2e.enabled=true.",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

		final String startUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"Set saleads.login.url (or SALEADS_LOGIN_URL) to the current environment login page. The test never hardcodes a domain.",
				startUrl != null);

		final String accountEmail = firstNonBlank(System.getProperty("saleads.google.account.email"),
				System.getenv("SALEADS_GOOGLE_ACCOUNT_EMAIL"), "juanlucasbarbiergarzon@gmail.com");
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));

		final Path screenshotDir = buildScreenshotDirectory();
		final Map<String, String> report = initializeFailReport();
		final Map<String, String> evidenceUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(150));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 1024));
			final Page appPage = context.newPage();
			appPage.navigate(startUrl);
			waitForUi(appPage);

			final boolean loginOk = runStep(report, LOGIN,
					() -> stepLoginWithGoogle(appPage, accountEmail, screenshotDir));
			final boolean miNegocioMenuOk = loginOk
					&& runStep(report, MI_NEGOCIO_MENU, () -> stepOpenMiNegocioMenu(appPage, screenshotDir));
			final boolean agregarNegocioOk = miNegocioMenuOk
					&& runStep(report, AGREGAR_NEGOCIO_MODAL, () -> stepAgregarNegocioModal(appPage, screenshotDir));
			final boolean administrarNegociosOk = miNegocioMenuOk
					&& runStep(report, ADMINISTRAR_NEGOCIOS_VIEW, () -> stepAdministrarNegocios(appPage, screenshotDir));

			if (administrarNegociosOk) {
				runStep(report, INFORMACION_GENERAL, () -> stepInformacionGeneral(appPage));
				runStep(report, DETALLES_CUENTA, () -> stepDetallesCuenta(appPage));
				runStep(report, TUS_NEGOCIOS, () -> stepTusNegocios(appPage));
				runStep(report, TERMINOS,
						() -> stepLegalLink(appPage, TERMINOS_PATTERN, TERMINOS_PATTERN, "08_terminos", screenshotDir,
								evidenceUrls, "Términos y Condiciones URL"));
				runStep(report, POLITICA,
						() -> stepLegalLink(appPage, POLITICA_PATTERN, POLITICA_PATTERN, "09_politica", screenshotDir,
								evidenceUrls, "Política de Privacidad URL"));
			}

			printFinalReport(report, evidenceUrls, screenshotDir);

			final List<String> failed = report.entrySet().stream().filter(entry -> !"PASS".equals(entry.getValue()))
					.map(Map.Entry::getKey).collect(Collectors.toList());

			assertTrue("Workflow completed with failures in: " + failed + ". See screenshots under " + screenshotDir,
					failed.isEmpty() && agregarNegocioOk);
		}
	}

	private void stepLoginWithGoogle(final Page appPage, final String accountEmail, final Path screenshotDir) {
		if (!isAnyVisible(appPage, 2_000, getByButtonName(appPage, LOGIN_BUTTON_PATTERN),
				appPage.getByText(LOGIN_BUTTON_PATTERN).first())) {
			assertTrue("Main interface did not appear and no Google login button was visible.",
					isAnyVisible(appPage, 8_000, appPage.locator("aside"), appPage.locator("nav"),
							appPage.getByText(NEGOCIO_PATTERN).first()));
		} else {
			final Locator loginButton = firstVisible(appPage, 8_000, getByButtonName(appPage, LOGIN_BUTTON_PATTERN),
					appPage.getByText(LOGIN_BUTTON_PATTERN).first());
			final Page googlePage = clickAndMaybeCapturePopup(appPage, loginButton);
			final Page authPage = googlePage != null ? googlePage : appPage;
			waitForUi(authPage);
			selectGoogleAccountIfVisible(authPage, accountEmail);
		}

		waitForUi(appPage);
		assertAnyVisible(appPage, 20_000, "Main interface did not load after Google login.", appPage.locator("aside"),
				appPage.locator("nav"), appPage.getByText(NEGOCIO_PATTERN).first());
		assertAnyVisible(appPage, 20_000, "Left sidebar is not visible after login.",
				appPage.getByText(NEGOCIO_PATTERN).first(), appPage.getByText(MI_NEGOCIO_PATTERN).first());

		captureScreenshot(appPage, screenshotDir, "01_dashboard_loaded", true);
	}

	private void stepOpenMiNegocioMenu(final Page appPage, final Path screenshotDir) {
		clickByVisibleText(appPage, NEGOCIO_PATTERN);
		clickByVisibleText(appPage, MI_NEGOCIO_PATTERN);

		assertAnyVisible(appPage, 10_000, "Mi Negocio submenu did not expand.", appPage.getByText(AGREGAR_NEGOCIO_PATTERN),
				appPage.getByText(ADMIN_NEGOCIO_PATTERN));
		assertVisible(appPage.getByText(AGREGAR_NEGOCIO_PATTERN).first(), "Agregar Negocio is not visible.");
		assertVisible(appPage.getByText(ADMIN_NEGOCIO_PATTERN).first(), "Administrar Negocios is not visible.");

		captureScreenshot(appPage, screenshotDir, "02_mi_negocio_menu_expanded", true);
	}

	private void stepAgregarNegocioModal(final Page appPage, final Path screenshotDir) {
		clickByVisibleText(appPage, AGREGAR_NEGOCIO_PATTERN);
		assertVisible(firstVisible(appPage, 10_000, appPage.getByRole(AriaRole.HEADING,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s*nuevo\\s*negocio")))), "Modal title not found.");
		assertAnyVisible(appPage, 10_000, "Nombre del Negocio field is not visible.",
				appPage.getByLabel(Pattern.compile("(?i)nombre\\s*del\\s*negocio")), appPage.getByPlaceholder(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
				appPage.locator("input"));
		assertVisible(appPage.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")).first(),
				"Usage text \"Tienes 2 de 3 negocios\" is not visible.");
		assertVisible(getByButtonName(appPage, Pattern.compile("(?i)^\\s*cancelar\\s*$")).first(),
				"Cancelar button is missing.");
		assertVisible(getByButtonName(appPage, Pattern.compile("(?i)^\\s*crear\\s*negocio\\s*$")).first(),
				"Crear Negocio button is missing.");

		captureScreenshot(appPage, screenshotDir, "03_agregar_negocio_modal", true);

		final Locator nameField = firstVisible(appPage, 3_000,
				appPage.getByLabel(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
				appPage.getByPlaceholder(Pattern.compile("(?i)nombre\\s*del\\s*negocio")), appPage.locator("input"));
		nameField.click();
		nameField.fill("Negocio Prueba Automatizacion");
		clickByVisibleText(appPage, Pattern.compile("(?i)^\\s*cancelar\\s*$"));
		waitForUi(appPage);
	}

	private void stepAdministrarNegocios(final Page appPage, final Path screenshotDir) {
		if (!isAnyVisible(appPage, 2_000, appPage.getByText(ADMIN_NEGOCIO_PATTERN).first())) {
			clickByVisibleText(appPage, MI_NEGOCIO_PATTERN);
		}
		clickByVisibleText(appPage, ADMIN_NEGOCIO_PATTERN);

		assertAnyVisible(appPage, 20_000, "Informacion General section not found.",
				appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(INFO_GENERAL_PATTERN)),
				appPage.getByText(INFO_GENERAL_PATTERN));
		assertAnyVisible(appPage, 20_000, "Detalles de la Cuenta section not found.",
				appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(DETALLES_CUENTA_PATTERN)),
				appPage.getByText(DETALLES_CUENTA_PATTERN));
		assertAnyVisible(appPage, 20_000, "Tus Negocios section not found.",
				appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TUS_NEGOCIOS_PATTERN)),
				appPage.getByText(TUS_NEGOCIOS_PATTERN));
		assertAnyVisible(appPage, 20_000, "Seccion Legal section not found.",
				appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(SECCION_LEGAL_PATTERN)),
				appPage.getByText(SECCION_LEGAL_PATTERN));

		captureScreenshot(appPage, screenshotDir, "04_administrar_negocios", true);
	}

	private void stepInformacionGeneral(final Page appPage) {
		assertAnyVisible(appPage, 10_000, "Informacion General heading is not visible.",
				appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(INFO_GENERAL_PATTERN)),
				appPage.getByText(INFO_GENERAL_PATTERN));
		assertTrue("User email is not visible.", isAnyVisible(appPage, 8_000, appPage.getByText(EMAIL_PATTERN).first()));
		assertAnyVisible(appPage, 8_000, "BUSINESS PLAN text is not visible.",
				appPage.getByText(Pattern.compile("(?i)business\\s*plan")));
		assertVisible(getByButtonName(appPage, Pattern.compile("(?i)cambiar\\s*plan")).first(),
				"Cambiar Plan button is not visible.");
		assertTrue("A visible user name-like value was not detected.", hasVisibleNameLikeText(appPage));
	}

	private void stepDetallesCuenta(final Page appPage) {
		assertAnyVisible(appPage, 10_000, "\"Cuenta creada\" is not visible.",
				appPage.getByText(Pattern.compile("(?i)cuenta\\s*creada")));
		assertAnyVisible(appPage, 10_000, "\"Estado activo\" is not visible.",
				appPage.getByText(Pattern.compile("(?i)estado\\s*activo")));
		assertAnyVisible(appPage, 10_000, "\"Idioma seleccionado\" is not visible.",
				appPage.getByText(Pattern.compile("(?i)idioma\\s*seleccionado")));
	}

	private void stepTusNegocios(final Page appPage) {
		assertAnyVisible(appPage, 10_000, "Tus Negocios section is not visible.",
				appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TUS_NEGOCIOS_PATTERN)),
				appPage.getByText(TUS_NEGOCIOS_PATTERN));
		assertVisible(getByButtonName(appPage, AGREGAR_NEGOCIO_PATTERN).first(), "Agregar Negocio button is missing.");
		assertAnyVisible(appPage, 10_000, "\"Tienes 2 de 3 negocios\" text is not visible.",
				appPage.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")));
		assertTrue("Business list content is not visible.",
				isAnyVisible(appPage, 5_000, appPage.locator("li"), appPage.locator("tr"), appPage.locator("[role='row']")));
	}

	private void stepLegalLink(final Page appPage, final Pattern linkPattern, final Pattern headingPattern,
			final String screenshotFilePrefix, final Path screenshotDir, final Map<String, String> evidenceUrls,
			final String urlKey) {
		appPage.bringToFront();
		final String appUrlBeforeClick = appPage.url();

		final Locator link = firstVisible(appPage, 10_000, appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)),
				appPage.getByText(linkPattern).first());
		final Page legalPage = clickAndMaybeCapturePopup(appPage, link);
		final Page activeLegalPage = legalPage != null ? legalPage : appPage;
		waitForUi(activeLegalPage);

		assertAnyVisible(activeLegalPage, 12_000, "Expected legal heading was not found.",
				activeLegalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
				activeLegalPage.getByText(headingPattern));
		assertTrue("Legal content appears empty.",
				activeLegalPage.locator("p, article, section, div").allInnerTexts().stream()
						.map(String::trim).anyMatch(text -> text.length() > 80));

		captureScreenshot(activeLegalPage, screenshotDir, screenshotFilePrefix, true);
		evidenceUrls.put(urlKey, activeLegalPage.url());

		if (legalPage != null) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!appPage.url().equals(appUrlBeforeClick)) {
			appPage.goBack();
			waitForUi(appPage);
		}
	}

	private void selectGoogleAccountIfVisible(final Page authPage, final String accountEmail) {
		final Locator accountOption = authPage.getByText(Pattern.compile("(?i)" + Pattern.quote(accountEmail))).first();
		if (isVisible(accountOption, 8_000)) {
			accountOption.click();
			waitForUi(authPage);
		}
	}

	private boolean runStep(final Map<String, String> report, final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, "PASS");
			return true;
		} catch (final Throwable throwable) {
			report.put(stepName, "FAIL");
			System.out.println("[SALEADS_E2E] " + stepName + " failed: " + throwable.getMessage());
			return false;
		}
	}

	private void clickByVisibleText(final Page page, final Pattern textPattern) {
		final Locator target = firstVisible(page, 10_000, page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(textPattern)), page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(textPattern)), page.getByText(textPattern).first());
		clickAndWaitForUi(page, target);
	}

	private void clickAndWaitForUi(final Page page, final Locator target) {
		target.click(new Locator.ClickOptions().setTimeout(10_000));
		waitForUi(page);
	}

	private Page clickAndMaybeCapturePopup(final Page page, final Locator target) {
		try {
			return page.waitForPopup(() -> target.click(new Locator.ClickOptions().setTimeout(10_000)));
		} catch (final PlaywrightException ex) {
			target.click(new Locator.ClickOptions().setTimeout(10_000));
			waitForUi(page);
			return null;
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15_000));
		} catch (final PlaywrightException ignored) {
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
		} catch (final PlaywrightException ignored) {
		}
		page.waitForTimeout(500);
	}

	private void assertAnyVisible(final Page page, final double timeoutMs, final String errorMessage,
			final Locator... locators) {
		assertTrue(errorMessage, isAnyVisible(page, timeoutMs, locators));
	}

	private boolean isAnyVisible(final Page page, final double timeoutMs, final Locator... locators) {
		try {
			firstVisible(page, timeoutMs, locators);
			return true;
		} catch (final AssertionError ex) {
			return false;
		}
	}

	private Locator firstVisible(final Page page, final double timeoutMs, final Locator... locators) {
		final double perLocatorTimeoutMs = Math.max(500, timeoutMs / Math.max(1, locators.length));
		for (final Locator locator : locators) {
			if (locator == null) {
				continue;
			}
			final Locator candidate = locator.first();
			if (isVisible(candidate, perLocatorTimeoutMs)) {
				return candidate;
			}
		}
		throw new AssertionError("No expected element became visible after waiting " + timeoutMs + "ms.");
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return locator.isVisible();
		} catch (final PlaywrightException ex) {
			return false;
		}
	}

	private void assertVisible(final Locator locator, final String errorMessage) {
		assertTrue(errorMessage, isVisible(locator, 8_000));
	}

	private Locator getByButtonName(final Page page, final Pattern namePattern) {
		return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(namePattern));
	}

	private boolean hasVisibleNameLikeText(final Page appPage) {
		final String pageText = appPage.locator("body").innerText();
		final List<String> candidateLines = Arrays.stream(pageText.split("\\R")).map(String::trim)
				.filter(line -> line.length() >= 5).filter(line -> !EMAIL_PATTERN.matcher(line).find())
				.filter(line -> !line.matches("(?i).*informaci[oó]n\\s*general.*"))
				.filter(line -> !line.matches("(?i).*business\\s*plan.*")).filter(line -> !line.matches("(?i).*cambiar\\s*plan.*"))
				.filter(line -> line.matches("^[\\p{L}][\\p{L} .'-]+$")).collect(Collectors.toList());
		return !candidateLines.isEmpty();
	}

	private Path buildScreenshotDirectory() throws Exception {
		final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		final Path screenshotDir = Paths.get("target", "saleads-e2e-screenshots", formatter.format(new Date()));
		Files.createDirectories(screenshotDir);
		return screenshotDir;
	}

	private void captureScreenshot(final Page page, final Path screenshotDir, final String filePrefix,
			final boolean fullPage) {
		final Path screenshotPath = screenshotDir.resolve(filePrefix + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		System.out.println("[SALEADS_E2E] Screenshot: " + screenshotPath.toAbsolutePath());
	}

	private Map<String, String> initializeFailReport() {
		final Map<String, String> report = new LinkedHashMap<>();
		report.put(LOGIN, "FAIL");
		report.put(MI_NEGOCIO_MENU, "FAIL");
		report.put(AGREGAR_NEGOCIO_MODAL, "FAIL");
		report.put(ADMINISTRAR_NEGOCIOS_VIEW, "FAIL");
		report.put(INFORMACION_GENERAL, "FAIL");
		report.put(DETALLES_CUENTA, "FAIL");
		report.put(TUS_NEGOCIOS, "FAIL");
		report.put(TERMINOS, "FAIL");
		report.put(POLITICA, "FAIL");
		return report;
	}

	private void printFinalReport(final Map<String, String> report, final Map<String, String> evidenceUrls,
			final Path screenshotDir) {
		System.out.println("======================================================");
		System.out.println("SALEADS MI NEGOCIO - FINAL REPORT");
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
		report.forEach((field, status) -> System.out.println(field + ": " + status));
		evidenceUrls.forEach((key, value) -> System.out.println(key + ": " + value));
		System.out.println("======================================================");
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
