package io.github.uwol.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assert;
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
import java.util.regex.Pattern;

/**
 * End-to-end automation for the SaleADS "Mi Negocio" workflow.
 *
 * <p>Runtime configuration:
 * <ul>
 *   <li>SALEADS_LOGIN_URL (required): login page URL for the current environment.</li>
 *   <li>SALEADS_GOOGLE_ACCOUNT (optional): Google account to pick in account selector.</li>
 *   <li>SALEADS_STORAGE_STATE (optional): Playwright storage state JSON path.</li>
 *   <li>HEADLESS (optional, default true): launch browser in headless mode.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final long MEDIUM_TIMEOUT_MS = 8000;
	private static final long LONG_TIMEOUT_MS = 45000;
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Test
	public void saleadsMiNegocioWorkflow() throws IOException {
		final LinkedHashMap<String, StepResult> report = new LinkedHashMap<>();
		report.put("Login", new StepResult());
		report.put("Mi Negocio menu", new StepResult());
		report.put("Agregar Negocio modal", new StepResult());
		report.put("Administrar Negocios view", new StepResult());
		report.put("Información General", new StepResult());
		report.put("Detalles de la Cuenta", new StepResult());
		report.put("Tus Negocios", new StepResult());
		report.put("Términos y Condiciones", new StepResult());
		report.put("Política de Privacidad", new StepResult());

		final Path artifactsDir = createArtifactsDir();
		final String loginUrl = env("SALEADS_LOGIN_URL", "");
		final String googleAccountEmail = env("SALEADS_GOOGLE_ACCOUNT", "juanlucasbarbiergarzon@gmail.com");
		final boolean headless = Boolean.parseBoolean(env("HEADLESS", "true"));

		if (loginUrl.isBlank()) {
			markAsBlocked(report, "SALEADS_LOGIN_URL is required and must point to the SaleADS login page.");
			writeReport(report, artifactsDir, "FAILED_PRECONDITION");
			Assert.fail("Missing required environment variable: SALEADS_LOGIN_URL");
		}

		Page appPage = null;
		BrowserContext context = null;
		String executionStatus = "PASS";

		try (Playwright playwright = Playwright.create();
		     Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless))) {
			final Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();
			final String storageStatePath = env("SALEADS_STORAGE_STATE", "");
			if (!storageStatePath.isBlank()) {
				contextOptions.setStorageStatePath(Paths.get(storageStatePath));
			}
			context = browser.newContext(contextOptions);
			appPage = context.newPage();
			appPage.navigate(loginUrl, new Page.NavigateOptions().setTimeout(LONG_TIMEOUT_MS));
			waitForUi(appPage);

			final boolean loginOk = stepLoginWithGoogle(appPage, context, report.get("Login"), googleAccountEmail, artifactsDir);
			final boolean menuOk = loginOk && stepOpenMiNegocioMenu(appPage, report.get("Mi Negocio menu"), artifactsDir);
			final boolean modalOk = menuOk && stepValidateAgregarNegocioModal(appPage, report.get("Agregar Negocio modal"), artifactsDir);
			final boolean adminViewOk = menuOk && stepOpenAdministrarNegocios(appPage, report.get("Administrar Negocios view"), artifactsDir);
			final boolean infoGeneralOk = adminViewOk && stepValidateInformacionGeneral(appPage, report.get("Información General"));
			final boolean accountDetailsOk = adminViewOk && stepValidateDetallesCuenta(appPage, report.get("Detalles de la Cuenta"));
			final boolean businessesOk = adminViewOk && stepValidateTusNegocios(appPage, report.get("Tus Negocios"));
			final boolean termsOk = adminViewOk && stepValidateLegalLink(appPage, context, report.get("Términos y Condiciones"),
				"Términos y Condiciones", Pattern.compile("T[eé]rminos y Condiciones", Pattern.CASE_INSENSITIVE), "terminos-y-condiciones",
				artifactsDir);
			final boolean privacyOk = adminViewOk && stepValidateLegalLink(appPage, context, report.get("Política de Privacidad"),
				"Política de Privacidad", Pattern.compile("Pol[ií]tica de Privacidad", Pattern.CASE_INSENSITIVE), "politica-de-privacidad",
				artifactsDir);

			if (!menuOk) {
				markBlocked(report.get("Agregar Negocio modal"), "Blocked because 'Mi Negocio menu' failed.");
			}
			if (!adminViewOk) {
				markBlocked(report.get("Información General"), "Blocked because 'Administrar Negocios view' failed.");
				markBlocked(report.get("Detalles de la Cuenta"), "Blocked because 'Administrar Negocios view' failed.");
				markBlocked(report.get("Tus Negocios"), "Blocked because 'Administrar Negocios view' failed.");
				markBlocked(report.get("Términos y Condiciones"), "Blocked because 'Administrar Negocios view' failed.");
				markBlocked(report.get("Política de Privacidad"), "Blocked because 'Administrar Negocios view' failed.");
			}

			if (!(loginOk && menuOk && modalOk && adminViewOk && infoGeneralOk && accountDetailsOk && businessesOk && termsOk && privacyOk)) {
				executionStatus = "FAIL";
			}
		} catch (Exception e) {
			executionStatus = "FAIL";
			if (appPage != null) {
				safeScreenshot(appPage, artifactsDir.resolve("unexpected-error.png"), report.get("Login"));
			}
			report.get("Login").errors.add("Unexpected test error: " + e.getMessage());
			markUnfinishedStepsAsBlocked(report);
		}

		writeReport(report, artifactsDir, executionStatus);
		Assert.assertTrue("One or more SaleADS Mi Negocio validations failed. Report: " + artifactsDir.resolve("final-report.json"),
			allStepsPassed(report));
	}

	private boolean stepLoginWithGoogle(final Page appPage, final BrowserContext context, final StepResult result,
	                                    final String googleAccountEmail, final Path artifactsDir) {
		result.start();
		final Pattern loginBtnPattern = Pattern.compile("(Sign in|Iniciar sesi[oó]n|Acceder|Continuar).*(Google)|Google",
			Pattern.CASE_INSENSITIVE);
		final boolean[] loginClicked = new boolean[] {false};
		Page googlePage = null;
		try {
			googlePage = context.waitForPage(() -> loginClicked[0] = clickByTextPattern(appPage, loginBtnPattern, MEDIUM_TIMEOUT_MS),
				new BrowserContext.WaitForPageOptions().setTimeout(MEDIUM_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// Google flow can continue in the same tab.
		}

		if (!loginClicked[0]) {
			loginClicked[0] = clickByTextPattern(appPage, loginBtnPattern, MEDIUM_TIMEOUT_MS);
		}
		check(result, "Login button / 'Sign in with Google' is clickable", loginClicked[0]);
		if (!loginClicked[0]) {
			result.finish();
			return false;
		}
		waitForUi(appPage);

		if (googlePage != null) {
			waitForUi(googlePage);
			clickByExactText(googlePage, googleAccountEmail, MEDIUM_TIMEOUT_MS);
			waitForUi(appPage);
		} else {
			clickByExactText(appPage, googleAccountEmail, MEDIUM_TIMEOUT_MS);
			waitForUi(appPage);
		}

		final boolean mainInterfaceVisible = waitForAnyVisibleText(appPage,
			Arrays.asList(
				Pattern.compile("Dashboard|Panel|Inicio", Pattern.CASE_INSENSITIVE),
				Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE)),
			LONG_TIMEOUT_MS);
		final boolean leftSidebarVisible = isAnyVisible(appPage, "aside", "nav");
		check(result, "Main application interface is visible", mainInterfaceVisible);
		check(result, "Left sidebar navigation is visible", leftSidebarVisible);

		final Path dashboardShot = artifactsDir.resolve("01-dashboard-loaded.png");
		safeScreenshot(appPage, dashboardShot, result);
		result.metadata.put("dashboardScreenshot", dashboardShot.toString());
		result.finish();
		return result.passed;
	}

	private boolean stepOpenMiNegocioMenu(final Page appPage, final StepResult result, final Path artifactsDir) {
		result.start();
		final boolean leftSidebarVisible = isAnyVisible(appPage, "aside", "nav");
		check(result, "Left sidebar navigation is visible", leftSidebarVisible);

		final boolean clickedNegocio = clickByTextPattern(appPage, Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE), MEDIUM_TIMEOUT_MS);
		final boolean clickedMiNegocio = clickByTextPattern(appPage, Pattern.compile("Mi Negocio", Pattern.CASE_INSENSITIVE), MEDIUM_TIMEOUT_MS);
		check(result, "Menu section 'Negocio' can be opened", clickedNegocio || clickedMiNegocio);
		check(result, "Option 'Mi Negocio' can be clicked", clickedMiNegocio);

		final boolean submenuExpanded = waitForAnyVisibleText(appPage,
			Arrays.asList(
				Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE),
				Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE)),
			MEDIUM_TIMEOUT_MS);
		final boolean agregarVisible = isTextVisible(appPage, Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE));
		final boolean administrarVisible = isTextVisible(appPage, Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE));
		check(result, "Mi Negocio submenu expands", submenuExpanded);
		check(result, "'Agregar Negocio' is visible", agregarVisible);
		check(result, "'Administrar Negocios' is visible", administrarVisible);

		final Path menuShot = artifactsDir.resolve("02-mi-negocio-menu-expanded.png");
		safeScreenshot(appPage, menuShot, result);
		result.metadata.put("expandedMenuScreenshot", menuShot.toString());
		result.finish();
		return result.passed;
	}

	private boolean stepValidateAgregarNegocioModal(final Page appPage, final StepResult result, final Path artifactsDir) {
		result.start();
		final boolean clickAgregar = clickByTextPattern(appPage, Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE), MEDIUM_TIMEOUT_MS);
		check(result, "Clicked 'Agregar Negocio'", clickAgregar);
		if (!clickAgregar) {
			result.finish();
			return false;
		}

		final Locator modal = appPage.getByRole(AriaRole.DIALOG).first();
		final boolean modalVisible = waitForVisible(modal, MEDIUM_TIMEOUT_MS);
		check(result, "Agregar Negocio modal is visible", modalVisible);
		check(result, "Modal title 'Crear Nuevo Negocio' is visible",
			isTextVisible(appPage, Pattern.compile("Crear Nuevo Negocio", Pattern.CASE_INSENSITIVE)));
		check(result, "Input field 'Nombre del Negocio' exists",
			isAnyVisible(appPage, "input[placeholder*='Nombre del Negocio']", "input[name*='nombre']", "input[id*='nombre']"));
		check(result, "Text 'Tienes 2 de 3 negocios' is visible",
			isTextVisible(appPage, Pattern.compile("Tienes\\s*2\\s*de\\s*3\\s*negocios", Pattern.CASE_INSENSITIVE)));
		check(result, "Buttons 'Cancelar' and 'Crear Negocio' are present",
			isTextVisible(appPage, Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE))
				&& isTextVisible(appPage, Pattern.compile("Crear Negocio", Pattern.CASE_INSENSITIVE)));

		final Path modalShot = artifactsDir.resolve("03-agregar-negocio-modal.png");
		safeScreenshot(appPage, modalShot, result);
		result.metadata.put("modalScreenshot", modalShot.toString());

		// Optional exercise for field validation.
		final Locator businessNameInput = firstVisibleLocator(appPage,
			"input[placeholder*='Nombre del Negocio']",
			"input[name*='nombre']",
			"input[id*='nombre']");
		if (businessNameInput != null) {
			businessNameInput.click();
			businessNameInput.fill("Negocio Prueba Automatizacion");
			waitForUi(appPage);
		}

		clickByTextPattern(appPage, Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE), MEDIUM_TIMEOUT_MS);
		appPage.waitForTimeout(500);
		result.finish();
		return result.passed;
	}

	private boolean stepOpenAdministrarNegocios(final Page appPage, final StepResult result, final Path artifactsDir) {
		result.start();
		if (!isTextVisible(appPage, Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE))) {
			clickByTextPattern(appPage, Pattern.compile("Mi Negocio", Pattern.CASE_INSENSITIVE), MEDIUM_TIMEOUT_MS);
		}
		final boolean clickedAdministrar = clickByTextPattern(appPage, Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE),
			MEDIUM_TIMEOUT_MS);
		check(result, "Clicked 'Administrar Negocios'", clickedAdministrar);
		waitForUi(appPage);

		check(result, "Section 'Informacion General' exists",
			isTextVisible(appPage, Pattern.compile("Informaci[oó]n General", Pattern.CASE_INSENSITIVE)));
		check(result, "Section 'Detalles de la Cuenta' exists",
			isTextVisible(appPage, Pattern.compile("Detalles de la Cuenta", Pattern.CASE_INSENSITIVE)));
		check(result, "Section 'Tus Negocios' exists",
			isTextVisible(appPage, Pattern.compile("Tus Negocios", Pattern.CASE_INSENSITIVE)));
		check(result, "Section 'Seccion Legal' exists",
			isTextVisible(appPage, Pattern.compile("Secci[oó]n Legal", Pattern.CASE_INSENSITIVE)));

		final Path accountShot = artifactsDir.resolve("04-administrar-negocios-view-full.png");
		safeFullPageScreenshot(appPage, accountShot, result);
		result.metadata.put("administrarNegociosScreenshot", accountShot.toString());
		result.finish();
		return result.passed;
	}

	private boolean stepValidateInformacionGeneral(final Page appPage, final StepResult result) {
		result.start();
		final String bodyText = safeTextContent(appPage, "body");
		check(result, "User name is visible", Pattern.compile("Nombre|Usuario|Perfil", Pattern.CASE_INSENSITIVE).matcher(bodyText).find());
		check(result, "User email is visible",
			Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(bodyText).find());
		check(result, "Text 'BUSINESS PLAN' is visible",
			isTextVisible(appPage, Pattern.compile("BUSINESS PLAN", Pattern.CASE_INSENSITIVE)));
		check(result, "Button 'Cambiar Plan' is visible",
			isTextVisible(appPage, Pattern.compile("Cambiar Plan", Pattern.CASE_INSENSITIVE)));
		result.finish();
		return result.passed;
	}

	private boolean stepValidateDetallesCuenta(final Page appPage, final StepResult result) {
		result.start();
		check(result, "'Cuenta creada' is visible", isTextVisible(appPage, Pattern.compile("Cuenta creada", Pattern.CASE_INSENSITIVE)));
		check(result, "'Estado activo' is visible", isTextVisible(appPage, Pattern.compile("Estado activo", Pattern.CASE_INSENSITIVE)));
		check(result, "'Idioma seleccionado' is visible",
			isTextVisible(appPage, Pattern.compile("Idioma seleccionado", Pattern.CASE_INSENSITIVE)));
		result.finish();
		return result.passed;
	}

	private boolean stepValidateTusNegocios(final Page appPage, final StepResult result) {
		result.start();
		check(result, "Business list is visible",
			isTextVisible(appPage, Pattern.compile("Tus Negocios", Pattern.CASE_INSENSITIVE))
				&& (isAnyVisible(appPage, "[data-testid*='business']", "[class*='business']") || isTextVisible(appPage,
				Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE))));
		check(result, "Button 'Agregar Negocio' exists",
			isTextVisible(appPage, Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE)));
		check(result, "Text 'Tienes 2 de 3 negocios' is visible",
			isTextVisible(appPage, Pattern.compile("Tienes\\s*2\\s*de\\s*3\\s*negocios", Pattern.CASE_INSENSITIVE)));
		result.finish();
		return result.passed;
	}

	private boolean stepValidateLegalLink(final Page appPage, final BrowserContext context, final StepResult result,
	                                      final String reportName, final Pattern headingPattern, final String screenshotSuffix,
	                                      final Path artifactsDir) {
		result.start();
		Page legalPage = appPage;
		boolean openedNewTab = false;
		final boolean[] clicked = new boolean[] {false};

		try {
			legalPage = context.waitForPage(
				() -> clicked[0] = clickByTextPattern(appPage, headingPattern, MEDIUM_TIMEOUT_MS),
				new BrowserContext.WaitForPageOptions().setTimeout(7000));
			openedNewTab = true;
		} catch (PlaywrightException ignored) {
			// Link can navigate in the same tab. Click has already been attempted in waitForPage callback.
		}
		if (!clicked[0]) {
			clicked[0] = clickByTextPattern(appPage, headingPattern, MEDIUM_TIMEOUT_MS);
		}
		check(result, "Clicked '" + reportName + "'", clicked[0]);
		if (!clicked[0]) {
			result.finish();
			return false;
		}

		waitForUi(legalPage);
		check(result, "The page contains heading '" + reportName + "'", isTextVisible(legalPage, headingPattern));
		final String legalBody = safeTextContent(legalPage, "body");
		check(result, "Legal content text is visible", legalBody.trim().length() > 150);
		result.metadata.put("finalUrl", legalPage.url());

		final Path legalShot = artifactsDir.resolve("0" + ("Términos y Condiciones".equals(reportName) ? "5" : "6")
			+ "-legal-" + screenshotSuffix + ".png");
		safeFullPageScreenshot(legalPage, legalShot, result);
		result.metadata.put("screenshot", legalShot.toString());

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				appPage.goBack();
				waitForUi(appPage);
			} catch (PlaywrightException ignored) {
				// Some links may route within SPA and not create browser history entries.
			}
		}

		result.finish();
		return result.passed;
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(MEDIUM_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// Some flows may not trigger a navigation event.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(MEDIUM_TIMEOUT_MS));
		} catch (PlaywrightException ignored) {
			// SPAs may keep open requests; proceed with a small stability pause.
		}
		page.waitForTimeout(700);
	}

	private boolean clickByExactText(final Page page, final String text, final long timeoutMs) {
		if (text == null || text.isBlank()) {
			return false;
		}
		final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
		if (waitForVisible(locator, timeoutMs)) {
			locator.click();
			waitForUi(page);
			return true;
		}
		return false;
	}

	private boolean clickByTextPattern(final Page page, final Pattern pattern, final long timeoutMs) {
		final Locator locator = page.getByText(pattern).first();
		if (waitForVisible(locator, timeoutMs)) {
			locator.click();
			waitForUi(page);
			return true;
		}
		return false;
	}

	private boolean waitForAnyVisibleText(final Page page, final List<Pattern> patterns, final long timeoutMs) {
		final long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start <= timeoutMs) {
			for (Pattern pattern : patterns) {
				if (isTextVisible(page, pattern)) {
					return true;
				}
			}
			page.waitForTimeout(300);
		}
		return false;
	}

	private boolean waitForVisible(final Locator locator, final long timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
				.setTimeout(timeoutMs));
			return true;
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private boolean isTextVisible(final Page page, final Pattern pattern) {
		try {
			final Locator locator = page.getByText(pattern).first();
			return locator.count() > 0 && locator.isVisible();
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private boolean isAnyVisible(final Page page, final String... selectors) {
		for (String selector : selectors) {
			try {
				final Locator locator = page.locator(selector).first();
				if (locator.count() > 0 && locator.isVisible()) {
					return true;
				}
			} catch (PlaywrightException ignored) {
				// Continue checking fallback selectors.
			}
		}
		return false;
	}

	private Locator firstVisibleLocator(final Page page, final String... selectors) {
		for (String selector : selectors) {
			try {
				final Locator locator = page.locator(selector).first();
				if (locator.count() > 0 && locator.isVisible()) {
					return locator;
				}
			} catch (PlaywrightException ignored) {
				// Continue trying alternative selectors.
			}
		}
		return null;
	}

	private static String safeTextContent(final Page page, final String selector) {
		try {
			return page.locator(selector).first().innerText();
		} catch (PlaywrightException e) {
			return "";
		}
	}

	private static void safeScreenshot(final Page page, final Path output, final StepResult stepResult) {
		try {
			page.screenshot(new Page.ScreenshotOptions().setPath(output));
			stepResult.evidence.add(output.toString());
		} catch (PlaywrightException e) {
			stepResult.errors.add("Could not capture screenshot '" + output + "': " + e.getMessage());
		}
	}

	private static void safeFullPageScreenshot(final Page page, final Path output, final StepResult stepResult) {
		try {
			page.screenshot(new Page.ScreenshotOptions().setPath(output).setFullPage(true));
			stepResult.evidence.add(output.toString());
		} catch (PlaywrightException e) {
			stepResult.errors.add("Could not capture full-page screenshot '" + output + "': " + e.getMessage());
		}
	}

	private static Path createArtifactsDir() throws IOException {
		final Path path = Paths.get("target", "saleads-mi-negocio", TS_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(path);
		return path;
	}

	private static void check(final StepResult result, final String message, final boolean condition) {
		result.assertions.add((condition ? "PASS: " : "FAIL: ") + message);
		if (!condition) {
			result.passed = false;
		}
	}

	private static void markBlocked(final StepResult result, final String reason) {
		if (!result.started) {
			result.start();
		}
		result.passed = false;
		result.errors.add(reason);
		result.finish();
	}

	private static void markUnfinishedStepsAsBlocked(final LinkedHashMap<String, StepResult> report) {
		for (StepResult result : report.values()) {
			if (!result.finished) {
				markBlocked(result, "Not completed due to unexpected failure in workflow execution.");
			}
		}
	}

	private static void markAsBlocked(final LinkedHashMap<String, StepResult> report, final String reason) {
		for (StepResult result : report.values()) {
			markBlocked(result, reason);
		}
	}

	private static boolean allStepsPassed(final Map<String, StepResult> report) {
		for (StepResult result : report.values()) {
			if (!result.passed) {
				return false;
			}
		}
		return true;
	}

	private static void writeReport(final LinkedHashMap<String, StepResult> report, final Path artifactsDir,
	                                final String executionStatus) throws IOException {
		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"testName\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"executionStatus\": \"").append(escapeJson(executionStatus)).append("\",\n");
		json.append("  \"generatedAt\": \"").append(escapeJson(LocalDateTime.now().toString())).append("\",\n");
		json.append("  \"artifactsDirectory\": \"").append(escapeJson(artifactsDir.toString())).append("\",\n");
		json.append("  \"results\": {\n");

		int i = 0;
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			final String key = entry.getKey();
			final StepResult result = entry.getValue();
			json.append("    \"").append(escapeJson(key)).append("\": {\n");
			json.append("      \"status\": \"").append(result.passed ? "PASS" : "FAIL").append("\",\n");
			json.append("      \"assertions\": ").append(toJsonArray(result.assertions)).append(",\n");
			json.append("      \"evidence\": ").append(toJsonArray(result.evidence)).append(",\n");
			json.append("      \"errors\": ").append(toJsonArray(result.errors)).append(",\n");
			json.append("      \"metadata\": ").append(toJsonObject(result.metadata)).append("\n");
			json.append("    }");
			if (i < report.size() - 1) {
				json.append(",");
			}
			json.append("\n");
			i++;
		}
		json.append("  }\n");
		json.append("}\n");

		final Path reportFile = artifactsDir.resolve("final-report.json");
		Files.writeString(reportFile, json.toString(), StandardCharsets.UTF_8);
	}

	private static String toJsonArray(final List<String> values) {
		final StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append("\"").append(escapeJson(values.get(i))).append("\"");
		}
		sb.append("]");
		return sb.toString();
	}

	private static String toJsonObject(final Map<String, String> map) {
		final StringBuilder sb = new StringBuilder("{");
		int i = 0;
		for (Map.Entry<String, String> entry : map.entrySet()) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append("\"").append(escapeJson(entry.getKey())).append("\": ");
			sb.append("\"").append(escapeJson(entry.getValue())).append("\"");
			i++;
		}
		sb.append("}");
		return sb.toString();
	}

	private static String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r");
	}

	private static String env(final String key, final String fallback) {
		final String value = System.getenv(key);
		return value == null ? fallback : value;
	}

	private static final class StepResult {
		private boolean passed = true;
		private boolean started = false;
		private boolean finished = false;
		private final List<String> assertions = new ArrayList<>();
		private final List<String> evidence = new ArrayList<>();
		private final List<String> errors = new ArrayList<>();
		private final Map<String, String> metadata = new LinkedHashMap<>();

		private void start() {
			this.started = true;
		}

		private void finish() {
			this.finished = true;
		}
	}
}
