package io.proleap.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioWorkflowTest {

	private static final String ENABLE_PROPERTY = "saleads.e2e.enabled";
	private static final String LOGIN_URL_PROPERTY = "saleads.login.url";
	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String GOOGLE_ACCOUNT_PROPERTY = "saleads.google.account";
	private static final String GOOGLE_ACCOUNT_ENV = "SALEADS_GOOGLE_ACCOUNT";
	private static final String HEADLESS_PROPERTY = "saleads.e2e.headless";
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private static final String LOGIN_STEP = "Login";
	private static final String MENU_STEP = "Mi Negocio menu";
	private static final String MODAL_STEP = "Agregar Negocio modal";
	private static final String ADMIN_STEP = "Administrar Negocios view";
	private static final String INFO_STEP = "Información General";
	private static final String ACCOUNT_STEP = "Detalles de la Cuenta";
	private static final String BUSINESSES_STEP = "Tus Negocios";
	private static final String TERMS_STEP = "Términos y Condiciones";
	private static final String PRIVACY_STEP = "Política de Privacidad";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue("Set -D" + ENABLE_PROPERTY + "=true to run this external E2E test.",
				Boolean.parseBoolean(System.getProperty(ENABLE_PROPERTY, "false")));

		final String loginUrl = firstNonBlank(System.getProperty(LOGIN_URL_PROPERTY), System.getenv(LOGIN_URL_ENV));
		Assume.assumeTrue("Provide the SaleADS login page URL via -D" + LOGIN_URL_PROPERTY + " or " + LOGIN_URL_ENV + ".",
				loginUrl != null && !loginUrl.isBlank());

		final String googleAccount = firstNonBlank(System.getProperty(GOOGLE_ACCOUNT_PROPERTY),
				System.getenv(GOOGLE_ACCOUNT_ENV), DEFAULT_GOOGLE_ACCOUNT);
		final boolean headless = Boolean.parseBoolean(System.getProperty(HEADLESS_PROPERTY, "true"));

		final Path evidenceDir = createEvidenceDirectory();
		final Map<String, String> report = initReport();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page page = context.newPage();

			page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(page);

			final boolean loginOk = runStep(report, LOGIN_STEP,
					() -> stepLoginWithGoogle(page, googleAccount, evidenceDir.resolve("01-dashboard.png")));

			final boolean menuOk;
			if (loginOk) {
				menuOk = runStep(report, MENU_STEP,
						() -> stepOpenMiNegocioMenu(page, evidenceDir.resolve("02-mi-negocio-menu.png")));
			} else {
				menuOk = false;
				markBlocked(report, MENU_STEP, "Login failed.");
			}

			if (menuOk) {
				runStep(report, MODAL_STEP,
						() -> stepValidateAgregarNegocioModal(page, evidenceDir.resolve("03-agregar-negocio-modal.png")));
			} else {
				markBlocked(report, MODAL_STEP, "Mi Negocio menu step failed.");
			}

			final boolean adminOk;
			if (menuOk) {
				adminOk = runStep(report, ADMIN_STEP,
						() -> stepOpenAdministrarNegocios(page, evidenceDir.resolve("04-administrar-negocios.png")));
			} else {
				adminOk = false;
				markBlocked(report, ADMIN_STEP, "Mi Negocio menu step failed.");
			}

			if (adminOk) {
				runStep(report, INFO_STEP, () -> stepValidateInformacionGeneral(page, googleAccount));
				runStep(report, ACCOUNT_STEP, () -> stepValidateDetallesCuenta(page));
				runStep(report, BUSINESSES_STEP, () -> stepValidateTusNegocios(page));
				runStep(report, TERMS_STEP,
						() -> stepValidateLegalLink(page, "Términos y Condiciones", "08-terminos-y-condiciones.png", legalUrls));
				runStep(report, PRIVACY_STEP,
						() -> stepValidateLegalLink(page, "Política de Privacidad", "09-politica-de-privacidad.png", legalUrls));
			} else {
				markBlocked(report, INFO_STEP, "Administrar Negocios view step failed.");
				markBlocked(report, ACCOUNT_STEP, "Administrar Negocios view step failed.");
				markBlocked(report, BUSINESSES_STEP, "Administrar Negocios view step failed.");
				markBlocked(report, TERMS_STEP, "Administrar Negocios view step failed.");
				markBlocked(report, PRIVACY_STEP, "Administrar Negocios view step failed.");
			}

			writeFinalReport(evidenceDir, report, legalUrls);
			assertNoFailures(report);
		}
	}

	private void stepLoginWithGoogle(final Page page, final String googleAccount, final Path screenshotPath) {
		final Locator loginButton = firstVisibleLocator(page, List.of("Sign in with Google", "Iniciar sesión con Google",
				"Inicia sesión con Google", "Continuar con Google", "Google"), 8000);
		Assert.assertNotNull("Login button or 'Sign in with Google' was not found.", loginButton);

		final Page authPopup = clickAndWaitForPopupIfAny(page, loginButton);
		if (authPopup != null) {
			waitForUi(authPopup);
			selectGoogleAccountIfVisible(authPopup, googleAccount);
			waitForUi(page);
		} else {
			selectGoogleAccountIfVisible(page, googleAccount);
			waitForUi(page);
		}

		// Validate main interface and left sidebar are visible.
		Assert.assertTrue("Main application interface is not visible.",
				isVisible(page.locator("main, [role='main']").first(), 8000) || hasAnyVisibleText(page, List.of("Dashboard", "Inicio"), 4000));
		Assert.assertTrue("Left sidebar navigation is not visible.",
				isVisible(page.locator("aside, nav").first(), 8000) || hasAnyVisibleText(page, List.of("Negocio", "Mi Negocio"), 8000));

		takeScreenshot(page, screenshotPath, false);
	}

	private void stepOpenMiNegocioMenu(final Page page, final Path screenshotPath) {
		clickByVisibleText(page, "Negocio");
		clickByVisibleText(page, "Mi Negocio");

		assertTextVisible(page, "Agregar Negocio", 10000);
		assertTextVisible(page, "Administrar Negocios", 10000);
		takeScreenshot(page, screenshotPath, false);
	}

	private void stepValidateAgregarNegocioModal(final Page page, final Path screenshotPath) {
		clickByVisibleText(page, "Agregar Negocio");

		assertTextVisible(page, "Crear Nuevo Negocio", 10000);
		assertTextVisible(page, "Nombre del Negocio", 10000);
		assertTextVisible(page, "Tienes 2 de 3 negocios", 10000);
		assertTextVisible(page, "Cancelar", 10000);
		assertTextVisible(page, "Crear Negocio", 10000);
		takeScreenshot(page, screenshotPath, false);

		final Locator nombreInput = firstVisibleLocator(page,
				List.of("Nombre del Negocio"), 3000, true);
		if (nombreInput != null) {
			nombreInput.fill("Negocio Prueba Automatización");
		}

		clickByVisibleText(page, "Cancelar");
		Assert.assertFalse("Agregar Negocio modal did not close after clicking 'Cancelar'.",
				hasVisibleText(page, "Crear Nuevo Negocio", 3000));
	}

	private void stepOpenAdministrarNegocios(final Page page, final Path screenshotPath) {
		if (!hasVisibleText(page, "Administrar Negocios", 3000)) {
			clickByVisibleText(page, "Mi Negocio");
		}

		clickByVisibleText(page, "Administrar Negocios");
		assertTextVisible(page, "Información General", 15000);
		assertTextVisible(page, "Detalles de la Cuenta", 15000);
		assertTextVisible(page, "Tus Negocios", 15000);
		assertTextVisible(page, "Sección Legal", 15000);
		takeScreenshot(page, screenshotPath, true);
	}

	private void stepValidateInformacionGeneral(final Page page, final String googleAccount) {
		final Locator section = findSection(page, "Información General");
		Assert.assertTrue("Section 'Información General' was not found.", isVisible(section, 10000));

		assertTextVisible(page, googleAccount, 10000);
		assertTextVisible(page, "BUSINESS PLAN", 10000);
		assertTextVisible(page, "Cambiar Plan", 10000);

		final String sectionText = normalizeSpaces(section.innerText());
		final String reduced = sectionText.replace("Información General", "").replace(googleAccount, "")
				.replace("BUSINESS PLAN", "").replace("Cambiar Plan", "").trim();
		Assert.assertTrue("User name is not visible in 'Información General'.", reduced.length() > 2);
	}

	private void stepValidateDetallesCuenta(final Page page) {
		findSection(page, "Detalles de la Cuenta").waitFor(
				new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
		assertTextVisible(page, "Cuenta creada", 10000);
		assertTextVisible(page, "Estado activo", 10000);
		assertTextVisible(page, "Idioma seleccionado", 10000);
	}

	private void stepValidateTusNegocios(final Page page) {
		final Locator section = findSection(page, "Tus Negocios");
		Assert.assertTrue("Section 'Tus Negocios' was not found.", isVisible(section, 10000));
		assertTextVisible(section, "Agregar Negocio", 10000);
		assertTextVisible(section, "Tienes 2 de 3 negocios", 10000);

		final String text = normalizeSpaces(section.innerText());
		final String reduced = text.replace("Tus Negocios", "").replace("Agregar Negocio", "")
				.replace("Tienes 2 de 3 negocios", "").trim();
		Assert.assertTrue("Business list is not visible in 'Tus Negocios'.", reduced.length() > 5);
	}

	private void stepValidateLegalLink(final Page appPage, final String linkText, final String screenshotFileName,
			final Map<String, String> legalUrls) {
		clickByVisibleText(appPage, "Sección Legal");

		final Locator legalLink = firstVisibleLocator(appPage, List.of(linkText), 8000);
		Assert.assertNotNull("Legal link was not found: " + linkText, legalLink);

		final Page legalPopup = clickAndWaitForPopupIfAny(appPage, legalLink);
		final Page legalPage = legalPopup != null ? legalPopup : appPage;
		waitForUi(legalPage);

		assertTextVisible(legalPage, linkText, 12000);
		final String bodyText = normalizeSpaces(legalPage.locator("body").innerText());
		Assert.assertTrue("Legal content text is not visible for '" + linkText + "'.", bodyText.length() > 250);

		takeScreenshot(legalPage, createEvidenceDirectoryIfNeeded(screenshotFileName), true);
		legalUrls.put(linkText, legalPage.url());

		if (legalPopup != null) {
			legalPopup.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(appPage);
		}
	}

	private boolean runStep(final Map<String, String> report, final String step, final StepAction action) {
		try {
			action.run();
			report.put(step, "PASS");
			return true;
		} catch (final Throwable error) {
			report.put(step, "FAIL - " + firstLine(error.getMessage()));
			return false;
		}
	}

	private void markBlocked(final Map<String, String> report, final String step, final String reason) {
		if (!report.containsKey(step) || "PENDING".equals(report.get(step))) {
			report.put(step, "FAIL - " + reason);
		}
	}

	private Map<String, String> initReport() {
		final Map<String, String> report = new LinkedHashMap<>();
		report.put(LOGIN_STEP, "PENDING");
		report.put(MENU_STEP, "PENDING");
		report.put(MODAL_STEP, "PENDING");
		report.put(ADMIN_STEP, "PENDING");
		report.put(INFO_STEP, "PENDING");
		report.put(ACCOUNT_STEP, "PENDING");
		report.put(BUSINESSES_STEP, "PENDING");
		report.put(TERMS_STEP, "PENDING");
		report.put(PRIVACY_STEP, "PENDING");
		return report;
	}

	private void writeFinalReport(final Path evidenceDir, final Map<String, String> report, final Map<String, String> legalUrls)
			throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Workflow - Final Report").append(System.lineSeparator());
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		builder.append(System.lineSeparator());
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}

		if (!legalUrls.isEmpty()) {
			builder.append(System.lineSeparator()).append("Captured legal URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> url : legalUrls.entrySet()) {
				builder.append("- ").append(url.getKey()).append(": ").append(url.getValue()).append(System.lineSeparator());
			}
		}

		final Path reportFile = evidenceDir.resolve("10-final-report.txt");
		Files.writeString(reportFile, builder.toString());
		System.out.println(builder);
	}

	private void assertNoFailures(final Map<String, String> report) {
		final StringBuilder failures = new StringBuilder();
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			if (!entry.getValue().startsWith("PASS")) {
				failures.append(entry.getKey()).append(" => ").append(entry.getValue()).append(System.lineSeparator());
			}
		}
		Assert.assertTrue("One or more workflow validations failed:" + System.lineSeparator() + failures,
				failures.length() == 0);
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(TS_FORMAT);
		final Path evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private Path createEvidenceDirectoryIfNeeded(final String screenshotFileName) {
		return Paths.get("target", "saleads-evidence").resolve(screenshotFileName);
	}

	private void takeScreenshot(final Page page, final Path path, final boolean fullPage) {
		try {
			Files.createDirectories(path.getParent());
			page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
		} catch (final Exception error) {
			throw new AssertionError("Failed to capture screenshot: " + path + " -> " + firstLine(error.getMessage()));
		}
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7000));
		} catch (final TimeoutError ignored) {
			// Some UIs keep long-polling requests open; continue after a safe wait.
		}
		page.waitForTimeout(500);
	}

	private void clickByVisibleText(final Page page, final String text) {
		final Locator locator = firstVisibleLocator(page, List.of(text), 10000);
		Assert.assertNotNull("Clickable element was not found by text: " + text, locator);
		clickAndWait(locator, page);
	}

	private void clickAndWait(final Locator locator, final Page page) {
		locator.scrollIntoViewIfNeeded();
		locator.click();
		waitForUi(page);
	}

	private Page clickAndWaitForPopupIfAny(final Page page, final Locator locator) {
		try {
			return page.waitForPopup(() -> clickAndWait(locator, page), new Page.WaitForPopupOptions().setTimeout(8000));
		} catch (final TimeoutError ignored) {
			return null;
		}
	}

	private void selectGoogleAccountIfVisible(final Page page, final String googleAccount) {
		final Locator account = firstVisibleLocator(page, List.of(googleAccount), 6000);
		if (account != null) {
			clickAndWait(account, page);
		}
	}

	private Locator findSection(final Page page, final String heading) {
		final Pattern pattern = Pattern.compile("(?i).*" + Pattern.quote(heading) + ".*");
		return page.locator("section, article, div").filter(new Locator.FilterOptions().setHasText(pattern)).first();
	}

	private Locator firstVisibleLocator(final Page page, final List<String> texts, final double timeoutMs) {
		return firstVisibleLocator(page, texts, timeoutMs, false);
	}

	private Locator firstVisibleLocator(final Page page, final List<String> texts, final double timeoutMs,
			final boolean inputFirst) {
		for (final String text : texts) {
			final Pattern containsText = Pattern.compile("(?i).*" + Pattern.quote(text) + ".*");
			final Locator[] candidates = inputFirst
					? new Locator[] { page.getByLabel(containsText).first(), page.getByPlaceholder(containsText).first(),
							page.getByText(containsText).first() }
					: new Locator[] { page.getByText(containsText).first(), page.getByLabel(containsText).first(),
							page.getByPlaceholder(containsText).first() };
			for (final Locator candidate : candidates) {
				if (isVisible(candidate, timeoutMs)) {
					return candidate;
				}
			}
		}
		return null;
	}

	private void assertTextVisible(final Page page, final String text, final double timeoutMs) {
		final Locator locator = firstVisibleLocator(page, List.of(text), timeoutMs);
		Assert.assertNotNull("Expected text is not visible: " + text, locator);
	}

	private void assertTextVisible(final Locator scope, final String text, final double timeoutMs) {
		final Pattern pattern = Pattern.compile("(?i).*" + Pattern.quote(text) + ".*");
		final Locator locator = scope.getByText(pattern).first();
		Assert.assertTrue("Expected text is not visible in section: " + text, isVisible(locator, timeoutMs));
	}

	private boolean hasVisibleText(final Page page, final String text, final double timeoutMs) {
		return firstVisibleLocator(page, List.of(text), timeoutMs) != null;
	}

	private boolean hasAnyVisibleText(final Page page, final List<String> texts, final double timeoutMs) {
		return firstVisibleLocator(page, texts, timeoutMs) != null;
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final TimeoutError ignored) {
			return false;
		}
	}

	private String normalizeSpaces(final String value) {
		return value == null ? "" : value.replaceAll("\\s+", " ").trim();
	}

	private String firstLine(final String message) {
		if (message == null || message.isBlank()) {
			return "No error message available.";
		}
		final int lineBreak = message.indexOf('\n');
		return lineBreak >= 0 ? message.substring(0, lineBreak).trim() : message.trim();
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
