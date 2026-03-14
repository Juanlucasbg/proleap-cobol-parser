package io.proleap.e2e;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * End-to-end workflow validation for SaleADS "Mi Negocio" module.
 *
 * <p>Execution notes:
 * <ul>
 *   <li>Provide login page URL with SALEADS_LOGIN_URL env var or -Dsaleads.login.url.</li>
 *   <li>The test intentionally does not hardcode any domain.</li>
 *   <li>Screenshots are stored in target/saleads-e2e-artifacts/&lt;timestamp&gt;/.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final int SHORT_TIMEOUT_MS = 2500;
	private static final int DEFAULT_TIMEOUT_MS = 12000;
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String loginUrl = resolveValue("SALEADS_LOGIN_URL", "saleads.login.url");
		final String cdpUrl = resolveValue("SALEADS_CDP_URL", "saleads.cdp.url");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL (or -Dsaleads.login.url), or provide SALEADS_CDP_URL (or -Dsaleads.cdp.url).",
				(loginUrl != null && !loginUrl.isBlank()) || (cdpUrl != null && !cdpUrl.isBlank()));

		final Path artifactsDir = createArtifactsDir();
		final Map<String, Boolean> report = new LinkedHashMap<>();
		final Map<String, String> evidence = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser;
			final BrowserContext context;
			final Page appPage;
			if (cdpUrl != null && !cdpUrl.isBlank()) {
				browser = playwright.chromium().connectOverCDP(cdpUrl);
				context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
				appPage = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
				appPage.bringToFront();
				if ((appPage.url() == null || appPage.url().isBlank() || "about:blank".equals(appPage.url()))
						&& loginUrl != null
						&& !loginUrl.isBlank()) {
					appPage.navigate(loginUrl);
				}
			} else {
				browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
						.setHeadless(Boolean.parseBoolean(System.getProperty("saleads.headless", "true"))));
				context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
				appPage = context.newPage();
				appPage.navigate(loginUrl);
			}
			waitForUi(appPage);

			report.put("Login", runLoginStep(appPage, context, artifactsDir));
			report.put("Mi Negocio menu", runMiNegocioMenuStep(appPage, artifactsDir));
			report.put("Agregar Negocio modal", runAgregarNegocioModalStep(appPage, artifactsDir));
			report.put("Administrar Negocios view", runAdministrarNegociosStep(appPage, artifactsDir));
			report.put("Información General", runInformacionGeneralValidation(appPage));
			report.put("Detalles de la Cuenta", runDetallesCuentaValidation(appPage));
			report.put("Tus Negocios", runTusNegociosValidation(appPage));

			final LegalValidationResult termsResult = validateLegalPage(
					appPage,
					context,
					artifactsDir,
					"Términos y Condiciones",
					"Términos y Condiciones",
					"terminos_y_condiciones");
			report.put("Términos y Condiciones", termsResult.passed);
			evidence.put("Términos y Condiciones URL", termsResult.finalUrl);

			final LegalValidationResult privacyResult = validateLegalPage(
					appPage,
					context,
					artifactsDir,
					"Política de Privacidad",
					"Política de Privacidad",
					"politica_de_privacidad");
			report.put("Política de Privacidad", privacyResult.passed);
			evidence.put("Política de Privacidad URL", privacyResult.finalUrl);
		}

		final String reportText = buildFinalReport(report, evidence, artifactsDir);
		System.out.println(reportText);
		Assert.assertTrue(reportText, allPassed(report));
	}

	private static boolean runLoginStep(final Page appPage, final BrowserContext context, final Path artifactsDir) {
		if (waitAnyVisible(appPage.locator("aside"), appPage.getByRole(AriaRole.NAVIGATION))) {
			screenshot(appPage, artifactsDir, "01_dashboard_loaded");
			return true;
		}

		final Set<Page> pagesBeforeClick = new HashSet<>(context.pages());
		final boolean clickedGoogleLogin = clickFirstVisible(appPage,
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*"))),
				appPage.getByText("Sign in with Google", new Page.GetByTextOptions().setExact(false)),
				appPage.getByText("Continuar con Google", new Page.GetByTextOptions().setExact(false)),
				appPage.getByText("Google", new Page.GetByTextOptions().setExact(false)));
		if (!clickedGoogleLogin) {
			return false;
		}

		Page authPage = detectNewlyOpenedPage(context, pagesBeforeClick);
		if (authPage == null) {
			authPage = appPage;
		}
		waitForUi(authPage);
		selectGoogleAccountIfPresent(authPage);
		waitForUi(appPage);

		final boolean mainInterfaceVisible = waitAnyVisible(
				appPage.locator("aside"),
				appPage.getByRole(AriaRole.NAVIGATION),
				appPage.getByText("Negocio", new Page.GetByTextOptions().setExact(false)),
				appPage.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)));
		final boolean sidebarVisible = waitAnyVisible(
				appPage.locator("aside"),
				appPage.getByRole(AriaRole.NAVIGATION));

		screenshot(appPage, artifactsDir, "01_dashboard_loaded");
		return mainInterfaceVisible && sidebarVisible;
	}

	private static boolean runMiNegocioMenuStep(final Page appPage, final Path artifactsDir) {
		final boolean clickedNegocioSection = clickFirstVisible(appPage,
				appPage.getByText("Negocio", new Page.GetByTextOptions().setExact(false)),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*negocio.*"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*negocio.*"))));

		final boolean clickedMiNegocio = clickFirstVisible(appPage,
				appPage.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*mi\\s+negocio.*"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*mi\\s+negocio.*"))));

		final boolean agregarVisible = waitAnyVisible(
				appPage.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)));
		final boolean administrarVisible = waitAnyVisible(
				appPage.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)));

		screenshot(appPage, artifactsDir, "02_mi_negocio_menu_expanded");
		return clickedNegocioSection && clickedMiNegocio && agregarVisible && administrarVisible;
	}

	private static boolean runAgregarNegocioModalStep(final Page appPage, final Path artifactsDir) {
		final boolean clickedAgregar = clickFirstVisible(appPage,
				appPage.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*agregar\\s+negocio.*"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*agregar\\s+negocio.*"))));
		if (!clickedAgregar) {
			return false;
		}

		final Locator modalTitle = firstLocator(
				appPage.getByText("Crear Nuevo Negocio", new Page.GetByTextOptions().setExact(false)),
				appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*crear\\s+nuevo\\s+negocio.*"))));
		final Locator nameInput = firstLocator(
				appPage.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)),
				appPage.getByPlaceholder("Nombre del Negocio", new Page.GetByPlaceholderOptions().setExact(false)));
		final Locator quotaText = appPage.getByText("Tienes 2 de 3 negocios", new Page.GetByTextOptions().setExact(false));
		final Locator cancelButton = appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*cancelar.*")));
		final Locator createButton = appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*crear\\s+negocio.*")));

		final boolean titleVisible = waitAnyVisible(modalTitle);
		final boolean inputVisible = waitAnyVisible(nameInput);
		final boolean quotaVisible = waitAnyVisible(quotaText);
		final boolean cancelVisible = waitAnyVisible(cancelButton);
		final boolean createVisible = waitAnyVisible(createButton);

		screenshot(appPage, artifactsDir, "03_agregar_negocio_modal");

		if (inputVisible) {
			nameInput.first().click();
			waitForUi(appPage);
			nameInput.first().fill("Negocio Prueba Automatizacion");
			waitForUi(appPage);
		}
		if (cancelVisible) {
			cancelButton.first().click();
			waitForUi(appPage);
		}

		return titleVisible && inputVisible && quotaVisible && cancelVisible && createVisible;
	}

	private static boolean runAdministrarNegociosStep(final Page appPage, final Path artifactsDir) {
		if (!waitAnyVisible(appPage.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)))) {
			clickFirstVisible(appPage,
					appPage.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(false)),
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*mi\\s+negocio.*"))));
		}

		final boolean clickedAdministrar = clickFirstVisible(appPage,
				appPage.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*administrar\\s+negocios.*"))),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*administrar\\s+negocios.*"))));
		if (!clickedAdministrar) {
			return false;
		}

		final boolean infoGeneral = waitAnyVisible(appPage.getByText("Informacion General", new Page.GetByTextOptions().setExact(false)));
		final boolean infoGeneralAccented = waitAnyVisible(appPage.getByText("Información General", new Page.GetByTextOptions().setExact(false)));
		final boolean detallesCuenta = waitAnyVisible(appPage.getByText("Detalles de la Cuenta", new Page.GetByTextOptions().setExact(false)));
		final boolean tusNegocios = waitAnyVisible(appPage.getByText("Tus Negocios", new Page.GetByTextOptions().setExact(false)));
		final boolean seccionLegal = waitAnyVisible(
				appPage.getByText("Seccion Legal", new Page.GetByTextOptions().setExact(false)),
				appPage.getByText("Sección Legal", new Page.GetByTextOptions().setExact(false)));

		screenshot(appPage, artifactsDir, "04_administrar_negocios_view");
		return (infoGeneral || infoGeneralAccented) && detallesCuenta && tusNegocios && seccionLegal;
	}

	private static boolean runInformacionGeneralValidation(final Page appPage) {
		final boolean userNameVisible = waitAnyVisible(
				appPage.getByText("@", new Page.GetByTextOptions().setExact(false)),
				appPage.locator("text=/[A-Za-z].* [A-Za-z].*/"));
		final boolean userEmailVisible = waitAnyVisible(appPage.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/"));
		final boolean businessPlanVisible = waitAnyVisible(appPage.getByText("BUSINESS PLAN", new Page.GetByTextOptions().setExact(false)));
		final boolean changePlanButtonVisible = waitAnyVisible(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*cambiar\\s+plan.*"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*cambiar\\s+plan.*"))));
		return userNameVisible && userEmailVisible && businessPlanVisible && changePlanButtonVisible;
	}

	private static boolean runDetallesCuentaValidation(final Page appPage) {
		final boolean cuentaCreadaVisible = waitAnyVisible(appPage.getByText("Cuenta creada", new Page.GetByTextOptions().setExact(false)));
		final boolean estadoActivoVisible = waitAnyVisible(appPage.getByText("Estado activo", new Page.GetByTextOptions().setExact(false)));
		final boolean idiomaVisible = waitAnyVisible(
				appPage.getByText("Idioma seleccionado", new Page.GetByTextOptions().setExact(false)),
				appPage.getByText("Idioma Seleccionado", new Page.GetByTextOptions().setExact(false)));
		return cuentaCreadaVisible && estadoActivoVisible && idiomaVisible;
	}

	private static boolean runTusNegociosValidation(final Page appPage) {
		final boolean businessListVisible = waitAnyVisible(
				appPage.getByText("Tus Negocios", new Page.GetByTextOptions().setExact(false)),
				appPage.locator("[class*='business'], [id*='business'], [data-testid*='business']"));
		final boolean agregarVisible = waitAnyVisible(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*agregar\\s+negocio.*"))),
				appPage.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(false)));
		final boolean quotaVisible = waitAnyVisible(appPage.getByText("Tienes 2 de 3 negocios", new Page.GetByTextOptions().setExact(false)));
		return businessListVisible && agregarVisible && quotaVisible;
	}

	private static LegalValidationResult validateLegalPage(
			final Page appPage,
			final BrowserContext context,
			final Path artifactsDir,
			final String linkText,
			final String headingText,
			final String screenshotName) {
		final Set<Page> beforeClickPages = new HashSet<>(context.pages());
		final boolean clicked = clickFirstVisible(appPage,
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*" + Pattern.quote(linkText) + ".*"))),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*" + Pattern.quote(linkText) + ".*"))),
				appPage.getByText(linkText, new Page.GetByTextOptions().setExact(false)),
				appPage.getByText(stripAccents(linkText), new Page.GetByTextOptions().setExact(false)));
		if (!clicked) {
			return new LegalValidationResult(false, "<link not found>");
		}

		Page legalPage = detectNewlyOpenedPage(context, beforeClickPages);
		final boolean openedNewTab = legalPage != null;
		if (!openedNewTab) {
			legalPage = appPage;
		}

		waitForUi(legalPage);
		final boolean headingVisible = waitAnyVisible(
				legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*" + Pattern.quote(headingText) + ".*"))),
				legalPage.getByText(headingText, new Page.GetByTextOptions().setExact(false)),
				legalPage.getByText(stripAccents(headingText), new Page.GetByTextOptions().setExact(false)));
		final boolean legalContentVisible = hasLongBodyText(legalPage);

		screenshot(legalPage, artifactsDir, screenshotName);
		final String finalUrl = legalPage.url();

		if (openedNewTab) {
			try {
				legalPage.close();
			} catch (PlaywrightException ignored) {
				// best effort cleanup
			}
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				legalPage.goBack(new Page.GoBackOptions().setTimeout((double) DEFAULT_TIMEOUT_MS));
				waitForUi(legalPage);
			} catch (PlaywrightException ignored) {
				// navigation may be blocked in some environments
			}
		}

		return new LegalValidationResult(headingVisible && legalContentVisible, finalUrl);
	}

	private static void selectGoogleAccountIfPresent(final Page authPage) {
		if (clickFirstVisible(authPage,
				authPage.getByText(GOOGLE_ACCOUNT, new Page.GetByTextOptions().setExact(false)),
				authPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*" + Pattern.quote(GOOGLE_ACCOUNT) + ".*"))),
				authPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*" + Pattern.quote(GOOGLE_ACCOUNT) + ".*"))))) {
			waitForUi(authPage);
		}
	}

	private static boolean clickFirstVisible(final Page page, final Locator... candidates) {
		final Locator target = firstVisible(candidates);
		if (target == null) {
			return false;
		}
		try {
			target.scrollIntoViewIfNeeded();
		} catch (PlaywrightException ignored) {
			// element might not support scrolling but can still be clicked
		}
		target.first().click(new Locator.ClickOptions().setTimeout((double) DEFAULT_TIMEOUT_MS));
		waitForUi(page);
		return true;
	}

	private static Locator firstVisible(final Locator... candidates) {
		for (Locator candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			try {
				if (candidate.count() > 0 && candidate.first().isVisible()) {
					return candidate.first();
				}
				if (waitForVisible(candidate.first(), SHORT_TIMEOUT_MS)) {
					return candidate.first();
				}
			} catch (PlaywrightException ignored) {
				// keep trying other candidates
			}
		}
		return null;
	}

	private static Locator firstLocator(final Locator... candidates) {
		for (Locator candidate : candidates) {
			if (candidate != null) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean waitAnyVisible(final Locator... locators) {
		for (Locator locator : locators) {
			if (locator != null && waitForVisible(locator, DEFAULT_TIMEOUT_MS)) {
				return true;
			}
		}
		return false;
	}

	private static boolean waitForVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout((double) timeoutMs));
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private static boolean hasLongBodyText(final Page page) {
		try {
			final String bodyText = page.locator("body").innerText();
			return bodyText != null && bodyText.trim().length() > 120;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private static Page detectNewlyOpenedPage(final BrowserContext context, final Set<Page> knownPages) {
		for (Page page : context.pages()) {
			if (!knownPages.contains(page)) {
				return page;
			}
		}
		return null;
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout((double) DEFAULT_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// continue if DOMContentLoaded is already completed or cannot be observed
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(3000.0));
		} catch (PlaywrightException ignored) {
			// some pages keep long-lived connections
		}
		page.waitForTimeout(600);
	}

	private static void screenshot(final Page page, final Path artifactsDir, final String fileName) {
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(artifactsDir.resolve(sanitizeFileName(fileName) + ".png"))
				.setFullPage(true));
	}

	private static Path createArtifactsDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Paths.get("target", "saleads-e2e-artifacts", timestamp);
		Files.createDirectories(path);
		return path.toAbsolutePath();
	}

	private static String sanitizeFileName(final String fileName) {
		return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private static String stripAccents(final String value) {
		return value
				.replace("á", "a")
				.replace("Á", "A")
				.replace("é", "e")
				.replace("É", "E")
				.replace("í", "i")
				.replace("Í", "I")
				.replace("ó", "o")
				.replace("Ó", "O")
				.replace("ú", "u")
				.replace("Ú", "U")
				.replace("ü", "u")
				.replace("Ü", "U")
				.replace("ñ", "n")
				.replace("Ñ", "N");
	}

	private static String resolveValue(final String envName, final String propertyName) {
		String value = System.getenv(envName);
		if (value == null || value.isBlank()) {
			value = System.getProperty(propertyName);
		}
		return (value == null || value.isBlank()) ? null : value.trim();
	}

	private static boolean allPassed(final Map<String, Boolean> report) {
		for (Boolean value : report.values()) {
			if (!Boolean.TRUE.equals(value)) {
				return false;
			}
		}
		return true;
	}

	private static String buildFinalReport(
			final Map<String, Boolean> report,
			final Map<String, String> evidence,
			final Path artifactsDir) {
		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test final report\n");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			builder.append("- ")
					.append(entry.getKey())
					.append(": ")
					.append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL")
					.append('\n');
		}
		for (Map.Entry<String, String> evidenceEntry : evidence.entrySet()) {
			builder.append("- ")
					.append(evidenceEntry.getKey())
					.append(": ")
					.append(evidenceEntry.getValue())
					.append('\n');
		}
		builder.append("- Screenshots directory: ").append(artifactsDir).append('\n');
		return builder.toString();
	}

	private static class LegalValidationResult {
		private final boolean passed;
		private final String finalUrl;

		private LegalValidationResult(final boolean passed, final String finalUrl) {
			this.passed = passed;
			this.finalUrl = finalUrl;
		}
	}
}
