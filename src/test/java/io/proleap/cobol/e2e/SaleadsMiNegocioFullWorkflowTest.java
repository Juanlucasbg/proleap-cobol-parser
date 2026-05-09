package io.proleap.cobol.e2e;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final int DEFAULT_TIMEOUT_MS = 20000;
	private static final int SHORT_TIMEOUT_MS = 2500;
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String loginUrl = getEnv("SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL to the login page for the current SaleADS environment before running this test.",
				loginUrl != null && !loginUrl.isBlank());

		final String googleAccountEmail = getEnvOrDefault("SALEADS_GOOGLE_ACCOUNT_EMAIL", DEFAULT_GOOGLE_ACCOUNT);
		final boolean headless = Boolean.parseBoolean(getEnvOrDefault("SALEADS_HEADLESS", "true"));
		final Path userDataDir = Path.of(getEnvOrDefault("SALEADS_USER_DATA_DIR", "target/playwright/saleads-user-data"));
		final Path evidenceDir = Path
				.of("target", "saleads-evidence", "mi-negocio-full-" + LocalDateTime.now().format(TIMESTAMP_FORMAT));

		Files.createDirectories(userDataDir);
		Files.createDirectories(evidenceDir);

		final Map<String, Boolean> finalReport = initFinalReport();
		final List<String> failures = new ArrayList<>();
		final List<String> legalUrls = new ArrayList<>();

		try (Playwright playwright = Playwright.create();
				BrowserContext context = playwright.chromium().launchPersistentContext(userDataDir,
						new BrowserType.LaunchPersistentContextOptions().setHeadless(headless).setViewportSize(1440, 900))) {

			Page appPage = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
			appPage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
			appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(appPage);

			runStep("Login", finalReport, failures, appPage, evidenceDir, () -> {
				loginWithGoogle(appPage, context, googleAccountEmail);
				assertLeftSidebarVisible(appPage);
				assertAnyTextVisible(appPage, "Mi Negocio", "Negocio");
				takeScreenshot(appPage, evidenceDir, "01-dashboard-loaded", true);
			});

			runStep("Mi Negocio menu", finalReport, failures, appPage, evidenceDir, () -> {
				openMiNegocioMenu(appPage);
				assertAnyTextVisible(appPage, "Agregar Negocio");
				assertAnyTextVisible(appPage, "Administrar Negocios");
				takeScreenshot(appPage, evidenceDir, "02-mi-negocio-menu-expanded", false);
			});

			runStep("Agregar Negocio modal", finalReport, failures, appPage, evidenceDir, () -> {
				clickByVisibleText(appPage, "Agregar Negocio");
				assertAnyTextVisible(appPage, "Crear Nuevo Negocio");
				assertAnyTextVisible(appPage, "Nombre del Negocio");
				assertAnyTextVisible(appPage, "Tienes 2 de 3 negocios");
				assertAnyTextVisible(appPage, "Cancelar");
				assertAnyTextVisible(appPage, "Crear Negocio");
				takeScreenshot(appPage, evidenceDir, "03-agregar-negocio-modal", false);
				fillBusinessNameAndCloseModal(appPage);
			});

			runStep("Administrar Negocios view", finalReport, failures, appPage, evidenceDir, () -> {
				ensureMiNegocioSubmenuVisible(appPage);
				clickByVisibleText(appPage, "Administrar Negocios");
				assertAnyTextVisible(appPage, "Información General");
				assertAnyTextVisible(appPage, "Detalles de la Cuenta");
				assertAnyTextVisible(appPage, "Tus Negocios");
				assertAnyTextVisible(appPage, "Sección Legal");
				takeScreenshot(appPage, evidenceDir, "04-administrar-negocios-page", true);
			});

			runStep("Información General", finalReport, failures, appPage, evidenceDir, () -> {
				assertBusinessInfoSection(appPage);
			});

			runStep("Detalles de la Cuenta", finalReport, failures, appPage, evidenceDir, () -> {
				assertAnyTextVisible(appPage, "Cuenta creada");
				assertAnyTextVisible(appPage, "Estado activo");
				assertAnyTextVisible(appPage, "Idioma seleccionado");
			});

			runStep("Tus Negocios", finalReport, failures, appPage, evidenceDir, () -> {
				assertAnyTextVisible(appPage, "Tus Negocios");
				assertAnyTextVisible(appPage, "Agregar Negocio");
				assertAnyTextVisible(appPage, "Tienes 2 de 3 negocios");
			});

			runStep("Términos y Condiciones", finalReport, failures, appPage, evidenceDir, () -> {
				String termsUrl = validateLegalLink(appPage, context, evidenceDir, "Términos y Condiciones",
						"Términos y Condiciones", "08-terminos-y-condiciones");
				legalUrls.add("Términos y Condiciones URL: " + termsUrl);
			});

			runStep("Política de Privacidad", finalReport, failures, appPage, evidenceDir, () -> {
				String privacyUrl = validateLegalLink(appPage, context, evidenceDir, "Política de Privacidad",
						"Política de Privacidad", "09-politica-de-privacidad");
				legalUrls.add("Política de Privacidad URL: " + privacyUrl);
			});
		}

		printFinalReport(finalReport, legalUrls, evidenceDir);

		if (!failures.isEmpty()) {
			fail("SaleADS Mi Negocio full workflow failures:\n- " + String.join("\n- ", failures));
		}
	}

	private static void loginWithGoogle(final Page appPage, final BrowserContext context, final String googleAccountEmail) {
		final int pagesBeforeClick = context.pages().size();
		clickByVisibleText(appPage, "Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");

		appPage.waitForTimeout(1500);
		Page loginSurface = appPage;
		if (context.pages().size() > pagesBeforeClick) {
			loginSurface = context.pages().get(context.pages().size() - 1);
			waitForUi(loginSurface);
		}

		if (isTextVisible(loginSurface, googleAccountEmail, SHORT_TIMEOUT_MS)) {
			clickByVisibleText(loginSurface, googleAccountEmail);
		}

		waitForUi(appPage);
		appPage.waitForTimeout(2500);
	}

	private static void openMiNegocioMenu(final Page appPage) {
		if (isTextVisible(appPage, "Mi Negocio", SHORT_TIMEOUT_MS)) {
			clickByVisibleText(appPage, "Mi Negocio");
		} else {
			clickByVisibleText(appPage, "Negocio");
			clickByVisibleText(appPage, "Mi Negocio");
		}
	}

	private static void ensureMiNegocioSubmenuVisible(final Page appPage) {
		if (!isTextVisible(appPage, "Administrar Negocios", SHORT_TIMEOUT_MS)
				|| !isTextVisible(appPage, "Agregar Negocio", SHORT_TIMEOUT_MS)) {
			openMiNegocioMenu(appPage);
		}
	}

	private static void fillBusinessNameAndCloseModal(final Page appPage) {
		Locator input = firstVisibleLocator(
				appPage.getByLabel(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
				appPage.getByPlaceholder(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE)),
				appPage.locator("input[name*='negocio' i], input[id*='negocio' i]"));

		if (input != null) {
			input.fill("Negocio Prueba Automatización");
		}

		clickByVisibleText(appPage, "Cancelar");
	}

	private static void assertBusinessInfoSection(final Page appPage) {
		assertAnyTextVisible(appPage, "Información General");
		assertVisibleAnyLocator("Expected user name to be visible.", appPage.locator("h1"), appPage.locator("h2"),
				appPage.locator("[data-testid*='name']"), appPage.locator("[class*='name']"));
		assertVisibleAnyLocator("Expected user email to be visible.", appPage.locator("text=/[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}/"));
		assertAnyTextVisible(appPage, "BUSINESS PLAN");
		assertAnyTextVisible(appPage, "Cambiar Plan");
	}

	private static String validateLegalLink(final Page appPage, final BrowserContext context, final Path evidenceDir,
			final String linkText, final String headingText, final String screenshotName) {
		final int pagesBefore = context.pages().size();
		final String appUrlBefore = appPage.url();

		clickByVisibleText(appPage, linkText);
		appPage.waitForTimeout(1500);

		Page targetPage = appPage;
		boolean openedNewTab = false;

		if (context.pages().size() > pagesBefore) {
			openedNewTab = true;
			targetPage = context.pages().get(context.pages().size() - 1);
			waitForUi(targetPage);
		} else {
			waitForUi(appPage);
		}

		assertAnyTextVisible(targetPage, headingText);
		assertVisibleAnyLocator("Expected legal content text to be visible.",
				targetPage.locator("main p, article p, section p, body p"));
		takeScreenshot(targetPage, evidenceDir, screenshotName, true);
		final String finalUrl = targetPage.url();

		if (openedNewTab) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!appUrlBefore.equals(finalUrl)) {
			appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private static void runStep(final String stepName, final Map<String, Boolean> finalReport, final List<String> failures,
			final Page page, final Path evidenceDir, final ThrowingRunnable action) {
		try {
			action.run();
			finalReport.put(stepName, true);
		} catch (Throwable throwable) {
			finalReport.put(stepName, false);
			failures.add(stepName + ": " + throwable.getMessage());
			try {
				takeScreenshot(page, evidenceDir, "failure-" + slugify(stepName), true);
			} catch (Throwable ignored) {
				// Best-effort screenshot on failure.
			}
		}
	}

	private static void clickByVisibleText(final Page page, final String... textOptions) {
		for (String text : textOptions) {
			Locator locator = firstVisibleLocator(
					page.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))),
					page.getByRole(AriaRole.LINK,
							new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))),
					page.getByRole(AriaRole.MENUITEM,
							new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))),
					page.getByText(text, new Page.GetByTextOptions().setExact(true)),
					page.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)));

			if (locator != null) {
				locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
				waitForUi(page);
				return;
			}
		}

		throw new AssertionError("Could not find clickable element using visible text options: " + String.join(", ", textOptions));
	}

	private static void assertLeftSidebarVisible(final Page page) {
		assertVisibleAnyLocator("Expected left sidebar navigation to be visible.", page.locator("aside"),
				page.locator("nav"), page.locator("[class*='sidebar' i]"), page.locator("[data-testid*='sidebar' i]"));
	}

	private static void assertAnyTextVisible(final Page page, final String... texts) {
		for (String text : texts) {
			if (isTextVisible(page, text, DEFAULT_TIMEOUT_MS)) {
				return;
			}
		}
		throw new AssertionError("None of the expected texts are visible: " + String.join(", ", texts));
	}

	private static void assertVisibleAnyLocator(final String message, final Locator... locators) {
		for (Locator locator : locators) {
			try {
				if (locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout(SHORT_TIMEOUT_MS))) {
					return;
				}
			} catch (PlaywrightException ignored) {
				// Try the next locator.
			}
		}
		throw new AssertionError(message);
	}

	private static boolean isTextVisible(final Page page, final String text, final int timeoutMs) {
		Locator locator = page.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE));
		try {
			return locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private static Locator firstVisibleLocator(final Locator... locators) {
		for (Locator locator : locators) {
			try {
				if (locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout(SHORT_TIMEOUT_MS))) {
					return locator.first();
				}
			} catch (PlaywrightException ignored) {
				// Try the next candidate.
			}
		}
		return null;
	}

	private static void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
			// Network idle may not occur on SPAs with background requests.
		}
	}

	private static void takeScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		try {
			Files.createDirectories(evidenceDir);
			page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName + ".png")).setFullPage(fullPage));
		} catch (IOException exception) {
			throw new RuntimeException("Could not write screenshot to " + evidenceDir, exception);
		}
	}

	private static String getEnv(final String key) {
		return System.getenv(key);
	}

	private static String getEnvOrDefault(final String key, final String fallback) {
		String value = System.getenv(key);
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String slugify(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static Map<String, Boolean> initFinalReport() {
		Map<String, Boolean> report = new LinkedHashMap<>();
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

	private static void printFinalReport(final Map<String, Boolean> report, final List<String> legalUrls,
			final Path evidenceDir) {
		System.out.println("=== SaleADS Mi Negocio Full Workflow Report ===");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		for (String legalUrl : legalUrls) {
			System.out.println(legalUrl);
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("===============================================");
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
