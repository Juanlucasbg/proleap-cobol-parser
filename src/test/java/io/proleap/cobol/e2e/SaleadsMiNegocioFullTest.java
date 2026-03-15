package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_FLAG = "RUN_SALEADS_MI_NEGOCIO_E2E";
	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String EMAIL_ENV = "SALEADS_GOOGLE_EMAIL";
	private static final String DEFAULT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String SCREENSHOT_DIR_ENV = "SALEADS_E2E_SCREENSHOT_DIR";
	private static final String DEFAULT_SCREENSHOT_DIR = "target/saleads-evidence";
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue(
				"Set RUN_SALEADS_MI_NEGOCIO_E2E=true to run this browser E2E test.",
				Boolean.parseBoolean(System.getenv().getOrDefault(TEST_FLAG, "false")));

		final String loginUrl = System.getenv(LOGIN_URL_ENV);
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL with the environment login URL before running this test.",
				loginUrl != null && !loginUrl.isBlank());

		final String googleEmail = System.getenv().getOrDefault(EMAIL_ENV, DEFAULT_EMAIL);
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault(HEADLESS_ENV, "true"));
		final Path screenshotDir = Paths
				.get(System.getenv().getOrDefault(SCREENSHOT_DIR_ENV, DEFAULT_SCREENSHOT_DIR))
				.resolve("saleads_mi_negocio_full_test_" + TS_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(screenshotDir);

		final Map<String, Boolean> report = new LinkedHashMap<>();
		final Map<String, String> evidence = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext();
			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(20000);

			appPage.navigate(loginUrl);
			waitForUi(appPage);

			// Step 1: Login with Google
			boolean loginOk = clickFirstVisible(appPage, "Sign in with Google", "Iniciar sesión con Google", "Google");
			waitForUi(appPage);
			chooseGoogleAccountIfVisible(context, googleEmail);
			waitForUi(appPage);
			boolean appInterfaceVisible = isAnyVisible(
					appPage.getByRole(AriaRole.NAVIGATION),
					appPage.locator("aside"),
					appPage.getByText("Negocio", new Page.GetByTextOptions().setExact(false)));
			boolean sidebarVisible = isAnyVisible(
					appPage.locator("aside"),
					appPage.getByRole(AriaRole.NAVIGATION),
					appPage.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)));
			recordStep(report, failures, "Login", loginOk && appInterfaceVisible && sidebarVisible);
			screenshot(appPage, screenshotDir, "01-dashboard-loaded", false);

			// Step 2: Open Mi Negocio menu
			clickIfVisible(appPage, "Negocio");
			boolean miNegocioClicked = clickFirstVisible(appPage, "Mi Negocio");
			waitForUi(appPage);
			boolean addBusinessVisible = isTextVisible(appPage, "Agregar Negocio");
			boolean adminBusinessVisible = isTextVisible(appPage, "Administrar Negocios");
			recordStep(report, failures, "Mi Negocio menu", miNegocioClicked && addBusinessVisible && adminBusinessVisible);
			screenshot(appPage, screenshotDir, "02-mi-negocio-expanded", false);

			// Step 3: Validate Agregar Negocio modal
			boolean addBusinessClicked = clickFirstVisible(appPage, "Agregar Negocio");
			waitForUi(appPage);
			boolean modalVisible = isAnyVisible(
					appPage.getByText("Crear Nuevo Negocio"),
					appPage.getByRole(AriaRole.DIALOG));
			boolean businessNameInputVisible = isAnyVisible(
					appPage.getByLabel("Nombre del Negocio"),
					appPage.getByPlaceholder("Nombre del Negocio"),
					appPage.locator("input[name*='nombre']"));
			boolean usageTextVisible = isTextVisible(appPage, "Tienes 2 de 3 negocios");
			boolean cancelVisible = isTextVisible(appPage, "Cancelar");
			boolean createBusinessVisible = isTextVisible(appPage, "Crear Negocio");
			recordStep(
					report,
					failures,
					"Agregar Negocio modal",
					addBusinessClicked && modalVisible && businessNameInputVisible && usageTextVisible && cancelVisible
							&& createBusinessVisible);
			screenshot(appPage, screenshotDir, "03-agregar-negocio-modal", false);
			fillIfVisible(appPage, "Nombre del Negocio", "Negocio Prueba Automatización");
			clickIfVisible(appPage, "Cancelar");
			waitForUi(appPage);

			// Step 4: Open Administrar Negocios
			if (!isTextVisible(appPage, "Administrar Negocios")) {
				clickIfVisible(appPage, "Mi Negocio");
			}
			boolean adminClicked = clickFirstVisible(appPage, "Administrar Negocios");
			waitForUi(appPage);
			boolean infoGeneralVisible = isTextVisible(appPage, "Información General");
			boolean detailsVisible = isTextVisible(appPage, "Detalles de la Cuenta");
			boolean businessesSectionVisible = isTextVisible(appPage, "Tus Negocios");
			boolean legalSectionVisible = isAnyVisible(
					appPage.getByText("Sección Legal", new Page.GetByTextOptions().setExact(false)),
					appPage.getByText("Legal", new Page.GetByTextOptions().setExact(false)));
			recordStep(
					report,
					failures,
					"Administrar Negocios view",
					adminClicked && infoGeneralVisible && detailsVisible && businessesSectionVisible && legalSectionVisible);
			screenshot(appPage, screenshotDir, "04-administrar-negocios", true);

			// Step 5: Validate Información General
			boolean userNameVisible = isAnyVisible(
					appPage.getByText(Pattern.compile("(?i)nombre")),
					appPage.getByText(Pattern.compile("(?i)informaci[oó]n general")));
			boolean userEmailVisible = isAnyVisible(
					appPage.getByText(googleEmail, new Page.GetByTextOptions().setExact(false)),
					appPage.getByText(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")));
			boolean businessPlanVisible = isAnyVisible(
					appPage.getByText("BUSINESS PLAN"),
					appPage.getByText("Business Plan", new Page.GetByTextOptions().setExact(false)));
			boolean changePlanButtonVisible = isAnyVisible(
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")),
					appPage.getByText("Cambiar Plan"));
			recordStep(
					report,
					failures,
					"Información General",
					userNameVisible && userEmailVisible && businessPlanVisible && changePlanButtonVisible);

			// Step 6: Validate Detalles de la Cuenta
			boolean accountCreatedVisible = isTextVisible(appPage, "Cuenta creada");
			boolean activeStateVisible = isAnyVisible(
					appPage.getByText("Estado activo"),
					appPage.getByText("Activo", new Page.GetByTextOptions().setExact(false)));
			boolean languageVisible = isTextVisible(appPage, "Idioma seleccionado");
			recordStep(
					report,
					failures,
					"Detalles de la Cuenta",
					accountCreatedVisible && activeStateVisible && languageVisible);

			// Step 7: Validate Tus Negocios
			boolean businessListVisible = isAnyVisible(
					appPage.getByText("Tus Negocios"),
					appPage.locator("section:has-text('Tus Negocios')"));
			boolean addBusinessButtonVisible = isAnyVisible(
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
					appPage.getByText("Agregar Negocio"));
			boolean businessesUsageVisible = isTextVisible(appPage, "Tienes 2 de 3 negocios");
			recordStep(
					report,
					failures,
					"Tus Negocios",
					businessListVisible && addBusinessButtonVisible && businessesUsageVisible);

			// Step 8: Validate Términos y Condiciones
			LegalValidationResult termsResult = validateLegalLink(
					context,
					appPage,
					"Términos y Condiciones",
					Pattern.compile("(?i)t[eé]rminos y condiciones"),
					screenshotDir,
					"08-terminos-y-condiciones");
			recordStep(report, failures, "Términos y Condiciones", termsResult.valid);
			evidence.put("Términos y Condiciones URL", termsResult.finalUrl);

			// Step 9: Validate Política de Privacidad
			LegalValidationResult privacyResult = validateLegalLink(
					context,
					appPage,
					"Política de Privacidad",
					Pattern.compile("(?i)pol[ií]tica de privacidad"),
					screenshotDir,
					"09-politica-de-privacidad");
			recordStep(report, failures, "Política de Privacidad", privacyResult.valid);
			evidence.put("Política de Privacidad URL", privacyResult.finalUrl);

			printFinalReport(report, evidence, screenshotDir);
			Assert.assertTrue(
					"SaleADS Mi Negocio workflow failed in steps: " + String.join(", ", failures),
					failures.isEmpty());
		}
	}

	private static LegalValidationResult validateLegalLink(
			final BrowserContext context,
			final Page appPage,
			final String linkText,
			final Pattern expectedHeading,
			final Path screenshotDir,
			final String screenshotName) {
		boolean clicked = false;
		Page targetPage = appPage;
		Page openedTab = null;

		try {
			openedTab = context.waitForPage(
					new BrowserContext.WaitForPageOptions().setTimeout(8000),
					() -> clickFirstVisible(appPage, linkText));
			clicked = true;
			targetPage = openedTab;
		} catch (TimeoutError ignored) {
			clicked = clickFirstVisible(appPage, linkText);
			targetPage = appPage;
		}

		waitForUi(targetPage);
		boolean headingVisible = isAnyVisible(
				targetPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(expectedHeading)),
				targetPage.getByText(expectedHeading));
		boolean legalTextVisible = isAnyVisible(
				targetPage.locator("main p").first(),
				targetPage.locator("article p").first(),
				targetPage.locator("p").first());
		screenshot(targetPage, screenshotDir, screenshotName, true);
		String finalUrl = targetPage.url();

		// Return to app tab.
		if (openedTab != null) {
			openedTab.close();
			appPage.bringToFront();
		} else if (targetPage == appPage) {
			try {
				appPage.goBack();
				waitForUi(appPage);
			} catch (Exception ignored) {
				// App may keep legal content in-place. This is acceptable for URL evidence.
			}
		}

		return new LegalValidationResult(clicked && headingVisible && legalTextVisible, finalUrl);
	}

	private static boolean clickFirstVisible(final Page page, final String... texts) {
		for (String text : texts) {
			Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
			if (isVisible(locator)) {
				locator.click();
				waitForUi(page);
				return true;
			}
		}
		return false;
	}

	private static void clickIfVisible(final Page page, final String text) {
		Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
		if (isVisible(locator)) {
			locator.click();
			waitForUi(page);
		}
	}

	private static void fillIfVisible(final Page page, final String label, final String value) {
		Locator byLabel = page.getByLabel(label).first();
		if (isVisible(byLabel)) {
			byLabel.fill(value);
			return;
		}

		Locator byPlaceholder = page.getByPlaceholder(label).first();
		if (isVisible(byPlaceholder)) {
			byPlaceholder.fill(value);
		}
	}

	private static void chooseGoogleAccountIfVisible(final BrowserContext context, final String email) {
		for (Page currentPage : context.pages()) {
			currentPage.setDefaultTimeout(5000);
			Locator account = currentPage.getByText(email, new Page.GetByTextOptions().setExact(false)).first();
			if (isVisible(account)) {
				account.click();
				waitForUi(currentPage);
				return;
			}
		}
	}

	private static boolean isTextVisible(final Page page, final String text) {
		return isVisible(page.getByText(text, new Page.GetByTextOptions().setExact(false)).first());
	}

	private static boolean isAnyVisible(final Locator... locators) {
		for (Locator locator : locators) {
			if (isVisible(locator)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isVisible(final Locator locator) {
		try {
			return locator.isVisible();
		} catch (Exception ignored) {
			return false;
		}
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (Exception ignored) {
			// Some UI actions do not trigger full document events.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (Exception ignored) {
			// Network idle is best effort.
		}
		page.waitForTimeout(600);
	}

	private static void screenshot(final Page page, final Path dir, final String name, final boolean fullPage) {
		Path file = dir.resolve(name + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(fullPage));
	}

	private static void recordStep(
			final Map<String, Boolean> report,
			final List<String> failures,
			final String stepName,
			final boolean success) {
		report.put(stepName, success);
		if (!success) {
			failures.add(stepName);
		}
	}

	private static void printFinalReport(
			final Map<String, Boolean> report,
			final Map<String, String> evidence,
			final Path screenshotDir) {
		System.out.println("=== SaleADS Mi Negocio Final Report ===");
		for (Map.Entry<String, Boolean> result : report.entrySet()) {
			System.out.println(result.getKey() + ": " + (result.getValue() ? "PASS" : "FAIL"));
		}
		System.out.println("Evidence directory: " + screenshotDir.toAbsolutePath());
		for (Map.Entry<String, String> item : evidence.entrySet()) {
			System.out.println(item.getKey() + ": " + item.getValue());
		}
	}

	private static class LegalValidationResult {
		private final boolean valid;
		private final String finalUrl;

		private LegalValidationResult(final boolean valid, final String finalUrl) {
			this.valid = valid;
			this.finalUrl = finalUrl;
		}
	}
}
