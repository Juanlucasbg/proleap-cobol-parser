package io.proleap.automation;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioFullTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String loginUrl = env("SALEADS_LOGIN_URL");
		final String cdpUrl = env("SALEADS_CDP_URL");
		final boolean headless = Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true"));
		final String googleAccount = envOrDefault("SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);
		final Path evidenceDir = Paths.get(envOrDefault("SALEADS_EVIDENCE_DIR", "target/saleads-evidence"));

		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL for standalone execution or SALEADS_CDP_URL for an existing browser session.",
				hasText(loginUrl) || hasText(cdpUrl));

		Files.createDirectories(evidenceDir);

		final Map<String, Boolean> report = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final Path reportPath = evidenceDir.resolve("final-report.txt");

		try (Playwright playwright = Playwright.create()) {
			final BrowserSession session = createSession(playwright, cdpUrl, headless);
			final Browser browser = session.browser;
			final BrowserContext context = session.context;
			final Page page = session.page;

			try {
				if (hasText(loginUrl)) {
					page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(45000));
					waitForUi(page);
				} else if (page.url().startsWith("about:blank")) {
					failures.add("Connected via CDP but no SaleADS tab was found. Open the login page first.");
				}

				// Step 1 - Login with Google.
				boolean loginOk = executeLoginWithGoogle(page, context, googleAccount, evidenceDir, failures);
				report.put("Login", loginOk);

				// Step 2 - Open Mi Negocio menu.
				boolean menuOk = openMiNegocioMenu(page, evidenceDir, failures);
				report.put("Mi Negocio menu", menuOk);

				// Step 3 - Validate Agregar Negocio modal.
				boolean modalOk = validateAgregarNegocioModal(page, evidenceDir, failures);
				report.put("Agregar Negocio modal", modalOk);

				// Step 4 - Open Administrar Negocios.
				boolean administrarOk = openAdministrarNegocios(page, evidenceDir, failures);
				report.put("Administrar Negocios view", administrarOk);

				// Step 5 - Validate Informacion General.
				boolean infoGeneralOk = validateInformacionGeneral(page, failures);
				report.put("Informacion General", infoGeneralOk);

				// Step 6 - Validate Detalles de la Cuenta.
				boolean cuentaOk = validateDetallesCuenta(page, failures);
				report.put("Detalles de la Cuenta", cuentaOk);

				// Step 7 - Validate Tus Negocios.
				boolean negociosOk = validateTusNegocios(page, failures);
				report.put("Tus Negocios", negociosOk);

				// Step 8 - Validate Terminos y Condiciones.
				LegalValidationResult terminosResult = validateLegalLink(page, context,
						new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
						new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
						evidenceDir.resolve("08-terminos-y-condiciones.png"), failures);
				report.put("Términos y Condiciones", terminosResult.passed);
				legalUrls.put("Términos y Condiciones URL", terminosResult.finalUrl);

				// Step 9 - Validate Politica de Privacidad.
				LegalValidationResult privacidadResult = validateLegalLink(page, context,
						new String[] { "Política de Privacidad", "Politica de Privacidad" },
						new String[] { "Política de Privacidad", "Politica de Privacidad" },
						evidenceDir.resolve("09-politica-de-privacidad.png"), failures);
				report.put("Política de Privacidad", privacidadResult.passed);
				legalUrls.put("Política de Privacidad URL", privacidadResult.finalUrl);
			} finally {
				writeFinalReport(report, legalUrls, failures, reportPath);
				if (session.closeContext) {
					context.close();
				}
				if (session.closeBrowser) {
					browser.close();
				}
			}
		}

		assertTrue("Workflow validation failed:\n - " + String.join("\n - ", failures), failures.isEmpty());
	}

	private boolean executeLoginWithGoogle(final Page appPage, final BrowserContext context, final String googleAccount,
			final Path evidenceDir, final List<String> failures) {
		Page googlePage = null;

		try {
			googlePage = context.waitForPage(
					() -> clickByVisibleText(appPage,
							"Sign in with Google",
							"Iniciar sesión con Google",
							"Inicia sesión con Google",
							"Continuar con Google",
							"Login with Google",
							"Google"),
					new BrowserContext.WaitForPageOptions().setTimeout(7000));
		} catch (PlaywrightException ignored) {
			if (!clickByVisibleText(appPage,
					"Sign in with Google",
					"Iniciar sesión con Google",
					"Inicia sesión con Google",
					"Continuar con Google",
					"Login with Google",
					"Google")) {
				failures.add("Login button ('Sign in with Google') was not found.");
				return false;
			}
		}

		if (googlePage != null) {
			waitForUi(googlePage);
			clickByVisibleText(googlePage, googleAccount);
			waitForUi(googlePage);
			if (!googlePage.isClosed()) {
				googlePage.bringToFront();
			}
			appPage.bringToFront();
		} else {
			// Some environments render Google account selection in the same tab.
			clickByVisibleText(appPage, googleAccount);
		}

		waitForUi(appPage);

		boolean mainInterfaceVisible = isVisibleByText(appPage, "Negocio")
				|| isVisibleByText(appPage, "Mi Negocio")
				|| isVisibleByText(appPage, "Dashboard")
				|| isVisibleByText(appPage, "Inicio");

		boolean leftSidebarVisible = appPage.locator("aside").first().isVisible()
				|| isVisibleByText(appPage, "Negocio");

		if (!mainInterfaceVisible) {
			failures.add("Main application interface did not appear after Google login.");
		}
		if (!leftSidebarVisible) {
			failures.add("Left sidebar navigation is not visible after login.");
		}

		takeScreenshot(appPage, evidenceDir.resolve("01-dashboard-loaded.png"), false);
		return mainInterfaceVisible && leftSidebarVisible;
	}

	private boolean openMiNegocioMenu(final Page page, final Path evidenceDir, final List<String> failures) {
		clickByVisibleText(page, "Negocio");
		clickByVisibleText(page, "Mi Negocio");

		boolean agregarNegocioVisible = isVisibleByText(page, "Agregar Negocio");
		boolean administrarVisible = isVisibleByText(page, "Administrar Negocios");

		if (!agregarNegocioVisible) {
			failures.add("'Agregar Negocio' is not visible after opening Mi Negocio.");
		}
		if (!administrarVisible) {
			failures.add("'Administrar Negocios' is not visible after opening Mi Negocio.");
		}

		takeScreenshot(page, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
		return agregarNegocioVisible && administrarVisible;
	}

	private boolean validateAgregarNegocioModal(final Page page, final Path evidenceDir, final List<String> failures) {
		if (!clickByVisibleText(page, "Agregar Negocio")) {
			failures.add("Could not click 'Agregar Negocio' to open modal.");
			return false;
		}

		waitForUi(page);
		boolean titleVisible = isVisibleByText(page, "Crear Nuevo Negocio");
		boolean inputVisible = hasVisibleElement(
				page.getByLabel("Nombre del Negocio"),
				page.getByPlaceholder("Nombre del Negocio"),
				page.locator("input[name='businessName']"));
		boolean quotaVisible = isVisibleByText(page, "Tienes 2 de 3 negocios");
		boolean cancelarVisible = isVisibleByText(page, "Cancelar");
		boolean crearVisible = isVisibleByText(page, "Crear Negocio");

		if (!titleVisible) {
			failures.add("Modal title 'Crear Nuevo Negocio' was not found.");
		}
		if (!inputVisible) {
			failures.add("Input field 'Nombre del Negocio' was not found.");
		}
		if (!quotaVisible) {
			failures.add("Text 'Tienes 2 de 3 negocios' was not found in the modal.");
		}
		if (!cancelarVisible || !crearVisible) {
			failures.add("Modal buttons 'Cancelar' and/or 'Crear Negocio' were not found.");
		}

		Locator nombreInput = page.getByLabel("Nombre del Negocio");
		if (!nombreInput.first().isVisible()) {
			nombreInput = page.getByPlaceholder("Nombre del Negocio");
		}
		if (nombreInput.first().isVisible()) {
			nombreInput.first().click();
			waitForUi(page);
			nombreInput.first().fill("Negocio Prueba Automatización");
			waitForUi(page);
		}

		takeScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);
		clickByVisibleText(page, "Cancelar");
		waitForUi(page);

		return titleVisible && inputVisible && quotaVisible && cancelarVisible && crearVisible;
	}

	private boolean openAdministrarNegocios(final Page page, final Path evidenceDir, final List<String> failures) {
		if (!isVisibleByText(page, "Administrar Negocios")) {
			clickByVisibleText(page, "Mi Negocio");
			waitForUi(page);
		}
		if (!clickByVisibleText(page, "Administrar Negocios")) {
			failures.add("Could not open 'Administrar Negocios'.");
			return false;
		}

		waitForUi(page);
		boolean informacionGeneral = isVisibleByText(page, "Información General");
		boolean detallesCuenta = isVisibleByText(page, "Detalles de la Cuenta");
		boolean tusNegocios = isVisibleByText(page, "Tus Negocios");
		boolean seccionLegal = isVisibleByText(page, "Sección Legal") || isVisibleByText(page, "Seccion Legal");

		if (!informacionGeneral) {
			failures.add("'Información General' section was not found.");
		}
		if (!detallesCuenta) {
			failures.add("'Detalles de la Cuenta' section was not found.");
		}
		if (!tusNegocios) {
			failures.add("'Tus Negocios' section was not found.");
		}
		if (!seccionLegal) {
			failures.add("'Sección Legal' section was not found.");
		}

		takeScreenshot(page, evidenceDir.resolve("04-administrar-negocios-view.png"), true);
		return informacionGeneral && detallesCuenta && tusNegocios && seccionLegal;
	}

	private boolean validateInformacionGeneral(final Page page, final List<String> failures) {
		String pageText = page.locator("body").innerText();
		boolean userEmailVisible = EMAIL_PATTERN.matcher(pageText).find();
		boolean businessPlanVisible = isVisibleByText(page, "BUSINESS PLAN");
		boolean cambiarPlanVisible = isVisibleByText(page, "Cambiar Plan");
		boolean userNameVisible = sectionTextLength(page, "Información General") > 40;

		if (!userNameVisible) {
			failures.add("User name is not clearly visible in 'Información General'.");
		}
		if (!userEmailVisible) {
			failures.add("User email is not visible in 'Información General'.");
		}
		if (!businessPlanVisible) {
			failures.add("Text 'BUSINESS PLAN' is not visible.");
		}
		if (!cambiarPlanVisible) {
			failures.add("Button 'Cambiar Plan' is not visible.");
		}

		return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean validateDetallesCuenta(final Page page, final List<String> failures) {
		boolean cuentaCreada = isVisibleByText(page, "Cuenta creada");
		boolean estadoActivo = isVisibleByText(page, "Estado activo");
		boolean idiomaSeleccionado = isVisibleByText(page, "Idioma seleccionado");

		if (!cuentaCreada) {
			failures.add("'Cuenta creada' is not visible.");
		}
		if (!estadoActivo) {
			failures.add("'Estado activo' is not visible.");
		}
		if (!idiomaSeleccionado) {
			failures.add("'Idioma seleccionado' is not visible.");
		}

		return cuentaCreada && estadoActivo && idiomaSeleccionado;
	}

	private boolean validateTusNegocios(final Page page, final List<String> failures) {
		boolean businessListVisible = isVisibleByText(page, "Tus Negocios");
		boolean agregarNegocioBtn = isVisibleByText(page, "Agregar Negocio");
		boolean quotaVisible = isVisibleByText(page, "Tienes 2 de 3 negocios");

		if (!businessListVisible) {
			failures.add("Business list is not visible in 'Tus Negocios'.");
		}
		if (!agregarNegocioBtn) {
			failures.add("Button 'Agregar Negocio' is not visible in 'Tus Negocios'.");
		}
		if (!quotaVisible) {
			failures.add("Text 'Tienes 2 de 3 negocios' is not visible in 'Tus Negocios'.");
		}

		return businessListVisible && agregarNegocioBtn && quotaVisible;
	}

	private LegalValidationResult validateLegalLink(final Page appPage, final BrowserContext context,
			final String[] linkTexts, final String[] headingTexts, final Path screenshotPath, final List<String> failures) {
		Page targetPage = appPage;
		boolean newTabOpened = false;

		try {
			targetPage = context.waitForPage(
					() -> clickByVisibleText(appPage, linkTexts),
					new BrowserContext.WaitForPageOptions().setTimeout(7000));
			newTabOpened = true;
		} catch (PlaywrightException ignored) {
			if (!clickByVisibleText(appPage, linkTexts)) {
				failures.add("Could not open legal link: " + Arrays.toString(linkTexts));
				return new LegalValidationResult(false, appPage.url());
			}
		}

		waitForUi(targetPage);
		boolean headingVisible = isVisibleByText(targetPage, headingTexts);
		boolean legalContentVisible = hasLegalContent(targetPage);

		if (!headingVisible) {
			failures.add("Legal heading not found for: " + Arrays.toString(headingTexts));
		}
		if (!legalContentVisible) {
			failures.add("Legal content appears empty for: " + Arrays.toString(linkTexts));
		}

		takeScreenshot(targetPage, screenshotPath, true);
		String finalUrl = targetPage.url();

		if (newTabOpened) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				appPage.goBack(new Page.GoBackOptions().setTimeout(15000));
				waitForUi(appPage);
			} catch (PlaywrightException ignored) {
				// No-op: if app does not support browser history, next step still retries from current tab.
			}
		}

		return new LegalValidationResult(headingVisible && legalContentVisible, finalUrl);
	}

	private BrowserSession createSession(final Playwright playwright, final String cdpUrl, final boolean headless) {
		if (hasText(cdpUrl)) {
			Browser browser = playwright.chromium().connectOverCDP(cdpUrl);
			boolean createdContext = browser.contexts().isEmpty();
			BrowserContext context = createdContext ? browser.newContext() : browser.contexts().get(0);
			Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
			page.bringToFront();
			waitForUi(page);
			return new BrowserSession(browser, context, page, true, createdContext);
		}

		Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		BrowserContext context = browser.newContext();
		Page page = context.newPage();
		return new BrowserSession(browser, context, page, true, true);
	}

	private boolean hasVisibleElement(final Locator... locators) {
		for (Locator locator : locators) {
			try {
				if (locator.first().isVisible()) {
					return true;
				}
			} catch (PlaywrightException ignored) {
				// Try next locator.
			}
		}
		return false;
	}

	private boolean clickByVisibleText(final Page page, final String... texts) {
		for (String text : texts) {
			if (!hasText(text)) {
				continue;
			}
			if (clickLocatorIfPossible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)).first(), page)) {
				return true;
			}
			if (clickLocatorIfPossible(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)).first(), page)) {
				return true;
			}
			if (clickLocatorIfPossible(page.getByText(text).first(), page)) {
				return true;
			}
		}
		return false;
	}

	private boolean clickLocatorIfPossible(final Locator locator, final Page page) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(2500));
			locator.click(new Locator.ClickOptions().setTimeout(5000));
			waitForUi(page);
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private boolean isVisibleByText(final Page page, final String... texts) {
		for (String text : texts) {
			try {
				Locator locator = page.getByText(text).first();
				if (locator.isVisible()) {
					return true;
				}
			} catch (PlaywrightException ignored) {
				// Continue with alternatives.
			}
		}
		return false;
	}

	private int sectionTextLength(final Page page, final String sectionTitle) {
		try {
			Locator section = page.locator("section,div").filter(new Locator.FilterOptions().setHasText(sectionTitle)).first();
			if (section.isVisible()) {
				return section.innerText().trim().length();
			}
		} catch (PlaywrightException ignored) {
			// Ignore and report zero length.
		}
		return 0;
	}

	private boolean hasLegalContent(final Page page) {
		try {
			String body = page.locator("body").innerText();
			return body != null && body.trim().length() > 120;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void takeScreenshot(final Page page, final Path path, final boolean fullPage) {
		try {
			Files.createDirectories(path.getParent());
			page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
		} catch (IOException | PlaywrightException ignored) {
			// Screenshot evidence is best-effort; validations still drive pass/fail.
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (PlaywrightException ignored) {
			// Single-page apps may not emit all load states.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
			// Continuous background network calls can keep this event from firing.
		}
		page.waitForTimeout(600);
	}

	private void writeFinalReport(final Map<String, Boolean> report, final Map<String, String> legalUrls,
			final List<String> failures, final Path reportPath) throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Full Workflow Report");
		lines.add("Generated at: " + DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
		lines.add("");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			lines.add(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		lines.add("");
		for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
			lines.add(entry.getKey() + ": " + entry.getValue());
		}
		if (!failures.isEmpty()) {
			lines.add("");
			lines.add("Failures:");
			for (String failure : failures) {
				lines.add("- " + failure);
			}
		}
		Files.write(reportPath, lines, StandardCharsets.UTF_8);
	}

	private String env(final String key) {
		return System.getenv(key);
	}

	private String envOrDefault(final String key, final String defaultValue) {
		String value = System.getenv(key);
		return hasText(value) ? value : defaultValue;
	}

	private boolean hasText(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	private static final class BrowserSession {
		private final Browser browser;
		private final BrowserContext context;
		private final Page page;
		private final boolean closeBrowser;
		private final boolean closeContext;

		private BrowserSession(final Browser browser, final BrowserContext context, final Page page,
				final boolean closeBrowser, final boolean closeContext) {
			this.browser = browser;
			this.context = context;
			this.page = page;
			this.closeBrowser = closeBrowser;
			this.closeContext = closeContext;
		}
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
