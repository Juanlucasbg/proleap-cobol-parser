package io.proleap.e2e.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleAdsMiNegocioWorkflowTest {

	private static final Pattern GOOGLE_BUTTON_PATTERN = Pattern.compile(
			"(sign\\s*in\\s*with\\s*google|continuar\\s*con\\s*google|iniciar\\s*con\\s*google|acceder\\s*con\\s*google|google)",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	private static final String TEST_NAME = "saleads_mi_negocio_full_test";

	@Test
	public void validateMiNegocioWorkflow() throws IOException {
		final Path evidenceDir = createEvidenceDirectory();
		final Map<String, Boolean> report = initializeReport();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = launchBrowser(playwright);
			try {
				final BrowserContext context = browser.newContext();
				final Page page = context.newPage();
				openLoginPage(page);

				final boolean loginOk = runStep("Login", report, failures, () -> {
					loginWithGoogle(page);
					waitForMainInterface(page);
					takeScreenshot(page, evidenceDir.resolve("01-dashboard.png"), false);
				});

				final boolean menuOk = runStep("Mi Negocio menu", report, failures, () -> {
					require(loginOk, "No se puede abrir Mi Negocio sin login exitoso.");
					openMiNegocioMenu(page);
					assertVisibleText(page, "Agregar Negocio", 10_000);
					assertVisibleText(page, "Administrar Negocios", 10_000);
					takeScreenshot(page, evidenceDir.resolve("02-mi-negocio-menu-expandido.png"), false);
				});

				runStep("Agregar Negocio modal", report, failures, () -> {
					require(menuOk, "No se puede validar el modal sin el menu Mi Negocio.");
					clickByVisibleText(page, "Agregar Negocio");
					page.waitForLoadState(LoadState.DOMCONTENTLOADED);
					page.waitForTimeout(600);
					assertVisibleText(page, "Crear Nuevo Negocio", 10_000);
					assertVisibleInput(page, "Nombre del Negocio", 10_000);
					assertVisibleText(page, "Tienes 2 de 3 negocios", 10_000);
					assertActionableByText(page, "Cancelar", 10_000);
					assertActionableByText(page, "Crear Negocio", 10_000);
					takeScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

					fillOptionalField(page, "Nombre del Negocio", "Negocio Prueba Automatizacion");
					clickByVisibleText(page, "Cancelar");
					page.waitForLoadState(LoadState.DOMCONTENTLOADED);
				});

				final boolean administrarOk = runStep("Administrar Negocios view", report, failures, () -> {
					require(menuOk, "No se puede abrir Administrar Negocios sin menu Mi Negocio.");
					openMiNegocioMenu(page);
					clickByVisibleText(page, "Administrar Negocios");
					page.waitForLoadState(LoadState.DOMCONTENTLOADED);
					page.waitForTimeout(800);
					assertVisibleText(page, "Informacion General", 12_000);
					assertVisibleText(page, "Detalles de la Cuenta", 12_000);
					assertVisibleText(page, "Tus Negocios", 12_000);
					assertVisibleText(page, "Seccion Legal", 12_000);
					takeScreenshot(page, evidenceDir.resolve("04-administrar-negocios-full.png"), true);
				});

				runStep("Informacion General", report, failures, () -> {
					require(administrarOk, "No se puede validar Informacion General sin Administrar Negocios.");
					assertBusinessPlanSection(page);
				});

				runStep("Detalles de la Cuenta", report, failures, () -> {
					require(administrarOk, "No se puede validar Detalles de la Cuenta sin Administrar Negocios.");
					assertVisibleText(page, "Cuenta creada", 10_000);
					assertVisibleText(page, "Estado activo", 10_000);
					assertVisibleText(page, "Idioma seleccionado", 10_000);
				});

				runStep("Tus Negocios", report, failures, () -> {
					require(administrarOk, "No se puede validar Tus Negocios sin Administrar Negocios.");
					assertVisibleText(page, "Tus Negocios", 10_000);
					assertActionableByText(page, "Agregar Negocio", 10_000);
					assertVisibleText(page, "Tienes 2 de 3 negocios", 10_000);
				});

				runStep("Terminos y Condiciones", report, failures, () -> {
					require(administrarOk, "No se puede validar Terminos y Condiciones sin Administrar Negocios.");
					final String finalUrl = validateLegalLink(page, "Terminos y Condiciones", "Terminos y Condiciones",
							evidenceDir.resolve("08-terminos-condiciones.png"));
					legalUrls.put("Terminos y Condiciones", finalUrl);
				});

				runStep("Politica de Privacidad", report, failures, () -> {
					require(administrarOk, "No se puede validar Politica de Privacidad sin Administrar Negocios.");
					final String finalUrl = validateLegalLink(page, "Politica de Privacidad", "Politica de Privacidad",
							evidenceDir.resolve("09-politica-privacidad.png"));
					legalUrls.put("Politica de Privacidad", finalUrl);
				});
			} finally {
				browser.close();
			}
		}

		writeReport(evidenceDir, report, legalUrls, failures);
		if (!failures.isEmpty()) {
			Assert.fail("Validaciones fallidas:\n" + String.join("\n", failures));
		}
	}

	private void openLoginPage(final Page page) {
		final String baseUrl = getConfigValue("saleads.baseUrl", "SALEADS_BASE_URL");
		require(baseUrl != null && !baseUrl.isBlank(),
				"Configura saleads.baseUrl o SALEADS_BASE_URL para iniciar en la pagina de login.");
		page.navigate(baseUrl);
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
	}

	private Browser launchBrowser(final Playwright playwright) {
		final boolean headless = Boolean.parseBoolean(getConfigOrDefault("saleads.headless", "SALEADS_HEADLESS", "true"));
		final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(headless);
		final String slowMo = getConfigValue("saleads.slowMo", "SALEADS_SLOWMO");
		if (slowMo != null && !slowMo.isBlank()) {
			launchOptions.setSlowMo(Double.parseDouble(slowMo));
		}
		return playwright.chromium().launch(launchOptions);
	}

	private void loginWithGoogle(final Page page) {
		final Locator loginButton = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_BUTTON_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_BUTTON_PATTERN)),
				page.getByText(GOOGLE_BUTTON_PATTERN));

		clickAndWait(page, loginButton);

		final String accountEmail = getConfigOrDefault("saleads.google.account", "SALEADS_GOOGLE_ACCOUNT",
				"juanlucasbarbiergarzon@gmail.com");
		tryClickIfVisible(page.getByText(Pattern.compile(Pattern.quote(accountEmail), Pattern.CASE_INSENSITIVE)), 8_000);
	}

	private void waitForMainInterface(final Page page) {
		final Locator sidebar = firstVisible(
				page.locator("aside"),
				page.getByRole(AriaRole.NAVIGATION),
				page.getByText(Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE)));
		sidebar.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
	}

	private void openMiNegocioMenu(final Page page) {
		assertVisibleText(page, "Negocio", 12_000);
		clickByVisibleText(page, "Mi Negocio");
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(500);
	}

	private String validateLegalLink(final Page appPage, final String linkText, final String headingText,
			final Path screenshotPath) {
		final String appUrlBefore = appPage.url();
		Page legalPage = null;

		try {
			legalPage = appPage.waitForPopup(() -> clickByVisibleText(appPage, linkText),
					new Page.WaitForPopupOptions().setTimeout(5_000));
			legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			legalPage.waitForTimeout(500);
		} catch (PlaywrightException popupNotOpened) {
			clickByVisibleText(appPage, linkText);
			appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			appPage.waitForTimeout(500);
			legalPage = appPage;
		}

		assertVisibleText(legalPage, headingText, 12_000);
		assertLegalContentVisible(legalPage);
		takeScreenshot(legalPage, screenshotPath, true);

		final String finalUrl = legalPage.url();

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
		} else if (!appPage.url().equals(appUrlBefore)) {
			appPage.goBack();
			appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			appPage.waitForTimeout(500);
		}

		return finalUrl;
	}

	private void assertBusinessPlanSection(final Page page) {
		assertVisibleText(page, "Informacion General", 10_000);
		final String bodyText = normalizeWhitespace(page.locator("body").innerText());
		Assert.assertTrue("No se detecto nombre de usuario visible.", bodyText.length() > 20);
		Assert.assertTrue("No se detecto email visible.", bodyText.matches("(?s).*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*"));
		assertVisibleText(page, "BUSINESS PLAN", 10_000);
		assertActionableByText(page, "Cambiar Plan", 10_000);
	}

	private void assertLegalContentVisible(final Page page) {
		final String bodyText = normalizeWhitespace(page.locator("body").innerText());
		Assert.assertTrue("No se detecto contenido legal legible.", bodyText.length() > 180);
	}

	private void fillOptionalField(final Page page, final String fieldLabel, final String value) {
		final List<Locator> options = new ArrayList<>();
		options.add(page.getByLabel(Pattern.compile(Pattern.quote(fieldLabel), Pattern.CASE_INSENSITIVE)));
		options.add(page.getByPlaceholder(Pattern.compile(Pattern.quote(fieldLabel), Pattern.CASE_INSENSITIVE)));
		options.add(page.locator("input").filter(new Locator.FilterOptions().setHasText(fieldLabel)));

		for (final Locator option : options) {
			final Locator target = option.first();
			try {
				target.waitFor(new Locator.WaitForOptions().setTimeout(2_000));
				target.fill(value);
				return;
			} catch (PlaywrightException ignored) {
			}
		}
	}

	private void assertVisibleInput(final Page page, final String inputLabel, final int timeoutMs) {
		final List<Locator> options = new ArrayList<>();
		options.add(page.getByLabel(Pattern.compile(Pattern.quote(inputLabel), Pattern.CASE_INSENSITIVE)));
		options.add(page.getByPlaceholder(Pattern.compile(Pattern.quote(inputLabel), Pattern.CASE_INSENSITIVE)));

		for (final Locator option : options) {
			try {
				option.first().waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
				return;
			} catch (PlaywrightException ignored) {
			}
		}

		Assert.fail("No se encontro input visible: " + inputLabel);
	}

	private void assertActionableByText(final Page page, final String text, final int timeoutMs) {
		final Locator target = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))),
				page.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)));
		target.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
	}

	private void clickByVisibleText(final Page page, final String text) {
		final Pattern textPattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
		final Locator target = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern)),
				page.getByText(textPattern));
		clickAndWait(page, target);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click();
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(450);
	}

	private void assertVisibleText(final Page page, final String text, final int timeoutMs) {
		final Pattern pattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
		final Locator target = firstVisible(page.getByText(pattern),
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(pattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)));
		target.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
	}

	private Locator firstVisible(final Locator... options) {
		for (final Locator option : options) {
			final Locator candidate = option.first();
			try {
				candidate.waitFor(new Locator.WaitForOptions().setTimeout(4_000));
				return candidate;
			} catch (PlaywrightException ignored) {
			}
		}
		throw new AssertionError("No se encontro un elemento visible con los selectores provistos.");
	}

	private void takeScreenshot(final Page page, final Path path, final boolean fullPage) {
		try {
			Files.createDirectories(path.getParent());
			page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
		} catch (IOException e) {
			throw new RuntimeException("No se pudo guardar screenshot: " + path, e);
		}
	}

	private boolean runStep(final String stepName, final Map<String, Boolean> report, final List<String> failures,
			final Runnable action) {
		try {
			action.run();
			report.put(stepName, true);
			return true;
		} catch (Throwable t) {
			report.put(stepName, false);
			failures.add(stepName + " -> " + t.getMessage());
			return false;
		}
	}

	private Path createEvidenceDirectory() throws IOException {
		final String runId = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDir = Paths.get("target", "saleads-evidence", runId);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private Map<String, Boolean> initializeReport() {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Informacion General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Terminos y Condiciones", false);
		report.put("Politica de Privacidad", false);
		return report;
	}

	private void writeReport(final Path evidenceDir, final Map<String, Boolean> report, final Map<String, String> legalUrls,
			final List<String> failures) throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"name\": \"").append(TEST_NAME).append("\",\n");
		sb.append("  \"generatedAt\": \"").append(OffsetDateTime.now()).append("\",\n");
		sb.append("  \"evidenceDirectory\": \"").append(escapeJson(evidenceDir.toString())).append("\",\n");
		sb.append("  \"results\": [\n");
		int index = 0;
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			sb.append("    {\"field\":\"").append(escapeJson(entry.getKey())).append("\",\"status\":\"")
					.append(entry.getValue() ? "PASS" : "FAIL").append("\"}");
			if (index < report.size() - 1) {
				sb.append(",");
			}
			sb.append("\n");
			index++;
		}
		sb.append("  ],\n");
		sb.append("  \"legalUrls\": {\n");
		sb.append("    \"Terminos y Condiciones\": \"")
				.append(escapeJson(legalUrls.getOrDefault("Terminos y Condiciones", ""))).append("\",\n");
		sb.append("    \"Politica de Privacidad\": \"")
				.append(escapeJson(legalUrls.getOrDefault("Politica de Privacidad", ""))).append("\"\n");
		sb.append("  },\n");
		sb.append("  \"failures\": [\n");
		for (int i = 0; i < failures.size(); i++) {
			sb.append("    \"").append(escapeJson(failures.get(i))).append("\"");
			if (i < failures.size() - 1) {
				sb.append(",");
			}
			sb.append("\n");
		}
		sb.append("  ]\n");
		sb.append("}\n");

		Files.writeString(evidenceDir.resolve("final-report.json"), sb.toString(), StandardCharsets.UTF_8);
	}

	private String getConfigValue(final String property, final String envName) {
		final String propertyValue = System.getProperty(property);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return null;
	}

	private String getConfigOrDefault(final String property, final String envName, final String defaultValue) {
		final String value = getConfigValue(property, envName);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private String normalizeWhitespace(final String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").trim();
	}

	private String escapeJson(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private void require(final boolean condition, final String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private void tryClickIfVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
			locator.first().click();
		} catch (PlaywrightException ignored) {
		}
	}
}
