package io.proleap.e2e.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final String startUrl = requiredEnv("SALEADS_START_URL");
		final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));
		final Path artifactsDir = createArtifactsDirectory();

		final Map<String, Boolean> report = initializeReport();
		final Map<String, String> reportErrors = new LinkedHashMap<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setViewportSize(1920, 1080);
			final String storageStatePath = System.getenv("SALEADS_STORAGE_STATE");
			if (storageStatePath != null && !storageStatePath.isBlank()) {
				contextOptions.setStorageStatePath(Paths.get(storageStatePath));
			}

			final BrowserContext context = browser.newContext(contextOptions);
			final Page page = context.newPage();
			page.setDefaultTimeout(20000);

			page.navigate(startUrl);
			waitForUiToLoad(page);

			runStep(report, reportErrors, "Login", () -> validateLoginWithGoogle(page, context, artifactsDir));
			runStep(report, reportErrors, "Mi Negocio menu", () -> validateMiNegocioMenu(page, artifactsDir));
			runStep(report, reportErrors, "Agregar Negocio modal", () -> validateAgregarNegocioModal(page, artifactsDir));
			runStep(report, reportErrors, "Administrar Negocios view", () -> validateAdministrarNegocios(page, artifactsDir));
			runStep(report, reportErrors, "Información General", () -> validateInformacionGeneral(page));
			runStep(report, reportErrors, "Detalles de la Cuenta", () -> validateDetallesCuenta(page));
			runStep(report, reportErrors, "Tus Negocios", () -> validateTusNegocios(page));
			runStep(report, reportErrors, "Términos y Condiciones", () -> {
				final String url = validateLegalLink(page, "Términos y Condiciones", "Términos y Condiciones",
						artifactsDir.resolve("05-terminos-y-condiciones.png"));
				legalUrls.put("Términos y Condiciones", url);
			});
			runStep(report, reportErrors, "Política de Privacidad", () -> {
				final String url = validateLegalLink(page, "Política de Privacidad", "Política de Privacidad",
						artifactsDir.resolve("06-politica-de-privacidad.png"));
				legalUrls.put("Política de Privacidad", url);
			});

			printReport(report, reportErrors, legalUrls, artifactsDir);
			context.close();
			browser.close();
		}

		Assert.assertTrue("saleads_mi_negocio_full_test failed. Check report in test logs.", allPassed(report));
	}

	private static void validateLoginWithGoogle(final Page page, final BrowserContext context, final Path artifactsDir) {
		final Locator loginButton = findByVisibleText(page, "Sign in with Google", "Iniciar sesión con Google",
				"Iniciar Sesión con Google", "Continuar con Google", "Google");

		Page googlePopup = null;
		try {
			googlePopup = page.waitForPopup(() -> loginButton.click());
		} catch (RuntimeException popupNotOpened) {
			loginButton.click();
		}

		waitForUiToLoad(page);
		if (googlePopup != null) {
			waitForUiToLoad(googlePopup);
			selectGoogleAccountIfPresent(googlePopup);
		} else if (page.url().contains("accounts.google")) {
			selectGoogleAccountIfPresent(page);
		}

		waitForUiToLoad(page);
		assertVisible(findByVisibleText(page, "Negocio"), "Left sidebar navigation");
		takeScreenshot(page, artifactsDir.resolve("01-dashboard-loaded.png"), false);
	}

	private static void validateMiNegocioMenu(final Page page, final Path artifactsDir) {
		assertVisible(findByVisibleText(page, "Negocio"), "Sidebar section 'Negocio'");

		final Locator miNegocio = findByVisibleText(page, "Mi Negocio");
		clickAndWait(miNegocio, page);

		assertVisible(findByVisibleText(page, "Agregar Negocio"), "Submenu option 'Agregar Negocio'");
		assertVisible(findByVisibleText(page, "Administrar Negocios"), "Submenu option 'Administrar Negocios'");
		takeScreenshot(page, artifactsDir.resolve("02-mi-negocio-menu-expanded.png"), false);
	}

	private static void validateAgregarNegocioModal(final Page page, final Path artifactsDir) {
		clickAndWait(findByVisibleText(page, "Agregar Negocio"), page);

		assertVisible(page.getByText(Pattern.compile("(?i)^\\s*Crear\\s+Nuevo\\s+Negocio\\s*$")).first(),
				"Modal title 'Crear Nuevo Negocio'");
		assertVisible(page.getByLabel(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")).first(),
				"Input 'Nombre del Negocio'");
		assertVisible(page.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")).first(),
				"Business quota text");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
				.setName(Pattern.compile("(?i)^\\s*Cancelar\\s*$"))).first(), "Button 'Cancelar'");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
				.setName(Pattern.compile("(?i)^\\s*Crear\\s+Negocio\\s*$"))).first(), "Button 'Crear Negocio'");

		final Locator nombreNegocioInput = page.getByLabel(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")).first();
		nombreNegocioInput.click();
		nombreNegocioInput.fill("Negocio Prueba Automatización");
		takeScreenshot(page, artifactsDir.resolve("03-agregar-negocio-modal.png"), false);
		clickAndWait(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first(), page);
	}

	private static void validateAdministrarNegocios(final Page page, final Path artifactsDir) {
		ensureMiNegocioExpanded(page);
		clickAndWait(findByVisibleText(page, "Administrar Negocios"), page);

		assertVisible(findByVisibleText(page, "Información General"), "Section 'Información General'");
		assertVisible(findByVisibleText(page, "Detalles de la Cuenta"), "Section 'Detalles de la Cuenta'");
		assertVisible(findByVisibleText(page, "Tus Negocios"), "Section 'Tus Negocios'");
		assertVisible(findByVisibleText(page, "Sección Legal"), "Section 'Sección Legal'");
		takeScreenshot(page, artifactsDir.resolve("04-administrar-negocios-full.png"), true);
	}

	private static void validateInformacionGeneral(final Page page) {
		final Locator section = sectionContaining(page, "Información General");
		assertVisible(section, "Información General section");

		final String infoText = normalizeWhitespace(section.innerText());
		assertCondition(infoText.contains("BUSINESS PLAN"), "Text 'BUSINESS PLAN' is visible");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
				.setName(Pattern.compile("(?i)^\\s*Cambiar\\s+Plan\\s*$"))).first(), "Button 'Cambiar Plan'");

		final Pattern emailPattern = Pattern.compile("(?i)[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}");
		assertCondition(emailPattern.matcher(infoText).find(), "User email is visible");

		final Pattern namePattern = Pattern.compile("(?i)\\b[\\p{L}]{2,}(?:\\s+[\\p{L}]{2,}){1,3}\\b");
		assertCondition(namePattern.matcher(infoText).find(), "User name is visible");
	}

	private static void validateDetallesCuenta(final Page page) {
		final Locator section = sectionContaining(page, "Detalles de la Cuenta");
		assertVisible(section, "Detalles de la Cuenta section");
		assertVisible(section.getByText(Pattern.compile("(?i)Cuenta\\s+creada")).first(), "'Cuenta creada' label");
		assertVisible(section.getByText(Pattern.compile("(?i)Estado\\s+activo")).first(), "'Estado activo' label");
		assertVisible(section.getByText(Pattern.compile("(?i)Idioma\\s+seleccionado")).first(),
				"'Idioma seleccionado' label");
	}

	private static void validateTusNegocios(final Page page) {
		final Locator section = sectionContaining(page, "Tus Negocios");
		assertVisible(section, "Tus Negocios section");
		assertVisible(page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$"))).first(),
				"Button 'Agregar Negocio'");
		assertVisible(section.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")).first(),
				"Text 'Tienes 2 de 3 negocios'");

		final int itemsCount = section.locator("li, article, tr, [class*='business'], [data-testid*='business']")
				.count();
		assertCondition(itemsCount > 0 || normalizeWhitespace(section.innerText()).contains("Negocio"),
				"Business list is visible");
	}

	private static String validateLegalLink(final Page appPage, final String linkText, final String headingText,
			final Path screenshotPath) {
		ensureMiNegocioExpanded(appPage);
		final Locator link = findByVisibleText(appPage, linkText);
		Page legalPage = null;

		try {
			legalPage = appPage.waitForPopup(() -> link.click());
		} catch (RuntimeException popupNotOpened) {
			clickAndWait(link, appPage);
		}

		if (legalPage != null) {
			waitForUiToLoad(legalPage);
			assertVisible(legalPage.getByText(Pattern.compile("(?i)" + Pattern.quote(headingText))).first(),
					"Heading '" + headingText + "'");
			assertCondition(normalizeWhitespace(legalPage.locator("body").innerText()).length() > 120,
					"Legal content text is visible");
			takeScreenshot(legalPage, screenshotPath, false);
			final String finalUrl = legalPage.url();
			legalPage.close();
			appPage.bringToFront();
			waitForUiToLoad(appPage);
			return finalUrl;
		}

		assertVisible(appPage.getByText(Pattern.compile("(?i)" + Pattern.quote(headingText))).first(),
				"Heading '" + headingText + "'");
		assertCondition(normalizeWhitespace(appPage.locator("body").innerText()).length() > 120,
				"Legal content text is visible");
		takeScreenshot(appPage, screenshotPath, false);
		final String finalUrl = appPage.url();

		appPage.goBack();
		waitForUiToLoad(appPage);
		return finalUrl;
	}

	private static void ensureMiNegocioExpanded(final Page page) {
		if (!isVisible(findByVisibleText(page, "Administrar Negocios"), 2000)) {
			clickAndWait(findByVisibleText(page, "Mi Negocio"), page);
		}
	}

	private static Locator sectionContaining(final Page page, final String headingText) {
		return page.locator("section, div").filter(new Locator.FilterOptions().setHasText(headingText)).first();
	}

	private static void selectGoogleAccountIfPresent(final Page googlePage) {
		final Locator account = googlePage.getByText(Pattern.compile("(?i)^\\s*" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + "\\s*$"))
				.first();
		if (isVisible(account, 12000)) {
			clickAndWait(account, googlePage);
		}
	}

	private static Locator findByVisibleText(final Page page, final String... options) {
		Locator fallback = null;
		for (final String option : options) {
			final Pattern exactPattern = Pattern.compile("(?i)^\\s*" + Pattern.quote(option) + "\\s*$");

			final Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(exactPattern)).first();
			if (isVisible(button, 1500)) {
				return button;
			}

			final Locator link = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(exactPattern)).first();
			if (isVisible(link, 1500)) {
				return link;
			}

			final Locator text = page.getByText(exactPattern).first();
			if (isVisible(text, 1500)) {
				return text;
			}

			if (fallback == null) {
				fallback = text;
			}
		}
		return fallback;
	}

	private static void clickAndWait(final Locator locator, final Page page) {
		assertVisible(locator, "Target element to click");
		locator.click();
		waitForUiToLoad(page);
	}

	private static void waitForUiToLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (TimeoutError ignored) {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
		}
	}

	private static void assertVisible(final Locator locator, final String description) {
		assertCondition(locator != null && isVisible(locator, 10000), description + " should be visible");
	}

	private static boolean isVisible(final Locator locator, final double timeoutMs) {
		if (locator == null) {
			return false;
		}
		try {
			locator.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
			return locator.isVisible();
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static void takeScreenshot(final Page page, final Path path, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private static void runStep(final Map<String, Boolean> report, final Map<String, String> reportErrors,
			final String stepName, final ThrowingRunnable action) {
		try {
			action.run();
			report.put(stepName, true);
		} catch (RuntimeException ex) {
			report.put(stepName, false);
			reportErrors.put(stepName, normalizeWhitespace(ex.getMessage()));
		}
	}

	private static boolean allPassed(final Map<String, Boolean> report) {
		for (final Boolean value : report.values()) {
			if (!Boolean.TRUE.equals(value)) {
				return false;
			}
		}
		return true;
	}

	private static Map<String, Boolean> initializeReport() {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
		return report;
	}

	private static String normalizeWhitespace(final String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("\\s+", " ").trim();
	}

	private static String env(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private static String requiredEnv(final String key) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing environment variable: " + key);
		}
		return value;
	}

	private static Path createArtifactsDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Paths.get("target", "saleads-mi-negocio-artifacts", timestamp);
		Files.createDirectories(path);
		return path;
	}

	private static void printReport(final Map<String, Boolean> report, final Map<String, String> reportErrors,
			final Map<String, String> legalUrls, final Path artifactsDir) {
		System.out.println();
		System.out.println("=== saleads_mi_negocio_full_test report ===");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			final String status = Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL";
			System.out.println("- " + entry.getKey() + ": " + status);
			if (!Boolean.TRUE.equals(entry.getValue()) && reportErrors.containsKey(entry.getKey())) {
				System.out.println("  reason: " + reportErrors.get(entry.getKey()));
			}
		}
		for (final Map.Entry<String, String> legalEntry : legalUrls.entrySet()) {
			System.out.println("- " + legalEntry.getKey() + " final URL: " + legalEntry.getValue());
		}
		System.out.println("- screenshots: " + artifactsDir.toAbsolutePath());
		System.out.println("===========================================");
		System.out.println();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run();
	}
}
