package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import java.util.stream.Collectors;

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

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");
	private static final double SHORT_TIMEOUT_MS = 2_000;
	private static final double DEFAULT_TIMEOUT_MS = 12_000;
	private static final double POPUP_TIMEOUT_MS = 10_000;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run this external workflow test.",
				isEnabled("SALEADS_E2E_ENABLED"));

		final String loginUrl = getEnv("SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL to the current SaleADS login page for the target environment.",
				!loginUrl.isBlank());

		final Path artifactsDir = buildArtifactsDir();
		final Map<String, Boolean> report = new LinkedHashMap<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		final boolean headless = !"false".equalsIgnoreCase(getEnv("SALEADS_HEADLESS"));
		final BrowserTypeHolder browserTypeHolder = BrowserTypeHolder.fromEnv(getEnv("SALEADS_BROWSER"));

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = browserTypeHolder.launch(playwright, headless);
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setAcceptDownloads(true));
			final Page page = context.newPage();

			final boolean loginOk = runStep(report, "Login", () -> {
				page.navigate(loginUrl);
				waitForUi(page);

				final Locator signInWithGoogle = findClickableByText(page, "Sign in with Google", "Iniciar sesión con Google",
						"Continuar con Google", "Google");
				final Page googlePopup = clickAndCapturePopup(page, signInWithGoogle);

				if (googlePopup != null) {
					waitForUi(googlePopup);
					selectGoogleAccountIfVisible(googlePopup);
					waitForUi(page);
				} else {
					selectGoogleAccountIfVisible(page);
					waitForUi(page);
				}

				assertTrue("Main application interface should appear after login.",
						hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Negocio", "Dashboard", "Mi Negocio"));
				assertTrue("Left sidebar navigation should be visible.",
						isVisible(page.locator("aside").first(), DEFAULT_TIMEOUT_MS)
								|| isVisible(page.locator("nav").first(), DEFAULT_TIMEOUT_MS));
				captureScreenshot(page, artifactsDir, "01-dashboard.png", true);
			});

			boolean menuOk = false;
			if (loginOk) {
				menuOk = runStep(report, "Mi Negocio menu", () -> {
					clickIfVisible(page, "Negocio");
					clickText(page, "Mi Negocio");

					assertTrue("Submenu should show 'Agregar Negocio'.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Agregar Negocio"));
					assertTrue("Submenu should show 'Administrar Negocios'.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Administrar Negocios"));
					captureScreenshot(page, artifactsDir, "02-mi-negocio-menu-expandido.png", true);
				});
			} else {
				markStepBlocked(report, "Mi Negocio menu", "Blocked because login failed.");
			}

			boolean modalOk = false;
			if (menuOk) {
				modalOk = runStep(report, "Agregar Negocio modal", () -> {
					clickText(page, "Agregar Negocio");

					assertTrue("Modal title should be visible.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Crear Nuevo Negocio"));
					assertTrue("Label 'Nombre del Negocio' should be visible.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Nombre del Negocio"));
					assertTrue("Business quota text should be visible.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Tienes 2 de 3 negocios"));
					assertTrue("'Cancelar' button should be present.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Cancelar"));
					assertTrue("'Crear Negocio' button should be present.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Crear Negocio"));

					final Locator nombreNegocioInput = findModalInput(page);
					assertTrue("Input field for business name should exist.", isVisible(nombreNegocioInput, DEFAULT_TIMEOUT_MS));
					nombreNegocioInput.click();
					nombreNegocioInput.fill("Negocio Prueba Automatizacion");
					waitForUi(page);

					captureScreenshot(page, artifactsDir, "03-modal-crear-negocio.png", true);
					clickText(page, "Cancelar");
				});
			} else {
				markStepBlocked(report, "Agregar Negocio modal", "Blocked because Mi Negocio menu failed.");
			}

			boolean administrarOk = false;
			if (menuOk || modalOk) {
				administrarOk = runStep(report, "Administrar Negocios view", () -> {
					if (!hasVisibleText(page, SHORT_TIMEOUT_MS, "Administrar Negocios")) {
						clickText(page, "Mi Negocio");
					}

					clickText(page, "Administrar Negocios");
					assertTrue("Section 'Información General' should exist.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Información General"));
					assertTrue("Section 'Detalles de la Cuenta' should exist.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Detalles de la Cuenta"));
					assertTrue("Section 'Tus Negocios' should exist.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Tus Negocios"));
					assertTrue("Section 'Sección Legal' should exist.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Sección Legal", "Seccion Legal"));
					captureScreenshot(page, artifactsDir, "04-administrar-negocios.png", true);
				});
			} else {
				markStepBlocked(report, "Administrar Negocios view", "Blocked because previous navigation failed.");
			}

			if (administrarOk) {
				runStep(report, "Información General", () -> {
					final String bodyText = page.locator("body").innerText();
					assertTrue("User email should be visible.", EMAIL_PATTERN.matcher(bodyText).find());
					assertTrue("User name/label should be visible.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Nombre", "Usuario", "Perfil"));
					assertTrue("Plan text should be visible.", hasVisibleText(page, DEFAULT_TIMEOUT_MS, "BUSINESS PLAN"));
					assertTrue("Button 'Cambiar Plan' should be visible.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Cambiar Plan"));
				});

				runStep(report, "Detalles de la Cuenta", () -> {
					assertTrue("'Cuenta creada' should be visible.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Cuenta creada"));
					assertTrue("'Estado activo' should be visible.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Estado activo"));
					assertTrue("'Idioma seleccionado' should be visible.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Idioma seleccionado"));
				});

				runStep(report, "Tus Negocios", () -> {
					assertTrue("Section title should be visible.", hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Tus Negocios"));
					assertTrue("Button 'Agregar Negocio' should exist.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Agregar Negocio"));
					assertTrue("Quota text should be visible.",
							hasVisibleText(page, DEFAULT_TIMEOUT_MS, "Tienes 2 de 3 negocios"));

					final boolean hasBusinessList = isVisible(page.locator("table").first(), SHORT_TIMEOUT_MS)
							|| isVisible(page.locator("ul").first(), SHORT_TIMEOUT_MS)
							|| isVisible(page.locator("[role='list']").first(), SHORT_TIMEOUT_MS)
							|| page.locator("[role='listitem']").count() > 0
							|| page.locator("tbody tr").count() > 0;
					assertTrue("Business list should be visible.", hasBusinessList);
				});

				runStep(report, "Términos y Condiciones", () -> {
					final String termsUrl = validateLegalPageAndReturn(
							page,
							artifactsDir,
							"Términos y Condiciones",
							"Términos y Condiciones",
							"05-terminos-y-condiciones.png");
					legalUrls.put("Términos y Condiciones", termsUrl);
				});

				runStep(report, "Política de Privacidad", () -> {
					final String policyUrl = validateLegalPageAndReturn(
							page,
							artifactsDir,
							"Política de Privacidad",
							"Política de Privacidad",
							"06-politica-de-privacidad.png");
					legalUrls.put("Política de Privacidad", policyUrl);
				});
			} else {
				markStepBlocked(report, "Información General", "Blocked because Administrar Negocios view failed.");
				markStepBlocked(report, "Detalles de la Cuenta", "Blocked because Administrar Negocios view failed.");
				markStepBlocked(report, "Tus Negocios", "Blocked because Administrar Negocios view failed.");
				markStepBlocked(report, "Términos y Condiciones", "Blocked because Administrar Negocios view failed.");
				markStepBlocked(report, "Política de Privacidad", "Blocked because Administrar Negocios view failed.");
			}

			printReport(report, legalUrls, artifactsDir);
			assertAllStepsPassed(report);
		}
	}

	private static Path buildArtifactsDir() throws Exception {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final String configuredPath = getEnv("SALEADS_SCREENSHOT_DIR");
		final Path artifactsDir = configuredPath.isBlank()
				? Paths.get("target", "saleads-artifacts", timestamp)
				: Paths.get(configuredPath);
		Files.createDirectories(artifactsDir);
		return artifactsDir;
	}

	private static boolean runStep(final Map<String, Boolean> report, final String stepName, final StepAction action) {
		try {
			action.run();
			report.put(stepName, true);
			System.out.printf("[PASS] %s%n", stepName);
			return true;
		} catch (final Throwable throwable) {
			report.put(stepName, false);
			System.err.printf("[FAIL] %s - %s%n", stepName, throwable.getMessage());
			return false;
		}
	}

	private static void markStepBlocked(final Map<String, Boolean> report, final String stepName, final String reason) {
		report.put(stepName, false);
		System.err.printf("[FAIL] %s - %s%n", stepName, reason);
	}

	private static void assertAllStepsPassed(final Map<String, Boolean> report) {
		final List<String> failedSteps = report.entrySet()
				.stream()
				.filter(entry -> !entry.getValue())
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

		if (!failedSteps.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failed in steps: " + failedSteps);
		}
	}

	private static void printReport(
			final Map<String, Boolean> report,
			final Map<String, String> legalUrls,
			final Path artifactsDir) {
		System.out.println("========== SaleADS Mi Negocio Final Report ==========");
		report.forEach((key, value) -> System.out.printf("%s: %s%n", key, value ? "PASS" : "FAIL"));
		System.out.printf("Screenshots directory: %s%n", artifactsDir.toAbsolutePath());

		if (!legalUrls.isEmpty()) {
			System.out.println("Final legal URLs:");
			legalUrls.forEach((key, value) -> System.out.printf("- %s: %s%n", key, value));
		}
		System.out.println("=====================================================");
	}

	private static Locator findClickableByText(final Page page, final String... texts) {
		for (final String text : texts) {
			final String escaped = escapeForSelector(text);
			final String clickableSelector = String.format(
					"button:has-text(\"%s\"), a:has-text(\"%s\"), [role='button']:has-text(\"%s\"), [role='menuitem']:has-text(\"%s\"), [role='link']:has-text(\"%s\")",
					escaped,
					escaped,
					escaped,
					escaped,
					escaped);
			final Locator clickableLocator = page.locator(clickableSelector).first();
			if (isVisible(clickableLocator, SHORT_TIMEOUT_MS)) {
				return clickableLocator;
			}

			final Locator textLocator = page.locator("text=" + text).first();
			if (isVisible(textLocator, SHORT_TIMEOUT_MS)) {
				return textLocator;
			}
		}

		throw new AssertionError("Could not find clickable element with any text: " + String.join(", ", texts));
	}

	private static void clickText(final Page page, final String... texts) {
		final Locator locator = findClickableByText(page, texts);
		clickAndWait(page, locator);
	}

	private static void clickIfVisible(final Page page, final String text) {
		try {
			final Locator locator = findClickableByText(page, text);
			clickAndWait(page, locator);
		} catch (final AssertionError ignored) {
			// Intentional best effort for optional expansion click.
		}
	}

	private static Page clickAndCapturePopup(final Page page, final Locator locator) {
		try {
			return page.waitForPopup(
					() -> clickAndWait(page, locator),
					new Page.WaitForPopupOptions().setTimeout(POPUP_TIMEOUT_MS));
		} catch (final PlaywrightException popupNotOpened) {
			clickAndWait(page, locator);
			return null;
		}
	}

	private static void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUi(page);
	}

	private static void selectGoogleAccountIfVisible(final Page page) {
		final Locator accountLocator = page.locator("text=" + GOOGLE_ACCOUNT_EMAIL).first();
		if (isVisible(accountLocator, DEFAULT_TIMEOUT_MS)) {
			accountLocator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
			waitForUi(page);
		}
	}

	private static Locator findModalInput(final Page page) {
		final List<Locator> candidateInputs = new ArrayList<>();
		candidateInputs.add(page.locator("[role='dialog'] input[placeholder*='Nombre']").first());
		candidateInputs.add(page.locator("[role='dialog'] input[name*='nombre']").first());
		candidateInputs.add(page.locator("[role='dialog'] input[id*='nombre']").first());
		candidateInputs.add(page.locator("[role='dialog'] input").first());
		candidateInputs.add(page.locator(".modal input").first());

		for (final Locator candidate : candidateInputs) {
			if (isVisible(candidate, SHORT_TIMEOUT_MS)) {
				return candidate;
			}
		}

		throw new AssertionError("Could not locate a visible input inside the modal dialog.");
	}

	private static String validateLegalPageAndReturn(
			final Page appPage,
			final Path artifactsDir,
			final String linkText,
			final String expectedHeading,
			final String screenshotName) {
		final String appUrl = appPage.url();
		final Locator legalLink = findClickableByText(appPage, linkText);
		final Page legalPage = clickAndCapturePopup(appPage, legalLink);
		final Page targetPage = legalPage == null ? appPage : legalPage;

		waitForUi(targetPage);
		assertTrue("Expected legal heading should be visible: " + expectedHeading,
				hasVisibleText(targetPage, DEFAULT_TIMEOUT_MS, expectedHeading));
		final String legalText = targetPage.locator("body").innerText();
		assertTrue("Legal content text should be visible.", legalText != null && legalText.trim().length() > 80);

		captureScreenshot(targetPage, artifactsDir, screenshotName, true);
		final String finalUrl = targetPage.url();

		if (legalPage != null) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (appUrl != null && !appUrl.isBlank()) {
			appPage.navigate(appUrl);
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private static boolean hasVisibleText(final Page page, final double timeoutMs, final String... texts) {
		for (final String text : texts) {
			final Locator locator = page.locator("text=" + text).first();
			if (isVisible(locator, timeoutMs)) {
				return true;
			}
		}

		return false;
	}

	private static boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// If network is long-polling, continue after a short deterministic pause.
		}
		page.waitForTimeout(700);
	}

	private static void captureScreenshot(
			final Page page,
			final Path artifactsDir,
			final String fileName,
			final boolean fullPage) {
		final Path screenshotPath = artifactsDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		System.out.printf("Screenshot captured: %s%n", screenshotPath.toAbsolutePath());
	}

	private static String escapeForSelector(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static boolean isEnabled(final String envVarName) {
		return "true".equalsIgnoreCase(getEnv(envVarName));
	}

	private static String getEnv(final String envVarName) {
		final String value = System.getenv(envVarName);
		return value == null ? "" : value.trim();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}

	private enum BrowserTypeHolder {
		CHROMIUM,
		FIREFOX,
		WEBKIT;

		static BrowserTypeHolder fromEnv(final String configuredBrowser) {
			if ("firefox".equalsIgnoreCase(configuredBrowser)) {
				return FIREFOX;
			}
			if ("webkit".equalsIgnoreCase(configuredBrowser)) {
				return WEBKIT;
			}
			return CHROMIUM;
		}

		Browser launch(final Playwright playwright, final boolean headless) {
			final BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(headless);
			switch (this) {
			case FIREFOX:
				return playwright.firefox().launch(options);
			case WEBKIT:
				return playwright.webkit().launch(options);
			case CHROMIUM:
			default:
				return playwright.chromium().launch(options);
			}
		}
	}
}
