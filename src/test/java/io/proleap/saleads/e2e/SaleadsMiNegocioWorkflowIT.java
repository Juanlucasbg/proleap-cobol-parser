package io.proleap.saleads.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
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
import org.junit.Assert;
import org.junit.Test;

public class SaleadsMiNegocioWorkflowIT {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private interface StepAction {
		void run() throws Exception;
	}

	@Test
	public void validateMiNegocioWorkflow() throws Exception {
		final String appUrl = firstNonBlank(System.getProperty("saleads.url"), System.getenv("SALEADS_URL"));
		Assert.assertNotNull(
				"Missing SaleADS login URL. Provide -Dsaleads.url=<login-page-url> or SALEADS_URL env var.",
				appUrl);

		final Path evidenceDir = createEvidenceDirectory();
		final LinkedHashMap<String, Boolean> report = initializeReport();
		final List<String> errors = new ArrayList<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		Path reportPath = evidenceDir.resolve("final-report.txt");

		try (Playwright playwright = Playwright.create()) {
			final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(
					new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page appPage = context.newPage();

			appPage.navigate(appUrl);
			waitForUi(appPage);

			runStep("Login", report, errors, () -> runLoginStep(appPage, context, evidenceDir));
			runStep("Mi Negocio menu", report, errors, () -> runMiNegocioMenuStep(appPage, evidenceDir));
			runStep("Agregar Negocio modal", report, errors, () -> runAgregarNegocioModalStep(appPage, evidenceDir));
			runStep("Administrar Negocios view", report, errors,
					() -> runAdministrarNegociosStep(appPage, evidenceDir));
			runStep("Informacion General", report, errors, () -> runInformacionGeneralStep(appPage));
			runStep("Detalles de la Cuenta", report, errors, () -> runDetallesCuentaStep(appPage));
			runStep("Tus Negocios", report, errors, () -> runTusNegociosStep(appPage));
			runStep("Terminos y Condiciones", report, errors, () -> {
				final String url = runLegalLinkStep(appPage, "T\u00e9rminos y Condiciones",
						"text=/T[e\u00e9]rminos y Condiciones/i", "08-terminos-y-condiciones", evidenceDir);
				legalUrls.put("Terminos y Condiciones", url);
			});
			runStep("Politica de Privacidad", report, errors, () -> {
				final String url = runLegalLinkStep(appPage, "Pol\u00edtica de Privacidad",
						"text=/Pol[i\u00ed]tica de Privacidad/i", "09-politica-de-privacidad", evidenceDir);
				legalUrls.put("Politica de Privacidad", url);
			});

			reportPath = writeFinalReport(evidenceDir, report, errors, legalUrls);
			System.out.println("SaleADS evidence saved at: " + evidenceDir.toAbsolutePath());
			System.out.println("SaleADS final report: " + reportPath.toAbsolutePath());
		}

		final boolean allPassed = report.values().stream().allMatch(Boolean.TRUE::equals);
		Assert.assertTrue("One or more SaleADS workflow validations failed. See: " + reportPath.toAbsolutePath(),
				allPassed);
	}

	private void runLoginStep(final Page appPage, final BrowserContext context, final Path evidenceDir) {
		final Locator loginButton = firstVisible("Google login button", Arrays.asList(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in with Google")),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Continuar con Google")),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Iniciar con Google")),
				appPage.locator("button:has-text('Google')"),
				appPage.locator("text=Sign in with Google"),
				appPage.locator("text=Continuar con Google"),
				appPage.locator("text=Iniciar con Google")));

		final int pagesBefore = context.pages().size();
		clickAndWait(appPage, loginButton);

		final Page popup = waitForNewPage(context, pagesBefore, appPage);
		final Page authPage = popup == null ? appPage : popup;
		waitForUi(authPage);

		final Locator accountLocator = authPage.locator("text=" + GOOGLE_ACCOUNT_EMAIL);
		if (hasVisible(accountLocator)) {
			clickAndWait(authPage, accountLocator.first());
		}

		if (popup != null) {
			waitForPopupToCloseOrStabilize(popup);
			appPage.bringToFront();
		}
		waitForUi(appPage);

