package io.proleap.cobol.e2e;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SaleadsMiNegocioFullTest {

	private static final double SHORT_TIMEOUT_MS = 7_000;
	private static final double DEFAULT_TIMEOUT_MS = 15_000;
	private static final double LONG_TIMEOUT_MS = 25_000;

	private static final Pattern GOOGLE_LOGIN_PATTERN = Pattern
			.compile("(?i)(sign\\s*in\\s*with\\s*google|login\\s*with\\s*google|iniciar\\s*sesi[o\\u00f3]n\\s*con\\s*google|google)");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*mi\\s+negocio\\s*$");
	private static final Pattern NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*negocio\\s*$");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*agregar\\s+negocio\\s*$");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)^\\s*administrar\\s+negocios\\s*$");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	@Test
	public void saleads_mi_negocio_full_test() throws Exception {
		final String loginUrl = env("SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL to the SaleADS login page for the current environment before running this test.",
				loginUrl != null && !loginUrl.isBlank());

		final String googleEmail = envOrDefault("SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com");
		final boolean headless = Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true"));
		final Path evidenceDir = createEvidenceDirectory();

		final Map<String, Boolean> finalReport = new LinkedHashMap<>();
		final Map<String, String> reportDetails = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(250));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 950));
			final Page page = context.newPage();
			page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

			page.navigate(loginUrl);
			waitForUiLoad(page);

			final boolean loginResult = loginWithGoogleAndValidateShell(page, context, googleEmail, evidenceDir);
			finalReport.put("Login", loginResult);

			final boolean miNegocioMenuResult = openMiNegocioMenuAndValidate(page, evidenceDir);
			finalReport.put("Mi Negocio menu", miNegocioMenuResult);

			final boolean agregarNegocioResult = validateAgregarNegocioModal(page, evidenceDir);
			finalReport.put("Agregar Negocio modal", agregarNegocioResult);

			final boolean administrarNegociosResult = openAdministrarNegociosAndValidate(page, evidenceDir);
			finalReport.put("Administrar Negocios view", administrarNegociosResult);

			final boolean infoGeneralResult = validateInformacionGeneral(page, googleEmail);
			finalReport.put("Informacion General", infoGeneralResult);

			final boolean detallesCuentaResult = validateDetallesCuenta(page);
			finalReport.put("Detalles de la Cuenta", detallesCuentaResult);

			final boolean tusNegociosResult = validateTusNegocios(page);
			finalReport.put("Tus Negocios", tusNegociosResult);

			final LegalValidationResult terminosResult = validateLegalLink(page, context,
					Pattern.compile("(?i)t[e\\u00e9]rminos\\s+y\\s+condiciones"),
					Pattern.compile("(?i)t[e\\u00e9]rminos\\s+y\\s+condiciones"), "step-08-terminos-y-condiciones",
					evidenceDir);
			finalReport.put("Terminos y Condiciones", terminosResult.passed);
			reportDetails.put("Terminos y Condiciones URL", terminosResult.finalUrl);

			final LegalValidationResult privacidadResult = validateLegalLink(page, context,
					Pattern.compile("(?i)pol[i\\u00ed]tica\\s+de\\s+privacidad"),
					Pattern.compile("(?i)pol[i\\u00ed]tica\\s+de\\s+privacidad"), "step-09-politica-de-privacidad",
					evidenceDir);
			finalReport.put("Politica de Privacidad", privacidadResult.passed);
			reportDetails.put("Politica de Privacidad URL", privacidadResult.finalUrl);
		}

		printFinalReport(finalReport, reportDetails, evidenceDir);
		assertAllPassed(finalReport);
	}

	private boolean loginWithGoogleAndValidateShell(final Page page, final BrowserContext context, final String googleEmail,
			final Path evidenceDir) {
		final Locator googleButton = firstVisible(page,
				List.of(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Google")),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Google")),
						page.getByText(GOOGLE_LOGIN_PATTERN)),
				SHORT_TIMEOUT_MS);

		if (googleButton == null) {
			return false;
		}

		final int existingPages = context.pages().size();
		googleButton.first().click();
		page.waitForTimeout(1_500);

		Page interactivePage = page;
		if (context.pages().size() > existingPages) {
			interactivePage = context.pages().get(context.pages().size() - 1);
		}

		waitForUiLoad(interactivePage);

		// If Google account chooser appears, prefer the expected account.
		clickIfVisible(interactivePage, List.of(
				interactivePage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(googleEmail)),
				interactivePage.getByText(Pattern.compile(Pattern.quote(googleEmail)))), SHORT_TIMEOUT_MS);

		waitForUiLoad(page);

		if (interactivePage != page) {
			waitForUiLoad(interactivePage);
		}

		final boolean mainInterfaceVisible = waitForAnyVisible(
				List.of(page.locator("main"), page.locator("div").filter(new Locator.FilterOptions().setHasText("Negocio"))),
				LONG_TIMEOUT_MS);
		final boolean sidebarVisible = isSidebarVisible(page);

		if (mainInterfaceVisible && sidebarVisible) {
			captureScreenshot(page, evidenceDir.resolve("step-01-dashboard-loaded.png"), false);
		}

		return mainInterfaceVisible && sidebarVisible;
	}

	private boolean openMiNegocioMenuAndValidate(final Page page, final Path evidenceDir) {
		if (!isSidebarVisible(page)) {
			return false;
		}

		final Locator negocioSection = firstVisible(page,
				List.of(sidebar(page).getByText(NEGOCIO_PATTERN), page.getByText(NEGOCIO_PATTERN)), SHORT_TIMEOUT_MS);
		if (negocioSection == null) {
			return false;
		}

		final Locator miNegocioOption = firstVisible(page,
				List.of(sidebar(page).getByText(MI_NEGOCIO_PATTERN), page.getByText(MI_NEGOCIO_PATTERN)), SHORT_TIMEOUT_MS);
		if (miNegocioOption == null) {
			return false;
		}

		clickAndWait(miNegocioOption, page);

		boolean submenuExpanded = areMiNegocioChildrenVisible(page);
		if (!submenuExpanded) {
			clickAndWait(miNegocioOption, page);
			submenuExpanded = areMiNegocioChildrenVisible(page);
		}

		if (submenuExpanded) {
			captureScreenshot(page, evidenceDir.resolve("step-02-mi-negocio-menu-expanded.png"), false);
		}

		return submenuExpanded;
	}

	private boolean validateAgregarNegocioModal(final Page page, final Path evidenceDir) {
		expandMiNegocioIfNeeded(page);

		final Locator agregarNegocio = firstVisible(page,
				List.of(sidebar(page).getByText(AGREGAR_NEGOCIO_PATTERN), page.getByText(AGREGAR_NEGOCIO_PATTERN)),
				SHORT_TIMEOUT_MS);
		if (agregarNegocio == null) {
			return false;
		}

		clickAndWait(agregarNegocio, page);

		final boolean titleVisible = waitForAnyVisible(
				List.of(page.getByRole(AriaRole.HEADING,
						new Page.GetByRoleOptions().setName("Crear Nuevo Negocio")),
						page.getByText(Pattern.compile("(?i)crear\\s+nuevo\\s+negocio"))),
				SHORT_TIMEOUT_MS);
		final boolean inputVisible = waitForAnyVisible(List.of(
				page.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
				page.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
				page.locator("input")),
				SHORT_TIMEOUT_MS);
		final boolean quotaVisible = isTextVisible(page, Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"));
		final boolean cancelarVisible = waitForAnyVisible(
				List.of(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
						page.getByText(Pattern.compile("(?i)^\\s*cancelar\\s*$"))),
				SHORT_TIMEOUT_MS);
		final boolean crearNegocioVisible = waitForAnyVisible(List.of(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")),
				page.getByText(Pattern.compile("(?i)^\\s*crear\\s+negocio\\s*$"))), SHORT_TIMEOUT_MS);

		if (titleVisible) {
			captureScreenshot(page, evidenceDir.resolve("step-03-agregar-negocio-modal.png"), false);
		}

		clickIfVisible(page,
				List.of(page.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
						page.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio"))),
				SHORT_TIMEOUT_MS);

		final Locator input = firstVisible(page,
				List.of(page.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
						page.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")), page.locator("input")),
				SHORT_TIMEOUT_MS);
		if (input != null) {
			input.fill("Negocio Prueba Automatizacion");
		}

		clickIfVisible(page,
				List.of(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
						page.getByText(Pattern.compile("(?i)^\\s*cancelar\\s*$"))),
				SHORT_TIMEOUT_MS);
		waitForUiLoad(page);

		return titleVisible && inputVisible && quotaVisible && cancelarVisible && crearNegocioVisible;
	}

	private boolean openAdministrarNegociosAndValidate(final Page page, final Path evidenceDir) {
		expandMiNegocioIfNeeded(page);

		final Locator administrarNegocios = firstVisible(page, List.of(sidebar(page).getByText(ADMINISTRAR_NEGOCIOS_PATTERN),
				page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN)), SHORT_TIMEOUT_MS);
		if (administrarNegocios == null) {
			return false;
		}

		clickAndWait(administrarNegocios, page);

		final boolean informacionGeneralVisible = isTextVisible(page,
				Pattern.compile("(?i)informaci[o\\u00f3]n\\s+general"));
		final boolean detallesCuentaVisible = isTextVisible(page, Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta"));
		final boolean tusNegociosVisible = isTextVisible(page, Pattern.compile("(?i)tus\\s+negocios"));
		final boolean seccionLegalVisible = isTextVisible(page, Pattern.compile("(?i)secci[o\\u00f3]n\\s+legal"));

		if (informacionGeneralVisible) {
			captureScreenshot(page, evidenceDir.resolve("step-04-administrar-negocios.png"), true);
		}

		return informacionGeneralVisible && detallesCuentaVisible && tusNegociosVisible && seccionLegalVisible;
	}

	private boolean validateInformacionGeneral(final Page page, final String googleEmail) {
		final String sectionText = sectionText(page, Pattern.compile("(?i)informaci[o\\u00f3]n\\s+general"));
		final String normalized = normalize(sectionText);

		final boolean userNameVisible = normalized.lines().map(String::trim)
				.anyMatch(line -> !line.isEmpty() && line.length() >= 3 && !line.contains("@")
						&& !line.contains("informacion general") && !line.contains("business plan")
						&& !line.contains("cambiar plan"));
		final boolean userEmailVisible = sectionText.contains(googleEmail) || EMAIL_PATTERN.matcher(sectionText).find();
		final boolean businessPlanVisible = normalized.contains("business plan");
		final boolean cambiarPlanVisible = normalized.contains("cambiar plan");

		return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean validateDetallesCuenta(final Page page) {
		return isTextVisible(page, Pattern.compile("(?i)cuenta\\s+creada"))
				&& isTextVisible(page, Pattern.compile("(?i)estado\\s+activo"))
				&& isTextVisible(page, Pattern.compile("(?i)idioma\\s+seleccionado"));
	}

	private boolean validateTusNegocios(final Page page) {
		final Locator tusNegociosSection = firstSectionContainingText(page, Pattern.compile("(?i)tus\\s+negocios"));
		final boolean sectionVisible = waitForVisible(tusNegociosSection, SHORT_TIMEOUT_MS);
		final boolean addButtonVisible = waitForAnyVisible(
				List.of(tusNegociosSection.getByRole(AriaRole.BUTTON,
						new Locator.GetByRoleOptions().setName("Agregar Negocio")),
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio"))),
				SHORT_TIMEOUT_MS);
		final boolean quotaVisible = isTextVisible(page, Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"));
		final boolean listVisible = sectionVisible
				&& (tusNegociosSection.locator("li, tr, article, [role='row']").count() > 0
						|| tusNegociosSection.locator("div").count() > 3);

		return sectionVisible && addButtonVisible && quotaVisible && listVisible;
	}

	private LegalValidationResult validateLegalLink(final Page appPage, final BrowserContext context, final Pattern linkPattern,
			final Pattern headingPattern, final String screenshotName, final Path evidenceDir) {
		expandMiNegocioIfNeeded(appPage);

		final Locator link = firstVisible(appPage,
				List.of(appPage.getByText(linkPattern), appPage.locator("a, button").getByText(linkPattern)),
				SHORT_TIMEOUT_MS);
		if (link == null) {
			return new LegalValidationResult(false, "N/A");
		}

		final String appUrlBeforeClick = appPage.url();
		final int pagesBeforeClick = context.pages().size();

		link.click();
		appPage.waitForTimeout(1_750);

		Page legalPage = appPage;
		boolean openedNewTab = false;

		if (context.pages().size() > pagesBeforeClick) {
			legalPage = context.pages().get(context.pages().size() - 1);
			openedNewTab = true;
		}

		waitForUiLoad(legalPage);

		final boolean headingVisible = waitForAnyVisible(
				List.of(legalPage.getByText(headingPattern),
						legalPage.locator("h1, h2, h3").getByText(headingPattern)),
				LONG_TIMEOUT_MS);
		final boolean legalContentVisible = hasLegalContent(legalPage);

		captureScreenshot(legalPage, evidenceDir.resolve(screenshotName + ".png"), true);
		final String finalUrl = legalPage.url();

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else if (!appUrlBeforeClick.equals(appPage.url())) {
			try {
				appPage.goBack();
				waitForUiLoad(appPage);
			} catch (PlaywrightException ignored) {
				// Cleanup best effort when legal page navigates in the same tab.
			}
		}

		return new LegalValidationResult(headingVisible && legalContentVisible, finalUrl);
	}

	private void expandMiNegocioIfNeeded(final Page page) {
		if (areMiNegocioChildrenVisible(page)) {
			return;
		}

		final Locator miNegocioOption = firstVisible(page,
				List.of(sidebar(page).getByText(MI_NEGOCIO_PATTERN), page.getByText(MI_NEGOCIO_PATTERN)), SHORT_TIMEOUT_MS);
		if (miNegocioOption != null) {
			clickAndWait(miNegocioOption, page);
		}
	}

	private boolean areMiNegocioChildrenVisible(final Page page) {
		final Locator agregarNegocio = firstVisible(page,
				List.of(sidebar(page).getByText(AGREGAR_NEGOCIO_PATTERN), page.getByText(AGREGAR_NEGOCIO_PATTERN)),
				SHORT_TIMEOUT_MS / 2);
		final Locator administrarNegocios = firstVisible(page, List.of(sidebar(page).getByText(ADMINISTRAR_NEGOCIOS_PATTERN),
				page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN)), SHORT_TIMEOUT_MS / 2);

		return agregarNegocio != null && administrarNegocios != null;
	}

	private Locator sidebar(final Page page) {
		final Locator sidebar = firstVisible(page, List.of(page.locator("aside"), page.getByRole(AriaRole.NAVIGATION)),
				SHORT_TIMEOUT_MS);
		return sidebar == null ? page.locator("body") : sidebar;
	}

	private boolean isSidebarVisible(final Page page) {
		return waitForAnyVisible(List.of(page.locator("aside"), page.getByRole(AriaRole.NAVIGATION)), SHORT_TIMEOUT_MS);
	}

	private String sectionText(final Page page, final Pattern sectionHeadingPattern) {
		final Locator section = firstSectionContainingText(page, sectionHeadingPattern);
		if (waitForVisible(section, SHORT_TIMEOUT_MS)) {
			try {
				return section.innerText();
			} catch (PlaywrightException ignored) {
				return "";
			}
		}

		try {
			return page.locator("body").innerText();
		} catch (PlaywrightException ignored) {
			return "";
		}
	}

	private Locator firstSectionContainingText(final Page page, final Pattern pattern) {
		final Locator sections = page.locator("section");
		final int sectionCount = sections.count();

		for (int i = 0; i < sectionCount; i++) {
			final Locator candidate = sections.nth(i);
			try {
				final String text = candidate.innerText();
				if (pattern.matcher(text).find()) {
					return candidate;
				}
			} catch (PlaywrightException ignored) {
				// Keep scanning other sections.
			}
		}

		return page.locator("body");
	}

	private boolean hasLegalContent(final Page page) {
		try {
			final String text = normalize(page.locator("body").innerText());
			return text.length() > 200 && (text.contains("terminos") || text.contains("privacidad") || text.contains("legal"));
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private boolean isTextVisible(final Page page, final Pattern pattern) {
		return waitForAnyVisible(List.of(page.getByText(pattern)), SHORT_TIMEOUT_MS);
	}

	private void clickAndWait(final Locator locator, final Page page) {
		locator.first().click();
		waitForUiLoad(page);
	}

	private boolean clickIfVisible(final Page page, final List<Locator> locatorOptions, final double timeoutMs) {
		final Locator match = firstVisible(page, locatorOptions, timeoutMs);
		if (match != null) {
			clickAndWait(match, page);
			return true;
		}
		return false;
	}

	private Locator firstVisible(final Page page, final List<Locator> locators, final double timeoutMs) {
		for (final Locator locator : locators) {
			if (waitForVisible(locator.first(), timeoutMs)) {
				return locator.first();
			}
		}
		return null;
	}

	private boolean waitForAnyVisible(final List<Locator> locators, final double timeoutMs) {
		for (final Locator locator : locators) {
			if (waitForVisible(locator, timeoutMs)) {
				return true;
			}
		}
		return false;
	}

	private boolean waitForVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (PlaywrightException ignored) {
			// Some SPA interactions do not trigger a full navigation.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// Fallback to fixed wait when network is chatty.
		}
		page.waitForTimeout(500);
	}

	private void captureScreenshot(final Page page, final Path targetPath, final boolean fullPage) {
		try {
			page.screenshot(new Page.ScreenshotOptions().setPath(targetPath).setFullPage(fullPage));
		} catch (PlaywrightException ignored) {
			// Evidence capture is best effort.
		}
	}

	private Path createEvidenceDirectory() throws Exception {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private void printFinalReport(final Map<String, Boolean> report, final Map<String, String> details, final Path evidenceDir) {
		System.out.println("=== SaleADS Mi Negocio - Final Report ===");
		report.forEach((step, passed) -> System.out.println(step + ": " + (passed ? "PASS" : "FAIL")));
		details.forEach((key, value) -> System.out.println(key + ": " + value));
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("=========================================");
	}

	private void assertAllPassed(final Map<String, Boolean> report) {
		final List<String> failedSteps = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		Assert.assertTrue("Failed workflow checks: " + String.join(", ", failedSteps), failedSteps.isEmpty());
	}

	private static String env(final String key) {
		return System.getenv(key);
	}

	private static String envOrDefault(final String key, final String fallback) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String normalize(final String text) {
		final String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
		return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	private static final class LegalValidationResult {
		private final boolean passed;
		private final String finalUrl;

		private LegalValidationResult(final boolean passed, final String finalUrl) {
			this.passed = passed;
			this.finalUrl = finalUrl;
		}
	}
}
