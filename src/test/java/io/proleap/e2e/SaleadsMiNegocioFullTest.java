package io.proleap.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String STEP_INFORMACION_GENERAL = "Informacion General";
	private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Terminos y Condiciones";
	private static final String STEP_POLITICA = "Politica de Privacidad";

	private static final double WAIT_TIMEOUT_MS = 20000;
	private static final double FAST_WAIT_TIMEOUT_MS = 3000;

	private final Map<String, StepResult> report = new LinkedHashMap<>();
	private final Map<String, String> stepArtifacts = new LinkedHashMap<>();
	private String evidenceDirectoryPath = "not-set";

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final String baseUrl = getConfig("SALEADS_BASE_URL", "saleads.baseUrl", "");
		final String googleAccount = getConfig("SALEADS_GOOGLE_ACCOUNT", "saleads.googleAccount",
				"juanlucasbarbiergarzon@gmail.com");
		final boolean headless = Boolean
				.parseBoolean(getConfig("SALEADS_HEADLESS", "saleads.headless", "true"));
		final Path evidenceDir = createEvidenceDirectory();
		evidenceDirectoryPath = evidenceDir.toString();

		Assume.assumeTrue(
				"Set SALEADS_BASE_URL (or -Dsaleads.baseUrl) to run this environment-agnostic E2E workflow.",
				baseUrl != null && !baseUrl.isBlank());

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions()
					.setViewportSize(1600, 1000));
			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(WAIT_TIMEOUT_MS);

			appPage.navigate(baseUrl);
			waitForUi(appPage);

			runStep(STEP_LOGIN, appPage, evidenceDir, () -> {
				clickByVisibleText(appPage, Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|google)"));
				selectGoogleAccountIfVisible(appPage, googleAccount);
				waitForUi(appPage);

				assertAnyVisible("Main application interface did not appear after login",
						appPage.locator("main"), appPage.locator("aside"), appPage.locator("nav"));
				assertAnyVisible("Left sidebar navigation is not visible after login",
						appPage.locator("aside"), appPage.locator("[class*='sidebar']"),
						appPage.getByText(Pattern.compile("(?i)Negocio")).first());
				takeScreenshot(appPage, evidenceDir, "step1_dashboard_loaded", false);
			});

			runStep(STEP_MI_NEGOCIO_MENU, appPage, evidenceDir, () -> {
				assertAnyVisible("Left sidebar navigation is not visible",
						appPage.locator("aside"), appPage.locator("[class*='sidebar']"), appPage.locator("nav"));

				clickByVisibleText(appPage, Pattern.compile("(?i)Mi\\s+Negocio|Negocio"));
				assertVisible(appPage.getByText(Pattern.compile("(?i)Agregar\\s+Negocio")).first(),
						"'Agregar Negocio' is not visible in expanded menu");
				assertVisible(appPage.getByText(Pattern.compile("(?i)Administrar\\s+Negocios")).first(),
						"'Administrar Negocios' is not visible in expanded menu");
				takeScreenshot(appPage, evidenceDir, "step2_mi_negocio_menu_expanded", false);
			});

			runStep(STEP_AGREGAR_NEGOCIO_MODAL, appPage, evidenceDir, () -> {
				clickByVisibleText(appPage, Pattern.compile("(?i)Agregar\\s+Negocio"));
				assertVisible(appPage.getByText(Pattern.compile("(?i)Crear\\s+Nuevo\\s+Negocio")).first(),
						"Modal title 'Crear Nuevo Negocio' is not visible");
				assertAnyVisible("Input field 'Nombre del Negocio' is not visible",
						appPage.getByLabel(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")).first(),
						appPage.getByPlaceholder(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")).first(),
						appPage.getByText(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")).first());
				assertVisible(appPage.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")).first(),
						"Usage text 'Tienes 2 de 3 negocios' is not visible");
				assertVisible(
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first(),
						"Button 'Cancelar' is not visible");
				assertVisible(appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Crear\\s+Negocio"))).first(),
						"Button 'Crear Negocio' is not visible");
				takeScreenshot(appPage, evidenceDir, "step3_agregar_negocio_modal", false);

				final Locator businessNameInput = firstVisible(
						appPage.getByLabel(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")).first(),
						appPage.getByPlaceholder(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")).first());
				if (businessNameInput != null) {
					businessNameInput.click();
					waitForUi(appPage);
					businessNameInput.fill("Negocio Prueba Automatizacion");
					waitForUi(appPage);
				}

				clickByVisibleText(appPage, Pattern.compile("(?i)Cancelar"));
			});

			runStep(STEP_ADMINISTRAR_NEGOCIOS_VIEW, appPage, evidenceDir, () -> {
				expandMiNegocioIfNeeded(appPage);
				clickByVisibleText(appPage, Pattern.compile("(?i)Administrar\\s+Negocios"));
				waitForUi(appPage);

				assertVisible(appPage.getByText(Pattern.compile("(?i)Informaci[o\\u00f3]n\\s+General")).first(),
						"Section 'Informacion General' is not visible");
				assertVisible(appPage.getByText(Pattern.compile("(?i)Detalles\\s+de\\s+la\\s+Cuenta")).first(),
						"Section 'Detalles de la Cuenta' is not visible");
				assertVisible(appPage.getByText(Pattern.compile("(?i)Tus\\s+Negocios")).first(),
						"Section 'Tus Negocios' is not visible");
				assertVisible(appPage.getByText(Pattern.compile("(?i)Secci[o\\u00f3]n\\s+Legal")).first(),
						"Section 'Seccion Legal' is not visible");
				takeScreenshot(appPage, evidenceDir, "step4_administrar_negocios_view", true);
			});

			runStep(STEP_INFORMACION_GENERAL, appPage, evidenceDir, () -> {
				assertAnyVisible("User name is not visible in Informacion General",
						appPage.getByText(Pattern.compile("(?i)Hola")).first(),
						appPage.locator("text=/^[A-Za-z].*$/").first());
				assertAnyVisible("User email is not visible in Informacion General",
						appPage.getByText(Pattern.compile("(?i)@")).first(),
						appPage.locator("text=/.*@.*\\..*/").first());
				assertVisible(appPage.getByText(Pattern.compile("(?i)BUSINESS\\s+PLAN")).first(),
						"Text 'BUSINESS PLAN' is not visible");
				assertVisible(appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cambiar\\s+Plan"))).first(),
						"Button 'Cambiar Plan' is not visible");
			});

			runStep(STEP_DETALLES_CUENTA, appPage, evidenceDir, () -> {
				assertVisible(appPage.getByText(Pattern.compile("(?i)Cuenta\\s+creada")).first(),
						"'Cuenta creada' is not visible");
				assertVisible(appPage.getByText(Pattern.compile("(?i)Estado\\s+activo|Activo")).first(),
						"'Estado activo' is not visible");
				assertVisible(appPage.getByText(Pattern.compile("(?i)Idioma\\s+seleccionado")).first(),
						"'Idioma seleccionado' is not visible");
			});

			runStep(STEP_TUS_NEGOCIOS, appPage, evidenceDir, () -> {
				assertAnyVisible("Business list is not visible",
						appPage.getByText(Pattern.compile("(?i)Tus\\s+Negocios")).first(),
						appPage.locator("table"), appPage.locator("ul"));
				assertVisible(appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Agregar\\s+Negocio"))).first(),
						"Button 'Agregar Negocio' does not exist in 'Tus Negocios'");
				assertVisible(appPage.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")).first(),
						"Text 'Tienes 2 de 3 negocios' is not visible in 'Tus Negocios'");
			});

			runStep(STEP_TERMINOS, appPage, evidenceDir, () -> {
				final String finalUrl = validateLegalDocument(appPage, Pattern.compile("(?i)T[e\\u00e9]rminos\\s+y\\s+Condiciones"),
						Pattern.compile("(?i)T[e\\u00e9]rminos\\s+y\\s+Condiciones"), evidenceDir,
						"step8_terminos_condiciones");
				stepArtifacts.put(STEP_TERMINOS, "URL: " + finalUrl);
			});

			runStep(STEP_POLITICA, appPage, evidenceDir, () -> {
				final String finalUrl = validateLegalDocument(appPage,
						Pattern.compile("(?i)Pol[i\\u00ed]tica\\s+de\\s+Privacidad"),
						Pattern.compile("(?i)Pol[i\\u00ed]tica\\s+de\\s+Privacidad"), evidenceDir,
						"step9_politica_privacidad");
				stepArtifacts.put(STEP_POLITICA, "URL: " + finalUrl);
			});

			System.out.println(renderFinalReport());
			assertFalse("Some SaleADS validations failed:\n" + renderFinalReport(), hasFailures());
		}
	}

	private void runStep(final String stepName, final Page page, final Path evidenceDir, final CheckedStep action) {
		try {
			action.run();
			report.put(stepName, new StepResult(true, "PASS"));
		} catch (final Throwable e) {
			report.put(stepName, new StepResult(false, shortenMessage(e)));
			takeScreenshotQuietly(page, evidenceDir, "fail_" + normalizeFileName(stepName), false);
		}
	}

	private String validateLegalDocument(final Page appPage, final Pattern linkPattern, final Pattern headingPattern,
			final Path evidenceDir, final String screenshotName) {
		Page legalPage = appPage;
		boolean popupOpened = false;

		try {
			legalPage = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(8000), () -> {
				clickByVisibleText(appPage, linkPattern);
			});
			popupOpened = true;
		} catch (final PlaywrightException popupNotOpened) {
			clickByVisibleText(appPage, linkPattern);
			waitForUi(appPage);
		}

		waitForUi(legalPage);
		assertVisible(legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)).first(),
				"Legal heading is not visible for pattern: " + headingPattern.pattern());
		final String legalBodyText = legalPage.locator("body").innerText();
		assertTrue("Legal content text is not visible", legalBodyText != null && legalBodyText.trim().length() > 120);
		takeScreenshot(legalPage, evidenceDir, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (popupOpened) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack(new Page.GoBackOptions().setTimeout(10000));
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void selectGoogleAccountIfVisible(final Page page, final String accountEmail) {
		final Pattern accountPattern = Pattern.compile("(?i)" + Pattern.quote(accountEmail));
		final Locator accountChoice = firstVisible(page.getByText(accountPattern).first(),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(accountPattern)).first(),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(accountPattern)).first());
		if (accountChoice != null) {
			accountChoice.click();
			waitForUi(page);
		}
	}

	private void expandMiNegocioIfNeeded(final Page page) {
		if (!isVisible(page.getByText(Pattern.compile("(?i)Administrar\\s+Negocios")).first(), FAST_WAIT_TIMEOUT_MS)) {
			clickByVisibleText(page, Pattern.compile("(?i)Mi\\s+Negocio|Negocio"));
		}
	}

	private void clickByVisibleText(final Page page, final Pattern textPattern) {
		final Locator target = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)).first(),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern)).first(),
				page.getByText(textPattern).first());
		if (target == null) {
			throw new AssertionError("Could not find visible clickable element with text pattern: " + textPattern.pattern());
		}
		target.click(new Locator.ClickOptions().setTimeout(WAIT_TIMEOUT_MS));
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (final PlaywrightException ignored) {
			// best effort wait
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			// best effort wait
		}
		page.waitForTimeout(600);
	}

	private void assertVisible(final Locator locator, final String message) {
		assertTrue(message, isVisible(locator, FAST_WAIT_TIMEOUT_MS));
	}

	private void assertAnyVisible(final String message, final Locator... locators) {
		for (final Locator locator : locators) {
			if (isVisible(locator, FAST_WAIT_TIMEOUT_MS)) {
				return;
			}
		}
		throw new AssertionError(message);
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private Locator firstVisible(final Locator... locators) {
		final List<Locator> locatorList = Arrays.asList(locators);
		for (final Locator locator : locatorList) {
			if (locator != null && isVisible(locator, FAST_WAIT_TIMEOUT_MS)) {
				return locator;
			}
		}
		return null;
	}

	private Path createEvidenceDirectory() throws IOException {
		final String dir = getConfig("SALEADS_EVIDENCE_DIR", "saleads.evidenceDir",
				"target/saleads-evidence/mi-negocio-"
						+ DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now(ZoneOffset.UTC)));
		final Path evidenceDir = Paths.get(dir);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private void takeScreenshot(final Page page, final Path evidenceDir, final String screenshotName,
			final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(screenshotName + ".png"))
				.setFullPage(fullPage));
	}

	private void takeScreenshotQuietly(final Page page, final Path evidenceDir, final String screenshotName,
			final boolean fullPage) {
		try {
			if (page != null) {
				takeScreenshot(page, evidenceDir, screenshotName, fullPage);
			}
		} catch (final Exception ignored) {
			// failure evidence is best effort
		}
	}

	private String renderFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Workflow - Final Report").append(System.lineSeparator());
		builder.append("- ").append(STEP_LOGIN).append(": ").append(status(STEP_LOGIN)).append(System.lineSeparator());
		builder.append("- ").append(STEP_MI_NEGOCIO_MENU).append(": ").append(status(STEP_MI_NEGOCIO_MENU))
				.append(System.lineSeparator());
		builder.append("- ").append(STEP_AGREGAR_NEGOCIO_MODAL).append(": ").append(status(STEP_AGREGAR_NEGOCIO_MODAL))
				.append(System.lineSeparator());
		builder.append("- ").append(STEP_ADMINISTRAR_NEGOCIOS_VIEW).append(": ").append(status(STEP_ADMINISTRAR_NEGOCIOS_VIEW))
				.append(System.lineSeparator());
		builder.append("- ").append(STEP_INFORMACION_GENERAL).append(": ").append(status(STEP_INFORMACION_GENERAL))
				.append(System.lineSeparator());
		builder.append("- ").append(STEP_DETALLES_CUENTA).append(": ").append(status(STEP_DETALLES_CUENTA))
				.append(System.lineSeparator());
		builder.append("- ").append(STEP_TUS_NEGOCIOS).append(": ").append(status(STEP_TUS_NEGOCIOS))
				.append(System.lineSeparator());
		builder.append("- ").append(STEP_TERMINOS).append(": ").append(status(STEP_TERMINOS)).append(System.lineSeparator());
		builder.append("- ").append(STEP_POLITICA).append(": ").append(status(STEP_POLITICA)).append(System.lineSeparator());
		builder.append("Evidence directory: ").append(evidenceDirectoryPath).append(System.lineSeparator());
		return builder.toString();
	}

	private String status(final String stepKey) {
		final StepResult result = report.get(stepKey);
		if (result == null) {
			return "FAIL (step not executed)";
		}
		if (result.passed) {
			final String artifact = stepArtifacts.get(stepKey);
			if (artifact != null && !artifact.isBlank()) {
				return "PASS (" + artifact + ")";
			}
			return "PASS";
		}
		return "FAIL - " + result.detail;
	}

	private boolean hasFailures() {
		if (report.size() < 9) {
			return true;
		}
		for (final StepResult result : report.values()) {
			if (!result.passed) {
				return true;
			}
		}
		return false;
	}

	private String shortenMessage(final Throwable e) {
		final String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
		final int max = 250;
		return message.length() > max ? message.substring(0, max) + "..." : message;
	}

	private String normalizeFileName(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "_");
	}

	private String getConfig(final String envName, final String systemProperty, final String defaultValue) {
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		final String systemValue = System.getProperty(systemProperty);
		if (systemValue != null && !systemValue.isBlank()) {
			return systemValue;
		}
		return defaultValue;
	}

	private interface CheckedStep {
		void run() throws Exception;
	}

	private static class StepResult {
		private final boolean passed;
		private String detail;

		private StepResult(final boolean passed, final String detail) {
			this.passed = passed;
			this.detail = detail;
		}
	}

}
