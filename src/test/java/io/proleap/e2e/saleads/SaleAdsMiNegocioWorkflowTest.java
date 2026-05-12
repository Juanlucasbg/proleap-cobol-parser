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
			Pattern.CASE_INSENSITIVE);
	private static final String TEST_NAME = "saleads_mi_negocio_full_test";

	@Test
	public void validateMiNegocioWorkflow() throws IOException {
		final boolean failOnValidation = Boolean
				.parseBoolean(getConfigOrDefault("saleads.failOnValidation", "SALEADS_FAIL_ON_VALIDATION", "true"));
		final Path evidenceDir = createEvidenceDirectory();
		final Map<String, Boolean> report = initializeReport();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = launchBrowser(playwright);
			try {
				final BrowserContext context = browser.newContext();
				final Page page = context.newPage();
				openLoginPageIfConfigured(page);

				final boolean loginOk = runStep("Login", report, failures, () -> {
					loginWithGoogle(page);
					waitForMainInterface(page);
					takeScreenshot(page, evidenceDir.resolve("01-dashboard.png"), false);
				});

				final boolean menuOk = runStep("Mi Negocio menu", report, failures, () -> {
					require(loginOk, "No se puede abrir Mi Negocio sin login exitoso.");
					openMiNegocioMenu(page);
					assertVisibleTextAny(page, 10_000, "Agregar Negocio");
					assertVisibleTextAny(page, 10_000, "Administrar Negocios");
					takeScreenshot(page, evidenceDir.resolve("02-mi-negocio-menu-expandido.png"), false);
				});

				runStep("Agregar Negocio modal", report, failures, () -> {
					require(menuOk, "No se puede validar el modal sin el menu Mi Negocio.");
					clickByVisibleTextAny(page, "Agregar Negocio");
					page.waitForLoadState(LoadState.DOMCONTENTLOADED);
					page.waitForTimeout(600);
					assertVisibleTextAny(page, 10_000, "Crear Nuevo Negocio");
					assertVisibleInput(page, "Nombre del Negocio", 10_000);
					assertVisibleTextAny(page, 10_000, "Tienes 2 de 3 negocios");
					assertActionableByTextAny(page, 10_000, "Cancelar");
					assertActionableByTextAny(page, 10_000, "Crear Negocio");
					takeScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

					fillOptionalField(page, "Nombre del Negocio", "Negocio Prueba Automatizacion");
					clickByVisibleTextAny(page, "Cancelar");
					page.waitForLoadState(LoadState.DOMCONTENTLOADED);
				});

				final boolean administrarOk = runStep("Administrar Negocios view", report, failures, () -> {
					require(menuOk, "No se puede abrir Administrar Negocios sin menu Mi Negocio.");
					openMiNegocioMenu(page);
					clickByVisibleTextAny(page, "Administrar Negocios");
					page.waitForLoadState(LoadState.DOMCONTENTLOADED);
					page.waitForTimeout(800);
					assertVisibleTextAny(page, 12_000, "Información General", "Informacion General");
					assertVisibleTextAny(page, 12_000, "Detalles de la Cuenta");
					assertVisibleTextAny(page, 12_000, "Tus Negocios");
					assertVisibleTextAny(page, 12_000, "Sección Legal", "Seccion Legal");
					takeScreenshot(page, evidenceDir.resolve("04-administrar-negocios-full.png"), true);
				});

				runStep("Información General", report, failures, () -> {
					require(administrarOk, "No se puede validar Información General sin Administrar Negocios.");
					assertBusinessPlanSection(page);
				});

				runStep("Detalles de la Cuenta", report, failures, () -> {
					require(administrarOk, "No se puede validar Detalles de la Cuenta sin Administrar Negocios.");
					assertVisibleTextAny(page, 10_000, "Cuenta creada");
					assertVisibleTextAny(page, 10_000, "Estado activo");
					assertVisibleTextAny(page, 10_000, "Idioma seleccionado");
				});

				runStep("Tus Negocios", report, failures, () -> {
					require(administrarOk, "No se puede validar Tus Negocios sin Administrar Negocios.");
					assertVisibleTextAny(page, 10_000, "Tus Negocios");
					assertActionableByTextAny(page, 10_000, "Agregar Negocio");
					assertVisibleTextAny(page, 10_000, "Tienes 2 de 3 negocios");
				});

				runStep("Términos y Condiciones", report, failures, () -> {
					require(administrarOk, "No se puede validar Términos y Condiciones sin Administrar Negocios.");
					final String finalUrl = validateLegalLink(page, evidenceDir.resolve("08-terminos-condiciones.png"),
							new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
							new String[] { "Términos y Condiciones", "Terminos y Condiciones" });
					legalUrls.put("Términos y Condiciones", finalUrl);
				});

				runStep("Política de Privacidad", report, failures, () -> {
					require(administrarOk, "No se puede validar Política de Privacidad sin Administrar Negocios.");
					final String finalUrl = validateLegalLink(page, evidenceDir.resolve("09-politica-privacidad.png"),
							new String[] { "Política de Privacidad", "Politica de Privacidad" },
							new String[] { "Política de Privacidad", "Politica de Privacidad" });
					legalUrls.put("Política de Privacidad", finalUrl);
				});
			} finally {
				browser.close();
			}
		}

		writeReport(evidenceDir, report, legalUrls, failures);
		if (!failures.isEmpty() && failOnValidation) {
			Assert.fail("Validaciones fallidas:\n" + String.join("\n", failures));
		}
	}

	private void openLoginPageIfConfigured(final Page page) {
		final String baseUrl = getConfigValue("saleads.baseUrl", "SALEADS_BASE_URL");
		if (baseUrl == null || baseUrl.isBlank()) {
			return;
		}
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
		tryClickIfVisible(page.getByText(Pattern.compile(Pattern.quote(accountEmail), Pattern.CASE_INSENSITIVE)),
				8_000);
	}

	private void waitForMainInterface(final Page page) {
		final Locator sidebar = firstVisible(
				page.locator("aside"),
				page.getByRole(AriaRole.NAVIGATION),
				page.getByText(Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE)));
		sidebar.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
	}

	private void openMiNegocioMenu(final Page page) {
		assertVisibleTextAny(page, 12_000, "Negocio");
		clickByVisibleTextAny(page, "Mi Negocio");
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(500);
	}

	private String validateLegalLink(final Page appPage, final Path screenshotPath, final String[] linkTexts,
			final String[] headingTexts) {
		final String appUrlBefore = appPage.url();
		Page legalPage;

		try {
			legalPage = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(5_000),
					() -> clickByVisibleTextAny(appPage, linkTexts));
			legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			legalPage.waitForTimeout(500);
		} catch (PlaywrightException popupNotOpened) {
			clickByVisibleTextAny(appPage, linkTexts);
			appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			appPage.waitForTimeout(500);
			legalPage = appPage;
		}

		assertVisibleTextAny(legalPage, 12_000, headingTexts);
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
		assertVisibleTextAny(page, 10_000, "Información General", "Informacion General");
		final String bodyText = normalizeWhitespace(page.locator("body").innerText());
		Assert.assertTrue("No se detecto nombre de usuario visible.", bodyText.length() > 20);
		Assert.assertTrue("No se detecto email visible.",
				bodyText.matches("(?s).*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*"));
		assertVisibleTextAny(page, 10_000, "BUSINESS PLAN");
		assertActionableByTextAny(page, 10_000, "Cambiar Plan");
	}

	private void assertLegalContentVisible(final Page page) {
		final String bodyText = normalizeWhitespace(page.locator("body").innerText());
		Assert.assertTrue("No se detecto contenido legal legible.", bodyText.length() > 180);
	}

	private void fillOptionalField(final Page page, final String fieldLabel, final String value) {
		final List<Locator> options = new ArrayList<>();
		options.add(page.getByLabel(Pattern.compile(Pattern.quote(fieldLabel), Pattern.CASE_INSENSITIVE)));
		options.add(page.getByPlaceholder(Pattern.compile(Pattern.quote(fieldLabel), Pattern.CASE_INSENSITIVE)));

		for (final Locator option : options) {
			try {
				final Locator target = option.first();
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

	private void assertActionableByTextAny(final Page page, final int timeoutMs, final String... texts) {
		final Locator target = firstVisibleByText(page, texts);
		target.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
	}

	private void clickByVisibleTextAny(final Page page, final String... texts) {
		final Locator target = firstVisibleByText(page, texts);
		clickAndWait(page, target);
	}

	private void assertVisibleTextAny(final Page page, final int timeoutMs, final String... texts) {
		final Locator target = firstVisibleByText(page, texts);
		target.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
	}

	private Locator firstVisibleByText(final Page page, final String... texts) {
		final List<Locator> options = new ArrayList<>();
		for (final String text : texts) {
			final Pattern textPattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
			options.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)));
			options.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern)));
			options.add(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(textPattern)));
			options.add(page.getByText(textPattern));
		}
		return firstVisible(options.toArray(new Locator[0]));
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click();
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(450);
	}

	private Locator firstVisible(final Locator... options) {
		for (final Locator option : options) {
			try {
				final Locator candidate = option.first();
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
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
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
		sb.append("    \"Términos y Condiciones\": \"")
				.append(escapeJson(legalUrls.getOrDefault("Términos y Condiciones", ""))).append("\",\n");
		sb.append("    \"Política de Privacidad\": \"")
				.append(escapeJson(legalUrls.getOrDefault("Política de Privacidad", ""))).append("\"\n");
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
			final Locator target = locator.first();
			target.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
			target.click();
		} catch (PlaywrightException ignored) {
		}
	}
}
