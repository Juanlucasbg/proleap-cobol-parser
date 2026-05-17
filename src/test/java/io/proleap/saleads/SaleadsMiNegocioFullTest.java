package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
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
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String RUN_ENV = "RUN_SALEADS_MI_NEGOCIO_TEST";
	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String CDP_URL_ENV = "SALEADS_CDP_URL";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final long SHORT_TIMEOUT_MS = 4000;
	private static final long DEFAULT_TIMEOUT_MS = 15000;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		Assume.assumeTrue("Set RUN_SALEADS_MI_NEGOCIO_TEST=true to run this E2E workflow.",
				"true".equalsIgnoreCase(System.getenv(RUN_ENV)));

		final LinkedHashMap<String, Boolean> report = initializeReport();
		final List<String> failures = new ArrayList<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final Path evidenceDirectory = createEvidenceDirectory();

		Playwright playwright = null;
		Browser browser = null;
		boolean closeBrowser = true;

		try {
			playwright = Playwright.create();
			final Session session = openSession(playwright);
			browser = session.browser;
			closeBrowser = session.closeBrowser;
			Page appPage = session.page;
			final BrowserContext context = session.context;

			try {
				appPage = loginWithGoogle(appPage, context, evidenceDirectory);
				report.put("Login", true);
			} catch (final Throwable error) {
				failures.add("Login: " + error.getMessage());
			}

			try {
				assertNotNullPage(appPage, "Mi Negocio menu");
				openMiNegocioMenu(appPage, evidenceDirectory);
				report.put("Mi Negocio menu", true);
			} catch (final Throwable error) {
				failures.add("Mi Negocio menu: " + error.getMessage());
			}

			try {
				assertNotNullPage(appPage, "Agregar Negocio modal");
				validateAgregarNegocioModal(appPage, evidenceDirectory);
				report.put("Agregar Negocio modal", true);
			} catch (final Throwable error) {
				failures.add("Agregar Negocio modal: " + error.getMessage());
			}

			try {
				assertNotNullPage(appPage, "Administrar Negocios view");
				openAdministrarNegocios(appPage, evidenceDirectory);
				report.put("Administrar Negocios view", true);
			} catch (final Throwable error) {
				failures.add("Administrar Negocios view: " + error.getMessage());
			}

			try {
				assertNotNullPage(appPage, "Información General");
				validateInformacionGeneral(appPage);
				report.put("Información General", true);
			} catch (final Throwable error) {
				failures.add("Información General: " + error.getMessage());
			}

			try {
				assertNotNullPage(appPage, "Detalles de la Cuenta");
				validateDetallesCuenta(appPage);
				report.put("Detalles de la Cuenta", true);
			} catch (final Throwable error) {
				failures.add("Detalles de la Cuenta: " + error.getMessage());
			}

			try {
				assertNotNullPage(appPage, "Tus Negocios");
				validateTusNegocios(appPage);
				report.put("Tus Negocios", true);
			} catch (final Throwable error) {
				failures.add("Tus Negocios: " + error.getMessage());
			}

			try {
				assertNotNullPage(appPage, "Términos y Condiciones");
				validateLegalPage(appPage, context, "Términos y Condiciones", "Términos y Condiciones",
						evidenceDirectory.resolve("05-terminos-y-condiciones.png"), legalUrls);
				report.put("Términos y Condiciones", true);
			} catch (final Throwable error) {
				failures.add("Términos y Condiciones: " + error.getMessage());
			}

			try {
				assertNotNullPage(appPage, "Política de Privacidad");
				validateLegalPage(appPage, context, "Política de Privacidad", "Política de Privacidad",
						evidenceDirectory.resolve("06-politica-de-privacidad.png"), legalUrls);
				report.put("Política de Privacidad", true);
			} catch (final Throwable error) {
				failures.add("Política de Privacidad: " + error.getMessage());
			}

			printFinalReport(report, legalUrls, evidenceDirectory);

			if (!failures.isEmpty()) {
				Assert.fail("SaleADS Mi Negocio workflow reported failures:\n - " + String.join("\n - ", failures));
			}
		} finally {
			if (browser != null && closeBrowser) {
				browser.close();
			}

			if (playwright != null) {
				playwright.close();
			}
		}
	}

	private Session openSession(final Playwright playwright) {
		final String cdpUrl = env(CDP_URL_ENV);

		if (cdpUrl != null) {
			final Browser browser = playwright.chromium().connectOverCDP(cdpUrl);
			final BrowserContext context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
			final Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(context.pages().size() - 1);
			page.bringToFront();
			waitForUiToLoad(page);
			return new Session(browser, context, page, false);
		}

		final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions();
		launchOptions.setHeadless(!"false".equalsIgnoreCase(env(HEADLESS_ENV)));

		final Browser browser = playwright.chromium().launch(launchOptions);
		final BrowserContext context = browser.newContext();
		final Page page = context.newPage();

		final String loginUrl = env(LOGIN_URL_ENV);
		Assert.assertTrue("Set SALEADS_LOGIN_URL when SALEADS_CDP_URL is not provided.",
				loginUrl != null && !loginUrl.isBlank());
		page.navigate(loginUrl);
		waitForUiToLoad(page);

		return new Session(browser, context, page, true);
	}

	private Page loginWithGoogle(final Page page, final BrowserContext context, final Path evidenceDirectory) {
		final int pagesBeforeClick = context.pages().size();
		clickByVisibleText(page, "Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
				"Continuar con Google", "Google");

		Page workingPage = page;
		final Page authPage = waitForNewPage(context, pagesBeforeClick, SHORT_TIMEOUT_MS);
		if (authPage != null) {
			workingPage = authPage;
			workingPage.bringToFront();
			waitForUiToLoad(workingPage);
		}

		clickIfVisible(workingPage, GOOGLE_ACCOUNT_EMAIL, SHORT_TIMEOUT_MS);
		if (workingPage != page) {
			clickIfVisible(page, GOOGLE_ACCOUNT_EMAIL, SHORT_TIMEOUT_MS);
		}

		waitForUiToLoad(page);
		final Page appPage = resolveAppPage(context, page, DEFAULT_TIMEOUT_MS);

		assertTrue("Main application interface did not appear after login.",
				hasAnyVisibleText(appPage, DEFAULT_TIMEOUT_MS, "Negocio", "Mi Negocio", "Dashboard", "Inicio"));
		assertTrue("Left sidebar navigation is not visible.", isSidebarVisible(appPage));

		takeScreenshot(appPage, evidenceDirectory.resolve("01-dashboard-loaded.png"), false);
		return appPage;
	}

	private void openMiNegocioMenu(final Page page, final Path evidenceDirectory) {
		clickIfVisible(page, "Negocio", SHORT_TIMEOUT_MS);
		clickByVisibleText(page, "Mi Negocio");

		assertTrue("Submenu did not expand with 'Agregar Negocio'.",
				hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "Agregar Negocio"));
		assertTrue("Submenu did not expand with 'Administrar Negocios'.",
				hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "Administrar Negocios"));

		takeScreenshot(page, evidenceDirectory.resolve("02-mi-negocio-menu-expanded.png"), false);
	}

	private void validateAgregarNegocioModal(final Page page, final Path evidenceDirectory) {
		clickByVisibleText(page, "Agregar Negocio");

		assertTrue("Modal title 'Crear Nuevo Negocio' is not visible.",
				hasAnyVisibleText(page, DEFAULT_TIMEOUT_MS, "Crear Nuevo Negocio"));
		assertTrue("Field 'Nombre del Negocio' is not visible.", hasNombreDelNegocioInput(page));
		assertTrue("Usage text 'Tienes 2 de 3 negocios' is not visible.",
				hasAnyVisibleText(page, DEFAULT_TIMEOUT_MS, "Tienes 2 de 3 negocios"));
		assertTrue("Button 'Cancelar' is not visible.", hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "Cancelar"));
		assertTrue("Button 'Crear Negocio' is not visible.", hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "Crear Negocio"));

		takeScreenshot(page, evidenceDirectory.resolve("03-agregar-negocio-modal.png"), false);

		final Locator nombreInput = firstVisibleLocator(page, SHORT_TIMEOUT_MS, page.getByLabel("Nombre del Negocio"),
				page.getByPlaceholder("Nombre del Negocio"), page.locator("input[name*='nombre']"));
		if (nombreInput != null) {
			nombreInput.click();
			nombreInput.fill("Negocio Prueba Automatizacion");
		}

		clickIfVisible(page, "Cancelar", SHORT_TIMEOUT_MS);
	}

	private void openAdministrarNegocios(final Page page, final Path evidenceDirectory) {
		if (!hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "Administrar Negocios")) {
			clickByVisibleText(page, "Mi Negocio");
		}

		clickByVisibleText(page, "Administrar Negocios");

		assertTrue("Section 'Información General' not found.",
				hasAnyVisibleText(page, DEFAULT_TIMEOUT_MS, "Información General"));
		assertTrue("Section 'Detalles de la Cuenta' not found.",
				hasAnyVisibleText(page, DEFAULT_TIMEOUT_MS, "Detalles de la Cuenta"));
		assertTrue("Section 'Tus Negocios' not found.", hasAnyVisibleText(page, DEFAULT_TIMEOUT_MS, "Tus Negocios"));
		assertTrue("Section 'Sección Legal' not found.", hasAnyVisibleText(page, DEFAULT_TIMEOUT_MS, "Sección Legal"));

		takeScreenshot(page, evidenceDirectory.resolve("04-administrar-negocios-full-page.png"), true);
	}

	private void validateInformacionGeneral(final Page page) {
		assertTrue("A user name label/value was not found in Información General.",
				hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "Nombre", "Usuario", "Name"));
		assertTrue("A user email was not found in Información General.", hasEmail(page));
		assertTrue("'BUSINESS PLAN' is not visible.", hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "BUSINESS PLAN"));
		assertTrue("'Cambiar Plan' button is not visible.", hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "Cambiar Plan"));
	}

	private void validateDetallesCuenta(final Page page) {
		assertTrue("'Cuenta creada' is not visible.", hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "Cuenta creada"));
		assertTrue("'Estado activo' is not visible.",
				hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "Estado activo", "Estado Activo"));
		assertTrue("'Idioma seleccionado' is not visible.",
				hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "Idioma seleccionado"));
	}

	private void validateTusNegocios(final Page page) {
		assertTrue("'Tus Negocios' section is not visible.", hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "Tus Negocios"));
		assertTrue("'Agregar Negocio' button is not visible.", hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "Agregar Negocio"));
		assertTrue("'Tienes 2 de 3 negocios' text is not visible.",
				hasAnyVisibleText(page, SHORT_TIMEOUT_MS, "Tienes 2 de 3 negocios"));

		final boolean hasList = page.locator("ul li").count() > 0 || page.locator("table tr").count() > 1
				|| page.locator("[role='listitem']").count() > 0;
		assertTrue("Business list content is not visible in 'Tus Negocios'.", hasList);
	}

	private void validateLegalPage(final Page appPage, final BrowserContext context, final String linkText,
			final String expectedHeading, final Path screenshotPath, final Map<String, String> legalUrls) {
		final int pagesBeforeClick = context.pages().size();
		final String appUrlBeforeClick = appPage.url();

		clickByVisibleText(appPage, linkText);

		Page legalPage = waitForNewPage(context, pagesBeforeClick, SHORT_TIMEOUT_MS);
		boolean openedNewTab = true;
		if (legalPage == null) {
			openedNewTab = false;
			legalPage = appPage;
			waitForUiToLoad(legalPage);
		} else {
			legalPage.bringToFront();
			waitForUiToLoad(legalPage);
		}

		assertTrue("Heading '" + expectedHeading + "' was not visible.",
				hasAnyVisibleText(legalPage, DEFAULT_TIMEOUT_MS, expectedHeading));

		final String bodyText = legalPage.locator("body").innerText();
		assertTrue("Legal page did not contain visible content text.", bodyText != null && bodyText.trim().length() > 120);

		takeScreenshot(legalPage, screenshotPath, true);
		legalUrls.put(linkText, legalPage.url());

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiToLoad(appPage);
			return;
		}

		try {
			appPage.goBack();
			waitForUiToLoad(appPage);
		} catch (final Throwable ignored) {
			appPage.navigate(appUrlBeforeClick);
			waitForUiToLoad(appPage);
		}
	}

	private LinkedHashMap<String, Boolean> initializeReport() {
		final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
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

	private void printFinalReport(final Map<String, Boolean> report, final Map<String, String> legalUrls,
			final Path evidenceDirectory) {
		System.out.println("== SaleADS Mi Negocio Final Report ==");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			final String status = entry.getValue() ? "PASS" : "FAIL";
			System.out.println(entry.getKey() + ": " + status);
		}

		if (!legalUrls.isEmpty()) {
			System.out.println("Captured legal URLs:");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(" - " + entry.getKey() + ": " + entry.getValue());
			}
		}

		System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());
	}

	private Page resolveAppPage(final BrowserContext context, final Page fallbackPage, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;

		while (System.currentTimeMillis() < deadline) {
			for (final Page candidate : context.pages()) {
				if (hasAnyVisibleText(candidate, 1000, "Negocio", "Mi Negocio", "Dashboard", "Inicio")
						&& isSidebarVisible(candidate)) {
					candidate.bringToFront();
					return candidate;
				}
			}
			sleep(250);
		}

		fallbackPage.bringToFront();
		return fallbackPage;
	}

	private void clickByVisibleText(final Page page, final String... texts) {
		for (final String text : texts) {
			final Locator locator = page.getByText(text).first();
			if (waitForVisible(locator, page, SHORT_TIMEOUT_MS)) {
				locator.click();
				waitForUiToLoad(page);
				return;
			}
		}

		throw new AssertionError("Unable to find clickable text: " + String.join(", ", texts));
	}

	private boolean clickIfVisible(final Page page, final String text, final long timeoutMs) {
		final Locator locator = page.getByText(text).first();
		if (!waitForVisible(locator, page, timeoutMs)) {
			return false;
		}

		locator.click();
		waitForUiToLoad(page);
		return true;
	}

	private boolean hasAnyVisibleText(final Page page, final long timeoutMs, final String... texts) {
		for (final String text : texts) {
			if (waitForVisible(page.getByText(text).first(), page, timeoutMs)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasEmail(final Page page) {
		final String body = page.locator("body").innerText();
		if (body == null) {
			return false;
		}

		return Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(body).find();
	}

	private boolean hasNombreDelNegocioInput(final Page page) {
		return firstVisibleLocator(page, SHORT_TIMEOUT_MS, page.getByLabel("Nombre del Negocio"),
				page.getByPlaceholder("Nombre del Negocio"), page.locator("input[name*='nombre']"),
				page.locator("input[placeholder*='Nombre']")) != null;
	}

	private Locator firstVisibleLocator(final Page page, final long timeoutMs, final Locator... locators) {
		for (final Locator locator : locators) {
			if (waitForVisible(locator.first(), page, timeoutMs)) {
				return locator.first();
			}
		}

		return null;
	}

	private boolean isSidebarVisible(final Page page) {
		return waitForVisible(page.locator("aside").first(), page, SHORT_TIMEOUT_MS)
				|| waitForVisible(page.locator("nav").first(), page, SHORT_TIMEOUT_MS);
	}

	private boolean waitForVisible(final Locator locator, final Page page, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			try {
				if (locator.count() > 0 && locator.isVisible()) {
					return true;
				}
			} catch (final Throwable ignored) {
				// ignored by design while polling for UI updates
			}
			sleep(250);
		}

		return false;
	}

	private Page waitForNewPage(final BrowserContext context, final int pageCountBefore, final long timeoutMs) {
		final long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			final List<Page> pages = context.pages();
			if (pages.size() > pageCountBefore) {
				return pages.get(pages.size() - 1);
			}
			sleep(200);
		}
		return null;
	}

	private void waitForUiToLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (final Throwable ignored) {
			// ignored by design (some SPA transitions don't trigger load states)
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (final Throwable ignored) {
			// ignored by design (long polling can keep network idle from occurring)
		}

		sleep(800);
	}

	private void takeScreenshot(final Page page, final Path screenshotPath, final boolean fullPage) {
		ensureParentExists(screenshotPath);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private Path createEvidenceDirectory() {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path directory = Paths.get("target", "saleads-evidence", timestamp);
		ensureDirectoryExists(directory);
		return directory;
	}

	private void ensureDirectoryExists(final Path directory) {
		try {
			Files.createDirectories(directory);
		} catch (final Exception error) {
			throw new RuntimeException("Unable to create directory: " + directory, error);
		}
	}

	private void ensureParentExists(final Path filePath) {
		final Path parent = filePath.getParent();
		if (parent != null) {
			ensureDirectoryExists(parent);
		}
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for UI update.", interrupted);
		}
	}

	private String env(final String key) {
		final String value = System.getenv(key);
		if (value == null) {
			return null;
		}
		return value.trim();
	}

	private void assertNotNullPage(final Page page, final String stepName) {
		Assert.assertNotNull("Missing browser page before step: " + stepName, page);
	}

	private static class Session {
		private final Browser browser;
		private final BrowserContext context;
		private final Page page;
		private final boolean closeBrowser;

		private Session(final Browser browser, final BrowserContext context, final Page page, final boolean closeBrowser) {
			this.browser = browser;
			this.context = context;
			this.page = page;
			this.closeBrowser = closeBrowser;
		}
	}
}
