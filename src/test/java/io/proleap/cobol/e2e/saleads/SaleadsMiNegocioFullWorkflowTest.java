package io.proleap.cobol.e2e.saleads;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final String loginUrl = getConfigValue("saleads.login.url", "SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL (or -Dsaleads.login.url) to the login page URL of the target environment.",
				!isBlank(loginUrl));

		final int timeoutMs = parseInt(getConfigValue("saleads.timeout.ms", "SALEADS_TIMEOUT_MS"), 30000);
		final boolean headless = Boolean.parseBoolean(getConfigValueOrDefault("saleads.headless", "SALEADS_HEADLESS", "true"));
		final Path evidenceDir = createEvidenceDirectory();
		final Path reportPath = evidenceDir.resolve("final-report.md");

		final Map<String, String> results = new LinkedHashMap<>();
		for (final String field : REPORT_FIELDS) {
			results.put(field, "FAIL - not executed");
		}

		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create();
				Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
				BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 1024))) {

			final Page page = context.newPage();
			page.setDefaultTimeout(timeoutMs);
			page.navigate(loginUrl);
			waitForUiLoad(page);

			String blocker = null;

			if (blocker == null && !runStep("Login", results, () -> stepLoginWithGoogle(page, evidenceDir))) {
				blocker = "Login";
			}

			if (blocker == null && !runStep("Mi Negocio menu", results, () -> stepOpenMiNegocioMenu(page, evidenceDir))) {
				blocker = "Mi Negocio menu";
			}

			if (blocker == null && !runStep("Agregar Negocio modal", results, () -> stepAgregarNegocioModal(page, evidenceDir))) {
				blocker = "Agregar Negocio modal";
			}

			if (blocker == null && !runStep("Administrar Negocios view", results,
					() -> stepAdministrarNegociosView(page, evidenceDir))) {
				blocker = "Administrar Negocios view";
			}

			if (blocker == null && !runStep("Información General", results, () -> stepInformacionGeneral(page))) {
				blocker = "Información General";
			}

			if (blocker == null && !runStep("Detalles de la Cuenta", results, () -> stepDetallesCuenta(page))) {
				blocker = "Detalles de la Cuenta";
			}

			if (blocker == null && !runStep("Tus Negocios", results, () -> stepTusNegocios(page))) {
				blocker = "Tus Negocios";
			}

			if (blocker == null && !runStep("Términos y Condiciones", results,
					() -> stepLegalPage(page, "Términos y Condiciones", "05-terminos-y-condiciones.png", evidenceDir, legalUrls))) {
				blocker = "Términos y Condiciones";
			}

			if (blocker == null && !runStep("Política de Privacidad", results,
					() -> stepLegalPage(page, "Política de Privacidad", "06-politica-de-privacidad.png", evidenceDir, legalUrls))) {
				blocker = "Política de Privacidad";
			}

			if (blocker != null) {
				markBlocked(results, blocker);
			}
		}

		writeFinalReport(reportPath, results, legalUrls, evidenceDir);

		final StringBuilder failed = new StringBuilder();
		for (final Map.Entry<String, String> entry : results.entrySet()) {
			if (!entry.getValue().startsWith("PASS")) {
				if (failed.length() > 0) {
					failed.append(", ");
				}
				failed.append(entry.getKey()).append(" -> ").append(entry.getValue());
			}
		}

		if (failed.length() > 0) {
			Assert.fail("SaleADS Mi Negocio workflow validation failed. Details: " + failed + ". Evidence: "
					+ evidenceDir.toAbsolutePath());
		}
	}

	private void stepLoginWithGoogle(final Page page, final Path evidenceDir) {
		final Locator loginButton = firstVisible(page, "Google login button",
				"button:has-text('Sign in with Google')",
				"button:has-text('Iniciar sesión con Google')",
				"button:has-text('Iniciar sesion con Google')",
				"button:has-text('Continuar con Google')",
				"[role='button']:has-text('Sign in with Google')",
				"[role='button']:has-text('Iniciar sesión con Google')",
				"a:has-text('Sign in with Google')",
				"a:has-text('Iniciar sesión con Google')",
				"text=Google");
		Page accountSelectionPage = page;

		try {
			accountSelectionPage = page.waitForPopup(() -> loginButton.click(), new Page.WaitForPopupOptions().setTimeout(6000));
			waitForUiLoad(accountSelectionPage);
		} catch (final PlaywrightException ignored) {
			clickAndWait(loginButton, page);
		}

		clickIfVisible(accountSelectionPage, 8000,
				"text=" + ACCOUNT_EMAIL,
				"div:has-text('" + ACCOUNT_EMAIL + "')",
				"span:has-text('" + ACCOUNT_EMAIL + "')");
		waitForUiLoad(accountSelectionPage);

		assertVisible(page, "main application interface",
				"main",
				"[role='main']",
				"text=Negocio",
				"text=Mi Negocio");
		assertVisible(page, "left sidebar navigation",
				"aside",
				"nav",
				"aside:has-text('Negocio')",
				"nav:has-text('Negocio')");
		takeScreenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), false);
	}

	private void stepOpenMiNegocioMenu(final Page page, final Path evidenceDir) {
		expandMiNegocioMenu(page);
		assertVisible(page, "Agregar Negocio menu option",
				"text=Agregar Negocio",
				"a:has-text('Agregar Negocio')",
				"button:has-text('Agregar Negocio')");
		assertVisible(page, "Administrar Negocios menu option",
				"text=Administrar Negocios",
				"a:has-text('Administrar Negocios')",
				"button:has-text('Administrar Negocios')");
		takeScreenshot(page, evidenceDir.resolve("02-mi-negocio-expanded-menu.png"), false);
	}

	private void stepAgregarNegocioModal(final Page page, final Path evidenceDir) {
		final Locator agregarNegocio = firstVisible(page, "Agregar Negocio action",
				"text=Agregar Negocio",
				"a:has-text('Agregar Negocio')",
				"button:has-text('Agregar Negocio')");
		clickAndWait(agregarNegocio, page);

		assertVisible(page, "Crear Nuevo Negocio modal title",
				"text=Crear Nuevo Negocio",
				"h2:has-text('Crear Nuevo Negocio')",
				"h3:has-text('Crear Nuevo Negocio')");
		assertVisible(page, "Nombre del Negocio input field",
				"label:has-text('Nombre del Negocio')",
				"input[placeholder*='Nombre del Negocio']",
				"input[aria-label*='Nombre del Negocio']",
				"input[name*='negocio']");
		assertVisible(page, "business quota text",
				"text=Tienes 2 de 3 negocios");
		assertVisible(page, "Cancelar button",
				"button:has-text('Cancelar')");
		assertVisible(page, "Crear Negocio button",
				"button:has-text('Crear Negocio')");

		takeScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

		final Locator businessNameInput = firstVisible(page, "Nombre del Negocio input",
				"input[placeholder*='Nombre del Negocio']",
				"input[aria-label*='Nombre del Negocio']",
				"input[name*='negocio']");
		businessNameInput.click();
		businessNameInput.fill("Negocio Prueba Automatización");
		waitForUiLoad(page);

		final Locator cancelar = firstVisible(page, "Cancelar button",
				"button:has-text('Cancelar')");
		clickAndWait(cancelar, page);
	}

	private void stepAdministrarNegociosView(final Page page, final Path evidenceDir) {
		expandMiNegocioMenu(page);
		final Locator administrarNegocios = firstVisible(page, "Administrar Negocios action",
				"text=Administrar Negocios",
				"a:has-text('Administrar Negocios')",
				"button:has-text('Administrar Negocios')");
		clickAndWait(administrarNegocios, page);

		assertVisible(page, "Información General section", "text=Información General");
		assertVisible(page, "Detalles de la Cuenta section", "text=Detalles de la Cuenta");
		assertVisible(page, "Tus Negocios section", "text=Tus Negocios");
		assertVisible(page, "Sección Legal section", "text=Sección Legal");

		takeScreenshot(page, evidenceDir.resolve("04-administrar-negocios-account-page.png"), true);
	}

	private void stepInformacionGeneral(final Page page) {
		final String sectionText = sectionText(page, "Información General");
		assertContains(sectionText, "BUSINESS PLAN", "BUSINESS PLAN text");
		assertVisible(page, "Cambiar Plan button",
				"button:has-text('Cambiar Plan')",
				"text=Cambiar Plan");

		final Matcher emailMatcher = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}").matcher(sectionText);
		if (!emailMatcher.find()) {
			throw new AssertionError("User email is not visible in Información General.");
		}

		if (!containsLikelyUserName(sectionText, emailMatcher.group())) {
			throw new AssertionError("A likely user name is not visible in Información General.");
		}
	}

	private void stepDetallesCuenta(final Page page) {
		final String sectionText = sectionText(page, "Detalles de la Cuenta");
		assertContainsIgnoreCase(sectionText, "Cuenta creada", "Cuenta creada");
		assertContainsIgnoreCase(sectionText, "Estado activo", "Estado activo");
		assertContainsIgnoreCase(sectionText, "Idioma seleccionado", "Idioma seleccionado");
	}

	private void stepTusNegocios(final Page page) {
		final Locator section = firstVisible(page, "Tus Negocios section container",
				"section:has-text('Tus Negocios')",
				"div:has-text('Tus Negocios')");

		assertVisible(page, "Agregar Negocio button in Tus Negocios",
				"section:has-text('Tus Negocios') button:has-text('Agregar Negocio')",
				"div:has-text('Tus Negocios') button:has-text('Agregar Negocio')",
				"text=Agregar Negocio");

		final String sectionText = section.innerText();
		assertContains(sectionText, "Tienes 2 de 3 negocios", "business quota text");

		final int listLikeElements = section.locator("li, [role='listitem'], table tr, .card, [class*='card']").count();
		if (listLikeElements == 0) {
			throw new AssertionError("Business list is not visible in Tus Negocios section.");
		}
	}

	private void stepLegalPage(final Page appPage, final String linkText, final String screenshotName, final Path evidenceDir,
			final Map<String, String> legalUrls) {
		final Locator link = firstVisible(appPage, linkText + " link",
				"section:has-text('Sección Legal') a:has-text('" + linkText + "')",
				"div:has-text('Sección Legal') a:has-text('" + linkText + "')",
				"a:has-text('" + linkText + "')",
				"text=" + linkText);

		Page legalPage = null;
		boolean popupOpened = false;

		try {
			legalPage = appPage.waitForPopup(() -> link.click(), new Page.WaitForPopupOptions().setTimeout(6000));
			popupOpened = true;
		} catch (final PlaywrightException ignored) {
			clickAndWait(link, appPage);
			legalPage = appPage;
		}

		waitForUiLoad(legalPage);
		assertVisible(legalPage, linkText + " heading",
				"h1:has-text('" + linkText + "')",
				"h2:has-text('" + linkText + "')",
				"text=" + linkText);

		final String bodyText = legalPage.locator("body").innerText();
		if (bodyText == null || bodyText.trim().length() < 120) {
			throw new AssertionError("Legal content text is not visible for " + linkText + ".");
		}

		takeScreenshot(legalPage, evidenceDir.resolve(screenshotName), true);
		legalUrls.put(linkText, legalPage.url());

		if (popupOpened) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else {
			appPage.goBack();
			waitForUiLoad(appPage);
			assertVisible(appPage, "application page after returning from legal page",
					"text=Sección Legal",
					"text=Información General");
		}
	}

	private void expandMiNegocioMenu(final Page page) {
		if (isAnyVisible(page, 1500, "text=Agregar Negocio")
				&& isAnyVisible(page, 1500, "text=Administrar Negocios")) {
			return;
		}

		if (clickIfVisible(page, 3000,
				"text=Mi Negocio",
				"button:has-text('Mi Negocio')",
				"a:has-text('Mi Negocio')")) {
			waitForUiLoad(page);
		}

		if (!isAnyVisible(page, 2000, "text=Agregar Negocio")
				|| !isAnyVisible(page, 2000, "text=Administrar Negocios")) {
			clickIfVisible(page, 3000,
					"text=Negocio",
					"button:has-text('Negocio')",
					"a:has-text('Negocio')");
			waitForUiLoad(page);
			final Locator miNegocio = firstVisible(page, "Mi Negocio option",
					"text=Mi Negocio",
					"button:has-text('Mi Negocio')",
					"a:has-text('Mi Negocio')");
			clickAndWait(miNegocio, page);
		}
	}

	private boolean runStep(final String field, final Map<String, String> results, final ThrowingRunnable runnable) {
		try {
			runnable.run();
			results.put(field, "PASS");
			return true;
		} catch (final Throwable throwable) {
			results.put(field, "FAIL - " + sanitizeMessage(throwable.getMessage()));
			return false;
		}
	}

	private void markBlocked(final Map<String, String> results, final String blocker) {
		for (final String field : REPORT_FIELDS) {
			if ("FAIL - not executed".equals(results.get(field))) {
				results.put(field, "FAIL - blocked by " + blocker);
			}
		}
	}

	private String sectionText(final Page page, final String title) {
		final Locator section = firstVisible(page, title + " section container",
				"section:has-text('" + title + "')",
				"div:has-text('" + title + "')");
		return section.innerText();
	}

	private boolean containsLikelyUserName(final String sectionText, final String email) {
		final String[] lines = sectionText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			final String lower = line.toLowerCase();
			if (line.equalsIgnoreCase(email)
					|| lower.contains("@")
					|| lower.contains("información general")
					|| lower.contains("business plan")
					|| lower.contains("cambiar plan")) {
				continue;
			}
			if (Pattern.compile("[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}").matcher(line).find()) {
				return true;
			}
		}
		return false;
	}

	private void assertContains(final String actualText, final String expectedText, final String label) {
		if (actualText == null || !actualText.contains(expectedText)) {
			throw new AssertionError(label + " is not visible.");
		}
	}

	private void assertContainsIgnoreCase(final String actualText, final String expectedText, final String label) {
		if (actualText == null || !actualText.toLowerCase().contains(expectedText.toLowerCase())) {
			throw new AssertionError(label + " is not visible.");
		}
	}

	private Locator firstVisible(final Page page, final String label, final String... selectors) {
		for (final String selector : selectors) {
			final Locator locator = page.locator(selector).first();
			if (waitVisible(locator, 3000)) {
				return locator;
			}
		}
		throw new AssertionError("Unable to find visible element for " + label + ".");
	}

	private boolean isAnyVisible(final Page page, final int timeoutMs, final String... selectors) {
		for (final String selector : selectors) {
			if (waitVisible(page.locator(selector).first(), timeoutMs)) {
				return true;
			}
		}
		return false;
	}

	private void assertVisible(final Page page, final String label, final String... selectors) {
		firstVisible(page, label, selectors);
	}

	private boolean clickIfVisible(final Page page, final int timeoutMs, final String... selectors) {
		for (final String selector : selectors) {
			final Locator locator = page.locator(selector).first();
			if (waitVisible(locator, timeoutMs)) {
				clickAndWait(locator, page);
				return true;
			}
		}
		return false;
	}

	private boolean waitVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setTimeout((double) timeoutMs).setState(WaitForSelectorState.VISIBLE));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void clickAndWait(final Locator locator, final Page page) {
		locator.click();
		waitForUiLoad(page);
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000));
		} catch (final PlaywrightException ignored) {
			// Some SPA interactions do not trigger a full document load state.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7000));
		} catch (final PlaywrightException ignored) {
			// Keep moving when websocket traffic prevents network idle.
		}
		try {
			page.waitForTimeout(500);
		} catch (final PlaywrightException ignored) {
			// Page may have been closed if auth or legal navigation happened in a popup.
		}
	}

	private void takeScreenshot(final Page page, final Path path, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private Path createEvidenceDirectory() throws IOException {
		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path evidenceDir = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private void writeFinalReport(final Path reportPath, final Map<String, String> results,
			final Map<String, String> legalUrls, final Path evidenceDir) throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("# SaleADS Mi Negocio Full Test Report\n\n");
		report.append("| Step | Result |\n");
		report.append("| --- | --- |\n");
		for (final String field : REPORT_FIELDS) {
			report.append("| ").append(field).append(" | ").append(results.get(field)).append(" |\n");
		}
		report.append("\n## Legal URLs\n\n");
		report.append("- Términos y Condiciones: ")
				.append(legalUrls.getOrDefault("Términos y Condiciones", "N/A"))
				.append("\n");
		report.append("- Política de Privacidad: ")
				.append(legalUrls.getOrDefault("Política de Privacidad", "N/A"))
				.append("\n");
		report.append("\n## Evidence Directory\n\n");
		report.append(evidenceDir.toAbsolutePath()).append("\n");
		Files.write(reportPath, report.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String sanitizeMessage(final String message) {
		if (message == null || message.trim().isEmpty()) {
			return "Unknown error";
		}
		return message.replace('\n', ' ').replace('\r', ' ').trim();
	}

	private String getConfigValue(final String propertyKey, final String envKey) {
		final String propertyValue = System.getProperty(propertyKey);
		if (!isBlank(propertyValue)) {
			return propertyValue.trim();
		}
		final String envValue = System.getenv(envKey);
		if (!isBlank(envValue)) {
			return envValue.trim();
		}
		return null;
	}

	private String getConfigValueOrDefault(final String propertyKey, final String envKey, final String defaultValue) {
		final String value = getConfigValue(propertyKey, envKey);
		return isBlank(value) ? defaultValue : value;
	}

	private int parseInt(final String value, final int defaultValue) {
		try {
			return Integer.parseInt(value);
		} catch (final Exception ignored) {
			return defaultValue;
		}
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
