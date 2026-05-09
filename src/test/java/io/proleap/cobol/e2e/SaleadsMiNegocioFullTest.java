package io.proleap.cobol.e2e;

import java.io.IOException;
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

import org.junit.Assert;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;

public class SaleadsMiNegocioFullTest {

	private static final long UI_TIMEOUT_MS = 30000;
	private static final long SHORT_WAIT_MS = 1500;
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final LinkedHashMap<String, Boolean> report = initReport();
		final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

		final String startUrl = System.getenv("SALEADS_START_URL");
		final String screenshotDirEnv = envOrDefault("SALEADS_SCREENSHOT_DIR", "target/saleads-screenshots");
		final String googleAccount = envOrDefault("SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);
		final boolean headless = Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "false"));
		final Path screenshotDir = Paths.get(screenshotDirEnv);

		Files.createDirectories(screenshotDir);

		if (isBlank(startUrl)) {
			Assert.fail(
					"SALEADS_START_URL is required. This test is environment-agnostic and does not hardcode any SaleADS domain.");
		}

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page page = context.newPage();

			page.navigate(startUrl);
			waitForUi(page);

			report.put("Login", runStep("Login", () -> validateLoginStep(page, context, googleAccount, screenshotDir)));
			report.put("Mi Negocio menu", runStep("Mi Negocio menu", () -> validateMiNegocioMenu(page, screenshotDir)));
			report.put("Agregar Negocio modal",
					runStep("Agregar Negocio modal", () -> validateAgregarNegocioModal(page, screenshotDir)));
			report.put("Administrar Negocios view",
					runStep("Administrar Negocios view", () -> validateAdministrarNegociosView(page, screenshotDir)));
			report.put("Informaci\u00f3n General",
					runStep("Informaci\u00f3n General", () -> validateInformacionGeneral(page)));
			report.put("Detalles de la Cuenta", runStep("Detalles de la Cuenta", () -> validateDetallesCuenta(page)));
			report.put("Tus Negocios", runStep("Tus Negocios", () -> validateTusNegocios(page)));

			final LegalValidation terminos = runLegalStep(context, page, "Terminos y Condiciones",
					Arrays.asList("T\u00e9rminos y Condiciones", "Terminos y Condiciones"), screenshotDir,
					"08_terminos_y_condiciones");
			report.put("T\u00e9rminos y Condiciones", terminos.passed);
			legalUrls.put("T\u00e9rminos y Condiciones URL", terminos.finalUrl);

			final LegalValidation privacidad = runLegalStep(context, page, "Politica de Privacidad",
					Arrays.asList("Pol\u00edtica de Privacidad", "Politica de Privacidad"), screenshotDir,
					"09_politica_de_privacidad");
			report.put("Pol\u00edtica de Privacidad", privacidad.passed);
			legalUrls.put("Pol\u00edtica de Privacidad URL", privacidad.finalUrl);

			context.close();
			browser.close();
		}

		final String summary = buildSummary(report, legalUrls);
		System.out.println(summary);
		Assert.assertTrue(summary, allPassed(report));
	}

	private LinkedHashMap<String, Boolean> initReport() {
		final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Informaci\u00f3n General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("T\u00e9rminos y Condiciones", false);
		report.put("Pol\u00edtica de Privacidad", false);
		return report;
	}

	private boolean validateLoginStep(final Page page, final BrowserContext context, final String googleAccount,
			final Path screenshotDir) {
		if (!isSidebarVisible(page)) {
			final boolean clickedLogin = clickFirstVisibleText(page, Arrays.asList("Sign in with Google",
					"Iniciar sesi\u00f3n con Google", "Continuar con Google", "Acceder con Google"), true);
			if (clickedLogin) {
				handleGoogleAccountSelection(context, googleAccount);
			}
		}

		final boolean sidebarVisible = waitForSidebar(page, context, UI_TIMEOUT_MS);
		final boolean mainVisible = isAnySelectorVisible(page, Arrays.asList("main", "[role='main']", "section"));
		if (sidebarVisible) {
			captureScreenshot(page, screenshotDir, "01_dashboard_loaded", false);
		}

		return sidebarVisible && mainVisible;
	}

	private boolean validateMiNegocioMenu(final Page page, final Path screenshotDir) {
		clickFirstVisibleText(page, Arrays.asList("Negocio"), true);
		clickFirstVisibleText(page, Arrays.asList("Mi Negocio"), true);

		final boolean agregarVisible = waitForAnyTextVisible(page, Arrays.asList("Agregar Negocio"), UI_TIMEOUT_MS);
		final boolean administrarVisible = waitForAnyTextVisible(page, Arrays.asList("Administrar Negocios"),
				UI_TIMEOUT_MS);
		final boolean expanded = agregarVisible && administrarVisible;

		if (expanded) {
			captureScreenshot(page, screenshotDir, "02_mi_negocio_menu_expanded", false);
		}

		return expanded;
	}

	private boolean validateAgregarNegocioModal(final Page page, final Path screenshotDir) {
		final boolean clicked = clickFirstVisibleText(page, Arrays.asList("Agregar Negocio"), true);
		if (!clicked) {
			return false;
		}

		final boolean modalTitleVisible = waitForAnyTextVisible(page,
				Arrays.asList("Crear Nuevo Negocio", "Crear nuevo negocio"), UI_TIMEOUT_MS);
		final boolean nombreTextVisible = isAnyTextVisible(page, Arrays.asList("Nombre del Negocio"));
		final boolean quotaTextVisible = isAnyTextVisible(page, Arrays.asList("Tienes 2 de 3 negocios"));
		final boolean cancelVisible = isAnyTextVisible(page, Arrays.asList("Cancelar"));
		final boolean createVisible = isAnyTextVisible(page, Arrays.asList("Crear Negocio"));
		final boolean inputExists = isAnySelectorVisible(page,
				Arrays.asList("input[placeholder*='Negocio' i]", "input[name*='negocio' i]", "input[type='text']"));

		if (modalTitleVisible) {
			captureScreenshot(page, screenshotDir, "03_agregar_negocio_modal", false);
		}

		// Optional action requested by workflow.
		fillFirstVisibleInput(page, "Negocio Prueba Automatizacion");
		clickFirstVisibleText(page, Arrays.asList("Cancelar"), true);

		return modalTitleVisible && nombreTextVisible && quotaTextVisible && cancelVisible && createVisible && inputExists;
	}

	private boolean validateAdministrarNegociosView(final Page page, final Path screenshotDir) {
		if (!isAnyTextVisible(page, Arrays.asList("Administrar Negocios"))) {
			clickFirstVisibleText(page, Arrays.asList("Mi Negocio"), true);
		}

		final boolean clicked = clickFirstVisibleText(page, Arrays.asList("Administrar Negocios"), true);
		if (!clicked) {
			return false;
		}

		final boolean informacionGeneral = waitForAnyTextVisible(page, Arrays.asList("Informaci\u00f3n General",
				"Informacion General"), UI_TIMEOUT_MS);
		final boolean detallesCuenta = waitForAnyTextVisible(page, Arrays.asList("Detalles de la Cuenta"), UI_TIMEOUT_MS);
		final boolean tusNegocios = waitForAnyTextVisible(page, Arrays.asList("Tus Negocios"), UI_TIMEOUT_MS);
		final boolean legalSection = waitForAnyTextVisible(page,
				Arrays.asList("Secci\u00f3n Legal", "Seccion Legal"), UI_TIMEOUT_MS);

		final boolean passed = informacionGeneral && detallesCuenta && tusNegocios && legalSection;
		if (passed) {
			captureScreenshot(page, screenshotDir, "04_administrar_negocios_view", true);
		}

		return passed;
	}

	private boolean validateInformacionGeneral(final Page page) {
		final boolean userNameVisible = isAnyTextVisible(page,
				Arrays.asList("Nombre", "Usuario", "User", "Perfil", "Cuenta"));
		final boolean userEmailVisible = isAnySelectorVisible(page,
				Arrays.asList("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/"));
		final boolean businessPlanVisible = isAnyTextVisible(page, Arrays.asList("BUSINESS PLAN"));
		final boolean cambiarPlanVisible = isAnyTextVisible(page, Arrays.asList("Cambiar Plan"));
		return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean validateDetallesCuenta(final Page page) {
		final boolean cuentaCreada = isAnyTextVisible(page, Arrays.asList("Cuenta creada"));
		final boolean estadoActivo = isAnyTextVisible(page, Arrays.asList("Estado activo"));
		final boolean idiomaSeleccionado = isAnyTextVisible(page, Arrays.asList("Idioma seleccionado"));
		return cuentaCreada && estadoActivo && idiomaSeleccionado;
	}

	private boolean validateTusNegocios(final Page page) {
		final boolean tusNegociosVisible = isAnyTextVisible(page, Arrays.asList("Tus Negocios"));
		final boolean agregarNegocioButton = isAnyTextVisible(page, Arrays.asList("Agregar Negocio"));
		final boolean quotaTextVisible = isAnyTextVisible(page, Arrays.asList("Tienes 2 de 3 negocios"));
		final boolean businessListVisible = isAnySelectorVisible(page,
				Arrays.asList("[class*='business' i]", "[data-testid*='business' i]", "ul", "table"));
		return tusNegociosVisible && agregarNegocioButton && quotaTextVisible && businessListVisible;
	}

	private LegalValidation runLegalStep(final BrowserContext context, final Page appPage, final String reportName,
			final List<String> linkTexts, final Path screenshotDir, final String screenshotPrefix) {
		try {
			final String appUrlBefore = appPage.url();
			Page legalPage = null;
			boolean popupOpened = false;

			try {
				legalPage = context.waitForPage(() -> {
					clickFirstVisibleText(appPage, linkTexts, false);
				},
						new BrowserContext.WaitForPageOptions().setTimeout(6000));
				popupOpened = true;
			} catch (final PlaywrightException popupTimeout) {
				if (!clickFirstVisibleText(appPage, linkTexts, true)) {
					return new LegalValidation(false, "link-not-found");
				}
				legalPage = appPage;
			}

			waitForUi(legalPage);
			final boolean headingVisible = isAnyTextVisible(legalPage, linkTexts);
			final boolean legalTextVisible = isAnySelectorVisible(legalPage,
					Arrays.asList("article", "main", "section", "p", "[class*='legal' i]"));
			captureScreenshot(legalPage, screenshotDir, screenshotPrefix, true);
			final String finalUrl = legalPage.url();

			if (popupOpened) {
				legalPage.close();
				appPage.bringToFront();
			} else if (!Objects.equals(appPage.url(), appUrlBefore)) {
				appPage.navigate(appUrlBefore);
				waitForUi(appPage);
			}

			return new LegalValidation(headingVisible && legalTextVisible, finalUrl);
		} catch (final Exception error) {
			System.out.println("Step '" + reportName + "' failed: " + error.getMessage());
			return new LegalValidation(false, "error");
		}
	}

	private boolean runStep(final String stepName, final CheckedBooleanSupplier action) {
		try {
			return action.get();
		} catch (final Exception error) {
			System.out.println("Step '" + stepName + "' failed: " + error.getMessage());
			return false;
		}
	}

	private void handleGoogleAccountSelection(final BrowserContext context, final String googleAccount) {
		final long started = System.currentTimeMillis();
		while (System.currentTimeMillis() - started < UI_TIMEOUT_MS) {
			for (final Page candidate : context.pages()) {
				final boolean looksLikeGooglePage = candidate.url().contains("accounts.google.com")
						|| isAnyTextVisible(candidate, Arrays.asList("Choose an account", "Elige una cuenta"));
				if (looksLikeGooglePage) {
					final boolean accountClicked = clickFirstVisibleText(candidate, Arrays.asList(googleAccount), true);
					if (accountClicked) {
						return;
					}
				}
			}
			context.pages().get(0).waitForTimeout(400);
		}
	}

	private boolean waitForSidebar(final Page page, final BrowserContext context, final long timeoutMs) {
		final long started = System.currentTimeMillis();
		while (System.currentTimeMillis() - started < timeoutMs) {
			for (final Page candidate : context.pages()) {
				if (isSidebarVisible(candidate)) {
					candidate.bringToFront();
					waitForUi(candidate);
					return true;
				}
			}
			page.waitForTimeout(350);
		}
		return false;
	}

	private boolean isSidebarVisible(final Page page) {
		return isAnySelectorVisible(page, Arrays.asList("aside", "[class*='sidebar' i]", "nav"));
	}

	private boolean clickFirstVisibleText(final Page page, final List<String> texts, final boolean waitAfterClick) {
		for (final String text : texts) {
			try {
				final Locator locator = page.locator("text=" + text).first();
				if (locator.count() > 0 && locator.isVisible()) {
					locator.click(new Locator.ClickOptions().setTimeout(UI_TIMEOUT_MS));
					if (waitAfterClick) {
						waitForUi(page);
					}
					return true;
				}
			} catch (final PlaywrightException ignored) {
				// Try next matching text.
			}
		}
		return false;
	}

	private boolean waitForAnyTextVisible(final Page page, final List<String> texts, final long timeoutMs) {
		final long started = System.currentTimeMillis();
		while (System.currentTimeMillis() - started < timeoutMs) {
			if (isAnyTextVisible(page, texts)) {
				return true;
			}
			page.waitForTimeout(250);
		}
		return false;
	}

	private boolean isAnyTextVisible(final Page page, final List<String> texts) {
		for (final String text : texts) {
			try {
				final Locator locator = page.locator("text=" + text).first();
				if (locator.count() > 0 && locator.isVisible()) {
					return true;
				}
			} catch (final PlaywrightException ignored) {
				// Ignore and continue with next candidate text.
			}
		}
		return false;
	}

	private boolean isAnySelectorVisible(final Page page, final List<String> selectors) {
		for (final String selector : selectors) {
			try {
				final Locator locator = page.locator(selector).first();
				if (locator.count() > 0 && locator.isVisible()) {
					return true;
				}
			} catch (final PlaywrightException ignored) {
				// Ignore and continue with next selector.
			}
		}
		return false;
	}

	private void fillFirstVisibleInput(final Page page, final String value) {
		final List<String> selectors = Arrays.asList("input[placeholder*='Negocio' i]", "input[name*='negocio' i]",
				"input[type='text']", "textarea");
		for (final String selector : selectors) {
			try {
				final Locator locator = page.locator(selector).first();
				if (locator.count() > 0 && locator.isVisible()) {
					locator.fill(value, new Locator.FillOptions().setTimeout(SHORT_WAIT_MS));
					return;
				}
			} catch (final PlaywrightException ignored) {
				// Try next selector.
			}
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(UI_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_WAIT_MS));
		} catch (final PlaywrightException ignored) {
		}
		page.waitForTimeout(250);
	}

	private void captureScreenshot(final Page page, final Path screenshotDir, final String baseName, final boolean fullPage) {
		final String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
		final Path outputPath = screenshotDir.resolve(baseName + "_" + timestamp + ".png");
		try {
			page.screenshot(new Page.ScreenshotOptions().setPath(outputPath).setFullPage(fullPage));
			System.out.println("Screenshot: " + outputPath.toAbsolutePath());
		} catch (final PlaywrightException screenshotError) {
			System.out.println("Could not capture screenshot " + outputPath + ": " + screenshotError.getMessage());
		}
	}

	private String buildSummary(final Map<String, Boolean> report, final Map<String, String> legalUrls) {
		final List<String> lines = new ArrayList<>();
		lines.add("===== SaleADS Mi Negocio Final Report =====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			lines.add(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		if (!legalUrls.isEmpty()) {
			lines.add("----- Captured URLs -----");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				lines.add(entry.getKey() + ": " + entry.getValue());
			}
		}
		return String.join(System.lineSeparator(), lines);
	}

	private boolean allPassed(final Map<String, Boolean> report) {
		for (final boolean stepResult : report.values()) {
			if (!stepResult) {
				return false;
			}
		}
		return true;
	}

	private String envOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return isBlank(value) ? defaultValue : value;
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface CheckedBooleanSupplier {
		boolean get() throws Exception;
	}

	private static class LegalValidation {
		private final boolean passed;
		private final String finalUrl;

		private LegalValidation(final boolean passed, final String finalUrl) {
			this.passed = passed;
			this.finalUrl = finalUrl;
		}
	}
}
