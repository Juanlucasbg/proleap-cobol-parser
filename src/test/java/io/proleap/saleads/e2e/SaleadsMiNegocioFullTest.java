package io.proleap.saleads.e2e;

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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final double DEFAULT_TIMEOUT_MS = 15000;
	private static final double SHORT_TIMEOUT_MS = 5000;
	private static final String[] REPORT_FIELDS = new String[] {
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad"
	};

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		Assume.assumeTrue("Set SALEADS_RUN_E2E=true to execute this UI workflow test.",
				Boolean.parseBoolean(env("SALEADS_RUN_E2E", "false")));

		final String loginUrl = firstNonBlank(
				System.getProperty("saleads.loginUrl"),
				System.getenv("SALEADS_LOGIN_URL"),
				System.getenv("SALEADS_BASE_URL"));
		Assume.assumeTrue("Provide SALEADS_LOGIN_URL (or SALEADS_BASE_URL / -Dsaleads.loginUrl).",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(env("HEADLESS", "true"));
		final Map<String, Boolean> report = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final Path evidenceDir = createEvidenceDir();

		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium().launch(
						new BrowserType.LaunchOptions().setHeadless(headless).setTimeout(DEFAULT_TIMEOUT_MS));
				BrowserContext context = browser.newContext(
						new Browser.NewContextOptions().setViewportSize(1600, 1200).setIgnoreHTTPSErrors(true))) {

			final Page page = context.newPage();
			page.navigate(loginUrl);
			waitForUi(page);

			runStep("Login", report, failures, () -> {
				loginWithGoogleIfNeeded(page, context);
				Assert.assertTrue("Main interface should be visible after login.", isMainInterfaceVisible(page));
				Assert.assertTrue("Left sidebar should be visible after login.", isLeftSidebarVisible(page));
				takeScreenshot(page, evidenceDir, "01_dashboard_loaded", true);
			});

			runStep("Mi Negocio menu", report, failures, () -> {
				openMiNegocioMenu(page);
				Assert.assertTrue("'Agregar Negocio' should be visible.", isTextVisible(page, "Agregar Negocio"));
				Assert.assertTrue("'Administrar Negocios' should be visible.",
						isTextVisible(page, "Administrar Negocios"));
				takeScreenshot(page, evidenceDir, "02_mi_negocio_menu_expanded", true);
			});

			runStep("Agregar Negocio modal", report, failures, () -> {
				openMiNegocioMenu(page);
				clickVisibleText(page, "Agregar Negocio");
				waitForVisibleText(page, "Crear Nuevo Negocio", DEFAULT_TIMEOUT_MS);
				Assert.assertTrue("Modal title should be visible.",
						isTextVisible(page, "Crear Nuevo Negocio"));
				Assert.assertTrue("'Nombre del Negocio' input should exist.", businessNameInput(page) != null);
				Assert.assertTrue("'Tienes 2 de 3 negocios' should be visible.",
						isTextVisible(page, "Tienes 2 de 3 negocios"));
				Assert.assertTrue("'Cancelar' button should be visible.", isTextVisible(page, "Cancelar"));
				Assert.assertTrue("'Crear Negocio' button should be visible.", isTextVisible(page, "Crear Negocio"));

				final Locator businessNameInput = businessNameInput(page);
				if (businessNameInput != null) {
					businessNameInput.click();
					waitForUi(page);
					businessNameInput.fill("Negocio Prueba Automatización");
				}

				takeScreenshot(page, evidenceDir, "03_agregar_negocio_modal", true);
				clickVisibleText(page, "Cancelar");
			});

			runStep("Administrar Negocios view", report, failures, () -> {
				openMiNegocioMenu(page);
				clickVisibleText(page, "Administrar Negocios");
				waitForUi(page);

				Assert.assertTrue("'Información General' section should be visible.",
						isTextVisible(page, "Información General"));
				Assert.assertTrue("'Detalles de la Cuenta' section should be visible.",
						isTextVisible(page, "Detalles de la Cuenta"));
				Assert.assertTrue("'Tus Negocios' section should be visible.", isTextVisible(page, "Tus Negocios"));
				Assert.assertTrue("'Sección Legal' section should be visible.", isTextVisible(page, "Sección Legal"));
				takeScreenshot(page, evidenceDir, "04_administrar_negocios_view", true);
			});

			runStep("Información General", report, failures, () -> {
				Assert.assertTrue("User name should be visible.", hasVisibleUserProfileBlock(page));
				Assert.assertTrue("User email should be visible.", hasVisibleEmail(page));
				Assert.assertTrue("'BUSINESS PLAN' should be visible.", isTextVisible(page, "BUSINESS PLAN"));
				Assert.assertTrue("'Cambiar Plan' should be visible.", isTextVisible(page, "Cambiar Plan"));
			});

			runStep("Detalles de la Cuenta", report, failures, () -> {
				Assert.assertTrue("'Cuenta creada' should be visible.", isTextVisible(page, "Cuenta creada"));
				Assert.assertTrue("'Estado activo' should be visible.", isTextVisible(page, "Estado activo"));
				Assert.assertTrue("'Idioma seleccionado' should be visible.",
						isTextVisible(page, "Idioma seleccionado"));
			});

			runStep("Tus Negocios", report, failures, () -> {
				Assert.assertTrue("Business list should be visible.", isTextVisible(page, "Tus Negocios"));
				Assert.assertTrue("'Agregar Negocio' button should exist.",
						isTextVisible(page, "Agregar Negocio"));
				Assert.assertTrue("'Tienes 2 de 3 negocios' should be visible.",
						isTextVisible(page, "Tienes 2 de 3 negocios"));
			});

			runStep("Términos y Condiciones", report, failures, () -> {
				final String finalUrl = validateLegalDocument(page, context, evidenceDir,
						"Términos y Condiciones",
						"Términos y Condiciones",
						"08_terminos_y_condiciones");
				legalUrls.put("Términos y Condiciones", finalUrl);
			});

			runStep("Política de Privacidad", report, failures, () -> {
				final String finalUrl = validateLegalDocument(page, context, evidenceDir,
						"Política de Privacidad",
						"Política de Privacidad",
						"09_politica_de_privacidad");
				legalUrls.put("Política de Privacidad", finalUrl);
			});
		}

		final String reportText = Arrays.stream(REPORT_FIELDS)
				.map(field -> field + ": " + (report.getOrDefault(field, false) ? "PASS" : "FAIL"))
				.collect(Collectors.joining(System.lineSeparator()));

		System.out.println("=== saleads_mi_negocio_full_test report ===");
		System.out.println(reportText);
		if (!legalUrls.isEmpty()) {
			System.out.println("--- Final legal URLs ---");
			legalUrls.forEach((key, value) -> System.out.println(key + ": " + value));
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());

		Assert.assertTrue("Workflow has failing validations:\n" + reportText + "\nFailures:\n"
				+ String.join(System.lineSeparator(), failures), failures.isEmpty());
	}

	private static void loginWithGoogleIfNeeded(final Page page, final BrowserContext context) {
		if (isMainInterfaceVisible(page)) {
			return;
		}

		final Locator loginButton = firstVisibleText(page,
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Login con Google",
				"Continuar con Google",
				"Google");
		Assert.assertNotNull("Could not find Google login button.", loginButton);

		final Page googlePage = clickAndCaptureNewPage(context, page, loginButton);
		if (googlePage != null) {
			waitForUi(googlePage);
			selectGoogleAccountIfVisible(googlePage, GOOGLE_ACCOUNT_EMAIL);
			page.bringToFront();
		} else {
			selectGoogleAccountIfVisible(page, GOOGLE_ACCOUNT_EMAIL);
		}

		waitForUi(page);
	}

	private static void selectGoogleAccountIfVisible(final Page page, final String accountEmail) {
		final Locator accountOption = textLocator(page, accountEmail);
		if (isLocatorVisible(accountOption, SHORT_TIMEOUT_MS)) {
			accountOption.click();
			waitForUi(page);
		}
	}

	private static String validateLegalDocument(
			final Page appPage,
			final BrowserContext context,
			final Path evidenceDir,
			final String linkText,
			final String headingText,
			final String screenshotName) {

		final Locator link = firstVisibleText(appPage, linkText);
		Assert.assertNotNull("Could not find legal link: " + linkText, link);

		final Page openedPage = clickAndCaptureNewPage(context, appPage, link);
		final Page target = openedPage != null ? openedPage : appPage;
		waitForUi(target);

		Assert.assertTrue("Legal heading should be visible: " + headingText, isTextVisible(target, headingText));
		final String bodyText = target.locator("body").innerText();
		Assert.assertTrue("Legal content text should be visible.",
				bodyText != null && bodyText.replaceAll("\\s+", " ").trim().length() > 120);

		takeScreenshot(target, evidenceDir, screenshotName, true);
		final String finalUrl = target.url();

		if (openedPage != null) {
			openedPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			try {
				appPage.goBack();
				waitForUi(appPage);
			} catch (final PlaywrightException ignored) {
				// If history navigation is unavailable, keep current page and continue.
			}
		}

		return finalUrl;
	}

	private static void openMiNegocioMenu(final Page page) {
		if (!isTextVisible(page, "Mi Negocio")) {
			clickVisibleText(page, "Negocio");
		}

		if (!isTextVisible(page, "Agregar Negocio") || !isTextVisible(page, "Administrar Negocios")) {
			clickVisibleText(page, "Mi Negocio");
		}

		Assert.assertTrue("Mi Negocio submenu should be expanded.",
				isTextVisible(page, "Agregar Negocio") && isTextVisible(page, "Administrar Negocios"));
	}

	private static Locator businessNameInput(final Page page) {
		final List<Locator> candidates = Arrays.asList(
				page.getByLabel(Pattern.compile("(?i).*Nombre del Negocio.*")).first(),
				page.locator("input[placeholder*='Nombre del Negocio']").first(),
				page.locator("input[name*='nombre']").first(),
				page.locator("input[id*='nombre']").first());

		for (final Locator candidate : candidates) {
			if (candidate.count() > 0 && isLocatorVisible(candidate, SHORT_TIMEOUT_MS)) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean isMainInterfaceVisible(final Page page) {
		return isLeftSidebarVisible(page)
				|| isTextVisible(page, "Mi Negocio")
				|| isTextVisible(page, "Negocio");
	}

	private static boolean isLeftSidebarVisible(final Page page) {
		try {
			return page.locator("aside").first().isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static boolean hasVisibleUserProfileBlock(final Page page) {
		return isTextVisible(page, "Información General") && page.locator("img, [data-testid*='avatar']").count() > 0;
	}

	private static boolean hasVisibleEmail(final Page page) {
		final String body = page.locator("body").innerText();
		return body != null && body.matches("(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*");
	}

	private static Page clickAndCaptureNewPage(final BrowserContext context, final Page page, final Locator locator) {
		try {
			return context.waitForPage(() -> {
				locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
				waitForUi(page);
			}, new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			return null;
		}
	}

	private static void clickVisibleText(final Page page, final String... texts) {
		final Locator target = firstVisibleText(page, texts);
		Assert.assertNotNull("Could not find clickable text in " + Arrays.toString(texts), target);
		target.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUi(page);
	}

	private static Locator firstVisibleText(final Page page, final String... texts) {
		for (final String text : texts) {
			final Locator locator = textLocator(page, text);
			if (isLocatorVisible(locator, SHORT_TIMEOUT_MS)) {
				return locator;
			}
		}
		return null;
	}

	private static Locator textLocator(final Page page, final String text) {
		return page.getByText(Pattern.compile("(?i).*" + Pattern.quote(text) + ".*")).first();
	}

	private static void waitForVisibleText(final Page page, final String text, final double timeoutMs) {
		textLocator(page, text).waitFor(
				new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
	}

	private static boolean isTextVisible(final Page page, final String text) {
		return isLocatorVisible(textLocator(page, text), SHORT_TIMEOUT_MS);
	}

	private static boolean isLocatorVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout(timeoutMs));
			return locator.isVisible();
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final PlaywrightException ignored) {
			// Some UI updates are not full navigations.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// NETWORKIDLE may not be reached on apps with background polling.
		}

		page.waitForTimeout(350);
	}

	private static Path createEvidenceDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		final Path dir = Paths.get("target", "saleads-evidence", "saleads_mi_negocio_full_test_" + timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private static void takeScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(evidenceDir.resolve(fileName + ".png"))
				.setFullPage(fullPage));
	}

	private static void runStep(
			final String name,
			final Map<String, Boolean> report,
			final List<String> failures,
			final Runnable stepBody) {
		try {
			stepBody.run();
			report.put(name, true);
		} catch (final Throwable t) {
			report.put(name, false);
			final String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
			failures.add(name + ": " + message);
		}
	}

	private static String env(final String key, final String fallback) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}
}
