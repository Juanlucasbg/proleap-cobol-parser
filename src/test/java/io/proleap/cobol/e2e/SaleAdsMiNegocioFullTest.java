package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Test;

public class SaleAdsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final Map<String, String> results = new LinkedHashMap<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final Path evidenceDir = createEvidenceDir();

		final String headlessEnv = System.getenv().getOrDefault("SALEADS_HEADLESS", "true");
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(Boolean.parseBoolean(headlessEnv)));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page page = context.newPage();

			if (loginUrl != null && !loginUrl.isBlank()) {
				page.navigate(loginUrl);
				waitForUi(page);
			}

			results.put("Login", runStep(() -> {
				loginWithGoogle(page, context);
				waitForUi(page);
				requireVisible(page.getByRole(AriaRole.NAVIGATION).first(), "Left sidebar navigation must be visible.");
				requireAnyVisible("Main application interface should be visible.",
						page.getByRole(AriaRole.MAIN).first(),
						page.locator("main").first(),
						page.locator("aside").first());
				takeScreenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), false);
			}));

			results.put("Mi Negocio menu", runStep(() -> {
				openMiNegocioMenu(page);
				requireTextVisible(page, "Agregar Negocio");
				requireTextVisible(page, "Administrar Negocios");
				takeScreenshot(page, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
			}));

			results.put("Agregar Negocio modal", runStep(() -> {
				clickByVisibleText(page, "Agregar Negocio");
				waitForUi(page);
				requireAnyTextVisible(page, "Crear Nuevo Negocio", "Crear nuevo negocio");
				requireAnyTextVisible(page, "Nombre del Negocio", "Nombre del negocio");
				requireAnyTextVisible(page, "Tienes 2 de 3 negocios", "2 de 3 negocios");
				requireTextVisible(page, "Cancelar");
				requireAnyTextVisible(page, "Crear Negocio", "Crear negocio");
				takeScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

				// Optional interaction required by the workflow.
				typeIfVisible(page, "Nombre del Negocio", "Negocio Prueba Automatización");
				clickIfVisible(page, "Cancelar");
				waitForUi(page);
			}));

			results.put("Administrar Negocios view", runStep(() -> {
				openMiNegocioMenu(page);
				clickByVisibleText(page, "Administrar Negocios");
				waitForUi(page);
				requireTextVisible(page, "Información General");
				requireTextVisible(page, "Detalles de la Cuenta");
				requireTextVisible(page, "Tus Negocios");
				requireAnyTextVisible(page, "Sección Legal", "Seccion Legal");
				takeScreenshot(page, evidenceDir.resolve("04-administrar-negocios.png"), true);
			}));

			results.put("Información General", runStep(() -> {
				requireAnyVisible("User name should be visible.",
						page.locator("section:has-text(\"Información General\") [class*=\"name\"]").first(),
						page.locator("section:has-text(\"Información General\") [data-testid*=\"name\"]").first(),
						page.locator("section:has-text(\"Información General\") p").first());
				requireAnyVisible("User email should be visible.",
						page.locator("section:has-text(\"Información General\") :text-matches(\".+@.+\\\\..+\", \"i\")").first(),
						page.locator(":text-matches(\".+@.+\\\\..+\", \"i\")").first());
				requireAnyTextVisible(page, "BUSINESS PLAN", "Business Plan");
				requireTextVisible(page, "Cambiar Plan");
			}));

			results.put("Detalles de la Cuenta", runStep(() -> {
				requireAnyTextVisible(page, "Cuenta creada", "Cuenta Creada");
				requireAnyTextVisible(page, "Estado activo", "Estado Activo");
				requireAnyTextVisible(page, "Idioma seleccionado", "Idioma Seleccionado");
			}));

			results.put("Tus Negocios", runStep(() -> {
				requireTextVisible(page, "Tus Negocios");
				requireTextVisible(page, "Agregar Negocio");
				requireAnyTextVisible(page, "Tienes 2 de 3 negocios", "2 de 3 negocios");
				requireAnyVisible("Business list should be visible.",
						page.locator("section:has-text(\"Tus Negocios\") ul").first(),
						page.locator("section:has-text(\"Tus Negocios\") table").first(),
						page.locator("section:has-text(\"Tus Negocios\") [role=\"list\"]").first(),
						page.locator("section:has-text(\"Tus Negocios\") [class*=\"business\"]").first());
			}));

			results.put("Términos y Condiciones", runStep(() -> {
				final String termsUrl = validateLegalLink(page, context, "Términos y Condiciones", "08-terminos-y-condiciones.png",
						evidenceDir);
				legalUrls.put("Términos y Condiciones", termsUrl);
			}));

			results.put("Política de Privacidad", runStep(() -> {
				final String privacyUrl = validateLegalLink(page, context, "Política de Privacidad", "09-politica-de-privacidad.png",
						evidenceDir);
				legalUrls.put("Política de Privacidad", privacyUrl);
			}));

			browser.close();
		}

		final Path reportPath = evidenceDir.resolve("final-report.json");
		writeReport(reportPath, results, legalUrls);

		boolean hasFailure = false;
		for (final String status : results.values()) {
			if ("FAIL".equals(status)) {
				hasFailure = true;
				break;
			}
		}

		Assert.assertFalse("One or more SaleADS Mi Negocio validations failed. Review: " + reportPath.toAbsolutePath(), hasFailure);
	}

	private interface StepAction {
		void run() throws Exception;
	}

	private String runStep(final StepAction stepAction) {
		try {
			stepAction.run();
			return "PASS";
		} catch (final Exception exception) {
			return "FAIL";
		}
	}

	private void loginWithGoogle(final Page page, final BrowserContext context) {
		final Locator signInButton = firstVisible(
				page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)"))),
				page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)"))),
				page.getByText("Sign in with Google", new Page.GetByTextOptions().setExact(false)).first(),
				page.getByText("Iniciar sesión con Google", new Page.GetByTextOptions().setExact(false)).first(),
				page.getByText("Continuar con Google", new Page.GetByTextOptions().setExact(false)).first());
		if (signInButton == null) {
			throw new IllegalStateException("Google login button was not found.");
		}

		final Page popup = waitForPopupAfterClick(context, signInButton);
		final Page authPage = popup != null ? popup : page;

		clickIfVisible(authPage, GOOGLE_ACCOUNT_EMAIL);
		waitForUi(authPage);

		if (popup != null) {
			waitForUi(page);
			page.bringToFront();
		}
	}

	private void openMiNegocioMenu(final Page page) {
		clickIfVisible(page, "Negocio");
		waitForUi(page);
		clickIfVisible(page, "Mi Negocio");
		waitForUi(page);
	}

	private String validateLegalLink(final Page appPage, final BrowserContext context, final String linkText,
			final String screenshotName, final Path evidenceDir) throws IOException {
		final Locator linkLocator = firstVisible(
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))).first(),
				appPage.getByText(linkText, new Page.GetByTextOptions().setExact(false)).first());
		if (linkLocator == null) {
			throw new IllegalStateException("Could not find legal link: " + linkText);
		}

		Page targetPage = appPage;
		try {
			final Page popup = context.waitForPage(() -> {
				linkLocator.click();
			}, new BrowserContext.WaitForPageOptions().setTimeout(5000));
			targetPage = popup;
		} catch (final PlaywrightException ignored) {
			// Link stayed in the current tab.
		}

		waitForUi(targetPage);
		requireAnyTextVisible(targetPage, linkText, normalizeAccents(linkText));
		requireAnyVisible("Legal content text should be visible for " + linkText,
				targetPage.locator("main p").first(),
				targetPage.locator("article p").first(),
				targetPage.locator("body p").first());
		takeScreenshot(targetPage, evidenceDir.resolve(screenshotName), true);
		final String finalUrl = targetPage.url();

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void clickByVisibleText(final Page page, final String text) {
		final Locator locator = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(text)))).first(),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(text)))).first(),
				page.getByText(text, new Page.GetByTextOptions().setExact(false)).first());
		if (locator == null) {
			throw new IllegalStateException("Could not find clickable text: " + text);
		}
		locator.click();
		waitForUi(page);
	}

	private void clickIfVisible(final Page page, final String text) {
		final Locator locator = firstVisible(
				page.getByText(text, new Page.GetByTextOptions().setExact(false)).first(),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(text)))).first(),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(text)))).first());
		if (locator != null) {
			locator.click();
			waitForUi(page);
		}
	}

	private void typeIfVisible(final Page page, final String labelText, final String value) {
		final Locator input = firstVisible(
				page.getByLabel(labelText, new Page.GetByLabelOptions().setExact(false)).first(),
				page.getByPlaceholder(labelText, new Page.GetByPlaceholderOptions().setExact(false)).first(),
				page.locator("input[name*=\"negocio\" i]").first(),
				page.locator("input[type='text']").first());
		if (input != null) {
			input.click();
			waitForUi(page);
			input.fill(value);
			waitForUi(page);
		}
	}

	private void requireTextVisible(final Page page, final String text) {
		final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
		if (!isVisible(locator)) {
			throw new IllegalStateException("Expected text not visible: " + text);
		}
	}

	private void requireAnyTextVisible(final Page page, final String... texts) {
		for (final String text : texts) {
			final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
			if (isVisible(locator)) {
				return;
			}
		}
		throw new IllegalStateException("None of the expected texts are visible.");
	}

	private void requireVisible(final Locator locator, final String message) {
		if (!isVisible(locator)) {
			throw new IllegalStateException(message);
		}
	}

	private void requireAnyVisible(final String message, final Locator... locators) {
		for (final Locator locator : locators) {
			if (locator != null && isVisible(locator)) {
				return;
			}
		}
		throw new IllegalStateException(message);
	}

	private Locator firstVisible(final Locator... locators) {
		for (final Locator locator : locators) {
			if (locator != null && isVisible(locator)) {
				return locator;
			}
		}
		return null;
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(4000));
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private Page waitForPopupAfterClick(final BrowserContext context, final Locator locator) {
		try {
			return context.waitForPage(() -> {
				locator.click();
			}, new BrowserContext.WaitForPageOptions().setTimeout(6000));
		} catch (final PlaywrightException exception) {
			// Login flow stayed in the current tab.
			return null;
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			// Some clicks do not trigger navigation and will timeout.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (final PlaywrightException ignored) {
			// NETWORKIDLE may not always be reached on SPA pages.
		}
		page.waitForTimeout(700);
	}

	private void takeScreenshot(final Page page, final Path target, final boolean fullPage) throws IOException {
		Files.createDirectories(target.getParent());
		page.screenshot(new Page.ScreenshotOptions().setPath(target).setFullPage(fullPage));
	}

	private Path createEvidenceDir() throws IOException {
		final String runId = LocalDateTime.now().format(RUN_ID_FORMAT);
		final Path path = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(path);
		return path;
	}

	private void writeReport(final Path reportPath, final Map<String, String> results, final Map<String, String> legalUrls)
			throws IOException {
		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"results\": {\n");

		int index = 0;
		for (final Map.Entry<String, String> entry : results.entrySet()) {
			json.append("    \"").append(escape(entry.getKey())).append("\": \"").append(escape(entry.getValue())).append("\"");
			if (index < results.size() - 1) {
				json.append(",");
			}
			json.append("\n");
			index++;
		}

		json.append("  },\n");
		json.append("  \"legal_urls\": {\n");
		int legalIndex = 0;
		for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
			json.append("    \"").append(escape(entry.getKey())).append("\": \"").append(escape(entry.getValue())).append("\"");
			if (legalIndex < legalUrls.size() - 1) {
				json.append(",");
			}
			json.append("\n");
			legalIndex++;
		}
		json.append("  }\n");
		json.append("}\n");

		Files.createDirectories(reportPath.getParent());
		Files.write(reportPath, json.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String normalizeAccents(final String text) {
		return text
				.replace("á", "a")
				.replace("é", "e")
				.replace("í", "i")
				.replace("ó", "o")
				.replace("ú", "u")
				.replace("Á", "A")
				.replace("É", "E")
				.replace("Í", "I")
				.replace("Ó", "O")
				.replace("Ú", "U");
	}

	private String escape(final String value) {
		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}
}
