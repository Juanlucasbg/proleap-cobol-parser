package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * End-to-end coverage for SaleADS "Mi Negocio" workflow.
 *
 * Runtime configuration (all optional):
 * - SALEADS_START_URL: login page URL for the target environment.
 * - SALEADS_HEADLESS: true/false (default true).
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final int TIMEOUT_MS = 15_000;
	private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private final Path runEvidenceDir = Paths.get("target", "saleads-evidence", RUN_ID_FORMAT.format(LocalDateTime.now()));

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		Files.createDirectories(runEvidenceDir);

		try (Playwright playwright = Playwright.create()) {
			final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(headless);
			final Browser browser = playwright.chromium().launch(launchOptions);
			final BrowserContext context = browser.newContext();
			context.setDefaultTimeout(TIMEOUT_MS);

			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(TIMEOUT_MS);

			final String startUrl = trimToNull(System.getenv("SALEADS_START_URL"));
			if (startUrl != null) {
				appPage.navigate(startUrl);
			}
			waitForUi(appPage);

			runStep("Login", () -> {
				final Locator loginButton = firstVisible("login/sign in button", appPage.getByText("Sign in with Google"),
						appPage.getByText("Iniciar con Google"), appPage.getByText("Login"),
						appPage.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
								new Page.GetByRoleOptions().setName("Sign in with Google")),
						appPage.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
								new Page.GetByRoleOptions().setName("Iniciar con Google")));

				Page authPage = null;
				try {
					authPage = context.waitForPage(() -> loginButton.click());
				} catch (final PlaywrightException ignored) {
					loginButton.click();
				}

				final Page googlePage = authPage != null ? authPage : appPage;
				waitForUi(googlePage);
				clickIfVisible(googlePage.getByText(GOOGLE_ACCOUNT_EMAIL), googlePage);

				// If an account chooser popup was opened, return to the app tab.
				if (authPage != null) {
					waitForUi(appPage);
					appPage.bringToFront();
				}

				firstVisible("main interface", appPage.locator("aside"), appPage.locator("nav"), appPage.getByText("Negocio"));
				firstVisible("left sidebar", appPage.getByText("Negocio"), appPage.locator("aside"));
				screenshot(appPage, "01-dashboard-loaded.png", false);
			});

			runStep("Mi Negocio menu", () -> {
				clickIfVisible(firstVisible("Negocio section", appPage.getByText("Negocio")), appPage);
				clickIfVisible(firstVisible("Mi Negocio option", appPage.getByText("Mi Negocio")), appPage);

				firstVisible("Agregar Negocio option", appPage.getByText("Agregar Negocio"));
				firstVisible("Administrar Negocios option", appPage.getByText("Administrar Negocios"));
				screenshot(appPage, "02-mi-negocio-menu-expanded.png", false);
			});

			runStep("Agregar Negocio modal", () -> {
				clickIfVisible(firstVisible("Agregar Negocio", appPage.getByText("Agregar Negocio")), appPage);

				firstVisible("Crear Nuevo Negocio title", appPage.getByText("Crear Nuevo Negocio"));
				final Locator businessNameInput = firstVisible("Nombre del Negocio input",
						appPage.getByLabel("Nombre del Negocio"), appPage.getByPlaceholder("Nombre del Negocio"),
						appPage.locator("input[name*='nombre'], input[id*='nombre'], input"));
				firstVisible("business limit text", appPage.getByText("Tienes 2 de 3 negocios"));
				firstVisible("Cancelar button", appPage.getByText("Cancelar"));
				firstVisible("Crear Negocio button", appPage.getByText("Crear Negocio"));

				businessNameInput.click();
				businessNameInput.fill("Negocio Prueba Automatizacion");
				screenshot(appPage, "03-agregar-negocio-modal.png", false);

				clickIfVisible(firstVisible("Cancelar", appPage.getByText("Cancelar")), appPage);
			});

			runStep("Administrar Negocios view", () -> {
				clickIfVisible(firstVisible("Mi Negocio option", appPage.getByText("Mi Negocio")), appPage);
				clickIfVisible(firstVisible("Administrar Negocios option", appPage.getByText("Administrar Negocios")),
						appPage);
				waitForUi(appPage);

				firstVisible("Informacion General section", appPage.getByText("Informacion General"),
						appPage.getByText("Informaci\u00f3n General"));
				firstVisible("Detalles de la Cuenta section", appPage.getByText("Detalles de la Cuenta"));
				firstVisible("Tus Negocios section", appPage.getByText("Tus Negocios"));
				firstVisible("Seccion Legal section", appPage.getByText("Seccion Legal"), appPage.getByText("Secci\u00f3n Legal"));
				screenshot(appPage, "04-administrar-negocios-page.png", true);
			});

			runStep("Informaci\u00f3n General", () -> {
				firstVisible("user name",
						appPage.locator(":text-matches(\"[A-Za-z\\u00c1\\u00c9\\u00cd\\u00d3\\u00da\\u00d1\\u00e1\\u00e9\\u00ed\\u00f3\\u00fa\\u00f1]{2,}\\\\s+[A-Za-z\\u00c1\\u00c9\\u00cd\\u00d3\\u00da\\u00d1\\u00e1\\u00e9\\u00ed\\u00f3\\u00fa\\u00f1]{2,}\", \"i\")"));
				visibleText(appPage, "@");
				firstVisible("BUSINESS PLAN text", appPage.getByText("BUSINESS PLAN"));
				firstVisible("Cambiar Plan button", appPage.getByText("Cambiar Plan"));
			});

			runStep("Detalles de la Cuenta", () -> {
				firstVisible("Cuenta creada text", appPage.getByText("Cuenta creada"));
				firstVisible("Estado activo text", appPage.getByText("Estado activo"));
				firstVisible("Idioma seleccionado text", appPage.getByText("Idioma seleccionado"));
			});

			runStep("Tus Negocios", () -> {
				firstVisible("Tus Negocios section", appPage.getByText("Tus Negocios"));
				firstVisible("Agregar Negocio button", appPage.getByText("Agregar Negocio"));
				firstVisible("Tienes 2 de 3 negocios text", appPage.getByText("Tienes 2 de 3 negocios"));
			});

			runStep("T\u00e9rminos y Condiciones", () -> {
				final String finalUrl = validateLegalLink(context, appPage, "T\u00e9rminos y Condiciones",
						"T\u00e9rminos y Condiciones", "05-terminos-y-condiciones.png");
				legalUrls.put("Terminos y Condiciones URL", finalUrl);
			});

			runStep("Pol\u00edtica de Privacidad", () -> {
				final String finalUrl = validateLegalLink(context, appPage, "Pol\u00edtica de Privacidad",
						"Pol\u00edtica de Privacidad", "06-politica-de-privacidad.png");
				legalUrls.put("Politica de Privacidad URL", finalUrl);
			});

			writeFinalReport();

			final List<String> failedSteps = new ArrayList<>();
			for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
				if (!entry.getValue().passed) {
					failedSteps.add(entry.getKey() + ": " + entry.getValue().details);
				}
			}

			assertTrue("SaleADS Mi Negocio workflow validation failed:\n" + String.join("\n", failedSteps),
					failedSteps.isEmpty());
		}
	}

	private void runStep(final String name, final ThrowingRunnable body) {
		try {
			body.run();
			report.put(name, StepResult.pass());
		} catch (final Throwable t) {
			report.put(name, StepResult.fail(t.getMessage()));
		}
	}

	private String validateLegalLink(final BrowserContext context, final Page appPage, final String linkText,
			final String expectedHeading, final String screenshotName) {
		final Locator legalLink = firstVisible(linkText + " link", appPage.getByText(linkText));
		final String startUrl = appPage.url();
		Page targetPage = appPage;

		try {
			targetPage = context.waitForPage(() -> legalLink.click());
		} catch (final PlaywrightException ignored) {
			legalLink.click();
			waitForUi(appPage);
		}

		waitForUi(targetPage);
		firstVisible(expectedHeading + " heading", targetPage.getByText(expectedHeading));
		assertTrue("Expected legal content text to be visible",
				targetPage.locator("main, article, body").first().innerText().trim().length() > 100);
		screenshot(targetPage, screenshotName, true);
		final String finalUrl = targetPage.url();

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!startUrl.equals(finalUrl)) {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void clickIfVisible(final Locator locator, final Page page) {
		locator.click();
		waitForUi(page);
	}

	private Locator firstVisible(final String description, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			try {
				final Locator first = candidate.first();
				first.waitFor(
						new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout((double) TIMEOUT_MS));
				return first;
			} catch (final PlaywrightException ignored) {
				// try next candidate
			}
		}

		throw new AssertionError("Could not find visible element: " + description);
	}

	private void visibleText(final Page page, final String textSnippet) {
		final Locator text = page.getByText(textSnippet);
		text.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout((double) TIMEOUT_MS));
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// no-op
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
		} catch (final PlaywrightException ignored) {
			// no-op
		}
	}

	private void screenshot(final Page page, final String filename, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(runEvidenceDir.resolve(filename)).setFullPage(fullPage));
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder content = new StringBuilder();
		content.append("saleads_mi_negocio_full_test\n");
		content.append("Evidence directory: ").append(runEvidenceDir).append("\n\n");
		content.append("Final Report:\n");

		writeReportLine(content, "Login");
		writeReportLine(content, "Mi Negocio menu");
		writeReportLine(content, "Agregar Negocio modal");
		writeReportLine(content, "Administrar Negocios view");
		writeReportLine(content, "Informaci\u00f3n General");
		writeReportLine(content, "Detalles de la Cuenta");
		writeReportLine(content, "Tus Negocios");
		writeReportLine(content, "T\u00e9rminos y Condiciones");
		writeReportLine(content, "Pol\u00edtica de Privacidad");

		if (!legalUrls.isEmpty()) {
			content.append("\nCaptured URLs:\n");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				content.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
			}
		}

		final Path reportFile = runEvidenceDir.resolve("final-report.txt");
		Files.writeString(reportFile, content.toString());
		System.out.println(content.toString());
		System.out.println("Final report saved at: " + reportFile.toAbsolutePath());
	}

	private void writeReportLine(final StringBuilder content, final String key) {
		final StepResult result = report.getOrDefault(key, StepResult.fail("step did not execute"));
		content.append("- ").append(key).append(": ").append(result.passed ? "PASS" : "FAIL");
		if (result.details != null && !result.details.isBlank()) {
			content.append(" (").append(result.details).append(")");
		}
		content.append("\n");
	}

	private static String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass() {
			return new StepResult(true, "");
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details == null ? "no details" : details);
		}
	}
}
