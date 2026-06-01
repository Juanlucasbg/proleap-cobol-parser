package io.proleap.saleads;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

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
import java.util.regex.Pattern;

import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * SaleADS.ai E2E test for the "Mi Negocio" workflow.
 *
 * <p>
 * This test is disabled by default.
 *
 * <p>
 * Run example:
 * <code>
 * mvn -Dtest=io.proleap.saleads.SaleadsMiNegocioFullTest \
 *   -Dsaleads.e2e.enabled=true \
 *   -Dsaleads.login.url=https://your-saleads-environment/login \
 *   test
 * </code>
 */
public class SaleadsMiNegocioFullTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO = "Información General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Términos y Condiciones";
	private static final String STEP_PRIVACY = "Política de Privacidad";

	@Test
	public void saleadsMiNegocioWorkflow() throws IOException {
		assumeTrue("Enable this E2E with -Dsaleads.e2e.enabled=true",
				Boolean.parseBoolean(readValue("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false")));

		final String loginUrl = readValue("saleads.login.url", "SALEADS_LOGIN_URL", "");
		assertTrue(
				"saleads.login.url or SALEADS_LOGIN_URL must be provided. The URL is environment-provided and not hardcoded.",
				!loginUrl.isBlank());

		final String loginEmail = readValue("saleads.login.email", "SALEADS_LOGIN_EMAIL",
				"juanlucasbarbiergarzon@gmail.com");
		final boolean headless = Boolean.parseBoolean(readValue("saleads.headless", "SALEADS_HEADLESS", "true"));
		final Path screenshotDir = Paths.get(readValue("saleads.screenshot.dir", "SALEADS_SCREENSHOT_DIR",
				"target/saleads-e2e-screenshots"));
		Files.createDirectories(screenshotDir);

		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final LinkedHashMap<String, String> report = initializeReport();
		final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();

		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium()
						.launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(250));
				BrowserContext context = browser.newContext(
						new Browser.NewContextOptions().setViewportSize(1600, 1200).setLocale("es-ES"))) {

			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(20000);
			appPage.setDefaultNavigationTimeout(30000);
			appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUiToLoad(appPage);

			runStep(STEP_LOGIN, report, failures,
					() -> performLoginWithGoogle(context, appPage, loginEmail, screenshotDir, runId));

			runStep(STEP_MENU, report, failures, () -> validateMiNegocioMenu(appPage, screenshotDir, runId));

			runStep(STEP_MODAL, report, failures, () -> validateAgregarNegocioModal(appPage, screenshotDir, runId));

			runStep(STEP_ADMIN_VIEW, report, failures,
					() -> validateAdministrarNegociosView(appPage, screenshotDir, runId));

			runStep(STEP_INFO, report, failures, () -> validateInformacionGeneral(appPage, loginEmail));

			runStep(STEP_ACCOUNT_DETAILS, report, failures, () -> validateDetallesCuenta(appPage));

			runStep(STEP_BUSINESSES, report, failures, () -> validateTusNegocios(appPage));

			runStep(STEP_TERMS, report, failures, () -> {
				final String termsUrl = validateLegalLinkAndReturn(appPage, context, "Términos y Condiciones",
						"Términos y Condiciones", screenshotDir, runId, "terminos");
				legalUrls.put("Términos y Condiciones", termsUrl);
			});

			runStep(STEP_PRIVACY, report, failures, () -> {
				final String privacyUrl = validateLegalLinkAndReturn(appPage, context, "Política de Privacidad",
						"Política de Privacidad", screenshotDir, runId, "politica-privacidad");
				legalUrls.put("Política de Privacidad", privacyUrl);
			});
		} finally {
			printFinalReport(report, legalUrls, screenshotDir);
		}

		assertTrue("Some validations failed:\n - " + String.join("\n - ", failures), failures.isEmpty());
	}

	private void performLoginWithGoogle(final BrowserContext context, final Page appPage, final String loginEmail,
			final Path screenshotDir, final String runId) {
		final Locator loginButton = firstVisible(20000, appPage.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(google|sign in|iniciar sesi[oó]n)"))),
				appPage.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(google|sign in|iniciar sesi[oó]n)"))),
				appPage.getByText(Pattern.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|google)")));
		clickAndWait(appPage, loginButton);

		// Some environments open Google account selection in a popup.
		appPage.waitForTimeout(1500);
		Page popup = latestPopupIfAny(context, appPage);
		if (popup != null) {
			waitForUiToLoad(popup);
			clickGoogleAccountIfVisible(popup, loginEmail);
		}

		// Fallback for account selector rendered in the same page.
		clickGoogleAccountIfVisible(appPage, loginEmail);

		waitForUiToLoad(appPage);
		assertTrue("Main application interface should be visible after login",
				anyVisible(appPage.locator("aside").first(), appPage.locator("nav").first(),
						appPage.getByText(Pattern.compile("(?i)(mi negocio|negocio)")).first()));
		assertTrue("Left sidebar navigation should be visible after login",
				anyVisible(appPage.locator("aside").first(), appPage.getByText(Pattern.compile("(?i)(mi negocio|negocio)")).first()));
		takeScreenshot(appPage, screenshotDir, runId, "01-dashboard-loaded", true);
	}

	private void validateMiNegocioMenu(final Page appPage, final Path screenshotDir, final String runId) {
		expandMiNegocioIfNeeded(appPage);
		assertVisible(appPage, "Agregar Negocio");
		assertVisible(appPage, "Administrar Negocios");
		takeScreenshot(appPage, screenshotDir, runId, "02-mi-negocio-menu-expanded", false);
	}

	private void validateAgregarNegocioModal(final Page appPage, final Path screenshotDir, final String runId) {
		clickAndWait(appPage, byTextEntry(appPage, "Agregar Negocio"));
		assertVisible(appPage, "Crear Nuevo Negocio");
		assertVisible(appPage, "Tienes 2 de 3 negocios");
		assertVisible(appPage, "Cancelar");
		assertVisible(appPage, "Crear Negocio");

		final Locator nombreInput = firstVisible(10000, appPage.getByLabel("Nombre del Negocio"),
				appPage.getByPlaceholder("Nombre del Negocio"),
				appPage.locator("label:has-text('Nombre del Negocio')").locator("xpath=following::input[1]"));
		assertTrue("'Nombre del Negocio' input field should be visible", nombreInput.isVisible());

		takeScreenshot(appPage, screenshotDir, runId, "03-agregar-negocio-modal", false);

		nombreInput.click();
		nombreInput.fill("Negocio Prueba Automatizacion");
		clickAndWait(appPage, byTextEntry(appPage, "Cancelar"));
	}

	private void validateAdministrarNegociosView(final Page appPage, final Path screenshotDir, final String runId) {
		expandMiNegocioIfNeeded(appPage);
		clickAndWait(appPage, byTextEntry(appPage, "Administrar Negocios"));

		assertVisible(appPage, "Información General");
		assertVisible(appPage, "Detalles de la Cuenta");
		assertVisible(appPage, "Tus Negocios");
		assertVisible(appPage, "Sección Legal");
		takeScreenshot(appPage, screenshotDir, runId, "04-administrar-negocios-page", true);
	}

	private void validateInformacionGeneral(final Page appPage, final String loginEmail) {
		assertVisible(appPage, "Información General");
		assertVisible(appPage, "BUSINESS PLAN");
		assertVisible(appPage, "Cambiar Plan");

		final String bodyText = safeText(appPage.locator("body").first());
		assertTrue("User email should be visible", bodyText.contains(loginEmail) || bodyText.matches("(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*"));
		assertTrue("User name should be visible", hasNameLikeText(bodyText));
	}

	private void validateDetallesCuenta(final Page appPage) {
		assertVisible(appPage, "Detalles de la Cuenta");
		assertVisible(appPage, "Cuenta creada");
		assertVisible(appPage, "Estado activo");
		assertVisible(appPage, "Idioma seleccionado");
	}

	private void validateTusNegocios(final Page appPage) {
		assertVisible(appPage, "Tus Negocios");
		assertVisible(appPage, "Agregar Negocio");
		assertVisible(appPage, "Tienes 2 de 3 negocios");
		final String bodyText = safeText(appPage.locator("body").first());
		assertTrue("Business list should be visible", bodyText.contains("Tus Negocios") && bodyText.length() > 80);
	}

	private String validateLegalLinkAndReturn(final Page appPage, final BrowserContext context, final String linkText,
			final String expectedHeading, final Path screenshotDir, final String runId, final String screenshotName) {
		final int pagesBefore = context.pages().size();
		clickAndWait(appPage, byTextEntry(appPage, linkText));
		appPage.waitForTimeout(1500);

		final List<Page> pagesAfter = context.pages();
		final boolean openedInNewTab = pagesAfter.size() > pagesBefore;
		final Page legalPage = openedInNewTab ? pagesAfter.get(pagesAfter.size() - 1) : appPage;

		waitForUiToLoad(legalPage);
		waitForAnyVisible(15000,
				legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(expectedHeading)),
				legalPage.getByText(expectedHeading));
		final String legalBody = safeText(legalPage.locator("body").first());
		assertTrue("Legal content text should be visible for " + expectedHeading, legalBody.trim().length() > 200);
		takeScreenshot(legalPage, screenshotDir, runId, "0" + ("Términos y Condiciones".equals(linkText) ? "5" : "6")
				+ "-" + screenshotName, true);

		final String finalUrl = legalPage.url();

		if (openedInNewTab) {
			legalPage.close();
			appPage.bringToFront();
		} else {
			appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
		}
		waitForUiToLoad(appPage);
		return finalUrl;
	}

	private void clickGoogleAccountIfVisible(final Page page, final String email) {
		final Locator accountLocator = firstVisibleOrNull(6000, page.getByText(email),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(email)),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(email)));
		if (accountLocator != null) {
			clickAndWait(page, accountLocator);
		}
	}

	private void expandMiNegocioIfNeeded(final Page page) {
		if (!anyVisible(page.getByText("Agregar Negocio").first(), page.getByText("Administrar Negocios").first())) {
			final Locator negocio = byTextEntry(page, "Negocio");
			clickAndWait(page, negocio);
		}

		if (!anyVisible(page.getByText("Agregar Negocio").first(), page.getByText("Administrar Negocios").first())) {
			final Locator miNegocio = byTextEntry(page, "Mi Negocio");
			clickAndWait(page, miNegocio);
		}
	}

	private void runStep(final String stepName, final Map<String, String> report, final List<String> failures,
			final Runnable stepAction) {
		try {
			stepAction.run();
			report.put(stepName, "PASS");
		} catch (final Throwable throwable) {
			report.put(stepName, "FAIL");
			failures.add(stepName + ": " + throwable.getMessage());
		}
	}

	private void printFinalReport(final LinkedHashMap<String, String> report, final LinkedHashMap<String, String> legalUrls,
			final Path screenshotDir) {
		System.out.println("=== SaleADS Mi Negocio Full Test Report ===");
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("=== Final URLs ===");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(entry.getKey() + ": " + entry.getValue());
			}
		}
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
	}

	private void assertVisible(final Page page, final String text) {
		final Locator locator = firstVisible(10000, page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(text)),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)),
				page.getByText(text));
		assertTrue("Expected visible text: " + text, locator.isVisible());
	}

	private Locator byTextEntry(final Page page, final String text) {
		return firstVisible(10000, page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)),
				page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(text)), page.getByText(text));
	}

	private Page latestPopupIfAny(final BrowserContext context, final Page appPage) {
		Page latest = null;
		for (final Page page : context.pages()) {
			if (page != appPage) {
				latest = page;
			}
		}
		return latest;
	}

	private Locator firstVisible(final long timeoutMs, final Locator... candidates) {
		final Locator locator = firstVisibleOrNull(timeoutMs, candidates);
		if (locator == null) {
			throw new AssertionError("Expected at least one visible element for provided locator candidates.");
		}
		return locator;
	}

	private Locator firstVisibleOrNull(final long timeoutMs, final Locator... candidates) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			for (final Locator candidate : candidates) {
				final Locator first = candidate.first();
				try {
					if (first.count() > 0 && first.isVisible()) {
						return first;
					}
				} catch (final RuntimeException ignored) {
					// Try the next candidate.
				}
			}
			sleep(250);
		}
		return null;
	}

	private void waitForAnyVisible(final long timeoutMs, final Locator... candidates) {
		firstVisible(timeoutMs, candidates);
	}

	private boolean anyVisible(final Locator... locators) {
		for (final Locator locator : locators) {
			try {
				if (locator.count() > 0 && locator.isVisible()) {
					return true;
				}
			} catch (final RuntimeException ignored) {
				// Continue checking.
			}
		}
		return false;
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.scrollIntoViewIfNeeded();
		locator.click();
		waitForUiToLoad(page);
	}

	private void waitForUiToLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000));
		} catch (final RuntimeException ignored) {
			// Some UI actions don't trigger document load.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(12000));
		} catch (final RuntimeException ignored) {
			// Network can stay active in SPA apps; continue.
		}
		page.waitForTimeout(700);
	}

	private void takeScreenshot(final Page page, final Path screenshotDir, final String runId, final String label,
			final boolean fullPage) {
		final Path screenshotPath = screenshotDir.resolve(runId + "-" + label + ".png");
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private String readValue(final String propertyKey, final String envKey, final String defaultValue) {
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		return defaultValue;
	}

	private LinkedHashMap<String, String> initializeReport() {
		final LinkedHashMap<String, String> report = new LinkedHashMap<>();
		report.put(STEP_LOGIN, "FAIL");
		report.put(STEP_MENU, "FAIL");
		report.put(STEP_MODAL, "FAIL");
		report.put(STEP_ADMIN_VIEW, "FAIL");
		report.put(STEP_INFO, "FAIL");
		report.put(STEP_ACCOUNT_DETAILS, "FAIL");
		report.put(STEP_BUSINESSES, "FAIL");
		report.put(STEP_TERMS, "FAIL");
		report.put(STEP_PRIVACY, "FAIL");
		return report;
	}

	private String safeText(final Locator locator) {
		try {
			final String text = locator.innerText();
			return text == null ? "" : text;
		} catch (final RuntimeException exception) {
			return "";
		}
	}

	private boolean hasNameLikeText(final String text) {
		final String normalized = text.replaceAll("[\\r\\n]+", " ").replaceAll("\\s{2,}", " ").trim();
		return Pattern.compile("(?i).*\\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,}\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,}\\b.*")
				.matcher(normalized).matches();
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(exception);
		}
	}
}
