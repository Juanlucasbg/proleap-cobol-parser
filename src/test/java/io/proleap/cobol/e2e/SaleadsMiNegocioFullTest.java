package io.proleap.cobol.e2e;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;

public class SaleadsMiNegocioFullTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MENU = "Mi Negocio menu";
	private static final String REPORT_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN = "Administrar Negocios view";
	private static final String REPORT_GENERAL = "Información General";
	private static final String REPORT_ACCOUNT = "Detalles de la Cuenta";
	private static final String REPORT_BUSINESSES = "Tus Negocios";
	private static final String REPORT_TERMS = "Términos y Condiciones";
	private static final String REPORT_PRIVACY = "Política de Privacidad";

	private static final String TEXT_TERMS = "Términos y Condiciones";
	private static final String TEXT_PRIVACY = "Política de Privacidad";
	private static final String TEXT_INFO_GENERAL = "Información General";
	private static final String TEXT_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String TEXT_LEGAL_SECTION = "Sección Legal";

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final Map<String, StepResult> results = initializeResults();
		final Path artifactsDir = createArtifactsDirectory();
		Path reportPath = artifactsDir.resolve("final-report.md");

		Page appPage = null;
		String fatalError = null;

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(resolveHeadlessMode()));
			try (BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200))) {
				final Page page = context.newPage();
				appPage = page;

				runStepLogin(results.get(REPORT_LOGIN), context, page, artifactsDir);
				if (results.get(REPORT_LOGIN).status == StepStatus.PASS) {
					appPage = waitForApplicationPage(context, 60000L);
				} else {
					markSkipped(results.get(REPORT_MENU), "Skipped because login did not complete successfully.");
					markSkipped(results.get(REPORT_MODAL), "Skipped because login did not complete successfully.");
					markSkipped(results.get(REPORT_ADMIN), "Skipped because login did not complete successfully.");
					markSkipped(results.get(REPORT_GENERAL), "Skipped because login did not complete successfully.");
					markSkipped(results.get(REPORT_ACCOUNT), "Skipped because login did not complete successfully.");
					markSkipped(results.get(REPORT_BUSINESSES), "Skipped because login did not complete successfully.");
					markSkipped(results.get(REPORT_TERMS), "Skipped because login did not complete successfully.");
					markSkipped(results.get(REPORT_PRIVACY), "Skipped because login did not complete successfully.");
				}

				if (results.get(REPORT_MENU).status == StepStatus.SKIPPED && results.get(REPORT_LOGIN).status == StepStatus.PASS) {
					runStepMenu(results.get(REPORT_MENU), appPage, artifactsDir);
				}

				if (results.get(REPORT_MENU).status == StepStatus.PASS) {
					runStepAgregarModal(results.get(REPORT_MODAL), appPage, artifactsDir);
				} else if (results.get(REPORT_MODAL).status == StepStatus.SKIPPED) {
					markSkipped(results.get(REPORT_MODAL), "Skipped because Mi Negocio menu step did not pass.");
				}

				if (results.get(REPORT_MENU).status == StepStatus.PASS) {
					runStepAdministrarNegocios(results.get(REPORT_ADMIN), appPage, artifactsDir);
				} else if (results.get(REPORT_ADMIN).status == StepStatus.SKIPPED) {
					markSkipped(results.get(REPORT_ADMIN), "Skipped because Mi Negocio menu step did not pass.");
				}

				if (results.get(REPORT_ADMIN).status == StepStatus.PASS) {
					runStepInformacionGeneral(results.get(REPORT_GENERAL), appPage);
					runStepDetallesCuenta(results.get(REPORT_ACCOUNT), appPage);
					runStepTusNegocios(results.get(REPORT_BUSINESSES), appPage);
					runStepLegal(results.get(REPORT_TERMS), appPage, artifactsDir, TEXT_TERMS, "terms-page");
					runStepLegal(results.get(REPORT_PRIVACY), appPage, artifactsDir, TEXT_PRIVACY, "privacy-page");
				} else {
					markSkipped(results.get(REPORT_GENERAL), "Skipped because Administrar Negocios view did not load.");
					markSkipped(results.get(REPORT_ACCOUNT), "Skipped because Administrar Negocios view did not load.");
					markSkipped(results.get(REPORT_BUSINESSES), "Skipped because Administrar Negocios view did not load.");
					markSkipped(results.get(REPORT_TERMS), "Skipped because Administrar Negocios view did not load.");
					markSkipped(results.get(REPORT_PRIVACY), "Skipped because Administrar Negocios view did not load.");
				}
			}
		} catch (Exception exception) {
			fatalError = "Unhandled exception during workflow execution: " + exception.getMessage();
		}

		if (fatalError != null) {
			final StepResult loginResult = results.get(REPORT_LOGIN);
			if (loginResult.status == StepStatus.SKIPPED) {
				markFail(loginResult, fatalError);
				if (appPage != null) {
					loginResult.screenshot = takeScreenshot(appPage, artifactsDir, "fatal-error");
				}
			}
		}

		reportPath = writeFinalReport(artifactsDir, results);
		final String summary = buildSummary(results, reportPath);
		if (hasFailures(results) || fatalError != null) {
			fail(summary);
		}
	}

	private void runStepLogin(final StepResult result, final BrowserContext context, final Page page, final Path artifactsDir) {
		try {
			final String loginUrl = resolveLoginUrl();
			if (isBlank(loginUrl)) {
				markFail(result,
						"Missing login URL. Provide one with SALEADS_LOGIN_URL or -Dsaleads.login.url and point to the active environment login page.");
				return;
			}

			page.navigate(loginUrl);
			waitForUi(page);

			final Locator signInWithGoogle = waitForVisibleText(page, 20000L, "Sign in with Google",
					"Iniciar sesión con Google", "Continuar con Google", "Google");

			final int pageCountBeforeLoginClick = context.pages().size();
			signInWithGoogle.click();
			waitForUi(page);

			final Page accountPage = waitForAnyPage(context, pageCountBeforeLoginClick, 10000L);
			if (accountPage != null) {
				accountPage.bringToFront();
				waitForUi(accountPage);
				clickIfVisible(accountPage, GOOGLE_ACCOUNT_EMAIL);
				waitForUi(accountPage);
			} else {
				clickIfVisible(page, GOOGLE_ACCOUNT_EMAIL);
				waitForUi(page);
			}

			final Page applicationPage = waitForApplicationPage(context, 90000L);
			if (applicationPage == null) {
				markFail(result, "Could not detect post-login SaleADS application interface.");
				result.screenshot = takeScreenshot(page, artifactsDir, "login-failed");
				return;
			}

			final boolean sidebarVisible = isSidebarVisible(applicationPage);
			final boolean interfaceVisible = hasAnyVisibleText(applicationPage, "Mi Negocio", "Negocio", "Dashboard",
					"Inicio");

			if (!sidebarVisible || !interfaceVisible) {
				markFail(result, "Login completed but main interface/left sidebar was not confirmed.");
				result.screenshot = takeScreenshot(applicationPage, artifactsDir, "dashboard-missing-sidebar");
				return;
			}

			result.screenshot = takeScreenshot(applicationPage, artifactsDir, "01-dashboard-loaded");
			markPass(result, "Main interface and left sidebar are visible.");
		} catch (Exception exception) {
			markFail(result, "Login step failed: " + exception.getMessage());
			result.screenshot = safeTakeScreenshot(page, artifactsDir, "01-login-exception");
		}
	}

	private void runStepMenu(final StepResult result, final Page appPage, final Path artifactsDir) {
		try {
			waitForUi(appPage);
			clickIfVisible(appPage, "Negocio");
			waitForUi(appPage);
			waitForVisibleText(appPage, 20000L, "Mi Negocio").click();
			waitForUi(appPage);

			final boolean submenuExpanded = hasAnyVisibleText(appPage, "Agregar Negocio")
					&& hasAnyVisibleText(appPage, "Administrar Negocios");
			if (!submenuExpanded) {
				markFail(result, "Mi Negocio submenu did not show both expected options.");
				result.screenshot = takeScreenshot(appPage, artifactsDir, "02-menu-expand-failed");
				return;
			}

			result.screenshot = takeScreenshot(appPage, artifactsDir, "02-mi-negocio-menu-expanded");
			markPass(result, "Mi Negocio menu expanded and expected options are visible.");
		} catch (Exception exception) {
			markFail(result, "Mi Negocio menu step failed: " + exception.getMessage());
			result.screenshot = safeTakeScreenshot(appPage, artifactsDir, "02-menu-exception");
		}
	}

	private void runStepAgregarModal(final StepResult result, final Page appPage, final Path artifactsDir) {
		try {
			waitForUi(appPage);
			waitForVisibleText(appPage, 15000L, "Agregar Negocio").click();
			waitForUi(appPage);

			final boolean titleVisible = hasAnyVisibleText(appPage, "Crear Nuevo Negocio");
			final boolean businessNameVisible = hasAnyVisibleText(appPage, "Nombre del Negocio")
					|| appPage.locator("input[placeholder*='Nombre']").count() > 0
					|| appPage.locator("input[name*='nombre']").count() > 0;
			final boolean limitVisible = hasAnyVisibleText(appPage, "Tienes 2 de 3 negocios");
			final boolean actionsVisible = hasAnyVisibleText(appPage, "Cancelar")
					&& hasAnyVisibleText(appPage, "Crear Negocio");

			if (!(titleVisible && businessNameVisible && limitVisible && actionsVisible)) {
				markFail(result, "Agregar Negocio modal did not contain all expected fields and actions.");
				result.screenshot = takeScreenshot(appPage, artifactsDir, "03-modal-validation-failed");
				return;
			}

			final Locator nameField = firstVisibleLocator(appPage, "input[placeholder*='Nombre']",
					"input[name*='nombre']", "input[type='text']");
			if (nameField != null) {
				nameField.click();
				waitForUi(appPage);
				nameField.fill("Negocio Prueba Automatización");
				waitForUi(appPage);
			}

			result.screenshot = takeScreenshot(appPage, artifactsDir, "03-agregar-negocio-modal");
			clickIfVisible(appPage, "Cancelar");
			waitForUi(appPage);
			markPass(result, "Agregar Negocio modal validated and closed with Cancelar.");
		} catch (Exception exception) {
			markFail(result, "Agregar Negocio modal step failed: " + exception.getMessage());
			result.screenshot = safeTakeScreenshot(appPage, artifactsDir, "03-modal-exception");
		}
	}

	private void runStepAdministrarNegocios(final StepResult result, final Page appPage, final Path artifactsDir) {
		try {
			waitForUi(appPage);
			clickIfVisible(appPage, "Mi Negocio");
			waitForUi(appPage);
			waitForVisibleText(appPage, 15000L, "Administrar Negocios").click();
			waitForUi(appPage);

			final boolean allSectionsVisible = hasAnyVisibleText(appPage, TEXT_INFO_GENERAL)
					&& hasAnyVisibleText(appPage, TEXT_ACCOUNT_DETAILS) && hasAnyVisibleText(appPage, "Tus Negocios")
					&& hasAnyVisibleText(appPage, TEXT_LEGAL_SECTION);
			if (!allSectionsVisible) {
				markFail(result, "Administrar Negocios page is missing one or more required sections.");
				result.screenshot = takeScreenshot(appPage, artifactsDir, "04-admin-view-validation-failed");
				return;
			}

			result.screenshot = takeScreenshot(appPage, artifactsDir, "04-administrar-negocios-full-page");
			markPass(result, "Administrar Negocios page loaded with all expected sections.");
		} catch (Exception exception) {
			markFail(result, "Administrar Negocios step failed: " + exception.getMessage());
			result.screenshot = safeTakeScreenshot(appPage, artifactsDir, "04-admin-exception");
		}
	}

	private void runStepInformacionGeneral(final StepResult result, final Page appPage) {
		try {
			waitForUi(appPage);
			final String bodyText = safeBodyText(appPage);

			final boolean userNameVisible = hasAnyVisibleText(appPage, "Nombre", "Name", "Usuario", "User");
			final boolean emailVisible = EMAIL_PATTERN.matcher(bodyText).find();
			final boolean planVisible = hasAnyVisibleText(appPage, "BUSINESS PLAN");
			final boolean changePlanVisible = hasAnyVisibleText(appPage, "Cambiar Plan");

			if (userNameVisible && emailVisible && planVisible && changePlanVisible) {
				markPass(result, "Información General fields validated.");
			} else {
				markFail(result,
						"Información General validation failed. userNameVisible=" + userNameVisible + ", emailVisible="
								+ emailVisible + ", planVisible=" + planVisible + ", changePlanVisible="
								+ changePlanVisible);
			}
		} catch (Exception exception) {
			markFail(result, "Información General step failed: " + exception.getMessage());
		}
	}

	private void runStepDetallesCuenta(final StepResult result, final Page appPage) {
		try {
			waitForUi(appPage);
			final boolean createdVisible = hasAnyVisibleText(appPage, "Cuenta creada");
			final boolean activeVisible = hasAnyVisibleText(appPage, "Estado activo");
			final boolean languageVisible = hasAnyVisibleText(appPage, "Idioma seleccionado");

			if (createdVisible && activeVisible && languageVisible) {
				markPass(result, "Detalles de la Cuenta fields validated.");
			} else {
				markFail(result, "Detalles de la Cuenta validation failed. cuentaCreada=" + createdVisible
						+ ", estadoActivo=" + activeVisible + ", idiomaSeleccionado=" + languageVisible);
			}
		} catch (Exception exception) {
			markFail(result, "Detalles de la Cuenta step failed: " + exception.getMessage());
		}
	}

	private void runStepTusNegocios(final StepResult result, final Page appPage) {
		try {
			waitForUi(appPage);
			final boolean sectionVisible = hasAnyVisibleText(appPage, "Tus Negocios");
			final boolean addBusinessVisible = hasAnyVisibleText(appPage, "Agregar Negocio");
			final boolean counterVisible = hasAnyVisibleText(appPage, "Tienes 2 de 3 negocios");
			final boolean businessListVisible = isLikelyBusinessListVisible(appPage);

			if (sectionVisible && addBusinessVisible && counterVisible && businessListVisible) {
				markPass(result, "Tus Negocios section validated.");
			} else {
				markFail(result, "Tus Negocios validation failed. sectionVisible=" + sectionVisible
						+ ", addBusinessVisible=" + addBusinessVisible + ", counterVisible=" + counterVisible
						+ ", businessListVisible=" + businessListVisible);
			}
		} catch (Exception exception) {
			markFail(result, "Tus Negocios step failed: " + exception.getMessage());
		}
	}

	private void runStepLegal(final StepResult result, final Page appPage, final Path artifactsDir, final String linkText,
			final String screenshotPrefix) {
		try {
			waitForUi(appPage);
			final BrowserContext context = appPage.context();
			final int pageCountBefore = context.pages().size();

			waitForVisibleText(appPage, 20000L, linkText).click();
			waitForUi(appPage);

			Page legalPage = waitForAnyPage(context, pageCountBefore, 8000L);
			boolean openedNewTab = true;
			if (legalPage == null) {
				legalPage = appPage;
				openedNewTab = false;
			}

			legalPage.bringToFront();
			waitForUi(legalPage);

			final boolean headingVisible = hasAnyVisibleText(legalPage, linkText);
			final String legalBody = safeBodyText(legalPage);
			final boolean legalTextVisible = legalBody != null && legalBody.trim().length() > 200;
			result.finalUrl = legalPage.url();
			result.screenshot = takeScreenshot(legalPage, artifactsDir, screenshotPrefix);

			if (headingVisible && legalTextVisible) {
				markPass(result, "Legal page validated. finalUrl=" + result.finalUrl);
			} else {
				markFail(result, "Legal page validation failed. headingVisible=" + headingVisible + ", legalTextVisible="
						+ legalTextVisible + ", finalUrl=" + result.finalUrl);
			}

			if (openedNewTab) {
				legalPage.close();
				appPage.bringToFront();
				waitForUi(appPage);
			} else {
				try {
					appPage.goBack();
					waitForUi(appPage);
				} catch (Exception ignored) {
					// If history navigation is unavailable, this step still keeps explicit diagnostics in report.
				}
			}
		} catch (Exception exception) {
			markFail(result, "Legal step failed for '" + linkText + "': " + exception.getMessage());
			result.screenshot = safeTakeScreenshot(appPage, artifactsDir, screenshotPrefix + "-exception");
		}
	}

	private static Map<String, StepResult> initializeResults() {
		final Map<String, StepResult> results = new LinkedHashMap<>();
		results.put(REPORT_LOGIN, new StepResult(REPORT_LOGIN));
		results.put(REPORT_MENU, new StepResult(REPORT_MENU));
		results.put(REPORT_MODAL, new StepResult(REPORT_MODAL));
		results.put(REPORT_ADMIN, new StepResult(REPORT_ADMIN));
		results.put(REPORT_GENERAL, new StepResult(REPORT_GENERAL));
		results.put(REPORT_ACCOUNT, new StepResult(REPORT_ACCOUNT));
		results.put(REPORT_BUSINESSES, new StepResult(REPORT_BUSINESSES));
		results.put(REPORT_TERMS, new StepResult(REPORT_TERMS));
		results.put(REPORT_PRIVACY, new StepResult(REPORT_PRIVACY));
		return results;
	}

	private static Path createArtifactsDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
		final Path artifactsDir = Paths.get("target", "saleads-e2e-artifacts", timestamp);
		Files.createDirectories(artifactsDir);
		return artifactsDir;
	}

	private static String resolveLoginUrl() {
		final String fromProperty = System.getProperty("saleads.login.url");
		if (!isBlank(fromProperty)) {
			return fromProperty.trim();
		}
		final String fromEnvironment = System.getenv("SALEADS_LOGIN_URL");
		if (!isBlank(fromEnvironment)) {
			return fromEnvironment.trim();
		}
		return null;
	}

	private static boolean resolveHeadlessMode() {
		final String fromProperty = System.getProperty("saleads.headless");
		if (!isBlank(fromProperty)) {
			return Boolean.parseBoolean(fromProperty);
		}
		final String fromEnvironment = System.getenv("SALEADS_HEADLESS");
		if (!isBlank(fromEnvironment)) {
			return Boolean.parseBoolean(fromEnvironment);
		}
		return true;
	}

	private static Page waitForAnyPage(final BrowserContext context, final int pageCountBefore, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			final List<Page> pages = context.pages();
			if (pages.size() > pageCountBefore) {
				return pages.get(pages.size() - 1);
			}
			sleep(250L);
		}
		return null;
	}

	private static Page waitForApplicationPage(final BrowserContext context, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (Page candidate : context.pages()) {
				try {
					candidate.bringToFront();
					waitForUi(candidate);
					if (isSidebarVisible(candidate)
							&& hasAnyVisibleText(candidate, "Mi Negocio", "Negocio", TEXT_INFO_GENERAL, "Dashboard")) {
						return candidate;
					}
				} catch (Exception ignored) {
					// Keep scanning other pages until timeout.
				}
			}
			sleep(500L);
		}
		return null;
	}

	private static boolean isSidebarVisible(final Page page) {
		return page.locator("aside").count() > 0 || page.locator("nav").count() > 0
				|| hasAnyVisibleText(page, "Mi Negocio", "Negocio");
	}

	private static boolean isLikelyBusinessListVisible(final Page page) {
		final boolean hasRows = page.locator("li").count() > 0 || page.locator("tr").count() > 0
				|| page.locator("[class*='business']").count() > 0 || page.locator("[data-testid*='business']").count() > 0;
		if (hasRows) {
			return true;
		}

		final String bodyText = safeBodyText(page);
		return bodyText != null && (bodyText.contains("Negocio") || bodyText.contains("Business"));
	}

	private static Locator waitForVisibleText(final Page page, final long timeoutMs, final String... texts) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (String text : texts) {
				try {
					final Locator locator = page.locator("text=" + text);
					if (locator.count() > 0 && locator.first().isVisible()) {
						return locator.first();
					}
				} catch (Exception ignored) {
					// Ignore transient selector failures while page is updating.
				}
			}
			waitForUi(page);
			sleep(150L);
		}
		throw new RuntimeException("Could not find visible text within timeout: " + String.join(", ", texts));
	}

	private static Locator firstVisibleLocator(final Page page, final String... selectors) {
		for (String selector : selectors) {
			try {
				final Locator locator = page.locator(selector);
				if (locator.count() > 0 && locator.first().isVisible()) {
					return locator.first();
				}
			} catch (Exception ignored) {
				// Continue trying fallback selectors.
			}
		}
		return null;
	}

	private static boolean hasAnyVisibleText(final Page page, final String... texts) {
		for (String text : texts) {
			try {
				final Locator locator = page.locator("text=" + text);
				if (locator.count() > 0 && locator.first().isVisible()) {
					return true;
				}
			} catch (Exception ignored) {
				// Continue trying alternate text candidates.
			}
		}
		return false;
	}

	private static void clickIfVisible(final Page page, final String text) {
		try {
			final Locator locator = page.locator("text=" + text);
			if (locator.count() > 0 && locator.first().isVisible()) {
				locator.first().click();
			}
		} catch (Exception ignored) {
			// Optional click, so ignore if not possible.
		}
	}

	private static String safeBodyText(final Page page) {
		try {
			return page.locator("body").innerText();
		} catch (Exception ignored) {
			return "";
		}
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (Exception ignored) {
			// Some SPA transitions do not trigger this event.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (Exception ignored) {
			// NETWORKIDLE may not happen if analytics/network polling is active.
		}
		try {
			page.waitForTimeout(500);
		} catch (Exception ignored) {
			// Ignore if page has navigated.
		}
	}

	private static Path takeScreenshot(final Page page, final Path artifactsDir, final String filePrefix) {
		final Path screenshotPath = artifactsDir.resolve(filePrefix + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(true));
		return screenshotPath;
	}

	private static Path safeTakeScreenshot(final Page page, final Path artifactsDir, final String filePrefix) {
		try {
			return takeScreenshot(page, artifactsDir, filePrefix);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static Path writeFinalReport(final Path artifactsDir, final Map<String, StepResult> results) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("# SaleADS Mi Negocio workflow report\n\n");
		builder.append("Generated at: ").append(LocalDateTime.now()).append("\n\n");
		builder.append("| Step | Status | Details | Screenshot | Final URL |\n");
		builder.append("| --- | --- | --- | --- | --- |\n");

		for (StepResult result : results.values()) {
			builder.append("| ").append(result.name).append(" | ").append(result.status).append(" | ")
					.append(escapePipes(result.details)).append(" | ")
					.append(result.screenshot == null ? "-" : result.screenshot.toString()).append(" | ")
					.append(result.finalUrl == null ? "-" : result.finalUrl).append(" |\n");
		}

		final Path reportPath = artifactsDir.resolve("final-report.md");
		Files.write(reportPath, builder.toString().getBytes(StandardCharsets.UTF_8));
		return reportPath;
	}

	private static String buildSummary(final Map<String, StepResult> results, final Path reportPath) {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio workflow summary. Report: ").append(reportPath).append("\n");
		for (StepResult result : results.values()) {
			builder.append("- ").append(result.name).append(": ").append(result.status).append(" -> ")
					.append(result.details).append("\n");
		}
		return builder.toString();
	}

	private static String escapePipes(final String value) {
		if (value == null) {
			return "-";
		}
		return value.replace("|", "\\|").replace("\n", " ");
	}

	private static boolean hasFailures(final Map<String, StepResult> results) {
		for (StepResult result : results.values()) {
			if (result.status == StepStatus.FAIL) {
				return true;
			}
		}
		return false;
	}

	private static boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private static void markPass(final StepResult result, final String details) {
		result.status = StepStatus.PASS;
		result.details = details;
	}

	private static void markFail(final StepResult result, final String details) {
		result.status = StepStatus.FAIL;
		result.details = details;
	}

	private static void markSkipped(final StepResult result, final String details) {
		result.status = StepStatus.SKIPPED;
		result.details = details;
	}

	private enum StepStatus {
		PASS, FAIL, SKIPPED
	}

	private static final class StepResult {
		private final String name;
		private StepStatus status;
		private String details;
		private Path screenshot;
		private String finalUrl;

		private StepResult(final String name) {
			this.name = name;
			this.status = StepStatus.SKIPPED;
			this.details = "Not executed yet.";
		}
	}
}
