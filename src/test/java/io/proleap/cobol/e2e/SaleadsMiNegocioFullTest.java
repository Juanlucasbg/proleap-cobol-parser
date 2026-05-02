package io.proleap.cobol.e2e;

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter ARTIFACT_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String saleadsStartUrl = getValue("saleads.url", "SALEADS_START_URL", null);
		Assume.assumeTrue(
				"Set -Dsaleads.url or SALEADS_START_URL to run SaleADS E2E workflow test.",
				saleadsStartUrl != null && !saleadsStartUrl.isBlank());
		final boolean headless = Boolean.parseBoolean(getValue("saleads.headless", "SALEADS_HEADLESS", "true"));
		final String browserName = getValue("saleads.browser", "SALEADS_BROWSER", "chromium");
		final Path artifactsDir = createArtifactsDir();
		final Map<String, String> finalReport = initializeFinalReport();
		final List<String> failureDetails = new ArrayList<>();
		final Map<String, String> capturedUrls = new LinkedHashMap<>();
		final AtomicReference<String> appPageUrl = new AtomicReference<>(saleadsStartUrl);

		try (Playwright playwright = Playwright.create();
				Browser browser = launchBrowser(playwright, browserName, headless);
				BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
				Page page = context.newPage()) {

			page.navigate(saleadsStartUrl, new Page.NavigateOptions().setWaitUntil(LoadState.DOMCONTENTLOADED));
			waitForUi(page);

			boolean canContinue = runStep("Login", finalReport, failureDetails, () -> {
				loginWithGoogle(page);
				assertAnyVisible("Main application interface", visibleTextLocators(page, "Negocio", "Mi Negocio", "Dashboard"));
				assertAnyVisible("Left sidebar navigation", page.locator("aside"), page.locator("nav"));
				saveScreenshot(page, artifactsDir, "01-dashboard-loaded.png", true);
			});

			if (canContinue) {
				canContinue = runStep("Mi Negocio menu", finalReport, failureDetails, () -> {
					openMiNegocioMenu(page);
					assertAnyVisible("Expanded Mi Negocio submenu", visibleTextLocators(page, "Agregar Negocio", "Administrar Negocios"));
					waitForVisibleByText(page, "Agregar Negocio");
					waitForVisibleByText(page, "Administrar Negocios");
					saveScreenshot(page, artifactsDir, "02-mi-negocio-menu-expanded.png", true);
				});
			}

			if (canContinue) {
				canContinue = runStep("Agregar Negocio modal", finalReport, failureDetails, () -> {
					clickByText(page, "Agregar Negocio");
					waitForUi(page);
					waitForVisibleByText(page, "Crear Nuevo Negocio");
					waitForAnyVisible("Nombre del Negocio input",
							page.getByLabel(Pattern.compile("(?i)Nombre del Negocio")),
							page.locator("input[placeholder*='Nombre del Negocio']"));
					waitForVisibleByRegex(page, "(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios");
					waitForVisibleByText(page, "Cancelar");
					waitForVisibleByText(page, "Crear Negocio");
					saveScreenshot(page, artifactsDir, "03-agregar-negocio-modal.png", true);

					Locator nameInput = firstVisibleOrNull(
							page.getByLabel(Pattern.compile("(?i)Nombre del Negocio")),
							page.locator("input[placeholder*='Nombre del Negocio']"));
					if (nameInput != null) {
						nameInput.fill("Negocio Prueba Automatizacion");
					}
					clickByText(page, "Cancelar");
					waitForUi(page);
				});
			}

			if (canContinue) {
				canContinue = runStep("Administrar Negocios view", finalReport, failureDetails, () -> {
					openMiNegocioMenu(page);
					clickByText(page, "Administrar Negocios");
					waitForUi(page);
					waitForAnyVisible("Informacion General section",
							textLocators(page, "Información General", "Informacion General"));
					waitForAnyVisible("Detalles de la Cuenta section",
							textLocators(page, "Detalles de la Cuenta", "Detalles de Cuenta"));
					waitForVisibleByText(page, "Tus Negocios");
					waitForAnyVisible("Seccion Legal section",
							textLocators(page, "Sección Legal", "Seccion Legal"));
					saveScreenshot(page, artifactsDir, "04-administrar-negocios-view.png", true);
					appPageUrl.set(page.url());
				});
			}

			if (canContinue) {
				canContinue = runStep("Información General", finalReport, failureDetails, () -> {
					waitForAnyVisible("User name",
							textLocators(page, "Juan", "Lucas", "Nombre", "Nombre de usuario"));
					waitForVisibleByRegex(page, "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
					waitForVisibleByText(page, "BUSINESS PLAN");
					waitForVisibleByText(page, "Cambiar Plan");
				});
			}

			if (canContinue) {
				canContinue = runStep("Detalles de la Cuenta", finalReport, failureDetails, () -> {
					waitForVisibleByText(page, "Cuenta creada");
					waitForAnyVisible("Estado activo text",
							textLocators(page, "Estado activo", "Activo"));
					waitForAnyVisible("Idioma seleccionado text",
							textLocators(page, "Idioma seleccionado", "Idioma"));
				});
			}

			if (canContinue) {
				canContinue = runStep("Tus Negocios", finalReport, failureDetails, () -> {
					waitForVisibleByText(page, "Tus Negocios");
					waitForVisibleByRegex(page, "(?i)Agregar\\s+Negocio");
					waitForVisibleByRegex(page, "(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios");
					Locator listCandidates = page.locator("section:has-text('Tus Negocios') li, section:has-text('Tus Negocios') [role='listitem'], section:has-text('Tus Negocios') tr");
					if (listCandidates.count() == 0) {
						waitForAnyVisible("Business list content",
								textLocators(page, "Negocio", "Empresa", "Business"));
					}
				});
			}

			if (canContinue) {
				canContinue = runStep("Términos y Condiciones", finalReport, failureDetails, () -> {
					final String legalUrl = openLegalDocument(page, "Términos y Condiciones", "Terminos y Condiciones",
							"Términos y Condiciones", "Terminos y Condiciones",
							artifactsDir, "05-terminos-y-condiciones.png", appPageUrl.get());
					capturedUrls.put("Términos y Condiciones URL", legalUrl);
				});
			}

			if (canContinue) {
				runStep("Política de Privacidad", finalReport, failureDetails, () -> {
					final String legalUrl = openLegalDocument(page, "Política de Privacidad", "Politica de Privacidad",
							"Política de Privacidad", "Politica de Privacidad",
							artifactsDir, "06-politica-de-privacidad.png", appPageUrl.get());
					capturedUrls.put("Política de Privacidad URL", legalUrl);
				});
			}
		}

		writeFinalReport(artifactsDir, finalReport, failureDetails, capturedUrls);
		if (!failureDetails.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow failed. Review artifacts in: " + artifactsDir.toAbsolutePath());
		}
	}

	private String openLegalDocument(
			final Page appPage,
			final String legalLinkTextPrimary,
			final String legalLinkTextFallback,
			final String legalHeadingPrimary,
			final String legalHeadingFallback,
			final Path artifactsDir,
			final String screenshotName,
			final String returnUrl) {
		Page legalPage = null;
		boolean openedInNewTab = false;

		try {
			legalPage = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(6000), () -> {
				clickByAnyText(appPage, legalLinkTextPrimary, legalLinkTextFallback);
			});
			openedInNewTab = true;
		} catch (PlaywrightException popupTimeout) {
			clickByAnyText(appPage, legalLinkTextPrimary, legalLinkTextFallback);
			legalPage = appPage;
		}

		waitForUi(legalPage);
		waitForAnyVisible("Legal page heading", textLocators(legalPage, legalHeadingPrimary, legalHeadingFallback));
		final String bodyText = legalPage.locator("body").innerText();
		if (bodyText == null || bodyText.trim().length() < 100) {
			throw new AssertionError("Legal content text is not visible.");
		}

		saveScreenshot(legalPage, artifactsDir, screenshotName, true);
		final String legalUrl = legalPage.url();

		if (openedInNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.navigate(returnUrl, new Page.NavigateOptions().setWaitUntil(LoadState.DOMCONTENTLOADED));
			waitForUi(appPage);
		}

		return legalUrl;
	}

	private void loginWithGoogle(final Page page) {
		Page loginPopup = null;

		try {
			loginPopup = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(6000), () -> {
				clickByAnyText(page, "Sign in with Google", "Iniciar sesion con Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
			});
		} catch (PlaywrightException popupTimeout) {
			clickByAnyText(page, "Sign in with Google", "Iniciar sesion con Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		}

		if (loginPopup != null) {
			waitForUi(loginPopup);
			Locator preferredAccount = loginPopup.getByText(GOOGLE_ACCOUNT_EMAIL, new Page.GetByTextOptions().setExact(true));
			if (isVisible(preferredAccount, 7000)) {
				preferredAccount.first().click();
				waitForUi(loginPopup);
			}
		}

		waitForAnyVisible("Application sidebar after login", visibleTextLocators(page, "Negocio", "Mi Negocio", "Dashboard"));
	}

	private void openMiNegocioMenu(final Page page) {
		Locator administrar = page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true));
		Locator agregar = page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(true));
		if (isVisible(administrar, 800) && isVisible(agregar, 800)) {
			return;
		}

		clickByAnyText(page, "Mi Negocio", "Negocio");
		waitForUi(page);
	}

	private static Map<String, String> initializeFinalReport() {
		final Map<String, String> report = new LinkedHashMap<>();
		for (String field : REPORT_FIELDS) {
			report.put(field, "FAIL");
		}
		return report;
	}

	private static Path createArtifactsDir() throws IOException {
		final Path artifactsDir = Paths.get("target", "saleads-mi-negocio-e2e", ARTIFACT_TS_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(artifactsDir);
		return artifactsDir;
	}

	private static Browser launchBrowser(final Playwright playwright, final String browserName, final boolean headless) {
		final BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);
		switch (browserName.toLowerCase()) {
		case "firefox":
			return playwright.firefox().launch(options);
		case "webkit":
			return playwright.webkit().launch(options);
		case "chromium":
		default:
			return playwright.chromium().launch(options);
		}
	}

	private static String getValue(final String systemProperty, final String envVar, final String defaultValue) {
		final String propertyValue = System.getProperty(systemProperty);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		final String envValue = System.getenv(envVar);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		return defaultValue;
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(6000));
		} catch (PlaywrightException ignored) {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		}
		page.waitForTimeout(600);
	}

	private static void saveScreenshot(final Page page, final Path artifactsDir, final String fileName, final boolean fullPage) {
		final Path screenshotPath = artifactsDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private static boolean runStep(
			final String stepName,
			final Map<String, String> finalReport,
			final List<String> failureDetails,
			final CheckedRunnable action) {
		try {
			action.run();
			finalReport.put(stepName, "PASS");
			return true;
		} catch (Exception e) {
			finalReport.put(stepName, "FAIL");
			failureDetails.add(stepName + ": " + e.getMessage());
			return false;
		}
	}

	private static void writeFinalReport(
			final Path artifactsDir,
			final Map<String, String> finalReport,
			final List<String> failureDetails,
			final Map<String, String> capturedUrls) throws IOException {
		final Path reportPath = artifactsDir.resolve("final-report.txt");
		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("SaleADS Mi Negocio Full Test Report").append('\n');
		reportBuilder.append("=================================").append('\n').append('\n');

		for (String field : REPORT_FIELDS) {
			reportBuilder.append(field).append(": ").append(finalReport.getOrDefault(field, "FAIL")).append('\n');
		}

		if (!capturedUrls.isEmpty()) {
			reportBuilder.append('\n').append("Captured URLs").append('\n');
			reportBuilder.append("-------------").append('\n');
			for (Map.Entry<String, String> entry : capturedUrls.entrySet()) {
				reportBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		if (!failureDetails.isEmpty()) {
			reportBuilder.append('\n').append("Failure details").append('\n');
			reportBuilder.append("---------------").append('\n');
			for (String failure : failureDetails) {
				reportBuilder.append("- ").append(failure).append('\n');
			}
		}

		Files.writeString(reportPath, reportBuilder.toString());
		System.out.println(reportBuilder);
		System.out.println("Artifacts directory: " + artifactsDir.toAbsolutePath());
	}

	private static Locator[] textLocators(final Page page, final String... texts) {
		final Locator[] locators = new Locator[texts.length];
		for (int i = 0; i < texts.length; i++) {
			locators[i] = page.getByText(texts[i], new Page.GetByTextOptions().setExact(true));
		}
		return locators;
	}

	private static Locator[] visibleTextLocators(final Page page, final String... texts) {
		final List<Locator> locators = new ArrayList<>();
		for (String text : texts) {
			locators.add(page.getByText(text, new Page.GetByTextOptions().setExact(true)));
			locators.add(page.getByText(text));
		}
		return locators.toArray(new Locator[0]);
	}

	private static void clickByAnyText(final Page page, final String... texts) {
		for (String text : texts) {
			Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true));
			if (isVisible(exact, 1500)) {
				exact.first().click();
				waitForUi(page);
				return;
			}

			Locator contains = page.getByText(text);
			if (isVisible(contains, 1500)) {
				contains.first().click();
				waitForUi(page);
				return;
			}
		}

		throw new AssertionError("Could not click any visible text target: " + Arrays.toString(texts));
	}

	private static void clickByText(final Page page, final String text) {
		clickByAnyText(page, text);
	}

	private static void waitForVisibleByText(final Page page, final String text) {
		waitForAnyVisible("Text '" + text + "'", visibleTextLocators(page, text));
	}

	private static void waitForVisibleByRegex(final Page page, final String regex) {
		final Locator locator = page.getByText(Pattern.compile(regex));
		waitForAnyVisible("Regex text '" + regex + "'", locator);
	}

	private static void waitForAnyVisible(final String description, final Locator... locators) {
		assertAnyVisible(description, locators);
	}

	private static void assertAnyVisible(final String description, final Locator... locators) {
		for (Locator locator : locators) {
			if (isVisible(locator, 6000)) {
				return;
			}
		}

		throw new AssertionError(description + " is not visible.");
	}

	private static Locator firstVisibleOrNull(final Locator... locators) {
		for (Locator locator : locators) {
			if (isVisible(locator, 1200)) {
				return locator.first();
			}
		}
		return null;
	}

	private static boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
