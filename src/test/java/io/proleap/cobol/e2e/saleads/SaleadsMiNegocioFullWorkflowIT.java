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
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

public class SaleadsMiNegocioFullWorkflowIT {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern GOOGLE_LOGIN_PATTERN = Pattern
			.compile("(?i)(sign in with google|iniciar sesión con google|iniciar sesion con google|continuar con google|google)");
	private static final Pattern NEGOCIO_SECTION_PATTERN = Pattern.compile("(?i)^\\s*Negocio\\s*$");
	private static final int DEFAULT_TIMEOUT_MS = 30000;
	private static final int SHORT_TIMEOUT_MS = 5000;
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Test
	public void runSaleadsMiNegocioWorkflow() throws Exception {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		final Map<String, String> evidence = new LinkedHashMap<>();
		final Path screenshotDir = prepareScreenshotDirectory();

		try (final Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(isHeadless()));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

			openLoginPageIfProvided(appPage);

			report.put("Login", validateLoginAndDashboard(appPage, screenshotDir));
			report.put("Mi Negocio menu", validateMiNegocioMenu(appPage, screenshotDir));
			report.put("Agregar Negocio modal", validateAgregarNegocioModal(appPage, screenshotDir));
			report.put("Administrar Negocios view", validateAdministrarNegociosView(appPage, screenshotDir));
			report.put("Información General", validateInformacionGeneral(appPage));
			report.put("Detalles de la Cuenta", validateDetallesCuenta(appPage));
			report.put("Tus Negocios", validateTusNegocios(appPage));

			final LegalValidationResult terminosResult = validateLegalPage(context, appPage, "Términos y Condiciones",
					Pattern.compile("(?i)términos\\s+y\\s+condiciones|terminos\\s+y\\s+condiciones"), screenshotDir, "terms-and-conditions");
			report.put("Términos y Condiciones", terminosResult.valid);
			evidence.put("Términos y Condiciones URL", terminosResult.finalUrl);

			final LegalValidationResult privacidadResult = validateLegalPage(context, appPage, "Política de Privacidad",
					Pattern.compile("(?i)política\\s+de\\s+privacidad|politica\\s+de\\s+privacidad"), screenshotDir, "privacy-policy");
			report.put("Política de Privacidad", privacidadResult.valid);
			evidence.put("Política de Privacidad URL", privacidadResult.finalUrl);
		}

		printFinalReport(report, evidence, screenshotDir);

