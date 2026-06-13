package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

public class SaleadsMiNegocioFullTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Política de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
			.withZone(ZoneOffset.UTC);

	private static final class StepResult {
		private String status = "FAIL";
		private String details = "Not executed";
		private String screenshotPath = "";
		private String finalUrl = "";
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final Map<String, StepResult> results = createInitialResults();
		final Path outputDir = createOutputDirectory();

		Throwable failure = null;
		boolean preconditionsMet = true;
		final String loginUrl = resolveLoginUrl();

		try {
			if (loginUrl == null || loginUrl.isBlank()) {
				preconditionsMet = false;
				markFail(results, STEP_LOGIN,
						"Missing login URL. Set SALEADS_LOGIN_URL or -Dsaleads.login.url to the current environment login page.");
				markPrerequisiteFailures(results, STEP_MI_NEGOCIO_MENU, STEP_AGREGAR_MODAL, STEP_ADMIN_VIEW,
						STEP_INFO_GENERAL, STEP_DETALLES_CUENTA, STEP_TUS_NEGOCIOS, STEP_TERMINOS, STEP_PRIVACIDAD);
			} else {
				try (Playwright playwright = Playwright.create()) {
					final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
							.setHeadless(resolveHeadless()).setSlowMo(resolveSlowMoMillis()));
					final BrowserContext context = browser
							.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
					final Page appPage = context.newPage();

					appPage.navigate(loginUrl);
					waitForUi(appPage);
					captureScreenshot(appPage, outputDir, "step0_login_page.png", true);

					final boolean loginPassed = runLoginStep(appPage, outputDir, results);
					if (!loginPassed) {
						markPrerequisiteFailures(results, STEP_MI_NEGOCIO_MENU, STEP_AGREGAR_MODAL, STEP_ADMIN_VIEW,
								STEP_INFO_GENERAL, STEP_DETALLES_CUENTA, STEP_TUS_NEGOCIOS, STEP_TERMINOS, STEP_PRIVACIDAD);
					} else {
						final boolean miNegocioMenuPassed = runMiNegocioMenuStep(appPage, outputDir, results);
						if (!miNegocioMenuPassed) {
							markPrerequisiteFailures(results, STEP_AGREGAR_MODAL, STEP_ADMIN_VIEW, STEP_INFO_GENERAL,
									STEP_DETALLES_CUENTA, STEP_TUS_NEGOCIOS, STEP_TERMINOS, STEP_PRIVACIDAD);
						} else {
							final boolean agregarModalPassed = runAgregarNegocioModalStep(appPage, outputDir, results);
							if (!agregarModalPassed) {
								markPrerequisiteFailures(results, STEP_ADMIN_VIEW, STEP_INFO_GENERAL, STEP_DETALLES_CUENTA,
										STEP_TUS_NEGOCIOS, STEP_TERMINOS, STEP_PRIVACIDAD);
							} else {
								final boolean adminViewPassed = runAdministrarNegociosStep(appPage, outputDir, results);
								if (!adminViewPassed) {
									markPrerequisiteFailures(results, STEP_INFO_GENERAL, STEP_DETALLES_CUENTA,
											STEP_TUS_NEGOCIOS, STEP_TERMINOS, STEP_PRIVACIDAD);
								} else {
									runInformacionGeneralStep(appPage, results);
									runDetallesCuentaStep(appPage, results);
									runTusNegociosStep(appPage, results);
									runLegalPageStep(appPage, outputDir, results, STEP_TERMINOS, "Términos y Condiciones");
									runLegalPageStep(appPage, outputDir, results, STEP_PRIVACIDAD, "Política de Privacidad");
								}
							}
						}
					}
				} catch (final Throwable t) {
					failure = t;
				}
			}
		} finally {
			writeReports(results, outputDir);
		}

		if (failure != null) {
			throw new RuntimeException("SaleADS Mi Negocio workflow test failed before completion.", failure);
		}

		Assume.assumeTrue(
				"Skipping SaleADS E2E workflow because login URL is missing. Provide SALEADS_LOGIN_URL or -Dsaleads.login.url.",
				preconditionsMet);

		assertTrue("One or more validation steps failed. Report generated at: " + outputDir,
				allStepsPassed(results));
	}

	private boolean runLoginStep(final Page page, final Path outputDir, final Map<String, StepResult> results) {
		final StepResult loginResult = results.get(STEP_LOGIN);

		try {
			if (!clickVisibleText(page, "Sign in with Google", "Iniciar sesión con Google", "Inicia sesión con Google",
					"Sign in", "Iniciar sesión", "Inicia sesión")) {
				markFail(results, STEP_LOGIN, "Could not find a login button or 'Sign in with Google' action.");
				return false;
			}

			waitForUi(page);
			clickVisibleText(page, "GOOGLE", "Google");
			waitForUi(page);

			if (clickVisibleText(page, GOOGLE_ACCOUNT_EMAIL)) {
				waitForUi(page);
			} else if (fillGoogleEmailIfPrompted(page)) {
				waitForUi(page);
			}

			final boolean sidebarVisible = isSidebarVisible(page);
			final boolean appVisible = isAnyTextVisible(page, "Mi Negocio", "Negocio", "Dashboard", "Panel");

			if (sidebarVisible && appVisible) {
				loginResult.status = "PASS";
				loginResult.details = "Main application interface and left sidebar are visible.";
				loginResult.screenshotPath = captureScreenshot(page, outputDir, "step1_dashboard_loaded.png", true);
				loginResult.finalUrl = page.url();
				return true;
			}

			loginResult.screenshotPath = captureScreenshot(page, outputDir, "step1_login_blocked.png", true);
			loginResult.finalUrl = page.url();
			loginResult.details = "Login did not reach a page with visible application interface and sidebar.";
			return false;
		} catch (final Exception e) {
			markFail(results, STEP_LOGIN, "Login step failed with exception: " + e.getMessage());
			loginResult.screenshotPath = safeCapture(page, outputDir, "step1_login_exception.png", true);
			loginResult.finalUrl = safeUrl(page);
			return false;
		}
	}

	private boolean runMiNegocioMenuStep(final Page page, final Path outputDir, final Map<String, StepResult> results) {
		try {
			clickVisibleText(page, "Negocio");
			final boolean miNegocioClicked = clickVisibleText(page, "Mi Negocio");
			waitForUi(page);

			final boolean agregarVisible = isAnyTextVisible(page, "Agregar Negocio");
			final boolean administrarVisible = isAnyTextVisible(page, "Administrar Negocios");

			if (miNegocioClicked && agregarVisible && administrarVisible) {
				markPass(results, STEP_MI_NEGOCIO_MENU,
						"Mi Negocio menu expanded with 'Agregar Negocio' and 'Administrar Negocios'.");
				final StepResult result = results.get(STEP_MI_NEGOCIO_MENU);
				result.screenshotPath = captureScreenshot(page, outputDir, "step2_mi_negocio_menu_expanded.png", true);
				result.finalUrl = page.url();
				return true;
			}

			final StepResult result = results.get(STEP_MI_NEGOCIO_MENU);
			result.screenshotPath = safeCapture(page, outputDir, "step2_menu_not_expanded.png", true);
			result.finalUrl = safeUrl(page);
			markFail(results, STEP_MI_NEGOCIO_MENU,
					"Failed to confirm expanded Mi Negocio menu with both required options.");
			return false;
		} catch (final Exception e) {
			final StepResult result = results.get(STEP_MI_NEGOCIO_MENU);
			result.screenshotPath = safeCapture(page, outputDir, "step2_menu_exception.png", true);
			result.finalUrl = safeUrl(page);
			markFail(results, STEP_MI_NEGOCIO_MENU, "Mi Negocio menu step failed with exception: " + e.getMessage());
			return false;
		}
	}

	private boolean runAgregarNegocioModalStep(final Page page, final Path outputDir, final Map<String, StepResult> results) {
		try {
			if (!clickVisibleText(page, "Agregar Negocio")) {
				markFail(results, STEP_AGREGAR_MODAL, "Could not click 'Agregar Negocio'.");
				return false;
			}

			waitForUi(page);

			final boolean titleVisible = waitForAnyText(page, 7000, "Crear Nuevo Negocio");
			final boolean nombreFieldVisible = isNombreDelNegocioFieldVisible(page);
			final boolean quotaVisible = isAnyTextVisible(page, "Tienes 2 de 3 negocios");
			final boolean cancelarVisible = isAnyTextVisible(page, "Cancelar");
			final boolean crearVisible = isAnyTextVisible(page, "Crear Negocio");

			final StepResult result = results.get(STEP_AGREGAR_MODAL);
			result.screenshotPath = safeCapture(page, outputDir, "step3_agregar_negocio_modal.png", true);
			result.finalUrl = safeUrl(page);

			if (titleVisible && nombreFieldVisible && quotaVisible && cancelarVisible && crearVisible) {
				fillNombreDelNegocioIfVisible(page, "Negocio Prueba Automatización");
				clickVisibleText(page, "Cancelar");
				waitForUi(page);
				markPass(results, STEP_AGREGAR_MODAL,
						"Modal validations passed: title, field, quota text, and action buttons are visible.");
				return true;
			}

			markFail(results, STEP_AGREGAR_MODAL,
					"Modal did not expose all required elements for 'Crear Nuevo Negocio'.");
			return false;
		} catch (final Exception e) {
			final StepResult result = results.get(STEP_AGREGAR_MODAL);
			result.screenshotPath = safeCapture(page, outputDir, "step3_modal_exception.png", true);
			result.finalUrl = safeUrl(page);
			markFail(results, STEP_AGREGAR_MODAL, "Agregar Negocio modal step failed with exception: " + e.getMessage());
			return false;
		}
	}

	private boolean runAdministrarNegociosStep(final Page page, final Path outputDir, final Map<String, StepResult> results) {
		try {
			if (!isAnyTextVisible(page, "Administrar Negocios")) {
				clickVisibleText(page, "Mi Negocio");
				waitForUi(page);
			}

			if (!clickVisibleText(page, "Administrar Negocios")) {
				markFail(results, STEP_ADMIN_VIEW, "Could not click 'Administrar Negocios'.");
				return false;
			}

			waitForUi(page);

			final boolean infoGeneralVisible = waitForAnyText(page, 10000, "Información General");
			final boolean detallesVisible = isAnyTextVisible(page, "Detalles de la Cuenta");
			final boolean tusNegociosVisible = isAnyTextVisible(page, "Tus Negocios");
			final boolean legalVisible = isAnyTextVisible(page, "Sección Legal");

			final StepResult result = results.get(STEP_ADMIN_VIEW);
			result.screenshotPath = safeCapture(page, outputDir, "step4_administrar_negocios_view.png", true);
			result.finalUrl = safeUrl(page);

			if (infoGeneralVisible && detallesVisible && tusNegociosVisible && legalVisible) {
				markPass(results, STEP_ADMIN_VIEW,
						"Administrar Negocios page loaded with all required account sections.");
				return true;
			}

			markFail(results, STEP_ADMIN_VIEW,
					"Missing one or more required sections in Administrar Negocios view.");
			return false;
		} catch (final Exception e) {
			final StepResult result = results.get(STEP_ADMIN_VIEW);
			result.screenshotPath = safeCapture(page, outputDir, "step4_admin_view_exception.png", true);
			result.finalUrl = safeUrl(page);
			markFail(results, STEP_ADMIN_VIEW,
					"Administrar Negocios navigation failed with exception: " + e.getMessage());
			return false;
		}
	}

	private void runInformacionGeneralStep(final Page page, final Map<String, StepResult> results) {
		final boolean emailVisible = isEmailVisible(page);
		final boolean businessPlanVisible = isAnyTextVisible(page, "BUSINESS PLAN");
		final boolean cambiarPlanVisible = isAnyTextVisible(page, "Cambiar Plan");
		final boolean userNameVisible = isAnyTextVisible(page, "Nombre", "Perfil", "Cuenta")
				|| isAnyTextVisible(page, "Juan", "Lucas", "Barbier", "Garzon");

		if (emailVisible && businessPlanVisible && cambiarPlanVisible && userNameVisible) {
			markPass(results, STEP_INFO_GENERAL,
					"Información General section contains user name, user email, plan text, and plan action button.");
		} else {
			markFail(results, STEP_INFO_GENERAL,
					"Información General validation failed. Required labels or account details are missing.");
		}

		results.get(STEP_INFO_GENERAL).finalUrl = safeUrl(page);
	}

	private void runDetallesCuentaStep(final Page page, final Map<String, StepResult> results) {
		final boolean cuentaCreadaVisible = isAnyTextVisible(page, "Cuenta creada");
		final boolean estadoActivoVisible = isAnyTextVisible(page, "Estado activo");
		final boolean idiomaVisible = isAnyTextVisible(page, "Idioma seleccionado");

		if (cuentaCreadaVisible && estadoActivoVisible && idiomaVisible) {
			markPass(results, STEP_DETALLES_CUENTA,
					"Detalles de la Cuenta section shows account creation date, active status, and selected language.");
		} else {
			markFail(results, STEP_DETALLES_CUENTA,
					"Detalles de la Cuenta validation failed. One or more required labels are missing.");
		}

		results.get(STEP_DETALLES_CUENTA).finalUrl = safeUrl(page);
	}

	private void runTusNegociosStep(final Page page, final Map<String, StepResult> results) {
		final boolean headingVisible = isAnyTextVisible(page, "Tus Negocios");
		final boolean addBusinessVisible = isAnyTextVisible(page, "Agregar Negocio");
		final boolean quotaVisible = isAnyTextVisible(page, "Tienes 2 de 3 negocios");

		if (headingVisible && addBusinessVisible && quotaVisible) {
			markPass(results, STEP_TUS_NEGOCIOS,
					"Tus Negocios section shows business list area, add button, and usage counter.");
		} else {
			markFail(results, STEP_TUS_NEGOCIOS,
					"Tus Negocios validation failed. Missing business list heading, action, or usage text.");
		}

		results.get(STEP_TUS_NEGOCIOS).finalUrl = safeUrl(page);
	}

	private void runLegalPageStep(final Page appPage, final Path outputDir, final Map<String, StepResult> results,
			final String reportField, final String linkText) {
		final StepResult result = results.get(reportField);
		final String appUrlBeforeClick = safeUrl(appPage);
		Page legalPage = null;
		boolean openedInPopup = false;

		try {
			legalPage = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(6000), () -> {
				clickVisibleText(appPage, linkText);
			});
			openedInPopup = legalPage != null;
		} catch (final PlaywrightException ignored) {
			clickVisibleText(appPage, linkText);
			legalPage = appPage;
		}

		try {
			waitForUi(legalPage);
			final boolean headingVisible = waitForAnyText(legalPage, 10000, linkText);
			final boolean hasLegalContent = hasLegalBodyContent(legalPage);

			result.screenshotPath = safeCapture(legalPage, outputDir, reportFieldToFileName(reportField), true);
			result.finalUrl = safeUrl(legalPage);

			if (headingVisible && hasLegalContent) {
				markPass(results, reportField, reportField + " page loaded with heading and legal content. URL: " + result.finalUrl);
			} else {
				markFail(results, reportField,
						reportField + " page validation failed (missing heading or legal content). URL: " + result.finalUrl);
			}
		} catch (final Exception e) {
			result.screenshotPath = safeCapture(legalPage, outputDir, reportFieldToFileName(reportField), true);
			result.finalUrl = safeUrl(legalPage);
			markFail(results, reportField, "Legal page step failed with exception: " + e.getMessage());
		} finally {
			if (openedInPopup && legalPage != null) {
				legalPage.close();
				appPage.bringToFront();
				waitForUi(appPage);
			} else if (!safeUrl(appPage).equals(appUrlBeforeClick)) {
				appPage.goBack();
				waitForUi(appPage);
			}
		}
	}

	private static Map<String, StepResult> createInitialResults() {
		final Map<String, StepResult> results = new LinkedHashMap<>();
		results.put(STEP_LOGIN, new StepResult());
		results.put(STEP_MI_NEGOCIO_MENU, new StepResult());
		results.put(STEP_AGREGAR_MODAL, new StepResult());
		results.put(STEP_ADMIN_VIEW, new StepResult());
		results.put(STEP_INFO_GENERAL, new StepResult());
		results.put(STEP_DETALLES_CUENTA, new StepResult());
		results.put(STEP_TUS_NEGOCIOS, new StepResult());
		results.put(STEP_TERMINOS, new StepResult());
		results.put(STEP_PRIVACIDAD, new StepResult());
		return results;
	}

	private static void markPass(final Map<String, StepResult> results, final String step, final String details) {
		final StepResult result = results.get(step);
		result.status = "PASS";
		result.details = details;
	}

	private static void markFail(final Map<String, StepResult> results, final String step, final String details) {
		final StepResult result = results.get(step);
		result.status = "FAIL";
		result.details = details;
	}

	private static void markPrerequisiteFailures(final Map<String, StepResult> results, final String... steps) {
		for (final String step : steps) {
			final StepResult result = results.get(step);
			if ("FAIL".equals(result.status) && "Not executed".equals(result.details)) {
				result.details = "Prerequisite failed in a previous step.";
			}
		}
	}

	private static boolean allStepsPassed(final Map<String, StepResult> results) {
		for (final StepResult result : results.values()) {
			if (!"PASS".equals(result.status)) {
				return false;
			}
		}
		return true;
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (final PlaywrightException ignored) {
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (final PlaywrightException ignored) {
		}

		page.waitForTimeout(1000);
	}

	private static boolean clickVisibleText(final Page page, final String... texts) {
		for (final String text : texts) {
			final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
			try {
				if (exact.count() > 0 && exact.isVisible()) {
					exact.click();
					waitForUi(page);
					return true;
				}
			} catch (final PlaywrightException ignored) {
			}

			final Locator partial = page.getByText(text).first();
			try {
				if (partial.count() > 0 && partial.isVisible()) {
					partial.click();
					waitForUi(page);
					return true;
				}
			} catch (final PlaywrightException ignored) {
			}
		}

		return false;
	}

	private static boolean waitForAnyText(final Page page, final long timeoutMs, final String... texts) {
		final long end = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < end) {
			if (isAnyTextVisible(page, texts)) {
				return true;
			}
			page.waitForTimeout(250);
		}
		return false;
	}

	private static boolean isAnyTextVisible(final Page page, final String... texts) {
		for (final String text : texts) {
			try {
				final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
				if (exact.count() > 0 && exact.isVisible()) {
					return true;
				}
			} catch (final PlaywrightException ignored) {
			}

			try {
				final Locator partial = page.getByText(text).first();
				if (partial.count() > 0 && partial.isVisible()) {
					return true;
				}
			} catch (final PlaywrightException ignored) {
			}
		}
		return false;
	}

	private static boolean isSidebarVisible(final Page page) {
		try {
			final Locator nav = page.getByRole(AriaRole.NAVIGATION).first();
			if (nav.count() > 0 && nav.isVisible()) {
				return true;
			}
		} catch (final PlaywrightException ignored) {
		}

		try {
			final Locator aside = page.locator("aside").first();
			return aside.count() > 0 && aside.isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static boolean fillGoogleEmailIfPrompted(final Page page) {
		try {
			final Locator emailField = page.getByLabel("Email or phone").or(page.getByLabel("Correo electrónico o teléfono"))
					.or(page.getByPlaceholder("Email or phone")).or(page.getByPlaceholder("Correo electrónico o teléfono"))
					.first();

			if (emailField.count() > 0 && emailField.isVisible()) {
				emailField.fill(GOOGLE_ACCOUNT_EMAIL);
				clickVisibleText(page, "Next", "Siguiente");
				return true;
			}
		} catch (final PlaywrightException ignored) {
		}
		return false;
	}

	private static boolean isNombreDelNegocioFieldVisible(final Page page) {
		try {
			final Locator byLabel = page.getByLabel("Nombre del Negocio").first();
			if (byLabel.count() > 0 && byLabel.isVisible()) {
				return true;
			}
		} catch (final PlaywrightException ignored) {
		}

		try {
			final Locator byPlaceholder = page.getByPlaceholder("Nombre del Negocio").first();
			return byPlaceholder.count() > 0 && byPlaceholder.isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static void fillNombreDelNegocioIfVisible(final Page page, final String value) {
		try {
			final Locator byLabel = page.getByLabel("Nombre del Negocio").first();
			if (byLabel.count() > 0 && byLabel.isVisible()) {
				byLabel.fill(value);
				return;
			}
		} catch (final PlaywrightException ignored) {
		}

		try {
			final Locator byPlaceholder = page.getByPlaceholder("Nombre del Negocio").first();
			if (byPlaceholder.count() > 0 && byPlaceholder.isVisible()) {
				byPlaceholder.fill(value);
			}
		} catch (final PlaywrightException ignored) {
		}
	}

	private static boolean isEmailVisible(final Page page) {
		try {
			final String pageText = page.locator("body").innerText();
			final Matcher matcher = EMAIL_PATTERN.matcher(pageText);
			return matcher.find();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static boolean hasLegalBodyContent(final Page page) {
		try {
			final String bodyText = page.locator("body").innerText();
			return bodyText != null && bodyText.trim().length() > 200;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static String reportFieldToFileName(final String reportField) {
		if (STEP_TERMINOS.equals(reportField)) {
			return "step8_terminos_y_condiciones.png";
		}
		if (STEP_PRIVACIDAD.equals(reportField)) {
			return "step9_politica_de_privacidad.png";
		}
		return "legal_page.png";
	}

	private static String safeCapture(final Page page, final Path outputDir, final String fileName, final boolean fullPage) {
		try {
			if (page == null) {
				return "";
			}
			return captureScreenshot(page, outputDir, fileName, fullPage);
		} catch (final Exception ignored) {
			return "";
		}
	}

	private static String captureScreenshot(final Page page, final Path outputDir, final String fileName,
			final boolean fullPage) {
		final Path screenshotPath = outputDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		return screenshotPath.toString();
	}

	private static String safeUrl(final Page page) {
		try {
			if (page == null) {
				return "";
			}
			return page.url();
		} catch (final PlaywrightException ignored) {
			return "";
		}
	}

	private static Path createOutputDirectory() throws IOException {
		final String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
		final Path outputDir = Paths.get("target", "saleads-mi-negocio", timestamp);
		Files.createDirectories(outputDir);
		return outputDir;
	}

	private static boolean resolveHeadless() {
		final String propertyValue = System.getProperty("saleads.headless");
		if (propertyValue != null && !propertyValue.isBlank()) {
			return Boolean.parseBoolean(propertyValue);
		}

		final String envValue = System.getenv("SALEADS_HEADLESS");
		if (envValue != null && !envValue.isBlank()) {
			return Boolean.parseBoolean(envValue);
		}

		return true;
	}

	private static double resolveSlowMoMillis() {
		final String propertyValue = System.getProperty("saleads.slowmo.ms");
		if (propertyValue != null && !propertyValue.isBlank()) {
			return Double.parseDouble(propertyValue);
		}

		final String envValue = System.getenv("SALEADS_SLOWMO_MS");
		if (envValue != null && !envValue.isBlank()) {
			return Double.parseDouble(envValue);
		}

		return 300D;
	}

	private static String resolveLoginUrl() {
		final String propertyValue = System.getProperty("saleads.login.url");
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}

		final String envValue = System.getenv("SALEADS_LOGIN_URL");
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return null;
	}

	private static void writeReports(final Map<String, StepResult> results, final Path outputDir) throws IOException {
		final Path jsonPath = outputDir.resolve("report.json");
		final Path markdownPath = outputDir.resolve("report.md");

		Files.writeString(jsonPath, toJson(results), StandardCharsets.UTF_8);
		Files.writeString(markdownPath, toMarkdown(results, outputDir), StandardCharsets.UTF_8);
	}

	private static String toJson(final Map<String, StepResult> results) {
		final StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"workflow\": \"saleads_mi_negocio_full_test\",\n");
		sb.append("  \"results\": {\n");

		int index = 0;
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			final String field = entry.getKey();
			final StepResult result = entry.getValue();

			sb.append("    \"").append(escapeJson(field)).append("\": {\n");
			sb.append("      \"status\": \"").append(escapeJson(result.status)).append("\",\n");
			sb.append("      \"details\": \"").append(escapeJson(result.details)).append("\",\n");
			sb.append("      \"screenshot\": \"").append(escapeJson(result.screenshotPath)).append("\",\n");
			sb.append("      \"finalUrl\": \"").append(escapeJson(result.finalUrl)).append("\"\n");
			sb.append("    }");
			if (index < results.size() - 1) {
				sb.append(",");
			}
			sb.append("\n");
			index++;
		}

		sb.append("  }\n");
		sb.append("}\n");
		return sb.toString();
	}

	private static String toMarkdown(final Map<String, StepResult> results, final Path outputDir) {
		final StringBuilder sb = new StringBuilder();
		sb.append("# SaleADS Mi Negocio Full Test Report\n\n");
		sb.append("- Report directory: `").append(outputDir).append("`\n\n");
		sb.append("| Field | Status | Details | Final URL |\n");
		sb.append("| --- | --- | --- | --- |\n");

		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			final StepResult result = entry.getValue();
			sb.append("| ").append(entry.getKey()).append(" | ").append(result.status).append(" | ")
					.append(escapeMarkdown(result.details)).append(" | ").append(escapeMarkdown(result.finalUrl))
					.append(" |\n");
		}

		sb.append("\n## Evidence\n\n");
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			final StepResult result = entry.getValue();
			if (!result.screenshotPath.isBlank()) {
				sb.append("- ").append(entry.getKey()).append(": `").append(result.screenshotPath).append("`\n");
			}
		}

		return sb.toString();
	}

	private static String escapeJson(final String input) {
		if (input == null) {
			return "";
		}
		return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	private static String escapeMarkdown(final String input) {
		if (input == null) {
			return "";
		}
		return input.replace("|", "\\|").replace("\n", "<br/>");
	}
}
