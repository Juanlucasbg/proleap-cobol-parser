package e2e;

import com.microsoft.playwright.Browser;
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
import java.util.stream.Collectors;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String RUN_FLAG = "RUN_SALEADS_E2E";
	private static final String ENTRY_URL_ENV = "SALEADS_ENTRY_URL";
	private static final String GOOGLE_EMAIL_ENV = "SALEADS_GOOGLE_EMAIL";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private BrowserContext context;
	private Page appPage;
	private Path evidenceDirectory;
	private String googleEmail;

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		Assume.assumeTrue(
				"Skipping SaleADS E2E. Set RUN_SALEADS_E2E=true to run this test.",
				Boolean.parseBoolean(getEnvOrDefault(RUN_FLAG, "false")));

		final String entryUrl = getRequiredEnv(ENTRY_URL_ENV);
		googleEmail = getEnvOrDefault(GOOGLE_EMAIL_ENV, DEFAULT_GOOGLE_EMAIL);
		evidenceDirectory = createEvidenceDirectory();

		final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
		final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(
					new BrowserType.LaunchOptions().setHeadless(Boolean.parseBoolean(getEnvOrDefault(HEADLESS_ENV, "true"))));

			context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
			appPage = context.newPage();
			appPage.navigate(entryUrl);
			waitForUiLoad(appPage);

			report.put("Login", runStep(this::stepLoginWithGoogle));
			report.put("Mi Negocio menu", runStep(this::stepOpenMiNegocioMenu));
			report.put("Agregar Negocio modal", runStep(this::stepValidateAgregarNegocioModal));
			report.put("Administrar Negocios view", runStep(this::stepOpenAdministrarNegocios));
			report.put("Información General", runStep(this::stepValidateInformacionGeneral));
			report.put("Detalles de la Cuenta", runStep(this::stepValidateDetallesCuenta));
			report.put("Tus Negocios", runStep(this::stepValidateTusNegocios));
			report.put("Términos y Condiciones", runStep(() -> {
				final String url = stepValidateLegalDocument("Terminos y Condiciones", "Términos y Condiciones", "08-terminos-condiciones.png");
				legalUrls.put("Terminos y Condiciones URL", url);
			}));
			report.put("Política de Privacidad", runStep(() -> {
				final String url = stepValidateLegalDocument("Politica de Privacidad", "Política de Privacidad", "09-politica-privacidad.png");
				legalUrls.put("Politica de Privacidad URL", url);
			}));
		}

		printFinalReport(report, legalUrls);
		final List<String> failedSteps = report.entrySet()
				.stream()
				.filter(entry -> !entry.getValue())
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

		Assert.assertTrue("Failed validations: " + failedSteps, failedSteps.isEmpty());
	}

	private void stepLoginWithGoogle() {
		final int pageCountBeforeClick = context.pages().size();
		clickAnyVisible(appPage, Arrays.asList(
				"button:has-text(\"Sign in with Google\")",
				"button:has-text(\"Iniciar con Google\")",
				"button:has-text(\"Continuar con Google\")",
				"text=\"Sign in with Google\"",
				"text=\"Iniciar con Google\"",
				"text=\"Continuar con Google\""));

		waitForUiLoad(appPage);
		final Page googlePage = waitForNewPage(pageCountBeforeClick, 6000);

		if (googlePage != null) {
			trySelectGoogleAccount(googlePage);
		} else {
			trySelectGoogleAccount(appPage);
		}

		appPage = resolveApplicationPage(15000);
		assertAnyVisible(appPage, "Main interface is not visible", Arrays.asList("main", "div[role=\"main\"]"));
		assertAnyVisible(appPage, "Left sidebar is not visible", Arrays.asList(
				"aside",
				"nav:has-text(\"Negocio\")",
				"text=\"Negocio\""));
		takeScreenshot(appPage, "01-dashboard-loaded.png", true);
	}

	private void stepOpenMiNegocioMenu() {
		assertAnyVisible(appPage, "Left sidebar must be visible before opening menu", Arrays.asList(
				"aside",
				"nav",
				"text=\"Negocio\""));

		clickAnyVisible(appPage, Arrays.asList(
				"text=\"Negocio\"",
				"button:has-text(\"Negocio\")",
				"[role=\"button\"]:has-text(\"Negocio\")"));
		clickAnyVisible(appPage, Arrays.asList(
				"text=\"Mi Negocio\"",
				"button:has-text(\"Mi Negocio\")",
				"a:has-text(\"Mi Negocio\")"));

		assertAnyVisible(appPage, "'Agregar Negocio' is not visible in expanded menu", Arrays.asList(
				"text=\"Agregar Negocio\"",
				"a:has-text(\"Agregar Negocio\")",
				"button:has-text(\"Agregar Negocio\")"));
		assertAnyVisible(appPage, "'Administrar Negocios' is not visible in expanded menu", Arrays.asList(
				"text=\"Administrar Negocios\"",
				"a:has-text(\"Administrar Negocios\")",
				"button:has-text(\"Administrar Negocios\")"));
		takeScreenshot(appPage, "02-mi-negocio-expanded-menu.png", true);
	}

	private void stepValidateAgregarNegocioModal() {
		clickAnyVisible(appPage, Arrays.asList(
				"text=\"Agregar Negocio\"",
				"a:has-text(\"Agregar Negocio\")",
				"button:has-text(\"Agregar Negocio\")"));

		assertAnyVisible(appPage, "Expected modal title 'Crear Nuevo Negocio'", Arrays.asList(
				"text=\"Crear Nuevo Negocio\"",
				"[role=\"dialog\"]:has-text(\"Crear Nuevo Negocio\")"));

		final Locator nombreNegocioInput = assertAnyVisible(appPage,
				"Expected input field 'Nombre del Negocio'",
				Arrays.asList(
						"input[placeholder*=\"Nombre del Negocio\"]",
						"label:has-text(\"Nombre del Negocio\") + input",
						"[role=\"dialog\"] input"));

		assertAnyVisible(appPage, "Expected text 'Tienes 2 de 3 negocios'", Arrays.asList(
				"text=\"Tienes 2 de 3 negocios\"",
				"text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i"));
		assertAnyVisible(appPage, "Expected button 'Cancelar'", Arrays.asList(
				"button:has-text(\"Cancelar\")",
				"text=\"Cancelar\""));
		assertAnyVisible(appPage, "Expected button 'Crear Negocio'", Arrays.asList(
				"button:has-text(\"Crear Negocio\")",
				"text=\"Crear Negocio\""));

		nombreNegocioInput.click();
		nombreNegocioInput.fill("Negocio Prueba Automatizacion");
		takeScreenshot(appPage, "03-agregar-negocio-modal.png", true);

		clickAnyVisible(appPage, Arrays.asList(
				"button:has-text(\"Cancelar\")",
				"text=\"Cancelar\""));
	}

	private void stepOpenAdministrarNegocios() {
		if (!isAnyVisible(appPage, Arrays.asList(
				"text=\"Administrar Negocios\"",
				"a:has-text(\"Administrar Negocios\")",
				"button:has-text(\"Administrar Negocios\")"), 2000)) {
			clickAnyVisible(appPage, Arrays.asList(
					"text=\"Mi Negocio\"",
					"button:has-text(\"Mi Negocio\")",
					"a:has-text(\"Mi Negocio\")"));
		}

		clickAnyVisible(appPage, Arrays.asList(
				"text=\"Administrar Negocios\"",
				"a:has-text(\"Administrar Negocios\")",
				"button:has-text(\"Administrar Negocios\")"));

		assertAnyVisible(appPage, "Section 'Informacion General' must exist", Arrays.asList(
				"text=\"Información General\"",
				"text=\"Informacion General\""));
		assertAnyVisible(appPage, "Section 'Detalles de la Cuenta' must exist", Arrays.asList("text=\"Detalles de la Cuenta\""));
		assertAnyVisible(appPage, "Section 'Tus Negocios' must exist", Arrays.asList("text=\"Tus Negocios\""));
		assertAnyVisible(appPage, "Section 'Seccion Legal' must exist", Arrays.asList(
				"text=\"Sección Legal\"",
				"text=\"Seccion Legal\""));
		takeScreenshot(appPage, "04-administrar-negocios-page.png", true);
	}

	private void stepValidateInformacionGeneral() {
		assertAnyVisible(appPage, "User name should be visible", Arrays.asList(
				"text=/[A-Za-z]+\\s+[A-Za-z]+/",
				"[data-testid*=\"name\"]",
				"[class*=\"name\"]"));
		assertAnyVisible(appPage, "User email should be visible", Arrays.asList(
				"text=\"" + googleEmail + "\"",
				"text=/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+/"));
		assertAnyVisible(appPage, "Text 'BUSINESS PLAN' should be visible", Arrays.asList(
				"text=\"BUSINESS PLAN\"",
				"text=\"Business Plan\""));
		assertAnyVisible(appPage, "Button 'Cambiar Plan' should be visible", Arrays.asList(
				"button:has-text(\"Cambiar Plan\")",
				"text=\"Cambiar Plan\""));
	}

	private void stepValidateDetallesCuenta() {
		assertAnyVisible(appPage, "'Cuenta creada' should be visible", Arrays.asList(
				"text=\"Cuenta creada\"",
				"text=\"Cuenta Creada\""));
		assertAnyVisible(appPage, "'Estado activo' should be visible", Arrays.asList(
				"text=\"Estado activo\"",
				"text=\"Estado Activo\"",
				"text=\"Activo\""));
		assertAnyVisible(appPage, "'Idioma seleccionado' should be visible", Arrays.asList(
				"text=\"Idioma seleccionado\"",
				"text=\"Idioma Seleccionado\""));
	}

	private void stepValidateTusNegocios() {
		assertAnyVisible(appPage, "Business list should be visible", Arrays.asList(
				"text=\"Tus Negocios\"",
				"[class*=\"business\"]",
				"[data-testid*=\"business\"]"));
		assertAnyVisible(appPage, "Button 'Agregar Negocio' should exist", Arrays.asList(
				"button:has-text(\"Agregar Negocio\")",
				"text=\"Agregar Negocio\""));
		assertAnyVisible(appPage, "Text 'Tienes 2 de 3 negocios' should be visible", Arrays.asList(
				"text=\"Tienes 2 de 3 negocios\"",
				"text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i"));
	}

	private String stepValidateLegalDocument(final String normalizedLinkText, final String expectedHeading, final String screenshotName) {
		final int pageCountBeforeClick = context.pages().size();
		clickAnyVisible(appPage, linkSelectors(normalizedLinkText));
		waitForUiLoad(appPage);

		final Page possibleNewTab = waitForNewPage(pageCountBeforeClick, 5000);
		final boolean openedInNewTab = possibleNewTab != null;
		final Page legalPage = openedInNewTab ? possibleNewTab : appPage;

		legalPage.bringToFront();
		waitForUiLoad(legalPage);

		assertAnyVisible(legalPage, "Expected legal heading: " + expectedHeading, Arrays.asList(
				"text=\"" + expectedHeading + "\"",
				"h1:has-text(\"" + expectedHeading + "\")",
				"h2:has-text(\"" + expectedHeading + "\")"));
		assertLegalContentVisible(legalPage);
		takeScreenshot(legalPage, screenshotName, true);

		final String finalUrl = legalPage.url();

		if (openedInNewTab) {
			legalPage.close();
			appPage.bringToFront();
		} else {
			appPage.goBack();
			waitForUiLoad(appPage);
		}

		return finalUrl;
	}

	private List<String> linkSelectors(final String normalizedText) {
		if ("Terminos y Condiciones".equals(normalizedText)) {
			return Arrays.asList(
					"text=\"Términos y Condiciones\"",
					"text=\"Terminos y Condiciones\"",
					"a:has-text(\"Términos y Condiciones\")",
					"a:has-text(\"Terminos y Condiciones\")");
		}

		return Arrays.asList(
				"text=\"Política de Privacidad\"",
				"text=\"Politica de Privacidad\"",
				"a:has-text(\"Política de Privacidad\")",
				"a:has-text(\"Politica de Privacidad\")");
	}

	private void assertLegalContentVisible(final Page page) {
		final String content = page.locator("main, article, body").first().innerText();
		Assert.assertTrue("Legal content should be visible", content != null && content.trim().length() > 80);
	}

	private boolean runStep(final StepAction action) {
		try {
			action.run();
			return true;
		} catch (Throwable throwable) {
			System.err.println("Step failed: " + throwable.getMessage());
			return false;
		}
	}

	private void clickAnyVisible(final Page page, final List<String> selectors) {
		final Locator locator = assertAnyVisible(page, "Could not find clickable element: " + selectors, selectors);
		locator.scrollIntoViewIfNeeded();
		locator.click();
		waitForUiLoad(page);
	}

	private Locator assertAnyVisible(final Page page, final String message, final List<String> selectors) {
		final Locator visible = findAnyVisible(page, selectors, 12000);
		Assert.assertNotNull(message, visible);
		return visible;
	}

	private boolean isAnyVisible(final Page page, final List<String> selectors, final double timeoutMs) {
		return findAnyVisible(page, selectors, timeoutMs) != null;
	}

	private Locator findAnyVisible(final Page page, final List<String> selectors, final double timeoutMs) {
		for (final String selector : selectors) {
			final Locator candidate = page.locator(selector).first();
			try {
				candidate.waitFor(
						new Locator.WaitForOptions()
								.setState(WaitForSelectorState.VISIBLE)
								.setTimeout(timeoutMs));
				return candidate;
			} catch (PlaywrightException ignored) {
				// Continue checking next selector.
			}
		}

		return null;
	}

	private void trySelectGoogleAccount(final Page page) {
		final Locator accountLocator = findAnyVisible(page, Arrays.asList(
				"div[data-identifier=\"" + googleEmail + "\"]",
				"text=\"" + googleEmail + "\"",
				"button:has-text(\"" + googleEmail + "\")"), 5000);
		if (accountLocator != null) {
			accountLocator.click();
			waitForUiLoad(page);
		}
	}

	private Page waitForNewPage(final int previousPageCount, final int timeoutMs) {
		final long timeoutAt = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < timeoutAt) {
			final List<Page> pages = context.pages();
			if (pages.size() > previousPageCount) {
				return pages.get(pages.size() - 1);
			}
			appPage.waitForTimeout(250);
		}
		return null;
	}

	private Page resolveApplicationPage(final int timeoutMs) {
		final long timeoutAt = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < timeoutAt) {
			final List<Page> pages = new ArrayList<>(context.pages());
			for (final Page page : pages) {
				page.bringToFront();
				waitForUiLoad(page);
				if (isAnyVisible(page, Arrays.asList(
						"text=\"Negocio\"",
						"aside",
						"text=\"Mi Negocio\""), 2000)) {
					return page;
				}
			}
			appPage.waitForTimeout(400);
		}

		return appPage;
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (PlaywrightException ignored) {
			// Some pages keep long-polling connections; DOM ready is enough.
		}
		page.waitForTimeout(500);
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(evidenceDirectory.resolve(fileName))
				.setFullPage(fullPage));
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(path);
		return path;
	}

	private void printFinalReport(final LinkedHashMap<String, Boolean> report, final LinkedHashMap<String, String> legalUrls) {
		System.out.println("=== SaleADS Mi Negocio Full Test Report ===");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.printf("%s: %s%n", entry.getKey(), entry.getValue() ? "PASS" : "FAIL");
		}
		for (Map.Entry<String, String> legalUrlEntry : legalUrls.entrySet()) {
			System.out.printf("%s: %s%n", legalUrlEntry.getKey(), legalUrlEntry.getValue());
		}
		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
	}

	private String getRequiredEnv(final String name) {
		final String value = System.getenv(name);
		Assert.assertTrue("Required environment variable not set: " + name, value != null && !value.trim().isEmpty());
		return value.trim();
	}

	private String getEnvOrDefault(final String name, final String defaultValue) {
		final String value = System.getenv(name);
		return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
	}

	@FunctionalInterface
	private interface StepAction {
		void run();
	}
}
