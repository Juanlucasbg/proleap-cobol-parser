package io.proleap.cobol.ui.saleads;

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
import java.util.Objects;
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

public class SaleadsMiNegocioFullWorkflowTest {

	private static final long DEFAULT_TIMEOUT_MS = 20_000;
	private static final long LONG_TIMEOUT_MS = 90_000;
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final boolean uiEnabled = readBooleanConfig("saleads.ui.enabled", "SALEADS_UI_ENABLED", false);
		Assume.assumeTrue(
				"Set -Dsaleads.ui.enabled=true (or SALEADS_UI_ENABLED=true) to run SaleADS UI automation.", uiEnabled);

		final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set -Dsaleads.login.url=<SaleADS login URL> (or SALEADS_LOGIN_URL) for environment-agnostic execution.",
				loginUrl != null && !loginUrl.isBlank());

		final String googleAccount = readConfigWithDefault("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT",
				DEFAULT_GOOGLE_ACCOUNT);
		final boolean headless = readBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true);
		final Path evidenceDir = createEvidenceDirectory(
				readConfigWithDefault("saleads.evidence.dir", "SALEADS_EVIDENCE_DIR", "target/saleads-evidence"));

		final Map<String, Boolean> report = initializeReport();
		final List<String> failures = new ArrayList<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
			final Page appPage = context.newPage();

			appPage.navigate(loginUrl);
			appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);

			executeStep(report, failures, "Login", () -> {
				performGoogleLogin(appPage, context, googleAccount);
				waitForSidebar(appPage, LONG_TIMEOUT_MS);
				captureScreenshot(appPage, evidenceDir.resolve("01-dashboard-loaded.png"), false);
			});

			executeStep(report, failures, "Mi Negocio menu", () -> {
				expandMiNegocioMenu(appPage);
				assertVisibleText(appPage, "(?i)agregar negocio", "Agregar Negocio");
				assertVisibleText(appPage, "(?i)administrar negocios", "Administrar Negocios");
				captureScreenshot(appPage, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
			});

			executeStep(report, failures, "Agregar Negocio modal", () -> {
				clickByVisibleText(appPage, "(?i)agregar negocio", "Agregar Negocio");
				assertVisibleText(appPage, "(?i)crear nuevo negocio", "Crear Nuevo Negocio modal title");
				assertVisibleText(appPage, "(?i)nombre del negocio", "Nombre del Negocio field");
				assertVisibleText(appPage, "(?i)tienes\\s*2\\s*de\\s*3\\s*negocios", "Tienes 2 de 3 negocios");
				assertVisibleText(appPage, "(?i)cancelar", "Cancelar button");
				assertVisibleText(appPage, "(?i)crear negocio", "Crear Negocio button");
				captureScreenshot(appPage, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

				fillBusinessNameIfPresent(appPage, "Negocio Prueba Automatizacion");
				clickByVisibleText(appPage, "(?i)cancelar", "Cancelar");
			});

			executeStep(report, failures, "Administrar Negocios view", () -> {
				closeCrearNegocioModalIfOpen(appPage);
				expandMiNegocioMenu(appPage);
				clickByVisibleText(appPage, "(?i)administrar negocios", "Administrar Negocios");
				assertVisibleText(appPage, "(?i)informaci[oó]n general", "Informacion General section");
				assertVisibleText(appPage, "(?i)detalles de la cuenta", "Detalles de la Cuenta section");
				assertVisibleText(appPage, "(?i)tus negocios", "Tus Negocios section");
				assertVisibleText(appPage, "(?i)secci[oó]n legal", "Seccion Legal section");
				captureScreenshot(appPage, evidenceDir.resolve("04-administrar-negocios-view.png"), true);
			});

			executeStep(report, failures, "Información General", () -> {
				assertVisibleText(appPage, "(?i)business\\s*plan", "BUSINESS PLAN text");
				assertVisibleText(appPage, "(?i)cambiar plan", "Cambiar Plan button");
				assertLikelyUserIdentityVisible(appPage);
			});

			executeStep(report, failures, "Detalles de la Cuenta", () -> {
				assertVisibleText(appPage, "(?i)cuenta creada", "Cuenta creada");
				assertVisibleText(appPage, "(?i)estado activo", "Estado activo");
				assertVisibleText(appPage, "(?i)idioma seleccionado", "Idioma seleccionado");
			});

			executeStep(report, failures, "Tus Negocios", () -> {
				assertVisibleText(appPage, "(?i)tus negocios", "Tus Negocios section");
				assertVisibleText(appPage, "(?i)agregar negocio", "Agregar Negocio button");
				assertVisibleText(appPage, "(?i)tienes\\s*2\\s*de\\s*3\\s*negocios", "Tienes 2 de 3 negocios");
			});

			executeStep(report, failures, "Términos y Condiciones", () -> {
				final String termsUrl = openLegalLinkAndValidate(appPage, context, "(?i)t[eé]rminos y condiciones",
						"(?i)t[eé]rminos y condiciones", evidenceDir.resolve("05-terminos-y-condiciones.png"));
				legalUrls.put("Términos y Condiciones", termsUrl);
			});

			executeStep(report, failures, "Política de Privacidad", () -> {
				final String privacyUrl = openLegalLinkAndValidate(appPage, context, "(?i)pol[ií]tica de privacidad",
						"(?i)pol[ií]tica de privacidad", evidenceDir.resolve("06-politica-de-privacidad.png"));
				legalUrls.put("Política de Privacidad", privacyUrl);
			});
		}

		printFinalReport(report, legalUrls, evidenceDir);

		if (report.containsValue(Boolean.FALSE)) {
			fail("SaleADS Mi Negocio workflow validation failed:\n" + String.join("\n", failures));
		}
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

	private static void executeStep(final Map<String, Boolean> report, final List<String> failures, final String stepName,
			final ThrowingRunnable step) {
		try {
			step.run();
			report.put(stepName, true);
		} catch (final Throwable throwable) {
			report.put(stepName, false);
			failures.add(stepName + " -> " + throwable.getMessage());
		}
	}

	private static void performGoogleLogin(final Page appPage, final BrowserContext context, final String accountEmail) {
		final String loginRegex = "(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)";
		Page googlePopup = null;
		boolean clickedLogin = false;

		try {
			googlePopup = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(6_000),
					() -> {
						clickByVisibleText(appPage, loginRegex, "Google login button");
					});
			clickedLogin = true;
		} catch (final PlaywrightException ignored) {
			// Callback click still runs when no popup is opened.
			clickedLogin = true;
		}

		if (!clickedLogin) {
			clickByVisibleText(appPage, loginRegex, "Google login button");
		}

		if (googlePopup != null) {
			googlePopup.waitForLoadState(LoadState.DOMCONTENTLOADED);
			selectGoogleAccountIfVisible(googlePopup, accountEmail);
		}

		selectGoogleAccountIfVisible(appPage, accountEmail);
		waitForSidebar(appPage, LONG_TIMEOUT_MS);
	}

	private static void waitForSidebar(final Page page, final long timeoutMs) {
		final long startedAt = System.currentTimeMillis();

		while (System.currentTimeMillis() - startedAt < timeoutMs) {
			try {
				if (isVisible(page.locator("aside").first(), 800)) {
					return;
				}
			} catch (final PlaywrightException ignored) {
				// Keep trying while the page settles.
			}

			if (isTextVisible(page, "(?i)(mi\\s*negocio|negocio|dashboard|inicio)", 800)) {
				return;
			}

			page.waitForTimeout(350);
		}

		throw new AssertionError("Main application interface did not load and sidebar was not detected.");
	}

	private static void expandMiNegocioMenu(final Page page) {
		if (isTextVisible(page, "(?i)agregar negocio", 1_000) && isTextVisible(page, "(?i)administrar negocios", 1_000)) {
			return;
		}

		if (isTextVisible(page, "(?i)mi\\s*negocio", 2_000)) {
			clickByVisibleText(page, "(?i)mi\\s*negocio", "Mi Negocio");
		} else {
			clickByVisibleText(page, "(?i)negocio", "Negocio");
			clickByVisibleText(page, "(?i)mi\\s*negocio", "Mi Negocio");
		}
	}

	private static void closeCrearNegocioModalIfOpen(final Page page) {
		if (isTextVisible(page, "(?i)crear nuevo negocio", 1_500)) {
			clickByVisibleText(page, "(?i)cancelar", "Cancelar");
		}
	}

	private static String openLegalLinkAndValidate(final Page appPage, final BrowserContext context, final String linkRegex,
			final String headingRegex, final Path screenshotPath) {
		Page targetPage = null;
		boolean clickedLink = false;

		try {
			targetPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(6_000),
					() -> {
						clickByVisibleText(appPage, linkRegex, "Legal link");
					});
			clickedLink = true;
		} catch (final PlaywrightException ignored) {
			targetPage = appPage;
			// Callback click still runs when no popup is opened.
			clickedLink = true;
		}

		if (!clickedLink) {
			clickByVisibleText(appPage, linkRegex, "Legal link");
			targetPage = appPage;
		}

		waitForPageReady(targetPage);
		assertVisibleText(targetPage, headingRegex, "Legal page heading");
		assertVisibleText(targetPage, "(?i)(informaci[oó]n|t[eé]rminos|condiciones|privacidad|datos personales)",
				"Legal content text");
		captureScreenshot(targetPage, screenshotPath, true);

		final String finalUrl = targetPage.url();

		if (!Objects.equals(targetPage, appPage)) {
			targetPage.close();
			appPage.bringToFront();
			waitForPageReady(appPage);
		} else if (!isTextVisible(appPage, "(?i)secci[oó]n legal", 1_000)) {
			appPage.goBack();
			waitForPageReady(appPage);
		}

		return finalUrl;
	}

	private static void selectGoogleAccountIfVisible(final Page page, final String accountEmail) {
		final Locator accountOption = page.getByText(Pattern.compile(Pattern.quote(accountEmail), Pattern.CASE_INSENSITIVE))
				.first();
		if (isVisible(accountOption, 5_000)) {
			accountOption.click();
			waitForPageReady(page);
		}
	}

	private static void assertLikelyUserIdentityVisible(final Page page) {
		if (!isTextVisible(page, "@", 3_000)) {
			throw new AssertionError("User email is not visible in Informacion General.");
		}

		final Locator profileIdentity = page.locator("text=/[A-Za-z]{2,}(\\s+[A-Za-z]{2,})?/");
		if (!isVisible(profileIdentity.first(), 3_000)) {
			throw new AssertionError("User name is not visible in Informacion General.");
		}
	}

	private static void fillBusinessNameIfPresent(final Page page, final String businessName) {
		Locator input = page.getByLabel(Pattern.compile("(?i)nombre del negocio")).first();
		if (!isVisible(input, 1_500)) {
			input = page.getByPlaceholder(Pattern.compile("(?i)nombre del negocio")).first();
		}

		if (isVisible(input, 1_500)) {
			input.click();
			input.fill(businessName);
			waitForPageReady(page);
		}
	}

	private static void clickByVisibleText(final Page page, final String regexText, final String description) {
		final Pattern pattern = Pattern.compile(regexText, Pattern.CASE_INSENSITIVE);
		final List<Locator> candidates = List.of(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(pattern)).first(),
				page.getByText(pattern).first());

		for (final Locator candidate : candidates) {
			if (isVisible(candidate, 2_500)) {
				candidate.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
				waitForPageReady(page);
				return;
			}
		}

		throw new AssertionError("Unable to find clickable element by visible text for: " + description);
	}

	private static void assertVisibleText(final Page page, final String regexText, final String description) {
		if (!isTextVisible(page, regexText, DEFAULT_TIMEOUT_MS)) {
			throw new AssertionError("Expected visible text missing: " + description);
		}
	}

	private static boolean isTextVisible(final Page page, final String regexText, final long timeoutMs) {
		final Locator locator = page.getByText(Pattern.compile(regexText, Pattern.CASE_INSENSITIVE)).first();
		return isVisible(locator, timeoutMs);
	}

	private static boolean isVisible(final Locator locator, final long timeoutMs) {
		try {
			locator.waitFor(
					new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(Math.max(timeoutMs, 500)));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private static void waitForPageReady(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
		} catch (final PlaywrightException ignored) {
			// Some routes keep long polling open; DOM readiness is enough in those cases.
		}
		page.waitForTimeout(400);
	}

	private static void captureScreenshot(final Page page, final Path destination, final boolean fullPage) {
		try {
			Files.createDirectories(destination.getParent());
			page.screenshot(new Page.ScreenshotOptions().setPath(destination).setFullPage(fullPage));
		} catch (final Exception exception) {
			throw new IllegalStateException("Unable to capture screenshot: " + destination, exception);
		}
	}

	private static String readConfig(final String sysPropKey, final String envKey) {
		final String sysPropValue = System.getProperty(sysPropKey);
		if (sysPropValue != null && !sysPropValue.isBlank()) {
			return sysPropValue.trim();
		}

		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}

		return null;
	}

	private static String readConfigWithDefault(final String sysPropKey, final String envKey, final String defaultValue) {
		final String configuredValue = readConfig(sysPropKey, envKey);
		return configuredValue == null ? defaultValue : configuredValue;
	}

	private static boolean readBooleanConfig(final String sysPropKey, final String envKey, final boolean defaultValue) {
		final String value = readConfig(sysPropKey, envKey);
		return value == null ? defaultValue : Boolean.parseBoolean(value);
	}

	private static Path createEvidenceDirectory(final String baseDirectory) throws Exception {
		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDirectory = Paths.get(baseDirectory, "saleads-mi-negocio-full-test-" + runId);
		Files.createDirectories(evidenceDirectory);
		return evidenceDirectory;
	}

	private static void printFinalReport(final Map<String, Boolean> report, final Map<String, String> legalUrls,
			final Path evidenceDir) {
		System.out.println();
		System.out.println("SaleADS Mi Negocio workflow final report:");
		report.forEach((step, passed) -> System.out.println(" - " + step + ": " + (passed ? "PASS" : "FAIL")));
		legalUrls.forEach((label, url) -> System.out.println(" - " + label + " URL: " + url));
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println();
	}
}
