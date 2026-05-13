package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

	private static final String LOGIN_STEP = "Login";
	private static final String MI_NEGOCIO_STEP = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_STEP = "Agregar Negocio modal";
	private static final String ADMIN_NEGOCIOS_STEP = "Administrar Negocios view";
	private static final String INFO_GENERAL_STEP = "Informaci\u00f3n General";
	private static final String DETALLES_CUENTA_STEP = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS_STEP = "Tus Negocios";
	private static final String TERMINOS_STEP = "T\u00e9rminos y Condiciones";
	private static final String PRIVACIDAD_STEP = "Pol\u00edtica de Privacidad";

	private static final String LOGIN_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Path SCREENSHOT_DIR = Paths.get("target", "saleads-evidence");

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = readConfig("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL (or -DSALEADS_LOGIN_URL) to the SaleADS login page for the current environment.",
				loginUrl != null && !loginUrl.isBlank());

		Files.createDirectories(SCREENSHOT_DIR);

		final Map<String, Boolean> report = new LinkedHashMap<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (final Playwright playwright = Playwright.create()) {
			final boolean headless = Boolean.parseBoolean(readConfigOrDefault("SALEADS_HEADLESS", "true"));

			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser
					.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page page = context.newPage();

			page.navigate(loginUrl);
			waitForUi(page);

			report.put(LOGIN_STEP, executeStep(() -> performGoogleLogin(context, page)));

			if (Boolean.TRUE.equals(report.get(LOGIN_STEP))) {
				report.put(MI_NEGOCIO_STEP, executeStep(() -> openMiNegocioMenu(page)));
				report.put(AGREGAR_NEGOCIO_STEP, executeStep(() -> validateAgregarNegocioModal(page)));
				report.put(ADMIN_NEGOCIOS_STEP, executeStep(() -> openAdministrarNegocios(page)));
				report.put(INFO_GENERAL_STEP, executeStep(() -> validateInformacionGeneral(page)));
				report.put(DETALLES_CUENTA_STEP, executeStep(() -> validateDetallesCuenta(page)));
				report.put(TUS_NEGOCIOS_STEP, executeStep(() -> validateTusNegocios(page)));
				report.put(TERMINOS_STEP, executeStep(() -> {
					final String termsUrl = openLegalDocumentAndReturn(page, context,
							new String[] { "T\u00e9rminos y Condiciones", "Terminos y Condiciones" },
							new String[] { "T\u00e9rminos y Condiciones", "Terminos y Condiciones" },
							"08-terminos-y-condiciones");
					legalUrls.put(TERMINOS_STEP, termsUrl);
				}));
				report.put(PRIVACIDAD_STEP, executeStep(() -> {
					final String privacyUrl = openLegalDocumentAndReturn(page, context,
							new String[] { "Pol\u00edtica de Privacidad", "Politica de Privacidad" },
							new String[] { "Pol\u00edtica de Privacidad", "Politica de Privacidad" },
							"09-politica-de-privacidad");
					legalUrls.put(PRIVACIDAD_STEP, privacyUrl);
				}));
			} else {
				report.put(MI_NEGOCIO_STEP, false);
				report.put(AGREGAR_NEGOCIO_STEP, false);
				report.put(ADMIN_NEGOCIOS_STEP, false);
				report.put(INFO_GENERAL_STEP, false);
				report.put(DETALLES_CUENTA_STEP, false);
				report.put(TUS_NEGOCIOS_STEP, false);
				report.put(TERMINOS_STEP, false);
				report.put(PRIVACIDAD_STEP, false);
			}
		}

		printReport(report, legalUrls);

		final boolean allPassed = report.values().stream().allMatch(Boolean::booleanValue);
		assertTrue("Some Mi Negocio validations failed. Review screenshots in " + SCREENSHOT_DIR.toString(), allPassed);
	}

	private void performGoogleLogin(final BrowserContext context, final Page page) {
		final Locator loginButton = waitForGoogleButton(page);
		Page potentialPopup = null;

		try {
			potentialPopup = context.waitForPage(loginButton::click, new BrowserContext.WaitForPageOptions().setTimeout(7000));
		} catch (final PlaywrightException ignored) {
			loginButton.click();
		}

		waitForUi(page);
		if (potentialPopup != null) {
			waitForUi(potentialPopup);
			selectGoogleAccountIfVisible(potentialPopup);
			try {
				potentialPopup.close();
			} catch (final PlaywrightException ignored) {
				// If popup closes itself after account selection we can ignore this.
			}
		} else {
			selectGoogleAccountIfVisible(page);
		}

		waitForAnyText(page, 30000, "Negocio", "Mi Negocio");
		final Locator sidebar = page.locator("aside, nav").first();
		assertTrue("Expected left sidebar navigation to be visible after login.", isVisible(sidebar, 10000));
		takeScreenshot(page, "01-dashboard-loaded");
	}

	private void openMiNegocioMenu(final Page page) {
		waitForAnyText(page, 20000, "Negocio");
		clickByVisibleText(page, "Negocio");
		clickByVisibleText(page, "Mi Negocio");

		waitForAnyText(page, 15000, "Agregar Negocio");
		waitForAnyText(page, 15000, "Administrar Negocios");
		takeScreenshot(page, "02-mi-negocio-expanded");
	}

	private void validateAgregarNegocioModal(final Page page) {
		clickByVisibleText(page, "Agregar Negocio");

		waitForAnyText(page, 15000, "Crear Nuevo Negocio");
		waitForAnyText(page, 10000, "Nombre del Negocio");
		waitForAnyText(page, 10000, "Tienes 2 de 3 negocios");

		final Locator cancelar = page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))).first();
		final Locator crearNegocio = page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear negocio"))).first();
		assertTrue("Expected 'Cancelar' button in modal.", isVisible(cancelar, 10000));
		assertTrue("Expected 'Crear Negocio' button in modal.", isVisible(crearNegocio, 10000));

		takeScreenshot(page, "03-crear-nuevo-negocio-modal");

		final Locator negocioInput = findBusinessNameInput(page);
		negocioInput.click();
		negocioInput.fill("Negocio Prueba Automatizacion");

		cancelar.click();
		waitForUi(page);
	}

	private void openAdministrarNegocios(final Page page) {
		if (!isAnyTextVisible(page, "Administrar Negocios")) {
			clickByVisibleText(page, "Mi Negocio");
		}

		clickByVisibleText(page, "Administrar Negocios");
		waitForUi(page);

		waitForAnyText(page, 20000, "Informaci\u00f3n General", "Informacion General");
		waitForAnyText(page, 20000, "Detalles de la Cuenta");
		waitForAnyText(page, 20000, "Tus Negocios");
		waitForAnyText(page, 20000, "Secci\u00f3n Legal", "Seccion Legal");
		takeScreenshot(page, "04-administrar-negocios");
	}

	private void validateInformacionGeneral(final Page page) {
		waitForAnyText(page, 10000, "Informaci\u00f3n General", "Informacion General");

		final String expectedName = readConfig("SALEADS_EXPECTED_USER_NAME");
		if (expectedName != null && !expectedName.isBlank()) {
			waitForAnyText(page, 10000, expectedName);
		} else {
			assertTrue("Expected a visible user name label/value in Informacion General.",
					isAnyTextVisible(page, "Nombre", "Usuario", "Name"));
		}

		final Locator emailCandidate = page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first();
		assertTrue("Expected a visible user email in Informacion General.", isVisible(emailCandidate, 10000));
		waitForAnyText(page, 10000, "BUSINESS PLAN");
		waitForAnyText(page, 10000, "Cambiar Plan");
	}

	private void validateDetallesCuenta(final Page page) {
		waitForAnyText(page, 10000, "Detalles de la Cuenta");
		waitForAnyText(page, 10000, "Cuenta creada");
		waitForAnyText(page, 10000, "Estado activo");
		waitForAnyText(page, 10000, "Idioma seleccionado");
	}

	private void validateTusNegocios(final Page page) {
		waitForAnyText(page, 10000, "Tus Negocios");
		waitForAnyText(page, 10000, "Agregar Negocio");
		waitForAnyText(page, 10000, "Tienes 2 de 3 negocios");
	}

	private String openLegalDocumentAndReturn(final Page appPage, final BrowserContext context, final String[] linkCandidates,
			final String[] headingCandidates, final String screenshotName) {
		Page targetPage = null;
		try {
			targetPage = context.waitForPage(() -> clickByVisibleText(appPage, linkCandidates),
					new BrowserContext.WaitForPageOptions().setTimeout(7000));
		} catch (final PlaywrightException ignored) {
			clickByVisibleText(appPage, linkCandidates);
		}

		if (targetPage == null) {
			targetPage = appPage;
		}

		waitForUi(targetPage);
		waitForAnyText(targetPage, 20000, headingCandidates);

		final String bodyText = targetPage.locator("body").innerText();
		assertTrue("Expected legal content text to be visible for " + Arrays.toString(headingCandidates) + ".",
				bodyText != null && bodyText.trim().length() > 120);

		takeScreenshot(targetPage, screenshotName);

		final String finalUrl = targetPage.url();

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				appPage.goBack();
				waitForUi(appPage);
			} catch (final PlaywrightException ignored) {
				// Some deployments can open legal docs in same tab without history navigation.
			}
		}

		return finalUrl;
	}

	private Locator findBusinessNameInput(final Page page) {
		final Locator byLabel = page.getByLabel("Nombre del Negocio",
				new Page.GetByLabelOptions().setExact(false)).first();
		if (isVisible(byLabel, 3000)) {
			return byLabel;
		}

		final Locator byPlaceholder = page.getByPlaceholder("Nombre del Negocio",
				new Page.GetByPlaceholderOptions().setExact(false)).first();
		if (isVisible(byPlaceholder, 3000)) {
			return byPlaceholder;
		}

		throw new AssertionError("Could not locate the 'Nombre del Negocio' input.");
	}

	private Locator waitForGoogleButton(final Page page) {
		final Locator roleButton = page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(
						Pattern.compile("(?i)(sign in with google|iniciar sesi[o\u00f3]n con google|continuar con google|google)")))
				.first();
		if (isVisible(roleButton, 10000)) {
			return roleButton;
		}

		return waitForAnyText(page, 10000, "Sign in with Google", "Iniciar sesi\u00f3n con Google", "Continuar con Google",
				"Google");
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		final Locator account = page.getByText(LOGIN_EMAIL, new Page.GetByTextOptions().setExact(false)).first();
		if (isVisible(account, 8000)) {
			account.click();
			waitForUi(page);
		}
	}

	private Locator waitForAnyText(final Page page, final double timeoutMs, final String... candidates) {
		final double eachTimeout = Math.max(750, timeoutMs / Math.max(1, candidates.length));
		PlaywrightException lastException = null;

		for (final String candidate : candidates) {
			final Locator locator = page.getByText(candidate, new Page.GetByTextOptions().setExact(false)).first();
			try {
				locator.waitFor(new Locator.WaitForOptions().setTimeout(eachTimeout));
				return locator;
			} catch (final PlaywrightException ex) {
				lastException = ex;
			}
		}

		throw new AssertionError("Could not find any of the expected texts: " + Arrays.toString(candidates), lastException);
	}

	private void clickByVisibleText(final Page page, final String... candidates) {
		final Locator target = waitForAnyText(page, 15000, candidates);
		target.click();
		waitForUi(page);
	}

	private boolean isAnyTextVisible(final Page page, final String... candidates) {
		for (final String candidate : candidates) {
			final Locator locator = page.getByText(candidate, new Page.GetByTextOptions().setExact(false)).first();
			if (isVisible(locator, 1000)) {
				return true;
			}
		}

		return false;
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7000));
		} catch (final PlaywrightException ignored) {
			// Some SPA transitions never reach strict network-idle; DOM ready + short delay is enough.
		}
		page.waitForTimeout(500);
	}

	private void takeScreenshot(final Page page, final String name) {
		final String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
		page.screenshot(new Page.ScreenshotOptions().setPath(SCREENSHOT_DIR.resolve(normalized + ".png")).setFullPage(true));
	}

	private boolean executeStep(final StepAction stepAction) {
		try {
			stepAction.run();
			return true;
		} catch (final Throwable throwable) {
			System.err.println("Step failed: " + throwable.getMessage());
			return false;
		}
	}

	private String readConfig(final String key) {
		final String fromEnv = System.getenv(key);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}

		final String fromProp = System.getProperty(key);
		if (fromProp != null && !fromProp.isBlank()) {
			return fromProp;
		}

		return null;
	}

	private String readConfigOrDefault(final String key, final String defaultValue) {
		final String value = readConfig(key);
		return value == null ? defaultValue : value;
	}

	private void printReport(final Map<String, Boolean> report, final Map<String, String> legalUrls) {
		final String reportBody = report.entrySet().stream()
				.map(entry -> String.format("%s: %s", entry.getKey(), entry.getValue() ? "PASS" : "FAIL"))
				.collect(Collectors.joining(System.lineSeparator()));
		System.out.println("Final SaleADS Mi Negocio Report");
		System.out.println(reportBody);
		if (!legalUrls.isEmpty()) {
			legalUrls.forEach((name, url) -> System.out.println(name + " final URL: " + url));
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
