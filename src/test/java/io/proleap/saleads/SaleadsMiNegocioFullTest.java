package io.proleap.saleads;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assert;
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

	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-evidence");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		Files.createDirectories(EVIDENCE_DIR);

		final String loginUrl = resolveLoginUrl();
		final String expectedUserName = readOptionalEnv("SALEADS_EXPECTED_USER_NAME");
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));

		final Map<String, Boolean> report = new LinkedHashMap<>();
		final Map<String, String> failures = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);

		if (loginUrl == null || loginUrl.isBlank()) {
			Assert.fail("Set SALEADS_LOGIN_URL or SALEADS_BASE_URL to run the workflow in any environment.");
		}

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 900));
			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(15000);

			appPage.navigate(loginUrl);
			waitForUi(appPage);

			report.put("Login", runStep("Login", failures, () -> {
				clickFirstVisible(appPage,
						"Sign in with Google",
						"Continuar con Google",
						"Iniciar sesión con Google",
						"Login with Google",
						"Iniciar sesión",
						"Login");
				waitForUi(appPage);

				// Optional: Google account chooser
				if (isTextVisible(appPage, "juanlucasbarbiergarzon@gmail.com")) {
					clickByVisibleText(appPage, "juanlucasbarbiergarzon@gmail.com");
					waitForUi(appPage);
				}

				final boolean mainInterfaceVisible = isAnyTextVisible(appPage, "Dashboard", "Panel", "Negocio", "Mi Negocio");
				final boolean leftSidebarVisible = isSidebarVisible(appPage);
				screenshot(appPage, "01-dashboard-loaded.png", true);
				return mainInterfaceVisible && leftSidebarVisible;
			}));

			report.put("Mi Negocio menu", runStep("Mi Negocio menu", failures, () -> {
				clickByVisibleText(appPage, "Negocio");
				waitForUi(appPage);
				clickByVisibleText(appPage, "Mi Negocio");
				waitForUi(appPage);

				final boolean submenuExpanded = isAnyTextVisible(appPage, "Agregar Negocio", "Administrar Negocios");
				final boolean agregarVisible = isTextVisible(appPage, "Agregar Negocio");
				final boolean administrarVisible = isTextVisible(appPage, "Administrar Negocios");
				screenshot(appPage, "02-mi-negocio-menu-expanded.png", true);
				return submenuExpanded && agregarVisible && administrarVisible;
			}));

			report.put("Agregar Negocio modal", runStep("Agregar Negocio modal", failures, () -> {
				clickByVisibleText(appPage, "Agregar Negocio");
				waitForUi(appPage);

				final boolean titleVisible = isTextVisible(appPage, "Crear Nuevo Negocio");
				final boolean fieldVisible = isTextVisible(appPage, "Nombre del Negocio");
				final boolean quotaVisible = isTextVisible(appPage, "Tienes 2 de 3 negocios");
				final boolean cancelVisible = isTextVisible(appPage, "Cancelar");
				final boolean createVisible = isTextVisible(appPage, "Crear Negocio");

				screenshot(appPage, "03-agregar-negocio-modal.png", true);

				// Optional action required in prompt.
				if (fieldVisible) {
					fillByLabel(appPage, "Nombre del Negocio", "Negocio Prueba Automatización");
					waitForUi(appPage);
				}
				if (cancelVisible) {
					clickByVisibleText(appPage, "Cancelar");
					waitForUi(appPage);
				}

				return titleVisible && fieldVisible && quotaVisible && cancelVisible && createVisible;
			}));

			report.put("Administrar Negocios view", runStep("Administrar Negocios view", failures, () -> {
				if (!isTextVisible(appPage, "Administrar Negocios")) {
					clickByVisibleText(appPage, "Mi Negocio");
					waitForUi(appPage);
				}

				clickByVisibleText(appPage, "Administrar Negocios");
				waitForUi(appPage);

				final boolean infoGeneral = isTextVisible(appPage, "Información General");
				final boolean detallesCuenta = isTextVisible(appPage, "Detalles de la Cuenta");
				final boolean tusNegocios = isTextVisible(appPage, "Tus Negocios");
				final boolean seccionLegal = isTextVisible(appPage, "Sección Legal");

				screenshot(appPage, "04-administrar-negocios-cuenta.png", true);
				return infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
			}));

			report.put("Información General", runStep("Información General", failures, () -> {
				final String pageText = bodyText(appPage);
				final boolean userNameVisible = expectedUserName == null
						? hasNameLineNearEmail(pageText)
						: containsIgnoreCase(pageText, expectedUserName);
				final boolean userEmailVisible = hasAnyEmail(pageText);
				final boolean businessPlanVisible = isTextVisible(appPage, "BUSINESS PLAN");
				final boolean cambiarPlanVisible = isTextVisible(appPage, "Cambiar Plan");
				return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
			}));

			report.put("Detalles de la Cuenta", runStep("Detalles de la Cuenta", failures, () -> {
				final boolean cuentaCreada = isTextVisible(appPage, "Cuenta creada");
				final boolean estadoActivo = isTextVisible(appPage, "Estado activo");
				final boolean idiomaSeleccionado = isTextVisible(appPage, "Idioma seleccionado");
				return cuentaCreada && estadoActivo && idiomaSeleccionado;
			}));

			report.put("Tus Negocios", runStep("Tus Negocios", failures, () -> {
				final boolean businessListVisible = isTextVisible(appPage, "Tus Negocios");
				final boolean addBusinessVisible = isTextVisible(appPage, "Agregar Negocio");
				final boolean quotaVisible = isTextVisible(appPage, "Tienes 2 de 3 negocios");
				return businessListVisible && addBusinessVisible && quotaVisible;
			}));

			report.put("Términos y Condiciones", runStep("Términos y Condiciones", failures, () -> {
				LegalResult legalResult = openAndValidateLegalLink(context, appPage, "Términos y Condiciones",
						"05-terminos-y-condiciones.png");
				System.out.println("TERMINOS_URL=" + legalResult.url);
				return legalResult.valid;
			}));

			report.put("Política de Privacidad", runStep("Política de Privacidad", failures, () -> {
				LegalResult legalResult = openAndValidateLegalLink(context, appPage, "Política de Privacidad",
						"06-politica-de-privacidad.png");
				System.out.println("POLITICA_PRIVACIDAD_URL=" + legalResult.url);
				return legalResult.valid;
			}));
		}

		final List<String> failedSteps = new ArrayList<>();
		System.out.println("---- SALEADS MI NEGOCIO FINAL REPORT ----");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			final String status = entry.getValue() ? "PASS" : "FAIL";
			System.out.println(entry.getKey() + ": " + status);
			if (!entry.getValue()) {
				failedSteps.add(entry.getKey());
			}
		}

		if (!failures.isEmpty()) {
			System.out.println("---- STEP ERRORS ----");
			for (Map.Entry<String, String> failure : failures.entrySet()) {
				System.out.println(failure.getKey() + " -> " + failure.getValue());
			}
		}

		Assert.assertTrue("Failed validations: " + failedSteps, failedSteps.isEmpty());
	}

	private boolean runStep(String stepName, Map<String, String> failures, StepAction stepAction) {
		try {
			return stepAction.run();
		} catch (Exception e) {
			failures.put(stepName, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
			return false;
		}
	}

	private void clickFirstVisible(Page page, String... labels) {
		for (String label : labels) {
			if (isTextVisible(page, label)) {
				clickByVisibleText(page, label);
				return;
			}
		}
		throw new AssertionError("No clickable element found for labels: " + String.join(", ", labels));
	}

	private void clickByVisibleText(Page page, String text) {
		List<Locator> candidates = List.of(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)),
				page.getByText(text, new Page.GetByTextOptions().setExact(true)),
				page.getByText(text));

		for (Locator candidate : candidates) {
			try {
				if (candidate.count() > 0 && candidate.first().isVisible()) {
					candidate.first().click();
					return;
				}
			} catch (PlaywrightException ignored) {
				// Continue with next candidate.
			}
		}

		throw new AssertionError("Unable to click visible text: " + text);
	}

	private void fillByLabel(Page page, String label, String value) {
		Locator input = page.getByLabel(label);
		if (input.count() > 0 && input.first().isVisible()) {
			input.first().fill(value);
			return;
		}

		Locator fallback = page.getByPlaceholder(label);
		if (fallback.count() > 0 && fallback.first().isVisible()) {
			fallback.first().fill(value);
			return;
		}

		throw new AssertionError("Unable to fill input for label: " + label);
	}

	private LegalResult openAndValidateLegalLink(BrowserContext context, Page appPage, String linkText, String screenshotName) {
		final int pagesBefore = context.pages().size();
		clickByVisibleText(appPage, linkText);
		waitForUi(appPage);
		appPage.waitForTimeout(1500);

		Page legalPage = appPage;
		if (context.pages().size() > pagesBefore) {
			legalPage = context.pages().get(context.pages().size() - 1);
			legalPage.bringToFront();
			waitForUi(legalPage);
		}

		final boolean headingVisible = isTextVisible(legalPage, linkText);
		final boolean contentVisible = bodyText(legalPage).trim().length() > 250;
		screenshot(legalPage, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return new LegalResult(headingVisible && contentVisible, finalUrl);
	}

	private boolean isSidebarVisible(Page page) {
		try {
			Locator sidebar = page.locator("aside, nav").first();
			return sidebar.count() > 0 && sidebar.isVisible();
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private boolean isAnyTextVisible(Page page, String... values) {
		for (String value : values) {
			if (isTextVisible(page, value)) {
				return true;
			}
		}
		return false;
	}

	private boolean isTextVisible(Page page, String text) {
		try {
			Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true));
			if (exact.count() > 0 && exact.first().isVisible(new Locator.IsVisibleOptions().setTimeout(1200))) {
				return true;
			}
		} catch (PlaywrightException ignored) {
			// Continue with partial match fallback.
		}

		try {
			Locator partial = page.getByText(text);
			return partial.count() > 0 && partial.first().isVisible(new Locator.IsVisibleOptions().setTimeout(1200));
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void waitForUi(Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (PlaywrightException ignored) {
			// Some SPAs keep long-running network traffic; DOM content loaded is enough.
		}
		page.waitForTimeout(400);
	}

	private void screenshot(Page page, String fileName, boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(EVIDENCE_DIR.resolve(fileName)).setFullPage(fullPage));
	}

	private String bodyText(Page page) {
		Object value = page.evaluate("() => document.body ? document.body.innerText : ''");
		return value == null ? "" : value.toString();
	}

	private boolean hasAnyEmail(String content) {
		return Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(content).find();
	}

	private boolean hasNameLineNearEmail(String content) {
		String[] lines = content.split("\\R");
		for (int i = 0; i < lines.length; i++) {
			String current = lines[i].trim();
			if (!current.contains("@")) {
				continue;
			}

			for (int j = i - 1; j >= 0; j--) {
				String possibleName = lines[j].trim();
				if (possibleName.isEmpty()) {
					continue;
				}
				if (!possibleName.contains("@") && possibleName.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
					return true;
				}
				break;
			}
		}
		return false;
	}

	private boolean containsIgnoreCase(String content, String value) {
		return content.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
	}

	private String resolveLoginUrl() {
		String explicit = readOptionalEnv("SALEADS_LOGIN_URL");
		if (explicit != null && !explicit.isBlank()) {
			return explicit;
		}

		String base = readOptionalEnv("SALEADS_BASE_URL");
		if (base == null || base.isBlank()) {
			return null;
		}

		String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		return normalizedBase + "/login";
	}

	private String readOptionalEnv(String key) {
		String value = System.getenv(key);
		return value == null || value.isBlank() ? null : value;
	}

	@FunctionalInterface
	private interface StepAction {
		boolean run() throws Exception;
	}

	private static class LegalResult {
		private final boolean valid;
		private final String url;

		private LegalResult(boolean valid, String url) {
			this.valid = valid;
			this.url = url;
		}
	}
}
