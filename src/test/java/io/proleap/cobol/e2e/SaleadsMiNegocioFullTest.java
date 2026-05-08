package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	@Test
	public void saleadsMiNegocioWorkflow() throws Exception {
		final String loginUrl = trimToNull(System.getenv("SALEADS_LOGIN_URL"));
		final String cdpUrl = trimToNull(System.getenv("SALEADS_CDP_URL"));
		Assume.assumeTrue("Set SALEADS_LOGIN_URL or SALEADS_CDP_URL to run this test.",
				loginUrl != null || cdpUrl != null);

		final Path evidenceDir = createEvidenceDirectory();
		final LinkedHashMap<String, StepResult> results = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final BrowserSession session = openSession(playwright, loginUrl, cdpUrl);
			try {
				final BrowserContext context = session.context;
				final Page appPage = session.appPage;

				results.put("Login", runStep(() -> validateLoginWithGoogle(appPage, context, evidenceDir)));
				results.put("Mi Negocio menu", runStep(() -> validateMiNegocioMenu(appPage, evidenceDir)));
				results.put("Agregar Negocio modal", runStep(() -> validateAgregarNegocioModal(appPage, evidenceDir)));
				results.put("Administrar Negocios view",
						runStep(() -> validateAdministrarNegociosView(appPage, evidenceDir)));
				results.put("Información General", runStep(() -> validateInformacionGeneral(appPage)));
				results.put("Detalles de la Cuenta", runStep(() -> validateDetallesCuenta(appPage)));
				results.put("Tus Negocios", runStep(() -> validateTusNegocios(appPage)));
				results.put("Términos y Condiciones",
						runStep(() -> validateLegalPage(appPage, context, evidenceDir, "Términos y Condiciones",
								"Términos y Condiciones", "08-terminos-y-condiciones")));
				results.put("Política de Privacidad",
						runStep(() -> validateLegalPage(appPage, context, evidenceDir, "Política de Privacidad",
								"Política de Privacidad", "09-politica-de-privacidad")));
			} finally {
				if (session.closeBrowserOnExit) {
					session.browser.close();
				}
			}
		}

		final Path reportPath = writeFinalReport(evidenceDir, results);
		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			if (!entry.getValue().passed) {
				failed.add(entry.getKey());
			}
		}

		assertTrue("Failed validation steps: " + failed + ". Report: " + reportPath.toAbsolutePath(), failed.isEmpty());
	}

	private StepResult validateLoginWithGoogle(final Page appPage, final BrowserContext context, final Path evidenceDir) {
		appPage.setDefaultTimeout(30000);
		waitForUiLoad(appPage);

		final boolean sidebarInitiallyVisible = isSidebarVisible(appPage);
		final StringBuilder details = new StringBuilder();
		if (!sidebarInitiallyVisible) {
			final int pagesBefore = context.pages().size();
			final boolean clickedLogin = clickByVisibleText(appPage, "Sign in with Google", "Iniciar sesión con Google",
					"Iniciar con Google", "Continuar con Google", "Google");

			if (!clickedLogin) {
				return StepResult.fail("Login button with Google text not found.");
			}

			Page googlePage = waitForNewPage(context, pagesBefore, 10000);
			if (googlePage != null) {
				details.append("Google selector opened in new tab. ");
				googlePage.setDefaultTimeout(15000);
				selectGoogleAccountIfVisible(googlePage, GOOGLE_ACCOUNT_EMAIL);
				waitForUiLoad(googlePage);
				if (!googlePage.isClosed()) {
					googlePage.bringToFront();
				}
			} else {
				selectGoogleAccountIfVisible(appPage, GOOGLE_ACCOUNT_EMAIL);
			}
		} else {
			details.append("Application was already authenticated. ");
		}

		appPage.bringToFront();
		waitForUiLoad(appPage);

		final boolean sidebarVisible = isSidebarVisible(appPage);
		final boolean dashboardVisible = isAnyTextVisible(appPage, "Dashboard", "Panel", "Inicio", "Negocio")
				|| sidebarVisible;
		final String screenshot = captureScreenshot(appPage, evidenceDir, "01-dashboard-loaded", true);
		final boolean passed = sidebarVisible && dashboardVisible;
		details.append("Sidebar visible=").append(sidebarVisible).append(", main UI visible=").append(dashboardVisible);

		return new StepResult(passed, details.toString(), screenshot, appPage.url());
	}

	private StepResult validateMiNegocioMenu(final Page appPage, final Path evidenceDir) {
		final boolean negocioVisible = clickByVisibleText(appPage, "Negocio") || isAnyTextVisible(appPage, "Negocio");
		final boolean clickedMiNegocio = clickByVisibleText(appPage, "Mi Negocio");
		waitForUiLoad(appPage);

		final boolean agregarVisible = isAnyTextVisible(appPage, "Agregar Negocio");
		final boolean administrarVisible = isAnyTextVisible(appPage, "Administrar Negocios");
		final String screenshot = captureScreenshot(appPage, evidenceDir, "02-mi-negocio-menu-expanded", false);

		final boolean passed = negocioVisible && clickedMiNegocio && agregarVisible && administrarVisible;
		final String details = "Negocio visible=" + negocioVisible + ", Mi Negocio clicked=" + clickedMiNegocio
				+ ", Agregar Negocio visible=" + agregarVisible + ", Administrar Negocios visible=" + administrarVisible;
		return new StepResult(passed, details, screenshot, appPage.url());
	}

	private StepResult validateAgregarNegocioModal(final Page appPage, final Path evidenceDir) {
		final boolean clickedAgregar = clickByVisibleText(appPage, "Agregar Negocio");
		waitForUiLoad(appPage);
		if (!clickedAgregar) {
			return StepResult.fail("Could not click 'Agregar Negocio'.");
		}

		final boolean titleVisible = isAnyTextVisible(appPage, "Crear Nuevo Negocio");
		final Locator nombreInput = findNombreNegocioInput(appPage);
		final boolean inputVisible = isVisible(nombreInput, 4000);
		final boolean quotaVisible = isAnyTextVisible(appPage, "Tienes 2 de 3 negocios");
		final boolean cancelarVisible = isAnyTextVisible(appPage, "Cancelar");
		final boolean crearVisible = isAnyTextVisible(appPage, "Crear Negocio");
		final String screenshot = captureScreenshot(appPage, evidenceDir, "03-agregar-negocio-modal", false);

		if (inputVisible) {
			nombreInput.fill("Negocio Prueba Automatización");
		}
		clickByVisibleText(appPage, "Cancelar");
		waitForUiLoad(appPage);

		final boolean passed = titleVisible && inputVisible && quotaVisible && cancelarVisible && crearVisible;
		final String details = "Modal title=" + titleVisible + ", Nombre field=" + inputVisible + ", quota=" + quotaVisible
				+ ", Cancelar=" + cancelarVisible + ", Crear Negocio=" + crearVisible;
		return new StepResult(passed, details, screenshot, appPage.url());
	}

	private StepResult validateAdministrarNegociosView(final Page appPage, final Path evidenceDir) {
		if (!isAnyTextVisible(appPage, "Administrar Negocios")) {
			clickByVisibleText(appPage, "Negocio");
			clickByVisibleText(appPage, "Mi Negocio");
		}

		final boolean clickedAdministrar = clickByVisibleText(appPage, "Administrar Negocios");
		waitForUiLoad(appPage);

		final boolean infoGeneral = isAnyTextVisible(appPage, "Información General");
		final boolean detallesCuenta = isAnyTextVisible(appPage, "Detalles de la Cuenta");
		final boolean tusNegocios = isAnyTextVisible(appPage, "Tus Negocios");
		final boolean seccionLegal = isAnyTextVisible(appPage, "Sección Legal");
		final String screenshot = captureScreenshot(appPage, evidenceDir, "04-administrar-negocios-view", true);

		final boolean passed = clickedAdministrar && infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
		final String details = "Administrar click=" + clickedAdministrar + ", Información General=" + infoGeneral
				+ ", Detalles de la Cuenta=" + detallesCuenta + ", Tus Negocios=" + tusNegocios + ", Sección Legal="
				+ seccionLegal;
		return new StepResult(passed, details, screenshot, appPage.url());
	}

	private StepResult validateInformacionGeneral(final Page appPage) {
		final String pageText = normalizeText(appPage.locator("body").innerText());
		final boolean emailVisible = EMAIL_PATTERN.matcher(pageText).find();
		final boolean businessPlanVisible = containsIgnoreCase(pageText, "BUSINESS PLAN");
		final boolean cambiarPlanVisible = containsIgnoreCase(pageText, "Cambiar Plan");
		final boolean userNameVisible = containsLikelyUserName(pageText);

		final boolean passed = userNameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible;
		final String details = "User name visible=" + userNameVisible + ", email visible=" + emailVisible
				+ ", BUSINESS PLAN visible=" + businessPlanVisible + ", Cambiar Plan visible=" + cambiarPlanVisible;
		return new StepResult(passed, details, null, appPage.url());
	}

	private StepResult validateDetallesCuenta(final Page appPage) {
		final boolean cuentaCreada = isAnyTextVisible(appPage, "Cuenta creada");
		final boolean estadoActivo = isAnyTextVisible(appPage, "Estado activo");
		final boolean idiomaSeleccionado = isAnyTextVisible(appPage, "Idioma seleccionado");

		final boolean passed = cuentaCreada && estadoActivo && idiomaSeleccionado;
		final String details = "Cuenta creada=" + cuentaCreada + ", Estado activo=" + estadoActivo
				+ ", Idioma seleccionado=" + idiomaSeleccionado;
		return new StepResult(passed, details, null, appPage.url());
	}

	private StepResult validateTusNegocios(final Page appPage) {
		final boolean sectionVisible = isAnyTextVisible(appPage, "Tus Negocios");
		final boolean agregarButton = isAnyTextVisible(appPage, "Agregar Negocio");
		final boolean quotaVisible = isAnyTextVisible(appPage, "Tienes 2 de 3 negocios");

		final boolean passed = sectionVisible && agregarButton && quotaVisible;
		final String details = "Business section=" + sectionVisible + ", Agregar Negocio=" + agregarButton + ", quota="
				+ quotaVisible;
		return new StepResult(passed, details, null, appPage.url());
	}

	private StepResult validateLegalPage(final Page appPage, final BrowserContext context, final Path evidenceDir,
			final String linkText, final String headingText, final String screenshotName) {
		final int pagesBefore = context.pages().size();
		final boolean clicked = clickByVisibleText(appPage, linkText);
		waitForUiLoad(appPage);
		if (!clicked) {
			return StepResult.fail("Could not click legal link: " + linkText);
		}

		Page legalPage = waitForNewPage(context, pagesBefore, 8000);
		final boolean openedNewTab = legalPage != null;
		if (!openedNewTab) {
			legalPage = appPage;
		}

		waitForUiLoad(legalPage);
		final boolean headingVisible = isAnyTextVisible(legalPage, headingText);
		final String legalText = normalizeText(legalPage.locator("body").innerText());
		final boolean legalContentVisible = legalText.length() > 120;
		final String finalUrl = legalPage.url();
		final String screenshot = captureScreenshot(legalPage, evidenceDir, screenshotName, true);

		if (openedNewTab && legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else {
			try {
				appPage.goBack();
				waitForUiLoad(appPage);
			} catch (final RuntimeException ignored) {
				// Keep current page when browser history is unavailable.
			}
		}

		final boolean passed = headingVisible && legalContentVisible;
		final String details = "heading=" + headingVisible + ", legal content=" + legalContentVisible + ", new tab="
				+ openedNewTab + ", final URL=" + finalUrl;
		return new StepResult(passed, details, screenshot, finalUrl);
	}

	private BrowserSession openSession(final Playwright playwright, final String loginUrl, final String cdpUrl) {
		if (cdpUrl != null) {
			final Browser browser = playwright.chromium().connectOverCDP(cdpUrl);
			BrowserContext context;
			if (browser.contexts().isEmpty()) {
				context = browser.newContext();
			} else {
				context = browser.contexts().get(0);
			}
			Page page;
			if (context.pages().isEmpty()) {
				page = context.newPage();
			} else {
				page = context.pages().get(context.pages().size() - 1);
			}
			if (loginUrl != null) {
				page.navigate(loginUrl);
			}
			return new BrowserSession(browser, context, page, false);
		}

		final boolean headless = parseHeadlessMode(System.getenv("SALEADS_HEADLESS"));
		final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		final BrowserContext context = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(1920, 1080).setLocale("es-ES"));
		final Page page = context.newPage();
		page.navigate(loginUrl);
		return new BrowserSession(browser, context, page, true);
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).format(LocalDateTime.now());
		final Path path = Path.of("target", "saleads-mi-negocio-evidence", timestamp);
		Files.createDirectories(path);
		return path;
	}

	private Path writeFinalReport(final Path evidenceDir, final LinkedHashMap<String, StepResult> results)
			throws IOException {
		final Path reportPath = evidenceDir.resolve("final-report.json");
		final StringBuilder report = new StringBuilder();
		report.append("{\n");
		report.append("  \"generatedAt\": \"")
				.append(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()))
				.append("\",\n");
		report.append("  \"results\": [\n");
		int i = 0;
		for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
			final StepResult stepResult = entry.getValue();
			report.append("    {\n");
			report.append("      \"step\": \"").append(escapeJson(entry.getKey())).append("\",\n");
			report.append("      \"status\": \"").append(stepResult.passed ? "PASS" : "FAIL").append("\",\n");
			report.append("      \"details\": \"").append(escapeJson(stepResult.details)).append("\",\n");
			report.append("      \"screenshot\": ")
					.append(stepResult.screenshot == null ? "null" : "\"" + escapeJson(stepResult.screenshot) + "\"")
					.append(",\n");
			report.append("      \"url\": ")
					.append(stepResult.url == null ? "null" : "\"" + escapeJson(stepResult.url) + "\"")
					.append("\n");
			report.append("    }");
			if (i < results.size() - 1) {
				report.append(",");
			}
			report.append("\n");
			i++;
		}
		report.append("  ]\n");
		report.append("}\n");

		Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		return reportPath;
	}

	private StepResult runStep(final StepAction action) {
		try {
			return action.execute();
		} catch (final Exception ex) {
			return StepResult.fail("Unexpected error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}
	}

	private boolean clickByVisibleText(final Page page, final String... texts) {
		for (final String text : texts) {
			final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
			if (isVisible(locator, 3000)) {
				try {
					locator.click();
					waitForUiLoad(page);
					return true;
				} catch (final RuntimeException ignored) {
					// Try other text variants.
				}
			}
		}
		return false;
	}

	private void selectGoogleAccountIfVisible(final Page page, final String email) {
		final Locator account = page.getByText(email, new Page.GetByTextOptions().setExact(false)).first();
		if (isVisible(account, 5000)) {
			account.click();
			waitForUiLoad(page);
		}
	}

	private boolean isSidebarVisible(final Page page) {
		if (isVisible(page.locator("aside").first(), 4000)) {
			return true;
		}
		if (isVisible(page.locator("nav").first(), 4000)) {
			return true;
		}
		return isAnyTextVisible(page, "Negocio", "Mi Negocio");
	}

	private boolean isAnyTextVisible(final Page page, final String... texts) {
		for (final String text : texts) {
			final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
			if (isVisible(locator, 3000)) {
				return true;
			}
		}
		return false;
	}

	private Locator findNombreNegocioInput(final Page page) {
		final List<Locator> candidates = Arrays.asList(
				page.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)).first(),
				page.getByPlaceholder("Nombre del Negocio", new Page.GetByPlaceholderOptions().setExact(false)).first(),
				page.locator("input[name*='nombre'], input[id*='nombre']").first());

		for (final Locator candidate : candidates) {
			if (isVisible(candidate, 1500)) {
				return candidate;
			}
		}
		return page.locator("input").first();
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final TimeoutError timeout) {
			return false;
		} catch (final RuntimeException ex) {
			return false;
		}
	}

	private Page waitForNewPage(final BrowserContext context, final int pagesBefore, final int timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			final List<Page> pages = context.pages();
			if (pages.size() > pagesBefore) {
				return pages.get(pages.size() - 1);
			}
			try {
				Thread.sleep(250L);
			} catch (final InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7000));
		} catch (final RuntimeException ignored) {
			try {
				page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(5000));
			} catch (final RuntimeException ignoredAgain) {
				// Keep moving when navigation events are not triggered by UI interaction.
			}
		}
		page.waitForTimeout(350);
	}

	private String captureScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		final Path screenshot = evidenceDir.resolve(fileName + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshot).setFullPage(fullPage));
		return screenshot.toString();
	}

	private boolean parseHeadlessMode(final String configuredValue) {
		if (configuredValue == null) {
			return true;
		}
		return !"false".equalsIgnoreCase(configuredValue.trim());
	}

	private String normalizeText(final String value) {
		return value == null ? "" : value.replace("\u00A0", " ").replace('\r', '\n');
	}

	private boolean containsLikelyUserName(final String text) {
		final String expectedName = trimToNull(System.getenv("SALEADS_EXPECTED_USER_NAME"));
		if (expectedName != null) {
			return containsIgnoreCase(text, expectedName);
		}

		for (final String line : text.split("\\R")) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (containsIgnoreCase(trimmed, "información general") || containsIgnoreCase(trimmed, "business plan")
					|| containsIgnoreCase(trimmed, "cambiar plan") || containsIgnoreCase(trimmed, "cuenta creada")
					|| containsIgnoreCase(trimmed, "estado activo") || containsIgnoreCase(trimmed, "idioma")) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(trimmed).find()) {
				continue;
			}
			if (trimmed.length() >= 4 && trimmed.split("\\s+").length >= 2) {
				return true;
			}
		}
		return false;
	}

	private boolean containsIgnoreCase(final String content, final String expected) {
		return content.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
	}

	private String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String escapeJson(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static class BrowserSession {
		private final Browser browser;
		private final BrowserContext context;
		private final Page appPage;
		private final boolean closeBrowserOnExit;

		private BrowserSession(final Browser browser, final BrowserContext context, final Page appPage,
				final boolean closeBrowserOnExit) {
			this.browser = browser;
			this.context = context;
			this.appPage = appPage;
			this.closeBrowserOnExit = closeBrowserOnExit;
		}
	}

	private static class StepResult {
		private final boolean passed;
		private final String details;
		private final String screenshot;
		private final String url;

		private StepResult(final boolean passed, final String details, final String screenshot, final String url) {
			this.passed = passed;
			this.details = details;
			this.screenshot = screenshot;
			this.url = url;
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details, null, null);
		}
	}

	@FunctionalInterface
	private interface StepAction {
		StepResult execute() throws Exception;
	}
}
