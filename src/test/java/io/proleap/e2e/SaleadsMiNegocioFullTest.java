package io.proleap.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * Full E2E test for SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * Required env var:
 * <ul>
 * <li>SALEADS_LOGIN_URL: login URL for current environment (dev/staging/prod)</li>
 * </ul>
 *
 * <p>
 * Optional env vars:
 * <ul>
 * <li>SALEADS_GOOGLE_ACCOUNT_EMAIL (default: juanlucasbarbiergarzon@gmail.com)</li>
 * <li>SALEADS_EXPECTED_USER_NAME</li>
 * <li>SALEADS_HEADLESS (default: true)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final int DEFAULT_TIMEOUT_MS = 20_000;
	private static final int SHORT_TIMEOUT_MS = 2_500;

	private static final String FIELD_LOGIN = "Login";
	private static final String FIELD_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String FIELD_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String FIELD_ADMIN_VIEW = "Administrar Negocios view";
	private static final String FIELD_INFO_GENERAL = "Información General";
	private static final String FIELD_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String FIELD_BUSINESSES = "Tus Negocios";
	private static final String FIELD_TERMS = "Términos y Condiciones";
	private static final String FIELD_PRIVACY = "Política de Privacidad";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Set SALEADS_LOGIN_URL to run this E2E flow.", loginUrl != null && !loginUrl.isBlank());

		final String expectedGoogleEmail = getEnvOrDefault("SALEADS_GOOGLE_ACCOUNT_EMAIL",
				"juanlucasbarbiergarzon@gmail.com");
		final String expectedUserName = getEnvOrDefault("SALEADS_EXPECTED_USER_NAME", "").trim();
		final boolean headless = !"false".equalsIgnoreCase(getEnvOrDefault("SALEADS_HEADLESS", "true"));
		final Path evidenceDir = createEvidenceDir();
		final LinkedHashMap<String, String> report = initReport();
		final String[] termsUrl = new String[1];
		final String[] privacyUrl = new String[1];

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext();
			final Page page = context.newPage();

			final boolean loginOk = runStep(report, FIELD_LOGIN, () -> {
				page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
				waitForUi(page);

				final Page authPage = clickAndCapturePopupByText(page, "Sign in with Google",
						"Iniciar sesión con Google", "Iniciar con Google", "Continuar con Google", "Google");

				if (authPage != null) {
					waitForUi(authPage);
					selectGoogleAccountIfVisible(authPage, expectedGoogleEmail);
				} else {
					selectGoogleAccountIfVisible(page, expectedGoogleEmail);
				}

				waitForUi(page);
				assertAnyTextVisible(page, "Negocio", "Mi Negocio");
				assertSidebarVisible(page);
				takeScreenshot(page, evidenceDir, "01-dashboard-loaded.png", false);
			});

			final boolean menuOk;
			if (loginOk) {
				menuOk = runStep(report, FIELD_MI_NEGOCIO_MENU, () -> {
					clickByVisibleText(page, "Mi Negocio", "Negocio");
					assertAnyTextVisible(page, "Agregar Negocio");
					assertAnyTextVisible(page, "Administrar Negocios");
					takeScreenshot(page, evidenceDir, "02-mi-negocio-menu-expanded.png", false);
				});
			} else {
				menuOk = false;
				markBlocked(report, FIELD_MI_NEGOCIO_MENU, FIELD_LOGIN);
			}

			final boolean addBusinessModalOk;
			if (menuOk) {
				addBusinessModalOk = runStep(report, FIELD_AGREGAR_NEGOCIO_MODAL, () -> {
					clickByVisibleText(page, "Agregar Negocio");
					assertAnyTextVisible(page, "Crear Nuevo Negocio");
					assertAnyTextVisible(page, "Nombre del Negocio");
					assertAnyTextVisible(page, "Tienes 2 de 3 negocios");
					assertAnyTextVisible(page, "Cancelar");
					assertAnyTextVisible(page, "Crear Negocio");

					final Locator input = page.locator("input").first();
					assertTrue("Expected an input field in the modal.", isVisible(input));
					input.click();
					input.fill("Negocio Prueba Automatización");
					takeScreenshot(page, evidenceDir, "03-agregar-negocio-modal.png", false);
					clickByVisibleText(page, "Cancelar");
				});
			} else {
				addBusinessModalOk = false;
				markBlocked(report, FIELD_AGREGAR_NEGOCIO_MODAL, FIELD_MI_NEGOCIO_MENU);
			}

			final boolean adminViewOk;
			if (menuOk || addBusinessModalOk) {
				adminViewOk = runStep(report, FIELD_ADMIN_VIEW, () -> {
					if (!isAnyTextVisible(page, "Administrar Negocios")) {
						clickByVisibleText(page, "Mi Negocio", "Negocio");
					}
					clickByVisibleText(page, "Administrar Negocios");
					assertAnyTextVisible(page, "Información General");
					assertAnyTextVisible(page, "Detalles de la Cuenta");
					assertAnyTextVisible(page, "Tus Negocios");
					assertAnyTextVisible(page, "Sección Legal");
					takeScreenshot(page, evidenceDir, "04-administrar-negocios.png", true);
				});
			} else {
				adminViewOk = false;
				markBlocked(report, FIELD_ADMIN_VIEW, FIELD_MI_NEGOCIO_MENU);
			}

			if (adminViewOk) {
				runStep(report, FIELD_INFO_GENERAL, () -> {
					assertAnyTextVisible(page, "Información General");
					assertAnyTextVisible(page, expectedGoogleEmail);
					assertAnyTextVisible(page, "BUSINESS PLAN");
					assertAnyTextVisible(page, "Cambiar Plan");

					if (!expectedUserName.isBlank()) {
						assertAnyTextVisible(page, expectedUserName);
					} else {
						assertAnyTextVisible(page, "Nombre", "Usuario");
					}
				});

				runStep(report, FIELD_ACCOUNT_DETAILS, () -> {
					assertAnyTextVisible(page, "Cuenta creada");
					assertAnyTextVisible(page, "Estado activo");
					assertAnyTextVisible(page, "Idioma seleccionado");
				});

				runStep(report, FIELD_BUSINESSES, () -> {
					assertAnyTextVisible(page, "Tus Negocios");
					assertAnyTextVisible(page, "Agregar Negocio");
					assertAnyTextVisible(page, "Tienes 2 de 3 negocios");
				});

				runStep(report, FIELD_TERMS, () -> {
					termsUrl[0] = openLegalPageAndValidate(page, evidenceDir, "Términos y Condiciones",
							"Términos y Condiciones", "05-terminos-y-condiciones.png");
				});

				runStep(report, FIELD_PRIVACY, () -> {
					privacyUrl[0] = openLegalPageAndValidate(page, evidenceDir, "Política de Privacidad",
							"Política de Privacidad", "06-politica-de-privacidad.png");
				});
			} else {
				markBlocked(report, FIELD_INFO_GENERAL, FIELD_ADMIN_VIEW);
				markBlocked(report, FIELD_ACCOUNT_DETAILS, FIELD_ADMIN_VIEW);
				markBlocked(report, FIELD_BUSINESSES, FIELD_ADMIN_VIEW);
				markBlocked(report, FIELD_TERMS, FIELD_ADMIN_VIEW);
				markBlocked(report, FIELD_PRIVACY, FIELD_ADMIN_VIEW);
			}
		}

		final Path reportPath = writeFinalReport(evidenceDir, report, termsUrl[0], privacyUrl[0]);
		if (hasFailures(report)) {
			fail("SaleADS Mi Negocio workflow has failures. Review report: " + reportPath.toAbsolutePath());
		}
	}

	private LinkedHashMap<String, String> initReport() {
		final LinkedHashMap<String, String> report = new LinkedHashMap<>();
		report.put(FIELD_LOGIN, "NOT_EXECUTED");
		report.put(FIELD_MI_NEGOCIO_MENU, "NOT_EXECUTED");
		report.put(FIELD_AGREGAR_NEGOCIO_MODAL, "NOT_EXECUTED");
		report.put(FIELD_ADMIN_VIEW, "NOT_EXECUTED");
		report.put(FIELD_INFO_GENERAL, "NOT_EXECUTED");
		report.put(FIELD_ACCOUNT_DETAILS, "NOT_EXECUTED");
		report.put(FIELD_BUSINESSES, "NOT_EXECUTED");
		report.put(FIELD_TERMS, "NOT_EXECUTED");
		report.put(FIELD_PRIVACY, "NOT_EXECUTED");
		return report;
	}

	private Path createEvidenceDir() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
				.format(LocalDateTime.now(ZoneOffset.UTC));
		final Path evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private boolean runStep(final Map<String, String> report, final String field, final CheckedRunnable step) {
		try {
			step.run();
			report.put(field, "PASS");
			return true;
		} catch (final Throwable throwable) {
			report.put(field, "FAIL - " + safeMessage(throwable));
			return false;
		}
	}

	private void markBlocked(final Map<String, String> report, final String field, final String blockingField) {
		report.put(field, "FAIL - Blocked by " + blockingField);
	}

	private boolean hasFailures(final Map<String, String> report) {
		for (final String value : report.values()) {
			if (!value.startsWith("PASS")) {
				return true;
			}
		}
		return false;
	}

	private Path writeFinalReport(final Path evidenceDir, final LinkedHashMap<String, String> report, final String termsUrl,
			final String privacyUrl) throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Test Report").append('\n');
		builder.append("Generated (UTC): ").append(LocalDateTime.now(ZoneOffset.UTC)).append('\n');
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n').append('\n');

		for (final Map.Entry<String, String> entry : report.entrySet()) {
			builder.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}

		builder.append('\n');
		builder.append("Términos y Condiciones URL: ").append(termsUrl == null ? "N/A" : termsUrl).append('\n');
		builder.append("Política de Privacidad URL: ").append(privacyUrl == null ? "N/A" : privacyUrl).append('\n');

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, builder.toString(), StandardCharsets.UTF_8);
		return reportPath;
	}

	private void selectGoogleAccountIfVisible(final Page page, final String expectedGoogleEmail) {
		if (isAnyTextVisible(page, expectedGoogleEmail)) {
			clickByVisibleText(page, expectedGoogleEmail);
		}
	}

	private Page clickAndCapturePopupByText(final Page page, final String... textOptions) {
		Locator clickableTarget = null;

		for (final String text : textOptions) {
			final Locator matches = page.getByText(text, new Page.GetByTextOptions().setExact(false));
			final long count = Math.min(matches.count(), 5);

			for (int index = 0; index < count; index++) {
				final Locator candidate = matches.nth(index);
				if (!isVisible(candidate)) {
					continue;
				}

				final Locator clickableAncestor = candidate
						.locator("xpath=ancestor-or-self::*[self::button or self::a or @role='button' or @role='menuitem'][1]");
				clickableTarget = clickableAncestor.count() > 0 ? clickableAncestor.first() : candidate;
				break;
			}

			if (clickableTarget != null) {
				break;
			}
		}

		if (clickableTarget == null) {
			throw new AssertionError("Unable to find login button by visible text.");
		}

		try {
			final Locator finalClickTarget = clickableTarget;
			return page.waitForPopup(() -> finalClickTarget.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS)),
					new Page.WaitForPopupOptions().setTimeout(8_000));
		} catch (final PlaywrightException exception) {
			waitForUi(page);
			return null;
		}
	}

	private String openLegalPageAndValidate(final Page appPage, final Path evidenceDir, final String linkText,
			final String headingText, final String screenshotName) {
		Page legalPage;

		try {
			legalPage = appPage.waitForPopup(() -> clickByVisibleText(appPage, linkText),
					new Page.WaitForPopupOptions().setTimeout(8_000));
			waitForUi(legalPage);
		} catch (final PlaywrightException exception) {
			legalPage = appPage;
		}

		waitForUi(legalPage);
		assertAnyTextVisible(legalPage, headingText);
		final String body = legalPage.locator("body").innerText();
		assertTrue("Expected legal content text to be visible.", body != null && body.trim().length() > 120);
		takeScreenshot(legalPage, evidenceDir, screenshotName, true);

		final String finalUrl = legalPage.url();
		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		}
		return finalUrl;
	}

	private void takeScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private void clickByVisibleText(final Page page, final String... textOptions) {
		PlaywrightException lastException = null;

		for (final String text : textOptions) {
			final Locator matches = page.getByText(text, new Page.GetByTextOptions().setExact(false));
			final long count = Math.min(matches.count(), 5);

			for (int index = 0; index < count; index++) {
				final Locator candidate = matches.nth(index);
				if (!isVisible(candidate)) {
					continue;
				}

				final Locator clickableAncestor = candidate
						.locator("xpath=ancestor-or-self::*[self::button or self::a or @role='button' or @role='menuitem'][1]");
				final Locator clickTarget = clickableAncestor.count() > 0 ? clickableAncestor.first() : candidate;
				try {
					clickTarget.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
					waitForUi(page);
					return;
				} catch (final PlaywrightException exception) {
					lastException = exception;
				}
			}
		}

		throw new AssertionError("Unable to click by visible text: " + String.join(", ", textOptions), lastException);
	}

	private void assertAnyTextVisible(final Page page, final String... textOptions) {
		assertTrue("Expected one of these texts to be visible: " + String.join(", ", textOptions),
				isAnyTextVisible(page, textOptions));
	}

	private boolean isAnyTextVisible(final Page page, final String... textOptions) {
		for (final String text : textOptions) {
			final Locator matches = page.getByText(text, new Page.GetByTextOptions().setExact(false));
			final long count = Math.min(matches.count(), 5);
			for (int index = 0; index < count; index++) {
				if (isVisible(matches.nth(index))) {
					return true;
				}
			}
		}

		return false;
	}

	private void assertSidebarVisible(final Page page) {
		final Locator aside = page.locator("aside");
		if (aside.count() > 0 && isVisible(aside.first())) {
			return;
		}

		final Locator nav = page.locator("nav");
		assertTrue("Expected sidebar navigation to be visible.", nav.count() > 0 && isVisible(nav.first()));
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final PlaywrightException exception) {
			return false;
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (final PlaywrightException exception) {
			// Ignore and continue with next stabilization step.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
		} catch (final PlaywrightException exception) {
			// Ignore: some SPAs keep active connections and never reach network idle.
		}

		page.waitForTimeout(750);
	}

	private String safeMessage(final Throwable throwable) {
		final String message = throwable.getMessage();
		if (message == null || message.isBlank()) {
			return throwable.getClass().getSimpleName();
		}
		if (message.length() <= 200) {
			return message;
		}
		return message.substring(0, 200) + "...";
	}

	private String getEnvOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
