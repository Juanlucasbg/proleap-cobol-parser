package io.proleap.saleads;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SaleAdsMiNegocioWorkflowTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final int CLICK_TIMEOUT_MS = 15_000;
	private static final int SHORT_TIMEOUT_MS = 6_000;
	private static final DateTimeFormatter EVIDENCE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String expectedGoogleAccount = firstNonBlank(
				System.getProperty("saleads.google.account"),
				System.getenv("SALEADS_GOOGLE_ACCOUNT"),
				DEFAULT_GOOGLE_ACCOUNT);
		final String startUrl = firstNonBlank(
				System.getProperty("saleads.start.url"),
				System.getenv("SALEADS_START_URL"));
		final boolean headless = Boolean.parseBoolean(firstNonBlank(
				System.getProperty("saleads.headless"),
				System.getenv("SALEADS_HEADLESS"),
				"false"));
		final Path userDataDir = Paths.get(firstNonBlank(
				System.getProperty("saleads.user.data.dir"),
				System.getenv("SALEADS_USER_DATA_DIR"),
				"target/saleads-browser-profile"));

		Files.createDirectories(userDataDir);
		final Path evidenceDir = createEvidenceDirectory();
		final Map<String, Boolean> report = createReport();
		final Map<String, String> capturedUrls = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();

		try (Playwright playwright = Playwright.create();
			 BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir,
					 new BrowserType.LaunchPersistentContextOptions()
							 .setHeadless(headless)
							 .setViewportSize(1600, 1000))) {

			final Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
			page.bringToFront();

			if (isNonBlank(startUrl)) {
				page.navigate(startUrl);
				waitForUiLoad(page);
			} else {
				Assume.assumeTrue(
						"Set SALEADS_START_URL (or -Dsaleads.start.url) or open the SaleADS login page in the persistent browser profile.",
						!page.url().startsWith("about:blank"));
			}

			// Step 1 - Login with Google
			final boolean loginPass = performLogin(page, context, expectedGoogleAccount, evidenceDir);
			record("Login", loginPass, report, failures);

			// Step 2 - Open Mi Negocio menu
			final boolean menuPass = openMiNegocioMenu(page, evidenceDir);
			record("Mi Negocio menu", menuPass, report, failures);

			// Step 3 - Validate Agregar Negocio modal
			final boolean addBusinessModalPass = validateAgregarNegocioModal(page, evidenceDir);
			record("Agregar Negocio modal", addBusinessModalPass, report, failures);

			// Step 4 - Open Administrar Negocios
			final boolean administrarViewPass = openAdministrarNegocios(page, evidenceDir);
			record("Administrar Negocios view", administrarViewPass, report, failures);

			// Step 5 - Validate Informacion General
			final boolean infoGeneralPass = validateInformacionGeneral(page);
			record("Información General", infoGeneralPass, report, failures);

			// Step 6 - Validate Detalles de la Cuenta
			final boolean accountDetailsPass = validateDetallesCuenta(page);
			record("Detalles de la Cuenta", accountDetailsPass, report, failures);

			// Step 7 - Validate Tus Negocios
			final boolean tusNegociosPass = validateTusNegocios(page);
			record("Tus Negocios", tusNegociosPass, report, failures);

			// Step 8 - Validate Terminos y Condiciones (same tab or new tab)
			final LegalValidationResult termsResult = validateLegalLink(
					context,
					page,
					evidenceDir,
					"Términos y Condiciones",
					"08-terminos-y-condiciones.png");
			capturedUrls.put("Términos y Condiciones URL", termsResult.url);
			record("Términos y Condiciones", termsResult.passed, report, failures);

			// Step 9 - Validate Politica de Privacidad (same tab or new tab)
			final LegalValidationResult privacyResult = validateLegalLink(
					context,
					page,
					evidenceDir,
					"Política de Privacidad",
					"09-politica-de-privacidad.png");
			capturedUrls.put("Política de Privacidad URL", privacyResult.url);
			record("Política de Privacidad", privacyResult.passed, report, failures);
		}

		writeFinalReport(evidenceDir, report, capturedUrls, failures);
		Assert.assertTrue("SaleADS Mi Negocio workflow has failures. Evidence: " + evidenceDir, failures.isEmpty());
	}

	private boolean performLogin(final Page page, final BrowserContext context, final String expectedGoogleAccount, final Path evidenceDir) {
		final Locator loginButton = firstVisible(
				page.getByText("Sign in with Google"),
				page.getByText("Iniciar sesión con Google"),
				page.getByText("Continuar con Google"),
				page.getByText("Google"));
		if (loginButton == null) {
			return false;
		}

		Page accountSelectionPage = null;
		try {
			accountSelectionPage = context.waitForPage(() -> clickAndWait(page, loginButton),
					new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			clickAndWait(page, loginButton);
		}

		final Page accountPage = accountSelectionPage != null ? accountSelectionPage : page;
		final Locator requestedAccount = accountPage.getByText(expectedGoogleAccount);
		if (isVisible(requestedAccount, SHORT_TIMEOUT_MS)) {
			clickAndWait(accountPage, requestedAccount.first());
		}

		if (accountSelectionPage != null) {
			try {
				accountSelectionPage.waitForClose(new Page.WaitForCloseOptions().setTimeout(30_000));
			} catch (PlaywrightException ignored) {
				// OAuth tabs are not guaranteed to auto-close in all environments.
			}
			page.bringToFront();
		}

		waitForUiLoad(page);
		final boolean dashboardVisible = hasAnyVisibleText(page, "Dashboard", "Inicio", "Panel", "Mi Negocio", "Negocio");
		final boolean sidebarVisible = isVisible(page.locator("aside, nav").first(), SHORT_TIMEOUT_MS);
		takeScreenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), true);
		return dashboardVisible && sidebarVisible;
	}

	private boolean openMiNegocioMenu(final Page page, final Path evidenceDir) {
		final Locator menuTrigger = firstVisible(
				page.getByText("Mi Negocio"),
				page.getByText("Negocio"));
		if (menuTrigger == null) {
			return false;
		}

		if (!areBusinessSubmenuItemsVisible(page)) {
			clickAndWait(page, menuTrigger);
		}

		final boolean expanded = areBusinessSubmenuItemsVisible(page);
		takeScreenshot(page, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), true);
		return expanded;
	}

	private boolean validateAgregarNegocioModal(final Page page, final Path evidenceDir) {
		final Locator addBusinessMenuItem = firstVisible(
				page.getByText("Agregar Negocio"),
				page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName("Agregar Negocio")));
		if (addBusinessMenuItem == null) {
			return false;
		}

		clickAndWait(page, addBusinessMenuItem);
		final boolean titleVisible = isVisible(page.getByText("Crear Nuevo Negocio"), CLICK_TIMEOUT_MS);
		final boolean fieldVisible = isVisible(firstVisible(
				page.getByLabel("Nombre del Negocio"),
				page.getByPlaceholder("Nombre del Negocio"),
				page.getByText("Nombre del Negocio")), CLICK_TIMEOUT_MS);
		final boolean businessCountVisible = isVisible(page.getByText("Tienes 2 de 3 negocios"), CLICK_TIMEOUT_MS);
		final boolean cancelVisible = isVisible(page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName("Cancelar")), CLICK_TIMEOUT_MS);
		final boolean createVisible = isVisible(page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName("Crear Negocio")), CLICK_TIMEOUT_MS);
		takeScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), true);

		final Locator nameInput = firstVisible(
				page.getByLabel("Nombre del Negocio"),
				page.getByPlaceholder("Nombre del Negocio"));
		if (nameInput != null && nameInput.isVisible()) {
			nameInput.fill("Negocio Prueba Automatización");
		}

		final Locator cancelButton = firstVisible(
				page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName("Cancelar")),
				page.getByText("Cancelar"));
		if (cancelButton != null) {
			clickAndWait(page, cancelButton);
		}

		return titleVisible && fieldVisible && businessCountVisible && cancelVisible && createVisible;
	}

	private boolean openAdministrarNegocios(final Page page, final Path evidenceDir) {
		if (!areBusinessSubmenuItemsVisible(page)) {
			final Locator menuTrigger = firstVisible(page.getByText("Mi Negocio"), page.getByText("Negocio"));
			if (menuTrigger != null) {
				clickAndWait(page, menuTrigger);
			}
		}

		final Locator manageItem = firstVisible(
				page.getByText("Administrar Negocios"),
				page.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
						new Page.GetByRoleOptions().setName("Administrar Negocios")));
		if (manageItem == null) {
			return false;
		}

		clickAndWait(page, manageItem);
		final boolean infoGeneral = isVisible(page.getByText("Información General"), CLICK_TIMEOUT_MS);
		final boolean accountDetails = isVisible(page.getByText("Detalles de la Cuenta"), CLICK_TIMEOUT_MS);
		final boolean businesses = isVisible(page.getByText("Tus Negocios"), CLICK_TIMEOUT_MS);
		final boolean legal = hasAnyVisibleText(page, "Sección Legal", "Legal");
		takeScreenshot(page, evidenceDir.resolve("04-administrar-negocios-full-page.png"), true);
		return infoGeneral && accountDetails && businesses && legal;
	}

	private boolean validateInformacionGeneral(final Page page) {
		final boolean emailVisible = page.locator("xpath=//*[contains(normalize-space(.), '@')]").first().isVisible();
		final boolean planVisible = isVisible(page.getByText("BUSINESS PLAN"), SHORT_TIMEOUT_MS);
		final boolean changePlanVisible = isVisible(page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName("Cambiar Plan")), SHORT_TIMEOUT_MS);
		final boolean possibleNameLabel = hasAnyVisibleText(page, "Nombre", "Usuario", "Perfil");
		return emailVisible && planVisible && changePlanVisible && possibleNameLabel;
	}

	private boolean validateDetallesCuenta(final Page page) {
		return isVisible(page.getByText("Cuenta creada"), SHORT_TIMEOUT_MS)
				&& hasAnyVisibleText(page, "Estado activo", "Activo")
				&& isVisible(page.getByText("Idioma seleccionado"), SHORT_TIMEOUT_MS);
	}

	private boolean validateTusNegocios(final Page page) {
		final boolean titleVisible = isVisible(page.getByText("Tus Negocios"), SHORT_TIMEOUT_MS);
		final boolean addBusinessButtonVisible = isVisible(page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName("Agregar Negocio")), SHORT_TIMEOUT_MS);
		final boolean businessCountVisible = isVisible(page.getByText("Tienes 2 de 3 negocios"), SHORT_TIMEOUT_MS);
		return titleVisible && addBusinessButtonVisible && businessCountVisible;
	}

	private LegalValidationResult validateLegalLink(
			final BrowserContext context,
			final Page appPage,
			final Path evidenceDir,
			final String linkText,
			final String screenshotName) {
		final String appUrlBefore = appPage.url();
		final Locator link = firstVisible(
				appPage.getByText(linkText),
				appPage.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new Page.GetByRoleOptions().setName(linkText)));
		if (link == null) {
			return new LegalValidationResult(false, "N/A");
		}

		Page legalPage;
		boolean openedNewTab = false;

		try {
			legalPage = context.waitForPage(() -> clickAndWait(appPage, link),
					new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS));
			openedNewTab = true;
		} catch (PlaywrightException ignored) {
			clickAndWait(appPage, link);
			legalPage = appPage;
		}

		legalPage.bringToFront();
		waitForUiLoad(legalPage);

		final boolean headingVisible = isVisible(legalPage.getByText(linkText), CLICK_TIMEOUT_MS);
		final boolean legalTextVisible = hasAnyVisibleText(
				legalPage,
				"términos",
				"condiciones",
				"privacidad",
				"datos",
				"responsabilidad");
		final String finalUrl = legalPage.url();
		takeScreenshot(legalPage, evidenceDir.resolve(screenshotName), true);

		if (openedNewTab && legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
		} else {
			try {
				appPage.goBack(new Page.GoBackOptions().setTimeout(CLICK_TIMEOUT_MS));
				waitForUiLoad(appPage);
			} catch (PlaywrightException ignored) {
				appPage.navigate(appUrlBefore);
				waitForUiLoad(appPage);
			}
		}

		return new LegalValidationResult(headingVisible && legalTextVisible, finalUrl);
	}

	private void record(
			final String label,
			final boolean passed,
			final Map<String, Boolean> report,
			final List<String> failures) {
		report.put(label, passed);
		if (!passed) {
			failures.add(label + " -> FAIL");
		}
	}

	private Map<String, Boolean> createReport() {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
		return report;
	}

	private Path createEvidenceDirectory() throws IOException {
		final Path directory = Paths.get("target", "saleads-mi-negocio-evidence", EVIDENCE_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(directory);
		return directory;
	}

	private void writeFinalReport(
			final Path evidenceDir,
			final Map<String, Boolean> report,
			final Map<String, String> capturedUrls,
			final List<String> failures) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
		builder.append("Evidence directory: ").append(evidenceDir).append(System.lineSeparator()).append(System.lineSeparator());
		builder.append("Step results:").append(System.lineSeparator());

		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			builder.append(" - ")
					.append(entry.getKey())
					.append(": ")
					.append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL")
					.append(System.lineSeparator());
		}

		builder.append(System.lineSeparator()).append("Captured URLs:").append(System.lineSeparator());
		for (Map.Entry<String, String> entry : capturedUrls.entrySet()) {
			builder.append(" - ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}

		builder.append(System.lineSeparator()).append("Failures:").append(System.lineSeparator());
		if (failures.isEmpty()) {
			builder.append(" - None").append(System.lineSeparator());
		} else {
			for (String failure : failures) {
				builder.append(" - ").append(failure).append(System.lineSeparator());
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), builder.toString(), StandardCharsets.UTF_8);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout(CLICK_TIMEOUT_MS));
		waitForUiLoad(page);
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(CLICK_TIMEOUT_MS));
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// Some views keep active polling and never become fully idle.
		}
		page.waitForTimeout(400);
	}

	private void takeScreenshot(final Page page, final Path target, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(target).setFullPage(fullPage));
	}

	private boolean areBusinessSubmenuItemsVisible(final Page page) {
		return isVisible(page.getByText("Agregar Negocio"), SHORT_TIMEOUT_MS)
				&& isVisible(page.getByText("Administrar Negocios"), SHORT_TIMEOUT_MS);
	}

	private boolean hasAnyVisibleText(final Page page, final String... texts) {
		return Arrays.stream(texts)
				.filter(Objects::nonNull)
				.anyMatch(text -> {
					try {
						return page.getByText(text).first().isVisible();
					} catch (PlaywrightException ignored) {
						return false;
					}
				});
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		if (locator == null) {
			return false;
		}

		try {
			locator.first().waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout((double) timeoutMs));
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private Locator firstVisible(final Locator... locators) {
		for (Locator locator : locators) {
			if (locator == null) {
				continue;
			}
			try {
				if (locator.first().isVisible()) {
					return locator.first();
				}
			} catch (PlaywrightException ignored) {
				// Try next locator candidate.
			}
		}
		return null;
	}

	private boolean isNonBlank(final String value) {
		return value != null && !value.isBlank();
	}

	private String firstNonBlank(final String... values) {
		for (String value : values) {
			if (isNonBlank(value)) {
				return value;
			}
		}
		return null;
	}

	private static final class LegalValidationResult {
		private final boolean passed;
		private final String url;

		private LegalValidationResult(final boolean passed, final String url) {
			this.passed = passed;
			this.url = url;
		}
	}
}
