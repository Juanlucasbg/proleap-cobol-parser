package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.junit.Test;

public class SaleadsMiNegocioWorkflowE2E {

	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern GOOGLE_SIGN_IN_PATTERN = Pattern
			.compile("(?i)(sign in with google|iniciar sesión con google|iniciar sesion con google|continuar con google)");
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
	private static final double DEFAULT_TIMEOUT_MS = 30000;
	private static final double UI_WAIT_TIMEOUT_MS = 15000;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String saleadsLoginUrl = readRequiredEnv("SALEADS_LOGIN_URL");
		final Path evidenceDir = createEvidenceDirectory();

		final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);

		final Map<String, String> legalUrls = new LinkedHashMap<>();
		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(Boolean.parseBoolean(readEnv("HEADLESS", "true"))));
			final BrowserContext context = browser.newContext(
					new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page page = context.newPage();
			page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

			page.navigate(saleadsLoginUrl);
			waitForUiLoad(page);

			report.put("Login", doLoginWithGoogle(page, evidenceDir));
			report.put("Mi Negocio menu", openMiNegocioMenu(page, evidenceDir));
			report.put("Agregar Negocio modal", validateAgregarNegocioModal(page, evidenceDir));
			report.put("Administrar Negocios view", openAdministrarNegocios(page, evidenceDir));
			report.put("Información General", validateInformacionGeneral(page));
			report.put("Detalles de la Cuenta", validateDetallesCuenta(page));
			report.put("Tus Negocios", validateTusNegocios(page));

			final LegalValidationResult termsResult = validateLegalDocument(page, "Términos y Condiciones",
					Pattern.compile("(?i)términos y condiciones|terminos y condiciones"), evidenceDir,
					"08-terminos-y-condiciones.png");
			report.put("Términos y Condiciones", termsResult.passed);
			legalUrls.put("Términos y Condiciones URL", termsResult.finalUrl);

			final LegalValidationResult privacyResult = validateLegalDocument(page, "Política de Privacidad",
					Pattern.compile("(?i)política de privacidad|politica de privacidad"), evidenceDir,
					"09-politica-de-privacidad.png");
			report.put("Política de Privacidad", privacyResult.passed);
			legalUrls.put("Política de Privacidad URL", privacyResult.finalUrl);
		}

		writeFinalReport(evidenceDir, report, legalUrls);
		assertAllStepsPassed(report, evidenceDir);
	}

	private boolean doLoginWithGoogle(final Page page, final Path evidenceDir) {
		final Locator loginButton = firstVisibleLocator(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_SIGN_IN_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_SIGN_IN_PATTERN)),
				page.getByText(GOOGLE_SIGN_IN_PATTERN));

		if (loginButton == null) {
			return false;
		}

		Page googlePopup = null;
		try {
			googlePopup = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(10000), () -> {
				loginButton.first().click();
				waitForUiLoad(page);
			});
		} catch (final PlaywrightException popupNotOpened) {
			loginButton.first().click();
			waitForUiLoad(page);
		}

		if (googlePopup != null) {
			selectGoogleAccountIfShown(googlePopup);
			if (!googlePopup.isClosed()) {
				try {
					googlePopup.waitForTimeout(2000);
				} catch (final PlaywrightException ignored) {
					// Popup can stay open if Google already redirected in-place.
				}
			}
		}

		waitForUiLoad(page);
		final boolean appVisible = isVisible(page.locator("main, [role='main'], nav, aside"), UI_WAIT_TIMEOUT_MS);
		final boolean sidebarVisible = isVisible(page.locator("aside, nav"), UI_WAIT_TIMEOUT_MS)
				&& isTextVisible(page, "Negocio", UI_WAIT_TIMEOUT_MS);
		takeScreenshot(page, evidenceDir, "01-dashboard-after-login.png", true);

		return appVisible && sidebarVisible;
	}

	private boolean openMiNegocioMenu(final Page page, final Path evidenceDir) {
		final Locator negocioSection = firstVisibleLocator(page,
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^negocio$"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^negocio$"))),
				page.getByText(Pattern.compile("(?i)^negocio$")));
		if (negocioSection != null) {
			negocioSection.first().click();
			waitForUiLoad(page);
		}

		final Locator miNegocioOption = firstVisibleLocator(page,
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^mi negocio$"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^mi negocio$"))),
				page.getByText(Pattern.compile("(?i)^mi negocio$")));
		if (miNegocioOption == null) {
			return false;
		}

		miNegocioOption.first().click();
		waitForUiLoad(page);

		final boolean agregarVisible = isTextVisible(page, "Agregar Negocio", UI_WAIT_TIMEOUT_MS);
		final boolean administrarVisible = isTextVisible(page, "Administrar Negocios", UI_WAIT_TIMEOUT_MS);
		takeScreenshot(page, evidenceDir, "02-mi-negocio-menu-expandido.png", false);

		return agregarVisible && administrarVisible;
	}

	private boolean validateAgregarNegocioModal(final Page page, final Path evidenceDir) {
		final Locator agregarNegocioOption = firstVisibleLocator(page,
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^agregar negocio$"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^agregar negocio$"))),
				page.getByText(Pattern.compile("(?i)^agregar negocio$")));
		if (agregarNegocioOption == null) {
			return false;
		}

		agregarNegocioOption.first().click();
		waitForUiLoad(page);

		final boolean titleVisible = isTextVisible(page, "Crear Nuevo Negocio", UI_WAIT_TIMEOUT_MS);
		final Locator nombreNegocioInput = firstVisibleLocator(page,
				page.getByLabel(Pattern.compile("(?i)nombre del negocio")),
				page.getByPlaceholder(Pattern.compile("(?i)nombre del negocio")),
				page.locator("input[name*='nombre'], input[id*='nombre']"));
		final boolean inputVisible = nombreNegocioInput != null && isVisible(nombreNegocioInput, UI_WAIT_TIMEOUT_MS);
		final boolean limitsTextVisible = isTextVisible(page, "Tienes 2 de 3 negocios", UI_WAIT_TIMEOUT_MS);
		final boolean cancelarVisible = isTextVisible(page, "Cancelar", UI_WAIT_TIMEOUT_MS);
		final boolean crearNegocioVisible = isTextVisible(page, "Crear Negocio", UI_WAIT_TIMEOUT_MS);
		takeScreenshot(page, evidenceDir, "03-modal-agregar-negocio.png", false);

		if (inputVisible) {
			nombreNegocioInput.first().fill("Negocio Prueba Automatización");
			waitForUiLoad(page);
		}
		final Locator cancelButton = firstVisibleLocator(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^cancelar$"))),
				page.getByText(Pattern.compile("(?i)^cancelar$")));
		if (cancelButton != null) {
			cancelButton.first().click();
			waitForUiLoad(page);
		}
		return titleVisible && inputVisible && limitsTextVisible && cancelarVisible && crearNegocioVisible;
	}

	private boolean openAdministrarNegocios(final Page page, final Path evidenceDir) {
		ensureMiNegocioExpanded(page);

		final Locator administrarNegociosOption = firstVisibleLocator(page,
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^administrar negocios$"))),
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^administrar negocios$"))),
				page.getByText(Pattern.compile("(?i)^administrar negocios$")));
		if (administrarNegociosOption == null) {
			return false;
		}

		administrarNegociosOption.first().click();
		waitForUiLoad(page);

		final boolean informacionGeneralVisible = isTextVisible(page, "Información General", UI_WAIT_TIMEOUT_MS);
		final boolean detallesCuentaVisible = isTextVisible(page, "Detalles de la Cuenta", UI_WAIT_TIMEOUT_MS);
		final boolean tusNegociosVisible = isTextVisible(page, "Tus Negocios", UI_WAIT_TIMEOUT_MS);
		final boolean seccionLegalVisible = isTextVisible(page, "Sección Legal", UI_WAIT_TIMEOUT_MS);
		takeScreenshot(page, evidenceDir, "04-administrar-negocios.png", true);

		return informacionGeneralVisible && detallesCuentaVisible && tusNegociosVisible && seccionLegalVisible;
	}

	private boolean validateInformacionGeneral(final Page page) {
		final boolean userNameVisible = isTextVisible(page, "Nombre", UI_WAIT_TIMEOUT_MS)
				|| isTextVisible(page, "Usuario", UI_WAIT_TIMEOUT_MS);
		final boolean userEmailVisible = isVisible(page.getByText(EMAIL_PATTERN), UI_WAIT_TIMEOUT_MS);
		final boolean planVisible = isTextVisible(page, "BUSINESS PLAN", UI_WAIT_TIMEOUT_MS);
		final boolean changePlanVisible = isTextVisible(page, "Cambiar Plan", UI_WAIT_TIMEOUT_MS);

		return userNameVisible && userEmailVisible && planVisible && changePlanVisible;
	}

	private boolean validateDetallesCuenta(final Page page) {
		final boolean accountCreatedVisible = isTextVisible(page, "Cuenta creada", UI_WAIT_TIMEOUT_MS);
		final boolean activeStatusVisible = isTextVisible(page, "Estado activo", UI_WAIT_TIMEOUT_MS);
		final boolean selectedLanguageVisible = isTextVisible(page, "Idioma seleccionado", UI_WAIT_TIMEOUT_MS);
		return accountCreatedVisible && activeStatusVisible && selectedLanguageVisible;
	}

	private boolean validateTusNegocios(final Page page) {
		final boolean listVisible = isTextVisible(page, "Tus Negocios", UI_WAIT_TIMEOUT_MS);
		final boolean addBusinessButtonVisible = isTextVisible(page, "Agregar Negocio", UI_WAIT_TIMEOUT_MS);
		final boolean limitsTextVisible = isTextVisible(page, "Tienes 2 de 3 negocios", UI_WAIT_TIMEOUT_MS);
		return listVisible && addBusinessButtonVisible && limitsTextVisible;
	}

	private LegalValidationResult validateLegalDocument(final Page page, final String linkText, final Pattern headingPattern,
			final Path evidenceDir, final String screenshotName) {
		final Locator link = firstVisibleLocator(page,
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$"))),
				page.getByText(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$")));
		if (link == null) {
			return new LegalValidationResult(false, "N/A");
		}

		Page destinationPage = page;
		boolean openedInNewTab = false;
		try {
			final Page popup = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(10000), () -> {
				link.first().click();
				waitForUiLoad(page);
			});
			if (popup != null) {
				destinationPage = popup;
				openedInNewTab = true;
			}
		} catch (final PlaywrightException popupNotOpened) {
			link.first().click();
		}

		waitForUiLoad(destinationPage);
		final boolean headingVisible = isVisible(
				destinationPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
				UI_WAIT_TIMEOUT_MS) || isVisible(destinationPage.getByText(headingPattern), UI_WAIT_TIMEOUT_MS);
		final boolean legalTextVisible = isVisible(destinationPage.locator("article, main, p, li"), UI_WAIT_TIMEOUT_MS);
		final String finalUrl = destinationPage.url();
		takeScreenshot(destinationPage, evidenceDir, screenshotName, true);

		if (openedInNewTab) {
			try {
				if (!destinationPage.isClosed()) {
					destinationPage.close();
				}
			} catch (final PlaywrightException ignored) {
				// Nothing to do if popup cannot be closed safely.
			}
			page.bringToFront();
			waitForUiLoad(page);
		} else {
			try {
				page.goBack(new Page.GoBackOptions().setTimeout(10000));
				waitForUiLoad(page);
			} catch (final PlaywrightException ignored) {
				// Some legal pages may open in current tab without browser history.
			}
		}

		return new LegalValidationResult(headingVisible && legalTextVisible, finalUrl);
	}

	private void ensureMiNegocioExpanded(final Page page) {
		if (isTextVisible(page, "Administrar Negocios", 2000)) {
			return;
		}

		final Locator miNegocioOption = firstVisibleLocator(page,
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^mi negocio$"))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^mi negocio$"))),
				page.getByText(Pattern.compile("(?i)^mi negocio$")));
		if (miNegocioOption != null) {
			miNegocioOption.first().click();
			waitForUiLoad(page);
		}
	}

	private void selectGoogleAccountIfShown(final Page googlePopup) {
		try {
			waitForUiLoad(googlePopup);
			final Locator accountOption = googlePopup.getByText(GOOGLE_ACCOUNT, new Page.GetByTextOptions().setExact(true));
			if (isVisible(accountOption, 7000)) {
				accountOption.first().click();
				waitForUiLoad(googlePopup);
			}
		} catch (final PlaywrightException ignored) {
			// Account selector can be skipped if the session is already authenticated.
		}
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (final PlaywrightException ignored) {
			// SPAs with background traffic may never reach network idle.
		}
		page.waitForTimeout(600);
	}

	private Locator firstVisibleLocator(final Page page, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			try {
				if (candidate.count() > 0 && isVisible(candidate.first(), 2500)) {
					return candidate;
				}
			} catch (final PlaywrightException ignored) {
				// Continue trying other selectors.
			}
		}
		return null;
	}

	private boolean isTextVisible(final Page page, final String text, final double timeoutMs) {
		return isVisible(page.getByText(Pattern.compile("(?i)^" + Pattern.quote(text) + "$")), timeoutMs)
				|| isVisible(page.getByText(Pattern.compile("(?i)" + Pattern.quote(text))), timeoutMs);
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.first()
					.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void takeScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		final Page.ScreenshotOptions options = new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName));
		if (fullPage) {
			options.setFullPage(true);
		}
		page.screenshot(options);
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path outputDir = Paths.get("target", "saleads-mi-negocio-evidence", timestamp);
		Files.createDirectories(outputDir);
		return outputDir;
	}

	private void writeFinalReport(final Path evidenceDir, final LinkedHashMap<String, Boolean> report,
			final Map<String, String> legalUrls) throws IOException {
		final StringBuilder content = new StringBuilder();
		content.append("name: saleads_mi_negocio_full_test\n");
		content.append("result_by_step:\n");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			content.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append('\n');
		}
		content.append("final_urls:\n");
		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			content.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}

		Files.writeString(evidenceDir.resolve("10-final-report.txt"), content.toString(), StandardCharsets.UTF_8);
	}

	private void assertAllStepsPassed(final LinkedHashMap<String, Boolean> report, final Path evidenceDir) {
		final List<String> failedSteps = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue()) {
				failedSteps.add(entry.getKey());
			}
		}

		Assert.assertTrue("Failed steps: " + failedSteps + ". Evidence directory: " + evidenceDir.toAbsolutePath(),
				failedSteps.isEmpty());
	}

	private String readRequiredEnv(final String envKey) {
		final String value = System.getenv(envKey);
		Assert.assertTrue("Environment variable '" + envKey
				+ "' is required. Set it to the current SaleADS login URL of the active environment.",
				value != null && !value.trim().isEmpty());
		return value.trim();
	}

	private String readEnv(final String envKey, final String defaultValue) {
		final String value = System.getenv(envKey);
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		return value.trim();
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