		assertVisible("Main application interface should appear",
				firstVisible("main application interface",
						Arrays.asList(appPage.locator("main"), appPage.locator("aside"),
								appPage.locator("text=/Dashboard|Panel|Inicio/i"))));
		assertVisible("Left sidebar navigation should be visible",
				firstVisible("left sidebar",
						Arrays.asList(appPage.locator("aside"), appPage.locator("[class*='sidebar']"),
								appPage.getByRole(AriaRole.NAVIGATION))));
		takeScreenshot(appPage, evidenceDir, "01-dashboard-loaded", true);
	}

	private void runMiNegocioMenuStep(final Page appPage, final Path evidenceDir) {
		if (hasVisible(appPage.locator("text=Negocio"))) {
			clickAndWait(appPage, appPage.locator("text=Negocio").first());
		}

		final Locator miNegocio = firstVisible("Mi Negocio option", Arrays.asList(
				appPage.locator("text=Mi Negocio"), appPage.locator("text=Mi negocio"),
				appPage.locator("text=MI NEGOCIO")));
		clickAndWait(appPage, miNegocio);

		assertVisible("Agregar Negocio should be visible", firstVisible("Agregar Negocio",
				Arrays.asList(appPage.locator("text=Agregar Negocio"), appPage.locator("text=Agregar negocio"))));
		assertVisible("Administrar Negocios should be visible", firstVisible("Administrar Negocios",
				Arrays.asList(appPage.locator("text=Administrar Negocios"),
						appPage.locator("text=Administrar negocios"))));
		takeScreenshot(appPage, evidenceDir, "02-mi-negocio-menu-expanded", true);
	}

	private void runAgregarNegocioModalStep(final Page appPage, final Path evidenceDir) {
		final Locator agregarNegocio = firstVisible("Agregar Negocio action", Arrays.asList(
				appPage.locator("text=Agregar Negocio"), appPage.locator("text=Agregar negocio")));
		clickAndWait(appPage, agregarNegocio);

		final Locator modalTitle = firstVisible("Crear Nuevo Negocio modal title",
				Arrays.asList(appPage.locator("text=Crear Nuevo Negocio"), appPage.locator("text=Crear nuevo negocio")));
		assertVisible("Modal title should be visible", modalTitle);
		assertVisible("Nombre del Negocio input should exist", firstVisible("Nombre del Negocio input",
				Arrays.asList(appPage.getByLabel("Nombre del Negocio"), appPage.getByPlaceholder("Nombre del Negocio"),
						appPage.locator("input[name='businessName']"), appPage.locator("input[type='text']"))));
		assertVisible("Business quota text should be visible",
				firstVisible("Tienes 2 de 3 negocios text",
						Arrays.asList(appPage.locator("text=Tienes 2 de 3 negocios"),
								appPage.locator("text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i"))));
		assertVisible("Cancelar button should be visible",
				firstVisible("Cancelar button", Arrays.asList(appPage.locator("text=Cancelar"),
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")))));
		assertVisible("Crear Negocio button should be visible", firstVisible("Crear Negocio button",
				Arrays.asList(appPage.locator("text=Crear Negocio"),
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")))));

		takeScreenshot(appPage, evidenceDir, "03-agregar-negocio-modal", true);

		final Locator businessNameInput = firstVisible("Nombre del Negocio input for optional typing",
				Arrays.asList(appPage.getByLabel("Nombre del Negocio"), appPage.getByPlaceholder("Nombre del Negocio"),
						appPage.locator("input[name='businessName']"), appPage.locator("input[type='text']")));
		businessNameInput.fill("Negocio Prueba Automatizacion");
		clickAndWait(appPage, firstVisible("Cancelar button for modal close", Arrays.asList(appPage.locator("text=Cancelar"),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")))));
	}

	private void runAdministrarNegociosStep(final Page appPage, final Path evidenceDir) {
		if (!hasVisible(appPage.locator("text=Administrar Negocios"))) {
			final Locator miNegocio = firstVisible("Mi Negocio re-open option", Arrays.asList(
					appPage.locator("text=Mi Negocio"), appPage.locator("text=Mi negocio")));
			clickAndWait(appPage, miNegocio);
		}

		final Locator administrarNegocios = firstVisible("Administrar Negocios option", Arrays.asList(
				appPage.locator("text=Administrar Negocios"), appPage.locator("text=Administrar negocios")));
		clickAndWait(appPage, administrarNegocios);

		assertVisible("Informacion General section should exist",
				firstVisible("Informacion General section",
						Arrays.asList(appPage.locator("text=/Informaci[o\u00f3]n General/i"))));
		assertVisible("Detalles de la Cuenta section should exist",
				firstVisible("Detalles de la Cuenta section",
						Arrays.asList(appPage.locator("text=/Detalles de la Cuenta/i"))));
		assertVisible("Tus Negocios section should exist",
				firstVisible("Tus Negocios section", Arrays.asList(appPage.locator("text=/Tus Negocios/i"))));
		assertVisible("Seccion Legal section should exist", firstVisible("Seccion Legal section",
				Arrays.asList(appPage.locator("text=/Secci[o\u00f3]n Legal/i"), appPage.locator("text=Seccion Legal"))));

		takeScreenshot(appPage, evidenceDir, "04-administrar-negocios-account-page", true);
	}

	private void runInformacionGeneralStep(final Page appPage) {
		assertVisible("A user email should be visible",
				firstVisible("email text", Arrays.asList(
						appPage.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/"))));
		assertVisible("A user name-like text should be visible",
				firstVisible("name-like text", Arrays.asList(appPage.locator("text=/[A-Za-z]{2,}\\s+[A-Za-z]{2,}/"))));
		assertVisible("BUSINESS PLAN text should be visible",
				firstVisible("BUSINESS PLAN", Arrays.asList(appPage.locator("text=BUSINESS PLAN"))));
		assertVisible("Cambiar Plan button should be visible", firstVisible("Cambiar Plan button",
				Arrays.asList(appPage.locator("text=Cambiar Plan"),
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")))));
	}

	private void runDetallesCuentaStep(final Page appPage) {
		assertVisible("Cuenta creada should be visible",
				firstVisible("Cuenta creada text", Arrays.asList(appPage.locator("text=/Cuenta creada/i"))));
		assertVisible("Estado activo should be visible", firstVisible("Estado activo text",
				Arrays.asList(appPage.locator("text=/Estado\\s+activo/i"), appPage.locator("text=/Estado:?.*Activo/i"))));
		assertVisible("Idioma seleccionado should be visible", firstVisible("Idioma seleccionado text",
				Arrays.asList(appPage.locator("text=/Idioma seleccionado/i"))));
	}

	private void runTusNegociosStep(final Page appPage) {
		assertVisible("Business list should be visible",
				firstVisible("business list section", Arrays.asList(appPage.locator("text=/Tus Negocios/i"),
						appPage.locator("table"), appPage.locator("[class*='business']"))));
		assertVisible("Agregar Negocio button should exist", firstVisible("Agregar Negocio button in business section",
				Arrays.asList(appPage.locator("text=Agregar Negocio"), appPage.locator("text=Agregar negocio"),
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")))));
		assertVisible("Tienes 2 de 3 negocios should be visible",
				firstVisible("Tienes 2 de 3 negocios text",
						Arrays.asList(appPage.locator("text=Tienes 2 de 3 negocios"),
								appPage.locator("text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i"))));
	}

	private String runLegalLinkStep(final Page appPage, final String linkText, final String headingLocator,
			final String screenshotName, final Path evidenceDir) {
		final BrowserContext context = appPage.context();
		final String originalUrl = appPage.url();
		final int pagesBefore = context.pages().size();

		final Locator legalLink = firstVisible(linkText + " link", Arrays.asList(
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkText)),
				appPage.locator("text=" + linkText)));
		clickAndWait(appPage, legalLink);

		Page targetPage = waitForNewPage(context, pagesBefore, appPage);
		final boolean popupOpened = targetPage != null;

		if (targetPage == null) {
			targetPage = appPage;
			waitForUrlChangeIfNeeded(appPage, originalUrl);
		}

		waitForUi(targetPage);
		assertVisible("Legal heading should be visible",
				firstVisible("legal heading", Arrays.asList(targetPage.locator(headingLocator))));
		assertVisible("Legal content should be visible",
				firstVisible("legal content", Arrays.asList(targetPage.locator("article p"), targetPage.locator("main p"),
						targetPage.locator("section p"), targetPage.locator("p"), targetPage.locator("text=Pol\u00edtica"),
						targetPage.locator("text=T\u00e9rminos"), targetPage.locator("text=privacidad"))));
		takeScreenshot(targetPage, evidenceDir, screenshotName, true);
		final String finalUrl = targetPage.url();

		if (popupOpened) {
			try {
				targetPage.close();
			} catch (final PlaywrightException ignored) {
			}
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!originalUrl.equals(appPage.url())) {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void runStep(final String stepName, final Map<String, Boolean> report, final List<String> errors,
			final StepAction stepAction) {
		try {
			stepAction.run();
			report.put(stepName, true);
		} catch (final Throwable throwable) {
			report.put(stepName, false);
			errors.add(stepName + " -> " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
		}
	}

	private void clickAndWait(final Page page, final Locator locator) {
		assertVisible("Element must be visible before click", locator);
		locator.scrollIntoViewIfNeeded();
		locator.click(new Locator.ClickOptions().setTimeout(15000));
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (final PlaywrightException ignored) {
		}
		page.waitForTimeout(500);
	}

	private void waitForUrlChangeIfNeeded(final Page page, final String previousUrl) {
		for (int i = 0; i < 12; i++) {
			if (!previousUrl.equals(page.url())) {
				return;
			}
			page.waitForTimeout(500);
		}
	}

	private Page waitForNewPage(final BrowserContext context, final int pagesBefore, final Page currentPage) {
		for (int i = 0; i < 14; i++) {
			final List<Page> pages = context.pages();
			if (pages.size() > pagesBefore) {
				for (final Page page : pages) {
					if (page != currentPage) {
						return page;
					}
				}
			}
			currentPage.waitForTimeout(500);
		}
		return null;
	}

	private void waitForPopupToCloseOrStabilize(final Page popup) {
		for (int i = 0; i < 12; i++) {
			if (popup.isClosed()) {
				return;
			}
			try {
				popup.waitForTimeout(500);
			} catch (final PlaywrightException ignored) {
				return;
			}
		}
	}

	private Locator firstVisible(final String description, final List<Locator> candidates) {
		for (final Locator candidate : candidates) {
			try {
				final int count = candidate.count();
				final int cap = Math.min(count, 5);
				for (int i = 0; i < cap; i++) {
					final Locator nth = candidate.nth(i);
					if (hasVisible(nth)) {
						return nth;
					}
				}
			} catch (final PlaywrightException ignored) {
			}
		}
		throw new AssertionError("Unable to find visible element: " + description);
	}

	private boolean hasVisible(final Locator locator) {
		try {
			return locator.isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void assertVisible(final String message, final Locator locator) {
		Assert.assertTrue(message, hasVisible(locator));
	}

	private void takeScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		try {
			Files.createDirectories(evidenceDir);
			page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName + ".png")).setFullPage(fullPage));
		} catch (final IOException exception) {
			throw new RuntimeException("Unable to save screenshot: " + fileName, exception);
		}
	}

	private Path createEvidenceDirectory() {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence", timestamp);
		try {
			Files.createDirectories(evidenceDir);
		} catch (final IOException exception) {
			throw new RuntimeException("Unable to create evidence directory", exception);
		}
		return evidenceDir;
	}

	private LinkedHashMap<String, Boolean> initializeReport() {
		final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Informacion General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Terminos y Condiciones", false);
		report.put("Politica de Privacidad", false);
		return report;
	}

	private Path writeFinalReport(final Path evidenceDir, final Map<String, Boolean> report, final List<String> errors,
			final Map<String, String> legalUrls) {
		final Path reportPath = evidenceDir.resolve("final-report.txt");
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio workflow final report").append('\n');
		builder.append("Generated at: ").append(LocalDateTime.now()).append('\n').append('\n');
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			builder.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append('\n');
		}
		builder.append('\n');
		builder.append("Legal URLs").append('\n');
		builder.append("Terminos y Condiciones URL: ")
				.append(legalUrls.getOrDefault("Terminos y Condiciones", "N/A"))
				.append('\n');
		builder.append("Politica de Privacidad URL: ")
				.append(legalUrls.getOrDefault("Politica de Privacidad", "N/A"))
				.append('\n');
		builder.append('\n');
		builder.append("Errors").append('\n');
		if (errors.isEmpty()) {
			builder.append("None").append('\n');
		} else {
			for (final String error : errors) {
				builder.append("- ").append(error).append('\n');
			}
		}
		try {
			Files.writeString(reportPath, builder.toString(), StandardCharsets.UTF_8);
		} catch (final IOException exception) {
			throw new RuntimeException("Unable to write final report", exception);
		}
		return reportPath;
	}

	private String firstNonBlank(final String first, final String second) {
		if (first != null && !first.trim().isEmpty()) {
			return first.trim();
		}
		if (second != null && !second.trim().isEmpty()) {
			return second.trim();
		}
		return null;
	}
}
