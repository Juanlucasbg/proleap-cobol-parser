package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SaleAdsMiNegocioFullWorkflowTest {

	private static final String RUN_E2E_FLAG = "RUN_SALEADS_E2E";
	private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
	private static final String GOOGLE_ACCOUNT_ENV = "SALEADS_GOOGLE_ACCOUNT";
	private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-evidence");
	private static final Path REPORT_PATH = EVIDENCE_DIR.resolve("saleads-mi-negocio-report.txt");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		Assume.assumeTrue(
				"Skipping SaleADS E2E test. Set RUN_SALEADS_E2E=true to execute.",
				isEnabled(RUN_E2E_FLAG)
		);

		Files.createDirectories(EVIDENCE_DIR);

		final Map<String, String> statuses = new LinkedHashMap<>();
		statuses.put("Login", "FAIL");
		statuses.put("Mi Negocio menu", "FAIL");
		statuses.put("Agregar Negocio modal", "FAIL");
		statuses.put("Administrar Negocios view", "FAIL");
		statuses.put("Información General", "FAIL");
		statuses.put("Detalles de la Cuenta", "FAIL");
		statuses.put("Tus Negocios", "FAIL");
		statuses.put("Términos y Condiciones", "FAIL");
		statuses.put("Política de Privacidad", "FAIL");

		final Map<String, String> finalUrls = new LinkedHashMap<>();
		Throwable failure = null;

		try (Playwright playwright = Playwright.create()) {
			final boolean headless = Boolean.parseBoolean(getSetting(HEADLESS_ENV, "true"));
			try (Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			     BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900))) {
				final Page appPage = context.newPage();

				openLoginPage(appPage);
				waitForUi(appPage);

				// Step 1 - Login with Google
				final Locator googleLoginButton = requireVisible(
						appPage,
						"Google login button",
						"button:has-text(\"Sign in with Google\")",
						"button:has-text(\"Iniciar sesión con Google\")",
						"button:has-text(\"Google\")",
						"text=/Sign in with Google/i",
						"text=/Iniciar sesi[oó]n con Google/i"
				);
				clickAndWait(appPage, googleLoginButton);
				maybeSelectGoogleAccount(context, getSetting(GOOGLE_ACCOUNT_ENV, DEFAULT_GOOGLE_ACCOUNT));

				requireVisible(
						appPage,
						"Main application interface",
						"aside",
						"nav",
						"text=/Negocio/i"
				);
				requireVisible(
						appPage,
						"Left sidebar navigation",
						"aside",
						"nav"
				);
				captureScreenshot(appPage, "01-dashboard-loaded.png", false);
				statuses.put("Login", "PASS");

				// Step 2 - Open Mi Negocio menu
				final Locator negocioMenu = requireVisible(
						appPage,
						"Negocio menu entry",
						"text=/^\\s*Negocio\\s*$/i",
						"button:has-text(\"Negocio\")",
						"a:has-text(\"Negocio\")"
				);
				clickAndWait(appPage, negocioMenu);

				requireVisible(
						appPage,
						"Agregar Negocio option",
						"text=/Agregar Negocio/i"
				);
				requireVisible(
						appPage,
						"Administrar Negocios option",
						"text=/Administrar Negocios/i"
				);
				captureScreenshot(appPage, "02-mi-negocio-menu-expanded.png", false);
				statuses.put("Mi Negocio menu", "PASS");

				// Step 3 - Validate Agregar Negocio modal
				final Locator agregarNegocio = requireVisible(
						appPage,
						"Agregar Negocio option",
						"text=/Agregar Negocio/i"
				);
				clickAndWait(appPage, agregarNegocio);

				requireVisible(appPage, "Crear Nuevo Negocio modal title", "text=/Crear Nuevo Negocio/i");
				final Locator nombreNegocioInput = requireVisible(
						appPage,
						"Nombre del Negocio input",
						"input[placeholder*=\"Nombre del Negocio\"]",
						"input[name*=\"nombre\"]",
						"input[aria-label*=\"Nombre del Negocio\"]"
				);
				requireVisible(appPage, "Business quota text", "text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i");
				requireVisible(appPage, "Cancelar button", "button:has-text(\"Cancelar\")");
				requireVisible(appPage, "Crear Negocio button", "button:has-text(\"Crear Negocio\")");
				captureScreenshot(appPage, "03-agregar-negocio-modal.png", false);

				nombreNegocioInput.fill("Negocio Prueba Automatización");
				clickAndWait(appPage, requireVisible(appPage, "Cancelar button", "button:has-text(\"Cancelar\")"));
				statuses.put("Agregar Negocio modal", "PASS");

				// Step 4 - Open Administrar Negocios
				ensureMiNegocioExpanded(appPage);
				clickAndWait(
						appPage,
						requireVisible(appPage, "Administrar Negocios option", "text=/Administrar Negocios/i")
				);

				requireVisible(appPage, "Información General section", "text=/Informaci[oó]n General/i");
				requireVisible(appPage, "Detalles de la Cuenta section", "text=/Detalles de la Cuenta/i");
				requireVisible(appPage, "Tus Negocios section", "text=/Tus Negocios/i");
				requireVisible(appPage, "Sección Legal section", "text=/Secci[oó]n Legal/i");
				captureScreenshot(appPage, "04-administrar-negocios-page.png", true);
				statuses.put("Administrar Negocios view", "PASS");

				// Step 5 - Validate Información General
				requireVisible(appPage, "Información General section", "text=/Informaci[oó]n General/i");
				requireVisible(appPage, "BUSINESS PLAN text", "text=/BUSINESS PLAN/i");
				requireVisible(appPage, "Cambiar Plan button", "button:has-text(\"Cambiar Plan\")");
				final String pageText = appPage.locator("body").innerText();
				Assert.assertTrue("User email should be visible.", pageText.matches("(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*"));
				Assert.assertTrue("User name should be visible.", containsLikelyUserName(pageText));
				statuses.put("Información General", "PASS");

				// Step 6 - Validate Detalles de la Cuenta
				requireVisible(appPage, "'Cuenta creada' text", "text=/Cuenta creada/i");
				requireVisible(appPage, "'Estado activo' text", "text=/Estado activo/i");
				requireVisible(appPage, "'Idioma seleccionado' text", "text=/Idioma seleccionado/i");
				statuses.put("Detalles de la Cuenta", "PASS");

				// Step 7 - Validate Tus Negocios
				requireVisible(appPage, "Tus Negocios section", "text=/Tus Negocios/i");
				requireVisible(appPage, "Agregar Negocio button in section", "button:has-text(\"Agregar Negocio\")");
				requireVisible(appPage, "Business quota text", "text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i");
				Assert.assertTrue(
						"Business list should be visible.",
						appPage.locator("li, tr, [role='listitem'], [class*='business'], [id*='business']").count() > 0
				);
				statuses.put("Tus Negocios", "PASS");

				// Step 8 - Validate Términos y Condiciones
				final String termsUrl = openLegalLinkAndValidate(
						context,
						appPage,
						"Términos y Condiciones",
						"text=/T[eé]rminos y Condiciones/i",
						"05-terminos-y-condiciones.png"
				);
				finalUrls.put("Términos y Condiciones URL", termsUrl);
				statuses.put("Términos y Condiciones", "PASS");

				// Step 9 - Validate Política de Privacidad
				final String privacyUrl = openLegalLinkAndValidate(
						context,
						appPage,
						"Política de Privacidad",
						"text=/Pol[ií]tica de Privacidad/i",
						"06-politica-de-privacidad.png"
				);
				finalUrls.put("Política de Privacidad URL", privacyUrl);
				statuses.put("Política de Privacidad", "PASS");
			}
		} catch (Throwable throwable) {
			failure = throwable;
		} finally {
			writeReport(statuses, finalUrls, failure);
		}

		if (failure != null) {
			if (failure instanceof RuntimeException) {
				throw (RuntimeException) failure;
			}
			throw new RuntimeException(failure);
		}
	}

	private void openLoginPage(final Page appPage) {
		final String loginUrl = getSetting(LOGIN_URL_ENV, "");
		if (!loginUrl.isBlank()) {
			appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			return;
		}

		Assert.assertTrue(
				"No login URL provided. Set SALEADS_LOGIN_URL (or -DSALEADS_LOGIN_URL) when running in a fresh browser.",
				!appPage.url().startsWith("about:blank")
		);
	}

	private void maybeSelectGoogleAccount(final BrowserContext context, final String email) {
		final long deadline = System.currentTimeMillis() + 15000L;
		while (System.currentTimeMillis() < deadline) {
			for (final Page page : context.pages()) {
				final Locator account = page.locator("text=\"" + email + "\"").first();
				if (account.count() > 0 && account.isVisible()) {
					account.click();
					waitForUi(page);
					return;
				}
			}
			if (!context.pages().isEmpty()) {
				context.pages().get(context.pages().size() - 1).waitForTimeout(250);
			}
		}
	}

	private void ensureMiNegocioExpanded(final Page appPage) {
		final Locator administrar = appPage.locator("text=/Administrar Negocios/i").first();
		if (administrar.count() > 0 && administrar.isVisible()) {
			return;
		}

		final Locator negocio = requireVisible(
				appPage,
				"Negocio menu entry",
				"text=/^\\s*Negocio\\s*$/i",
				"button:has-text(\"Negocio\")",
				"a:has-text(\"Negocio\")"
		);
		clickAndWait(appPage, negocio);
	}

	private String openLegalLinkAndValidate(
			final BrowserContext context,
			final Page appPage,
			final String linkText,
			final String headingSelector,
			final String screenshotName
	) {
		final Locator legalLink = requireVisible(
				appPage,
				linkText + " link",
				"text=\"" + linkText + "\"",
				"a:has-text(\"" + linkText + "\")"
		);
		final int pagesBeforeClick = context.pages().size();
		clickAndWait(appPage, legalLink);

		Page legalPage = appPage;
		final boolean openedNewTab = context.pages().size() > pagesBeforeClick;
		if (openedNewTab) {
			legalPage = context.pages().get(context.pages().size() - 1);
		}

		waitForUi(legalPage);
		requireVisible(legalPage, linkText + " heading", headingSelector, "h1:has-text(\"" + linkText + "\")");
		Assert.assertTrue(
				"Legal content should be visible for " + linkText + ".",
				legalPage.locator("p, article, section").count() > 0
		);
		captureScreenshot(legalPage, screenshotName, true);
		final String finalUrl = legalPage.url();

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private Locator requireVisible(final Page page, final String description, final String... selectors) {
		final long deadline = System.currentTimeMillis() + 20000L;
		while (System.currentTimeMillis() < deadline) {
			for (final String selector : selectors) {
				final Locator locator = page.locator(selector).first();
				if (locator.count() > 0 && locator.isVisible()) {
					return locator;
				}
			}
			page.waitForTimeout(250);
		}
		throw new AssertionError("Element not visible: " + description);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click();
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(700);
	}

	private void captureScreenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(EVIDENCE_DIR.resolve(fileName)).setFullPage(fullPage));
	}

	private void writeReport(final Map<String, String> statuses, final Map<String, String> finalUrls, final Throwable failure) throws IOException {
		final String result = statuses.entrySet()
				.stream()
				.map(entry -> "- " + entry.getKey() + ": " + entry.getValue())
				.collect(Collectors.joining(System.lineSeparator()));

		final String urls = finalUrls.entrySet()
				.stream()
				.map(entry -> "- " + entry.getKey() + ": " + entry.getValue())
				.collect(Collectors.joining(System.lineSeparator()));

		final StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
		report.append("Generated at: ").append(OffsetDateTime.now()).append(System.lineSeparator());
		report.append(System.lineSeparator());
		report.append("Step Results").append(System.lineSeparator());
		report.append(result).append(System.lineSeparator());
		report.append(System.lineSeparator());
		report.append("Captured Final URLs").append(System.lineSeparator());
		report.append(urls.isBlank() ? "- none" : urls).append(System.lineSeparator());

		if (failure != null) {
			report.append(System.lineSeparator());
			report.append("Failure").append(System.lineSeparator());
			report.append("- ").append(failure.getClass().getSimpleName()).append(": ").append(failure.getMessage()).append(System.lineSeparator());
		}

		Files.write(REPORT_PATH, report.toString().getBytes(StandardCharsets.UTF_8));
	}

	private boolean isEnabled(final String key) {
		return "true".equalsIgnoreCase(getSetting(key, "false"));
	}

	private String getSetting(final String key, final String defaultValue) {
		final String propertyValue = System.getProperty(key);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		final String envValue = System.getenv(key);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return defaultValue;
	}

	private boolean containsLikelyUserName(final String bodyText) {
		final String[] lines = bodyText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.contains("@") || line.matches("(?i).*(informaci[oó]n general|business plan|cambiar plan|cuenta creada|estado activo|idioma seleccionado).*")) {
				continue;
			}
			if (line.matches("^[\\p{L}][\\p{L}'\\-]+(?:\\s+[\\p{L}][\\p{L}'\\-]+)+$")) {
				return true;
			}
		}
		return false;
	}
}