		final StringBuilder failures = new StringBuilder();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue()) {
				if (failures.length() > 0) {
					failures.append(", ");
				}
				failures.append(entry.getKey());
			}
		}

		assertTrue("Workflow validation failed for: " + failures, failures.length() == 0);
	}

	private void openLoginPageIfProvided(final Page page) {
		final String configuredUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getenv("SALEADS_APP_URL"));
		if (configuredUrl != null) {
			page.navigate(configuredUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
		}
	}

	private boolean validateLoginAndDashboard(final Page page, final Path screenshotDir) {
		final boolean sidebarAlreadyVisible = isSidebarVisible(page);
		if (!sidebarAlreadyVisible) {
			final Locator loginButton = findFirstVisible(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_PATTERN)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_PATTERN)),
					page.getByText(GOOGLE_LOGIN_PATTERN).first());

			if (loginButton == null) {
				return false;
			}

			clickAndSettle(page, loginButton);
			trySelectGoogleAccount(page);
		}

		final boolean sidebarVisible = waitForSidebar(page);
		if (sidebarVisible) {
			captureScreenshot(page, screenshotDir, "01-dashboard-loaded", true);
		}
		return sidebarVisible;
	}

	private void trySelectGoogleAccount(final Page page) {
		final Locator accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, new Page.GetByTextOptions().setExact(true));
		if (isVisible(accountOption, SHORT_TIMEOUT_MS)) {
			clickAndSettle(page, accountOption);
		}
	}

	private boolean validateMiNegocioMenu(final Page page, final Path screenshotDir) {
		final Locator negocioSection = findNegocioSection(page);
		if (negocioSection == null) {
			return false;
		}

		clickAndSettle(page, negocioSection);

		final boolean agregarVisible = isVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
				SHORT_TIMEOUT_MS)
				|| isVisible(page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(true)), SHORT_TIMEOUT_MS);
		final boolean administrarVisible = isVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Administrar Negocios")),
				SHORT_TIMEOUT_MS)
				|| isVisible(page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true)), SHORT_TIMEOUT_MS);

		if (agregarVisible && administrarVisible) {
			captureScreenshot(page, screenshotDir, "02-mi-negocio-expanded", false);
		}
		return agregarVisible && administrarVisible;
	}

	private boolean validateAgregarNegocioModal(final Page page, final Path screenshotDir) {
		ensureMiNegocioExpanded(page);

		final Locator agregarNegocioAction = findFirstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Agregar Negocio")),
				page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(true)).first());
		if (agregarNegocioAction == null) {
			return false;
		}

		clickAndSettle(page, agregarNegocioAction);

		final Locator modalTitle = page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Crear Nuevo Negocio"));
		final Locator businessNameField = page.getByLabel("Nombre del Negocio");
		final Locator businessLimitText = page.getByText("Tienes 2 de 3 negocios");
		final Locator cancelButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar"));
		final Locator createButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio"));

		final boolean valid = isVisible(modalTitle, SHORT_TIMEOUT_MS) && isVisible(businessNameField, SHORT_TIMEOUT_MS)
				&& isVisible(businessLimitText, SHORT_TIMEOUT_MS) && isVisible(cancelButton, SHORT_TIMEOUT_MS)
				&& isVisible(createButton, SHORT_TIMEOUT_MS);

		if (valid) {
			captureScreenshot(page, screenshotDir, "03-agregar-negocio-modal", false);

			businessNameField.click();
			businessNameField.fill("Negocio Prueba Automatización");
			clickAndSettle(page, cancelButton);
		}

		return valid;
	}

	private boolean validateAdministrarNegociosView(final Page page, final Path screenshotDir) {
		ensureMiNegocioExpanded(page);

		final Locator administrarNegociosAction = findFirstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Administrar Negocios")),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Administrar Negocios")),
				page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true)).first());
		if (administrarNegociosAction == null) {
			return false;
		}

		clickAndSettle(page, administrarNegociosAction);

		final boolean infoGeneralVisible = isVisible(headingOrText(page, "Información General"), SHORT_TIMEOUT_MS);
		final boolean detallesVisible = isVisible(headingOrText(page, "Detalles de la Cuenta"), SHORT_TIMEOUT_MS);
		final boolean negociosVisible = isVisible(headingOrText(page, "Tus Negocios"), SHORT_TIMEOUT_MS);
		final boolean legalVisible = isVisible(headingOrText(page, "Sección Legal"), SHORT_TIMEOUT_MS);

		final boolean valid = infoGeneralVisible && detallesVisible && negociosVisible && legalVisible;
		if (valid) {
			captureScreenshot(page, screenshotDir, "04-administrar-negocios-view", true);
		}
		return valid;
	}

	private boolean validateInformacionGeneral(final Page page) {
		final boolean planVisible = isVisible(page.getByText("BUSINESS PLAN"), SHORT_TIMEOUT_MS);
		final boolean changePlanVisible = isVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")),
				SHORT_TIMEOUT_MS);
		final boolean emailVisible = isVisible(page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i"), SHORT_TIMEOUT_MS);

		// Name format can vary. We confirm there is profile-like user text near "Información General".
		final Locator infoSection = sectionContainer(page, "Información General");
		final boolean nameVisible = infoSection != null
				&& isVisible(infoSection.locator("text=/[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/"), SHORT_TIMEOUT_MS);

		return nameVisible && emailVisible && planVisible && changePlanVisible;
	}

	private boolean validateDetallesCuenta(final Page page) {
		final Locator section = sectionContainer(page, "Detalles de la Cuenta");
		if (section == null) {
			return false;
		}

		final boolean cuentaCreadaVisible = isVisible(section.getByText(Pattern.compile("(?i)cuenta\\s+creada")), SHORT_TIMEOUT_MS);
		final boolean estadoActivoVisible = isVisible(section.getByText(Pattern.compile("(?i)estado\\s+activo")), SHORT_TIMEOUT_MS);
		final boolean idiomaVisible = isVisible(section.getByText(Pattern.compile("(?i)idioma\\s+seleccionado")), SHORT_TIMEOUT_MS);
		return cuentaCreadaVisible && estadoActivoVisible && idiomaVisible;
	}

	private boolean validateTusNegocios(final Page page) {
		final Locator section = sectionContainer(page, "Tus Negocios");
		if (section == null) {
			return false;
		}

		final boolean addBusinessVisible = isVisible(
				section.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Agregar Negocio")),
				SHORT_TIMEOUT_MS)
				|| isVisible(section.getByText("Agregar Negocio"), SHORT_TIMEOUT_MS);
		final boolean limitVisible = isVisible(section.getByText("Tienes 2 de 3 negocios"), SHORT_TIMEOUT_MS);
		final boolean listVisible = section.locator("li, [role='listitem'], table tbody tr").count() > 0
				|| isVisible(section.locator("[class*='business'], [class*='negocio']"), SHORT_TIMEOUT_MS);
		return addBusinessVisible && limitVisible && listVisible;
	}

	private LegalValidationResult validateLegalPage(final BrowserContext context, final Page appPage, final String linkText,
			final Pattern headingPattern, final Path screenshotDir, final String screenshotName) {
		final Locator legalLink = findFirstVisible(
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkText)),
				appPage.getByText(linkText, new Page.GetByTextOptions().setExact(true)).first());
		if (legalLink == null) {
			return LegalValidationResult.failed("N/A");
		}

		Page legalPage = null;
		boolean openedInNewTab = false;
		final String appUrlBefore = appPage.url();
		try {
			legalPage = context.waitForPage(() -> clickAndSettle(appPage, legalLink),
					new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS));
			openedInNewTab = true;
		} catch (final PlaywrightException timeout) {
			legalPage = appPage;
		}

		waitForUiSettle(legalPage);

		final boolean headingVisible = isVisible(
				legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
				SHORT_TIMEOUT_MS)
				|| isVisible(legalPage.getByText(headingPattern).first(), SHORT_TIMEOUT_MS);
		final boolean legalContentVisible = legalPage.locator("p, article, main, section").count() > 0
				|| isVisible(legalPage.locator("text=/\\w{20,}/"), SHORT_TIMEOUT_MS);
		final boolean valid = headingVisible && legalContentVisible;
		final String finalUrl = legalPage.url();

		captureScreenshot(legalPage, screenshotDir, screenshotName, true);

		if (openedInNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiSettle(appPage);
		} else if (!sameUrl(finalUrl, appUrlBefore)) {
			appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
			waitForUiSettle(appPage);
		}

		return new LegalValidationResult(valid, finalUrl);
	}

	private void ensureMiNegocioExpanded(final Page page) {
		final boolean administrarVisible = isVisible(page.getByText("Administrar Negocios"), SHORT_TIMEOUT_MS);
		if (!administrarVisible) {
			final Locator negocioSection = findNegocioSection(page);
			if (negocioSection != null) {
				clickAndSettle(page, negocioSection);
			}
		}
	}

	private Locator findNegocioSection(final Page page) {
		return findFirstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEGOCIO_SECTION_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(NEGOCIO_SECTION_PATTERN)),
				page.getByText(NEGOCIO_SECTION_PATTERN).first());
	}

	private Locator sectionContainer(final Page page, final String headingText) {
		final Locator heading = headingOrText(page, headingText);
		if (!isVisible(heading, SHORT_TIMEOUT_MS)) {
			return null;
		}

		final Locator section = heading.locator("xpath=ancestor::section[1]");
		if (section.count() > 0) {
			return section.first();
		}

		final Locator article = heading.locator("xpath=ancestor::*[self::div or self::article][1]");
		if (article.count() > 0) {
			return article.first();
		}

		return null;
	}

	private Locator headingOrText(final Page page, final String text) {
		return findFirstVisible(
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(text)),
				page.getByText(text, new Page.GetByTextOptions().setExact(true)).first(),
				page.getByText(Pattern.compile("(?i)" + Pattern.quote(text))).first());
	}

	@SafeVarargs
	private final Locator findFirstVisible(final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			if (isVisible(candidate, SHORT_TIMEOUT_MS)) {
				return candidate;
			}
		}
		return null;
	}

	private boolean waitForSidebar(final Page page) {
		final long started = System.currentTimeMillis();
		while (System.currentTimeMillis() - started < DEFAULT_TIMEOUT_MS) {
			if (isSidebarVisible(page)) {
				return true;
			}
			page.waitForTimeout(500);
		}
		return false;
	}

	private boolean isSidebarVisible(final Page page) {
		return isVisible(page.locator("aside").first(), SHORT_TIMEOUT_MS)
				|| isVisible(page.getByRole(AriaRole.NAVIGATION).first(), SHORT_TIMEOUT_MS)
				|| isVisible(page.getByText(NEGOCIO_SECTION_PATTERN).first(), SHORT_TIMEOUT_MS);
	}

	private void clickAndSettle(final Page page, final Locator locator) {
		locator.click();
		waitForUiSettle(page);
	}

	private void waitForUiSettle(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
		}
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			return locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout((double) timeoutMs));
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void captureScreenshot(final Page page, final Path screenshotDir, final String filename, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotDir.resolve(filename + ".png")).setFullPage(fullPage));
	}

	private Path prepareScreenshotDirectory() throws Exception {
		final String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
		final Path directory = Paths.get("target", "saleads-mi-negocio-screenshots", timestamp);
		Files.createDirectories(directory);
		return directory;
	}

	private boolean isHeadless() {
		final String value = System.getenv("SALEADS_HEADLESS");
		if (value == null) {
			return true;
		}
		return Boolean.parseBoolean(value);
	}

	private String firstNonBlank(final String first, final String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		if (second != null && !second.isBlank()) {
			return second;
		}
		return null;
	}

	private boolean sameUrl(final String left, final String right) {
		return left != null && left.equals(right);
	}

	private void printFinalReport(final Map<String, Boolean> report, final Map<String, String> evidence, final Path screenshotDir) {
		System.out.println("SaleADS Mi Negocio workflow final report:");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println("- " + entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}

		System.out.println("Evidence:");
		System.out.println("- Screenshots directory: " + screenshotDir.toAbsolutePath());
		for (final Map.Entry<String, String> entry : evidence.entrySet()) {
			System.out.println("- " + entry.getKey() + ": " + entry.getValue());
		}
	}

	private static class LegalValidationResult {
		private final boolean valid;
		private final String finalUrl;

		private LegalValidationResult(final boolean valid, final String finalUrl) {
			this.valid = valid;
			this.finalUrl = finalUrl;
		}

		private static LegalValidationResult failed(final String url) {
			return new LegalValidationResult(false, url);
		}
	}
}
